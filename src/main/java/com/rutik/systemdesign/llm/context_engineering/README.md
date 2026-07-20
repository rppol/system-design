# Context Engineering

## 1. Concept Overview

Context engineering is the discipline of deciding *what information to place in the context window,
where to place it, and how much of it to include* — to maximize model performance while respecting
token budgets and latency constraints. It sits between prompt engineering (designing instructions)
and RAG (retrieving documents) and addresses the meta-level problem: given a fixed context budget,
how do you allocate it optimally across system instructions, tools, retrieved content, memory,
conversation history, and scratchpad space?

The practical need arises because modern models have context windows from 128k (GPT-4o) to 1M+
(Gemini 1.5 Pro) tokens, but larger context does not uniformly improve performance — there are
reliability, cost, and latency penalties. Context engineering produces the smallest, most
signal-dense context that answers the question.

**Cost anchor:** At GPT-4o rates ($2.50/1M input tokens), a 100k-token context at 10 queries per
second costs $2.50/second in input tokens alone — $9,000/hour. Even with prompt caching, the
uncached portion grows with every conversation turn. Context engineering is simultaneously a
quality and a cost discipline.

---

## 2. Intuition

**One-line analogy:** Context engineering is like briefing a consultant before a meeting — you
choose what documents to hand them, how much background to explain, and what to leave out, because
giving them everything wastes time and buries the key facts.

**Mental model:** Think of the context window as a finite whiteboard. Different zones serve
different purposes: the top-left corner (system prompt) is always visible and sets ground rules;
the bottom-right corner (most recent user message) gets the most attention; the middle is where
conversation history and retrieved documents fight for space. Effective context engineering is
whiteboard management: put the most important things where attention is strongest, ruthlessly evict
what does not earn its space.

**Why it matters:** The "lost in the middle" phenomenon (Liu et al., 2023) shows that information
placed in the middle of a long context is reliably less attended to than information at the start
or end. Naive RAG that dumps 20 retrieved chunks into the middle of the context loses quality even
though technically "all the information is there." Context engineering is the fix.

**Key insight:** Token position matters as much as token content. A 500-token summary placed at
the front of context often outperforms a 2,000-token verbatim document placed in the middle.

---

## 3. Core Principles

**Budget awareness.** Before constructing any context, define a token budget and allocation per
zone. Never fill the context reactively; fill it proactively within defined bounds.

**Positional primacy.** The most critical information goes at the start (system prompt) or end
(latest user message + most recent retrieved context). Deprioritize the middle for static,
low-information content.

**KV-cache alignment.** Content that is stable across many requests (system prompt, tool
definitions, few-shot examples) should appear at the front of the context and remain unchanged so
the KV cache can serve it cheaply. Changing this prefix forces a cache miss. See
[LLM Caching](../llm_caching/README.md) for provider prefix-caching mechanics.

**Signal density over completeness.** A compressed summary often outperforms the verbatim source.
LLMs extract signal well from dense summaries; they attend poorly to all 10,000 tokens of a long
document simultaneously.

**Graceful degradation.** When the context overflows, the policy for what to drop matters. Drop
from the middle, not from the start or end. Drop oldest conversation turns before dropping
retrieved context. Never drop the system prompt or the current user query.

---

## 4. Types / Strategies

**Context zone allocation framework:**

| Zone | Content | Typical Token Allocation |
|------|---------|--------------------------|
| System instructions | Role, rules, output format | 200-500 tokens |
| Tool definitions | JSON function schemas | 200-2,000 tokens (scales with tool count) |
| Few-shot examples | 2-5 input/output examples | 500-2,000 tokens |
| Retrieved documents | RAG chunks, search results | 1,000-20,000 tokens |
| Conversation history | Prior turns (compressed) | 1,000-10,000 tokens |
| Working memory / scratchpad | Intermediate agent state | 500-5,000 tokens |
| Current user message | The actual query | 50-500 tokens |

**Retrieval vs long context vs fine-tuning decision matrix** (mechanics of each side:
[RAG Fundamentals](../rag_fundamentals/README.md),
[Context Windows & Long Context](../context_windows_and_long_context/README.md)):

| Scenario | Best approach | Reason |
|----------|--------------|--------|
| Large knowledge base (>100M tokens), frequently updated | RAG | Cannot fit in context; updates need immediate availability |
| Medium corpus (<200k tokens), infrequently updated | Long context | Simpler; no retrieval errors; all context always available |
| Domain-specific style/format required | Fine-tuning | Style is not a retrieval problem; it needs to be baked in |
| Specific facts that must always be present | System prompt | Retrieval may miss; embed directly |
| Long multi-step conversation | Conversation compaction | Summarize old turns rather than truncate |

**Decoding the "<200k tokens" cutoff in that matrix.** The row boundaries look like cost thresholds.
They are not. Write both sides out and the cost comparison collapses almost immediately:

```
  long-context cost per query   = C x p
  RAG cost per query            = k x s x p + r

  break-even corpus size  C*    = (k x s x p + r) / p  =  k x s + r/p
```

