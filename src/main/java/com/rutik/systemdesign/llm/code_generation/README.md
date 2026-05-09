# Code Generation

## 1. Concept Overview

Code generation is one of the most commercially successful LLM applications, powering tools like GitHub Copilot, Cursor, Replit Ghostwriter, and Amazon CodeWhisperer. LLMs have become exceptional at generating, completing, editing, explaining, and debugging code — often surpassing junior developers on well-specified tasks.

Code is an ideal domain for LLMs because: (1) it has enormous training data (billions of lines on GitHub); (2) it's verifiable — code either runs and passes tests or it doesn't; (3) it follows predictable patterns — syntax, idioms, and APIs are relatively consistent. This verifiability enables unique training approaches (execution feedback) and evaluation methods unavailable for open-ended text.

---

## Intuition

> **One-line analogy**: Code generation is like having a pair programmer who has read every open-source repository ever written and can complete your thought before you finish typing.

**Mental model**: Code has properties that make LLMs especially effective: massive training data (GitHub), clear structure (syntax, APIs), and verifiability (run the code). Fill-in-the-Middle (FIM) training teaches the model to complete a "hole" in code given the surrounding context — exactly what IDE autocomplete needs. The model doesn't understand code semantically; it predicts what code statistically follows from the context, which works surprisingly well because code has strong local patterns.

**Why it matters**: Code generation tools (Copilot, Cursor) demonstrably increase developer productivity by 30-55% on measurable tasks. Code is also uniquely amenable to agentic use — agents can generate code, execute it, observe the result, and iterate in a tight loop.

**Key insight**: The verifiability of code (it runs or it doesn't, tests pass or they don't) enables training signals that language generation can't have — execution feedback closes the loop between generation and correctness.

---

## 2. Core Principles

- **Context is everything**: Good code completion requires understanding the full codebase context — imports, variable names, function signatures, related functions. More context → better completions.
- **Fill-in-the-Middle (FIM)**: Unlike text generation, code completion often needs to complete a middle section, not just the suffix. Models must be trained with FIM objective.
- **Executability as ground truth**: Code can be automatically verified. This enables RL training with execution feedback and reliable evaluation.
- **Multiple granularities**: Code generation includes single-line completion, function generation, class generation, and full feature implementation. Each has different requirements.
- **Security matters**: LLMs trained on public code learn vulnerable patterns; code generation systems must actively filter insecure suggestions.

---

## 3. Types / Strategies

### 3.1 Code Completion (Single/Multi-line)

Predict the next line(s) given the preceding code (and optionally, the remaining file):

```python
# Given (prefix):
def calculate_bmi(weight_kg, height_m):
    """Calculate Body Mass Index."""
    # Model completes:
    return weight_kg / (height_m ** 2)
```

Requirements: low latency (<100ms feel), short context window typically sufficient.

### 3.2 Fill-in-the-Middle (FIM)

Complete code given both prefix (before cursor) and suffix (after cursor):

```
Prefix: "def factorial(n):\n    if n == 0:\n"
Suffix: "    return n * factorial(n-1)"
Middle (model generates): "        return 1\n"
```

**FIM Training Format (SPM — Suffix-Prefix-Middle)**:
```
Original text: PREFIX MIDDLE SUFFIX
FIM training example: <fim_suffix>SUFFIX<fim_prefix>PREFIX<fim_middle>MIDDLE
Model learns: given suffix + prefix, predict middle
```

FIM is what enables IDE features like pressing Tab mid-function — the model sees both sides of your cursor.

### 3.3 Code Editing / Instruction Following

Given existing code + natural language instruction, produce modified code:

```
Instruction: "Refactor this function to use list comprehension instead of for loop"
Input code:
  result = []
  for x in items:
      if x > 0:
          result.append(x * 2)

Output:
  result = [x * 2 for x in items if x > 0]
```

### 3.4 Repository-Level Code Completion

Context extends beyond the current file to the entire codebase:

```
Copilot's context gathering:
  1. Current file (full content)
  2. Open tabs in editor (recent files)
  3. Related files (imports, parent classes, same directory)
  4. Recently viewed files
  5. Symbol definitions (function signatures, type hints)
  6. Git history for the current file
  7. Project README / documentation

This "context window" may be assembled from 10+ files
Prioritized by: proximity to cursor, recency, relevance
```

### 3.5 Code Agents

