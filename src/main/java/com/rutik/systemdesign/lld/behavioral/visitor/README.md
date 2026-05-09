# Visitor Pattern

## 1. Pattern Name & Category

**Pattern Name:** Visitor
**Category:** Behavioral
**GoF Classification:** Behavioral Design Pattern (Gang of Four, "Design Patterns: Elements of Reusable Object-Oriented Software", 1994)

---

## 2. Intent

Represent an operation to be performed on elements of an object structure, letting you define a new operation without changing the classes of the elements on which it operates.

---

## Intuition

> **One-line analogy**: Visitor is like a tax inspector visiting different types of businesses — the inspector (visitor) adapts their inspection process based on the business type (element), without the businesses needing to change for each inspector.

**Mental model**: You have a stable object structure (AST nodes, document elements, company departments) and want to add new operations over it without modifying the element classes. Visitor separates the operation (Visitor class) from the data (Element class). Each Visitor implements `visit(ConcreteElementA)`, `visit(ConcreteElementB)` etc. Elements implement `accept(Visitor)` which calls `visitor.visit(this)`. Adding a new operation = new Visitor class; element classes unchanged. This is "double dispatch."

**Why it matters**: Compilers use Visitor for AST traversals (type checking, code generation, optimization passes — each is a Visitor). Document export (export to PDF, HTML, Markdown — each is a Visitor over document elements). The pattern lets you add new operations to a class hierarchy without the Open/Closed Principle violation of modifying every class.

**Key insight**: Visitor is the "add operations without modifying classes" pattern, but it has the inverse weakness: adding new element types requires modifying every Visitor. Use it when element types are stable but operations change frequently; use other patterns when element types change frequently.

---

## 3. Problem Statement

### The Problem
You have a stable object structure (a class hierarchy — e.g., AST nodes, file system items, UI components) and you need to perform many distinct, unrelated operations on these objects. Adding each new operation as a method to every class pollutes the class hierarchy with unrelated concerns and forces you to modify all element classes every time a new operation is needed.

### Scenario 1: Compiler Abstract Syntax Tree (AST)
A compiler has AST node types: `NumberLiteral`, `BinaryOp`, `Variable`, `IfStatement`, `FunctionCall`. You need to perform multiple operations on this tree:
- Evaluate the expression (interpreter)
- Generate code (code generator)
- Pretty-print the AST (formatter)
- Perform type checking
- Compute cyclomatic complexity

Without Visitor, you'd add `evaluate()`, `generateCode()`, `prettyPrint()`, `typeCheck()`, and `computeComplexity()` to every AST node class. Every new analysis pass requires modifying all node classes. AST node classes become bloated with unrelated operations.

### Scenario 2: E-commerce order system
You have order items: `PhysicalProduct`, `DigitalProduct`, `Subscription`. You need operations: compute shipping cost, apply tax, generate invoice line, compute discount. Without Visitor, each new operation requires modifying all three product classes.

---

## 4. Solution

Separate the operations from the object structure by creating a **Visitor** interface with one `visit()` method for each element type in the hierarchy. Each concrete visitor implements all these methods, encapsulating one complete operation.

Each element in the hierarchy implements an `accept(Visitor v)` method that simply calls `v.visit(this)` — this is called **double dispatch**. The first dispatch is selecting the `accept()` method based on the element's runtime type; the second dispatch is selecting the `visit()` overload based on the visitor's type.

New operations are added by creating new Visitor implementations — no modification to the element classes is required.

---

## 5. UML Structure

```
    <<interface>>
       Visitor
+────────────────────────────+
| + visitConcreteElementA(a) |
| + visitConcreteElementB(b) |
+────────────────────────────+
          /\
          |  implements
   _______|_______
   |              |
ConcreteVisitor1  ConcreteVisitor2
+ visitConcreteElementA(a)  { ... }
+ visitConcreteElementB(b)  { ... }


    <<interface>>
       Element
+───────────────────────────+
| + accept(v: Visitor): void |
+───────────────────────────+
          /\
          |  implements
   _______|_______
   |              |
ConcreteElementA  ConcreteElementB
+ accept(v) { v.visitConcreteElementA(this); }
+ accept(v) { v.visitConcreteElementB(this); }
+ operationA()               + operationB()


ObjectStructure
+─────────────────────────────────────+
| - elements: List<Element>           |
| + accept(v: Visitor): void          |  <-- iterates elements, calls accept(v)
+─────────────────────────────────────+

Client ──uses──> ConcreteVisitor
Client ──uses──> ObjectStructure
```

