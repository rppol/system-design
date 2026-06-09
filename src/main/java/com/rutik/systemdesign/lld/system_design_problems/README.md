# LLD System Design Problems

Canonical low-level design interview problems. Each problem exercises pattern identification, state machine design, and the ability to deliver a class design in 30 minutes under pressure.

---

## 1. Concept Overview

LLD interview problems test five distinct skills:

1. **Pattern identification** — recognizing that a Vending Machine is a State pattern problem, not just a class with a big switch statement
2. **Class responsibility assignment** — deciding which class owns which method (SRP in practice)
3. **State machine design** — modeling the lifecycle of an entity (spot status, elevator direction, order state)
4. **Concurrency handling** — reasoning about race conditions (two users booking the same seat)
5. **Communication under pressure** — delivering a coherent design in 30 minutes with a live audience

| Problem | File | Primary Patterns | Has State Machine | Concurrency? |
|---------|------|-----------------|-------------------|-------------|
| Parking Lot | [ParkingLot_README.md](ParkingLot_README.md) | Strategy (pricing), Factory (spot type), State (spot status) | Yes | Yes |
| Elevator System | [ElevatorSystem_README.md](ElevatorSystem_README.md) | State (IDLE/MOVING/DOOR_OPEN), Observer (floor requests) | Yes (complex) | Yes |
| Library Management | [LibraryManagement_README.md](LibraryManagement_README.md) | Builder (borrowing record), Observer (overdue), Strategy (search) | No | Minimal |
| Chess Game | [ChessGame_README.md](ChessGame_README.md) | Composite (board), Command (move history + undo), State (game phase) | Yes | No |
| Vending Machine | [VendingMachine_README.md](VendingMachine_README.md) | State (IDLE/HAS_MONEY/DISPENSING/OUT_OF_STOCK), Strategy (payment), Factory | Yes (primary) | Minimal |
| ATM | [ATM_README.md](ATM_README.md) | State (IDLE/CARD_INSERTED/PIN_VERIFIED/TRANSACTION), Template Method (transaction) | Yes | Minimal |
| Online Booking System | [OnlineBookingSystem_README.md](OnlineBookingSystem_README.md) | Strategy (pricing), Observer (confirmation), Builder (booking record) | Yes | Yes (double-booking) |

---

## 2. Intuition

LLD interview problems are the bridge between abstract design patterns and real software engineering judgment. They test whether you can take a verbal description of a system and produce a class diagram, state machine, and key code snippets in 30 minutes — the same skill needed in engineering design reviews. The interviewer is not looking for a perfect solution; they are looking for structured thinking, awareness of tradeoffs, and the vocabulary to communicate the design.

---

## 3. The 30-Minute LLD Interview Framework

Use this structure in every LLD interview. Interviewers notice when candidates jump to code immediately — it signals junior-level thinking.

```
Minutes 0–5: Requirements Clarification
  - What are the core use cases? (not everything, just the primary flow)
  - What are the scale constraints? (single machine vs distributed?)
  - What are the concurrency requirements? (one user vs many simultaneous?)
  - What does "complete" mean for this problem?
  - Sample question: "Is this a single-machine simulation or a
    distributed system?" Changes the answer significantly.

Minutes 5–10: Entity Identification
  - Nouns in the problem description -> candidate classes
  - Verbs -> candidate methods or responsibilities
  - Relationships -> has-a (composition) or is-a (inheritance)?
  - Example: "Parking Lot has Floors, Floors have Spots, Spots
    have status, Tickets have entry/exit time and cost"

Minutes 10–20: Class Design + State Machine
  - Draw the class diagram (simplified ASCII)
  - Identify the state machine if present (parking spot states, elevator states)
  - Assign responsibilities: which class owns which methods?
  - Pick the GoF pattern that fits the key abstraction
  - Speak aloud: "I'm choosing State pattern here because the behavior
    changes dramatically depending on the current state"

Minutes 20–25: Key Code + Walkthrough
  - Write the most important class or interface in Java
  - Walk through one complete use case end-to-end
  - Show error handling for at least one unhappy path

Minutes 25–30: Handle "Add a Feature" extension
  - Show how the design accommodates a new feature without major rework
  - This tests OCP compliance
  - Example: "Add a disabled parking spot tier"
    -> If design uses SpotType enum + Strategy, this is trivial
    -> If design uses if-else chains, this requires surgery
```

