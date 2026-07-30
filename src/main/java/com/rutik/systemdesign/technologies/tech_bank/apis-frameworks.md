# APIs & app frameworks — technology bank

<!-- tech-bank tier: apis-frameworks -->

The 512 tools whose PRIMARY role — the first, best-weighted one — sits in
the **APIs & app frameworks** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### /actuator/beans
**Short:** Spring Boot Actuator endpoint that dumps every bean in the context with its scope, type and dependencies.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### /actuator/env
**Short:** Spring Boot Actuator endpoint showing every property source and the resolved value at runtime.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, observability/metrics-and-monitoring @3

### @ApplicationModule
**Short:** Spring Modulith annotation declaring a package as an application module with an explicit API surface.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/dependency-injection-and-config @2

### @ApplicationModuleListener
**Short:** Spring Modulith annotation for transactional, async event listeners that decouple application modules.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @2

### @Argument
**Short:** Spring GraphQL annotation binding a GraphQL field argument to a controller method parameter.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

### @Aspect
**Short:** AspectJ/Spring annotation marking a class as an aspect holding pointcuts and cross-cutting advice.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### @AutoConfiguration
**Short:** Spring Boot annotation marking an auto-configuration class; implies proxyBeanMethods = false and ordering hints.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @AutoConfigureAfter/Before
**Short:** Spring Boot annotations that order one auto-configuration relative to another during context startup.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @AutoValue.Builder
**Short:** AutoValue annotation generating a builder plus equals/hashCode/toString at compile time, with no runtime dependency.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @2

### @BatchMapping
**Short:** Spring GraphQL annotation batch-loading a field for all parent objects at once, avoiding the N+1 query problem.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### @Bean
**Short:** Spring annotation declaring a factory method whose return value becomes a container-managed, injectable bean.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @3

### @Builder.Default
**Short:** Lombok annotation preserving a field initializer as the default in a generated builder instead of dropping it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @3

### @ComponentScan
**Short:** Spring annotation that scans packages for @Component classes and registers them as beans.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @Conditional
**Short:** Spring's base annotation for registering a bean only when a programmatic condition matches.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ConditionalOnClass
**Short:** Spring Boot condition that activates an auto-configuration only when a given class is on the classpath.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ConditionalOnMissingBean
**Short:** Spring Boot condition that registers an auto-configured bean only if the application has not defined one itself.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ConditionalOnProperty
**Short:** Spring Boot condition that registers a bean only when a configuration property has a given value.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @Configuration
**Short:** Spring annotation declaring a class of @Bean factory methods; full mode CGLIB-proxies it to honour bean scopes.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ConfigurationProperties
**Short:** Spring Boot annotation that binds a group of external properties onto a type-safe configuration object.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ConstructorBinding
**Short:** Spring Boot annotation binding external configuration into an immutable @ConfigurationProperties record or class.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ControllerAdvice
**Short:** Spring MVC annotation for cross-controller advice: central exception-to-response mapping and response post-processing.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### @DateTimeFormat
**Short:** Spring annotation declaring the pattern or style used to bind and render dates on request parameters and fields.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/text-encoding-and-regex @3

### @EnableAspectJAutoProxy
**Short:** Spring annotation registering the auto-proxy creator that enables @Aspect-based AOP advice on beans.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/dependency-injection-and-config @2

### @EnableAsync
**Short:** Spring annotation that turns on proxy-based @Async processing so annotated methods run on an executor.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/concurrency-and-async @2

### @EnableAutoConfiguration
**Short:** Spring Boot annotation that triggers classpath-driven auto-configuration of beans; folded into @SpringBootApplication.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @EnableBatchProcessing
**Short:** Spring Batch annotation registering job repository and launcher beans; Boot 3 auto-configures these, so omit it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2, apis-frameworks/dependency-injection-and-config @3

### @EnableIntegration
**Short:** Spring annotation bootstrapping the Spring Integration infrastructure: channels, endpoints and message-handling beans.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/dependency-injection-and-config @2

### @EnableScheduling
**Short:** Spring annotation that turns on the scheduling infrastructure backing @Scheduled methods.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### @EnableWebMvc
**Short:** Annotation that switches on Spring MVC's default configuration in non-Boot applications.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @3

### @EventListener
**Short:** Spring annotation making a method an in-process event listener, with ordering, async delivery and commit-bound variants.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @2

### @ExceptionHandler
**Short:** Spring MVC annotation mapping an exception type to a handler method, centralizing exception-to-response translation.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### @HttpExchange
**Short:** Spring annotation declaring an HTTP endpoint on an interface method so a client proxy can be generated for it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### @Import
**Short:** Spring annotation that pulls additional @Configuration classes or registrars into the current context.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @ImportHttpServices
**Short:** Spring Boot annotation auto-configuring @HttpExchange declarative HTTP client interfaces from properties.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2

### @Inject
**Short:** JSR-330 standard injection annotation, type-first and framework-neutral; the portable alternative to @Autowired.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @MutationMapping
**Short:** Spring for GraphQL annotation binding a controller method to a GraphQL mutation field.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### @NamedInterface
**Short:** Spring Modulith annotation marking a package as a module's public API so other modules may depend only on it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @3

### @NumberFormat
**Short:** Spring annotation for locale-aware parsing and rendering of numbers, currencies and percentages.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/text-encoding-and-regex @3

### @Pointcut
**Short:** Spring AOP annotation declaring a named, reusable pointcut expression that advice methods can reference.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### @Primary
**Short:** Spring annotation naming the default bean to inject when several candidates match the same type.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @Profile
**Short:** Spring annotation registering a bean only when the named environment profile is active.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @PropertySource
**Short:** Spring annotation that adds an external property file to the Environment so its keys resolve in @Value/binding.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @Qualifier
**Short:** Spring annotation that disambiguates which bean to inject by name when several candidates match the type.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @QueryMapping
**Short:** Spring for GraphQL annotation binding a controller method to a top-level Query field.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### @Resource
**Short:** JSR-250 injection annotation resolved by bean name first, falling back to type, unlike @Autowired.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @RestControllerAdvice
**Short:** Spring annotation for controller-wide cross-cutting concerns: global @ExceptionHandler mapping and response body advice.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### @Scheduled
**Short:** Spring annotation running a method on a fixed delay, fixed rate or cron expression.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @3

### @SchemaMapping
**Short:** Spring for GraphQL annotation binding a controller method to a field resolver on a schema type.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### @Scope
**Short:** Spring annotation setting a bean's scope, with proxy injection for shorter-lived scopes.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @SpringBootApplication
**Short:** Composite Spring Boot annotation enabling component scanning and auto-configuration on the main class.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### @stomp/stompjs
**Short:** Browser and Node STOMP client over WebSocket; the maintained successor to the old stomp-websocket package.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### @SubscriptionMapping
**Short:** Spring for GraphQL annotation mapping a controller method to a GraphQL subscription returning a stream of results.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### @SuperBuilder
**Short:** Lombok annotation generating a builder that works across an inheritance hierarchy, unlike plain @Builder.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @3

### @TransactionalEventListener
**Short:** Spring annotation binding an event listener to a transaction phase, so handlers fire only after commit.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-access/transactions-and-consistency @1, apis-frameworks/design-patterns-and-principles @3

### @Valid
**Short:** Jakarta Validation annotation that triggers cascading bean validation on a request body or nested object.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### @Validated
**Short:** Spring annotation enabling group-aware Bean Validation on controller args, config properties and methods.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/aop-middleware-and-scheduling @3

### @Value
**Short:** Spring annotation injecting an external property or evaluated SpEL expression into a field, parameter or setter.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### a backend binding
**Short:** The Bridge-pattern idea of choosing an implementation at deploy time, as SLF4J does when bound to Logback or Log4j 2.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, observability/logging @3

### Abstract base classes vs interfaces
**Short:** Design choice between sharing implementation through a base class and declaring a contract through an interface.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Abstract Factory
**Short:** GoF creational pattern producing whole families of related objects behind one interface so the families never get mixed.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Adapter
**Short:** GoF structural pattern converting one interface into another so incompatible types can collaborate.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### add_exception_handler
**Short:** Starlette/FastAPI call that registers a handler mapping an exception type to a custom HTTP response.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### AdvisedSupport
**Short:** Spring AOP config holder for a proxy's advisors; caches the resolved interceptor chain per method.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, caching/in-process-cache @3

### Affordances
**Short:** Spring HATEOAS API that attaches affordances (_templates) describing the inputs of actions on a resource.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### aiohttp
**Short:** Async HTTP client and server for Python asyncio, used for high-concurrency outbound calls and light servers.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2

### annotated-types
**Short:** Tiny Python package of standard constraint metadata (Gt, Lt, Len) that validators like Pydantic read from Annotated.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @2

### AnnotationAwareAspectJAutoProxyCreator
**Short:** Spring bean post-processor that wraps beans in AOP proxies during postProcessAfterInitialization.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/dependency-injection-and-config @2

### Anthropic streaming events
**Short:** Server-sent event stream from the Claude API delivering incremental text, thinking and tool-use blocks.
**Kind:** api
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, llm-apps/tool-use-and-mcp @2

### AopUtils.canApply
**Short:** Spring startup helper that decides whether an advisor matches a bean, and therefore whether to proxy it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/dependency-injection-and-config @3

### AopUtils.getTargetClass
**Short:** Spring utility returning the real class behind a proxied bean, so reflection sees the target rather than the proxy.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/runtime-internals-and-types @2

### AopUtils.isAopProxy
**Short:** Spring utility that reports whether a given object is an AOP proxy rather than the raw target bean.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### Apache Arrow
**Short:** Language-agnostic columnar in-memory format enabling zero-copy data exchange between analytics engines.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-stores/warehouse-and-olap @2, data-movement/batch-and-distributed-compute @3

### Apache Commons JEXL 3
**Short:** Embeddable Java expression engine with a JexlPermissions sandbox for user-supplied expressions.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, security/supply-chain-and-runtime-security @3

