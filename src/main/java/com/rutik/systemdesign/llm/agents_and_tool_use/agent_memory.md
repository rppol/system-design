# Agent Memory

## Concept Overview

Agent memory encompasses all mechanisms by which an LLM agent stores and retrieves information across the span of a task and across sessions. Without memory, every agent invocation starts blank — no knowledge of past user preferences, prior task outcomes, or accumulated domain knowledge. Memory is what makes an agent feel coherent and capable over time rather than amnesiac.

Memory for LLM agents has four distinct types, each with different storage mechanisms, retrieval characteristics, and use cases. Managing memory efficiently — knowing what to store, how to retrieve it, and when to evict it — is one of the primary engineering challenges in production agentic systems.

---

## Intuition

> **One-line analogy**: Agent memory is like a researcher's notebook system — sticky notes on the desk for the current task (working memory), a notebook for recent meetings (episodic), a reference library for domain facts (semantic), and personal shorthand for well-practiced procedures (procedural).

**Mental model**: The context window is a desk with limited space. Long conversations and accumulated tool results fill it up. Evicting older content saves space but loses information. MemGPT solves this like an OS: the context window is RAM; external databases are disk; the agent itself manages paging — deciding what to move to disk (store externally) and what to load from disk (retrieve). This OS metaphor clarifies why memory management for agents is structurally similar to memory management in operating systems.

**Why it matters**: A 128K context window seems large until you account for a system prompt (2K), conversation history (50K), and multiple tool results (10K each). After ~10 tool calls in a long session, the context is full. Without memory management, either the agent crashes (context overflow) or loses critical earlier information (truncation).

**Key insight**: The context window is the agent's working memory, and it is finite. All non-trivial agents need some mechanism to handle context overflow — whether compression, summarization, or retrieval-augmented memory injection.

---

## Core Principles

- **Four memory types**: working (in-context), episodic (event records), semantic (factual knowledge), procedural (skill templates). Each has different access patterns and lifetimes.
- **Memory is not infinite context**: even with 1M-token context windows, filling context with everything is wasteful and degrading to reasoning quality.
- **Retrieval over full injection**: for long-term memory, retrieve only what's relevant to the current step — don't inject the entire memory into context.
- **Eviction must be principled**: FIFO eviction loses important information randomly; importance-based or recency-weighted eviction is better.
- **Token budgets drive architecture decisions**: every memory retrieval and injection has a token cost; model this explicitly.

---

## How It Works — Detailed Mechanics

### The Four Memory Types

```
1. WORKING MEMORY (In-Context)
   Storage: current context window
   Capacity: 8K-2M tokens (model-dependent)
   Latency: 0ms (already in context)
   Persistence: session only (cleared when context resets)
   Contents: system prompt, conversation history, tool call/results, scratch notes
   Use: everything the agent is currently reasoning about

2. EPISODIC MEMORY (Event Store)
   Storage: external database (vector DB, key-value store, relational DB)
   Capacity: unlimited
   Latency: 10-100ms retrieval
   Persistence: permanent across sessions
   Contents: past conversations, task outcomes, user interactions, observations
   Example: "On 2025-05-01, user asked about Python asyncio and preferred
             simple examples over complex ones"
   Retrieval: semantic similarity to current query

3. SEMANTIC MEMORY (Knowledge Store)
   Storage: vector DB + optional knowledge graph
   Capacity: unlimited
   Latency: 20-200ms retrieval
   Persistence: permanent; updated when new facts are learned
   Contents: facts about the world, domain knowledge, user profile
   Example: "User works at Anthropic, prefers TypeScript, has senior-level experience"
   Retrieval: semantic similarity or graph traversal

4. PROCEDURAL MEMORY (Skill Store)
   Storage: vector DB or code/template library
   Capacity: unlimited
   Latency: 20-100ms retrieval
   Persistence: permanent; grows with experience
   Contents: successful tool call patterns, solution templates, code snippets
   Example: "When debugging Python import errors, the sequence is:
             1) check __init__.py, 2) verify sys.path, 3) check venv activation"
   Retrieval: semantic similarity to current task type
```

### MemGPT Architecture

MemGPT (Packer et al., 2023) treats the LLM context window like OS RAM with virtual memory:

```
MemGPT OS-Style Memory Layout:

                     Context Window (128K tokens = "RAM")
┌─────────────────────────────────────────────────────────────────────┐
│ CORE MEMORY (always in context)                                     │
│   Persona: "You are a helpful assistant named Lena..."              │
│   Human profile: "User is Alice, software engineer, prefers Python" │
│   System state: current task, goals, constraints                    │
├─────────────────────────────────────────────────────────────────────┤
│ RECALL STORAGE (recent messages — FIFO, most recent N messages)     │
│   [conv_turn_89] [conv_turn_90] ... [conv_turn_100]                 │
├─────────────────────────────────────────────────────────────────────┤
│ ARCHIVAL STORAGE SNIPPETS (retrieved on demand)                     │
│   [retrieved_memory_1] [retrieved_memory_2]                         │
└─────────────────────────────────────────────────────────────────────┘
              ↑ retrieve                      ↓ archive
┌─────────────────────────────────────────────────────────────────────┐
│ ARCHIVAL STORAGE ("Disk" — external vector DB)                      │
│   All past conversations, compressed summaries, learned facts        │
│   Thousands of entries; not in context unless retrieved             │
└─────────────────────────────────────────────────────────────────────┘

MemGPT's key innovation: the model itself decides when to archive
(move from context to external storage) and when to retrieve
(load from external storage into context). These are tool calls:

memory_append(content) → writes to archival storage
memory_search(query)   → retrieves relevant archival content into context
```

### Context Compression Strategies

