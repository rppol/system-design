package com.rutik.systemdesign.lld.structural.decorator;

/**
 * DECORATOR PATTERN — Real-World Example: Coffee Shop Add-Ons
 *
 * Scenario: Starbucks-style beverage ordering system.
 *
 * Problem:
 *   A coffee shop offers a small set of base beverages (Espresso, HouseBlend, etc.)
 *   but dozens of optional condiments (Milk, Soy, Whip, Mocha, Caramel, Vanilla...).
 *   Modeling every combination as a subclass leads to an explosion:
 *     EspressoWithMilk, EspressoWithWhip, EspressoWithMilkAndWhip,
 *     HouseBlendWithMocha, HouseBlendWithMochaAndWhip, ... (2^n combinations)
 *   Adding a single new condiment requires adding up to 2^(n-1) new subclasses.
 *
 * Solution:
 *   The Decorator pattern. Each condiment is a Decorator that:
 *     1. Implements the same Beverage interface as the base drinks.
 *     2. Wraps a Beverage instance (base drink or another decorator).
 *     3. Delegates cost() and getDescription() to the wrapped beverage,
 *        then adds its own contribution.
 *   Beverages are composed at runtime by wrapping:
 *     new Whip(new Milk(new Espresso()))
 *   The client only sees a Beverage — it cannot tell how many layers are wrapped.
 *
 * Key insight from Head First Design Patterns (Freeman & Freeman):
 *   "The Decorator pattern attaches additional responsibilities to an object
 *    dynamically. Decorators provide a flexible alternative to subclassing
 *    for extending functionality."
 *
 * Run: javac RealWorldExample.java && java CoffeeShopDemo
 */

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT INTERFACE
// The common type that both base beverages and decorators implement.
// This is what makes wrapping transparent — everything is a Beverage.
// ─────────────────────────────────────────────────────────────────────────────

abstract class Beverage {

    // Description is set by each concrete class
    protected String description = "Unknown Beverage";

    /**
     * Returns a human-readable description of this beverage and all its condiments.
     * Each decorator will prepend or append to the description returned by the
     * wrapped beverage — building the full description as the chain unwinds.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the total cost of this beverage including all condiments.
     * Each decorator adds its own cost to the cost returned by the wrapped
     * beverage — the final cost accumulates as calls pass through the chain.
     */
    public abstract double cost();

    /**
     * Returns a formatted price string for display.
     */
    public String formattedCost() {
        return String.format("$%.2f", cost());
    }

