# Adapter vs Bridge vs Facade
## Overview

All three patterns involve wrapping or composing other objects, but they serve fundamentally different purposes at different points in the software lifecycle.

---

## Intuition

> **One-line analogy**: Adapter is a travel plug converter (retrofitting incompatible things); Bridge is a modular power strip (abstraction and plug type evolve independently); Facade is a power strip with an on/off switch for the whole room (simplified control over complexity).

**Mental model**: Adapter fixes incompatibility after the fact — you have an existing class with the wrong interface. Bridge is planned from the start — you know both abstraction and implementation will vary independently, so you separate them by design. Facade makes a complex subsystem simple — no incompatibility is being fixed, just complexity is being hidden.

**Why it matters**: All three involve wrapping, but at different lifecycle points and for different reasons. Using Adapter when you should use Bridge leads to an explosion of adapter subclasses; using Facade when you should use Adapter fails to actually fix the interface mismatch.

**Key insight**: Adapter = fix existing incompatibility (retrofitting). Bridge = plan for independent variation (forward-looking). Facade = simplify complexity for a specific client (usability).

---

## Side-by-Side UML

```mermaid
classDiagram
    direction LR

    namespace AdapterPattern {
        class AdapterClient
        class Target {
            <<interface>>
            +request()
        }
        class Adapter {
            -adaptee : Adaptee
            +request()
        }
        class Adaptee {
            +specificRequest()
        }
    }

    namespace BridgePattern {
        class Abstraction {
            <<abstract>>
            #impl : Implementor
            +operation()
        }
        class RefinedAbstraction {
            +operation()
        }
        class Implementor {
            <<interface>>
            +operationImpl()
        }
        class ConcreteImplementorA
        class ConcreteImplementorB
    }

    namespace FacadePattern {
        class FacadeClient
        class Facade {
            -sub1 : Sub1
            -sub2 : Sub2
            -sub3 : Sub3
            +subsystemOperation()
        }
        class Sub1
        class Sub2
        class Sub3
    }

    AdapterClient --> Target : uses
    Target <|.. Adapter
    Adapter --> Adaptee : translates to

    Abstraction o-- Implementor : bridge
    Abstraction <|-- RefinedAbstraction
    Implementor <|.. ConcreteImplementorA
    Implementor <|.. ConcreteImplementorB

    FacadeClient --> Facade : uses
    Facade o-- Sub1
    Facade o-- Sub2
    Facade o-- Sub3
```

Three different shapes for the same "wrap something" idea. Adapter converts one incompatible interface after the fact (`Adapter` translates `Target.request()` into `Adaptee.specificRequest()`). Bridge plans for independent variation upfront — `Abstraction` and `Implementor` are two hierarchies joined by a swappable, shared reference (the "bridge"). Facade exposes one simplified entry point (`subsystemOperation()`) over several subsystem classes that remain independently usable (aggregation, not ownership — see the "Subsystem still accessible?" row below).

---

## Key Differences Table

| Dimension | Adapter | Bridge | Facade |
|-----------|---------|--------|--------|
| **Intent** | Make incompatible interfaces work together | Separate abstraction from implementation | Simplify a complex subsystem |
| **Timing** | Applied AFTER the fact (retrofit) | Designed UPFRONT | Applied to existing subsystem |
| **Number of objects wrapped** | One incompatible object | One implementation hierarchy | Multiple subsystem objects |
| **Interface direction** | Converts old interface to expected | Defines two parallel hierarchies | Creates a new simplified interface |
| **Hides complexity?** | No — exposes same complexity | No — exposes two dimensions | Yes — hides subsystem detail |
| **Subsystem still accessible?** | N/A | N/A | Yes — Facade doesn't lock out direct access |
| **Inheritance vs composition** | Either (class adapter or object adapter) | Composition | Composition |
| **Changes original interface** | Yes (translates it) | No — defines new parallel hierarchy | Provides additional simpler interface |

