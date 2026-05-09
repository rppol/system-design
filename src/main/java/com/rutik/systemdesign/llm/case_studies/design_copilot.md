# Case Study: Design GitHub Copilot

## Intuition

> **Design intuition**: Copilot is a real-time system with < 300ms latency requirements — this means aggressive caching, small fast models for completions, debouncing to avoid wasting calls, and Fill-in-the-Middle (FIM) model architecture. The engineering challenge is delivering sub-second IDE-integrated AI while managing cost and maintaining quality.

**Key insight for this design**: Two separate latency regimes with different models: fast inline completions (300ms, small FIM model) and slower chat interactions (2-5s, large capable model). Caching at multiple levels (prefix cache, semantic dedup of similar completions) is essential to meet latency targets at scale.

---

## 1. Requirements Clarification

### Functional Requirements
- Real-time code completions as the developer types (inline suggestions)
- Multi-line code generation from natural language comments
- Context-aware completions using current file, open tabs, and repository structure
- Support for 30+ programming languages
- Accept/reject/cycle through suggestions
- Copilot Chat: conversational code assistance in IDE
- Agent mode: autonomous multi-file edits (Copilot Workspace)

### Non-Functional Requirements
- **Latency**: Inline completions < 300ms (imperceptible to typing pace)
- **Availability**: 99.9% uptime (developer tools must be reliable)
- **Scale**: 1M+ active developers; 20B+ completions per day
- **Privacy**: User code must not leak to other users; GDPR compliance
- **Accuracy**: > 30% acceptance rate on suggestions (baseline quality metric)

### Out of Scope
- Code execution or testing
- Version control operations (git)
- Pull request creation (handled by separate Copilot PRs feature)

---

## 2. Scale Estimation

### Traffic Estimates
```
Active developers: 1M (GitHub Copilot reported 1.3M paid subscribers in 2023)
IDE events per developer per hour: 500 (keystrokes, cursor moves)
Completion requests triggered: 10% of events = 50 requests/hour/developer
Daily (8-hour workday): 50 × 8 = 400 requests/developer/day

Total requests/day: 1M developers × 400 = 400M requests/day
Peak QPS (workday 10am-2pm): 400M / 86,400 × 4 = ~18,500 req/sec

Token estimates:
  Average context sent: 1,500 tokens (current file + nearby code)
  Average completion: 40 tokens (1-5 lines of code)
  Total per request: 1,540 tokens
  Daily: 400M × 1,540 = 616B tokens/day
```

### Latency Budget
```
End-to-end budget: 300ms

- IDE debounce (don't send on every keystroke): 75ms
- Network round trip (edge proximity): 30ms
- Request processing (auth, rate limit): 10ms
- Context assembly (file parsing, token counting): 15ms
- Inference (Codex/model): 120ms  ← dominant cost
- Response decode + streaming: 10ms
- IDE rendering: 20ms
Total: ~280ms  ← under budget
```

---

## 3. High-Level Architecture

```
IDE (VS Code / JetBrains / vim)
  [Copilot Extension]
    - Debounce (75ms after last keystroke)
    - Context collector (current file, cursor position)
    - Cache: local LRU for recent completions
         |
         | HTTPS/2 (persistent connection)
         v
[Edge PoP] (Microsoft Azure CDN / GitHub edge)
  - Geographic proximity (30ms RTT target)
  - TLS termination
  - Request forwarding
         |
         v
[API Gateway]
  - Authentication (GitHub OAuth token)
  - Rate limiting (requests per developer per minute)
  - Request routing (inline vs chat vs workspace)
         |
    _____|______
   |            |
   v            v
[Context      [Auth &
 Service]      Telemetry]
 - RAG over    - Token validation
   repository  - Usage tracking
 - Open tabs   - Billing events
 - Recent edits
   |
   v
[Prompt Builder]
  - Assemble final prompt (system + context + cursor)
  - Token budget management
  - Format: FIM (Fill-In-the-Middle)
   |
   v
[Inference Router]
  - Select model tier (completion vs chat)
  - Load balance across GPU clusters
  - Handle streaming
   |
   v
[GPU Inference Cluster]
  - Codex / GPT-4o (inline completions)
  - GPT-4o (Copilot Chat)
  - vLLM / TRT-LLM with speculative decoding
   |
   v
[Post-processing]
  - Filter unsafe code
  - Trim redundant completions
  - License filter (check for verbatim copies)
   |
   v
IDE Extension → renders ghost text suggestion
```

---

## 4. Component Deep Dives

### 4.1 Context Collection

```
What gets sent with each completion request:

Priority 1 (always included):
  - Current file: content before cursor (prefix) + content after cursor (suffix)
    Total: up to 1,000 tokens
  - Language + file extension
  - Cursor position (line, column)
  - Current function/class context (AST-derived)

Priority 2 (if budget allows):
  - Recently edited files (last 3 open tabs)
  - Content most similar to cursor location (BM25 similarity)
  - Import statements from other files
  Total: additional ~500 tokens

Priority 3 (if budget still allows):
  - Repository-level context (Copilot Workspace)
  - Project README, configuration files
  - Recently viewed but not open files

Budget management:
  Total context limit: 1,500 tokens
  Leave room for completion: 200 tokens
  Context window = 1,500 - current file tokens - overhead

Context assembly time: < 15ms (runs async while debounce timer fires)
```

