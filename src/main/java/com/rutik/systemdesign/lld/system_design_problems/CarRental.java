package com.rutik.systemdesign.lld.system_design_problems;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

// =============================================================================
//  CAR RENTAL - Low-Level Design
//
//  Core ideas:
//    - Rental periods are half-open intervals [start, end)
//    - Each vehicle owns a calendar (TreeMap by start). Conflict detection is
//      one lowerEntry() lookup, O(log n), because stored intervals are disjoint
//    - A turnaround buffer is part of every interval for conflict purposes
//    - A car's location is derived from its calendar, so a one-way rental moves
//      it, and a booking that breaks the next pickup location is rejected
//    - Price = a base component wrapped by decorators, assembled in fixed
//      stages so a discount can never silently apply to insurance or fees
//    - check-and-insert is atomic per vehicle (synchronized calendar)
//
//  Patterns used:
//    - Decorator : price components (discount, add-ons, fees)
//    - Builder   : PricingPipeline orders decorators by Stage
//    - State     : ReservationStatus owns its legal transitions
//    - Strategy  : the PriceComponent chain is swappable per market
//    - Observer  : ReservationListener (confirmation, fleet ops)
//    - Facade    : RentalService
// =============================================================================

public class CarRental {

    // -------------------------------------------------------------------------
    //  DOMAIN
    // -------------------------------------------------------------------------

    enum VehicleClass {
        ECONOMY(4_500), SUV(8_000), LUXURY(15_000);

        final long dailyRateMinor;

        VehicleClass(long dailyRateMinor) {
            this.dailyRateMinor = dailyRateMinor;
        }
    }

    record Vehicle(String id, VehicleClass vehicleClass, String homeBranch) { }

    record Customer(String id, int age, boolean licenseValid, boolean loyaltyMember) { }

    /** Half-open interval [start, end). Back-to-back intervals do not overlap. */
    record Interval(Instant start, Instant end) {
        Interval {
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("end must be after start");
            }
        }

        boolean overlaps(Interval o) {
            return start.isBefore(o.end) && o.start.isBefore(end);
        }

