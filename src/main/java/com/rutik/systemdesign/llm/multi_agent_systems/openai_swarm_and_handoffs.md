# OpenAI Swarm and Agents SDK — Deep Dive

---

## 1. Concept Overview

The **OpenAI Agents SDK** (`pip install openai-agents`) is the framework for building multi-agent systems on OpenAI models, where several LLM-powered agents collaborate by handing off control to one another. Its central primitive is the **Agent**: a named entity with a system prompt (instructions), a list of callable tools, and a declared set of peers it may transfer control to. Around that it adds async execution, streaming, typed context, guardrails, retries, and tracing that is on by default.

This file also uses **Swarm** (October 2024) as a teaching foil. Swarm is a ~100-line experimental repo, never published to PyPI, that OpenAI now points at the Agents SDK for anything real — but its source is short enough to read end to end, which makes it the clearest way to see that *a handoff is just a tool call whose return value happens to be an agent*. Read §4.1 and §6.1 for the mechanism, then build on the SDK. The two APIs are not interchangeable: they differ in the fields you pass, the return types, and where handoffs are declared (§4.1 vs §4.2).

**Version this file targets (checked July 2026).** Agents SDK code below targets the `openai-agents` PyPI package (current release line 0.18.x) and was checked signature-by-signature against `openai/openai-agents-python` on `main`. Swarm code targets `openai/swarm` on `main`.

Both frameworks solve the same problem: a single LLM context window is not the right place to handle every subtask. Routing specialised work to specialised agents keeps prompts short, tools focused, and behaviour predictable.

This file covers the multi-agent handoff pattern itself; the framework-level Agents SDK deep dive (Runner internals, tracing, output_type) lives in [openai_agents_sdk.md](../agentic_frameworks/openai_agents_sdk.md).

---

## 2. Intuition

Think of a hospital emergency department. A triage nurse (Triage Agent) talks to every patient first. Based on symptoms she routes the patient to Cardiology (Billing Agent) or Neurology (Technical Agent). Each specialist has their own protocols (instructions), equipment (tools), and does not need to know the triage nurse's full history — only the relevant handoff note.

One-line analogy: Swarm / Agents SDK is a traffic cop that redirects callers to the right specialist counter, passing a sticky note (context variables) with each redirect.

Mental model:
- Agent = function with a personality and a list of colleagues it can call
- Handoff = return value that says "I am done; hand control to Agent X"
- Context variables = a shared clipboard any agent can read or write

Why it matters: Without structured handoffs, a single mega-prompt tries to handle billing, technical support, and sales simultaneously, leading to prompt bloat, hallucination, and poor specialisation. Handoffs enforce single-responsibility at the agent level.

Key insight: Handoffs are just tool calls in disguise. The LLM produces a function call whose return value is an Agent object instead of data. The runner interprets this and switches the active agent.

---

## 3. Core Principles

1. **Agent as first-class primitive.** In the Agents SDK an agent is defined by (name, instructions, tools, handoffs). Instructions are the system prompt. Tools are `@function_tool`-decorated callables. Handoffs are other Agent objects the runner is allowed to transfer control to. Swarm has no separate `handoffs` field at all — it has one list, `functions`, and a "handoff" is simply a function in that list whose return value happens to be an `Agent`.

2. **Handoff via special return type.** In the Agents SDK the runner auto-generates a `transfer_to_<agent>` tool from each entry in `handoffs`; when the model calls it, the runner switches the active agent without another round-trip to the user. In Swarm the same effect comes from the return value: a plain function returns an `Agent` (or a `Result` carrying one) and `Swarm.run` sees the `Agent` and switches.

3. **Context variables propagate.** A dict of context variables is threaded through every agent invocation. Any agent or tool can modify it. The next agent in the chain sees the updated dict.

4. **Stateless turns (Swarm) vs persistent runs (Agents SDK).** Swarm requires the caller to maintain and pass message history on every call. The Agents SDK maintains a RunResult that accumulates the full conversation internally across turns.

5. **Guardrails as cross-cutting concerns.** Input guardrails run before the LLM call; output guardrails run after. Either can abort the run by raising — `InputGuardrailTripwireTriggered` or `OutputGuardrailTripwireTriggered` respectively; there is no combined `GuardrailTripwireTriggered` class. This keeps safety logic out of agent instructions. Guardrail design beyond this SDK (NeMo Guardrails, Llama Guard) is covered in [Guardrails & Content Safety](../guardrails_and_content_safety/README.md).

6. **Routines encode business flows.** A routine is an ordered series of steps baked into an agent's instructions (e.g., "Step 1: greet. Step 2: qualify. Step 3: pitch. Step 4: close."). The LLM follows the routine like a script.

---

## 4. Types / Architectures / Strategies

### 4.1 Swarm Agent Primitive (2024)

Swarm's `Agent` is a pydantic model with exactly these fields: `name`, `model`, `instructions`, `functions`, `tool_choice`, `parallel_tool_calls`. There is **no `tools` field and no `handoffs` field** — everything, including handoffs, goes in `functions`:

```python
from swarm import Agent

agent = Agent(
    name="Triage",
    instructions="You are a triage agent. Route issues.",
    # ONE list. Data tools and handoff functions live side by side.
    functions=[lookup_account, transfer_to_billing, transfer_to_technical],
)
```

`Swarm().run(agent=..., messages=..., context_variables=...)` is synchronous. It returns a `Response` with three fields — `messages`, `agent` (the agent that was active when the run ended) and `context_variables`. The caller must append messages and call again for multi-turn.

### 4.2 Agents SDK Agent Primitive (2025)

```python
from agents import Agent, Runner

agent = Agent(
    name="Triage",
    instructions="You are a triage agent. Route issues.",
    tools=[lookup_account],                     # @function_tool-decorated callables
    handoffs=[billing_agent, technical_agent],
    input_guardrails=[pii_guardrail],
    output_guardrails=[toxicity_guardrail],     # PLURAL, and a list
    model="gpt-5.6-terra",
)

result: RunResult = await Runner.run(agent, input="My bill is wrong")
```

`Runner.run` is async (`Runner.run_sync` is the blocking variant). `RunResult` exposes `final_output`, `last_agent`, `new_items`, `raw_responses`, `input_guardrail_results`, `output_guardrail_results` and `to_input_list()`. Note what it does **not** expose: there is no `.messages` attribute (use `to_input_list()` or `new_items`) and no `.trace_url` attribute — traces are viewed in the OpenAI Traces dashboard, not read off the result object. Streaming is available via `Runner.run_streamed`.

