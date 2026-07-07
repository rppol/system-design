# Low-Level Design (LLD) — Master Overview

## What is Low-Level Design?

Low-Level Design (LLD) describes **how individual components are implemented** — the classes, interfaces, methods, and their interactions. Where HLD answers "what are the components?", LLD answers "how is each component built?".

LLD answers: *"What classes exist? What are their responsibilities? How do they interact?"*

---

## Intuition

> **One-line analogy**: LLD is the blueprint for a single building — HLD tells you where to put it in the city; LLD tells you where the walls, rooms, and wiring go inside.

**Mental model**: HLD stops at "we need an Order Service." LLD asks: What classes does Order Service contain? What interfaces do they implement? How does an `Order` know if it's valid? Who creates `Order` objects — a factory, a builder, or a direct constructor? What happens when order status changes — does something observe it? These micro-decisions, multiplied across hundreds of classes, determine whether the codebase is easy or hard to work with.

**Why it matters**: LLD skills directly impact your day-to-day code. A developer who understands SOLID, GoF patterns, and anti-patterns writes code that's easier to extend, test, and review. These skills compound: each well-designed class makes the next one easier.

**Key insight**: LLD interviews test your ability to identify entities, assign responsibilities, and apply patterns appropriately — not pattern memorization. The best LLD answers say "I'm using Strategy here because pricing changes independently of the order lifecycle" — not just "I'll use Strategy."

---

## HLD vs LLD

| Aspect | HLD | LLD |
|--------|-----|-----|
| Focus | System architecture | Class/module design |
| Audience | Architects, senior engineers | Developers |
| Output | Architecture diagrams, component specs | Class diagrams, sequence diagrams, code |
| Patterns | Sharding, replication, caching, CDN | Design patterns (GoF), SOLID |
| Questions | "What databases?", "How do we scale?" | "What classes?", "What methods?" |
| Abstraction | Services, systems | Classes, interfaces, objects |
| Timeframe | Months (architecture) | Days/weeks (implementation) |

---

## Category Navigation

Each category has a master index with a decision flowchart, pattern comparison table, and
category-level Q&As. Start with the category index before diving into individual pattern files.

| Category | Index File | Patterns / Topics |
|----------|-----------|-------------------|
| Design Principles | [design_principles/README.md](design_principles/README.md) | DRY, KISS, YAGNI, Law of Demeter, Composition over Inheritance, Program to Interface |
| Creational Patterns | [creational/README.md](creational/README.md) | Singleton, Factory Method, Abstract Factory, Builder, Prototype |
| Structural Patterns | [structural/README.md](structural/README.md) | Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy |
| Behavioral Patterns | [behavioral/README.md](behavioral/README.md) | Chain of Responsibility, Command, Interpreter, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor |
| Concurrency Patterns | [concurrency_patterns/README.md](concurrency_patterns/README.md) | Thread-Safe Singleton, Producer-Consumer, Read-Write Lock, Thread Pool |
| System Design Problems | [system_design_problems/README.md](system_design_problems/README.md) | Parking Lot, Elevator, Library, Chess, Vending Machine, ATM, Online Booking, Ride Sharing, LRU Cache, Rate Limiter, Tic-Tac-Toe, Splitwise |
| LLD Case Studies | [case_studies/README.md](case_studies/README.md) | Learning path, pattern matrix, interview shortcuts for 12 problems |

---

## All Topics

### Design Principles

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [SOLID Principles](solid_principles/README.md) | SRP, OCP, LSP, ISP, DIP | Beginner |
| [DRY](design_principles/DRY.md) | Don't Repeat Yourself, abstraction, deduplication | Beginner |
| [KISS](design_principles/KISS.md) | Keep It Simple Stupid, simplicity over cleverness | Beginner |
| [YAGNI](design_principles/YAGNI.md) | You Aren't Gonna Need It, avoid speculation | Beginner |
| [Composition over Inheritance](design_principles/Composition_over_Inheritance.md) | Favor composition, flexibility, delegation | Intermediate |
| [Law of Demeter](design_principles/LawOfDemeter.md) | Least knowledge, loose coupling, talk to friends | Intermediate |
| [Program to Interface](design_principles/ProgramToInterface.md) | Abstractions, polymorphism, decoupling | Intermediate |

