# AWS Strands Agents — Deep Dive

---

## 1. Concept Overview

AWS Strands Agents (Apache 2.0, 2025) is AWS's open-source agent SDK with first-class Bedrock integration, designed for production deployment on AWS infrastructure. The framework's core abstraction is the `@tool` decorator — any Python function becomes an agent tool. Agents are configured with a model, a tool set, and an optional system prompt, then run via `agent("user input")`. The minimal API surface (few classes, few primitives) makes it easy to learn but expressive enough for production multi-agent systems.

Strands distinguishes itself with: (a) deep Bedrock integration (Claude, Nova, Titan, Llama via Bedrock with no extra setup), (b) `agent_as_tool` pattern for multi-agent composition, (c) built-in observability via OpenTelemetry to CloudWatch/X-Ray/Honeycomb, (d) zero framework cost (you pay only Bedrock API). For teams already on AWS, Strands is the path of least resistance.

---

## 2. Intuition

**One-line analogy**: Strands is to AWS what the OpenAI Agents SDK is to OpenAI — opinionated production agents, tightly integrated with the host cloud, low friction to deploy.

**Mental model**: Define tools as decorated functions. Create an `Agent(model=BedrockModel("claude-sonnet-4"), tools=[...])`. Call `agent("user input")` and get a response. Multi-agent: wrap one agent as a tool with `agent_as_tool(specialist_agent)` and give it to an orchestrator agent.

**Why it matters**: AWS-heavy organizations face friction integrating non-AWS agent frameworks (auth, networking, observability all need glue code). Strands eliminates that — IAM-based auth, VPC-friendly, CloudWatch metrics built-in. Time from "concept" to "running in our AWS account" is measured in hours, not weeks.

**Key insight**: Strands isn't trying to outcompete LangGraph on features — it's optimizing for the "we're already on AWS and need agents now" workflow. The framework cost is zero; you pay Bedrock prices, which are typically 5-10% above direct Anthropic/Meta pricing.

---

## 3. Core Principles

- **Tool-first**: any decorated Python function is a tool; no class hierarchy required.
- **Bedrock-native**: Claude, Nova, Titan, Llama, Mistral via unified BedrockModel.
- **Multi-model**: LiteLLMModel adapter for non-Bedrock providers.
- **Composable**: `agent_as_tool` for multi-agent without separate orchestration framework.
- **Observable**: OpenTelemetry traces to CloudWatch/X-Ray/Datadog/Honeycomb.
- **VPC-friendly**: works inside private VPCs; Bedrock VPC endpoints supported.
- **IAM-integrated**: agents use IAM role for Bedrock auth, no API keys to manage.

---

## 4. Types / Architectures / Strategies

### 4.1 Single Agent

`Agent(model, tools)` — one agent, multiple tools, runs to completion.

### 4.2 Multi-Agent via agent_as_tool

Wrap specialist agents as tools for an orchestrator agent. Sequential delegation.

### 4.3 Streaming

Yields events as the agent runs — tool calls, text deltas, completion.

### 4.4 Bedrock Built-in Tools

`bedrock_knowledge_base`, `bedrock_agent`, `code_interpreter` — managed AWS services exposed as tools.

---

## 5. Architecture Diagrams

```
Strands Agent Execution
========================

  agent = Agent(
      model=BedrockModel("us.anthropic.claude-sonnet-4-..."),
      tools=[search, lookup, write_file],
  )

  response = agent("user query")

       |
       v
  +----+------------------------+
  | Strands runtime:            |
  | - Send prompt + tools schema|
  | - Bedrock API call          |
  | - Parse tool_use blocks     |
  | - Execute tools in parallel |
  | - Loop                      |
  +----+------------------------+
       |
       v
  Response (final text + metadata)


Multi-Agent Composition
========================

  Specialist agents:
    research_agent = Agent(model, tools=[web_search, read_url])
    coding_agent   = Agent(model, tools=[bash, write_file])

  Orchestrator:
    orch = Agent(
        model,
        tools=[
            agent_as_tool(research_agent, name="research"),
            agent_as_tool(coding_agent, name="code"),
        ],
    )

  Orchestrator calls "research" or "code" tools;
  each invocation runs the specialist agent end-to-end
  and returns its result to the orchestrator.


Bedrock Integration
====================

  Application                 AWS Account
  +-----------+              +-----------------+
  | Strands   | ---IAM----->| Bedrock         |
  | Agent     |              | (Claude/Nova/   |
  | (Python)  | <--TLS------|  Llama/Mistral) |
  +-----------+              +-----------------+

  No API keys; IAM role authorization.
  VPC endpoints for private networking.
  CloudWatch for usage/cost dashboards.
```

---

## 6. How It Works — Detailed Mechanics

