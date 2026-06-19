# Spring AI

## 1. Concept Overview

Spring AI is the Spring portfolio's framework for building AI/LLM-powered applications
with the same idioms Spring developers already know: dependency injection, auto-configuration,
starters, fluent builders, and portable abstractions over vendor APIs. It reached **1.0 GA in
2025** (baseline Spring Boot 3.3/3.4, Java 17+) and provides a `ChatClient` fluent API, portable
model abstractions, a `VectorStore` SPI for retrieval-augmented generation (RAG), embeddings,
tool/function calling, structured output mapping, chat memory, and Micrometer observability.

Its central goal is **portability and integration**: swap OpenAI for Anthropic Claude, Ollama,
Azure OpenAI, Amazon Bedrock, or Google Vertex AI by changing a starter dependency and
properties — not application code. It bridges this Java/Spring section to the
[`llm/`](../../llm/) engineering material: the LLM section explains *how the models and RAG
pipelines work*; this module explains *how to wire them into a Spring service*.

Key building blocks:
- **`ChatClient`** — fluent, `RestClient`-style API for prompting a chat model.
- **`ChatModel` / `EmbeddingModel`** — portable low-level model interfaces with per-vendor implementations.
- **`Advisor`** — interceptor chain around `ChatClient` calls (RAG, chat memory, logging) — the AOP-of-prompts.
- **`VectorStore`** — SPI over PgVector, Redis, Chroma, Milvus, etc., for similarity search.
- **`PromptTemplate`** — parameterized prompt rendering (StringTemplate/Mustache-style placeholders).
- **Structured output** — map model responses directly to Java records/beans.
- **Tools (`@Tool`)** — let the model call your Java methods (function calling).
- **ETL pipeline** — `DocumentReader` → `TextSplitter` → `VectorStore` for ingestion.

---

## 2. Intuition

> **One-line analogy:** Spring AI is to LLM providers what Spring Data is to databases and
> what `RestClient` is to HTTP — a thin, portable, auto-configured abstraction so your code
> talks to "a chat model," not "OpenAI's REST API."

**Mental model:** A `ChatClient` is a configured bean. You build a request fluently
(`chatClient.prompt().system(...).user(...).call()`), and a chain of **advisors** wraps that
call — one advisor retrieves relevant documents from a `VectorStore` and stuffs them into the
prompt (RAG), another injects conversation history (chat memory), another records metrics.
The result is parsed into a `String`, a Java record, or a `Flux<String>` stream.

**Why it matters:** Without a framework, every team hand-rolls HTTP clients, retry logic,
prompt string-building, JSON parsing, and vendor lock-in. Spring AI gives the *same*
auto-configuration, testability (`@AutoConfigureTestDatabase`-style), and observability story
that made Spring Boot dominant, applied to AI workloads — so an LLM feature looks like any
other Spring service, not a bespoke integration.

**Key insight:** Spring AI does not reinvent LLM concepts; it *adapts* them to Spring's
proven patterns. Advisors are AOP for prompts; `VectorStore` is a `Repository` for embeddings;
starters auto-configure the model bean from properties. Everything you know about Spring DI,
conditional beans, and `@ConfigurationProperties` carries over.

---

## 3. Core Principles

1. **Portability over lock-in.** Program against `ChatModel`/`ChatClient`/`VectorStore`, not a vendor SDK; switch providers via starter + properties.
2. **Auto-configuration drives wiring.** A `spring-ai-*-spring-boot-starter` plus an API key in properties auto-configures the model bean — `@ConditionalOnClass`/`@ConditionalOnMissingBean` let you override.
3. **Advisors compose cross-cutting prompt concerns.** RAG, chat memory, safety guards, and logging are advisors layered around a call — the same interceptor model as Spring AOP / `HandlerInterceptor`.
4. **Structured output closes the loop with the type system.** Responses map to records via `BeanOutputConverter`, turning free-text LLM output into typed Java objects.
5. **RAG = retrieval + augmentation as a pipeline.** Ingest (ETL) into a `VectorStore`, then at query time retrieve top-k similar chunks and inject them as context.
6. **Tools invert control to the model.** With `@Tool`, the model decides to call your Java method; Spring AI handles the function-calling round trip.
7. **Observability is first-class.** Every model and vector-store call emits Micrometer observations (latency, token usage) with the same `Observation` API as the rest of Boot 3.

