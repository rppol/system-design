# LLM apps & agents — technology bank

<!-- tech-bank tier: llm-apps -->

The 255 tools whose PRIMARY role — the first, best-weighted one — sits in
the **LLM apps & agents** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### /client
**Short:** MCP TypeScript SDK client module: the tier-1 Node.js entry point for connecting to MCP servers.
**Kind:** api
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

### 2captcha
**Short:** Paid API that solves CAPTCHAs on demand, used to keep browser automation and web agents moving past challenge pages.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### @ai-sdk/anthropic
**Short:** Vercel AI SDK provider package that adapts Anthropic models to the SDK's uniform generate/stream interface.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @3

### @anthropic-ai/sdk
**Short:** Official TypeScript/JavaScript client for the Anthropic API: messages, streaming, tool use and key handling.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/llm-gateway-and-routing @1, apis-frameworks/web-framework-and-http-client @3

### @mastra/core
**Short:** TypeScript agent framework core: agents, tools and durable workflow primitives.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, data-movement/workflow-and-durable-execution @2

### @mastra/memory
**Short:** Mastra's TypeScript agent memory package: persists conversation history and recalled context between agent turns.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

### @modelcontextprotocol/inspector
**Short:** Browser UI that connects to any MCP server to explore its tools/resources and issue test calls interactively.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

### @modelcontextprotocol/sdk
**Short:** Official TypeScript SDK for building MCP servers and clients over stdio or streamable HTTP transports.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

### @modelcontextprotocol/server
**Short:** Node package for implementing an MCP server that exposes tools, resources and prompts to any MCP client.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

### @modelcontextprotocol/server-everything
**Short:** Reference MCP server exercising every protocol feature; used to test clients rather than run in production.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

### @modelcontextprotocol/server-filesystem
**Short:** Reference MCP server exposing local filesystem read, write and list operations as agent tools.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

### @modelcontextprotocol/server-memory
**Short:** Reference MCP server giving an agent a persistent knowledge-graph memory of entities and relations.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @2, data-stores/graph-db @3

### @modelcontextprotocol/servers
**Short:** Official reference MCP server implementations (filesystem, git, fetch and friends) published as npm packages.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

### @openai/agents
**Short:** TypeScript build of the OpenAI Agents SDK: agents, tools, handoffs and guardrails with the same primitives as Python.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### @Tool
**Short:** Spring AI annotation marking a method as a callable tool, generating its JSON schema for model function calling.
**Kind:** api
**Lang:** java
**Roles:** llm-apps/tool-use-and-mcp @1

### @tool decorator
**Short:** Decorator that turns a plain function into an agent-callable tool with an auto-derived schema.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

### a2aproject/a2a-js
**Short:** Official JavaScript/TypeScript SDK for the Agent2Agent protocol so agents can discover and call each other.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### a2aproject/a2a-python
**Short:** Official Python SDK for the Agent2Agent protocol; lets independent agents advertise skills and exchange tasks.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, apis-frameworks/rpc-graphql-and-streaming @3

### ACP
**Short:** Agentic Commerce Protocol from OpenAI and Stripe: product feed, REST/MCP checkout and a shared payment token.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, apis-frameworks/data-formats-and-api-contracts @2

### adk api_server
**Short:** Google ADK CLI command that serves an agent over local REST for integration testing.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, devtools/testing-and-mocking @3

### ADK Java
**Short:** Google's Agent Development Kit for the JVM: LlmAgent, workflow agents, Runner and session/memory services.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

### ADK Python
**Short:** Google's Agent Development Kit for Python: LlmAgent, workflow agents, Runner, session/memory services and eval hooks.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

### adk run
**Short:** Google Agent Development Kit CLI that runs an agent locally and exposes a local REST API for integration testing.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, devtools/testing-and-mocking @3

### adk web
**Short:** Local browser dev UI for Google ADK that visualizes the agent tree and replays the live event trace of a run.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, observability/tracing-apm-and-llm-observability @2

### agent_as_tool
**Short:** Pattern exposing a whole sub-agent as a callable tool, so one agent can delegate without a handoff.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### Agenta
**Short:** Open-source prompt registry and playground with versioning and custom evaluation metrics for prompt iteration.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/evaluation-and-benchmarks @2, ml-lifecycle/experiment-tracking-and-tuning @3

### agents.extensions.models
**Short:** OpenAI Agents SDK adapter package letting the agent loop run on non-OpenAI models via LiteLLM or Anthropic.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @3

### Agentverse
**Short:** Fetch.ai's hosted marketplace and registry where autonomous agents are published, discovered and hired.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

### aider
**Short:** Open-source terminal coding agent that edits a git repo and commits its own changes.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

### Amazon Bedrock
**Short:** AWS managed service exposing many foundation-model providers behind one API, with guardrails, batching and VPC access.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @2, inference/model-server @3

### Amazon Bedrock Intelligent Prompt Routing
**Short:** Managed Bedrock feature that predicts per-request response quality and routes between two models in one family.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @3

### anthropic
**Short:** Official Anthropic Python SDK with sync and async clients for messages, streaming, tools and batching.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/tool-use-and-mcp @3, apis-frameworks/web-framework-and-http-client @3

