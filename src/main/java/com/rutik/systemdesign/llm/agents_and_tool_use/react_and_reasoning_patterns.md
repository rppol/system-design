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

---

## Best Practices

1. **Use ReAct as the default**: for any agent with more than one tool, the structured Thought-Action-Observation loop consistently outperforms direct tool selection.
2. **Add Reflexion for verifiable tasks**: when success is measurable (tests pass/fail, fact is correct/wrong), Reflexion's retry-with-reflection loop substantially improves completion rates.
3. **Constrain ToT with beam search**: never use BFS with large branching factors; beam width 2-3 with depth 3-4 captures most of the quality gain at manageable cost.
4. **Log all Thought fields**: the reasoning traces are essential for debugging agent mistakes and building evaluation datasets.
5. **Inject step counts into the Thought context**: "You have used 6 of 15 allowed steps" — prevents the model from not noticing when it's approaching the iteration limit.
6. **Test for observation-ignoring**: create test cases where the tool returns "not found" and verify the agent's next Thought acknowledges this; hallucinated observations are a common failure mode.
