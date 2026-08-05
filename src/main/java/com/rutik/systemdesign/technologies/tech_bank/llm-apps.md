# LLM apps & agents — technology bank

<!-- tech-bank tier: llm-apps -->

The 248 tools whose PRIMARY role — the first, best-weighted one — sits in
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

You post the challenge to its API -- an image, or for reCAPTCHA/hCaptcha/Turnstile the site key plus the page URL -- then poll until a solver returns a token, and inject that token into the page's hidden response field before submitting the form. Solving is a mix of human workers and automated solvers, which is why latency is seconds rather than milliseconds and price is per solve.

Reach for it only when a legitimate workflow is blocked by a challenge and no API exists. The costs are real: seconds added to every run, per-solve billing that scales with traffic, and the fact that you are deliberately defeating a control the site owner installed, which is usually a terms-of-service violation. An official API, an authenticated session you keep alive, or a data-sharing agreement are the durable answers.

### @ai-sdk/anthropic
**Short:** Vercel AI SDK provider package that adapts Anthropic models to the SDK's uniform generate/stream interface.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @3

It implements the Vercel AI SDK's language-model interface for Anthropic, translating `generateText` and `streamText` calls into Messages API requests, mapping tool definitions onto Anthropic's tool-use blocks, and surfacing vendor-specific features such as extended thinking and prompt caching through the SDK's provider-options escape hatch.

Reach for it when the app is TypeScript and you want changing models to be a one-line edit rather than a rewrite. The cost of that portability is lag: anything Anthropic ships that has no place in the SDK's common interface arrives late or only through provider options, so if Claude is the only model you will ever call, `@anthropic-ai/sdk` is more direct.

### @anthropic-ai/sdk
**Short:** Official TypeScript/JavaScript client for the Anthropic API: messages, streaming, tool use and key handling.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/llm-gateway-and-routing @1, apis-frameworks/web-framework-and-http-client @3

It is a typed wrapper over the Messages endpoint: `messages.create` for one shot, `messages.stream` for an accumulating stream you can await with `finalMessage()`, automatic retries with backoff on 429 and 5xx, an exception class per status code, token counting, and a `client.beta` namespace that sets the right beta headers for pre-GA features.

Reach for it whenever a Node or TypeScript service talks to Claude directly and you want the vendor's full surface without an abstraction in the way. The tradeoff is that call sites are provider-shaped, so adding a second model provider means rewriting them -- put the Vercel AI SDK or a gateway in front if portability matters more than feature freshness.

### @mastra/core
**Short:** TypeScript agent framework core: agents, tools and durable workflow primitives.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, data-movement/workflow-and-durable-execution @2

An agent binds a model, instructions and a set of typed tools; workflows are a separate primitive where each step declares its input and output schema and the graph is composed with sequencing, branching and parallel steps. Workflow runs are persisted, so a step can suspend waiting on human input and resume later in a different process, which is the part a plain tool-calling loop cannot express.

Reach for it when the stack is TypeScript end to end and you want agents and durable workflows from one library rather than gluing an agent loop to a job queue. The Python agent ecosystem is considerably denser, so if the team is polyglot the comparison is against LangGraph, and Mastra earns its place mainly on staying in one language and deploying as an ordinary Node service.

### @mastra/memory
**Short:** Mastra's TypeScript agent memory package: persists conversation history and recalled context between agent turns.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

Memory is scoped to a thread and a resource such as a user, and combines three mechanisms: a rolling window of recent messages, semantic recall that embeds past messages and retrieves the relevant few, and a working-memory block the agent rewrites itself to hold durable facts. Storage is pluggable, so the same code runs on a local file database in development and Postgres in production.

Reach for it when an assistant must feel continuous across sessions rather than replaying a transcript. The cost is that recalled messages and the working-memory block are prepended to every prompt, so an untuned recall count quietly doubles input tokens -- and a wrong fact written into working memory persists and biases every later turn until something overwrites it.

### @modelcontextprotocol/inspector
**Short:** Browser UI that connects to any MCP server to explore its tools/resources and issue test calls interactively.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

Run it against a server command or URL and it launches a local web app that connects as a genuine MCP client: it performs the initialize handshake, lists tools, resources and prompts, gives you a generated form per tool, and shows the raw JSON-RPC traffic in both directions alongside anything the server writes to stderr.

Use it as the first step whenever a server misbehaves, before the server is ever wired into a model -- most failures are a malformed input schema, a capability the server never advertised, or a handler that throws, and all three are obvious in the message log. What it cannot tell you is whether a model will choose the tool correctly; that depends on the description and is a separate experiment.

### @modelcontextprotocol/sdk
**Short:** Official TypeScript SDK for building MCP servers and clients over stdio or streamable HTTP transports.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

You declare tools, resources and prompts as typed handlers and the SDK handles the JSON-RPC framing, capability negotiation and transport, so a server is a few dozen lines rather than a protocol implementation. The same package ships the client side, which is what you use to connect a host application to somebody else's server.

Reach for it when the server is Node or TypeScript; Python, and several other languages, have their own official SDK with the same shape. Because MCP is versioned, pin the SDK and check the protocol version your host negotiates.

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

It is the protocol's conformance fixture: one server that exercises every feature at once -- tools with varied input schemas, tools that emit progress notifications during a long operation, resources of several content types, prompt templates, logging at each level, and a tool that asks the client to sample from the model. Nothing it does is useful work; each handler exists to make a client feature observable.

Point a client you are building at it to check that your handshake, schema rendering, progress display, resource fetching and sampling callback all behave, before you debug against a real server that only exercises a slice of the protocol. It is not something you ship -- for a production integration you want the narrowest server that does the job.

### @modelcontextprotocol/server-filesystem
**Short:** Reference MCP server exposing local filesystem read, write and list operations as agent tools.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

You launch it with a list of allowed directories as command-line arguments, and every tool argument is resolved and checked against those roots, so a path with `..` or a symlink pointing outside is rejected rather than followed. Tools cover reading, writing, editing by string replacement, listing, tree walks, search and move -- a small filesystem API rather than a shell.

Reach for it to give an agent scoped access to a project directory when a full terminal is more capability than the task needs. The directory allowlist is the only real guardrail, and the process runs with your own user permissions, so scope it to one project and never point it at a home directory. Note it is reference-quality code: no auditing, no quotas, no concurrency story.

### @modelcontextprotocol/server-memory
**Short:** Reference MCP server giving an agent a persistent knowledge-graph memory of entities and relations.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @2, data-stores/graph-db @3

Memory here is an explicit graph, not a blob of text: the agent calls tools to create entities, attach observations to them, and record typed relations between them, then searches nodes or reads the whole graph back. Everything persists to a single JSON file on disk, so the state is inspectable and editable by hand.

Reach for it when you want a model's long-term memory to be structured and auditable -- who works where, which service depends on which -- rather than an opaque vector index. The limits follow from the implementation: one JSON file means no concurrent writers and no scale, and the agent only builds the graph if its instructions tell it to, since nothing writes memories automatically.

### @modelcontextprotocol/servers
**Short:** Official reference MCP server implementations (filesystem, git, fetch and friends) published as npm packages.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/tool-use-and-mcp @1

Each reference server is published as its own package runnable with `npx`, so wiring one into a client is a command line in a config file rather than a build step. The implementations are deliberately small and readable in TypeScript or Python, which makes copying one the fastest way to learn the shape of a server: register handlers for listing and calling tools, pick a transport, run.

Reach for them to avoid writing a server for a common integration, and to crib from when you write your own. They are reference quality rather than product quality -- minimal guardrails, uneven maintenance, and several superseded by first-party servers the vendors now publish themselves. Check what a given server actually does before handing it credentials.

### @openai/agents
**Short:** TypeScript build of the OpenAI Agents SDK: agents, tools, handoffs and guardrails with the same primitives as Python.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

An agent is instructions plus tools plus an optional typed output schema, and the runner drives the loop until the model stops calling tools. Handoffs expose one agent to another as a callable tool so a triage agent can route to specialists; guardrails run alongside the main call and can abort the run before an expensive turn; tracing is on by default so a run is inspectable span by span.

Reach for it when the application is TypeScript and you want the loop handled while everything else stays ordinary code -- it is deliberately thin, with no chain, memory or retrieval abstractions of its own. That thinness is also the limit: there is no durable execution, so a crashed process loses the run, and anything needing checkpoints or branching belongs in a graph runtime.

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

The server side publishes an agent card at a well-known URL describing the agent's skills, endpoint and authentication, so another agent can discover what it can do without prior integration. Work is then exchanged as tasks over JSON-RPC with a defined lifecycle and streaming updates, so a long-running remote agent reports progress instead of blocking on a single response.

Reach for it when independent agents owned by different teams or vendors must call each other and you want a protocol rather than bilateral REST contracts. The protocol is young and the deployed ecosystem is small, so most systems get further with MCP alone -- MCP connects an agent to tools, A2A connects agents to each other, and only the second problem needs this.

### a2aproject/a2a-python
**Short:** Official Python SDK for the Agent2Agent protocol; lets independent agents advertise skills and exchange tasks.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, apis-frameworks/rpc-graphql-and-streaming @3

It ships both halves: a server built on ASGI that serves your agent's card and routes incoming task requests into an executor you implement, with a pluggable task store for state, and a client that discovers a remote card and sends or streams tasks against it. The task lifecycle is explicit, so an agent that needs more information can pause and ask rather than failing.

Reach for it when a Python agent must be callable by, or must call, agents built on other frameworks. The honest limitation is adoption: interoperability is only worth the abstraction if the other side speaks it, so for a system you own end to end, exposing the agent as an ordinary HTTP service or an MCP server is less machinery for the same result.

### ACP
**Short:** Agentic Commerce Protocol from OpenAI and Stripe: product feed, REST/MCP checkout and a shared payment token.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, apis-frameworks/data-formats-and-api-contracts @2

The design keeps the merchant as merchant of record. A product feed makes inventory discoverable to the agent surface, a REST checkout flow creates and updates a session and then completes it, and payment moves as a delegated token the merchant redeems through their own payment processor -- so the agent never holds card details and the merchant keeps the customer relationship, the settlement and the dispute liability.

Reach for it if you are a merchant wanting purchases to complete inside a chat surface rather than bouncing the user to a checkout page. The cost is integration work against a specification whose fate depends on adoption, and it is not alone: competing agentic-commerce protocols mean either betting on one or implementing several.

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

An LlmAgent binds a model, an instruction and tools, where a tool is an ordinary Java method whose signature and annotations generate the schema. Around that sit workflow agents -- sequential, parallel and loop -- that impose deterministic control flow rather than leaving ordering to the model, and a Runner that executes an agent against session and memory services holding conversation state.

Reach for it when the organisation is on the JVM and wants Google's agent model without standing up a Python service beside the existing stack. The ergonomics are Gemini and Vertex first, the Java implementation trails the Python one on new features, and the surrounding ecosystem of examples is thinner -- so weigh it against calling a model provider's Java SDK directly for simple cases.

### ADK Python
**Short:** Google's Agent Development Kit for Python: LlmAgent, workflow agents, Runner, session/memory services and eval hooks.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @3

The same building blocks with a fuller toolchain: LlmAgent for model-driven behaviour, sequential, parallel and loop workflow agents for control flow you want deterministic, a Runner backed by session, state and memory services, and callbacks that let you inspect or veto each model and tool call. A local development UI and an evaluation harness that scores both the final response and the trajectory of tool calls come with it.

Reach for it when you are deploying on Google Cloud and want the path to a managed runtime to be short. It is model-agnostic through LiteLLM, but the defaults, the deployment targets and the documentation all assume Gemini and Vertex -- outside that ecosystem the framework's advantages largely evaporate against a lighter agent library.

### adk run
**Short:** Google Agent Development Kit CLI that runs an agent locally and exposes a local REST API for integration testing.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, devtools/testing-and-mocking @3

It loads an agent from a project directory by convention, starting the interactive loop in your terminal so you can type a message and watch tool calls and responses without writing a driver script. Because it uses the same Runner and session services as a deployed agent, behaviour matches production more closely than a hand-rolled test harness would.

Reach for it for the fast inner loop while writing an agent -- change the instruction, rerun, see the difference. It is a development tool: sessions live locally, there is no concurrency or auth story, and a terminal transcript is a poor way to inspect a long multi-agent trace. The browser dev UI or exported traces are better once runs get complicated.

### adk web
**Short:** Local browser dev UI for Google ADK that visualizes the agent tree and replays the live event trace of a run.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, observability/tracing-apm-and-llm-observability @2

It starts a local server and browser UI where you pick an agent, chat with it, and inspect the run as structured events rather than console output: each model request and response, each tool call with its arguments and result, and the session state changes each step produced, laid out so you can click back through the trace. Saved evaluation sets can be run from the same UI.

Reach for it whenever a multi-agent or tool-heavy run misbehaves and the transcript does not explain why -- seeing which sub-agent was invoked and what state it wrote usually identifies the problem in one pass. It is local-only and single-user; production observability needs traces exported to a real backend.

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

It splits into a playground where prompt variants run side by side against the same inputs, a registry that versions each variant and lets a non-engineer edit one without a deploy, and an evaluation layer with built-in scorers, LLM-as-judge and custom Python evaluators run over a test set. Tracing links a production request back to the exact variant that produced it, so a regression has a cause.

Reach for it when prompt iteration is a team activity and you want changes scored instead of argued about, and self-hosting matters. The cost is another service to operate and a runtime dependency on fetching prompts; a solo developer usually gets further keeping prompts in the repository beside the code and adding a tracing tool for the request logs.

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

Agents built on Fetch.ai's uAgents framework register themselves with a description and an address, either hosted on the platform or running on your own infrastructure and registered remotely. A search layer indexes those descriptions so another agent, or a chat front end, can find an agent by capability and message it directly over the network's protocol rather than through a hard-coded integration.

Reach for it if you are specifically building on Fetch.ai's stack and want discovery without running your own directory. The bet is ecosystem-shaped: value depends entirely on other useful agents being registered and on that network staying alive, whereas an MCP server or a plain HTTP endpoint reaches every client without joining anything.

### aider
**Short:** Open-source terminal coding agent that edits a git repo and commits its own changes.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

You run it inside a git repository and add the files it may edit; aider builds a repository map so the model has structure beyond those files, asks for changes as diffs, applies them, and commits each change with a generated message -- so git history is the undo mechanism and reverting a bad edit is an ordinary revert. It can run your tests or linter and feed the failures back for another pass, and it works with whichever model you point it at through an API key. The explicit file-adding is deliberate: the context is what you chose, which keeps token cost predictable and stops it wandering through a large repository. Reach for it for surgical, well-scoped edits in code you already understand, from the terminal you are already in; a task that requires exploring many unfamiliar files is where more autonomous agents fit better.

### Amazon Bedrock
**Short:** AWS managed service exposing many foundation-model providers behind one API, with guardrails, batching and VPC access.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @2, inference/model-server @3

One request shape covers Anthropic, Meta, Mistral, Cohere and Amazon's own models, and the call stays inside your AWS account -- IAM for authentication, VPC endpoints for private networking, CloudWatch and CloudTrail for logs and audit. Around inference it adds Guardrails for content filtering and PII redaction, batch inference at reduced cost, and provisioned throughput when you need reserved capacity.

Reach for it when the governance story is the requirement: an enterprise that already runs on AWS gets model access without a new vendor, a new contract or a new egress path. The price is lag and gaps -- models and features land later than on the vendor's own API, availability varies by region, and some capabilities never appear. Call the provider directly if you need the newest surface.

### Amazon Bedrock Intelligent Prompt Routing
**Short:** Managed Bedrock feature that predicts per-request response quality and routes between two models in one family.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @3

A small routing model reads the incoming prompt and predicts how much worse the cheaper model's answer would be than the stronger one's; if the predicted gap is inside a tolerance you configure, the request goes to the cheap model. Routing happens inside Bedrock, so the client sends one request to a router identifier and never sees which model answered.

Reach for it when traffic is a mix of easy and hard prompts against one model family and you want a cost cut with no code change. The limits are narrow: routing only happens within a family, the quality tolerance is a guess until you measure it on your own traffic, and you give up per-request control of which model ran. A classifier you train yourself gives more control and more work.

### anthropic
**Short:** Official Anthropic Python SDK with sync and async clients for messages, streaming, tools and batching.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/tool-use-and-mcp @3, apis-frameworks/web-framework-and-http-client @3

The package exposes synchronous and asynchronous clients over the Messages endpoint, with a streaming helper that accumulates events into a final message, retries with backoff on rate limits and server errors, a typed exception per status code, a batches interface for large asynchronous jobs at reduced cost, token counting, and a beta namespace that attaches the correct headers for pre-release features.

Reach for it whenever Python calls Claude directly and you want the whole vendor surface -- thinking, tool use, prompt caching, structured output -- without an abstraction filtering it. The tradeoff is coupling: the request shape is Anthropic's, so supporting a second provider means either duplicating call sites or putting a gateway in front.

### Anthropic Claude Opus
**Short:** Anthropic's highest-reasoning hosted Claude tier, typically used as the orchestrator in multi-agent systems.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2

Opus is the reasoning-heavy end of the Claude family, aimed at long-horizon and multi-step work where being wrong is expensive. Reasoning depth is adaptive rather than a fixed token budget, with an effort setting trading depth against cost and latency, and the same request surface as the rest of the family means moving between tiers is a model-string change.

