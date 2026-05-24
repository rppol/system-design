# System Design Master Repository

A comprehensive, one-stop repository for learning **Low-Level Design (LLD)**, **High-Level Design (HLD)**, **Large Language Models (LLM)**, **Machine Learning (ML)**, **Java**, **Spring**, **Backend Engineering**, and **Database Engineering** — with practical examples, real-world scenarios, and interview preparation material.

---

## Repository Structure

### LLD (Low-Level Design) - Design Patterns

#### Creational Patterns
Patterns that deal with object creation mechanisms, trying to create objects in a manner suitable to the situation.

| Pattern | Description | Complexity |
|---------|-------------|------------|
| [Singleton](src/main/java/com/rutik/systemdesign/lld/creational/singleton/) | Ensures a class has only one instance | Simple |
| [Factory Method](src/main/java/com/rutik/systemdesign/lld/creational/factory_method/) | Defines an interface for creating objects, letting subclasses decide | Medium |
| [Abstract Factory](src/main/java/com/rutik/systemdesign/lld/creational/abstract_factory/) | Creates families of related objects without specifying concrete classes | Complex |
| [Builder](src/main/java/com/rutik/systemdesign/lld/creational/builder/) | Constructs complex objects step by step | Medium |
| [Prototype](src/main/java/com/rutik/systemdesign/lld/creational/prototype/) | Creates new objects by cloning existing ones | Medium |

#### Structural Patterns
Patterns that deal with object composition, creating relationships between objects to form larger structures.

| Pattern | Description | Complexity |
|---------|-------------|------------|
| [Adapter](src/main/java/com/rutik/systemdesign/lld/structural/adapter/) | Allows incompatible interfaces to work together | Simple |
| [Bridge](src/main/java/com/rutik/systemdesign/lld/structural/bridge/) | Separates abstraction from implementation | Complex |
| [Composite](src/main/java/com/rutik/systemdesign/lld/structural/composite/) | Composes objects into tree structures | Medium |
| [Decorator](src/main/java/com/rutik/systemdesign/lld/structural/decorator/) | Adds behavior to objects dynamically | Medium |
| [Facade](src/main/java/com/rutik/systemdesign/lld/structural/facade/) | Provides a simplified interface to a complex subsystem | Simple |
| [Flyweight](src/main/java/com/rutik/systemdesign/lld/structural/flyweight/) | Shares common state between multiple objects | Complex |
| [Proxy](src/main/java/com/rutik/systemdesign/lld/structural/proxy/) | Provides a surrogate or placeholder for another object | Medium |

#### Behavioral Patterns
Patterns that deal with communication between objects, defining how objects interact and distribute responsibility.

| Pattern | Description | Complexity |
|---------|-------------|------------|
| [Chain of Responsibility](src/main/java/com/rutik/systemdesign/lld/behavioral/chain_of_responsibility/) | Passes requests along a chain of handlers | Medium |
| [Command](src/main/java/com/rutik/systemdesign/lld/behavioral/command/) | Encapsulates a request as an object | Medium |
| [Iterator](src/main/java/com/rutik/systemdesign/lld/behavioral/iterator/) | Provides sequential access to collection elements | Simple |
| [Mediator](src/main/java/com/rutik/systemdesign/lld/behavioral/mediator/) | Reduces chaotic dependencies between objects | Complex |
| [Memento](src/main/java/com/rutik/systemdesign/lld/behavioral/memento/) | Captures and restores an object's state | Medium |
| [Observer](src/main/java/com/rutik/systemdesign/lld/behavioral/observer/) | Defines a subscription mechanism to notify objects | Medium |
| [State](src/main/java/com/rutik/systemdesign/lld/behavioral/state/) | Alters object behavior when its state changes | Complex |
| [Strategy](src/main/java/com/rutik/systemdesign/lld/behavioral/strategy/) | Defines a family of interchangeable algorithms | Simple |
| [Template Method](src/main/java/com/rutik/systemdesign/lld/behavioral/template_method/) | Defines the skeleton of an algorithm | Medium |
| [Visitor](src/main/java/com/rutik/systemdesign/lld/behavioral/visitor/) | Separates algorithms from object structures | Complex |
| [Interpreter](src/main/java/com/rutik/systemdesign/lld/behavioral/interpreter/) | Defines a grammar and interprets sentences | Complex |

#### Extras
| Section | Description |
|---------|-------------|
| [SOLID Principles](src/main/java/com/rutik/systemdesign/lld/solid_principles/) | The 5 foundational principles of OOP |
| [Anti-Patterns](src/main/java/com/rutik/systemdesign/lld/anti_patterns/) | Common design mistakes and how to avoid them |
| [Pattern Comparisons](src/main/java/com/rutik/systemdesign/lld/pattern_comparisons/) | Head-to-head comparisons of similar patterns |

---

### HLD (High-Level Design) - System Design Concepts

| Concept | Description |
|---------|-------------|
| [Scalability](src/main/java/com/rutik/systemdesign/hld/scalability/) | Horizontal vs Vertical scaling strategies |
| [Load Balancing](src/main/java/com/rutik/systemdesign/hld/load_balancing/) | Distributing traffic across servers |
| [Caching](src/main/java/com/rutik/systemdesign/hld/caching/) | Caching strategies and cache invalidation |
| [Database Design](src/main/java/com/rutik/systemdesign/hld/database_design/) | SQL vs NoSQL, replication, indexing |
| [Message Queues](src/main/java/com/rutik/systemdesign/hld/message_queues/) | Async communication with Kafka, RabbitMQ |
| [Microservices](src/main/java/com/rutik/systemdesign/hld/microservices/) | Microservices architecture and patterns |
| [API Design](src/main/java/com/rutik/systemdesign/hld/api_design/) | REST, GraphQL, gRPC best practices |
| [CAP Theorem](src/main/java/com/rutik/systemdesign/hld/cap_theorem/) | Consistency, Availability, Partition Tolerance |
| [Consistent Hashing](src/main/java/com/rutik/systemdesign/hld/consistent_hashing/) | Distributed hash ring for load distribution |
| [Rate Limiting](src/main/java/com/rutik/systemdesign/hld/rate_limiting/) | Throttling strategies and algorithms |
| [CDN](src/main/java/com/rutik/systemdesign/hld/cdn/) | Content Delivery Network architecture |
| [Database Sharding](src/main/java/com/rutik/systemdesign/hld/database_sharding/) | Partitioning data across databases |

---

### LLM (Large Language Models) - AI Systems Guide

A distilled, one-stop reference for everything LLM — from transformer fundamentals to production deployment, agents, safety, and real-world system design.

#### Foundations
| Topic | Key Concepts |
|-------|-------------|
| [Foundations & Architecture](src/main/java/com/rutik/systemdesign/llm/foundations_and_architecture/) | Transformers, self-attention, MoE, scaling laws, GPT/LLaMA/DeepSeek — with 3 deep-dive sub-files (Flash Attention, RoPE, training dynamics) |
| [Tokenization & Embeddings](src/main/java/com/rutik/systemdesign/llm/tokenization_and_embeddings/) | BPE, WordPiece, SentencePiece, tiktoken, vocabulary design |
| [Embeddings & Similarity Search](src/main/java/com/rutik/systemdesign/llm/embeddings_and_similarity_search/) | Sentence embeddings, contrastive learning, HNSW, FAISS, Matryoshka |

