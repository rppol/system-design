package com.rutik.systemdesign.lld.behavioral.strategy;

/**
 * STRATEGY PATTERN - Real-World Example: Payment Processing
 *
 * An e-commerce checkout supports multiple payment methods.
 * Each method (CreditCard, PayPal, Bitcoin) is a separate strategy.
 * The PaymentProcessor (Context) delegates the actual payment to
 * whichever strategy is configured at checkout time.
 *
 * Adding a new payment method never touches existing code — just add
 * a new ConcreteStrategy class (Open/Closed Principle).
 */

// ---------------------------------------------------------------------------
// PaymentStrategy interface
// ---------------------------------------------------------------------------
interface PaymentStrategy {
    /**
     * Process the payment for the given amount.
     * @param amount total amount to charge (in USD)
     * @return true if payment succeeded, false otherwise
     */
    boolean pay(double amount);

    /** Human-readable name of this payment method. */
    String getMethodName();
}

// ---------------------------------------------------------------------------
// CreditCardPayment strategy
// ---------------------------------------------------------------------------
class CreditCardPayment implements PaymentStrategy {

    private final String cardHolderName;
    private final String cardNumber;   // last 4 digits for display
    private final String cvv;
    private final String expiryDate;

    public CreditCardPayment(String cardHolderName, String cardNumber,
                             String cvv, String expiryDate) {
        this.cardHolderName = cardHolderName;
        this.cardNumber     = cardNumber;
        this.cvv            = cvv;
        this.expiryDate     = expiryDate;
    }

    @Override
    public boolean pay(double amount) {
        // Real implementation would call a payment gateway API
        System.out.println("[CreditCard] Charging $" + String.format("%.2f", amount)
                + " to card ending in " + cardNumber.substring(cardNumber.length() - 4)
                + " (" + cardHolderName + ")");
        System.out.println("[CreditCard] Authorising with CVV and expiry " + expiryDate + "...");
        System.out.println("[CreditCard] Payment approved.");
        return true;
    }

    @Override
    public String getMethodName() { return "Credit Card"; }
}

// ---------------------------------------------------------------------------
// PayPalPayment strategy
// ---------------------------------------------------------------------------
class PayPalPayment implements PaymentStrategy {

    private final String email;
    private final String password; // in reality this would never be stored in plain text

    public PayPalPayment(String email, String password) {
        this.email    = email;
        this.password = password;
    }

    @Override
    public boolean pay(double amount) {
        System.out.println("[PayPal] Logging in as " + email + "...");
        System.out.println("[PayPal] Sending $" + String.format("%.2f", amount)
                + " via PayPal...");
        System.out.println("[PayPal] Payment confirmed.");
        return true;
    }

    @Override
    public String getMethodName() { return "PayPal"; }
}

// ---------------------------------------------------------------------------
// BitcoinPayment strategy
// ---------------------------------------------------------------------------
class BitcoinPayment implements PaymentStrategy {

    private final String walletAddress;
    // Exchange rate — in a real system this would be fetched live
    private static final double BTC_PER_USD = 0.000015;

    public BitcoinPayment(String walletAddress) {
        this.walletAddress = walletAddress;
    }

    @Override
    public boolean pay(double amount) {
        double btcAmount = amount * BTC_PER_USD;
        System.out.println("[Bitcoin] Converting $" + String.format("%.2f", amount)
                + " -> " + String.format("%.8f", btcAmount) + " BTC");
        System.out.println("[Bitcoin] Sending to wallet: " + walletAddress);
        System.out.println("[Bitcoin] Transaction broadcast to network. Awaiting confirmation...");
        System.out.println("[Bitcoin] Payment confirmed.");
        return true;
    }

    @Override
    public String getMethodName() { return "Bitcoin"; }
}

// ---------------------------------------------------------------------------
// ShoppingCart — holds items and total
// ---------------------------------------------------------------------------
class ShoppingCart {

    private final java.util.List<String> items = new java.util.ArrayList<>();
    private double total = 0.0;

    public void addItem(String name, double price) {
        items.add(name);
        total += price;
        System.out.println("  Added: " + name + " ($" + String.format("%.2f", price) + ")");
    }

    public double getTotal() { return total; }

    public void printReceipt() {
        System.out.println("  Items: " + items);
        System.out.println("  Total: $" + String.format("%.2f", total));
    }
}

// ---------------------------------------------------------------------------
// PaymentProcessor (Context)
//   - Decoupled from any specific payment method.
//   - Accepts any PaymentStrategy; can be swapped at runtime.
// ---------------------------------------------------------------------------
class PaymentProcessor {

    private PaymentStrategy paymentStrategy;

    public PaymentProcessor(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
    }

    /** Allows the customer to switch payment method before confirming. */
    public void setPaymentStrategy(PaymentStrategy paymentStrategy) {
        this.paymentStrategy = paymentStrategy;
        System.out.println("Payment method switched to: " + paymentStrategy.getMethodName());
    }

    /**
     * Processes checkout for the given cart using the current strategy.
     */
    public void checkout(ShoppingCart cart) {
        System.out.println("\nCheckout Summary:");
        cart.printReceipt();
        System.out.println("Processing payment via " + paymentStrategy.getMethodName() + "...");
        boolean success = paymentStrategy.pay(cart.getTotal());
        if (success) {
            System.out.println("Order placed successfully!\n");
        } else {
            System.out.println("Payment failed. Please try another method.\n");
        }
    }
}

// ---------------------------------------------------------------------------
// Main / Demo
// ---------------------------------------------------------------------------
public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("=== Payment Strategy Pattern Demo ===\n");

        // Build a shopping cart
        ShoppingCart cart = new ShoppingCart();
        System.out.println("Adding items to cart:");
        cart.addItem("Laptop",      999.99);
        cart.addItem("Mouse",        29.99);
        cart.addItem("USB-C Hub",    49.99);

        // --- Pay with Credit Card ---
        System.out.println("\n--- Scenario 1: Pay with Credit Card ---");
        PaymentProcessor processor = new PaymentProcessor(
                new CreditCardPayment("Alice Smith", "4111111111111234", "123", "12/27"));
        processor.checkout(cart);

        // --- Pay with PayPal ---
        System.out.println("--- Scenario 2: Pay with PayPal ---");
        processor.setPaymentStrategy(new PayPalPayment("alice@example.com", "s3cr3t"));
        processor.checkout(cart);

        // --- Pay with Bitcoin ---
        System.out.println("--- Scenario 3: Pay with Bitcoin ---");
        processor.setPaymentStrategy(
                new BitcoinPayment("1A2b3C4d5E6f7G8h9I0jKLMNOPQRSTUVWXYZ"));
        processor.checkout(cart);

        // --- Switch mid-session (customer changes mind) ---
        System.out.println("--- Scenario 4: Switch from Bitcoin to Credit Card ---");
        processor.setPaymentStrategy(
                new CreditCardPayment("Alice Smith", "5500005555555559", "456", "09/26"));
        processor.checkout(cart);
    }
}
