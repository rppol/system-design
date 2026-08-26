package com.rutik.systemdesign.lld.system_design_problems;

import java.util.*;

// =============================================================================
//  FOOD DELIVERY — Low-Level Design
//  Patterns used:
//    - State     : OrderStatus enum + Order.requestTransition() guards the order lifecycle
//    - Strategy  : DeliveryFeeStrategy (Standard, Surge) and
//                  DeliveryPartnerAssignmentStrategy (NearestAvailable, RoundRobin)
//    - Decorator : PriceCalculator chain (TaxDecorator, coupon decorators) stacked on
//                  top of BasePriceCalculator without touching the base or each other
//    - Observer  : OrderObserver / CustomerNotifier, RestaurantNotifier, DeliveryPartnerNotifier
//  Three-sided handoff (customer -> restaurant -> delivery partner) is the structural
//  difference from RideSharing.java's two-sided rider/driver match; see FoodDelivery.md.
// =============================================================================

// ─────────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────────

enum Cuisine { NORTH_INDIAN, CHINESE, ITALIAN, MEXICAN }

/**
 * STATE PATTERN — the order lifecycle.
 * canTransitionTo() encodes the legal-transition table: cancellation is only
 * legal from PLACED or ACCEPTED, because once PREPARING starts the kitchen
 * has already committed real ingredients and labor to the order.
 */
enum OrderStatus {
    PLACED, ACCEPTED, PREPARING, READY, PICKED_UP, DELIVERED, CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PLACED    -> target == ACCEPTED || target == CANCELLED;
            case ACCEPTED  -> target == PREPARING || target == CANCELLED;
            case PREPARING -> target == READY;
            case READY     -> target == PICKED_UP;
            case PICKED_UP -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false; // terminal states
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}

// ─────────────────────────────────────────────
//  LOCATION — simple (x, y) coordinate system, matching RideSharing.java.
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
//  MENU / RESTAURANT / CATALOGUE
// ─────────────────────────────────────────────

class MenuItem {
    private final String id;
    private final String name;
    private final double price;

    public MenuItem(String id, String name, double price) {
        this.id    = id;
        this.name  = name;
        this.price = price;
    }

    public String getId()    { return id; }
    public String getName()  { return name; }
    public double getPrice() { return price; }

    @Override
    public String toString() { return name; }
}

class Restaurant {
    private final String id;
    private final String name;
    private final Set<Cuisine> cuisines;
    private final double rating;
    private final Location location;
    private final List<MenuItem> menu;

    public Restaurant(String id, String name, Set<Cuisine> cuisines, double rating, Location location, List<MenuItem> menu) {
        this.id       = id;
        this.name     = name;
        this.cuisines = cuisines;
        this.rating   = rating;
        this.location = location;
        this.menu     = menu;
    }

    public String        getId()       { return id; }
    public String        getName()     { return name; }
    public Set<Cuisine>  getCuisines() { return cuisines; }
    public double        getRating()   { return rating; }
    public Location      getLocation() { return location; }
    public List<MenuItem> getMenu()    { return menu; }

    @Override
    public String toString() { return name; }
}

/** Search/filter by cuisine and a minimum rating threshold — the discovery half of the problem. */
class RestaurantCatalogue {
    private final Map<String, Restaurant> restaurants = new LinkedHashMap<>();

    public void register(Restaurant restaurant) {
        restaurants.put(restaurant.getId(), restaurant);
        System.out.printf("[System] Restaurant registered: %s (%s, rating %.1f) at %s%n",
                restaurant.getName(), restaurant.getCuisines(), restaurant.getRating(), restaurant.getLocation());
    }

    /** Linear scan filter, sorted by rating descending — fine at catalogue sizes an LLD interview asks about. */
    public List<Restaurant> search(Cuisine cuisine, double minRating) {
        List<Restaurant> results = new ArrayList<>();
        for (Restaurant r : restaurants.values()) {
            if (r.getCuisines().contains(cuisine) && r.getRating() >= minRating) {
                results.add(r);
            }
        }
        results.sort((a, b) -> Double.compare(b.getRating(), a.getRating()));
        return results;
    }
}

// ─────────────────────────────────────────────
//  CUSTOMER / DELIVERY PARTNER / CART
// ─────────────────────────────────────────────

