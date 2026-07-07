# How Code Runs — Compilers & Interpreters

> A compiler is a translator who finishes the whole book before it ships; an interpreter stands next to the reader, translating one line at a time as it is read.

---

## 1. Concept Overview

Every program starts as text written in a language designed for humans to read — Python, Java, C, Rust — and must end up as something a CPU can execute directly, or as a sequence of steps another program carries out on the programmer's behalf. "How code runs" is the story of that transformation: who performs it, when they perform it, and how much of the work is done once versus repeated on every execution.

Every serious language implementation, whatever it is called, is built from the same three stages. A **front end** understands the source language: it turns characters into tokens (lexing), tokens into a tree that represents the program's structure (parsing into an AST), and then checks that the tree actually makes sense (semantic analysis — scopes, symbol tables, type checking). A **middle end** lowers that tree into an intermediate representation (IR) that no longer looks like any particular source language, and improves it with optimization passes such as constant folding, dead-code elimination, and inlining. A **back end** turns the optimized IR into something a specific target can run: machine instructions for a specific CPU architecture (instruction selection, register allocation, code generation), or bytecode for a specific virtual machine. What separates a "compiler" from an "interpreter" is not whether these three stages exist — they all do, in some form — but when the back end's output is produced and how it is finally executed.

Producing machine code is not the same as running it. The **linker** combines multiple independently compiled pieces — object files, static archives, shared libraries — into one program image and resolves the address of every function and global variable referenced across those pieces. The **loader** is the operating-system component that reads that image and turns it into a live process: mapping its segments into virtual memory with the right permissions, building the initial stack, and handing control to the entry point. Underneath both sits the **ABI** (Application Binary Interface): the low-level contract, stricter than the language's own API, for exactly how a function call passes arguments, returns a value, and which registers must survive the call — the reason code compiled by different compilers, or written in different languages entirely, can call each other at all.

---

## 2. Intuition

> **One-line analogy**: a compiler is a translator who translates an entire novel before publication — all the cost is paid once, upfront, and the reader gets a finished book in their own language. An interpreter is a simultaneous interpreter at a live conference, translating sentence by sentence as the speaker talks — every session pays the translation cost again. A bytecode VM is a translator who pre-translates the novel into compact shorthand notes and reads the notes aloud at the event — cheaper than live translation, but still not the original language. A JIT is that same live interpreter noticing the speaker keeps repeating one paragraph, and mid-conference writing a polished, fast translation of just that paragraph so future repeats are instant.

**Mental model**: picture one pipeline that every implementation pushes a program through — `source text -> tokens -> AST -> IR -> optimized IR -> target code` — and ask two questions about any concrete language implementation. First, *how far along this pipeline does work happen before the program is ever run* — all the way to native machine code (ahead-of-time), or only to an AST or bytecode (the rest happens at run time)? Second, *is the final output executed directly by hardware, or read and carried out by another program?* Every compiler, interpreter, bytecode VM, and JIT you will be asked about is a specific pair of answers to those two questions.

**Why it matters**: where translation happens governs two numbers engineers argue about constantly — **startup latency** (how long until the first request is served) and **steady-state throughput** (how fast the program runs once it has been running a while). A serverless function that scales to zero and cold-starts on every burst cares enormously about startup latency; a database server that runs for weeks can happily pay a warmup cost once and cares only about steady-state throughput. This single axis explains why GraalVM Native Image trades away a JIT's peak throughput for a 10-50 ms cold start, and why JVM code that is sluggish on its first call can end up faster than naively-compiled C once HotSpot's C2 compiler has profiled and re-compiled the hot loop using data that did not exist at build time.

**Key insight — the "an interpreter is just a slow compiler" misconception**: this framing is wrong on both halves. An interpreter is not "slow" as an inherent property of interpreting — CPython's bytecode interpreter is slow mainly because of dynamic typing and reference-counted objects on every operation, not because it interprets rather than compiles; a bytecode interpreter over a statically-typed IR is far faster than a naive tree-walking interpreter, and CPython 3.11's adaptive specialization made the *same* interpreter loop measurably faster without adding a JIT at all. And an interpreter is not "a compiler that runs later" either — a compiler produces a complete, standalone artifact meant to be handed to hardware once; an interpreter's job is to *be* the execution engine, reading and acting on a program representation step by step, every single run, forever. The real axis is *when translation happens and who executes the result* — not "compilers think, interpreters just wing it."

---

## 3. Core Principles