#### Training
| Topic | Key Concepts |
|-------|-------------|
| [Pre-Training](src/main/java/com/rutik/systemdesign/llm/pre_training/) | CLM/MLM/FIM objectives, data curation, Chinchilla scaling laws |
| [Training Infrastructure](src/main/java/com/rutik/systemdesign/llm/training_infrastructure/) | Tensor/pipeline/data parallelism, ZeRO, FSDP, mixed precision |
| [Synthetic Data Generation](src/main/java/com/rutik/systemdesign/llm/synthetic_data_generation/) | Self-Instruct, Evol-Instruct, LIMA insight, quality filtering |
| [Fine-Tuning](src/main/java/com/rutik/systemdesign/llm/fine_tuning/) | LoRA, QLoRA, PEFT, instruction tuning, domain adaptation |
| [Alignment & RLHF](src/main/java/com/rutik/systemdesign/llm/alignment_and_rlhf/) | RLHF, DPO, Constitutional AI, ORPO, reward hacking |

#### Using LLMs
| Topic | Key Concepts |
|-------|-------------|
| [Prompt Engineering](src/main/java/com/rutik/systemdesign/llm/prompt_engineering/) | CoT, few-shot, ReAct, self-consistency, structured outputs |
| [RAG Fundamentals](src/main/java/com/rutik/systemdesign/llm/rag_fundamentals/) | Chunking, hybrid retrieval, reranking, RAGAS evaluation |
| [Advanced RAG](src/main/java/com/rutik/systemdesign/llm/advanced_rag/) | Graph RAG, Agentic RAG, HyDE, Self-RAG, multi-query expansion |
| [Context Engineering](src/main/java/com/rutik/systemdesign/llm/context_engineering/) | Context budget allocation, "lost in the middle" fix, KV-cache-aware ordering, compaction, retrieval vs long-context decision matrix |
| [Reasoning Models](src/main/java/com/rutik/systemdesign/llm/reasoning_models/) | o1/o3, DeepSeek-R1, test-time compute, PRM/ORM, MCTS |
| [Code Generation](src/main/java/com/rutik/systemdesign/llm/code_generation/) | FIM, Copilot architecture, HumanEval, SWE-bench, StarCoder |

#### Agents & Frameworks
| Topic | Key Concepts |
|-------|-------------|
| [Agents & Tool Use](src/main/java/com/rutik/systemdesign/llm/agents_and_tool_use/) | Function calling, ReAct, plan-and-execute, memory systems |
| [Agentic Frameworks](src/main/java/com/rutik/systemdesign/llm/agentic_frameworks/) | LangChain, LangGraph, LlamaIndex, CrewAI, AutoGen, Haystack |
| [Multi-Agent Systems](src/main/java/com/rutik/systemdesign/llm/multi_agent_systems/) | Orchestrator, debate, hierarchical, ChatDev, MetaGPT, Swarm |

#### Production
| Topic | Key Concepts |
|-------|-------------|
| [Inference & Decoding](src/main/java/com/rutik/systemdesign/llm/inference_and_decoding/) | KV cache, PagedAttention, speculative decoding, continuous batching |
| [Inference Engines](src/main/java/com/rutik/systemdesign/llm/inference_engines/) | vLLM, TensorRT-LLM, llama.cpp, SGLang, Ollama, TGI |
| [Optimization & Quantization](src/main/java/com/rutik/systemdesign/llm/optimization_and_quantization/) | GPTQ, AWQ, Flash Attention, MoE, pruning, distillation |
| [Deployment & MLOps](src/main/java/com/rutik/systemdesign/llm/deployment_and_mlops/) | LLM gateway, model routing, semantic caching, observability |
| [Token Economics & Cost Optimization](src/main/java/com/rutik/systemdesign/llm/token_economics_and_cost_optimization/) | Per-token pricing, prompt caching, batch APIs, self-hosting break-even |
| [LLM Routing & Model Selection](src/main/java/com/rutik/systemdesign/llm/llm_routing_and_model_selection/) | Multi-model routing, cascade patterns, confidence thresholds |
| [Knowledge Distillation & Model Merging](src/main/java/com/rutik/systemdesign/llm/knowledge_distillation_and_model_merging/) | Teacher-student distillation, SLERP/TIES/DARE merging, structured pruning |
| [LLM Observability & Monitoring](src/main/java/com/rutik/systemdesign/llm/llm_observability_and_monitoring/) | Tracing, quality monitoring, cost attribution, alerting, Langfuse |
| [LLM Caching](src/main/java/com/rutik/systemdesign/llm/llm_caching/) | Exact-match, semantic cache, Anthropic/OpenAI prompt caching, vLLM APC, threshold tuning, invalidation |
| [Prompt Management & PromptOps](src/main/java/com/rutik/systemdesign/llm/prompt_management_and_promptops/) | Prompt versioning, registries, eval-gated CI, A/B testing, aliases, injection-safe templates |

#### Safety & Evaluation
| Topic | Key Concepts |
|-------|-------------|
| [Guardrails & Content Safety](src/main/java/com/rutik/systemdesign/llm/guardrails_and_content_safety/) | NeMo Guardrails, Llama Guard, PII detection, HIPAA compliance |
| [Safety & Alignment](src/main/java/com/rutik/systemdesign/llm/safety_and_alignment/) | Jailbreaking, hallucination, bias, prompt injection, red teaming |
| [LLM Security](src/main/java/com/rutik/systemdesign/llm/llm_security/) | Prompt injection, data extraction, model theft, supply chain, adversarial robustness |
| [Evaluation & Benchmarks](src/main/java/com/rutik/systemdesign/llm/evaluation_and_benchmarks/) | MMLU, HumanEval, RAGAS, LLM-as-judge, Chatbot Arena |

#### Advanced & Landscape
| Topic | Key Concepts |
|-------|-------------|
| [Multimodal Models](src/main/java/com/rutik/systemdesign/llm/multimodal_models/) | VLMs, CLIP, LLaVA, diffusion models, Whisper, video models |
| [Context Windows & Long Context](src/main/java/com/rutik/systemdesign/llm/context_windows_and_long_context/) | RoPE, YaRN, ALiBi, "lost in the middle", long context vs RAG |
| [AI Applications](src/main/java/com/rutik/systemdesign/llm/ai_applications/) | Healthcare, legal, finance, education, customer support, ROI |
| [LLM Ecosystem & Landscape](src/main/java/com/rutik/systemdesign/llm/llm_ecosystem_and_landscape/) | Model families, licensing, cost analysis, timeline 2017-2025 |
| [Small Language Models & Edge AI](src/main/java/com/rutik/systemdesign/llm/small_language_models_and_edge_ai/) | Phi-3/4, Gemma, on-device inference, quantization for mobile |
| [Mixture of Experts](src/main/java/com/rutik/systemdesign/llm/mixture_of_experts/) | MoE routing, Mixtral, DeepSeek-V3, load balancing, sparse activation |
| [MCP (Model Context Protocol)](src/main/java/com/rutik/systemdesign/llm/mcp_model_context_protocol/) | Universal LLM-tool protocol, servers/clients, JSON-RPC, A2A |
| [Data Flywheels & Continuous Learning](src/main/java/com/rutik/systemdesign/llm/data_flywheels_and_continuous_learning/) | Production feedback loops, active learning, drift detection, A/B testing |
| [LLM Testing Strategies](src/main/java/com/rutik/systemdesign/llm/llm_testing_strategies/) | Golden datasets, LLM-as-judge, regression eval, flakiness detection, CI/CD integration |
| [Agentic Workflow Patterns](src/main/java/com/rutik/systemdesign/llm/agentic_workflow_patterns/) | Anthropic taxonomy — chaining, routing, parallelization, orchestrator-workers, evaluator-optimizer |
| [Coding Agents](src/main/java/com/rutik/systemdesign/llm/coding_agents/) | SWE-agent ACI, OpenHands, Aider, Devin, Cursor Composer, Claude Code, SWE-bench |
| [Voice Agents](src/main/java/com/rutik/systemdesign/llm/voice_agents/) | OpenAI Realtime, Gemini Live, STT→LLM→TTS pipelines, VAD, barge-in, telephony |
| [Browser Agents Deep Dive](src/main/java/com/rutik/systemdesign/llm/browser_agents_deep_dive/) | Browser Use, Stagehand, Playwright MCP, DOM vs vision, WebArena |

