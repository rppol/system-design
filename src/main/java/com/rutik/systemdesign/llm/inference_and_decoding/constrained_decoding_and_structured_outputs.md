# Constrained Decoding & Structured Outputs — Internals

Deep-dive sub-file of [Inference & Decoding](README.md). Covers how guided decoding actually works — logit masking, FSM/CFG compilation, XGrammar and llguidance internals, jump-forward decoding, provider "structured outputs" features — plus the quality tradeoffs and failure modes.

---

## 1. Concept Overview

Constrained decoding (also: guided decoding, grammar-constrained generation) guarantees that an LLM's output conforms to a formal specification — a JSON schema, a regular expression, or a context-free grammar — by intervening in the sampling loop itself. At every decoding step, the engine computes the set of vocabulary tokens that keep the partial output valid, sets the logits of all other tokens to negative infinity, and only then samples. The result is *syntactic validity by construction*: a 100% parse rate, not a 95% parse rate with retries.

This sits in contrast to the two softer approaches: prompting ("respond only in JSON") which fails 1–10% of the time depending on model and schema complexity, and validate-and-retry loops (Instructor/Pydantic style) which fix failures after the fact at 2–3× cost in the tail. Provider features like OpenAI Structured Outputs and the JSON modes in vLLM/SGLang/TensorRT-LLM are all constrained decoding under the hood.

The engineering substance is in *how* the valid-token set is computed fast enough: a naive grammar check across a 128K-token vocabulary per step would dwarf the model's own forward pass. The solutions — DFA compilation over the token vocabulary (Outlines), adaptive token-mask caches with pushdown automata (XGrammar), token-trie lexers (llguidance) — are what this file covers.

---

## 2. Intuition

> **One-line analogy**: Constrained decoding is autocomplete that physically removes the keys from the keyboard that would produce an invalid document — the model can only type what still parses.

**Mental model**: Think of the grammar as a state machine walking alongside generation. After emitting `{"age":`, the machine is in a state where only digits, whitespace, or `-` are legal. The engine looks up "which of my 128K tokens begin with something legal from this state?", masks the rest, and the model picks among survivors. The model still chooses *which* valid token — the grammar never picks content, it only vetoes syntax.

**Why it matters**: Every production LLM system that feeds model output into code — function calling, extraction pipelines, agents parsing tool arguments — dies on malformed output. Senior interviews probe whether you know the difference between "ask nicely for JSON", "retry until it parses", and "make invalid output impossible", and the cost/quality profile of each.

**Key insight**: The hard problem is the mismatch of alphabets. Grammars are defined over *characters*; the model emits *tokens*, and a given character string has many tokenizations (`"age"` might be one token or three). Every fast implementation is fundamentally a clever precomputation that bridges character-level grammar states to token-level masks.

---

## 3. Core Principles

1. **Mask, then sample.** Validity is enforced before sampling, so it composes with any sampling strategy (greedy, temperature, top-p) and never requires regeneration.
2. **Compile once, mask cheaply.** Schema→automaton compilation can take milliseconds to seconds; the per-step mask lookup must be microseconds. All engines cache compiled artifacts keyed by schema/grammar hash.
3. **Regular vs context-free is a real boundary.** Regexes compile to finite automata with O(1) state transitions. JSON with arbitrary nesting needs a pushdown automaton (stack for brace matching) — that stack is why JSON-schema engines are harder than regex engines.
4. **The grammar guarantees syntax, never semantics.** `{"price": -999999}` is schema-valid. Semantic validation (Pydantic validators, business rules) still runs after parsing.
5. **Constraints distort the distribution.** Masking renormalizes probability over surviving tokens. If the model "wanted" an invalid continuation, the constraint silently redirects that mass — which is where quality degradation can enter.
6. **Forced tokens need no forward pass.** When the grammar admits exactly one continuation (e.g., closing `"}` after the last field), the engine can append it directly — jump-forward decoding — saving GPU work.

---

## 4. Types / Approaches