- **Translation always happens somewhere.** The question that actually distinguishes implementations is *when* — build time, load time, or run time — not *whether* translation occurs at all.
- **Front ends are source-language-specific; back ends are target-specific.** The IR between them is what lets one front end feed many back ends (Clang's C/C++ front end emits LLVM IR consumed by x86, ARM, RISC-V, and WebAssembly back ends) and one back end serve many front ends (C, Rust, Swift, and Zig all lower to LLVM IR and reuse the same code generator).
- **An optimization is any IR rewrite that is provably semantics-preserving and improves some cost metric** — speed, code size, or energy. "Provably" is the operative word: an incorrect optimization is a correctness bug wearing a performance disguise.
- **SSA (static single assignment) form gives every variable exactly one definition**, turning dataflow questions ("what value could `x` hold here?") into simple graph-reachability questions — which is why LLVM, GCC's internal IRs, the JVM's C2 compiler, and V8's TurboFan all convert to SSA internally even though no source language ever mentions it.
- **Static vs. dynamic linking is the same "when is this resolved" axis, applied to whole modules instead of instructions.** A statically linked symbol's address is fixed at link time; a dynamically linked symbol's address is fixed only when its shared library is actually loaded, and can even be re-resolved lazily on first call.
- **The ABI is a stricter, lower-level contract than the language's API.** Two functions can have "the same" signature in source code and still be ABI-incompatible if compiled with different struct-padding rules, calling conventions, or name-mangling schemes — which is exactly why `extern "C"` exists.
- **Everything past the front end can, in principle, be shared infrastructure.** This is why JVM bytecode, LLVM IR, and WebAssembly all exist as stable, well-specified "middle" targets: many front ends compile into them and many back ends (interpreters, JITs, AOT compilers) consume them, so a new language does not need to write its own optimizer and code generator from scratch.

---

## 4. Types / Architectures / Strategies

### 4.1 Execution Strategies

| Strategy | When translation happens | What actually executes | Concrete example | Typical cold start | Typical steady-state throughput |
|---|---|---|---|---|---|
| Ahead-of-time (AOT) native compiler | Entirely before the program runs (build time) | The CPU executes machine code directly | `gcc -O2 file.c -o prog`, `rustc`, `go build` | ~1-5 ms process start | Fixed at the chosen optimization level; cannot improve further at run time |
| Tree-walking interpreter | Only parsing happens ahead of time; each AST node is re-examined on every visit | A host program recursively walks the AST and performs each node's action | Textbook `eval`-style interpreters, early Ruby MRI (< 1.9) | ~0 ms (nothing to compile) | Slowest tier — re-traverses the same tree node on every loop iteration |
| Bytecode interpreter | Source compiled to compact bytecode once, at first import/load | A dispatch loop fetches and executes one bytecode instruction at a time | CPython's `ceval.c` loop over `.pyc` bytecode, Ruby's YARV | ~10-60 ms interpreter/runtime init | Faster than tree-walking (flat instruction stream), still 10-100x slower than native code on tight numeric loops |
| Bytecode VM + JIT | Bytecode ahead of time; hot bytecode re-compiled to native machine code *during* the run, using data observed while running | Cold code: interpreted. Hot code: natively executed, speculatively optimized, with a bailout path back to the interpreter | JVM HotSpot (C1/C2 tiers), V8 (Ignition + Sparkplug + TurboFan), PyPy | Interpreter speed at first call; ~100 ms to a few seconds to reach peak | Can match or exceed statically compiled code on polymorphic hot code, because it optimizes using real runtime type and branch data unavailable at build time |
| AOT compilation of a managed/bytecode language | The whole program, assumed closed-world, is compiled to native code before it ships, using build-time static analysis instead of runtime profiling | The CPU executes machine code directly, same as row 1, but produced from bytecode instead of a systems language | GraalVM Native Image, .NET Native AOT | ~10-50 ms | Usually below a fully warmed-up JIT's ceiling (no runtime profile-guided speculation), but reached instantly and far above interpreter speed |

### 4.2 Static vs. Dynamic Linking

| Aspect | Static linking | Dynamic linking |
|---|---|---|
| When symbols are resolved | Link time (build) | Load time — sometimes lazily, on first call |
| Where library code lives | Copied into the final executable | Stays in a separate `.so` / `.dll` / `.dylib`, mapped in at load time |
| Binary size | Larger — includes every used library function | Smaller — the library is shared, not duplicated |
| Cross-process memory sharing | None — every process holds its own copy | Shared — the OS maps one physical copy of `libc.so` into every process using it |
| Patching a bug in a library | Requires recompiling and redeploying every consumer | Replace the `.so` once; every consumer picks it up on its next load |
| Failure visibility | Caught at link time (`undefined reference`) | Can be deferred all the way to run time — a missing lazily-bound symbol is only discovered the first time that function is actually called |
| Typical use | Go binaries, static musl/Alpine builds, single hermetic container artifacts | Standard Linux/Windows/macOS executables against system libc, GUI toolkits, plugin architectures |

---

## 5. Architecture Diagrams

### The Full Pipeline: Source Text to Running Process

```
SOURCE TEXT   (e.g. "x = 2 + 3 * 4")
    |
    v
FRONT END  -- source-language specific; rejects malformed programs
    1. Lexer               characters -> tokens
    2. Parser              tokens     -> AST
    3. Semantic analysis   AST        -> AST + symbol table (scopes, types checked)
    |
    v
MIDDLE END  -- target-independent; same shape for every source language
    4. Lower to IR         AST -> three-address code / SSA
    5. Optimize             constant folding, dead-code elimination, inlining, ...
    |
    v
BACK END  -- target-machine specific; one per CPU architecture
    6. Instruction selection   IR          -> machine instructions
    7. Register allocation      temporaries -> physical registers (or stack spill)
    8. Code generation          -> object code (a .o file)
    |
    v
LINKER    merges every .o file and library, resolves each symbol to an address,
          then relocates (patches) every reference now that layout is final
    |
    v
LOADER (operating system)   maps segments into a fresh process's virtual memory,
          builds the initial stack, jumps to the entry point
    |
    v
RUNNING PROCESS
```

This is the same pipeline whether the implementation is called a "compiler" or an "interpreter" — the difference is only how far along it a given piece of code travels before it is first executed, and whether the box that finally executes it is the CPU or another program's dispatch loop.

### Tracing One Line Through Every Representation

```
source:   x = 2 + 3 * 4

tokens:   [NAME x] [OP =] [NUM 2] [OP +] [NUM 3] [OP *] [NUM 4]

AST (precedence already resolved -- '*' binds tighter than '+'):
              =
             / \
            x   +
               / \
              2   *
                 / \
                3   4

IR, three-address code (one operation per line):
    t1 = 3 * 4
    t2 = 2 + t1
    x  = t2

after constant folding (both operands of each op are known at compile time):
    x = 14

x86-64 assembly, Intel syntax (x lives at [rbp-8] after register allocation):
    mov  [rbp-8], 14      ; x = 14 -- computed once, at compile time, never at run time
```

### Where Is the Translation Cost Paid? (BUILD -- LOAD -- RUN)

```
AOT native (C, Rust, Go):
  BUILD [====================]  LOAD [-]  RUN [.............................]
        all translation cost here                 zero re-translation, ever

Bytecode interpreter (CPython):
  BUILD [==]  LOAD [-]  RUN [tttttttttttttttttttttttttttttttttttttttttttttttt]
        parse to .pyc            every single bytecode re-interpreted, every run

Bytecode VM + JIT (JVM, V8):
  BUILD [==]  LOAD [-]  RUN [ttttt][=====][.............................]
        parse to bytecode        interpret profile JIT-compile  native from here on,
                                  (cold)   (warmup) (hot code)  lost again on exit

AOT native image (GraalVM Native Image):
  BUILD [========================]  LOAD [-]  RUN [.............................]
        compile + closed-world analysis here     zero re-translation, ever

  legend:  [=] one-time heavy cost   [t] interpreted execution   [.] native execution
```

The bytecode-VM-plus-JIT row is the only one that pays translation cost *twice* — once at build time (to bytecode) and again at run time (bytecode to native, but only for the code that turns out to be hot) — which is exactly why it is the only strategy whose performance depends on how long the process lives.

### Process Virtual Memory Right After the Loader Runs

```
  high address
  +------------------------------------------+
  |  kernel space (not visible to user code)  |
  +------------------------------------------+
  |  stack              (grows downward)      |  <- argv, envp, auxv land here first
  +------------------------------------------+
  |  mmap region         shared libraries,    |  <- ld.so maps each needed .so here
  |                       anonymous mappings  |
  +------------------------------------------+
  |  heap               (grows upward)        |  <- malloc/new; empty until first use
  +------------------------------------------+
  |  .bss    zero-filled by the loader        |  <- size known from the ELF, no bytes to copy
  +------------------------------------------+
  |  .data   bytes copied from the file       |
  +------------------------------------------+
  |  .rodata mapped read-only                 |
  +------------------------------------------+
  |  .text   mapped read + execute            |  <- this is what "running the code" means
  +------------------------------------------+
  low address

The loader learns WHERE and with WHAT permissions to map each of these from the
ELF's Program Header table (segments) -- not the section header table, which the
linker and debuggers use but the loader never even reads.
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Lexing — Turning Characters Into Tokens

```python
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum, auto


class TokKind(Enum):
    NUM = auto()
    NAME = auto()
    PLUS = auto()
    MINUS = auto()
    STAR = auto()
    SLASH = auto()
    LPAREN = auto()
    RPAREN = auto()
    ASSIGN = auto()
    EOF = auto()


@dataclass
class Token:
    kind: TokKind
    text: str


_SINGLE = {
    "+": TokKind.PLUS, "-": TokKind.MINUS, "*": TokKind.STAR, "/": TokKind.SLASH,
    "(": TokKind.LPAREN, ")": TokKind.RPAREN, "=": TokKind.ASSIGN,
}


def lex(source: str) -> list[Token]:
    """Character stream -> token stream, a single O(n) pass. Real lexers are
    generated from regular expressions compiled to a DFA (the theory behind
    flex/re2c); this hand-written loop implements the same idea directly."""
    tokens: list[Token] = []
    i, n = 0, len(source)
    while i < n:
        ch = source[i]
        if ch.isspace():
            i += 1
        elif ch.isdigit():
            j = i
            while j < n and source[j].isdigit():
                j += 1
            tokens.append(Token(TokKind.NUM, source[i:j]))
            i = j
        elif ch.isalpha():
            j = i
            while j < n and source[j].isalnum():
                j += 1
            tokens.append(Token(TokKind.NAME, source[i:j]))
            i = j
        elif ch in _SINGLE:
            tokens.append(Token(_SINGLE[ch], ch))
            i += 1
        else:
            raise SyntaxError(f"unexpected character {ch!r} at position {i}")
    tokens.append(Token(TokKind.EOF, ""))
    return tokens
```

`lex("x = 2 + 3 * 4")` produces exactly the token list shown in the section 5 trace diagram. Notice what the lexer does *not* decide: it has no idea that `*` binds tighter than `+`, or that `x` is being assigned rather than compared — those are the parser's and semantic analyzer's problems, respectively. A lexer's only job is to turn a character stream into the smallest meaningful chunks.

### 6.2 Parsing — Building the AST With Recursive Descent

```python
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Num:
    value: int


@dataclass
class Var:
    name: str


@dataclass
class BinOp:
    op: str
    left: "Node"
    right: "Node"


@dataclass
class Assign:
    name: str
    value: "Node"


Node = Num | Var | BinOp | Assign


class Parser:
    """Recursive-descent parser: each grammar rule becomes one method, and
    the call stack IS the parse. This is why pathologically deep input (a
    thousand nested parentheses) can overflow the parser's own call stack --
    a real, recurring bug class in hand-written parsers."""

    def __init__(self, tokens: list[Token]) -> None:
        self.tokens = tokens
        self.pos = 0

    def _peek(self) -> Token:
        return self.tokens[self.pos]

    def _advance(self) -> Token:
        tok = self.tokens[self.pos]
        self.pos += 1
        return tok

    def parse_statement(self) -> Node:
        name = self._advance()                 # NAME
        self._advance()                        # '='
        return Assign(name.text, self.parse_expr())

    def parse_expr(self) -> Node:               # lowest precedence: + and -
        node = self.parse_term()
        while self._peek().kind in (TokKind.PLUS, TokKind.MINUS):
            op = self._advance().text
            node = BinOp(op, node, self.parse_term())
        return node

    def parse_term(self) -> Node:                # higher precedence: * and /
        node = self.parse_factor()
        while self._peek().kind in (TokKind.STAR, TokKind.SLASH):
            op = self._advance().text
            node = BinOp(op, node, self.parse_factor())
        return node

    def parse_factor(self) -> Node:               # numbers, names, ( expr )
        tok = self._advance()
        if tok.kind == TokKind.NUM:
            return Num(int(tok.text))
        if tok.kind == TokKind.NAME:
            return Var(tok.text)
        if tok.kind == TokKind.LPAREN:
            node = self.parse_expr()
            self._advance()                     # ')'
            return node
        raise SyntaxError(f"unexpected token {tok}")
```

`parse_expr` calling `parse_term` calling `parse_factor` is precedence, encoded directly as nesting depth — no separate precedence table is consulted (contrast with operator-precedence / Pratt parsing, which does look one up). This parser rejects `2 + * 3` at `parse_factor` — a syntax error, caught by the front end. It happily accepts `x + "hello"` if the lexer and parser have no notion of types at all; closing that gap is exactly what semantic analysis is for.

### 6.3 Semantic Analysis — Symbol Tables, Scope, and Type Checking

```python
from __future__ import annotations


class Scope:
    """One lexical scope. `parent` is the enclosing scope; lookup walks
    outward -- exactly how a closure resolves a free variable."""

    def __init__(self, parent: "Scope | None" = None) -> None:
        self.vars: dict[str, str] = {}          # name -> inferred type
        self.parent = parent

    def define(self, name: str, type_: str) -> None:
        self.vars[name] = type_

    def resolve(self, name: str) -> str:
        scope: Scope | None = self
        while scope is not None:
            if name in scope.vars:
                return scope.vars[name]
            scope = scope.parent
        raise NameError(f"undefined name '{name}'")


def infer_type(node: Node, scope: Scope) -> str:
    """Walk the AST once, assigning a type to every node -- the check a
    parser alone cannot perform, because 'x + "hi"' parses just fine."""
    if isinstance(node, Num):
        return "int"
    if isinstance(node, Var):
        return scope.resolve(node.name)
    if isinstance(node, BinOp):
        left_t, right_t = infer_type(node.left, scope), infer_type(node.right, scope)
        if left_t != right_t:
            raise TypeError(f"cannot apply '{node.op}' to {left_t} and {right_t}")
        return left_t
    if isinstance(node, Assign):
        value_t = infer_type(node.value, scope)
        scope.define(node.name, value_t)
        return value_t
    raise TypeError(f"unknown node {node}")
```

A scope chain is a tree at compile time even though only one root-to-leaf path is walked per lookup:

```
Scope chain for:
    x = 10
    def outer():
        y = 20
        def inner():
            z = x + y        <- must resolve BOTH x and y by walking outward

module scope       { x }
  |
  +-- outer() scope { y }
        |
        +-- inner() scope { z }
              resolve(x): not here -> outer: not here -> module: found (x = 10)
              resolve(y): not here -> outer: found (y = 20)
```

A compiled language answers "which scope is `x` in" once, during semantic analysis, and bakes the answer into a fixed memory offset or register — the question is never re-asked at run time. A dynamic language like Python re-asks a cheaper version of the same question every time (`LOAD_FAST`/`LOAD_GLOBAL` bytecode, see 6.6), which is part of why dynamic languages pay a per-access cost that statically resolved languages do not.

### 6.4 Middle End — IR and Two Concrete Optimization Passes

```python
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class Instr:
    op: str                       # 'const', 'add', 'mul', 'assign'
    dest: str
    a: str | int
    b: str | int | None = None


def constant_fold(instrs: list[Instr]) -> list[Instr]:
    """Fold operations whose operands are already known integer literals --
    a provably semantics-preserving rewrite: 3 * 4 always equals 12, so
    'mul t3 t1 t2' can be replaced by 'const t3 12' without changing
    behaviour. This folds the COMPUTATION; propagating the folded value
    into every later USE of t3 is a related pass, constant propagation,
    often run in the same sweep."""
    known: dict[str, int] = {}
    out: list[Instr] = []
    for ins in instrs:
        if (ins.op in ("add", "mul") and isinstance(ins.a, str) and isinstance(ins.b, str)
                and ins.a in known and ins.b in known):
            value = known[ins.a] + known[ins.b] if ins.op == "add" else known[ins.a] * known[ins.b]
            known[ins.dest] = value
            out.append(Instr("const", ins.dest, value))
        elif ins.op == "const":
            known[ins.dest] = ins.a          # type: ignore[assignment]
            out.append(ins)
        else:
            out.append(ins)
    return out


def dead_code_eliminate(instrs: list[Instr], live_out: set[str]) -> list[Instr]:
    """Remove instructions whose result is never read. Walk backwards,
    tracking which names are still 'live' (needed by something later).
    Named (non-temporary) assignments are always kept: this simplified
    pass treats them as potentially observable and refuses to guess that
    they are safe to drop -- a real compiler can prove more, but every
    DCE pass must stay conservative about anything that might later be
    inspected (a debugger, an exception handler, a closure)."""
    live = set(live_out)
    keep: list[Instr] = []
    for ins in reversed(instrs):
        if ins.dest in live or ins.op == "assign":
            keep.append(ins)
            live.discard(ins.dest)
            for operand in (ins.a, ins.b):
                if isinstance(operand, str):
                    live.add(operand)
        # else: ins.dest is never read again -- safe to drop entirely
    return list(reversed(keep))


if __name__ == "__main__":
    program = [
        Instr("const", "t1", 3),
        Instr("const", "t2", 4),
        Instr("mul", "t3", "t1", "t2"),
        Instr("const", "t4", 99),          # dead from the start -- never read
        Instr("assign", "x", "t3"),
    ]
    folded = constant_fold(program)
    final = dead_code_eliminate(folded, live_out={"x"})
    for ins in final:
        print(ins)
    # Output:
    #   Instr(op='const', dest='t3', a=12, b=None)
    #   Instr(op='assign', dest='x', a='t3', b=None)
    # t1, t2, and t4 are all gone: t1/t2 became unreachable once folding
    # replaced the multiply with a literal, and t4 was dead on arrival.
```

**Constant folding** evaluates an operation at compile time when every operand is already a known literal. **Dead-code elimination** removes an instruction once nothing downstream can possibly read its result. Run together, they cascade: folding the multiply into `const t3 12` makes the original `t1`/`t2` definitions unreachable, so DCE sweeps those away too, on top of the `t4` that was dead from the start.

**Function inlining** replaces a call with a copy of the callee's body. Its main value is not removing call overhead — it is exposing the caller's and callee's code to every *other* pass at once: a constant argument can now be folded straight through what used to be a function boundary, and a branch that only ever goes one way inside the inlined body becomes dead code. This is why inlining is often called "the mother of all optimizations": it does not, by itself, make anything faster, but it unlocks the passes that do.

**SSA form** renames every assignment to a fresh version, so `x = 1; x = x + 1; y = x` becomes `x1 = 1; x2 = x1 + 1; y1 = x2`. With every name defined exactly once, "what value can `x2` hold at this point" stops being a search through the program's history and becomes a single, direct lookup — the reason LLVM, GCC, HotSpot's C2, and V8's TurboFan all build an SSA form internally before optimizing, regardless of the source language.

### 6.5 Back End — Instruction Selection and Register Allocation

```
Live ranges for  t1=3; t2=4; t3=t1*t2; t4=t3+2  (which temporaries overlap):
  t1: needed at lines 1-3   t2: needed at lines 2-3   t3: needed at lines 3-4   t4: line 4

With only 2 registers available (rax, rbx):
  t1 -> rax
  t2 -> rbx
  t3 -> rax     (t1 is dead after line 3 -- safe to reuse its register)
  t4 -> rbx     (t2 is dead after line 3 -- safe to reuse its register)
```

```
Generated x86-64, Intel syntax:
    mov  rax, 3        ; t1 = 3
    mov  rbx, 4        ; t2 = 4
    imul rax, rbx      ; t3 = t1 * t2   (reuses rax)
    add  rax, 2        ; t4 = t3 + 2    (final result lives in rax)
```

Register allocation is assigning each temporary to a physical register. When more values are simultaneously live than there are registers, the allocator must **spill** one to the stack instead:

```
  Three values (t1, t2, t3) all live at once, but only 2 registers exist:
    t1 -> rax
    t2 -> rbx
    t3 -> [rbp-8]        ; SPILLED -- no free register, stored to the stack instead
```

Deciding which temporaries can share a register is graph coloring: build an interference graph where two temporaries are connected if their live ranges overlap, then try to color it with *k* colors, one per available register. General graph coloring is NP-complete, so real compilers use heuristics rather than an exact solver — Chaitin's algorithm and Briggs' optimistic-coloring refinement are the classic named approaches taught in every compilers course.

### 6.6 CPython Bytecode — A Concrete `dis` Walkthrough

```python
import dis

def f(a, b):
    return a + b * 2

dis.dis(f)
```

CPython 3.11 and later:

```
  2           0 RESUME                   0
              2 LOAD_FAST                0 (a)
              4 LOAD_FAST                1 (b)
              6 LOAD_CONST               1 (2)
              8 BINARY_OP                5 (*)
             12 BINARY_OP                0 (+)
             16 RETURN_VALUE
```

CPython 3.10 and earlier, for contrast:

```
  2           0 LOAD_FAST                0 (a)
              2 LOAD_FAST                1 (b)
              4 LOAD_CONST               1 (2)
              6 BINARY_MULTIPLY
              8 BINARY_ADD
             10 RETURN_VALUE
```

`f.__code__.co_code` is the raw byte string CPython actually loops over; `dis` is a disassembler, exactly like `objdump -d` for machine code, except the "machine" here is `ceval.c`'s bytecode dispatch loop — a `while` loop around a giant `switch` (or a computed-goto table, on compilers that support it) keyed on the opcode. Calling `f` a second time does not skip this loop; it re-enters it and re-dispatches these exact instructions from the top.

Python 3.11 collapsed the old per-operator opcodes (`BINARY_ADD`, `BINARY_MULTIPLY`, and friends) into a single `BINARY_OP` carrying the operator as an argument, and added `RESUME` as instruction zero of every code object for the new **specializing adaptive interpreter** (PEP 659). After a bytecode like `BINARY_OP` has executed the same operation on the same concrete types repeatedly, CPython rewrites that instruction *in place* to a specialized form (for example `BINARY_OP_ADD_INT`) that skips the generic type dispatch and jumps straight to the integer fast path, falling back to the generic form the moment the types change. This is *quickening*, not compiling — the result is still bytecode, interpreted by the same loop, just cheaper bytecode — and it is the main reason the official CPython 3.11 release notes measured the interpreter as roughly 25% faster than 3.10 on the standard benchmark suite, without anyone writing a JIT.

None of this changes the fundamental shape of the strategy: `.pyc` caching (`__pycache__/f.cpython-311.pyc`) skips re-lexing and re-parsing on later runs, but every run still pays the full cost of interpreting every bytecode instruction one at a time. A `gcc -O2`-compiled equivalent of the same arithmetic starts running in roughly 1-2 ms; CPython's own interpreter and runtime initialization alone commonly costs 10-30 ms before user code executes at all — and every line of that user code is then interpreted, not compiled to machine code, which is the real reason CPython remains 10-100x slower than C on CPU-bound numeric loops despite the bytecode cache.

### 6.7 JIT Compilation — Tiering, On-Stack Replacement, and Deoptimization

The JVM's HotSpot runtime is the textbook tiered-JIT example. A method starts in the interpreter (Tier 0), which counts invocations and loop back-edges. Cross an invocation threshold and the fast, lightly-optimizing **C1** compiler produces a first native version, optionally instrumented to gather type feedback (which branch a call site actually took, which concrete class a call site actually saw). Cross a second, much higher threshold and the slow, aggressively-optimizing **C2** compiler recompiles the method using that collected profile, speculating on things the profile suggests are always true — a monomorphic call site can be devirtualized into a direct call, a loop's bounds check can be hoisted out entirely if the profile shows the index never goes out of range. The classic, widely cited (pre-tiered) defaults are a `CompileThreshold` of 1,500 invocations for C1 and 10,000 for C2; modern tiered compilation (default since JDK 8) blends these with per-tier thresholds and back-edge counters rather than one flat number.

A hot *loop* inside a method that has only been called once cannot wait for an invocation counter to cross any threshold — the method has not even returned yet. **On-stack replacement (OSR)** solves this: the interpreter tracks loop back-edge counts directly, and once a loop is hot enough, the runtime swaps the *currently executing* interpreted frame for an equivalent compiled frame in the middle of execution, without waiting for the enclosing method to be called again.

Speculation can be wrong. If a call site the JIT devirtualized under the assumption "this is always a `Dog`" is later reached with a `Cat`, the compiled code's assumption breaks. The runtime must **deoptimize** (V8 calls this a "bailout"): discard the compiled frame, reconstruct an equivalent interpreter frame from it, and resume in the interpreter — possibly recompiling a more conservative, polymorphism-aware version later. HotSpot exposes this via `-XX:+PrintCompilation`/`-XX:+TraceDeoptimization`; V8 exposes the same idea via `--trace-deopt`. V8 itself runs a three-tier pipeline for the same reasons: **Ignition** (a bytecode interpreter), **Sparkplug** (a non-optimizing baseline JIT added in Chrome 91 specifically to close the gap between "cheap bytecode" and "slow-to-produce, fully optimized code"), and **TurboFan** (the optimizing tier that does the same profile-guided speculation as C2).

This warmup requirement is also the most common way engineers accidentally lie to themselves in a benchmark:

```java
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    compute(i);
}
long elapsed = System.nanoTime() - start;
// BROKEN: this single elapsed time blends interpreter-speed early
// iterations with JIT-compiled-speed later iterations. It measures
// neither steady-state throughput nor cold-start latency -- it measures
// an arbitrary mixture of both, and the mixture shifts with JVM version,
// heap size, and machine load.
```

The full fix, and the reason JMH exists at all, is in section 10's pitfalls below.

GraalVM Native Image takes the opposite bet entirely: instead of a runtime JIT, a build-time **points-to analysis** starting from the program's declared entry points (`main`, or a framework's registered handlers) computes the entire reachable call graph under a **closed-world assumption**, and compiles everything it can prove is reachable straight to native code, packaged with a minimal embedded runtime (historically called SubstrateVM) that ships its own simple garbage collector but no JIT at all. The payoff is a cold start commonly in the 10-50 ms range, against a typical JVM cold-start-to-first-response commonly reported in the hundreds of milliseconds to low seconds for a framework-heavy app, depending on classpath size — at the cost of a multi-minute native-image build and a closed-world assumption that anything the static analysis cannot see (reflection, dynamic proxies, classes loaded by name from a config file) must be explicitly declared, or it silently is not there at run time.

### 6.8 The Linker — Symbol Resolution and Relocation

```c
/* mathutils.h */
#ifndef MATHUTILS_H
#define MATHUTILS_H
int add(int a, int b);
#endif
```

```c
/* main.c */
#include <stdio.h>
#include "mathutils.h"

