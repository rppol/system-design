package com.rutik.systemdesign.lld.structural.adapter;

/**
 * ADAPTER PATTERN — Pure Structural Template
 *
 * Intent: Convert the interface of a class into another interface that clients
 * expect. Adapter lets classes work together that couldn't otherwise because of
 * incompatible interfaces.
 *
 * Also known as: Wrapper
 *
 * Two flavors:
 *   - Object Adapter (shown here): uses composition, preferred in Java
 *   - Class Adapter              : uses multiple inheritance (not possible in Java)
 *
 * Participants:
 *   - Target       : the interface the client expects to use
 *   - Adaptee      : the existing class with an incompatible interface
 *   - Adapter      : wraps Adaptee, implements Target — bridges the gap
 *   - Client       : works only with Target; unaware Adaptee exists
 */

// ─────────────────────────────────────────────
// TARGET INTERFACE
// This is what the client knows about and expects.
// New code is written against this interface.
// ─────────────────────────────────────────────

interface Target {
    /**
     * The operation the client wants to call.
     * The Adaptee cannot be called this way directly.
     */
    void request();
}

// ─────────────────────────────────────────────
// ADAPTEE
// An existing class (often legacy or third-party)
// that has useful functionality but an incompatible
// interface. We cannot (or should not) modify it.
// ─────────────────────────────────────────────

class Adaptee {
    /**
     * Useful existing behavior — but the method name and/or
     * signature does not match what the client expects.
     */
    public void specificRequest() {
        System.out.println("Adaptee: specificRequest() called — doing the real work");
    }
}

// ─────────────────────────────────────────────
// ADAPTER (Object Adapter via composition)
// Implements the Target interface so the client
// can use it transparently.
// Holds a reference to an Adaptee instance and
// delegates calls to it after any needed translation.
// ─────────────────────────────────────────────

class Adapter implements Target {

    // Composition: Adapter wraps the Adaptee.
    // Prefer this over inheritance — it decouples Adapter from Adaptee's
    // class hierarchy and allows adapting different Adaptee instances at runtime.
    private final Adaptee adaptee;

    /**
     * Constructor injection: Adaptee is provided externally.
     * Allows adapting any Adaptee instance, including subclasses.
     */
    public Adapter(Adaptee adaptee) {
        this.adaptee = adaptee;
    }

    /**
     * Translates the Target's request() into the Adaptee's specificRequest().
     * Any parameter mapping, data conversion, or protocol translation
     * lives here — the client and the Adaptee never need to change.
     */
    @Override
    public void request() {
        System.out.println("Adapter: translating request() → specificRequest()");
        // Optional pre-processing / parameter conversion goes here
        adaptee.specificRequest();
        // Optional post-processing / result conversion goes here
    }
}

// ─────────────────────────────────────────────
// CLIENT
// Works only with Target. Completely unaware of
// Adaptee's existence or interface.
// ─────────────────────────────────────────────

public class Template {

    /**
     * Client code: receives a Target, calls request().
     * Whether Target is a native implementation or an Adapter
     * is entirely transparent.
     */
    static void clientCode(Target target) {
        target.request();
    }

    public static void main(String[] args) {

        System.out.println("--- Client using a native Target implementation ---");
        // A hypothetical class that already implements Target natively
        Target nativeTarget = new Target() {
            @Override
            public void request() {
                System.out.println("NativeTarget: request() handled directly");
            }
        };
        clientCode(nativeTarget);

        System.out.println();

        System.out.println("--- Client using an Adapter to reach the Adaptee ---");
        Adaptee adaptee = new Adaptee();
        Target adapter = new Adapter(adaptee);
        clientCode(adapter);  // client code is unchanged — same call, different behavior
    }
}

/*
 * KEY STRUCTURAL NOTES:
 *
 * 1. Object Adapter (composition) vs Class Adapter (inheritance):
 *    - Composition is preferred in Java because Java does not support multiple
 *      class inheritance. It also lets you adapt subclasses of Adaptee without
 *      changing the Adapter.
 *
 * 2. The Adapter is the ONLY place where translation logic lives.
 *    Neither the Client nor the Adaptee needs to change.
 *
 * 3. Constructor-inject the Adaptee to keep the Adapter testable.
 *    In tests, pass a mock Adaptee to verify translation behavior.
 *
 * 4. If the Adaptee interface changes, only the Adapter needs updating.
 *
 * WHEN TO USE:
 * - You want to use an existing class but its interface doesn't match.
 * - You want to create a reusable class that cooperates with unrelated classes.
 * - You need to use several existing subclasses, but it's impractical to
 *   adapt their interface by subclassing every one.
 *
 * EXTENSION POINTS:
 * - Two-way Adapter: implements both Target and Adaptee interfaces.
 * - Null Adapter: an Adapter that swallows calls for disabled features.
 */
