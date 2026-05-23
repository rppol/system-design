# Memento Pattern

## 1. Pattern Name & Category

**Pattern:** Memento
**Category:** Behavioral
**GoF Classification:** Behavioral Design Pattern (Gang of Four)
**Also Known As:** Token, Snapshot

---

## 2. Intent

Capture and externalize an object's internal state so it can be restored later, without violating encapsulation.

---

## Intuition

> **One-line analogy**: Memento is like a save file in a video game — you capture the game state at a checkpoint, and if things go wrong, you can restore back to that exact moment.

**Mental model**: You want to add undo to an object, but the undo mechanism shouldn't break the object's encapsulation (accessing private fields). Memento creates a snapshot of an object's state that the object itself can restore from. The Originator creates Mementos and restores from them; the Caretaker stores a stack of Mementos but can never read their contents (preserving encapsulation). Undo = pop from stack, call restore.

**Why it matters**: Text editors (Ctrl+Z), graphics applications (history panel), database transactions (savepoints and rollbacks), browser history (back button) — all implement Memento semantics. The pattern cleanly separates "what state to save" (Originator knows its own internals) from "when and how to save it" (Caretaker manages the history).

**Key insight**: Memento's cost is memory — storing N states means N copies of the full object state. Incremental snapshots (store only what changed) are a common optimization but add complexity. The pattern trades memory for the ability to undo arbitrary operations.

---

## 3. Problem Statement

### The Core Problem
You have an object with complex internal state. You need to take a "snapshot" of that state at some point in time and later restore the object back to that snapshot — but you cannot expose the internal state to outside classes, because doing so would break encapsulation and couple external code to implementation details.

### Concrete Scenario
Consider a **text editor**. Users type text, apply formatting, and may want to undo changes. Naively, you might expose the editor's internal state (cursor position, text buffer, formatting metadata) to an external undo manager. But now the undo manager knows everything about the editor's internals — any change to the editor's internal structure requires updating the undo manager too. You've tightly coupled two unrelated classes.

A second scenario: a **game** that needs save points. The game character has dozens of fields: health, inventory, position, level, active quests. You want to save the game state and restore it. Exposing all of those fields externally is messy and breaks the single-responsibility principle.

### What Goes Wrong Without the Pattern
- External classes directly access and store internal fields, breaking encapsulation.
- The "history" object is tightly coupled to the originator's internals.
- Changing the originator's internal structure forces updates everywhere the state is captured.
- No clean contract for what "a saved state" means.

---

## 4. Solution

The Memento pattern introduces three roles:

1. **Originator** — the object whose state needs to be saved. It creates a Memento containing a snapshot of its current state, and it can restore itself from a Memento. Only the Originator knows how to pack/unpack its own state.

2. **Memento** — a value object that stores the snapshot. It has no behavior. Its state is opaque to everyone except the Originator.

3. **Caretaker** — manages the history of Mementos. It holds a stack (or list) of Mementos and asks the Originator to save/restore, but it never inspects the content of a Memento.

This separation preserves encapsulation: the Caretaker holds Mementos but cannot read them; only the Originator can interpret them.

---

## 5. UML Structure

```
+------------------+          creates         +--------------------+
|   Originator     |------------------------>|     Memento        |
|------------------|                          |--------------------|
| - state: State   |                          | - state: State     |
|------------------|                          |--------------------|
| + createMemento()|  <-- returns Memento     | + getState(): State|  (package-private or nested)
| + restore(m)     |  <-- accepts Memento     +--------------------+
+------------------+
         ^
         |  uses
         |
+------------------+
|   Caretaker      |
|------------------|
| - history: Stack |  <-- holds Mementos (but cannot read them)
|------------------|
| + save()         |
| + undo()         |
+------------------+
```

**Key structural insight:** The Memento's state accessor (`getState()`) should only be visible to the Originator. In Java this is typically achieved by making Memento an inner class of Originator, or by using package-private access.

---

## 6. How It Works — Step-by-Step

