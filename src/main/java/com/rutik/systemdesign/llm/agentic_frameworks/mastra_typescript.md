# Mastra (TypeScript) — Deep Dive

---

## 1. Concept Overview

Mastra is a TypeScript-first agent framework (2024, MIT licensed) designed for full-stack JavaScript teams building agentic features. It targets the LangChain-equivalent slot for the JS ecosystem: agents, workflows, tools, RAG, evals, and deployment — all with TypeScript types throughout. The framework was built by the team behind Gatsby; it emphasizes developer experience (CLI scaffolding, hot-reload dev server, typed integrations).

Mastra's primary abstractions are `Agent` (LLM + tools + memory + instructions), `Workflow` (typed DAG of steps with branching, looping, parallelism), `Tool` (Zod-validated input/output), `Memory` (LibSQL or Postgres vector store), and `MastraMCPClient` (connect to MCP servers). It deploys naturally to Vercel Edge, Cloudflare Workers, AWS Lambda, or any Node server. For Next.js / React stacks, it eliminates the impedance mismatch of bridging Python LLM code to a JS frontend.

---

## 2. Intuition

**One-line analogy**: Mastra is to TypeScript agents what Next.js is to TypeScript web apps — opinionated, full-stack-ready, optimized for the JS/TS development workflow.

**Mental model**: Two complementary primitives. Agents are LLM-driven (model decides next step). Workflows are deterministic (you define the DAG; LLMs are steps within it). Most production systems combine both — workflows for the overall business process; agents for the parts that need autonomy.

**Why it matters**: Vast majority of web product engineering happens in TypeScript. Building agents in Python and bridging to a TS frontend means duplicate types, network IPC, and deployment complexity. Mastra collapses this — agent code lives next to your Next.js routes, deploys to the same edge function, shares types via shared Zod schemas.

**Key insight**: Mastra's workflow primitive is genuinely different from agent — it's a typed state machine where steps can be deterministic (call API) or LLM-powered. This makes business processes (PR review, onboarding, content moderation) far cleaner than pure-agent approaches.

---

## 3. Core Principles

- **TypeScript-first**: types throughout — agent inputs, tool params, workflow state.
- **Zod-validated**: tool schemas use Zod (runtime + compile-time validation).
- **Edge-deployable**: works on Vercel Edge, Cloudflare Workers (limited Node APIs).
- **Workflows vs Agents**: deterministic DAGs for processes; agentic loops for autonomy.
- **Voice support**: real-time audio in/out via VoiceModel abstraction.
- **Built-in evals**: define metrics in TypeScript; run as test suite.
- **MCP integration**: native MCPClient for connecting to MCP servers.

---

## 4. Types / Architectures / Strategies

### 4.1 Agent with Tools and Memory

`Agent({name, instructions, model, tools, memory})` — standard agent pattern.

### 4.2 Workflow (Step DAG)

`createWorkflow()` then `.step()`, `.then()`, `.parallel()`, `.until()`, `.branch()`. Typed state flows through.

### 4.3 RAG via Built-in Vector Store

LibSQL or Postgres pgvector; `Memory` class manages embedding + retrieval.

### 4.4 Voice Agent

`VoiceAgent` with input/output VoiceModel; uses OpenAI Realtime API or providers.

### 4.5 MCP Tools

`MastraMCPClient({command, args})` connects to MCP server; tools auto-imported. See [MCP — Model Context Protocol](../mcp_model_context_protocol/README.md) for the protocol itself.

---

## 5. Architecture Diagrams