### Anthropic Claude Opus
**Short:** Anthropic's highest-reasoning hosted Claude tier, typically used as the orchestrator in multi-agent systems.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2

### Anthropic Claude Sonnet 5
**Short:** Anthropic's hosted general-purpose Claude model, called over the provider API for agentic and long-context work.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2, applied-ml/nlp-and-text @3

### Anthropic Computer Use
**Short:** Claude tool that drives a real screen: the model sees a screenshot and returns mouse/keyboard actions.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @2

### Anthropic Computer Use API
**Short:** Anthropic tool letting a model see a screen and drive mouse and keyboard to operate a real desktop.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @2

### anthropic SDK
**Short:** Official client library for the Anthropic Messages API: streaming, tool use, and structured output helpers.
**Kind:** tech
**Lang:** python, js
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @2

### Anthropic tool use
**Short:** Claude's function-calling interface: JSON tool schemas the model selects and fills, plus constrained outputs.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @2

### Any OpenAI-compatible SDK
**Short:** Any client speaking the OpenAI /v1 request shape, which is what lets you swap a managed API for a self-hosted server.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, apis-frameworks/data-formats-and-api-contracts @3

### AP2 (Agent Payments Protocol) v0.2
**Short:** Google-led open protocol for agent-initiated payments: Checkout and Payment Mandates over A2A/MCP, rail-agnostic.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### AutoGen
**Short:** Microsoft multi-agent framework where agents solve tasks by structured conversation, with first-class code execution.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @3

### autogen-agentchat
**Short:** AutoGen's agent and team layer: AssistantAgent plus round-robin, selector, swarm and graph team topologies.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### autogen-core
**Short:** AutoGen's low-level runtime: typed messages, RoutedAgent and the event loop multi-agent topologies are built on.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### autogen-ext
**Short:** Extension package for AutoGen supplying model clients, tool adapters and code executors for its agent runtime.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### autogen-ext[magentic-one]
**Short:** AutoGen extra shipping the Magentic-One reference multi-agent team (orchestrator plus web/file/coder agents).
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @3

### autogen-ext[openai]
**Short:** AutoGen extension package providing the OpenAI model client; installed separately from the autogen core.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @3

### awesome-mcp-servers
**Short:** Community-curated catalogue of Model Context Protocol servers, used to find an existing server before writing one.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### BabyAGI
**Short:** Early autonomous-agent script keeping a task queue: execute the next task with an LLM, then re-plan.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, data-movement/task-queue-and-jobs @3

### BAML
**Short:** Typed prompting language with a compiler: a prompt declares its output schema and generates typed client functions.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, devtools/compiler-toolchain-and-codegen @2

### BedrockModel
**Short:** Agent-framework model client that routes generation through AWS Bedrock's hosted model catalogue.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2

### BeeAI framework
**Short:** IBM Research agent framework with A2A server and client adapters for cross-framework agent interoperability.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

### Brave Search API
**Short:** Web search API over an independent index, used as an agent tool and grounding source for RAG.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @2

### Browser MCP
**Short:** MCP server exposing a real browser to an agent as tools - navigate, read the page, click and type.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @1

### Browser Use
**Short:** Python library letting an LLM drive a Playwright browser: page state to the model, clicks and typing back out.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1

### browser-use
**Short:** Python library that drives Chromium via Playwright and the accessibility tree so an LLM can browse the web.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @3

### Browserbase
**Short:** Hosted headless-browser infrastructure giving agents fresh, scalable Chrome sessions with proxies and recording.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, platform-delivery/cloud-platform-and-cost @3

### CapSolver
**Short:** Commercial captcha-solving API used by browser automation and web agents to get past challenge pages.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### Carnegie Learning MATHia
**Short:** Commercial adaptive math tutoring software that models a student's skills and personalises problems step by step.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @3

### ChatDev
**Short:** Research multi-agent framework simulating a software company, with role-playing agents in a waterfall pipeline.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### ChatGPT
**Short:** OpenAI's chat product and API surface, including built-in connections to remote MCP servers and tools.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @3

### Claude
**Short:** Anthropic's hosted frontier model family, including adaptive and extended thinking modes billed as output tokens.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/llm-gateway-and-routing @3

### Claude API
**Short:** Anthropic's hosted Claude endpoint; long-context (1M on Opus 5/Sonnet 5) text and tool-use generation.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/prompting-context-and-structured-output @2

### Claude Code
**Short:** Anthropic's terminal coding agent: autonomous file editing, subagents, prompt caching and MCP tool access.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @2

### Claude Code Agent tool
**Short:** Claude Code's built-in tool for spawning isolated subagents with their own context to run a delegated task.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, llm-apps/agentic-environments @3

### Claude Desktop config
**Short:** claude_desktop_config.json, the file that declares which MCP servers Claude Desktop launches and with what args.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### Claude extended thinking
**Short:** Anthropic API parameter that gives the model a visible reasoning budget before it answers.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1

### Claude thinking
**Short:** Anthropic API parameter that lets the model spend extra internal reasoning tokens before answering.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3