Reach for it as the planner in a system where cheaper models do the fan-out -- the orchestrator that decides what to do, delegating reading, extraction and summarisation to Sonnet or Haiku instances whose output it reads. The common waste is the opposite: routing every request to the top tier, paying several times the per-token price for classification work a small model handles identically.

### Anthropic Claude Sonnet 5
**Short:** Anthropic's hosted general-purpose Claude model, called over the provider API for agentic and long-context work.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2, applied-ml/nlp-and-text @3

Sonnet is the middle tier, close enough to Opus on coding and agentic work to be the sensible default while costing meaningfully less per token. It shares the family's request surface -- adaptive thinking, the full effort ladder, tool use, prompt caching, a very large context window -- so a route can be moved between tiers by changing the model string and re-checking the output.

Reach for it as the workhorse: the model behind the agent loop, the extraction pipeline, the chat product. Two directions to escape it are worth knowing -- the hardest long-horizon reasoning still favours Opus, and high-volume classification or routing is cheaper on Haiku or a fine-tuned small model. Effort level moves cost more than tier choice does on many workloads, so sweep it before upgrading.

### Anthropic Computer Use API
**Short:** Anthropic tool letting a model see a screen and drive mouse and keyboard to operate a real desktop.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @2

It is a client-executed tool: Anthropic defines the schema and the model's action vocabulary, but the environment is yours to supply and the actions are yours to execute, with a reference container image showing one way to do it. Coordinate accuracy depends on the resolution you report and the image you send back, which is also what determines the token cost of every step.

Reach for it when the target is a whole desktop rather than a browser, and treat the harness as the engineering work -- action execution, screenshot cadence, resolution, and a step budget that stops a confused loop. For web tasks a browser tool driven by the accessibility tree is faster, cheaper and far more reliable, because it acts on named elements instead of pixels.

### Anthropic Console
**Short:** Anthropic's web console: a Workbench for iterating on prompts, evaluation test cases, plus API keys, workspaces and usage tracking.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/evaluation-and-benchmarks @2, platform-delivery/cloud-platform-and-cost @3

The Workbench is the part engineers use daily: a system prompt, a message list, a model and sampling parameters, run and inspect, with tool definitions available so a function-calling prompt can be exercised without writing a client. Alongside it sit helpers that draft or rewrite a prompt from a description of the task, and an evaluation surface where a prompt is run against a set of test inputs so two versions can be compared side by side rather than judged from one lucky sample. The account-level half of the console is where API keys are issued, workspaces separate one project's keys and spend limits from another's, and usage and cost are broken down.

Reach for it as the design surface for a prompt before that prompt becomes code, and for the evaluation pass that stops prompt changes being decided by vibes. What it is not is a runtime: the console exports a prompt as SDK code and your repository then owns the copy, so nothing keeps the version you tested and the version you shipped in step. A prompt registry the application pulls at runtime is a different product category, and versioning discipline in your own repository is the alternative.

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

The chat-completions request and response shape became a de facto interface, and vLLM, SGLang, Ollama, llama.cpp's server, most inference vendors and most gateways all implement it. That means changing a base URL and a model name is the whole migration, and existing client libraries, tracing integrations and test fixtures keep working unchanged.

Lean on it when you want the option to move -- during evaluation, or to keep a self-hosted fallback behind the same code. The trap is assuming compatibility is total: only the common subset is portable, and tool calling fidelity, structured output modes, log probabilities, streaming details and usage accounting all differ between implementations. Test the specific features you depend on rather than the endpoint.

### AP2 (Agent Payments Protocol) v0.2
**Short:** Google-led open protocol for agent-initiated payments: Checkout and Payment Mandates over A2A/MCP, rail-agnostic.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

Its central idea is the mandate: a cryptographically signed credential recording what the user actually authorised -- the intent, the specific cart, and the payment -- so that after the fact a merchant, an issuer or a network can prove a human approved this purchase rather than a model inventing it. Being rail-agnostic, the same authorisation structure is meant to sit above cards, bank transfers or token settlement.

It matters because authorisation, not payment mechanics, is the unsolved part of agent commerce -- existing rails assume a human clicked. The cost is that it only works if issuers, networks and merchants implement it, and it competes with other agentic-commerce specifications, so today it is something to track and prototype against rather than build a business on.

### AutoGen
**Short:** Microsoft multi-agent framework where agents solve tasks by structured conversation, with first-class code execution.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @3

Agents solve a task by talking to each other: an assistant agent wraps a model, and a proxy agent can execute the code the assistant writes -- in a container or locally -- and feed stdout and tracebacks back as the next message, which is the loop that made it well known. The rewritten version sits on an asynchronous, event-driven core with typed messages instead of a synchronous chat loop.

Reach for it when a task genuinely benefits from a write-run-fix cycle or from distinct specialists debating. The cost grows quadratically: every agent reads every message, so a four-agent conversation burns tokens fast and is hard to debug. Microsoft now points new .NET and Python work at its unified Agent Framework, which absorbs this project.

### autogen-agentchat
**Short:** AutoGen's agent and team layer: AssistantAgent plus round-robin, selector, swarm and graph team topologies.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

It is the high-level layer over AutoGen's event-driven core runtime. An assistant agent wraps a model client plus a set of tools; a team decides who speaks next, and the shipped team types cover the useful topologies: round robin for a fixed rotation, a selector that asks a model to pick the next speaker, swarm for explicit handoffs between agents, and the Magentic-One team driven by an orchestrator that plans and tracks progress. Termination conditions, not a loop you write, decide when the conversation stops.

Reach for it when the problem genuinely decomposes into specialists that need to talk to each other, and when you want streaming and async execution out of the box. A single agent with a good tool set is cheaper, faster and much easier to debug, so make the multi-agent structure earn its place.

### autogen-core
**Short:** AutoGen's low-level runtime: typed messages, RoutedAgent and the event loop multi-agent topologies are built on.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

It is an actor runtime rather than a chat framework: agents are addressed by identity, messages are typed, and handlers are selected by message type; a runtime delivers them, either in one process or across processes over gRPC. Nothing about conversation, teams or models is baked in -- those are conventions the layer above adds on top of message passing.

Reach for it when you are building your own orchestration semantics, or when agents must run in separate processes or languages and you want a supported transport. For almost every application the higher-level agent and team layer is the right entry point; dropping to the core means writing the coordination logic that layer already provides.

### autogen-ext
**Short:** Extension package for AutoGen supplying model clients, tool adapters and code executors for its agent runtime.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

The core runtime deliberately ships no integrations, so everything concrete lives here: model clients for the major providers and for any OpenAI-compatible endpoint, code executors backed by Docker, a local subprocess or a hosted container service, adapters that import tools from other ecosystems including MCP, and prebuilt web and file agents. It installs through extras, so you pull only the pieces you use.

You will need it for any real AutoGen application -- an agent without a model client does nothing. The cost is dependency management: extras have their own version constraints, and a mismatch between the core package and this one produces import errors rather than a clear message, so pin both together.

### autogen-ext[magentic-one]
**Short:** AutoGen extra shipping the Magentic-One reference multi-agent team (orchestrator plus web/file/coder agents).
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @3

The team is an orchestrator plus four specialists: a browser agent, a file agent, a coder and a terminal that executes what the coder writes. The orchestrator keeps two ledgers -- one of facts, guesses and a plan, another tracking progress -- and re-checks the progress ledger every turn, replanning when it detects the team is stuck or looping, which is the mechanism that distinguishes it from a round-robin team.

Reach for it as a generalist agent baseline you can read and benchmark against, particularly for tasks that mix browsing, files and code. It is slow and expensive per task, needs a browser and a code-execution sandbox provisioned, and the open-ended action space means failures are hard to attribute. A purpose-built single agent beats it on any task you can specify.

### autogen-ext[openai]
**Short:** AutoGen extension package providing the OpenAI model client; installed separately from the autogen core.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @3

This extra supplies the model client that binds an agent to OpenAI or Azure OpenAI, and it also carries the capability declaration used when you point it elsewhere: because the client accepts a base URL, you can target Ollama, vLLM or another compatible server, but you must tell it whether that model supports function calling, JSON output and vision, since the framework cannot probe for it.

You need it, or a sibling extra, before any agent can call a model. The capability declaration is the part that bites -- getting it wrong produces confusing failures deep in a team run rather than a clear error at configuration time, so set it from the target model's actual behaviour rather than copying an example.

### awesome-mcp-servers
**Short:** Community-curated catalogue of Model Context Protocol servers, used to find an existing server before writing one.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

It is a curated README: categorised links to servers for databases, cloud providers, browser automation, developer tools and consumer apps, usually with a marker distinguishing official from community implementations. Discovery is the entire function -- you read an entry, follow the link, and install the server yourself by whatever means its author documented.

Use it to check whether a server already exists before writing one, which is often the case. Treat it as a bibliography rather than a registry: there is no namespace verification, no install tooling and no freshness guarantee, and installing a server means running someone else's code alongside your agent with whatever credentials you hand it. The official registry adds provenance that a list cannot.

### BabyAGI
**Short:** Early autonomous-agent script keeping a task queue: execute the next task with an LLM, then re-plan.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, data-movement/task-queue-and-jobs @3

Three prompts in a loop: an execution step runs the top task with the objective as context, a creation step invents follow-up tasks from that result, and a prioritisation step reorders the queue. Results were embedded into a vector store so later tasks could retrieve earlier ones. That is the whole system -- there is no tool layer, no verification, and no explicit stopping condition.

Its value now is historical: it was the first widely copied demonstration that a model plus a task queue produces apparently autonomous behaviour, and the first widely felt demonstration of why that is not enough. Task creation keeps inventing work, so it rarely converges and burns tokens indefinitely. Anything built today wants explicit termination conditions and a real success signal.

### BAML
**Short:** Typed prompting language with a compiler: a prompt declares its output schema and generates typed client functions.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, devtools/compiler-toolchain-and-codegen @2

You declare a function in a dedicated file -- its input types, its output type, and the prompt template -- and a compiler generates typed client functions for your language. The part that earns its place is the parser: rather than requiring exact JSON, it coerces near-miss output such as fenced code blocks, trailing commas or unquoted keys into the declared type, so ordinary model sloppiness stops being a runtime failure.

Reach for it when extraction reliability matters and you want prompts out of string concatenation and into something with types, a diff and an editor preview. The cost is adopting a new language and a code-generation step into the build, which is a lot of process for one prompt -- a validation library over the provider SDK is the lighter option until schemas multiply.

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

It provides the usual primitives -- agents with tools, memory, and structured workflows -- in both Python and TypeScript, but its distinguishing bet is interoperability: agents are exposed and consumed over agent-to-agent protocols rather than through framework-specific calls, so a team can build in one framework and still be callable from another.

Reach for it when the requirement is cross-framework or cross-team agent composition and you want that to be protocol-level rather than a shared library. Against the mainstream Python frameworks it is a smaller ecosystem with fewer integrations and examples, so the interoperability has to be the reason you pick it -- as a single-team agent library there is less here than in the alternatives.

### Brave Search API
**Short:** Web search API over an independent index, used as an agent tool and grounding source for RAG.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @2

Queries hit Brave's own crawled and ranked index rather than a reseller of another engine's results, which is the practical reason to choose it: independence from the incumbents, and terms that permit programmatic use. Responses are JSON -- titles, URLs, descriptions, plus knowledge and news blocks -- with separate endpoints for web, news and images and tiered plans including a free level with attribution requirements.

Reach for it as a grounding source when cost and licensing matter more than absolute coverage. Two limits follow: an independent index has weaker long-tail recall than Google's, and what you get back is links and short snippets, so an agent still needs a fetch-and-parse step before it can answer. Agent-oriented search APIs return cleaned page content instead, at higher cost per call.

### Browser MCP
**Short:** MCP server exposing a real browser to an agent as tools - navigate, read the page, click and type.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @1

It pairs a local MCP server with a browser extension that drives your existing browser profile, so the agent inherits the sessions you are already logged into instead of starting from a clean automated browser that every site treats as a bot. Actions are taken against the accessibility tree -- click element, type into field -- rather than screen coordinates, which keeps steps cheap and deterministic.

Reach for it when the task needs authenticated access to sites where scripted login is impractical. That convenience is also the danger: the agent acts as you, with your cookies, in your real browser, so a prompt injection on any visited page can act on your accounts. A separate profile, or Playwright with a disposable context, contains the blast radius.

### browser-use
**Short:** Python library that drives Chromium via Playwright and the accessibility tree so an LLM can browse the web.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @3

It wraps Playwright and hands the model a compressed, indexed view of the page built from the DOM and accessibility tree, with interactive elements numbered so the agent can say "click element 12" instead of asking a vision model to find coordinates in a screenshot. That indexing is what makes the loop reliable and cheap enough to run for many steps, with screenshots as a fallback for pages whose structure is not enough. Reach for it when a task genuinely has no API behind it: authenticated portals, legacy internal tools, multi-step forms. Treat it as a live-web capability with real risk, because page content becomes model input, so prompt injection from a visited site is the failure mode to design around, and it should run sandboxed with narrowly scoped credentials.

### Browserbase
**Short:** Hosted headless-browser infrastructure giving agents fresh, scalable Chrome sessions with proxies and recording.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, platform-delivery/cloud-platform-and-cost @3

It runs the Chrome instances so you do not have to: your existing Playwright or Puppeteer code connects to a hosted session over the DevTools protocol instead of launching a local browser, and the platform adds proxies, session recording, a live view you can watch or take over, fingerprint handling, and persistent contexts that carry logins between sessions.

Reach for it when browser automation needs to scale or must not run on developer laptops, and when you would otherwise be maintaining a headless browser pool. The costs are per-session billing that adds up under an agent that retries, and a third party that sees every page the agent visits -- including whatever it types into a login form.

### CapSolver
**Short:** Commercial captcha-solving API used by browser automation and web agents to get past challenge pages.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

You submit the challenge parameters -- site key, page URL, and usually the proxy the page was loaded through -- then poll for a token and inject it into the page before submitting. Solving is presented as machine-driven rather than routed to human workers, which is what keeps per-solve latency and price lower than the human-farm services.

The engineering caveat is that the token must be produced under conditions consistent with the session that will use it, which is why proxy and fingerprint matching matter more than the API call itself. The larger caveats are unchanged: it exists to defeat a control the site owner installed, detection evolves so working recipes break, and an official API or a data agreement is the only stable path.

### Carnegie Learning MATHia
**Short:** Commercial adaptive math tutoring software that models a student's skills and personalises problems step by step.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @3

It descends from Carnegie Mellon's Cognitive Tutor research, and the mechanism is a student model rather than a language model: the curriculum is decomposed into fine-grained skills, the system watches each step of a worked problem, updates a probability that the student has mastered each skill, and selects the next problem to target the weak ones. Hints are given at the step where the student stalled, not on the final answer.

Its strength is that mastery estimates are grounded in observed steps against expert-authored content, so the guidance is reliably correct. Its cost is exactly that authoring: every skill, problem type and hint is written by domain experts, which is enormous upfront work and confines the system to the curriculum somebody built. That is the trade against an LLM tutor, which generalises anywhere and guarantees nothing.

### ChatDev
**Short:** Research multi-agent framework simulating a software company, with role-playing agents in a waterfall pipeline.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

It simulates a software company: agents take roles such as chief executive, designer, programmer, reviewer and tester, and the run proceeds through waterfall phases -- design, coding, testing, documentation. Each phase is a structured two-agent dialogue with a defined termination condition rather than an open chat, which is what keeps the role play from wandering indefinitely.

Read it as a research artefact about multi-agent orchestration, not as a tool. It produces small self-contained programs from a one-line brief, spends many model calls on role play to get there, and has no story for working inside an existing codebase. A single coding agent with repository access does more useful work for less.

### ChatGPT
**Short:** OpenAI's chat product and API surface, including built-in connections to remote MCP servers and tools.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/prompting-context-and-structured-output @3

It is the product surface rather than the API: a model wrapped in a system prompt you do not control, plus memory, file upload, code execution, image generation, web browsing, custom instructions, and connections to remote tool servers. From an integration standpoint that makes it a client of your tools rather than a library you call -- you expose capabilities to it, you do not orchestrate it.

Reach for it when the user is the one driving and you want the fastest path to a working assistant over your data. The cost is that nothing about the loop is reproducible: the surrounding prompt, the context assembly and the model routing change under you, so behaviour drifts without a release on your side. Build against the API when you need control or evaluation.

### Claude
**Short:** Anthropic's hosted frontier model family, including adaptive and extended thinking modes billed as output tokens.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/llm-gateway-and-routing @3

The family is tiered -- a top reasoning tier, a balanced mid tier and a small fast tier -- all behind one request shape, so moving between them is a model-string change plus re-testing. Common to all of them: content is a list of typed blocks rather than a string, tools are JSON-schema definitions the model fills, thinking is billed as output tokens, and caching a long stable prefix cuts its cost dramatically on repeat calls.

Pick the tier per route rather than per application: the top tier for planning and hard reasoning, the mid tier as the default workhorse, the small tier for classification and bulk transformation. The expensive mistake is sending everything to the largest model, and the second most expensive is not caching a system prompt that is identical on every request.

### Claude Code
**Short:** Anthropic's terminal coding agent: autonomous file editing, subagents, prompt caching and MCP tool access.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/tool-use-and-mcp @2

