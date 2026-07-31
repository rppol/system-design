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

It implements the STOMP frame protocol over a WebSocket, so the browser gets destinations,
`subscribe` with per-subscription callbacks, acknowledgement modes and transactions rather
than raw frames. The `Client` object owns the lifecycle: set `brokerURL` and it dials, and
`reconnectDelay` makes reconnection automatic — with the important caveat that subscriptions
are not restored for you, so resubscribing belongs in the `onConnect` callback. Heartbeats are
configured on both directions and are what detect a connection a proxy has silently dropped.

Reach for it against any STOMP broker or a Spring WebSocket endpoint. The costs are that the
protocol is text-framed and therefore chattier than a purpose-built binary one, that a
`webSocketFactory` is needed to run it over SockJS for legacy fallback, and that message
delivery guarantees are the broker's, not the library's — an in-memory simple broker loses
everything on reconnect.

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

The facade jar carries only the API; the implementation is whatever separate artifact happens
to be on the classpath at startup, discovered through a service-provider lookup. SLF4J finds a
`ServiceProvider` supplied by `logback-classic` or `log4j-slf4j2-impl`; JDBC finds a `Driver`;
JPA finds a persistence provider. Libraries compile against the abstraction and never name a
backend, so the deployable unit gets to choose.

Reach for this shape whenever many libraries must agree on one facility that the application
owns. The cost is that resolution is a classpath fact rather than a compile-time one: two
bindings produce an ambiguity warning and an arbitrary winner, zero bindings produce a silent
no-op, and both surface at startup. Pin the binding in one place in the build and exclude
transitive ones.

### Abstract base classes vs interfaces
**Short:** Design choice between sharing implementation through a base class and declaring a contract through an interface.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

An interface declares a contract and holds no state, and a class may implement any number of
them, so it is the right unit for a capability. A base class also carries fields and
constructor logic and consumes the single inheritance slot, so it buys shared implementation
at a real price. Java default methods and Python's `abc` blur the line — a default method adds
behaviour but still no fields, and an `ABC` with `@abstractmethod` gives interface semantics
inside a class hierarchy.

Default to the interface, and add a skeletal `Abstract...` companion beside it when several
implementers genuinely share code, the way `Collection` and `AbstractCollection` pair up. The
base class's cost is fragility: a change to a protected method breaks every subclass, and
subclasses often depend on internals nobody documented. When the shared behaviour needs its
own state, prefer composition and delegate.

### Abstract Factory
**Short:** GoF creational pattern producing whole families of related objects behind one interface so the families never get mixed.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

The client holds a factory interface with one creation method per product, and a concrete
factory returns a matching set. The consistency guarantee is structural rather than checked:
because a single factory object is the only source of every product, pairing a Windows button
with a macOS scrollbar is simply not expressible. It is Factory Method repeated across a
family, one level of indirection higher.

Reach for it when parallel product families really do vary together — widget toolkits,
per-dialect SQL object sets, cross-platform drivers. The cost is asymmetric: adding a new
family is easy, adding a new product type means editing every concrete factory and the
interface itself. With only one family, a Factory Method or a plain map of suppliers carries
the same intent with far less scaffolding.

### Adapter
**Short:** GoF structural pattern converting one interface into another so incompatible types can collaborate.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

Two forms exist. The object adapter holds the adaptee as a field and delegates, so it works
with `final` classes and can front several objects at once; the class adapter inherits from
both sides, which single-inheritance languages mostly rule out. Either way the adapter
implements the interface the caller already speaks and translates each call, argument and
error into the vocabulary of the thing behind it.

Reach for it at the seam with third-party or legacy code you cannot change, so the translation
lives in one file instead of being smeared across callers. The cost is a hidden impedance
mismatch: a method the adaptee cannot honour becomes a thrown exception or a silent no-op, and
the caller has no way to know. If whole data models rather than call shapes are being
converted, an explicit anti-corruption layer is the honest name for it.

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

aiohttp implements HTTP on top of `asyncio`: a `ClientSession` for outbound calls and a small server framework with its own router. The client half is what most projects want, because one session pools connections and keeps sockets alive, so thousands of concurrent requests share a single thread while each awaits its own socket rather than occupying a worker.

Reach for it when a service fans out to many slow upstreams and you are already inside an event loop; a blocking call made from a coroutine still stalls every other request on that loop. Its server half is rarely chosen over an ASGI framework today, and `httpx` is the usual alternative on the client side.
### annotated-types
**Short:** Tiny Python package of standard constraint metadata (Gt, Lt, Len) that validators like Pydantic read from Annotated.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @2

It contains almost no code — just small frozen dataclasses like `Gt`, `Ge`, `Le`,
`MultipleOf`, `Len` and `Predicate` that are meant to be placed inside `typing.Annotated`. The
point is that a constraint becomes a piece of standard metadata attached to a type rather than
library- specific syntax, so `Annotated[int, Gt(0)]` means the same thing to Pydantic, to a
different validator, and to a human reading the annotation with nothing installed.

Reach for it when you want constraints in shared type aliases that several tools consume, or
when writing a validation library that should interoperate. The limit is that it defines
vocabulary and enforces nothing: the constraints are inert unless a validator chooses to
interpret them, and anything more specific than the handful of provided predicates still
requires library-native syntax such as Pydantic's own `Field`.

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

Arrow specifies a memory layout, not a file format: values of a column live in one contiguous
buffer with a separate validity bitmap for nulls, laid out identically in every language
binding. Because the layout is the specification, a Python process and a C++ or Rust library
can share the same buffer with no serialization at all, and the IPC format is that layout
written straight to a stream or a mapped file. Vectorized engines read it directly, and Arrow
Flight moves it over gRPC without converting to rows.

Reach for it as the interchange between analytical components — a query engine handing results
to pandas or Polars, a Parquet reader feeding a model. The costs are that it is a columnar
in-memory representation, so row-at-a-time access and single-record updates are the wrong
shape for it, and that a full Arrow dependency is heavy for a service that only moves a few
records.

### Apache Commons JEXL 3
**Short:** Embeddable Java expression engine with a JexlPermissions sandbox for user-supplied expressions.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, security/supply-chain-and-runtime-security @3

The value in version 3 is `JexlPermissions`: the engine is built with an explicit policy over
which packages, classes and methods an expression may touch, so a script cannot reach
`java.lang.Runtime` or walk to a classloader through an exposed object. Expressions or full
scripts are parsed into a reusable compiled form and evaluated against a `JexlContext`
supplying the variables, and options control whether unknown variables are silent or an error.

Reach for it when expressions come from users or from configuration a non-developer edits, and
you need a sandbox rather than trust. The costs are that the sandbox is only as good as the
objects you place in the context — exposing one rich domain object can hand back everything
the permissions were meant to withhold — and that it is another expression dialect for the
team to learn. Inside Spring, SpEL is already available; CEL is the choice when
non-Turing-complete evaluation with predictable cost is a requirement.

### Apollo Client
**Short:** JavaScript/TypeScript GraphQL client with a normalized in-memory cache, query hooks and subscription support.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, caching/in-process-cache @3

The cache is what distinguishes it. Results are normalized into a flat store keyed by
`__typename` plus `id`, so an entity fetched by one query is the same object everywhere it
appears and a mutation that returns the updated entity updates every screen showing it with no
extra code. Fetch policies (`cache-first`, `cache-and-network`, `network-only`) choose per
query how much of that store to trust, and the framework hooks return loading and error state
alongside data.

Reach for it when an application has enough shared entities that a normalized store pays for
itself. The costs are size and subtlety: it is a large dependency, and the parts that are not
automatic — cache updates after a mutation that adds or removes list members, pagination merge
functions, cache eviction — are where the real work goes, producing stale lists that look like
server bugs. For simple fetching, `urql` or a plain query hook is far lighter.

### Apollo Federation
**Short:** Composes many GraphQL subgraphs into one supergraph served by a router, so teams own their slice of the schema.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

Each subgraph is an ordinary GraphQL service that annotates its schema with federation directives -- `@key` declares the fields identifying an entity, `@external` and `@requires` describe fields it borrows from elsewhere -- and a composition step checks those pieces fit together into one supergraph schema. At runtime the router reads that schema, plans a query across subgraphs, and resolves cross-service references through each subgraph's entity resolver, so a client sees one endpoint and never learns the topology. The problem it solves is organisational more than technical: a single monolithic GraphQL schema becomes a merge-conflict funnel once several teams write into it, and ownership of a type's fields cannot be split. Reach for it when independent teams own separate domains behind one graph; one team with one service gains nothing but a router hop and a composition pipeline.

### Apollo GraphOS Studio
**Short:** Hosted GraphQL schema registry and observability console: schema checks, field usage and operation metrics.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2, observability/metrics-and-monitoring @3

Subgraph schemas are published to a registry on deploy, and clients report the operations they
actually execute, which is what makes the interesting feature possible: a schema check
compares a proposed change against real recent traffic and tells you whether any live
operation would break, so removing a field nobody has called in thirty days is safe and
removing one a mobile release still uses is blocked. Field-level metrics show latency and
error rates per resolver.

Reach for it when a federated graph has enough teams that schema changes need a gate somewhere
other than code review. The costs are that it is a hosted commercial service in the critical
path of your release pipeline, that usage-based checks are only as good as the client
reporting feeding them — an unreported client is invisible and its queries will break — and
that it is Apollo-specific tooling around your graph.

### Apollo Router
**Short:** Rust GraphQL federation gateway that composes subgraph schemas and plans/executes queries across services.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

It sits in front of a set of subgraph services, each owning a slice of one graph. A client sends a single query against the composed supergraph schema; the router builds a query plan that splits the operation into per-subgraph fetches, runs the independent ones in parallel, resolves entity references between them and stitches the result back into the shape the client asked for.

Reach for it when several teams own different parts of one API and you want them to deploy independently behind one endpoint. A single GraphQL service needs no router at all, and federation adds real cost: schema composition becomes a CI gate, and a slow subgraph now shows up as a slow field on someone else's query.

### Apollo Server
**Short:** Node.js GraphQL server implementation with schema, resolvers and subscription support.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

You give Apollo Server a schema plus a resolver map and it serves that schema over HTTP, handling parsing, validation, execution order, batching hooks and error formatting so you only write the field resolvers. Around that it adds the operational layer a GraphQL endpoint needs: persisted queries, response caching hints, subscriptions, plugins for tracing, and Federation for composing several subgraph services into one supergraph.

Reach for it when the GraphQL layer itself is Node; if your services are Java or Python, use that ecosystem's GraphQL server rather than adding a Node hop purely for the schema.
### Apollo/Relay clients
**Short:** Browser-side GraphQL clients that issue queries, normalize results into a local cache and manage fragments.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, caching/in-process-cache @3

Both normalize responses into a client-side store and both build on fragments, but their
philosophies differ sharply. Relay is opinionated and compiler-driven: a build step reads the
fragments each component declares, composes them into one query per route, and generates typed
artifacts, and it requires the schema to follow the Node and Connection conventions. Apollo
Client imposes no build step and lets you write queries wherever you like, trading Relay's
guarantees for flexibility.

Choose Relay when the application is large, the schema is under your control and you want data
requirements colocated with components and enforced by a compiler. Choose Apollo for almost
everything else, since the ramp is shorter. The shared cost is the normalized cache itself:
list mutations, pagination and eviction need explicit handling in both, and getting them wrong
shows up as stale UI rather than as an error.

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

They are ordinary unit tests that assert over the structure of the code rather than its
behaviour: ArchUnit reads the compiled classes and lets you state that nothing in `..domain..`
may reference `..infrastructure..`, that no cycles exist between packages, or that every class
named `*Repository` sits in the right layer. Spring Modulith's `ApplicationModules.verify()`
is the same idea scoped to module boundaries. Because they run in the test phase, a violation
fails the build the day it is introduced instead of being found in a review a year later.

Reach for one as soon as a rule exists that people keep breaking. The cost is maintenance: a
rule written too tightly produces constant false failures and gets `@Disabled`, so record a
frozen baseline for existing violations and require a written reason for each exception. For
coarse boundaries, a real module system enforces the same thing with no test code.

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

It is a Java agent. Attached with `-javaagent:aspectjweaver.jar`, it hooks classloading and
rewrites bytecode as classes are defined, applying aspects declared in `META-INF/aop.xml`, so
advice reaches constructors, field access, `private` and `final` methods, self-invocations and
classes the Spring container never manages — everything proxying structurally cannot.

Reach for it when the limitation is genuinely blocking: instrumenting a domain model,
`@Configurable` injection into `new`-ed objects, or advising a third-party library. The costs
are heavy. Every environment — IDE, tests, containers — needs the agent argument or behaviour
differs silently between them, class loading slows measurably, and the resulting bytecode does
not match the source, which makes debugging harder and can confuse other agents doing their
own instrumentation. Weave the narrowest scope you can and prefer compile-time weaving where
the build allows it.

### AsyncAPI for event-driven
**Short:** Specification for documenting event-driven APIs: channels, messages and schemas, the AsyncAPI analogue of OpenAPI.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2

The document describes channels rather than paths: what a service publishes and subscribes to,
the message payload schemas, the headers, and protocol-specific bindings for Kafka, AMQP, MQTT
or WebSocket, including things like partition keys and queue durability that have no HTTP
analogue. Version 3 split operations from channels, which is what finally let one document
express that service A produces to a channel service B consumes.

Reach for it when an event-driven estate has enough producers and consumers that "which topics
exist and what is on them" is a real question. The costs are ecosystem maturity — codegen and
tooling are noticeably thinner than OpenAPI's — and the fact that a document nothing validates
against becomes fiction. Tie it to the schema registry so payload schemas have one source
rather than two.

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

Avro writes no field names or tags into the payload: values are serialized positionally
according to the writer's schema, which is why the encoding is so compact and why the schema
is mandatory to read anything at all. Evolution works by schema resolution — the reader
matches the writer's fields to its own by name, uses a declared default for a field the writer
did not have, and ignores fields it does not know — so adding a field with a default is
backward compatible and adding one without a default is not. Object container files embed the
schema in a header; Kafka messages instead carry a registry id.

Reach for it for high-volume event payloads where compactness and enforced evolution rules
matter. The costs are that nothing is self-describing on the wire, making a registry
operationally mandatory, and that the union-with-null idiom for optionality and the strict
default rules trip up teams used to JSON's tolerance.

### AWS Glue Schema Registry
**Short:** AWS-managed schema registry for Avro/JSON/Protobuf, enforcing compatibility for Kafka and MSK producers.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2

It is a registry integrated with the AWS serialization libraries rather than a standalone
service: the producer's serializer registers the schema under a registry and schema name,
receives a version id, and prefixes each record with a small header carrying that id instead
of the schema, so consumers resolve and cache it. Compatibility modes — backward, forward,
full and their transitive forms — are enforced at registration, and the encoders can also
compress the payload.

Reach for it when the estate is already on MSK, Kinesis or Flink under AWS and IAM should
govern schema access alongside everything else, since it is bundled with Glue at no separate
charge. The costs are portability and ecosystem: the wire header is AWS's own format, not
Confluent's, so a consumer built for Confluent Schema Registry cannot read the records and
migrating means a dual-read period. Third-party Kafka tooling largely assumes the Confluent
format.

### AWS SDK for Java 2.x
**Short:** Java client library for AWS services, with non-blocking HTTP and built-in jittered retry and adaptive rate limiting.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, traffic-edge/rate-limiting-and-resilience @2, platform-delivery/cloud-platform-and-cost @3

The v2 rewrite made clients immutable and thread-safe, so one client per service is built at
startup and shared, and it separated the HTTP layer behind an interface: a synchronous Apache
or URL-connection client, or `NettyNioAsyncHttpClient` for the async clients that return
`CompletableFuture`. Retries are a first-class policy — the standard mode uses jittered
exponential backoff with a retry token bucket so a widespread failure cannot amplify into a
retry storm, and adaptive mode adds client-side rate limiting when throttled.

Reach for it for any JVM service calling AWS. The costs to plan for are that a client is
expensive to construct, so creating one per request is a serious performance bug; that the
default credentials provider chain walks several sources and its failure messages are opaque;
and that the module granularity is a feature you must use — depend on the individual service
artifacts rather than the aggregate, or the jar and the cold start both balloon.

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

Both are desktop applications that load a service catalogue — from a `.proto` file or by
server reflection — and render an editable request per method with response, status and
metadata panes, plus saved collections and environment variables. BloomRPC was the early
open-source option and is no longer actively developed; Kreya is the maintained
commercial-with-free-tier successor and also handles REST and OpenAPI.

Reach for one when the audience wants a GUI: QA, front-end developers, or anyone debugging a
mesh they did not build. The costs are that a saved collection is another artifact that drifts
from the schema, that team sharing usually sits behind the paid tier, and that nothing here is
scriptable. For CI, reproducible bug reports and anything you want in version control,
`grpcurl` is the tool.

### Bridge
**Short:** GoF structural pattern that splits an abstraction from its implementation so both can vary independently (JDBC, SLF4J).
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

Two hierarchies, joined by composition: the abstraction holds a reference to an implementor
interface and forwards the primitive operations to it, while each side subclasses freely. That
turns an M-by-N class explosion into M plus N. JDBC is the canonical case, where `Connection`
and `Statement` are the abstraction and each vendor driver is an implementor; SLF4J splits the
same way between the logging API and its binding.

Reach for it when both axes genuinely vary and you can see the multiplication coming — shapes
by rendering backend, message types by transport. The cost is that the implementor interface
must be designed early and is expensive to change once several backends exist, and one more
indirection makes stack traces longer. If only one axis varies, Strategy or plain delegation
says the same thing with half the types.

### brotli
**Short:** Lossless compression format for HTTP content encoding; better ratios than gzip on text at similar speed.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, caching/http-and-cdn-cache @3

Brotli's advantage on web text is not just a better entropy coder but a built-in static
dictionary of common web fragments — HTML tags, HTTP headers, common English words — so short
responses compress well without needing to learn patterns from the content itself. It has
eleven quality levels and a configurable window, and the level choice matters more than with
gzip: the top levels are dramatically more expensive and are meant for content compressed once
and served many times.

Reach for it for static assets compressed at build time, and at a low or middling level for
dynamic responses; browsers advertise `br` only over HTTPS. The costs are that high-level
compression is too slow for per-request use, that non-browser clients and older proxies may
not accept the encoding so you must keep a gzip fallback, and that for binary payloads it
offers little over gzip while Zstandard is faster at similar ratios.

### Buf
**Short:** Modern Protobuf toolchain: schema registry, lint rules, breaking-change detection and code/SDK generation.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/static-analysis-and-linting @2, devtools/compiler-toolchain-and-codegen @2

Buf replaces the raw `protoc` invocation with a configured toolchain: `buf lint` enforces naming and style rules, `buf breaking` diffs your `.proto` files against a previous commit or a registry version and fails when a change would break the wire or the generated API, and `buf generate` runs plugins from a YAML config instead of a long shell line. The Buf Schema Registry hosts modules so consumers depend on a versioned schema rather than vendoring files.

Reach for it in any repo where more than one team consumes the same protos — the breaking-change gate is the reason, since renumbering a field or changing a type is silent at compile time and corrupt at runtime.
### buf.build
**Short:** Protobuf toolchain and schema registry: linting, breaking-change detection and remote code generation.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/compiler-toolchain-and-codegen @2, devtools/build-and-dependency-management @3

The hosted half of the Buf toolchain is a schema registry with a module system: a repository
of `.proto` files is a module with a version, other modules depend on it through `buf.yaml`
with resolved versions pinned in `buf.lock`, and the registry hosts the well-known third-party
protos so a build no longer means vendoring files by hand. Remote plugins run code generation
on the registry's side, so a contributor needs no local `protoc` or plugin binaries, and
generated SDKs can be consumed directly from a language package manager.

Reach for it when several teams share protobuf definitions and copying files between
repositories has become the coordination problem. The costs are dependency on a hosted service
in the build path, a paid tier for private modules and team features, and lock-in to Buf's
module conventions — the local CLI works fine standalone if you would rather keep schemas in
your own monorepo.

### Builder
**Short:** Creational pattern assembling an object step by step via a fluent builder, keeping the result immutable and validated.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

The builder accumulates fields through chained setters that return `this`, then `build()`
validates the whole set at once and hands back an immutable object whose constructor is
private. The point is not the fluency but that a half-configured object never escapes: the
mutable phase is the builder, and the invariant check happens exactly once at the boundary
between the two.

Reach for it past roughly four parameters, or whenever most parameters are optional and
telescoping constructors would multiply. The cost is that nothing checks at compile time that
a required field was set — the failure moves to `build()`, at runtime. A staged builder, where
each step returns a different interface, buys the compile-time check back at the price of an
interface per stage. With few parameters, a record or plain constructor is better.

### Chain of Responsibility
**Short:** GoF behavioral pattern passing a request along a chain of handlers until one handles it; the shape behind filter chains.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @2

Each handler holds a reference to the next and decides whether to handle the request, mutate
it and pass it on, or stop the chain. Servlet filters, the Spring Security filter chain and
OkHttp interceptors are all this shape, and the interesting property is that a handler can act
both before and after the rest of the chain by wrapping the `next` call, which is what makes
timing, logging and retry work.

Reach for it when the handler set is configuration rather than code — ordered, insertable,
removable per deployment. The costs are that a request can fall off the end unhandled unless
the last link is a terminal handler, that ordering bugs are subtle because every element looks
locally correct, and that debugging means walking the whole chain. If exactly one handler
always applies, a keyed lookup is clearer and faster.

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

The receiver, the method and its arguments are bundled into one object exposing `execute()`,
which decouples the caller from what actually happens and, crucially, moves the invocation off
the stack and into the heap where it can be stored. Once a request is a value you can put it
on a queue, persist it, replay it, log it for audit, or pair it with an inverse for undo.

Reach for it for undo stacks, job queues, transactional outboxes and anything that must
survive a restart. The cost appears the moment commands are serialized: they become a wire
contract, so a field rename breaks replay of everything already queued, and versioning them is
now your problem. If the operation is never deferred, reversed or recorded, a method reference
already is the pattern.

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

The server walks the parsed document before execution and assigns a cost — graphql-java ships
`MaxQueryComplexityInstrumentation` and `MaxQueryDepthInstrumentation` for exactly this —
summing per-field weights and multiplying by list `first`/`last` arguments so a nested
pagination explosion scores high. Over budget, the operation is rejected with an error and
nothing is executed, which is the point: the resolvers never run, so the database is never
touched.

Reach for it on any graph where clients write their own queries. The cost is that a static
score is a guess: a weight of 1 on a field backed by a slow join is mispriced, and a list
argument with no maximum lets `first: 100000` through the arithmetic unchallenged. Budgets
also need per-client tuning or they block legitimate work. On an internal graph, an allowlist
of trusted documents is simpler and exact.

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

Leaf and container both implement the same component interface, and the container's
implementation of an operation is to forward it to its children. Recursion therefore lives in
the structure itself, not in the caller, so client code that renders one node renders an
entire tree without knowing the depth. Filesystems, UI scene graphs and expression trees are
the recurring examples.

