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
