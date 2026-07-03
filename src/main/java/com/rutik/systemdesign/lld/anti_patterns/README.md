# Anti-Patterns in Low-Level Design

Anti-patterns are recurring solutions to common problems that seem reasonable at first but cause more harm than good over time. Understanding them is as important as knowing design patterns — recognizing and avoiding them is a key skill for senior engineers and a common topic in system design and coding interviews.

---

## Intuition

> **One-line analogy**: Anti-patterns are the potholes on the road to good software — they look like shortcuts but leave you with a flat tire miles later.

**Mental model**: Every anti-pattern starts as a reasonable decision under pressure. God Object? "Let's just put it all in one place for now." Premature Optimization? "We'll need this speed eventually." Lava Flow? "Better not touch it — might break something." The danger is that each feels justified in isolation; the harm accumulates quietly until the codebase becomes hard to understand, test, or change.

**Why it matters**: Recognizing anti-patterns is a force multiplier. A developer who can name and explain why Singleton Abuse is harmful will write better code AND be a more effective code reviewer, architect, and mentor.

**Key insight**: Anti-patterns are not just bad code — they are documented failure modes with documented cures. Learning them is learning from the industry's collective mistakes rather than repeating them.

---

## What is an Anti-Pattern?

An anti-pattern is a commonly used but ineffective or counterproductive solution to a recurring design problem. Unlike mistakes, anti-patterns are seductive — they often feel like the right move in the short term.

Key characteristics:
- Appears to solve a problem but introduces new, worse problems
- Is repeated across codebases, teams, and organizations
- Has a documented, better alternative (the "refactored solution")
- Usually arises from time pressure, lack of experience, or misapplied knowledge

---

## Anti-Patterns Covered

### Categorization Table

| Anti-Pattern | Category | Core Problem | Refactored Solution |
|---|---|---|---|
| [God Object](GodObject.md) | Structural | One class does everything | SRP + decomposition |
| [Singleton Abuse](Singleton_Abuse.md) | Creational | Global state masquerading as design | Dependency Injection |
| [Service Locator](ServiceLocator.md) | Creational / DI | Hidden dependencies, global registry | Constructor Injection |
| [Anemic Domain Model](Anemic_Domain_Model.md) | Structural / DDD | Data classes with no behavior | Rich Domain Model |
| [Golden Hammer](Golden_Hammer.md) | Process | One tool for every problem | Fit-for-purpose solutions |
| [Lava Flow](Lava_Flow.md) | Maintenance | Dead code nobody touches | Continuous refactoring |
| [Spaghetti Code](Spaghetti_Code.md) | Structural | Tangled, unstructured logic | Layered architecture + SRP |
| [Copy-Paste Programming](Copy_Paste_Programming.md) | Code Quality | Code duplication | DRY + abstraction |
| [Premature Optimization](Premature_Optimization.md) | Process | Optimizing without data | Profile first, optimize later |
| [Circular Dependencies](Circular_Dependencies.md) | Structural | A depends on B depends on A | Dependency inversion |

---

## SOLID Principles Violated (Quick Reference)

Most anti-patterns violate one or more SOLID principles:

| Anti-Pattern | S | O | L | I | D |
|---|---|---|---|---|---|
| God Object | X | | | X | X |
| Singleton Abuse | X | | | | X |
| Service Locator | X | | | | X |
| Anemic Domain Model | X | | | | |
| Spaghetti Code | X | X | | | X |
| Copy-Paste Programming | X | | | | |
| Circular Dependencies | | | | | X |

Legend: S=Single Responsibility, O=Open/Closed, L=Liskov, I=Interface Segregation, D=Dependency Inversion

---

## How to Use This Documentation

1. **For code reviews**: Use these as a checklist when reviewing PRs
2. **For interviews**: Each file has an "Interview Relevance" section — read those when preparing
3. **For refactoring**: Each file includes concrete before/after Java examples
4. **For onboarding**: Share with new team members to establish shared vocabulary

---

## Common Interview Questions on Anti-Patterns

- "What is the difference between a design pattern and an anti-pattern?"
- "When does Singleton become an anti-pattern?"
- "What is the God Object and how do you fix it?"
- "What is the Anemic Domain Model and why is it considered an anti-pattern?"
- "How does Service Locator differ from Dependency Injection?"
- "What is premature optimization and why is it harmful?"

---

## Recommended Reading Order

For someone new to the topic, read in this order:

1. Spaghetti Code — the most visceral, easy to relate to
2. Copy-Paste Programming — very common, simple to understand
3. God Object — natural progression from understanding SRP
4. Anemic Domain Model — requires OOP fundamentals
5. Singleton Abuse — requires understanding of DI
6. Service Locator — follows naturally from Singleton Abuse
7. Circular Dependencies — structural / architectural concern
8. Premature Optimization — process/mindset concern
9. Golden Hammer — organizational/cultural concern
10. Lava Flow — maintenance/legacy concern

---

## 12. Interview Q&As

Questions are ordered by interview frequency: gotchas first, then definitions, then nuanced cases.