int main(void) {
    printf("%d\n", add(2, 3));
    return 0;
}
```

**BROKEN** — `mathutils.c` was never written (or never added to the build), so no object file anywhere defines `add`:

```
$ gcc main.c -o prog
/usr/bin/ld: /tmp/ccXXXXXX.o: in function `main':
main.c:(.text+0x15): undefined reference to `add'
collect2: error: ld returned 1 exit status
```

`main.c` compiled without a single error — the header's *declaration* of `add` is enough for the front end to check the call's argument types and for the back end to emit a `call` instruction with a placeholder target. It is the **linker's** job, not the compiler's, to find a *definition* of `add` somewhere among the object files and libraries on the link line and patch the real address into that placeholder. If nothing defines it, the individual compilations still succeeded, but the link step — the step that turns several separately-compiled pieces into one program — fails outright.

**FIX** — compile and link the file that actually defines `add`:

```
$ cat mathutils.c
int add(int a, int b) { return a + b; }

$ gcc main.c mathutils.c -o prog
$ ./prog
5
```

The same class of error shows up when a definition exists but in a library that was never linked — `undefined reference to 'sqrt'` even though `<math.h>` declares it, because on many toolchains `sqrt`'s definition lives in `libm`, not automatically linked; the fix is `gcc main.c -o prog -lm`.

What the linker actually does: it merges every object file's sections (`.text`, `.data`, `.bss`, `.rodata`) into one output layout, builds one global symbol table across all inputs, resolves every undefined symbol against a definition found in another object file or library, and **relocates** every reference that assumed an address it did not yet know — a `.rela.text` entry says, in effect, "at this byte offset, once you know where symbol X finally lives in the output, patch in its address." Static linking does all of this once, at build time; dynamic linking defers the same resolution to load time (or later) via an extra layer of indirection, the Procedure Linkage Table and Global Offset Table, so that a shared library's own `.text` section can stay byte-for-byte identical — and therefore shareable read-only across every process using it — regardless of which process is calling it or from where.

### 6.9 The Loader

`execve()` hands a path to the kernel, which reads the ELF header's magic bytes, then reads the **Program Header table** — not the section header table, which exists only for linkers and debuggers. Each Program Header describes one *segment*: a contiguous region to `mmap` at a given virtual address with specific permissions and specific file-backed (or zero-filled) content, producing the layout shown in section 5's virtual-memory diagram. The kernel builds the initial stack with `argv`, `envp`, and the auxiliary vector (`auxv` — entries like `AT_PAGESZ` and `AT_RANDOM` that the C runtime reads before `main` even starts). If the binary is dynamically linked, its `PT_INTERP` segment names an interpreter of its own — on Linux, typically `/lib64/ld-linux-x86-64.so.2` — and the kernel loads and runs *that* first; the dynamic linker maps every needed shared library, resolves the remaining symbols (lazily through the PLT/GOT, or eagerly if `LD_BIND_NOW` is set), and only then jumps into the real program, through glibc's `__libc_start_main`, and finally into `main`.

### 6.10 The ABI and Calling Conventions

The System V AMD64 ABI (Linux/macOS, x86-64) passes the first six integer or pointer arguments in fixed registers, in order: `rdi, rsi, rdx, rcx, r8, r9`; the return value comes back in `rax`. `int add(int a, int b) { return a + b; }` — the exact function from section 6.8 — commonly compiles under `gcc -O2` to:

```
add:
    lea  eax, [rdi+rsi]     ; a is in rdi, b is in rsi per the ABI; result -> eax
    ret
