# Plan and Execute

## Concept Overview

Plan-and-Execute is a two-phase agent architecture that separates task planning from task execution. Phase 1: a Planner LLM generates a complete, structured plan — a numbered sequence of steps to accomplish the goal. Phase 2: an Executor Agent works through the plan step-by-step, calling tools and completing each step in order. The phases use different prompts, different system roles, and often different models.

Unlike ReAct where planning and acting are interleaved moment-to-moment, Plan-and-Execute commits to a high-level structure upfront. This makes it more reliable for tasks with known structure, and easier to validate and monitor — but it requires a replanning mechanism for when the plan becomes stale.

---

## Intuition

> **One-line analogy**: Plan-and-Execute is like a general issuing battle orders before the engagement — broad strategy committed upfront, with staff officers (executors) handling moment-to-moment tactics.

**Mental model**: ReAct agents are reactive — they decide what to do one step at a time as new information arrives. This works for short tasks but can drift on long tasks: the agent may lose sight of the original goal after 15 tool calls. Plan-and-Execute provides a structural anchor: the plan defines what success looks like at each milestone. Executors have clear, scoped sub-tasks rather than open-ended goals.

**Why it matters**: For long-horizon tasks (10+ steps), a structured plan reduces the probability of the agent going off-track. The plan also provides a natural checkpointing mechanism: after each step, you can verify that the step's output matches expectations before proceeding.

**Key insight**: The hardest LLM tasks fail not because individual steps are wrong, but because the agent loses its strategic direction over many steps. An explicit plan keeps the goal visible even 20 steps in.

---

## Core Principles

- **Separation of concerns**: The Planner excels at strategic decomposition; the Executor excels at individual tool use. Using each for its strength produces better results than one LLM doing both.
- **Upfront structure vs emergent structure**: Plan-and-Execute commits to structure early; ReAct discovers structure as it goes. Neither is universally better — the right choice depends on task predictability.
- **Replanning is mandatory**: Plans become stale when execution reveals unexpected information. A system without replanning will blindly execute an invalid plan.
- **Step validation**: Verifying each step's output before proceeding prevents compounding errors and enables early failure detection.
- **Cost model awareness**: Planning uses one expensive LLM call; execution uses N cheaper calls. Assign your best model to planning.

---

## How It Works — Detailed Mechanics

### Planner Prompt Structure

```python
PLANNER_SYSTEM_PROMPT = """You are a strategic planner. Given a task, produce a
numbered, concrete execution plan.

Requirements:
- Each step must be atomic and actionable (one tool call or one synthesis step)
- Steps must be ordered by dependency (later steps may depend on earlier ones)
- Identify which steps can run in parallel (mark with [PARALLEL])
- Include a final synthesis step that produces the deliverable
- Be specific: instead of "research the topic", write "search for [specific query]"

Output format:
Step 1: [action description] — Expected output: [what success looks like]
Step 2: [action description] — Expected output: [what success looks like]
...
Step N: Synthesize findings → Final answer/deliverable

Example task: "Write a competitive analysis of Stripe vs Braintree"
Step 1: Search for Stripe's pricing, features, and market position — Expected output: structured data on Stripe
Step 2: Search for Braintree's pricing, features, and market position — Expected output: structured data on Braintree
Step 3: [PARALLEL with 2] Search for recent news about both companies — Expected output: news snippets
Step 4: Synthesize into comparison table and analysis — Expected output: structured document
"""

def plan_task(task: str) -> list[str]:
    response = planner_llm.invoke([
        SystemMessage(PLANNER_SYSTEM_PROMPT),
        HumanMessage(f"Task: {task}")
    ])
    return parse_plan(response.content)  # returns list of step strings
```

### Executor Prompt Structure

```python
EXECUTOR_SYSTEM_PROMPT = """You are a precise executor. You are given one step
from a larger plan and the context of what has been completed so far.

Your job:
- Complete ONLY the assigned step — do not do other steps
- Use the available tools as needed
- Return a structured result that matches the expected output
- If you cannot complete the step, report the specific blocker

Current step: {current_step}
Previous steps completed: {completed_steps_summary}
Available tools: {tool_list}
"""

def execute_step(step: str, context: dict, tools: list) -> dict:
    response = executor_llm.invoke(
        [SystemMessage(EXECUTOR_SYSTEM_PROMPT.format(
            current_step=step,
            completed_steps_summary=context["summary"],
            tool_list=format_tools(tools)
        ))],
        tools=tools
    )
    return {"step": step, "result": response.content, "status": "complete"}
```

