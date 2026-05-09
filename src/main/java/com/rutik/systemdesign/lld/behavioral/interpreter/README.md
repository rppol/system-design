# Interpreter Pattern

## 1. Pattern Name & Category

**Pattern Name:** Interpreter
**Category:** Behavioral
**GoF Classification:** Behavioral Design Pattern (Gang of Four, "Design Patterns: Elements of Reusable Object-Oriented Software", 1994)

---

## 2. Intent

Given a language, define a representation for its grammar along with an interpreter that uses the representation to interpret sentences in the language.

---

## Intuition

> **One-line analogy**: Interpreter is like a calculator reading a math expression — it parses "3 + 4 * 2" into a tree of operations and evaluates each node (multiply 4*2=8, then add 3+8=11).

**Mental model**: When you have a simple, domain-specific language (SQL WHERE clauses, regular expressions, search queries, configuration expressions), Interpreter builds a grammar in code. Each grammar rule becomes a class that implements `interpret(context)`. Complex expressions are trees of simpler expressions. Evaluation recursively evaluates the tree. The pattern makes the grammar explicit in the class hierarchy.

**Why it matters**: SQL query parsers, boolean expression evaluators, configuration DSLs, regular expression matchers — all use Interpreter at their core. It's the pattern behind any "evaluate an expression tree" scenario. More commonly, production systems use parser generators (ANTLR, JavaCC) instead of hand-coding Interpreter, but the pattern explains what those tools produce.

**Key insight**: Interpreter is only practical for small, simple grammars. For complex languages, use parser generators. The class hierarchy becomes unwieldy for grammars with many rules, which is why real language implementations rarely use the raw Interpreter pattern.

---

## 3. Problem Statement

### The Problem
You need to process sentences in a simple, domain-specific language (DSL) — a search query, a mathematical expression, a configuration rule, a boolean filter expression — and your processing logic is scattered across complex conditional statements or hard-coded string parsing.

### Scenario 1: Search Query Parser
A search engine supports queries like: `title:java AND (author:gosling OR tag:oop) NOT deprecated`. You need to parse and evaluate these queries against a document database. Each query can combine terms with `AND`, `OR`, `NOT`, parentheses, and field specifiers. Writing ad-hoc string parsing with nested conditionals is error-prone and hard to extend.

### Scenario 2: Business Rule Engine
An insurance system has rules like: `age > 25 AND income >= 50000 AND (creditScore > 700 OR hasCollateral = true)`. These rules are stored as strings and evaluated against customer profiles. Adding support for new operators or functions requires modifying a monolithic parser class.

### Scenario 3: Mathematical Expression Evaluator
A spreadsheet application needs to evaluate formulas like `(A1 + B2) * C3 / 100`. Each cell reference, number, arithmetic operation, and parenthesized group needs a representation that can be evaluated in context.

The common thread: you have a structured language with a grammar, and you need to evaluate/execute sentences in that language.

---

## 4. Solution

The Interpreter pattern maps each grammar rule directly to a class. The sentence is parsed into an **Abstract Syntax Tree (AST)** where each node is an instance of one of these grammar-rule classes. Evaluating the sentence means calling `interpret()` on the root of the AST, which recursively calls `interpret()` on its children.

**Grammar:** Define it formally (BNF or EBNF). Map each rule to a class.
**Terminal Expressions:** Leaf nodes representing the basic tokens (literals, identifiers). They don't have children.
**Non-Terminal Expressions:** Inner nodes representing grammar rules that combine sub-expressions. They hold references to child expression nodes.
**Context:** Holds global information needed during interpretation (variable values, symbol table, evaluation environment).

---

## 5. UML Structure