### 4.2 FIM (Fill-In-the-Middle) Prompt Format

```
Standard completion (cursor at end of file):
  PROMPT: [file prefix up to cursor]
  Expected: [code continuation]

FIM format (cursor in middle of file):
  <PRE> [code before cursor] <SUF> [code after cursor] <MID>
  Model fills in: [completion that connects PRE to SUF]

Example:
  File:
    def calculate_average(numbers):
        [cursor here]
        return total / len(numbers)

  FIM prompt:
    <PRE>def calculate_average(numbers):\n    <SUF>\n    return total / len(numbers)\n<MID>

  Model completion:
    total = sum(numbers)\n

  Result:
    def calculate_average(numbers):
        total = sum(numbers)
        return total / len(numbers)

FIM training (StarCoder, Codex):
  - During pre-training, randomly split code into prefix/suffix/middle
  - Three tasks: SPM order (suffix first), PSM order (prefix first)
  - Model learns to complete any position, not just end-of-file
```

### 4.3 Repository-Level Context (Copilot Workspace)

```
Challenge: Modern codebases are 100K-1M tokens. Can't fit everything in context.

Solution: Lightweight RAG over the repository

Step 1: Index repository
  - Parse all files → AST (Abstract Syntax Tree)
  - Extract: function signatures, class names, docstrings, type hints
  - Embed each function/class as a semantic chunk
  - Store in local vector index (FAISS, embedded in IDE extension)

Step 2: Query at completion time
  - Embed current context (cursor surrounding code)
  - ANN search in local index
  - Retrieve top-10 most relevant function signatures

Step 3: Inject into context
  [System]: You are completing code in a Python project.
  [Related symbols from repo]:
    - UserService.get_user(id: int) -> User
    - DatabaseManager.query(sql: str, params: dict) -> List[Row]
    - TIMEOUT = 30
  [Current file]:
    def fetch_user_data(user_id):
        [cursor]

Performance:
  - FAISS index built on first open: 5-30 seconds for large repos
  - Incremental updates on file save: < 100ms
  - Query: < 5ms (local, in-memory)
```

### 4.4 Speculative Decoding for Low Latency

```
Problem: GPT-4 completion of 40 tokens takes 150ms+ on A100.
         Developers feel >300ms total latency.

Solution: Speculative decoding

                Small draft model (GPT-3.5 equivalent, 6B params)
                         |
         Generates 8 tokens speculatively (fast, ~10ms)
                         |
                         v
                Large verifier model (GPT-4o, 200B params)
                         |
         Verifies all 8 tokens in single forward pass (parallel attention)
                         |
                         v
         Accept: all 8 correct → 8 tokens for price of 1 verification
         Reject at position k: discard tokens k+, regenerate from k

Math:
  Without speculative: 40 tokens × 4ms/token = 160ms
  With speculative (70% accept rate on code):
    Avg accepted per draft cycle: 0.7 × 8 = 5.6 tokens
    Verification: 1 forward pass = 15ms per 8 drafts
    Effective time: 40 tokens / 5.6 tokens × 15ms = 107ms
  Speedup: 160ms → 107ms = 1.5× faster (additional savings at higher accept rates)

Code-specific accept rates:
  Repetitive patterns (loops, boilerplate): ~85% accept rate → 2-3× speedup
  Novel logic: ~50% accept rate → 1.2× speedup
```

### 4.5 License Filter

```
Problem: GitHub repos contain copyrighted code. Copilot might regurgitate
         verbatim copied code, creating legal risk.

Solution: Duplication detection filter

Training-time:
  - Hash all training code into Bloom filter
  - Track n-gram hashes of all training files
  - Store: (hash → source file + license) index

Inference-time:
  For each generated completion:
  1. Extract all 20-token n-grams
  2. Check n-gram hashes against training index
  3. If >50% of n-grams match a specific training file:
     → Flag as potential verbatim copy
     → Include source file + license in response metadata

Setting: "Suggestions matching public code"
  - On (default): block completions that match public code exactly
  - Off: show completions but flag with source attribution

Legal note: This remains an active area of litigation (GitHub Copilot class action).
```

---

## 5. Copilot Chat Architecture

```
Chat is different from inline completions:

1. Longer context: full conversation history + selected code
2. Higher latency acceptable: developers expect 2-5 seconds for chat
3. Tool use: can call functions (/explain, /fix, /test, /doc)
4. Streaming: token-by-token display in chat panel

Chat prompt structure:
  [System]: You are an expert programmer. Help the developer understand
            and improve their code. Be concise and accurate.
  [Context]: Current file: [file content]
             Selected code: [highlighted selection]
  [User]: /explain what does this function do?
  [Assistant]: ...
  [User]: can you add error handling?

Special commands:
  /explain → prepend "Explain the following code:" to query
  /fix     → prepend "Identify and fix bugs in:" + code
  /tests   → prepend "Generate unit tests for:" + code
  /doc     → prepend "Generate documentation for:" + code

These are just prompt templates, not special model fine-tuning
```

