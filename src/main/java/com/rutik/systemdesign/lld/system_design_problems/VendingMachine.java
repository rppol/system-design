package com.rutik.systemdesign.lld.system_design_problems; /**
 * VENDING MACHINE - State Pattern Deep Dive
 *
 * A vending machine is the classic State pattern example because:
 * - The machine has distinct, well-defined states
 * - The same actions (insertCoin, select, dispense) behave differently in each state
 * - Without State pattern: massive if-else / switch chains that are hard to extend
 *
 * States:
 *   IdleState           → machine is waiting, no money inserted
 *   HasMoneyState       → money inserted, waiting for selection
 *   ProductSelectedState→ product selected, ready to dispense
 *   DispensingState     → currently dispensing product
 *   OutOfStockState     → selected item is out of stock
 *
 * State Transitions:
 *   Idle ──insertCoin──► HasMoney
 *   HasMoney ──selectProduct──► ProductSelected (if in stock)
 *   HasMoney ──refund──► Idle
 *   ProductSelected ──dispense──► Dispensing ──done──► Idle
 *   ProductSelected ──cancel──► HasMoney (money returned minus fee)
 *   * ──outOfStock──► OutOfStock ──restock──► Idle
 */

import java.util.HashMap;
import java.util.Map;

// ─────────────────────────────────────────────────────────────
// Product and Inventory
// ─────────────────────────────────────────────────────────────

class Item {
    private final String code;
    private final String name;
    private final double price;

    public Item(String code, String name, double price) {
        this.code = code;
        this.name = name;
        this.price = price;
    }

    public String getCode()  { return code; }
    public String getName()  { return name; }
    public double getPrice() { return price; }

    @Override
    public String toString() {
        return String.format("[%s] %s - $%.2f", code, name, price);
    }
}

class Inventory {
    private final Map<String, Item> items = new HashMap<>();
    private final Map<String, Integer> quantities = new HashMap<>();

    public void addItem(Item item, int quantity) {
        items.put(item.getCode(), item);
        quantities.put(item.getCode(), quantity);
    }

    public boolean isAvailable(String code) {
        return items.containsKey(code) && quantities.getOrDefault(code, 0) > 0;
    }

    public Item getItem(String code) {
        return items.get(code);
    }

    public void dispense(String code) {
        int qty = quantities.getOrDefault(code, 0);
        if (qty <= 0) throw new IllegalStateException("Item out of stock: " + code);
        quantities.put(code, qty - 1);
    }

    public void restock(String code, int quantity) {
        quantities.merge(code, quantity, Integer::sum);
    }

    public void displayMenu() {
        System.out.println("  ┌─────────────────────────────────┐");
        System.out.println("  │         AVAILABLE ITEMS          │");
        System.out.println("  ├─────────────────────────────────┤");
        items.forEach((code, item) -> {
            int qty = quantities.getOrDefault(code, 0);
            String status = qty > 0 ? "qty=" + qty : "OUT OF STOCK";
            System.out.printf("  │ %-32s│%n",
                    String.format("[%s] %-12s $%.2f (%s)", code, item.getName(), item.getPrice(), status));
        });
        System.out.println("  └─────────────────────────────────┘");
    }
}

// ─────────────────────────────────────────────────────────────
// State Interface
// ─────────────────────────────────────────────────────────────

interface VendingMachineState {
    void insertCoin(double amount);
    void selectProduct(String code);
    void dispense();
    void refund();
    String getStateName();
}

// ─────────────────────────────────────────────────────────────
// VendingMachine (Context)
// ─────────────────────────────────────────────────────────────

class VendingMachine {
    private VendingMachineState currentState;
    private double balance;
    private Item selectedItem;
    private final Inventory inventory;

    // State instances (flyweight — reuse same state objects)
    final VendingMachineState idleState;
    final VendingMachineState hasMoneyState;
    final VendingMachineState productSelectedState;
    final VendingMachineState dispensingState;
    final VendingMachineState outOfStockState;

    public VendingMachine(Inventory inventory) {
        this.inventory = inventory;
        this.balance = 0.0;
        this.selectedItem = null;

        idleState            = new IdleState(this);
        hasMoneyState        = new HasMoneyState(this);
        productSelectedState = new ProductSelectedState(this);
        dispensingState      = new DispensingState(this);
        outOfStockState      = new OutOfStockState(this);

        currentState = idleState;
    }