class Customer {
    private final String id;
    private final String name;
    private final Location location;

    public Customer(String id, String name, Location location) {
        this.id       = id;
        this.name     = name;
        this.location = location;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }

    @Override
    public String toString() { return name; }
}

class DeliveryPartner {
    private final String id;
    private final String name;
    private final Location location;
    private boolean available;

    public DeliveryPartner(String id, String name, Location location) {
        this.id        = id;
        this.name      = name;
        this.location  = location;
        this.available = true;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }
    public boolean  isAvailable() { return available; }
    public void     setAvailable(boolean available) { this.available = available; }

    @Override
    public String toString() { return name; }
}

/** Accumulates items for one restaurant before checkout; not itself an Order. */
class Cart {
    private final Customer customer;
    private final Restaurant restaurant;
    private final Map<MenuItem, Integer> items = new LinkedHashMap<>();

    public Cart(Customer customer, Restaurant restaurant) {
        this.customer   = customer;
        this.restaurant = restaurant;
    }

    public void addItem(MenuItem item, int quantity) {
        items.merge(item, quantity, Integer::sum);
    }

    public double subtotal() {
        double total = 0.0;
        for (Map.Entry<MenuItem, Integer> entry : items.entrySet()) {
            total += entry.getKey().getPrice() * entry.getValue();
        }
        return total;
    }

    public boolean isEmpty()             { return items.isEmpty(); }
    public Customer getCustomer()        { return customer; }
    public Restaurant getRestaurant()    { return restaurant; }
    public Map<MenuItem, Integer> getItems() { return items; }
}

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — delivery fee calculation
// ─────────────────────────────────────────────

interface DeliveryFeeStrategy {
    double calculateFee(double distanceKm);
    String getName();
}

class StandardDeliveryFeeStrategy implements DeliveryFeeStrategy {
    private final double baseFee;
    private final double perKmRate;

    public StandardDeliveryFeeStrategy(double baseFee, double perKmRate) {
        this.baseFee   = baseFee;
        this.perKmRate = perKmRate;
    }

    @Override
    public double calculateFee(double distanceKm) {
        return baseFee + (perKmRate * distanceKm);
    }

    @Override
    public String getName() { return "Standard"; }
}

/**
 * Surge fee: deliberately its OWN implementation of DeliveryFeeStrategy rather than a
 * subclass of StandardDeliveryFeeStrategy. RideSharing.java's SurgePricingFareStrategy
 * extends StandardFareStrategy and can therefore only ever surge that one formula; this
 * one composes distance and a multiplier directly, so it can be swapped in anywhere a
 * DeliveryFeeStrategy is expected with zero coupling to the standard implementation.
 */
class SurgeDeliveryFeeStrategy implements DeliveryFeeStrategy {
    private final double baseFee;
    private final double perKmRate;
    private final double surgeMultiplier;

    public SurgeDeliveryFeeStrategy(double baseFee, double perKmRate, double surgeMultiplier) {
        if (surgeMultiplier < 1.0) {
            throw new IllegalArgumentException("Surge multiplier must be >= 1.0, got " + surgeMultiplier);
        }
        this.baseFee         = baseFee;
        this.perKmRate       = perKmRate;
        this.surgeMultiplier = surgeMultiplier;
    }

    @Override
    public double calculateFee(double distanceKm) {
        return (baseFee + (perKmRate * distanceKm)) * surgeMultiplier;
    }

    @Override
    public String getName() { return "Surge x" + surgeMultiplier; }
}

// ─────────────────────────────────────────────
//  DECORATOR PATTERN — tax and coupons stacked on the Strategy's base amount
// ─────────────────────────────────────────────

interface PriceCalculator {
    double calculate();
}

/** The base of every chain: subtotal + whatever the DeliveryFeeStrategy computed. */
class BasePriceCalculator implements PriceCalculator {
    private final double subtotal;
    private final double deliveryFee;

    public BasePriceCalculator(double subtotal, double deliveryFee) {
        this.subtotal    = subtotal;
        this.deliveryFee = deliveryFee;
    }

    @Override
    public double calculate() { return subtotal + deliveryFee; }
}

abstract class PriceDecorator implements PriceCalculator {
    protected final PriceCalculator wrapped;

    protected PriceDecorator(PriceCalculator wrapped) {
        this.wrapped = wrapped;
    }
}