1. **User triggers a save action** — the Caretaker calls `originator.createMemento()`.
2. **Originator packages its state** — the Originator copies its current internal state into a new Memento object and returns it.
3. **Caretaker stores the Memento** — the Caretaker pushes the Memento onto a stack. It does not look inside.
4. **User triggers undo** — the Caretaker pops the top Memento off the stack and calls `originator.restore(memento)`.
5. **Originator restores state** — the Originator reads the state from the Memento and replaces its own state with it.
6. **Encapsulation preserved** — at no point does the Caretaker access the raw fields of the Originator.

---

## 7. Key Components

| Role | Responsibility |
|---|---|
| **Originator** | Creates Mementos from its own state; restores state from a Memento |
| **Memento** | Stores the snapshot; opaque to everyone except the Originator |
| **Caretaker** | Manages the history of Mementos; never reads Memento internals |

### Optional Variants
- **Wide vs. Narrow Interface:** Originator uses the "wide" interface (reads/writes state); Caretaker uses the "narrow" interface (just holds a reference).
- **Incremental Mementos:** Instead of storing the full state, store only the diff/delta from the previous state.
- **Serialized Mementos:** Serialize the state to JSON/bytes for persistence across sessions.

---

## 8. When to Use

- **Undo/Redo functionality** — text editors, drawing tools, IDEs (most common use case).
- **Transaction rollback** — database-like objects that need to revert to a prior state on failure.
- **Game save points** — capture full game state so the player can resume from a checkpoint.
- **Wizard-style forms** — allow users to go "back" in a multi-step form, restoring previous field values.
- **State machine snapshots** — capture a complex state machine's current configuration for debugging or replay.
- **Optimistic concurrency** — save the state before an operation, restore on conflict.
- **Configuration experiments** — allow users to try a configuration change and roll it back if they don't like it.

---

## 9. When NOT to Use

- **When state is trivially small** — if the Originator has only 1-2 fields, a simple copy constructor or cloning approach is cleaner.
- **When state changes are very frequent** — creating a Memento on every keystroke and storing all of them is wasteful. Use delta/diff storage instead.
- **When the Originator's state references mutable shared objects** — shallow copies of references will corrupt the saved state when those objects change later. Deep-copy semantics are required, which can be expensive.
- **When encapsulation doesn't matter** — if the Originator's state is already public (a plain data class/record), a simple clone is sufficient.
- **When you need a distributed snapshot** — Memento is a single-object pattern; it does not coordinate state across multiple objects. Consider the Saga pattern instead.

---

## 10. Pros

- **Preserves encapsulation** — The Originator's internal state is never exposed to external classes.
- **Simplifies the Originator** — The Originator doesn't need to manage its own history; that responsibility is cleanly delegated to the Caretaker.
- **Clean undo/redo contract** — Provides a well-defined mechanism for history management.
- **Separation of concerns** — State management (Originator), state storage (Memento), and history management (Caretaker) are fully separated.
- **Restorable to any point** — With a stack or list of Mementos, you can restore to any previous state, not just the last one.
- **Testable** — Each component (Originator, Caretaker) can be tested independently.
- **Supports branching history** — With a tree of Mementos, you can implement branching undo trees (like in Vim or Emacs).

---

## 11. Cons

- **Memory overhead** — Storing a full snapshot per save point can be expensive if state is large or snapshots are frequent.
- **Serialization complexity** — If state contains object references, deep copying is required to avoid aliasing bugs.
- **No structural sharing** — Two Mementos for similar states store everything redundantly (no copy-on-write by default).
- **Caretaker lifecycle responsibility** — The Caretaker must manage Memento lifetimes carefully; old Mementos must be discarded to avoid memory leaks.
- **Hidden complexity** — Developers unfamiliar with the pattern may be confused by the "opaque token" idiom.
- **Not thread-safe by default** — Creating a Memento while another thread modifies the Originator requires synchronization.

---

## 12. Tradeoffs

