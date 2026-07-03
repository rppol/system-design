# Strings and Text in Java

## 1. Concept Overview

Java strings are immutable, interned objects backed by a `byte[]` array (Java 9+, Compact Strings). The `String` class participates in a JVM-level string table (the constant pool / string table), `invokedynamic`-based concatenation at the bytecode level, and a rich set of text-manipulation APIs added through Java 15 (text blocks) and Java 21 (template expressions preview).

Key capabilities:
- String immutability + constant pool sharing
- `String.intern()` and JVM string table (heap-resident since Java 7u40)
- Compact Strings (JEP 254, Java 9): `LATIN1` vs `UTF16` coder
- `StringBuilder` vs `StringBuffer` vs `+` operator (invokedynamic / `StringConcatFactory`)
- Text blocks (JEP 378, Java 15 GA)
- `String.format` vs `formatted()` vs `MessageFormat`
- `String.chars()` / `codePoints()` and Unicode correctness
- Pattern matching and regular expressions
- Immutable value semantics as a design pattern

---

## 2. Intuition

> A `String` is like a sealed, laminated label: once printed you never erase it — you print a new one and the old goes to the recycling bin (GC). The JVM keeps a dictionary of these labels (`String` table) so two pieces of code asking for the same label get the exact same physical object.

**Key insight:** Immutability is not a limitation — it is a *guarantee* that makes Strings safe for use as map keys, cache keys, thread-shared constants, and class-loader paths without any synchronisation. Every mutating operation (concatenation, replace, trim) returns a new object; that is the design, not a bug.

**Why this matters in interviews:** String immutability, constant pool, and memory layout are perennial senior-level questions. Understanding *why* `+` in a loop is acceptable in Java 9+ (but still suboptimal), why `intern()` moved from PermGen to heap, and how Compact Strings reduced heap pressure by ~40% for ASCII-heavy workloads directly impacts production memory sizing decisions.

---

## 3. Core Principles

1. **Immutability** — `String` is `final`; its `byte[] value` is `final` and `private`; every "modification" creates a new `String` instance.
2. **String constant pool** — string literals are automatically interned; two literals with the same content share one object (`"a" == "a"` is `true`; `new String("a") == "a"` is `false`).
3. **Compact Strings (JEP 254)** — uses `byte[]` + a 1-byte `coder` field (`LATIN1=0`, `UTF16=1`). Strings that fit in ISO-8859-1 use 1 byte/char instead of 2 bytes/char (UTF-16). This halved heap usage for typical English applications.
4. **String table is on the heap** — since Java 7u40, interned strings reside in the main heap (not PermGen / Metaspace). They are GC-eligible; string table size is tunable via `-XX:StringTableSize`.
5. **Concatenation via `invokedynamic`** — since Java 9, the compiler emits a single `invokedynamic` call-site instead of a `new StringBuilder` chain. `StringConcatFactory.makeConcatWithConstants()` generates the actual strategy at link time.
6. **`StringBuilder` is for explicit multi-step mutation in a single thread**; `StringBuffer` is synchronized (rarely needed since Java 5; prefer `StringBuilder` + explicit locks).

---

## 4. Types / Architectures / Strategies

### 4.1 String Representation (Java 9+ Compact Strings)

```
                String object
       ┌────────────────────────────┐
       │ byte[] value               │  ← 1 byte/char (LATIN1) or 2 bytes/char (UTF16)
       │ byte   coder  (0 or 1)     │  ← LATIN1=0, UTF16=1
       │ int    hash   (cached)     │
       │ boolean hashIsZero         │
       └────────────────────────────┘
```

Java 8 and earlier used `char[] value` (always 2 bytes/char, even for pure-ASCII strings). The switch to `byte[]` in JEP 254 (Java 9) reduced heap usage for ASCII-heavy applications by ~40–50%.

### 4.2 String Constant Pool vs Heap

```
       Source                  Storage                  Lifecycle
  ─────────────────────────────────────────────────────────────────
  String literal "abc"    →  string table (heap)      GC-eligible (Java 7u40+)
  String.intern()         →  string table (heap)      GC-eligible
  new String("abc")       →  ordinary heap object     GC-eligible
  String.valueOf(42)      →  may or may not intern     depends on caching (Integer.toString
                                                        has a small cache for 0–9)
```

### 4.3 Concatenation Strategies

