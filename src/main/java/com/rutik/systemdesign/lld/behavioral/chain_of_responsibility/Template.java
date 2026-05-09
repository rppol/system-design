package com.rutik.systemdesign.lld.behavioral.chain_of_responsibility; /**
 * Chain of Responsibility Pattern - Template
 *
 * Intent: Pass a request along a chain of handlers. Each handler decides either
 * to process the request or to pass it to the next handler in the chain.
 *
 * Key participants:
 *  - Handler        : Declares the interface for handling requests and (optionally)
 *                     holds a reference to the next handler.
 *  - ConcreteHandler: Handles requests it is responsible for; otherwise forwards
 *                     to the next handler.
 *  - Client         : Initiates the request to a handler in the chain.
 *
 * When to use:
 *  - More than one object may handle a request, and the handler is not known a priori.
 *  - You want to issue a request to one of several objects without specifying the receiver.
 *  - The set of objects that can handle a request should be specified dynamically.
 */

// ─── Handler (abstract base) ─────────────────────────────────────────────────

/**
 * Defines the interface for handling requests and chaining handlers together.
 */
abstract class Handler {

    // Reference to the next handler in the chain
    protected Handler nextHandler;

    /**
     * Sets the next handler and returns it to allow fluent chaining:
     *   handlerA.setNext(handlerB).setNext(handlerC)
     */
    public Handler setNext(Handler next) {
        this.nextHandler = next;
        return next;
    }

    /**
     * Attempt to handle the request. Subclasses implement their own logic
     * and call passToNext() if they cannot handle it.
     *
     * @param request the incoming request object
     */
    public abstract void handle(Object request);

    /**
     * Forwards the request to the next handler, if one exists.
     */
    protected void passToNext(Object request) {
        if (nextHandler != null) {
            nextHandler.handle(request);
        } else {
            System.out.println("End of chain reached. Request not handled: " + request);
        }
    }
}

// ─── ConcreteHandler A ────────────────────────────────────────────────────────

/**
 * Handles requests that match its own criterion; forwards others down the chain.
 */
class ConcreteHandlerA extends Handler {

    @Override
    public void handle(Object request) {
        if (canHandle(request)) {
            System.out.println("ConcreteHandlerA handled: " + request);
        } else {
            System.out.println("ConcreteHandlerA passing request along: " + request);
            passToNext(request);
        }
    }

    private boolean canHandle(Object request) {
        // Define handling criterion for handler A
        return request instanceof String && ((String) request).startsWith("A");
    }
}

// ─── ConcreteHandler B ────────────────────────────────────────────────────────

class ConcreteHandlerB extends Handler {

    @Override
    public void handle(Object request) {
        if (canHandle(request)) {
            System.out.println("ConcreteHandlerB handled: " + request);
        } else {
            System.out.println("ConcreteHandlerB passing request along: " + request);
            passToNext(request);
        }
    }

    private boolean canHandle(Object request) {
        return request instanceof String && ((String) request).startsWith("B");
    }
}

// ─── ConcreteHandler C ────────────────────────────────────────────────────────

class ConcreteHandlerC extends Handler {

    @Override
    public void handle(Object request) {
        if (canHandle(request)) {
            System.out.println("ConcreteHandlerC handled: " + request);
        } else {
            System.out.println("ConcreteHandlerC passing request along: " + request);
            passToNext(request);
        }
    }

    private boolean canHandle(Object request) {
        return request instanceof String && ((String) request).startsWith("C");
    }
}

// ─── Client / Demo ────────────────────────────────────────────────────────────

public class Template {

    public static void main(String[] args) {
        // Build the chain: A -> B -> C
        Handler a = new ConcreteHandlerA();
        Handler b = new ConcreteHandlerB();
        Handler c = new ConcreteHandlerC();
        a.setNext(b).setNext(c);

        System.out.println("=== Chain of Responsibility Template ===\n");

        String[] requests = {"Apple", "Banana", "Cherry", "Delta"};
        for (String req : requests) {
            System.out.println("-- Sending request: " + req);
            a.handle(req);
            System.out.println();
        }
    }
}
