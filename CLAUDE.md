# CLAUDE.md — System Design Repository

## What This Repo Is

A comprehensive system design study repository covering:
- **LLD** — Design patterns (GoF), SOLID, anti-patterns
- **HLD** — Distributed system concepts (CAP, caching, queues, sharding, etc.) + case studies
- **LLM** — Complete LLM engineering guide (34 modules, 7 case studies, 31 deep-dive sub-files across 5 topics)
- **Java** — Pure Java senior-engineer + interview prep guide (17 modules, 4 case studies)
- **Spring** — Spring Framework senior-engineer + interview prep guide (23 modules, 5 case studies)

All content is Markdown — no runnable application. Java source files under `src/main/java/` are documentation organized as a Maven project skeleton.

---

## Repository Structure

```
/
├── CLAUDE.md                          ← this file
├── README.md                          ← root index (update when adding sections)
├── HELP.md                            ← Maven Spring Boot boilerplate (ignore)
└── src/main/java/com/rutik/systemdesign/
    ├── lld/                           ← Design patterns
    │   ├── creational/
    │   ├── structural/
    │   ├── behavioral/
    │   ├── solid_principles/
    │   ├── anti_patterns/
    │   └── pattern_comparisons/
    ├── hld/                           ← System design concepts
    │   └── case_studies/
    ├── llm/                           ← LLM engineering guide
    │   └── case_studies/
    ├── java/                          ← Pure Java guide
    │   └── case_studies/
    └── spring/                        ← Spring Framework guide
        └── case_studies/
```

---

## Content Conventions

### The 14-Section Module Template

Every module README must follow this exact structure (matches `llm/foundations_and_architecture/README.md`):

```
## 1. Concept Overview
## 2. Intuition           (one-line analogy, mental model, why it matters, key insight)
## 3. Core Principles
## 4. Types / Architectures / Strategies
## 5. Architecture Diagrams    (ASCII art — no image files)
## 6. How It Works — Detailed Mechanics   (code, pseudocode, concrete numbers)
## 7. Real-World Examples
## 8. Tradeoffs            (comparison tables)
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls     (production war stories)
## 11. Technologies & Tools
## 12. Interview Questions with Answers   (10+ Q&As, bold Q, plain A)
## 13. Best Practices
## 14. Case Study
```

### Case Study Files (in `case_studies/` directories)

Case study files do NOT use the 14-section template. They use:
```
## Problem Statement
## Architecture Overview   (ASCII diagram)
## Key Design Decisions
## Implementation          (detailed Java/Spring code)
## Spring/Java Components Used
## Tradeoffs and Alternatives
## Interview Discussion Points
```

### Interview Q&A Rules

- **Bold the question**, plain text the answer
- First sentence = direct answer
- Following sentences = mechanism / example / gotcha
- Final sentence = practical guidance
- Minimum 10 Q&As per module; 12–15 for concurrency, collections, JVM internals
- 15–18 Q&As for deep Spring modules: spring_data_jpa, spring_transactions, spring_security_architecture, spring_aop

### Content Quality Standards

- **Show broken code, then the fix** — DCL without volatile, HashMap concurrency, self-invocation, N+1
- **Concrete numbers everywhere** — virtual thread ~few KB stack; platform thread ~1MB; HashMap default capacity 16; load factor 0.75; ArrayList grows 1.5×; G1 default pause 200ms; ZGC sub-1ms; HikariCP default pool 10; Tomcat default threads 200; BCrypt cost factor 10–12
- **Production war stories** in Common Pitfalls — real incident patterns, not toy examples
- **No emojis** in any file
- **No Spring/framework content** in the `java/` section — pure Java only
- **Effective Java item references** where applicable (Item 1, Item 3, Item 17, etc.)

### Java Version Tags

When covering a feature, include the version it was introduced and LTS status:
- Java 8 (LTS), Java 11 (LTS), Java 17 (LTS), Java 21 (LTS)
- Non-LTS: 9, 10, 12–16, 18–20

### Spring Version Tags