### Replanning Logic

```python
def should_replan(plan: list[str], step_index: int,
                  step_result: dict, original_task: str) -> bool:
    """Determine if the plan is still valid after this step's result."""

    # Trigger 1: Step failed completely
    if step_result["status"] == "failed":
        return True

    # Trigger 2: Result contradicts plan assumption
    # Ask planner to evaluate if plan is still valid
    check_prompt = f"""
    Original plan step {step_index}: {plan[step_index]}
    Expected output: {extract_expected_output(plan[step_index])}
    Actual result: {step_result["result"]}
    Remaining plan: {plan[step_index+1:]}

    Is the remaining plan still valid given this result? Answer YES or NO.
    If NO, explain the specific assumption that was violated.
    """
    verdict = planner_llm.invoke(check_prompt)
    return "NO" in verdict.upper()

def replan(original_task: str, completed_steps: list[dict],
           remaining_steps: list[str], failure_reason: str) -> list[str]:
    """Generate a new plan for remaining work."""
    context = summarize_completed_steps(completed_steps)

    prompt = f"""Task: {original_task}

Completed work: {context}
Original remaining plan: {remaining_steps}
Why original plan is invalid: {failure_reason}

Generate a new plan for the remaining work, accounting for what has been
discovered. You do not need to redo completed steps."""

    return parse_plan(planner_llm.invoke(prompt))
```

### Full Plan-and-Execute Loop

```python
def plan_and_execute(task: str, tools: list, max_replan: int = 3) -> str:
    # Phase 1: Planning
    plan = plan_task(task)
    print(f"Initial plan: {plan}")

    completed_steps = []
    replan_count = 0

    # Phase 2: Execution
    step_index = 0
    while step_index < len(plan):
        step = plan[step_index]

        # Execute current step
        context = {"summary": summarize_completed_steps(completed_steps)}
        step_result = execute_step(step, context, tools)
        completed_steps.append(step_result)

        # Validate: should we replan?
        if should_replan(plan, step_index, step_result, task):
            if replan_count >= max_replan:
                return synthesize_partial_results(completed_steps)

            failure_reason = diagnose_failure(step_result, plan[step_index])
            new_remaining = replan(task, completed_steps,
                                   plan[step_index+1:], failure_reason)
            plan = plan[:step_index+1] + new_remaining
            replan_count += 1

        step_index += 1

    # Phase 3: Final synthesis
    return synthesize_final_answer(task, completed_steps)
```

### Hierarchical Task Network (HTN) Decomposition

```
HTN: decompose tasks into sub-tasks recursively until primitive actions

Level 0 (goal):       "Build a marketing dashboard"
                       |
Level 1 (sub-goals):  +-- "Set up data pipeline"
                       |   |
Level 2 (tasks):       |   +-- "Connect to analytics DB"
                       |   +-- "Define metrics schema"
                       |   +-- "Set up ETL jobs"
                       |
                       +-- "Build visualization layer"
                           |
                           +-- "Design dashboard layout"
                           +-- "Implement charts"
                           +-- "Add filtering/drill-down"

Each task is decomposed until it reaches a "primitive task" —
a single tool call or direct action the executor can perform.

Implementation: Planner produces nested JSON structure;
Orchestrator walks the tree, sending leaf tasks to executors.
```

### Comparison: Plan-and-Execute vs ReAct

```
ReAct:
  Planner and executor are the same LLM call
  No upfront plan; decisions made step by step
  Adapts to new information naturally
  Struggles with long-horizon tasks (goal drift after 15+ steps)
  Easier to implement (single loop)

Plan-and-Execute:
  Planner and executor are separate prompts (often separate models)
  Upfront plan provides strategic anchor
  Requires replanning mechanism when plan becomes stale
  More reliable for tasks with known structure
  Enables parallel execution of independent steps
  Easier to monitor and checkpoint

When ReAct wins:
  - Short tasks (< 10 steps)
  - Highly unpredictable tasks (can't plan ahead meaningfully)
  - Interactive tasks (user provides feedback mid-task)

When Plan-and-Execute wins:
  - Long tasks (10+ steps)
  - Tasks with known structure (research → analyze → write)
  - Tasks needing parallel execution of independent sub-tasks
  - Compliance requirements (must show a plan for approval before execution)
```

---

## Architecture Diagrams