Autonomous agents that write, execute, debug, and iterate on code:

```
Task: "Build a REST API for a todo list app with FastAPI"

Agent loop:
  1. Plan: decompose into subtasks (models, routes, auth, tests)
  2. Code: write initial implementation
  3. Execute: run the code in sandbox
  4. Evaluate: did tests pass? Any errors?
  5. Debug: if errors, read traceback, identify fix
  6. Iterate: fix issues, re-run tests
  7. Complete: all tests pass → return solution
```

---

## 4. Architecture Diagrams

### GitHub Copilot Architecture
```
IDE (VSCode, JetBrains, etc.)
     |
     v
[Local Context Extractor]
  - Current file (with cursor position)
  - Open tabs (prioritized by relevance)
  - Language Server: imports, symbols
     |
     v
[Prompt Constructor]
  Build prompt: file header + imports + nearby code + cursor position
  Add neighbor file snippets (retrieved by embedding similarity)
  Format: FIM-compatible (prefix + suffix markers)
     |
     v
[Copilot Backend]
  Model: Codex → GPT-4o-based Copilot model
  Response: multiple completions (N=3-5)
  Streaming: tokens appear character-by-character
     |
     v
[Ranking + Filtering]
  Filter: syntax validity, security patterns
  Rank: by model confidence
     |
     v
[IDE Display]
  Show ghost text for top suggestion
  Cmd+→ to cycle alternatives
```

### Copilot Code Retrieval
```
Current cursor context
     |
     v
[Embedding of surrounding code]
     |
     v
[Retrieve similar code snippets] from:
  - Open tabs: code with similar patterns
  - Recently viewed files: temporal relevance
  - Indexed repository (optional, for Copilot Enterprise)
     |
     v
[Inject as few-shot examples in prompt]
  "Here are similar patterns in this codebase:"
  [snippet_1]
  [snippet_2]
  "Complete the following:"
  [current_code_with_cursor]
```

---

## 5. How It Works — Detailed Mechanics

### Code LLM Training Data

**The Stack (HuggingFace)**:
- 6.4TB of code from GitHub across 300+ programming languages
- License-filtered: only permissive licenses (MIT, Apache, BSD)
- Deduplicated at function/file level
- Quality filtering: remove files with high comment-to-code ratio, auto-generated files

**Code-specific tokenization**:
- Indentation as tokens (Python): `    ` (4 spaces) → single token
- Common identifiers: `self`, `def`, `return`, `import` → single tokens
- Operators and brackets: individually tokenized

**Data mixing for code models**:
```
The Stack code tokens:    80%
Natural language text:    15%  (for understanding docstrings, comments)
Math (for reasoning):      5%
```

### Evaluation Benchmarks

**HumanEval (OpenAI, 2021)**:
```
164 Python programming problems
Each problem: docstring → generate function body
Metric: pass@k = probability at least 1 of k completions passes all tests

Example:
  def has_close_elements(numbers: List[float], threshold: float) -> bool:
      """Check if any two numbers in the list are closer to each other than threshold."""
      # Model generates the body

GPT-4: 88% pass@1
o1: 95%+
Human programmers: ~95%

Note: HumanEval is considered largely "solved"; harder benchmarks needed
```

**SWE-bench** (real GitHub issues):
```
2294 real GitHub issues from 12 Python repositories
Task: given issue description + codebase → generate patch that resolves the issue
Evaluation: automated test suite (did the patch fix the failing tests?)

Metric: % resolved

GPT-4 (2023): 1.7%
SWE-agent (2024): 12%
Devin (Cognition, 2024): 13.8% (first public agent benchmark claim)
Claude 3.5 Sonnet + tools: 49%
o3 + scaffolding: 71.7%  (SWE-bench verified subset)
```

**MBPP** (Mostly Basic Python Problems):
- 500 simple Python functions from crowdsourcing
- More basic than HumanEval; good for smaller models

**BigCodeBench**:
- Complex, multi-step coding tasks; harder than HumanEval
- Tests: libraries, I/O, data processing, algorithm implementation

### Security Considerations

LLMs trained on public code learn vulnerable patterns:

```
Known problematic patterns:
  SQL injection: f"SELECT * FROM users WHERE id = {user_input}"
  Path traversal: open(base_dir + user_input)
  Hard-coded secrets: API_KEY = "sk-abc123..."
  Weak crypto: MD5, DES, RC4
  Eval injection: eval(user_input)

Detection and filtering:
  - Rule-based: regex patterns for common vulnerabilities
  - Model-based: CodeBERT fine-tuned on vulnerability datasets (CWE top 25)
  - Integration with SAST tools: Semgrep, CodeQL

Copilot's approach:
  - Filter completions through security patterns
  - Block secret-looking strings (API keys, passwords)
  - Flag known anti-patterns in UI
```

---

## 6. Real-World Examples

### GitHub Copilot
- 1M+ paid subscribers; 30%+ of code written with Copilot in some projects
- Codex (2021) → GPT-4 Copilot (2023) → custom models
- FIM training on 159GB of public GitHub code
- Latency target: ghost text appears within 100ms
- Acceptance rate: ~30-40% of suggestions accepted
- Measured productivity impact: 55% faster task completion (GitHub study)

### Cursor IDE
- Built entirely around LLM-first code editing
- Multi-file edit: select code across files → natural language edit instruction
- Codebase chat: embed entire repository; ask questions about it
- Composer: autonomous multi-step code generation with file creation

### Amazon CodeWhisperer
- Integrated into AWS environments
- Security scanning: built-in vulnerability detection
- Reference tracker: flags suggestions that match open-source code (copyright)
- Optimized for AWS SDK usage

### DeepSeek-Coder
- 33B model trained on 2T code tokens + 400B text tokens
- State-of-the-art open-source code model (before o1)
- Strong on HumanEval, MBPP, DS-1000 (data science)
- FIM training for completion scenarios

---

## 7. Tradeoffs

| Model | HumanEval | Latency | Cost | Context |
|-------|-----------|---------|------|---------|
| Codestral 7B (Mistral) | 81% | <1s | Free | 32K |
| DeepSeek-Coder 33B | 79% | 1-2s | Free/self-host | 16K |
| GPT-4o | 90% | 2-4s | API cost | 128K |
| Claude 3.5 Sonnet | 93% | 2-5s | API cost | 200K |
| o1 | 95%+ | 10-60s | 10× GPT-4o | 128K |

| Task | Best Approach |
|------|---------------|
| Single-line completion | Small fast model (Codestral, StarCoder2 3B) |
| Function generation | Mid-size model (GPT-4o, DeepSeek-Coder) |
| Complex algorithms | Reasoning model (o1, R1) |
| Bug fixing | Agent loop (Claude + tools) |
| Full feature implementation | Agent (Cursor, Claude Code) |

---

## 8. When to Use / When NOT to Use

### Use Code LLMs When:
- Well-specified tasks with clear success criteria (tests)
- Boilerplate generation (CRUD routes, test scaffolding, config files)
- Language translation (Python → JavaScript, pseudocode → code)
- Documentation generation (docstrings, README from code)
- Code review and bug detection