```python
# Strategy 1: Summarize-and-Replace
# When context exceeds threshold, summarize oldest N messages

def compress_context(messages: list[dict], threshold_tokens: int = 100000) -> list[dict]:
    if count_tokens(messages) < threshold_tokens:
        return messages

    # Keep first message (system prompt) and last K messages
    system = messages[0]
    recent = messages[-10:]          # always keep 10 most recent
    middle = messages[1:-10]         # compress the middle

    summary = llm.invoke([
        SystemMessage("Summarize the following conversation turns concisely, "
                      "preserving all decisions, facts, and key information:"),
        HumanMessage(format_messages(middle))
    ]).content

    compressed = [
        system,
        {"role": "system", "content": f"[CONVERSATION SUMMARY]: {summary}"},
        *recent
    ]
    print(f"Compressed {count_tokens(middle)} tokens → {count_tokens([summary])} tokens")
    return compressed

# Strategy 2: Sliding Window
# Simple: keep only the last N tokens of conversation

def sliding_window(messages: list[dict], max_tokens: int = 80000) -> list[dict]:
    result = [messages[0]]  # always keep system prompt
    budget = max_tokens - count_tokens([messages[0]])

    # Add messages from most recent, working backwards
    for msg in reversed(messages[1:]):
        msg_tokens = count_tokens([msg])
        if budget - msg_tokens > 0:
            result.insert(1, msg)
            budget -= msg_tokens
        else:
            break

    return result

# Strategy 3: Hierarchical Summary
# Maintain multi-level summaries: session summary → task summary → step summary

class HierarchicalMemory:
    def __init__(self):
        self.step_summaries = []      # fine-grained: 1 per step
        self.task_summary = ""        # medium: 1 per major task phase
        self.session_summary = ""     # coarse: entire session

    def add_step(self, step_content: str):
        summary = self.llm.summarize(step_content, max_words=30)
        self.step_summaries.append(summary)

        # Every 10 steps: condense into task summary
        if len(self.step_summaries) % 10 == 0:
            self.task_summary = self.llm.summarize(
                "\n".join(self.step_summaries[-10:]), max_words=100
            )

    def get_context_injection(self) -> str:
        """Return compact memory representation for injection into context."""
        return f"Session context: {self.session_summary}\n" \
               f"Recent task: {self.task_summary}\n" \
               f"Last 3 steps: {'; '.join(self.step_summaries[-3:])}"
```

**In plain terms.** "When the transcript gets too big, pin both ends and replace the boring middle with a paragraph."

Every compression strategy above is the same trade in different clothing: buy context-window space with fidelity. What separates them is *which* tokens you agree to lose and how much you pay to lose them gracefully.

| Symbol | What it is |
|--------|------------|
| `threshold_tokens` | The trigger line, `100000`. Below it, do nothing at all — compression is not free |
| `messages[0]` | The system prompt. Pinned verbatim, always. Losing it means losing the task itself |
| `messages[-10:]` | The recent tail, pinned verbatim. The model needs exact wording for what just happened |
| `messages[1:-10]` | The middle. The only region eligible for summarization |
| compression ratio | Middle tokens divided by summary tokens. "How many tokens did one summary token replace" |

**Walk one example.** Using this module's own sizing — a system prompt of about 1K tokens and about 2K tokens per conversation turn:

```
  context at the moment compression fires       100,000 tokens  (the threshold)
    system prompt   (pinned, verbatim)            1,000
    recent 10 turns (pinned, verbatim)           20,000         10 turns x 2K
    middle          (eligible for summary)       79,000         about 39 turns

  summarize the middle: 79,000 -> 1,000 tokens
    compression ratio = 79,000 / 1,000  =  79 : 1
    tokens dropped    = 79,000 - 1,000  =  78,000   (98.7% of the middle)

  new context = 1,000 + 1,000 + 20,000  =  22,000 tokens
  input cost  =  22,000 x $0.000005     =  $0.11 per call
  was         = 100,000 x $0.000005     =  $0.50 per call     4.5x cheaper
```

**Why the "keep last 10" term exists.** Drop it and you summarize everything, including the turn the model is mid-way through answering. Summaries are lossy in exactly the way that hurts most locally: they preserve "the user asked about auth" but discard the variable name, the error string, the file path. Pinning a verbatim tail costs 20,000 tokens and buys back the precision that the 79:1 ratio just destroyed. The sliding-window strategy is what you get when you keep only that tail and skip the summary entirely — zero latency, zero cost, and no memory of the first 39 turns at all.

### Token Budget Management

```python
# Concrete token cost model (GPT-4o as of 2025)
# Input: $5.00/1M tokens   Output: $15.00/1M tokens

MODEL_INPUT_PRICE_PER_TOKEN = 5.00 / 1_000_000    # $0.000005/token
MODEL_OUTPUT_PRICE_PER_TOKEN = 15.00 / 1_000_000  # $0.000015/token

# Context window: 128,000 tokens
# Full context fill cost: 128K × $0.000005 = $0.64 per call (input only)
# 10 calls with full context: $6.40

# Memory-efficient agent loop with budget tracking
class BudgetedAgentMemory:
    def __init__(self, token_budget: int = 50000):
        self.token_budget = token_budget          # per-step allocation
        self.total_tokens_used = 0
        self.cost_usd = 0.0

    def build_context(self, task: str, recent_history: list,
                      retrieved_memories: list) -> list[dict]:
        """Build context within token budget."""
        context = []
        budget_remaining = self.token_budget

        # System prompt: ~1K tokens — always include
        system = build_system_prompt(task)
        context.append(system)
        budget_remaining -= count_tokens([system])

        # Recent history: ~2K tokens per turn
        for msg in reversed(recent_history):
            msg_tokens = count_tokens([msg])
            if budget_remaining - msg_tokens > 5000:  # reserve 5K for generation
                context.insert(1, msg)
                budget_remaining -= msg_tokens
            else:
                break

        # Retrieved memories: inject up to 5K tokens
        memory_budget = min(5000, budget_remaining - 5000)
        for memory in retrieved_memories:
            mem_tokens = count_tokens([memory])
            if memory_budget - mem_tokens > 0:
                context.append(memory)
                memory_budget -= mem_tokens

        return context

    def record_usage(self, input_tokens: int, output_tokens: int):
        self.total_tokens_used += input_tokens + output_tokens
        self.cost_usd += (input_tokens * MODEL_INPUT_PRICE_PER_TOKEN +
                          output_tokens * MODEL_OUTPUT_PRICE_PER_TOKEN)
```

