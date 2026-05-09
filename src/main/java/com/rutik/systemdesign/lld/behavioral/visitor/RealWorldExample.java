package com.rutik.systemdesign.lld.behavioral.visitor; /**
 * VISITOR PATTERN - Real-World Example: Shopping Cart
 *
 * A shopping cart contains different types of items:
 *   - Book        : subject to 0% tax (tax-exempt in many jurisdictions)
 *   - Electronics : subject to 18% tax
 *   - Clothing    : subject to 5% tax
 *
 * We need two independent operations across the cart:
 *   1. TaxCalculatorVisitor  — computes the tax for each item.
 *   2. DiscountVisitor        — applies category-specific discounts.
 *
 * Neither operation is placed inside the item classes; both live in their
 * own visitor, making it easy to add new operations (e.g. ShippingVisitor)
 * without touching the item hierarchy.
 */

import java.util.ArrayList;
import java.util.List;

// ---------------------------------------------------------------------------
// CartItem (Element interface)
// ---------------------------------------------------------------------------
interface CartItem {
    String getName();
    double getPrice();
    int    getQuantity();

    /** Double-dispatch hook. */
    void accept(CartVisitor visitor);
}

// ---------------------------------------------------------------------------
// CartVisitor (Visitor interface)
//   One overload per concrete CartItem type.
// ---------------------------------------------------------------------------
interface CartVisitor {
    void visit(Book book);
    void visit(Electronics electronics);
    void visit(Clothing clothing);
}

// ---------------------------------------------------------------------------
// Book — ConcreteElement
// ---------------------------------------------------------------------------
class Book implements CartItem {

    private final String name;
    private final double price;
    private final int    quantity;
    private final String author;

    public Book(String name, double price, int quantity, String author) {
        this.name     = name;
        this.price    = price;
        this.quantity = quantity;
        this.author   = author;
    }

    @Override public String getName()     { return name; }
    @Override public double getPrice()    { return price; }
    @Override public int    getQuantity() { return quantity; }
    public    String getAuthor()          { return author; }

    @Override
    public void accept(CartVisitor visitor) {
        visitor.visit(this); // double dispatch
    }
}

// ---------------------------------------------------------------------------
// Electronics — ConcreteElement
// ---------------------------------------------------------------------------
class Electronics implements CartItem {

    private final String name;
    private final double price;
    private final int    quantity;
    private final String brand;
    private final int    warrantyYears;

    public Electronics(String name, double price, int quantity,
                       String brand, int warrantyYears) {
        this.name          = name;
        this.price         = price;
        this.quantity      = quantity;
        this.brand         = brand;
        this.warrantyYears = warrantyYears;
    }

    @Override public String getName()     { return name; }
    @Override public double getPrice()    { return price; }
    @Override public int    getQuantity() { return quantity; }
    public    String getBrand()           { return brand; }
    public    int    getWarrantyYears()   { return warrantyYears; }

    @Override
    public void accept(CartVisitor visitor) {
        visitor.visit(this);
    }
}

// ---------------------------------------------------------------------------
// Clothing — ConcreteElement
// ---------------------------------------------------------------------------
class Clothing implements CartItem {

    private final String name;
    private final double price;
    private final int    quantity;
    private final String size;

    public Clothing(String name, double price, int quantity, String size) {
        this.name     = name;
        this.price    = price;
        this.quantity = quantity;
        this.size     = size;
    }

    @Override public String getName()     { return name; }
    @Override public double getPrice()    { return price; }
    @Override public int    getQuantity() { return quantity; }
    public    String getSize()            { return size; }

    @Override
    public void accept(CartVisitor visitor) {
        visitor.visit(this);
    }
}

// ---------------------------------------------------------------------------
// TaxCalculatorVisitor — ConcreteVisitor 1
//   Book: 0%  |  Electronics: 18%  |  Clothing: 5%
// ---------------------------------------------------------------------------
class TaxCalculatorVisitor implements CartVisitor {

    private double totalTax = 0.0;

    @Override
    public void visit(Book book) {
        // Books are typically tax-exempt
        double tax = 0.0;
        totalTax += tax;
        System.out.printf("  [Tax] %-20s | unit $%7.2f x%d | tax @  0%% = $%.2f%n",
                book.getName(), book.getPrice(), book.getQuantity(), tax);
    }