**Reading it in plain English.** "Long context makes you pay for the entire corpus on every single
query; RAG makes you pay for the handful of chunks you retrieved, plus small change for the
retrieval itself."

| Symbol | Say it out loud | What it actually is |
|--------|-----------------|---------------------|
| `C` | "C", corpus size | Total tokens you would stuff into the window. The whole knowledge base |
| `p` | "p", the price | Input price per token. $2.50/1M for GPT-4o = $0.0000025 per token |
| `k` | "k", top-k | How many chunks the retriever returns. 4 in the Section 5 layout |
| `s` | "s", chunk size | Average tokens per chunk. 1,750 in the Section 5 layout |
| `r` | "r", retrieval overhead | Per-query cost of embedding the query plus the vector search. Fractions of a cent |
| `C*` | "C star" | Corpus size above which RAG is cheaper. Everything larger favours retrieval |

**Walk one example.** GPT-4o pricing, the `k = 4` and `s = 1,750` from the Section 5 budget:

```
  p = $2.50/1M       = $0.0000025 per token
  k x s = 4 x 1,750  = 7,000 tokens retrieved
  r     ~ $0.00002   (500-token query embedding at $0.02/1M, plus amortized index search)

  C* = 7,000 + 0.00002 / 0.0000025
     = 7,000 + 8
     = 7,008 tokens

  So on pure token cost, RAG wins for ANY corpus above about 7,000 tokens.
  The retrieval overhead is 8 tokens' worth -- it barely registers.

  At the matrix's own 200k boundary, per query:

      long context:  200,000 x $0.0000025  =  $0.5000
      RAG:             7,000 x $0.0000025  =  $0.0175  + $0.00002  =  $0.0175

      ratio = 0.5000 / 0.0175 = 28.6x

  At the 1M queries/day scale from Section 1:

      long context:  $500,000/day
      RAG:           $ 17,500/day        difference: $482,500/day
```

**Why the matrix still says "long context" for a 200k corpus.** Because cost is not the binding
constraint — 28.6x cheaper and the recommendation still goes the other way. What the matrix is
actually trading is *recall risk against engineering cost*. Long context has recall 1.0 by
construction: the answer is definitionally in the window. RAG multiplies two fallible terms:

```
  RAG answer accuracy         = recall@k x accuracy_given_present
                              = 0.85    x 0.92                    = 0.782

  long-context accuracy at 200k, key fact mid-window (the U-curve floor)
                              = 1.00    x 0.75                    = 0.750
```

Those land within three points of each other, so at 200k the accuracy argument is a wash and the
tiebreaker is that RAG costs you a retriever, an index, a reranker, an embedding pipeline, and a
freshness story. Below 200k, skip all of it. Above 200k, `recall@k` stays roughly flat while
long-context accuracy keeps sliding down the U-curve *and* the 28.6x cost gap widens — which is
when RAG becomes not just cheaper but more accurate, and the matrix flips.

**Compaction strategies:**

- *Sliding window* — keep only the last N turns verbatim, drop older ones entirely.
- *Hierarchical summarization* — summarize old turns progressively; oldest turns are most
  compressed.
- *Entity-centric compression* — extract entities and facts from old turns, store as a structured
  summary, discard raw text.
- *Importance scoring* — score each message for relevance to the current query; keep top-k by
  score.

---

## 5. Architecture Diagrams

```
Context Budget Allocation (32k window example)
================================================

32,000 tokens total
|
+--[0]------------------[3,000] System prompt (stable, cached)
|
+--[3,000]-------------[5,000] Tool definitions (stable, cached)
|
+--[5,000]-------------[7,000] Few-shot examples (stable, cached)
|
+--[7,000]-------------[14,000] Retrieved context (dynamic)
|                               Top-4 ranked chunks @ 1,750 tokens avg
|
+--[14,000]------------[20,000] Conversation history (compressed)
|                               Last 6 turns verbatim (3,000)
|                               Older turns as entity-centric summary (3,000)
|
+--[20,000]------------[20,500] Current user message
|
+--[20,500]------[32,000] Reserve (model output headroom + safety)
```

**Decoding those bracket boundaries.** Every number above is derived from one inequality, the one
the context assembler in Section 6 enforces before it will make the call:

```
  sum(zones) + output_reserve <= total

  where sum(zones) = system + tools + few_shot + retrieved + history + user_msg
  and    slack     = total - sum(zones) - output_reserve
```

**Reading it in plain English.** "Everything you put in, plus room for everything the model wants
to say back, has to fit in the window — and you must decide the split before you start filling, not
after you overflow."

The `output_reserve` term is what separates context engineering from just truncating. The context
window is shared between input and output; a budget that only counts input is a budget that
produces truncated answers under exactly the conditions where the answer matters most.

