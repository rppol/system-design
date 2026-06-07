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

| Problem | Key Patterns |
|---------|-------------|
| Design a parking lot | Strategy, Factory, State |
| Design an elevator | State, Observer |
| Design a library management system | Builder, Observer, Strategy |
| Design a chess game | Composite, Command, State |
| Design a vending machine | State, Strategy, Factory |
| Design a ride-sharing app | Strategy, Observer, Factory |
| Design an ATM | State, Template Method |

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
4. Update the relevant section of `README.md` (the LLD master index)
5. Add to the `pattern_comparisons/` file if it overlaps with other patterns