```
    <<interface>>
     Expression
+──────────────────────────────+
| + interpret(ctx: Context)    |
+──────────────────────────────+
           /\
           | implements
    _______|___________
    |       |          |
Terminal  NonTerminal  NonTerminal
Expression  AndExpr    OrExpr
+────────+ +─────────+ +─────────+
| value  | | left    | | left    |
|        | | right   | | right   |
+────────+ +─────────+ +─────────+
  interpret()  interpret()  interpret()
  returns       calls left    calls left
  own value     .interpret()  .interpret()
              AND right     OR right
              .interpret()  .interpret()


Context
+─────────────────────────────────+
| - variables: Map<String,Object> |
| + lookup(name): Object          |
| + assign(name, value): void     |
+─────────────────────────────────+

Client
  |
  |--> Builds AST (parse tree of Expression objects)
  |--> Creates Context with variable values
  |--> Calls root.interpret(context)
```

**Example AST for `a AND (b OR c)`:**
```
        AndExpression
        /            \
  VariableExpr    OrExpression
      "a"         /          \
           VariableExpr  VariableExpr
               "b"           "c"
```

---

## 6. How It Works — Step-by-Step

1. **Define the grammar** (formally or informally). Identify terminal symbols (literals, identifiers, keywords) and non-terminal rules (combinations like `AND`, `OR`, `NOT`, arithmetic operators).

2. **Create an `Expression` interface** with `interpret(Context ctx)` returning the evaluation result (boolean, integer, string, etc.).

3. **Create Terminal Expression classes** for each literal/token type: `NumberExpression`, `VariableExpression`, `BooleanLiteralExpression`. Their `interpret()` returns the literal value or looks up the variable in the context.

4. **Create Non-Terminal Expression classes** for each grammar rule that combines sub-expressions: `AddExpression`, `AndExpression`, `OrExpression`, `NotExpression`. They hold references to child `Expression` objects and their `interpret()` evaluates children and combines results.