### 4.3 Handoff Strategies

| Strategy | Description | Use Case |
|----------|-------------|----------|
| Triage → Specialist | One router agent dispatches to N specialist agents | Customer support, help desk |
| Chain | Agent A always hands off to Agent B then Agent C | Multi-step workflows |
| Hub and Spoke | Central orchestrator dispatches and receives returns | Research pipelines |
| Escalation | Specialist hands back up to supervisor on failure | Complex dispute resolution |
| Parallel (SDK only) | Runner spawns multiple agents concurrently | Report generation, data enrichment |

Hub-and-spoke is the same topology as the [orchestrator-worker pattern](orchestrator_worker_pattern.md), which has its own deep dive covering task ledgers and result aggregation.

### 4.4 Routines

A routine is a numbered list of steps in the agent's instructions. The agent works through them in order, calling tools as needed, and only hands off when the routine says to.

```
Instructions for Sales Agent:
1. Greet the customer by name using get_customer_name().
2. Ask one qualifying question about their pain point.
3. Present the relevant product using get_product_info().
4. Address objections. If unresolvable, call transfer_to_human().
5. Attempt to close. If successful, call create_order().
```

### 4.5 Agents as Tools (the Manager Pattern) — the alternative to handing off

A handoff is a one-way door: control moves to the specialist and the specialist owns the rest of
the conversation. That is wrong whenever a specialist should do a **bounded piece of work and give
control back**. The Agents SDK's answer is `Agent.as_tool()`, which wraps an agent as an ordinary
callable tool on another agent — the "Parallel (SDK only)" row of §4.3 and the only way to get
fan-out on this SDK, because a run has exactly one active agent and therefore cannot hand off to
two specialists at once.

```python
orchestrator_agent = Agent(
    name="orchestrator_agent",
    instructions="You are a translation agent. You use the tools given to you to translate.",
    tools=[
        spanish_agent.as_tool(
            tool_name="translate_to_spanish",
            tool_description="Translate the user's message to Spanish",
        ),
        french_agent.as_tool(
            tool_name="translate_to_french",
            tool_description="Translate the user's message to French",
        ),
    ],
)
```

Each `as_tool` call starts a **nested run**. The manager stays the active agent throughout, so
`RunResult.last_agent` is still the manager and `final_output` is the manager's synthesis, not the
specialist's raw text. Two consequences people trip on: the nested agent sees only the argument
the manager passed, not the conversation so far (history sharing is opt-in via the `session` /
`conversation_id` / `previous_response_id` parameters), and its output arrives as a tool result
string — use `custom_output_extractor` to pull structured fields out before the manager reasons
over it. Other parameters worth knowing: `parameters` for a typed Pydantic input instead of a bare
string, `is_enabled` to switch a specialist off at runtime, `needs_approval` to gate it behind
human review, and `max_turns` / `run_config` / `hooks` to bound and instrument the nested run.
When `as_tool` is not configurable enough, the escape hatch is a plain `@function_tool` that calls
`await Runner.run(specialist, ...)` itself.

Choosing between the two is a control question, not a capability question. Hand off when the
specialist should **own the user-facing conversation** from here on — triage to billing, and
billing answers the customer directly. Use agents-as-tools when the specialist should **help and
return** — translate this string, score this résumé, summarise this PDF — and the manager must
still merge several such results, enforce a house voice, or apply its own output guardrail to the
combined answer. This is the same shape as the [orchestrator-worker
pattern](orchestrator_worker_pattern.md), expressed in SDK primitives, so it inherits that
pattern's cost profile: the manager pays for its own context plus every nested run.

---

## 5. Architecture Diagrams

### 5.1 Swarm Request Lifecycle

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Caller(["Caller\nmessages + context_variables"]) --> RunCall["Client.run(triage_agent, messages, ctx)"]
    RunCall --> Triage["Triage Agent LLM call"]
    Triage -- "tool call: transfer_to_billing_agent()" --> Detect["Runner detects Handoff(agent=billing_agent)"]
    Detect --> Billing["Billing Agent LLM call\nnew system prompt, same messages"]
    Billing -- "'Your refund is $45.'" --> Response["Response(messages=(...), context_variables={...})"]
    Response --> Caller2(["Caller appends messages\ncalls Client.run again for next turn"])

    class Caller,Caller2,Response io
    class RunCall req
    class Detect mathOp
    class Triage,Billing base
```

### 5.2 Agents SDK Run Lifecycle

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Input([User Input]) --> RunnerRun["Runner.run(triage_agent, input)"]
    RunnerRun --> Guard{"input_guardrail"}
    Guard -- "InputGuardrailTripwireTriggered" --> Blocked([Blocked])
    Guard -- pass --> LLMCall["LLM Call (triage_agent)"]
    LLMCall --> ToolCalls{"tool calls?"}
    ToolCalls -- YES --> ExecTools["execute tools\nappend results"] --> LLMCall
    ToolCalls -- handoff --> Switch["switch active_agent\ncarry context_variables"] --> LLMCall
    ToolCalls -- "final text" --> OutGuard["output_guardrails"] --> Result["RunResult\n.final_output | .last_agent\n.new_items | .to_input_list()"]

    class Input,Result io
    class RunnerRun req
    class Guard,OutGuard,Blocked lossN
    class LLMCall base
    class ToolCalls,ExecTools,Switch mathOp
```

### 5.3 Context Variable Flow

```
ctx = {"customer_id": "C123", "plan": "pro", "issue_category": None}

Triage Agent
  calls lookup_account(ctx["customer_id"])  --> updates ctx["plan"] = "enterprise"
  handoff --> Billing Agent

Billing Agent
  reads ctx["plan"] = "enterprise"
  applies enterprise discount logic
  updates ctx["refund_amount"] = 45.00
  returns final answer
```

### 5.4 Guardrail Position

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    UserMsg(["User message"]) --> InputG["input_guardrail<br/>runs BEFORE LLM<br/>blocks PII, injection attempts"]
    InputG --> LLM["LLM generates response"]
    LLM --> OutputG["output_guardrail<br/>runs AFTER LLM<br/>blocks toxic / off-policy output"]
    OutputG --> Delivered(["Delivered to caller"])

    class UserMsg,Delivered io
    class InputG,OutputG lossN
    class LLM base
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Swarm — Complete Customer Service Example

