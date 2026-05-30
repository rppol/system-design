# Agent Durability Patterns

Cross-references: [`../../agents_and_tool_use/durable_long_running_agents.md`](../../agents_and_tool_use/durable_long_running_agents.md) | [`../../agentic_frameworks/langgraph.md`](../../agentic_frameworks/langgraph.md) | [`../../agentic_workflow_patterns/README.md`](../../agentic_workflow_patterns/README.md)

Referenced by: [`../design_autonomous_swe_agent.md`](../design_autonomous_swe_agent.md) | [`../design_browser_research_agent.md`](../design_browser_research_agent.md) | [`../design_computer_use_agent.md`](../design_computer_use_agent.md) | [`../design_ai_data_analyst.md`](../design_ai_data_analyst.md)

---

## 1. Concept Overview

A long-running LLM agent is not a single API call — it is a distributed computation spread across dozens of LLM inferences, tool executions, and external API calls that may span minutes or hours. A 50-tool-call SWE agent running for 2 hours has approximately 120 distinct failure points: each tool call can fail due to a network timeout, each LLM inference can fail due to rate limiting, the host process can be preempted by the cloud scheduler, and the user can interrupt mid-task. Without durability infrastructure, any single failure restarts the entire run from scratch, wasting wall-clock time, compute cost, and user trust.

Durability is especially critical — and qualitatively different from ordinary fault tolerance — because LLM agents take real-world actions. An agent that reads files, commits code, sends emails, or calls payment APIs cannot simply be replayed from the beginning: replaying those write-side-effect actions causes double commits, duplicate emails, and double charges. Durability for agents requires three interlocking mechanisms: (1) checkpoint state after every successful tool call so resumption starts at the last committed step; (2) idempotency keys on every write-side-effect tool call so that replaying the same logical operation does not repeat the side effect; and (3) user interruption as a first-class concept, not an error condition, so that a user pausing a 2-hour task can safely resume it later.

---

## 2. Intuition

**One-line analogy**: Durable agent execution is the saga pattern for LLMs — each tool call is a saga step that either commits its result to a checkpoint store or does not commit at all; a crash mid-saga resumes from the last committed step, and write-side-effect steps carry idempotency keys that prevent compensating actions from becoming double-actions.

**Mental model**: Think of a checkpoint store as a flight data recorder for agent execution. After every tool call, the agent's full state — messages, tool call history, accumulated outputs — is written to durable storage. On restart, the agent reads the recorder, fast-forwards through already-committed steps (skipping the actual tool execution for write steps that carry cached results), and picks up exactly where it left off.

**Why it matters**: The cost of a 2-hour SWE agent run on GPT-4o is roughly $4–8 in LLM tokens plus any tool API costs. A non-durable agent that fails at step 48 of 50 throws away $7.90 of work and starts over. With durability, the same failure costs one checkpoint-save latency (5ms) and resumes in under 1 second.

**Key insight**: Checkpoint granularity and idempotency are separable concerns. Checkpoint granularity controls recovery point precision (every step vs every N steps). Idempotency controls whether replayed write operations cause duplicate side effects. You can have fine-grained checkpoints with no idempotency (reads-only agent, safe to re-execute) or coarse-grained checkpoints with full idempotency (write-heavy agent where re-execution is too expensive). A production SWE agent needs both.

---

## 3. Core Principles

**Checkpoint at every tool call boundary**: Save agent state to durable storage after every successful tool call completion, before issuing the next LLM inference. The tool call boundary is the natural unit of atomic progress — a tool call either completes and its result is recorded, or it does not complete and the next run will re-execute it.

**Idempotency keys prevent duplicate side effects on replay**: Every write-side-effect tool call must carry an idempotency key — a deterministic, content-addressable identifier (typically SHA-256 of the logical operation parameters). On replay, the idempotency key lookup returns the cached result instead of re-executing the operation. The caller assigns the key, not the tool — the caller knows the semantic identity of the operation.

**Distinguish read-only tools from write tools**: Read-only tools (grep, file read, web fetch, code search) are always safe to replay without an idempotency key. Write tools (file edit, git commit, email send, API POST) require idempotency keys. In a typical 50-tool-call SWE agent, approximately 47 calls are read-only and 3 are write operations — only those 3 need idempotency tracking.

**User interruption is a first-class concept, not an error**: The agent loop must poll for an interruption signal between tool calls. When interrupted, the agent transitions to an "interrupted" state, saves that state to the checkpoint store, and halts cleanly. Resumption loads the checkpoint and continues from the next pending tool call.

**State is serializable and stored externally, not in process memory**: All agent state — accumulated messages, tool call log, idempotency key cache, current step index — must be JSON-serializable and stored in a system that survives process restart (Postgres, Redis, SQLite on persistent volume). In-memory state is a single-process liability.

---

## 4. Types / Architectures / Strategies

| Pattern | Durability Level | Complexity | Latency Overhead per Step | Infrastructure Dependency |
|---------|-----------------|------------|--------------------------|--------------------------|
| In-memory checkpoint | Interruption-only (no crash recovery) | Low | ~0ms | None |
| Database checkpoint (SQLite/Postgres) | Full crash recovery | Medium | 3–10ms | SQLite or Postgres |
| Temporal workflow | Full + replay + versioning + signals | High | 5–15ms | Temporal server cluster |
| LangGraph persistence | Full crash recovery, LangGraph-native | Medium | 5–8ms | SQLite or Postgres |

**Pattern 1 — In-memory checkpoint**: Agent state is held in a Python dataclass and serialized to a dict at each step. State survives user interruption (the process stays alive, the state dict is accessible), but a process crash loses everything. Suitable for: interactive REPL sessions, notebooks, tasks under 5 minutes where restart cost is acceptable.

