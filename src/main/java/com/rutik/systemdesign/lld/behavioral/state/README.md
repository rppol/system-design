# State Pattern

## 1. Pattern Name & Category

**Pattern:** State
**Category:** Behavioral
**GoF Classification:** Behavioral Design Pattern (Gang of Four)
**Also Known As:** Objects for States, FSM (Finite State Machine) Pattern

---

## 2. Intent

Allow an object to alter its behavior when its internal state changes. The object will appear to change its class.

---

## Intuition

> **One-line analogy**: State is like a vending machine — the same button press ("dispense") does different things depending on whether the machine is idle, has money inserted, or is out of stock. The machine's state determines its behavior.

**Mental model**: When an object's behavior depends heavily on its current state and changes at runtime, if-else/switch chains on a state variable grow unmanageable. State pattern extracts each state into its own class that handles all behaviors for that state. The Context holds a reference to the current State object and delegates to it. Transitioning to a new state means replacing the State object.

**Why it matters**: State is the pattern for implementing Finite State Machines in OOP. ATM states (Idle, CardInserted, Processing, Dispensing), order states (Pending, Confirmed, Shipped, Delivered), traffic light states — all benefit from State pattern. It eliminates state-checking conditionals and makes adding new states/transitions safe (modify/add a single State class).

**Key insight**: The difference from Strategy: in Strategy, the client explicitly selects the strategy; in State, the state itself knows about transitions and can trigger them. State objects often have references to the Context and can trigger state changes autonomously (ATM moves itself from CardInserted → Processing when PIN is verified).

---

## 3. Problem Statement

### The Core Problem
An object has behavior that depends on its internal state, and the behavior must change at runtime when the state changes. A naive implementation uses a large switch-case or if-else chain to branch on the current state for every method. This becomes unmanageable as states and transitions multiply.

### Concrete Scenario
Consider a **vending machine**. It has several states: `IDLE` (waiting for money), `HAS_MONEY` (money inserted, waiting for selection), `DISPENSING` (dispensing product), and `OUT_OF_STOCK`. Every operation (insert coin, select product, dispense, refund) behaves differently depending on which state the machine is in.

Without the State pattern:
```java
public void insertCoin(double amount) {
    if (state == IDLE) {
        // accept coin logic
    } else if (state == HAS_MONEY) {
        // add to existing balance logic
    } else if (state == DISPENSING) {
        // reject coin, currently busy
    } else if (state == OUT_OF_STOCK) {
        // return coin immediately
    }
}
```
Now imagine 6 operations and 5 states — you have 30 branching blocks. Add a new state (e.g., `MAINTENANCE`) and you must update every method. This violates the Open/Closed Principle and Single Responsibility Principle simultaneously.

### What Goes Wrong Without the Pattern
- Monolithic context class with massive conditional blocks.
- Adding a new state requires modifying every method in the context.
- State-specific behavior is scattered throughout the class rather than co-located.
- Transition logic is interleaved with business logic, making the code hard to read.
- Testing individual state behaviors requires exercising the full context.

---

## 4. Solution

Extract each state's behavior into its own class that implements a common `State` interface. The `Context` object holds a reference to the current `State` object and delegates all state-dependent operations to it. To change state, the Context simply swaps out the State reference.

Each concrete state class contains:
1. The behavior for each operation *when in that state*.
2. The logic for transitioning to other states.

---

## 5. UML Structure

```
+------------------+          has a          +------------------+
|    Context       |----------------------->|  <<interface>>   |
|------------------|                         |     State        |
| -state: State    |                         |------------------|
|------------------|      delegates to       | +insertCoin()    |
| +request()       |------------------------>| +selectProduct() |
| +setState(s)     |                         | +dispense()      |
+------------------+                         +------------------+
                                                      ^
                                      +---------------+-------------+
                                      |               |             |
                               +-----------+   +-----------+  +-----------+
                               | IdleState |   |HasMoney   |  |OutOfStock |
                               |-----------|   |State      |  |State      |
                               |+insertCoin|   |-----------|  |-----------|
                               |+select()  |   |+insertCoin|  |+insertCoin|
                               |+dispense()|   |+select()  |  |+select()  |
                               +-----------+   +-----------+  +-----------+
```

**Key structural insight:** The Context is the stable public interface. Clients interact only with the Context, never with concrete State classes. State transitions are triggered either by the Context (centralized) or by the State classes themselves (distributed).

---

## 6. How It Works — Step-by-Step

1. **Context is created** with an initial state (e.g., `new VendingMachine()` sets `state = new IdleState(this)`).
2. **Client calls a method on Context** — e.g., `machine.insertCoin(1.00)`.
3. **Context delegates to current State** — `state.insertCoin(1.00)`.
4. **State executes behavior** — `IdleState.insertCoin()` accepts the coin, sets balance, and transitions: `context.setState(new HasMoneyState(context))`.
5. **Context now points to new State** — subsequent calls delegate to `HasMoneyState`.
6. **State machine progresses** — each operation either stays in state or transitions to another.
7. **Client is unaware of state changes** — it only sees the Context's public interface.

