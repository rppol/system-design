# Functional Programming

## 1. Concept Overview

Java isn't a purely functional language, but since Java 8 it supports a functional style through lambdas, functional interfaces, and the Stream API. This module goes deeper than the basics: function composition, currying, Collector internals, parallel stream mechanics, and immutability patterns — the techniques that appear in senior code reviews and architecture discussions.

Functional programming in Java means: **pure functions** (no side effects, output depends only on input), **immutability** (data doesn't change after creation), **composition** (build complex behavior from simple building blocks), and **first-class functions** (functions as values that can be passed and returned).

---

## 2. Intuition

> **One-line analogy**: Functional programming in Java is like LEGO — small, self-contained function pieces that snap together in any order because each piece has no hidden state or side effects.

**Mental model**: A pure function is deterministic: same input always produces same output, no observable side effects. Composition is the key technique — `f.andThen(g)` creates a new function that applies `f` then `g`, building a pipeline from small pieces. This makes behavior easy to test (test each function in isolation), easy to reason about (no shared state), and safe to parallelize.

**Why it matters**: Functional patterns appear in stream pipelines, reactive systems, and any collection processing code. Understanding `Collector` internals helps you write custom aggregators. Understanding when parallel streams hurt helps you avoid a common performance trap.

**Key insight**: The critical difference between `andThen` and `compose` (order reversal) is a common interview question and a common source of bugs when functions are composed in the wrong order.

---

## 3. Core Principles

- **Pure function**: No side effects; same input → same output. Enables memoization, parallelism.
- **Immutability**: Once created, data cannot change. No need for defensive copies on return; safe to share across threads.
- **Composition**: `f.andThen(g)` = `x -> g(f(x))`. Pipelines of small, focused functions.
- **Higher-order functions**: Functions that take or return other functions (`map`, `filter`, `reduce`).
- **Referential transparency**: A function call can be replaced with its result without changing program behavior.
- **Collector contract**: supplier + accumulator + combiner + finisher + characteristics. Understanding this contract lets you write custom collectors.

---

## 4. Types / Architectures / Strategies

### 4.1 Function Composition

| Method | On | Meaning | Order |
|--------|-----|---------|-------|
| `f.andThen(g)` | `Function<A,B>` | `x -> g(f(x))` | f first, then g |
| `f.compose(g)` | `Function<A,B>` | `x -> f(g(x))` | g first, then f |
| `p.and(q)` | `Predicate<T>` | `x -> p.test(x) && q.test(x)` | logical AND |
| `p.or(q)` | `Predicate<T>` | `x -> p.test(x) \|\| q.test(x)` | logical OR |
| `p.negate()` | `Predicate<T>` | `x -> !p.test(x)` | logical NOT |

### 4.2 Collector Internals

```
Collector<T, A, R> has 4 components:
  Supplier<A>      supplier()     -- creates empty accumulator
  BiConsumer<A,T>  accumulator()  -- folds element T into accumulator A
  BinaryOperator<A> combiner()   -- merges two accumulators (parallel only)
  Function<A,R>    finisher()    -- transforms accumulator A to result R

Characteristics:
  CONCURRENT  -- accumulator is thread-safe; skip combiner in parallel
  UNORDERED   -- result order doesn't matter (allows parallel optimizations)
  IDENTITY_FINISH -- finisher is identity; skip finisher call
```

### 4.3 Parallel Stream Fork/Join
```
Stream source (List<T> with size n)
  |
  Spliterator (splits source into halves recursively)
  |
  ForkJoinPool.commonPool() (by default)
  |-- Worker thread 1: process sub-range [0, n/4)
  |-- Worker thread 2: process sub-range [n/4, n/2)
  |-- Worker thread 3: process sub-range [n/2, 3n/4)
  |-- Worker thread 4: process sub-range [3n/4, n)
  |
  Combiner: merge partial results
```

---

## 5. Architecture Diagrams

### andThen vs compose (Crucial Difference)
```
Function<Integer, Integer> doubleIt = x -> x * 2;
Function<Integer, Integer> addTen   = x -> x + 10;

doubleIt.andThen(addTen).apply(3):
  Step 1: doubleIt(3) = 6
  Step 2: addTen(6)   = 16
  Result: 16

doubleIt.compose(addTen).apply(3):
  Step 1: addTen(3)   = 13
  Step 2: doubleIt(13) = 26
  Result: 26

Memory aid:
  andThen = "do f, THEN do g"    (f before g)
  compose = "compose f WITH g"   (g before f, like mathematical f∘g)
```

### Immutability Patterns
```
MUTABLE (dangerous):
  class Config {
      List<String> hosts;
      Config(List<String> hosts) { this.hosts = hosts; }
      List<String> getHosts() { return hosts; }  // exposes mutable list
  }
  // Caller: config.getHosts().clear()  -> Config is silently mutated

IMMUTABLE (correct):
  final class Config {
      private final List<String> hosts;
      Config(List<String> hosts) {
          this.hosts = List.copyOf(hosts);  // defensive copy on input
      }
      List<String> getHosts() {
          return Collections.unmodifiableList(hosts);  // defensive copy on output
          // OR: return hosts; (already unmodifiable via List.copyOf)
      }
  }
```

---

## 6. How It Works — Detailed Mechanics

### Custom Collector Implementation

```java
// Example: Collector that builds a formatted summary string
// "Total: 5 items, Sum: 150, Avg: 30.0"

public static Collector<Integer, ?, String> toSummary() {
    return Collector.of(
        // Supplier: create empty accumulator (int[] = [count, sum])
        () -> new int[]{0, 0},

        // Accumulator: fold one element into accumulator
        (acc, val) -> { acc[0]++; acc[1] += val; },

        // Combiner: merge two partial accumulators (for parallel streams)
        (acc1, acc2) -> new int[]{acc1[0] + acc2[0], acc1[1] + acc2[1]},

        // Finisher: convert accumulator to result
        acc -> String.format("Total: %d items, Sum: %d, Avg: %.1f",
            acc[0], acc[1], acc[0] == 0 ? 0.0 : (double) acc[1] / acc[0])
        // No CONCURRENT/UNORDERED/IDENTITY_FINISH characteristics
    );
}

// Usage:
String summary = IntStream.rangeClosed(1, 10).boxed()
    .collect(toSummary());
// "Total: 10 items, Sum: 55, Avg: 5.5"
```

### Parallel Stream — When It Helps vs Hurts

```java
// HELPS: large, CPU-bound, stateless, splittable, associative
long count = LongStream.rangeClosed(1, 1_000_000)
    .parallel()
    .filter(n -> isPrime(n))  // CPU-bound, stateless
    .count();                  // associative

// HURTS: I/O-bound (blocks ForkJoinPool.commonPool())
List<String> results = urls.parallelStream()
    .map(url -> httpClient.get(url))  // WRONG: blocks carrier threads
    .collect(toList());

// HURTS: small collections (overhead > benefit)
List<Integer> small = List.of(1,2,3,4,5);
small.parallelStream().map(x -> x*2).collect(toList());  // slower than sequential

// HURTS: ordered operations with side effects
List<Integer> list = new ArrayList<>();
IntStream.range(0, 100).parallel()
    .forEach(i -> list.add(i));  // RACE CONDITION: ArrayList not thread-safe

// HURTS: non-associative reduce
// sum is associative: (a + b) + c == a + (b + c)  -> parallel OK
// subtraction is not: (a - b) - c != a - (b - c)  -> parallel wrong
```

### Spliterator and Parallel Streams

```java
// Spliterator: tells the parallel splitter how to divide the source
// Key characteristics: SIZED, SUBSIZED, ORDERED, SORTED, DISTINCT, NONNULL, IMMUTABLE, CONCURRENT

// ArrayList has SIZED + SUBSIZED -> perfect binary splitting
// LinkedList has no SIZED -> poor splitting performance
// Stream.of() -> array-backed -> efficient

// Custom Spliterator:
public class RangeSpliterator implements Spliterator<Integer> {
    private int current, end;
    RangeSpliterator(int start, int end) { this.current = start; this.end = end; }

    @Override
    public boolean tryAdvance(Consumer<? super Integer> action) {
        if (current < end) { action.accept(current++); return true; }
        return false;
    }

    @Override
    public Spliterator<Integer> trySplit() {
        int mid = (current + end) >>> 1;
        if (mid <= current) return null;
        int lo = current; current = mid;
        return new RangeSpliterator(lo, mid);
    }

    @Override public long estimateSize() { return end - current; }
    @Override public int characteristics() { return ORDERED | SIZED | SUBSIZED | IMMUTABLE; }
}
```

### Currying and Partial Application in Java

```java
// Currying: transform f(a,b) into f(a)(b)
// Java version: return a Function when you apply the first argument

// BiFunction curried to Function<A, Function<B, C>>
Function<Integer, Function<Integer, Integer>> add = a -> b -> a + b;
Function<Integer, Integer> add5 = add.apply(5);  // partial application
int result = add5.apply(3);  // 8

// Practical partial application: pre-fill a validator
BiFunction<Integer, Integer, Boolean> between = (min, val) -> val >= min;
Function<Integer, Boolean> positiveCheck = between.apply(0);  // partial: min=0

// Method reference as partial application
// String::startsWith is BiFunction<String, String, Boolean>
// "hello"::startsWith is Function<String, Boolean>  (partial: receiver bound)
Predicate<String> startsWithHi = "Hello World"::contains;  // receiver bound
```

---

## 7. Real-World Examples

- **Stream pipeline in reporting**: Filter transactions by date range → group by category → sum amounts → sort descending → limit top 10 → format as report.
- **Custom Collector for histogram**: Collect numeric values into histogram buckets — supplier creates `int[]` of bucket counts, accumulator increments the right bucket, combiner adds bucket arrays.
- **Immutable domain model**: Financial transaction objects are immutable — prevents accidental mutation in concurrent processing; safe to share across threads without copying.
- **Function composition for validation**: Compose `Predicate`s for input validation: `notNull.and(notEmpty).and(maxLength(100))`.

---

## 8. Tradeoffs

| Functional vs Imperative | Functional | Imperative |
|--------------------------|-----------|------------|
| Readability | High for data pipelines | High for complex control flow |
| Debuggability | Harder (pipeline steps) | Easier (step-by-step) |
| Testability | High (pure functions) | Lower (stateful methods) |
| Performance (hot paths) | Possible overhead | Optimal |
| Parallelism | Safe (pure functions) | Dangerous (shared state) |

---

## 9. When to Use / When NOT to Use

**Use functional style when**:
- Processing collections of data (filter/map/collect)
- Building configurable behavior (Strategy pattern with functions)
- Composing validation or transformation pipelines

**Do NOT use functional style when**:
- Performance is critical and the overhead matters (measure with JMH)
- Complex control flow with multiple exit conditions (for-loop is clearer)
- You need checked exceptions to propagate (streams don't support them cleanly)

**Use parallel streams when**:
- Large datasets (>10K elements)
- CPU-bound, stateless operations
- Associative reduce operations

---

## 10. Common Pitfalls

### War Story 1: `andThen` vs `compose` order confusion
A data transformation pipeline had `sanitize.andThen(validate).andThen(transform)` and later refactored to use `compose`. The order was accidentally reversed, causing validation to run before sanitization — dirty data reached the validator and caused false failures. **Fix**: Be explicit, document the order, use integration tests that verify transformation order.

### War Story 2: Non-thread-safe collection in parallelStream forEach
A developer used `.parallelStream().forEach(list::add)` where `list` was an `ArrayList`. Under parallel execution, concurrent `add()` calls caused `ConcurrentModificationException` and data corruption (some elements silently lost). **Fix**: Use `collect(toList())` or `ConcurrentLinkedQueue` or `Collections.synchronizedList()`.

### War Story 3: Stream pipeline with stateful lambda
A lambda captured and modified an external `int[]` counter inside a `parallelStream` — both threads incremented the same array cell, causing data races. **Fix**: Use `count()`, `reduce()`, or `Collectors.counting()` — all thread-safe aggregation mechanisms.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `java.util.function.*` | Core functional interfaces |
| `Collectors` | Built-in and custom collectors |
| `Stream.of()` / `IntStream.range()` | Stream creation |
| `Spliterators` | Custom source splitting for parallel |
| Vavr | Functional data structures (Option, Either, immutable List) for Java |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between `andThen` and `compose` in `Function`?**
Both compose two functions, but the order differs. `f.andThen(g)` applies `f` first, then `g`: equivalent to `x -> g(f(x))`. `f.compose(g)` applies `g` first, then `f`: equivalent to `x -> f(g(x))`. Memory aid: `andThen` means "apply f, then do g afterward"; `compose` matches mathematical function composition notation `f∘g` where g is applied first. For `Predicate`, the equivalents are `and()`, `or()`, `negate()`.

**Q2: What is a Spliterator and why does it matter for parallel streams?**
A `Spliterator` (splittable iterator) describes how a stream's source can be split into sub-ranges for parallel processing. It has `tryAdvance()` (process one element), `trySplit()` (split into two halves), `estimateSize()`, and `characteristics()` (SIZED, ORDERED, etc.). ArrayList has a `Spliterator` that supports balanced binary splitting (SIZED+SUBSIZED), making parallel streams very efficient. LinkedList cannot be efficiently split — `trySplit()` returns null early — so parallel streams over LinkedList fall back to nearly sequential execution.

**Q3: How does a custom `Collector` work? Implement one.**
A `Collector<T, A, R>` has four functions: `Supplier<A>` creates an empty accumulator; `BiConsumer<A,T>` folds each element into the accumulator; `BinaryOperator<A>` merges two partial accumulators (called in parallel mode); `Function<A,R>` finishes — converts accumulator to result. The `combiner` is critical for parallel correctness: if your accumulator isn't safely mergeable, mark the Collector without `CONCURRENT` and the framework handles parallel safely. `Collector.of(supplier, accumulator, combiner, finisher, characteristics)` is the factory.

**Q4: When should you NOT use parallel streams?**
(1) I/O-bound operations — block ForkJoinPool.commonPool() carrier threads. (2) Small collections (<100 elements) — overhead of splitting and combining exceeds gains. (3) Non-associative reduce — `reduce((a,b) -> a-b)` is wrong in parallel (subtraction isn't associative). (4) Ordered operations with side effects — `forEach` ordering is non-deterministic in parallel. (5) When ordering matters — `findFirst()` in parallel may degrade to `findAny()` behavior. (6) When collection is not efficiently splittable (LinkedList, iterators).

**Q5: What makes a stream operation stateful vs stateless, and why does it matter?**
Stateless operations (`filter`, `map`, `flatMap`, `peek`) process each element independently — no memory of previous elements. Stateful operations (`sorted`, `distinct`, `limit`, `skip`) need to see multiple elements before producing output. In parallel streams, stateful operations are performance hazards: `sorted()` must materialize all elements; `distinct()` must track seen elements across threads (synchronized state). For correct parallel execution, prefer stateless pipelines; use stateful ops at the end of the pipeline.

**Q6: How does `flatMap` differ from `map` conceptually?**
`map(f)` transforms each element: `Stream<T> → Stream<R>` where `f: T → R`. `flatMap(f)` transforms and flattens: `Stream<T> → Stream<R>` where `f: T → Stream<R>`. Conceptually, `flatMap` is monadic bind (>>=): it "flattens" one level of nesting. `Optional.flatMap` handles `Optional<Optional<T>> → Optional<T>`. This is the key primitive for monadic composition — chaining operations that each return a wrapped value.

**Q7: What is "effectively final" and why does Java require it for lambda captures?**
A variable is effectively final if it's initialized once and never reassigned. Java requires it because lambdas can outlive the method scope (submitted to a thread pool, stored in a field). Local variables are on the stack; when the method returns, the stack frame is gone. Java copies the value into the lambda's closure. If the variable could change after capture, the lambda would hold a stale copy — misleading semantics. For mutable state, use `AtomicInteger`, `AtomicReference`, or arrays.

**Q8: Explain the difference between `Function<A,B>` and `BiFunction<A,B,C>`.**
`Function<T,R>` takes one argument of type T, returns R. `BiFunction<T,U,R>` takes two arguments (T and U), returns R. Java has no `TriFunction` in the standard library — for 3+ arguments, use a custom functional interface or compose. `BiFunction` is commonly used with `Map.merge()`, `Map.replaceAll()`, and building binary operators. `UnaryOperator<T>` is `Function<T,T>`; `BinaryOperator<T>` is `BiFunction<T,T,T>` — useful for reduce operations.

**Q9: How does Records' auto-generated `equals()` differ from a typical POJO's?**
Record's auto-generated `equals()` is field-by-field comparison using `Objects.equals()` for each component — guaranteed correct by the spec. A typical POJO's `equals()` is whatever the developer wrote (or IDE generated, which may be correct or outdated if fields were added later). The critical difference: if you add a field to a POJO, `equals()` and `hashCode()` must be manually updated; for a Record, they're automatically regenerated at compile time — no risk of forgetting.

**Q10: When would you choose an unmodifiable wrapper vs `List.of()`?**
`Collections.unmodifiableList(list)` creates a view — changes to the underlying list are visible through the wrapper, but the wrapper itself rejects modifications. Use when you need to expose a list as read-only while still being able to mutate it internally. `List.of()` creates a truly immutable, independent list — no backing mutable list. Use for constant data, returning truly immutable results from API methods. Key tradeoff: `unmodifiableList` is a live view (changes propagate through); `List.of()` is a snapshot copy (no live relationship).

---

## 13. Best Practices

1. **Prefer `andThen` over `compose`** for readability — the name matches the execution order.
2. **Document parallel stream assumptions** — which operations are stateless, splittable, associative.
3. **Use custom Collectors over collect-then-transform** for single-pass aggregation.
4. **Make domain objects immutable by default** — use defensive copy in constructors and getters.
5. **Use `Predicate.not()`** (Java 11) for negation: `stream.filter(Predicate.not(String::isEmpty))`.
6. **Prefer `reduce(identity, accumulator, combiner)`** for parallel-safe reductions.
7. **Test Collector combiner** explicitly — parallel correctness depends on the combiner being associative.
8. **Use `List.copyOf()`** (Java 10) to create immutable snapshots of existing collections.
9. **Avoid functional interfaces in performance-critical paths** without JMH verification.
10. **Keep lambda bodies short** — extract to named methods for anything > 3 lines.

---

## 14. Case Study

### Custom `Collector` for Histogram Bucketing

**Problem**: Given a stream of response latencies (ms), produce a histogram with configurable bucket sizes.

```java
public class HistogramCollector {

    public static Collector<Long, ?, Map<String, Long>> toHistogram(long bucketSize) {
        return Collector.of(
            () -> new TreeMap<String, Long>(),

            (map, latency) -> {
                long bucket = (latency / bucketSize) * bucketSize;
                String label = bucket + "-" + (bucket + bucketSize);
                map.merge(label, 1L, Long::sum);
            },

            (map1, map2) -> {
                map2.forEach((k, v) -> map1.merge(k, v, Long::sum));
                return map1;
            },

            Function.identity(),
            Collector.Characteristics.UNORDERED  // order within combiner doesn't matter
        );
    }
}

// Usage:
Map<String, Long> histogram = latencies.stream()
    .parallel()  // combiner makes this parallel-safe
    .collect(HistogramCollector.toHistogram(100));

// Output: {"0-100"=45, "100-200"=128, "200-300"=37, "300-400"=10, ...}
```

**Key concepts**: Custom Collector with combiner for parallel safety, TreeMap for sorted buckets, `merge()` for count aggregation, `UNORDERED` characteristic.
