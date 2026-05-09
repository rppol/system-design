# Iterator Pattern

## 1. Pattern Name & Category

**Pattern:** Iterator
**Category:** Behavioral (Gang of Four)
**Also Known As:** Cursor

---

## 2. Intent

Provide a way to sequentially access elements of an aggregate object without exposing its underlying representation.

---

## Intuition

> **One-line analogy**: Iterator is like a conveyor belt at a factory — items come out one at a time in order; you don't need to know the factory's internal layout to get the next item.

**Mental model**: Different collections (arrays, linked lists, trees, hash maps) store data differently internally. Without Iterator, client code must know the internal structure to traverse — tightly coupling client to implementation. Iterator provides a uniform `hasNext()` / `next()` interface regardless of the underlying structure. Swap an ArrayList for a LinkedList and the traversal code doesn't change.

**Why it matters**: Iterator is built into most programming languages (Java's Iterable/Iterator, Python's `__iter__`/`__next__`, C++ iterators). Every for-each loop uses Iterator under the hood. It's one of the most fundamental patterns because iteration is ubiquitous — understanding it explains how language constructs work internally.

**Key insight**: External iterators (client controls iteration via next()) are flexible; internal iterators (collection controls iteration, you pass a function) are simpler. Java streams and Python generators extend the Iterator concept with lazy evaluation — elements are computed on demand, not pre-loaded.

---

## 3. Problem Statement

### The Problem
Collections (lists, trees, graphs, sets) have different internal structures, but clients often need to traverse them in a uniform way. If traversal logic is embedded in the collection class, it bloats the class with multiple traversal algorithms (forward, backward, depth-first, breadth-first). If clients traverse directly (via index, pointer, or knowledge of internal structure), they are tightly coupled to the collection's implementation.

The problem compounds when you need to support multiple simultaneous traversals of the same collection, or when the traversal algorithm must vary independently from the collection.

### Scenario: File System Navigator
A file system tool must traverse directories in multiple ways:
- List files alphabetically
- List files by modification date
- Traverse recursively depth-first
- Traverse recursively breadth-first

If the traversal logic lives in the `Directory` class, it becomes a god class with multiple traversal methods. If UI code manipulates the directory tree directly (using `children[i]`), the UI is coupled to the tree structure. Any change to the internal representation breaks the UI.

### Scenario: Social Network Feed
A social network's feed can be ordered chronologically, by relevance, or by trending topics. The `FeedCollection` shouldn't need to know about all these orderings. Instead, different iterator implementations encapsulate each ordering strategy.

---

## 4. Solution

The Iterator pattern separates the traversal logic from the collection:
1. Define an `Iterator` interface with `hasNext()` and `next()` methods (and optionally `remove()`, `reset()`).
2. Each collection implements an `Iterable` interface (or equivalent) with a `createIterator()` method.
3. Concrete iterators encapsulate the traversal logic and maintain the current traversal position.
4. Clients use only the Iterator interface — they never access the collection's internal structure directly.

Now the collection can have multiple iterator types (e.g., `AlphabeticIterator`, `RecursiveDepthFirstIterator`) without changing the collection class or the client code.

---

## 5. UML Structure

```
  +-----------------+            +------------------+
  |  <<interface>>  |            |  <<interface>>   |
  |    Iterable     |            |    Iterator<T>   |
  +-----------------+            +------------------+
  | +iterator():    |            | + hasNext():bool |
  |   Iterator<T>   |            | + next(): T      |
  +-----------------+            | + remove(): void |
          ^                      +------------------+
          |                               ^
  +------------------+                   |
  | ConcreteAggregate|        +--------------------+
  +------------------+        | ConcreteIterator   |
  | - elements       |        +--------------------+
  | +iterator()      |------->| - aggregate        |
  +------------------+        | - position: int    |
                               | + hasNext()        |
                               | + next()           |
                               +--------------------+

  Client
    |
    |---uses---> Iterator interface only
    |                  |
    |              hasNext() / next()
    |                  |
    |           ConcreteIterator navigates
    |                  |
    |           ConcreteAggregate (hidden from client)
```

