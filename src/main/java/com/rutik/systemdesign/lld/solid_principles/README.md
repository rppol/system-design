# SOLID Principles - Master Overview

SOLID is an acronym for five object-oriented design principles introduced by Robert C. Martin (Uncle Bob). These principles form the foundation of maintainable, scalable, and testable software design. They are not rigid rules but guidelines that help you reason about design decisions.

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

```
SRP  ──────► Keeps classes focused, making OCP and LSP easier to achieve
OCP  ──────► Requires good abstractions, reinforces DIP
LSP  ──────► Ensures polymorphism is safe, which OCP relies on
ISP  ──────► Produces narrow abstractions, which DIP depends on
DIP  ──────► Ties everything together at the architectural boundary level
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