---

## Common Confusion Points

1. **Adapter vs Facade**: Adapter adapts ONE object to an interface. Facade simplifies access to MANY objects. Adapter translates; Facade unifies.
2. **Adapter vs Bridge timing**: The most important distinction. Adapter is a retrofit — you apply it when two things weren't designed to work together. Bridge is designed upfront to allow independent variation of two dimensions.
3. **Bridge vs Strategy**: Bridge separates an abstraction hierarchy from an implementation hierarchy (structural). Strategy swaps algorithms at runtime (behavioral). Bridge is about decomposing structure; Strategy is about selecting behavior.
4. **Facade as a wrapper**: Facade is not just "wrapping" — it is a simplification layer. The subsystem objects still exist independently and can be used without the Facade.

---

## When to Use Which

### Use Adapter when:
- You want to use an existing class but its interface doesn't match what you need
- You have a legacy component you cannot modify
- You are integrating third-party libraries with different interfaces
- You want to create a reusable class that works with classes that don't have compatible interfaces

### Use Bridge when:
- You anticipate that both an abstraction and its implementation will need to vary independently
- You want to avoid a permanent binding between abstraction and implementation
- Changes in implementation should not impact the client code
- You want to avoid an explosion of subclasses from combining two dimensions

### Use Facade when:
- You want to provide a simple interface to a complex subsystem
- There are many dependencies between clients and implementation classes
- You want to layer your subsystems (each layer has a facade for the one below it)
- You want to reduce coupling between clients and subsystem internals

---

## Code Examples

### Adapter — Plugging in a legacy logger

```java
// What our system expects
interface Logger {
    void log(String level, String message);
}

// Legacy logger we cannot modify (wrong interface)
class LegacyFileLogger {
    public void writeToFile(String content) {
        System.out.println("[FILE] " + content);
    }
}

// Third-party logger (also wrong interface)
class ThirdPartyCloudLogger {
    public void send(String severity, String msg, long timestamp) {
        System.out.println("[CLOUD:" + severity + "] " + msg);
    }
}

// Adapter for legacy logger
class LegacyLoggerAdapter implements Logger {
    private final LegacyFileLogger legacyLogger;

    public LegacyLoggerAdapter(LegacyFileLogger legacy) {
        this.legacyLogger = legacy;
    }

    @Override
    public void log(String level, String message) {
        // Translate from expected interface to legacy interface
        legacyLogger.writeToFile("[" + level + "] " + message);
    }
}

// Adapter for third-party logger
class CloudLoggerAdapter implements Logger {
    private final ThirdPartyCloudLogger cloudLogger;

    public CloudLoggerAdapter(ThirdPartyCloudLogger cloud) {
        this.cloudLogger = cloud;
    }

    @Override
    public void log(String level, String message) {
        cloudLogger.send(level.toLowerCase(), message, System.currentTimeMillis());
    }
}

// Application uses only the Logger interface — doesn't know about adapters
class Application {
    private final Logger logger;

    public Application(Logger logger) { this.logger = logger; }

    public void doWork() {
        logger.log("INFO",  "Starting work...");
        logger.log("ERROR", "Something failed");
    }
}

// Wire-up
Application app1 = new Application(new LegacyLoggerAdapter(new LegacyFileLogger()));
Application app2 = new Application(new CloudLoggerAdapter(new ThirdPartyCloudLogger()));
```

```mermaid
sequenceDiagram
    participant App as Application
    participant LA as LegacyLoggerAdapter
    participant LF as LegacyFileLogger
    participant CA as CloudLoggerAdapter
    participant CL as ThirdPartyCloudLogger

    Note over App,LF: app1 = new Application(new LegacyLoggerAdapter(new LegacyFileLogger()))
    App->>LA: log("ERROR", "Something failed")
    LA->>LF: writeToFile("[ERROR] Something failed")

    Note over App,CL: app2 = new Application(new CloudLoggerAdapter(new ThirdPartyCloudLogger()))
    App->>CA: log("ERROR", "Something failed")
    CA->>CL: send("error", "Something failed", timestamp)
```

