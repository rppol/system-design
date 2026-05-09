package com.rutik.systemdesign.lld.behavioral.strategy;

/**
 * STRATEGY PATTERN - Template / Skeleton
 *
 * Intent:
 *   Define a family of algorithms, encapsulate each one, and make them
 *   interchangeable. Strategy lets the algorithm vary independently from
 *   clients that use it.
 *
 * When to use:
 *   - Many related classes differ only in their behavior.
 *   - You need different variants of an algorithm at runtime.
 *   - You want to eliminate conditionals that select algorithm variants.
 *
 * Structure:
 *   - Strategy         : Common interface for all concrete strategies.
 *   - ConcreteStrategy : Implements one specific algorithm variant.
 *   - Context          : Configured with a Strategy object; delegates
 *                        the algorithm call to it.
 */

// ---------------------------------------------------------------------------
// 1. Strategy Interface
//    Declares the operation that all concrete strategies must implement.
// ---------------------------------------------------------------------------
interface Strategy {
    /**
     * @param data  Input to the algorithm.
     * @return      Result produced by the algorithm.
     */
    int execute(int a, int b);
}

// ---------------------------------------------------------------------------
// 2. ConcreteStrategy A  — one algorithm variant
// ---------------------------------------------------------------------------
class ConcreteStrategyAdd implements Strategy {

    @Override
    public int execute(int a, int b) {
        System.out.println("ConcreteStrategyAdd: " + a + " + " + b);
        return a + b;
    }
}

// ---------------------------------------------------------------------------
// 3. ConcreteStrategy B  — another algorithm variant
// ---------------------------------------------------------------------------
class ConcreteStrategySubtract implements Strategy {

    @Override
    public int execute(int a, int b) {
        System.out.println("ConcreteStrategySubtract: " + a + " - " + b);
        return a - b;
    }
}

// ---------------------------------------------------------------------------
// 4. ConcreteStrategy C  — yet another algorithm variant
// ---------------------------------------------------------------------------
class ConcreteStrategyMultiply implements Strategy {

    @Override
    public int execute(int a, int b) {
        System.out.println("ConcreteStrategyMultiply: " + a + " * " + b);
        return a * b;
    }
}

// ---------------------------------------------------------------------------
// 5. Context
//    - Holds a reference to a Strategy.
//    - Delegates the algorithm to the strategy rather than implementing it.
//    - The strategy can be swapped at any point via setStrategy().
// ---------------------------------------------------------------------------
class Context {

    private Strategy strategy;

    /** Create context with an initial strategy. */
    public Context(Strategy strategy) {
        this.strategy = strategy;
    }

    /** Hot-swap the strategy at runtime. */
    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    /** Delegates algorithm execution to the current strategy. */
    public int executeStrategy(int a, int b) {
        return strategy.execute(a, b);
    }
}

// ---------------------------------------------------------------------------
// 6. Client / Demo
// ---------------------------------------------------------------------------
public class Template {

    public static void main(String[] args) {
        Context context = new Context(new ConcreteStrategyAdd());

        System.out.println("Result: " + context.executeStrategy(10, 5));

        // Swap strategy at runtime — no change needed in Context
        context.setStrategy(new ConcreteStrategySubtract());
        System.out.println("Result: " + context.executeStrategy(10, 5));

        context.setStrategy(new ConcreteStrategyMultiply());
        System.out.println("Result: " + context.executeStrategy(10, 5));
    }
}