| Approach | Spec language | Automaton | Per-token cost | Used by |
|----------|--------------|-----------|---------------|---------|
| FSM/DFA indexing (Outlines) | Regex (JSON schema lowered to regex) | DFA over token vocab, precomputed state→mask index | ~O(1) lookup, ~50μs | vLLM `outlines` backend, .txt |
| Grammar + token-mask cache (XGrammar) | EBNF / JSON schema | Pushdown automaton + adaptive token mask cache | ~36μs, overlapped with GPU | vLLM default JSON backend, SGLang, TensorRT-LLM, MLC |
| Token-trie lexer (llguidance) | Lark-style CFG, regex | Earley-style parser + lexer over token trie | ~50μs/token, no compile step | guidance, vLLM `guidance` backend, SGLang |
| GBNF (llama.cpp) | BNF grammar | Interpreted PDA, per-step scan | Slower (ms-scale on big vocabs) | llama.cpp, Ollama JSON mode |
| Provider-managed | JSON Schema subset | Compiled server-side, cached per schema | First request +ms–s latency | OpenAI Structured Outputs, Gemini responseSchema |
| Validate-and-retry (no masking) | Pydantic model | None — parse after the fact | 1–3 extra full calls on failure | Instructor, LangChain output parsers |

Spectrum of guarantees: prompting (~90–99% valid) < JSON *mode* (valid JSON, arbitrary shape) < JSON *schema* / structured outputs (exact shape) < CFG (arbitrary formal language: SQL dialects, DSLs, tool-call syntax).

---

## 5. Architecture Diagrams

Per-step masking inside the inference engine:

```
                       ┌───────────────────────────────┐
 prompt + generated ──>│        LLM forward pass        │──> logits [V=128K]
                       └───────────────────────────────┘         │
                                                                 v
   grammar state s_t ──> mask lookup: allowed(s_t) ──> bitmask [V]   (CPU, ~36-50μs,
                                  │                        │          overlapped with
                                  │                        v          the forward pass)
                                  │            logits[~allowed] = -inf
                                  │                        │
                                  │                        v
                                  │            softmax + sample ──> token t
                                  │                        │
                                  └── advance: s_{t+1} = δ(s_t, t) <─┘
```

XGrammar's core trick — split the vocabulary per grammar state, precompute what can be precomputed:

```
                 JSON schema / EBNF
                        │ compile (once, cached by hash)
                        v
        ┌─────────────────────────────────┐
        │ Adaptive token mask cache        │   context-INDEPENDENT tokens:
        │  state -> {accepted | rejected}  │   validity decidable without the stack
        │  (covers ~99% of vocab/state)    │   -> free at runtime
        └─────────────────────────────────┘
                        +
        ┌─────────────────────────────────┐
        │ Runtime check w/ persistent      │   context-DEPENDENT tokens (~1%):
        │ execution stack (PDA)            │   need brace-depth / recursion context
        └─────────────────────────────────┘
```

Jump-forward decoding (SGLang):

