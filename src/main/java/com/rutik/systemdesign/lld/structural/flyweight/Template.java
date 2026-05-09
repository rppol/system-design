package com.rutik.systemdesign.lld.structural.flyweight; /**
 * FLYWEIGHT PATTERN — Pure Structural Template
 *
 * Intent: Use sharing to efficiently support a large number of fine-grained
 * objects by separating intrinsic (shared, immutable) state from
 * extrinsic (context-specific, passed-in) state.
 *
 * Participants:
 *   - Flyweight           : interface declaring operation(extrinsicState)
 *   - ConcreteFlyweight   : stores intrinsic state; must be immutable
 *   - UnsharedFlyweight   : not all flyweights must be shared (optional)
 *   - FlyweightFactory    : manages the pool; ensures sharing
 *   - Client              : maintains extrinsic state; uses factory
 */

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// ─────────────────────────────────────────────
// FLYWEIGHT INTERFACE
// Declares the operation that accepts extrinsic
// state as a parameter. Clients interact through
// this interface — they cannot tell if they have
// a shared or unshared flyweight.
// ─────────────────────────────────────────────

interface Flyweight {
    /**
     * Performs the operation using both:
     *   - intrinsic state: stored inside this flyweight object (shared)
     *   - extrinsicState: passed in by the caller (context-specific)
     *
     * @param extrinsicState Context-specific data that varies per use
     */
    void operation(String extrinsicState);
}

// ─────────────────────────────────────────────
// CONCRETE FLYWEIGHT
// Stores ONLY intrinsic (shared, immutable) state.
// This object will be shared across many clients.
// MUST be immutable — no setters, all fields final.
// ─────────────────────────────────────────────

final class ConcreteFlyweight implements Flyweight {

    // Intrinsic state: stored inside the flyweight, shared across contexts.
    // These fields are final — the object is immutable.
    private final String intrinsicState;

    /**
     * Package-private constructor: clients must go through FlyweightFactory.
     * This prevents accidental un-shared instantiation.
     */
    ConcreteFlyweight(String intrinsicState) {
        this.intrinsicState = intrinsicState;
        System.out.println("  [FlyweightFactory] Creating new ConcreteFlyweight: intrinsic='" + intrinsicState + "'");
    }

    /**
     * The flyweight combines its stored intrinsic state with the
     * caller-supplied extrinsic state to perform the operation.
     * No extrinsic state is ever stored as a field.
     */
    @Override
    public void operation(String extrinsicState) {
        System.out.println("ConcreteFlyweight [intrinsic='" + intrinsicState
                + "'] operating with [extrinsic='" + extrinsicState + "']");
    }

    // No setters. No mutable state. Thread-safe by design.
}

// ─────────────────────────────────────────────
// UNSHARED CONCRETE FLYWEIGHT (optional)
// Some flyweights do not need to be shared.
// They hold all state internally and are not
// pooled. The Flyweight interface allows this.
// ─────────────────────────────────────────────

class UnsharedConcreteFlyweight implements Flyweight {

    // Can store both intrinsic and extrinsic state since it is not shared.
    private final String allState;

    public UnsharedConcreteFlyweight(String allState) {
        this.allState = allState;
    }

    @Override
    public void operation(String extrinsicState) {
        System.out.println("UnsharedFlyweight [allState='" + allState
                + "'] called with [extrinsic='" + extrinsicState + "']");
    }
}

// ─────────────────────────────────────────────
// FLYWEIGHT FACTORY
// The central authority for Flyweight objects.
// Ensures that ConcreteFlyweights are shared:
//   - If a flyweight for the given key exists → return it
//   - If not → create, store, and return a new one
//
// Thread-safe: uses ConcurrentHashMap with computeIfAbsent
// to avoid race conditions during concurrent creation.
// ─────────────────────────────────────────────

class FlyweightFactory {

    // The pool: maps intrinsic state key → shared Flyweight instance
    private final Map<String, Flyweight> pool = new ConcurrentHashMap<>();

