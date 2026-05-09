# Command vs Strategy Pattern

## Quick Summary

- **Command**: Encapsulates a *request* as an object — enables queuing, logging, and undo/redo.
- **Strategy**: Encapsulates an *algorithm* as an object — enables runtime algorithm switching.

---

## Intuition

> **One-line analogy**: Command is a written order slip at a restaurant (the request is captured, queued, and can be cancelled); Strategy is a recipe card (defines how to cook, swappable, stateless).

**Mental model**: Command encapsulates a *specific action* with its parameters as an object — enabling queuing, logging, undo, and replay. Strategy encapsulates an *algorithm* — a reusable, stateless way to accomplish something. A `SaveFileCommand` is a Command: it captures state (which file, what content, when). A `CompressionStrategy` is a Strategy: it's a pure function with no captured state, interchangeable at runtime.

**Why it matters**: Both use the same "encapsulate in an object" move but for different purposes. Confusing them leads to bloated Command objects that try to be algorithms, or stateless Strategies that accumulate request-specific state.

**Key insight**: Command has identity and state (what was requested, when, by whom). Strategy is stateless and interchangeable. If you need undo, history, or queuing → Command. If you need runtime algorithm swapping → Strategy.

---

## Side-by-Side Comparison

| Aspect           | Command                                      | Strategy                                        |
|------------------|----------------------------------------------|-------------------------------------------------|
| **Intent**       | Turn a request/action into a first-class object | Turn an algorithm into a first-class object   |
| **Structure**    | Invoker → Command → Receiver                 | Context → Strategy interface → ConcreteStrategy |
| **Key Difference** | About WHAT (the operation, its params, and who receives it) | About HOW (the algorithm used to do something) |
| **Use When**     | You need undo/redo, queuing, logging, or macro recording | You need to swap algorithms at runtime without changing the client |

---

## Java Code Examples

### Command Pattern — Text Editor with Undo Stack

```java
// Command interface
public interface Command {
    void execute();
    void undo();
}

// Receiver
public class TextBuffer {
    private StringBuilder text = new StringBuilder();

    public void insert(String s, int pos) {
        text.insert(pos, s);
        System.out.println("Buffer: " + text);
    }

    public void delete(int pos, int length) {
        text.delete(pos, pos + length);
        System.out.println("Buffer: " + text);
    }

    public String getText() { return text.toString(); }
}

// Concrete Commands
public class CutCommand implements Command {
    private TextBuffer buffer;
    private int pos, length;
    private String cutText;

    public CutCommand(TextBuffer buffer, int pos, int length) {
        this.buffer = buffer;
        this.pos = pos;
        this.length = length;
    }

    @Override
    public void execute() {
        cutText = buffer.getText().substring(pos, pos + length);
        buffer.delete(pos, length);
    }

    @Override
    public void undo() {
        buffer.insert(cutText, pos);
    }
}

public class PasteCommand implements Command {
    private TextBuffer buffer;
    private String text;
    private int pos;

    public PasteCommand(TextBuffer buffer, String text, int pos) {
        this.buffer = buffer;
        this.text = text;
        this.pos = pos;
    }

    @Override
    public void execute() {
        buffer.insert(text, pos);
    }

    @Override
    public void undo() {
        buffer.delete(pos, text.length());
    }
}

// Invoker — holds undo stack
public class TextEditor {
    private final Deque<Command> history = new ArrayDeque<>();

    public void executeCommand(Command cmd) {
        cmd.execute();
        history.push(cmd);
    }

    public void undo() {
        if (!history.isEmpty()) {
            history.pop().undo();
        }
    }
}

// Client
public class CommandDemo {
    public static void main(String[] args) {
        TextBuffer buffer = new TextBuffer();
        TextEditor editor = new TextEditor();

        buffer.insert("Hello World", 0);

        Command cut = new CutCommand(buffer, 6, 5);   // cut "World"
        editor.executeCommand(cut);

        Command paste = new PasteCommand(buffer, "Java", 6);
        editor.executeCommand(paste);

        editor.undo(); // undo paste
        editor.undo(); // undo cut — "Hello World" restored
    }
}
```

---

### Strategy Pattern — Sorter with Pluggable Algorithms

