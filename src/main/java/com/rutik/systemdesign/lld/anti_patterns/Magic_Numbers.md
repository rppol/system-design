# Anti-Pattern: Magic Numbers
## What It Is

A magic number is a literal value embedded directly in code whose meaning is not obvious from the code itself — `if (status == 3)`, `sleep(86400)`, `if (retries > 5)` — where `3`, `86400`, and `5` each stand for something the reader has to reverse-engineer or already know. The value works. The compiler has no complaint. The only thing wrong with it is that its meaning lives exactly one place: the head of whoever wrote it, on the day they wrote it.

Per this section's own [tradeoffs table](anti_patterns.md#8-tradeoffs--why-each-anti-pattern-gets-adopted): the payoff is "no naming decision, no extra line" — it is genuinely faster to type `if (age >= 18)` than to first declare `private static final int LEGAL_ADULT_AGE = 18;`. The cost is that the value's meaning "lives only in the author's head, changed in one place out of five" once it turns out the same `18` was copy-pasted into four other conditions that all meant the same rule. And the moment the cost lands is exactly when someone makes "a tuning change that silently applies to some call paths and not others" — they find and fix the number in the one file they were looking at, and the other four keep the old value with no error, no warning, and no test that catches it.

---

## Intuition

> **One-line analogy**: A magic number is a locked door with no label — you can walk through it every day without needing to know what's on the other side, right up until someone needs to change what's behind five identical-looking doors and can only find four of them.

**Mental model**: A literal value in code is either a domain concept wearing no name tag, or it is nothing at all — an implementation detail so obvious it needs no name (`arr[0]` for "the first element"). The anti-pattern is not "using a number" — every program uses numbers. It is using a number to encode a *decision* (a business rule, a threshold, a unit, a limit) without naming that decision anywhere, so the only record of what the number means is the surrounding context a reader has to reconstruct by inference.

**Why it matters**: Magic numbers are the single most automatable anti-pattern in this section — SonarQube, Checkstyle, and PMD all flag them out of the box — and yet they survive in nearly every codebase, because each individual instance looks harmless in isolation. The damage is never in one number; it is in the *set* of copies that drift apart silently.

**Key insight**: The test is not "is this a literal" but "does this literal encode a decision that could plausibly need to change, or be misread, independently of the code around it?" `0` as a loop's starting index encodes nothing — it is the definition of iteration, not a business rule. `0` as "the free tier's monthly API call limit" encodes a decision a product manager made, and it deserves a name.

---

## How to Recognize It

**Detection heuristics, in order of reliability:**

1. **The number appears twice (or more).** One occurrence might be a true one-off. Two occurrences of the same literal, especially across files, means it is a *concept* being repeated by copy-paste rather than reused by reference — and nothing enforces that the copies stay in sync.
2. **The number appears in a condition.** `if (x > 86400)`, `if (retryCount >= 5)`, `if (discountPercent < 0.15)` — a comparison against a literal is almost always encoding a business rule (a threshold, a limit, a cutoff), and business rules are exactly the kind of thing that changes, gets asked about in a code review, or gets misquoted by someone reading the code six months later.
3. **The number has a unit.** Any literal that means "seconds," "bytes," "percent," "milliseconds," or "rows" is one refactor away from meaning the *wrong* unit somewhere else, because the unit lives only in a comment or the author's memory — never in the type.

```java
// Code smell: a literal that satisfies all three heuristics at once
public boolean isSessionExpired(long lastActivityMillis) {
    return System.currentTimeMillis() - lastActivityMillis > 1800000; // appears once here...
}

public void scheduleSessionCleanup() {
    scheduler.scheduleAtFixedRate(this::purgeExpiredSessions, 0, 1800000, TimeUnit.MILLISECONDS); // ...and again here
}
```

`1800000` appears twice (heuristic 1), gates a conditional expiry check (heuristic 2), and is unitless in its own text — nothing marks it as milliseconds without reading the surrounding `TimeUnit.MILLISECONDS` argument in the second call site (heuristic 3). A reader of the first method has no such marker at all.

