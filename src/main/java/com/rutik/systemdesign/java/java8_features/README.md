# Java 8 Features

## 1. Concept Overview

Java 8 (March 2014) was the most significant Java release since Java 5. It introduced **lambdas**, **functional interfaces**, the **Stream API**, **Optional**, **default/static interface methods**, and the **new Date/Time API**. Together, these features brought first-class functional programming to Java without breaking backward compatibility.

The mental shift: Java 8 moved from imperative "how to do it" to declarative "what to do." A 20-line for-loop that filters, transforms, and aggregates a list becomes 3 lines of expressive stream pipeline. More importantly, it enabled composition — combining small functions into complex behavior without mutable accumulators.

Java 8 is still the second-most-used Java version in production (after Java 11/17) and understanding it deeply is table stakes for any Java engineer.

---

## 2. Intuition

> **One-line analogy**: A lambda is a snippet of behavior you hand to a method like a note on a piece of paper — the method reads the note and follows the instructions when it needs to.

**Mental model**: A lambda `x -> x * 2` is a block of code that captures the ambient environment (effectively-final variables) and can be passed around as a value. The Stream API is a lazy pipeline — you describe a sequence of transformations and only the *terminal operation* triggers actual execution. This laziness enables short-circuit optimization (e.g., `findFirst()` stops processing after finding the first match).

**Why it matters**: Streams replaced the ceremonial for-loop for collection processing. Functional interfaces enabled callbacks and event handlers without anonymous class boilerplate. Optional forced explicit null-handling discipline. The Date/Time API replaced the broken, non-thread-safe `Calendar` class.

**Key insight**: Lambdas are *not* compiled to anonymous inner classes (unlike Java 7 workarounds). They use `invokedynamic` bytecode instruction — the JVM defers the exact implementation to runtime via `LambdaMetafactory`, which can inline them more aggressively. This is why lambdas can be faster than anonymous classes in some benchmarks.

---

## 3. Core Principles

- **Functional interface**: An interface with exactly one abstract method (`@FunctionalInterface`). The target type for a lambda.
- **Lambda**: An anonymous function `(params) -> body` that can be assigned to a functional interface.
- **Effectively final**: Local variables captured by a lambda must not be reassigned after capture — the compiler enforces this without requiring the `final` keyword.
- **Stream laziness**: Intermediate operations (filter, map, etc.) don't execute until a terminal operation is called.
- **Stateless operations**: Stream operations should not modify shared state or depend on outside mutation — enables parallel execution.
- **Optional as a return type**: `Optional<T>` signals that a value may be absent. Never use it as a method parameter or field (Effective Java Item 55).

---

## 4. Types / Architectures / Strategies

### 4.1 Core Functional Interfaces

| Interface | Signature | Purpose |
|-----------|-----------|---------|
| `Function<T,R>` | `R apply(T t)` | Transform T to R |
| `Predicate<T>` | `boolean test(T t)` | Filter: true/false |
| `Supplier<T>` | `T get()` | Produce a value |
| `Consumer<T>` | `void accept(T t)` | Consume a value |
| `BiFunction<T,U,R>` | `R apply(T t, U u)` | Two inputs, one output |
| `UnaryOperator<T>` | `T apply(T t)` | Function where T=R |
| `BinaryOperator<T>` | `T apply(T a, T b)` | BiFunction where all same type |
| `Runnable` | `void run()` | No input, no output |
| `Callable<V>` | `V call() throws Exception` | Like Runnable but returns value |

### 4.2 Method Reference Types

| Syntax | Equivalent Lambda | Example |
|--------|------------------|---------|
| `ClassName::staticMethod` | `x -> ClassName.staticMethod(x)` | `Integer::parseInt` |
| `instance::instanceMethod` | `x -> instance.method(x)` | `System.out::println` |
| `ClassName::instanceMethod` | `(x, y) -> x.method(y)` | `String::toUpperCase` |
| `ClassName::new` | `x -> new ClassName(x)` | `ArrayList::new` |

### 4.3 Stream Operations

| Category | Operations |
|----------|-----------|
| Intermediate (lazy) | `filter`, `map`, `flatMap`, `distinct`, `sorted`, `peek`, `limit`, `skip`, `mapToInt/Long/Double` |
| Terminal (eager) | `collect`, `forEach`, `reduce`, `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`, `count`, `min`, `max`, `toArray` |
| Short-circuit | `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`, `limit` |

