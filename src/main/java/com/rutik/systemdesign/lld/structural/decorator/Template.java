package com.rutik.systemdesign.lld.structural.decorator;

/**
 * DECORATOR PATTERN — Pure Structural Template
 *
 * Intent: Attach additional responsibilities to an object dynamically.
 * Decorators provide a flexible alternative to subclassing for extending
 * functionality.
 *
 * Core insight: A Decorator wraps a Component, implements the same interface,
 * and delegates to the wrapped Component — then adds its own behavior before
 * or after. Decorators can be layered (stacked) in any combination.
 *
 * Analogy: Java's InputStream → BufferedInputStream → GZIPInputStream.
 * Each wrapper adds a capability without modifying what's beneath it.
 *
 * Participants:
 *   - Component          : the interface for objects that can be decorated
 *   - ConcreteComponent  : the core object being decorated
 *   - Decorator          : abstract base; holds a Component reference, implements Component
 *   - ConcreteDecorator  : adds specific behavior; calls super (the wrapped component)
 */

// ─────────────────────────────────────────────
// COMPONENT INTERFACE
// Defines the interface for both ConcreteComponent
// and all Decorators. The client uses this interface.
// ─────────────────────────────────────────────

interface Component {
    /**
     * The primary operation that decorators will extend.
     */
    String operation();
}

// ─────────────────────────────────────────────
// CONCRETE COMPONENT
// The core object. Defines the base behavior.
// Decorators wrap this (or other decorators wrapping this).
// ─────────────────────────────────────────────

class ConcreteComponent implements Component {

    @Override
    public String operation() {
        return "ConcreteComponent";
    }
}

// ─────────────────────────────────────────────
// BASE DECORATOR (abstract)
// Implements Component and holds a reference to one.
// This reference is the "wrappee" — the object being decorated.
//
// Having an abstract Decorator class is optional (you could go straight
// to ConcreteDecorator), but it:
//   - centralizes the delegation call (wrappee.operation())
//   - provides a clear extension point for concrete decorators
// ─────────────────────────────────────────────

abstract class Decorator implements Component {

    // The wrapped component. Can be a ConcreteComponent or another Decorator.
    protected final Component wrappee;

    protected Decorator(Component wrappee) {
        this.wrappee = wrappee;
    }

    /**
     * By default, just delegate to the wrapped component.
     * Concrete decorators override this to add behavior.
     */
    @Override
    public String operation() {
        return wrappee.operation();
    }
}

// ─────────────────────────────────────────────
// CONCRETE DECORATOR A
// Adds behavior BEFORE the wrapped component's operation.
// ─────────────────────────────────────────────

class ConcreteDecoratorA extends Decorator {

    public ConcreteDecoratorA(Component wrappee) {
        super(wrappee);
    }

    /**
     * Adds a prefix, then delegates to the wrapped component.
     * Pattern: modify input → call super → return
     */
    @Override
    public String operation() {
        // Pre-processing, then delegate
        return "DecoratorA( " + super.operation() + " )";
    }
}

// ─────────────────────────────────────────────
// CONCRETE DECORATOR B
// Adds behavior AFTER the wrapped component's operation.
// Demonstrates that decorators are independent and stackable.
// ─────────────────────────────────────────────

class ConcreteDecoratorB extends Decorator {

    public ConcreteDecoratorB(Component wrappee) {
        super(wrappee);
    }

    /**
     * Delegates to the wrapped component, then adds a suffix.
     * Pattern: call super → modify output → return
     */
    @Override
    public String operation() {
        // Delegate first, then post-process
        return "DecoratorB[ " + super.operation() + " ]";
    }
}

// ─────────────────────────────────────────────
// CLIENT
// Composes decorators at runtime.
// The stacking order determines the nesting order.
// ─────────────────────────────────────────────

public class Template {

    /** Client code works with any Component — decorated or not. */
    static void clientCode(Component component) {
        System.out.println("Result: " + component.operation());
    }

    public static void main(String[] args) {

        System.out.println("--- Plain ConcreteComponent (no decorators) ---");
        Component plain = new ConcreteComponent();
        clientCode(plain);

        System.out.println();

        System.out.println("--- Wrapped with DecoratorA only ---");
        Component withA = new ConcreteDecoratorA(new ConcreteComponent());
        clientCode(withA);

        System.out.println();

        System.out.println("--- Wrapped with DecoratorB only ---");
        Component withB = new ConcreteDecoratorB(new ConcreteComponent());
        clientCode(withB);

        System.out.println();

        System.out.println("--- A wrapping B wrapping ConcreteComponent ---");
        // Innermost → outermost: ConcreteComponent → B → A
        // Reading the output: A( B[ ConcreteComponent ] )
        Component withAB = new ConcreteDecoratorA(
                               new ConcreteDecoratorB(
                                   new ConcreteComponent()));
        clientCode(withAB);

        System.out.println();

        System.out.println("--- B wrapping A wrapping ConcreteComponent ---");
        // Same decorators, reversed stacking order — different result
        Component withBA = new ConcreteDecoratorB(
                               new ConcreteDecoratorA(
                                   new ConcreteComponent()));
        clientCode(withBA);
    }
}

/*
 * KEY STRUCTURAL NOTES:
 *
 * 1. The Decorator holds a Component reference — not a ConcreteComponent.
 *    This means you can stack decorators on top of decorators arbitrarily.
 *
 * 2. super.operation() in a ConcreteDecorator delegates to the wrapped object.
 *    If that wrapped object is another Decorator, the call propagates down
 *    the chain until it reaches the ConcreteComponent at the bottom.
 *
 * 3. Stacking order matters: A(B(core)) ≠ B(A(core)).
 *    Pre-processing decorators apply outer-to-inner; post-processing inner-to-outer.
 *
 * 4. At runtime, you can build any combination without new subclasses.
 *    2 decorators → 4 combinations; N decorators → N! orderings.
 *    Subclassing would require a class per combination.
 *
 * WHEN TO USE:
 * - Adding responsibilities to individual objects without affecting others.
 * - When extension by subclassing is impractical due to combinatorial explosion.
 * - When responsibilities should be added and removed at runtime.
 *
 * REAL-WORLD EXAMPLES:
 * - java.io: FileInputStream → BufferedInputStream → DataInputStream
 * - Servlet filters (each filter wraps the next)
 * - Spring AOP: method interceptors as decorator chains
 */
