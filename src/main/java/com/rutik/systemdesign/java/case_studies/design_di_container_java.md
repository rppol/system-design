# Design a Dependency Injection Container (Java)

> **A DI container is a recipe book that cooks itself.**  
> Given a set of classes annotated with instructions ("I need a `PaymentService` and a `Logger`"),
> the container reads the recipes, resolves ingredients recursively, and assembles the final
> dish — without you writing a single `new` statement.

**Key insight:** Dependency Injection at its core is just `reflection + a Map<Class, Object>`.
Everything Spring adds on top (scopes, proxies, lifecycle hooks, AOP) is built on this 10-line
foundation. Understanding the foundation demystifies every Spring behaviour from
`@Scope("prototype")` to circular dependency handling.

---

## 1. Requirements Clarification

### Functional requirements
- Register classes by type and retrieve singleton instances
- Support constructor injection (`@Inject` annotation)
- Support named bindings (`@Named("paymentProcessor")`)
- Detect and report circular dependencies at startup, not at runtime
- Support multiple bindings for the same interface (via `@Named` qualifiers)
- Eager singleton creation on container startup (validates all dependencies upfront)
- Support `@PostConstruct` lifecycle callbacks after instance creation

### Non-functional requirements
- **Startup time**: resolve all dependencies in < 500ms for a graph of 500 classes
- **Lookup time**: `getBean(Class)` must be O(1) — cache all resolved instances
- **Thread safety**: the container itself is immutable after build; concurrent `getBean()` is lock-free
- **Memory**: one instance per singleton type; no duplicate allocations

### Out of scope
- Spring-compatible proxies (CGLIB, JDK proxy — see `spring/spring_proxies/`)
- Field injection (discourages immutability and testability)
- HTTP request scope / session scope (not needed for pure Java container)
- Configuration properties binding (purely a framework concern)

---

## 2. Scale Estimation

**Startup overhead analysis:**
```
Graph size: 500 classes (typical medium Spring app has 200–800 beans)
Reflection calls: 500 classes × ~5 constructor params avg = 2,500 reflection lookups
Reflection cost: ~200 ns per getDeclaredConstructors() = 500 µs total
Object creation: 500 instances × ~1 µs avg = 500 µs
Total startup: ~1ms for pure reflection + instantiation

Memory per class entry in container:
  ConcurrentHashMap<Class, Object> entry: ~100 bytes + object size
  500 × 100 bytes = 50 KB overhead — negligible

Runtime lookup: O(1) HashMap.get() = ~30 ns
```

---

## 3. High-Level Architecture

```
+------------------------------------------------------------------+
|                    DI Container                                  |
|                                                                  |
|  [1] Registration phase                                          |
|    bind(Interface.class, Implementation.class, scope)           |
|    bind(Interface.class).toInstance(existingObject)             |
|    bind(Interface.class).toProvider(Supplier)                   |
|                          |                                       |
|                          v                                       |
|  bindings: Map<Class, Binding>                                   |
|    {Interface → (Implementation.class, SINGLETON)}              |
|                          |                                       |
|                          v                                       |
|  [2] Eager startup resolve (Injector.build())                   |
|    For each singleton binding:                                   |
|      resolve(type) → recursively resolve dependencies           |
|      detect cycles (inProgressSet)                              |
|      cache in singletonCache                                     |
|                          |                                       |
|                          v                                       |
|  singletonCache: Map<Class, Object>                              |
|    {PaymentService → paymentService instance}                   |
|    {OrderService → orderService instance}                       |
|                          |                                       |
|                          v                                       |
|  [3] Runtime getBean(Class) — O(1) cache lookup                 |
+------------------------------------------------------------------+
```

**Component inventory:**
- `Binding<T>` — records: implementation class, instance (for `toInstance()`), provider, scope
- `ContainerBuilder` — fluent DSL for registrations before container is built
- `Injector` — the built (immutable) container; `getBean(Class)` and `getBean(String)` API
- `DependencyResolver` — recursive constructor-parameter resolution + cycle detection
- `LifecycleManager` — calls `@PostConstruct` after each instance is created