| Strategy | When Compiler Generates It | Notes |
|---|---|---|
| `invokedynamic` + `StringConcatFactory` | Java 9+, any `+` in source | Default strategy: `MH_INLINE_COPY` (most common) or `BC_SB_TOSTRING` |
| `new StringBuilder().append()...toString()` | Java 8 and below | Still valid to write explicitly |
| `StringBuilder` in a loop | Any version, explicit loop | Best for unknown N iterations |
| `String.join` / `Collectors.joining` | Stream/array join | Overhead: creates a `StringJoiner` internally |
| `String.format` / `formatted()` | Formatted output | 3–5x slower than `+` due to printf parsing |

### 4.4 Text Blocks (JEP 378, Java 15 GA)

```java
// Before Java 15
String json = "{\n" +
              "  \"id\": 1,\n" +
              "  \"name\": \"Alice\"\n" +
              "}";

// Java 15+
String json = """
              {
                "id": 1,
                "name": "Alice"
              }
              """;
```

Incidental whitespace is stripped based on the position of the closing `"""`. The result is a regular `String`; text blocks are a compile-time transformation.

---

## 5. Architecture Diagrams

### String Interning Flow

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    LIT(["\"hello\" literal"]) --> LDC["LDC #2\nload constant"]
    LDC --> LOOKUP{"string table\nlookup"}
    LOOKUP -->|found| RET(["return existing ref"])
    LOOKUP -->|missing| ALLOC["allocate on heap,\nadd to table"]
    ALLOC --> RET2(["return ref"])

    NEWSTR(["new String(\"hello\")"]) --> HEAP["new heap allocation\nalways a separate object"]

    class LIT,RET,RET2,NEWSTR io
    class LDC,ALLOC mathOp
    class LOOKUP req
    class HEAP frozen
```
Even though `"hello"` is already interned, `new String("hello")` always allocates a fresh heap object — the two paths never share a reference unless you explicitly call `.intern()`.

### Concatenation Bytecode (Java 9+)

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    SRC(["String result = a + \" \" + b;"])

    subgraph J8["Java 8 bytecode"]
        NSB["new StringBuilder"] --> INIT["invokespecial StringBuilder.&lt;init&gt;"]
        INIT --> AP1["aload a → invokevirtual append"]
        AP1 --> AP2["ldc \" \" → invokevirtual append"]
        AP2 --> AP3["aload b → invokevirtual append"]
        AP3 --> TOS["invokevirtual toString"]
    end

    subgraph J9["Java 9+ bytecode"]
        LA["aload a"] --> LB["aload b"]
        LB --> IDY["invokedynamic makeConcatWithConstants(...)\nsingle call-site; JVM picks strategy at link-time"]
    end

    SRC --> NSB
    SRC --> LA

    class SRC io
    class NSB,INIT,AP1,AP2,AP3 frozen
    class TOS mathOp
    class LA,LB frozen
    class IDY train
```

### Compact String Memory Layout

```
  Java 8  ("Hello"):   [ 'H','e','l','l','o' ]   → 5 chars × 2 bytes = 10 bytes for value
  Java 9+ ("Hello"):   [ 72, 101,108,108,111 ]   → 5 bytes for value (LATIN1 coder)
  Java 9+ ("Héllo"):   [ 0,72, 0,233, ... ]      → 10 bytes for value (UTF16 coder, 2 bytes/char)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 String Immutability Contract

```java
public final class String implements Serializable, Comparable<String>, CharSequence {
    @Stable private final byte[] value;   // @Stable = trusted final by JIT
    private final byte coder;             // LATIN1 = 0, UTF16 = 1
    private int hash;                     // lazily computed, benign data race (JMM §17.5)
    private boolean hashIsZero;
}
```

The `@Stable` annotation on `value` tells the JIT compiler to trust it as effectively constant, enabling aggressive scalar replacement and loop optimisations.

### 6.2 String.intern() Mechanics

```java
// intern() returns the canonical representation from the string table.
// Two strings with identical content yield the same reference after interning.

String s1 = new String("abc");
String s2 = new String("abc");

System.out.println(s1 == s2);           // false — two heap objects
System.out.println(s1.intern() == s2.intern()); // true — both return the table entry

