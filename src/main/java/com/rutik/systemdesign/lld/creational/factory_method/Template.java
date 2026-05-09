package com.rutik.systemdesign.lld.creational.factory_method;

/**
 * FACTORY METHOD PATTERN — Template / Skeleton
 *
 * Intent: Define an interface for creating an object, but let subclasses decide
 *         which class to instantiate.
 *
 * Participants:
 *   - Product          : Interface all created objects implement
 *   - ConcreteProductA : A specific product
 *   - ConcreteProductB : Another specific product
 *   - Creator          : Declares the factory method; may call it in business logic
 *   - ConcreteCreatorA : Overrides factory method to return ConcreteProductA
 *   - ConcreteCreatorB : Overrides factory method to return ConcreteProductB
 *   - Client           : Works with Creator and Product — never with concrete types
 */

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1: Define the Product interface
// ─────────────────────────────────────────────────────────────────────────────
interface Product {
    /**
     * The operation(s) all products must support.
     * Clients use products exclusively through this interface.
     */
    void operation();

    String getDescription();
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2: Implement Concrete Products
// ─────────────────────────────────────────────────────────────────────────────
class ConcreteProductA implements Product {

    @Override
    public void operation() {
        System.out.println("ConcreteProductA: performing operation A");
    }

    @Override
    public String getDescription() {
        return "Product A";
    }
}

class ConcreteProductB implements Product {

    @Override
    public void operation() {
        System.out.println("ConcreteProductB: performing operation B");
    }

    @Override
    public String getDescription() {
        return "Product B";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 3: Define the Creator abstract class
// ─────────────────────────────────────────────────────────────────────────────
abstract class Creator {

    /**
     * THE FACTORY METHOD — the heart of the pattern.
     *
     * Declared abstract here so subclasses MUST override it.
     * (Alternatively, provide a default implementation that subclasses can optionally override.)
     *
     * Important: returns Product (the interface), never a concrete type.
     */
    public abstract Product createProduct();

    /**
     * Business logic that USES the product but doesn't know its concrete type.
     * It calls the factory method to get the product, then works with the interface.
     *
     * This is the "template" — the factory method is the "hook" it delegates to.
     */
    public void someOperation() {
        // The factory method is called — concrete type is determined by subclass
        Product product = createProduct();

        // Work with the product through the interface — no casting, no instanceof
        System.out.println("Creator: working with " + product.getDescription());
        product.operation();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 4: Implement Concrete Creators — each overrides the factory method
// ─────────────────────────────────────────────────────────────────────────────
class ConcreteCreatorA extends Creator {

    /**
     * This is the ONLY place where ConcreteProductA is instantiated.
     * Everything else in the system works with the Product interface.
     */
    @Override
    public Product createProduct() {
        return new ConcreteProductA();
    }
}

class ConcreteCreatorB extends Creator {

    @Override
    public Product createProduct() {
        return new ConcreteProductB();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 5: Client — works with Creator and Product abstractions
// ─────────────────────────────────────────────────────────────────────────────
class FactoryMethodClient {

    /**
     * The client accepts a Creator — it doesn't care which concrete Creator it gets.
     * The factory method (and therefore the product) is polymorphically determined.
     */
    public static void executeWithCreator(Creator creator) {
        creator.someOperation();
    }

    public static void main(String[] args) {
        System.out.println("=== Factory Method Pattern ===\n");

        // Client chooses the Creator — typically from config, DI, or user input
        Creator creatorA = new ConcreteCreatorA();
        Creator creatorB = new ConcreteCreatorB();

        System.out.println("--- Using Creator A ---");
        executeWithCreator(creatorA);

        System.out.println("\n--- Using Creator B ---");
        executeWithCreator(creatorB);

        // Direct product creation via factory method
        System.out.println("\n--- Directly creating products ---");
        Product productA = creatorA.createProduct();
        productA.operation();

        Product productB = creatorB.createProduct();
        productB.operation();
    }
}