It runs in a terminal against a real repository — reading files, editing them, running builds and tests, and iterating on the output rather than emitting a patch blind. Project conventions come from a `CLAUDE.md` it reads each session, extra capabilities from MCP servers, and long or parallelizable work can be delegated to subagents that return a summary instead of filling the main context.

The skill in using it is scoping: it is strongest when done is checkable — a failing test, a lint error, a migration to apply — and weakest when the goal is vague. Every edit lands in your working tree, so a clean git state before a large change is the real undo button.

### Claude Code Agent tool
**Short:** Claude Code's built-in tool for spawning isolated subagents with their own context to run a delegated task.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, llm-apps/agentic-environments @3

It spawns a subagent with a fresh context window, its own tool set and often a cheaper model; the parent sends a prompt, the subagent runs its own loop to completion, and only its final report comes back. That is the point -- a search that reads fifty files costs the parent a paragraph instead of fifty file bodies -- and several can run concurrently when the work fans out.

Reach for it for wide, independent investigation and for long jobs whose intermediate output the parent does not need. The costs are real and often underestimated: the subagent cannot ask a clarifying question and does not share the parent's context, so an underspecified prompt produces confident nonsense, and each one re-establishes context from scratch. Anything you could finish in a few tool calls is cheaper done directly.

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

It works in explicit plan and act modes, and every file edit and terminal command is surfaced as a diff or a command for approval before it runs, with auto-approval opt-in per action type. You supply your own API key, so any provider works including a local model, and it consumes MCP servers for extra capability.

Reach for it when you want a capable agent inside your editor and would rather pay per token than per seat, or when policy requires seeing every change before it lands. The costs follow from the design: approval per step is safe and slow, and because it re-reads context aggressively, token spend is high and visible. Subscription products bundle inference and hide both.

### Codeium
**Short:** AI code completion and chat assistant embedded in the IDE, free for individual use.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

It made its name by offering in-editor completion and chat free for individuals, running its own models rather than reselling a frontier API, which is also what let it offer a self-hosted deployment for teams that could not send code to a vendor. Editor plugins covered a wide range of IDEs, with repository context feeding completions beyond the current file.

The product line was subsequently rebranded to Windsurf, whose editor is its successor, so material under the old name may not match what ships today -- check the current product before relying on a feature. As a category, a free completion plugin is the low-commitment entry point; a full agentic editor is the tier above, and self-hosting is the reason to choose this branch of the family.

### Codestral
**Short:** Mistral's open-weight code model with a 32K context, tuned for fast completion and fill-in-the-middle.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, applied-ml/nlp-and-text @3

It is trained specifically for code across many languages, and its distinguishing capability is fill-in-the-middle: a dedicated completion endpoint takes a prefix and a suffix and generates the span between them, which is what inline IDE completion actually needs and what plain left-to-right generation does poorly. Weights are published under Mistral's non-production licence, so you can download and evaluate them but commercial use needs a separate agreement.

Reach for it when you want a self-hostable completion model rather than a hosted assistant. Two things to check on the specific release you pull: the licence, which is the usual blocker for shipping it in a product, and the context length, which has changed across releases. For chat and multi-file reasoning a general frontier model still does better.

### Computer use docker reference
**Short:** Anthropic's quickstart container image providing a sandboxed desktop (VNC, browser, shell) for the computer-use tool.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, platform-delivery/container-and-image @3

The image bundles everything the loop needs on one machine: a minimal Linux desktop with a window manager, a browser, a tool that synthesises mouse and keyboard events, a VNC server with a web viewer so you can watch and take over, and a small app running the model loop. It exists so you can see the screenshot-act-screenshot cycle working before building your own harness.

Use it to learn the action-execution layer and to reproduce behaviour when debugging. Do not ship it: it is a single container with no hardening beyond the container boundary, no session isolation, no persistence and no scaling story, and a computer-use agent is precisely the workload where a container is the wrong boundary. Production wants a disposable virtual machine per session with controlled egress.

### Continue
**Short:** Open-source, model-agnostic coding agent extension for VS Code and JetBrains.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

Configuration is the product: a single file declares a model per role -- chat, inline completion, embeddings, reranking -- so you can pair a hosted frontier model for conversation with a small local model for tab completion. Context providers pull in files, the indexed codebase, terminal output or documentation on demand, and rules and prompt templates live in the repository beside the code.

Reach for it when you want to choose your own models, or mix hosted and local, rather than accept a vendor's bundle. The cost is setup and ownership: you pick the models, hold the keys, and inherit whatever completion quality your choice delivers, which is usually below a tuned commercial product until you have spent time on it.

### Continue.dev
**Short:** Open-source IDE coding-assistant plugin that can point at self-hosted or local models for privacy-sensitive codebases.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

Because every role in its configuration is just a model endpoint, pointing the chat and completion models at a local runtime or an internal inference server keeps source code inside the network -- the extension itself carries no vendor dependency. Teams share a single configuration so everyone gets the same models, context providers and rules without each developer wiring it up.

Reach for it when code cannot leave the perimeter and a hosted assistant is therefore off the table. Two costs: local completion models are noticeably weaker than hosted frontier ones, so expectations set by a commercial product will not be met, and you now own GPU capacity, model updates and the latency budget that inline completion is unforgiving about.

### Copilot coding agent
**Short:** GitHub's PR-native coding agent that takes an assigned issue and opens a pull request with the change.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2

You assign an issue to it and the work happens on GitHub's infrastructure: an ephemeral environment defined by an Actions workflow clones the repository, the agent explores, makes commits on a branch, and opens a draft pull request. Progress is visible in the pull request timeline, and you steer by leaving review comments, which it answers with further commits.

Reach for it for well-scoped, test-covered issues where the review surface -- a pull request -- is already the team's workflow, since nothing leaves that surface. The limits are the environment, which is only as capable as the workflow you configured, and the task shape: it does mechanical and localised changes well and architecture badly. It requires a paid plan and repository configuration.

### Copy.ai
**Short:** SaaS product generating marketing and sales copy at scale from LLM workflows and brand-tuned templates.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1

Beyond templates, the substance is a workflow builder: steps chain together to fetch a page, enrich a record from a CRM, generate copy against a brand voice and knowledge base, and write the result back, so the output lands in the systems a go-to-market team already uses rather than in a chat window. The product has shifted from copywriting toward that broader sales and marketing automation.

Reach for it when non-engineers need to run repeatable generation at volume without a developer in the loop. The cost is opacity and lock-in: you cannot inspect or version the prompts, the workflows do not export, and per-seat or per-credit pricing scales with usage -- so teams past the prototype stage usually rebuild the same pipeline on a provider API.

### CrewAI
**Short:** Role-based multi-agent Python framework: define agents with roles, goals and tools, then run them as a crew.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

You declare agents with a role, a goal, a backstory and tools, tasks with a description and an expected output, and a crew that runs them either in sequence or hierarchically with a manager agent delegating to the others. Those descriptive fields are not decoration, they are the prompt scaffolding, so the quality of a crew depends heavily on how sharply the roles and expected outputs are written.

Reach for it when you want a multi-agent prototype running quickly and the workflow is close to a linear handoff between specialists. You get less control over the loop than a graph-based framework gives you, which shows up as soon as you need conditional branching, a retry on a specific step, or a human approval gate in the middle.

### crewai-tools
**Short:** Prebuilt tool library for CrewAI agents: web search, site scraping, file reading and retrieval-style tools.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2

It is a library of ready-made tool classes -- web search, page scraping, file and document readers for PDF, CSV and JSON, database and vector-store queries, a code interpreter, browser automation -- plus a base class and a decorator for writing your own, and adapters that import tools from other ecosystems so an existing catalogue is reusable.

Reach for it to avoid writing the tenth scraping tool, and treat the catalogue as a starting point rather than a dependency you adopt wholesale. Each tool brings its own transitive dependencies and usually its own API key, so install narrow extras, and read the implementation of anything you hand credentials to -- a tool is arbitrary code running with your agent's permissions.

### CRITIC
**Short:** Self-correction technique where the model verifies and revises its output using external tools, not self-critique.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2, ml-lifecycle/evaluation-and-benchmarks @3

The loop is draft, verify, revise -- but verification is external. Instead of asking the model to grade itself, the technique has it call tools that supply ground truth about specific claims: run the code, search for the fact, evaluate the expression, hit the API. The evidence returned is what drives the revision, and the cycle repeats while the evidence keeps contradicting the draft.

Its premise is that self-critique adds little because the critic shares the generator's blind spots, while a tool contributes information the model did not have. That bounds where it works: use it wherever a cheap external check exists -- executable code, checkable facts, validatable schemas -- and skip it for judgment, style or taste, where each round is extra latency and tokens for no signal.

### Cursor IDE
**Short:** AI-native code editor with inline edit, composer and background agents, and a built-in MCP client.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, devtools/version-control-and-workbench @2, llm-apps/tool-use-and-mcp @3

It is a VS Code fork, so extensions, keybindings and settings mostly carry over, and the additions are the point: multi-line predictive completion, inline edit on a selection, and a chat/agent surface that plans and applies changes across several files at once. It indexes the repository so the model retrieves related files rather than seeing only the buffer you have open, which is what makes whole-codebase questions and refactors work.

Being an MCP client means you can attach your own servers - an issue tracker, a database, internal docs - and have the agent call them as tools. Reach for it when the work is editing an existing codebase in place; a chat window is a poor fit for changes that span files, and a terminal agent is a better fit when the loop is build-test-fix rather than reading code.

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

DistilBERT is a distilled six-layer BERT, roughly 40 percent smaller and much faster than the original, which makes it cheap enough to run on CPU in front of every request. Fine-tuned as a classifier over prompts, it becomes a router: labels come from logging real traffic and recording whether the cheap model's answer was acceptable, and the trained head then predicts that for new prompts in tens of milliseconds.

Reach for it when volume makes routing worth real engineering and you want a decision that is deterministic, inspectable and free of an extra model call. The costs are the labels, which nobody has until they build the logging, and drift -- the router is calibrated against a specific pair of models and a specific traffic mix, and silently degrades when either changes.

### Daytona
**Short:** Managed sandbox runtime giving an agent a disposable full Linux box to run untrusted code in.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, platform-delivery/container-and-image @3

The SDK creates a sandbox in around a second, then you execute commands, run code, read and write files, expose preview URLs and destroy it. Snapshots and prebuilt images let you bake dependencies in so a sandbox starts warm rather than pip-installing on every run, which is usually what dominates latency in agent code execution.

Reach for it when an agent writes code that must actually run and you do not want to operate the compute. The costs are a hosted dependency billed by runtime and an isolation guarantee that is only as good as the network and filesystem policy you configure. If the code is yours and known, a plain container or a subprocess is cheaper; if it is genuinely hostile, check the isolation boundary carefully.

### Devin
**Short:** Commercial autonomous software-engineering agent that runs long sessions in its own VM with a plan UI.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

It takes a task rather than a keystroke: given an issue, it plans, then works inside its own sandboxed VM with a shell, an editor, and a browser, running tests and iterating over a session that can last hours before opening a pull request. The plan is visible and steerable, so you can correct a wrong assumption partway rather than only judging the final diff.

Reach for it when the work is well-scoped and machine-verifiable — a failing test to fix, a mechanical migration across many files, a dependency upgrade — because a test suite is what makes autonomous iteration converge. It is a commercial product billed by consumption rather than per seat, so a long autonomous session has a real cost, and reviewing what it produces is still your job.

### Docker sandboxes
**Short:** Containers used as disposable isolated environments for executing untrusted model-generated code.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @2, platform-delivery/container-and-image @3

The recipe matters more than the idea: run the container with no network, a read-only root filesystem, a non-root user, dropped capabilities, memory and CPU limits and a hard timeout, mounting only an input directory and collecting output from a defined path. Then destroy it. Anything less -- a mounted Docker socket, a host volume, default capabilities -- gives back most of what the container was supposed to contain.

Reach for it when the code is semi-trusted: your own generated snippets, a dependency you mostly believe. The limit is architectural, not configurational -- containers share the host kernel, so a kernel escape is a host compromise, and for code an unattended model wrote from an untrusted prompt a microVM or a userspace kernel is the boundary that actually holds.

### DSPy
**Short:** Framework that treats prompts as programs and auto-optimizes them against a metric instead of hand-tuning strings.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3, ml-lifecycle/evaluation-and-benchmarks @3

You declare a signature such as `question -> answer` and compose modules like `ChainOfThought` or `ReAct`; the prompt text is generated rather than written, and an optimizer then searches over few-shot demonstrations and instruction wordings, scoring candidates with your own metric on your own examples. The problem it attacks is that hand-tuned prompt strings are brittle and get re-tuned every time the model changes, whereas a compiled program is simply recompiled against the new model. It fits pipelines where labelled examples and a real metric exist, such as a RAG chain whose retrieval and answer steps can both be scored. With no metric and no examples there is nothing to optimize, and a plain prompt is the honest choice.

### dspy-ai
**Short:** DSPy framework that declares LLM programs as typed modules and compiles prompts and few-shot demos automatically.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

The unit is a module with a typed signature -- inputs and outputs named and described -- composed the way you would compose functions, with prebuilt modules for chain-of-thought and tool-using loops. Compilation is a real step: an optimiser runs your program over labelled examples, scores candidates with your metric, and searches over demonstrations and instruction wordings, producing a compiled artefact you save and load rather than a prompt you paste.

Reach for it when a pipeline has a metric and examples and you expect to change models, since recompiling is cheaper than re-tuning strings by hand. Budget for compilation itself: an optimiser run makes many model calls before it produces anything, and the compiled artefact must be versioned and re-run when the pipeline changes. Without a metric there is nothing to optimise.

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

Two features show the pattern. A roleplay mode gives the learner a scenario and a conversational partner in the target language, grading the exchange afterwards, and an explanation feature turns a specific wrong answer into a tailored account of why it was wrong -- something a hand-authored hint cannot do because it would need one hint per possible mistake. Both sit on top of the existing spaced-repetition course rather than replacing it.

The instructive part for a system designer is the split: the curriculum, the progression and the correctness checks stay deterministic, and the model is used where variety and personalisation are the point. The cost is per-learner-minute inference, which is why these features sit behind a paid tier rather than being switched on for everyone.

### E2B
**Short:** Cloud Firecracker microVM sandboxes for running agent-generated code safely, with sub-second start times.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @3

The SDK starts a sandbox in about a second, then you run code or shell commands inside it, read files back out, and destroy it. The isolation boundary is a Firecracker microVM rather than a container, which is what makes it defensible to execute code an unattended model just wrote.

Its two common jobs are the code-execution tool behind an agent and the runner for code-based rewards during RL training. Reach for it whenever the code is untrusted or the agent can install packages; if the code is yours and known, a subprocess or a plain container is cheaper.

### Epic
**Short:** Electronic health record platform; the system of record LLM clinical-documentation features integrate into.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

It is the dominant electronic health record in large US health systems, and clinical AI features reach it as registered integrations rather than standalone products: patient data is read through standards-based interfaces, and generated content -- a draft note, a summary, a message reply -- is written back into the chart where the clinician reviews and signs it. The chart, not the model, remains the system of record.

The engineering is rarely the hard part. Each health system is a separate procurement with security review, a data agreement covering protected health information, and validation before clinicians touch it, so integration timelines are measured in quarters. And the value case is clinician minutes saved, which evaporates if the draft needs heavy editing.

### FastMCP
**Short:** Decorator-based Python framework for building MCP servers: expose tools, resources and prompts with type hints.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/tool-use-and-mcp @1

Decorate a function and the framework derives the tool's JSON schema from its type hints and its docstring, so the schema cannot drift from the implementation. The same treatment applies to resources, addressed by a URI template whose placeholders become parameters, and to prompt templates. Running the server picks a transport -- standard input and output when it is spawned as a local child process, HTTP when it is remote.

Reach for it for any Python MCP server; the low-level SDK interface is worth dropping to only for unusual protocol work. One wrinkle to watch: an earlier version of this project was absorbed into the official Python SDK while development continued separately, so examples on the internet target different packages with slightly different imports. Check which one you installed.

### Gemini 3.x
**Short:** Google's frontier multimodal reasoning model family, with reasoning depth set by the thinking_level enum.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/prompting-context-and-structured-output @3

It is natively multimodal rather than multimodal by adapter: text, images, audio, video and PDFs arrive as parts of the same request and are processed by the same model, which is why video question-answering works without a separate transcription pipeline. Reasoning depth is set by a discrete level rather than a token budget, and the same models are reachable through the Gemini API and through Vertex AI.

Reach for it when the input is genuinely mixed media or very long, which is where it is hardest to replace. The costs to plan for: multimodal input inflates token counts quickly, so a few minutes of video is not a cheap prompt, and safety behaviour, grounding and tool-calling details differ enough from other providers that a prompt tuned elsewhere needs retesting rather than porting.

### Gemini API
**Short:** Google's hosted Gemini endpoint, notable for a million-token context window and native multimodal input.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/tool-use-and-mcp @3

A request is a list of content parts, so text, inline media and references to previously uploaded files sit side by side in one call, with a separate files service for anything large. The very large context window changes an architectural decision: for a corpus that fits, putting the whole thing in the prompt is a real alternative to building a retrieval pipeline, and grounding against web search is available as a server-side tool.

Reach for it when multimodal input or long-document work is central. The tradeoff on long context is that price and latency scale with input, and recall across a very long prompt is not uniform -- material in the middle is attended to less reliably than material at either end -- so measure retrieval accuracy on your own documents rather than assuming a million tokens is free memory.

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