Reach for it when clients should be indifferent to whether they hold one thing or many. The
cost is the uniform interface: leaves inherit `add()` and `remove()` they cannot honour, and
you must choose between throwing at runtime or splitting the interface and giving up the
uniformity that motivated the pattern. Cycles turn a traversal into infinite recursion, so
guard parenting if the structure is user-editable.

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

The producer's serializer registers the schema once and prepends its numeric id to every message instead of the schema itself, so consumers fetch the schema by id and cache it while the bytes on the wire stay small. Registration is checked against a compatibility mode per subject — backward compatibility by default, meaning a new schema must still be able to read data written by the previous one — which turns a removed required field from a 3am consumer crash into a failed produce at deploy time.

Use it as soon as more than one team reads a topic: it is the enforcement point for the event contract, and it covers Avro, Protobuf and JSON Schema alike.

### confuse
**Short:** Python configuration library layering YAML files, env vars and CLI overrides behind a schema with typed views.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

Configuration is a stack of YAML sources — a package default shipped with the application, a
system file, a user file, then command-line overrides — and reading a key returns a view
rather than a value, resolving through the layers in order so a user file need only contain
what it changes. A view is converted and validated on access with a template:
`config["port"].get(int)`, or a full template describing an entire section with defaults and
choices.

Reach for it for an application distributed to users who edit a config file, which is the case
it was designed for and where the built-in per-platform config directory handling saves work.
The costs are that it is YAML-first with environment variables as an afterthought, which is
backwards for a containerized service, and that the view abstraction is unfamiliar and
produces no typed object to pass around. For a twelve-factor service, `pydantic-settings` or
`environs` fit better.

### Connect
**Short:** Protobuf RPC framework speaking gRPC, gRPC-Web and a plain HTTP/JSON protocol callable straight from browsers.
**Kind:** tech
**Lang:** go, js, java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @3

Connect's own protocol puts a normal HTTP POST with a JSON or protobuf body at a path shaped
`/package.Service/Method`, so a call is reachable from `curl` and from a browser's `fetch`
with no proxy and no special framing. The same servers and clients also speak gRPC and
gRPC-Web on the same port, negotiated by content type, which is what makes it a drop-in in
front of an existing gRPC mesh.

Reach for it when browsers are first-class clients or when you want gRPC's typed contracts
without an Envoy translation layer in the path. The costs are ecosystem rather than technical:
it is a newer stack with fewer third-party interceptors and integrations than `grpc-go` or
`grpc-java`, and streaming over the Connect protocol has the same buffering-proxy caveats as
gRPC-Web. When both ends are internal services you control, plain gRPC remains the default.

### Consul KV
**Short:** HashiCorp Consul's distributed key-value store used for dynamic service configuration alongside its service registry.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/dependency-injection-and-config @1, traffic-edge/service-mesh-and-discovery @2, data-stores/key-value-and-embedded @3

The store is a hierarchical key space replicated by Raft across the server cluster, and its
distinguishing feature is the blocking query: a read may pass the modify index it last saw and
the server holds the request open until that key changes or the timeout expires, so a client
watches for configuration changes without polling. Sessions layered on keys give distributed
locks and leader election, and `consul-template` renders files and signals processes when
watched keys move.

Reach for it when Consul is already in place for service discovery and health checking, since
configuration then needs no extra system and Spring Cloud Consul can back a config server with
it. The costs are that it stores opaque bytes with no schema, versioning or history, that
values are unencrypted at rest unless you add it, so real secrets belong in Vault, and that a
key everything depends on turns Consul into a hard runtime dependency of every service.

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

It is a Python-level convention rather than a C struct: an object exposes a
`__cuda_array_interface__` dictionary with `shape`, `typestr`, a `data` tuple of device
pointer and read-only flag, optional `strides`, a version, and a `stream` entry for
synchronization. A consumer reads the dictionary and wraps the same device memory. Numba
introduced it and CuPy, RAPIDS and PyTorch all understand it, which made zero-copy interchange
possible in the GPU Python ecosystem before a cross-language standard existed.

It still works and remains the path of least resistance between Numba and CuPy. For anything
new, prefer DLPack: it is the cross-language standard, is what the array API standard's
`from_dlpack` uses, and covers non-CUDA devices, whereas this interface is CUDA-only and
Python- only. The shared hazard is the same — the consumer must respect stream ordering, or it
reads memory a kernel has not finished writing.

### curl -N --no-buffer
**Short:** Unbuffered curl invocation used to watch a Server-Sent Events or chunked stream arrive token by token from the CLI.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/io-networking-and-syscalls @2

`curl` buffers its output when it is not writing to a terminal, which is why a piped streaming
response appears to hang and then arrive all at once. `-N` (the long form is `--no-buffer`)
turns that off, so each chunk is written as it is received and you can watch tokens or events
land in real time. Add `-v` to see the response headers and confirm the content type, and
`--max-time` so a stream that never ends does not hold the shell.

Reach for it as the first diagnostic on any streaming endpoint, because it isolates the
question: if `curl -N` streams and the browser does not, the problem is client-side or a
buffering proxy in between, not the server. What it cannot do is parse — you see raw `data:`
lines, so for anything beyond confirming that bytes flow, pipe into a small script or use a
client library.

### Dagger/Hilt
**Short:** Compile-time dependency-injection frameworks that generate the wiring code via annotation processing.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/compiler-toolchain-and-codegen @2

Dagger resolves the entire object graph at compile time with an annotation processor and emits
plain Java factory classes, so there is no reflection and no runtime graph construction — a
missing binding is a compilation error rather than a startup exception, and the generated code
is readable. Hilt is a layer over Dagger for Android that predefines the components and scopes
matching the platform's lifecycles, so a developer annotates rather than assembling components
by hand.

Reach for them on Android, where startup time and method count matter and reflection-based
containers are a poor fit, and in any JVM context wanting compile-time-verified wiring. The
costs are build time — annotation processing on a large graph is slow, and every wiring change
triggers regeneration — and a learning curve around components, modules, scopes and
subcomponents whose error messages are notoriously hard to read. For a server, Spring's
runtime flexibility usually wins; Micronaut and Quarkus offer the compile-time approach there.

### DataLoader
**Short:** Per-request batching and caching layer that collapses GraphQL resolver N+1 fan-out into one backend call.
**Kind:** tech
**Lang:** js, java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-access/orm-and-data-mapping @2, caching/in-process-cache @3

A GraphQL resolver runs once per field per object, so a list of 50 posts each resolving `author` fires 50 identically shaped queries. A DataLoader wraps a batch function: individual `.load(id)` calls are collected within one tick of the event loop, dispatched once as `loadFn([id1, id2, ...])`, and the results scattered back to the waiting promises -- the batch function must return values in the same order as the keys it received, which is the classic implementation bug. It also memoizes per key, so the same author requested by ten posts is fetched once. Create a fresh loader per request and never a global one: a process-lifetime loader is a cache with no invalidation, and it will serve one user's data to another.

### Decorator
**Short:** GoF structural pattern wrapping an object to add behaviour without subclassing, as java.io streams do.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

A decorator implements the component interface and holds a delegate of the same interface, so
decorators nest arbitrarily and each adds behaviour before or after forwarding. N independent
decorators give two-to-the-N combinations without that many classes, which is why `java.io`
composes buffering, compression and encoding by construction rather than by subclass.

Reach for it when the combinations are open-ended and chosen at runtime. The costs are real:
stack traces grow a frame per layer, identity breaks because the wrapper is not the wrapped
object so `equals` and `instanceof` against the concrete type fail, and order matters in ways
nothing enforces — buffering outside compression behaves nothing like the reverse. If the
combinations are fixed and few, a subclass or a configuration flag is less machinery.

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

The container reads declarations — annotations, a module class, or configuration — builds the
object graph by resolving each constructor parameter to a provider, and owns the lifecycle and
scope of what it creates. The split that matters operationally is when this happens: Spring
and Guice resolve reflectively at startup, while Dagger, Micronaut and Quarkus generate the
wiring at compile time, which is why the latter start in milliseconds and produce native
images without reflection metadata.

Reach for one when the graph is large enough that a hand-written composition root is real
work, or when you want scopes and interception. The cost is that wiring errors move from the
compiler to startup, or with lazy beans to first use, and reflective containers add both
startup time and a proxying layer that distorts stack traces. For a small program, wiring by
hand in `main` is fully compiler-checked and faster.

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

DRF layers on Django: a `Serializer` or `ModelSerializer` handles validation and the object-to-JSON conversion in both directions, a `ViewSet` bundles the list/retrieve/create/update/destroy actions for a model, and a router generates URL patterns from that viewset. Around it sit pluggable authentication and permission classes, throttling, pagination, content negotiation, and a browsable HTML API that makes hand-testing endpoints easy. The tradeoff is that the convenience assumes your API mirrors your models -- the generic views stop helping as soon as a response spans several aggregates or the write path carries real business rules, and you end up writing plain `APIView`s anyway. Reach for it when the project is already Django and the ORM is the source of truth; its views are synchronous, so a streaming or heavily concurrent API fits it poorly.

### DLPack
**Short:** Open tensor interchange protocol letting CuPy, PyTorch, JAX and TensorFlow share device memory with no copy.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, gpu/gpu-portability-and-precision @2, model-training/deep-learning-framework @3

The protocol is a C struct, `DLManagedTensor`, describing a strided tensor — device type and
id, data pointer, dtype, shape, strides, byte offset — plus a deleter callback that the
consumer invokes when it is finished, which is how ownership is transferred without either
framework knowing about the other. At the Python level a producer implements `__dlpack__` and
`__dlpack_device__` and a consumer calls `from_dlpack`, so a CuPy array becomes a PyTorch
tensor on the same GPU memory with no copy.

Reach for it to move tensors between frameworks in a pipeline — a RAPIDS preprocessing step
feeding a PyTorch model — where a device-to-host round trip would dominate. The constraints
are that the memory must be strided, so sparse and ragged structures are out; that CUDA stream
synchronization is the consumer's responsibility and getting it wrong yields intermittent
wrong answers rather than an error; and that the buffer is shared, so a mutation is visible on
both sides.

### Drools
**Short:** Rete-based business rules engine: a DSL plus working memory and agenda, so rules change without code changes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @3

Rules are written in DRL as when-then pairs matching over facts inserted into a working
memory, and the engine indexes those conditions into a network — PHREAK, the successor to Rete
— so adding a fact incrementally re-evaluates only the conditions it could affect rather than
re-running every rule. Matched rules are placed on an agenda, ordered by salience and conflict
resolution, and firing one may insert or modify facts, which re-triggers matching. That
inference loop is the capability an if-chain does not have.

Reach for it when the rules are numerous, interdependent, and owned by people who are not
developers, and when they must change without a deployment. The costs are severe and
underestimated: the execution order of a large rule base is genuinely hard to reason about,
debugging means understanding the agenda, and a rule set nobody fully understands is a worse
liability than the code it replaced. For a few dozen independent rules, a decision table in
code is safer.

### Dropwizard
**Short:** Opinionated Java microservice framework bundling Jetty, Jersey, Jackson and Metrics into one runnable JAR.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1

It is a curated assembly rather than a framework in its own right: Jetty for HTTP, Jersey for
JAX-RS resources, Jackson for JSON, Metrics for instrumentation and Hibernate Validator for
validation, packaged as a single fat JAR driven by one YAML configuration file that is
deserialized into a typed configuration class and validated at startup. Health checks and a
metrics endpoint on a separate admin port were built in years before that was standard.

Reach for it on an existing Dropwizard estate, or when you want a small opinionated JAX-RS
service with no auto-configuration magic and a very explicit `Application` bootstrap. The cost
is momentum: Spring Boot and Quarkus have far larger ecosystems, more integrations and more
active development, and Dropwizard's component choices are fixed in a way that makes deviating
awkward. New JVM services rarely start here.

### dynaconf 3.x
**Short:** Layered Python settings library merging files, env vars and Vault, with per-environment profiles and casting.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, security/secrets-and-cryptography @3

Its distinguishing idea is layering with environments: settings files (TOML, YAML, JSON, INI
or Python) are merged in order, each may declare per-environment sections such as `[default]`,
`[development]` and `[production]`, and variables prefixed `DYNACONF_` override anything from
a file. Additional loaders read Vault, Redis or a custom source, `@format` and `@jinja` tokens
let one value reference another, and a validator set can require variables and check them at
startup.

Reach for it when configuration genuinely is layered — several environments, a secrets
backend, per-deployment overrides — and you want that resolution to be the library's problem.
The costs are that the resolution order is powerful enough to be hard to reason about, so
"where did this value come from" becomes a real question, and that dynamic attribute access
gives no static typing. For a straightforward twelve-factor service, `pydantic-settings` is
simpler and typed.

### EIP-3009
**Short:** Ethereum token standard for signed transfer authorizations, enabling sign-now settle-later agent payment flows.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, security/secrets-and-cryptography @3

The standard adds `transferWithAuthorization` and `receiveWithAuthorization` to a token
contract. The holder signs an EIP-712 typed message naming the recipient, the amount, a
validity window and a random 32-byte nonce, and any third party can then submit that signature
on chain; the contract verifies it and moves the funds. Because the submitter pays the gas,
the holder needs no native token at all, and the random nonce means authorizations are
independent rather than strictly ordered.

Reach for it for flows where the payer signs now and someone else settles later — a metered
service collecting on completion, or an agent authorizing a payment it does not itself
broadcast. The costs are that only tokens implementing it work, so it is not universal; that a
signed authorization is bearer-like until it expires or is used, which makes its handling a
security problem; and that the validity window must be short or a stale authorization can be
replayed into a very different context.

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

The JVM initializes an enum class once, under the classloader's initialization lock, so the
single constant is created exactly once with correct publication and no `synchronized`
anywhere. Serialization is handled by name rather than by copying fields, so no `readResolve`
is needed, and `Constructor.newInstance` on an enum throws, which closes the reflection hole
every other singleton form leaves open.

Reach for it for a stateless helper or a genuinely process-wide registry in code that has no
container. The costs are that an enum cannot extend a class, that it is initialized eagerly
with its class rather than on first use, and above all that it is still global state a test
cannot substitute. Inside a DI application, a singleton-scoped bean gives you one instance and
still lets a test inject another.

### EnumSet transition tables
**Short:** State machine encoded as data in nested EnumMap/EnumSet structures instead of branching code.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/collections-and-algorithms @3

The machine becomes a nested `EnumMap<State, EnumMap<Event, State>>`, or an `EnumMap<State,
EnumSet<State>>` of legal successors. Those collections are not hash maps: `EnumMap` is an
array indexed by ordinal and `EnumSet` is a bit vector in a single `long` for up to 64
constants, so a lookup is an array access and a whole transition table costs almost nothing.

Reach for it when the transitions are something you want to print, diff, test or validate as a
whole — proving no state is unreachable becomes a traversal of the table rather than a code
review. The limit is that a table holds shape only; guards, entry and exit actions, timeouts
and side effects have nowhere to live, and once you bolt them on the table has stopped being
the whole answer. That is the point to move to a state-machine library with persistence.

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

It is a typed reader over the environment: `env.int("PORT", 5000)`, `env.bool("DEBUG",
False)`, `env.list("HOSTS")`, `env.url(...)`, `env.timedelta(...)`, each parsing and
validating with marshmallow underneath so a malformed value raises with the variable's name in
the message. `env.read_env()` pulls in a `.env` file first, and wrapping the reads in
`env.prefixed("APP_")` scopes a block of variables.

Reach for it when you want types and fail-fast validation without adopting a settings-class
framework — it is a few lines in a config module rather than a new modelling layer. The costs
are that it is imperative, so configuration is spread wherever the reads happen unless you
discipline yourself into one module, and that there is no schema object to introspect,
serialize or document. When you want a declared, typed settings model, `pydantic-settings` is
the better shape.

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

The distinguishing feature is the REPL. You `show service`, `desc` a message, `service` to
select one and then `call` a method, and Evans prompts field by field for the request, so
exploring an unfamiliar API is a conversation rather than a hand-built JSON blob. It discovers
the schema by reflection or from `.proto` files, and a headless mode runs a single call for
scripting.

Reach for it when poking at a service interactively from a terminal, especially with deeply
nested request messages where the prompting saves real effort. The costs are that the
interactive session is not a reproducible artifact and that repeated-field and oneof prompting
gets tedious for large messages. For anything you want to keep, save a `grpcurl` invocation
instead.

### ExchangeFilterFunction
**Short:** WebClient's reactive filter SPI for intercepting outbound requests to add auth, retry or logging.
**Kind:** api
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/aop-middleware-and-scheduling @2

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

The whole framework is one idea: an ordered list of middleware functions with the signature
`(req, res, next)`, where routes are middleware bound to a method and path. Anything that does
not end the response calls `next()`, and error handlers are distinguished only by arity — four
parameters means Express treats it as the error path. `Router` instances let a subtree be
mounted, and since Express 5, a rejected promise in an async handler is forwarded to the error
handler rather than crashing the process.

Reach for it as the default for a Node HTTP service: the middleware ecosystem is enormous and
every hosting platform expects it. The costs are that it ships almost nothing itself —
validation, auth, serialization and OpenAPI are all decisions you make and wire — that
ordering bugs in the middleware chain are silent, and that it has no TypeScript-first design,
so request typing is bolted on. Fastify and NestJS are the usual alternatives.

### Facade
**Short:** Structural pattern: one simplified entry point over a complicated subsystem, as SLF4J or JdbcTemplate do.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

One coarse-grained type sits in front of several finer ones, exposes the handful of
combinations that callers actually want, and does the ordering and error translation
internally. Unlike Adapter it invents a new interface rather than matching an existing one,
and unlike a wrapper it does not forbid direct access to the subsystem — the subsystem stays
public for the cases the facade does not cover.

Reach for it to give a library a default path, or to define the one public surface of a module
so the rest of the package can be treated as internal. The cost is drift: a facade that grows
a method for every caller's special case becomes a god object, and it hides the expense of
what it invokes, so a single innocuous call can turn out to be four network round trips.

### Factory Method
**Short:** GoF creational pattern: a named method owns the creation rule so callers get an instance without a concrete class.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

The creation rule lives in a named method rather than in a constructor, so the caller receives
an instance without naming a concrete class. That buys three things a constructor cannot: the
method has a name, so several creation paths coexist (`of`, `copyOf`, `parse`); it may return
a cached or shared instance instead of a fresh one; and it may return any subtype, which is
how `EnumSet.noneOf` picks a bit-vector implementation by size.

Reach for it when the concrete class is a policy decision or when construction needs a name.
The costs are that a static factory cannot be subclassed and is invisible to a DI container in
the way a constructor is not, and that the indirection hides which class you actually got.
Inside a DI application the container is already the factory, so a bare `@Bean` method usually
covers it.

### Falcon
**Short:** Minimal, high-performance Python WSGI/ASGI framework built around resource classes and responder methods.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1

Routing maps a URI template to a resource class whose methods are named for the HTTP verb —
`on_get`, `on_post` — and receive a `Request` and a mutable `Response` rather than returning a
value. There is deliberately no ORM, no templating, no dependency injection and no automatic
validation; the framework compiles the route tree ahead of time and does very little per
request, which is where its low overhead comes from. The same codebase supports WSGI and ASGI,
with async methods for the latter.

Reach for it for a high-throughput API layer or a proxy where per-request framework overhead
is a measurable part of the budget, or for a small service where the absence of magic is a
virtue. The costs are everything you now write yourself — serialization, validation, OpenAPI
documentation, auth — and a much smaller community than Flask's or FastAPI's. If the
type-driven validation and generated docs are what you want, FastAPI gives them for a modest
overhead.

### FastAPI
**Short:** Async Python web framework: type-hint routing, Pydantic validation, auto OpenAPI docs, WebSocket and streaming.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2, apis-frameworks/dependency-injection-and-config @2, apis-frameworks/rpc-graphql-and-streaming @3, inference/model-server @3

It sits on Starlette for the ASGI machinery and Pydantic for data, and the function signature is the contract: path, query and body parameters are parsed, validated and coerced from the type hints, and the OpenAPI schema plus interactive docs are generated from the same annotations. `Depends` gives you dependency injection for database sessions, authentication and per-request setup, with overrides in tests.

Reach for it for Python HTTP APIs and as the front end for a model server. The gotcha to know: a plain `def` endpoint is run in a threadpool, but blocking I/O inside an `async def` endpoint blocks the event loop and stalls every other request on that worker.

### FastAPI 0.140+
**Short:** Current FastAPI release whose Depends/Security graph and dependency_overrides give the framework its DI container.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/web-framework-and-http-client @1, security/authentication-and-identity @3

`Depends` is the whole container. A dependency is any callable, its own parameters are
resolved recursively, and the result is cached for the duration of the request, so a chain
like `get_settings` to `get_engine` to `get_session` is declared once and reused by every
route. A dependency written as a generator yields the resource and resumes after the response
for teardown. `Security` layers OAuth2 scopes on top, and dependencies declared on a router or
on the app apply to everything beneath them.

The feature that makes it testable is `app.dependency_overrides`: a test replaces a dependency
by using the function object itself as the key, so a real database session becomes a
transactional one without touching application code. The limits are that scopes are
per-request only — anything longer-lived belongs in the `lifespan` handler — and that a heavy
dependency graph runs on every request, so expensive setup must be hoisted rather than
injected.

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

Airflow 3 replaced the Flask-AppBuilder webserver with a FastAPI application that serves both
the React UI's REST API and, importantly, a separate Task Execution API. That second surface
is the architectural change: workers no longer connect to the metadata database directly, they
call the API server to fetch task context, heartbeat, report state and read connections and
variables, so task code runs without database credentials and can run outside the cluster.

The consequence for operators is that the API server moves into the critical path of task
execution rather than being a UI convenience — it must be sized, monitored and made highly
available, because if it is down, running tasks cannot report their state. The upside is a
genuine security boundary around the metadata database and a documented, versioned API for
everything the UI and the workers do.

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

It supplies what graphql-java needs to satisfy the federation contract: SDL transformation
that adds the federation types, a `_service` field returning the subgraph's schema, and an
`_entities` data fetcher that dispatches a batch of entity representations to per-type
resolvers you register. Spring for GraphQL and Netflix DGS both build their federation support
on it rather than reimplementing the spec.

Reach for it when a JVM service must be a subgraph behind an Apollo Router or a comparable
gateway. The costs are mostly operational: entity resolvers receive representations rather
than typed arguments, so they must be defensive; they are called in batches and are the
natural home of an N+1 problem unless you add a dataloader; and the subgraph SDL must now pass
composition in CI, which makes a schema change a cross-team event rather than a local one.

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

Flask is a WSGI micro-framework: routes are declared with the `@app.route` decorator, request state is reached through thread-local proxies, and everything beyond routing and templating — validation, serialization, auth, database access — is an extension you add. Being WSGI means one request occupies one worker thread or process for its whole life, so you size a `gunicorn`/`uWSGI` worker pool rather than relying on an event loop, and a slow upstream call ties up a worker. Reach for it for a small internal service, a webhook receiver, or a quick endpoint wrapped around a model; choose FastAPI instead when you want async I/O, request/response validation from type hints, and generated OpenAPI docs without assembling them yourself.

