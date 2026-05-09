package com.rutik.systemdesign.lld.system_design_problems;

import java.util.*;

// =============================================================================
//  ELEVATOR SYSTEM — Low-Level Design
//  Patterns used:
//    - State    : Elevator states (Moving, Stopped, Maintenance)
//    - Strategy : Scheduling algorithm (FCFS, SCAN)
//    - Observer : Arrival notifications to waiting passengers
// =============================================================================

// ─────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────

enum Direction     { UP, DOWN, IDLE }
enum ElevatorState { MOVING, STOPPED, MAINTENANCE }

// ─────────────────────────────────────────────
//  REQUEST — a floor call with a desired direction
// ─────────────────────────────────────────────

class Request {
    private final int       floor;
    private final Direction direction;

    public Request(int floor, Direction direction) {
        this.floor     = floor;
        this.direction = direction;
    }

    public int       getFloor()     { return floor; }
    public Direction getDirection() { return direction; }

    @Override
    public String toString() { return "Request(floor=" + floor + ", dir=" + direction + ")"; }
}

// ─────────────────────────────────────────────
//  OBSERVER PATTERN — Arrival notifications
//  Observers register interest in a specific floor and are notified on arrival.
// ─────────────────────────────────────────────

interface ElevatorObserver {
    void onArrival(int elevatorId, int floor);
}

/** Simulates a passenger waiting at a floor. */
class WaitingPassenger implements ElevatorObserver {
    private final String name;
    private final int    waitingFloor;

    public WaitingPassenger(String name, int waitingFloor) {
        this.name         = name;
        this.waitingFloor = waitingFloor;
    }

    @Override
    public void onArrival(int elevatorId, int floor) {
        if (floor == waitingFloor) {
            System.out.printf("  [Passenger %s] Elevator %d arrived at floor %d — boarding!%n",
                    name, elevatorId, floor);
        }
    }
}

// ─────────────────────────────────────────────
//  STATE PATTERN — Elevator states
//  Each state defines which operations are legal and drives state transitions.
// ─────────────────────────────────────────────

interface ElevatorStateHandler {
    void requestFloor(ElevatorCar elevator, int floor);
    void openDoor(ElevatorCar elevator);
    void closeDoor(ElevatorCar elevator);
    void emergencyStop(ElevatorCar elevator);
    String stateName();
}

/** State: elevator is moving between floors. */
class MovingState implements ElevatorStateHandler {
    @Override
    public void requestFloor(ElevatorCar elevator, int floor) {
        elevator.addDestination(floor);
        System.out.printf("  [Elevator %d | MOVING] Queued floor %d%n", elevator.getId(), floor);
    }

    @Override
    public void openDoor(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | MOVING] Cannot open door while moving.%n", elevator.getId());
    }

    @Override
    public void closeDoor(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | MOVING] Door already closed.%n", elevator.getId());
    }

    @Override
    public void emergencyStop(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | MOVING] EMERGENCY STOP at floor %d!%n",
                elevator.getId(), elevator.getCurrentFloor());
        elevator.setState(new MaintenanceState());
    }

    @Override
    public String stateName() { return "MOVING"; }
}

/** State: elevator is stopped at a floor, doors may open. */
class StoppedState implements ElevatorStateHandler {
    @Override
    public void requestFloor(ElevatorCar elevator, int floor) {
        elevator.addDestination(floor);
        System.out.printf("  [Elevator %d | STOPPED] Floor %d added. Starting movement.%n",
                elevator.getId(), floor);
        elevator.setState(new MovingState());
    }

    @Override
    public void openDoor(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | STOPPED] Doors opening at floor %d.%n",
                elevator.getId(), elevator.getCurrentFloor());
        elevator.notifyObservers(elevator.getCurrentFloor());
    }

    @Override
    public void closeDoor(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | STOPPED] Doors closing.%n", elevator.getId());
    }

    @Override
    public void emergencyStop(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | STOPPED] Emergency: placing in maintenance.%n", elevator.getId());
        elevator.setState(new MaintenanceState());
    }

    @Override
    public String stateName() { return "STOPPED"; }
}

/** State: elevator is under maintenance and cannot serve requests. */
class MaintenanceState implements ElevatorStateHandler {
    @Override
    public void requestFloor(ElevatorCar elevator, int floor) {
        System.out.printf("  [Elevator %d | MAINTENANCE] Cannot accept requests.%n", elevator.getId());
    }

    @Override
    public void openDoor(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | MAINTENANCE] Doors locked.%n", elevator.getId());
    }

    @Override
    public void closeDoor(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | MAINTENANCE] Doors locked.%n", elevator.getId());
    }

    @Override
    public void emergencyStop(ElevatorCar elevator) {
        System.out.printf("  [Elevator %d | MAINTENANCE] Already in maintenance.%n", elevator.getId());
    }

    @Override
    public String stateName() { return "MAINTENANCE"; }
}

// ─────────────────────────────────────────────
//  ELEVATOR CAR (Context for State pattern)
// ─────────────────────────────────────────────

