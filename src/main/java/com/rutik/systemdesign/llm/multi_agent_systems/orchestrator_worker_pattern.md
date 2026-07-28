# Orchestrator-Worker Pattern — Deep Dive

---

## 1. Concept Overview

The orchestrator-worker pattern is a multi-agent architecture in which a central orchestrator agent dynamically decomposes a complex task into sub-tasks and delegates each to specialized worker agents. The orchestrator maintains a task list, tracks worker results, handles failures, and assembles a final output from partial results. Workers are narrow, focused, and stateless; the orchestrator carries all coordination state.

This pattern maps directly to how large software engineering teams operate: a tech lead (orchestrator) defines tickets and assigns them to engineers (workers) who specialize in backend, frontend, QA, or documentation. The tech lead integrates the pull requests into a coherent release.

The key engineering insight is that a single LLM context window is a fixed resource. Breaking a 50,000-token research task into ten 5,000-token sub-tasks — each run by a focused worker with clean context — produces better output than cramming everything into one call. Anthropic's published account of its multi-agent research system reports the quality side of this concretely: a system with a Claude Opus 4 lead agent and Claude Sonnet 4 subagents **outperformed single-agent Claude Opus 4 by 90.2%** on their internal research eval, and parallel tool calling **cut research time by up to 90% for complex queries**. It also reports the bill honestly, and in the opposite direction from what most write-ups assume: **multi-agent systems use about 15x more tokens than chat interactions** (single agents about 4x), and token usage alone explains 80% of the performance variance on their BrowseComp eval. Orchestrator-worker buys latency and quality; it does not, by itself, buy cheapness. See [Subagents & Delegation](../agents_and_tool_use/subagents_and_delegation.md) for the single-agent-spawning-subagents variant of the same idea.

---

## 2. Intuition

One-line analogy: The orchestrator is a project manager who writes the sprint board, assigns each ticket to the right specialist, and merges the PRs — workers just close tickets.

Mental model: Imagine planning a 200-page research report. A single expert could write every section sequentially (exhausting, slow, context degrades). Alternatively, an editor (orchestrator) creates an outline, assigns each chapter to a subject-matter expert (worker), collects drafts, and edits them into a coherent whole. Each expert works with focused domain context. The editor does not need to know quantum chemistry to coordinate a chapter on it — only to recognize whether the output is complete and well-formed.

Why it matters: Most real-world tasks that benefit from AI exceed a comfortable single-agent context window or reasoning budget. The orchestrator-worker pattern is the primary mechanism for scaling agent capability beyond these limits.

Key insight: The orchestrator's intelligence determines system-level quality. A weak orchestrator with strong workers produces incoherent results; a strong orchestrator with mediocre workers still produces acceptable output. Always use your highest-capability model for the orchestrator.

---

## 3. Core Principles

- **Separation of planning and execution**: The orchestrator plans; workers execute. Workers should never re-plan unless the orchestrator explicitly delegates planning authority.
- **Task idempotency**: Each worker call should be idempotent — re-running a worker with the same input should produce equivalent output. This enables safe retries.
- **Result schema enforcement**: Workers return structured outputs (JSON, typed dictionaries) rather than free-form prose. The orchestrator must be able to programmatically consume worker results.
- **Stateful orchestrator, stateless workers**: Workers receive their full context in each call. The orchestrator accumulates state across the entire task.
- **Graceful partial completion**: If some workers fail, the orchestrator assembles the best possible output from successful workers rather than failing entirely.
- **Auditability**: Every task dispatch and every worker result is logged with timestamps, agent IDs, and the full content exchanged.

---

## 4. Types / Architectures / Strategies

### 4.1 LLM Orchestrator (Dynamic Planning)

The orchestrator is itself an LLM. It reasons about the task, generates the sub-task list dynamically, evaluates worker outputs, and decides whether additional tasks are needed. This is the most flexible form but also the most expensive: every orchestrator decision is an LLM call.

Use when: tasks are unpredictable, sub-task count is not known upfront, or the orchestrator needs to adapt based on intermediate results.

Cost profile: 1 orchestrator call to plan + N worker calls + 1 orchestrator call to integrate = N+2 LLM calls minimum; typically N+5 to N+10 including error recovery loops.

### 4.2 Deterministic Orchestrator (Code-Based)

The orchestrator is Python/Java code that applies a fixed workflow: parse the input, generate a predefined task list, dispatch workers, collect results. Workers are LLMs; the orchestrator is not.

Use when: the task decomposition is well-understood and static (e.g., "always extract entities, then classify, then summarize"), latency and cost matter, and you want deterministic behavior.

Cost profile: N worker calls only. Significantly cheaper and faster.

### 4.3 Hybrid: Code Router + LLM Orchestrator

The top-level routing is deterministic code; within each branch, an LLM orchestrator handles dynamic sub-task planning. Common in production systems where the top-level pipeline is stable but individual stages require adaptive reasoning.

### 4.4 Parallel vs Sequential Dispatch

- **Parallel dispatch**: The orchestrator fires all workers simultaneously. Wall-clock time = slowest worker. Requires workers to be independent. Best for: research tasks, data enrichment, multi-section document generation.
- **Sequential dispatch**: Worker N receives output from Worker N-1. Each worker's input depends on the prior worker's output. Best for: code generation (requirements → design → code → tests), where later stages build on earlier artifacts.
- **Hybrid (DAG)**: Some workers are parallel; some are sequential. Model the task as a directed acyclic graph. [LangGraph](../agentic_frameworks/langgraph.md) implements this natively.

---

## 5. Architecture Diagrams

