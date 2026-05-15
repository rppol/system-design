# Agent Reliability — Deep Dive

---

## 1. Concept Overview

Agent reliability is the property of an agentic system to complete assigned tasks correctly and consistently, even in the presence of tool failures, model errors, and unexpected environmental states. It is measured by three operational metrics:

- **Task completion rate**: fraction of tasks the agent successfully completes end-to-end (target: >95% for production).
- **Mean steps to completion**: average number of loop iterations required; rising trend indicates degradation.
- **Stuck rate**: fraction of runs where the agent fails to make progress for N consecutive steps, requiring intervention.

Unlike reliability in conventional software (which means "the service responds within SLA"), agent reliability means "the agent accomplishes the goal despite partial failures along the way." A tool returning an error should not abort the task — it should trigger recovery. A model choosing the wrong tool should not loop forever — it should self-correct or escalate.

Agent reliability matters because production agents fail at high rates without explicit reliability engineering. Empirically: a 10-step agent with 10% per-step failure probability has only a (0.9)^10 = 35% end-to-end success rate without recovery logic. With retry and fallback, the same agent achieves >95%.

---

## 2. Intuition

**One-line analogy**: An agent is a distributed system; every tool call is a network hop that can fail — and like distributed systems, reliability must be engineered in, not assumed.

**Mental model**: A production agent faces the same failure modes as a microservices architecture: transient errors (retry), persistent failures (circuit breaker), dependency unavailability (fallback), and runaway processes (timeout + kill). The same engineering patterns that make microservices reliable — bulkhead isolation, exponential backoff, health checks, graceful degradation — apply directly to agent loops.

**Why it matters**: Without reliability engineering, agents fail silently. The LLM keeps calling failing tools (retry amplification), loops infinitely on ambiguous states (infinite loop), or hangs on a blocked tool call (timeout). Users see a spinner until the job times out or the operator is paged. With reliability engineering, failures are detected early, recovered from automatically where possible, and escalated to humans when not — and the system remains predictable under adversarial conditions.

**Key insight**: The most important reliability mechanism is the step counter combined with a progress detector. A step counter prevents infinite loops; a progress detector catches the subtler case where the agent IS making steps but not making progress (calling different tools but getting the same results).

---

## 3. Core Principles

**Timeout at every boundary**: Every tool call must have a timeout. An agent that waits indefinitely for a slow API blocks its event loop and accumulates cost. Use `asyncio.wait_for` (async) or `signal.alarm` (sync) for all external calls.

**Retry with exponential backoff**: Transient failures (rate limits, network blips, 503s) should trigger automatic retry. Use Fibonacci or exponential backoff with jitter to avoid thundering herd. Cap total retry duration to stay within task latency SLA.

**Circuit breaker**: If a tool fails repeatedly, stop calling it. A circuit breaker transitions from CLOSED (normal) to OPEN (fail fast, no calls) after N consecutive failures, then to HALF-OPEN (allow one test call) after a cooldown period. This prevents retry amplification storms.

**Progress checkpointing**: Save agent state after each successful step. If the agent crashes or is interrupted, resume from the last checkpoint rather than restarting from scratch. This is essential for long-running agents (30+ steps, 10+ minutes).

**Dead-loop detection**: Track not just step count but semantic progress. If the agent has called the same tool with the same arguments twice (or if the last N actions are identical), the agent is stuck in a dead loop. Trigger recovery or escalation.

**Human handoff**: Define explicit escalation criteria. When the agent is stuck (stuck-rate trigger), when all tools have failed, or when confidence is low, surface the current state to a human and pause the agent loop.

**Graceful degradation**: When a tool is unavailable, the agent should fall back to a simpler approach rather than failing entirely. Example: web search fails → use model's training knowledge with a caveat. File write fails → return result in text format. API unavailable → use cached result from last successful call.

---

## 4. Types / Strategies

### 4.1 Optimistic Retry

Retry the failed operation with the assumption that the failure is transient. Use when: the tool's failure mode is "transient" (rate limit, network timeout, 5xx). Limit to 3 retries maximum; use exponential backoff (1s, 2s, 4s). After 3 failures, transition to fallback strategy.

### 4.2 Conservative Fallback

When a tool fails repeatedly, route to an alternative approach. Examples: web search → model knowledge, external API → cached data, primary model → smaller cheaper model. The fallback produces lower quality but keeps the task moving.

### 4.3 Human Handoff

When the agent cannot self-recover, escalate to a human. Surface the current state, the last N actions taken, and the stuck reason. The human can modify state and resume, or abort and provide guidance. Implemented via LangGraph's `interrupt()` or an explicit escalation message to a monitoring channel.

### 4.4 Rollback

For agents that take irreversible actions (file writes, API mutations), checkpoint state before each action and roll back on failure. Implement as a transactional step: acquire lock → write checkpoint → execute action → release lock; on failure, restore from checkpoint.