### Apollo Client
**Short:** JavaScript/TypeScript GraphQL client with a normalized in-memory cache, query hooks and subscription support.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, caching/in-process-cache @3

### Apollo Federation
**Short:** Composes many GraphQL subgraphs into one supergraph served by a router, so teams own their slice of the schema.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

### Apollo GraphOS Studio
**Short:** Hosted GraphQL schema registry and observability console: schema checks, field usage and operation metrics.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2, observability/metrics-and-monitoring @3

### Apollo Router
**Short:** Rust GraphQL federation gateway that composes subgraph schemas and plans/executes queries across services.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

### Apollo Server
**Short:** Node.js GraphQL server implementation with schema, resolvers and subscription support.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

### Apollo/Relay clients
**Short:** Browser-side GraphQL clients that issue queries, normalize results into a local cache and manage fragments.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, caching/in-process-cache @3

### app.exception_handler
**Short:** FastAPI decorator registering a custom handler for an exception type, e.g. emitting RFC 9457 problem JSON.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### ApplicationEventPublisher
**Short:** Spring interface for publishing in-process application events, optionally async or bound to transaction commit.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2, data-movement/task-queue-and-jobs @3

### ApplicationModules
**Short:** Spring Modulith's entry-point type that discovers application modules and verifies their allowed dependencies.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @2

### ApplicationReadyEvent
**Short:** Spring Boot lifecycle event fired once the embedded server is serving; the safe hook for post-startup work.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/aop-middleware-and-scheduling @3

### Architecture fitness functions
**Short:** Automated tests that assert architectural rules (layering, dependency direction) so drift fails the build.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @2, devtools/testing-and-mocking @3

### ASGI compliance
**Short:** Conformance to the ASGI 3 async server/application interface that lets a Python app run on any ASGI server.
**Kind:** spec
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @3

### AspectJAdviceParameterNameDiscoverer
**Short:** Spring's last-resort discoverer deducing advice parameter names from a pointcut when javac -parameters is absent.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/runtime-internals-and-types @3

### AspectJExpressionPointcut
**Short:** Spring AOP class that parses an AspectJ pointcut expression and acts as both ClassFilter and MethodMatcher.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### AspectJProxyFactory
**Short:** Spring factory that wraps a plain object in an AOP proxy programmatically, handy for unit-testing pointcuts.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, devtools/testing-and-mocking @3

### aspectjweaver
**Short:** AspectJ agent that weaves aspects into classes at load time, covering calls Spring's proxy-based AOP cannot.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, devtools/compiler-toolchain-and-codegen @2

### AsyncAPI for event-driven
**Short:** Specification for documenting event-driven APIs: channels, messages and schemas, the AsyncAPI analogue of OpenAPI.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2

### AsyncEventBus
**Short:** Guava's in-process asynchronous event bus: @Subscribe handlers, type-based dispatch and DeadEvent for misses.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/concurrency-and-async @3, apis-frameworks/aop-middleware-and-scheduling @3

### AutoConfigurationImportSelector
**Short:** Spring Boot's selector that loads candidate auto-configuration classes and filters them against their conditions.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, runtime-systems/runtime-internals-and-types @3

### AutowiredAnnotationBeanPostProcessor
**Short:** The Spring BeanPostProcessor that actually performs @Autowired, @Value and @Inject injection during bean creation.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Avro
**Short:** Compact binary serialization format with a schema and defined evolution rules; common for Kafka event payloads.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2

### AWS Glue Schema Registry
**Short:** AWS-managed schema registry for Avro/JSON/Protobuf, enforcing compatibility for Kafka and MSK producers.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2

### AWS SDK for Java 2.x
**Short:** Java client library for AWS services, with non-blocking HTTP and built-in jittered retry and adaptive rate limiting.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, traffic-edge/rate-limiting-and-resilience @2, platform-delivery/cloud-platform-and-cost @3

### BasicErrorController
**Short:** Spring Boot's default /error handler, which produces the fallback JSON or whitelabel page and can be replaced.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### BatchLoaderRegistry
**Short:** Spring for GraphQL registry for DataLoaders, batching child fetches to kill the GraphQL N+1 problem.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-access/orm-and-data-mapping @3

### BloomRPC/Kreya
**Short:** GUI clients for exploring and calling gRPC services from reflection or proto files; the Postman of gRPC.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

### Bridge
**Short:** GoF structural pattern that splits an abstraction from its implementation so both can vary independently (JDBC, SLF4J).
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### brotli
**Short:** Lossless compression format for HTTP content encoding; better ratios than gzip on text at similar speed.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, caching/http-and-cdn-cache @3

### Buf
**Short:** Modern Protobuf toolchain: schema registry, lint rules, breaking-change detection and code/SDK generation.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/static-analysis-and-linting @2, devtools/compiler-toolchain-and-codegen @2

### buf.build
**Short:** Protobuf toolchain and schema registry: linting, breaking-change detection and remote code generation.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/compiler-toolchain-and-codegen @2, devtools/build-and-dependency-management @3

### Builder
**Short:** Creational pattern assembling an object step by step via a fluent builder, keeping the result immutable and validated.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Chain of Responsibility
**Short:** GoF behavioral pattern passing a request along a chain of handlers until one handles it; the shape behind filter chains.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @2

### ClassFilter
**Short:** Spring AOP pointcut component that rejects whole classes before any method matching is attempted.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### ClassifierCompositeItemWriter
**Short:** Spring Batch writer that routes each item to a different delegate writer based on a Classifier, typically by item type.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @3

### ClientHttpRequestFactoryBuilder
**Short:** Spring Boot's unified builder for HTTP client request factories and their connect/read timeouts.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-access/drivers-and-connection-pooling @3

### ClientHttpRequestFactorySettings
**Short:** Spring Boot's unified settings object for building HTTP client request factories, including connect/read timeouts.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, traffic-edge/rate-limiting-and-resilience @3

### ClientHttpRequestInterceptor
**Short:** Spring SPI for wrapping synchronous RestTemplate/RestClient calls to add headers, retries or logging.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### ClientInterceptor
**Short:** gRPC client-side interceptor chained around every call, with access to metadata for auth, deadlines and tracing.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/aop-middleware-and-scheduling @2, observability/tracing-apm-and-llm-observability @3

### CollectionModel
**Short:** Spring HATEOAS wrapper representing a collection of resources together with hypermedia links.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### Command
**Short:** GoF pattern wrapping a request as an object so it can be queued, logged, retried or undone.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/task-queue-and-jobs @3

### CommonAnnotationBeanPostProcessor
**Short:** Spring BeanPostProcessor handling JSR-250 annotations: @PostConstruct, @PreDestroy and @Resource injection.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### complexity instrumentation
**Short:** GraphQL cost control that scores a query's depth and field weight before execution and rejects anything over budget.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/rate-limiting-and-resilience @2

### Component and JavaFX Parent
**Short:** The JavaFX Composite archetype: a Parent is itself a Node, so layout and rendering recurse over the scene graph.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Composite
**Short:** GoF structural pattern: treat leaves and containers uniformly so a client walks a whole tree through one interface.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### CompositeItemProcessor
**Short:** Spring Batch processor that chains several ItemProcessors so a step can apply transformations in sequence.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @3

### ConditionEvaluationReport
**Short:** Spring Boot report listing every auto-configuration condition and why it matched or did not.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/testing-and-mocking @3

### ConfigDataEnvironmentPostProcessor
**Short:** Spring Boot post-processor that locates and loads application.properties or YAML into the Environment at startup.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### ConfigurableBeanFactory
**Short:** Spring's configurable bean-factory interface for programmatic bean lookup and scope registration.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Confluent Schema Registry
**Short:** Registry that stores and versions Avro/Protobuf/JSON schemas so Kafka producers and consumers validate compatibility.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2

### confuse
**Short:** Python configuration library layering YAML files, env vars and CLI overrides behind a schema with typed views.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Connect
**Short:** Protobuf RPC framework speaking gRPC, gRPC-Web and a plain HTTP/JSON protocol callable straight from browsers.
**Kind:** tech
**Lang:** go, js, java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @3

### Consul KV
**Short:** HashiCorp Consul's distributed key-value store used for dynamic service configuration alongside its service registry.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/dependency-injection-and-config @1, traffic-edge/service-mesh-and-discovery @2, data-stores/key-value-and-embedded @3

### ContentCachingRequestWrapper
**Short:** Spring wrapper that buffers a request body so a filter can log or read it and the controller can still parse it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2, caching/in-process-cache @3

### ContentNegotiatingViewResolver
**Short:** Spring MVC resolver that picks the view or message converter to use based on the request's Accept header.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### contextlib.ExitStack
**Short:** Python stdlib helper that composes a dynamic number of context managers and unwinds them in reverse on exit.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @3

### CORSMiddleware
**Short:** Starlette/FastAPI middleware that injects CORS headers and answers preflight OPTIONS requests automatically.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2

### cuda_array_interface
**Short:** Older zero-copy protocol letting Numba, CuPy and RAPIDS share device buffers without a transfer; predates DLPack.
**Kind:** spec
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, gpu/kernel-programming @3, gpu/gpu-portability-and-precision @3

### curl -N --no-buffer
**Short:** Unbuffered curl invocation used to watch a Server-Sent Events or chunked stream arrive token by token from the CLI.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/io-networking-and-syscalls @2

### Dagger/Hilt
**Short:** Compile-time dependency-injection frameworks that generate the wiring code via annotation processing.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/compiler-toolchain-and-codegen @2

### DataLoader
**Short:** Per-request batching and caching layer that collapses GraphQL resolver N+1 fan-out into one backend call.
**Kind:** tech
**Lang:** js, java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-access/orm-and-data-mapping @2, caching/in-process-cache @3

