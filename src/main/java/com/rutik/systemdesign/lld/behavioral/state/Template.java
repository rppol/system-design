package com.rutik.systemdesign.lld.behavioral.state;

/**
 * STATE PATTERN - Template / Skeleton
 *
 * Intent:
 *   Allow an object to alter its behavior when its internal state changes.
 *   The object will appear to change its class.
 *
 * When to use:
 *   - An object's behavior depends on its state and must change at runtime.
 *   - Operations have large, multi-part conditionals that depend on the object's state.
 *
 * Structure:
 *   - State         : Interface declaring the state-specific behavior.
 *   - ConcreteState : Each subclass implements behavior for one particular state.
 *   - Context       : Maintains a reference to the current State; delegates
 *                     state-dependent work to it and exposes a way to switch states.
 */

// ---------------------------------------------------------------------------
// 1. State Interface
//    Declares all operations that are state-sensitive.
// ---------------------------------------------------------------------------
interface State {
    /**
     * Every concrete state receives the context so it can trigger
     * a state transition when appropriate.
     */
    void handle(Context context);
}

// ---------------------------------------------------------------------------
// 2. ConcreteState A
// ---------------------------------------------------------------------------
class ConcreteStateA implements State {

    @Override
    public void handle(Context context) {
        System.out.println("ConcreteStateA: handling request, transitioning to StateB.");
        // Transition: tell the context to switch to StateB
        context.setState(new ConcreteStateB());
    }
}

// ---------------------------------------------------------------------------
// 3. ConcreteState B
// ---------------------------------------------------------------------------
class ConcreteStateB implements State {

    @Override
    public void handle(Context context) {
        System.out.println("ConcreteStateB: handling request, transitioning to StateA.");
        // Transition: tell the context to switch back to StateA
        context.setState(new ConcreteStateA());
    }
}

// ---------------------------------------------------------------------------
// 4. Context
//    - Holds the current state.
//    - Delegates state-specific behavior to the current State object.
//    - Provides setState() so states (or external code) can trigger transitions.
// ---------------------------------------------------------------------------
class Context {

    private State currentState;

    /** Initialise the context with a starting state. */
    public Context(State initialState) {
        this.currentState = initialState;
    }

    /** Called by states to trigger a transition. */
    public void setState(State newState) {
        System.out.println("Context: transitioning from "
                + currentState.getClass().getSimpleName()
                + " to " + newState.getClass().getSimpleName());
        this.currentState = newState;
    }

    /** The context delegates to the current state. */
    public void request() {
        currentState.handle(this);
    }

    public State getState() {
        return currentState;
    }
}

// ---------------------------------------------------------------------------
// 5. Client / Demo
// ---------------------------------------------------------------------------
public class Template {

    public static void main(String[] args) {
        // Start in StateA
        Context context = new Context(new ConcreteStateA());

        System.out.println("--- Request 1 ---");
        context.request(); // StateA handles -> transitions to StateB

        System.out.println("--- Request 2 ---");
        context.request(); // StateB handles -> transitions to StateA

        System.out.println("--- Request 3 ---");
        context.request(); // StateA handles -> transitions to StateB
    }
}