**Q: What is the difference between a design pattern and an anti-pattern?**
A design pattern is a proven solution to a recurring problem. An anti-pattern is a commonly used but ineffective or counterproductive solution — it appears reasonable in the short term but causes structural damage over time. The key distinction: anti-patterns are seductive. Every anti-pattern starts as a reasonable decision under pressure; the harm accumulates quietly. Unlike mistakes, anti-patterns are documented with both the failure mode and the documented cure ("refactored solution").

**Q: When does Singleton become an anti-pattern?**
Singleton becomes an anti-pattern when it introduces global mutable state. Problems: (1) hidden coupling — callers don't declare the dependency, making code impossible to unit-test without the real singleton; (2) untestable — you can't inject a test double without changing the production class; (3) thread-unsafe if the singleton holds mutable fields without synchronization; (4) breaks in multi-classloader environments (OSGi, application servers). Fix: manage the object's lifecycle via a DI container (Spring singleton scope) — effectively singleton, but injectable and overridable in tests.

**Q: What is the God Object antipattern and how do you identify one?**
A God Object is a class that knows too much and does too much — it has dozens of responsibilities, hundreds of methods, and is depended upon by most of the system. Identification: a class where every sprint's JIRA tickets include it, where removing it would require rewriting half the system, or where it has more than one "primary noun" in its name (`UserOrderPaymentManager`). Fix: apply SRP iteratively — extract one cohesive responsibility at a time into a new class, starting with the responsibility that changes most frequently.

**Q: What is the Anemic Domain Model antipattern, and why is it harmful?**
An Anemic Domain Model has domain objects that are pure data containers (getters and setters only) with all business logic living in service classes. It looks like OOP but behaves like procedural code with a thin wrapper. Harmful because: business rules are scattered across service methods that all know about (and mutate) the domain objects' internals; the domain objects have no invariants or encapsulation; testing a business rule requires setting up the entire service graph. Fix: move behavior back into the domain object — `order.cancel()`, `invoice.markPaid()` — so the object enforces its own invariants.

**Q: How does Service Locator differ from Dependency Injection, and why is Service Locator considered worse?**
Service Locator is a global registry from which objects pull their dependencies by calling `locator.get(MyService.class)`. DI pushes dependencies to the object via constructor or setter. Service Locator problems: (1) dependencies are hidden — you can't tell from a class's constructor what it needs; (2) tests must configure the global registry; (3) swapping implementations requires modifying the registry. DI makes every dependency explicit, declarative, and visible at the call site. Service Locator is sometimes called "DI done wrong" — same goal, worse tradeoffs.

**Q: How do you handle Lava Flow code that nobody dares to touch?**
Lava Flow is dead or unclear code that persists because no one is confident enough to remove it. Approach: (1) add characterization tests — tests that capture the current behavior without understanding it, so you can verify nothing breaks when you refactor; (2) use static analysis tools to identify unreachable code paths; (3) check git blame and history — if a block hasn't been touched in 3 years and has `// not sure if this is needed`, it probably isn't; (4) remove incrementally, deploying after each removal. Never delete in bulk without the safety net of tests.

**Q: What causes circular dependencies and how do you break them?**
Circular dependency: module A depends on module B, which depends on module A. Causes: poor separation of concerns (A and B know too much about each other), missing abstraction layer, or an event that should be modeled as a domain event instead of a direct call. Breaking them: (a) extract the shared behavior both A and B depend on into a new module C; (b) introduce an interface — A depends on `BInterface`, which B implements, and B depends on `AInterface`, which A implements, breaking the concrete cycle; (c) replace one direction of the dependency with an event (Observer), so B fires an event that A listens to, rather than calling A directly.

**Q: What is premature optimization and why is it harmful?**
Premature optimization is spending engineering effort optimizing code before profiling has identified a real bottleneck. Harmful because: (1) you almost always optimize the wrong thing (our intuitions about hotspots are wrong ~90% of the time without profiling); (2) optimized code is harder to read, maintain, and extend; (3) it delays feature delivery for a performance gain that may not matter. Correct sequence: make it work → make it correct → profile → make it fast (only the proven bottlenecks). Knuth: "Premature optimization is the root of all evil." Corollary: "Late optimization is the root of all performance fixes."

**Q: How does Copy-Paste programming compound over time?**
Each copy drifts independently: a bug fixed in one copy is not fixed in the others. A feature added in one copy is not added in the others. After 18 months: 5 slightly different versions of the same logic, each with its own unique bugs and behaviors. Developers don't know which is canonical. A "simple" change requires modifying 5 places — and typically someone finds only 3. This is the "shotgun surgery" code smell. Rule of Three: the first duplication is acceptable, the second is a warning, the third means it's time to extract an abstraction.

**Q: Golden Hammer: give a real-world engineering example and the fix.**
A team that knows Kafka deeply uses Kafka as the communication mechanism for everything: synchronous request-response APIs (should be REST/gRPC), small config values (should be a database), in-process function calls (no messaging needed). Each use case forces the latency and operational overhead of Kafka where it adds no value. Fix: evaluate tools against requirements. Kafka excels at high-throughput async event streaming with durable replay. For synchronous request-response, use REST. For configuration, use a config store. The smell: "we use X for everything" is always a Golden Hammer.