### Decorator
**Short:** GoF structural pattern wrapping an object to add behaviour without subclassing, as java.io streams do.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### DefaultAdvisorChainFactory
**Short:** Spring AOP component that builds a method's interceptor chain, honouring runtime pointcut evaluation.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### Dependency injection frameworks
**Short:** Containers that construct and wire objects from declarations, so classes depend on interfaces they are handed.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### DispatcherServlet
**Short:** Spring MVC's front controller: receives every request and delegates to handler mapping, adapter and view.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/design-patterns-and-principles @2

### Django REST Framework
**Short:** REST toolkit for Django: serializers, ViewSets and routers over Django models.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### DLPack
**Short:** Open tensor interchange protocol letting CuPy, PyTorch, JAX and TensorFlow share device memory with no copy.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, gpu/gpu-portability-and-precision @2, model-training/deep-learning-framework @3

### Drools
**Short:** Rete-based business rules engine: a DSL plus working memory and agenda, so rules change without code changes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @3

### Dropwizard
**Short:** Opinionated Java microservice framework bundling Jetty, Jersey, Jackson and Metrics into one runnable JAR.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### dynaconf 3.x
**Short:** Layered Python settings library merging files, env vars and Vault, with per-environment profiles and casting.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, security/secrets-and-cryptography @3

### EIP-3009
**Short:** Ethereum token standard for signed transfer authorizations, enabling sign-now settle-later agent payment flows.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, security/secrets-and-cryptography @3

### EntityModel
**Short:** Spring HATEOAS wrapper that attaches hypermedia links to a domain object in a REST response.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### enum singleton
**Short:** Java's single-element enum idiom for a singleton: JVM-guaranteed one instance, free thread safety, serialization-proof.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### EnumSet transition tables
**Short:** State machine encoded as data in nested EnumMap/EnumSet structures instead of branching code.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @3

### EnvironmentPostProcessor
**Short:** Spring Boot extension point for adding or mutating property sources programmatically before the context is created.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### environs
**Short:** marshmallow-based library that parses and validates environment variables into typed application settings.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Envoy gRPC-Web filter
**Short:** Envoy filter that translates browser gRPC-Web calls into normal gRPC for backend services.
**Kind:** api
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/proxy-and-load-balancer @2

### ErrorAttributes
**Short:** Spring Boot strategy interface controlling what the default /error response body contains.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### evans
**Short:** Interactive gRPC client with a REPL that discovers services via reflection and calls methods by hand.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

### ExchangeFilterFunction
**Short:** WebClient's reactive filter SPI for intercepting outbound requests to add auth, retry or logging.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### Explicit in signature
**Short:** Comparison-table cell, not a product: dependencies declared as function parameters rather than pulled from globals.
**Kind:** concept
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### ExposeInvocationInterceptor
**Short:** Spring AOP interceptor exposing the current MethodInvocation in a ThreadLocal for AspectJ advisors.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### Express.js
**Short:** Minimal Node.js HTTP routing and middleware framework, the default choice for REST services on Node.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/web-framework-and-http-client @1

### Facade
**Short:** Structural pattern: one simplified entry point over a complicated subsystem, as SLF4J or JdbcTemplate do.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Factory Method
**Short:** GoF creational pattern: a named method owns the creation rule so callers get an instance without a concrete class.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Falcon
**Short:** Minimal, high-performance Python WSGI/ASGI framework built around resource classes and responder methods.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

### FastAPI
**Short:** Async Python web framework: type-hint routing, Pydantic validation, auto OpenAPI docs, WebSocket and streaming.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2, apis-frameworks/dependency-injection-and-config @2, apis-frameworks/rpc-graphql-and-streaming @3, inference/model-server @3

### FastAPI 0.140+
**Short:** Current FastAPI release whose Depends/Security graph and dependency_overrides give the framework its DI container.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/web-framework-and-http-client @1, security/authentication-and-identity @3

### FastAPI Depends
**Short:** FastAPI's dependency-injection marker resolving a callable per request, with caching and override for tests.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @3, apis-frameworks/web-framework-and-http-client @3

### FastAPI StreamingResponse
**Short:** FastAPI response class streaming an iterator body; the usual way to serve SSE token streams from an LLM.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @2

### FastAPI-based API server
**Short:** Airflow 3.0's rewritten API server, a FastAPI application serving the UI and the task execution API.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-movement/workflow-and-durable-execution @2

### fastapi.Depends with yield
**Short:** FastAPI dependency that yields a resource and cleans it up after the response; the per-request session pattern.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/web-framework-and-http-client @3, runtime-systems/concurrency-and-async @3

### fastapi.exceptions.RequestValidationError
**Short:** FastAPI exception raised when Pydantic request validation fails; exposes per-field loc/msg/type/input.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### fastapi.HTTPException
**Short:** FastAPI's built-in exception for returning a status code, detail body and headers from a route.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

### fastapi.responses.JSONResponse
**Short:** FastAPI/Starlette response class returning an explicit JSON body with a chosen status code and media type.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @3

### fastapi.status
**Short:** Module of named HTTP status-code constants re-exported from Starlette for readable FastAPI responses.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

### federation-jvm
**Short:** JVM implementation of Apollo Federation, letting a Java service act as a subgraph behind a federated gateway.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

### FilterChain
**Short:** Servlet chain-of-responsibility: each filter may handle, transform, or pass the request on via doFilter.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2

### Flask
**Short:** Minimal synchronous Python web framework; common for quick REST endpoints and model-serving prototypes.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, inference/model-server @3

### Flyweight
**Short:** GoF structural pattern that shares immutable intrinsic state across many objects to cut heap duplication.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/memory-processes-and-os @3

### ForwardingCollection
**Short:** Guava base class that delegates every collection method, so a decorator overrides only what it changes.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @2

### ForwardingMap
**Short:** Guava abstract decorator base that delegates every Map method, so you override only the behaviour you change.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @2

### functools
**Short:** Python stdlib higher-order-function toolkit: wraps, partial, reduce, plus lru_cache/cached_property memoization.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/design-patterns-and-principles @1, caching/in-process-cache @2, runtime-systems/collections-and-algorithms @3

### GenericJackson2JsonRedisSerializer
**Short:** Spring Data Redis serializer storing values as JSON with type info; safer across deploys than JDK serialization.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, caching/distributed-cache @2

### GenericVisitorAdapter
**Short:** JavaParser visitor adapter that recurses a Java source AST by default, so you override only the nodes you care about.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @2, devtools/static-analysis-and-linting @3

### Google Guice @Singleton
**Short:** Guice scope annotation giving a container-managed single instance per injector, outside any Spring context.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### Graceful reload
**Short:** Restarting workers without dropping in-flight requests, so config or code changes deploy with no downtime.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, platform-delivery/ci-cd-and-release @2

### GraphiQL
**Short:** In-browser GraphQL IDE for exploring a schema, autocompleting and running queries against any GraphQL endpoint.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

### GraphQL Federation spec
**Short:** Specification for composing many GraphQL subgraphs into one supergraph schema served by a federated gateway.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2, traffic-edge/api-gateway @3

### GraphQL Playground
**Short:** Browser IDE for GraphQL: schema docs, autocomplete and an interactive query console.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

### graphql-java
**Short:** The reference GraphQL execution engine for the JVM; parses, validates and executes queries against a schema.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### gRPC
**Short:** HTTP/2 RPC framework using protobuf contracts, with generated stubs and unary plus bidirectional streaming.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @3

### grpc health checking protocol
**Short:** Standard gRPC health service (Check/Watch) that balancers, meshes and K8s probes use for readiness.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/service-mesh-and-discovery @2, observability/metrics-and-monitoring @3

### grpc interceptors
**Short:** gRPC hook wrapping every call on client or server for auth, retries, metrics and trace propagation.
**Kind:** api
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/aop-middleware-and-scheduling @2, observability/tracing-apm-and-llm-observability @3

### gRPC server reflection
**Short:** gRPC service letting clients discover methods and schemas at runtime; what makes grpcurl work with no .proto.
**Kind:** api
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/service-mesh-and-discovery @3

### gRPC ServerInterceptor
**Short:** gRPC server-side interceptor: a chain around every RPC with access to metadata, deadlines and the call lifecycle.
**Kind:** api
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

### gRPC support
**Short:** Whether a framework can serve or consume gRPC; a comparison-table attribute rather than a product.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### grpc-client-spring-boot-starter
**Short:** Community Spring Boot starter autoconfiguring gRPC client channels via @GrpcClient and grpc.client.* properties.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @2

### grpc-gateway
**Short:** protoc plugin generating a reverse proxy that transcodes REST/JSON requests into gRPC calls.
**Kind:** tech
**Lang:** go
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

### grpc-go
**Short:** The Go implementation of gRPC: generated stubs, interceptors and streaming over HTTP/2.
**Kind:** tech
**Lang:** go
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### grpc-health-probe
**Short:** Standalone binary calling the gRPC Health Checking service, for exec-based liveness probes outside native support.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, platform-delivery/kubernetes-and-orchestration @2, observability/metrics-and-monitoring @3

### grpc-java
**Short:** The JVM implementation of gRPC: generated stubs, HTTP/2 transport, streaming calls, interceptors and deadlines.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### grpc-node
**Short:** The Node.js gRPC implementation: protobuf service stubs, unary and streaming calls, interceptors and deadlines.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### grpc-protobuf
**Short:** gRPC Java runtime artifact providing protobuf message marshalling for generated stubs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2

### grpc-server-spring-boot-starter
**Short:** Community Spring Boot starter auto-configuring a gRPC server: @GrpcService beans and grpc.server.* properties.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @2

### grpc-stub
**Short:** grpc-java runtime artifact providing the generated blocking, async and future client stubs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### gRPC-Web
**Short:** Browser-compatible gRPC wire variant over HTTP/1.1 or HTTP/2, usually terminated by an Envoy translating proxy.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### grpcio
**Short:** The Python gRPC runtime: generated stubs, channels, interceptors and streaming over HTTP/2.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### grpcui
**Short:** Web UI for exploring and calling gRPC services via reflection; the gRPC analogue of a REST client.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