It began as inline completion trained on public code and is now a suite: ghost-text suggestions, chat with repository and file context, multi-file edits, code review, and an agent that opens pull requests -- available across the major IDEs and the GitHub web interface, with a model picker so the underlying model is not fixed. Distribution through the editor is its real advantage.

Reach for it when predictable per-seat pricing and enterprise administration matter more than control over the loop. That is also the limit: you cannot see or shape how context is assembled, so behaviour is what the vendor ships. The settings that matter before a rollout are the public-code filter, indexing scope and telemetry, not the model choice.

### GitHub modelcontextprotocol/servers
**Short:** The official repository of reference MCP servers - filesystem, git, fetch and more - to copy or run as-is.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

The repository holds reference server implementations in TypeScript and Python, each a self-contained directory with its configuration snippet, plus a large community index in the README pointing at third-party servers. Because the implementations are deliberately small, reading one end to end is the fastest way to understand the protocol's shape: register handlers, declare capabilities, pick a transport.

Reach for it to copy a server as a starting point or to check whether one already exists. Treat what you find as reference quality: guardrails are minimal -- the filesystem server's directory allowlist is about the extent of it -- maintenance is uneven, and several early servers were retired as vendors published their own. Verify what a server does before it runs beside your agent.

### Gorilla LLM
**Short:** LLaMA fine-tuned to emit correct API calls against large, changing catalogues by retrieving the documentation first.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @3

Its contribution is retriever-aware training: the model is fine-tuned with retrieved API documentation in the prompt, so it learns to depend on that text rather than on memorised signatures. That is what lets it stay correct when an API changes - update the retrieved docs and behaviour follows, instead of requiring another fine-tune - and it measurably reduces invented endpoints and arguments.

Reach for it when the tool catalogue is large, versioned and outside your control. The pattern matters more than the checkpoint now that hosted models ship native function calling: retrieve the schema, then generate against it, rather than trusting anything the model remembers.

### Guidance
**Short:** Microsoft library for prompt templating with constrained generation and token healing into a fixed structure.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1

A Guidance program interleaves fixed text you supply with slots the model fills, and the constraints on those slots — a regex, a grammar, a choice among options — are enforced during decoding by masking tokens that cannot continue a valid string. Structure is therefore guaranteed rather than requested, and the parts of the output you already know are not paid for as generated tokens. Token healing fixes the boundary artifact where a prompt ends mid-token and the model is pushed toward an unnatural continuation.

Reach for it when you control the model and need output that parses every time — extraction into a fixed schema, forced multiple-choice, a numbered plan. With a hosted API you cannot mask logits, so the equivalent lever there is the provider's own structured-output or JSON-schema mode.

### haystack-experimental
**Short:** Haystack's pre-release channel where new agent and RAG components ship before stabilising into core.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @2

It is a deliberately unstable channel: new components -- agents, tool calling, evaluators, newer retrieval strategies -- ship here first under an explicit no-stability policy, installed alongside the main package and imported from a separate namespace. Components that prove themselves graduate into core, at which point the import path changes.

Reach for it when you need a capability that has not landed in core yet and you can absorb churn. The cost is that ordinary version bumps can rename or remove a component, so pin the exact version, isolate the import behind your own wrapper, and expect a migration when something graduates. Anything long-lived should sit on core.

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

It patches a provider's client so a call takes a response model, converts that Pydantic model into a schema the model is asked to fill, validates the reply against it, and on a validation error feeds the error message back to the model and retries up to a limit. What you get back is a typed object, so downstream code accesses attributes instead of digging through a dictionary and hoping the key exists.

The same call shape works across the major providers and local runtimes, which is why it is a common choice for extraction pipelines. Where a provider supports strict or constrained decoding, that guarantees the JSON is syntactically valid and matches the schema; this library still earns its place on top by handling semantic validators, field constraints and the retry loop when the model returns well-formed but wrong output.

### instructor-haystack
**Short:** Haystack integration for Instructor, forcing generator output to validate against a Pydantic response model.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3

It slots the validate-and-retry pattern into a pipeline component: the generator is given a Pydantic model as its response schema, the reply is validated against it, and a validation error is fed back to the model for a bounded number of retries before the step fails. Downstream components then receive a typed object rather than a string somebody has to parse.

Reach for it when a pipeline's output feeds code rather than a human and you want the failure to surface at the boundary. Budget for the retries: one pipeline step can become three model calls under a schema the model struggles with, which is invisible in the pipeline diagram. Where the model supports native constrained decoding, use that first and keep this for semantic validators it cannot express.

### Intercom Fin
**Short:** Intercom's customer-support AI agent that resolves inquiries autonomously from a company's help content.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @3

It answers from the customer's own help centre, past conversations and connected documentation, cites what it used, and hands off to a human when confidence is low or policy demands it. Beyond answering it can take configured actions -- look up an order, process a refund -- and it is priced per resolved conversation rather than per seat, which aligns the vendor with deflection rate.

Reach for it when a support team already has decent documentation and wants deflection without building retrieval themselves. Two consequences: answer quality is a direct function of the help content, so the real work is writing and pruning articles, and per-resolution pricing means a traffic spike is a cost spike. Anything requiring account-specific reasoning needs the action integrations, which is where the effort lands.

### Jamba
**Short:** AI21's hybrid Mamba-plus-Transformer LLM, whose state-space layers make very long contexts cheaper to serve.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, applied-ml/nlp-and-text @3

It interleaves Mamba state-space layers with a minority of attention layers and adds mixture-of-experts on top, so most of the sequence is processed by recurrent layers with a fixed-size state while the occasional attention layer preserves the exact in-context recall that pure state-space models lose. The practical payoff is a much smaller key-value cache, which is what makes very long contexts cheap to serve.

Reach for it when long-context serving cost is the binding constraint and you can host open weights. The costs are ecosystem-shaped: hybrid architectures are less well supported by inference engines, quantisation tooling and fine-tuning libraries than plain transformers, so the operational path is rockier, and on short prompts quality per parameter still trails the best conventional models.

### JSON-RPC log inspection
**Short:** MCP debugging technique: turn on SDK debug logging to dump every JSON-RPC request and response to stderr.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, observability/logging @2

Over the standard-input transport the protocol owns stdout, which leaves stderr free for diagnostics -- so turning on the SDK's debug logging dumps the whole exchange without corrupting anything: the initialize handshake, the negotiated capabilities, the tool listing, and each call with its arguments and result or error. Most failures are visible there in one pass.

Reach for it when a tool is not being offered, not being called, or failing opaquely, and use it to catch the one bug nothing else finds: a server that prints to stdout injects garbage into the protocol stream and breaks the session in a way that looks like a client bug. The Inspector shows the same traffic with a UI, which is easier for interactive work.

### Khan Academy Khanmigo
**Short:** Khan Academy's LLM tutor product: Socratic step-by-step coaching in math, science and coding rather than answers.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1

The system prompt is the product: the tutor is constrained not to give answers, instead asking what the student tried and working forward step by step, and it is grounded in Khan's existing exercises and videos rather than free-form. There is a teacher-facing side for planning, and conversations are logged and visible to teachers and parents, which is how a chatbot gets deployed to minors at all.

The instructive detail is what it does not trust the model with: arithmetic and grading were early weak points, so the model works against structured exercise content that already knows the right answer. That is the general pattern for education products -- the model supplies explanation and patience, a deterministic system supplies correctness, and per-student inference cost is why it is a paid tier.

### LangChain
**Short:** General LLM app framework: LCEL pipelines, prompt templates, retrievers and create_agent tool-calling loops.
**Kind:** tech
**Lang:** python, js
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @1, llm-apps/prompting-context-and-structured-output @2, llm-apps/tool-use-and-mcp @3

It supplies the glue around a model call: prompt templates, output parsers, document loaders and splitters, retrievers, memory, and a large catalogue of provider and vector-store integrations. LCEL composes those pieces with the `|` operator into runnables that stream, batch and run branches in parallel for free, and `create_agent` wraps the tool-calling loop.

Reach for it to get a RAG or agent pipeline standing quickly and to swap providers without rewriting. The abstractions also hide the prompt and the actual request, which makes debugging and cost accounting harder -- pin the version strictly, since the surface moves, and expect that a settled pipeline is often clearer rewritten against the provider SDK.

### LangChain AgentExecutor
**Short:** LangChain's original agent runtime: it loops an agent's tool choice, runs the tool, appends the observation, and stops on a finish or a limit.
**Kind:** api
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

You construct an agent that returns either an action -- a tool name and its arguments -- or a finish, and hand it plus the tool list to the executor, which runs the loop: invoke the agent, execute the chosen tool, append the result to the accumulated intermediate steps, and pass the whole thing back in on the next turn. The guards are constructor arguments: a maximum iteration count and a wall-clock budget so a confused agent cannot spin forever, an early-stopping mode, a parsing-error handler that feeds a malformed tool call back to the model instead of raising, and a flag to return the intermediate steps for inspection.

It is the legacy runtime, and knowing that is the point of the record: the executor's loop is fixed, its state is an opaque list of steps rather than something you can shape, and there is no built-in place to interrupt, checkpoint, resume or branch. That is why the pre-1.0 executors moved into the compatibility package and why new work goes to the current agent factory or a graph runtime. Reach for it only in a codebase already built on it, where the two-line construction is genuinely cheaper than a migration.

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

It connects to one or more MCP servers over stdio or HTTP, reads the tools they advertise, and materializes each as a LangChain tool object with the schema translated into the argument format an agent expects, so a LangGraph agent calls an MCP server without any protocol code of its own. The value is that tools stop being compiled into the application: a server can be added or swapped by configuration, and the same server still serves any other MCP client. Use it when you are already on LangChain or LangGraph and want the ecosystem of existing servers; if you are not, the official MCP SDK talks to those same servers directly and adds less indirection.

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

LangChain splits each provider integration into its own package so it can version against the vendor SDK independently; this one wraps the `anthropic` SDK and exposes `ChatAnthropic`, mapping LangChain's message, streaming and tool-calling abstractions onto the Messages API, including multimodal content blocks. Install it alongside `langchain-core` when a chain or a LangGraph agent should run on Claude. Provider-specific capabilities usually surface here before they are abstracted into the generic interface, so it is worth reading this package's own documentation rather than assuming the common `BaseChatModel` surface covers everything the model can do.

### langchain-classic
**Short:** Compatibility package holding LangChain's pre-1.0 chains such as LLMChain and RetrievalQA; only for legacy code.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

When the main package was slimmed down to agent and message primitives, the pre-1.0 abstractions -- the chain classes, the retrieval-question-answering wrappers, the old agent executors and index helpers -- moved here so existing applications keep running after a package rename rather than a rewrite. Nothing new is developed against it.

Depend on it only to buy time on a codebase that cannot be migrated yet, and treat the dependency as debt with a date on it. New work belongs on the current agent factory or on a graph runtime, both of which express the same pipelines with the prompt and the control flow visible instead of hidden inside a chain class.

### langchain-community
**Short:** LangChain's package of 300+ third-party integrations: vector stores, loaders, tools and model providers.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2

It is the catch-all for third-party integrations that do not have a maintained partner package -- vector stores, document loaders, retrievers, tools, chat models -- contributed and largely maintained by whoever needed them. Dependencies are optional and imported lazily, so an integration installs fine and fails at first call if its library is missing.

Reach for it when the integration you need exists nowhere else, which is often. Two costs: quality varies enormously between entries, since there is no common maintenance standard, and popular integrations get promoted out into their own vendor package, so the maintained version of the thing you imported may have moved. Check for a dedicated partner package before importing from here.

### langchain-core
**Short:** LangChain's stable base package: Runnable composition primitives, message and prompt types shared by integrations.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2

langchain-core holds the abstractions everything else is built on: the Runnable protocol with its invoke, batch and stream methods, message and prompt-template types, output parsers and the tool interface — with almost no dependencies of its own. Because those interfaces compose, a chain is a pipeline of Runnables that inherits streaming, batching, async and retries without each integration implementing them separately.

Depend on it directly when you are writing an integration, or when you want the primitives without pulling in the wider framework. The point of the split is that this base contract stays stable while the integration packages move quickly.

### langchain-openai
**Short:** LangChain's OpenAI integration package supplying ChatOpenAI, embeddings and function-calling for agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @3, llm-apps/tool-use-and-mcp @3

This is the provider package holding LangChain's OpenAI bindings — `ChatOpenAI`, `OpenAIEmbeddings`, and the tool-calling and structured-output plumbing that maps LangChain tool definitions onto the API's function-calling schema. Provider integrations were split out of the core package, so you install only the providers you use and their SDK version bumps do not drag the framework along with them.

You will meet it as the LLM behind an agent or chain in most LangChain and LangGraph examples. `ChatOpenAI` also points at any OpenAI-compatible endpoint through `base_url`, which is how local servers and gateways get used without changing application code.

### LangGraph
**Short:** Graph runtime for stateful, checkpointed LLM agents with branching, loops and human-in-the-loop.
**Kind:** tech
**Lang:** python, js
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2, data-movement/workflow-and-durable-execution @2, search-retrieval/rag-and-document-processing @3

You declare a typed state object and register nodes that each return a partial update to it; edges — including conditional edges that choose the next node from the current state — make the control flow explicit graph structure rather than emergent behaviour of a while loop, so branching, retries and backtracking are things you can read off the graph. A checkpointer persists state after every step, which is what makes an agent resumable after a crash, interruptible for human approval before a risky tool call, and inspectable when it misbehaves.

Reach for it when the agent needs durability or control flow a single prompt-and-tools loop cannot express. For a straightforward tool-calling assistant the extra graph machinery is not worth it.

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

It is the managed tier of the graph runtime: a compiled graph is deployed as a service where a task queue and horizontal workers execute runs, a managed database holds checkpoints and the long-term store, and the platform layers on a streaming API, scheduled runs, endpoints for resuming an interrupted graph after human approval, and a visual debugger over the trace.

Reach for it when durability and human-in-the-loop are the requirement and you would rather not build the queue, the checkpoint store and the resume endpoints yourself. The cost is hosting fees and coupling for what is ultimately a process plus a Postgres database -- self-hosting the same graph is entirely feasible, and the platform earns its keep on the operational plumbing rather than on the runtime.

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

It implements the checkpointer interface against Postgres, writing a row per super-step containing the channel values and any pending writes, so a thread can be resumed after a crash, forked from an earlier checkpoint, or replayed step by step for debugging. The same package backs the long-term store, optionally with vector search over saved memories. Tables are created by a one-time setup call.

This is the production choice: multiple workers can serve the same threads, and the state is queryable with ordinary SQL when something goes wrong. The cost is write amplification -- a checkpoint every step means a large state object is serialised repeatedly -- so keep state small, put big blobs behind references, and have a retention policy for old threads.

### langgraph-checkpoint-sqlite
**Short:** LangGraph checkpointer persisting agent graph state to SQLite so a run can resume after a restart.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, data-movement/workflow-and-durable-execution @2, data-stores/key-value-and-embedded @3

The same checkpointer interface backed by a local file or an in-memory database, in synchronous and asynchronous variants. Nothing to provision, so it is the default for development, notebooks, tests and single-process desktop applications, and it still gives you the real behaviour -- interrupt, resume, time-travel to an earlier checkpoint -- rather than a stub.

Use it locally and swap the checkpointer for Postgres on deploy; the graph code does not change, which is the point of the interface. Do not carry it into a multi-worker server: SQLite's single-writer model and file locking turn concurrent runs into lock contention and intermittent write failures that look like graph bugs.

### LangMem
**Short:** Long-term agent memory for LangChain: stores reflections and retrieves them by recency and importance.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1

It gives an agent memory that outlives a single conversation: rather than replaying a transcript, the agent extracts durable facts and reflections into a store and searches that store on later turns, so the prompt carries a consolidated view instead of everything ever said. Extraction can run in the background between turns, keeping the cost of writing memory off the user's latency path.

Reach for it when an assistant must remember preferences or prior decisions across sessions. Memory written automatically also accumulates stale and contradictory entries, so decide how entries get updated and forgotten before treating any of them as authoritative.

### LangSmith Prompt Hub
**Short:** Versioned prompt registry in LangSmith, tied to its datasets and evals so a prompt change can be scored.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/evaluation-and-benchmarks @2, observability/tracing-apm-and-llm-observability @3

Prompts are versioned objects with commits and tags: the application pulls one by name and tag at runtime, so editing a prompt ships without a deploy and rolling back is moving a tag. Because it lives inside the tracing product, a prompt version is linked to the traces it produced and to datasets and evaluators, which turns did this edit help into a scored comparison rather than an argument.

Reach for it when prompts are iterated by people who do not deploy code and you want each change evaluated. Two costs: a runtime pull is a network dependency on the request path unless you cache or pin, and the evaluation integration -- the actual reason to choose this over a file in the repository -- ties you to the surrounding platform.

### Letta
**Short:** Agent framework (formerly MemGPT) giving agents OS-style memory: self-managed context and archival paging.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

It productises the idea that an agent should manage its own context: a small core memory block sits permanently in the prompt and the agent rewrites it with tools, while larger archival and recall stores are searched and paged in on demand. Agents are server-side objects with identifiers and persistent state in a database, so the agent outlives your process rather than being reconstructed from a transcript each run.

Reach for it when an assistant must accumulate durable knowledge about a user or a domain across long-running use. The costs are that self-editing memory can also self-corrupt -- a wrong fact written into core memory colours everything afterwards -- and every memory operation is another model call. A retrieval layer you control is cheaper and more inspectable when self-management is not the point.

