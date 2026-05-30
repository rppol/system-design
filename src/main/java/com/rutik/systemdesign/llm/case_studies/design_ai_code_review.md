# Case Study: Design an AI Code Review System (CodeRabbit / Copilot Code Review)

## Intuition

> **Design intuition**: An AI code review system is an event-driven pipeline triggered by pull request webhooks -- it must parse diffs, reconstruct surrounding repository context (imports, class hierarchies, test files), run specialized detection passes (security, performance, style), and post prioritized, actionable comments back to the PR. The core challenge is assembling enough repository context to avoid false positives while staying within LLM context limits and completing reviews within 60 seconds.

**Key insight for this design**: A single monolithic LLM call reviewing an entire PR produces vague, low-signal comments. The production-winning architecture uses a multi-pass pipeline: (1) diff parsing and context loading, (2) parallel specialized analysis passes (security, performance, style, logic), (3) confidence scoring and deduplication, (4) comment generation with code suggestions. Each pass uses a focused system prompt, yielding 3-5x higher precision than a single generic pass.

---

## 1. Requirements Clarification

### Functional Requirements
- Trigger automated review on every pull request (open, update, rebase)
- Analyze code diffs with full repository context (imports, type definitions, related tests)
- Detect bugs, logic errors, null pointer risks, race conditions
- Detect security vulnerabilities: SQL injection, XSS, hardcoded secrets, insecure deserialization, OWASP Top 10
- Detect performance anti-patterns: N+1 queries, unbounded queries, missing indexes, unnecessary allocations, blocking calls in async code
- Enforce configurable style rules consistent with existing codebase conventions
- Generate specific, actionable review comments with inline code suggestions (GitHub suggestion blocks)
- Prioritize comments by severity: critical > warning > suggestion > nitpick
- Integrate with CI/CD: run as GitHub Action / GitLab CI step, optional blocking gate for critical issues
- Track suggestion acceptance/dismissal to improve over time
- Support configurable sensitivity and false positive suppression

### Non-Functional Requirements
- **Latency**: Complete review within 60 seconds for PRs under 500 changed lines; 120 seconds for PRs up to 2,000 lines
- **Accuracy**: > 70% precision on flagged issues (< 30% false positive rate); > 50% acceptance rate on suggestions
- **Scale**: 50,000 PRs/day; peak 2,000 PRs/hour during US business hours
- **Availability**: 99.9% uptime (missing a review is tolerable; repeated failures erode trust)
- **Privacy**: Repository code processed for review only; never stored beyond review lifecycle; never used for model training
- **Cost**: < $0.50 per PR review on average (viable for team/org billing)

### Out of Scope
- Running tests or building the project (handled by existing CI)
- Merge/approval workflow management
- Automated code fixes (suggest only; human applies)

---

## 2. Scale Estimation

### Traffic Estimates
```
Organizations: 5,000 (mix of startups and enterprises)
Developers per org: 50 average
PRs per developer per day: 2
Total PRs/day: 5,000 x 50 x 2 = 500,000 PRs/day (optimistic target)
Initial realistic load: 50,000 PRs/day

Peak hour (10am-12pm US timezones): 50,000 / 24 x 4 = ~8,300 PRs/hour
Peak QPS: 8,300 / 3,600 = ~2.3 PR reviews/sec

Average PR size:
  Changed files: 5
  Changed lines per file: 60
  Total changed lines: 300
  Context lines needed per file: 200 (surrounding code, imports, types)
  Total tokens per PR: ~15,000 (diff + context + system prompts)

Token budget per review:
  Input tokens: 15,000 (diff + repo context + system prompt)
  Output tokens: 2,000 (review comments + explanations + suggestions)
  Total: 17,000 tokens per PR

Daily token usage:
  50,000 PRs x 17,000 tokens = 850M tokens/day
  At $3/M input + $15/M output (GPT-4o pricing):
    Input cost: 750M x $3/M = $2,250/day
    Output cost: 100M x $15/M = $1,500/day
    Total: $3,750/day = ~$0.075/PR average
  With multi-pass (3 passes average): $0.075 x 3 = $0.225/PR
```

### Latency Budget
```
End-to-end target: 60 seconds for typical PR (300 lines)

  Webhook receipt + queue: 500ms
  Diff fetch (GitHub API): 800ms
  Repository context loading: 2,000ms (fetch related files via API)
  Context assembly + chunking: 500ms
  Security analysis pass (LLM): 12,000ms
  Performance analysis pass (LLM): 12,000ms
  Logic/bug analysis pass (LLM): 12,000ms
  Style analysis pass (LLM): 8,000ms
  (Security + Performance + Logic run in parallel: max 12,000ms)
  (Style runs in parallel with above: 8,000ms, overlaps)
  Confidence scoring + dedup: 500ms
  Comment formatting + batching: 300ms
  Post comments to PR API: 1,500ms
  Total: ~18,100ms for typical PR

For large PRs (2,000 lines):
  Split into chunks of 500 lines
  4 chunks x 3 analysis passes = 12 LLM calls
  Parallel within each pass: 4 chunks concurrent
  Sequential passes: 3 x 15,000ms = 45,000ms
  With parallelism across passes: ~20,000ms
  Total with overhead: ~30,000ms
```

### Storage Estimates
```
Review results (for feedback learning):
  Per review: 5KB (comments, metadata, acceptance status)
  Daily: 50,000 x 5KB = 250MB/day
  Retention: 1 year = 91GB

Repository context cache:
  Per repo: 50MB average (file tree, symbol index, embeddings)
  5,000 orgs x 20 repos average = 100,000 repos
  Cache (hot repos only, LRU): top 10,000 repos = 500GB

Configuration store:
  Per org: 2KB (rules, sensitivity, ignore patterns)
  5,000 orgs = 10MB (trivially small)
```

---

## 3. High-Level Architecture

