# Spring — RESCUED subagent verification (Framework 7 / Boot 4.1)

Salvaged from a `sonnet` verification subagent whose parent (the spring fixer) was stopped
to serialize the queue. **The relaunched spring fixer MUST read this before touching any
Framework 7 / Boot 4.1 claim — four of these are verdicts against the audit, not for it.**

## 1. `@Proxyable` — REAL, but the framework-wide-default claim is CONTRADICTED

`org.springframework.context.annotation.Proxyable` (`@since 7.0`), `@Target({TYPE, METHOD})`.
- `value(): ProxyType` — `INTERFACES` (JDK dynamic proxy) / `TARGET_CLASS` (CGLIB), default `DEFAULT`
- `interfaces(): Class<?>[]` — only honoured when `value()` is not `TARGET_CLASS`
- Goes on a `@Bean` method or `@Component`; overrides the globally configured proxy type.
  It does NOT itself force proxying — the bean must be auto-proxied already.

**DO NOT WRITE "Framework 7 made CGLIB the default."** The reference doc still says the core
framework "suggests interface-based proxies by default". Class-based-by-default is a **Spring
Boot** behaviour via Boot's own properties, not a Framework 7 global flip. `@Proxyable` exists
precisely BECAUSE the core default is still interface-based while Boot's differs.

Sources: Javadoc `.../context/annotation/Proxyable.html`;
`https://docs.spring.io/spring-framework/reference/core/aop/proxying.html`;
`https://github.com/spring-projects/spring-framework/issues/35296`

## 2. `BeanRegistrar` — VERIFIED

`org.springframework.beans.factory.BeanRegistrar` (`@since 7.0`), functional interface,
`void register(BeanRegistry registry, Environment env)`. Applied via
`@Import(MyBeanRegistrar.class)` on a `@Configuration` class, or
`GenericApplicationContext.register(BeanRegistrar...)`. `@Conditional` works on the import.

`BeanRegistry`: `registerBean(Class<?>)`, `registerBean(String, Class<?>)`, and the spec
overload `registerBean(String, Class<?>, Consumer<Spec>)` exposing `.prototype()`,
`.lazyInit()`, `.description(String)`, `.supplier(Function<SupplierContext, T>)`;
`SupplierContext` has `bean(Class<T>)` / `bean(String)`. Kotlin DSL: `BeanRegistrarDsl`.

Positioning: the modern type-safe replacement for `BeanDefinitionRegistryPostProcessor` in the
common case (no raw `BeanDefinition`/`BeanDefinitionBuilder`). vs `@Bean`: `@Bean` is
declarative/static, `BeanRegistrar` is for registration logic with branching, loops, or
environment/profile checks. Works under AOT + GraalVM native-image, supplier form included,
without reflection.

CAVEAT: `registerAlias` is per the reference doc but was not independently quoted —
high-confidence, not literally verified.

Sources: Javadoc `.../beans/factory/BeanRegistrar.html`;
`https://docs.spring.io/spring-framework/reference/core/beans/java/programmatic-bean-registration.html`

## 3. `InetAddressFilter` — VERIFIED, with two scope limits

`org.springframework.boot.http.client.InetAddressFilter` (Spring Boot 4.1.0),
`@FunctionalInterface`, `boolean matches(InetAddress)` + default `matches(InetSocketAddress)`.
Factories: `of(String...)` (IP/CIDR), `internalAddresses()`, `externalAddresses()`, `all()`,
`none()`; composable via `and()`, `or()`, `negate()`, `andNot()`. Blocked -> `FilteredHostException`.

Wiring: declare an `InetAddressFilter` bean; `HttpClientAutoConfiguration` feeds it into
`HttpClientSettings`/`ClientHttpRequestFactorySettings`. Applies to clients built from an
**injected** `RestClient.Builder` / `WebClient.Builder`. A client from `RestClient.create()` /
`WebClient.create()` BYPASSES it and needs manual wiring.