#### LLM Case Studies
| Case Study | What It Covers |
|------------|---------------|
| [Design ChatGPT](src/main/java/com/rutik/systemdesign/llm/case_studies/design_chatgpt.md) | Streaming, context management, PagedAttention, tool use, safety |
| [Design GitHub Copilot](src/main/java/com/rutik/systemdesign/llm/case_studies/design_copilot.md) | FIM completions, repo RAG, speculative decoding, license filter |
| [Design RAG Pipeline](src/main/java/com/rutik/systemdesign/llm/case_studies/design_rag_pipeline.md) | Chunking, hybrid retrieval, reranking, multi-tenant, RAGAS |
| [Design AI Search Engine](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_search_engine.md) | Web fetch, source ranking, synthesis, caching, freshness |
| [Design LLM Gateway](src/main/java/com/rutik/systemdesign/llm/case_studies/design_llm_gateway.md) | Routing, semantic cache, circuit breaker, budget enforcement |
| [Design AI Coding Assistant](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_coding_assistant.md) | Completions, agent loops, sandboxed execution, privacy |
| [Design Customer Support Bot](src/main/java/com/rutik/systemdesign/llm/case_studies/design_customer_support_bot.md) | Intent routing, escalation, tool use, multilingual, CSAT |
| [Design AI Content Moderation](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_content_moderation.md) | Multi-tier filtering, toxicity classification, appeals workflow |
| [Design LLM Fine-Tuning Platform](src/main/java/com/rutik/systemdesign/llm/case_studies/design_llm_fine_tuning_platform.md) | Self-serve fine-tuning, data pipeline, distributed training |
| [Design Notion AI](src/main/java/com/rutik/systemdesign/llm/case_studies/design_notion_ai.md) | Permission-aware RAG, workspace search, multi-tenant isolation |
| [Design AI Data Analyst](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_data_analyst.md) | File upload, NL-to-SQL, code sandbox, visualization |
| [Design AI Code Review](src/main/java/com/rutik/systemdesign/llm/case_studies/design_ai_code_review.md) | PR diff analysis, security detection, CI/CD gate |
| [Design Real-Time Translation](src/main/java/com/rutik/systemdesign/llm/case_studies/design_real_time_translation.md) | Sub-1s latency, context preservation, streaming translations |

See the [LLM Master Index](src/main/java/com/rutik/systemdesign/llm/README.md) for the full 6-phase learning path and system design interview framework.

---

### Java (Pure Java) - Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **pure Java** — language internals, JVM mechanics, concurrency, collections, performance tuning, and interview patterns. No frameworks, no Spring — core Java only.

#### Phase 1 — Language Core
| Module | Key Concepts |
|--------|-------------|
| [Core Language](src/main/java/com/rutik/systemdesign/java/core_language/) | OOP, equals/hashCode contract, inner classes, polymorphism, Object methods |
| [Generics & Type System](src/main/java/com/rutik/systemdesign/java/generics_and_type_system/) | PECS, type erasure, bridge methods, wildcards, dynamic proxies |
| [Exceptions & I/O](src/main/java/com/rutik/systemdesign/java/exceptions_and_io/) | Checked/unchecked, try-with-resources, NIO.2, serialization security |

#### Phase 2 — Modern Java
| Module | Key Concepts |
|--------|-------------|
| [Java 8 Features](src/main/java/com/rutik/systemdesign/java/java8_features/) | Lambdas, Streams overview, Optional, Collectors, Date/Time API |
| [Java Streams — Deep Dive](src/main/java/com/rutik/systemdesign/java/java_streams/) | All 20+ ops, lazy eval, flatMap, reduce, Spliterator, parallel rules |
| [Functional Programming](src/main/java/com/rutik/systemdesign/java/functional_programming/) | Function composition, custom Collectors, parallel streams, immutability |
| [Java 9–21 Features](src/main/java/com/rutik/systemdesign/java/java9_to_21_features/) | Records, Sealed classes, Virtual threads, Pattern matching, JPMS |

#### Phase 3 — JVM Internals
| Module | Key Concepts |
|--------|-------------|
| [JVM Internals](src/main/java/com/rutik/systemdesign/java/jvm_internals/) | G1/ZGC algorithms, JIT tiers, Java Memory Model, class loading, object layout |

#### Phase 4 — Concurrency + Collections (Most Tested)
| Module | Key Concepts |
|--------|-------------|
| [Concurrency](src/main/java/com/rutik/systemdesign/java/concurrency/) | synchronized, volatile, ThreadPoolExecutor, CompletableFuture, CAS, virtual threads |
| [Collections Internals](src/main/java/com/rutik/systemdesign/java/collections_internals/) | HashMap treeification, ArrayList growth, fail-fast, ConcurrentHashMap |

#### Phase 5 — Performance
| Module | Key Concepts |
|--------|-------------|
| [Performance & Tuning](src/main/java/com/rutik/systemdesign/java/performance_and_tuning/) | GC tuning, JMH, heap/thread dumps, false sharing, async-profiler |

#### Phase 6 — Interview Consolidation
| Module | Key Concepts |
|--------|-------------|
| [Java Interview Patterns](src/main/java/com/rutik/systemdesign/java/java_interview_patterns/) | Immutable class, Builder, equals contract, Integer cache, enum singleton |

#### Java Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design Connection Pool](src/main/java/com/rutik/systemdesign/java/case_studies/design_connection_pool.md) | BlockingQueue, AtomicInteger, timeouts, health checks |
| [Design Rate Limiter](src/main/java/com/rutik/systemdesign/java/case_studies/design_rate_limiter_java.md) | AtomicLong, CAS, token bucket, ScheduledExecutor |
| [Design Event Bus](src/main/java/com/rutik/systemdesign/java/case_studies/design_event_bus.md) | WeakReference, CopyOnWriteArrayList, CompletableFuture, generics |
| [Design LRU Cache](src/main/java/com/rutik/systemdesign/java/case_studies/design_lru_cache_java.md) | LinkedHashMap, ConcurrentHashMap, SoftReference, ReentrantLock |

See the [Java Master Index](src/main/java/com/rutik/systemdesign/java/README.md) for the full 6-phase learning path, Java version matrix, and cross-reference map.

---

### Spring Framework — Senior Engineer & Interview Prep Guide

A comprehensive guide to mastering **Spring Framework internals**, Spring Boot, Spring Security, Spring Data, Spring Cloud, and production patterns — targeting senior engineers and interview preparation.