---

## 4. Component Deep Dives

### 4.1 Core annotations and binding model

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD})
public @interface Inject {}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.TYPE})
public @interface Named {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostConstruct {}

// Binding: what to create for a given type key
public record Binding<T>(
    Class<T> type,
    Class<? extends T> implementation,   // null if instance/provider
    T instance,                          // null if implementation/provider
    Supplier<T> provider,                // null if implementation/instance
    boolean singleton
) {}
```

---

### 4.2 `ContainerBuilder` — fluent registration DSL

```java
import java.util.*;
import java.util.function.Supplier;

public class ContainerBuilder {

    // bindings: Map<qualifiedKey, Binding>
    // qualifiedKey = InterfaceClass + optional @Named value
    private final Map<String, Binding<?>> bindings = new LinkedHashMap<>();

    // Register interface → implementation (constructor injection)
    public <T, I extends T> BindingBuilder<T> bind(Class<T> type) {
        return new BindingBuilder<>(type, this);
    }

    @SuppressWarnings("unchecked")
    <T> void register(String key, Binding<T> binding) {
        bindings.put(key, binding);
    }

    public Injector build() {
        return new Injector(Collections.unmodifiableMap(bindings));
    }

    // Inner builder for fluent DSL
    public static class BindingBuilder<T> {
        private final Class<T> type;
        private final ContainerBuilder parent;
        private String name = "";

        BindingBuilder(Class<T> type, ContainerBuilder parent) {
            this.type = type;
            this.parent = parent;
        }

        public BindingBuilder<T> named(String name) {
            this.name = name;
            return this;
        }

        public void to(Class<? extends T> impl) {
            String key = type.getName() + ":" + name;
            parent.register(key, new Binding<>(type, impl, null, null, true));
        }

        public void toInstance(T instance) {
            String key = type.getName() + ":" + name;
            parent.register(key, new Binding<>(type, null, instance, null, true));
        }

        public void toProvider(Supplier<T> supplier) {
            String key = type.getName() + ":" + name;
            parent.register(key, new Binding<>(type, null, null, supplier, true));
        }
    }
}
```

---

### 4.3 `DependencyResolver` — recursive resolution with cycle detection

```java
import java.lang.reflect.*;
import java.util.*;

public class DependencyResolver {

    private final Map<String, Binding<?>> bindings;
    private final Map<String, Object> singletonCache;  // the instance cache
    private final Set<String> inProgress;              // cycle detection