```
Agent vs Workflow Comparison
=============================

  Agent (LLM-driven control):
    User input -> LLM decides tool calls -> repeat until done
    Best for: research, Q&A, open-ended tasks

  Workflow (deterministic control):
    Step 1: fetch_data
       |
       v
    Step 2: parallel(analyze_security, analyze_quality)
       |
       v
    Step 3: branch(if security_issue → escalate, else → continue)
       |
       v
    Step 4: generate_report

    LLM may be ONE of the steps (e.g., generate_report uses LLM)
    Best for: business processes, multi-stage pipelines


Mastra Deployment Surfaces
===========================

  Mastra App
       |
       +---deploy---> Vercel Edge (limited Node API)
       +---deploy---> Cloudflare Workers (sub-50ms cold start)
       +---deploy---> Node server (full Node API)
       +---deploy---> AWS Lambda
       +---deploy---> Docker container
```

---

## 6. How It Works — Detailed Mechanics

```typescript
import { Mastra } from "@mastra/core";
import { Agent } from "@mastra/core/agent";
import { createWorkflow, createStep } from "@mastra/core/workflows";
import { Memory } from "@mastra/memory";
import { LibSQLVector } from "@mastra/libsql";
import { anthropic } from "@ai-sdk/anthropic";
import { z } from "zod";

// 1. Define typed tools with Zod
const searchTool = {
  id: "web_search",
  description: "Search the web for current information",
  inputSchema: z.object({
    query: z.string(),
    maxResults: z.number().int().min(1).max(10).default(5),
  }),
  outputSchema: z.array(z.object({
    title: z.string(),
    url: z.string().url(),
    snippet: z.string(),
  })),
  execute: async ({ context }) => {
    // Real search API call
    const results = await searchAPI(context.query, context.maxResults);
    return results;
  },
};

// 2. Memory for the agent
const memory = new Memory({
  vector: new LibSQLVector({ connectionUrl: process.env.LIBSQL_URL! }),
  options: { lastMessages: 10, semanticRecall: { topK: 3 } },
});

// 3. Define agent
const researchAgent = new Agent({
  name: "research-agent",
  instructions: "You are a research assistant. Use web_search to find current info.",
  model: anthropic("claude-sonnet-4-6"),
  tools: { searchTool },
  memory,
});

// 4. Define workflow (deterministic DAG)
const extractStep = createStep({
  id: "extract",
  inputSchema: z.object({ prContent: z.string() }),
  outputSchema: z.object({ diff: z.string(), filesChanged: z.array(z.string()) }),
  execute: async ({ inputData }) => {
    // Parse PR content
    return { diff: "...", filesChanged: ["src/foo.ts"] };
  },
});

const securityCheckStep = createStep({
  id: "security",
  inputSchema: z.object({ diff: z.string() }),
  outputSchema: z.object({ issues: z.array(z.string()) }),
  execute: async ({ inputData, mastra }) => {
    const agent = mastra.getAgent("security-agent");
    const result = await agent.generate(`Review for security issues:\n${inputData.diff}`);
    return { issues: parseIssues(result.text) };
  },
});

const styleCheckStep = createStep({
  id: "style",
  inputSchema: z.object({ diff: z.string() }),
  outputSchema: z.object({ issues: z.array(z.string()) }),
  execute: async ({ inputData, mastra }) => {
    // Similar
    return { issues: [] };
  },
});

const reviewWorkflow = createWorkflow({
  id: "pr-review",
  inputSchema: z.object({ prContent: z.string() }),
  outputSchema: z.object({ comment: z.string() }),
})
  .then(extractStep)
  .parallel([securityCheckStep, styleCheckStep])
  .then(createStep({
    id: "compose",
    inputSchema: z.object({
      security: z.object({ issues: z.array(z.string()) }),
      style: z.object({ issues: z.array(z.string()) }),
    }),
    outputSchema: z.object({ comment: z.string() }),
    execute: async ({ inputData }) => {
      const allIssues = [...inputData.security.issues, ...inputData.style.issues];
      return { comment: allIssues.length ? `Issues:\n${allIssues.join("\n")}` : "LGTM!" };
    },
  }))
  .commit();

// 5. Wire into Mastra app
const mastra = new Mastra({
  agents: { researchAgent },
  workflows: { reviewWorkflow },
});

// 6. Use it
const { runId, start } = mastra.getWorkflow("pr-review").createRun();
const result = await start({ inputData: { prContent: "..." } });
console.log(result.result?.comment);

// 7. Stream agent output
const stream = await researchAgent.stream("What is Mastra?");
for await (const chunk of stream.textStream) {
  process.stdout.write(chunk);
}
```

