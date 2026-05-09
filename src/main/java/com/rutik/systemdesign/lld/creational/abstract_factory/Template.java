package com.rutik.systemdesign.lld.creational.abstract_factory;

/**
 * ABSTRACT FACTORY PATTERN — Template / Skeleton
 *
 * Intent: Provide an interface for creating families of related or dependent
 *         objects without specifying their concrete classes.
 *
 * Key distinction from Factory Method:
 *   - Factory Method creates ONE product via subclassing.
 *   - Abstract Factory creates a FAMILY of related products via composition.
 *
 * Participants:
 *   - AbstractFactory    : Declares creation methods for each product type
 *   - ConcreteFactory1   : Implements creation methods — produces Family-1 products
 *   - ConcreteFactory2   : Implements creation methods — produces Family-2 products
 *   - AbstractProductA   : Interface for Product-A family
 *   - AbstractProductB   : Interface for Product-B family
 *   - ConcreteProduct*   : Concrete implementations belonging to a family
 *   - Client             : Uses only AbstractFactory and Abstract Product interfaces
 */

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1: Define Abstract Product interfaces — one per product type in the family
// ─────────────────────────────────────────────────────────────────────────────

interface AbstractProductA {
    /**
     * All ProductA variants must support this operation.
     * Client code calls this without knowing the concrete type.
     */
    String operationA();
}

interface AbstractProductB {
    /**
     * All ProductB variants must support this operation.
     */
    String operationB();

    /**
     * Products within the same family can collaborate with each other.
     * ProductB knows it may interact with a ProductA from the same factory.
     */
    String collaborateWith(AbstractProductA collaborator);
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2: Implement Concrete Products — grouped by family
// ─────────────────────────────────────────────────────────────────────────────

// -- Family 1 Products --------------------------------------------------------

class ConcreteProductA1 implements AbstractProductA {

    @Override
    public String operationA() {
        return "Result of ProductA1";
    }
}

class ConcreteProductB1 implements AbstractProductB {

    @Override
    public String operationB() {
        return "Result of ProductB1";
    }

    @Override
    public String collaborateWith(AbstractProductA collaborator) {
        // B1 only works correctly with A1 — same family
        return "B1 collaborating with (" + collaborator.operationA() + ")";
    }
}

// -- Family 2 Products --------------------------------------------------------

class ConcreteProductA2 implements AbstractProductA {

    @Override
    public String operationA() {
        return "Result of ProductA2";
    }
}

class ConcreteProductB2 implements AbstractProductB {

    @Override
    public String operationB() {
        return "Result of ProductB2";
    }

    @Override
    public String collaborateWith(AbstractProductA collaborator) {
        // B2 only works correctly with A2 — same family
        return "B2 collaborating with (" + collaborator.operationA() + ")";
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 3: Declare the Abstract Factory interface
// ─────────────────────────────────────────────────────────────────────────────
interface AbstractFactory {
    /**
     * Factory method for Product A.
     * Concrete factories override this to return a family-appropriate product.
     */
    AbstractProductA createProductA();

    /**
     * Factory method for Product B.
     */
    AbstractProductB createProductB();
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 4: Implement Concrete Factories — each produces one consistent family
// ─────────────────────────────────────────────────────────────────────────────
class ConcreteFactory1 implements AbstractFactory {

    @Override
    public AbstractProductA createProductA() {
        return new ConcreteProductA1();
    }

    @Override
    public AbstractProductB createProductB() {
        return new ConcreteProductB1();
    }
}

class ConcreteFactory2 implements AbstractFactory {

    @Override
    public AbstractProductA createProductA() {
        return new ConcreteProductA2();
    }

    @Override
    public AbstractProductB createProductB() {
        return new ConcreteProductB2();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 5: Client — works exclusively through abstract interfaces
// ─────────────────────────────────────────────────────────────────────────────
class AbstractFactoryClient {

    /**
     * The client is given a factory at construction time (dependency injection).
     * It never calls `new ConcreteProduct*()` — the factory handles that.
     * Switching the factory switches the entire product family transparently.
     */
    private final AbstractProductA productA;
    private final AbstractProductB productB;

    public AbstractFactoryClient(AbstractFactory factory) {
        // Client asks the factory to build the products — doesn't know which family
        this.productA = factory.createProductA();
        this.productB = factory.createProductB();
    }

    public void run() {
        System.out.println("ProductA result: " + productA.operationA());
        System.out.println("ProductB result: " + productB.operationB());
        // Cross-product collaboration — both come from the same factory, so they match
        System.out.println("Collaboration:   " + productB.collaborateWith(productA));
    }

    public static void main(String[] args) {
        System.out.println("=== Abstract Factory Pattern ===\n");

        // Swap the factory to get a completely different product family
        System.out.println("--- Client with Factory 1 ---");
        AbstractFactoryClient client1 = new AbstractFactoryClient(new ConcreteFactory1());
        client1.run();

        System.out.println("\n--- Client with Factory 2 ---");
        AbstractFactoryClient client2 = new AbstractFactoryClient(new ConcreteFactory2());
        client2.run();
    }
}
