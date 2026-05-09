package com.rutik.systemdesign.lld.concurrency_patterns;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

// =============================================================================
// THREAD-SAFE SINGLETON PATTERNS IN JAVA
// =============================================================================
// A Singleton ensures a class has only ONE instance and provides a global
// access point to it. The challenge in multi-threaded environments is ensuring
// that two threads cannot create separate instances simultaneously.
// =============================================================================


// =============================================================================
// APPROACH 1: EAGER INITIALIZATION
// =============================================================================
// The instance is created at class load time, before any thread can access it.
// Class loading in the JVM is thread-safe (handled by ClassLoader), so this is
// inherently thread-safe.
//
// PROS: Simple, always thread-safe, no synchronization overhead at runtime.
// CONS: Instance created even if never used (wastes resources if expensive to
//       create). Cannot handle constructor exceptions gracefully.
// =============================================================================
class EagerSingleton {

    // Created when the class is first loaded by the JVM.
    // The JVM guarantees class initialization happens-before any thread
    // can access the static field.
    private static final EagerSingleton INSTANCE = new EagerSingleton();

    private int value;

    // Private constructor prevents external instantiation
    private EagerSingleton() {
        System.out.println("[EagerSingleton] Instance created at class load time");
        this.value = 42;
    }

    public static EagerSingleton getInstance() {
        return INSTANCE; // No synchronization needed - instance already exists
    }

    public int getValue() { return value; }
    public void setValue(int v) { this.value = v; }

    @Override
    public String toString() {
        return "EagerSingleton{value=" + value + ", hashCode=" + System.identityHashCode(this) + "}";
    }
}


// =============================================================================
// APPROACH 2: SYNCHRONIZED METHOD (Lazy Initialization - Thread-Safe but Slow)
// =============================================================================
// The getInstance() method is synchronized, ensuring only one thread can
// execute it at a time. Lazy: instance is created only when first needed.
//
// PROS: Thread-safe, lazy initialization.
// CONS: EVERY call to getInstance() acquires the lock, even after the instance
//       is already created. This is a significant performance bottleneck in
//       high-throughput systems. The lock is needed only once (first call),
//       but we pay for it on every call.
// =============================================================================
class SynchronizedSingleton {

    // Not volatile here because the synchronized method provides full
    // memory visibility guarantees (happens-before via monitor exit/enter).
    private static SynchronizedSingleton instance;

    private String config;

    private SynchronizedSingleton() {
        System.out.println("[SynchronizedSingleton] Instance created");
        this.config = "default-config";
    }

    // The synchronized keyword locks on SynchronizedSingleton.class (class-level lock).
    // Every thread calling getInstance() must acquire this lock.
    // After the first call, 99.9% of calls just do a null check — but still pay
    // the full lock acquisition cost (hundreds of nanoseconds each).
    public static synchronized SynchronizedSingleton getInstance() {
        if (instance == null) {
            instance = new SynchronizedSingleton();
        }
        return instance;
    }

    public String getConfig() { return config; }

    @Override
    public String toString() {
        return "SynchronizedSingleton{config='" + config + "', hashCode=" + System.identityHashCode(this) + "}";
    }
}


// =============================================================================
// APPROACH 3: DOUBLE-CHECKED LOCKING (DCL)
// =============================================================================
// Optimizes Approach 2 by only synchronizing the first few calls (when instance
// is still null). Once created, subsequent reads skip the synchronized block.
//
// CRITICAL: The 'volatile' keyword is MANDATORY for correctness.
//
// WHY volatile IS REQUIRED:
// Without volatile, the JVM/CPU can reorder instructions. The line:
//   instance = new DoubleCheckedSingleton();
// is NOT atomic. It expands roughly to:
//   1. Allocate memory for DoubleCheckedSingleton object
//   2. Call constructor (initialize fields)
//   3. Assign memory address to 'instance'
//
// The JVM can reorder steps 2 and 3! So another thread may see a non-null
// 'instance' (step 3 completed) but the object is not yet fully constructed
// (step 2 not done). That thread returns a partially constructed object —
// a very hard-to-reproduce bug.
//
// volatile provides two guarantees:
//   (a) MEMORY VISIBILITY: writes to 'instance' are immediately visible to all threads.
//   (b) ORDERING: prevents instruction reordering around the volatile write/read.
//       The happens-before relationship is established: the constructor completion
//       happens-before the volatile write, which happens-before the volatile read.
//
// PROS: Thread-safe, lazy, good performance (only synchronizes when null).
// CONS: Slightly complex, volatile has a small but nonzero cost, easy to get wrong
//       (forgetting volatile makes it subtly broken).
// =============================================================================
class DoubleCheckedSingleton {

