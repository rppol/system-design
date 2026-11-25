package com.rutik.systemdesign.lld.system_design_problems;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

// =============================================================================
//  PAYMENT PROCESSOR / DIGITAL WALLET - Low-Level Design
//
//  Core ideas:
//    - Money is a long count of minor units plus a currency, never a double
//    - Every money movement is a balanced pair of ledger postings (double entry)
//    - A payment is a state machine; illegal transitions throw
//    - An idempotency store makes a retried request return the first result
//    - Gateway calls are retried with capped exponential backoff and full jitter,
//      and carry a gateway idempotency key derived from the client key
//
//  Patterns used:
//    - State     : PaymentStatus owns its legal next states
//    - Strategy  : PaymentMethod (WalletMethod, CardMethod)
//    - Command   : RefundCommand executed through an idempotent CommandLog
//    - Observer  : PaymentEventListener notified on every transition
//    - Adapter   : CardMethod adapts the external PaymentGateway
//    - Facade    : PaymentService is the single entry point
//
//  All types are nested so this file never collides with other problems in
//  the same package. Run main() to see every path, including the concurrency
//  and conservation checks.
// =============================================================================

public class PaymentProcessor {

    // -------------------------------------------------------------------------
    //  MONEY - minor units plus currency. Two decimal places in this demo; the
    //  per-currency exponent (ISO 4217) is a fintech concern, see the .md page.
    // -------------------------------------------------------------------------

    record Money(long minor, String currency) {
        Money {
            Objects.requireNonNull(currency, "currency");
        }

        static Money of(String amount, String currency) {
            return new Money(new BigDecimal(amount).movePointRight(2).longValueExact(), currency);
        }

        static Money zero(String currency) {
            return new Money(0, currency);
        }

        Money plus(Money o) {
            requireSameCurrency(o);
            return new Money(Math.addExact(minor, o.minor), currency);
        }

        Money minus(Money o) {
            requireSameCurrency(o);
            return new Money(Math.subtractExact(minor, o.minor), currency);
        }

        boolean greaterThan(Money o) {
            requireSameCurrency(o);
            return minor > o.minor;
        }

        private void requireSameCurrency(Money o) {
            if (!currency.equals(o.currency)) {
                throw new IllegalArgumentException("currency mismatch: " + currency + " vs " + o.currency);
            }
        }

        @Override
        public String toString() {
            long abs = Math.abs(minor);
            return String.format("%s %s%d.%02d", currency, minor < 0 ? "-" : "", abs / 100, abs % 100);
        }
    }

    // -------------------------------------------------------------------------
    //  EXCEPTIONS
    // -------------------------------------------------------------------------

    static final class InsufficientFundsException extends RuntimeException {
        InsufficientFundsException(String account, Money needed) {
            super("insufficient funds in " + account + " for " + needed);
        }
    }

    static final class IdempotencyConflictException extends RuntimeException {
        IdempotencyConflictException(String key) {
            super("idempotency key " + key + " was already used with different parameters");
        }
    }

    static final class RequestInProgressException extends RuntimeException {
        RequestInProgressException(String key) {
            super("request with key " + key + " is still in progress");
        }
    }

    static final class OutcomeUnknownException extends RuntimeException {
        OutcomeUnknownException(String paymentId, Throwable cause) {
            super("outcome unknown for " + paymentId + ", retry with the SAME key", cause);
        }
    }

    /** A failure reported by the external gateway. Only retryable ones are retried. */
    static final class GatewayException extends RuntimeException {
        final boolean retryable;

        GatewayException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
    }

    // -------------------------------------------------------------------------
    //  STATE PATTERN - the status enum owns its legal transitions
    // -------------------------------------------------------------------------

    enum PaymentStatus {
        CREATED, AUTHORIZED, CAPTURED, PARTIALLY_REFUNDED, REFUNDED, VOIDED, FAILED;

        private Set<PaymentStatus> next;

        static {
            CREATED.next = EnumSet.of(AUTHORIZED, FAILED);
            AUTHORIZED.next = EnumSet.of(CAPTURED, VOIDED, FAILED);
            CAPTURED.next = EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            PARTIALLY_REFUNDED.next = EnumSet.of(PARTIALLY_REFUNDED, REFUNDED);
            REFUNDED.next = EnumSet.noneOf(PaymentStatus.class);
            VOIDED.next = EnumSet.noneOf(PaymentStatus.class);
            FAILED.next = EnumSet.noneOf(PaymentStatus.class);
        }