---

## Why It Happens

1. **It is genuinely faster to write.** Declaring a constant is one more line and one more naming decision than typing the literal inline — for a one-off value that decision feels like unnecessary ceremony.
2. **The value looks self-evident at the moment it's written.** The author just decided the timeout should be 30 seconds; of course `30` means 30 seconds, they were just looking at the ticket that said so.
3. **Copy-paste propagation.** A working conditional gets copied to a sibling method, literal and all, because copying the whole line is faster than extracting a shared constant first.
4. **No enforcement in code review.** Unlike a missing test or an obvious null-check, a bare literal does not visually signal "this might be wrong" the way a red test failure does — it has to be actively looked for.
5. **The value used to be a true one-off** and stopped being one. A single hardcoded `20` for "items per page" was fine when there was one page; it became a magic number the moment a second screen needed the same limit and someone typed `20` again instead of importing a constant.

---

## Why It's Harmful

1. **The meaning is not discoverable from the code.** A reader encountering `if (level > 7)` cannot tell whether `7` is a logging level, a permission tier, or a UI nesting depth without external context — the code does not carry its own explanation.
2. **Duplicated literals drift.** Once the same value is copy-pasted into multiple call sites, nothing guarantees they stay equal. A "fix" applied to one site silently leaves the others on the old value, and the codebase now has one rule expressed as several different numbers, each locally correct-looking.
3. **Units are invisible.** A primitive `long` or `int` carries no unit information at compile time — `long ttl = 3600` compiles identically whether `3600` means seconds or milliseconds, and the compiler will never catch the two being confused.
4. **Refactoring tools cannot help.** An IDE's "rename symbol" or "find usages" works on named identifiers; it cannot find every place `30` means "the retry timeout" versus every place `30` means "the page size," because to the tooling they are the same token.
5. **Tests encode the same ambiguity.** A test asserting `assertEquals(3600, config.getTtl())` says nothing about whether `3600` is a correct value or an accidentally-copied wrong one — the test passes either way, because the test itself has no name for what the number means.

---

## How to Fix It: The Cure Ladder

Not every magic number needs the same fix. Climb only as high as the value's actual behavior requires — over-fixing (giving every literal a full configuration entry) is its own tax.

### Rung 1: Named constant

The default fix for a value that is fixed at compile time and used more than once.

```java
// BROKEN: the literal is duplicated and unitless
public boolean isSessionExpired(long lastActivityMillis) {
    return System.currentTimeMillis() - lastActivityMillis > 1800000;
}

// FIX: one declaration, one meaning, one place to change it
private static final long SESSION_TIMEOUT_MILLIS = 30 * 60 * 1000; // 30 minutes

public boolean isSessionExpired(long lastActivityMillis) {
    return System.currentTimeMillis() - lastActivityMillis > SESSION_TIMEOUT_MILLIS;
}
```

`30 * 60 * 1000` left as an expression rather than pre-computed to `1800000` is itself a small documentation win — it shows the reader the derivation (30 minutes, in milliseconds) instead of asking them to reverse it.

### Rung 2: Enum

The right fix once the numbers represent a **fixed, closed set of named states**, not just individual thresholds.

```java
// BROKEN: magic numbers standing in for a closed set of states
public String describeOrder(int status) {
    if (status == 1) return "Placed";
    if (status == 2) return "Shipped";
    if (status == 3) return "Delivered";
    return "Unknown";
}

// FIX: the set itself is named, and each member carries its own label
public enum OrderStatus {
    PLACED("Placed"), SHIPPED("Shipped"), DELIVERED("Delivered");
    private final String label;
    OrderStatus(String label) { this.label = label; }
    public String describe() { return label; }
}
```