**What this actually says.** "Treat the context window like a fixed spending limit: pay the mandatory bills first, hold back a reserve for the answer, and let memory have whatever is left over."

The budget is not the context window. `token_budget = 50000` against a 128,000-token window is a deliberate choice to leave 78,000 tokens unused, because tokens you do not send are tokens you do not pay for and do not dilute attention with.

| Symbol | What it is |
|--------|------------|
| `token_budget` | Self-imposed per-step ceiling, `50000`. Independent of the model's real 128K limit |
| `budget_remaining` | Running balance. Decremented as each piece is admitted to the context |
| `> 5000` in the history loop | Reserve held back so the model has room to *generate*. Input budget is not output budget |
| `memory_budget` | `min(5000, budget_remaining - 5000)` — a cap and a floor guard fighting each other |
| `MODEL_INPUT_PRICE_PER_TOKEN` | `$0.000005`, i.e. `$5.00 / 1M`. Multiply by tokens to get dollars |

**Walk one example.** Recent turns at the module's stated ~2K tokens each, filled greedily newest-first:

```
  token_budget                                        50,000
  - system prompt (always admitted)                  - 1,000
                                                    --------
    budget_remaining                                  49,000

  admit a turn while (budget_remaining - 2,000) > 5,000, i.e. remaining > 7,000
    turns admitted = floor((49,000 - 7,000) / 2,000) = 21 turns
    history spent  = 21 x 2,000                      = 42,000
    budget_remaining = 49,000 - 42,000               =  7,000

  memory_budget = min(5,000, 7,000 - 5,000) = 2,000     <- NOT 5,000

  final context = 1,000 + 42,000 + 2,000 = 45,000 tokens
  input cost    = 45,000 x $0.000005     = $0.225 per call
  full 128K window for comparison        = $0.640 per call   (2.8x more)
```

**The bug hiding in the ordering.** The comment says "inject up to 5K tokens" of memory, but the walk above lands on 2,000 — because history is filled *first* and greedily, leaving only `7,000 - 5,000` for memory. Retrieval quality silently drops by 60% and nothing in the code reports it. The fix is to reserve memory's 5,000 before the history loop runs, subtracting it from `budget_remaining` up front, so that history competes for what is left rather than the other way round. This is pitfall 4 in Section 10 arriving from the opposite direction: there, memory crowded out conversation; here, conversation crowds out memory. A budget that is allocated in a fixed order always starves whatever is last in line.

### Mem0: Production Memory Library

```python
from mem0 import Memory

# Mem0 provides automatic memory extraction and retrieval
m = Memory()

# Store a conversation — Mem0 extracts facts automatically
messages = [
    {"role": "user", "content": "I'm working on a Python FastAPI project"},
    {"role": "assistant", "content": "Great! I'll help with FastAPI."},
    {"role": "user", "content": "I prefer async endpoints"}
]
m.add(messages, user_id="alice")
# Mem0 extracts: "User Alice works on Python FastAPI, prefers async endpoints"
# Stored in vector DB with user_id metadata

# Retrieve relevant memories for next session
query = "Help me with my web project"
memories = m.search(query, user_id="alice")
# Returns: ["User works on Python FastAPI", "User prefers async endpoints"]

# Inject into new conversation
context = f"Relevant user context: {'; '.join(memories)}\n\n{user_query}"
```

### FIFO vs Importance-Based Eviction

```
FIFO (First In, First Out):
  Evict oldest messages first
  Simple to implement; O(1) per eviction
  Problem: evicts critical early information (task description,
           important constraints stated at conversation start)
  Use when: conversation is uniformly important throughout

Importance-Based Eviction:
  Score each message/memory by importance:
    - Explicit facts stated by user: high importance
    - Tool call results with actionable data: medium-high
    - Casual conversational turns: low
    - Error messages already resolved: low
  Evict lowest-importance first
  Score update: message importance increases if later messages reference it
  Problem: scoring requires LLM call or complex heuristics
  Use when: conversation has heterogeneous information density

Recency-Weighted:
  Importance decays over time but spikes for recently referenced items
  Similar to OS LRU (Least Recently Used) page replacement
  Good balance: recent relevance + importance score
  Practical: LangMem uses this approach
```

---

## Architecture Diagrams

### Agent Memory System

```
                    ┌────────────────────────────────────┐
                    │         CONTEXT WINDOW              │
                    │   (128K tokens = working memory)    │
                    │                                     │
                    │  System Prompt         ~2K tokens   │
                    │  Recent History       ~30K tokens   │
                    │  Retrieved Memories    ~5K tokens   │
                    │  Current Tool Results ~10K tokens   │
                    │  [Available for generation] ~80K    │
                    └─────────────┬──────────────────────┘
                                  │ archive/retrieve
         ┌────────────────────────┼────────────────────────┐
         │                        │                         │
         ▼                        ▼                         ▼
┌────────────────┐   ┌────────────────────┐   ┌───────────────────┐
│ EPISODIC STORE │   │   SEMANTIC STORE   │   │ PROCEDURAL STORE  │
│ (vector DB)    │   │   (vector DB +     │   │ (vector DB)       │
│                │   │    knowledge graph)│   │                   │
│ Past events:   │   │ Facts:             │   │ Skill templates:  │
│ - conversations│   │ - user preferences │   │ - search patterns │
│ - outcomes     │   │ - domain knowledge │   │ - code templates  │
│ - observations │   │ - entity facts     │   │ - debug sequences │
│                │   │                   │   │                   │
│ Retrieved by:  │   │ Retrieved by:      │   │ Retrieved by:     │
│ temporal query │   │ semantic similarity│   │ task similarity   │
│ event type     │   │ entity lookup      │   │                   │
└────────────────┘   └────────────────────┘   └───────────────────┘
```