**Pattern 2 — Database checkpoint**: After each tool call, state is serialized to JSON and written to SQLite (development) or Postgres (production) keyed by `thread_id`. On process start, the agent loads any existing checkpoint for the thread. Suitable for: production agents with tasks up to several hours, where operator-managed infrastructure is acceptable. This is the baseline for most production deployments.

**Pattern 3 — Temporal workflow**: The agent is implemented as a Temporal workflow function, with each tool call wrapped in a Temporal activity. Temporal automatically persists the workflow event history to its backend (Cassandra or Postgres), replays deterministic history on restart, and provides signals for human-in-the-loop interruption. Suitable for: high-volume agent platforms (>10K runs/day), agents that need versioning across deployments, complex branching workflows.

**Pattern 4 — LangGraph persistence**: LangGraph's `SqliteSaver` or `PostgresSaver` checkpointer attaches to a `StateGraph` and automatically saves the full graph state at each node boundary. The `thread_id` in the config dict identifies the conversation. On re-invocation with the same `thread_id`, execution resumes from the last checkpointed node. Suitable for: LangGraph-based agents, teams already using LangGraph who want durability with minimal additional infrastructure.

---

## 5. Architecture Diagrams

### Checkpoint Lifecycle and Crash Recovery

```
NORMAL EXECUTION:
                  +---------+     +---------+     +---------+
  Start  -------> | Tool 1  | --> | Tool 2  | --> | Tool 3  |
                  +---------+     +---------+     +---------+
                       |               |               |
                  CHECKPOINT       CHECKPOINT       CHECKPOINT
                  (step=1)         (step=2)         (step=3)
                  written to DB    written to DB    written to DB
                       |               |               |
                       v               v               v
                  +-----------------------------------------+
                  |         Checkpoint Store (Postgres)     |
                  | thread_id | step | state_json | ts      |
                  +-----------------------------------------+

CRASH AT STEP 2:
  Process crash between Tool 2 and Tool 3.
  DB holds checkpoint at step=2.

RECOVERY:
  Process restart
       |
       v
  Load checkpoint (thread_id) from DB
       |
  state.current_step == 2
       |
  Skip Tool 1, Tool 2 (already in tool_calls_made)
       |
  Resume at Tool 3
       |
  Continue to completion
```

### Idempotency Flow for Write-Side-Effect Tool Calls

```
Agent issues write tool call with idempotency_key:
  key = SHA256("git_commit:" + diff_hash)

          +-------------------------------------+
          |  IdempotentToolExecutor.execute()   |
          |                                     |
          |  1. CHECK Redis:                    |
          |     GET idempotency:{key}           |
          |                                     |
          |  2a. Cache HIT (TTL 24h):           |
          |      return cached ToolResult       |
          |      (NO tool re-execution)         |
          |                                     |
          |  2b. Cache MISS:                    |
          |      execute tool                   |
          |      SET idempotency:{key} = result |
          |      EXPIRE key 86400               |
          |      return ToolResult              |
          +-------------------------------------+
                         |
                    ToolResult returned to agent
                    Agent checkpoints state with result
```

### Temporal Workflow for Durable Agent Execution

```
  Client                  Temporal Server             Worker Process
  ------                  ---------------             --------------
  StartWorkflow --------> Persist WorkflowStarted
                          event to history
                                  |
                                  v
                          Schedule Activity --------> Execute tool call
                          (tool_call_activity)        (actual side effect)
                                  |
                          Persist ActivityCompleted
                          event to history
                                  |
                          Signal: "interrupt" -------> Worker sees signal
                                  |                    sets interrupt_requested
                          Persist SignalReceived        = True; agent loop
                                  |                    halts cleanly
                                  |
                          WorkflowQuery: status -----> Return "interrupted"

  CRASH RECOVERY:
  Worker restarts ------> Temporal replays event history
                          deterministically at ~1000 steps/sec
                          Worker resumes at next pending activity
```

---

## 6. How It Works — Detailed Mechanics

### AgentState Dataclass

```python
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Literal
import json
import time


@dataclass
class ToolCall:
    tool_name: str
    args: dict[str, Any]
    result: Any
    idempotency_key: str | None
    completed_at: float  # epoch timestamp


@dataclass
class AgentState:
    thread_id: str
    messages: list[dict[str, Any]]  # serialized LLM messages
    tool_calls_made: list[ToolCall] = field(default_factory=list)
    current_step: int = 0
    idempotency_keys: dict[str, Any] = field(default_factory=dict)
    status: Literal["running", "interrupted", "completed"] = "running"
    created_at: float = field(default_factory=time.time)
    updated_at: float = field(default_factory=time.time)

    def to_json(self) -> str:
        return json.dumps({
            "thread_id": self.thread_id,
            "messages": self.messages,
            "tool_calls_made": [
                {
                    "tool_name": tc.tool_name,
                    "args": tc.args,
                    "result": tc.result,
                    "idempotency_key": tc.idempotency_key,
                    "completed_at": tc.completed_at,
                }
                for tc in self.tool_calls_made
            ],
            "current_step": self.current_step,
            "idempotency_keys": self.idempotency_keys,
            "status": self.status,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
        })

    @classmethod
    def from_json(cls, data: str) -> "AgentState":
        d = json.loads(data)
        state = cls(
            thread_id=d["thread_id"],
            messages=d["messages"],
            current_step=d["current_step"],
            idempotency_keys=d["idempotency_keys"],
            status=d["status"],
            created_at=d["created_at"],
            updated_at=d["updated_at"],
        )
        state.tool_calls_made = [
            ToolCall(
                tool_name=tc["tool_name"],
                args=tc["args"],
                result=tc["result"],
                idempotency_key=tc["idempotency_key"],
                completed_at=tc["completed_at"],
            )
            for tc in d["tool_calls_made"]
        ]
        return state
```

### CheckpointStore — Abstract Base and Postgres Implementation