#### Phase 1 — Core Container
| Module | Key Concepts |
|--------|-------------|
| [IoC Container](src/main/java/com/rutik/systemdesign/spring/ioc_container/) | BeanFactory vs. ApplicationContext, bean scopes, component scan, BeanPostProcessor |
| [Bean Lifecycle](src/main/java/com/rutik/systemdesign/spring/bean_lifecycle/) | Instantiation, populate properties, BeanPostProcessor, init/destroy, @PostConstruct/@PreDestroy |
| [Dependency Injection](src/main/java/com/rutik/systemdesign/spring/dependency_injection/) | Constructor vs. field vs. setter injection, circular deps, @Qualifier, @Primary |
| [Spring Configuration](src/main/java/com/rutik/systemdesign/spring/spring_configuration/) | @Configuration, @Bean, @ComponentScan, @PropertySource, @Profile, @Conditional |

#### Phase 2 — Proxies & AOP
| Module | Key Concepts |
|--------|-------------|
| [Spring Proxies](src/main/java/com/rutik/systemdesign/spring/spring_proxies/) | JDK dynamic proxy, CGLIB, proxyTargetClass, self-invocation bypass, proxy ordering |
| [Spring AOP](src/main/java/com/rutik/systemdesign/spring/spring_aop/) | Pointcut expressions, advice types (@Around, @Before, @After), AspectJ weaving, proxy limits |

#### Phase 3 — Spring Boot
| Module | Key Concepts |
|--------|-------------|
| [Spring Boot Auto-Configuration](src/main/java/com/rutik/systemdesign/spring/spring_boot_autoconfiguration/) | @EnableAutoConfiguration, AutoConfiguration.imports, @Conditional*, custom starters |
| [Spring Boot Configuration](src/main/java/com/rutik/systemdesign/spring/spring_boot_configuration/) | @ConfigurationProperties, relaxed binding, config server integration, secrets management |
| [Spring Boot Actuator](src/main/java/com/rutik/systemdesign/spring/spring_boot_actuator/) | Health indicators, Micrometer metrics, custom endpoints, Prometheus integration |

#### Phase 4 — Spring Web
| Module | Key Concepts |
|--------|-------------|
| [Spring MVC Architecture](src/main/java/com/rutik/systemdesign/spring/spring_mvc_architecture/) | DispatcherServlet, HandlerMapping, HandlerAdapter, ViewResolver, message converters |
| [Request Handling](src/main/java/com/rutik/systemdesign/spring/request_handling/) | @RequestMapping, argument resolvers, @ControllerAdvice, exception handling, content negotiation |
| [Filters & Interceptors](src/main/java/com/rutik/systemdesign/spring/filters_and_interceptors/) | Servlet Filter vs. HandlerInterceptor, filter order, OncePerRequestFilter |
| [Spring WebFlux](src/main/java/com/rutik/systemdesign/spring/spring_webflux/) | Project Reactor, Mono/Flux, Netty event loop, RouterFunction, backpressure, WebClient |

#### Phase 5 — Spring Data & Transactions
| Module | Key Concepts |
|--------|-------------|
| [Spring Data JPA](src/main/java/com/rutik/systemdesign/spring/spring_data_jpa/) | JpaRepository, query derivation, JPQL, native queries, projections, N+1 problem |
| [Spring Transactions](src/main/java/com/rutik/systemdesign/spring/spring_transactions/) | Propagation types, isolation levels, @Transactional internals, proxy limits, distributed TX |
| [Spring Caching](src/main/java/com/rutik/systemdesign/spring/spring_caching/) | @Cacheable, @CacheEvict, @CachePut, CacheManager, Redis integration, cache stampede |

#### Phase 6 — Spring Security
| Module | Key Concepts |
|--------|-------------|
| [Spring Security Architecture](src/main/java/com/rutik/systemdesign/spring/spring_security_architecture/) | SecurityFilterChain, AuthenticationManager, SecurityContext, CSRF, CORS, method security |
| [Spring Security JWT & OAuth2](src/main/java/com/rutik/systemdesign/spring/spring_security_jwt_oauth/) | JWT validation, OAuth2 resource server, PKCE, Spring Authorization Server, token introspection |

#### Phase 7 — Spring Cloud & Messaging
| Module | Key Concepts |
|--------|-------------|
| [Spring Cloud Config](src/main/java/com/rutik/systemdesign/spring/spring_cloud_config/) | Config server, @RefreshScope, Vault integration, config encryption, Bootstrap context |
| [Spring Cloud Patterns](src/main/java/com/rutik/systemdesign/spring/spring_cloud_patterns/) | Eureka, Resilience4j (circuit breaker, retry, bulkhead), Spring Cloud Gateway, load balancer |
| [Spring Messaging](src/main/java/com/rutik/systemdesign/spring/spring_messaging/) | @KafkaListener, @RabbitListener, message converters, dead-letter queues, idempotency |

#### Phase 8 — Testing & Production
| Module | Key Concepts |
|--------|-------------|
| [Spring Testing](src/main/java/com/rutik/systemdesign/spring/spring_testing/) | @SpringBootTest, @WebMvcTest, @DataJpaTest, MockMvc, WebTestClient, Testcontainers, @MockBean |
| [Spring Performance](src/main/java/com/rutik/systemdesign/spring/spring_performance/) | Startup optimization, lazy init, virtual threads (Boot 3.2+), GraalVM native, connection pool sizing |

#### Spring Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design a Multi-Tenant SaaS API](src/main/java/com/rutik/systemdesign/spring/case_studies/design_multitenant_saas_api.md) | Request-scoped beans, per-tenant data sources, dynamic routing, security context propagation |
| [Design a Distributed Rate Limiter with Spring](src/main/java/com/rutik/systemdesign/spring/case_studies/design_distributed_rate_limiter_spring.md) | Custom AOP advice, Redis integration, Lua scripts, filter chain position |
| [Design a Secure OAuth2 Authorization Server](src/main/java/com/rutik/systemdesign/spring/case_studies/design_oauth2_authorization_server.md) | Spring Authorization Server, PKCE, refresh token rotation, opaque vs. JWT tokens |
| [Design a Spring Boot Event-Driven Microservice](src/main/java/com/rutik/systemdesign/spring/case_studies/design_event_driven_microservice.md) | Spring Kafka, transactional outbox pattern, idempotent consumers, dead-letter topics |
| [Design a Reactive API Gateway](src/main/java/com/rutik/systemdesign/spring/case_studies/design_reactive_api_gateway.md) | Spring Cloud Gateway, WebFlux, global filters, circuit breaker integration, JWT relay |

See the [Spring Master Index](src/main/java/com/rutik/systemdesign/spring/README.md) for the full 8-phase learning path, version matrix, and cross-reference map.

---

### Backend Engineering — Senior Engineer & Interview Prep Guide

A deep-dive guide to building, optimizing, inspecting, and testing production backend systems. Primary focus is Java/Spring. Covers networking fundamentals, API design, performance engineering, database internals, resilience patterns, security, testing, event-driven architecture, and microservices — with production war stories, concrete numbers, and broken-code-then-fix examples throughout.

#### Phase 1 — Networking Fundamentals
| Module | Key Concepts |
|--------|-------------|
| [OSI Model & Networking](src/main/java/com/rutik/systemdesign/backend/osi_model_and_networking/) | 7 OSI layers, TCP/IP 4-layer mapping, ARP, NAT, MTU/fragmentation, ICMP, subnetting |
| [TCP/IP Deep Dive](src/main/java/com/rutik/systemdesign/backend/tcp_ip_deep_dive/) | 3-way handshake, congestion control (CUBIC/BBR), TIME_WAIT, Nagle's algorithm, socket tuning |
| [UDP and QUIC](src/main/java/com/rutik/systemdesign/backend/udp_and_quic/) | UDP stateless model, QUIC 0-RTT/connection migration/HoL-blocking elimination, HTTP/3 |
| [HTTP Protocols](src/main/java/com/rutik/systemdesign/backend/http_protocols/) | HTTP/1.1 vs 2 vs 3, TLS 1.2 vs 1.3, HPACK, ALPN, SNI, HSTS, ETag, Cache-Control |

