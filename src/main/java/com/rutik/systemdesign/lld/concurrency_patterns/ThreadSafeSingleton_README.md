# Thread-Safe Singleton Pattern

## Table of Contents
1. [What is Singleton?](#what-is-singleton)
2. [Why Thread Safety Matters](#why-thread-safety-matters)
3. [Comparison of All 5 Approaches](#comparison-of-all-5-approaches)
4. [Approach Deep-Dives](#approach-deep-dives)
   - [Eager Initialization](#1-eager-initialization)
   - [Synchronized Method](#2-synchronized-method)
   - [Double-Checked Locking](#3-double-checked-locking-dcl)
   - [Bill Pugh Holder Idiom](#4-bill-pugh-holder-idiom)
   - [Enum Singleton](#5-enum-singleton)
5. [Why DCL Needs volatile](#why-dcl-needs-volatile)
6. [Why Holder Idiom Works](#why-holder-idiom-works)
7. [When to Use Enum Singleton](#when-to-use-enum-singleton)
8. [Common Mistakes](#common-mistakes)
9. [Testing Thread-Safe Singletons](#testing-thread-safe-singletons)
10. [Spring's Alternative](#springs-alternative)
11. [Interview Questions](#interview-questions)

---

## Intuition

> **One-line analogy**: Thread-Safe Singleton is like ensuring only one president is inaugurated even if two election processes run simultaneously — you need coordination mechanisms to prevent two simultaneous "winners."

**Mental model**: In single-threaded code, lazy Singleton is simple: check if instance is null, create if so, return it. With multiple threads, two threads can simultaneously pass the null check, both create instances, and return different objects — breaking the guarantee. The solutions range from eager initialization (create instance at class loading, before threads start) to double-checked locking with volatile (minimize synchronization overhead).

**Why it matters**: Thread safety is invisible until it causes a production bug — often intermittent, hard to reproduce. Understanding the five thread-safe Singleton implementations (eager, synchronized method, DCL + volatile, Bill Pugh holder, enum) is a core Java concurrency interview topic.

**Key insight**: The Bill Pugh Holder Idiom is the best pure Java solution — lazy initialization via inner class, no synchronization overhead, thread-safe by JVM class loading guarantees. Enum Singleton is best when serialization safety also matters.

---

## What is Singleton?

The Singleton pattern ensures a class has **exactly one instance** throughout the application's lifetime and provides a **global access point** to it.

**Real-world use cases:**
- Database connection pools
- Logger instances
- Configuration managers
- Thread pools
- Cache managers
- Service registries

---

## Why Thread Safety Matters

In a single-threaded application, a naive lazy singleton works fine:

```java
// BROKEN in multi-threaded environments
class NaiveSingleton {
    private static NaiveSingleton instance;
    public static NaiveSingleton getInstance() {
        if (instance == null) {          // Thread A checks: null
            instance = new NaiveSingleton(); // Thread B also checks: null, both create!
        }
        return instance;
    }
}
```

**The race condition:**

```
Thread A                          Thread B
--------                          --------
if (instance == null) -> TRUE
                                  if (instance == null) -> TRUE (A hasn't written yet)
instance = new Singleton()
                                  instance = new Singleton()  <- SECOND INSTANCE!
return instance (instance #1)
                                  return instance (instance #2)
```

Both threads get different instances. All state managed by the "singleton" is now split across two objects. This leads to subtle, hard-to-reproduce bugs in production.

---

## Comparison of All 5 Approaches

| Approach               | Thread-Safe | Lazy Init | Performance | Serialization-Safe | Reflection-Safe | Complexity |
|------------------------|:-----------:|:---------:|:-----------:|:------------------:|:---------------:|:----------:|
| Eager Initialization   | YES         | NO        | Fast        | NO (needs readResolve) | NO          | Low        |
| Synchronized Method    | YES         | YES       | Slow        | NO                 | NO              | Low        |
| Double-Checked Locking | YES*        | YES       | Fast        | NO                 | NO              | Medium     |
| Bill Pugh Holder       | YES         | YES       | Fast        | NO                 | NO              | Medium     |
| Enum Singleton         | YES         | NO**      | Fast        | YES (built-in)     | YES (built-in)  | Low        |

\* Only with `volatile` keyword — without it, DCL is broken.
\*\* Enum constants are initialized when the enum class is loaded; can simulate lazy loading with nested enums.

**Recommendation for most cases: Bill Pugh Holder Idiom (production Java code)**
**Recommendation when serialization/reflection matters: Enum Singleton**

---

## Approach Deep-Dives

### 1. Eager Initialization

```java
class EagerSingleton {
    private static final EagerSingleton INSTANCE = new EagerSingleton();
    private EagerSingleton() {}
    public static EagerSingleton getInstance() { return INSTANCE; }
}
```

**How it works:** JVM initializes the static field when the class is loaded. Class loading is thread-safe (per JLS §12.4.2), so INSTANCE is created exactly once, before any thread can call getInstance().

**When to use:** When the singleton is always needed and cheap to create.

---

### 2. Synchronized Method

```java
class SynchronizedSingleton {
    private static SynchronizedSingleton instance;
    public static synchronized SynchronizedSingleton getInstance() {
        if (instance == null) instance = new SynchronizedSingleton();
        return instance;
    }
}
```

**Problem:** The lock is acquired on every call. After startup, `getInstance()` might be called millions of times per second. Each call pays ~100-300ns for lock acquisition — unnecessary after the first call.

**When to use:** Almost never. Use only when simplicity is paramount and getInstance() is rarely called.

---

### 3. Double-Checked Locking (DCL)

```java
class DoubleCheckedSingleton {
    private static volatile DoubleCheckedSingleton instance;  // volatile is REQUIRED
    public static DoubleCheckedSingleton getInstance() {
        if (instance == null) {                     // First check (no lock)
            synchronized (DoubleCheckedSingleton.class) {
                if (instance == null) {             // Second check (with lock)
                    instance = new DoubleCheckedSingleton();
                }
            }
        }
        return instance;
    }
}
```

See [Why DCL Needs volatile](#why-dcl-needs-volatile) for the deep explanation.

---

### 4. Bill Pugh Holder Idiom

```java
class HolderSingleton {
    private HolderSingleton() {}
    private static class SingletonHolder {
        private static final HolderSingleton INSTANCE = new HolderSingleton();
    }
    public static HolderSingleton getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
```

See [Why Holder Idiom Works](#why-holder-idiom-works) for the deep explanation.

---

### 5. Enum Singleton

```java
enum EnumSingleton {
    INSTANCE;
    public void doSomething() { ... }
}
// Usage: EnumSingleton.INSTANCE.doSomething();
```

See [When to Use Enum Singleton](#when-to-use-enum-singleton) for details.

---

## Why DCL Needs volatile

This is one of the most important Java memory model subtleties. Without `volatile`, DCL has a subtle, platform-dependent bug.

### Object Construction is Not Atomic

The line `instance = new DoubleCheckedSingleton()` compiles to roughly:

```
1. memory = allocate(sizeof(DoubleCheckedSingleton))   // Allocate raw memory
2. DoubleCheckedSingleton.<init>(memory)               // Run constructor (initialize fields)
3. instance = memory                                   // Assign reference to static field
```

### The Reordering Problem

The JVM and CPU are allowed to reorder steps 2 and 3 (as long as the result is equivalent within a single thread). So the actual execution might be:

```
1. memory = allocate(sizeof(DoubleCheckedSingleton))   // Allocate memory
3. instance = memory                                   // Assign (BEFORE constructor runs!)
2. DoubleCheckedSingleton.<init>(memory)               // Constructor runs
```

### The Race Condition

```
Thread A (creating instance)       Thread B (reading instance)
---------------------------------  ---------------------------------
memory = allocate(...)
instance = memory    <-- write
                                   if (instance == null) -> FALSE  (sees non-null!)
                                   return instance  <-- partially constructed object!
<init>(memory)       <-- writes fields AFTER Thread B already returned the object
```

Thread B gets a reference to an object whose constructor hasn't finished yet. Its fields may contain default values (0, null, false). This causes `NullPointerException` or `ClassCastException` downstream — bugs that appear randomly under load.

### How volatile Fixes It

`volatile` provides the **happens-before** guarantee via the Java Memory Model (JMM):

- **Volatile write**: All writes performed before a volatile write are visible to any thread that subsequently reads the volatile variable.
- **Volatile read**: After reading a volatile variable, the reading thread sees all writes that happened before the volatile write.

This means: the constructor completing (step 2) happens-before the volatile write (step 3), which happens-before Thread B's volatile read. Thread B is guaranteed to see a fully constructed object.

Additionally, `volatile` **prevents the compiler from reordering** steps 2 and 3, eliminating the problem entirely.

### Memory Visibility Without volatile

Even if reordering didn't happen, without `volatile`, Thread B might read a stale cached value of `instance` from its CPU cache. `volatile` forces the value to be read from main memory.

---

## Why Holder Idiom Works

The Holder idiom relies on a fundamental JVM guarantee from the Java Language Specification (JLS §12.4):

> "A class or interface type T will be initialized immediately before the first occurrence of any one of the following: a static field of T is assigned, a static field of T that is a compile-time constant is used, T is a top level class and an assert statement nested within T is executed, an instance of T is created, or T is a static nested class."

More specifically, JLS §12.4.2 specifies that class initialization is synchronized using a **per-class initialization lock (LC)**. Only one thread can run the initialization procedure at a time. Other threads wait until initialization completes.

### Step-by-Step for Holder Idiom

1. Thread A calls `HolderSingleton.getInstance()`.
2. JVM detects `SingletonHolder` has not been initialized. Acquires `LC` for `SingletonHolder`.
3. Thread A runs `SingletonHolder`'s static initializer: `INSTANCE = new HolderSingleton()`.
4. Thread B calls `getInstance()` concurrently. JVM sees `SingletonHolder` is being initialized. Thread B **blocks** on `LC`.
5. Thread A finishes initialization. Releases `LC`. `INSTANCE` is fully constructed and `final`.
6. Thread B acquires `LC`, sees initialization is done, proceeds. Returns the same `INSTANCE`.

### Why `final` Matters

`INSTANCE` is declared `final`. The JMM guarantees that **a final field is visible to all threads once the constructor completes**, without any synchronization. Combined with the class initialization guarantee, every thread will always see the fully constructed `INSTANCE`.

### After Initialization

Once `SingletonHolder` is initialized, `getInstance()` becomes a simple read of a final static field — faster than any locking or volatile read.

---

## When to Use Enum Singleton

### Serialization Attack Prevention

A regular singleton can be broken by serialization/deserialization:

```java
// Attack on a regular singleton
ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("singleton.ser"));
oos.writeObject(NormalSingleton.getInstance());
ObjectInputStream ois = new ObjectInputStream(new FileInputStream("singleton.ser"));
NormalSingleton deserialized = (NormalSingleton) ois.readObject();

// deserialized != NormalSingleton.getInstance() — TWO INSTANCES!
```

Fix for regular singletons: implement `readResolve()`:
```java
protected Object readResolve() { return getInstance(); }
```

**Enum doesn't need this fix** — the JVM's serialization mechanism automatically handles enum constants. Only the name is serialized; on deserialization, `Enum.valueOf()` is used to retrieve the existing constant.

### Reflection Attack Prevention

```java
// Attack on a regular singleton
Constructor<NormalSingleton> c = NormalSingleton.class.getDeclaredConstructor();
c.setAccessible(true);
NormalSingleton second = c.newInstance(); // Creates a second instance!
```

For enums, `Constructor.newInstance()` throws `IllegalArgumentException` for enum types. The JVM actively prevents this.

### When NOT to use Enum

- When you need lazy initialization with resource-intensive setup.
- When the class needs to extend another class (enums implicitly extend `java.lang.Enum`).
- When the class needs to implement multiple interfaces and you want to avoid the enum syntax for clarity.

---

## Common Mistakes

### Mistake 1: Missing volatile in DCL

```java
// BROKEN - looks correct but has subtle bug on multi-core systems
private static DoubleCheckedSingleton instance; // Missing volatile!
```

This compiles and often works in testing (single-core machines, lucky scheduling) but fails randomly in production under high load.

### Mistake 2: Using String Interning as a Lock

```java
// DANGEROUS - do not do this
synchronized ("SINGLETON_LOCK") { ... }
```

String literals are interned (shared across the JVM), so any class using the same string literal will share the lock. This causes unintended coupling and potential deadlocks.

### Mistake 3: Ignoring Serialization

Implementing `Serializable` on a regular singleton without `readResolve()` silently creates multiple instances when the singleton is persisted and restored (e.g., in distributed caches or RMI).

### Mistake 4: Making Singleton a Superclass

Subclassing a singleton defeats the entire pattern. The subclass constructor calls `super()`, creating an instance of the parent, potentially creating multiple "singletons."

### Mistake 5: Using Class-Level Lock When Instance-Level Lock Suffices

```java
// Heavy-handed: locks the class even for unrelated operations
public static synchronized void doSomething() { ... }

// Better: use instance-level or field-level locks if the singleton
// has independent components
```

---

## Testing Thread-Safe Singletons

Testing that a singleton is truly thread-safe requires simulating concurrent access:

```java
@Test
public void testSingletonUnderConcurrency() throws InterruptedException {
    int threadCount = 100;
    Set<Integer> instanceHashCodes = Collections.synchronizedSet(new HashSet<>());
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        new Thread(() -> {
            try {
                startLatch.await(); // Maximize contention by starting all at once
                instanceHashCodes.add(System.identityHashCode(MySingleton.getInstance()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                endLatch.countDown();
            }
        }).start();
    }

    startLatch.countDown(); // Release all threads simultaneously
    endLatch.await(5, TimeUnit.SECONDS);

    assertEquals(1, instanceHashCodes.size(), "Only one instance should exist");
}
```

**Key testing tips:**
- Use `CountDownLatch` to maximize contention (all threads start simultaneously).
- Use `System.identityHashCode()` to distinguish instances (not `equals()` or `hashCode()` which may be overridden).
- Run the test multiple times — race conditions are not always reproducible.
- Use tools like `jcstress` (Java Concurrency Stress Tests) for thorough memory model testing.

---

## Spring's Alternative

In Spring applications, the framework manages bean lifecycle and eliminates the need for manual singleton patterns.

### Spring's `@Scope("singleton")` (Default)

```java
@Component  // or @Service, @Repository, etc.
// @Scope("singleton") is implicit — Spring creates one instance per ApplicationContext
public class MyService {
    // No getInstance() needed — Spring injects the same instance everywhere
}

// Usage:
@Autowired MyService myService; // Always the same instance within the ApplicationContext
```

**Spring singleton vs. classic Singleton pattern:**
- Spring's singleton scope means one instance per **ApplicationContext**, not per JVM. In tests, each `@SpringBootTest` gets its own context.
- Classic Singleton pattern means one instance per **JVM ClassLoader**.

### When to Use Classic Singleton in a Spring App

Generally, you should not. Let Spring manage all beans as singletons. However, classic singletons (especially Enum singletons) are appropriate for:
- Utility classes that are not Spring-managed (e.g., in library code).
- Enums serving as strategy/strategy registries.
- Static contexts where DI is not available.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Thread-Safe Singleton Appears in Distributed Systems**

- **Connection pool managers** — HikariCP and c3p0 are Singletons initialized once per JVM. The Bill Pugh Holder idiom ensures that under concurrent startup (multiple threads calling `getPool()` simultaneously), only one pool is created.
- **Distributed configuration clients** — Config clients (Consul client, etcd client, Spring Cloud Config client) are Singletons: one instance holds the live config snapshot and propagates changes to all in-process subscribers. Thread safety is critical because config can be updated concurrently with reads.
- **API gateway rate-limiter registry** — A `RateLimiterRegistry` Singleton manages per-client token buckets. Thread safety is required as hundreds of threads check and decrement counters simultaneously.
- **Scaling caveat** — At system scale, a Singleton guarantees one instance per JVM process. With 20 replicas, you have 20 singletons. State that must be globally unique requires a distributed coordination layer (Redis, etcd), not a local Singleton.

---

## Interview Questions

**Q1: Why is DCL broken without volatile?**
Without `volatile`, the JVM can reorder object construction steps so the reference is published before the constructor runs. Another thread reading the non-null reference gets a partially constructed object.

**Q2: What makes the Holder idiom better than DCL?**
Holder relies on JVM class loading (which is inherently synchronized) rather than explicit `synchronized` blocks or `volatile`. It achieves the same thread safety with no runtime overhead and is harder to implement incorrectly.

**Q3: How does Enum prevent serialization attacks?**
Java's serialization mechanism uses `Enum.valueOf(enumClass, name)` to deserialize enum constants instead of calling the constructor. This ensures the existing constant is returned, never creating a new instance.

**Q4: Can you break an Enum singleton via reflection?**
No. `Constructor.newInstance()` explicitly checks if the class is an enum and throws `IllegalArgumentException`. This is enforced by the JVM, not user code.

**Q5: What is the happens-before guarantee provided by volatile?**
A write to a volatile variable happens-before all subsequent reads of that variable. All actions before a volatile write are visible to all threads after they read the volatile variable. This establishes a memory barrier that prevents both reordering and CPU cache staleness.

**Q6: How would you implement a singleton that supports lazy initialization AND is safe under serialization AND reflection?**
Use an Enum singleton. It satisfies all three requirements natively. Alternatively, use the Holder idiom with `readResolve()` for serialization safety (but reflection safety requires additional checks in the constructor: `throw new IllegalStateException("Already instantiated")`).
