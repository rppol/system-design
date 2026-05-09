package com.rutik.systemdesign.lld.creational.prototype;

import java.util.HashMap;
import java.util.Map;

/**
 * PROTOTYPE PATTERN — Template / Skeleton
 *
 * Intent: Specify the kinds of objects to create using a prototypical instance,
 *         and create new objects by copying (cloning) this prototype.
 *
 * Use when:
 *   - Object creation is expensive (DB lookup, complex computation) and a copy
 *     is cheaper than constructing from scratch.
 *   - You need many objects that differ only slightly from a known baseline.
 *   - You want to avoid coupling client code to concrete classes.
 *
 * Key concepts:
 *   - Shallow copy : copies field values; nested objects are shared (same reference).
 *   - Deep copy    : recursively copies all nested objects; no shared state.
 *
 * Participants:
 *   - Prototype          : Interface declaring the clone() method
 *   - ConcretePrototype  : Implements clone() — performs the actual copy
 *   - PrototypeRegistry  : Optional — stores named prototypes; clients clone from it
 *   - Client             : Clones a prototype instead of calling new ConcreteClass()
 */

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1: Declare the Prototype interface
// ─────────────────────────────────────────────────────────────────────────────
interface Prototype {
    /**
     * Returns a copy of this object.
     * Implementations decide whether to do a shallow or deep copy.
     * Using a custom interface (instead of java.lang.Cloneable) gives more
     * control and avoids the quirks of Object.clone().
     */
    Prototype clone();
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2: A mutable nested object — illustrates shallow vs. deep copy
// ─────────────────────────────────────────────────────────────────────────────
class NestedConfig {

    private String value;

    public NestedConfig(String value) {
        this.value = value;
    }

    // Copy constructor — used by deep clone
    public NestedConfig(NestedConfig other) {
        this.value = other.value;
    }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    @Override
    public String toString() {
        return "NestedConfig{value='" + value + "'}";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 3: ConcretePrototype — implements both shallow and deep clone
// ─────────────────────────────────────────────────────────────────────────────
class ConcretePrototype implements Prototype {

    private String name;
    private int count;
    private NestedConfig config; // mutable nested object

    public ConcretePrototype(String name, int count, NestedConfig config) {
        this.name = name;
        this.count = count;
        this.config = config;
    }

    /**
     * SHALLOW CLONE — copies primitives and strings by value,
     * but copies the nested config by reference (shared!).
     * Mutating the clone's config will also affect the original.
     */
    @Override
    public ConcretePrototype clone() {
        return new ConcretePrototype(this.name, this.count, this.config); // config is shared
    }

    /**
     * DEEP CLONE — copies everything recursively.
     * The clone is fully independent — mutations don't affect the original.
     */
    public ConcretePrototype deepClone() {
        return new ConcretePrototype(
                this.name,
                this.count,
                new NestedConfig(this.config) // copy constructor produces new object
        );
    }

    // ── Getters / Setters ──────────────────────────────────────────────────
    public String getName()           { return name; }
    public void setName(String name)  { this.name = name; }
    public int getCount()             { return count; }
    public void setCount(int count)   { this.count = count; }
    public NestedConfig getConfig()   { return config; }

    @Override
    public String toString() {
        return "ConcretePrototype{name='" + name + "', count=" + count
                + ", config=" + config
                + ", id=@" + Integer.toHexString(System.identityHashCode(this)) + "}";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 4: Prototype Registry — a cache of pre-configured prototypes
// ─────────────────────────────────────────────────────────────────────────────
class PrototypeRegistry {

    private final Map<String, Prototype> registry = new HashMap<>();

    /** Register a prototype under a logical name. */
    public void register(String key, Prototype prototype) {
        registry.put(key, prototype);
    }

    /**
     * Clone a registered prototype by name.
     * The client gets a new independent copy without knowing the concrete class.
     */
    public Prototype getClone(String key) {
        Prototype prototype = registry.get(key);
        if (prototype == null) {
            throw new IllegalArgumentException("No prototype registered for key: " + key);
        }
        return prototype.clone();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 5: Client
// ─────────────────────────────────────────────────────────────────────────────
class PrototypeClient {

    public static void main(String[] args) {
        System.out.println("=== Prototype Pattern ===\n");

        // ── Shallow clone demonstration ───────────────────────────────────────
        System.out.println("--- Shallow Clone ---");
        ConcretePrototype original = new ConcretePrototype("original", 10,
                new NestedConfig("shared-config"));
        ConcretePrototype shallowCopy = original.clone();

        System.out.println("Original:     " + original);
        System.out.println("Shallow copy: " + shallowCopy);

        // Mutating the copy's nested object also changes the original!
        shallowCopy.getConfig().setValue("MUTATED");
        System.out.println("\nAfter mutating shallowCopy.config:");
        System.out.println("Original config:     " + original.getConfig()); // also changed!
        System.out.println("Shallow copy config: " + shallowCopy.getConfig());

        System.out.println("\n  ^ Both show MUTATED because they share the same NestedConfig object.");

        // ── Deep clone demonstration ──────────────────────────────────────────
        System.out.println("\n--- Deep Clone ---");
        ConcretePrototype original2 = new ConcretePrototype("original2", 20,
                new NestedConfig("deep-config"));
        ConcretePrototype deepCopy = original2.deepClone();

        System.out.println("Original:   " + original2);
        System.out.println("Deep copy:  " + deepCopy);

        deepCopy.getConfig().setValue("MUTATED");
        System.out.println("\nAfter mutating deepCopy.config:");
        System.out.println("Original config:  " + original2.getConfig()); // unchanged
        System.out.println("Deep copy config: " + deepCopy.getConfig());

        System.out.println("\n  ^ Original unchanged — deep copy has its own NestedConfig.");

        // ── Registry demonstration ────────────────────────────────────────────
        System.out.println("\n--- Prototype Registry ---");
        PrototypeRegistry registry = new PrototypeRegistry();
        registry.register("default",  new ConcretePrototype("default",  0, new NestedConfig("base")));
        registry.register("enhanced", new ConcretePrototype("enhanced", 5, new NestedConfig("plus")));

        Prototype c1 = registry.getClone("default");
        Prototype c2 = registry.getClone("default");
        Prototype c3 = registry.getClone("enhanced");

        System.out.println("Clone from 'default':  " + c1);
        System.out.println("Clone from 'default':  " + c2);
        System.out.println("Clone from 'enhanced': " + c3);
        System.out.println("c1 != c2 (different instances): " + (c1 != c2));
    }
}
