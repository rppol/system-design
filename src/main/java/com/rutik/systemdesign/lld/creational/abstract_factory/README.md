# Abstract Factory Pattern

## 1. Pattern Name & Category

**Name:** Abstract Factory (also called Kit)
**Category:** Creational (GoF)
**GoF Classification:** Gang of Four — Creational Design Pattern
**Book Reference:** "Design Patterns: Elements of Reusable Object-Oriented Software" (Gamma et al., 1994)

---

## 2. Intent

Provide an interface for creating **families of related or dependent objects** without specifying their concrete classes.

---

## Intuition

> **One-line analogy**: Abstract Factory is like IKEA's furniture families — you choose a style (Scandinavian, Modern), and the whole set (table, chair, lamp) is guaranteed to match. You never mix and match incompatible pieces.

**Mental model**: When you need multiple related objects that must work together (Button + TextField + Dialog all need to be Mac-style or Windows-style), creating them individually risks mismatches. Abstract Factory gives you a factory interface where each concrete factory creates a complete, consistent family. Swap the factory, and everything produced by it automatically coheres.

**Why it matters**: Abstract Factory enforces product family consistency at the type system level. It's the pattern behind UI toolkits (swing, JavaFX themes), database drivers (all JDBC objects come from the same DriverManager), and cross-platform abstraction layers.

**Key insight**: The difference from Factory Method: Factory Method creates one product type; Abstract Factory creates a family of related products. If you're only dealing with one product, use Factory Method; use Abstract Factory when product compatibility matters.

---

## 3. Problem Statement

### The Problem
You need to create multiple objects that must be consistent with each other — they belong to the same "family." If you mix objects from different families, the system breaks or produces incorrect results.

### The Scenario
Consider building a cross-platform UI toolkit. Your application renders `Button`, `Checkbox`, and `TextField` components. But:

- On **Windows**, buttons look like Win32 buttons, checkboxes use Windows styling, text fields have Windows borders.
- On **macOS**, buttons use Aqua styling, checkboxes are macOS-styled, text fields have rounded corners.
- On **Linux**, all components use the GTK theme.

The problem: if you accidentally mix a macOS Button with a Windows Checkbox, the UI looks inconsistent or breaks entirely. You need to guarantee that all components come from the same platform family.

A second scenario: a database access layer where `Connection`, `Command`, and `Transaction` objects must all come from the same database driver (MySQL vs. PostgreSQL). Mixing a MySQL Connection with a PostgreSQL Command will fail at runtime.

### What We Need
1. A way to create multiple related objects at once.
2. A guarantee that all created objects belong to the same family.
3. The client code should work with any family without modification.

---

## 4. Solution

The Abstract Factory pattern:
1. Defines an **AbstractFactory interface** with a creation method for each product type.
2. Each **ConcreteFactory** implements the interface and creates a consistent family of products.
3. **AbstractProduct interfaces** define what each product type can do.
4. **ConcreteProducts** implement the AbstractProduct interfaces — one per family.
5. **Client** is initialized with a factory and uses it exclusively — never calls `new`.

Swapping the factory object swaps the entire product family atomically.

---

## 5. UML Structure

```
+---------------------+          +----------------------+
|   <<interface>>     |          |   <<interface>>      |
|   AbstractFactory   |          |   AbstractProductA   |
+---------------------+          +----------------------+
| +createProductA()   |          | +operationA()        |
| +createProductB()   |          +----------------------+
+---------------------+               ^           ^
         ^        ^                   |           |
         |        |            ProductA1      ProductA2
+--------+   +----+-------+
|            |             |     +----------------------+
ConcreteFactory1  ConcreteFactory2|   AbstractProductB   |
|            |             |     +----------------------+
|creates     |creates       |    | +operationB()        |
|ProductA1   |ProductA2     |    +----------------------+
|ProductB1   |ProductB2     |         ^           ^
+------------+--------------+         |           |
                                  ProductB1    ProductB2

ConcreteFactory1 creates the "Family 1" products: ProductA1 + ProductB1
ConcreteFactory2 creates the "Family 2" products: ProductA2 + ProductB2
```

**Key relationship:** Products from the same ConcreteFactory are designed to work together.

---

## 6. How It Works — Step by Step

1. **Define product interfaces** for each type of object the system needs (Button, Checkbox, etc.).
2. **Define ConcreteProducts** for each family × product type combination (WindowsButton, MacButton, etc.).
3. **Define the AbstractFactory** interface with one creation method per product type.
4. **Implement ConcreteFactories** — one per family — each creating consistent products.
5. **Client is initialized** with an AbstractFactory (injected, passed via constructor, or selected from config).
6. **Client calls factory.createButton()** — gets back an AbstractButton. It never knows or cares which concrete type it received.
7. **Client can call factory.createCheckbox()** — guaranteed to be from the same family as the button.
8. To switch families, pass a different ConcreteFactory to the client — zero client code changes.