---

## 7. Real-World Examples

**SaaS internal AI features** at Next.js-based companies use Mastra alongside their existing app code — deploys to same Vercel.

**Customer support widgets** built with Mastra agents embedded directly in React components.

**Document processing pipelines** as Mastra workflows — extract → classify → enrich → store; each step typed end-to-end.

**Voice assistants** using Mastra VoiceAgent on edge functions for low latency.

---

## 8. Tradeoffs

| Dimension | Mastra | LangChain JS | Vercel AI SDK | Inngest+OpenAI |
|---|---|---|---|---|
| TypeScript-first | Yes | Yes (less idiomatic) | Yes | Yes |
| Workflows (typed DAG) | Yes | No (LangGraph JS) | No | Yes |
| Built-in memory/RAG | Yes | Yes | Manual | Manual |
| Voice support | Yes | Partial | Partial | Manual |
| Edge deployment | Native | Partial | Excellent | Yes |
| Eval harness | Built-in | Manual | Manual | Manual |
| Maturity | New (2024) | More mature | Mature | Mature |
| Best for | Full-stack TS agents | Multi-language teams | Simple chat UIs | Durable backend agents |

---

## 9. When to Use / When NOT to Use

**Use Mastra when:**
- TypeScript/Next.js stack
- Need both agents and deterministic workflows
- Edge deployment desired
- Want built-in RAG memory and evals

**Skip when:**
- Python team
- Need maximum ecosystem integrations (LangChain has more)
- Simple chat UI only (Vercel AI SDK is lighter)

---

## 10. Common Pitfalls

### Pitfall 1: Edge runtime API mismatch

```typescript
// BROKEN: uses Node-only API in tool, breaks on Vercel Edge
import { promises as fs } from "fs";  // Not in Edge runtime

const readFileTool = {
  execute: async ({ context }) => {
    return await fs.readFile(context.path, "utf-8");  // Throws in Edge
  },
};
```