| Strategy | Use Case | Cost | Recovery Speed |
|---------|---------|------|---------------|
| Optimistic retry | Transient failures | Low | Fast (seconds) |
| Conservative fallback | Tool unavailability | Medium | Fast (immediate) |
| Human handoff | Persistent failure, ambiguity | High | Slow (minutes) |
| Rollback | Action failure with side effects | High | Fast (automatic) |

---

## 5. Architecture Diagrams

### Reliability Wrapper Around Agent Loop

```
                    Task Input
                         |
                         v
              +---------------------+
              |   Pre-flight check  |
              |   (tool health      |
              |    check, budget)   |
              +---------------------+
                         |
                         v
              +---------------------+      timeout exceeded
              |   Step counter      |----> Stuck detector
              |   Max steps check   |           |
              +---------------------+           v
                         |              +----------------+
                         v              |  Human handoff |
              +---------------------+  +----------------+
              |   Agent node        |
              |   (LLM reasoning)   |
              +---------------------+
                         |
                    tool call?
                   /           \
                 YES             NO (final answer)
                  |               |
                  v               v
     +------------------+      Output
     | Tool call wrapper |
     |  - timeout        |
     |  - retry (3x)     |
     |  - circuit breaker|
     +------------------+
           |       |
         OK      FAIL
           |       |
           |       v
           |  +------------------+
           |  | Fallback handler |
           |  | - use alternative|
           |  | - inject error   |
           |  |   observation    |
           |  +------------------+
           |         |
           v         v
    [Inject result into context]
                |
                v
        [Checkpoint state]
                |
                v
        [Back to Agent node]

Dead-loop detector (parallel):
  Track last N action hashes
  If duplicate detected -> trigger Human handoff
```

### Circuit Breaker State Machine

```
         N consecutive failures
CLOSED -----------------------> OPEN
  ^                               |
  |      test call succeeds       |  cooldown period
  +------- HALF-OPEN <-----------+
                |
         test call fails
                |
                v
              OPEN
```

---

## 6. How It Works — Detailed Mechanics

### Tool Call Timeouts

```python
import asyncio
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from typing import Any, Callable

# Async timeout wrapper
async def call_tool_with_timeout(tool_fn: Callable, args: dict, timeout: float = 30.0) -> Any:
    """Execute a tool with a hard timeout."""
    try:
        return await asyncio.wait_for(tool_fn(**args), timeout=timeout)
    except asyncio.TimeoutError:
        raise ToolTimeoutError(f"Tool timed out after {timeout}s: {tool_fn.__name__}")

# Synchronous timeout using threading
import concurrent.futures

def call_tool_sync_with_timeout(tool_fn: Callable, args: dict, timeout: float = 30.0) -> Any:
    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as executor:
        future = executor.submit(tool_fn, **args)
        try:
            return future.result(timeout=timeout)
        except concurrent.futures.TimeoutError:
            raise ToolTimeoutError(f"Tool timed out after {timeout}s")
```

### Retry with Exponential Backoff (tenacity)

```python
from tenacity import (
    retry, stop_after_attempt, wait_exponential,
    retry_if_exception_type, before_sleep_log
)
import logging

logger = logging.getLogger(__name__)

class ToolError(Exception):
    pass

class ToolTimeoutError(ToolError):
    pass

class ToolRateLimitError(ToolError):
    pass

@retry(
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=1, max=10),  # 1s, 2s, 4s
    retry=retry_if_exception_type((ToolTimeoutError, ToolRateLimitError)),
    before_sleep=before_sleep_log(logger, logging.WARNING),
    reraise=True  # re-raise on final failure
)
async def reliable_tool_call(tool_fn: Callable, args: dict) -> Any:
    """Tool call with automatic retry for transient failures."""
    return await call_tool_with_timeout(tool_fn, args, timeout=30.0)

# Usage in agent loop:
try:
    result = await reliable_tool_call(web_search, {"query": state["query"]})
except ToolError as e:
    # All retries exhausted — inject error as observation
    result = f"Tool failed after 3 attempts: {e}. Try an alternative approach."
```

### Circuit Breaker Pattern