### grpcurl
**Short:** Command-line gRPC client; calls services from a .proto file or server reflection, the curl of gRPC.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

### Gson
**Short:** Google's JSON binding library for Java: simpler and Android-friendly, with no default-typing RCE surface.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### Gson TypeToken
**Short:** Gson's trick for capturing a generic type at runtime, so an erased type like List<Foo> can still be deserialized.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @2

### Guava AbstractIterator
**Short:** Guava base class where computeNext()/endOfData() replace the hand-written hasNext/next lookahead state machine.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @2

### Guava EventBus
**Short:** In-process publish/subscribe bus: @Subscribe handlers, type-based dispatch and DeadEvent for unhandled messages.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/message-broker @3, apis-frameworks/aop-middleware-and-scheduling @3

### Guava ForwardingList
**Short:** Guava abstract decorator base that delegates every List method, so you override only the behaviour you change.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @2

### gunicorn
**Short:** Pre-fork WSGI process supervisor; runs FastAPI/ASGI apps via UvicornWorker across multiple worker processes.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/memory-processes-and-os @3

### gzip
**Short:** Ubiquitous DEFLATE (LZ77 + Huffman) compressor, used for files and as an HTTP content encoding.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/collections-and-algorithms @2, caching/http-and-cdn-cache @3

### GZipMiddleware
**Short:** Starlette and FastAPI middleware gzipping responses above a size threshold; 70-80% smaller JSON payloads.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2, observability/profiling-and-performance @3

### h2c
**Short:** HTTP/2 over cleartext TCP - no TLS - used for local testing and for internal hops behind a terminating proxy.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, devtools/testing-and-mocking @2, runtime-systems/io-networking-and-syscalls @3

### hal-explorer
**Short:** Browsable web UI for navigating HAL hypermedia APIs by following their link relations.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @3

### HAL-FORMS
**Short:** Media-type extension to HAL adding _templates, so a hypermedia response can describe an action's input fields.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### HandlerMethodArgumentResolver
**Short:** Spring MVC extension point supplying a controller method parameter from the request, e.g. @CurrentUser.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### Hasura
**Short:** Engine that auto-generates a GraphQL API with permissions and subscriptions over Postgres and other databases.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-access/orm-and-data-mapping @3, traffic-edge/api-gateway @3

### Helmet.js
**Short:** Express middleware that sets defensive HTTP headers - CSP, HSTS, X-Frame-Options - on every response.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, security/supply-chain-and-runtime-security @2

### Hibernate lazy-loading proxies
**Short:** Virtual-proxy stand-ins for unloaded JPA associations, loaded on first access; source of LazyInitializationException.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-access/orm-and-data-mapping @2

### Hibernate Validator 9.x
**Short:** Reference implementation of Jakarta Bean Validation: constraint annotations, groups and method validation.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/aop-middleware-and-scheduling @3

### HTTP
**Short:** The request/response application protocol of the web: methods, status codes, headers and caching semantics.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/io-networking-and-syscalls @2

### HttpClient.newBuilder()
**Short:** Entry point to the JDK's built-in HTTP client builder, supporting HTTP/2, timeouts and sync or async sends.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/design-patterns-and-principles @2

### HttpComponentsClientHttpRequestFactory
**Short:** Spring request factory backing RestClient/RestTemplate with Apache HttpClient 5: pooling, timeouts and proxies.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-access/drivers-and-connection-pooling @3

### httpie
**Short:** Human-friendly CLI HTTP client with JSON formatting and TLS/handshake inspection, used to poke endpoints by hand.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, devtools/testing-and-mocking @3, runtime-systems/io-networking-and-syscalls @3

### HttpMessageConverter
**Short:** Spring MVC strategy that serializes and deserializes request and response bodies by content type.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### HttpServiceProxyFactory
**Short:** Spring factory that turns an annotated Java interface into a working HTTP client proxy over RestClient/WebClient.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @3

### HttpServletResponseWrapper
**Short:** Servlet decorator letting a filter rewrite or buffer a response body rather than only observe it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/web-framework-and-http-client @3

### httpx
**Short:** Python HTTP client with sync and async APIs, HTTP/2 and pooling; also the test client used against ASGI apps.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, devtools/testing-and-mocking @2

### httpx AsyncClient
**Short:** httpx's async HTTP client with connection pooling; shared via lifespan or scoped per request in a yield dependency.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @3

### httpx-auth
**Short:** Auth extension for httpx supplying OAuth2, API key and AWS SigV4 flows as reusable request authenticators.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, security/authentication-and-identity @2

### httpx.AsyncClient
**Short:** httpx's asynchronous client class; also the standard way to exercise an ASGI app in async tests.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, devtools/testing-and-mocking @2

### hypercorn
**Short:** ASGI server for Python apps with HTTP/2 and HTTP/3 support; an alternative to uvicorn in production deployments.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/rpc-graphql-and-streaming @3

### InitDestroyAnnotationBeanPostProcessor
**Short:** Spring bean post-processor that invokes JSR-250 @PostConstruct and @PreDestroy callbacks.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Initialization-on-demand holder idiom
**Short:** Lazy singleton using a nested holder class so the JVM's class-init lock does the synchronization for free.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/concurrency-and-async @2, runtime-systems/runtime-internals-and-types @3

### Integer.valueOf
**Short:** JDK boxing entry point backed by a flyweight cache; why == holds for small Integers (-XX:AutoBoxCacheMax).
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, caching/in-process-cache @2, runtime-systems/memory-processes-and-os @3

### IntegrationFlow Java DSL
**Short:** Spring Integration's fluent Java DSL for wiring channels, transformers and adapters into an EIP message flow.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/message-broker @2

### InterceptorAndDynamicMethodMatcher
**Short:** Spring AOP internal record pairing an interceptor with its matcher so the match can be re-evaluated per invocation.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### Interpreter
**Short:** GoF behavioral pattern: model a small grammar as a class per rule and evaluate expression trees over a context.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @3

### InvocationHandler
**Short:** JDK interface behind dynamic proxies: one method receives every interface call for interception or delegation.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/aop-middleware-and-scheduling @3

### io.grpc:grpc-netty/grpc-netty-shaded
**Short:** The Netty-based HTTP/2 transport for gRPC Java; the shaded artifact avoids Netty version conflicts.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/io-networking-and-syscalls @3

### io.rsocket:rsocket-core
**Short:** The Java implementation of RSocket, a bidirectional reactive protocol with request-stream and channel modes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### ItemProcessor
**Short:** The transform slot of a Spring Batch chunk step; transaction and restart semantics come from the framework.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @2, apis-frameworks/design-patterns-and-principles @3

### ItemReaderAdapter
**Short:** Spring Batch adapter that turns any existing service method into an ItemReader.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @3

### ItemWriter
**Short:** Spring Batch write slot of a chunk-oriented step, invoked once per chunk inside the step transaction.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @3, apis-frameworks/design-patterns-and-principles @3

### Iterator
**Short:** GoF behavioral pattern exposing sequential traversal of a collection without revealing its internal structure.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @2

### Iterators
**Short:** Guava's Iterators helpers: concat, partition, peekingIterator and friends for composing lazy traversals.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @2

### Jackson
**Short:** The standard Java JSON library: streaming parser, databind to POJOs, and modules for other wire formats.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### Jackson JavaTimeModule
**Short:** Jackson module that serializes java.time types to ISO-8601 instead of numeric timestamps or reflection blobs.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### Jackson JsonFactory
**Short:** Jackson factory producing matched JsonParser and JsonGenerator instances for one wire format; Abstract Factory.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @2

### Jackson JsonNode
**Short:** Jackson's mutable in-memory JSON tree, letting objects and arrays be traversed uniformly without a POJO.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @3

### Jackson JsonNode traversal
**Short:** Walking Jackson's untyped JSON tree node by node, the object structure visitor-shaped code operates on.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @2

### Jackson ObjectMapper
**Short:** Jackson's JSON serializer/deserializer; also a convenient deep-copy and persistable-snapshot mechanism.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### JacksonJsonHttpMessageConverter
**Short:** Spring MVC converter that serializes controller return values to JSON and deserializes request bodies using Jackson.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### Jakarta CDI @ApplicationScoped
**Short:** CDI scope giving one container-managed instance per application, injected through a client proxy.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### Jakarta Expression Language (EL) 5.0
**Short:** Jakarta EE's ${...} expression grammar with a pluggable ELResolver, evaluated in templates and framework config.
**Kind:** spec
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/dependency-injection-and-config @3, runtime-systems/text-encoding-and-regex @3

### Jakarta Validation 3.1
**Short:** Jakarta EE constraint-annotation specification: @NotNull, @Size and friends plus the validator SPI.
**Kind:** spec
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### jakarta.servlet.Filter
**Short:** Servlet interface for chained request interception - correlation IDs, CORS, compression - via chain.doFilter().
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/web-framework-and-http-client @3

### jakarta.servlet.FilterChain
**Short:** The servlet chain handed to doFilter: call chain.doFilter to continue, or return to stop the request there.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2, apis-frameworks/design-patterns-and-principles @3

### jakarta.servlet.http.HttpServlet
**Short:** Jakarta servlet base class whose service() is a Template Method dispatching to the doGet/doPost hooks you override.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/design-patterns-and-principles @2

### Java 8+ default methods on interfaces
**Short:** Interface methods with a body: ship a Template Method skeleton without taking the implementor's inheritance slot.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @3

### Java enum with abstract methods
**Short:** Java idiom where each enum constant overrides an abstract method, giving a compact state machine or strategy table.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Java enum with per-constant method bodies
**Short:** State-machine idiom: each enum constant overrides the transition method, making the state set exhaustive.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### java.awt.Container
**Short:** The AWT Composite archetype: a Container is itself a Component, so layout and painting recurse over the UI tree.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @3