---

## 7. Key Components

| Component | Role |
|-----------|------|
| `AbstractFactory` | Interface declaring creation methods for each product type |
| `ConcreteFactory` | Implements AbstractFactory; creates a consistent family of products |
| `AbstractProduct` | Interface for one type of product |
| `ConcreteProduct` | A specific product belonging to one family |
| `Client` | Uses factories and products exclusively through abstract interfaces |

---

## 8. When to Use

- **Platform-independent code:** UI toolkits, rendering engines, database drivers where components must be consistent.
- **Product families:** When the system must use objects from one of several families and must enforce that they work together.
- **Configuration-driven family selection:** At startup, read config/environment and select the appropriate factory — all downstream code is agnostic.
- **Multiple themes or variants:** Applications with light/dark mode, premium/free tiers, or locale-specific behavior.
- **Testing with test doubles:** Pass a `MockFactory` or `FakeFactory` to inject test doubles for all products at once.
- **Plugin systems:** Third parties implement `AbstractFactory` to provide their product family; the core system accepts any conforming factory.

---

## 9. When NOT to Use

- **When you only have one product type:** Use Factory Method instead — Abstract Factory is overkill for a single object.
- **When families change frequently:** Adding a new product type to the factory requires changing the AbstractFactory interface AND every ConcreteFactory — this is a costly modification.
- **When products don't need to be consistent:** If mixing types from different families is fine, there's no need to enforce family consistency.
- **Simple applications:** The pattern introduces significant structure. For simple apps, direct instantiation or a simple factory is more appropriate.

---

## 10. Pros

- **Family consistency:** Enforces that products from the same factory work together — eliminates incompatibility bugs.
- **Open/Closed Principle:** New families can be added by creating a new ConcreteFactory without changing client code.
- **Swappable families:** Changing the factory object swaps the entire product family atomically.
- **Loose coupling:** Client is decoupled from all concrete product classes.
- **Single Responsibility:** Each ConcreteFactory knows how to create one family; each ConcreteProduct knows its own behavior.
- **Easy testing:** Pass a test factory to inject fakes/mocks for all products simultaneously.

---

## 11. Cons

- **New product types are expensive:** Adding a new product type (e.g., ScrollBar) requires changing the AbstractFactory interface, all ConcreteFactories, and potentially all clients. This is a breaking change.
- **Class explosion:** For N families and M product types, you need N×M concrete product classes plus N concrete factories.
- **Complexity:** Significantly more structure than direct instantiation. Can be over-engineering for simple scenarios.
- **Rigid product type set:** The set of products a factory creates is fixed in the interface — extending it is not open/closed.
- **Potential over-abstraction:** Sometimes developers reach for Abstract Factory when a simpler pattern (Factory Method, Builder) would suffice.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Family consistency (objects work together) | Easy extensibility of product types |
| Full client decoupling | Simple structure (class count grows as N×M) |
| Atomic family swap | Flexibility to mix products from different families |
| Testability via factory injection | Low overhead — this is a heavy-weight pattern |
| Open/Closed for new families | Open/Closed for new product types (adding a type breaks all factories) |

**The core tradeoff:** Abstract Factory excels at the "new families" dimension but struggles with the "new product types" dimension. The inverse of Factory Method's tradeoff.

---

## 13. Common Pitfalls

1. **Confusing with Factory Method:** Abstract Factory creates *families of objects*. Factory Method creates *one object* and defers the type to subclasses. Abstract Factory often uses Factory Methods internally.

2. **Adding product types breaks the interface:** When you add a new method to AbstractFactory, every ConcreteFactory must implement it. Plan your product type set carefully upfront.

3. **Using it for a single product:** If you only have one product type, use Factory Method. Abstract Factory's value is in managing multiple related product types.

4. **Putting construction logic in the factory:** Factories should do minimal work — create and return. Complex configuration belongs in a builder or the product's own initialization.

5. **Not making products depend on each other correctly:** The whole point of Abstract Factory is that Family-1 products are designed to work together. If your concrete products don't actually interact, you may not need this pattern.

6. **Ignoring the interface segregation for factories:** If some clients only need some products, consider splitting the AbstractFactory into smaller, focused interfaces.

---

## 14. Real-World Usage