### Two-Phase Architecture

```
INPUT: Task
    |
    v
┌──────────────────────────────┐
│  PHASE 1: PLANNER            │
│  Model: GPT-4o / Claude Opus │
│  Prompt: Strategic planning  │
│                              │
│  Output:                     │
│    Step 1: [...]             │
│    Step 2: [...]             │
│    Step N: [synthesize]      │
└──────────────────────────────┘
    |
    v
┌──────────────────────────────┐
│  PHASE 2: EXECUTION LOOP     │
│                              │
│  for step in plan:           │
│    ┌─────────────────────┐   │
│    │ EXECUTOR            │   │
│    │ Model: GPT-4o-mini  │   │
│    │ Prompt: Execute     │   │
│    │        this step    │   │
│    │ Tools available     │   │
│    └─────────────────────┘   │
│         |                    │
│    [Validate step output]    │
│         |                    │
│    [Should replan?] ─YES──► [REPLAN]
│         |                    │
│         NO                   │
│         ↓                    │
│    [next step]               │
└──────────────────────────────┘
    |
    v
┌──────────────────────────────┐
│  PHASE 3: SYNTHESIS          │
│  Combine all step outputs    │
│  Produce final deliverable   │
└──────────────────────────────┘
    |
    v
OUTPUT: Final answer
```

### LangGraph Plan-and-Execute Pattern

```python
from langgraph.graph import StateGraph, END
from typing import TypedDict, List, Optional

class PlanExecuteState(TypedDict):
    task: str
    plan: List[str]
    current_step_index: int
    completed_steps: List[dict]
    replan_count: int
    final_answer: Optional[str]

def planner_node(state: PlanExecuteState) -> PlanExecuteState:
    plan = plan_task(state["task"])
    return {"plan": plan, "current_step_index": 0}

def executor_node(state: PlanExecuteState) -> PlanExecuteState:
    step = state["plan"][state["current_step_index"]]
    result = execute_step(step, state["completed_steps"], tools)
    return {
        "completed_steps": state["completed_steps"] + [result],
        "current_step_index": state["current_step_index"] + 1
    }

def replanner_node(state: PlanExecuteState) -> PlanExecuteState:
    new_plan = replan(state["task"], state["completed_steps"],
                      state["plan"][state["current_step_index"]:], "plan stale")
    return {"plan": state["plan"][:state["current_step_index"]] + new_plan,
            "replan_count": state["replan_count"] + 1}

def route_after_step(state: PlanExecuteState) -> str:
    if state["current_step_index"] >= len(state["plan"]):
        return "synthesize"
    if state["replan_count"] > 3:
        return "synthesize"  # too many replans; stop
    last_result = state["completed_steps"][-1]
    if last_result["status"] == "failed":
        return "replan"
    return "execute"

graph = StateGraph(PlanExecuteState)
graph.add_node("plan", planner_node)
graph.add_node("execute", executor_node)
graph.add_node("replan", replanner_node)
graph.add_node("synthesize", synthesizer_node)

graph.set_entry_point("plan")
graph.add_edge("plan", "execute")
graph.add_conditional_edges("execute", route_after_step,
    {"execute": "execute", "replan": "replan", "synthesize": "synthesize"})
graph.add_edge("replan", "execute")
graph.add_edge("synthesize", END)

app = graph.compile()
```

---

## Real-World Examples

### Devin (Cognition AI)

Devin uses implicit Plan-and-Execute for software engineering tasks:
- On receiving a GitHub issue, Devin generates a high-level plan visible in its UI: "1. Reproduce bug, 2. Identify root cause, 3. Write fix, 4. Write tests, 5. Verify"
- Each step is executed with a sub-agent (terminal, browser, editor)
- Automatic replanning when a step reveals unexpected codebase structure
- Human can inject feedback at plan step boundaries
- Average: 5-8 steps per issue, ~$0.50-$2 in API costs per issue

### OpenAI o1's Internal Chain of Thought

o1's extended reasoning is structurally similar to Plan-and-Execute:
- The model generates a hidden "thinking" phase (planning) before visible output
- Thinking budget: 1,000-100,000 tokens depending on task difficulty
- This internal planning dramatically improves performance on math (AIME), coding (SWE-bench), and multi-step reasoning
- Not user-configurable; the model manages its own thinking budget

### LangGraph's Plan-and-Execute Template

