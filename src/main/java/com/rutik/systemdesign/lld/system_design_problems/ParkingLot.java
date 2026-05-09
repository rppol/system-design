package com.rutik.systemdesign.lld.system_design_problems;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

// =============================================================================
//  PARKING LOT — Low-Level Design
//  Patterns used:
//    - Singleton  : ParkingLotSystem (single lot instance)
//    - Strategy   : PricingStrategy (HourlyPricing, DailyPricing, WeekendPricing)
//    - Observer   : ParkingSpotObserver / DisplayBoard (availability updates)
//    - Factory    : VehicleFactory (creates concrete Vehicle subtypes)
// =============================================================================

// ─────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────

enum VehicleType       { MOTORCYCLE, CAR, TRUCK }
enum ParkingSpotType   { SMALL, MEDIUM, LARGE }
enum TicketStatus      { ACTIVE, PAID, LOST }

// ─────────────────────────────────────────────
//  VEHICLES  — simple inheritance hierarchy
// ─────────────────────────────────────────────

abstract class Vehicle {
    protected final String licensePlate;
    protected final VehicleType type;

    public Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type         = type;
    }

    public String      getLicensePlate() { return licensePlate; }
    public VehicleType getType()         { return type; }

    /** Returns the minimum spot size this vehicle requires. */
    public abstract ParkingSpotType requiredSpotType();

    @Override
    public String toString() { return type + "(" + licensePlate + ")"; }
}

class Motorcycle extends Vehicle {
    public Motorcycle(String plate) { super(plate, VehicleType.MOTORCYCLE); }
    @Override public ParkingSpotType requiredSpotType() { return ParkingSpotType.SMALL; }
}

class Car extends Vehicle {
    public Car(String plate) { super(plate, VehicleType.CAR); }
    @Override public ParkingSpotType requiredSpotType() { return ParkingSpotType.MEDIUM; }
}

class Truck extends Vehicle {
    public Truck(String plate) { super(plate, VehicleType.TRUCK); }
    @Override public ParkingSpotType requiredSpotType() { return ParkingSpotType.LARGE; }
}

// ─────────────────────────────────────────────
//  FACTORY PATTERN — Vehicle creation
//  Decouples callers from concrete Vehicle subclasses.
// ─────────────────────────────────────────────

class VehicleFactory {
    public static Vehicle create(VehicleType type, String plate) {
        return switch (type) {
            case MOTORCYCLE -> new Motorcycle(plate);
            case CAR        -> new Car(plate);
            case TRUCK      -> new Truck(plate);
        };
    }
}

// ─────────────────────────────────────────────
//  PARKING SPOTS
// ─────────────────────────────────────────────

abstract class ParkingSpot {
    protected final String spotId;
    protected final ParkingSpotType spotType;
    protected Vehicle currentVehicle;

    public ParkingSpot(String spotId, ParkingSpotType spotType) {
        this.spotId   = spotId;
        this.spotType = spotType;
    }

    public boolean isAvailable() { return currentVehicle == null; }

    public void park(Vehicle v) {
        if (!isAvailable()) throw new IllegalStateException("Spot " + spotId + " is already occupied.");
        currentVehicle = v;
    }

    public void vacate() { currentVehicle = null; }

    public String          getSpotId()       { return spotId; }
    public ParkingSpotType getSpotType()     { return spotType; }
    public Vehicle         getCurrentVehicle() { return currentVehicle; }

    @Override
    public String toString() {
        return spotType + "-" + spotId + (isAvailable() ? "[FREE]" : "[OCCUPIED by " + currentVehicle + "]");
    }
}

class SmallSpot  extends ParkingSpot { public SmallSpot(String id)  { super(id, ParkingSpotType.SMALL);  } }
class MediumSpot extends ParkingSpot { public MediumSpot(String id) { super(id, ParkingSpotType.MEDIUM); } }
class LargeSpot  extends ParkingSpot { public LargeSpot(String id)  { super(id, ParkingSpotType.LARGE);  } }

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — Pricing
//  Each concrete strategy encapsulates one pricing algorithm.
//  The lot can switch strategies at runtime without changing client code.
// ─────────────────────────────────────────────

interface PricingStrategy {
    /** Returns the total fee in dollars for the given parking duration and spot size. */
    double calculateFee(Duration duration, ParkingSpotType spotType);
}

