package com.rutik.systemdesign.lld.behavioral.visitor; /**
 * VISITOR PATTERN - Template / Skeleton
 *
 * Intent:
 *   Represent an operation to be performed on elements of an object structure.
 *   Visitor lets you define a new operation without changing the classes of
 *   the elements on which it operates.
 *
 * When to use:
 *   - You need to perform many distinct, unrelated operations on an object
 *     structure and you don't want to pollute their classes with those operations.
 *   - The object structure rarely changes but you often add new operations.
 *   - Operations need to work across classes that don't share a common interface
 *     beyond the accept() method.
 *
 * Structure:
 *   - Visitor         : Declares a visit() overload for each ConcreteElement type.
 *   - ConcreteVisitor : Implements each visit() — one algorithm variant per element.
 *   - Element         : Declares accept(Visitor) — the "double dispatch" hook.
 *   - ConcreteElement : Implements accept() by calling visitor.visit(this).
 *   - ObjectStructure : (Optional) A collection that iterates elements and
 *                        calls accept() on each.
 *
 * Key insight — Double Dispatch:
 *   Calling element.accept(visitor) selects the right accept() implementation
 *   based on element's runtime type; inside accept(), visitor.visit(this)
 *   selects the right visit() overload based on 'this' type.
 *   Together these two virtual calls dispatch to exactly the right
 *   (element-type, visitor-type) combination.
 */

import java.util.ArrayList;
import java.util.List;

// ---------------------------------------------------------------------------
// 1. Element interface
// ---------------------------------------------------------------------------
interface Element {
    /** Accept a visitor — implementors call visitor.visit(this). */
    void accept(Visitor visitor);
}

// ---------------------------------------------------------------------------
// 2. Visitor interface
//    One visit() overload per ConcreteElement type.
// ---------------------------------------------------------------------------
interface Visitor {
    void visit(ConcreteElementA element);
    void visit(ConcreteElementB element);
}

// ---------------------------------------------------------------------------
// 3. ConcreteElement A
// ---------------------------------------------------------------------------
class ConcreteElementA implements Element {

    private final String dataA = "ElementA-data";

    public String getDataA() { return dataA; }

    @Override
    public void accept(Visitor visitor) {
        // Double dispatch: visitor now knows the concrete type is A
        visitor.visit(this);
    }
}

// ---------------------------------------------------------------------------
// 4. ConcreteElement B
// ---------------------------------------------------------------------------
class ConcreteElementB implements Element {

    private final int dataB = 42;

    public int getDataB() { return dataB; }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}

// ---------------------------------------------------------------------------
// 5. ConcreteVisitor 1 — one operation over the whole structure
// ---------------------------------------------------------------------------
class ConcreteVisitor1 implements Visitor {

    @Override
    public void visit(ConcreteElementA element) {
        System.out.println("ConcreteVisitor1 processing ElementA: "
                + element.getDataA().toUpperCase());
    }

    @Override
    public void visit(ConcreteElementB element) {
        System.out.println("ConcreteVisitor1 processing ElementB: "
                + element.getDataB() * 2);
    }
}

// ---------------------------------------------------------------------------
// 6. ConcreteVisitor 2 — a different operation over the same structure
// ---------------------------------------------------------------------------
class ConcreteVisitor2 implements Visitor {

    @Override
    public void visit(ConcreteElementA element) {
        System.out.println("ConcreteVisitor2 processing ElementA: length="
                + element.getDataA().length());
    }

    @Override
    public void visit(ConcreteElementB element) {
        System.out.println("ConcreteVisitor2 processing ElementB: is-even="
                + (element.getDataB() % 2 == 0));
    }
}

// ---------------------------------------------------------------------------
// 7. ObjectStructure — holds the elements and lets visitors traverse them
// ---------------------------------------------------------------------------
class ObjectStructure {

    private final List<Element> elements = new ArrayList<>();

    public void add(Element element) { elements.add(element); }

    /** Iterate all elements and let the visitor process each one. */
    public void accept(Visitor visitor) {
        for (Element element : elements) {
            element.accept(visitor);
        }
    }
}

// ---------------------------------------------------------------------------
// 8. Client / Demo
// ---------------------------------------------------------------------------
public class Template {

    public static void main(String[] args) {

        ObjectStructure structure = new ObjectStructure();
        structure.add(new ConcreteElementA());
        structure.add(new ConcreteElementB());
        structure.add(new ConcreteElementA());

        System.out.println("=== Applying ConcreteVisitor1 ===");
        structure.accept(new ConcreteVisitor1());

        System.out.println();

        System.out.println("=== Applying ConcreteVisitor2 ===");
        structure.accept(new ConcreteVisitor2());
    }
}
