# Spring Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/spring/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 39 Modules

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
| `validation_and_error_handling/` | 4 | Bean Validation (JSR-380), @Valid/@Validated, validation groups, custom ConstraintValidator, ProblemDetail (RFC 7807) |
| `spring_data_jpa/` | 5 | JpaRepository, N+1, projections, locking, Specifications |
| `spring_transactions/` | 5 | Propagation, isolation, self-invocation, rollback rules |
| `spring_caching/` | 5 | @Cacheable, @CacheEvict, RedisCacheManager, stampede prevention |
| `spring_security_architecture/` | 6 | FilterChainProxy, AuthenticationManager, CSRF, method security |
| `spring_security_jwt_oauth/` | 6 | JWT, OAuth2 resource server, PKCE, refresh token rotation |
| `spring_cloud_config/` | 7 | Config Server, @RefreshScope, Spring Cloud Bus, Vault |
| `spring_cloud_patterns/` | 7 | Gateway, Resilience4j, Feign, Eureka, Micrometer Tracing |
| `spring_messaging/` | 7 | Kafka, RabbitMQ, Spring Cloud Stream, @Async, WebSocket |
| `spring_batch/` | 7 | Job/Step/chunk model, ItemReader/Processor/Writer, @StepScope/@JobScope, JobRepository, skip/retry, partitioning |
| `spring_events_and_scheduling/` | 7 | ApplicationEventPublisher, @EventListener, @TransactionalEventListener, @Scheduled/@Async, ShedLock |
| `spring_testing/` | 8 | @SpringBootTest, test slices, MockMvc, @MockBean, Testcontainers |
| `spring_performance/` | 8 | HikariCP tuning, lazy init, GraalVM native, virtual threads |
| `observability_and_tracing/` | 8 | Micrometer Observation API, Micrometer Tracing + OTLP, W3C traceparent, structured logging, exemplars |
| `spring_ai/` | 7 | ChatClient fluent API, prompt templates, structured output, VectorStore + RAG advisors, @Tool function calling, model routing via beans (Spring AI 1.0 GA) |
| `spring_native_graalvm/` | 8 | AOT processing, reachability metadata (reflection/resource/proxy hints), build-time vs runtime init, tracing agent, startup/memory vs peak-throughput tradeoff (Boot 3.0+) |
| `spring_integration/` | 7 | EIP: message channels, channel adapters/gateways, router/splitter/aggregator/transformer/filter, Java DSL; contrast with spring_messaging (Kafka/Rabbit) |
| `spring_modulith/` | 3 | Modular monolith: @ApplicationModule, ArchUnit verification, @ApplicationModuleListener, event publication registry, @ApplicationModuleTest, Documenter (Boot 3.1+) |
| `spring_graphql/` | 4 | Schema-first @QueryMapping/@MutationMapping/@SubscriptionMapping/@SchemaMapping, @BatchMapping + DataLoader (N+1), subscriptions (WebSocket/SSE), cursor pagination, error handling |
| `spring_hateoas_rest_maturity/` | 4 | Richardson Maturity Model L0-L3, Spring HATEOAS (EntityModel/CollectionModel/Link, WebMvcLinkBuilder, assemblers, affordances), HAL/HAL-FORMS, @HttpExchange/RestClient, ProblemDetail, API versioning |
| `spring_grpc/` | 4 | @GrpcService beans, server/channel autoconfig, Server/ClientInterceptor + ordering, @GrpcExceptionHandler Status mapping, deadlines/cancellation, 4 streaming modes, Security + Micrometer tracing, gRPC vs REST |
| `spring_session/` | 6 | SessionRepositoryFilter, Redis/JDBC/Hazelcast/Mongo backends, serialization, session fixation (changeSessionId), concurrent-session control, cookie vs header id, WebSession, stateless-JWT vs stateful tradeoff |
| `spring_data_nosql/` | 5 | Spring Data MongoDB (MongoTemplate, aggregation, transactions) + Redis (RedisTemplate, @RedisHash, pub/sub) + reactive repositories; NoSQL vs JPA |
| `database_migrations/` | 5 | Flyway (versioned/repeatable, checksums) + Liquibase (changesets, rollback), expand-contract zero-downtime, Boot integration, Testcontainers |
| `spring_websocket_stomp/` | 7 | WebSocket handshake, STOMP over WebSocket, simple vs external broker relay, SockJS, per-user destinations, scaling/sticky sessions |
| `spring_http_clients/` | 4 | RestTemplate vs WebClient vs RestClient (6.1) vs @HttpExchange, connection pooling, timeouts, error handling, MockRestServiceServer |