```python
from enum import Enum
from dataclasses import dataclass, field
from datetime import datetime, timedelta

class CircuitState(Enum):
    CLOSED = "closed"      # normal operation
    OPEN = "open"          # fail fast
    HALF_OPEN = "half_open"  # testing recovery

@dataclass
class CircuitBreaker:
    failure_threshold: int = 5       # open after 5 consecutive failures
    cooldown_seconds: float = 60.0   # try again after 60s
    state: CircuitState = CircuitState.CLOSED
    failure_count: int = 0
    last_failure_time: datetime | None = None

    def call_allowed(self) -> bool:
        if self.state == CircuitState.CLOSED:
            return True
        if self.state == CircuitState.OPEN:
            if datetime.now() - self.last_failure_time > timedelta(seconds=self.cooldown_seconds):
                self.state = CircuitState.HALF_OPEN
                return True  # allow one test call
            return False
        return True  # HALF_OPEN: allow the test call

    def record_success(self):
        self.failure_count = 0
        self.state = CircuitState.CLOSED

    def record_failure(self):
        self.failure_count += 1
        self.last_failure_time = datetime.now()
        if self.failure_count >= self.failure_threshold:
            self.state = CircuitState.OPEN

# Per-tool circuit breakers
breakers: dict[str, CircuitBreaker] = {}

async def circuit_broken_tool_call(tool_name: str, tool_fn: Callable, args: dict) -> Any:
    breaker = breakers.setdefault(tool_name, CircuitBreaker())

    if not breaker.call_allowed():
        raise ToolError(f"Circuit breaker OPEN for {tool_name} — tool is failing consistently")

    try:
        result = await reliable_tool_call(tool_fn, args)
        breaker.record_success()
        return result
    except ToolError as e:
        breaker.record_failure()
        raise
```

### Progress Checkpointing (LangGraph)

```python
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.postgres import PostgresSaver
from typing import TypedDict, Annotated
from langgraph.graph.message import add_messages

class AgentState(TypedDict):
    messages: Annotated[list, add_messages]
    task_id: str
    step_count: int
    last_progress_marker: str  # tracks semantic progress

# LangGraph automatically checkpoints after every node
# With PostgresSaver, state survives crashes and can be resumed

checkpointer = PostgresSaver.from_conn_string(os.environ["DATABASE_URL"])
app = graph.compile(checkpointer=checkpointer)

# Each invoke with the same thread_id resumes from last checkpoint
config = {"configurable": {"thread_id": f"task-{task_id}"}}
result = await app.ainvoke(initial_state, config=config)

# Manual JSON snapshot for non-LangGraph agents
import json
from pathlib import Path

class ProgressCheckpointer:
    def __init__(self, checkpoint_dir: str = "/tmp/agent_checkpoints"):
        self.checkpoint_dir = Path(checkpoint_dir)
        self.checkpoint_dir.mkdir(exist_ok=True)

    def save(self, task_id: str, state: dict):
        path = self.checkpoint_dir / f"{task_id}.json"
        path.write_text(json.dumps(state, default=str))

    def load(self, task_id: str) -> dict | None:
        path = self.checkpoint_dir / f"{task_id}.json"
        if path.exists():
            return json.loads(path.read_text())
        return None
```

### Dead-Loop Detection

```python
import hashlib
from collections import deque

class DeadLoopDetector:
    def __init__(self, window: int = 5, max_duplicates: int = 2):
        self.window = window          # look at last N actions
        self.max_duplicates = max_duplicates
        self.action_history: deque = deque(maxlen=window)

    def record_action(self, tool_name: str, args: dict) -> bool:
        """Returns True if a dead loop is detected."""
        # Create a hashable fingerprint of the action
        action_hash = hashlib.md5(
            f"{tool_name}:{json.dumps(args, sort_keys=True)}".encode()
        ).hexdigest()

        self.action_history.append(action_hash)

        # Count duplicates in recent window
        if len(self.action_history) == self.window:
            duplicate_count = self.action_history.count(action_hash)
            if duplicate_count >= self.max_duplicates:
                return True  # Dead loop detected

        return False

# Usage in agent loop:
loop_detector = DeadLoopDetector(window=6, max_duplicates=2)

def agent_loop_step(state: AgentState, tool_call: dict) -> AgentState:
    if loop_detector.record_action(tool_call["name"], tool_call["args"]):
        # Dead loop: same tool called with same args twice in last 6 steps
        return escalate_to_human(state, reason="Dead loop detected — agent stuck")

    # ... normal tool execution
```

### Human-in-Loop Handoff Trigger

```python
from langgraph.types import interrupt

def agent_node_with_stuck_detection(state: AgentState) -> dict:
    """Agent node that escalates to human when stuck."""
    # Check stuck conditions
    if state["step_count"] >= 15:
        human_input = interrupt({
            "reason": "max_steps_reached",
            "message": f"Agent reached {state['step_count']} steps without completing the task.",
            "current_state_summary": summarize_state(state),
            "last_3_actions": extract_last_actions(state["messages"], n=3),
            "options": ["continue", "abort", "provide_guidance"]
        })

        if human_input["choice"] == "abort":
            return {"messages": [AIMessage("Task aborted by human reviewer.")]}
        elif human_input["choice"] == "provide_guidance":
            # Human added guidance — inject into messages and reset step count
            guidance = human_input.get("guidance", "")
            return {
                "messages": [SystemMessage(f"Human guidance: {guidance}")],
                "step_count": 0  # reset — fresh start with guidance
            }

    # Normal agent logic
    response = model.invoke(state["messages"])
    return {"messages": [response], "step_count": state["step_count"] + 1}
```