#### Phase 2 — API Design & Protocols
| Module | Key Concepts |
|--------|-------------|
| [REST API Design](src/main/java/com/rutik/systemdesign/backend/rest_api_design/) | REST constraints, resource modeling, versioning, idempotency, cursor pagination, RFC 7807 |
| [gRPC and Protobuf](src/main/java/com/rutik/systemdesign/backend/grpc_and_protobuf/) | Protobuf wire format, 4 RPC modes, interceptors, deadlines, health checking, gRPC-Web |
| [GraphQL](src/main/java/com/rutik/systemdesign/backend/graphql/) | Schema-first design, DataLoader N+1 fix, persisted queries, complexity limiting, federation |
| [WebSockets and SSE](src/main/java/com/rutik/systemdesign/backend/websockets_and_sse/) | WebSocket upgrade, frame structure, SSE reconnection, STOMP, Redis pub/sub fan-out |

#### Phase 3 — Performance Engineering
| Module | Key Concepts |
|--------|-------------|
| [Performance Profiling](src/main/java/com/rutik/systemdesign/backend/performance_profiling/) | async-profiler, JFR, flamegraph reading, heap/thread dump analysis, GC log parsing |
| [Connection Pooling Deep Dive](src/main/java/com/rutik/systemdesign/backend/connection_pooling_deep_dive/) | HikariCP ConcurrentBag internals, pool sizing formula, leak detection, PgBouncer |
| [Caching Strategies Deep Dive](src/main/java/com/rutik/systemdesign/backend/caching_strategies_deep_dive/) | Cache-aside/write-through/write-behind, Redis data structures, XFetch stampede prevention |
| [Async and Concurrency Patterns](src/main/java/com/rutik/systemdesign/backend/async_and_concurrency_patterns/) | Thread pool sizing, CompletableFuture pitfalls, virtual thread pinning, reactive backpressure |

#### Phase 4 — Database Engineering
| Module | Key Concepts |
|--------|-------------|
| [Database Internals and Indexing](src/main/java/com/rutik/systemdesign/backend/database_internals_and_indexing/) | B+tree, WAL mechanics, MVCC, index types (B-tree/GIN/BRIN), covering indexes, VACUUM |
| [Query Optimization](src/main/java/com/rutik/systemdesign/backend/query_optimization/) | EXPLAIN ANALYZE, N+1 detection and fixes, keyset pagination, JDBC batch inserts |
| [Database Migrations](src/main/java/com/rutik/systemdesign/backend/database_migrations/) | Flyway vs Liquibase, expand-contract pattern, CREATE INDEX CONCURRENTLY, gh-ost |
| [Distributed Transactions and Consistency](src/main/java/com/rutik/systemdesign/backend/distributed_transactions_and_consistency/) | 2PC failure modes, Saga (choreography vs orchestration), outbox pattern, idempotency keys |
| [Database Types Deep Dive](src/main/java/com/rutik/systemdesign/backend/database_types_deep_dive/) | Relational/Document/Key-Value/Wide-Column/Time-Series/Search/Graph/NewSQL — internals, tradeoffs, when to use |

#### Phase 5 — Resilience & Reliability
| Module | Key Concepts |
|--------|-------------|
| [Fault Tolerance Patterns](src/main/java/com/rutik/systemdesign/backend/fault_tolerance_patterns/) | Circuit breaker (Resilience4j), retry with jitter, bulkhead, timeout hierarchy, fallback |
| [Rate Limiting In Depth](src/main/java/com/rutik/systemdesign/backend/rate_limiting_in_depth/) | Token bucket, leaky bucket, sliding window, Redis Lua atomic scripts, adaptive throttling |
| [Observability and Monitoring](src/main/java/com/rutik/systemdesign/backend/observability_and_monitoring/) | Metrics/logs/traces, Micrometer, MDC correlation, OpenTelemetry, SLO/SLI/error budget |

#### Phase 6 — Security
| Module | Key Concepts |
|--------|-------------|
| [Backend Security (OWASP)](src/main/java/com/rutik/systemdesign/backend/backend_security_owasp/) | OWASP Top 10 2021, SQL injection fix, CSRF/XSS/SSRF, secret management, security headers |
| [Auth and Authorization Systems](src/main/java/com/rutik/systemdesign/backend/auth_and_authorization_systems/) | JWT internals, OAuth2 flows + PKCE, OIDC, opaque vs JWT, refresh rotation, RBAC vs ABAC |

#### Phase 7 — Testing & Quality
| Module | Key Concepts |
|--------|-------------|
| [Backend Testing Strategies](src/main/java/com/rutik/systemdesign/backend/backend_testing_strategies/) | Testing pyramid, test doubles taxonomy, Pact contracts, PIT mutation testing, Testcontainers |
| [Load and Performance Testing](src/main/java/com/rutik/systemdesign/backend/load_and_performance_testing/) | k6/Gatling scripting, coordinated omission, percentile analysis, capacity planning |
| [Chaos Engineering](src/main/java/com/rutik/systemdesign/backend/chaos_engineering/) | Steady-state hypothesis, fault injection taxonomy, blast radius, GameDay runbook, AWS FIS |

#### Phase 8 — Event-Driven Architecture
| Module | Key Concepts |
|--------|-------------|
| [Event-Driven Fundamentals](src/main/java/com/rutik/systemdesign/backend/event_driven_fundamentals/) | Events vs commands vs queries, event taxonomy, choreography vs orchestration, event storming |
| [Kafka Deep Dive](src/main/java/com/rutik/systemdesign/backend/kafka_deep_dive/) | Producer internals (acks/batching/idempotence), consumer rebalancing, EOS, Kafka Streams, Schema Registry |
| [Event Sourcing and CQRS](src/main/java/com/rutik/systemdesign/backend/event_sourcing_and_cqrs/) | Events as source of truth, aggregate design, snapshots, projections, event upcasting, Axon Framework |
| [Messaging Patterns](src/main/java/com/rutik/systemdesign/backend/messaging_patterns/) | Outbox pattern (polling + Debezium CDC), transactional inbox, DLQ handling, Avro schema evolution |

#### Phase 9 — Microservices Architecture
| Module | Key Concepts |
|--------|-------------|
| [Microservices Fundamentals](src/main/java/com/rutik/systemdesign/backend/microservices_fundamentals/) | When NOT to decompose, DDD bounded contexts, strangler fig, data ownership, distributed monolith |
| [API Gateway Patterns](src/main/java/com/rutik/systemdesign/backend/api_gateway_patterns/) | Gateway responsibilities, BFF pattern, API composition, Spring Cloud Gateway, Kong, AWS API GW |
| [Service Mesh and Service Discovery](src/main/java/com/rutik/systemdesign/backend/service_mesh_and_service_discovery/) | Istio/Envoy, mTLS zero-trust, xDS protocol, Eureka vs DNS vs mesh discovery, probe design |
| [Distributed System Operational Patterns](src/main/java/com/rutik/systemdesign/backend/distributed_system_operational_patterns/) | Bulkhead, sidecar, ACL, strangler fig steps, correlation ID, distributed config, graceful shutdown |
| [Container and Deployment Patterns](src/main/java/com/rutik/systemdesign/backend/container_and_deployment_patterns/) | Docker multi-stage builds, K8s rolling/blue-green/canary, probe design, resource limits, HPA/KEDA |