`Application` calls the exact same `Logger.log(level, message)` method in both wire-ups — only the adapter changes. `LegacyLoggerAdapter` translates that call into `writeToFile(...)`; `CloudLoggerAdapter` translates the identical call into `send(...)`. `Application` never sees `LegacyFileLogger` or `ThirdPartyCloudLogger` directly.

---

### Bridge — Shapes with rendering implementations

```java
// Implementation hierarchy (varies independently)
interface Renderer {
    void renderCircle(double radius);
    void renderRectangle(double w, double h);
}

class VectorRenderer implements Renderer {
    public void renderCircle(double r)          { System.out.println("SVG Circle r=" + r); }
    public void renderRectangle(double w, double h) { System.out.println("SVG Rect " + w + "x" + h); }
}

class RasterRenderer implements Renderer {
    public void renderCircle(double r)          { System.out.println("Raster Circle r=" + r); }
    public void renderRectangle(double w, double h) { System.out.println("Raster Rect " + w + "x" + h); }
}

// Abstraction hierarchy (varies independently)
abstract class Shape {
    protected Renderer renderer;   // bridge

    public Shape(Renderer renderer) { this.renderer = renderer; }

    public abstract void draw();
    public abstract void resize(double factor);
}

class Circle extends Shape {
    private double radius;

    public Circle(Renderer renderer, double radius) {
        super(renderer);
        this.radius = radius;
    }

    @Override
    public void draw()               { renderer.renderCircle(radius); }

    @Override
    public void resize(double factor) { radius *= factor; }
}

class Rectangle extends Shape {
    private double width, height;

    public Rectangle(Renderer renderer, double w, double h) {
        super(renderer);
        this.width  = w;
        this.height = h;
    }

    @Override
    public void draw()               { renderer.renderRectangle(width, height); }

    @Override
    public void resize(double factor) { width *= factor; height *= factor; }
}

// Without Bridge: m shapes * n renderers subclasses (2 * 2 = 4 here)
// With Bridge: m + n classes (2 + 2 = 4 here) — grows additively, not multiplicatively
Shape c1 = new Circle(new VectorRenderer(), 5.0);
Shape c2 = new Circle(new RasterRenderer(), 5.0);
Shape r1 = new Rectangle(new VectorRenderer(), 4.0, 3.0);

c1.draw(); // SVG Circle r=5.0
c2.draw(); // Raster Circle r=5.0
r1.draw(); // SVG Rect 4.0x3.0
```

---

### Facade — Home theater system

```java
// Complex subsystem with many interdependent components
class Amplifier {
    public void on()                   { System.out.println("Amp on"); }
    public void setVolume(int level)   { System.out.println("Volume: " + level); }
    public void off()                  { System.out.println("Amp off"); }
}

class DVDPlayer {
    public void on()                   { System.out.println("DVD on"); }
    public void play(String movie)     { System.out.println("Playing: " + movie); }
    public void stop()                 { System.out.println("DVD stop"); }
    public void off()                  { System.out.println("DVD off"); }
}

class Projector {
    public void on()                   { System.out.println("Projector on"); }
    public void wideScreenMode()       { System.out.println("Widescreen mode"); }
    public void off()                  { System.out.println("Projector off"); }
}

class Lights {
    public void dim(int level)         { System.out.println("Lights dim to " + level + "%"); }
    public void on()                   { System.out.println("Lights on"); }
}

// Facade — provides simple interface to the complex subsystem
class HomeTheaterFacade {
    private final Amplifier amp;
    private final DVDPlayer dvd;
    private final Projector projector;
    private final Lights lights;

    public HomeTheaterFacade(Amplifier amp, DVDPlayer dvd,
                             Projector projector, Lights lights) {
        this.amp       = amp;
        this.dvd       = dvd;
        this.projector = projector;
        this.lights    = lights;
    }

    public void watchMovie(String movie) {
        System.out.println("--- Get ready to watch a movie ---");
        lights.dim(10);
        projector.on();
        projector.wideScreenMode();
        amp.on();
        amp.setVolume(20);
        dvd.on();
        dvd.play(movie);
    }

    public void endMovie() {
        System.out.println("--- Shutting down theater ---");
        dvd.stop();
        dvd.off();
        amp.off();
        projector.off();
        lights.on();
    }
}

// Client uses simple facade instead of managing 4 objects
HomeTheaterFacade theater = new HomeTheaterFacade(
    new Amplifier(), new DVDPlayer(), new Projector(), new Lights()
);
theater.watchMovie("Inception");
theater.endMovie();
// Subsystem objects still available for advanced users who need direct access
```