### claude_desktop_config.json
**Short:** Claude Desktop's config file declaring which MCP servers to launch, with their commands, arguments and environment.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### Cline
**Short:** Open-source VS Code coding agent that plans, edits files and runs commands using a model you choose.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### Codeium
**Short:** AI code completion and chat assistant embedded in the IDE, free for individual use.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### Codestral
**Short:** Mistral's open-weight code model with a 32K context, tuned for fast completion and fill-in-the-middle.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, applied-ml/nlp-and-text @3

### Computer use docker reference
**Short:** Anthropic's quickstart container image providing a sandboxed desktop (VNC, browser, shell) for the computer-use tool.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, platform-delivery/container-and-image @3

### Continue
**Short:** Open-source, model-agnostic coding agent extension for VS Code and JetBrains.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### Continue.dev
**Short:** Open-source IDE coding-assistant plugin that can point at self-hosted or local models for privacy-sensitive codebases.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

### Copilot coding agent
**Short:** GitHub's PR-native coding agent that takes an assigned issue and opens a pull request with the change.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

### Copy.ai
**Short:** SaaS product generating marketing and sales copy at scale from LLM workflows and brand-tuned templates.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1

### CrewAI
**Short:** Role-based multi-agent Python framework: define agents with roles, goals and tools, then run them as a crew.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### crewai-tools
**Short:** Prebuilt tool library for CrewAI agents: web search, site scraping, file reading and retrieval-style tools.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### CRITIC
**Short:** Self-correction technique where the model verifies and revises its output using external tools, not self-critique.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2, ml-lifecycle/evaluation-and-benchmarks @3

### Cursor
**Short:** AI-first code editor with codebase-aware chat and multi-file agentic edits.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

### Cursor IDE
**Short:** AI-native code editor with inline edit, composer and background agents, and a built-in MCP client.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2, llm-apps/tool-use-and-mcp @3

### Cursor MCP config
**Short:** Cursor's mcp.json configuration declaring which MCP servers the editor launches and exposes to its agent.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agentic-environments @2

### Custom DistilBERT
**Short:** A small DistilBERT classifier fine-tuned in-house to route prompts between strong and weak models at low latency.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, applied-ml/nlp-and-text @3

### Daytona
**Short:** Managed sandbox runtime giving an agent a disposable full Linux box to run untrusted code in.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, platform-delivery/container-and-image @3

### Devin
**Short:** Commercial autonomous software-engineering agent that runs long sessions in its own VM with a plan UI.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### Docker sandboxes
**Short:** Containers used as disposable isolated environments for executing untrusted model-generated code.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @2, platform-delivery/container-and-image @3

### DSPy
**Short:** Framework that treats prompts as programs and auto-optimizes them against a metric instead of hand-tuning strings.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3, ml-lifecycle/evaluation-and-benchmarks @3

### dspy-ai
**Short:** DSPy framework that declares LLM programs as typed modules and compiles prompts and few-shot demos automatically.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

### dspy.LM
**Short:** DSPy's unified language-model client abstracting OpenAI, Anthropic, Cohere, Ollama and HuggingFace backends.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/llm-gateway-and-routing @2

### dspy.teleprompt
**Short:** DSPy's optimizer module: BootstrapFewShot, MIPRO and friends that compile prompts against a metric.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1

### Duolingo AI
**Short:** Duolingo's LLM features for language learning, including conversational speaking practice and explanations.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @3, applied-ml/vision-speech-and-multimodal @3

### E2B
**Short:** Cloud Firecracker microVM sandboxes for running agent-generated code safely, with sub-second start times.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @3

### Epic
**Short:** Electronic health record platform; the system of record LLM clinical-documentation features integrate into.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### FastMCP
**Short:** Decorator-based Python framework for building MCP servers: expose tools, resources and prompts with type hints.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

### Fine-tuned models
**Short:** Index entry for tool-calling fine-tunes such as Gorilla and ToolLLaMA, trained with a retriever in the loop.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, model-training/fine-tuning-and-peft @2

### Gemini 3.x
**Short:** Google's frontier multimodal reasoning model family, with reasoning depth set by the thinking_level enum.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/prompting-context-and-structured-output @3

### Gemini API
**Short:** Google's hosted Gemini endpoint, notable for a million-token context window and native multimodal input.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/tool-use-and-mcp @3

### Gemini responseSchema
**Short:** Gemini API field pinning a JSON schema so the provider constrains decoding to a valid structured response.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1

### GitHub Copilot
**Short:** The most widely used IDE code-completion and chat assistant, sold as a per-seat subscription.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

### GitHub modelcontextprotocol/servers
**Short:** The official repository of reference MCP servers - filesystem, git, fetch and more - to copy or run as-is.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### Guidance
**Short:** Microsoft library for prompt templating with constrained generation and token healing into a fixed structure.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1

### haystack-experimental
**Short:** Haystack's pre-release channel where new agent and RAG components ship before stabilising into core.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @2

### HfApiModel
**Short:** smolagents model adapter that drives an agent using models hosted on the Hugging Face Hub inference API.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @3

