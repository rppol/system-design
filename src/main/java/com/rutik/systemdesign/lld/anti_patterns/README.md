# Anti-Patterns in Low-Level Design

<!-- study-paths
senior: README.md, GodObject.md, Singleton_Abuse.md
principal: README.md, Golden_Hammer.md, Premature_Optimization.md, ServiceLocator.md, Anemic_Domain_Model.md, Circular_Dependencies.md, Lava_Flow.md
files this module contributes to each curated path; omit a tier to leave it out
-->
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

## 8. Tradeoffs — Why Each Anti-Pattern Gets Adopted

An anti-pattern is not a mistake anyone makes out of ignorance; it is a decision that was locally rational and globally expensive. Naming the short-term payoff is what makes the argument against it persuasive in a code review, and it is what an interviewer is listening for.

| Anti-Pattern | The short-term payoff that makes it attractive | What it actually costs | The moment the cost lands |
|--------------|-----------------------------------------------|------------------------|---------------------------|
| God Object | One file to open; no decisions about where code belongs | Every change touches the same class, so nothing can be reviewed or deployed independently | The first sprint where three people edit it at once and every pull request conflicts |
| Spaghetti Code | Fastest possible path to a working feature | Control flow that cannot be held in one head, so behaviour is discovered by running it | The first bug that cannot be reproduced from reading the code |
| Copy-Paste Programming | Zero risk to the original caller; ships today | N copies of one rule, and a fix that reaches only the copy you remembered | The audit that finds the same bug still live in four of six copies |
| Magic Numbers | No naming decision, no extra line | A value whose meaning lives only in the author's head, changed in one place out of five | A tuning change that silently applies to some call paths and not others |
| Anemic Domain Model | Fits the ORM and the DTO shape; feels like clean layering | Invariants live in services, so any new caller can construct an invalid object | The second service that forgets one of the validation steps |
| Singleton Abuse | Global access with no wiring to write | Hidden dependencies, order-dependent initialisation, and shared state across tests | The first flaky test that passes alone and fails in the suite |
| Service Locator | Dependencies resolvable from anywhere; no constructor churn | Dependencies invisible in the signature, so breakage appears at runtime not compile time | A refactor that removes a registration and fails in production, not in the build |
| Circular Dependencies | The quickest way to reach a method you already have | Neither module can be understood, tested, or extracted alone | The first attempt to split the module or reuse half of it |
| Premature Optimization | Feels responsible; produces visible cleverness | Complexity paid for up front against a bottleneck that was never measured | Profiling shows the hot path was somewhere else entirely |
| Golden Hammer | Reuses expertise the team already has; no learning curve | A tool bent far past its fit, with the mismatch handled by ever-growing workarounds | The workaround layer becomes larger than the problem it wraps |
| Lava Flow | Deleting unknown code feels riskier than leaving it | Dead code that still has to be read, compiled, scanned, and reasoned about | Every subsequent change budget includes time spent on code that never runs |

The counter-argument that works is always the same shape: accept that the payoff was real, then price the cost in the currency the team feels — review time, deploy coupling, flaky tests, or incident minutes. "This violates SRP" moves nobody; "any change to the email template redeploys the payment path" moves everybody.

---

## 11. Technologies and Tools

Anti-patterns are the one design topic with genuine automated detection, because most of them have a measurable structural signature. Wire the checks into the build so the discussion happens on the pull request rather than at the retrospective.

| Tool | What it detects here | How to use it |
|------|---------------------|---------------|
| SonarQube / SonarLint | Cognitive and cyclomatic complexity, duplicated blocks, magic numbers, god classes (too many methods or fields) | Set a quality gate on **new code** only — a gate over the whole legacy codebase gets muted on day one |
| ArchUnit | Circular package dependencies, layer violations, and forbidden access (for example a controller reaching a repository directly) | Plain JUnit tests, so the rules run in the normal build and fail like any other test |
| PMD | Long methods, long parameter lists, excessive coupling, unused code (Lava Flow) | Ruleset tuned per project; the default set is noisy |
| SpotBugs (with the FindSecBugs plugin) | Bytecode-level bug patterns, including mutable static state behind Singleton Abuse | Run on the compiled artifact, not the source |
| Checkstyle | Magic numbers, class and method length, nesting depth | The cheapest gate to add first; purely syntactic and fast |
| Error Prone | Compile-time detection of a large catalogue of real bug patterns, with suggested fixes | Attaches to javac, so violations are compile errors rather than a separate report |
| jQAssistant or Structure101 | Package and module dependency graphs, cycle detection, and drift from the intended architecture | Periodic architecture review rather than per-commit |
| JaCoCo | Coverage as a proxy signal — an anemic domain model and a god object both tend to leave large untested regions | Read it as a hint about where invariants are untested, never as a target to game |
| IntelliJ IDEA inspections and refactorings | "Replace inheritance with delegation", "Extract delegate", "Introduce constant", plus live duplicate detection | The fastest path from detection to fix while the context is still loaded |

