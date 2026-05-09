# Flyweight Pattern

## 1. Pattern Name & Category

**Pattern:** Flyweight
**Category:** Structural (GoF)
**GoF Classification:** Structural Design Pattern — Chapter 4 of "Design Patterns: Elements of Reusable Object-Oriented Software" by Gamma, Helm, Johnson, Vlissides.

---

## 2. Intent

Use sharing to support a large number of fine-grained objects efficiently, by separating intrinsic (shared, immutable) state from extrinsic (context-specific) state.

---

## Intuition

> **One-line analogy**: Flyweight is like font rendering — you don't create a new "A" character object for every "A" in a 100-page document. Instead, you share one "A" object (shared intrinsic state: shape, style) and only remember its position in each document (extrinsic state: context-specific).

**Mental model**: When you have a huge number of similar objects, most of their state is identical across instances. Flyweight separates the state into intrinsic (shared, immutable — stored in the Flyweight) and extrinsic (unique per instance — passed in at use time). You maintain a pool (factory) of Flyweight instances; clients look up or create one and pass the extrinsic state at call time.

**Why it matters**: Flyweight can reduce memory usage dramatically for object-heavy scenarios — game engines (millions of tree/particle objects), text editors (millions of character objects), browsers (many DOM nodes sharing style data). Without it, 1M similar objects = 1M × object_size memory; with Flyweight, potentially 1M × extrinsic_data_size + few × intrinsic_data_size.

**Key insight**: Flyweight trades code complexity (you must separate state into intrinsic/extrinsic) for memory efficiency. Only apply it when you actually have massive numbers of similar objects AND memory is a bottleneck — don't apply it prematurely.

---

## 3. Problem Statement

### The Problem
Some applications require a very large number of objects — potentially millions. Creating a distinct object for each unit of data causes severe memory pressure. If many of these objects share the same core data, maintaining separate copies is wasteful. The challenge is: how do you support a large number of granular objects without running out of memory?

### Scenario
Consider a text editor rendering a 500-page document. Each character on the screen is an object with properties like:
- **Character code** (e.g., 'A', 'b', '3') — same across all 'A's in the document
- **Font family** (e.g., "Arial") — same for all characters in a paragraph
- **Font size** (e.g., 12pt) — same for a section
- **Position on screen** (x, y) — unique per character instance
- **Color** — may vary

A 500-page document might have 1,500,000 characters. If each character is a full object carrying font, size, and color data, this becomes hundreds of MB just for character objects. However, the font/size/style data is *repeated* across millions of characters — it does not need to be stored per-character.

---

## 4. Solution

Split the character object's state into two categories:
- **Intrinsic state**: Data that is shared and immutable — character code, font family, font size, style. Store this *once* in a shared Flyweight object.
- **Extrinsic state**: Data that is context-specific — position (x, y), color override. This is *not* stored in the Flyweight; it is passed in by the client at render time.

A **FlyweightFactory** manages a pool of Flyweight objects keyed by their intrinsic state. When a client requests a character, the factory returns the existing shared Flyweight if it exists, or creates and caches a new one. The client holds a reference to the shared Flyweight plus stores its own extrinsic state separately. Memory for common character types is allocated only once regardless of how many times they appear.

---

## 5. UML Structure

```
+------------------+          +---------------------------+
|  FlyweightFactory|          |     <<interface>>         |
|------------------|          |       Flyweight            |
| - pool: Map      |--------->|---------------------------|
|------------------|          | + operation(extrinsicState)|
| + getFlyweight() |          +---------------------------+
+------------------+                    ^
                                        |
                          +-------------+-------------+
                          |                           |
               +---------------------+   +------------------------+
               |  ConcreteFlyweight  |   | UnsharedConcreteFlyweight|
               |---------------------|   |------------------------|
               | - intrinsicState    |   | - allState             |
               |---------------------|   |------------------------|
               | + operation(extState)|  | + operation(extState)  |
               +---------------------+   +------------------------+

Client:
  - Holds reference to Flyweight (shared)
  - Maintains extrinsic state locally
  - Passes extrinsic state when calling operation()
```

---

## 6. How It Works — Step-by-Step

1. **Client requests a Flyweight** from the FlyweightFactory, providing the intrinsic state key (e.g., `factory.getCharacter('A', "Arial", 12)`).
2. **Factory checks its pool** (a HashMap): if a Flyweight with that key exists, it returns the cached instance.
3. **If not found**, the factory creates a new ConcreteFlyweight, stores it in the pool, and returns it.
4. **Client stores extrinsic state** (e.g., position x=100, y=200) in its own data structure (e.g., a list of character positions).
5. **When rendering**, the client calls `flyweight.render(x, y, color)` — passing extrinsic state as parameters.
6. **The Flyweight uses its intrinsic state** (font, character code) combined with the passed extrinsic state to perform the operation.
7. The same ConcreteFlyweight instance handles thousands of render calls for the character 'A' — one object, many contexts.

---

## 7. Key Components

