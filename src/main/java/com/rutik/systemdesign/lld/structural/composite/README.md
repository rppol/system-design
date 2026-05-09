# Composite Pattern

## 1. Pattern Name & Category

**Pattern:** Composite
**Category:** Structural
**GoF Classification:** Structural Design Pattern (Gang of Four, "Design Patterns: Elements of Reusable Object-Oriented Software", 1994)

---

## 2. Intent

Compose objects into tree structures to represent part-whole hierarchies. Composite lets clients treat individual objects (leaves) and compositions of objects (composites) uniformly through a common interface.

---

## Intuition

> **One-line analogy**: Composite is like a file system — folders contain files and other folders, and you can "open" anything (whether it's a file or a folder full of files) with the same interface.

**Mental model**: When you have a tree structure where leaves and containers should be treated identically, Composite is the answer. File system: `FileSystemItem.open()` works on both `File` (leaf) and `Directory` (composite containing more FileSystemItems). UI widgets: a `Panel` contains `Buttons` and other `Panels` — both respond to `render()`. You traverse and operate on the whole tree without knowing whether any node is a leaf or container.

**Why it matters**: Composite eliminates conditional logic that checks "is this a leaf or a container?" The uniform interface lets you write recursive tree algorithms once (not separately for leaves and containers). It's the pattern behind XML/HTML DOM, organization hierarchies, arithmetic expression trees, and GUI component trees.

**Key insight**: The power of Composite lies in client code simplicity — algorithms (calculate total price, render UI, count files) are written once and work on the entire tree. The complexity of managing the tree is contained within the Composite nodes, not scattered across client code.

---

## 3. Problem Statement

### The Core Problem
You need to work with a tree-structured hierarchy where some nodes are simple objects (leaves) and others are containers that hold more objects. Client code must handle both types, leading to repetitive `instanceof` checks and branching logic that becomes increasingly complex as the hierarchy grows.

### Scenario: File System Tree
You are building a file system browser. The structure is:
- **Files** — leaf nodes with a name and size.
- **Directories** — containers that hold files and other directories.

Operations you need:
- `getSize()` — for a file, return its size; for a directory, sum the sizes of all contents recursively.
- `display(indent)` — print the tree with proper indentation.
- `delete()` — delete a file, or delete a directory and all its contents.
- `search(name)` — find a file or directory by name.

Without Composite, client code looks like:
```java
if (item instanceof File) {
    size += ((File) item).getSize();
} else if (item instanceof Directory) {
    for (FileSystemItem child : ((Directory) item).getChildren()) {
        // recursive logic duplicated everywhere
    }
}
```

This `instanceof` proliferation:
- Violates Open/Closed Principle — every new node type requires updates everywhere.
- Duplicates recursive traversal logic across multiple methods.
- Makes it impossible to treat trees and leaves uniformly.
- Breaks when you add new composites (e.g., `Archive`, `SymLink`).

---

## 4. Solution

Define a **Component interface** that both leaves and composites implement. The interface declares all operations that make sense for both (e.g., `getSize()`, `display()`, `getName()`).

- **Leaf** implements the interface directly.
- **Composite** implements the interface by delegating to its children and aggregating/combining the results.

Now client code works entirely through the Component interface. A `Directory` and a `File` are both `FileSystemItem`s. When you call `item.getSize()`, you don't care what `item` is — the polymorphism handles it.

---

## 5. UML Structure

```
         +----------------------+
         |   <<interface>>      |
         |     Component        |
         |  (FileSystemItem)    |
         +----------------------+
         | +operation()         |
         | +add(Component)      |  <-- optional; can be in Composite only
         | +remove(Component)   |
         | +getChild(int)       |
         +----------------------+
                  ^
        __________|___________
       |                      |
+-------------+       +------------------+
|    Leaf      |       |    Composite     |
|    (File)    |       |  (Directory)     |
+-------------+       +------------------+
| +operation()|       | -children: List  |
+-------------+       | +operation()     |  <-- iterates children, delegates
                       | +add(Component)  |
                       | +remove(Component)|
                       | +getChild(int)   |
                       +------------------+
                                |
                       has 0..* Component children
```

**Two design choices for the Component interface:**

1. **Transparency:** Declare `add/remove/getChild` in Component — clients treat everything uniformly but leaves must provide dummy implementations.
2. **Safety:** Only declare `add/remove/getChild` in Composite — no dummy implementations but clients must downcast to call tree operations.

GoF prefers transparency; most modern implementations prefer safety.

---

## 6. How It Works

**Step-by-step mechanics:**

1. **Client calls `operation()`** on a `Component` reference (could be Leaf or Composite).
2. **If it's a Leaf:** The operation is handled directly (e.g., `File.getSize()` returns the file's own size).
3. **If it's a Composite:** The operation iterates over all children and calls `operation()` on each, then combines the results (e.g., `Directory.getSize()` sums sizes from all children).
4. **Recursion happens naturally** — a child `Composite` (subdirectory) will in turn iterate its own children, and so on down the tree.
5. **Base cases are Leaves** — no children to iterate; recursion terminates.
6. **Tree building:** Client calls `composite.add(leaf)` and `composite.add(anotherComposite)` to build the tree.
7. **Client is oblivious** — once the tree is built, the client calls `root.operation()` and the tree handles traversal internally.