The enum form also converts "forgot to handle a new status" from a silent `"Unknown"` fallback into a compiler-checked exhaustive `switch` if the caller chooses one — see [`Visitor_vs_PatternMatching.md`](Visitor_vs_PatternMatching.md) for the same exhaustiveness argument applied to a full type hierarchy rather than a single enum.

### Rung 3: Configuration

The right fix once the value legitimately **varies by environment or needs to change without a redeploy** — a rate limit, a timeout, a feature threshold tuned by an operator rather than a developer.

```java
// BROKEN: the threshold is a compile-time constant that requires a redeploy to tune
private static final int MAX_REQUESTS_PER_MINUTE = 100;

// FIX: externalized, changeable without touching code
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitConfig {
    private int maxRequestsPerMinute = 100; // default, overridable per environment
    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
    public void setMaxRequestsPerMinute(int v) { this.maxRequestsPerMinute = v; }
}
```

A named constant is still the wrong fix here even though it solves the "unnamed literal" problem — the real defect is that a value operators need to tune live is compiled into the binary.

### Rung 4: Domain type

The right fix once the raw primitive **loses meaning, or loses safety, on its own** — most often when a unit can be silently confused with a different unit of the same primitive type.

```java
// BROKEN: an int has no unit; nothing stops seconds and milliseconds from mixing
public void setCacheTtl(int ttl) { this.ttlMillis = ttl; }        // caller thinks: seconds
public void configureRetryBackoff(int backoff) { schedule(backoff); } // callee expects: milliseconds

// FIX: the type itself carries and enforces the unit
public void setCacheTtl(Duration ttl) { this.ttlMillis = ttl.toMillis(); }
public void configureRetryBackoff(Duration backoff) { schedule(backoff.toMillis()); }

// A caller is now forced to be explicit — the compiler will not accept a bare int
cache.setCacheTtl(Duration.ofHours(1));       // unambiguous
retry.configureRetryBackoff(Duration.ofSeconds(2)); // unambiguous
```

`java.time.Duration` (or a hand-rolled value type for a domain-specific unit — `Money`, `Meters`, `BasisPoints`) makes the unit part of the type signature instead of a comment, so a mismatched unit becomes a compile error or an explicit, visible conversion call, not a silent runtime bug.

---

## What Is NOT a Magic Number

Treating every literal as a violation is what turns this from a real defect into a lint rule nobody respects. The following are not magic numbers, and "naming" them adds noise without adding meaning:

- **`0`, `1`, `-1` as structural values** — a loop's starting index (`for (int i = 0; ...)`), an increment step, or a sentinel return like `indexOf`'s `-1` for "not found." These encode the definition of the operation itself, not a business decision.
- **Array/collection indices tied to a well-known convention** — `arr[0]` for "the first element," `list.get(list.size() - 1)` for "the last element." The `0` and `1` here are inherent to how indexing works, not domain knowledge.
- **Mathematical or geometric constants used in their own formula** — `circumference = 2 * Math.PI * radius`, `midpoint = (low + high) / 2`. The `2` is the definition of "diameter to radius" or "average of two points," not a tunable business rule.
- **A value that is definitionally fixed by an external standard already named in context** — `Duration.ofDays(7)` inside a method literally named `weeklyReportSchedule()` does not need a `DAYS_PER_WEEK = 7` constant; the surrounding name already carries the meaning, and `7` cannot mean anything else in that expression.

The nuance that separates a real answer from a lint rule: the question is never "is this a literal," it is "could this value plausibly need to change independently of the code around it, or be misread by someone who doesn't already know what it means." A `0` used as "the free tier's monthly request limit" fails that test despite being the smallest possible number — it is a business decision wearing the most innocent-looking literal available.

---

## War Story: The Unit That Was Seconds in One Place and Milliseconds in the Other

*The scenario below is an illustrative composite of a well-known incident pattern in cache and scheduling code, not a report of one specific named public incident. The numbers show the shape of the failure.*