### Flyweight
**Short:** GoF structural pattern that shares immutable intrinsic state across many objects to cut heap duplication.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/memory-processes-and-os @3

State is split in two: the intrinsic part that is identical across many objects is stored once
in a shared immutable instance handed out by a pool, and the extrinsic part that varies is
passed in as a parameter at each use. `Integer.valueOf` caching minus 128 through 127 and
string interning are the everyday examples — the same object is returned repeatedly rather
than allocated.

Reach for it when object count is enormous and most of the state repeats: glyphs, tokens, map
tiles, column dictionaries. The costs bite in two places. The pool is a lifetime problem, so
an unbounded one is a leak. And identity comparison starts to appear to work — `==` on two
cached `Integer` values is true until a value leaves the cached range, which is exactly the
class of bug that survives every test. A record plus an explicit bounded cache is usually
clearer.

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

### GraphiQL
**Short:** In-browser GraphQL IDE for exploring a schema, autocompleting and running queries against any GraphQL endpoint.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

It loads the schema by introspection and gives you autocompletion, a documentation pane, a variables and headers editor, and a run button, so exploring an unfamiliar GraphQL API needs no client code. Most GraphQL servers, including Spring for GraphQL, can serve it at a path in development.

Reach for it while designing or learning a schema and for reproducing a query a client reported. Introspection is normally disabled in production for the same reason you would not expose a schema browser, so treat it as a development and staging tool.

### GraphQL Federation spec
**Short:** Specification for composing many GraphQL subgraphs into one supergraph schema served by a federated gateway.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2, traffic-edge/api-gateway @3

The specification defines the directives and the runtime contract that make composition
possible. `@key` declares the fields that identify an entity so any subgraph can reference it,
`@external`, `@requires` and `@provides` describe fields borrowed from elsewhere, and every
subgraph must expose two things: `_service` returning its SDL and `_entities`, which resolves
a list of entity representations back into objects. That second field is the whole mechanism —
the gateway fetches an entity's key from one subgraph and hands it to another to fill in the
rest.

Reach for it when several teams must own slices of one graph and you want composition checked
at build time rather than discovered in production. The costs are a query planner in the
request path, entity resolution turning one client query into several sequential subgraph
fetches, and a composition step that becomes a shared release gate. For a single team, one
schema is far simpler.

### GraphQL Playground
**Short:** Browser IDE for GraphQL: schema docs, autocomplete and an interactive query console.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

It was the fork of GraphiQL that most GraphQL servers embedded for a while, adding tabs, a
persisted history, editable HTTP headers and a dark theme, all driven by an introspection
query against the endpoint. It is no longer maintained: the project was retired and its
improvements were folded back into GraphiQL 2, with Apollo Sandbox as the other successor.

Treat it as a legacy dependency to remove rather than something to add. If you find it wired
into a server, the replacement is GraphiQL or Apollo Sandbox, both of which do the same job
with current dependencies. The reason to care beyond tidiness is that any embedded IDE
requires introspection to be enabled on the endpoint it points at, which is not something you
want left switched on in production.

### graphql-java
**Short:** The reference GraphQL execution engine for the JVM; parses, validates and executes queries against a schema.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

It does the language-level work: parse a query into a document, validate it against the schema, then execute field by field, invoking the `DataFetcher` registered for each field and assembling the result along with any errors. Everything above that — HTTP transport, dependency injection, subscription plumbing — is deliberately left to a framework, which is why it is nearly always used through Spring for GraphQL or DGS rather than on its own.

Its `DataLoader` support is the piece worth knowing about, since it batches and per-request caches the fetches a nested field would otherwise issue one at a time. Reach for the raw library only when you are building that framework layer yourself or embedding GraphQL somewhere unusual.

### gRPC
**Short:** HTTP/2 RPC framework using protobuf contracts, with generated stubs and unary plus bidirectional streaming.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @3

The contract is a `.proto` service definition, and `protoc` generates both the client stub and
the server base class from it, so the wire format, the field numbering and the method
signatures cannot drift between the two sides. Every call is an HTTP/2 stream, which is what
makes the four call types — unary, server-streaming, client-streaming and bidirectional — the
same mechanism at different arities, and lets many concurrent calls share one connection
without head-of-line blocking. Deadlines propagate across hops as metadata, and cancellation
travels with them.

Reach for it for internal service-to-service traffic where you control both ends and want
typed contracts, streaming and low overhead. The costs are that browsers cannot speak it
natively, that a binary body is unreadable to every ordinary HTTP tool without reflection or a
descriptor, and that HTTP/2 connection reuse defeats connection-level load balancers, so you
need an L7 proxy or client-side balancing. For a public API, REST with OpenAPI remains the
lower-friction choice.

### grpc health checking protocol
**Short:** Standard gRPC health service (Check/Watch) that balancers, meshes and K8s probes use for readiness.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/service-mesh-and-discovery @2, observability/metrics-and-monitoring @3

It is a normal gRPC service, `grpc.health.v1.Health`, with two methods: `Check` returns
`SERVING`, `NOT_SERVING` or `SERVICE_UNKNOWN` for a named service, and `Watch` streams status
changes so a client learns about a transition without polling. The empty service name is the
convention for the server's overall status, and a per-service name lets one process report
that one of its services is degraded while the rest are fine.

Reach for it because the whole ecosystem already does — Kubernetes probes, Envoy and gRPC's
own client-side health checking all speak it, so implementing it is what makes a service
first-class in a mesh. The trap is what the status actually means: a hand-written
implementation that always returns `SERVING` is worse than none, because it tells the
orchestrator to keep routing traffic to a process that cannot reach its database.

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

### grpc-client-spring-boot-starter
**Short:** Community Spring Boot starter autoconfiguring gRPC client channels via @GrpcClient and grpc.client.* properties.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @2

This is the community project from `net.devh`, which predates Spring's own gRPC support. It
turns channel creation into configuration: `@GrpcClient("orders")` on a stub field injects a
stub built from a `ManagedChannel` configured by `grpc.client.orders.*` properties — address,
negotiation type, deadline, keep-alive — and the channel's lifecycle follows the application
context. Client interceptors registered as beans apply globally, which is where tracing and
metrics attach.

Reach for it in an existing Boot application already using this family. The cost is that it is
a third-party project whose release cadence is independent of Spring Boot, so a Boot upgrade
can outrun it, and the property namespace differs from the official Spring gRPC project. For
something new, prefer the official `spring-grpc` starters and keep the migration in mind if
you inherit this one.

### grpc-gateway
**Short:** protoc plugin generating a reverse proxy that transcodes REST/JSON requests into gRPC calls.
**Kind:** tech
**Lang:** go
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, traffic-edge/api-gateway @2

It is a `protoc` plugin, not a runtime library: it reads `google.api.http` annotations on your
RPC methods and generates Go source for a reverse proxy that accepts REST-shaped requests,
maps path and query parameters onto the request message, converts JSON to protobuf, calls the
gRPC service and converts the response back. A companion plugin emits an OpenAPI document from
the same annotations, so the REST facade is documented from one source.

Reach for it when one service must serve both a gRPC mesh and browser or third-party HTTP
clients without maintaining two handlers. The costs are that the mapping annotations are extra
proto surface to keep correct, that streaming translates poorly — server streams become
chunked JSON and client streaming has no natural REST form — and that you have added a hop.
Envoy's gRPC-JSON transcoder does the same job in the proxy with no generated code, if you
already run Envoy.

### grpc-go
**Short:** The Go implementation of gRPC: generated stubs, interceptors and streaming over HTTP/2.
**Kind:** tech
**Lang:** go
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

Idiomatic Go throughout: every generated method takes a `context.Context` as its first
argument, so deadlines and cancellation are the standard Go mechanism rather than a gRPC
concept, and a cancelled context propagates to the server as a `CANCELLED` status. `grpc.Dial`
returns a `ClientConn` that is safe for concurrent use and reconnects on its own, and
interceptors — unary and stream, client and server — are where logging, auth and metrics
attach.

Reach for it for any Go service in a gRPC mesh; it is the reference implementation and the one
where new features land first. The costs are that the generated code has churned (the
separation of `protoc-gen-go` from `protoc-gen-go-grpc` still catches people), that streaming
handlers must respect context cancellation or they leak goroutines, and that a single
`ClientConn` pinned to one HTTP/2 connection needs a resolver and balancer configured to
spread load across backends.

### grpc-health-probe
**Short:** Standalone binary calling the gRPC Health Checking service, for exec-based liveness probes outside native support.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, platform-delivery/kubernetes-and-orchestration @2, observability/metrics-and-monitoring @3

It exists to solve a container-orchestration problem: an exec probe must be a binary inside
the image, and `curl` cannot speak gRPC. The probe dials the address given by `-addr`, calls
`Check`, and maps the response to a process exit code, with flags for TLS, a service name and
a connection timeout, so a liveness or readiness probe becomes a single command line.

It is largely legacy now, because Kubernetes gained a native `grpc` probe field that performs
the same check from the kubelet without shipping a binary in the image — prefer that where the
cluster supports it. Keep the probe for older clusters, for non-Kubernetes runtimes, or when
you need a flag the native probe does not expose. Either way, remember that adding it to the
image also adds a gRPC client to your attack surface.

### grpc-java
**Short:** The JVM implementation of gRPC: generated stubs, HTTP/2 transport, streaming calls, interceptors and deadlines.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

The runtime is split across artifacts on purpose: `grpc-api` and `grpc-core` hold the call
abstractions, `grpc-stub` the generated stub base classes, `grpc-protobuf` the marshalling,
and a transport artifact supplies the actual HTTP/2 plumbing. A `ManagedChannel` is the
expensive, thread-safe, long-lived object that owns connections and name resolution; stubs are
cheap immutable wrappers around it that you re-derive per call to attach a deadline or
credentials.

Reach for it for any JVM gRPC endpoint. The operational costs to plan for are that a channel
must be shut down explicitly or the process will not exit, that a missing deadline means a
call can hang until the transport notices, and that server handlers run on a shared executor
you should size and isolate yourself. Inside Spring Boot, a starter that autoconfigures the
channel and server saves most of this boilerplate.

### grpc-node
**Short:** The Node.js gRPC implementation: protobuf service stubs, unary and streaming calls, interceptors and deadlines.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

Two implementations coexist. `@grpc/grpc-js` is pure JavaScript with no native build step and
is the maintained one; the older `grpc` native addon binds the C-core library and is
deprecated. With `@grpc/proto-loader` a `.proto` is parsed at runtime into a service
definition, which avoids a codegen step but also gives up the compile-time typing that
generated TypeScript stubs provide.

Reach for it when a Node service must join a gRPC mesh. The costs to plan for are that all the
usual Node rules still apply — a synchronous handler blocks the event loop and therefore every
concurrent stream on that connection — and that runtime proto loading means a schema mistake
surfaces at first call rather than at build. Generate TypeScript definitions if the service is
more than a few methods.

### grpc-protobuf
**Short:** gRPC Java runtime artifact providing protobuf message marshalling for generated stubs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/data-formats-and-api-contracts @2

It supplies the `Marshaller` implementations that turn generated protobuf messages into the
byte streams the transport writes, along with the `ProtoUtils` helpers and the status-detail
types that let a server attach a structured `google.rpc.Status` payload to an error rather
than just a code and a string. It is a compile and runtime dependency of anything generated by
`protoc-gen-grpc-java`, not something you call directly.

The reason to know it exists is version alignment: it pins a `protobuf-java` version, and a
different version dragged in by another dependency produces `NoSuchMethodError` at runtime
rather than a build failure. Use the `grpc-bom` and the protobuf BOM so every gRPC artifact
and the protobuf runtime move together. For JSON payloads instead of protobuf there is a
separate marshaller artifact.

### grpc-server-spring-boot-starter
**Short:** Community Spring Boot starter auto-configuring a gRPC server: @GrpcService beans and grpc.server.* properties.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @2

The `net.devh` server half. Any bean annotated `@GrpcService` is registered with an embedded
gRPC server that starts with the application context on its own port, configured through
`grpc.server.*` — port, security, max message size, keep-alive. Because handlers are ordinary
Spring beans they get constructor injection, `@Transactional` and the rest, and global server
interceptor beans give you one place for authentication, exception translation and metrics.

The details that matter in production are that the gRPC port is separate from the servlet
port, so probes, firewall rules and service definitions need both; that a handler exception
which is not translated reaches the client as an opaque `UNKNOWN`, so a global exception
advice is not optional; and that the handler executor is shared and worth sizing. As above,
new projects should prefer the official Spring gRPC starters.

### grpc-stub
**Short:** grpc-java runtime artifact providing the generated blocking, async and future client stubs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

`protoc-gen-grpc-java` emits three stub flavours per service and they all extend
`AbstractStub` from this artifact: a blocking stub whose unary calls look like ordinary method
calls, a future stub returning `ListenableFuture`, and an async stub driven by
`StreamObserver` callbacks. Only the async stub can do client-streaming or bidirectional
calls; the blocking stub supports unary and server-streaming only.

Stubs are immutable and cheap, and the `withDeadlineAfter`, `withCallCredentials` and
`withInterceptors` methods return a new stub rather than mutating one — which is exactly why
setting a deadline and discarding the result is such a common bug. Derive a per-call stub from
a long-lived channel; do not cache a stub with an absolute deadline baked in, because
deadlines are absolute instants and a reused stub will start failing immediately once that
instant passes.

### gRPC-Web
**Short:** Browser-compatible gRPC wire variant over HTTP/1.1 or HTTP/2, usually terminated by an Envoy translating proxy.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

Browsers cannot speak gRPC because no browser API exposes the HTTP/2 frame-level control that
trailers and streaming require. gRPC-Web changes the framing: the payload is length-prefixed
inside an ordinary request body and the trailers are appended as a final message chunk, so it
survives an HTTP/1.1 hop and `XMLHttpRequest` or `fetch`. Something must translate — the Envoy
`grpc_web` filter is the usual choice, or a native handler in the server.

Reach for it when a browser front end must call an existing gRPC backend and you do not want a
hand-written REST facade. The limits are that client-streaming and bidirectional calls are not
supported at all, that server streaming works but buffering proxies can break it, and that
CORS plus the exposure of `grpc-status` and `grpc-message` headers is a recurring source of
silent failures. Connect's protocol is the modern alternative that avoids the proxy.

### grpcio
**Short:** The Python gRPC runtime: generated stubs, channels, interceptors and streaming over HTTP/2.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

The package ships a C extension wrapping gRPC's core library, so the event loop and HTTP/2
framing live in C and Python only sees callbacks. The synchronous server runs handlers on a
`ThreadPoolExecutor` you supply, and its size is the real concurrency limit — the default in
most examples is small enough to become a bottleneck under load. `grpcio.aio` provides an
asyncio-native client and server for code already built around coroutines.

Reach for it for any Python service in a gRPC mesh. The costs are that generated stubs come
from `grpcio-tools` and land as `_pb2.py` and `_pb2_grpc.py` files whose imports are famously
awkward inside packages, that the GIL still applies so CPU-bound handlers need processes
rather than threads, and that mixing the sync and asyncio APIs in one process does not work.
Pin `grpcio` and `protobuf` versions together.

### grpcui
**Short:** Web UI for exploring and calling gRPC services via reflection; the gRPC analogue of a REST client.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/version-control-and-workbench @3

It starts a local web server that fetches the service catalogue by reflection, or from a
descriptor set you pass, and renders a form per method: fields become inputs typed by the
protobuf schema, so you fill in a request without writing JSON and see the response, the
status code and the metadata rendered structurally. Streaming methods are supported, with
successive messages shown as they arrive.

Reach for it when handing a service to someone who does not want to learn the schema — QA, a
front-end developer, a support engineer — since the form is self-documenting in a way a
command line is not. The costs are that it needs reflection enabled or a descriptor file on
hand, and that it is an interactive tool with nothing to commit: for anything repeatable,
`grpcurl` in a script is the better artifact.

### grpcurl
**Short:** Command-line gRPC client; calls services from a .proto file or server reflection, the curl of gRPC.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

It calls a gRPC service from the shell: `list` enumerates services, `describe` prints a method's message types, and `-d` sends a request body as JSON which grpcurl converts to protobuf and back. It gets the schema either from the server's reflection service or, when reflection is disabled, from `.proto` files or a compiled descriptor set passed with `-proto` or `-protoset` -- which is the first thing to check when it cannot find your method. Because gRPC is binary over HTTP/2, curl is not a usable substitute, so this is the tool for confirming a service is up, reproducing a bad request, or checking TLS and metadata handling from outside the application. Reach for it during development and incident debugging, and enable reflection in non-production environments to make it far more pleasant to use.

### Gson
**Short:** Google's JSON binding library for Java: simpler and Android-friendly, with no default-typing RCE surface.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

Binding is reflective and needs no annotations for the common case: `new Gson().fromJson(json,
Foo.class)` walks the fields directly, which also means it can construct objects with no
no-arg constructor by using `Unsafe`. Generic types are erased, so a `List<Foo>` requires a
`TypeToken` to carry the parameterization, and `@SerializedName` handles name mismatches.
Notably it has no polymorphic default-typing feature, which is why it never had Jackson's
family of deserialization gadget vulnerabilities.

Reach for it on Android or wherever a small dependency and zero configuration matter more than
throughput and features. The costs are that it is in maintenance mode with a much smaller
feature set — no streaming databind ergonomics comparable to Jackson's, fewer format modules,
weaker Kotlin and record support — and that fields absent from the JSON are silently left null
rather than reported. For a server on the JVM, Jackson is the default.

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

`register(obj)` reflects over an object's `@Subscribe` methods to build a type-to-handler map, and `post(event)` dispatches to every handler whose parameter type the event is assignable to, supertypes and interfaces included. Delivery happens on the posting thread unless you use `AsyncEventBus` with an `Executor`, and an event nobody subscribed to comes back as a `DeadEvent` — the cheapest way to catch a collaborator you forgot to wire up.

It decouples publishers from subscribers inside one JVM, which makes it a practical Mediator or Observer implementation outside Spring. It is in-memory with no durability across a restart, and a handler that throws is swallowed by the exception handler rather than failing the poster, so treat it as a notification mechanism and not a broker.

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

A master process forks a fixed number of worker processes, hands each the listening socket, restarts any that die or hang past the timeout, and handles graceful reload and shutdown. For an ASGI app such as FastAPI you set a Uvicorn worker class so each worker runs its own event loop, which is how one container uses more than one CPU core.

The consequence people trip over is that workers are separate processes with separate memory: an in-process cache, a rate-limit counter or a scheduler running in each worker is duplicated N times, not shared. Reach for it when you want multiple cores and a supervisor inside one container; if your platform already scales by replicas, a single Uvicorn process per container is simpler to reason about.

### gzip
**Short:** Ubiquitous DEFLATE (LZ77 + Huffman) compressor, used for files and as an HTTP content encoding.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/collections-and-algorithms @2, caching/http-and-cdn-cache @3

The algorithm is DEFLATE: LZ77 replaces repeated byte sequences with distance-length back
references over a 32 KB window, and Huffman coding then entropy-codes the result. The gzip
wrapper adds a header with an optional filename and timestamp and a trailing CRC-32 and
length. Compression levels 1 to 9 trade CPU against ratio, and the practical point is that the
low levels are far cheaper than the high ones for a fairly small loss on text.

Reach for it as the safe default for HTTP content encoding and for log and backup files, since
every client, proxy and library on earth supports it. The costs are that it is a single
stream, so you cannot seek into a file or parallelize decompression, that it does poorly on
already- compressed data like images, and that compressing a response containing a secret
alongside attacker-influenced input is what makes the BREACH class of attacks possible. For
better ratios on text, Brotli; for higher throughput, Zstandard.

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

There are two ways to start an HTTP/2 connection without TLS. Prior knowledge is the one that
matters: the client simply opens a TCP connection and sends the HTTP/2 connection preface,
which requires both ends to have agreed out of band that this port speaks HTTP/2. The other
was an HTTP/1.1 `Upgrade: h2c` handshake, which was never widely implemented and has since
been dropped from the specification. No browser has ever supported cleartext HTTP/2, because
ALPN over TLS is how they negotiate it.

Reach for it on internal hops where TLS is terminated at an ingress or handled by a service
mesh sidecar, and for local testing where certificates are noise — gRPC's plaintext mode is
exactly this. The costs are that a client must be told explicitly to use it, since it cannot
be discovered, and that plaintext inside the cluster is a security decision: without mTLS
anywhere in the path, the traffic is readable by anything on the network.

### hal-explorer
**Short:** Browsable web UI for navigating HAL hypermedia APIs by following their link relations.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/web-framework-and-http-client @3

It renders a HAL or HAL-FORMS response as a browsable page: links become clickable so
following a relation is a click rather than a hand-built URL, embedded resources are
expandable, and where `_templates` are present it generates a form for POST and PUT so
state-changing calls can be exercised too. It is served as static resources from the classpath
and needs no configuration beyond the dependency in a Spring HATEOAS application.

Reach for it while developing or demonstrating a hypermedia API, because it is the fastest way
to prove that the links actually work and are actually followed. The costs are the usual ones
for an embedded developer tool: it exposes the whole API surface wherever it is deployed, so
gate it by profile, and it is a debugging aid rather than documentation — for a reference a
consumer reads, you still want a published specification.

### HAL-FORMS
**Short:** Media-type extension to HAL adding _templates, so a hypermedia response can describe an action's input fields.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

Plain HAL tells a client where it can go but not what it can send: a link has an href and a
relation, and nothing describes the body of a POST. HAL-FORMS adds a `_templates` object,
whose `default` entry names the HTTP method, the content type, and a `properties` array giving
each input's name, whether it is required, a regex, a prompt and a value. A client can
therefore render a form, or validate a request, from the response itself.

Reach for it when the point of hypermedia is that the server drives what the client may do
next — an approval that is only available in certain states, for instance. The costs are that
generic client support is thin, so in practice you are writing the consumer as well; that it
duplicates information an OpenAPI document may already carry; and that per-response templates
add payload size to every representation.

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

It introspects the database catalogue and generates a GraphQL schema from it: tables become
types, foreign keys become nested fields, and queries are compiled into a single SQL statement
rather than resolved field by field, which is why a deeply nested query does not become N+1.
Authorization is declarative — per role, per table, per operation, with row filters expressed
as boolean expressions over session variables — and subscriptions are implemented by
multiplexed polling of that same compiled query.

Reach for it to put a governed API over an existing database quickly, with custom business
logic attached as Actions calling your own services and Event Triggers firing on writes. The
costs are that the API mirrors your schema, so the database becomes the public contract and
refactoring it becomes a breaking change; that permission rules become an intricate second
system to review; and that subscription polling has a real cost at high connection counts.

### Helmet.js
**Short:** Express middleware that sets defensive HTTP headers - CSP, HSTS, X-Frame-Options - on every response.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, security/supply-chain-and-runtime-security @2

It is a collection of small middleware functions, each setting one response header, bundled
behind a single call. Out of the box that means `Content-Security-Policy` with a restrictive
default, `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`,
`X-Frame-Options` and removing the `X-Powered-By` header that advertises Express. Each piece
can be configured or switched off individually.

