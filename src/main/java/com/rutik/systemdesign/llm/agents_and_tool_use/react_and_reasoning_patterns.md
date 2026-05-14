# ReAct & Reasoning Patterns

## Concept Overview

ReAct (Reasoning + Acting), Reflexion, Tree of Thoughts, and self-consistency are prompting patterns that guide LLMs through structured reasoning before and during action. They address a fundamental limitation: raw LLMs produce better outputs when their reasoning is made explicit and structured rather than implicit and one-shot.

ReAct interleaves thought and action in a Thought-Action-Observation loop. Reflexion adds self-critique and episodic memory. Tree of Thoughts explores multiple reasoning paths. Self-consistency samples multiple reasoning chains and votes. Each pattern trades compute (more tokens, more calls) for quality.

---

## Intuition

> **One-line analogy**: ReAct is like a detective who thinks aloud before acting — "I need to check the alibi first, then examine the evidence, then form a conclusion" — rather than guessing the answer immediately.

**Mental model**: A standard LLM prompt is like an exam question answered under time pressure — the model compresses all reasoning into the output. ReAct separates thinking from acting: the "Thought" field is scratch space where the model reasons; the "Action" field commits to a tool call; "Observation" is the tool result. This separation makes reasoning visible, debuggable, and steerable. Reflexion extends this by adding a loop where the model evaluates its own performance after a task and stores lessons for next time.

**Why it matters**: Explicit reasoning traces improve accuracy on multi-step tasks by 10-30% compared to direct answers. They also make agent behavior understandable — you can read the Thought field to understand why the agent took a particular action.

**Key insight**: LLMs think better when they write their thinking down. Chain-of-thought, ReAct, and all related patterns exploit the fact that each generated token attends to all previous tokens — "writing it out" expands the effective reasoning capacity of the model.

---

## Core Principles

- **Explicit reasoning improves accuracy**: Prompting for a Thought before each Action consistently outperforms direct action selection on multi-step tasks.
- **Grounded actions prevent hallucination**: Acting on tool observations rather than memory alone reduces hallucinated facts.
- **Iteration enables correction**: Multi-round loops (ReAct, Reflexion) let the agent detect and correct errors mid-task.
- **Search improves planning**: Tree of Thoughts evaluates multiple strategies before committing — useful when the optimal path is not obvious.
- **Majority vote reduces variance**: Self-consistency reduces the effect of single-path reasoning errors.

---

## How It Works — Detailed Mechanics

### ReAct: Thought-Action-Observation Loop

```
ReAct Prompt Structure:

System: "Solve the task using the following format:
  Thought: [your reasoning about what to do next]
  Action: [tool_name]([arguments])
  Observation: [result of the action]
  ... (repeat until you have an answer)
  Thought: I now have enough information.
  Final Answer: [your answer]"

Example trace:
--------------------------------------------------
Task: What is the capital of the country where the 2024 Olympics were held?

Thought: I need to find which country hosted the 2024 Olympics first.
Action: search("2024 Olympics host country")
Observation: The 2024 Summer Olympics were held in Paris, France.

Thought: The country is France. I know the capital is Paris, which is also
         the host city. I can answer without another search.
Final Answer: Paris, France.
--------------------------------------------------
```

**Why explicit Thought helps**: The Thought field causes the model to articulate its current state before selecting an action. This activates chain-of-thought reasoning and prevents "action reflexes" — selecting the first plausible-sounding tool without reasoning.

### ReAct Failure Modes

```
Failure 1: Thought-Action disconnect
  Thought: "I should search for X"
  Action: search("Y")   ← model ignores its own Thought
  Fix: Reinforce in system prompt: "Your Action must be consistent with your Thought."

Failure 2: Premature final answer
  Thought: "I think I know the answer"
  Final Answer: [hallucinated fact, no tool verification]
  Fix: Require specific tool calls for verifiable claims in system prompt.

Failure 3: Observation ignored
  Observation: "No results found for X"
  Thought: "I found information about X"  ← contradicts observation
  Fix: Instruct: "Your Thought must be consistent with the Observation."

Failure 4: Infinite refinement loop
  The agent keeps searching without converging.
  Fix: Hard limit of N steps (typically 10-15); inject "You have N steps remaining."
```

