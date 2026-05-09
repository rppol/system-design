# Decorator vs Proxy

## Overview

Both Decorator and Proxy wrap a target object and implement the same interface. The structural similarity is almost perfect. The difference is in **intent** and **who manages the wrapped object's lifecycle**.

---

## Intuition

> **One-line analogy**: Decorator is like adding toppings to a pizza (you're enriching what's already there); Proxy is like a receptionist who decides whether you even get to see the pizza chef.

**Mental model**: Both wrap the same interface. Decorator adds new behavior on top of the real object — you always reach the real object, but with something extra attached. Proxy controls access to the real object — it might delay creation, check permissions, cache results, or log — and the caller may not even know a proxy is involved.

**Why it matters**: Decorator and Proxy are structurally nearly identical. The distinction is intent: enrichment vs access control. Real examples: Java I/O streams are Decorators; Spring's `@Transactional` proxies are Proxies.

**Key insight**: Ask "does this wrapper add behavior or guard access?" — add behavior → Decorator. Guard/intercept access → Proxy.

---

## Side-by-Side UML

```
DECORATOR                             PROXY
──────────────────────────────        ──────────────────────────────
<<interface>> Component               <<interface>> Subject
└── operation()                       └── request()

ConcreteComponent                     RealSubject
└── operation()  <── wraps            └── request()   <── proxied

Decorator (abstract)                  Proxy
├── wrappee: Component                ├── realSubject: RealSubject
└── operation()                       └── request()
    calls wrappee.operation()             pre-processing
                                          realSubject.request()
ConcreteDecoratorA                        post-processing
├── Decorator fields
└── operation()                       (Proxy often creates/controls
    super.operation()                  the RealSubject itself)
    add extra behavior

ConcreteDecoratorB
└── operation()
    add different extra behavior
```

---

## Key Differences Table

| Dimension | Decorator | Proxy |
|-----------|-----------|-------|
| **Primary intent** | Add or enhance behavior dynamically | Control access to an object |
| **Who creates the wrapped object** | Client creates the component, wraps externally | Proxy often creates/manages the real subject |
| **Stacking** | Designed to stack multiple layers | Typically one proxy around one subject |
| **Client awareness** | Client assembles the decoration chain | Client usually unaware a proxy exists |
| **Transparency** | Client may know about decorators | Client should be fully transparent to proxy |
| **Lifecycle control** | No — just adds behavior | Often yes — lazy init, pooling, caching |
| **Typical variations** | One type: add behavior | Many types: remote, virtual, protection, logging |
| **Change to subject's behavior** | Extends/adds behavior | Usually does not change core behavior |

---

## Common Confusion Points

1. **Identical structure** — The UML is nearly the same. The tell is intent and who creates the inner object.
2. **Proxy doesn't add behavior** (usually) — A logging proxy adds logging infrastructure, but doesn't change what the method does. A Decorator fundamentally adds new behavior to the operation.
3. **Decorator is additive and stackable** — You can wrap a coffee in milk, then whipped cream, then caramel. Each adds to the previous. Proxies are not typically stacked.
4. **Proxy may be invisible** — The client code for `subject.request()` should be identical whether using a proxy or not. With Decorator, the client often explicitly wraps: `new MilkDecorator(new Coffee())`.
5. **Virtual Proxy vs Decorator**: A virtual proxy delays object creation. A decorator adds behavior immediately upon creation.

---

## Proxy Variants

| Proxy Type | Purpose | Example |
|------------|---------|---------|
| Virtual Proxy | Lazy initialization, defer expensive creation | Loading image placeholders |
| Remote Proxy | Represents object in another address space | RMI stub, gRPC client stub |
| Protection Proxy | Access control | Role-based method guards |
| Caching Proxy | Cache results of expensive operations | Database query cache |
| Logging Proxy | Audit trail without modifying target | AOP-style method logging |
| Smart Reference | Extra actions on access/dereference | Reference counting, null checks |

---

## When to Use Which

### Use Decorator when:
- You want to add responsibilities to individual objects without affecting other objects of the same class
- You want to add behavior that can be combined and stacked in different ways
- Subclassing would lead to a class explosion for every combination of features
- You want to be able to add and remove responsibilities at runtime

### Use Proxy when:
- You need lazy initialization — delay expensive object creation until first use
- You need access control — restrict who can use the object
- You need a local representative for a remote object
- You want to log/audit method calls transparently
- You need smart reference behavior (e.g., caching expensive operations)

---

## Code Examples

### Decorator — Coffee customization