| Symbol | Say it out loud | What it actually is |
|--------|-----------------|---------------------|
| `total` | "total" | The model's full context window. 32,000 here; 128k on GPT-4o, 1M+ on Gemini 1.5 Pro |
| `sum(zones)` | "sum of the zones" | Everything you send. Input tokens, billed at the input rate |
| `output_reserve` | "output reserve" | Tokens held back, unspent, for the completion. Pitfall 6 is forgetting this |
| `slack` | "slack" | Unallocated headroom. Your absorber for a long user turn or a mis-estimated chunk |
| `<=` | "must be less than or equal to" | A hard wall. Cross it and the API returns a context-length error, not a degraded answer |

**Walk one example.** The exact 32k allocation drawn above, added up zone by zone against the
`ContextBudget` dataclass in Section 6:

```
  zone                        tokens    cumulative   position in window
  -------------------------   ------    ----------   ---------------------------
  system prompt                3,000         3,000   [0]      - [3,000]    stable
  tool definitions             2,000         5,000   [3,000]  - [5,000]    stable
  few-shot examples            2,000         7,000   [5,000]  - [7,000]    stable
  retrieved context            7,000        14,000   [7,000]  - [14,000]   dynamic
  conversation history         6,000        20,000   [14,000] - [20,000]   dynamic
  current user message           500        20,500   [20,000] - [20,500]   dynamic
                              ------
  sum(zones)                  20,500

  output_reserve               4,000        24,500

  slack = 32,000 - 20,500 - 4,000 = 7,500 tokens  (23.4% of the window, unspent)

  Check against Pitfall 6's "reserve 20-30%":
      reserve + slack = 4,000 + 7,500 = 11,500 = 35.9% of 32,000  -> comfortably inside

  Cacheable share:  7,000 / 20,500 = 34.1% of input tokens are the stable prefix
```

**Why the stable prefix is exactly the first 7,000 tokens.** Notice that the three stable zones are
contiguous and come first — that is not aesthetic ordering, it is the KV-cache alignment principle
from Section 3 expressed as arithmetic. Because prefix caching matches from position 0 forward, the
cacheable quantity equals the length of the *unbroken* stable run at the front. Move tool
definitions after the retrieved context and the cacheable run collapses from 7,000 to 3,000 tokens
— you lose 4,000 tokens of discount per request without changing a single word of content.

```mermaid
xychart-beta
    title "Lost in the Middle — accuracy vs position of key doc (Liu et al. 2023, 20 docs)"
    x-axis ["doc 1", "doc 5", "doc 10", "doc 15", "doc 20"]
    y-axis "retrieval accuracy (%)" 40 --> 100
    line [92, 70, 54, 69, 90]
```

The U-curve (Liu et al. 2023, 20-document retrieval setting): accuracy is ~92% when the key
fact is in the first document and ~90% in the last, but drops to ~54% in the middle — exactly
where naive RAG lands its retrieved docs (after 14k tokens of system+history), so critical
facts there go unnoticed. Fix: place the most critical retrieved chunks BEFORE conversation
history, immediately after the stable prefix.

**Decoding the U-curve into a number you can act on.** The chart shows accuracy per position; what
you actually care about is expected accuracy over your *placement policy*, which is a weighted
average across the curve:

```
  A(i)        = accuracy when the key document sits at position i
  E[A]        = sum over i of  P(key doc lands at i) x A(i)
  lift        = E[A | ranked placement] - E[A | random placement]
```

**Reading it in plain English.** "If you do not control where the answer lands, you get the average
of the whole curve; if your reranker puts it first, you get the peak of the curve — and the gap
between those two is free accuracy."

This reframes reranking. A reranker is usually sold as "finds better documents." Its larger effect
in a long context is positional: it decides *where in the U-curve* the right document sits.

| Symbol | Say it out loud | What it actually is |
|--------|-----------------|---------------------|
| `i` | "i", the position | Which slot in the retrieved list the key document occupies. 1 = first, 20 = last |
| `A(i)` | "A of i" | Measured accuracy at that slot. 92% at i=1, 54% at i=10, 90% at i=20 |
| `P(...)` | "probability that" | Your placement policy. Random ordering = 1/20 everywhere; a good reranker concentrates it at i=1 |
| `E[A]` | "expected A" | Accuracy averaged over where the doc actually lands. The number your eval harness reports |
| `lift` | "lift" | Accuracy gained purely by reordering. No new documents, no bigger model, no extra tokens |

**Walk one example.** 20 retrieved documents, one of which contains the answer, using the five
sampled points from the chart above as the curve:

```
  random placement -- the key doc is equally likely to be anywhere:

      position       i=1     i=5     i=10    i=15    i=20
      A(i)            92%     70%     54%     69%     90%
      weight         0.20    0.20    0.20    0.20    0.20
      contribution   18.4    14.0    10.8    13.8    18.0

      E[A] = 18.4 + 14.0 + 10.8 + 13.8 + 18.0 = 75.0%

  ranked placement -- reranker puts the key doc first 80% of the time,
  and it lands mid-list the other 20%:

      contribution   0.80 x 92%  = 73.6
                     0.20 x 54%  = 10.8

      E[A] = 73.6 + 10.8 = 84.4%

  lift = 84.4 - 75.0 = 9.4 accuracy points, for zero extra tokens

  Worst case -- Pitfall 1, chunks dumped after 5,000 tokens of history so the key
  doc reliably lands mid-context:

      E[A] = 54.0%    -> 30.4 points BELOW ranked placement
```