LangGraph provides a reference implementation:
- GitHub: `langgraph/examples/plan-and-execute`
- Uses GPT-4 for planning, GPT-3.5-turbo for execution (cost optimization)
- Includes replanning trigger and human-in-the-loop approval gate
- Used as the foundation for many production research agents

---

## Tradeoffs

| Dimension | ReAct | Plan-and-Execute |
|-----------|-------|-----------------|
| Long tasks (20+ steps) | Poor (goal drift) | Good (plan anchor) |
| Short tasks (<10 steps) | Good | Overhead not worth it |
| Unpredictable tasks | Good (adapts) | Poor (plan stales quickly) |
| Parallelism | None (sequential) | High (parallel steps) |
| Transparency | Medium (trace) | High (explicit plan) |
| Replanning complexity | N/A | Required, non-trivial |
| Implementation complexity | Low | High |
| Initial latency | Low | High (planning call) |
| Cost (same task) | Similar | Higher (planning LLM call) |

---

## When to Use / When NOT to Use

### Use Plan-and-Execute When:
- Task has 10+ steps with known structure (research → analyze → write)
- Task benefits from parallel execution of independent sub-tasks
- Stakeholders need to review or approve a plan before execution
- Task has natural checkpoints where output should be validated
- Context management is critical (long tasks where ReAct would accumulate too much context)

### Avoid Plan-and-Execute When:
- Task is short or unpredictable (planning overhead not justified)
- The task requires rapid adaptation (plan stales every 2-3 steps — just use ReAct)
- Interactive tasks where user feedback modifies the task mid-execution
- Budget constraints prohibit the additional planning call

---

## Common Pitfalls

1. **No replanning mechanism**: Deploying Plan-and-Execute without a replanning trigger. If the first search returns no results, the agent blindly executes steps 2-10 of a now-invalid plan. Always implement `should_replan()` after each step.

2. **Planner ignores executor capabilities**: Planner says "search academic databases" but executor only has a web search tool. The planner prompt must include available tools so the plan uses only executable actions.

3. **Plan too granular or too coarse**: Plans with 50 micro-steps lose the strategic clarity that makes the pattern valuable. Plans with 3 vague mega-steps give executors insufficient guidance. Optimal: 5-15 concrete, atomic steps.

4. **Unlimited replanning**: A plan-and-execute system can cycle in replan loops. Cap replanning at 2-3 iterations; after that, return best partial answer or escalate to human.

5. **Synthesis step underspecified**: Many implementations plan well but have a vague "synthesize results" final step. The synthesis step should specify format, required sections, and quality criteria explicitly.

---

## Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **LangGraph** | Plan-and-execute implementation | Reference template; stateful |
| **LangChain Plan-and-Execute** | Legacy implementation | Deprecated; use LangGraph |
| **OpenAI o1/o3** | Built-in planning | Internal CoT is implicit P&E |
| **Claude extended thinking** | Built-in planning | `thinking` parameter |
| **BabyAGI** | Task queue management | Early Plan-and-Execute variant |
| **Devin** | Software engineering P&E | Commercial; explicit plan UI |

---

## Interview Questions with Answers

**Q: What is Plan-and-Execute and how does it differ from ReAct?**
A: Plan-and-Execute separates task planning from task execution into two distinct phases. The Planner LLM generates a complete numbered plan before any action is taken. The Executor Agent then works through the plan step-by-step. In ReAct, planning and acting are interleaved — the agent decides its next action after each observation without a global plan. Plan-and-Execute provides a structural anchor for long tasks, enables parallel execution of independent steps, and gives stakeholders a plan to review before execution. ReAct is more adaptive and simpler to implement. Choose Plan-and-Execute for tasks with 10+ steps and known structure; ReAct for shorter tasks and unpredictable environments.

**Q: When does a plan become stale and how do you trigger replanning?**
A: A plan becomes stale when execution reveals that an assumption the planner made is false. Triggers: (1) a step fails completely (tool returns error or no results); (2) a step's actual output differs materially from the expected output — e.g., "find contact email" returns "no email found" when later steps assume an email exists; (3) new information contradicts a plan assumption — "research competitor X" reveals competitor X was acquired and no longer operates as planned. Detection: after each step, a lightweight validation call checks whether the remaining plan is still valid. Recovery: invoke the replanner with all completed context plus the failure reason; it generates a new plan for remaining work without redoing completed steps.