One caveat that matters more than the tool list: **these detect structure, not intent**. A class with 40 methods may be a legitimate facade, and a duplicated block may be two rules that only coincidentally look alike. Treat every finding as a question, and keep a documented suppression with a reason rather than lowering the threshold until the report is empty.

---

## 12. Interview Q&As

Questions are ordered by interview frequency: gotchas first, then definitions, then nuanced cases.

**Q: What is the difference between a design pattern and an anti-pattern?**
**Short:** A design pattern is a proven solution to a recurring problem; an anti-pattern is a seductive but ultimately harmful one.

A design pattern is a proven solution to a recurring problem. An anti-pattern is a commonly used but ineffective or counterproductive solution — it appears reasonable in the short term but causes structural damage over time. The key distinction: anti-patterns are seductive. Every anti-pattern starts as a reasonable decision under pressure; the harm accumulates quietly. Unlike mistakes, anti-patterns are documented with both the failure mode and the documented cure ("refactored solution").

**Q: When does Singleton become an anti-pattern?**
**Short:** Singleton becomes an anti-pattern when it introduces global mutable state that hides dependencies and blocks testing.

Singleton becomes an anti-pattern when it introduces global mutable state. Problems: (1) hidden coupling — callers don't declare the dependency, making code impossible to unit-test without the real singleton; (2) untestable — you can't inject a test double without changing the production class; (3) thread-unsafe if the singleton holds mutable fields without synchronization; (4) breaks in multi-classloader environments (OSGi, application servers). Fix: manage the object's lifecycle via a DI container (Spring singleton scope) — effectively singleton, but injectable and overridable in tests.

**Q: What is the God Object antipattern and how do you identify one?**
**Short:** A God Object is a single class that knows and does too much, depended on by most of the system.

A God Object is a class that knows too much and does too much — it has dozens of responsibilities, hundreds of methods, and is depended upon by most of the system. Identification: a class where every sprint's JIRA tickets include it, where removing it would require rewriting half the system, or where it has more than one "primary noun" in its name (`UserOrderPaymentManager`). Fix: apply SRP iteratively — extract one cohesive responsibility at a time into a new class, starting with the responsibility that changes most frequently.

**Q: What is the Anemic Domain Model antipattern, and why is it harmful?**
**Short:** An Anemic Domain Model has data-only objects with getters and setters while all logic lives in service classes.

An Anemic Domain Model has domain objects that are pure data containers (getters and setters only) with all business logic living in service classes. It looks like OOP but behaves like procedural code with a thin wrapper. Harmful because: business rules are scattered across service methods that all know about (and mutate) the domain objects' internals; the domain objects have no invariants or encapsulation; testing a business rule requires setting up the entire service graph. Fix: move behavior back into the domain object — `order.cancel()`, `invoice.markPaid()` — so the object enforces its own invariants.

**Q: How does Service Locator differ from Dependency Injection, and why is Service Locator considered worse?**
**Short:** Service Locator hides dependencies behind a global lookup, while Dependency Injection makes them explicit at construction.

Service Locator is a global registry from which objects pull their dependencies by calling `locator.get(MyService.class)`. DI pushes dependencies to the object via constructor or setter. Service Locator problems: (1) dependencies are hidden — you can't tell from a class's constructor what it needs; (2) tests must configure the global registry; (3) swapping implementations requires modifying the registry. DI makes every dependency explicit, declarative, and visible at the call site. Service Locator is sometimes called "DI done wrong" — same goal, worse tradeoffs.