    // volatile is MANDATORY - without it, DCL is broken on modern JVMs/CPUs
    // due to instruction reordering (see explanation above).
    private static volatile DoubleCheckedSingleton instance;

    private final String databaseUrl;
    private final int maxConnections;

    private DoubleCheckedSingleton() {
        System.out.println("[DoubleCheckedSingleton] Instance created (inside synchronized block)");
        this.databaseUrl = "jdbc:postgresql://localhost:5432/mydb";
        this.maxConnections = 20;
    }

    public static DoubleCheckedSingleton getInstance() {
        // FIRST CHECK: No lock — just read volatile field.
        // If instance is already created (99.9% of calls after startup),
        // we return immediately without acquiring any lock.
        if (instance == null) {
            // Only synchronize when instance appears to be null.
            // Multiple threads might pass the first check simultaneously,
            // but only one can enter the synchronized block at a time.
            synchronized (DoubleCheckedSingleton.class) {
                // SECOND CHECK: Re-check inside the lock.
                // By the time this thread acquires the lock, another thread
                // may have already created the instance. Without this second
                // check, we'd create a second instance.
                if (instance == null) {
                    instance = new DoubleCheckedSingleton(); // volatile write
                }
            }
        }
        return instance; // volatile read (after first creation, always takes this path)
    }

    public String getDatabaseUrl() { return databaseUrl; }
    public int getMaxConnections() { return maxConnections; }

    @Override
    public String toString() {
        return "DoubleCheckedSingleton{db='" + databaseUrl + "', maxConn=" + maxConnections
                + ", hashCode=" + System.identityHashCode(this) + "}";
    }
}


// =============================================================================
// APPROACH 4: BILL PUGH HOLDER IDIOM (Initialization-on-Demand Holder)
// =============================================================================
// Uses the JVM's class loading mechanism to guarantee thread safety without
// any explicit synchronization.
//
// HOW IT WORKS:
// The JVM loads classes lazily — SingletonHolder is NOT loaded when
// HolderSingleton class is loaded. It is only loaded when getInstance() is
// called for the first time (i.e., when SingletonHolder.INSTANCE is accessed).
//
// JVM CLASS LOADING GUARANTEE (JLS §12.4.2):
// Class initialization is performed exactly once, and it is synchronized
// by the JVM using a per-class initialization lock. If multiple threads
// try to trigger initialization simultaneously, all but one will block
// until initialization completes. This is a fundamental JVM guarantee.
//
// Therefore:
//   - LAZY: SingletonHolder is loaded only on first call to getInstance()
//   - THREAD-SAFE: JVM's class initialization lock ensures single creation
//   - NO SYNCHRONIZATION OVERHEAD: After initialization, getInstance() is
//     just a static field read — no locking at all.
//   - NO volatile NEEDED: Class initialization establishes happens-before
//     for all threads that observe the class as initialized.
//
// PROS: Best of all worlds — lazy, thread-safe, zero runtime overhead.
// CONS: Slightly less intuitive than DCL (relies on JVM knowledge).
//       Still breakable via reflection.
// =============================================================================
class HolderSingleton {

    // Private constructor
    private HolderSingleton() {
        System.out.println("[HolderSingleton] Instance created (triggered by class loading)");
    }

