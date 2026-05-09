package com.rutik.systemdesign.lld.system_design_problems;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// ─────────────────────────────────────────────
// ENUMS
// ─────────────────────────────────────────────

enum ATMState {
    IDLE, CARD_INSERTED, PIN_VERIFIED, TRANSACTION, OUT_OF_CASH
}

enum TransactionType {
    WITHDRAWAL, DEPOSIT, BALANCE_INQUIRY, TRANSFER
}

// ─────────────────────────────────────────────
// DOMAIN: BankAccount, Cash, Receipt
// ─────────────────────────────────────────────

class BankAccount {
    private final String accountNumber;
    private final int pin;
    private double balance;

    public BankAccount(String accountNumber, int pin, double initialBalance) {
        this.accountNumber = accountNumber;
        this.pin = pin;
        this.balance = initialBalance;
    }

    /** PIN never logged — only a boolean result is returned to the caller. */
    public boolean verifyPIN(int inputPin) {
        return this.pin == inputPin;
    }

    public String getAccountNumber() { return accountNumber; }
    public double getBalance()       { return balance; }

    public void debit(double amount) {
        if (amount > balance) throw new IllegalStateException("Insufficient funds");
        balance -= amount;
    }

    public void credit(double amount) { balance += amount; }

    @Override
    public String toString() {
        return "BankAccount[" + accountNumber + ", balance=" + balance + "]";
    }
}

/**
 * Represents the physical cash cassette inside the ATM.
 * Supports denomination-aware dispensing (greedy algorithm).
 */
class Cash {
    // Higher denominations first
    private final TreeMap<Integer, Integer> denominations =
            new TreeMap<>(Collections.reverseOrder());
    private double totalAmount;

    public Cash(Map<Integer, Integer> denominations) {
        this.denominations.putAll(denominations);
        this.totalAmount = denominations.entrySet().stream()
                .mapToDouble(e -> (double) e.getKey() * e.getValue()).sum();
    }

    public double getTotalAmount() { return totalAmount; }

    public boolean canDispense(double amount) {
        if (totalAmount < amount) return false;
        double remaining = amount;
        for (Map.Entry<Integer, Integer> e : denominations.entrySet()) {
            int denom = e.getKey();
            int count = e.getValue();
            int use   = (int) Math.min(remaining / denom, count);
            remaining -= (double) use * denom;
        }
        return remaining == 0;
    }

    /** Dispenses exact amount; throws if not possible. */
    public Map<Integer, Integer> dispense(double amount) {
        if (!canDispense(amount))
            throw new IllegalStateException("Cannot dispense $" + amount);
        Map<Integer, Integer> dispensed = new LinkedHashMap<>();
        double remaining = amount;
        for (Map.Entry<Integer, Integer> e : denominations.entrySet()) {
            int denom = e.getKey();
            int count = e.getValue();
            int use   = (int) Math.min(remaining / denom, count);
            if (use > 0) {
                dispensed.put(denom, use);
                denominations.put(denom, count - use);
                remaining   -= (double) use * denom;
                totalAmount -= (double) use * denom;
            }
        }
        return dispensed;
    }

    public void addCash(Map<Integer, Integer> extra) {
        extra.forEach((denom, cnt) -> {
            denominations.merge(denom, cnt, Integer::sum);
            totalAmount += (double) denom * cnt;
        });
    }

    @Override
    public String toString() {
        return "Cash{total=$" + totalAmount + ", notes=" + denominations + "}";
    }
}

/** Immutable receipt printed at end of a transaction. */
class Receipt {
    private final String transactionId;
    private final TransactionType type;
    private final double amount;
    private final double balanceAfter;
    private final LocalDateTime timestamp;
    private final String maskedAccount; // only last 4 digits