---

## 4. Types / Architectures / Strategies

### 4.1 Core Abstractions

| Abstraction | Analogous to | Responsibility |
|---|---|---|
| `ChatModel` | `JdbcTemplate` | Low-level synchronous/streaming model call |
| `ChatClient` | `RestClient` | Fluent, advisor-aware client over a `ChatModel` |
| `EmbeddingModel` | — | Text → float vector |
| `VectorStore` | `Repository` | Store + similarity-search embeddings |
| `Advisor` | `HandlerInterceptor` / AOP advice | Wrap a `ChatClient` call |
| `PromptTemplate` | `MessageSource` | Render parameterized prompts |
| `ChatMemory` | `HttpSession` | Persist conversation turns |

### 4.2 Supported Model Providers (starters)

| Provider | Starter artifact | Notes |
|---|---|---|
| OpenAI | `spring-ai-openai-spring-boot-starter` | Chat, embedding, image, audio |
| Anthropic (Claude) | `spring-ai-anthropic-spring-boot-starter` | Chat + tools; use the current Claude model id in properties |
| Ollama | `spring-ai-ollama-spring-boot-starter` | Local models, no API key |
| Azure OpenAI | `spring-ai-azure-openai-spring-boot-starter` | Enterprise Azure deployment |
| Amazon Bedrock | `spring-ai-bedrock-*` | Multiple model families via AWS |
| Google Vertex AI | `spring-ai-vertex-ai-gemini-spring-boot-starter` | Gemini family |

### 4.3 RAG Strategies

| Strategy | How | When |
|---|---|---|
| Naive RAG (`QuestionAnswerAdvisor`) | Single top-k retrieval, stuff context | Simple FAQ/docs |
| Advanced RAG (`RetrievalAugmentationAdvisor`) | Query transform → retrieve → rerank → augment | Quality-critical, large corpora |
| Tool-based retrieval | Model calls a `@Tool` search method on demand | Agentic, multi-step |

### 4.4 Output Strategies

| Strategy | API | Result |
|---|---|---|
| Raw text | `.call().content()` | `String` |
| Structured | `.call().entity(MyRecord.class)` | Typed Java object |
| Streaming | `.stream().content()` | `Flux<String>` (token stream) |
| Collection | `.call().entity(new ParameterizedTypeReference<List<X>>(){})` | `List<X>` |

---

## 5. Architecture Diagrams

### ChatClient request through the advisor chain

```
  chatClient.prompt()
     .user("How do I reset my password?")
     .call().content()
        |
        v
  +--------------------- Advisor chain (ordered) ---------------------+
  | SafeGuardAdvisor  ->  QuestionAnswerAdvisor  ->  ChatMemoryAdvisor |
  |   (block unsafe)      (RAG: retrieve top-k        (inject history)  |
  |                        from VectorStore,                            |
  |                        augment prompt)                              |
  +-------------------------------+----------------------------------- +
                                  v
                            ChatModel.call()  --HTTP-->  LLM provider
                                  ^                         (OpenAI/Claude/...)
                                  |
                          response parsed -> String / record / Flux

  Advisors wrap the call like AOP around-advice: each can mutate the request
  (add context) on the way down and the response on the way back up.
```

### RAG: ingestion (ETL) vs query time

```
  INGESTION (offline)                          QUERY (online)
  ──────────────────                           ──────────────
  PDF/HTML/Markdown                            user question
       |  DocumentReader                            |  EmbeddingModel.embed()
       v                                            v
  List<Document>                              query vector  [0.12, -0.4, ...]
       |  TextSplitter (chunk ~800 tokens)         |  VectorStore.similaritySearch(topK=4)
       v                                            v
  chunks                                      top-4 nearest chunks
       |  EmbeddingModel + VectorStore.add()        |  inject into prompt template
       v                                            v
  PgVector / Redis (cosine index)             augmented prompt -> ChatModel
```

### Tool (function) calling round trip