```
[GitHub / GitLab / Bitbucket]
  PR event (webhook: opened, synchronize, reopened)
         |
         v
[Webhook Receiver] (stateless, auto-scaled)
  - Verify webhook signature (HMAC-SHA256)
  - Parse event type + PR metadata
  - Enqueue review job
         |
         v
[Job Queue] (Redis Streams / SQS)
  - Priority: org tier (enterprise > team > free)
  - Dedup: skip if same PR head SHA already queued
         |
         v
[Review Orchestrator] (worker pool, 200 concurrent workers)
  ┌──────────────────────────────────────────────────────┐
  │                                                      │
  │  [Diff Parser]                                       │
  │   - Fetch PR diff via Git provider API               │
  │   - Parse hunks: file, line range, added/removed     │
  │   - Identify file types, languages                   │
  │                                                      │
  │  [Context Loader]                                    │
  │   - Fetch full file content for changed files        │
  │   - Resolve imports -> fetch imported modules         │
  │   - Fetch class/interface definitions referenced      │
  │   - Fetch related test files                         │
  │   - Load .codereview.yml config (ignore rules, etc.) │
  │                                                      │
  │  [Context Assembler]                                 │
  │   - Budget token allocation across files             │
  │   - Prioritize: changed code > direct imports >      │
  │     type definitions > test files > distant deps     │
  │   - Chunk large PRs into reviewable segments         │
  │                                                      │
  │  [Analysis Pipeline] (parallel passes)               │
  │   ┌─────────────┐ ┌─────────────┐ ┌──────────────┐  │
  │   │  Security   │ │ Performance │ │  Logic/Bug   │  │
  │   │  Analyzer   │ │  Analyzer   │ │  Analyzer    │  │
  │   └─────────────┘ └─────────────┘ └──────────────┘  │
  │   ┌─────────────┐ ┌─────────────┐                   │
  │   │   Style     │ │   Secret    │                   │
  │   │  Analyzer   │ │  Scanner    │                   │
  │   └─────────────┘ └─────────────┘                   │
  │         |               |              |             │
  │         v               v              v             │
  │  [Finding Aggregator]                                │
  │   - Merge findings from all passes                   │
  │   - Deduplicate overlapping findings                 │
  │   - Confidence scoring (0.0 - 1.0)                   │
  │   - Filter below org sensitivity threshold           │
  │   - Prioritize: critical > warning > suggestion      │
  │                                                      │
  │  [Comment Generator]                                 │
  │   - Format findings as PR review comments            │
  │   - Generate code suggestion blocks                  │
  │   - Group related findings per file                  │
  │   - Cap total comments (max 25 per review)           │
  │                                                      │
  └──────────────────────────────────────────────────────┘
         |
         v
[PR API Client]
  - Post review comments via GitHub/GitLab API
  - Set review status (approve / request changes / comment)
  - Respect API rate limits (5,000 req/hour GitHub)
         |
         v
[Feedback Collector] (async)
  - Track: comment resolved, suggestion accepted, comment hidden
  - Store in PostgreSQL for learning pipeline
         |
         v
[Learning Pipeline] (offline, daily batch)
  - Analyze acceptance/dismissal patterns
  - Update confidence calibration model
  - Identify high false-positive rule categories
  - Generate org-specific tuning recommendations
```

---

## 4. Component Deep Dives

### 4.1 Diff Parsing and File-Level Context

```
PR diff structure (GitHub unified diff format):

  diff --git a/src/UserService.java b/src/UserService.java
  index abc1234..def5678 100644
  --- a/src/UserService.java
  +++ b/src/UserService.java
  @@ -45,6 +45,12 @@ public class UserService {
       public User findUser(String email) {
  -        return userRepo.findByEmail(email);
  +        String query = "SELECT * FROM users WHERE email = '" + email + "'";
  +        return jdbcTemplate.queryForObject(query, new UserRowMapper());
       }

Diff parsing extracts:
  {
    file: "src/UserService.java",
    language: "java",
    hunks: [
      {
        old_start: 45, old_count: 6,
        new_start: 45, new_count: 12,
        added_lines: [47, 48],       // line numbers in new file
        removed_lines: [46],          // line numbers in old file
        context_lines: [45, 49],      // unchanged surrounding lines
        content: "..."                // raw hunk text
      }
    ]
  }

Context loading priority (token budget: 12,000 tokens per analysis pass):

  Priority 1 — Changed file full content (up to 3,000 tokens per file):
    The complete file, not just the diff hunk
    Critical for understanding: class structure, field declarations, method signatures
    If file > 3,000 tokens: include changed methods fully + class skeleton

  Priority 2 — Direct imports (up to 2,000 tokens):
    Parse import statements from changed files
    Fetch signatures and type definitions of imported classes
    Example: if diff uses UserRepository, fetch UserRepository interface definition

  Priority 3 — Type definitions (up to 1,500 tokens):
    If diff references a type (User, OrderDTO), fetch its definition
    Include field names, types, validation annotations
    Critical for detecting: null safety issues, missing field mappings

  Priority 4 — Related test files (up to 1,500 tokens):
    If src/UserService.java changed, look for test/UserServiceTest.java
    Helps detect: untested code paths, test coverage gaps

  Priority 5 — Configuration files (up to 500 tokens):
    pom.xml / build.gradle (dependency versions)
    application.yml (database config, feature flags)
    .codereview.yml (review rules)

  Remaining budget: reserved for system prompt and output

Context assembly time: < 2 seconds
  File fetch: parallel API calls (batch file content endpoint)
  Import parsing: regex + Tree-sitter AST (language-specific)
  Token counting: tiktoken (cl100k_base) for budget enforcement
```

#### DiffContextBuilder — real implementation