```python
from abc import ABC, abstractmethod
import asyncpg


class CheckpointStore(ABC):
    @abstractmethod
    async def save(self, thread_id: str, state: AgentState) -> None: ...

    @abstractmethod
    async def load(self, thread_id: str) -> AgentState | None: ...


class PostgresCheckpointStore(CheckpointStore):
    """
    Checkpoint latency: ~3-5ms for a 50KB state blob on Postgres + asyncpg.
    Schema: CREATE TABLE agent_checkpoints (
        thread_id TEXT PRIMARY KEY,
        state_json TEXT NOT NULL,
        updated_at DOUBLE PRECISION NOT NULL
    );
    """

    def __init__(self, dsn: str) -> None:
        self._dsn = dsn
        self._pool: asyncpg.Pool | None = None

    async def _get_pool(self) -> asyncpg.Pool:
        if self._pool is None:
            self._pool = await asyncpg.create_pool(self._dsn, min_size=2, max_size=10)
        return self._pool

    async def save(self, thread_id: str, state: AgentState) -> None:
        state.updated_at = time.time()
        pool = await self._get_pool()
        await pool.execute(
            """
            INSERT INTO agent_checkpoints (thread_id, state_json, updated_at)
            VALUES ($1, $2, $3)
            ON CONFLICT (thread_id) DO UPDATE
              SET state_json = EXCLUDED.state_json,
                  updated_at = EXCLUDED.updated_at
            """,
            thread_id,
            state.to_json(),
            state.updated_at,
        )

    async def load(self, thread_id: str) -> AgentState | None:
        pool = await self._get_pool()
        row = await pool.fetchrow(
            "SELECT state_json FROM agent_checkpoints WHERE thread_id = $1",
            thread_id,
        )
        if row is None:
            return None
        return AgentState.from_json(row["state_json"])
```

### BROKEN: Naive Non-Durable Agent

```python
# BROKEN — holds all state in local variables; no checkpoint; replays re-execute all tools
async def run_agent_naive(task: str, tools: dict) -> str:
    messages = [{"role": "user", "content": task}]
    # If this process crashes here, everything is lost.
    # If a write tool (e.g., git_commit) was called, on restart it will be called again,
    # creating a duplicate commit. There is no idempotency protection.
    while True:
        response = await llm_call(messages)
        if response.finish_reason == "stop":
            return response.content
        tool_call = response.tool_calls[0]
        # result is never saved anywhere durable
        result = await tools[tool_call.name](**tool_call.args)
        messages.append({"role": "tool", "content": str(result)})
        # No checkpoint save. No idempotency key. No interrupt check.
        # A 2-hour agent run killed by OOM at step 47 of 50 restarts from zero.
```

### FIX: DurableAgentRunner

```python
import asyncio
import hashlib
from dataclasses import dataclass


@dataclass
class ToolResult:
    value: Any
    error: str | None = None


class DurableAgentRunner:
    def __init__(
        self,
        thread_id: str,
        checkpoint_store: CheckpointStore,
        idempotent_executor: "IdempotentToolExecutor",
        tools: dict[str, Any],
    ) -> None:
        self._thread_id = thread_id
        self._store = checkpoint_store
        self._executor = idempotent_executor
        self._tools = tools
        self._interrupt_requested = False

    def interrupt(self) -> None:
        """Call from a signal handler, HTTP endpoint, or user cancel button."""
        self._interrupt_requested = True

    async def run(self, task: str) -> str:
        # Load existing checkpoint; fresh state if none exists
        state = await self._store.load(self._thread_id)
        if state is None:
            state = AgentState(
                thread_id=self._thread_id,
                messages=[{"role": "user", "content": task}],
            )

        while True:
            # Check for interruption signal between every tool call
            if self._interrupt_requested:
                state.status = "interrupted"
                await self._store.save(self._thread_id, state)
                raise InterruptedError(
                    f"Agent interrupted at step {state.current_step}. "
                    f"Resume with thread_id={self._thread_id}"
                )

            response = await llm_call(state.messages)
            if response.finish_reason == "stop":
                state.status = "completed"
                await self._store.save(self._thread_id, state)
                return response.content

            tool_call = response.tool_calls[0]
            step_key = f"step_{state.current_step}_{tool_call.name}"

            # Skip already-completed tool calls on resume
            if step_key in state.idempotency_keys:
                cached_result = state.idempotency_keys[step_key]
                state.messages.append(
                    {"role": "tool", "content": str(cached_result)}
                )
                state.current_step += 1
                continue

            # Compute idempotency key for write tools
            is_write_tool = tool_call.name in WRITE_SIDE_EFFECT_TOOLS
            idempotency_key: str | None = None
            if is_write_tool:
                key_input = f"{tool_call.name}:{json.dumps(tool_call.args, sort_keys=True)}"
                idempotency_key = hashlib.sha256(key_input.encode()).hexdigest()

            result: ToolResult = await self._executor.execute(
                tool_name=tool_call.name,
                args=tool_call.args,
                idempotency_key=idempotency_key,
            )

            state.tool_calls_made.append(
                ToolCall(
                    tool_name=tool_call.name,
                    args=tool_call.args,
                    result=result.value,
                    idempotency_key=idempotency_key,
                    completed_at=time.time(),
                )
            )
            state.idempotency_keys[step_key] = result.value
            state.messages.append(
                {"role": "tool", "content": str(result.value)}
            )
            state.current_step += 1

            # Checkpoint after every successful tool call — cost: ~5ms
            await self._store.save(self._thread_id, state)


WRITE_SIDE_EFFECT_TOOLS = frozenset({
    "git_commit", "file_write", "file_delete", "send_email",
    "api_post", "create_pull_request", "run_shell_command",
})
```

### IdempotentToolExecutor

