# Agents & Tool Use

## 1. Concept Overview

An LLM agent is a system where a language model acts as the reasoning engine that decides which actions to take to accomplish a goal. Unlike a simple prompt-response interaction, an agent can call tools, execute code, browse the web, read/write files, and take multiple actions in sequence — autonomously working toward a goal until it's accomplished.

The key insight: LLMs are exceptional reasoners but poor executors (they can't run code, access the internet, or call APIs). Tools bridge this gap. By giving an LLM access to tools and a loop to keep acting until a task is complete, we get a system that can accomplish tasks no single LLM call could handle.

Agents represent the frontier of LLM application development in 2024-2025, powering systems like Claude Code, Devin, Cursor Composer, and countless enterprise automation workflows.

---

## Intuition

> **One-line analogy**: An LLM agent is like a brilliant intern who can think through problems and delegate to specialists (tools) — you give them a goal and they figure out how to get it done.

**Mental model**: A regular LLM call is stateless — one question, one answer. An agent is a loop: the LLM reads the task, decides what action to take (call a tool, write code, search the web), observes the result, and decides the next action. This loop continues until the task is complete or an error occurs. The LLM is the brain (reasoning); tools are the hands (execution). ReAct (Reason + Act) is the pattern: think about what to do, do it, observe, repeat.

**Why it matters**: Tools give LLMs access to real-time information, code execution, APIs, and file systems — capabilities far beyond text generation. This transforms LLMs from Q&A systems into autonomous workers that can complete multi-step tasks.

**Key insight**: The fundamental insight of agentic systems is that LLMs are better at planning and reasoning than they are at reliable one-shot execution — breaking work into small, verifiable steps with tool calls makes complex tasks tractable.

---

## 2. Core Principles

- **Planning**: Breaking down complex goals into executable sub-steps.
- **Tool use**: Calling external functions (APIs, databases, code execution) to extend capabilities.
- **Memory**: Maintaining state across multiple steps (working memory in context; long-term in storage).
- **Reflection**: Evaluating progress and adjusting the plan when things go wrong.
- **Loops**: Agents work in a cycle: reason → act → observe → reason again.
- **Termination**: Agents must know when to stop (task complete, error, maximum steps reached).

---

## 3. Types / Strategies

### 3.1 Function Calling (Tool Use)

Modern LLMs are fine-tuned to recognize when to call tools and output structured calls:

```python
# OpenAI function calling
tools = [{
    "type": "function",
    "function": {
        "name": "get_weather",
        "description": "Get current weather for a location",
        "parameters": {
            "type": "object",
            "properties": {
                "location": {"type": "string", "description": "City, Country"},
                "units": {"type": "string", "enum": ["celsius", "fahrenheit"]}
            },
            "required": ["location"]
        }
    }
}]

response = client.chat.completions.create(
    model="gpt-4o",
    messages=[{"role": "user", "content": "What's the weather in Paris?"}],
    tools=tools
)
# Returns: tool_call { name: "get_weather", arguments: {"location": "Paris, France"} }
```

The model outputs a structured tool call; your code executes it; result injected back into context.

### 3.2 ReAct (Reasoning + Acting)

Interleave thoughts and actions in a structured format:

```
Task: Find the CEO of Apple and their net worth.

Thought: I need to find the current CEO of Apple first.
Action: search("Apple CEO 2024")
Observation: Tim Cook is the CEO of Apple Inc.

Thought: Now I need to find Tim Cook's net worth.
Action: search("Tim Cook net worth 2024")
Observation: Tim Cook's net worth is approximately $1.5 billion.

Thought: I have all the information needed to answer.
Final Answer: Tim Cook is the CEO of Apple. His net worth is approximately $1.5 billion.
```

ReAct was proposed as a prompting pattern (2022) and is now the default architecture for most agents.

### 3.3 Plan-and-Execute

Separate planning from execution for more reliable long-horizon tasks:

```
Phase 1: Planning (one LLM call)
  Task: "Write a market analysis report on the EV industry"
  Plan:
    1. Research current EV market size and growth
    2. Identify top 5 EV manufacturers by market share
    3. Analyze recent battery technology developments
    4. Investigate charging infrastructure trends
    5. Compile competitive analysis
    6. Write executive summary

Phase 2: Execution (one agent per step)
  Execute step 1: search, retrieve, summarize
  Execute step 2: search, retrieve, summarize
  ...
  Execute step 6: synthesize all gathered info → write report

Benefits: clear structure, each step can be independently validated
Drawbacks: plan may become outdated as execution reveals new info
```

### 3.4 Memory Systems

Agents need memory to handle tasks that span multiple steps:

```
Memory Types:

In-context (working memory):
  The current conversation / context window
  Limited: 8K-200K tokens depending on model
  Volatile: lost when context is cleared

External (episodic memory):
  Store past interactions in vector DB
  Retrieve relevant memories when needed
  Persistent: survives sessions
  Example: "I remember from last week you prefer TypeScript..."

Semantic memory:
  Facts about the world / domain
  Stored in knowledge base or documents
  Accessed via RAG

Procedural memory:
  Skill programs / few-shot examples stored
  Retrieved when similar task encountered
  Example: successful code templates
```

### 3.5 Tool Library

Common tools given to agents:

| Tool Category | Examples | Use Case |
|---------------|---------|---------|
| Search | Bing Search, Serper, Tavily | Real-time information |
| Code execution | Python sandbox, REPL | Data analysis, calculations |
| File I/O | Read/write files, list directory | File management |
| Browser | Playwright, Selenium | Web scraping, form filling |
| Database | SQL executor, API client | Data retrieval |
| Communication | Email, Slack, calendar | Enterprise automation |
| LLM sub-calls | Summarizer, translator | Specialized sub-tasks |
| Vector DB | Retrieval, storage | Long-term memory |

---

## 4. Architecture Diagrams

### Agent Loop
```
Goal / Task
     |
     v
[Plan] ← Plan step(s) to take
     |
     v
[Act] → Tool Call (e.g., search, code execution)
     |
     v
[Observe] → Tool Result injected into context
     |
     v
[Reflect] ← Is the task complete?
     |
     +-- YES → Return final answer
     |
     +-- NO  → [Plan] next step (loop)

Max iterations safety: stop after N steps to prevent infinite loops
```

### Function Calling Flow
```
User Message
     |
     v
[LLM Reasoning]
  "I need to call get_weather to answer this"
     |
     v
Tool Call: { "function": "get_weather", "args": {"location": "Paris"} }
     |
     v
[Your Code Executes the Function]
  weather_api.get("Paris") → {"temp": 18, "condition": "cloudy"}
     |
     v
[Inject Result into Messages]
  { "role": "tool", "content": '{"temp": 18, "condition": "cloudy"}' }
     |
     v
[LLM Generates Final Response]
  "The weather in Paris is currently 18°C and cloudy."
```

### Agent Memory Architecture
```
Agent
  |
  +-- Working Memory (context window)
  |   Current conversation, recent observations, scratch pad
  |
  +-- Episodic Memory (vector DB)
  |   Past conversations, past task outcomes
  |   Retrieve: "what did we learn last time we ran this analysis?"
  |
  +-- Semantic Memory (knowledge base)
  |   Domain facts, documentation, product info
  |   Retrieve: RAG over knowledge base
  |
  +-- Procedural Memory (few-shot library)
      Successful past tool call patterns
      Retrieved by similarity to current task
```

---

## 5. How It Works — Detailed Mechanics

### Tool Definition Best Practices

```python
# Good tool definition: clear name, description, typed parameters
{
    "name": "execute_python",
    "description": "Execute Python code in a sandbox and return stdout/stderr. "
                   "Use for data analysis, calculations, and generating charts.",
    "parameters": {
        "code": {
            "type": "string",
            "description": "Valid Python code to execute. Import libraries as needed."
        },
        "timeout_seconds": {
            "type": "integer",
            "description": "Maximum execution time. Default 30. Max 120.",
            "default": 30
        }
    }
}

# Poor tool definition: vague, ambiguous
{
    "name": "run",
    "description": "Run something",  # Too vague - model doesn't know when to use it
    "parameters": {"input": {"type": "string"}}
}
```

### Error Handling and Recovery

Agents must handle tool failures gracefully:

```python
# Agent loop with error handling
def agent_loop(task, max_steps=10):
    messages = [{"role": "user", "content": task}]

    for step in range(max_steps):
        response = llm.call(messages, tools=available_tools)

        if response.is_final_answer:
            return response.content

        if response.tool_call:
            try:
                result = execute_tool(response.tool_call)
                messages.append({"role": "tool", "content": result})
            except ToolError as e:
                # Inject error as observation so agent can recover
                messages.append({
                    "role": "tool",
                    "content": f"Error: {str(e)}. Please try a different approach."
                })

    return "Task exceeded maximum steps. Partial results: ..."
```

### Prompt Construction for Agents

```
System Prompt Structure for Agents:

1. Role: "You are an autonomous agent that..."
2. Available tools: [list with descriptions]
3. Output format: "Use this format for tool calls..."
4. Decision rules: "Search before answering factual questions"
5. Completion criteria: "Say DONE when the task is complete"
6. Safety constraints: "Never execute destructive operations without confirmation"
7. Iteration limit: "Complete the task in at most 10 steps"
```

---

## 6. Real-World Examples

### Claude Code (Anthropic)
- Terminal-based agent that reads/writes files, executes commands
- Tools: read_file, write_file, bash, list_directory
- Context: up to 200K tokens; can hold entire codebases
- Can refactor multi-file projects, run tests, debug errors autonomously
- Human-in-the-loop: asks permission for destructive operations

### OpenAI Assistants API
- Managed agent infrastructure: threads, tools, file storage
- Built-in tools: code_interpreter (Python sandbox), file_search (RAG)
- Custom function calling
- Persistent threads: conversation history managed server-side
- Used by thousands of production applications

### Devin (Cognition AI)
- Full autonomous software engineering agent
- Tools: terminal, browser, code editor, web search
- Completes real GitHub issues end-to-end
- Persistent workspace: remembers state across sessions
- SWE-bench: 13.8% resolution rate (first highly publicized agent benchmark)

---

## 7. Tradeoffs

| Factor | Simple Chain | ReAct Agent | Plan-Execute |
|--------|-------------|-------------|--------------|
| Reliability | High | Medium | High |
| Flexibility | Low | High | Medium |
| Latency | Fast | Slow (N rounds) | Medium |
| Debugging | Easy | Hard | Medium |
| Long tasks | Fails | Handles | Handles well |

---

## 8. When to Use / When NOT to Use

### Use Agents When:
- Task requires dynamic tool calls (you don't know in advance which tools are needed)
- Multi-step tasks where each step depends on previous results
- Tasks requiring real-time information (web search, API calls)
- Tasks requiring code execution or file manipulation

### Don't Use Agents When:
- Task is a single LLM call (no tools needed)
- Latency is critical (agent loops add 1-10+ seconds per step)
- Task can be solved with a static chain (same tools, same order every time)
- Safety requirements prohibit autonomous action (medical, legal decisions)

---

## 9. Common Pitfalls

1. **Infinite loops**: Agent keeps trying the same failing tool. Enforce max_iterations and detect repetitive patterns.
2. **Tool overuse**: Agent calls tools when it already has the answer in context. Prompt: "Use your knowledge when confident, tools only when needed."
3. **Context overflow**: Long agent runs accumulate many tool call messages. Implement context compression (summarize old messages).
4. **Verbose tool results**: Large API responses bloat context. Truncate or summarize tool results before injecting.
5. **No timeout on tool calls**: Network failures cause agent to hang. Always implement async timeouts.
6. **Trust but don't verify**: Agents blindly trust tool outputs. Validate critical tool results before acting on them.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **OpenAI Assistants API** | Managed agent infra | Threads, built-in tools, file storage |
| **Anthropic API** | Tool use + Claude | Best instruction following; claude-3.5 |
| **LangGraph** | Stateful agent graphs | Complex multi-agent flows |
| **LlamaIndex Agents** | RAG-focused agents | Data agents, query planning |
| **Tavily Search** | Agent-optimized search | LLM-friendly search results |
| **E2B** | Code execution sandbox | Secure; fast spin-up |
| **Modal** | Serverless agent execution | Scale agent workloads |
| **Tool-augmented LLM guide** | Best practices | Anthropic's tool use cookbook |
| **Mem0** | Agent memory | Long-term memory for agents |

---

## 11. Interview Questions with Answers

**Q: What is a LLM agent and how is it different from a standard LLM call?**
A: An LLM agent places the LLM in a loop where it can call tools, observe results, and decide on next actions — iterating until a task is complete. A standard LLM call is a single round-trip: input → output. Agents handle tasks that require multiple steps, dynamic tool selection, real-time information, or actions on external systems. The cost is added latency, complexity, and failure modes.

**Q: What is the ReAct pattern?**
A: ReAct (Reasoning + Acting) prompts the LLM to produce alternating Thought-Action-Observation tuples. Thought: the model's reasoning about what to do next. Action: a tool call (specified in structured format). Observation: the tool's result. This cycle repeats until the model produces a Final Answer. It works well because explicit reasoning traces make the model's planning visible and debuggable, and grounding action selection in explicit thoughts improves decision quality.

**Q: How do you prevent an agent from running indefinitely?**
A: (1) Hard iteration limit — stop after N steps (typically 10-20) and return a partial answer; (2) Timeout — kill the agent after T seconds total; (3) Repetition detection — if the same tool is called with the same arguments twice, exit the loop; (4) Cost tracking — stop if cumulative LLM cost exceeds a budget; (5) Human-in-the-loop — check in with a human when uncertain.

**Q: What is the difference between working memory and long-term memory for agents?**
A: Working memory is the agent's context window — everything in the current conversation. It's fast (no retrieval) but limited (128K tokens max) and volatile (cleared between sessions). Long-term memory stores information in external databases (vector store, key-value store) and is retrieved as needed. It's persistent, unlimited, but requires retrieval latency. Agents need both: working memory for the current task, long-term memory for user preferences, past outcomes, and domain knowledge.

---

## 12. Best Practices

1. **Design tools carefully** — clear names, specific descriptions, strict typing; poor tool specs lead to wrong tool selection.
2. **Log all agent steps** — every thought, action, and observation; essential for debugging production agents.
3. **Implement graceful degradation** — if agent fails after N steps, return best partial answer rather than nothing.
4. **Test with adversarial inputs** — what happens with empty responses, API failures, or contradictory tool results?
5. **Use human-in-the-loop for high-risk actions** — always confirm before deleting data, sending emails, or making purchases.
6. **Monitor cost per agent run** — agents can spiral; set cost limits and alert on outliers.

---

## 13. Case Study: Automated Research Agent for Competitive Intelligence

**Problem:** Sales team wants an agent that, given a prospect company name, produces a competitive intelligence brief: company overview, recent news, product comparison, and pricing analysis.

**Agent Design:**
```
Tools:
  1. web_search(query) → top 5 result snippets
  2. scrape_url(url) → webpage text (500 word limit)
  3. get_crunchbase_data(company) → funding, headcount, founded year
  4. analyze_pricing_page(url) → extract pricing tiers

Agent loop (ReAct):
  Thought: I need to find company overview first
  Action: web_search("Acme Corp company overview products")
  Observation: [search results]

  Thought: I need recent news
  Action: web_search("Acme Corp news 2024 site:techcrunch.com OR site:venturebeat.com")
  Observation: [results]

  Thought: Get funding info
  Action: get_crunchbase_data("Acme Corp")
  Observation: [funding rounds, investors, headcount]

  Thought: Get pricing
  Action: scrape_url("https://acmecorp.com/pricing")
  Observation: [pricing tiers]

  Thought: I have enough info to write the brief
  Final Answer: [structured competitive intelligence brief]
```

**Results:**
- Average run: 6 steps, 45 seconds, $0.12 in API costs
- Output quality: rated 8.1/10 by sales reps
- Time saved: 2 hours/brief → 45 seconds automated
- Deployed via Slack slash command: `/research Acme Corp`
