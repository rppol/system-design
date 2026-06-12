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

### Production Scenario: Collaborative Document Editor — Undo/Redo Command Log (100k ops/hour)

A collaborative document editor processes 100,000 edit operations per hour from concurrent users.
Every operation — insert text, delete text, apply bold, change font size — is a Command object
persisted to an append-only command log. Undo replays the log in reverse (calling `undo()` on
each command); redo replays forward (calling `execute()` again). Commands are compacted after
1,000 entries per document to prevent unbounded memory growth.

At 100k ops/hour across 10,000 active documents, average 10 ops/hour per document. Each Command
object is ~200 bytes in memory (text snapshot + metadata); a 1,000-command log per document costs
~200 KB. Compaction reduces this to the net document state (typically < 10 KB).

**Scale numbers:**
- 100,000 edit operations/hour = ~28 ops/sec sustained
- Command object size: ~200 bytes (text delta + position + userId + timestamp)
- Undo/redo response time: < 5 ms (local log replay, no network)
- Command log compaction trigger: every 1,000 commands (reduces 200 KB to < 10 KB net state)
- Spring `@Async` task executor: `Callable` commands submitted to `ThreadPoolTaskExecutor`
  achieve 10,000 concurrent background tasks at < 2 ms dispatch latency

```
Collaborative Editor — Command Log Architecture
================================================

  [ User Keystroke ]
         |
         v
  +----------------+
  | CommandFactory | creates InsertTextCommand / DeleteTextCommand / FormatCommand
  +----------------+
         |
         v
  +-------------------+
  | CommandInvoker    |
  | - executeStack[]  |  <-- undo history (ring buffer, max 1000)
  | - redoStack[]     |  <-- redo candidates
  +-------------------+
         |
   execute(cmd)  -->  cmd.execute()  -->  DocumentEditor.applyDelta()
   undo()        -->  cmd.undo()     -->  DocumentEditor.revertDelta()
         |
  +----------------+
  | CommandLog     |  persisted to Redis Sorted Set (score = sequenceNum)
  | (append-only)  |  compacted to snapshot every 1000 commands
  +----------------+
```

```java
// Java 17 LTS — Command interface and concrete implementations
// Collaborative document editor undo/redo

public interface DocumentCommand {
    void execute(DocumentEditor editor);
    void undo(DocumentEditor editor);
    String commandId();    // UUID for log deduplication
}

public final class InsertTextCommand implements DocumentCommand {

    private final int position;
    private final String text;
    private final String commandId;
    private final String userId;

    public InsertTextCommand(int position, String text, String userId) {
        this.position = position;
        this.text = text;
        this.commandId = UUID.randomUUID().toString();
        this.userId = userId;
    }

    @Override
    public void execute(DocumentEditor editor) {
        editor.insert(position, text);
    }

    @Override
    public void undo(DocumentEditor editor) {
        editor.delete(position, text.length());
        // pre-state is fully encoded in this Command — no external lookup needed
    }

    @Override public String commandId() { return commandId; }
}

// CommandInvoker — manages execute/undo/redo stacks
public class DocumentCommandInvoker {

    private static final int MAX_HISTORY = 1_000;

    // ArrayDeque as bounded ring buffer (remove oldest when full)
    private final Deque<DocumentCommand> undoStack = new ArrayDeque<>(MAX_HISTORY);
    private final Deque<DocumentCommand> redoStack = new ArrayDeque<>();
    private final DocumentEditor editor;

    public DocumentCommandInvoker(DocumentEditor editor) {
        this.editor = editor;
    }

    public void execute(DocumentCommand cmd) {
        if (undoStack.size() == MAX_HISTORY) {
            undoStack.pollFirst();  // compact: discard oldest, trigger snapshot asynchronously
        }
        cmd.execute(editor);
        undoStack.push(cmd);
        redoStack.clear();  // new command invalidates redo future
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        DocumentCommand cmd = undoStack.pop();
        cmd.undo(editor);
        redoStack.push(cmd);
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        DocumentCommand cmd = redoStack.pop();
        cmd.execute(editor);
        undoStack.push(cmd);
    }
}
```

```java
// Java 17 LTS — Spring @Async + Callable as Command pattern
// Background export job using Callable (Command with return value)

@Service
public class DocumentExportService {

    private final ThreadPoolTaskExecutor executor;

    public DocumentExportService(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    // Callable<ExportResult> IS a Command — encapsulates the "export document" request
    public Future<ExportResult> submitExport(String documentId, ExportFormat format) {
        Callable<ExportResult> exportCommand = () -> {
            byte[] bytes = renderDocument(documentId, format);
            return new ExportResult(documentId, bytes, format);
        };
        return executor.submit(exportCommand);  // Callable dispatched as a task command
    }
}
```

### Famous Codebase Usages

- **`java.lang.Runnable`**: the minimal Command interface — `run()` is `execute()`. Used by
  `Thread`, `ExecutorService.execute(Runnable)`, `CompletableFuture.runAsync()`.