```python
from dataclasses import dataclass
from pathlib import Path
import re, tiktoken
_ENC = tiktoken.get_encoding("cl100k_base")
_HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@", re.MULTILINE)
@dataclass
class Hunk:
    file: str; line_start: int; line_end: int  # 1-based, new file
    added_lines: list[int]; removed_lines: list[int]
@dataclass
class ReviewContext:
    hunks: list[Hunk]; file_contents: dict[str, str]   # file -> ±10-line context
    symbol_definitions: dict[str, str]; test_coverage: dict[str, str]; total_tokens: int
class DiffContextBuilder:
    # Token budget (default 4,000): hunks 40% | context 30% | LSP symbols 20% | tests 10%.
    SPLIT = {"hunks": 0.40, "ctx": 0.30, "sym": 0.20, "test": 0.10}
    def build(self, diff_text: str, repo_path: Path, budget_tokens: int = 4000) -> ReviewContext:
        b = {k: int(budget_tokens * v) for k, v in self.SPLIT.items()}
        hunks = self._parse_hunks(diff_text)
        contents = {h.file: self._ctx_snippet(repo_path, h, b["ctx"]) for h in hunks}
        syms, tests = self._symbols(repo_path, hunks, b["sym"]), self._tests(repo_path, hunks, b["test"])
        total = len(_ENC.encode(diff_text + "".join((*contents.values(), *syms.values(), *tests.values()))))
        return ReviewContext(hunks, contents, syms, tests, total)
    def _parse_hunks(self, diff_text: str) -> list[Hunk]:
        ...  # "+++ b/" -> file; _HUNK_RE groups(1,2) -> Hunk(new_start, new_start+count-1)
    def _ctx_snippet(self, repo_path: Path, h: Hunk, budget: int) -> str:
        """Read ±10 lines from disk around the hunk; hard-trim to token budget."""
        if not (p := repo_path / h.file).exists(): return ""
        lines = p.read_text(errors="replace").splitlines()
        chunk = "\n".join(lines[max(0, h.line_start - 11):min(len(lines), h.line_end + 10)])
        ids = _ENC.encode(chunk)
        return _ENC.decode(ids[:budget]) if len(ids) > budget else chunk
    def _symbols(self, r: Path, h: list[Hunk], b: int) -> dict[str, str]:
        ...  # LSP textDocument/definition; fallback: import-line extraction
    def _tests(self, r: Path, h: list[Hunk], b: int) -> dict[str, str]:
        ...  # rglob(f"*{stem}*test*"); first 400 chars per matched test file
```

### 4.2 Security Detection

```
Security analysis uses a focused system prompt + structured output:

System prompt (security pass):
  You are a security-focused code reviewer specializing in {language}.
  Analyze the following code changes for security vulnerabilities.
  Focus on OWASP Top 10 categories.
  For each finding, provide:
    - vulnerability_type (e.g., "SQL_INJECTION", "XSS", "HARDCODED_SECRET")
    - severity: "critical" | "high" | "medium" | "low"
    - file and line number
    - explanation (2-3 sentences, why this is dangerous)
    - suggested_fix (corrected code)
    - confidence (0.0 - 1.0)
  Return findings as JSON array.

Detection categories:

  1. SQL Injection (critical):
     Pattern: string concatenation in SQL queries
     Example caught:
       String query = "SELECT * FROM users WHERE email = '" + email + "'";
     Fix suggested:
       PreparedStatement ps = conn.prepareStatement(
           "SELECT * FROM users WHERE email = ?");
       ps.setString(1, email);
     Confidence: 0.95 (string concat + SQL keywords = high signal)

  2. XSS — Cross-Site Scripting (high):
     Pattern: user input rendered without escaping
     Example: response.getWriter().write("<div>" + userInput + "</div>");
     Fix: use OWASP encoder or template engine auto-escaping

  3. Hardcoded Secrets (critical):
     Pre-LLM pass: regex scan for known patterns
       AWS_ACCESS_KEY: AKIA[0-9A-Z]{16}
       Private keys: -----BEGIN (RSA|EC|OPENSSH) PRIVATE KEY-----
       JWT secrets: jwt[_-]?secret\s*[:=]\s*["'][^"']+["']
       API keys: api[_-]?key\s*[:=]\s*["'][A-Za-z0-9]{20,}["']
       Database URLs: (mysql|postgres|mongodb)://[^:]+:[^@]+@
     Regex catches 80% of secrets with near-zero false positives
     LLM catches remaining 20%: variable names like "password = 'admin123'"

  4. Insecure Deserialization (high):
     Pattern: ObjectInputStream.readObject() on untrusted input
     Pattern: JSON.parse without schema validation on external input
     Pattern: yaml.load() instead of yaml.safe_load() in Python

  5. Path Traversal (high):
     Pattern: user input in file paths without sanitization
     Example: new File("/uploads/" + request.getParameter("filename"))
     Fix: validate filename, use Path.normalize(), reject ".."

  6. Missing Authentication/Authorization (medium):
     Pattern: new endpoint without @PreAuthorize / auth middleware
     Requires context: compare with existing endpoints in same controller

  7. Insecure Cryptography (medium):
     Pattern: MD5/SHA1 for password hashing, DES/3DES, ECB mode
     Fix: BCrypt (cost 12), AES-GCM, Argon2id

  8. SSRF — Server-Side Request Forgery (high):
     Pattern: URL from user input passed to HTTP client
     Fix: allowlist of permitted hosts, block internal IP ranges

Hybrid approach (regex + LLM):
  Step 1: Regex scanner (< 50ms) catches known patterns with high precision
  Step 2: LLM analysis catches subtle/novel issues regex misses
  Step 3: Merge results, deduplicate, pick highest confidence per location
  Combined precision: ~85% (regex: 95% on patterns it knows; LLM: 75% general)
```

### 4.3 Performance Anti-Pattern Detection