```python
# pip install git+https://github.com/openai/swarm.git (educational library, not production;
# never published to PyPI — the "swarm" package on PyPI is unrelated)
from swarm import Swarm, Agent

client = Swarm()  # wraps openai.OpenAI()

# --- Tool functions ---

def lookup_account(customer_id: str, context_variables: dict) -> str:
    """Simulate a DB lookup."""
    context_variables["plan"] = "enterprise"
    return f"Account {customer_id} found. Plan: enterprise."

def get_billing_history(context_variables: dict) -> str:
    return "Last invoice: $299 on 2026-04-01. Status: paid."

def reset_password(context_variables: dict) -> str:
    return "Password reset email sent to registered address."

# --- Agent definitions (forward refs resolved after all agents exist) ---

def transfer_to_billing():
    """Hand off to the billing specialist."""
    return billing_agent  # returns Agent object — Swarm detects this

def transfer_to_technical():
    """Hand off to the technical specialist."""
    return technical_agent

billing_agent = Agent(
    name="Billing Agent",
    instructions=(
        "You are a billing specialist. Answer billing questions using "
        "get_billing_history(). Be concise and empathetic."
    ),
    functions=[get_billing_history],   # NOTE: `functions`, not `tools`
)

technical_agent = Agent(
    name="Technical Agent",
    instructions=(
        "You are a technical support specialist. Help with account access "
        "and technical issues using reset_password()."
    ),
    functions=[reset_password],
)

triage_agent = Agent(
    name="Triage Agent",
    instructions=(
        "You are the first point of contact. "
        "1. Call lookup_account with the customer_id from context_variables. "
        "2. If the issue is about billing or invoices, transfer to Billing Agent. "
        "3. If the issue is about passwords or technical problems, transfer to Technical Agent. "
        "4. Otherwise, answer directly."
    ),
    # Handoff functions are just entries in `functions` — Swarm has no `handoffs` field.
    functions=[lookup_account, transfer_to_billing, transfer_to_technical],
)

# --- Run ---

context = {"customer_id": "C789"}
messages = [{"role": "user", "content": "My invoice is wrong, I was charged twice."}]

response = client.run(
    agent=triage_agent,
    messages=messages,
    context_variables=context,
)

print(response.messages[-1]["content"])
# Output: "I've checked your account (enterprise plan). Your last invoice was $299 on
#          2026-04-01 and shows as paid. Could you clarify which charge looks duplicated?"
print(response.context_variables)
# {"customer_id": "C789", "plan": "enterprise"}
```

### 6.2 Agents SDK — Async Production Version

```python
# pip install openai-agents
import asyncio
from agents import Agent, Runner, RunContextWrapper, GuardrailFunctionOutput, function_tool
from agents import input_guardrail, output_guardrail
# There is NO generic `GuardrailTripwireTriggered`; the SDK raises one of these two.
from agents.exceptions import (
    InputGuardrailTripwireTriggered,
    OutputGuardrailTripwireTriggered,
)
from dataclasses import dataclass

# --- Context type (typed context variables) ---

@dataclass
class SupportContext:
    customer_id: str
    plan: str = "free"
    refund_amount: float = 0.0

# --- Guardrails ---

@input_guardrail
async def pii_guardrail(
    ctx: RunContextWrapper[SupportContext], agent: Agent, input: str
) -> GuardrailFunctionOutput:
    """Block messages containing raw credit card numbers."""
    import re
    if re.search(r"\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b", input):
        return GuardrailFunctionOutput(
            output_info="credit_card_detected",
            tripwire_triggered=True,  # raises InputGuardrailTripwireTriggered
        )
    return GuardrailFunctionOutput(output_info="ok", tripwire_triggered=False)

@output_guardrail
async def length_guardrail(
    ctx: RunContextWrapper[SupportContext], agent: Agent, output: str
) -> GuardrailFunctionOutput:
    """Warn if agent response is suspiciously short (< 20 chars)."""
    if len(output) < 20:
        return GuardrailFunctionOutput(output_info="too_short", tripwire_triggered=True)
    return GuardrailFunctionOutput(output_info="ok", tripwire_triggered=False)

# --- Tools ---
# A bare callable is NOT a Tool. Every Python function must be wrapped with
# @function_tool (or a hand-built FunctionTool) before it can go in `tools=[...]`.
# When a tool needs the run context, RunContextWrapper must be the FIRST parameter.

@function_tool
async def lookup_account(ctx: RunContextWrapper[SupportContext]) -> str:
    """Load the customer record."""
    # In production: await db.fetch(ctx.context.customer_id)
    ctx.context.plan = "enterprise"
    return f"Customer {ctx.context.customer_id}: enterprise plan, active."

@function_tool
async def get_billing_history(ctx: RunContextWrapper[SupportContext]) -> str:
    """Return the customer's recent invoices."""
    return "Invoice #1042: $299 on 2026-04-01 (paid). Invoice #1041: $299 on 2026-03-01 (paid)."

@function_tool
async def issue_refund(
    ctx: RunContextWrapper[SupportContext], amount: float
) -> str:
    """Issue a refund of the given amount."""
    ctx.context.refund_amount = amount
    return f"Refund of ${amount:.2f} initiated. ETA: 3-5 business days."

@function_tool
async def reset_password(ctx: RunContextWrapper[SupportContext]) -> str:
    """Send a password reset email."""
    return "Password reset email sent."

# --- Agent definitions ---

billing_agent = Agent[SupportContext](
    name="Billing Agent",
    model="gpt-5.6-terra",
    instructions=(
        "You are a billing specialist. "
        "Use get_billing_history() to review charges. "
        "Use issue_refund(amount) if a refund is warranted. "
        "Be empathetic and concise. Do not discuss technical issues."
    ),
    tools=[get_billing_history, issue_refund],
    output_guardrails=[length_guardrail],
)

technical_agent = Agent[SupportContext](
    name="Technical Agent",
    model="gpt-5.6-terra",
    instructions=(
        "You are a technical support specialist. "
        "Use reset_password() for access issues. "
        "Do not discuss billing."
    ),
    tools=[reset_password],
    output_guardrails=[length_guardrail],
)

triage_agent = Agent[SupportContext](
    name="Triage Agent",
    model="gpt-5.6-terra",
    instructions=(
        "You are the first point of contact for customer support. "
        "Step 1: Call lookup_account() to load the customer record. "
        "Step 2: Classify the issue. "
        "  - Billing / invoice / charge / refund -> transfer to Billing Agent. "
        "  - Password / login / technical -> transfer to Technical Agent. "
        "  - General -> answer directly. "
        "Step 3: Transfer using the appropriate handoff."
    ),
    tools=[lookup_account],
    handoffs=[billing_agent, technical_agent],
    input_guardrails=[pii_guardrail],
)

# --- Runner ---

async def handle_customer_query(customer_id: str, message: str) -> None:
    ctx = SupportContext(customer_id=customer_id)
    try:
        result = await Runner.run(
            starting_agent=triage_agent,
            input=message,
            context=ctx,
            max_turns=10,  # safety limit; default is 10
        )
        print(f"Final answer: {result.final_output}")
        print(f"Handled by: {result.last_agent.name}")
        print(f"Refund initiated: ${ctx.refund_amount:.2f}")
        # RunResult has no `.trace_url`. Traces appear in the OpenAI Traces dashboard;
        # correlate by wrapping the call in `with trace("customer-query", group_id=...)`.
        print(f"Items produced: {len(result.new_items)}")
    except (InputGuardrailTripwireTriggered, OutputGuardrailTripwireTriggered) as e:
        print(f"Request blocked by guardrail: {e.guardrail_result.output.output_info}")

asyncio.run(handle_customer_query("C789", "I was charged twice on my last invoice."))
```

