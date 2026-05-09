package com.rutik.systemdesign.lld.structural.facade;

/**
 * FACADE PATTERN — Pure Structural Template
 *
 * Intent: Provide a unified, simplified interface to a set of interfaces
 * in a subsystem, making the subsystem easier to use.
 *
 * Participants:
 *   - Facade          : knows the subsystem, delegates to it
 *   - SubsystemA/B/C  : the complex classes doing real work
 *   - Client          : only talks to Facade
 */

// ─────────────────────────────────────────────
// SUBSYSTEM CLASSES
// These classes represent the complex, low-level
// components that form the subsystem.
// They have no knowledge of the Facade.
// ─────────────────────────────────────────────

class SubsystemA {
    /**
     * Complex operation A1. Clients should not call this directly
     * unless they need fine-grained control.
     */
    public void operationA1() {
        System.out.println("SubsystemA: operationA1");
    }

    public void operationA2() {
        System.out.println("SubsystemA: operationA2");
    }
}

class SubsystemB {
    public void operationB1() {
        System.out.println("SubsystemB: operationB1");
    }

    public void operationB2() {
        System.out.println("SubsystemB: operationB2");
    }
}

class SubsystemC {
    public void operationC1() {
        System.out.println("SubsystemC: operationC1");
    }
}

// ─────────────────────────────────────────────
// FACADE INTERFACE
// Defining an interface allows the Facade to
// be mocked in tests and swapped with different
// implementations (e.g., a legacy vs new subsystem).
// ─────────────────────────────────────────────

interface Facade {
    /**
     * A simplified, high-level operation that orchestrates
     * a complex sequence of subsystem calls.
     */
    void operationOne();

    /**
     * Another high-level operation representing a different
     * usage scenario of the subsystem.
     */
    void operationTwo();
}

// ─────────────────────────────────────────────
// CONCRETE FACADE
// The facade knows the subsystem internals and
// delegates appropriately. It does NOT implement
// the subsystem logic itself — it orchestrates.
// ─────────────────────────────────────────────

class ConcreteFacade implements Facade {

    // Facade holds references to all subsystem components.
    // These can be injected via constructor (preferred for testability)
    // or created internally.
    private final SubsystemA subsystemA;
    private final SubsystemB subsystemB;
    private final SubsystemC subsystemC;

    /**
     * Constructor injection: subsystem components are provided externally.
     * This makes the Facade testable — subsystems can be mocked.
     */
    public ConcreteFacade(SubsystemA a, SubsystemB b, SubsystemC c) {
        this.subsystemA = a;
        this.subsystemB = b;
        this.subsystemC = c;
    }

    /**
     * Default constructor: Facade creates its own subsystem instances.
     * Simpler for clients, but harder to test.
     */
    public ConcreteFacade() {
        this(new SubsystemA(), new SubsystemB(), new SubsystemC());
    }

    /**
     * Orchestrates a sequence of subsystem operations.
     * The client calls this single method instead of knowing
     * the ordering and interplay of A1 → B1 → C1.
     */
    @Override
    public void operationOne() {
        System.out.println("Facade: executing operationOne (orchestrates A1 → B1 → C1)");
        subsystemA.operationA1();
        subsystemB.operationB1();
        subsystemC.operationC1();
    }

    /**
     * A different high-level use case that involves a different
     * combination of subsystem operations.
     */
    @Override
    public void operationTwo() {
        System.out.println("Facade: executing operationTwo (orchestrates A2 → B2)");
        subsystemA.operationA2();
        subsystemB.operationB2();
    }
}

// ─────────────────────────────────────────────
// CLIENT
// The client only knows the Facade interface.
// It is completely decoupled from SubsystemA/B/C.
// ─────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {

        // Client creates (or receives) the Facade.
        // Notice: client has zero imports of Subsystem classes.
        Facade facade = new ConcreteFacade();

        System.out.println("--- Client calls operationOne ---");
        facade.operationOne();

        System.out.println();

        System.out.println("--- Client calls operationTwo ---");
        facade.operationTwo();

        // The client never knows that SubsystemA, SubsystemB, SubsystemC exist.
        // It interacts only with the Facade interface.
    }
}

/*
 * KEY STRUCTURAL NOTES:
 *
 * 1. The Facade INTERFACE (not just class) allows mocking in tests:
 *      Facade mockFacade = mock(Facade.class);
 *
 * 2. Constructor injection enables testability:
 *      new ConcreteFacade(mockA, mockB, mockC) — subsystems are mockable.
 *
 * 3. Subsystem classes are unmodified. No back-references to Facade.
 *    This means subsystems can be upgraded independently.
 *
 * 4. The Facade does NOT contain business logic.
 *    It only orchestrates the sequence of subsystem calls.
 *
 * 5. Advanced clients can still use SubsystemA/B/C directly
 *    if they need fine-grained control — the Facade does not
 *    prohibit this.
 *
 * EXTENSION POINTS:
 * - Add a second concrete facade (e.g., SimplifiedFacade vs ExtendedFacade)
 *   for different client sophistication levels.
 * - Layer facades: a high-level Facade can use a mid-level Facade internally.
 */