### java.beans.PropertyChangeSupport
**Short:** JDK helper implementing bound properties: register listeners and fire an event only when a value actually changes.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### java.lang.reflect.Proxy
**Short:** JDK dynamic proxy factory generating an interface implementation at runtime; the base of Spring JDK proxies.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/aop-middleware-and-scheduling @3

### java.lang.Runnable
**Short:** The JDK's Command interface: a parameterless unit of deferred work, executable by any executor or thread.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/concurrency-and-async @2

### java.net.http.HttpClient
**Short:** JDK built-in HTTP/1.1 and HTTP/2 client with sync and async APIs, TLS, redirects and pooling behind a builder.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/io-networking-and-syscalls @3, apis-frameworks/design-patterns-and-principles @3

### java.net.http.HttpRequest.newBuilder
**Short:** The JDK HTTP client's request builder - a current, non-legacy example of the Builder pattern, including copy().
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/web-framework-and-http-client @2

### java.nio.file.FileVisitor
**Short:** JDK visitor over a directory tree with pre/post-directory hooks and a FileVisitResult to prune or terminate the walk.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/io-networking-and-syscalls @2

### java.time, Optional, parameter names
**Short:** Jackson's built-in support for java.time, Optional and real parameter names when binding JSON to objects.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @3

### java.util.function
**Short:** Java's standard functional interfaces (Function, Supplier, Predicate) letting lambdas replace strategy classes.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @3

### java.util.function.Consumer
**Short:** JDK functional interface taking one argument and returning nothing; a lambda-sized Command with no undo.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @3

### java.util.ServiceLoader
**Short:** JDK service-discovery mechanism that loads implementations declared in META-INF/services or a module descriptor.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/dependency-injection-and-config @3

### java.util.ServiceLoader.load
**Short:** JDK service-provider lookup that instantiates implementations declared in META-INF/services or module provides.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/dependency-injection-and-config @3

### javax.swing.undo.UndoManager
**Short:** JDK-provided undo/redo stack with edit coalescing; a ready-made Memento caretaker for desktop editors.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### javax.xml.parsers.DocumentBuilderFactory
**Short:** JDK XML factory whose newInstance() picks an implementation producing a matched family of DOM parser objects.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @2

### javax.xml.transform.TransformerFactory
**Short:** JAXP factory whose newInstance() picks an XSLT implementation and returns a matched family of transformer objects.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @2

### JdbcBatchItemWriter
**Short:** Spring Batch writer that flushes a chunk of items with a single JDBC batch insert or update.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-access/drivers-and-connection-pooling @3

### JdbcPagingItemReader
**Short:** Spring Batch reader that pages through a SQL result set with keyset pagination instead of holding a cursor open.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-access/orm-and-data-mapping @2

### JdkClientHttpConnector
**Short:** Spring transport backing WebClient with the JDK's java.net.http.HttpClient instead of Reactor Netty.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### JdkClientHttpRequestFactory
**Short:** Spring request factory backing RestClient with the JDK's java.net.http.HttpClient, no third-party HTTP dependency.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### Jetty
**Short:** Embeddable Java servlet container and HTTP server, the usual alternative runtime to Tomcat under Spring Boot.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @3

### JMESPath
**Short:** Query language for extracting and reshaping values from JSON documents with a user-supplied selector expression.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/text-encoding-and-regex @3

### jMolecules
**Short:** Annotation library that expresses DDD and architectural concepts (aggregate, repository, value object) in code.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### JoinPoint
**Short:** Spring AOP object given to non-around advice exposing the target, arguments and signature at the intercepted call.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### JpaPagingItemReader
**Short:** Spring Batch reader paging through JPA entities with a JPQL query, keeping a step's memory footprint bounded.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-access/orm-and-data-mapping @2

### jq
**Short:** Command-line JSON processor with its own query language; the standard way to slice API or kubectl output.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/version-control-and-workbench @2

### json
**Short:** Python's standard JSON encoder/decoder: portable and str-based, roughly 100 MB/s and slower than orjson.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### JSON-B
**Short:** Jakarta EE standard API for binding Java objects to and from JSON, portable across compliant providers.
**Kind:** spec
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### JSON-RPC 2.0 spec
**Short:** Minimal JSON request/response RPC standard; the wire format MCP is built on.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, llm-apps/tool-use-and-mcp @2, apis-frameworks/data-formats-and-api-contracts @2

### JSONPath
**Short:** Path-expression language for selecting values inside a JSON document; used in tests and config selectors.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/text-encoding-and-regex @3

### jsonschema
**Short:** Lightweight JSON Schema validator for Python; a dependency-free alternative to Pydantic for tool schemas.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/prompting-context-and-structured-output @3

### KafkaItemReader
**Short:** Spring Batch reader consuming a Kafka topic partition from stored offsets as restartable batch step input.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/event-streaming-and-processing @2

### Kreya
**Short:** Desktop GUI gRPC client that builds requests from a .proto file or server reflection.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

### Kryo
**Short:** Fast JVM binary serializer used for deep copies and cache payloads without requiring Serializable.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @3

### Kryo Kryo.copy
**Short:** Kryo's fast reflective deep copy of an object graph, with no Serializable requirement, for hot paths.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/data-formats-and-api-contracts @2

### Kubernetes ConfigMap/Secret
**Short:** Kubernetes objects that inject configuration and credentials into pods as env vars or mounted files.
**Kind:** api
**Lang:** *
**Roles:** apis-frameworks/dependency-injection-and-config @1, security/secrets-and-cryptography @2, platform-delivery/kubernetes-and-orchestration @2

### Litestar
**Short:** ASGI web framework with type-hint routing, layered DI and attrs or Pydantic models; a FastAPI alternative.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

### LocaleChangeInterceptor
**Short:** Spring MVC interceptor that switches the request locale from a query parameter such as ?lang=fr.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2, runtime-systems/text-encoding-and-regex @3

### LocaleContextHolder
**Short:** Spring holder exposing the current request's Locale and TimeZone through a ThreadLocal for i18n lookups.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/text-encoding-and-regex @3

### LocaleContextResolver
**Short:** Spring strategy that decides the locale (and time zone) for a request in MVC or WebFlux.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/text-encoding-and-regex @3

### LocaleResolver
**Short:** Spring strategy deciding the Locale of a request (header, cookie or session) for MVC and WebFlux i18n.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/text-encoding-and-regex @3

### LocalValidatorFactoryBean
**Short:** Spring bean exposing Jakarta Validation with messages resolved through MessageSource for i18n.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/dependency-injection-and-config @2

### Lombok @Builder
**Short:** Lombok annotation generating a builder at compile time, with @Builder.Default and @SuperBuilder variants.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @2

### Lombok @Delegate
**Short:** Lombok annotation generating pass-through methods to a wrapped field, removing decorator/delegation boilerplate.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/compiler-toolchain-and-codegen @2

### maxQueryDepth
**Short:** GraphQL query-depth limit that rejects deeply nested queries before they become an amplification attack.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/rate-limiting-and-resilience @2

### Mediator
**Short:** GoF behavioural pattern: colleagues communicate through one mediator instead of referencing each other.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/message-broker @3

### Memento
**Short:** GoF behavioral pattern capturing an object's state as an opaque snapshot so it can be restored later, e.g. undo.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### MessageSource
**Short:** Spring interface resolving a message code plus arguments and locale into a localized string.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2

### MessageSourceAware
**Short:** Spring callback interface letting a bean receive the container's MessageSource for i18n message resolution.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, runtime-systems/text-encoding-and-regex @3

### MethodMatcher
**Short:** Spring AOP pointcut half that decides per method whether advice applies, with an optional runtime-argument phase.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### MethodValidationPostProcessor
**Short:** Spring post-processor that creates the AOP proxy enabling @Validated method-level constraint checking.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/aop-middleware-and-scheduling @2

### Micronaut
**Short:** JVM microservice framework doing DI and AOP at compile time, giving fast startup and low memory versus reflection.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2, devtools/compiler-toolchain-and-codegen @3

### msgpack
**Short:** Compact binary serialization format with a JSON-like data model, used where JSON is too slow or too large.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### msgspec
**Short:** Very fast Python JSON/MessagePack serialization and validation via typed Structs; a Pydantic alternative.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @3

### MVEL 2
**Short:** Fast JVM expression language with compiled expressions, used for rule conditions evaluated in hot loops.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/text-encoding-and-regex @3

### Netflix DGS
**Short:** Netflix's Spring Boot GraphQL framework: annotation-driven data fetchers, codegen and federation support.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### Node
**Short:** The GUI Composite archetype (as in JavaFX Node): a container is itself a component, so layout and paint recurse.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### now, Instant.now
**Short:** Static factory methods as a creation point: a named call that may cache, return a subtype, or hide the implementation.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @3

### ObjectProvider<T>
**Short:** Spring injection point for lazy, optional or multi-bean lookup; resolves dependencies on demand, not at wiring time.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Observer
**Short:** GoF behavioural pattern: a subject notifies registered listeners of state changes, decoupling it from consumers.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @3

### ofNullable, Stream.of, LocalDate.of
**Short:** JDK static factory methods: unlike constructors they can be named, cache instances and hide a subtype.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### OkHttp
**Short:** Square's JVM HTTP client with connection pooling, HTTP/2 and an interceptor chain for retries, auth and logging.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @3, traffic-edge/rate-limiting-and-resilience @3

### OkHttp Interceptor
**Short:** OkHttp's chained interceptors: application ones run once per call, network ones once per redirect or retry.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @2

### openai
**Short:** Official Python SDK for the OpenAI API: chat, embeddings, structured output, tools and streaming.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, llm-apps/prompting-context-and-structured-output @2, inference/model-server @3

### OpenAPI 3.2
**Short:** The published standard for describing REST APIs in JSON or YAML, driving docs, clients and contract tests.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### OpenAPI 3.x
**Short:** Standard machine-readable REST API description of paths, schemas and auth; drives codegen, docs and gateways.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, traffic-edge/api-gateway @3