```python
import redis.asyncio as aioredis


class IdempotentToolExecutor:
    """
    Idempotency cache TTL: 24 hours (86400 seconds).
    Redis key format: idempotency:{sha256_key}
    Cache stores JSON-serialized ToolResult.
    """

    def __init__(self, redis_url: str, tools: dict[str, Any]) -> None:
        self._redis = aioredis.from_url(redis_url)
        self._tools = tools

    async def execute(
        self,
        tool_name: str,
        args: dict[str, Any],
        idempotency_key: str | None,
    ) -> ToolResult:
        if idempotency_key is not None:
            cache_key = f"idempotency:{idempotency_key}"
            cached = await self._redis.get(cache_key)
            if cached is not None:
                # Cache hit: return stored result, do NOT re-execute the tool
                return ToolResult(value=json.loads(cached))

        # Cache miss or read-only tool: execute normally
        try:
            value = await self._tools[tool_name](**args)
            result = ToolResult(value=value)
        except Exception as exc:
            result = ToolResult(value=None, error=str(exc))

        if idempotency_key is not None and result.error is None:
            cache_key = f"idempotency:{idempotency_key}"
            await self._redis.set(
                cache_key,
                json.dumps(result.value),
                ex=86400,  # 24h TTL
            )

        return result
```

### Concrete Numbers

- Checkpoint save latency (Postgres + asyncpg): **3–5ms** for a 50KB state blob
- Checkpoint size for a 50-tool-call agent: **~50KB** (JSON-serialized messages + tool results, excluding raw file contents)
- Checkpoint size with naive message storage (full file contents in messages): **50–500MB** — bloat pitfall, see Section 10
- Redis idempotency cache TTL: **24 hours** (86400 seconds)
- Temporal workflow replay speed: **~1000 steps/second** replaying deterministic history
- Typical 2-hour SWE agent: 50 tool calls — approximately 47 read-only (grep, read, search) and 3 write (file edit, git commit, create PR) — only the 3 write calls need idempotency keys
- Overhead of durability per step: **5–10ms** (checkpoint write + idempotency check) — negligible compared to LLM inference latency of 1–10 seconds per step

---

## 7. Real-World Examples

**Cognition / Devin**: Devin's SWE agent maintains a persistent Docker container session across the lifetime of a task. Agent state (file system state, terminal history, tool call log) is checkpointed at each major action. The Docker volume acts as a durable store for file-system side effects; the tool call log is persisted in Postgres. On task resumption, Devin re-attaches to the existing container rather than spinning up a fresh environment — this eliminates the cost of re-cloning repositories and re-installing dependencies, which can take 3–5 minutes for large monorepos.

**OpenHands (formerly OpenDevin)**: OpenHands implements a `ConversationMemory` class that serializes the agent's action-observation history to a JSON file after each step. The sandbox Docker container is paused rather than destroyed on interruption, preserving the ephemeral filesystem state. On resume, the container is un-paused and the action history is replayed in read-only mode to reconstruct the agent's internal state. This hybrid approach — durable action log + paused container — achieves crash recovery without replaying write-side-effect actions.

**Temporal in Production Agent Platforms**: DoorDash's engineering blog (2023) describes using Temporal for their "courier assignment" workflows, which share structural similarities with long-running agent workflows: branching logic, external API calls with retry semantics, human approval gates. The key insight: Temporal's deterministic replay means that deploying a new worker version while a workflow is in-flight is safe — the workflow replays history using the new code, but any workflow-versioning incompatibilities surface as `NonDeterminismError` before they corrupt state.

**LangGraph SqliteSaver**: LangGraph's built-in `SqliteSaver` stores the full `StateGraph` state as a JSON blob keyed by `(thread_id, checkpoint_ns, checkpoint_id)`. Each node boundary triggers an automatic save. The implementation uses SQLite's WAL mode for concurrent reads during long-running agents. In development, `MemorySaver` provides the same interface with no persistence — teams swap `MemorySaver` for `SqliteSaver` at deployment time with a one-line change.

**Claude Computer Use Session Persistence**: Anthropic's computer use reference implementation stores screenshot history and action log in a session object that can be serialized between turns. Each `computer_call` result (screenshot + coordinates) is appended to the session log. On user interruption, the session is serialized to disk. On resume, the session is loaded and the agent has full context of what the screen looked like at each prior step, enabling human review before resumption.

---

## 8. Tradeoffs

### Durability Strategy Comparison

| Dimension | In-Memory | DB Checkpoint | Temporal | LangGraph Persistence |
|-----------|-----------|--------------|----------|-----------------------|
| Crash recovery | No | Yes | Yes | Yes |
| Replay fidelity | N/A | Manual (skip by step index) | Full deterministic replay | Full (node-level) |
| Infrastructure deps | None | Postgres or SQLite | Temporal server cluster | SQLite or Postgres |
| Latency overhead per step | ~0ms | 3–10ms | 5–15ms | 5–8ms |
| Versioning support | No | No | Yes (Workflow.get_current_version) | No |
| Human-in-the-loop support | Via flag | Via status field | Via signals + queries | Via interrupt_before |
| Min viable infrastructure | None | Single Postgres instance | 3-node Temporal cluster + Cassandra | Single SQLite file |

### Checkpoint Granularity Tradeoffs

| Checkpoint Every Step | Checkpoint Every N Steps |
|-----------------------|--------------------------|
| Recovery starts at last step | Recovery loses up to N-1 steps |
| 5ms overhead per tool call | N * 5ms amortized (lower overhead) |
| 50 checkpoints for 50 tool calls | 5–10 checkpoints for 50 tool calls |
| Safe for write-heavy agents | Risky: may re-execute write tools |
| Recommended for production | Acceptable only for read-only agents |

---

## 9. When to Use / When NOT to Use

**Durability is required when:**
- Agent task duration exceeds 30 seconds (restart cost becomes user-visible)
- Any tool call has write side effects (file edit, git commit, email send, API POST)
- The task is user-facing and a restart would be unacceptable UX (paid task, time-sensitive)
- The operator charges per completed task — a failed non-durable agent is pure cost with no revenue
- The agent branches on external state that changes over time (e.g., a repo that other engineers are committing to during the task)