class TaxDecorator extends PriceDecorator {
    private final double taxRate;

    public TaxDecorator(PriceCalculator wrapped, double taxRate) {
        super(wrapped);
        this.taxRate = taxRate;
    }

    @Override
    public double calculate() { return wrapped.calculate() * (1 + taxRate); }
}

class PercentageDiscountCouponDecorator extends PriceDecorator {
    private final double discountRate;

    public PercentageDiscountCouponDecorator(PriceCalculator wrapped, double discountRate) {
        super(wrapped);
        this.discountRate = discountRate;
    }

    @Override
    public double calculate() { return wrapped.calculate() * (1 - discountRate); }
}

/**
 * FIXED half of the coupon-stacking bug documented in FoodDelivery.md: a flat-amount
 * coupon larger than the running total must never drive the price negative, no matter
 * where in the chain it sits or how large it is. The BROKEN version (no clamp) is
 * teaching material on the page and is not carried here — see FoodDelivery.md.
 */
class FlatDiscountCouponDecorator extends PriceDecorator {
    private final double flatAmount;

    public FlatDiscountCouponDecorator(PriceCalculator wrapped, double flatAmount) {
        super(wrapped);
        this.flatAmount = flatAmount;
    }

    @Override
    public double calculate() { return Math.max(0.0, wrapped.calculate() - flatAmount); }
}

/** Builds the decorator chain on top of a Strategy-computed subtotal + delivery fee. */
interface PriceCalculatorFactory {
    PriceCalculator build(double subtotal, double deliveryFee);
}

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — delivery-partner assignment
// ─────────────────────────────────────────────

interface DeliveryPartnerAssignmentStrategy {
    /** Returns the chosen partner, or null if none are available. */
    DeliveryPartner assign(List<DeliveryPartner> availablePartners, Location restaurantLocation);
    String getName();
}

class NearestAvailableStrategy implements DeliveryPartnerAssignmentStrategy {
    @Override
    public DeliveryPartner assign(List<DeliveryPartner> availablePartners, Location restaurantLocation) {
        DeliveryPartner nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (DeliveryPartner partner : availablePartners) {
            double distance = restaurantLocation.distanceTo(partner.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = partner;
            }
        }
        if (nearest != null) {
            System.out.printf("[Match] Nearest available partner: %s (distance %.2f km)%n", nearest.getName(), nearestDistance);
        }
        return nearest;
    }

    @Override
    public String getName() { return "NearestAvailable"; }
}

/**
 * Ignores distance entirely and rotates through whichever partners are available right
 * now — a fairness-over-efficiency policy real platforms use to spread earnings evenly
 * instead of always routing to whoever is geographically closest.
 */
class RoundRobinStrategy implements DeliveryPartnerAssignmentStrategy {
    private int cursor = 0;

    @Override
    public DeliveryPartner assign(List<DeliveryPartner> availablePartners, Location restaurantLocation) {
        if (availablePartners.isEmpty()) {
            return null;
        }
        DeliveryPartner chosen = availablePartners.get(cursor % availablePartners.size());
        cursor++;
        double distance = restaurantLocation.distanceTo(chosen.getLocation());
        System.out.printf("[Match] Round-robin partner: %s (distance %.2f km, not necessarily nearest)%n", chosen.getName(), distance);
        return chosen;
    }

    @Override
    public String getName() { return "RoundRobin"; }
}

// ─────────────────────────────────────────────
//  OBSERVER PATTERN — order status notifications, three independent audiences
// ─────────────────────────────────────────────

interface OrderObserver {
    void onOrderStatusChanged(Order order);
}

/** Reacts to every transition — the customer wants to see the whole journey. */
class CustomerNotifier implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Order order) {
        String name = order.getCustomer().getName();
        switch (order.getStatus()) {
            case PLACED -> System.out.printf("  [Customer-%s] Order %s placed at %s. Estimated total: $%.2f%n",
                    name, order.getId(), order.getRestaurant().getName(), order.getFinalPrice());
            case ACCEPTED -> System.out.printf("  [Customer-%s] %s accepted your order.%n", name, order.getRestaurant().getName());
            case PREPARING -> System.out.printf("  [Customer-%s] Your food is being prepared.%n", name);
            case READY -> System.out.printf("  [Customer-%s] Your order is ready and waiting for a delivery partner.%n", name);
            case PICKED_UP -> System.out.printf("  [Customer-%s] %s picked up your order and is on the way!%n",
                    name, order.getDeliveryPartner().getName());
            case DELIVERED -> System.out.printf("  [Customer-%s] Order delivered. Enjoy your meal!%n", name);
            case CANCELLED -> System.out.printf("  [Customer-%s] Order %s was cancelled.%n", name, order.getId());
        }
    }
}

