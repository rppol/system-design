package com.rutik.systemdesign.lld.solid_principles;

/**
 * SolidExamples.java
 *
 * A single-file reference demonstrating all five SOLID principles with
 * concise before/after examples in Java. Each principle is introduced
 * with a violation followed by the compliant refactoring.
 *
 * Compile-check notes:
 *   - Placeholder types (Database, EmailService, etc.) are defined as
 *     minimal stubs at the bottom of the file so everything compiles.
 *   - Each principle is in its own static nested class to keep namespaces clean.
 *
 * Outline:
 *   1. SRP  — Single Responsibility Principle
 *   2. OCP  — Open/Closed Principle
 *   3. LSP  — Liskov Substitution Principle
 *   4. ISP  — Interface Segregation Principle
 *   5. DIP  — Dependency Inversion Principle
 */
public class SolidExamples {

    // =========================================================================
    // 1. SINGLE RESPONSIBILITY PRINCIPLE (SRP)
    //    "A class should have only one reason to change."
    // =========================================================================
    static class SRP {

        // ----- VIOLATION -----
        // Invoice handles business logic AND PDF generation AND database persistence.
        // Three reasons to change: pricing rules, report format, storage technology.
        static class InvoiceViolation {
            private String customerName;
            private double amount;

            public InvoiceViolation(String customerName, double amount) {
                this.customerName = customerName;
                this.amount = amount;
            }

            public double calculateTotal() {
                return amount * 1.1; // 10% tax — business logic
            }

            public void printInvoice() {
                // PDF/console rendering — presentation concern
                System.out.println("Invoice for " + customerName + ": $" + calculateTotal());
            }

            public void saveToDatabase() {
                // Persistence — infrastructure concern
                System.out.println("Saving invoice to DB for " + customerName);
            }
        }

        // ----- COMPLIANT -----
        // Each class has exactly one reason to change.

        static class Invoice {
            private final String customerName;
            private final double amount;

            public Invoice(String customerName, double amount) {
                this.customerName = customerName;
                this.amount = amount;
            }

            public double calculateTotal() { return amount * 1.1; }
            public String getCustomerName() { return customerName; }
        }

        static class InvoicePrinter {
            public void print(Invoice invoice) {
                System.out.println("Invoice for " + invoice.getCustomerName()
                    + ": $" + invoice.calculateTotal());
            }
        }

        static class InvoiceRepository {
            public void save(Invoice invoice) {
                System.out.println("Saving invoice for " + invoice.getCustomerName());
            }
        }

        static void demo() {
            Invoice inv = new Invoice("Acme Corp", 1000.00);
            new InvoicePrinter().print(inv);
            new InvoiceRepository().save(inv);
        }
    }

    // =========================================================================
    // 2. OPEN/CLOSED PRINCIPLE (OCP)
    //    "Software entities should be open for extension, closed for modification."
    // =========================================================================
    static class OCP {

        // ----- VIOLATION -----
        // Adding a new shape requires modifying AreaCalculator — touching existing code.
        static class AreaCalculatorViolation {
            public double calculateArea(Object shape) {
                if (shape instanceof CircleViolation) {
                    CircleViolation c = (CircleViolation) shape;
                    return Math.PI * c.radius * c.radius;
                } else if (shape instanceof RectangleViolation) {
                    RectangleViolation r = (RectangleViolation) shape;
                    return r.width * r.height;
                }
                // Adding Triangle requires modifying this method — OCP violation
                throw new IllegalArgumentException("Unknown shape");
            }
        }

        static class CircleViolation    { double radius; }
        static class RectangleViolation { double width, height; }

        // ----- COMPLIANT -----
        // New shapes extend the system without touching AreaCalculator.

        interface Shape {
            double calculateArea();
        }

        static class Circle implements Shape {
            private final double radius;
            public Circle(double radius) { this.radius = radius; }

            @Override
            public double calculateArea() { return Math.PI * radius * radius; }
        }

        static class Rectangle implements Shape {
            private final double width, height;
            public Rectangle(double width, double height) {
                this.width = width;
                this.height = height;
            }

            @Override
            public double calculateArea() { return width * height; }
        }

        // Adding Triangle: zero changes to AreaCalculator
        static class Triangle implements Shape {
            private final double base, height;
            public Triangle(double base, double height) {
                this.base = base;
                this.height = height;
            }