### Reflexion: Self-Reflection and Episodic Memory

```
Standard ReAct: single attempt → success or failure

Reflexion adds two components:
  1. Evaluator: after task completion, assess success/failure
  2. Memory: store verbal "lesson learned" → used in next attempt

Reflexion Loop:
  Attempt 1:
    [ReAct loop] → fails (wrong answer, test fails)

  Reflection prompt:
    "You attempted: [task]
     Your trajectory: [all thoughts/actions/observations]
     The result was: FAILED (reason: X)
     What specific mistake led to failure? What will you do differently?"

  Stored reflection (episodic memory):
    "I searched for 'X CEO' but should have searched 'X founder' to find the
     correct person. Next time, try multiple search variants."

  Attempt 2:
    [Context includes stored reflection]
    [ReAct loop with corrected strategy] → success

Memory storage:
  Verbal reinforcement stored as text in a buffer (not gradient updates)
  Buffer injected into context on subsequent attempts
  Buffer size: typically 5-10 reflections before summarization
  LangMem / Mem0: production libraries for managing reflection memory
```

### Tree of Thoughts (ToT)

```
Problem: optimal path through multi-step reasoning is not obvious upfront

ToT approach: generate multiple candidate thoughts, evaluate each,
              continue from the best

Branching factor B = 3-5 thoughts per step
Search depth D = 3-5 steps
Evaluation: LLM rates each thought (0-10) or votes on best continuation

Structure:
  Task: [problem]
      |
      +-- Thought 1a: [approach A]    score: 7
      |       +-- Thought 2a: [...]   score: 5  PRUNE
      |       +-- Thought 2b: [...]   score: 8
      |               +-- Final: [answer A]
      |
      +-- Thought 1b: [approach B]    score: 9  ← best
      |       +-- Thought 2c: [...]   score: 9
      |               +-- Final: [answer B]  ← selected
      |
      +-- Thought 1c: [approach C]    score: 4  PRUNE

Search strategies:
  BFS: expand all candidates at each level, keep top-K
  DFS: follow most promising branch, backtrack on failure
  Beam search: keep top-B candidates at each level (B = beam width)

Cost: B × D × LLM calls (e.g., 3 branches × 4 depth = 12 LLM calls minimum)
Use when: planning problems where early choices have large impact on outcome
Avoid when: single-step tasks where one good CoT is sufficient
```

### Self-Consistency

```
Idea: sample N independent reasoning chains; take the majority answer

Standard greedy decoding:
  Prompt → one chain of thought → one answer
  If chain has error early → propagates to wrong answer

Self-consistency:
  Prompt → N chains (temperature > 0) → N answers → majority vote

  N=5 example:
    Chain 1: [reasoning] → Paris
    Chain 2: [reasoning] → Paris
    Chain 3: [reasoning] → Lyon (error)
    Chain 4: [reasoning] → Paris
    Chain 5: [reasoning] → Paris

    Vote: Paris (4/5) ← correct

  Optimal N by task difficulty:
    Simple math: N=3 sufficient
    Complex reasoning: N=5-10
    Diminishing returns beyond N=20

  Cost: N × single_call_cost
  Gain: ~5-15% accuracy improvement on math and reasoning benchmarks
  No gain: factual lookup tasks (voting doesn't help if the fact is wrong)
```

### Scratchpad Prompting

```
Scratchpad: a text area in the prompt where the model can draft reasoning
before producing the final answer.

Template:
  "Think through this problem step by step in <scratchpad> tags.
   Write out all your reasoning, calculations, and intermediate steps.
   Then provide your final answer after </scratchpad>."

Example:
  <scratchpad>
  The question asks for 15% of 840.
  15% = 0.15
  0.15 × 840 = 126
  Let me verify: 10% = 84, 5% = 42, 15% = 84 + 42 = 126. Correct.
  </scratchpad>
  Final Answer: 126

Scratchpad vs explicit CoT:
  Scratchpad: model writes freely, including false starts, corrections
  Explicit CoT: model follows a structured format (Thought/Action/Observation)
  Scratchpad is better for pure reasoning; CoT format better for agentic loops
```

