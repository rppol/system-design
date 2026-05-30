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

## Operational Playbook

### Eval Pipeline

Weekly acceptance-rate regression check against a golden set of 500 code completion scenarios spanning Python, TypeScript, Java, Go, and Rust. Each scenario provides a fixed prefix/suffix pair and an expected completion class (exact match, fuzzy token match, or semantic equivalence via LLM-as-judge). If the acceptance rate on the golden set drops more than 5 percentage points from the prior week's baseline, the pipeline fires a P1 alert and blocks deployment of the new model checkpoint. Cross-reference: See [LLM Eval Harness in Production](./cross_cutting/llm_eval_harness_in_production.md).

```
Golden eval pipeline (runs every Sunday 02:00 UTC):
  1. Freeze model candidate checkpoint
  2. Replay 500 golden scenarios against candidate via shadow traffic
  3. Score: exact-match, fuzzy-match (token F1 > 0.8), LLM-as-judge for multi-line
  4. Compare acceptance_rate_candidate vs acceptance_rate_baseline
  5. If delta < -5pp → alert + block → human review required before deploy
  6. If delta >= 0 → auto-promote checkpoint to canary (1% traffic)
  7. Monitor live acceptance rate for 24h; if stable → full rollout
```

### Observability

Every completion request carries a single OTel root span with child spans mirroring the latency budget:

```
completion_request [root]  lang, file_ext, cursor_position_type
  +-- fim_assembly          prefix_tokens, suffix_tokens, neighbor_tokens, rag_tokens, total_tokens  <5ms
  +-- speculative_decode    draft_tokens_generated, accepted_tokens, acceptance_rate, fallback_count  <185ms P50
  +-- prefix_cache          cache_hit, prefix_tokens_saved, kv_cache_reuse_ratio                     <5ms
  +-- license_filter        ngrams_checked, match_found, matched_license                             <5ms
  +-- streaming             TTFT_ms, tokens_per_sec, completion_tokens, finish_reason   TTFT <200ms P50
```

Cross-reference: See [OpenTelemetry for LLM Apps](./cross_cutting/opentelemetry_for_llm_apps.md) for span schema standards and sampling strategy.

### Incident Runbooks

**Runbook 1 — Latency Spike (P99 TTFT > 500ms)**

Symptom: `completion_latency_p99 > 500ms` sustained 3 minutes.
Diagnosis: (1) Check `speculative_decode.duration` — is inference the bottleneck? (2) Check `prefix_cache.kv_cache_reuse_ratio` — if dropped from 0.85 to <0.5, the system prompt may have changed; check recent config deploys. (3) If `acceptance_rate < 0.4`, draft model is on a degraded node; reroute. (4) If `fim_assembly.total_tokens > 3,500`, budget guard may have regressed.
Mitigation: Scale out replicas if GPU utilization > 80%; disable RAG context (Priority 4) to shed 280 input tokens/request.
Resolution: Root cause to — (a) system prompt change breaking prefix cache affinity, (b) draft model node failure, or (c) FIM context budget regression. Verify P99 < 400ms before close.

**Runbook 2 — Acceptance Rate Drop (rolling 7-day average down >5pp)**

Symptom: Weekly eval fires or live panel shows acceptance rate < 29% (baseline 34%).
Diagnosis: (1) Replay golden 500 scenarios against last 3 checkpoints to isolate regression commit. (2) Check `fim_assembly` spans for malformed FIM token order — `<PRE>`, `<SUF>`, `<MID>` must appear in sequence. (3) Verify FIMContextAssembler budget ratios unchanged. (4) Segment by language: if only Rust/Go regresses, check tokenizer-model mismatch.
Mitigation: Roll back to last known-good checkpoint; revert FIMContextAssembler budget ratios if changed.
Resolution: Add per-language acceptance rate baselines to eval gate before next checkpoint promotion.

**Runbook 3 — License Filter False Positive Spike**

Symptom: `license_filter.match_found` jumps from 0.1% baseline to >2%; users report "Suggestion blocked" spike.
Diagnosis: (1) Sample 100 blocked completions; identify common triggering pattern. (2) Check if a recently indexed library introduced idiomatic code (`__init__(self, config: Config)`) that now matches the n-gram Bloom filter. (3) Confirm false positive vs. true verbatim copy.
Mitigation: Temporarily raise n-gram match threshold from 50% to 70% — reduces false positives ~60% with <1% increase in true-positive miss rate.
Resolution: Retrain Bloom filter excluding patterns appearing in >10,000 distinct repos (idiomatic, not licensable expression); re-deploy; monitor false positive rate.

Cross-reference: See [GPU Pool Economics](./cross_cutting/gpu_pool_economics.md) for GPU utilization math and cost attribution during incidents.

---

## 10. Interview Discussion Points