**Why the curve is a U and not a slope.** Two separate mechanisms pin up the ends. The start is
privileged by attention sinks and the recency of the instruction framing; the end is privileged
because it is nearest the generation point. The middle has neither, so it decays to roughly the
54% floor. This is why "just put it at the end" and "just put it at the start" are both defensible
and "put it wherever the retriever emitted it" is not — the only genuinely bad position is the one
naive RAG picks by default.

```
Context Engineering Pipeline (per request)
============================================

 Incoming request
       |
       v
 Budget planner
 +-------------------------------------------+
 | total_budget = 32,000                     |
 | system_zone = 3,000 (fixed)               |
 | tools_zone = 2,000 (fixed, RAG-retrieved) |
 | few_shot_zone = 2,000 (fixed)             |
 | retrieval_zone = 7,000 (dynamic)          |
 | history_zone = 6,000 (with compaction)    |
 | user_msg_zone = 500 (current turn)        |
 | output_reserve = 4,000+                   |
 +-------------------------------------------+
       |
       v
 Tool retrieval (RAG over tool definitions)
 (only include tools relevant to this query)
       |
       v
 History compactor
 (summarize if history > 6,000 tokens)
       |
       v
 Document retrieval + reranker
 (fetch top-k, trim to retrieval_zone)
       |
       v
 Context assembler
 Order: system -> few-shot -> retrieved -> history -> user
       |
       v
 Token counter — abort if over budget
       |
       v
 LLM call
```

---

## 6. How It Works — Detailed Mechanics

### Token budget enforcement

```python
import tiktoken
from dataclasses import dataclass

ENCODER = tiktoken.encoding_for_model("gpt-4o")

@dataclass
class ContextBudget:
    total: int = 32_000
    system: int = 3_000
    tools: int = 2_000
    few_shot: int = 2_000
    history: int = 6_000
    retrieved: int = 7_000
    user_msg: int = 500
    output_reserve: int = 4_000

def count_tokens(text: str) -> int:
    return len(ENCODER.encode(text))

def build_context(
    system: str,
    few_shot: list[dict],
    history: list[dict],
    retrieved_chunks: list[str],
    user_message: str,
    budget: ContextBudget = ContextBudget(),
) -> list[dict]:
    messages: list[dict] = []

    # System prompt — always first (KV-cache stable prefix)
    sys_tokens = count_tokens(system)
    if sys_tokens > budget.system:
        raise ValueError(f"System prompt {sys_tokens}t exceeds budget {budget.system}t")
    messages.append({"role": "system", "content": system})

    # Few-shot examples (stable, after system — cache-friendly)
    few_shot_used = 0
    for ex in few_shot:
        tokens = count_tokens(ex["input"]) + count_tokens(ex["output"])
        if few_shot_used + tokens > budget.few_shot:
            break
        messages.extend([
            {"role": "user",      "content": ex["input"]},
            {"role": "assistant", "content": ex["output"]},
        ])
        few_shot_used += tokens

    # Retrieved context — BEFORE history to avoid "lost in the middle"
    retrieved_used, retrieved_text = 0, ""
    for chunk in retrieved_chunks:  # pre-sorted by relevance score desc
        t = count_tokens(chunk)
        if retrieved_used + t > budget.retrieved:
            break
        retrieved_text += chunk + "\n\n"
        retrieved_used += t
    if retrieved_text:
        messages.append({
            "role": "system",
            "content": f"Relevant context:\n\n{retrieved_text.strip()}"
        })

    # Conversation history — compressed sliding window
    history_used, trimmed = 0, []
    for turn in reversed(history):  # newest first
        t = count_tokens(turn["content"])
        if history_used + t > budget.history:
            break
        trimmed.insert(0, turn)
        history_used += t
    messages.extend(trimmed)

    # Current user message — always last
    messages.append({"role": "user", "content": user_message})

    total = sum(count_tokens(m["content"]) for m in messages)
    if total + budget.output_reserve > budget.total:
        raise ValueError(f"Context {total}t + output reserve {budget.output_reserve}t > budget {budget.total}t")

    return messages
```

### KV-cache-aware ordering (broken vs fixed)

```python
# BROKEN: dynamic content in the stable prefix breaks cache every request
def broken_build(user_id: str, system_base: str, user_msg: str) -> list[dict]:
    return [
        # user_id in system prompt -> unique prefix per user -> cache miss every time
        {"role": "system", "content": f"{system_base}\n\nUser ID: {user_id}"},
        {"role": "user",   "content": user_msg},
    ]

# FIX: stable prefix first; dynamic user-specific data injected in the user turn
def fixed_build(user_id: str, system_base: str, user_msg: str) -> list[dict]:
    return [
        # Stable across all users — cached by provider
        {"role": "system", "content": system_base},
        # Dynamic — after the cached prefix; does not invalidate the cache
        {"role": "user",   "content": f"[user_id={user_id}]\n{user_msg}"},
    ]
```