    // ── Delegate all actions to current state ──
    public void insertCoin(double amount)    { currentState.insertCoin(amount); }
    public void selectProduct(String code)   { currentState.selectProduct(code); }
    public void dispense()                   { currentState.dispense(); }
    public void refund()                     { currentState.refund(); }

    // ── State transitions ──
    public void setState(VendingMachineState state) {
        System.out.println("  [STATE] " + currentState.getStateName() + " → " + state.getStateName());
        currentState = state;
    }

    // ── Accessors for states ──
    public double getBalance()             { return balance; }
    public void addBalance(double amount)  { balance += amount; }
    public void deductBalance(double amt)  { balance -= amt; }
    public void resetBalance()             { balance = 0.0; }

    public Item getSelectedItem()          { return selectedItem; }
    public void setSelectedItem(Item item) { selectedItem = item; }
    public void clearSelectedItem()        { selectedItem = null; }

    public Inventory getInventory()        { return inventory; }

    public void displayStatus() {
        System.out.printf("  [Machine] State: %-20s Balance: $%.2f%n",
                currentState.getStateName(), balance);
    }
}

// ─────────────────────────────────────────────────────────────
// Concrete States
// ─────────────────────────────────────────────────────────────

class IdleState implements VendingMachineState {
    private final VendingMachine machine;

    public IdleState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(double amount) {
        if (amount <= 0) {
            System.out.println("  [IDLE] Invalid amount. Please insert a positive amount.");
            return;
        }
        machine.addBalance(amount);
        System.out.printf("  [IDLE] Coin inserted: $%.2f. Total balance: $%.2f%n",
                amount, machine.getBalance());
        machine.setState(machine.hasMoneyState);
    }

    @Override
    public void selectProduct(String code) {
        System.out.println("  [IDLE] Please insert coins first.");
    }

    @Override
    public void dispense() {
        System.out.println("  [IDLE] No product selected and no money inserted.");
    }

    @Override
    public void refund() {
        System.out.println("  [IDLE] No money to refund.");
    }

    @Override public String getStateName() { return "IDLE"; }
}

class HasMoneyState implements VendingMachineState {
    private final VendingMachine machine;

    public HasMoneyState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(double amount) {
        machine.addBalance(amount);
        System.out.printf("  [HAS_MONEY] Added $%.2f. Total balance: $%.2f%n",
                amount, machine.getBalance());
    }

    @Override
    public void selectProduct(String code) {
        Inventory inv = machine.getInventory();
        if (!inv.isAvailable(code)) {
            System.out.println("  [HAS_MONEY] Item '" + code + "' is out of stock.");
            machine.setState(machine.outOfStockState);
            return;
        }
        Item item = inv.getItem(code);
        if (machine.getBalance() < item.getPrice()) {
            System.out.printf("  [HAS_MONEY] Insufficient balance. Need $%.2f more.%n",
                    item.getPrice() - machine.getBalance());
            return;
        }
        machine.setSelectedItem(item);
        System.out.println("  [HAS_MONEY] Selected: " + item.getName());
        machine.setState(machine.productSelectedState);
    }

    @Override
    public void dispense() {
        System.out.println("  [HAS_MONEY] Please select a product first.");
    }

    @Override
    public void refund() {
        double refundAmount = machine.getBalance();
        machine.resetBalance();
        System.out.printf("  [HAS_MONEY] Refunded $%.2f. Thank you!%n", refundAmount);
        machine.setState(machine.idleState);
    }

    @Override public String getStateName() { return "HAS_MONEY"; }
}

class ProductSelectedState implements VendingMachineState {
    private final VendingMachine machine;

    public ProductSelectedState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(double amount) {
        System.out.println("  [SELECTED] Product already selected. Press dispense or cancel.");
    }

    @Override
    public void selectProduct(String code) {
        System.out.println("  [SELECTED] Product already selected. Press dispense to continue.");
    }

    @Override
    public void dispense() {
        machine.setState(machine.dispensingState);
        machine.dispense();
    }

    @Override
    public void refund() {
        double refund = machine.getBalance();
        machine.resetBalance();
        machine.clearSelectedItem();
        System.out.printf("  [SELECTED] Cancelled. Refunded $%.2f%n", refund);
        machine.setState(machine.idleState);
    }