### LiteLLM
**Short:** Unified API and proxy over 100+ LLM providers with routing, fallbacks, cost tracking and response caching.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, caching/semantic-and-llm-cache @2, observability/tracing-apm-and-llm-observability @3

The library normalises every provider to the OpenAI chat-completions shape, so `litellm.completion(model=..., messages=[...])` returns the same object whether the call went to OpenAI, Anthropic, Bedrock, Vertex or a local vLLM server -- and exceptions are mapped onto the OpenAI error types too, so retry logic written once keeps working across providers. Around that it adds router behaviour: model lists with weights, fallback chains when a deployment errors or rate-limits, retries with backoff, and per-call cost computed from a maintained price map. It is the seam that turns "swap the model" into a config change rather than a code change, which matters most while you are still choosing one. Reach for it in application code; when several teams or services need the same behaviour, run its proxy server instead so keys and budgets live in one place.

### litellm Proxy
**Short:** Self-hosted LLM gateway giving one OpenAI-shaped endpoint over many providers with keys, budgets and fallback.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, platform-delivery/cloud-platform-and-cost @3

The proxy is LiteLLM run as a server: you define model deployments in a config file, and clients point any OpenAI-compatible SDK at its base URL using a virtual key it issued, so provider credentials live only in the proxy and never in application code or a notebook. That central position is what makes governance possible -- per-key and per-team budgets and rate limits, spend attributed by key, tag and model, request logging into your observability backend, and routing policy (weighted deployments, least-busy selection, cross-provider fallback) changed without redeploying a single client. Reach for it when several teams or services share model access and somebody must answer who spent what and cut off a runaway job. The cost is a hop in the request path that is now a shared dependency, so it needs the same availability and latency budget as any other piece of production infrastructure.

### litellm Python SDK
**Short:** Python client giving one OpenAI-shaped call signature over 100+ model providers, with fallback, retry and cost tracking.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1

Beyond the single call signature, the substance is the router and the accounting: a router holds a list of deployments with weights and health state and picks one by strategy, falling back across providers on errors and rate limits, while a maintained price map turns each response's token usage into a cost figure you can log. Callbacks push traces and spend into observability backends without touching call sites.

Reach for it in application code when one service talks to several models. Two costs: normalising to one shape means provider-specific parameters travel through an escape hatch and behave differently per backend, and the package moves quickly as providers and prices change, so pin the version. When several teams need the same behaviour, run its proxy instead of duplicating configuration.

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

The agent joins a real-time room as another participant and runs a pipeline inside a worker process: voice activity detection, speech to text, the model, then text to speech -- or a single realtime speech model in place of the middle. Turn detection decides when the user has actually stopped speaking, and barge-in cancels in-flight synthesis when they interrupt, which is what makes the conversation feel like one.

Reach for it when the product is voice over real networks -- mobile, browser, telephony -- because the transport handles packet loss and jitter that a naive WebSocket does not. The cost is that you are now operating real-time media infrastructure with a latency budget of a few hundred milliseconds end to end, and that budget constrains every model and vendor choice in the chain.

### llama.cpp GBNF
**Short:** llama.cpp's BNF-style grammar format constraining sampling to a schema; the engine behind Ollama's JSON mode.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2

A grammar file describes the allowed output in a BNF-like syntax; the engine compiles it into a state machine and, at each sampling step, zeroes the probability of every token that could not continue a valid string, so invalid output is unreachable rather than merely discouraged. JSON Schema is not supported directly -- a bundled converter translates a schema into a grammar first.

Reach for it whenever local generation feeds a parser. The subtleties are all at the token boundary: grammars constrain byte sequences while the model emits tokens, so whitespace rules matter, an over-tight grammar can force the model into awkward continuations, and deeply recursive grammars slow sampling measurably. As always, valid structure is not correct content.

### LlamaIndex Agents
**Short:** LlamaIndex's data-agent layer (ReActAgent, SubQuestionQueryEngine) for query planning and tool use over indexed corpora.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, search-retrieval/rag-and-document-processing @2, llm-apps/tool-use-and-mcp @3

This is LlamaIndex's agent layer, which turns indices, retrievers and query engines into tools an LLM can choose between. `ReActAgent` runs the reason-act-observe loop, deciding at each step which tool to call and when it has enough to answer. `SubQuestionQueryEngine` takes a compound question, decomposes it into sub-questions, routes each to whichever index can answer it, and synthesizes the parts — which is how you answer a question that spans two document collections.

Reach for it when the agent's job is mostly answering over your own corpora and you want the retrieval plumbing, node postprocessing and response synthesis already assembled. When you need explicit control of the state machine — branching, retries, checkpoints, human approval mid-run — a graph-based framework gives you more, at the cost of writing more.

### llguidance
**Short:** Compile-free token-trie lexer engine for constrained decoding; backs Guidance and vLLM's guidance grammar backend.
**Kind:** tech
**Lang:** rust
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2

It builds the set of allowed tokens incrementally as generation proceeds, using a trie over the tokenizer's vocabulary and a parser that advances with each token, rather than precomputing a full automaton for the schema up front. That removes the per-schema compilation stall, so a grammar seen for the first time is usable immediately -- which matters when every request carries a different schema.

You rarely reach for it directly; you meet it as the backend behind a higher-level structured-output library or an inference engine's grammar flag. The constraint is the same as for every masking approach: it only applies where you control the logits, so it is irrelevant against a hosted API, and it guarantees shape rather than substance.

### LLMLingua
**Short:** Neural prompt compressor that drops low-information tokens from long retrieved context while keeping the answer.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, search-retrieval/rag-and-document-processing @3

A small language model scores each token by how predictable it is in context and drops the ones the large model could infer anyway, working coarse to fine -- first allocating a budget across demonstrations and documents, then compressing token by token within them. The result is a prompt several times smaller that the target model still answers from correctly.

Reach for it when a long retrieved context is the dominant cost and the ratio is large enough that a small model's forward pass is cheap by comparison. Two costs: the compressed prompt is unreadable, so debugging a bad answer gets harder, and aggressive ratios eventually drop the one sentence the answer needed. Measure end-task accuracy, not compression ratio.

### LLMLingua-2
**Short:** Neural prompt compressor that drops low-information tokens from long retrieved context while preserving task accuracy.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, search-retrieval/rag-and-document-processing @2

It replaces the perplexity heuristic with a trained classifier: a small bidirectional encoder labels each token keep or drop, trained on data distilled from a strong model asked to compress text without losing information. Being bidirectional it sees the whole passage rather than only the left context, and being a single classifier pass it is far faster and less tied to any particular target model.

Reach for it when compression must run inline on the request path, where a causal scoring pass would be too slow. Its blind spot follows from the training objective: it compresses for general informativeness and does not know which facts your question needs, so on retrieval-heavy question answering a query-aware variant does better. Validate against your own task, not a compression benchmark.

### lm-format-enforcer
**Short:** Constrained-decoding library forcing generation to match a JSON schema or regex; a vLLM/HF backend.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @3

It works inside the sampling loop rather than after generation: at each step it computes which tokens could still extend the output into something matching the JSON schema or regular expression, and masks the logits of the rest. Invalid output is therefore not possible, as opposed to being asked for in the prompt and retried when the model ignores it. It plugs into `transformers`, vLLM and llama.cpp-style runtimes as a logits processor, and it deliberately allows the whitespace and token variation a strict character-level automaton would forbid, so the model stays in distribution.

Reach for it whenever code downstream parses the model's output. The costs are a per-step overhead to compute the allowed set, and the subtler one that constraining hard can force a well-formed but poor answer — the schema is satisfied while the content is wrong.

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

Rather than stretching every rotary dimension by the same factor, it searches -- with an evolutionary algorithm -- for per-dimension rescaling factors, exploiting the fact that different frequency bands tolerate different amounts of interpolation. Combined with progressive extension, fine-tuning at an intermediate length before searching again, this reaches very large context windows with little degradation at short lengths.

Reach for it when you are producing a long-context checkpoint yourself and quality across the whole window matters. The cost is that this is a training job, not a runtime flag: the search plus fine-tuning takes real compute, and the resulting factors are specific to the model they were found for and cannot be copied to another checkpoint. Simpler rescaling is the cheap, lossier alternative.

### Mamba
**Short:** Selective state-space sequence architecture with linear-time scaling; an attention alternative for long context.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, applied-ml/nlp-and-text @3

It is a state-space model whose transition parameters are functions of the current input -- the selective part -- so the model chooses what to write into and what to forget from a fixed-size hidden state as it scans the sequence. Training uses a hardware-aware parallel scan to stay efficient, and inference needs constant memory per token with no key-value cache, against attention's linear cache growth and quadratic training cost.

It matters wherever sequences are long and the cache is the bottleneck. The fixed-size state is also the limitation: exact recall of an arbitrary earlier token -- copying, in-context lookup, retrieving a needle -- is where pure state-space models lose to attention, which is why the models that actually ship are hybrids that keep a minority of attention layers for precisely that.

### marvin
**Short:** High-level library that maps LLM output onto Python types for typed extraction and classification.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @3

You annotate a function or hand it a Pydantic model, and it builds the prompt, calls the model, validates the result against your type and retries when parsing fails, so the call site returns a typed Python value rather than a string you have to parse. That suits extraction, classification, labelling and generating structured test data.

Reach for it when the task is one call with a known output shape and you want the LLM to feel like a normal function. `instructor` is the thinner alternative if you want to keep the provider SDK in view, and anything that needs tools, memory and a loop belongs in an agent framework instead.

### Mastercard Agent Pay
**Short:** Mastercard's agentic payment tokens, extending MDES tokenization so an AI agent can transact with scoped credentials.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, security/authentication-and-identity @3

It extends the network's existing tokenization so an agent transacts with a token scoped to that agent and to the user's consent, rather than with a card number. The value is at the network level: an issuer can distinguish an authorised agent purchase from card-not-present fraud and has something concrete to authorise against, instead of seeing an unexplained transaction from an unfamiliar context.

It addresses payment authorisation only. Discovery, cart construction, order status and dispute handling live in the commerce protocols above it, so a working agent checkout needs both layers. And being a network scheme, it arrives when issuers, acquirers and merchants implement it -- adoption timelines, not the specification, decide whether it is usable.

### Mastra CLI
**Short:** Mastra's CLI: scaffolds an agent project and runs the local dev server and playground.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agent-framework @1, devtools/version-control-and-workbench @3

One command scaffolds a project with agents, tools and workflows laid out in a conventional directory structure, and another runs a local server plus a playground where you chat with an agent, step through a workflow, and inspect each tool call and trace. A build command produces a deployable server bundle from the same project.

Reach for it for the inner loop while developing -- the playground makes an agent's tool calls visible without writing a driver script. It is a development surface only: it reloads on file change, holds state locally, and is not an observability story, so production still needs traces exported somewhere durable.

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

This is the official Python SDK, carrying both layers: a low-level server class where you register handlers yourself, and the decorator-based high-level interface that derives tool schemas from type hints, which was absorbed into this package from a separate project. The client half provides a session object plus transport helpers for spawning a local server over standard input and output or connecting to a remote one over HTTP.

Reach for it for any Python MCP work, server or client. Two practical notes: the protocol is dated-versioned and the client and server negotiate at connect time, so pin the SDK and check the version your host actually agrees to; and the API is asynchronous throughout, so a synchronous codebase needs an event loop around it.

### MCP CLI
**Short:** Command-line runner for MCP servers that starts one locally and lists or invokes its tools during development.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

It does what a client does, from a shell: spawn or connect to the server, complete the handshake, and then list or invoke tools from the command line, printing the result. That makes a server testable from a script or a continuous-integration job with no model, no editor and no browser in the loop.

Reach for it to smoke-test a server after a change, or to check that a deployment is reachable and advertising the tools it should. Two limits: text output makes it a poor instrument for reading the raw protocol exchange, where an inspector UI is better, and a tool working from the command line says nothing about whether a model will choose it -- that depends on the description, which is a separate experiment.

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

Run it against a server command or URL and it starts a local UI that connects as a genuine client: you can list tools, resources and prompts, invoke a tool with arbitrary arguments, and read the raw JSON-RPC messages flowing in both directions. That message log is what makes it the debugging tool, because most MCP failures are a malformed input schema, a capability the server never advertised, or a handler that throws, and all three are visible there immediately. Use it before wiring a server into an agent, so you are debugging one component rather than a server and a model at the same time.

### MCP servers
**Short:** Model Context Protocol servers: processes exposing tools, resources and prompts over stdio or HTTP.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

A server is an ordinary process speaking JSON-RPC that advertises three kinds of thing: tools the model may invoke, resources the host application can read by URI, and prompt templates the user can select. Transport is standard input and output when the host spawns it locally, or streamable HTTP when it is remote, and capabilities are negotiated at connect time so a host knows what it is talking to.

The reason to build one instead of a library function is decoupling: the same server serves every MCP-capable client, and adding a capability becomes configuration rather than a code change. The security question follows directly -- a local server runs with your credentials and a remote one sees every argument the model sends it, and a tool description is prompt text a hostile server can weaponise.

### MCP spec
**Short:** Model Context Protocol: JSON-RPC schema plus stdio and Streamable-HTTP transports exposing tools to models.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

The specification defines the message set -- initialize and capabilities, tool listing and invocation, resources, prompts, sampling back to the host's model, and change notifications -- over JSON-RPC, plus the two standard transports and an authorisation model for HTTP servers built on OAuth. Revisions are dated, and the two sides negotiate a version at connect time so a mismatch degrades explicitly instead of misbehaving.

Read it when writing a server or client from scratch, or when debugging behaviour an SDK abstracts. The practical caveat is that it moves: capabilities have been added across revisions, so supports MCP is not a single level of support, and the honest question about any implementation is which dated version it targets and which optional features it actually implements.

### mcp-cli
**Short:** Command-line MCP client for listing and invoking a server's tools, resources and prompts while developing or debugging.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/testing-and-mocking @3

It connects to a server the way a host would, then exposes the protocol as subcommands: enumerate tools, resources and prompts, call a tool with JSON arguments, read a resource. Because it is scriptable, it slots into a test suite or a deployment check where an interactive UI cannot.

Reach for it to verify a server outside the agent loop, which is the single most useful debugging move -- it separates is the server broken from is the model choosing badly. Its limits are the same as any command-line client: no view of the raw message exchange, and no signal at all about whether tool descriptions are good enough for a model to act on.

### mcp-server-fetch
**Short:** Reference MCP server that fetches a URL and converts the page to Markdown for a model to read.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @3

It takes a URL, retrieves it, and converts the HTML to Markdown so the model reads prose and headings instead of tag soup and script blocks. A start index and a length limit let a long page be paged through across several calls rather than blowing the context window, and it honours robots directives by default for model-initiated requests.

Reach for it when an agent needs to read a specific page and a full browser is overkill. Two limits: no JavaScript runs, so single-page applications come back essentially empty and need a real browser tool; and whatever it fetches lands directly in the model's context, which makes it the textbook indirect prompt-injection path -- treat retrieved text as untrusted data, not instructions.

### mcp-server-git
**Short:** Reference MCP server exposing git read, search and mutation operations as agent tools.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, devtools/version-control-and-workbench @2

It exposes git as typed tools over a repository path -- status, diffs against the index or a ref, log, show, staging, commit, branch creation and checkout -- so an agent inspects and mutates history through structured arguments and structured results instead of parsing the output of a shell command. Network operations are not part of it as shipped.

The read side is where most of the value is: an agent that can diff and read history makes better changes than one that only sees current file contents. The write side deserves care -- commit, checkout and reset give an agent control of history, so scope it to one repository and give it a branch it owns. Pushing, which is often what you actually wanted, needs something else.

### mcp-server-time
**Short:** Reference MCP server exposing current-time and timezone-conversion tools to any MCP-capable model.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

Two tools: the current time in a given IANA timezone, and conversion of a time between zones, resolved against the system timezone database. It exists because a model has no clock -- asked for today's date it will state one from its training data with complete confidence -- and because timezone arithmetic is exactly the kind of thing models get subtly wrong.

Its real role is pedagogical: it is the canonical minimal server, small enough to read in a sitting, and the clearest example of a capability that must come from a tool rather than the model. Anything beyond current time and conversion -- durations, business calendars, scheduling -- you write yourself, at which point you are writing a server and this is the template.

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

Mem0 sits between the application and the model: it reads the conversation, uses an LLM to extract durable facts worth keeping ("prefers metric units", "is migrating off Postgres"), embeds and stores them, and retrieves the relevant few to inject into later prompts. The part that distinguishes it from a plain vector store is the update step — when a new message contradicts a stored fact, it revises or deletes rather than accumulating both.

Reach for it when an assistant needs to feel continuous across sessions and stuffing the full history into context is too expensive or too long. Treat the extraction step as a failure source you own: a fact captured wrongly persists and quietly biases every later answer, so memories should be inspectable, editable and deletable by the user, and scoped per user so one person's context never reaches another's.

### MemGPT
**Short:** Agent memory system (now Letta) that pages facts between a small context window and external storage, OS-style.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

The paper framed the context window as RAM and external storage as disk, and made the model itself the pager: it issues function calls to edit a small in-context core memory, append to and search recall and archival stores, and it is prompted to do so when it approaches a memory-pressure threshold. A fixed context window therefore behaves like a larger virtual one, without changing the model.

