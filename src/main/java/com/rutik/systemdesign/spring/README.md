# Spring Framework — Senior Engineer & Interview Prep Guide

A comprehensive, one-stop reference for mastering **Spring Framework internals**, Spring Boot auto-configuration, Spring Security, Spring Data, Spring Cloud, and production-grade patterns — targeting senior engineers and interview preparation.

---

## 1. Section Overview

Spring Framework is the most widely deployed Java application framework in enterprise software. Senior engineers are expected to understand not just the API surface but the underlying mechanics: how the IoC container resolves dependencies, how proxies implement AOP and transactions, how Spring Boot conditions drive auto-configuration, and how Spring Security's filter chain protects endpoints.

This section covers:
- IoC container internals (BeanFactory, ApplicationContext, bean scopes, lifecycle)
- Proxy mechanisms (JDK dynamic proxies, CGLIB, interface vs. class proxies)
- AOP (AspectJ pointcuts, advice types, proxy self-invocation limitations)
- Spring Boot auto-configuration, conditional annotations, custom starters, Actuator
- Spring MVC request lifecycle, DispatcherServlet, handler mappings, argument resolvers
- Spring WebFlux (reactive stack, Project Reactor, Netty event loop)
- Spring Data JPA (repositories, query derivation, N+1, custom queries)
- Spring Transactions (propagation, isolation, proxy limitations, distributed transactions)
- Spring Security (filter chain, authentication manager, authorization, JWT/OAuth2)
- Spring Cloud (config server, service discovery, circuit breakers, gateway)
- Spring Messaging (Kafka/RabbitMQ integration, message converters)
- Testing (MockMvc, WebTestClient, Testcontainers, slice tests)
- Performance tuning (connection pool sizing, caching, virtual threads in Boot 3.2+)

---