#### Backend Case Studies
| Case Study | Core Concepts |
|------------|--------------|
| [Design Booking System](src/main/java/com/rutik/systemdesign/backend/case_studies/design_booking_system/) | Optimistic locking, idempotency key, Redis distributed lock, race condition prevention |
| [Design Feed Service](src/main/java/com/rutik/systemdesign/backend/case_studies/design_feed_service/) | Fan-out-on-write vs fan-out-on-read, Redis sorted sets, cursor pagination, cache warming |
| [Design Payment Processor](src/main/java/com/rutik/systemdesign/backend/case_studies/design_payment_processor/) | Saga orchestration, outbox pattern, idempotency table, audit log, compensating transactions |
| [Design Event-Driven Order System](src/main/java/com/rutik/systemdesign/backend/case_studies/design_event_driven_order_system/) | Kafka choreography, CQRS read model, Avro schema evolution, DLQ handling, exactly-once |
| [Design Microservices Migration](src/main/java/com/rutik/systemdesign/backend/case_studies/design_microservices_migration/) | Strangler fig steps, Spring Cloud Gateway routing, Debezium CDC, feature flag cutover |

See the [Backend Master Index](src/main/java/com/rutik/systemdesign/backend/README.md) for the full 9-phase learning path, version matrix (Java 21, Spring Boot 3.2+, Kafka 3.6+), and cross-reference map.

---

### Database Engineering — Principal Engineer & Interview Prep Guide

A laser-focused, principal-engineer-level reference for database internals, selection strategies, production operations, distributed systems, and real-world case studies. 28 modules across 7 phases covering relational, NoSQL, emerging, and distributed database concepts.

#### Phase 1 — Foundations
| Module | Key Concepts |
|--------|-------------|
| [Database Fundamentals](src/main/java/com/rutik/systemdesign/database/database_fundamentals/) | ACID, BASE, CAP, PACELC, isolation levels, MVCC |
| [Storage Engines Internals](src/main/java/com/rutik/systemdesign/database/storage_engines_internals/) | B+tree, LSM-tree, WAL, buffer pool, row vs columnar storage |
| [Indexing Deep Dive](src/main/java/com/rutik/systemdesign/database/indexing_deep_dive/) | B+tree, GIN, BRIN, covering indexes, partial, composite, index bloat |
| [Concurrency Control & Locking](src/main/java/com/rutik/systemdesign/database/concurrency_control_and_locking/) | MVCC, deadlocks, gap locks, SELECT FOR UPDATE SKIP LOCKED, advisory locks |

#### Phase 2 — Relational Databases
| Module | Key Concepts |
|--------|-------------|
| [PostgreSQL Internals](src/main/java/com/rutik/systemdesign/database/postgresql_internals/) | VACUUM, autovacuum, EXPLAIN ANALYZE, TOAST, replication slots, partitioning |
| [MySQL InnoDB Internals](src/main/java/com/rutik/systemdesign/database/mysql_innodb_internals/) | Clustered index, redo/undo log, binary log, online DDL, GTID |
| [SQL Query Optimization](src/main/java/com/rutik/systemdesign/database/sql_query_optimization/) | Join algorithms, CBO statistics, keyset pagination, N+1, window functions |
| [Schema Design & Normalization](src/main/java/com/rutik/systemdesign/database/schema_design_and_normalization/) | Normal forms, temporal data, audit trails, multi-tenancy, JSONB |
| [Database Migrations (Zero Downtime)](src/main/java/com/rutik/systemdesign/database/database_migrations_zero_downtime/) | Flyway, Liquibase, expand-contract, gh-ost, CREATE INDEX CONCURRENTLY |

#### Phase 3 — NoSQL Databases
| Module | Key Concepts |
|--------|-------------|
| [Document Databases](src/main/java/com/rutik/systemdesign/database/document_databases/) | MongoDB WiredTiger, embedding vs referencing, aggregation pipeline, sharding |
| [Key-Value Stores](src/main/java/com/rutik/systemdesign/database/key_value_stores/) | Redis data structures, persistence (RDB/AOF), Cluster, Streams, Redlock |
| [Wide-Column Databases](src/main/java/com/rutik/systemdesign/database/wide_column_databases/) | Cassandra ring, partition key, compaction strategies, consistency levels |
| [Search Engines](src/main/java/com/rutik/systemdesign/database/search_engines/) | Inverted index, BM25, Elasticsearch ILM, aggregations, deep pagination |
| [Graph Databases](src/main/java/com/rutik/systemdesign/database/graph_databases/) | Property graph, Neo4j index-free adjacency, Cypher, fraud detection patterns |
| [Time-Series Databases](src/main/java/com/rutik/systemdesign/database/time_series_databases/) | TimescaleDB, InfluxDB, ClickHouse, Prometheus, Gorilla XOR compression |

#### Phase 4 — Emerging Databases
| Module | Key Concepts |
|--------|-------------|
| [Vector Databases](src/main/java/com/rutik/systemdesign/database/vector_databases/) | HNSW, IVF, PQ, pgvector, hybrid search, multi-tenancy, RAG integration |
| [NewSQL & Distributed SQL](src/main/java/com/rutik/systemdesign/database/newsql_and_distributed_sql/) | Spanner TrueTime, CockroachDB Raft, TiDB HTAP, YugabyteDB, global ACID |
| [In-Memory Databases](src/main/java/com/rutik/systemdesign/database/in_memory_databases/) | Redis vs Memcached, VoltDB, Ignite, eviction policies, durability modes |

#### Phase 5 — Distributed Database Concepts
| Module | Key Concepts |
|--------|-------------|
| [Replication & High Availability](src/main/java/com/rutik/systemdesign/database/replication_and_high_availability/) | Sync vs async, Patroni, split-brain prevention, replication slots, multi-region |
| [Sharding & Partitioning](src/main/java/com/rutik/systemdesign/database/sharding_and_partitioning/) | Consistent hashing, shard key selection, Vitess, celebrity shard, resharding |
| [Distributed Transactions](src/main/java/com/rutik/systemdesign/database/distributed_transactions/) | 2PC, Saga (choreography vs orchestration), outbox pattern, idempotency keys |
| [Consistency Models & Consensus](src/main/java/com/rutik/systemdesign/database/consistency_models_and_consensus/) | Linearizability, Raft, Paxos, CRDTs, vector clocks, fencing tokens |
| [Database Caching Patterns](src/main/java/com/rutik/systemdesign/database/database_caching_patterns/) | Cache-aside, write-through, write-behind, stampede prevention, hot key |

#### Phase 6 — Production Operations
| Module | Key Concepts |
|--------|-------------|
| [Connection Pool Management](src/main/java/com/rutik/systemdesign/database/connection_pool_management/) | HikariCP internals, pool sizing formula, PgBouncer transaction mode, K8s storm |
| [Database Performance Tuning](src/main/java/com/rutik/systemdesign/database/database_performance_tuning/) | shared_buffers, work_mem, checkpoint tuning, autovacuum, slow query analysis |
| [Backup, Recovery & Disaster Recovery](src/main/java/com/rutik/systemdesign/database/backup_recovery_and_disaster_recovery/) | PITR, WAL-G, pgBackRest, RPO/RTO measurement, restore drills |
| [Database Security & Compliance](src/main/java/com/rutik/systemdesign/database/database_security_and_compliance/) | RLS, scram-sha-256, pgAudit, HashiCorp Vault, GDPR erasure, TDE |