class HourlyPricing implements PricingStrategy {
    private static final Map<ParkingSpotType, Double> RATES = Map.of(
        ParkingSpotType.SMALL,  2.0,
        ParkingSpotType.MEDIUM, 3.5,
        ParkingSpotType.LARGE,  5.0
    );

    @Override
    public double calculateFee(Duration duration, ParkingSpotType spotType) {
        long hours = Math.max(1, (long) Math.ceil(duration.toMinutes() / 60.0));
        return hours * RATES.get(spotType);
    }
}

class DailyPricing implements PricingStrategy {
    private static final Map<ParkingSpotType, Double> DAILY_RATES = Map.of(
        ParkingSpotType.SMALL,  15.0,
        ParkingSpotType.MEDIUM, 25.0,
        ParkingSpotType.LARGE,  40.0
    );

    @Override
    public double calculateFee(Duration duration, ParkingSpotType spotType) {
        long days = Math.max(1, (long) Math.ceil(duration.toHours() / 24.0));
        return days * DAILY_RATES.get(spotType);
    }
}

/** Weekend pricing: flat fee per entry regardless of duration. */
class WeekendPricing implements PricingStrategy {
    private static final Map<ParkingSpotType, Double> WEEKEND_FLAT = Map.of(
        ParkingSpotType.SMALL,  8.0,
        ParkingSpotType.MEDIUM, 12.0,
        ParkingSpotType.LARGE,  18.0
    );

    @Override
    public double calculateFee(Duration duration, ParkingSpotType spotType) {
        return WEEKEND_FLAT.get(spotType);  // flat rate ignores duration
    }
}

// ─────────────────────────────────────────────
//  OBSERVER PATTERN — Display boards
//  Observers are notified whenever spot availability changes on a floor.
// ─────────────────────────────────────────────

interface ParkingSpotObserver {
    void onAvailabilityChanged(String floorId, ParkingSpotType type, int availableCount);
}

/** Concrete observer: a digital display board that prints availability. */
class DisplayBoard implements ParkingSpotObserver {
    private final String boardId;

    public DisplayBoard(String boardId) { this.boardId = boardId; }

    @Override
    public void onAvailabilityChanged(String floorId, ParkingSpotType type, int availableCount) {
        System.out.printf("  [Board-%s] Floor %-3s | %-8s available: %d%n",
                boardId, floorId, type, availableCount);
    }
}

// ─────────────────────────────────────────────
//  PARKING TICKET
// ─────────────────────────────────────────────

class ParkingTicket {
    private static int counter = 1000;

    private final String          ticketId;
    private final Vehicle         vehicle;
    private final ParkingSpot     spot;
    private final LocalDateTime   entryTime;
    private LocalDateTime         exitTime;
    private TicketStatus          status;
    private double                fee;

    public ParkingTicket(Vehicle vehicle, ParkingSpot spot) {
        this.ticketId  = "TKT-" + (++counter);
        this.vehicle   = vehicle;
        this.spot      = spot;
        this.entryTime = LocalDateTime.now();
        this.status    = TicketStatus.ACTIVE;
    }

    public void markPaid(double fee) {
        this.fee      = fee;
        this.exitTime = LocalDateTime.now();
        this.status   = TicketStatus.PAID;
    }

    public Duration getParkingDuration() {
        LocalDateTime end = (exitTime != null) ? exitTime : LocalDateTime.now();
        return Duration.between(entryTime, end);
    }

    public String      getTicketId() { return ticketId; }
    public Vehicle     getVehicle()  { return vehicle; }
    public ParkingSpot getSpot()     { return spot; }
    public TicketStatus getStatus()  { return status; }
    public double      getFee()      { return fee; }

    @Override
    public String toString() {
        return String.format("Ticket[%s | %s | Spot:%s | Entry:%s | Status:%s]",
                ticketId, vehicle, spot.getSpotId(), entryTime, status);
    }
}

// ─────────────────────────────────────────────
//  PARKING FLOOR
// ─────────────────────────────────────────────

class ParkingFloor {
    private final String floorId;
    // Spots grouped by type for fast lookup
    private final Map<ParkingSpotType, List<ParkingSpot>> spotsByType = new EnumMap<>(ParkingSpotType.class);
    // Observer list (Observer pattern)
    private final List<ParkingSpotObserver> observers = new ArrayList<>();