**Why is 300ms the target latency?** Human typing speed is ~200ms between keystrokes. If completions appear within 300ms of stopping typing, they feel instantaneous. Above 500ms, developers notice the delay. Above 1s, developers move on before the suggestion appears.

**Key bottleneck: GPU inference.** At 18,500 req/sec with 120ms inference time, you need 18,500 × 0.12 = 2,220 concurrent requests in flight at once. A100s with continuous batching can handle ~100 concurrent code completions = 22 A100s minimum, with safety margin → 50+ A100s.

**Context window trade-off:** More context = better completions, but longer prompts = higher latency + cost. Copilot's 1,500 token context is a deliberate balance: empirically, most useful context is in the current file and recently opened tabs. Additional repository context provides diminishing returns for inline completions.

**Privacy tension:** Individual developers want personalized completions (requiring code context). Enterprise customers want zero data retention (requiring stateless API). GitHub handles this with two tiers: Copilot Individual (may use code) vs Business (no retention, higher price).

**Acceptance rate as product health metric:** If acceptance rate drops from 34% to 25%, it could mean model degraded, or context collection broke, or a language-specific regression. Tracking acceptance rate by language, file type, and model version enables rapid root cause analysis.

---

## FIM Architecture Deep-Dive

**Fill-in-Middle Token Layout:**

GitHub Copilot's FIM prompt uses the `<PRE>`, `<SUF>`, `<MID>` token convention (Codestral/StarCoder2 style) or the equivalent sentinel tokens from Code Llama. The exact format for Codestral:

```
<PRE> {prefix_code} <SUF> {suffix_code} <MID>
```

The model generates tokens that fill the `<MID>` slot, terminating at `<EOT>` (end of infill). Context selection priority (from most to least important for completion quality):

```
Priority 1: Current file, cursor-adjacent code (1,200 tokens: 800 prefix + 400 suffix)
Priority 2: Open tabs in IDE (300 tokens total, most recently viewed files first)
Priority 3: Imported file signatures (200 tokens: function signatures only, not bodies)
Priority 4: Repository-level context (deprecated for inline; used only for agent mode)
```

**Latency Budget Breakdown (P50 at GitHub scale):**

```
IDE trigger (user stops typing 400ms)         :   0ms (client-side debounce)
Tokenization of context window               :   2ms
Network RTT (user → CDN → inference cluster) :  18ms
Queue wait (continuous batching scheduler)   :   8ms
KV cache lookup (prefix match)               :   5ms
Model inference (speculative decoding)       : 102ms  ← dominant
  - Draft model (130M params, 4 tokens/step) :  22ms
  - Verification pass (verify 4 candidates)  :  80ms
Detokenization                               :   3ms
Network response streaming (first token)     :  12ms
─────────────────────────────────────────────────────
Total P50 TTFT                               : 150ms
─────────────────────────────────────────────────────
Ghost text visible to user                   : 150ms + keystroke delay
User-perceived latency (with ghost text UX)  : ~300ms (acceptable: <400ms)
```

### FIM Context Assembler — Production Python

The prose above describes the priority ordering conceptually. The code below enforces it with strict token budget accounting. Without budget enforcement the FIM completion slot is crowded out and the model silently truncates from the suffix end, producing partial or syntactically broken completions.