### Basic Orchestrator-Worker (Sequential)

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    Orch["Orchestrator\n(LLM or Code)\nTask list: research→draft→review"] --> W1
    W1["Research Worker\nGPT-4o-mini\nweb_search, fetch_url\nOutput: research_findings JSON"] --> Orch2
    Orch2["Orchestrator\nmark 1 done, start 2"] --> W2
    W2["Draft Writer Worker\nClaude Sonnet 5\nInput: research_findings\nOutput: draft_document Markdown"] --> Orch3
    Orch3["Orchestrator\nmark 2 done, start 3"] --> W3
    W3["Review Worker\nGPT-4o\nInput: draft_document\nOutput: review_result JSON"] --> Approved{"approved?"}
    Approved -- YES --> Result([Return draft])
    Approved -- "NO (with comments)" --> W2

    classDef io     fill:#282c34,stroke:#61afef,color:#abb2bf
    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef llm    fill:#1e2127,stroke:#c678dd,color:#abb2bf
    classDef decide fill:#1e2127,stroke:#e5c07b,color:#abb2bf

    class Result io
    class Orch,Orch2,Orch3 llm
    class W1,W2,W3 proc
    class Approved decide
```

### Parallel Dispatch (Fan-Out / Fan-In)

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    Orch([Orchestrator]) --> W1 & W2 & W3
    W1["Worker 1\nSection 1 research"] --> FanIn
    W2["Worker 2\nSection 2 research"] --> FanIn
    W3["Worker 3\nSection 3 research"] --> FanIn
    FanIn["Orchestrator — Fan-In\nmerge 3 result sets"] --> Output([Final output])

    classDef io     fill:#282c34,stroke:#61afef,color:#abb2bf
    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef llm    fill:#1e2127,stroke:#c678dd,color:#abb2bf

    class Orch,Output io
    class W1,W2,W3 proc
    class FanIn llm
```

### Error Recovery Loop

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    Dispatch["Orchestrator dispatches Worker A"] --> WorkerA["Worker A"]
    WorkerA -- "timeout 60s exceeded" --> Retry{"attempts < 2?"}
    Retry -- YES --> WorkerA
    WorkerA -- success --> Continue([Continue to next task])
    Retry -- NO --> Fallback{"Fallback strategy"}
    Fallback -- "use Worker B (simpler model)" --> WorkerB["Worker B"]
    Fallback -- "skip task" --> Partial([Partial result])
    Fallback -- "escalate" --> Human([Escalate to human])
    WorkerB --> Continue

    classDef io     fill:#282c34,stroke:#61afef,color:#abb2bf
    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef llm    fill:#1e2127,stroke:#c678dd,color:#abb2bf
    classDef decide fill:#1e2127,stroke:#e5c07b,color:#abb2bf
    classDef warn   fill:#1e2127,stroke:#e06c75,color:#abb2bf

    class Continue,Partial,Human io
    class Dispatch,WorkerA,WorkerB proc
    class Retry,Fallback decide
```

### Task Ledger State Machine

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart LR
    PENDING --> DISPATCHED --> COMPLETED
    DISPATCHED --> FAILED --> RETRY --> COMPLETED
    RETRY -- "retry 2 also fails" --> ESCALATED

    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef warn   fill:#1e2127,stroke:#e06c75,color:#abb2bf
    classDef decide fill:#1e2127,stroke:#e5c07b,color:#abb2bf

    class PENDING,DISPATCHED,COMPLETED proc
    class FAILED,ESCALATED warn
    class RETRY decide
```

Task Ledger (orchestrator internal state):

| task_id | description          | worker    | status    | result |
|---------|-----------------------|-----------|-----------|--------|
| T001    | search arxiv papers  | research  | COMPLETED | {...}  |
| T002    | summarize paper 1    | summary   | COMPLETED | {...}  |
| T003    | summarize paper 2    | summary   | FAILED    | null   |
| T004    | merge summaries      | writer    | PENDING   | null   |

The task-ledger design is also the core of [Magentic-One](magentic_one_and_autogen_v04.md)'s dual-loop orchestrator (task ledger for the plan, progress ledger for per-step tracking).

---

## 6. How It Works — Detailed Mechanics

### Orchestrator as LLM (Dynamic Planning)

```python
from __future__ import annotations
import json
import asyncio
from typing import Any
from dataclasses import dataclass, field
from anthropic import AsyncAnthropic

client = AsyncAnthropic()

@dataclass
class Task:
    task_id: str
    description: str
    depends_on: list[str] = field(default_factory=list)
    status: str = "pending"   # pending | dispatched | completed | failed
    result: Any = None
    attempts: int = 0

@dataclass
class TaskLedger:
    tasks: dict[str, Task] = field(default_factory=dict)

    def add(self, task: Task) -> None:
        self.tasks[task.task_id] = task

    def ready_tasks(self) -> list[Task]:
        """Return tasks whose dependencies are all completed."""
        return [
            t for t in self.tasks.values()
            if t.status == "pending" and all(
                self.tasks[dep].status == "completed"
                for dep in t.depends_on
            )
        ]

    def all_done(self) -> bool:
        return all(t.status in ("completed", "failed") for t in self.tasks.values())

    def completed_results(self) -> dict[str, Any]:
        return {t.task_id: t.result for t in self.tasks.values() if t.status == "completed"}


async def run_worker(task: Task, context: dict[str, Any]) -> Any:
    """Stateless worker: receives full context, returns structured result."""
    system = (
        "You are a specialized research worker. "
        "Return your output as a JSON object with keys: 'summary', 'key_facts', 'confidence'."
    )
    user_msg = (
        f"Task: {task.description}\n\n"
        f"Context from prior tasks:\n{json.dumps(context, indent=2)}"
    )
    response = await client.messages.create(
        # Cheap worker tier. NOTE: claude-3-5-haiku-20241022 was RETIRED on
        # 2026-02-19 -- requests to it now fail. claude-haiku-4-5 is the successor.
        model="claude-haiku-4-5",
        max_tokens=2048,
        system=system,
        messages=[{"role": "user", "content": user_msg}],
    )
    raw = response.content[0].text
    # Workers must return JSON; validate here
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # Fallback: wrap raw text
        return {"summary": raw, "key_facts": [], "confidence": 0.5}


async def orchestrate(goal: str, max_retries: int = 2) -> str:
    """LLM orchestrator: plan tasks, dispatch workers, integrate results."""
    # Step 1: Orchestrator plans the task list
    plan_prompt = (
        f"Goal: {goal}\n\n"
        "Break this goal into 3-5 independent or sequentially dependent research subtasks. "
        "Return JSON: list of {task_id, description, depends_on (list of task_ids)}."
    )
    plan_response = await client.messages.create(
        model="claude-opus-5",   # expensive orchestrator model
        max_tokens=1024,
        messages=[{"role": "user", "content": plan_prompt}],
    )
    tasks_raw = json.loads(plan_response.content[0].text)

    ledger = TaskLedger()
    for t in tasks_raw:
        ledger.add(Task(
            task_id=t["task_id"],
            description=t["description"],
            depends_on=t.get("depends_on", []),
        ))

    # Step 2: Execute tasks respecting dependencies
    while not ledger.all_done():
        ready = ledger.ready_tasks()
        if not ready:
            break  # Deadlock or all done

        # Dispatch all ready tasks in parallel
        context = ledger.completed_results()
        async def dispatch(task: Task) -> None:
            task.status = "dispatched"
            task.attempts += 1
            try:
                result = await asyncio.wait_for(
                    run_worker(task, context),
                    timeout=60.0,
                )
                task.result = result
                task.status = "completed"
            except (asyncio.TimeoutError, Exception) as e:
                if task.attempts < max_retries:
                    task.status = "pending"   # retry
                else:
                    task.status = "failed"
                    task.result = {"error": str(e)}

        await asyncio.gather(*[dispatch(t) for t in ready])

    # Step 3: Orchestrator integrates results
    integration_prompt = (
        f"Goal: {goal}\n\n"
        f"Worker results:\n{json.dumps(ledger.completed_results(), indent=2)}\n\n"
        "Synthesize a final comprehensive answer."
    )
    final_response = await client.messages.create(
        model="claude-opus-5",
        max_tokens=4096,
        messages=[{"role": "user", "content": integration_prompt}],
    )
    return final_response.content[0].text


# Usage
# result = asyncio.run(orchestrate("Survey the current state of LLM reasoning benchmarks"))
```