    public Receipt(String transactionId, TransactionType type, double amount,
                   double balanceAfter, String accountNumber) {
        this.transactionId  = transactionId;
        this.type           = type;
        this.amount         = amount;
        this.balanceAfter   = balanceAfter;
        this.timestamp      = LocalDateTime.now();
        int len = accountNumber.length();
        this.maskedAccount  = "****" + accountNumber.substring(Math.max(0, len - 4));
    }

    public void print() {
        String sep = "─".repeat(40);
        System.out.println(sep);
        System.out.println("         TRANSACTION RECEIPT");
        System.out.println(sep);
        System.out.printf("  Date   : %s%n",
                timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.printf("  TxnID  : %s%n", transactionId);
        System.out.printf("  Account: %s%n", maskedAccount);
        System.out.printf("  Type   : %s%n", type);
        System.out.printf("  Amount : $%.2f%n", amount);
        System.out.printf("  Balance: $%.2f%n", balanceAfter);
        System.out.println(sep);
    }

    @Override
    public String toString() {
        return "Receipt[" + transactionId + ", " + type + ", $" + amount + "]";
    }
}

// ─────────────────────────────────────────────
// COMMAND PATTERN — Transactions with Rollback
// ─────────────────────────────────────────────

/**
 * Command interface.
 * execute()  — performs the transaction.
 * rollback() — reverses it (used for error recovery / audit correction).
 */
interface Transaction {
    void execute();
    void rollback();
    Receipt getReceipt();
    String getTransactionId();
}

// ── ConcreteCommand: Withdrawal ────────────────

class WithdrawalTransaction implements Transaction {
    private final String transactionId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private final BankAccount account;
    private final Cash atm;
    private final double amount;
    private Receipt receipt;

    public WithdrawalTransaction(BankAccount account, Cash atm, double amount) {
        this.account = account;
        this.atm     = atm;
        this.amount  = amount;
    }

    @Override
    public void execute() {
        if (!atm.canDispense(amount))
            throw new IllegalStateException("ATM cannot dispense $" + amount);
        account.debit(amount);
        Map<Integer, Integer> dispensed = atm.dispense(amount);
        receipt = new Receipt(transactionId, TransactionType.WITHDRAWAL,
                amount, account.getBalance(), account.getAccountNumber());
        System.out.println("[ATM] Dispensing notes: " + dispensed);
    }

    @Override
    public void rollback() {
        // Re-credit the account (physical cash recovery is handled separately in real systems)
        account.credit(amount);
        System.out.println("[Rollback] Withdrawal of $" + amount + " reversed for " +
                account.getAccountNumber());
    }

    @Override public Receipt getReceipt()       { return receipt; }
    @Override public String getTransactionId()  { return transactionId; }
}

// ── ConcreteCommand: Deposit ───────────────────

class DepositTransaction implements Transaction {
    private final String transactionId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private final BankAccount account;
    private final Cash atm;
    private final double amount;
    private final Map<Integer, Integer> depositedNotes;
    private Receipt receipt;

    public DepositTransaction(BankAccount account, Cash atm, double amount,
                               Map<Integer, Integer> depositedNotes) {
        this.account        = account;
        this.atm            = atm;
        this.amount         = amount;
        this.depositedNotes = depositedNotes;
    }

    @Override
    public void execute() {
        account.credit(amount);
        atm.addCash(depositedNotes);
        receipt = new Receipt(transactionId, TransactionType.DEPOSIT,
                amount, account.getBalance(), account.getAccountNumber());
        System.out.println("[ATM] Deposit of $" + amount + " accepted.");
    }

    @Override
    public void rollback() {
        account.debit(amount);
        System.out.println("[Rollback] Deposit of $" + amount + " reversed for " +
                account.getAccountNumber());
    }

    @Override public Receipt getReceipt()      { return receipt; }
    @Override public String getTransactionId() { return transactionId; }
}

// ── ConcreteCommand: Balance Inquiry ──────────

class BalanceInquiryTransaction implements Transaction {
    private final String transactionId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private final BankAccount account;
    private Receipt receipt;