A payments service configured its idempotency-key cache with `long ttl = 3600;`, intending "3600 seconds — one hour," matching the business requirement that a duplicate payment retry within an hour should be rejected. The cache client's `set(key, value, ttl)` method, however, took its TTL argument in **milliseconds**, inherited from an earlier module that used the same literal, `3600`, to mean "3.6 seconds" for an unrelated rate-limiter burst window — and the payments team, skimming that file for a working example, copied the call shape without noticing the unit mismatch.

The result: every idempotency key expired after 3.6 seconds instead of one hour. For the first few weeks, this was invisible — duplicate payment retries inside a 3.6-second window (the common case, from client-side double-clicks) still deduplicated correctly, so nothing in normal testing exercised the gap between 3.6 seconds and one hour. The defect surfaced only when a client's retry logic, backing off after a slow network response, resubmitted a payment nine seconds later — well past the actual 3.6-second window, well inside the intended one-hour window — and the "duplicate" charge went through as new. The fix that shipped was one line, changing `3600` to `Duration.ofHours(1)` passed through a method that made the unit explicit; the incident review that produced that fix took longer than writing it, because the literal `3600` gave no signal, in either file, of which unit it meant.

This is precisely the moment named in the tradeoffs table at the top of this file: a tuning change (the payments team's `3600`) silently applied to only the call path it touched, while the pre-existing rate-limiter's `3600` — a different unit for the same literal — was never revisited, because nothing connected the two beyond visual similarity.

---

## Real-World Examples

- **HTTP status codes as bare integers**: `if (response.getStatus() == 404)` scattered across a codebase instead of `HttpStatus.NOT_FOUND` — the value is standardized enough that most HTTP client libraries already ship the enum, so there is rarely an excuse to hand-roll the literal.
- **Retry and backoff logic**: `Thread.sleep(1000 * attempt)` duplicated across every service that calls an external API, each copy independently deciding what "backoff" means with no shared constant or configuration.
- **Buffer and batch sizes**: `List<T> batch = new ArrayList<>(500)` repeated across ingestion pipelines, where `500` is a tuned value from a specific workload that quietly stops being correct as data shapes change, with no single place to revisit it.
- **Feature-gate thresholds**: `if (userSignupCount > 10000)` gating a rollout, hardcoded instead of read from a feature-flag service — the number is a product decision, not an implementation detail, and belongs in configuration (Rung 3) rather than source.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Magic Numbers Appear in Distributed Systems**

- **Hardcoded tuning values across replicas** — A timeout or retry-count literal baked into a service's binary means changing it requires rebuilding and redeploying every replica, rather than pushing a config update through a feature-flag or config service. This is Rung 3 of the cure ladder applied at cluster scale: the fix is the same externalization, just with a bigger blast radius when it is skipped.
- **Unit mismatches at a service boundary are worse, not just repeated** — A magic number's unit ambiguity inside one process is a bug; the same ambiguity across a network call (one service's `timeout: 3000` meaning milliseconds, the client interpreting it as seconds) is a distributed-systems incident, because there is no shared compiler or type system spanning the two processes to catch the mismatch — only a wire-format contract that has to be documented and trusted.
- **Capacity constants that silently stop matching reality** — A hardcoded `MAX_CONNECTIONS = 100` pool size tuned for one traffic profile does not adjust itself as a service scales horizontally; the HLD-level fix is autoscaling driven by measured load rather than a literal chosen once and never revisited, which is the same "value needs to be tunable without a redeploy" argument that promotes a value from Rung 1 to Rung 3.

---

## Prevention Strategies