### Use Caution / Human Review Required:
- Security-sensitive code (authentication, encryption, payment processing)
- Novel algorithms without reference implementations
- Performance-critical code (LLM may not optimize for efficiency)
- Complex business logic (LLM doesn't know your domain rules)

---

## 9. Common Pitfalls

1. **Hallucinated APIs**: Models generate plausible but non-existent function calls (`pandas.read_json_fast()`). Always test before deploying.
2. **Insecure code**: SQL injection, path traversal, hard-coded secrets in suggestions. Use security scanning.
3. **Copyright issues**: Completions that reproduce copyrighted code. GitHub Copilot has reference tracker; consider alternatives.
4. **Over-trust in completions**: Developers accepting suggestions without reading leads to bugs. Treat completions as drafts, not answers.
5. **Long completion quality**: Completions beyond 50-100 lines degrade quickly. Use for short snippets; write long code in multiple short iterations.
6. **Context window exhaustion**: Large repositories need smart context selection — you can't fit everything in the prompt.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **GitHub Copilot** | IDE completion | Most widely used; subscription model |
| **Cursor** | AI-first IDE | Multi-file editing; codebase chat |
| **Continue.dev** | Open IDE plugin | Self-hosted models; privacy-first |
| **Codeium** | Free completion | Personal use free; strong quality |
| **Tabby** | Self-hosted Copilot | Open source; privacy; multiple models |
| **Claude Code (Anthropic)** | Terminal agent | Autonomous coding; file editing |
| **StarCoder2** | Open code model | HuggingFace; strong base model |
| **DeepSeek-Coder** | Open code model | Best open quality; 7B-33B |
| **Codestral** | Open code model | Mistral; 32K context; fast |
| **SWE-agent** | Autonomous bug fixing | Princeton; SWE-bench |
| **aider** | Terminal AI coding | Open source; Claude/GPT backend |

---

## 11. Interview Questions with Answers

**Q: What is Fill-in-the-Middle (FIM) and why is it important for code completion?**
A: FIM trains the model to predict a middle section given both the prefix (code before cursor) and suffix (code after cursor). Standard autoregressive training only predicts suffixes. For IDE completion, the cursor is often mid-function, surrounded by existing code — FIM enables the model to complete this middle portion coherently. Training format: shuffle documents into [suffix][prefix][middle] order; the model learns the mapping.

**Q: How does GitHub Copilot gather context for a completion?**
A: Copilot collects: (1) current file with cursor position; (2) other open tabs, ranked by recency and relevance; (3) import statements and symbol definitions from the language server; (4) recently viewed files; (5) optionally, repository-indexed snippets similar to current code (enterprise). It assembles this into a FIM-formatted prompt, sending prefix + suffix + adjacent code snippets. The challenge is fitting everything into the context window while prioritizing the most relevant code.

**Q: What is SWE-bench and why is it a better benchmark than HumanEval?**
A: SWE-bench consists of 2294 real GitHub issues with their corresponding patches. The task: given the issue description and codebase, generate a patch that resolves the failing tests. It's harder than HumanEval because: (1) tasks are multi-file, not single-function; (2) requires understanding existing codebase structure; (3) real-world bugs are messier than toy problems; (4) evaluation is via test suite, not simple output comparison. HumanEval is essentially "solved" (~90%+); SWE-bench is still challenging (71% with o3 + scaffolding as of 2025).

**Q: What are the main security risks in LLM code generation?**
A: (1) Injection vulnerabilities: LLMs learn SQL injection, XSS, command injection patterns from insecure training code; (2) Hard-coded secrets: models suggest API keys, passwords found in training data; (3) Insecure cryptography: models may suggest outdated algorithms (MD5, DES); (4) Path traversal: models may suggest file operations without input validation; (5) Copyright: models may reproduce licensed code verbatim. Mitigations: static analysis (Semgrep, CodeQL) on generated code; secret detection; security-focused post-processing.

---

## 12. Best Practices

1. **Always run generated code** — never deploy without testing; treat generations as drafts.
2. **Use tests as specification** — write tests first; have the LLM write code to pass them (TDD + LLM = great).
3. **Iterate in small steps** — ask for one function at a time, not entire systems.
4. **Review for security** — always check for injection, hard-coded credentials, and insecure operations.
5. **Use the right model for the task** — small fast model for completion; large capable model for complex logic.
6. **Feed context explicitly** — paste relevant code/docs into the prompt; don't assume the model knows your codebase.

---

## 13. Case Study: Building a Repository-Level Code Assistant

**Problem:** Dev team at startup wants an AI assistant that understands their entire codebase and can answer questions like "How does payment processing work?" and generate new features consistent with existing patterns.

**Architecture:**
```
Indexing (run once, updated incrementally):
  Parse all code files: Python, TypeScript, SQL (300 files, 150K LOC)
  Chunk at function/class level (semantic boundaries)
  Add metadata: file path, language, class name, docstring
  Embed with text-embedding-3-large (code fine-tuned)
  Store in Qdrant with metadata filters

Query pipeline:
  User: "Add a new endpoint to list all orders by customer_id"

  Step 1: Retrieve relevant context
    Search: "order listing endpoint", "customer API", "routes"
    Retrieve top-5 functions: existing route handlers, models, schemas

  Step 2: Construct prompt
    System: "You are a senior engineer on this codebase. Match existing patterns."
    Context: [5 retrieved code examples showing existing patterns]
    Request: [user's task]

  Step 3: Generate with Claude 3.5 Sonnet (200K context)
    Model generates: route handler, model query, schema, test

  Step 4: Validation
    Syntax check (AST parse)
    Type check (mypy)
    Run existing test suite in sandbox
    Flag any security patterns
```

**Results:**
- Engineers spend 40% less time on feature implementation
- Generated code passes code review 73% of the time on first try (vs. estimate of 30% expected)
- Time to implement a new CRUD endpoint: 8 minutes (vs. 45 minutes before)