class ElevatorCar {
    private final int               id;
    private int                     currentFloor;
    private Direction               direction;
    private ElevatorStateHandler    state;
    private final TreeSet<Integer>  destinations = new TreeSet<>();
    private final List<ElevatorObserver> observers = new ArrayList<>();

    public ElevatorCar(int id, int initialFloor) {
        this.id           = id;
        this.currentFloor = initialFloor;
        this.direction    = Direction.IDLE;
        this.state        = new StoppedState();  // starts stopped
    }

    // ── State pattern delegation ──────────────────────────────────────────
    public void requestFloor(int floor)  { state.requestFloor(this, floor); }
    public void openDoor()               { state.openDoor(this); }
    public void closeDoor()              { state.closeDoor(this); }
    public void emergencyStop()          { state.emergencyStop(this); }

    // ── Internal helpers used by state objects ────────────────────────────
    public void setState(ElevatorStateHandler newState) {
        System.out.printf("  [Elevator %d] State: %s → %s%n", id, state.stateName(), newState.stateName());
        this.state = newState;
    }

    public void addDestination(int floor) { destinations.add(floor); }

    /** Moves one step towards the next destination in the queue. */
    public void step() {
        if (destinations.isEmpty()) {
            direction = Direction.IDLE;
            if (!(state instanceof StoppedState)) setState(new StoppedState());
            return;
        }

        int next;
        if (direction == Direction.UP || direction == Direction.IDLE) {
            Integer higher = destinations.ceiling(currentFloor + 1);
            next = (higher != null) ? higher : destinations.first();
        } else {
            Integer lower = destinations.floor(currentFloor - 1);
            next = (lower != null) ? lower : destinations.last();
        }

        if (next > currentFloor)      direction = Direction.UP;
        else if (next < currentFloor) direction = Direction.DOWN;

        currentFloor = next;
        destinations.remove(next);

        System.out.printf("  [Elevator %d] Arrived at floor %d (dir=%s, remaining=%s)%n",
                id, currentFloor, direction, destinations);
        setState(new StoppedState());
        openDoor();
        closeDoor();

        if (destinations.isEmpty()) direction = Direction.IDLE;
        else                         setState(new MovingState());
    }

    // ── Observer pattern ──────────────────────────────────────────────────
    public void addObserver(ElevatorObserver o)    { observers.add(o); }
    public void removeObserver(ElevatorObserver o) { observers.remove(o); }
    public void notifyObservers(int floor) {
        for (ElevatorObserver o : observers) o.onArrival(id, floor);
    }

    // ── Getters ───────────────────────────────────────────────────────────
    public int                   getId()            { return id; }
    public int                   getCurrentFloor()  { return currentFloor; }
    public Direction             getDirection()     { return direction; }
    public ElevatorStateHandler  getState()         { return state; }
    public boolean               isAvailable()      { return !(state instanceof MaintenanceState); }
    public int                   pendingStops()     { return destinations.size(); }

    @Override
    public String toString() {
        return String.format("Elevator[id=%d, floor=%d, dir=%s, state=%s, pending=%s]",
                id, currentFloor, direction, state.stateName(), destinations);
    }
}

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — Elevator Scheduling
//  Selects which elevator should handle an incoming request.
// ─────────────────────────────────────────────

interface ElevatorScheduler {
    /** Returns the best elevator to handle the request, or null if none available. */
    ElevatorCar selectElevator(List<ElevatorCar> elevators, Request request);
}

/**
 * FCFS Scheduler — assigns request to the elevator with the fewest pending stops.
 * Simple and fair but can result in unnecessary travel.
 */
class FCFSScheduler implements ElevatorScheduler {
    @Override
    public ElevatorCar selectElevator(List<ElevatorCar> elevators, Request request) {
        return elevators.stream()
                .filter(ElevatorCar::isAvailable)
                .min(Comparator.comparingInt(ElevatorCar::pendingStops))
                .orElse(null);
    }
}

/**
 * SCAN Scheduler (elevator / look algorithm) —
 * Prefers an elevator already moving in the right direction that hasn't passed
 * the requested floor yet. Falls back to the closest idle elevator.
 */
class SCANScheduler implements ElevatorScheduler {
    @Override
    public ElevatorCar selectElevator(List<ElevatorCar> elevators, Request request) {
        int requestedFloor = request.getFloor();
        ElevatorCar best    = null;
        int         minCost = Integer.MAX_VALUE;

        for (ElevatorCar e : elevators) {
            if (!e.isAvailable()) continue;
            int cost = computeCost(e, requestedFloor, request.getDirection());
            if (cost < minCost) {
                minCost = cost;
                best    = e;
            }
        }
        return best;
    }