            @Override
            public double calculateArea() { return 0.5 * base * height; }
        }

        static class AreaCalculator {
            public double calculateArea(Shape shape) {
                return shape.calculateArea(); // closed for modification, open for extension
            }
        }

        static void demo() {
            AreaCalculator calc = new AreaCalculator();
            System.out.println("Circle area:    " + calc.calculateArea(new Circle(5)));
            System.out.println("Rectangle area: " + calc.calculateArea(new Rectangle(4, 6)));
            System.out.println("Triangle area:  " + calc.calculateArea(new Triangle(3, 8)));
        }
    }

    // =========================================================================
    // 3. LISKOV SUBSTITUTION PRINCIPLE (LSP)
    //    "Objects of a subclass must be substitutable for objects of the superclass
    //     without altering the correctness of the program."
    // =========================================================================
    static class LSP {

        // ----- VIOLATION -----
        // Square is-a Rectangle in geometry, but NOT in program behavior.
        // Setting width changes height (and vice versa), breaking Rectangle's contract.
        static class RectangleViolation {
            protected int width;
            protected int height;

            public void setWidth(int w)  { this.width  = w; }
            public void setHeight(int h) { this.height = h; }
            public int  getArea()        { return width * height; }
        }

        static class SquareViolation extends RectangleViolation {
            @Override
            public void setWidth(int w) {
                this.width  = w;
                this.height = w; // must keep sides equal — breaks Rectangle contract
            }

            @Override
            public void setHeight(int h) {
                this.width  = h;
                this.height = h;
            }
        }

        static void demonstrateViolation() {
            RectangleViolation rect = new SquareViolation(); // substituted
            rect.setWidth(5);
            rect.setHeight(3);
            // Expected: 5 * 3 = 15.  Actual: 3 * 3 = 9 — LSP violated
            System.out.println("LSP violation, expected 15 got: " + rect.getArea());
        }

        // ----- COMPLIANT -----
        // Model the true abstraction. Rectangle and Square share a Shape contract
        // but do not inherit from each other.

        interface ShapeLSP {
            int getArea();
        }

        static class Rectangle implements ShapeLSP {
            private final int width, height;
            public Rectangle(int width, int height) {
                this.width = width;
                this.height = height;
            }

            @Override
            public int getArea() { return width * height; }
        }

        static class Square implements ShapeLSP {
            private final int side;
            public Square(int side) { this.side = side; }

            @Override
            public int getArea() { return side * side; }
        }

        // Any ShapeLSP can be substituted safely — no hidden behavior surprises
        static void printArea(ShapeLSP shape) {
            System.out.println("Area: " + shape.getArea());
        }

        static void demo() {
            demonstrateViolation();
            printArea(new Rectangle(5, 3)); // Area: 15
            printArea(new Square(4));        // Area: 16
        }
    }

    // =========================================================================
    // 4. INTERFACE SEGREGATION PRINCIPLE (ISP)
    //    "No client should be forced to depend on methods it does not use."
    // =========================================================================
    static class ISP {

        // ----- VIOLATION -----
        // A fat Worker interface forces Robot to implement eat() and sleep().
        interface WorkerViolation {
            void work();
            void eat();
            void sleep();
        }

        static class HumanWorker implements WorkerViolation {
            @Override public void work()  { System.out.println("Human working"); }
            @Override public void eat()   { System.out.println("Human eating"); }
            @Override public void sleep() { System.out.println("Human sleeping"); }
        }

        static class RobotWorker implements WorkerViolation {
            @Override public void work()  { System.out.println("Robot working"); }

            @Override
            public void eat() {
                throw new UnsupportedOperationException("Robots don't eat!"); // forced stub
            }

            @Override
            public void sleep() {
                throw new UnsupportedOperationException("Robots don't sleep!"); // forced stub
            }
        }

        // ----- COMPLIANT -----
        // Split into focused, role-specific interfaces.

        interface Workable {
            void work();
        }

        interface Feedable {
            void eat();
            void sleep();
        }

        static class Human implements Workable, Feedable {
            @Override public void work()  { System.out.println("Human working"); }
            @Override public void eat()   { System.out.println("Human eating"); }
            @Override public void sleep() { System.out.println("Human sleeping"); }
        }

        static class Robot implements Workable {
            @Override public void work()  { System.out.println("Robot working"); }
            // No eat() or sleep() — no stubs, no exceptions
        }