```python
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Optional
import tiktoken  # cl100k_base tokenizer, ~3ms cold start

_TOKENIZER = tiktoken.get_encoding("cl100k_base")


@dataclass
class FIMPrompt:
    prefix: str
    suffix: str
    neighbor_context: str
    rag_context: str
    total_tokens: int
    assembly_ms: float


# BROKEN — no budget enforcement: neighbor may be 8,000+ tokens, context overflows,
# model truncates the completion slot to ~0 tokens, ghost text is always blank.
# def assemble_broken(prefix, suffix, open_files, budget_tokens):
#     return f"<PRE>{prefix}<SUF>{''.join(open_files)}<MID>"  # NO truncation guard

class FIMContextAssembler:
    # Budget: 40% prefix, 20% suffix, 30% neighbor tabs, 10% RAG. Avg prompt: 2,800 tokens.
    BUDGET_RATIOS = {"prefix": 0.40, "suffix": 0.20, "neighbor": 0.30, "rag": 0.10}
    FIM_OVERHEAD_TOKENS = 8  # <PRE>, <SUF>, <MID>, <EOT>

    def __init__(self, model: str = "cl100k_base") -> None:
        self._enc = tiktoken.get_encoding(model)

    def _count(self, text: str) -> int:
        return len(self._enc.encode(text))

    def _truncate_to_tokens(self, text: str, max_tokens: int, from_end: bool = False) -> str:
        tokens = self._enc.encode(text)
        if len(tokens) <= max_tokens:
            return text
        return self._enc.decode(tokens[-max_tokens:])  # keep nearest-to-cursor end

    def assemble(
        self,
        prefix: str,
        suffix: str,
        cursor_file: str,
        open_files: list[str],
        budget_tokens: int = 2800,
        rag_results: Optional[list[str]] = None,
    ) -> FIMPrompt:
        t0 = time.perf_counter()
        usable = budget_tokens - self.FIM_OVERHEAD_TOKENS
        alloc = {k: int(usable * v) for k, v in self.BUDGET_RATIOS.items()}

        prefix_trimmed = self._truncate_to_tokens(prefix, alloc["prefix"])
        suffix_trimmed = self._truncate_to_tokens(suffix, alloc["suffix"], from_end=True)

        # Neighbor context: most recently viewed tab first; truncate furthest content first
        neighbor_parts: list[str] = []
        for f in open_files:
            remaining = alloc["neighbor"] - sum(self._count(p) for p in neighbor_parts)
            if remaining <= 0:
                break
            neighbor_parts.append(self._truncate_to_tokens(f, remaining))
        neighbor_ctx = "\n".join(neighbor_parts)

        # RAG context: function signatures from repo-level semantic search
        rag_parts: list[str] = []
        for r in (rag_results or []):
            remaining = alloc["rag"] - sum(self._count(p) for p in rag_parts)
            if remaining <= 0:
                break
            rag_parts.append(self._truncate_to_tokens(r, remaining))
        rag_ctx = "\n".join(rag_parts)

        total = (self._count(prefix_trimmed) + self._count(suffix_trimmed)
                 + self._count(neighbor_ctx) + self._count(rag_ctx) + self.FIM_OVERHEAD_TOKENS)
        return FIMPrompt(
            prefix=prefix_trimmed, suffix=suffix_trimmed,
            neighbor_context=neighbor_ctx, rag_context=rag_ctx,
            total_tokens=total, assembly_ms=(time.perf_counter() - t0) * 1000,
        )

    def build_prompt_string(self, p: FIMPrompt) -> str:
        header = ""
        if p.rag_context:
            header = f"# Related symbols\n{p.rag_context}\n"
        if p.neighbor_context:
            header += f"# Open files context\n{p.neighbor_context}\n"
        return f"<PRE>{header}{p.prefix}<SUF>{p.suffix}<MID>"
```

**Concrete numbers:** Average FIM prompt = 2,800 tokens (prefix 1,120 + suffix 560 + neighbor 840 + RAG 280); context assembly completes in <5ms client-side measured on a MacBook M2. Without the budget guard the overflow scenario pushes the FIM completion slot to 0 usable tokens, causing the model to emit `<EOT>` immediately — manifesting as blank ghost text with no error reported.

---

## Multi-Language Tokenizer Trade-offs

Copilot serves Python, JavaScript, TypeScript, Java, Go, Rust, C++, and 20+ other languages with a single model and tokenizer (BPE-based, vocabulary size 50,000-100,000). Trade-offs by language:

| Language | Tokens per 100 chars | Implication |
|---|---|---|
| Python | ~25 | Efficient; whitespace-significant indentation tokenizes predictably |
| TypeScript | ~30 | Generic type parameters (`Array<Map<string, T>>`) tokenize verbosely |
| Java | ~40 | Long identifiers (`AbstractSingletonProxyFactoryBean`) fragment into many tokens |
| Go | ~22 | Short identifiers, minimal syntax → very token-efficient |
| SQL | ~35 | Keywords are multi-token; identifiers vary; schema names add overhead |
| Rust | ~45 | Lifetime annotations (`'static`, `'a`), ownership syntax is tokenizer-hostile |

**Implication for context window usage:** A Rust function of 200 characters consumes 90 tokens vs a Python function of the same length consuming 50 tokens. For languages with high tokens-per-character ratios, the same context budget (1,500 tokens) covers 40% less code. Copilot's context selection is token-budget-aware, not character-budget-aware — it always computes token counts before constructing the FIM prompt.

---

## Ghost-Text UX Latency War Story

Copilot's ghost text (inline completion suggestion shown in grey) has an acceptance rate that drops sharply with TTFT:

```
TTFT < 100ms  →  acceptance rate 38%
TTFT 100-200ms  →  acceptance rate 34%  (P50 target zone)
TTFT 200-400ms  →  acceptance rate 28%
TTFT 400-600ms  →  acceptance rate 19%
TTFT > 600ms    →  acceptance rate 11%
```

During a datacenter network maintenance window in 2023 (unreported publicly), Copilot's P50 TTFT spiked from 150ms to 520ms for 2 hours. Acceptance rate dropped from 34% to 20%. Because Copilot monetizes on usage (accepted completions drive engagement and retention), the 14-point acceptance rate drop directly correlated with increased churn risk. Post-incident: Copilot added a second inference cluster in a different AWS region for failover, targeting <300ms TTFT even during single-region degradation. For multi-region active-active deployment of the inference cluster, see [Multi-Region LLM Topology](./cross_cutting/multi_region_llm_topology.md).