    // This static inner class is NOT loaded when HolderSingleton is loaded.
    // It is loaded only when SingletonHolder.INSTANCE is first accessed
    // (i.e., inside getInstance()). JVM class loading is thread-safe.
    private static class SingletonHolder {
        // This field is initialized exactly once, by the class loading mechanism.
        // final guarantees visibility: once the class is initialized, all threads
        // see the fully constructed INSTANCE.
        private static final HolderSingleton INSTANCE = new HolderSingleton();
    }

    public static HolderSingleton getInstance() {
        // Accessing SingletonHolder.INSTANCE triggers class loading of
        // SingletonHolder (if not already loaded). JVM handles thread safety.
        // After first call, this is simply a static field read — zero overhead.
        return SingletonHolder.INSTANCE;
    }

    @Override
    public String toString() {
        return "HolderSingleton{hashCode=" + System.identityHashCode(this) + "}";
    }
}


// =============================================================================
// APPROACH 5: ENUM SINGLETON (Josh Bloch's Recommendation from Effective Java)
// =============================================================================
// Using an enum to implement Singleton is the most concise and robust approach.
//
// WHY ENUM IS SPECIAL:
//   (a) SERIALIZATION SAFETY: Regular singletons can be broken by Java
//       serialization — deserializing creates a new instance. Enums handle
//       serialization natively: the JVM guarantees only one instance per
//       enum constant exists, even after deserialization (readResolve is
//       called automatically by the serialization mechanism for enums).
//
//   (b) REFLECTION SAFETY: You can break regular singletons via reflection
//       by calling setAccessible(true) on the private constructor.
//       Enum constructors cannot be invoked reflectively (IllegalArgumentException
//       is thrown by Constructor.newInstance() for enum types).
//
//   (c) THREAD SAFETY: Enum constants are initialized by the JVM's class loading
//       mechanism, same guarantee as HolderSingleton.
//
//   (d) SIMPLICITY: No boilerplate needed — JVM handles everything.
//
// CONS: Cannot extend another class (enums implicitly extend java.lang.Enum).
//       Feels unconventional to developers unfamiliar with the pattern.
//       Lazy initialization is not straightforward.
// =============================================================================
enum EnumSingleton {

    // The single instance — JVM ensures this is created exactly once.
    INSTANCE;

    // Enum can hold state and behavior — use it like a normal singleton.
    private final List<String> registry = new ArrayList<>();
    private int requestCount = 0;

    // This "constructor" is called once by the JVM during enum initialization.
    // Note: enum constructors are implicitly private.
    EnumSingleton() {
        System.out.println("[EnumSingleton] INSTANCE enum constant initialized");
        // Simulate resource setup
        registry.add("ServiceA");
        registry.add("ServiceB");
    }

    // Business methods on the singleton
    public void register(String serviceName) {
        registry.add(serviceName);
        System.out.println("Registered: " + serviceName + " | Total services: " + registry.size());
    }

    public List<String> getRegisteredServices() {
        return new ArrayList<>(registry); // Return defensive copy
    }

    public int incrementAndGetRequestCount() {
        return ++requestCount; // Not thread-safe — add synchronization if needed
    }

    public int getRequestCount() { return requestCount; }

    // Serialization test helper
    public void demonstrateSerializationSafety() {
        System.out.println("EnumSingleton hashCode: " + System.identityHashCode(this));
        System.out.println("After any serialization/deserialization, same hashCode will appear.");
    }
}


// =============================================================================
// DEMO CLASS
// =============================================================================
// Demonstrates all 5 approaches and verifies thread safety by running
// multiple threads concurrently and asserting only one instance is created.
// =============================================================================
public class ThreadSafeSingletonDemo {

    // Number of concurrent threads to simulate contention during instance creation
    private static final int THREAD_COUNT = 10;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Thread-Safe Singleton Patterns Demo ===\n");

        demoEagerSingleton();
        demoSynchronizedSingleton();
        demoDoubleCheckedLocking();
        demoHolderIdiom();
        demoEnumSingleton();
        concurrencyVerification();