```java
// Component interface
interface Coffee {
    String getDescription();
    double getCost();
}

// Concrete component
class SimpleCoffee implements Coffee {
    public String getDescription() { return "Simple coffee"; }
    public double getCost()        { return 1.00; }
}

// Abstract Decorator
abstract class CoffeeDecorator implements Coffee {
    protected final Coffee decoratedCoffee;

    public CoffeeDecorator(Coffee coffee) {
        this.decoratedCoffee = coffee;
    }

    public String getDescription() { return decoratedCoffee.getDescription(); }
    public double getCost()        { return decoratedCoffee.getCost(); }
}

// Concrete Decorators — each adds something
class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) { super(coffee); }

    @Override
    public String getDescription() { return decoratedCoffee.getDescription() + ", milk"; }

    @Override
    public double getCost() { return decoratedCoffee.getCost() + 0.25; }
}

class WhipDecorator extends CoffeeDecorator {
    public WhipDecorator(Coffee coffee) { super(coffee); }

    @Override
    public String getDescription() { return decoratedCoffee.getDescription() + ", whip"; }

    @Override
    public double getCost() { return decoratedCoffee.getCost() + 0.50; }
}

class CaramelDecorator extends CoffeeDecorator {
    public CaramelDecorator(Coffee coffee) { super(coffee); }

    @Override
    public String getDescription() { return decoratedCoffee.getDescription() + ", caramel"; }

    @Override
    public double getCost() { return decoratedCoffee.getCost() + 0.75; }
}

// Client stacks decorators — order matters and is controlled by client
Coffee order = new SimpleCoffee();
System.out.println(order.getDescription() + " $" + order.getCost());

order = new MilkDecorator(order);
order = new WhipDecorator(order);
order = new CaramelDecorator(order);
System.out.println(order.getDescription() + " $" + order.getCost());
// "Simple coffee, milk, whip, caramel $2.50"

// Different combination — mix and match freely
Coffee latte = new MilkDecorator(new MilkDecorator(new SimpleCoffee()));
System.out.println(latte.getDescription() + " $" + latte.getCost());
// "Simple coffee, milk, milk $1.50"
```

---

### Proxy — Virtual Proxy for expensive resource

```java
// Subject interface
interface Image {
    void display();
}

// Real subject — expensive to create
class HighResolutionImage implements Image {
    private final String filename;
    private byte[] imageData;

    public HighResolutionImage(String filename) {
        this.filename = filename;
        loadFromDisk();  // expensive operation
    }

    private void loadFromDisk() {
        System.out.println("Loading " + filename + " from disk...");
        // Simulate expensive loading
        this.imageData = new byte[1024 * 1024]; // 1MB
    }

    @Override
    public void display() {
        System.out.println("Displaying: " + filename);
    }
}

// Virtual Proxy — delays creation until first use
class ImageProxy implements Image {
    private final String filename;
    private HighResolutionImage realImage;  // null until needed

    public ImageProxy(String filename) {
        this.filename = filename;
        // Note: does NOT create HighResolutionImage here
    }

    @Override
    public void display() {
        if (realImage == null) {
            realImage = new HighResolutionImage(filename);  // lazy init
        }
        realImage.display();
    }
}

// Protection Proxy — adds access control
class SecureImageProxy implements Image {
    private final Image target;
    private final String userRole;

    public SecureImageProxy(Image target, String userRole) {
        this.target   = target;
        this.userRole = userRole;
    }

    @Override
    public void display() {
        if (!userRole.equals("ADMIN") && !userRole.equals("VIEWER")) {
            throw new SecurityException("Access denied for role: " + userRole);
        }
        target.display();
    }
}

// Logging Proxy — transparent audit trail
class LoggingImageProxy implements Image {
    private final Image target;

    public LoggingImageProxy(Image target) { this.target = target; }

    @Override
    public void display() {
        System.out.println("[LOG] display() called at " + System.currentTimeMillis());
        target.display();
        System.out.println("[LOG] display() completed");
    }
}

// Usage — client code is identical whether proxy or real object
Image img1 = new ImageProxy("photo.jpg");
Image img2 = new ImageProxy("landscape.png");

// Only photo.jpg is loaded — landscape.png proxy never triggers loading
img1.display();
// Loading photo.jpg from disk...
// Displaying: photo.jpg

img1.display();
// Displaying: photo.jpg  (no reload — already in memory)

// Proxy chain (less common but possible)
Image secured = new SecureImageProxy(
    new LoggingImageProxy(new ImageProxy("secret.jpg")),
    "ADMIN"
);
secured.display();
```

---

## Java Standard Library Examples

| Pattern | Java Example |
|---------|-------------|
| Decorator | `java.io.BufferedReader(new FileReader(...))` — wraps with buffering |
| Decorator | `Collections.unmodifiableList(list)` — wraps with immutability |
| Decorator | `Collections.synchronizedList(list)` — wraps with thread-safety |
| Proxy | `java.lang.reflect.Proxy` — dynamic proxy for interfaces |
| Proxy | Spring AOP `@Transactional` — transparent proxy around service beans |
| Proxy | Hibernate lazy-loaded entities — virtual proxy for DB records |

---

## Interview Answer Template

**Q: Both Decorator and Proxy wrap an object with the same interface. What's the difference?**

> The structural similarity is real — they're nearly identical in UML. The difference is intent.
>
> Decorator's job is to **add or enhance behavior** — it extends what the wrapped object does. You can stack multiple decorators. The client often explicitly creates the decoration chain. A coffee with milk and whip is a concrete example.
>
> Proxy's job is to **control access** — it acts as a gatekeeper or stand-in. It typically doesn't change what the method does; it decides whether and when to call it. Proxies are usually transparent to the client. Examples: lazy initialization, access control, caching, remote access.
>
> The diagnostic question: "Is the pattern adding new behavior, or managing *how* the original behavior is invoked?" Adding behavior = Decorator. Managing invocation = Proxy.