    private int computeCost(ElevatorCar e, int targetFloor, Direction requestDir) {
        int dist = Math.abs(e.getCurrentFloor() - targetFloor);
        Direction eDir = e.getDirection();

        // Bonus (lower cost) if moving same direction and hasn't passed the floor
        if (eDir == Direction.IDLE)                         return dist;
        if (eDir == Direction.UP   && eDir == requestDir && e.getCurrentFloor() <= targetFloor) return dist;
        if (eDir == Direction.DOWN && eDir == requestDir && e.getCurrentFloor() >= targetFloor) return dist;
        // Penalty: elevator needs to reverse
        return dist + 10;
    }
}

// ─────────────────────────────────────────────
//  ELEVATOR CONTROLLER — manages one elevator's lifecycle
// ─────────────────────────────────────────────

class ElevatorController {
    private final ElevatorCar elevator;

    public ElevatorController(ElevatorCar elevator) {
        this.elevator = elevator;
    }

    public void addRequest(int floor) {
        System.out.printf("[Controller] Elevator %d: floor %d requested%n", elevator.getId(), floor);
        elevator.requestFloor(floor);
    }

    /** Runs all pending stops in sequence. */
    public void processAllRequests() {
        System.out.printf("[Controller] Elevator %d: processing queue — %s%n",
                elevator.getId(), elevator);
        while (elevator.pendingStops() > 0) {
            elevator.step();
        }
    }

    public ElevatorCar getElevator() { return elevator; }
}

// ─────────────────────────────────────────────
//  ELEVATOR SYSTEM — top-level coordinator
// ─────────────────────────────────────────────

class ElevatorSystem {
    private final int                        totalFloors;
    private final List<ElevatorController>   controllers = new ArrayList<>();
    private final List<ElevatorCar>          elevators   = new ArrayList<>();
    private ElevatorScheduler                scheduler;

    public ElevatorSystem(int totalFloors, ElevatorScheduler scheduler) {
        this.totalFloors = totalFloors;
        this.scheduler   = scheduler;
    }

    public void addElevator(ElevatorCar car) {
        elevators.add(car);
        controllers.add(new ElevatorController(car));
    }

    public void setScheduler(ElevatorScheduler scheduler) { this.scheduler = scheduler; }

    /**
     * External hall call: a person at `floor` presses the `direction` button.
     * The scheduler picks the best elevator and dispatches it.
     */
    public void requestElevator(int floor, Direction direction) {
        if (floor < 1 || floor > totalFloors) {
            System.out.println("[System] Invalid floor: " + floor);
            return;
        }
        Request request = new Request(floor, direction);
        ElevatorCar selected = scheduler.selectElevator(elevators, request);
        if (selected == null) {
            System.out.println("[System] No available elevator for " + request);
            return;
        }
        System.out.printf("[System] %s → assigned to Elevator %d%n", request, selected.getId());
        selected.requestFloor(floor);
    }

    /** Runs all pending requests across all elevators. */
    public void runAll() {
        for (ElevatorController c : controllers) {
            c.processAllRequests();
        }
    }

    public void printStatus() {
        System.out.println("[System] Current elevator status:");
        for (ElevatorCar e : elevators) System.out.println("  " + e);
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class ElevatorSystem {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Elevator System — LLD Demo");
        System.out.println("========================================\n");

        // 1. Build system: 10-floor building, 2 elevators, SCAN scheduler
        elevator.lld.ElevatorSystem system = new elevator.lld.ElevatorSystem(10, new SCANScheduler());

        ElevatorCar e1 = new ElevatorCar(1, 1);   // starts at floor 1
        ElevatorCar e2 = new ElevatorCar(2, 5);   // starts at floor 5

        // Observer pattern: attach waiting passengers
        WaitingPassenger alice = new WaitingPassenger("Alice", 3);
        WaitingPassenger bob   = new WaitingPassenger("Bob",   7);
        e1.addObserver(alice);
        e2.addObserver(bob);

        system.addElevator(e1);
        system.addElevator(e2);

        // 2. Initial status
        System.out.println("--- Initial state ---");
        system.printStatus();

        // 3. Hall calls from multiple floors
        System.out.println("\n--- Hall calls ---");
        system.requestElevator(3, Direction.UP);    // Alice waiting → should go to E1
        system.requestElevator(7, Direction.DOWN);  // Bob waiting → should go to E2
        system.requestElevator(2, Direction.UP);
        system.requestElevator(9, Direction.DOWN);

        // 4. Cabin button presses (passengers already inside pressing destination)
        System.out.println("\n--- Inside-cabin destination presses ---");
        e1.requestFloor(6);
        e2.requestFloor(1);

        System.out.println("\n--- Processing all requests ---");
        system.runAll();

        // 5. State pattern demo: emergency stop
        System.out.println("\n--- Emergency stop on Elevator 1 ---");
        e1.requestFloor(8);
        e1.emergencyStop();   // transitions to MaintenanceState
        e1.requestFloor(9);   // should be rejected

        System.out.println("\n--- Status after emergency ---");
        system.printStatus();

        // 6. Switch to FCFS scheduler
        System.out.println("\n--- Switching to FCFS scheduler ---");
        system.setScheduler(new FCFSScheduler());
        system.requestElevator(4, Direction.UP);
        system.runAll();

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