| Framework / Library | Usage |
|--------------------|-------|
| **`java.awt` / Swing** | `Toolkit.getDefaultToolkit()` returns platform-specific UI components — an Abstract Factory. |
| **JDBC** | `java.sql.Connection`, `Statement`, `ResultSet` are a product family; each JDBC driver is a ConcreteFactory. |
| **`javax.xml.parsers`** | `DocumentBuilderFactory` and `SAXParserFactory` — configure once, create consistent parsers. |
| **Spring Framework** | `ApplicationContext` can be seen as an Abstract Factory for beans; different implementations create beans differently. |
| **`javax.xml.transform`** | `TransformerFactory` creates `Transformer` and `Templates` objects (a consistent family). |
| **Android** | `Context.getSystemService()` returns different service objects based on the device — effectively a factory for platform-specific objects. |
| **Mockito** | `MockitoSession` creates consistent mocks, stubs, and spies together. |
| **Go's `testing.T`** | Test fixtures using `testify/suite` create a consistent test environment. |

---

## 15. Comparison with Similar Patterns

| Pattern | How It Differs from Abstract Factory |
|---------|--------------------------------------|
| **Factory Method** | Creates one object type; uses inheritance. Abstract Factory creates multiple related object types; uses composition. |
| **Builder** | Constructs one complex object step-by-step. Abstract Factory creates multiple different objects in one call per type. |
| **Prototype** | Clones existing objects; doesn't use a factory class hierarchy. |
| **Singleton** | Ensures one instance. Often used to implement the Abstract Factory (factories are often singletons). |
| **Service Locator** | Looks up existing services; Abstract Factory creates new product instances. |

---

## 16. Interview Tips

**Common Interview Questions:**

1. **"When would you choose Abstract Factory over Factory Method?"**
   Answer: When you have multiple related product types that must be created together and must be consistent. Factory Method is for one product type; Abstract Factory is for a whole family. Give the platform UI example.

2. **"What's the biggest limitation of Abstract Factory?"**
   Answer: Adding a new product type to the factory requires changing the abstract interface and all concrete factories — a potentially breaking change. This is why you should plan the product type set carefully.

3. **"How is JDBC related to Abstract Factory?"**
   Answer: Each JDBC driver acts as a concrete factory. `Connection`, `Statement`, and `ResultSet` are product families. `DriverManager.getConnection()` returns a driver-specific Connection, and all subsequent objects created from it are from the same driver family.

4. **"How do you swap families at runtime?"**
   Answer: Since clients depend only on AbstractFactory and AbstractProduct, you simply pass in a different ConcreteFactory. All product creation calls through that factory will return the new family's objects.

**Key Phrases:**
- "Family of related objects"
- "Consistency guarantee across product types"
- "Composition over inheritance (vs. Factory Method)"
- "Swap entire product family by swapping the factory"

---

## Cross-Perspective: HLD Connections

**HLD View — Where Abstract Factory Appears in Distributed Systems**

- **Multi-cloud infrastructure** — A `CloudResourceFactory` interface produces families of related resources (`createVM()`, `createStorage()`, `createLoadBalancer()`). `AWSFactory`, `GCPFactory`, `AzureFactory` implement it. Switching clouds means swapping the factory, not the entire codebase.
- **Database driver families** — A `DatabaseFactory` produces a consistent family: `createConnection()`, `createQueryBuilder()`, `createMigrationRunner()`. MySQL and PostgreSQL families implement it, letting the application run on either without per-component conditionals.
- **Notification channel families** — `EmailNotificationFactory` and `SMSNotificationFactory` each produce a `Sender`, `Formatter`, and `Tracker` that are guaranteed to work together. Mixing components across families causes mismatches.
- **Test vs production infrastructure** — A `MockInfraFactory` produces in-memory fakes for all external dependencies in tests; `ProductionInfraFactory` produces real clients. The application code never changes between environments.

---

## 17. Best Practices

1. **Define product interfaces carefully upfront** — the set of product types in AbstractFactory is hard to change later. Spend time on this.

2. **Use composition:** Pass the factory as a constructor argument or inject it — don't use a static/singleton factory if you want testability.

3. **Keep factory methods minimal** — a factory method should return a new (or appropriately shared) product, nothing else.

4. **One ConcreteFactory per family** — resist the temptation to put conditional logic inside a factory. Create separate ConcreteFactories.

5. **Document family constraints** — make it clear in comments/javadoc which products are expected to work together.

6. **Consider making factories singletons** — since factories are stateless in most implementations, a single instance per family is sufficient. Use a DI container for this.

7. **Pair with Abstract Factory in tests** — provide a `FakeFactory` or `MockFactory` that returns in-memory, fast implementations of all products.

8. **Consider the Abstract Factory + Builder combination** — when individual products are complex to construct, have the Abstract Factory return a Builder for each product type.
