# LLD Audit — 2026-08-25

## 0. Snapshot

`lld/` is the repo's design-craft section: 9 modules, 83 content pages, 39,119 lines of Markdown,
271 Mermaid fences, and 63 companion `.java` files (19,977 lines). Its real strength is the **GoF
core**: all 23 patterns are present, each as its own folder-named page under
`creational/`, `structural/`, `behavioral/`, running 500–1,200 lines on a consistent 18-section
pattern template (Intent → Problem → Solution → UML → Mechanics → When/When-NOT → Pros/Cons →
Tradeoffs → Pitfalls → Real-World Usage → Comparison → Interview Q&As → Cross-Perspective → Best
Practices → Technologies). Those 26 pages carry 15–17 Q&As each, 100% `**Short:**` coverage, and
are genuinely current — `visitor.md` teaches sealed interfaces + record patterns + exhaustive
`switch` as the modern replacement for double dispatch, and `observer.md` covers `java.util.concurrent.Flow`
backpressure, Reactor, and `@TransactionalEventListener`. `flyweight.md` derives a memory formula and
recomputes it under JDK 25 compact object headers. That core is close to finished — the handful of
real defects in it are itemised in §4 and are all small.

**The single biggest weakness is that the rest of the section is a tier below, and the gap is
measurable in the quiz bank.** 36 of the 83 content pages contribute **zero** questions. The three
GoF modules produce 399 of the section's 571 questions across 26 files (15.3/file); the other six
modules produce 172 across 57 files (3.0/file). Every `design_principles/` sub-file, 10 of 11
`anti_patterns/` files, 7 of 13 `pattern_comparisons/` files, and **all 12 `system_design_problems/`
files** have no interview section at all. The prose to answer those questions is already written —
this is a harvest, not authoring.

