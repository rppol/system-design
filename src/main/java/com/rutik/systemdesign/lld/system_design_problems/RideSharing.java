package com.rutik.systemdesign.lld.system_design_problems;

import java.util.*;

// =============================================================================
//  RIDE SHARING — Low-Level Design
//  Patterns used:
//    - Strategy   : FareStrategy (StandardFareStrategy, SurgePricingFareStrategy, PremiumFareStrategy)
//    - Observer   : RideObserver / RiderNotifier, DriverNotifier, DispatchDashboard
//    - Factory    : VehicleFactory (creates concrete Vehicle subtypes per tier)
//    - State      : RideState enum + Ride.requestTransition() enforces the ride lifecycle
// =============================================================================

// ─────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────

enum VehicleType { ECONOMY, PREMIUM, XL }

/**
 * STATE PATTERN — the ride lifecycle.
 * canTransitionTo() encodes the legal-transition table so that no caller,
 * anywhere, can push a Ride into an illegal sequence of states.
 */
enum RideState {
    REQUESTED, ACCEPTED, DRIVER_ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canTransitionTo(RideState target) {
        return switch (this) {
            case REQUESTED      -> target == ACCEPTED || target == CANCELLED;
            case ACCEPTED       -> target == DRIVER_ARRIVED || target == CANCELLED;
            case DRIVER_ARRIVED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS    -> target == COMPLETED;
            case COMPLETED, CANCELLED -> false; // terminal states
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}

// ─────────────────────────────────────────────
//  LOCATION — simple (x, y) coordinate system.
//  Production systems use lat/long + haversine distance;
//  Euclidean distance keeps the demo's math simple.
// ─────────────────────────────────────────────

class Location {
    final double x;
    final double y;

    public Location(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double distanceTo(Location other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public String toString() {
        return String.format("(%.0f, %.0f)", x, y);
    }
}

// ─────────────────────────────────────────────
//  VEHICLES — simple inheritance hierarchy.
//  Each tier fixes its own capacity and base per-km rate.
// ─────────────────────────────────────────────

abstract class Vehicle {
    protected final String licensePlate;
    protected final VehicleType type;
    protected final int capacity;
    protected final double baseRatePerKm;

    public Vehicle(String licensePlate, VehicleType type, int capacity, double baseRatePerKm) {
        this.licensePlate  = licensePlate;
        this.type          = type;
        this.capacity      = capacity;
        this.baseRatePerKm = baseRatePerKm;
    }

    public String      getLicensePlate()  { return licensePlate; }
    public VehicleType getType()          { return type; }
    public int         getCapacity()      { return capacity; }
    public double      getBaseRatePerKm() { return baseRatePerKm; }

    @Override
    public String toString() { return type + "(" + licensePlate + ")"; }
}

class EconomyVehicle extends Vehicle {
    public EconomyVehicle(String plate) { super(plate, VehicleType.ECONOMY, 4, 1.20); }
}

class PremiumVehicle extends Vehicle {
    public PremiumVehicle(String plate) { super(plate, VehicleType.PREMIUM, 4, 2.00); }
}

class XLVehicle extends Vehicle {
    public XLVehicle(String plate) { super(plate, VehicleType.XL, 6, 1.60); }
}

// ─────────────────────────────────────────────
//  FACTORY PATTERN — Vehicle creation
//  Decouples driver onboarding / matching from concrete Vehicle subclasses.
// ─────────────────────────────────────────────

class VehicleFactory {
    public static Vehicle create(VehicleType type, String plate) {
        return switch (type) {
            case ECONOMY -> new EconomyVehicle(plate);
            case PREMIUM -> new PremiumVehicle(plate);
            case XL      -> new XLVehicle(plate);
        };
    }
}

// ─────────────────────────────────────────────
//  RIDER / DRIVER
// ─────────────────────────────────────────────

class Rider {
    private final String id;
    private final String name;
    private Location location;
    private final List<Ride> rideHistory = new ArrayList<>();

    public Rider(String id, String name, Location location) {
        this.id       = id;
        this.name     = name;
        this.location = location;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }
    public void     setLocation(Location location) { this.location = location; }
    public void     addRide(Ride ride) { rideHistory.add(ride); }
    public List<Ride> getRideHistory() { return rideHistory; }

    @Override
    public String toString() { return name; }
}

class Driver {
    private final String id;
    private final String name;
    private Location location;
    private final Vehicle vehicle;
    private boolean available;
    private double ratingSum   = 0.0;
    private int    ratingCount = 0;

    public Driver(String id, String name, Location location, Vehicle vehicle) {
        this.id       = id;
        this.name     = name;
        this.location = location;
        this.vehicle  = vehicle;
        this.available = true;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }
    public void     setLocation(Location location) { this.location = location; }
    public Vehicle  getVehicle()  { return vehicle; }
    public boolean  isAvailable() { return available; }
    public void     setAvailable(boolean available) { this.available = available; }

    public void addRating(int stars) {
        ratingSum   += stars;
        ratingCount += 1;
    }

    public double getRating() {
        return ratingCount == 0 ? 5.0 : ratingSum / ratingCount;
    }

    @Override
    public String toString() { return name; }
}

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — Fare calculation
//  Each concrete strategy encapsulates one pricing algorithm.
//  A Ride holds a reference chosen at request time and can be
//  swapped without changing Ride or RideSharingSystem.
// ─────────────────────────────────────────────

interface FareStrategy {
    /** Returns the total fare in dollars for the given trip. */
    double calculateFare(double distanceKm, double durationMin, VehicleType vehicleType);

    /** Human-readable label used in logs/receipts. */
    String getName();
}

/** Standard fare: base fare + per-km rate (tier-dependent) + per-minute rate. */
class StandardFareStrategy implements FareStrategy {
    private static final double BASE_FARE     = 2.50;
    private static final double PER_MIN_RATE  = 0.20;

    @Override
    public double calculateFare(double distanceKm, double durationMin, VehicleType vehicleType) {
        double perKmRate = ratePerKm(vehicleType);
        return BASE_FARE + (perKmRate * distanceKm) + (PER_MIN_RATE * durationMin);
    }

    protected double ratePerKm(VehicleType vehicleType) {
        return switch (vehicleType) {
            case ECONOMY -> 1.20;
            case PREMIUM -> 2.00;
            case XL      -> 1.60;
        };
    }

    @Override
    public String getName() { return "Standard"; }
}

/**
 * Surge pricing: wraps the standard formula and multiplies the result by
 * a surgeMultiplier (typically 1.5x - 2.5x during high demand).
 */
class SurgePricingFareStrategy extends StandardFareStrategy {
    private final double surgeMultiplier;

    public SurgePricingFareStrategy(double surgeMultiplier) {
        if (surgeMultiplier < 1.0) {
            throw new IllegalArgumentException("Surge multiplier must be >= 1.0, got " + surgeMultiplier);
        }
        this.surgeMultiplier = surgeMultiplier;
    }

    @Override
    public double calculateFare(double distanceKm, double durationMin, VehicleType vehicleType) {
        double standardFare = super.calculateFare(distanceKm, durationMin, vehicleType);
        return standardFare * surgeMultiplier;
    }

    @Override
    public String getName() { return "Surge x" + surgeMultiplier; }
}

/** Premium fare: higher base fare + flat luxury surcharge on top of the standard formula. */
class PremiumFareStrategy extends StandardFareStrategy {
    private static final double PREMIUM_BASE_FARE   = 5.00;
    private static final double LUXURY_SURCHARGE    = 3.00;

    @Override
    public double calculateFare(double distanceKm, double durationMin, VehicleType vehicleType) {
        double perKmRate = ratePerKm(vehicleType);
        double distanceAndTime = (perKmRate * distanceKm) + (0.20 * durationMin);
        return PREMIUM_BASE_FARE + distanceAndTime + LUXURY_SURCHARGE;
    }

    @Override
    public String getName() { return "Premium"; }
}

// ─────────────────────────────────────────────
//  OBSERVER PATTERN — Ride status notifications
//  Observers are notified whenever a Ride's state changes.
// ─────────────────────────────────────────────

interface RideObserver {
    void onRideStatusChanged(Ride ride);
}

/** Concrete observer: prints rider-facing notifications. */
class RiderNotifier implements RideObserver {
    @Override
    public void onRideStatusChanged(Ride ride) {
        String riderName = ride.getRider().getName();
        switch (ride.getState()) {
            case REQUESTED -> System.out.printf("  [Rider-%s] Ride %s status: REQUESTED%n", riderName, ride.getId());
            case ACCEPTED -> System.out.printf("  [Rider-%s] Your driver %s is on the way!%n", riderName, ride.getDriver().getName());
            case DRIVER_ARRIVED -> System.out.printf("  [Rider-%s] %s has arrived at your pickup location.%n", riderName, ride.getDriver().getName());
            case IN_PROGRESS -> System.out.printf("  [Rider-%s] Your ride has started. Enjoy!%n", riderName);
            case COMPLETED -> System.out.printf("  [Rider-%s] Ride completed. Fare: $%.2f%n", riderName, ride.getFare());
            case CANCELLED -> System.out.printf("  [Rider-%s] Ride %s was cancelled.%n", riderName, ride.getId());
        }
    }
}

/** Concrete observer: prints driver-facing notifications. */
class DriverNotifier implements RideObserver {
    @Override
    public void onRideStatusChanged(Ride ride) {
        String driverName = ride.getDriver().getName();
        if (ride.getState() == RideState.REQUESTED) {
            System.out.printf("  [Driver-%s] New ride request assigned: %s%n", driverName, ride.getId());
        }
    }
}

/** Concrete observer: logs every transition for operations monitoring. */
class DispatchDashboard implements RideObserver {
    @Override
    public void onRideStatusChanged(Ride ride) {
        RideState previous = ride.getPreviousState();
        if (previous == null) {
            System.out.printf("[Dispatch] Ride %s created | %s -> %s | state=%s%n",
                    ride.getId(), ride.getRider().getName(), ride.getDriver().getName(), ride.getState());
        } else {
            System.out.printf("  [Dispatch] %s: %s -> %s%n", ride.getId(), previous, ride.getState());
        }
    }
}

// ─────────────────────────────────────────────
//  RIDE — STATE PATTERN
//  Holds the current RideState and enforces legal transitions.
// ─────────────────────────────────────────────

class Ride {
    private static int counter = 1000;

    private final String   id;
    private final Rider    rider;
    private final Driver   driver;
    private final Vehicle  vehicle;
    private final Location pickup;
    private final Location dropoff;
    private final FareStrategy fareStrategy;
    private final List<RideObserver> observers = new ArrayList<>();

    private RideState state;
    private RideState previousState;
    private double    fare;

    public Ride(Rider rider, Driver driver, Vehicle vehicle, Location pickup, Location dropoff, FareStrategy fareStrategy) {
        this.id           = "RIDE-" + (++counter);
        this.rider        = rider;
        this.driver       = driver;
        this.vehicle      = vehicle;
        this.pickup       = pickup;
        this.dropoff      = dropoff;
        this.fareStrategy = fareStrategy;
        this.state        = RideState.REQUESTED;
        this.previousState = null;
    }

    public void addObserver(RideObserver observer) { observers.add(observer); }

    /**
     * STATE PATTERN — validates the transition via RideState.canTransitionTo()
     * before mutating state. Throws on any illegal jump (e.g., COMPLETED -> ACCEPTED).
     */
    public void requestTransition(RideState target) {
        if (!state.canTransitionTo(target)) {
            String reason = state.isTerminal() ? " (" + state + " is terminal)" : "";
            throw new IllegalStateException(
                    "Cannot transition ride " + id + " from " + state + " to " + target + reason + ".");
        }
        this.previousState = this.state;
        this.state         = target;
        notifyObservers();
    }

    private void notifyObservers() {
        for (RideObserver observer : observers) {
            observer.onRideStatusChanged(this);
        }
    }

    /** Fires the "ride created" notification (previousState == null signals creation). */
    public void announceCreation() {
        notifyObservers();
    }

    public double calculateAndSetFare() {
        double distanceKm = pickup.distanceTo(dropoff);
        // Fixed demo duration model: ~2 minutes per km of travel
        double durationMin = distanceKm * 2.0;
        this.fare = fareStrategy.calculateFare(distanceKm, durationMin, vehicle.getType());
        return this.fare;
    }

    public String      getId()            { return id; }
    public Rider       getRider()         { return rider; }
    public Driver      getDriver()        { return driver; }
    public Vehicle     getVehicle()       { return vehicle; }
    public Location    getPickup()        { return pickup; }
    public Location    getDropoff()       { return dropoff; }
    public RideState   getState()         { return state; }
    public RideState   getPreviousState() { return previousState; }
    public FareStrategy getFareStrategy() { return fareStrategy; }
    public double      getFare()          { return fare; }

    @Override
    public String toString() {
        return String.format("Ride[%s | %s -> %s | %s | state=%s]",
                id, rider.getName(), driver.getName(), vehicle, state);
    }
}

// ─────────────────────────────────────────────
//  RIDE SHARING SYSTEM — central coordinator
//  Registers riders/drivers, matches requests to drivers, and
//  drives Ride objects through their lifecycle.
// ─────────────────────────────────────────────

class RideSharingSystem {
    private final Map<String, Rider>  riders  = new HashMap<>();
    private final Map<String, Driver> drivers = new HashMap<>();
    private final Map<String, Ride>   activeRides = new HashMap<>();

    public void registerRider(Rider rider) {
        riders.put(rider.getId(), rider);
        System.out.printf("[System] Rider registered: %s at %s%n", rider.getName(), rider.getLocation());
    }

    public void registerDriver(Driver driver) {
        drivers.put(driver.getId(), driver);
        System.out.printf("[System] Driver registered: %s (%s) at %s%n",
                driver.getName(), driver.getVehicle().getType(), driver.getLocation());
    }

    /**
     * Finds the nearest available driver of the requested vehicle type via a
     * linear scan (Euclidean distance). Production systems replace this with
     * a geo-indexed lookup (quadtree / geohash / H3) — the interface here
     * (pickup location + vehicle type in, Driver out) would remain unchanged.
     */
    public Driver findNearestDriver(Location pickup, VehicleType vehicleType) {
        Driver nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Driver driver : drivers.values()) {
            if (!driver.isAvailable()) continue;
            if (driver.getVehicle().getType() != vehicleType) continue;

            double distance = pickup.distanceTo(driver.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = driver;
            }
        }

        if (nearest != null) {
            System.out.printf("[Match] Nearest %s driver: %s (distance %.2f km)%n",
                    vehicleType, nearest.getName(), nearestDistance);
        }
        return nearest;
    }

    /**
     * Matches the request to the nearest available driver, creates the Ride
     * in REQUESTED state, attaches observers, and marks the driver unavailable.
     * Returns null if no driver of the requested tier is available.
     */
    public Ride requestRide(Rider rider, Location pickup, Location dropoff, VehicleType vehicleType, FareStrategy fareStrategy) {
        Driver driver = findNearestDriver(pickup, vehicleType);
        if (driver == null) {
            System.out.println("[System] No " + vehicleType + " drivers available for " + rider.getName());
            return null;
        }

        Ride ride = new Ride(rider, driver, driver.getVehicle(), pickup, dropoff, fareStrategy);

        // Observer pattern: attach rider/driver notifiers and the dispatch dashboard
        ride.addObserver(new DispatchDashboard());
        ride.addObserver(new DriverNotifier());
        ride.addObserver(new RiderNotifier());

        driver.setAvailable(false);
        activeRides.put(ride.getId(), ride);
        rider.addRide(ride);

        ride.announceCreation();
        return ride;
    }

    public void acceptRide(Ride ride)      { ride.requestTransition(RideState.ACCEPTED); }
    public void arriveAtPickup(Ride ride)  { ride.requestTransition(RideState.DRIVER_ARRIVED); }
    public void startRide(Ride ride)       { ride.requestTransition(RideState.IN_PROGRESS); }

    /** Completes the ride, calculates the fare, and frees the driver. */
    public double completeRide(Ride ride) {
        // Calculate fare BEFORE the COMPLETED transition so the single
        // notifyObservers() call fired by requestTransition() already
        // has the correct fare to print (RiderNotifier reads ride.getFare()).
        double fare = ride.calculateAndSetFare();
        ride.requestTransition(RideState.COMPLETED);

        ride.getDriver().setAvailable(true);
        activeRides.remove(ride.getId());

        System.out.printf("[System] Fare for %s (%s): $%.2f%n", ride.getId(), ride.getFareStrategy().getName(), fare);
        return fare;
    }

    /** Cancels the ride from any non-terminal state and frees the driver. */
    public void cancelRide(Ride ride) {
        ride.requestTransition(RideState.CANCELLED);
        ride.getDriver().setAvailable(true);
        activeRides.remove(ride.getId());
        System.out.printf("[System] %s is available again.%n", ride.getDriver().getName());
    }

    /** Records the rider's rating of the driver for a completed ride. */
    public void rateDriver(Ride ride, int stars) {
        if (ride.getState() != RideState.COMPLETED) {
            throw new IllegalStateException("Cannot rate driver before ride " + ride.getId() + " is COMPLETED.");
        }
        Driver driver = ride.getDriver();
        driver.addRating(stars);
        System.out.printf("[System] %s rated %s %d stars (new average: %.2f)%n",
                ride.getRider().getName(), driver.getName(), stars, driver.getRating());
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class RideSharing {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Ride Sharing System — LLD Demo");
        System.out.println("========================================\n");

        RideSharingSystem system = new RideSharingSystem();

        // 1. Register drivers (Factory pattern: VehicleFactory creates each tier)
        System.out.println("--- Registering drivers ---");
        Driver alex   = new Driver("D1", "Alex",   new Location(2, 3),  VehicleFactory.create(VehicleType.ECONOMY, "ECO-100"));
        Driver bianca = new Driver("D2", "Bianca", new Location(10, 10), VehicleFactory.create(VehicleType.PREMIUM, "PRM-200"));
        Driver carlos = new Driver("D3", "Carlos", new Location(1, 1),  VehicleFactory.create(VehicleType.XL, "XL-300"));
        Driver dina   = new Driver("D4", "Dina",   new Location(5, 5),  VehicleFactory.create(VehicleType.ECONOMY, "ECO-101"));
        system.registerDriver(alex);
        system.registerDriver(bianca);
        system.registerDriver(carlos);
        system.registerDriver(dina);

        // 2. Register riders
        System.out.println("\n--- Registering riders ---");
        Rider priya = new Rider("R1", "Priya", new Location(0, 0));
        Rider marco = new Rider("R2", "Marco", new Location(8, 9));
        system.registerRider(priya);
        system.registerRider(marco);

        // 3. Request ride 1: Priya wants an ECONOMY ride, standard fare (Strategy pattern)
        System.out.println("\n--- Requesting ride 1 (Priya, ECONOMY) ---");
        Ride ride1 = system.requestRide(priya, priya.getLocation(), new Location(6, 2), VehicleType.ECONOMY, new StandardFareStrategy());

        // 4. Walk ride 1 through its full lifecycle (State pattern enforces order)
        System.out.println("\n--- Walking ride 1 through its lifecycle ---");
        if (ride1 != null) {
            system.acceptRide(ride1);
            system.arriveAtPickup(ride1);
            system.startRide(ride1);
            system.completeRide(ride1);
            system.rateDriver(ride1, 5);
        }

        // 5. Request ride 2: Marco wants a PREMIUM ride during surge conditions (Strategy pattern)
        System.out.println("\n--- Requesting ride 2 (Marco, PREMIUM, surge pricing) ---");
        Ride ride2 = system.requestRide(marco, marco.getLocation(), new Location(12, 14), VehicleType.PREMIUM, new SurgePricingFareStrategy(1.8));

        // 6. Cancel ride 2 (State pattern: REQUESTED -> CANCELLED is legal)
        System.out.println("\n--- Cancelling ride 2 ---");
        if (ride2 != null) {
            system.cancelRide(ride2);
        }

        // 7. Attempt an invalid transition on the now-terminal ride 2 (expected error)
        System.out.println("\n--- Invalid transition attempt (expected error) ---");
        try {
            if (ride2 != null) {
                system.acceptRide(ride2); // CANCELLED -> ACCEPTED is illegal
            }
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
