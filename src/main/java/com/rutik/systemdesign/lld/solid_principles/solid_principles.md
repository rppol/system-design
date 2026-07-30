# SOLID Principles - Master Overview

<!-- study-paths
senior: solid_principles.md, SingleResponsibility.md, OpenClosed.md, LiskovSubstitution.md, InterfaceSegregation.md, DependencyInversion.md
principal: solid_principles.md, OpenClosed.md, LiskovSubstitution.md, DependencyInversion.md
files this module contributes to each curated path; omit a tier to leave it out
-->
SOLID is an acronym for five object-oriented design principles collected and named by Robert C. Martin (Uncle Bob) in his 2000 paper *Design Principles and Design Patterns*. Two of the five predate that collection and are not Martin's: the Open/Closed Principle is Bertrand Meyer's (*Object-Oriented Software Construction*, 1988) and the Liskov Substitution Principle is Barbara Liskov's (OOPSLA 1987 keynote, formalized with Jeannette Wing in 1994). The mnemonic **SOLID** itself was coined by Michael Feathers around 2004, not by Martin. These principles form the foundation of maintainable, scalable, and testable software design. They are not rigid rules but guidelines that help you reason about design decisions.

---

## Intuition

> **One-line analogy**: SOLID principles are the load-bearing walls of software design — violate them and your codebase's structure slowly collapses under the weight of change.

**Mental model**: Each SOLID principle addresses a specific failure mode. SRP: a class that does too much breaks for too many reasons. OCP: modifying working code to add features introduces regressions. LSP: a subclass that breaks parent contracts silently corrupts call sites. ISP: a fat interface forces clients to depend on methods they never use. DIP: high-level policy coupled to low-level detail makes the system rigid. Together they push toward small, focused, interchangeable pieces that evolve independently.

**Why it matters**: SOLID violations are the root cause of most codebases that become hard to change. They make the difference between a codebase that welcomes new features and one that makes every change feel dangerous.

**Key insight**: SOLID principles are most useful as diagnostic tools during code review. When a change is unexpectedly hard, ask which principle is being violated — the answer usually points directly at the refactoring needed.

---

## The Five Principles at a Glance

| Principle | Full Name | One-Line Summary | Key Question |
|---|---|---|---|
| **S** | Single Responsibility Principle | A class should have only one reason to change | "What is this class responsible for?" |
| **O** | Open/Closed Principle | Open for extension, closed for modification | "Can I add behavior without touching existing code?" |
| **L** | Liskov Substitution Principle | Subtypes must be substitutable for their base types | "Can I swap this subclass in anywhere the parent is used?" |
| **I** | Interface Segregation Principle | No client should depend on methods it does not use | "Does this interface force anyone to implement irrelevant methods?" |
| **D** | Dependency Inversion Principle | Depend on abstractions, not concretions | "Does high-level policy depend on low-level detail?" |

---

## Why SOLID Matters

Without SOLID principles, codebases tend toward:
- **Rigidity**: A single change requires modifications in many places
- **Fragility**: Changing one thing breaks seemingly unrelated parts
- **Immobility**: Code cannot be reused because it is tightly coupled
- **Viscosity**: The right design is harder to implement than the wrong one
- **Needless complexity**: Abstractions added speculatively with no current benefit

SOLID principles directly counteract each of these symptoms.

---

## The Principles in Detail

### S - Single Responsibility Principle (SRP)

**Definition:** A class should have one, and only one, reason to change.

**Core idea:** A "reason to change" maps to a stakeholder or actor whose requirements drive that change. If a class serves two different actors, changes for one actor risk breaking behavior for the other.

**Files:** [SingleResponsibility.md](SingleResponsibility.md)

---

### O - Open/Closed Principle (OCP)

**Definition:** Software entities should be open for extension but closed for modification.

**Core idea:** You should be able to add new behavior to a system without altering existing, tested code. Typically achieved through polymorphism, composition, and well-defined abstractions.

**Files:** [OpenClosed.md](OpenClosed.md)

---

### L - Liskov Substitution Principle (LSP)

**Definition:** Objects of a superclass should be replaceable with objects of its subclasses without altering the correctness of the program.