---

## Real-World Examples

### ChatGPT Memory (OpenAI, 2024)

- Users can enable persistent memory across sessions
- OpenAI extracts facts from conversations: "User is a vegetarian", "User's name is Alex"
- Facts stored server-side; injected into future conversations automatically
- Users can view, edit, and delete stored memories
- Implementation: semantic extraction + fact store; ~10-20 facts per user in context

### Claude Projects (Anthropic, 2024)

- Project context: files, instructions, conversation history scoped to a project
- Custom instructions per project persist across all conversations in that project
- Conversation history in project: last N turns retained
- Use case: a coding project always has access to the codebase README and coding conventions

### Replit Ghostwriter Agent Memory

- Codebase index: all files embedded; retrieved on demand per query
- User preferences: preferred libraries, code style extracted from recent edits
- Error patterns: common errors and their fixes stored; retrieved when similar error seen
- Session memory: conversation with the user cleared between sessions; codebase index persists

---

## Tradeoffs

| Memory Type | Latency | Storage Cost | Retrieval Quality | Best For |
|-------------|---------|-------------|-------------------|---------|
| Working (in-context) | 0ms | High (per token) | Perfect | Current task context |
| Episodic (vector DB) | 20-100ms | Low | Good (semantic) | Past interactions |
| Semantic (knowledge base) | 20-200ms | Low | Good | Domain facts |
| Procedural (templates) | 20-100ms | Low | Good | Skill retrieval |

| Compression Strategy | Quality | Cost | Latency | Complexity |
|---------------------|---------|------|---------|------------|
| Sliding window | Low (loses context) | Lowest | 0ms | Trivial |
| Summarize-and-replace | Medium | Low | 200-500ms | Low |
| Hierarchical summary | High | Medium | 300-800ms | Medium |
| MemGPT-style paging | Highest | High | 50-200ms | High |

---

## When to Use / When NOT to Use

### Multi-Session Memory is Essential When:
- User has persistent preferences that improve quality (coding style, domain expertise)
- Tasks span multiple sessions (long research project, ongoing work relationship)
- The application's value proposition depends on personalization

### Keep It Simple (Working Memory Only) When:
- Single-session use cases (one-off queries, batch processing)
- Task is self-contained within one context window
- Privacy requirements prohibit persistent storage
- Performance requirements can't afford retrieval latency

---

## Common Pitfalls

1. **Memory injection without relevance filtering**: Injecting all memories regardless of relevance bloats context and degrades reasoning. Always retrieve top-K by semantic similarity, never inject everything.

2. **FIFO eviction in long conversations**: Evicting the earliest messages removes the task description and user constraints — the most important information. Implement importance scoring or at minimum always pin the first 2-3 turns.

3. **Missing memory write operations**: Developers implement memory retrieval but forget to write back new facts learned during a session. After each conversation, extract and store important facts explicitly.

4. **Token budget ignored**: A team adds 5 memory retrieval sources each injecting 2K tokens — context is 90% memory injection, leaving little room for the actual conversation. Model and enforce token budgets per memory source.

5. **Stale semantic memory**: User preferences change (they switched from Python to TypeScript); old memories contradict new behavior. Add `last_updated` timestamps and recency weighting; allow users to explicitly override or delete memories.

---

## Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **Mem0** | Automatic memory extraction | Extracts + stores facts from conversations |
| **LangMem** | LangChain memory integration | Recency + importance weighting |
| **MemGPT / Letta** | OS-style memory management | Self-managed context paging |
| **Zep** | Conversational memory store | Graph-based; entity extraction |
| **OpenAI Memory** | Managed memory (ChatGPT) | Server-side; consumer-facing |
| **Pinecone / Qdrant / Weaviate** | Vector stores for memory | Storage backend for episodic/semantic |
| **LangGraph checkpointing** | Session state persistence | Saves graph state between runs |

---

## Interview Questions with Answers

**Q: What are the four types of agent memory and what is each used for?**
A: Working memory: the current context window — everything the agent can see right now; limited to the context window size (typically 128K-200K tokens); cleared between sessions. Episodic memory: a record of past events and interactions stored in an external database; retrieved by semantic similarity or temporal query; enables the agent to remember what happened in past conversations. Semantic memory: factual knowledge about the world and the user stored in a knowledge base; includes user preferences, domain facts, entity information; retrieved by semantic similarity. Procedural memory: templates and patterns for successful task execution — successful code patterns, search strategies, debug sequences; retrieved when a similar task type is encountered.

**Q: What is the MemGPT architecture and how does it solve context overflow?**
A: MemGPT (Memory GPTs) treats the context window like OS RAM with virtual memory. The model maintains a fixed core memory (always in context: persona, user profile, current task). Recent conversation is in recall storage (last N turns in context, FIFO). Older content is in archival storage (external vector database). The novel part: the model itself manages paging — it calls `memory_append(content)` to archive information and `memory_search(query)` to retrieve relevant content from the archive. This enables effectively unlimited conversation length because the model actively manages what stays in "RAM" based on current relevance. Cost: additional tool call overhead per turn; retrieval latency 20-100ms per search.