// String table size default: 65536 buckets (power-of-2 hash table)
// Tune with: -XX:StringTableSize=1048576  (1M buckets for intern-heavy apps)
// Inspect: jcmd <pid> VM.stringtable statistics
```

**When intern() helps:** deduplicate millions of short, repeating strings (IP addresses, HTTP headers, enum-like constants from database). Not for arbitrary user input — unbounded growth fills the table.

**Java 8u20+ G1 String Deduplication** (`-XX:+UseStringDeduplication`): G1 automatically deduplicates `char[]`/`byte[]` backing arrays without programmer involvement. Different from interning (separate references, shared array).

### 6.3 StringBuilder Performance

```java
// BROKEN: O(n^2) due to quadratic string copies
String result = "";
for (String word : words) {
    result += word + " ";   // creates a new String each iteration
}

// FIX: O(n) — one buffer, one final toString()
StringBuilder sb = new StringBuilder(words.size() * 16); // pre-size the buffer
for (String word : words) {
    sb.append(word).append(' ');
}
String result = sb.toString();
```

**Capacity matters:** `StringBuilder` defaults to 16 characters. Each resize doubles capacity (like `ArrayList`). Pre-sizing to approximate final length avoids 3–5 resize + copy cycles for large outputs.

### 6.4 StringConcatFactory Strategies (Java 9+)

The JDK ships with three strategies (configurable via `-Djava.lang.invoke.stringConcat`):
- `BC_SB` — generates bytecode that uses `StringBuilder` (compatibility mode)
- `BC_SB_TOSTRING` — like `BC_SB` but inlines the final `toString()`
- `MH_INLINE_COPY` — **(default)** uses `MethodHandle` array pre-allocated to exact needed size, no intermediate `StringBuilder`, very cache-friendly

The default `MH_INLINE_COPY` strategy is faster than Java 8's `StringBuilder` chain for short, fixed-arity concatenations (e.g., `"id=" + id + " name=" + name`) because it knows the exact size upfront.

### 6.5 String.format vs formatted() vs MessageFormat

```java
// String.format — static, uses printf-style format string, ~3–5x slower than +
String s1 = String.format("Hello, %s! You are %d years old.", name, age);

// .formatted() — Java 15+, instance method, identical semantics, more readable in fluent chains
String s2 = "Hello, %s! You are %d years old.".formatted(name, age);

// MessageFormat — for localised, indexed placeholders; supports plural rules
String s3 = MessageFormat.format("Hello, {0}! You are {1,number,integer} years old.", name, age);

// Performance hierarchy (fastest to slowest for single invocation):
//   + (invokedynamic) > StringBuilder.append > String.format ≈ formatted() > MessageFormat
```

### 6.6 Text Blocks — Incidental Whitespace and Escape Sequences

```java
// The closing """ position determines how much leading whitespace is stripped.
String sql = """
        SELECT id, name
        FROM users
        WHERE active = true
        """;
// Strips 8 spaces of leading whitespace from each line (position of "SELECT").
// Trailing newline IS included (closing """ is on its own line).

// No trailing newline:
String inline = """
        SELECT *""";  // closing """ on same line as content = no trailing newline

// Escape sequences in text blocks:
String noNewline = """
        line1 \
        line2
        """;   // \<newline> continuation → "line1 line2\n"

String noTrailing = """
        trailing spaces here   \s
        """;   // \s anchor prevents trailing whitespace stripping
```

### 6.7 Unicode Correctness: chars() vs codePoints()

```java
String emoji = "Hello 😀";  // emoji is a surrogate pair (2 char values, 1 code point)

// BROKEN: iterates char values, surrogate pairs appear as 2 separate chars
emoji.chars()
     .forEach(c -> System.out.print((char) c));   // prints garbage for the emoji

// FIX: iterate code points — Unicode scalar values
emoji.codePoints()
     .forEach(cp -> System.out.print(new String(Character.toChars(cp))));