**Q: What is Hierarchical Task Network decomposition?**
A: HTN decomposes a high-level goal into sub-goals recursively until every task is a "primitive task" — an atomic action executable by a single tool call. Structure: a tree where non-leaf nodes are goals/sub-goals and leaf nodes are executable actions. The planner works top-down (goal → sub-goals → tasks); the executor works bottom-up (leaf tasks → synthesize → sub-goal complete → goal complete). HTN is useful for complex multi-domain tasks (e.g., "build a marketing dashboard" decomposes into data pipeline, visualization, and reporting sub-trees). The challenge: the LLM planner must produce a valid HTN without cycles or dead ends; this requires careful prompting and validation.

**Q: How do you parallelize steps in a Plan-and-Execute system?**
A: Steps marked as independent (no data dependency between them) can execute simultaneously. The planner identifies parallel opportunities and marks them: "Step 2 [PARALLEL with Step 3]: search competitor A" and "Step 3 [PARALLEL with Step 2]: search competitor B." The orchestrator groups parallel steps and executes them with `asyncio.gather()` or a thread pool. The executor receives its specific step with its specific context; results are collected and merged before Step 4 proceeds. Parallelism speedup: if 3 steps each take 10s and are independent, parallelism reduces wall time from 30s to 10s. Implementation note: each parallel executor needs isolated context — ensure they don't share mutable state.

**Q: How do you choose the right model for planner vs executor roles?**
A: The planner requires strong strategic reasoning — assign the highest-quality model available (GPT-4o, Claude Opus, o1). Planning quality directly determines overall task success; the additional cost is worth it. The executor handles focused, scoped sub-tasks — a smaller, cheaper model (GPT-4o-mini, Claude Haiku) is often sufficient. Cost impact: if you have a 15-step plan with an expensive planner ($0.05) and cheap executors ($0.002/step = $0.03 total), total cost is $0.08 vs. using a large model for everything ($0.05 × 15 = $0.75). The quality trade-off: executor model quality matters most for complex tool use and reasoning-heavy steps; routing critical steps to the large model is a valid optimization.

**Q: What is the maximum number of steps before Plan-and-Execute breaks down?**
A: Context accumulation is the primary limit: each completed step's result is added to the context, and after 20-30 steps, the context window is nearly full (~100K tokens). Mitigations: (1) summarize step results rather than including full outputs (30-word summary vs. 500-word raw result); (2) hierarchical execution — the top-level plan has 5 sub-goals, each sub-goal runs its own plan with 5-10 steps, and only summaries bubble up; (3) external state storage — persist step results to disk or vector DB, retrieve only what the current step needs. Practically, 10-20 steps is the reliable range without these optimizations; with hierarchical execution and summarization, 50-100 steps is achievable.

**Q: How does step validation work and what happens when a step's output doesn't match expectations?**
A: Each plan step should specify expected output: "Expected output: structured list of company name, founding year, funding rounds." After execution, a validation check compares the actual result against this specification. Validation methods: (1) schema check — if expected output is JSON, validate against a schema; (2) completeness check — a lightweight LLM call: "Does this result satisfy: [expected output]? YES or NO"; (3) programmatic checks for specific assertions (e.g., non-empty result). If validation fails: (a) retry the step with a modified prompt once; (b) trigger replanning with the failure reason as context; (c) proceed with partial output and note the gap. Never silently ignore failed step validation — it will corrupt all downstream steps.

**Q: How do you handle human-in-the-loop in a Plan-and-Execute system?**
A: LangGraph's `interrupt_before` and `interrupt_after` mechanisms enable precise human approval gates. Common patterns: (1) Plan approval: after the planner generates a plan, pause and present it to a human before executing; (2) Step approval: for high-risk steps (delete file, send email, make purchase), interrupt before execution and require human confirmation; (3) Exception handling: when a step fails and replanning is triggered, show the human the new plan before proceeding. Implementation: `graph.compile(interrupt_before=["execute_risky_step"])` — the graph pauses at that node, surfaces state to the user, resumes when the user approves via `graph.update_state()`. The `checkpointer` persists state between the interrupt and resume, enabling asynchronous approval workflows.

**Q: What is the cost model of Plan-and-Execute compared to direct ReAct?**
A: Plan-and-Execute has an additional planning call (typically 1,000-3,000 tokens with a large model). At GPT-4o pricing ($5/1M input): planning call costs ~$0.005-$0.015. For a 15-step task using GPT-4o-mini executors (~1K tokens/step × $0.60/1M): execution costs ~$0.009. Total P&E: ~$0.015-$0.025. ReAct with the same large model for all steps: 15 steps × 2K tokens × $5/1M = ~$0.15. P&E is typically cheaper because it routes executor work to smaller models. However, if replanning triggers multiple times, each replan adds another large-model call — keep replan count ≤ 3.