### Graceful Degradation

```python
async def search_with_fallback(query: str) -> str:
    """Try web search; fall back to model knowledge if unavailable."""
    try:
        result = await circuit_broken_tool_call("web_search", web_search, {"query": query})
        return result
    except ToolError as e:
        # Tool is unavailable — degrade gracefully
        degradation_note = (
            f"Note: Live web search is currently unavailable ({e}). "
            "Answering from training knowledge, which may be outdated."
        )
        # Return a marker that the agent can include in its response
        return f"[DEGRADED: {degradation_note}]\n\nBased on training knowledge: ..."
```

---

## 7. Real-World Examples

**Devin (Cognition AI) SWE-Agent Reliability**: Devin operates a software engineering agent with a code sandbox (terminal, file system). Reliability mechanisms: (1) sandbox timeout — each shell command has a 120s timeout; long-running builds are detected and the agent is notified; (2) test-driven recovery — if tests fail, the agent retries with a different approach rather than retrying the exact same code; (3) session persistence — the workspace is preserved so a crash doesn't lose all file edits; (4) the agent emits a confidence score after each action, and low-confidence actions trigger a confirmation step.

**Anthropic Computer Use Retry Logic**: The Computer Use API (Claude 3.5 Sonnet controlling a desktop) implements: (1) screenshot-based progress detection — if the screen hasn't changed after an action, the action likely failed; retry with a modified approach; (2) element detection fallback — if coordinate-based clicking fails (element moved), fall back to accessibility-tree-based selection; (3) explicit error injection — if an action fails, inject the failure screenshot as an observation so the model can reason about what went wrong rather than repeating blindly.

**SWE-bench Agents**: Top-performing SWE-bench agents (Agentless, SWE-agent) achieve 30-50% resolution rates partly through reliability engineering: (1) multi-attempt with different strategies — run the same issue through 3 different code paths, use the one that passes tests; (2) test-time compute — run solutions against a subset of test cases to verify correctness before finalizing; (3) rollback on test failure — if a code edit breaks existing tests, revert the edit and try a different approach.

---

## 8. Tradeoffs

| Dimension | More Aggressive Retry | Less Aggressive Retry |
|-----------|----------------------|----------------------|
| Task completion rate | Higher (more recovery attempts) | Lower (fail fast) |
| Latency | Higher (retries add seconds) | Lower |
| Cost | Higher (more LLM calls per task) | Lower |
| Thundering herd risk | Higher without jitter | Lower |

| Checkpoint Frequency | Per-Step | Per-N-Steps | None |
|---------------------|----------|-------------|------|
| Recovery granularity | Fine (restart from any step) | Coarse (re-run up to N steps) | No recovery |
| Storage cost | High | Medium | None |
| Overhead | ~20ms/checkpoint (Postgres) | Amortized | None |
| Recommended for | Long (>20 step) agents | Medium (5-20 step) agents | Short (<5 step) agents |

| Timeout Value | Too Short | Well-Tuned | Too Long |
|---------------|-----------|-----------|----------|
| Effect | Premature failures on slow tools | Good tradeoff | Agent hangs on truly dead tools |
| P95 impact | Failures at P95 latency | Prevents SLA breach | Blocks agent loop for minutes |

---

## 9. When to Use / When NOT to Use

**Use reliability engineering when:**
- Agent runs for more than 3 steps (any multi-step workflow benefits from step limits and timeout)
- Agent calls external APIs or tools with non-trivial failure rates (>1% error rate)
- Agent tasks take more than 30 seconds (checkpointing prevents costly restarts)
- Agent takes irreversible actions (file writes, API mutations, emails) — always add rollback
- Production deployment with real users — stuck-rate and escalation monitoring required

**Do NOT add all reliability layers when:**
- Simple single-step agents (one tool call, one response) — retry is sufficient, no circuit breaker needed
- Batch evaluation agents (no real-time SLA) — retry with large backoff is enough
- Development/testing — overly complex reliability logic obscures the agent's actual behavior
- Very short tasks (<10s) — per-step checkpointing overhead is disproportionate
- Stateless agents (no side effects) — no need for rollback; re-run is cheap

---

## 10. Common Pitfalls

**Pitfall 1: Retry amplification storm**
Production incident: a web search tool returned 503 for 2 hours during a traffic spike. The agent had `max_retries=10` with 1s backoff. For 1000 concurrent agent runs, this created 10,000 retries/second to an already overloaded search API — worsening the outage. Fix: (1) circuit breaker — after 5 failures, stop calling; (2) jitter — add `random.uniform(0, 1)` to backoff to spread retries; (3) per-service rate limit on retry volume.