---

## 7. Key Components

| Role | Responsibility |
|---|---|
| **Context** | Maintains current State reference; delegates state-dependent operations to it; exposes `setState()` for transitions |
| **State (interface/abstract)** | Defines the interface for all state-specific operations |
| **ConcreteState** | Implements behavior for each operation in a specific state; may trigger transitions |

### Two Transition Strategies
- **Context-driven transitions:** The Context decides which state to transition to. States only execute behavior. Centralizes transition logic.
- **State-driven transitions:** State classes call `context.setState(new NextState())` directly. Distributes transition logic but makes it co-located with the state that triggers it.

---

## 8. When to Use

- **Object behavior depends on state** and must change at runtime based on that state.
- **Massive conditional blocks** that branch on an enumerated state value — refactor to State pattern.
- **Finite state machines** with well-defined states and transitions (vending machines, traffic lights, order workflows, TCP connections).
- **UI components with modes** — a media player (playing, paused, stopped, loading), a text editor (insert mode, command mode).
- **Workflow/process engines** — order statuses (pending, confirmed, shipped, delivered, cancelled).
- **Protocol implementations** — TCP/IP connection states (LISTEN, SYN_SENT, ESTABLISHED, CLOSE_WAIT, etc.).
- **Game character states** — idle, walking, running, jumping, attacking, dead.

---

## 9. When NOT to Use

- **Simple two-state objects** — a boolean flag is cleaner and more readable for an object that is just "enabled/disabled".
- **Stateless objects** — if behavior doesn't depend on accumulated state, the pattern adds unnecessary structure.
- **Few operations per state** — if there's only one or two methods, an if-else is more readable than creating a full class hierarchy.
- **Transitions are rare** — if the object almost never changes state, the overhead of the pattern isn't justified.
- **State transitions require complex coordination** — if transitioning involves multiple objects atomically, consider a dedicated state machine library (e.g., Spring State Machine) rather than a hand-rolled implementation.

---

## 10. Pros

- **Eliminates large conditional blocks** — each state's behavior is encapsulated in its own class.
- **Open/Closed Principle** — new states can be added without modifying the Context or existing State classes (only a new ConcreteState class is needed).
- **Single Responsibility** — each ConcreteState class handles exactly one state's behavior.
- **State-specific code is co-located** — all behavior for "HasMoney" state is in `HasMoneyState`, making it easy to find and modify.
- **Explicit state transitions** — transitions are visible and traceable in code.
- **Easier to test** — each state can be tested independently by setting the context to that state and verifying behavior.
- **Self-documenting** — the set of ConcreteState classes documents all possible states of the system.

---

## 11. Cons

- **Class explosion** — each state becomes a class, which can be excessive for simple state machines with many states.
- **Tight coupling between states and Context** — states often need a reference back to the Context (to trigger transitions and access shared data), creating bidirectional coupling.
- **Distributed transition logic** — if states drive their own transitions, understanding the full state machine requires reading all State classes.
- **Shared state via Context** — states access shared data through the Context object, which can become a "god object" if not managed carefully.
- **Concurrency risks** — concurrent calls to the Context can cause race conditions in state transitions. Must synchronize state reads/writes.

---

## 12. Tradeoffs

| You Gain | You Lose |
|---|---|
| Clean, state-specific behavior encapsulation | Increased number of classes |
| Easy extensibility for new states | Centralized view of all transitions |
| Testability of individual states | Simple code when state machine is small |
| Clear separation of state-specific logic | Potential Context bloat as shared data grows |

---

## 13. Common Pitfalls

1. **Forgetting to pass Context reference to State** — States that need to trigger transitions need access to the Context. Pass `this` during state construction or via each method call.

2. **Mutable shared data in Context vs. State** — if state-specific data lives in the Context (shared across states), it becomes a dumping ground. Consider moving state-local data into the ConcreteState.

3. **Not handling invalid operations** — calling `dispense()` in `IdleState` (where no money has been inserted) must either throw an appropriate exception or silently ignore — define a clear policy.

4. **Race conditions in multithreaded contexts** — two threads calling `setState()` simultaneously can corrupt the state reference. Use `synchronized` or `AtomicReference<State>`.

5. **Confusing State with Strategy** — State objects hold a reference back to the Context and drive transitions; Strategy objects are stateless algorithms passed from outside. The difference is *who drives transitions*.

6. **Creating a new State object on every transition** — instantiating new State objects on every transition is fine for simple cases, but for high-frequency machines, pre-allocate state singletons (flyweight states) to avoid GC pressure.

---

## 14. Real-World Usage