Reach for it on any Express application serving HTML, because these headers are pure defence
in depth and the defaults are sensible. The costs are all about the CSP: the default policy
breaks inline scripts and styles and third-party assets, so applications that hit trouble tend
to disable CSP entirely rather than write a policy, which throws away the most valuable header
in the set. Headers also do nothing for injection, authorization or dependency vulnerabilities
— they are one layer, not a security posture.

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

It is the reference implementation, and the parts it adds beyond the specification are what
most projects actually rely on: a large set of extra constraints, a fail-fast mode that stops
at the first violation instead of collecting all of them, and programmatic constraint
declaration for cases where annotating the class is not possible. Custom rules mean writing a
`ConstraintValidator` and pairing it with an annotation; cross-field rules go on the class
rather than a field.

Reach for it because in practice you already have — it is what the Spring Boot validation
starter brings in. The costs are that it needs an Expression Language implementation on the
classpath for message interpolation, which is a recurring startup failure in trimmed
deployments, and that interpolation itself is a security boundary: never build a message
template from user input, and use `ConstraintValidatorContext` to add a parameterized
violation instead.

### HTTP
**Short:** The request/response application protocol of the web: methods, status codes, headers and caching semantics.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/io-networking-and-syscalls @2

Everything follows from it being a stateless request-response protocol: each request carries
everything needed to serve it, which is what allows any request to go to any server and makes
horizontal scaling and caching possible at all. The semantics that matter are the method
properties — `GET` and `HEAD` safe, `PUT` and `DELETE` idempotent so a retry is harmless,
`POST` neither — and the caching headers, where `ETag` with `If-None-Match` gives conditional
requests and `Cache-Control` decides what any intermediary may store. The versions changed the
transport, not these semantics: HTTP/2 multiplexes streams over one TCP connection, HTTP/3
moves to QUIC over UDP to remove TCP head-of-line blocking.

Design to the semantics rather than around them, because proxies, CDNs and client libraries
act on them whether or not you meant them. Most production surprises are exactly this: a `GET`
with side effects being retried or prefetched, or a response cached because it did not say not
to be.

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

The point of `httpie` is that the common case is short: `http POST api.example.com/users name=ada role=admin` builds a JSON body, sets the content type, and pretty-prints and colorizes the response, where the curl equivalent needs several flags and a hand-written body. `--verbose` prints the full request alongside the response so you can see exactly which headers went out, and sessions persist cookies and auth between invocations. Reach for it when exploring or debugging an API by hand; keep `curl` for scripts and containers, since it is present everywhere and its flags are what every runbook already assumes.

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

The module-level API mirrors `requests`, but the piece that matters is using `Client` or `AsyncClient` as a long-lived object: it owns a connection pool, so reusing one across requests avoids a TCP and TLS handshake per call, and it is where you set base URL, headers, limits and timeouts once. HTTP/2 comes with the optional extra, `client.stream(...)` yields a response incrementally rather than buffering it, and an ASGI transport lets tests drive a FastAPI application in-process with no server and no port.

Two habits prevent most production incidents with it: set an explicit `timeout` on every call or on the client, because a hung upstream otherwise ties up a connection and a task indefinitely, and set `follow_redirects=False` when the URL came from a user, since redirect-following is a standard SSRF vector.

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

`httpx` defines an `Auth` interface whose implementation can inspect the request, add headers
and even issue its own request before the real one; this library supplies ready-made
implementations of that interface for the flows people otherwise hand-roll — OAuth2 client
credentials, authorization code with PKCE, various header and query API-key schemes, and AWS
SigV4 request signing. Token caching and refresh happen inside the auth object, so callers
just pass `auth=` and forget it.

Reach for it rather than writing token refresh yourself, since the failure mode of a homegrown
implementation is a thundering herd of refreshes or a token that expires mid-retry. The costs
are an extra dependency for something a few lines can approximate, per-flow configuration that
still requires understanding the provider's quirks, and a token cache that lives in the
process — across many workers, each refreshes independently.

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

It implements ASGI (and WSGI) and can run its workers on asyncio, uvloop or trio, and it terminates HTTP/2 and HTTP/3 over QUIC directly, with TLS configured at the server. That is the reason to pick it: those protocols end to end, or a trio-based application that uvicorn will not host.

Reach for it in those two cases. For an ordinary HTTP/1.1 service behind nginx, an ingress or a cloud load balancer -- which already terminates HTTP/2 for you -- uvicorn is the more common default and usually a little faster.

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

The instance is a `static final` field of a private nested class. The JVM does not initialize
a nested class until something first references it, so the first call to `getInstance()`
triggers initialization, and the language specification guarantees that runs exactly once
under the class initialization lock with correct publication to every thread. No
`synchronized`, no `volatile`, and none of the ways double-checked locking is written wrong.

Reach for it when construction is expensive and may never be needed, in code with no container
to lean on. The limits are structural: there is nowhere to pass a parameter, so the
constructor must take none, and an exception during initialization is permanent — the first
caller sees `ExceptionInInitializerError` and every later caller gets `NoClassDefFoundError`
with the original cause gone. Anything parameterized or replaceable belongs to a DI container.

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

Each production of the grammar becomes a class with an `interpret(context)` method, and a
parsed expression is a tree of those objects evaluated by recursive descent through the
structure. Composite supplies the tree shape and the context carries variable bindings, so
adding an operator means adding a class rather than editing a switch.

Reach for it only when the grammar is genuinely tiny and stable — a filter expression, a retry
condition — and you want it in plain code with no dependency. The costs arrive quickly: one
class per production, and re-walking the tree on every evaluation is slow enough to matter in
a hot loop. Past a page of grammar, a parser generator such as ANTLR, or an existing
expression engine like SpEL, MVEL, JEXL or CEL, is a better trade.

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

gRPC Java does not implement HTTP/2 itself; a transport artifact does, and this is the Netty
one, handling framing, flow control and TLS through Netty's `SslContext` — which on the JVM
usually means adding `netty-tcnative-boringssl-static` for ALPN and decent throughput. The
shaded artifact relocates Netty and its native bits into gRPC's own package space so it cannot
collide with a different Netty version already on the classpath.

Choose the shaded one in an application, which is the common case and why it is what the
starters pull in; choose the unshaded one when you deliberately share a Netty version and
event loop with the rest of the process, for example alongside Reactor Netty, and are prepared
to manage the version yourself. The third option is `grpc-okhttp`, a lighter client-side
transport used on Android where Netty is too heavy.

### io.rsocket:rsocket-core
**Short:** The Java implementation of RSocket, a bidirectional reactive protocol with request-stream and channel modes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

RSocket defines four interaction models over one connection — fire-and-forget,
request-response, request-stream and request-channel — and both peers may initiate any of
them, so there is no client and server role at the protocol level. Its distinguishing feature
is that Reactive Streams backpressure is in the wire protocol: a receiver sends `REQUEST_N`
frames granting credit, so a slow consumer throttles the producer end to end rather than
buffering. Session resumption can survive a dropped connection, and leases let a responder
advertise capacity.

Reach for it for long-lived bidirectional streaming between services, or from a browser over
WebSocket, where credit-based flow control genuinely matters. The costs are ecosystem size:
far fewer proxies, gateways and observability tools understand RSocket frames than understand
HTTP/2, so debugging tooling is thin and every hop must be RSocket-aware. For ordinary
request-response, gRPC is the better-supported choice.

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

The iterator holds the cursor so the collection does not have to, which is what lets several
traversals run over one structure at once and lets a linked list, a tree and an array all be
walked by the same loop. Java's for-each is `Iterable` desugared, and the standard collections
are fail-fast: they compare a `modCount` on each step and throw
`ConcurrentModificationException` when the collection changed underneath.

You rarely implement one by hand any more — `Iterable`, `Stream` and `Spliterator`, which adds
splitting for parallel traversal, cover almost everything. The traps worth knowing are that
fail-fast is best effort and not a concurrency guarantee, that an iterator over a lazy or
remote source holds that resource open until exhausted, and that removing during iteration is
only legal through the iterator's own `remove`.

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

Three layers stack on each other: a streaming layer with `JsonParser` and `JsonGenerator`
emitting tokens, a tree model of `JsonNode`, and databind, where `ObjectMapper` maps tokens
onto POJOs using reflection plus generated accessors. Nearly every behaviour is a configurable
feature or a pluggable module, which is how one library covers records, Kotlin, `java.time`,
XML, YAML, CBOR and Smile through the same API.

Reach for it as the default on the JVM; Spring Boot already configures it for you. The costs
to know are that `ObjectMapper` is expensive to construct and must be shared — creating one
per request is a classic performance bug, though it is thread-safe once configured — and that
polymorphic deserialization is where the security incidents live: never enable default typing
on untrusted input, and prefer explicit `@JsonSubTypes` over a class name on the wire.

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

EL is a small expression grammar evaluated at runtime against a resolver chain: `${...}` is
evaluated immediately, `#{...}` is deferred so the expression object can be evaluated later or
written back to, and an `ELResolver` decides how a name maps to a bean, a map entry, a list
index or a method. Frameworks embed it wherever a string in configuration or a template must
be evaluated against application state, including Bean Validation's message interpolation.

The important consequence is security rather than syntax. An EL expression can call methods on
whatever the resolver exposes, so any path that lets user input reach a template is an
injection vector — the recurring real-world case is a validation message built from a
submitted value. Keep templates static and pass user data as parameters, and note that an EL
implementation must be on the classpath at runtime or the feature that quietly depends on it
will fail at startup.

### Jakarta Validation 3.1
**Short:** Jakarta EE constraint-annotation specification: @NotNull, @Size and friends plus the validator SPI.
**Kind:** spec
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

The specification defines constraint annotations, the `Validator` and `ValidatorFactory` API,
and the `ConstraintValidator` interface implementers plug into; it deliberately provides no
implementation of its own. The parts that matter beyond `@NotNull` are cascading with
`@Valid`, which walks into nested objects and collections, validation groups so the same class
can have different rules for create and update, and method validation, which lets a container
validate parameters and return values on any bean method rather than only on a DTO's fields.

Reach for it as the standard way to express constraints, since Spring, Jakarta EE and most JPA
providers integrate with it. The costs are that it is an API only — Hibernate Validator
supplies the behaviour — and that constraint messages are interpolated through Expression
Language, so placing user-supplied text into a message template is an injection risk rather
than a display nicety.

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

Declaring the method `abstract` on the enum forces every constant to supply a body, and the
compiler rejects a new constant that forgets one — each constant is compiled as an anonymous
subclass, so the call is ordinary virtual dispatch. The behaviour and its name therefore live
in one place, and the set of behaviours is closed and enumerable via `values()`.

Reach for it for a small fixed family of operations: arithmetic operators, retry policies,
payment methods, units of measure. The structural limit is dependencies — constants are built
during class initialization, so a constant cannot receive an injected collaborator and
anything it needs must arrive as a method argument. When the strategies need wiring or must be
extensible by another module, an interface plus a container-populated map is the right shape.

### Java enum with per-constant method bodies
**Short:** State-machine idiom: each enum constant overrides the transition method, making the state set exhaustive.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

The state is the constant and the transition function is its body: `next(Event)` returns the
successor constant, so the whole machine reads one state at a time and an illegal transition
is an explicit throw rather than a fall-through to a default. Because the constant set is
closed, a `switch` over states can be checked for exhaustiveness, so adding a state surfaces
every place that must handle it.

Reach for it for short-lived machines with a handful of states — an order lifecycle, a
connection state, a retry ladder. It runs out when the machine needs anything around the
transition: guards, entry and exit actions, timers, nested regions and history have nowhere
natural to live, and persisting the current state across a restart means storing a name and
trusting nothing renamed it. Those requirements are the signal to move to a state-machine
framework.

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

Jetty was designed to be embedded rather than deployed into, which is why its API is a set of
components you assemble — a `Server`, connectors, a handler chain and a `QueuedThreadPool` —
and why it has long been the lighter-footprint alternative in Boot. Jetty 12 restructured
around environments, so one server can run applications built against different Jakarta EE
servlet versions side by side, and it has strong HTTP/2 and WebSocket support.

Choose it over Tomcat when you want a smaller memory footprint, need its particular protocol
support, or are embedding a server inside something that is not a web application. The costs
are ecosystem gravity: Tomcat is the default everyone tests against, so operational guides,
tuning advice and third-party integrations assume it, and a Jetty-specific configuration
problem has fewer answers. Both share the fundamental limitation of a bounded worker pool
unless virtual threads are enabled.

### JMESPath
**Short:** Query language for extracting and reshaping values from JSON documents with a user-supplied selector expression.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/text-encoding-and-regex @3

Unlike a path language that only selects, JMESPath composes: projections apply an expression
to every element of a list, pipes feed one expression's result into the next, and multiselect
hashes build a brand new object shape — `services[*].{name: name, port: ports[0]}` returns a
reshaped list rather than a slice of the original. It is a proper specification with a
compliance test suite, so implementations agree.

Reach for it when the consumer needs the data reshaped, not merely extracted, and especially
inside AWS where `--query` on every CLI command speaks it and Step Functions uses it for state
transformation. The costs are that projection semantics take real practice — an expression
that silently returns an empty list because a projection stopped early is the classic
confusion — and that outside the AWS world tooling support is thinner than JSONPath's.

### jMolecules
**Short:** Annotation library that expresses DDD and architectural concepts (aggregate, repository, value object) in code.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

The annotations and interfaces are deliberately empty: marking a class `@AggregateRoot`,
`@ValueObject` or `@Repository` adds no behaviour and no runtime dependency on any framework,
it records the design intent in the type system. The value comes from what reads them — an
ArchUnit rule set that enforces, for instance, that an aggregate is only referenced by
identity, and bytecode-transformation integrations that derive technical mapping such as JPA
annotations from the declared concept, so the domain class is not littered with persistence
concerns.

Reach for it when a team practising DDD wants the vocabulary to be checkable rather than a
convention in a wiki. The costs are that the annotations alone change nothing, so without the
verification rules wired into the build they are decoration; that the generative integrations
add build-time magic which can obscure what the persistence layer is actually doing; and that
it presumes the team agrees on the tactical patterns in the first place.

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

`jq` is a small functional language rather than a selector: a filter is applied to an input
value and produces a stream of output values, and everything else — `map`, `select`,
`group_by`, `to_entries`, variables, user-defined functions, `reduce` — composes over that one
idea. `-r` emits raw strings instead of quoted JSON, which is what makes it usable in shell
pipelines, and `-e` sets the exit code from the result so a filter can be a test.

Reach for it any time JSON has to be inspected or reshaped in a terminal or a CI script. The
costs are that it reads the whole document into memory unless you use the streaming parser,
which is awkward, and that a filter beyond a few operators becomes write-only — past that
point, a short Python script is more maintainable and reviewable than a clever one-liner.

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

It standardizes what Jackson and Gson each do their own way: `JsonbBuilder.create()` yields a
`Jsonb` with `toJson` and `fromJson`, mapping follows documented default rules over public
properties, and `@JsonbProperty`, `@JsonbTransient` and friends adjust it. Yasson is the
reference implementation. Being a specification, the same annotated classes work on any
compliant provider, which is the point in a portable Jakarta EE application.

Reach for it when portability across application servers is a genuine requirement. Outside
that, the cost is a much smaller feature surface than Jackson: fewer modules and formats, less
control over polymorphism and streaming, and far less community material when something maps
unexpectedly. Most Spring and Boot applications simply use Jackson, which the framework
already configures.

### JSON-RPC 2.0 spec
**Short:** Minimal JSON request/response RPC standard; the wire format MCP is built on.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, llm-apps/tool-use-and-mcp @2, apis-frameworks/data-formats-and-api-contracts @2

A request is a JSON object with `jsonrpc`, `method`, optional `params` as either an array or
an object, and an `id`; the response echoes the `id` and carries exactly one of `result` or
`error`, where the error is an object with a numeric code, a message and optional data. Two
details give it most of its usefulness: omitting `id` makes the call a notification with no
response at all, and an array of requests is a batch answered by an array of responses.

Reach for it when you want request-response semantics over a transport that is not necessarily
HTTP — a pipe, a socket, stdio — which is exactly why MCP adopted it. The cost is that it
specifies the envelope and nothing else: no schema for `params`, no versioning, no discovery,
no streaming. Those you must add yourself, or pick gRPC, which brings them.

### JSONPath
**Short:** Path-expression language for selecting values inside a JSON document; used in tests and config selectors.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/text-encoding-and-regex @3

Expressions navigate a document with a small grammar: `$` for the root, dotted or bracketed
names, `[*]` and slices for arrays, `..` for a recursive descent that finds a name at any
depth, and `[?(...)]` for a filter. The recursive descent is the feature that makes it useful
against documents whose shape you do not fully control. For most of its life it was a
convention rather than a standard, with implementations disagreeing on filters and result
ordering, until RFC 9535 defined it properly.

Reach for it where a selector must be configuration rather than code — an assertion in a test,
a `kubectl -o jsonpath` output template, a field extractor in a pipeline. The costs are that
implementation differences are real, so an expression that works in one tool may not in
another, and that a `..` scan over a large document is expensive. For AWS tooling the
equivalent is JMESPath.

### jsonschema
**Short:** Lightweight JSON Schema validator for Python; a dependency-free alternative to Pydantic for tool schemas.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/prompting-context-and-structured-output @3

The library implements the JSON Schema drafts against plain Python dicts: `validate(instance,
schema)` raises on the first problem, while constructing a draft-specific validator such as
`Draft202012Validator` and calling `iter_errors` yields every violation with a JSON pointer to
its location, which is what you want when reporting back to a caller. Format checks such as
`date-time` and `email` are annotations by default and only enforced when you attach a format
checker with the relevant extras installed.

Reach for it when the schema is data — supplied by a client, stored in a database, or defining
an LLM tool — rather than a Python class you control. That is precisely where Pydantic is
awkward. The costs are that validation is interpreted rather than compiled, so it is slow on
hot paths, and that it validates but does not construct: you get a dict back, not a typed
object.

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

It builds the request form from the schema — a `.proto` file, a compiled descriptor set, or
server reflection — and adds the things a repeated workflow needs: environments with
variables, saved request collections, scripted pre-request steps for fetching a token, and
gRPC streaming support alongside REST and OpenAPI in the same workspace.

Reach for it when someone works against a gRPC API all day and wants stored requests and
environment switching rather than reconstructing a command line each time. The costs are that
collections are a parallel artifact that can drift from the schema, that team collaboration
features sit behind the paid tier, and that a desktop tool contributes nothing to CI. Pair it
with `grpcurl` for anything that must be reproducible or automated.

### Kryo
**Short:** Fast JVM binary serializer used for deep copies and cache payloads without requiring Serializable.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, apis-frameworks/design-patterns-and-principles @3

Kryo writes a compact binary form of arbitrary JVM object graphs without requiring
`Serializable`, handling references and cycles, and it can construct instances without calling
a constructor. The size and speed win comes from registration: registering classes up front
replaces the full class name in the stream with a small integer id, and
`setRegistrationRequired(true)` makes an unregistered class an error rather than a silent
fallback to the verbose form.

Reach for it for in-process deep copies and for cache or shuffle payloads inside one system
where both ends run the same code — this is why Spark and Flink use it. The costs are severe
outside that niche: a `Kryo` instance is not thread-safe and must be pooled or thread-local,
the format is not a stable cross-version contract so a class change breaks stored bytes, and
deserializing untrusted input is a remote-code-execution risk. For anything crossing a service
boundary, use protobuf or Avro.

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

Formerly Starlite, it is an ASGI framework that takes a different position from FastAPI on two
points. Dependency injection is layered — dependencies can be declared on the app, a router, a
controller or a handler and are resolved by name with the most specific winning — and
serialization defaults to `msgspec` rather than Pydantic, though Pydantic, attrs and
dataclasses are all supported as model types. Controllers group related routes as a class, and
plugins cover SQLAlchemy, channels and background tasks in the framework rather than in the
ecosystem.

Reach for it when an application is large enough that per-router configuration and a
batteries- included stack are worth more than FastAPI's ubiquity, or when serialization
throughput matters. The cost is exactly that ubiquity: a much smaller community, fewer
tutorials and third-party integrations, and less chance that a given hosting guide or LLM
answer is about your framework.

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

Colleagues hold a reference to the mediator instead of to each other, so the mediator owns the
interaction rules and the object graph goes from potentially N-by-N edges down to N. A dialog
controller that enables a button when two fields are valid, a chat room routing messages, and
a saga orchestrator coordinating services are all the same shape at different scales.

Reach for it when a cluster of components has grown tangled cross-references and every change
touches several of them. The cost is that the coupling is not removed, only collected: the
mediator accumulates every rule and becomes the god object nobody wants to open. Past a
certain size the honest successors are an event bus, where colleagues publish facts and the
mediator disappears, or an explicit workflow engine when the coordination is long-running.

### Memento
**Short:** GoF behavioral pattern capturing an object's state as an opaque snapshot so it can be restored later, e.g. undo.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

The originator produces a snapshot object that only it can interpret, and the caretaker stores
and returns it without ever reading the inside. That is the whole trick: rollback becomes
possible without exposing the originator's fields, because the memento's interior is visible
only to the type that made it — a private nested class in Java, or a closure in a language
without one.

Reach for it for undo and redo, a checkpoint before a risky operation, or an in-memory
transaction. The cost is memory: a full deep copy per step is unaffordable for anything large,
which is why real editors store inverse commands or diffs instead and snapshot only
periodically. If the state is already immutable, keeping a reference to the old value is the
entire pattern with no extra type.

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

Micronaut resolves injection points, AOP advice and configuration binding at compile time with annotation processors, generating the wiring as ordinary classes instead of discovering it by reflection during startup. That removes the reflective metadata a classic container builds on every boot, so processes start in tens of milliseconds with a smaller heap, and it makes GraalVM native images straightforward because there is little dynamic behaviour left to register.

It fits serverless functions and fleets of small services where cold start and memory per instance dominate the bill. The ecosystem is much smaller than Spring's, which is usually what decides the question.

### msgpack
**Short:** Compact binary serialization format with a JSON-like data model, used where JSON is too slow or too large.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

The data model is JSON's — maps, arrays, strings, numbers, booleans, null — but each value is
written with a one-byte type tag and a binary length rather than as text, so there is no
quoting, no escaping and no number parsing on the way in. Small integers, short strings and
short arrays fit in a single prefix byte, which is where most of the size saving comes from.
An extension type mechanism lets applications register their own type tags, and that is how
timestamps are represented.

Reach for it when a service already speaks JSON-shaped data but the encode and decode cost or
the payload size has become the bottleneck — cache values, RPC bodies, message queue payloads.
The cost is that the payload is opaque to every text-based tool and that, unlike protobuf or
Avro, there is no schema, so nothing enforces or evolves the shape. If you need a contract as
well as compactness, use protobuf.

### msgspec
**Short:** Very fast Python JSON/MessagePack serialization and validation via typed Structs; a Pydantic alternative.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @3