// String length vs code point count
emoji.length();           // 8 (6 chars for "Hello " + 2 chars for surrogate pair)
emoji.codePointCount(0, emoji.length()); // 7 (6 + 1 emoji)
```

---

## 7. Real-World Examples

### 7.1 HTTP Header Interning at Netty

Netty interns common HTTP header names (`"Content-Type"`, `"Authorization"`) so that all handlers share the same `String` reference. Header equality checks become `==` comparisons after the first access, saving both memory and CPU in hot paths processing thousands of requests per second.

### 7.2 JDK's internal String Deduplication

The JDK's `sun.nio.cs.StreamDecoder` caches charset names as interned strings. When parsing `"UTF-8"` from a `FileReader`, the charset name goes through the table so that 100 readers all share one `"UTF-8"` object rather than 100 independent copies.

### 7.3 Log4j 2 / Logback Pattern Caching

Logging frameworks cache compiled `MessagePattern` objects keyed by the pattern `String`. They rely on `String.equals()` for map key equality — not `==` — which is why `String.intern()` is not needed here. The lesson: use `intern()` only when `==` comparison speed matters AND you control all input paths.

### 7.4 Database ORM Column Name Interning

Hibernate interns entity field names during `SessionFactory` build-time. At runtime, reflection-based property access compares interned names with `==`, producing measurably faster property resolution in tight ORM loops mapping 50,000+ rows.

---

## 8. Tradeoffs

| Approach | Pros | Cons | Best For |
|---|---|---|---|
| `+` concatenation (Java 9+) | Readable; `StringConcatFactory` optimises fixed arity | Still creates objects per expression in complex loops | Short, readable one-liners outside loops |
| `StringBuilder` | Explicit control; best for N-iteration loops | Verbose; easy to forget `toString()` | Loops, dynamic content building |
| `StringBuffer` | Thread-safe without external sync | ~20–30% slower than `StringBuilder` due to synchronized; almost never needed | Only in legacy APIs requiring `StringBuffer` |
| `String.join` / `Collectors.joining` | Clean API for collections/arrays | Creates `StringJoiner` overhead; not for mixed content | Joining arrays/lists with delimiter |
| `String.format` / `formatted()` | Readable formatted output | 3–5x slower than `+`; parsing overhead each call | Debug messages, logs, human output |
| `MessageFormat` | Locale-aware, supports plurals | Slowest; verbose API | I18N / L10N requirements |
| `String.intern()` | Reduces heap for repeated strings | Risk of table overflow; moved to heap (GC-eligible) in Java 7u40+ | Controlled, finite set of known strings |
| G1 String Deduplication | Transparent; no code change needed | Only deduplicates `byte[]`/`char[]`; references remain separate | GC-managed dedup without code coupling |

---

## 9. When to Use / When NOT to Use

### Use `StringBuilder` when:
- Building a `String` in a loop with unknown or large N (> ~3 concatenations)
- Constructing SQL/HTML/JSON by parts in a method
- Appending to a growing buffer in a generator or formatter class

### Use `+` (invokedynamic) when:
- 2–5 fixed-arity concatenations in one expression
- Readability is more important than micro-performance
- Java 9+ (the `StringConcatFactory` makes it safe)

### Use `String.format` / `formatted()` when:
- Output format is important and `printf`-style placeholders aid clarity
- Not in a hot path (avoid in tight loops processing >100k items/sec)

### Use `String.intern()` when:
- You have a finite, known set of strings (enum-like constants from external data)
- You are implementing a `Symbol` or `Name` type where `==` identity checks are a performance feature
- NOT for arbitrary user input — unbounded interning causes memory pressure and GC pauses

### Do NOT use `StringBuffer` unless:
- A legacy API requires it (e.g., `append(StringBuffer)` method signature you cannot change)
- You are targeting pre-Java 5 environments (irrelevant for any modern work)

---

## 10. Common Pitfalls

### Pitfall 1: `+` in a loop compiling to quadratic copies (Java 8)
```java
// Java 8 BROKEN: each += creates a new String, copying all previous content
String log = "";
for (LogEntry e : entries) {
    log += e.getMessage() + "\n";  // O(n^2) memory allocations
}
// Fix: StringBuilder (all versions) or the fact that Java 9+ inlines BUT only for fixed-arity;
// a loop body is NOT fixed-arity, so even Java 9 emits a new StringBuilder per iteration here.
StringBuilder log = new StringBuilder();
for (LogEntry e : entries) {
    log.append(e.getMessage()).append('\n');
}
```

### Pitfall 2: Comparing strings with `==`
```java
// BROKEN
String input = request.getParameter("action");
if (input == "delete") {  // false — input is a new heap object, not the literal
    performDelete();
}

// FIX
if ("delete".equals(input)) {  // null-safe; uses .equals()
    performDelete();
}
```

### Pitfall 3: Interning unbounded data
```java
// BROKEN: username comes from HTTP — unbounded; attacker can flood the string table
String user = username.intern();  // denial-of-service via string table overflow