**Core idea:** Inheritance should model "is-a" in the behavioral sense, not just the structural sense. A subclass must honor the contract of its parent — including preconditions, postconditions, and invariants.

**Files:** [LiskovSubstitution.md](LiskovSubstitution.md)

---

### I - Interface Segregation Principle (ISP)

**Definition:** No client should be forced to depend on methods it does not use.

**Core idea:** Fat interfaces create tight coupling between unrelated clients. Splitting interfaces into role-specific contracts keeps clients decoupled and makes implementations focused.

**Files:** [InterfaceSegregation.md](InterfaceSegregation.md)

---

### D - Dependency Inversion Principle (DIP)

**Definition:** High-level modules should not depend on low-level modules. Both should depend on abstractions. Abstractions should not depend on details; details should depend on abstractions.

**Core idea:** The direction of dependency should be inverted from what intuition suggests. Business logic should not depend on database drivers or HTTP libraries — it should depend on interfaces, and the infrastructure should depend on those same interfaces.

**Files:** [DependencyInversion.md](DependencyInversion.md)

---

## How the Principles Relate to Each Other

The five principles form a dependency chain rather than five isolated rules: SRP and ISP lay the foundation, LSP keeps polymorphism safe for OCP to build on, and OCP's abstractions feed into DIP — the architectural boundary that ties the whole chain together.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    SRP(["SRP<br/>focused classes"]) -->|"keeps classes focused"| OCP("OCP<br/>extension points")
    SRP -->|"keeps classes focused"| LSP("LSP<br/>safe polymorphism")
    LSP -->|"ensures safe polymorphism"| OCP
    ISP(["ISP<br/>narrow abstractions"]) -->|"produces narrow abstractions"| DIP("DIP<br/>inverted dependencies")
    OCP -->|"requires abstractions, reinforces"| DIP
    DIP -->|"ties everything together"| Boundary(["Architectural<br/>Boundary"])

    class SRP,ISP base
    class LSP train
    class OCP mathOp
    class DIP io
    class Boundary frozen