Types are declared as `msgspec.Struct` subclasses, which compile to a fixed-slot C
representation rather than a dict-backed object, and decoding is done in one pass: the C
decoder reads the JSON or MessagePack bytes and constructs the typed object directly, instead
of building an intermediate dict and then validating it. That single-pass design is where the
speed advantage over validate-after-parse libraries comes from, and it applies to encoding
too.

Reach for it for high-throughput boundaries — a hot API route, a message consumer, a
serialization layer — where validation cost shows up in a profile. The trade is deliberate
strictness and a smaller feature set: it validates rather than coerces, so a string where an
int is declared is an error rather than a conversion, and there is no equivalent of Pydantic's
custom validators, computed fields and broad ecosystem integration. Litestar uses it by
default; FastAPI does not.

### MVEL 2
**Short:** Fast JVM expression language with compiled expressions, used for rule conditions evaluated in hot loops.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, runtime-systems/text-encoding-and-regex @3

Its distinguishing feature is that an expression can be compiled once into a reusable form and
then executed repeatedly against different variable resolvers, so the parsing cost is paid at
setup rather than per evaluation — which is why Drools uses it for rule consequences and why
it appears wherever expressions run in a tight loop. The syntax is deliberately Java-like,
with property navigation, collection projections and inline lists and maps.

Reach for it when expressions come from configuration and are evaluated at high frequency. The
costs matter: MVEL evaluates arbitrary expressions against whatever objects you expose, so
executing a user-supplied expression is code execution unless you tightly control the context,
and the project has been quiet for a long time with a small community. For new work needing
sandboxed user expressions, CEL or JEXL with an explicit permission model are better-supported
choices; inside Spring, SpEL is already present.

### Netflix DGS
**Short:** Netflix's Spring Boot GraphQL framework: annotation-driven data fetchers, codegen and federation support.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

DGS layers annotation-driven GraphQL onto Spring Boot: `@DgsComponent` classes expose `@DgsQuery` and `@DgsData` fetchers bound to schema fields, a codegen plugin turns the SDL into Java types so schema and code cannot drift apart, and data loader support keeps nested fields from becoming N+1 queries. It runs on graphql-java underneath and supports Apollo Federation for serving a subgraph of a larger supergraph.

It is a reasonable choice in a Spring estate that values its codegen and federation ergonomics. Spring for GraphQL is the framework-native option covering much of the same ground, so a new project should compare the two rather than assume it needs a second GraphQL stack.

### Node
**Short:** The GUI Composite archetype (as in JavaFX Node): a container is itself a component, so layout and paint recurse.
**Kind:** concept
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1

The archetype's mechanism is that the container extends the same base type as the leaf and
holds a child list, so layout, hit testing, event delivery and painting are written once
against the base type and recurse down the tree. That is why setting an opacity or a transform
on a branch applies to everything beneath it — the traversal does not distinguish branches
from leaves, so the property composes automatically as it descends.

Reach for it whenever a container must be usable everywhere a leaf is: UI toolkits, document
models, expression trees. The costs are the ones Composite always carries — the base type
carries child operations a leaf cannot honour, and property lookups that walk to the root or
invalidations that walk to the leaves make an innocuous-looking setter an O(n) subtree
operation on a deep graph.

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

The subject keeps a list of listeners and calls them on change. The design choice that matters
is push versus pull: pushing the changed value into the callback is cheap but fixes the
payload shape forever, while pushing only a notification and letting the listener query back
keeps the contract small at the cost of a second call and a possible race with a later change.

Reach for it for in-process decoupling — UI updates, domain events inside one JVM. Three costs
recur. Listener lists hold strong references, so a listener that is never unregistered is a
textbook leak. Notification runs on the publisher's thread, often while it still holds a lock,
so a slow listener stalls the publisher and one thrown exception can abort the remaining
notifications. And ordering is unspecified. Across processes you want a broker; for composing
streams of events, Reactive Streams.

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

One `OkHttpClient` is designed to be shared for the life of the process: it owns the
connection pool, the dispatcher's thread pool and the cache, so creating one per request
throws away connection reuse and leaks threads. The interceptor chain is the extension point,
split deliberately in two — application interceptors run once per call and see redirects and
retries as a single logical request, while network interceptors run per actual network request
and can see the redirect hops. Retry on connection failure, HTTP/2 multiplexing and
transparent gzip are on by default.

Reach for it for any JVM or Android HTTP client work; Retrofit is the typed layer usually put
on top. The costs are that its defaults are permissive — a call has generous timeouts you
should tighten, and automatic retry on connection failure can duplicate a non-idempotent
request — and that a client created with `newBuilder()` shares the parent's pool, which is
correct but surprises people expecting isolation.

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

The SDK wraps the HTTP API in typed methods — chat completions, embeddings, files, batches — reading the key from `OPENAI_API_KEY`, retrying transient failures with backoff, and offering both a synchronous `OpenAI` and an `AsyncOpenAI` client. Streaming is exposed as iteration over response chunks, tool/function calling as structured objects rather than hand-parsed JSON, and structured output can be parsed straight into a Pydantic model.

Its wider significance is that its request and response shapes have become a de facto interchange format: point `base_url` at vLLM, Ollama, a router or an internal gateway and the same code talks to a self-hosted model, which is why the OpenAI-compatible endpoint shows up in tools that have nothing to do with OpenAI.

### OpenAPI 3.2
**Short:** The published standard for describing REST APIs in JSON or YAML, driving docs, clients and contract tests.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

The specification is maintained by the OpenAPI Initiative under the Linux Foundation and moves
slowly and additively, so a document is a long-lived artifact rather than something to rewrite
per release. What matters when picking a version is less the feature list than the support
matrix: the codegen, mock server, gateway and documentation renderer in your pipeline each
implement a particular version, and the newest one is rarely the safest.

Treat the version as a compatibility decision. Author to the newest version your entire
toolchain accepts, keep the document generated from or verified against the implementation so
it cannot drift, and version the document alongside the API it describes. When a tool does not
yet understand a newer document, downgrading the spec version is usually less painful than
replacing the tool, since the constructs that differ are mostly ones you can express either
way.

### OpenAPI 3.x
**Short:** Standard machine-readable REST API description of paths, schemas and auth; drives codegen, docs and gateways.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, traffic-edge/api-gateway @3

The document describes paths, operations, parameters, request and response bodies, security
schemes and reusable components, and its value is that it is machine-readable: the same file
drives generated clients, server stubs, mock servers, contract tests and gateway
configuration. The significant change within the 3.x line was 3.1 adopting JSON Schema 2020-12
as its schema dialect, replacing the earlier near-miss subset, which finally made schemas
portable to ordinary JSON Schema validators.

Reach for it for any HTTP API with consumers you do not control. The costs are drift and
tooling lag. A hand-written document diverges from the implementation immediately, so generate
it from code or test the code against it. And 3.1 support arrived unevenly — some generators
and gateways still expect 3.0 — so check the whole toolchain before choosing a version.

### OpenAPI Generator
**Short:** Generates typed client SDKs, server stubs and docs in many languages from an OpenAPI specification.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/compiler-toolchain-and-codegen @2

It is a fork of swagger-codegen with a far larger generator set — dozens of languages, and
often several per language differing in HTTP client and async model. Each generator is a set
of Mustache templates plus a Java class that builds the model, which is the important
structural fact: templates can be overridden per project, and generator-specific
`additionalProperties` control naming, packaging and library choice.

Reach for it to produce client SDKs for consumers or to enforce a contract-first workflow
where server interfaces are generated and implemented. The costs are uneven quality between
generators, output that is verbose and occasionally non-idiomatic, and sensitivity to the
input document — a spec with unnamed inline schemas produces types called `InlineResponse200`.
Pin the generator version, commit or publish the output rather than regenerating silently, and
budget time for template overrides.

### openapi-python-client
**Short:** Generates a typed async Python SDK from an OpenAPI document, typically run against a service's own /openapi.json.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/compiler-toolchain-and-codegen @2, apis-frameworks/web-framework-and-http-client @3

It generates a real package rather than a single module: `attrs` classes for every schema with
`UNSET` distinguishing an absent field from an explicit null, one module per endpoint exposing
`sync`, `asyncio` and their `_detailed` variants, and an `httpx`-based client object carrying
the base URL, headers and timeouts. Because the models are typed, an IDE and mypy see the API
surface.

Reach for it to consume a service that publishes an OpenAPI document — pointing it at a
FastAPI application's own `/openapi.json` is the common case, and it makes an internal service
call type-checked at both ends. The costs are that the output is generated code you must
regenerate and re-review whenever the spec changes, that a poor spec produces poorly named
classes, and that customization is limited to templates. Committing the generated client and
regenerating in CI keeps the diff visible.

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

orjson is written in Rust and serializes straight to `bytes` rather than `str`, skipping the encode step the standard library pays, and it natively handles `datetime`, `date`, `UUID`, dataclasses and numpy arrays that `json.dumps` refuses without a custom encoder. It is not a drop-in in every respect: output is always UTF-8 bytes, behaviour is selected through option flags rather than keyword arguments, and it will not serialize subclasses of `dict` or `list` unless you supply a default hook.

Reach for it on a hot serialization path — an API response, a cache write, a structured log line — where the difference is measurable. For a config file read once at startup it changes nothing.

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

The client sends a SHA-256 hash of the operation text instead of the operation. With automatic
persisted queries the server answers a cache miss with `PersistedQueryNotFound`, the client
retries once with the full document plus the hash, and the server registers it; with trusted
documents the manifest is extracted at build time and anything absent is rejected outright.
Because the request shrinks to a short GET with a stable URL, it also becomes cacheable by a
CDN, which is often the larger win.

Reach for it when request bodies dominate on mobile links, or when you want the executable
operation set closed. The costs are deployment coupling — the client build and the server
registry must ship together, and a rollback can leave clients sending hashes the server has
forgotten — and the fact that automatic registration is not a security control, since any
caller can register a document.

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

The schema is built in TypeScript with a builder object rather than written as SDL and matched
to resolvers, and the builder is generically typed such that a resolver's arguments and return
value are inferred from the field definition — a mismatch between the schema and the code is a
compile error, not a runtime one. Plugins add federation, Relay conventions, authorization
scopes and dataloader integration, each contributing to the same inference.

Reach for it when the whole team is in TypeScript and you want the schema and the code to be
provably in step. The costs are the ones every code-first approach carries: the SDL is an
output, so schema review and non-TypeScript consumers work from a generated artifact you must
remember to publish, and the heavy generic types can slow the compiler and produce error
messages that take practice to read.

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

Each field is written as a varint tag combining the field number and a wire type, followed by
the value, and that is the whole compatibility story: names are a source-level convenience,
numbers are the contract. A parser that meets an unknown field number keeps the bytes and
re-emits them on serialization, so an old service can round-trip a message containing new
fields without losing them. Proto3 makes scalars implicitly defaulted, which is why
distinguishing unset from zero requires `optional` or a wrapper type.

Reach for it wherever payload size and parse cost matter and both ends can share a schema —
gRPC, event streams, on-disk records. The costs are that the bytes are unreadable without the
schema, making debugging and ad-hoc querying awkward, and that the rules protecting you are
conventions a careless edit can break: renumbering or reusing a deleted field number silently
corrupts every consumer that has not been rebuilt.

### Protobuf schema registry
**Short:** Central store of versioned .proto schemas enforcing compatibility rules between producers and consumers.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2, data-access/schema-and-migration @3

Registering `.proto` schemas is different from registering Avro ones, because protobuf's
compatibility rules live in the field numbers rather than in the registry: a consumer with an
old descriptor skips unknown fields automatically, so most additive changes are safe by
construction. What the registry adds is enforcement of the rules that are not automatic —
never reuse or renumber a field, never change a field's type, reserve the numbers of anything
deleted — plus a place for consumers to fetch a descriptor set they were not compiled against.

Reach for it when producers and consumers are released independently and a bad `.proto` edit
would otherwise reach production. The costs are that a registry only sees changes people push
through it, so a service that generates code straight from a local file bypasses the gate
entirely, and that imports between `.proto` files make the registered unit a dependency graph
rather than a single file.

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

A new object is produced by copying a configured one rather than by running a constructor,
which matters when the setup is expensive or when the concrete type is only known at runtime.
Java's `Cloneable` is the notorious implementation: `clone` is `protected` on `Object`, the
interface declares no method, no constructor runs, and `final` fields cannot be reassigned, so
correct use is almost all convention.

Reach for the idea when you need many near-identical objects from a template — a prototype-
scoped bean, a pre-configured request builder, a test fixture. The real cost is depth: a
shallow copy shares every mutable referent, so two "independent" copies mutate each other, and
hand-written deep copies rot as fields are added. Prefer a copy constructor or a record with
`with`-style methods, both of which the compiler helps you keep complete.

### Proxy
**Short:** GoF structural pattern: a stand-in with the same interface that adds lazy loading, remoting, caching or access control.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1, apis-frameworks/aop-middleware-and-scheduling @3

The proxy implements the target's interface and holds it, so callers cannot tell the
difference, and it inserts a policy in front of every call: create the target on first use,
send the call over the wire, check a permission, cache the result. On the JVM this is
generated at runtime — a JDK dynamic proxy when the target has an interface, a CGLIB subclass
when it does not, which is why `final` classes and `final` methods cannot be advised.

Reach for it whenever a cross-cutting concern must attach without editing the target, which is
precisely how Spring applies `@Transactional` and `@Cacheable`. The costs are the ones that
generate the most confused bug reports: a call from one method of the target to another goes
straight to `this` and skips the proxy entirely, `getClass()` returns a generated name, and
stack traces gain frames. When the behaviour must apply inside the class, byte-code weaving
with AspectJ is the alternative.

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

Validation is not interpreted on every call: the schema is compiled when the class is created and then executed by `pydantic-core`, a Rust engine, so the Python-level cost is mostly whatever `field_validator` functions you add yourself. FastAPI builds request parsing, response serialization and its OpenAPI schema on top of it, and LLM tooling reuses the same models as JSON Schema for structured output and tool arguments.

Reach for it wherever untrusted data crosses a boundary: request bodies, config files, model output. One practical trap is that v2 renamed most of the v1 surface (`model_dump`, `field_validator`, `model_config`, `Annotated` constraints), so v1-era snippets do not run unchanged.

### pydantic-core
**Short:** The compiled Rust validation/serialization engine underneath Pydantic v2; installed automatically with it.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

Pydantic v2 compiles each model once into a core schema and executes it in Rust, which is where the large speedup over the pure-Python v1 comes from and why validation errors carry precise structured locations. You never import it directly; you meet it through `BaseModel`, `TypeAdapter`, `model_validate` and `model_dump`.

It explains behaviour that otherwise looks arbitrary. Strict versus lax mode decides whether the string `"1"` becomes the integer `1`. `model_construct` skips validation entirely, which is fast and unsafe. Custom types are integrated by implementing `__get_pydantic_core_schema__` rather than the v1 validator decorators, which is the migration step people trip on. Serialization runs through the same engine, so `by_alias`, `exclude_none` and the warnings raised when a field's runtime value does not match its declared type all originate there.

### pydantic-settings
**Short:** Pydantic BaseSettings package that loads and validates twelve-factor config from env vars and .env files.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, security/secrets-and-cryptography @3

A `BaseSettings` subclass declares configuration as typed fields with defaults; at startup the values are read from environment variables, optionally with a prefix, from a `.env` file, or from a secrets directory, then coerced and validated by Pydantic. The point is that a missing or malformed value fails loudly at boot rather than as a `None` deep inside a request an hour later, and `SecretStr` keeps credentials out of reprs, tracebacks and logs.

Reach for it for twelve-factor configuration in any Pydantic application, and instantiate the settings object once and inject it rather than reading `os.environ` at call sites. It lives in its own package since Pydantic v2, so it is a separate dependency from `pydantic` itself.

### pydantic-settings 2.x
**Short:** Pydantic v2 settings management: typed config from env vars, dotenv and secret files, with SecretStr masking.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/data-formats-and-api-contracts @3

The v2 line is where the source model became extensible. Configuration is declared on
`SettingsConfigDict` — env prefix, dotenv file, case sensitivity, and `env_nested_delimiter`,
which lets `APP__DB__HOST` populate a nested model — and `settings_customise_sources` lets a
settings class reorder or add sources, so a secrets manager, a JSON file or a CLI parser slots
in beside environment variables with a defined precedence. TOML, YAML and CLI sources ship in
the package.

Reach for it whenever a service is already on Pydantic v2, since configuration then uses the
same validation and the same error format as the rest of the application. The costs are that
precedence between sources is a design decision you must get right and document, that a
nested-model layout with delimiters is easy to mis-set, and that the whole model is validated
at construction — which is the point, but means one bad variable stops the process at boot.

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

`EmailStr` is not self-contained: the type raises an import error when a model is defined unless the `email-validator` package is present, which is exactly what this extra installs. Validation goes well beyond a regex, since the library parses the address, normalizes the domain including internationalized names, and can optionally test the domain for deliverability, though the Pydantic type itself sticks to the syntactic and normalization checks. Add it when a signup or contact payload needs a real address; a plain `str` is honest when you only care that the field is present.

### python-dotenv
**Short:** Loads key=value pairs from a .env file into the process environment so local config stays out of the code.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/dependency-injection-and-config @1

`load_dotenv()` reads a `.env` file of `KEY=value` lines and injects them into `os.environ`,
searching upward from the calling file by default. The behaviour that matters is that it does
not overwrite a variable already set in the real environment unless you pass `override=True`,
which is exactly right: the file is a local convenience and a genuinely exported variable, or
one injected by the platform, must win. `dotenv_values` returns a dict without touching the
process environment, for cases where you would rather not mutate global state.

Reach for it for local development so twelve-factor configuration works without exporting
variables by hand. The costs are that it is only a loader — no typing, no validation, no
defaults, so everything arrives as a string — and that a `.env` file containing real secrets
is a file on disk that must never be committed. In production, inject the environment from the
platform's secret store instead.

### Quarkus
**Short:** Kubernetes-native Java framework with build-time wiring and GraalVM native-image support for fast, small services.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @2, devtools/compiler-toolchain-and-codegen @3, platform-delivery/container-and-image @3

Quarkus moves work that traditional Java frameworks do at startup -- classpath scanning, annotation processing, proxy generation, configuration binding -- into build-time augmentation performed by extensions, so the running application does less and starts faster on the JVM, and can be compiled ahead of time by GraalVM into a native binary that starts in milliseconds with a fraction of the memory. It is built on Vert.x with a reactive core, supports both imperative and reactive styles, and its extensions cover the usual set (Jakarta REST, Hibernate with Panache, Kafka, gRPC, security) with the constraint that a library needs an extension to behave under native compilation. Its dev mode, which recompiles on the next request, is the day-to-day reason developers like it. Reach for it for many small Kubernetes services or serverless functions where startup time and memory per instance drive cost; Spring Boot has a far larger ecosystem, and native compilation brings its own costs in build time, reflection configuration and harder profiling.

### Quartz
**Short:** Java job scheduler with cron triggers, JDBC job persistence and clustered execution with misfire handling.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2

The model is three objects: a `Job` with the work, a `JobDetail` with its identity and data
map, and a `Trigger` — cron, simple interval, or calendar-based — that decides when to fire.
With the JDBC job store the whole schedule lives in the `QRTZ_` tables, so it survives
restarts and can be changed at runtime, and clustering works by every node contending for a
row lock on the same tables so exactly one fires a given trigger. A missed fire, from downtime
or a saturated pool, is handled by a per-trigger misfire instruction that says whether to run
immediately, skip, or reschedule.

Reach for it when schedules are data — created or edited at runtime — and must survive
restarts. The costs are the schema and the locking: cluster coordination is database
contention, so it does not scale to very high trigger rates, and clock skew between nodes
causes real problems. For fixed schedules, `@Scheduled` with ShedLock is far less machinery.

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

Convention over configuration is a mechanism, not a slogan: a class named `Order` maps to an
`orders` table by inflection, a controller action renders the matching template by name, and
routes are generated from a `resources` declaration, so a working CRUD path exists before any
wiring is written. ActiveRecord is the pattern of the same name — the model object is the row
— with migrations as versioned schema changes, and the modern default front end is Hotwire,
which sends HTML over the wire instead of building a JavaScript client.

Reach for it when time to a working product dominates and the domain is broadly CRUD over a
relational database. The costs appear at scale: ActiveRecord makes N+1 queries the default
outcome unless you eager-load, per-request memory and CPU are high next to compiled stacks,
and the conventions that accelerate the first year resist an unusual domain model in the
third.

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

It wraps Netty in a Reactive Streams API, giving `HttpClient` and `HttpServer` types whose
requests and responses are `Mono` and `Flux` pipelines, and it manages the pieces Netty leaves
to you: a shared `LoopResources` event-loop pool sized by default to the available processors,
and a connection pool per remote host with configurable maximum connections, pending-acquire
limits and idle eviction. It is what runs underneath both Spring WebFlux and `WebClient`.

You mostly configure it rather than call it, and the configuration matters. The default
connection pool is a common source of `PoolAcquireTimeoutException` under load, response
timeouts must be set explicitly because the default is none, and the rule that dominates
everything else is that blocking on an event-loop thread is fatal to throughput — Reactor's
`BlockHound` exists specifically to catch it in tests.

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

The strategy set is materialized as data rather than as anonymous lambdas: an enum constant
per strategy carrying its behaviour, or a record bundling a predicate with a handler.
Selection becomes `valueOf` or an `EnumMap` lookup instead of an if-chain, and because each
strategy has a real name that token survives into configuration files, database columns and
log lines, where a lambda would have printed a synthetic class name.

Reach for it when the set is closed and must be persisted, configured or audited. The cost is
that closure: another module cannot contribute a strategy, and an enum constant is built
during class initialization so it cannot take an injected collaborator. When the set is
open-ended or the strategies need wiring, inject a `Map<String, Strategy>` and let the
container fill it from the bean names.

### Redoc
**Short:** Renders an OpenAPI spec into readable three-panel reference documentation, as a static page or a CLI build step.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

Redoc turns an OpenAPI document into a three-panel reference page: navigation on the left, prose and schema in the middle, request and response samples on the right. You either drop it on a page as a script tag pointing at your spec URL, or build a single self-contained HTML file in CI so the docs are a deploy artifact that cannot drift from the spec.

The contrast with Swagger UI is what decides it: Swagger UI centres on an interactive "try it" console, Redoc centres on readability and deep schema rendering. Publish Redoc when the spec is your public contract; keep Swagger UI when developers mostly want to fire requests at a dev environment.
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

The convention wraps a list in three layers: a connection type with `edges` and `pageInfo`,
edges carrying a `cursor` alongside each `node`, and `pageInfo` with `hasNextPage`,
`hasPreviousPage`, `startCursor` and `endCursor`. Paging arguments are `first`/`after` and
`last`/`before`. The reason for the ceremony is that a cursor is an opaque server-side
position rather than an offset, so inserts and deletes during paging do not shift or duplicate
rows the way `OFFSET` does, and the edge is a place to hang relationship-specific data such as
a role or a joined-at date.

Reach for it whenever a list is large, mutable, or consumed by Relay, which requires it. The
costs are verbosity in every query, and a `totalCount` that the specification does not include
and that is often expensive to compute. For a small, stable list, a plain array field is
honest and much easier to read.

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