The idea outlived the name -- the project was renamed Letta, so MemGPT now refers to the technique rather than a package you install. The costs are inherent to self-management: every page-in is another model call on the latency path, and a bad edit to core memory is durable and colours everything afterwards, which is why inspectable and editable memory matters more than clever retrieval.

### Mercury Coder API
**Short:** Inception Labs' hosted diffusion language model, aimed at very fast code completion.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, applied-ml/vision-speech-and-multimodal @3

It is a diffusion language model rather than an autoregressive one: instead of emitting tokens strictly left to right, it iteratively denoises a block of tokens in parallel, so throughput per generated token is far higher than a comparable autoregressive model. That is the whole pitch for code completion, where latency is the difference between a suggestion being useful and being ignored.

Reach for it when raw completion speed dominates and quality requirements are moderate. The costs are ecosystem and paradigm risk: diffusion language models are new, so tooling, fine-tuning and evaluation practice are thin, capability is aimed at completion rather than deep reasoning, and it is a hosted API from one vendor with no open-weight fallback.

### MetaGPT
**Short:** Multi-agent framework assigning software-team roles (PM, architect, engineer) that follow a structured SOP.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @3

It encodes a software-company standard operating procedure: each role has a profile, a goal and a set of actions, and produces a structured document -- requirements, a design with diagrams, an API specification, a task breakdown, then code -- which the next role consumes. Agents communicate through those artefacts in a shared workspace rather than through free-form chat, which is what keeps a multi-agent run from degenerating.

The document-passing idea is worth stealing even if the framework is not. Its ceiling is the procedure: it does small greenfield projects following a waterfall, and has no story for iterating inside an existing codebase, which is what most engineering actually is. A single coding agent with repository access is more useful for real work.

### Microsoft Agent Framework
**Short:** Microsoft's supported agent runtime merging AutoGen and Semantic Kernel, with native MCP and A2A support.
**Kind:** tech
**Lang:** python, csharp
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, data-movement/workflow-and-durable-execution @3

It is the single supported path Microsoft points at for building agents on .NET and Python, folding AutoGen's multi-agent orchestration together with Semantic Kernel's plugins, connectors and enterprise plumbing. Tools arrive over MCP and agent-to-agent messaging over A2A, so the interop surfaces are protocol-level rather than framework-specific, and it plugs into Azure's Foundry tooling for deployment, evaluation and tracing.

Reach for it when the organization is already on Azure and .NET, where the alternatives are thin. Elsewhere the Python agent ecosystem is considerably denser, and Microsoft publishes a migration guide for the AutoGen code this supersedes.

### MLflow AI Gateway
**Short:** MLflow's provider-agnostic LLM endpoint: one API over many providers with keys resolved server-side.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1

Configuration is a list of named endpoints, each binding a provider and model to a request
shape such as chat, completions or embeddings, with credentials resolved on the server so
application code never holds a key. It is served by the tracking server itself; the standalone
deployment-server application from MLflow 2, and its `start-server` command, were removed in
MLflow 3, and the older `routes` and `route_type` configuration keys became `endpoints` and
`endpoint_type`.

Reach for it when you already operate MLflow and the immediate problem is keys scattered across
services and notebooks. It is deliberately thin: no semantic cache, no automatic fallback
chains, no per-tenant budgets. When routing behaviour is itself the product, a dedicated
gateway is the better fit.

### MLflow Prompt Registry
**Short:** Versioned, aliased prompt templates in MLflow, promoted and rolled back with the same vocabulary as model versions.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, ml-lifecycle/experiment-tracking-and-tuning @2

Registering a template creates a numbered version with a commit message, and aliases point at
whichever version is live, so loading a prompt by alias gives the same instant rollback that
loading a model by alias does. Traces record which prompt version produced them, which is what
turns "quality dropped last Tuesday" into an answerable question.

The value is not storage. It is that a prompt edit stops being an untracked string change
inside a Python file and becomes an attributable, reviewable, revertible version with an
evaluation attached. Reach for it as soon as more than one person edits prompts, and gate
promotion on a scored evaluation rather than on a reviewer's judgement, since a prompt change
has no type system to catch it.

### modelcontextprotocol/csharp-sdk
**Short:** Official tier-1 C# SDK for building MCP servers and clients in .NET.
**Kind:** tech
**Lang:** csharp
**Roles:** llm-apps/tool-use-and-mcp @1

It is built to look like ordinary .NET: the server registers into the generic host's service collection with a transport chosen by configuration, tools are attribute-annotated methods whose parameters and documentation generate the schema, and dependency injection works inside handlers. A client half connects to other servers from the same application.

Reach for it to expose an existing .NET service's capabilities over MCP without standing up a separate Python or Node process beside it. The usual caveat for a non-first-party SDK applies: newer protocol features land here after they land in the Python and TypeScript implementations, so check the protocol version it implements against what your hosts negotiate.

### modelcontextprotocol/go-sdk
**Short:** The official tier-1 Go SDK for building Model Context Protocol servers and clients.
**Kind:** tech
**Lang:** go
**Roles:** llm-apps/tool-use-and-mcp @1

Servers register typed handlers whose input schemas are derived from Go structs, and the transports cover the locally spawned process and streamable HTTP. The practical appeal is deployment shape: a Go MCP server compiles to a single static binary with no runtime, which makes it trivial to ship as a local tool server or run in a scratch container.

Reach for it when the surrounding service is Go, or when you want a server that installs as one file. It is newer than the first-party Python and TypeScript SDKs, so the API has churned and some protocol features arrive later -- pin the module version and re-read the release notes before upgrading.

### modelcontextprotocol/java-sdk
**Short:** Official Java MCP SDK for building MCP servers and clients, with Spring AI integration.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/tool-use-and-mcp @1

It offers both synchronous and reactive server and client APIs with pluggable transports, but most JVM teams meet it through Spring AI's starters, which auto-configure a server from annotated tool beans or register client connections from application properties -- so exposing an existing Spring service over MCP is largely configuration.

Reach for it when the tools you want to expose already live in a JVM service and rewriting them elsewhere is not sensible. Used bare it is more ceremony than the Python equivalent, and like every non-first-party SDK it trails the specification, so verify which protocol revision it implements before depending on a recent feature.

### modelcontextprotocol/kotlin-sdk
**Short:** The official Kotlin MCP SDK for building Model Context Protocol servers and clients on the JVM and Kotlin Multiplatform.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/tool-use-and-mcp @1

It is a Kotlin Multiplatform library built on coroutines and kotlinx.serialization, so
handlers are suspending functions and a tool's input schema follows from the serializable
types you declare rather than being written twice. Transports cover the locally spawned stdio
process and streamable HTTP.

Reach for it when the host is already Kotlin — an IDE plugin, an Android application, a Ktor
service — and rewriting the integration in another language makes no sense. It sits below the
first-party Python and TypeScript SDKs in maintenance commitment, so check the latest release
and which dated protocol revision it implements before depending on a recent feature; a client
that negotiates an older revision silently loses capabilities your server assumes.

### modelcontextprotocol/php-sdk
**Short:** The official PHP MCP SDK for building Model Context Protocol servers and clients from a PHP application.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

It exposes the core protocol — tools, resources and prompts — over the locally spawned stdio
transport and streamable HTTP, with tool schemas derived from typed handler signatures so the
declaration and the implementation cannot drift apart.

Reach for it when the tools worth exposing already live in a PHP application and the
alternative is a second service in another language purely to speak MCP. It is a
best-effort-tier SDK, so newer protocol features and authorisation arrive later than in the
first-party SDKs; check the last release and the protocol revision it implements before
building on anything recent.

### modelcontextprotocol/ruby-sdk
**Short:** The official Ruby MCP SDK for building Model Context Protocol servers and clients from a Ruby or Rails application.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

It covers the core protocol — tools, resources and prompts — over the locally spawned stdio
transport and streamable HTTP, with a server object you register handlers on and a client for
the other direction. Mounting a server inside an existing Rack or Rails application is the
usual deployment, so the tools reuse the authentication and models already there.

Reach for it when the domain logic worth exposing is already Ruby. It is a best-effort-tier
SDK rather than a first-party one, so it trails the specification: verify the last release and
the dated protocol revision it implements before relying on a recent capability.

### modelcontextprotocol/rust-sdk
**Short:** Rust SDK for building MCP servers and clients; a tier-2 official implementation of the protocol.
**Kind:** tech
**Lang:** rust
**Roles:** llm-apps/tool-use-and-mcp @1

An asynchronous implementation on the standard Rust runtime, with macro-based tool declaration deriving schemas from typed handler signatures, covering both server and client roles. Its natural uses are embedding an MCP endpoint inside an existing Rust service and shipping a small static binary as a local tool server.

Reach for it when the surrounding code is already Rust. As a second-tier implementation it lags the first-party SDKs on newer protocol features and its API is still moving, so pin the crate version and expect breaking changes; if the language is negotiable, an official SDK removes a class of surprises.

### modelcontextprotocol/swift-sdk
**Short:** The official Swift MCP SDK for building Model Context Protocol servers and clients in Apple-platform and server-side Swift.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

It implements the core protocol on Swift concurrency, with `Codable` types for the message
schema and transports covering the locally spawned stdio process and streamable HTTP, so both
a macOS host application acting as a client and a server-side Swift process exposing tools are
in scope.

Reach for it when the surrounding application is a native Apple-platform app or a Swift
service and running a sidecar in another language is not worth it. It is a best-effort-tier
SDK, so protocol revisions and newer features land later than in the Python and TypeScript
ones — check the last release and the implemented revision before depending on either.

### Nuance DAX Copilot
**Short:** Healthcare vertical AI product that listens to a clinical visit and drafts the encounter note.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, applied-ml/vision-speech-and-multimodal @2

It is ambient documentation: a microphone captures the clinician-patient conversation, speech recognition transcribes it with speaker separation, and a model turns the transcript into a structured encounter note in the expected sections, delivered into the electronic health record for the clinician to review, edit and sign. The clinician remains the author of record.

The value proposition is minutes saved per encounter, which only materialises if the draft needs light editing rather than rewriting -- so review is not optional, and an unedited hallucinated finding in a medical record is a serious harm rather than an inconvenience. Deployment gates are procurement-shaped: protected health information handling, consent, and per-system integration rather than technical difficulty.

### Official MCP Registry
**Short:** The canonical registry of Model Context Protocol servers: verified reverse-DNS namespaces and server.json metadata.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

Server authors publish a `server.json` describing the package, its transports and configuration, under a namespace verified by DNS or GitHub ownership, so a name provably belongs to the organisation it claims. That provenance is the point: installing an MCP server means running someone else's code against your data, and a name-squatted server is a supply-chain attack.

Consume it to discover servers or to let a client or a downstream registry mirror the catalogue. It is still in preview, so expect the metadata schema and the publishing flow to move, and verify a server yourself before trusting it regardless of listing.

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

### OpenAI function-calling JSON schema
**Short:** The JSON Schema tool-declaration format models emit calls against; effectively the interoperable default across providers.
**Kind:** spec
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, apis-frameworks/data-formats-and-api-contracts @3

A tool is declared as a name, a description and a JSON Schema for its parameters, and the model returns a structured call naming the tool with an argument object rather than free text you have to parse. Constrained decoding against that schema is what makes the arguments reliably well-formed; the description field does more work than it looks, since it is the only thing telling the model when the tool applies.

It became a de-facto interchange format - Anthropic's tool use and MCP both take the same shape - so one declaration usually moves between providers with minor edits. Schema validity is not semantic validity, though: a well-formed call can still be the wrong tool with plausible arguments, so validate server-side.

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

It is a product feature of the chat application, not a developer surface: the system decides what is worth keeping from a conversation, stores it as memories the user can view and delete, and injects the relevant ones into the context of later conversations, with a separate setting for referencing past chat history wholesale.

Study it as a design reference rather than a dependency -- the user-facing controls, the ability to inspect and delete, and the per-user scoping are the parts worth copying. There is no API: you cannot read, write, scope or evaluate it, so building the same behaviour on top of the API means your own extraction step, your own store and your own retrieval, which is what the dedicated memory layers exist to provide.

### OpenAI Playground
**Short:** OpenAI's browser console for interactive prompt iteration, parameter sweeps and inspecting token probabilities.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, devtools/version-control-and-workbench @3

It is a browser console over the API: choose a model, edit the system and user messages, adjust parameters, attach tools and an output schema, run, and copy out code that reproduces the exact request. A comparison mode runs the same input against two configurations side by side, and prompts can be saved and versioned rather than living in a browser tab.

Reach for it for the first ten minutes of any prompt, where seeing the raw request and response beats reading documentation. Two things it is not: it bills your key at normal rates, and it evaluates nothing -- a prompt that looks good on three hand-picked inputs is an anecdote. Move to a dataset and a scoring harness before shipping.

### OpenAI Responses API
**Short:** OpenAI's agent-oriented endpoint: hosted tools, remote MCP, computer use and server-side conversation state.
**Kind:** api
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agent-framework @2, llm-apps/agentic-environments @3, llm-apps/prompting-context-and-structured-output @3

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

An `Agent` is instructions plus tools plus an optional typed output schema, and `Runner.run` drives the loop: call the model, execute any tool calls, feed the results back, repeat until a final answer. Handoffs let one agent delegate to another as though it were a tool, which is how a triage agent routes to specialists; guardrails run validation on input or output and can abort a run; sessions persist conversation history; tracing is on by default, so a run is inspectable span by span rather than a black box.

It is deliberately small - a thin typed loop rather than a framework with its own abstractions for prompts, memory and chains - and it works against Chat Completions-compatible endpoints generally, not only OpenAI's. Reach for it when you want the agent loop handled and everything else to stay ordinary Python.

### OpenAI/Anthropic APIs, open-weight LLMs
**Short:** The general pool of hosted and open-weight LLMs used for zero/few-shot prototyping, long-tail cases and generation.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/llm-gateway-and-routing @3

The real decision behind this pairing is where inference runs. A hosted frontier API gives the best quality with no infrastructure and per-token pricing, but the weights move under you and the data leaves your network. Open weights you serve yourself pin the model version, keep data inside the perimeter, and turn cost into GPU-hours instead of tokens -- and are the only option if you need to fine-tune the weights.

Hosted wins for prototyping, spiky traffic and the hard tail of prompts where quality decides the product. Self-hosted wins once volume is high and steady, latency must be predictable, or compliance forbids egress. Most serious systems end up with both -- a small model for the mechanical majority and a frontier API for what it cannot handle -- which is why an interface that abstracts the two is worth having early.

### OpenHands
**Short:** Open-source coding-agent platform you self-host: sandboxed shell, editor and browser for an agent to work in.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @2

The agent acts through a small event interface -- run a command, edit a file, browse, message the user -- and those actions execute inside a sandboxed container that holds the workspace, while a web interface shows the terminal, the editor, the browser and the event stream as it happens. It is model-agnostic, so you point it at whichever provider or local model you have.

Reach for it when an autonomous coding agent must run on your own infrastructure, with your own model, and you can operate the container layer. The honest comparison is against commercial coding agents: capability depends heavily on the model you choose, and the polish gap is real -- what you get in exchange is inspectable code and no data leaving your environment.

### OpenRouter
**Short:** Aggregator giving one OpenAI-compatible endpoint over 400+ models and 70+ providers, with fallback lists.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1

You keep one key and one OpenAI-compatible base URL, and name the model in the request; OpenRouter routes to a provider, can fall back to another when one is rate-limited or down, and reports token usage and spend in one place. That makes comparing candidate models, or adding failover, a configuration change instead of another vendor integration.

Reach for it during evaluation, for a routing layer that picks a cheap model for easy requests, or when you want one bill. The costs are an extra hop of latency, another party in the request path, and provider-specific features arriving late or not at all -- production traffic on one settled model is usually better pointed straight at the provider.

### Outlines
**Short:** Structured-generation library that compiles a regex or JSON schema into an FSM constraining the decoder.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @3

Given a regex, a JSON Schema or a grammar, it precomputes an automaton over the tokenizer's vocabulary and, at each decoding step, masks the logits of every token that could not continue a valid string, so the output is structurally correct by construction instead of by generate-parse-retry. Because the vocabulary-to-state index is built once, the per-token cost stays close to unconstrained generation. Reach for it when you control the model and its logits and need output that always parses. It does not apply to a hosted API you cannot reach inside, and structural validity is not semantic correctness: the model can still fill a perfectly valid schema with wrong values.

### Pipecat
**Short:** Python framework for real-time voice agents: pipelines wiring STT, LLM and TTS with interruption handling.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, applied-ml/vision-speech-and-multimodal @2

Audio moves through a pipeline of processors as frames -- voice activity detection, speech to text, context aggregation, the model, text to speech, transport -- and separate control frames carry interruption, so a barge-in cancels downstream work mid-utterance instead of the agent talking over the user. Each stage is a swappable vendor, and transports cover WebRTC, WebSockets and telephony.

Reach for it when you want to assemble a voice agent from specific vendors rather than accept a bundled stack. The cost is that latency is additive across every stage and one slow vendor ruins the interaction, and the genuinely hard part is turn detection -- knowing when the human has finished speaking -- which is tuning work, not a model call.

### Plandex
**Short:** Terminal coding agent that plans a change set first and checkpoints work so edits can be reviewed or rolled back.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @2

