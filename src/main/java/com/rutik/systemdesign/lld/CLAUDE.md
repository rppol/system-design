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

12 problems, each with a `<Name>.md` (problem statement, ASCII class diagram, patterns, tradeoffs) and a runnable `<Name>.java` in `system_design_problems/`:

| Problem | Key Patterns | File |
|---------|-------------|------|
| Design a parking lot | Strategy, Factory, State | [ParkingLot](system_design_problems/ParkingLot.md) |
| Design an elevator | State, Observer | [ElevatorSystem](system_design_problems/ElevatorSystem.md) |
| Design a library management system | Builder, Observer, Strategy | [LibraryManagement](system_design_problems/LibraryManagement.md) |
| Design a chess game | Command, Observer, Singleton | [ChessGame](system_design_problems/ChessGame.md) |
| Design a vending machine | State, Strategy, Factory | [VendingMachine](system_design_problems/VendingMachine.md) |
| Design an ATM | State, Command, Facade | [ATM](system_design_problems/ATM.md) |
| Design a movie/flight/hotel booking system | Strategy, Observer, Builder | [OnlineBookingSystem](system_design_problems/OnlineBookingSystem.md) |
| Design a ride-sharing app | Strategy, Observer, Factory, State | [RideSharing](system_design_problems/RideSharing.md) |
| Design an LRU cache (+ LFU variant) | Decorator (thread-safe wrapper) | [LRUCache](system_design_problems/LRUCache.md) |
| Design a rate limiter (LLD angle) | Strategy, Factory | [RateLimiter](system_design_problems/RateLimiter.md) |
| Design tic-tac-toe | Strategy, State | [TicTacToe](system_design_problems/TicTacToe.md) |
| Design Splitwise / expense sharing | Strategy, Factory | [Splitwise](system_design_problems/Splitwise.md) |

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
| `system_design_problems/RateLimiter.md` | `../../hld/rate_limiting/` — single-JVM token bucket/sliding window here vs. Redis-backed distributed rate limiting at HLD scale |
| `system_design_problems/LRUCache.md` | `../../hld/caching/`, `../../database/database_caching_patterns/` — exact in-process LRU here vs. approximated/sampled LRU eviction in Redis/Memcached at scale |

---

## Content Rules (LLD-specific)

- Diagrams follow the repo-wide **appeal-first** policy in root `CLAUDE.md` — prefer Mermaid
  (`classDiagram` for class relationships, `sequenceDiagram` for interaction order,
  `stateDiagram-v2` for FSM patterns like State and Memento), and keep ASCII only where
  character alignment carries the meaning. No image files. This rule previously read "no
  Mermaid", which contradicted both root policy and the section itself: 82 of 85 lld files
  already carry Mermaid, 271 fences in total. Corrected 2026-07-28.
- Code examples in Java (primary) — show the pattern then show violation then fix

### Java fences are illustrative, not compilable units (owner-ruled 2026-07-28)

A ```` ```java ```` fence in this section exists to **explain a concept in Java**, not to be
pasted into a file and compiled. Two consequences follow, and **neither is a defect — do not
"fix" them, and do not flag them in an audit:**

1. **A fence may declare many public top-level types.** Java permits only one per `.java`
   file, so these fences cannot compile as-is. That is fine and deliberate: 99 fences across
   47 files show a whole design at once (`InterfaceSegregation.md` shows eight types — the
   violation and its fix together), and splitting them into one-type-per-fence would destroy
   the side-by-side comparison that makes the lesson land.

2. **A fence may declare the same type twice** when it is a `// BROKEN:` / `// FIX:` pair.
   Reusing the name IS the teaching device — it shows the *same* class before and after, which
   is exactly the "show broken code, then the fix" standard root `CLAUDE.md` requires. 14
   fences do this (DCL `Singleton` broken/volatile/holder; `EmailObserver` sync/`@Async`;
   `GlyphData` with and without extrinsic state). Renaming them to `FooBroken`/`FooFixed`
   would weaken every one.