Underneath it is `urllib3`, and the object that matters is `Session`: it holds a connection
pool so consecutive requests to the same host reuse a socket, and it carries cookies, headers
and auth across calls. The module-level `requests.get` creates and discards a session each
time, which is fine for a script and wasteful in a service. The single most consequential
default is that there is no timeout at all, so a request can hang indefinitely — always pass
one.

Reach for it in scripts, tests and synchronous services. Inside an async application it is the
wrong tool: every call blocks the event loop, so an `await` elsewhere in the same coroutine
buys nothing and one slow upstream stalls the process. Use `httpx`, which offers a nearly
identical API with an async client, or `aiohttp`.

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

The style is a set of constraints — statelessness, a uniform interface, resources identified
by URI, representations manipulated through standard methods, cacheability, layering — and the
one almost nobody implements is hypermedia, where responses carry the links describing what
the client may do next. What the industry calls REST is usually the Richardson maturity
model's level two: resource URIs plus correct HTTP methods and status codes, which is a
reasonable place to stop.

Reach for it as the default for anything with external or browser consumers, because the
entire toolchain — caches, gateways, logs, `curl`, OpenAPI — already understands it. The costs
show up when the resource model fights the use case: over-fetching and under-fetching that
drive teams to GraphQL, and multi-step operations that do not map to a noun, where an explicit
action endpoint is more honest than inventing a resource. For internal high-volume calls, gRPC
is cheaper.

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

The enterprise-integration router is a component that reads a message — a header, a field, or
the result of a rule — and returns the name of the channel to send it on, with variants for a
recipient list, a splitter and aggregator pair, and a dynamic router whose rules are
themselves data. Spring Integration and Camel both implement them as first-class endpoints, so
the topology is declared rather than coded into branch statements.

Reach for it when the routing rule is something you want to see, change and test on its own.
The cost is that a message's path is no longer readable from any single file: tracing needs
correlation ids and channel-level logging, and a dynamic router driven from a database becomes
a distributed conditional with no owner. For two fixed destinations, an explicit call is
plainer.

### rsocket-transport-netty
**Short:** Netty-based RSocket transports over TCP and WebSocket; reactive request/stream messaging with credit-based backpressure.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/io-networking-and-syscalls @3

RSocket's core is transport-agnostic — it produces and consumes frames — and this artifact
supplies the two Netty-based transports that carry them: `TcpClientTransport` and
`TcpServerTransport` for service-to-service links, and `WebsocketClientTransport` and
`WebsocketServerTransport` for anything that must traverse a browser or an HTTP-only path. It
builds on Reactor Netty, so the connection shares that event-loop model and its resources.

Choose TCP inside the network for the lowest framing overhead, and WebSocket when a browser, a
load balancer or a firewall requires the traffic to look like HTTP. The costs follow the
transport rather than RSocket: TCP needs its own TLS and health handling since no HTTP layer
provides them, and WebSocket adds a handshake and a masking overhead plus every idle-timeout
and buffering quirk that proxies apply to long-lived upgrades.

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

Every spec-compliant server answers the `__schema` and `__type` meta-fields, returning its own
type system as ordinary GraphQL data. That single mechanism is what makes the tooling
ecosystem possible without any out-of-band artifact: playgrounds build their documentation and
autocomplete from it, client code generators emit types from it, and gateways use it to
validate a subgraph.

Keep it on in development, where it is the whole developer experience. On a public production
endpoint most teams disable it, because it hands an attacker the complete map of queries,
mutations and deprecated fields; the schema is then published as SDL through the normal
release process instead. Be aware that turning it off is a speed bump rather than a wall —
field-suggestion errors leak type names unless those are disabled too — and it breaks any
client codegen pointed at the live endpoint.

### Schema Registry
**Short:** Central store of Avro/Protobuf/JSON event schemas that enforces compatibility rules as producers evolve.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, data-movement/event-streaming-and-processing @2, data-movement/data-quality-and-lineage @3

The producer registers its writer schema once and puts only a small schema id on the wire, so messages stay compact; the consumer looks the id up and deserializes against it, which is what lets an old consumer read a new message. Registration is the enforcement point: the registry rejects a schema that violates the configured compatibility mode, so an incompatible field change fails at deploy time rather than breaking every downstream consumer at 3am.

Reach for it whenever a topic outlives the code that writes to it, which in practice is every event stream in a microservice estate. The discipline it demands is choosing the compatibility mode deliberately, since backward, forward and full compatibility each forbid a different class of change.

### schema validation
**Short:** Enforcing a declared shape on a message so fields stay separated and untrusted text cannot pose as instructions.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/prompting-context-and-structured-output @2

A declared shape — a JSON Schema document, a Pydantic model, a protobuf descriptor — is
applied at the boundary before anything downstream reads the payload, and the validator either
produces typed values or rejects with a path to the offending field. The security property
follows from the structure rather than from the checking: when each piece of data has its own
typed field, untrusted text stays a value and never gets concatenated into a position where an
interpreter would read it as an instruction.

Reach for it at every trust boundary, including the output of a model. Two costs recur. Schema
and code drift apart unless one is generated from the other, and the unknown-field policy is a
real decision — ignoring extras keeps you forward compatible, rejecting them catches typos,
and you must choose deliberately. Validation also confirms shape, not meaning: a perfectly
well-formed value can still be hostile.

### Schema versioning
**Short:** Hashing or versioning a tool or API schema so consumers detect contract drift when a deploy changes it.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/tool-use-and-mcp @2, platform-delivery/ci-cd-and-release @3

Each revision of the contract gets an identity: a semantic version bumped by hand, or a hash
over the normalized schema so that any change at all produces a new id. Consumers record the
id they were generated against and compare it at call time or at startup, which converts
silent drift — a renamed field, a tightened enum — into an explicit mismatch at a point where
someone is watching.

Reach for it wherever a consumer is generated from or caches the schema: SDK clients, agents
that cache tool definitions, event consumers holding a reader schema. The cost of hashing is
noise, since reordering or a comment change yields a new id unless you normalize first, and
alerts people learn to ignore are worse than none. Pair it with compatibility rules — additive
fields only, never reuse an identifier — so the common version bump requires no consumer
action at all.

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

The server exposes `grpc.reflection.v1.ServerReflection`, a bidirectional streaming service
that returns its own file descriptors on request. A client asks for the descriptor of a symbol
or file, gets the compiled schema back, and can then build a request message for a method it
knew nothing about a moment earlier — which is precisely how `grpcurl`, `grpcui` and the GUI
clients work with no `.proto` on disk.

Enable it in development and in internal environments; the productivity difference when
debugging is large. Disable it, or gate it behind authorization, on anything exposed beyond a
trusted network, because it publishes the complete method and message catalogue to any caller.
When it is off, the same tools still work if you pass a compiled descriptor set built with
`protoc --descriptor_set_out`, which is the pattern for locked-down environments.

### Server-Sent Events
**Short:** One-way HTTP streaming format pushing incremental server events (such as LLM tokens) with auto-reconnect.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

The response is a long-lived `text/event-stream` body in which each event is a block of
`field: value` lines terminated by a blank line, so the parsing rule is trivial and the
connection is an ordinary HTTP response that proxies, gateways and auth headers already
understand. Reconnection is part of the format rather than the application: the server may
send a `retry:` interval and an `id:` per event, and the browser reconnects automatically with
`Last-Event-ID`, letting the server resume from where the client stopped.

Reach for it for one-way push — token streaming from a model, progress updates, dashboards —
where the simplicity over a WebSocket is worth having. The costs are that it is text-only,
that any buffering proxy in the path will hold the stream until it thinks the response is
complete unless you disable buffering, and that a long-lived connection still consumes a
worker or an event-loop slot per client.

### Server-Sent Events (SSE) spec
**Short:** W3C/WHATWG standard for a one-way server-to-client event stream over plain HTTP; the basis of token streaming.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

The specification defines both the `text/event-stream` wire format and the browser
`EventSource` API on top of it. Three fields carry the semantics: `event:` names a type so a
listener can be registered for it, `data:` may repeat and the lines are joined with newlines,
and `id:` sets the value the browser will resend as `Last-Event-ID` after an automatic
reconnect. A line beginning with a colon is a comment, which is the standard way to send a
keepalive that no listener ever sees.

The constraints are in the API rather than the protocol. `EventSource` cannot set request
headers, so a bearer token has to travel in a cookie or a query parameter, and over HTTP/1.1
the browser's six-connections-per-origin limit is shared with everything else the page loads.
Both disappear if you read the same stream with `fetch` and parse it yourself, or if the
connection is HTTP/2.

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

It is deliberately not a scheduler. Each instance still runs its own `@Scheduled` trigger, but
`@SchedulerLock` wraps the invocation in an attempt to insert or update a row in a shared
store — JDBC, Redis, Mongo, DynamoDB — keyed by the task name; whoever wins runs, everyone
else returns immediately. `lockAtMostFor` is the safety valve that releases a lock held by a
crashed node, and `lockAtLeastFor` guards against clock skew making two nodes both fire within
a moment of each other.

Reach for it to make an existing single-node schedule safe on multiple replicas, which is the
common need and takes one annotation. The important caveat is in the documentation and worth
repeating: it does not guarantee exactly-once execution, because a lock lost to a long GC
pause or an expired `lockAtMostFor` can let a second node start while the first is still
working. The task must still tolerate overlap.

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

The pattern bundles two separate promises: exactly one instance, and a global point of access
to it. The first is often genuinely needed; the second is what causes the damage. Every
implementation is a trade among initialization timing, thread safety and reflection or
serialization holes — eager static field, holder class, enum, or lazy with double-checked
locking, which is simply broken without a `volatile` field.

The costs are testing and lifecycle. A class reaching for a singleton names a concrete type,
so no test can substitute it and state leaks between tests unless someone remembers to reset
it; in a container with multiple classloaders there is one instance per loader, not one per
process. Reach for a singleton-scoped bean instead — the container guarantees the single
instance while injection keeps the dependency visible and replaceable.

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

It is not a WebSocket library with extras — it is its own protocol layered on Engine.IO, which
means a raw WebSocket client cannot talk to a Socket.IO server and vice versa. The handshake
may start on HTTP long polling and upgrade to WebSocket afterwards, and the protocol adds
named events, acknowledgement callbacks, rooms and namespaces, plus automatic reconnection
with backoff and buffered emits while disconnected.

Reach for it when you want those features out of the box and both ends are yours. The
deployment costs are specific: the polling-then-upgrade handshake requires sticky sessions at
the load balancer, and broadcasting across more than one process requires an adapter — the
Redis adapter is the usual one — because rooms are per-process state. Where you control the
client and need neither rooms nor fallback, a plain WebSocket with a small message envelope is
much less to operate.

### SockJS
**Short:** WebSocket emulation layer that falls back to XHR streaming or long polling when a real WebSocket cannot be established.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

The client first issues an HTTP request to `/info` to learn what the server supports and
whether a proxy is interfering, then negotiates a session over the best available transport
and addresses it with a URL shaped `/server/session/transport`. If a real WebSocket cannot be
established it degrades to XHR streaming, then to long polling, while presenting the same
message-oriented API to application code either way.

It was essential when corporate proxies and older browsers routinely blocked upgrades. That is
largely historical now, and the cost is significant: the session URL pattern requires sticky
sessions at the load balancer, the fallback transports multiply requests and latency, and the
extra layer complicates every debugging session. For new work, use WebSocket directly and
handle failure with reconnection and a polling path only if measurement shows you need one.

### SockJS client
**Short:** Browser library negotiating a WebSocket-like session, falling back to XHR streaming when WebSocket is blocked.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

It presents a `WebSocket`-shaped object — `onopen`, `onmessage`, `onclose`, `send` — so
application code is written once and does not care which transport was negotiated underneath.
Before connecting it probes the server's `/info` endpoint for capabilities and clock skew,
then tries transports in order, and it emulates the WebSocket API closely enough that most
libraries built for a raw socket accept it, which is how it plugs in as a `webSocketFactory`
for a STOMP client.

Reach for it only when a measured population of clients cannot open a WebSocket. Otherwise the
costs outweigh it: the fallback transports need sticky routing and generate far more HTTP
traffic, binary frames are not supported so everything is text, and the emulation is close but
not identical, which produces subtle differences in close codes and back-pressure behaviour.

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

Advice is applied by wrapping the bean in a proxy at container startup — a JDK dynamic proxy
when the bean implements an interface, a CGLIB subclass otherwise — and the pointcut language
is a subset of AspectJ's, matched against method executions on Spring-managed beans only.
Every declarative feature you already use is this mechanism: `@Transactional`, `@Cacheable`,
`@Async`, `@Retryable` and method-level security are all advice on a proxy.

The consequences are the source of most Spring mysteries. A call from one method of a bean to
another goes through `this` and bypasses the proxy entirely, so the annotation silently does
nothing; `private` and `final` methods cannot be advised; and the proxy is a different class,
so injecting the concrete type or comparing `getClass()` behaves unexpectedly. When you need
interception on constructors, fields or intra-class calls, AspectJ weaving is the alternative
— otherwise, restructure so the call crosses a bean boundary.

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

The unit of work is the chunk: the framework reads `commit-interval` items through an
`ItemReader`, passes each through an optional `ItemProcessor`, and writes the whole chunk at
once through an `ItemWriter` inside a single transaction, then repeats. Progress is recorded
in the `JobRepository` — a set of database tables holding job and step executions with their
read, write, skip and commit counts — which is what makes restart meaningful: a failed job
resumes at the chunk boundary it reached rather than from the beginning. Skip and retry
policies decide whether a bad record kills the run.

Reach for it for large-volume, restartable, auditable processing over files or databases. The
costs are the repository schema and the concepts that come with it, the fact that a job
instance is identified by its parameters so re-running with identical parameters is refused by
design, and that the framework assumes a batch shape — for streaming or event-driven work it
is the wrong model entirely.

### Spring Batch Admin
**Short:** Legacy web UI for Spring Batch: browse job executions, restart failures and inspect step metrics.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, observability/alerting-and-incident-response @3

It was a drop-in web application providing what the core framework has no UI for: browsing job
and step executions from the repository tables, inspecting parameters and failure exit
messages, and restarting or stopping a job without writing a launcher. It read the same
`JobRepository` schema the runtime writes to.

It reached end of life and was not carried forward; Spring Cloud Data Flow is the recommended
successor for orchestrating and monitoring batch and stream jobs, at considerably greater
operational weight. Treat this as something to migrate away from rather than adopt. If all you
need is visibility, the repository tables are a documented schema you can query directly or
expose through your own admin surface, which is what many teams end up doing rather than
running Data Flow for a handful of jobs.

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

Auto-configuration inspects what is on the classpath and what beans you have not defined, then wires sensible defaults, so adding a starter dependency is usually the whole setup step. Starters bundle a coherent set of dependencies at compatible versions, an embedded server makes the application a runnable jar, and externalized configuration plus profiles keep environment differences out of code.

It exists to remove the configuration boilerplate that plain Spring required. Reach for it for essentially any JVM service; the price is a large opinionated dependency graph, and debugging means learning to read the condition evaluation report to find out why a bean you expected was or was not created.

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

It is two halves. The server reads property and YAML files out of a Git repository (or a filesystem, or Vault) and serves them over HTTP addressed by application name, profile and label, so `orders-service` on the `prod` profile gets exactly the files that match. The client pulls its configuration during bootstrap before the rest of the context starts, and `@RefreshScope` beans can be rebuilt at runtime by hitting `/actuator/refresh` or broadcasting over Spring Cloud Bus, so a value change does not require a redeploy.

Git backing is the reason to use it: configuration changes get history, review and rollback like code, and values can be stored `{cipher}`-encrypted for the server to decrypt. On Kubernetes, ConfigMaps and Secrets plus an external secrets operator usually cover the same ground with one fewer service to run and keep available.

### Spring Data REST
**Short:** Auto-exposes Spring Data repositories as a hypermedia HAL REST API with paging, sorting and discoverable links.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, data-access/orm-and-data-mapping @2, apis-frameworks/data-formats-and-api-contracts @3

It scans for Spring Data repositories and exposes each as a HAL hypermedia collection with
paging, sorting, search endpoints derived from your query methods, and association links,
without a controller being written. `@RepositoryRestResource` renames or hides a repository,
projections shape the returned representation, and excerpts control what an embedded resource
looks like inside a collection.

Reach for it for an internal admin surface or a prototype, where getting a working API in
minutes matters more than its shape. The reason it is a poor fit for a public API is
architectural: the exposed contract is your persistence model, so a column rename becomes a
breaking API change and there is no natural place for use-case logic, validation beyond
constraints, or an operation that is not a CRUD verb. Once those appear, hand-written
controllers over a service layer are the right structure.

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

It is the Spring team's integration of graphql-java with Spring Boot: `@Controller` classes carry `@QueryMapping`, `@MutationMapping` and `@SchemaMapping` handlers, schema files under `resources/graphql` drive the wiring, and `@BatchMapping` or a `DataLoader` collapses the N+1 that nested fields otherwise cause. Transports come with it — HTTP, WebSocket subscriptions, RSocket — as do Spring Security integration and `GraphQlTester` for tests.

Reach for it as the default in a Spring Boot application, since it fits the bean, security and observability machinery already there. It is schema-first: the SDL is the contract and resolvers are written against it, which is a discipline rather than a limitation.

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

The machine is configured as states and transitions with guards deciding whether an event is
accepted and actions running on transition, entry or exit, and it implements the parts hand-
rolled machines usually lack: hierarchical states, orthogonal regions running concurrently,
timers and deferred events, and a persistence interface so the current state can be written to
a database and rehydrated for a long-running process. A `StateMachineFactory` builds one per
business entity rather than sharing a singleton.

Reach for it when the process is genuinely complex and long-lived — an order or claim
lifecycle spanning days, with concurrent sub-processes. The costs are substantial: the
configuration DSL is verbose, a machine instance is heavyweight so a factory plus persistence
is required for anything per-entity, and debugging a transition that did not fire because a
guard returned false is unpleasant. For a handful of states, an enum with per-constant
transitions and a persisted state column is far less to own.

### Spring WebFlux
**Short:** Spring's non-blocking reactive web stack built on Reactor and Netty, with Flux/Mono handler signatures.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2

Instead of a thread per request, a small event-loop pool handles all connections and a handler
returns a `Mono` or `Flux` describing the pipeline; nothing executes until subscription, and
backpressure propagates from the consumer back to the source. That is what lets one process
hold tens of thousands of idle or slow connections and stream a response without buffering it.
Both annotated controllers and functional `RouterFunction` routing are supported.

Reach for it for high-connection, streaming or heavily I/O-fanout workloads. The costs are the
reason it is not a default. The stack must be non-blocking end to end — one blocking JDBC call
on an event-loop thread stalls every other request on that loop — debugging a reactive
pipeline is much harder, and `ThreadLocal`-based context such as MDC logging needs deliberate
handling. With virtual threads, the servlet stack now covers many of the cases that used to
justify the move.

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

The handshake runs through the normal servlet request pipeline, so `HandshakeInterceptor` is
where the HTTP session, cookies and authentication are copied into the WebSocket session's
attributes — after the upgrade there are no further HTTP requests to authenticate. Above the
raw handler sits the STOMP layer: an in-memory simple broker handles subscriptions and
destinations for a single node, and `StompBrokerRelay` forwards them to RabbitMQ or ActiveMQ
so subscribers on different instances see each other's messages.

Two costs decide the deployment. A `WebSocketSession` is not safe for concurrent sends, so
concurrent publishing to one client needs `ConcurrentWebSocketSessionDecorator` with send
limits or you get an `IllegalStateException` under load. And every connection pins a client to
one instance, so a load balancer needs sticky sessions and rolling deploys drop connections —
the client must reconnect and resubscribe on its own.

### Spring, Guice, or Jakarta CDI
**Short:** The JVM's DI containers; constructor injection makes the abstraction the only thing a class names.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/design-patterns-and-principles @2

The three differ mainly in when and how binding happens. Spring builds bean definitions from
annotations, configuration classes and starters, and resolves largely by type with qualifiers
and a rich scope and lifecycle model. Guice binds explicitly in `Module` classes, so the
wiring is code you read in one place and errors are found when the injector is created. CDI is
the Jakarta standard, resolving by type plus qualifier annotations with interceptors,
decorators and an events mechanism defined by specification.

The thing all three make possible is the same and is what matters: a class declares its
collaborators as constructor parameters typed as interfaces, so the concrete implementation is
chosen outside it and a test supplies a different one by construction. The shared cost is that
wiring errors move to startup rather than compile time — Guice's explicit modules and
compile-time containers such as Dagger and Micronaut trade flexibility to move them back.

### spring-aspects
**Short:** Spring's AspectJ weaving module, needed for @Configurable domain objects and self-invocation-proof aspects.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1

This module contains real AspectJ aspects, pre-compiled, for the cases proxies cannot reach.
`AnnotationBeanConfigurerAspect` backs `@Configurable`, which injects dependencies into
objects created with `new` — a JPA entity or a domain object the container never sees — and
the module also provides AspectJ-mode implementations of transaction and cache advice, which
apply to self-invocations and to non-public methods because the advice is woven into the
bytecode rather than wrapped around it.

Using it means enabling weaving: load-time weaving with the AspectJ agent, or compile-time
weaving in the build. That is the cost, and it is not small — an extra agent or build plugin
in every environment, slower startup or compilation, and behaviour that no longer corresponds
to anything visible in the source. Reach for it only when the proxy limitation genuinely
blocks the design; most teams restructure instead.

### spring-batch-core
**Short:** Spring Batch's Job/Step chunk model with a JobRepository for restart, skip and retry of long-running batch work.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2, data-movement/batch-and-distributed-compute @3

This artifact holds the domain and the runtime: `Job`, `Step`, `JobLauncher`,
`JobExecution`/`StepExecution`, the listener interfaces, and above all the `JobRepository` and
its schema. It owns the semantics that distinguish batch from a loop — a `JobInstance` is
identified by job name plus parameters, so the same instance cannot complete twice and a
restart resumes the existing execution using the persisted step state.

Depending on it means you have accepted the metadata tables, and they are the thing to plan
for: they need to exist in every environment, they grow without a purge strategy, and they
must be on a data source the job can reach transactionally. The counterpart artifact,
`spring-batch-infrastructure`, supplies the readers and writers; this one supplies the
execution model around them.

### spring-batch-infrastructure
**Short:** Spring Batch's infrastructure module: ItemReader/ItemWriter implementations, chunk processing, retry and skip policies.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/batch-and-distributed-compute @2, data-movement/task-queue-and-jobs @3

The reusable pieces live here rather than in core: the item interfaces and their concrete
implementations — flat file, XML, JSON and JDBC cursor or paging readers, JPA and Hibernate
readers, delimited and fixed-width line mappers, corresponding writers — plus the retry and
repeat templates the chunk loop is built from. It has no dependency on the job and step model,
so the retry support in particular is usable on its own.