```
Performance pass system prompt focuses on runtime behavior:

Detection categories:

  1. N+1 Query Detection (high):
     Pattern: loop that triggers individual database queries
       for (Order order : orders) {
           Customer c = customerRepo.findById(order.getCustomerId());
       }
     Context needed: check if the repository method triggers a query
     Fix: use JOIN fetch, @EntityGraph, or batch query
       List<Customer> customers = customerRepo.findByIdIn(customerIds);

  2. Unbounded Queries (high):
     Pattern: SELECT without LIMIT / pagination
       @Query("SELECT u FROM User u WHERE u.active = true")
       List<User> findAllActive();
     Risk: returns 10M rows into memory, OOM in production
     Fix: add Pageable parameter, enforce max page size of 100

  3. Missing Database Indexes (medium):
     Pattern: WHERE clause on columns likely missing indexes
     Context needed: check if the column appears in entity annotations
       @Column(name = "email")  // no @Index annotation
       private String email;
     With: findByEmail() in repository
     Fix: @Table(indexes = @Index(columnList = "email"))

  4. Unnecessary Object Allocation in Hot Paths (medium):
     Pattern: object creation inside tight loops
       for (int i = 0; i < 1_000_000; i++) {
           String key = new StringBuilder().append("key_").append(i).toString();
       }
     Fix: reuse StringBuilder, use String.format outside loop

  5. Blocking Calls in Async/Reactive Code (critical):
     Pattern: Thread.sleep(), synchronous JDBC, blocking I/O in Mono/Flux chain
       Mono.fromCallable(() -> {
           return jdbcTemplate.query(sql);  // blocks event loop thread
       })
     Fix: use R2DBC, subscribeOn(Schedulers.boundedElastic())
     Context needed: detect if code is within reactive pipeline

  6. Missing Connection Pool Configuration (medium):
     Pattern: creating new connections per request
     Pattern: DataSource without pool (DriverManagerDataSource)
     Fix: use HikariCP (default pool size 10, max 20-50)

  7. Excessive Logging in Hot Path (low):
     Pattern: DEBUG/TRACE logging with string concatenation in tight loops
       logger.debug("Processing item: " + item.toString());
     Fix: use parameterized logging: logger.debug("Processing item: {}", item);

  8. Missing Caching for Repeated Expensive Operations (medium):
     Pattern: identical expensive computation called multiple times
     Context needed: method call frequency analysis from caller graph
     Fix: @Cacheable or local cache with TTL

Performance pass output format:
  {
    "findings": [
      {
        "type": "N_PLUS_1_QUERY",
        "severity": "high",
        "file": "src/OrderService.java",
        "line": 67,
        "explanation": "Loop at line 67 calls customerRepo.findById() for each order.
          With 1,000 orders this generates 1,001 queries (1 for orders + 1000 for customers).
          Measured impact: 50ms per query x 1000 = 50 seconds response time.",
        "suggestion": "Batch load: customerRepo.findByIdIn(order.stream()
          .map(Order::getCustomerId).collect(toSet()))",
        "confidence": 0.92
      }
    ]
  }
```

### 4.4 Style Enforcement

```
Style analysis adapts to each repository's existing conventions.

Convention detection (runs once per repo, cached):
  On first review for a repo:
    1. Sample 20 files from the codebase
    2. Detect patterns:
       - Naming: camelCase vs snake_case vs PascalCase
       - Indentation: tabs vs spaces, 2 vs 4
       - Brace style: K&R vs Allman vs GNU
       - Import ordering: stdlib first? grouped by package?
       - Max line length: 80 / 100 / 120
       - Comment style: Javadoc? inline? docstring format?
       - Error handling: exceptions vs error codes vs Result types
    3. Store detected conventions in repo config cache
    4. TTL: 7 days (re-detect on cache expiry)

  Convention file (.codereview.yml) overrides detection:
    style:
      naming: camelCase
      indent: spaces-4
      max_line_length: 120
      import_order: [java, javax, org, com, project]
      require_javadoc: public_methods
      banned_patterns:
        - "System.out.println"    # use logger
        - "e.printStackTrace()"   # use logger.error
        - "Thread.sleep"          # use ScheduledExecutor
      ignore_files:
        - "**/*Generated*"
        - "**/test/**"

Style analysis is cheaper than security/performance:
  Uses smaller model (GPT-4o-mini or Claude Haiku): $0.25/M input tokens
  Lower token budget: 8,000 tokens (conventions + diff only)
  Latency: 5-8 seconds

Style findings severity:
  Always "suggestion" or "nitpick" (never blocks CI)
  Capped: max 5 style comments per review (avoid noise)
  Grouped: "3 instances of inconsistent naming" instead of 3 separate comments

Example style findings:
  - "Method getUserData uses snake_case but codebase convention is camelCase"
  - "Missing Javadoc on public method processOrder(). All public methods
     in this package have Javadoc."
  - "Line 142 is 156 characters. Project convention is max 120."
```

### 4.5 Review Comment Generation

```
Comment generation transforms raw findings into developer-friendly PR comments.

Comment anatomy:
  1. Severity badge: [Critical] / [Warning] / [Suggestion] / [Nitpick]
  2. Category tag: Security | Performance | Bug | Style
  3. One-line summary (what is wrong)
  4. Explanation (2-4 sentences: why this matters, what could happen)
  5. Code suggestion block (GitHub-compatible suggestion syntax)

Example generated comment:

  **[Critical] Security: SQL Injection vulnerability**

  This query concatenates user input directly into the SQL string.
  An attacker can inject arbitrary SQL via the `email` parameter,
  potentially dumping the entire users table or escalating privileges.
  This pattern appears in OWASP Top 10 #A03:2021 (Injection).

  ```suggestion
  public User findUser(String email) {
      return jdbcTemplate.queryForObject(
          "SELECT * FROM users WHERE email = ?",
          new Object[]{email},
          new UserRowMapper());
  }
  ```

  Confidence: 95%

GitHub suggestion block format:
  The ```suggestion block allows one-click "Apply suggestion" in GitHub UI
  Must contain the exact replacement code for the specified line range
  Multi-line suggestions: specify line range in the review comment API

Comment posting strategy:
  Post as a single review (not individual comments):
    - GitHub API: POST /repos/{owner}/{repo}/pulls/{pr}/reviews
    - Body: { "event": "COMMENT", "comments": [...] }
    - All comments appear atomically in one review
    - Shows "AI Code Review" as the reviewer name (via GitHub App identity)

  Comment ordering in review:
    1. Critical findings first (security vulnerabilities, data loss risks)
    2. High-severity warnings (performance, potential bugs)
    3. Medium suggestions (improvements, better patterns)
    4. Low/nitpick (style, naming, documentation)

  Comment cap: max 25 comments per review
    More than 25 overwhelms the developer
    If > 25 findings: show top 25 by severity x confidence
    Add summary comment: "25 of 42 findings shown. Run /review-all for complete list."

  Review verdict:
    If critical findings with confidence > 0.9: "REQUEST_CHANGES"
    If only warnings/suggestions: "COMMENT"
    Configurable per org: some orgs want "COMMENT" only (never block)
```

### 4.6 Confidence Scoring and False Positive Management

```
Every finding gets a confidence score (0.0 - 1.0):