// FIX: only intern known-finite sets
private static final Map<String, String> ALLOWED_HEADERS = Map.of(
    "content-type", "content-type".intern(),
    "authorization", "authorization".intern()
);
```

### Pitfall 4: substring memory leak (Java 6 and earlier)
In Java 6 and earlier, `substring()` kept a reference to the original `char[]` backing array. Long strings in memory for the lifetime of a small substring. Fixed in Java 7u6 by making `substring()` copy the backing array.

### Pitfall 5: `charAt()` on emoji / surrogate pairs
```java
// BROKEN: emoji is 2 chars, but one code point
String s = "A😀B";
System.out.println(s.charAt(1));  // '\uD83D' — high surrogate, not printable
System.out.println(s.length());   // 4, not 3

// FIX: use codePoints() or handle surrogate pairs explicitly
s.codePoints().forEach(cp -> System.out.print(new String(Character.toChars(cp))));
```

### Pitfall 6: `String.format` in hot logging paths
```java
// BROKEN: formats the string even if log level is INFO and debug is off
logger.debug("Processing record: " + record.toString());  // Java 8 style, still builds String

// FIX: use SLF4J parameterised logging (defers toString until level is enabled)
logger.debug("Processing record: {}", record);
```

---

## 11. Technologies & Tools

| Tool / Feature | Version | Purpose |
|---|---|---|
| `StringConcatFactory` | Java 9 (JEP 280) | `invokedynamic` bootstrap for `+` operator |
| Compact Strings | Java 9 (JEP 254) | `byte[]` + coder; halves memory for ASCII strings |
| Text Blocks | Java 13 preview → Java 15 GA (JEP 378) | Multi-line string literals with incidental whitespace stripping |
| String Templates | Java 21 preview (JEP 430) → Java 25 (proposed GA) | Type-safe `STR."Hello \{name}"` interpolation |
| `G1StringDeduplication` | Java 8u20+ | `-XX:+UseStringDeduplication`; G1-only, deduplicates byte[] |
| `String.intern()` | All versions | Returns canonical instance; table on heap since Java 7u40 |
| `jcmd VM.stringtable` | Java 7+ | Dumps string table stats (count, table size, histogram) |
| `String.chars()` | Java 8 | Returns `IntStream` of `char` values (UTF-16 code units) |
| `String.codePoints()` | Java 8 | Returns `IntStream` of Unicode code points (correct for emoji) |
| `String.isBlank()` | Java 11 | Returns `true` if empty or whitespace only (`Character.isWhitespace`) |
| `String.strip()` | Java 11 | Unicode-aware trim; use over `trim()` which only handles `<= U+0020` |
| `String.repeat(n)` | Java 11 | Repeats string n times; uses `Arrays.copyOf` internally |
| `String.indent(n)` | Java 12 | Adjusts leading whitespace, normalises line endings |
| `String.formatted(args)` | Java 15 | Instance version of `String.format`; same performance |
| Apache Commons Text | Library | `StringUtils`, `WordUtils`, `StrSubstitutor` for advanced manipulation |

---

## 12. Interview Questions with Answers

**Q1: Why is `String` immutable in Java, and what would break if it were mutable?**
`String` is immutable so it can be safely shared across threads, used as a `HashMap` key, and placed in the string constant pool without copying. If `String` were mutable, a cached `hashCode()` would become stale after modification, map lookups would fail silently, and a class name passed to `ClassLoader.loadClass()` could be mutated mid-load to trigger privilege escalation — a well-known historical security concern. Immutability gives compile-time guarantees the JIT exploits via `@Stable` on the backing array.

**Q2: What is the difference between `new String("abc")` and `"abc"`?**
`"abc"` is a compile-time literal; the JVM automatically interns it — the first time it is loaded, one object is created in the string table and all references to `"abc"` share it. `new String("abc")` bypasses the table and allocates a fresh `String` object on the heap; `==` against the literal returns `false` even though `.equals()` returns `true`. The literal in the constructor argument is still interned, but the result of `new String(...)` is not. To force the heap object into the table, call `.intern()`.

**Q3: What changed in Java 9 regarding how String concatenation is compiled?**
Before Java 9, the compiler translated `a + b + c` into `new StringBuilder().append(a).append(b).append(c).toString()` — fixed bytecode. In Java 9 (JEP 280), this became a single `invokedynamic` call-site bootstrapped by `StringConcatFactory.makeConcatWithConstants()`. The JVM selects the best strategy at link-time (`MH_INLINE_COPY` by default): it pre-allocates an exact-size `byte[]`, copies all parts in, and wraps it without an intermediate `StringBuilder`. This is faster for fixed-arity expressions and produces smaller bytecode. However, a `+` inside a loop still emits a `StringConcatFactory` call per iteration — `StringBuilder` is still needed for loops.

**Q4: Explain Compact Strings (JEP 254). How do they save memory, and when are they NOT active?**
Compact Strings (Java 9, JEP 254) store `String` content in a `byte[]` instead of `char[]`. When all code points fit in ISO-8859-1 (Latin-1), the `coder` byte is `LATIN1 (0)` and each character uses 1 byte. When any character requires UTF-16, `coder` is `UTF16 (1)` and each character uses 2 bytes. For typical English-language applications (logs, SQL, HTTP headers, JSON keys), ~90–95% of strings are LATIN1, halving their memory footprint compared to Java 8. Compact Strings are NOT active when: (a) `-XX:-CompactStrings` is set, (b) the string contains any character outside ISO-8859-1 (code point > 255), or (c) building on Java 8 or earlier.

**Q5: When should you use `String.intern()`, and what are the risks?**
Use `intern()` when you have a large, finite, controlled set of repeated strings — HTTP header names, column names from a fixed schema, enum-like values from a configuration file — where `==` comparison speed provides measurable benefit. The risks: (1) the string table is bounded; flooding it with arbitrary user input causes memory pressure and long GC pauses; (2) interned strings were in PermGen (fixed size) before Java 7u40 — in modern Java they are heap-resident and GC-eligible, but the table itself is a hash table with modest default capacity (65,536 buckets); resize with `-XX:StringTableSize`; (3) profiling with `jcmd <pid> VM.stringtable` is essential before committing to an interning strategy.

**Q6: What is G1 String Deduplication and how does it differ from interning?**
G1 String Deduplication (`-XX:+UseStringDeduplication`, Java 8u20+, G1 only) transparently finds `String` objects on the heap whose `byte[]` backing arrays contain identical content and replaces all but one of the arrays with a shared reference. Unlike interning, the `String` object references remain separate — `==` between deduplicated strings is still `false`. Deduplication is transparent to the application and costs GC work rather than application CPU. It is most effective in applications with many short, repeated strings created via `new String(...)` or deserialization.

**Q7: Show the memory layout difference between `String` in Java 8 and Java 9+.**
Java 8: `String` holds `char[] value` (each `char` = 2 bytes, regardless of actual content). `"hello"` backs to `char[5]` = 10 bytes.
Java 9+: `String` holds `byte[] value` + `byte coder`. `"hello"` (pure ASCII): `byte[5]` = 5 bytes, `coder = 0 (LATIN1)`. `"héllo"` (one non-ASCII): `byte[10]` = 10 bytes, `coder = 1 (UTF16)`. The object header (~16 bytes) and `hash` / `hashIsZero` fields are present in both.

**Q8: Why is `StringBuilder` not thread-safe, and how do you handle multi-threaded string building?**
`StringBuilder` has no synchronisation — `append()` is not atomic: it checks capacity, possibly reallocates the buffer, then copies bytes. Two threads calling `append()` concurrently can interleave these steps, producing garbled output or an `ArrayIndexOutOfBoundsException`. For multi-threaded building: (1) use `StringBuffer` (all methods synchronized, ~20–30% slower), (2) use a per-thread `StringBuilder` and combine results at the end (best performance), or (3) use a `ConcurrentLinkedDeque<String>` + single-threaded join step. In practice, option (2) via thread-local or stream-style aggregation is almost always preferred over `StringBuffer`.

**Q9: What does a text block look like after the compiler processes it, and how is incidental whitespace stripped?**
Text blocks are a compile-time transformation (JEP 378). The compiler: (1) line-terminates consistently (CRLF/CR → LF); (2) strips "incidental whitespace" — defined as the longest common leading-whitespace prefix across all non-blank content lines and the closing-`"""` line; (3) strips trailing whitespace from each line (use `\s` escape to preserve intentional trailing spaces); (4) interprets `\<newline>` as a line continuation. The result is a regular `String` constant — text blocks have zero runtime overhead. Two text blocks with the same content after processing are the same compile-time constant and may be interned like any literal.

**Q10: What is the difference between `String.chars()` and `String.codePoints()`, and when does it matter?**
`chars()` returns an `IntStream` of UTF-16 code units (Java `char` values, 16-bit). For code points in the Basic Multilingual Plane (U+0000–U+FFFF), one code unit equals one code point. For supplementary characters (e.g., emoji, CJK Extension B), one code point spans two `char` values (a surrogate pair, U+D800–U+DFFF). `codePoints()` returns an `IntStream` of actual Unicode code points — it always gives one `int` per logical character. Use `codePoints()` whenever the input may contain emoji, symbols from supplementary planes, or when implementing Unicode-correct string length, reversal, or iteration.

**Q11: How would you efficiently reverse a `String` that may contain emoji?**
```java
// BROKEN: reverses char values, splits surrogate pairs
String reversed = new StringBuilder("Hello 😀").reverse().toString();
// "reverse()" in StringBuilder IS smart about surrogates (it swaps them back)
// but a naive char-by-char approach would break them:
char[] chars = s.toCharArray();
// ... swap chars[i] with chars[n-1-i] → splits surrogate pairs

// FIX: code-point-aware reversal
public static String reverseCPAware(String s) {
    int[] cps = s.codePoints().toArray();
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = cps.length - 1; i >= 0; i--) {
        sb.appendCodePoint(cps[i]);
    }
    return sb.toString();
}
// Note: StringBuilder.reverse() handles surrogate pairs correctly, but be explicit
// in an interview to show you know *why*.
```
`StringBuilder.reverse()` does handle surrogate pairs internally. Knowing this distinction is what separates correct Unicode handling from accidental correctness.

**Q12: What are the four String comparison methods and their correct use cases?**
`==` compares object identity (reference equality) — use only when comparing interned strings or the exact same reference. `.equals()` compares content character by character — use for all user-facing equality. `.equalsIgnoreCase()` normalises both sides via `Character.toLowerCase()` with the default locale — avoid for locale-sensitive comparison (`"i".equalsIgnoreCase("I")` is `false` in Turkish locale). `.compareTo()` / `.compareToIgnoreCase()` gives a lexicographic ordering — use for sorting. For locale-correct comparison (sorting names, user input), use `java.text.Collator` which honours locale-specific ordering rules (e.g., accented characters in French, ß in German).

**Q13: How does `String.hashCode()` work, and why can a hash code be 0 for a non-empty string?**
`hashCode()` computes `s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]` modulo the `int` overflow boundary (31 is a small prime that reduces collisions). The result is cached in the `hash` field after first computation. Java 8 has a race (two threads compute it simultaneously) — both compute the same value, making the benign data race safe by the JMM's guarantee that `int` writes are atomic and the computation is deterministic. In Java 9+, a second flag `hashIsZero` is added: if the hash computes to exactly 0, it is stored as 0 and `hashIsZero = true` prevents re-computation on every call. Practical implication: never rely on `hash == 0` to mean "not computed."

**Q14: How does `String.strip()` differ from `String.trim()`, and why should you prefer `strip()` in Java 11+?**
`trim()` removes leading and trailing characters with code point ≤ U+0020 (space and ASCII control characters). `strip()` (Java 11, JEP 340 Unicode) uses `Character.isWhitespace()`, which additionally removes Unicode whitespace like U+00A0 (no-break space), U+2009 (thin space), U+3000 (ideographic space), and others. Modern web and database content often includes these Unicode whitespace characters from copy-paste, rich-text editors, or non-English sources. `trim()` on such a string returns a string that still looks padded to the user. `stripLeading()` and `stripTrailing()` are available for asymmetric stripping. Prefer `strip()` for all new code on Java 11+.

**Q15: Describe three places in the JDK where `String` interning or constant pooling provides a concrete performance benefit.**
(1) **Class loading**: class and package names are interned during class loading. `Class.getName()` returns an interned string, allowing the class loader's internal hash maps to use `==` for key comparison. (2) **`Boolean.toString()`**: returns `"true"` or `"false"`, both compile-time literals that are already interned — no allocation occurs. Similarly `Integer.toString(0)` through `Integer.toString(9)` use cached constants. (3) **Enum name constants**: every enum's `.name()` field is initialised from a string literal interned at compile time. Two references to `Status.ACTIVE.name()` return the same `String` object, so `==` comparisons in switch expressions on enum names are safe (though `.equals()` is still safer for external input).

---

## 13. Best Practices

1. **Prefer `+` for ≤5 fixed parts; use `StringBuilder` for loops** — the JIT optimises small fixed-arity concatenation but cannot hoist loop-body `String` allocations.
2. **Use `"literal".equals(variable)` pattern** — avoids `NullPointerException`; `null.equals(...)` would throw, `"literal".equals(null)` returns `false`.
3. **`String.strip()` over `String.trim()`** in Java 11+ — Unicode-correct whitespace removal.
4. **`String.isBlank()` over `.isEmpty()`** when whitespace-only strings count as "empty" for your use case.
5. **Use `codePoints()` for any Unicode iteration** that must be correct for emoji and supplementary characters.
6. **Limit `intern()` to finite, controlled string sets**; monitor the table with `jcmd VM.stringtable`.
7. **Enable G1 String Deduplication** (`-XX:+UseStringDeduplication`) when heap analysis shows >5% of live heap is duplicate string data (check with `jmap -histo`).
8. **Pre-size `StringBuilder`** when the final length is predictable: `new StringBuilder(expectedLength)` avoids resize copies.
9. **Text blocks for multi-line literals** (Java 15+): no escaping of inner `"`, incidental whitespace is stripped, content is version-control friendly (no `\n` noise).
10. **For locale-sensitive string comparison** (user-facing sort, search), use `java.text.Collator.getInstance(locale)` rather than `compareTo()` / `equalsIgnoreCase()`.

---

## 14. Case Study

**Scenario: Bulk log-line builder in a high-throughput audit service**

A financial audit service processes 50,000 events per second, building a structured log line per event for an immutable audit trail. Initial code used `String.format()` inside the hot loop.

**Before (production incident — 18% CPU on string formatting):**
```java
public String buildAuditLine(AuditEvent e) {
    return String.format(
        "[%s] user=%s action=%s resource=%s result=%s duration=%dms",
        e.getTimestamp(), e.getUserId(), e.getAction(),
        e.getResource(), e.getResult(), e.getDurationMs()
    );
}
// Called 50,000 × per second → 50,000 × printf parse overhead
```

**After (profiling-driven fix):**
```java
private static final int AVG_LINE_LEN = 120;

public String buildAuditLine(AuditEvent e) {
    return new StringBuilder(AVG_LINE_LEN)
        .append('[').append(e.getTimestamp()).append("] user=")
        .append(e.getUserId()).append(" action=")
        .append(e.getAction()).append(" resource=")
        .append(e.getResource()).append(" result=")
        .append(e.getResult()).append(" duration=")
        .append(e.getDurationMs()).append("ms")
        .toString();
}
// Zero printf parsing; pre-sized buffer; single toString() allocation.
// Result: 18% CPU → 4% CPU for string building; allocation rate -60%
```

**Measurement (JMH, Java 21, M2 Pro, 50k calls/sec simulated):**
```
Benchmark                    Mode  Cnt   Score   Error  Units
StringFormat.format          avgt   10   412 ±   8  ns/op
StringFormat.sbAppend        avgt   10    78 ±   2  ns/op
StringFormat.invokedynamic   avgt   10   105 ±   3  ns/op  (+ operator, 6 parts)
```

`StringBuilder.append()` is ~5x faster than `String.format()` for fixed-arity, high-frequency log building. For low-frequency human-readable output, `String.format()` remains preferable for clarity.

**See also:**
- [Performance and Tuning](../performance_and_tuning/README.md) — JMH benchmarking methodology
- [JVM Internals](../jvm_internals/README.md) — object header layout, GC impact of allocation rate

---

## Related / See Also

- [Core Language](../core_language/README.md) — Java object model, immutability contract, equals/hashCode for String
- [Java 9–21 Features](../java9_to_21_features/README.md) — text blocks (JEP 378, Java 15), invokedynamic string concat (JEP 280)
- [Performance & Tuning](../performance_and_tuning/README.md) — String allocation cost, StringBuilder vs String.format benchmarks with JMH
- [Java Memory Model](../java_memory_model/README.md) — `hash` field benign data race in `String.hashCode()`
- [Arrays, Strings & Hashing](../../cs_fundamentals/arrays_strings_and_hashing/README.md) — language-agnostic string/hashing fundamentals underlying `String.hashCode()` and interning