#### Phase 7 — Architecture & Selection
| Module | Key Concepts |
|--------|-------------|
| [Database Selection Framework](src/main/java/com/rutik/systemdesign/database/database_selection_framework/) | Selection matrix, benchmark traps, TCO analysis, scorecard methodology |
| [Polyglot Persistence Patterns](src/main/java/com/rutik/systemdesign/database/polyglot_persistence_patterns/) | CQRS, CDC (Debezium), dual-write failure modes, event sourcing, data mesh |

#### Database Case Studies
| Case Study | Key Databases | Core Concepts | Level |
|------------|--------------|---------------|-------|
| [Banking Ledger](src/main/java/com/rutik/systemdesign/database/case_studies/design_banking_ledger/) | PostgreSQL, Redis | Double-entry bookkeeping, SERIALIZABLE isolation, idempotency, RPO=0 | Expert |
| [E-Commerce Catalog](src/main/java/com/rutik/systemdesign/database/case_studies/design_ecommerce_catalog/) | PostgreSQL, Elasticsearch, Redis | Polyglot persistence, CDC sync, inventory counters, full-text search | Advanced |
| [Social Media Feed Storage](src/main/java/com/rutik/systemdesign/database/case_studies/design_social_media_feed_storage/) | Cassandra, Redis, PostgreSQL | Fan-out on write vs read, celebrity problem, TWCS, trending leaderboards | Advanced |
| [Real-Time Analytics Platform](src/main/java/com/rutik/systemdesign/database/case_studies/design_realtime_analytics_platform/) | ClickHouse, Kafka, Redis | Columnar storage, materialized views, HyperLogLog, tenant isolation | Expert |
| [Multi-Tenant SaaS Database](src/main/java/com/rutik/systemdesign/database/case_studies/design_multitenant_saas_database/) | PostgreSQL (RLS/schema/DB), PgBouncer | Three-tier isolation, RLS, schema-per-tenant, connection pooling at scale | Expert |
| [Monolith to Polyglot Migration](src/main/java/com/rutik/systemdesign/database/case_studies/design_monolith_to_polyglot_migration/) | MySQL, PostgreSQL, Elasticsearch, ClickHouse | Strangler fig, CDC, dual-write, validation, zero-downtime migration | Expert |

See the [Database Engineering Master Index](src/main/java/com/rutik/systemdesign/database/README.md) for the full 7-phase learning path, version matrix, and cross-reference map.

---

### Machine Learning (ML) — Senior ML/AI Engineer & Interview Prep Guide

A comprehensive, senior-engineer-level guide to Machine Learning — from mathematical foundations through production MLOps. Covers classical algorithms, deep learning, ML system design, domain specializations (CV, RecSys, NLP, RL, time series), and 10 end-to-end case studies. Deliberately scoped to not overlap with the LLM section (which handles transformers, fine-tuning, RAG, and agents).

#### Phase 1 — Mathematical Foundations
| Module | Key Concepts |
|--------|-------------|
| [Linear Algebra and Calculus](src/main/java/com/rutik/systemdesign/ml/linear_algebra_and_calculus/) | Vectors, matrices, eigendecomposition, SVD, gradients, Jacobians, Hessians, chain rule |
| [Probability and Statistics](src/main/java/com/rutik/systemdesign/ml/probability_and_statistics/) | Distributions, Bayes theorem, MLE, MAP, hypothesis testing, confidence intervals, CLT |
| [Optimization Theory](src/main/java/com/rutik/systemdesign/ml/optimization_theory/) | SGD, momentum, Adam, AdamW, LR schedules, convexity, saddle points, second-order methods |
| [Information Theory](src/main/java/com/rutik/systemdesign/ml/information_theory/) | Entropy, cross-entropy loss derivation, KL divergence, mutual information, information gain |

#### Phase 2 — Classical ML (Most Interview-Tested)
| Module | Key Concepts |
|--------|-------------|
| [Supervised Learning](src/main/java/com/rutik/systemdesign/ml/supervised_learning/) | Linear/logistic regression, SVM, decision trees, KNN, Naive Bayes — with 4 deep-dive sub-files |
| [Ensemble Methods](src/main/java/com/rutik/systemdesign/ml/ensemble_methods/) | Random Forest, XGBoost, LightGBM, CatBoost, stacking, blending — with 4 deep-dive sub-files |
| [Unsupervised Learning](src/main/java/com/rutik/systemdesign/ml/unsupervised_learning/) | k-means, DBSCAN, hierarchical clustering, PCA, t-SNE, UMAP |
| [Feature Engineering](src/main/java/com/rutik/systemdesign/ml/feature_engineering/) | Encoding, scaling, imputation, target encoding, feature selection, Pipeline patterns |
| [Model Evaluation and Selection](src/main/java/com/rutik/systemdesign/ml/model_evaluation_and_selection/) | Cross-validation, AUC-ROC/AUC-PR, calibration, bias-variance, Optuna hyperparameter search |

#### Phase 3 — Deep Learning Foundations
| Module | Key Concepts |
|--------|-------------|
| [Neural Network Fundamentals](src/main/java/com/rutik/systemdesign/ml/neural_network_fundamentals/) | MLPs, backpropagation, activations, weight initialization, batch norm, dropout |
| [Convolutional Neural Networks](src/main/java/com/rutik/systemdesign/ml/convolutional_neural_networks/) | Conv2D, pooling, ResNet skip connections, EfficientNet compound scaling, transfer learning |
| [Recurrent Neural Networks](src/main/java/com/rutik/systemdesign/ml/recurrent_neural_networks/) | LSTM, GRU, vanishing gradients, bidirectional, seq2seq, teacher forcing, CTC loss |
| [Training Deep Networks](src/main/java/com/rutik/systemdesign/ml/training_deep_networks/) | LR warmup, gradient clipping, mixed precision, data augmentation, gradient accumulation |
| [Generative Models](src/main/java/com/rutik/systemdesign/ml/generative_models/) | VAEs, GANs, Diffusion (DDPM), mode collapse, FID score, classifier-free guidance |

#### Phase 4 — Domain Specializations
| Module | Key Concepts |
|--------|-------------|
| [Computer Vision](src/main/java/com/rutik/systemdesign/ml/computer_vision/) | Object detection, segmentation, ViT, CLIP, self-supervised vision — with 4 deep-dive sub-files |
| [Natural Language Processing](src/main/java/com/rutik/systemdesign/ml/natural_language_processing/) | Word2Vec, GloVe, TF-IDF, text classification, NER (BIO tagging), CRF, topic modeling — with 4 deep-dive sub-files (BERT, attention/seq2seq, retrieval, evaluation) |
| [Recommender Systems](src/main/java/com/rutik/systemdesign/ml/recommender_systems/) | Two-tower retrieval, collaborative filtering, LTR ranking, bandits — with 5 deep-dive sub-files |
| [Time Series Forecasting](src/main/java/com/rutik/systemdesign/ml/time_series_forecasting/) | ARIMA, Prophet, DeepAR, Temporal Fusion Transformer, walk-forward validation |
| [Reinforcement Learning](src/main/java/com/rutik/systemdesign/ml/reinforcement_learning/) | MDP, Q-learning, DQN, PPO, actor-critic, reward shaping, RLHF connection |