```mermaid
sequenceDiagram
    participant Client
    participant Facade as HomeTheaterFacade
    participant Lights
    participant Projector
    participant Amp as Amplifier
    participant DVD as DVDPlayer

    Client->>Facade: watchMovie("Inception")
    Facade->>Lights: dim(10)
    Facade->>Projector: on()
    Facade->>Projector: wideScreenMode()
    Facade->>Amp: on()
    Facade->>Amp: setVolume(20)
    Facade->>DVD: on()
    Facade->>DVD: play("Inception")
```

One `watchMovie("Inception")` call fans out to seven calls across four subsystem objects, in an order `Client` never has to know. `Client` still could call `Amplifier`, `Projector`, `DVDPlayer`, or `Lights` directly — the Facade doesn't lock out direct access — it just means most callers never need to.

---

## Interview Questions with Answers

**Q: What is the difference between Adapter and Facade?**
**Short:** Adapter translates one incompatible interface to match what a client expects, while Facade simplifies a whole subsystem.

> Adapter converts the interface of a single object so it matches what the client expects — it's a translator between two incompatible interfaces. Facade provides a simplified, unified interface to a whole subsystem made of multiple objects — it's a simplification layer. Adapter is about interface compatibility; Facade is about reducing complexity.

**Q: How does Bridge differ from Adapter?**
**Short:** Bridge is designed upfront to prevent a class explosion, while Adapter is a retrofit applied after two interfaces already exist.

> The fundamental difference is intent and timing. Adapter is a retrofit — you apply it when two things weren't originally designed to work together. Bridge is designed upfront when you anticipate two independent dimensions of variation (e.g., shapes and rendering strategies). Adapter solves a compatibility problem after the fact. Bridge prevents a class explosion problem before it occurs.

**Q: Can Adapter and Facade be used together?**
**Short:** Yes — a Facade commonly uses Adapter internally to integrate subsystems whose interfaces are incompatible.

> Yes, commonly. A Facade might use Adapter internally to integrate third-party subsystems that have incompatible interfaces into the simplified facade interface.

**Q: All three patterns involve wrapping — what row in the Key Differences table proves Facade is not just "wrapping" like Adapter?**
**Short:** The "Hides complexity?" row: Facade hides subsystem detail while Adapter exposes the same complexity it wraps.

The Key Differences table marks Adapter's "Hides complexity?" as "No — exposes same complexity," since an adapted object still does exactly what it did before, just through a translated interface. Facade's row instead reads "Yes — hides subsystem detail," because its entire purpose is reducing what the client needs to know about four or five collaborating subsystem objects. This is the concrete evidence that Facade is a simplification layer, not merely another wrapper.

**Q: Why does the Key Differences table say Facade's subsystem is "still accessible" while Adapter's and Bridge's rows read "N/A"?**
**Short:** Facade deliberately leaves the wrapped subsystem objects directly usable; Adapter and Bridge have no equivalent "bypass" concept.

