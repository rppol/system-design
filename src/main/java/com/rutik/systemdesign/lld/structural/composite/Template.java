package com.rutik.systemdesign.lld.structural.composite; /**
 * COMPOSITE PATTERN — Pure Structural Template
 *
 * Intent: Compose objects into tree structures to represent part-whole hierarchies.
 * Composite lets clients treat individual objects (Leaf) and compositions of
 * objects (Composite) uniformly through a common Component interface.
 *
 * Core insight: A Composite holds a list of Components.
 *   Since a Component can be either a Leaf or another Composite,
 *   you get an arbitrarily deep, recursive tree — and all nodes
 *   are operated on with the same interface.
 *
 * Participants:
 *   - Component   : common interface for both Leaf and Composite
 *   - Leaf        : a node with no children; defines behavior for primitives
 *   - Composite   : a node with children; stores Components, delegates to them
 *   - Client      : manipulates objects through the Component interface only
 */

import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────────
// COMPONENT INTERFACE
// Declares operations common to both simple and
// complex objects in the tree.
// Also optionally declares child-management
// methods (add/remove/getChildren).
//
// Two schools of thought on child management:
//   - "Transparency" design: declare add/remove/getChildren here
//     so clients can treat Leaf and Composite uniformly — but then
//     Leaf must handle these as no-ops or throw exceptions.
//   - "Safety" design: only declare them on Composite.
//     Clients need instanceof checks if they want to manage children.
//
// This template uses the Transparency approach (most common in GoF).
// ─────────────────────────────────────────────

interface Component {
    /**
     * The core operation that both Leaf and Composite perform.
     * Composites delegate this recursively to their children.
     *
     * @param depth Current tree depth — used for indented printing.
     */
    void operation(int depth);

    /**
     * Add a child component. Meaningful only for Composites.
     * Leaf nodes can throw UnsupportedOperationException.
     */
    default void add(Component component) {
        throw new UnsupportedOperationException("Leaf nodes do not support add()");
    }

    /**
     * Remove a child component. Meaningful only for Composites.
     */
    default void remove(Component component) {
        throw new UnsupportedOperationException("Leaf nodes do not support remove()");
    }

    /**
     * Returns true if this component is a Composite (has children).
     * Useful for safe narrowing without instanceof.
     */
    default boolean isComposite() {
        return false;
    }
}

// ─────────────────────────────────────────────
// LEAF
// A terminal node — it has no children.
// Defines the actual primitive behavior.
// ─────────────────────────────────────────────

class Leaf implements Component {

    private final String name;

    public Leaf(String name) {
        this.name = name;
    }

    /**
     * Leaf performs the operation directly — no delegation.
     */
    @Override
    public void operation(int depth) {
        System.out.println("  ".repeat(depth) + "Leaf: " + name);
    }

    // isComposite() returns false (default) — correct for Leaf
}

// ─────────────────────────────────────────────
// COMPOSITE
// A branch node — holds a list of child Components.
// Each child may itself be a Leaf or another Composite.
// Delegates operation() recursively to all children.
// ─────────────────────────────────────────────

class Composite implements Component {

    private final String name;

    // Children: each can be a Leaf or another Composite.
    // This List<Component> is what creates the tree structure.
    private final List<Component> children = new ArrayList<>();

    public Composite(String name) {
        this.name = name;
    }

    /** Add a child (Leaf or Composite) to this node. */
    @Override
    public void add(Component component) {
        children.add(component);
    }

    /** Remove a child from this node. */
    @Override
    public void remove(Component component) {
        children.remove(component);
    }

    /**
     * Perform the operation on this node, then recursively on all children.
     * This is the heart of the Composite pattern — tree traversal is implicit.
     */
    @Override
    public void operation(int depth) {
        System.out.println("  ".repeat(depth) + "Composite: " + name + " (" + children.size() + " children)");
        for (Component child : children) {
            child.operation(depth + 1);   // recursive delegation
        }
    }

    @Override
    public boolean isComposite() {
        return true;
    }

    /** Utility: return a snapshot of children (read-only view). */
    public List<Component> getChildren() {
        return List.copyOf(children);
    }
}

// ─────────────────────────────────────────────
// CLIENT
// Works only with Component references.
// Does not distinguish between Leaf and Composite.
// ─────────────────────────────────────────────

public class Template {

    /**
     * Client code: calls operation() on any Component.
     * Works for a single Leaf and for an entire tree.
     */
    static void clientCode(Component component) {
        component.operation(0);
    }

    public static void main(String[] args) {

        // Build a tree:
        //   root
        //   ├── branchA
        //   │   ├── leafA1
        //   │   └── leafA2
        //   ├── branchB
        //   │   ├── branchB1
        //   │   │   └── leafB1_1
        //   │   └── leafB2
        //   └── leafC

        Composite root = new Composite("root");

        Composite branchA = new Composite("branchA");
        branchA.add(new Leaf("leafA1"));
        branchA.add(new Leaf("leafA2"));

        Composite branchB = new Composite("branchB");
        Composite branchB1 = new Composite("branchB1");
        branchB1.add(new Leaf("leafB1_1"));
        branchB.add(branchB1);
        branchB.add(new Leaf("leafB2"));

        root.add(branchA);
        root.add(branchB);
        root.add(new Leaf("leafC"));

        System.out.println("--- Traversing the entire tree from root ---");
        clientCode(root);

        System.out.println();

        System.out.println("--- Client operates on a Leaf directly (same interface) ---");
        clientCode(new Leaf("standalone-leaf"));

        System.out.println();

        System.out.println("--- Client operates on a subtree (same interface) ---");
        clientCode(branchA);

        System.out.println();

        System.out.println("--- isComposite() check ---");
        Component leaf = new Leaf("x");
        Component composite = new Composite("y");
        System.out.println("leaf.isComposite() = " + leaf.isComposite());
        System.out.println("composite.isComposite() = " + composite.isComposite());
    }
}

/*
 * KEY STRUCTURAL NOTES:
 *
 * 1. Uniform interface: clientCode(Component) works identically whether
 *    you pass a Leaf or a deep Composite tree.
 *
 * 2. Recursive delegation: Composite.operation() calls child.operation()
 *    for each child. Since children can be Composites, this recursively
 *    traverses the whole tree without any special-case code.
 *
 * 3. Child management on Component (Transparency): Leaf throws
 *    UnsupportedOperationException on add/remove. This keeps client code
 *    clean — no instanceof checks needed to call operation().
 *
 * 4. isComposite() provides a safe, non-casting way to detect Composites
 *    when you genuinely need to manage children (e.g., tree editors).
 *
 * WHEN TO USE:
 * - You want clients to treat primitive and container objects uniformly.
 * - You need to represent part-whole hierarchies (trees).
 *
 * EXTENSION POINTS:
 * - Add a parent reference in Component for upward traversal.
 * - Add getChild(int index) on Composite for indexed access.
 * - Add an iterator or visitor to traverse the tree non-recursively.
 */