---

## 7. Key Components

| Component | Role | Description |
|-----------|------|-------------|
| **Component** | Common interface | Declares interface for all objects in the composition (both leaves and composites) |
| **Leaf** | Individual node | Represents a leaf node with no children; implements Component directly |
| **Composite** | Container node | Stores child Components; implements Component by delegating to children |
| **Client** | Tree consumer | Works only through the Component interface; unaware of leaf vs. composite distinction |

---

## 8. When to Use

- **Tree-structured data** — whenever your domain has a natural part-whole hierarchy (file systems, org charts, menus, DOM trees, bill of materials).
- **Uniform treatment** — when clients should be able to treat individual objects and compositions the same way.
- **Recursive operations** — when operations naturally propagate down a tree (calculate total price, render a UI tree, print an org chart).
- **Dynamic hierarchies** — when the tree structure is not known at compile time and can change at runtime.
- **Adding new leaf/composite types** — when you expect the type of nodes to grow without changing client traversal logic.

### Concrete Examples
- File system (files and directories)
- GUI component trees (panels containing buttons containing icons)
- XML/HTML DOM traversal
- Organization hierarchy (employees and managers)
- Menu systems (menu items and submenus)
- Expression trees in compilers/calculators
- Bill of materials in manufacturing

---

## 9. When NOT to Use

- **Flat structures** — if your data has no hierarchy, Composite adds unnecessary complexity.
- **Type-specific operations matter** — if clients genuinely need to behave very differently for leaves vs. composites, forcing a common interface is awkward.
- **Performance-critical aggregation** — recursive traversal can be slow on very deep or very wide trees; consider caching or a different data structure.
- **When hierarchy is fixed and known** — if the tree never changes and has only 2-3 known types, a simpler design may be clearer.
- **When add/remove/getChild on leaves is unacceptable** — if the "transparency" design requires leaves to throw `UnsupportedOperationException`, this can lead to surprises; use "safety" design or a different pattern.

---

## 10. Pros

- **Uniformity** — clients treat leaves and composites identically through the Component interface.
- **Open/Closed Principle** — add new Component types (new leaf or composite) without changing existing client code.
- **Simplifies client code** — eliminates `instanceof` checks and recursive traversal boilerplate in client code.
- **Natural recursive structure** — mirrors the recursive nature of tree data naturally.
- **Flexible tree building** — composites and leaves can be assembled in any combination at runtime.
- **Easy traversal** — tree-walking algorithms are encapsulated inside the composite, not scattered across clients.
- **Scalability** — works with arbitrarily deep and wide trees without code changes.

---

## 11. Cons