    public DependencyResolver(Map<String, Binding<?>> bindings) {
        this.bindings = bindings;
        this.singletonCache = new HashMap<>();
        this.inProgress = new LinkedHashSet<>();        // preserves insertion order for error message
    }

    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> type, String name) {
        String key = type.getName() + ":" + name;

        // 1. Check singleton cache first (O(1))
        if (singletonCache.containsKey(key)) {
            return (T) singletonCache.get(key);
        }

        // 2. Find binding
        Binding<?> binding = bindings.get(key);
        if (binding == null) {
            throw new IllegalStateException(
                "No binding found for " + type.getName() +
                (name.isEmpty() ? "" : " named '" + name + "'") +
                ". Registered bindings: " + bindings.keySet());
        }

        // 3. Cycle detection
        if (inProgress.contains(key)) {
            List<String> cycle = new ArrayList<>(inProgress);
            cycle.add(key);
            throw new CircularDependencyException(
                "Circular dependency detected: " +
                String.join(" → ", cycle));
        }

        // 4. Resolve the instance
        inProgress.add(key);
        try {
            T instance = createInstance(binding);

            // 5. @PostConstruct lifecycle
            invokePostConstruct(instance);

            // 6. Cache if singleton
            if (binding.singleton()) {
                singletonCache.put(key, instance);
            }
            return instance;
        } finally {
            inProgress.remove(key);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createInstance(Binding<T> binding) {
        if (binding.instance() != null) {
            return binding.instance();  // pre-built instance
        }
        if (binding.provider() != null) {
            return binding.provider().get();  // factory method
        }

        // Constructor injection: find @Inject constructor (or single constructor)
        Class<? extends T> impl = binding.implementation();
        Constructor<? extends T> constructor = findInjectConstructor(impl);

        // Resolve each parameter type
        Object[] args = Arrays.stream(constructor.getParameters())
            .map(param -> {
                String paramName = param.isAnnotationPresent(Named.class)
                    ? param.getAnnotation(Named.class).value()
                    : "";
                return resolve(param.getType(), paramName);
            })
            .toArray();

        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate " + impl.getName(), e);
        }
    }

    private <T> Constructor<T> findInjectConstructor(Class<T> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        // Find @Inject-annotated constructor
        List<Constructor<?>> injected = Arrays.stream(constructors)
            .filter(c -> c.isAnnotationPresent(Inject.class))
            .collect(java.util.stream.Collectors.toList());

        if (injected.size() == 1) {
            return (Constructor<T>) injected.get(0);
        }
        if (injected.size() > 1) {
            throw new IllegalStateException(clazz.getName() + " has multiple @Inject constructors");
        }

        // No @Inject: use the single no-arg or single constructor
        if (constructors.length == 1) {
            return (Constructor<T>) constructors[0];
        }

        throw new IllegalStateException(
            clazz.getName() + " has " + constructors.length +
            " constructors but none is annotated with @Inject. " +
            "Annotate exactly one constructor with @Inject.");
    }

    private void invokePostConstruct(Object instance) {
        Arrays.stream(instance.getClass().getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(PostConstruct.class))
            .forEach(m -> {
                try {
                    m.setAccessible(true);
                    m.invoke(instance);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("@PostConstruct failed on " +
                        instance.getClass().getName() + "." + m.getName(), e);
                }
            });
    }
}

public class CircularDependencyException extends RuntimeException {
    public CircularDependencyException(String message) { super(message); }
}
```

---

### 4.4 `Injector` — the built container

```java
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Injector {

    private final Map<String, Object> singletonCache;

    Injector(Map<String, Binding<?>> bindings) {
        // Eagerly resolve all singletons at startup
        DependencyResolver resolver = new DependencyResolver(bindings);
        bindings.keySet().forEach(key -> {
            String[] parts = key.split(":", 2);
            try {
                Class<?> type = Class.forName(parts[0]);
                String name = parts.length > 1 ? parts[1] : "";
                resolver.resolve(type, name);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot load class " + parts[0], e);
            }
        });
        this.singletonCache = Collections.unmodifiableMap(resolver.getSingletonCache());
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type) {
        return getBean(type, "");
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> type, String name) {
        String key = type.getName() + ":" + name;
        Object instance = singletonCache.get(key);
        if (instance == null) {
            throw new IllegalStateException("No bean found for type " + type.getName() +
                (name.isEmpty() ? "" : " named '" + name + "'"));
        }
        return (T) instance;
    }

    // For diagnostic use
    public Set<String> getRegisteredKeys() {
        return singletonCache.keySet();
    }
}
```

---

### 4.5 Usage example

```java
// Domain classes
public interface PaymentGateway {
    PaymentResult charge(PaymentRequest request);
}

public class StripeGateway implements PaymentGateway {
    private final HttpClient httpClient;

    @Inject
    public StripeGateway(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public PaymentResult charge(PaymentRequest request) { /* ... */ return null; }

    @PostConstruct
    public void init() {
        System.out.println("StripeGateway initialized — warming up connection pool");
    }
}

public class OrderService {
    private final PaymentGateway payment;
    private final InventoryService inventory;

    @Inject
    public OrderService(PaymentGateway payment, InventoryService inventory) {
        this.payment = payment;
        this.inventory = inventory;
    }

