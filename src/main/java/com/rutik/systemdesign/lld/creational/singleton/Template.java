package com.rutik.systemdesign.lld.creational.singleton;

/**
 * SINGLETON PATTERN — Template / Skeleton
 *
 * Intent: Ensure a class has only one instance and provide a global access point to it.
 *
 * This file shows THREE standard Java implementations:
 *   1. Enum Singleton          — safest, recommended for most cases
 *   2. Holder Idiom            — lazy + thread-safe without synchronization overhead
 *   3. Double-Checked Locking  — explicit, educational, useful to know for interviews
 *
 * Participants:
 *   - Singleton : the class that manages its own single instance
 *   - Client    : any class that calls Singleton.getInstance()
 */

// ─────────────────────────────────────────────────────────────────────────────
// IMPLEMENTATION 1: Enum Singleton (Recommended)
// ─────────────────────────────────────────────────────────────────────────────
// The JVM guarantees that enum values are instantiated once and only once.
// This is automatically thread-safe, serialization-safe, and reflection-safe.
// ─────────────────────────────────────────────────────────────────────────────
enum SingletonEnum {

    INSTANCE; // The one and only instance

    // Business state — add fields here
    private int state;

    // Business methods — add behavior here
    public void doSomething() {
        // implementation
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IMPLEMENTATION 2: Initialization-on-Demand Holder (Bill Pugh Idiom)
// ─────────────────────────────────────────────────────────────────────────────
// The nested static class `Holder` is not loaded until getInstance() is called.
// The JVM's class initialization guarantee ensures thread safety with zero
// synchronization overhead on the hot path.
// ─────────────────────────────────────────────────────────────────────────────
class SingletonHolder {

    // Private constructor — prevents external instantiation
    private SingletonHolder() {
        // Guard against reflection attacks
        if (Holder.INSTANCE != null) {
            throw new IllegalStateException("Use getInstance()");
        }
        // Perform any expensive initialization here
    }

    // Nested static holder class — loaded lazily, only when getInstance() is first called
    private static final class Holder {
        static final SingletonHolder INSTANCE = new SingletonHolder();
    }

    // Public global access point
    public static SingletonHolder getInstance() {
        return Holder.INSTANCE;
    }

    // ── Business Methods ──────────────────────────────────────────────────────
    public void businessOperation() {
        // Implement your domain logic here
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// IMPLEMENTATION 3: Double-Checked Locking (Explicit, Educational)
// ─────────────────────────────────────────────────────────────────────────────
// First check avoids synchronization on every call (hot path is lock-free).
// Second check (inside synchronized block) prevents double-creation when two
// threads pass the first check simultaneously.
// REQUIRES: `volatile` on the instance field (Java 5+) for correct visibility.
// ─────────────────────────────────────────────────────────────────────────────
class SingletonDCL {

    // volatile ensures that writes to instance are visible across all threads
    // and prevents instruction reordering during object construction
    private static volatile SingletonDCL instance;

    // Private constructor
    private SingletonDCL() {
        // initialization logic
    }

    public static SingletonDCL getInstance() {
        if (instance == null) {                    // First check — fast path, no lock
            synchronized (SingletonDCL.class) {
                if (instance == null) {            // Second check — inside lock
                    instance = new SingletonDCL();
                }
            }
        }
        return instance;
    }

    // ── Business Methods ──────────────────────────────────────────────────────
    public void businessOperation() {
        // implement domain logic
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CLIENT USAGE
// ─────────────────────────────────────────────────────────────────────────────
class SingletonClient {

    public static void main(String[] args) {

        // ── Enum Singleton ────────────────────────────────────────────────────
        SingletonEnum s1 = SingletonEnum.INSTANCE;
        SingletonEnum s2 = SingletonEnum.INSTANCE;
        System.out.println(s1 == s2); // true — same instance

        s1.doSomething();

        // ── Holder Idiom ──────────────────────────────────────────────────────
        SingletonHolder h1 = SingletonHolder.getInstance();
        SingletonHolder h2 = SingletonHolder.getInstance();
        System.out.println(h1 == h2); // true — same instance

        h1.businessOperation();

        // ── Double-Checked Locking ─────────────────────────────────────────────
        SingletonDCL d1 = SingletonDCL.getInstance();
        SingletonDCL d2 = SingletonDCL.getInstance();
        System.out.println(d1 == d2); // true — same instance

        d1.businessOperation();
    }
}