Work is organised as a version-controlled plan: files are added to context explicitly, proposed changes accumulate in a sandbox separate from your working tree, and you review, apply or roll back at plan granularity, with branches and history over the plan itself. Long tasks are decomposed into subtasks it works through, and it can execute commands and iterate on failures.

Reach for it for large multi-file changes where you want a reviewable, revertible change set before anything touches your tree. The sandbox and plan machinery is ceremony for a one-file edit, so the smaller the change the less it earns; a terminal agent that commits directly to git gives the same undo with less structure.

### Playwright MCP
**Short:** MCP server exposing Playwright browser control as tools, so an agent can navigate and act on real web pages.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, llm-apps/agentic-environments @2

Its default mode hands the model the page's accessibility tree as structured text with references to each interactive element, so an action targets a named node rather than pixel coordinates -- which is faster, cheaper and repeatable compared with screenshot-and-click. A vision mode with screenshots exists for pages the tree cannot describe, and the full browser is available underneath.

Reach for it as the default browser tool for agents: deterministic targeting means fewer retries and far fewer tokens than an image-based loop. The costs are operational and security-shaped -- it runs a real browser, so browser binaries and memory are part of your deployment, and every page it visits injects untrusted text into the model's context, so use a disposable profile rather than your logged-in one.

### Portkey
**Short:** Managed LLM gateway giving one endpoint over many providers with routing, fallback, caching and cost tracing.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, caching/semantic-and-llm-cache @2, observability/tracing-apm-and-llm-observability @2

You point the SDK, or any OpenAI-compatible base URL, at Portkey and it fronts many providers behind one endpoint, applying a config that declares retries, timeouts, fallback chains and load balancing across models, plus exact-match and semantic caching. Every call is logged with tokens, latency and cost attributed per key or user, which is usually the reason it gets adopted in the first place.

Reach for it when a product calls several models and a provider outage or rate limit should become a fallback rather than an incident. It is another hop in the request path and another vendor holding your prompts, so weigh that against running a self-hosted gateway such as LiteLLM.

### PromptLayer
**Short:** Prompt registry and logging platform: versioning, team collaboration and production request monitoring.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, observability/tracing-apm-and-llm-observability @2

Prompts live in a registry with versions and labels, so the application fetches one by name at runtime and a non-engineer can edit or roll it back without a deploy, while every request through the SDK is logged with its inputs, output, latency and cost and can be scored or replayed against a new version. The point is decoupling: prompts become deployable configuration with an evaluation history behind a change, rather than strings buried in code and shipped on the application's release cycle. It suits teams where product or domain people own the wording. A single developer usually gets further keeping prompts in Git next to the code and adding a tracing tool for the request logs.

### Puppeteer
**Short:** Node library driving headless Chrome over DevTools Protocol for scraping, screenshots and browser automation.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agentic-environments @1, devtools/testing-and-mocking @2

It drives Chrome over the DevTools protocol from Node, exposing navigation, selector queries, evaluation of arbitrary JavaScript in the page, request interception, and screenshot or PDF capture, and it downloads a matched browser build on install so versions cannot drift. It is the raw automation layer -- what the page shows and what gets clicked is entirely your code's decision.

Reach for it when you want scripted, deterministic browser work and the pages are known. For new projects Playwright, from the same lineage, covers more browsers and more languages and is usually the better default. As an agent substrate it is the bottom layer: an LLM-driven wrapper still has to decide what representation of the page to show the model, which is the actual design problem.

### pyautogen
**Short:** The pip package for AutoGen, Microsoft's conversational multi-agent framework.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

The name is the trap. It was the distribution for the original conversational multi-agent library, whose successor generation split into separate core, agent-chat and extension packages with an incompatible API, and the project has since been absorbed into Microsoft's unified agent framework. Two very different codebases therefore answer to roughly the same name.

Before copying any AutoGen example, establish which generation it targets -- the old single-package imports and the new split packages do not interoperate, and a tutorial that runs fine against one produces import errors against the other. For new work, start from the current supported framework rather than either historical package.

### PyAutoGUI
**Short:** Cross-platform Python library driving real mouse and keyboard events for desktop automation and computer-use agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, devtools/testing-and-mocking @3

It synthesises real operating-system mouse and keyboard events, so from the target application's point of view a human is typing. There is no DOM and no accessibility tree -- locating a button means either hard-coded coordinates or template matching against a screenshot -- and a failsafe aborts the script when the cursor is slammed into a screen corner, which tells you how it is expected to go wrong.

Reach for it when the target is a desktop application with no API and no scriptable interface. The costs are severe: it drives the actual desktop, so it fights the user for the mouse and there is no headless mode; and pixel matching breaks on a resolution, theme or scaling change. Run it inside a dedicated virtual machine, and prefer a browser tool whenever the target is a web page.

### pydantic-ai package
**Short:** Pydantic's typed agent framework: dependency-injected tools and model output validated into Python types.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2

An agent is generic over two types: a dependencies type injected into tools and dynamic instructions, and an output type the framework validates -- on a validation failure the error is fed back to the model for a bounded retry, so the call site receives a typed object or an exception rather than a string. Model providers are pluggable and tracing integrates with OpenTelemetry.

Reach for it when a Python service wants type safety and dependency injection around model calls and the team already thinks in Pydantic and FastAPI idioms. It is younger and deliberately smaller than the established frameworks, so there are fewer prebuilt integrations and you write more of the retrieval and memory layer yourself -- which is the point for some teams and a cost for others.

### pydantic-ai-slim
**Short:** Minimal install of PydanticAI without bundled provider SDKs, for typed agents with only the deps you pick.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @2

It is the same library with provider clients and optional features moved into extras, so installing it with one provider extra pulls that vendor's SDK and nothing else. That keeps container images and serverless bundles small and avoids shipping five HTTP clients to run one model.

Reach for it in anything where dependency weight or cold-start time matters. The cost is a class of avoidable errors: forget an extra and the failure is an import error at first call rather than at install time, so pin the extras you need explicitly in the dependency file. If image size is irrelevant, the umbrella package is one less thing to get wrong.

### Reflexion
**Short:** Agent technique (Shinn et al. 2023) where the model writes verbal self-critique into memory and retries the task.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/agent-framework @1, llm-apps/prompting-context-and-structured-output @3

After a failed attempt the agent writes a short natural-language critique of what went wrong and what to do differently, stores it in an episodic memory, and that reflection is prepended to the next attempt -- so behaviour improves across trials with no weight update and no gradient. The essential prerequisite is a signal telling it the attempt failed: a failing test, an environment reward, or an evaluator.

Reach for it where such a signal is cheap and automatic, which is why it shows up in code and game-like tasks. The costs are that each trial re-runs the whole task, multiplying cost and latency by the trial budget, and that without a real success signal the reflection is a guess -- stale or wrong reflections accumulate and actively mislead later attempts.

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

Models are packaged as containers with a declared input and output schema and published under an owner, name and version, then run behind a uniform prediction API with streaming and webhooks. GPUs are allocated on demand and billed by the second, so an idle model costs nothing -- which is what makes trying twenty open models cheap.

Reach for it for evaluation, for occasional or bursty workloads, and for models you do not want to operate. The tax is cold starts: a rarely used model pays seconds of container and weight loading on each burst unless you pay to keep capacity warm, and per-second billing costs more than a reserved instance once traffic is steady. Move hot paths to dedicated capacity.

### RestrictedPython
**Short:** Compiles Python with a restricted AST and guarded builtins, giving in-process sandboxing of untrusted snippets.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @2

It compiles source against a restricted abstract syntax tree -- rejecting imports, exec and attribute access to internals, and rewriting attribute and subscript operations into guarded calls -- and you supply the globals, so the snippet sees only the builtins and objects you deliberately expose. Execution happens in your own process.

Reach for it for small expressions in a semi-trusted setting, such as user-authored formulas. It is not a boundary for code an unattended model wrote from an untrusted prompt: in-process means no protection against runaway CPU or memory, and its safety depends entirely on your globals dictionary being complete, which is historically where escapes have come from. A container or microVM is the real answer there.

### Riza
**Short:** Hosted WebAssembly sandbox running untrusted LLM-generated Python, JavaScript, Ruby or PHP code safely.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, security/supply-chain-and-runtime-security @2

It executes untrusted code inside a WebAssembly runtime rather than a container, so the guest has no syscalls except the ones the host explicitly provides and start-up is milliseconds rather than a container boot. Access is a hosted API, with integrations that drop straight in as an agent's code-execution tool.

Reach for it when per-call latency matters and the code is short and self-contained -- the fast start is the differentiator against container and microVM sandboxes. The boundary is also the constraint: no arbitrary native extensions and a restricted filesystem and network mean dependency-heavy Python does not simply work, so for pip-installable data analysis a microVM sandbox fits better.

### ROS2
**Short:** Robot Operating System 2: pub/sub middleware over DDS for robot control; VLA servers integrate as ROS2 nodes.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, data-movement/message-broker @2, apis-frameworks/rpc-graphql-and-streaming @3

Nodes exchange typed messages over three patterns -- publish and subscribe on topics, request and reply through services, and long-running goals with feedback through actions -- and discovery is handled by a DDS implementation, so processes find each other across machines with no central broker. Per-topic quality-of-service settings cover reliability, durability and depth.

Reach for it when integrating with real robot hardware, where it is effectively the standard. Two things cost new users days: DDS discovery is chatty and unreliable across subnets and containers, and mismatched quality-of-service settings mean a publisher and subscriber simply never connect, with no error to explain it. A model server integrates as a node that subscribes to sensor topics and publishes commands.

### RouteLLM
**Short:** Preference-trained routers that send easy prompts to a weak model and hard ones to a strong model.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/llm-gateway-and-routing @1, ml-lifecycle/evaluation-and-benchmarks @3

The routers are trained on human preference data rather than heuristics: given a prompt, predict whether the weak model's answer would be judged as good as the strong model's, and route accordingly. A threshold controls what fraction of traffic reaches the strong model, and several router types are provided -- a matrix factorisation model, a fine-tuned encoder classifier, and similarity-weighted ranking over labelled examples.

Reach for it when a large share of traffic is genuinely easy and you want a principled way to find it. The catch is calibration: a router trained against one strong-weak pair and one prompt distribution is not calibrated for yours, so the threshold has to be re-tuned against your own traffic and re-checked whenever either model changes.

### Salesforce Einstein
**Short:** Salesforce's embedded AI layer for CRM: agent assist, case routing, summarization and predictive scoring.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @3, applied-ml/nlp-and-text @3

It is the AI layer inside the CRM rather than a product you integrate with: predictive scoring and forecasting run over your Salesforce objects, and the generative features -- drafting emails, summarising cases, answering from knowledge -- are grounded in CRM records through a retrieval layer, with a trust layer that masks sensitive fields and enforces zero-retention terms with the underlying model providers.

Reach for it when the data and the workflow already live in Salesforce, because that grounding is the whole advantage and it does not travel. The costs are platform-shaped: per-feature licensing, platform limits, and a hard dependency on data quality -- grounded generation over a messy CRM produces confident summaries of wrong records.

### Self-Refine
**Short:** Prompting loop (Madaan et al. 2023) where one model drafts, critiques its own output, then revises it.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, llm-apps/agent-framework @2

One model plays all three parts: generate a draft, critique it against the task with specific actionable feedback, then revise using that feedback, repeating for a fixed number of rounds or until the critique reports nothing to change. No training, no second model, no external tools -- which is what makes it trivially available and also what bounds it.

It reliably helps on things the model can evaluate by reading: completeness, instruction adherence, tone, structure. It helps much less, and can actively hurt, on factual accuracy and hard reasoning, where the critic shares the generator's blind spots and can talk the model out of a correct answer. Each round costs two or three extra calls, so where a cheap external check exists, tool-based verification beats self-critique.

### Semantic Kernel
**Short:** Microsoft's enterprise agent SDK with a plugin/function model and planners, available for C#, Java and Python.
**Kind:** tech
**Lang:** csharp, java, python
**Roles:** llm-apps/agent-framework @1, llm-apps/tool-use-and-mcp @2, llm-apps/prompting-context-and-structured-output @3

Everything is a kernel function -- either a native method or a prompt template with typed inputs -- grouped into plugins that the kernel resolves and invokes, with the model choosing which to call through native function calling. Planners were the original mechanism for multi-step behaviour, generating a plan of function calls to execute, and have largely been replaced by simply letting the model call functions in a loop.

Reach for it in enterprise environments where the surrounding platform integration matters more than the newest agent pattern. Two costs: the abstraction count is high relative to what it does, so a small application carries a lot of ceremony, and cross-language parity is uneven -- the .NET implementation leads and the others follow, so a feature in one language's documentation may not exist in yours.

### semantic-kernel-azure-ai-inference
**Short:** Semantic Kernel connector for Azure AI Inference, exposing Azure-hosted models such as Phi-3 and Llama 3.
**Kind:** tech
**Lang:** python, csharp
**Roles:** llm-apps/agent-framework @1, llm-apps/llm-gateway-and-routing @2

It connects the kernel to Azure's inference endpoint, which fronts a catalogue of non-OpenAI models behind a single Azure-managed key and URL, so an application can target open-weight and third-party models with the same chat-completion interface it uses for Azure OpenAI, under the same Azure identity, networking and billing.

Reach for it when policy requires everything to run through Azure and the model you want is in that catalogue. The abstraction leaks: feature support varies by model behind the endpoint -- function calling, JSON output and streaming are not uniform -- so a chain that works on one deployment can fail on another. Test the specific model rather than the interface.

### Serper.dev
**Short:** Low-cost Google Search API wrapper used as an agent's web-search tool; returns links needing a fetch/parse step.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @2, search-retrieval/lexical-and-hybrid-search @3

It is a thin, fast API over Google's results page: send a query, get back organic results, the knowledge panel, answer box and related searches as JSON, typically in a few hundred milliseconds and at a fraction of the price of the official search offerings. That price is the reason it shows up as the default search tool in so many agent frameworks.

Reach for it when you want breadth of coverage cheaply. The consequence is a two-step agent: titles, links and short snippets are enough to decide what to read but not to answer from, so each search needs a follow-up fetch and parse -- two round trips and a page-extraction problem you now own. Agent-oriented search APIs return cleaned content in one call at higher unit cost.

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

It is a pre-release package feed published from the mainline branch, so a fix or a new connector is consumable before it reaches the stable feed. You add the feed to the project's package configuration and pin an explicit build number, since these are not semantically versioned releases.

Reach for it only to unblock -- a bug fixed upstream but not yet released, or a connector you need this week. There is no stability guarantee: APIs change between nightlies, and old builds are eventually removed, so a pinned version can vanish and break a build months later. Move back to a released version as soon as one carries the fix.

### Skyvern
**Short:** LLM+vision browser automation agent that fills forms and drives workflows on sites without hand-written selectors.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @3

Skyvern drives a real browser through Playwright and decides what to do from what is on the page -- a screenshot plus the extracted DOM handed to a vision-capable model -- rather than from selectors you wrote, so a workflow survives a site redesign that would break a scripted scraper. You give it a goal, a target URL, and a schema for the data to extract or the fields to fill; it plans and executes step by step, and it handles the tedious parts of real forms including file uploads and two-factor hand-offs. It is open source with a hosted option, and exposes runs through an API and a workflow builder. Reach for it for form-filling and extraction across many sites that offer no API -- it is slower and far more expensive per run than a scripted scraper, so it earns its place on breadth and brittleness, never on volume.

### Small OpenAI model
**Short:** A cheap small OpenAI model (gpt-4o-mini / nano class) used for query rewriting and other bulk transformations.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/llm-gateway-and-routing @1, search-retrieval/rag-and-document-processing @3

The cheap tier exists for the mechanical work around a pipeline rather than the final answer: rewriting and expanding a query before retrieval, reranking candidates, classifying intent, extracting fields, summarising a chunk. Those tasks are narrow enough that a small model with a tight prompt matches a frontier model's output at a fraction of the price and latency, and they run on every request, which is where the money is.

Reach for it for anything high-volume and well-specified. It degrades first on multi-step reasoning, long or conflicting instructions, and adversarial input -- and the failure is quiet: a subtly wrong query rewrite poisons retrieval and the end-to-end answer looks merely mediocre. Evaluate the sub-task in isolation so you can see which stage broke.

### Smithery
**Short:** Third-party MCP server registry and install CLI with publisher accounts for discovering agent tools.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

Smithery lists MCP servers with the command needed to install each one, so adding a tool server to a client is a CLI invocation rather than hand-editing a JSON config and guessing at the arguments. Publishers can claim accounts and ship their own servers, and the listing surfaces what a server exposes before you install it.

Treat an entry as a package, not a vetted component. An MCP server runs alongside your agent with whatever credentials you give it and sees the prompts and tool arguments that flow through it, so the same care you would apply to adding a dependency — who publishes it, what it can reach, what it needs access to — applies here, and appearing in a registry is not a security review.

### Smithery CLI
**Short:** Command-line installer and manager for MCP servers, wiring them into a client's config for you.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

It turns installing a tool server into one command: the CLI resolves the package from the registry, prompts for whatever configuration it declares, and writes the entry into the target client's configuration file with the right command and arguments -- replacing hand-edited JSON and guesswork about argv. It also lists and removes what it installed.

Reach for it when adding servers to a desktop client and you would rather not learn each client's configuration format. Two cautions: it writes to a file the client reads at startup, so a bad entry can leave the client with no tools and no clear error, and it installs whatever the registry lists -- the CLI removes friction, not the need to decide whether that code should run beside your agent.