5. **Parse the input sentence** into an AST. The Parser reads tokens and constructs the tree of expression objects. (The Parser itself is not part of the pattern; it's the mechanism that builds the AST.)

6. **Create a `Context`** object populated with any variable bindings or state the interpreter needs.

7. **Call `interpret(context)` on the root expression.** Evaluation propagates recursively down the tree, with results bubbling back up.

8. **The root expression returns the final result** of evaluating the entire sentence.

---

## 7. Key Components

| Component | Role |
|---|---|
| **AbstractExpression (interface/abstract class)** | Declares `interpret(Context)` — the common interface for all nodes |
| **TerminalExpression** | Leaf node; interprets a basic grammar symbol (literal, variable) |
| **NonTerminalExpression** | Inner node; holds child expressions and combines their results |
| **Context** | Stores global state/variables needed during evaluation |
| **Client / Parser** | Builds the AST from input; calls `interpret()` on the root |
| **AST (Abstract Syntax Tree)** | The tree of `Expression` objects representing the parsed sentence |

---

## 8. When to Use

- **Simple, well-defined grammar:** When the language has a clear, limited grammar that doesn't require a full parser generator (e.g., ANTLR, JavaCC).
- **Repeated interpretation:** The same sentence (or many sentences) will be interpreted many times. Building an AST once and re-evaluating it is efficient.
- **Grammar rules map cleanly to classes:** When the grammar is compositional (expressions contain sub-expressions), the recursive structure maps naturally to the composite pattern.
- **DSL for configuration or rules:** Business rules, query languages, filter expressions, configuration DSLs that need runtime evaluation.
- **Mathematical/logical expression evaluation:** Calculators, spreadsheet formula engines, rule engines.
- **Small languages with ~10 grammar rules:** Beyond that, a proper parser generator is more appropriate.
- **When you want to extend the language:** Adding a new expression type is as simple as adding a new class.

---

## 9. When NOT to Use

- **Complex grammars:** If the grammar has dozens of rules or complex precedence/associativity rules, maintaining one class per rule becomes unmanageable. Use ANTLR, JavaCC, or PEG parsers instead.
- **Performance-critical interpretation:** Recursive tree traversal with virtual dispatch is slow for hot paths. Consider compiling to bytecode or using a more efficient evaluator.
- **When a library exists:** If you need SQL parsing, JSON evaluation, XPath, or regular expressions, use a dedicated library. Don't reimplement them with Interpreter.
- **Infrequent evaluation of changing sentences:** If each sentence is evaluated only once and sentences change often, the overhead of building and discarding ASTs is wasteful.
- **When grammar changes frequently:** If the grammar rules evolve rapidly, the class-per-rule structure means frequent class additions/modifications across the hierarchy.

---

## 10. Pros

- **Grammar is explicit and maintainable:** Each grammar rule is encapsulated in its own class. The grammar is self-documenting through the class structure.
- **Easy to extend the language:** Add a new grammar rule by adding a new `Expression` class. No modification to existing code (Open/Closed Principle).
- **Composable by nature:** Expressions are naturally composable (they hold references to other expressions), making complex expressions easy to build.
- **Separates parsing from evaluation:** The AST separates the structure of the sentence from how it's evaluated. You can have multiple visitors or evaluation strategies over the same AST.
- **Reusable sub-expressions:** The same sub-expression object can be shared in the AST (flyweight), reducing memory for repeated sub-expressions.
- **Testable in isolation:** Each `Expression` class can be unit-tested independently by constructing it with mock children and a test context.
- **Leverages the Composite pattern:** The tree of expressions naturally follows the Composite pattern, giving you all its benefits (recursive structure, uniform interface).

---

## 11. Cons

- **Class explosion for complex grammars:** A grammar with 30 rules requires at least 30 classes. This becomes very hard to navigate and maintain.
- **No error recovery:** The basic pattern doesn't provide mechanisms for handling malformed input gracefully. Adding error recovery significantly complicates the implementation.
- **Performance overhead:** Recursive tree traversal with polymorphic dispatch is slower than a direct evaluation loop or a compiled approach.
- **Hard to optimize:** Optimizations like constant folding, common subexpression elimination, and short-circuit evaluation must be manually added to each non-terminal class.
- **Parser not included:** The pattern defines how to evaluate; it doesn't define how to parse the input into an AST. Building a robust parser is often the harder problem.
- **Debugging complexity:** Tracing through a deep recursive tree of polymorphic calls is harder than debugging a simple loop-based evaluator.
- **Memory overhead:** Representing a sentence as a tree of objects is much more memory-intensive than a flat string or token array.

---

## 12. Tradeoffs

| What You Gain | What You Lose |
|---|---|
| Clean grammar representation | Class count grows with grammar size |
| Easy to add new expressions | Complex grammars become unmanageable |
| Composable, testable structure | Performance (recursive dispatch overhead) |
| Separation of parsing and evaluation | No built-in error recovery |
| Open for extension | Hard to optimize hot paths |

**Interpreter vs Parser Generators:** Interpreter is appropriate for grammars with ~5-15 rules. For larger grammars, tools like ANTLR generate optimized parsers and tree walkers automatically, with better error handling and performance.

---

## 13. Common Pitfalls

1. **Using Interpreter for complex grammars:** The pattern works for simple DSLs. For anything with complex precedence rules, multiple levels of statements/expressions, or error recovery, use a dedicated parser. Drawing the line at ~10-15 grammar rules is a reasonable heuristic.

2. **Mixing parsing and interpretation in the same class:** The parser (input → AST) and the interpreter (AST → result) are separate concerns. Mixing them creates tangled code that's hard to test and extend.

3. **Not handling operator precedence correctly:** In expression languages, multiplication binds tighter than addition. Your parser must construct the AST with correct precedence. A common mistake is building a left-associative tree for right-associative operators.

4. **Mutable context causing bugs:** If `interpret()` calls modify the context (e.g., variable assignments), the order of evaluation matters. Side effects in sub-expressions can cause non-obvious bugs when expressions are shared or evaluated out of order.

5. **Forgetting null-checks for optional sub-expressions:** Some grammar rules have optional parts (e.g., the `ELSE` in `IF-THEN-ELSE`). If the optional child is null and `interpret()` doesn't handle it, you get NullPointerExceptions.

6. **Infinite recursion in recursive grammars:** Left-recursive grammar rules (e.g., `expr := expr '+' term`) map to infinite recursion if naively implemented. Parser construction must handle left-recursion elimination.

7. **Building the AST manually in production code:** Real applications parse user input, not hardcoded expressions. Make sure you have a proper tokenizer and parser before the AST-building phase.

---

## 14. Real-World Usage

### Java Standard Library
- **`java.util.regex`:** Regular expressions are a DSL interpreted by the regex engine. The pattern is compiled to an internal representation (analogous to an AST) and then interpreted against input strings.
- **`javax.el` (Expression Language):** Jakarta EL used in JSP/JSF evaluates expressions like `${user.name}` and `${list.size() > 0}` using an interpreter over an AST.
- **`java.util.logging` formatters:** Format patterns like `%d{yyyy-MM-dd} %-5level %msg%n` are interpreted by log formatters.

### Spring Framework
- **Spring Expression Language (SpEL):** `#{user.name}`, `#{T(Math).random() * 100}`, `@{/path/${id}}` — SpEL is a full Interpreter implementation with operators, method invocation, collection projection, and more.
- **`@PreAuthorize("hasRole('ADMIN') and #user.id == authentication.principal.id")`:** Spring Security evaluates SpEL expressions for method security.

### Build and Configuration Tools
- **Ant/Maven XML expressions:** Property substitution like `${project.version}` uses simple interpreters.
- **OGNL (Object-Graph Navigation Language):** Used by Struts/MyBatis to evaluate property access expressions.

### Database and Query Engines
- **SQL query evaluators:** The WHERE clause in a SQL query is evaluated as a boolean expression tree — a classic Interpreter.
- **Apache Calcite:** An SQL parser and query optimizer that builds an AST (relational algebra tree) using an Interpreter-like approach.
- **MongoDB query operators:** `$and`, `$or`, `$gt`, `$regex` operators form a composable expression language evaluated against documents.

### Rules Engines
- **Drools (Red Hat):** Business rules are compiled to an internal representation and interpreted against working memory facts.
- **Easy Rules:** Lightweight Java rules engine where rules are composed of conditions (boolean expressions) that read like an Interpreter.

---

## 15. Comparison with Similar Patterns

### Interpreter vs Composite
- Interpreter IS a specialized application of Composite. The expression tree IS a composite structure where leaf nodes are TerminalExpressions and inner nodes are NonTerminalExpressions.
- Composite provides the structural pattern; Interpreter adds semantic meaning (the `interpret()` operation).

### Interpreter vs Visitor
- These are complementary. You can add new operations to an existing Interpreter's AST by applying the Visitor pattern to the AST nodes.
- Interpreter defines the AST structure and one built-in operation (interpret). Visitor lets you add more operations (type-check, pretty-print, optimize) without modifying the AST classes.

### Interpreter vs Strategy
- Strategy replaces an algorithm in one step. Interpreter evaluates a composed, recursive structure.
- Strategy is flat (swap one algorithm); Interpreter is recursive (evaluate a tree).

### Interpreter vs Template Method
- Template Method defines how an algorithm's steps are orchestrated. Interpreter defines how a sentence's sub-expressions are composed and evaluated.
- Template Method is linear/sequential; Interpreter is recursive/tree-structured.

---

## 16. Interview Tips

**Q: What is the Interpreter pattern?**
A: Describe it as "grammar rules mapped to classes." Each rule in the grammar becomes a class; parsing a sentence produces an AST of those classes; evaluating the sentence means calling `interpret()` on the root. Give a concrete example: SQL WHERE clause, SpEL, or a boolean filter expression.

**Q: What is an AST and how does it relate to Interpreter?**
A: AST (Abstract Syntax Tree) is the tree of expression objects produced by parsing. In Interpreter, each node of the AST is an `Expression` object. Evaluating the sentence = traversing the AST by calling `interpret()` recursively from the root.

**Q: When would you NOT use the Interpreter pattern?**
A: When the grammar is complex (use ANTLR/JavaCC), when performance is critical (compile to bytecode), or when a library already exists for the language (regex, SQL, JSON).

**Q: How does Interpreter relate to Composite?**
A: Interpreter IS Composite applied to grammar. NonTerminalExpressions are composite nodes; TerminalExpressions are leaves. The `interpret()` method traverses the composite tree recursively.

**Q: What is the Context in the Interpreter pattern?**
A: Context stores the global state needed during evaluation — typically a map of variable names to values. When a VariableExpression evaluates itself, it looks up its name in the context to get the current value.

**Q: Where is Interpreter used in real frameworks?**
A: Spring Expression Language (SpEL), Java's regex engine (`java.util.regex`), Jakarta Expression Language (`javax.el`), OGNL in MyBatis/Struts, SQL query evaluation engines.

---

## Cross-Perspective: HLD Connections

**HLD View — Where Interpreter Appears in Distributed Systems**

- **SQL/query language parsers** — Database gateways and ORMs parse SQL or query DSLs using an Interpreter: each grammar rule maps to an expression class (`SelectExpression`, `WhereExpression`, `JoinExpression`). The interpreter tree is evaluated against the schema and data.
- **Config DSLs** — Terraform HCL, Kubernetes CEL (Common Expression Language), and Open Policy Agent Rego are interpreted languages embedded in infrastructure tools. The interpreter evaluates policy expressions to make routing, admission, or authorization decisions.
- **Rule engines** — Business rule engines (Drools, Easy Rules) represent rules as Interpreter trees. Adding a new rule means adding a new expression node; the engine evaluates the tree against incoming facts to fire matching rules.
- **Search query parsing** — Elasticsearch's Query DSL, Lucene's query parser, and ElasticSearch's Painless scripting language are all Interpreter implementations — JSON/string queries are parsed into expression trees and evaluated against the inverted index.

---

## 17. Best Practices

1. **Keep the grammar simple:** Use Interpreter only for grammars with ~5-15 rules. If the grammar grows beyond that, invest in a proper parser generator (ANTLR, JavaCC, PEG.js).

2. **Separate the parser from the interpreter:** The parser (String → AST) and the interpreter (AST → result) are separate concerns. Keep them in separate classes. This enables you to swap parsers or add new interpreters over the same AST.

3. **Make the Context immutable or explicitly document mutation:** If interpretation has side effects (variable assignments, counter increments), make these explicit and document evaluation order carefully.

4. **Design the Context API carefully:** The context is shared across the entire expression tree during one evaluation. It should provide a clean API for expression nodes to use — lookups, assignments, error reporting.

5. **Use the Visitor pattern for multiple operations over the AST:** If you need more than one thing from the AST (evaluate, pretty-print, type-check, optimize), use Visitor to add operations without modifying the expression classes.

6. **Handle errors gracefully:** Define a clear error-handling strategy. Options include: throw a descriptive exception, return a sentinel value, or accumulate errors in the Context. The last option enables reporting multiple errors instead of failing on the first.

7. **Test each expression class in isolation:** Each `TerminalExpression` and `NonTerminalExpression` should have unit tests that construct the expression with known children/values and verify `interpret()` returns the expected result.

8. **Consider caching for repeated evaluation:** If the same AST is evaluated many times with the same context, consider caching results in nodes that don't depend on mutable context state (constant folding at build time).

9. **Document operator precedence and associativity:** In expression languages, operators have precedence (multiplication before addition) and associativity (left or right). Document these clearly and ensure the parser constructs the AST correctly.

10. **Use builder or factory methods for common expressions:** For frequently used expression types, provide static factory methods (`Expression.and(left, right)`, `Expression.not(child)`) to make AST construction in tests and application code more readable.