### 6.3 Streaming Example

```python
async def stream_response(customer_id: str, message: str) -> None:
    ctx = SupportContext(customer_id=customer_id)
    # Runner.run_streamed is a SYNC call returning RunResultStreaming.
    # It is NOT awaited and NOT an async context manager.
    result = Runner.run_streamed(triage_agent, message, context=ctx)
    async for event in result.stream_events():
        if event.type == "raw_response_event":
            # token-level streaming; event.data is a Responses API streaming event
            if getattr(event.data, "type", "") == "response.output_text.delta":
                print(event.data.delta, end="", flush=True)
        elif event.type == "agent_updated_stream_event":
            print(f"\n[Switched to: {event.new_agent.name}]")
        elif event.type == "run_item_stream_event":
            # higher-level items: message_output_item, tool_call_item,
            # tool_call_output_item, handoff_output_item, ...
            print(f"\n[item: {event.item.type}]")
    print(f"\n[Final output: {result.final_output}]")
```

### 6.4 Handoff Mechanics — Internal Flow

```
1. triage_agent LLM produces:
   {"tool_calls": [{"function": {"name": "transfer_to_billing_agent", "arguments": "{}"}}]}

2. Runner recognises "transfer_to_billing_agent" as a registered Handoff function
   (auto-generated from triage_agent.handoffs = [billing_agent]).

3. Runner sets active_agent = billing_agent.
   The handoff still has to be closed out as a tool call, so a tool result is appended --
   but it carries a handoff acknowledgement rather than any data the model can reason over:
   {"role": "tool", "content": "Transferred to Billing Agent", "tool_call_id": "..."}

4. Runner calls billing_agent LLM with:
   - system: billing_agent.instructions
   - messages: full conversation history so far (including triage turns)
   - context: same SupportContext object (shared by reference)

5. billing_agent completes the conversation and returns a text response.

6. Runner sets RunResult.last_agent = billing_agent, returns to caller.
```

---

## 7. Real-World Examples

### 7.1 Support-Ticket Triage (the canonical Swarm example)

The shape Swarm's own repository uses to teach the pattern, and the one most teams copy first: a triage agent reads the ticket subject and first message, then routes to billing, safety, API-technical, or enterprise specialist agents. Each specialist carries a focused system prompt of a couple hundred words instead of a shared thousand-word mega-prompt. (OpenAI publishes Swarm's examples but no public account of running it on its own support queue — treat this as the reference topology, not a documented OpenAI deployment.)

### 7.2 E-Commerce Order Management

Triage Agent classifies messages: returns, shipping, product questions. Order Status Agent calls the OMS API. Returns Agent initiates RMA and calls the refund API. Shipping Agent calls the carrier API for tracking. Context variables carry `order_id` and `customer_tier` across all three agents.

### 7.3 Sales Routine Pipeline

A sales development agent runs a 5-step routine: lookup lead → enrich with firmographic data → draft personalised opener → check opt-out list → send or flag for human review. Each step calls a tool; the routine never hands off (one agent, multiple tools). The Agents SDK traces each step in the OpenAI dashboard so the sales team can audit what was sent.

### 7.4 Code Review Multi-Agent Chain

PR arrives. Triage Agent determines language and scope. Style Agent checks formatting and linting output. Security Agent scans for OWASP top-10 patterns. Performance Agent flags O(n^2) loops. Each specialist agent appends findings to context variables. Aggregator Agent compiles the final review comment from all findings.

---

## 8. Tradeoffs

### 8.1 Swarm vs Agents SDK

| Dimension | Swarm (2024) | Agents SDK (2025) |
|-----------|--------------|-------------------|
| Status | Experimental / educational reference source | Production, actively maintained (0.18.x as of July 2026) |
| Async | No (synchronous) | Yes (asyncio native) |
| Streaming | No | Yes (run_streamed) |
| Persistence | Caller manages messages | RunResult manages internally |
| Tracing | No | Built-in; traces viewable in the OpenAI Traces dashboard (no trace URL on `RunResult`) |
| Guardrails | No | `input_guardrails`, `output_guardrails` (both lists) |
| Retry logic | No | Yes (configurable) |
| Handoff declaration | Return an `Agent` from a function in `functions` | Explicit `handoffs=[...]` field; runner auto-generates `transfer_to_*` tools |
| Tool declaration | Plain callables in `functions` | `@function_tool`-decorated callables in `tools` |
| Typed context | dict (untyped) | Generic[ContextType] dataclass |
| Install | pip install git+https://github.com/openai/swarm.git | pip install openai-agents |
| Max turns safeguard | `max_turns` exists but defaults to `float("inf")` — no protection unless you set it | `max_turns` param, `DEFAULT_MAX_TURNS = 10` |

### 8.2 Handoffs vs Tool Calls

| Dimension | Handoff | Tool Call |
|-----------|---------|-----------|
| Returns | Agent object | Data (str / dict) |
| Effect | Changes active agent | Appends tool result to messages |
| System prompt | Switches to new agent's instructions | Stays on current agent |
| Use case | Route to specialist | Fetch data, call API |
| Context sharing | Shared by reference | Shared by reference |

### 8.3 Routines vs Handoffs

