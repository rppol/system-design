# LLD Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/lld/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

LLD describes how individual components are implemented — class relationships, design patterns, SOLID principles. Use HLD for architectural decisions; use LLD for implementation decisions.

---

## Topic List

### Design Principles

| File/Directory | Topic |
|----------------|-------|
| `solid_principles/` | SRP, OCP, LSP, ISP, DIP — with violation examples and fixes |
| `anti_patterns/` | God Object, Blob, Golden Hammer, Spaghetti Code, Copy-Paste, Magic Numbers, etc. |
| `pattern_comparisons/` | When to use which pattern; comparison tables across GoF patterns |

### Creational Patterns

| Directory | Pattern |
|-----------|---------|
| `creational/singleton/` | Thread-safe singleton: DCL with volatile, enum singleton, holder pattern |
| `creational/factory_method/` | Factory method vs abstract factory; virtual constructor idiom |
| `creational/abstract_factory/` | Family of related objects; cross-platform UI example |
| `creational/builder/` | Step-by-step construction; fluent API; Effective Java Item 2 |
| `creational/prototype/` | Clone via copy constructor vs Cloneable; deep vs shallow copy |

### Structural Patterns

| Directory | Pattern |
|-----------|---------|
| `structural/adapter/` | Object vs class adapter; legacy system integration |
| `structural/bridge/` | Abstraction + implementation separation; prevent cartesian product explosion |
| `structural/composite/` | Tree structures; file system, UI component hierarchy |
| `structural/decorator/` | Runtime behavior extension; Java I/O streams; Effective Java Item 18 |
| `structural/facade/` | Simplified interface to complex subsystem |
| `structural/flyweight/` | Shared intrinsic state; String pool analogy |
| `structural/proxy/` | Virtual, protection, remote, logging proxies; Spring AOP |

### Behavioral Patterns

| Directory | Pattern |
|-----------|---------|
| `behavioral/chain_of_responsibility/` | Handler chain; Spring filter chain; middleware pipelines |
| `behavioral/command/` | Encapsulate operations; undo/redo; task queue |
| `behavioral/iterator/` | Sequential access without exposing internals; Java Iterator protocol |
| `behavioral/mediator/` | Reduce coupling via central coordinator; chat room, ATC |
| `behavioral/memento/` | Snapshot and restore state; undo history |
| `behavioral/observer/` | Event notification; Java EventListener; Spring ApplicationEvent |
| `behavioral/state/` | FSM via objects; vending machine, TCP connection states |
| `behavioral/strategy/` | Interchangeable algorithms; sorting, payment methods |
| `behavioral/template_method/` | Fixed algorithm skeleton with overridable steps |
| `behavioral/visitor/` | New operations without modifying elements; AST traversal |
| `behavioral/interpreter/` | Grammar interpreter; expression parser; SQL WHERE clause |

### LLD Interview Problems

12 problems, each with a `<Name>_README.md` (problem statement, ASCII class diagram, patterns, tradeoffs) and a runnable `<Name>.java` in `system_design_problems/`:

| Problem | Key Patterns | File |
|---------|-------------|------|
| Design a parking lot | Strategy, Factory, State | [ParkingLot](system_design_problems/ParkingLot_README.md) |
| Design an elevator | State, Observer | [ElevatorSystem](system_design_problems/ElevatorSystem_README.md) |
| Design a library management system | Builder, Observer, Strategy | [LibraryManagement](system_design_problems/LibraryManagement_README.md) |
| Design a chess game | Composite, Command, State | [ChessGame](system_design_problems/ChessGame_README.md) |
| Design a vending machine | State, Strategy, Factory | [VendingMachine](system_design_problems/VendingMachine_README.md) |
| Design an ATM | State, Template Method | [ATM](system_design_problems/ATM_README.md) |
| Design a movie/flight/hotel booking system | Strategy, Observer, Builder | [OnlineBookingSystem](system_design_problems/OnlineBookingSystem_README.md) |
| Design a ride-sharing app | Strategy, Observer, Factory, State | [RideSharing](system_design_problems/RideSharing_README.md) |
| Design an LRU cache (+ LFU variant) | Decorator (thread-safe wrapper) | [LRUCache](system_design_problems/LRUCache_README.md) |
| Design a rate limiter (LLD angle) | Strategy, Factory | [RateLimiter](system_design_problems/RateLimiter_README.md) |
| Design tic-tac-toe | Strategy, State | [TicTacToe](system_design_problems/TicTacToe_README.md) |
| Design Splitwise / expense sharing | Strategy, Factory | [Splitwise](system_design_problems/Splitwise_README.md) |

---

## Cross-Reference Map

| LLD Topic | See Also |
|-----------|---------|
| `behavioral/observer/` | `../../spring/spring_events_and_scheduling/` — Spring ApplicationEvent; `../../java/design_patterns_in_java/` |
| `structural/proxy/` | `../../spring/spring_proxies/` — JDK vs CGLIB proxy mechanics |
| `behavioral/template_method/` | `../../spring/spring_batch/` — ItemReader/Processor/Writer uses template method |
| `behavioral/strategy/` | `../../java/functional_programming/` — lambda as strategy object |
| `behavioral/chain_of_responsibility/` | `../../spring/filters_and_interceptors/` — Spring filter chain |
| `solid_principles/` | `../../java/design_patterns_in_java/` — SOLID applied to GoF patterns |
| `design_principles/` | `../../java/design_patterns_in_java/` — SOLID + principles applied in Java |
| `concurrency_patterns/` | `../../java/concurrency/` — ExecutorService internals, ThreadPoolExecutor tuning, deep Java concurrency |
| `concurrency_patterns/` | `../../java/java_memory_model/` — happens-before, volatile semantics, DCL correctness |
| `system_design_problems/` | `../../hld/microservices/` — Parking Lot / Elevator at distributed system scale |
| `system_design_problems/RateLimiter_README.md` | `../../hld/rate_limiting/` — single-JVM token bucket/sliding window here vs. Redis-backed distributed rate limiting at HLD scale |
| `system_design_problems/LRUCache_README.md` | `../../hld/caching/`, `../../database/database_caching_patterns/` — exact in-process LRU here vs. approximated/sampled LRU eviction in Redis/Memcached at scale |

---

## Content Rules (LLD-specific)

- Diagrams must use ASCII class/sequence diagrams — no Mermaid, no image files
- Code examples in Java (primary) — show the pattern then show violation then fix
- Minimum 10 Q&As per pattern module
- Always include: when to use vs when NOT to use, common misuse/anti-pattern of this pattern
- Cross-reference to HLD when the pattern has a system-design analogue (e.g., Observer → Event-Driven Architecture)

## Adding a New LLD Pattern

1. Create `<category>/<pattern_name>/README.md` — 14-section template
2. Include: UML ASCII diagram, motivation, participants, Java implementation, real-world example, tradeoffs, Q&As
3. Show the problem it solves (before-code) and the pattern applied (after-code)
4. Update the relevant **category** `README.md` (e.g., `behavioral/README.md`) — add a row to the pattern catalogue table
5. Update the **master** `README.md` (the LLD master index) — add a row to the appropriate section table
6. Add to the `pattern_comparisons/` file if it overlaps with other patterns