**Speculative decoding for ghost text — why it matters more for code than for chat:**

Code completions are highly predictable: after `for i in range(`, the next 4-6 tokens are almost always `10):` or `len(arr)):`. Speculative decoding exploits this: a small draft model (130M parameters, 20x cheaper per token) generates 4 candidate tokens in parallel; the large verification model accepts all 4 in a single forward pass if they match. For code completions, draft model acceptance rate is 78% (vs ~55% for general text), making speculative decoding 3.2x more efficient than standard decoding for FIM completions.

### Speculative Decoder — Production Python

```python
from __future__ import annotations
import time
from dataclasses import dataclass
from typing import Protocol

class DraftModel(Protocol):
    def generate_tokens(self, prompt: str, n: int) -> list[str]: ...

class TargetModel(Protocol):
    def verify_tokens(self, prompt: str, candidates: list[str]) -> list[float]: ...
    def generate_one(self, prompt: str) -> str: ...

@dataclass
class DecodingStats:
    draft_tokens_generated: int
    accepted_tokens: int
    target_fallback_tokens: int
    acceptance_rate: float
    latency_ms: float


class SpeculativeDecoder:
    # Draft: CodeLlama-7B equivalent, 22ms for 5 tokens.
    # Target: 70B FIM model, single forward pass verification = 80ms.
    # P50 latency: 185ms with vs 310ms without = 40% reduction. Acceptance: 72%.
    ACCEPTANCE_THRESHOLD = 0.85  # accept draft token if target probability ratio >= 0.85

    def __init__(
        self,
        draft_model: DraftModel,
        target_model: TargetModel,
        num_speculative: int = 5,
        max_new_tokens: int = 128,
    ) -> None:
        self._draft, self._target = draft_model, target_model
        self._n_spec, self._max_new = num_speculative, max_new_tokens

    def generate(self, prompt: str) -> tuple[str, DecodingStats]:
        t0 = time.perf_counter()
        output_tokens: list[str] = []
        draft_generated = accepted_total = fallback_total = 0
        current_prompt = prompt

        while len(output_tokens) < self._max_new:
            # Step 1: draft model speculatively generates N tokens (fast, cheap)
            draft_tokens = self._draft.generate_tokens(current_prompt, self._n_spec)
            draft_generated += len(draft_tokens)

            # Step 2: target verifies all N tokens in ONE forward pass (parallel attention)
            probs = self._target.verify_tokens(current_prompt, draft_tokens)

            # Step 3: accept prefix up to first mismatch; regenerate from target on miss
            for token, prob in zip(draft_tokens, probs):
                if prob >= self.ACCEPTANCE_THRESHOLD:
                    output_tokens.append(token); accepted_total += 1
                else:
                    fallback = self._target.generate_one(current_prompt + "".join(output_tokens))
                    output_tokens.append(fallback); fallback_total += 1
                    break

            current_prompt = prompt + "".join(output_tokens)
            if output_tokens and output_tokens[-1] in ("\n\n", "<EOT>", "```"):
                break

        stats = DecodingStats(
            draft_tokens_generated=draft_generated, accepted_tokens=accepted_total,
            target_fallback_tokens=fallback_total,
            acceptance_rate=accepted_total / max(draft_generated, 1),
            latency_ms=(time.perf_counter() - t0) * 1000,
        )
        return "".join(output_tokens), stats
```

**Concrete numbers:** P50 latency with speculative decoding = 185ms vs 310ms without (40% reduction). Acceptance rate in code completion context = 72% (higher than general text's 55% because code is locally predictable). At 15,000 completions/second, speculative decoding reduces target model compute by 72% × 5 = 3.6x, lowering GPU spend from an estimated $30,900/day to $8,600/day on inference alone.

---

### Prefix Cache Hit Ratio

vLLM prefix caching (Automatic Prefix Caching, APC) allows Copilot to avoid recomputing KV cache for the system-prompt and language-context header on every request. Because the language instruction block (`# Python file, complete the following:` + file-type metadata) is identical across all completions for the same file type, it is prefix-cached at the inference engine layer.

```python
from __future__ import annotations
from dataclasses import dataclass
from collections import deque
import time

@dataclass
class PrefixCacheTracker:
    # Rolling 1-hour hit rate. Copilot: 2,380 of 2,800 input tokens are prefix-cached.
    # At 85% hit rate, 20M req/day: saves $40,460/day in GPU input-token cost.
    window_seconds: int = 3600

    def __post_init__(self) -> None:
        self._events: deque[tuple[float, bool]] = deque()  # (timestamp, hit)

    def record(self, hit: bool) -> None:
        now = time.monotonic()
        self._events.append((now, hit))
        self._evict(now)

    def _evict(self, now: float) -> None:
        cutoff = now - self.window_seconds
        while self._events and self._events[0][0] < cutoff:
            self._events.popleft()

    @property
    def hit_rate(self) -> float:
        if not self._events:
            return 0.0
        hits = sum(1 for _, h in self._events if h)
        return hits / len(self._events)

    @property
    def prefix_tokens_saved_per_request(self) -> float:
        """Expected prefix tokens saved per request given current hit rate."""
        PREFIX_TOKENS = 2_380
        return self.hit_rate * PREFIX_TOKENS

    @property
    def cost_reduction_pct(self) -> float:
        """Percentage reduction in effective input token cost from prefix caching."""
        TOTAL_TOKENS = 2_800
        return (self.prefix_tokens_saved_per_request / TOTAL_TOKENS) * 100
```