    public ParkingFloor(String floorId) {
        this.floorId = floorId;
        for (ParkingSpotType t : ParkingSpotType.values()) spotsByType.put(t, new ArrayList<>());
    }

    public void addSpot(ParkingSpot spot)             { spotsByType.get(spot.getSpotType()).add(spot); }
    public void addObserver(ParkingSpotObserver o)    { observers.add(o); }
    public void removeObserver(ParkingSpotObserver o) { observers.remove(o); }

    /** Finds the first available spot of the requested type; returns null if none. */
    public ParkingSpot findAvailableSpot(ParkingSpotType type) {
        return spotsByType.get(type).stream().filter(ParkingSpot::isAvailable).findFirst().orElse(null);
    }

    public void parkVehicle(ParkingSpot spot, Vehicle vehicle) {
        spot.park(vehicle);
        notifyObservers(spot.getSpotType());
    }

    public void vacateSpot(ParkingSpot spot) {
        spot.vacate();
        notifyObservers(spot.getSpotType());
    }

    // Notify all registered observers about the updated count (Observer pattern)
    private void notifyObservers(ParkingSpotType type) {
        long available = spotsByType.get(type).stream().filter(ParkingSpot::isAvailable).count();
        for (ParkingSpotObserver o : observers) {
            o.onAvailabilityChanged(floorId, type, (int) available);
        }
    }

    public Map<ParkingSpotType, Integer> getAvailableCount() {
        Map<ParkingSpotType, Integer> result = new EnumMap<>(ParkingSpotType.class);
        for (ParkingSpotType t : ParkingSpotType.values()) {
            result.put(t, (int) spotsByType.get(t).stream().filter(ParkingSpot::isAvailable).count());
        }
        return result;
    }

    public String getFloorId() { return floorId; }
}

// ─────────────────────────────────────────────
//  PARKING LOT SYSTEM — SINGLETON PATTERN
//  Single point of orchestration for floors, tickets, and pricing.
// ─────────────────────────────────────────────

class ParkingLotSystem {
    // Singleton: exactly one lot instance per JVM
    private static ParkingLotSystem instance;

    private final String             name;
    private final List<ParkingFloor> floors        = new ArrayList<>();
    private final Map<String, ParkingTicket> activeTickets = new HashMap<>();
    private PricingStrategy          pricingStrategy;

    private ParkingLotSystem(String name, PricingStrategy strategy) {
        this.name            = name;
        this.pricingStrategy = strategy;
    }

    /** Initialise singleton on first call; subsequent calls return the same instance. */
    public static synchronized ParkingLotSystem getInstance(String name, PricingStrategy strategy) {
        if (instance == null) instance = new ParkingLotSystem(name, strategy);
        return instance;
    }

    public static ParkingLotSystem getInstance() {
        if (instance == null) throw new IllegalStateException("ParkingLotSystem not initialised.");
        return instance;
    }

    // Swap pricing algorithm at runtime without touching the rest of the code (Strategy pattern)
    public void setPricingStrategy(PricingStrategy strategy) { this.pricingStrategy = strategy; }

    public void addFloor(ParkingFloor floor) { floors.add(floor); }

    // ── Core operations ──────────────────────────────────────────────────