    public BalanceInquiryTransaction(BankAccount account) {
        this.account = account;
    }

    @Override
    public void execute() {
        receipt = new Receipt(transactionId, TransactionType.BALANCE_INQUIRY,
                0, account.getBalance(), account.getAccountNumber());
        System.out.printf("[ATM] Balance: $%.2f%n", account.getBalance());
    }

    @Override
    public void rollback() {
        System.out.println("[Rollback] Balance inquiry is read-only — nothing to undo.");
    }

    @Override public Receipt getReceipt()      { return receipt; }
    @Override public String getTransactionId() { return transactionId; }
}

// ── ConcreteCommand: Transfer ─────────────────

class TransferTransaction implements Transaction {
    private final String transactionId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private final BankAccount source;
    private final BankAccount destination;
    private final double amount;
    private Receipt receipt;

    public TransferTransaction(BankAccount source, BankAccount destination, double amount) {
        this.source      = source;
        this.destination = destination;
        this.amount      = amount;
    }

    @Override
    public void execute() {
        source.debit(amount);
        destination.credit(amount);
        receipt = new Receipt(transactionId, TransactionType.TRANSFER,
                amount, source.getBalance(), source.getAccountNumber());
        System.out.printf("[ATM] Transferred $%.2f → %s%n", amount, destination.getAccountNumber());
    }

    @Override
    public void rollback() {
        destination.debit(amount);
        source.credit(amount);
        System.out.println("[Rollback] Transfer of $" + amount + " reversed.");
    }

    @Override public Receipt getReceipt()      { return receipt; }
    @Override public String getTransactionId() { return transactionId; }
}

/**
 * Keeps an append-only log of executed transactions.
 * Supports single-step rollback (undo last) for demo purposes.
 */
class TransactionHistory {
    private final List<Transaction> log = new ArrayList<>();

    public void record(Transaction t) { log.add(t); }

    public void rollbackLast() {
        if (log.isEmpty()) { System.out.println("[History] Nothing to roll back."); return; }
        Transaction last = log.remove(log.size() - 1);
        last.rollback();
    }

    public void printAll() {
        System.out.println("=== Transaction History ===");
        if (log.isEmpty()) { System.out.println("  (empty)"); return; }
        log.forEach(t -> System.out.println("  " + t.getReceipt()));
    }
}

// ─────────────────────────────────────────────
// STATE PATTERN — ATM State Handlers
// ─────────────────────────────────────────────

/**
 * State interface: each concrete state implements exactly which actions
 * are valid in that phase, and drives transitions on the ATMContext.
 */
interface ATMStateHandler {
    void insertCard(ATMContext ctx, BankAccount account);
    void enterPIN(ATMContext ctx, int pin);
    void selectTransaction(ATMContext ctx);
    void processTransaction(ATMContext ctx, Transaction txn);
    void ejectCard(ATMContext ctx);
}

// ── IdleState ──────────────────────────────────

class IdleState implements ATMStateHandler {
    @Override
    public void insertCard(ATMContext ctx, BankAccount account) {
        System.out.println("[Idle] Card inserted. Please enter your PIN.");
        ctx.setCurrentAccount(account);
        ctx.setState(new CardInsertedState());
    }
    @Override public void enterPIN(ATMContext ctx, int pin) {
        System.out.println("[Idle] Insert a card first."); }
    @Override public void selectTransaction(ATMContext ctx) {
        System.out.println("[Idle] Insert a card first."); }
    @Override public void processTransaction(ATMContext ctx, Transaction txn) {
        System.out.println("[Idle] No active session."); }
    @Override public void ejectCard(ATMContext ctx) {
        System.out.println("[Idle] No card to eject."); }
}

// ── CardInsertedState ──────────────────────────

class CardInsertedState implements ATMStateHandler {
    private int failedAttempts = 0;
    private static final int MAX_ATTEMPTS = 3;

