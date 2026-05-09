# Prototype Pattern

## 1. Pattern Name & Category

**Name:** Prototype
**Category:** Creational (GoF)
**GoF Classification:** Gang of Four — Creational Design Pattern
**Book Reference:** "Design Patterns: Elements of Reusable Object-Oriented Software" (Gamma et al., 1994)

---

## 2. Intent

Specify the kinds of objects to create using a prototypical instance, and create new objects by copying (cloning) this prototype — avoiding the cost of constructing from scratch and decoupling client code from concrete classes.

---

## Intuition

> **One-line analogy**: Prototype is like a Xerox machine — instead of hand-crafting a new document from scratch, you copy an existing one and make modifications. The original stays intact.

**Mental model**: When object creation is expensive (database queries, complex initialization) or you want to decouple client code from concrete classes, you clone an existing "prototype" object instead of creating from scratch. The prototype knows how to clone itself (implements `clone()`). The client just asks for a copy without knowing the object's class.

**Why it matters**: Prototype shines for objects with expensive initialization (game entities, UI configurations). Instead of re-running expensive setup, you create one prototype and clone it for each new instance. It's also used in prototype registries where you register named prototypes and clone them by name.

**Key insight**: The key distinction is deep vs. shallow copy — shallow copy shares nested object references (changes to one affect others); deep copy creates independent copies of everything. Always clarify which semantics you need before implementing clone().

---

## 3. Problem Statement

### The Core Problem
Creating objects is sometimes expensive — not just in CPU time, but in the sense of complexity: loading from a database, running computation, connecting to a remote service, or resolving a deeply configured object graph. If you need many objects that are largely similar (differing only in a few fields), constructing each one from scratch is wasteful.

Additionally, in some systems the client code should not need to know the concrete class of the object it is working with. Using `new ConcreteClass()` ties the client to a specific type. If the client only knows the interface, it cannot call `new`.

### The Scenario
You are building a 2D game with hundreds of enemy units. Each enemy has:
- A sprite loaded from disk (expensive: 50ms per load)
- A physics body initialized from configuration (expensive: database lookup)
- A behavior tree compiled from a script (expensive: parsing + compilation)
- A handful of runtime fields: position, health, id (cheap to vary)

Creating 500 enemy units by constructing each from scratch = 25 seconds of loading. Instead, you create one fully-initialized `EnemyPrototype` (load once = 50ms), then clone it 500 times (each clone: copy fields = microseconds), adjusting position and health per clone. Total: 50ms + negligible.

### What We Need
1. A way to copy a pre-built, expensive object cheaply.
2. The copy must be independent of the original — mutating the copy must not affect the original (and vice versa).
3. Client code should clone without knowing the concrete class.
4. A registry to hold named prototypes that clients can clone by name.

---

## 4. Solution

Define a `Prototype` interface with a `clone()` method. Each concrete class implements `clone()` to return a copy of itself. Clients call `prototype.clone()` instead of `new ConcreteClass()`. An optional `PrototypeRegistry` stores pre-configured named prototypes; clients request a clone by name without knowing the underlying type.

---

## 5. UML Structure

```
        +----------------------------+
        |   <<interface>>            |
        |      Prototype             |
        +----------------------------+
        | + clone(): Prototype       |
        +----------------------------+
                    ^
         ___________|___________
        |                       |
+------------------+   +------------------+
| ConcretePrototype|   | ConcretePrototype|
|       A          |   |       B          |
+------------------+   +------------------+
| - fieldA         |   | - fieldX         |
| - nested: Config |   | - items: List    |
+------------------+   +------------------+
| + clone()        |   | + clone()        |
+------------------+   +------------------+

+----------------------------+
|    PrototypeRegistry       |   (optional)
+----------------------------+
| - registry: Map<String,    |
|             Prototype>     |
+----------------------------+
| + register(key, proto)     |
| + getClone(key): Prototype |
+----------------------------+

Client ──> PrototypeRegistry.getClone("enemy") ──> ConcretePrototypeA.clone()
                                                         |
                                                    returns new copy
```