- **Over-generalization** — if leaves and composites need very different interfaces, forcing them under one Component is awkward.
- **Type safety loss** — with the transparency design, leaves must implement `add/remove` and throw runtime exceptions.
- **Hard to restrict structure** — it's difficult to enforce constraints like "a directory can only have files, not other directories" because everything is a Component.
- **Performance** — deep recursive traversal can be slow; no built-in caching.
- **Circular reference risk** — adding a composite as a child of itself (directly or transitively) causes infinite recursion.
- **Design complexity** — finding the right balance between transparency and safety requires careful interface design.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Uniform client code (no instanceof) | Type safety — leaves must stub out child management methods |
| Easy addition of new node types | Harder to add constraints on which nodes can be children of which |
| Natural recursive algorithm support | Potential runtime exceptions from "unsupported" operations on leaves |
| Clean tree construction API | Circular reference bugs if tree is not built carefully |
| Scalability across arbitrary tree depths | Performance on very deep trees without caching |

---

## 13. Common Pitfalls

1. **Circular trees:** Adding a composite as its own descendant causes infinite recursion. Always validate when adding children.
2. **Transparency vs. safety confusion:** Implementing `add/remove` on Leaf and having it throw `UnsupportedOperationException` surprises callers. Document this clearly or use the safety variant.
3. **Caching not considered:** `getSize()` on the root of a huge tree recalculates from scratch on every call. Cache aggregated values when the tree is static.
4. **Forgetting the parent reference:** Some traversals (like `delete`) need to know a node's parent; forgetting to maintain a `parent` reference makes these operations impossible.
5. **Thread safety:** Modifying a composite's child list while another thread is traversing it causes `ConcurrentModificationException`. Use synchronization or immutable trees.
6. **Overloading the Component interface:** Adding too many operations to Component forces every Leaf to implement irrelevant methods.
7. **Not protecting against null children:** `composite.add(null)` leads to `NullPointerException` deep in recursive traversal; validate inputs.

---

## 14. Real-World Usage

### Java Standard Library
- **`java.awt.Container`** extends `java.awt.Component`. Every AWT/Swing GUI element is a `Component`; panels and frames are `Container`s (composites) that hold other `Component`s. The entire Swing component tree is the Composite pattern.
- **`javax.faces.component.UIComponent`** in JSF — the entire JSF component tree is a Composite.

### XML/JSON Processing
- **DOM API (`org.w3c.dom`):** `Node` is the Component interface; `Element`, `Attr`, `Text` are leaves; `Document` and `Element` act as composites with `getChildNodes()`.

### Build Tools
- **Apache Ant / Maven:** Build tasks can be composed — a "build" task contains "compile", "test", and "package" tasks; each task can itself be composite.

### Spring Framework
- **`CompositePropertySource`:** Aggregates multiple `PropertySource`s behind a single interface.
- **`CompositeInterceptor`:** Chains multiple interceptors treated as one.

### Expression Trees
- **Java Compiler (javac):** The AST (Abstract Syntax Tree) is a Composite — binary operations are composites with two children; literals are leaves.
- **Spring Expression Language (SpEL):** Expressions are composites of sub-expressions.

### Android
- **View hierarchy:** `View` is the Component; `ViewGroup` is the Composite; `TextView`, `Button` are Leaves.

---

## 15. Comparison with Similar Patterns

| Pattern | Intent | Key Difference |
|---------|--------|----------------|
| **Composite** | Represent part-whole hierarchies uniformly | Specifically for tree structures; operations propagate down the tree |
| **Decorator** | Add responsibilities to objects | Wraps a single object (chain, not tree); doesn't represent hierarchy |
| **Iterator** | Traverse a collection | Traversal mechanism only; doesn't define the structure |
| **Visitor** | Separate algorithm from structure | Used to add operations to a Composite tree without modifying it |
| **Chain of Responsibility** | Pass a request along a chain | Linear chain, not a tree; focus on who handles the request |

**Composite + Visitor** is a very common combination: Composite defines the tree structure; Visitor adds operations to traverse and process the tree without modifying the node classes.

**Composite vs. Decorator:** Both use recursive composition, but Composite builds trees (children can be many) while Decorator wraps a single object to add behavior.

---

## 16. Interview Tips

### Common Questions