### Chain-of-Thought in Tool-Calling Agents

```python
# Combining CoT with tool calling: inject a "thinking" step before each action

system_prompt = """You are a research agent. Before each tool call, write a
brief <thinking> section explaining:
1. What information you currently have
2. What gap you're trying to fill
3. Why you chose this specific tool and these specific arguments

Format:
<thinking>
[your reasoning]
</thinking>
[then your tool call]
"""

# For Anthropic's extended thinking feature (claude-3-7-sonnet):
response = client.messages.create(
    model="claude-3-7-sonnet-20250219",
    thinking={"type": "enabled", "budget_tokens": 10000},
    messages=[{"role": "user", "content": task}]
)
# model.thinking blocks are separate from text blocks
# shows chain-of-thought before response
```

---

## Architecture Diagrams

### ReAct Loop

```
Task Input
    |
    v
[Thought] ← LLM reasons about current state and next action
    |
    v
[Action] → tool call (search, code execution, API call)
    |
    v
[Observation] → tool result injected into context
    |
    v
[Is task complete?]
    |
    +-- YES → Final Answer
    |
    +-- NO  → back to [Thought]
    |
    +-- STUCK (same action 2× or N steps exceeded) → abort / partial answer
```

### Reflexion Architecture

```
Episode N                    Memory Buffer
┌──────────────────┐         ┌─────────────────────────────────┐
│ ReAct loop       │         │ Reflection 1: "search broader"  │
│ (uses memory)    │         │ Reflection 2: "verify dates"    │
│       ↓          │         │ Reflection N-1: "..."           │
│ Outcome: FAIL    │         └─────────────────────────────────┘
└──────────────────┘                       ↑
         ↓                                 │
    [Evaluator]                            │
    "What went wrong?"                     │
         ↓                                 │
    [Reflection]  ─────────────────────────┘
    "Next time try X"

Episode N+1 (with memory injected in context):
[ReAct loop] → improved strategy → PASS
```

### Tree of Thoughts

```
Task
 |
 +── Thought A (score: 6)
 |       |
 |       +── A.1 (score: 8) ── A.1.1 → ANSWER 1
 |       +── A.2 (score: 4) [PRUNED]
 |
 +── Thought B (score: 9) ★ best
 |       |
 |       +── B.1 (score: 9) ── B.1.1 → ANSWER 2 ★ selected
 |       +── B.2 (score: 7)
 |
 +── Thought C (score: 3) [PRUNED]
```

---

## Real-World Examples

### ReAct in Production: Claude Code

Claude Code uses an implicit ReAct loop for every engineering task:
- Thought: "I need to understand the codebase structure before modifying it" → no explicit label but present in reasoning
- Action: `read_file(path)` or `bash("ls -la")`
- Observation: file contents or directory listing
- Next thought adapts based on observation
- ~6-15 steps per typical coding task; hard limit enforced to prevent runaway

### Reflexion in Research Agents

Anthropic's multi-agent research systems use Reflexion-style memory:
- Agent attempts a week-long research task
- At each major failure checkpoint (dead-end search, contradictory sources), the supervisor agent writes a reflection
- Subsequent sub-agents receive the reflection in their context
- Observed: ~30% improvement in task completion rate with reflection vs without

### Self-Consistency in Medical Q&A

A healthcare AI system uses N=7 self-consistency for clinical decision support:
- 7 reasoning chains generated at temperature=0.7
- Final answer requires 5/7 agreement (supermajority)
- Edge case: if no supermajority → escalate to human reviewer
- Reduces single-chain reasoning errors in medication dosing calculations

---

## Tradeoffs

| Pattern | Cost | Quality Gain | Best For |
|---------|------|-------------|---------|
| Direct answer | 1× call | Baseline | Simple factual Q&A |
| Standard CoT | 1× call (+tokens) | +10-20% reasoning | Math, logic |
| ReAct | N calls (N steps) | +20-30% multi-step | Agentic tasks with tools |
| Reflexion | K attempts × N steps | +15-30% on retry | Tasks with verifiable feedback |
| Tree of Thoughts | B × D calls | +20-40% planning | Complex planning, search |
| Self-consistency | N× calls | +5-15% on reasoning | High-stakes single Q&A |