**Durability is overkill when:**
- The agent makes 2–3 tool calls and completes in under 5 seconds — restart cost is negligible
- All tool calls are read-only (web search, file read, code grep) — safe to replay from scratch
- The task is idempotent at the task level (re-running from scratch produces the same result safely)
- Latency is the primary constraint and even 5ms checkpoint overhead per step is unacceptable

**When Temporal is worth the infrastructure investment:**
- More than 10,000 agent runs per day — at this scale, operational overhead of Temporal amortizes
- Agents must survive rolling deployments without losing in-flight state
- Workflow logic has complex branching that benefits from Temporal's event history visualization
- Human approval gates are required mid-workflow (Temporal signals are the cleanest mechanism)
- Compliance requires full audit trail of every action taken (Temporal history is immutable and exportable)

**When LangGraph persistence is the right choice:**
- The team is already using LangGraph and does not want to introduce Temporal
- Tasks run for up to a few hours with moderate crash recovery requirements
- The SqliteSaver (development) to PostgresSaver (production) swap path is sufficient

---

## 10. Common Pitfalls

**Pitfall 1 — Inconsistent repo state from non-idempotent git operations (real SWE agent incident pattern)**

A SWE agent was tasked with applying a security patch across 12 files. The agent executed `git_commit` after each batch of edits. At step 9 of 12, a network timeout caused the process to restart. On restart, the agent had no checkpoint and no idempotency keys — it replayed from the beginning. Steps 1–8 re-applied diffs that had already been committed, creating 8 additional commits with duplicate content. The repository history was corrupted and required a manual force-push to recover. The lesson: assign `idempotency_key = SHA256(diff_content)` to every `git_commit` call. On replay, the idempotency cache returns the prior commit SHA and the agent skips the re-commit.

**Pitfall 2 — Checkpoint bloat from storing raw file contents in messages**

A code analysis agent stored the full content of every file it read in the messages list (as tool call results). After 200 tool calls reading large source files, the checkpoint blob grew to 500MB — exceeding the Postgres `text` column practical limit and taking 800ms to serialize/deserialize. The symptom was progressive slowdown: checkpoint saves that started at 5ms grew to 800ms as the agent accumulated more file reads. The fix: store only tool call inputs and summary outputs in the checkpoint; reconstruct the full message list (including raw file contents) from the checkpoint on resume by re-reading files as needed. Checkpoint size target: under 100KB for a 50-tool-call agent.

**Pitfall 3 — Non-deterministic replay causing Temporal NonDeterminismError**

A Temporal-based research agent used `datetime.now()` inside the workflow function to timestamp each tool call result. On worker restart, Temporal replayed the workflow history, but `datetime.now()` returned the current time rather than the original timestamp, producing a different event sequence. Temporal detected the mismatch between replayed events and the stored history and raised `NonDeterminismError`, permanently blocking the workflow. The fix is fundamental: never call side-effectful functions (`datetime.now()`, `uuid.uuid4()`, `random.random()`) directly inside Temporal workflow functions. Pass timestamps as explicit activity parameters, or use Temporal's `workflow.now()` which is deterministic in replay. The same principle applies to any nondeterminism: network calls, file reads, and random number generation must all happen inside activities, not the workflow function.

**Pitfall 4 — Double-side-effects from missing idempotency keys on email send**

A customer support automation agent was designed to send a resolution email after completing a ticket investigation. The email send tool was called without an idempotency key. A transient Redis connectivity issue caused the checkpoint save after the email send to fail. On retry (same run, no crash), the agent re-executed from the last successful checkpoint — which was before the email send — and sent the email a second time. The customer received two identical resolution emails. The fix: always assign idempotency keys to email send, SMS send, and any external notification tool. Key: `SHA256(recipient_address + subject + body_hash)`. Cache the send result in Redis with 24h TTL; on replay, the cached result is returned and the send is skipped.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| Temporal (Go/Python SDK) | Full workflow durability | Python SDK: `temporalio`; event history replay at ~1000 steps/sec; requires Temporal server (self-hosted or Temporal Cloud) |
| LangGraph SqliteSaver | LangGraph-native checkpoint | `langgraph.checkpoint.sqlite.SqliteSaver`; WAL mode; good for development |
| LangGraph PostgresSaver | LangGraph-native checkpoint | `langgraph.checkpoint.postgres.PostgresSaver`; production-grade |
| asyncpg | Postgres async client | Used in custom checkpoint stores; 3–5ms save latency; connection pooling via `create_pool` |
| Redis / aioredis | Idempotency cache | `redis.asyncio`; 24h TTL per idempotency key; ~1ms lookup latency |
| Prefect | Task orchestration | `@task` decorator with result persistence; useful for batch agent pipelines |
| Celery + result backend | Task queue | `celery[redis]`; stores task results in Redis or Postgres; less suited to streaming agent state |
| SQLite | Lightweight local checkpoint | Single file, WAL mode; suitable for development and single-process production agents |

### Temporal vs Database Checkpoint Decision Matrix

| Requirement | DB Checkpoint | Temporal |
|-------------|--------------|----------|
| <1000 runs/day | Sufficient | Overkill |
| Cross-deployment in-flight safety | No | Yes |
| Human signals mid-workflow | Manual polling | Native signals |
| Full audit trail | Application-level | Built-in immutable history |
| Deterministic replay guarantees | No | Yes |
| Setup time | <1 hour | 1–2 days |

---

## 12. Interview Questions with Answers

**Q: What makes a tool call idempotent, and why does the agent caller assign the idempotency key rather than the tool itself?**
A tool call is idempotent if executing it multiple times with the same inputs produces the same observable outcome and no additional side effects on re-execution. The caller assigns the idempotency key rather than the tool because the caller has the semantic context to define the logical identity of the operation — for example, the agent knows that "commit this specific diff to branch X" is the same logical operation regardless of when it is called, and can compute `SHA256(branch + diff_hash)` as the key. The tool only sees raw arguments and cannot determine whether two calls represent the same logical intent or two distinct intended operations. In practice: assign idempotency keys at the `DurableAgentRunner` call site, keyed on `tool_name + sorted_args_hash`.