**Relationships:**
- `ConcretePrototype` implements `Prototype` and returns a copy of itself from `clone()`.
- `PrototypeRegistry` stores prototypes keyed by name; returns clones, not the originals.
- `Client` codes to the `Prototype` interface — it never calls `new ConcretePrototype()`.

---

## 6. Shallow Copy vs Deep Copy

This is the most important concept in the Prototype pattern and the most common source of bugs.

### Shallow Copy
Copies all field values as-is. For primitive types and immutable objects (e.g., `String`, `Integer`), this is safe. For mutable reference types (e.g., `List`, `HashMap`, custom objects), the copy and the original share the same nested object reference.

```
Original:   [ name="Enemy" | health=100 | config ──────────> Config{speed=5} ]
Shallow:    [ name="Enemy" | health=100 | config ──────────> Config{speed=5} ]
                                                              (SAME OBJECT)
```

**Consequence:** If the clone mutates `config.speed`, the original also sees the change.

### Deep Copy
Recursively copies all nested objects. Each level of the object graph produces a fresh, independent copy.

```
Original:   [ name="Enemy" | health=100 | config ──────────> Config{speed=5} ]
Deep:       [ name="Enemy" | health=100 | config ──────────> Config{speed=5} ]
                                                              (DIFFERENT OBJECT, same values)
```

**Consequence:** Mutations to the clone's `config` are fully isolated from the original.

### Summary Table

| Aspect | Shallow Copy | Deep Copy |
|--------|-------------|-----------|
| Primitives / immutables | Copied by value — safe | Copied by value — same |
| Mutable nested objects | Shared reference — DANGEROUS | New independent copy — safe |
| Performance | Fast — no recursion | Slower — allocates new objects at each level |
| Implementation | Copy each field directly | Copy constructor / recursive clone at every level |
| When to use | No mutable nested state, or nested objects are intentionally shared | Nested objects must be independent between clone and original |

---

## 7. Key Components

| Component | Role |
|-----------|------|
| **Prototype** | Interface (or abstract class) declaring `clone()`. Clients depend on this interface — never on concrete types. |
| **ConcretePrototype** | Implements `clone()`. Decides whether to do a shallow or deep copy. Keeps intrinsic expensive state; lets client vary extrinsic state after cloning. |
| **PrototypeRegistry** | Optional map of name → prototype. Provides `getClone(key)` — clients request copies by name without knowing the concrete class. Stores the originals and always returns clones, never the originals themselves. |
| **Client** | Calls `clone()` on a Prototype (obtained from the registry or directly) instead of `new ConcreteClass()`. Customizes the clone after creation. |

---

## 8. Pros

- **Performance**: Cloning a pre-initialized object is orders of magnitude cheaper than re-constructing from scratch when construction involves I/O, computation, or network calls.
- **Decouples client from concrete classes**: The client calls `prototype.clone()` and gets back a `Prototype`. It never needs to import or reference the concrete class.
- **Reduces subclassing**: Without Prototype, adding a new object configuration requires a new subclass or a new factory. With Prototype, you just register a new pre-configured instance.
- **Runtime object composition**: New "types" can be added to the registry at runtime by registering new prototypes — no recompilation.
- **Preserves complex initialization**: The prototype carries the result of expensive setup. Clones inherit this fully-initialized state.

---

## 9. Cons

- **Deep copy complexity**: Implementing a correct deep clone for a deeply nested, circular, or polymorphic object graph is non-trivial and error-prone.
- **Cloning breaks encapsulation**: The `clone()` method must access all private fields of its own class (and nested classes). This couples the implementation to its own internals.
- **Circular references require careful handling**: If object A references object B and B references A, a naive recursive deep clone loops infinitely. You need a visited-set to break cycles.
- **`java.lang.Cloneable` is broken in Java**: `Cloneable` is a marker interface that doesn't declare `clone()`. `Object.clone()` does a shallow copy and throws `CloneNotSupportedException`. This is a notorious Java design mistake — prefer a custom `Prototype` interface with explicit copy constructors.
- **Hidden shared state**: A shallow clone looks independent but isn't. Bugs from accidentally shared nested objects can be subtle and hard to trace.