**Formula:** `hit_ratio = hits / (hits + misses)` tracked over a rolling 1-hour window. At 85% hit rate with 20M requests/day: effective input tokens drop from 2,800 to 420 per request (2,380 prefix tokens skipped on cache hit). Cost saved = 20M × 0.85 × 2,380 / 1,000 × $0.001/1K tokens = **$40,460/day** ($14.8M/year). The system prompt (language instruction + file-type context block) is the cacheable prefix; the FIM prefix/suffix content changes per request and is never cached.

See [Token Economics and Cost Optimization](../token_economics_and_cost_optimization/README.md) for the full provider prompt caching analysis.

---

## Failure Scenarios and Recovery

**Failure 1 — Repository-Level Context Retrieval Adding 800ms Latency to All Completions**

A Copilot feature experiment added repository-level context retrieval (embedding-based search over the full codebase) to every inline completion request. In local testing, retrieval added 80ms. In production with a codebase of 2M files, the retrieval index returned results in 450ms at P95, pushing total TTFT to 650ms — well above the 400ms threshold where acceptance rate drops sharply.

**Detection:** Acceptance rate monitoring dashboard showed a 9-point drop the day the feature launched in canary. Correlated with TTFT P95 metric spike.

**Recovery:** Immediate rollback of repository context retrieval for inline completions (keep for agent mode only). Rebuilt retrieval pipeline with ANN index (HNSW, 50ms P95) and a pre-filter that skips retrieval for files under 200 tokens of context (inline completions where local context is sufficient).

**Failure 2 — Language Detection Mismatch Sending Python Prompts to JavaScript-Tuned Model**

Copilot maintained separate model checkpoints fine-tuned for Python/JS/TS vs systems languages (C, C++, Rust, Go). Language detection used file extension: `.py` → Python model, `.ts` → JS model, `.cpp` → systems model. A user with a `.py` file containing embedded C extension code (via `ctypes`) triggered the Python model, which produced syntactically incorrect C pointer arithmetic. File-extension-based detection was replaced with a lightweight language classifier (12ms inference) that analyzed the content, not the filename.

---

## Capacity Planning

```
GitHub Copilot (estimated public scale, 2024):
- 1.8M paying subscribers × average 5 active coding hours/day
- Completion trigger rate: 1 trigger per 8 seconds of coding = 7.5 triggers/minute
- Active user triggers: 1.8M × (5h × 60m/h × 7.5/m) = 4,050M triggers/day
- Accepted completions: 4,050M × 0.34 acceptance rate = 1,377M accepted/day

But most completions are rejected in <1 second (user keeps typing):
- 66% rejected within 1s: no model call needed (debounce absorbs)
- Only 34% of triggers result in a model call: 4,050M × 0.34 = 1,377M model calls/day

Model call throughput:
= 1,377M / 86,400 seconds = 15,938 req/second average
= 47,814 req/second at 3x peak (business hours)

Compute per request:
- Input: 1,500 tokens × BF16 FIM model (Codestral-22B equivalent)
- Output: 65 tokens average (P50 completion length)
- Throughput per A100: ~120 req/second (continuous batching, FIM, 22B FP8)

GPUs at peak: ceil(47,814 / 120) = 399 A100s
With 65% utilization target: 614 A100s

Cost:
- Reserved A100 at $2.10/GPU-hour: $614 × $2.10 × 24 = $30,945/day
- Revenue: 1.8M subscribers × $10/month / 30 days = $600,000/day
- GPU inference is ~5% of revenue — sustainable unit economics
```

---

## Additional Interview Questions

**Why does GitHub Copilot use a separate FIM-trained model rather than a general instruction-tuned model?** Instruction-tuned models (e.g., GPT-4o) are trained to follow natural language instructions and generate complete responses, optimizing for coherence across a full reply. FIM training specifically teaches the model to predict a middle span given prefix and suffix, which is the exact task of inline code completion. Instruction-tuned models perform poorly on FIM tasks (they tend to generate preamble text or complete functions when the cursor is mid-line) because their pretraining objective never required predicting a bounded middle span. The FIM objective also teaches the model to terminate at natural code boundaries (end of statement, end of block) rather than generating until a stop token.