| You Gain | You Lose |
|---|---|
| Encapsulation of internal state | Memory for storing snapshots |
| Clean undo/redo mechanism | Complexity of deep-copy logic |
| Separation of history management | Performance on high-frequency saves |
| Testable, loosely coupled design | Risk of memory leaks from stale Mementos |

---

## 13. Common Pitfalls

1. **Shallow copy trap** — Creating a Memento by copying field references rather than deep-copying mutable objects. If the Originator later mutates those objects, the Memento's "snapshot" changes retroactively.

2. **Memento bloat** — Storing unlimited Mementos without a cap. Always implement a maximum history size (e.g., 50 undo steps) or use a circular buffer.

3. **Breaking encapsulation via reflection** — Making the Memento's state accessible to the Caretaker defeats the entire purpose. Use inner classes or package-private access to enforce the narrow interface.

4. **Mutable Mementos** — A Memento should be immutable after creation. Providing setters on a Memento allows the state to be corrupted.

5. **Forgetting to handle null** — The first call to undo when history is empty must be handled gracefully.

6. **Coupling Memento to Originator version** — If the Originator's internal structure changes (e.g., a field is renamed), old persisted Mementos become unrestorable. This is a versioning problem for serialized Mementos.

---

## 14. Real-World Usage

### Production Anchor: JDBC Savepoints in a multi-step order workflow

The canonical Java Memento in production is JDBC `Connection.setSavepoint()` / `rollbackToSavepoint()`. A multi-step order workflow — validate, reserve inventory, charge payment, create shipment — wraps each step in a savepoint so a failure rolls back only that step, not the entire transaction. The database is the Caretaker; the `Savepoint` handle is the Memento; the transaction's internal undo log is the snapshot.

Observed numbers in an order-processing service at 10k attempted orders/day:
- Savepoint creation: ~0.2 ms (server-side, no network round-trip on most drivers when batched).
- `rollbackToSavepoint()` p99: **< 5 ms** for steps touching < 100 rows.
- Full transaction rollback would have cost p99 ~80 ms (rebuilds entire write set).
- Without savepoints, a fraud-flag step at the tail forced full rollback + restart, doubling order latency from 220 ms to 510 ms.
- Savepoint-per-step pattern reduced retry storms by 73% during a payment-gateway flap incident.

```
+--------+     setSavepoint("validated")     +-------------------+
| Order  | -----------------------------> SP1| Transaction       |
| Flow   |     setSavepoint("reserved")  SP2 |   undo log:       |
| (Care- |     setSavepoint("charged")   SP3 |   [SP1][SP2][SP3] |
| taker) |                                   +-------------------+
|        |     fraud check fails!
|        | -- rollbackToSavepoint(SP2) -->  [SP1][SP2]
|        |                                   (charge undone; reserve kept)
+--------+
```

### Production-grade Memento (inner-class, encapsulated state)

```java
public final class Order {
    private OrderStatus status;
    private final List<LineItem> items;
    private Money charged;

    public Order() {
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();
        this.charged = Money.ZERO;
    }

    // Memento as a static inner class — only Order can read its fields.
    public static final class Snapshot {
        private final OrderStatus status;
        private final List<LineItem> items;       // deep copy
        private final Money charged;
        private Snapshot(Order o) {
            this.status  = o.status;
            this.items   = List.copyOf(o.items);  // immutable deep copy
            this.charged = o.charged;             // Money is itself immutable
        }
    }

    public Snapshot snapshot()             { return new Snapshot(this); }
    public void restore(Snapshot s) {
        this.status  = s.status;
        this.items.clear();
        this.items.addAll(s.items);
        this.charged = s.charged;
    }
}
```

```java
public final class OrderWorkflow {
    public void run(Order order, Connection conn) throws SQLException {
        conn.setAutoCommit(false);
        Savepoint reserved = null;
        Order.Snapshot domainBefore = order.snapshot();   // in-memory memento
        try {
            validate(order);
            reserved = conn.setSavepoint("RESERVED");     // JDBC memento
            inventory.reserve(order, conn);
            payment.charge(order, conn);
            if (fraud.flagged(order)) {
                conn.rollback(reserved);                  // rollback DB to SP
                order.restore(domainBefore);              // restore in-memory state
                throw new FraudException();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            order.restore(domainBefore);
            throw e;
        }
    }
}
```