**Double Dispatch Flow:**
```
Client
  |
  |--> objectStructure.accept(visitor)
         |
         |--> elementA.accept(visitor)       [1st dispatch: element type]
                |
                |--> visitor.visitA(this)    [2nd dispatch: visitor type]
                        |
                        |--> (visitor's logic for element A executes)
```

---

## 6. How It Works — Step-by-Step

1. **Define the Visitor interface** with one `visit(ConcreteElementX)` method for each element type. This is the key — the interface must enumerate all element types upfront.

2. **Define the Element interface** with `accept(Visitor v)`. Each concrete element implements `accept()` by calling `v.visit(this)`.

3. **Implement Concrete Elements:** Each element class implements `accept()` as `visitor.visitThisType(this)`. The element's own operations (unrelated to visitors) stay in the element class.

4. **Implement Concrete Visitors:** Each visitor class implements all `visit()` methods, one for each element type. This is where the operation logic lives.

5. **Client creates a visitor instance** (e.g., `new TaxCalculatorVisitor()`).

6. **Client traverses the object structure**, calling `element.accept(visitor)` on each element.

7. **Double dispatch occurs:** `accept()` on the element ensures the correct `visit()` overload is called on the visitor, based on the element's concrete type — even if the element is referenced via an interface.

8. **The visitor accumulates results** or performs side effects across all elements.

---

## 7. Key Components

| Component | Role |
|---|---|
| **Visitor (interface)** | Declares `visit()` methods for each concrete element type |
| **ConcreteVisitor** | Implements one complete operation across all element types |
| **Element (interface)** | Declares `accept(Visitor)` — the entry point for double dispatch |
| **ConcreteElement** | Implements `accept()` as `visitor.visitThisType(this)`; contains element data |
| **ObjectStructure** | Holds a collection of elements; provides a way to iterate and accept visitors |
| **Client** | Creates visitors and triggers traversal of the object structure |

---

## 8. When to Use

- **Stable class hierarchy, many operations:** When you have a fixed set of element types but frequently add new operations. Adding a new visitor is easy; adding a new element type is hard (requires updating all visitors).
- **Unrelated operations on an object structure:** When you want to avoid polluting element classes with operations that don't belong to their core responsibility.
- **Operations need to accumulate state:** When a visitor needs to collect data across multiple elements (e.g., total price, complexity score), it's natural to store that state in the visitor.
- **AST traversals in compilers:** Type checking, code generation, optimization passes — each is a visitor over the AST.
- **Document object models:** Rendering, serialization, validation of document trees.
- **Composite pattern + Visitor:** Visitor pairs naturally with Composite to traverse tree structures.
- **Reporting and analytics:** Computing metrics over a complex object graph without modifying the objects.

---

## 9. When NOT to Use

- **Frequently changing element hierarchy:** If you add/remove element types often, you must update every visitor for each change. This is the pattern's primary weakness.
- **Small, simple hierarchies:** If you have 2 element types and 1 operation, Visitor adds unnecessary complexity. Direct method calls or polymorphism suffice.
- **When elements need to be encapsulated:** Visitor requires elements to expose their internal state to the visitor (often through getters). This breaks encapsulation.
- **When operations belong to the elements:** If an operation is intrinsic to the element's identity (e.g., `draw()` on a shape), it should be a method on the element, not a visitor.
- **Languages with multi-methods or pattern matching:** In Scala, Haskell, or modern Java with pattern matching (`switch` on sealed types), Visitor is often unnecessary boilerplate.

---

## 10. Pros

- **Open/Closed Principle:** Add new operations (visitors) without modifying element classes. New functionality is isolated in new visitor classes.
- **Single Responsibility Principle:** Operations are separated from the objects they operate on. Each visitor class has one clear purpose.
- **Accumulate state across elements:** A visitor naturally accumulates results while traversing a structure (e.g., total sum, collected list).
- **Related behavior is co-located:** All logic for a single operation (e.g., tax calculation) lives in one visitor class, making it easy to find and modify.
- **Double dispatch:** Achieves type-based dispatch without `instanceof` checks. The type system selects the right `visit()` overload automatically.
- **Works well with Composite:** Naturally traverses tree structures when combined with the Composite pattern.
- **Clean separation of concerns:** Element classes remain focused on their data; visitor classes contain the operational logic.

