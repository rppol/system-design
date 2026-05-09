# Liskov Substitution Principle (LSP)

**Part of the SOLID series** | [Back to Overview](README.md)

---

## Definition and Intent

> "If S is a subtype of T, then objects of type T may be replaced with objects of type S without altering any of the desirable properties of the program."
> — Barbara Liskov, 1987 (Turing Award recipient)

In plain terms: **anywhere your code uses a base class or interface, you should be able to plug in a subclass and have the program still work correctly — without the caller needing to know which subclass it is dealing with.**

This is a stronger statement than "it compiles." A subclass can override a method and still compile, but if it changes the behavior in a way that breaks the caller's assumptions, LSP is violated.

**Intent:** Make inheritance and polymorphism safe. OCP says "extend without modifying"; LSP ensures the extensions are actually correct and trustworthy.

---

## Intuition

> **One-line analogy**: LSP is like a promise that all plugs fit the same outlet — if you advertise a certain socket shape (interface), every plug (implementation) must actually work in it, not just fit physically but behave correctly.

**Mental model**: Polymorphism promises: "use any subclass wherever the base class is accepted." LSP ensures this promise isn't broken. If `Square extends Rectangle` and you pass a Square to `setWidth(5)`, should the height also change to 5? If it does, code assuming Rectangle behavior breaks — LSP is violated. Subtypes must honor the full behavioral contract of the base type, not just the method signatures.

**Why it matters**: LSP violations destroy polymorphism's value — if you can't trust substitutability, you end up writing `instanceof` checks everywhere, defeating the purpose of inheritance. It ensures the type hierarchy is semantically coherent, not just syntactically valid.

**Key insight**: "Is-a" in OOP requires behavioral compatibility, not just logical similarity. A Square IS-A rectangle geometrically, but it's NOT-A rectangle behaviorally in most OOP implementations. When in doubt, prefer composition over inheritance to avoid LSP violations.

---

## The Formal Contract

LSP is rooted in Design by Contract (Eiffel language concept). For a subclass to be a valid substitution:

1. **Preconditions cannot be strengthened.** The subclass cannot require more from its callers than the parent does.
2. **Postconditions cannot be weakened.** The subclass must deliver at least what the parent promised — it can deliver more, but not less.
3. **Invariants must be preserved.** Any property the base class guarantees about its state must hold in the subclass.
4. **The history constraint.** Subclass methods must not introduce mutations that the base class did not allow (a read-only parent should not have a mutable subclass that modifies shared state unexpectedly).
5. **Exceptions:** A subclass should not throw new, checked exceptions that the base class does not throw.

---

## The Classic Problem: Rectangle and Square

This is the canonical LSP example, included in virtually every textbook and interview.

### The Naive Design (Violates LSP)

```java
// Seems reasonable: a Square IS-A Rectangle mathematically
public class Rectangle {
    protected int width;
    protected int height;

    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public int calculateArea() {
        return width * height;
    }
}

// A square must keep width == height at all times
public class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width; // Must override to keep invariant
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.width = height; // Must override to keep invariant
    }
}
```

Now look at this perfectly reasonable client code:

```java
// This client was written against Rectangle's contract
public void resizeToDoubleWidth(Rectangle rect) {
    int originalHeight = rect.getHeight();
    rect.setWidth(rect.getWidth() * 2);
    // ASSUMPTION (from Rectangle contract): height is unchanged after setWidth
    assert rect.getHeight() == originalHeight; // FAILS for Square!
}

// Caller
Rectangle r = new Square(); // LSP says this should work
resizeToDoubleWidth(r);      // It does NOT work — assertion fails
```

**Why LSP is violated:**
- `Rectangle` has an implicit postcondition: `setWidth` only changes width, not height
- `Square` violates this postcondition by changing both
- The caller `resizeToDoubleWidth` has a valid assumption about `Rectangle`, but `Square` breaks it
- A `Square` cannot be substituted for a `Rectangle` without breaking the caller

**The lesson:** The mathematical "is-a" relationship (a square is a rectangle) does not imply a valid behavioral subtype relationship. In OOP, "is-a" must be behavioral, not just definitional.

### LSP-Compliant Solution

```java
// Option 1: Use a shared interface without setters — immutable shapes
public interface Shape {
    int calculateArea();
}

public class Rectangle implements Shape {
    private final int width;
    private final int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    @Override
    public int calculateArea() {
        return width * height;
    }
}

public class Square implements Shape {
    private final int side;

    public Square(int side) {
        this.side = side;
    }

    public int getSide() { return side; }

    @Override
    public int calculateArea() {
        return side * side;
    }
}

// Now both can substitute for Shape safely — no shared mutable state contract is broken
public void printArea(Shape shape) {
    System.out.println("Area: " + shape.calculateArea()); // Works for both
}
```