| Dimension | Routine | Handoff |
|-----------|---------|---------|
| Complexity | Instructions as numbered steps | Separate Agent objects |
| Specialisation | One agent, many steps | Multiple agents, one step each |
| Prompt isolation | No — all steps in one prompt | Yes — each agent has own prompt |
| Debugging | Harder (one trace) | Easier (per-agent traces) |
| Best for | Linear workflows, scripted flows | Domain specialisation, routing |

---

## 9. When to Use / When NOT to Use

### Read Swarm's source when:
- You want to see the handoff mechanism with nothing between you and it — the whole runner is under 100 lines
- You are teaching multi-agent concepts and need a diagram that fits on one screen

Build on the Agents SDK regardless of which of those applies; Swarm is a reading exercise, not a dependency.

### Use Agents SDK when:
- Building a production customer-facing system
- Need streaming for perceived responsiveness (first token under 500ms)
- Need audit trails (compliance, GDPR, SOC2 require traces)
- Need guardrails to enforce safety or business policy
- Running more than one agent concurrently

### Do NOT use either when:
- A single well-crafted prompt with tools suffices (adds unnecessary complexity)
- Latency budget is under 300ms end-to-end (each handoff adds ~200-400ms per LLM call)
- You need deterministic control flow — use a state machine or workflow engine instead
- Your team has no LLM ops experience; the failure modes (infinite loops, guardrail bypasses) are non-trivial

---

## 10. Common Pitfalls

### Pitfall 1: Expecting Swarm to mutate the caller's context_variables dict

`Swarm.run` starts with `context_variables = copy.deepcopy(context_variables)`. Every mutation a
tool makes lands on that private deep copy. The dict you passed in is **never** modified — not at
the top level, not nested. The only way to see updates is to read `response.context_variables`
and pass it back on the next turn.

**Broken:**
```python
# Swarm -- tool mutates context; caller reads its OWN dict afterwards and sees nothing
def update_plan(context_variables: dict) -> str:
    context_variables["plan"] = "pro"
    return "Updated"

context = {"customer_id": "C1", "plan": "free"}
response = client.run(agent, messages, context_variables=context)
print(context["plan"])  # "free" -- bug: run() deep-copied `context` on entry
```

**Fixed:**
```python
def update_plan(context_variables: dict) -> str:
    context_variables["plan"] = "pro"
    return "Updated"

context = {"customer_id": "C1", "plan": "free"}
response = client.run(agent, messages, context_variables=context)
# Rebind from the Response; this is the ONLY dict that carries the run's updates.
context = response.context_variables
print(context["plan"])  # "pro" -- correct

# Also rebind the agent: Response.agent is whichever agent was active at the end,
# and multi-turn callers must resume from it, not from the original triage agent.
next_agent = response.agent
```

### Pitfall 2: Infinite handoff loop

**Broken (Agents SDK):**
```python
billing_agent = Agent(
    name="Billing",
    instructions="If unsure, transfer to Triage.",
    handoffs=[triage_agent],   # cycle: transfers back to triage
)
triage_agent = Agent(
    name="Triage",
    instructions="If billing issue, transfer to Billing.",
    handoffs=[billing_agent],
)
# Triage -> Billing -> Triage -> Billing ... until max_turns or token limit
```

**Fixed:**
```python
billing_agent = Agent(
    name="Billing",
    instructions=(
        "Handle billing questions directly. "
        "If you truly cannot resolve the issue, say: "
        "'I need to escalate this to a human agent.' "
        "Do NOT transfer back to Triage."
    ),
    handoffs=[],   # no edge back to Triage -- the cycle is structurally absent
)
# And in Runner:
result = await Runner.run(triage_agent, input, max_turns=8)
# max_turns prevents runaway loops; raises MaxTurnsExceeded after 8 turns
```

### Pitfall 3: Guardrail that re-calls LLM inside itself (causes recursion)

**Broken:**
```python
@input_guardrail
async def classify_guardrail(ctx, agent, input):
    # Calls another agent to classify -- triggers another Runner.run inside a guardrail
    result = await Runner.run(classifier_agent, input)  # WRONG
    if "harmful" in result.final_output:
        return GuardrailFunctionOutput(tripwire_triggered=True, ...)
```

**Fixed:**
```python
import re

@input_guardrail
async def classify_guardrail(ctx, agent, input):
    # Use a fast heuristic or a direct openai.chat call, not Runner.run
    bad_patterns = [r"\b(hack|exploit|bypass)\b"]
    for pattern in bad_patterns:
        if re.search(pattern, input, re.IGNORECASE):
            return GuardrailFunctionOutput(
                output_info="policy_violation",
                tripwire_triggered=True,
            )
    return GuardrailFunctionOutput(output_info="ok", tripwire_triggered=False)
```

### Pitfall 4: Not awaiting async tools in Agents SDK

**Broken:**
```python
def lookup_account(ctx: RunContextWrapper[SupportContext]) -> str:
    import httpx
    response = httpx.get(f"https://api.example.com/accounts/{ctx.context.customer_id}")
    # Synchronous HTTP call blocks the event loop -- kills throughput
    return response.json()["plan"]
```

**Fixed:**
```python
async def lookup_account(ctx: RunContextWrapper[SupportContext]) -> str:
    import httpx
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"https://api.example.com/accounts/{ctx.context.customer_id}",
            timeout=5.0,
        )
        response.raise_for_status()
        return response.json()["plan"]
```

---

## 11. Technologies & Tools

| Tool / Library | Role | Notes |
|----------------|------|-------|
| openai-agents | Agents SDK core | `pip install openai-agents`; MIT license; 0.18.x as of July 2026 |
| swarm | Reading-only reference implementation of handoffs | `git clone https://github.com/openai/swarm` — never on PyPI (the PyPI `swarm` package is unrelated), so it cannot enter a lockfile by accident |
| openai Python SDK | Underlying LLM calls | The Agents SDK uses the async client; Swarm wraps the sync `openai.OpenAI` |
| asyncio | Async runtime | Required by Agents SDK |
| httpx | Async HTTP in tools | Preferred over requests in async context |
| pydantic | Structured tool outputs | Agents SDK supports pydantic model as output_type |
| OpenAI Traces dashboard | Trace viewer | Tracing is on by default; view runs at platform.openai.com/traces (RunResult carries no trace URL) |
| Langfuse / Arize | Third-party tracing | Hook into Agents SDK via custom span exporters |
| pytest-asyncio | Testing async agents | `@pytest.mark.asyncio` for Runner.run tests |
| tenacity | Retry logic for tools | Wrap tool functions with @retry for flaky APIs |

---

## 12. Interview Questions with Answers

