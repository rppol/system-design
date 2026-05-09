package com.rutik.systemdesign.lld.structural.adapter;

/**
 * ADAPTER PATTERN — Real-World Example: Legacy Payment System Integration
 *
 * Scenario:
 *   A modern e-commerce platform uses a PaymentProcessor interface. A legacy
 *   bank integration (LegacyPaymentGateway) already handles the actual charge
 *   logic but exposes a completely different interface — older method names,
 *   amount in cents instead of dollars, and a separate authorization step.
 *
 *   The LegacyPaymentGateway cannot be modified (it is a third-party library).
 *   The solution: a LegacyPaymentAdapter bridges the gap so the platform's
 *   checkout code never knows about the legacy system.
 *
 * Roles:
 *   - PaymentProcessor       : Target   — what checkout code expects
 *   - LegacyPaymentGateway   : Adaptee  — existing, incompatible library
 *   - LegacyPaymentAdapter   : Adapter  — translates between the two
 *   - ModernStripeProcessor  : a native Target, used for comparison
 */

// ─────────────────────────────────────────────
// TARGET: PaymentProcessor
// Modern interface that the checkout system uses.
// All payment processors must implement this.
// ─────────────────────────────────────────────

interface PaymentProcessor {
    /**
     * Processes a payment for the given order.
     *
     * @param orderId  Unique identifier for the order
     * @param amount   Amount in dollars (e.g., 49.99)
     * @param currency ISO 4217 currency code (e.g., "USD")
     * @return true if payment succeeded, false otherwise
     */
    boolean processPayment(String orderId, double amount, String currency);

    /**
     * Issues a refund for a previously processed order.
     *
     * @param orderId      The original order ID
     * @param refundAmount Amount to refund in dollars
     * @return true if refund succeeded
     */
    boolean refund(String orderId, double refundAmount);
}

// ─────────────────────────────────────────────
// ADAPTEE: LegacyPaymentGateway
// An existing third-party / legacy class that we
// CANNOT modify. It has valuable logic but an
// interface incompatible with PaymentProcessor.
//
// Differences from PaymentProcessor:
//   - Amount is in CENTS (not dollars)
//   - Has a separate authorize() + capture() two-step model
//   - Uses "transactionRef" instead of "orderId"
//   - Refund is called "reverseCharge"
//   - No currency concept — always assumes USD
// ─────────────────────────────────────────────

class LegacyPaymentGateway {

    /**
     * Step 1 of 2: Authorizes (reserves) the funds.
     *
     * @param transactionRef Internal reference (maps to orderId)
     * @param amountInCents  Amount to authorize, in cents
     * @return Authorization token, or null on failure
     */
    public String authorize(String transactionRef, long amountInCents) {
        System.out.println("[LegacyGateway] authorize(): ref=" + transactionRef
                + ", amount=" + amountInCents + " cents");
        // Simulate success — return a fake auth token
        if (amountInCents <= 0) return null;
        return "AUTH-" + transactionRef.toUpperCase() + "-OK";
    }

    /**
     * Step 2 of 2: Captures (finalizes) an authorized charge.
     *
     * @param authToken Token returned by authorize()
     * @return true if capture succeeded
     */
    public boolean capture(String authToken) {
        System.out.println("[LegacyGateway] capture(): authToken=" + authToken);
        return authToken != null && authToken.startsWith("AUTH-");
    }

    /**
     * Reverses (refunds) a previous charge.
     *
     * @param transactionRef The original transaction reference
     * @param amountInCents  Amount to refund in cents
     * @return true if reversal succeeded
     */
    public boolean reverseCharge(String transactionRef, long amountInCents) {
        System.out.println("[LegacyGateway] reverseCharge(): ref=" + transactionRef
                + ", amount=" + amountInCents + " cents");
        return amountInCents > 0;
    }
}

// ─────────────────────────────────────────────
// ADAPTER: LegacyPaymentAdapter
// Implements PaymentProcessor (Target) so the
// checkout system can use it without any changes.
// Internally delegates to LegacyPaymentGateway
// (Adaptee) with all necessary translations.
// ─────────────────────────────────────────────

class LegacyPaymentAdapter implements PaymentProcessor {

    private final LegacyPaymentGateway legacyGateway;

    public LegacyPaymentAdapter(LegacyPaymentGateway legacyGateway) {
        this.legacyGateway = legacyGateway;
    }

    /**
     * Translates processPayment() → authorize() + capture().
     *
     * Translations performed:
     *   - dollars → cents  (multiply by 100, round to long)
     *   - orderId  → transactionRef
     *   - single call → two-step authorize/capture
     *   - currency is logged but legacy gateway ignores it
     */
    @Override
    public boolean processPayment(String orderId, double amount, String currency) {
        System.out.println("[LegacyAdapter] processPayment() — translating for LegacyGateway");
        System.out.println("[LegacyAdapter] Note: currency '" + currency + "' noted; legacy gateway processes as USD");

        // Translation 1: dollars → cents
        long amountInCents = Math.round(amount * 100);

        // Translation 2: single call → two-step model
        String authToken = legacyGateway.authorize(orderId, amountInCents);
        if (authToken == null) {
            System.out.println("[LegacyAdapter] Authorization failed");
            return false;
        }
        boolean captured = legacyGateway.capture(authToken);
        System.out.println("[LegacyAdapter] Payment " + (captured ? "succeeded" : "failed"));
        return captured;
    }