### Creational Patterns

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Singleton](creational/singleton/README.md) | Single instance, lazy/eager init, thread safety | Beginner |
| [Factory Method](creational/factory_method/README.md) | Virtual constructor, defer instantiation to subclass | Intermediate |
| [Abstract Factory](creational/abstract_factory/README.md) | Families of related objects, product families | Intermediate |
| [Builder](creational/builder/README.md) | Step-by-step construction, fluent API, complex objects | Intermediate |
| [Prototype](creational/prototype/README.md) | Clone objects, copying, prototype registry | Intermediate |

### Structural Patterns

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Adapter](structural/adapter/README.md) | Convert interface, legacy integration, wrapper | Intermediate |
| [Decorator](structural/decorator/README.md) | Dynamic behavior extension, wrapping, open/closed | Intermediate |
| [Proxy](structural/proxy/README.md) | Surrogate, virtual proxy, protection proxy, lazy loading | Intermediate |
| [Facade](structural/facade/README.md) | Simplified interface, subsystem hiding, unified API | Beginner |
| [Composite](structural/composite/README.md) | Tree structure, part-whole hierarchy, uniform treatment | Intermediate |
| [Bridge](structural/bridge/README.md) | Separate abstraction from implementation, decouple | Advanced |
| [Flyweight](structural/flyweight/README.md) | Object sharing, intrinsic/extrinsic state, memory optimization | Advanced |

### Behavioral Patterns

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Strategy](behavioral/strategy/README.md) | Algorithm family, interchangeable behaviors, runtime selection | Intermediate |
| [Observer](behavioral/observer/README.md) | Pub/sub, event notification, loose coupling | Intermediate |
| [Command](behavioral/command/README.md) | Encapsulate requests, undo/redo, queue operations | Intermediate |
| [State](behavioral/state/README.md) | Object behavior varies with state, finite state machine | Intermediate |
| [Template Method](behavioral/template_method/README.md) | Algorithm skeleton, hook methods, subclass customization | Intermediate |
| [Iterator](behavioral/iterator/README.md) | Sequential access, collection traversal, hide structure | Beginner |
| [Chain of Responsibility](behavioral/chain_of_responsibility/README.md) | Handler chain, decoupled processing, pass along | Intermediate |
| [Mediator](behavioral/mediator/README.md) | Centralized communication, reduce coupling, hub | Intermediate |
| [Memento](behavioral/memento/README.md) | Capture and restore state, undo mechanism | Intermediate |
| [Visitor](behavioral/visitor/README.md) | Separate algorithm from object structure, double dispatch | Advanced |
| [Interpreter](behavioral/interpreter/README.md) | Grammar, language interpreter, expression trees | Advanced |

### Anti-Patterns

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Anti-Patterns Overview](anti_patterns/README.md) | Code smells, design pitfalls, refactored solutions | Intermediate |
| [God Object](anti_patterns/GodObject.md) | Bloated class, SRP violation, decomposition | Intermediate |
| [Spaghetti Code](anti_patterns/Spaghetti_Code.md) | Tangled control flow, no structure, refactoring | Intermediate |
| [Golden Hammer](anti_patterns/Golden_Hammer.md) | Overused tool, inappropriate pattern application | Intermediate |
| [Copy-Paste Programming](anti_patterns/Copy_Paste_Programming.md) | DRY violation, duplication, abstraction | Beginner |
| [Premature Optimization](anti_patterns/Premature_Optimization.md) | YAGNI violation, complexity without need | Intermediate |
| [Singleton Abuse](anti_patterns/Singleton_Abuse.md) | Global state, hidden coupling, testing difficulty | Intermediate |
| [Anemic Domain Model](anti_patterns/Anemic_Domain_Model.md) | Data-only classes, no behavior, service bloat | Advanced |
| [Service Locator](anti_patterns/ServiceLocator.md) | Hidden dependencies, anti-DI, unclear contracts | Advanced |
| [Lava Flow](anti_patterns/Lava_Flow.md) | Dead code, accidental architecture, cleanup | Intermediate |
| [Circular Dependencies](anti_patterns/Circular_Dependencies.md) | Dependency cycles, tight coupling, restructuring | Intermediate |