**Q: What is the difference between OpenAI Swarm and the Agents SDK?**
**Short:** Swarm is an experimental synchronous prototype OpenAI replaced with the async, typed, production Agents SDK.
Swarm is a synchronous, stateless, explicitly experimental prototype released in October 2024 to demonstrate the handoff pattern; its own README says it is replaced by the Agents SDK. The Agents SDK (announced 11 March 2025) is the production successor: async, streaming, typed context, guardrails, built-in tracing, retry logic, and a max_turns safety limit. The APIs are not compatible — Swarm's `Agent` has a single `functions` list and no `handoffs` field, while the SDK separates `tools` (which must be `@function_tool`-decorated) from `handoffs`.

**Q: How does a handoff actually work under the hood?**
**Short:** A handoff-registered function call switches the active agent and restarts the LLM call under the new agent's system prompt.
The LLM generates a function call whose name matches a transfer function (e.g., "transfer_to_billing_agent"). The runner detects that this function is registered as a handoff rather than a data-returning tool. It switches the active agent to billing_agent, appends a tool message saying "Transferred", and starts a new LLM call using billing_agent's instructions as the system prompt — while keeping the full conversation history.

**Q: What are context variables and how do they persist across handoffs?**
**Short:** Context variables are a shared dict or dataclass across a run, but the Agents SDK mutates it live while Swarm deep-copies it.
Context variables are a dict (Swarm) or a typed dataclass (Agents SDK) shared across every agent invocation and tool call within a single run, so a mutation by one agent is immediately visible to the next. The boundary differs by framework: the Agents SDK passes your context object itself, so mutations are visible to the caller after the run; Swarm deep-copies the dict on entry to `run()`, so the caller's original is untouched and you must read `response.context_variables` and pass it back on the next call.

**Q: What is a routine in the Swarm / Agents SDK pattern?**
**Short:** A routine is a numbered scripted flow embedded in one agent's instructions, guiding the LLM through fixed steps without handoffs.
A routine is a numbered list of steps embedded in an agent's instructions that guides the LLM through a predefined conversation flow (e.g., greet → qualify → pitch → close). The LLM follows the steps in order, calling tools at each step, and only hands off when the routine explicitly says to. Routines are useful for linear, scripted workflows where specialisation is not needed.

**Q: When should you use a routine instead of separate agents with handoffs?**
**Short:** Use a routine for a linear shared-context workflow and separate handoff agents when steps need distinct knowledge or tools.
Use a routine when the workflow is linear, steps share the same domain context, and prompt isolation is not critical. Use separate agents with handoffs when steps require different specialised knowledge (reducing prompt length and hallucination risk), different tool sets, or when you need per-agent traceability.

**Q: What is max_turns and why does it matter?**
**Short:** max_turns caps total LLM calls per run so a pair of agents handing off to each other can't loop forever.
max_turns is a parameter to Runner.run (default 10) that limits the total number of LLM calls per run, counting tool calls and handoffs. Without it, a pair of agents that hand off to each other could loop indefinitely, consuming unbounded tokens and cost. After max_turns is exceeded, the SDK raises MaxTurnsExceeded.

**Q: How do input and output guardrails differ?**
**Short:** Input guardrails inspect the message before the LLM sees it; output guardrails inspect the response after it's produced.
Input guardrails run before the LLM receives the user message; they can inspect and block PII, prompt injection, or policy violations. Output guardrails run after the LLM produces its response; they can block toxic, off-topic, or malformed outputs. Both return GuardrailFunctionOutput; setting tripwire_triggered=True aborts the run by raising InputGuardrailTripwireTriggered or OutputGuardrailTripwireTriggered — two distinct exception classes, so catch both.

**Q: Can a guardrail call another LLM?**
**Short:** Avoid calling Runner.run inside a guardrail since it nests runs recursively; use a direct cheap-model call instead.
You should avoid calling Runner.run inside a guardrail because it causes nested runs and can trigger guardrails recursively. Instead, use a direct openai.chat.completions.create call with a fast, cheap model (e.g., gpt-5.4-nano), a regex heuristic, or a local classifier. The latency budget for a guardrail is typically under 200ms.

**Q: How does streaming work in the Agents SDK?**
**Short:** run_streamed returns immediately and is iterated with async for, emitting raw token, agent-change, and run-item events.
Runner.run_streamed is a synchronous call that immediately returns a RunResultStreaming object, which you then iterate with `async for event in result.stream_events()`. It is neither awaited nor used as an async context manager. There are three event types: raw_response_event (raw Responses API deltas, for token-level output), agent_updated_stream_event (the active agent changed, i.e. a handoff), and run_item_stream_event (a higher-level item finished — message_output_item, tool_call_item, tool_call_output_item, handoff_output_item). The caller can display tokens in real time while the runner continues processing.

**Q: What happens to conversation history when a handoff occurs?**
**Short:** A handoff preserves the full message history and only swaps the system prompt to the new agent's instructions.
The full message history is preserved and passed to the new agent. The new agent sees all previous turns, including the triage agent's messages and tool results. The only change is the system prompt, which switches to the specialist agent's instructions. This allows the specialist to understand the context of the conversation without re-asking questions.

**Q: How do you test an agent that makes handoffs?**
**Short:** Run with a faked model and assert on last_agent.name and mutated context fields rather than calling the real API.
Use Runner.run in a pytest-asyncio test with a FakeModel or by patching openai.AsyncOpenAI to return canned responses. Assert on result.last_agent.name to confirm the correct handoff occurred, and on context attributes to confirm tools mutated context correctly. Test the guardrail separately by calling it directly with a RunContextWrapper mock.

**Q: What is the token cost implication of multi-agent handoffs?**
**Short:** Each handoff resends the full conversation history, so a multi-agent chain's token cost grows roughly linearly with agent count.
Each handoff triggers a new LLM call. The new call includes the full conversation history, so token cost grows linearly with conversation length. A 3-agent chain on a 10-turn conversation may pay 3x the token cost of a single-agent approach. Mitigation: summarise earlier turns before handoff, or use a cheaper model (gpt-5.4-nano at $0.20/1M input tokens) for triage and reserve gpt-5.6-terra ($2.50/1M) for specialists.

**Q: How would you prevent a triage agent from looping back to itself?**
**Short:** Remove any transfer-to-triage tool from specialists entirely and give them an escape hatch instead of a way back.
Remove the transfer_to_triage tool from specialist agents entirely. Specialists should have an escape hatch that says "tell the user you cannot help and they should contact human support" — not a transfer back to triage. Set max_turns=8 as a circuit breaker. Log a warning if last_agent == starting_agent after more than 3 turns.

