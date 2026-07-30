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
- Performance tuning (connection pool sizing, caching, virtual threads)

---

## 2. Module Table

| # | Module Directory | Phase | Difficulty | Key Topics |
|---|-----------------|-------|------------|------------|
| 1 | [ioc_container](ioc_container/ioc_container.md) | 1 — Core Container | Intermediate | BeanFactory vs. ApplicationContext, bean scopes, component scan, bean post-processors |
| 2 | [bean_lifecycle](bean_lifecycle/bean_lifecycle.md) | 1 — Core Container | Intermediate | Instantiation, populate properties, BeanPostProcessor, init/destroy, @PostConstruct |
| 3 | [dependency_injection](dependency_injection/dependency_injection.md) | 1 — Core Container | Beginner | Constructor vs. field vs. setter injection, circular deps, @Qualifier, @Primary |
| 4 | [spring_configuration](spring_configuration/spring_configuration.md) | 1 — Core Container | Beginner | @Configuration, @Bean, @ComponentScan, @PropertySource, @Profile, @Conditional |
| 5 | [spring_proxies](spring_proxies/spring_proxies.md) | 2 — Proxies & AOP | Advanced | JDK dynamic proxy, CGLIB, proxyTargetClass, self-invocation bypass, proxy order |
| 6 | [spring_aop](spring_aop/spring_aop.md) | 2 — Proxies & AOP | Advanced | Pointcut expressions, advice types, AspectJ weaving, @Around, proxy limitations — with 1 deep-dive sub-file ([pointcut designators](spring_aop/pointcut_designators.md)) |
| 7 | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) | 3 — Spring Boot | Intermediate | @EnableAutoConfiguration, AutoConfiguration.imports, @Conditional* |
| 8 | [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md) | 3 — Spring Boot | Beginner | @ConfigurationProperties, relaxed binding, config server, secrets management |
| 9 | [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md) | 3 — Spring Boot | Intermediate | Health indicators, metrics (Micrometer), custom endpoints, security, Prometheus |
| 10 | [spring_modulith](spring_modulith/spring_modulith.md) | 3 — Spring Boot | Advanced | Modular monolith: @ApplicationModule, ArchUnit verification, @ApplicationModuleListener, event publication registry, module tests, docs |
| 11 | [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md) | 4 — Spring Web | Intermediate | DispatcherServlet, HandlerMapping, HandlerAdapter, ViewResolver, message converters |
| 12 | [request_handling](request_handling/request_handling.md) | 4 — Spring Web | Intermediate | @RequestMapping, argument resolvers, @ControllerAdvice, exception handling |
| 13 | [filters_and_interceptors](filters_and_interceptors/filters_and_interceptors.md) | 4 — Spring Web | Intermediate | Servlet Filter vs. HandlerInterceptor, Filter order, OncePerRequestFilter |
| 14 | [spring_webflux](spring_webflux/spring_webflux.md) | 4 — Spring Web | Advanced | Reactor, Mono/Flux, Netty, RouterFunction, backpressure, WebClient |
| 15 | [spring_graphql](spring_graphql/spring_graphql.md) | 4 — Spring Web | Advanced | Schema-first @QueryMapping/@MutationMapping/@SchemaMapping, @BatchMapping/DataLoader (N+1), subscriptions, cursor pagination, error handling |
| 16 | [validation_and_error_handling](validation_and_error_handling/validation_and_error_handling.md) | 4 — Spring Web | Intermediate | Jakarta Validation 3.1, @Valid/@Validated, custom ConstraintValidator, ProblemDetail (RFC 9457) |
| 17 | [spring_data_jpa](spring_data_jpa/spring_data_jpa.md) | 5 — Data & Transactions | Intermediate | JpaRepository, query derivation, JPQL, native queries, projections, N+1 problem |
| 18 | [spring_transactions](spring_transactions/spring_transactions.md) | 5 — Data & Transactions | Advanced | Propagation, isolation, @Transactional internals, proxy limits, distributed TX |
| 19 | [spring_caching](spring_caching/spring_caching.md) | 5 — Data & Transactions | Intermediate | @Cacheable, @CacheEvict, CacheManager, Redis integration, cache stampede |
| 20 | [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) | 6 — Security | Advanced | SecurityFilterChain, AuthenticationManager, SecurityContext, CSRF, CORS |
| 21 | [spring_security_jwt_oauth](spring_security_jwt_oauth/spring_security_jwt_oauth.md) | 6 — Security | Advanced | JWT validation, OAuth2 resource server, PKCE, Spring Authorization Server |
| 22 | [spring_cloud_config](spring_cloud_config/spring_cloud_config.md) | 7 — Cloud & Messaging | Intermediate | Config server, refresh scope, Vault integration, config encryption |
| 23 | [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) | 7 — Cloud & Messaging | Advanced | Eureka, Resilience4j, Spring Cloud Gateway, load balancer, circuit breaker — with 1 deep-dive sub-file ([gateway server webmvc](spring_cloud_patterns/gateway_server_webmvc.md)) |
| 24 | [spring_messaging](spring_messaging/spring_messaging.md) | 7 — Cloud & Messaging | Intermediate | @KafkaListener, @RabbitListener, message converters, DLQ, idempotency |
| 25 | [spring_batch](spring_batch/spring_batch.md) | 7 — Cloud & Messaging | Advanced | Job/Step/chunk model, ItemReader/Processor/Writer, @StepScope, JobRepository, partitioning, skip/retry |
| 26 | [spring_events_and_scheduling](spring_events_and_scheduling/spring_events_and_scheduling.md) | 7 — Cloud & Messaging | Intermediate | ApplicationEventPublisher, @EventListener, @TransactionalEventListener, @Scheduled, ShedLock |
| 27 | [spring_ai](spring_ai/spring_ai.md) | 7 — Cloud & Messaging | Advanced | ChatClient fluent API, prompt templates, structured output, VectorStore + RAG advisors, @Tool function calling, model routing via beans |
| 28 | [spring_integration](spring_integration/spring_integration.md) | 7 — Cloud & Messaging | Advanced | EIP: channels, adapters/gateways, router/splitter/aggregator/transformer, Java DSL; contrast with spring_messaging |
| 29 | [spring_testing](spring_testing/spring_testing.md) | 8 — Testing & Production | Intermediate | @SpringBootTest, @WebMvcTest, MockMvc, WebTestClient, Testcontainers, slice tests |
| 30 | [spring_performance](spring_performance/spring_performance.md) | 8 — Testing & Production | Advanced | Startup time, lazy init, virtual threads, GraalVM native, connection pools |
| 31 | [observability_and_tracing](observability_and_tracing/observability_and_tracing.md) | 8 — Testing & Production | Advanced | Micrometer Observation API, Micrometer Tracing + OTLP, W3C traceparent, structured logging, exemplars |
| 32 | [spring_native_graalvm](spring_native_graalvm/spring_native_graalvm.md) | 8 — Testing & Production | Advanced | AOT processing, reachability metadata/hints, build-time vs runtime init, tracing agent, startup/memory vs peak-throughput tradeoff |
| 33 | [spring_hateoas_rest_maturity](spring_hateoas_rest_maturity/spring_hateoas_rest_maturity.md) | 4 — Spring Web | Advanced | Richardson Maturity Model L0-L3, Spring HATEOAS (EntityModel/Link/assemblers), HAL/HAL-FORMS, @HttpExchange/RestClient, ProblemDetail |
| 34 | [spring_grpc](spring_grpc/spring_grpc.md) | 4 — Spring Web | Advanced | @GrpcService beans, server/channel autoconfig, Server/ClientInterceptor, Status↔exception mapping, deadlines, streaming, security + tracing |
| 35 | [spring_session](spring_session/spring_session.md) | 6 — Security | Advanced | SessionRepositoryFilter, Redis/JDBC/Hazelcast backends, session fixation, concurrent-session control, WebSession, JWT vs stateful tradeoff |
| 36 | [spring_data_nosql](spring_data_nosql/spring_data_nosql.md) | 5 — Data & Transactions | Intermediate | Spring Data MongoDB (MongoTemplate, aggregation, transactions) + Redis (RedisTemplate, @RedisHash, pub/sub) + reactive repositories; NoSQL vs JPA |
| 37 | [database_migrations](database_migrations/database_migrations.md) | 5 — Data & Transactions | Intermediate | Flyway (versioned/repeatable, checksums) + Liquibase (changesets, rollback), expand-contract zero-downtime, Boot integration, Testcontainers |
| 38 | [spring_websocket_stomp](spring_websocket_stomp/spring_websocket_stomp.md) | 7 — Cloud & Messaging | Advanced | WebSocket handshake, STOMP over WebSocket, simple vs external broker relay, SockJS, per-user destinations, scaling |
| 39 | [spring_http_clients](spring_http_clients/spring_http_clients.md) | 4 — Spring Web | Intermediate | RestClient vs WebClient vs @HttpExchange, `@ImportHttpServices` groups, connection pooling, timeouts, error handling, MockRestServiceServer |