### 4.4 Key Collectors

| Collector | Output | Example |
|-----------|--------|---------|
| `toList()` | `List<T>` | `stream.collect(toList())` |
| `toSet()` | `Set<T>` | `stream.collect(toSet())` |
| `toMap(k,v)` | `Map<K,V>` | `collect(toMap(Person::id, Person::name))` |
| `groupingBy(f)` | `Map<K, List<V>>` | `collect(groupingBy(Person::dept))` |
| `partitioningBy(p)` | `Map<Boolean, List<V>>` | `collect(partitioningBy(p -> p.age > 18))` |
| `joining(delim)` | `String` | `collect(joining(", "))` |
| `counting()` | `Long` | downstream: `groupingBy(dept, counting())` |
| `summarizingInt(f)` | `IntSummaryStatistics` | min/max/avg/count/sum in one pass |

---

## 5. Architecture Diagrams

### Stream Pipeline
```
Source          Intermediate Ops (lazy)          Terminal Op (triggers execution)
  |                                                         |
List.stream()
  -> filter(p -> p.active)        [lazy - no execution]
  -> map(Person::getName)         [lazy - no execution]
  -> sorted()                     [stateful - buffers all]
  -> limit(10)                    [short-circuit]
  -> collect(toList())            [TRIGGER: pipeline executes now]

Short-circuit example:
  -> filter(...)
  -> findFirst()   -> stops after first match, doesn't process rest
```

### Lambda vs Anonymous Class (Bytecode Level)
```
// Anonymous class (Java 7 style)
Runnable r = new Runnable() {
    public void run() { System.out.println("hello"); }
};
// Compiled to: HelloApp$1.class (separate .class file)
// Allocates new object for each use

// Lambda (Java 8)
Runnable r = () -> System.out.println("hello");
// Compiled to: invokedynamic + LambdaMetafactory
// JVM can reuse same instance if no captured state (non-capturing lambda)
// No new class file, potentially zero allocation
```

### Optional Correct Patterns
```
WRONG: Optional<String> opt = ...; String val = opt.get(); // NPE risk
WRONG: Optional<String> opt = Optional.of(null);          // NPE immediately

CORRECT:
opt.isPresent() ? opt.get() : "default"   // explicit check (verbose)
opt.orElse("default")                      // return default if absent
opt.orElseGet(() -> computeDefault())      // lazy default (supplier)
opt.orElseThrow(() -> new IllegalStateException("missing"))
opt.map(String::toUpperCase)               // transform if present
opt.filter(s -> s.startsWith("A"))        // filter if present
opt.ifPresent(s -> process(s))            // consume if present
```

---

## 6. How It Works — Detailed Mechanics

### Lambda Capture and Effectively Final

```java
// Capturing effectively-final local variable
String prefix = "Hello";  // effectively final - never reassigned
Function<String, String> greet = name -> prefix + " " + name;  // OK

// COMPILE ERROR: captured variable must be effectively final
String greeting = "Hi";
greeting = "Hello";  // reassignment after capture
Function<String, String> f = name -> greeting + name;  // COMPILE ERROR

// WORKAROUND: use array or AtomicReference for mutable capture
AtomicInteger count = new AtomicInteger(0);
list.forEach(item -> count.incrementAndGet());  // OK - count reference is final
```

### flatMap vs map

```java
// map: one-to-one transformation, preserves structure
List<String> words = List.of("hello world", "foo bar");
List<String[]> arrays = words.stream()
    .map(s -> s.split(" "))   // Stream<String[]>
    .collect(toList());        // [[hello, world], [foo, bar]]

// flatMap: one-to-many, flattens one level
List<String> allWords = words.stream()
    .flatMap(s -> Arrays.stream(s.split(" ")))  // Stream<String>
    .collect(toList());  // [hello, world, foo, bar]

// flatMap is monadic bind (M<M<T>> -> M<T>)
// Essential for nested structures: Optional<Optional<T>> -> Optional<T>
Optional<String> outer = Optional.of("value");
Optional<String> result = outer.flatMap(s -> findSomething(s)); // avoids Optional<Optional>
```

### groupingBy Internal Mechanics