### Famous Java/library usages
- `java.sql.Connection.setSavepoint()` / `rollbackToSavepoint()` — JDBC savepoint = Memento.
- `javax.swing.undo.UndoManager` + `UndoableEdit` — Swing undo stack.
- `com.fasterxml.jackson.core.JsonParser.mark()` / `reset()` — backtracking parser (note: not all parsers support; some use `JsonLocation` snapshots).
- `java.nio.ByteBuffer.mark()` / `reset()` — buffer position snapshot.
- `java.util.regex.Matcher` resettable state.
- Git commits — each commit object is a Memento of the full working-tree state; the commit DAG is the Caretaker.
- IntelliJ local history — every save creates a Memento; the IDE is the Caretaker.
- Android `Activity.onSaveInstanceState(Bundle)` — Bundle is the Memento.
- Hibernate session-level dirty checking — original entity snapshot acts as a Memento for diff generation.

### Anti-pattern 1: Shallow copy of mutable state

```java
// BROKEN: snapshot keeps a live reference to the same ArrayList instance.
// Subsequent mutations to order.items also mutate the snapshot. Rollback
// restores nothing — both states point at the SAME list.
public static final class Snapshot {
    private final List<LineItem> items;
    private Snapshot(Order o) { this.items = o.items; }   // <-- alias, not copy
}
```

```java
// FIX: deep copy at snapshot time. Use List.copyOf (Java 10+) for an
// immutable copy, or new ArrayList<>(o.items) if you need mutability.
private Snapshot(Order o) { this.items = List.copyOf(o.items); }
```

### Anti-pattern 2: Unbounded memento list -> OOM

```java
// BROKEN: every keystroke pushes a memento; a long editing session in an
// IDE-like app accumulates 200k mementos averaging 8 KB each -> 1.6 GB heap.
// We saw OOMKill at 4-hour mark during user-acceptance testing.
public final class History {
    private final List<Snapshot> stack = new ArrayList<>();
    public void push(Snapshot s) { stack.add(s); }
}
```

```java
// FIX: cap with a ring buffer; optionally compress old snapshots.
public final class History {
    private final Deque<Snapshot> stack = new ArrayDeque<>();
    private final int max;
    public History(int max) { this.max = max; }       // e.g. 100
    public void push(Snapshot s) {
        if (stack.size() == max) stack.removeFirst(); // drop oldest
        stack.addLast(s);
    }
}
// For longer history, store deltas instead of full snapshots beyond N.
```

### Anti-pattern 3: Memento exposing public state

```java
// BROKEN: any caller can mutate or read internals; encapsulation gone.
// Worse: undo no longer represents the historical state if a caller edits it.
public final class Memento {
    public OrderStatus status;                       // public mutable
    public List<LineItem> items;
}
```

```java
// FIX: Memento is a private/package-private inner class of the Originator.
// Only Originator can construct or read fields; outsiders hold an opaque token.
public final class Order {
    public static final class Snapshot {             // opaque to outsiders
        private final OrderStatus status;            // private fields
        private final List<LineItem> items;
        private Snapshot(Order o) { /* ... */ }
    }
    public Snapshot snapshot()         { return new Snapshot(this); }
    public void restore(Snapshot s)    { /* only Order touches s.* */ }
}
```

### Migration story

**Move TO Memento when**: you need undo/redo, transactional rollback at a granularity finer than the database transaction, or checkpoint/restore for long-running computations; state is small enough that snapshots are cheap (< 1 MB); the originator can be cleanly snapshotted without external side effects. We adopted savepoints + in-memory mementos after a fraud-check step at the tail of a 4-step workflow was forcing full-transaction rollback and doubling p99 latency.