### OpenAPI Generator
**Short:** Generates typed client SDKs, server stubs and docs in many languages from an OpenAPI specification.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/compiler-toolchain-and-codegen @2

### openapi-python-client
**Short:** Generates a typed async Python SDK from an OpenAPI document, typically run against a service's own /openapi.json.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/compiler-toolchain-and-codegen @2, apis-frameworks/web-framework-and-http-client @3

### org.springframework.web.filter.CorsFilter
**Short:** Spring's servlet filter applying a CORS configuration source to preflight and actual cross-origin requests.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2, security/authentication-and-identity @3

### orjson
**Short:** Rust-backed JSON serializer for Python, 3-5x faster than the stdlib and native on datetime and UUID.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, observability/profiling-and-performance @3

### PagedModel
**Short:** Spring HATEOAS wrapper carrying a page of resources plus page metadata and next/prev navigation links.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### PagedResourcesAssembler
**Short:** Spring HATEOAS helper turning a Page into a PagedModel with first/prev/next/last navigation links.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### pattern matching for switch
**Short:** Java language feature giving compiler-checked exhaustive dispatch over a sealed hierarchy, replacing double dispatch.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @3

### persisted queries
**Short:** GraphQL technique: clients send a hash of a pre-registered document, shrinking payloads and blocking ad-hoc queries.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, caching/http-and-cdn-cache @2, traffic-edge/rate-limiting-and-resilience @3

### pickle
**Short:** Python's native binary object serialization - fast and general, but unsafe to load from untrusted sources.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, security/supply-chain-and-runtime-security @3

### PoolingHttpClientConnectionManager
**Short:** Apache HttpClient 5's connection pool, capping total and per-route connections so a client cannot exhaust sockets.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-access/drivers-and-connection-pooling @2

### Pothos
**Short:** TypeScript-first GraphQL schema builder that derives the schema from code with full end-to-end type inference.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### ProblemDetail
**Short:** Spring 6 representation of an RFC 9457 problem-details error body, returned instead of ad-hoc error JSON.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### ProceedingJoinPoint
**Short:** Passed to Spring @Around advice; calling proceed() invokes the target, so advice can wrap or skip it.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### Production readiness
**Short:** A framework-comparison attribute rating how ready for production a stack is; not a product.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1

### PropertyChangeListener
**Short:** JavaBeans bound-property mechanism: register listeners and fire events only when a property value actually changes.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### protobuf
**Short:** Google's schema-first binary serialization format, compact and fast, and the payload format for gRPC.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/rpc-graphql-and-streaming @2

### Protobuf schema registry
**Short:** Central store of versioned .proto schemas enforcing compatibility rules between producers and consumers.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2, data-access/schema-and-migration @3

### Protocol Buffers generated builders
**Short:** protoc-generated builder per message, with field-presence tracking, mergeFrom and an immutable build() result.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/rpc-graphql-and-streaming @3

### Prototype
**Short:** Creational pattern: build a new object by copying an existing one, via copy constructors, withers or deep copy.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Proxy
**Short:** GoF structural pattern: a stand-in with the same interface that adds lazy loading, remoting, caching or access control.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @3

### ProxyCreationContext
**Short:** Spring AOP thread-local carrying the bean name under proxy creation, which is what makes the bean() pointcut resolvable.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### ProxyFactory
**Short:** Spring AOP class for creating AOP proxies programmatically when annotation-driven proxying is not enough.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2

### ProxyFactoryBean
**Short:** Spring bean that builds an AOP proxy programmatically from a target plus an explicit advisor chain.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2

### Pydantic v2
**Short:** Python validation and serialization library with a Rust core; typed models define API contracts and structured output.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/prompting-context-and-structured-output @2, runtime-systems/runtime-internals-and-types @3, apis-frameworks/design-patterns-and-principles @3

### pydantic-core
**Short:** The compiled Rust validation/serialization engine underneath Pydantic v2; installed automatically with it.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### pydantic-settings
**Short:** Pydantic BaseSettings package that loads and validates twelve-factor config from env vars and .env files.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, security/secrets-and-cryptography @3

### pydantic-settings 2.x
**Short:** Pydantic v2 settings management: typed config from env vars, dotenv and secret files, with SecretStr masking.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/data-formats-and-api-contracts @3

### pydantic.BaseModel
**Short:** Pydantic base class declaring a typed schema that validates, coerces and serializes request and response bodies.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @3

### pydantic.ValidationError
**Short:** The structured error Pydantic raises when a model fails validation, carrying per-field location, message and type.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### pydantic[email]
**Short:** Pydantic extra pulling in email-validator so the EmailStr type can enforce RFC-compliant address validation.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### python-dotenv
**Short:** Loads key=value pairs from a .env file into the process environment so local config stays out of the code.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Quarkus
**Short:** Kubernetes-native Java framework with build-time wiring and GraalVM native-image support for fast, small services.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2, devtools/compiler-toolchain-and-codegen @3, platform-delivery/container-and-image @3

### Quartz
**Short:** Java job scheduler with cron triggers, JDBC job persistence and clustered execution with misfire handling.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2

### Quartz Job
**Short:** Quartz's unit of scheduled work: serializable job data stored in a JobStore, with cron triggers and misfire policy.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2, data-movement/workflow-and-durable-execution @3

### Rails
**Short:** Ruby on Rails: convention-over-configuration MVC web framework with ActiveRecord ORM and REST routing.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-access/orm-and-data-mapping @3

### Raw os.environ
**Short:** Reading configuration straight from the process environment, with no typing, defaults or validation layer.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Reactor Netty
**Short:** Netty-based reactive HTTP client and server; the default event-loop runtime underneath Spring WebFlux.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2, runtime-systems/io-networking-and-syscalls @3

### ReactorClientHttpConnector
**Short:** Reactor Netty transport behind WebClient, the default non-blocking HTTP connector in Spring.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2

### Reader
**Short:** java.io.Reader: an abstract character-stream class that is one whole template method over the single primitive read().
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/io-networking-and-syscalls @2, runtime-systems/text-encoding-and-regex @3

### Records or enums as strategy holders
**Short:** Idiom holding a fixed strategy set in an enum or record: a closed set, a loggable name and free valueOf parsing.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Redoc
**Short:** Renders an OpenAPI spec into readable three-panel reference documentation, as a static page or a CLI build step.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### ReflectiveMethodInvocation
**Short:** Spring AOP's invocation object that walks the interceptor chain in proceed() before reflectively calling the target.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/runtime-internals-and-types @2

### RelaxedPropertyResolver
**Short:** Spring Boot internal doing relaxed binding, matching my-prop, my_prop, myProp and MY_PROP to one key.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Relay Connection
**Short:** GraphQL cursor-pagination convention of edges, nodes and pageInfo that clients like Relay expect.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @3

### ReloadableResourceBundleMessageSource
**Short:** Spring MessageSource reading i18n bundles from the filesystem and reloading them on a cache interval without restart.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, runtime-systems/text-encoding-and-regex @3, apis-frameworks/web-framework-and-http-client @3

### RepresentationModel
**Short:** Spring HATEOAS base class for a single resource representation carrying hypermedia links alongside its data.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### RepresentationModelAssembler
**Short:** Spring HATEOAS strategy converting a domain object into a representation model with hypermedia links attached.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### RequestMappingHandlerAdapter
**Short:** Spring MVC adapter that resolves handler-method arguments and invokes the matched @RequestMapping controller method.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/design-patterns-and-principles @3

### RequestMappingHandlerMapping
**Short:** Spring MVC component that indexes @RequestMapping methods and resolves an incoming request to the handler.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### requests
**Short:** Python's ubiquitous synchronous HTTP client; simple and blocking, so unsuitable inside an async event loop.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

### ResourceBundleMessageSource
**Short:** Spring MessageSource resolving i18n messages from classpath resource bundles, cached for the JVM's lifetime.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/web-framework-and-http-client @2, runtime-systems/text-encoding-and-regex @3

### ResponseBodyAdvice
**Short:** Spring MVC hook intercepting a controller's return value before it is written, for envelope wrapping.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### ResponseEntityExceptionHandler
**Short:** Spring MVC base class mapping standard framework exceptions to RFC 9457 ProblemDetail responses.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

### REST
**Short:** Resource-oriented HTTP architectural style; the default synchronous request/response contract between services.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### RestClient
**Short:** Spring's synchronous fluent HTTP client, the default for new blocking code in place of RestTemplate.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### RestClientAdapter
**Short:** Spring adapter that backs a declarative @HttpExchange interface with a RestClient instance.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### RestTemplate
**Short:** Spring's original synchronous template-style HTTP client; in maintenance mode - new code should use RestClient.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### routers
**Short:** Enterprise-integration routers: named channels plus routing rules making a mediator's topology explicit.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/message-broker @3

### rsocket-transport-netty
**Short:** Netty transports for RSocket over TCP, WebSocket and Aeron; reactive request/stream messaging with backpressure.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/io-networking-and-syscalls @3

### RSocketMessageHandler
**Short:** Spring component routing inbound RSocket frames to @MessageMapping handler methods by route.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### RSocketRequester
**Short:** Spring's fluent RSocket client covering request-response, fire-and-forget, request-stream and channel.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### RSocketStrategies
**Short:** Spring configuration holder for RSocket encoders/decoders (CBOR, Protobuf, JSON), route matching and metadata.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2

### SAXParserFactory
**Short:** The JDK's textbook Abstract Factory: newInstance() picks an XML implementation that produces matched parsers.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/data-formats-and-api-contracts @2

### schema introspection
**Short:** GraphQL's ability to query its own type system, which is what powers client codegen, playgrounds and docs.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2

### Schema Registry
**Short:** Central store of Avro/Protobuf/JSON event schemas that enforces compatibility rules as producers evolve.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2, data-movement/data-quality-and-lineage @3