| Role | Description |
|------|-------------|
| **Flyweight (interface/abstract)** | Declares the interface through which Flyweights can receive and act on extrinsic state. |
| **ConcreteFlyweight** | Implements Flyweight, stores intrinsic state. Must be shareable — must not maintain extrinsic state. |
| **UnsharedConcreteFlyweight** | Not all Flyweights need to be shared. Some may hold combined state and not participate in sharing (optional). |
| **FlyweightFactory** | Creates and manages the pool of Flyweight objects. Ensures flyweights are shared correctly. Returns existing instances or creates new ones. |
| **Client** | Maintains references to Flyweights, computes or stores extrinsic state, and passes it to Flyweights when invoking operations. |

---

## 8. When to Use

Use Flyweight when **ALL** of the following are true:
- The application uses a **large number** of objects (thousands to millions).
- **Storage costs are high** due to the sheer quantity of objects.
- **Most object state can be made extrinsic** (moved outside the object and passed in).
- **Many groups of objects share the same intrinsic state** and can be replaced by a smaller set of shared objects.
- The application does not depend on object identity — two flyweights with the same intrinsic state are interchangeable.

### Examples
- Text editors rendering characters
- Game engines rendering trees, particles, bullets, enemies of the same type
- GUI frameworks managing icons, cursors, or repeated UI components
- Network applications managing connection objects or protocol state
- Database connection pools (a form of flyweight for connections)

---

## 9. When NOT to Use

- **When objects have mostly unique state**: If every object has fundamentally different intrinsic data, there is nothing to share — the pattern adds complexity without benefit.
- **When object count is small**: The factory overhead (hashing, lookups) outweighs savings for small numbers of objects.
- **When object identity matters**: If clients need to distinguish between two 'A' characters (e.g., for selection or mutation), sharing breaks the model.
- **When extrinsic state is expensive to pass**: If extrinsic state is large or complex, passing it on every method call offsets the memory savings.
- **When mutability is required**: Flyweights should be immutable (intrinsic state must not change). If objects need to change their core state, flyweight is inappropriate.

---

## 10. Pros

- **Dramatic memory reduction**: Sharing objects that would otherwise be duplicated millions of times can reduce memory by orders of magnitude.
- **Improved performance for large datasets**: Fewer object allocations means less GC pressure and better cache locality.
- **Centralized intrinsic state management**: The factory becomes the single source of truth for shared state.
- **Transparent to clients**: Clients interact with flyweights via a standard interface and may not even know sharing is happening.
- **Scales well**: Memory consumption grows with the number of *unique* intrinsic states, not total object count.
- **Works well with Factory and Pool patterns**: Combines naturally with object pools for lifecycle management.

---

## 11. Cons

- **Increased complexity**: The separation of intrinsic/extrinsic state complicates the design and is non-obvious to developers unfamiliar with the pattern.
- **Extrinsic state management burden**: The client must track and pass extrinsic state on every call — this responsibility is shifted to the caller.
- **Immutability requirement**: Flyweights must be immutable, which can be restrictive in domains where objects naturally need to change.
- **Factory overhead**: The FlyweightFactory adds a lookup cost (hash map get) on every object request.
- **Debugging difficulty**: Because objects are shared, it is harder to associate a flyweight with a specific context during debugging.
- **Concurrency concerns**: Shared mutable state in flyweights (even accidentally) creates race conditions in multi-threaded environments.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Massive memory savings for large object counts | Code complexity — intrinsic/extrinsic separation is not obvious |
| Reduced GC pressure | Extrinsic state management is now the client's responsibility |
| Ability to support millions of objects | Shared objects cannot carry mutable per-instance state |
| Centralized shared data (single source of truth) | Debugging is harder — one object serves many contexts |
| Better CPU cache performance | Factory lookup overhead on every object retrieval |

---

## 13. Common Pitfalls

1. **Storing extrinsic state inside the Flyweight**: This breaks sharing — each "shared" object ends up holding context-specific data and you cannot share it. Always pass extrinsic state as method parameters.
2. **Making Flyweights mutable**: If a client modifies a flyweight (e.g., changes font size), it accidentally affects every other client sharing that flyweight. Enforce immutability with `final` fields and no setters.
3. **Forgetting to use the factory**: Clients that `new` their own Flyweights bypass sharing entirely — the pattern provides zero benefit. Always go through the FlyweightFactory.
4. **Not synchronizing the factory in concurrent environments**: The factory's pool (HashMap) must be thread-safe. Use `ConcurrentHashMap` or synchronize the `getFlyweight` method.
5. **Over-applying the pattern**: Using Flyweight for small object counts adds complexity with no benefit. Profile memory usage first to confirm the problem exists.
6. **Confusing Flyweight with Singleton**: Singleton ensures one instance globally. Flyweight ensures one instance *per unique intrinsic state* — there can be many flyweight instances, just not duplicated ones.

---

## 14. Real-World Usage