```java
// groupingBy(classifier) groups elements by the classifier's result
// Internal behavior: creates HashMap, for each element:
//   key = classifier.apply(element)
//   value = list of matching elements (built via downstream collector)

Map<String, List<Person>> byDept = people.stream()
    .collect(Collectors.groupingBy(Person::getDepartment));

// With downstream collector
Map<String, Long> countByDept = people.stream()
    .collect(Collectors.groupingBy(
        Person::getDepartment,    // classifier
        Collectors.counting()     // downstream
    ));

// Multi-level grouping
Map<String, Map<String, List<Person>>> byDeptThenCity = people.stream()
    .collect(groupingBy(Person::getDept, groupingBy(Person::getCity)));
```

### Lazy Evaluation and Short-Circuit

```java
// Only processes elements until condition met
Optional<Person> firstSenior = people.stream()
    .filter(p -> p.age > 60)     // doesn't run for all elements
    .findFirst();                 // stops after first match

// Without short-circuit, this would be O(n):
// With findFirst() on a sorted stream, it's O(1) amortized

// peek() for debugging without changing pipeline
long count = people.stream()
    .peek(p -> System.out.println("Processing: " + p.name))  // for debugging
    .filter(p -> p.active)
    .peek(p -> System.out.println("Passed filter: " + p.name))
    .count();
```

### Function.andThen vs Function.compose

```java
// andThen: apply THIS function first, then the argument function
// compose:  apply the argument function first, then THIS function
// Mathematical: andThen = g ∘ f where f applied first; compose = f ∘ g where g applied first

Function<Integer, Integer> times2  = x -> x * 2;
Function<Integer, Integer> plus3   = x -> x + 3;

// andThen: times2 THEN plus3
Function<Integer, Integer> times2ThenPlus3 = times2.andThen(plus3);
times2ThenPlus3.apply(5);  // (5 * 2) + 3 = 13

// compose: plus3 FIRST, then times2 (argument applied first)
Function<Integer, Integer> times2AfterPlus3 = times2.compose(plus3);
times2AfterPlus3.apply(5);  // (5 + 3) * 2 = 16

// Mnemonic: andThen reads left-to-right (times2 AND THEN plus3)
//           compose reads right-to-left (math notation: f.compose(g) = f(g(x)))

// Real example: input validation pipeline
Function<String, String> trim       = String::trim;
Function<String, String> upperCase  = String::toUpperCase;
Function<String, String> addPrefix  = s -> "PREFIX_" + s;

Function<String, String> pipeline = trim.andThen(upperCase).andThen(addPrefix);
pipeline.apply("  hello  ");  // "PREFIX_HELLO"
```

### Primitive Streams — Performance Over Boxing

```java
// Stream<Integer> boxes every int -> Integer -> GC pressure, slower
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
int sum1 = numbers.stream()
    .map(x -> x * 2)          // produces Stream<Integer>, each int is boxed
    .reduce(0, Integer::sum);  // unbox for sum -> rebox -> unbox -> ...

// IntStream avoids boxing entirely — elements are primitive int
int sum2 = numbers.stream()
    .mapToInt(Integer::intValue)  // Stream<Integer> -> IntStream (unboxed)
    .map(x -> x * 2)             // stays as int primitive
    .sum();                       // IntStream.sum() - no boxing anywhere

// IntStream.range for index-based iteration (replaces for loop in many cases):
int[] squares = IntStream.range(0, 10)
    .map(i -> i * i)
    .toArray();  // int[] not Integer[]

// Performance comparison (JMH result for 1M elements, simple map+sum):
// Stream<Integer>.map().reduce()  ~85ms  (boxing/unboxing overhead)
// IntStream.map().sum()           ~12ms  (7x faster — pure primitive ops)

// LongStream and DoubleStream follow the same pattern.
// Rule: whenever processing numeric data in a hot path,
// use IntStream/LongStream/DoubleStream instead of Stream<Integer/Long/Double>.

// mapToInt vs map:
numbers.stream().map(x -> x + 1)       // returns Stream<Integer> (boxed)
numbers.stream().mapToInt(x -> x + 1)  // returns IntStream (primitive)

// Converting back to Stream<Integer> if needed:
IntStream.of(1, 2, 3).boxed()  // IntStream -> Stream<Integer>
```

---

## 7. Real-World Examples