---

## When to Use / When NOT to Use

### Use Explicit Reasoning Patterns When:
- Multi-step tasks where intermediate reasoning affects later steps
- Tasks with verifiable outcomes (code execution, math, fact lookup)
- High-stakes decisions where reasoning transparency is required
- Agent tasks with tool calls (ReAct is the default)

### Avoid / Simplify When:
- Simple single-step tasks (CoT adds tokens without quality gain)
- Latency-critical paths (ReAct's N-round loop adds N × LLM latency)
- Tasks where the model is essentially certain (factual lookups by capable models)
- Cost-sensitive batch processing (self-consistency's N× cost rarely justified)

---

## Common Pitfalls

1. **Thought-Action mismatch**: Model writes "I should search for X" then searches for Y. Add validation in system prompt: "Your Action must directly follow from your Thought."

2. **Observation fabrication**: In some implementations, if the tool call fails silently, the model invents an Observation. Always require real tool execution before injecting an observation; never allow the model to self-fill the observation field.

3. **ToT cost explosion**: Branching factor 5 × depth 5 = 3,125 leaf nodes with naive BFS. Always set a beam width limit (top-3) and prune early with evaluator scores. Budget 20-50 LLM calls maximum for ToT.

4. **Reflexion memory overflow**: Storing every reflection in context will eventually exceed the context window. Summarize reflections periodically; keep only the last 5-10 or a compressed summary.

5. **Self-consistency on factual tasks**: If the underlying fact is wrong in training data, sampling N chains from the same model just gives N wrong answers — majority vote does not help. Self-consistency helps with reasoning errors, not knowledge gaps.

---

## Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **LangGraph** | ReAct agent loop | Built-in `create_react_agent` helper |
| **LangChain agents** | ReAct + tool execution | Legacy `AgentExecutor`; prefer LangGraph |
| **Reflexion (GitHub)** | Reflexion implementation | Princeton; reference implementation |
| **LangMem** | Long-term agent memory | Reflection storage + retrieval |
| **Mem0** | Agent memory platform | Persistent memory across sessions |
| **DSPy** | Programmatic prompting | Self-optimizing prompts including CoT |
| **SGLang** | Efficient structured gen | Fast parallel sampling for self-consistency |
| **Claude extended thinking** | Native model CoT | `thinking` parameter in API call |

---

## Interview Questions with Answers

**Q: What is the ReAct pattern and why does it outperform direct LLM calls for agentic tasks?**
A: ReAct (Reasoning + Acting) prompts the LLM to alternate between a Thought (reasoning about what to do), an Action (tool call), and an Observation (tool result), repeating until the task is complete. It outperforms direct calls for three reasons: (1) explicit thinking causes the model to reason through the task before committing to an action, reducing impulsive wrong tool selections; (2) grounding in tool observations prevents the model from hallucinating intermediate facts; (3) the iterative loop allows the model to observe whether its hypothesis was correct and adjust. The 2022 ReAct paper showed 10-30% improvement over chain-of-thought-only prompting on knowledge-intensive multi-step tasks.

**Q: What is Reflexion and how does it differ from standard ReAct?**
A: Reflexion (Shinn et al., 2023) adds a self-reflection loop after task failure. After an unsuccessful ReAct attempt, a separate "reflection" prompt asks the model what went wrong and what it would do differently. This verbal self-critique is stored in an episodic memory buffer and injected into the context of subsequent attempts. Unlike ReAct (single attempt, no learning from failure), Reflexion converges over multiple attempts — it's like a test student reviewing their mistakes before retaking the exam. The key limitation: Reflexion requires a verifiable success signal (tests pass/fail, factual answer is correct/wrong) to trigger the reflection; it cannot reflect on subjective tasks without an external evaluator.

**Q: What is Tree of Thoughts and when is it worth the additional cost?**
A: Tree of Thoughts (Yao et al., 2023) generates multiple candidate thoughts at each reasoning step, evaluates them (with an LLM scorer), expands the most promising branches, and prunes low-scoring ones — essentially beam search over a reasoning tree. Cost: B (branching factor) × D (depth) × LLM calls minimum. This is only worthwhile for problems where: (1) early reasoning choices significantly affect the outcome; (2) there is a way to evaluate partial progress; (3) the task has no simple greedy solution. Examples: 24-game math puzzle, multi-step planning, code debugging with multiple candidate fixes. For straightforward tasks, standard ReAct or CoT is sufficient and far cheaper.

**Q: How does self-consistency work and when does it improve accuracy?**
A: Self-consistency (Wang et al., 2022) generates N independent reasoning chains at temperature > 0, extracts the final answer from each, and takes the majority vote. It exploits the fact that multiple chains making different errors will produce different wrong answers, but multiple chains reaching the correct answer via different reasoning will converge. Improvement is largest on: arithmetic, multi-step math, and logical reasoning — tasks with a single correct answer that can be reached by multiple valid reasoning paths. It does NOT help on: tasks where the model lacks the knowledge (all chains hallucinate the same wrong fact), open-ended generation, or tasks without a discrete final answer. Typical gain: 5-15% on GSM8K math benchmarks with N=5-10.

**Q: What is scratchpad prompting and how does it differ from ReAct's Thought field?**
A: Scratchpad prompting gives the model free-form scratch space to write interim calculations, false starts, and corrections before committing to a final answer — like scrap paper. ReAct's Thought field is more structured: it's part of the agentic loop, each Thought must directly motivate the subsequent Action, and it's observable by the application. Scratchpad is typically enclosed in `<scratchpad>` tags and may be stripped from the final output; Thought fields are logged and used for debugging. Scratchpad is better for pure reasoning tasks (math, logic) where intermediate steps shouldn't be exposed; ReAct Thoughts are better for agent tasks where reasoning transparency is required.

**Q: What causes an agent to get stuck in a loop and how do you prevent it?**
A: Loops occur when the agent repeatedly calls the same tool with the same arguments after receiving a result that doesn't help it progress. Common causes: (1) the tool returned an error the agent doesn't know how to recover from; (2) the model's Thought doesn't update based on the Observation (model ignores evidence); (3) the task is not solvable with available tools. Prevention: (1) hard step limit (10-20 steps); (2) repetition detection — if the same (tool, arguments) pair appears twice, inject "You have already tried this. Try a different approach or conclude the task."; (3) progress tracking — after each step, assess whether new information was obtained.

**Q: How do you choose between ReAct, Reflexion, and Tree of Thoughts for a production agent?**
A: ReAct: default choice for all agentic tool-use tasks; good balance of quality and cost. Reflexion: add when the task has a verifiable success signal and failure is common on first attempt — it's retry-with-learning, suitable for coding agents (tests define success) and research agents with verifiable facts. Tree of Thoughts: reserve for planning-heavy tasks where the decision space is large and backtracking is valuable — avoid in production due to N× cost unless the task explicitly requires multi-path search. Most production agents use ReAct; add Reflexion if quality on difficult tasks is insufficient; consider ToT only for specialized planning domains.

**Q: What is chain-of-thought (CoT) prompting and how does it relate to ReAct?**
A: Chain-of-thought prompting adds reasoning steps to the LLM's output before the final answer — "Let me think step by step..." — without any tool calls. ReAct extends CoT by interleaving reasoning with actions (tool calls) and observations (results). CoT is a pure in-context reasoning enhancement; ReAct is CoT embedded in an agent loop with real-world grounding. CoT improves reasoning on self-contained tasks (math, logic); ReAct is CoT plus external verification (actual search results, code execution outputs). In practice, the "Thought" field in ReAct IS chain-of-thought applied per action step.

**Q: How does Anthropic's extended thinking feature relate to these patterns?**
A: Anthropic's extended thinking (`thinking` parameter in the Claude API) enables the model to produce a private, extensive reasoning trace before the visible response. This is built-in CoT at the model level rather than prompted CoT — the model uses its native reasoning capacity in a hidden scratch space, then produces a final answer. Unlike ReAct's Thought field (which is prompted and visible), extended thinking tokens are generated at a different inference cost, can be much longer (budget up to tens of thousands of tokens), and are not constrained to follow a human-readable format. Extended thinking significantly improves performance on hard math, coding, and multi-step reasoning tasks — equivalent to adding ToT-like depth without the multiple-call overhead.

**Q: Why does the order of reasoning matter — Thought before Action vs. Action before Thought?**
A: Thought before Action (standard ReAct) forces the model to explicitly reason about what to do before committing to a tool call. This activates the model's planning capacity via chain-of-thought and reduces "reflex actions" — selecting the first plausible tool. Action before Thought would produce lower-quality decisions because the model generates the action token before articulating its reasoning, losing the benefit of explicit deliberation. Empirically, models that output a Thought field select the correct tool more often and use better search queries. The mechanism: each token the model generates attends to all previous tokens — writing a complete Thought first makes that reasoning available when generating the Action's arguments.

**Q: How does ReAct compare to Plan-and-Execute for complex multi-step tasks?**
A: ReAct discovers its next action one step at a time using the Thought-Action-Observation loop — it adapts dynamically but can lose strategic direction after 15+ steps. Plan-and-Execute generates a complete plan upfront, then executes each step with a focused executor — it maintains strategic coherence on long tasks but requires a replanning mechanism when new information invalidates the plan. ReAct wins for: short tasks (<10 steps), highly unpredictable environments where no meaningful plan can be formed ahead of time, and interactive tasks where user feedback changes the goal mid-execution. Plan-and-Execute wins for: long structured tasks (research → analyze → write), tasks requiring parallel execution of independent sub-tasks, and compliance contexts where stakeholders must approve a plan before execution. Many production systems layer them: Plan-and-Execute for top-level structure, with each execution step running a mini ReAct loop for its sub-tasks.

**Q: How do you handle ReAct loops where the model keeps repeating the same action?**
A: Repetition in a ReAct loop occurs when the model's Thought field fails to update based on the Observation — the agent "ignores" what it saw and repeats the same action. Prevention: (1) inject explicit repetition detection — after each step, check if (tool_name, arguments) matches any prior step; if so, inject "You have already tried this exact action and received: [prior result]. Do not repeat it. Try a different approach or conclude the task."; (2) add recency weighting in the system prompt: "In your Thought, explicitly reference what the most recent Observation told you before deciding your next action"; (3) enforce step diversity — if the same tool is called 3 times with identical arguments, abort and return a partial answer. The root cause is often that the Observation was not informative (tool returned nothing) and the model defaults to the action it knows. Inject better error messages in tool results: "No results found — try broader search terms or check spelling."

**Q: How do you reduce ReAct latency for latency-sensitive applications?**
A: ReAct's latency is N_steps × LLM_call_latency, where each step adds 1-3 seconds for a frontier model. Reduction strategies: (1) Thought verbosity control — add to the system prompt "Keep your Thought concise — 1-2 sentences maximum. Do not explain your reasoning at length."; verbose Thoughts use more output tokens and slow generation by 30-50%; (2) Smaller model for executor — if the ReAct loop uses a frontier model for every step, switch to a smaller model (GPT-4o-mini, Haiku) for standard tool calls; reserve the frontier model only for the final synthesis step; (3) Parallel tool calls — emit multiple independent tool calls in one response to eliminate one full LLM round-trip per parallel call; (4) Early termination — after each step check if the accumulated information already suffices to answer; inject a "can you answer now without more tool calls?" gate to short-circuit loops. A well-optimized ReAct agent can achieve 40-60% latency reduction vs. naive implementation while maintaining most quality.

**Q: How do you use ReAct with structured tool outputs to improve observation parsing?**
A: Structured observations (JSON with explicit field names) dramatically outperform prose observations in ReAct because the model extracts facts reliably from labeled fields rather than parsing natural language. Instead of returning "Apple stock is trading at $189.50, up 1.2% today", return `{"symbol": "AAPL", "price_usd": 189.50, "change_pct": 1.2, "as_of": "2025-05-15T14:30:00Z"}`. This enables the model's Thought to reference `price_usd` explicitly: "The observation shows price_usd=189.50, which is above the user's $180 threshold." Beyond reliability, structured observations support programmatic parsing — you can extract specific fields from observations for logging, alerting, or downstream processing without re-parsing the model's Thought. For tools returning large results, add a `summary` field with a one-sentence digest so the Thought field can remain concise.

**Q: How do you measure ReAct reasoning faithfulness — whether the model's Thought actually predicts its Action?**
A: Faithfulness measures whether the stated Thought causally determines the Action, or whether the Action was chosen first and the Thought post-hoc rationalized. Measurement approach: (1) Prediction test — have an independent LLM read only the Thought field and predict what Action it implies; compare to actual Action taken; agreement rate measures faithfulness; (2) Counterfactual test — modify the Thought to imply a different action ("I should search for X instead of Y") and check if the Action changes accordingly; a faithful model changes its action; an unfaithful one ignores the Thought change; (3) Ablation — run the agent with Thought fields stripped from context; if task success rate is unchanged, the Thoughts were not influencing decisions. In practice, faithfulness varies by model: Claude and GPT-4o show ~70-80% Thought-Action faithfulness on standard tasks; smaller models show lower faithfulness, especially when the task becomes complex. Log Thought and Action separately for analysis.

---

## Best Practices

1. **Use ReAct as the default**: for any agent with more than one tool, the structured Thought-Action-Observation loop consistently outperforms direct tool selection.
2. **Add Reflexion for verifiable tasks**: when success is measurable (tests pass/fail, fact is correct/wrong), Reflexion's retry-with-reflection loop substantially improves completion rates.
3. **Constrain ToT with beam search**: never use BFS with large branching factors; beam width 2-3 with depth 3-4 captures most of the quality gain at manageable cost.
4. **Log all Thought fields**: the reasoning traces are essential for debugging agent mistakes and building evaluation datasets.
5. **Inject step counts into the Thought context**: "You have used 6 of 15 allowed steps" — prevents the model from not noticing when it's approaching the iteration limit.
6. **Test for observation-ignoring**: create test cases where the tool returns "not found" and verify the agent's next Thought acknowledges this; hallucinated observations are a common failure mode.

---

## 14. Case Study: ReAct Agent for a Data Analysis Assistant

**Problem Statement**: Build a data analysis assistant for a 500-person SaaS company. Business analysts submit natural language questions like "Which customer segment had the highest churn in Q1 2025, and what were the top 3 contributing factors?" The agent must query a PostgreSQL database, generate charts, and answer iteratively — analysts often ask follow-up questions within the same session. Average question requires 6-10 ReAct steps.

**Architecture Overview**:

```
Analyst Question
      |
      v
┌────────────────────────────────────────────────────────────┐
│  ReAct LOOP (GPT-4o, max 15 steps)                         │
│                                                            │
│  Thought: "I need to identify churn by segment first.      │
│            I'll query the customers and events tables."    │
│      |                                                     │
│  Action: run_sql(query="SELECT segment, COUNT(*) ...")     │
│      |                                                     │
│  Observation: {"rows": [...], "row_count": 847, ...}       │
│      |                                                     │
│  Thought: "Enterprise segment shows 18% churn vs 6% SMB.  │
│            I need to drill into Enterprise cancellation    │
│            reasons next."                                  │
│      |                                                     │
│  Action: run_sql(query="SELECT reason, COUNT(*) ...")      │
│      |                                                     │
│  Observation: ...                                          │
│      |                                                     │
│  [optionally: create_chart(data=..., type="bar")]          │
│      |                                                     │
│  Final Answer: structured business response with chart URL │
└────────────────────────────────────────────────────────────┘
```

**Key Design Decisions**:

1. Concise Thoughts enforced: system prompt limits Thoughts to 2 sentences. Without this, the model generates 5-sentence Thoughts adding 200+ extra output tokens per step — on a 10-step task that is 2,000 extra tokens at $0.015/1K = $0.03 wasted per question.

2. Structured SQL results: `run_sql` returns `{"rows": [...], "row_count": N, "columns": [...], "truncated": bool, "query_time_ms": T}` — never raw psycopg2 output. The `truncated` flag tells the model to add a `LIMIT` or aggregate when the result was cut.

3. Step limit with countdown injection: at step 8 of 15, the system injects "You have 7 steps remaining. Prioritize answering the core question." This prevents the model from exhaustively querying every dimension and failing to synthesize.

4. Repetition detection: the executor checks if the last SQL query is a substring of any prior query. If so, it injects "You already ran a similar query at step N and received [result]. Do not repeat — synthesize from existing results."

5. Chart creation as the final ReAct step: `create_chart` is always the last tool call before Final Answer. The system prompt instructs: "Only create one chart per question, after you have gathered all necessary data."

**Implementation**:

```python
REACT_SYSTEM_PROMPT = """You are a data analysis assistant with access to the company database.

Use this format strictly:
Thought: [1-2 sentences: what do you know, what gap are you filling]
Action: tool_name(arg1=value1, arg2=value2)
Observation: [tool result — provided by system]
... (repeat until you have enough data)
Thought: I now have sufficient data to answer the question.
Final Answer: [structured business answer with key metrics]

Rules:
- Keep each Thought to 1-2 sentences maximum
- Your Action must directly follow from your Thought
- Reference specific numbers from the most recent Observation in your next Thought
- Create at most one chart per question
- If you have 3 steps remaining, stop querying and synthesize
"""

tools = [
    {
        "name": "run_sql",
        "description": (
            "Execute a read-only SQL query against the analytics database. "
            "Use for retrieving metrics, counts, and aggregations. "
            "Results are truncated at 500 rows — use aggregations for large tables. "
            "Do NOT use for mutations (INSERT, UPDATE, DELETE are blocked)."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Valid PostgreSQL SELECT statement"},
                "description": {"type": "string", "description": "One sentence: what this query answers"}
            },
            "required": ["query", "description"]
        }
    },
    {
        "name": "create_chart",
        "description": (
            "Generate a chart from structured data and return a URL. "
            "Call only after data is fully gathered — this is typically the last tool call. "
            "Use for bar, line, or pie charts of final result data."
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "data": {"type": "object", "description": "Data to visualize as {labels: [...], values: [...]}"},
                "chart_type": {"type": "string", "enum": ["bar", "line", "pie"]},
                "title": {"type": "string"}
            },
            "required": ["data", "chart_type", "title"]
        }
    }
]

async def react_data_agent(question: str, max_steps: int = 15) -> str:
    messages = [
        {"role": "system", "content": REACT_SYSTEM_PROMPT},
        {"role": "user", "content": question}
    ]
    prior_queries = []

    for step in range(max_steps):
        # Inject step countdown at 60% of budget
        if step == int(max_steps * 0.6):
            messages.append({
                "role": "system",
                "content": f"[SYSTEM: {max_steps - step} steps remaining. Prioritize synthesizing your answer.]"
            })

        response = await llm.ainvoke(messages, tools=tools)

        if response.stop_reason == "end_turn":
            return extract_final_answer(response)

        # Repetition detection
        tool_call = extract_tool_call(response)
        if tool_call.name == "run_sql":
            query = tool_call.args["query"]
            for prior in prior_queries:
                if similarity(query, prior) > 0.85:
                    result = {"status": "skipped",
                              "reason": f"Similar query already executed at a prior step.",
                              "suggestion": "Synthesize from existing results."}
                    break
            else:
                result = await execute_sql(query)
                prior_queries.append(query)
        else:
            result = await execute_tool(tool_call)

        messages.append({"role": "assistant", "content": response.content})
        messages.append({"role": "tool", "tool_call_id": tool_call.id,
                         "content": json.dumps(result)})

    return synthesize_partial_results(messages)
```

**Results**:

- Average steps per question: 6.8 (target was <= 10)
- Question success rate (analyst rated as correct): 89%
- Average cost per question: $0.041 (well within $0.10 budget)
- Repetition loop incidents: 0.3% of questions after repetition detection added (was 4.1% before)
- P95 latency: 28 seconds (acceptable for async analyst workflow)

**Tradeoffs and Alternatives**:

- Plan-and-Execute was considered: rejected because analyst follow-up questions mid-session make upfront planning unreliable — ReAct's adaptability is more valuable here.
- Streaming intermediate Thoughts to the analyst UI was added after launch — analysts report significantly higher trust in results when they can see the Thought-Action-Observation trace in real time.
- Self-consistency (N=3 runs, majority vote) was prototyped for critical metrics queries — improved accuracy by ~7% but tripled cost; not deployed for general use; available as an optional "high confidence" mode.