    @Override public void insertCard(ATMContext ctx, BankAccount account) {
        System.out.println("[CardInserted] Card already in slot."); }

    @Override
    public void enterPIN(ATMContext ctx, int pin) {
        // Security: never log the PIN value itself
        if (ctx.getCurrentAccount().verifyPIN(pin)) {
            System.out.println("[CardInserted] PIN verified.");
            ctx.setState(new PINVerifiedState());
        } else {
            failedAttempts++;
            System.out.printf("[CardInserted] Wrong PIN (%d/%d attempts).%n",
                    failedAttempts, MAX_ATTEMPTS);
            if (failedAttempts >= MAX_ATTEMPTS) {
                System.out.println("[CardInserted] Card blocked — too many wrong PINs.");
                ctx.setCurrentAccount(null);
                ctx.setState(new IdleState());
            }
        }
    }

    @Override public void selectTransaction(ATMContext ctx) {
        System.out.println("[CardInserted] Enter PIN first."); }
    @Override public void processTransaction(ATMContext ctx, Transaction txn) {
        System.out.println("[CardInserted] Enter PIN first."); }

    @Override
    public void ejectCard(ATMContext ctx) {
        System.out.println("[CardInserted] Card ejected.");
        ctx.setCurrentAccount(null);
        ctx.setState(new IdleState());
    }
}

// ── PINVerifiedState ───────────────────────────

class PINVerifiedState implements ATMStateHandler {
    @Override public void insertCard(ATMContext ctx, BankAccount account) {
        System.out.println("[PINVerified] Session active."); }
    @Override public void enterPIN(ATMContext ctx, int pin) {
        System.out.println("[PINVerified] PIN already verified."); }

    @Override
    public void selectTransaction(ATMContext ctx) {
        System.out.println("[PINVerified] Transaction menu open.");
        ctx.setState(new TransactionState());
    }

    @Override public void processTransaction(ATMContext ctx, Transaction txn) {
        System.out.println("[PINVerified] Select a transaction first."); }

    @Override
    public void ejectCard(ATMContext ctx) {
        System.out.println("[PINVerified] Card ejected.");
        ctx.setCurrentAccount(null);
        ctx.setState(new IdleState());
    }
}

// ── TransactionState ───────────────────────────

class TransactionState implements ATMStateHandler {
    @Override public void insertCard(ATMContext ctx, BankAccount account) {
        System.out.println("[Transaction] Session in progress."); }
    @Override public void enterPIN(ATMContext ctx, int pin) {
        System.out.println("[Transaction] PIN already verified."); }
    @Override public void selectTransaction(ATMContext ctx) {
        System.out.println("[Transaction] Already in transaction."); }

    @Override
    public void processTransaction(ATMContext ctx, Transaction txn) {
        try {
            txn.execute();
            ctx.getHistory().record(txn);
            if (txn.getReceipt() != null) txn.getReceipt().print();

            // Transition: out of cash or back to menu
            if (ctx.getCash().getTotalAmount() == 0) {
                System.out.println("[Transaction] ATM is now out of cash.");
                ctx.setState(new OutOfCashState());
            } else {
                ctx.setState(new PINVerifiedState());
            }
        } catch (IllegalStateException ex) {
            System.out.println("[Transaction] FAILED: " + ex.getMessage());
            ctx.setState(new PINVerifiedState());
        }
    }

