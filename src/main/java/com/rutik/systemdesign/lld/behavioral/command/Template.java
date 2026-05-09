package com.rutik.systemdesign.lld.behavioral.command; /**
 * Command Pattern - Template
 *
 * Intent: Encapsulate a request as an object, thereby letting you parameterize
 * clients with different requests, queue or log requests, and support undoable
 * operations.
 *
 * Key participants:
 *  - Command        : Declares an interface for executing an operation (and optionally undo).
 *  - ConcreteCommand: Binds a Receiver to an action; implements execute() by invoking
 *                     corresponding operation(s) on the Receiver.
 *  - Receiver       : Knows how to perform the operations needed to carry out a request.
 *  - Invoker        : Asks the command to carry out the request; may maintain a history.
 *  - Client         : Creates ConcreteCommand objects and sets their Receiver.
 *
 * When to use:
 *  - You want to parameterize objects with an action to perform.
 *  - You want to queue, schedule, or log operations.
 *  - You need undoable operations.
 */

import java.util.ArrayDeque;
import java.util.Deque;

// ─── Command Interface ────────────────────────────────────────────────────────

/**
 * Every command must be executable and undoable.
 */
interface Command {
    /** Executes the operation. */
    void execute();

    /** Reverses the operation (for undo support). */
    void undo();
}

// ─── Receiver ─────────────────────────────────────────────────────────────────

/**
 * The object that knows how to perform the actual work.
 * Commands delegate their logic to the Receiver.
 */
class Receiver {
    private String state = "initial";

    public void actionA(String param) {
        System.out.println("Receiver: performing ActionA with param='" + param + "'");
        state = "after-A:" + param;
    }

    public void undoActionA(String param) {
        System.out.println("Receiver: undoing ActionA with param='" + param + "'");
        state = "initial";
    }

    public void actionB(int value) {
        System.out.println("Receiver: performing ActionB with value=" + value);
        state = "after-B:" + value;
    }

    public void undoActionB(int value) {
        System.out.println("Receiver: undoing ActionB with value=" + value);
        state = "initial";
    }

    public String getState() { return state; }
}

// ─── ConcreteCommand A ────────────────────────────────────────────────────────

/**
 * Wraps actionA on the Receiver.
 */
class ConcreteCommandA implements Command {
    private final Receiver receiver;
    private final String   param;

    public ConcreteCommandA(Receiver receiver, String param) {
        this.receiver = receiver;
        this.param    = param;
    }

    @Override
    public void execute() {
        receiver.actionA(param);
    }

    @Override
    public void undo() {
        receiver.undoActionA(param);
    }
}

// ─── ConcreteCommand B ────────────────────────────────────────────────────────

class ConcreteCommandB implements Command {
    private final Receiver receiver;
    private final int      value;

    public ConcreteCommandB(Receiver receiver, int value) {
        this.receiver = receiver;
        this.value    = value;
    }

    @Override
    public void execute() {
        receiver.actionB(value);
    }

    @Override
    public void undo() {
        receiver.undoActionB(value);
    }
}

// ─── Invoker ──────────────────────────────────────────────────────────────────

/**
 * The Invoker triggers commands and manages the undo/redo history stacks.
 * It has no knowledge of what the commands actually do.
 */
class Invoker {
    private final Deque<Command> history  = new ArrayDeque<>(); // executed commands
    private final Deque<Command> redoStack = new ArrayDeque<>(); // undone commands

    /** Executes a command and records it in history. */
    public void executeCommand(Command command) {
        command.execute();
        history.push(command);
        redoStack.clear(); // new command invalidates the redo stack
    }

    /** Undoes the most recently executed command. */
    public void undo() {
        if (history.isEmpty()) {
            System.out.println("Invoker: nothing to undo.");
            return;
        }
        Command cmd = history.pop();
        cmd.undo();
        redoStack.push(cmd);
    }

    /** Re-executes the most recently undone command. */
    public void redo() {
        if (redoStack.isEmpty()) {
            System.out.println("Invoker: nothing to redo.");
            return;
        }
        Command cmd = redoStack.pop();
        cmd.execute();
        history.push(cmd);
    }
}

// ─── Client / Demo ────────────────────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {
        System.out.println("=== Command Pattern Template ===\n");

        Receiver receiver = new Receiver();
        Invoker  invoker  = new Invoker();

        Command cmdA = new ConcreteCommandA(receiver, "hello");
        Command cmdB = new ConcreteCommandB(receiver, 42);

        System.out.println("-- Execute A --");
        invoker.executeCommand(cmdA);
        System.out.println("State: " + receiver.getState());

        System.out.println("\n-- Execute B --");
        invoker.executeCommand(cmdB);
        System.out.println("State: " + receiver.getState());

        System.out.println("\n-- Undo B --");
        invoker.undo();
        System.out.println("State: " + receiver.getState());

        System.out.println("\n-- Redo B --");
        invoker.redo();
        System.out.println("State: " + receiver.getState());

        System.out.println("\n-- Undo all --");
        invoker.undo();
        invoker.undo();
        invoker.undo(); // should say "nothing to undo"
    }
}