```java
// Strategy interface
public interface SortStrategy {
    void sort(int[] data);
}

// Concrete Strategies
public class BubbleSortStrategy implements SortStrategy {
    @Override
    public void sort(int[] data) {
        System.out.println("Sorting with BubbleSort");
        int n = data.length;
        for (int i = 0; i < n - 1; i++)
            for (int j = 0; j < n - i - 1; j++)
                if (data[j] > data[j + 1]) {
                    int tmp = data[j]; data[j] = data[j + 1]; data[j + 1] = tmp;
                }
    }
}

public class QuickSortStrategy implements SortStrategy {
    @Override
    public void sort(int[] data) {
        System.out.println("Sorting with QuickSort");
        quickSort(data, 0, data.length - 1);
    }

    private void quickSort(int[] data, int low, int high) {
        if (low < high) {
            int pi = partition(data, low, high);
            quickSort(data, low, pi - 1);
            quickSort(data, pi + 1, high);
        }
    }

    private int partition(int[] data, int low, int high) {
        int pivot = data[high], i = low - 1;
        for (int j = low; j < high; j++)
            if (data[j] <= pivot) { i++; int t = data[i]; data[i] = data[j]; data[j] = t; }
        int t = data[i + 1]; data[i + 1] = data[high]; data[high] = t;
        return i + 1;
    }
}

// Context
public class Sorter {
    private SortStrategy strategy;

    public Sorter(SortStrategy strategy) {
        this.strategy = strategy;
    }

    // Switch algorithm at runtime
    public void setStrategy(SortStrategy strategy) {
        this.strategy = strategy;
    }

    public void sort(int[] data) {
        strategy.sort(data);
    }
}

// Client
public class StrategyDemo {
    public static void main(String[] args) {
        int[] data = {5, 3, 8, 1, 9, 2};

        Sorter sorter = new Sorter(new BubbleSortStrategy());
        sorter.sort(data);

        // Switch strategy at runtime — no change to client code
        sorter.setStrategy(new QuickSortStrategy());
        sorter.sort(data);
    }
}
```

---

## Key Structural Differences — ASCII Class Diagrams

### Command

```
+----------+       +------------------+       +----------+
| Invoker  |------>| <<interface>>    |       | Receiver |
|          |       |    Command       |<------+----------+
| history  |       |+ execute()       |
| stack    |       |+ undo()          |
+----------+       +------------------+
                          ^
               +----------+----------+
               |                     |
       +---------------+    +----------------+
       |  CutCommand   |    |  PasteCommand  |
       |+ execute()    |    |+ execute()     |
       |+ undo()       |    |+ undo()        |
       +---------------+    +----------------+
```

### Strategy

```
+----------+       +------------------+
| Context  |------>| <<interface>>    |
| (Sorter) |       |  SortStrategy    |
|          |       |+ sort(int[])     |
+----------+       +------------------+
                          ^
               +----------+----------+
               |                     |
  +---------------------+   +--------------------+
  | BubbleSortStrategy  |   | QuickSortStrategy  |
  |+ sort(int[])        |   |+ sort(int[])       |
  +---------------------+   +--------------------+
```

**Structural note**: Both patterns look similar in UML. The distinction is semantic:
- Command holds a *Receiver* reference and encapsulates *who* and *what*.
- Strategy holds only *how* — no concept of a separate receiver.

---

## Decision Guide

Use **Command** when you need:
- Undo/redo functionality
- Request queuing or scheduling (e.g., job queues)
- Logging or auditing of operations
- Macro recording (composing multiple commands)
- Decoupling the *sender* from the *receiver* of a request

Use **Strategy** when you need:
- Multiple algorithms that are interchangeable
- Removing large if/else or switch blocks that select behavior
- Runtime algorithm selection based on context (e.g., device, load, user preference)
- Unit testing algorithms in isolation

---

## Common Confusion Points

1. **Both encapsulate behavior** — The key tell: does your object need `undo()`, a history stack, or a Receiver? That's Command. Does your object only swap the *algorithm*? That's Strategy.

2. **Strategy has no undo concept** — Strategy just selects HOW to do something. Command remembers WHAT was done so it can be reversed.

3. **Command carries state** — A `CutCommand` stores the text that was cut. A Strategy is usually stateless.

4. **Invoker ≠ Context** — Command's Invoker triggers execution and manages history. Strategy's Context uses the strategy directly without history.

---

## Real-World Examples

| Command | Strategy |
|---------|----------|
| Menu items / toolbar buttons in GUIs | `java.util.Comparator` for sorting |
| Database transaction log (replay / rollback) | Payment processors (credit card, PayPal, crypto) |
| Job queues (e.g., Celery tasks, Sidekiq) | Tax calculators per region/country |
| Git commits (each commit is a reversible command) | Route finding (Dijkstra vs A* vs BFS) |
| Macro recording in spreadsheets | Compression algorithms (zip, gzip, lz4) |

---

## Can They Work Together?

Yes — and this is a powerful combination.

A **Command** can use a **Strategy** internally to decide *how* to perform its action:

```java
public class SortCommand implements Command {
    private int[] data;
    private int[] backup;
    private SortStrategy strategy;  // Strategy inside Command

    public SortCommand(int[] data, SortStrategy strategy) {
        this.data = data;
        this.strategy = strategy;
    }

    @Override
    public void execute() {
        backup = data.clone();
        strategy.sort(data);   // Strategy decides HOW to sort
    }

    @Override
    public void undo() {
        System.arraycopy(backup, 0, data, 0, backup.length);
    }
}
```

The Command handles *what happened and reversibility*; the Strategy handles *how the sort is performed*.