Score components:
  Base confidence (from LLM output): 0.0 - 1.0
  Pattern match bonus: +0.15 if regex also flagged same location
  Context penalty: -0.20 if surrounding code suggests intentional pattern
  Historical penalty: -0.30 if similar finding was dismissed 3+ times in this repo

Calibration (Platt scaling on historical data):
  Raw LLM confidence is poorly calibrated (overconfident)
  Train logistic regression: raw_score -> P(accepted)
  Training data: 100K historical findings with accept/dismiss labels
  After calibration: confidence 0.8 means 80% chance developer accepts

Thresholds (configurable per org):
  High sensitivity (default for security-critical repos):
    Show findings with confidence >= 0.5
    Expected false positive rate: ~30%

  Medium sensitivity (default):
    Show findings with confidence >= 0.65
    Expected false positive rate: ~20%

  Low sensitivity (for noisy repos):
    Show findings with confidence >= 0.8
    Expected false positive rate: ~10%

  Per-category overrides:
    security_threshold: 0.5    (show more, miss fewer real issues)
    style_threshold: 0.85      (show fewer, reduce noise)
    performance_threshold: 0.7

False positive suppression mechanisms:

  1. Inline suppression:
     Developer adds comment: // codereview-ignore: N_PLUS_1
     System skips that pattern for that line permanently

  2. Repository-level ignore:
     .codereview.yml:
       ignore_rules:
         - SQL_INJECTION     # "we use a custom SQL sanitizer"
       ignore_files:
         - "**/generated/**"
         - "**/migrations/**"

  3. Feedback-driven suppression:
     If same finding type dismissed > 5 times in same repo:
       Auto-reduce confidence by 0.2 for future occurrences
       Notify org admin: "Consider adding to ignore_rules"

  4. Duplicate suppression:
     Same finding on same line in consecutive PR updates:
       Don't re-post (comment already exists)
       Update existing comment if severity changed