```
  user: "What's the weather in Pune and should I carry an umbrella?"
     |
     v  ChatModel sees registered @Tool getWeather(city)
  model responds: tool_call getWeather("Pune")   <-- model, not your code, decides
     |
     v  Spring AI invokes your Java method getWeather("Pune") -> {temp:31, rain:80%}
  result fed back to model
     |
     v  model composes final answer using the tool result
  "It's 31C with an 80% chance of rain — yes, carry an umbrella."
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Auto-configuration and a basic call

```properties
# application.properties — starter auto-configures the ChatModel + ChatClient.Builder
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o
spring.ai.openai.chat.options.temperature=0.2
```

```java
@RestController
class AssistantController {
    private final ChatClient chatClient;

    // ChatClient.Builder is auto-configured; customize per-bean here
    AssistantController(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a concise support assistant. Answer in <= 3 sentences.")
            .build();
    }

    @GetMapping("/ask")
    String ask(@RequestParam String q) {
        return chatClient.prompt()
            .user(q)
            .call()
            .content();   // String response
    }
}
```

### 6.2 Prompt Templates

```java
String summary = chatClient.prompt()
    .user(u -> u.text("Summarize {topic} for a {audience} in {n} bullet points.")
                .param("topic", "vector databases")
                .param("audience", "backend engineers")
                .param("n", 5))
    .call()
    .content();
// Placeholders are rendered by PromptTemplate before the call. Keep templates in
// resources/prompts/*.st and inject via @Value("classpath:...") for versioning.
```

### 6.3 Structured Output (typed responses)

```java
record MovieRecommendation(String title, int year, String reason) {}

// Spring AI appends format instructions and parses JSON into the record via BeanOutputConverter
List<MovieRecommendation> recs = chatClient.prompt()
    .user("Recommend 3 sci-fi movies about AI.")
    .call()
    .entity(new ParameterizedTypeReference<List<MovieRecommendation>>() {});
// No manual JSON parsing; on malformed output Spring AI throws — wrap with retry advisor.
```

### 6.4 RAG with QuestionAnswerAdvisor

```java
// Ingestion (run once / on document change)
List<Document> docs = new TikaDocumentReader(resource).get();
List<Document> chunks = new TokenTextSplitter().apply(docs);
vectorStore.add(chunks);   // embeds + stores in PgVector

// Query time — advisor retrieves top-k and augments the prompt automatically
String answer = chatClient.prompt()
    .user(question)
    .advisors(new QuestionAnswerAdvisor(vectorStore,
              SearchRequest.builder().topK(4).similarityThreshold(0.7).build()))
    .call()
    .content();
```

### 6.5 Tool / Function Calling

```java
@Component
class WeatherTools {
    @Tool(description = "Get current weather for a city")
    WeatherInfo getWeather(@ToolParam(description = "city name") String city) {
        return weatherService.lookup(city);   // your normal Spring bean call
    }
}

String reply = chatClient.prompt()
    .user("Should I carry an umbrella in Pune today?")
    .tools(new WeatherTools())     // model may call getWeather(); Spring AI handles the round trip
    .call()
    .content();
```

### 6.6 Streaming Responses

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<String> stream(@RequestParam String q) {
    return chatClient.prompt().user(q).stream().content();  // token-by-token SSE
}
// Backed by Reactor (see ../../java/reactive_programming/) — tokens arrive as a Flux.
```

### 6.7 Model Routing via Spring Beans (BROKEN → FIX)

```java
// BROKEN: hardcoding a vendor SDK defeats portability and is untestable
OpenAiChatModel model = new OpenAiChatModel(new OpenAiApi(apiKey)); // tied to OpenAI forever

// FIX: define multiple ChatClient beans, route by request — vendor-agnostic, mockable
@Bean @Qualifier("fast")
ChatClient fast(OpenAiChatModel m) { return ChatClient.create(m); }

@Bean @Qualifier("smart")
ChatClient smart(AnthropicChatModel m) { return ChatClient.create(m); }

// Route cheap queries to the fast model, hard ones to the smart model
ChatClient client = isComplex(query) ? smart : fast;
```

---

## 7. Real-World Examples

### 7.1 Internal Documentation Assistant (RAG)

A company ingests its Confluence + Markdown runbooks into PgVector via the ETL pipeline,
then exposes a `/ask` endpoint using `QuestionAnswerAdvisor`. Engineers ask natural-language
questions; the advisor retrieves the top-4 relevant chunks and grounds the answer, cutting
"where is the runbook for X" Slack traffic. Because it is a normal Spring service, it reuses
existing auth (Spring Security), metrics (Actuator/Micrometer), and Testcontainers tests.