When covering a Spring feature, note the version:
- Spring Boot 2.7 / Spring Framework 5.3 — last javax.* baseline, spring.factories
- Spring Boot 3.0 / Spring Framework 6.0 — jakarta.* namespace, AutoConfiguration.imports, WebSecurityConfigurerAdapter removed, lambda DSL required, JDK 17 baseline
- Spring Boot 3.1+ — Testcontainers support out-of-the-box, virtual threads preview
- Spring Boot 3.2+ — Virtual threads GA (spring.threads.virtual.enabled=true), RestClient

---

## LLM Section Module List

Current modules under `src/main/java/com/rutik/systemdesign/llm/`:

| Module Directory | Topic | Sub-files |
|-----------------|-------|-----------|
| `foundations_and_architecture/` | Transformers, self-attention, scaling laws, GPT vs BERT | — |
| `tokenization_and_embeddings/` | BPE, WordPiece, SentencePiece, vocabulary design | — |
| `embeddings_and_similarity_search/` | Sentence embeddings, HNSW, IVF, Matryoshka, FAISS | — |
| `pre_training/` | CLM, MLM, data curation, training dynamics, compute | — |
| `training_infrastructure/` | Distributed training, tensor/pipeline parallelism, ZeRO, FSDP | — |
| `synthetic_data_generation/` | Self-Instruct, Evol-Instruct, quality filtering, LIMA | — |
| `fine_tuning/` | LoRA, QLoRA, PEFT, instruction tuning, domain adaptation | lora, qlora, peft_methods, instruction_tuning, domain_adaptation |
| `alignment_and_rlhf/` | RLHF, DPO, Constitutional AI, ORPO, KTO, reward models | — |
| `prompt_engineering/` | CoT, few-shot, ReAct, structured outputs, system prompts | — |
| `rag_fundamentals/` | Chunking, vector DBs, retrieval, reranking, hybrid search | chunking_strategies, embedding_models, retrieval_methods, reranking |
| `advanced_rag/` | Agentic RAG, Graph RAG, multimodal RAG, evaluation | agentic_rag, corrective_rag, graph_rag, multimodal_rag, query_transformation, self_rag |
| `reasoning_models/` | o1/o3, test-time compute, MCTS, DeepSeek-R1, PRM/ORM | — |
| `code_generation/` | FIM, CodeLLaMA, Copilot architecture, SWE-bench, code agents | — |
| `agents_and_tool_use/` | Function calling, ReAct, plan-and-execute, memory systems | function_calling_and_tool_design, react_and_reasoning_patterns, plan_and_execute, agent_memory, computer_use_and_browser_agents, agent_evaluation_and_benchmarking |
| `agentic_frameworks/` | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, SK, Haystack, DSPy | langchain_and_lcel, langgraph, llamaindex, crewai, autogen, semantic_kernel, haystack, dspy, framework_observability, structured_outputs_and_instructor |
| `multi_agent_systems/` | Orchestrator pattern, debate, ChatDev, MetaGPT, Swarm | — |
| `inference_and_decoding/` | Sampling, KV cache, speculative decoding, continuous batching | — |
| `inference_engines/` | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI | — |
| `optimization_and_quantization/` | GPTQ, AWQ, Flash Attention, MoE, pruning, distillation | — |
| `deployment_and_mlops/` | Serving, GPU cost, monitoring, routing, edge deployment | — |
| `guardrails_and_content_safety/` | NeMo Guardrails, Llama Guard, input/output filters | — |
| `safety_and_alignment/` | Jailbreaking, prompt injection, hallucination, bias, red teaming | — |
| `evaluation_and_benchmarks/` | MMLU, HumanEval, LLM-as-judge, Chatbot Arena, RAGAs | — |
| `multimodal_models/` | VLMs, vision encoders, diffusion, speech, video | — |
| `context_windows_and_long_context/` | RoPE, ALiBi, YaRN, long context vs RAG | — |
| `ai_applications/` | Healthcare, legal, finance, education, customer support | — |
| `llm_ecosystem_and_landscape/` | Model families, licensing, cost analysis, timeline | — |
| `vllm_deep_dive/` | PagedAttention, continuous batching, prefix caching, TP/PP | — |
| `mcp_model_context_protocol/` | MCP protocol, servers/clients, resources/tools/prompts, JSON-RPC, A2A | — |
| `small_language_models_and_edge_ai/` | Phi-3/4, Gemma, on-device inference, ONNX, Core ML, quantization | — |
| `mixture_of_experts/` | MoE routing, Mixtral 8x7B, DeepSeek-V3, load balancing, sparse activation | — |
| `llm_routing_and_model_selection/` | Multi-model routing, cascade patterns, confidence thresholds, cost optimization | — |
| `token_economics_and_cost_optimization/` | Per-token pricing, prompt caching, batch APIs, self-hosting break-even | — |
| `data_flywheels_and_continuous_learning/` | Production feedback loops, active learning, drift detection, A/B testing | — |