**Q: How do you validate a generated plan before beginning execution?**
A: Plan validation runs immediately after the planner generates the plan and before any executor call. Validation checks: (1) tool availability — every action in the plan must reference a tool that exists; if the plan says "query the analytics database" but no `run_sql` tool is available, flag the step before wasting execution budget; (2) dependency ordering — verify that no step references information that will only be produced by a later step; (3) completeness — does the plan include a synthesis step that produces the required deliverable format?; (4) step count sanity — a plan with 50 steps is likely over-decomposed; a plan with 2 vague steps is under-specified; (5) parallelism validity — verify that steps marked [PARALLEL] have no data dependency between them. Implementation: a lightweight validator LLM call ("Does this plan have any obvious issues? List problems.") or deterministic checks against the tool registry. A bad plan caught before execution saves the cost of all executor calls that would run before the inevitable failure.

**Q: How do you revise a plan dynamically when a step fails mid-execution?**
A: Dynamic plan revision (replanning) is triggered when a step's actual output contradicts the expectation embedded in the plan. The replanner receives: (a) the original task, (b) a summary of all completed steps and their results, (c) the remaining original plan, (d) the specific failure reason. It outputs a revised plan for remaining work only — completed steps are not redone. Key design: the replanner must be told explicitly what assumption the original plan made that is now violated. Example: original plan assumed "search for company X's recent funding" would find results; step 2 found "company X was acquired in 2023 and no longer files public reports." The replanner rewrites remaining steps to search for the acquiring company's records instead. Without this context, the replanner generates a nearly identical plan. Cap replanning at 2-3 iterations — a plan that keeps failing likely indicates the task is not achievable with available tools, not that better planning will help.

**Q: What is optimal plan granularity — how coarse or fine should plan steps be?**
A: Each plan step should map to a single executor invocation — approximately one tool call or one synthesis action. Too coarse: "Research all competitors" as one step gives the executor insufficient guidance on which competitors, which sources, and what format; the executor must make many implicit decisions, leading to inconsistent results across runs. Too fine: "Search Google for Stripe pricing page URL, click the first result, read the pricing table, copy the table to text" as four micro-steps creates a 50-step plan for a 10-step task, makes replanning expensive (one failure cascades to many steps), and fills the plan with orchestration noise rather than strategic content. Optimal granularity: "Search for Stripe's pricing page and extract all tier names and prices as structured JSON — Expected output: {tier: string, monthly_price: number, features: [string]}[]". This gives the executor enough guidance to complete the step reliably while preserving planner authority over strategy.

**Q: What are the known limitations of LLM planning and how do they affect plan quality?**
A: LLMs have four consistent planning failure modes: (1) optimism bias — the planner assumes every step will succeed and doesn't include contingency branches; fix by prompting "For each step, briefly note what to do if it fails or returns no results"; (2) tool hallucination — the planner invents tool names that don't exist ("use the competitor_analysis_api"); fix by including the exact tool list in the planner prompt; (3) dependency blindness — the planner marks steps as [PARALLEL] when they actually share a data dependency; fix with a dependency validation pass after plan generation; (4) scope creep — plans for open-ended research tasks expand to 30+ steps as the planner tries to be thorough; fix with a step budget cap in the planner prompt ("Generate a plan with at most 12 steps"). Current frontier models (GPT-4o, Claude 3.5 Sonnet) produce plans with ~85% step validity on structured tasks; on open-ended tasks, validity drops to 60-70%, making replanning more common.

**Q: How does Plan-and-Execute compare to pure ReAct on long multi-step tasks?**
A: On tasks requiring 15+ steps, Plan-and-Execute outperforms ReAct on goal adherence (the agent completes the intended task rather than drifting to a related but different task), completeness (all required sections produced), and efficiency (fewer redundant steps because the planner identifies which work is needed upfront). ReAct outperforms Plan-and-Execute on adaptability (tasks where new information dramatically changes what should be done next), latency-to-first-result (ReAct produces partial results after each step; P&E waits for the whole plan), and simplicity of implementation. Empirically, for research and report generation tasks (10-20 steps with known structure), Plan-and-Execute achieves ~20-30% higher quality ratings in human evaluation. For debugging and exploratory tasks (unknown structure, requiring many hypothesis-test cycles), ReAct is more effective. The practical recommendation: start with ReAct; migrate to Plan-and-Execute when tasks consistently exceed 10 steps and analysts report that the agent "lost the thread."