---

## 4. Pattern Selection by Problem Type

| If the problem has... | Reach for... |
|-----------------------|-------------|
| Multiple status transitions (spot, elevator, order) | State pattern |
| Multiple pricing or algorithm variants | Strategy pattern |
| Complex object construction (borrowing record, booking) | Builder pattern |
| Notification on events (overdue books, booking confirmation) | Observer pattern |
| Operations that should be undoable (chess moves) | Command pattern |
| Creating different types of a thing (spot types, ticket types) | Factory Method |
| Recursive tree of identical-interface components | Composite pattern |
| Skeleton process with variable steps (transaction flow) | Template Method |

---

## 5. Concurrency Handling Guide

Problems with concurrency requirements need explicit treatment. The interviewer will always ask "what happens if two users do X simultaneously?"

| Problem | Concurrency Issue | Solution |
|---------|-------------------|---------|
| Parking Lot | Two cars arrive simultaneously for the last available spot | `AtomicReference<SpotStatus>.compareAndSet(AVAILABLE, OCCUPIED)` on single machine; optimistic locking (`version` column) in DB |
| Elevator System | Multiple floor buttons pressed simultaneously | `BlockingQueue` for floor requests; single elevator controller thread processes them sequentially |
| Online Booking System | Two users book the same seat at the same time | Optimistic locking (`WHERE version=? AND status='AVAILABLE'`) or `SELECT FOR UPDATE` pessimistic lock |

**The atomic check-then-act pattern:**

```
BROKEN: check availability, then reserve (two separate operations)
  Thread A: check -> available
  Thread B: check -> available
  Thread A: reserve -> success
  Thread B: reserve -> success (DOUBLE BOOKING)

FIXED: single atomic operation
  Thread A: compareAndSet(AVAILABLE, RESERVED) -> success
  Thread B: compareAndSet(AVAILABLE, RESERVED) -> fails (already RESERVED)
  Thread B: returns "no availability" to the user
```

---

## 6. Common Interview Mistakes

**Starting to code before clarifying requirements** — you code the wrong problem. Interviewers let you code for 20 minutes and then reveal that the problem requires concurrency, which invalidates your single-threaded design. The fix: ask about scale and concurrency in the first 5 minutes.

**Designing for infinite scale when the problem says single machine** — adding distributed locking, Kafka, and microservices to a Parking Lot simulation that is explicitly a single-machine design. Over-engineering signals poor judgment more than a simple design does.