### Deterministic Orchestrator (Code-Based, Cheaper)

```python
import asyncio
import json
from anthropic import AsyncAnthropic

client = AsyncAnthropic()

PIPELINE: list[dict] = [
    {"stage": "extract_entities",  "model": "claude-haiku-4-5",  "max_tokens": 512},
    {"stage": "classify_intent",   "model": "claude-haiku-4-5",  "max_tokens": 256},
    {"stage": "generate_response", "model": "claude-sonnet-5",   "max_tokens": 2048},
    {"stage": "review_response",   "model": "claude-haiku-4-5",  "max_tokens": 512},
]

SYSTEM_PROMPTS = {
    "extract_entities": "Extract all named entities. Return JSON: {entities: [...]}.",
    "classify_intent":  "Classify the user intent into one of [complaint, question, purchase, other]. Return JSON: {intent, confidence}.",
    "generate_response": "Generate a helpful customer service response. Return JSON: {response_text}.",
    "review_response":  "Review the response for accuracy and tone. Return JSON: {approved: bool, feedback: str}.",
}

async def run_stage(stage: str, model: str, max_tokens: int, accumulated: dict) -> dict:
    prompt = f"Input data:\n{json.dumps(accumulated, indent=2)}"
    response = await client.messages.create(
        model=model,
        max_tokens=max_tokens,
        system=SYSTEM_PROMPTS[stage],
        messages=[{"role": "user", "content": prompt}],
    )
    return json.loads(response.content[0].text)

async def run_deterministic_pipeline(user_input: str) -> str:
    accumulated: dict = {"user_input": user_input}
    for stage_config in PIPELINE:
        stage_result = await run_stage(**stage_config, accumulated=accumulated)
        accumulated[stage_config["stage"]] = stage_result
        # Early exit: if review rejected, loop back (simplified here)
        if stage_config["stage"] == "review_response" and not stage_result.get("approved"):
            # Re-run generate_response with feedback
            accumulated["review_feedback"] = stage_result["feedback"]
            regen = await run_stage("generate_response", "claude-sonnet-5", 2048, accumulated)
            accumulated["generate_response"] = regen
    return accumulated["generate_response"]["response_text"]
```

### Retry Safety — What "Idempotent Worker" Actually Buys You

The task-idempotency principle in §3 asks for idempotent workers, and the loops above lean on it
hard: the async
orchestrator resets a timed-out task to `pending` and re-dispatches it, and §12's malformed-output
answer retries twice before falling back. Both are only safe under a contract worth stating
precisely, because the literal reading of "same input produces the same output" is false for an
LLM — sampling is stochastic above temperature 0, and even at 0 you are not promised bit-identical
text across batches or model revisions.

The achievable contract is weaker and sufficient: **a retried worker must produce a result that is
schema-valid and interchangeable for the orchestrator's purpose, and must not double-apply any
side effect.** That splits cleanly in two:

- **Output equivalence** is what §3's schema enforcement is *for*. Two runs of a summariser return
  different prose; if both validate against `{summary: str, confidence: float, sources: [str]}`
  and the integration step consumes only those fields, the difference does not propagate. This is
  why schema enforcement and idempotency are one principle in practice, not two — the schema is
  the equivalence relation.
- **Side-effect safety** is not solved by schemas at all, and fan-out makes it worse. A 60-second
  timeout does not tell you the worker failed; it tells you no response arrived. A worker that
  filed a ticket at t=58s and timed out at t=60s files a second ticket on retry. In parallel
  dispatch this compounds, because a batch-level failure tempts you to re-dispatch the whole
  `asyncio.gather` set including the workers that already completed.

The task ledger is already the right place to fix this: it holds a stable identity for every
dispatch, so derive an idempotency key from `(run_id, task_id)` — attempt-invariant, so retry 2
carries the same key as attempt 0 — and require every side-effecting tool to deduplicate on it.
The mechanics (key derivation, cached results, TTL as a ceiling on workflow lifetime, Temporal
activity semantics) are developed in
[Durable & Long-Running Agents](../agents_and_tool_use/durable_long_running_agents.md); the case
where an action has **no inverse** and must therefore sit behind an approval gate rather than a
retry is developed in [Agent Reliability](../agents_and_tool_use/agent_reliability.md)'s treatment
of compensating actions. The orchestrator-specific rule is the cheap one: keep write-capable
workers out of the parallel fan-out where you can, and give read-only workers the aggressive
retry policy instead.