    @Override
    public void ejectCard(ATMContext ctx) {
        System.out.println("[Transaction] Cancelled. Card ejected.");
        ctx.setCurrentAccount(null);
        ctx.setState(new IdleState());
    }
}

// ── OutOfCashState ────────────────────────────

class OutOfCashState implements ATMStateHandler {
    @Override public void insertCard(ATMContext ctx, BankAccount account) {
        System.out.println("[OutOfCash] ATM out of service."); }
    @Override public void enterPIN(ATMContext ctx, int pin) {
        System.out.println("[OutOfCash] ATM out of service."); }
    @Override public void selectTransaction(ATMContext ctx) {
        System.out.println("[OutOfCash] ATM out of service."); }
    @Override public void processTransaction(ATMContext ctx, Transaction txn) {
        System.out.println("[OutOfCash] ATM out of service — no withdrawals possible."); }
    @Override public void ejectCard(ATMContext ctx) {
        System.out.println("[OutOfCash] No card inserted."); }
}

// ─────────────────────────────────────────────
// ATM CONTEXT (renamed from ATM to avoid conflict with public class)
// ─────────────────────────────────────────────

/**
 * ATMContext is the State-pattern context.
 * It holds the current ATMStateHandler and delegates every user action to it.
 */
class ATMContext {
    private ATMStateHandler state;
    private BankAccount currentAccount;
    private final Cash cash;
    private final TransactionHistory history;
    private final String atmId;

    public ATMContext(String atmId, Cash initialCash) {
        this.atmId   = atmId;
        this.cash    = initialCash;
        this.history = new TransactionHistory();
        this.state   = new IdleState();
    }

    // ── Delegate all user actions to current state ─────────────

    public void insertCard(BankAccount acct)       { state.insertCard(this, acct); }
    public void enterPIN(int pin)                  { state.enterPIN(this, pin); }
    public void selectTransaction()                { state.selectTransaction(this); }
    public void processTransaction(Transaction t)  { state.processTransaction(this, t); }
    public void ejectCard()                        { state.ejectCard(this); }

    // ── Accessors used by state implementations ────────────────

    public void setState(ATMStateHandler s)          { this.state = s; }
    public ATMStateHandler getState()                { return state; }
    public void setCurrentAccount(BankAccount acct) { this.currentAccount = acct; }
    public BankAccount getCurrentAccount()           { return currentAccount; }
    public Cash getCash()                            { return cash; }
    public TransactionHistory getHistory()           { return history; }
    public String getAtmId()                         { return atmId; }
}

// ─────────────────────────────────────────────
// FACADE PATTERN — ATMFacade
// ─────────────────────────────────────────────

/**
 * ATMFacade hides the multi-step ATM protocol behind simple, intent-revealing methods.
 * Clients never have to manage state transitions manually.
 */
class ATMFacade {
    private final ATMContext atm;
    private final Map<String, BankAccount> accounts = new HashMap<>();

    public ATMFacade(ATMContext atm) { this.atm = atm; }

    public void registerAccount(BankAccount acct) {
        accounts.put(acct.getAccountNumber(), acct);
    }

    /** Full withdrawal flow in one call. */
    public void withdraw(String accountNum, int pin, double amount) {
        BankAccount acct = lookup(accountNum); if (acct == null) return;
        atm.insertCard(acct);
        atm.enterPIN(pin);
        atm.selectTransaction();
        atm.processTransaction(new WithdrawalTransaction(acct, atm.getCash(), amount));
        atm.ejectCard();
    }

    /** Full deposit flow in one call. */
    public void deposit(String accountNum, int pin, double amount,
                        Map<Integer, Integer> notes) {
        BankAccount acct = lookup(accountNum); if (acct == null) return;
        atm.insertCard(acct);
        atm.enterPIN(pin);
        atm.selectTransaction();
        atm.processTransaction(new DepositTransaction(acct, atm.getCash(), amount, notes));
        atm.ejectCard();
    }

    /** Balance check in one call. */
    public void checkBalance(String accountNum, int pin) {
        BankAccount acct = lookup(accountNum); if (acct == null) return;
        atm.insertCard(acct);
        atm.enterPIN(pin);
        atm.selectTransaction();
        atm.processTransaction(new BalanceInquiryTransaction(acct));
        atm.ejectCard();
    }