```python
from strands import Agent, tool, agent_as_tool
from strands.models import BedrockModel
import boto3

# Define tools
@tool
def search_documents(query: str, max_results: int = 5) -> list[dict]:
    """Search the company document store.
    
    Args:
        query: Natural language query
        max_results: Max documents to return
    """
    # Pseudocode: real search
    return [{"id": "doc_1", "title": "...", "snippet": "..."}]


@tool
def get_document(doc_id: str) -> str:
    """Fetch full text of a document by ID."""
    s3 = boto3.client("s3")
    obj = s3.get_object(Bucket="docs", Key=doc_id)
    return obj["Body"].read().decode("utf-8")[:50_000]


@tool
def write_report(content: str, filename: str) -> str:
    """Write a report to S3."""
    s3 = boto3.client("s3")
    s3.put_object(Bucket="reports", Key=filename, Body=content.encode("utf-8"))
    return f"s3://reports/{filename}"


# Create agent with Bedrock Claude
model = BedrockModel(
    model_id="us.anthropic.claude-sonnet-4-20250514-v1:0",
    region_name="us-east-1",
    streaming=True,
)

agent = Agent(
    model=model,
    tools=[search_documents, get_document, write_report],
    system_prompt=(
        "You are a research analyst. Search documents, read relevant ones, "
        "synthesize findings, and write a report to S3."
    ),
)

# Run
response = agent("Find documents about Q4 sales performance and write a summary report.")
print(response.message)


# Streaming
for event in agent.stream("Same query"):
    if event.type == "tool_call":
        print(f"[Tool: {event.tool_name}({event.tool_input})]")
    elif event.type == "text_delta":
        print(event.delta, end="", flush=True)


# Multi-agent
research = Agent(model=model, tools=[search_documents, get_document])
writer = Agent(model=model, tools=[write_report])

orchestrator = Agent(
    model=model,
    tools=[
        agent_as_tool(research, name="research", description="Research a topic and return findings"),
        agent_as_tool(writer, name="write", description="Write a report given findings"),
    ],
    system_prompt="Plan: first research, then write the report.",
)

result = orchestrator("Q4 sales analysis report")


# LiteLLM fallback to non-Bedrock
from strands.models.litellm import LiteLLMModel
fallback_model = LiteLLMModel(model_id="anthropic/claude-sonnet-4-6")  # Direct Anthropic
backup_agent = Agent(model=fallback_model, tools=[...])
```

---

## 7. Real-World Examples

**AWS internal data science workflows** use Strands for cross-service automation (S3 → Athena → SageMaker).

**Enterprise BI assistant** at an AWS-heavy enterprise: Strands agent answers questions over Redshift via tool calls, writes findings to S3.

**Production DevOps agent**: triages CloudWatch alarms; tools include `query_cloudwatch_logs`, `restart_ec2_instance` (with approval gate), `scale_asg`.

---

## 8. Tradeoffs

| Dimension | Strands | LangGraph | OpenAI Agents SDK | Native API |
|---|---|---|---|---|
| AWS integration | Native | Manual | Manual | Manual |
| Multi-provider | Yes (LiteLLM) | Yes | OpenAI-leaning | Single provider |
| Multi-agent | agent_as_tool | Subgraphs | handoff() | Custom |
| Observability | OTEL → AWS native | LangSmith (paid) | OpenAI native | Manual |
| Maturity | Newer (2025) | Mature | New (2025) | N/A |
| Learning curve | Low | Steep | Low | Low |
| Best for | AWS-centric stacks | Complex stateful | OpenAI-centric | Cost-critical |

---

## 9. When to Use / When NOT to Use

**Use Strands when:**
- AWS is the primary cloud
- Bedrock is the primary model gateway
- Want minimal framework surface area
- Need quick integration with AWS services (S3, Lambda, Bedrock KB)

**Skip when:**
- Multi-cloud or non-AWS deployment
- Need complex stateful workflows ([LangGraph](langgraph.md) fits better)
- Heavy reliance on non-Bedrock providers (use [LiteLLM](litellm_routing.md) directly)

---

## 10. Common Pitfalls

### Pitfall 1: Direct Anthropic API in Bedrock region

```python
# BROKEN: hardcoded direct Anthropic call; ignores Bedrock IAM
from anthropic import Anthropic
client = Anthropic(api_key=os.getenv("ANTHROPIC_API_KEY"))
# Now you have a secret to rotate; bypasses VPC; loses CloudWatch billing
```

```python
# FIXED: BedrockModel uses IAM
model = BedrockModel(model_id="us.anthropic.claude-sonnet-4-...")
# No API key; IAM role auth; CloudWatch metrics on requests
```

### Pitfall 2: Synchronous tool blocking event loop