    /**
     * Finds an appropriate spot (across all floors in order), parks the vehicle,
     * and returns an active ParkingTicket. Returns null if the lot is full.
     */
    public ParkingTicket parkVehicle(Vehicle vehicle) {
        ParkingSpotType required = vehicle.requiredSpotType();
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(required);
            if (spot != null) {
                floor.parkVehicle(spot, vehicle);
                ParkingTicket ticket = new ParkingTicket(vehicle, spot);
                activeTickets.put(ticket.getTicketId(), ticket);
                System.out.printf("[Lot] %s parked → spot %s on %s | %s%n",
                        vehicle, spot.getSpotId(), floor.getFloorId(), ticket.getTicketId());
                return ticket;
            }
        }
        System.out.println("[Lot] No available " + required + " spot for " + vehicle);
        return null;
    }

    /**
     * Calculates fee using the current PricingStrategy, marks the ticket paid,
     * and frees the spot (which triggers Observer notifications).
     */
    public double exitVehicle(ParkingTicket ticket) {
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            throw new IllegalStateException("Ticket " + ticket.getTicketId() + " is not active.");
        }
        double fee = pricingStrategy.calculateFee(ticket.getParkingDuration(), ticket.getSpot().getSpotType());
        ticket.markPaid(fee);
        activeTickets.remove(ticket.getTicketId());

        // Vacate the spot on the owning floor
        ParkingSpot spot = ticket.getSpot();
        for (ParkingFloor floor : floors) {
            if (!spot.isAvailable()) { // still occupied → this is the floor to free
                floor.vacateSpot(spot);
                break;
            }
        }
        System.out.printf("[Lot] %s exited | Duration: %s | Fee: $%.2f%n",
                ticket.getVehicle(), formatDuration(ticket.getParkingDuration()), fee);
        return fee;
    }

    /** Aggregated availability summary across all floors. */
    public void printAvailability() {
        System.out.println("[Lot] Availability snapshot:");
        for (ParkingFloor floor : floors) {
            System.out.println("  Floor " + floor.getFloorId() + ": " + floor.getAvailableCount());
        }
    }

    private String formatDuration(Duration d) {
        return d.toHours() + "h " + d.toMinutesPart() + "m";
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class ParkingLot {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Parking Lot System — LLD Demo");
        System.out.println("========================================\n");

        // 1. Singleton: get the lot instance with HourlyPricing (Strategy pattern)
        ParkingLotSystem lot = ParkingLotSystem.getInstance("CityCenter Lot", new HourlyPricing());

        // 2. Build Floor 1
        ParkingFloor floor1 = new ParkingFloor("F1");
        floor1.addSpot(new SmallSpot("S1-01"));
        floor1.addSpot(new SmallSpot("S1-02"));
        floor1.addSpot(new MediumSpot("M1-01"));
        floor1.addSpot(new MediumSpot("M1-02"));
        floor1.addSpot(new LargeSpot("L1-01"));

        // Observer pattern: attach display boards
        DisplayBoard entryBoard = new DisplayBoard("ENTRY");
        DisplayBoard mobileApp  = new DisplayBoard("APP");
        floor1.addObserver(entryBoard);
        floor1.addObserver(mobileApp);

        // 3. Build Floor 2
        ParkingFloor floor2 = new ParkingFloor("F2");
        floor2.addSpot(new SmallSpot("S2-01"));
        floor2.addSpot(new MediumSpot("M2-01"));
        floor2.addSpot(new LargeSpot("L2-01"));
        floor2.addObserver(entryBoard);

        lot.addFloor(floor1);
        lot.addFloor(floor2);

        // 4. Factory pattern: create vehicles without coupling to subclasses
        Vehicle bike1  = VehicleFactory.create(VehicleType.MOTORCYCLE, "MBC-001");
        Vehicle car1   = VehicleFactory.create(VehicleType.CAR,        "CAR-101");
        Vehicle car2   = VehicleFactory.create(VehicleType.CAR,        "CAR-202");
        Vehicle truck1 = VehicleFactory.create(VehicleType.TRUCK,      "TRK-999");

        // 5. Park vehicles
        System.out.println("--- Parking vehicles ---");
        ParkingTicket t1 = lot.parkVehicle(bike1);
        ParkingTicket t2 = lot.parkVehicle(car1);
        ParkingTicket t3 = lot.parkVehicle(car2);
        ParkingTicket t4 = lot.parkVehicle(truck1);

        // 6. Availability snapshot
        System.out.println("\n--- Availability snapshot ---");
        lot.printAvailability();

        // 7. Exit with HourlyPricing (Strategy pattern in action)
        System.out.println("\n--- Exiting vehicles (Hourly pricing) ---");
        if (t1 != null) lot.exitVehicle(t1);
        if (t2 != null) lot.exitVehicle(t2);

        // 8. Switch to WeekendPricing at runtime (Strategy swap)
        System.out.println("\n--- Switching to Weekend flat pricing ---");
        lot.setPricingStrategy(new WeekendPricing());
        if (t3 != null) lot.exitVehicle(t3);
        if (t4 != null) lot.exitVehicle(t4);

        // 9. Attempt double-exit to demonstrate guard
        System.out.println("\n--- Double-exit attempt (expected error) ---");
        try {
            if (t1 != null) lot.exitVehicle(t1);
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