**Two things NOT to assert:**
- **RestTemplate applicability is UNCONFIRMED** — primary sources name RestClient and WebClient only.
- **It is opt-in, not on-by-default** (no filtering with no bean declared). High-confidence from
  how every source frames it, but no primary line literally says "off by default".

Sources: Javadoc `.../boot/http/client/InetAddressFilter.html`;
`https://www.danvega.dev/blog/spring-boot-ssrf-protection-inet-address-filter`;
`https://www.infoq.com/news/2026/06/spring-boot-4-1/`

## 4. Spring for GraphQL — one verified, one CONTRADICTED, one code-only

**(a) Apollo Federation — integration layer, NOT native.** Spring for GraphQL integrates the
external `apollographql/federation-jvm` library (`FederationSchemaFactory` bean into
`GraphQlSource.Builder`, plus `@EntityMapping` for `_entities`). It does not implement
federation. Source: `https://docs.spring.io/spring-graphql/reference/federation.html`

**(b) "Trusted documents" — CONTRADICTED. DO NOT SHIP.** Current reference docs (2.0.4) have
**no "trusted documents" terminology and no `spring.graphql.http.*` property for it**. What
exists is "Operation Caching", configured code-only via a `PreparsedDocumentProvider` with
graphql-java's `ApolloPersistedQuerySupport` + a `PersistedQueryCache`. Spring's own docs say
"persisted query". `ResourceDocumentSource` is real but UNRELATED — a client-side/testing
helper loading `.graphql` files by name for `GraphQlTester`/`HttpGraphQlClient`, not a
security mechanism.

**(c) Query complexity/depth limiting — code-only, NO property exists.**
`MaxQueryDepthInstrumentation` / `MaxQueryComplexityInstrumentation` are graphql-java classes;
auto-config registers any `Instrumentation` bean, so you write the `@Bean` yourself. There is
**no** `spring.graphql.instrumentation.max-query-depth` / `max-query-complexity` property —
both feature requests were closed **declined / not planned**
(`spring-graphql#469`, `spring-boot#29379`). Property names in blog posts are what the
reporter WANTED. Do not present one as real.

---

**Scoreboard: 4 audit claims examined, 2 contradicted outright ((b) trusted documents, the
CGLIB-default framing of `@Proxyable`), 2 verified with scope caveats.** This is the
"delegate the finding, never the verdict" rule paying for itself again.

---

# RESCUED #2: Spring Batch 6.0 migration facts

Second orphaned `sonnet` verification subagent from the same stopped parent.

## CONTRADICTED — do not ship

**JSR-352 removal is a Spring Batch 5.0 item, NOT 6.0.** It was removed in 5.0 (2022). Any Q&A
attributing it to 6.0 is wrong. Sources: the 5.0 migration guide; `spring-batch#3894`.

**XML namespace config is NOT removed in 6.0** — `batch:` and `batch-integration:` are
*deprecated*, scheduled for removal in **v7**. A Batch 5 XML codebase still runs on 6.0 with
warnings. Same for `@EnableBatchProcessing(modular = true)`: deprecated, removal in 6.2.

**`CommandLineJobRunner` is deprecated, not removed** (`@Deprecated(since="6.0")`, removal 6.2),
in favour of `CommandLineJobOperator`.

## VERIFIED

**Schema.** `BATCH_JOB_SEQ` -> `BATCH_JOB_INSTANCE_SEQ` (confirmed by fetching live
`schema-h2.sql` off `main`). Table/column shapes otherwise unchanged from 5.x. Per-vendor
migration scripts at `spring-batch-core/.../core/migration/6.0/` (11 vendors) — a Batch 5 DB is
**not** silently readable by Batch 6.