/**
 * Reacts to PLACED (new order alert) and CANCELLED (stop cooking) — a wider slice than
 * RideSharing.java's DriverNotifier, which reacts only to REQUESTED. A restaurant that
 * already started shopping ingredients for an order genuinely needs the cancel signal;
 * a driver never needs an equivalent mid-ride signal because IN_PROGRESS can't cancel.
 */
class RestaurantNotifier implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Order order) {
        String name = order.getRestaurant().getName();
        if (order.getStatus() == OrderStatus.PLACED) {
            System.out.printf("  [Restaurant-%s] New order received: %s (%d line items)%n",
                    name, order.getId(), order.getItems().size());
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            System.out.printf("  [Restaurant-%s] Order %s was cancelled — stand down.%n", name, order.getId());
        }
    }
}

/**
 * Silent until a partner is actually assigned (Order.getDeliveryPartner() != null), then
 * reacts to the pickup-and-delivery leg only. This is the three-sided handoff made
 * concrete: this observer has nothing to say during PLACED/ACCEPTED/PREPARING because no
 * partner exists yet to notify — a state RideSharing's driver/rider pairing never has,
 * since a Ride is created with its driver already matched.
 */
class DeliveryPartnerNotifier implements OrderObserver {
    @Override
    public void onOrderStatusChanged(Order order) {
        DeliveryPartner partner = order.getDeliveryPartner();
        if (partner == null) {
            return;
        }
        switch (order.getStatus()) {
            case READY -> System.out.printf("  [Partner-%s] Assigned to order %s — pick up from %s.%n",
                    partner.getName(), order.getId(), order.getRestaurant().getName());
            case PICKED_UP -> System.out.printf("  [Partner-%s] Picked up order %s, heading to %s.%n",
                    partner.getName(), order.getId(), order.getCustomer().getName());
            case DELIVERED -> System.out.printf("  [Partner-%s] Delivered order %s.%n", partner.getName(), order.getId());
            default -> { /* no-op: PLACED/ACCEPTED/PREPARING/CANCELLED have no assigned partner yet */ }
        }
    }
}

// ─────────────────────────────────────────────
//  ORDER — STATE PATTERN
//  Holds the current OrderStatus and enforces legal transitions.
// ─────────────────────────────────────────────

class Order {
    private static int counter = 5000;

    private final String id;
    private final Customer customer;
    private final Restaurant restaurant;
    private final Map<MenuItem, Integer> items;
    private final double subtotal;
    private final double deliveryFee;
    private final double finalPrice;
    private final double distanceKm;
    private final List<OrderObserver> observers = new ArrayList<>();

    private OrderStatus status = OrderStatus.PLACED;
    private OrderStatus previousStatus = null;
    private DeliveryPartner deliveryPartner;

    public Order(Customer customer, Restaurant restaurant, Map<MenuItem, Integer> items,
                 double subtotal, double deliveryFee, double finalPrice, double distanceKm) {
        this.id          = "ORDER-" + (++counter);
        this.customer    = customer;
        this.restaurant  = restaurant;
        this.items       = items;
        this.subtotal    = subtotal;
        this.deliveryFee = deliveryFee;
        this.finalPrice  = finalPrice;
        this.distanceKm  = distanceKm;
    }

    public void addObserver(OrderObserver observer) { observers.add(observer); }

    /** Fires the "order placed" notification (previousStatus == null signals creation). */
    public void announceCreation() { notifyObservers(); }

    /** Fires a notification without a status change — used only for partner assignment. */
    public void announceAssignment() { notifyObservers(); }