**Move AWAY FROM Memento when**: snapshots become so large they dominate heap (consider Command-based undo instead — store the inverse operation, not the whole state); the originator has external side effects that snapshots cannot capture (file I/O, network); you only ever need to rollback the most recent operation (a single field-level backup is simpler). For event-sourced systems, the event log subsumes Memento entirely — replay rather than snapshot.

---

## 15. Comparison with Similar Patterns

| Pattern | Similarity | Key Difference |
|---|---|---|
| **Command** | Both support undo | Command stores the *operation* (and knows how to reverse it); Memento stores the *full state snapshot*. Use Command for fine-grained undo, Memento for coarse-grained state restore. |
| **Prototype** | Both involve copying state | Prototype clones an object for *creation* purposes; Memento clones state for *rollback* purposes. Memento adds the Caretaker role. |
| **Serialization** | Both capture state | Serialization is persistence-focused (disk/network); Memento is typically in-memory and focused on rollback. |
| **State** | Both involve "state" | The State pattern manages *behavioral state transitions*; Memento manages *data state snapshots* for rollback. |

---

## 16. Interview Tips

**Q: Explain the Memento pattern.**
A: Lead with the intent — capturing and externalizing state for rollback without breaking encapsulation. Describe the three roles (Originator, Memento, Caretaker), explain the wide vs. narrow interface insight, and give a concrete example (text editor undo).

**Q: How does Memento preserve encapsulation?**
A: The Memento stores state that only the Originator knows how to pack/unpack. The Caretaker holds Mementos as opaque tokens. In Java, making Memento an inner class of Originator is the cleanest way to enforce this — the inner class can access private fields of the outer class, but no other class can instantiate it.

**Q: What's the difference between Memento and Command for undo?**
A: Command-based undo stores the reverse operation (e.g., "delete this character" undoes "insert this character"). It's memory-efficient but requires implementing an inverse for every command. Memento-based undo stores a full state snapshot — simpler to implement but uses more memory. In practice, most editors use Command for undo since it's more granular.

**Q: What are the memory implications?**
A: Each Memento stores a full copy of state. For large objects or frequent saves, this is costly. Solutions: limit history depth, use incremental/delta Mementos, or use structural sharing (persistent data structures).

**Q: Where have you seen this pattern in the Java SDK?**
A: `javax.swing.undo.UndoManager`, Android's `onSaveInstanceState`, and conceptually in Java serialization.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Memento Appears in Distributed Systems**

- **Raft log snapshots** — Raft consensus periodically takes a Memento of the state machine (snapshot of all applied log entries) to prevent log growth. On restart or node join, the snapshot restores state without replaying the full log.
- **Database WAL checkpoints** — Write-ahead logging checkpoints are Mementos: a snapshot of the committed database state at a point in time. Recovery replays WAL entries from the last checkpoint forward, not from the beginning.
- **Saga compensating transactions** — Each saga step stores a Memento of pre-step state. On failure, the Memento enables compensation (rollback) without needing to know the internal implementation of the failed service.
- **Workflow state persistence** — Long-running workflows (Temporal, Step Functions) persist execution state as Mementos between steps. This enables pause-and-resume, crash recovery, and human-in-the-loop approval workflows across days or weeks.

---

## 17. Best Practices

1. **Make Memento immutable** — set all state in the constructor, provide only getters (ideally package-private), no setters.

2. **Use inner class to enforce narrow interface** — declaring Memento as a private static inner class of Originator ensures only the Originator can read its state.

3. **Cap history depth** — use a `Deque` with a max size in the Caretaker to prevent unbounded memory growth.

4. **Deep copy all mutable references** — Collections, arrays, and other mutable objects must be deep-copied, not reference-copied.

5. **Consider serialization for persistence** — if Mementos need to survive process restarts (game saves, document recovery), implement `Serializable` on the Memento.

6. **Name Mementos descriptively** — if supporting labeled snapshots ("before bulk edit", "version 2.0"), add a timestamp or label to the Memento.

7. **Log Memento creation in debug mode** — helps diagnose memory issues and understand save frequency.

8. **Test with undo-redo cycles** — ensure that save → modify → restore → modify → restore works correctly, especially with shared references.