```

`lea` (load effective address) is a common GCC trick here: it computes `rdi + rsi` using the address-calculation unit instead of the integer ALU, without touching condition-code flags — a real compiler optimization visible in ordinary generated code, not just a textbook simplification.

The ABI is what makes it possible for `main.o` and `mathutils.o` — compiled separately, possibly months apart, possibly by different compiler versions — to agree on where `a` and `b` live without either file ever seeing the other's source. But the ABI also has sharp edges the language's API says nothing about. C has no function overloading, so a C compiler emits `add` as the literal symbol `add`. C++ allows overloading, so a C++ compiler must encode the parameter types into the symbol name to keep `add(int,int)` distinct from `add(double,double)` — the Itanium C++ ABI used by GCC and Clang encodes `int add(int,int)` as `_Z3addii`. This is exactly why calling a C library from C++ requires `extern "C"`:

**BROKEN** — a C++ translation unit includes a C header without telling the compiler not to mangle it:

```cpp
// mathutils.h, included from a .cpp file
int add(int a, int b);   // C++ will mangle calls to this as _Z3addii
```

```
$ g++ main.cpp mathutils.o -o prog
undefined reference to `add(int, int)'
```

`mathutils.o` was compiled by a plain C compiler and exports the plain symbol `add` — not `_Z3addii` — so the linker sees a call to a symbol that, as far as it can tell, nothing defines. It is the identical linker error message as the missing-translation-unit case in 6.8, but the underlying cause and the fix are completely different.