    public Order createOrder(CreateOrderRequest req) {
        inventory.reserve(req.items());
        PaymentResult result = payment.charge(req.paymentDetails());
        return new Order(req, result);
    }
}

// Wiring
Injector injector = new ContainerBuilder()
    .bind(PaymentGateway.class).to(StripeGateway.class)
    .bind(HttpClient.class).toInstance(HttpClient.newBuilder().build())
    .bind(InventoryService.class).to(InventoryServiceImpl.class)
    .bind(OrderService.class).to(OrderService.class)
    .build();  // eagerly resolves all; throws CircularDependencyException if cycles found

OrderService orderService = injector.getBean(OrderService.class);
```

---

### 4.6 Broken pattern — circular dependency without detection

**Broken:**
```java
public class ServiceA {
    @Inject public ServiceA(ServiceB b) { /* ... */ }
}
public class ServiceB {
    @Inject public ServiceB(ServiceA a) { /* ... */ }
}

// Without cycle detection: stack overflow
// resolve(A) → createInstance(A) → resolve(B) → createInstance(B) → resolve(A) → ...
// → StackOverflowError at ~10,000 recursion depth
```

**Fixed (the `inProgress` set in `DependencyResolver`):**
```
resolve(A)              → inProgress = {A}
  createInstance(A)     → needs B
    resolve(B)          → inProgress = {A, B}
      createInstance(B) → needs A
        resolve(A)      → A is in inProgress!
          → throw CircularDependencyException("A → B → A")
```

The error message shows the exact cycle path, enabling fast diagnosis. Spring's circular
dependency error message follows the same pattern.

---

## 5. Design Decisions & Tradeoffs

### Decision 1: Eager vs lazy singleton resolution

| | Eager (at `build()`) | Lazy (at first `getBean()`) |
|--|---------------------|---------------------------|
| Fail-fast | Yes — all errors at startup | No — errors at runtime |
| Startup time | Higher (all objects created) | Lower |
| Thread safety | Container is immutable + safe after build | Need double-checked locking |
| Production recommendation | **Prefer eager** — fail-fast is critical for services | Use lazy only for rarely-used expensive objects |

Spring Boot uses eager by default for singletons; `@Lazy` opts specific beans out.

### Decision 2: Constructor vs field injection

| | Constructor injection | Field injection |
|--|----------------------|-----------------|
| Immutability | Supports `final` fields | Cannot use `final` |
| Testability | Inject mocks via constructor (no Spring needed) | Requires Spring test context for field injection |
| Cycle detection | Easy (at resolve time) | Hard (field access is deferred) |
| Effective Java Item 17 | Promotes immutability | Violates it |
| **Recommendation** | **Constructor injection always** | Avoid |

### Decision 3: Interface key vs class key

Using `Interface.class` as the key (rather than implementation class) allows switching implementations
without changing callers — the Open/Closed Principle (Effective Java Item 64: prefer interfaces
over classes). Using implementation class as key loses this benefit. The container must resolve
the *declared parameter type* (usually an interface), not the implementation class.

### Decision 4: `LinkedHashSet<String> inProgress` for ordered cycle error message

A `HashSet` would detect cycles but report them as `{A, B, A}` in undefined order.
`LinkedHashSet` preserves insertion order, producing `A → B → A` — the exact cycle path.
Small detail, large debugging improvement.

### Decision 5: `@Named` qualifier for multiple bindings of the same interface

Without qualifiers, you cannot bind `PaymentGateway` to both `StripeGateway` and `PayPalGateway`.
`@Named("stripe")` and `@Named("paypal")` allows both. The binding key is `(type, name)` pair.
Spring's `@Qualifier` works identically; JSR-330's `@Named` is the standard.

---

## 6. Real-World Implementations

### Google Guice — the original JSR-330 implementation

Guice (2007) popularised constructor injection with annotations. Guice uses `Binding<T>` objects
identical to the above design, but adds: Interceptors (AOP via `MethodInterceptor`), scopes
(Singleton, PerRequest, custom), and `Provider<T>` for lazy injection. The same `@Inject` and
`@Named` annotations we defined above are actually from JSR-330 (`javax.inject`) which Guice
co-created. Guice's `Injector.getInstance()` is semantically identical to our `getBean()`.

### Spring Framework — BeanDefinition + BeanFactory

Spring's `BeanFactory` maps `beanName → BeanDefinition` (not `Class → Class` but name → metadata).
`BeanDefinition` stores: class name, scope, constructor args, property values, `initMethod`
(equivalent to `@PostConstruct`). Spring's `DefaultListableBeanFactory.doCreateBean()` follows
the same 5-step flow as our `createInstance()`: find constructor → resolve args → `newInstance()`
→ populate properties → `afterPropertiesSet()`. Spring additionally: creates CGLIB proxies for
AOP, handles `@Transactional`, manages `@Scope("prototype")` (new instance on each `getBean()`).

### Dagger 2 — compile-time code generation

Dagger 2 generates injection code at compile time (via annotation processor) instead of using
reflection. Result: zero reflection overhead at runtime; compile-time validation of the dependency
graph (missing bindings fail the build, not at startup). The generated code is a series of
`new PaymentService(new StripeGateway(new HttpClient()))` calls — as fast as hand-written code.
Dagger is preferred for Android (reflection is expensive on Dalvik/ART) and performance-critical
JVM services. Tradeoff: build-time code generation adds complexity; runtime flexibility (dynamic
bindings) is limited.

### Micronaut — ahead-of-time compilation for DI

Micronaut processes `@Inject` annotations at compile time and generates `BeanDefinition` classes
that contain the wiring logic as bytecode (not reflection). Startup time: ~50ms vs Spring Boot's
~3s for a similar application, because no classpath scanning or reflection occurs at startup.
This is the same principle as Dagger but for server-side Spring-style development. GraalVM native
image compatibility is much better than Spring (no reflection), enabling ~10ms startup times.

### Hilt (Android) — Dagger specialised for Android components

Hilt is built on Dagger 2 and provides Android-specific scopes (`@ActivityScoped`,
`@ViewModelScoped`) and integration with the Android component lifecycle. It solves the Dagger
boilerplate problem for Android by auto-generating the `@Component` wiring. The underlying
principle is identical to our container: register bindings → resolve graph → cache singletons.

---

## 7. Technologies & Tools

| Tool | Approach | Reflection | Compile-time check | Use case |
|------|----------|-----------|-------------------|---------|
| Our mini-container | Reflection at runtime | Yes | No | Learning, simple apps |
| Google Guice | Reflection at runtime | Yes | Partial (module validation) | Server apps, testing flexibility |
| Spring Framework | Reflection + CGLIB | Yes | No (runtime failures) | Full enterprise stack |
| Dagger 2 | Compile-time codegen | No | Yes (build fails) | Android, performance-critical |
| Micronaut DI | Compile-time metadata | No | Yes | Cloud-native microservices |
| Quarkus Arc | Compile-time CDI | Minimal | Yes | Quarkus apps, native image |

---

## 8. Operational Playbook

**(a) Runbook: `CircularDependencyException` at startup**
- **Symptom**: `CircularDependencyException: A → B → C → A` during application startup
- **Diagnosis**: Read the cycle path in the exception message; identify which dependency introduces the cycle
- **Resolution options**: (1) Extract an interface that breaks the cycle — A depends on `CInterface`, C depends on `AInterface`; (2) use `Provider<C>` (lazy injection) to break the instantiation loop; (3) restructure so A and C share a common dependency instead of forming a cycle

**(b) Runbook: `No binding found for Interface.class` at startup**
- **Symptom**: `IllegalStateException: No binding found for com.example.PaymentGateway`
- **Diagnosis**: Check `ContainerBuilder` registrations — did you bind the interface, or only the implementation? Check for typo in `@Named` qualifier
- **Resolution**: Add `bind(PaymentGateway.class).to(StripeGateway.class)` in the module configuration

**(c) Runbook: `@PostConstruct` method throwing exception**
- **Symptom**: `RuntimeException: @PostConstruct failed on StripeGateway.init`
- **Diagnosis**: The exception from `init()` is wrapped; check the cause for the actual error (DB connection refused, missing config)
- **Resolution**: Fix the init logic; or move the init to a `StartupValidator` component that can be separately tested

**(d) Runbook: Wrong implementation resolved for an interface**
- **Symptom**: `ClassCastException` or wrong logic; debug shows the wrong implementation is being used
- **Diagnosis**: Multiple bindings for the same interface; missing `@Named` qualifier means the last binding wins
- **Resolution**: Add `@Named` qualifiers to all bindings for the same interface; update `@Inject` constructor parameters to include `@Named`

---

## 9. Common Pitfalls & War Stories

### Pitfall 1 — Mutable singletons shared across threads

**Incident (2020, healthcare startup):** The mini-container created one instance of `ReportGenerator`
(mutable state: `StringBuilder currentReport`). Multiple request threads shared the singleton
and wrote to `currentReport` concurrently — producing corrupted reports that combined data from
different patients. **Fix:** Either make singletons truly stateless (no mutable fields), or use
scope = prototype (new instance per `getBean()` call) for stateful components. **Cost:** 12
corrupted medical reports; HIPAA compliance review.

### Pitfall 2 — Circular dependency handled by Spring but not by a simple container

A team migrated from a custom container to Spring and discovered Spring's circular dependency
handling (via `ObjectProvider` / setter injection) was masking a real design flaw. The original
container's `CircularDependencyException` was the correct signal — two services were too tightly
coupled. Spring's `@Lazy` workaround hid the smell. **Lesson:** Fail-fast on circular dependencies
in a DI container; don't add workarounds that mask architectural problems.

### Pitfall 3 — Reflection performance at startup for 2,000-class graphs

A financial platform with 2,000 Spring beans experienced 8-second startup time, largely from
reflection-based `BeanDefinition` scanning and CGLIB proxy generation. Migration to Spring Native
(Ahead-of-Time compilation) reduced startup to 400ms. **Quantified impact:** In Kubernetes
rolling deploys, 8s startup meant 8s of reduced capacity per pod restart; 50 pods restarting
in parallel = 400s of degraded capacity during each deploy. With 400ms startup: 20s total.

### Pitfall 4 — Singleton scope in a multi-tenant environment

A singleton `TenantContextHolder` stored the current tenant ID in a field. In a multitenant
app, all requests shared the same `TenantContextHolder` instance — tenant A's request would
overwrite tenant B's context. **Fix:** Use `ThreadLocal<String>` inside the singleton for
per-thread tenant context, always cleaned up in a `finally` block. Or use `@RequestScope` beans
(one per HTTP request). **Cost:** ~500 requests saw wrong tenant data; security incident.

### Pitfall 5 — Missing `@PostConstruct` on dependent beans

A `CacheManager` bean had a `@PostConstruct` that loaded the initial cache from DB. A `ProductService`
(dependent on `CacheManager`) called `cache.get()` in ITS `@PostConstruct` — which ran before
`CacheManager`'s `@PostConstruct`. Result: `NullPointerException` on `cache.get()`. **Fix:**
DI containers call `@PostConstruct` in dependency order (dependency first, then dependent).
Our `DependencyResolver` achieves this because `createInstance(ProductService)` first resolves
and fully creates `CacheManager` (including its `@PostConstruct`) before creating `ProductService`.

---

## 10. Capacity Planning

**Container build time scaling:**
```
T_build = N_classes × T_reflection + N_edges × T_instantiation

where:
  T_reflection ≈ 200 ns per class (getDeclaredConstructors)
  T_instantiation ≈ 1-5 µs per class (newInstance + init)
  N_classes = number of registered classes
  N_edges = total constructor parameter count across all classes

For N=500, avg_params=3:
  T_build = 500 × 200ns + 1500 × 3µs = 100µs + 4.5ms ≈ 5ms

Spring Boot adds:
  Classpath scanning: +50–500ms (scans all JARs)
  CGLIB proxy generation: +50–200ms (transactional + AOP beans)
  Autoconfiguration evaluation: +100–300ms
  Total Spring Boot startup: 2–8s for a typical app

Native compilation (GraalVM):
  All reflection resolved at build time → startup: 50–200ms
```

---

## 11. Interview Discussion Points

**Q1. What is dependency injection and how does it differ from service locator?**
Dependency injection is a design pattern where a component's dependencies are provided
(injected) by an external entity rather than the component fetching them itself. The component
declares what it needs (via constructor parameters); the container provides the instances.
The service locator pattern has components pull their dependencies from a central registry
via `ServiceLocator.get(PaymentService.class)`. The key difference: with DI, dependencies are
explicit (visible in the constructor signature) and the component has no knowledge of the
container — it works identically whether constructed by a container or in a test with mocks.
With service locator, the dependency on the locator is hidden and testing requires mocking or
configuring the locator, coupling the component to the framework.

**Q2. How does a DI container detect circular dependencies?**
During recursive construction, maintain a `Set<BeanKey>` of beans currently being resolved
(the "in-progress" set). Before resolving a bean, check if its key is already in the set — if
so, a circular dependency exists. Use a `LinkedHashSet` to preserve insertion order for a
readable error message showing the full cycle path (e.g., `A → B → C → A`). Add the key to
the set before resolving, remove it in a `finally` block after resolution (whether successful
or failed). This approach uses O(depth) memory (the call stack depth) and O(1) per-level check.
Spring uses a similar `singletonsCurrentlyInCreation` set in `DefaultSingletonBeanRegistry`.

**Q3. Why is constructor injection preferred over field injection?**
Constructor injection allows fields to be `final` (guaranteeing immutability after construction),
makes all dependencies explicit in the class API (visible to callers and IDE tools), and enables
direct instantiation in unit tests without a DI framework (`new OrderService(mockPayment, mockInventory)`).
Field injection (`@Autowired private PaymentService payment`) requires reflection to set a private field —
the class cannot be instantiated without a framework, making unit tests require a full Spring
context or mocking frameworks that use reflection. Field injection also hides the dependency
count (Effective Java Item 5: prefer dependency injection) — a class with 10 field-injected
dependencies looks clean but would obviously need refactoring if those 10 dependencies were
visible constructor parameters.

**Q4. What is the difference between singleton and prototype scope in a DI container?**
Singleton scope means the container creates exactly one instance per binding and returns the
same instance on every `getBean()` call — the container owns the object's lifecycle.
Prototype scope (or "transient" in other frameworks) creates a new instance on every `getBean()`
call — the caller owns the lifecycle. Spring Boot defaults to singleton for all beans. Use prototype
for: stateful components that must not be shared across threads (request-specific context,
builders), objects with mutable state that differs per use site. The pitfall: injecting a
prototype bean into a singleton — the singleton gets one instance at construction time and reuses
it forever, defeating the prototype purpose. Fix: inject `ObjectProvider<PrototypeBean>` or
`Supplier<PrototypeBean>` so the singleton calls `provider.getObject()` on each use.

**Q5. How does Dagger 2's compile-time approach differ from runtime reflection DI?**
Dagger 2 processes `@Inject` annotations at compile time via a Java annotation processor.
It generates Java source files that contain the dependency resolution logic as explicit
`new` statements: `new OrderService(new StripeGateway(new HttpClient()), new InventoryService())`.
At runtime, no reflection occurs — the generated code is pure Java with full compiler type
checking. Advantages: 10–100× faster startup (no reflection), compile-time validation (missing
bindings fail the build), GraalVM native image compatibility (no reflection metadata needed).
Disadvantages: cannot bind dynamically at runtime (all bindings must be known at compile time),
more complex setup (annotation processor + component generation). Runtime reflection DI
(Guice, Spring) is more flexible (e.g., reading bindings from a database) but pays reflection
cost at startup.

**Q6. What are the lifecycle hooks in a DI container and when are they called?**
Standard lifecycle hooks: `@PostConstruct` (called after the object is fully constructed and
all dependencies are injected — useful for warming caches, validating configuration, opening
connections), `@PreDestroy` (called before the container shuts down — useful for closing connections,
flushing buffers, deregistering from service registries). In our implementation, `@PostConstruct`
is called immediately after `createInstance()` completes, within the `resolve()` method — before
the instance is added to the singleton cache. This means dependent beans' `@PostConstruct`
methods can safely call methods on their dependencies (which are fully initialised by this point).
Spring's ordering: dependencies' `@PostConstruct` runs before dependents', because dependencies
are resolved first in `addWorker()`.

**Q7. How would you add support for `@Scope("request")` to this container?**
Request scope requires a per-request context. Implement: (1) a `ThreadLocal<Map<BeanKey, Object>>`
that holds the current request's scoped instances, (2) a `RequestScope` class with `begin()`
(initialises the ThreadLocal map) and `end()` (clears it — must be in a `finally` block), (3)
modify `resolve()` to check the request scope map first (for `REQUEST`-scoped bindings) before
the singleton cache, (4) a Servlet filter (or Spring interceptor) that calls `requestScope.begin()`
at the start of each HTTP request and `requestScope.end()` in a `finally` block. The challenge
is ensuring `end()` is always called — a leaked request scope accumulates objects that are never
garbage collected until the thread is retired.

**Q8. How does Spring handle circular dependencies for singleton beans?**
Spring allows certain circular dependencies among singletons by using a three-level cache:
(1) `singletonObjects` (fully created), (2) `earlySingletonObjects` (created but not yet populated),
(3) `singletonFactories` (lambdas that can produce an early reference). When constructing bean A
that needs bean B (which needs A back), Spring puts A's `ObjectFactory` in level 3 during
construction. When B requests A, Spring finds the factory in level 3, invokes it to get an
"early" (not fully constructed) reference to A, and gives it to B. After A finishes construction,
it is promoted to level 1. This only works for setter injection and `@Autowired` field injection —
NOT for constructor injection, because the constructor needs the fully constructed argument.
Constructor circular dependencies always fail with `BeanCurrentlyInCreationException`.

**Q9. What is a `BeanPostProcessor` and how does Spring use it for AOP?**
`BeanPostProcessor` (BPP) is a callback interface that Spring calls after each bean is created
but before it is stored in the singleton cache. Spring's AOP infrastructure implements
`AnnotationAwareAspectJAutoProxyCreator` as a BPP: it checks whether the new bean matches any
pointcut; if so, it wraps the bean in a CGLIB or JDK proxy and returns the proxy instead of the
original bean. The caller then holds the proxy; all method calls go through the proxy's
`MethodInterceptor` chain before reaching the real bean. Our mini-container could support this:
after `createInstance()` but before caching, iterate over registered `BeanPostProcessor`
instances and allow each to return a replacement object. This is the exact mechanism
Spring uses for `@Transactional`, `@Cacheable`, and `@Async` proxy creation.

**Q10. How would you add support for prototype-scoped beans that can be injected into singletons?**
The standard solution is to inject `Supplier<T>` or `Provider<T>` (JSR-330 / Guice style)
instead of `T` directly. The container detects when a parameter type is `Supplier<X>` or
`Provider<X>`, resolves the inner type `X` as a factory lambda, and injects the lambda:
`() -> resolve(X.class, name)`. The singleton calls `paymentGatewaySupplier.get()` each time
it needs a fresh instance. This avoids the "prototype injected into singleton gets stale" problem
because the singleton holds a `Supplier`, not a specific instance. Spring supports this via
`ObjectProvider<T>` injection (Spring 4.3+) which also adds `ifAvailable()` and `ifUnique()`
null-safety methods.

---

## Cross-Cutting References

- [cross_cutting/concurrency_memory_visibility_primitives.md](cross_cutting/concurrency_memory_visibility_primitives.md) — thread-safety of singleton cache, ThreadLocal for request scope
- [../../spring/ioc_container/README.md](../../spring/ioc_container/README.md) — Spring's BeanFactory, ApplicationContext, BeanDefinition
- [../../spring/spring_proxies/README.md](../../spring/spring_proxies/README.md) — CGLIB and JDK proxy wrapping after container creates instances
- [../../lld/creational/README.md](../../lld/creational/README.md) — Factory pattern used in binding resolution; Singleton pattern as a scope