**Q: What is the optimal checkpoint granularity for a long-running SWE agent, and what are the tradeoffs of checkpointing every N steps instead of every step?**
Checkpoint after every tool call for any agent with write-side-effect tools. The cost of one extra 5ms Postgres write is trivial compared to restarting a 2-hour, $7 agent run from scratch. Checkpointing every N steps reduces overhead but increases the recovery gap: a crash at step N-1 requires re-executing N-1 steps, which risks re-executing write operations if their idempotency keys were not also checkpointed. Every-N-step checkpointing is acceptable only for read-only agents (all tools are safe to replay). In production: always checkpoint every step; the overhead is negligible.

**Q: How does Temporal's workflow replay differ from a database checkpoint-based resume, and when does the difference matter?**
Temporal replays the full deterministic event history of the workflow function at ~1000 steps/sec, re-executing workflow code but returning pre-stored results for completed activities. A database checkpoint-based resume loads the serialized state at the last checkpoint and continues forward — it does not replay code, only restores state. The difference matters in three cases: (1) versioning — Temporal's `Workflow.get_current_version()` handles code changes between checkpoint and resume; a DB checkpoint approach provides no protection against state-schema mismatches after code deployment; (2) audit — Temporal's event history is immutable and queryable; DB checkpoints are mutable blobs; (3) debugging — Temporal's Web UI shows the full event history including which activities ran, how long they took, and what they returned.

**Q: How do you implement clean user interruption in a long-running agent without data loss?**
Poll for an interrupt flag between every tool call — not between LLM inference steps, but between the tool call completion and the next LLM inference. When the flag is set, save the current state with `status="interrupted"` to the checkpoint store before halting. The interrupt flag can be set by a signal handler (OS signal), an HTTP endpoint, or a message queue consumer. On the user-facing side, the interrupt should return a thread ID and the current step number so the user can resume later. Do not raise an exception that bypasses the checkpoint save — the final checkpoint write is the last operation before the process exits. In Temporal: use a workflow signal (`@workflow.signal`) which Temporal delivers reliably even if the worker is processing an activity.

**Q: How do you distinguish read-only tool calls from write-side-effect tool calls at the framework level?**
Define a static registry (`WRITE_SIDE_EFFECT_TOOLS = frozenset({"git_commit", "file_write", "send_email", ...})`) and check membership at the `DurableAgentRunner` level before executing the tool. Alternatively, annotate tools with a metadata field: `tool.metadata["idempotent"] = False`. The framework checks this annotation and automatically generates an idempotency key for any non-idempotent tool. Read-only tools (grep, file read, web fetch, code search, git log) are always safe to re-execute and do not need idempotency keys — re-executing a grep has no side effect. The classification should be conservative: when in doubt, treat a tool as write-side-effect and assign an idempotency key.

**Q: What causes Temporal NonDeterminismError and how do you prevent it?**
Temporal NonDeterminismError occurs when the workflow function produces a different sequence of commands during replay than it did during original execution. This happens when workflow code directly calls side-effectful functions: `datetime.now()`, `uuid.uuid4()`, `random.random()`, or any I/O operation. During replay, Temporal executes the workflow function again but the timestamps differ, the UUIDs differ, or the I/O returns different values — so the command sequence diverges from stored history. Prevention: all nondeterminism must live inside activities (decorated with `@activity.defn`), not inside the `@workflow.run` function. For timestamps: pass `workflow.now()` (deterministic). For random values: generate in an activity and pass as a result. For external reads: wrap in an activity even if the read is cheap.

**Q: How do you manage checkpoint state schema evolution when the agent code changes between runs?**
Use a `schema_version` field in the checkpoint JSON. On load, check the version and apply migration logic if needed: `if state.schema_version < CURRENT_VERSION: state = migrate(state)`. For backward compatibility: add new fields with default values in `from_json` using `d.get("new_field", default)` — this handles checkpoints written before the field existed. For breaking changes (field renamed, type changed): increment `schema_version` and write a migration function. In Temporal, use `Workflow.get_current_version()` with a change ID — this allows in-flight workflows to continue on the old code path while new workflows use the new path, with the version decision recorded in the event history for deterministic replay.

**Q: How do you handle the case where a tool call itself is non-deterministic — e.g., an LLM sub-call that returns different results each time?**
For tool calls that invoke LLMs (sub-agent calls, LLM-graded evaluations), the result is inherently non-deterministic. Store the result in the checkpoint after the first execution, just like any other tool call. On replay, the stored result is returned from `state.idempotency_keys[step_key]` rather than re-executing the LLM call. This is why the idempotency key for an LLM sub-call should be based on the step index and input hash, not a content hash of the output — the output varies, but the logical operation (step N, input X) is deterministic. The checkpoint acts as the source of truth for the outcome of non-deterministic operations.

**Q: What is the cost of durability overhead at scale for a high-volume agent platform?**
At 10,000 agent runs/day with 50 tool calls each, the checkpoint write cost is 500,000 Postgres writes/day. At 5ms per write, that is 2,500 seconds of total write latency — but with 10ms amortized per-write connection pool overhead, this is achievable on a single Postgres instance (typically handles 1,000–5,000 writes/sec sustained). Redis idempotency key lookups: at 3 write tool calls per run × 10,000 runs = 30,000 Redis ops/day — negligible. The total infrastructure cost for durability at this scale: one additional Postgres instance ($50–200/month) plus Redis ($20–100/month). Compared to the cost of re-running failed agents: at $0.15/run (LLM cost), a 5% failure rate × 10,000 runs/day = $75/day in wasted retries. Durability pays for itself within days.

