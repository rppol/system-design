package com.rutik.systemdesign.lld.behavioral.template_method;

/**
 * TEMPLATE METHOD PATTERN - Template / Skeleton
 *
 * Intent:
 *   Define the skeleton of an algorithm in a base class, deferring some
 *   steps to subclasses. Subclasses can redefine certain steps without
 *   changing the overall algorithm structure.
 *
 * When to use:
 *   - You want to let subclasses implement varying behavior while keeping
 *     the invariant parts of an algorithm in one place.
 *   - You notice identical code in multiple classes and want to pull the
 *     common logic up into a shared parent.
 *
 * Structure:
 *   - AbstractClass  : Defines the templateMethod() that calls primitive
 *                      operations. Some operations have default implementations
 *                      (hooks); others are abstract (mandatory overrides).
 *   - ConcreteClass  : Implements the abstract primitive operations.
 */

// ---------------------------------------------------------------------------
// 1. AbstractClass
//    Contains the template method and declares primitive operations.
// ---------------------------------------------------------------------------
abstract class AbstractClass {

    /**
     * The template method: defines the skeleton of the algorithm.
     * Declared final so subclasses cannot reorder the steps.
     */
    public final void templateMethod() {
        step1();           // always the same
        step2();           // subclass-specific (abstract)
        step3();           // subclass-specific (abstract)
        hook();            // optional override (hook method)
        step4();           // always the same
    }

    // Invariant step — common to all subclasses
    private void step1() {
        System.out.println("AbstractClass.step1(): common initialization");
    }

    // Primitive operation — subclass MUST implement
    protected abstract void step2();

    // Primitive operation — subclass MUST implement
    protected abstract void step3();

    /**
     * Hook: subclasses MAY override this, but don't have to.
     * Default implementation does nothing.
     */
    protected void hook() {
        // default: no-op
    }

    // Invariant step — common to all subclasses
    private void step4() {
        System.out.println("AbstractClass.step4(): common cleanup");
    }
}

// ---------------------------------------------------------------------------
// 2. ConcreteClass A
// ---------------------------------------------------------------------------
class ConcreteClassA extends AbstractClass {

    @Override
    protected void step2() {
        System.out.println("ConcreteClassA.step2(): variant A implementation");
    }

    @Override
    protected void step3() {
        System.out.println("ConcreteClassA.step3(): variant A implementation");
    }

    // Does NOT override hook — uses the default no-op
}

// ---------------------------------------------------------------------------
// 3. ConcreteClass B  — also overrides the hook
// ---------------------------------------------------------------------------
class ConcreteClassB extends AbstractClass {

    @Override
    protected void step2() {
        System.out.println("ConcreteClassB.step2(): variant B implementation");
    }

    @Override
    protected void step3() {
        System.out.println("ConcreteClassB.step3(): variant B implementation");
    }

    @Override
    protected void hook() {
        System.out.println("ConcreteClassB.hook(): optional extra behaviour activated");
    }
}

// ---------------------------------------------------------------------------
// 4. Client / Demo
// ---------------------------------------------------------------------------
public class Template {

    public static void main(String[] args) {

        System.out.println("=== ConcreteClassA ===");
        AbstractClass a = new ConcreteClassA();
        a.templateMethod();

        System.out.println();

        System.out.println("=== ConcreteClassB ===");
        AbstractClass b = new ConcreteClassB();
        b.templateMethod();
    }
}