- **`java.util.concurrent.Callable<V>`**: Command with a return value and checked exception.
  `ExecutorService.submit(Callable)` returns a `Future<V>` — queuing and async execution of commands.
- **`javax.swing.Action`** (`javax.swing`): extends `ActionListener`; carries name, icon,
  enabled/disabled state — a full Command object wired to a menu item or toolbar button.
- **Spring Batch `Step`**: each `Step` (comprising `ItemReader`, `ItemProcessor`, `ItemWriter`)
  is a Command object composed into a `Job`; supports restart, skip, retry — built-in undo analogue.
- **Spring Integration `MessageHandler`**: `handleMessage(Message<?>)` is a Command that processes
  a queued message; handlers compose into integration flows.
- **Flyway / Liquibase migrations**: each migration script is a Command with `up` (execute) and
  `down` (undo/rollback) — the canonical enterprise undo pattern.

---

### Anti-Pattern 1: Command Without Pre-State — Undo Impossible

```java
// BROKEN — DeleteTextCommand does not record what text was deleted.
// After execute(), the deleted text is gone from the editor; undo() cannot restore it.

public class DeleteTextCommand implements DocumentCommand {
    private final int position;
    private final int length;

    @Override
    public void execute(DocumentEditor editor) {
        editor.delete(position, length);  // text is gone — not stored anywhere
    }

    @Override
    public void undo(DocumentEditor editor) {
        // IMPOSSIBLE — we do not know what text to re-insert
        throw new UnsupportedOperationException("undo not implemented");
    }
}
```

```java
// FIX — capture the deleted text (pre-state) inside the command before executing.
// Command is now a self-contained snapshot: execute + undo are fully symmetric.

public final class DeleteTextCommand implements DocumentCommand {
    private final int position;
    private final int length;
    private final String commandId = UUID.randomUUID().toString();
    private String deletedText;  // captured during execute(), used in undo()

    public DeleteTextCommand(int position, int length) {
        this.position = position;
        this.length = length;
    }

    @Override
    public void execute(DocumentEditor editor) {
        deletedText = editor.getText(position, length);  // snapshot pre-state first
        editor.delete(position, length);
    }

    @Override
    public void undo(DocumentEditor editor) {
        editor.insert(position, deletedText);   // restore using captured pre-state
    }

    @Override public String commandId() { return commandId; }
}
```

---

### Anti-Pattern 2: Fat Command with Business Logic (500-line execute())

```java
// BROKEN — Command implements 500 lines of business logic inline.
// Unit-testing requires constructing the entire command with all its dependencies.
// The Command layer now owns domain rules that belong in a domain service.

public class ProcessOrderCommand implements DocumentCommand {
    private final Order order;
    private final InventoryService inventory;
    private final BillingService billing;
    private final NotificationService notifications;
    // ...

    @Override
    public void execute(DocumentEditor ignored) {
        // 500 lines: validate order, check inventory, reserve stock, charge card,
        // send email, update analytics, trigger fulfillment...
        // This is a domain service disguised as a Command.
    }
}
```

```java
// FIX — Command is a thin dispatcher that delegates to a domain service.
// Business logic lives in OrderFulfillmentService (unit-testable without Command).
// Command's job: record intent, capture pre-state for undo, delegate to service.

public final class ProcessOrderCommand implements DocumentCommand {
    private final String orderId;
    private final String commandId = UUID.randomUUID().toString();
    private OrderSnapshot preState;  // for undo

    // Injected via constructor, not created inside Command
    private final OrderFulfillmentService fulfillmentService;

    @Override
    public void execute(DocumentEditor editor) {
        preState = fulfillmentService.snapshot(orderId);  // capture before mutation
        fulfillmentService.fulfill(orderId);              // delegate — 1 line
    }

    @Override
    public void undo(DocumentEditor editor) {
        fulfillmentService.restore(orderId, preState);
    }

    @Override public String commandId() { return commandId; }
}
```

---

### Anti-Pattern 3: Command Log Growing Unbounded in Memory

```java
// BROKEN — ArrayList accumulates all commands ever executed.
// A document with 10,000 edits holds 10,000 Command objects (~2 MB).
// After 1 hour of active editing across 10,000 documents: ~20 GB heap.

public class DocumentCommandInvoker {
    private final List<DocumentCommand> history = new ArrayList<>();  // unbounded

    public void execute(DocumentCommand cmd) {
        cmd.execute(editor);
        history.add(cmd);  // grows forever — OOM risk in long sessions
    }
}
```

```java
// FIX — ring buffer capped at MAX_HISTORY; compact to a snapshot when full.
// Users rarely need more than 50 undo steps; 1000 is a generous upper bound.

public class DocumentCommandInvoker {
    private static final int MAX_HISTORY = 1_000;
    private final Deque<DocumentCommand> undoStack = new ArrayDeque<>(MAX_HISTORY);
    private final SnapshotService snapshotService;

    public void execute(DocumentCommand cmd) {
        if (undoStack.size() == MAX_HISTORY) {
            undoStack.pollFirst();  // evict oldest command
            // Optionally: trigger async snapshot so oldest state is still recoverable from DB
            snapshotService.persistSnapshotAsync(documentId, currentState());
        }
        cmd.execute(editor);
        undoStack.push(cmd);
    }
}
```

