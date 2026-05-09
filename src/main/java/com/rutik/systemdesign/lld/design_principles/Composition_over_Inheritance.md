# Composition Over Inheritance

## Origins

One of the **two golden rules** from the Gang of Four's "Design Patterns: Elements of Reusable Object-Oriented Software" (1994):

> "Favor object composition over class inheritance."

The GoF identified this principle after observing that inheritance — despite being a central feature of OOP languages — leads to rigid, brittle designs when overused as the primary mechanism for code reuse.

---

## Intuition

> **One-line analogy**: Composition is like building with Lego — you assemble independent bricks (components) into any shape; Inheritance is like molding clay — you start with a parent shape and can only add, never fundamentally restructure without starting over.

**Mental model**: Inheritance locks you into a class hierarchy that's hard to change. If `Bird extends Animal` and `FlyingBird extends Bird`, what do you do with `Penguin` (a bird that doesn't fly)? Composition escapes this: `Bird` has-a `FlightBehavior` (an interface). `Eagle` gets `CanFly`; `Penguin` gets `CannotFly`. Swap behaviors at runtime; mix behaviors freely; no fragile hierarchy to maintain.

**Why it matters**: Deep inheritance hierarchies are the most common source of rigid, brittle design in OOP. The Strategy pattern is composition-over-inheritance in its purest form. Almost every GoF pattern relies on composition as its primary code reuse mechanism.

**Key insight**: Use inheritance when the IS-A relationship is genuine and stable (and when you want to inherit behavior, not just contract). Use composition when you want flexibility, testability (inject mock behaviors), or when the relationship might change. When in doubt, compose.

---

## Definition

**Inheritance** ("IS-A" relationship): a class gains behavior by extending a parent class. Behavior is fixed at compile time. Structure is hierarchical.

**Composition** ("HAS-A" relationship): a class gains behavior by holding references to objects that provide that behavior. Behavior can be assembled at runtime. Structure is flat.

**The principle:** when choosing between the two for code reuse, prefer composition. Reserve inheritance for genuine IS-A relationships.

---

## Problems with Inheritance

### 1. Fragile Base Class Problem

When a base class changes, all subclasses are potentially affected — even if the subclass had no reason to change. A subclass is tightly coupled to the internals of its parent, often in ways that are invisible from the subclass code.

```java
public class Base {
    public void doA() {
        doB(); // calls doB internally
    }
    public void doB() { ... }
}

public class Child extends Base {
    @Override
    public void doB() {
        super.doB();
        // additional logic
    }
}
// If Base.doA() stops calling doB(), Child silently breaks.
```

### 2. Deep Hierarchies

After 3-4 levels of inheritance, understanding what a class actually does requires tracing the entire hierarchy. Debugging becomes an exercise in class archaeology.

### 3. Inflexibility — Cannot Change at Runtime

Inheritance is a compile-time decision. A `FlyingDog` is always a `FlyingDog`. You cannot make it swim at runtime without restructuring the class hierarchy.

### 4. Java's Single Inheritance Constraint

Java only allows a class to extend one parent. In multiple-axis inheritance (a class that needs two behaviors from two unrelated hierarchies), the hierarchy approach forces awkward workarounds.

---

## Java Violation Example

Modeling animals with varying combinations of flying and swimming:

```java
class Animal { ... }
class FlyingAnimal extends Animal { void fly() { ... } }
class SwimmingAnimal extends Animal { void swim() { ... } }

// What about a duck — it both flies and swims?
// Option A: Pick one hierarchy branch, lose the other.
// Option B: Create a separate class:
class SwimmingFlyingAnimal extends Animal {
    void fly() { ... }
    void swim() { ... }
}
// But now: what about a FlyingSwimmingRunningAnimal?
// The hierarchy explodes combinatorially.

class FlyingAnimal extends Animal { ... }
class SwimmingFlyingAnimal extends FlyingAnimal { ... }
class RunningSwimmingFlyingAnimal extends SwimmingFlyingAnimal { ... }
// This is the "class explosion" problem.
```

Every new combination of behaviors requires a new class. The hierarchy becomes unmaintainable.

---

## Compliant Example

Model capabilities as interfaces and compose them:

```java
// Capability interfaces
public interface Flyable {
    void fly();
}

public interface Swimmable {
    void swim();
}

public interface Runnable {
    void run();
}

// Concrete implementations of each capability
public class FlyingBehavior implements Flyable {
    public void fly() {
        System.out.println("Flying with wings");
    }
}

public class SwimmingBehavior implements Swimmable {
    public void swim() {
        System.out.println("Swimming with webbed feet");
    }
}

// Animal composes whatever capabilities it needs
public class Duck implements Flyable, Swimmable {
    private final Flyable flyingBehavior = new FlyingBehavior();
    private final Swimmable swimmingBehavior = new SwimmingBehavior();

    public void fly() { flyingBehavior.fly(); }
    public void swim() { swimmingBehavior.swim(); }
}

public class Eagle implements Flyable {
    private final Flyable flyingBehavior = new FlyingBehavior();
    public void fly() { flyingBehavior.fly(); }
}

// With dependency injection: behaviors can be changed at runtime
public class Animal {
    private Flyable flyingBehavior;
    private Swimmable swimmingBehavior;

    public Animal(Flyable flyingBehavior, Swimmable swimmingBehavior) {
        this.flyingBehavior = flyingBehavior;
        this.swimmingBehavior = swimmingBehavior;
    }

    // Behavior can be swapped at runtime
    public void setFlyingBehavior(Flyable fb) {
        this.flyingBehavior = fb;
    }
}
```

Any combination of behaviors is possible with no class explosion. Behaviors can be changed at runtime by swapping the injected object.

---

## When IS Inheritance Appropriate?

Inheritance is not always wrong. Use it when:

1. **Genuine IS-A relationship:** `Dog` truly IS-A `Animal` in the domain. Not just "reuses some of the same code."

2. **Liskov Substitution Principle holds:** every subclass can be used wherever the parent is expected without breaking correctness. If you need to override a method with "throw UnsupportedOperationException," LSP is violated — use composition instead.

3. **Sealed, well-understood hierarchy:** the hierarchy is small, closed to external extension, and unlikely to need new axes of variation. Example: `Exception` → `RuntimeException` → `IllegalArgumentException`.

4. **Framework extension points:** some frameworks (e.g., older JUnit, some Spring components) are designed for extension via subclassing. Follow the framework's intent.

---

## Real-World Examples

### Java I/O Streams — Composition (Decorator Pattern)

```java
// Composition: wrap streams to add behavior
InputStream fileStream = new FileInputStream("data.txt");
InputStream bufferedStream = new BufferedInputStream(fileStream);  // adds buffering
InputStream gzipStream = new GZIPInputStream(bufferedStream);       // adds decompression
DataInputStream dataStream = new DataInputStream(gzipStream);       // adds typed reads
```

Each wrapper adds one behavior. You compose exactly the behaviors you need. The underlying stream can be any InputStream implementation.

### Spring Framework

Spring uses composition extensively. A `BeanFactory` holds references to beans (composition). Spring AOP creates proxy objects that wrap target objects (composition, not subclassing). `JdbcTemplate` holds a `DataSource` (composition).

---

## Related Patterns

- **Decorator:** The composition-over-inheritance pattern made explicit. Wraps an object to add behavior, rather than subclassing it.
- **Strategy:** Encapsulates a varying algorithm as an object. The host class holds a Strategy interface (composition), not a subclass.
- **Bridge:** Decouples abstraction from implementation using composition, preventing the two dimensions from creating a class explosion.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Composition Over Inheritance Appears in Distributed Systems**

- **Microservices over monolith** — Rather than inheriting functionality from a shared monolith base, microservices compose capabilities: a service uses auth, storage, and messaging as independent components. This avoids the brittle coupling of a shared inheritance hierarchy.
- **Sidecar pattern** — Infrastructure capabilities (auth, logging, tracing, mTLS) are composed into a service via sidecars, not inherited from a shared base service. Adding a new capability means deploying a new sidecar — no changes to the service itself.
- **Plugin/middleware composition** — API gateway capabilities are composed as plugins: rate limiting, auth, and transformation are separate, composable units. This is more flexible than inheriting from a monolithic gateway base class.
- **Service mesh capability composition** — Envoy, Istio, and Linkerd compose security, observability, and traffic management as orthogonal capabilities added to any service without the service "inheriting" from a specific framework.

---

## Quick Summary

| Aspect | Summary |
|--------|---------|
| Core idea | Build behavior by combining objects, not by extending class hierarchies |
| Problems with inheritance | Fragile base class, class explosion, compile-time rigidity, single-inheritance limit |
| When to use inheritance | Genuine IS-A, LSP holds, sealed hierarchy |
| Runtime benefit | Behavior can be changed by swapping composed objects |
| Real-world example | Java I/O streams (Decorator), Spring (composition-first framework) |
| Related patterns | Decorator, Strategy, Bridge |