---

## 10. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Fast creation of similar objects | Deep copy implementation complexity |
| Decoupling from concrete classes | Risk of accidental shared state (shallow copy) |
| Dynamic "types" via registry | `clone()` breaks encapsulation |
| Preservation of expensive initialization | Circular reference handling burden |
| Runtime extensibility via registration | `java.lang.Cloneable` API is awkward |

---

## 11. Common Pitfalls

1. **Shallow copy when deep copy is needed**: The most common Prototype bug. If any mutable nested object is shared between the clone and the original, mutations in one unexpectedly affect the other. Always audit every field type when implementing `clone()`.

2. **Using `java.lang.Cloneable` and `Object.clone()`**: Java's built-in mechanism is notorious: the interface declares no methods, `Object.clone()` does a shallow copy, and calling it on a non-`Cloneable` class throws a checked exception. Prefer a custom `Prototype` interface with a copy constructor at each level.

3. **Cloning mutable collections without copying their contents**: `new ArrayList<>(original.list)` copies the list structure but not the elements inside it. If list elements are mutable objects, mutating them in the clone still affects the original.

4. **Registry returns the original instead of a clone**: If `PrototypeRegistry.getClone()` accidentally returns `registry.get(key)` instead of `registry.get(key).clone()`, all callers share the same object — the registry defeats itself.

5. **Circular references in deep clone**: Object A → Object B → Object A. A naive deep clone recurses infinitely. Use an `IdentityHashMap` to track already-cloned objects.

6. **Not implementing `clone()` in every subclass**: If a subclass adds a field and forgets to override `clone()`, the base class `clone()` runs — producing a clone of the wrong type that is missing the subclass field.

---

## 12. When to Use

- **Object creation is expensive**: Loading from DB, disk, network; complex computation; large initialization graphs.
- **Many similar objects needed**: Game units, document templates, UI component configurations — where objects share most state but vary in a few fields.
- **Client should be decoupled from concrete types**: The client only knows the `Prototype` interface; a registry provides the actual objects.
- **Dynamic type configuration at runtime**: New object "variants" are added by registering new prototypes — no new classes needed.
- **Copying state at a point in time**: Snapshot patterns — capture the current state of an object and clone it for undo/redo or comparison.

---

## 13. Real-World Examples

### `java.lang.Object.clone()` and `java.lang.Cloneable`
Java's built-in mechanism. Despite its design flaws, it is the JDK's native Prototype support. `ArrayList`, `HashMap`, and most collection classes implement `Cloneable` and override `clone()` to produce shallow copies.

```java
ArrayList<String> original = new ArrayList<>(List.of("a", "b", "c"));
ArrayList<String> copy = (ArrayList<String>) original.clone(); // shallow copy of the list
```

### Spring Framework — Prototype Bean Scope
Spring's `@Scope("prototype")` annotation tells the container to create a new instance (clone the configuration) every time the bean is requested — instead of returning a shared singleton.

```java
@Component
@Scope("prototype")
public class RequestContext { ... }
// Each getBean(RequestContext.class) returns a fresh independent instance.
```

### Java's `Object.clone()` in Collections
`java.util.Properties`, `java.util.Hashtable`, and `java.util.LinkedList` all implement `Cloneable` to support prototype-style duplication.

### Game Engine: Object Pooling + Prototype
Unreal Engine, Unity, and most game engines use prototype + pooling for game objects (enemies, bullets, particles). One canonical "prefab" object is the prototype; spawning creates a cheap copy.

### Document Template Systems
Word processors store document templates as prototypes. Opening a new document from a template clones the template rather than rebuilding the document structure from scratch.