### smolagents package
**Short:** Hugging Face's minimal agent library whose agents act by writing and executing Python code rather than JSON calls.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1, llm-apps/agentic-environments @2

Its distinguishing choice is the action format: the agent writes a Python snippet as its action and the framework executes it, so a sequence of calls, a loop or a filter over results happens in a single step rather than one JSON tool call per operation. Tools are plain Python functions with type hints, and execution runs either through a restricted local interpreter or in an external sandbox.

Reach for it when actions compose -- fetching several things and combining them -- and when you want a library small enough to read end to end. Executing model-written code is the whole risk, so the sandbox is the design decision rather than a deployment detail. It is also deliberately minimal: no memory, no durability, no graph, so anything long-running needs scaffolding around it.

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

It maps LLM work onto Spring idioms: a fluent client over a model bean, providers auto-configured from application properties by starter dependencies, advisors as the interceptor chain where chat memory and retrieval-augmented question answering plug in, annotated methods becoming callable tools with generated schemas, and one vector-store interface over the common backends.

Reach for it when the application is already Spring Boot and you want model calls to look like the rest of the codebase -- injected beans, externalised configuration, existing observability. Outside that stack it makes little sense, and being a fast-moving project through its first major release, older tutorials reference renamed classes and reorganised starter coordinates.

### spring-ai-anthropic-spring-boot-starter
**Short:** Spring Boot starter auto-configuring a Spring AI ChatModel backed by Anthropic Claude, with tool calling.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/tool-use-and-mcp @3, apis-frameworks/dependency-injection-and-config @3

The starter auto-configures a Claude-backed chat model from properties -- key, model name, sampling and token settings -- so application code only injects the client builder. Tool calling maps annotated methods onto Anthropic's tool-use blocks, and provider-specific options such as thinking configuration are exposed through the Anthropic options class rather than the portable interface.

Reach for it to run an existing Spring AI application on Claude with a dependency and a property change. Two frictions: starter artifact coordinates were reorganised around the framework's first major release, so an older tutorial's dependency may not resolve, and anything Anthropic-specific lives off the common interface -- which is fine until you assumed the abstraction was complete.

### spring-ai-ollama-spring-boot-starter
**Short:** Spring AI starter wiring a ChatModel to a local Ollama server, so a Spring app can call open models with no API key.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/llm-gateway-and-routing @1, inference/inference-engine @3, apis-frameworks/dependency-injection-and-config @3

It points chat and embedding models at a local model server's HTTP API using a base URL and a model name, with an option to pull the model automatically at startup so a fresh machine works without a manual step. No API key is involved, and the calling code is identical to the hosted-provider case.

Reach for it for local development without burning API credits, and for deployments where source or documents must not leave the network. The costs are capability and operations: local models are noticeably weaker at tool calling and structured output, so a chain tuned against a frontier model often needs prompt work, and you now own the hardware, the model pulls and the startup latency.

### spring-ai-openai-spring-boot-starter
**Short:** Spring AI starter auto-configuring OpenAI chat, embedding and image clients from properties.
**Kind:** tech
**Lang:** java
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @3

It configures chat, embedding, image and transcription clients from properties, and because it speaks the OpenAI wire format, overriding the base URL repoints the same beans at any compatible server -- a local runtime, an inference vendor, an internal gateway. That is the usual route by which a Spring application reaches a non-OpenAI model without a different starter.

Reach for it as the default provider starter, and as the escape hatch for compatible endpoints. The trap in that escape hatch is partial compatibility: a server may implement chat but not tool calling or structured output, and the failure surfaces as an empty or malformed response rather than a clear error, so test the specific features you use against the specific endpoint.

### Stablecoins
**Short:** Price-pegged crypto tokens used as the settlement asset in agent micropayment rails such as x402.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @3

A token pegged to a fiat currency, usually by held reserves, settling on a chain with sub-cent fees and near-immediate finality. That fee structure is the whole point for machine payments: a card network's fixed per-transaction cost exceeds the value of a fraction-of-a-cent API call by orders of magnitude, so per-request billing is arithmetically impossible on conventional rails and merely cheap here.

The properties that make it work also define the risk. Settlement is final and there are no chargebacks, which protects the recipient and leaves the payer with no recourse -- so a compromised agent's spending is unrecoverable, and keys belong in scoped, low-balance wallets with per-transaction limits. Add custody, key management, jurisdiction-dependent regulation, and the fact that the peg is an assumption rather than a guarantee.

### Stagehand
**Short:** TypeScript browser-automation SDK on Playwright exposing act/extract/observe primitives for AI-driven web agents.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/agentic-environments @1, llm-apps/agent-framework @3

It layers three primitives over a browser automation library: act performs a natural-language instruction against the page, extract pulls data into a schema you supply, and observe proposes candidate actions. Because it is a superset rather than a replacement, you drop to ordinary selectors for the steps you already know, and successful AI-chosen actions can be cached and replayed so a stable flow stops costing model calls.

Reach for it when part of a workflow is predictable and part is not -- the design lets you script the stable half and spend model calls only where pages change. A fully AI-driven flow is slow, expensive and non-deterministic, which is the failure mode to avoid; the discipline is deciding, per step, whether a selector would do.

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

It patches the fingerprints a headless browser leaks before page scripts run: the automation flag on the navigator object, missing plugin and language arrays, the absent browser runtime object, giveaway WebGL vendor strings, and user-agent values inconsistent with the client hints alongside them. Evasions are independent modules you can enable individually.

Reach for it when a legitimate automation task is blocked by naive bot detection. It is one side of an arms race and it is losing ground: modern detection also fingerprints the TLS handshake, request timing and mouse dynamics, none of which a JavaScript patch touches, so recipes age quickly. Using it usually also means acting against a site's terms -- an official API or a real browser profile is the durable path.

### strands-agents package
**Short:** Main SDK package for AWS Strands Agents, a model-driven Python framework for building tool-using agents.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

It is a model-driven loop rather than an authored graph: give an agent a model, a system prompt and Python functions decorated as tools, and the model decides when to call them until it produces an answer. Providers span the major hosted APIs and local runtimes, with MCP support for external tools and OpenTelemetry tracing for observability.

Reach for it when the deployment target is AWS -- that is where the model access, the deployment paths and the examples are thickest -- and when a thin loop is what you want. That thinness is the cost: no checkpointing, no branching, no resumable state, so long-running or human-approval workflows need a durable layer around it, and a graph runtime is the alternative worth comparing against.

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

The observation behind it is that transformers dump a large share of attention onto the first few tokens regardless of their content -- attention sinks -- so a naive sliding window that evicts them makes perplexity explode. Pinning a handful of initial tokens in the cache alongside a rolling window of recent ones fixes that, letting a model generate over a stream of millions of tokens at constant memory.

Reach for it for genuinely unbounded streams: an always-on assistant, a log or transcript monitor. The critical thing not to confuse is duration with recall -- it does not extend the model's effective context, and anything evicted from the window is gone, so it cannot answer questions about the middle of the stream. If you need that, you need retrieval, not a bigger window.

### swarm
**Short:** OpenAI's reference implementation of agent handoffs and routines; read-only teaching code, never published to PyPI.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agent-framework @1

A few hundred lines demonstrating two primitives: a routine, meaning a system prompt plus a set of functions, and a handoff, meaning a function that returns another agent and thereby transfers the conversation. It is deliberately stateless between calls and runs on nothing but chat completions -- the minimum viable multi-agent pattern, published to make the idea legible.

Read it to understand handoffs in an afternoon; do not build on it. It was never a supported package and has been superseded by a productised successor that keeps the same primitives and adds guardrails, sessions and tracing. Its lasting contribution is the observation that routing between agents is just a tool call that changes who is answering.

### SWE-agent
**Short:** Princeton research coding agent with an agent-computer interface for autonomous repo bug fixing on SWE-bench.
**Kind:** tech
**Lang:** python
**Roles:** llm-apps/agentic-environments @1, ml-lifecycle/evaluation-and-benchmarks @3

Its contribution is the agent-computer interface. Rather than handing a model a raw shell, it exposes a small purpose-built command set — open a file at a line, scroll a window, edit a line range with a lint check that rejects a syntactically broken patch, search the repository — because unbounded terminal output floods the context window and blind edits fail silently. That interface, not a better prompt, is what moved its SWE-bench resolve rate.

It runs each task instance in a container against a real repository and is the open baseline that later agent papers compare against. Reach for it as a reference implementation to read, extend, or benchmark against; for daily engineering work a maintained commercial coding agent is more capable.

### Tabby
**Short:** Self-hosted open-source coding assistant: an on-prem Copilot serving completions from models you control.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agentic-environments @1, inference/model-server @2

It is a self-hosted completion server: a single binary serving a code model, with a repository indexer that supplies cross-file context and IDE extensions that talk to your endpoint instead of a vendor's. It runs on CUDA, ROCm or Apple hardware, and can also proxy to a hosted model when you want the deployment shape without local weights.

Reach for it when code cannot leave the network and a hosted assistant is therefore ruled out. The costs are the usual self-hosting trade: completion quality tracks whatever model you can afford to run, which is below the frontier, and a GPU plus the upgrade and monitoring burden is the price of the privacy guarantee.

### Tavily Search
**Short:** Search API built for agents: returns cleaned, ranked, LLM-ready content and snippets instead of raw SERP HTML.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, search-retrieval/rag-and-document-processing @3

One call runs the search, fetches the result pages, strips navigation and boilerplate, and returns extracted content per result along with a short synthesised answer, so an agent receives text it can put straight in a prompt rather than a list of URLs it must crawl itself. Parameters shape that work, covering search depth, domain allowlists and blocklists, result count, topic and recency filters, and the depth setting is the direct cost-versus-quality dial.

Reach for it when an agent needs current information and you do not want to own crawling, HTML extraction, robots handling and rate limits. It is a metered external dependency sitting in your latency path, and the answers are only as good as the pages it found, so cache aggressively and pass the source URLs through to the user. Where the corpus is your own documents rather than the web, this is the wrong layer entirely and a vector index is the right one.

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

It serves a large catalogue of open-weight models behind an OpenAI-compatible endpoint, so switching from a hosted frontier API is a base URL and a model name. Beyond shared inference it offers dedicated endpoints for reserved capacity, fine-tuning that returns weights you can download, and GPU clusters for training.

Reach for it to run open models without operating GPUs, and particularly when you want the option to take the weights with you -- fine-tuning that produces a portable artefact is the differentiator against providers that keep the result. The costs are variable latency and throughput on shared capacity unless you pay for dedicated endpoints, and catalogue churn: pin the model string and watch deprecations.

### Tool schema hash
**Short:** Hashing a tool's declared schema so a deploy can detect that its contract changed and invalidate what depended on it.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, ml-lifecycle/drift-and-production-monitoring @3

The schema is serialized canonically and hashed, and the digest is stored alongside whatever was derived from it - the tool's embedding in a retrieval index, a cached routing decision, a golden-output eval. When a deploy produces a different digest the dependents are known to be stale, which is what turns a silent contract change into an explicit invalidation.

Reach for it once the tool catalogue is large enough that nobody can reason about which downstream artifacts a schema edit touches. Canonicalization is where it goes wrong: key order, whitespace and default values must be normalized first, or every deploy looks like a change and the signal is ignored.

### Tool-augmented LLM guide
**Short:** Anthropic's tool-use cookbook: reference guidance on defining tool schemas and structuring a tool-calling loop.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1

What such guidance codifies is the loop and its edges: the model returns a stop reason indicating it wants a tool, you execute and return the result inside a user turn, and you repeat until it stops asking. Details that matter more than they look -- return every parallel tool result in one message rather than splitting them, mark failures as errors rather than dropping them, and never string-match serialised tool input, since escaping varies.

Read it before writing a loop, because the shape is easy to get subtly wrong and the symptoms are confusing. What it will not cover is the operational half you own: timeouts, retries, idempotency for tools with side effects, and an approval gate in front of anything destructive -- a cookbook shows the happy path, and production is mostly the other one.

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

### ToolLLaMA
**Short:** LLaMA fine-tuned on ToolBench for multi-step API calling, released as the reference open tool-use model.
**Kind:** model
**Lang:** *
**Roles:** llm-apps/tool-use-and-mcp @1, model-training/fine-tuning-and-peft @3

It is the model half of the ToolBench release: a LLaMA base tuned on the benchmark's decision-tree-searched solution paths, so it learns to plan several calls and to recover when one returns an error rather than emitting a single call and stopping. A retriever selects candidate tools first, which is what lets it work against a catalogue far larger than the context window.

Reach for it as an open baseline when you are measuring tool use without sending traffic to a hosted model. Frontier models with native tool-calling now exceed it on most tool benchmarks, so its value is reproducibility and self-hosting rather than raw capability.

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

It is a merchant-side specification so a shopping agent can read a catalogue, assemble a cart and complete a purchase through a defined interface rather than by scraping a storefront, with the checkout represented as a signed object each party can verify. Its practical significance comes from backing by a large commerce platform, since merchant adoption is what makes any such protocol real.

Reach for it if you operate a storefront and want agent traffic to convert rather than bounce. The cost is that it is one of several competing agentic-commerce protocols, which puts merchants in the position of either implementing multiple integrations or waiting -- so treat it as a bet on adoption, not a settled standard, and design the integration behind an interface you can repoint.

### Vercel AI SDK
**Short:** TypeScript toolkit for LLM apps: one API over many providers plus React/Next.js streaming and tool-call hooks.
**Kind:** tech
**Lang:** js
**Roles:** llm-apps/llm-gateway-and-routing @1, llm-apps/agent-framework @2, apis-frameworks/rpc-graphql-and-streaming @3

A core layer gives one function set for generation, streaming and schema-validated structured output over pluggable provider packages, and a UI layer gives framework hooks plus a streaming protocol that carries text, tool calls and structured data from a server route to a component -- so the streaming plumbing between the model and the browser is not something you write.

Reach for it for any TypeScript full-stack chat or agent interface, which is where it is hard to beat. Two limits: the abstraction is thin by design, so provider-specific features arrive through an options escape hatch and lag the vendor SDKs, and most of the value is in the UI half -- if the backend is Python and only the front end is JavaScript, the case weakens considerably.

### Vertex AI Agent Engine
**Short:** Google Cloud managed runtime for deployed agents with sessions, memory, autoscaling and integrated evaluation.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/agent-framework @1, platform-delivery/cloud-platform-and-cost @2, ml-lifecycle/ml-platform-and-pipelines @3

It is a managed runtime for agents you wrote yourself: package an agent built with any of the common frameworks or plain Python, deploy it, and the platform runs it behind an endpoint with autoscaling, managed sessions, a memory service that extracts and retrieves user facts across sessions, tracing into the cloud's observability stack, and hooks into the evaluation service.

Reach for it when the agent already lives on Google Cloud and sessions, memory and tracing are the parts you would otherwise build. The costs are coupling and fit: identity, networking and quotas are all platform-shaped, and for a simple stateless agent a container on a serverless runtime with your own session store is cheaper and easier to move.

### XGrammar
**Short:** Fast CFG-constrained decoding engine; the default structured-output backend in vLLM, SGLang and TensorRT-LLM.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @2

Its optimisation is splitting the vocabulary per grammar state into tokens that can be decided from the token alone and tokens that need a stack check: the first group is precomputed into a cached bitmask and the second is a small remainder evaluated at runtime, with mask computation overlapped against GPU work. The result is that constrained decoding costs close to nothing per token, which is why serving engines adopted it as the default.

You meet it as a backend flag rather than a library you call. Two things to keep in view: grammar compilation is real work amortised only when the same schema repeats, so a workload of many one-off schemas has a different cost profile; and it constrains syntax only -- a schema-valid answer can still be entirely wrong.

### yarn
**Short:** YaRN - a RoPE rescaling method extending a model's usable context beyond its training length; a llama.cpp CLI option.
**Kind:** concept
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, inference/inference-engine @3, model-training/fine-tuning-and-peft @3

YaRN rescales rotary position embeddings non-uniformly: high-frequency dimensions are left alone, low-frequency ones are interpolated, and a blend covers the middle, with an adjustment to attention temperature compensating for the entropy change at longer sequences. It reaches a given context extension with far less fine-tuning than uniform interpolation, and can be applied at inference with a scale factor.

Reach for it when you need more usable context from an existing checkpoint and cannot run a full long-context training job. The extension is not free: quality degrades gradually as the scale factor grows, short-context performance can regress, and applying the flag to a model that was never tuned for it produces worse output than leaving it alone -- check whether the checkpoint's configuration already declares a scaling scheme before adding one.

### Zep
**Short:** Long-term memory service for agents building a temporal knowledge graph of entities and facts from conversations.
**Kind:** tech
**Lang:** *
**Roles:** llm-apps/prompting-context-and-structured-output @1, data-stores/graph-db @2, search-retrieval/rag-and-document-processing @3

Rather than embedding chat turns, it builds a temporal knowledge graph: entities and relationships are extracted from conversations and business data, and every fact carries when it became valid and when it was superseded, so a changed preference is dated rather than overwritten and the store can answer both what is true now and what was true then. Retrieval combines semantic search, keyword search and graph traversal.

Reach for it when an assistant must track facts that change over time and contradictory memories would otherwise accumulate. The costs are on the write path: extraction is model work, so ingestion has real latency and cost, and an extraction error becomes a durable wrong edge that retrieval will faithfully surface. For an assistant that only needs the last few turns, a message buffer is far less machinery.