**Q: What is output_type in the Agents SDK and how does it differ from a guardrail?**
**Short:** output_type enforces a JSON schema on the final response with automatic retry; a guardrail only inspects for policy violations.
output_type is a pydantic model that forces the LLM's final response to conform to a JSON schema (structured output). The runner validates the output against the schema and retries if validation fails. A guardrail, in contrast, can inspect free-text output for policy violations but does not enforce schema. Use output_type for structured data extraction; use guardrails for safety and policy enforcement.

**Q: How does the Agents SDK integrate with OpenAI's tracing dashboard?**
**Short:** Every Runner.run auto-creates a trace on OpenAI's dashboard by default, correlated by wrapping the call in a named trace context.
Every Runner.run call automatically creates a trace on the OpenAI platform, viewable in the Traces dashboard. There is no trace_url attribute on RunResult — to correlate a run with its trace, wrap the call in `with trace("workflow-name", group_id=...)` and look it up by that name or group ID. The trace shows each LLM call, tool call, handoff, and guardrail evaluation with latency, token counts, and inputs/outputs. No additional instrumentation code is required; it is on by default with a valid API key.

**Q: What is the recommended model size split for triage vs specialist agents in production?**
**Short:** Put the cheapest adequate model on triage classification and reserve a stronger reasoning model for specialist work.
Put your cheapest adequate model on triage, since triage only classifies intent and routes. gpt-5.4-nano at $0.20/1M input is a common choice; reserve a stronger model such as gpt-5.6-terra ($2.50/1M) for specialists that need reasoning, tool use, or domain knowledge. Never put a reasoning model on triage: hidden thinking tokens are billed as output and add seconds of latency to a call whose entire job is picking one of four labels. The size of the saving is just the price ratio times the share of turns handled by the cheap tier, so compute it from your own turn mix rather than quoting a generic percentage.

**Q: When do you use `Agent.as_tool()` instead of a handoff in the Agents SDK?**
**Short:** Use as_tool for bounded work that returns control to the manager, and a handoff when the specialist should own the conversation.
Use `as_tool()` when a specialist should do a bounded piece of work and give control back; use a handoff when the specialist should own the user-facing conversation from that point on. `as_tool()` wraps an agent as an ordinary callable tool, so the manager stays the active agent — `RunResult.last_agent` is still the manager, and `final_output` is the manager's synthesis rather than the specialist's raw text. That is what makes it the only fan-out primitive on this SDK: a run has exactly one active agent, so you cannot hand off to three specialists at once, but you can expose three of them as tools and let the model call them in the same turn. Two behaviours differ from a handoff and cause most of the surprises: the nested agent does not inherit the conversation (history sharing is opt-in via `session` / `conversation_id` / `previous_response_id`), and its result comes back as a tool-result string, so use `custom_output_extractor` if the manager needs structured fields. Concrete test: "should the customer's next message go to this agent?" — yes means handoff, no means tool.

**Q: What is the cost consequence of the manager pattern versus a chain of handoffs?**
**Short:** The manager pattern pays for its own context plus a full nested run per specialist call, unlike a handoff chain's single active context.
The manager pattern is strictly more expensive per unit of work, because the manager's context stays alive across every nested run instead of being replaced. In a handoff chain there is one active context at a time — the specialist inherits the history and the triage agent stops being billed. With `as_tool()` you pay the manager's prompt on every turn *plus* a full nested run per specialist call *plus* the manager re-reading each returned result, which is the [orchestrator-worker](orchestrator_worker_pattern.md) cost profile expressed in SDK primitives. You buy three things with that: parallel fan-out, a single enforcement point for output guardrails and house voice, and a `last_agent` that never drifts. Route on it accordingly — triage-to-specialist support flows should stay handoffs, and only genuinely fan-out work (translate to four languages, score one document on five rubrics) should pay for a manager.

---

## 13. Best Practices

1. **Keep agent instructions under 500 words.** Longer instructions increase hallucination risk and make the agent harder to debug. Move domain knowledge into tool return values.

2. **Use typed context (dataclass) in the Agents SDK, not raw dicts.** Type safety prevents KeyError bugs when agents read each other's context mutations. Use @dataclass with default values for optional fields.

3. **Set max_turns explicitly.** Never rely on the default of 10 for production. Analyse your longest expected workflow (e.g., 3 handoffs × 2 turns each = 6) and set max_turns = workflow_max + 2.

**What the formula is telling you.** "Size the turn budget from the shape of your worst legitimate
workflow, then add a small fixed slack — not from a number that felt safe."

```
  workflow_max = num_handoffs x turns_per_handoff
  max_turns    = workflow_max + 2
```

| Symbol | What it is |
|--------|------------|
| `num_handoffs` | How many agent-to-agent transfers the longest real path makes |
| `turns_per_handoff` | Turns each agent burns before transferring. Typically 2: act, then hand off |
| `+ 2` | Slack for one retry or one clarifying exchange. Not a safety factor, a repair allowance |
| `max_turns` | Hard stop. Exceeding it aborts the run |

**Walk one example.** The stated workflow, and the latency it implies:

```
  3 handoffs x 2 turns = 6 turns   ->  max_turns = 6 + 2 = 8

  each turn is one LLM call at ~200-400 ms:
    typical path  : 6 turns x 200 ms = 1.2 s
    worst allowed : 8 turns x 400 ms = 3.2 s   <- matches the case study's 3.2 s average
```

Both failure directions cost something real. Set it too low and legitimate long conversations
abort mid-flight — the user sees a truncated session, not an error you can explain. Set it too
high (the case study ran `max_turns=12`, well above the `+2` rule) and a loop between two agents
that keep transferring to each other burns 12 LLM calls before anything stops it. The case study
recorded 12 turns firing 0.3% of the time and traced every instance to a user repeating the same
question — a real signal that a cheaper detector, not a bigger budget, was the fix.

This is why best practice 4 ("design handoffs as one-way") is the load-bearing companion to this
one. In a directed acyclic handoff graph, `num_handoffs` is bounded by the graph's longest path
and `workflow_max` is a number you can actually compute. Allow cycles and there is no longest
path — `max_turns` stops being a budget derived from the design and becomes the only thing
standing between you and an unbounded bill.

4. **Design handoffs as one-way.** Specialists should never transfer back to triage or to each other unless you have explicitly audited the graph for cycles. A directed acyclic handoff graph is easiest to reason about.