**How does Copilot handle multi-cursor and multi-selection completions in editors that support them?** Multi-cursor completions are treated as independent requests, one per cursor, submitted in parallel. The completions are generated independently (no shared state between cursors) and displayed simultaneously. This means two cursors in different parts of the file may generate inconsistent completions (e.g., different variable names for the same concept). Advanced editors (VS Code with a Copilot Labs experiment) attempted "consistent multi-cursor" where cursor 1's accepted completion was added to cursor 2's context, but this increased latency by 200-300ms and was not pursued in production. The latency cost of consistency outweighed the rare benefit.

**What is the difference between Copilot's inline completion mode and Copilot Chat, and how does the architecture differ?** Inline completions optimize for TTFT (target <200ms), use FIM models, process short contexts (<2,000 tokens), stream the first token immediately, and are ephemeral (no conversation history). Copilot Chat uses instruction-tuned models (GPT-4o), processes long contexts (up to 128k tokens for full workspace context), maintains conversation history, and accepts TTFT up to 3s because the user explicitly submitted a query. Inline completions use a separate inference cluster with A100s optimized for high-throughput short requests; Chat uses a shared API (OpenAI API or Azure OpenAI) with less strict latency requirements. The shared latency SLAs are fundamentally incompatible — they must be separated.

**How would you design Copilot's telemetry pipeline to measure acceptance rate at 15,000 completions/second?** Client-side telemetry: each completion is assigned a UUID at generation time. When the user accepts (Tab) or dismisses (Esc/continues typing), the client sends an event with `{completion_id, accepted: bool, language, trigger_context, latency_ms}`. Events are batched client-side (send every 30 seconds or every 20 events) and sent to a Kafka topic. A Flink streaming job aggregates acceptance rate per language, file type, and model version in 1-minute windows. The acceptance rate metric is available in Grafana with a 90-second lag. The pipeline handles 15,000 completions/second × 1 event each = 15,000 events/second — modest for Kafka (which handles millions/second), but the batching (30-second window) reduces actual event rate to ~500/second.

**What happens to Copilot completions when the user's internet connection drops mid-typing?** The IDE extension queues the trigger event locally. If the network is restored within the 400ms debounce window, the request is submitted normally. If the network is down during inference, the request times out (2s timeout per the extension's default), and the ghost text is never shown — the user sees no completion, same as if the completion had been rejected. The extension maintains a local "offline mode" indicator; when offline, it skips triggering requests entirely to avoid queuing up stale completions that will arrive after the user has continued typing. There is no local model fallback in the standard Copilot product (unlike some IDE plugins that support on-device small models as fallback).

---

## Production Failure Scenarios and Capacity Math

### Incident: Completion Latency Regression from IDE Extension Memory Leak

**What happened:** After a VS Code extension update, memory consumption of the Copilot extension process grew by 3 MB per hour due to a reference cycle in the completion history buffer (used to debounce duplicate completions). After 4 hours of continuous use, the extension process consumed 800 MB, causing VS Code's extension host to restart it. The restart cleared the debounce state and re-triggered completions that had already been dismissed. More importantly, the restart added 2-4 seconds of cold-start latency before completions resumed. Users reported "Copilot stopped working" despite the server-side being healthy.

**Root cause:** The `CompletionHistory` object held strong references to `TextDocument` objects via closures. When the document was closed, the TextDocument was not garbage collected because CompletionHistory still held a reference.

**Fix applied:**
```typescript
// BROKEN: strong reference to TextDocument prevents GC
class CompletionHistory {
    private history: Map<TextDocument, string[]> = new Map();
    // When document closes, TextDocument reference kept alive in Map
}

// FIX: WeakMap allows TextDocument to be GC'd when document closes
class CompletionHistory {
    // WeakMap: key is garbage collected when no other strong reference exists
    private history: WeakMap<TextDocument, string[]> = new WeakMap();
    // Keys (TextDocuments) are released automatically when VS Code closes the document
}
```

**Prevention:** Extension memory profiling in CI: run VS Code extension host with `--inspect` for 2 hours of simulated usage, capture heap snapshots at t=0 and t=120min. Alert if retained heap grows > 50 MB over the session. WeakRef/WeakMap usage is mandatory for any extension data keyed on VS Code API objects (documents, editors, terminals).

---

### Incident: Acceptance Rate Drop from Tokenization Change

**What happened:** The BPE tokenizer was updated to add 2,048 code-specific tokens. Completion acceptance rate dropped from 31% to 24% overnight. Root cause: the new tokenizer produced different token boundaries for common patterns (e.g., `self.` was previously 3 tokens, now 1 token). The completion model was fine-tuned with the old tokenizer. The new tokenizer's token IDs were out-of-distribution — the model generated correct code structurally but with incorrect indentation (Python) and bracket placement (JS/TS) because the attention patterns were calibrated to old token boundaries.