**Q: What is the cost of the planning step itself and when is it not worth paying?**
A: A planning call uses a large model (GPT-4o, Claude Opus) and generates 300-800 tokens of plan text at ~1,500-2,500 input tokens of context. At GPT-4o pricing: planning costs $0.008-$0.020 per task. This overhead is not worth paying when: (a) the task is always 3-5 steps with obvious structure — "answer this question by searching and summarizing" needs no formal plan; (b) task success rate with pure ReAct is already above 90% — the planning overhead buys no quality improvement; (c) latency matters — the planning call adds 2-4 seconds of wall time before the first executor call begins. The overhead is worthwhile when: task quality with ReAct is below 80%, tasks frequently have parallel opportunities that ReAct cannot exploit, or the task has human approval gates where an explicit plan is required for the approval workflow. Rule of thumb: if the average task requires more than 8 steps, the planning overhead pays for itself in execution efficiency.

---

## Best Practices

1. **Always include expected output in each plan step**: "Expected output: [specific format/content]" — this drives validation and replanning.
2. **Mark parallel steps in the plan**: explicit parallelism markers make the orchestrator's job straightforward and document intent.
3. **Use the best model for planning**: planning quality determines overall success; never cut corners on the planner model.
4. **Cap replanning at 2-3 iterations**: unlimited replanning loops waste budget; after the cap, return best partial answer or escalate.
5. **Summarize step results before storing**: store concise summaries (50-100 words per step) rather than full outputs — prevents context window overflow on long plans.
6. **Test plan staleness explicitly**: inject deliberate failures in testing (tool returns empty result, unexpected data) and verify replanning triggers correctly.

---

## 14. Case Study: Automated Code Migration Agent

**Problem Statement**: A fintech company needs to migrate 200,000 lines of Python 2 code across 140 modules to Python 3. Manual migration takes 3 developer-months. The migration must: identify affected files, map deprecated API calls to Python 3 equivalents, apply transforms, run the test suite, and roll back on failures. Mistakes in financial calculation code are unacceptable — correctness is more important than speed.

**Architecture Overview**:

```
Task: "Migrate module payments/core.py from Python 2 to Python 3"
      |
      v
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 1: PLANNER (Claude Opus)                                 │
│                                                                 │
│  Step 1: Read payments/core.py — Expected: file contents        │
│  Step 2: Identify Python 2 patterns — Expected: pattern list    │
│  Step 3: [PARALLEL] Look up migration for each pattern          │
│  Step 4: Apply transforms — Expected: modified file             │
│  Step 5: Run test suite — Expected: all tests pass              │
│  Step 6: If tests fail → diagnose and fix (max 2 retries)       │
│  Step 7: Generate migration report                              │
└─────────────────────────────────────────────────────────────────┘
      |
      v
┌─────────────────────────────────────────────────────────────────┐
│  PHASE 2: EXECUTOR LOOP (GPT-4o-mini per step)                  │
│                                                                 │
│  Step 1: read_file("payments/core.py")                          │
│  Step 2: analyze_patterns(content) → [print_stmt, dict_items,  │
│                                        unicode_str, xrange]     │
│  Step 3a: lookup_migration("print_stmt") → "print() function"  │
│  Step 3b: lookup_migration("dict_items") → ".items() returns   │
│            view, not list — wrap in list() if needed"           │
│  Step 4: apply_2to3(file, patterns) → modified_content         │
│  Step 5: write_file + run_tests → {pass:142, fail:3, error:0}  │
│        |                                                        │
│  [3 failures → REPLAN: diagnose why and add fix steps]         │
│        |                                                        │
│  Step 6: fix_test_failures(failure_details)                     │
│  Step 7: run_tests → {pass:145, fail:0, error:0}               │
│  Step 8: generate_migration_report()                            │
└─────────────────────────────────────────────────────────────────┘
      |
      v
Migration committed to git with report attached
```

**Key Design Decisions**:

1. Git checkpoint before any write: the first executor action is always `git_commit("pre-migration snapshot")`. If the migration produces an unrecoverable state, the rollback tool reverts to this checkpoint. This makes all write steps safe.

