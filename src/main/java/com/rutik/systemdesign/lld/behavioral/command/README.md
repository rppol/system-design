# Command Pattern

## 1. Pattern Name & Category

**Pattern:** Command
**Category:** Behavioral (Gang of Four)
**Also Known As:** Action, Transaction, Request Object

---

## 2. Intent

Encapsulate a request as an object, thereby allowing parameterization of clients with different requests, queuing or logging of requests, and support for undoable operations.

---

## Intuition

> **One-line analogy**: Command is like a restaurant order — instead of the waiter (invoker) directly telling the kitchen (receiver) what to cook, they hand over a written order (Command object). The order can be queued, handed to a different chef, canceled, or replayed.

**Mental model**: Normally, "client calls method directly on receiver" — tight coupling, no history, no undo. Command wraps the request as an object with `execute()` and optionally `undo()`. The Invoker holds the Command; the Receiver implements the actual logic. Now requests are first-class objects: queue them, log them, replay them, undo them, serialize them for distributed processing.

**Why it matters**: Command enables undo/redo (text editors, Photoshop), transactional operations (rollback on failure), job queues (thread pool workers execute queued Commands), macro recording (record sequence of Commands), and remote procedure calls (send Command objects over network). Java's Runnable is essentially a Command without undo.

**Key insight**: The crucial enabler of undo is storing pre-execution state in the Command object (or as a Memento). Without state capture, `undo()` can't reverse the operation. This is why Command and Memento often appear together in text editors and document applications.

---

## 3. Problem Statement

### The Problem
You have objects that need to issue requests to other objects without knowing anything about the operation being requested or the receiver. You also need to:
- Support undo/redo operations
- Queue operations for deferred execution
- Log operations for audit trails or replay
- Support transactional behavior (all-or-nothing)
- Parameterize UI components (buttons, menu items) with actions

Without the Command pattern, UI elements directly call business logic methods. A "Save" button would call `document.save()` directly. This tightly couples the button to the document class. Supporting undo requires the button to know the inverse operation. Supporting macros requires the button to know about sequencing. All of this is unmanageable.

### Scenario: Text Editor
Consider a text editor with Bold, Italic, Undo, Redo, and Macro recording:
- Toolbar button "Bold" should execute the same action as menu item "Format > Bold" and keyboard shortcut Ctrl+B.
- Undo must reverse the last operation.
- Macro recording captures a sequence of operations to replay.

Without Command, each of these three UI elements would have to know the bold operation's implementation, how to reverse it, and how to log it. Triplicating this knowledge across the codebase is a maintenance nightmare.

### Scenario: Task Scheduling
A job queue that executes tasks at different times, potentially on different threads, needs a way to store "what to do" without immediately executing it. Without Command, you can't store method calls — you need Command objects.

---

## 4. Solution

Wrap each operation in a `Command` object with an `execute()` method (and optionally `undo()`). The invoker holds a Command reference and calls `execute()` without knowing what happens. The receiver contains the actual business logic. The client creates commands, links them to receivers, and hands them to invokers.

This separates:
- **When** an operation is triggered (Invoker)
- **What** operation is executed (Command)
- **How** it's executed (Receiver)

---

## 5. UML Structure

```
  +----------+        +-------------------+        +-----------+
  |  Client  |------->|    <<interface>>  |        |  Receiver |
  +----------+        |      Command      |        +-----------+
                       +-------------------+        | +action() |
                       | + execute(): void |        +-----------+
                       | + undo(): void    |              ^
                       +-------------------+              |
                                ^                         |
                    ____________|____________             |
                   |                         |           |
          +------------------+   +------------------+   |
          | ConcreteCommand1 |   | ConcreteCommand2 |   |
          +------------------+   +------------------+   |
          | - receiver       |-->|                  |   |
          | + execute()      |   | + execute()      |   |
          | + undo()         |   | + undo()         |   |
          +------------------+   +------------------+   |
                                                         |
  +----------+                                           |
  |  Invoker |                                           |
  +----------+                                           |
  | -command |----(holds Command reference)              |
  | +setCmd()|                                           |
  | +invoke()|---calls execute()                         |
  +----------+                                           |

Command history (for undo/redo):
  Stack<Command> history = [cmd1, cmd2, cmd3] <- top
  undo() pops cmd3 and calls cmd3.undo()
```

---

## 6. How It Works