**Q: How do you test a durable agent in a CI/CD pipeline?**
Three test layers: (1) Unit test the `CheckpointStore` with an in-memory or SQLite store — verify that `save` + `load` roundtrips preserve all state fields including tool call history; (2) Integration test the `DurableAgentRunner` by simulating a mid-run crash: run the agent for N steps, call `runner.interrupt()`, instantiate a new runner with the same `thread_id`, and verify it resumes from step N without re-executing completed steps; (3) Idempotency test: call `IdempotentToolExecutor.execute` twice with the same idempotency key and a mock write tool, verify the tool is called exactly once and the second call returns the cached result. Use `pytest-asyncio` for async agent tests. Mock LLM calls with deterministic responses to make tests stable.

**Q: How do you implement a human-in-the-loop approval gate in a durable agent?**
After a high-stakes tool call (e.g., `create_pull_request`, `deploy_to_production`), transition the agent to `status="awaiting_approval"` and save the checkpoint. The agent halts. An external system (webhook, email, Slack) notifies the human reviewer with the proposed action. The human approves or rejects via a UI that calls a resume endpoint. The resume endpoint loads the checkpoint, sets an `approval_granted` flag in state, and re-instantiates the `DurableAgentRunner` which continues past the approval gate. In Temporal, this is a workflow signal: the workflow waits on `await workflow.wait_condition(lambda: self._approved)`, and the human approval system sends a `approve` signal to the workflow ID. The Temporal approach is more reliable because the signal delivery is guaranteed even if the worker restarts while waiting.

**Q: What happens to the idempotency key cache if the agent task is retried after the 24-hour TTL expires?**
After 24 hours, the Redis idempotency cache entries expire. If the same `thread_id` is resumed after 24 hours, the write-side-effect tools will re-execute because the idempotency cache is empty. Whether this is correct depends on the task: for a git commit, re-executing after 24 hours may create a duplicate commit (if the first commit is already in the remote). The mitigation: store idempotency keys persistently in the checkpoint state itself (the `state.idempotency_keys` dict), not only in Redis. On resume, pre-populate the Redis cache from `state.idempotency_keys` before executing any tool calls. This makes the idempotency cache durable across both process restarts and Redis evictions, with Redis serving only as a fast lookup layer.

**Q: How do you handle partial tool call failures — e.g., a tool that writes 10 files but fails on file 7?**
Design write tools to be atomic or to report partial completion. For file-writing tools: either write all files in a single transaction (atomic) or write files one at a time and return a `PartialResult` that lists successfully written files. The `DurableAgentRunner` records the `PartialResult` in the checkpoint. On retry (manual or automatic), the agent issues the tool call again with the same idempotency key, but the idempotency cache returns the `PartialResult` — the agent can inspect which files were written and issue targeted retry calls for the remaining files. The key principle: partial results are better than treating a partial failure as a total failure. Record what succeeded so retry logic can be incremental.

**Q: How should checkpoint state handle secrets — API keys, credentials — that the agent received as tool call results?**
Never store secrets in plaintext checkpoint state. Two options: (1) Encrypt the checkpoint blob using AES-256-GCM with a key stored in a secrets manager (AWS KMS, HashiCorp Vault) — the checkpoint store holds only ciphertext; (2) Store a reference ID rather than the secret itself (e.g., store the token name, not the token value) and re-fetch from the secrets manager on resume. Option 2 is simpler and avoids key rotation issues. The checkpoint should also be stored in a Postgres instance with row-level encryption or in a secrets-manager-backed store, not in a plaintext SQLite file accessible to other processes. Audit log every checkpoint access.

**Q: What is the relationship between agent durability and distributed tracing, and should they share the same thread_id?**
They should use a consistent trace/thread identifier but serve different purposes. The `thread_id` in the checkpoint store is the key for resumption — it must be stable across process restarts and user sessions. The distributed trace `trace_id` links LLM calls, tool calls, and checkpoint operations for observability. Use the same `thread_id` as the trace's root span ID so that Langfuse, Jaeger, or OpenTelemetry traces can be correlated with checkpoint state. When debugging a failed agent run, you can look up `thread_id` in both the checkpoint store (what state was at each step) and the trace system (what latency, what errors) and correlate them to determine exactly which step failed and why.

**Q: How does LangGraph's `interrupt_before` differ from a manual interrupt flag?**
LangGraph's `interrupt_before=["node_name"]` configuration tells the graph executor to pause before executing the named node and return control to the caller, saving the checkpoint at that boundary. It is declarative — you configure which nodes require human approval at graph construction time. A manual interrupt flag (`self._interrupt_requested = True`) is imperative — you set it from outside the agent loop at any time. The LangGraph approach is cleaner for predefined approval gates (always pause before `deploy_node`) and integrates with LangGraph's thread-level state management. The manual flag is more flexible for dynamic interruption (user presses cancel at any point). Production usage: combine both — use `interrupt_before` for known approval gates and a manual flag for user-initiated cancellation.

---

## 13. Best Practices

1. **Checkpoint after every tool call without exception** — the cost of one 5ms Postgres write is negligible compared to restarting a 2-hour, $7 agent run from scratch. Do not attempt to optimize checkpoint frequency until profiling shows it is an actual bottleneck.

2. **Assign idempotency keys to ALL write-side-effect tool calls at the call site, not inside the tool** — the caller knows the semantic identity of the operation; the tool only sees raw arguments. Key format: `SHA256(tool_name + ":" + json.dumps(args, sort_keys=True))`. Consistent serialization (sorted keys) is mandatory for deterministic keys.

3. **Store idempotency keys in the checkpoint state itself, not only in Redis** — Redis TTL expiry, eviction, or a Redis failure must not make a previously-completed write tool re-executable. The checkpoint is the source of truth; Redis is a fast lookup cache.

4. **Keep checkpoint size under 100KB** — store tool call inputs and summary outputs, not raw file contents. For a 50-tool-call agent reading large files, store only the file path and a hash of the content in the checkpoint; re-read the file from disk on resume if the full content is needed.