**Q: What is the difference between summarize-and-replace and sliding window for context compression?**
A: Sliding window simply drops the oldest messages when the context overflows — fast but loses information without any synthesis. Summarize-and-replace uses an LLM call to compress old messages into a summary before evicting them — slower (200-500ms extra) but preserves the key information from compressed turns as a compact summary injected back into context. Sliding window is appropriate when: conversation turns are roughly equal in importance, budget is tight, or latency is critical. Summarize-and-replace is appropriate when: early turns contain important context (task constraints, user decisions) that must not be lost, and a 500ms compression overhead is acceptable.

**Q: How do you implement semantic memory for a user's long-term preferences?**
A: After each conversation, extract user-stated preferences via an LLM call: "Extract any explicit user preferences, constraints, or facts stated in this conversation as structured JSON." Store each extracted fact in a vector database with user_id metadata and a timestamp. At the start of each new conversation, run a semantic search against the user's memory using the current query: `memories = vector_db.search(query, filter={"user_id": user_id}, top_k=5)`. Inject the top-K results into the system prompt: "Known user preferences: [memories]." Add recency weighting to prefer recent facts over stale ones. Provide a mechanism for users to view, edit, and delete their stored memories for privacy compliance.

**Q: How does token budget affect memory architecture decisions?**
A: Token budget is the primary constraint driving every memory decision. At $5/1M input tokens (GPT-4o): a 128K context filled completely costs $0.64/call. Run 10 agents simultaneously making 20 calls each = $128/run — unsustainable. Token budget forces: (1) selective memory injection — retrieve top-K by relevance, not all memories; (2) summarization before storage — store 50-word summaries, not raw 500-word exchanges; (3) tiered memory — not all memory types need injection every call; (4) model routing — use cheaper models for memory-heavy steps. Rule of thumb: allocate ~20% of context to memory injection (semantic + episodic), 30% to conversation history, 50% to current task context and tool results.

**Q: What is the "lost in the middle" problem and how does it affect memory injection?**
A: LLM attention degrades for information placed in the middle of a long context — models tend to focus on the beginning (recency to the system prompt) and the end (recency to the current query). Information placed in the middle of a 100K context window is recalled less reliably. For memory injection: place the most critical memories at the END of the system context (just before the conversation begins) rather than the middle. Structure injected memory as a concise summary immediately preceding the conversation history, not buried in a long system prompt. Avoid injecting large blocks of memory that push the current task far from the beginning or end of context. See [Context Windows & Long Context](../context_windows_and_long_context/README.md) for the underlying attention behavior.