### Humanloop
**Short:** Prompt management platform: a versioned prompt registry, non-engineer editing UI, evaluations and A/B deployment.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/evaluation-and-benchmarks @2, observability/tracing-apm-and-llm-observability @3

### Instructor
**Short:** Coerces LLM output into validated Pydantic models, retrying on schema failure, with one API across providers.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/tool-use-and-mcp @3

### instructor-haystack
**Short:** Haystack integration for Instructor, forcing generator output to validate against a Pydantic response model.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3

### Intercom Fin
**Short:** Intercom's customer-support AI agent that resolves inquiries autonomously from a company's help content.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @3

### Jamba
**Short:** AI21's hybrid Mamba-plus-Transformer LLM, whose state-space layers make very long contexts cheaper to serve.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, applied-ml/nlp-and-text @3

### JSON-RPC log inspection
**Short:** MCP debugging technique: turn on SDK debug logging to dump every JSON-RPC request and response to stderr.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, observability/logging @2

### Khan Academy Khanmigo
**Short:** Khan Academy's LLM tutor product: Socratic step-by-step coaching in math, science and coding rather than answers.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### LangChain
**Short:** General LLM app framework: LCEL pipelines, prompt templates, retrievers and create_agent tool-calling loops.
**Kind:** tech
**Lang:** python, js
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @1, llm-apps/prompting-context-and-structured-output @2, llm-apps/tool-use-and-mcp @3

### LangChain create_agent
**Short:** LangChain factory building a ReAct-style tool-calling agent loop with middleware hooks for guards and summarization.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### LangChain MCP adapter
**Short:** Bridge that converts MCP server tools into LangChain/LangGraph tool objects.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### LangChain tools
**Short:** LangChain's tool abstraction plus its catalog of prebuilt integrations that an agent can call.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### langchain-anthropic
**Short:** LangChain's Anthropic provider package supplying ChatAnthropic for Claude-backed chains and agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @2

### langchain-classic
**Short:** Compatibility package holding LangChain's pre-1.0 chains such as LLMChain and RetrievalQA; only for legacy code.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### langchain-community
**Short:** LangChain's package of 300+ third-party integrations: vector stores, loaders, tools and model providers.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### langchain-core
**Short:** LangChain's stable base package: Runnable composition primitives, message and prompt types shared by integrations.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2

### langchain-openai
**Short:** LangChain's OpenAI integration package supplying ChatOpenAI, embeddings and function-calling for agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @3, llm-apps/tool-use-and-mcp @3

### LangGraph
**Short:** Graph runtime for stateful, checkpointed LLM agents with branching, loops and human-in-the-loop.
**Kind:** tech
**Lang:** python, js
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2, data-movement/workflow-and-durable-execution @2, search-retrieval/rag-and-document-processing @3

### LangGraph checkpointing
**Short:** LangGraph's state persistence layer: per-node checkpoints in memory, SQLite or Postgres, enabling interrupt and resume.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, data-movement/workflow-and-durable-execution @2, llm-apps/prompting-context-and-structured-output @3

### LangGraph Cloud
**Short:** Managed hosting for LangGraph agents: durable execution with built-in checkpointing, scaling and observability.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, platform-delivery/cloud-platform-and-cost @3, observability/tracing-apm-and-llm-observability @3

### LangGraph interrupt
**Short:** LangGraph call that pauses a graph inside a node and surfaces state to the caller for human-in-the-loop approval.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @3

### LangGraph subgraphs
**Short:** Nested LangGraph graphs used as reusable subagents, each with its own state schema and checkpointed execution.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, data-movement/workflow-and-durable-execution @2

### LangGraph tool node
**Short:** Prebuilt LangGraph node that executes the tool calls in the last message and feeds results back, with error handling.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### langgraph-checkpoint-postgres
**Short:** LangGraph checkpointer storing graph state and agent memory in PostgreSQL for durable production runs.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2, data-stores/relational @3

### langgraph-checkpoint-sqlite
**Short:** LangGraph checkpointer persisting agent graph state to SQLite so a run can resume after a restart.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, data-movement/workflow-and-durable-execution @2, data-stores/key-value-and-embedded @3

### LangMem
**Short:** Long-term agent memory for LangChain: stores reflections and retrieves them by recency and importance.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1

### LangSmith Prompt Hub
**Short:** Versioned prompt registry in LangSmith, tied to its datasets and evals so a prompt change can be scored.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/evaluation-and-benchmarks @2, observability/tracing-apm-and-llm-observability @3

### Letta
**Short:** Agent framework (formerly MemGPT) giving agents OS-style memory: self-managed context and archival paging.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

### LiteLLM
**Short:** Unified API and proxy over 100+ LLM providers with routing, fallbacks, cost tracking and response caching.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, caching/semantic-and-llm-cache @2, observability/tracing-apm-and-llm-observability @3

### litellm Proxy
**Short:** Self-hosted LLM gateway giving one OpenAI-shaped endpoint over many providers with keys, budgets and fallback.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @3

### litellm Python SDK
**Short:** Python client giving one OpenAI-shaped call signature over 100+ model providers, with fallback, retry and cost tracking.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1