    /**
     * Translates refund() → reverseCharge().
     *
     * Translations performed:
     *   - dollars → cents
     *   - "refund" terminology → "reverseCharge"
     */
    @Override
    public boolean refund(String orderId, double refundAmount) {
        System.out.println("[LegacyAdapter] refund() — translating for LegacyGateway");
        long amountInCents = Math.round(refundAmount * 100);
        boolean success = legacyGateway.reverseCharge(orderId, amountInCents);
        System.out.println("[LegacyAdapter] Refund " + (success ? "succeeded" : "failed"));
        return success;
    }
}

// ─────────────────────────────────────────────
// NATIVE TARGET: ModernStripeProcessor
// A payment processor that already implements
// PaymentProcessor natively — no adapter needed.
// Used to show that the checkout code works
// identically with both implementations.
// ─────────────────────────────────────────────

class ModernStripeProcessor implements PaymentProcessor {

    @Override
    public boolean processPayment(String orderId, double amount, String currency) {
        System.out.println("[StripeProcessor] Charging $" + amount + " " + currency
                + " for order " + orderId + " via Stripe API");
        return true;
    }

    @Override
    public boolean refund(String orderId, double refundAmount) {
        System.out.println("[StripeProcessor] Refunding $" + refundAmount
                + " for order " + orderId + " via Stripe API");
        return true;
    }
}

// ─────────────────────────────────────────────
// CHECKOUT SERVICE
// The client. It only depends on PaymentProcessor.
// Completely unaware of legacy or modern internals.
// ─────────────────────────────────────────────

class CheckoutService {

    private final PaymentProcessor processor;

    public CheckoutService(PaymentProcessor processor) {
        this.processor = processor;
    }

    public void checkout(String orderId, double totalAmount, String currency) {
        System.out.println("\nCheckoutService: Processing order " + orderId
                + " for $" + totalAmount + " " + currency);
        boolean success = processor.processPayment(orderId, totalAmount, currency);
        if (success) {
            System.out.println("CheckoutService: Order " + orderId + " confirmed.");
        } else {
            System.out.println("CheckoutService: Payment FAILED for order " + orderId);
        }
    }

    public void cancelOrder(String orderId, double amount) {
        System.out.println("\nCheckoutService: Refunding order " + orderId + " ($" + amount + ")");
        boolean success = processor.refund(orderId, amount);
        System.out.println("CheckoutService: Refund " + (success ? "issued." : "FAILED."));
    }
}

// ─────────────────────────────────────────────
// MAIN
// ─────────────────────────────────────────────

public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("========================================");
        System.out.println(" Checkout with MODERN Stripe Processor");
        System.out.println("========================================");
        PaymentProcessor stripe = new ModernStripeProcessor();
        CheckoutService stripeCheckout = new CheckoutService(stripe);
        stripeCheckout.checkout("ORD-001", 79.99, "USD");
        stripeCheckout.cancelOrder("ORD-001", 79.99);

        System.out.println();

        System.out.println("========================================");
        System.out.println(" Checkout with LEGACY Gateway (via Adapter)");
        System.out.println("========================================");
        // The legacy gateway is wrapped by the adapter.
        // CheckoutService sees only PaymentProcessor — completely transparent.
        LegacyPaymentGateway legacyGateway = new LegacyPaymentGateway();
        PaymentProcessor legacyAdapter = new LegacyPaymentAdapter(legacyGateway);
        CheckoutService legacyCheckout = new CheckoutService(legacyAdapter);
        legacyCheckout.checkout("ORD-002", 149.50, "USD");
        legacyCheckout.cancelOrder("ORD-002", 149.50);

        System.out.println();

        System.out.println("========================================");
        System.out.println(" Swapping processors at runtime (config-driven)");
        System.out.println("========================================");
        // Simulating a feature flag: route high-value orders to legacy, others to Stripe
        String orderId = "ORD-003";
        double amount = 300.00;
        boolean useStripe = amount < 200.0;

        PaymentProcessor selected = useStripe
                ? new ModernStripeProcessor()
                : new LegacyPaymentAdapter(new LegacyPaymentGateway());

        System.out.println("Selected processor: " + selected.getClass().getSimpleName());
        new CheckoutService(selected).checkout(orderId, amount, "USD");
    }
}

/*
 * WHAT THIS EXAMPLE DEMONSTRATES:
 *
 * 1. Interface mismatch resolution:
 *    LegacyPaymentGateway speaks "cents + two-step auth/capture".
 *    PaymentProcessor speaks "dollars + single processPayment call".
 *    The Adapter translates between them without touching either side.
 *
 * 2. Open/Closed Principle:
 *    CheckoutService is closed for modification but open for extension —
 *    new payment backends plug in by implementing PaymentProcessor or
 *    by wrapping an existing backend in a new Adapter.
 *
 * 3. Runtime substitutability:
 *    The "feature flag" section shows that the adapter and native
 *    implementations are interchangeable at runtime — identical behavior
 *    from CheckoutService's perspective.
 *
 * 4. Isolation of translation logic:
 *    All cents/dollars and authorize/capture translation is in one class.
 *    If the legacy gateway's contract changes, only the Adapter changes.
 */
