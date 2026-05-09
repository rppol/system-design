package com.rutik.systemdesign.lld.creational.builder;

/**
 * BUILDER PATTERN — Template / Skeleton
 *
 * Intent: Separate the construction of a complex object from its representation
 *         so that the same construction process can create different representations.
 *
 * Use when:
 *   - An object requires many constructor parameters (telescoping constructor problem).
 *   - Some parameters are optional and have sensible defaults.
 *   - You want to produce different representations of the same type using the
 *     same step-by-step construction process.
 *
 * Participants:
 *   - Product         : The complex object being built
 *   - Builder         : Interface declaring all construction steps
 *   - ConcreteBuilder : Implements Builder steps; keeps a partially-built Product
 *   - Director        : Orchestrates the construction steps in a specific order
 *   - Client          : Creates a ConcreteBuilder, hands it to a Director (optional),
 *                       and retrieves the finished Product
 */

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1: Define the Product — the complex object we are building
// ─────────────────────────────────────────────────────────────────────────────
class Product {

    // Required parts
    private String partA;

    // Optional parts
    private String partB;
    private String partC;
    private int    partD;

    // Private — only the Builder creates Product instances
    private Product() {}

    public String getPartA() { return partA; }
    public String getPartB() { return partB; }
    public String getPartC() { return partC; }
    public int    getPartD() { return partD; }

    @Override
    public String toString() {
        return "Product{" +
               "partA='" + partA + '\'' +
               ", partB='" + partB + '\'' +
               ", partC='" + partC + '\'' +
               ", partD=" + partD +
               '}';
    }

    // ── Inner Builder class (fluent, nested style) ─────────────────────────
    // NOTE: The alternative is a separate Builder class with a getResult() method.
    // This nested + fluent approach is idiomatic Java (StringBuilder, Lombok, etc.)
    // and is the preferred style for most modern Java code.
    static class Builder {

        // Shadowed fields — mirror the Product's fields
        private final String partA; // required

        private String partB = "default-B"; // optional with default
        private String partC = "default-C"; // optional with default
        private int    partD = 0;           // optional with default

        // Constructor takes only required fields
        public Builder(String partA) {
            if (partA == null || partA.isBlank()) {
                throw new IllegalArgumentException("partA is required");
            }
            this.partA = partA;
        }

        // Each setter returns `this` for method chaining
        public Builder partB(String partB) {
            this.partB = partB;
            return this;
        }

        public Builder partC(String partC) {
            this.partC = partC;
            return this;
        }

        public Builder partD(int partD) {
            this.partD = partD;
            return this;
        }

        // Terminal method — validates and assembles the Product
        public Product build() {
            // Perform cross-field validation here before creating the object
            Product product = new Product();
            product.partA = this.partA;
            product.partB = this.partB;
            product.partC = this.partC;
            product.partD = this.partD;
            return product;
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2: Builder interface — used when multiple representations are needed
// ─────────────────────────────────────────────────────────────────────────────
// Use this style when the Director + interchangeable ConcreteBuilders approach
// is more appropriate (e.g., generating the same data in XML vs. JSON format).
// ─────────────────────────────────────────────────────────────────────────────
interface ProductBuilderInterface {
    ProductBuilderInterface setPartA(String partA);
    ProductBuilderInterface setPartB(String partB);
    ProductBuilderInterface setPartC(String partC);
    ProductBuilderInterface setPartD(int partD);
    Product build();
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 3: Concrete Builders — each produces a different "flavor" of Product
// ─────────────────────────────────────────────────────────────────────────────
class ConcreteBuilderMinimal implements ProductBuilderInterface {

    // Internal Product instance assembled step-by-step
    private final Product.Builder builder;

    public ConcreteBuilderMinimal() {
        // Start with required defaults for minimal variant
        this.builder = new Product.Builder("minimal-A");
    }

    @Override
    public ProductBuilderInterface setPartA(String partA) {
        // In minimal variant, partA is fixed — silently ignore overrides
        return this;
    }

    @Override
    public ProductBuilderInterface setPartB(String partB) {
        builder.partB(partB);
        return this;
    }

    @Override
    public ProductBuilderInterface setPartC(String partC) {
        // Minimal variant does not support partC — silently ignore
        return this;
    }

    @Override
    public ProductBuilderInterface setPartD(int partD) {
        builder.partD(partD);
        return this;
    }

    @Override
    public Product build() {
        return builder.build();
    }
}

class ConcreteBuilderFull implements ProductBuilderInterface {

    private Product.Builder builder = new Product.Builder("full-A");

    @Override
    public ProductBuilderInterface setPartA(String partA) {
        this.builder = new Product.Builder(partA);
        return this;
    }

    @Override
    public ProductBuilderInterface setPartB(String partB) {
        builder.partB(partB);
        return this;
    }

    @Override
    public ProductBuilderInterface setPartC(String partC) {
        builder.partC(partC);
        return this;
    }

    @Override
    public ProductBuilderInterface setPartD(int partD) {
        builder.partD(partD);
        return this;
    }

    @Override
    public Product build() {
        return builder.build();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 4: Director — encapsulates construction recipes
// ─────────────────────────────────────────────────────────────────────────────
class Director {

    private ProductBuilderInterface builder;

    public void setBuilder(ProductBuilderInterface builder) {
        this.builder = builder;
    }

    /**
     * Constructs a minimal product — only sets the required parts.
     * The Director defines the ORDER of steps; the Builder defines the HOW.
     */
    public Product buildMinimalProduct() {
        return builder
                .setPartA("core-component")
                .setPartD(1)
                .build();
    }

    /**
     * Constructs a fully-configured product.
     */
    public Product buildFullProduct() {
        return builder
                .setPartA("full-component")
                .setPartB("enhanced-B")
                .setPartC("extended-C")
                .setPartD(99)
                .build();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 5: Client — shows both usage styles
// ─────────────────────────────────────────────────────────────────────────────
class BuilderClient {

    public static void main(String[] args) {
        System.out.println("=== Builder Pattern ===\n");

        // ── Style A: Fluent / inline Builder (most common in Java) ────────────
        System.out.println("--- Style A: Fluent Builder ---");

        Product minimal = new Product.Builder("required-A")
                .build(); // all optionals use defaults
        System.out.println("Minimal: " + minimal);

        Product full = new Product.Builder("required-A")
                .partB("custom-B")
                .partC("custom-C")
                .partD(42)
                .build();
        System.out.println("Full:    " + full);

        // ── Style B: Director + interchangeable ConcreteBuilders ──────────────
        System.out.println("\n--- Style B: Director + ConcreteBuilders ---");
        Director director = new Director();

        // Use the "Full" builder
        director.setBuilder(new ConcreteBuilderFull());
        Product directorMinimal = director.buildMinimalProduct();
        Product directorFull    = director.buildFullProduct();
        System.out.println("Director (full builder) minimal: " + directorMinimal);
        System.out.println("Director (full builder) full:    " + directorFull);

        // Swap to "Minimal" builder — same director methods, different output
        director.setBuilder(new ConcreteBuilderMinimal());
        Product minimalBuilderProduct = director.buildFullProduct();
        System.out.println("Director (minimal builder) full: " + minimalBuilderProduct);
    }
}