1. **Wire a linter into the build, not just the editor.** SonarQube, Checkstyle, and PMD all detect magic numbers by default (see [`anti_patterns.md` §11](anti_patterns.md#11-technologies-and-tools)); gate new code on the rule rather than trying to retrofit an entire legacy codebase at once.
2. **Apply the three-heuristic test during review.** Does the literal repeat? Does it gate a condition? Does it carry an implicit unit? Any "yes" is worth a two-second "should this have a name?" comment.
3. **Prefer `Duration`, `Instant`, and typed value objects over raw numeric fields for anything with a unit.** This converts an entire class of unit-mismatch bugs into compile errors, which is strictly cheaper than catching them in code review.
4. **When you extract a constant, name the decision, not the value.** `TIMEOUT_30` documents nothing; `SESSION_TIMEOUT_MILLIS` documents the decision the `30` (or `1800000`) encodes.
5. **Treat a second occurrence of the same literal as a mandatory extraction trigger**, not an optional cleanup — the first occurrence might be a one-off; the second one is evidence it is a concept.

---

## Interview Questions with Answers

**Q: What makes a numeric literal a "magic number" as opposed to ordinary, harmless use of a number in code?**
**Short:** It encodes a decision or business rule with no name attached, not merely the presence of a numeral.

A magic number is specifically a literal standing in for a decision — a threshold, a limit, a unit-bearing quantity — with its meaning recoverable only by inference from context. `for (int i = 0; i < n; i++)` uses numbers but encodes no decision; `if (retries > 5)` uses the same kind of token to encode a business rule about how many retries are acceptable, and that is the one that needs a name. The test is never "is this a literal," it is "could this value plausibly need to change or be misread independently of the surrounding code."

**Q: According to this file's own tradeoffs-table framing, what is the actual moment the cost of a magic number lands?**
**Short:** When a tuning change is made and it silently applies to some call sites but not the duplicated copies elsewhere.

The short-term payoff (no naming decision, no extra line) is real and immediate. The cost is deferred until someone needs to change the value's meaning — at that point they typically find and fix only the copy they are looking at, and every other duplicate of the same literal keeps the old value with no compiler error, no test failure, and no visible signal that the codebase now disagrees with itself about what the rule is.

**Q: Why is "the number appears twice" a more reliable detection signal than "the number is not zero or one"?**
**Short:** Duplication proves the value is a repeated concept, while small-number status alone says nothing about whether it encodes a decision.

A literal appearing in exactly one place might be a genuine one-off with no risk of drifting from a duplicate, regardless of its size — `Duration.ofDays(7)` inside `weeklyReportSchedule()` is fine at any magnitude. A literal appearing twice, even a small one, has already demonstrated the exact failure mode magic numbers cause: two places now encode the same rule with no mechanism keeping them equal. Size is a weak signal; duplication is a direct, structural one.

**Q: Why does a value's "unit" matter as a detection heuristic, separately from whether it repeats or gates a condition?**
**Short:** An unlabeled numeric unit (seconds vs milliseconds, bytes vs kilobytes) can be silently misread even in a single, non-duplicated occurrence.

A raw `int` or `long` carries no unit information at the type level, so a single, non-repeated literal like `long ttl = 3600` is still dangerous on its own if the reader (or a caller passing it into a differently-unit-expecting method) assumes the wrong unit. This is a distinct failure mode from duplication-drift — it does not require a second copy to exist; it only requires one boundary where the unit assumption can be wrong.

**Q: Walk through the cure ladder — when do you stop at a named constant instead of climbing to configuration or a domain type?**
**Short:** Stop at a named constant when the value is fixed at compile time and has no unit-confusion risk across call sites.

A named constant fully solves "unnamed literal, possibly duplicated" for a value that legitimately never changes without a code change and is used the same way everywhere it appears — a page size, a maximum name length. Climb to configuration only when the value must be tunable without a redeploy (a rate limit an operator adjusts live), and climb to a domain type only when the raw primitive's unit can plausibly be confused with a different unit of the same primitive type at some call site. Applying rung 3 or 4 to a value that doesn't need it is its own cost — needless indirection for a number nobody was ever going to mistune or misread.

**Q: Why is an enum the right fix for `if (status == 1) ... else if (status == 2) ...` but not for a single timeout threshold?**
**Short:** Enum fits a closed, named set of states; a single threshold isn't a set of alternatives, it's one tunable value.

`status == 1`, `== 2`, `== 3` are members of one fixed, mutually exclusive set — exactly what an enum exists to name and enumerate, and it additionally enables compiler-checked exhaustive handling if paired with a `switch`. A timeout threshold like `1800000` is not a member of a set of alternatives at all; it is a single scalar quantity, so wrapping it in an enum would misrepresent its nature — a named constant, or configuration if it needs to be tunable, is the correct rung for that shape of value.

**Q: How does wrapping a raw `long` in `java.time.Duration` prevent the specific bug where 3600 means seconds in one place and milliseconds in another?**
**Short:** Duration forces every call site to state its unit explicitly via a factory method, so a bare unitless number can no longer compile.

`Duration.ofHours(1)` and `Duration.ofSeconds(2)` both carry their unit in the method name itself — there is no `Duration` constructor that accepts a bare, unit-ambiguous number the way `long ttl = 3600` does. A method signature that accepts `Duration` instead of `long` cannot be called with an unlabeled numeric literal at all; the caller is forced to either state a unit explicitly or convert from one `Duration` factory to another, both of which make the unit visible at the call site instead of buried in a comment or a variable name the reader has to trust.

**Q: Why doesn't `indexOf`'s `-1` sentinel count as a magic number, even though `-1` is exactly the kind of bare literal this anti-pattern targets?**
**Short:** -1 as a not-found sentinel is a structural convention defining the operation, not a business decision that could independently change.

The three-heuristic test (repeats, gates a condition, carries a unit) still technically fires on `-1` in some code, but the deeper question is whether the value encodes a decision someone made that could plausibly change — and "not found" being represented as `-1` is baked into the definition of `indexOf` itself, a convention shared across essentially all of Java's standard library. There is no future world where a team decides `indexOf` should instead return `-2` for "not found" as a tunable business choice; it is part of what the operation means, which is exactly the exclusion this file draws around structural values like loop starting indices and array bounds.

**Q: A codebase has `discountRate = 0.15` appearing in exactly one method, in exactly one condition. Is this a magic number, and does the answer depend on more than the code shown?**
**Short:** Likely yes, and the answer depends on whether 0.15 encodes a business decision that could plausibly change, which the snippet alone can't fully answer.

`0.15` in a discount condition satisfies two of the three heuristics on its own — it gates a comparison, and a percentage is inherently unit-bearing (15% versus 0.15 versus 15, three different literal encodings of the same rule) — even without a second occurrence anywhere else. Whether it truly needs a name depends on context this snippet doesn't show: if `0.15` is a marketing-defined promotional threshold that changes with campaigns, it should be at least a named constant and likely configuration; if it is, implausibly, a mathematical constant intrinsic to the calculation itself, it might be exempt the way `2` is exempt in a circumference formula. The detection heuristics flag it as worth investigating — they do not replace judgment about what the number actually represents.

**Q: How does duplicating a magic number differ, as a failure mode, from duplicating an entire method via copy-paste (Copy-Paste Programming)?**
**Short:** A duplicated literal is invisible to search and refactoring tools in a way a duplicated method is not.

Copy-Paste Programming produces a second method a code-search or "find duplicated code" tool can detect structurally — the duplicated block has shape, a name, a location IDEs can flag. A duplicated literal like `1800000` is a single token indistinguishable from every other unrelated occurrence of the number `1800000` in the codebase; there is no tool that can safely say "these five instances of `1800000` all mean the same thing and should stay in sync" without a human first giving that value a name, at which point it stops being a magic number and starts being an ordinary, searchable, refactorable identifier.

**Q: What specific limitation does a unit test have when it only asserts against the magic number itself, e.g. `assertEquals(3600, config.getTtl())`?**
**Short:** The test encodes the same ambiguity as the code — it proves the value is 3600, not that 3600 is the correct, correctly-unitized value.

A test asserting equality against a bare literal passes whether `3600` is the intended value or an accidentally-duplicated wrong one, because the test has no independent source of truth about what `3600` is supposed to mean — it merely mirrors the production code's own literal back at it. A stronger test asserts against a named constant or a `Duration`-typed expectation (`assertEquals(Duration.ofHours(1), config.getTtl())`), which at least makes the intended unit and magnitude legible in the test's failure message, even though it still cannot catch a case where both the production code and the test were written with the same wrong assumption.

**Q: Why is "no naming decision, no extra line" a real payoff worth acknowledging, rather than dismissing magic numbers as pure carelessness?**
**Short:** Acknowledging the real short-term convenience is what makes the argument against magic numbers persuasive instead of moralizing.

Per this section's general counter-argument framing, naming the genuine short-term benefit — here, that extracting a constant costs a naming decision and an extra line the author may not feel is worth it in the moment — is what makes the case against the anti-pattern land in a code review. Dismissing every magic number as simple carelessness invites the reasonable objection "it was faster and it worked"; pricing the cost instead ("the next person who needs to change this rule will only find four of the five copies") moves the conversation to a currency the team actually feels, which is the same rhetorical move this section's tradeoffs table is built around.

**Q: How does the "cure ladder" framing avoid becoming its own form of over-engineering?**
**Short:** By stopping at the lowest rung that actually addresses the value's real risk, instead of applying the heaviest fix to every literal.

The ladder is explicitly ordered so that a named constant remains the default and correct fix for most magic numbers — climbing to an enum, configuration, or a domain type is warranted only when the value specifically exhibits the property that rung addresses (a closed set of states, a need for live tunability, or unit-confusion risk). Wrapping every literal in a configuration property or a dedicated value type regardless of whether it will ever change or be misread trades one anti-pattern (unnamed literals) for another (needless indirection over values nobody was ever going to mistune), which is precisely the failure mode the ladder's ordering is designed to prevent.

**Q: What automated tools catch magic numbers before code review, and what is the practical rollout advice for adopting one on an existing codebase?**
**Short:** SonarQube, Checkstyle, and PMD all detect magic numbers by default; gate the rule on new code only, not the whole legacy codebase at once.

All three tools flag bare numeric literals structurally, without needing any project-specific configuration to start. The practical rollout advice — consistent with how this section recommends adopting any static-analysis gate — is to apply the rule only to newly written or newly touched code, since retroactively flagging every pre-existing literal across a mature codebase produces enough noise that the gate typically gets muted or disabled within the first week rather than actually changing behavior going forward.

**Q: A team replaces `if (retries > 3)` with `if (retries > THREE)`. Has the magic number been fixed?**
**Short:** No — naming a constant after its value rather than its meaning moves the literal without adding any information a reader did not already have.

`THREE` tells you the number is 3, which the literal already told you; what a reader needs is `MAX_RETRY_ATTEMPTS`, which explains why 3 and what changing it would mean. This is the most common way a mechanical fix satisfies a linter while leaving the defect intact — `Checkstyle`'s MagicNumber rule is purely syntactic and accepts any identifier, so it will pass. The test for a good constant name is whether it survives a value change: `MAX_RETRY_ATTEMPTS = 5` still reads correctly, whereas `THREE = 5` is now an outright lie in the source.

**Q: Two constants in the same class both equal 3. Should they be collapsed into one shared constant?**
**Short:** No — collapse constants only when they are the same concept, never when they merely hold the same value today.

`MAX_RETRY_ATTEMPTS = 3` and `WORKER_THREAD_COUNT = 3` are unrelated quantities that coincide; merging them into a shared `THREE_ATTEMPTS` or reusing one for the other couples two independent tuning decisions, so the day someone raises the retry budget to 5 they silently add two worker threads as well. This is the mirror image of the original anti-pattern: magic numbers hide meaning behind a value, and over-eager constant merging hides two meanings behind one name. The rule is that a constant is named for a concept, and two concepts get two constants even when the numbers are identical.