    /**
     * Returns a shared ConcreteFlyweight for the given intrinsic state.
     * Creates a new one if it does not yet exist in the pool.
     *
     * Thread-safe: computeIfAbsent is atomic in ConcurrentHashMap.
     *
     * @param intrinsicState The key that uniquely identifies the flyweight
     * @return The shared (or newly created) Flyweight instance
     */
    public Flyweight getFlyweight(String intrinsicState) {
        // computeIfAbsent: atomically creates the entry if absent.
        // The lambda is called at most once per key.
        return pool.computeIfAbsent(intrinsicState, ConcreteFlyweight::new);
    }

    /** Returns the current number of unique Flyweight instances in the pool. */
    public int poolSize() {
        return pool.size();
    }

    /** Prints the current state of the pool for introspection. */
    public void printPool() {
        System.out.println("FlyweightFactory pool (" + pool.size() + " entries):");
        pool.forEach((key, fw) -> System.out.println("  key='" + key + "' → " + fw));
    }
}

// ─────────────────────────────────────────────
// CLIENT
// The client maintains extrinsic state separately.
// It always requests flyweights through the factory.
// It passes extrinsic state to the flyweight at call time.
// ─────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {

        FlyweightFactory factory = new FlyweightFactory();

        System.out.println("=== Requesting Flyweights (pool starts empty) ===");

        // First request for "TypeA" — factory creates a new instance
        Flyweight fw1 = factory.getFlyweight("TypeA");
        fw1.operation("context-1");

        // Second request for "TypeA" — factory returns the SAME instance
        Flyweight fw2 = factory.getFlyweight("TypeA");
        fw2.operation("context-2");

        // Verify both references point to the same object (sharing confirmed)
        System.out.println("\nfw1 == fw2 (same shared instance)? " + (fw1 == fw2)); // true

        System.out.println();

        // Request for "TypeB" — factory creates another new instance
        Flyweight fw3 = factory.getFlyweight("TypeB");
        fw3.operation("context-3");

        Flyweight fw4 = factory.getFlyweight("TypeB");
        fw4.operation("context-4");

        System.out.println("fw3 == fw4? " + (fw3 == fw4)); // true

        System.out.println();

        // Demonstrate: 6 usage "slots" but only 2 actual objects in the pool
        Flyweight fw5 = factory.getFlyweight("TypeA"); // reused again
        fw5.operation("context-5");

        System.out.println("\n--- Pool state ---");
        factory.printPool();
        System.out.println("Total distinct Flyweight objects created: " + factory.poolSize());
        System.out.println("Total logical usages: 5 (TypeA×3, TypeB×2) with only "
                + factory.poolSize() + " objects");

        System.out.println();

        // Unshared flyweight — used when full state must be encapsulated
        Flyweight unshared = new UnsharedConcreteFlyweight("fullState-X");
        unshared.operation("ignored-extrinsic");
    }
}

/*
 * KEY STRUCTURAL NOTES:
 *
 * 1. ConcreteFlyweight is IMMUTABLE: all fields are final, no setters.
 *    This is required for safe sharing across multiple clients and threads.
 *
 * 2. Extrinsic state is NEVER stored in the Flyweight.
 *    It is always passed as a parameter to operation().
 *
 * 3. FlyweightFactory uses ConcurrentHashMap + computeIfAbsent for
 *    thread-safe lazy creation without explicit synchronization.
 *
 * 4. Constructor of ConcreteFlyweight is package-private to enforce
 *    factory-only instantiation.
 *
 * 5. fw1 == fw2 demonstrates that the same Java object reference is
 *    returned for the same intrinsic state key.
 *
 * MEMORY SAVINGS ANALOGY:
 * If each Flyweight were 1 KB and you used "TypeA" 1,000,000 times:
 *   Without Flyweight: 1,000,000 × 1 KB = ~1 GB
 *   With Flyweight:    1 × 1 KB = 1 KB (+ extrinsic state per use)
 */