    @Override
    public String toString() {
        return getDescription() + " — " + formattedCost();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CONCRETE COMPONENTS — the base beverages (no condiments)
// These are the innermost objects in the decorator chain.
// They define the starting description and base cost.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Espresso — a concentrated coffee shot. The simplest base beverage.
 */
class Espresso extends Beverage {

    public Espresso() {
        // The description field is inherited from Beverage and set here
        description = "Espresso";
    }

    @Override
    public double cost() {
        return 1.99; // base price, no condiments
    }
}

/**
 * HouseBlend — a medium-roast brewed coffee.
 */
class HouseBlend extends Beverage {

    public HouseBlend() {
        description = "House Blend Coffee";
    }

    @Override
    public double cost() {
        return 0.89;
    }
}

/**
 * DarkRoast — a bold, full-bodied roast.
 */
class DarkRoast extends Beverage {

    public DarkRoast() {
        description = "Dark Roast Coffee";
    }

    @Override
    public double cost() {
        return 0.99;
    }
}

/**
 * Decaf — a caffeine-free base coffee.
 */
class Decaf extends Beverage {

    public Decaf() {
        description = "Decaf Coffee";
    }

    @Override
    public double cost() {
        return 1.05;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ABSTRACT DECORATOR
// The key structural component of the pattern.
//
// CondimentDecorator:
//   1. Extends Beverage — it IS-A Beverage (same interface, transparent wrapping)
//   2. HAS-A Beverage (the wrapped component) — it WRAPS another Beverage
//   3. Forces subclasses to override getDescription() via abstract declaration
//   4. Delegates cost() to the wrapped beverage — subclasses add to this
//
// The IS-A relationship (extends Beverage) is used ONLY for type matching,
// not for inheriting behavior. Behavior comes via composition (HAS-A).
// This is a critical point: we use inheritance here only to achieve
// the same type, NOT to inherit behavior.
// ─────────────────────────────────────────────────────────────────────────────

abstract class CondimentDecorator extends Beverage {

    // The wrapped Beverage — could be a base drink or another decorator.
    // This is what makes the chain possible: Decorator wraps Beverage,
    // and Decorator IS-A Beverage, so a Decorator can wrap another Decorator.
    protected final Beverage wrappedBeverage;

    /**
     * Constructor stores the beverage being wrapped.
     * Every concrete decorator must call super(beverage).
     */
    protected CondimentDecorator(Beverage beverage) {
        this.wrappedBeverage = beverage;
    }

    /**
     * Force all concrete decorators to override getDescription().
     * The typical implementation prepends the condiment name to the
     * wrapped beverage's description:
     *   return wrappedBeverage.getDescription() + ", Milk";
     */
    @Override
    public abstract String getDescription();
}

// ─────────────────────────────────────────────────────────────────────────────
// CONCRETE DECORATORS — one per condiment
// Each decorator:
//   - Calls super(beverage) to store the wrapped beverage.
//   - Overrides getDescription() to append its name to the wrapped description.
//   - Overrides cost() to add its price to the wrapped beverage's cost.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Milk — steamed whole milk condiment. Adds creaminess.
 */
class Milk extends CondimentDecorator {

    public Milk(Beverage beverage) {
        super(beverage); // store the wrapped beverage
    }

    /**
     * Delegates to the wrapped beverage's getDescription(), then appends ", Milk".
     * If the chain is: Espresso wrapped in Milk wrapped in Whip, then:
     *   Whip.getDescription()
     *     → Milk.getDescription()  ← we are here
     *         → Espresso.getDescription() → "Espresso"
     *     → returns "Espresso, Milk"
     *   → returns "Espresso, Milk, Whip"
     */
    @Override
    public String getDescription() {
        return wrappedBeverage.getDescription() + ", Milk";
    }

    /**
     * Adds Milk's cost on top of whatever the wrapped beverage costs.
     */
    @Override
    public double cost() {
        return wrappedBeverage.cost() + 0.10;
    }
}

/**
 * Soy — plant-based milk alternative. Slightly more expensive than regular milk.
 */
class Soy extends CondimentDecorator {

    public Soy(Beverage beverage) {
        super(beverage);
    }

    @Override
    public String getDescription() {
        return wrappedBeverage.getDescription() + ", Soy";
    }

    @Override
    public double cost() {
        return wrappedBeverage.cost() + 0.15;
    }
}

/**
 * Whip — whipped cream topping. Indulgent but cheap.
 */
class Whip extends CondimentDecorator {

    public Whip(Beverage beverage) {
        super(beverage);
    }

    @Override
    public String getDescription() {
        return wrappedBeverage.getDescription() + ", Whip";
    }

    @Override
    public double cost() {
        return wrappedBeverage.cost() + 0.10;
    }
}

/**
 * Mocha — chocolate syrup. The classic coffee shop add-on.
 * Can be added multiple times for double mocha — each layer adds its cost.
 */
class Mocha extends CondimentDecorator {

    public Mocha(Beverage beverage) {
        super(beverage);
    }

    @Override
    public String getDescription() {
        return wrappedBeverage.getDescription() + ", Mocha";
    }

    @Override
    public double cost() {
        return wrappedBeverage.cost() + 0.20;
    }
}

/**
 * Caramel — sweet caramel sauce drizzle.
 */
class Caramel extends CondimentDecorator {

    public Caramel(Beverage beverage) {
        super(beverage);
    }

    @Override
    public String getDescription() {
        return wrappedBeverage.getDescription() + ", Caramel";
    }

    @Override
    public double cost() {
        return wrappedBeverage.cost() + 0.25;
    }
}

/**
 * Vanilla — vanilla syrup for a sweet, fragrant note.
 */
class Vanilla extends CondimentDecorator {

    public Vanilla(Beverage beverage) {
        super(beverage);
    }

    @Override
    public String getDescription() {
        return wrappedBeverage.getDescription() + ", Vanilla";
    }

    @Override
    public double cost() {
        return wrappedBeverage.cost() + 0.20;
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ORDER SUMMARY HELPER
// Prints a formatted receipt line for a beverage.
// ─────────────────────────────────────────────────────────────────────────────

class OrderPrinter {

    private static int orderNum = 1;

    public static void print(Beverage beverage) {
        System.out.printf("  [%d] %-55s %s%n",
                orderNum++,
                beverage.getDescription(),
                beverage.formattedCost());
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN DEMO
// ─────────────────────────────────────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  Decorator Pattern: Coffee Shop Demo   ");
        System.out.println("========================================\n");

        // ── Order 1: Plain Espresso — no decorators ──────────────────────────
        // The base component with no wrapping at all.
        System.out.println("--- Order 1: Plain Espresso ---");
        Beverage order1 = new Espresso();
        System.out.println("  Created: new Espresso()");
        OrderPrinter.print(order1);

        // ── Order 2: Espresso + Double Mocha + Whip ──────────────────────────
        // Demonstrates:
        //   a) Multiple layers of decoration
        //   b) The SAME condiment applied TWICE (double mocha)
        //      — each Mocha wrapper independently adds its cost
        System.out.println("\n--- Order 2: Espresso + Double Mocha + Whip ---");
        System.out.println("  Constructed as: new Whip(new Mocha(new Mocha(new Espresso())))");
        Beverage order2 = new Espresso();     // innermost: base drink
        order2 = new Mocha(order2);           // wrap in first Mocha
        order2 = new Mocha(order2);           // wrap in second Mocha (double!)
        order2 = new Whip(order2);            // outermost: Whip
        // Call flow for cost():
        //   Whip.cost() → Mocha.cost() → Mocha.cost() → Espresso.cost()
        //   = 0.10      +  0.20        +  0.20        +  1.99  = $2.49
        OrderPrinter.print(order2);

        // ── Order 3: House Blend + Soy + Mocha + Whip ────────────────────────
        // A common combination: soy latte variant.
        System.out.println("\n--- Order 3: House Blend + Soy + Mocha + Whip ---");
        System.out.println("  Constructed as: new Whip(new Mocha(new Soy(new HouseBlend())))");
        Beverage order3 = new Whip(new Mocha(new Soy(new HouseBlend())));
        OrderPrinter.print(order3);

        // ── Order 4: Dark Roast + Milk + Caramel ─────────────────────────────
        // Demonstrates inline construction in a single expression.
        System.out.println("\n--- Order 4: Dark Roast + Milk + Caramel ---");
        Beverage order4 = new Caramel(new Milk(new DarkRoast()));
        System.out.println("  Constructed as: new Caramel(new Milk(new DarkRoast()))");
        OrderPrinter.print(order4);

        // ── Order 5: Decaf + Vanilla + Soy + Whip ────────────────────────────
        // Demonstrates that the same base drink can be composed differently.
        System.out.println("\n--- Order 5: Decaf + Vanilla + Soy + Whip ---");
        Beverage order5 = new Whip(new Soy(new Vanilla(new Decaf())));
        System.out.println("  Constructed as: new Whip(new Soy(new Vanilla(new Decaf())))");
        OrderPrinter.print(order5);

        // ── Order 6: The Works — House Blend + Mocha + Caramel + Vanilla + Whip
        // Demonstrates deep stacking. The chain is:
        //   Whip → Vanilla → Caramel → Mocha → HouseBlend
        // Each .cost() call propagates inward; each return propagates outward.
        System.out.println("\n--- Order 6: The Works (HouseBlend + Mocha + Caramel + Vanilla + Whip) ---");
        Beverage order6 = new HouseBlend();
        order6 = new Mocha(order6);    // $0.20
        order6 = new Caramel(order6);  // $0.25
        order6 = new Vanilla(order6);  // $0.20
        order6 = new Whip(order6);     // $0.10
        // Total: 0.89 + 0.20 + 0.25 + 0.20 + 0.10 = $1.64
        System.out.println("  Chain: Whip(Vanilla(Caramel(Mocha(HouseBlend()))))");
        OrderPrinter.print(order6);

        // ── Order 7: Demonstrate transparency ────────────────────────────────
        // The client holds only a Beverage reference. It cannot tell whether
        // it has a base drink or a 4-layer decorated drink.
        System.out.println("\n--- Order 7: Demonstrating type transparency ---");
        Beverage plain  = new Espresso();
        Beverage fancy  = new Whip(new Mocha(new Milk(new Espresso())));
        System.out.println("  plain instanceof Beverage:  " + (plain instanceof Beverage));  // true
        System.out.println("  fancy instanceof Beverage:  " + (fancy instanceof Beverage));  // true
        // Both are Beverage — client code doesn't need to know about the layers.
        System.out.println("  Both treated identically by client code:");
        processBeverage(plain);
        processBeverage(fancy);

        // ── Cost breakdown for Order 2 — manual trace ────────────────────────
        System.out.println("\n--- Cost Trace for Double Mocha Espresso ---");
        System.out.println("  Espresso base:     $1.99");
        System.out.println("  + Mocha (1st):     $0.20");
        System.out.println("  + Mocha (2nd):     $0.20");
        System.out.println("  + Whip:            $0.10");
        System.out.println("  ─────────────────────────");
        System.out.printf ("  Total:             $%.2f%n",
                1.99 + 0.20 + 0.20 + 0.10);

        System.out.println("\n========================================");
        System.out.println("  Receipt Summary");
        System.out.println("========================================");
        Beverage[] allOrders = {order1, order2, order3, order4, order5, order6};
        double total = 0;
        for (Beverage b : allOrders) {
            System.out.printf("  %-55s %s%n", b.getDescription(), b.formattedCost());
            total += b.cost();
        }
        System.out.println("  ─────────────────────────────────────────────────────────────");
        System.out.printf ("  Total for 6 orders:                                    $%.2f%n", total);
    }

    /**
     * Demonstrates that any Beverage — base or decorated — can be processed
     * through the same method. The client has no knowledge of decoration layers.
     */
    private static void processBeverage(Beverage beverage) {
        // This method works identically whether beverage is plain Espresso
        // or a deeply decorated drink. The decorator chain is transparent.
        System.out.println("    Processing: " + beverage);
    }
}

/*
 * PATTERN TAKEAWAYS:
 *
 * 1. SAME INTERFACE IN AND OUT
 *    CondimentDecorator extends Beverage AND wraps a Beverage.
 *    This means a Decorator can wrap a base component OR another Decorator.
 *    The chain can be arbitrarily deep without any client change.
 *
 * 2. BEHAVIOR IS ADDED, NOT REPLACED
 *    Each decorator calls wrappedBeverage.cost() and ADDS to it.
 *    It does not override the base behavior — it extends it.
 *
 * 3. ORDER MATTERS FOR DESCRIPTIONS
 *    new Mocha(new Milk(espresso)) → "Espresso, Milk, Mocha"
 *    new Milk(new Mocha(espresso)) → "Espresso, Mocha, Milk"
 *    (Cost is the same regardless of order — addition is commutative.)
 *
 * 4. SAME DECORATOR CAN BE APPLIED MULTIPLE TIMES
 *    new Mocha(new Mocha(espresso)) — double mocha — each wrapper
 *    independently adds its cost. This is impossible with inheritance.
 *
 * 5. OPEN/CLOSED PRINCIPLE
 *    To add a new condiment (e.g., Oat Milk), create one new class
 *    that extends CondimentDecorator. Zero changes to existing classes.
 *
 * 6. COMPARE TO JAVA I/O
 *    new DataInputStream(new BufferedInputStream(new FileInputStream("f")))
 *    is the EXACT same structure:
 *      InputStream     = Beverage
 *      FileInputStream = Espresso / HouseBlend
 *      FilterInputStream = CondimentDecorator
 *      BufferedInputStream = Milk / Mocha
 *      DataInputStream = Whip
 */
