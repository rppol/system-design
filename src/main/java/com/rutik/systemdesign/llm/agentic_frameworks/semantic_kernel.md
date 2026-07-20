# Semantic Kernel — Deep Dive

---

## 1. Concept Overview

Semantic Kernel (SK) is Microsoft's enterprise-grade SDK for building AI applications. Unlike LangChain (Python-first, research-oriented) or LangGraph (graph-based agents), Semantic Kernel is designed for production enterprise integration: it supports C#, Python, and Java; uses dependency injection patterns familiar to enterprise developers; and emphasizes enterprise concerns — multi-tenancy, audit logging, plugin management, and OpenAPI integration.

Semantic Kernel is the foundation of Microsoft's Copilot Stack, powering GitHub Copilot, Microsoft 365 Copilot, and Bing Chat. This gives it production credibility at scale that few other frameworks match.

**Current version**: semantic-kernel 1.x (Python), 1.x (C#) (2024)
**Production adoption signal**: Powers GitHub Copilot Enterprise, Microsoft 365 Copilot, Bing Chat integration. Salesforce uses SK for Einstein AI. Thousands of enterprise deployments.

---

## 2. Intuition

**One-line analogy**: Semantic Kernel is like a Spring Boot application framework for AI — dependency injection, plugins (beans), planners (orchestrators), and enterprise patterns baked in.

**Mental model**: In a Spring Boot app, you have beans (services), dependency injection, and configuration management. In Semantic Kernel: the `Kernel` is the DI container; plugins are the services (each with semantic functions and native functions); the Kernel invokes them via dependency injection. Enterprise developers adopt SK quickly because the patterns map directly to their existing knowledge.

**Why it matters**: Enterprise teams building production AI systems need: audit trails (who called what prompt, when), plugin management (enable/disable AI capabilities per tenant), multi-model support (switch LLMs per use case), and existing integration patterns (.NET ecosystem, Azure services). Semantic Kernel provides these out of the box; LangChain and LangGraph do not.

**Key insight**: Semantic Kernel's strength is the enterprise integration model — importing an OpenAPI spec as a plugin, using .NET dependency injection for AI services, and running within existing enterprise monitoring infrastructure. Its weakness is that the agent capabilities lag behind LangGraph.

---

## 3. Core Principles

**Kernel as the orchestration hub**: The `Kernel` is the central object. It manages: AI service registrations (multiple LLMs, embedding models), plugin registrations (semantic + native functions), and execution settings. All AI calls go through the Kernel, enabling centralized logging, rate limiting, and filtering.

**Plugins**: The primary unit of capability. A plugin is a class or directory containing: semantic functions (prompt templates with input/output variables) and native functions (regular C#/Python/Java methods). Example: a `MathPlugin` with `Add(a, b)` (native) and `ExplainCalculation` (semantic).

**Semantic functions vs native functions**: Semantic functions are prompt templates with variable substitution — `"Translate {{$text}} to {{$language}}"`. Native functions are regular programming language functions decorated to be SK-callable. Both types can be composed in planners.

**Planners**: Take a user goal and automatically decompose it into a sequence of plugin function calls. `SequentialPlanner` creates a step-by-step plan; `FunctionCallingStepwisePlanner` uses LLM function calling to dynamically generate and execute plans.

**Kernel filters**: Middleware that intercepts function invocations. Used for: logging every AI call, rate limiting, PII detection, cost tracking, and A/B testing prompt variants. This is the enterprise audit trail mechanism.

---

## 4. Types / Architectures / Strategies

### Plugin Types

| Type | Definition | Best For |
|------|-----------|---------|
| Semantic plugin | Prompt template file (`.skprompt.txt` + `config.json`) | LLM-based operations |
| Native plugin | C#/Python/Java class with `[KernelFunction]` decorators | Deterministic operations |
| OpenAPI plugin | Imported from OpenAPI spec | REST API integration |
| Copilot plugin | Compatible with Microsoft Copilot ecosystem | M365 integration |

### Planner Types

| Planner | Strategy | Use Case |
|---------|---------|---------|
| `SequentialPlanner` | Creates a linear step-by-step plan | Known, predictable task structures |
| `FunctionCallingStepwisePlanner` | Uses LLM function calling to plan dynamically | Complex, adaptive tasks |
| `HandlebarsPlanner` | Template-based plan (Handlebars syntax) | Deterministic workflows with branching |

### Memory and Context

| Type | Storage | Use Case |
|------|---------|---------|
| Semantic Memory | Vector store (Azure Cognitive Search, Chroma, etc.) | Knowledge base lookup |
| VolatileMemoryStore | In-memory | Development/testing |
| Chat History | `ChatHistory` object | Conversation context |

---

## 5. Architecture Diagrams

### Kernel Architecture

```
                        ┌─────────────────────────────────────────┐
                        │                  Kernel                  │
                        │                                         │
┌──────────────┐        │  ┌─────────────┐  ┌─────────────────┐  │
│  Chat OpenAI │◄──────►│  │  AI Services│  │     Plugins     │  │
│  Claude 3    │        │  │  Registry   │  │  ┌───────────┐  │  │
│  Gemini      │        │  │             │  │  │  Math     │  │  │
└──────────────┘        │  └─────────────┘  │  │  Weather  │  │  │
                        │                   │  │  Database │  │  │
┌──────────────┐        │  ┌─────────────┐  │  │  Email    │  │  │
│  Kernel      │◄──────►│  │   Filters   │  │  └───────────┘  │  │
│  Filters     │        │  │  (logging,  │  └─────────────────┘  │
│  (logging,   │        │  │  rate limit)|                        │
│  audit)      │        │  └─────────────┘                        │
└──────────────┘        └─────────────────────────────────────────┘
                                        │
                                        ▼
                               ┌──────────────┐
                               │   Planner     │
                               │  (SequentialP)|
                               └──────────────┘
                                        │
                          Goal: "Write and email a report"
                                        │
                                        ▼
                               Plan:
                               1. WritePlugin.DraftReport(topic)
                               2. MathPlugin.CalculateStats(data)
                               3. EmailPlugin.SendEmail(to, subject, body)
```

### Plugin Execution Flow

```
User Request: "Summarize this document in French"

kernel.invoke("SummaryPlugin", "Summarize", input_vars)
        |
        v
  Kernel Filters (pre-invocation):
    - Log: function=Summarize, user=alice, timestamp=...
    - Rate limit check: alice has 50/100 calls remaining
    - PII check: no sensitive data detected
        |
        v
  Retrieve AI Service (ChatOpenAI)
  Load semantic function template:
    "Summarize the following text in {{$language}}:\n{{$input}}"
  Substitute variables:
    "Summarize the following text in French:\n[document text]"
        |
        v
  Call OpenAI API
        |
        v
  Kernel Filters (post-invocation):
    - Log: tokens=523, cost=$0.003, latency=1.2s
    - Save to audit log
        |
        v
  Return: FunctionResult("Le document décrit...")
```

---

## 6. How It Works — Detailed Mechanics

### Kernel Setup (Python)

```python
import semantic_kernel as sk
from semantic_kernel.connectors.ai.open_ai import OpenAIChatCompletion, OpenAITextEmbedding
from semantic_kernel.contents import ChatHistory

# Create kernel
kernel = sk.Kernel()

# Register AI services (can register multiple)
kernel.add_service(
    OpenAIChatCompletion(
        service_id="gpt-4o",
        ai_model_id="gpt-4o",
        api_key=os.environ["OPENAI_API_KEY"]
    )
)

kernel.add_service(
    OpenAIChatCompletion(
        service_id="gpt-4o-mini",  # cheaper model for simple tasks
        ai_model_id="gpt-4o-mini",
        api_key=os.environ["OPENAI_API_KEY"]
    )
)

# Invoke a semantic function inline
result = await kernel.invoke_prompt(
    "Translate '{{$text}}' to {{$language}}",
    arguments=sk.KernelArguments(text="Hello world", language="Spanish")
)
print(str(result))  # "Hola mundo"
```

### Native Plugin

```python
from semantic_kernel.functions import kernel_function
from semantic_kernel.plugin_definition import kernel_plugin

@kernel_plugin(description="Math operations plugin")
class MathPlugin:
    @kernel_function(
        name="add",
        description="Add two numbers together"
    )
    def add(self, a: float, b: float) -> float:
        return a + b

    @kernel_function(
        name="calculate_percentage",
        description="Calculate what percentage value_a is of value_b"
    )
    def calculate_percentage(self, value_a: float, value_b: float) -> str:
        if value_b == 0:
            return "Error: cannot divide by zero"
        pct = (value_a / value_b) * 100
        return f"{pct:.1f}%"

# Register and use
kernel.add_plugin(MathPlugin(), plugin_name="Math")
result = await kernel.invoke("Math", "add", a=5.0, b=3.0)
print(float(str(result)))  # 8.0
```

### Semantic Plugin from Files

```python
# File structure:
# plugins/
#   SummaryPlugin/
#     Summarize/
#       skprompt.txt   ← prompt template
#       config.json    ← execution settings

# skprompt.txt:
# Summarize the following content in {{$style}} style.
# Keep it under {{$max_words}} words.
#
# CONTENT:
# {{$input}}

# config.json:
# {
#   "description": "Summarize content in specified style",
#   "input_variables": [
#     {"name": "input", "description": "Content to summarize"},
#     {"name": "style", "description": "Summary style: formal, casual, bullet-points"},
#     {"name": "max_words", "description": "Maximum word count", "default_value": "100"}
#   ],
#   "execution_settings": {
#     "default": {"temperature": 0.3, "max_tokens": 500}
#   }
# }

# Load the entire plugin directory
summary_plugin = kernel.add_plugin(
    plugin_name="Summary",
    parent_directory="./plugins"
)

# Invoke
result = await kernel.invoke(
    "Summary",
    "Summarize",
    sk.KernelArguments(
        input="[long article text]",
        style="bullet-points",
        max_words="150"
    )
)
```

### OpenAPI Plugin Import

```python
from semantic_kernel.plugins.open_api_plugin.open_api_manager import OpenApiKernelPluginFactory

# Import REST API from OpenAPI spec
weather_plugin = await OpenApiKernelPluginFactory.create_kernel_plugin_from_openapi(
    kernel=kernel,
    plugin_name="Weather",
    openapi_document_path="weather_api_openapi.yaml",
    execution_parameters={
        "server_url_override": "https://api.openweathermap.org",
        "auth_callback": lambda: {"Authorization": f"Bearer {os.environ['WEATHER_API_KEY']}"}
    }
)

kernel.add_plugin(weather_plugin)

# Now the entire REST API is callable as SK functions
result = await kernel.invoke(
    "Weather",
    "GetCurrentWeather",
    sk.KernelArguments(city="San Francisco", units="imperial")
)
```

### Kernel Filters (Audit + Cost Tracking)

```python
from semantic_kernel.filters import FunctionInvocationContext
from semantic_kernel.filters.functions.function_invocation_filter_base import FunctionInvocationFilterBase

class AuditFilter(FunctionInvocationFilterBase):
    def __init__(self, audit_logger):
        self.logger = audit_logger

    async def on_function_invocation(self, context: FunctionInvocationContext, next):
        # Pre-invocation
        start_time = time.time()
        self.logger.log_invocation(
            function=context.function.fully_qualified_name,
            user=context.kernel_arguments.get("user_id"),
            inputs=context.kernel_arguments
        )

        try:
            # Call the actual function
            await next(context)
        except Exception as e:
            self.logger.log_error(str(e))
            raise
        finally:
            # Post-invocation
            latency = (time.time() - start_time) * 1000
            self.logger.log_completion(
                function=context.function.fully_qualified_name,
                latency_ms=latency,
                result=str(context.result)
            )

# Register filter with kernel
kernel.add_filter("function_invocation", AuditFilter(audit_logger))
```

### Chat with History

```python
from semantic_kernel.contents import ChatHistory
from semantic_kernel.connectors.ai.open_ai import OpenAIChatPromptExecutionSettings

chat_history = ChatHistory()
chat_history.add_system_message(
    "You are a helpful enterprise assistant. Keep responses professional and concise."
)

async def chat(user_message: str) -> str:
    chat_history.add_user_message(user_message)

    settings = OpenAIChatPromptExecutionSettings(
        service_id="gpt-4o",
        temperature=0.2,
        max_tokens=1000
    )

    result = await kernel.get_service("gpt-4o").get_chat_message_content(
        chat_history=chat_history,
        settings=settings,
        kernel=kernel
    )

    chat_history.add_assistant_message(str(result))
    return str(result)

# Multi-turn conversation
response1 = await chat("What are the key features of Semantic Kernel?")
response2 = await chat("Which of those features are most relevant for enterprise use?")
```

### FunctionCallingStepwisePlanner

```python
from semantic_kernel.planners import FunctionCallingStepwisePlanner

# Add plugins for the planner to use
kernel.add_plugin(MathPlugin(), "Math")
kernel.add_plugin(WeatherPlugin(), "Weather")
kernel.add_plugin(EmailPlugin(), "Email")

planner = FunctionCallingStepwisePlanner(service_id="gpt-4o")

# Planner creates a plan, executes step by step
result = await planner.invoke(
    kernel,
    "Get the weather in Seattle, calculate how many degrees that is above/below 70F, "
    "and email the result to weather@company.com"
)

print(result.final_answer)
print(result.chat_history)  # see the step-by-step execution
```

### C# Example (Enterprise Pattern)

```csharp
using Microsoft.SemanticKernel;
using Microsoft.SemanticKernel.ChatCompletion;
using Microsoft.SemanticKernel.Connectors.OpenAI;

// Dependency injection setup (ASP.NET Core)
builder.Services.AddKernel()
    .AddOpenAIChatCompletion("gpt-4o", Environment.GetEnvironmentVariable("OPENAI_API_KEY"))
    .Plugins.AddFromType<DatabasePlugin>("Database")
    .Plugins.AddFromType<EmailPlugin>("Email");

// Register custom filter
builder.Services.AddSingleton<IFunctionInvocationFilter, AuditLoggingFilter>();

// In a controller
[ApiController]
public class AIController : ControllerBase
{
    private readonly Kernel _kernel;

    public AIController(Kernel kernel) => _kernel = kernel;

    [HttpPost("analyze")]
    public async Task<IActionResult> Analyze([FromBody] AnalyzeRequest req)
    {
        var result = await _kernel.InvokeAsync<string>(
            "Database",
            "QueryAndSummarize",
            new KernelArguments { ["query"] = req.Query }
        );
        return Ok(new { result });
    }
}
```

---

## 7. Real-World Examples

**Microsoft 365 Copilot**: SK is the foundation of M365 Copilot. Plugins represent Office365 services (Outlook, Teams, SharePoint). SK orchestrates: "draft an email summarizing the meeting transcript from Teams and send to the attendees" — involves Teams plugin (get transcript), summarize semantic function, Outlook plugin (send email).

**GitHub Copilot Enterprise**: SK powers code review, PR summaries, and repository-level Q&A. Custom plugins wrap GitHub API calls; semantic functions handle code explanation and review generation.

**Salesforce Einstein**: Microsoft partnership brought SK into Salesforce's AI platform. CRM data access via OpenAPI-imported Salesforce REST API; SK's multi-tenant model manages per-org API credentials.

**Healthcare enterprise**: Hospital system built patient intake automation using SK in C#: pull patient history (native plugin → EHR API), generate summary (semantic function), route to specialist (classification plugin). Full audit trail via Kernel filters for HIPAA compliance.

**Banking chatbot**: Large bank uses SK for internal employee assistant. Plugins: policy document search (semantic memory), HR systems (OpenAPI), IT helpdesk (native). The enterprise patterns (DI, audit filters, Azure AD integration) mapped directly to their existing Java/C# infrastructure.

---

## 8. Tradeoffs

| Dimension | Semantic Kernel | LangChain | LangGraph |
|-----------|----------------|-----------|-----------|
| Enterprise patterns | Excellent (DI, filters, audit) | Limited | Limited |
| Multi-language | C#, Python, Java | Python only | Python only |
| Agent sophistication | Medium | Good | Excellent |
| Learning curve | High (many abstractions) | Medium | High |
| Azure integration | Excellent (native) | Good | Good |
| OpenAPI plugin import | First-class | Manual | Manual |
| Community/OSS ecosystem | Medium | Largest | Large |
| Production examples | Microsoft scale | Many startups | Many startups |
| Planner quality | Good | N/A (use LangGraph) | N/A |

**When SK wins:**
- .NET/C# enterprise shops
- Azure-first deployments
- Needing audit trails and Kernel filters
- M365/Copilot integration
- Multi-language codebase (C# backend + Python ML)

---

## 9. When to Use / When NOT to Use

**Use Semantic Kernel when:**
- Enterprise .NET/C# shop — SK's C# SDK is best-in-class
- Building on Azure (native Azure OpenAI, Azure Cognitive Search, Azure AD integrations)
- Need enterprise audit trail (Kernel filters for every AI call)
- Integrating with existing REST APIs (OpenAPI plugin import)
- Building on Microsoft's Copilot Stack ecosystem
- Multi-language support required (frontend in Python, backend in C#)

**Do NOT use Semantic Kernel when:**
- Complex stateful agents with loops and checkpointing — [LangGraph](langgraph.md) is more capable
- Python-first team with no enterprise requirements — [LangChain](langchain_and_lcel.md)/LangGraph have better Python tooling
- Need maximum open-source community integrations — LangChain's ecosystem is larger
- Agentic patterns beyond simple planning — SK's agent capabilities are less mature

---

## 10. Common Pitfalls

**Pitfall 1: Confusing semantic functions with native functions**
Teams write everything as semantic functions (prompt templates) even for deterministic operations. Native functions are better for: math, date formatting, string manipulation, API calls. Semantic functions for everything inflates token usage and introduces non-determinism where determinism is needed.

**Pitfall 2: Planner hallucination**
`SequentialPlanner` uses the LLM to create a plan from plugin descriptions. If plugin descriptions are vague, the planner picks the wrong function or creates an invalid plan. Example: `description="process data"` is too vague — the planner may choose this for unrelated tasks. Requirement: every plugin function must have a specific, unique description: `"Calculate compound interest given principal, rate, and years"`.

**Pitfall 3: Not using Kernel filters for security**
Production SK deployments without filters have no audit trail. One enterprise customer deployed SK without filters, then had to reconstruct what happened in a compliance audit by querying LLM provider logs. Add `IFunctionInvocationFilter` on day 1 for logging; it takes 30 minutes and saves hours of post-incident forensics.

**Pitfall 4: Memory store misconfiguration**
Semantic Memory with `VolatileMemoryStore` is reset on every application restart. Production must use persistent stores (Azure Cognitive Search, Chroma, Pinecone). Teams prototype with volatile memory, deploy to production with the same config, and wonder why the chatbot forgets everything.

```python
# BROKEN: prototype config shipped to production — every deploy/restart wipes all memories
from semantic_kernel.memory import VolatileMemoryStore
memory_store = VolatileMemoryStore()  # in-process dict; gone on pod restart

# FIXED: persistent, service-backed store for production
from semantic_kernel.connectors.memory.azure_ai_search import AzureAISearchMemoryStore
memory_store = AzureAISearchMemoryStore(
    search_endpoint=os.environ["AZURE_SEARCH_ENDPOINT"],
    admin_key=os.environ["AZURE_SEARCH_KEY"],
)  # memories survive restarts, scale across replicas
```

**Pitfall 5: Planner over-generation**
`FunctionCallingStepwisePlanner` can generate 10+ step plans for simple tasks. Each step is an LLM call. A "summarize this document" task should be 1 step; the planner sometimes generates: translate → clean text → extract topics → format → summarize (5 steps × $0.005 = $0.025 per call). Use direct function invocation (`kernel.invoke(...)`) for known, simple tasks; reserve planners for genuinely complex multi-step coordination.

**Put simply.** "A planner charges you one LLM call per step it invents, plus one to invent them — so the cost of a task is decided by the model's imagination rather than by the task."

| Symbol | What it is |
|--------|------------|
| `s` | Steps the planner generated — model-chosen, not specified by you |
| `p` | Cost per step, $0.005 at this model and prompt size |
| `s x p` | Execution cost of the plan |
| `s_min` | Steps the task actually required, 1 for "summarize this document" |
| `kernel.invoke()` | Direct invocation — skips planning entirely, fixes `s` at 1 |

**Walk one example.** The over-generated plan against the direct call:

```
  planner path
    translate + clean + extract + format + summarize   =  5 steps
    5 x $0.005                                         =  $0.025 per call
    (plus the planning call itself, typically ~$0.005) =  $0.030

  direct kernel.invoke() path
    1 x $0.005                                         =  $0.005

  overspend   $0.025 / $0.005                          =  5x
  waste       (0.025 - 0.005) / 0.025                  =  80%

  at 10,000 calls/day
    planner    10,000 x $0.025                         =  $250 / day
    direct     10,000 x $0.005                         =  $ 50 / day
    annual difference  ($250 - $50) x 365              =  $73,000
```

Four of those five steps do nothing a summarizer would not do internally — translating text
that is already English, "cleaning" text that is already clean. The planner is not
malfunctioning; it is doing what it was asked, which is to decompose. Decomposition applied
to a task that needs none is pure overhead, and at volume it is $73,000/year of it.

**Why latency compounds the problem worse than cost.** The five steps run sequentially, each
waiting on the last, so a 1.2s task becomes ~6s. Unlike the parallel tool case elsewhere in
this section, planner steps are genuinely dependent — step 3 consumes step 2's output — so
there is no `max` to fall back to. You cannot parallelize your way out of an
over-decomposed plan; you have to not generate it.

---

## 11. Technologies & Tools

| Tool | Category | Notes |
|------|----------|-------|
| `semantic-kernel` (Python) | Framework | `pip install semantic-kernel>=1.0` |
| `Microsoft.SemanticKernel` (NuGet) | Framework | C# package |
| Azure OpenAI | LLM provider | Native SK integration |
| Azure Cognitive Search | Vector store | Semantic Memory backend |
| `semantic-kernel-azure-ai-inference` | Azure AI integration | Phi-3, Llama 3 via Azure |
| `sk-nightly` | Nightly builds | Latest features (unstable) |

**Version notes:**
- SK 0.x (2023): preview, frequent breaking changes
- SK 1.0.x (early 2024): stable API for C# and Python
- SK 1.x Python: `semantic_kernel.functions` module structure, async-first

---

## 12. Interview Questions with Answers

**Q: What is Semantic Kernel and how does it differ from LangChain?**
Semantic Kernel is Microsoft's enterprise-grade AI SDK supporting C#, Python, and Java, designed around enterprise patterns: dependency injection, plugin management, audit filters, and Azure integrations. LangChain is Python-first with a larger open-source ecosystem but fewer enterprise-specific features. Key differences: SK has first-class C# support (critical for .NET enterprises), native OpenAPI plugin import, Kernel filters for audit trails, and tighter Azure integration. LangChain has more community integrations, better agent patterns (LCEL, LangGraph), and a larger Python community. Choose SK for enterprise .NET/Azure deployments; LangChain for Python-first or complex agent patterns.

**Q: What is a plugin in Semantic Kernel?**
A plugin is a collection of functions (semantic and native) that represent a capability. Semantic functions are prompt templates stored as text files with variable substitution. Native functions are C#/Python/Java methods decorated to be SK-callable. A `WeatherPlugin` might have a native function `GetTemperature(city)` (calls weather API) and a semantic function `DescribeWeather` (generates natural language description from temperature data). Plugins are the unit of capability in SK — analogous to tools in LangChain or skills in Copilot.

**Q: What is a Kernel filter and why is it important for enterprise deployments?**
Kernel filters are middleware that intercepts function invocations (pre and post). They receive the `FunctionInvocationContext` with: function name, input arguments, and (after execution) the result. Enterprise use cases: (1) Audit logging — log every AI call with user identity, inputs, outputs, latency, and cost; (2) PII detection — scan inputs for sensitive data before sending to LLM; (3) Rate limiting — enforce per-user or per-tenant usage limits; (4) Cost tracking — accumulate token usage per department or project; (5) A/B testing — route % of calls to different prompt variants. Filters are the enterprise audit trail mechanism that compliance teams require.

**Q: What is the difference between SequentialPlanner and FunctionCallingStepwisePlanner?**
`SequentialPlanner` generates a complete step-by-step plan upfront (as XML/JSON) using the LLM, then executes each step. It requires the LLM to see all available functions and plan the entire sequence before executing anything — prone to planning hallucinations on complex tasks. `FunctionCallingStepwisePlanner` uses native LLM function calling to decide each step dynamically: after each step, the LLM sees the result and decides the next function to call. More adaptive but more expensive (one LLM call per step). Use `SequentialPlanner` when the task structure is known; use `FunctionCallingStepwisePlanner` when the task requires adaptive reasoning.

**Q: How do you import a REST API as a Semantic Kernel plugin?**
Use `OpenApiKernelPluginFactory.create_kernel_plugin_from_openapi()` with the path to an OpenAPI YAML/JSON spec. SK parses the spec, creates one SK function per API endpoint (named by `operationId`), and registers the plugin with the Kernel. The functions accept the endpoint's parameters as SK arguments. Authentication is configured via `execution_parameters`. This pattern enables any REST API to become an AI-callable plugin without writing wrapper code — a significant productivity gain for enterprise integrations with existing APIs.

**Q: How does Semantic Kernel's memory system work?**
Semantic Memory stores text and vector embeddings for semantic search. Add memories: `kernel.memory.save_information(collection="knowledge_base", id="doc1", text="...")` — this embeds the text and stores it. Query memories: `results = await kernel.memory.search("knowledge_base", "what is the return policy?", limit=3)` — embeds the query, retrieves nearest neighbors. Memory backends: `VolatileMemoryStore` (in-memory, dev only), `AzureAISearchMemoryStore` (production Azure), `ChromaMemoryStore`, `SqliteMemoryStore`. The memory system is simpler than LlamaIndex's advanced RAG — sufficient for most enterprise Q&A but lacks advanced retrieval strategies.

**Q: How does multi-model support work in Semantic Kernel?**
Register multiple AI services with different `service_id`s:
```python
kernel.add_service(OpenAIChatCompletion(service_id="gpt-4o", ...))
kernel.add_service(OpenAIChatCompletion(service_id="gpt-4o-mini", ...))
```
Semantic functions specify which service to use in `config.json` or execution settings. Planners default to a designated "planner model." Use cheap models for classification and routing; expensive models for complex reasoning. Multi-model support enables: cost optimization (GPT-4o-mini for simple tasks), fallback (if primary fails, use secondary), and model routing by capability.

**Q: How do you build a multi-tenant AI application with Semantic Kernel?**
Multi-tenancy in SK: (1) Per-tenant Kernel instances — create a `Kernel` per tenant with tenant-specific plugins, API keys, and settings; this provides strong isolation but higher memory usage; (2) Shared Kernel with per-request context — use `KernelArguments` to pass tenant ID; Kernel filters route to tenant-specific configurations; (3) Per-tenant memory collections — prefix memory collection names with tenant ID; ensures data isolation. Kernel filters are critical for multi-tenancy: enforce that tenant A cannot access tenant B's data, log all cross-tenant operations, and implement tenant-level rate limits.

**Q: What is the Copilot Stack and where does Semantic Kernel fit?**
The Copilot Stack is Microsoft's architecture for building AI-powered applications within the Microsoft ecosystem. Layers: Foundation Models (Azure OpenAI, GPT-4o), AI Orchestration (Semantic Kernel), Copilot Extensions (plugins), and Copilot Experience (M365, GitHub, Bing). SK sits in the AI Orchestration layer — it connects foundation models to application logic through plugins and planners. Building on SK means your application can potentially integrate with M365 Copilot as a plugin, though this requires additional Copilot Studio configuration.

**Q: How do you test a Semantic Kernel application?**
Testing approach: (1) Unit test native plugins as plain C#/Python functions — no SK dependency; (2) Unit test semantic functions by mocking the `IChatCompletionService` in C# or patching the OpenAI client in Python; (3) Integration test with a cheap model (GPT-4o-mini) and `max_tokens=200` to keep tests fast and cheap; (4) Planner testing: provide a mock function catalog and verify the planner generates the expected plan structure. In C#: use `IKernelBuilder` with test doubles. Key: test the logic of what SK should do, not SK's internal behavior.

**Q: How does SK's C# SDK differ from the Python SDK?**
C# SDK: mature (longer development), uses .NET DI for configuration, async/await first-class, strong typing throughout. Python SDK: more recent, fewer features, but growing. Key differences: C# has `IKernelBuilder` for DI setup; Python uses `sk.Kernel()` directly. C# has `[KernelFunction]` attribute for native functions; Python uses `@kernel_function` decorator. C# plugin loading uses reflection; Python uses direct class registration. For enterprise .NET shops: C# is recommended. For ML-heavy Python teams: Python SDK. The APIs are designed to be conceptually consistent across languages — knowledge transfers.

**Q: What are the main limitations of Semantic Kernel for building agents?**
SK's agent capabilities lag behind LangGraph: (1) No built-in state machine — SK's planners don't support complex conditional branching or loops; (2) No checkpointing — long-running agent tasks cannot be paused and resumed; (3) Limited streaming of intermediate agent steps to UI; (4) Planner reliability — for complex multi-step plans, the LLM-generated plan often has errors; (5) No native multi-agent coordination beyond basic GroupChat patterns. For complex agentic tasks: combine SK's plugin/filter infrastructure with a custom state machine or use LangGraph. SK's strength is in the integration and enterprise patterns, not agent orchestration.

**Q: How do you handle multi-language support in SK responses?**
Three approaches: (1) System message: "Always respond in {{$language}}" injected via `ChatHistory.AddSystemMessage()` — simplest, but model may ignore for complex technical content; (2) Semantic function with language variable: `"Respond in {{$language}}: {{$question}}"` — explicit, usually reliable with GPT-4; (3) Post-processing translation: generate response in English, then invoke a translation semantic function; most reliable for accuracy but doubles LLM calls. For enterprise multi-lingual deployments: approach 3 with caching (cache translations for repeated phrases) balances reliability and cost.

**Q: How does Semantic Kernel's plugin architecture differ from LangChain's tool abstraction?**
Semantic Kernel plugins are strongly typed classes with annotated methods (KernelFunction attribute), providing compile-time type safety and IDE support — particularly advantageous in C# and Java enterprise environments. LangChain tools are Python functions wrapped with decorators, offering more flexibility but less type safety. SK's approach enables automatic function signature extraction for the model's function calling schema. Choose SK for enterprise .NET/Java projects where type safety and existing SDK integration matter; choose LangChain for Python-first rapid prototyping.

**Q: How do you implement multi-step planning with Semantic Kernel's Planner?**
SK's planner takes a user goal and available plugins, then generates a plan (sequence of function calls) to achieve it. The Handlebars planner generates a Handlebars template with function calls; the Stepwise planner executes functions iteratively with ReAct-style reasoning. Key configuration: limit the number of available functions presented to the planner (exposing 50+ functions degrades plan quality), set max iterations (default 10), and implement plan validation before execution. The planner works best with well-described function parameters and clear function names.

---

## 13. Best Practices

1. **Use C# for .NET enterprise deployments** — the C# SDK is more mature and better integrated with .NET DI.
2. **Register Kernel filters on day 1** — audit logging is a compliance requirement, not an afterthought.
3. **Write specific plugin descriptions** — vague descriptions cause planner failures; every function description must be unique and precise.
4. **Use native functions for deterministic operations** — reserve semantic functions for tasks requiring LLM reasoning.
5. **Use persistent memory stores in production** — `VolatileMemoryStore` is for testing only.
6. **Register multiple AI services** — use GPT-4o-mini for simple tasks, GPT-4o for complex ones; configure per semantic function.
7. **Import OpenAPI specs instead of writing wrappers** — any REST API becomes a plugin in minutes.
8. **Use direct `kernel.invoke()` for known tasks** — only use planners when task structure is genuinely unknown.
9. **Isolate plugin side effects** — plugins that write to databases or send emails must be clearly documented; filter-based confirmation for destructive operations.
10. **Version semantic functions** — treat prompt templates as code; use git versioning; have a rollback plan.

---

## 14. Case Study: Enterprise HR Copilot

**Scenario**: A 10,000-person enterprise wants an internal HR Copilot accessible to all employees. Questions cover: company policies, benefits, PTO calculations, org chart, and IT helpdesk. Must integrate with existing HR system (Workday), policy documents (SharePoint), and helpdesk (ServiceNow). Requires full audit trail for compliance. .NET shop, Azure infrastructure.

### Architecture

```
Employee: "How many PTO days do I have left and what's the procedure to request time off?"

ASP.NET Core API (SK Kernel per request)
  |
  v
AuditLoggingFilter (logs: user, query, timestamp)
PiiDetectionFilter (scans for SSN, salary data in query)
  |
  v
FunctionCallingStepwisePlanner
  |
  v
Step 1: WorkdayPlugin.GetEmployeePTO(employee_id)
  → calls Workday REST API (OpenAPI-imported plugin)
  → returns: {"vacation_days_remaining": 8, "sick_days_remaining": 12}
  |
  v
Step 2: PolicyPlugin.SearchPolicies(query="PTO request procedure")
  → Azure AI Search (semantic memory)
  → returns: relevant policy document chunks
  |
  v
Step 3: SynthesisPlugin.GenerateResponse(pto_data, policy_text)
  → GPT-4o semantic function
  → returns: "You have 8 vacation days remaining. To request time off, log in to Workday..."
  |
  v
AuditLoggingFilter (logs: response, tokens=423, cost=$0.003, latency=2.1s)
  |
  v
Employee receives answer
```

### C# Implementation

```csharp
// Startup.cs
builder.Services.AddKernel()
    .AddAzureOpenAIChatCompletion("gpt-4o", azureEndpoint, apiKey)
    .Plugins.AddFromType<WorkdayPlugin>("Workday")
    .Plugins.AddFromType<PolicyPlugin>("Policy")
    .Plugins.AddFromType<ServiceNowPlugin>("HelpDesk");

builder.Services.AddSingleton<IFunctionInvocationFilter, ComplianceAuditFilter>();
builder.Services.AddSingleton<IFunctionInvocationFilter, PIIDetectionFilter>();

// HRController.cs
[HttpPost("ask")]
[Authorize]
public async Task<IActionResult> Ask([FromBody] HRQuery query)
{
    var employee = await _employeeService.GetCurrentEmployee(User);
    var arguments = new KernelArguments
    {
        ["employee_id"] = employee.Id,
        ["department"] = employee.Department,
        ["user_id"] = employee.Id  // used by audit filter
    };

    var planner = new FunctionCallingStepwisePlanner(
        new FunctionCallingStepwisePlannerOptions { MaxIterations = 5 }
    );

    var result = await planner.ExecuteAsync(_kernel, query.Question, arguments);
    return Ok(new { answer = result.FinalAnswer, steps = result.Iterations });
}
```

### Results

| Metric | Before (HR help desk) | After (HR Copilot) |
|--------|----------------------|-------------------|
| PTO-related queries handled automatically | 0% | 91% |
| Average resolution time | 4 hours | 8 seconds |
| HR team capacity freed | 0 hours | 20 hours/week |
| Compliance audit trail | Manual logs | Automatic (Kernel filter) |
| PII incidents | N/A | 0 (PII filter blocks before LLM) |
| Employee satisfaction | 3.2/5 | 4.6/5 |