```

### 4.7 CI/CD Integration

```
GitHub Action integration:

  # .github/workflows/ai-review.yml
  name: AI Code Review
  on:
    pull_request:
      types: [opened, synchronize, reopened]

  jobs:
    review:
      runs-on: ubuntu-latest
      steps:
        - uses: ai-code-review/action@v2
          with:
            api_key: ${{ secrets.CODE_REVIEW_API_KEY }}
            sensitivity: medium
            block_on_critical: true
            max_comments: 25
            ignore_paths: |
              **/generated/**
              **/vendor/**

  Action behavior:
    1. Action receives PR event context (owner, repo, PR number, head SHA)
    2. Calls AI Code Review API: POST /api/v1/reviews
    3. Polls for completion: GET /api/v1/reviews/{id}/status
    4. Posts comments via GitHub API (using the GitHub App token)
    5. Sets commit status: success (no critical) / failure (critical found)

  Blocking gate:
    If block_on_critical: true AND critical findings exist:
      GitHub commit status = "failure"
      PR cannot be merged (branch protection rule)
      Developer must resolve or dismiss findings

    If block_on_critical: false:
      Always sets commit status = "success"
      Comments are informational only

GitLab CI integration:
  Similar pattern: .gitlab-ci.yml job triggered on merge_request events
  Posts comments via GitLab Merge Request Notes API
  Sets pipeline status for merge blocking

Webhook-based integration (provider-agnostic):
  For Bitbucket, Azure DevOps, or self-hosted Git:
    Register webhook URL: POST https://api.codereview.ai/webhooks/git
    Webhook payload includes: repo URL, PR ID, diff URL, auth token
    System fetches diff, runs review, posts comments via provider API

Rate limiting against Git provider APIs:
  GitHub: 5,000 requests/hour per installation
  Budget per review: ~10 API calls (fetch diff, fetch files, post review)
  Max concurrent reviews per GitHub App installation: 500/hour
  Backpressure: if approaching rate limit, queue reviews with delay
```

#### Webhook-to-review pipeline

BROKEN: synchronous review in the webhook handler causes GitHub's 10-second timeout → 3 retries
→ 3 duplicate review comment sets on every PR.

```python
# BROKEN — blocks 15-45s; GitHub retries 3x after 10s
async def broken(req): await run_full_review(await req.json()); return {"status": "done"}
# FIX: HMAC-SHA256 verify, enqueue to Redis, return job_id in <2s
import hashlib, hmac, json, uuid
from dataclasses import dataclass
import httpx, redis.asyncio as aioredis
_redis = aioredis.from_url("redis://localhost:6379")
GITHUB_API = "https://api.github.com"
@dataclass
class ReviewJob:
    job_id: str; owner: str; repo: str; pr_number: int; head_sha: str; token: str
async def handle_pr_event(payload: dict) -> str:
    """Return job_id in <2s. ReviewWorker.process() runs the 15-45s review out-of-band."""
    if payload.get("action") not in {"opened", "synchronize", "reopened"}: return "ignored"
    pr = payload["pull_request"]
    job = ReviewJob(str(uuid.uuid4()), payload["repository"]["owner"]["login"],
                    payload["repository"]["name"], pr["number"],
                    pr["head"]["sha"], payload.get("installation_token", ""))
    if not await _redis.set(f"review:q:{job.owner}:{job.repo}:{job.head_sha}",
                            job.job_id, nx=True, ex=300):
        return job.job_id  # dedup: same head SHA already queued
    await _redis.rpush("review:jobs", json.dumps(job.__dict__))
    return job.job_id
async def post_review_comment(job: ReviewJob, findings: list[dict]) -> None:
    """POST findings as a single GitHub PR review; X-Idempotency-Key prevents duplicates."""
    idem = hashlib.sha256(f"{job.pr_number}:{job.head_sha}".encode()).hexdigest()[:16]
    comments = [{"path": f["file"], "line": f["line"], "side": "RIGHT",
                 "body": f"**[{f['severity'].upper()}] {f['type']}**\n\n{f['explanation']}"}
                for f in findings]
    async with httpx.AsyncClient() as c:
        await c.post(f"{GITHUB_API}/repos/{job.owner}/{job.repo}/pulls/{job.pr_number}/reviews",
                     headers={"Authorization": f"Bearer {job.token}", "X-Idempotency-Key": idem},
                     json={"event": "COMMENT", "comments": comments}, timeout=15)
```

Webhook responds in <2s; full review takes 15-45s; Redis bridges the gap.
`X-Idempotency-Key = sha256(pr_number+head_sha)[:16]` prevents duplicates on worker retry.

### 4.8 Learning from Feedback

```
Feedback signals collected:

  1. Suggestion accepted (strongest positive signal):
     Developer clicked "Apply suggestion" in GitHub UI
     Tracked via: webhook on PR comment thread resolution + code match

  2. Comment resolved (positive signal):
     Developer resolved the comment thread
     Ambiguous: could mean "fixed" or "acknowledged and ignored"
     Cross-reference with actual code change to disambiguate

  3. Comment hidden/minimized (negative signal):
     Developer explicitly hid the comment as "off-topic" or "outdated"
     Strong signal: this was a false positive or unhelpful

  4. Thumbs down reaction (negative signal):
     Developer reacted with thumbs-down emoji
     Direct negative feedback

  5. No action (weak negative signal):
     Comment posted, PR merged, comment never addressed
     Weakest signal: developer may have missed it

Feedback data schema (PostgreSQL):
  reviews:
    id, org_id, repo_id, pr_number, head_sha, created_at, model_version

  findings:
    id, review_id, category, finding_type, file, line, confidence,
    severity, accepted, dismissed, suggestion_applied, created_at

  org_patterns:
    org_id, finding_type, total_shown, total_accepted, acceptance_rate,
    last_updated

Learning pipeline (daily batch job):

  1. Confidence recalibration:
     Input: all findings from last 30 days with known outcomes
     Output: updated Platt scaling parameters per finding_type
     Effect: if SQL_INJECTION findings have 90% acceptance, boost confidence
             if MISSING_JAVADOC findings have 20% acceptance, reduce confidence

  2. Org-specific pattern learning:
     Aggregate acceptance rates per (org, finding_type)
     If org consistently dismisses a finding type:
       Auto-adjust that org's threshold upward
       Suggest adding to ignore_rules in weekly digest email

  3. Global model improvement:
     Collect (finding, outcome) pairs across all orgs (anonymized)
     Use as evaluation set for new model versions
     A/B test: 10% of reviews use candidate model, compare acceptance rates

  4. False positive pattern mining:
     Cluster dismissed findings by code pattern
     Identify systematic false positive categories
     Add to regex allowlist or adjust system prompt

Metrics tracked:
  Overall acceptance rate: target > 50%
  Acceptance rate by category:
    Security: target > 70% (high-value findings)
    Performance: target > 55%
    Style: target > 40% (inherently more subjective)
  False positive rate: target < 30%
  Time to resolve: median < 4 hours after review posted
```

---

## 5. Context Window Management for Large PRs

```
Challenge: PRs can range from 1 line to 10,000+ lines.
  Small PR (< 100 lines): fits in single LLM call with full context
  Medium PR (100-500 lines): needs context prioritization
  Large PR (500-2,000 lines): needs chunking into segments
  Mega PR (2,000+ lines): needs aggressive filtering and summarization

Strategy by PR size:

  Small (< 100 changed lines, < 3 files):
    Single LLM call per analysis pass
    Include: full changed files + all imports + type definitions
    Token budget: 12,000 tokens (plenty of room)
    Latency: 8-12 seconds total

  Medium (100-500 lines, 3-10 files):
    Single LLM call per pass, but prioritize context
    Include: changed hunks with 50 lines surrounding context
    Trim: only include referenced imports, not all
    Token budget: 15,000 tokens
    Latency: 15-25 seconds total

  Large (500-2,000 lines, 10-30 files):
    Chunk by file groups (related files together)
    Chunk strategy:
      Group 1: src/auth/*.java (auth-related changes)
      Group 2: src/api/*.java (API layer changes)
      Group 3: src/db/*.java (database layer changes)
    Each chunk: independent analysis with its own context
    Merge findings across chunks, deduplicate
    Token budget per chunk: 12,000 tokens
    Latency: 30-60 seconds (chunks run in parallel within each pass)

  Mega (2,000+ lines):
    Warning comment: "This PR has 3,500 changed lines. AI review
      covers the most critical files. Consider splitting into smaller PRs."
    Filter: only review files with highest risk score
    Risk score = f(file_type, change_magnitude, security_sensitivity)
      Security-sensitive files (auth, crypto, input handling): risk 1.0
      Database migrations: risk 0.9
      API endpoints: risk 0.8
      Business logic: risk 0.7
      Tests: risk 0.3
      Config/docs: risk 0.1
    Review top files until token budget exhausted
    Latency: 60-120 seconds

File-level context window allocation:
  Total budget per analysis pass: 12,000 tokens
  System prompt: 800 tokens (fixed)
  Output reserved: 2,000 tokens
  Available for code: 9,200 tokens

  Per-file allocation:
    changed_file_tokens = min(file_size, 3000)
    import_tokens = 500 per file (signatures only)
    If total > 9,200: proportionally shrink each file's allocation
    Always preserve: the diff hunks themselves (never truncate the actual changes)
```

---

## 6. Secret Scanning Deep Dive

```
Secret scanning runs as a pre-LLM pass (regex-based, deterministic):

Why separate from LLM:
  Speed: regex scan completes in < 50ms for any PR size
  Determinism: no false negatives on known patterns (regex never misses a match)
  Cost: zero LLM tokens consumed
  Confidence: pattern-matched secrets get confidence 0.99

Pattern library (200+ patterns):

  Cloud provider keys:
    AWS Access Key: AKIA[0-9A-Z]{16}
    AWS Secret Key: [A-Za-z0-9/+=]{40} (context: near AWS_SECRET)
    GCP Service Account: "type": "service_account"
    Azure Storage Key: [A-Za-z0-9+/]{86}==

  API keys:
    Stripe: sk_live_[A-Za-z0-9]{24,}
    Twilio: SK[0-9a-f]{32}
    SendGrid: SG\.[A-Za-z0-9_-]{22}\.[A-Za-z0-9_-]{43}
    Slack: xoxb-[0-9]{11}-[0-9]{11}-[A-Za-z0-9]{24}

  Certificates and keys:
    RSA private key: -----BEGIN RSA PRIVATE KEY-----
    Generic private key: -----BEGIN PRIVATE KEY-----
    PGP private key: -----BEGIN PGP PRIVATE KEY BLOCK-----

  Database URLs (with embedded passwords):
    postgres://user:password@host:5432/db
    mysql://user:password@host:3306/db
    mongodb://user:password@host:27017/db
    redis://:password@host:6379

  JWT and session secrets:
    Variable patterns: (jwt|session|auth)[_-]?(secret|key|token)\s*[:=]
    Hardcoded long strings: base64 strings > 30 chars assigned to secret-like vars

Entropy-based detection (catches novel secrets):
  Calculate Shannon entropy of string constants
  High entropy (> 4.5 bits/char) + length > 20 + in assignment context
  = likely a secret or API key
  False positive rate: ~15% (some legitimate base64 content)
  Combined with variable name analysis to reduce false positives

False positive reduction for secrets:
  Ignore: test files (test/, __tests__/, *_test.go)
  Ignore: example/placeholder values (EXAMPLE_KEY, YOUR_API_KEY_HERE, xxxx)
  Ignore: hash constants (known checksums, git SHAs)
  Ignore: values from environment variables (os.getenv, System.getenv)

Action on secret detection:
  Severity: always "critical"
  Comment: includes exact line, pattern matched, recommended fix
  Recommended fix: move to environment variable or secret manager (Vault, AWS Secrets Manager)
  If block_on_critical enabled: PR blocked until secret removed
```

---

## 7. Trade-offs and Design Decisions

| Decision | Chosen | Alternative | Reason |
|----------|--------|-------------|--------|
| Analysis approach | Multi-pass parallel | Single monolithic LLM call | 3-5x higher precision; each pass gets focused system prompt |
| Secret scanning | Regex pre-pass + LLM | LLM only | Regex: deterministic, zero cost, < 50ms; LLM catches novel patterns |
| Context loading | Git provider API | Clone full repo | API calls: 2s; full clone: 30-60s for large repos |
| Large PR handling | Chunk by file groups | Reject large PRs | Partial review better than no review; risk-prioritized file selection |
| Comment cap | 25 per review | Unlimited | Developer research shows > 25 comments ignored; prioritize high-value |
| Style detection | Detect conventions from codebase | Require explicit config | Lower friction for onboarding; config file overrides detection |
| Review verdict | Configurable blocking | Always block on critical | Enterprise teams have different risk tolerance; default: comment only |
| Model selection | GPT-4o for security/logic, mini for style | Single model for all | Cost optimization: style pass is 10x cheaper with smaller model |
| Feedback learning | Daily batch recalibration | Real-time update | Batch is simpler, debuggable; real-time risks feedback loops |
| Confidence scoring | Platt-scaled calibration | Raw LLM confidence | Raw LLM scores are poorly calibrated; Platt scaling maps to true probability |

---

## 8. Cost Analysis

```
Cost per review breakdown (average PR: 300 lines, 5 files):

  LLM costs (3 analysis passes):
    Security pass: 12,000 input + 1,500 output tokens
      GPT-4o: 12K x $2.50/M + 1.5K x $10/M = $0.030 + $0.015 = $0.045
    Performance pass: 12,000 input + 1,500 output tokens
      GPT-4o: same = $0.045
    Logic/bug pass: 12,000 input + 1,500 output tokens
      GPT-4o: same = $0.045
    Style pass: 8,000 input + 800 output tokens
      GPT-4o-mini: 8K x $0.15/M + 0.8K x $0.60/M = $0.0012 + $0.0005 = $0.002
    Comment generation: 3,000 input + 1,000 output tokens
      GPT-4o-mini: $0.0005 + $0.0006 = $0.001

  Total LLM cost per review: $0.138

  Infrastructure costs per review:
    Compute (worker time): $0.005
    Git API calls: $0.000 (free within rate limits)
    Storage (review results): $0.0001
    Queue/orchestration: $0.001

  Total cost per review: ~$0.145
  With overhead (20%): ~$0.175 per review

Daily cost at 50,000 PRs:
  LLM: 50,000 x $0.138 = $6,900/day
  Infrastructure: 50,000 x $0.037 = $1,850/day
  Total: $8,750/day = ~$262,500/month

Cost optimization levers:
  1. Skip analysis passes based on file type:
     If no database code: skip performance pass → saves 33% LLM cost
     If no user-facing code: skip XSS checks
  2. Cache common patterns:
     Identical diff hunks across repos (boilerplate): cache findings
     Cache hit rate: ~5% (low, but free savings)
  3. Use smaller models where possible:
     Style + comment generation on GPT-4o-mini: 10x cheaper
     Security must stay on capable model (cost of missing a vuln >> LLM cost)
  4. Prompt caching (Anthropic/OpenAI):
     System prompts are identical across reviews
     Cached prompt tokens: 50% discount
     Saves ~15% on input token costs
```

---

## 9. Observability and Metrics

```
Key operational metrics:

  Review pipeline:
    review_latency_seconds (P50, P95, P99): target P95 < 60s
    review_queue_depth: target < 100 (backlog indicator)
    review_success_rate: target > 99% (failed reviews / total)
    review_timeout_rate: target < 2%

  Quality metrics:
    finding_acceptance_rate: target > 50% overall
    finding_acceptance_by_category: security > 70%, perf > 55%, style > 40%
    false_positive_rate: target < 30%
    suggestion_apply_rate: target > 25% (one-click apply in GitHub)
    comments_per_review_avg: target 3-8 (< 3 = too quiet; > 15 = too noisy)

  Cost metrics:
    cost_per_review_usd: target < $0.50
    tokens_per_review: track for budget forecasting
    llm_cost_daily: absolute spend tracking
    cost_by_model: identify optimization opportunities

  Developer experience:
    time_to_first_comment: how fast review appears on PR
    developer_dismiss_rate: by org, by category
    repeat_finding_rate: same issue flagged on subsequent PRs (learning signal)

Alerting rules:
  review_latency P95 > 120s: page on-call (pipeline degradation)
  review_success_rate < 95%: page on-call (system failure)
  finding_acceptance_rate drops > 10% week-over-week: investigate model regression
  cost_per_review > $1.00: investigate (context explosion or model misconfiguration)
  queue_depth > 500 for > 10 minutes: scale up workers

Dashboard layout:
  Row 1: review volume (PRs/hour), queue depth, worker utilization
  Row 2: latency percentiles, success/failure rates
  Row 3: acceptance rates by category, false positive trend
  Row 4: cost per review, daily spend, model distribution
```

---

## Operational Playbook

**Eval Pipeline.** Weekly FP check against golden PRs (known-good PRs that should produce zero
comments in specific categories). Target FP rate <10%; freeze prompt version if FP rate exceeds
15% on two consecutive runs. Metrics: `eval.fp_rate` (by category), `eval.total_comments_on_golden`.
See [LLM Eval Harness in Production](./cross_cutting/llm_eval_harness_in_production.md).
**Observability.** OTel trace at `pr_review` (attrs: pr_id, repo, num_files_changed, org_tier)
with spans: `diff_parse` [200-400ms], `context_build` [800-1800ms, budget_tokens/symbols_resolved],
`llm_review` [parallel: security_pass, performance_pass, logic_pass — each carries model,
input_tokens, output_tokens, findings_count], `comment_post` [500-1500ms, idempotency_key]. P95
SLO 60,000ms; `llm_review` spans aggregate `cost_usd` per review for billing. See
[OpenTelemetry for LLM Apps](./cross_cutting/opentelemetry_for_llm_apps.md).
**Incident Runbooks.** Runbook 1 — Duplicate comments storm. Symptom: 3 identical comments per PR
line. Root cause: webhook retry (handler >10s) + missing idempotency key. Fix: confirm
`handle_pr_event` returns in <2s; set `X-Idempotency-Key: sha256(pr_number+head_sha)[:16]`.
Runbook 2 — False positive spike. Symptom: FP rate >15% on golden set. Root cause: prompt
regression or silent model upgrade (check `x-model-version` header). Fix: roll back prompt alias;
pin model (`model="gpt-4o-2024-05-13"`); re-run golden eval; fire `alert.fp_rate_spike`.
Runbook 3 — Latency SLA breach. Symptom: P95 `pr_review` >90,000ms for >5 minutes. Root cause:
LLM rate limit (429) or insufficient workers (>2,000 PRs/hour needs >40). Fix: token-bucket queue
per org tier, failover to Claude Sonnet; scale worker replicas 20→60; target queue depth <50.

---

## 10. Interview Discussion Points

**Why multi-pass beats single-pass for code review.** A single LLM call with "review this code for all issues" produces shallow, generic comments. Each analysis domain (security, performance, style) has different detection patterns, different context needs, and different severity calibration. A focused security prompt with OWASP examples in the system message catches 2-3x more real vulnerabilities than a generic review prompt. The trade-off is 3x more LLM calls and cost, but at $0.14 per review the absolute cost is trivial compared to the value of catching a SQL injection.

**The context loading problem is the hardest engineering challenge.** Reviewing a diff without surrounding context is like reading one paragraph of a novel -- you miss the plot. But loading the entire repository (500K+ tokens) is impractical and expensive. The solution is a priority-based context loading strategy: changed file full content first, then direct imports, then type definitions, then tests. This mirrors how a human reviewer reads a PR: look at the diff, then check what the referenced classes look like. The 2-second budget for context loading forces efficient API usage (parallel batch fetches, caching file content across reviews in the same repo).

**Confidence calibration separates useful tools from noisy ones.** Raw LLM confidence scores are notoriously poorly calibrated -- a model might say "95% confident" on 60% of its findings, even when only 70% are correct. Platt scaling (logistic regression on historical outcomes) maps raw scores to true acceptance probabilities. Without calibration, you either show too many false positives (eroding trust) or filter too aggressively (missing real issues). The feedback loop matters: every accepted/dismissed finding improves the calibration model.

**Why 25 comments maximum per review.** Developer psychology research (Microsoft, Google code review studies) shows that review effectiveness drops sharply beyond 20-30 comments. Developers experience "comment fatigue" and start dismissing findings without reading them. Showing the top 25 by severity times confidence maximizes the chance that critical findings get attention. For teams that want completeness, the "/review-all" escape hatch provides the full list on demand.

**Secret scanning must be deterministic, not probabilistic.** A leaked AWS key costs $10K-$100K+ in unauthorized compute charges. The consequence of a false negative (missed secret) is orders of magnitude worse than a false positive (flagging a non-secret). Regex-based detection with known patterns provides near-perfect recall on known secret formats. The LLM layer adds coverage for novel patterns (e.g., a variable named "db_password" assigned a literal string) that regex cannot catch. This defense-in-depth approach -- deterministic layer plus probabilistic layer -- is standard in security tooling.

**How to handle the cold start problem for new repositories.** The first review for a new repository has no historical feedback data, no convention detection cache, and no calibrated confidence model. The solution is a sensible default: use global confidence thresholds (not org-specific), run convention detection on first review (add 3 seconds), and use the global calibration model. After 50 reviews in a repository, org-specific patterns emerge and confidence improves. Explicit configuration (.codereview.yml) lets teams skip the learning period for style rules.

**The blocking gate trade-off in CI/CD.** Blocking PRs on critical findings prevents security issues from reaching production but creates friction: a false positive on a Friday afternoon blocks a hotfix deployment. The production-safe default is "comment only" (never block). Teams that enable blocking should set a high confidence threshold (0.9+) for blocking findings and maintain a fast manual override process (security team can dismiss within minutes). The escalation path matters more than the gate itself.

**Cost scales linearly with PR volume but optimization is sublinear.** At 50,000 PRs/day the LLM cost is $6,900/day. Doubling to 100,000 PRs/day doubles cost to $13,800/day -- there is no economy of scale on per-token LLM pricing. However, optimizations compound: skipping unnecessary analysis passes based on file type saves 20-30%, prompt caching saves 15%, and the style pass on a cheaper model saves 10x for that pass. The architecture decision to use different models for different passes (GPT-4o for security, GPT-4o-mini for style) is a direct reflection of this cost reality. A single-model architecture would either overpay for style analysis or underperform on security detection.