**FIX** — tell the C++ compiler this declaration follows C linkage rules, so it emits the plain, unmangled name at every call site:

```cpp
// mathutils.h
#ifdef __cplusplus
extern "C" {
#endif
int add(int a, int b);
#ifdef __cplusplus
}
#endif
```

---

## 7. Real-World Examples

**GCC/Clang/LLVM retargetability** — Clang's front end lowers C, C++, and Objective-C into one shared LLVM IR; that same IR is consumed by back ends for x86, ARM, RISC-V, and WebAssembly. Rust, Swift, and Zig all target the same IR, which is why they can reuse LLVM's optimizer and code generators instead of writing their own.

**CPython's `.pyc` cache** — every `import` compiles the source to bytecode once and caches it under `__pycache__/module.cpython-3XX.pyc`, keyed by a source hash or mtime. This skips lexing and parsing on later runs; it does not skip interpretation, which is why a cached `.pyc` runs at the same speed as a fresh compile of unchanged source.

**JVM HotSpot tiered compilation** — `java -Xint` forces pure interpretation (useful for isolating a JIT-related bug); `-XX:+PrintCompilation` prints every method as it crosses a compilation tier, visible proof of the warmup described in 6.7.

**V8's three-tier pipeline** — Ignition, Sparkplug, and TurboFan, described in 6.7, ship in every Chrome and Node.js release; `node --v8-options | grep trace-opt` surfaces the flags that expose which functions got optimized and why.

**GraalVM Native Image in production** — Quarkus, Micronaut, and Spring Boot 3's AOT processing all build on Native Image specifically to fix serverless and container cold-start latency; all three ship a "tracing agent" that runs the app's real test suite once to auto-generate the reflection/resource configuration the closed-world analysis needs.

**Go compiles AOT to native machine code and still has a runtime** — a `go build` binary needs no separate VM or JIT, but it embeds a goroutine scheduler and a garbage collector; AOT native compilation and "no runtime at all" are two different things, and Go is the clearest counter-example to conflating them.

**Rust monomorphizes generics at compile time; Java erases them** — `Vec<i32>` and `Vec<f64>` in Rust are compiled into two entirely separate, specialized functions ahead of time (zero runtime dispatch cost); Java's `List<Integer>` and `List<Double>` share one compiled method operating on erased `Object` references, with casts inserted by the compiler and checked at run time — a direct, concrete consequence of one language being purely AOT and the other targeting a bytecode VM.

**WebAssembly** — a portable bytecode designed explicitly to be both quickly interpretable and quickly JIT-compilable by a browser, so that a `.wasm` module's cold start is close to native even before any optimizing tier has run.

---

## 8. Tradeoffs

### 8.1 Execution Strategy Tradeoffs

| Dimension | AOT native | Bytecode interpreter | Bytecode VM + JIT | AOT native image (managed language) |
|---|---|---|---|---|
| Startup latency | Best (~1-5 ms) | Good (~10-60 ms) | Worst until warm (100 ms-few s) | Best (~10-50 ms) |
| Peak throughput | Fixed at build time | Lowest | Highest achievable — uses runtime profile data | High, but capped below a fully warmed JIT |
| Debuggability | Needs debug symbols (DWARF); optimized code can be hard to step through | Easiest — can pause between every bytecode | Hardest — an optimized frame may need deoptimizing just to inspect | Similar difficulty to AOT native |
| Adapts to observed runtime behavior | No — fixed at build time | No | Yes — recompiles based on observed types and branches | No — build-time static analysis only |
| Portability of the build artifact | One build per target CPU/OS | One build runs wherever the interpreter exists | One build (bytecode) runs wherever the VM exists | One build per target CPU/OS, not portable bytecode |

### 8.2 Static vs. Dynamic Linking: Why Pick One

| Dimension | Static linking | Dynamic linking |
|---|---|---|
| Security patch rollout | Rebuild and redeploy every binary that used the vulnerable code | Patch the one `.so`; every consumer picks it up on its next start |
| Reproducible, hermetic deploys | Excellent — one self-contained file behaves the same forever | Fragile — behavior depends on whichever `.so` version happens to be on the target machine |
| Disk / cross-process memory footprint | Higher — N processes means N copies of the library code | Lower — the OS maps one physical copy of a shared library's code into every process using it |
| When failures surface | Build/link time | Anywhere from load time to the first call of a lazily-bound symbol, in production |

---

## 9. When to Use / When NOT to Use