---

## 11. Cons

- **Adding new element types is expensive:** Every new element type requires updating all existing Visitor interfaces and their implementations. This can break the Open/Closed principle from the element hierarchy's perspective.
- **Breaks encapsulation:** For visitors to operate on elements, elements must expose their internal state through public getters. This increases coupling.
- **Complexity and indirection:** The double-dispatch mechanism is non-obvious to developers unfamiliar with the pattern. The control flow is harder to trace.
- **Visitor interface becomes a bottleneck:** The `Visitor` interface is a central, frequently-changed artifact whenever element types are added or removed.
- **Circular dependency risk:** Visitors depend on concrete element types; elements depend on the Visitor interface. Tight coupling between the two hierarchies.
- **Can be overkill:** For simple use cases, a straightforward `instanceof` check or a `switch` statement is cleaner than the full Visitor infrastructure.
- **Verbose in Java/C#:** Each visitor must implement `visit()` for every element type, even if it only cares about a few. Default/no-op implementations help but add boilerplate.

---

## 12. Tradeoffs

| What You Gain | What You Lose |
|---|---|
| Easy to add new operations | Hard to add new element types |
| Operations separated from data | Element encapsulation (must expose state) |
| State accumulation in visitor | Clarity of control flow (double dispatch) |
| Single-responsibility visitors | Verbose visitor interface & implementations |
| No `instanceof` in operation code | Tight coupling between visitor and element hierarchies |

**The fundamental tension:** Visitor optimizes for adding new operations. It pessimizes for adding new element types. Before using it, ask: "What changes more often — the operations or the element types?"

---

## 13. Common Pitfalls

1. **Using Visitor when the element hierarchy changes often:** If you add element types frequently, you'll be updating every visitor constantly. The pattern becomes a maintenance burden instead of a benefit.

2. **Forgetting to call `accept()` recursively in composite elements:** If an element contains child elements (like AST nodes), its `accept()` must recursively call `accept(visitor)` on children — otherwise the visitor only sees the root.

3. **State leakage between traversals:** If a visitor accumulates state (e.g., a running total), you must reset or create a new visitor instance for each traversal. Reusing a stateful visitor without resetting leads to incorrect results.

4. **Visitor accessing private state:** The pattern works best when elements provide rich public getters. If the visitor needs to call package-private or internal methods, you have a design problem — the operation may belong on the element itself.

5. **Not making the Visitor interface comprehensive:** If you add a new element type but forget to add its `visit()` method to the interface, existing code compiles but the new element is silently ignored. Always update the interface AND all implementations.

6. **Using Visitor for single operations:** If you only have one operation, just add a method to the element interface. Visitor pays off when there are multiple, unrelated operations.

---

## 14. Real-World Usage

### Compilers and Language Tools
- **ANTLR (ANother Tool for Language Recognition):** Generates Visitor and Listener interfaces for traversing parse trees. Every grammar rule becomes an element; analysis passes are visitors.
- **Eclipse JDT (Java Development Tools):** `ASTVisitor` class visits Java AST nodes for refactoring tools, code analysis, and the Eclipse compiler.
- **javac (Java Compiler):** Internally uses tree visitors (`com.sun.source.tree.TreeVisitor`) for various compilation phases.
- **Checkstyle / PMD / SpotBugs:** Static analysis tools are visitors over the Java AST.

### Java Standard Library
- **`java.nio.file.FileVisitor`:** The `Files.walkFileTree()` API uses Visitor to traverse directory trees. `SimpleFileVisitor` provides default no-op implementations.
- **`javax.lang.model.element.ElementVisitor`:** Annotation processing API uses visitors to process Java program elements.

### DOM/XML Processing
- **DOM traversal:** Visitor pattern is commonly used with the DOM API to process XML/HTML trees without modifying the node classes.
- **XPath evaluation engines** implement Visitor to traverse the document tree.

### Spring Framework
- **`BeanDefinitionVisitor`:** Visits and processes Spring bean definitions during context initialization.

### UI Frameworks
- **Scene graph rendering:** 3D engines like JavaFX or SceneKit use visitors to traverse the scene graph for rendering, hit-testing, and serialization.

---

## 15. Comparison with Similar Patterns

