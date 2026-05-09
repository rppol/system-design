# Case Study: Design an AI Coding Assistant (Cursor/Devin-Style)

## Intuition

> **Design intuition**: An AI coding assistant (Cursor/Devin-style) has three distinct interaction modes with fundamentally different latency requirements: inline completions (300ms), chat (2-5s), and autonomous agents (minutes). Each mode requires different models, different context assembly, and different serving infrastructure.

**Key insight for this design**: The repository context problem is central — an LLM seeing only the current file gives poor completions; seeing the entire 500K-token codebase is expensive. The solution is a retrieval layer (embedding-based file selection + dependency graph analysis) that assembles the most relevant 20-50K token context for each request.

---

## 1. Requirements Clarification

### Functional Requirements
- Inline code completions as developer types (< 300ms)
- Chat interface for code questions, explanations, and refactoring
- Autonomous multi-file editing: "add unit tests for all functions in auth module"
- Codebase-aware: understands full repository structure and dependencies
- Multi-language: Python, JavaScript/TypeScript, Java, Go, Rust, C++
- Terminal integration: run commands, observe output, fix errors autonomously
- Git-aware: can create branches, make commits, generate PR descriptions
- Tool use: web search for documentation, run tests, linter feedback

### Non-Functional Requirements
- **Completion latency**: < 300ms for inline suggestions
- **Chat latency**: < 3s first token, complete in < 15s
- **Agent task latency**: minutes (autonomous tasks can take 2-30 minutes)
- **Privacy**: code stays local or in private tenant; no training on user code
- **Scale**: 500K developer seats; 5M completions/hour peak

### Out of Scope
- Code execution environment (use existing Docker/CI infrastructure)
- Version control hosting (use GitHub/GitLab)
- Code review approval workflows

---

## 2. Scale Estimation

### Traffic Estimates
```
Developer seats: 500K
Active at peak (10am-2pm): 30% = 150K concurrent developers
Completions per active developer per hour: 30 (after debounce/filtering)
Peak completion QPS: 150K × 30 / 3600 = 1,250 req/sec

Chat queries: 10/developer/hour
Peak chat QPS: 150K × 10 / 3600 = 416 req/sec

Agent tasks: 2/developer/day
Daily agent tasks: 500K × 2 = 1M tasks/day

Token estimates:
  Completion: 1,500 input + 40 output = 1,540 tokens
  Chat: 3,000 input + 500 output = 3,500 tokens
  Agent task: 10,000 input + 2,000 output per step × 10 steps = 120,000 tokens
```

### Context Budget
```
Codebase sizes:
  Small (startup): < 50K tokens
  Medium (scale-up): 50K-500K tokens
  Large (enterprise): 500K-5M tokens

Strategy by codebase size:
  < 50K tokens: put entire codebase in long context (Gemini 1.5 Pro or Claude 3.5)
  50K-500K tokens: smart RAG selection (top-20 most relevant files)
  > 500K tokens: strict RAG (top-10 files by relevance + dependency graph)
```

---

## 3. High-Level Architecture

```
Developer IDE (VS Code / JetBrains)
  [Extension]
    - File watcher (detect changes)
    - Context collector (open files, cursor, git status)
    - Local cache (recent completions, codebase index)
    - WebSocket connection to backend
         |
         v
[Edge Server] (per-region, low latency)
  - Connection management (WebSocket)
  - Auth token validation
  - Request priority queue (completions > chat > agent)
         |
         v
[Core Services]
  ┌─────────────────────────────────────────────────┐
  │                                                  │
  │  ┌──────────────┐  ┌─────────────┐              │
  │  │  Completion  │  │    Chat     │              │
  │  │  Service     │  │  Service    │              │
  │  └──────────────┘  └─────────────┘              │
  │         |                 |                      │
  │         v                 v                      │
  │  ┌──────────────────────────────────┐            │
  │  │       Context Assembly Service   │            │
  │  │  - Codebase RAG (repo index)     │            │
  │  │  - File dependency graph         │            │
  │  │  - Open tabs + recent edits      │            │
  │  │  - LSP symbols (types, imports)  │            │
  │  └──────────────────────────────────┘            │
  │                    |                              │
  │                    v                              │
  │  ┌──────────────────────────────────┐            │
  │  │         Model Orchestrator       │            │
  │  │  - Inline: fast model (< 300ms)  │            │
  │  │  - Chat: quality model (GPT-4o)  │            │
  │  │  - Agent: reasoning model (o3)   │            │
  │  └──────────────────────────────────┘            │
  │                    |                              │
  │                    v                              │
  │  ┌──────────────────────────────────┐            │
  │  │         Agent Engine             │            │
  │  │  - Tool execution (code runner)  │            │
  │  │  - Multi-step planning           │            │
  │  │  - Error recovery loop           │            │
  │  └──────────────────────────────────┘            │
  └─────────────────────────────────────────────────┘
         |
    [Tool Services]
    - Code Runner (Docker sandboxes)
    - Web Search (docs, Stack Overflow)
    - Git Client (branch/commit operations)
    - Linter/Type Checker (ESLint, mypy)
    - Test Runner
```

