# Template Method Pattern

## 1. Pattern Name & Category

**Pattern Name:** Template Method
**Category:** Behavioral
**GoF Classification:** Behavioral Design Pattern (Gang of Four, "Design Patterns: Elements of Reusable Object-Oriented Software", 1994)

---

## 2. Intent

Define the skeleton of an algorithm in a base class, deferring some steps to subclasses, so that subclasses can redefine certain steps without changing the algorithm's overall structure.

---

## Intuition

> **One-line analogy**: Template Method is like a recipe framework — the steps are fixed (prep, cook, plate, serve), but each dish fills in its own prep and cook instructions while the serving sequence stays the same.

**Mental model**: When multiple classes share the same algorithm structure but differ in specific steps, Template Method defines the skeleton in the abstract base class and declares "hook" methods (abstract or with defaults) for the varying steps. Subclasses implement the hooks without touching the algorithm's overall structure. The base class calls the hooks in the right order — subclasses never need to know the sequence.

**Why it matters**: Template Method is pervasive in frameworks — JUnit's test lifecycle (setUp, test, tearDown), Spring's JdbcTemplate (execute, map results), Servlet's service() method — all use Template Method. The framework defines the invariant structure; users fill in the variable parts without knowing the full execution flow.

**Key insight**: The Hollywood Principle applies: "Don't call us, we'll call you." The framework (abstract class) calls your code (hook methods), not the reverse. This inversion of control is what gives frameworks their power to manage the execution lifecycle while letting users customize specific steps.

---

## 3. Problem Statement

### The Problem
You have multiple classes that perform the same high-level algorithm but differ in specific steps. Without a pattern, you either:
- Duplicate the algorithm skeleton in every class (violating DRY), or
- Write a single monolithic class with complex conditional logic to handle every variant.

### Scenario
Consider building a data mining application that processes reports from different file formats: CSV, PDF, and Excel. The overall process is always:
1. Open the file
2. Extract the raw data
3. Parse/analyze the data
4. Format the results
5. Close the file

Steps 1, 3, and 5 are identical across all formats. Steps 2 and 4 differ per format. Without Template Method, you'd write three separate classes with 60%+ duplicated code, or one god-class with `if (format == CSV)` branches everywhere.

Another scenario: think about unit test frameworks. Every test follows: setup -> execute test -> verify assertions -> teardown. JUnit's `TestCase` class uses Template Method so you only override the test body, not the whole lifecycle.

---

## 4. Solution

The Template Method pattern extracts the common algorithm skeleton into an **abstract base class**. The invariant parts are implemented as concrete methods in the base class. The variant parts are declared as **abstract methods** (or **hook methods** with default implementations) that subclasses override.

The base class has one final (or non-overridable) method — the **template method** — that orchestrates the algorithm by calling all the steps in order. Subclasses only implement the steps that are unique to them.

This is a classic example of the **Hollywood Principle**: "Don't call us, we'll call you." The base class controls the flow; subclasses just fill in the blanks.

---

## 5. UML Structure

```
AbstractClass
+──────────────────────────────────────────+
|  + templateMethod() : void  [final]      |  <-- orchestrates the algorithm
|  + step1() : void  [concrete]            |  <-- invariant, implemented here
|  + step2() : void  [abstract]            |  <-- variant, subclass must implement
|  + step3() : void  [concrete]            |  <-- invariant, implemented here
|  + hook() : void   [concrete/empty]      |  <-- optional hook, subclass may override
+──────────────────────────────────────────+
              /\
              |  extends
    __________|__________
    |                   |
ConcreteClassA      ConcreteClassB
+──────────────+   +──────────────+
| + step2()    |   | + step2()    |
| + hook()     |   |              |
+--------------+   +--------------+

Client
  |
  | uses
  v
AbstractClass (reference)  --> calls templateMethod()
```

**Key relationships:**
- `AbstractClass` is the parent; `ConcreteClass` subclasses extend it.
- The `templateMethod()` is typically `final` to prevent subclasses from altering the algorithm structure.
- Abstract methods are **primitive operations** — required overrides.
- Hook methods are **optional overrides** with default (often no-op) implementations.

---

## 6. How It Works — Step-by-Step