        System.out.println("\n=== All demos completed ===");
    }

    // --------------------------------------------------
    static void demoEagerSingleton() {
        System.out.println("--- 1. Eager Singleton ---");
        EagerSingleton a = EagerSingleton.getInstance();
        EagerSingleton b = EagerSingleton.getInstance();
        System.out.println("Same instance? " + (a == b)); // true
        System.out.println("Instance: " + a);
        System.out.println();
    }

    // --------------------------------------------------
    static void demoSynchronizedSingleton() {
        System.out.println("--- 2. Synchronized Method Singleton ---");
        SynchronizedSingleton a = SynchronizedSingleton.getInstance();
        SynchronizedSingleton b = SynchronizedSingleton.getInstance();
        System.out.println("Same instance? " + (a == b)); // true
        System.out.println("Instance: " + a);
        System.out.println();
    }

    // --------------------------------------------------
    static void demoDoubleCheckedLocking() {
        System.out.println("--- 3. Double-Checked Locking Singleton ---");
        DoubleCheckedSingleton a = DoubleCheckedSingleton.getInstance();
        DoubleCheckedSingleton b = DoubleCheckedSingleton.getInstance();
        System.out.println("Same instance? " + (a == b)); // true
        System.out.println("Instance: " + a);
        System.out.println();
    }

    // --------------------------------------------------
    static void demoHolderIdiom() {
        System.out.println("--- 4. Bill Pugh Holder Idiom ---");
        HolderSingleton a = HolderSingleton.getInstance();
        HolderSingleton b = HolderSingleton.getInstance();
        System.out.println("Same instance? " + (a == b)); // true
        System.out.println("Instance: " + a);
        System.out.println();
    }

    // --------------------------------------------------
    static void demoEnumSingleton() {
        System.out.println("--- 5. Enum Singleton ---");
        EnumSingleton a = EnumSingleton.INSTANCE;
        EnumSingleton b = EnumSingleton.INSTANCE;
        System.out.println("Same instance? " + (a == b)); // true
        a.register("ServiceC");
        System.out.println("Services from b: " + b.getRegisteredServices()); // includes ServiceC
        a.demonstrateSerializationSafety();
        System.out.println();
    }

    // --------------------------------------------------
    // CONCURRENCY VERIFICATION: Spawn THREAD_COUNT threads simultaneously,
    // all racing to call getInstance(). Verify only one instance is ever created.
    // --------------------------------------------------
    static void concurrencyVerification() throws InterruptedException {
        System.out.println("--- Concurrency Verification (DCL + Holder) ---");

        // Test DCL under contention
        System.out.println("Testing DoubleCheckedSingleton under " + THREAD_COUNT + " concurrent threads...");
        ConcurrentHashMap<Integer, DoubleCheckedSingleton> dcInstances = new ConcurrentHashMap<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // All threads wait here until released
                    DoubleCheckedSingleton instance = DoubleCheckedSingleton.getInstance();
                    dcInstances.put(System.identityHashCode(instance), instance);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }, "DCL-Thread-" + id).start();
        }

        startLatch.countDown(); // Release all threads simultaneously
        endLatch.await();       // Wait for all to finish

        System.out.println("Distinct DCL instances created: " + dcInstances.size() + " (expected: 1)");
        System.out.println("DCL thread-safety: " + (dcInstances.size() == 1 ? "PASSED" : "FAILED"));

        // Test Holder under contention
        System.out.println("Testing HolderSingleton under " + THREAD_COUNT + " concurrent threads...");
        ConcurrentHashMap<Integer, HolderSingleton> holderInstances = new ConcurrentHashMap<>();
        CountDownLatch startLatch2 = new CountDownLatch(1);
        CountDownLatch endLatch2 = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int id = i;
            new Thread(() -> {
                try {
                    startLatch2.await();
                    HolderSingleton instance = HolderSingleton.getInstance();
                    holderInstances.put(System.identityHashCode(instance), instance);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch2.countDown();
                }
            }, "Holder-Thread-" + id).start();
        }

        startLatch2.countDown();
        endLatch2.await();

        System.out.println("Distinct Holder instances created: " + holderInstances.size() + " (expected: 1)");
        System.out.println("Holder thread-safety: " + (holderInstances.size() == 1 ? "PASSED" : "FAILED"));
        System.out.println();
    }
}