---

## 14. Java Code Snippet — Core Pattern

```java
// ── Prototype interface (preferred over java.lang.Cloneable) ────────────────
interface Prototype {
    Prototype clone(); // each implementor decides shallow vs deep
}

// ── Mutable nested object ────────────────────────────────────────────────────
class EnemyConfig {
    int speed;
    int damage;

    EnemyConfig(int speed, int damage) {
        this.speed = speed;
        this.damage = damage;
    }

    // Copy constructor — the clean Java way to deep-copy a value object
    EnemyConfig(EnemyConfig other) {
        this.speed  = other.speed;
        this.damage = other.damage;
    }
}

// ── ConcretePrototype ────────────────────────────────────────────────────────
class Enemy implements Prototype {

    private String type;       // immutable after construction
    private int health;        // mutable: each unit has its own health
    private EnemyConfig config; // mutable nested object

    Enemy(String type, int health, EnemyConfig config) {
        this.type   = type;
        this.health = health;
        this.config = config;
    }

    // Deep clone — produces a fully independent copy
    @Override
    public Enemy clone() {
        return new Enemy(
            this.type,
            this.health,
            new EnemyConfig(this.config) // deep copy via copy constructor
        );
    }

    public void setHealth(int health) { this.health = health; }
    public EnemyConfig getConfig()    { return config; }

    @Override
    public String toString() {
        return "Enemy{type='" + type + "', health=" + health
             + ", speed=" + config.speed + ", damage=" + config.damage + "}";
    }
}

// ── Prototype Registry ───────────────────────────────────────────────────────
class EnemyRegistry {
    private final Map<String, Prototype> registry = new HashMap<>();

    void register(String key, Prototype proto) {
        registry.put(key, proto);
    }

    Prototype getClone(String key) {
        Prototype proto = registry.get(key);
        if (proto == null) throw new IllegalArgumentException("Unknown prototype: " + key);
        return proto.clone(); // always returns a COPY, never the original
    }
}

// ── Client ───────────────────────────────────────────────────────────────────
public class PrototypeDemo {
    public static void main(String[] args) {
        // Create the expensive prototype once
        Enemy archerPrototype = new Enemy("Archer", 80, new EnemyConfig(6, 15));

        EnemyRegistry registry = new EnemyRegistry();
        registry.register("archer", archerPrototype);

        // Spawn 3 archers by cloning
        Enemy a1 = (Enemy) registry.getClone("archer");
        Enemy a2 = (Enemy) registry.getClone("archer");
        Enemy a3 = (Enemy) registry.getClone("archer");

        // Customize each clone independently
        a1.setHealth(60);  // a1 is wounded
        a2.getConfig().speed = 9; // a2 is fast (only affects a2, not prototype)

        System.out.println(archerPrototype); // Enemy{type='Archer', health=80, speed=6}
        System.out.println(a1);              // Enemy{type='Archer', health=60, speed=6}
        System.out.println(a2);              // Enemy{type='Archer', health=80, speed=9}
        System.out.println(a3);              // Enemy{type='Archer', health=80, speed=6}
    }
}
```

---

## 15. Interview Tips

### Common Questions

**Q: What is the Prototype pattern and when would you use it?**
A: Prototype creates new objects by cloning an existing one instead of constructing from scratch. Use it when object construction is expensive (DB load, network call, complex init), when you need many similar objects that differ in a few fields, or when client code should not depend on concrete types.

**Q: What is the difference between shallow copy and deep copy?**
A: Shallow copy copies field values as-is. For primitive and immutable fields this is safe. For mutable nested objects, the copy and original share the same reference — mutating one affects the other. Deep copy recursively clones all nested objects, producing a fully independent copy. The choice depends on whether nested objects need to be independently mutable in the clone.