### What Anthropic Actually Published

Anthropic's engineering write-up on its multi-agent research system is the closest thing to a
primary source here. The figures it reports, verbatim in substance:

| Reported figure | Value |
|---|---|
| Multi-agent (Opus 4 lead + Sonnet 4 subagents) vs single-agent Opus 4, internal research eval | **+90.2%** |
| Token usage: agents vs chat | ~**4x** |
| Token usage: multi-agent vs chat | ~**15x** |
| Share of BrowseComp performance variance explained by token usage alone | **80%** |
| Research-time reduction from parallel tool calling, complex queries | up to **90%** |
| Task-completion-time reduction from improved tool descriptions | **40%** |

Anthropic publishes no wall-clock A/B, no absolute accuracy percentages, and no cost comparison.
The direction of the cost finding matters and is frequently reported backwards: because
multi-agent runs burn roughly 15x chat-level tokens, Anthropic's guidance is that the pattern
only pays where task value justifies that spend.

### Worked Cost Model (ILLUSTRATIVE — not measured data)

The numbers below are a self-consistent worked example built from current list prices, so you can
see how the arithmetic behaves. They are **not** a published benchmark and must not be cited as one.

```
Task: "Survey and summarize papers on speculative decoding published in a
       given window, with a structured comparison table."

Single agent (Claude Opus 5, $5 / $25 per MTok):
  Wall-clock time:   62 minutes (sequential tool calls)
  Context used:      187K input + 24K output (near the useful working limit)
  Cost:              187 x $5/1000  = $0.935
                      24 x $25/1000 = $0.600   ->  $1.54

Orchestrator-Worker (1 Opus 5 orchestrator + 8 Haiku 4.5 workers, $1 / $5 per MTok):
  Wall-clock time:   14 minutes
  Peak tokens/agent: 12K input (focused context per worker)
  Workers:           8 x (12K x $1/1M + 3K x $5/1M) = 8 x $0.027 = $0.216
  Plan call:         2K in / 1K out on Opus 5              = $0.035
  Integrate call:    8K in / 4K out on Opus 5              = $0.140
                                                    total ->  $0.39

  - 4.4x faster wall-clock
  - 75% lower cost in THIS configuration, because the 8 workers together read
    96K tokens rather than 187K AND read them on a 5x cheaper tier
  - fan out wider than the work you replaced and the sign flips: Anthropic's
    real systems land at ~15x chat token usage, not a saving
```

**In plain terms.** "Splitting the work across workers buys back wall-clock time in proportion
to how many run at once, but you always pay a fixed orchestration tax on top — so the speedup
is never the worker count."

The reason to write this out is that teams size fan-out by dividing the sequential time by the
worker count and then miss their latency target by the orchestration term they forgot.

```
  wall_clock  ~=  sequential_time / num_workers  +  orchestration_overhead
  speedup     =   sequential_time / wall_clock
  cost_change =  (cost_single - cost_multi) / cost_single
```

| Symbol | What it is |
|--------|------------|
| `sequential_time` | What one agent takes doing every sub-task back to back. Here 62 min (illustrative) |
| `num_workers` | How many workers actually run in parallel. Here 8 |
| `orchestration_overhead` | Planning call + integration call + dispatch/collect. Not divisible |
| `wall_clock` | Time the user waits. Here 14 min |
| `speedup` | Wall-clock ratio, not a token ratio — the two move in opposite directions |
| `cost_change` | Fraction of spend removed. Positive means the parallel version is cheaper |

**Walk one example.** The illustrative numbers above, decomposed:

```
  parallel work   : 62 / 8               =  7.75 min
  assumed total   :                         14.00 min
  orchestration   : 14.00 - 7.75         =  6.25 min   <- the fixed tax
                                             (2 Opus calls: plan, then integrate)

  speedup         : 62 / 14              =  4.4x       (NOT 8x -- the tax eats 3.6x)
  cost reduction  : (1.54 - 0.39) / 1.54 =  75%
```

The counter-intuitive line is the cost one: the orchestrator-worker run makes *more* LLM calls
(1 + 8 = 9 vs 1) and is still cheaper here. Two things produce that, and only one of them is the
architecture. First, tier: Claude Haiku 4.5 is 5x cheaper per token than Claude Opus 5 ($1/$5
against $5/$25). Second, and larger, total input shrank — 8 workers x 12K = 96K tokens read
against the single agent's 187K, because each worker reads only its own slice. Flip the tiers
(Opus workers under a Haiku orchestrator) and the same architecture becomes several times *more*
expensive than the single agent.

**The saving is conditional, and in production it usually does not appear.** This example holds
total work roughly constant and redistributes it. Real research fan-out does the opposite: each
worker issues its own searches and reads its own sources, so aggregate tokens go *up*, which is
exactly what Anthropic measured (~15x chat-level usage). Budget orchestrator-worker as a latency
and quality purchase, and verify the cost direction on your own workload rather than assuming it.

Note also what shrank on the context axis: `187K` tokens in one context became `12K` peak per
agent. That is a 15.6x reduction in the largest prompt any single model has to reason over, and
it is the proposed mechanism behind the quality gap Anthropic measured — not extra intelligence,
just no "lost in the middle."

---

## 7. Real-World Examples

### Anthropic — "Building Effective Agents" and the Research System

Two distinct Anthropic sources, often conflated. **"Building Effective Agents"** (19 December 2024) names orchestrator-workers as one of five workflow patterns and defines it as "a central LLM dynamically breaks down tasks, delegates them to worker LLMs, and synthesizes their results," recommending it for "complex tasks where you can't predict the subtasks needed." It contains no task ledger, no checkpointing, and no week-long-workflow claim. The separate engineering write-up on Anthropic's **multi-agent research system** supplies the measured figures reproduced in §6: Claude Opus 4 lead with Claude Sonnet 4 subagents, +90.2% over single-agent Opus 4 on their internal research eval, and ~15x chat-level token usage. The orchestrator/worker tier split (best model on the orchestrator, cheaper models on narrow workers) is Anthropic's stated practice.