**Q: How do you handle Lava Flow code that nobody dares to touch?**
**Short:** Add characterization tests to capture current behavior, then remove Lava Flow code incrementally with deploys between.

Lava Flow is dead or unclear code that persists because no one is confident enough to remove it. Approach: (1) add characterization tests — tests that capture the current behavior without understanding it, so you can verify nothing breaks when you refactor; (2) use static analysis tools to identify unreachable code paths; (3) check git blame and history — if a block hasn't been touched in 3 years and has `// not sure if this is needed`, it probably isn't; (4) remove incrementally, deploying after each removal. Never delete in bulk without the safety net of tests.

**Q: What causes circular dependencies and how do you break them?**
**Short:** Circular dependencies arise from poor separation of concerns and are broken by extracting a shared module or interface.

Circular dependency: module A depends on module B, which depends on module A. Causes: poor separation of concerns (A and B know too much about each other), missing abstraction layer, or an event that should be modeled as a domain event instead of a direct call. Breaking them: (a) extract the shared behavior both A and B depend on into a new module C; (b) introduce an interface — A depends on `BInterface`, which B implements, and B depends on `AInterface`, which A implements, breaking the concrete cycle; (c) replace one direction of the dependency with an event (Observer), so B fires an event that A listens to, rather than calling A directly.

**Q: What is premature optimization and why is it harmful?**
**Short:** Premature optimization is tuning code before profiling proves a real bottleneck, often optimizing the wrong thing.

Premature optimization is spending engineering effort optimizing code before profiling has identified a real bottleneck. Harmful because: (1) you often optimize the wrong thing, because unaided intuition about where the time goes is unreliable — Knuth's own words are that "the universal experience of programmers who have been using measurement tools has been that their intuitive guesses fail"; (2) optimized code is harder to read, maintain, and extend; (3) it delays feature delivery for a performance gain that may not matter. Correct sequence: make it work → make it correct → profile → make it fast (only the proven bottlenecks). Quote Knuth in full, because the popular fragment inverts him: "We should forget about small efficiencies, say about 97% of the time: premature optimization is the root of all evil. Yet we should not pass up our opportunities in that critical 3%" (ACM Computing Surveys 6:4, Dec 1974, p.268). He is telling you to find the 3%, not to skip optimization.

**Q: How does Copy-Paste programming compound over time?**
**Short:** Copy-pasted code drifts independently, so a bug fixed in one copy silently persists in every other copy.

Each copy drifts independently: a bug fixed in one copy is not fixed in the others. A feature added in one copy is not added in the others. After 18 months: 5 slightly different versions of the same logic, each with its own unique bugs and behaviors. Developers don't know which is canonical. A "simple" change requires modifying 5 places — and typically someone finds only 3. This is the "shotgun surgery" code smell. Rule of Three: the first duplication is acceptable, the second is a warning, the third means it's time to extract an abstraction.

**Q: Golden Hammer: give a real-world engineering example and the fix?**
**Short:** Golden Hammer is reaching for one familiar tool, like Kafka, for every problem regardless of fit.

A team that knows Kafka deeply reaches for Kafka as the communication mechanism for every problem it meets. The misfits: synchronous request-response APIs (should be REST/gRPC), small config values (should be a database), in-process function calls (no messaging needed). Each use case forces the latency and operational overhead of Kafka where it adds no value. Fix: evaluate tools against requirements. Kafka excels at high-throughput async event streaming with durable replay. For synchronous request-response, use REST. For configuration, use a config store. The smell: "we use X for everything" is always a Golden Hammer.

**Q: What is Spaghetti Code and what are its telltale warning signs?**
**Short:** Spaghetti Code has control flow so tangled that no single behavior can be tested or changed in isolation.

Spaghetti Code is code whose control flow is so tangled and unstructured that no single behavior can be understood, tested, or changed in isolation. Warning signs: methods over 100-200 lines mixing validation, SQL, business logic, and notifications; nesting 4+ levels deep; boolean flow flags (`isValid`, `paymentSucceeded`) set in one branch and checked 50 lines later; cyclomatic complexity above 15; vague method names like `process()` or `handleRequest()`; and zero unit tests because there are no seams. It emerges from deadline pressure ("just make it work") compounding over iterations. Prevent it with layered separation (Controller to Service to Repository), the Extract Method refactoring whenever a comment labels a code block, and refactoring any method that grows past roughly 30 lines.