## 2. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|------------|------------|
| 1 | [ioc_container](ioc_container/) | 1 — Core Container | Intermediate | BeanFactory vs. ApplicationContext, bean scopes, component scan, bean post-processors |
| 2 | [bean_lifecycle](bean_lifecycle/) | 1 — Core Container | Intermediate | Instantiation, populate properties, BeanPostProcessor, init/destroy, @PostConstruct |
| 3 | [dependency_injection](dependency_injection/) | 1 — Core Container | Beginner | Constructor vs. field vs. setter injection, circular deps, @Qualifier, @Primary |
| 4 | [spring_configuration](spring_configuration/) | 1 — Core Container | Beginner | @Configuration, @Bean, @ComponentScan, @PropertySource, @Profile, @Conditional |
| 5 | [spring_proxies](spring_proxies/) | 2 — Proxies & AOP | Advanced | JDK dynamic proxy, CGLIB, proxyTargetClass, self-invocation bypass, proxy order |
| 6 | [spring_aop](spring_aop/) | 2 — Proxies & AOP | Advanced | Pointcut expressions, advice types, AspectJ weaving, @Around, proxy limitations |
| 7 | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/) | 3 — Spring Boot | Intermediate | @EnableAutoConfiguration, spring.factories / AutoConfiguration.imports, @Conditional* |
| 8 | [spring_boot_configuration](spring_boot_configuration/) | 3 — Spring Boot | Beginner | @ConfigurationProperties, relaxed binding, config server, secrets management |
| 9 | [spring_boot_actuator](spring_boot_actuator/) | 3 — Spring Boot | Intermediate | Health indicators, metrics (Micrometer), custom endpoints, security, Prometheus |
| 10 | [spring_mvc_architecture](spring_mvc_architecture/) | 4 — Spring Web | Intermediate | DispatcherServlet, HandlerMapping, HandlerAdapter, ViewResolver, message converters |
| 11 | [request_handling](request_handling/) | 4 — Spring Web | Intermediate | @RequestMapping, argument resolvers, @ControllerAdvice, exception handling |
| 12 | [filters_and_interceptors](filters_and_interceptors/) | 4 — Spring Web | Intermediate | Servlet Filter vs. HandlerInterceptor, Filter order, OncePerRequestFilter |
| 13 | [spring_webflux](spring_webflux/) | 4 — Spring Web | Advanced | Reactor, Mono/Flux, Netty, RouterFunction, backpressure, WebClient |
| 14 | [spring_data_jpa](spring_data_jpa/) | 5 — Data & Transactions | Intermediate | JpaRepository, query derivation, JPQL, native queries, projections, N+1 problem |
| 15 | [spring_transactions](spring_transactions/) | 5 — Data & Transactions | Advanced | Propagation, isolation, @Transactional internals, proxy limits, distributed TX |
| 16 | [spring_caching](spring_caching/) | 5 — Data & Transactions | Intermediate | @Cacheable, @CacheEvict, CacheManager, Redis integration, cache stampede |
| 17 | [spring_security_architecture](spring_security_architecture/) | 6 — Security | Advanced | SecurityFilterChain, AuthenticationManager, SecurityContext, CSRF, CORS |
| 18 | [spring_security_jwt_oauth](spring_security_jwt_oauth/) | 6 — Security | Advanced | JWT validation, OAuth2 resource server, PKCE, Spring Authorization Server |
| 19 | [spring_cloud_config](spring_cloud_config/) | 7 — Cloud & Messaging | Intermediate | Config server, refresh scope, Vault integration, config encryption |
| 20 | [spring_cloud_patterns](spring_cloud_patterns/) | 7 — Cloud & Messaging | Advanced | Eureka, Resilience4j, Spring Cloud Gateway, load balancer, circuit breaker |
| 21 | [spring_messaging](spring_messaging/) | 7 — Cloud & Messaging | Intermediate | @KafkaListener, @RabbitListener, message converters, DLQ, idempotency |
| 22 | [spring_testing](spring_testing/) | 8 — Testing & Production | Intermediate | @SpringBootTest, @WebMvcTest, MockMvc, WebTestClient, Testcontainers, slice tests |
| 23 | [spring_performance](spring_performance/) | 8 — Testing & Production | Advanced | Startup time, lazy init, virtual threads (Boot 3.2), GraalVM native, connection pools |
| 24 | [spring_batch](spring_batch/) | 7 — Cloud & Messaging | Advanced | Job/Step/chunk model, ItemReader/Processor/Writer, @StepScope, JobRepository, partitioning, skip/retry |
| 25 | [spring_events_and_scheduling](spring_events_and_scheduling/) | 7 — Cloud & Messaging | Intermediate | ApplicationEventPublisher, @EventListener, @TransactionalEventListener, @Scheduled, ShedLock |
| 26 | [validation_and_error_handling](validation_and_error_handling/) | 4 — Spring Web | Intermediate | Bean Validation (JSR-380), @Valid/@Validated, custom ConstraintValidator, ProblemDetail (RFC 7807) |
| 27 | [observability_and_tracing](observability_and_tracing/) | 8 — Testing & Production | Advanced | Micrometer Observation API, Micrometer Tracing + OTLP, W3C traceparent, structured logging, exemplars |
| 28 | [spring_ai](spring_ai/) | 7 — Cloud & Messaging | Advanced | ChatClient fluent API, prompt templates, structured output, VectorStore + RAG advisors, @Tool function calling, model routing via beans |
| 29 | [spring_native_graalvm](spring_native_graalvm/) | 8 — Testing & Production | Advanced | AOT processing, reachability metadata/hints, build-time vs runtime init, tracing agent, startup/memory vs peak-throughput tradeoff |
| 30 | [spring_integration](spring_integration/) | 7 — Cloud & Messaging | Advanced | EIP: channels, adapters/gateways, router/splitter/aggregator/transformer, Java DSL; contrast with spring_messaging |
| 31 | [spring_modulith](spring_modulith/) | 3 — Spring Boot | Advanced | Modular monolith: @ApplicationModule, ArchUnit verification, @ApplicationModuleListener, event publication registry, module tests, docs |
| 32 | [spring_graphql](spring_graphql/) | 4 — Spring Web | Advanced | Schema-first @QueryMapping/@MutationMapping/@SchemaMapping, @BatchMapping/DataLoader (N+1), subscriptions, cursor pagination, error handling |

---

## 3. 8-Phase Learning Path