**Q: What is wrong with Java's `Cloneable`?**
A: `Cloneable` is a marker interface that does not declare the `clone()` method. `Object.clone()` does a shallow copy and is `protected` by default. The method throws `CloneNotSupportedException` if `Cloneable` is not implemented — a checked exception for a pattern that should be natural. The recommended Java alternative is a custom `Prototype` interface with copy constructors at each level.

**Q: How does Spring's `@Scope("prototype")` relate to the Prototype pattern?**
A: Spring's prototype scope means the DI container creates a new instance of the bean every time it is requested, rather than returning a shared singleton. This is the Prototype pattern's intent at the framework level — the container manages the "registry" and "cloning" transparently.

**Q: How do you handle circular references in a deep clone?**
A: Maintain a `Map<Object, Object>` (using `IdentityHashMap` to use reference equality) tracking already-cloned objects. Before cloning any nested object, check if it is already in the map. If so, return the existing clone instead of recursing. This breaks the cycle.

**Q: Prototype vs Factory Method — when do you choose Prototype?**
A: Use Factory Method when you need to decide *which class* to instantiate. Use Prototype when you need a *copy* of an expensive or pre-configured object. Factory Method creates new objects from scratch; Prototype creates new objects from an existing blueprint.

### Key Phrases to Use
- "Cloning is cheaper than construction from scratch"
- "Shallow copy — shared mutable state — the main pitfall"
- "Deep copy — fully independent — requires copy constructors"
- "`java.lang.Cloneable` is broken — prefer custom `Prototype` interface"
- "Prototype Registry — decouples client from concrete types"
- "Spring `@Scope(\"prototype\")` is a framework-level Prototype"

---

## Cross-Perspective: HLD Connections

**HLD View — Where Prototype Appears in Distributed Systems**

- **Distributed config snapshots** — When a config service pushes an update, each subscriber clones its current config snapshot, applies the delta, and atomically replaces the old reference. Cloning is faster and safer than reconstructing from scratch.
- **Connection pool pre-warming** — Connection pools clone a prototype connection to pre-fill the pool on startup, avoiding the cold-start cost of establishing N connections sequentially.
- **Deployment template cloning** — CI/CD systems clone a base environment configuration (prototype) when spinning up ephemeral preview or test environments, overriding only environment-specific values.
- **Caching pre-populated objects** — Object caches store a prototypical response and clone it per-request, preventing shared mutable state bugs while avoiding the overhead of full reconstruction.

---

## 16. Best Practices

1. **Prefer a custom `Prototype` interface over `java.lang.Cloneable`** — declare `clone()` explicitly on your interface, use copy constructors in concrete classes, and avoid the gotchas of `Object.clone()`.

2. **Use copy constructors for nested objects** — `new NestedConfig(other.config)` is explicit, clear, and does not require reflection or casting.

3. **Default to deep copy unless sharing is intentional** — shallow copy creates subtle shared-state bugs that are hard to trace. Only use shallow copy when you explicitly intend for nested objects to be shared.

4. **Registry should always return clones, never originals** — `getClone(key)` must call `proto.clone()` before returning. Returning the original defeats the pattern and introduces shared mutable state.

5. **Declare fields as `final` where possible in value objects** — this forces you to use copy constructors and makes deep copy safe.

6. **Override `toString()` in prototypes** — during debugging, knowing the exact field values of a clone vs. the original is essential for catching shallow-copy bugs.

7. **Document whether `clone()` is shallow or deep** — this is a critical contract that callers depend on. If the signature is the same for both, the Javadoc is the only signal.

8. **Use serialization for deep copy as a last resort** — serializing and deserializing an object graph produces a deep copy of any `Serializable` object. It works on arbitrary graphs (handles cycles) but is slow and requires all objects to be `Serializable`.

9. **Test clone independence explicitly** — in unit tests, always mutate a nested object in the clone and assert the original is unchanged. This is the only reliable way to catch shallow-copy bugs.

10. **Consider the Memento pattern as an alternative for snapshots** — if your goal is to capture state for undo/redo rather than to produce new working objects, Memento may be more appropriate than Prototype.
