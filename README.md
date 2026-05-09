# System Design Master Repository

A comprehensive, one-stop repository for learning **Low-Level Design (LLD)**, **High-Level Design (HLD)**, and **Large Language Models (LLM)** — with practical examples, real-world scenarios, and interview preparation material.

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
| [Foundations & Architecture](src/main/java/com/rutik/systemdesign/llm/foundations_and_architecture/) | Transformers, self-attention, MoE, scaling laws, GPT/LLaMA/DeepSeek |
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

#### Safety & Evaluation
| Topic | Key Concepts |
|-------|-------------|
| [Guardrails & Content Safety](src/main/java/com/rutik/systemdesign/llm/guardrails_and_content_safety/) | NeMo Guardrails, Llama Guard, PII detection, HIPAA compliance |
| [Safety & Alignment](src/main/java/com/rutik/systemdesign/llm/safety_and_alignment/) | Jailbreaking, hallucination, bias, prompt injection, red teaming |
| [Evaluation & Benchmarks](src/main/java/com/rutik/systemdesign/llm/evaluation_and_benchmarks/) | MMLU, HumanEval, RAGAS, LLM-as-judge, Chatbot Arena |

#### Advanced & Landscape
| Topic | Key Concepts |
|-------|-------------|
| [Multimodal Models](src/main/java/com/rutik/systemdesign/llm/multimodal_models/) | VLMs, CLIP, LLaVA, diffusion models, Whisper, video models |
| [Context Windows & Long Context](src/main/java/com/rutik/systemdesign/llm/context_windows_and_long_context/) | RoPE, YaRN, ALiBi, "lost in the middle", long context vs RAG |
| [AI Applications](src/main/java/com/rutik/systemdesign/llm/ai_applications/) | Healthcare, legal, finance, education, customer support, ROI |
| [LLM Ecosystem & Landscape](src/main/java/com/rutik/systemdesign/llm/llm_ecosystem_and_landscape/) | Model families, licensing, cost analysis, timeline 2017-2025 |

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