```
Phase 1: Core Container
+--------------------------+
| ioc_container            |
| bean_lifecycle           |
| dependency_injection     |
| spring_configuration     |
+-----------+--------------+
            |
            v
Phase 2: Proxies & AOP
+--------------------------+
| spring_proxies           |
| spring_aop               |
+-----------+--------------+
            |
     +------+------+
     |             |
     v             v
Phase 3:         Phase 4:
Spring Boot      Spring Web
+----------+     +------------------------+
| auto-    |     | spring_mvc_architecture|
| config   |     | request_handling       |
| config   |     | filters_interceptors   |
| actuator |     | spring_webflux         |
| modulith |     | spring_graphql         |
+----+-----+     +----------+-------------+
     |                      |
     +----------+-----------+
                |
                v
Phase 5: Spring Data & Transactions
+----------------------------------+
| spring_data_jpa                  |
| spring_transactions              |
| spring_caching                   |
+----------------+-----------------+
                 |
                 v
Phase 6: Spring Security
+----------------------------------+
| spring_security_architecture     |
| spring_security_jwt_oauth        |
+----------------+-----------------+
                 |
                 v
Phase 7: Spring Cloud & Messaging
+----------------------------------+
| spring_cloud_config              |
| spring_cloud_patterns            |
| spring_messaging                 |
| spring_batch                     |
| spring_events_and_scheduling     |
| spring_ai                        |
| spring_integration               |
+----------------+-----------------+
                 |
                 v
Phase 8: Testing & Production
+----------------------------------+
| spring_testing                   |
| spring_performance               |
| observability_and_tracing        |
| spring_native_graalvm            |
+----------------------------------+

Phase 4 additions:
| validation_and_error_handling    |  (extends request_handling)
+----------------------------------+
```

Dependencies to note:
- Phase 2 (Proxies & AOP) must come before Phase 5 (Transactions) and Phase 6 (Security), both of which rely on proxy mechanics.
- Phase 3 (Boot) and Phase 4 (Web) can be studied in parallel after Phase 1.
- Phase 7 (Cloud) assumes Phase 5 knowledge (transactions, caching in distributed systems).

---

## 4. Spring Version Matrix

| Feature | Spring 5.3 / Boot 2.7 | Spring 6.0 / Boot 3.0 | Spring 6.1 / Boot 3.1 | Spring 6.2 / Boot 3.3 |
|---------|----------------------|----------------------|----------------------|----------------------|
| Baseline JDK | Java 8+ (recommended 11) | Java 17 (minimum) | Java 17 (minimum) | Java 17 (minimum), Java 21 recommended |
| Jakarta namespace | `javax.*` | `jakarta.*` (full migration) | `jakarta.*` | `jakarta.*` |
| Security config style | `WebSecurityConfigurerAdapter` (deprecated) | `SecurityFilterChain` bean (mandatory) | `SecurityFilterChain` bean | `SecurityFilterChain` bean |
| Auto-config SPI | `spring.factories` (`EnableAutoConfiguration` key) | `AutoConfiguration.imports` (new SPI) | `AutoConfiguration.imports` | `AutoConfiguration.imports` |
| Observability | Spring Cloud Sleuth (separate project) | Micrometer Tracing (built-in) | Micrometer Tracing + OTLP | Micrometer Tracing + OTLP |
| Native image support | Spring Native (experimental, separate project) | GraalVM native (first-class, `spring-aot-maven-plugin`) | GraalVM native (improved AOT hints) | GraalVM native (stable) |
| Virtual threads | Not supported | Preview support | `spring.threads.virtual.enabled=true` (production-ready) | Virtual threads default option |
| HTTP interface clients | Not available | `@HttpExchange` (declarative HTTP clients) | `@HttpExchange` (stable) | `@HttpExchange` (stable) |
| Problem Details (RFC 7807) | Manual implementation | `ProblemDetail` built-in, `@ControllerAdvice` integration | `ProblemDetail` (stable) | `ProblemDetail` (stable) |
| RestClient | `RestTemplate` / `WebClient` | `RestTemplate` (legacy), `WebClient` | `RestClient` (new synchronous fluent API) | `RestClient` (stable) |
| Testcontainers | Manual integration | `@ServiceConnection` (Boot 3.1+) | `@ServiceConnection` (stable) | `@ServiceConnection` (stable) |

---

## 5. Top Interview Topics by Category

### Core Container
1. **How does Spring resolve circular dependencies?** — Constructor injection fails; field/setter injection is resolved via an early reference (three-level cache: singletonObjects, earlySingletonObjects, singletonFactories).
2. **What is the difference between BeanFactory and ApplicationContext?** — ApplicationContext extends BeanFactory adding event publishing, i18n, AOP auto-proxying, and eager initialization of singletons.
3. **What are the bean scopes?** — singleton (default), prototype, request, session, application, websocket; prototype means a new instance on every getBean().
4. **What does a BeanPostProcessor do?** — Intercepts every bean after instantiation; `postProcessBeforeInitialization` and `postProcessAfterInitialization`; used by AOP auto-proxy creator.
5. **When is @Lazy useful and when does it break things?** — Delays initialization until first use; breaks circular dependency detection and can hide misconfiguration until runtime.