**Java's built-in structure:**
```
Iterable<T>  <----  Collection<T>  <----  ArrayList<T>
    |                                          |
    +-- iterator() --> Iterator<T>  <-- ArrayList$Itr
                           |
                      hasNext(), next(), remove()
```

---

## 6. How It Works

1. **Client calls** `aggregate.iterator()` to get an Iterator.
2. The **ConcreteAggregate** creates a `ConcreteIterator`, typically passing `this` (the collection) to it.
3. The **ConcreteIterator** stores the collection reference and initializes its position (e.g., `index = 0`).
4. **Client calls** `iterator.hasNext()` to check if more elements remain.
5. **Client calls** `iterator.next()` to get the current element and advance the position.
6. The iterator uses its position + the collection to retrieve elements without exposing internal structure.
7. **Multiple iterators** can exist simultaneously on the same collection, each maintaining independent state.

**For external vs internal iterators:**
- **External iterator** (most common): Client controls the loop (`while(it.hasNext()) it.next()`).
- **Internal iterator**: Collection controls the loop, client provides a callback (`collection.forEach(elem -> ...)`).

---

## 7. Key Components

| Component | Role |
|-----------|------|
| **Iterator** | Interface with `hasNext()`, `next()`, optional `remove()` and `reset()` |
| **ConcreteIterator** | Maintains traversal state (position); implements the traversal algorithm |
| **Aggregate (Iterable)** | Interface with `iterator()` factory method |
| **ConcreteAggregate** | Creates the appropriate ConcreteIterator; contains the actual data |
| **Client** | Uses Iterator interface; never touches the aggregate's internal structure |

---

## 8. When to Use

- **Uniform traversal** — when multiple collection types must be traversable with identical client code.
- **Multiple traversal algorithms** — when the same collection needs to be traversed in different orders (sorted, reversed, filtered).
- **Hiding internal structure** — when you want to expose traversal without exposing the collection's data structure.
- **Concurrent traversal** — when multiple clients need to traverse the same collection simultaneously with independent positions.
- **Lazy evaluation** — when generating elements on demand (e.g., infinite sequences, paged API results, database cursors).
- **Custom data structures** — trees, graphs, tries, rings — non-standard structures that benefit from a standard traversal API.
- **Infinite sequences** — iterators can generate Fibonacci numbers, prime numbers, etc. without pre-computing all values.

**Concrete examples:**
- Java's `Iterable`/`Iterator` — every `Collection` implements this.
- Java's `Scanner` — iterates over tokens in a stream.
- Database `ResultSet` — iterates over query results.
- Java `DirectoryStream` — iterates over file system entries.
- Java `Stream` — lazy pipeline built on top of iterators/spliterators.

---

## 9. When NOT to Use

- **Simple array/list traversal** — Java's for-each loop and indexed access are cleaner for simple cases.
- **When the collection is accessed only once** — the abstraction overhead is unnecessary.
- **Performance-critical tight loops** — iterator abstraction adds overhead vs. direct index access.
- **When Java's built-in iteration suffices** — don't re-implement what `java.util.Iterator` and for-each already provide.
- **Functional pipelines** — Java Streams (`stream().filter().map()`) are more expressive for transformation pipelines than custom iterators.
- **Random access patterns** — iterators are sequential; if you need random access, use indexed collections directly.

---

## 10. Pros

- **Single Responsibility Principle** — traversal logic lives in the iterator, not the collection.
- **Open/Closed Principle** — add new traversal algorithms (new iterators) without modifying the collection.
- **Uniform interface** — clients traverse arrays, linked lists, trees, and graphs with identical code.
- **Multiple simultaneous traversals** — independent iterators maintain independent positions.
- **Lazy evaluation** — elements can be generated or fetched on demand (useful for large datasets, infinite sequences).
- **Hides implementation** — clients don't depend on array indices, node pointers, or hash buckets.
- **Language integration** — implementing `Iterable`/`Iterator` enables Java for-each loops.
- **Composability** — iterators can be stacked (FilteringIterator wrapping a CollectionIterator).