```python
# BROKEN: sync tool blocks parallel execution
@tool
def fetch_url(url: str) -> str:
    return requests.get(url, timeout=10).text  # Blocking
```

```python
# FIXED: async tool
@tool
async def fetch_url(url: str) -> str:
    async with httpx.AsyncClient() as c:
        r = await c.get(url, timeout=10)
        return r.text
```

**War story**: An AWS team built a Strands agent for log analysis, deployed to Lambda. Cold starts took 4-6 seconds due to importing the full boto3. After lazy-importing boto3 inside the tool functions and using Lambda Snapshot, cold start dropped to 800ms.

---

## 11. Technologies & Tools

| Tool | Purpose |
|---|---|
| `strands-agents` package | Main SDK |
| `BedrockModel` | Bedrock client |
| `LiteLLMModel` | Non-Bedrock providers |
| `@tool` decorator | Tool definition |
| `agent_as_tool` | Multi-agent composition |
| AWS CloudWatch | Metrics dashboard |
| AWS X-Ray | Distributed tracing |
| Bedrock Knowledge Base | Managed RAG |
| Bedrock Guardrails | Native content filtering |

---

## 12. Interview Questions with Answers

**What is the core abstraction of Strands Agents?**
The `Agent(model, tools)` class is the core. `model` is a `BedrockModel` or `LiteLLMModel`; `tools` is a list of `@tool`-decorated Python functions. Calling `agent("input")` runs the loop until the model returns a final response.

**How does Strands differ from the Anthropic native API or OpenAI Agents SDK?**
Strands is AWS-centric — Bedrock as primary backend, IAM-based auth, OTEL traces flowing into CloudWatch/X-Ray. OpenAI Agents SDK is OpenAI-centric with similar abstractions. Native Anthropic API is provider-direct, lower-level. Pick based on your cloud and primary provider.

**How is multi-agent composition done in Strands?**
Wrap a specialist agent as a tool with `agent_as_tool(specialist, name="...", description="...")`. The orchestrator agent gets this as a callable tool. When the orchestrator calls it, the specialist agent runs end-to-end and returns its result. Simpler than separate orchestration frameworks but limited to sequential delegation.

**What models does BedrockModel support?**
All Bedrock-available models: Anthropic Claude (Opus, Sonnet, Haiku), AWS Nova (Lite, Pro, Premier, Micro), Meta Llama 3.x/4, Mistral, Cohere Command, AI21. Function calling capability varies by model — Claude and Nova have strong tool use; some Llama variants are weaker.

**Why does BedrockModel sometimes fail with a ValidationException even though the model is enabled in the console?**
Because many newer Bedrock models can only be invoked through a cross-region inference profile, whose ID carries a geography prefix (`us.anthropic.claude-sonnet-4-...`, `eu.`, `apac.`) rather than the bare `anthropic.claude-...` model ID. Invoking the bare ID returns "Invocation of model ID ... with on-demand throughput isn't supported" — confusing, since model access shows as granted. Note the compliance angle: a cross-region profile may route requests to any region within that geography, which matters for data-residency reviews. Always pass the full inference-profile ID (with prefix) to `BedrockModel`, matching the geography your compliance posture requires.

**How does Strands handle observability?**
Built-in OpenTelemetry instrumentation. Out-of-the-box exporters for CloudWatch, X-Ray, Honeycomb, Datadog, Jaeger. Each agent run produces a trace with spans for: LLM calls, tool executions, multi-agent delegations.

**What's the cost difference between Bedrock and direct Anthropic API?**
Bedrock typically prices 5-15% above direct provider pricing for the same model (Anthropic Claude on Bedrock vs claude.ai/api). You pay for AWS's integration value (IAM, VPC, CloudWatch). For high-volume workloads, the markup adds up — evaluate vs direct Anthropic if cost is paramount.

**Can Strands agents run in Lambda?**
Yes. Common deployment: Lambda + API Gateway for synchronous endpoints, Step Functions for orchestration of longer agent runs. Tips: lazy-import boto3, use Lambda SnapStart, increase memory to 2GB+ for faster CPU.

**How do you secure tool execution in Strands?**
Two main approaches: (1) tool-level: validate inputs, use parameterized queries, restrict file paths via os.path.commonpath. (2) Bedrock Guardrails: configure content filtering at the model layer. For sandboxed code execution, integrate E2B or AWS-managed code interpreter.

**What is the relationship between Strands and Bedrock Agents (the AWS managed service)?**
Bedrock Agents is AWS's fully-managed agent service — define agents in console/CLI, AWS hosts and runs them. Strands is the open-source SDK for self-hosted agents using Bedrock as the LLM backend. Use Bedrock Agents for no-code/low-code; use Strands for code-first agents.