```
state after `{"user": {`     grammar says next MUST be `"name": "`
   normal decoding: 4 forward passes for `"name`, `":`, ` "`, ...
   jump-forward:    append forced string directly, 0 forward passes,
                    re-tokenize boundary, resume at the first free choice
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 The naive version (to understand what engines optimize away)

```python
import torch
from transformers import PreTrainedTokenizer


def naive_constrained_step(
    logits: torch.Tensor,            # [V] next-token logits
    generated_text: str,
    is_valid_prefix,                 # Callable[[str], bool] from the grammar
    tokenizer: PreTrainedTokenizer,
) -> torch.Tensor:
    mask = torch.full_like(logits, float("-inf"))
    for token_id in range(logits.shape[0]):          # 128K iterations...
        candidate = generated_text + tokenizer.decode([token_id])
        if is_valid_prefix(candidate):               # ...each running a parser
            mask[token_id] = 0.0
    return logits + mask
# At 128K vocab x a parser call each, this costs ~100ms+/token —
# often more than the model's own forward pass. Everything below
# exists to replace this loop with a precomputed lookup.
```

### 6.2 Outlines: regex → DFA → token-level index

Outlines lowers a JSON schema to a regex, compiles the regex to a character-level DFA, then walks the *token vocabulary* against the DFA once at build time: for every DFA state, it records which tokens (multi-character!) trace a path of valid transitions, and which state each lands in. The result is a dict `index[state] -> {token_id: next_state}`. At runtime, masking is a hash lookup — O(1) regardless of vocab size.

Costs: index construction is O(states × vocab) — sub-second for simple schemas, but complex schemas (long enums, deeply nested objects, unbounded strings) can take seconds to tens of seconds; production deployments cache compiled indexes by schema hash. Limitation: regexes cannot express unbounded nesting, so recursive schemas are bounded to a fixed depth during lowering.

### 6.3 XGrammar: pushdown automaton + adaptive token mask cache

JSON's nesting needs a stack. XGrammar keeps a byte-level pushdown automaton but observes that for any grammar state, the validity of *most* tokens does not depend on the stack at all (a token of pure ASCII letters is fine inside any string context). It therefore classifies tokens per state as **context-independent** (validity precomputed into an "adaptive token mask cache" at compile time) or **context-dependent** (the ~1% needing a runtime stack check), and maintains a persistent execution stack with rollback for those. Mask generation runs on CPU *overlapped with the GPU forward pass*, so its ~36μs effectively disappears from token latency. The paper reports up to ~100× faster mask generation than prior CFG engines; it is the default JSON-schema backend in vLLM and SGLang.

### 6.4 llguidance: lexer over the token trie

llguidance (the engine behind Microsoft Guidance) splits the grammar classically into lexer (regex-level) and parser (Earley over lexemes), and computes masks by walking the *token trie* — the prefix tree of all vocabulary byte strings — against the lexer's DFA, so shared token prefixes are checked once. It needs no expensive compilation step (~no warm-up) and sustains ~50μs per token, which is why it backs the `guidance` backend in vLLM and is attractive when schemas are dynamic per-request and you can't amortize compile time.

### 6.5 Jump-forward decoding and the token-boundary trap

When the automaton's state admits exactly one legal continuation string (structural boilerplate like `", "next_field": `), SGLang appends it without forward passes — on JSON-heavy outputs with long key names this yields up to ~2–3× throughput. The trap: the forced string was chosen at the *character* level, and naively continuing generation can leave the sequence mid-token relative to the model's canonical tokenization (`{"name": "` may canonically tokenize with the opening quote glued to what follows). Engines re-tokenize the boundary ("token healing": back up to the last unambiguous token and let the model re-emit the boundary) — without it, the model sees token sequences it never saw in training and quality drops on the very next field.

The same boundary problem appears at the prompt/generation seam (Guidance's original token-healing use case) and is the deep reason character-level grammars and BPE vocabularies are awkward partners: one string, many tokenizations, but the model has strong priors only for the canonical one.

### 6.6 Broken → fixed: schema design is part of the system

```python
# BROKEN: two classic schema mistakes
from pydantic import BaseModel


class Verdict(BaseModel):
    answer: bool          # 1) the model must commit to the answer FIRST...
    reasoning: str        # 2) ...and reason afterwards; also unbounded string


# Why it fails:
# - Generation is sequential. With `answer` first, the model decides true/false
#   before it has produced a single reasoning token — you've silently disabled
#   chain-of-thought. Accuracy on reasoning-heavy extraction drops measurably.
# - `reasoning: str` with no bound: the grammar happily accepts 30K tokens of
#   string content; combined with whitespace-tolerant grammars this is the
#   classic "model rambles forever inside a JSON string" incident.
```

```python
# FIX: order fields so thinking precedes commitment; bound everything
from pydantic import BaseModel, Field


class Verdict(BaseModel):
    reasoning: str = Field(max_length=2000)   # CoT happens inside the schema, first
    confidence: float = Field(ge=0.0, le=1.0)
    answer: bool                              # committed last, after reasoning tokens


# Equally valid alternative: two-pass — free-form CoT generation, then a second
# cheap constrained call that extracts the structured verdict from the CoT.
```

### 6.7 Quality: does constraining hurt?

The 2024 "Let Me Speak Freely?" paper reported reasoning degradation under format restriction; the rebuttal from the Outlines team ("Say What You Mean") showed that with schema-aware prompting (tell the model the schema in the prompt, don't rely on the mask alone) and reasoning-first field order, constrained generation matches or beats unconstrained-then-parse. The synthesis that holds up in practice: **the mask is not the problem; surprising the model is.** Degradation appears when (a) the prompt never mentions the format the mask enforces, (b) fields force premature commitment, or (c) forced/healed boundaries leave non-canonical tokenizations. Fix those three and constrained decoding is quality-neutral with a 100% parse rate.

---

## 7. Real-World Examples

- **OpenAI Structured Outputs** (`response_format={"type": "json_schema", "strict": true}`) — server-side schema→grammar compilation with per-schema artifact caching; first request with a new schema pays added latency (up to ~10s for complex schemas per OpenAI's docs), subsequent requests hit the cache. Supports a constrained JSON Schema subset: all fields `required` (optionality via union with `null`), `additionalProperties: false` mandatory, no `oneOf` originally, limits on nesting depth (~5 levels per launch docs) and enum sizes. Function calling with `strict: true` runs the same machinery over tool parameter schemas.
- **vLLM** — `structured_outputs` / guided decoding with selectable backends: `xgrammar` (default for JSON schema), `guidance` (llguidance), with Outlines lineage for regex/choice constraints; exposes `guided_json`, `guided_regex`, `guided_choice`, `guided_grammar` (EBNF).
- **SGLang** — XGrammar default plus jump-forward decoding; its benchmarks on JSON-extraction workloads showed up to ~2–3× throughput from skipping structural tokens.
- **llama.cpp / Ollama** — GBNF grammars; Ollama's JSON mode is a GBNF JSON grammar under the hood. Slower masking (interpreted, per-step scans) but runs anywhere.
- **Anthropic** — structured outputs via tool use (the API guarantees tool `input` conforms to the tool's JSON schema) and a structured-outputs response format on newer models; same masking principle, provider-managed.
- **Instructor** (Jia/Liu) — the dominant *retry-based* alternative: Pydantic model → tool schema → parse → on `ValidationError`, re-prompt with the error message, up to N retries. No engine support needed; works against any API. See [structured_outputs_and_instructor.md](../agentic_frameworks/structured_outputs_and_instructor.md).

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Enforcement | Constrained decoding (100% valid, needs engine support) | Validate-and-retry (works on any API, 2–3× tail cost) | Control over serving stack |
| Backend | XGrammar (fastest steady-state, compile cost) | llguidance (no warm-up, dynamic schemas) | Schema reuse rate per process |
| Spec power | Regex/DFA (simple, bounded nesting) | CFG/PDA (full JSON, SQL, DSLs) | Does the format recurse? |
| Field order | Reasoning-first (preserves CoT, more tokens) | Answer-first (cheap, kills CoT) | Task reasoning depth |
| Structure source | One constrained pass | Two-pass: free CoT → extract | Latency budget vs max quality |
| Jump-forward | On (2–3× on boilerplate-heavy JSON) | Off (no boundary-healing risk) | Engine maturity, eval evidence |
| Provider feature | Managed structured outputs (zero infra) | Self-hosted guided decoding (any grammar, e.g. SQL) | Need for non-JSON grammars |

---

## 9. When to Use / When NOT to Use

**Use constrained decoding when:**
- Output feeds a parser, database, or downstream function — extraction, tool/function arguments, agent action selection, classification into enums.
- You self-host with vLLM/SGLang/TensorRT-LLM (near-free with XGrammar) or your provider exposes structured outputs.
- You need *non-JSON* formal outputs: SQL constrained to your dialect's grammar, valid Cypher, a DSL, ABNF-defined protocols — CFG-guided decoding is the only reliable tool here.
- Small/local models: weaker instruction-following makes masking disproportionately valuable (a 3B model with a grammar beats a 3B model with retries on parse rate at equal cost).

**Do NOT use when:**
- The output is prose for humans — masking adds nothing and field-shaped prose reads worse.
- The schema changes every request *and* requests are rare — compile cost is never amortized; use llguidance or retries instead.
- You need semantic guarantees ("the SQL is correct", "the citation exists") — grammar gives syntax only; pair with execution checks or verification.
- Deep recursive schemas on a DFA-only backend — bounded lowering will silently truncate valid nesting; use a PDA backend (XGrammar) instead.

---

## 10. Common Pitfalls

1. **Relying on the mask without telling the model the format.** The single biggest quality killer: the prompt says "extract the entities" with no schema description, the mask forces JSON, and the model's probability mass fights the grammar at every structural token. Always include the schema (or an example) in the prompt *and* enforce it with the mask.
2. **Answer-before-reasoning field order.** Forces commitment before any CoT tokens exist; measurable accuracy drops on multi-step extraction. Put free-text rationale fields first or run two-pass.
3. **Unbounded strings and permissive whitespace.** Grammars that accept any-length strings or arbitrary inter-token whitespace let a confused model loop ("infinite whitespace" incidents in early JSON-mode implementations — providers now cap whitespace runs). Set `maxLength`, cap array sizes, set `max_tokens` defensively.
4. **Compile-time surprises in the request path.** First call with a new complex schema can stall seconds (index/grammar compilation, or OpenAI's first-request artifact build). Pre-warm caches at deploy time; alert on p99 first-token latency per new schema hash.
5. **Schema features silently unsupported.** Engines and providers support JSON Schema *subsets* — `oneOf`, conditional sub-schemas, `patternProperties`, formats like `date-time` may be ignored or rejected. The failure mode is silent: output validates against the supported subset but violates the full schema. Validate post-hoc with the real schema in CI.
6. **Token-boundary damage after forced tokens.** Jump-forward or template-forced text that ends mid-canonical-token degrades the next few generated tokens. Symptom: quality dips exactly at field boundaries. Engines with token healing fix this; verify yours does before enabling jump-forward.
7. **Treating schema-valid as correct.** `{"iban": "not an iban"}` parses fine. Teams have shipped extraction pipelines with 100% parse rate dashboards while field-level accuracy was 60%. Track semantic accuracy separately from parse rate.
8. **Speculative decoding interactions.** Draft tokens must be grammar-checked too, or accepted spans can violate the grammar; engines that combine the two verify masks on the target model's acceptance pass. If you enable both, regression-test grammar adherence explicitly.

---

## 11. Technologies & Tools

| Tool | Role |
|------|------|
| XGrammar | Fastest open CFG engine; default in vLLM/SGLang/TensorRT-LLM/MLC |
| Outlines (.txt) | Regex/JSON-schema FSM indexing; pioneered the DFA-index approach |
| llguidance | Compile-free token-trie lexer engine; backs Guidance, vLLM `guidance` backend |
| Guidance (Microsoft) | Templating + constrained generation programs, token healing |
| llama.cpp GBNF | Grammar files for local inference; powers Ollama JSON mode |
| lm-format-enforcer | Earlier JSON/regex enforcement library (vLLM legacy backend) |
| Instructor | Retry-based Pydantic structured outputs over any API |
| OpenAI Structured Outputs / Gemini responseSchema / Anthropic tool use | Provider-managed constrained outputs |

---

## 12. Interview Questions with Answers

**Q1: How does constrained decoding actually guarantee valid JSON?**
At each decoding step the engine maintains a grammar automaton state for the text generated so far, looks up the set of vocabulary tokens whose addition keeps the output a valid prefix, sets all other logits to −inf, and samples from the survivors. Validity is enforced *before* sampling, so no retry is ever needed and the guarantee is absolute for syntax. The model still chooses content among valid tokens — the grammar only vetoes. Key supporting detail: the valid-token set is a precomputed lookup (DFA index or token-mask cache), not a per-step scan of the 128K vocabulary, which is what makes it fast enough for production.

**Q2: What's the difference between JSON mode and structured outputs?**
JSON mode guarantees the output is *some* syntactically valid JSON — any keys, any shape; you still parse-and-pray on structure. Structured outputs (OpenAI `json_schema` with `strict: true`, vLLM `guided_json`) compiles your specific schema to a grammar and masks against it, guaranteeing the exact shape: required keys, types, enums. Practical gotchas worth volunteering: structured outputs supports a JSON Schema *subset* (all fields required, `additionalProperties: false`, limited `oneOf`/nesting), and the first request per new schema pays compilation latency that is cached afterwards.

**Q3: Why can't you handle JSON schemas with just regular expressions?**
JSON allows arbitrary nesting — `{"a": {"a": {"a": ...}}}` — and matching balanced braces to unbounded depth is the canonical non-regular language; a finite automaton has no stack to count depth. Regex-based engines (Outlines lineage) handle this by lowering the schema to a regex with a *fixed maximum recursion depth*, which works for typical API schemas but silently bounds legal nesting. True CFG engines (XGrammar, llguidance, GBNF) run a pushdown automaton with an explicit stack and handle unbounded recursion. This is exactly why XGrammar's contribution matters: PDAs were historically much slower to mask with, and its token-mask cache closed the gap.

**Q4: Explain the token-boundary problem in grammar-constrained generation.**
Grammars are defined over characters; models emit BPE tokens, and one character string has many tokenizations — but the model only has strong priors for the canonical one. Two places this bites: (1) computing masks — a single token can span multiple grammar transitions (`":{"` crosses three states), so the engine must trace each token's full byte path through the automaton, not just its first character; (2) forced text — when a template or jump-forward inserts characters, the seam may not fall on a canonical token boundary, so the model continues from a token sequence it has essentially never seen in training and quality drops at exactly that seam. Token healing (back up to the last unambiguous token, let the model re-emit the boundary) is the standard fix.

**Q5: What is jump-forward decoding and what's the catch?**
When the grammar state admits exactly one legal continuation — structural boilerplate like `", "email": "` — the engine appends those tokens directly with zero forward passes, then resumes normal decoding at the next genuine choice point. On schema-heavy JSON (long key names, deep structure) SGLang reports ~2–3× throughput gains. The catch is the token-boundary problem from Q4: the forced string is character-chosen, so the engine must re-tokenize the boundary or the model resumes from a non-canonical tokenization and the first generated field after each jump degrades. Enable it only on engines that do boundary healing, and A/B quality, not just latency.

**Q6: Does constraining generation degrade output quality?**
It can, but the mechanism is misattributed. The "Let Me Speak Freely?" result (format restriction hurts reasoning) reproduces mainly when the prompt doesn't describe the format the mask enforces, or the schema forces answer-before-reasoning. The Outlines rebuttal showed schema-aware prompting plus reasoning-first field order makes constrained generation match or beat unconstrained-then-parse. The distribution distortion is real — masking renormalizes over surviving tokens — but it is small when the model already intends to produce the format. Practical protocol: state the schema in the prompt, put rationale fields first (or use two-pass CoT→extract), and eval semantic accuracy, not just parse rate.

**Q7: Why does field order in your Pydantic model affect accuracy?**
Generation is strictly sequential: the model produces the first schema field before any later one exists. If `answer: bool` precedes `reasoning: str`, the model must commit with zero reasoning tokens generated — chain-of-thought is structurally disabled, and you'll see accuracy drop on anything multi-step. Reversing the order makes the reasoning field function as in-schema CoT that conditions the answer. This is a favorite interview trap because the schema looks semantically identical either way; the fix costs nothing.

**Q8: Compare constrained decoding with Instructor-style retry loops. When is each right?**
Retry loops (parse → on ValidationError re-prompt with the error → repeat) work against any black-box API, need no engine support, and naturally incorporate *semantic* validators (Pydantic field checks) — but they pay 1–3 extra full-priced calls in the failure tail, add p95 latency, and still cap out below 100% success. Constrained decoding gives 100% syntactic validity at microseconds per token, but needs engine/provider support and covers syntax only. Production synthesis: use provider structured outputs or guided decoding for syntax, keep Pydantic semantic validation behind it, and reserve a single retry for semantic failures. If you self-host vLLM, there is no reason not to constrain.

**Q9: How does XGrammar make CFG masking fast enough for serving?**
Three ideas. (1) **Adaptive token mask cache**: for each automaton state, most tokens' validity is independent of the stack (context-independent) and is precomputed at grammar compile time — covering the vast majority of the vocabulary. (2) **Persistent execution stack**: the small context-dependent remainder is checked at runtime against an efficiently maintained PDA stack with rollback for speculative paths. (3) **Overlap**: mask computation runs on CPU concurrently with the GPU forward pass, so its ~tens-of-microseconds cost hides entirely. Net effect: near-zero added latency per token and up to ~100× faster mask generation than prior CFG engines, which is why vLLM, SGLang, and TensorRT-LLM adopted it as the default.

**Q10: What do provider function-calling guarantees actually rely on?**
With strict mode, the provider compiles each tool's parameter JSON schema into a grammar artifact (cached per schema) and constrains the argument generation exactly as described here — function calling *is* structured outputs over the tool-args object, plus a learned decision of which tool to call. That's why strict function calling imposes the same schema-subset limits (required-only fields, `additionalProperties: false`) and why the first call with a new tool schema can be slower. Without strict mode, you get best-effort JSON and must validate. The tool *choice* itself is unconstrained model behavior — the grammar can't save you from calling the wrong tool with valid arguments.

**Q11: Which JSON Schema features typically break or silently degrade under constrained decoding?**
Optionality (engines often require all fields, modeling optional as `union[T, null]`), `oneOf`/`anyOf` discriminated unions (limited or unsupported — strict mode historically rejected `oneOf`), `patternProperties` and open-ended `additionalProperties`, semantic `format` annotations (`date-time`, `email` — usually ignored by the grammar, so invalid dates pass), numeric `minimum`/`maximum` (hard to express as token grammars — often unenforced), and deep recursion limits (~5 levels in OpenAI's launch constraints). The senior-level point: the *silent* degradations are dangerous — output validates against the engine's subset while violating your real schema, so run full-schema validation post-hoc in CI and at runtime.

**Q12: How do constrained decoding and streaming interact?**
They compose naturally — masking happens per token, so you can stream schema-guaranteed JSON incrementally, and clients can parse with an incremental JSON parser knowing it will never go syntactically invalid. Two operational wrinkles: partial documents are still semantically incomplete (a streamed `"amount": 12` may become `129.50`), so act-on-stream logic must wait for field completion (close-quote/comma); and jump-forward emits bursts of tokens at once, so token-timing-based UX (typing effect) gets lumpy. See [streaming_at_scale.md](../case_studies/cross_cutting/streaming_at_scale.md) for transport-level handling.

**Q13: What's the failure mode of combining speculative decoding with grammar constraints?**
The draft model proposes k tokens that the target then verifies; if drafts aren't masked, the proposal distribution wastes its budget on grammar-invalid tokens (acceptance rate collapses on structural regions), and a buggy integration that accepts unmasked drafts can emit grammar-violating spans outright. Correct integrations apply the mask to the draft's sampling *and* during target verification, advancing the automaton state across the accepted span. Practical guidance: measure acceptance rate with grammar on/off — a steep drop on JSON-heavy output means the draft isn't grammar-aware, and jump-forward may recover more throughput than speculation there.

**Q14: A new schema's first request takes 8 seconds, then it's fast. What's happening and what do you do?**
That's compilation: schema → regex/DFA index (Outlines) or grammar → token mask cache (XGrammar), or the provider building and caching its server-side artifact (OpenAI documents added latency on first strict-mode use of a schema, up to ~minutes for pathological ones). Mitigations: pre-warm at deploy time by issuing a dummy request per schema; key caches by schema hash and pin them in memory across workers; keep schemas stable (every prompt-templated dynamic enum creates a new hash → cache miss); for inherently dynamic schemas, prefer a compile-free backend (llguidance) over an indexing one.

**Q15: How would you constrain a model to emit only valid SQL for your warehouse?**
Use CFG-guided decoding with an EBNF grammar of the allowed SQL dialect subset — vLLM's `guided_grammar` or llguidance with a Lark grammar — restricting to `SELECT` statements, your function whitelist, and optionally table/column names injected into the grammar as enums from the catalog. This guarantees parseability and blocks `DROP`/`DELETE` *syntactically*, which prompt instructions cannot. Then layer semantics: run `EXPLAIN` against a read replica to catch invalid references, and execution-sandbox with row limits. Caveats to mention: full SQL grammars are large (compile cost — cache it), and column-level enums explode grammar size, so many teams constrain structure but validate identifiers post-hoc.

**Q16: How do you evaluate a structured-output pipeline beyond "it parses"?**
Three layers. Parse rate (should be 100% with constraints — if you're measuring it, you're testing the engine, not the model). Schema-semantic accuracy: per-field exact match/F1 against gold labels, plus validator-level checks (dates real, enums meaningful, numbers in range) — this is where the real number lives, often 20–40 points below parse rate. Task accuracy: does the structured answer match ground truth (the `answer` field is right, not just well-typed). Plus regression dimensions specific to constraints: accuracy at field boundaries (token-healing bugs), accuracy vs field order, and a constrained-vs-unconstrained A/B on a golden set to catch distribution-distortion regressions when you change backend or schema. Wire these into eval-gated CI like any prompt change — see [llm_eval_harness_in_production.md](../case_studies/cross_cutting/llm_eval_harness_in_production.md).

---

## 13. Best Practices

1. **Constrain syntax, validate semantics** — mask for shape, then Pydantic/business-rule validation behind it; never let a parse-rate dashboard stand in for accuracy.
2. **Always describe the schema in the prompt** the mask enforces — the mask should confirm the model's intent, not fight it.
3. **Order fields reasoning-first**, or use two-pass CoT→extract for reasoning-heavy tasks.
4. **Bound everything**: `maxLength` on strings, `maxItems` on arrays, defensive `max_tokens` — assume a confused model will fill any unbounded region.
5. **Pre-warm and pin grammar caches** by schema hash at deploy; alert on first-token p99 per new hash.
6. **Validate against the full JSON Schema post-hoc in CI** — engines enforce subsets; catch silent feature drops before production does.
7. **A/B constrained vs unconstrained on a golden set** when adopting or switching backends — quality regressions hide at field boundaries.
8. **Prefer XGrammar-class backends for stable schemas, llguidance for dynamic ones**; enable jump-forward only with verified token healing.
9. **For agent tool calls, use strict mode everywhere** — malformed arguments are the most common agent loop failure, and strict mode removes the class entirely.
10. **Keep schemas stable and small** — every dynamic variation is a cache miss and a compile; 40-field mega-schemas both compile slower and reason worse than two focused calls.

---

## 14. Case Study

**Scenario**: An insurance company extracts 22 structured fields (dates, amounts, ICD codes, free-text summaries) from claim documents — 1.2M documents/month on self-hosted vLLM (Qwen2.5-32B, 4×A100). The v1 pipeline used prompt-only JSON with an Instructor-style retry loop.

**v1 pain (quantified)**: 6.8% first-pass parse failures; retries pushed mean calls/doc to 1.11 (+11% GPU cost ≈ $4,100/month) and p95 latency from 3.1s to 7.4s; 0.4% of documents failed all 3 retries and fell to a manual queue (~4,800 docs/month at ~$0.85 handling cost each).

**v2 design**:
1. Switched to vLLM guided JSON with the XGrammar backend; schema compiled and cache-pinned at pod startup (one dummy request per schema version in the readiness probe — first-request compile was 2.3s for the 22-field schema).
2. Schema rework: `extraction_notes` rationale field moved to position one (in-schema CoT); all strings bounded (`max_length` 120–2000); ICD codes as `pattern`-constrained strings; amounts as strings matching a decimal regex, parsed to `Decimal` post-hoc (avoiding unenforceable numeric ranges in the grammar).
3. Post-hoc layer kept: full-schema Pydantic validation with semantic validators (date orderings, amount sanity) — now the *only* source of retries, capped at one.
4. Eval harness: 1,500-document golden set; field-level F1 tracked per release alongside parse rate.

**Outcome**: parse failures 6.8% → 0% by construction; calls/doc 1.11 → 1.004 (semantic retries only); p95 latency 7.4s → 3.3s; manual queue from parse failures eliminated (~$4,100 + $4,080/month recovered). Field-level F1 *rose* 1.9 points — attributed in ablation to the rationale-first schema, not the mask itself. One incident worth recording: a schema update added an unbounded `notes` field; within hours a malformed scan produced a 14K-token string output and tail latency alarms — caught by the `max_tokens` defense, fixed by restoring the length bound. The lesson the team kept: the grammar is part of the prompt-engineering surface and goes through the same eval-gated review as prompt changes.

---

## Related

- [Inference & Decoding README](README.md) — sampling, KV cache, speculative decoding
- [Structured Outputs & Instructor](../agentic_frameworks/structured_outputs_and_instructor.md) — the client-library/retry side
- [vLLM Deep Dive](../vllm_deep_dive/README.md) — guided decoding backends in the serving stack
- [Function Calling & Tool Design](../agents_and_tool_use/function_calling_and_tool_design.md) — schemas as the agent/tool contract
- [LLM Eval Harness in Production](../case_studies/cross_cutting/llm_eval_harness_in_production.md) — golden-set evaluation for extraction pipelines