---

## 11. Cons

- **Overhead** — for simple indexed collections, iterator abstraction is slower than direct index access.
- **Not suitable for random access** — iterators are strictly forward (or bidirectional); arbitrary access requires resetting.
- **Concurrent modification** — if the collection is modified during iteration, most iterators throw `ConcurrentModificationException`.
- **Verbosity** — explicitly creating and using an iterator is more verbose than a for-each loop.
- **Stateful complexity** — complex iterators (e.g., for trees or graphs) must track significant traversal state.
- **Forced order** — once you choose an iterator, you're locked into its traversal order for that pass.
- **Resource management** — iterators over external resources (DB connections, file handles) must be closed; forgetting causes leaks.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Uniform traversal API | Performance vs. direct access |
| Hidden collection internals | Random access capability |
| Independent simultaneous traversal | Simplicity (for trivial cases) |
| New algorithms without modifying collection | Complexity for concurrent modification handling |
| Lazy/on-demand element generation | Extra object allocation per iteration |

---

## 13. Common Pitfalls

1. **`ConcurrentModificationException`** — modifying a collection while iterating it (adding/removing elements) causes this exception. Use `iterator.remove()` instead of `collection.remove()` during iteration, or use a `CopyOnWriteArrayList`.

2. **Not closing resource iterators** — iterators over databases, files, or network streams must be closed. Use try-with-resources (`for (X x : stream)` auto-closes in Java 7+).

3. **Implementing `next()` without checking `hasNext()`** — if the client calls `next()` when exhausted, it should throw `NoSuchElementException`, not return null (null return is ambiguous).

4. **Forgetting `remove()` semantics** — `remove()` removes the element returned by the LAST `next()` call, not the current position. Many custom iterators implement it incorrectly.

5. **Making the iterator heavy** — complex iterators that eagerly load or compute all results lose the lazy evaluation benefit. Compute elements on demand in `next()`.

6. **Exposing the backing collection through the iterator** — if the iterator exposes a setter or direct reference, encapsulation is broken.

7. **Not implementing `Iterable`** — in Java, implementing `Iterator` without making your aggregate `Iterable` means it can't be used in for-each loops.

8. **Stateless iterator on mutable collection** — if the collection changes between `hasNext()` and `next()` calls, results are undefined. Store a snapshot or use fail-fast checks.

---

## 14. Real-World Usage

### Java Standard Library
- `java.util.Iterator<E>` — the foundational interface for all Java collection traversal.
- `java.util.ListIterator<E>` — bidirectional iterator for Lists with `hasPrevious()`, `previous()`, `add()`, `set()`.
- `java.util.Scanner` — tokenizes input streams; implements `Iterator<String>`.
- `java.nio.file.DirectoryStream<Path>` — iterates over directory entries lazily.
- `java.sql.ResultSet` — iterates over database query results (forward-only cursor).
- `java.util.Spliterator` — parallel-aware splittable iterator used by Java Streams.

### Spring Framework
- `org.springframework.core.io.support.PathMatchingResourcePatternResolver` uses iterators internally.
- Spring Data `Streamable<T>` — extends Iterable with Stream conversion.
- Spring Batch `ItemReader` — a Command/Iterator hybrid for reading batch processing items.

### Java Collections Framework
- Every `Collection` is `Iterable`; every `Collection` provides an `Iterator`.
- `TreeSet`/`TreeMap` iterators provide sorted order.
- `LinkedHashMap` iterator provides insertion-order traversal.

### Google Guava
- `Iterators` and `Iterables` utility classes for transforming, filtering, and composing iterators.
- `AbstractIterator` — a base class simplifying custom iterator implementation.

---

## 15. Comparison with Similar Patterns