---

## 4. Component Deep Dives

### 4.1 Repository Indexing and RAG

```
Local index (built in IDE extension, lives on developer's machine):

What gets indexed:
  1. All source files (Python, JS, Java, etc.)
  2. Configuration files (package.json, requirements.txt, Dockerfile)
  3. Test files (important for agent tasks)
  4. README and documentation

Index structure (built with Tree-sitter AST parsing):
  Function index:
    {name, file_path, line_start, line_end, signature, docstring, body_hash}
  Class index:
    {name, file_path, methods[], parent_class, imports}
  Symbol index:
    {symbol, type (function/class/variable), defined_at, used_at[]}
  Import graph:
    {file → imports_from[]}  # for dependency traversal

Embedding index:
  Each function/class → embedded as code unit
  Stored in local FAISS index (SQLite backing)
  Index build time: 30-120 seconds for 1M lines of code
  Incremental update on file save: < 100ms

Query at completion time:
  1. Embed current code context (cursor surroundings)
  2. ANN search in local FAISS index
  3. Return top-10 relevant functions/classes
  4. Inject into prompt as "Related symbols from codebase"

Dependency graph traversal (for agent tasks):
  If editing users.py → also include: models/user.py, db/user_repository.py
  Traverse: import graph to find directly related files
  Max depth: 2 levels of imports (avoid context explosion)
```

### 4.2 Completion Service (< 300ms)

```
Tight latency budget breakdown:
  IDE debounce: 75ms (don't trigger on every keystroke)
  Network (edge PoP): 20ms RTT
  Context assembly (local index query): 10ms
  Prompt building: 5ms
  LLM inference: 150ms  ← must be fast
  Network return + render: 20ms
  Total: ~280ms

Model selection for completions:
  Requirement: fast inference, good code quality, FIM support
  Options:
    Codestral (Mistral, 22B, FIM-native): excellent
    DeepSeek-Coder-1.3B (quantized, local): 30ms but lower quality
    GPT-3.5-turbo: not FIM-native; slightly worse for mid-file completions
    Cursor uses: proprietary cursor-small for completions, GPT-4o for chat

Completion prompt (FIM format):
  <file_path>src/auth/users.py</file_path>
  <related_context>
  # From db/user_repository.py:
  def get_user_by_id(id: int) -> Optional[User]:
  def create_user(email: str, password_hash: str) -> User:
  </related_context>
  <prefix>
  def authenticate(email: str, password: str) -> Optional[str]:
      user = UserRepository.get_user_by_email(email)
      if user is None:
          return None
  </prefix>
  <suffix>
      return token
  </suffix>
  <mid>

  Expected completion: [code that verifies password and generates JWT token]

Local cache:
  LRU cache of recent completions by (file_hash, cursor_position)
  If file unchanged and cursor near same position → return cached
  Cache hit rate: 25% (saves 75ms + backend request)
```

### 4.3 Chat Service