**Pitfall 2: Infinite retry loops at the agent level**
A step counter prevents infinite loops at the step level, but not at the retry level. An agent with `max_retries=∞` on each tool call can run a single tool call indefinitely. Always set `stop_after_attempt(3)` in tenacity and cap total task wall time with an outer timeout.

**Pitfall 3: Checkpoint storage cost blowup**
A research agent with 50 steps and 50KB state per step (retrieved documents included) × 10,000 daily tasks = 25GB/day of checkpoint data in Postgres. Fix: (1) store only IDs in state, not full document content; (2) compress checkpoints (zstd: 10× compression on JSON); (3) TTL-expire checkpoints older than 7 days; (4) only checkpoint at N-step intervals for short, cheap tasks.

**Pitfall 4: Dead-loop detector with false positives**
A legitimate research workflow calls `search("LLM architecture")` multiple times with different intents. A naive deduplication hash triggers a false dead-loop detection. Fix: include a sequence index in the action fingerprint, or detect loops only when both tool name AND semantic similarity of results are high (not just argument hash).

**Pitfall 5: Human handoff without context**
Paging a human with "agent stuck" and no context forces them to reconstruct the task from logs. Fix: the escalation payload must include: (1) original task description; (2) last 5 actions with observations; (3) current state summary; (4) specific stuck reason; (5) suggested next actions. A well-formed escalation resolves in 2 minutes; a bare "agent stuck" takes 20.

**Pitfall 6: Circuit breaker shared across tenants**
A multi-tenant agent platform where all tenants share one circuit breaker per tool: one tenant's high failure rate opens the circuit for all tenants. Fix: per-tenant circuit breakers, or aggregate failure rates but only open the circuit when aggregate rate AND per-tenant rate both exceed threshold.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| `tenacity` | Retry library | Python; decorator-based; exponential backoff, stop conditions, retry predicates |
| `pybreaker` | Circuit breaker | Python; configurable thresholds, listeners, Redis-backed state for distributed use |
| `asyncio.wait_for` | Async timeout | Built-in; raises `asyncio.TimeoutError` after deadline |
| `LangGraph checkpointing` | State persistence | `MemorySaver`, `SqliteSaver`, `PostgresSaver`; automatic per-node checkpointing |
| `LangGraph interrupt()` | Human-in-loop | Dynamic interrupt inside node; surfaces state to caller |
| `langsmith` | Observability | Trace every agent step; filter by stuck rate, error rate, step count |
| `redis-py` | Distributed state | Store circuit breaker state across multiple agent workers |
| `aiobotocore` / `boto3` | Checkpoint storage | S3 for large checkpoint blobs; keep only references in DB |

**Concrete numbers for configuration:**
- Retry: 3 attempts max; backoff: 1s, 2s, 4s with ±0.5s jitter
- Circuit breaker: open after 5 consecutive failures; cooldown 60s
- Step limit: set `recursion_limit=25` in LangGraph; add manual `step_count >= 20` check
- Checkpoint: per-step for agents >20 steps; every 5 steps for shorter agents
- Tool timeout: 30s default; 120s for code execution; 10s for search/lookup APIs
- Dead-loop window: 6 steps; trigger if same action appears 2+ times

---

## 12. Interview Questions with Answers

**Q: What is agent reliability and how is it different from service reliability?**
Agent reliability measures whether an agent completes its assigned task correctly and consistently, despite partial failures. Service reliability (uptime, P99 latency) measures whether a service responds within SLA. A service can be 99.9% reliable (responds fast) while an agent deployed on it has 40% task completion rate (the agent loops, retries incorrectly, or gives up prematurely). Agent reliability requires domain-specific metrics: task success rate, mean steps to completion, and stuck rate — none of which are captured by standard service SLOs.

**Q: Why does an N-step agent have compounded failure probability, and how do you fix it?**
If each step succeeds with probability p and steps are independent, an N-step agent succeeds with probability p^N. At p=0.9 and N=10: (0.9)^10 = 35%. At p=0.9 and N=20: (0.9)^20 = 12%. Without recovery, longer tasks have exponentially lower success rates. Fix: reliability mechanisms change the failure model from serial (all steps must succeed) to parallel-with-recovery (each step retries, falls back, or escalates). With retry (3 attempts) per step at p=0.9, per-step success becomes 1-(0.1)^3 = 99.9%, and a 10-step agent succeeds with 99.9^10 = 99%. Recovery changes the math from exponential decay to near-constant success.