**Option 2: Factory method approach**

```java
public class Rectangle {
    private final int width;
    private final int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // Returns a new rectangle — immutable approach
    public Rectangle withWidth(int newWidth) {
        return new Rectangle(newWidth, this.height);
    }

    public Rectangle withHeight(int newHeight) {
        return new Rectangle(this.width, newHeight);
    }

    public int calculateArea() {
        return width * height;
    }
}
```

---

## Additional LSP Violation Examples

### Violation: Throwing UnsupportedOperationException

```java
// BAD: ReadOnlyList violates List's contract
public class ReadOnlyList<T> extends ArrayList<T> {
    @Override
    public boolean add(T element) {
        throw new UnsupportedOperationException("This list is read-only!");
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException("This list is read-only!");
    }
}

// Caller has no idea this will throw
public void addDefaultItem(List<String> list) {
    list.add("DEFAULT"); // Explodes at runtime if list is ReadOnlyList
}
```

**Fix:** Use composition, not inheritance. Or use a separate `ReadableList` interface that does not include mutation methods.

```java
public interface ReadableList<T> {
    T get(int index);
    int size();
    boolean contains(T element);
}

// No UnsupportedOperationException needed — the interface never promised mutation
public class ImmutableList<T> implements ReadableList<T> {
    private final List<T> internal;

    public ImmutableList(List<T> items) {
        this.internal = List.copyOf(items);
    }

    @Override public T get(int index) { return internal.get(index); }
    @Override public int size() { return internal.size(); }
    @Override public boolean contains(T element) { return internal.contains(element); }
}
```

### Violation: Strengthening Preconditions

```java
// Base class accepts any positive amount
public class BankAccount {
    protected double balance;

    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        balance += amount;
    }
}

// Subclass strengthens the precondition — amounts must also be > 100
public class PremiumBankAccount extends BankAccount {
    @Override
    public void deposit(double amount) {
        // VIOLATION: strengthening precondition (parent only required > 0)
        if (amount <= 100) throw new IllegalArgumentException("Minimum deposit is $100");
        balance += amount;
    }
}

// Code written against BankAccount contract breaks when given PremiumBankAccount
public void depositFiveDollars(BankAccount account) {
    account.deposit(5); // Valid per BankAccount contract, throws for PremiumBankAccount
}
```

### Violation: Weakening Postconditions

```java
// Base class guarantees list is never null and is sorted
public class SortedDataProvider {
    public List<Integer> getData() {
        List<Integer> data = fetchFromDB();
        Collections.sort(data);
        return data; // Always sorted, never null
    }
}

// Subclass returns unsorted data — weakened postcondition
public class FastDataProvider extends SortedDataProvider {
    @Override
    public List<Integer> getData() {
        return fetchFromDB(); // Skips sort for "performance" — breaks caller's assumption
    }
}

// Caller depends on the postcondition
public void processData(SortedDataProvider provider) {
    List<Integer> data = provider.getData();
    // Relies on sorted order for binary search — breaks silently with FastDataProvider
    int idx = Collections.binarySearch(data, targetValue);
}
```

---

## Real-World Analogies

**Employee substitution:** You hire a contractor to fill in for your senior developer. If the contractor cannot attend meetings, cannot access the codebase, or refuses to review PRs (all things the senior developer did), the substitution fails. A valid substitute must honor the entire contract of the role, not just parts of it.

**Electrical devices:** A device that accepts 110V is the base type. Plugging in a device that accepts only 220V violates the substitution — the precondition (input voltage) has been strengthened beyond what the outlet guarantees.

**Bird analogy:** `Bird` has a method `fly()`. `Ostrich` is a `Bird` but cannot fly. Making `Ostrich extend Bird` and throwing `UnsupportedOperationException` from `fly()` is an LSP violation. The `Bird` type hierarchy should separate `FlyingBird` from `Bird`.

---

## How to Identify LSP Violations

- A subclass method throws `UnsupportedOperationException`
- A subclass overrides a method but does less than the parent promised
- Client code has `instanceof` checks before calling a method (implies the type is not safely substitutable)
- A subclass method ignores its arguments in a way the parent would not
- Tests written against the base class fail when run with a subclass

---

## Code Smell: The instanceof Check

When you see this pattern, LSP is likely violated:

```java
// This instanceof check is a red flag — why can't we treat all Animals as Animals?
public void makeSound(Animal animal) {
    if (animal instanceof Dog) {
        ((Dog) animal).bark();
    } else if (animal instanceof Cat) {
        ((Cat) animal).meow();
    }
}

// LSP-compliant version
public void makeSound(Animal animal) {
    animal.makeSound(); // Safe polymorphic dispatch
}
```

---