### Java Standard Library
- **`Integer.valueOf(int)`** — Java caches Integer objects for values -128 to 127. `Integer.valueOf(42)` always returns the same object. This is a Flyweight pool.
- **`String.intern()`** — the JVM String pool interns strings to avoid duplicate string objects. All string literals are automatically interned.
- **`Boolean.TRUE` / `Boolean.FALSE`** — only two Boolean instances exist; `Boolean.valueOf()` returns shared instances.
- **`Byte`, `Short`, `Character`** — similar caching ranges exist for these wrapper types.

### Java AWT/Swing
- **`Font` objects** — fonts are typically shared; the same Font object is reused across many text components with the same rendering properties.

### Game Development (Unity/Unreal concepts)
- Sprite sheets / texture atlases — a single texture object is shared among thousands of game entities; each entity stores its own position/rotation (extrinsic).

### Database Connection Pools (HikariCP, c3p0)
- Connection objects represent an expensive resource; a pool of shared connections is maintained. Each client borrows a connection (gets the flyweight), uses it, and returns it.

### Apache Commons Pool
- Generic object pooling library implementing the flyweight/pool concept for any expensive object.

---

## 15. Comparison with Similar Patterns

| Pattern | Purpose | Key Difference |
|---------|---------|----------------|
| **Singleton** | Ensures one global instance | Flyweight has one instance *per unique state*; Singleton has one global instance regardless |
| **Prototype** | Creates objects by cloning | Prototype *creates copies*; Flyweight *shares* the same instance across contexts |
| **Object Pool** | Reuses expensive objects | Pool manages object lifecycle (checkout/return); Flyweight focuses on sharing read-only intrinsic state |
| **Proxy** | Controls access to one object | Proxy wraps *one* object for access control; Flyweight manages a *pool of shared* objects |
| **Composite** | Composes objects into trees | Composite structures hierarchy; Flyweight optimizes memory for large numbers of leaf nodes — often used together |

---

## 16. Interview Tips

**Q: Explain the Flyweight pattern with a real example.**
A: Use Java's `Integer.valueOf()` cache — it is concrete, well-known, and perfectly illustrates intrinsic state (the int value) that is shared. Then explain the text editor scenario for depth.

**Q: What is intrinsic vs extrinsic state?**
A: Intrinsic state is stored inside the flyweight — it is context-independent and shared. Extrinsic state is context-dependent — it changes with each use and is passed to the flyweight by the client. This separation is the core insight of the pattern.

**Q: How does Flyweight differ from Singleton?**
A: Singleton ensures ONE instance total. Flyweight ensures ONE instance per unique intrinsic state — there can be many flyweight types but no duplicates within a type.

**Q: What are the risks of using Flyweight?**
A: Extrinsic state management complexity, immutability constraints, factory thread safety, and the risk of accidentally storing extrinsic state inside the flyweight.

**Q: Is Java's String pool a Flyweight?**
A: Yes — it is a great example. String literals with the same value share the same object in the string pool. `"hello" == "hello"` is true because they are the same flyweight instance.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Flyweight Appears in Distributed Systems**

- **Connection pooling** — A connection pool is a Flyweight registry: physical DB connections (heavy, shared state) are pooled; callers borrow and return them. The intrinsic state (driver config, credentials) is shared; the extrinsic state (current transaction) is per-caller.
- **Compiled regex caching** — API gateways and validation services compile regex patterns once and cache them as Flyweights. Re-compiling on every request would waste CPU; sharing the compiled pattern across threads is safe because `Pattern` is immutable.
- **Shared config objects** — Stateless microservices share a single immutable `AppConfig` Flyweight across all request-handling threads. No per-request config copy is needed; thread safety comes from immutability.
- **HTTP thread pool** — Worker threads in a thread pool are Flyweights: the pool maintains a fixed set of threads (shared, expensive objects); request context (the extrinsic state) is passed in per task, not stored on the thread.

---

## 17. Best Practices

1. **Make Flyweights strictly immutable**: Use `final` fields for all intrinsic state. Provide no setters. This guarantees thread safety and safe sharing.
2. **Always use the FlyweightFactory**: Never allow direct instantiation of Flyweights. Make constructors package-private or private and enforce factory access.
3. **Use thread-safe collections in the factory**: `ConcurrentHashMap` or `computeIfAbsent` for lock-free creation of new flyweights in concurrent environments.
4. **Clearly separate intrinsic and extrinsic state in documentation**: Comment which fields are intrinsic (stored) vs which are extrinsic (passed as parameters). This prevents future developers from accidentally adding extrinsic fields.
5. **Profile before applying**: Only apply Flyweight after measuring a real memory problem. Premature optimization with Flyweight adds significant complexity.
6. **Consider using records (Java 16+)**: Java records are immutable by design and work excellently as flyweights. Their structural equality also aids key-based caching.
7. **Design the extrinsic state API carefully**: Passing 10 parameters as extrinsic state to every method call is unwieldy. Consider grouping extrinsic state into a context/render-context object.