**How do you implement input/output guardrails in Strands?**
Configure Bedrock Guardrails (AWS managed) for content filtering — denied topics, PII redaction, prompt attack detection. Pass `guardrail_id` to `BedrockModel`. For custom Python-level guardrails, wrap the agent call in your own validation function.

**Can Strands work with MCP tool servers?**
Yes via `MCPClient` from `strands.tools.mcp`. Connect to MCP server, get tools, pass as agent tools alongside native `@tool` functions. See [MCP](../mcp_model_context_protocol/README.md) for the protocol itself.

**How does the Strands agent loop decide when to stop?**
The loop terminates when the model returns a final text response with no tool-use blocks (stop reason `end_turn`) instead of requesting another tool call. Each iteration sends the conversation plus accumulated tool results back to Bedrock, so a poorly designed tool that returns unhelpful output can drive many extra iterations before the model gives up. Guard with an iteration cap and a per-run cost budget, and log tool-call counts per run — a runaway loop should show up in your metrics before it shows up on the Bedrock bill.

**What's the streaming event model?**
`agent.stream(query)` yields events: `tool_call`, `tool_result`, `text_delta`, `done`. Useful for UI integration. Bedrock streaming latency is similar to direct Anthropic streaming.

**How do you handle Bedrock throttling?**
BedrockModel auto-retries on `ThrottlingException` with exponential backoff. For high throughput, use Bedrock Provisioned Throughput (reserved capacity). Or use multiple Bedrock regions with a router.

**How do you keep long conversations from overflowing the context window in Strands?**
Use a conversation manager — Strands ships a sliding-window conversation manager that truncates the oldest turns once history exceeds a configured size, preserving the system prompt and recent turns. Without it, a long-running agent accumulates every tool result in history; a single large tool output (like the 50 KB document cap in `get_document` above) then gets re-sent as input tokens on every subsequent turn, inflating both latency and cost since Bedrock bills the full input each call. Truncate or summarize bulky tool results at the tool boundary, and cap history length before the model caps it for you.

**Is Strands actively developed?**
Yes — AWS commitment, regular releases. As of 2025, considered stable for production but expect API additions. Pin version in production.

---

## 13. Best Practices

1. Use BedrockModel with IAM role auth in production — no API keys.
2. Make tools async with `async def` to enable parallel tool execution.
3. Enable OpenTelemetry exporter on day 1 — debugging without traces is painful.
4. Use VPC endpoints for Bedrock in private networks.
5. Set CloudWatch alarms on per-agent cost (via Bedrock usage metrics).
6. For multi-agent, prefer `agent_as_tool` over rolling your own orchestration.
7. Test with smaller Bedrock models (Haiku, Nova Lite) during dev to save cost.
8. Use Bedrock Guardrails for content filtering rather than DIY input/output validation.
9. Lazy-import heavy modules (boto3, pandas) in tools for Lambda cold-start performance.
10. Pin Strands version in production; SDK still evolving.

---

## 14. Case Study

**Enterprise CloudWatch Alarm Triage**

**Problem**: A 5000-employee enterprise gets 200-500 CloudWatch alarms/day. On-call engineers spent 30-60 min per alarm context-gathering before resolution. Wanted an agent to do the initial triage.

**Architecture**:
- Strands Agent on Lambda (triggered by EventBridge on alarm)
- Tools: `query_cloudwatch_logs`, `get_metric_data`, `list_recent_deployments`, `find_related_alarms`, `post_to_slack` (with approval gate for "restart" actions)
- Model: BedrockModel("us.anthropic.claude-sonnet-4...")
- OTEL → X-Ray for tracing

**Flow**:
1. EventBridge sends alarm → Lambda invokes agent
2. Agent calls tools to gather context (recent logs, related deploys, similar past alarms)
3. Agent posts Slack thread with: summary, root cause hypothesis, suggested action, confidence
4. On-call responds to thread (approve action / take over)

**Results**:
- Average triage time: 30-60 min → 90 seconds (agent's runtime)
- Engineer time saved: 8-12 minutes per alarm (only need to read summary, not gather)
- Cost: $0.04 per alarm (Bedrock Claude Sonnet via Strands)
- ~$300/day total cost; saves ~150 engineer-hours/day across the team
- Confidence calibration: 92% of "high confidence" suggestions accepted by engineers

**Lessons**:
1. BedrockModel + IAM removed all secret management — no key rotation, no Vault integration needed.
2. agent_as_tool let us split "log triage" and "metric analysis" into specialist subagents, improving accuracy.
3. CloudWatch dashboards for agent latency/cost were built-in via OTEL exporter.
4. Approval gates on `restart_*` actions caught 4 false-positive restart suggestions in month 1.