        long billableDays() {
            long hours = Duration.between(start, end).toHours();
            return Math.max(1, (hours + 23) / 24);                  // ceil, minimum one day
        }
    }

    enum ReservationStatus {
        RESERVED, PICKED_UP, RETURNED, CANCELLED, NO_SHOW;

        private Set<ReservationStatus> next;

        static {
            RESERVED.next = EnumSet.of(PICKED_UP, CANCELLED, NO_SHOW);
            PICKED_UP.next = EnumSet.of(RETURNED);
            RETURNED.next = EnumSet.noneOf(ReservationStatus.class);
            CANCELLED.next = EnumSet.noneOf(ReservationStatus.class);
            NO_SHOW.next = EnumSet.noneOf(ReservationStatus.class);
        }

        boolean canMoveTo(ReservationStatus t) {
            return next.contains(t);
        }
    }

    static final class Reservation {
        final String id;
        final String vehicleId;
        final Customer customer;
        final String pickupBranch;
        final String dropoffBranch;
        final Interval period;
        final Quote quote;
        private ReservationStatus status = ReservationStatus.RESERVED;

        Reservation(String id, String vehicleId, Customer customer, String pickupBranch, String dropoffBranch,
                    Interval period, Quote quote) {
            this.id = id;
            this.vehicleId = vehicleId;
            this.customer = customer;
            this.pickupBranch = pickupBranch;
            this.dropoffBranch = dropoffBranch;
            this.period = period;
            this.quote = quote;
        }

        synchronized ReservationStatus status() {
            return status;
        }

        synchronized void moveTo(ReservationStatus target) {
            if (!status.canMoveTo(target)) {
                throw new IllegalStateException(id + ": " + status + " -> " + target + " is not allowed");
            }
            status = target;
        }

        @Override
        public String toString() {
            return id + "[" + vehicleId + " " + pickupBranch + "->" + dropoffBranch + " "
                    + period.start() + ".." + period.end() + "]";
        }
    }

    static final class NoAvailabilityException extends RuntimeException {
        NoAvailabilityException(String msg) {
            super(msg);
        }
    }

    // -------------------------------------------------------------------------
    //  CONFLICT DETECTION - one calendar per vehicle
    // -------------------------------------------------------------------------

    enum BookingCheck { OK, OVERLAP, WRONG_LOCATION, BREAKS_NEXT_PICKUP }

    static final class VehicleCalendar {
        private final Vehicle vehicle;
        private final Duration turnaround;
        private final TreeMap<Instant, Reservation> byStart = new TreeMap<>();

        VehicleCalendar(Vehicle vehicle, Duration turnaround) {
            this.vehicle = vehicle;
            this.turnaround = turnaround;
        }

        private Instant blockedUntil(Reservation r) {
            return r.period.end().plus(turnaround);
        }

        /** Check and insert under one lock, so two threads can never both pass the check. */
        synchronized BookingCheck tryAdd(Reservation r) {
            BookingCheck check = check(r.period, r.pickupBranch, r.dropoffBranch);
            if (check == BookingCheck.OK) {
                byStart.put(r.period.start(), r);
            }
            return check;
        }

        synchronized BookingCheck check(Interval want, String pickup, String dropoff) {
            Instant wantBlockedUntil = want.end().plus(turnaround);
            // Stored blocks are disjoint and sorted by start, so their ends are sorted too.
            // Only the last block starting before our blocked end can overlap us.
            Map.Entry<Instant, Reservation> candidate = byStart.lowerEntry(wantBlockedUntil);
            if (candidate != null && blockedUntil(candidate.getValue()).isAfter(want.start())) {
                return BookingCheck.OVERLAP;
            }
            if (!locationAt(want.start()).equals(pickup)) {
                return BookingCheck.WRONG_LOCATION;
            }
            Map.Entry<Instant, Reservation> next = byStart.higherEntry(want.start());
            if (next != null && !next.getValue().pickupBranch.equals(dropoff)) {
                return BookingCheck.BREAKS_NEXT_PICKUP;
            }
            return BookingCheck.OK;
        }

        /** Where the car is at time t: the drop-off branch of the last rental before t, else home. */
        synchronized String locationAt(Instant t) {
            Map.Entry<Instant, Reservation> prev = byStart.lowerEntry(t);
            return prev == null ? vehicle.homeBranch() : prev.getValue().dropoffBranch;
        }

        synchronized void remove(Reservation r) {
            byStart.remove(r.period.start(), r);
        }

        /** The booking after r on this vehicle, used to flag a late return that eats the next pickup. */
        synchronized Reservation nextAfter(Reservation r) {
            Map.Entry<Instant, Reservation> e = byStart.higherEntry(r.period.start());
            return e == null ? null : e.getValue();
        }

        synchronized boolean overlapFree() {
            Reservation prev = null;
            for (Reservation r : byStart.values()) {
                if (prev != null && blockedUntil(prev).isAfter(r.period.start())) {
                    return false;
                }
                prev = r;
            }
            return true;
        }

        synchronized int size() {
            return byStart.size();
        }
    }

    // -------------------------------------------------------------------------
    //  PRICING - base component plus decorators, assembled by stage
    // -------------------------------------------------------------------------

    record LineItem(String label, long minor) { }

    record Quote(List<LineItem> items) {
        Quote plus(LineItem item) {
            List<LineItem> copy = new ArrayList<>(items);
            copy.add(item);
            return new Quote(List.copyOf(copy));
        }

        long total() {
            return items.stream().mapToLong(LineItem::minor).sum();
        }

        /** Sum of the rental charge lines only: base plus any discount already applied. */
        long rentalCharges() {
            return items.stream().filter(i -> i.label().startsWith("rental")).mapToLong(LineItem::minor).sum();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (LineItem i : items) {
                sb.append(String.format("      %-34s %s%n", i.label(), money(i.minor())));
            }
            sb.append(String.format("      %-34s %s", "TOTAL", money(total())));
            return sb.toString();
        }
    }

    record RentalContext(VehicleClass vehicleClass, Interval period, String pickup, String dropoff,
                         Customer customer, boolean insurance) { }

    interface PriceComponent {
        Quote quote(RentalContext ctx);
    }

    static final class DailyRate implements PriceComponent {
        @Override
        public Quote quote(RentalContext ctx) {
            long days = ctx.period().billableDays();
            return new Quote(List.of(new LineItem("rental " + days + " day(s) x " + ctx.vehicleClass(),
                    days * ctx.vehicleClass().dailyRateMinor)));
        }
    }

    abstract static class PriceDecorator implements PriceComponent {
        protected final PriceComponent inner;

        PriceDecorator(PriceComponent inner) {
            this.inner = inner;
        }
    }

    /** 10% off, computed on the rental charge lines only. */
    static final class LoyaltyDiscount extends PriceDecorator {
        LoyaltyDiscount(PriceComponent inner) {
            super(inner);
        }

        @Override
        public Quote quote(RentalContext ctx) {
            Quote q = inner.quote(ctx);
            if (!ctx.customer().loyaltyMember()) {
                return q;
            }
            return q.plus(new LineItem("rental loyalty -10%", -q.rentalCharges() / 10));
        }
    }

    /** BROKEN variant kept for the demo: discounts whatever total it wraps. */
    static final class NaiveLoyaltyDiscount extends PriceDecorator {
        NaiveLoyaltyDiscount(PriceComponent inner) {
            super(inner);
        }

        @Override
        public Quote quote(RentalContext ctx) {
            Quote q = inner.quote(ctx);
            return q.plus(new LineItem("loyalty -10% of running total", -q.total() / 10));
        }
    }

    static final class InsuranceAddOn extends PriceDecorator {
        InsuranceAddOn(PriceComponent inner) {
            super(inner);
        }

        @Override
        public Quote quote(RentalContext ctx) {
            Quote q = inner.quote(ctx);
            if (!ctx.insurance()) {
                return q;
            }
            long days = ctx.period().billableDays();
            return q.plus(new LineItem("insurance " + days + " day(s) x 15.00", days * 1_500));
        }
    }

    static final class YoungDriverSurcharge extends PriceDecorator {
        YoungDriverSurcharge(PriceComponent inner) {
            super(inner);
        }

        @Override
        public Quote quote(RentalContext ctx) {
            Quote q = inner.quote(ctx);
            if (ctx.customer().age() >= 25) {
                return q;
            }
            long days = ctx.period().billableDays();
            return q.plus(new LineItem("young driver " + days + " day(s) x 25.00", days * 2_500));
        }
    }

    static final class OneWayDropFee extends PriceDecorator {
        OneWayDropFee(PriceComponent inner) {
            super(inner);
        }

        @Override
        public Quote quote(RentalContext ctx) {
            Quote q = inner.quote(ctx);
            if (ctx.pickup().equals(ctx.dropoff())) {
                return q;
            }
            return q.plus(new LineItem("one-way " + ctx.pickup() + "->" + ctx.dropoff(), 7_500));
        }
    }

    enum Stage { DISCOUNT, ADD_ON, FEE }

    /** BUILDER - callers add decorators in any order; build() wraps them in Stage order. */
    static final class PricingPipeline {
        private record Step(Stage stage, int seq, Function<PriceComponent, PriceComponent> wrap) { }

        private final List<Step> steps = new ArrayList<>();

        PricingPipeline add(Stage stage, Function<PriceComponent, PriceComponent> wrap) {
            steps.add(new Step(stage, steps.size(), wrap));
            return this;
        }

        PriceComponent build() {
            PriceComponent c = new DailyRate();
            List<Step> ordered = new ArrayList<>(steps);
            ordered.sort(Comparator.comparing(Step::stage).thenComparingInt(Step::seq));
            for (Step s : ordered) {
                c = s.wrap().apply(c);
            }
            return c;
        }
    }

    static String money(long minor) {
        long abs = Math.abs(minor);
        return String.format("%s%d.%02d", minor < 0 ? "-" : "", abs / 100, abs % 100);
    }

    // -------------------------------------------------------------------------
    //  OBSERVER
    // -------------------------------------------------------------------------

    interface ReservationListener {
        void onEvent(String event, Reservation r);
    }

    // -------------------------------------------------------------------------
    //  FACADE - the rental service
    // -------------------------------------------------------------------------

    record ReturnInvoice(Quote quote, long lateDays, long fuelEighthsMissing) { }

    static final class RentalService {
        static final Duration LATE_GRACE = Duration.ofMinutes(59);
        static final long FUEL_PER_EIGHTH_MINOR = 1_200;

        private final List<Vehicle> fleet = new ArrayList<>();
        private final Map<String, VehicleCalendar> calendars = new ConcurrentHashMap<>();
        private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
        private final List<ReservationListener> listeners = new CopyOnWriteArrayList<>();
        private final PriceComponent pricing;
        private final Duration turnaround;
        private final AtomicInteger seq = new AtomicInteger(100);

        RentalService(PriceComponent pricing, Duration turnaround) {
            this.pricing = pricing;
            this.turnaround = turnaround;
        }

        void addVehicle(Vehicle v) {
            fleet.add(v);
            calendars.put(v.id(), new VehicleCalendar(v, turnaround));
        }

        void addListener(ReservationListener l) {
            listeners.add(l);
        }

        Quote quote(RentalContext ctx) {
            return pricing.quote(ctx);
        }

        Reservation reserve(Customer c, VehicleClass cls, String pickup, String dropoff, Interval period,
                            boolean insurance) {
            if (!c.licenseValid() || c.age() < 21) {
                throw new IllegalArgumentException(c.id() + " is not eligible to rent");
            }
            Quote q = pricing.quote(new RentalContext(cls, period, pickup, dropoff, c, insurance));
            String id = "R-" + seq.incrementAndGet();      // like a DB sequence: failures leave gaps
            List<String> reasons = new ArrayList<>();
            for (Vehicle v : fleet) {
                if (v.vehicleClass() != cls) {
                    continue;
                }
                Reservation r = new Reservation(id, v.id(), c, pickup, dropoff,
                        period, q);
                BookingCheck check = calendars.get(v.id()).tryAdd(r);
                if (check == BookingCheck.OK) {
                    reservations.put(r.id, r);
                    publish("RESERVED", r);
                    return r;
                }
                reasons.add(v.id() + "=" + check);
            }
            throw new NoAvailabilityException("no " + cls + " at " + pickup + " for " + period.start()
                    + ".." + period.end() + " " + reasons);
        }

        void pickUp(String reservationId) {
            Reservation r = reservations.get(reservationId);
            r.moveTo(ReservationStatus.PICKED_UP);
            publish("PICKED_UP", r);
        }

        void cancel(String reservationId) {
            Reservation r = reservations.get(reservationId);
            r.moveTo(ReservationStatus.CANCELLED);
            calendars.get(r.vehicleId).remove(r);
            publish("CANCELLED", r);
        }

        ReturnInvoice returnVehicle(String reservationId, Instant actualReturn, int fuelEighthsMissing) {
            Reservation r = reservations.get(reservationId);
            r.moveTo(ReservationStatus.RETURNED);
            Quote q = r.quote;
            long lateDays = 0;
            Duration late = Duration.between(r.period.end(), actualReturn);
            if (late.compareTo(LATE_GRACE) > 0) {
                lateDays = (late.toHours() + 23) / 24;
                VehicleClass cls = vehicle(r.vehicleId).vehicleClass();
                q = q.plus(new LineItem("late return " + lateDays + " day(s)", lateDays * cls.dailyRateMinor));
            }
            if (fuelEighthsMissing > 0) {
                q = q.plus(new LineItem("fuel " + fuelEighthsMissing + "/8 tank", fuelEighthsMissing
                        * FUEL_PER_EIGHTH_MINOR));
            }
            publish("RETURNED", r);
            Reservation next = calendars.get(r.vehicleId).nextAfter(r);
            if (next != null && actualReturn.plus(turnaround).isAfter(next.period.start())) {
                publish("NEXT_PICKUP_AT_RISK", next);           // ops must swap the car
            }
            return new ReturnInvoice(q, lateDays, fuelEighthsMissing);
        }

        Vehicle vehicle(String id) {
            return fleet.stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
        }

        String locationOf(String vehicleId, Instant t) {
            return calendars.get(vehicleId).locationAt(t);
        }

        boolean allCalendarsOverlapFree() {
            return calendars.values().stream().allMatch(VehicleCalendar::overlapFree);
        }

        int bookingsOn(String vehicleId) {
            return calendars.get(vehicleId).size();
        }

        private void publish(String event, Reservation r) {
            for (ReservationListener l : listeners) {
                l.onEvent(event, r);
            }
        }
    }

    // -------------------------------------------------------------------------
    //  DEMO
    // -------------------------------------------------------------------------

    static Instant at(String dayHour) {
        return Instant.parse("2026-12-" + dayHour + ":00:00Z");
    }

    static Interval period(String from, String to) {
        return new Interval(at(from), at(to));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   Car Rental - LLD Demo");
        System.out.println("========================================");

        PriceComponent pricing = new PricingPipeline()
                .add(Stage.FEE, OneWayDropFee::new)
                .add(Stage.ADD_ON, InsuranceAddOn::new)
                .add(Stage.DISCOUNT, LoyaltyDiscount::new)
                .add(Stage.ADD_ON, YoungDriverSurcharge::new)
                .build();
        RentalService svc = new RentalService(pricing, Duration.ofHours(2));
        svc.addVehicle(new Vehicle("ECO-1", VehicleClass.ECONOMY, "SFO"));
        svc.addVehicle(new Vehicle("ECO-2", VehicleClass.ECONOMY, "SFO"));
        svc.addVehicle(new Vehicle("SUV-1", VehicleClass.SUV, "SFO"));
        svc.addListener((event, r) -> System.out.println("    [event] " + event + " " + r));

        Customer alice = new Customer("alice", 34, true, true);
        Customer bob = new Customer("bob", 23, true, false);
        Customer carol = new Customer("carol", 41, true, false);

        System.out.println("\n--- 1. Reserve, then overlapping requests ---");
        Reservation r1 = svc.reserve(alice, VehicleClass.ECONOMY, "SFO", "SFO", period("01T10", "04T10"), true);
        System.out.println(r1.quote);
        svc.reserve(bob, VehicleClass.ECONOMY, "SFO", "SFO", period("03T10", "05T10"), false);
        try {
            svc.reserve(carol, VehicleClass.ECONOMY, "SFO", "SFO", period("02T09", "03T09"), false);
        } catch (NoAvailabilityException e) {
            System.out.println("  rejected: " + e.getMessage());
        }

        System.out.println("\n--- 2. Back-to-back with a 2 h turnaround buffer ---");
        try {
            svc.reserve(carol, VehicleClass.ECONOMY, "SFO", "SFO", period("04T10", "06T10"), false);
        } catch (NoAvailabilityException e) {
            System.out.println("  04T10 rejected: " + e.getMessage());
        }
        svc.reserve(carol, VehicleClass.ECONOMY, "SFO", "SFO", period("04T12", "06T10"), false);

        System.out.println("\n--- 3. One-way rental moves the car ---");
        Customer erin = new Customer("erin", 29, true, false);
        Reservation oneWay = svc.reserve(erin, VehicleClass.SUV, "SFO", "LAX", period("10T09", "12T09"), false);
        System.out.println(oneWay.quote);
        System.out.println("  SUV-1 on 13T09 is at " + svc.locationOf("SUV-1", at("13T09")));
        try {
            svc.reserve(carol, VehicleClass.SUV, "SFO", "SFO", period("14T09", "15T09"), false);
        } catch (NoAvailabilityException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        svc.reserve(carol, VehicleClass.SUV, "LAX", "LAX", period("14T09", "15T09"), false);
        try {
            svc.reserve(carol, VehicleClass.SUV, "SFO", "LAX", period("06T09", "07T09"), false);
        } catch (NoAvailabilityException e) {
            System.out.println("  rejected: " + e.getMessage());
        }

        System.out.println("\n--- 4. Decorator order: the same three components, two totals ---");
        RentalContext ctx = new RentalContext(VehicleClass.SUV, period("20T09", "23T09"), "SFO", "SFO",
                alice, true);
        PriceComponent wrongOrder = new NaiveLoyaltyDiscount(new InsuranceAddOn(new DailyRate()));
        System.out.println("  BROKEN, discount wraps insurance:");
        System.out.println(wrongOrder.quote(ctx));
        System.out.println("  FIX, pipeline applies DISCOUNT before ADD_ON:");
        System.out.println(svc.quote(ctx));

        System.out.println("\n--- 5. Pick up, return 3 h late with 2/8 tank missing ---");
        svc.pickUp(r1.id);
        ReturnInvoice inv = svc.returnVehicle(r1.id, at("04T13"), 2);
        System.out.println(inv.quote());
        try {
            svc.pickUp(r1.id);
        } catch (IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }

        System.out.println("\n--- 6. Sixteen customers race for the last SUV window ---");
        svc.listeners.clear();
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger losses = new AtomicInteger();
        runConcurrently(16, i -> {
            Customer c = new Customer("racer-" + i, 30, true, false);
            try {
                svc.reserve(c, VehicleClass.SUV, "LAX", "LAX", period("20T09", "22T09"), false);
                wins.incrementAndGet();
            } catch (NoAvailabilityException e) {
                losses.incrementAndGet();
            }
        });
        System.out.println("  winners=" + wins.get() + ", rejected=" + losses.get());

        System.out.println("\n--- 7. Stress: 8 threads x 400 random requests on a 4-car fleet ---");
        RentalService busy = new RentalService(new PricingPipeline().build(), Duration.ofHours(2));
        for (int i = 1; i <= 4; i++) {
            busy.addVehicle(new Vehicle("CAR-" + i, VehicleClass.ECONOMY, "SFO"));
        }
        AtomicInteger booked = new AtomicInteger();
        runConcurrently(8, worker -> {
            Random rnd = new Random(worker);
            Customer c = new Customer("load-" + worker, 30, true, false);
            Instant base = Instant.parse("2027-01-01T00:00:00Z");
            for (int i = 0; i < 400; i++) {
                Instant s = base.plus(Duration.ofHours(rnd.nextInt(24 * 60)));
                Interval p = new Interval(s, s.plus(Duration.ofHours(4 + rnd.nextInt(68))));
                try {
                    busy.reserve(c, VehicleClass.ECONOMY, "SFO", "SFO", p, false);
                    booked.incrementAndGet();
                } catch (NoAvailabilityException e) {
                    // expected: the fleet fills up
                }
            }
        });
        int stored = 0;
        for (int i = 1; i <= 4; i++) {
            stored += busy.bookingsOn("CAR-" + i);
        }
        System.out.println("  every calendar overlap-free: " + busy.allCalendarsOverlapFree()
                + ", accepted == stored: " + (booked.get() == stored));

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }

    interface IndexedTask {
        void run(int index);
    }

    private static void runConcurrently(int threads, IndexedTask task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.submit(() -> {
                start.await();
                task.run(index);
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
    }
}