### 7.2 Customer-Support Triage with Structured Output

Incoming support emails are classified into a `record Triage(String category, int priority,
boolean needsHuman)` via `.entity(Triage.class)`. The typed result feeds straight into the
existing ticketing workflow — the LLM becomes just another typed service call, not a special case.

### 7.3 Agentic Tool Use

A finance assistant registers `@Tool` methods for `getAccountBalance`, `listTransactions`, and
`transferFunds` (the last guarded by a confirmation step). The model orchestrates multi-step
requests ("move 200 from savings to checking if my checking is below 100"), calling tools as
needed — Spring AI handles each function-calling round trip while business logic stays in
ordinary, individually testable Spring beans.

---

## 8. Tradeoffs

| Concern | Spring AI | Raw vendor SDK | LangChain4j |
|---|---|---|---|
| Vendor portability | High (swap starter) | None (locked in) | High |
| Spring integration | Native (DI, Boot, Actuator) | Manual | Good |
| Auto-configuration | Yes | No | Partial |
| Maturity (vs Python LangChain) | Newer, 1.0 GA 2025 | Vendor-stable | Mature in JVM space |
| Advisor/RAG abstractions | Built-in | Hand-rolled | Built-in (chains) |
| Learning curve for Spring devs | Low (familiar idioms) | Medium | Medium |
| Cutting-edge model features | May lag vendor SDK | Immediate | Varies |

---

## 9. When to Use / When NOT to Use

### Use Spring AI when:
- You are building on the **Spring/Boot stack** and want LLM features wired with the same DI/auto-config/observability story.
- **Provider portability** matters — you want to A/B or switch between OpenAI, Anthropic, Bedrock, or local Ollama without rewriting code.
- You need **RAG, chat memory, tools, and structured output** as composable, testable Spring beans.
- You want first-class **Micrometer observability** of token usage and latency alongside the rest of your service metrics.

### Do NOT use Spring AI when:
- You need a **bleeding-edge vendor feature** the day it ships — the raw SDK exposes it first; Spring AI may lag by a release.
- Your app is **not on the JVM/Spring** — use the native Python/JS ecosystem (LangChain, LlamaIndex) instead.
- The integration is a **one-off script** with a single provider and no Spring context — the vendor SDK is lighter.
- You require **heavy custom orchestration** beyond advisors/tools that fights the abstraction — evaluate LangChain4j or a custom layer.

---

## 10. Common Pitfalls