### Visitor vs Iterator
| Aspect | Visitor | Iterator |
|---|---|---|
| Purpose | Perform an operation across elements | Traverse elements sequentially |
| Type awareness | Type-specific logic per element type | Treats all elements uniformly |
| Element modification | No — operates on existing structure | No — traversal only |
| Best for | Multiple operations on heterogeneous types | Sequential access to homogeneous collection |

### Visitor vs Strategy
- Strategy replaces an algorithm within one object. Visitor applies an algorithm across many objects in a structure.
- Strategy is single-object; Visitor is multi-object traversal.

### Visitor vs Command
- Command encapsulates a single action as an object. Visitor encapsulates an action that applies to an entire object structure.
- Commands are executed independently; a Visitor is applied across all elements.

### Visitor vs Composite
- These patterns are complementary. Composite defines the tree structure; Visitor traverses it.
- Composite's `accept()` calls `accept()` on children — the Composite pattern is the traversal mechanism; Visitor is the operation applied at each node.

---

## 16. Interview Tips

**Q: What problem does Visitor solve?**
A: Explain the "operations vs types" problem: when you have a fixed class hierarchy and need many unrelated operations, Visitor lets you add operations without modifying the element classes. The classic example is a compiler AST with multiple analysis passes.

**Q: What is double dispatch and why does Visitor need it?**
A: Java uses single dispatch — method selection based on the runtime type of the receiver object only. But Visitor needs behavior based on BOTH the element type AND the visitor type. Double dispatch achieves this: `accept()` on the element selects based on element type (1st dispatch), and `visitor.visit(this)` selects the overload based on the visitor type (2nd dispatch).

**Q: What are the main limitations of Visitor?**
A: Adding new element types requires updating all visitors. It also requires elements to expose their internal state, breaking encapsulation. Best used when the element hierarchy is stable.

**Q: Where is Visitor used in the Java ecosystem?**
A: `java.nio.file.FileVisitor`, ANTLR's generated visitor interfaces, Eclipse JDT's `ASTVisitor`, javac's tree visitor API.

**Q: How would you implement Visitor without the pattern?**
A: You'd use `instanceof` chains or add operation methods to each element class. Both approaches lead to scattered code and force modification of existing classes for new operations.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Visitor Appears in Distributed Systems**

- **Static analysis in CI/CD** — Linters, security scanners (SonarQube, Semgrep), and code coverage tools traverse ASTs using Visitor. Each analysis pass (null check, SQL injection detection, complexity scoring) is a separate Visitor that traverses the same tree.
- **Query optimizer** — Database query optimizers traverse the query plan tree with multiple Visitor passes: selectivity estimation, join reordering, index selection, and cost estimation are separate visitors on the same plan representation.
- **Serialization frameworks** — Jackson, Protobuf serializers, and Avro use Visitor-like dispatch: the serializer visits each node of an object graph, dispatching to type-specific serialization logic without the object knowing how it's being serialized.
- **Infrastructure cost calculator** — Terraform plan output is a resource graph. A cost estimation visitor traverses it, dispatching to type-specific pricing logic (`visitEC2Instance`, `visitRDSInstance`) to produce a total cost estimate.

---

## 17. Best Practices

1. **Use when the element hierarchy is stable:** Commit to Visitor only when you're confident the set of element types won't change frequently.

2. **Provide a default visitor with no-op implementations:** Create an `AbstractVisitor` or default adapter class that implements all `visit()` methods as no-ops. Concrete visitors only override the types they care about.

3. **Handle recursive structures in `accept()`:** In composite elements, `accept()` should call `accept(visitor)` on all children before or after calling `visitor.visit(this)`, depending on whether you want pre-order or post-order traversal.

4. **Use visitor for accumulation with clear reset semantics:** If a visitor maintains state (running total, collected errors), document clearly that it should not be reused across traversals without resetting. Consider making visitors immutable or providing a factory.

5. **Consider sealed interfaces + pattern matching in modern Java:** Java 21+ sealed interfaces with `switch` pattern matching can replace Visitor in many cases with less boilerplate and better compiler-enforced exhaustiveness.

6. **Keep visitors focused:** Each visitor should implement exactly one concern. If a visitor does two things (e.g., type check AND pretty print), split it into two visitors.

7. **Separate visitor interface from traversal:** The visitor handles per-element logic; the object structure or a separate traverser handles the traversal order. Don't mix these concerns.

8. **Name visitors after the operation:** `TaxCalculatorVisitor`, `HtmlRenderVisitor`, `TypeCheckVisitor` — the name should describe the operation, not the elements it visits.