- **Netflix**: Uses stream pipelines to process recommendation data — filter active content, map to recommendation scores, sort, and limit to top-N.
- **Financial systems**: `groupingBy(transaction::getCurrency, summingDouble(Transaction::getAmount))` for multi-currency P&L aggregation.
- **Spring Data**: `Optional` as the return type of `findById()` — forces callers to handle the case where an entity doesn't exist.
- **Log processing**: `Files.lines(path).filter(l -> l.contains("ERROR")).collect(toList())` — lazy line processing.

---

## 8. Tradeoffs

| Approach | Pros | Cons |
|----------|------|------|
| Stream vs for-loop | Expressive, composable, lazy | Harder to debug, overhead for small collections |
| `Optional.orElseGet()` vs `orElse()` | `orElseGet()`: lazy (supplier) — avoids evaluating default when not needed | `orElse()` always evaluates the argument |
| `parallelStream()` | Faster for large, CPU-bound, independent ops | Wrong for I/O, small data, non-associative ops, ordering |
| `collect(toList())` vs `toUnmodifiableList()` | `toList()` (Java 16): always unmodifiable, more efficient | older `toList()` returns modifiable list |

---

## 9. When to Use / When NOT to Use

**Use streams when**:
- Processing collections of data with filter/map/reduce patterns
- You want composable, readable pipelines
- Working with `Files.lines()`, `IntStream.range()`, or other stream sources