```
Chat is where developers ask complex questions about their code.

Session context management:
  Window: current file (full) + conversation history (10 turns)
  Available budget: 128K context - current file - conversation
  Fill with: top-5 relevant codebase files from RAG

Special chat commands:
  @file src/auth.py → include entire file in context
  @folder src/api/  → include all files in folder (if fits in context)
  #explain          → trigger explanation template
  #refactor         → include refactoring guidelines in system prompt
  #tests            → set mode to test generation

Supported query types:
  Code explanation: "explain this function"
  Bug analysis: "why is this test failing?" + test output
  Refactoring: "refactor this to use the repository pattern"
  Documentation: "write docstrings for all public methods"
  Code review: "review this PR diff for issues"
  Architecture: "how should I structure the database layer?"

Chat prompt:
  [System]
  You are an expert software engineer with deep knowledge of {detected_languages}.
  The developer is working in a {repo_type} codebase.
  Provide clear, concise answers. Include code examples when helpful.
  When modifying code, show the full modified function (not just the changed part).

  [Codebase context]
  ... (top-5 relevant files from RAG) ...

  [Current file]
  ... (full current file if < 50K tokens) ...

  [Conversation]
  ... (last 10 messages) ...

  [User]
  {user_message}
```

### 4.4 Agent Engine (Autonomous Multi-File Editing)

```
Agent tasks: "Add unit tests for all functions in the auth module"

This requires: planning, file operations, code generation, test running, error fixing.

Architecture: ReAct loop with tool use

Tools available to agent:
  read_file(path) → file content
  write_file(path, content) → apply edit
  create_file(path, content) → new file
  run_terminal(command) → stdout, stderr, exit_code
  search_codebase(query) → relevant symbols
  web_search(query) → documentation results
  git_status() → current changes
  git_commit(message) → commit changes

Agent loop:
  WHILE task_not_complete AND steps < max_steps:
    1. [Observe] Gather current state (files, test results, errors)
    2. [Plan] Reason about next action (chain-of-thought reasoning)
    3. [Act] Execute one tool call
    4. [Reflect] Observe result; update plan if unexpected

Example execution for "add unit tests for auth module":

Step 1: search_codebase("auth module functions")
  → Found: authenticate(), create_user(), refresh_token() in auth/users.py

Step 2: read_file("auth/users.py")
  → Read file; understand function signatures and logic

Step 3: read_file("tests/test_auth.py")
  → File exists; currently has 2 tests; need to add 10 more

Step 4: web_search("pytest unit testing auth JWT tokens best practices")
  → Found: patterns for mocking JWT, testing edge cases

Step 5: write_file("tests/test_auth.py", [new content with all tests])
  → File written

Step 6: run_terminal("pytest tests/test_auth.py -v")
  → 8/12 tests pass; 4 fail: TypeError on mock_db fixture

Step 7: [Analyze failure] → understand fixture setup issue

Step 8: write_file("tests/test_auth.py", [fixed content])
  → Updated conftest.py fixture usage

Step 9: run_terminal("pytest tests/test_auth.py -v")
  → 12/12 tests pass

Step 10: git_commit("Add comprehensive unit tests for auth module")
  → Committed

Task complete. Summary: Added 10 unit tests, fixed fixture issue, all passing.

Model for agent tasks:
  Use: Claude 3.5 Sonnet, GPT-4o, or o3 (for complex planning)
  o3/o4 preferred: reasoning models handle multi-step planning better
  Each agent step: 1-2 LLM calls
  Average task: 10-20 steps = 20-40 LLM calls per agent task
```

### 4.5 Code Execution Sandbox

```
Agent runs terminal commands → must be secure, isolated, controlled.

Sandbox architecture (Docker-based):
  Per agent task: dedicated container
    Image: base language image + project dependencies
    Resources: 2 CPU, 4GB RAM, 10GB disk
    Network: isolated (no outbound except package registries)
    Timeout: 30 minutes max per agent task

Container lifecycle:
  Task start → create container (warm pool of pre-created containers)
  Code changes → sync files to container via volume mount
  Terminal command → exec in container → capture stdout/stderr
  Task complete → destroy container

Warm container pool:
  Pre-create 100 containers per language (Python, Node, Java, Go)
  Container creation takes 5-10 seconds → warm pool = instant start
  Auto-scale pool based on agent task queue depth

Security constraints:
  No: outbound network to arbitrary hosts (only npm/pypi/maven registries)
  No: privileged operations (no sudo, no /proc access)
  No: access to host filesystem (volume mount is isolated project copy)
  Rate limit: 100 commands per task (prevent infinite loops)

Output handling:
  Stdout/stderr: streamed to agent and developer in real time
  Large outputs (test logs): truncated to last 5,000 characters
  Exit codes: non-zero triggers agent error analysis
```

