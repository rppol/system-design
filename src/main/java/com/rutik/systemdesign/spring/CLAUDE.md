# Spring Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/spring/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

---

## Module List — 27 Modules

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

---

## Planned / Missing Topics (not yet created)

The following topics are identified as senior Spring engineer coverage gaps:

| Module Directory | Phase | Topic | Priority |
|-----------------|-------|-------|---------|
| `spring_ai/` | 7 | Spring AI framework (2024+): ChatClient, RAG chains, VectorStore, prompt templates, model routing via Spring beans — bridges Java↔LLM | Critical |
| `spring_native_graalvm/` | 8 | AOT compilation, static proxy generation, native hints, reflection config, native images for serverless (Spring Boot 3.0+) | High |
| `spring_integration/` | 7 | Message channels, channel adapters, routers, transformers, EAI patterns — distinct from spring_messaging (Kafka/RabbitMQ) | High |
| `spring_modulith/` | 3 | Modular monolith: @ApplicationModule, ArchUnit integration, module tests, event-based decoupling (Spring Boot 3.1+, 2023 release) | Medium |
| `spring_graphql/` | 4 | Spring for GraphQL: @QueryMapping, DataLoader, subscriptions, schema-first vs annotation-driven | Medium |

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
| `spring_webflux/` | `../../java/reactive_programming/` — Project Reactor internals (once created) |
| `spring_security_jwt_oauth/` | `../../backend/auth_and_authorization_systems/` — OAuth2 flows, OIDC, token revocation |
| `spring_events_and_scheduling/` | `../../lld/behavioral/observer/` — Observer pattern theory behind ApplicationEvent |
| `spring_proxies/` | `../../lld/structural/proxy/` — Proxy pattern theory (virtual/protection/remote) |
| `spring_batch/` | `../../lld/behavioral/template_method/` — Template Method pattern theory |
| `filters_and_interceptors/` | `../../lld/behavioral/chain_of_responsibility/` — Chain of Responsibility theory |
| `spring_aop/` | `../../lld/structural/proxy/` — AOP proxy mechanics rooted in Proxy pattern |
| `dependency_injection/` | `../../lld/solid_principles/` — Dependency Inversion Principle; `../../lld/creational/factory_method/` — BeanFactory as Factory Method |
| `bean_lifecycle/` | `../../lld/creational/singleton/` — Singleton scope theory |

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
2. Meet the Q&A minimum for the module's depth level
3. Add a row to the module table in `README.md` (the Spring master index)
4. Place it in the correct phase in the phase diagram
5. Add cross-references in the Cross-Reference Map in `README.md`
6. Update root `README.md` Phase table under the Spring section
7. Update `spring/CLAUDE.md` module table (this file)