**Q: How does a circuit breaker differ from retry, and when should you use each?**
Retry handles transient failures: the assumption is the tool will succeed "eventually" within the current request. Circuit breaker handles persistent failures: the assumption is the tool is broken and calling it repeatedly wastes resources and worsens the outage. Use retry first (3 attempts with backoff) for any transient failure mode. If retry exhausts, record the failure in the circuit breaker. After N consecutive retry-exhausted failures, the circuit opens — subsequent calls fail immediately without attempting the tool. Use circuit breakers when: a tool is calling an external service with known SLA (downtime happens); multiple agent workers share the same downstream dependency; you want to protect a degraded service from being further overwhelmed.

**Q: How do you implement dead-loop detection that avoids false positives?**
Track a rolling window (last 6 steps) of action fingerprints, where a fingerprint is a hash of (tool_name, sorted_args). Trigger dead-loop only when the same fingerprint appears 2+ times in the window. To avoid false positives: (1) include a step sequence number in the fingerprint — same tool called at step 3 vs step 7 with different context is different; (2) use semantic similarity of results, not just argument hashes — if two calls produce different results, the agent IS making progress; (3) allow some repetition for verification patterns (agent calls `read_file` twice to double-check — legitimate). Tune the window and threshold empirically on a sample of successful runs before deploying.

**Q: How does LangGraph's `interrupt()` differ from compile-time `interrupt_before`?**
`interrupt_before=["node_name"]` at compile time always pauses before that specific node, regardless of state. `interrupt()` called inside a node allows conditional, context-aware interrupts: the node executes partially, evaluates the current state, and calls `interrupt(payload)` only when the escalation condition is met. This is more flexible: you can interrupt only when risk level is high, only after N retries, or only when confidence is below a threshold. The `interrupt()` function receives the node's computed context at that moment — richer than compile-time interrupts which only have the state before the node ran.

**Q: What is retry amplification and how do you prevent it?**
Retry amplification occurs when many concurrent agents simultaneously retry a failing tool, multiplying the load on an already degraded service. Example: 500 agents, each retrying 5 times with 1s fixed interval = 2500 requests/second to a service that was handling 100 requests/second before the outage. Prevention: (1) exponential backoff with jitter — spread retries across a time window; (2) circuit breaker — after tool enters OPEN state, retries are blocked (zero amplification); (3) global retry budget — count total retries per tool per minute across all agents; stop retrying when budget exhausted; (4) backpressure — when the retry queue grows, shed new tasks rather than queuing more retries.

**Q: How do you design progress checkpointing that minimizes storage cost?**
Store only references (IDs) in checkpoint state, not full data blobs. A chunk retrieved from a vector store should be stored as `{"chunk_id": "abc-123", "source": "doc42"}`, not the full text. Large intermediate results (generated code, scraped HTML) go to S3 or a blob store; the checkpoint contains only the S3 key. For LangGraph: annotate large fields with a custom serializer that uploads to S3 on write and downloads on read. Frequency: checkpoint every step for agents >20 steps; every 5 steps for shorter agents. TTL: expire checkpoints after `max(task_SLA × 2, 7_days)`. Compression: zstd on the serialized JSON gives 8-12× reduction on typical agent state.

**Q: How do you implement graceful degradation when a tool is permanently unavailable?**
Three-layer degradation: (1) immediate fallback within the same call — if `web_search` fails, try `cached_search` with last-week's index; (2) alternative tool path — if the primary code execution sandbox is down, route to a secondary sandbox or return the code without executing it, with a note that execution failed; (3) prompt-level fallback — inject "Tool X is currently unavailable. Answer from your training knowledge and clearly label this as potentially outdated." into the system prompt. The key design principle: never let tool unavailability propagate as an exception to the end user. Inject it as an observation that the model can reason about and work around.

**Q: How should you set step limits for a production agent?**
Step limits require two mechanisms: (1) LangGraph `recursion_limit` at compile time — this is a hard kill; set it to `expected_max_steps × 1.5`; (2) explicit step counter in state with logic to escalate before the hard limit is hit. Rule of thumb: if a task should take at most 10 steps in 95% of cases, set step limit to 15 and recursion_limit to 25. The gap gives room for legitimate outliers without hitting the hard stop. Monitor step count distribution in production; if P95 step count rises from 10 to 13 over two weeks, the agent is degrading and needs investigation, not just a higher limit.

**Q: What metrics should you track for agent reliability monitoring?**
Core metrics: (1) task success rate — binary per task; alert on >5% drop from rolling 7-day baseline; (2) stuck rate — fraction of tasks requiring human escalation; (3) mean step count — rising trend signals model or tool degradation; (4) tool error rate per tool — identifies unreliable tools; (5) cost per task — rising cost with stable step count means more expensive models or larger contexts. Derived: step count per dollar (efficiency), retry rate per tool (tool health), circuit breaker open events per hour (infra issues). Use LangSmith tags (`thread_id`, `task_type`) to slice these metrics. Set up anomaly detection: alert when any metric deviates >2σ from the 7-day rolling mean.