---

## 5. Privacy Architecture

```
Enterprise requirements: code never leaves the company network

Deployment modes:
  1. Cloud (default): code sent to API (encrypted in transit, not stored)
  2. Private Cloud: deploy assistant in customer's VPC (AWS/GCP/Azure)
  3. On-premise: full deployment in customer data center

For on-premise / private cloud:
  Self-hosted LLMs: Llama 3 70B or Qwen-Coder-32B
  Local inference: vLLM cluster inside customer network
  Local index: FAISS index on developer's machine or local server
  No external API calls: Bing search replaced with internal docs
  Audit log: stored in customer's own storage

Code handling policy:
  - Code snippets: used for inference, not stored after response
  - No training: user code never used to train or fine-tune models
  - Embeddings: computed locally (IDE extension), not sent to cloud
  - BAA available: for HIPAA-covered code (e.g., healthcare companies)
```

---

## 6. Quality Metrics

```
Completion quality:
  Acceptance rate: accepted / shown (target > 30%)
  Edit distance: chars changed in accepted suggestion (lower = better)
  "Kept as is" rate: suggestion accepted without any edits (target > 20%)

Chat quality:
  Developer satisfaction: thumbs up/down feedback (target > 80% positive)
  Code correctness: does suggested code compile/run? (tracked async)
  Hallucination rate: code references non-existent functions

Agent quality:
  Task completion rate: % of agent tasks fully completed (target > 70%)
  Human intervention rate: % that required developer fixes
  Test pass rate: % of generated code that passes tests on first try
  Lines of code generated per task: productivity metric

Latency SLOs:
  Completion P95 < 300ms
  Chat first-token P95 < 2s
  Agent step P95 < 10s

Codebase index quality:
  Index coverage: % of symbols indexed vs total
  RAG recall: % of relevant files found for sample queries
  Index freshness: time since last update < 5 seconds
```

---

## 7. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Completion model | Codestral 22B (FIM-native) | GPT-3.5 | FIM-native gives better mid-file completions |
| Context collection | Hybrid (local FAISS + LSP) | Pure embedding RAG | LSP gives precise type info; FAISS for semantic |
| Repo index | Local (developer machine) | Remote server | Zero latency; privacy (code stays local) |
| Agent model | Claude 3.5 Sonnet / o3 | Smaller models | Agent tasks need strong reasoning; cost acceptable |
| Sandbox | Docker (warm pool) | VM | Container start time 100ms (warm); VM = 30s |
| Network access | IDE WebSocket persistent | HTTP polling | Low latency; instant push; efficient |
| Privacy | On-premise option | Cloud only | Enterprise requirement; enables regulated industries |

---

## 8. Interview Discussion Points

**Why local embedding index beats remote for completions.** At < 300ms total budget, a round-trip to a remote vector DB (50ms+ latency) would consume 17% of the budget before inference. Local FAISS queries in < 5ms. The downside: index must be maintained on developer's machine and kept in sync. For code that changes frequently, incremental updates on file save keep the index fresh.

**The agent reliability problem.** The hardest challenge isn't generating code — it's ensuring the agent doesn't make wrong assumptions, break working code, or loop endlessly. Mitigations: (1) always run tests after changes; (2) use git to checkpoint before each agent task (easy rollback); (3) surface all changes to developer before committing; (4) set hard limits (max steps, max file edits).

**Why multi-model architecture?** Completions need speed (Codestral); chat needs quality (GPT-4o); agent tasks need planning (o3/Claude). Using one model for all three would mean: either too slow for completions (GPT-4o) or too weak for agent planning (small model). The 10ms routing decision unlocks 10× cost optimization.

**Latency-accuracy trade-off in FIM.** Providing more context (open tabs, dependency files) improves completion quality but adds tokens and increases latency. Cursor's empirical finding: beyond 1,500 tokens of context, completion quality improvement is marginal but latency increases linearly. The context budget is a product decision, not just a technical one.

**Agent scope limitation.** Unlike Devin (tries to fully autonomously complete engineering tasks), a coding assistant should work in tight collaboration: propose changes → show diff → require approval → apply. This "human-in-the-loop" design sacrifices some autonomy for safety and trust. Most enterprise teams prefer assisted autonomy over full autonomy.