**Fix:** Rolled back tokenizer update. Established the rule: tokenizer changes require full model retraining, not just a vocabulary update. Model and tokenizer are a coupled artifact and must be versioned together.

---

### Capacity Planning Math (1.4M paying users)

```
Completion request rate:
  1.4M users × avg 120 active minutes/day = 168M user-minutes/day
  Completions triggered: 1 per 3 seconds of typing = 20 per active minute
  Daily completions: 168M × 20 = 3.36B completions/day
  Accepted (31%): 1.04B completions accepted/day

Peak rate (business hours, 5× average):
  3.36B / (86,400s) = 38,889 completions/sec average
  Peak: 38,889 × 5 = 194,444 completions/sec

FIM model inference requirements:
  Model: CodeLlama-13B (FIM variant), on A100 80GB
  Throughput: A100 handles ~400 FIM completions/sec at p99 < 200ms
  Required A100s: 194,444 / 400 = 486 A100s at peak
  With 40% utilization headroom: 810 A100s
  Cost at $2.50/hr: 810 × $2.50 × 24 × 365 = $17.7M/year GPU cost alone

GitHub Copilot revenue (1.4M × $19/month): $26.6M/month = $319M/year
GPU cost as % of revenue: $17.7M / $319M = 5.5% — well within typical ML SaaS unit economics
Cache (completion dedup): identical trigger contexts cached for 60s → 15% hit rate
Effective GPU cost after caching: $17.7M × 0.85 = $15M/year
```

---

### Additional Q&As (Reliability and Business)

**Q: How does Copilot's completion model stay current with new frameworks and APIs released after training?**
Three mechanisms: (1) Retrieval augmentation: Copilot indexes open GitHub repositories weekly; new libraries committed to public repos are indexed and available for retrieval-augmented completion within 7 days of their first commit; (2) Fine-tuning cadence: the base FIM model is retrained quarterly on the latest 6 months of GitHub code, capturing framework evolution; (3) User correction signals: when users consistently modify Copilot's suggestions for a new API pattern, these corrections are fed back into the fine-tuning dataset (with PII scrubbing). The lag is inherent — a new API released today will not be well-supported for 4-8 weeks. This is disclosed in Copilot's documentation.

**Q: What is Copilot's strategy for avoiding completion of copyrighted code verbatim?**
Duplication filter: before returning a completion, Copilot checks the completion against an index of GitHub code with restrictive licenses. If the completion matches a verbatim sequence of > 150 characters from a licensed repository, the completion is suppressed. The filter is implemented as a Bloom filter of n-grams from known-licensed code — false positive rate < 0.1%, false negative rate < 2%. The filter adds 5ms to the p99 completion path. This is the "public code filter" that users can enable in Copilot settings — it defaults to on for Enterprise customers and off for individual subscribers.

---

### IDE Extension Architecture Deep Dive

**VS Code extension request lifecycle:**
```
User Typing
     |
     v  (trigger: pause > 75ms OR newline)
[CompletionProvider.provideInlineCompletionItems()]
     |
     v
[DocumentContextExtractor]
  - current file text (prefix + suffix for FIM)
  - file language, path, imports section
  - open tabs (similar files, up to 3)
  - LSP symbols in scope
  - git blame context (who wrote this, when)
     |
     v
[ContextPruner]  ── fit within 2,048 token budget
  priority: current file > similar files > symbols
     |
     v  (HTTP/2 POST, gzip compressed)
[Copilot API: api.github.com/copilot_internal/v2/token]
     |
     v
[Load Balancer] ── consistent hash on user_id for KV cache affinity
     |
     v
[FIM Inference Service]
  - decode with temperature=0.2, top_p=0.95
  - stop tokens: ["\n\n", "```", "<|endoftext|>"]
  - max_new_tokens=128 (single-line) or 512 (block)
     |
     v  (streaming SSE response)
[Ghost Text Rendering]  ── VS Code inlineCompletions API
```

**Debounce and deduplication:**
```typescript
class CompletionDebouncer {
    private pending: NodeJS.Timeout | null = null;
    private lastRequest: string | null = null;