**The migration trap worth teaching.** Verbatim from the wiki: *"Due to the change to job
parameters in v6, the serialisation format of parameters is different from the one in v5.
Therefore, it is not possible to restart failed job instances that were launched with v5 using
v6."* — *"It is expected that failed v5 job instances are either restarted to success or
abandoned before the migration to v6 is done."* This is a genuine operational gotcha and good
§10 Common Pitfalls material.

**`JobOperator extends JobLauncher`.** No separate `JobLauncher` bean needed.
`JobLauncher` carries exactly `@Deprecated(since="6.0", forRemoval=true)`, javadoc *"in favor of
JobOperator. Scheduled for removal in 6.2 or later."*
Methods: `start(Job, JobParameters)` (default, inherited), `restart`, `startNextInstance`,
`stop` -> boolean, `abandon`, and **new in 6.0 `recover(JobExecution)`**.
Its query methods (`getJobNames`, `getJobInstances`, `getExecutions`, `getRunningExecutions`,
`getParameters`, `getSummary`, `getStepExecutionSummaries`) are **themselves deprecated
forRemoval in 6.0** in favour of `JobRepository`/`JobRegistry` directly.

**`recover()` vs `abandon()` — a clean exam contrast.** `recover()` takes an execution stuck in
`STARTED` (abrupt process death), marks it `FAILED`, sets `recovered=true` in the execution
context, and makes it **eligible for restart**. An `abandon()`ed execution can **never** be
restarted.

**Auto-configured.** `DefaultBatchConfiguration` declares `@Bean JobOperator jobOperator(
JobRepository)` and **no longer declares any `jobLauncher()` bean** (verified by fetching the
source). Boot 4's `BatchAutoConfiguration` extends it.

**`JobRepository extends JobExplorer`.** `JobExplorer` + `JobExplorerFactoryBean` deprecated;
`isJobInstanceExists()`, `findJobExecutions()`, `findJobInstancesByName()` moved onto
`JobRepository`.

**Package relocation, exact.** `org.springframework.batch.item` ->
`org.springframework.batch.infrastructure.item`; same for `poller`, `repeat`, `support`.
Also `core.explore` -> `core.repository.explore`; JDBC/Mongo DAOs into `.jdbc`/`.mongo`;
listeners into `core.listener`; job classes into `core.job` and `core.job.parameters`.
Official recipe: `org.openrewrite.java.spring.batch.SpringBatch5To6Migration`.

**`ChunkOrientedStep`** (via `ChunkOrientedStepBuilder`) is now stable (experimental in 5.1),
replacing `ChunkOrientedTasklet`/`TaskletStep`. The fluent form is `.chunk(5)
.transactionManager(tm)`; the old `.chunk(int, PlatformTransactionManager)` and
`.chunk(CompletionPolicy, PlatformTransactionManager)` are **deprecated but still compile**,
kept in deprecated form for the whole v6 generation.

**Observability — a real behavioural change.** *"The usage of Micrometer's global static meter
registry was removed, and it is now required to configure an ObservationRegistry as a bean"* to
collect batch metrics. JFR event support is **additive**, not a requirement.

**Graceful shutdown, new in 6.0** — a new `StoppableStep` interface (extends `Step`). A graceful
shutdown stops active steps and writes a consistent repository state that preserves restartability.

**Also deprecated (not removed):** JUnit 4 support in `spring-batch-test`; Jackson 2 (use 3.x).

## Explicitly NOT verified — do not assert these

- **Whether `retry` is a package relocation at all.** Evidence points to a **dependency swap**
  (Spring Retry -> Spring Framework 7's own retry support), NOT a
  `batch.retry` -> `infrastructure.retry` rename. Do not list it alongside the four real moves.
- **The removed reader/writer list** (`MongoItemReader`/`Writer`, `Neo4jItemReader`/`Writer`,
  `SqlWindowingPagingQueryProvider`) rests on the migration wiki ALONE — one primary source, not
  cross-confirmed against a 5.2-vs-6.0 GA javadoc diff. Usable, but flag the confidence.
