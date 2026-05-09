# DRY — Don't Repeat Yourself

## Origins

Coined by Andrew Hunt and David Thomas in **"The Pragmatic Programmer" (1999)**:

> "Every piece of knowledge must have a single, unambiguous, authoritative representation within a system."

---

## Intuition

> **One-line analogy**: DRY is like maintaining one authoritative source of truth — one master spreadsheet, not a dozen copies. When the truth changes, update once; if it's duplicated, update everywhere and miss one.

**Mental model**: Every piece of business knowledge has exactly one home in your codebase. "Discount is 10% for premium users" should live in exactly one place. If it's copied in three services, changing the discount rate to 15% requires finding and updating all three — missing one is a bug. DRY isn't about code similarity; it's about knowledge duplication.

**Why it matters**: Duplication is the root cause of most maintenance nightmares. One change requires multiple edits; forgetting any one creates inconsistencies. DRY makes codebases cheaper to change and less error-prone.

**Key insight**: DRY is often misapplied — similar-looking code that encodes different concepts is NOT a violation. Three sorting functions for three different business rules that happen to look similar should stay separate; premature abstraction creates worse coupling. Test duplication WET (Write Everything Twice) first, then DRY when the duplication pattern is clear.

---

## Definition

DRY is NOT just about avoiding copy-paste. It is about avoiding duplication of **knowledge and logic**. Two pieces of code that look identical but represent different concepts are NOT a DRY violation. Two pieces of code that look different but encode the same business rule ARE a DRY violation.

The key question: **"If this business rule changes, how many places do I need to update?"**

If the answer is more than one, you have a DRY violation — regardless of whether the code looks similar.

---

## Motivation

- A single change to a business rule should require only one code change.
- Duplicated knowledge creates inconsistency bugs when one copy is updated and another is forgotten.
- Reduces the cognitive surface area of the system.

---

## Java Violation Example

Three services each define their own email validation logic:

```java
// In UserService.java
public boolean validateEmail(String email) {
    return email != null && email.contains("@") && email.endsWith(".com");
}

// In RegistrationService.java
public boolean isValidEmail(String email) {
    return email != null && email.contains("@") && email.endsWith(".com");
}

// In NotificationService.java
private boolean checkEmail(String email) {
    return email != null && email.contains("@") && email.endsWith(".com");
}
```

**The problem:** the email validation rule (what constitutes a valid email) is a single piece of business knowledge encoded in three places. When the rule needs to change (e.g., support `.org` and `.net` domains), a developer must find and update all three copies — and is likely to miss one.

---

## Compliant Example

Extract the shared knowledge into a single authoritative location:

```java
// ValidationUtils.java
public final class ValidationUtils {

    private ValidationUtils() {}

    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        // Single, authoritative email validation rule
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}

// UserService.java
public boolean registerUser(String email) {
    if (!ValidationUtils.isValidEmail(email)) {
        throw new IllegalArgumentException("Invalid email");
    }
    // ...
}

// RegistrationService.java
public void register(String email) {
    if (!ValidationUtils.isValidEmail(email)) {
        throw new IllegalArgumentException("Invalid email");
    }
    // ...
}
```

Now there is one place to update when the email rule changes.

---

## WET vs DRY

**WET** = "Write Everything Twice" (or "We Enjoy Typing") — the anti-pattern opposite of DRY.

| WET | DRY |
|-----|-----|
| Logic copied across classes | Logic centralized in one place |
| Bug fixed in one copy, forgotten in others | Fix once, everywhere benefits |
| Inconsistent behavior between copies | Consistent, single source of truth |
| Easy to write, expensive to maintain | Requires upfront abstraction discipline |

---

## Tradeoffs

**Benefits:**
- Single place to fix bugs.
- Consistent behavior guaranteed by design.
- Easier to understand the system (one canonical place for each rule).

**Costs:**
- Abstractions add indirection — developers must navigate to the abstraction to understand what's happening.
- Wrong abstractions create coupling between unrelated things just because they look alike today.
- Over-DRYing can make code harder to read and modify.

---

## When DRY is WRONG: The Danger of Premature Abstraction

**"Duplication is far cheaper than the wrong abstraction."** — Sandi Metz

Consider two pieces of code that look identical today but represent different domain concepts — for example, `calculateEmployeeTax()` and `calculateContractorTax()`. If you merge them into one `calculateTax()` function now, when the rules diverge (and they will), you'll have to untangle a shared abstraction — which is harder than having two separate functions.

**Signs you have the wrong abstraction:**
- You need to add boolean flags or `type` parameters to the shared function to handle different cases.
- Callers pass `null` for parameters they don't need.
- The abstraction has comments like "only used for X, not Y."

---

## Rule of Three

A pragmatic heuristic for when to apply DRY:

1. **First time:** just write it.
2. **Second time:** note the duplication, but leave it (premature abstraction risk is too high).
3. **Third time:** now you have enough signal to understand the real abstraction — extract it.

This prevents over-engineering while still preventing rampant duplication.

---

## Real-World Examples

- **Spring Framework:** `JdbcTemplate` eliminates duplicated boilerplate for database connection setup/teardown that every DAO would otherwise repeat.
- **Apache Commons / Guava:** utility libraries that centralize common operations so every project doesn't re-implement them.
- **Database normalization:** 3NF (Third Normal Form) is literally DRY applied to data — every fact stored exactly once.
- **CSS variables / design tokens:** a color defined once in a variable, not hardcoded in hundreds of selectors.

---

## Related Patterns

- **Template Method:** Defines a skeleton algorithm in a base class, eliminating duplication of the algorithm structure while allowing steps to vary.
- **Strategy:** Eliminates duplicated conditional logic by encapsulating each variant as an interchangeable object.
- **Single Responsibility Principle:** Each class has one reason to change — closely tied to having one authoritative place per concept.

---

## Cross-Perspective: HLD Connections

**HLD View — Where DRY Appears in Distributed Systems**

- **Shared service libraries** — Cross-cutting concerns (auth token validation, distributed tracing, structured logging, metrics emission) are extracted into shared libraries rather than duplicated per service. Every service imports the library; the logic lives in one place.
- **API gateway centralization** — Instead of each microservice implementing rate limiting, auth, and request logging independently, the API gateway centralizes these concerns. Adding a new service means it inherits these capabilities automatically — DRY at the infrastructure level.
- **Schema registry** — A schema registry (Confluent Schema Registry, AWS Glue Schema Registry) is the single source of truth for event schemas. Producers and consumers reference the registry; no service hard-codes schema definitions — preventing divergence.
- **Infrastructure modules** — Terraform modules and Helm charts reuse infrastructure definitions (VPC setup, RDS config, EKS cluster) rather than copy-pasting across environments. A fix in the module propagates everywhere.

---

## Quick Summary

| Aspect | Summary |
|--------|---------|
| Core idea | Every piece of knowledge has exactly one authoritative representation |
| Not just | Copy-paste avoidance — it is knowledge/logic duplication avoidance |
| Key question | "If this rule changes, how many places change?" |
| Danger | Wrong abstraction is worse than duplication |
| Heuristic | Rule of Three — abstract on the third occurrence |
| Opposite | WET (Write Everything Twice) |