**Use AOT native compilation** (C, C++, Rust, Go, Zig) when startup latency is part of the product (CLI tools, serverless functions, edge workers), when performance must be predictable and deterministic (real-time systems, embedded targets, a game engine's hot path), or when you are building a shared library that many different language runtimes must call through a stable ABI.

**Use a bytecode VM with a JIT** (JVM, Node/V8, PyPy) when the process runs long enough, relative to its warmup, to amortize that cost (application servers, long-lived batch jobs), when the workload is polymorphic in ways only observable at run time (profile-guided speculation beats static optimization here), or when a mature managed runtime — GC, reflection, dynamic class loading — matters more than shaving the last millisecond off startup.

**Use a plain bytecode interpreter** (reference CPython, an embedded scripting language) when the workload is I/O-bound or short-lived enough that interpretation overhead is dwarfed by other costs, or when a tiny, simple, easily sandboxed execution engine matters more than raw speed.

**Use AOT compilation of a normally-JIT'd language** (GraalVM Native Image, .NET Native AOT) when you want a managed language's developer ergonomics with a deployment shape that punishes JIT warmup — and accept the closed-world constraints (explicit reflection configuration) as the real cost of that trade.

**Do not hand-roll a lexer/parser for a non-trivial grammar.** A parser generator (ANTLR, yacc/bison) surfaces grammar ambiguity as a build-time conflict report; a hand-written recursive-descent parser just silently mis-parses the ambiguous case and ships a bug.

**Do not judge a runtime's speed from a single cold, un-warmed benchmark, and do not assume "AOT is always faster than JIT" either** — compare like workloads: for short scripts, an AOT or an interpreter is fine either way; for long-running, polymorphic hot loops, let the JIT warm up before drawing any conclusion.

---

## 10. Common Pitfalls

### Pitfall 1 — "An Interpreter Is Just a Slow Compiler"

```
BROKEN mental model:
  "An interpreter is a compiler that just runs slower -- give it enough
  optimizations and it eventually becomes a compiler."
```

This conflates two independent axes. Speed is a property of *what* an implementation does with dynamic typing, boxing, and reference counting on every operation, not a property of *when* translation happens — CPython's adaptive interpreter got measurably faster in 3.11 by specializing individual bytecodes in place, without adding a JIT or a single AOT compilation step. And "eventually becomes a compiler" gets the direction backwards: a compiler produces a standalone artifact and steps out of the way; an interpreter's entire purpose is to remain the execution engine, forever, on every run. Fix the framing, not the interpreter: ask "when does translation happen, and who executes the result" instead of "how optimized is the translator."

### Pitfall 2 — Missing Library at Link Time

```
BROKEN:
$ gcc sensor_reader.c -o sensor_reader
/usr/bin/ld: /tmp/xyz.o: in function `read_temp':
sensor_reader.c:(.text+0x42): undefined reference to `sqrt'
collect2: error: ld returned 1 exit status
```

`<math.h>` declares `sqrt`, which satisfies the front end and back end completely — the compiler can check the call's signature and emit a `call` instruction. On many Linux toolchains, `sqrt`'s actual definition lives in `libm`, which is not linked by default.

```
FIX:
$ gcc sensor_reader.c -o sensor_reader -lm
```

### Pitfall 3 — Benchmarking a JIT Without Warming It Up

```java
// BROKEN: one elapsed-time measurement spans interpreter-speed AND
// JIT-compiled-speed iterations -- it measures neither cleanly, and the
// mixture point shifts with JVM version, flags, and machine load.
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    compute(i);
}
System.out.println(System.nanoTime() - start);
```

```java
// FIX: discard a warmup phase, then measure only the (now compiled) steady state.
for (int i = 0; i < 200_000; i++) {
    compute(i);                    // warmup -- let C1/C2 do their job, discard timing
}
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    compute(i);                    // now measuring steady-state, JIT-compiled speed
}
System.out.println(System.nanoTime() - start);
```

Production benchmarking should use JMH rather than hand-rolled timing loops: JMH forks a fresh JVM per benchmark, runs configurable warmup iterations before measuring, and accounts for dead-code elimination silently discarding a computation whose result is never used — a benchmark that never reads its own result can have its entire body optimized away.

### Pitfall 4 — "Native Is Always Faster Than Managed"

A naive claim — "C will always beat Java because Java is interpreted" — misses that HotSpot's C2 (or V8's TurboFan) recompiles hot code using runtime profile data (actual observed types, actual observed branch outcomes) that a static AOT compiler never has at build time. A long-running, polymorphism-heavy service can see a warmed-up JVM match or exceed naively-compiled AOT code on the same logic. The nuance that survives scrutiny is narrower and about *process lifetime*, not language: short-lived processes (CLI tools, scale-to-zero functions) favor AOT because they never live long enough to reach a JIT's peak; long-lived processes can let a JIT catch up and sometimes win.

### Pitfall 5 — GraalVM Native Image Breaks on Reflection the JVM Never Noticed

A Spring Boot service works perfectly on the JVM, where classpath component scanning reflectively instantiates `@Component`-annotated classes by name at startup. The team builds a GraalVM Native Image for faster cold starts; the build succeeds, but the native binary throws `ClassNotFoundException` at run time for a class the JVM version instantiated without complaint.

```
BROKEN: no reachability metadata --
the closed-world static analysis starts from main() and cannot see a
class that is only ever referenced by a string in a classpath scan; it
is therefore not compiled into the native image at all.
```

```
FIX: run the native-image tracing agent against the real test suite to
auto-generate the missing configuration, or use the framework's own
AOT-processing step (Spring Boot 3's AOT engine, Quarkus's build-time
extension model) instead of relying on runtime reflection at all:

  java -agentlib:native-image-agent=config-output-dir=META-INF/native-image \
       -jar app.jar
```

### Pitfall 6 — A Shared Library Upgrade Silently Breaks an ABI Contract

A production war story: a container base image is patched to a newer minor version of a shared crypto library. Every consuming process still finds the `.so`, still resolves every symbol, and still links — the failure is not a linker error at all. A struct the library used to expose directly changed layout between versions; a process built against the old layout reads garbage or crashes at run time, because dynamic linking only checks that a *symbol name* resolves, not that the *data layout* behind it is unchanged. This is exactly the class of bug OpenSSL's 1.1.0+ releases addressed by making most of their structs opaque — forcing callers through accessor functions whose ABI can stay stable even when the internal layout changes. Fix: pin exact shared library versions for anything that exposes struct layout across the ABI boundary, or static-link, or containerize with an immutable base image, rather than trusting "the symbol still resolves" as proof that nothing changed.

---

## 11. Technologies & Tools

| Tool | Category | What it does | Notes |
|---|---|---|---|
| `gcc` / `clang` | AOT compiler | C/C++ source to native machine code | `-O0` through `-O3`; `-S` emits assembly for inspection |
| LLVM `opt` / `llc` | IR tooling | Run optimization passes on LLVM IR / generate code from it | Used to inspect exactly what an optimization pass changed |
| CPython `dis` module | Bytecode inspection | Disassembles a function's bytecode | `dis.dis(func)`; add `adaptive=True` on 3.11+ to see specialized forms |
| CPython `ast` module | AST inspection | Parses source into Python's own AST, `ast.dump()` to print it | Useful for seeing exactly how precedence resolves in real Python |
| `py_compile` / `__pycache__` | Bytecode cache | Compiles and caches `.pyc` files | Speeds up import, not execution |
| `javac` + `-XX:+PrintCompilation` | JVM tooling | Compile Java; log every JIT tier transition | Direct, visible proof of tiered warmup described in 6.7 |
| JITWatch / async-profiler | JVM profiling | Visualize what HotSpot compiled, inlined, or deoptimized | Essential for diagnosing "why is this method still interpreted" |
| `node --trace-opt --trace-deopt` | V8 tooling | Log TurboFan optimization and bailout decisions | Node.js equivalent of `-XX:+PrintCompilation` |
| GraalVM `native-image` | AOT tool for managed languages | Compiles a JVM-bytecode app to a native binary | Pair with the tracing agent for reflection configuration |
| `objdump` / `readelf` / `nm` | Binary inspection | Disassemble, dump ELF sections, list symbols | `objdump -d` for machine code, `readelf -l` for Program Headers |
| `ldd` | Dynamic linking inspection | Lists a binary's shared library dependencies | Also a common vector for the ABI-mismatch war story in Pitfall 6 |
| ANTLR / yacc / bison / flex | Parser/lexer generators | Generate a lexer/parser from a grammar specification | Surfaces grammar ambiguity as a build-time conflict, not a runtime bug |
| JMH (Java Microbenchmark Harness) | Benchmarking | Forks a JVM, runs warmup iterations, then measures | The correct tool for Pitfall 3; avoids dead-code-eliminated benchmarks |

---

## 12. Interview Questions with Answers

**Q1: Is an interpreter just a slow compiler?**
No — the real difference is when translation happens and who executes the result, not how smart the translator is. A compiler produces a standalone artifact and steps aside; an interpreter's job is to remain the execution engine on every single run. CPython 3.11 got measurably faster by specializing bytecodes in place, without adding a JIT or an AOT step, which shows "slow" is not an inherent property of interpreting at all.