**Do NOT use streams when**:
- The loop body has side effects or needs break/continue with complex conditions
- Performance is critical and the collection is small (< ~100 elements, overhead noticeable in JMH)
- You need index-based access (`IntStream.range()` helps but awkward)
- Checked exceptions must propagate (streams don't support checked exceptions cleanly)

**Use `Optional` when**:
- A method might return no result (replace `null` returns in APIs)

**Do NOT use `Optional` when**:
- As a method parameter — use overloading instead
- As a field in a class — not serializable, memory overhead
- For collections — return empty collection instead

---

## 10. Common Pitfalls

### War Story 1: Streams are consumed once
A developer stored a `Stream<T>` in a field, used it twice. The second use threw `IllegalStateException: stream has already been operated upon or closed`. **Fix**: Streams are single-use pipelines. Store the `Collection`, not the `Stream`. Re-call `.stream()` each time.

### War Story 2: `Optional.get()` without check
Teams using `Optional` incorrectly called `opt.get()` without `isPresent()` — same NPE problem they were trying to avoid, just wrapped in `NoSuchElementException`. **Fix**: Never call `.get()` directly. Use `orElse()`, `orElseGet()`, `orElseThrow()`, or `map()`/`ifPresent()`.

### War Story 3: Parallel stream on I/O
A team wrapped a database-calling `map()` operation in `parallelStream()`, expecting speed improvement. Instead, all tasks blocked on I/O, starved the `ForkJoinPool.commonPool()` (which is shared across the JVM), and slowed every other parallel stream in the application. **Fix**: Parallel streams are for CPU-bound, non-blocking operations. Use `CompletableFuture` with a custom executor for I/O parallelism.

### War Story 4: `toMap()` collision throws exception
`Collectors.toMap(Person::getId, Person::getName)` throws `IllegalStateException: Duplicate key` if two persons have the same id. **Fix**: Always provide a merge function for production code: `toMap(Person::getId, Person::getName, (a, b) -> a)`.

### War Story 5: sorted() is stateful — buffers entire stream
`stream().sorted()` must see all elements before it can emit the first — it materializes the entire stream into memory. For large data sets this causes GC pressure. **Fix**: Sort the source collection before streaming, or use `limit()` with `sorted()` carefully.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `java.util.stream.Stream` | Core stream API |
| `java.util.function.*` | Functional interfaces |
| `java.util.Optional` | Null-safe return type |
| `java.time.*` | Date/Time API (LocalDate, ZonedDateTime, Duration, Period) |
| `Collectors` | Built-in terminal reduction strategies |
| IntelliJ IDEA | Stream trace debugger (shows each stage's elements) |

---

## 12. Interview Questions with Answers

**Q1: What is a functional interface? How does `@FunctionalInterface` help?**
A functional interface has exactly one abstract method (SAM — Single Abstract Method). It is the target type for a lambda or method reference. `@FunctionalInterface` is a compile-time annotation that causes a compiler error if the interface has more than one abstract method, preventing accidental interface evolution that would break all existing lambdas. Examples: `Runnable`, `Callable`, `Comparator`, `Predicate`, `Function`.

**Q2: How do lambdas differ from anonymous inner classes?**
Four key differences: (1) `this` — inside a lambda, `this` refers to the enclosing instance; inside an anonymous class, `this` refers to the anonymous class instance. (2) No separate `.class` file — lambdas use `invokedynamic` + `LambdaMetafactory`; anonymous classes compile to separate `$N.class` files. (3) Non-capturing lambdas can be instance-reused by the JVM, reducing allocation. (4) Lambdas cannot have state (no fields); anonymous classes can.

**Q3: What is the difference between `map()` and `flatMap()` in Streams?**
`map()` applies a one-to-one function: `Stream<T> → Stream<R>` where each element maps to exactly one result. `flatMap()` applies a one-to-many function: `Stream<T> → Stream<R>` where each element maps to a `Stream<R>`, and all streams are concatenated (flattened) into one. Think of `flatMap` as "map then flatten." It's the monadic bind operation — it removes one level of nesting: `Optional<Optional<T>>` → `Optional<T>` via `flatMap`.

**Q4: What is lazy evaluation in streams, and why does it matter?**
Intermediate operations (filter, map, peek) are lazy — they don't execute until a terminal operation is called. The pipeline is a description of transformations, not the execution. This matters because: (1) Short-circuit operations (`findFirst`, `anyMatch`, `limit`) can stop processing early — e.g., `findFirst()` processes only enough elements to find one, not all. (2) Stateful operations (`sorted`, `distinct`) must materialize the stream but only when forced. (3) No unnecessary computation for elements filtered out early.

**Q5: Why should Optional NOT be used as a method parameter?**
Using `Optional` as a parameter forces the caller to wrap their value in `Optional.of()` or `Optional.empty()` — awkward and verbose. It also obscures intent (does `null` mean "not provided" or is it a bug?). The correct patterns are: use method overloading (one method with the parameter, one without), or use a null check with `Objects.requireNonNull()`. `Optional` is designed purely as a return type to signal "this method may return nothing."

**Q6: What are intermediate vs terminal operations? Give examples of each.**
Intermediate operations are lazy, return a new `Stream`, and don't trigger computation: `filter`, `map`, `flatMap`, `distinct`, `sorted`, `peek`, `limit`, `skip`. Terminal operations are eager, trigger the pipeline, and produce a non-stream result or side effect: `collect`, `forEach`, `reduce`, `count`, `min`, `max`, `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`, `toArray`. A pipeline must end with exactly one terminal operation.

**Q7: How does `groupingBy()` work internally?**
`groupingBy(classifier)` is equivalent to: iterate elements, apply classifier to get a key, insert element into the value list for that key in a `HashMap`. Internally it uses `HashMap<K, List<T>>` with a downstream collector (default: `toList()`). The combiner function is used for parallel streams to merge partial maps. For `counting()` as downstream: instead of `List`, it tracks `Long` counts, making it `Map<K, Long>`.

**Q8: When should you avoid streams and prefer a regular for-loop?**
(1) Hot paths with very small collections — stream pipeline overhead (lambda dispatch, boxing for primitives) can dominate. (2) Complex control flow requiring `break`, `continue`, or checked exception propagation. (3) Multiple side effects or mutations needed per element. (4) Code that's clearer as imperative (sometimes a for-loop is simply more readable). Rule of thumb: for collections under ~50 elements in hot paths, measure with JMH — streams are often comparable but occasionally slower.

**Q9: What is the difference between `orElse()` and `orElseGet()`?**
`orElse(T value)` always evaluates the argument, even if the Optional is present — if computing the default is expensive (DB call, object creation), you pay the cost every time. `orElseGet(Supplier<T> supplier)` only invokes the supplier when the Optional is empty — lazy evaluation. Rule: use `orElseGet()` whenever the default is computationally expensive or has side effects.

**Q10: How do method references compile, and what are their four types?**
Method references compile to the same `invokedynamic` mechanism as lambdas. The four types: (1) `ClassName::staticMethod` → `args -> ClassName.staticMethod(args)`. (2) `instance::instanceMethod` → `args -> instance.method(args)` (captures `instance`). (3) `ClassName::instanceMethod` → `(instance, args) -> instance.method(args)` (receiver is first argument). (4) `ClassName::new` → `args -> new ClassName(args)` (constructor reference). The compiler infers which functional interface type the method reference matches.

**Q11: Explain the Date/Time API and why it replaced Calendar.**
`java.util.Date` and `Calendar` had three critical flaws: mutable (not thread-safe), poor API design (month is 0-indexed, confusing methods), and no separation of concepts. `java.time.*` (JSR-310): `LocalDate` (no time, no timezone), `LocalTime` (no date, no timezone), `LocalDateTime`, `ZonedDateTime` (with timezone), `Instant` (epoch milliseconds), `Duration` (time-based), `Period` (date-based). All immutable. Parseable with `DateTimeFormatter`. Operations like `plusDays()`, `minusMonths()` return new instances. Use `Instant` for timestamps, `LocalDate` for business dates, `ZonedDateTime` for user-facing times with timezone.

**Q12: What is the effectively-final requirement for lambda captures, and why does Java require it?**
A variable captured by a lambda must be effectively final — either explicitly `final` or never reassigned after initialization. Java requires this because lambdas can outlive the method that created them (e.g., submitted to a thread pool). If a local variable could be mutated after capture, the lambda might see a stale value (the lambda gets a copy of the primitive/reference, not a live view). Making captured variables final ensures the semantics are clear: the lambda sees the value at capture time, forever. For mutable state, use `AtomicInteger`, `AtomicReference`, or an array of size 1.

**Q13: What is the difference between `Function.andThen()` and `Function.compose()`?**
`andThen(g)` returns a function that applies `this` first, then `g`: result is `g(f(x))` where `f` is `this`. `compose(g)` returns a function that applies `g` first, then `this`: result is `f(g(x))`. Mnemonic: `andThen` reads left-to-right — "do f, AND THEN do g." `compose` reads right-to-left — mathematical function composition `f ∘ g`. Example: `trim.andThen(toUpperCase)` → trim first, then uppercase. `toUpperCase.compose(trim)` → same result. They differ in which function is "outer": `andThen` makes the argument the outer function; `compose` makes `this` the outer function.

**Q14: Why prefer `IntStream` over `Stream<Integer>` in performance-critical paths?**
`Stream<Integer>` requires boxing every primitive `int` into a heap-allocated `Integer` object. For 1M integers: 1M `Integer` allocations, GC pressure from short-lived objects, and CPU time for boxing/unboxing at every operation. `IntStream` stores elements as bare `int` primitives — no boxing, no heap allocation, cache-friendly. JMH benchmarks consistently show `IntStream` 5-10x faster than `Stream<Integer>` for numeric workloads. The standard library provides `IntStream`, `LongStream`, and `DoubleStream` for this reason. Always use `mapToInt()`/`mapToLong()`/`mapToDouble()` to convert to primitive streams when processing numbers.

**Q15: What is the difference between `Optional.of()`, `Optional.ofNullable()`, and `Optional.empty()`, and what are the anti-patterns for `Optional` usage?**
`Optional.of(value)` throws `NullPointerException` if `value` is null — use when you know the value is non-null and want to fail-fast. `Optional.ofNullable(value)` wraps null as `Optional.empty()` — use when the value may legitimately be null. `Optional.empty()` returns the canonical empty instance (a singleton). Common anti-patterns: (1) **Using `Optional.get()` without `isPresent()` check** — throws `NoSuchElementException`, defeating the purpose; use `orElse()`, `orElseGet()`, or `orElseThrow()` instead. (2) **`Optional` as a method parameter** — forces callers to wrap values; use overloading or nullable parameters. (3) **`Optional` as a field** — `Optional` is not `Serializable`, making the class non-serializable; use nullable field with a `getX()` returning `Optional`. (4) **Unnecessary `isPresent()` + `get()`** — use `map()`/`flatMap()` for transformations. The canonical use case for `Optional` is as a return type for a method that may have no result, explicitly communicating the absence to the caller.

---

## 13. Best Practices

1. **Use `Collectors.toUnmodifiableList()` or `Stream.toList()` (Java 16)** for read-only results.
2. **Prefer `orElseGet()` over `orElse()` for expensive defaults** — avoid unnecessary computation.
3. **Never use `parallelStream()` without benchmarking** — it uses ForkJoinPool.commonPool() shared across JVM.
4. **Use `IntStream`/`LongStream`/`DoubleStream`** for numeric processing to avoid boxing overhead.
5. **Add `@FunctionalInterface` annotation** to all custom single-method interfaces.
6. **Use method references over lambdas** when the lambda is just a single method call — more readable.
7. **Keep stream pipelines short and readable** — extract complex lambdas to named methods for testability.
8. **Provide merge function to `toMap()`** to handle duplicate keys gracefully.
9. **Use `Stream.of()` for small, known elements** and `list.stream()` for collections.
10. **Replace `new Date()` / `Calendar.getInstance()` with `Instant.now()`** in all new code.

---

## 14. Case Study

### Rewriting a User-Profile ETL from Imperative Loops to Streams + Optional

**Scenario.** A user-profile service ingests **10M profile records/day** (~115 records/sec sustained, ~2,000/sec peak during nightly batch). The legacy enrichment job is a 200-line imperative ETL: nested `for` loops, manual null checks at every level (`profile -> address -> city -> zipCode`), and a hand-rolled `HashMap` grouping by country. It throws ~4,000 `NullPointerException`s/day (profiles with partial addresses) which abort whole batches, forcing reruns. The rewrite to Java 8 (LTS) Streams + Optional eliminates the NPEs and cuts the code to ~40 lines.

```
  10M records/day
        |
        v
  [ read ] --> Stream<Profile>
        |
        +--> map: Optional chain (profile -> address -> city -> zipCode)
        |        never throws NPE; missing levels collapse to Optional.empty()
        |
        +--> filter: keep valid, enrichable profiles
        |
        +--> collect: groupingBy(country) -> Map<Country, List<EnrichedProfile>>
                          |
                          v
                  downstream sink
```

#### Null-safe nested access with chained Optional

```java
record Address(String city, String zipCode) {}
record Profile(String userId, Address address, String country) {}

// BROKEN (legacy): pyramid of null checks; one missed branch = NPE that aborts batch
String zip;
if (profile != null && profile.address() != null
        && profile.address().city() != null) {
    zip = profile.address().zipCode();      // still NPE if address present but zip null
} else {
    zip = "UNKNOWN";
}
```

```java
// FIX: flat Optional chain. Any missing link short-circuits to the default.
String zip = Optional.ofNullable(profile)
        .map(Profile::address)
        .map(Address::zipCode)
        .filter(z -> !z.isBlank())
        .orElse("UNKNOWN");           // never throws, never returns null
```

#### Grouping 10M records by country in one pass

```java
Map<String, List<Profile>> byCountry = profiles.stream()
        .filter(p -> p.address() != null)              // enrichable only
        .collect(Collectors.groupingBy(
                p -> Optional.ofNullable(p.country()).orElse("UNKNOWN"),
                Collectors.toList()));

// Or a count histogram in the same idiom:
Map<String, Long> countByCountry = profiles.stream()
        .collect(Collectors.groupingBy(Profile::country, Collectors.counting()));
```

The 200-line imperative ETL collapses to a single declarative pipeline. JMH on a representative 1M-record slice showed the Stream version within ~5% of the hand-tuned loop (the JIT inlines the lambdas), while eliminating the entire class of NPE batch aborts — the operational win dwarfs the micro-benchmark difference.

#### The full enrichment pipeline, end to end

```java
record EnrichedProfile(String userId, String country, String zip, String tier) {}

List<EnrichedProfile> enriched = profiles.stream()
        .filter(p -> p.address() != null)                      // drop un-enrichable
        .map(p -> new EnrichedProfile(
                p.userId(),
                Optional.ofNullable(p.country()).orElse("UNKNOWN"),
                Optional.ofNullable(p.address())               // chained Optional, no NPE
                        .map(Address::zipCode)
                        .filter(z -> !z.isBlank())
                        .orElse("UNKNOWN"),
                tierFor(p)))                                   // pure function, easily testable
        .collect(Collectors.toList());
```

Each stage is independently unit-testable, and the missing-data handling is uniform: a null at any level of the address chain collapses to `"UNKNOWN"` instead of aborting the batch. Before the rewrite, a single malformed record killed the whole 10M-record run; now it produces one `UNKNOWN`-filled row.

#### Aggregating with `Collectors` downstream collectors

```java
// Average zip-completeness per country in one declarative pass.
Map<String, Double> completenessByCountry = enriched.stream()
        .collect(Collectors.groupingBy(
                EnrichedProfile::country,
                Collectors.averagingDouble(e -> e.zip().equals("UNKNOWN") ? 0.0 : 1.0)));
```

### Common Pitfalls (production war stories)

**1. `Optional.get()` without a presence check.** A developer wrote `findFirst().get()` to fetch a "primary" address. For users with no address it threw `NoSuchElementException`, aborting the batch — the exact failure mode Optional was meant to remove.

```java
Address a = list.stream().filter(Address::isPrimary).findFirst().get();   // BROKEN
Address a = list.stream().filter(Address::isPrimary).findFirst()
                .orElse(Address.empty());                                 // FIX
```

**2. `parallel()` on an I/O-bound stage.** Someone parallelized the enrichment stage, which made a blocking REST call per record. All tasks ran on the shared common `ForkJoinPool` (size = cores - 1, ~7 threads), so the unrelated nightly report stream — also using the common pool — starved and missed its SLA. Fix: keep I/O off the common pool; use a dedicated `ExecutorService` or `CompletableFuture` with your own pool.

**3. `Collectors.toMap()` throwing on duplicate keys.** Grouping profiles into `toMap(Profile::userId, p -> p)` threw `IllegalStateException: Duplicate key` because a few user IDs appeared twice in a day's feed.

```java
.collect(Collectors.toMap(Profile::userId, p -> p));                     // BROKEN on dup
.collect(Collectors.toMap(Profile::userId, p -> p, (a, b) -> b));        // FIX: merge fn
```

**4. Nested stream inside a parallel stream.** An inner `inner.parallelStream()` nested within an outer `outer.parallelStream()` flooded the common pool with re-entrant tasks, causing thread-starvation deadlock-like stalls. Keep at most one level of parallelism; make the inner stream sequential.

### Interview Discussion Points

**When should you NOT use `parallel()`?** When the work is I/O-bound, the dataset is small (< ~10k elements), the per-element cost is tiny, or the pipeline is stateful/order-dependent. Parallel streams use the shared common ForkJoinPool, so blocking tasks there harm every other parallel stream in the JVM.

**Is `Optional` meant for fields and method parameters?** No (Effective Java Item 55). It is designed for return types where "no result" is a normal outcome. As a field it adds an allocation and serialization headaches; as a parameter it just pushes null-handling to the caller.

**Why does `Collectors.toMap` throw on duplicates but `groupingBy` does not?** `toMap` produces one value per key and has no defined way to combine collisions unless you supply a merge function. `groupingBy` accumulates all values for a key into a downstream collector, so duplicates are expected and handled by design.

**What is the difference between `map` and `flatMap` on Optional?** `map` wraps the result: `Optional<Optional<T>>` if the mapper returns an Optional. `flatMap` flattens one level, so chaining methods that themselves return `Optional` uses `flatMap` to avoid nesting.

**Why did the imperative version throw NPEs the Stream version avoids?** Each `Optional.map` step internally checks presence before invoking the next mapper and short-circuits to `empty()` on any null. The pyramid of manual `!= null` checks is easy to get partially wrong (e.g. checking `address` but not `zipCode`); the chain makes the null-propagation uniform and total.

**What is the difference between `orElse` and `orElseGet`?** `orElse(x)` always evaluates `x`, even when the Optional is present, so a costly default is computed needlessly. `orElseGet(supplier)` invokes the supplier only on absence. Use `orElseGet` whenever the default is expensive or has side effects.

**Why is `averagingDouble`/`summingDouble` preferable to mapping then averaging manually?** The downstream collector folds each element directly into the running aggregate in a single traversal, avoiding an intermediate collection and keeping the operation parallel-safe via the collector's combiner.

**Does the Stream rewrite hurt throughput at 10M records/day?** No meaningfully — at ~115 records/sec sustained the bottleneck is I/O and downstream calls, not stream overhead, and JMH showed the in-memory transform within ~5% of a hand loop. The correctness and readability gains dominate, which is the typical real-world tradeoff for Java 8 stream adoption.

---

## Related / See Also

- [Java Streams — Deep Dive](../java_streams/README.md) — full stream internals, Spliterator, parallel splitter mechanics
- [Functional Programming](../functional_programming/README.md) — function composition, custom Collectors, memoization
- [Java 9–21 Features](../java9_to_21_features/README.md) — records, sealed classes, virtual threads building on Java 8 foundations

**When would you keep an imperative loop instead?** For tight numeric inner loops where you need fine control over allocation and early-exit, or where a stream's lambda capture and boxing would add overhead the JIT cannot remove. Profile with JMH before assuming streams are slower — they usually are not for I/O-bound ETL.
