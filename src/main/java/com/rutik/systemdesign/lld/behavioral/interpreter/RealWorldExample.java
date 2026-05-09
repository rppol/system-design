package com.rutik.systemdesign.lld.behavioral.interpreter; /**
 * INTERPRETER PATTERN - Real-World Example: Math Expression Parser/Evaluator
 *
 * This example implements a simple arithmetic expression evaluator that:
 *   1. Parses an infix string like "( 3 + ( 2 * 5 ) ) - 4" into an AST.
 *   2. Evaluates the AST to produce the numeric result.
 *   3. Supports variables that are resolved from a symbol table at eval time.
 *
 * Supported grammar:
 *   expression := number
 *               | variable
 *               | '(' expression operator expression ')'
 *   operator   := '+' | '-' | '*' | '/'
 *   number     := [0-9]+
 *   variable   := [a-zA-Z]+
 *
 * Note: For simplicity the parser expects fully-parenthesised expressions
 * (like a Lisp S-expression written with infix operators). Every binary
 * operation must be wrapped in parentheses. Literals and variables standing
 * alone do not need parentheses.
 *
 * Examples of valid input:
 *   "42"
 *   "x"
 *   "( 3 + 4 )"
 *   "( ( 3 + 4 ) * ( 10 - 2 ) )"
 *   "( price * quantity )"
 */

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// ============================================================================
// AST Node types (Expressions)
// ============================================================================

/** Base interface for all AST nodes. */
interface Expression {
    double evaluate(Map<String, Double> variables);
    String toInfix();
}

/** Terminal: a numeric literal. */
class Literal implements Expression {

    private final double value;

    public Literal(double value) { this.value = value; }

    @Override public double evaluate(Map<String, Double> variables) { return value; }
    @Override public String toInfix() { return (value == (long) value)
            ? String.valueOf((long) value)
            : String.valueOf(value); }
}

/** Terminal: a variable name, resolved against the symbol table at eval time. */
class Variable implements Expression {

    private final String name;

    public Variable(String name) { this.name = name; }

    @Override
    public double evaluate(Map<String, Double> variables) {
        if (!variables.containsKey(name)) {
            throw new IllegalArgumentException("Undefined variable: '" + name + "'");
        }
        return variables.get(name);
    }

    @Override public String toInfix() { return name; }
}

/** Non-terminal: a binary operation (+, -, *, /). */
class BinaryOperation implements Expression {

    private final Expression left;
    private final Expression right;
    private final char operator;

    public BinaryOperation(Expression left, char operator, Expression right) {
        this.left     = left;
        this.operator = operator;
        this.right    = right;
    }

    @Override
    public double evaluate(Map<String, Double> variables) {
        double l = left.evaluate(variables);
        double r = right.evaluate(variables);
        switch (operator) {
            case '+': return l + r;
            case '-': return l - r;
            case '*': return l * r;
            case '/':
                if (r == 0) throw new ArithmeticException("Division by zero");
                return l / r;
            default:
                throw new IllegalArgumentException("Unknown operator: " + operator);
        }
    }

    @Override
    public String toInfix() {
        return "(" + left.toInfix() + " " + operator + " " + right.toInfix() + ")";
    }
}

// ============================================================================
// Parser
//   Converts a space-delimited infix string into an AST.
//   Uses a recursive-descent approach over a token deque.
// ============================================================================
class ExpressionParser {

    /**
     * Parse the input string into an Expression AST.
     *
     * @param input space-delimited infix expression (fully parenthesised)
     * @return root Expression node
     */
    public Expression parse(String input) {
        Deque<String> tokens = tokenise(input);
        Expression result = parseExpression(tokens);
        if (!tokens.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unexpected tokens remaining: " + tokens);
        }
        return result;
    }

    /** Split the input on whitespace and push tokens into a deque. */
    private Deque<String> tokenise(String input) {
        Deque<String> deque = new ArrayDeque<>();
        for (String token : input.trim().split("\\s+")) {
            if (!token.isEmpty()) deque.addLast(token);
        }
        return deque;
    }

    /**
     * Recursive descent parser.
     *
     * expression := '(' expression operator expression ')'
     *             | number
     *             | variable
     */
    private Expression parseExpression(Deque<String> tokens) {
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }

        String token = tokens.peekFirst();