### LiteLlm wrapper
**Short:** Google ADK adapter that routes an LlmAgent's model calls to non-Gemini providers through LiteLLM.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2

### LiteLLMModel
**Short:** Agent-framework model adapter (smolagents, Strands) that routes calls to any provider through LiteLLM.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2

### LiveKit Agents
**Short:** Voice-first agent framework on LiveKit WebRTC, wiring STT, LLM and TTS with turn detection and barge-in.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, applied-ml/vision-speech-and-multimodal @2, apis-frameworks/rpc-graphql-and-streaming @3

### llama.cpp GBNF
**Short:** llama.cpp's BNF-style grammar format constraining sampling to a schema; the engine behind Ollama's JSON mode.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2

### LlamaIndex Agents
**Short:** LlamaIndex's data-agent layer (ReActAgent, SubQuestionQueryEngine) for query planning and tool use over indexed corpora.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @2, llm-apps/tool-use-and-mcp @3

### llguidance
**Short:** Compile-free token-trie lexer engine for constrained decoding; backs Guidance and vLLM's guidance grammar backend.
**Kind:** tech
**Lang:** rust
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2

### LLMLingua
**Short:** Neural prompt compressor that drops low-information tokens from long retrieved context while keeping the answer.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, search-retrieval/rag-and-document-processing @3

### LLMLingua-2
**Short:** Neural prompt compressor that drops low-information tokens from long retrieved context while preserving task accuracy.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, search-retrieval/rag-and-document-processing @2

### lm-format-enforcer
**Short:** Constrained-decoding library forcing generation to match a JSON schema or regex; a vLLM/HF backend.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @3

### LocalPythonExecutor
**Short:** smolagents' built-in restricted Python interpreter for running agent-written code with a limited import allowlist.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @3

### longrope
**Short:** LongRoPE implementation: non-uniform RoPE rescaling that extends usable context past the training length.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, model-training/fine-tuning-and-peft @3

### Mamba
**Short:** Selective state-space sequence architecture with linear-time scaling; an attention alternative for long context.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, applied-ml/nlp-and-text @3

### marvin
**Short:** High-level library that maps LLM output onto Python types for typed extraction and classification.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3

### Mastercard Agent Pay
**Short:** Mastercard's agentic payment tokens, extending MDES tokenization so an AI agent can transact with scoped credentials.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, security/authentication-and-identity @3

### Mastra CLI
**Short:** Mastra's CLI: scaffolds an agent project and runs the local dev server and playground.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, devtools/version-control-and-workbench @3

### MastraMCPClient
**Short:** Mastra client that connects to MCP servers and exposes their tools to a TypeScript agent.
**Kind:** api
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

### mcp
**Short:** The official Python SDK for the Model Context Protocol: build MCP servers and clients over stdio or HTTP.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

### MCP CLI
**Short:** Command-line runner for MCP servers that starts one locally and lists or invokes its tools during development.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

### MCP Hub
**Short:** Community registry for discovering and installing Model Context Protocol servers.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### MCP Inspector
**Short:** Interactive UI for exercising an MCP server: list tools, call them, and watch the raw JSON-RPC traffic.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @2

### mcp Python SDK
**Short:** Official Python SDK for the Model Context Protocol: build MCP servers exposing tools and resources, or clients.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

### mcp SDK
**Short:** Model Context Protocol client and server SDK that hides transport details behind tool, resource and prompt APIs.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### MCP servers
**Short:** Model Context Protocol servers: processes exposing tools, resources and prompts over stdio or HTTP.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### MCP spec
**Short:** Model Context Protocol: JSON-RPC schema plus stdio and Streamable-HTTP transports exposing tools to models.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### mcp-cli
**Short:** Command-line MCP client for listing and invoking a server's tools, resources and prompts while developing or debugging.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

### mcp-server-fetch
**Short:** Reference MCP server that fetches a URL and converts the page to Markdown for a model to read.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @3

### mcp-server-git
**Short:** Reference MCP server exposing git read, search and mutation operations as agent tools.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/version-control-and-workbench @2

### mcp-server-time
**Short:** Reference MCP server exposing current-time and timezone-conversion tools to any MCP-capable model.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### mcp.ClientSession
**Short:** The MCP Python SDK's client session object: initializes the protocol, then lists and calls server tools.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

### MCPToolset
**Short:** ADK component that connects to MCP servers and exposes their tools to an agent as callable functions.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### Mem0
**Short:** Agent memory layer that extracts facts from conversations and persists them for recall across sessions.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3, data-stores/vector-store @3

### MemGPT
**Short:** Agent memory system (now Letta) that pages facts between a small context window and external storage, OS-style.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

### Mercury Coder API
**Short:** Inception Labs' hosted diffusion language model, aimed at very fast code completion.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, applied-ml/vision-speech-and-multimodal @3

### MetaGPT
**Short:** Multi-agent framework assigning software-team roles (PM, architect, engineer) that follow a structured SOP.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @3

### Microsoft Agent Framework
**Short:** Microsoft's supported agent runtime merging AutoGen and Semantic Kernel, with native MCP and A2A support.
**Kind:** tech
**Lang:** python, csharp
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, data-movement/workflow-and-durable-execution @3