**Q2: What actually causes a linker "undefined reference" / "undefined symbol" error?**
It means the linker found a call to a symbol that no object file or library on the link line actually defines. The front end and back end only need a *declaration* to check argument types and emit a `call` instruction with a placeholder address; only the linker needs a *definition* to patch in a real one. The fix is always some version of "compile and link the file that defines it, or link the library that contains it" — a missing `.c` file and a missing `-lm` produce the identical error message for different reasons.

**Q3: Why does `extern "C"` matter when calling a C library from C++?**
It stops the C++ compiler from name-mangling the declaration, so the linker looks for the same plain symbol the C compiler actually emitted. C allows no function overloading, so a C compiler exports `add` as literally `add`; C++ allows overloading and must encode parameter types into the symbol (`_Z3addii` for `add(int,int)` under the Itanium ABI) to keep overloads distinct. Without `extern "C"`, the two compilers agree on the function's behavior but disagree on its name, and linking fails with an error that looks identical to a genuinely missing definition.

**Q4: Why does a JIT-compiled program often start out slower than an equivalent AOT binary?**
Because the JIT itself runs on the CPU during the program's own execution, spending cycles interpreting and profiling before it has compiled anything worth running natively. An AOT binary pays its entire translation cost once, at build time, so the very first instruction executed is already optimized machine code. This is exactly why GraalVM Native Image and short-lived AOT binaries win the cold-start race, while a JVM or V8 process needs enough total runtime to amortize the warmup before it can catch up or overtake.

**Q5: Is a statically compiled (AOT) language always faster than a JIT-compiled one?**
Not necessarily — a warmed-up JIT can match or exceed naive AOT code because it optimizes using real runtime type and branch data that a static compiler never sees at build time. A monomorphic call site the profiler has observed can be devirtualized and inlined by the JIT in a way a purely static analysis could not safely assume. The nuance that survives scrutiny is about process lifetime rather than language: short-lived processes favor AOT because they never live long enough to reach the JIT's peak, while long-lived processes can let the JIT catch up.

**Q6: What is the difference between a syntax error and a semantic error, and which compiler phase catches each?**
A syntax error means the token stream does not match the language's grammar, and it is caught by the parser while building the AST. A semantic error means the program is grammatically valid but still meaningless — an undefined variable, a type mismatch like `x + "hello"` — and it is caught by semantic analysis, which walks the completed AST with a symbol table. A parser alone cannot catch semantic errors because it has no concept of types or declared names; it only enforces structure.

**Q7: What is an AST, and why can't the compiler just work directly on the token stream?**
An AST is a tree that represents the program's structure and operator precedence, something a flat token list cannot express on its own. `2 + 3 * 4` as tokens has no inherent grouping; as an AST, the multiplication is nested one level deeper than the addition, encoding precedence directly in the tree's shape. Every later phase — type checking, IR generation, optimization — operates on this tree rather than re-deriving structure from tokens every time.

**Q8: What is an intermediate representation (IR), and why not compile straight from the AST to machine code?**
An IR is a representation of the program that is independent of both the source language and the target machine, sitting between the AST and the generated code. Without it, a compiler supporting N source languages and M target architectures would need roughly N times M translators; with a shared IR, it needs only N front ends and M back ends. It is also the representation that optimization passes are written against once, so a pass like dead-code elimination works identically regardless of whether the source was C or Rust.

**Q9: What is SSA (static single assignment) form, and why do optimizing compilers use it?**
SSA form gives every variable exactly one definition, creating a fresh version number any time a variable would otherwise be reassigned. This turns "what value could this variable hold here" from a search through the program's control flow into a direct lookup of which single definition reaches this point, which is why dataflow-based optimizations become simpler and more mechanical to implement correctly. LLVM, GCC's internal IRs, the JVM's C2 compiler, and V8's TurboFan all build SSA internally even though no source language exposes it directly.

**Q10: What is constant folding?**
Constant folding evaluates an operation at compile time when every one of its operands is already a known literal, replacing the operation with its result. `3 * 4` inside an expression becomes the literal `12` before the program ever runs, so the multiply instruction itself disappears from the generated code. It is one of the cheapest, safest optimizations to implement and is almost always run first because it frequently exposes further dead code for elimination.

**Q11: What is dead-code elimination, and why must it be conservative?**
Dead-code elimination removes a computation whose result is never used by anything that follows it. It must be conservative because "never used" has to be *proven*, not guessed — a value that looks unused in straight-line code might still be observable through a closure, an exception handler, or a debugger attached to the running process. A real compiler tracks liveness precisely enough to prove a value is truly unreachable before deleting the instruction that produced it.

**Q12: What does function inlining actually buy you, beyond removing call overhead?**
Inlining replaces a call site with a copy of the callee's body, and its main value is exposing the caller's and callee's code to every other optimization pass simultaneously. A constant argument passed into the inlined body can now be folded straight through what used to be an opaque function boundary, and branches that only ever go one way inside it can become dead code. This is why inlining is often called the mother of all optimizations — it rarely speeds anything up directly, but it unlocks the passes that do.

**Q13: What is register allocation, and what happens when there are more live values than registers?**
Register allocation assigns each temporary value produced by the IR to a physical CPU register so the back end can generate real instructions. When more values are live simultaneously than there are available registers, the excess must be spilled — stored to a stack slot instead and reloaded when needed, at the cost of an extra memory access. Because deciding which values can share a register is graph coloring, and general graph coloring is NP-complete, real compilers use heuristics like Chaitin-style iterative coloring rather than an exact solver.

**Q14: What is the difference between static linking and dynamic linking?**
Static linking copies a library's code directly into the final executable at build time, while dynamic linking leaves it in a separate shared library that is mapped in and resolved at load time. Static linking produces a larger but self-contained, hermetic binary; dynamic linking produces a smaller binary that can share one physical copy of the library's code across every process using it, and that can be patched by replacing the shared library alone. The tradeoff shows up directly in security patching: a static binary needs a full rebuild and redeploy, while a dynamic one just needs the `.so` replaced.

**Q15: What does the OS loader actually do when you run a program?**
The loader reads the executable's Program Header table and maps each segment into the new process's virtual memory with the permissions and file offsets that segment specifies. It sets up the initial stack with `argv`, `envp`, and the auxiliary vector, and, if the binary is dynamically linked, hands control first to the dynamic linker named in the `PT_INTERP` segment so it can map any needed shared libraries before the real entry point runs. Crucially, the loader reads Program Headers, describing segments, not the section headers that tools like linkers and debuggers use — those never factor into how memory gets mapped at run time.

**Q16: What is the ABI, and why is it a stricter contract than a language's API?**
The ABI is the low-level contract for how a function call passes arguments and returns a value, enforced by hardware and the linker rather than the compiler's type checker. It specifies exactly which registers carry which parameters and which registers must still hold their original values after the call returns. Two functions can have "the same" signature in source code and still be ABI-incompatible if they were compiled with different calling conventions or, in C++, different name-mangling schemes — precisely why calling a C library from C++ requires `extern "C"`, even though the function's C-level signature never changed.

**Q17: How does CPython's `.pyc` bytecode cache change what gets re-executed on every run?**
The `.pyc` cache skips re-lexing and re-parsing the source on later runs, since it stores the already-compiled bytecode. It does not skip execution: every run still re-interprets that same bytecode instruction by instruction through the same dispatch loop, which is why a cached `.pyc` runs at the same speed as freshly compiling unchanged source — the cache saves front-end work, not interpretation cost.

**Q18: What is on-stack replacement (OSR) in a JIT compiler, and why is it needed?**
OSR lets a JIT swap a currently running interpreted loop for a compiled version in the middle of execution, instead of waiting for the enclosing method to return and be called again. A method containing one very hot loop might only be called once in the program's entire lifetime, so an invocation counter alone would never trigger compilation of that loop; OSR tracks the loop's own back-edge count directly and can promote it to compiled code mid-flight. Without OSR, exactly the kind of long-running hot loop that benefits most from JIT compilation would be the one case the JIT could never reach.

**Q19: What is deoptimization ("bailout") in a JIT compiler?**
Deoptimization is a JIT discarding a speculative optimization once the runtime assumption behind it turns out to be false, and falling back to the interpreter to continue correctly. A call site the JIT devirtualized under the assumption "this is always class Dog" must bail out the moment a Cat reaches the same call site, reconstructing an equivalent interpreter frame from the compiled one. Production JIT diagnostics — HotSpot's `-XX:+TraceDeoptimization`, V8's `--trace-deopt` — exist specifically to catch a method that is deoptimizing repeatedly, since that thrashing can be slower than never having compiled it at all.