| Metric | Value |
|--------|-------|
| Modules (`STUDY_ORDER.lld`) | 9 |
| Content pages (`.md`, excl. README/CLAUDE) | 83 |
| GoF pattern pages | 23 (+3 category pages) |
| LLD problems | 12 (in `system_design_problems/`, not `case_studies/`) |
| Case studies | 0 — `case_studies/` holds only a 214-line index |
| Deep-dive sub-files | 57 (non-category, non-module pages) |
| Total lines (`.md`) | 39,119 |
| Companion `.java` files | 63 files / 19,977 lines |
| Mermaid fences | 271 |
| Q&As authored (`^**Q`) | 580 |
| Q&As reaching the bank | **571** (9 lost — see §2) |
| `**Short:**` coverage | 571/571 of parsed Q&As (complete) |
| Avg lines/page | 471 |
| Pages with **zero** bank questions | **36 of 83 (43%)** |

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `lld/system_design_problems/MACHINE_CODING.md` → better: fold into `system_design_problems.md` as `## 3b. The 90-Minute Machine-Coding Round` | module-page section | The module page only teaches a **30-minute design-discussion** framework. The Indian market's standard round is 90–120 min producing "a working, clean, extensible console-based application," graded on working code / clean design / extensibility / edge cases — a different artifact with a different clock. [lowleveldesignmastery.com/blog/machine-coding-round](https://www.lowleveldesignmastery.com/blog/machine-coding-round/), [workat.tech](https://workat.tech/machine-coding/article/what-is-a-machine-coding-round-omfn1w54ojlg) | **P0** | +180 |
| `lld/oop_and_uml_foundations/oop_and_uml_foundations.md` | module | Every 2026 LLD curriculum starts here and this repo has **no OOP/OOD/UML module anywhere** (verified: no `oop*`/`uml*` directory in any of the 16 sections). The canonical order is OOP → class relationships → design principles → SOLID → UML → patterns. Association vs aggregation vs composition vs dependency, and class/sequence/state diagram notation, are assumed by all 23 pattern pages and taught by none. [ashishps1/awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design), ["I Would NOT Start Learning LLD With Design Patterns in 2026"](https://javascript.plainenglish.io/i-would-not-start-learning-low-level-design-with-design-patterns-in-2026-2eca9951e667) | **P0** | 700 |
| `lld/concurrency_patterns/modern_java_concurrency.md` | sub-file | `StructuredTaskScope` and `ScopedValue` have **zero hits** in the whole `lld/` tree. `ScopedValue` is *final* in Java 25 (JEP 506) and is the structural fix for `ThreadPool.md`'s own `ThreadLocal`-leak pitfall; structured concurrency is the fan-out/fan-in story for virtual threads. [openjdk.org/jeps/506](https://openjdk.org/jeps/506), [openjdk.org/jeps/533](https://openjdk.org/jeps/533) | **P0** | 550 |
| `lld/refactoring/refactoring.md` (promote the orphaned `Refactoring_to_Patterns.md`) | module | `Refactoring_to_Patterns.md` sits at the **section root**, is in no module, absent from the `<!-- study-paths -->` block, absent from `STUDY_ORDER`, and produces 0 questions — it is invisible to the game and reachable only from README prose. Promoting it to a module and adding Fowler's smell catalog (Data Clumps, Message Chains, Refused Bequest, Divergent Change, Shotgun Surgery, Primitive Obsession, Speculative Generality, Middle Man, Temporary Field) closes both the orphan and the smell-coverage gap in one move. | **P0** | 900 (492 existing + 400) |
| `lld/dependency_injection_and_testability/…md` | module | DI appears in 22 files as a passing mention and has no page of its own; the only dedicated treatment is `anti_patterns/ServiceLocator.md` (the negative case). Interviewers now grade "explain how your interfaces allow mocking" and "constructor injection makes this testable without a Spring container" as core LLD signal. Test-double taxonomy (dummy/stub/spy/mock/fake), seams, humble object. [designgurus.io — what to expect in an LLD round](https://www.designgurus.io/answers/detail/what-to-expect-in-a-low-level-design-interview-round) | **P1** | 750 |
| `lld/system_design_problems/LoggingFramework.md` | problem | Ranked #6 of the top 20, asked at Amazon/Google/Microsoft/Apple/Splunk; it is *the* clean exercise for Chain of Responsibility + Strategy + async appenders + thread safety, and every list has it. [lowleveldesignmastery top-20](https://www.lowleveldesignmastery.com/blog/low-level-design-interview-questions/), [hellointerview logging-service](https://www.hellointerview.com/learn/low-level-design/problem-breakdowns/logging-service) | **P1** | 450 |
| `lld/system_design_problems/NotificationService.md` | problem | Top-20 #17 (Amazon, Uber, LinkedIn, Twitter, Flipkart). `case_studies.md` currently punts "Design a notification system" to *Library Management* — a stretch mapping a dedicated page would replace. Multi-channel Strategy, per-user vs global throttling, retry/DLQ, priority classes. [vervecopilot notification-system-design](https://www.vervecopilot.com/hot-blogs/notification-system-design-interview) | **P1** | 480 |
| `lld/system_design_problems/TaskScheduler.md` | problem | Top-20 #18; the canonical *concurrency* LLD problem (priority queue + delay + worker pool + cancellation) and the natural bridge to `concurrency_patterns/`. Also `ashishps1`'s "Task Management System". | **P1** | 450 |
| `lld/system_design_problems/SnakeAndLadder.md` | problem | Listed "Hard" in the canonical India-market repo; the standard *simple-rules, clean-modelling* warm-up that pairs with TicTacToe. [ashishps1/awesome-low-level-design](https://github.com/ashishps1/awesome-low-level-design) | **P2** | 380 |
| `lld/system_design_problems/FoodDelivery.md` | problem | "Design Online Food Delivery Service like Swiggy" (Hard) + top-20 #11 Restaurant Ordering (Zomato/Swiggy/DoorDash/UberEats). `case_studies.md` currently answers it with "ATM + Booking". | **P2** | 480 |
| `lld/system_design_problems/OrderMatchingEngine.md` | problem | Top-20 #12 (Goldman Sachs, Morgan Stanley, Coinbase, Robinhood); the fintech-track LLD problem and the only one that forces price-time priority + `TreeMap` design. | **P2** | 450 |
| `lld/architecture_patterns/architecture_patterns.md` (hexagonal / ports-and-adapters / layered / clean + DDD tactical) | module | Hexagonal has "seen a major resurgence in 2026 as teams adopt AI-generated code, microservices, and rapid iteration"; DDD tactical patterns (entity / value object / aggregate root / repository / domain service / domain event) are baseline backend vocabulary. `lld/` mentions "hexagonal" **3 times total**, "ports and adapters" once, "aggregate root" once — all as passing asides. Nothing in `hld/` or `backend/` covers it either. [architecturediagram.ai](https://architecturediagram.ai/blog/hexagonal-architecture-diagram), [Microsoft Learn — tactical DDD](https://learn.microsoft.com/en-us/azure/architecture/microservices/model/tactical-domain-driven-design) | **P2** | 800 |
| `lld/pattern_comparisons/Singleton_vs_DI_Container.md` | sub-file | The highest-frequency "old pattern vs modern answer" trap. Currently a one-line aside in `PatternCombinations.md`'s Anti-Combinations. | **P2** | 380 |
| `lld/pattern_comparisons/Visitor_vs_PatternMatching.md` | sub-file | `visitor.md` already argues it well; a dedicated comparison surfaces it where readers look for disambiguation. Sealed types + `switch` (JEP 441, final Java 21). | **P2** | 350 |
| `lld/anti_patterns/Magic_Numbers.md` | sub-file | **`anti_patterns.md`'s §8 Tradeoffs table has a "Magic Numbers" row** with payoff/cost/moment-it-lands analysis, positioned identically to the 10 real anti-patterns — and there is no file behind it. Either write the page or drop the row. | P2 | 350 |

### What each P0 would contain

**`system_design_problems.md` → `## 3b. The 90-Minute Machine-Coding Round.`** Format and clock (90–120 min build + 30 min defence); the deliverable is a runnable console app, not a diagram; a minute-by-minute budget (0–10 requirements & entities, 10–25 interfaces & enums, 25–70 core implementation, 70–80 demo `main` + sample run, 80–90 edge cases & README); the four graded axes (working code / clean OO decomposition / extensibility / input validation) with what each looks like when failed; the "add feature X now" live extension test; in-memory-only, no-framework, no-DB house rules; what to cut when short on time (persistence, UI, thread safety — in that order); and a worked pass/fail comparison on one existing problem.

**`oop_and_uml_foundations/`.** §1 what OOD is and how it differs from writing classes; §2 objects as responsibility-holders, the noun/verb extraction ritual; §3 encapsulation, abstraction, inheritance, polymorphism — each with a violation and fix; §4 the four class relationships (association / aggregation / composition / dependency) plus realisation and generalisation, with the diamond-vs-filled-diamond rule and a lifetime test; §5 Mermaid `classDiagram`, `sequenceDiagram`, `stateDiagram-v2` notation cheat-sheets — cardinality, visibility markers, static/abstract, stereotypes; §6 mechanics: turning a two-paragraph problem statement into entities, then interfaces, then a class diagram, worked end-to-end; §7 how the JDK models these relationships; §8 inheritance vs composition vs delegation table; §9 when a UML diagram earns its place vs when it wastes interview minutes; §10 pitfalls — modelling data instead of behaviour, aggregation/composition confusion, deep hierarchies, anaemic entities; §11 PlantUML/Mermaid/draw.io/Excalidraw; §12 18 Q&As; §13 best practices; §14 case study: model a hotel-booking domain from a paragraph.

**`concurrency_patterns/modern_java_concurrency.md`.** §1 what changed between Java 8-era concurrency and Java 25; §2 the mental model — cheap threads mean you stop pooling and start scoping; §3 principles: unbounded concurrency is still bounded by downstream, cancellation must be structural, context must be immutable; §4 virtual threads / `StructuredTaskScope` / `ScopedValue` / `Executors.newVirtualThreadPerTaskExecutor()`; §5 carrier/mount/unmount diagram and a scope-tree diagram; §6 mechanics with runnable fan-out-fan-in code, `ScopedValue.where(...).call(...)` replacing a `ThreadLocal` that leaks across pooled tasks, and the *current* pinning story (JEP 491 removed monitor pinning in Java 24; native frames still pin); §7 real usage — Tomcat/Jetty virtual-thread executors, JDBC drivers, Spring Boot 4 `spring.threads.virtual.enabled`; §8 platform vs virtual vs structured comparison table with thread-cost numbers; §9 when virtual threads do nothing for you (CPU-bound); §10 pitfalls — pooling virtual threads, `ThreadLocal` in a per-task-thread world, unbounded fan-out, `synchronized` advice that is now obsolete; §11 tools; §12 18 Q&As; §13 best practices; §14 case study: migrate the existing `ThreadPool.md` connection-pool example.

**`refactoring/refactoring.md`.** Absorbs the existing 492-line `Refactoring_to_Patterns.md` (smell→pattern table, five worked before/after refactorings, the decision heuristic, the six safety rules) and adds: Fowler *Refactoring* 2nd ed's smell catalog as a full table with the named refactoring that cures each; the mechanics of the six refactorings an interviewer actually asks you to perform live (Extract Method, Extract Class, Replace Conditional with Polymorphism, Introduce Parameter Object, Replace Constructor with Factory Method, Move Method); characterization tests and the "make the change easy, then make the easy change" sequencing; IDE-assisted vs manual; and 18 Q&As. Requires a `STUDY_ORDER` entry, a `<!-- study-paths -->` block entry, root README and section README rows.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | What is true in Aug 2026 | Source |
|---|---|---|---|---|
| `concurrency_patterns/concurrency_patterns.md` | 105, `## 6. Virtual Threads (Project Loom) Impact` | "**Caution:** `synchronized` blocks pin virtual threads to their carrier platform thread, eliminating the benefit of virtual threads. Prefer `ReentrantLock` over `synchronized` in code paths expected to run on virtual threads." | **JEP 491 (delivered in JDK 24, March 2025) eliminated monitor-based pinning.** A virtual thread blocking inside `synchronized` now releases its carrier. Only native frames (JNI, FFM upcalls) and class initialization still pin. The `ReentrantLock`-instead-of-`synchronized` advice is obsolete as blanket guidance. | [openjdk.org/jeps/491](https://openjdk.org/jeps/491) |
| `concurrency_patterns/concurrency_patterns.md` | 409 (**a `**Short:**` line** — ships as the graded-correct MCQ option) | "A virtual thread inside a synchronized block pins to its carrier thread, blocking the carrier too if I/O then blocks." | Same as above. This is the exact failure the root CLAUDE.md audit-before-Short rule warns about: a wrong answer indistinguishable from a right one, already in the question bank. | [openjdk.org/jeps/491](https://openjdk.org/jeps/491) |
| `concurrency_patterns/concurrency_patterns.md` | 411 (full answer to "What is the `synchronized` pinning problem…") | "…On Java 21, this means a `synchronized`-heavy codebase does not benefit from `newVirtualThreadPerTaskExecutor()`. Fix: replace `synchronized` with `ReentrantLock`." | Scoped to "Java 21" but never says the fix shipped in Java 24; a reader on Java 25 LTS is told to do unnecessary work. Rewrite to teach the *current* rule and keep native-frame pinning as the surviving caveat. | [openjdk.org/jeps/491](https://openjdk.org/jeps/491) |
| `concurrency_patterns/concurrency_patterns.md` | 426–427 (§13 Tradeoffs table) | "Pooling them is pointless, and `synchronized` pins a virtual thread to its carrier" / "`ReentrantLock` over `synchronized` … no carrier pinning" | "Pooling is pointless" is still right; the pinning half of both rows is not. | [openjdk.org/jeps/491](https://openjdk.org/jeps/491) |
| `concurrency_patterns/concurrency_patterns.md` | 300 (§10 war story, "Virtual thread pinning in Hibernate") | "…Fix: stay on platform threads for Hibernate-heavy paths until the library migrates to `ReentrantLock`." | Reads as live 2026 guidance. Post-JEP 491 the generic `synchronized` cause is gone; if a JDBC driver still pins it is via native frames, which is a different diagnosis and a different fix. Either date the incident explicitly to Java 21 or re-diagnose it. | [openjdk.org/jeps/491](https://openjdk.org/jeps/491) |
| `concurrency_patterns/ThreadPool.md` | 328 | "Pinning: synchronized blocks + native calls still pin virtual threads to carrier threads" | The `synchronized` half is flatly wrong for Java 24+; the native-calls half is correct. | [openjdk.org/jeps/491](https://openjdk.org/jeps/491) |
| `structural/proxy/proxy.md` | 555 | "Resilience4j and **Hystrix** implement this as a proxy layer." | Hystrix's own README: "no longer in active development, and is currently in maintenance mode"; 1.5.18 is the final release and Netflix directs new projects to Resilience4j. Naming it in the present tense alongside a live library reads as a current recommendation. | [github.com/Netflix/Hystrix](https://github.com/Netflix/Hystrix) |
| `structural/flyweight/flyweight.md` | 258–263 | "…`-XX:+UseCompactObjectHeaders` (JEP 519, a product feature since JDK 25 but opt-in)… **JEP 534 proposes** making compact headers the default in JDK 27" | The JDK-25 half is exactly right. "Proposes" is now behind: **JEP 534 is integrated into JDK 27**, which is in rampdown with GA September 2026. Change "proposes" to "makes". | [openjdk.org/jeps/534](https://openjdk.org/jeps/534) |
| `structural/proxy/proxy.md` | 453 | "…~2-3ns of overhead over a direct call (50M-call loop, **JDK 23**, 8 distinct proxy identities…)" | Not a world change — an internal inconsistency. The file frames everything else as Java 25 LTS but anchors its one benchmark to a short-lived non-LTS release. Re-run or re-label on 25. | — |
| `creational/singleton/singleton.md` | 243–244 | Flagship production scenario built on **Netflix Archaius 1.x** (`ConfigurationManager.getConfigInstance()`), with the text itself noting "the 2.x API is different… DI-oriented, no static registry" | Self-acknowledged as superseded. The scenario still teaches, but the section's best pages (`flyweight`, `proxy`, `decorator`) anchor on live libraries. Consider re-anchoring to Spring's `DefaultListableBeanFactory` singleton scope, already cited one line above. | — |

### The one internal contradiction — not a staleness item, but the most serious defect found

`system_design_problems.md` **§9 "Key Class Relationships"** draws a *different* Parking Lot class
model than `system_design_problems/ParkingLot.md` does. Not a compressed subset — a different design:

| | `system_design_problems.md` §9 (~301-358) | `ParkingLot.md` (canonical) |
|---|---|---|
| Spot enum | `SpotType { COMPACT, LARGE, MOTORCYCLE, DISABLED }` | `ParkingSpotType { SMALL, MEDIUM, LARGE }` |
| Lot class | bare `ParkingLot` (no stereotype) | `ParkingLotSystem` marked `<<Singleton>>` |
| Pricing | `HourlyPricingStrategy`, `FlatRatePricingStrategy` | `PricingStrategy` → `HourlyPricing`, `DailyPricing`, `WeekendPricing` |
| Vehicles | absent | `Vehicle` → `Motorcycle`/`Car`/`Truck` + `VehicleFactory` |
| Observer | absent | `ParkingSpotObserver` / `DisplayBoard` |
| Ticket | `entryTime`, `exitTime`, `totalCost` | `vehicle`, `spot`, `entryTime`, `status` |
| Extra | `ParkingLotController` | — |

Three of the five patterns `ParkingLot.md` builds its entire write-up around (Singleton, Factory,
Observer) do not appear in the index's diagram at all. A reader who studies §9 as the quick reference
and then opens `ParkingLot.md` for depth sees two irreconcilable designs for the section's most-asked
interview question, inside the same module. **Fix: make §9's diagram a strict subset of
`ParkingLot.md`'s, or delete it and link.** The two other diagrams duplicated this way (LRU Cache,
Vending Machine) *are* faithful compressions — this one alone diverged.

**Cross-file inconsistency worth naming:** `java/CLAUDE.md` and `java/README.md` already record JEP 491
correctly ("`synchronized` no longer pins virtual threads | Java 24 (JEP 491) | Java 25 (LTS)"). The
`lld/concurrency_patterns/` module contradicts its own repo in six places. Fix `lld/` to match `java/`.

**Verified NOT stale** (checked, no action): all Java-version claims (20 × "Java 25", contextual
"Java 21+" for sealed types / virtual threads), `jakarta.*` vs `javax.*` usage (`javax.swing`,
`javax.crypto`, `javax.net`, `javax.xml`, `javax.lang.model` are still correctly `javax`;
`jakarta.faces` is correctly noted as the JSF rename), Spring Boot 3.x/4.x version pairs,
`Cloneable` guidance, and the JDepend/SonarQube tooling claims in `Circular_Dependencies.md`.

**One nuance to preserve when fixing the pinning items:** `StructuredTaskScope` is **still a preview
API** (JEP 505 fifth preview in JDK 25, JEP 525 sixth in JDK 26, JEP 533 seventh) while `ScopedValue`
is **final in JDK 25** (JEP 506). Do not describe them as having the same maturity.

---

## 3. Reordering — SEQUENCE and TIER changes

### Sequence

| Module | Current position/tier | Proposed | Why |
|---|---|---|---|
| `oop_and_uml_foundations` | does not exist | **new position 1** | Every 2026 curriculum orders OOP → relationships → principles → UML → patterns. Today the section's first page assumes class relationships and UML notation it never teaches. |
| `solid_principles` | position **2** | **position 2** but *before* `design_principles` | **This is a live drift.** `STUDY_ORDER.lld` opens with `lld/design_principles`, but the section README's own "Phase 1 — Foundations" lists **SOLID as item 1** and DRY/KISS/YAGNI after it. The game shows one order, the README teaches another. SOLID is also the higher-frequency interview vocabulary. |
| `design_principles` | position **1** | **position 3** | Consequence of the swap above. |
| `refactoring` | orphan file at section root | **new position 9**, right after `anti_patterns` | Refactoring is the answer to anti-patterns; adjacency is the whole point. Also fixes the orphan. |
| `dependency_injection_and_testability` | does not exist | **new position 10** | Follows anti-patterns/refactoring (Service Locator is its foil) and precedes concurrency + problems, where "how would you test this" lands. |
| `architecture_patterns` | does not exist | **new position 11** | Last of the vocabulary modules, immediately before applying it. |
| `concurrency_patterns` | position 8 | position 12 (unchanged relative order) | "Now make it thread-safe" is the standard escalation *on* a problem, so keeping it immediately before `system_design_problems` is correct. No change beyond renumbering. |

### Proposed `STUDY_ORDER["lld"]`

```js
  lld: [
    "lld/oop_and_uml_foundations",
    "lld/solid_principles",
    "lld/design_principles",
    "lld/creational","lld/structural","lld/behavioral",
    "lld/pattern_comparisons",
    "lld/anti_patterns","lld/refactoring",
    "lld/dependency_injection_and_testability",
    "lld/architecture_patterns",
    "lld/concurrency_patterns","lld/system_design_problems",
  ],
```

If only one change is made, make it the `solid_principles` / `design_principles` swap — it is the
only item here that fixes an existing inconsistency rather than adding scope.

### Tiers

| Module / file | Current | Proposed | Why |
|---|---|---|---|
| `concurrency_patterns` (all 5 files) | senior only | senior **+ principal** for `concurrency_patterns.md` and `ThreadSafeSingleton.md` | Principal is judgment: which lock at what contention cost, when not to share state, when virtual threads buy nothing. Excluding concurrency wholesale from principal is the weakest call in the current cut. |
| `system_design_problems` (principal) | module page only | add `RateLimiter.md` + `Splitwise.md` | The principal cut should include the two problems that are *arguments* (four rate-limiting algorithms with tradeoffs; debt-graph simplification and the deliberate Observer omission), not just the framework page. |
| `design_principles` (all 7 files) | principal only | keep, but see §4 | Defensible as "when DRY is wrong" judgment — however the 6 sub-files carry **zero questions**, so the principal path's first module currently ships 7 files and 16 questions. Fix by authoring, not by re-tiering. |
| `anti_patterns` senior cut | 3 files (`anti_patterns.md`, `GodObject.md`, `Singleton_Abuse.md`) | add `Spaghetti_Code.md`, `Copy_Paste_Programming.md` | The "how would you refactor this?" round is a senior staple and these two carry the strongest broken→fix Java in the folder. |
| `pattern_comparisons/InterviewQuestions.md` | senior + principal | unchanged | Correct — it is 50 of the module's 59 questions. |
| new `oop_and_uml_foundations` | — | **senior** (module page + all sub-files); principal: module page only | Craft, not judgment. |
| new `refactoring` | — | senior + principal (module page); principal for the smell catalog | Both cuts need it, for different reasons. |
| new `dependency_injection_and_testability` | — | senior + principal | Genuinely both. |
| new `architecture_patterns` | — | **principal only** | Boundary choice is judgment; a senior does not pick the application's shape. |

---

## 4. Depth — THIN modules and BLOAT

### The 36 zero-question pages

| Module | Files with 0 bank Q&As | Lines of prose sitting unused | Root cause | Fix |
|---|---|---|---|---|
| `design_principles` | 6 of 7 (`DRY`, `KISS`, `YAGNI`, `LawOfDemeter`, `ProgramToInterface`, `Composition_over_Inheritance`) | 1,646 | **No interview heading of any kind.** Not a rename — the section simply does not exist. | Add `## Interview Questions and Answers` (the shape the 5 SOLID sub-files already use), 15 Q&As each, harvested from the file's own Violation/Compliant/Tradeoffs sections. **+90 questions.** |
| `anti_patterns` | 10 of 11 | 4,285 | Two distinct shapes. 5 files (`GodObject`, `Singleton_Abuse`, `ServiceLocator`, `Anemic_Domain_Model`, `Golden_Hammer`) end in `## Interview Relevance` — **19 answerless prompts + 20 talking-point fragments**, all 19 genuinely good questions whose answers are already written in the same file. 5 files (`Spaghetti_Code`, `Copy_Paste_Programming`, `Lava_Flow`, `Premature_Optimization`, `Circular_Dependencies`) have no interview section at all. | **Do NOT rename `## Interview Relevance`** — owner-set, and the prompts are answerless bullets. Instead add a real `## Interview Questions with Answers` section *alongside* it in the 5 Shape-A files, and add one outright to the 5 Shape-B files. **+150 questions.** |
| `pattern_comparisons` | 7 of 13, incl. **the module page** | 2,821 | A structural split: 5 comparison files were authored from a template that ends in `## Interview Questions with Answers`; the other 4 (`ChainOfResponsibility_vs_Command`, `Command_vs_Strategy`, `Composite_vs_Decorator`, `Template_vs_Strategy`) from one that ends in "Can They Work Together?" and has no interview section. `pattern_comparisons.md`, `DecisionFlowchart.md`, `PatternCombinations.md` have none either. | Add 15 Q&As to each of the 7. **Do not redistribute `InterviewQuestions.md`'s 50** — its content is cross-cutting (plugin architecture, circuit breaker, DI container from scratch) and would need artificial ownership decisions. **+105 questions.** |
| `system_design_problems` | 12 of 13 | 4,113 | 9 problem files have no interview section. `ChessGame.md` uses `## Interview Discussion Points` — **does not match** the extractor regex `^##\s+.*interview\s+q`, so its 3 Q&As are silently lost; `LibraryManagement.md` and `VendingMachine.md` have 3 bold-Q lines each with no heading above them at all. **9 Q&As currently lost.** | Rename `## Interview Discussion Points` → `## Interview Questions with Answers` (recovers 3), add the heading in Library/Vending (recovers 6), then take all 12 to 15 Q&As. **+9 recovered, +171 new.** |
| root | `Refactoring_to_Patterns.md` | 492 | Orphan — no module, no `study-paths` entry, no `STUDY_ORDER`, invisible to the game. `--strict` passes only because the inventory guard walks module directories and this file is in none. | Promote to `lld/refactoring/refactoring.md` (§1). **+18 questions.** |

**Total available from harvest alone: ~543 new questions, nearly doubling the section's bank, with
zero new research.** This is the cheapest work in the section by an order of magnitude and should be
Wave 1.

### Thin content (as opposed to thin Q&As)

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `anti_patterns/Golden_Hammer.md` | 324 | 0 | Weakest file in the folder — shortest, no quantified war stories, mostly a comparison table. The 5 Shape-B siblings each carry 4 quantified scenarios; the 5 Shape-A files carry none. | Bring Shape-A files up to Shape-B depth: 4 quantified war stories each. |
| `anti_patterns/GodObject.md` | 382 | 0 | 2 Java blocks for the single most canonical anti-pattern in the set; no war stories. | Same. |
| `anti_patterns/Anemic_Domain_Model.md` | 408 | 0 | Accurate Fowler-2003 citation, thin fix code. | Same. |
| `concurrency_patterns/ProducerConsumer.md` | 240 | 5 | Under the 15-floor; no `LinkedTransferQueue`; no virtual-thread mention at all. | +10 Q&As, add `LinkedTransferQueue` to the variants table. |
| `concurrency_patterns/ReadWriteLock.md` | 272 | 5 | Under the floor. (Content itself is strong — `StampedLock` optimistic reads, writer starvation with the actual javadoc language.) | +10 Q&As. |
| `concurrency_patterns/ThreadPool.md` | 363 | 5 | Under the floor. `CompletableFuture` gets **one incidental mention** in the whole module; no `Semaphore`, `CountDownLatch`, `CyclicBarrier`, `Phaser` as named coordination patterns. | +10 Q&As; add a `CompletableFuture` composition subsection. |
| `concurrency_patterns/ThreadSafeSingleton.md` | 524 | 6 | Under the floor. | +9 Q&As. |
| `solid_principles/*` (5 sub-files) | 339–566 | 4–5 each | Under the 15-floor, though each is backstopped by the module page's 12. | +10 Q&As each (**+50**). |
| `concurrency_patterns/concurrency_patterns.md` | 432 | 11 | Heading numbers run **1,2,3,4,5,6,7,9,10,11,8,12,13** — `## 8. Cross-References` sits between §11 and §12. | Renumber. |

### Defects inside the otherwise-excellent GoF pages

The 23 pattern pages are the section's strength, but they are not uniform. All 11 behavioral pages
match the 18-section shape **exactly**; 10 of 12 creational/structural pages do too. The exceptions
and the content gaps:

| File | Defect | Fix |
|---|---|---|
| `creational/prototype/prototype.md` | **`## When NOT to Use` is missing entirely** (jumps §12 When to Use → §13 Real-World Examples) and **`## Comparison with Similar Patterns` is missing entirely**. §6 is retitled "Shallow Copy vs Deep Copy" in place of "How It Works", so the mechanics section is gone. 17 numbered headings, interview at §15. | Add both missing sections; restore §6 mechanics. `When NOT to Use` is the repo-wide required section and `lld/CLAUDE.md` names it explicitly ("Always include: when to use vs when NOT to use"). |
| `creational/builder/builder.md` | 19 headings. Pros/Cons/Tradeoffs/Pitfalls placed *before* When to Use / When NOT to Use (reversed vs every sibling); §14 retitled "Comparison with Factory Patterns"; an extra "§16 Java Code Snippet — Core Pattern" pushes interview to §17. | Reorder to match the 21 siblings. |
| `creational/creational.md` | **Skips `## 10.` entirely** — headings run §9 Pattern Skeletons → §11 Technologies and Tools. | Renumber. |
| `creational/builder/builder.md` | **Effective Java Item 2 is never cited by number in the page.** `lld/CLAUDE.md`'s topic table promises "Builder — …fluent API; Effective Java Item 2" — the citation lives only in `creational.md:313`. | Add the Item 2 citation to the page, or fix the CLAUDE.md claim. |
| `structural/proxy/proxy.md` | **The record/sealed × proxying gotcha is absent.** The file covers `final` methods and classes defeating CGLIB at length (:528-530) but never ties it to `record`, which is implicitly `final` and therefore unproxyable — a live 2026 trap given how much new code is records. | Add to §13 Pitfalls + one Q&A. |
| `structural/decorator/decorator.md` | `java.util.stream.Stream` — the JDK's largest decoration chain — is never mentioned. `Collections.unmodifiableList`/`synchronizedList` are, repeatedly. | Add Stream as a §14 real-world usage. |
| `behavioral/observer/observer.md` | **The same anti-pattern is written twice**: "Observer Registered but Never Unregistered (Memory Leak)" at :449-503 and "Observer Never Unregistered (Memory Leak)" at :771-820, near-identical in structure and fix. The file is 1,041 lines — the longest in the section — partly because of this. | Merge; keep the stronger of the two. |
| `behavioral/observer/observer.md` | `java.util.Observable` / `java.util.Observer` are **never mentioned at all** — not even to say they are deprecated (since Java 9). The JDK-alternatives Q&A (:962-964) lists a hand-written listener, `PropertyChangeSupport`, and `Flow`, and skips the class most readers will have been taught. | One sentence in §14 or the Q&A: deprecated since Java 9, here is why, here is what to use. |
| `structural/bridge/bridge.md` :377 vs `structural/facade/facade.md` :482-485 | **SLF4J is the flagship example for both Bridge and Facade** ("the name is literal"). Defensible — it genuinely is both — but a reader comparing the two files reads it as inconsistent categorisation. | Add one cross-referencing sentence in each acknowledging the other. |

### The 12 LLD problems — machine-coding readiness

All 12 use a bespoke **"LLD Problem Brief"** shape (Intuition → Problem Statement → Requirements →
Class Diagram → Patterns Used → Design Decisions/Tradeoffs → State/Flow → Sample Output →
Cross-Perspective: HLD → Follow-Up Extensions), neither the 14-section module template nor the
11-section case-study template. 7 of 12 carry it exactly; ATM, ChessGame, LibraryManagement,
VendingMachine and OnlineBookingSystem substitute problem-specific headings and drop `## Requirements`.

Scored 0–7 on requirements / diagram / enum+interface skeletons / complete Java **in the page** /
thread-safety / extension points / demo-main + time-box:

| Score | Problems | Missing |
|---|---|---|
| 5/7 | ParkingLot, RateLimiter, OnlineBookingSystem, LRUCache | complete implementation, demo+time-box |
| 4/7 | ElevatorSystem, LibraryManagement, ChessGame, VendingMachine, ATM, RideSharing, TicTacToe, Splitwise | + thread-safety |

**Every one of the 12 misses the same two things**: no complete implementation in the page, and no
runnable-demo / time-box guidance. Requirements, diagram, skeletons and extension points are
structural constants — the template is sound, it just stops one section short of what a
machine-coding reader needs. 8 of 12 have no single-JVM thread-safety discussion at all;
`ElevatorSystem` is the worst case, since simultaneous hall calls make it the most naturally
concurrent problem in the set.

**Depth ranking.** Strongest: `RateLimiter` (Cloudflare's 0.003%-error / 400M-request figure, Kong's
default window type, two worked numeric traces, a boxed-`Long` 24-vs-8-byte correction), `Splitwise`
(Verhoeff 2004 with DOI, correct strong-vs-weak NP-hardness distinction, a counterexample showing
greedy is *not* optimal), `OnlineBookingSystem` (dedicated double-booking section, named scale, DB
schema). Use these three as the template. Weakest and worth a depth pass: `ChessGame`, `RideSharing`,
`ATM`, `ElevatorSystem` — all 4/7, all without thread-safety, all Big-O-only where the top three
carry real numbers. `TicTacToe` also scores 4/7 but its thinness matches the problem's genuine
simplicity; do not prioritise it.

Worth preserving: `VendingMachine` and `LibraryManagement` each name a real **implementation gap** in
their own design (restock exists but nothing calls it, no `OUT_OF_STOCK → IDLE` path; `RESERVED` is a
dead-end state with no claim/expiry logic). That honesty is a strength — keep it when deepening them.

Two vendor claims to spot-check on any future currency pass (plausible, unverified):
`RateLimiter.md:185` (Cloudflare/Kong figures) and `Splitwise.md:368` (Stripe integer minor units vs
PayPal Orders v2 decimal strings).

### Bloat, merges, deletions

- **`PatternCombinations.md` lines 473–486** (an 8×10 checkmark grid) duplicates `DecisionFlowchart.md`
  §4's 22-row prose combination table. Two sources of truth for the same relationships, in
  incompatible formats, guaranteed to drift. **Delete the grid**, keep `DecisionFlowchart.md` §4.
- **`pattern_comparisons.md` "Quick Selection Guide" prose checklists (165–191)** are close to
  subsumed by `DecisionFlowchart.md` §2's richer Problem-to-Pattern table. Trim to a pointer; keep the
  Mermaid tree as an index teaser.
- **`anti_patterns.md` §8 Tradeoffs, "Magic Numbers" row (line 114)** promises a topic with no page
  and no Q&A behind it. Write `Magic_Numbers.md` or drop the row.
- **No module should be deleted.** Nothing here is redundant with a sibling in a way that merging fixes.

### The `.java` discovery

**16 complete, runnable single-file implementations totalling 8,082 lines** live beside the problem
and concurrency pages (`ChessGame.java` 741, `ATM.java` 679, `Splitwise.java` 611, `RideSharing.java`
589, …) — every one longer than the `.md` that should be presenting it. **Exactly one `.md` in the
entire section references any `.java` file** (`solid_principles.md` → `SolidExamples.java`). The
game's reader only intercepts `.md` links (`a.md-link`), so a link would not open in the app anyway.

**Spot-checked for drift: there is none.** `ParkingLot.java` (471), `RateLimiter.java` (467) and
`Splitwise.java` (611) were each read in full against their `.md`. Every one is a legal single-file
program — one `public class` matching the filename, package-private siblings, a working
`public static void main`. And the prose is a faithful distillation, not a divergent rewrite:
`ParkingLotSystem.getInstance(name, strategy)` reproduces exactly the "second call silently ignores
both arguments" sharp edge the page calls out; `TokenBucketRateLimiter.refill()` implements the exact
`tokensToAdd = elapsedMs/1000.0 * refillRate` formula the page walks through numerically;
`DebtSimplifier.simplify()` implements the exact max-heap greedy loop the page's flowchart describes.

So this is a **discoverability** problem, not a content-drift problem, and that makes the cheap fix
legitimate: **one link line per page** ("Full runnable implementation: `ParkingLot.java`") closes the
missing-implementation gap on all 12 problems for the cost of 12 lines — do this first. The fuller
move, folding each implementation in as a §Implementation section, is worth doing afterwards because
it also makes the code visible inside the game reader (which only intercepts `.md` links), but it is
no longer urgent once the pointer exists.

---

## 5. Cross-section notes

- **`java/` already has the correct JEP 491 fact** (`java/CLAUDE.md`, `java/README.md:396`) that
  `lld/concurrency_patterns/` contradicts in six places. Fix `lld/`; do not touch `java/`.
- **`java/structured_concurrency_and_loom/`** is the deep home for virtual threads, `StructuredTaskScope`,
  `ScopedValue`. The proposed `lld/concurrency_patterns/modern_java_concurrency.md` should be the
  *design-pattern* view and cross-link there rather than duplicating internals. Add the reciprocal
  link to `lld/CLAUDE.md`'s Cross-Reference Map.
- **`java/design_patterns_in_java/`** (893 lines, 16 Q&As, proper 14-section template) overlaps `lld/`
  by subject but not by angle — it teaches how the JDK itself uses patterns. Not a merge. Add an
  explicit cross-link both ways so a reader knows which to open.
- **`spring/dependency_injection/` and `spring/ioc_container/`** cover framework DI. The proposed
  `lld/dependency_injection_and_testability/` must be framework-agnostic (constructor vs setter vs
  field, DI without a container, DI vs Service Locator, seams) and cross-link to Spring rather than
  restate it.
- **`java/testing_junit_mockito/`** covers the tooling. The LLD angle is *design for testability* —
  test-double taxonomy, humble object, seams. Cross-link, do not duplicate.
- **No section in the repo owns OOP/UML fundamentals.** Verified: no `oop*`/`uml*`/`object_oriented*`
  directory in any of the 16 sections, and `cs_fundamentals/` has no OOP module. `lld/` is the right
  home.
- **No section owns hexagonal / ports-and-adapters / clean architecture / DDD tactical patterns.**
  `hld/` and `backend/` both have `event_sourcing_cqrs` but nothing on application-layer boundaries.
  If `lld/architecture_patterns` is judged too architectural for LLD, `hld/` is the alternative home —
  but the *tactical* half (entity / value object / aggregate) is class-level and belongs here.
- **`lld/case_studies/case_studies.md` is good** — all 5 required index sections present, with a
  Cross-Cutting Pattern Matrix that honestly documents *omissions* ("two patterns interviewers expect
  appear in none of the twelve"). Two follow-ups: its Interview-Prep table maps "Design a notification
  system" → Library Management and "Design a food delivery order lifecycle" → ATM + Booking, both of
  which the proposed new problems would replace; and its Cross-References table renders four display
  labels as bare directories (`[../behavioral/state/](../behavioral/state/state.md)`) — targets are
  correct, labels are cosmetically wrong.

---

## 6. Execution plan

**Wave 1 — Harvest (no new research; ~543 questions; fully parallelisable across 5 agents).**

1. `design_principles/` — add `## Interview Questions and Answers` + 15 Q&As with `**Short:**` lines to
   6 sub-files. Harvest from each file's own Violation/Compliant/Tradeoffs prose. *(6 files, ~1.5 days)*
2. `anti_patterns/` — add `## Interview Questions with Answers` + 15 Q&As to all 10 sub-files. Leave
   `## Interview Relevance` untouched and place the new section alongside it in the 5 Shape-A files.
   *(10 files, ~2.5 days)*
3. `pattern_comparisons/` — add 15 Q&As to the 7 zero-Q&A files including the module page. *(7 files, ~2 days)*
4. `system_design_problems/` — rename `ChessGame.md`'s `## Interview Discussion Points` →
   `## Interview Questions with Answers`; add the heading in `LibraryManagement.md` and
   `VendingMachine.md`; then take all 12 problems to 15 Q&As. *(12 files, ~3 days)*
5. `solid_principles/` (+10 each) and `concurrency_patterns/` sub-files (+9–10 each) to clear the
   15-floor. *(9 files, ~2 days)*

   Runs 1–5 are independent. **Nothing here touches `STUDY_ORDER` or `<!-- study-paths -->`.**
   Verify each with `python3 game/extract.py` — `lld` should climb from 571 toward ~1,110.

**Wave 2 — Corrections (small, must precede any re-Short of the touched answers).**

6. Fix all six JEP 491 pinning locations in `concurrency_patterns.md` (105, 300, 409, 411, 426, 427)
   and `ThreadPool.md` (328). **Line 409 is a `**Short:**` line — update it in the same edit as its
   answer.** Preserve the surviving native-frame caveat. *(0.5 day)*
7. `structural/proxy/proxy.md:555` — drop Hystrix or mark it as the historical predecessor. *(10 min)*
8. Renumber `concurrency_patterns.md`'s out-of-order `## 8. Cross-References`. *(10 min)*
9. Delete `PatternCombinations.md`'s duplicate matrix (473–486); trim `pattern_comparisons.md`'s Quick
   Selection Guide checklists; resolve the `anti_patterns.md` "Magic Numbers" phantom row. *(0.5 day)*
10. Fix the four bare-directory link labels in `case_studies/case_studies.md`. *(10 min)*
10a. **Reconcile `system_design_problems.md` §9's Parking Lot class diagram with `ParkingLot.md`'s**
    (see §2) — make it a strict subset or delete it and link. The LRU Cache and Vending Machine
    diagrams in the same section are faithful compressions and need no change. Then add the 16
    one-line `.java` pointers. *(0.5 day)*
10b. The GoF-page defects table in §4: restore `prototype.md`'s missing `When NOT to Use` and
    `Comparison with Similar Patterns` and its §6 mechanics; reorder `builder.md`; renumber
    `creational.md`'s skipped §10; add the record-is-final/CGLIB trap to `proxy.md`; add `Stream` to
    `decorator.md`; merge `observer.md`'s duplicated memory-leak anti-pattern and add the
    `java.util.Observable` deprecation sentence; cross-reference the SLF4J Bridge/Facade split;
    fix `flyweight.md`'s "JEP 534 proposes" → "makes"; re-label `proxy.md:453`'s JDK 23 benchmark;
    re-anchor `singleton.md`'s Archaius 1.x scenario. *(1.5 days)*

**Wave 3 — Structure (touches `STUDY_ORDER`, `<!-- study-paths -->`, both READMEs).**

11. Swap `lld/solid_principles` before `lld/design_principles` in `STUDY_ORDER.lld`; confirm the
    section README's Phase 1 already matches. Run `python3 game/extract.py --write-paths` then
    `scripts/build_banks.sh`. *(0.5 day)*
12. Promote `Refactoring_to_Patterns.md` → `lld/refactoring/refactoring.md`, add Fowler's smell
    catalog + 18 Q&As, wire `STUDY_ORDER`, `<!-- study-paths -->`, section README, root README,
    `lld/CLAUDE.md`. *(2 days)*
13. Tier moves from §3: `concurrency_patterns` → principal (2 files); `RateLimiter.md` +
    `Splitwise.md` → principal; `Spaghetti_Code.md` + `Copy_Paste_Programming.md` → senior. All are
    edits to the `<!-- study-paths -->` block followed by `--write-paths`. *(0.5 day)*

**Wave 4 — New content (parallelisable; each needs `STUDY_ORDER` + `study-paths` + both READMEs).**

14. `system_design_problems.md` → `## 3b. The 90-Minute Machine-Coding Round`. *(1 day)*
15. Fold the 16 `.java` companions into their `.md` pages as §Implementation. *(3 days)* —
    but do the **one-line pointer version first, in Wave 2** (*30 min for all 16*): it closes the
    missing-implementation gap on all 12 problems immediately, and spot-checking confirmed the code
    and prose do not contradict each other.
16. `lld/oop_and_uml_foundations/` — new module, 18 Q&As. *(3 days)*
17. `lld/concurrency_patterns/modern_java_concurrency.md` — new sub-file, 18 Q&As. *(2 days)*
18. `lld/dependency_injection_and_testability/` — new module. *(3 days)*
19. New problems, in priority order: `LoggingFramework`, `NotificationService`, `TaskScheduler`, then
    `SnakeAndLadder`, `FoodDelivery`, `OrderMatchingEngine`. Each needs a row in the section README
    table, `<!-- study-paths -->`, and **`case_studies/case_studies.md`** (all five of its sections —
    same commit, no exceptions). *(1.5 days each)*
20. `lld/architecture_patterns/` and the two new `pattern_comparisons/` files. *(4 days)*

**Effort total: ~11 days Wave 1, ~3 days Wave 2, ~3 days Wave 3, ~22 days Wave 4 ≈ 39 engineer-days.**
Waves 1 and 2 alone (≈14 days) capture most of the value: they roughly double the question bank and
remove the only verified wrong answer in the section. Wave 4 item 15 (folding the 16 `.java`
companions into their pages) is the highest value-per-hour item outside Wave 1 and can be pulled
forward if capacity allows.

**Files that must be updated together whenever content is added:** `game/app.js` `STUDY_ORDER.lld`
(new modules only — nested pattern pages and sub-files fold into their parent), the
`<!-- study-paths -->` block in `lld/README.md` (every new file, same commit — fatal both directions
under `--strict`), the section README module tables, `lld/CLAUDE.md`'s topic list and Cross-Reference
Map, the root `README.md` table, and `case_studies/case_studies.md` for any new problem. After every
batch run `python3 game/extract.py --write-paths`, then the whole of `scripts/build_banks.sh` — not
just `--strict`, which is only one of its checks.

---

## 7. Confidence and gaps

**High confidence.** Every count in §0 and §4 was measured directly (`wc -l`, `grep -c '^\*\*Q'`,
`grep -c '^\*\*Short:\*\*'`, `grep -rniE '^#{2,4} .*interview'`) and reconciled against
`python3 game/extract.py`, which reports `lld 571` against 580 authored bold-Q lines — the 9-question
delta lands exactly on `ChessGame` (3), `LibraryManagement` (3), `VendingMachine` (3), confirming the
lost-Q&A diagnosis rather than inferring it. `extract.py --strict` exits 0 today, which is how the
`Refactoring_to_Patterns.md` orphan was confirmed: the inventory guard walks module directories and a
section-root file is in none, so the failure is silent by construction.

**Verified against sources.** JEP 491 (Java 24, monitor pinning removed), JEP 506 (`ScopedValue` final
in 25) vs JEP 505/525/533 (`StructuredTaskScope` still preview), and Hystrix's maintenance-mode README
were each fetched. The interview-frequency claims come from three independent 2026 lists
(lowleveldesignmastery top-20 with company attributions, `ashishps1/awesome-low-level-design`'s 33
problems, Hello Interview's problem breakdowns) that agree on the missing set.

**What I could not verify.**

1. **The historical Q&A-loss claims.** The brief said 217 Q&As were lost to mislabeled headings and 45
   to numbered-list formatting, both fixed. The fixes **did** hold for the three GoF folders — no
   `Interview Tips`, no `Interview Answer Template`, and every `^[0-9]+\. \*\*` hit I sampled in the
   pattern pages is ordinary prose enumeration, not a question boundary. But I could not reconstruct
   the original 217/45 sets to prove nothing else was missed. The 9 losses I found are new findings,
   not residue.
2. **Whether the 5 Shape-A `## Interview Relevance` sections should keep their 19 answerless prompts**
   once real Q&As sit beside them. I recommend keeping both (the owner's ruling was about not
   *renaming*), but that is a judgment call the owner may want to make.
3. **The quantified war-story numbers** in the Shape-B anti-pattern files are labelled "illustrative
   composites, not specific incidents" in the files themselves. I did not attempt to verify them and
   do not think they need it — but if the owner wants them anchored to real postmortems, that is a
   separate pass.
4. **Whether `architecture_patterns` belongs in `lld/` or `hld/`.** I placed the tactical half here on
   the argument that entity/value-object/aggregate are class-level decisions; the ports-and-adapters
   half is genuinely borderline and the `hld/` auditor may have a competing claim.
5. **Five second-order staleness candidates I flagged but did not verify** (all low-stakes, none
   changes an answer's correctness): `chain_of_responsibility.md:639` "Jakarta EE 10 / Servlet 6.0"
   and `interpreter.md:628` "Jakarta Expression Language (EL) 5.0" may be one generation behind;
   `command.md:454,628` claims Flyway undo migrations are a paid Redgate Pro/Enterprise tier;
   `template_method.md:319-328` makes a specific API-surface claim about Spring Batch 6's
   `DefaultBatchConfiguration` hook methods; `interpreter.md:441-442` uses CVE-2022-22963 /
   CVE-2022-22950 as its only security anchors — accurate, but four years old for a 2026 reader.
   Each needs one web check before anyone edits the surrounding answer.
6. **Only 3 of the 16 `.java` companions were read in full** (`ParkingLot`, `RateLimiter`,
   `Splitwise`). All three matched their prose exactly, which is good evidence the set is in sync —
   but the other 13 were not diffed against their pages. Check them before Wave 4 item 15 folds any
   code in.
7. **Reader behaviour for `.java` links.** I inferred from `a.md-link` in `game/app.js` that only `.md`
   links are intercepted; I did not run the game to confirm. The recommendation (inline the code
   rather than link it) is correct either way, so this does not change the plan.