2. Replanning on test failure: if `run_tests` returns any failures, the executor does not proceed — it triggers a replan. The replanner receives the failure details and adds targeted fix steps before re-running tests. Test failure is not treated as a final outcome; it is treated as new information requiring plan revision.

3. Parallel pattern lookup: Python 2 modules typically have 4-8 distinct deprecated patterns. Looking them up sequentially wastes time; they are independent and run in parallel (step 3 is marked [PARALLEL]). This reduces pattern lookup from ~8s sequential to ~2s parallel.

4. Executor model routing: steps 1-2 (read + identify patterns) use GPT-4o-mini (cheap, low complexity). Step 4 (apply transforms with correctness requirements) uses GPT-4o (higher quality for code modification). Step 5 (test diagnosis) uses Claude Opus (best reasoning for understanding why tests fail in financial code).

5. Planner tool list constraint: the planner prompt includes the exact tool list: `read_file, write_file, run_tests, git_commit, git_rollback, apply_2to3, lookup_migration, generate_report`. The planner cannot hallucinate a `deploy_to_production` step because that tool does not exist.

**Implementation**:

```python
MIGRATION_PLANNER_PROMPT = """You are a Python 2→3 migration planner.
Generate a step-by-step migration plan for the given file.

Available tools: read_file, write_file, run_tests, git_commit,
                 git_rollback, apply_2to3, lookup_migration, generate_report

Requirements:
- First step must always be git_commit (checkpoint)
- Last step before reporting must be run_tests (all tests must pass)
- Mark independent lookup steps as [PARALLEL]
- Include rollback instructions for each write step
- Maximum 10 steps

Output format:
Step N: [action] — Expected output: [what success looks like]
         On failure: [what to do if this step fails]
"""

def migration_replan(completed_steps, test_failures, remaining_plan):
    """Called when run_tests returns failures."""
    failure_context = format_test_failures(test_failures)
    prompt = f"""
Migration is partially complete. Tests are failing.

Completed steps: {summarize_steps(completed_steps)}
Test failures: {failure_context}
Original remaining plan: {remaining_plan}

Generate a revised plan that:
1. Diagnoses why these specific tests fail
2. Applies targeted fixes
3. Re-runs tests to verify
4. Proceeds to reporting if tests pass

Do not redo already completed steps. Maximum 5 additional steps.
"""
    return planner_llm.invoke(prompt)

async def migrate_module(filepath: str) -> MigrationResult:
    # Phase 1: Plan with validation
    plan = planner.generate(task=f"Migrate {filepath} from Python 2 to Python 3")
    validate_plan(plan, available_tools=TOOLS)  # raises on tool hallucination

    completed = []
    replan_count = 0

    for step in plan:
        result = await executor.run(step, context=summarize(completed))
        completed.append(result)

        # Replanning trigger: test failures
        if step.tool == "run_tests" and result.failures:
            if replan_count >= 2:
                await git_rollback()
                return MigrationResult(status="failed", reason="max replans reached")
            new_steps = migration_replan(completed, result.failures, plan[plan.index(step)+1:])
            plan = plan[:plan.index(step)+1] + new_steps
            replan_count += 1

    return MigrationResult(status="success", steps_taken=len(completed))
```

**Results**:

- Migration success rate (all tests pass, no rollback): 91% of modules on first attempt
- Average steps per module: 8.3 (target was <= 10)
- Average cost per module: $0.18 (GPT-4o-mini executors, GPT-4o for transforms, Opus for diagnosis)
- Replanning triggered: 23% of modules (mostly due to implicit Python 2 assumptions in financial logic)
- Total 140-module migration: completed in 4 days vs. 3-month manual estimate
- Zero production incidents post-deployment (financial calculations verified by full test suite)

**Tradeoffs and Alternatives**:

- Pure ReAct was prototyped first: it achieved 74% success rate but frequently "lost the thread" after step 8 and began repeating pattern lookups without making progress. Plan-and-Execute improved success rate to 91%.
- Using `2to3` CLI directly without an LLM planner was considered for simple files: implemented as a fast path — files with only syntactic changes (print statements, integer division) skip the LLM planner entirely and use the CLI tool. The LLM planner handles only files with semantic changes.
- Human review gate was added after Step 4 (apply transforms) for files touching the core payment calculation engine — the plan pauses and emails a diff to the team lead before running tests. This added 2-4 hours per such file but eliminated the risk of merging semantically incorrect financial logic.