### schema validation
**Short:** Enforcing a declared shape on a message so fields stay separated and untrusted text cannot pose as instructions.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/prompting-context-and-structured-output @2

### Schema versioning
**Short:** Hashing or versioning a tool or API schema so consumers detect contract drift when a deploy changes it.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/tool-use-and-mcp @2, platform-delivery/ci-cd-and-release @3

### Scope
**Short:** The lifetime a dependency-injected object gets; FastAPI dependencies are per-request by default.
**Kind:** concept
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### ScopedProxyMode
**Short:** Spring setting injecting a proxy for a shorter-lived bean so a singleton can hold a request-scoped dependency.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @3

### Scopes.SINGLETON
**Short:** Guice scope annotation giving one container-managed instance per injector.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### ScrollSubrange
**Short:** Spring for GraphQL type that turns a Spring Data Scroll position into keyset-paginated connection results.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-access/orm-and-data-mapping @2

### server reflection
**Short:** gRPC service that publishes a server's proto descriptors so clients like grpcurl can call methods without the .proto.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

### Server-Sent Events
**Short:** One-way HTTP streaming format pushing incremental server events (such as LLM tokens) with auto-reconnect.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### Server-Sent Events (SSE) spec
**Short:** W3C/WHATWG standard for a one-way server-to-client event stream over plain HTTP; the basis of token streaming.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### ShadowMatchUtils
**Short:** Spring AOP's global cache of AspectJ shadow matches, keeping per-call pointcut evaluation to a map lookup.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, caching/in-process-cache @3

### ShedLock
**Short:** Distributed lock for @Scheduled methods, backed by JDBC, Redis or Mongo, so only one instance runs a given job.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-access/transactions-and-consistency @2, data-movement/task-queue-and-jobs @3

### SimpleClientHttpRequestFactory
**Short:** Spring's HttpURLConnection-backed RestTemplate transport; the dependency-free fallback when no HTTP client is present.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### Singleton
**Short:** GoF creational pattern giving exactly one instance; in a DI application the container should own the lifecycle instead.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/dependency-injection-and-config @3

### SmartLifecycle
**Short:** Spring interface for beans that must start and stop in a controlled phase order, with graceful shutdown callbacks.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/aop-middleware-and-scheduling @3

### socket.io
**Short:** Node and browser realtime messaging library over WebSocket with automatic fallback, rooms and reconnection.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

### SockJS
**Short:** WebSocket emulation layer that falls back to XHR streaming or long polling when a real WebSocket cannot be established.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### SockJS client
**Short:** Browser library negotiating a WebSocket-like session, falling back to XHR streaming when WebSocket is blocked.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### Spring @Async
**Short:** Spring annotation dispatching a method onto a TaskExecutor, making it fire-and-forget or Future-returning.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/concurrency-and-async @2, data-movement/task-queue-and-jobs @3

### Spring @Bean methods
**Short:** A factory method the Spring container calls once per scope, with dependencies supplied as method parameters.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### Spring @Component
**Short:** Annotation registering a bean that is injected rather than looked up, one instance per application context.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### Spring @HttpExchange
**Short:** Spring annotation that turns a Java interface into a typed HTTP client proxy, marshalling calls into requests.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/rpc-graphql-and-streaming @3

### Spring @Scheduled
**Short:** Spring annotation running a bean method on a cron expression or fixed rate/delay from a managed task scheduler.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @3

### Spring @Scope
**Short:** Declares a bean's lifecycle scope - singleton, prototype, request, session - which is not object cloning.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @3

### Spring AOP
**Short:** Spring's proxy-based aspect layer intercepting bean method calls to apply @Transactional, @Cacheable and custom advice.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2

### Spring AOP proxies
**Short:** Container-applied decoration: Spring wraps a bean in a JDK or CGLIB proxy and runs ordered advisors around each call.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/dependency-injection-and-config @3

### Spring ApplicationEventPublisher
**Short:** Spring's in-process event bus: type-dispatched observers, @Order, @Async and commit-bound delivery.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/dependency-injection-and-config @2, apis-frameworks/aop-middleware-and-scheduling @3

### Spring Batch
**Short:** Spring's batch framework: chunk-oriented read-process-write steps with transactions and restartability.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @2, data-movement/task-queue-and-jobs @2, data-movement/workflow-and-durable-execution @3

### Spring Batch Admin
**Short:** Legacy web UI for Spring Batch: browse job executions, restart failures and inspect step metrics.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, observability/alerting-and-incident-response @3

### Spring Batch ItemReader
**Short:** Spring Batch read slot of a chunk-oriented step, called once per item with restart state tracked for you.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @3, apis-frameworks/design-patterns-and-principles @3

### Spring Boot
**Short:** Opinionated Java application framework: auto-configuration, starters and an embedded server for REST services.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @1

### Spring Boot JsonMapperBuilderCustomizer
**Short:** The supported hook for adjusting Spring Boot's auto-configured Jackson JsonMapper, since the built mapper is immutable.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/dependency-injection-and-config @2

### Spring Boot RestClient
**Short:** Spring's modern synchronous HTTP client with a fluent API, configured through the spring.http.client properties.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

### Spring Cloud Config
**Short:** Centralized configuration server backed by Git, serving versioned properties and encrypted values to Spring clients.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, platform-delivery/infrastructure-as-code-and-config @2, security/secrets-and-cryptography @3

### Spring Data REST
**Short:** Auto-exposes Spring Data repositories as a hypermedia HAL REST API with paging, sorting and discoverable links.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-access/orm-and-data-mapping @2, apis-frameworks/data-formats-and-api-contracts @3

### Spring events
**Short:** In-process publish/subscribe inside the Spring context; how Modulith keeps modules decoupled without a broker.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @3, data-movement/event-streaming-and-processing @3

### Spring Expression Language
**Short:** SpEL: runtime expression interpreter for property access and method calls inside @Value, @Cacheable, @PreAuthorize.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2, security/authorization-and-policy @3

### Spring FactoryBean<T> and BeanFactory
**Short:** Spring's container-level factory hooks; the bean definition, plus @Profile/@Conditional, chooses the concrete family.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### Spring for GraphQL
**Short:** Spring's GraphQL integration over GraphQL Java: annotated controllers, subscriptions, batching and a test client.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

### Spring Integration MessageChannel
**Short:** Spring Integration channel abstraction giving an explicit, monitorable topology of routers and transformers.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @2, data-movement/message-broker @3

### Spring MVC HandlerInterceptor
**Short:** Spring MVC hook with preHandle/postHandle/afterCompletion running inside DispatcherServlet after handler mapping.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/design-patterns-and-principles @3

### Spring RestClient
**Short:** Spring's fluent synchronous HTTP client, a facade over the underlying client, message converters and error handling.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/design-patterns-and-principles @2

### Spring Statemachine
**Short:** Spring framework for declarative state machines: states, transitions, guards, nested regions, state persistence.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/workflow-and-durable-execution @3

### Spring WebFlux
**Short:** Spring's non-blocking reactive web stack built on Reactor and Netty, with Flux/Mono handler signatures.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2

### Spring WebMvc.fn
**Short:** Spring's functional servlet routing: RouterFunction, ServerRequest and HandlerFilterFunction instead of controllers.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, traffic-edge/api-gateway @3

### Spring WebSocket
**Short:** Spring's WebSocket support including handshake handling, session management and STOMP sub-protocol messaging.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @2

### Spring, Guice, or Jakarta CDI
**Short:** The JVM's DI containers; constructor injection makes the abstraction the only thing a class names.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

### spring-aspects
**Short:** Spring's AspectJ weaving module, needed for @Configurable domain objects and self-invocation-proof aspects.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

### spring-batch-core
**Short:** Spring Batch's Job/Step chunk model with a JobRepository for restart, skip and retry of long-running batch work.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2, data-movement/batch-and-distributed-compute @3

### spring-batch-infrastructure
**Short:** Spring Batch's infrastructure module: ItemReader/ItemWriter implementations, chunk processing, retry and skip policies.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @2, data-movement/task-queue-and-jobs @3

### spring-beans jar
**Short:** The Spring module holding BeanFactory, BeanDefinition and BeanPostProcessor - the core of the IoC container.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, runtime-systems/runtime-internals-and-types @3

### spring-boot-configuration-processor
**Short:** Annotation processor generating metadata from @ConfigurationProperties for IDE autocompletion.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/compiler-toolchain-and-codegen @2

### spring-boot-starter-batch
**Short:** Spring Boot starter pulling in Spring Batch core and infrastructure for chunk-oriented batch jobs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2

### spring-boot-starter-hateoas
**Short:** Spring Boot starter pulling in Spring HATEOAS and HAL rendering for hypermedia-driven REST responses.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### spring-boot-starter-restclient
**Short:** Spring Boot starter pulling in RestClient and its auto-configuration without dragging in an embedded web server.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, devtools/build-and-dependency-management @3

### spring-boot-starter-rsocket
**Short:** Spring Boot starter auto-configuring an RSocket server and RSocketRequester.Builder for reactive messaging.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

### spring-boot-starter-validation
**Short:** Spring Boot starter pulling in Hibernate Validator so Jakarta Bean Validation annotations take effect.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### spring-boot-starter-webclient
**Short:** Spring Boot 4 starter bringing WebClient and its reactive HTTP stack without also pulling in a web server.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @3

### spring-boot-starter-websocket
**Short:** Boot starter dependency pulling spring-websocket and spring-messaging in for both raw WebSocket and STOMP APIs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/build-and-dependency-management @3

### spring-cloud-config-server
**Short:** Serves versioned external configuration from Git or Vault over HTTP to Spring clients; enabled with @EnableConfigServer.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### spring-cloud-starter-bus-kafka
**Short:** Spring Cloud Bus over Kafka: broadcasts config-refresh and management events to every instance.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, data-movement/message-broker @2, data-movement/event-streaming-and-processing @3