### AOP & Proxies
1. **Why does self-invocation bypass @Transactional / @Cacheable?** — Spring AOP uses a proxy; calling a method on `this` bypasses the proxy, so the advice is never triggered.
2. **When does Spring use JDK dynamic proxy vs. CGLIB?** — JDK proxy when the target implements at least one interface and `proxyTargetClass=false`; CGLIB subclasses the target class otherwise.
3. **What is the proxy order when multiple aspects are applied?** — Controlled by `@Order` on `@Aspect` classes or `Ordered` interface; lower value = higher precedence (outer wrapper).
4. **Can you advise final classes or final methods with Spring AOP?** — No; CGLIB cannot subclass a final class or override a final method. Use AspectJ compile-time weaving instead.
5. **What pointcut expression matches all service-layer methods?** — `execution(* com.example.service..*.*(..))`; `within(com.example.service..*)` is an alternative.

### Transactions
1. **What are the transaction propagation types?** — REQUIRED (default), REQUIRES_NEW (suspends outer), NESTED (savepoint), MANDATORY (must exist), SUPPORTS, NOT_SUPPORTED, NEVER.
2. **What isolation level prevents phantom reads?** — SERIALIZABLE; REPEATABLE_READ prevents non-repeatable reads but not phantoms in most databases.
3. **Why does @Transactional on a private method not work?** — Proxy-based AOP cannot intercept private methods; the proxy calls the concrete class method directly.
4. **What happens when a checked exception is thrown inside @Transactional?** — By default, Spring only rolls back on unchecked exceptions (RuntimeException/Error); set `rollbackFor = Exception.class` to include checked exceptions.
5. **How do you test transactional rollback behavior?** — Use `@Transactional` on the test method (Spring rolls back after each test by default) or use `TransactionTemplate` in the test body.

### Security
1. **What is the SecurityFilterChain and how many can you have?** — An ordered list of servlet filters processing every request; multiple chains can be registered with different `securityMatcher` patterns (e.g., one for API, one for UI).
2. **How does Spring Security store the authenticated user?** — `SecurityContextHolder` uses `ThreadLocal` (or `InheritableThreadLocal`) to hold a `SecurityContext` containing the `Authentication` object.
3. **What is the difference between authentication and authorization in Spring Security?** — Authentication verifies identity (who); authorization decides what the authenticated principal can do; handled by `AuthenticationManager` and `AccessDecisionManager`/`AuthorizationManager` respectively.
4. **How does CSRF protection work in Spring Security?** — Synchronizer token pattern: server stores a token in the session; every mutating request must include the token in a header or form field; stateless APIs typically disable CSRF.
5. **How do you validate a JWT in Spring Security?** — Register a `JwtDecoder` bean and use `oauth2ResourceServer().jwt()`; Spring calls the decoder on every request and populates the `SecurityContext` with a `JwtAuthenticationToken`.