### Concurrency Patterns

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Thread-Safe Singleton](concurrency_patterns/ThreadSafeSingleton_README.md) | Double-checked locking, volatile, holder idiom | Intermediate |
| [Producer-Consumer](concurrency_patterns/ProducerConsumer_README.md) | Blocking queue, work queue, coordination | Intermediate |
| [Read-Write Lock](concurrency_patterns/ReadWriteLock_README.md) | Concurrent reads, exclusive writes, ReentrantReadWriteLock | Intermediate |
| [Thread Pool](concurrency_patterns/ThreadPool_README.md) | Executor service, task queue, bounded concurrency | Intermediate |

### Pattern Comparisons & Advanced

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Pattern Comparisons Overview](pattern_comparisons/README.md) | When to use which pattern, decision guide | Intermediate |
| [Strategy vs State](pattern_comparisons/Strategy_vs_State.md) | External vs internal context, switching mechanism | Intermediate |
| [Decorator vs Proxy](pattern_comparisons/Decorator_vs_Proxy.md) | Enhancement vs control, transparency | Intermediate |
| [Adapter vs Bridge vs Facade](pattern_comparisons/Adapter_vs_Bridge_vs_Facade.md) | Interface conversion vs decoupling vs simplification | Advanced |
| [Factory vs AbstractFactory vs Builder](pattern_comparisons/Factory_vs_AbstractFactory_vs_Builder.md) | Creation strategies | Intermediate |
| [Observer vs Mediator](pattern_comparisons/Observer_vs_Mediator.md) | Broadcast vs coordinated communication | Intermediate |
| [Command vs Strategy](pattern_comparisons/Command_vs_Strategy.md) | Request encapsulation vs algorithm selection | Intermediate |
| [Template vs Strategy](pattern_comparisons/Template_vs_Strategy.md) | Inheritance vs composition for variability | Intermediate |
| [Composite vs Decorator](pattern_comparisons/Composite_vs_Decorator.md) | Tree structure vs enhancement chain | Intermediate |
| [ChainOfResponsibility vs Command](pattern_comparisons/ChainOfResponsibility_vs_Command.md) | Handler chain vs request object | Intermediate |
| [Decision Flowchart](pattern_comparisons/DecisionFlowchart.md) | Pattern selection guide | Intermediate |
| [Pattern Combinations](pattern_comparisons/PatternCombinations.md) | Patterns working together | Advanced |
| [Interview Questions](pattern_comparisons/InterviewQuestions.md) | Common LLD interview questions | Intermediate |
| [Refactoring to Patterns](Refactoring_to_Patterns.md) | Step-by-step pattern application | Advanced |

### System Design Problems (LLD)

| Topic | Key Concepts | Difficulty |
|-------|-------------|------------|
| [Parking Lot](system_design_problems/ParkingLot_README.md) | OOP modeling, state machines, factory | Intermediate |
| [ATM System](system_design_problems/ATM_README.md) | State pattern, transaction modeling, security | Intermediate |
| [Elevator System](system_design_problems/ElevatorSystem_README.md) | Scheduling algorithm, state machine, observer | Intermediate |
| [Vending Machine](system_design_problems/VendingMachine_README.md) | State pattern, inventory management, payments | Intermediate |
| [Chess Game](system_design_problems/ChessGame_README.md) | Board modeling, piece polymorphism, move validation | Advanced |
| [Library Management](system_design_problems/LibraryManagement_README.md) | Catalog, reservations, fine calculation | Intermediate |
| [Online Booking System](system_design_problems/OnlineBookingSystem_README.md) | Concurrency, reservation, state transitions | Advanced |
| [Ride Sharing](system_design_problems/RideSharing_README.md) | Strategy (fare), Observer (status), Factory (vehicle), State (ride lifecycle) | Advanced |
| [LRU Cache](system_design_problems/LRUCache_README.md) | Doubly-linked list + HashMap, O(1) get/put, Decorator (thread safety) | Intermediate |
| [Rate Limiter](system_design_problems/RateLimiter_README.md) | Token bucket, sliding window, Strategy + Factory | Intermediate |
| [Tic-Tac-Toe](system_design_problems/TicTacToe_README.md) | Incremental win detection, Strategy (AI move selection) | Beginner |
| [Splitwise](system_design_problems/Splitwise_README.md) | Expense-sharing ledger, Strategy (split types), debt-graph simplification | Advanced |