    /** Fund transfer in one call. */
    public void transfer(String fromNum, int pin, String toNum, double amount) {
        BankAccount from = lookup(fromNum); if (from == null) return;
        BankAccount to   = lookup(toNum);   if (to   == null) return;
        atm.insertCard(from);
        atm.enterPIN(pin);
        atm.selectTransaction();
        atm.processTransaction(new TransferTransaction(from, to, amount));
        atm.ejectCard();
    }

    public void rollbackLastTransaction() { atm.getHistory().rollbackLast(); }
    public void printHistory()            { atm.getHistory().printAll(); }

    private BankAccount lookup(String num) {
        BankAccount a = accounts.get(num);
        if (a == null) System.out.println("[Facade] Account not found: " + num);
        return a;
    }
}

// ─────────────────────────────────────────────
// MAIN / DEMO
// ─────────────────────────────────────────────

public class ATM {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║         ATM SYSTEM DEMO              ║");
        System.out.println("╚══════════════════════════════════════╝");

        // ── Bootstrap ATM with cash cassette ──────────────────
        Map<Integer, Integer> notes = new LinkedHashMap<>();
        notes.put(100, 10);  // $1,000
        notes.put(50,  20);  // $1,000
        notes.put(20,  30);  // $600
        notes.put(10,  40);  // $400
        ATMContext atm = new ATMContext("ATM-001", new Cash(notes));
        ATMFacade  facade = new ATMFacade(atm);

        BankAccount alice = new BankAccount("ACC1001", 1234, 5000.00);
        BankAccount bob   = new BankAccount("ACC1002", 5678, 2000.00);
        facade.registerAccount(alice);
        facade.registerAccount(bob);

        // ── Scenario 1: Successful withdrawal ─────────────────
        System.out.println("\n>>> Scenario 1: Alice withdraws $200");
        facade.withdraw("ACC1001", 1234, 200.00);
        System.out.printf("Alice balance: $%.2f%n", alice.getBalance());

        // ── Scenario 2: Balance inquiry ────────────────────────
        System.out.println("\n>>> Scenario 2: Alice checks balance");
        facade.checkBalance("ACC1001", 1234);

        // ── Scenario 3: Fund transfer ──────────────────────────
        System.out.println("\n>>> Scenario 3: Alice transfers $500 to Bob");
        facade.transfer("ACC1001", 1234, "ACC1002", 500.00);
        System.out.printf("Alice: $%.2f  Bob: $%.2f%n", alice.getBalance(), bob.getBalance());

        // ── Scenario 4: Wrong PIN — block after 3 failures ─────
        System.out.println("\n>>> Scenario 4: Wrong PIN three times");
        atm.insertCard(alice);
        atm.enterPIN(0000);
        atm.enterPIN(0000);
        atm.enterPIN(0000); // card blocked

        // ── Scenario 5: Invalid state — action in Idle ─────────
        System.out.println("\n>>> Scenario 5: Process transaction while Idle");
        atm.processTransaction(new BalanceInquiryTransaction(alice));

        // ── Scenario 6: Deposit ────────────────────────────────
        System.out.println("\n>>> Scenario 6: Bob deposits $300");
        Map<Integer, Integer> depositNotes = Map.of(100, 3);
        facade.deposit("ACC1002", 5678, 300.00, depositNotes);
        System.out.printf("Bob balance: $%.2f%n", bob.getBalance());

        // ── Scenario 7: Rollback last transaction ──────────────
        System.out.println("\n>>> Scenario 7: Rollback Bob's deposit");
        System.out.printf("Bob before rollback: $%.2f%n", bob.getBalance());
        facade.rollbackLastTransaction();
        System.out.printf("Bob after rollback : $%.2f%n", bob.getBalance());

        // ── History ────────────────────────────────────────────
        System.out.println("\n>>> Full Transaction History:");
        facade.printHistory();

        System.out.printf("%nATM cash remaining: $%.2f%n", atm.getCash().getTotalAmount());
    }
}