Case studies in `llm/case_studies/`: design_chatgpt, design_copilot, design_rag_pipeline, design_ai_search_engine, design_llm_gateway, design_ai_coding_assistant, design_customer_support_bot.

Master index: `llm/README.md` (topics table + Sub-Files Index section).

---

## Java Section Module List

Current modules under `src/main/java/com/rutik/systemdesign/java/`:

| Module Directory | Topic |
|-----------------|-------|
| `core_language/` | OOP, equals/hashCode, inner classes, polymorphism, init order |
| `java8_features/` | Lambdas, Streams overview, Optional, Collectors, Date/Time, primitive streams |
| `java_streams/` | Stream API deep dive — all ops, internals, parallel, Spliterator |
| `java9_to_21_features/` | Records, Sealed classes, Virtual threads, Pattern matching, JPMS |
| `jvm_internals/` | GC algorithms, JIT, memory barriers, safepoints, class loading |
| `concurrency/` | synchronized, volatile, ThreadPool, CAS, AQS, LockSupport, CompletableFuture |
| `collections_internals/` | HashMap, ArrayList, LinkedHashMap, NavigableMap, Spliterator internals |
| `exceptions_and_io/` | Checked/unchecked, try-with-resources, NIO.2, FileChannel, serialization |
| `functional_programming/` | Composition, custom Collectors, parallel streams, immutability |
| `generics_and_type_system/` | PECS, erasure, bridge methods, wildcards, MethodHandle, dynamic proxies |
| `performance_and_tuning/` | GC tuning, JMH, CPU cache, JIT inlining, tiered compilation |
| `java_interview_patterns/` | Immutable class, Builder, enum singleton, Integer cache |
| `design_patterns_in_java/` | GoF patterns (Creational, Structural, Behavioral), concurrency patterns |
| `testing_junit_mockito/` | JUnit 5, Mockito, test doubles, AAA pattern, parameterized tests |
| `java_memory_model/` | Happens-before rules, memory barriers, safe publication, DRF |
| `networking_and_http_client/` | HttpClient (Java 11), NIO Selector, Reactor pattern, HTTP/2 |
| `jdbc_and_database/` | PreparedStatement, HikariCP, transaction isolation, batch inserts |

Case studies in `java/case_studies/`: connection pool, rate limiter, event bus, LRU cache.

---

## Spring Section Module List

Current modules under `src/main/java/com/rutik/systemdesign/spring/`:

| Module Directory | Phase | Topic |
|-----------------|-------|-------|
| `ioc_container/` | 1 | BeanFactory vs ApplicationContext, refresh lifecycle, BeanDefinition |
| `bean_lifecycle/` | 1 | Full lifecycle sequence, scopes, BPP vs BFPP, prototype gotchas |
| `dependency_injection/` | 1 | Constructor/setter/field injection, @Primary, @Qualifier, ObjectProvider |
| `spring_configuration/` | 1 | @Configuration full vs lite mode, @Conditional, @Profile, @Import |
| `spring_proxies/` | 2 | JDK proxy vs CGLIB, self-invocation problem, proxyTargetClass |
| `spring_aop/` | 2 | Pointcut expressions, advice types, @Around, AspectJ vs Spring AOP |
| `spring_boot_autoconfiguration/` | 3 | @EnableAutoConfiguration, AutoConfiguration.imports, custom starters |
| `spring_boot_configuration/` | 3 | @ConfigurationProperties, relaxed binding, property source priority |
| `spring_boot_actuator/` | 3 | Health indicators, Micrometer, custom endpoints, K8s probes |
| `spring_mvc_architecture/` | 4 | DispatcherServlet pipeline, HandlerMapping, message converters |
| `request_handling/` | 4 | @RequestMapping, validation, @ControllerAdvice, ProblemDetail |
| `filters_and_interceptors/` | 4 | Filter vs HandlerInterceptor, OncePerRequestFilter, order |
| `spring_webflux/` | 4 | Mono/Flux, Netty event loop, backpressure, WebClient, R2DBC |
| `spring_data_jpa/` | 5 | JpaRepository, N+1, projections, locking, Specifications |
| `spring_transactions/` | 5 | Propagation, isolation, self-invocation, rollback rules |
| `spring_caching/` | 5 | @Cacheable, @CacheEvict, RedisCacheManager, stampede prevention |
| `spring_security_architecture/` | 6 | FilterChainProxy, AuthenticationManager, CSRF, method security |
| `spring_security_jwt_oauth/` | 6 | JWT, OAuth2 resource server, PKCE, refresh token rotation |
| `spring_cloud_config/` | 7 | Config Server, @RefreshScope, Spring Cloud Bus, Vault |
| `spring_cloud_patterns/` | 7 | Gateway, Resilience4j, Feign, Eureka, Micrometer Tracing |
| `spring_messaging/` | 7 | Kafka, RabbitMQ, Spring Cloud Stream, @Async, WebSocket |
| `spring_testing/` | 8 | @SpringBootTest, test slices, MockMvc, @MockBean, Testcontainers |
| `spring_performance/` | 8 | HikariCP tuning, lazy init, GraalVM native, virtual threads |

Case studies in `spring/case_studies/`: multitenant API, event-driven microservice, API gateway, batch pipeline, distributed caching.

---

## How to Add a New Module

1. Create `src/main/java/com/rutik/systemdesign/<section>/<module_name>/README.md`
2. Follow the 14-section template exactly
3. Add 10+ interview Q&As (15–18 for deep modules)
4. Update the section's master `README.md` (the index file for that section)
5. Update the root `README.md` table

### Adding an LLM sub-file (deep-dive within an existing topic):
- Create `src/main/java/com/rutik/systemdesign/llm/<topic>/<subtopic>.md`
- Follow the 14-section template; minimum 15 Q&As
- Update the topic's own `README.md` to add a "Deep Dive Files" table at the top linking to the new file
- Update `llm/README.md`: note the sub-file count in the topic's table row, add the file to the Sub-Files Index section
- Update the LLM module table in `CLAUDE.md` (add to Sub-files column)

### Adding a Spring module specifically:
- Add a row to the module table in `spring/README.md`
- Place it in the correct phase in the phase diagram
- Add cross-references in the Cross-Reference Map
- Update root `README.md` Phase table under the Spring section

### Adding a Java module specifically:
- Add a row to the module table in `java/README.md`
- Place it in the correct learning phase in the phase diagram
- Add cross-references in the Cross-Reference Map if applicable
- Update root `README.md` Phase table under the Java section

---

## Reference Files

| File | Purpose |
|------|---------|
| `llm/foundations_and_architecture/README.md` | Gold standard 14-section format |
| `llm/rag_fundamentals/README.md` | Second format reference |
| `llm/agentic_frameworks/langchain_and_lcel.md` | Example deep-dive sub-file (15+ Q&As, full 14-section) |
| `java/README.md` | Java section master index |
| `java/concurrency/README.md` | Example of 15+ Q&A deep module |
| `java/collections_internals/README.md` | Example of 12+ Q&A module |
| `spring/README.md` | Spring section master index |
| `spring/spring_transactions/README.md` | Example of 18+ Q&A deep Spring module |
| `spring/spring_security_architecture/README.md` | Example of 18+ Q&A deep Spring module |
| `README.md` | Root index — always update when adding sections |

---

## Formatting Rules

- All diagrams use **ASCII art only** — no Mermaid, no image files
- Tables use standard Markdown pipe syntax
- Code blocks use triple backticks with language tag (` ```java `, ` ```sql `, ` ```yaml `, etc.)
- Section headers follow the exact numbering: `## 1.`, `## 2.`, ... `## 14.`
- Use `---` horizontal rules to separate major sections
- Links between modules: use relative paths, e.g., `[Concurrency](../concurrency/README.md)`