---

## Recommended Learning Order

### Phase 1 — Foundations
Build your design vocabulary before patterns.

1. [SOLID Principles](solid_principles/README.md) — the why behind every pattern
2. [DRY](design_principles/DRY.md) — don't repeat yourself
3. [KISS](design_principles/KISS.md) — simplicity over cleverness
4. [YAGNI](design_principles/YAGNI.md) — avoid speculative design
5. [Composition over Inheritance](design_principles/Composition_over_Inheritance.md)
6. [Program to Interface](design_principles/ProgramToInterface.md)
7. [Law of Demeter](design_principles/LawOfDemeter.md)

### Phase 2 — Creational Patterns
Learn how to construct objects safely and flexibly.

1. [Singleton](creational/singleton/README.md) — controlled single instance
2. [Factory Method](creational/factory_method/README.md) — deferred creation
3. [Abstract Factory](creational/abstract_factory/README.md) — product families
4. [Builder](creational/builder/README.md) — complex construction
5. [Prototype](creational/prototype/README.md) — object cloning

### Phase 3 — Structural Patterns
Learn how to compose classes and objects into larger structures.

1. [Adapter](structural/adapter/README.md) — interface bridging
2. [Decorator](structural/decorator/README.md) — dynamic enhancement
3. [Proxy](structural/proxy/README.md) — controlled access
4. [Facade](structural/facade/README.md) — simplified interface
5. [Composite](structural/composite/README.md) — tree structures
6. [Bridge](structural/bridge/README.md) — implementation decoupling
7. [Flyweight](structural/flyweight/README.md) — memory sharing

### Phase 4 — Behavioral Patterns
Learn how objects communicate and distribute responsibility.

1. [Strategy](behavioral/strategy/README.md) — interchangeable algorithms
2. [Observer](behavioral/observer/README.md) — event notification
3. [Command](behavioral/command/README.md) — encapsulated requests
4. [State](behavioral/state/README.md) — behavior per state
5. [Template Method](behavioral/template_method/README.md) — algorithm skeleton
6. [Iterator](behavioral/iterator/README.md) — collection traversal
7. [Chain of Responsibility](behavioral/chain_of_responsibility/README.md) — handler chain
8. [Mediator](behavioral/mediator/README.md) — centralized coordination
9. [Memento](behavioral/memento/README.md) — state capture/restore
10. [Visitor](behavioral/visitor/README.md) — external operations
11. [Interpreter](behavioral/interpreter/README.md) — grammar/language

### Phase 5 — Pattern Mastery
Understand when to use which pattern and how they combine.

1. [Pattern Comparisons](pattern_comparisons/README.md)
2. [Decision Flowchart](pattern_comparisons/DecisionFlowchart.md)
3. [Pattern Combinations](pattern_comparisons/PatternCombinations.md)
4. [Refactoring to Patterns](Refactoring_to_Patterns.md)
5. [Interview Questions](pattern_comparisons/InterviewQuestions.md)

### Phase 6 — Anti-Patterns
Know what to avoid and why.

1. [Anti-Patterns Overview](anti_patterns/README.md)
2. Read all 10 individual anti-pattern files
3. Recognize them in existing codebases

### Phase 7 — Concurrency Patterns
Thread safety and parallel design.

