package com.rutik.systemdesign.lld.behavioral.interpreter; /**
 * INTERPRETER PATTERN - Template / Skeleton
 *
 * Intent:
 *   Given a language, define a representation for its grammar along with an
 *   interpreter that uses the representation to interpret sentences in that
 *   language.
 *
 * When to use:
 *   - The grammar is simple and efficiency is not critical.
 *   - You want to represent sentences in a language as an Abstract Syntax Tree
 *     (AST) and evaluate the tree.
 *   - Common uses: rule engines, expression evaluators, query parsers, config DSLs.
 *
 * Structure:
 *   - AbstractExpression    : Declares the interpret(Context) interface.
 *   - TerminalExpression    : Implements interpret() for leaf grammar symbols
 *                             (variables, literals). No sub-expressions.
 *   - NonTerminalExpression : Implements interpret() for composite grammar rules.
 *                             Holds references to child AbstractExpressions and
 *                             combines their results.
 *   - Context               : Contains global information used during interpretation
 *                             (e.g., variable bindings).
 *   - Client                : Builds the AST from the grammar and calls interpret().
 */

import java.util.HashMap;
import java.util.Map;

// ---------------------------------------------------------------------------
// 1. Context
//    Stores variable bindings used during expression evaluation.
// ---------------------------------------------------------------------------
class Context {

    private final Map<String, Integer> variables = new HashMap<>();

    public void assign(String variable, int value) {
        variables.put(variable, value);
    }

    public int lookup(String variable) {
        if (!variables.containsKey(variable)) {
            throw new IllegalArgumentException("Undefined variable: " + variable);
        }
        return variables.get(variable);
    }
}

// ---------------------------------------------------------------------------
// 2. AbstractExpression
//    All expression nodes implement this interface.
// ---------------------------------------------------------------------------
interface AbstractExpression {
    /**
     * Interpret this expression node given the current context.
     * @param context global variable bindings
     * @return integer result of evaluating this node
     */
    int interpret(Context context);
}

// ---------------------------------------------------------------------------
// 3. TerminalExpression — Number literal
//    Leaf node: holds a constant integer value.
// ---------------------------------------------------------------------------
class NumberExpression implements AbstractExpression {

    private final int number;

    public NumberExpression(int number) {
        this.number = number;
    }

    @Override
    public int interpret(Context context) {
        return number; // just return the literal value
    }

    @Override
    public String toString() { return String.valueOf(number); }
}

// ---------------------------------------------------------------------------
// 4. TerminalExpression — Variable
//    Leaf node: looks up a variable name in the context.
// ---------------------------------------------------------------------------
class VariableExpression implements AbstractExpression {

    private final String name;

    public VariableExpression(String name) {
        this.name = name;
    }

    @Override
    public int interpret(Context context) {
        return context.lookup(name);
    }

    @Override
    public String toString() { return name; }
}

// ---------------------------------------------------------------------------
// 5. NonTerminalExpression — Addition
//    Composite node: evaluates left + right.
// ---------------------------------------------------------------------------
class AddExpression implements AbstractExpression {

    private final AbstractExpression left;
    private final AbstractExpression right;

    public AddExpression(AbstractExpression left, AbstractExpression right) {
        this.left  = left;
        this.right = right;
    }

    @Override
    public int interpret(Context context) {
        return left.interpret(context) + right.interpret(context);
    }

    @Override
    public String toString() { return "(" + left + " + " + right + ")"; }
}

// ---------------------------------------------------------------------------
// 6. NonTerminalExpression — Subtraction
// ---------------------------------------------------------------------------
class SubtractExpression implements AbstractExpression {

    private final AbstractExpression left;
    private final AbstractExpression right;

    public SubtractExpression(AbstractExpression left, AbstractExpression right) {
        this.left  = left;
        this.right = right;
    }

    @Override
    public int interpret(Context context) {
        return left.interpret(context) - right.interpret(context);
    }

    @Override
    public String toString() { return "(" + left + " - " + right + ")"; }
}

// ---------------------------------------------------------------------------
// 7. NonTerminalExpression — Multiplication
// ---------------------------------------------------------------------------
class MultiplyExpression implements AbstractExpression {

    private final AbstractExpression left;
    private final AbstractExpression right;

    public MultiplyExpression(AbstractExpression left, AbstractExpression right) {
        this.left  = left;
        this.right = right;
    }

    @Override
    public int interpret(Context context) {
        return left.interpret(context) * right.interpret(context);
    }

    @Override
    public String toString() { return "(" + left + " * " + right + ")"; }
}

// ---------------------------------------------------------------------------
// 8. Client / Demo
//    Manually builds the AST for: (a + b) * (c - 2)
// ---------------------------------------------------------------------------
public class Template {

    public static void main(String[] args) {

        // Set up context: a=5, b=3, c=10
        Context context = new Context();
        context.assign("a", 5);
        context.assign("b", 3);
        context.assign("c", 10);

        // Build AST for (a + b) * (c - 2)
        AbstractExpression a = new VariableExpression("a");
        AbstractExpression b = new VariableExpression("b");
        AbstractExpression c = new VariableExpression("c");
        AbstractExpression two = new NumberExpression(2);

        AbstractExpression sumAB    = new AddExpression(a, b);       // (a + b)
        AbstractExpression diffC2   = new SubtractExpression(c, two); // (c - 2)
        AbstractExpression product  = new MultiplyExpression(sumAB, diffC2); // (a+b)*(c-2)

        System.out.println("Expression : " + product);
        System.out.println("Context    : a=5, b=3, c=10");
        System.out.println("Result     : " + product.interpret(context));
        // Expected: (5+3) * (10-2) = 8 * 8 = 64

        // Another expression: a + 10 - b
        AbstractExpression expr2 = new SubtractExpression(
                new AddExpression(a, new NumberExpression(10)), b);
        System.out.println();
        System.out.println("Expression : " + expr2);
        System.out.println("Result     : " + expr2.interpret(context));
        // Expected: (5 + 10) - 3 = 12
    }
}