    /**
     * STATE PATTERN — validates the transition via OrderStatus.canTransitionTo() before
     * mutating status. Throws on any illegal jump (e.g. cancelling a PICKED_UP order).
     */
    public void requestTransition(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            String reason = status.isTerminal() ? " (" + status + " is terminal)" : "";
            throw new IllegalStateException(
                    "Cannot transition order " + id + " from " + status + " to " + target + reason + ".");
        }
        this.previousStatus = this.status;
        this.status = target;
        notifyObservers();
    }

    private void notifyObservers() {
        for (OrderObserver observer : observers) {
            observer.onOrderStatusChanged(this);
        }
    }

    public String getId()                        { return id; }
    public Customer getCustomer()                 { return customer; }
    public Restaurant getRestaurant()             { return restaurant; }
    public Map<MenuItem, Integer> getItems()      { return items; }
    public double getSubtotal()                   { return subtotal; }
    public double getDeliveryFee()                { return deliveryFee; }
    public double getFinalPrice()                 { return finalPrice; }
    public double getDistanceKm()                 { return distanceKm; }
    public OrderStatus getStatus()                { return status; }
    public OrderStatus getPreviousStatus()        { return previousStatus; }
    public DeliveryPartner getDeliveryPartner()   { return deliveryPartner; }
    public void setDeliveryPartner(DeliveryPartner deliveryPartner) { this.deliveryPartner = deliveryPartner; }

    @Override
    public String toString() {
        return String.format("Order[%s | %s -> %s | status=%s]", id, customer.getName(), restaurant.getName(), status);
    }
}

// ─────────────────────────────────────────────
//  FOOD DELIVERY SERVICE — central coordinator
//  Registers customers/restaurants/partners and drives Order objects through
//  their lifecycle: PLACED -> ACCEPTED -> PREPARING -> READY -> PICKED_UP -> DELIVERED.
// ─────────────────────────────────────────────

class FoodDeliveryService {
    private final RestaurantCatalogue catalogue = new RestaurantCatalogue();
    private final Map<String, Customer> customers = new HashMap<>();
    private final Map<String, DeliveryPartner> partners = new LinkedHashMap<>(); // deterministic scan order for demos
    private final Map<String, Order> orders = new HashMap<>();

    public void registerRestaurant(Restaurant restaurant) { catalogue.register(restaurant); }

    public void registerCustomer(Customer customer) {
        customers.put(customer.getId(), customer);
        System.out.printf("[System] Customer registered: %s at %s%n", customer.getName(), customer.getLocation());
    }

    public void registerDeliveryPartner(DeliveryPartner partner) {
        partners.put(partner.getId(), partner);
        System.out.printf("[System] Delivery partner registered: %s at %s%n", partner.getName(), partner.getLocation());
    }

    public List<Restaurant> searchRestaurants(Cuisine cuisine, double minRating) {
        return catalogue.search(cuisine, minRating);
    }

    /** Checkout: Strategy computes the delivery fee, then the caller's decorator chain adds tax/coupons. */
    public Order placeOrder(Cart cart, DeliveryFeeStrategy feeStrategy, PriceCalculatorFactory pricingChain) {
        if (cart.isEmpty()) {
            throw new IllegalArgumentException("Cannot place an order with an empty cart.");
        }
        Customer customer     = cart.getCustomer();
        Restaurant restaurant = cart.getRestaurant();
        double subtotal       = cart.subtotal();
        double distanceKm     = restaurant.getLocation().distanceTo(customer.getLocation());
        double deliveryFee    = feeStrategy.calculateFee(distanceKm);
        PriceCalculator calculator = pricingChain.build(subtotal, deliveryFee);
        double finalPrice     = calculator.calculate();

        Order order = new Order(customer, restaurant, cart.getItems(), subtotal, deliveryFee, finalPrice, distanceKm);
        order.addObserver(new CustomerNotifier());
        order.addObserver(new RestaurantNotifier());
        order.addObserver(new DeliveryPartnerNotifier());
        orders.put(order.getId(), order);
        order.announceCreation();
        return order;
    }

    public void acceptOrder(Order order)     { order.requestTransition(OrderStatus.ACCEPTED); }
    public void startPreparing(Order order)  { order.requestTransition(OrderStatus.PREPARING); }
    public void markReady(Order order)       { order.requestTransition(OrderStatus.READY); }