**Q: How do you decompose a God Object safely without breaking production behavior?**
**Short:** Wrap the God Object in characterization tests, then extract one cohesive responsibility at a time.

Wrap the God Object in characterization tests first, then extract one cohesive responsibility at a time, starting with the responsibility that changes most frequently. Characterization tests capture what the code currently does — without requiring anyone to understand why — so every extraction can be verified against unchanged behavior. Pull each cohesive group of methods and their fields into a focused class (`AuthenticationService`, `SubscriptionService`), keep the original class as a thin facade that delegates to the new classes while callers migrate, and deploy after each extraction rather than in one batch. This incremental strangler-style approach beats a big-bang rewrite because every intermediate state is shippable and reversible.

**Q: Which code metrics detect anti-patterns automatically, and what thresholds matter?**
**Short:** Cyclomatic complexity, fan-in/fan-out, and duplicate-code density are the metrics that surface anti-patterns automatically.

Cyclomatic complexity, method length, class fan-in/fan-out, and duplicate-code density are the metrics that surface anti-patterns before a human reads the code. Complexity above 10-15 per method flags Spaghetti Code; a class with very high fan-in (most of the system depends on it) combined with high fan-out (it depends on many collaborators) is the God Object signature; SonarQube's duplicate detection catches Copy-Paste Programming; dependency-cycle detection (ArchUnit, JDepend) catches Circular Dependencies. Enforce thresholds in CI — for example Checkstyle's CyclomaticComplexity module with max 10 — so violations fail the build. Metrics locate candidates, not verdicts: always confirm with a human read before refactoring.

**Q: How is a DI-container singleton different from the Singleton anti-pattern?**
**Short:** A DI-container singleton is injected via its constructor, while the anti-pattern enforces uniqueness through a static getInstance lookup.

A DI-scoped singleton is one instance whose lifecycle the container manages and injects, while the anti-pattern is a class enforcing its own uniqueness through a static `getInstance()` lookup. The Spring singleton-scope bean is constructed once but arrives via constructor injection — the dependency is explicit in the signature, and tests swap in a double without touching production code. `getInstance()` hides the dependency, couples every caller to the concrete class, and creates global mutable state; it also only guarantees one instance per classloader, which breaks in application servers and says nothing across service replicas. Get "one instance" from container scope, and reserve hand-rolled singletons for truly unique resources like a log-file writer.

**Q: What is cargo-cult programming and what other process anti-patterns accompany it?**
**Short:** Cargo-cult programming copies practices without understanding why they work, alongside Golden Hammer and Premature Optimization.

Cargo-cult programming is copying practices, patterns, or code without understanding why they work, hoping the form alone delivers the benefit. Examples: adopting microservices because Netflix did, at 1/1000th of the scale; wrapping every class in an interface "for testability" when no second implementation or test double will ever exist; pasting a double-checked-locking snippet without knowing what `volatile` does in it. It sits alongside the other process anti-patterns — Golden Hammer (one familiar tool for every problem) and Premature Optimization (effort spent before profiling) — all three are decisions driven by habit or fashion instead of requirements. Before adopting any practice, require a stated problem it solves in your context; "company X does it" is not a requirement.

**Q: How does Spaghetti Code relate to the Big Ball of Mud?**
**Short:** Big Ball of Mud is Spaghetti Code at system scale, caused by the same feature accretion under deadline pressure.

Big Ball of Mud is Spaghetti Code at system scale — an architecture with no discernible structure, where every module reaches into every other. The root cause is identical: feature accretion under deadline pressure without refactoring, just accumulated at a different level. At method level you get 200-line functions with tangled control flow; at system level you get services with undocumented call chains, shared database tables creating invisible coupling, and event flows nobody fully maps. The fixes rhyme too: Extract Method and layering at code level correspond to the strangler fig pattern and enforced module boundaries (ArchUnit rules, published API contracts) at system level. Audit for the mud early — the cost of restructuring grows super-linearly with every feature bolted on.