### Microsoft Semantic Kernel
**Short:** Microsoft's enterprise agent SDK for C#, Python and Java: plugins, planners, memory and multi-agent orchestration.
**Kind:** tech
**Lang:** csharp, python, java
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, llm-apps/prompting-context-and-structured-output @3

### Microsoft.SemanticKernel
**Short:** Microsoft's .NET SDK for LLM applications: plugins, planners, function calling and agent orchestration.
**Kind:** tech
**Lang:** csharp
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

### modelcontextprotocol/csharp-sdk
**Short:** Official tier-1 C# SDK for building MCP servers and clients in .NET.
**Kind:** tech
**Lang:** csharp
**Roles:** llm-apps/tool-use-and-mcp @1

### modelcontextprotocol/go-sdk
**Short:** The official tier-1 Go SDK for building Model Context Protocol servers and clients.
**Kind:** tech
**Lang:** go
**Roles:** llm-apps/tool-use-and-mcp @1

### modelcontextprotocol/java-sdk
**Short:** Official Java MCP SDK for building MCP servers and clients, with Spring AI integration.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/tool-use-and-mcp @1

### modelcontextprotocol/rust-sdk
**Short:** Rust SDK for building MCP servers and clients; a tier-2 official implementation of the protocol.
**Kind:** tech
**Lang:** rust
**Roles:** llm-apps/tool-use-and-mcp @1

### Nuance DAX Copilot
**Short:** Healthcare vertical AI product that listens to a clinical visit and drafts the encounter note.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, applied-ml/vision-speech-and-multimodal @2

### Official MCP Registry
**Short:** The canonical registry of Model Context Protocol servers: verified reverse-DNS namespaces and server.json metadata.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### OpenAI Agents SDK
**Short:** OpenAI's agent runtime: Agent/Runner loop, typed tools, handoffs between agents, guardrails, sessions and tracing.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, security/ai-safety-and-guardrails @3, observability/tracing-apm-and-llm-observability @3

### OpenAI Agents SDK handoff
**Short:** OpenAI Agents SDK primitive that transfers a conversation to another agent; sequential, not parallel.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### OpenAI computer tool
**Short:** OpenAI Responses API tool giving a model screenshot input and mouse/keyboard actions to drive a computer.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @2

### OpenAI function calling
**Short:** OpenAI's tool-calling API: JSON-schema tool definitions the model selects and fills, with strict-mode guarantees.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @2

### OpenAI GPT-5.6
**Short:** OpenAI's frontier hosted model, strong at instruction following and commonly used as the LLM judge in evals.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/evaluation-and-benchmarks @2, llm-apps/agent-framework @3

### OpenAI Memory
**Short:** ChatGPT's server-side memory that persists user facts across conversations; consumer-facing, not a developer API.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1

### OpenAI Playground
**Short:** OpenAI's browser console for interactive prompt iteration, parameter sweeps and inspecting token probabilities.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, devtools/version-control-and-workbench @3

### openai Python SDK
**Short:** Official OpenAI Python client with sync and async surfaces for chat, responses, embeddings and tool calls.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @3, apis-frameworks/web-framework-and-http-client @3

### OpenAI Responses API
**Short:** OpenAI's agent-oriented endpoint: hosted tools, remote MCP, computer use and server-side conversation state.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2, llm-apps/agentic-environments @3, llm-apps/prompting-context-and-structured-output @3

### openai SDK
**Short:** OpenAI's official client library, including native structured outputs via chat.completions.parse.
**Kind:** tech
**Lang:** python, js
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/llm-gateway-and-routing @2, llm-apps/tool-use-and-mcp @3

### OpenAI Structured Outputs
**Short:** OpenAI API mode constraining decoding to a JSON Schema, so a response always parses into your declared type.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, apis-frameworks/data-formats-and-api-contracts @3

### openai-agents
**Short:** OpenAI Agents SDK: agents, handoffs, guardrails, sessions and tracing for tool-using loops.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

### OpenAI/Anthropic APIs, open-weight LLMs
**Short:** The general pool of hosted and open-weight LLMs used for zero/few-shot prototyping, long-tail cases and generation.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/llm-gateway-and-routing @3

### OpenHands
**Short:** Open-source coding-agent platform you self-host: sandboxed shell, editor and browser for an agent to work in.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @2

### OpenRouter
**Short:** Aggregator giving one OpenAI-compatible endpoint over 400+ models and 70+ providers, with fallback lists.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1

### Outlines
**Short:** Structured-generation library that compiles a regex or JSON schema into an FSM constraining the decoder.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @3

### Pipecat
**Short:** Python framework for real-time voice agents: pipelines wiring STT, LLM and TTS with interruption handling.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, applied-ml/vision-speech-and-multimodal @2

### Plandex
**Short:** Terminal coding agent that plans a change set first and checkpoints work so edits can be reviewed or rolled back.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @2

### Playwright MCP
**Short:** MCP server exposing Playwright browser control as tools, so an agent can navigate and act on real web pages.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agentic-environments @2