---

### Performance and Correctness Numbers

| Metric | Value |
|---|---|
| Command object allocation | ~200 bytes (text delta + 2 String fields + UUID) |
| Undo/redo latency (in-memory) | < 5 ms for 1,000-command log |
| Command log compaction (1,000 cmds) | 200 KB -> < 10 KB net document state |
| Spring Callable dispatch latency | < 2 ms to `ThreadPoolTaskExecutor` queue |
| Flyway migration undo safety | 100% reproducible rollback with recorded down() script |

### Migration Story

**Move TO Command when:**
- You need undo/redo — Command is the canonical pattern for reversible operations.
- You need deferred execution, queuing, or scheduling of operations.
- You want to log all user actions for audit trails or replay (event sourcing).

**Move AWAY FROM Command when:**
- Operations are fire-and-forget with no undo requirement — `Runnable` suffices.
- The "undo" requirement is actually database rollback — use `@Transactional` instead.
- Command objects become large because they capture too much pre-state — consider
  Memento as a separate pattern to handle state snapshots, and keep Commands thin.

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

**Q: How do Command and Memento work together for undo?**
A: Command encapsulates the operation (what was done and to whom); Memento captures the state needed to reverse it. A typical flow: before `execute()` mutates the receiver, the command asks the receiver for a `Memento` (or builds one from the receiver's current fields) and stores it internally; `undo()` then calls `receiver.restore(memento)`. This split keeps the command focused on "what action happened" while the memento focuses on "what state existed before" — for example, `DeleteTextCommand` in a text editor stores the deleted substring (a memento-like snapshot) so `undo()` can re-insert it. Use this combination when the inverse of an operation isn't a simple algebraic inverse (e.g., "delete" has no obvious inverse without remembering what was deleted).

**Q: How would you implement a macro/composite command?**
A: A `MacroCommand` implements the same `Command` interface but holds a `List<Command>`; its `execute()` iterates and calls `execute()` on each sub-command in order, and its `undo()` iterates in *reverse* order calling `undo()` on each — this is the Composite pattern applied to Command. The tricky part is partial failure: if sub-command 3 of 5 throws during `execute()`, the macro must undo sub-commands 1-2 (which already succeeded) before propagating the error, otherwise the receiver is left in an inconsistent half-applied state. Treat a macro command's `execute()` like a mini-transaction — either all sub-commands apply or none do, from the caller's perspective.

**Q: How is Command used for task queues and job scheduling?**
A: Each queued job is a Command object (often a `Runnable` or `Callable`) holding everything needed to perform the work — parameters, target resource references — without the producer needing to know how or when it will run. A `ThreadPoolExecutor` or `BlockingQueue<Runnable>` is the Invoker: producers call `queue.put(command)`, worker threads call `queue.take()` then `command.run()`. This is exactly how `ExecutorService.submit()` works under the hood, and it generalizes to distributed systems — a message on a Kafka topic or SQS queue is a serialized Command that some consumer will eventually execute.

**Q: Is a shared command queue thread-safe? What do you need to watch for?**
A: A `BlockingQueue` (e.g., `LinkedBlockingQueue`, `ArrayBlockingQueue`) is thread-safe for the queue mechanics itself — `put()`/`take()` handle synchronization and blocking when full/empty — but that does NOT make the Command objects themselves thread-safe. If a command holds mutable shared state (e.g., a reference to a receiver that multiple commands mutate concurrently), you still need synchronization or immutable receivers per command. A common bug is reusing a single mutable `Command` instance across multiple `submit()` calls with different parameters — each submission should get its own immutable command instance, or the queue can hand the same logical operation to two threads with stale/overwritten fields.

**Q: How do you serialize a Command for remote execution, and what breaks if the receiver changes?**
A: A serializable Command must capture everything needed to execute remotely as primitive/serializable data — operation type, parameters, target identifiers — *not* live references to receivers, since `Receiver` objects (database connections, services) typically aren't serializable and wouldn't make sense on a different machine anyway. On the receiving side, a `CommandHandler` deserializes the payload, looks up the appropriate local `Receiver` by ID, and invokes the operation — this is exactly the shape of a Kafka/SQS message: `{"type": "ChargeCardCommand", "orderId": "123", "amount": 49.99}`. The danger is temporal coupling: if the command was created against `orderId: 123` when it was in state "PENDING," but by the time a consumer processes it the order has moved to "CANCELLED," blindly executing the command produces an inconsistent result — handlers must re-validate preconditions at execution time, not just at creation time, because serialized commands can sit in a queue for an arbitrarily long time before execution.

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