```typescript
// FIXED: use Web Streams / fetch APIs, or restrict deployment to Node
const readFileTool = {
  execute: async ({ context }) => {
    const res = await fetch(`https://storage/${context.path}`);
    return await res.text();
  },
};
```

### Pitfall 2: Untyped workflow state

```typescript
// BROKEN: any-typed steps; runtime errors when fields don't match
const step = createStep({
  id: "x",
  inputSchema: z.any(),  // No validation
  outputSchema: z.any(),
  execute: async ({ inputData }: any) => {
    return { result: inputData.foo.bar };  // Runtime error if foo missing
  },
});
```

```typescript
// FIXED: tight Zod schemas
const step = createStep({
  id: "x",
  inputSchema: z.object({ foo: z.object({ bar: z.string() }) }),
  outputSchema: z.object({ result: z.string() }),
  execute: async ({ inputData }) => {
    return { result: inputData.foo.bar };  // Validated; typed
  },
});
```

**War story**: A team deployed a Mastra workflow to Cloudflare Workers; first invocation failed because a tool used `crypto.randomUUID()` — fine in Node 19+ but Workers needed `crypto.subtle`. After switching to platform-agnostic UUID library, worked on both. Lesson: edge runtimes have subtle differences; test on target platform during development.

---

## 11. Technologies & Tools

| Tool | Purpose |
|---|---|
| `@mastra/core` | Core agent + workflow |
| `@mastra/memory` | Memory abstraction |
| `@mastra/libsql` / `@mastra/pg` | Vector stores |
| `@mastra/voice-openai` | Voice support |
| Zod | Schema validation |
| `@ai-sdk/anthropic` etc | Model adapters |
| `MastraMCPClient` | MCP tools |
| Mastra CLI | Scaffold + dev server |

---

## 12. Interview Questions with Answers

**Q: What's the difference between a Mastra Agent and a Mastra Workflow?**
An Agent is LLM-driven — the model decides which tools to call and when to stop. A Workflow is a deterministic DAG of steps — you define the structure, and steps can be deterministic functions or LLM calls. Use Agents for open-ended tasks; Workflows for predictable business processes.

**Q: Why TypeScript-first matters for agent frameworks?**
Type safety prevents schema drift between LLM outputs, tool params, and downstream consumers. In full-stack JS apps, sharing types between agent code and frontend (via shared Zod schemas) eliminates a whole class of integration bugs.

**Q: How does Mastra handle deployment to edge runtimes?**
Edge-friendly by default — avoids Node-specific APIs in core. Provides adapters for Vercel Edge, Cloudflare Workers, AWS Lambda Edge. Limitations: some tool implementations may use Node APIs (fs, child_process); those need alternatives or Node-runtime deployment.

**Q: What happens when a step's output fails its Zod outputSchema validation at runtime, and why is `z.any()` dangerous?**
Zod validates every step's output against its `outputSchema` when the step returns; a mismatch fails the workflow run at that step with a descriptive validation error, giving you the exact field and step id. With `z.any()` schemas, validation is disabled — malformed data flows into downstream steps and fails later (or worse, silently produces wrong output), and TypeScript inference degrades to `any` so the compiler cannot catch the drift either. Always write tight schemas; the definition-time and runtime checks are the framework's main correctness guarantee.

**Q: How is memory implemented?**
The `Memory` class wraps a vector store (LibSQL with native vector ops, or Postgres pgvector). Configurable retrieval: last N messages verbatim + top-K semantic recall. Embeddings via the model's embedding API or a dedicated embedder. Memory persists across agent calls within a session (`threadId`).

**Q: What's the Workflow execution model?**
Steps execute in order defined by `.then()` calls. Parallel steps via `.parallel([step1, step2])` run concurrently. Branching via `.branch(condition, [step1], [step2])`. Loops via `.until(condition, [stepN])`. Each step's output is typed and validated; mismatches fail at workflow definition time.

**Q: How do outputs from `.parallel()` steps reach the next step?**
The step after `.parallel([stepA, stepB])` receives an object keyed by step id — in the PR-review example, the compose step's `inputSchema` declares `{ security: {...}, style: {...} }` and reads `inputData.security.issues` and `inputData.style.issues`. Both parallel branches must complete before the join step runs; if either fails, the workflow run fails. Declare the join step's inputSchema to mirror the parallel step ids exactly — a key mismatch fails at workflow definition time, which is far cheaper than a production error.

**Q: How do you stream agent output?**
`const stream = await agent.stream(input);` returns a `StreamResult` with `textStream` async iterable. For structured output, `.objectStream`. For tool calls, `.fullStream` emits all events (tool_call, tool_result, text, etc).

**Q: What's the voice agent story?**
`VoiceAgent` with input + output `VoiceModel`. OpenAI Realtime API integration most mature. Handles WebRTC/WebSocket audio streaming, turn detection, barge-in. Limitation: requires server-sent events or WebSocket from server to client; not pure-edge yet.

**Q: How does Mastra integrate with MCP?**
`MastraMCPClient({ command, args })` spawns an MCP server (e.g., a Python or Node MCP server), lists its tools, exposes them as Mastra tools. Use in Agent.tools list. Cleanup via `await client.close()` when done.

**Q: What about evals?**
Built-in eval primitives: define metrics as functions taking model output + expected, return scores. Combine into eval suites; run with `mastra dev` (CLI) or as part of CI. Less mature than LangSmith but built-in to the framework.

**Q: Can Mastra workflows be durable / resumable?**
Currently limited durability — workflows persist state to memory by default. For production durability, integrate with external systems (Inngest, Temporal) as you would with any framework. Native durability is a roadmap item.

**Q: Cost overhead vs direct API calls?**
Minimal — Mastra is a thin layer over `@ai-sdk/*` provider clients. No additional LLM calls except where you use built-in features (memory retrieval uses embeddings; evals run model calls). Net cost ~equal to direct.

**Q: How do you debug a failing workflow?**
Mastra CLI `mastra dev` provides a UI showing workflow runs, step states, inputs, outputs, errors. Logs each step's execution time and Zod validation results. For production, integrate OpenTelemetry exporter.

**Q: What's the MCP client experience like?**
Smooth. Connect to stdio or HTTP MCP server; tools appear in autocomplete via TypeScript types (when MCP server provides typed schemas). Use Anthropic's, your own custom, or community MCP servers.

**Q: Compared to LangChain JS, what does Mastra do better?**
Workflows (LangChain JS lacks LangGraph-equivalent), built-in memory abstraction with first-class vector stores, voice support, eval harness, deployment-focused CLI. LangChain JS has more model/tool integrations. See [LangChain & LCEL](langchain_and_lcel.md) for the LangChain-side comparison.

**Q: How do you run a long multi-step workflow on an edge runtime with execution-time limits?**
You mostly shouldn't — edge runtimes cap execution (Cloudflare Workers allows ~10ms CPU on the free tier and ~30s on paid; wall-clock waiting on LLM I/O doesn't count against CPU but request duration limits still apply), so a workflow with several sequential LLM steps can exceed the budget. Patterns that work: keep only the request-facing agent call on the edge and enqueue the heavy workflow to a Node runtime or a durable executor (Inngest, Temporal); or split the workflow so each edge invocation runs one step and persists state between invocations. Measure your worst-case step chain on the target platform before committing to edge deployment.

---

## 13. Best Practices

1. Use Zod for all tool schemas — runtime validation + compile-time types in one definition.
2. Choose Agent vs Workflow consciously — workflow for known process, agent for unknown navigation.
3. Test on your target deployment runtime (Vercel Edge has different API surface than Node).
4. Use Memory's `semanticRecall` for long conversations to retrieve relevant past context.
5. Use `mastra dev` CLI during development — UI reveals workflow state visually.
6. Pin Mastra version; pre-1.0 framework so API still evolves.
7. Pair Mastra workflows with Inngest/Temporal for production durability beyond in-memory state.
8. Use `.parallel()` for independent steps to maximize throughput.
9. Stream outputs to client via Vercel AI SDK's `streamText` integration for smooth UX.
10. Add OpenTelemetry exporter for production observability.

---

## 14. Case Study

**PR Review Agent for an Open-Source Project**

**Context**: A 50-contributor open-source project needed automated PR review for: security issues, code style, test coverage, breaking API changes. Built with Mastra on Cloudflare Workers (triggered by GitHub webhook).

**Architecture**:
- GitHub webhook → Cloudflare Worker → Mastra Workflow
- Workflow steps: `extractDiff` → parallel([securityAgent, styleAgent, testCoverageAgent, breakingChangesAgent]) → `composeReview` → `postComment`
- Each "agent" step uses Claude Haiku for cost efficiency
- Memory: persists past PR reviews per author for personalized style suggestions

**Results**:
- Avg review latency: 4 seconds (Workers cold start + 4 parallel LLM calls)
- Cost: $0.008 per PR (mostly Haiku Bedrock)
- Maintainer time saved: ~15 min per PR (covers 80% of feedback)
- Workers cold start with Mastra runtime: ~120ms

**Lessons**:
1. Workflow .parallel() with 4 independent agents kept latency low — sequential would have been 16s.
2. Mastra's TypeScript types caught a schema mismatch between extractDiff and securityAgent during dev.
3. Cloudflare Worker deployment was friction-free — `mastra deploy` to existing CF account.
4. Memory's semantic recall over past reviews surprised maintainers — agent learned individual reviewer preferences.