        if ("(".equals(token)) {
            // Binary operation: ( left op right )
            tokens.pollFirst(); // consume '('

            Expression left = parseExpression(tokens);

            String opToken = tokens.pollFirst();
            if (opToken == null || opToken.length() != 1
                    || "+-*/".indexOf(opToken.charAt(0)) == -1) {
                throw new IllegalArgumentException("Expected operator, got: " + opToken);
            }
            char operator = opToken.charAt(0);

            Expression right = parseExpression(tokens);

            String closeParen = tokens.pollFirst();
            if (!")".equals(closeParen)) {
                throw new IllegalArgumentException("Expected ')', got: " + closeParen);
            }

            return new BinaryOperation(left, operator, right);

        } else if (token.matches("-?\\d+(\\.\\d+)?")) {
            // Numeric literal
            tokens.pollFirst();
            return new Literal(Double.parseDouble(token));

        } else if (token.matches("[a-zA-Z][a-zA-Z0-9_]*")) {
            // Variable
            tokens.pollFirst();
            return new Variable(token);

        } else {
            throw new IllegalArgumentException("Unexpected token: " + token);
        }
    }
}

// ============================================================================
// Evaluator facade — ties Parser + evaluate() together
// ============================================================================
class MathEvaluator {

    private final ExpressionParser parser = new ExpressionParser();
    private final Map<String, Double> symbolTable = new HashMap<>();

    /** Bind a variable to a numeric value. */
    public void setVariable(String name, double value) {
        symbolTable.put(name, value);
    }

    /**
     * Parse and evaluate the expression string.
     * Variables are resolved from the current symbol table.
     */
    public double evaluate(String expressionString) {
        Expression ast = parser.parse(expressionString);
        System.out.println("  Parsed AST : " + ast.toInfix());
        double result = ast.evaluate(symbolTable);
        return result;
    }
}

// ============================================================================
// Main / Demo
// ============================================================================
public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("=== Math Expression Interpreter Demo ===\n");

        MathEvaluator evaluator = new MathEvaluator();
        evaluator.setVariable("x", 10.0);
        evaluator.setVariable("y", 4.0);
        evaluator.setVariable("price", 29.99);
        evaluator.setVariable("quantity", 3.0);

        // --- Simple literal ---
        runDemo(evaluator, "42",
                "Simple literal");

        // --- Single variable ---
        runDemo(evaluator, "x",
                "Single variable (x = 10)");

        // --- Basic arithmetic ---
        runDemo(evaluator, "( 3 + 4 )",
                "3 + 4");

        // --- Nested expression: (3 + 4) * (10 - 2) ---
        runDemo(evaluator, "( ( 3 + 4 ) * ( 10 - 2 ) )",
                "(3 + 4) * (10 - 2) = 8 * 8 = 64");

        // --- Using variables: x * y + 5 ---
        runDemo(evaluator, "( ( x * y ) + 5 )",
                "(x * y) + 5 = (10 * 4) + 5 = 45");

        // --- Division ---
        runDemo(evaluator, "( 100 / ( x + y ) )",
                "100 / (x + y) = 100 / 14 ≈ 7.14");

        // --- E-commerce: total = price * quantity ---
        runDemo(evaluator, "( price * quantity )",
                "price * quantity = 29.99 * 3 = 89.97");

        // --- Complex nested: ((x + y) * (x - y)) ---
        runDemo(evaluator, "( ( x + y ) * ( x - y ) )",
                "(x+y)*(x-y) = 14 * 6 = 84");

        // --- Division by zero (error handling demo) ---
        System.out.println("\n--- Edge case: division by zero ---");
        try {
            evaluator.evaluate("( 10 / 0 )");
        } catch (ArithmeticException e) {
            System.out.println("  Caught expected error: " + e.getMessage());
        }

        // --- Undefined variable (error handling demo) ---
        System.out.println("\n--- Edge case: undefined variable ---");
        try {
            evaluator.evaluate("( x + z )");
        } catch (IllegalArgumentException e) {
            System.out.println("  Caught expected error: " + e.getMessage());
        }
    }

    private static void runDemo(MathEvaluator evaluator, String expr, String description) {
        System.out.println("--- " + description + " ---");
        System.out.println("  Input      : " + expr);
        double result = evaluator.evaluate(expr);
        System.out.printf("  Result     : %.4f%n%n", result);
    }
}