```

- **SRP + ISP** together prevent God objects and bloated interfaces
- **OCP + LSP** together make inheritance and polymorphism safe and extensible
- **DIP** is the architectural expression of OCP — it injects the right abstraction at the right boundary

---

## Common Anti-Patterns Each Principle Addresses

| Anti-Pattern | Violated Principle(s) |
|---|---|
| God Class / God Object | SRP |
| Long switch/if-else chains on type | OCP |
| Subclass that throws `UnsupportedOperationException` | LSP |
| Fat/God Interface | ISP |
| `new ConcreteService()` inside business logic | DIP |
| Test setup requiring real databases | DIP |
| Changing a utility class breaks unrelated features | SRP, OCP |

---

## Learning Path

Recommended order for understanding and internalizing SOLID:

1. **Start with SRP** — It is the most intuitive and sets the mindset for focused responsibilities.
2. **Move to ISP** — ISP is essentially SRP applied to interfaces; having read SRP, ISP clicks immediately.
3. **Study DIP** — Once you understand narrow interfaces (ISP), DIP shows you how to wire them.
4. **Then OCP** — OCP is the payoff of DIP. When dependencies are inverted, extension without modification becomes natural.
5. **Finish with LSP** — LSP is the most subtle. It requires understanding inheritance contracts, Liskov's formal definition, and behavioral subtyping.

---

## Code Examples

A single Java file demonstrating all five principles with before/after examples:

**File:** [SolidExamples.java](./SolidExamples.java)

---

## Quick Reference: Code Smell Checklist

Ask these questions during code review:

- [ ] Does this class have more than one reason to change? (SRP)
- [ ] Would adding a new type require me to edit this class? (OCP)
- [ ] Does any subclass throw `UnsupportedOperationException` or weaken a contract? (LSP)
- [ ] Does any class implement an interface method it does not need? (ISP)
- [ ] Does any high-level class instantiate a low-level class with `new`? (DIP)
- [ ] Are tests coupled to concrete implementations instead of interfaces? (DIP)

---

## 8. Tradeoffs — The Cost of Each Principle

SOLID is a set of heuristics, not laws, and each one has a failure mode on the far side of it. Interviewers separate candidates who have read the acronym from candidates who have paid for it by asking where each principle stops helping.

| Principle | What it buys | What over-applying it costs | The symptom |
|-----------|-------------|----------------------------|-------------|
| SRP | Each class has one reason to change, so unrelated work stops colliding | Class explosion — a single behaviour spread across five files with no single place to read it | Implementing one feature means opening seven classes and none of them tells the whole story |
| OCP | New behaviour arrives as new code rather than edits to tested code | Extension points built for variation that never arrives; every one is permanent API surface | A strategy interface with exactly one implementation, three years old |
| LSP | Any subtype can be substituted without the caller checking | Deep hierarchies contorted to preserve substitutability instead of switching to composition | A base class whose contract is a list of things subclasses must not do |
| ISP | Clients depend only on the methods they use | Interface proliferation — many one-method interfaces the reader must reassemble mentally | Six interfaces implemented by the same one class |
| DIP | High-level policy is testable and independent of infrastructure | An interface per class and a wiring layer that hides what actually runs | Navigate-to-definition always lands on the interface |

The conflicts are as important as the principles. **SRP versus cohesion**: splitting until every class has one reason to change eventually separates code that always changes together, which raises the cost of every feature. **OCP versus YAGNI**: an extension point is speculative flexibility until a second implementation exists — the honest rule is to edit the class the first time and extract the seam the second. **DIP versus navigability**: an interface with one implementation buys testability and costs readability, so it is worth it at a boundary you actually stub and not worth it inside a package.

The practical stance to state in an interview: apply SRP and DIP at module and boundary level where the payoff is real, and apply OCP reactively, when a second variant proves the axis of change rather than when you guess it.

---

## 11. Technologies and Tools

SOLID violations are structural, which means much of the review can be automated. That matters because the principles are otherwise enforced by opinion, and opinion does not survive a deadline.

| Tool | Principle it helps enforce | How to use it |
|------|---------------------------|---------------|
| ArchUnit | DIP and SRP at the architectural level — no layer skipping, no concrete infrastructure types referenced from domain packages, no package cycles | Written as ordinary JUnit tests, so violations fail the build like any other test |
| SonarQube / SonarLint | SRP via cognitive complexity, class and method length, and too-many-fields rules | Gate on new code only; also flags the `UnsupportedOperationException` throw that usually signals an LSP or ISP break |
| Checkstyle | SRP via hard ceilings on class length, method length, and parameter count | The cheapest first gate; purely syntactic |
| PMD | ISP and SRP via excessive-public-count, coupling-between-objects, and god-class rules | Tune the ruleset; the defaults are noisy on real codebases |
| Error Prone | LSP-adjacent contract bugs — missing `@Override`, inconsistent `equals` and `hashCode`, and mutability escapes | Runs inside javac, so findings are compile errors |
| Spring, Guice, or Jakarta CDI | DIP in practice — constructor injection makes the abstraction the only thing a class names | Prefer constructor injection; field injection hides the dependency and defeats the point |
| Mockito with constructor injection | DIP verification — if a class is hard to test without a real database, it depends on a concrete detail | Difficulty writing the test is the signal, before any tool reports anything |
| jQAssistant or Structure101 | Dependency direction and cycles across packages and modules | Periodic architecture review rather than per-commit |
| IntelliJ IDEA refactorings — "Extract Interface", "Extract Delegate", "Replace inheritance with delegation" | Mechanical application of ISP, SRP, and composition-over-inheritance | The fastest route from a review comment to a safe change |

The limitation is the same one every static rule has: **a tool sees shape, not responsibility**. A 300-line class can be perfectly cohesive and a 30-line one can have three reasons to change. Use the reports to find candidates for a human to read, and keep suppressions with a written reason instead of relaxing the threshold until the build is quiet.

---

## 12. Interview Q&As

Questions are ordered by interview frequency: traps and gotchas first, then internal mechanics, then edge cases.

**Q: Explain SRP and give a class that violates it. How do you fix it?**
**Short:** A UserService that both persists users and sends emails violates SRP, since two unrelated changes force redeploying both concerns.

SRP: a class should have only one reason to change — meaning it serves one stakeholder. A `UserService` class that both persists users to a database AND sends a welcome email violates SRP: a change in the email template forces a recompile/redeploy of the persistence code, and a database schema change risks breaking the email path. Fix: extract `UserRepository` for persistence and `EmailService` for notification. The `UserService` orchestrates them without owning either concern.

**Q: What is a "reason to change" in SRP? Is it "one method" or "one class"?**
**Short:** A reason to change maps to a stakeholder whose requirements drive it, not to a method count.

A "reason to change" maps to a stakeholder or actor whose requirements drive that change — not to a method count. A class with 20 methods that all serve the same business domain (e.g., all manipulate `Order` state for the order management team) satisfies SRP. A class with 2 methods that serve two different stakeholders (finance and logistics) violates SRP. Uncle Bob's test: "For whom does this class change?" If the answer is two different groups of people, split the class.

**Q: How does OCP work without modifying existing code? Show a concrete Java example.**
**Short:** OCP is satisfied by adding a new PaymentProcessor implementation for Bitcoin instead of extending an if-else chain.

OCP: open for extension, closed for modification. Payment processing: instead of `if (type == CREDIT_CARD) { ... } else if (type == PAYPAL) { ... }`, define `interface PaymentProcessor { void process(Payment p); }`. Add `CreditCardProcessor` and `PayPalProcessor` as implementations. Adding Bitcoin support means adding `BitcoinProcessor` — the existing `PaymentService` is never touched. The abstraction (interface) is the extension point; the if-else chain is the closed-for-extension design.

**Q: Square extends Rectangle: why does this violate LSP? Show the broken code.**
**Short:** Square extends Rectangle violates LSP because overriding setWidth to also set height breaks the independent-setter postcondition.

LSP: subtypes must be substitutable for their base type without altering correctness. Rectangle has `setWidth(int)` and `setHeight(int)` as independent operations. Square must keep width == height, so overriding `setWidth` to also set height changes the postcondition. Broken:
```java
Rectangle r = new Square();
r.setWidth(5);
r.setHeight(4);
assert r.area() == 20; // FAILS: Square sets both to 4, area = 16
```
Fix: don't model Square as a subtype of Rectangle. Model them as separate implementations of a `Shape` interface, or use a factory that returns the right shape for the dimensions.

**Q: What is ISP and how does it prevent the "fat interface" problem?**
**Short:** ISP is satisfied by splitting a fat Animal interface into Flyable, Swimmable, and Runnable so classes depend only on methods they use.

ISP: no client should depend on methods it does not use. A `Animal` interface with `eat()`, `fly()`, `swim()`, and `run()` forces `Dog` to implement `fly()` by throwing `UnsupportedOperationException`. Fix: split into `Flyable`, `Swimmable`, `Runnable` interfaces. `Dog` implements `Swimmable` and `Runnable`. `Bird` implements `Flyable`. Each class depends only on the methods it actually uses. Benefit: adding `Drone implements Flyable` never forces a change to `Dog`, `Fish`, or any unrelated class.

**Q: How does DIP differ from Dependency Injection? Which is the principle and which is the pattern?**
**Short:** DIP is the principle that both layers should depend on abstractions; Dependency Injection is the pattern most commonly used to achieve it.

DIP is the principle: high-level modules should not depend on low-level modules; both should depend on abstractions. Dependency Injection is a pattern that implements DIP: instead of the class creating its own dependency (`new JpaUserRepository()`), the dependency is passed in (injected) from outside. Spring's `@Autowired` is a DI mechanism. You can follow DIP without a DI framework (pass dependencies via constructor); you can use a DI framework without following DIP (inject concrete classes instead of interfaces). DIP is the goal; DI is the most common way to achieve it.

**Q: Give a Spring example of DIP in action.**
**Short:** UserService depending on the UserRepository interface rather than JpaUserRepository lets Spring swap implementations freely.

`UserService` depends on `UserRepository` (an interface from Spring Data), not on `JpaUserRepository` (the concrete JPA implementation). Spring injects the concrete impl at runtime. In tests, inject `InMemoryUserRepository` instead — no changes to `UserService`. The high-level policy (`UserService`) is isolated from the low-level detail (JPA, database). Adding a MongoDB-backed `MongoUserRepository` requires zero changes to `UserService`. This is DIP enforced by the Spring container.

**Q: Which SOLID principle is violated when you add `if (type == CREDIT_CARD)` to handle a new payment type?**
**Short:** Adding a type-check branch for every new payment type violates OCP, since the existing method must be modified each time.

OCP. The existing `PaymentService.process()` method must be modified every time a new payment type is added. After 10 payment types, the method has 10 branches, each requiring a regression test, and all sharing the risk of a bug in one branch breaking the others. The correct design: `PaymentProcessor` interface + one class per payment type. Adding a new type adds a new class; `PaymentService` is never touched.

**Q: Which SOLID principle is most commonly violated in legacy codebases, and why?**
**Short:** SRP is the most commonly violated principle in legacy code because each small added responsibility feels harmless at the time.

SRP, because it's the most gradual violation. A class starts with one responsibility. A developer adds "just a small change" that introduces a second responsibility. Another adds a third. After 3 years, the class has 50 methods serving 6 different stakeholders. No single change felt wrong at the time. Detection: a class with more than one reason to appear in a sprint's JIRA tickets is likely violating SRP.

**Q: Can SOLID be over-applied? Give a concrete example of too much SOLID.**
**Short:** Yes — extracting every method into its own class in the name of SRP produces indirection with no real payoff.

Yes — over-applied SOLID produces abstraction with no payoff. Extracting every single method into its own class in the name of SRP produces a "class-per-function" antipattern: 200 single-method classes that are harder to navigate than 10 well-organized classes with 20 methods each. Similarly, creating an interface for every concrete class even when there's only one implementation (and always will be one) adds indirection without value. The test: "Does this abstraction make the code easier to change or test?" If no, it's unnecessary complexity. SOLID is a diagnostic tool, not a scoring system.

**Q: How do the five SOLID principles relate to testability?**
**Short:** All five SOLID principles reinforce testability, mainly by making it easy to inject test doubles in place of real dependencies.

SRP: small, focused classes are easy to test in isolation. OCP: extension via interface means you can inject test doubles without changing production code. LSP: if subtypes are substitutable, tests written against the base type work for all subtypes. ISP: narrow interfaces make test setup minimal — mock only the methods the class actually uses. DIP: inject dependencies via constructor, and tests can pass fakes/mocks instead of real infrastructure. Together: SOLID principles are the single biggest enabler of unit testing in object-oriented code.

**Q: What is the most subtle SOLID violation to detect in code review?**
**Short:** LSP violations are hardest to detect because the code compiles and passes tests while silently breaking substitutability.

LSP violations are the hardest to detect because they don't show up as a structural error — the code compiles and often passes unit tests. A subclass that weakens a postcondition (returns a narrower set of values than the parent promises), strengthens a precondition (requires more than the parent accepts), or throws an exception the parent never throws — all violate LSP silently. They cause failures at call sites that use polymorphism, which are often far from the class definition. Clue: a test that passes for the parent type but fails when you substitute the subclass.

---

## Recommended Further Reading

- *Clean Code* — Robert C. Martin
- *Agile Software Development: Principles, Patterns, and Practices* — Robert C. Martin
- *Design Patterns: Elements of Reusable Object-Oriented Software* — Gang of Four
- *Growing Object-Oriented Software Guided by Tests* — Freeman & Pryce

---

## Interview Preparation Summary

SOLID questions appear in virtually every senior/staff-level Java/backend interview. Key tactics:

1. **Give a definition**, then immediately back it with a concrete code example
2. **Mention the symptom** the principle cures (not just the principle itself)
3. **State a real-world analogy** — interviewers remember candidates who can explain abstractions in plain language
4. **Discuss tradeoffs** — knowing when NOT to apply a principle shows senior-level judgment
5. **Connect to design patterns** — Strategy (OCP), Factory/DI (DIP), Adapter (LSP), Role interfaces (ISP)