### Portkey
**Short:** Managed LLM gateway giving one endpoint over many providers with routing, fallback, caching and cost tracing.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, caching/semantic-and-llm-cache @2, observability/tracing-apm-and-llm-observability @2

### PromptLayer
**Short:** Prompt registry and logging platform: versioning, team collaboration and production request monitoring.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, observability/tracing-apm-and-llm-observability @2

### Puppeteer
**Short:** Node library driving headless Chrome over DevTools Protocol for scraping, screenshots and browser automation.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agentic-environments @1, devtools/testing-and-mocking @2

### pyautogen
**Short:** The pip package for AutoGen, Microsoft's conversational multi-agent framework.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### PyAutoGUI
**Short:** Cross-platform Python library driving real mouse and keyboard events for desktop automation and computer-use agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, devtools/testing-and-mocking @3

### pydantic-ai package
**Short:** Pydantic's typed agent framework: dependency-injected tools and model output validated into Python types.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2

### pydantic-ai-slim
**Short:** Minimal install of PydanticAI without bundled provider SDKs, for typed agents with only the deps you pick.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2

### Reflexion
**Short:** Agent technique (Shinn et al. 2023) where the model writes verbal self-critique into memory and retries the task.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @3

### RemoteA2aAgent
**Short:** ADK class that consumes a remote A2A agent as if it were local, and exposes local agents as A2A servers.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### Replicate
**Short:** Hosted inference marketplace: run open-weight models behind an API and pay per second of GPU time.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @2, inference/model-server @3

### RestrictedPython
**Short:** Compiles Python with a restricted AST and guarded builtins, giving in-process sandboxing of untrusted snippets.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @2

### Riza
**Short:** Hosted WebAssembly sandbox running untrusted LLM-generated Python, JavaScript, Ruby or PHP code safely.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @2

### ROS2
**Short:** Robot Operating System 2: pub/sub middleware over DDS for robot control; VLA servers integrate as ROS2 nodes.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, data-movement/message-broker @2, apis-frameworks/rpc-graphql-and-streaming @3

### RouteLLM
**Short:** Preference-trained routers that send easy prompts to a weak model and hard ones to a strong model.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, ml-lifecycle/evaluation-and-benchmarks @3

### Routing classifier
**Short:** A small fine-tuned classifier that picks which model or tool handles a request, in tens of milliseconds on CPU.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, applied-ml/nlp-and-text @2

### Salesforce Einstein
**Short:** Salesforce's embedded AI layer for CRM: agent assist, case routing, summarization and predictive scoring.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @3, applied-ml/nlp-and-text @3

### Self-Refine
**Short:** Prompting loop (Madaan et al. 2023) where one model drafts, critiques its own output, then revises it.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

### Semantic Kernel
**Short:** Microsoft's enterprise agent SDK with a plugin/function model and planners, available for C#, Java and Python.
**Kind:** tech
**Lang:** csharp, java, python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, llm-apps/prompting-context-and-structured-output @3

### semantic-kernel
**Short:** Microsoft's agent SDK: plugins, planners and connectors for embedding LLM calls in an application.
**Kind:** tech
**Lang:** python, csharp
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### semantic-kernel-azure-ai-inference
**Short:** Semantic Kernel connector for Azure AI Inference, exposing Azure-hosted models such as Phi-3 and Llama 3.
**Kind:** tech
**Lang:** python, csharp
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @2

### Serper.dev
**Short:** Low-cost Google Search API wrapper used as an agent's web-search tool; returns links needing a fetch/parse step.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @2, search-retrieval/lexical-and-hybrid-search @3

### SerperDevTool
**Short:** CrewAI's built-in web-search tool, calling Google results through the Serper API and returning them to the agent.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/lexical-and-hybrid-search @3

### sk-nightly
**Short:** Semantic Kernel nightly build feed: latest unreleased features, unstable and not for production pinning.
**Kind:** tech
**Lang:** csharp
**Roles:** llm-apps/agent-framework @1

### Skyvern
**Short:** LLM+vision browser automation agent that fills forms and drives workflows on sites without hand-written selectors.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @3

### Small OpenAI model
**Short:** A cheap small OpenAI model (gpt-4o-mini / nano class) used for query rewriting and other bulk transformations.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, search-retrieval/rag-and-document-processing @3

### Smithery
**Short:** Third-party MCP server registry and install CLI with publisher accounts for discovering agent tools.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### Smithery CLI
**Short:** Command-line installer and manager for MCP servers, wiring them into a client's config for you.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### smolagents package
**Short:** Hugging Face's minimal agent library whose agents act by writing and executing Python code rather than JSON calls.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @2

### smolagents ToolCollection
**Short:** smolagents helper importing an MCP server's tools as native agent tools in one call.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### SomAgent
**Short:** Set-of-mark style web agent research work that grounds a model's clicks to labelled page elements.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

### Spring AI
**Short:** Spring's AI framework: ChatClient over many providers, advisors, RAG, tool calling and vector-store abstractions.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @2, llm-apps/llm-gateway-and-routing @2, llm-apps/prompting-context-and-structured-output @3