The details that decide whether a job scales are all here. A cursor reader holds a single
connection and result set open for the duration, which is fast but fragile over a long run,
while a paging reader issues a query per page and needs a stable sort or it will skip and
duplicate rows. Stateful readers must be declared `@StepScope` to be restartable, and a writer
that does not batch its statements will dominate the runtime.

### spring-beans jar
**Short:** The Spring module holding BeanFactory, BeanDefinition and BeanPostProcessor - the core of the IoC container.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, runtime-systems/runtime-internals-and-types @3

This is the IoC container proper, one layer below the application context. A `BeanDefinition`
is metadata — class, scope, constructor arguments, property values, lifecycle callbacks — and
`BeanFactory` is what instantiates and wires beans from those definitions. The extension
points that make Spring extensible live here too: `BeanFactoryPostProcessor` can rewrite
definitions before anything is created, and `BeanPostProcessor` can wrap an instance after
construction, which is precisely how AOP proxies and `@Autowired` injection are implemented.

Knowing this layer exists is what makes several confusing behaviours legible: why a
`BeanPostProcessor` bean is created very early and therefore cannot be advised itself, why
placeholder resolution happens before instantiation, and why the object you get injected may
be a proxy rather than your class. It is also the seam for registering beans programmatically
when annotations cannot express what you need.

### spring-boot-configuration-processor
**Short:** Annotation processor generating metadata from @ConfigurationProperties for IDE autocompletion.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/compiler-toolchain-and-codegen @2

It is an annotation processor, not a runtime library: at compile time it finds
`@ConfigurationProperties` classes and writes `META-INF/spring-configuration-metadata.json`
describing every property, its type, its default and its Javadoc. IDEs read that file to offer
completion, type checking and inline documentation while editing `application.yml`, and a
companion file can mark properties deprecated with a replacement.

Add it with `optional` or `provided` scope so it does not leak into the runtime classpath of
consumers. The two things that catch people out are that Javadoc on the field is what becomes
the description, so undocumented properties give a bare completion with no help, and that with
Lombok the processor ordering matters — if Lombok has not generated the accessors when this
processor runs, the metadata comes out empty and the only symptom is that completion silently
stops working.

### spring-boot-starter-batch
**Short:** Spring Boot starter pulling in Spring Batch core and infrastructure for chunk-oriented batch jobs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, data-movement/task-queue-and-jobs @2

The starter brings in Spring Batch and its auto-configuration, which does three things worth
knowing: it creates a `JobRepository` against the application's `DataSource`, it can
initialize the batch schema for you through a property, and it can run jobs on startup. That
last behaviour is the one that surprises people — the application boots, the job runs, and in
a web application it runs on every deployment unless you disable it.

Decide explicitly on all three. Point the repository at the right data source, since sharing
the business database gives you transactional restart state but couples the two; switch schema
initialization off in production and manage the tables with your migration tool; and control
job launching deliberately rather than relying on startup, especially if the same artifact
runs as both a service and a job. A job launched from a web request also needs an asynchronous
launcher, or the HTTP thread waits for the whole run.

### spring-boot-starter-hateoas
**Short:** Spring Boot starter pulling in Spring HATEOAS and HAL rendering for hypermedia-driven REST responses.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

The starter brings in Spring HATEOAS and switches the default representation to HAL, so
responses gain a `_links` object. The model types are the substance: `EntityModel` wraps a
payload with links, `CollectionModel` and `PagedModel` do the same for collections including
pagination links, and `WebMvcLinkBuilder.linkTo(methodOn(Controller.class).method(args))`
builds a URI by pointing at the controller method rather than by string concatenation, so a
mapping change cannot leave a stale link behind.

Reach for it when clients should discover available transitions instead of hardcoding URL
templates. The costs are real friction: every response needs assembling into a model, which is
either boilerplate or a `RepresentationModelAssembler` per type; the HAL envelope changes the
JSON shape, which breaks existing consumers; and almost no client library follows links
automatically, so unless a consumer is written for it you pay the cost without collecting the
benefit.

### spring-boot-starter-restclient
**Short:** Spring Boot starter pulling in RestClient and its auto-configuration without dragging in an embedded web server.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, devtools/build-and-dependency-management @3

`RestClient` is the modern synchronous HTTP client in Spring: a fluent API in the shape of
`WebClient` but blocking, so it reads like the reactive client without requiring a reactive
stack, and it is the intended replacement for `RestTemplate`. This starter packages it and its
auto-configuration on its own, so a service that only makes outbound calls does not have to
pull in an embedded server as a side effect of wanting an HTTP client.

Reach for it in a non-web application — a batch job, a worker, a CLI — or wherever the
previous route of depending on the web starter dragged in machinery you did not want. The
choices it leaves are the important ones: pick and configure the underlying request factory,
because the default JDK client's timeouts and connection pooling behave differently from
Apache's, and set timeouts explicitly since an unset one means waiting forever.

### spring-boot-starter-rsocket
**Short:** Spring Boot starter auto-configuring an RSocket server and RSocketRequester.Builder for reactive messaging.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

It pulls in `rsocket-core`, the Netty transports and Spring's messaging integration, then
autoconfigures both directions. `@MessageMapping` methods on a `@Controller` handle routes,
with the return type deciding the interaction model — `Mono` for request-response, `Flux` for
request-stream — and an `RSocketRequester.Builder` is injected for calling out. Properties
choose whether the server listens on its own TCP port or is mapped onto the existing WebSocket
endpoint.

Reach for it when a Spring service wants streaming with real backpressure, particularly for
server-push to browsers. The costs are that the routing metadata and its MIME types are a
concept your team has to learn, that Spring Security's RSocket support is a separate
configuration model from the HTTP one, and that almost no infrastructure between the two
endpoints understands the protocol. For plain request-response, an HTTP client is less to
operate.

### spring-boot-starter-validation
**Short:** Spring Boot starter pulling in Hibernate Validator so Jakarta Bean Validation annotations take effect.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

It exists because the web starter stopped including validation transitively, so `@NotNull` and
friends compile fine against the API but silently do nothing until this starter puts an
implementation on the classpath. Once present, Spring wires a `Validator` bean and `@Valid` on
a controller argument triggers validation, with failures surfacing as a
`MethodArgumentNotValidException` translated into a 400 response.

The distinction to keep straight is where the validation happens. `@Valid` on a handler
parameter is Spring MVC's argument resolution; `@Validated` on a class enables method
validation through an AOP proxy, which means it also applies to service beans — and, because
it is proxy-based, it does not apply to a self-invocation. Constraint violations from method
validation surface as a different exception and become a 500 unless you handle them
explicitly.

### spring-boot-starter-webclient
**Short:** Spring Boot 4 starter bringing WebClient and its reactive HTTP stack without also pulling in a web server.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/dependency-injection-and-config @3

`WebClient` is the non-blocking client from the reactive stack, returning `Mono` and `Flux`,
and historically the only way to get it was to depend on the WebFlux starter — which also
started a Netty server unless you suppressed it. Packaging the client separately removes that
side effect, so a servlet-stack application can use the reactive client for outbound fan-out
without becoming a reactive application.

Reach for it when you need streaming responses, real backpressure, or high-concurrency
parallel calls that a thread-per-request client would need a large pool to match. The costs
are that mixing paradigms is where bugs live — calling `.block()` on the result inside a
servlet handler works but discards most of the benefit, and doing it on an event-loop thread
deadlocks — and that the Reactor Netty connection pool needs deliberate sizing and timeout
configuration. For ordinary blocking calls, `RestClient` is simpler.

### spring-boot-starter-websocket
**Short:** Boot starter dependency pulling spring-websocket and spring-messaging in for both raw WebSocket and STOMP APIs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/build-and-dependency-management @3

The starter is the dependency step, not a feature: it brings in `spring-websocket` and
`spring-messaging` alongside the web starter, which is what makes `@EnableWebSocket`,
`@EnableWebSocketMessageBroker` and the `@MessageMapping` annotations resolvable. It
configures nothing on its own — without a `WebSocketConfigurer` or
`WebSocketMessageBrokerConfigurer` bean declaring endpoints, adding it changes no behaviour.

The choices it leaves you are the ones that matter. Decide between raw frames and STOMP;
decide between the in-memory simple broker, which does not span instances, and a broker relay
to RabbitMQ or ActiveMQ; and remember that the servlet container must support the upgrade,
which the embedded ones do. On the reactive stack, WebFlux has its own WebSocket support and
this starter is not the right entry point.

### spring-cloud-config-server
**Short:** Serves versioned external configuration from Git or Vault over HTTP to Spring clients; enabled with @EnableConfigServer.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

Annotating an application `@EnableConfigServer` turns it into an HTTP endpoint over a backend
— usually a Git repository — that serves properties addressed as
`/{application}/{profile}/{label}`, merging the shared `application.yml` with the
service-specific file and the profile-specific one in a defined order. Because the backend is
Git, every configuration change has an author, a diff and a revert, and `label` maps to a
branch or tag so a deployment can be pinned to a specific revision. Values prefixed `{cipher}`
are decrypted by the server using a key the clients never see.

Reach for it when many services share configuration and you want change history and review on
it. The costs are that it becomes a startup dependency for every client, so it needs to be
highly available or clients need a cached fallback; that decryption centralizes a key worth
protecting; and that Git is not a secret store — for real secrets, back it with Vault.

### spring-cloud-starter-bus-kafka
**Short:** Spring Cloud Bus over Kafka: broadcasts config-refresh and management events to every instance.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, data-movement/message-broker @2, data-movement/event-streaming-and-processing @3

Refreshing configuration one instance at a time does not scale, so the bus links every
instance over a shared Kafka topic: a POST to `/actuator/busrefresh` on any one of them
publishes a remote application event that all the others consume and act on, rebuilding their
`@RefreshScope` beans. Events carry an origin service id and an optional destination pattern,
so a refresh can be targeted at one service or one instance rather than the whole estate.

Reach for it when a fleet is large enough that scripted per-instance refresh calls have become
the problem. The costs are a hard dependency on Kafka for a control-plane function, an
actuator endpoint that must be secured because it can be triggered by anyone who reaches it,
and the fact that a broadcast refresh means every instance rebuilds beans and reconnects
downstream resources at the same moment. Staggering matters for a large fleet.

### spring-cloud-starter-config
**Short:** Spring Cloud Config client starter that fetches externalized configuration from a config server at startup.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, platform-delivery/infrastructure-as-code-and-config @3

The client fetches configuration from the config server before the main application context is
built, using the service's name, active profiles and label to construct the request, so the
retrieved properties are available to everything including auto-configuration. `fail-fast`
decides whether an unreachable server aborts startup or the application continues with local
defaults, and a retry policy can be layered on so a server restarting at the same moment does
not take every client down with it.

The behaviour to plan around is refresh. `@RefreshScope` beans are discarded and rebuilt when
`/actuator/refresh` is called, but plain `@Value` fields in ordinary singletons are not
re-read, and neither is anything captured at construction — so which beans actually pick up a
change is a design decision, not a given. Fetching at boot also means a service now fails to
start if the config server is down, which is why `fail-fast` and retry deserve deliberate
settings.

### spring-cloud-starter-openfeign
**Short:** Declarative HTTP client defined as an annotated interface, integrated with load balancing and Resilience4j.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/service-mesh-and-discovery @3

A client is a Java interface annotated with request mappings; at startup a proxy is generated
that turns a method call into an HTTP request, encoding arguments and decoding the response
with the application's message converters. Because the client name resolves through Spring
Cloud LoadBalancer, `http://orders/...` is a logical service rather than a host, and
Resilience4j integration adds circuit breaking and fallbacks per client.

Reach for it in an existing Spring Cloud estate where declarative clients and service
discovery are already the pattern. For new work, note that Spring Framework's own HTTP
interface clients — an annotated interface backed by `RestClient` or `WebClient` — cover the
same declarative shape without the extra dependency. Feign's costs are its own configuration
model layered beside Spring's, error handling that hides the response body behind a
`FeignException` unless you add a decoder, and defaults with no useful timeout.

### spring-context jar
**Short:** The Spring module providing ApplicationContext, bean lifecycle, events, scheduling and annotation-driven configuration.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1

`ApplicationContext` extends the bare bean factory with the things an application needs:
internationalization, an event publisher and listeners, resource loading, and the
annotation-driven configuration model — component scanning, `@Configuration`, `@Bean`,
`@Import`, `@Conditional` — plus the abstractions for scheduling with `@Scheduled` and async
execution with `@Async`. Refresh is the lifecycle that matters: definitions are loaded and
post-processed, then all singletons are instantiated eagerly.

That eager instantiation is why a wiring mistake fails at startup rather than at first
request, which is the behaviour you want. The costs to know are that the context is heavy to
build, so a test that constructs a distinct one per class is the usual reason a suite is slow
— Spring's context caching depends on the configuration being identical — and that
`@Scheduled` and `@Async` are proxy-based, so a self-invocation silently runs inline.

### spring-context-indexer
**Short:** Spring annotation processor that writes a compile-time component index, removing classpath scanning at startup.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, devtools/compiler-toolchain-and-codegen @2, observability/profiling-and-performance @3

Add it as an annotation processor and it writes a `META-INF/spring.components` index at compile time listing every class in that module carrying a stereotype annotation; at startup Spring reads the index instead of scanning the classpath for candidates. The saving is proportional to the scanning avoided -- a large application with wide component-scan base packages and many jars benefits, a small one barely notices -- and it only covers modules compiled with the processor, so a dependency without it is still scanned normally. Its limitation is that it indexes annotated components only: custom scan filters and other classpath-scanning mechanisms are unaffected. Reach for it when startup time matters and profiling actually points at scanning; on a modern Spring Boot application, AOT processing and native images address the same cost far more thoroughly.

### spring-core jar
**Short:** Spring's foundation module: Resource loading, the type conversion service and shared utility classes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/dependency-injection-and-config @1, apis-frameworks/data-formats-and-api-contracts @3

It is the module with no Spring dependencies of its own, holding the abstractions everything
above reuses: the `Resource` abstraction, which is why `classpath:`, `file:` and `http:` URLs
work identically wherever a location string is accepted; the `ConversionService` and
`PropertyEditor` machinery that turns a configuration string into a typed value; `Environment`
and property source plumbing; annotation and reflection utilities; and the `ResolvableType`
model that lets the container reason about generics.

You never depend on it deliberately — it arrives with everything — but it explains behaviour
that otherwise looks like magic, notably how a property string becomes a `Duration`, a
`DataSize` or an enum, and how generic injection points like `Map<String, Handler>` are
resolved. Registering a custom `Converter` is the supported way to extend that, and it is
usually the right fix when a configuration value will not bind.

### spring-grpc-client-spring-boot-starter
**Short:** Official Spring gRPC starter auto-configuring managed channels and injectable stubs via GrpcChannelFactory.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

The client-only slice. `GrpcChannelFactory` builds named `ManagedChannel` instances from
`spring.grpc.client.channels.<name>.*` properties, and stubs are injected from those channels,
so the address, TLS settings, deadlines, retries and keep-alive of every downstream service
are declared in configuration rather than constructed in code. Client interceptor beans apply
to the channels, which is where Micrometer tracing and metrics attach.

Reach for it in a service that only consumes gRPC — a web frontend calling backend services.
Channels are long-lived and expensive, so the factory's job is really to stop each caller
creating its own; the failure mode when they do is connection churn and blown file-descriptor
limits. Set a default deadline in configuration, because a gRPC call without one has no
timeout at all.

### spring-grpc-server-spring-boot-starter
**Short:** Official Spring gRPC starter auto-configuring a gRPC server bound to spring.grpc.* properties on Boot 3.x.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @2

The server-only slice of Spring gRPC, for a process that serves gRPC but never calls it. It
starts an embedded server, registers every `@GrpcService` bean and any `ServerInterceptor`
beans in order, and exposes the standard health and reflection services so probes and
`grpcurl` work without extra code. Server settings — port, message size limits, keep-alive,
TLS — come from `spring.grpc.server.*`.

Taking only this artifact keeps a channel factory and its Netty client machinery out of a
process that has no use for them, which matters for startup time and native images. The
operational notes are the usual ones: run gRPC on its own port and remember it in probes and
network policy, translate exceptions centrally so clients see real status codes, and decide
explicitly whether reflection stays enabled outside development.

### spring-grpc-spring-boot-starter
**Short:** Official Spring starter that auto-configures a gRPC server and channels for Spring Boot services.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/dependency-injection-and-config @3

This is the Spring team's own gRPC integration, the successor to the community starters. It
autoconfigures both halves from `spring.grpc.*` properties: `@GrpcService` beans are
registered with an embedded server, and a `GrpcChannelFactory` builds named channels whose
configuration lives beside the rest of the application's properties, with observability wired
through Micrometer rather than bolted on.

Reach for it for anything new on Boot 3.x and later, because it tracks Boot's release train
and its Spring Boot integration — testing support, actuator health, native image hints — is
maintained together with the framework. The migration cost from the `net.devh` starters is
mostly mechanical but real: the property namespace, the annotation package and the interceptor
registration model all differ, so plan it as a change rather than a dependency swap.

### spring-messaging
**Short:** Spring's messaging abstraction: @MessageMapping, channels and SimpMessagingTemplate behind STOMP/WebSocket.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, data-movement/message-broker @2

It is the transport-neutral messaging abstraction the WebSocket, STOMP and RSocket stacks all
sit on: a `Message` of payload plus headers, `MessageChannel` and `MessageHandler` for moving
it, and the annotation model — `@MessageMapping`, `@SendTo`, `@Payload`,
`@DestinationVariable` — that maps destinations onto methods the same way `@RequestMapping`
maps URLs. Outbound, `SimpMessagingTemplate` publishes to a destination or to one user's queue
from anywhere in the application.

You rarely add it deliberately; it arrives with the WebSocket or RSocket starter. The thing
worth knowing is where the boundaries are: destinations are strings with no compile-time
checking, so a typo simply means no messages arrive, and messages flow through executor-backed
channels whose thread pools are separate from the servlet container's and need sizing when the
volume is real.

### spring-modulith-core
**Short:** Spring Modulith's core artifact: declares application modules, verifies their boundaries and wires module events.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @2, apis-frameworks/aop-middleware-and-scheduling @3

Modules are derived from package structure: a direct sub-package of the application's main
package is a module, its own sub-packages are internal, and only types in the module's
top-level package — or in a package marked as a named interface — may be referenced from
outside. `ApplicationModules.of(App.class).verify()` in a test asserts that, catching a cycle
or a reach into another module's internals at build time. On top of that,
`@ApplicationModuleListener` makes an event listener transactional and asynchronous by
default, so modules integrate through published events rather than direct calls.

Reach for it to keep a modular monolith modular, and as a staging post before extracting
services. The costs are that module boundaries become package layout, so restructuring is a
real refactor; that the event-based integration changes transaction and failure semantics
between modules, which is a design shift rather than a rename; and that the event publication
registry needs its own table if you want at-least-once delivery.

### spring-modulith-docs
**Short:** Spring Modulith module that generates architecture documentation and C4 diagrams from the verified module structure.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, devtools/static-analysis-and-linting @2

`Documenter` takes the verified module model and writes documentation from it: C4-style
component diagrams as PlantUML for the whole application and for each module, and a module
canvas — a table per module listing its exposed types, dependencies, published and consumed
events, and configuration properties. Because it runs as a test, the output regenerates on
every build and cannot describe a structure the code no longer has.

Reach for it when architecture documentation exists but is stale, which is the normal state —
the point is that a diagram derived from the code is worth more than a hand-drawn one that was
accurate a year ago. The costs are that it documents structure only, so intent, rationale and
the reasons behind the boundaries still have to be written by a human; that the output needs a
place to be published or nobody reads it; and that the diagrams need PlantUML rendering in the
docs toolchain.

### spring-websocket
**Short:** Spring's servlet-stack WebSocket module: WebSocketHandler, STOMP messaging and SockJS fallback transports.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

This is Spring's servlet-stack WebSocket support. At the low level you register a `WebSocketHandler` through `WebSocketConfigurer` and deal in raw text and binary frames; at the messaging level you enable STOMP over WebSocket and then work in familiar Spring terms — `@MessageMapping` methods, a destination hierarchy, and `SimpMessagingTemplate` to push to a topic or to one user.

The broker behind those destinations is either the built-in simple in-memory one, which is fine for a single node, or an external relay to RabbitMQ or ActiveMQ, which is what lets several app instances share subscriptions. SockJS fallback transports cover clients or proxies that refuse the upgrade. Note this is the servlet stack only — WebFlux has its own reactive equivalent.
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

It builds the document at runtime by inspecting the live Spring context: request mappings
become paths, method parameter and return types become schemas, Bean Validation annotations
become schema constraints, and Spring Security configuration informs the security schemes.
`@Operation`, `@Schema` and friends fill in what cannot be inferred, mainly descriptions and
examples. The document is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

Reach for it in a code-first Spring project; it is the maintained successor to Springfox,
which is abandoned. The costs are that inference has limits — generic wrappers,
`ResponseEntity<?>` and polymorphic responses need explicit annotations or they document as
`Object` — that scanning adds startup cost worth disabling in production, and that a
runtime-generated document cannot gate a build. For contract-first workflows, write the spec
and generate interfaces from it instead.

### SSE
**Short:** Server-Sent Events: a one-way streaming HTTP transport where the server pushes text events to the browser.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1

Mechanically it is one HTTP response that never ends: the server sets `Content-Type:
text/event-stream`, disables buffering and compression, and writes blocks separated by blank
lines as they become available. Nothing about it is new protocol machinery, which is the
appeal — the same TLS termination, gateway, authentication and observability that handle every
other request handle this one, and there is no upgrade for a proxy to refuse.

Reach for it whenever the data only flows one way. The failure modes worth pre-empting are all
about intermediaries and lifetimes: an nginx or CDN layer that buffers will deliver nothing
until the stream closes, an idle-timeout on a load balancer will cut a quiet stream, and a
crashed server produces a client-side reconnect storm. Send a periodic comment as a heartbeat
and cap how long any one stream lives.

### sse-starlette
**Short:** Starlette/FastAPI add-on providing EventSourceResponse for server-sent events with keepalive and reconnect headers.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

Return an `EventSourceResponse` wrapping an async generator that yields dicts or `ServerSentEvent` objects and it handles the `text/event-stream` wire format - the `event:`, `data:`, `id:` and `retry:` framing, periodic keepalive comments so an idle connection is not reaped by a proxy, and client-disconnect detection so the generator stops producing work nobody will read.

It is the low-ceremony way to stream LLM tokens or long-running task progress out of FastAPI. SSE is one-way server-to-client over ordinary HTTP, so it passes through proxies and load balancers with no protocol upgrade and the browser's `EventSource` reconnects on its own - a much smaller commitment than WebSockets when the client only needs to listen. The one thing to remember at deploy time is to disable proxy response buffering, or every chunk arrives at once when the stream ends.

### Starlette
**Short:** Lightweight ASGI toolkit FastAPI is built on: routing, request/response lifecycle, WebSockets and streaming responses.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/rpc-graphql-and-streaming @2