The `HomeTheaterFacade` example notes explicitly that `Client` could still call `Amplifier`, `Projector`, `DVDPlayer`, or `Lights` directly — the Facade doesn't lock out direct access, it just means most callers never need it. Adapter and Bridge don't have an analogous "direct access path" because their wrapped/implementor objects aren't meant to be called independently of the pattern — an `Adaptee` called directly wouldn't match the client's expected interface at all, so the question doesn't apply to them the same way.

**Q: Per the Key Differences table, why does Adapter's "Changes original interface?" read Yes while Bridge's reads No?**
**Short:** Adapter translates an existing interface into a new one for the client; Bridge defines a brand-new parallel hierarchy instead.

Adapter's whole mechanism is converting the Adaptee's `specificRequest()` into whatever `Target.request()` the client expects — it is, by definition, changing how the original interface is exposed. Bridge doesn't touch or reinterpret any existing interface; it defines two entirely new, independent hierarchies (`Abstraction` and `Implementor`) that are connected by composition from the start. This distinction reflects the deeper Adapter-is-retrofit vs. Bridge-is-upfront-design difference the file emphasizes throughout.

**Q: Why would an interviewer say "you don't need Bridge" if only one dimension of your design actually varies?**
**Short:** Bridge's whole value is decoupling two independently varying dimensions; with only one dimension, it's pure overhead.

Bridge is explicitly for when "you anticipate that both an abstraction and its implementation will need to vary independently" — its structural cost is one abstraction hierarchy plus one implementor hierarchy wired at runtime. If only the abstraction side ever changes and the implementation is fixed, that second hierarchy buys nothing but indirection; a simple inheritance hierarchy or even no pattern at all would be simpler and just as correct. This is the same "pick the simpler pattern" principle from the master `pattern_comparisons.md` file applied specifically to Bridge.

**Q: How does the file's Shapes-with-rendering-implementations Bridge example demonstrate avoiding an O(m×n) subclass explosion?**
**Short:** Separating shape types from rendering implementations means m shapes plus n renderers instead of m×n combined subclasses.

Without Bridge, supporting `Circle`/`Square` shapes across `Vector`/`Raster` rendering would require four concrete classes (`VectorCircle`, `RasterCircle`, `VectorSquare`, `RasterSquare`), and every new shape or renderer multiplies that count. Bridge instead gives each shape an `Implementor` reference it can be constructed with — 2 shape classes plus 2 renderer classes cover all 4 combinations, and a third renderer only adds 1 new class instead of 2 more combined subclasses. This is the concrete mechanism behind "avoid an explosion of subclasses from combining two dimensions."

**Q: Why is "Adapter vs Bridge timing" called out as the most important distinction in the Common Confusion Points?**
**Short:** It's the distinction most likely to be misjudged in practice, since both can look structurally similar once built.

Once both patterns are implemented, an Adapter class and a Bridge's `RefinedAbstraction` can look superficially similar — both hold a reference to another object and delegate to it. The only way to correctly tell them apart in a design review is asking WHEN the decision was made: was this wrapping added to fix an existing mismatch (Adapter), or was it planned from the start to let two things vary independently (Bridge)? Getting this wrong in a live design either produces unnecessary Bridge machinery for a one-off compatibility fix, or an explosion of Adapter subclasses where Bridge was actually needed.

**Q: What does "Bridge vs Strategy" hinge on, according to the Common Confusion Points?**
**Short:** Bridge decomposes structure across two hierarchies; Strategy selects behavior at runtime — structural versus behavioral concerns.

Bridge is fundamentally about splitting a class's structure into two cooperating hierarchies (abstraction and implementation) that are each free to grow independently. Strategy is about selecting which algorithm executes at runtime, with no notion of two parallel class hierarchies — just one interface and interchangeable implementations. The file frames this as "Bridge is about decomposing structure; Strategy is about selecting behavior," which is the same intent-over-structure lens used throughout this directory.