1. **Client creates** a `ConcreteCommand`, injecting the `Receiver` it needs.
2. **Client hands** the command to the `Invoker` (e.g., sets it as a button's action).
3. **Invoker triggers** `command.execute()` when the user clicks/schedules/etc.
4. `execute()` delegates to the `Receiver`'s actual method (e.g., `receiver.action()`).
5. For undo: the command stores state before execution (or knows how to reverse), then `undo()` reverses it.
6. For macro/queue: commands are stored in a list and executed in order.
7. For logging: executed commands are serialized and stored; can be replayed on recovery.

**Undo/Redo mechanics:**
```
Execute: push to historyStack, push null to redoStack
Undo:    pop from historyStack, call cmd.undo(), push to redoStack
Redo:    pop from redoStack, call cmd.execute(), push to historyStack
```

---

## 7. Key Components

| Component | Role |
|-----------|------|
| **Command** | Interface with `execute()` and optionally `undo()` |
| **ConcreteCommand** | Implements Command; holds receiver reference; stores pre-execution state for undo |
| **Receiver** | The object that performs the actual work when `execute()` is called |
| **Invoker** | Triggers the command; may queue, log, or sequence commands |
| **Client** | Creates and configures commands; links commands to receivers and invokers |
| **Command History** | Stack used by invoker to support undo/redo |

---

## 8. When to Use

- **Undo/Redo functionality** — text editors, drawing tools, IDEs.
- **Transaction management** — database operations that must be atomic and reversible.
- **Task queues and schedulers** — commands are queued for deferred or async execution.
- **Macro recording** — record user actions and replay them.
- **Audit logging** — store every command executed for later review or replay.
- **Parameterizable UI** — toolbar buttons, menu items, keyboard shortcuts all need the same operation.
- **Remote execution** — serialize commands and send over a network; the remote system deserializes and executes.
- **Wizard/multi-step workflows** — each step is a command; "Back" undoes the previous step.
- **Progress tracking** — track which operations have been executed and their results.

**Concrete examples:**
- Java's `Runnable`/`Callable` interfaces are Commands.
- Java's `java.awt.event.ActionListener` is a Command.
- Spring Batch `ItemProcessor` steps.
- Database migration tools (Flyway, Liquibase) — each migration is a Command with up/down.

---

## 9. When NOT to Use

- **Simple one-shot operations** — if there's no need for undo, queueing, or parameterization, direct method calls are cleaner.
- **Performance-critical code** — the extra object allocation per operation adds overhead.
- **When undo is trivial** — if the operation is naturally reversible without storing state, Command adds overhead.
- **Stateless operations** — if the operation has no side effects and doesn't need logging/queuing, use a simple lambda.
- **When the receiver IS the command** — if the invoker already knows the receiver, the pattern adds no value.

---

## 10. Pros

- **Decouples invoker from receiver** — UI elements don't know what they're invoking or on whom.
- **Supports undo/redo** — history stack enables unlimited undo with clean semantics.
- **Enables queuing and scheduling** — commands can be stored and executed later, in order or on a schedule.
- **Supports logging and audit trails** — commands can be serialized before execution.
- **Enables macro/composite commands** — a MacroCommand contains a list of commands and executes them all.
- **Single Responsibility Principle** — operation logic lives in the command, not in the invoker.
- **Open/Closed Principle** — adding a new operation is a new Command class, no changes to invoker.
- **Testability** — commands can be tested independently from invoker and receiver.

---

## 11. Cons

- **Class explosion** — every operation becomes a class; complex applications may have hundreds of command classes.
- **Complexity overhead** — for simple operations without undo/queue needs, the pattern is overkill.
- **State management for undo is tricky** — commands must capture enough state before executing to reverse the operation correctly.
- **Memory usage** — a long history stack with large state snapshots consumes memory.
- **Command ordering issues** — if commands have dependencies, the invoker must manage ordering.
- **Not always reversible** — some operations (like external API calls, emails sent) cannot be truly undone.
- **Temporal coupling risk** — if a command's receiver state changes between creation and execution, the command may be stale.

---

## 12. Tradeoffs

| You Gain | You Lose |
|----------|----------|
| Undo/redo support | Extra classes per operation |
| Decoupled invoker/receiver | Higher memory usage (history stack) |
| Queuing and scheduling | Complexity in state capture for undo |
| Audit logging capability | Performance overhead per operation |
| Macro/composite commands | Potential for stale command state |
| Testable, isolated operations | Harder to see the full flow at a glance |

---

## 13. Common Pitfalls

1. **Not capturing pre-execution state** — `undo()` is useless if the command didn't save the original state before `execute()` mutated it.

2. **Confusing Command with Strategy** — Strategy replaces an algorithm. Command encapsulates an operation. A command has `execute()` + `undo()`; a strategy just has one method.

3. **Receiver doing too much** — commands should be thin wrappers; the receiver holds business logic. If commands are fat with logic, they become untestable.

4. **Unlimited undo history** — cap the history stack size to avoid memory leaks in long-running applications.

5. **Not clearing redo stack on new execute** — after a new command executes, the redo stack must be cleared. Otherwise, redoing a "future" that no longer applies creates inconsistency.

6. **Forgetting thread safety on the history stack** — if commands are executed on multiple threads, the history stack needs synchronization.

7. **Irreversible side effects** — commands that send emails or charge credit cards cannot be undone. The `undo()` method must compensate (send cancellation email, issue refund) rather than truly reverse.

8. **Composite command partial failure** — if a MacroCommand fails halfway, you need to undo the already-executed sub-commands. This is essentially a transaction — handle it explicitly.

---

## 14. Real-World Usage

### Java Standard Library
- `java.lang.Runnable` — the classic Command interface (`run()` = `execute()`).
- `java.util.concurrent.Callable` — Command with a return value.
- `java.awt.event.ActionListener` — `actionPerformed()` is `execute()`.
- `javax.swing.Action` — extends ActionListener; includes name, icon, enabled state — a full Command.

### Spring Framework
- **Spring Batch** — each `Step` is a command; steps can be composed into `Job`s.
- **Spring Integration** — `MessageHandler` follows the Command pattern.
- **Spring `@Transactional`** — conceptually wraps method execution in a command with commit/rollback.

### Java Enterprise
- **JMS MessageListener** — `onMessage()` processes a queued command.
- **EJB Timer Service** — `@Timeout` methods are deferred commands.

### Android
- `AsyncTask.doInBackground()` — a Command executed on a background thread.

### Database Migrations
- Flyway/Liquibase migration scripts — each migration is a Command with `up` (execute) and `down` (undo).

---

## 15. Comparison with Similar Patterns

| Pattern | Key Difference |
|---------|---------------|
| **Strategy** | Strategy replaces an interchangeable algorithm (how to do something). Command encapsulates a complete request (what to do, to whom, and how to undo). |
| **Chain of Responsibility** | CoR routes a request to the right handler. Command encapsulates the request itself. They are complementary: CoR can route Command objects. |
| **Memento** | Memento captures state for undo. Command captures the operation for undo. They work together — Command triggers an action, Memento saves state to reverse it. |
| **Observer** | Observer notifies multiple listeners reactively. Command is a proactive, parameterizable action request. |
| **Template Method** | Template Method defines algorithm steps in a base class. Command encapsulates a single operation without constraining its internal structure. |
| **Composite** | MacroCommand is a Composite applied to the Command pattern — a Command composed of other Commands. |

---

## 16. Interview Tips

**Common interview questions:**

**Q: What is the Command pattern? When would you use it?**
A: Command encapsulates an operation as an object. Use it when you need undo/redo, operation queuing, macro recording, or to decouple invokers from receivers.

**Q: How does Command support undo/redo?**
A: The command stores pre-execution state (or the inverse operation). A history stack tracks executed commands. Undo pops the stack and calls `undo()`; redo re-executes.

**Q: What's the difference between Command and Strategy?**
A: Strategy replaces a single interchangeable algorithm. Command is a complete request object — it knows the receiver, the operation, and how to reverse it. A Strategy changes how; a Command records what happened.

**Q: Is `Runnable` a Command?**
A: Yes — `Runnable` is the simplest possible Command with no return value and no undo. It decouples the executor (thread pool) from the operation.

**Q: How do you handle commands that can't be undone?**
A: Use compensating transactions. The `undo()` method issues a compensating action (e.g., send a cancellation email instead of unsending the original). Document which commands are irreversible.

**Follow-up traps:**
- Be ready to implement a simple undo/redo stack.
- Know the difference between Command (operation-focused) and Memento (state-focused) for undo.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Command Appears in Distributed Systems**

- **Message queue payloads** — Every message in a queue (Kafka, SQS, RabbitMQ) is a serialized Command: it encapsulates an action, parameters, and metadata (correlation ID, timestamp, retry count). Consumers execute the command; dead-letter queues hold failed commands for inspection.
- **CQRS write side** — Command Query Responsibility Segregation: write operations are explicit Command objects (`PlaceOrderCommand`, `CancelOrderCommand`). The command handler validates, executes, and emits domain events. This separates write intent from query intent cleanly.
- **Saga pattern** — Distributed transactions use Command with compensating commands for rollback. Each saga step is a Command; on failure, compensating Commands (the undo stack) restore consistency across services.
- **Audit log / event sourcing** — Event sourcing stores the complete history of Commands applied to an entity. Replaying the command log from any point reconstructs current state — Command as immutable persistent record.

---

## 17. Best Practices

1. **Capture state before mutation in `execute()`** — save everything needed to reverse the operation before changing receiver state.
2. **Clear redo stack on new execute** — a new operation invalidates the redo history.
3. **Cap history stack size** — prevent memory leaks with a bounded deque (e.g., max 100 entries).
4. **Keep commands thin** — delegate actual work to the receiver; commands are coordinators, not implementors.
5. **Use `Runnable`/`Callable` for simple cases** — don't create a custom `Command` interface when Java's built-in interfaces suffice.
6. **Make commands serializable for persistence** — if you need crash recovery, commands must be serializable to disk before execution.
7. **Separate Command from MacroCommand** — use the Composite pattern for macro commands rather than nesting command logic.
8. **Thread safety** — if commands are shared across threads, make them immutable after creation.
9. **Log commands before execution** — for audit trails, log before `execute()`, not after (in case execute fails).
10. **Design `undo()` defensively** — `undo()` can be called when the system is in an unexpected state; validate before reversing.