1. [Thread-Safe Singleton](concurrency_patterns/ThreadSafeSingleton_README.md)
2. [Producer-Consumer](concurrency_patterns/ProducerConsumer_README.md)
3. [Read-Write Lock](concurrency_patterns/ReadWriteLock_README.md)
4. [Thread Pool](concurrency_patterns/ThreadPool_README.md)

### Phase 8 — Apply: System Design Problems
Put it all together with end-to-end LLD exercises.

1. [Parking Lot](system_design_problems/ParkingLot_README.md) — start here (classic)
2. [ATM System](system_design_problems/ATM_README.md)
3. [Elevator System](system_design_problems/ElevatorSystem_README.md)
4. [Vending Machine](system_design_problems/VendingMachine_README.md)
5. [Chess Game](system_design_problems/ChessGame_README.md)
6. [Library Management](system_design_problems/LibraryManagement_README.md)
7. [Online Booking System](system_design_problems/OnlineBookingSystem_README.md)
8. [Ride Sharing](system_design_problems/RideSharing_README.md)
9. [LRU Cache](system_design_problems/LRUCache_README.md)
10. [Rate Limiter](system_design_problems/RateLimiter_README.md)
11. [Tic-Tac-Toe](system_design_problems/TicTacToe_README.md)
12. [Splitwise](system_design_problems/Splitwise_README.md)

---

## Learning Paths

This section is exhaustive by design — 9 modules spanning design vocabulary, all
three GoF pattern families, pattern-selection judgment, anti-patterns, concurrency
idioms, and 12 end-to-end system design problems. That is the right depth for a
reference and the wrong shape for someone one week from an LLD round. So there are
**two ways through it**; the browser learning game's **Study** view surfaces both as
a **Full / Interview** toggle (Full is the default).

### Full Path (9 modules)