**Q: Why can an Adapter be implemented as either a class adapter or an object adapter, per the Key Differences table?**
**Short:** A class adapter uses multiple inheritance to extend both Target and Adaptee; an object adapter composes the Adaptee instead.

The table's "Inheritance vs composition" row lists Adapter as "Either (class adapter or object adapter)," reflecting that in languages supporting multiple inheritance, an Adapter can directly extend both the `Target` interface and the `Adaptee` class. In Java, which lacks multiple class inheritance, the object adapter form — holding an `Adaptee` reference and implementing `Target` by delegation — is the only practical option, which is exactly the shape shown in this file's `Adapter — Plugging in a legacy logger` example.

**Q: How would you justify choosing Facade over directly exposing all four HomeTheater subsystem objects to client code?**
**Short:** Facade reduces the number of dependencies and calls a client must coordinate, from four objects and seven calls to one method.

Without a Facade, any client wanting to watch a movie would need direct references to `Amplifier`, `DVDPlayer`, `Projector`, and `Lights`, and would need to know the correct order to call `dim()`, `on()`, `wideScreenMode()`, `setVolume()`, and `play()` across all four — a coupling and sequencing burden repeated at every call site. `HomeTheaterFacade.watchMovie()` collapses that into one call, fanning out to the same seven operations internally in the order the Facade — not each caller — is responsible for getting right.

**Q: Per the "When to Use Which" guidance, when should you reach for Bridge specifically over Adapter even if both would technically compile?**
**Short:** Reach for Bridge when you anticipate future independent variation; reach for Adapter only to fix an existing, one-time mismatch.

Both patterns can be made to compile in overlapping situations, but Bridge's guidance is explicit about anticipation: "you anticipate that both an abstraction and its implementation will need to vary independently," and "you want to avoid a permanent binding between abstraction and implementation." If the mismatch you're solving is a one-off — a single legacy class that doesn't match your interface — Adapter is the right and simpler tool; reaching for Bridge there means paying its two-hierarchy structural cost for variation that will never actually happen.

**Q: What does "layering your subsystems, each with a facade for the one below it" mean in the "Use Facade when" guidance?**
**Short:** Complex systems can stack multiple Facades, where each layer's Facade simplifies the layer directly beneath it.

Rather than one giant Facade covering an entire application's subsystems, large systems often build a hierarchy of Facades — a `PaymentFacade` might sit atop `CardProcessor`, `FraudCheck`, and `LedgerService`, while a higher-level `CheckoutFacade` then depends only on `PaymentFacade` plus a couple of others, never reaching directly into `CardProcessor`. This layering keeps each Facade's job scoped to simplifying exactly one level of complexity rather than becoming a single sprawling entry point to everything.

**Q: Why does the `AdapterClient` in the Side-by-Side UML depend only on `Target`, never on `Adaptee` directly?**
**Short:** The client should be fully decoupled from the incompatible interface; only the Adapter itself knows about Adaptee.

The class diagram shows `AdapterClient --> Target : uses` and `Adapter --> Adaptee : translates to` as two separate edges — the client's dependency stops at the `Target` interface it expects, while the knowledge of `Adaptee`'s actual method names (`specificRequest()`) is confined entirely to the `Adapter` class. This isolation is what makes Adapter genuinely decoupling rather than just a convenience wrapper: if the legacy `Adaptee` class changes its method names, only the `Adapter` implementation needs updating, not every caller.

---

## Summary

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    P1{"These interfaces don't match<br/>and I can't change either one"} --> R1([ADAPTER])
    P2{"Two dimensions vary independently —<br/>don't want N×M subclasses"} --> R2([BRIDGE])
    P3{"This subsystem is too complex —<br/>need a simpler entry point"} --> R3([FACADE])

    class P1,P2,P3 mathOp
    class R1,R2,R3 train
```

Each problem statement maps to exactly one pattern. If a real design seems to need more than one of these three for the very same problem, revisit the Key Differences table above — genuine three-way overlap is rare.