It supplies the HTTP machinery that FastAPI builds on: routing, `Request` and `Response` objects, a middleware chain, background tasks, WebSocket and `StreamingResponse` primitives, static files, sessions, and a test client — everything except the type-driven validation, dependency injection, and OpenAPI generation FastAPI adds. It speaks ASGI, so a `def` endpoint is offloaded to a thread pool while an `async def` one runs on the event loop, and `request.state` is the per-request scratch space FastAPI dependencies pass context through.

Reach for Starlette directly for a small service, a proxy, or a websocket endpoint where validation buys you nothing and you want fewer moving parts. Know it either way: when a FastAPI question is about the request lifecycle, middleware ordering, or streaming, the answer lives in Starlette.

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

Each state becomes an object implementing a shared interface, the context delegates every
operation to its current state object, and a transition is the context swapping that
reference. The behaviour difference is therefore resolved by dispatch rather than by a
conditional, so the long `switch (status)` that appeared in five methods disappears — and each
state class is where you read what that state can and cannot do.

Reach for it when behaviour genuinely differs across many operations per state and the
transition graph is non-trivial. The costs are a class per state and, more insidiously, the
transition rules scattering across those classes until no single place shows the machine. For
a handful of states, an enum with per-constant bodies or an explicit transition table is less
code; for anything long-running that must survive a restart, a state-machine framework with
persistence.

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

The platform combines three things around an OpenAPI document: a visual editor so a spec can
be authored without hand-writing YAML, Spectral, its open-source style linter that enforces
rulesets such as naming conventions and required descriptions in CI, and hosted documentation
plus a mock server generated from the same file. The design-first workflow is the intent —
agree the contract, lint it, publish it, and only then implement.

Reach for it when an organization has enough APIs that consistency between them is a real
problem and a governance owner exists. The costs are that it is a commercial hosted product
with a paid tier for private work, and that design-first only holds if the implementation is
actually verified against the spec — otherwise you have beautifully linted documentation that
lies. Spectral alone is free and gives most of the governance value without the platform.

### Stoplight Studio
**Short:** Visual OpenAPI editor for designing and linting an API contract before any implementation exists.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, devtools/version-control-and-workbench @3

The editor's premise is that most people should not hand-edit OpenAPI YAML: forms describe
operations, parameters and schemas, the underlying document is written for you, and a live
preview plus a generated mock server lets a consumer try the API before any code exists.
Spectral linting runs as you type, so style violations surface at authoring time rather than
in CI.

Reach for it when the people who should own the contract — product, front-end, an API design
group — are not the ones comfortable in a YAML file. The costs are that the generated document
reflects the tool's conventions and can produce noisy diffs against a hand-maintained file,
and that the visual layer cannot express everything the specification can, so complex schemas
eventually need the source view. Note also that Stoplight has consolidated its tooling over
time, so check what is current before standardizing on the desktop editor.

### Strategy
**Short:** GoF pattern swapping an algorithm behind an interface; in Java it is Comparator, lambdas or a Map of beans.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

The algorithm is behind an interface and the object holding it does not know which
implementation it has. In modern Java the interface is usually functional, so a strategy is a
lambda or a method reference and `Comparator` is the one everybody already uses; in Spring the
container injects `Map<String, Strategy>` keyed by bean name, so the strategy set is whatever
is on the classpath.

Reach for it to dissolve a conditional that keeps growing as new cases arrive. The honest
caveat is that the conditional usually does not vanish, it relocates to whoever picks the
strategy — a keyed map or an enum makes that selection explicit and data-driven instead of
buried. If the algorithm never varies at runtime, the interface is pure ceremony and a plain
method is better.

### Strawberry
**Short:** Code-first Python GraphQL library that derives the schema from dataclass-style type annotations.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, apis-frameworks/web-framework-and-http-client @3

Types are ordinary Python classes decorated with `@strawberry.type`, and the schema is derived
from their annotations — a field's GraphQL type comes from its Python type hint, resolvers are
methods, and dataclass semantics apply — so there is no SDL file to keep in sync and mypy sees
the same types the schema does. Integrations ship for FastAPI, Django, Flask and others, and
subscriptions are async generators.

Reach for it when a Python service should expose GraphQL and the team already thinks in type
hints; it is the closest analogue to what FastAPI did for REST. The costs are that the schema
is a build output, so publishing SDL for consumers and for schema checks is an extra step, and
that the N+1 problem is not solved for you — nested resolvers need explicit dataloaders. For a
schema-first workflow shared with other languages, Ariadne is the alternative.

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

It is a JavaScript application that fetches an OpenAPI document and renders it as an
expandable list of operations, each with its parameters, schemas and example values, plus a
"Try it out" form that issues the real request from the browser. That live-call feature is its
distinguishing property and its main operational consideration, since the request comes from
the user's browser and is subject to CORS, authentication and whatever network the page is
loaded from.

Reach for it for internal and developer-facing APIs where being able to exercise an endpoint
without a client is worth more than polish. The costs are that it exposes your full API
surface wherever it is deployed, so it should be disabled or protected in production; that
large documents render slowly; and that it is optimized for trying calls rather than for
reading — for reference documentation people read end to end, Redoc's layout is better.

### Template Method
**Short:** Behavioral pattern: a base class fixes the algorithm skeleton and subclasses fill in the varying steps.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

The base class owns the sequence in one method — which should be `final` — and calls protected
hooks that subclasses fill in, so the invariant order is enforced by the framework and only
the varying steps are open. It is inheritance-based inversion of control:
`HttpServlet.service` dispatching to `doGet`, or a base test class fixing setup, act and
teardown.

Reach for it when the sequence is genuinely fixed and only steps vary. The costs are the ones
inheritance always brings: a subclass has one superclass slot and is coupled to protected
internals that were never designed as a public API, and if the template method is not `final`
a subclass can override the algorithm itself and break the invariant silently. Composing
strategies passed to a single concrete class achieves the same result without the hierarchy,
which is why frameworks increasingly prefer it.

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

The connector accepts connections on a small acceptor pool and hands each request to a worker
from a pool capped by `maxThreads`, whose default is 200; `maxConnections` bounds how many
sockets may be open at once and `acceptCount` is the OS backlog for what is waiting beyond
that. So concurrency is bounded by threads, and a request that blocks on a slow downstream
call occupies its worker for the whole wait — which is why a single slow dependency exhausts
the pool and takes down endpoints that have nothing to do with it.

It is the default embedded container in Spring Boot and the safe choice. Size `maxThreads`
against the downstream concurrency the service can actually sustain rather than raising it
reflexively, since more threads against a saturated database only moves the queue. On modern
JDKs, enabling virtual threads changes the calculus, replacing the bounded pool with a
per-request virtual thread and removing the classic exhaustion mode.

### Tomcat/Jetty
**Short:** The embedded servlet containers Spring Boot runs on; Boot 4 requires a Servlet 6.1 baseline.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @3

The choice matters less than it appears, because Spring Boot abstracts the container behind
`ServerProperties` and swapping one for the other is a dependency exclusion plus a starter.
Both are thread-per-request servlet containers with a bounded worker pool, both embed as a
library rather than hosting a deployed archive, and both support HTTP/2 and WebSocket. Tomcat
is the default and the more widely deployed; Jetty is more modular and lighter to embed.

What actually determines throughput is the same for both: the worker pool size versus the
concurrency your downstream dependencies can absorb, and whether any handler blocks. Undertow
is the third option in the same family. If the workload is a very large number of mostly idle
connections or streaming responses, no servlet container is the right answer and the reactive
stack is — though virtual threads have narrowed that gap considerably.

### tools.jackson.core:jackson-core
**Short:** Jackson 3's streaming core: JsonParser, JsonGenerator and the token factory the databind layers build on.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

Jackson 3 moved to a new Maven coordinate and a new `tools.jackson` package root so it can sit
on a classpath beside Jackson 2 without conflict — the annotations, notably, stayed under the
old `com.fasterxml.jackson.annotation` package so existing annotated classes carry over. This
artifact is the streaming layer: the token-level parser and generator, the factory that
creates them, and the buffer recycling underneath.

Use it directly when you are processing documents too large to hold in memory, or writing a
custom format module. The migration cost from Jackson 2 is the part to plan for: package
renames across every import, checked `JsonProcessingException` becoming an unchecked
`JacksonException`, and several defaults changing. Mixing 2 and 3 in one application works but
means two independent mapper configurations to keep aligned.

### tools.jackson.core:jackson-databind
**Short:** Jackson 3's data-binding artifact providing ObjectMapper/JsonMapper, POJO binding and the tree model.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1

This is where reflection-driven object mapping lives. In Jackson 3 the mapper is immutable and
built once — `JsonMapper.builder()...build()` — rather than mutated after construction, which
removes the long-standing hazard of one component reconfiguring a shared `ObjectMapper` and
changing behaviour for everything else in the process. Registering modules, changing inclusion
and naming strategies, and enabling features all happen on the builder.

Reach for it for ordinary POJO binding; it pulls in the streaming core transitively. The costs
are that the immutable mapper means a component wanting different settings must build its own
via `rebuild()`, and that databind is where deserialization security lives — polymorphic type
handling must be explicit and restricted rather than open, whatever the major version.

### tools.jackson.module:jackson-module-kotlin
**Short:** Jackson module teaching the mapper about Kotlin data classes, nullability and default constructor arguments.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/runtime-internals-and-types @3

Without it Jackson sees a Kotlin data class through the Java reflection lens: parameter names
are gone unless the compiler kept them, there is no no-arg constructor, and `null` is happily
written into a non-nullable field, producing an object that violates its own type at some
later line. The module reads Kotlin metadata to bind constructor parameters by name, respect
default argument values for absent properties, and fail deserialization when a non-nullable
property would be null.

Register it on any mapper that touches Kotlin types — Spring Boot does so automatically when
it is on the classpath. The costs are that its Kotlin reflection has a measurable startup and
first-call cost, and that it does not make everything work: value classes, sealed hierarchies
and generic inline types still need explicit configuration or custom serializers.

### Traverson
**Short:** Spring HATEOAS Java client that walks a hypermedia API by following link relations rather than hardcoded URLs.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/web-framework-and-http-client @1, apis-frameworks/data-formats-and-api-contracts @2

The client is built around following relations instead of constructing URLs: point it at an
entry point, then `follow("orders", "self")` and it fetches each step, reads the returned
document's `_links`, and resolves the next relation from there. Templated links can be
expanded with parameters, and `toObject` accepts a JSONPath expression when you want a value
rather than a deserialized type.

Reach for it when consuming an API that genuinely evolves its URLs and treats hypermedia as
the contract — the point is that the client survives a URL restructuring the server did not
announce. The costs are that every hop is a real HTTP round trip, so a three-step traversal is
three requests where a known URL is one, and that the loose typing means a renamed relation
fails at runtime. For a stable internal API, a typed HTTP interface client is faster and
safer.

### ujson
**Short:** C-backed JSON serializer, a near drop-in replacement for the stdlib json module at roughly 2-3x the speed.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, observability/profiling-and-performance @3

It is a C extension exposing `dumps` and `loads` with roughly the standard library's
signature, gaining its speed by doing the parsing and formatting in C with fewer of the
standard module's configuration hooks. The gaps are what matter in practice: no `cls` argument
for a custom encoder, `default` support arriving late, and a history of divergences from
strict JSON in how it handles very large integers, floats and invalid surrogates.

It is a reasonable drop-in when you are on an older codebase and want an easy speedup. For new
work, `orjson` is the better choice — it is faster still, serializes `datetime`, `UUID`,
dataclasses and numpy arrays natively, and is stricter about the specification — with the one
caveat that it returns `bytes` rather than `str`, which occasionally makes `ujson` the smaller
change.

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

Everything is an exchange — a small middleware in a pipeline the operation flows through — so
caching, deduplication, retries, authentication and persisted queries are each a composable
unit you add rather than framework behaviour you configure. The default cache is a document
cache keyed on the query and variables, which invalidates by `__typename` after a mutation:
crude compared with normalization, but it needs no configuration and is right often enough.
Normalized caching is opt-in through the Graphcache exchange.

Reach for it when Apollo Client's size and cache configuration are more than the application
needs. The trade is exactly that document cache: a mutation that changes an entity without
touching a queried type will not invalidate the right things, so you either accept refetching
or adopt Graphcache and are back to writing update logic. The ecosystem is also smaller.

### uvicorn
**Short:** ASGI server that runs FastAPI/Starlette apps, including WebSocket upgrades and multi-worker production serving.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/web-framework-and-http-client @1, runtime-systems/concurrency-and-async @2, apis-frameworks/rpc-graphql-and-streaming @3

It implements ASGI over `httptools`/`h11` on an asyncio loop, `uvloop` where available, so a coroutine handler can hold thousands of open connections and handle the WebSocket upgrade that WSGI cannot even express. The thing that surprises people is that one Uvicorn worker is single-threaded: CPU-bound work inside a handler blocks every other connection on that process, so parallelism comes from `--workers` or from one process per container with the orchestrator replicating it, never from the event loop itself. Use it as the default runner for FastAPI or Starlette, and keep a real reverse proxy in front for TLS termination and slow-client buffering.

### Vert.x event bus
**Short:** Vert.x's address-based in-process message bus with point-to-point and pub/sub modes, clusterable across nodes.
**Kind:** tech
**Lang:** java
**Roles:** apis-frameworks/design-patterns-and-principles @1, data-movement/message-broker @2, runtime-systems/concurrency-and-async @3

Handlers register against a string address and messages are sent to that address, with `send`
picking one handler round-robin and `publish` delivering to all. In clustered mode a cluster
manager such as Hazelcast or Infinispan replicates the address registry and the bus forwards
over TCP, so the same code addresses a handler in another JVM without changing. Anything other
than a `String`, `JsonObject` or `Buffer` needs a registered `MessageCodec`, which is the
first thing people hit.

Reach for it inside a Vert.x application to keep verticles decoupled, and for a browser-facing
bridge over SockJS with a permitted-address list. The cost is that it is a bus, not a broker:
delivery is at-most-once with no persistence, so a message sent while no handler is registered
is dropped silently and a restart loses everything in flight. Anything that must survive a
crash belongs on Kafka or a real broker.

### Visitor
**Short:** GoF behavioural pattern: move an operation out of a closed type hierarchy into a visitor with a method per type.
**Kind:** concept
**Lang:** *
**Roles:** apis-frameworks/design-patterns-and-principles @1

Double dispatch makes it work: the client calls `element.accept(visitor)`, the element calls
back `visitor.visit(this)`, and because that second call is resolved on the element's static
type it selects the overload for the concrete class. The payoff is that a new operation over
the whole hierarchy is one new visitor class, with the compiler checking that every type is
covered.

Reach for it when the type set is closed and stable but operations keep multiplying — compiler
ASTs are the canonical case. The trade is exactly inverted from ordinary polymorphism: adding
a type forces an edit to every visitor, so it is the wrong pattern for a hierarchy that grows.
In modern Java, sealed interfaces with a pattern-matching `switch` give the same
exhaustiveness guarantee with none of the accept-and-callback ceremony.

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

The consumer registers an HTTPS endpoint, and the provider POSTs a JSON event to it when
something happens, expecting a 2xx within a short timeout; anything else enters the provider's
retry schedule, which is why delivery is at-least-once and duplicates are normal rather than
exceptional. Authenticity comes from an HMAC signature header computed over the raw body with
a shared secret, plus a timestamp to bound replay, so the signature must be verified against
the exact bytes before any parsing.

Reach for them to replace polling when events are sparse and latency matters. The costs are
that the endpoint must be publicly reachable and always available, that ordering across events
is not guaranteed, and that any slow handler causes redeliveries — so acknowledge immediately
and process from a queue, keyed on the event id for idempotency. When the consumer cannot
expose an endpoint, polling or a long-lived streaming connection is the fallback.

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

The connection begins as an HTTP GET carrying `Upgrade: websocket` and a `Sec-WebSocket-Key`;
the server answers 101 and from then on the TCP connection carries binary frames in both
directions with no request-response pairing. Client-to-server frames are XOR-masked, which
exists to defeat cache-poisoning attacks on intermediaries rather than for any security
benefit to the endpoints. Ping and pong frames are the only liveness mechanism, and
subprotocols are negotiated in the handshake.

Reach for it when both ends genuinely need to push — collaborative editing, games, live
trading. The costs are structural: the protocol gives you framing and nothing else, so
reconnection, resubscription, message ordering across a reconnect and authentication after the
upgrade are all yours to build; every connection pins a client to one server instance, forcing
sticky routing and a shared broker to fan out; and idle connections are killed by proxies
unless you ping. If traffic only flows server to client, Server-Sent Events is far less to
operate.

### websockets
**Short:** Low-level asyncio WebSocket client and server library for Python; Starlette uses it under the hood.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, runtime-systems/concurrency-and-async @3, runtime-systems/io-networking-and-syscalls @3

The library implements RFC 6455 properly rather than approximately: it validates UTF-8,
handles close handshakes and masking correctly, and enforces limits — `max_size` defaults to
one mebibyte per message so a hostile peer cannot exhaust memory. Keepalive pings are sent
automatically and a peer that fails to pong within the timeout is closed, which is what
distinguishes a dead connection from an idle one. Both a client and a server are provided,
over asyncio.

Reach for it for a standalone WebSocket service, a load-generation client, or a protocol test,
and note that Starlette uses it under the hood so a FastAPI endpoint is already built on it.
The costs are that it is deliberately low level — no rooms, no reconnection, no message
routing, all of which you write — and that per-message compression, while supported, costs CPU
and memory per connection and is worth disabling for many small messages.

### wrapt
**Short:** Python decorator and proxy library that wraps functions transparently, preserving the descriptor protocol for methods.
**Kind:** tech
**Lang:** python
**Roles:** apis-frameworks/aop-middleware-and-scheduling @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/design-patterns-and-principles @3

Most decorators break something subtle: `functools.wraps` copies a name and docstring but the
result is still a plain function, so it loses the descriptor protocol and misbehaves on
`classmethod`, `staticmethod` and instance methods, and introspection sees the wrapper.
`@wrapt.decorator` builds on a transparent object proxy that forwards attribute access,
`isinstance`, and `__get__` binding to the wrapped object, and passes the decorator a uniform
`(wrapped, instance, args, kwargs)` signature so one implementation works on a function, a
method and a class alike.

Reach for it when writing a decorator that will be applied to code you do not control — an
instrumentation, tracing or retry library — or when monkey-patching a third party, which it
supports directly. The costs are an added dependency and a proxy layer with a real per-call
overhead on hot paths, plus the fact that the proxy is so transparent that debugging what is
actually wrapped takes deliberate effort.

### wscat
**Short:** Command-line WebSocket client for connecting to and poking a raw WebSocket endpoint by hand.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/rpc-graphql-and-streaming @1, devtools/testing-and-mocking @3

Installed from npm, it opens a WebSocket connection from the terminal with `wscat -c ws://host/path` and gives you a prompt: what you type is sent as a frame, whatever arrives is printed, and `-H` adds headers such as an `Authorization` token used during the handshake. That is enough to answer the questions that come first in any WebSocket problem -- does the upgrade succeed, is the token accepted, does the server push anything unprompted, and is the connection being closed with which code. It also has a listen mode that runs a trivial server, useful when you need something that definitely works to point a client at. Reach for it to poke a raw endpoint by hand; if your server speaks a subprotocol such as STOMP or socket.io, wscat shows only the raw frames, so anything past the handshake needs a protocol-aware client.

### x402 v2
**Short:** Open HTTP 402 payment protocol (Coinbase, now Linux Foundation) letting agents pay per request over HTTP, MCP or A2A.
**Kind:** spec
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/tool-use-and-mcp @2, llm-apps/agent-framework @3

It revives the unused HTTP 402 status code as a real handshake. The server answers an unpaid
request with 402 and a body describing what it will accept — scheme, amount, asset, recipient,
and where to settle — the client constructs and signs a payment payload, retries the same
request with it in a header, and the server verifies and settles, usually through a
facilitator service that handles the chain interaction, before returning the resource. Nothing
about the resource's URL or method changes, so an existing API gains payment as a middleware.

Reach for it for machine-to-machine metering where provisioning an API key per caller does not
scale — the archetypal case being an agent paying per call. The costs are that the ecosystem
is early and small, that it presumes a stablecoin and a facilitator you now depend on, and
that settlement latency and fees sit inside your request path. For known callers, API keys and
invoicing remain simpler.

### yq
**Short:** Command-line YAML processor with jq-style expressions, used to read and patch Kubernetes and CI manifests.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, platform-delivery/infrastructure-as-code-and-config @2, devtools/version-control-and-workbench @3

The first thing to establish is which `yq` you have: Mike Farah's Go implementation is a
standalone binary with a jq-like language that operates on YAML natively, while the older
Python `yq` is a thin wrapper that converts YAML to JSON, shells out to `jq`, and converts
back. Only the Go one can edit a document in place with `-i` and, importantly, preserve
comments and key order — which is the difference between patching a Kubernetes manifest and
mangling it.

Reach for it in CI and scripts to read or patch a value in a manifest, a Helm values file or a
compose file. The costs are that multi-document YAML needs explicit handling with `select` and
document indexes, that anchors and aliases are resolved rather than preserved in many
operations, and that a templated file — a Helm chart before rendering — is not valid YAML at
all, so it cannot be touched this way.

### zlib
**Short:** The reference DEFLATE (LZ77 + Huffman) compression library behind gzip, PNG and HTTP content encoding.
**Kind:** tech
**Lang:** *
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, runtime-systems/collections-and-algorithms @2, runtime-systems/io-networking-and-syscalls @3

The library provides the DEFLATE implementation everything else builds on, and the confusing
part is that it emits three different wrappers around the same compressed data: the raw
DEFLATE stream, the zlib wrapper with a two-byte header and an Adler-32 checksum, and the gzip
wrapper with its own header and a CRC-32. Picking the wrong one is the usual cause of an
"incorrect header check" error. The API is stream-based — feed input, drain output, repeat —
so data larger than memory is handled by construction.

Reach for it whenever compression is needed inside a program rather than as a file format; it
is in the Python, Java and Node standard libraries and behind PNG and every HTTP stack. The
costs are age-related: single-threaded and slower than modern codecs at the same ratio, so
where both ends can negotiate, Zstandard gives markedly better throughput and Brotli better
ratios on text.

### Zod
**Short:** TypeScript-first schema validation with type inference; the usual way to declare tool schemas in JS agents.
**Kind:** tech
**Lang:** js
**Roles:** apis-frameworks/data-formats-and-api-contracts @1, llm-apps/tool-use-and-mcp @2

The schema is a value, and the static type is derived from it: `z.infer<typeof schema>`
produces the TypeScript type, so there is one definition rather than a type and a validator
that can drift. `parse` throws on failure while `safeParse` returns a discriminated result,
and transforms, refinements and branded types let a schema both validate and narrow — an
unknown `string` becomes a validated `Email` the compiler will not accept elsewhere.

Reach for it at any TypeScript boundary: request bodies, environment variables, third-party
responses, and tool arguments for an LLM, where a companion converter turns the schema into
JSON Schema. The costs are bundle size in a browser, a runtime parse cost on hot paths, and
the fact that inference on deeply nested or heavily generic schemas can slow the compiler and
produce error messages that take practice to read.