**What IS still a defect,** and what an audit should flag:

- **Code that contradicts its own prose or diagram.** If the surrounding text or class diagram
  names `BrokenStack`/`FixedStack`, the fence must use those identifiers. (This was the one
  real defect among the 14 — `design_principles/README.md` declared `Stack` twice while its
  Mermaid diagram and prose called them `BrokenStack` and `FixedStack`.)
- Code that cannot work **as described** — a method that does not exist, a demo the compiler
  optimizes away, an override that is unreachable, an inverted mechanism.
- A duplicate type that is **not** a broken/fixed pair, i.e. two genuinely different classes
  that happen to collide on a name.
- Minimum 10 Q&As per pattern module
- Always include: when to use vs when NOT to use, common misuse/anti-pattern of this pattern
- Cross-reference to HLD when the pattern has a system-design analogue (e.g., Observer → Event-Driven Architecture)

---

## Learning Paths (Full + Senior + Principal)

`README.md` documents the **Full Path** (all 9 modules = "Recommended Learning Order")
plus two curated tiers: **Senior** (8 modules) and **Principal** (8). They are different
cuts, not nested depths — senior is the craft (write the pattern, spot the misuse),
principal is the judgment (which decomposition at what cost, what you tell a team *not*
to do), so equal module counts here hide genuinely different *file* lists. Membership is
declared ONCE per module, in a `<!-- study-paths -->` block in that module's own README
naming the files each tier takes — and in `lld` that file list is the whole point: a
category module holds dozens of nested pattern READMEs (`singleton/README.md`,
`factory_method/README.md`, …), and before the markers existed a tiered category dragged
in **every** one of them, so 7 modules shipped 62 sub-files, 90% of the section, in a path
advertised as a cut. Listing a tier joins it, omitting the tier opts out, and `README.md`
must always be listed. Order is never declared — it comes from `STUDY_ORDER.lld` in
`game/app.js`, so a tier is an ordered subset by construction. **There is no path array in
`app.js` to edit**: `extract.py` walks the markers and emits the gitignored
`questions/paths.json`, which the game fetches at boot. The tier tables in `README.md` sit
between `<!-- study-path-table <tier> -->` markers and are **generated** — regenerate with
`python3 game/extract.py --write-paths`; a hand-edited or stale block fails
`extract.py --strict` and the Pages deploy. There is no Case Studies track here (see
below). The README also carries a Knowledge-Question Map and a 6-week Study Plan (prose;
no path impact). The Study Plan pairs each week with a practice problem from
`system_design_problems/` rather than a `case_studies/` file — LLD's `case_studies/`
holds only a learning-path index (`case_studies/README.md`: pattern-dependency map +
interview shortcuts) over the same 12 problems, not separate case-study write-ups.

## Adding a New LLD Pattern

1. Create `<category>/<pattern_name>/README.md` — 14-section template
2. Include: UML class diagram (Mermaid `classDiagram` preferred per the Content Rules above; ASCII only where alignment carries the meaning), motivation, participants, Java implementation, real-world example, tradeoffs, Q&As
3. Show the problem it solves (before-code) and the pattern applied (after-code)
4. Update the relevant **category** `README.md` (e.g., `behavioral/README.md`) — add a row to the pattern catalogue table
5. Update the **master** `README.md` (the LLD master index) — add a row to the appropriate section table
6. Add to the `pattern_comparisons/` file if it overlaps with other patterns
7. **No `STUDY_ORDER` entry** — a nested `<category>/<pattern>/README.md` folds into its
   parent category module (ids stay 2 segments; a 3-segment key is fatal under `--strict`).
   If a tier should carry the pattern, add `<pattern_name>/README.md` to that tier's line
   in the CATEGORY README's `<!-- study-paths -->` block; naming no tier leaves it
   Full-path only, which is the right call for most patterns. Then run
   `python3 game/extract.py --write-paths` to regenerate the master README's tier tables.

---

## Visual Intuition Diagrams

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".