**Q: How do you handle memory for a multi-agent system where multiple agents need shared facts?**
A: Use a centralized memory service all agents can read from and write to. Architecture: (1) Shared vector database as the episodic/semantic memory backend — all agents query the same store with their user_id or task_id; (2) Write-back protocol: after each agent completes a step, it writes a structured summary of what it learned to the shared store; (3) Memory coordinator: in complex systems, a dedicated memory agent handles all read/write operations to prevent conflicts (two agents simultaneously updating the same fact); (4) Versioning: facts in shared memory have a version number; concurrent updates use optimistic locking. Individual working memory (the agent's own context window) is private; episodic/semantic memory is shared at the task level.

**Q: What is Mem0 and how does it differ from building memory with raw vector databases?**
A: Mem0 is a memory management library that handles the full lifecycle: extraction (LLM call to extract facts from conversations), storage (vector DB backend), retrieval (semantic search with user/session scoping), deduplication (merges new facts with existing ones rather than creating duplicates), and deletion. Using a raw vector database requires building all these yourself: writing extraction prompts, handling duplicates, implementing relevance scoring, managing metadata. Mem0's key advantage is automatic fact extraction — you pass a conversation and it identifies what's worth remembering without you specifying it. The trade-off: Mem0 adds an extraction LLM call per conversation turn, costing ~100-300 extra tokens per turn.

**Q: When is it better to not use external memory and just extend the context window?**
A: For single-session, bounded tasks (coding a specific feature, answering a research question), extending the context window is simpler, lower latency (no retrieval), and higher fidelity (no information loss from summarization). External memory only provides value when: (a) the session spans multiple separate conversations; (b) the relevant history exceeds the context window; (c) the agent should personalize based on accumulated knowledge across many users or tasks. For tasks that complete in under 100K tokens in a single session, just use a large context window. External memory adds implementation complexity, latency, and potential for retrieval misses — worth it only for long-horizon or multi-session use cases.

**Q: How do you evaluate whether your memory system is working correctly?**
A: Three metrics: (1) Recall accuracy: plant a specific fact in session N (e.g., "My name is Alice"); in session N+5, ask "What is my name?" — check if the agent correctly recalls it; (2) Precision (relevance): measure what fraction of retrieved memories are actually relevant to the current query — track with human evaluation or LLM-as-judge; (3) Context utilization: measure tokens used for memory injection vs. actual information referenced in the final response — high injection with low reference = wasted tokens. For production: instrument memory hit rate (fraction of queries where memory retrieval finds relevant content), memory write rate (facts extracted per conversation), and memory eviction rate. Low hit rate suggests retrieval quality problem; zero write rate suggests extraction is failing.

**Q: How do you architect working memory vs. long-term memory in a production agent?**
A: Working memory is the active context window — all information the agent can see right now. It is fast (zero retrieval latency), temporary (cleared when the session ends), and expensive (every token costs money per call). Long-term memory is an external store — vector database, relational DB, or key-value store. It is slower (10-100ms retrieval), persistent across sessions, and cheap (storage cost is negligible vs. compute). The architectural interface between the two is the retrieval gateway: at the start of each session, retrieve the top-K most relevant long-term memories and inject them into working memory. At the end of each session, extract new facts from the conversation and write them to long-term storage. Design rule: working memory is the agent's desk; long-term memory is the filing cabinet. Only bring files to the desk when they are needed for the current task.

**Q: How do you balance recency, importance, and similarity in memory retrieval?**
A: A production memory retrieval system should score candidate memories on three axes and combine them: (1) Similarity — cosine similarity between the current query embedding and the memory embedding; the primary signal for relevance; (2) Recency — an exponential decay function: `recency_score = exp(-lambda * days_since_stored)` where lambda = 0.1 gives memories from 7 days ago a score of ~0.5; prevents the agent from always retrieving the same old high-similarity memories; (3) Importance — a pre-computed score assigned at write time based on the type of fact: user-stated explicit preferences (high), casual mentions (low), critical decisions (high), resolved errors (low). Combined score: `final = 0.5 * similarity + 0.3 * recency + 0.2 * importance`. Tune weights based on your application: a personal assistant benefits from high recency weight; a knowledge base benefits from high similarity weight.

**Q: What memory compression strategies work for long conversations?**
A: Three practical strategies in increasing sophistication: (1) Sliding window with system prompt pinning — keep the last N turns plus always pin the first 2-3 turns (which contain task context and user constraints); fast, zero LLM cost, but loses middle conversation context; (2) Summarize-and-replace — when the context exceeds a threshold (e.g., 80K tokens), call an LLM to compress the middle 70% into a 500-token summary and replace it; costs one extra LLM call but preserves key information; (3) Hierarchical chunking — maintain summaries at multiple granularities: per-turn summary (30 words), per-task-phase summary (100 words), session summary (200 words); inject the appropriate granularity based on how far back the information is. In production, a 10-step agent task generates ~15K tokens of intermediate context; summarize-and-replace brings this down to ~2K while retaining all key facts for the synthesis step.

**Q: How do you persist agent memory across sessions in a multi-user system?**
A: Cross-session memory requires: (1) user scoping — every memory is tagged with `user_id` (or `session_id` for anonymous users); retrieval always filters by `user_id` to prevent cross-user leakage; (2) persistence layer — a vector database (Pinecone, Qdrant, pgvector) stores memory embeddings; a relational table stores metadata (user_id, content, created_at, importance, source_session_id); (3) session boundary triggers — at conversation end, an extraction job runs: summarize the session, extract facts, write to the persistent store; at session start, retrieve top-K memories and inject into the system prompt; (4) versioning — when a user states a fact that contradicts an existing memory ("I switched from Python to Go"), mark the old memory as `superseded` and create a new one; never delete the old one (audit trail); (5) TTL and deletion — honor user deletion requests by removing the vector and the metadata row; GDPR requires this within 30 days of request. LangGraph checkpointing + a vector store is the most common production pattern for this.

**Q: What privacy concerns arise with agent memory and how do you address them?**
A: Agent memory creates persistent profiles of user behavior, preferences, and sensitive disclosures. Key risks: (1) accidental storage of sensitive data — a user mentions their salary, medical condition, or password in passing; the extraction LLM stores it as a "fact"; mitigation: add a classifier to the extraction step that blocks storage of PII categories (financial, medical, credential data) and flags them for review rather than auto-storing; (2) cross-user data leakage — a bug in user_id scoping causes Alice's memories to be retrieved for Bob; mitigation: row-level security in the database enforced at query time, not just application level; (3) right to erasure — GDPR Article 17 requires full deletion within 30 days; ensure deletion cascades across all storage layers (vector index, relational metadata, backup snapshots); (4) purpose limitation — memories collected for task A should not be used for task B without consent; scope memories by application context. Best practice: give users a memory management UI showing all stored facts, with the ability to edit or delete individual entries.

---

## Best Practices

1. **Classify memory by type before storing**: episodic (event), semantic (fact), procedural (pattern) — store in separate collections with appropriate metadata for targeted retrieval.
2. **Always pin the system prompt and task description**: never evict the initial context that defines the agent's role and current goal, regardless of eviction policy.
3. **Budget tokens for memory injection**: allocate a maximum token budget per memory type (e.g., 2K for episodic, 2K for semantic); enforce it programmatically before injection.
4. **Implement memory write-back explicitly**: don't rely on automatic extraction; after key interactions, explicitly call the memory store with extracted facts.
5. **Add timestamps to all stored memories**: enables recency weighting, stale memory detection, and GDPR-compliant deletion by time range.
6. **Test memory retrieval quality separately**: write unit tests that store known facts and verify retrieval — memory bugs are subtle and often surface only in production when quality degrades.

---

## 14. Case Study: Personalized Learning Tutor with Episodic Memory

**Problem Statement**: Build an AI tutor for a K-12 EdTech platform serving 80,000 students. Each student has a different subject level, learning pace, and common mistake patterns. Without memory, every tutoring session starts fresh — the tutor re-explains concepts the student has already mastered and misses areas where they consistently struggle. The goal: a tutor that remembers across sessions and adapts its teaching strategy to each student.

**Architecture Overview**:

```
Student Session Start
      |
      v
┌──────────────────────────────────────────────────────────────┐
│  MEMORY RETRIEVAL GATEWAY                                    │
│                                                              │
│  student_id = "stu_4821"                                     │
│  subject = "algebra"                                         │
│                                                              │
│  Query 1: episodic_search("recent struggles", top_k=5)      │
│    → ["Struggled with quadratic equations 2025-05-10",       │
│        "Confused by negative exponents 2025-05-08"]          │
│                                                              │
│  Query 2: semantic_search("learning_style", exact_match)     │
│    → "Prefers visual examples over symbolic notation"        │
│                                                              │
│  Query 3: procedural_search("effective_approaches", top_k=3) │
│    → ["Worked problems step-by-step with student",           │
│        "Used pizza fractions analogy successfully"]          │
│                                                              │
│  Inject top-K memories into system prompt                    │
└──────────────────────────────────────────────────────────────┘
      |
      v
┌──────────────────────────────────────────────────────────────┐
│  TUTORING SESSION (GPT-4o)                                   │
│  System: [student profile + memory injection]                │
│  Conversation: multi-turn tutoring interaction               │
│  Tools: submit_problem, check_answer, show_hint, grade_work  │
└──────────────────────────────────────────────────────────────┘
      |
      v
┌──────────────────────────────────────────────────────────────┐
│  SESSION END: MEMORY EXTRACTION                              │
│                                                              │
│  Extract from session:                                       │
│  - New struggles: {"topic": "factoring trinomials",          │
│                    "error_type": "sign_error", "count": 3}   │
│  - Mastery updates: {"topic": "linear equations",            │
│                      "status": "mastered"}                   │
│  - Effective strategies: {"approach": "colored highlighting  │
│                            for terms worked well"}           │
│  - Session quality: {"engagement": 0.8, "problems_done": 12} │
│                                                              │
│  Write to: episodic_store, semantic_store, procedural_store  │
└──────────────────────────────────────────────────────────────┘
```

**Key Design Decisions**:

1. Three separate memory stores per student: episodic (session events and mistake logs), semantic (student profile: grade level, learning style, mastery map), and procedural (teaching strategies that worked for this student). Each store is queried independently with a topic-specific query at session start — this avoids polluting algebra retrieval with history facts.

2. Importance-weighted storage: not all session events are stored. Only events meeting an importance threshold are written: a single mistake is low importance; the same type of mistake appearing 3+ times in one session is high importance and written as a persistent pattern. This prevents the episodic store from filling with noise.

3. Mastery map as semantic memory: a JSON object per student per subject tracks mastery level (0.0-1.0) for each topic. Updated at session end: `mastery["quadratic_equations"] = 0.35 (↑ from 0.20)`. Retrieved as a single structured document rather than a semantic search — direct key lookup, not cosine similarity. This avoids retrieval misses on short topic names.

4. Privacy-first design: student memory is scoped by `(student_id, subject)`. No cross-student retrieval is possible at the database query level (row-level security). Sensitive observations (family context, emotional state) are flagged during extraction and routed to a human review queue rather than stored automatically.

5. Memory compression for long-term students: after 6 months, episodic memories older than 30 days are compressed: an LLM summarizes the previous month's sessions into a 100-word "learning phase summary." This keeps the episodic store bounded even for students using the platform for years.

**Implementation**:

```python
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Optional
import numpy as np

@dataclass
class StudentMemory:
    student_id: str
    subject: str
    mastery_map: dict[str, float]      # topic → 0.0-1.0
    learning_style: str
    episodic_events: list[dict]        # recent struggle/success events
    effective_strategies: list[str]   # teaching approaches that worked

class TutorMemorySystem:
    def __init__(self, vector_db, relational_db):
        self.vdb = vector_db
        self.rdb = relational_db

    def retrieve_for_session(self, student_id: str, subject: str,
                              current_topic: str) -> StudentMemory:
        """Retrieve all memory types before a tutoring session."""

        # Semantic: student profile + mastery map (exact lookup, no embedding search)
        profile = self.rdb.get_student_profile(student_id, subject)
        mastery_map = profile["mastery_map"]
        learning_style = profile["learning_style"]

        # Episodic: recent struggles relevant to current topic
        struggle_query = f"student struggles with {current_topic} {subject}"
        recent_struggles = self.vdb.search(
            query=struggle_query,
            filter={"student_id": student_id, "type": "struggle",
                    "created_at": {"gte": (datetime.now() - timedelta(days=30)).isoformat()}},
            top_k=5
        )

        # Procedural: effective strategies for this student's profile
        strategy_query = f"effective teaching {learning_style} {subject}"
        strategies = self.vdb.search(
            query=strategy_query,
            filter={"student_id": student_id, "type": "strategy"},
            top_k=3
        )

        return StudentMemory(
            student_id=student_id,
            subject=subject,
            mastery_map=mastery_map,
            learning_style=learning_style,
            episodic_events=[e["content"] for e in recent_struggles],
            effective_strategies=[s["content"] for s in strategies]
        )

    def build_system_prompt(self, memory: StudentMemory, topic: str) -> str:
        weak_topics = [t for t, m in memory.mastery_map.items() if m < 0.4]
        mastered_topics = [t for t, m in memory.mastery_map.items() if m > 0.8]

        return f"""You are a personalized math tutor for a student.

Student profile:
- Learning style: {memory.learning_style}
- Mastered topics (do not re-teach): {', '.join(mastered_topics)}
- Weak topics (extra care needed): {', '.join(weak_topics)}

Recent struggles to watch for:
{chr(10).join(f'- {e}' for e in memory.episodic_events)}

Teaching strategies that have worked well with this student:
{chr(10).join(f'- {s}' for s in memory.effective_strategies)}

Today's topic: {topic}
Adapt your explanations to the student's learning style.
If you observe new struggle patterns, they will be recorded for next session."""

    def extract_and_store_session(self, student_id: str, subject: str,
                                   session_transcript: str):
        """Extract learnable facts from a completed session and persist them."""

        # LLM extraction call
        extraction_prompt = f"""Analyze this tutoring session transcript and extract:
1. Topics the student struggled with (3+ mistakes of same type = pattern)
2. Topics newly mastered (correct on first attempt, no hints needed)
3. Teaching approaches that were effective (student said "oh I get it now" or similar)
4. Student engagement level (0.0-1.0)

Return as structured JSON. Omit sensitive personal information.

Transcript: {session_transcript[:4000]}  # truncate for cost control
"""
        extracted = llm.invoke(extraction_prompt, response_format={"type": "json_object"})
        data = json.loads(extracted)

        # Write episodic: only high-importance struggles
        for struggle in data.get("struggles", []):
            if struggle["mistake_count"] >= 3:  # importance threshold
                self.vdb.upsert(
                    text=f"Struggled with {struggle['topic']}: {struggle['error_type']}",
                    metadata={
                        "student_id": student_id, "type": "struggle",
                        "topic": struggle["topic"], "created_at": datetime.now().isoformat(),
                        "importance": min(1.0, struggle["mistake_count"] / 10)
                    }
                )

        # Write semantic: update mastery map
        for mastery in data.get("mastery_updates", []):
            self.rdb.update_mastery(student_id, subject,
                                    mastery["topic"], mastery["new_level"])

        # Write procedural: effective strategies
        for strategy in data.get("effective_strategies", []):
            self.vdb.upsert(
                text=strategy,
                metadata={"student_id": student_id, "type": "strategy",
                           "created_at": datetime.now().isoformat(), "importance": 0.7}
            )
```

**Read it like this.** "Repeat a mistake three times and it becomes worth remembering; repeat it ten times and it is as memorable as anything ever gets."

Two separate numbers are doing two separate jobs here, and conflating them is a common design error. `mistake_count >= 3` is a **gate** — it decides whether a memory is written at all. `min(1.0, mistake_count / 10)` is a **score** — it decides how loudly that memory competes at retrieval and eviction time.

| Symbol | What it is |
|--------|------------|
| `mistake_count` | How many times the same error type appeared in one session. The raw evidence |
| `>= 3` | Write gate. Below it, nothing is stored — this is what keeps the episodic store from filling with noise |
| `/ 10` | Normalizer. Turns a count into a 0-1 score by declaring 10 occurrences to be "full strength" |
| `min(1.0, ...)` | Saturation clamp. Stops any single pathological session from outranking every other memory |
| `importance` | The stored 0-1 weight, later combined with recency for eviction (the "recency-weighted" scheme above) |

**Walk one example.** Six sessions, same student, different error counts:

```
  mistake_count    passes gate (>= 3)?    count/10    min(1.0, count/10)    stored as
        1                 no                0.1              --             not stored
        2                 no                0.2              --             not stored
        3                YES                0.3             0.30            weak signal
        5                YES                0.5             0.50            moderate
       10                YES                1.0             1.00            saturated
       14                YES                1.4             1.00            saturated (clamped)

  counts 10 and 14 are indistinguishable once stored -- the clamp threw that away
```

**Why the clamp is worth the lost resolution.** Without `min`, a single bad session with 40 sign errors would produce `importance = 4.0` and dominate every retrieval ranking for that student for months, drowning out the twelve other topics they also need help with. Saturating at 1.0 says: past ten repetitions, "this is a real pattern" is the entire message, and the exact count adds nothing a tutor would act on differently. The cost is real but small — you can no longer tell a 10-mistake topic from a 14-mistake one, so pair the score with `mistake_count` in metadata if you ever need to rank within the saturated band.

**Results**:

- Session personalization score (LLM judge on rubric): 4.1/5.0 vs. 2.8/5.0 without memory
- Problem completion rate per session: 12.3 vs. 8.7 (no memory) — memory prevents re-teaching mastered content
- Student retention (30-day active rate): 67% vs. 54% in A/B test against memoryless tutor
- Memory retrieval latency: 85ms P95 (acceptable for session-start, not per-turn)
- False positive memory writes (sensitive PII stored despite filter): 0.08% — reviewed weekly by trust & safety team

**Tradeoffs and Alternatives**:

- Full conversation history injection (no retrieval) was prototyped: worked well for students with <5 sessions (context fits in window) but became prohibitively expensive at 50+ sessions ($1.20/session vs. $0.18 with retrieval).
- Single vector store (no memory type separation) was tried: retrieval quality was poor because algebra struggle events competed with learning style facts in the same index. Separating episodic, semantic, and procedural stores improved retrieval precision from 71% to 89%.
- Opt-in memory was considered: initial testing showed students and parents trusted the product more when memory was opt-in with a visible memory dashboard, despite slightly lower engagement when memory was disabled. The opt-in dashboard is now standard.

**Put simply.** "Retrieval does not make memory better here — it makes memory affordable. The whole argument is a division problem."

| Symbol | What it is |
|--------|------------|
| `$1.20/session` | Full-history injection. Every past session replayed into context, every time |
| `$0.18/session` | Retrieval. Only the top-K memories that match this session's topic |
| tokens implied | Cost divided by `$0.000005/token` — converts dollars back into context tokens |
| 71% -> 89% | Retrieval precision, single shared index versus three type-separated indexes |

**Walk one example.** Start from the two published per-session costs and the input price already defined in this module:

```
  full-history injection    $1.20 / $0.000005  =  240,000 input tokens per session
  retrieval-based           $0.18 / $0.000005  =   36,000 input tokens per session

  saving per session    $1.20 - $0.18  =  $1.02      (85.0% cheaper, a 6.7x ratio)

  at 80,000 students, one session per student per week:
    full history    80,000 x $1.20  =  $96,000 / week
    retrieval       80,000 x $0.18  =  $14,400 / week
    saved                              $81,600 / week
```

Note the first line: 240,000 tokens does not fit in a 128,000-token window at all. At 50+ sessions the full-history approach is not merely expensive, it is **impossible** in one call — it silently becomes multiple calls or a truncation, which is why it was abandoned rather than merely budgeted for. This is the same wall the token-budget code above is built to stay behind.

**Why splitting the index moved precision 18 points.** The single-store version put "struggled with factoring trinomials" and "prefers visual examples" in one embedding space, so an algebra query retrieved learning-style facts and vice versa — 71% precision means roughly 3 of every 10 retrieved memories were off-type. Separating episodic, semantic, and procedural stores does not improve any embedding; it removes the competition, lifting precision to 89% (a 25% relative gain) purely by narrowing what each query is allowed to match. The mastery map goes further and abandons similarity search entirely for a keyed lookup, because "quadratic_equations" is too short a string for cosine similarity to rank reliably.