### GitHub Copilot Workspace (retired)

Copilot Workspace, launched as a technical preview in April 2024, applied an issue-to-plan-to-code decomposition: a planning step read a GitHub issue and produced a spec and a plan, then code changes were generated per file rather than over the whole repository. **GitHub discontinued it on 30 May 2025**; the architecture was folded into the Copilot coding agent, generally available to paid subscribers from September 2025. Cite it as a retired preview whose decomposition idea survived, not as a live product.

### Deterministic Fan-Out in Fraud Scoring

The lowest-risk production form of this pattern uses a **code** orchestrator, not an LLM: a request fans out to independent scoring services — velocity, geolocation, merchant risk, device fingerprint — which run concurrently under a millisecond-scale budget, and a deterministic aggregator combines their scores. Payment processors run fan-out scoring of broadly this shape, but none publishes its worker roster or per-stage latency, so treat the specific decomposition here as a representative design rather than a documented one. The transferable point is the one that *is* checkable in your own system: when the sub-tasks are fixed and the latency budget is tight, every dispatch decision should be code, and the LLM (if any) should be inside a worker, not in the orchestrator.

---

## 8. Tradeoffs

| Dimension | LLM Orchestrator | Code Orchestrator |
|-----------|-----------------|-------------------|
| Flexibility | High — adapts plan based on intermediate results | Low — fixed workflow |
| Cost | High — orchestrator itself makes LLM calls | Low — only workers cost money |
| Latency | Higher — planning adds 1-3 seconds | Lower — dispatch is O(1) |
| Debuggability | Harder — orchestrator behavior is non-deterministic | Easy — code is deterministic |
| Failure handling | Nuanced — orchestrator can reason about failures | Rule-based — must code every failure case |
| Best for | Open-ended research, unpredictable tasks | Known pipelines, production systems |

| Worker Type | Parallelism | Dependencies | Suitable for |
|------------|-------------|--------------|-------------|
| Parallel (fan-out) | Maximum | None between workers | Multi-section research, data enrichment |
| Sequential (chain) | None | Each depends on previous | Code gen pipeline, multi-stage transformation |
| DAG | Partial | Partial | Complex workflows (e.g., 3 parallel then 1 merge) |

---

## 9. When to Use / When NOT to Use

### Use Orchestrator-Worker When:

- The task cannot fit in a single LLM context window (>100K tokens of input material)
- Sub-tasks are independently parallelizable (latency savings are significant)
- Different sub-tasks benefit from different model types (research vs coding vs review)
- You need robust error recovery: one failed worker should not abort the entire task
- The task has a natural decomposition into discrete, verifiable units of work

### Do NOT Use Orchestrator-Worker When:

- The task is simple enough for a single well-prompted LLM call (adding orchestration adds latency, cost, and complexity)
- Sub-tasks are so tightly coupled that workers constantly need each other's intermediate state (prefer a shared-state blackboard pattern instead)
- Latency is the primary constraint and you cannot afford the orchestration round-trips (prefer a streaming single-agent architecture)
- The team cannot observe and debug multi-agent interactions (without tracing infrastructure, failures are opaque)

---

## 10. Common Pitfalls

### Pitfall 1: Orchestrator Generates Undecidable Task Lists

Broken pattern: The LLM orchestrator generates tasks like "research all relevant papers on X" — open-ended with no exit condition. Workers time out or produce infinite results.

```python
# BROKEN: open-ended task with no exit condition
task = Task(task_id="T001", description="Research all papers on retrieval augmented generation")
# Worker spins for 120 seconds searching, returns 500 papers, orchestrator has no stopping rule
```

```python
# FIXED: scoped task with explicit bounds
task = Task(
    task_id="T001",
    description=(
        "Search arxiv for papers on retrieval augmented generation published Jan-Jun 2024. "
        "Return at most 10 most-cited papers. Stop after 20 search queries."
    )
)
# Worker now has clear termination criteria
```

### Pitfall 2: No Schema Enforcement on Worker Output

Production war story: A document generation orchestrator dispatched 12 workers in parallel. Workers 1-11 returned clean JSON; Worker 12 returned a natural language apology ("I cannot complete this task because..."). The orchestrator tried to merge all results assuming JSON, crashed on Worker 12's output, and lost the previous 11 successful results because there was no checkpoint.

```python
# BROKEN: assume all workers return valid JSON
results = await asyncio.gather(*[run_worker(t) for t in tasks])
merged = {k: v["summary"] for r in results for k, v in r.items()}  # KeyError on "summary"
```

```python
# FIXED: validate per-worker output and checkpoint after each completion
async def safe_worker(task: Task, context: dict) -> dict | None:
    try:
        result = await asyncio.wait_for(run_worker(task, context), timeout=60.0)
        if not isinstance(result, dict) or "summary" not in result:
            return {"summary": str(result), "key_facts": [], "confidence": 0.0}
        checkpoint_save(task.task_id, result)   # save immediately on success
        return result
    except Exception as e:
        return {"summary": "", "key_facts": [], "confidence": 0.0, "error": str(e)}
```

### Pitfall 3: Orchestrator Context Explosion

Production war story: An orchestrator accumulated every worker's full output into its context for the integration step. With 15 workers each returning 2,000 tokens, the orchestrator's integration call received 30,000+ tokens of worker outputs — causing the model to "lose" workers 5-10 (the middle of the context) and produce a final output that omitted entire sections.

Fix: Each worker returns a structured summary capped at 500-1,000 tokens. The full raw output is stored externally (S3, database); the orchestrator receives only the summary. The integration prompt references summaries, not raw outputs.

**Stated plainly.** "The orchestrator's integration prompt is the product of two numbers you
control separately — how many workers you fan out to, and how much each is allowed to say back
— and only the second one has a natural ceiling."