### Pitfall 1: Leaking API keys / no externalized config
```properties
# BROKEN: key hardcoded in committed properties
spring.ai.openai.api-key=sk-abc123...
# FIX: inject from environment / Vault; never commit secrets
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

### Pitfall 2: Unbounded chat memory growing the prompt (and cost)
Naively appending every turn to `ChatMemory` inflates the prompt until it exceeds the context
window and costs balloon. Use a windowed/`MessageWindowChatMemory` that caps turns, and
summarize older history.

### Pitfall 3: Structured-output parse failures unhandled
`.entity(Record.class)` throws if the model returns non-conforming text. Wrap with a retry
advisor or `@Retryable`, and lower temperature for deterministic structure.

### Pitfall 4: Treating similarity score as a relevance guarantee
A top-k retrieval *always* returns k chunks even if none are relevant. Set a
`similarityThreshold` and handle the empty/low-confidence case ("I don't have that in the
docs") instead of letting the model hallucinate from irrelevant context.

### Pitfall 5: Blocking the event loop when streaming
`.stream()` returns a `Flux`; doing blocking work in the subscriber on a Netty thread stalls
connections — keep streaming handlers non-blocking (see [reactive programming](../../java/reactive_programming/README.md)).

### Pitfall 6: Re-embedding unchanged documents on every startup
Running the ETL ingestion on every boot re-embeds the whole corpus (slow + costly). Ingest
out-of-band or guard with a content hash so only changed documents are re-embedded.

---

## 11. Technologies & Tools

| Tool / Feature | Version | Purpose |
|---|---|---|
| Spring AI | 1.0 GA (2025) | Core framework, `ChatClient`, advisors |
| Spring Boot | 3.3 / 3.4+ | Auto-configuration, starters baseline |
| `spring-ai-openai-spring-boot-starter` | 1.0+ | OpenAI chat/embedding/image |
| `spring-ai-anthropic-spring-boot-starter` | 1.0+ | Anthropic Claude chat + tools |
| `spring-ai-ollama-spring-boot-starter` | 1.0+ | Local models, no API key |
| PgVector / `spring-ai-pgvector-store` | 1.0+ | Postgres vector similarity search |
| Redis / Chroma / Milvus stores | 1.0+ | Alternative `VectorStore` backends |
| `QuestionAnswerAdvisor` / `RetrievalAugmentationAdvisor` | 1.0+ | Naive and advanced RAG |
| `@Tool` / `ToolCallback` | 1.0+ | Function calling |
| Micrometer Observation | 1.12+ | Token/latency metrics for AI calls |
| Testcontainers | 1.19+ | Integration-test PgVector / Ollama |

---

## 12. Interview Questions with Answers

**Q1: What problem does Spring AI solve, and how does it relate to the raw vendor SDKs?**
Spring AI provides portable, auto-configured abstractions over LLM providers so that Spring applications integrate AI with the same idioms as everything else — DI, starters, `@ConfigurationProperties`, fluent clients, and Micrometer observability. Instead of coding against OpenAI's or Anthropic's REST SDK directly (vendor lock-in, hand-rolled retries, manual JSON parsing), you program against `ChatClient`/`ChatModel`/`VectorStore`. Switching providers becomes a matter of changing a starter dependency and properties. The tradeoff is that cutting-edge vendor features may arrive in the raw SDK before Spring AI exposes them.

**Q2: What is `ChatClient` and how does it differ from `ChatModel`?**
`ChatModel` is the low-level, portable interface representing a chat model call (synchronous `call` and streaming variants) — analogous to `JdbcTemplate`. `ChatClient` is a higher-level fluent API built on top of a `ChatModel`, analogous to `RestClient`: it supports a builder, default system prompts, prompt templates, an **advisor chain** (RAG, memory, logging), tools, and structured output via `.entity(...)`. You typically inject the auto-configured `ChatClient.Builder`, customize defaults per bean, and call `.prompt().user(...).call().content()`. Use `ChatModel` directly only for low-level control.

**Q3: What is an Advisor in Spring AI and what is it analogous to in core Spring?**
An `Advisor` is an interceptor that wraps a `ChatClient` call, able to mutate the request on the way down and the response on the way back up — exactly the around-advice model of Spring AOP or the pre/post pattern of a `HandlerInterceptor`. Advisors compose cross-cutting prompt concerns: `QuestionAnswerAdvisor` injects retrieved RAG context, `MessageChatMemoryAdvisor` injects conversation history, `SafeGuardAdvisor` blocks unsafe content, and custom advisors add logging or token budgeting. They are ordered, so you control whether memory is injected before or after retrieval. This is the key abstraction that keeps RAG/memory/safety out of business code.

**Q4: How does RAG work in Spring AI, end to end?**
RAG has two phases. **Ingestion (ETL, offline):** a `DocumentReader` parses source files into `Document`s, a `TextSplitter` chunks them (~hundreds of tokens each), and `VectorStore.add()` embeds and stores the chunks in a vector database (PgVector, Redis, etc.). **Query (online):** the user's question is embedded, `VectorStore.similaritySearch(topK, threshold)` returns the nearest chunks, and a `QuestionAnswerAdvisor` injects those chunks into the prompt as grounding context before calling the model. The point is to ground answers in your private data and reduce hallucination. Setting a `similarityThreshold` and handling the low-confidence case prevents the model from confabulating when nothing relevant is retrieved.

**Q5: What is a `VectorStore` and how do you choose a backend?**
`VectorStore` is Spring AI's SPI for storing embeddings and performing similarity search — the `Repository` analogue for vectors. Implementations include PgVector (reuse your existing Postgres, good default), Redis (low latency), Chroma/Milvus/Weaviate (purpose-built vector DBs), and an in-memory `SimpleVectorStore` for tests. Choose PgVector when you already run Postgres and want one operational surface; choose a dedicated vector DB at very large scale or when you need advanced indexing (HNSW tuning, hybrid search). All are swappable behind the same interface, so you can start with PgVector and migrate later without changing application code.

**Q6: How does structured output work and what are its failure modes?**
`.call().entity(MyRecord.class)` instructs Spring AI to append format instructions to the prompt and parse the model's text response into a typed Java object via `BeanOutputConverter` (which generates a JSON schema from the record and deserializes the reply). This turns free-text output into compile-time-typed data, e.g., a `Triage` record fed straight into a workflow. The failure mode is non-conforming output — if the model returns prose or malformed JSON, parsing throws. Mitigate by lowering temperature, providing a clear schema/example, and wrapping the call in a retry so a one-off malformed response is re-requested.

**Q7: What is tool/function calling and how does Spring AI implement it?**
Tool calling lets the *model* decide to invoke your code. You annotate a Spring bean method with `@Tool(description=...)`; when registered on a `ChatClient` call via `.tools(...)`, Spring AI advertises the method's name, description, and parameter schema to the model. If the model returns a tool-call request, Spring AI invokes your Java method, feeds the result back, and lets the model compose the final answer — possibly across multiple tool calls. This is the foundation of agentic behavior. Critically, the business logic stays in ordinary, individually testable Spring beans; Spring AI only orchestrates the function-calling round trips.

**Q8: How do you make a Spring AI application portable across model providers?**
Program against the abstractions (`ChatClient`, `ChatModel`, `EmbeddingModel`, `VectorStore`), never a vendor's concrete SDK type, and supply the provider via a `spring-ai-<vendor>-spring-boot-starter` plus properties. Auto-configuration creates the right `ChatModel` bean from the classpath and config. For multi-model routing, define multiple `ChatClient` beans with `@Qualifier`s (e.g., a cheap "fast" model and a capable "smart" model) and select per request. Because everything is bean-wired, you can also mock the `ChatModel` in tests. The anti-pattern is `new OpenAiChatModel(...)` in application code, which re-introduces lock-in and untestability.

**Q9: How do you observe and control the cost of Spring AI calls?**
Spring AI emits Micrometer `Observation`s for model and vector-store calls, capturing latency and token usage (prompt/completion tokens), which surface through Actuator/Micrometer into Prometheus/Grafana like any other Boot metric. To control cost: cap context with a windowed `ChatMemory` (don't append unbounded history), set `topK`/chunk sizes deliberately in RAG, route easy queries to cheaper models, lower `max-tokens`, and cache deterministic responses. Token usage is the dominant cost driver, so the same observability you use for latency should alert on token spend.

**Q10: How does Spring AI handle streaming, and what is the underlying mechanism?**
`chatClient.prompt().user(q).stream().content()` returns a `Flux<String>` that emits tokens as the model produces them, ideal for `text/event-stream` SSE endpoints so users see output incrementally. Under the hood this is Reactor (Project Reactor) — the same reactive foundation as Spring WebFlux. The implication is that streaming handlers must remain non-blocking: doing blocking work in the subscriber on a Netty event-loop thread stalls other connections. For non-streaming use cases, `.call()` is the simpler synchronous path.

**Q11: What is the ETL pipeline in Spring AI and why split documents into chunks?**
The ETL pipeline is `DocumentReader` (extract: PDF/HTML/Markdown/Tika → `Document`s) → `DocumentTransformer`/`TextSplitter` (transform: chunk and enrich metadata) → `VectorStore` (load: embed and store). Documents are split into chunks of a few hundred tokens because (1) embedding models have input limits, (2) retrieval is more precise when each chunk is a focused unit of meaning, and (3) you can fit several relevant chunks into the prompt's context budget rather than one giant document. Chunk size and overlap are tuning knobs: too large dilutes relevance, too small loses context. The `TokenTextSplitter` handles this with configurable size/overlap.

**Q12: How do you test a Spring AI application without calling a real, paid model?**
Mock the `ChatModel`/`ChatClient` bean (with Mockito or a test configuration) to return canned responses for unit and slice tests, asserting that your advisors, prompt templates, and structured-output mapping behave correctly. For integration tests of RAG, use a `SimpleVectorStore` or a Testcontainers PgVector/Ollama instance so embeddings and similarity search run locally without external cost. Spring AI's portability is itself a testing asset: point the same code at a local Ollama model in CI. Avoid asserting on exact LLM text (non-deterministic); assert on structure, tool invocation, and that retrieval injected the expected context.

**Q13: What is chat memory and how do you keep it from blowing the context window?**
`ChatMemory` persists conversation turns so the model has context across requests — `MessageChatMemoryAdvisor` injects prior messages into each prompt, keyed by a conversation id (analogous to `HttpSession`). The risk is unbounded growth: appending every turn eventually exceeds the model's context window and inflates token cost linearly. The fix is a windowed memory (`MessageWindowChatMemory`) that retains only the last N turns, optionally with a summarization step that compresses older history into a short summary message. Persist memory in Redis/JDBC for multi-instance services rather than in-memory.

**Q14: How does Spring AI's auto-configuration decide which model bean to create, and how do you override it?**
The provider starter contributes an `AutoConfiguration` class registered in `AutoConfiguration.imports`, gated by `@ConditionalOnClass` (the vendor classes are on the classpath) and properties like `spring.ai.openai.api-key`. It creates the `ChatModel`/`EmbeddingModel` and a `ChatClient.Builder` bean, each guarded by `@ConditionalOnMissingBean` so you can override by simply defining your own bean of that type. This is the standard Boot back-off pattern (see [spring_boot_autoconfiguration](../spring_boot_autoconfiguration/README.md)). With multiple provider starters on the classpath you disambiguate via properties and `@Qualifier`s.

**Q15: When would you choose Spring AI over LangChain4j or the raw SDK, and when not?**
Choose Spring AI when you are on the Spring/Boot stack and want native DI, auto-configuration, Actuator observability, and provider portability with minimal new concepts — the LLM feature becomes "just another Spring service." Choose the **raw vendor SDK** for one-off scripts, single-provider apps with no Spring context, or when you need a brand-new vendor feature immediately. Consider **LangChain4j** if you want a more mature, chain-oriented JVM framework with a larger catalog of integrations and don't mind a less Spring-native feel. The deciding factors are: how Spring-centric your stack is, how much you value portability vs. bleeding-edge features, and how much custom orchestration you need beyond advisors and tools.

**Q16: Describe a production concern unique to LLM-backed Spring services and how you'd address it.**
LLM calls are slow (hundreds of ms to seconds), non-deterministic, costly per token, and can fail or return unsafe/hallucinated content — unlike a deterministic DB call. Address these with: timeouts and retries (Resilience4j) around model calls; a circuit breaker and a graceful fallback ("I'm unable to answer right now"); a `similarityThreshold` plus "answer only from context" prompting to curb hallucination; token-usage metrics and budget alerts via Micrometer; `SafeGuardAdvisor`/content filtering for safety; and caching deterministic prompts. Treat the model as an unreliable, expensive remote dependency and apply the same resilience patterns you'd use for any flaky third-party API.

---

## 13. Best Practices

1. **Program against abstractions** (`ChatClient`/`VectorStore`), never a vendor SDK type — preserve portability and testability.
2. **Externalize API keys** via environment/Vault; never commit secrets to properties.
3. **Compose cross-cutting concerns as advisors** (RAG, memory, safety, logging) — keep business beans clean.
4. **Set a `similarityThreshold` in RAG** and handle the low-confidence case instead of forcing an answer.
5. **Bound chat memory** with a window and summarize old turns to control context size and cost.
6. **Wrap model calls in Resilience4j** (timeout, retry, circuit breaker) — treat the LLM as a flaky, expensive remote dependency.
7. **Lower temperature for structured output** and wrap `.entity(...)` with retry to survive occasional malformed responses.
8. **Instrument token usage** via Micrometer and alert on spend, not just latency.
9. **Ingest documents out-of-band** with content-hash guards; don't re-embed the corpus on every startup.
10. **Mock the model in tests; use Testcontainers PgVector/Ollama** for integration tests — never hit a paid endpoint in CI.

---

## 14. Case Study

**Scenario: An internal "Ask the Docs" assistant over 40,000 engineering documents**

A platform team must build a question-answering assistant over ~40,000 internal documents
(runbooks, ADRs, API guides) spread across Confluence and Git. Requirements: answers grounded
in the docs (no hallucination), provider portability (start on OpenAI, keep the option to move
to a self-hosted model for data-residency), reuse of existing Spring Security/observability, and
a cost ceiling.

**Broken first attempt:**
```java
// BROKEN: vendor-locked, re-embeds everything on boot, no relevance gate
@PostConstruct
void ingest() {
    var docs = readAllConfluence();                 // 40k docs every startup
    vectorStore.add(new TokenTextSplitter().apply(docs));  // re-embeds 40k docs each boot
}
String ask(String q) {
    var ctx = vectorStore.similaritySearch(SearchRequest.query(q).withTopK(4)); // no threshold
    return openAiChatModel.call(promptWith(ctx, q)); // OpenAI hardcoded; hallucinates on empty ctx
}
```
Problems: every restart re-embeds 40k docs (~30 min, large embedding bill); `topK=4` with no
threshold returns 4 chunks even for off-topic questions, so the model confabulates; OpenAI is
hardcoded; no observability or resilience.

**Fixed design:**
```java
// Ingestion: out-of-band job, content-hash guarded
void ingestChanged(List<Document> changed) {       // only changed docs (hash compare)
    vectorStore.add(new TokenTextSplitter(800, 200, 5, 10000, true).apply(changed));
}