| Framework / Library | Usage |
|---|---|
| **Java TCP/IP (NIO)** | `SelectionKey` states (OP_ACCEPT, OP_READ, OP_WRITE, OP_CONNECT) — channel behavior depends on readiness state. |
| **Spring State Machine** | Explicit State Machine framework for Spring applications. Models workflows, order processing, and approval flows. |
| **Android** | `Fragment` lifecycle (created, started, resumed, paused, stopped, destroyed) — each lifecycle callback is a state-specific method. `MediaPlayer` states. |
| **javax.swing** | `ButtonModel` has states: armed, pressed, rollover, selected, enabled — classic State pattern in Swing. |
| **JPA / Hibernate** | Entity lifecycle states: Transient, Managed, Detached, Removed. Each state has different behavior for persistence operations. |
| **Java Thread** | `Thread.State` enum (NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED) — JVM thread scheduling is a state machine. |
| **Workflow engines** | Activiti, Camunda, jBPM all model process states and transitions using State-pattern-like structures. |

---

## 15. Comparison with Similar Patterns

| Pattern | Similarity | Key Difference |
|---|---|---|
| **Strategy** | Both use a family of interchangeable classes | Strategy is chosen by the *client* and is usually stateless; State is chosen by the *State/Context* and drives transitions. State objects know about the Context. |
| **Command** | Both encapsulate behavior | Command encapsulates a *request/operation*; State encapsulates *context-dependent behavior* for a full set of operations. |
| **Template Method** | Both define algorithmic steps | Template Method fixes the algorithm structure in a base class; State varies behavior based on runtime state. |
| **Flyweight** | State singletons use Flyweight | When ConcreteState objects are stateless (all data in Context), they can be shared as Flyweights. |

---

## 16. Interview Tips

**Q: Explain the State pattern.**
A: An object (Context) delegates state-dependent behavior to a current State object. When state changes, the Context swaps to a different State implementation. Classic example: vending machine or TCP connection — behavior for "insert coin" or "send packet" depends entirely on what state the system is in.

**Q: What's the difference between State and Strategy?**
A: This is the most common interview question. Strategy: the algorithm is selected by the *client* and is stateless; the Context doesn't change Strategy during operation. State: the *state itself* (or Context) drives transitions; the Context's behavior changes over its lifetime. State objects typically hold a back-reference to the Context; Strategy objects typically don't.

**Q: Where does transition logic live?**
A: Two approaches: (1) Context-driven — Context has a big switch/table that maps (currentState, event) to nextState. Centralized but Context becomes complex. (2) State-driven — each ConcreteState calls `context.setState(new NextState())`. Distributed but co-located. Mention that Spring State Machine centralizes transitions in a configuration DSL, which is the cleanest approach for complex machines.

**Q: How would you make a State machine thread-safe?**
A: Use `AtomicReference<State>` for the state field and compare-and-swap to transition atomically. Synchronize any shared data in the Context that states access. Be careful about re-entrant calls (a state transition during notification of another transition).

---

## Cross-Perspective: HLD Connections

**HLD View — Where State Appears in Distributed Systems**

- **Circuit breaker** — The most famous HLD State pattern application: CLOSED (passing through) → OPEN (failing fast) → HALF-OPEN (probing). Each state handles service calls differently. Resilience4j and Hystrix implement this as a State machine.
- **Order lifecycle management** — Distributed order management systems track order state (PENDING → CONFIRMED → SHIPPED → DELIVERED → RETURNED). Each state allows different transitions and different API operations, preventing invalid state jumps like shipping an unconfirmed order.
- **Connection state machines** — TCP connections, WebSocket sessions, and database connections each have state machines. The protocol behavior is entirely determined by the current state — exactly the State pattern motivation.
- **Workflow engines** — Temporal, Apache Airflow, and AWS Step Functions execute workflow definitions as state machines. Each step is a state; transitions are triggered by task completion, timeout, or failure events.

---

## 17. Best Practices

1. **Define a do-nothing base class** — create an abstract `AbstractState` with default no-op implementations of all operations. Concrete states only override the operations they care about, avoiding boilerplate.

2. **Pre-allocate states as singletons** — if states are stateless (all data in Context), store them as static final singletons to avoid object creation overhead on every transition.

3. **Use an enum for simple state machines** — Java enums with abstract methods are a compact alternative to full class hierarchies for simple machines.

4. **Log all state transitions** — invaluable for debugging. Log `previousState → event → newState` on every transition.

5. **Validate transitions** — explicitly reject illegal transitions (e.g., `dispense()` in `IdleState`) rather than silently ignoring them. Throw `IllegalStateException` with a descriptive message.

6. **Consider Spring State Machine for complex workflows** — hand-rolled State pattern is fine for simple machines, but real workflow engines with persistence, history, and parallel states deserve a framework.

7. **Document the state diagram** — draw a finite state machine diagram and keep it in sync with the code. It's the most important documentation for a state-based system.

8. **Keep Context lean** — the Context should contain only shared data and the current state reference. Avoid putting logic in the Context that belongs in a State.