5. **Never call side-effectful functions inside Temporal workflow functions** — `datetime.now()`, `uuid.uuid4()`, `random.random()`, network calls, and file reads must all live inside `@activity.defn` decorated functions. Any nondeterminism in the workflow function will cause `NonDeterminismError` on replay.

6. **Treat user interruption as a transition to a named state, not as an exception** — catch the interrupt signal, save `state.status = "interrupted"` to the checkpoint store, then raise. Never let an interrupt bypass the final checkpoint save.

7. **Test crash recovery by actually killing the process, not by mocking** — write integration tests that start an agent, run it for N steps, send `SIGKILL`, instantiate a new runner with the same `thread_id`, and verify the agent resumes at step N+1 with no repeated tool executions.

8. **Use a version field in checkpoint JSON and write migration functions** — `schema_version: int` in every checkpoint. When the `AgentState` dataclass adds or removes fields, increment the version and add a `migrate_v1_to_v2(raw: dict) -> dict` function called during `from_json`. This prevents silent data corruption when old checkpoints are loaded by new code.

9. **Pre-warm the Redis idempotency cache from checkpoint state on agent startup** — before executing any tool calls on a resumed run, iterate `state.idempotency_keys` and populate Redis. This ensures idempotency protection even if Redis was flushed while the agent was paused.

10. **Log the `thread_id` in every structured log line and distributed trace span** — when debugging a production failure, the ability to correlate logs, traces, and checkpoint state using a single `thread_id` is the difference between a 5-minute and a 5-hour investigation.

---

## 14. Case Study

### design_autonomous_swe_agent.md — 50+ Tool Call SWE Agent with Checkpoint-Resume

A production SWE agent for automated PR generation operates on tasks that average 47 minutes and 52 tool calls per task. The three write-side-effect operations — `file_write`, `git_commit`, and `create_pull_request` — each receive SHA-256 idempotency keys computed from the content hash and branch name. The checkpoint store is a single Postgres table with `thread_id` as the primary key; each successful tool call triggers an `upsert` of the full `AgentState` JSON blob, taking 4ms on average. The agent runs on spot instances that are preempted at a rate of approximately 2% per hour; for a 47-minute task, the expected probability of at least one preemption is about 1.5%. Without durability, 1.5% of tasks would restart from zero — at $0.90 average LLM cost per task, that is $0.014 wasted per task on average. At 5,000 tasks/day, that is $70/day in wasted compute. The Postgres checkpoint store costs $45/month. The durability infrastructure pays for itself in 18 days and continues to save $70/day thereafter.

### design_browser_research_agent.md — Multi-Hour Deep Research Agent with Interrupt-and-Resume

A competitive intelligence agent conducts deep research tasks that span 2–4 hours and 80–120 tool calls (web fetch, structured extraction, cross-reference). Users frequently start a research task, leave, and want to retrieve results the next morning. The agent implements `status="interrupted"` on user-initiated stop, preserving the full browsing history and extracted data in the checkpoint. On resume the next day, the agent reloads the checkpoint and continues from the last fetched URL. Because all tool calls are read-only (no write side effects), idempotency keys are not required — web fetch is safe to replay. The checkpoint size management challenge: each `web_fetch` result can be 50–200KB of HTML. The solution is to store only the extracted structured data (typically 500 bytes per page) in the checkpoint and re-fetch the raw HTML only if the extraction result is needed again. This keeps checkpoint size under 200KB for 120-page research tasks.

### design_computer_use_agent.md — Computer Use Agent with Per-Action Checkpoints and Confirmation Gates

A computer use agent controlling a desktop environment takes screenshot-action pairs that must be checkpointed individually because each mouse click or keystroke is a real-world state change. The checkpoint includes the last screenshot (base64-encoded PNG, ~50KB after compression) and the full action history. Per-action checkpoint overhead is 60ms (5ms Postgres write + 55ms screenshot compression) — acceptable given that each LLM inference step takes 3–8 seconds. High-risk actions — form submissions, file deletions, send button clicks — trigger an `interrupt_before` gate: the agent pauses, the checkpoint is saved with `status="awaiting_approval"`, and the UI displays the planned action to the user. On approval, the agent resumes and executes the action. On rejection, the user can edit the action parameters before resuming. This confirmation gate pattern reduced unintended action errors from 8% to under 1% in internal testing.

### design_ai_data_analyst.md — Multi-Turn Data Analysis Sessions with Conversation State Persistence

A data analyst agent runs exploratory analysis across multi-turn sessions where users ask follow-up questions referencing prior computations. The session state includes: the data schema, computed intermediate DataFrames (stored as Parquet references, not in-memory objects), SQL queries executed, and chart configurations generated. Each user turn checkpoints the accumulated session state to Postgres. The Parquet references — file paths on a shared object store — are stored in the checkpoint rather than the DataFrame contents, keeping checkpoint size under 10KB regardless of data volume. On session resume (the user returns 3 days later), the agent loads the checkpoint, re-reads the Parquet files from object storage, and reconstructs the in-memory DataFrames. Tool call idempotency is applied to `execute_sql` calls: key = `SHA256(sql_query + data_snapshot_id)`. This prevents re-executing expensive SQL queries (some taking 45 seconds on large tables) when the user resumes and the agent replays recent context.

---

*See also: [`../../agents_and_tool_use/durable_long_running_agents.md`](../../agents_and_tool_use/durable_long_running_agents.md) for the general durability taxonomy | [`../../agentic_frameworks/langgraph.md`](../../agentic_frameworks/langgraph.md) for LangGraph SqliteSaver/PostgresSaver implementation details | [`../../agentic_workflow_patterns/README.md`](../../agentic_workflow_patterns/README.md) for Anthropic's evaluator-optimizer and orchestrator-worker patterns that compose with durability.*