---

## 6. Data Privacy Design

```
Concerns:
  - Developer code is proprietary IP
  - Code may contain credentials, API keys
  - Company code may be under NDA

Mitigations:

1. Data transmission:
   - TLS 1.3 in transit (end-to-end encryption)
   - No code stored on GitHub servers beyond request (Copilot Business)
   - Code used only for completion, not for model training (opt-out)

2. Training data control (Copilot Business vs Individual):
   Individual: code snippets may be used to improve Copilot models
   Business/Enterprise: snippets never retained, never used for training

3. Secret detection:
   - Before sending to API: scan for patterns matching secrets
     (AWS_ACCESS_KEY, private key headers, JWT tokens)
   - Either redact or warn developer
   - Doesn't send raw credentials to model

4. Organizational policies:
   - Enterprise can block specific repositories from Copilot
   - Can enforce "no suggestions from public code"
   - Audit logs of all Copilot usage
```

---

## 7. Observability and Quality

```
Key metrics:

Acceptance rate (primary quality metric):
  accepted_completions / shown_completions
  Target: > 30% (reported ~34% for GitHub Copilot)
  Breakdown by: language, file type, time of day, model version

Completion quality tracking:
  - A/B test model versions (5% of traffic to new model)
  - Track: acceptance rate, edit distance of accepted completions
  - Edit distance: how much developer modified suggestion (lower = better)
  - "Kept as is" rate: suggestion accepted without any edits

Latency SLOs:
  P50 < 150ms (median feels instant)
  P95 < 300ms (near-instant for 95% of completions)
  P99 < 600ms (slow but acceptable for 1% of requests)

Error tracking:
  - Authentication failures (expired tokens)
  - Timeout rate (> 300ms → completion cancelled)
  - Model errors (context too long, unsafe content)

Usage analytics:
  - Lines of code suggested vs accepted vs edited
  - Languages most used
  - Time-of-day patterns (engineer productivity patterns)
```

---

## 8. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Debounce | 75ms | 0ms (every keystroke) | 10× fewer requests; within typing flow UX |
| Context | FIM (prefix + suffix) | Prefix only | FIM generates more accurate completions |
| Cache | Local LRU (IDE) | Server-side cache | Privacy; instant retrieval for recent completions |
| Repo indexing | Local FAISS | Remote vector DB | Zero latency; no code leaves machine |
| License filter | Training-time hash index | Runtime search | Millisecond lookup vs seconds |
| Chat model | GPT-4o | GPT-3.5 | Quality requires best model; latency acceptable |
| Multi-line | Generate up to N lines | Always single line | Better UX for boilerplate; trim on mismatch |

---

## 9. Copilot Workspace (Agent Mode)

```
Goes beyond single-file completions to autonomous multi-file edits:

User: "Add input validation to all API endpoints"
         |
         v
[Intent understanding]
  Parse task: what files? what changes? what constraints?
         |
         v
[Repository analysis]
  Identify: all files with API endpoint handlers
  Understand: current validation patterns, frameworks used
         |
         v
[Plan generation]
  1. Add validate_input() helper to utils.py
  2. Import in api/users.py, api/products.py, api/orders.py
  3. Add @validate decorator to each endpoint handler
         |
         v
[Multi-file execution]
  For each planned change:
    Read file → generate edit → show diff → apply on approval
         |
         v
[Human review]
  Show all diffs in PR-like view
  Developer accepts/modifies/rejects each change
         |
         v
Apply accepted changes to repository

Key challenge: repository coherence
  Edits across files must be consistent
  Solution: shared context (recent edits) passed to each subsequent edit
```

---

## 10. Interview Discussion Points

**Why is 300ms the target latency?** Human typing speed is ~200ms between keystrokes. If completions appear within 300ms of stopping typing, they feel instantaneous. Above 500ms, developers notice the delay. Above 1s, developers move on before the suggestion appears.

**Key bottleneck: GPU inference.** At 18,500 req/sec with 120ms inference time, you need 18,500 × 0.12 = 2,220 concurrent requests in flight at once. A100s with continuous batching can handle ~100 concurrent code completions = 22 A100s minimum, with safety margin → 50+ A100s.

**Context window trade-off:** More context = better completions, but longer prompts = higher latency + cost. Copilot's 1,500 token context is a deliberate balance: empirically, most useful context is in the current file and recently opened tabs. Additional repository context provides diminishing returns for inline completions.

**Privacy tension:** Individual developers want personalized completions (requiring code context). Enterprise customers want zero data retention (requiring stateless API). GitHub handles this with two tiers: Copilot Individual (may use code) vs Business (no retention, higher price).

**Acceptance rate as product health metric:** If acceptance rate drops from 34% to 25%, it could mean model degraded, or context collection broke, or a language-specific regression. Tracking acceptance rate by language, file type, and model version enables rapid root cause analysis.