        // Client depends only on what it needs
        static void scheduleWork(Workable worker) {
            worker.work();
        }

        static void scheduleBreak(Feedable worker) {
            worker.eat(); // type-safe: only Feedable workers can be passed
        }

        static void demo() {
            Human human = new Human();
            Robot robot = new Robot();

            scheduleWork(human);
            scheduleWork(robot);
            scheduleBreak(human);
            // scheduleBreak(robot); // compile error — Robot is not Feedable
        }
    }

    // =========================================================================
    // 5. DEPENDENCY INVERSION PRINCIPLE (DIP)
    //    "High-level modules should not depend on low-level modules.
    //     Both should depend on abstractions."
    // =========================================================================
    static class DIP {

        // ----- VIOLATION -----
        // UserService directly instantiates concrete infrastructure classes.
        static class MySQLUserRepo {
            public void save(String email) {
                System.out.println("MySQL: saving " + email);
            }
            public boolean existsByEmail(String email) { return false; }
        }

        static class SmtpMailer {
            public void sendWelcome(String email) {
                System.out.println("SMTP: sending welcome to " + email);
            }
        }

        static class UserServiceViolation {
            // Coupled to MySQL and SMTP — cannot test without real infrastructure
            private MySQLUserRepo repo   = new MySQLUserRepo();
            private SmtpMailer    mailer = new SmtpMailer();

            public void register(String email) {
                if (repo.existsByEmail(email)) throw new RuntimeException("Taken");
                repo.save(email);
                mailer.sendWelcome(email);
            }
        }

        // ----- COMPLIANT -----
        // Define abstractions. High-level service depends only on them.

        interface UserRepo {
            void save(String email);
            boolean existsByEmail(String email);
        }

        interface Mailer {
            void sendWelcome(String email);
        }

        // Low-level modules implement the abstractions
        static class MySQLRepo implements UserRepo {
            @Override public void save(String email) {
                System.out.println("MySQL: saving " + email);
            }
            @Override public boolean existsByEmail(String email) { return false; }
        }

        static class SmtpMailerImpl implements Mailer {
            @Override public void sendWelcome(String email) {
                System.out.println("SMTP: welcome email to " + email);
            }
        }

        // In-memory test doubles — trivial to write because of DIP
        static class InMemoryUserRepo implements UserRepo {
            private final java.util.Set<String> emails = new java.util.HashSet<>();
            @Override public void save(String email) { emails.add(email); }
            @Override public boolean existsByEmail(String email) { return emails.contains(email); }
        }

        static class CapturingMailer implements Mailer {
            final java.util.List<String> sent = new java.util.ArrayList<>();
            @Override public void sendWelcome(String email) { sent.add(email); }
        }

        // High-level module — depends only on abstractions injected via constructor
        static class UserService {
            private final UserRepo userRepo;
            private final Mailer   mailer;

            public UserService(UserRepo userRepo, Mailer mailer) {
                this.userRepo = userRepo;
                this.mailer   = mailer;
            }

            public void register(String email) {
                if (userRepo.existsByEmail(email)) {
                    throw new IllegalArgumentException("Email already registered: " + email);
                }
                userRepo.save(email);
                mailer.sendWelcome(email);
            }
        }

        static void demo() {
            // Production wiring
            UserService prod = new UserService(new MySQLRepo(), new SmtpMailerImpl());
            prod.register("alice@example.com");

            // Test wiring — no database, no SMTP server needed
            InMemoryUserRepo testRepo   = new InMemoryUserRepo();
            CapturingMailer  testMailer = new CapturingMailer();
            UserService      test       = new UserService(testRepo, testMailer);

            test.register("bob@example.com");
            System.out.println("Saved:       " + testRepo.existsByEmail("bob@example.com")); // true
            System.out.println("Email sent:  " + testMailer.sent);                           // [bob@...]
        }
    }

    // =========================================================================
    // MAIN — run all demos
    // =========================================================================
    public static void main(String[] args) {
        System.out.println("=== SRP Demo ===");
        SRP.demo();

        System.out.println("\n=== OCP Demo ===");
        OCP.demo();

        System.out.println("\n=== LSP Demo ===");
        LSP.demo();

        System.out.println("\n=== ISP Demo ===");
        ISP.demo();

        System.out.println("\n=== DIP Demo ===");
        DIP.demo();
    }
}
