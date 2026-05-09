package com.rutik.systemdesign.lld.behavioral.state;

/**
 * STATE PATTERN - Real-World Example: Vending Machine
 *
 * A vending machine moves through four distinct states:
 *
 *   NoCoinState   -- waiting for a coin to be inserted
 *   HasCoinState  -- coin inserted; waiting for product selection
 *   DispensingState -- dispensing the selected product
 *   SoldOutState  -- no inventory remaining
 *
 * State transitions:
 *
 *   NoCoin  --[insertCoin]-->  HasCoin
 *   HasCoin --[ejectCoin]-->   NoCoin
 *   HasCoin --[selectProduct]-->  Dispensing
 *   Dispensing --[dispense]--> NoCoin  (if stock > 0)
 *   Dispensing --[dispense]--> SoldOut (if stock == 0)
 *   Any     --[insertCoin in SoldOut]--> prints "sold out" message
 */

// ---------------------------------------------------------------------------
// VendingMachineState interface
// ---------------------------------------------------------------------------
interface VendingMachineState {
    void insertCoin(VendingMachine machine);
    void ejectCoin(VendingMachine machine);
    void selectProduct(VendingMachine machine);
    void dispense(VendingMachine machine);
}

// ---------------------------------------------------------------------------
// NoCoinState: waiting for a coin
// ---------------------------------------------------------------------------
class NoCoinState implements VendingMachineState {

    @Override
    public void insertCoin(VendingMachine machine) {
        System.out.println("[NoCoinState] Coin inserted. Ready for product selection.");
        machine.setState(machine.getHasCoinState());
    }

    @Override
    public void ejectCoin(VendingMachine machine) {
        System.out.println("[NoCoinState] No coin to eject.");
    }

    @Override
    public void selectProduct(VendingMachine machine) {
        System.out.println("[NoCoinState] Please insert a coin first.");
    }

    @Override
    public void dispense(VendingMachine machine) {
        System.out.println("[NoCoinState] Cannot dispense without a coin.");
    }
}

// ---------------------------------------------------------------------------
// HasCoinState: coin inserted, waiting for selection
// ---------------------------------------------------------------------------
class HasCoinState implements VendingMachineState {

    @Override
    public void insertCoin(VendingMachine machine) {
        System.out.println("[HasCoinState] Coin already inserted. Returning extra coin.");
    }

    @Override
    public void ejectCoin(VendingMachine machine) {
        System.out.println("[HasCoinState] Coin ejected. Returning to idle.");
        machine.setState(machine.getNoCoinState());
    }

    @Override
    public void selectProduct(VendingMachine machine) {
        System.out.println("[HasCoinState] Product selected. Dispensing...");
        machine.setState(machine.getDispensingState());
    }

    @Override
    public void dispense(VendingMachine machine) {
        System.out.println("[HasCoinState] Select a product before dispensing.");
    }
}

// ---------------------------------------------------------------------------
// DispensingState: actively dispensing the product
// ---------------------------------------------------------------------------
class DispensingState implements VendingMachineState {

    @Override
    public void insertCoin(VendingMachine machine) {
        System.out.println("[DispensingState] Please wait — dispensing in progress.");
    }

    @Override
    public void ejectCoin(VendingMachine machine) {
        System.out.println("[DispensingState] Cannot eject coin during dispensing.");
    }

    @Override
    public void selectProduct(VendingMachine machine) {
        System.out.println("[DispensingState] Already dispensing a product.");
    }

    @Override
    public void dispense(VendingMachine machine) {
        machine.releaseProduct(); // physically releases the product
        if (machine.getStock() > 0) {
            System.out.println("[DispensingState] Dispensed! Returning to idle.");
            machine.setState(machine.getNoCoinState());
        } else {
            System.out.println("[DispensingState] Dispensed last item! Machine is now sold out.");
            machine.setState(machine.getSoldOutState());
        }
    }
}

// ---------------------------------------------------------------------------
// SoldOutState: inventory empty
// ---------------------------------------------------------------------------
class SoldOutState implements VendingMachineState {