```
  integration_tokens = num_workers x tokens_per_worker_reply

  BROKEN (uncapped raw output)
    15 workers x 2,000 tokens = 30,000 tokens
    scale to 50 workers       = 100,000 tokens   <- half of a 200K window, one call

  FIXED (capped structured summary)
    15 workers x   750 tokens = 11,250 tokens    <- 2.7x smaller
    scale to 50 workers       = 37,500 tokens    <- still comfortably servable
```

Capping the reply is what makes fan-out width a free variable. Uncapped, `num_workers` is
bounded by the context window, so "analyse more patents" and "analyse them well" trade against
each other. Capped, the integration prompt grows linearly at a rate you set, and the full raw
output still exists in S3 for any worker the integrator wants to inspect in detail — you have
traded an unbounded push for a bounded push plus an optional pull.

The 30,000-token failure mode is worth naming precisely: nothing errored. The call succeeded,
stayed under the limit, and silently dropped workers 5-10 from the output because they sat in
the middle of a long context. Context explosion in an orchestrator does not announce itself with
a 400 — it announces itself with quietly incomplete answers, which is why the cap has to be an
enforced schema constraint rather than a prompt request.

### Pitfall 4: No Rate Limit Coordination Between Workers

All 15 workers in a parallel dispatch simultaneously fire LLM requests, hitting the API's requests-per-minute limit and causing wave-after-wave of 429 retries.

```python
# FIXED: shared semaphore limits concurrent LLM calls
import asyncio

_API_SEMAPHORE = asyncio.Semaphore(5)   # at most 5 concurrent calls

async def run_worker_rate_limited(task: Task, context: dict) -> Any:
    async with _API_SEMAPHORE:
        return await run_worker(task, context)
```

---

## 11. Technologies & Tools

| Tool | Role in Orchestrator-Worker | Notes |
|------|----------------------------|-------|
| LangGraph | Graph-based orchestration with typed state | Best production choice; supports checkpoints, human-in-the-loop |
| Anthropic Claude Opus | Orchestrator LLM | Highest reasoning capability; use sparingly. Opus 5: $5 / $25 per MTok |
| Anthropic Claude Haiku | Worker LLM | Haiku 4.5: $1 / $5 per MTok — exactly 5x cheaper than Opus 5 on both input and output; sufficient for narrow tasks |
| LangSmith / Langfuse | Tracing inter-agent calls | Essential for debugging; log every dispatch and result |
| Redis | Task ledger persistence | Allows orchestrator restart without losing progress |
| asyncio (Python) | Parallel worker dispatch | Core Python; use `asyncio.gather` for fan-out |
| Celery | Distributed worker dispatch | For workers that need separate processes or machines |
| Temporal | Durable workflow orchestration | Production-grade; handles retries, timeouts, checkpoints |

---

## 12. Interview Questions with Answers

**Q: What is the orchestrator-worker pattern and how does it differ from a single-agent approach?**
A: The orchestrator-worker pattern uses a central coordinator that breaks a complex task into sub-tasks and delegates each to a specialized worker agent, then aggregates results. A single-agent approach runs everything through one LLM call or one sequential agent loop. The key differences are: (1) orchestrator-worker enables parallelism — multiple workers run simultaneously; (2) each worker operates with focused context rather than the full accumulated history; (3) different models can be used for different stages; (4) failures are isolated — one worker failure does not abort the entire task. The tradeoff is increased coordination overhead and complexity.

**Q: When should the orchestrator itself be an LLM versus deterministic code?**
A: Use an LLM orchestrator when the task decomposition is unpredictable — when you do not know upfront how many sub-tasks are needed or what they will look like, and when the orchestrator needs to adapt its plan based on intermediate worker results. Use deterministic code as the orchestrator when the pipeline is well-understood and stable, latency and cost matter, and you want reproducible behavior. In production, most systems start with an LLM orchestrator (flexible), then harden the most common paths into code (faster, cheaper).

**Q: How do you implement parallel worker dispatch in Python?**
A: Use `asyncio.gather` to dispatch all ready workers simultaneously. Mark tasks as "dispatched" before firing, then collect results when gather completes. Use a semaphore to cap concurrent API calls and avoid rate limit errors. Workers should be `async` functions that accept a task and a context dictionary, returning a structured result dictionary. The orchestrator updates the task ledger after each gather cycle and loops until all tasks are done.

**Q: What is a task ledger and why is it important?**
A: A task ledger is the orchestrator's persistent record of all tasks, their dependencies, statuses, and results. It is important for three reasons: (1) it enables dependency-aware scheduling — tasks are only dispatched when their dependencies are completed; (2) it enables failure recovery — the orchestrator can retry failed tasks or skip them and continue with the rest; (3) it enables checkpointing — if the orchestrator process crashes, a persisted ledger allows resumption from the last completed task rather than starting over. In practice, store the ledger in Redis or a database so it survives process restarts.

**Q: How did Anthropic's published research demonstrate the advantage of orchestrator-worker over single-agent?**
A: Anthropic reported that a Claude Opus 4 lead agent with Claude Sonnet 4 subagents outperformed single-agent Claude Opus 4 by 90.2% on their internal research eval. Parallel tool calling separately cut research time by up to 90% on complex queries. The stated mechanism is context and token budget rather than raw intelligence: on their BrowseComp eval, token usage alone explained 80% of the performance variance, and fan-out is how you spend more tokens without any one context growing. The number interviewers most often get backwards is the cost: Anthropic measured multi-agent systems using roughly 15x the tokens of a chat interaction (single agents ~4x), so the pattern buys latency and quality and generally costs more, not less.

**Q: How do you handle a worker that returns invalid or malformed output?**
A: Validate every worker result against the expected schema before storing it in the task ledger. If the result is malformed, retry the worker up to max_retries times (typically 2). After all retries fail, store a fallback result (empty summary, zero confidence, error message) and mark the task as failed. The orchestrator must handle failed tasks during integration — either skip the section, use a placeholder, or escalate to a human reviewer. Never let a single malformed worker result crash the entire orchestration loop.