    /** Assignment is not itself a status change — READY orders can sit briefly before a partner is found. */
    public void assignDeliveryPartner(Order order, DeliveryPartnerAssignmentStrategy strategy) {
        List<DeliveryPartner> available = new ArrayList<>();
        for (DeliveryPartner partner : partners.values()) {
            if (partner.isAvailable()) {
                available.add(partner);
            }
        }
        DeliveryPartner chosen = strategy.assign(available, order.getRestaurant().getLocation());
        if (chosen == null) {
            System.out.println("[System] No delivery partners available for " + order.getId());
            return;
        }
        chosen.setAvailable(false);
        order.setDeliveryPartner(chosen);
        order.announceAssignment();
    }

    public void pickUp(Order order)   { order.requestTransition(OrderStatus.PICKED_UP); }

    public void deliverOrder(Order order) {
        order.requestTransition(OrderStatus.DELIVERED);
        if (order.getDeliveryPartner() != null) {
            order.getDeliveryPartner().setAvailable(true);
        }
    }

    /** Cancellation is only legal from PLACED/ACCEPTED per OrderStatus.canTransitionTo() — see FoodDelivery.md. */
    public void cancelOrder(Order order) {
        order.requestTransition(OrderStatus.CANCELLED);
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

public class FoodDelivery {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   Food Delivery System — LLD Demo");
        System.out.println("========================================\n");

        FoodDeliveryService service = new FoodDeliveryService();

        // 1. Register restaurants (catalogue: cuisine + rating)
        System.out.println("--- Registering restaurants ---");
        Restaurant spiceRoute = new Restaurant("RES1", "Spice Route", EnumSet.of(Cuisine.NORTH_INDIAN), 4.5,
                new Location(2, 3), List.of(
                        new MenuItem("M1", "Butter Chicken", 12.00),
                        new MenuItem("M2", "Naan", 2.50),
                        new MenuItem("M3", "Dal Makhani", 8.00)));
        Restaurant dragonWok = new Restaurant("RES2", "Dragon Wok", EnumSet.of(Cuisine.CHINESE), 4.2,
                new Location(10, 10), List.of(
                        new MenuItem("M4", "Kung Pao Chicken", 11.00),
                        new MenuItem("M5", "Spring Rolls", 5.00)));
        Restaurant bellaItalia = new Restaurant("RES3", "Bella Italia", EnumSet.of(Cuisine.ITALIAN), 4.8,
                new Location(5, 5), List.of(
                        new MenuItem("M6", "Margherita Pizza", 10.00),
                        new MenuItem("M7", "Pasta Alfredo", 9.50)));
        Restaurant tacoFiesta = new Restaurant("RES4", "Taco Fiesta", EnumSet.of(Cuisine.MEXICAN), 3.9,
                new Location(1, 1), List.of(
                        new MenuItem("M8", "Beef Tacos", 8.00),
                        new MenuItem("M9", "Nachos", 6.00)));
        service.registerRestaurant(spiceRoute);
        service.registerRestaurant(dragonWok);
        service.registerRestaurant(bellaItalia);
        service.registerRestaurant(tacoFiesta);

        // 2. Register customers and delivery partners
        System.out.println("\n--- Registering customers ---");
        Customer neha   = new Customer("C1", "Neha", new Location(0, 0));
        Customer vikram = new Customer("C2", "Vikram", new Location(2, 2));
        service.registerCustomer(neha);
        service.registerCustomer(vikram);

        System.out.println("\n--- Registering delivery partners ---");
        DeliveryPartner suresh = new DeliveryPartner("P1", "Suresh", new Location(2, 2));
        DeliveryPartner farah  = new DeliveryPartner("P2", "Farah", new Location(9, 9));
        DeliveryPartner om     = new DeliveryPartner("P3", "Om", new Location(3, 4));
        service.registerDeliveryPartner(suresh);
        service.registerDeliveryPartner(farah);
        service.registerDeliveryPartner(om);

        // 3. Search/filter the catalogue by cuisine and minimum rating
        System.out.println("\n--- Searching the catalogue ---");
        List<Restaurant> italianTop = service.searchRestaurants(Cuisine.ITALIAN, 4.0);
        System.out.println("ITALIAN, rating >= 4.0: " + italianTop);
        List<Restaurant> chineseTop = service.searchRestaurants(Cuisine.CHINESE, 4.5);
        System.out.println("CHINESE, rating >= 4.5: " + chineseTop + " (Dragon Wok is 4.2, filtered out)");

        // 4. Order 1: Neha at Spice Route, standard delivery fee, tax then a loyalty discount
        System.out.println("\n--- Order 1: Neha orders from Spice Route ---");
        Cart cart1 = new Cart(neha, spiceRoute);
        cart1.addItem(spiceRoute.getMenu().get(0), 2); // 2x Butter Chicken
        cart1.addItem(spiceRoute.getMenu().get(1), 1); // 1x Naan
        Order order1 = service.placeOrder(cart1,
                new StandardDeliveryFeeStrategy(2.00, 1.00),
                (subtotal, deliveryFee) -> new PercentageDiscountCouponDecorator(
                        new TaxDecorator(new BasePriceCalculator(subtotal, deliveryFee), 0.05),
                        0.10));
        System.out.printf("[System] Order %s: subtotal $%.2f + delivery $%.2f -> final $%.2f (tax then 10%% loyalty discount)%n",
                order1.getId(), order1.getSubtotal(), order1.getDeliveryFee(), order1.getFinalPrice());

        System.out.println("\n--- Walking order 1 through its lifecycle ---");
        service.acceptOrder(order1);
        service.startPreparing(order1);
        service.markReady(order1);
        service.assignDeliveryPartner(order1, new NearestAvailableStrategy());
        // Suresh (nearest to Spice Route) is now busy with order 1 -- kept that way
        // deliberately so order 2's round-robin assignment below has to choose among
        // the remaining two partners, not just re-pick the one NearestAvailable would.

        // 5. Order 2: Vikram at Taco Fiesta, surge fee, an oversized flat coupon that must clamp at zero.
        // Placed and walked to READY *before* order 1 finishes, so round-robin assignment
        // runs while Suresh is still unavailable -- see below.
        System.out.println("\n--- Order 2: Vikram orders from Taco Fiesta (surge fee, oversized coupon) ---");
        Cart cart2 = new Cart(vikram, tacoFiesta);
        cart2.addItem(tacoFiesta.getMenu().get(0), 1); // Beef Tacos
        cart2.addItem(tacoFiesta.getMenu().get(1), 1); // Nachos
        Order order2 = service.placeOrder(cart2,
                new SurgeDeliveryFeeStrategy(2.00, 1.00, 1.3),
                (subtotal, deliveryFee) -> new TaxDecorator(
                        new FlatDiscountCouponDecorator(new BasePriceCalculator(subtotal, deliveryFee), 20.00),
                        0.05));
        System.out.printf("[System] Order %s: subtotal $%.2f + delivery $%.2f = $%.2f pre-discount; " +
                        "$20.00 flat coupon exceeds it, clamped at $0.00 instead of going negative -> final $%.2f%n",
                order2.getId(), order2.getSubtotal(), order2.getDeliveryFee(),
                order2.getSubtotal() + order2.getDeliveryFee(), order2.getFinalPrice());

        System.out.println("\n--- Walking order 2 through its lifecycle (round-robin assignment, Suresh still busy) ---");
        service.acceptOrder(order2);
        service.startPreparing(order2);
        service.markReady(order2);
        service.assignDeliveryPartner(order2, new RoundRobinStrategy());

        System.out.println("\n--- Cancelling order 1 after PICKED_UP (expected error) ---");
        service.pickUp(order1);
        try {
            service.cancelOrder(order1);
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }
        service.deliverOrder(order1);

        System.out.println("\n--- Finishing order 2 ---");
        service.pickUp(order2);
        service.deliverOrder(order2);

        // 6. Order 3: a valid cancellation from PLACED, before the kitchen ever starts
        System.out.println("\n--- Order 3: Neha orders from Dragon Wok, then cancels before ACCEPTED ---");
        Cart cart3 = new Cart(neha, dragonWok);
        cart3.addItem(dragonWok.getMenu().get(0), 1); // Kung Pao Chicken
        Order order3 = service.placeOrder(cart3,
                new StandardDeliveryFeeStrategy(2.00, 1.00),
                (subtotal, deliveryFee) -> new TaxDecorator(new BasePriceCalculator(subtotal, deliveryFee), 0.05));
        service.cancelOrder(order3);
        System.out.printf("[System] Order %s final status: %s%n", order3.getId(), order3.getStatus());

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