**Q: How do you handle a tool that returns inconsistent or corrupted results intermittently?**
Three-layer defense: (1) schema validation before injection — parse tool result against expected schema; if invalid, inject a structured error observation ("Tool returned malformed response: {details}") instead of the raw corrupted output; (2) consistency check for high-stakes results — for critical data (prices, legal clauses, medical info), call the tool twice and compare; if results differ by more than a threshold, flag as unreliable and escalate; (3) circuit breaker on schema violation rate — if 20% of responses fail schema validation, open the circuit even if HTTP status is 200. Corrupted results are often worse than no results because they cause confident-but-wrong model reasoning.

**Q: How do production research agents like Devin handle reliability for long multi-step tasks?**
Devin's key reliability mechanisms (inferred from public information): (1) workspace persistence — the development environment (file system, installed packages, terminal history) is checkpointed between steps; a crash does not lose code changes; (2) test-driven recovery — after each code edit, run the relevant test suite; if tests fail, the failure output is injected as an observation for the model to reason about, not retried blindly; (3) confidence-gated actions — high-risk actions (running untested code, deleting files) require an explicit confidence check; low confidence triggers a verification step or human handoff; (4) step budget with adaptation — the step budget is not fixed; Devin adjusts it based on task complexity estimated at planning time, preventing premature timeouts on legitimately complex tasks.

**Q: What is the human handoff design pattern and what information should the escalation payload contain?**
Human handoff pauses the agent and routes to a human when automated recovery has failed. The escalation payload should contain: (1) `task_description` — original goal, verbatim; (2) `completed_steps` — summary of what was accomplished before getting stuck; (3) `last_actions` — last 3-5 tool calls with args and results, raw; (4) `stuck_reason` — specific triggering condition (max steps, dead loop, tool failure); (5) `suggested_options` — ["continue with guidance", "abort", "retry from step N"]; (6) `estimated_resume_steps` — how much work remains if the human provides guidance. This gives a human reviewer enough context to make a decision in 2-3 minutes. Present it in a Slack message or a UI modal, not as a raw JSON dump.

**Q: How do you test agent reliability without running the full agent in production?**
Four testing strategies: (1) fault injection testing — mock tool layer to inject failures at specified rates; verify the agent recovers correctly; test circuit breaker state transitions explicitly; (2) chaos testing — randomly kill tool calls mid-execution; verify checkpointing and resumption work; (3) dead-loop injection — craft inputs designed to cause the agent to loop; verify dead-loop detection triggers; (4) step budget simulation — replay production traces with reduced step budgets to understand where agents hit limits. Use `MemorySaver` checkpointer in tests. Assert on: final task success, number of retries, whether human handoff was triggered, and checkpoint frequency. Run reliability tests on every PR that modifies agent logic.

**Q: How does progress checkpointing interact with idempotency for agents that call external APIs?**
When an agent checkpoints after a successful API mutation (e.g., sent an email, created a ticket), a crash-and-resume scenario will NOT re-execute that step (the state records it as done). But if the agent crashes DURING the API call (after the call succeeds but before the checkpoint is written), it will re-execute on resume — potentially sending two emails. Solutions: (1) idempotency keys — include a `step_uuid` in API calls; the downstream service deduplicates; (2) transactional checkpointing — write checkpoint atomically with the API call in a two-phase commit pattern; (3) at-most-once semantics — mark the action as "in-flight" before calling, checkpoint, call the API, mark "completed"; on resume, skip if marked "completed"; (4) use LangGraph's `interrupt_before` for irreversible actions — always require human confirmation before the API call, so the state is clean before the risky step.

---

## 13. Best Practices

1. **Layer reliability mechanisms** — timeout + retry + circuit breaker together; each handles a different failure mode. Timeout alone does not prevent retry amplification; circuit breaker alone does not handle transient failures.
2. **Set step limits at two levels** — `recursion_limit` as a hard kill, explicit `step_count` check for graceful escalation at a lower threshold.
3. **Include jitter in all retry backoff** — without jitter, all retrying agents synchronize, creating request bursts on the recovering tool.
4. **Checkpoint state before irreversible actions** — never execute a destructive action without a checkpoint to roll back to.
5. **Define escalation criteria explicitly** — don't wait until after launch to decide when to call humans; write the escalation conditions in the system spec.
6. **Test with fault injection, not just happy path** — run reliability tests with 10%, 30%, and 50% tool failure rates before production.
7. **Monitor stuck rate as the primary reliability KPI** — task success rate masks partial failures; stuck rate reveals where agents need help.
8. **Store only references in checkpoint state** — never store full document content or binary data; use S3 keys with on-demand loading.
9. **Use per-tool circuit breakers, not a global one** — a global circuit breaker can be triggered by one unreliable tool, blocking access to all tools.
10. **Validate tool results before injection** — parse and schema-check every tool response; inject structured error observations for invalid responses rather than raw corrupted output.