    @Override
    public void visit(Electronics electronics) {
        double tax = electronics.getPrice() * electronics.getQuantity() * 0.18;
        totalTax += tax;
        System.out.printf("  [Tax] %-20s | unit $%7.2f x%d | tax @ 18%% = $%.2f%n",
                electronics.getName(), electronics.getPrice(),
                electronics.getQuantity(), tax);
    }

    @Override
    public void visit(Clothing clothing) {
        double tax = clothing.getPrice() * clothing.getQuantity() * 0.05;
        totalTax += tax;
        System.out.printf("  [Tax] %-20s | unit $%7.2f x%d | tax @  5%% = $%.2f%n",
                clothing.getName(), clothing.getPrice(),
                clothing.getQuantity(), tax);
    }

    public double getTotalTax() { return totalTax; }
}

// ---------------------------------------------------------------------------
// DiscountVisitor — ConcreteVisitor 2
//   Book: 10% off  |  Electronics: 5% off  |  Clothing: 20% off
// ---------------------------------------------------------------------------
class DiscountVisitor implements CartVisitor {

    private double totalDiscount = 0.0;

    @Override
    public void visit(Book book) {
        double discount = book.getPrice() * book.getQuantity() * 0.10;
        totalDiscount += discount;
        System.out.printf("  [Disc] %-20s | discount @ 10%% = $%.2f%n",
                book.getName(), discount);
    }

    @Override
    public void visit(Electronics electronics) {
        double discount = electronics.getPrice() * electronics.getQuantity() * 0.05;
        totalDiscount += discount;
        System.out.printf("  [Disc] %-20s | discount @  5%% = $%.2f%n",
                electronics.getName(), discount);
    }

    @Override
    public void visit(Clothing clothing) {
        double discount = clothing.getPrice() * clothing.getQuantity() * 0.20;
        totalDiscount += discount;
        System.out.printf("  [Disc] %-20s | discount @ 20%% = $%.2f%n",
                clothing.getName(), discount);
    }

    public double getTotalDiscount() { return totalDiscount; }
}

// ---------------------------------------------------------------------------
// ShoppingCart — ObjectStructure
// ---------------------------------------------------------------------------
class ShoppingCart {

    private final List<CartItem> items = new ArrayList<>();

    public void add(CartItem item) { items.add(item); }

    /** Run a visitor over every item in the cart. */
    public void accept(CartVisitor visitor) {
        for (CartItem item : items) {
            item.accept(visitor);
        }
    }

    /** Compute the raw subtotal (no tax, no discount). */
    public double getSubtotal() {
        return items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
    }
}

// ---------------------------------------------------------------------------
// Main / Demo
// ---------------------------------------------------------------------------
public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("=== Shopping Cart — Visitor Pattern Demo ===\n");

        ShoppingCart cart = new ShoppingCart();
        cart.add(new Book("Clean Code",       45.00, 2, "Robert C. Martin"));
        cart.add(new Electronics("Laptop",  1200.00, 1, "Dell", 2));
        cart.add(new Clothing("T-Shirt",      25.00, 3, "M"));
        cart.add(new Electronics("Headphones", 150.00, 2, "Sony", 1));
        cart.add(new Book("Design Patterns",   55.00, 1, "Gang of Four"));

        double subtotal = cart.getSubtotal();
        System.out.printf("Subtotal (before tax & discount): $%.2f%n%n", subtotal);

        // --- Apply Tax Visitor ---
        System.out.println("--- Tax Calculation ---");
        TaxCalculatorVisitor taxVisitor = new TaxCalculatorVisitor();
        cart.accept(taxVisitor);
        System.out.printf("  Total Tax: $%.2f%n%n", taxVisitor.getTotalTax());

        // --- Apply Discount Visitor ---
        System.out.println("--- Discount Calculation ---");
        DiscountVisitor discountVisitor = new DiscountVisitor();
        cart.accept(discountVisitor);
        System.out.printf("  Total Discount: $%.2f%n%n", discountVisitor.getTotalDiscount());

        // --- Final total ---
        double finalTotal = subtotal + taxVisitor.getTotalTax()
                            - discountVisitor.getTotalDiscount();
        System.out.printf("--- Final Total ----%n");
        System.out.printf("  Subtotal  : $%8.2f%n", subtotal);
        System.out.printf("  + Tax     : $%8.2f%n", taxVisitor.getTotalTax());
        System.out.printf("  - Discount: $%8.2f%n", discountVisitor.getTotalDiscount());
        System.out.printf("  = Grand Total: $%.2f%n", finalTotal);
    }
}
