package com.rutik.systemdesign.lld.behavioral.observer; /**
 * Observer Pattern - Template
 *
 * Intent: Define a one-to-many dependency between objects so that when one object
 * (the Subject) changes state, all its dependents (Observers) are notified and
 * updated automatically.
 *
 * Also known as: Publish/Subscribe, Dependents.
 *
 * Key participants:
 *  - Subject         : Knows its observers; provides attach/detach interface.
 *  - Observer        : Defines the update interface for objects that should be
 *                      notified of Subject changes.
 *  - ConcreteSubject : Stores the state of interest; sends notifications on change.
 *  - ConcreteObserver: Maintains a reference to the Subject; implements update()
 *                      to keep its state consistent with the Subject's.
 *
 * When to use:
 *  - A change in one object requires changing others, and you don't know how many.
 *  - An object should be able to notify other objects without making assumptions
 *    about who those objects are (loose coupling).
 */

import java.util.ArrayList;
import java.util.List;

// ─── Observer Interface ───────────────────────────────────────────────────────

/**
 * All observers implement this interface to receive state-change notifications.
 */
interface Observer {
    /**
     * Called by the Subject when its state changes.
     *
     * @param event a string describing what changed (e.g. "priceChanged")
     * @param data  the new value or payload (use Object for flexibility)
     */
    void update(String event, Object data);
}

// ─── Subject Interface ────────────────────────────────────────────────────────

/**
 * Manages a list of observers and notifies them of state changes.
 */
interface Subject {
    void attach(Observer observer);
    void detach(Observer observer);
    void notifyObservers(String event, Object data);
}

// ─── ConcreteSubject ──────────────────────────────────────────────────────────

/**
 * Holds some state. Whenever the state changes it notifies all registered observers.
 */
class ConcreteSubject implements Subject {
    private final List<Observer> observers = new ArrayList<>();
    private String               state;

    public ConcreteSubject(String initialState) {
        this.state = initialState;
    }

    // ── Observer management ───────────────────────────────────────────────────

    @Override
    public void attach(Observer observer) {
        observers.add(observer);
        System.out.println("Subject: observer attached (" + observer + ")");
    }

    @Override
    public void detach(Observer observer) {
        observers.remove(observer);
        System.out.println("Subject: observer detached (" + observer + ")");
    }

    @Override
    public void notifyObservers(String event, Object data) {
        System.out.println("Subject: notifying " + observers.size() + " observer(s) [event=" + event + "]");
        for (Observer o : observers) {
            o.update(event, data);
        }
    }

    // ── State mutation ────────────────────────────────────────────────────────

    public void setState(String newState) {
        System.out.println("\nSubject: state changing '" + state + "' -> '" + newState + "'");
        this.state = newState;
        notifyObservers("stateChanged", newState);
    }

    public String getState() { return state; }
}

// ─── ConcreteObserver A ───────────────────────────────────────────────────────

/**
 * An observer that simply logs every event it receives.
 */
class LoggingObserver implements Observer {
    private final String name;

    public LoggingObserver(String name) {
        this.name = name;
    }

    @Override
    public void update(String event, Object data) {
        System.out.println("  [" + name + "] received event='" + event + "' data='" + data + "'");
    }

    @Override public String toString() { return "LoggingObserver(" + name + ")"; }
}

// ─── ConcreteObserver B ───────────────────────────────────────────────────────

/**
 * An observer that mirrors the subject's state into its own field.
 */
class MirrorObserver implements Observer {
    private final String name;
    private Object       mirroredState;

    public MirrorObserver(String name) {
        this.name = name;
    }

    @Override
    public void update(String event, Object data) {
        this.mirroredState = data;
        System.out.println("  [" + name + "] mirrored state updated to '" + mirroredState + "'");
    }

    public Object getMirroredState() { return mirroredState; }

    @Override public String toString() { return "MirrorObserver(" + name + ")"; }
}

// ─── Client / Demo ────────────────────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {
        System.out.println("=== Observer Pattern Template ===\n");

        ConcreteSubject subject = new ConcreteSubject("initial");

        LoggingObserver logger  = new LoggingObserver("Logger-1");
        MirrorObserver  mirror  = new MirrorObserver("Mirror-1");
        LoggingObserver logger2 = new LoggingObserver("Logger-2");

        subject.attach(logger);
        subject.attach(mirror);
        subject.attach(logger2);

        subject.setState("alpha");
        subject.setState("beta");

        System.out.println("\n-- Detach Logger-2 --");
        subject.detach(logger2);

        subject.setState("gamma");

        System.out.println("\nMirror state: " + mirror.getMirroredState());
    }
}