**Deep-dive sub-files** (group under their parent module's game topic; no separate `STUDY_ORDER` entry): [spring_webflux/rsocket_reactive_messaging.md](spring_webflux/rsocket_reactive_messaging.md) — RSocket 4 interaction models, protocol-level backpressure, `@MessageMapping`/`RSocketRequester`; [request_handling/i18n_and_localization.md](request_handling/i18n_and_localization.md) — `MessageSource`, `LocaleResolver`/`LocaleChangeInterceptor`, locale-aware formatting.

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
| spring_hateoas_rest_maturity     |  (REST maturity L0-L3, hypermedia)
| spring_grpc                      |  (gRPC endpoints in Spring)
| spring_http_clients              |  (RestClient/WebClient/@HttpExchange)
+----------------------------------+

Phase 5 additions:
| spring_data_nosql                |  (MongoDB + Redis, reactive repositories)
| database_migrations              |  (Flyway/Liquibase, expand-contract)
+----------------------------------+

Phase 6 addition:
| spring_session                   |  (distributed / Redis-backed sessions)
+----------------------------------+

Phase 7 addition:
| spring_websocket_stomp           |  (STOMP messaging, SockJS, broker relay)
+----------------------------------+
```

Dependencies to note:
- Phase 2 (Proxies & AOP) must come before Phase 5 (Transactions) and Phase 6 (Security), both of which rely on proxy mechanics.
- Phase 3 (Boot) and Phase 4 (Web) can be studied in parallel after Phase 1.
- Phase 7 (Cloud) assumes Phase 5 knowledge (transactions, caching in distributed systems).

---

## Learning Paths

39 modules is the right depth for a reference and the wrong shape for someone two weeks from an interview. So there are **two ways through it**; the browser learning game's **Study** view surfaces both as a **Full / Interview** toggle (Full is the default).

### Full Path (39 modules)

The complete curriculum in the order above — see [8-Phase Learning Path](#3-8-phase-learning-path). Use it for genuine breadth: Modulith, GraphQL, gRPC, HATEOAS, Batch, Integration (EIP), Spring AI, Session, Cloud Config, native/GraalVM, and the full observability stack. Nothing is dropped.

<!-- study-path-table senior -->
### Senior Path (25 modules)

| # | Module | Files |
|---|--------|-------|
| 1 | [ioc_container](ioc_container/ioc_container.md) | module page only |
| 2 | [bean_lifecycle](bean_lifecycle/bean_lifecycle.md) | module page only |
| 3 | [dependency_injection](dependency_injection/dependency_injection.md) | module page only |
| 4 | [spring_configuration](spring_configuration/spring_configuration.md) | module page only |
| 5 | [spring_proxies](spring_proxies/spring_proxies.md) | module page only |
| 6 | [spring_aop](spring_aop/spring_aop.md) | 2 files |
| 7 | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) | module page only |
| 8 | [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md) | module page only |
| 9 | [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md) | module page only |
| 11 | [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md) | module page only |
| 12 | [request_handling](request_handling/request_handling.md) | module page only |
| 13 | [filters_and_interceptors](filters_and_interceptors/filters_and_interceptors.md) | module page only |
| 14 | [spring_webflux](spring_webflux/spring_webflux.md) | module page only |
| 17 | [spring_http_clients](spring_http_clients/spring_http_clients.md) | module page only |
| 19 | [validation_and_error_handling](validation_and_error_handling/validation_and_error_handling.md) | module page only |
| 20 | [spring_data_jpa](spring_data_jpa/spring_data_jpa.md) | module page only |
| 23 | [spring_transactions](spring_transactions/spring_transactions.md) | module page only |
| 24 | [spring_caching](spring_caching/spring_caching.md) | module page only |
| 25 | [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) | module page only |
| 26 | [spring_security_jwt_oauth](spring_security_jwt_oauth/spring_security_jwt_oauth.md) | module page only |
| 29 | [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) | 2 files |
| 30 | [spring_messaging](spring_messaging/spring_messaging.md) | module page only |
| 33 | [spring_events_and_scheduling](spring_events_and_scheduling/spring_events_and_scheduling.md) | module page only |
| 36 | [spring_testing](spring_testing/spring_testing.md) | module page only |
| 37 | [spring_performance](spring_performance/spring_performance.md) | module page only |

**Not in this path** (14 of 39, Full Path only): `spring_modulith`, `spring_graphql`, `spring_hateoas_rest_maturity`, `spring_grpc`, `spring_data_nosql`, `database_migrations`, `spring_session`, `spring_cloud_config`, `spring_websocket_stomp`, `spring_batch`, `spring_ai`, `spring_integration`, `observability_and_tracing`, `spring_native_graalvm`
<!-- /study-path-table -->

A ruthless cut to what a **senior Spring / Spring Boot interview** actually probes — the container internals, the proxy mechanics behind `@Transactional`/`@Cacheable`, the MVC request pipeline, and the data/security stack. Same learning order, a strict subset of the Full Path. Each group says why it earns senior time.

| Phase | Why it's tested |
|-------|-----------------|
| Core Container | ApplicationContext refresh, bean scopes, circular deps + the three-level cache, `@Configuration` full-vs-lite — the guaranteed openers |
| Proxies & AOP | JDK vs CGLIB and the self-invocation trap that silently disables `@Transactional`/`@Cacheable` — a near-universal gotcha |
| Spring Boot | `@EnableAutoConfiguration`/`AutoConfiguration.imports`/`@Conditional`, health probes and Micrometer metrics |
| Spring Web | DispatcherServlet pipeline, Filter vs Interceptor, Mono/Flux + backpressure, `@ControllerAdvice`/ProblemDetail |
| Data & Transactions | N+1 and fetch strategies, propagation/isolation/rollback rules, cache stampede — the highest-value data round |
| Security | SecurityFilterChain, authN vs authZ, JWT validation, OAuth2 resource server, PKCE |
| Cloud & Messaging | Gateway + Resilience4j circuit breakers, Kafka `@KafkaListener` with idempotency and DLQ |
| Testing | `@SpringBootTest` vs slice tests, `@MockitoBean`, MockMvc, Testcontainers |

<!-- study-path-table principal -->
### Principal Path (18 modules)

| # | Module | Files |
|---|--------|-------|
| 7 | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) | module page only |
| 8 | [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md) | module page only |
| 9 | [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md) | module page only |
| 10 | [spring_modulith](spring_modulith/spring_modulith.md) | module page only |
| 16 | [spring_hateoas_rest_maturity](spring_hateoas_rest_maturity/spring_hateoas_rest_maturity.md) | module page only |
| 17 | [spring_http_clients](spring_http_clients/spring_http_clients.md) | module page only |
| 22 | [database_migrations](database_migrations/database_migrations.md) | module page only |
| 23 | [spring_transactions](spring_transactions/spring_transactions.md) | module page only |
| 25 | [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) | module page only |
| 26 | [spring_security_jwt_oauth](spring_security_jwt_oauth/spring_security_jwt_oauth.md) | module page only |
| 27 | [spring_session](spring_session/spring_session.md) | module page only |
| 28 | [spring_cloud_config](spring_cloud_config/spring_cloud_config.md) | module page only |
| 29 | [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) | 2 files |
| 30 | [spring_messaging](spring_messaging/spring_messaging.md) | module page only |
| 36 | [spring_testing](spring_testing/spring_testing.md) | module page only |
| 37 | [spring_performance](spring_performance/spring_performance.md) | module page only |
| 38 | [observability_and_tracing](observability_and_tracing/observability_and_tracing.md) | module page only |
| 39 | [spring_native_graalvm](spring_native_graalvm/spring_native_graalvm.md) | module page only |

**Not in this path** (21 of 39, Full Path only): `ioc_container`, `bean_lifecycle`, `dependency_injection`, `spring_configuration`, `spring_proxies`, `spring_aop`, `spring_mvc_architecture`, `request_handling`, `filters_and_interceptors`, `spring_webflux`, `spring_graphql`, `spring_grpc`, `validation_and_error_handling`, `spring_data_jpa`, `spring_data_nosql`, `spring_caching`, `spring_websocket_stomp`, `spring_batch`, `spring_events_and_scheduling`, `spring_ai`, `spring_integration`
<!-- /study-path-table -->

A different cut, not senior-plus-extras. The Principal Path probes platform-level Spring judgment: transaction and boundary design, how a service is deployed and rolled back safely, and which framework magic a team should be forbidden from using. Roughly half of it is material the Senior Path never covers, and it is usually the smaller list -- depth of judgment, not depth of syllabus.

---

## Knowledge-Question Map

The highest-frequency Spring *knowledge* questions mapped to the file that answers them. For *system design* ("design X") questions, use [case_studies/README.md](case_studies/case_studies.md).

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Why does an internal `@Transactional`/`@Cacheable` call silently do nothing? | [Spring Proxies](spring_proxies/spring_proxies.md), [Spring Transactions](spring_transactions/spring_transactions.md) |
| JDK dynamic proxy vs CGLIB — when does Spring use each? | [Spring Proxies](spring_proxies/spring_proxies.md) |
| Walk through the bean lifecycle; BeanPostProcessor vs BeanFactoryPostProcessor. | [Bean Lifecycle](bean_lifecycle/bean_lifecycle.md) |
| How does Spring resolve a circular dependency? When does it fail? | [Dependency Injection](dependency_injection/dependency_injection.md) |
| `@Transactional` propagation — REQUIRED vs REQUIRES_NEW vs NESTED. | [Spring Transactions](spring_transactions/spring_transactions.md) |
| Which exceptions roll back a transaction by default, and why? | [Spring Transactions](spring_transactions/spring_transactions.md) |
| Diagnose and fix N+1 in Spring Data JPA. | [Spring Data JPA](spring_data_jpa/spring_data_jpa.md) |
| How does `@EnableAutoConfiguration` actually work? | [Boot Autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) |
| Trace a request through the DispatcherServlet. | [Spring MVC Architecture](spring_mvc_architecture/spring_mvc_architecture.md) |
| Servlet Filter vs HandlerInterceptor — ordering and use cases. | [Filters & Interceptors](filters_and_interceptors/filters_and_interceptors.md) |
| Centralized exception handling with `@ControllerAdvice` and ProblemDetail. | [Request Handling](request_handling/request_handling.md), [Validation & Error Handling](validation_and_error_handling/validation_and_error_handling.md) |
| Explain the SecurityFilterChain; authentication vs authorization. | [Security Architecture](spring_security_architecture/spring_security_architecture.md) |
| How do you validate a JWT? OAuth2 resource server and PKCE. | [JWT & OAuth2](spring_security_jwt_oauth/spring_security_jwt_oauth.md) |
| WebFlux Mono/Flux and backpressure — when reactive vs MVC + virtual threads? | [Spring WebFlux](spring_webflux/spring_webflux.md) |
| `@Cacheable` and preventing cache stampede. | [Spring Caching](spring_caching/spring_caching.md) |
| Bean scopes — the prototype-in-singleton injection trap. | [Bean Lifecycle](bean_lifecycle/bean_lifecycle.md), [IoC Container](ioc_container/ioc_container.md) |
| `@Configuration` full vs lite mode — what is `@Bean` method interception? | [Spring Configuration](spring_configuration/spring_configuration.md) |
| `@SpringBootTest` vs slice tests; `@MockitoBean`; Testcontainers. | [Spring Testing](spring_testing/spring_testing.md) |
| Resilience4j circuit breaker + retry in Spring Cloud. | [Spring Cloud Patterns](spring_cloud_patterns/spring_cloud_patterns.md) |
| `@KafkaListener` — idempotency, manual ack, and DLQ. | [Spring Messaging](spring_messaging/spring_messaging.md) |

---

## Study Plan

A 5-week plan over the Senior Path. Case studies live in [case_studies/](case_studies/case_studies.md) and rehearse the "design X" format.

| Week | Focus | Modules | Case study |
|------|-------|---------|------------|
| 1 | Core Container + AOP | IoC Container, Bean Lifecycle, Dependency Injection, Spring Configuration, Spring Proxies, Spring AOP | — |
| 2 | Boot + Web | Boot Autoconfiguration, Boot Actuator, Spring MVC Architecture, Request Handling, Filters & Interceptors, Validation & Error Handling | [API Gateway](case_studies/design_api_gateway.md) |
| 3 | Data + Transactions | Spring Data JPA, Spring Transactions, Spring Caching | [Distributed Caching](case_studies/design_distributed_caching.md) |
| 4 | Security + Reactive | Security Architecture, JWT & OAuth2, Spring WebFlux | [OAuth2 Authorization Server](case_studies/design_oauth2_authorization_server.md) |
| 5 | Cloud, Messaging, Testing + drills | Spring Cloud Patterns, Spring Messaging, Spring Testing | [Event-Driven Microservice](case_studies/design_event_driven_microservice.md), [Idempotent Payment API](case_studies/design_idempotent_payment_api.md) |

---

## 4. Spring Version Matrix

The current generation is **Spring Framework 7.0 / Spring Boot 4.1** (Boot 4.1.0 ships Framework 7.0.8 and Spring Security 7.1.0). Spring Framework 6.2 / Boot 3.5 is the final 6th-generation line and is what most existing codebases still run, so the matrix contrasts the two.

| Feature | Spring 6.2 / Boot 3.5 | Spring 7.0 / Boot 4.1 (current) |
|---------|----------------------|----------------------------------|
| Baseline JDK | Java 17–25 | Java 17 minimum, **JDK 25 recommended** |
| Jakarta EE baseline | EE 10 — Servlet 6.0, JPA 3.1, Bean Validation 3.0 | EE 11 — Servlet 6.1, JPA 3.2, Bean Validation 3.1 (Tomcat 11+, Jetty 12.1+) |
| Spring Security | 6.5 | 7.1 |
| Security config style | `SecurityFilterChain` bean, lambda DSL | `SecurityFilterChain` bean, lambda DSL; `AuthorizationManager` is the only authorization SPI |
| Auto-config SPI | `AutoConfiguration.imports` | `AutoConfiguration.imports` |
| JSON | Jackson 2.x (`com.fasterxml.jackson`) | **Jackson 3.x default** (`tools.jackson`), 2.x support deprecated |
| Starter naming | `spring-boot-starter-web` | Modular starters — `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`, each with a `-test` companion |
| Synchronous HTTP client | `RestClient` | `RestClient` (`RestTemplate` still ships un-annotated; `@Deprecated` is scheduled for 7.1, removal for 8.0) |
| HTTP interface clients | `@HttpExchange` + `HttpServiceProxyFactory` | `@ImportHttpServices` group registration + Boot auto-configuration |
| API versioning | Hand-rolled (URI segment / custom header) | First-class: `@RequestMapping(version = "1.2")`, `spring.mvc.apiversion.*` |
| Resilience | Spring Retry (separate project) | `@Retryable` / `@ConcurrencyLimit` in core via `@EnableResilientMethods` |
| Proxying | CGLIB default in Boot | CGLIB default framework-wide, per-bean opt-out with `@Proxyable` |
| Observability | Micrometer Tracing + OTLP | Micrometer 1.17 / Tracing 1.7, `spring-boot-starter-opentelemetry` |
| Native image support | GraalVM native (stable) | GraalVM 25, unified "exact reachability metadata" format |
| Virtual threads | `spring.threads.virtual.enabled=true` | `spring.threads.virtual.enabled=true` |
| Problem Details (RFC 9457) | `ProblemDetail` | `ProblemDetail` |
| Testing | `MockMvc`, `WebTestClient`, `@ServiceConnection` | Adds `RestTestClient` (non-reactive `WebTestClient`), JUnit 6 |

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
3. **What is the difference between authentication and authorization in Spring Security?** — Authentication verifies identity (who); authorization decides what the authenticated principal can do; handled by `AuthenticationManager` and `AuthorizationManager` respectively.
4. **How does CSRF protection work in Spring Security?** — Synchronizer token pattern: server stores a token in the session; every mutating request must include the token in a header or form field; stateless APIs typically disable CSRF.
5. **How do you validate a JWT in Spring Security?** — Register a `JwtDecoder` bean and use `oauth2ResourceServer().jwt()`; Spring calls the decoder on every request and populates the `SecurityContext` with a `JwtAuthenticationToken`.

### Spring Boot
1. **How does @SpringBootApplication work?** — Combines `@Configuration`, `@EnableAutoConfiguration`, and `@ComponentScan`; triggers class-path scanning and loads auto-configuration classes.
2. **How does auto-configuration know which classes to load?** — From `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; each entry is a configuration class loaded if its `@Conditional` gates are met.
3. **What is the difference between @ConditionalOnMissingBean and @ConditionalOnClass?** — `@ConditionalOnMissingBean` backs off if a bean of the given type is already defined (allows user override); `@ConditionalOnClass` activates only when a class is present on the classpath.
4. **How do you write a custom Spring Boot starter?** — Create an auto-configuration class annotated with `@AutoConfiguration`, register it in `AutoConfiguration.imports`, package as `my-spring-boot-autoconfigure` + `my-spring-boot-starter` (thin POM).
5. **How does the Actuator health endpoint work?** — Aggregates all `HealthIndicator` beans; each returns `Health.up()` / `Health.down()`; the composite health is `UP` only if all indicators report `UP`; expose via `management.endpoints.web.exposure.include=health`.

---

## 6. Cross-Reference Map

| Module | Also See |
|--------|----------|
| [ioc_container](ioc_container/ioc_container.md) | [bean_lifecycle](bean_lifecycle/bean_lifecycle.md), [spring_proxies](spring_proxies/spring_proxies.md), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) |
| [bean_lifecycle](bean_lifecycle/bean_lifecycle.md) | [ioc_container](ioc_container/ioc_container.md), [spring_aop](spring_aop/spring_aop.md) |
| [dependency_injection](dependency_injection/dependency_injection.md) | [ioc_container](ioc_container/ioc_container.md), [spring_configuration](spring_configuration/spring_configuration.md) |
| [spring_configuration](spring_configuration/spring_configuration.md) | [dependency_injection](dependency_injection/dependency_injection.md), [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md) |
| [spring_proxies](spring_proxies/spring_proxies.md) | [spring_aop](spring_aop/spring_aop.md), [spring_transactions](spring_transactions/spring_transactions.md), [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) |
| [spring_aop](spring_aop/spring_aop.md) | [spring_proxies](spring_proxies/spring_proxies.md), [spring_transactions](spring_transactions/spring_transactions.md), [spring_caching](spring_caching/spring_caching.md) |
| [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) | [ioc_container](ioc_container/ioc_container.md), [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md), [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md) |
| [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md) | [spring_configuration](spring_configuration/spring_configuration.md), [spring_cloud_config](spring_cloud_config/spring_cloud_config.md) |
| [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md) | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md), [spring_performance](spring_performance/spring_performance.md) |
| [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md) | [request_handling](request_handling/request_handling.md), [filters_and_interceptors](filters_and_interceptors/filters_and_interceptors.md) |
| [request_handling](request_handling/request_handling.md) | [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md), [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) |
| [filters_and_interceptors](filters_and_interceptors/filters_and_interceptors.md) | [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md), [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) |
| [spring_webflux](spring_webflux/spring_webflux.md) | [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md), [spring_data_jpa](spring_data_jpa/spring_data_jpa.md) |
| [spring_data_jpa](spring_data_jpa/spring_data_jpa.md) | [spring_transactions](spring_transactions/spring_transactions.md), [spring_caching](spring_caching/spring_caching.md) |
| [spring_transactions](spring_transactions/spring_transactions.md) | [spring_data_jpa](spring_data_jpa/spring_data_jpa.md), [spring_aop](spring_aop/spring_aop.md), [spring_proxies](spring_proxies/spring_proxies.md) |
| [spring_caching](spring_caching/spring_caching.md) | [spring_aop](spring_aop/spring_aop.md), [spring_data_jpa](spring_data_jpa/spring_data_jpa.md), [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) |
| [spring_security_architecture](spring_security_architecture/spring_security_architecture.md) | [filters_and_interceptors](filters_and_interceptors/filters_and_interceptors.md), [spring_security_jwt_oauth](spring_security_jwt_oauth/spring_security_jwt_oauth.md), [spring_proxies](spring_proxies/spring_proxies.md) |
| [spring_security_jwt_oauth](spring_security_jwt_oauth/spring_security_jwt_oauth.md) | [spring_security_architecture](spring_security_architecture/spring_security_architecture.md), [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) |
| [spring_cloud_config](spring_cloud_config/spring_cloud_config.md) | [spring_boot_configuration](spring_boot_configuration/spring_boot_configuration.md), [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) |
| [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) | [spring_cloud_config](spring_cloud_config/spring_cloud_config.md), [spring_messaging](spring_messaging/spring_messaging.md) |
| [spring_messaging](spring_messaging/spring_messaging.md) | [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md), [spring_transactions](spring_transactions/spring_transactions.md) |
| [spring_testing](spring_testing/spring_testing.md) | All modules — slice tests isolate specific layers |
| [spring_performance](spring_performance/spring_performance.md) | [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md), [spring_webflux](spring_webflux/spring_webflux.md) |
| [spring_batch](spring_batch/spring_batch.md) | [spring_transactions](spring_transactions/spring_transactions.md), [spring_messaging](spring_messaging/spring_messaging.md), [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md) |
| [spring_ai](spring_ai/spring_ai.md) | [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md), [spring_webflux](spring_webflux/spring_webflux.md) (streaming), [../../llm/advanced_rag/](../llm/advanced_rag/advanced_rag.md), [../../llm/embeddings_and_similarity_search/](../llm/embeddings_and_similarity_search/embeddings_and_similarity_search.md) |
| [spring_native_graalvm](spring_native_graalvm/spring_native_graalvm.md) | [spring_performance](spring_performance/spring_performance.md), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md), [../../java/annotation_processing/](../java/annotation_processing/annotation_processing.md), [../../java/jvm_internals/](../java/jvm_internals/jvm_internals.md) |
| [spring_integration](spring_integration/spring_integration.md) | [spring_messaging](spring_messaging/spring_messaging.md), [spring_events_and_scheduling](spring_events_and_scheduling/spring_events_and_scheduling.md), [../../backend/event_driven_fundamentals/](../backend/event_driven_fundamentals/event_driven_fundamentals.md), [../../java/microservices_patterns/](../java/microservices_patterns/microservices_patterns.md) |
| [spring_modulith](spring_modulith/spring_modulith.md) | [spring_events_and_scheduling](spring_events_and_scheduling/spring_events_and_scheduling.md), [spring_integration](spring_integration/spring_integration.md), [../../lld/solid_principles/](../lld/solid_principles/solid_principles.md), [../../java/microservices_patterns/](../java/microservices_patterns/microservices_patterns.md) |
| [spring_graphql](spring_graphql/spring_graphql.md) | [request_handling](request_handling/request_handling.md), [spring_data_jpa](spring_data_jpa/spring_data_jpa.md) (N+1), [spring_webflux](spring_webflux/spring_webflux.md), [../../backend/graphql/](../backend/graphql/graphql.md) |
| [spring_events_and_scheduling](spring_events_and_scheduling/spring_events_and_scheduling.md) | [spring_transactions](spring_transactions/spring_transactions.md), [spring_messaging](spring_messaging/spring_messaging.md), [spring_aop](spring_aop/spring_aop.md) |
| [spring_hateoas_rest_maturity](spring_hateoas_rest_maturity/spring_hateoas_rest_maturity.md) | [request_handling](request_handling/request_handling.md), [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md), [../../backend/rest_api_design/](../backend/rest_api_design/rest_api_design.md), [../../hld/api_design/](../hld/api_design/api_design.md) |
| [spring_grpc](spring_grpc/spring_grpc.md) | [../../java/grpc_protobuf/](../java/grpc_protobuf/grpc_protobuf.md) (wire format), [../../backend/grpc_and_protobuf/](../backend/grpc_and_protobuf/grpc_and_protobuf.md), [spring_webflux](spring_webflux/spring_webflux.md), [observability_and_tracing](observability_and_tracing/observability_and_tracing.md) |
| [spring_session](spring_session/spring_session.md) | [spring_security_architecture](spring_security_architecture/spring_security_architecture.md), [spring_security_jwt_oauth](spring_security_jwt_oauth/spring_security_jwt_oauth.md), [../../backend/auth_and_authorization_systems/](../backend/auth_and_authorization_systems/auth_and_authorization_systems.md), [../../database/key_value_stores/](../database/key_value_stores/key_value_stores.md) |
| [spring_webflux → rsocket_reactive_messaging.md](spring_webflux/rsocket_reactive_messaging.md) (sub-file) | [../../java/reactive_programming/](../java/reactive_programming/reactive_programming.md), [spring_messaging](spring_messaging/spring_messaging.md), [spring_grpc](spring_grpc/spring_grpc.md) |
| [request_handling → i18n_and_localization.md](request_handling/i18n_and_localization.md) (sub-file) | [validation_and_error_handling](validation_and_error_handling/validation_and_error_handling.md), [ioc_container](ioc_container/ioc_container.md), [../../java/java_time_datetime/](../java/java_time_datetime/java_time_datetime.md) |
| [validation_and_error_handling](validation_and_error_handling/validation_and_error_handling.md) | [request_handling](request_handling/request_handling.md), [spring_mvc_architecture](spring_mvc_architecture/spring_mvc_architecture.md), [spring_boot_autoconfiguration](spring_boot_autoconfiguration/spring_boot_autoconfiguration.md) |
| [observability_and_tracing](observability_and_tracing/observability_and_tracing.md) | [spring_boot_actuator](spring_boot_actuator/spring_boot_actuator.md), [spring_cloud_patterns](spring_cloud_patterns/spring_cloud_patterns.md), [spring_performance](spring_performance/spring_performance.md) |

---

## 7. Case Studies

For a guided learning path through all 9 case studies, see [case_studies/README.md](case_studies/case_studies.md).

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
5. **Review**: Work through all 9 case studies end-to-end — see [case_studies/README.md](case_studies/case_studies.md) for the recommended order

Each module follows the standard 14-section template. See [llm/foundations_and_architecture/README.md](../llm/foundations_and_architecture/foundations_and_architecture.md) as the format reference.