**Q20: What does GraalVM Native Image do differently from running the same program on the JVM?**
It ahead-of-time compiles the entire program under a closed-world assumption, using a build-time static analysis instead of a runtime JIT that compiles hot code while the program executes. The closed-world analysis starts from declared entry points and can only include what it can prove is reachable, so reflection, dynamic proxies, or classpath scanning invisible to that analysis must be explicitly declared via reachability metadata or a tracing agent, or the resulting native binary silently omits them. The payoff is a cold start typically in the tens of milliseconds instead of the hundreds of milliseconds to seconds a framework-heavy JVM app needs to first serve a request.

**Q21: Why can a hand-rolled recursive-descent parser stack-overflow on deeply nested input, and what does that reveal about parsing?**
A recursive-descent parser's call stack directly mirrors the grammar's own recursive structure, so a deeply nested expression produces deeply nested native calls. A thousand nested parentheses, for instance, can overflow the parser's own stack before it ever reaches the innermost token. This reveals that "the parse" and "the call stack" are the same data structure viewed two ways in this parsing style, unlike a table-driven (LALR) parser, which represents the same recursion as an explicit stack on the heap and does not share this failure mode; production parsers for languages that must accept arbitrary user input either bound nesting depth explicitly or use a table-driven approach for exactly this reason.

---

## 13. Best Practices

1. **Warm up before benchmarking a JIT'd runtime.** Use JMH (Java) or an equivalent harness that runs discardable warmup iterations first — a single wall-clock measurement spanning cold and hot code measures nothing meaningful.
2. **Treat linker warnings about undefined symbols as build failures, not deploy-time surprises.** Enable strict undefined-symbol checking (`-Wl,--no-undefined` or the platform equivalent) so a missing definition fails the build instead of only failing the first time that code path runs in production.
3. **Wrap C headers included from C++ in `extern "C"` at the boundary, always** — even if today's toolchain happens to link without it, a future compiler upgrade can change mangling defaults or optimization behavior around the boundary.
4. **Reach for a parser generator (ANTLR, yacc/bison) once a grammar is more than a small expression or config language.** A generated parser reports ambiguity as a build-time conflict; a hand-rolled recursive-descent parser just silently mis-parses the ambiguous input in production.
5. **Choose the execution strategy to match the process's actual lifetime shape.** Scale-to-zero and CLI workloads favor AOT or an AOT-native-image build; long-lived servers can afford — and often benefit from — a warmed JIT.
6. **Run the native-image tracing agent against the real test suite, not just `main`, before adopting a closed-world AOT tool.** Reflective and dynamic paths that the JVM tolerated silently can become a production `ClassNotFoundException` in the native build.
7. **Pin or vendor exact shared library versions for anything you cannot continuously re-test against every OS update, or static-link it instead.** A "compatible-looking" `.so` upgrade that changes struct layout underneath a stable symbol name is a classic, hard-to-diagnose production incident.
8. **Remember that an optimized JIT frame may not correspond one-to-one with your source when debugging.** Inlining and deoptimization can make a reported line number approximate; reach for `-XX:+PrintCompilation` or its equivalent the moment behavior stops matching the source you are reading.

---

## 14. Case Study: Diagnosing a Serverless Cold-Start Regression Across Runtime Strategies

**Scenario**: a payments-validation microservice, originally a long-running Spring Boot application on the JVM, is ported to a scale-to-zero serverless platform with bursty traffic (5-20 requests/second) separated by multi-minute idle gaps. The P99 latency budget is 300 ms. The first deploy reuses the exact same JVM build (`java -jar app.jar` inside the serverless container); the on-call engineer observes P99 cold-invocation latency of 1.2-1.8 seconds and initially concludes "the JVM is slow."

**Cold-invocation timeline, until the first response is served:**

```
JVM default (java -jar app.jar):
  [class load + cold interpretation ~900ms] [request handling ~50ms] = ~950ms

JVM tuned for startup (-XX:TieredStopAtLevel=1 -Xshare:on):
  [faster class load, C1-only, no C2 wait ~350ms] [request ~50ms] = ~400ms

GraalVM Native Image build of the same app:
  [native process start ~25ms] [request handling ~50ms] = ~75ms

Go rewrite of the validation logic:
  [native process start ~8ms] [request handling ~50ms] = ~58ms
```

**Diagnosis**: the root cause is not "the JVM is slow" in a throughput sense — it is that scale-to-zero means the process almost never stays warm long enough to amortize class loading, let alone JIT warmup, before a request must already be answered. This is a strategy mismatch with the deployment shape described in section 9, not a defect in the JVM itself.

**BROKEN** — default JVM flags inside a scale-to-zero container:

```
CMD ["java", "-jar", "app.jar"]
# Every cold invocation re-pays: class loading, bytecode verification, and
# interpreter-speed execution -- plus any JIT warmup that will never live
# long enough to pay for itself before the container freezes or exits.
```

**FIX, option A** — keep the JVM, tune it for fast startup instead of peak throughput:

```
CMD ["java", \
     "-XX:TieredStopAtLevel=1", \
     "-Xshare:on", "-XX:SharedArchiveFile=app-cds.jsa", \
     "-jar", "app.jar"]
# TieredStopAtLevel=1: use only the fast C1 compiler, never wait on C2.
# Xshare + a Class Data Sharing archive: skip re-parsing and re-verifying
# core class bytecode that is identical on every cold start.
```

**FIX, option B** — change strategy entirely, to an AOT native image:

```
native-image -jar app.jar app-native
CMD ["./app-native"]
# Ahead-of-time compiled: no class loading step and no JIT step remain
# at invocation time at all.
```

**Metrics:**

| Strategy | Cold-start P99 | Extra build/deploy cost | Peak throughput once warm |
|---|---|---|---|
| JVM default | ~950 ms | none | Highest achievable |
| JVM + CDS + TieredStopAtLevel=1 | ~400 ms | small — generate one CDS archive | Lower than full C2, still strong |
| GraalVM Native Image | ~75 ms | ~5-10 min native-image build, reflection config required | Good, below a fully warmed C2 |
| Go rewrite | ~58 ms | full rewrite — the largest cost by far | Good, simple GC |

**Discussion questions:**

1. **Why did capping at `-XX:TieredStopAtLevel=1` help even though it makes the JVM's best-case throughput worse?** The workload never lives long enough to reach C2 compilation regardless, so capping at C1 removes wasted background compilation work and lets the (now dominant) class-loading and verification cost shrink instead — trading away throughput headroom nobody was ever going to reach costs nothing in practice.
2. **If traffic shifted to a small number of always-on instances instead of scale-to-zero, would this analysis change?** Yes — with long-lived warm instances, the JVM default (or even removing the `TieredStopAtLevel` cap) would likely win on steady-state throughput, since the one-time warmup cost amortizes over millions of requests instead of being paid on every single cold start.
3. **What would have to be true about the codebase for the GraalVM Native Image build to fail even though the JVM version works fine?** Any reflective instantiation, dynamic proxy, JNI call, or classpath resource lookup invisible to the static reachability analysis starting from `main` — Spring's classpath component scanning is the textbook offender, which is exactly why the tracing agent or Spring's own AOT processing step exists.

---

## See Also

- [computer_architecture_and_memory_hierarchy](../computer_architecture_and_memory_hierarchy/README.md) — the CPU pipeline and cache hierarchy that a back end's generated machine code actually runs on
- [processes_threads_and_context_switching](../processes_threads_and_context_switching/README.md) — what the loader hands off to: process address space, fork/exec, ELF memory layout in full
- [memory_management_and_virtual_memory](../memory_management_and_virtual_memory/README.md) — paging and virtual memory that the loader's segment-mapping step relies on
- [`java/bytecode_and_classfile`](../../java/bytecode_and_classfile/README.md) — the JVM classfile format, constant pool, and bytecode verification in full depth
- [`java/jvm_internals`](../../java/jvm_internals/README.md) — HotSpot's tiered JIT compilation, garbage collectors, and classloading as a JVM-level analogue of linking
- [`python/cpython_memory_model`](../../python/cpython_memory_model/README.md) — what CPython's bytecode interpreter loop actually operates on (PyObject, refcounting) once translation is done