**Deep-dive sub-files** (group under parent module's game topic; NO separate `STUDY_ORDER` entry):
- `spring_webflux/rsocket_reactive_messaging.md` — RSocket 4 interaction models, protocol-level backpressure (`REQUEST_N`), `@MessageMapping`/`RSocketRequester`, RSocket vs gRPC/WebSocket.
- `request_handling/i18n_and_localization.md` — `MessageSource`, `LocaleResolver`/`LocaleChangeInterceptor`, `LocaleContextHolder`, locale-aware validation + number/date formatting.

---

## Learning Paths (Full + Interview-Specific)

`README.md` documents two routes: the **Full Path** (all 39 modules = "8-Phase Learning
Path") and a curated **Interview-Specific Path** (21 modules). The interview subset is a
**dual-source list** — it lives in both `README.md` ("## Learning Paths") and
`game/app.js` (`STUDY_PATHS.spring.interview`, which drives the game's Study Full/Interview
toggle). **Change one, change the other** — same modules, same order. Non-Q&A narrative
only; no `extract.py` re-run needed. The README also carries a Knowledge-Question Map and
a 5-week Study Plan (interview-readiness prose; no toggle impact).

---

## Planned / Missing Topics (not yet created)

No material interview gaps — every senior Spring topic has a module. The 2026-07-07
interview-path audit noted only these lower-yield candidates (roadmap, not a queue; build
only on explicit request following the adding guide below):

| Topic | Priority | Note |
|-------|----------|------|
| Spring Data non-JPA (MongoDB/Redis/reactive repositories) | DONE (2026-07-07) | Built as `spring_data_nosql/` |
| Database migrations (Flyway/Liquibase) as a module | DONE (2026-07-07) | Built as `database_migrations/` |
| Spring WebSocket/STOMP as a module | DONE (2026-07-07) | Built as `spring_websocket_stomp/` |
| HTTP clients deep-dive (RestClient/WebClient/`@HttpExchange`) | DONE (2026-07-07) | Built as `spring_http_clients/` |

---

## Case Studies — 9 Total

`case_studies/` directory — all use the 11-section principal template.

| File | Primary Pattern |
|------|----------------|
| `design_multitenant_api/` | Schema-per-tenant, RLS, datasource routing |
| `design_event_driven_microservice/` | Kafka + outbox + Saga orchestration |
| `design_api_gateway/` | Spring Cloud Gateway, filter chain, rate limiting |
| `design_batch_pipeline/` | Spring Batch, chunk model, partitioned steps |
| `design_distributed_caching/` | Redis cluster, stampede prevention, L1/L2 |
| `design_distributed_rate_limiter_spring/` | Redis Lua, sliding window, token bucket |
| `design_oauth2_authorization_server/` | Spring Authorization Server, PKCE, token introspection |
| `design_idempotent_payment_api/` | Idempotency keys, saga, outbox, exactly-once |
| `design_realtime_notification_service/` | WebSocket, SSE, Redis pub/sub, fan-out |

Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

---

## Cross-Cutting Shared Primitives — 4 Files

`case_studies/cross_cutting/` — all use the 14-section template:

| File | When Relevant |
|------|--------------|
| `otel_observability_for_spring/` | Any case study with observability requirements |
| `resilience4j_patterns/` | Any case study with circuit breaker, retry, or bulkhead |
| `testcontainers_and_test_strategy/` | Any case study with integration tests |
| `zero_downtime_deploys_and_config/` | Any case study with deployment or config management |

---

## Cross-Reference Map

| Spring Module | See Also (other sections) |
|--------------|--------------------------|
| `spring_data_jpa/` | `../../database/sql_query_optimization/` — EXPLAIN ANALYZE, N+1 at DB level; `../../database/indexing_deep_dive/` — index selection |
| `spring_caching/` | `../../database/database_caching_patterns/` — cache-aside, stampede theory; `../../database/key_value_stores/` — Redis internals |
| `spring_transactions/` | `../../database/concurrency_control_and_locking/` — MVCC, gap locks; `../../database/consistency_models_and_consensus/` — distributed consistency |
| `spring_messaging/` | `../../backend/kafka_deep_dive/` — Kafka EOS, Schema Registry; `../../backend/event_driven_fundamentals/` — choreography vs orchestration |
| `spring_webflux/` | `../../java/reactive_programming/` — Project Reactor internals (Flux/Mono, Schedulers, backpressure); `../../java/structured_concurrency_and_loom/` — virtual-thread alternative to reactive |
| `spring_security_jwt_oauth/` | `../../backend/auth_and_authorization_systems/` — OAuth2 flows, OIDC, token revocation |
| `spring_events_and_scheduling/` | `../../lld/behavioral/observer/` — Observer pattern theory behind ApplicationEvent |
| `spring_proxies/` | `../../lld/structural/proxy/` — Proxy pattern theory (virtual/protection/remote) |
| `spring_batch/` | `../../lld/behavioral/template_method/` — Template Method pattern theory |
| `filters_and_interceptors/` | `../../lld/behavioral/chain_of_responsibility/` — Chain of Responsibility theory |
| `spring_aop/` | `../../lld/structural/proxy/` — AOP proxy mechanics rooted in Proxy pattern |
| `spring_ai/` | `../../llm/advanced_rag/` — RAG retrieval/rerank theory behind QuestionAnswerAdvisor; `../../llm/agentic_frameworks/` — tool/function calling and agent loops; `../../llm/embeddings_and_similarity_search/` — vector store internals |
| `spring_native_graalvm/` | `../../java/annotation_processing/` — compile-time codegen, the same build-time philosophy as AOT; `../../java/jvm_internals/` — JIT vs AOT tradeoffs |
| `spring_integration/` | `../../backend/event_driven_fundamentals/` — EIP and choreography vs orchestration; `../../java/microservices_patterns/` — outbox/saga durability theme shared with the aggregator |
| `spring_modulith/` | `../../lld/solid_principles/` — dependency inversion/separation Modulith enforces mechanically; `../../java/microservices_patterns/` — transactional-outbox mirrored by the event publication registry |
| `spring_graphql/` | `../../backend/graphql/` — GraphQL architecture (federation, schema design, query cost); `../../backend/rest_api_design/` — REST vs GraphQL tradeoffs |
| `dependency_injection/` | `../../lld/solid_principles/` — Dependency Inversion Principle; `../../lld/creational/factory_method/` — BeanFactory as Factory Method |
| `bean_lifecycle/` | `../../lld/creational/singleton/` — Singleton scope theory |
| `spring_hateoas_rest_maturity/` | `../../backend/rest_api_design/` — REST maturity, versioning; `../../hld/api_design/` — API design at scale; `request_handling/` — content negotiation |
| `spring_grpc/` | `../../java/grpc_protobuf/` — Protobuf wire format, stubs, RPC modes (pure-Java depth); `../../backend/grpc_and_protobuf/` — LB/mesh; `observability_and_tracing/` — trace propagation |
| `spring_session/` | `spring_security_architecture/` — filter chain, session fixation; `spring_security_jwt_oauth/` — stateless alternative; `../../backend/auth_and_authorization_systems/`; `../../database/key_value_stores/` — Redis backend |

---

## Spring Version Tags

When covering a Spring feature, always note the version:
- **Spring Boot 2.7 / Spring Framework 5.3** — last `javax.*` baseline, `spring.factories`
- **Spring Boot 3.0 / Spring Framework 6.0** — `jakarta.*` namespace, `AutoConfiguration.imports`, `WebSecurityConfigurerAdapter` removed, lambda DSL required, JDK 17 baseline
- **Spring Boot 3.1+** — Testcontainers support out-of-the-box, virtual threads preview
- **Spring Boot 3.2+** — Virtual threads GA (`spring.threads.virtual.enabled=true`), RestClient
- **Spring Boot 3.3+** — Spring AI GA, CDS support, service connection improvements

## Q&A Minimums (Spring-specific)

- **All modules: 15+ Q&As** (absolute floor)
- **Interview-critical modules (20+ required):** `spring_data_jpa`, `spring_transactions`, `spring_security_architecture`, `spring_aop`
- **Deep modules (18+ required):** `spring_security_jwt_oauth`, `spring_webflux`, `spring_caching`, `spring_cloud_patterns`, `spring_proxies`
- Order Q&As by interview frequency: self-invocation/proxy traps first, then propagation/isolation mechanics, then edge cases last

## Adding a New Spring Module

1. Create `<module_name>/README.md` — 14-section template
2. Meet the Q&A minimum for the module's depth level. **First answer sentence
   must be self-contained and 15–220 chars** or `extract.py` silently drops the
   Q&A from the game bank.
3. Add a row to the module table in `README.md` (the Spring master index)
4. Place it in the correct phase in the phase diagram
5. Add cross-references in the Cross-Reference Map in `README.md`
6. Update root `README.md` Phase table under the Spring section
7. Update `spring/CLAUDE.md` module table (this file)
8. **REQUIRED for the game:** add the new module id (`spring/<module_name>`) to
   `STUDY_ORDER.spring` in `game/app.js` at its phase position — a module missing
   from that array sorts to the end. (A new **sub-file** `<module>/<name>.md`
   needs no `STUDY_ORDER` entry; it groups under its parent module's topic and its
   Q&As merge into that module's bank.)
9. **Re-run `python3 game/extract.py`** to regenerate the question bank.

---

## Diagrams — Appeal-First (Mermaid preferred)

**Owner policy (2026-07-02), supersedes the old ASCII-only rule.** Section 5
(Architecture Diagrams) and any hard-to-picture concept should use the most
visually appealing renderable form that conveys the info accurately. In practice
the **Mermaid family is preferred** — `sequenceDiagram` for actor chains
(`@Transactional` proxy, DispatcherServlet request path, OAuth2/JWT flows, 2PC),
`flowchart` for pipelines (FilterChainProxy, auto-configuration import selection,
gateway filter chain), `stateDiagram-v2` for lifecycles (bean lifecycle,
persistence-context entity states, circuit-breaker), `xychart-beta` for magnitude
comparisons. Run `/mermaid-diagrams` before authoring or converting any diagram —
it has the One-Dark `classDef` palette, supported types, and gotchas.

**Reader contract:** Mermaid renders in the game reader (v11, pitch-black
surface). Colour **every** flowchart node with the One-Dark `classDef` — the reader
auto-tints only the nodes you leave unstyled (authored colours are always respected,
so it degrades per node, never a flat-grey bail), but its order-based hues are
arbitrary, so hand-colour all nodes for semantic consistency. Never set a light
background inside a diagram.

**Keep ASCII only** for shapes Mermaid cannot draw — the `README.md` 8-Phase
learning-path layout map, the `case_studies/README.md` dependency-map trees, the
transaction-propagation REQUIRED/REQUIRES_NEW/NESTED timeline, the N+1
annotated-log, constraint grids, and alignment-critical layout maps. Validate
those with the `/visual-intuition-diagrams` skill's `diagram_tools.py check`
(ASCII only, no tabs/emojis, widest line ≤ 100 cols, caption every diagram). Full
policy in root `CLAUDE.md` → "Mermaid Diagrams" and "Visual Intuition Diagrams".