**Using inheritance when composition fits better** — `PremiumSpot extends Spot` instead of `Spot` with a `SpotType` enum + `PricingStrategy`. Inheritance breaks if you add a new dimension (spot size AND spot tier — you can't inherit from two base classes). Composition handles both dimensions independently.

**Forgetting to handle the unhappy path** — what if the card is invalid? What if the book is already checked out? What if the parking lot is full? Interviewers always probe the error cases. Show that your design has a clear error path for at least one failure mode.

**Not naming the design pattern** — interviewers want to hear the vocabulary. "I'm using the State pattern here because the elevator's behavior changes significantly depending on whether it's idle, moving up, or opening doors." Without naming it, the interviewer doesn't know if you recognized the pattern or stumbled into a similar structure accidentally.

**Putting all state logic in a giant switch statement** — this is the code smell that the State pattern exists to fix. A switch with 6 states and 5 operations is 30 branches. Adding a new state adds 5 branches. With State pattern: adding a new state means adding a new class.

---

## 7. State Machine Quick Reference

State machines appear in 5 of the 7 problems. Recognizing the states and transitions early is the key to choosing the right pattern.

**Parking Spot:**
```
AVAILABLE -> OCCUPIED (car parks)
OCCUPIED  -> AVAILABLE (car leaves)
AVAILABLE -> RESERVED (online reservation)
RESERVED  -> OCCUPIED (reserved car arrives)
RESERVED  -> AVAILABLE (reservation expires)
```

**Elevator:**
```
IDLE       -> MOVING_UP    (floor requested above current floor)
IDLE       -> MOVING_DOWN  (floor requested below current floor)
MOVING_UP  -> DOOR_OPEN    (reached target floor)
MOVING_DOWN-> DOOR_OPEN    (reached target floor)
DOOR_OPEN  -> IDLE         (door closes, no pending requests)
DOOR_OPEN  -> MOVING_UP    (door closes, pending request above)
DOOR_OPEN  -> MOVING_DOWN  (door closes, pending request below)
```

**Vending Machine:**
```
IDLE         -> HAS_MONEY    (money inserted)
HAS_MONEY    -> DISPENSING   (item selected, sufficient funds)
HAS_MONEY    -> IDLE         (cancel; money returned)
DISPENSING   -> IDLE         (item dispensed; change returned)
ANY_STATE    -> OUT_OF_STOCK (inventory reaches 0)
OUT_OF_STOCK -> IDLE         (inventory restocked)
```

**ATM:**
```
IDLE          -> CARD_INSERTED   (card inserted)
CARD_INSERTED -> PIN_VERIFIED    (correct PIN entered)
CARD_INSERTED -> IDLE            (wrong PIN 3 times; card ejected)
PIN_VERIFIED  -> TRANSACTION     (amount entered)
TRANSACTION   -> IDLE            (transaction complete or cancelled)
```

**Booking System (Seat):**
```
AVAILABLE -> RESERVED  (user selects seat; 10-min hold)
RESERVED  -> BOOKED    (user completes payment)
RESERVED  -> AVAILABLE (payment timeout; reservation expires)
BOOKED    -> CANCELLED (user cancels; refund issued)
CANCELLED -> AVAILABLE (seat re-listed)
```

---

## 8. Cross-References

| Pattern | See Also |
|---------|---------|
| State (Vending Machine, ATM, Elevator) | `../behavioral/state/` |
| Command (Chess undo) | `../behavioral/command/` |
| Observer (Library overdue, Booking confirmation) | `../behavioral/observer/` |
| Factory (Spot types, ticket types) | `../creational/factory_method/` |
| Concurrency (Parking Lot, Elevator, Booking) | `../concurrency_patterns/` |
| Strategy (pricing, search, payment) | `../behavioral/strategy/` |

---

## 9. Key Class Relationships — ASCII Diagrams

### Parking Lot

```
ParkingLot
  |-- Floor[]
        |-- ParkingSpot[]
              |-- SpotType (enum: COMPACT, LARGE, MOTORCYCLE, DISABLED)
              |-- SpotStatus (enum: AVAILABLE, OCCUPIED, RESERVED)
              |-- PricingStrategy (interface)
                    |-- HourlyPricingStrategy
                    |-- FlatRatePricingStrategy

ParkingTicket
  |-- spot: ParkingSpot
  |-- entryTime: LocalDateTime
  |-- exitTime: LocalDateTime
  |-- totalCost: BigDecimal

ParkingLotController
  |-- findAvailableSpot(vehicleType): ParkingSpot
  |-- park(vehicle): ParkingTicket
  |-- exit(ticket): BigDecimal (amount charged)
```

### Vending Machine (State Pattern)

```
VendingMachine
  |-- currentState: VendingMachineState (interface)
  |-- inventory: Map<Item, Integer>
  |-- balance: BigDecimal

VendingMachineState (interface)
  |-- insertMoney(amount)
  |-- selectItem(item)
  |-- cancel()
  |-- dispense()

  Implementations:
  |-- IdleState
  |-- HasMoneyState
  |-- DispensingState
  |-- OutOfStockState
```

### Online Booking System (Concurrency + Observer)

```
BookingSystem
  |-- seatInventory: Map<SeatId, Seat>
  |-- bookingObservers: List<BookingObserver>

Seat
  |-- seatId: String
  |-- status: SeatStatus (enum: AVAILABLE, RESERVED, BOOKED, CANCELLED)
  |-- version: int  <- optimistic lock field

BookingRecord (Builder pattern)
  |-- bookingId, userId, seatId
  |-- totalPrice: BigDecimal
  |-- bookingTime: LocalDateTime

BookingObserver (interface)
  |-- onBookingConfirmed(BookingRecord)
  Implementations: EmailNotifier, SMSNotifier, InvoiceGenerator
```

---

## 11. Technologies and Tools

| Tool / Framework | Use in LLD Problems |
|-----------------|-------------------|
| Java `enum` with abstract methods | State machine transitions (each enum constant overrides behavior) |
| `AtomicReference.compareAndSet()` | Spot / seat reservation race conditions on single machine |
| `ReentrantLock` | Elevator controller sequential access to request queue |
| `ScheduledExecutorService` | Library overdue notifications without polling |
| `BigDecimal` | Money in ATM and Booking System |
| Spring `@Scheduled` | Production-grade overdue book notification job |
| JPA `@Version` (optimistic locking) | Double-booking prevention in Online Booking System |
| `Deque<Command>` | Chess move history for undo/redo |

---

## 12. Interview Q&As

Q&As ordered by interview frequency: gotchas and traps first, internals second, edge cases last.

---

**How do you start a 30-minute LLD interview? What's your first sentence?**

Ask clarifying questions before drawing anything. "Before I start, I want to make sure I'm solving the right problem — can I ask a few questions about scope?" Then: single machine or distributed? How many concurrent users? What are the must-have use cases vs nice-to-haves? Is this interview more interested in class design, state machine, or concurrency handling? This opening shows senior-level judgment — junior candidates jump straight to coding.

---

**Parking Lot: how do you handle two cars arriving simultaneously for the last spot?**

If on a single machine: use `AtomicReference<SpotStatus>` with `compareAndSet(AVAILABLE, OCCUPIED)` — only one thread wins the CAS; the other retries and finds no available spot. If distributed: use optimistic locking in the database (add a `version` column; `UPDATE spot SET status='OCCUPIED', version=version+1 WHERE id=? AND version=?` — exactly one update succeeds). The key insight: spot assignment must be atomic. Do NOT check availability and then reserve in two separate operations — this is a classic check-then-act race condition.

---

**Vending Machine: why is the State pattern better than a switch statement for state transitions?**

A switch statement puts all state logic in one class, violating OCP — adding a new state requires modifying the switch. As states accumulate, the switch becomes unreadable and error-prone. State pattern: each state is a class; transitions are method calls that replace the current state object. Adding a new state means adding a new class and modifying only the states that transition to it — not the entire machine. The State pattern also makes illegal transitions explicit: an `OutOfStockState` simply doesn't implement `acceptMoney()` with success behavior.

---

**Chess: how does the Command pattern enable undo of moves?**

Each move is a `Command` object: `MoveCommand(piece, fromSquare, toSquare, capturedPiece)`. `execute()` moves the piece; `undo()` moves it back and restores the captured piece. A `Deque<MoveCommand>` is the history stack. Ctrl+Z pops the stack and calls `undo()`. The benefit: the `Board` class doesn't need any undo logic — it just responds to `move()` and `restore()` calls. The history management is entirely in the `MoveCommand` and the client. Chess engines also use this for "what-if" analysis: execute a speculative move, evaluate the board, undo it.

---

**ATM: what happens if power fails mid-transaction? How do you design for recovery?**

Each transaction must be idempotent: if the ATM dispenses cash and then power fails before writing the debit to the ledger, the debit should be recorded on recovery. Design: log the transaction intent to durable storage (a transaction log) BEFORE dispensing cash. On power-on, replay uncommitted transactions. This is the same write-ahead log (WAL) pattern used by databases. For the interview: mention that the ATM state machine must have a `DISPENSING` state that, on recovery, either completes the dispense or rolls back the debit — never leaves the account in an ambiguous state.

---

**Online Booking System: how do you prevent double-booking of the same seat?**

Option 1 (optimistic locking): add a `version` field to the seat record; the booking transaction does `UPDATE seat SET status='BOOKED', version=version+1 WHERE id=? AND status='AVAILABLE' AND version=?`. If 0 rows updated, another transaction won the race — return a conflict error. Option 2 (pessimistic locking): `SELECT * FROM seat WHERE id=? FOR UPDATE` — acquires a row-level lock, serializing concurrent bookings. Optimistic is preferred for high read-to-write ratios; pessimistic is preferred when conflicts are frequent. For the interview: mention both and explain the tradeoff.

---

**Library Management: how do you notify members about overdue books without polling the database?**

Schedule a daily job (Spring `@Scheduled`, cron, or a batch job) that queries all unreturned books past their due date and publishes overdue events. Observers (email sender, SMS sender, in-app notification) consume the events. Decoupling: adding a new notification channel (push notification) means adding a new Observer — no change to the scheduler or query logic. Alternative: event-driven — on each book checkout, schedule a future event (`ScheduledExecutorService.schedule()` or a job queue) that fires on the due date. The event-driven approach doesn't require daily polling.

---

**Elevator System: what scheduling algorithm should you use?**

SCAN (also called the "elevator algorithm"): the elevator moves in one direction, stopping at all requested floors, then reverses. LOOK variant: reverse when no more requests in the current direction (don't go to the top floor if the last request is floor 7). FCFS (First Come First Served) is simple but causes large variance in wait time. For the interview: mention SCAN as the baseline, note that modern elevators use destination dispatch (you enter your destination floor before entering the elevator, grouping passengers going to the same floor). The State pattern models the elevator's direction (MOVING_UP, MOVING_DOWN, IDLE) and door state (DOOR_OPEN, DOOR_CLOSED).

---

**How do you represent money in ATM or Booking System? Why not float?**

Use `BigDecimal` for exact decimal arithmetic, or represent money as the smallest currency unit in a `long` (e.g., cents for USD). `float` and `double` are binary floating-point and cannot represent 0.1 exactly (`0.1 + 0.2 != 0.3` in IEEE 754). For monetary calculations, rounding errors compound: a 0.0001 error per transaction multiplied by 10 million transactions equals thousands of dollars in discrepancy. `BigDecimal(String)` (not `BigDecimal(double)`) is precise; `RoundingMode.HALF_EVEN` (banker's rounding) minimizes systematic bias. The Money pattern (Fowler) wraps `BigDecimal` with a `Currency` to prevent mixing USD and EUR accidentally.

---

**When asked to "add a feature" mid-interview, how do you handle it gracefully?**

Show that the design is open for extension without modification (OCP). Example: "add a premium parking spot tier." If the design uses a `SpotType` enum + Strategy for pricing, adding premium means: add `PREMIUM` to the enum, add a `PremiumPricingStrategy` class — nothing else changes. If the design used `if (type == COMPACT) price = 2` hard-coded, adding premium requires touching that method. Use the "add a feature" moment to demonstrate OCP compliance, not to improvise. The best answer: "I anticipated extensibility here — let me show you how this works."

---

**How do you handle the Library Management "search" feature in the class design?**

Use the Strategy pattern for search: `SearchStrategy` interface with implementations like `TitleSearch`, `AuthorSearch`, `ISBNSearch`, `GenreSearch`. The `Library.search(String query, SearchStrategy strategy)` method delegates to the strategy. Adding a new search type (publication year, keywords) means adding a new strategy class — no change to `Library`. Alternative for simple cases: one `Catalog` class with multiple overloaded `findBy*()` methods. The Strategy approach is justified when search algorithms differ significantly in implementation (linear scan vs inverted index vs external search engine).

---