### Entity-centric history compaction

```python
from openai import OpenAI

client = OpenAI()

def compact_history(turns: list[dict], max_tokens: int = 500) -> str:
    """Summarize old turns into a structured entity/fact summary."""
    conversation_text = "\n".join(
        f"{t['role'].upper()}: {t['content']}" for t in turns
    )
    summary = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=[{
            "role": "user",
            "content": (
                "Extract key entities, decisions, and constraints from this "
                "conversation. Output as a concise bullet list. Be specific — "
                "preserve names, numbers, and agreed constraints.\n\n"
                f"{conversation_text}"
            )
        }],
        max_tokens=max_tokens,
    ).choices[0].message.content
    return f"[Prior conversation summary]\n{summary}"
```

**Decoding `max_tokens = 500` and the compression it implies.** Compaction is usually described
qualitatively ("summarize old turns"). The quantity that matters is the ratio, and whether the
summarizer call pays for itself:

```
  compression ratio     R  = tokens_before / tokens_after
  tokens saved per call    = tokens_before - tokens_after
  summarizer cost          = tokens_before x p_small        (one time)
  saving per later request = tokens_saved  x p_main
  break-even requests  N*  = summarizer_cost / saving_per_request, rounded UP
```

**Reading it in plain English.** "Pay a cheap model once to shrink the history, then collect the
savings on every expensive call afterwards — and the summary only has to survive a couple of turns
to be worth it."

| Symbol | Say it out loud | What it actually is |
|--------|-----------------|---------------------|
| `R` | "R", the compression ratio | How many times smaller the history got. `5x` means 5,000 tokens became 1,000 |
| `p_small` | "p small" | Price of the summarizer. gpt-4o-mini at $0.15/1M — 17x cheaper than the main model |
| `p_main` | "p main" | Price of the model doing the real work. GPT-4o at $2.50/1M |
| `N*` | "N star" | Turns the compacted history must survive before the summarizer call is repaid |
| one time | "amortized" | The summary is computed once and reused on every subsequent turn — that is the whole economics |

**Walk one example.** A 40-turn support session at 500 tokens per turn, compacted to fit the
6,000-token history zone from the Section 5 budget:

```
  raw history        40 turns x 500 =  20,000 tokens
  keep verbatim       6 turns x 500 =   3,000 tokens   (recent, uncompressed)
  compact the rest   34 turns x 500 =  17,000 tokens  ->  3,000 tokens

  R on the compacted portion = 17,000 / 3,000  = 5.67x
  R overall                  = 20,000 / 6,000  = 3.33x

  tokens saved per request   = 20,000 - 6,000  = 14,000

  summarizer cost   = 17,000 x $0.15/1M   = $0.00255   (once)
  saving/request    = 14,000 x $2.50/1M   = $0.03500   (every subsequent turn)

  N* = 0.00255 / 0.03500 = 0.073  ->  ceil  ->  1 request

  The compaction pays for itself on the very next turn, then returns
  $0.035 per turn for the rest of the session.
```

**Why R has a ceiling, and what breaks when you exceed it.** Nothing in the arithmetic stops you
from compressing 17,000 tokens to 300 (`R = 57x`) — the cost model says that is strictly better.
Pitfall 4 is what stops you: past roughly 5-8x, entity-centric summaries start dropping the
specifics (names, agreed numbers, earlier decisions) that make the summary useful at all, and the
model begins contradicting commitments it made twenty turns ago. The `R = 5.67x` above sits
deliberately at the top of the safe band. Treat R as a quality budget you spend down, not a cost
knob you turn up: the marginal saving from 5x to 10x is $0.0175 per turn, which is nowhere near
worth an agent that forgets the customer's name.

---

## 7. Real-World Examples

**Cursor (AI code editor)** uses a layered context strategy: editor configuration and language
server output are at the front (cached); recent file edits are retrieved by recency and relevance;
the cursor position (current user query) is always last. Approximately 70% of input tokens are
served from the KV cache even for novel queries.

**Perplexity AI** compresses conversation history aggressively after 4 turns: older turns are
summarized to a 2-sentence entity-and-intent summary. This keeps context under 8k tokens for most
queries while preserving the key facts from earlier in the session.

**Anthropic extended thinking** uses a designated scratchpad zone at the end of the context that
is allocated specifically for chain-of-thought. The budget for this zone is separate from the
user-visible response budget, ensuring reasoning tokens do not compete with context tokens.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key Factor |
|----------|----------|----------|-----------|
| Retrieved context size | More chunks (higher recall) | Fewer chunks (less noise) | "Lost in middle" risk; reranking quality |
| History strategy | Full verbatim (faithful) | Compressed summary (cost-efficient) | Turn count; session length |
| Critical info position | Start/end (attended) | Middle (ignored) | Attention distribution |
| RAG vs long context | RAG (dynamic, cost-efficient) | Long context (simple, no retrieval errors) | Corpus size; update frequency; latency |
| Fine-tune vs context | Fine-tune (style baked in) | Context injection (flexible) | Style stability; data volume |
| KV-cache prefix | Stable (maximizes cache hits) | Dynamic (maximizes freshness) | Cache hit rate vs personalization |