| Pattern | Key Difference |
|---------|---------------|
| **Composite** | Composite structures the aggregate (tree). Iterator traverses it. They are complementary — you iterate over a Composite. |
| **Visitor** | Visitor defines an operation over elements while traversing. Iterator only provides traversal; the client decides what to do with each element. |
| **Factory Method** | `createIterator()` on the aggregate IS a Factory Method — it creates the right iterator for the collection. |
| **Strategy** | An iterator IS a strategy for traversal. Different iterators represent different traversal strategies for the same collection. |
| **Memento** | An iterator's state (position) can be captured as a Memento, enabling "bookmark" functionality in a traversal. |

---

## 16. Interview Tips

**Common interview questions:**

**Q: What is the Iterator pattern and why does Java use it?**
A: Iterator provides a uniform way to traverse collections without exposing their internal structure. Java uses it so that arrays, linked lists, trees, and hash sets are all traversable with identical `hasNext()`/`next()` code, and for-each loops work with any `Iterable`.

**Q: What's `Iterable` vs `Iterator`?**
A: `Iterable` is the collection side — it has `iterator()` that creates a new Iterator. `Iterator` is the traversal state — it has `hasNext()` and `next()`. Implementing `Iterable` enables for-each loops. You can have multiple active Iterators on one Iterable simultaneously.

**Q: What is `ConcurrentModificationException` and how do you avoid it?**
A: It's thrown when the collection is structurally modified during iteration (detected via a `modCount` counter). Avoid it by: using `iterator.remove()` instead of `collection.remove()`, using `CopyOnWriteArrayList`, or collecting modifications and applying them after iteration.

**Q: How would you implement a binary tree iterator?**
A: Use an explicit stack to simulate recursive in-order traversal. Push the root's left spine onto the stack. `next()` pops a node, then pushes the right child's left spine. `hasNext()` checks if the stack is non-empty.

**Q: What is a `Spliterator`?**
A: Java 8's parallel-aware iterator that can split itself for parallel processing. It supports characteristics (ORDERED, SIZED, etc.) and is used internally by `Stream`.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Iterator Appears in Distributed Systems**

- **API pagination** — Cursor-based pagination is Iterator at the API level: the client calls `next(cursor)` repeatedly, advancing through result pages. The server maintains no per-client state — the cursor encodes position. Used by Stripe, GitHub, Slack APIs.
- **Kafka consumer iteration** — Kafka consumers iterate over partition offsets. `consumer.poll()` advances the iterator; committing the offset persists position; seeking to an earlier offset resets the iterator for replay — a stateful, resettable Iterator over a distributed log.
- **Database cursor** — JDBC `ResultSet` and database cursors are Iterators over query results. Lazy loading fetches rows on demand, keeping memory bounded for large result sets.
- **Stream processing batches** — Flink, Spark Streaming, and Kafka Streams iterate over micro-batches of events. The streaming framework manages the iteration; business logic processes one batch at a time via the Iterator interface.

---

## 17. Best Practices

1. **Implement `Iterable<T>` on your aggregates** — this enables for-each loops and integrates with Java's ecosystem.
2. **Throw `NoSuchElementException` from `next()`** when exhausted — never return null.
3. **Use fail-fast behavior** — track `modCount` and throw `ConcurrentModificationException` if the collection changes during iteration.
4. **Implement lazy evaluation** — compute/fetch elements in `next()`, not in the constructor.
5. **Extend `AbstractIterator` (Guava)** for custom iterators — it handles `hasNext()`/`next()` boilerplate; you only implement `computeNext()`.
6. **Use `ListIterator` when bidirectional traversal is needed** — don't roll your own bidirectional iterator.
7. **Close resource iterators** — implement `AutoCloseable` on iterators over external resources; use try-with-resources.
8. **Make iterators stateful but self-contained** — each iterator instance owns its position; never share position state across iterator instances.
9. **Favor `Stream` over custom iterators for transformation pipelines** — `stream().filter().map().collect()` is more expressive than a custom filtering iterator.
10. **Document traversal order and thread safety** — clients need to know if the iterator is ordered, concurrent-safe, or fail-fast.