    @Override public String getStateName() { return "PRODUCT_SELECTED"; }
}

class DispensingState implements VendingMachineState {
    private final VendingMachine machine;

    public DispensingState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(double amount) {
        System.out.println("  [DISPENSING] Please wait, dispensing in progress...");
    }

    @Override
    public void selectProduct(String code) {
        System.out.println("  [DISPENSING] Please wait, dispensing in progress...");
    }

    @Override
    public void dispense() {
        Item item = machine.getSelectedItem();
        machine.getInventory().dispense(item.getCode());
        machine.deductBalance(item.getPrice());

        System.out.println("  [DISPENSING] >>> Dispensing: " + item.getName() + " <<<");

        double change = machine.getBalance();
        if (change > 0) {
            System.out.printf("  [DISPENSING] Returning change: $%.2f%n", change);
        }

        machine.resetBalance();
        machine.clearSelectedItem();
        machine.setState(machine.idleState);
        System.out.println("  [DISPENSING] Thank you! Enjoy your " + item.getName() + "!");
    }

    @Override
    public void refund() {
        System.out.println("  [DISPENSING] Cannot refund while dispensing.");
    }

    @Override public String getStateName() { return "DISPENSING"; }
}

class OutOfStockState implements VendingMachineState {
    private final VendingMachine machine;

    public OutOfStockState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(double amount) {
        System.out.println("  [OUT_OF_STOCK] Machine is out of stock. Please refund.");
    }

    @Override
    public void selectProduct(String code) {
        System.out.println("  [OUT_OF_STOCK] Cannot select — machine out of stock.");
    }

    @Override
    public void dispense() {
        System.out.println("  [OUT_OF_STOCK] Cannot dispense — machine out of stock.");
    }

    @Override
    public void refund() {
        double refund = machine.getBalance();
        if (refund > 0) {
            machine.resetBalance();
            System.out.printf("  [OUT_OF_STOCK] Refunded $%.2f%n", refund);
        }
        machine.setState(machine.idleState);
    }

    @Override public String getStateName() { return "OUT_OF_STOCK"; }
}

// ─────────────────────────────────────────────────────────────
// Demo
// ─────────────────────────────────────────────────────────────

class VendingMachineDemo {

    public static void main(String[] args) {
        // Setup inventory
        Inventory inventory = new Inventory();
        inventory.addItem(new Item("A1", "Coke",    1.50), 2);
        inventory.addItem(new Item("B1", "Chips",   1.00), 1);
        inventory.addItem(new Item("C1", "Water",   0.75), 3);
        inventory.addItem(new Item("D1", "Candy",   0.50), 0); // out of stock

        VendingMachine machine = new VendingMachine(inventory);

        System.out.println("═══════════════════════════════════════");
        System.out.println("  VENDING MACHINE DEMO");
        System.out.println("═══════════════════════════════════════");
        inventory.displayMenu();

        // Scenario 1: Normal purchase with change
        System.out.println("\n--- Scenario 1: Normal Purchase ---");
        machine.insertCoin(1.00);
        machine.insertCoin(1.00);
        machine.selectProduct("A1");  // Coke $1.50
        machine.dispense();           // get $0.50 change
        machine.displayStatus();

        // Scenario 2: Insufficient money
        System.out.println("\n--- Scenario 2: Not Enough Money ---");
        machine.insertCoin(0.50);
        machine.selectProduct("A1");  // Coke $1.50 — not enough
        machine.insertCoin(0.75);     // add more
        machine.selectProduct("A1");  // now enough
        machine.dispense();

        // Scenario 3: Refund before selection
        System.out.println("\n--- Scenario 3: Refund ---");
        machine.insertCoin(2.00);
        machine.refund();
        machine.displayStatus();

        // Scenario 4: Out of stock item
        System.out.println("\n--- Scenario 4: Out of Stock ---");
        machine.insertCoin(1.00);
        machine.selectProduct("D1");  // Candy — out of stock
        machine.refund();             // get money back from OutOfStockState

        // Scenario 5: Invalid action in IDLE
        System.out.println("\n--- Scenario 5: Invalid Actions ---");
        machine.selectProduct("C1"); // can't select without money
        machine.dispense();          // can't dispense in IDLE
        machine.refund();            // nothing to refund
    }
}