---

## 9. When to Use / When NOT to Use

**Apply context engineering when:**
- Agent produces inconsistent results despite correct retrieval — the issue is likely positional.
- Latency or cost are too high and context windows are large (consistently >16k tokens).
- Conversation history grows unbounded and causes context overflow in production.
- Multiple information sources (tools, RAG, memory, history) compete for the same token budget.
- Model frequently ignores critical instructions — they may be buried in the middle.

**Simplify or skip when:**
- Context is always small (<4k tokens) and fits easily.
- Single-turn QA with no history, minimal retrieval.
- Fine-tuning is the right solution (style/format, not knowledge recall).
- Still in early experimentation — establish correctness before optimizing context layout.

---

## 10. Common Pitfalls

**Pitfall 1 — Naive RAG dumps all retrieved chunks in the middle.** Ten 1,000-token chunks placed
after 5,000 tokens of conversation history means the most relevant content lands at position 15,000
in the context. "Lost in the middle" guarantees the model under-uses them. Fix: place top-ranked
retrieved chunks at the start of the dynamic portion of context, just after the stable prefix.

**Pitfall 2 — Dynamic content in the stable prefix breaks KV cache.** Any per-request variation
(user ID, timestamp) embedded in the system prompt creates a unique prefix every request, defeating
prefix caching and adding full input cost. Fix: separate stable system instructions from dynamic
user-context; inject dynamic data in a user turn, after the stable prefix.

**Pitfall 3 — No budget enforcement.** Context grows with conversation length until the model
throws a context-length error in production. Fix: define explicit per-zone budgets and enforce them
in the context assembler; truncate predictably rather than crashing.

