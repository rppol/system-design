package com.rutik.systemdesign.lld.behavioral.memento; /**
 * Memento Pattern - Template
 *
 * Intent: Without violating encapsulation, capture and externalise an object's
 * internal state so that the object can be restored to that state later.
 *
 * Key participants:
 *  - Originator : The object whose state we want to save. Creates a Memento
 *                 containing a snapshot of its current state; can restore from one.
 *  - Memento    : Stores the internal state of the Originator. Only the Originator
 *                 can read the state; other objects treat it as opaque.
 *  - Caretaker  : Responsible for keeping the Memento safe; never modifies its contents.
 *
 * When to use:
 *  - A snapshot of an object's state must be saved so it can be restored later.
 *  - Direct interface to obtain the state would expose implementation details.
 */

import java.util.ArrayDeque;
import java.util.Deque;

// ─── Memento ──────────────────────────────────────────────────────────────────

/**
 * Stores a snapshot of the Originator's state.
 *
 * Access to the state is intentionally package-private / limited so external
 * code (including the Caretaker) cannot tamper with the stored values.
 */
final class Memento {
    // In a real application the state can be any serialisable value type
    private final String state;
    private final long   timestamp;

    // Constructor is package-private — only the Originator can create Mementos
    Memento(String state) {
        this.state     = state;
        this.timestamp = System.currentTimeMillis();
    }

    // Only the Originator should call getState(); treat this as narrow access
    String getState() { return state; }

    @Override
    public String toString() {
        return "Memento[state='" + state + "', ts=" + timestamp + "]";
    }
}

// ─── Originator ───────────────────────────────────────────────────────────────

/**
 * The object whose state we want to track.
 * It is the only class that creates and consumes Mementos.
 */
class Originator {
    private String state;

    public Originator(String initialState) {
        this.state = initialState;
        System.out.println("Originator: initial state = '" + state + "'");
    }

    /** Changes the internal state (simulates some operation). */
    public void setState(String newState) {
        System.out.println("Originator: state changed to '" + newState + "'");
        this.state = newState;
    }

    public String getState() { return state; }

    /** Captures the current state into a Memento. */
    public Memento save() {
        System.out.println("Originator: saving state '" + state + "'");
        return new Memento(state);
    }

    /** Restores state from a previously saved Memento. */
    public void restore(Memento memento) {
        this.state = memento.getState();
        System.out.println("Originator: restored to state '" + state + "'");
    }
}

// ─── Caretaker ────────────────────────────────────────────────────────────────

/**
 * Manages the history of Mementos (undo stack) without knowing anything about
 * the content of the state. It treats Mementos as opaque tokens.
 */
class Caretaker {
    private final Originator    originator;
    private final Deque<Memento> history = new ArrayDeque<>();

    public Caretaker(Originator originator) {
        this.originator = originator;
    }

    /** Saves the current state of the Originator. */
    public void backup() {
        history.push(originator.save());
    }

    /** Restores the most recently saved state. */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("Caretaker: no history to undo.");
            return;
        }
        Memento memento = history.pop();
        System.out.println("Caretaker: restoring " + memento);
        originator.restore(memento);
    }

    /** Prints the entire history stack (oldest to newest). */
    public void showHistory() {
        System.out.println("Caretaker: history (newest first):");
        if (history.isEmpty()) {
            System.out.println("  (empty)");
            return;
        }
        history.forEach(m -> System.out.println("  " + m));
    }
}

// ─── Client / Demo ────────────────────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {
        System.out.println("=== Memento Pattern Template ===\n");

        Originator originator = new Originator("State-0");
        Caretaker  caretaker  = new Caretaker(originator);

        System.out.println();
        caretaker.backup();              // save State-0

        originator.setState("State-1");
        caretaker.backup();              // save State-1

        originator.setState("State-2");
        caretaker.backup();              // save State-2

        originator.setState("State-3"); // no backup here

        System.out.println();
        caretaker.showHistory();

        System.out.println();
        caretaker.undo(); // back to State-2
        caretaker.undo(); // back to State-1
        caretaker.undo(); // back to State-0
        caretaker.undo(); // nothing left
    }
}
