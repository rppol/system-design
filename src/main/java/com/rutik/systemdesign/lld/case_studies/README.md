# LLD Case Studies — Learning Path

The seven problems in `../system_design_problems/` serve as the practical interview case studies
for Low-Level Design. This README provides the learning path, pattern dependency map, and
interview preparation shortcuts.

---

## 1. Quick Start

Read these three first, in order:

| Problem | File | Why First |
|---------|------|-----------|
| Vending Machine | [VendingMachine_README.md](../system_design_problems/VendingMachine_README.md) | Cleanest State pattern implementation — 4 states, tight FSM, easy to draw in 30 min |
| Parking Lot | [ParkingLot_README.md](../system_design_problems/ParkingLot_README.md) | Combines Factory + Strategy + State; most common LLD interview question |
| ATM | [ATM_README.md](../system_design_problems/ATM_README.md) | Template Method skeleton + State machine; introduces transaction idempotency concerns |

These three cover State (FSM), Factory, Strategy, and Template Method — the four patterns that
appear in every other problem. Once you can draw these three designs cold, the remaining four
become variations.

---

## 2. Full Learning Path

Problems grouped by the dominant engineering concern they exercise:

### Group A — State Machines (Start Here)

| Problem | Dominant Concern | File | Core Patterns |
|---------|-----------------|------|--------------|
| Vending Machine | FSM design — 4 states, clean transitions | [VendingMachine_README.md](../system_design_problems/VendingMachine_README.md) | State, Strategy (payment), Factory (product) |
| ATM | FSM + transaction integrity | [ATM_README.md](../system_design_problems/ATM_README.md) | State, Template Method (transaction flow), Command |
| Elevator System | Complex FSM + scheduling algorithm | [ElevatorSystem_README.md](../system_design_problems/ElevatorSystem_README.md) | State, Observer (floor requests), Strategy (SCAN/LOOK/FCFS) |

### Group B — Concurrency + Resource Management

| Problem | Dominant Concern | File | Core Patterns |
|---------|-----------------|------|--------------|
| Parking Lot | Concurrent spot allocation, pricing strategy | [ParkingLot_README.md](../system_design_problems/ParkingLot_README.md) | Factory (spot type), Strategy (pricing), State (spot status) |
| Online Booking System | Double-booking prevention, seat reservation | [OnlineBookingSystem_README.md](../system_design_problems/OnlineBookingSystem_README.md) | Strategy (pricing), Observer (notifications), Builder (booking record) |

### Group C — Domain Modeling

| Problem | Dominant Concern | File | Core Patterns |
|---------|-----------------|------|--------------|
| Library Management | Borrow/return lifecycle, overdue notifications | [LibraryManagement_README.md](../system_design_problems/LibraryManagement_README.md) | Builder (borrow record), Observer (overdue), Strategy (search) |
| Chess Game | Move validation, undo/redo, game phases | [ChessGame_README.md](../system_design_problems/ChessGame_README.md) | Composite (board), Command (move + undo), State (game phase) |

---

## 3. Cross-Cutting Pattern Matrix

Which GoF patterns appear in which problems — use this to decide which problems to study
when preparing for a specific pattern question:

| Pattern | Vending | Parking | Library | Chess | Elevator | ATM | Booking |
|---------|---------|---------|---------|-------|----------|-----|---------|
| State | Primary | Supporting | — | Supporting | Primary | Primary | Supporting |
| Strategy | Supporting | Primary | Supporting | — | Supporting | — | Primary |
| Factory | Supporting | Primary | — | — | — | — | — |
| Observer | — | — | Primary | — | Supporting | — | Supporting |
| Command | — | — | — | Primary | — | Supporting | — |
| Builder | — | — | Supporting | — | — | — | Supporting |
| Template Method | — | — | — | — | — | Primary | — |
| Composite | — | Supporting | — | Primary | — | — | — |

**Legend**: Primary = the pattern is the main architectural decision; Supporting = the pattern
appears as a secondary component.

---

## 4. Dependency Map

Conceptual dependencies — study problems lower in the tree first:

```
Foundation (study first)
  |
  +-- Vending Machine         [State FSM, 4 states, clean]
  |       |
  |       v
  +-- ATM                     [State + Template Method + idempotency]
  |       |
  |       v
  +-- Parking Lot             [Factory + Strategy + concurrent access]
  |       |
  |       v
  |   +---+-------------------+
  |   |                       |
  |   v                       v
  +-- Elevator System         Online Booking System
  |   [Complex FSM +          [Concurrency + Strategy +
  |    scheduling algo]        double-booking prevention]
  |
  v
Chess Game                    Library Management
[Command undo/redo +          [Observer + Builder +
 Composite board]              borrow lifecycle]
```

**Why this order**: Vending Machine's clean 4-state FSM is the template you'll reuse in every
other state-machine problem. ATM adds the transaction integrity concern. Parking Lot adds Factory
and Strategy. Elevator extends the FSM complexity. Chess and Library are standalone but assume
you can already identify patterns quickly.

---

## 5. Interview Prep Shortcuts

| "Design X" interview question | Best case study to study | Why |
|------------------------------|--------------------------|-----|
| Design a vending machine | Vending Machine | Direct match |
| Design an ATM | ATM | Direct match |
| Design a parking system / lot | Parking Lot | Direct match |
| Design an elevator / lift | Elevator System | Direct match |
| Design a library management system | Library Management | Direct match |
| Design a chess game | Chess Game | Direct match |
| Design a movie / flight / hotel booking | Online Booking System | Pattern is identical |
| Design a traffic light system | Vending Machine | Same 4-state FSM structure |
| Design a food delivery order lifecycle | ATM + Booking | State machine + double-allocation |
| Design a ride-sharing trip lifecycle | Parking Lot + Elevator | Resource allocation + FSM |
| Design a bank transaction system | ATM | Transaction integrity, rollback, idempotency |
| Design a document editor with undo | Chess Game | Command pattern undo/redo |
| Design a notification system | Library Management | Observer pattern, overdue/event triggers |

### 30-Minute Interview Time Box

```
0–5 min   Clarify requirements: scale, concurrency, extensibility asks
5–10 min  Identify entities (nouns → classes) and relationships
10–20 min Draw class diagram: key classes, interfaces, relationships
          Identify and NAME the patterns you're using
20–25 min Walk through one key scenario end-to-end (e.g., purchase flow)
25–30 min Discuss extensibility: "If I needed to add X, I would..."
```

The most common failure mode: spending 15+ minutes on requirements and running out of time
for the class diagram. Timebox requirements to 5 minutes maximum.

---

## Cross-References

| LLD Concern | See Also |
|-------------|---------|
| State machine depth | [../behavioral/state/](../behavioral/state/) |
| Command pattern (undo/redo) | [../behavioral/command/](../behavioral/command/) |
| Observer (notifications) | [../behavioral/observer/](../behavioral/observer/) |
| Factory + Strategy combo | [../creational/factory_method/](../creational/factory_method/), [../behavioral/strategy/](../behavioral/strategy/) |
| Concurrency in Parking/Elevator | [../concurrency_patterns/README.md](../concurrency_patterns/README.md) |
| Distributed scale of these problems | [../../hld/microservices/](../../hld/microservices/) |