**Pitfall 4 — Over-compressing history.** Summarizing too aggressively loses facts the model needs
for consistency (user's name, agreed-upon constraints, earlier decisions). Fix: use entity-centric
compression — extract and preserve key entities and facts in a structured summary rather than a
free-form summary that may drop specifics.

**Pitfall 5 — Tool definition bloat.** Including 50 tool schemas in every request costs 5,000+
tokens even when 45 of those tools will never be called. Fix: use tool selection at scale — retrieve
the relevant tool definitions as a RAG lookup before constructing the context.

**Pitfall 6 — No output reserve.** Filling the context to the maximum input limit leaves no room
for the model's output, causing truncated responses. Always reserve 20-30% of the context window
for output, especially for tasks that generate long structured responses.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| tiktoken / tokenizers | Token counting for budget enforcement |
| LangGraph | Stateful context management for multi-turn agents |
| LangChain ConversationSummaryMemory | Rolling conversation compaction |
| LLMLingua / LLMLingua-2 | Neural prompt compression for long retrieved docs |
| instructor / guidance | Structured output to reduce verbose output tokens |
| vLLM / SGLang | KV-prefix caching for stable context prefixes |
| Anthropic prompt caching | Provider-level prefix caching (cache_control: ephemeral) |
| ContextCite | Attribution — which context tokens influenced the output |
| RAGAS | Evaluate faithfulness — how well the model used retrieved context |

---

## 12. Interview Questions with Answers

**Q: What is context engineering and how does it differ from prompt engineering?**
Prompt engineering designs the instructions and format for a single prompt. Context engineering is
the broader discipline of deciding what information to include in the context window, how much of
each type, and in what order — across system prompt, tools, memory, retrieved documents, history,
and current message. Prompt engineering is about what to say; context engineering is about what to
include and where to put it.

**Q: What is the "lost in the middle" problem and how do you address it?**
Liu et al. (2023) showed that LLMs reliably attend to information at the start and end of the
context but under-attend to information in the middle. Content at position 10k in a 20k-token
context is much less likely to influence the output than the same content placed at position 500.
The fix is positional placement: put the most critical retrieved chunks and instructions at the top
(after the system prompt) and the current user query at the end. Avoid sandwiching critical
information between long conversation history and verbose tool outputs.

**Q: Does a 1M-token context window make RAG and context engineering obsolete?**
No — a large window changes the tradeoff but does not remove it. Three costs remain: money
(filling 1M tokens per request costs orders of magnitude more than retrieving a targeted 5k-token
subset), latency (prefill time grows roughly linearly with input length, so a 500k-token prompt
adds tens of seconds before the first output token), and quality ("lost in the middle" degradation
persists at long lengths, and needle-in-a-haystack scores overstate real multi-fact reasoning
performance). Treat a long context as a larger budget to allocate, not a license to stop
allocating.

**Q: Why can adding more retrieved chunks make answers worse, not better?**
Because every extra chunk adds distractors that compete for attention with the relevant one. Going
from top-4 to top-20 chunks raises recall slightly but pushes the best chunks deeper toward the
middle of the context and increases the chance the model quotes a near-miss passage — retrieval
noise compounds with the positional attention dip. The production pattern is retrieve wide, then
rerank and keep a small k (3-8 chunks): reranking buys the recall without paying the context-noise
tax. When answers start citing the wrong document, reduce k before touching the prompt.

**Q: How do you decide between RAG, long context, and fine-tuning for a knowledge-intensive task?**
RAG is the default for large, frequently updated knowledge bases (>1M tokens) that cannot fit in
context. Long context is better when the corpus is small (<200k tokens), update frequency is low,
and retrieval errors are costly — document review, contract analysis, codebase chat. Fine-tuning
addresses style, format, and domain-specific vocabulary rather than knowledge recall; if you need
the model to know a specific fact reliably, RAG or long context is more reliable. Cost is also a
factor: long context at 100k tokens per request is expensive at scale; RAG retrieves a targeted
subset.

**Q: How do you design a context budget for an agent with tools, memory, and RAG?**
Define a total budget (e.g., 32k tokens) and allocate hard limits per zone: system ~10%, tools ~8%,
retrieved ~25%, history ~20%, current message ~2%, output reserve ~15%. Enforce these limits in the
context assembler before the LLM call. The key policy decision is drop priority: when a zone is
over budget, drop retrieved chunks from the bottom of the ranked list first (least relevant), then
compress old history turns. Never drop the system prompt or the current user query.

**Q: Why does KV cache matter for context engineering, and how do you design for it?**
KV cache stores the key/value attention tensors for the prefix of a context; if the same prefix
appears in a later request, the model skips re-computing those layers, reducing latency and cost by
50-90% for that prefix. To maximize hit rate: keep stable content (system prompt, tool definitions,
few-shot examples) at the front of every request unchanged. Any dynamic content goes after the
stable prefix so it does not invalidate the cached portion. Anthropic cache_control and vLLM
automatic prefix caching both work on this principle.

**Q: How does provider prompt caching pricing change how you lay out context?**
It makes the stable prefix literally cheaper, not just faster. Anthropic prompt caching charges
roughly 25% extra to write a cache segment and about 90% less to read it (5-minute default TTL),
and OpenAI applies an automatic ~50% discount to cached prefixes of 1,024+ tokens — so a
5,000-token system-plus-tools prefix reused across requests costs a fraction of its nominal price.
This flips the economics of few-shot examples: a large stable example block is nearly free after
the first request, while the same tokens placed after dynamic content are billed in full every
time. Design rule: order zones by volatility — least-changing first — and never interleave
per-request data into the cached prefix.

**Q: How do you engineer context for sub-agent architectures?**
Give each sub-agent a fresh, minimal window and pass results back as compact summaries — context
isolation is the point of delegating to sub-agents. The orchestrator's context holds the plan and
each sub-agent's summarized findings (typically 200-500 tokens each), not raw transcripts: a
sub-agent that read 50k tokens of documents returns a 300-token digest, keeping the orchestrator's
window flat as the task grows. The failure mode is "context re-centralization" — forwarding full
sub-agent transcripts upward recreates the overflow you delegated to avoid. Define an explicit
return-format contract (findings, citations, confidence) for every sub-agent.

**Q: What is context compaction and when should you apply it?**
Compaction is reducing the token count of conversation history or retrieved context through
summarization, entity extraction, or selective truncation. Apply it when conversation history
exceeds the history budget (typically after 10-15 turns) or when a retrieved document is longer
than its allocated zone. The compaction strategy matters: naive truncation loses continuity;
hierarchical summarization preserves key facts; entity-centric compression (extract names,
decisions, constraints) is the most faithful for long-term consistency.

**Q: How do tool definitions affect context budget and what do you do with 50+ tools?**
Each JSON tool definition costs 100-300 tokens. At 50 tools, that is 5,000-15,000 tokens before
the user query is even processed — in a 32k context, 15-45% of the budget. The solution is tool
retrieval: embed all tool descriptions, then at query time retrieve the top-k most relevant tools
(k = 5-10) and include only those in the context. This "RAG for tools" adds 10-50ms latency but
saves thousands of tokens per request, improving both cost and model focus.

**Q: What is the difference between conversation compaction and a simple sliding window?**
A sliding window keeps the last N turns verbatim and drops older turns entirely. This is simple
but loses facts from early in the conversation (user's stated goal, agreed constraints, established
context). Compaction preserves the semantic content of dropped turns by summarizing them before
discarding. The most important case is multi-turn agents: if turn 3 established "the user wants a
Python solution" and turns 4-25 are problem-solving, a sliding window that drops turn 3 causes the
agent to forget the constraint by turn 26.

**Q: How do you test and measure context engineering decisions?**
Measure retrieval faithfulness (RAGAS faithfulness score), answer relevancy, and position-ablation:
run the same query with critical information at the start vs. the middle vs. the end of context and
compare output quality. For agents, measure task completion rate against context budget (does
success rate hold as history grows?). Track token costs per request in production; a spike in
input tokens often signals context budget enforcement failures.

**Q: What is LLMLingua and when is neural prompt compression worth the overhead?**
LLMLingua uses a small language model to identify and remove low-perplexity (redundant) tokens
from retrieved documents while preserving high-information tokens. It achieves 3-20x compression
with minimal quality loss. It is useful when retrieved documents are long and verbatim inclusion
exceeds the retrieval budget (legal documents, academic papers). The tradeoff is 50-200ms
compression latency and a small quality drop on edge cases where compressed sentences lose
connective tissue. For short, precise chunks (<1,000 tokens), chunking at retrieval time is faster;
LLMLingua shines on long verbatim documents.

**Q: Does structural formatting (XML tags, markdown headers) actually change how the model uses context?**
Yes — clear delimiters help the model locate and attribute sections, which matters most in crowded
contexts. Wrapping retrieved documents in tags like `<doc id="3">...</doc>` improves the model's
ability to cite the right source and reduces bleed-over between adjacent chunks; Anthropic
explicitly recommends XML tags for section boundaries, and structured markdown headers serve the
same role for GPT-family models. Formatting costs tens of tokens, trivial relative to its effect
on faithfulness in multi-document prompts. Standardize one delimiter scheme per application and
keep it byte-identical across requests so it lives inside the cached prefix.

**Q: What is context rot and how do you mitigate it in long agent sessions?**
Context rot is the gradual quality decline in long-running sessions as the window fills with stale
tool outputs, dead-end reasoning, and superseded facts — the model keeps attending to obsolete
content even well below the hard token limit. Symptoms: the agent retries abandoned approaches,
cites outdated intermediate values, or contradicts recent corrections. Mitigations: periodic
compaction that rewrites the session into a clean state summary (decisions made, current plan,
open items) and drops raw history; truncating verbose tool outputs at write time; and hard
eviction of superseded results rather than appending corrections after them. Schedule compaction
proactively — every 20-30 turns or at 60-70% window fill — instead of waiting for overflow.

---

## 13. Best Practices

- Define explicit per-zone token budgets before writing any context assembly code; enforce them
  with a token counter in the assembler.
- Place stable content (system prompt, tool definitions, few-shot examples) at the front of every
  request unchanged — this is the KV-cache-friendly prefix.
- Place the most critical retrieved chunks immediately after the stable prefix, not after
  conversation history.
- Always reserve 20-30% of the context window for model output; fill-to-limit causes truncated
  responses.
- Use entity-centric compaction for long conversations; sliding-window truncation loses early
  session facts.
- Profile context composition in production: track per-zone token usage and KV-cache hit rates on
  every request.
- Use tool retrieval (RAG-over-tools) once you have more than 10-15 tools.
- Test context layout changes with position-ablation experiments before deploying to production.

---

## 14. Case Study

**Problem Statement**

An enterprise legal research agent achieves 92% task completion on single-document questions but
drops to 61% on multi-document questions requiring synthesis of information from 5+ retrieved
documents. Context window is 128k tokens (GPT-4o); total context used is 90k tokens. Analysis
reveals all 5 documents are placed after 40k tokens of system prompt + conversation history.

**Architecture Overview**

```
Before (broken context layout):
=================================
[0 – 3,000]     System prompt
[3,000 – 18,000]  Tool definitions (15 schemas, all included)
[18,000 – 40,000]  Conversation history (22 turns verbatim)
[40,000 – 90,000]  5 retrieved legal documents (10k tokens each)
[90,000 – 90,200]  Current question
                     ^ Documents at position 40k-90k: "lost in the middle"

After (context engineering):
==============================
[0 – 3,000]     System prompt
[3,000 – 6,000]   Top-3 tool definitions (RAG-over-tools, 3 of 15)
[6,000 – 31,000]  Top-5 retrieved documents (placed BEFORE history)
[31,000 – 37,000]  Compressed history (22 turns -> entity-centric summary)
[37,000 – 37,200]  Current question
Output reserve: 90,800 tokens
```

**Key Design Decisions**

Tool retrieval: cuts tool zone from 15,000 to 3,000 tokens. Retrieved documents moved before
history: position shifts from 40k-90k to 6k-31k (peak attention zone). History compressed from
22k to 6k tokens via entity-centric summarization (names of documents reviewed, agreed legal
theories, prior questions answered).

**Tradeoffs and Alternatives**

Considered always putting all 15 tool schemas at the front but the 12,000 extra tokens would push
documents further into the middle. Considered removing conversation history entirely but the agent
needed continuity for long research sessions spanning 20+ turns.

**Interview Discussion Points**

- Total context dropped from 90k to 37k tokens: what other benefits does this produce? (Lower
  latency, lower cost, larger output reserve for longer reasoning.)
- How do you handle a legal document longer than the 25k-token retrieval budget? (Chunk it; use
  LLMLingua compression; retrieve targeted sections via a secondary retrieval step.)
- Task completion on multi-document questions: 61% → 89%. Why not 100%? (Some questions require
  information genuinely not in any retrieved document; that is a retrieval problem, not a context
  engineering problem.)