    @Override
    public void insertCoin(VendingMachine machine) {
        System.out.println("[SoldOutState] Sorry, the machine is sold out. Returning coin.");
    }

    @Override
    public void ejectCoin(VendingMachine machine) {
        System.out.println("[SoldOutState] No coin inserted.");
    }

    @Override
    public void selectProduct(VendingMachine machine) {
        System.out.println("[SoldOutState] No products available.");
    }

    @Override
    public void dispense(VendingMachine machine) {
        System.out.println("[SoldOutState] Cannot dispense — sold out.");
    }
}

// ---------------------------------------------------------------------------
// VendingMachine (Context)
//   - Owns the state objects (created once and reused).
//   - Delegates every user action to the current state.
//   - Exposes state references so states can trigger transitions.
// ---------------------------------------------------------------------------
class VendingMachine {

    // Pre-created state singletons (avoids allocating on every transition)
    private final VendingMachineState noCoinState    = new NoCoinState();
    private final VendingMachineState hasCoinState   = new HasCoinState();
    private final VendingMachineState dispensingState = new DispensingState();
    private final VendingMachineState soldOutState   = new SoldOutState();

    private VendingMachineState currentState;
    private int stock;

    public VendingMachine(int initialStock) {
        this.stock = initialStock;
        // Start in the appropriate state depending on stock level
        this.currentState = (initialStock > 0) ? noCoinState : soldOutState;
    }

    // ---- User-facing actions (delegated to current state) ------------------

    public void insertCoin()    { currentState.insertCoin(this); }
    public void ejectCoin()     { currentState.ejectCoin(this); }
    public void selectProduct() { currentState.selectProduct(this); }
    public void dispense()      { currentState.dispense(this); }

    // ---- Called by DispensingState to actually release the product ----------

    public void releaseProduct() {
        if (stock > 0) {
            stock--;
            System.out.println("  ** Product released. Remaining stock: " + stock + " **");
        }
    }

    // ---- State accessors (used by state objects for transitions) -----------

    public void setState(VendingMachineState state) { this.currentState = state; }

    public VendingMachineState getNoCoinState()     { return noCoinState; }
    public VendingMachineState getHasCoinState()    { return hasCoinState; }
    public VendingMachineState getDispensingState() { return dispensingState; }
    public VendingMachineState getSoldOutState()    { return soldOutState; }

    public int getStock()                           { return stock; }

    public String getCurrentStateName() {
        return currentState.getClass().getSimpleName();
    }
}

// ---------------------------------------------------------------------------
// Main / Demo
// ---------------------------------------------------------------------------
public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("=== Vending Machine Demo (2 items in stock) ===\n");
        VendingMachine machine = new VendingMachine(2);
        printState(machine);

        // --- Normal purchase flow (item 1) ---
        separator("Purchase #1: normal flow");
        machine.insertCoin();
        machine.selectProduct();
        machine.dispense();
        printState(machine);

        // --- Try to dispense without a coin ---
        separator("Edge case: select without coin");
        machine.selectProduct();
        printState(machine);

        // --- Normal purchase flow (item 2 — last item) ---
        separator("Purchase #2: last item");
        machine.insertCoin();
        machine.selectProduct();
        machine.dispense();
        printState(machine);

        // --- Try to buy when sold out ---
        separator("Edge case: buy when sold out");
        machine.insertCoin();
        machine.selectProduct();
        machine.dispense();
        printState(machine);

        // --- Eject coin mid-selection ---
        separator("Edge case: eject coin after inserting");
        VendingMachine machine2 = new VendingMachine(5);
        machine2.insertCoin();
        machine2.ejectCoin();
        printState(machine2);
    }

    private static void separator(String label) {
        System.out.println("\n--- " + label + " ---");
    }

    private static void printState(VendingMachine m) {
        System.out.println("  [State: " + m.getCurrentStateName()
                + " | Stock: " + m.getStock() + "]");
    }
}