### spring-ai-anthropic-spring-boot-starter
**Short:** Spring Boot starter auto-configuring a Spring AI ChatModel backed by Anthropic Claude, with tool calling.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/tool-use-and-mcp @3, apis-frameworks/dependency-injection-and-config @3

### spring-ai-ollama-spring-boot-starter
**Short:** Spring AI starter wiring a ChatModel to a local Ollama server, so a Spring app can call open models with no API key.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/llm-gateway-and-routing @1, inference/inference-engine @3, apis-frameworks/dependency-injection-and-config @3

### spring-ai-openai-spring-boot-starter
**Short:** Spring AI starter auto-configuring OpenAI chat, embedding and image clients from properties.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @3

### Stablecoins
**Short:** Price-pegged crypto tokens used as the settlement asset in agent micropayment rails such as x402.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @3

### Stagehand
**Short:** TypeScript browser-automation SDK on Playwright exposing act/extract/observe primitives for AI-driven web agents.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @3

### stdio_client
**Short:** MCP Python SDK helper that speaks the stdio transport to a locally spawned MCP server process.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

### Stealth plugin
**Short:** puppeteer-extra plugin masking the automation fingerprints that let sites detect a headless browser.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agentic-environments @1, devtools/testing-and-mocking @3

### strands-agents package
**Short:** Main SDK package for AWS Strands Agents, a model-driven Python framework for building tool-using agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### streamablehttp_client
**Short:** MCP Python SDK client for the Streamable HTTP transport, the successor to HTTP+SSE for remote MCP servers.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, apis-frameworks/rpc-graphql-and-streaming @2

### StreamingLLM
**Short:** Attention-sink plus sliding-window KV cache that lets a model stream indefinitely without perplexity collapse.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, caching/semantic-and-llm-cache @2, inference/inference-engine @3

### swarm
**Short:** OpenAI's reference implementation of agent handoffs and routines; read-only teaching code, never published to PyPI.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

### SWE-agent
**Short:** Princeton research coding agent with an agent-computer interface for autonomous repo bug fixing on SWE-bench.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, ml-lifecycle/evaluation-and-benchmarks @3

### Swift, Ruby, PHP, Kotlin SDKs
**Short:** The community-tier MCP SDKs for Swift, Ruby, PHP and Kotlin, behind the first-party Python/TypeScript ones.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### Tabby
**Short:** Self-hosted open-source coding assistant: an on-prem Copilot serving completions from models you control.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, inference/model-server @2

### Tavily Search
**Short:** Search API built for agents: returns cleaned, ranked, LLM-ready content and snippets instead of raw SERP HTML.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @3

### to_a2a
**Short:** Google ADK helper that exposes an agent as an A2A server, or wraps a remote A2A agent as a local one.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

### Together AI
**Short:** Managed inference provider running open-weight models behind an OpenAI-compatible API, plus fine-tuning.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, inference/model-server @2, platform-delivery/cloud-platform-and-cost @3

### Tool schema format
**Short:** The JSON-schema shape describing a callable tool's name, description and parameters to a model.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, apis-frameworks/data-formats-and-api-contracts @2

### Tool-augmented LLM guide
**Short:** Anthropic's tool-use cookbook: reference guidance on defining tool schemas and structuring a tool-calling loop.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

### ToolCallback
**Short:** Spring AI abstraction describing a callable tool - its schema and invocation - for model function calling.
**Kind:** api
**Lang:** java
**Roles:** llm-apps/tool-use-and-mcp @1

### ToolCollection.from_mcp
**Short:** smolagents helper that imports an MCP server's tools as a ready-to-use tool collection for an agent.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

### TransformersModel
**Short:** smolagents adapter that backs an agent with a locally loaded Hugging Face transformers model instead of an API.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, inference/inference-engine @3

### UCP
**Short:** Google and Shopify's agentic commerce protocol for product discovery, checkout and orders with signed Checkout objects.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, apis-frameworks/data-formats-and-api-contracts @2

### Vercel AI SDK
**Short:** TypeScript toolkit for LLM apps: one API over many providers plus React/Next.js streaming and tool-call hooks.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2, apis-frameworks/rpc-graphql-and-streaming @3

### Vertex AI Agent Engine
**Short:** Google Cloud managed runtime for deployed agents with sessions, memory, autoscaling and integrated evaluation.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, platform-delivery/cloud-platform-and-cost @2, ml-lifecycle/ml-platform-and-pipelines @3

### XGrammar
**Short:** Fast CFG-constrained decoding engine; the default structured-output backend in vLLM, SGLang and TensorRT-LLM.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2

### xgrammar, llguidance, and outlines
**Short:** Constrained-decoding backends masking logits to a JSON schema, regex or grammar for guaranteed structured output.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2, apis-frameworks/data-formats-and-api-contracts @3

### yarn
**Short:** YaRN - a RoPE rescaling method extending a model's usable context beyond its training length; a llama.cpp CLI option.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @3, model-training/fine-tuning-and-peft @3

### Zep
**Short:** Long-term memory service for agents building a temporal knowledge graph of entities and facts from conversations.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, data-stores/graph-db @2, search-retrieval/rag-and-document-processing @3