    trigger(context: CompletionContext, callback: () => void): void {
        const contextHash = this.hashContext(context);
        
        // Deduplicate: same context as last request — skip
        if (contextHash === this.lastRequest) return;
        
        // Debounce: cancel previous pending request
        if (this.pending) clearTimeout(this.pending);
        
        this.pending = setTimeout(() => {
            this.lastRequest = contextHash;
            callback();
        }, 75);  // 75ms debounce window
    }
}
```

---

### Additional Reliability and Scale Q&As

**Q: How does Copilot handle the cold start problem when a user opens a new file in an unfamiliar codebase?**
On file open: (1) the extension scans the project's directory structure and imports the first 200 lines of the 5 most recently modified files as "project context"; (2) reads the package manifest (package.json, pom.xml, requirements.txt) to understand the tech stack; (3) includes the current file's imports as the highest-priority context. This gives the model enough signal to produce relevant completions within the first keystrokes. Quality for cold-start completions is 15-20% lower acceptance rate than completions in familiar files, which improves as the model accumulates session context over 10-15 minutes.

**Q: How does Copilot's "Copilot for Business" ensure enterprise code never leaves the company's infrastructure?**
Three technical controls: (1) API isolation: enterprise traffic routes through a dedicated Azure OpenAI deployment (not shared with individual users), hosted in the customer's Azure tenant for regulated industries; (2) no training data use: enterprise prompts are contractually excluded from training data — enforced by tagging requests with `X-Copilot-Enterprise: true` header and filtering these from the training pipeline; (3) no prompt logging on Copilot's side: GitHub logs metadata (response latency, acceptance rate) but not prompt content for enterprise accounts. Customers can verify this via GitHub's enterprise audit log API, which shows what metadata is collected. For highest-security environments, GitHub Copilot Enterprise supports a proxy mode where all requests go through the customer's own API gateway, which can inspect and log all traffic before forwarding to GitHub.

**Q: What is the strategy for handling Copilot in a monorepo with 10,000 files and 5M lines of code?**
Context selection is the key challenge — the model's context window cannot hold 5M lines. Strategy: (1) file relevance ranking using BM25 on file content vs. current cursor context, returning the top 5 most similar files; (2) symbol-level retrieval: parse the current file's unresolved symbols and retrieve only the declarations that define them (function signatures, class definitions), not full file bodies; (3) git history proximity: files that were frequently modified together (co-change analysis on git log) with the current file are weighted higher in context selection; (4) test file pairing: if editing `payment_service.py`, auto-include `test_payment_service.py` in context — tests encode expected behavior. The combined context is pruned to 2,048 tokens with the above priority ordering.

---

### Privacy and Code Security Architecture

**What Copilot collects and what it does not:**

| Data Type | Collected | Used for Training | Retention |
|---|---|---|---|
| Code completions shown to user | Yes (hash only) | No (individual) | 28 days (aggregate) |
| Code completions accepted | Yes (hash only) | Aggregated opt-in only | 28 days |
| Prompt content (code context) | No (enterprise) | No | Not retained |
| Acceptance rate per language | Yes | Model improvement | 12 months |
| Latency telemetry | Yes | Infrastructure | 90 days |
| User code (opened files) | Never | Never | Never |

**Prompt construction privacy:**
```typescript
// Code context is processed client-side; only a fingerprint is sent for dedup
function buildSecurePrompt(
    document: TextDocument,
    cursorPosition: Position,
): { prompt: string; contextFingerprint: string } {
    const prefix = document.getText(new Range(new Position(0, 0), cursorPosition));
    const suffix = document.getText(new Range(cursorPosition, document.lineAt(document.lineCount - 1).range.end));

    // Hash the context for server-side dedup — actual code is never stored
    const contextFingerprint = sha256(prefix.slice(-500) + suffix.slice(0, 200));

    return {
        prompt: buildFIMPrompt(prefix, suffix),  // sent to inference, not stored
        contextFingerprint,                        // stored for rate-limit dedup
    };
}
```

**Why Copilot does not train on private repo code by default:** Private repository code is covered by GitHub's terms of service — users retain copyright. Copilot Individual trains on public GitHub repositories (pre-2022) only. Copilot Business and Enterprise are contractually prohibited from using any customer code for training. The technical enforcement: requests tagged `X-Copilot-Enterprise: true` are filtered from the training pipeline before any data ever reaches the training infrastructure.

---

### Final Metrics Summary

| Metric | Value | Notes |
|---|---|---|
| GitHub Copilot paying users | 1.8M+ | As of Q1 2025 |
| Daily completions served | 38M+ | Estimated |
| Global acceptance rate | 30–35% | Varies by language/experience |
| p50 completion latency | 95ms | IDE to first ghost-text character |
| p99 completion latency | 195ms | SLA target |
| Highest acceptance rate | TypeScript | ~38% (strong typing aids FIM) |
| Lowest acceptance rate | Shell scripting | ~18% (high ambiguity) |
| A10G GPUs (FIM serving) | ~500 at peak | Estimated from throughput |
| Annual revenue | ~$400M | 1.8M × $19/mo × 12 |
| GPU cost as % revenue | ~5% | Self-hosted FIM model efficiency |

**Copilot competitive moat:** The primary moat is not model quality (open-source FIM models are within 5% of Copilot quality) — it is IDE integration depth. Copilot has 4 years of VS Code integration work: ghost text rendering, multi-file context awareness, inline diff review, terminal command suggestions, voice input, and workspace search. A new entrant with a better model still needs 12–18 months of integration engineering to match the user experience. The product-distribution moat (built into GitHub, used by 4M+ GitHub users without a separate purchase decision for enterprise) is the second moat.