5. **Write a guardrail for every external input surface.** Customer-facing agents must have an input_guardrail for PII (credit card numbers, SSNs) and prompt injection. The guardrail should run in under 100ms using regex or a fast classifier.

6. **Test handoffs with mock LLM responses.** Do not call the real OpenAI API in unit tests. Patch the underlying client to return a canned tool call for the handoff, then assert on result.last_agent.name.

7. **Log context at the end of each run.** The final context state is a rich audit trail. In regulated industries (finance, healthcare) store context.model_dump() alongside the trace URL for compliance.

8. **Use routines for scripted, linear flows; use handoffs for domain routing.** A sales script is a routine (one agent, sequential steps). Customer support routing is a handoff graph (multiple specialist agents). Mixing the two leads to bloated agents with too many responsibilities.

9. **Cap specialist agent tool lists at 8-10 tools.** Beyond 10 tools, the LLM struggles to choose correctly. Split into sub-agents if a specialist's tool list grows beyond this.

10. **Handle MaxTurnsExceeded gracefully.** Catch the exception, log the partial result, and return a user-facing message like "This request is taking longer than expected. A human agent will follow up." Never surface the raw exception to the end user.

---

## 14. Case Study

### Customer Support Platform: Triage + Billing + Technical Agents

*All figures in this case study are an illustrative composite, not a published deployment — the arithmetic below is self-consistent and shows how the levers interact, but none of the rates is a citable industry number.*

**Company:** A B2B SaaS company with 50,000 customers, receiving 3,000 support tickets per day. Previous single-agent approach used a 1,400-word system prompt that hallucinated billing details and forgot technical troubleshooting steps as the conversation grew.

**Problem:**
- Single agent had a 14% hallucination rate on billing questions (wrong invoice amounts)
- Average first-response latency: 8 seconds (large prompt + long context)
- No audit trail for GDPR compliance requests
- 22% of conversations required human escalation due to off-topic answers

**Architecture:**

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    User(["User"]) --> Triage["Triage Agent<br/>gpt-5.4-nano, ~180-word instructions<br/>tools: lookup_account, classify_intent<br/>input_guardrail: pii, injection"]
    Triage -- "billing issue?" --> Billing["Billing Agent<br/>gpt-5.6-terra, ~220-word instructions<br/>tools: get_invoices, issue_refund,<br/>get_payment_methods<br/>output_guardrail: amount_sanity<br/>(blocks refunds &gt; $10,000)"]
    Triage -- "technical issue?" --> Technical["Technical Agent<br/>gpt-5.6-terra, ~200-word instructions<br/>tools: reset_password, check_service_status,<br/>create_ticket<br/>output_guardrail: length_guardrail"]

    class User io
    class Triage,Billing,Technical base
```

**Implementation highlights:**

Context dataclass carried `customer_id`, `plan`, `account_age_days`, `issue_category`, `refund_amount`, and `ticket_id`. The billing agent's amount_sanity_guardrail blocked any refund above $10,000 (max plan value), requiring human approval via create_ticket.

The triage agent's classify_intent tool called a local regex + keyword model (latency: 12ms) rather than a second LLM call, keeping triage under 800ms total.

All runs used Runner.run with max_turns=12. Trace URLs were stored in the CRM alongside the ticket, satisfying the GDPR audit requirement.

**Results after 30 days:**
- Hallucination rate on billing: 14% → 1.8% (billing agent has focused 220-word prompt + real invoice data from tool)
- Average first-response latency: 8s → 3.2s (gpt-5.4-nano for triage, smaller prompt for specialists)
- Human escalation rate: 22% → 9%
- Average cost per conversation: $0.021 → $0.009 (nano for triage, gpt-5.6-terra only for specialists)
- GDPR audit requests satisfied in < 2 minutes (trace URL lookup in CRM)

**In plain terms.** "Every one of these percentages is a rate, and a rate only becomes a
decision once you multiply it by the 3,000 tickets a day that actually flow through it."

Percentages hide magnitude. The same 3,000/day multiplier turns each result line into a number
you can take to a budget or a staffing meeting, which is the only form in which any of these
improvements is arguable.

| Symbol | What it is |
|--------|------------|
| `V` | Ticket volume. 3,000 per day here |
| `rate_before` / `rate_after` | The measured fraction, e.g. 0.14 → 0.018 hallucination |
| `V x rate` | Tickets per day actually affected |
| `cost_per_conv` | Blended model spend for one conversation, $0.021 → $0.009 |
| Relative reduction | `(before - after) / before` — the number quoted in the results list |

**Walk one example.** All four headline results, converted to daily units:

```
  hallucinated billing answers : 3,000 x 0.140 =  420/day
                                 3,000 x 0.018 =   54/day    -> 366 fewer wrong answers/day

  human escalations            : 3,000 x 0.220 =  660/day
                                 3,000 x 0.090 =  270/day    -> 390 fewer handoffs to staff/day

  model spend                  : 3,000 x $0.021 = $63.00/day
                                 3,000 x $0.009 = $27.00/day
                                 saving          = $36.00/day = $13,140/year

  first-response latency       : 8.0 s -> 3.2 s   = 60% faster, on every one of the 3,000

  relative reductions: cost 57.1%   latency 60.0%   escalation 59.1%   hallucination 87.1%
```

Note which number is *not* impressive in isolation and is decisive at volume: `max_turns=12` fired
0.3% of the time, which sounds like noise until it is `3,000 x 0.003 = 9 conversations per day`
hitting a hard stop — nine users per day getting a truncated session. Driving it to 0.05% leaves
1.5/day. A rate below 1% is exactly the regime where per-ticket multiplication changes whether
something is worth engineering.

The $13,140/year is also the honest framing of the cost win. A 57% reduction sounds like the
headline, but the absolute figure is small relative to an engineer's time — which is why the
lessons list ranks the *hallucination* and *escalation* wins above it. 390 fewer human escalations
per day is the result that pays for the architecture; the token savings are a rounding error
beside it.

**Lessons learned:**
1. The amount_sanity_guardrail caught 3 production incidents in the first week where the LLM hallucinated a refund amount 10x larger than the actual charge.
2. The biggest latency win came from switching triage to gpt-5.4-nano, not from architectural changes.
3. Routine-style instructions (numbered steps) in the triage agent reduced off-topic transfers by 40% versus free-form instructions.
4. max_turns=12 was triggered 0.3% of the time; all cases were users re-asking the same question repeatedly. Adding a "repeated question" detector in the triage agent's instructions reduced this to 0.05%.