        boolean canMoveTo(PaymentStatus target) {
            return next.contains(target);
        }

        boolean isTerminal() {
            return next.isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    //  PAYMENT ENTITY
    // -------------------------------------------------------------------------

    static final class Payment {
        final String id;
        final String idempotencyKey;
        final String payer;      // wallet id, or a card token such as "card:tok_visa"
        final String payee;
        final Money amount;
        final String method;
        private PaymentStatus status = PaymentStatus.CREATED;
        private Money refunded;
        private String authRef;

        Payment(String id, String idempotencyKey, PaymentRequest req) {
            this.id = id;
            this.idempotencyKey = idempotencyKey;
            this.payer = req.payer();
            this.payee = req.payee();
            this.amount = req.amount();
            this.method = req.method();
            this.refunded = Money.zero(req.amount().currency());
        }

        synchronized PaymentStatus status() {
            return status;
        }

        synchronized Money refunded() {
            return refunded;
        }

        /** Validates and applies a transition; returns the previous status. */
        synchronized PaymentStatus moveTo(PaymentStatus target) {
            if (!status.canMoveTo(target)) {
                throw new IllegalStateException(id + ": " + status + " -> " + target + " is not allowed");
            }
            PaymentStatus from = status;
            status = target;
            return from;
        }
    }

    // -------------------------------------------------------------------------
    //  LEDGER - double entry. Each transfer writes one debit and one credit,
    //  so the journal always sums to zero per currency.
    // -------------------------------------------------------------------------

    record LedgerEntry(long seq, String txnId, String account, long minor, String currency, String memo) { }

    static final class Account {
        final String id;
        final String currency;
        final boolean allowNegative;   // true only for external clearing/funding accounts
        long balanceMinor;             // guarded by lock
        final ReentrantLock lock = new ReentrantLock();

        Account(String id, String currency, boolean allowNegative) {
            this.id = id;
            this.currency = currency;
            this.allowNegative = allowNegative;
        }
    }

    static final class Ledger {
        private final Map<String, Account> accounts = new ConcurrentHashMap<>();
        private final Queue<LedgerEntry> journal = new ConcurrentLinkedQueue<>();
        private final AtomicLong seq = new AtomicLong();

        void open(String id, String currency, boolean allowNegative) {
            accounts.putIfAbsent(id, new Account(id, currency, allowNegative));
        }

        /**
         * Moves money between two accounts atomically. Both locks are taken in
         * account-id order, so A->B and B->A transfers can never deadlock.
         */
        void transfer(String txnId, String fromId, String toId, Money amt, String memo) {
            if (fromId.equals(toId)) {
                throw new IllegalArgumentException("self transfer");
            }
            if (amt.minor() <= 0) {
                throw new IllegalArgumentException("amount must be positive: " + amt);
            }
            Account from = require(fromId);
            Account to = require(toId);
            Account first = fromId.compareTo(toId) < 0 ? from : to;
            Account second = first == from ? to : from;
            first.lock.lock();
            try {
                second.lock.lock();
                try {
                    if (!from.currency.equals(amt.currency()) || !to.currency.equals(amt.currency())) {
                        throw new IllegalArgumentException("currency mismatch on " + txnId);
                    }
                    if (!from.allowNegative && from.balanceMinor < amt.minor()) {
                        throw new InsufficientFundsException(fromId, amt);
                    }
                    from.balanceMinor -= amt.minor();
                    to.balanceMinor += amt.minor();
                    long s = seq.incrementAndGet();
                    journal.add(new LedgerEntry(s, txnId, fromId, -amt.minor(), amt.currency(), memo));
                    journal.add(new LedgerEntry(s, txnId, toId, amt.minor(), amt.currency(), memo));
                } finally {
                    second.lock.unlock();
                }
            } finally {
                first.lock.unlock();
            }
        }

        Money balance(String id) {
            Account a = require(id);
            a.lock.lock();
            try {
                return new Money(a.balanceMinor, a.currency);
            } finally {
                a.lock.unlock();
            }
        }

        long journalSum(String currency) {
            long sum = 0;
            for (LedgerEntry e : journal) {
                if (e.currency().equals(currency)) {
                    sum += e.minor();
                }
            }
            return sum;
        }

        long balanceSum(String currency) {
            long sum = 0;
            for (Account a : accounts.values()) {
                if (a.currency.equals(currency)) {
                    sum += balance(a.id).minor();
                }
            }
            return sum;
        }

        int journalSize() {
            return journal.size();
        }

        private Account require(String id) {
            Account a = accounts.get(id);
            if (a == null) {
                throw new IllegalArgumentException("unknown account " + id);
            }
            return a;
        }
    }

    // -------------------------------------------------------------------------
    //  IDEMPOTENCY - first request with a key executes, later ones replay
    // -------------------------------------------------------------------------

    record PaymentRequest(String payer, String payee, Money amount, String method) {
        String fingerprint() {
            return payer + "|" + payee + "|" + amount.minor() + "|" + amount.currency() + "|" + method;
        }
    }

    record PaymentResult(String paymentId, PaymentStatus status, String detail, boolean replayed) {
        PaymentResult asReplay() {
            return new PaymentResult(paymentId, status, detail, true);
        }
    }

    enum IdemState { IN_PROGRESS, COMPLETED }

    static final class IdempotencyRecord {
        final String fingerprint;
        volatile PaymentResult result;              // written BEFORE state flips
        volatile IdemState state = IdemState.IN_PROGRESS;

        IdempotencyRecord(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }

    static final class IdempotencyStore {
        private final ConcurrentHashMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

        /** Returns null when the caller won the key and must execute; otherwise the prior record. */
        IdempotencyRecord claim(String key, String fingerprint) {
            IdempotencyRecord prior = records.putIfAbsent(key, new IdempotencyRecord(fingerprint));
            if (prior == null) {
                return null;
            }
            if (!prior.fingerprint.equals(fingerprint)) {
                throw new IdempotencyConflictException(key);
            }
            return prior;
        }

        void complete(String key, PaymentResult result) {
            IdempotencyRecord r = records.get(key);
            r.result = result;
            r.state = IdemState.COMPLETED;
        }

        /** Unknown outcome: drop the claim so the client may retry with the same key. */
        void release(String key) {
            records.remove(key);
        }
    }

    // -------------------------------------------------------------------------
    //  RETRY - capped exponential backoff with full jitter
    // -------------------------------------------------------------------------

    interface Sleeper {
        void sleep(long millis);
    }

    interface GatewayCall<T> {
        T call();
    }

    static final class RetryPolicy {
        private final int maxAttempts;
        private final long baseMillis;
        private final long capMillis;
        private final Random random;
        private final Sleeper sleeper;

        RetryPolicy(int maxAttempts, long baseMillis, long capMillis, Random random, Sleeper sleeper) {
            this.maxAttempts = maxAttempts;
            this.baseMillis = baseMillis;
            this.capMillis = capMillis;
            this.random = random;
            this.sleeper = sleeper;
        }

        <T> T run(GatewayCall<T> op) {
            for (int attempt = 1; ; attempt++) {
                try {
                    return op.call();
                } catch (GatewayException e) {
                    if (!e.retryable || attempt == maxAttempts) {
                        throw e;
                    }
                    long ceiling = Math.min(capMillis, baseMillis << (attempt - 1));
                    long delay = (long) (random.nextDouble() * ceiling);   // full jitter
                    System.out.printf("    [retry] attempt %d failed (%s), backing off %d ms (ceiling %d ms)%n",
                            attempt, e.getMessage(), delay, ceiling);
                    sleeper.sleep(delay);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    //  EXTERNAL GATEWAY (simulated) - dedupes by gateway idempotency key
    // -------------------------------------------------------------------------

    enum GatewayFault { TIMEOUT_AFTER_PROCESSING, UNAVAILABLE }

    interface PaymentGateway {
        String authorize(String gatewayKey, String cardToken, Money amount);

        void capture(String authRef);

        void refund(String authRef, Money amount, String refundId);
    }

    static final class SimulatedGateway implements PaymentGateway {
        private final Map<String, String> authsByKey = new ConcurrentHashMap<>();
        private final Deque<GatewayFault> script = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger authSeq = new AtomicInteger();

        void scriptFaults(GatewayFault... faults) {
            script.addAll(List.of(faults));
        }

        @Override
        public synchronized String authorize(String gatewayKey, String cardToken, Money amount) {
            calls.incrementAndGet();
            if (cardToken.endsWith("tok_decline")) {
                throw new GatewayException("card declined", false);
            }
            GatewayFault fault = script.pollFirst();
            if (fault == GatewayFault.UNAVAILABLE) {
                throw new GatewayException("503 before processing", true);
            }
            String auth = authsByKey.computeIfAbsent(gatewayKey, k -> "AUTH-" + authSeq.incrementAndGet());
            if (fault == GatewayFault.TIMEOUT_AFTER_PROCESSING) {
                throw new GatewayException("timeout after processing", true);
            }
            return auth;
        }

        @Override
        public void capture(String authRef) {
            calls.incrementAndGet();
        }

        @Override
        public void refund(String authRef, Money amount, String refundId) {
            calls.incrementAndGet();
        }

        int calls() {
            return calls.get();
        }

        int distinctAuthorizations() {
            return authsByKey.size();
        }
    }

    // -------------------------------------------------------------------------
    //  STRATEGY - how a payment method authorizes, captures and refunds
    // -------------------------------------------------------------------------

    interface PaymentMethod {
        String name();

        String authorize(Payment p);

        void capture(Payment p, String authRef);

        void voidAuthorization(Payment p, String authRef);

        void refund(Payment p, Money amount, String refundId);
    }

    /** Wallet: authorize moves funds into a holds account, capture moves them to the payee. */
    static final class WalletMethod implements PaymentMethod {
        static final String HOLDS = "sys:wallet-holds";
        private final Ledger ledger;

        WalletMethod(Ledger ledger) {
            this.ledger = ledger;
        }

        @Override
        public String name() {
            return "wallet";
        }

        @Override
        public String authorize(Payment p) {
            ledger.transfer(p.id + ":auth", p.payer, HOLDS, p.amount, "hold");
            return "HOLD-" + p.id;
        }

        @Override
        public void capture(Payment p, String authRef) {
            ledger.transfer(p.id + ":capture", HOLDS, p.payee, p.amount, "capture");
        }

        @Override
        public void voidAuthorization(Payment p, String authRef) {
            ledger.transfer(p.id + ":void", HOLDS, p.payer, p.amount, "void");
        }

        @Override
        public void refund(Payment p, Money amount, String refundId) {
            ledger.transfer(refundId, p.payee, p.payer, amount, "refund");
        }
    }

    /** Card: Adapter over the external gateway; money lands via a card clearing account. */
    static final class CardMethod implements PaymentMethod {
        static final String CLEARING = "ext:card-clearing";
        private final PaymentGateway gateway;
        private final RetryPolicy retry;
        private final Ledger ledger;

        CardMethod(PaymentGateway gateway, RetryPolicy retry, Ledger ledger) {
            this.gateway = gateway;
            this.retry = retry;
            this.ledger = ledger;
        }

        @Override
        public String name() {
            return "card";
        }

        @Override
        public String authorize(Payment p) {
            // The gateway key comes from the CLIENT key, so a fresh payment object
            // created by a client retry still maps to the same gateway authorization.
            String gatewayKey = "gw-" + p.idempotencyKey;
            return retry.run(() -> gateway.authorize(gatewayKey, p.payer, p.amount));
        }

        @Override
        public void capture(Payment p, String authRef) {
            gateway.capture(authRef);
            ledger.transfer(p.id + ":capture", CLEARING, p.payee, p.amount, "card capture");
        }

        @Override
        public void voidAuthorization(Payment p, String authRef) {
            // A real gateway call would release the issuer hold here.
        }

        @Override
        public void refund(Payment p, Money amount, String refundId) {
            gateway.refund(p.authRef, amount, refundId);
            ledger.transfer(refundId, p.payee, CLEARING, amount, "card refund");
        }
    }

    // -------------------------------------------------------------------------
    //  OBSERVER - every transition is published
    // -------------------------------------------------------------------------

    interface PaymentEventListener {
        void onTransition(Payment p, PaymentStatus from, PaymentStatus to);
    }

    static final class AuditLogListener implements PaymentEventListener {
        @Override
        public void onTransition(Payment p, PaymentStatus from, PaymentStatus to) {
            System.out.printf("    [audit] %s %s -> %s%n", p.id, from, to);
        }
    }

    // -------------------------------------------------------------------------
    //  COMMAND - refunds are objects: logged, deduplicated, replayable
    // -------------------------------------------------------------------------

    interface MoneyCommand {
        String id();

        void execute();

        String describe();
    }

    static final class CommandLog {
        private final Set<String> executed = new HashSet<>();
        private final List<String> history = new ArrayList<>();

        /** Executes a command once; a second submit with the same id is a no-op. */
        synchronized boolean submit(MoneyCommand cmd) {
            if (executed.contains(cmd.id())) {
                System.out.println("    [command] duplicate " + cmd.id() + " ignored");
                return false;
            }
            cmd.execute();
            executed.add(cmd.id());
            history.add(cmd.describe());
            return true;
        }

        synchronized List<String> history() {
            return List.copyOf(history);
        }
    }

    static final class RefundCommand implements MoneyCommand {
        private final String refundId;
        private final Payment payment;
        private final Money amount;
        private final PaymentService service;

        RefundCommand(String refundId, Payment payment, Money amount, PaymentService service) {
            this.refundId = refundId;
            this.payment = payment;
            this.amount = amount;
            this.service = service;
        }

        @Override
        public String id() {
            return refundId;
        }

        @Override
        public void execute() {
            synchronized (payment) {
                PaymentStatus s = payment.status();
                if (s != PaymentStatus.CAPTURED && s != PaymentStatus.PARTIALLY_REFUNDED) {
                    throw new IllegalStateException(payment.id + " cannot be refunded from " + s);
                }
                Money remaining = payment.amount.minus(payment.refunded);
                if (amount.greaterThan(remaining)) {
                    throw new IllegalArgumentException("refund " + amount + " exceeds refundable " + remaining);
                }
                service.method(payment.method).refund(payment, amount, refundId);
                payment.refunded = payment.refunded.plus(amount);
                PaymentStatus target = payment.refunded.equals(payment.amount)
                        ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
                service.move(payment, target);
            }
        }

        @Override
        public String describe() {
            return refundId + " refund " + amount + " on " + payment.id;
        }
    }

    // -------------------------------------------------------------------------
    //  FACADE - the payment service
    // -------------------------------------------------------------------------

    static final class PaymentService {
        private final Map<String, PaymentMethod> methods = new ConcurrentHashMap<>();
        private final Map<String, Payment> payments = new ConcurrentHashMap<>();
        private final Map<String, Payment> paymentsByKey = new ConcurrentHashMap<>();
        private final IdempotencyStore idempotency = new IdempotencyStore();
        private final List<PaymentEventListener> listeners = new CopyOnWriteArrayList<>();
        private final CommandLog commandLog = new CommandLog();
        private final AtomicInteger paymentSeq = new AtomicInteger(1000);
        final AtomicInteger executions = new AtomicInteger();

        void register(PaymentMethod m) {
            methods.put(m.name(), m);
        }

        void addListener(PaymentEventListener l) {
            listeners.add(l);
        }

        PaymentMethod method(String name) {
            PaymentMethod m = methods.get(name);
            if (m == null) {
                throw new IllegalArgumentException("unknown method " + name);
            }
            return m;
        }

        void move(Payment p, PaymentStatus to) {
            PaymentStatus from = p.moveTo(to);
            for (PaymentEventListener l : listeners) {
                l.onTransition(p, from, to);
            }
        }

        PaymentResult pay(String idempotencyKey, PaymentRequest req) {
            IdempotencyRecord prior = idempotency.claim(idempotencyKey, req.fingerprint());
            if (prior != null) {
                if (prior.state == IdemState.IN_PROGRESS) {
                    throw new RequestInProgressException(idempotencyKey);
                }
                return prior.result.asReplay();
            }
            executions.incrementAndGet();
            // Reuse the payment created by an earlier attempt that ended "outcome unknown".
            Payment p = paymentsByKey.computeIfAbsent(idempotencyKey,
                    k -> new Payment("PAY-" + paymentSeq.incrementAndGet(), k, req));
            payments.putIfAbsent(p.id, p);
            PaymentMethod m = method(req.method());
            PaymentResult result;
            try {
                p.authRef = m.authorize(p);
                move(p, PaymentStatus.AUTHORIZED);
                m.capture(p, p.authRef);
                move(p, PaymentStatus.CAPTURED);
                result = new PaymentResult(p.id, PaymentStatus.CAPTURED, p.authRef, false);
            } catch (GatewayException e) {
                if (e.retryable) {
                    idempotency.release(idempotencyKey);
                    throw new OutcomeUnknownException(p.id, e);
                }
                move(p, PaymentStatus.FAILED);
                result = new PaymentResult(p.id, PaymentStatus.FAILED, e.getMessage(), false);
            } catch (InsufficientFundsException e) {
                move(p, PaymentStatus.FAILED);
                result = new PaymentResult(p.id, PaymentStatus.FAILED, e.getMessage(), false);
            }
            idempotency.complete(idempotencyKey, result);
            return result;
        }

        boolean refund(String paymentId, Money amount, String refundId) {
            return commandLog.submit(new RefundCommand(refundId, payment(paymentId), amount, this));
        }

        void voidPayment(String paymentId) {
            Payment p = payment(paymentId);
            synchronized (p) {
                move(p, PaymentStatus.VOIDED);
                method(p.method).voidAuthorization(p, p.authRef);
            }
        }

        Payment payment(String id) {
            Payment p = payments.get(id);
            if (p == null) {
                throw new IllegalArgumentException("unknown payment " + id);
            }
            return p;
        }

        List<String> commandHistory() {
            return commandLog.history();
        }
    }

    // -------------------------------------------------------------------------
    //  DEMO
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("   Payment Processor - LLD Demo");
        System.out.println("========================================");

        Ledger ledger = new Ledger();
        ledger.open("ext:funding", "USD", true);
        ledger.open(CardMethod.CLEARING, "USD", true);
        ledger.open(WalletMethod.HOLDS, "USD", false);
        ledger.open("alice", "USD", false);
        ledger.open("bob", "USD", false);
        ledger.open("merchant", "USD", false);
        ledger.transfer("seed-1", "ext:funding", "alice", Money.of("100.00", "USD"), "top-up");
        ledger.transfer("seed-2", "ext:funding", "bob", Money.of("20.00", "USD"), "top-up");

        SimulatedGateway gateway = new SimulatedGateway();
        RetryPolicy retry = new RetryPolicy(3, 100, 2_000, new Random(42), ms -> { });
        PaymentService service = new PaymentService();
        service.register(new WalletMethod(ledger));
        service.register(new CardMethod(gateway, retry, ledger));
        service.addListener(new AuditLogListener());

        System.out.println("\n--- 1. Wallet payment alice -> merchant, then the client retries ---");
        PaymentRequest r1 = new PaymentRequest("alice", "merchant", Money.of("25.00", "USD"), "wallet");
        PaymentResult first = service.pay("key-001", r1);
        System.out.println("  first  : " + first);
        PaymentResult again = service.pay("key-001", r1);
        System.out.println("  retry  : " + again);
        System.out.println("  alice=" + ledger.balance("alice") + "  merchant=" + ledger.balance("merchant"));

        System.out.println("\n--- 2. Same key, different amount ---");
        try {
            service.pay("key-001", new PaymentRequest("alice", "merchant", Money.of("30.00", "USD"), "wallet"));
        } catch (IdempotencyConflictException e) {
            System.out.println("  rejected: " + e.getMessage());
        }

        System.out.println("\n--- 3. Insufficient funds ---");
        System.out.println("  " + service.pay("key-002",
                new PaymentRequest("bob", "merchant", Money.of("500.00", "USD"), "wallet")));

        System.out.println("\n--- 4. Card payment: timeout after processing, then 503, then success ---");
        gateway.scriptFaults(GatewayFault.TIMEOUT_AFTER_PROCESSING, GatewayFault.UNAVAILABLE);
        PaymentResult card = service.pay("key-003",
                new PaymentRequest("card:tok_visa", "merchant", Money.of("40.00", "USD"), "card"));
        System.out.println("  " + card);
        System.out.println("  gateway calls=" + gateway.calls() + ", distinct authorizations="
                + gateway.distinctAuthorizations());

        System.out.println("\n--- 5. Card decline is not retried ---");
        int before = gateway.calls();
        System.out.println("  " + service.pay("key-004",
                new PaymentRequest("card:tok_decline", "merchant", Money.of("10.00", "USD"), "card")));
        System.out.println("  gateway calls for this payment=" + (gateway.calls() - before));

        System.out.println("\n--- 6. Gateway down past the retry budget, client retries the SAME key ---");
        gateway.scriptFaults(GatewayFault.UNAVAILABLE, GatewayFault.UNAVAILABLE, GatewayFault.UNAVAILABLE);
        PaymentRequest r6 = new PaymentRequest("card:tok_visa", "merchant", Money.of("15.00", "USD"), "card");
        try {
            service.pay("key-005", r6);
        } catch (OutcomeUnknownException e) {
            System.out.println("  client sees: " + e.getMessage());
        }
        PaymentResult resumed = service.pay("key-005", r6);
        System.out.println("  client retry: " + resumed);
        System.out.println("  distinct authorizations=" + gateway.distinctAuthorizations());

        System.out.println("\n--- 7. Refunds as commands ---");
        String paid = first.paymentId();
        service.refund(paid, Money.of("10.00", "USD"), "RF-1");
        service.refund(paid, Money.of("10.00", "USD"), "RF-1");
        try {
            service.refund(paid, Money.of("20.00", "USD"), "RF-2");
        } catch (IllegalArgumentException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        service.refund(paid, Money.of("15.00", "USD"), "RF-3");
        System.out.println("  command log: " + service.commandHistory());
        System.out.println("  alice=" + ledger.balance("alice") + "  merchant=" + ledger.balance("merchant"));

        System.out.println("\n--- 8. Illegal transition ---");
        try {
            service.voidPayment(paid);
        } catch (IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }

        System.out.println("\n--- 9. Concurrency: 8 threads, one idempotency key ---");
        service.listeners.clear();
        int execBefore = service.executions.get();
        Money aliceBefore = ledger.balance("alice");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger deduplicated = new AtomicInteger();
        PaymentRequest r9 = new PaymentRequest("alice", "merchant", Money.of("1.00", "USD"), "wallet");
        for (int i = 0; i < 8; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    if (service.pay("key-burst", r9).replayed()) {
                        deduplicated.incrementAndGet();
                    }
                } catch (RequestInProgressException e) {
                    deduplicated.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("  executions=" + (service.executions.get() - execBefore)
                + ", deduplicated=" + deduplicated.get()
                + ", alice debited " + aliceBefore.minus(ledger.balance("alice")));

        System.out.println("\n--- 10. Concurrency: 2,000 opposing transfers alice <-> bob ---");
        ExecutorService movers = Executors.newFixedThreadPool(4);
        Money cent = Money.of("0.01", "USD");
        Money pairBefore = ledger.balance("alice").plus(ledger.balance("bob"));
        for (int t = 0; t < 4; t++) {
            boolean aliceToBob = t % 2 == 0;
            int worker = t;
            movers.submit(() -> {
                for (int i = 0; i < 500; i++) {
                    String id = "x-" + worker + "-" + i;
                    if (aliceToBob) {
                        ledger.transfer(id, "alice", "bob", cent, "p2p");
                    } else {
                        ledger.transfer(id, "bob", "alice", cent, "p2p");
                    }
                }
            });
        }
        movers.shutdown();
        boolean finished = movers.awaitTermination(10, TimeUnit.SECONDS);
        Money pairAfter = ledger.balance("alice").plus(ledger.balance("bob"));
        System.out.println("  finished without deadlock=" + finished
                + ", alice+bob before=" + pairBefore + " after=" + pairAfter);

        System.out.println("\n--- 11. Ledger invariants ---");
        System.out.println("  journal entries=" + ledger.journalSize()
                + ", sum of postings=" + ledger.journalSum("USD")
                + ", sum of balances=" + ledger.balanceSum("USD"));

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }
}