### Spring Boot
1. **How does @SpringBootApplication work?** — Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`; triggers class-path scanning and loads auto-configuration classes.
2. **How does auto-configuration know which classes to load?** — Boot 2: `META-INF/spring.factories`; Boot 3: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; each entry is a configuration class loaded if conditions are met.
3. **What is the difference between @ConditionalOnMissingBean and @ConditionalOnClass?** — `@ConditionalOnMissingBean` backs off if a bean of the given type is already defined (allows user override); `@ConditionalOnClass` activates only when a class is present on the classpath.
4. **How do you write a custom Spring Boot starter?** — Create an auto-configuration class annotated with `@AutoConfiguration`, register it in `AutoConfiguration.imports`, package as `my-spring-boot-autoconfigure` + `my-spring-boot-starter` (thin POM).
5. **How does the Actuator health endpoint work?** — Aggregates all `HealthIndicator` beans; each returns `Health.up()` / `Health.down()`; the composite health is `UP` only if all indicators report `UP`; expose via `management.endpoints.web.exposure.include=health`.

---

## 6. Cross-Reference Map

| Module | Also See |
|--------|----------|
| [ioc_container](ioc_container/) | [bean_lifecycle](bean_lifecycle/), [spring_proxies](spring_proxies/), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/) |
| [bean_lifecycle](bean_lifecycle/) | [ioc_container](ioc_container/), [spring_aop](spring_aop/) |
| [dependency_injection](dependency_injection/) | [ioc_container](ioc_container/), [spring_configuration](spring_configuration/) |
| [spring_configuration](spring_configuration/) | [dependency_injection](dependency_injection/), [spring_boot_configuration](spring_boot_configuration/) |
| [spring_proxies](spring_proxies/) | [spring_aop](spring_aop/), [spring_transactions](spring_transactions/), [spring_security_architecture](spring_security_architecture/) |
| [spring_aop](spring_aop/) | [spring_proxies](spring_proxies/), [spring_transactions](spring_transactions/), [spring_caching](spring_caching/) |
| [spring_boot_autoconfiguration](spring_boot_autoconfiguration/) | [ioc_container](ioc_container/), [spring_boot_configuration](spring_boot_configuration/), [spring_boot_actuator](spring_boot_actuator/) |
| [spring_boot_configuration](spring_boot_configuration/) | [spring_configuration](spring_configuration/), [spring_cloud_config](spring_cloud_config/) |
| [spring_boot_actuator](spring_boot_actuator/) | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/), [spring_performance](spring_performance/) |
| [spring_mvc_architecture](spring_mvc_architecture/) | [request_handling](request_handling/), [filters_and_interceptors](filters_and_interceptors/) |
| [request_handling](request_handling/) | [spring_mvc_architecture](spring_mvc_architecture/), [spring_security_architecture](spring_security_architecture/) |
| [filters_and_interceptors](filters_and_interceptors/) | [spring_mvc_architecture](spring_mvc_architecture/), [spring_security_architecture](spring_security_architecture/) |
| [spring_webflux](spring_webflux/) | [spring_mvc_architecture](spring_mvc_architecture/), [spring_data_jpa](spring_data_jpa/) |
| [spring_data_jpa](spring_data_jpa/) | [spring_transactions](spring_transactions/), [spring_caching](spring_caching/) |
| [spring_transactions](spring_transactions/) | [spring_data_jpa](spring_data_jpa/), [spring_aop](spring_aop/), [spring_proxies](spring_proxies/) |
| [spring_caching](spring_caching/) | [spring_aop](spring_aop/), [spring_data_jpa](spring_data_jpa/), [spring_cloud_patterns](spring_cloud_patterns/) |
| [spring_security_architecture](spring_security_architecture/) | [filters_and_interceptors](filters_and_interceptors/), [spring_security_jwt_oauth](spring_security_jwt_oauth/), [spring_proxies](spring_proxies/) |
| [spring_security_jwt_oauth](spring_security_jwt_oauth/) | [spring_security_architecture](spring_security_architecture/), [spring_cloud_patterns](spring_cloud_patterns/) |
| [spring_cloud_config](spring_cloud_config/) | [spring_boot_configuration](spring_boot_configuration/), [spring_cloud_patterns](spring_cloud_patterns/) |
| [spring_cloud_patterns](spring_cloud_patterns/) | [spring_cloud_config](spring_cloud_config/), [spring_messaging](spring_messaging/) |
| [spring_messaging](spring_messaging/) | [spring_cloud_patterns](spring_cloud_patterns/), [spring_transactions](spring_transactions/) |
| [spring_testing](spring_testing/) | All modules — slice tests isolate specific layers |
| [spring_performance](spring_performance/) | [spring_boot_actuator](spring_boot_actuator/), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/), [spring_webflux](spring_webflux/) |
| [spring_batch](spring_batch/) | [spring_transactions](spring_transactions/), [spring_messaging](spring_messaging/), [spring_cloud_patterns](spring_cloud_patterns/) |
| [spring_ai](spring_ai/) | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/), [spring_webflux](spring_webflux/) (streaming), [../../llm/advanced_rag/](../../llm/advanced_rag/), [../../llm/embeddings_and_similarity_search/](../../llm/embeddings_and_similarity_search/) |
| [spring_native_graalvm](spring_native_graalvm/) | [spring_performance](spring_performance/), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/), [../../java/annotation_processing/](../../java/annotation_processing/), [../../java/jvm_internals/](../../java/jvm_internals/) |
| [spring_integration](spring_integration/) | [spring_messaging](spring_messaging/), [spring_events_and_scheduling](spring_events_and_scheduling/), [../../backend/event_driven_fundamentals/](../../backend/event_driven_fundamentals/), [../../java/microservices_patterns/](../../java/microservices_patterns/) |
| [spring_modulith](spring_modulith/) | [spring_events_and_scheduling](spring_events_and_scheduling/), [spring_integration](spring_integration/), [../../lld/solid_principles/](../../lld/solid_principles/), [../../java/microservices_patterns/](../../java/microservices_patterns/) |
| [spring_graphql](spring_graphql/) | [request_handling](request_handling/), [spring_data_jpa](spring_data_jpa/) (N+1), [spring_webflux](spring_webflux/), [../../backend/graphql/](../../backend/graphql/) |
| [spring_events_and_scheduling](spring_events_and_scheduling/) | [spring_transactions](spring_transactions/), [spring_messaging](spring_messaging/), [spring_aop](spring_aop/) |
| [validation_and_error_handling](validation_and_error_handling/) | [request_handling](request_handling/), [spring_mvc_architecture](spring_mvc_architecture/), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/) |
| [observability_and_tracing](observability_and_tracing/) | [spring_boot_actuator](spring_boot_actuator/), [spring_cloud_patterns](spring_cloud_patterns/), [spring_performance](spring_performance/) |

---

## 7. Case Studies

For a guided learning path through all 9 case studies, see [case_studies/README.md](case_studies/README.md).

| Case Study | Core Concepts | Difficulty |
|------------|---------------|------------|
| [Design a Multi-Tenant API](case_studies/design_multitenant_api.md) | IoC container customization, request-scoped beans, per-tenant data sources, dynamic routing, security context propagation | Advanced |
| [Design a Spring Boot Event-Driven Microservice](case_studies/design_event_driven_microservice.md) | Spring Kafka, transactional outbox pattern, idempotent consumers, dead-letter topics, Saga orchestration | Advanced |
| [Design an API Gateway](case_studies/design_api_gateway.md) | Spring Cloud Gateway, WebFlux, global filters, circuit breaker integration, rate limiting, JWT relay | Advanced |
| [Design a Spring Batch ETL Pipeline](case_studies/design_batch_pipeline.md) | Spring Batch chunk model, Job/Step orchestration, ItemReader/Processor/Writer, restartability, partitioning | Advanced |
| [Design a Distributed Caching Layer](case_studies/design_distributed_caching.md) | Spring Cache abstraction, Redis CacheManager, cache stampede prevention, multi-level caching, eviction policies | Advanced |
| [Design a Distributed Rate Limiter](case_studies/design_distributed_rate_limiter_spring.md) | Redis Lua token bucket, atomic check-and-decrement, OncePerRequestFilter, fail-open fallback | Advanced |
| [Design an OAuth2 Authorization Server](case_studies/design_oauth2_authorization_server.md) | Spring Authorization Server 1.x, PKCE, refresh token rotation, dual-key JWKS rollover | Advanced |
| [Design an Idempotent Payment API](case_studies/design_idempotent_payment_api.md) | Idempotency keys, pg_advisory_xact_lock, outbox pattern, exactly-once semantics | Advanced |
| [Design a Real-Time Notification Service](case_studies/design_realtime_notification_service.md) | WebSocket fan-out, Redis Pub/Sub, ZSET history, virtual threads, backpressure | Advanced |

**Cross-cutting shared primitives** (consumed by the case studies above):

| Primitive | What it covers |
|-----------|---------------|
| [OTel Observability for Spring](case_studies/cross_cutting/otel_observability_for_spring.md) | @Observed, W3C traceparent, Kafka context propagation, exemplars, @Async fix |
| [Resilience4j Patterns](case_studies/cross_cutting/resilience4j_patterns.md) | CB state machine, Retry/CB composition, SemaphoreBulkhead vs ThreadPoolBulkhead |
| [Testcontainers & Test Strategy](case_studies/cross_cutting/testcontainers_and_test_strategy.md) | @ServiceConnection, Replace.NONE, transaction isolation test, Kafka idempotency test |
| [Zero-Downtime Deploys & Config](case_studies/cross_cutting/zero_downtime_deploys_and_config.md) | Expand-Contract, readiness/liveness separation, preStop sleep, @RefreshScope pitfalls |

---

## Getting Started

Recommended learning order for interview preparation:

1. **Week 1**: Phases 1–2 (IoC, bean lifecycle, DI, proxies, AOP) — these underpin everything else
2. **Week 2**: Phases 3–4 (Spring Boot internals, MVC, WebFlux)
3. **Week 3**: Phases 5–6 (Data, transactions, security)
4. **Week 4**: Phases 7–8 (Cloud patterns, messaging, testing, performance)
5. **Review**: Work through all 9 case studies end-to-end — see [case_studies/README.md](case_studies/README.md) for the recommended order

Each module follows the standard 14-section template. See [llm/foundations_and_architecture/README.md](../llm/foundations_and_architecture/README.md) as the format reference.