1. **Client** holds a reference to `AbstractClass` (polymorphically).
2. Client calls `templateMethod()` on the abstract class reference.
3. `templateMethod()` executes the algorithm steps in a fixed order:
   - Calls `step1()` — the base class handles this (invariant).
   - Calls `step2()` — dispatches to the concrete subclass via dynamic dispatch.
   - Calls `hook()` — subclass may or may not override this.
   - Calls `step3()` — the base class handles this (invariant).
4. The subclass's overridden methods execute their specific logic when called.
5. Control always returns to `templateMethod()` after each step.
6. The overall flow is fixed; only specific steps vary.

**Dynamic dispatch** is the key mechanism: when `templateMethod()` calls `this.step2()`, Java's virtual method dispatch ensures the subclass's version runs — even though the call originates in the base class.

---

## 7. Key Components

| Component | Role |
|---|---|
| **AbstractClass** | Defines `templateMethod()` (the skeleton) and declares abstract/hook methods |
| **templateMethod()** | The invariant algorithm skeleton; typically `final` |
| **Abstract Methods (Primitive Operations)** | Steps that MUST be overridden by subclasses |
| **Hook Methods** | Steps with default implementations; subclasses MAY override |
| **ConcreteClass** | Implements the abstract steps specific to that variant |
| **Client** | Instantiates a concrete class and triggers the algorithm |

---

## 8. When to Use

- **Invariant algorithm structure with variant steps:** You have a process that always follows the same sequence, but some steps differ per implementation (e.g., data import pipelines, build systems).
- **Code duplication elimination:** Multiple classes share the same algorithm skeleton. Consolidate it in one place.
- **Framework hooks:** You're building a framework and want to let users customize specific steps without letting them alter the overall flow. (e.g., Spring's `JdbcTemplate`, `AbstractController`)
- **Inversion of control:** You want the framework to call user code at well-defined extension points.
- **Test lifecycle management:** Setup, execution, verification, and teardown sequences where only the "execution" step varies.
- **Batch processing pipelines:** ETL (Extract, Transform, Load) processes where E and L are standard but T differs per data source.
- **Report generation:** Header and footer rendering are the same; only the body content varies.

---

## 9. When NOT to Use

- **Simple, single-step operations:** If the algorithm is trivial (1-2 steps), the pattern adds unnecessary abstraction.
- **Highly dynamic algorithms:** If the algorithm structure itself varies (not just the steps), Strategy pattern is more appropriate.
- **Composition is preferred over inheritance:** If you need maximum flexibility, favor Strategy (composition) over Template Method (inheritance). Template Method locks the hierarchy.
- **Many small variations:** If you have 10 steps and each subclass varies 8 of them, the pattern provides little value over just having separate classes.
- **When subclass proliferation is a concern:** Each new variant requires a new subclass. With many variants, this can explode into an unmanageable hierarchy.
- **Functional programming contexts:** In languages with first-class functions, passing a lambda/function is simpler and more flexible than creating a subclass.

---

## 10. Pros

- **Eliminates code duplication:** The invariant algorithm lives in one place. Bug fixes and improvements automatically apply to all subclasses.
- **Enforces the algorithm structure:** The `final` template method guarantees the skeleton cannot be broken by subclasses. This is a contract.
- **Open/Closed Principle:** The base class is closed for modification; new variants are added by creating new subclasses (open for extension).
- **Inversion of Control:** The base class controls the algorithm flow. Subclasses don't need to know the bigger picture — they just implement their piece.
- **Easy to add new variants:** Adding support for a new file format/variant means creating one new subclass. No modification to existing code.
- **Hook methods provide optional extensibility:** Subclasses can opt-in to override hooks, providing flexibility without forcing unnecessary overrides.
- **Frameworks benefit greatly:** Framework code can define the skeleton; user application code fills in the domain-specific steps.

---

## 11. Cons