**Q: What is the fan-out / fan-in pattern and when is it appropriate?**
A: Fan-out dispatches multiple workers simultaneously on independent sub-tasks; fan-in collects all worker results and merges them. It is appropriate when sub-tasks are fully independent (no inter-worker dependencies), when wall-clock latency is a concern (parallel execution reduces time to completion to the slowest worker's time), and when each sub-task requires a similar amount of work. It is not appropriate when sub-tasks depend on each other, when the number of tasks is large enough to exhaust API rate limits, or when the orchestrator context would overflow from accumulating all results.

**Q: How do you prevent context explosion in the orchestrator's integration step?**
A: Require each worker to return a structured summary capped at a fixed token budget (500-1,000 tokens), not the full raw output. Store the full raw output externally (S3, database, vector store). The orchestrator's integration step receives only summaries. If the integration step needs specific details from a worker's raw output, use a targeted retrieval call (RAG or direct lookup) rather than including the full output in the integration context. This keeps the integration step's input bounded regardless of how many workers ran.

**Q: What rate limiting strategy should you use when dispatching many workers in parallel?**
A: Use an asyncio semaphore to cap the number of concurrent LLM API calls. A typical safe limit is 5-10 concurrent calls (matching your API tier's requests-per-minute limit divided by average call duration). Additionally, implement exponential backoff with jitter on 429 responses at the worker level. For very high-throughput systems, use a token bucket implemented in Redis (allowing burst capacity up to the bucket size) rather than a simple semaphore. Tools like LiteLLM can handle this transparently with built-in load balancing across multiple API keys.

**Q: How do you make an orchestrator-worker system resumable after a crash?**
A: Persist the task ledger to durable storage (Redis, PostgreSQL) after every state change: task dispatched, task completed, task failed. Also checkpoint worker results immediately upon receipt rather than accumulating them in memory. On restart, load the ledger from storage, skip completed tasks, and resume from the first pending or failed task. Use a distributed workflow system like Temporal for this in production — it provides durable execution semantics natively, so crashes during worker execution automatically retry the failed step without manual checkpointing code.

**Q: What are the signs that you have designed the task decomposition too coarsely?**
A: Signs of overly coarse decomposition: (1) a worker's task description is so broad that the worker itself needs to decompose it further, effectively creating an unplanned nested orchestration; (2) workers frequently time out because a single sub-task is too large; (3) worker context windows overflow because the sub-task requires too much background material; (4) error recovery is coarse-grained — one failure requires redoing a large chunk of work. Fix by splitting coarse tasks into 2-3 finer tasks and adding explicit output schemas for each. A well-decomposed task should be completable by a worker in under 30 seconds with under 10K tokens of context.

**Q: How do you choose which model to use for orchestrator vs workers?**
A: The orchestrator requires high reasoning capability because it must understand the overall goal, generate a coherent task plan, evaluate whether worker outputs are sufficient, and handle unexpected situations. Use the highest-capability model you have (Claude Opus 5, or a frontier model from another vendor). Workers perform narrow, well-defined tasks with explicit instructions and output schemas; they do not need broad reasoning. Use the cheapest model that can reliably complete the specific worker task — often claude-haiku-4-5 or gpt-4o-mini. Size the saving from real list prices rather than a remembered ratio: Haiku 4.5 at $1/$5 against Opus 5 at $5/$25 is 5x, not the 15-60x gaps that held in earlier model generations.

**Q: What is the "cascading hallucination" problem specific to orchestrator-worker systems?**
A: Cascading hallucination occurs when Worker A produces a factual error, Worker B accepts that error as ground truth and builds on it, and Worker C builds on Worker B's compounded error. The final output is wrong with high apparent confidence because multiple agents "agreed." To prevent it: (1) add a fact-checking worker at key pipeline junctions (between research and synthesis); (2) instruct workers to flag uncertainty rather than confabulate; (3) require workers to cite their sources (web URLs, file names) so the orchestrator can verify; (4) treat high-confidence outputs with low source citation as a red flag.

**Q: How does LangGraph implement the orchestrator-worker pattern?**
A: LangGraph models the orchestration as a directed graph where nodes are agent functions and edges are conditional transitions. The orchestrator is a node that reads the shared state, plans the next worker to call (or calls multiple workers in parallel via a fan-out node), and updates the state with its decision. Worker nodes are separate graph nodes that read relevant state fields, run their LLM call, and write results back to state. The graph's conditional edge logic handles: which worker to call next, when to loop back for retry, and when to terminate. Built-in checkpointing (using a Redis or PostgreSQL checkpointer) makes the entire graph resumable.

**Q: How do you test an orchestrator-worker system in isolation?**
A: Test each layer independently: (1) unit test each worker with fixed input dictionaries — verify it returns the expected schema, handles edge cases, and fails gracefully on bad input; (2) unit test the orchestrator's planning logic by mocking worker calls with prebuilt responses — verify it generates the right task list, handles failed workers, and integrates results correctly; (3) integration test the full system on a canonical set of test tasks with expected output properties — use LLM-as-judge scoring rather than exact match; (4) chaos test by injecting worker failures, timeouts, and malformed outputs — verify the orchestrator recovers gracefully in all cases.

**Q: What observability should every orchestrator-worker system have?**
A: At minimum: (1) structured log entry for every task dispatch (task_id, worker_type, model, input_token_count, timestamp); (2) structured log entry for every worker completion or failure (task_id, output_token_count, latency_ms, status, error if applicable); (3) a trace that links all worker calls to the parent orchestration run (parent_run_id); (4) cost tracking per run (sum of all worker token costs); (5) alerting on high failure rates (more than 2 worker failures per orchestration run). Use LangSmith, Langfuse, or Arize Phoenix for LLM-specific tracing. Without this observability, debugging multi-agent failures in production is nearly impossible.

**Q: An LLM worker is non-deterministic, so what does it actually mean to require that workers be idempotent?**
It means the retry must be schema-equivalent and side-effect-safe, not textually identical — you are never promised the same tokens twice. Two halves. Output equivalence is what the JSON schema is for: a summariser that returns different prose on retry is harmless if both results validate against `{summary, confidence, sources}` and the integration step reads only those fields, which is why schema enforcement and idempotency are one principle in practice rather than two. Side-effect safety is the half schemas do not touch: a 60-second timeout does not mean the worker failed, only that no response arrived, so a worker that filed a ticket at t=58s files a second one on retry — and parallel dispatch compounds it, because a batch-level failure tempts you to re-dispatch the whole `asyncio.gather` set including workers that already succeeded. The fix uses machinery the pattern already has: derive an attempt-invariant idempotency key from `(run_id, task_id)` in the task ledger and make every side-effecting tool deduplicate on it (mechanics in [Durable & Long-Running Agents](../agents_and_tool_use/durable_long_running_agents.md); actions with no inverse belong behind an approval gate instead, per [Agent Reliability](../agents_and_tool_use/agent_reliability.md)). The orchestrator-level heuristic: keep write-capable workers out of the fan-out and reserve the aggressive retry policy for read-only workers.

---

## 13. Best Practices

1. Always use your highest-capability model for the orchestrator; use cheap specialized models for workers.
2. Enforce JSON schemas on all worker outputs — reject and retry malformed results immediately.
3. Cap worker context to the minimum necessary for the sub-task — do not pass the full accumulated history to every worker.
4. Persist the task ledger to durable storage so the orchestration can resume after a crash.
5. Use an asyncio semaphore or token bucket to prevent parallel workers from overwhelming API rate limits.
6. Add a validation/fact-checking worker at key pipeline junctions to prevent cascading hallucination.
7. Log every task dispatch and worker result with the parent orchestration run ID — tracing is non-negotiable in production.
8. Define explicit exit conditions for all tasks — open-ended tasks lead to timeout loops.
9. Design for partial completion: always return the best available result from successful workers even if some workers failed.
10. Test failure modes explicitly — inject worker timeouts, malformed outputs, and consecutive failures during development.

---

## 14. Case Study: Multi-Agent Patent Analysis System

### Problem Statement

A law firm needed to analyze 50-400 patent documents per case to identify prior art, claim overlaps, and potential infringement risks. A single-agent approach using GPT-4o hit the 128K context limit after ~15 patents, required sequential processing (3-4 hours per case), and produced inconsistent analysis formats that required manual harmonization. **All figures in this case study are illustrative and are worked for a 50-patent case** — the worker term scales linearly with patent count, the orchestration term does not.

### Architecture

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Orch["Orchestrator (Claude Opus 5)<br/>- Reads case brief<br/>- Generates patent analysis plan<br/>- Maintains task ledger<br/>- Integrates final analysis"] --> B1 & B2 & B3
    B1["Worker 1: patent #1<br/>Patent Analyst (Claude Haiku 4.5)<br/>Output: claim_analysis JSON"] --> Conflict
    B2["Worker 2: patent #2<br/>Patent Analyst (Claude Haiku 4.5)<br/>Output: claim_analysis JSON, same format"] --> Conflict
    B3["Worker N: patent #N<br/>one worker per patent, 50 in flight<br/>Output: claim_analysis JSON, same format"] --> Conflict
    Conflict["Conflict Detection Worker (Claude Sonnet 5)<br/>Input: all claim_analysis JSONs<br/>Output: overlap_matrix JSON"] --> Summary
    Summary["Legal Summary Writer (Opus 5)<br/>Input: overlap_matrix<br/>Output: attorney-ready report"]

    class Orch req
    class B1,B2,B3 base
    class Conflict,Summary io
```

### Key Design Decisions

- Orchestrator uses Claude Opus 5 (best reasoning for legal context) but runs only twice (planning + integration). Those two calls consume roughly 15K input and 22K output tokens combined — output-dominated, because the second one writes the attorney-ready report, and Opus 5 output at $25/MTok is what makes three calls carry 37.5% of the bill.
- Patent analyst workers use Claude Haiku 4.5; each receives exactly one patent (2,000-8,000 tokens) plus a standard claim analysis schema. Up to 50 workers run concurrently under a semaphore; a 300-patent case runs six such waves.
- Workers return a fixed JSON schema: `{patent_id, filing_date, claims: [{claim_id, text, keywords}], novelty_flags}`. Any deviation triggers immediate retry.
- Conflict detection worker is Claude Sonnet 5 (middle tier) — needs more reasoning than Haiku for cross-patent comparison, but less than Opus.
- Task ledger persisted in Redis with 48-hour TTL — cases can be paused and resumed.

### Results (illustrative, for a 50-patent case)

- Wall-clock time per case: 18 minutes (vs. 3-4 hours single-agent)
- Patent coverage: 100% (all patents analyzed; single-agent capped at ~15)
- Cost per case: $2.40 average (50 Haiku workers × ~$0.03 each + 2 Opus calls + 1 Sonnet call)

**What the formula is telling you.** "Almost two-thirds of this bill is 50 cheap workers, and
the remaining third is three expensive calls — so the lever on cost is which tier does the
integration, not how many patents you analyse."

```
  cost_per_case = num_workers x cost_per_worker  +  cost_orchestration

  workers       : 50 x $0.03          = $1.50    (62.5% of the bill)
  orchestration : $2.40 - $1.50       = $0.90    (37.5%, across just 3 calls)
                  2 Opus (plan + integrate) + 1 Sonnet (conflict detection)

  per-call cost : workers       $1.50 / 50 = $0.030
                  orchestration $0.90 /  3 = $0.300   <- 10x per call
```

Fifty-three calls, and the three at the top cost ten times each what the fifty at the bottom do.
That ratio is the whole design argument: because the expensive tier is called a fixed 3 times
regardless of case size, doubling the patent count to 100 does not double the bill — it adds
`50 x $0.03 = $1.50`, taking the case from $2.40 to $3.90 — 100% more work for 62.5% more money.
The orchestration cost amortises. That is why the pattern gets *more* attractive as fan-out
widens, and why the orchestrator's token budget is worth defending: it is the one term that does
not shrink when you move work to a cheaper model, and it is dominated by *output* — the report
the summary writer generates — so trimming its prompt buys almost nothing.
- Attorney acceptance rate on first draft: 84% (minor revisions needed on 16%)
- System ran 340 cases in first six months with 99.2% successful completion rate (0.8% required human intervention for corrupted patent PDFs)