### spring-cloud-starter-config
**Short:** Spring Cloud Config client starter that fetches externalized configuration from a config server at startup.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, platform-delivery/infrastructure-as-code-and-config @3

### spring-cloud-starter-openfeign
**Short:** Declarative HTTP client defined as an annotated interface, integrated with load balancing and Resilience4j.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/service-mesh-and-discovery @3

### spring-context jar
**Short:** The Spring module providing ApplicationContext, bean lifecycle, events, scheduling and annotation-driven configuration.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### spring-context-indexer
**Short:** Spring annotation processor that writes a compile-time component index, removing classpath scanning at startup.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/compiler-toolchain-and-codegen @2, observability/profiling-and-performance @3

### spring-core jar
**Short:** Spring's foundation module: Resource loading, the type conversion service and shared utility classes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/data-formats-and-api-contracts @3

### spring-grpc-client-spring-boot-starter
**Short:** Official Spring gRPC starter auto-configuring managed channels and injectable stubs via GrpcChannelFactory.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

### spring-grpc-server-spring-boot-starter
**Short:** Official Spring gRPC starter auto-configuring a gRPC server bound to spring.grpc.* properties on Boot 3.x.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @2

### spring-grpc-spring-boot-starter
**Short:** Official Spring starter that auto-configures a gRPC server and channels for Spring Boot services.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

### spring-messaging
**Short:** Spring's messaging abstraction: @MessageMapping, channels and SimpMessagingTemplate behind STOMP/WebSocket.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-movement/message-broker @2

### spring-modulith-core
**Short:** Spring Modulith's core artifact: declares application modules, verifies their boundaries and wires module events.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @2, apis-frameworks/aop-middleware-and-scheduling @3

### spring-modulith-docs
**Short:** Spring Modulith module that generates architecture documentation and C4 diagrams from the verified module structure.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @2

### spring-websocket
**Short:** Spring's servlet-stack WebSocket module: WebSocketHandler, STOMP messaging and SockJS fallback transports.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### spring.batch.jdbc.initialize-schema=always
**Short:** Spring Boot property that auto-creates the Spring Batch BATCH_* metadata tables at startup; use never in production.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-access/schema-and-migration @2, apis-frameworks/dependency-injection-and-config @3

### spring.config.import
**Short:** Boot property pulling in extra configuration files, secrets or a Config Server at startup.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

### spring.http.client.service
**Short:** Spring Boot property namespace auto-configuring @HttpExchange client interfaces in named groups: base URL, timeouts.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2

### spring.mvc.problemdetails.enabled=true
**Short:** Spring Boot property making built-in MVC exceptions render as RFC 9457 ProblemDetail bodies.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2, apis-frameworks/dependency-injection-and-config @3

### springdoc-openapi
**Short:** Generates an OpenAPI 3 document and Swagger UI from Spring controllers and their annotations at runtime.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @2

### SSE
**Short:** Server-Sent Events: a one-way streaming HTTP transport where the server pushes text events to the browser.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

### sse-starlette
**Short:** Starlette/FastAPI add-on providing EventSourceResponse for server-sent events with keepalive and reconnect headers.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

### Starlette
**Short:** Lightweight ASGI toolkit FastAPI is built on: routing, request/response lifecycle, WebSockets and streaming responses.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/rpc-graphql-and-streaming @2

### Starlette BaseHTTPMiddleware
**Short:** Starlette's class-based middleware: override dispatch and work with Request/Response objects instead of raw ASGI.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2

### Starlette CORS middleware
**Short:** ASGI middleware adding CORS headers; wildcard origins are rejected by browsers when credentials are allowed.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, security/authentication-and-identity @2, apis-frameworks/web-framework-and-http-client @3

### Starlette pure ASGI
**Short:** Middleware written straight against the ASGI scope/receive/send callables - no buffering, unlike BaseHTTPMiddleware.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, apis-frameworks/web-framework-and-http-client @2

### starlette.status
**Short:** Named HTTP status-code constants such as HTTP_404_NOT_FOUND; fastapi.status is the same module re-exported.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

### State
**Short:** Behavioral pattern where an object changes behavior by swapping state objects instead of branching on a flag.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Step
**Short:** Spring Batch's restartable unit of work with its own transaction boundary and execution metadata.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2, apis-frameworks/design-patterns-and-principles @3

### Stoplight
**Short:** OpenAPI design and documentation platform: visual spec editing, style linting via Spectral, and hosted API docs.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### Stoplight Studio
**Short:** Visual OpenAPI editor for designing and linting an API contract before any implementation exists.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/version-control-and-workbench @3

### Strategy
**Short:** GoF pattern swapping an algorithm behind an interface; in Java it is Comparator, lambdas or a Map of beans.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### Strawberry
**Short:** Code-first Python GraphQL library that derives the schema from dataclass-style type annotations.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

### struct
**Short:** Python stdlib module packing and unpacking C-layout binary records to and from bytes.
**Kind:** api
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/io-networking-and-syscalls @2, runtime-systems/text-encoding-and-regex @3

### Supplier
**Short:** Java functional interface for a zero-argument factory lambda, used for lazy values and class-free commands.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/runtime-internals-and-types @3

### Swagger UI
**Short:** Renders an OpenAPI document as browsable, executable API documentation.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @3

### Teardown support
**Short:** Comparison-table cell, not a product: whether a DI mechanism can release resources after the response, e.g. via yield.
**Kind:** concept
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

### Template Method
**Short:** Behavioral pattern: a base class fixes the algorithm skeleton and subclasses fill in the varying steps.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### ThreadPoolTaskScheduler
**Short:** Spring's configurable thread-pool scheduler backing @Scheduled, replacing the default single-threaded executor.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/concurrency-and-async @2

### Tomcat
**Short:** The servlet container embedded in Spring Boot by default, with a thread-per-request pool capped at 200 threads.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @3

### Tomcat/Jetty
**Short:** The embedded servlet containers Spring Boot runs on; Boot 4 requires a Servlet 6.1 baseline.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @3

### tools.jackson.core:jackson-core
**Short:** Jackson 3's streaming core: JsonParser, JsonGenerator and the token factory the databind layers build on.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### tools.jackson.core:jackson-databind
**Short:** Jackson 3's data-binding artifact providing ObjectMapper/JsonMapper, POJO binding and the tree model.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

### tools.jackson.module:jackson-module-kotlin
**Short:** Jackson module teaching the mapper about Kotlin data classes, nullability and default constructor arguments.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @3

### Traverson
**Short:** Spring HATEOAS Java client that walks a hypermedia API by following link relations rather than hardcoded URLs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### ujson
**Short:** C-backed JSON serializer, a near drop-in replacement for the stdlib json module at roughly 2-3x the speed.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, observability/profiling-and-performance @3

### UndoableEdit
**Short:** Swing undo API providing a ready-made bounded undo/redo caretaker with edit coalescing and significance flags.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

### urql
**Short:** Lightweight, extensible GraphQL client for JavaScript front-ends with a document-cache and exchange-based plugin model.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, caching/in-process-cache @3

### uvicorn
**Short:** ASGI server that runs FastAPI/Starlette apps, including WebSocket upgrades and multi-worker production serving.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2, apis-frameworks/rpc-graphql-and-streaming @3

### Vert.x event bus
**Short:** Vert.x's address-based in-process message bus with point-to-point and pub/sub modes, clusterable across nodes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/message-broker @2, runtime-systems/concurrency-and-async @3

### Visitor
**Short:** GoF behavioural pattern: move an operation out of a closed type hierarchy into a visitor with a method per type.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

### WebClient
**Short:** Spring's reactive non-blocking HTTP client returning Reactor Mono/Flux; the streaming counterpart to RestClient.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2, apis-frameworks/rpc-graphql-and-streaming @3

### WebDataBinder
**Short:** Spring MVC component binding request parameters onto command objects, with property editors, converters and allow-lists.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### Webhooks
**Short:** HTTP callbacks a provider POSTs on an event, used to push notifications instead of polling.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

### WebMvcConfigurer
**Short:** Spring MVC callback interface for adding interceptors, converters and CORS without replacing defaults.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2

### WebMvcLinkBuilder
**Short:** Spring HATEOAS builder generating hypermedia links from controller methods instead of hard-coded URL strings.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

### WebSocket
**Short:** Protocol upgrading an HTTP connection to a persistent full-duplex channel for bidirectional, low-latency messaging.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/io-networking-and-syscalls @3

### websockets
**Short:** Low-level asyncio WebSocket client and server library for Python; Starlette uses it under the hood.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/concurrency-and-async @3, runtime-systems/io-networking-and-syscalls @3

### Worker model
**Short:** The multi-process deployment shape for ASGI apps: N worker processes, each running one event loop.
**Kind:** concept
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2, runtime-systems/memory-processes-and-os @3

### wrapt
**Short:** Python decorator and proxy library that wraps functions transparently, preserving the descriptor protocol for methods.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/design-patterns-and-principles @3

### wscat
**Short:** Command-line WebSocket client for connecting to and poking a raw WebSocket endpoint by hand.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

### x402 v2
**Short:** Open HTTP 402 payment protocol (Coinbase, now Linux Foundation) letting agents pay per request over HTTP, MCP or A2A.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/tool-use-and-mcp @2, llm-apps/agent-framework @3

### yq
**Short:** Command-line YAML processor with jq-style expressions, used to read and patch Kubernetes and CI manifests.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, platform-delivery/infrastructure-as-code-and-config @2, devtools/version-control-and-workbench @3

### zlib
**Short:** The reference DEFLATE (LZ77 + Huffman) compression library behind gzip, PNG and HTTP content encoding.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/collections-and-algorithms @2, runtime-systems/io-networking-and-syscalls @3

### Zod
**Short:** TypeScript-first schema validation with type inference; the usual way to declare tool schemas in JS agents.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/tool-use-and-mcp @2
