package com.rutik.systemdesign.lld.structural.bridge;

/**
 * BRIDGE PATTERN — Pure Structural Template
 *
 * Intent: Decouple an abstraction from its implementation so that the two
 * can vary independently. Instead of a fixed binding at compile time,
 * the implementation is injected at runtime.
 *
 * Core insight: Replace inheritance with composition.
 *   Without Bridge: Shape (abstract) → RedCircle, BlueCircle, RedSquare, BlueSquare
 *                   → 2 shapes × 2 colors = 4 subclasses (combinatorial explosion)
 *   With Bridge:    Shape (abstract) ←→ Color (interface)
 *                   → 2 shapes + 2 colors = 4 classes, unlimited combinations
 *
 * Participants:
 *   - Abstraction         : defines the abstraction's interface; holds an Implementor
 *   - RefinedAbstraction  : extends Abstraction; adds higher-level operations
 *   - Implementor         : interface for the implementation side
 *   - ConcreteImplementor : a specific implementation of the Implementor interface
 */

// ─────────────────────────────────────────────
// IMPLEMENTOR INTERFACE
// Defines the low-level primitive operations that
// concrete implementations provide.
// The Abstraction depends on THIS interface — not
// on any ConcreteImplementor directly.
// ─────────────────────────────────────────────

interface Implementor {
    /**
     * A primitive operation used by Abstraction.
     * Implementations decide HOW to execute it.
     */
    void operationImpl();

    /**
     * Another primitive operation.
     */
    void anotherOperationImpl();
}

// ─────────────────────────────────────────────
// CONCRETE IMPLEMENTORS
// Two independent implementations of Implementor.
// The Abstraction can switch between them at runtime.
// ─────────────────────────────────────────────

class ConcreteImplementorA implements Implementor {

    @Override
    public void operationImpl() {
        System.out.println("ConcreteImplementorA: operationImpl — Platform A behavior");
    }

    @Override
    public void anotherOperationImpl() {
        System.out.println("ConcreteImplementorA: anotherOperationImpl — Platform A behavior");
    }
}

class ConcreteImplementorB implements Implementor {

    @Override
    public void operationImpl() {
        System.out.println("ConcreteImplementorB: operationImpl — Platform B behavior");
    }

    @Override
    public void anotherOperationImpl() {
        System.out.println("ConcreteImplementorB: anotherOperationImpl — Platform B behavior");
    }
}

// ─────────────────────────────────────────────
// ABSTRACTION
// Defines the higher-level interface.
// Maintains a reference (the "bridge") to an Implementor.
// Does NOT know which concrete implementation it holds.
// Higher-level logic lives here; low-level primitives
// are delegated to the Implementor.
// ─────────────────────────────────────────────

abstract class Abstraction {

    // The bridge: a reference to the implementation side.
    // This is what separates abstraction from implementation.
    protected final Implementor implementor;

    /**
     * The Implementor is injected — binding is deferred to runtime.
     * This is what enables independent variability on both dimensions.
     */
    protected Abstraction(Implementor implementor) {
        this.implementor = implementor;
    }

    /**
     * A high-level operation defined by the abstraction.
     * It uses the Implementor's primitives to carry out its logic.
     */
    public abstract void operation();

    /**
     * Another high-level operation.
     */
    public abstract void anotherOperation();

    /**
     * Optional: allow swapping the implementation at runtime.
     */
    // public void setImplementor(Implementor implementor) { ... }
}

// ─────────────────────────────────────────────
// REFINED ABSTRACTION
// Extends Abstraction with additional behavior.
// Still delegates low-level work to the Implementor.
// A second RefinedAbstraction can exist independently
// and can be paired with either ConcreteImplementor.
// ─────────────────────────────────────────────

class RefinedAbstraction extends Abstraction {

    public RefinedAbstraction(Implementor implementor) {
        super(implementor);
    }

    /**
     * High-level implementation: coordinates primitives from Implementor.
     * The abstraction adds its own logic before/after delegating.
     */
    @Override
    public void operation() {
        System.out.println("RefinedAbstraction: operation() — coordinating with implementor");
        implementor.operationImpl();
    }

    @Override
    public void anotherOperation() {
        System.out.println("RefinedAbstraction: anotherOperation()");
        implementor.anotherOperationImpl();
    }

    /**
     * An operation specific to this refined abstraction.
     * Demonstrates that refined abstractions can add capabilities.
     */
    public void extendedOperation() {
        System.out.println("RefinedAbstraction: extendedOperation() — uses both primitives");
        implementor.operationImpl();
        implementor.anotherOperationImpl();
    }
}

// ─────────────────────────────────────────────
// CLIENT
// ─────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {

        System.out.println("--- RefinedAbstraction with ConcreteImplementorA ---");
        Abstraction abstractionA = new RefinedAbstraction(new ConcreteImplementorA());
        abstractionA.operation();
        abstractionA.anotherOperation();

        System.out.println();

        System.out.println("--- RefinedAbstraction with ConcreteImplementorB ---");
        // Same abstraction class, different implementation — no subclassing needed
        Abstraction abstractionB = new RefinedAbstraction(new ConcreteImplementorB());
        abstractionB.operation();
        abstractionB.anotherOperation();

        System.out.println();

        System.out.println("--- Extended operation (specific to RefinedAbstraction) ---");
        RefinedAbstraction refined = new RefinedAbstraction(new ConcreteImplementorA());
        refined.extendedOperation();
    }
}

/*
 * KEY STRUCTURAL NOTES:
 *
 * 1. The "bridge" is the implementor reference inside Abstraction.
 *    It is the composition relationship that replaces inheritance.
 *
 * 2. Abstraction and Implementor hierarchies grow independently:
 *    - Add a new RefinedAbstraction → no change to any Implementor.
 *    - Add a new ConcreteImplementor → no change to any Abstraction.
 *
 * 3. Constructor injection of Implementor enables runtime binding and
 *    makes Abstraction testable (inject a mock Implementor).
 *
 * 4. Abstraction contains high-level logic; Implementor contains
 *    platform-specific / low-level primitives. Never mix the two.
 *
 * WHEN TO USE:
 * - You want to avoid a permanent binding between abstraction and implementation.
 * - Both should be extensible via subclassing.
 * - Changes in the implementation should not affect the client.
 * - You have a proliferating subclass hierarchy (N abstractions × M implementations).
 *
 * EXTENSION POINTS:
 * - Add a second RefinedAbstraction (e.g., SpecialAbstraction) — pairs freely
 *   with any ConcreteImplementor without new subclasses.
 * - Add a ConcreteImplementorC — all existing Abstractions gain it for free.
 */