---

## 14. Case Study: Production Research Agent with Full Reliability Stack

**Scenario**: A financial research firm deploys an agent that, given a company ticker, produces a 10-page competitive intelligence report. The agent calls web search, SEC filings retrieval, earnings transcript summarization, competitor analysis, and a report generation step — 25-35 steps per run, 20-minute wall time.

### Baseline (no reliability engineering)

- Tool error rate: 10% per call (SEC API has known instability)
- Per-step success: 90%
- 30-step completion rate: (0.9)^30 = 4.2%
- Outcome: 96% of runs fail; operators spend 4 hours/day manually restarting failed jobs.

### Reliability Stack Design

```
Tool calls:
  - Timeout: 30s per call (SEC API), 10s (web search)
  - Retry: 3 attempts, exponential backoff with jitter (1s, 2s, 4s ± 0.5s)
  - Circuit breaker per tool: open after 5 consecutive failures, 60s cooldown

Agent loop:
  - Step limit: escalate at step 25, hard kill at step 35
  - Dead-loop detector: window=8, duplicate threshold=2
  - Checkpoint: LangGraph PostgresSaver after every step

Fallback paths:
  - SEC API down: fall back to cached last-quarter filing
  - Web search down: use model knowledge with "as of training cutoff" caveat
  - Earnings transcript API down: use news sources + press releases

Human handoff triggers:
  - Step 25 reached without completion
  - Dead loop detected (same tool, same args, twice in window)
  - Circuit breaker for 2+ tools simultaneously open
  - Confidence score < 0.4 on final report generation step

Checkpoint strategy:
  - Store document IDs in state (not full text)
  - Full documents in S3 with 7-day TTL
  - Checkpoint compressed with zstd (12× compression on typical state)
  - Storage: ~4KB/step × 30 steps × 500 runs/day = 60MB/day (manageable)
```

### Key Implementation Excerpt

```python
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.postgres import PostgresSaver
from langgraph.types import interrupt

class ResearchState(TypedDict):
    messages: Annotated[list, add_messages]
    ticker: str
    report_sections: Annotated[dict, merge_dicts]  # custom reducer
    step_count: int
    tool_errors: Annotated[dict, merge_dicts]  # per-tool error counts
    s3_document_keys: Annotated[list, operator.add]

loop_detector = DeadLoopDetector(window=8, max_duplicates=2)

async def research_agent_node(state: ResearchState) -> dict:
    if state["step_count"] >= 25:
        human_input = interrupt({
            "reason": "approaching_step_limit",
            "completed_sections": list(state["report_sections"].keys()),
            "missing_sections": ["financials", "risk_factors"]
        })
        if human_input.get("continue"):
            return {"step_count": 0}  # reset with human approval

    response = await model.ainvoke(state["messages"])

    # Dead-loop check before executing tool
    if response.tool_calls:
        tc = response.tool_calls[0]
        if loop_detector.record_action(tc["name"], tc["args"]):
            human_input = interrupt({"reason": "dead_loop", "stuck_tool": tc["name"]})

    return {"messages": [response], "step_count": state["step_count"] + 1}

async def tool_node(state: ResearchState) -> dict:
    last_msg = state["messages"][-1]
    results = []
    for tc in last_msg.tool_calls:
        try:
            result = await circuit_broken_tool_call(tc["name"], tools[tc["name"]], tc["args"])
            results.append(ToolMessage(content=str(result), tool_call_id=tc["id"]))
        except ToolError as e:
            # Inject structured error — agent recovers
            results.append(ToolMessage(
                content=f"[TOOL_ERROR] {tc['name']} failed: {e}. Use fallback or alternative.",
                tool_call_id=tc["id"]
            ))
            return {
                "messages": results,
                "tool_errors": {tc["name"]: state["tool_errors"].get(tc["name"], 0) + 1}
            }
    return {"messages": results}

checkpointer = PostgresSaver.from_conn_string(os.environ["DATABASE_URL"])
app = graph.compile(checkpointer=checkpointer)
```

### Results After Deploying Reliability Stack

| Metric | Before | After |
|--------|--------|-------|
| Task completion rate | 4.2% | 99.2% |
| Mean steps to completion | N/A (mostly failed) | 28 steps |
| Human escalation rate | 96% (manual restart) | 1.8% |
| Mean task wall time | N/A | 22 minutes |
| Tool error amplification incidents | 3/week | 0/week |
| Storage cost (checkpoints) | 0 | 60MB/day |
| Operator intervention hours/day | 4 hours | 15 minutes |

**Key insight**: The largest gains came from retry (transient SEC API failures) and circuit breaker (prevents cascading failures during SEC API outages). Checkpointing alone saved 40% of the operator intervention time by enabling resume instead of restart.