The complete curriculum in the order above — see [Recommended Learning Order](#recommended-learning-order). Use it for genuine mastery: the design vocabulary (SOLID Principles and Design Principles), every GoF family (Creational, Structural, Behavioral), pattern-selection judgment (Pattern Comparisons), what to avoid (Anti-Patterns), thread-safety idioms (Concurrency Patterns), and all 12 System Design Problems. Nothing is dropped.

### Interview-Specific Path (7 modules)

A ruthless cut to what a **senior LLD interview** actually probes: the GoF vocabulary plus the "design a parking lot / rate limiter" round every LLD interview runs on. Same learning order, 2 modules fewer. Each group below says why it earns interview time.

| Group | Modules | Why it's tested |
|-------|---------|-----------------|
| Foundations | [SOLID Principles](solid_principles/README.md) | SRP/OCP/LSP violations are the standard "what's wrong with this class" opener, and justify every pattern discussed afterward |
| Creational Patterns | [Creational Patterns](creational/README.md) | Thread-safe Singleton (DCL + volatile) and Factory Method vs Abstract Factory vs Builder — the creation-strategy question nearly every interview touches |
| Structural Patterns | [Structural Patterns](structural/README.md) | Decorator vs Proxy, Adapter vs Facade — composition-based flexibility questions test whether you reach for inheritance by default |
| Behavioral Patterns | [Behavioral Patterns](behavioral/README.md) | Strategy, Observer, State, Command, Template Method — the core vocabulary; most "design X" problems resolve to two or three of these |
| Pattern Mastery | [Pattern Comparisons](pattern_comparisons/README.md) | Strategy vs State, Decorator vs Proxy — interviewers probe judgment ("why this pattern, not that one"), not memorization |
| Anti-Patterns | [Anti-Patterns](anti_patterns/README.md) | "How would you refactor this?" rounds test recognition of God Object, Service Locator, and Circular Dependencies before you can propose a fix |
| Apply: System Design Problems | [System Design Problems](system_design_problems/README.md) | The actual 30-45 minute round — Parking Lot, Rate Limiter, LRU Cache — where every principle above gets combined under time pressure |

**Deliberately deferred to the Full Path**: Design Principles (DRY, KISS, YAGNI, Law of Demeter, Composition over Inheritance, Program to Interface — folded into SOLID above, since interviewers rarely test these by name even though their instincts run through every pattern discussion) and Concurrency Patterns (Thread-Safe Singleton, Producer-Consumer, Read-Write Lock, Thread Pool — the deep mechanics live in [java/concurrency](../java/concurrency/README.md); LLD rounds rarely dedicate time to raw concurrency primitives). A niche flagged in an interview (e.g. "how would you make this thread-safe?") is a bonus, not a gate — reach for these once the 7 above are solid.

---

## Knowledge-Question Map

The highest-frequency LLD *knowledge* questions mapped to the file that answers them. For *system design* ("design X") questions, use the interview-prep shortcuts in [case_studies/README.md](case_studies/README.md).

| Interview question | Where the answer lives |
|--------------------|------------------------|
| What's a real Single Responsibility Principle violation, and how do you split it? | [SOLID Principles](solid_principles/README.md) |
| What is the Open/Closed Principle, and which pattern is its textbook embodiment? | [SOLID Principles](solid_principles/README.md) |
| What does a Liskov Substitution Principle violation actually look like? | [SOLID Principles](solid_principles/README.md) |
| Factory Method vs Abstract Factory vs Builder — how do you choose? | [Creational Patterns](creational/README.md), [Factory vs AbstractFactory vs Builder](pattern_comparisons/Factory_vs_AbstractFactory_vs_Builder.md) |
| How do you make a Singleton thread-safe without synchronizing every call? | [Singleton](creational/singleton/README.md) |
| Deep vs shallow copy in the Prototype pattern — which do you need, and when? | [Prototype](creational/prototype/README.md) |
| Decorator vs Proxy — both wrap an object, so what's the real distinction? | [Decorator vs Proxy](pattern_comparisons/Decorator_vs_Proxy.md) |
| Adapter vs Bridge vs Facade — when do you reach for each? | [Adapter vs Bridge vs Facade](pattern_comparisons/Adapter_vs_Bridge_vs_Facade.md) |
| Composite vs Decorator — tree structure vs enhancement chain, how do you tell them apart? | [Composite vs Decorator](pattern_comparisons/Composite_vs_Decorator.md) |
| Strategy vs State — both swap behavior at runtime, so what actually differs? | [Strategy vs State](pattern_comparisons/Strategy_vs_State.md) |
| Observer vs Mediator — broadcast vs coordinated communication? | [Observer vs Mediator](pattern_comparisons/Observer_vs_Mediator.md) |
| Command vs Strategy — both encapsulate "a thing to run" — what's the real distinction? | [Command vs Strategy](pattern_comparisons/Command_vs_Strategy.md) |
| Chain of Responsibility vs Command — chained handlers vs a single request object? | [ChainOfResponsibility vs Command](pattern_comparisons/ChainOfResponsibility_vs_Command.md) |
| Template Method vs Strategy — inheritance-based skeleton vs composition? | [Template vs Strategy](pattern_comparisons/Template_vs_Strategy.md) |
| How does Visitor add a new operation without touching the classes it operates on? | [Visitor](behavioral/visitor/README.md) |
| What is a God Object, and what's your refactoring plan for one? | [God Object](anti_patterns/GodObject.md) |
| What's wrong with Service Locator versus explicit Dependency Injection? | [Service Locator](anti_patterns/ServiceLocator.md) |
| How would you design a parking lot's spot allocation and pricing strategy? | [Parking Lot](system_design_problems/ParkingLot_README.md) |
| How do you implement an O(1) LRU cache and make it thread-safe? | [LRU Cache](system_design_problems/LRUCache_README.md) |
| Token bucket vs sliding window — what tradeoffs separate rate-limiting algorithms? | [Rate Limiter](system_design_problems/RateLimiter_README.md) |

---

## Study Plan

A 6-week plan over the Interview-Specific Path. Each week pairs the pattern theory with one problem from `system_design_problems/` to rehearse the "design X" format — see [case_studies/README.md](case_studies/README.md) for the full pattern-dependency map and interview-prep shortcuts across all 12 problems.

| Week | Focus | Modules | Practice problem |
|------|-------|---------|-------------------|
| 1 | Foundations | [SOLID Principles](solid_principles/README.md) | skim [Vending Machine](system_design_problems/VendingMachine_README.md) — cleanest FSM, see the vocabulary applied before you've learned the individual patterns |
| 2 | Creational Patterns | [Creational Patterns](creational/README.md) — Singleton, Factory Method, Abstract Factory, Builder, Prototype | [Parking Lot](system_design_problems/ParkingLot_README.md) — Factory (spot type) + Strategy (pricing); the most common LLD opener |
| 3 | Structural Patterns | [Structural Patterns](structural/README.md) — Adapter, Decorator, Proxy, Facade, Composite, Bridge, Flyweight | [LRU Cache](system_design_problems/LRUCache_README.md) — Decorator as a thread-safe wrapper around the core cache |
| 4 | Behavioral Patterns | [Behavioral Patterns](behavioral/README.md) — Strategy, Observer, Command, State, Template Method, and 6 more | [Ride Sharing](system_design_problems/RideSharing_README.md) — Strategy (fare), Observer (status), Factory (vehicle), State (ride lifecycle) |
| 5 | Pattern Mastery + Anti-Patterns | [Pattern Comparisons](pattern_comparisons/README.md), [Anti-Patterns](anti_patterns/README.md) | [ATM](system_design_problems/ATM_README.md) — justify State + Template Method over the alternatives; spot the anti-patterns a naive transaction flow invites |
| 6 | Apply: System Design Problems | [System Design Problems](system_design_problems/README.md) | timeboxed 30-minute runs through the remaining problems via the [interview shortcuts](case_studies/README.md) |

---

## Key Tradeoffs in LLD

| Decision | Option A | Option B | Deciding Factor |
|----------|----------|----------|-----------------|
| Inheritance vs Composition | Inheritance (is-a) | Composition (has-a) | Favor composition; inheritance for true is-a relationships only |
| Singleton vs Dependency Injection | Singleton (global access) | DI (explicit dependency) | DI for testability; Singleton only for truly unique resources |
| Abstract class vs Interface | Abstract class (shared implementation) | Interface (pure contract) | Interface when unrelated classes share behavior |
| Factory Method vs Abstract Factory | Factory Method (one product) | Abstract Factory (product families) | Use Abstract Factory when products must be compatible |
| Strategy vs Template Method | Strategy (composition) | Template Method (inheritance) | Strategy for runtime variability; Template for compile-time |
| Observer vs Mediator | Observer (broadcast) | Mediator (coordinated) | Observer for simple events; Mediator for complex coordination |
| Command vs Strategy | Command (request as object) | Strategy (algorithm) | Command for undo/redo/queue; Strategy for selection |
| Eager vs Lazy initialization | Eager (load at startup) | Lazy (load on demand) | Lazy for expensive resources; Eager for always-needed resources |

---

## Cross-References

- **HLD**: See [High-Level Design](../hld/README.md) for system architecture, distributed systems, and scalability patterns — HLD describes what LLD builds.
- **LLM**: See [LLM Section](../llm/README.md) for how these design patterns are applied in ML systems: Strategy for model selection, Observer for training callbacks, Chain of Responsibility for guardrails pipelines, Factory for model instantiation.

---

## LLD in System Design Interviews

LLD questions are typically asked separately from HLD but complement each other. Common formats:

1. **"Design a Parking Lot"** — OOP modeling, class relationships, state
2. **"Design a Rate Limiter"** (LLD angle) — data structures, algorithm; see [RateLimiter](system_design_problems/RateLimiter_README.md)
3. **"Apply [specific pattern] to this problem"** — pattern knowledge
4. **"How would you refactor this code?"** — anti-pattern recognition

**Framework for LLD interviews:**
1. Clarify requirements (functional scope, constraints)
2. Identify entities (nouns → classes)
3. Identify behaviors (verbs → methods)
4. Define relationships (association, aggregation, composition, inheritance)
5. Apply patterns where they fit naturally (don't force patterns)
6. Discuss SOLID compliance
7. Consider thread safety if applicable