- **Tight coupling via inheritance:** Subclasses are tightly coupled to the base class. Changes to the base class can break all subclasses (fragile base class problem).
- **Limited to single inheritance (Java/C#):** Since it relies on class inheritance, a class can only use one template method hierarchy. This is a significant constraint in languages without multiple inheritance.
- **Liskov Substitution Principle risk:** Subclasses must honor the contract implied by the base class. If a subclass overrides a step in a way that violates the expected behavior, LSP is broken.
- **Harder to understand the full algorithm:** The algorithm is split across base class and subclasses. Developers must read multiple files to understand the complete flow.
- **Subclass proliferation:** Every variant requires a new class. With many minor variations, the class hierarchy grows unwieldy.
- **Hook method confusion:** The distinction between abstract methods (must override) and hooks (may override) is not always obvious to subclass implementors without good documentation.
- **Testing complexity:** Testing each subclass in isolation is harder because they inherit behavior from the base class.

---

## 12. Tradeoffs

| What You Gain | What You Lose |
|---|---|
| Code reuse via inheritance | Flexibility of composition |
| Enforced algorithm structure | Ability to change the structure at runtime |
| Single place to fix algorithm bugs | Subclasses tightly coupled to base class |
| New variants without modifying existing code | Class hierarchy can become deep/complex |
| Clear extension points (hooks) | Can't mix-and-match steps from different hierarchies |

**Template Method vs Strategy:** Template Method uses inheritance and fixes the skeleton; Strategy uses composition and can swap the entire algorithm. If you need runtime algorithm selection, use Strategy. If the skeleton is fixed and only steps vary, use Template Method.

---

## 13. Common Pitfalls

1. **Not making `templateMethod()` final:** If it's not `final`, a subclass can accidentally override the skeleton itself, defeating the entire purpose of the pattern.

2. **Too many abstract methods:** If every step is abstract, the base class provides no real value. There's no reuse — just an interface contract. Consider whether an interface would be cleaner.

3. **Putting too much logic in hook methods:** Hooks should be lightweight extension points. Complex logic in hooks makes the base class hard to reason about.

4. **Calling abstract methods from the constructor:** If `templateMethod()` is called in the constructor of the base class, it will call abstract methods before the subclass is fully initialized — a classic Java bug leading to NullPointerExceptions.

5. **Ignoring the Liskov Substitution Principle:** A subclass that overrides a step in a way that changes its observable behavior (side effects, return values, exceptions) breaks the contract and causes bugs when the base class calls it.

6. **Using Template Method when Strategy fits better:** If you find yourself passing a flag to the constructor to choose between algorithm variants, you should be using Strategy (or even a factory + Template Method combination).

7. **Deep inheritance hierarchies:** Three or more levels of template method inheritance (AbstractA -> AbstractB -> Concrete) become extremely hard to reason about. Keep hierarchies shallow.

---

## 14. Real-World Usage

### Production Scenario: Spring JdbcTemplate at 10k queries/sec (connection pool of 20)

Spring's `JdbcTemplate` is the canonical production usage of Template Method in the Java ecosystem.
The template defines the invariant algorithm: acquire connection from HikariCP pool, prepare statement,
execute, map results, release connection, and translate any `SQLException` to a Spring
`DataAccessException`. The caller provides only the variable part: the SQL string and a `RowMapper`.

At 10,000 queries/sec through a HikariCP pool of 20 connections, each connection services ~500
queries/sec. `JdbcTemplate.query()` ensures that connections are always returned to the pool —
even when `RowMapper.mapRow()` throws a `RuntimeException`. Without this template guarantee,
a single buggy mapper would exhaust the 20-connection pool in seconds, causing a full service outage.

**Scale numbers:**
- 10,000 queries/sec; HikariCP default pool size 10 (production tuned to 20)
- Connection acquisition: < 1 ms from HikariCP pool under normal load
- JdbcTemplate overhead vs raw JDBC: ~5 us per query (reflection for exception translation)
- RowMapper call frequency: once per row; 1,000-row result set = 1,000 mapRow() calls per query
- AbstractBatchConfiguration batch job: 50,000 items/chunk, 200 chunks/job = 10M records/job

```
JdbcTemplate — Template Method in Production
=============================================

  Caller (Service layer)
       |
       | jdbcTemplate.query(sql, rowMapper, args...)
       |
  +----+-------------------------------+
  |         JdbcTemplate.query()       |  <-- TEMPLATE METHOD (final-like sequence)
  |                                    |
  |  1. dataSource.getConnection()     |  invariant: acquire from HikariCP pool
  |  2. prepareStatement(sql, args)    |  invariant: bind parameters safely
  |  3. executeQuery()                 |  invariant: run against DB
  |  4. while(rs.next())              |
  |       rowMapper.mapRow(rs, rowNum) |  <-- HOOK: caller-provided variable step
  |  5. rs.close() / stmt.close()     |  invariant: release resources (finally block)
  |  6. conn returned to pool          |  invariant: always, even on exception
  |  7. translate SQLException         |  invariant: wrap in DataAccessException hierarchy
  +------------------------------------+
       |
  [ List<T> result returned to caller ]
```

```java
// Java 17 LTS — JdbcTemplate RowMapper as a Template Method hook
// The template (JdbcTemplate.query) is invariant; only mapRow() varies per use case.

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // RowMapper is the hook — caller provides only the variable part (column -> object mapping)
    private static final RowMapper<Order> ORDER_MAPPER = (rs, rowNum) -> new Order(
        rs.getString("order_id"),
        rs.getString("status"),
        rs.getLong("amount_cents"),
        rs.getTimestamp("created_at").toInstant()
    );

    public List<Order> findByStatus(String status) {
        // JdbcTemplate handles: connection, PreparedStatement, execute, iterate, release, translate
        return jdbc.query(
            "SELECT order_id, status, amount_cents, created_at FROM orders WHERE status = ?",
            ORDER_MAPPER,
            status
        );
    }

    // NamedParameterJdbcTemplate variant — same template, different SQL binding hook
    public int countByMerchant(String merchantId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE merchant_id = ?",
            Integer.class,
            merchantId
        );
    }
}
```

```java
// Java 17 LTS — Spring Batch AbstractBatchConfiguration as Template Method
// Template defines the batch lifecycle; subclass provides Reader, Processor, Writer hooks

@Configuration
@EnableBatchProcessing
public class OrderExportBatchConfig extends DefaultBatchConfigurer {

    // DefaultBatchConfigurer.createJobRepository() is the template method:
    // it calls getDataSource(), getTransactionManager() — hooks the subclass can override.
    // Subclass does NOT override the full job-launch sequence, only specific steps.

    @Bean
    public Job orderExportJob(JobBuilderFactory jobs, Step exportStep) {
        return jobs.get("orderExportJob")
            .incrementer(new RunIdIncrementer())
            .flow(exportStep)
            .end()
            .build();
    }

    @Bean
    public Step exportStep(StepBuilderFactory steps,
                           ItemReader<Order> reader,
                           ItemProcessor<Order, OrderCsv> processor,
                           ItemWriter<OrderCsv> writer) {
        return steps.get("exportStep")
            .<Order, OrderCsv>chunk(50_000)   // chunk = one template iteration unit
            .reader(reader)        // hook: what to read (DB cursor)
            .processor(processor)  // hook: how to transform (Order -> OrderCsv)
            .writer(writer)        // hook: where to write (S3 CSV)
            .build();
        // Spring Batch template: open reader, iterate chunks, process, write, commit, close
        // Caller provides only the 3 hooks; retry, skip, and transaction logic are invariant.
    }
}
```

### Famous Codebase Usages

- **`java.io.InputStream.read(byte[], int, int)`**: the template method implemented using the
  single-byte abstract `read()` hook; `FileInputStream`, `ByteArrayInputStream`, `GZIPInputStream`
  each implement `read()` — the loop logic in the 3-arg version is invariant across all.
- **`java.util.AbstractList`**: `get(int)` and `size()` are the abstract hooks; `iterator()`,
  `indexOf()`, `subList()`, `contains()`, `toArray()` are all concrete template methods built
  on top. `ArrayList`, `LinkedList`, `UnmodifiableList` only implement the two hooks.
- **`javax.servlet.HttpServlet.service()`**: dispatches to `doGet()`, `doPost()`, `doPut()`,
  `doDelete()` based on HTTP method — the dispatch logic is the template; each `doXxx()` is a hook.
- **`JdbcTemplate.execute(ConnectionCallback<T>)`**: wraps the connection lifecycle around the
  caller's `ConnectionCallback.doInConnection()` — the original 2003 Spring template method.
- **`AbstractBatchJobLauncher`** (Spring Batch): `run(Job, JobParameters)` is the template;
  hooks `JobRepository.createJobExecution()`, `job.execute()`, `handleStep()` are overridable.
- **JUnit 4 `TestCase.runBare()`**: calls `setUp()` before and `tearDown()` after `runTest()` —
  the first widely-known Java template method for test lifecycle management.

---

### Anti-Pattern 1: Subclass Overrides the Template Method Itself

```java
// BROKEN — subclass overrides execute() (the template), bypassing the invariant sequence.
// Connection acquisition and release are skipped; connections leak under exception paths.
// The entire purpose of JdbcTemplate (resource safety) is defeated.

public class UnsafeOrderRepository extends JdbcTemplate {

    @Override
    public <T> T execute(ConnectionCallback<T> action) {
        // WRONG: subclass re-implements the template, skipping finally-block cleanup
        Connection conn = getDataSource().getConnection();
        return action.doInConnection(conn);
        // conn is never returned to pool if action throws — pool exhausted in seconds
    }
}
```

```java
// FIX — make the template method final; subclasses can only override declared hooks.
// Production code never subclasses JdbcTemplate; it injects it as a dependency.

@Repository
public class SafeOrderRepository {
    private final JdbcTemplate jdbc;  // injected — not extended

    public SafeOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Order> findAll() {
        return jdbc.query("SELECT * FROM orders", ORDER_MAPPER);
        // JdbcTemplate.query() is effectively final in design intent;
        // all connection lifecycle is handled inside the template.
    }
}
// Rule: prefer composition (inject JdbcTemplate) over inheritance (extend JdbcTemplate).
```

---

### Anti-Pattern 2: Hook Method With Too Much Mandatory Behavior (Forced super() Call)

```java
// BROKEN — base class hook has non-trivial mandatory behavior.
// Subclass MUST call super.beforeQuery() at exactly the right moment.
// Forgetting or calling it at the wrong time silently corrupts metrics or skips auth.

public abstract class BaseRepository {
    protected void beforeQuery(String sql) {
        MetricsRegistry.startTimer(sql);     // MANDATORY — subclass must call this
        SecurityContext.checkReadPermission(); // MANDATORY — subclass must call this
    }

    protected abstract List<?> executeQuery(String sql);

    public List<?> query(String sql) {
        beforeQuery(sql);   // What if a subclass overrides beforeQuery and forgets super?
        return executeQuery(sql);
    }
}

public class OrderRepository extends BaseRepository {
    @Override
    protected void beforeQuery(String sql) {
        // Forgot super.beforeQuery() — metrics never recorded, auth check skipped
        log.debug("Querying: {}", sql);
    }
}
```

```java
// FIX — template method is final; mandatory behavior is in the template, not the hook.
// Hooks are protected and optional (no super() contract).

public abstract class BaseRepository {

    // FINAL template — mandatory steps are locked, hooks are explicit extension points
    public final List<?> query(String sql) {
        MetricsRegistry.startTimer(sql);      // mandatory — always runs
        SecurityContext.checkReadPermission(); // mandatory — always runs
        try {
            return executeQuery(sql);         // hook — subclass provides the variable part
        } finally {
            MetricsRegistry.stopTimer(sql);   // mandatory — always runs, even on exception
        }
    }

    // Hook — subclass implements this ONLY; no super() call needed or expected
    protected abstract List<?> executeQuery(String sql);
}
```

---

### Anti-Pattern 3: Template With Too Many Abstract Methods (Forces Subclass to Implement Everything)

```java
// BROKEN — 20 abstract methods force every subclass to implement all of them,
// even when 15 are irrelevant to the subclass's use case.
// Adding a new abstract method breaks all existing subclasses (compile error).

public abstract class DataPipeline {
    protected abstract void validateSchema();
    protected abstract void connectSource();
    protected abstract void connectSink();
    protected abstract void transformRow(Row r);
    protected abstract void handleDuplicates();
    protected abstract void handleNulls();
    protected abstract void auditLog();
    protected abstract void sendAlerts();
    protected abstract void retryOnFailure();
    protected abstract void compressOutput();
    // ... 10 more abstract methods
    // Every subclass implements ALL 20, even trivial pipelines that need only 3.
}
```

```java
// FIX — only truly mandatory hooks are abstract; optional hooks have no-op defaults.
// Subclasses override only what they need; adding a new optional hook is non-breaking.

public abstract class DataPipeline {

    // FINAL template method — sequence is invariant
    public final void run() {
        connectSource();    // mandatory — always called
        connectSink();      // mandatory — always called
        while (hasNext()) {
            Row row = nextRow();
            handleNulls(row);    // optional hook — no-op default
            row = transformRow(row);  // mandatory — always called
            handleDuplicates(row);   // optional hook — no-op default
            writeSink(row);
        }
        auditLog();   // optional hook — no-op default
        cleanup();    // mandatory
    }

    // Mandatory hooks — subclasses MUST implement these
    protected abstract void connectSource();
    protected abstract void connectSink();
    protected abstract Row transformRow(Row row);

    // Optional hooks — default implementations do nothing; override only if needed
    protected void handleNulls(Row row) {}        // no-op default
    protected void handleDuplicates(Row row) {}   // no-op default
    protected void auditLog() {}                  // no-op default
    protected void cleanup() {}                   // no-op default
}
// SimplePipeline extends DataPipeline and implements only the 3 mandatory hooks.
```

---

### Performance and Correctness Numbers

| Metric | Value |
|---|---|
| JdbcTemplate overhead vs raw JDBC | ~5 us per query (exception translation reflection) |
| HikariCP connection acquisition | < 1 ms under normal load (pool size 20) |
| Connection leak prevention | 100% — JdbcTemplate finally block always releases |
| Spring Batch chunk processing | 50,000 items/chunk; 10M records/job at ~500k items/min |
| AbstractList template overhead | ~0 ns — get()/size() called directly, no dispatch indirection |

### Migration Story

**Move TO Template Method when:**
- Multiple classes share an identical sequence of steps with only 1-2 variable steps.
- The invariant steps involve resource acquisition/release that must never be skipped
  (JDBC connections, file handles, locks).
- You own the base class and all subclasses — inheritance requires this control.

**Move AWAY FROM Template Method (to Strategy) when:**
- The algorithm needs to be selected at runtime or injected externally — inheritance is compile-time.
- The hierarchy grows beyond 2 levels (AbstractA -> AbstractB -> Concrete) — prefer composition.
- You cannot control the base class (third-party library) — inject a Strategy callback instead.

---

## 15. Comparison with Similar Patterns

### Template Method vs Strategy
| Aspect | Template Method | Strategy |
|---|---|---|
| Mechanism | Inheritance | Composition |
| Algorithm structure | Fixed in base class | Entirely replaceable |
| Runtime flexibility | None — fixed at compile time | High — swap algorithms at runtime |
| Code reuse | Through inherited code | Through delegation |
| Best for | Fixed skeleton, variant steps | Entirely different algorithms |

### Template Method vs Factory Method
- Factory Method is often implemented using Template Method. The factory method (`createProduct()`) is an abstract primitive operation within a larger template method.
- Factory Method creates objects; Template Method defines algorithm flows.

### Template Method vs Hook (Extension Point)
- Hook methods within Template Method are a specific form of the general "Extension Point" pattern.
- Hooks are optional; abstract methods are mandatory. Both are used within Template Method.

---

## 16. Interview Tips

**Q: What is the Template Method pattern and when would you use it?**
A: Describe the pattern as "algorithm skeleton in base class, steps in subclasses." Use a concrete example like `HttpServlet.service()` calling `doGet()`/`doPost()`. Emphasize it's about code reuse and enforcing a process.

**Q: How does Template Method differ from Strategy?**
A: Template Method = inheritance, fixed structure, compile-time. Strategy = composition, swappable algorithm, runtime. If the interviewer probes further, say: "In Template Method, the base class IS the algorithm. In Strategy, the algorithm IS injected into the context."

**Q: Why make the template method `final`?**
A: To protect the algorithm skeleton. If subclasses could override the template method itself, they could alter the sequence of steps, break invariants, and defeat the entire purpose of having a shared skeleton.

**Q: What is a hook method?**
A: A concrete method in the base class with a default (often empty/no-op) implementation. Subclasses can optionally override it to inject behavior at a specific point in the algorithm. It's an optional extension point, unlike abstract primitive operations which are mandatory.

**Q: Name a real-world example of Template Method in Java.**
A: `HttpServlet.service()` dispatching to `doGet()`/`doPost()` is the canonical example. Also `AbstractList` in Java Collections, or `JdbcTemplate` in Spring.

**Q: What's the Hollywood Principle and how does it relate?**
A: "Don't call us, we'll call you." High-level base class controls the flow and calls low-level subclass methods — not the other way around. This inverts the typical dependency direction in inheritance.

**Q: How do you decide whether a step should be an abstract method or a hook method?**
A: Make a step abstract when every subclass MUST provide its own meaningful implementation and there's no sensible default — the algorithm is incomplete without it (e.g., `parseFormat()` in a data-import pipeline). Make it a hook (concrete, default no-op or default-behavior) when the step is optional and most subclasses can use the default — e.g., `beforeSave()` or `onError()` extension points that only some subclasses care about. Too many abstract methods makes every subclass verbose with boilerplate overrides; too many hooks can hide important required customization. A good template typically has 1-3 abstract "primitive operations" and any number of optional hooks.

**Q: What's the classic gotcha with calling overridable methods from a constructor in a Template-Method-style base class?**
A: If the base class constructor calls a method that a subclass overrides (an abstract or hook "step"), the overridden version runs *before* the subclass's own constructor has finished initializing its fields — so the override may see `null`/zero-valued fields it expects to be set. This is a well-known Java pitfall (Effective Java explicitly warns against it). The fix is to never invoke abstract/overridable methods from a constructor; instead, have the algorithm's entry point be an explicit method (`run()`, `process()`) called after construction is complete, which is exactly how Template Method is normally structured anyway.

**Q: How does Template Method interact with the Liskov Substitution Principle?**
A: Subclasses must implement the abstract steps in a way that preserves the invariants and overall contract of the template algorithm — a subclass that throws an unexpected exception, returns null where a value is required, or has wildly different performance/side-effect characteristics than the skeleton expects violates LSP even though it compiles fine. For example, if `AbstractReportGenerator.generate()` assumes `fetchData()` returns a non-null (possibly empty) list, a subclass returning `null` breaks every caller of `generate()`, not just that one method — document the contract of each abstract step (preconditions, postconditions, allowed exceptions) so subclasses can be substituted safely.

**Q: Beyond `HttpServlet`, what other Template Method examples exist in the JDK and Spring?**
A: JUnit's test lifecycle is a Template Method: the test runner calls `@BeforeEach` setup, then the `@Test` method, then `@AfterEach` teardown, in a fixed sequence the test class cannot reorder. `java.util.AbstractList` implements `indexOf()`, `contains()`, and iterators in terms of the abstract `get(int)` and `size()` that subclasses must provide. In Spring, `JdbcTemplate.execute()`/`query()` handle connection acquisition, exception translation, and resource cleanup (the fixed skeleton) while you supply a `RowMapper` or `PreparedStatementSetter` callback (the customizable step) — this is Template Method implemented via callback objects instead of subclassing, which is a common modern variation.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Template Method Appears in Distributed Systems**

- **ETL pipeline** — The Extract → Transform → Load sequence is fixed; only the implementation of each step varies per data source. A base `ETLPipeline` class defines the template; concrete subclasses implement `extract()` from S3, JDBC, or API as needed.
- **CI/CD pipeline stages** — Build → Test → Security Scan → Deploy is the fixed pipeline skeleton. Template Method lets teams override specific stages (custom test runner, custom deployment target) without modifying the pipeline orchestrator.
- **Framework request handling** — Servlet `doGet()`/`doPost()`, Spring MVC's `handleRequest()`, and gRPC's `onNext()` are Template Methods. The framework defines the lifecycle; developers override the specific step.
- **Health check base class** — A base `HealthCheck` class defines the template: run check → format result → publish metric. Concrete checks implement only `performCheck()` for their specific resource (DB, cache, external API).

---

## 17. Best Practices

1. **Always make `templateMethod()` final** (or at minimum, document it should not be overridden). This protects the invariant algorithm structure.

2. **Distinguish abstract methods from hooks clearly:** Name hooks with the `do` prefix or clearly document which methods are mandatory vs. optional. JavaDoc should specify this.

3. **Minimize the number of abstract methods:** Too many abstract methods burden subclass implementors. Each abstract method is a mandatory implementation contract.

4. **Use hooks for optional behavior:** If a step is optional (some subclasses need it, others don't), make it a hook with a default no-op implementation rather than forcing all subclasses to implement it.

5. **Never call abstract/overridable methods from the constructor:** Java initializes subclass fields AFTER calling the superclass constructor. Calling abstract methods in the constructor risks operating on uninitialized state.

6. **Keep the base class abstract if it has abstract methods:** Don't allow direct instantiation of the template class. Use the `abstract` keyword.

7. **Prefer shallow hierarchies:** Avoid chains like `AbstractBase -> AbstractMid -> Concrete`. Each level adds cognitive overhead. Two levels (abstract + concrete) is the sweet spot.

8. **Consider Strategy if variants proliferate:** If you're creating 10+ subclasses, consider whether Strategy pattern with lambdas/function objects would be cleaner and more maintainable.

9. **Document the algorithm contract in the base class:** The base class should clearly document what the template method does, what each step is supposed to accomplish, and what invariants must hold between steps.

10. **Test abstract classes using mock subclasses:** In unit tests, create a minimal concrete subclass (anonymous or test-only) that exposes the abstract methods with spy/mock implementations.