#### Phase 5 — ML Systems and Infrastructure
| Module | Key Concepts |
|--------|-------------|
| [ML System Design](src/main/java/com/rutik/systemdesign/ml/ml_system_design/) | 6-step design framework, feature stores, A/B testing, latency budgets — with 5 deep-dive sub-files |
| [Data Pipelines and Processing](src/main/java/com/rutik/systemdesign/ml/data_pipelines_and_processing/) | PySpark, Great Expectations, DVC, schema evolution, data validation, Lambda vs Kappa |
| [Distributed Training](src/main/java/com/rutik/systemdesign/ml/distributed_training/) | PyTorch DDP, FSDP, DeepSpeed ZeRO stages, gradient accumulation, mixed precision BF16 |
| [Experiment Tracking and Versioning](src/main/java/com/rutik/systemdesign/ml/experiment_tracking_and_versioning/) | MLflow, W&B, Optuna TPE, DVC, reproducibility checklist, hyperparameter sweeps |
| [GPU and Hardware Optimization](src/main/java/com/rutik/systemdesign/ml/gpu_and_hardware_optimization/) | CUDA, tensor cores, memory hierarchy, profiling, gradient checkpointing, DataLoader tuning |

#### Phase 6 — Production ML Engineering
| Module | Key Concepts |
|--------|-------------|
| [Model Serving and Inference](src/main/java/com/rutik/systemdesign/ml/model_serving_and_inference/) | TorchServe, ONNX, gRPC, dynamic batching, A/B testing, canary, shadow mode |
| [Model Compression and Efficiency](src/main/java/com/rutik/systemdesign/ml/model_compression_and_efficiency/) | PTQ, QAT, pruning, knowledge distillation, TensorRT, low-rank factorization |
| [Monitoring and Drift Detection](src/main/java/com/rutik/systemdesign/ml/monitoring_and_drift_detection/) | Data drift, concept drift, PSI, KS test, SHAP attribution drift, delayed label handling |
| [MLOps and CI/CD](src/main/java/com/rutik/systemdesign/ml/mlops_and_ci_cd/) | MLflow Registry, Kubeflow Pipelines, Vertex AI, canary deployment, rollback, data validation gates |

#### Phase 7 — Advanced Topics
| Module | Key Concepts |
|--------|-------------|
| [Graph Neural Networks](src/main/java/com/rutik/systemdesign/ml/graph_neural_networks/) | GCN, GraphSAGE, GAT, GIN, message passing, oversmoothing, PyTorch Geometric |
| [Self-Supervised and Contrastive Learning](src/main/java/com/rutik/systemdesign/ml/self_supervised_and_contrastive_learning/) | NT-Xent, InfoNCE, BYOL, ELECTRA, graph SSL, tabular SSL (SCARF) |
| [Causal Inference and ML](src/main/java/com/rutik/systemdesign/ml/causal_inference_and_ml/) | Potential outcomes, propensity scores, uplift modeling, CausalForest, Double ML |

#### Phase 8 — Interview Consolidation
| Module | Key Concepts |
|--------|-------------|
| [ML Interview Patterns](src/main/java/com/rutik/systemdesign/ml/ml_interview_patterns/) | 6-step design framework, debug checklist, system design templates, tradeoff tables |

#### ML Case Studies
| Case Study | Core ML Concepts |
|------------|-----------------|
| [Design Recommendation Engine](src/main/java/com/rutik/systemdesign/ml/case_studies/design_recommendation_engine.md) | Two-tower retrieval, LightGBM ranking, FAISS ANN, MMR diversity, cold start |
| [Design Fraud Detection](src/main/java/com/rutik/systemdesign/ml/case_studies/design_fraud_detection.md) | Imbalanced classification, SMOTE, Flink streaming features, threshold optimization |
| [Design Search Ranking](src/main/java/com/rutik/systemdesign/ml/case_studies/design_search_ranking.md) | LambdaMART, BM25 + dense hybrid, RRF, IPW position bias, LTR |
| [Design Image Classification Pipeline](src/main/java/com/rutik/systemdesign/ml/case_studies/design_image_classification_pipeline.md) | EfficientNet, DDP training, ONNX, TorchServe batching, drift detection |
| [Design Ads CTR Prediction](src/main/java/com/rutik/systemdesign/ml/case_studies/design_ads_click_prediction.md) | DeepFM, feature hashing, Platt calibration, real-time serving, online learning |
| [Design Anomaly Detection](src/main/java/com/rutik/systemdesign/ml/case_studies/design_anomaly_detection.md) | Isolation Forest, Autoencoder, STL decomposition, CUSUM, alert correlation |
| [Design Demand Forecasting](src/main/java/com/rutik/systemdesign/ml/case_studies/design_demand_forecasting.md) | Global LightGBM, lag features, MinT reconciliation, cold start, walk-forward CV |
| [Design Content Feed Ranking](src/main/java/com/rutik/systemdesign/ml/case_studies/design_content_feed_ranking.md) | MMOE multi-task, DPP diversity, position bias IPW, feedback loop handling |
| [Design Autonomous Driving Perception](src/main/java/com/rutik/systemdesign/ml/case_studies/design_autonomous_driving_perception.md) | Sensor fusion, Kalman filter, Hungarian tracking, 3D detection, safety margins |
| [Design ML Platform](src/main/java/com/rutik/systemdesign/ml/case_studies/design_ml_platform.md) | Feature store, Kubeflow, MLflow registry, A/B routing, GPU cost tracking |

See the [ML Master Index](src/main/java/com/rutik/systemdesign/ml/README.md) for the full 8-phase learning path, sub-files index, and LLM/ML non-overlap boundary.

---

## How to Use This Repository

1. **Learning Path**: Start with SOLID principles, then move to Creational -> Structural -> Behavioral patterns
2. **Each Pattern Contains**:
   - `README.md` - Comprehensive explanation with UML, pros/cons, pitfalls, tradeoffs, when/where to use, interview tips
   - `Template.java` - Clean pattern skeleton you can copy and adapt
   - `RealWorldExample.java` - Practical implementation from real software systems
3. **Interview Prep**: Use the pattern comparisons and decision flowcharts to solidify understanding
4. **HLD Section**: System design concepts with diagrams and real-world case studies

## Quick Reference: Pattern Selection Flowchart

```
Need to create objects?
  |-> Need exactly one instance? -> Singleton
  |-> Need to create families of related objects? -> Abstract Factory
  |-> Need to create one of several related objects? -> Factory Method
  |-> Need to construct complex objects step by step? -> Builder
  |-> Need to copy existing objects? -> Prototype

Need to compose objects?
  |-> Need to make incompatible things work together? -> Adapter
  |-> Need to separate abstraction from implementation? -> Bridge
  |-> Need tree structures? -> Composite
  |-> Need to add responsibilities dynamically? -> Decorator
  |-> Need a simple interface to a complex system? -> Facade
  |-> Need to share objects to save memory? -> Flyweight
  |-> Need to control access to an object? -> Proxy

Need to manage communication?
  |-> Need to pass requests through a pipeline? -> Chain of Responsibility
  |-> Need to parameterize/queue/undo operations? -> Command
  |-> Need to traverse a collection? -> Iterator
  |-> Need to reduce coupling between components? -> Mediator
  |-> Need to save/restore state? -> Memento
  |-> Need to notify objects about events? -> Observer
  |-> Need to change behavior based on state? -> State
  |-> Need to swap algorithms at runtime? -> Strategy
  |-> Need to define algorithm skeleton, defer steps? -> Template Method
  |-> Need to add operations to object structures? -> Visitor
  |-> Need to evaluate language/expressions? -> Interpreter
```

## Contributing

Feel free to add more patterns, examples, or improve existing documentation. Ensure each addition follows the established format.