**Q: What problem does Composite solve?**
A: Composite eliminates the need for clients to distinguish between leaf and composite nodes in a tree structure. Without it, client code is full of `instanceof` checks and duplicated recursive traversal logic. Composite makes individual objects and compositions of objects interchangeable through a common interface.

**Q: What's the difference between transparency and safety in Composite?**
A: In the transparency design, `add/remove/getChild` are declared on the Component interface — clients can treat everything uniformly but Leaf must implement these methods (usually throwing `UnsupportedOperationException`). In the safety design, these methods are only on Composite — no dummy implementations, but clients must downcast to use tree operations. Transparency is more uniform; safety is more type-safe.

**Q: How does Composite work with the Visitor pattern?**
A: Composite defines the tree structure. Visitor separates operations on that tree from the node classes. You add a new operation by writing a new Visitor rather than modifying every node class. The Composite tree calls `visitor.visit(this)` on each node; the Visitor implements different logic for each node type.

**Q: Give a real-world example of the Composite pattern.**
A: The Swing GUI framework. `java.awt.Component` is the Component interface. `java.awt.Container` is the Composite that holds child Components. `JButton`, `JLabel`, `JTextField` are Leaves. `JPanel`, `JFrame` are Containers. You can call `getPreferredSize()` on a `JPanel` and it recursively asks all children for their sizes — without knowing whether it's talking to a leaf or another container.

**Q: What are the risks of circular references in Composite?**
A: If a composite is accidentally added as a child of itself (directly or indirectly), any traversal becomes infinite recursion. Prevention strategies include: maintaining parent references and checking for cycles on `add()`, or making composites immutable after construction.

### What Interviewers Look For
- Clear understanding of the part-whole hierarchy problem
- Transparency vs. safety tradeoff
- Real example (Swing component tree, file system, DOM)
- Awareness of Composite + Visitor combination
- Circular reference awareness

---

## Cross-Perspective: HLD Connections

**HLD View — Where Composite Appears in Distributed Systems**

- **Infrastructure hierarchy** — Cloud resources form natural composites: a VPC contains subnets, which contain security groups, which contain EC2 instances. Infrastructure-as-Code tools (Terraform, CDK) model this as a Composite tree where `apply()` on the root recursively provisions all children.
- **Object storage hierarchy** — S3-style storage: Bucket → Folder → Object. Operations like `getSize()` or `listRecursive()` on any node naturally recurse into children — Composite enables uniform treatment of buckets and objects.
- **Distributed config trees** — Hierarchical configuration (Kubernetes ConfigMaps, Consul key-value trees) is a Composite: leaf nodes hold values; composite nodes aggregate them. A request to a parent path recursively returns all descendant values.
- **Health check aggregation** — A composite health check aggregates `UP`/`DOWN` from multiple sub-checks (database, cache, external API, disk space). `isHealthy()` on the root returns `true` only if all children are healthy — uniform recursion at any level of nesting.

---

## 17. Best Practices

1. **Decide on transparency vs. safety early** — it affects your entire Component interface design.
2. **Validate `add()` inputs** — check for null and for cycles before adding a child.
3. **Maintain parent references if needed** — if operations like `delete` need to modify the parent, add a `parent` field to Component.
4. **Cache aggregated results** — if `getSize()` or similar aggregation is expensive, cache the result in the composite and invalidate on structural changes.
5. **Use the safety variant in public APIs** — throwing `UnsupportedOperationException` from Leaf is surprising; expose `add/remove` only on Composite in public APIs.
6. **Combine with Visitor for new operations** — instead of adding methods to Component/Leaf/Composite, use Visitor to add new tree traversal algorithms.
7. **Use Iterator for traversal** — provide an `Iterator<Component>` to allow clients to traverse children without depending on the Composite's internal list type.
8. **Protect the child list** — return an unmodifiable view from `getChildren()` to prevent external mutation.
9. **Consider using the Builder pattern** — building complex composite trees via a fluent Builder is more readable than many `add()` calls.
10. **Thread safety** — if trees are shared across threads, make modifications synchronized or use a copy-on-write strategy.