## Pros and Cons of Strict LSP Adherence

### Pros
- Polymorphism is actually safe — you can substitute freely without surprises
- Fewer runtime errors from unexpected behavior in subclasses
- Client code is simpler — no need for defensive `instanceof` checks
- Inheritance hierarchies are trustworthy and composable

### Cons
- Mathematical "is-a" and behavioral "is-a" often diverge — Square/Rectangle tension is genuinely hard
- Strict invariant preservation sometimes forces awkward designs (e.g., immutable shapes)
- Can push you toward composition over inheritance earlier than feels natural
- The formal precondition/postcondition analysis requires discipline and documentation

---

## Tradeoffs: When Is It OK to Bend the Rule?

- **Framework constraints:** Some frameworks require subclassing for extension, and the framework's own design may have mild LSP violations. Working within those constraints pragmatically is reasonable.
- **Internal code with full control:** If you own all callers, you can sometimes break LSP knowingly and add a note, as long as the violation is contained. But document it.
- **Sealed hierarchies (Java 17+):** When using sealed classes + pattern matching, you know all subtypes at compile time. The caller can handle all cases exhaustively rather than relying on substitution. This is a valid alternative to LSP when the hierarchy is intentionally closed.

---

## Relationship to Other Principles

| Principle | Relationship |
|---|---|
| OCP | OCP relies on safe substitution — LSP ensures the substitution is actually safe |
| SRP | SRP clarifies contracts; single-purpose classes have clearer postconditions to preserve |
| ISP | Narrow interfaces are easier to implement fully — reducing LSP violations via `UnsupportedOperationException` |
| DIP | Code that depends on abstractions (DIP) assumes substitutability — LSP makes that assumption valid |

---

## Cross-Perspective: HLD Connections

**HLD View — Where LSP Appears in Distributed Systems**

- **Multi-cloud storage** — S3, GCS, and Azure Blob Storage are substitutable implementations of a `BlobStorage` interface. Any service using the interface must work correctly with any provider — LSP as a cloud portability guarantee.
- **Database read replicas** — A read replica must be substitutable for the primary for read operations: same query, same results (eventually). If a replica returns stale data for queries that require current data, it violates LSP — callers cannot safely substitute it.
- **Service versioning** — A v2 API endpoint should be substitutable for v1 for all requests that v1 could handle. Breaking LSP at the API level (v2 rejects valid v1 requests, or changes response semantics) breaks backward compatibility and all existing clients.
- **Container images** — A new container image for a service must be substitutable for the old one: same API contract, same behavior for all valid inputs. A deployment that introduces contract-breaking changes violates LSP at the infrastructure level.

---

## Interview Questions and Answers

**Q: What is the Liskov Substitution Principle?**

A: LSP states that a subclass must be substitutable for its base class without breaking the program's correctness. This means the subclass must honor the full behavioral contract of the parent: not strengthening preconditions, not weakening postconditions, and not violating class invariants.

---

**Q: Explain the Rectangle/Square problem.**

A: Mathematically, a square is a rectangle. But in OOP, making `Square` extend `Rectangle` causes issues because `Rectangle` has an implicit contract: setting width does not change height. `Square` must break this to maintain its own invariant (all sides equal). Code written against `Rectangle` that assumes independent width/height mutation will break when given a `Square`. The fix is to use an interface or immutable design that does not involve mutable setters.

---

**Q: How is LSP different from polymorphism?**

A: Polymorphism is the mechanism; LSP is the constraint that makes polymorphism safe and correct. Polymorphism allows you to write `Shape s = new Circle()`. LSP says: if you do that, `Circle` must behave in a way consistent with what `Shape` promised. Without LSP, polymorphism compiles but produces surprising runtime failures.

---

**Q: What does `UnsupportedOperationException` tell you about LSP?**

A: Throwing `UnsupportedOperationException` from an overridden method is almost always an LSP violation. It means a subclass is inheriting a contract it cannot fulfill. The right fix is to redesign the hierarchy — either use a narrower interface that does not include the unsupported operation, or switch to composition.

---

**Q: What is behavioral subtyping?**

A: Behavioral subtyping means "is-a" in the sense of the behavior contract, not just the structural type. A `Square` is structurally a `Rectangle` (same fields and methods), but it is not a behavioral subtype because it cannot fulfill `Rectangle`'s contract. LSP defines when a subtype relationship is behaviorally valid.

---

**Interview Tip:** The Rectangle/Square example is guaranteed to come up. Know it cold: the problem, why it violates LSP (broken postcondition), and two clean solutions (immutable design, shared interface without setters). Then be ready to give a second example from enterprise code — `ReadOnlyList extends ArrayList` throwing `UnsupportedOperationException` is equally well-known and more relatable to Java developers.