// Query: portable ChatClient + RAG advisor with threshold + resilience
@Bean ChatClient docsClient(ChatClient.Builder b, VectorStore store) {
    return b.defaultSystem("Answer ONLY from the provided context. If the context is "
                         + "insufficient, say you don't know.")
            .defaultAdvisors(new QuestionAnswerAdvisor(store,
                 SearchRequest.builder().topK(5).similarityThreshold(0.75).build()))
            .build();
}

@CircuitBreaker(name = "llm", fallbackMethod = "fallback")
@TimeLimiter(name = "llm")
String ask(String q) {
    return docsClient.prompt().user(q).call().content();
}
String fallback(String q, Throwable t) { return "The assistant is temporarily unavailable."; }
```

**Outcomes:**
- Provider portability: switching from OpenAI to a self-hosted Ollama model for a data-residency
  pilot was a starter+property change — zero application-code edits.
- Hallucination control: the 0.75 `similarityThreshold` + "answer only from context" system prompt
  turned off-topic questions into honest "I don't know" responses instead of fabrications.
- Cost/latency: content-hash ingestion cut startup re-embedding from ~30 min to seconds; bounded
  `topK` and `max-tokens` kept per-query token spend predictable; Micrometer dashboards tracked
  token usage alongside p99 latency.
- Reuse: the assistant inherited Spring Security auth, Actuator health, and Testcontainers PgVector
  integration tests — it shipped like any other Spring service, not a bespoke ML system.

**Lesson:** Spring AI's value here was *integration leverage*, not novel ML. The hard parts —
grounding, portability, resilience, observability — were solved with advisors, abstractions, and
the resilience/observability machinery the team already used everywhere else.

**See also:**
- [LLM: Advanced RAG](../../llm/advanced_rag/README.md) — retrieval quality, reranking, chunking theory behind `QuestionAnswerAdvisor`
- [LLM: Embeddings & Similarity Search](../../llm/embeddings_and_similarity_search/README.md) — how vector search and `VectorStore` actually work
- [Spring Boot Autoconfiguration](../spring_boot_autoconfiguration/README.md) — the conditional-bean back-off that wires model beans

---

## Related / See Also

- [LLM: Advanced RAG](../../llm/advanced_rag/README.md) — RAG pipelines, retrieval quality, reranking
- [LLM: Agentic Frameworks](../../llm/agentic_frameworks/README.md) — tool use and agent orchestration the `@Tool` API exposes
- [LLM: Embeddings & Similarity Search](../../llm/embeddings_and_similarity_search/README.md) — vector embeddings and `VectorStore` internals
- [Spring Boot Autoconfiguration](../spring_boot_autoconfiguration/README.md) — starter + conditional-bean mechanics that auto-configure models
- [Spring AOP](../spring_aop/README.md) — the interceptor model that advisors mirror
- [Java Reactive Programming](../../java/reactive_programming/README.md) — Reactor `Flux`, the basis of `.stream()` responses
