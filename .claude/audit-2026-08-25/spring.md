# Spring Audit — 2026-08-25

## 0. Snapshot

The `spring/` section is the most *structurally* disciplined section in the repo. All 39 module
pages and all 4 deep-dive sub-files carry the complete 14-section template (verified
programmatically — zero missing headings except `## 13`/`## 14` on the two template-exempt
sub-files); every Q&A minimum in the section's own `CLAUDE.md` is met; `**Short:**` coverage is
100% of parsed module Q&As; 161 Mermaid fences across 43 module files. Six independent full-read
passes over all 43 files returned **zero THIN verdicts** — every module has concrete numbers,
broken→fixed pairs, and comparison tables. Several files are reference-quality:
`spring_aop/pointcut_designators.md` opens with a pinned baseline (`Spring Boot 4.1.x / Framework
7.0.8 / AspectJ 1.9.25.1 / Java 25 LTS`) and backs every claim with `javap` output and a
benchmarked `xychart`; `spring_http_clients.md` and `spring_cloud_patterns/gateway_server_webmvc.md`
are pinned to Boot 4.1 GA (2026-06-10) and correctly handle JEP 491, RestTemplate's real
deprecation schedule, and the Spring Retry → Framework 7 core-resilience shift. The security
modules are clean of every legacy API (`WebSecurityConfigurerAdapter`, `antMatchers()`,
`@EnableGlobalMethodSecurity`, `AccessDecisionVoter`) — zero occurrences.

**The single biggest weakness is a split-brain between the index and the content.** The section's
`README.md` version matrix is accurate to Boot 4.1 and advertises Framework 7 features —
`@Proxyable`, `BeanRegistrar`, `@ConcurrencyLimit`, `RestTestClient`, first-class API versioning,
JSpecify, GraalVM 25 — that **no module actually teaches**. Meanwhile a consistent cluster of
files documents the *previous* generation's answer without the newer one even being mentioned for
comparison: `spring_batch` = Batch 5, `spring_ai` = Spring AI 1.0, `spring_modulith` = Modulith
1.x, `spring_native_graalvm` = **GraalVM 22.3 (December 2022)**, `observability_and_tracing` =
manual OTel wiring, `spring_performance` = CDS with no AOT cache. And the entire
modern-authentication surface — passkeys/WebAuthn, MFA, one-time-token login, DPoP, OAuth 2.1 —
has **zero** occurrences section-wide despite being the headline of Spring Security 7.1.

| Metric | Value |
|---|---|
| Modules | 39 |
| Case studies | 9 (+ 4 cross-cutting primitives, + 1 index) |
| Deep-dive sub-files | 4 (`pointcut_designators`, `gateway_server_webmvc`, `rsocket_reactive_messaging`, `i18n_and_localization`) |
| Total module lines | 38,959 |
| Total case-study lines | 9,963 |
| Total module Q&As | 733 (100% carry `**Short:**`) |
| Avg lines / module | ~999 |
| Mermaid fences (modules) | 161 |

> Doc bug found while counting: `spring/CLAUDE.md`'s "Deep-dive sub-files" list names only **2**.
> There are **4** — `pointcut_designators.md` and `gateway_server_webmvc.md` are mentioned inline
> in the module table but omitted from the sub-file list.

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `spring_security_modern_auth/spring_security_modern_auth.md` | module | MFA is the headline of Spring Security 7.1 (`@EnableMultiFactorAuthentication`, `AuthorizationManagerFactories.multiFactor()` with `when`/`withWhen`, `MultiFactorCondition.WEBAUTHN_REGISTERED`, `ConditionalAuthorizationManager`, `AllRequiredFactorsAuthorizationManager.anyOf`) — https://docs.spring.io/spring-security/reference/whats-new.html . Passkeys have shipped since 6.4 — https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html . Section-wide grep: **0 hits** for passkey, webauthn, MFA-as-feature, one-time token, DPoP, OAuth 2.1. The only MFA in the section is a hand-rolled `MfaAuthenticationProvider` in `spring_security_architecture.md`'s §14 case study, which predates the built-in support. | **P0** | 950 |
| `spring_ai/mcp_servers_and_clients.md` | sub-file | Spring AI 2.0 GA (2026-06-12) merged MCP into core with MCP Java SDK 2.0.0, `@McpTool`/`@McpResource`/`@McpPrompt`, OTel metrics and OAuth 2.0 on MCP endpoints — https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ . At Spring I/O 2026 "roughly a third of the main-track sessions were about Spring AI, MCP, agents, or building on LLMs" — https://foojay.io/today/spring-i-o-2026-field-notes-from-barcelona/ . Section-wide grep: **0 hits** for MCP or Model Context Protocol. | **P0** | 800 |
| `spring_boot_4_migration/spring_boot_4_migration.md` | module | The Boot 3.5→4.x upgrade is a discrete, breaking, interview-relevant body of work: modular starters (`spring-boot-starter-flyway`/`-liquibase` now required where a third-party dep sufficed), package relocations to `org.springframework.boot.<technology>`, Jackson 3 group-id + `@JsonComponent`→`@JacksonComponent` + `spring.jackson.read.*`→`spring.jackson.json.read.*`, `@MockBean`→`@MockitoBean`, `@SpringBootTest` no longer auto-providing MockMvc/TestRestTemplate, Undertow and Spock removed, Session Hazelcast/Mongo dropped, JSpecify compile breaks, `spring-boot-properties-migrator` — https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide . Spring I/O 2026 ran two dedicated sessions ("Inside Spring Boot 4: Restructuring for the Future"; "Hybrid Modernization" pairing OpenRewrite with LLMs) — https://2026.springio.net/sessions/ . | **P0** | 900 |
| `spring_performance/startup_and_aot_cache.md` | sub-file | Boot 4 has first-class JDK AOT-cache support (Project Leyden, JEP 483/514/515, Java 25) with its own reference page — https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html . Section-wide grep: **0 hits** for "AOT cache" or "Leyden". `spring_performance` covers only CDS ("Spring Boot 3.3+ supports CDS out of the box", L599) and never mentions CRaC; `spring_native_graalvm` §8's tradeoff table contrasts only "JVM (JIT)" vs "Native image (AOT)" with no third option. The four-way startup decision (plain JVM / CDS / AOT cache / native) is now the actual production choice. | **P0** | 550 |
| `spring_cloud_patterns/core_resilience_framework7.md` | sub-file | Framework 7 moved retry and concurrency limiting into core: `@Retryable`, `@ConcurrencyLimit`, `@EnableResilientMethods`. Juergen Hoeller gave it a main-track Spring I/O 2026 session ("Core Resilience Features in Spring Framework 7") — https://2026.springio.net/sessions/ . Today `@ConcurrencyLimit` appears **only** in `README.md`/`CLAUDE.md`; no module teaches it, and `spring_messaging.md` Pitfall 4 shows Spring Retry's `@Retryable` with no note that a same-named core annotation now exists in a different package. | **P1** | 500 |
| `spring_ai/agents_and_tool_calling.md` | sub-file | Spring AI 2.0 "lifts the tool loop into the advisor chain as a first-class, composable component" — `ToolCallingAdvisor`, `ToolSearchToolCallingAdvisor` (progressive disclosure over hundreds of tools), `StructuredOutputValidationAdvisor` (self-correcting JSON) — https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ . The current module's §6.5 function-calling and §7.3 "agentic tool use" predate this redesign and it has no agent-loop architecture section. | **P1** | 600 |
| `case_studies/design_mcp_tool_platform.md` | case study | 9 case studies, **none** AI/LLM-shaped, in a section that carries a `spring_ai` module and cross-links `llm/`. An MCP server exposing internal services as tools exercises Security (OAuth2 + DPoP), HTTP clients, observability, tenancy and Spring AI at once — the only case-study shape that touches both new P0 modules. | **P1** | 1000 |
| `spring_hateoas_rest_maturity/` (extend in place) | in-place | Framework 7 first-class API versioning: `@RequestMapping(version = "1.2")`, `ApiVersionConfigurer`, `spring.mvc.apiversion.*` / `spring.webflux.apiversion.*`. Grep: `apiversion` appears in `README.md`, `CLAUDE.md`, and one passing mention in `validation_and_error_handling.md` — **absent from all 9 web-stack files**. `spring_hateoas_rest_maturity.md` §4 already has an "API Versioning Strategies" table listing only URI/header/media-type, and its §14 case study literally walks a `/api/orders/{id}/pay` → `/api/v2/...` relocation, which is the exact scenario the new mechanism targets. | **P1** | +350 |
| `spring_transactions/` (extend in place) | in-place | Zero mention of virtual threads in the whole file (grep-confirmed) on a Boot 4.1 / Java 25 baseline. `@Transactional`'s `ThreadLocal`-bound `EntityManager`/connection binding under a virtual-thread-per-request executor, and the HikariCP-pool relocation of the queue, is exactly the broken→fixed gotcha this module's style would normally own. | **P1** | +200 |
| `spring_graphql/` (extend in place) | in-place | Federation is named but has no code and no note that **Spring for GraphQL ships no native federation support** (needs an external library); "persisted queries" never uses the current term *trusted documents*; query-complexity limits are prose-only (`maxQueryDepth`) — the only claims in an otherwise code-heavy file with no example. | **P2** | +200 |
| `spring_testing/` (extend in place) | in-place | `RestTestClient` (Framework 7's non-reactive `WebTestClient`) is absent; every HTTP test uses `TestRestTemplate`/`WebTestClient`. `@ServiceConnection` appears exactly once, in passing inside a Q&A answer, while every code example still hand-wires `@DynamicPropertySource` — now the legacy pattern. | **P2** | +200 |

### What each P0 would contain

**`spring_security_modern_auth/`** — §1 the 2026 authentication landscape (password → passkey →
factor-aware authorization); §2 a passkey is a hardware-bound signature, not a shared secret; §3
possession beats knowledge, factors are recorded on the `Authentication`, sender-constraining beats
bearer; §4 the mechanisms — WebAuthn/passkeys, one-time-token login (`OneTimeTokenLoginConfigurer`,
since 6.4), TOTP, DPoP, OAuth 2.1, RFC 8693 token exchange; §5 sequence diagrams for WebAuthn
registration/assertion and the DPoP proof round-trip; §6 the `webAuthn()` DSL,
`WebAuthnAuthenticatorManager` persistence (the default in-memory store is explicitly not
production), `@EnableMultiFactorAuthentication(when = MultiFactorCondition.WEBAUTHN_REGISTERED)`,
`AuthorizationManagerFactories.multiFactor()` with `when`/`withWhen`,
`RestClientOpaqueTokenIntrospector`; §7 named adopters plus the MCP/AI-agent driver; §8 passkey vs
TOTP vs SMS vs magic link, bearer vs DPoP; §9 when *not* to build it yourself; §10 the in-memory
credential store shipped to prod, the HTTPS/localhost rule, RP-ID mismatch across subdomains,
factor list not surviving session replication; §11 Spring Security 7.1, WebAuthn4J, Spring
Authorization Server 7.1; §12 20+ Q&As; §13; §14 adding passkeys to an existing password estate
without a flag day.

**`spring_ai/mcp_servers_and_clients.md`** — §1 MCP as the USB-C of tool calling and why it moved
into Spring AI core; §2 an MCP server is a `@RestController` for models; §3 tools/resources/prompts
as the three primitives, transport-agnostic; §4 client mode vs server mode vs both at once;
Streamable HTTP (the modern default) vs SSE (compat) vs stdio (local); §5 a sequence diagram of a
tool-call round trip through an MCP client; §6 `@McpTool`/`@McpResource`/`@McpPrompt` on an ordinary
Spring bean, MCP Java SDK 2.0 wiring, OAuth2-secured MCP endpoints, OTel metrics; §7 exposing an
existing service catalog; §8 MCP vs a plain `@Tool` bean vs a bespoke HTTP tool API; §9 when a tool
should *not* be exposed; §10 unbounded tool catalogs blowing the context window (the
`ToolSearchToolCallingAdvisor` answer), unauthenticated stdio servers, prompt injection via resource
content; §11 versions; §12 15+ Q&As; §13; §14.

**`spring_boot_4_migration/`** — §1 what Boot 4 changed and why it is not a routine minor; §2 "the
jar got split, and everything you imported by accident now needs a starter"; §3 the four axes
(modularization, Jakarta EE 11, Jackson 3, null-safety); §4 big-bang vs strangler vs
OpenRewrite-assisted; §5 a dependency-graph before/after; §6 the concrete change list with a
broken→fixed pair for each; §7 real migration reports; §8 Boot 3.5 vs 4.1 tradeoff **including the
Spring Cloud lag** (no train supports Boot 4.1 — 2025.1 "Oakwood" is pinned to Boot 4.0.x, and
2025.1.3 of 2026-08-20 still builds on 4.0.8); §9 when to stay on 3.5; §10 the JSpecify compile
wall, Undertow/Spock/Session-Hazelcast removals, the `@SpringBootTest`-lost-MockMvc trap; §11
OpenRewrite recipes, `spring-boot-properties-migrator`; §12 18+ Q&As; §13; §14 a 200-service estate
migration.

**`spring_performance/startup_and_aot_cache.md`** — §1 the cold-start problem restated for 2026; §2
"AOT cache is a memoised JVM warm-up; native image is a different program"; §3 what each technique
preserves (peak throughput, full reflection, dynamic classloading); §4 the four options; §5 a
startup-timeline bar comparison; §6 training-run mechanics, `-XX:AOTCache`, JEP 514 command-line
ergonomics, `spring-boot-maven-plugin` integration, CRaC checkpoint/restore and its
open-file/secret hazards; §7 measured numbers (4.9s→2.4s reported for a Boot 3.3 app; ~3x with
Leyden, ~4x combined with Spring AOT); §8 the four-way table (startup, peak throughput, memory,
build complexity, reflection cost); §9 when native image still wins and when it is a trap; §10
training runs that miss the real paths, cache invalidation on any classpath change; §11 JDK 25,
GraalVM 25, CRaC; §12 15+ Q&As; §13; §14.

---

## 2. Stale — verified CORRECTIONS

| File | Line / heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `spring_native_graalvm/spring_native_graalvm.md` | **L7 header**, and L633 §14 | "Spring Boot 3.0+ / **GraalVM 22.3+**; always note the version" and case study "Spring Boot 3.2, GraalVM 22.3" | GraalVM 22.3 shipped **December 2022** — ~3.75 years stale. Boot 4 requires **GraalVM native-image 25+**, and the section's own `README.md` matrix already says "GraalVM 25, unified 'exact reachability metadata' format". The module contradicts its own index. | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide |
| `spring_batch/spring_batch.md` | L20 §1; §5 diagram L105; §11 table L541-545; §12 Q&As; §13 | "Spring Batch 5 (GA with Spring Boot 3.0) requires Java 17 and Spring Framework 6." Whole module built on `JobLauncher` and `JobExplorer`; §11 pins "Spring Batch 5.x" | Boot 4 ships **Spring Batch 6** (GA 2025-11-19). Batch 6 **removed `JobLauncher`** (replaced by `JobOperator`), **merged `JobExplorer` into `JobRepository`**, promoted `ChunkOrientedStep` over `ChunkOrientedTasklet`/`TaskletStep`, added `CommandLineJobOperator`, `JobOperator.recover()` and graceful shutdown, and relocated item packages to `org.springframework.batch.infrastructure.item.*`. **Internal contradiction:** this section's own `case_studies/design_batch_pipeline.md` L467 already says "**Spring Batch 6.x** … Native (Boot 4.1) … Batch 6 relocated the item packages" — the module page is behind its own case study. | https://docs.spring.io/spring-batch/reference/whatsnew.html , https://spring.io/blog/2025/11/19/spring-batch-6-0-0-ga/ |
| `spring_ai/spring_ai.md` | L7-8 §1; §11 table row; §3 principle 7 | "It reached **1.0 GA in 2025** (baseline Spring Boot 3.3/3.4, Java 17+)"; "Spring AI \| 1.0 GA (2025)"; "the same `Observation` API as the rest of Boot **3**" | **Spring AI 2.0.0 GA on 2026-06-12**, requiring Spring Boot 4.0/4.1 and Framework 7.0, on Jackson 3 with JSpecify. Tool calling was redesigned out of per-model private mechanisms into the advisor chain (`ToolCallingAdvisor`, `ToolSearchToolCallingAdvisor`, `StructuredOutputValidationAdvisor`); MCP merged into core with MCP Java SDK 2.0.0; the core now carries 8 primary providers with others vendor-maintained externally. | https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ |
| `spring/CLAUDE.md` | module table, `spring_ai/` row | "(Spring AI 1.0 GA)" | Spring AI 2.0 GA | as above |
| `spring_modulith/spring_modulith.md` | L6 header blockquote; L616 §14 | "Spring Modulith 1.x / Spring Boot 3.1+."; case study "adopted Spring Modulith (Boot 3.2)" | **Spring Modulith 2.1** is current, platform-upgraded to Boot 4.1; it adds `@ModuleSlicing` (module slicing combined with Boot slice-test annotations), `JobRunrEventExternalizer`, `NamastackOutboxEventRecorder`. | https://spring.io/blog/2026/03/27/spring-modulith-2-1-m4-2-0-5-and-1-4-10-released/ , https://github.com/spring-projects/spring-modulith |
| `spring/CLAUDE.md` | module table, `spring_modulith/` row | "(Boot 3.1+)" | Modulith 2.1 / Boot 4.1 | as above |
| `spring_session/spring_session.md` | L17 §1 | "Spring Session 3.x aligns with Spring Boot 3.x / Spring Framework 6.x" | **Spring Session 4.1.0** ships with Boot 4.1 (4.1.0-RC1 in Boot 4.1.0-RC1). The module never mentions Boot 4. | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes |
| `spring_session/spring_session.md` §1/§4; `spring/CLAUDE.md` row | backend list | Teaches "Redis/JDBC/**Hazelcast**/**Mongo** backends" as four equal first-class options | Boot 4.0 **removed direct Spring Boot support for Spring Session Hazelcast and MongoDB**; the Hazelcast variant is now led by the Hazelcast team, outside Boot. | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide |
| `spring_data_nosql.md` (L416-417, 651, 736, 882, 1059, 1220, 1266), `spring_session.md` (L76, 583, 623, 714, 823), `spring_performance.md` (L430) | Redis serializer | All three recommend `GenericJackson2JsonRedisSerializer` as the JSON serializer | **`GenericJackson2JsonRedisSerializer` is deprecated as of Spring Data Redis 4.0** in favour of **`GenericJacksonJsonRedisSerializer`** (Jackson 3-based, with a `GenericJacksonJsonRedisSerializerBuilder`). **Internal contradiction:** `spring_caching.md` already uses the new name throughout — three files disagree with a fourth. | https://docs.spring.io/spring-data-redis/reference/api/java/org/springframework/data/redis/serializer/GenericJackson2JsonRedisSerializer.html |
| `spring_security_jwt_oauth/spring_security_jwt_oauth.md` | §11 table L680 | "Spring Authorization Server \| … current GA line is **1.5.x**" | The SAS line was **renumbered to 7.x to align with Spring Security** (2.0.0-M1/M2 were abandoned and never went GA); new features land in Spring Security from 7.0 onward. **Internal contradiction:** `case_studies/design_oauth2_authorization_server.md` L405 already says "Spring Authorization Server **7.1** … Versioned with Spring Security 7.1 / Spring Boot 4.1". | https://github.com/spring-projects/spring-authorization-server/releases , https://spring.io/projects/spring-authorization-server/ |
| `spring/README.md` | §7 case-study table L525 | "Spring Authorization Server **1.x**, PKCE, refresh token rotation…" | Same as above — the README index contradicts the content file it links to. | as above |
| `spring_grpc/spring_grpc.md` | L11-12 §1; §11 dependency block L207-241; §4 table L50 | Presents exactly two options — standalone "Spring gRPC (official, 1.0 GA, 2025)" with `spring-grpc-server-spring-boot-starter`, and the `net.devh` community starter — and instructs the reader to add those starters directly | **Spring Boot 4.1 auto-configures gRPC natively**: server and client starters from start.spring.io, `@GrpcService` discovery, **`@ImportGrpcClients`** for type-safe stub injection, `GrpcChannelBuilderCustomizer`, auto-configured `ObservationGrpcServerInterceptor`/`ObservationGrpcClientInterceptor`, and a **gRPC test slice with in-process transport**. None of these appear in the module. | https://docs.spring.io/spring-boot/reference/io/grpc.html , https://www.infoq.com/news/2026/06/spring-boot-4-1/ |
| `observability_and_tracing/observability_and_tracing.md` | §6.1 dependencies; §6.7 "BROKEN — Losing Trace Context in Async Operations"; Q12; §11 | Dependencies are the manual Boot-3-era trio (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` + Actuator). §6.7 and Q12 teach `ContextExecutorService.wrap()` as *the* fix for `@Async` trace-context loss. Micrometer only ever "1.x / 1.10+" | Boot 4 adds **`spring-boot-starter-opentelemetry`**; Boot 4.1 **propagates Micrometer context to `@Async` methods automatically** ("trace IDs and spans following the work into thread-pool tasks without extra wiring"), plus `management.opentelemetry.enabled`, OTLP exemplars and batch-processor config. Boot 4.1 pins **Micrometer 1.17 / Tracing 1.7**. The manual `@Async` fix now teaches a superseded workaround as current practice. | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes , https://www.infoq.com/news/2026/06/spring-boot-4-1/ |
| `case_studies/cross_cutting/otel_observability_for_spring.md` | "@Async fix" | Same manual-propagation guidance as the module | Same as above — must be updated in the same pass or the two files disagree. | as above |
| `spring_integration/spring_integration.md` | L7 header blockquote | "Spring Integration **6.x** / Spring Boot **3.x**." | Boot 4.1 ships **Spring Integration 7.1.0**. (Content is largely timeless EIP theory, so this is a version-tag fix, not a rewrite.) | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes |
| `spring_cloud_config/spring_cloud_config.md` | L59 and throughout | Frames `spring.config.import` as the "Boot 3.x (recommended)" approach; no Boot 4 reference anywhere in the file | The mechanism is unchanged, but the framing dates the file against siblings that pin "Spring Boot 4.1.x (GA 2026-06-10)". | https://spring.io/blog/2026/06/10/spring-boot-4/ |
| `spring_messaging/spring_messaging.md` | Pitfall 4, L917 | `@Retryable(maxAttempts=3, backoff=@Backoff(...))` presented as Spring Retry with no version note | Framework 7 ships a **core** `@Retryable` (plus `@ConcurrencyLimit`, `@EnableResilientMethods`) with the same simple name in a different package — a live import-ambiguity trap for any reader on Boot 4. | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes , https://2026.springio.net/sessions/ |
| `spring_cloud_patterns/spring_cloud_patterns.md` | L15 §1 | "Spring Cloud 2025.1.0 'Oakwood' … builds on Spring Framework 7 / Spring Boot 4" — true but unqualified | **No Spring Cloud release train supports Boot 4.1.** Oakwood is pinned to Boot **4.0.x**; the latest patch, 2025.1.3 (2026-08-20), still builds on Boot 4.0.8. A reader following the section's "Boot 4.1 is current" framing *plus* this module produces a version mismatch. A missing caveat, not a wrong statement. | https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions , https://spring.io/blog/2026/08/20/spring-cloud-2025-1-3-has-been-released/ |
| `spring_data_nosql/spring_data_nosql.md` | L1095 §14 | Case-study stack "Spring Boot 3.2, Java 17, Spring Data MongoDB 4.2, Spring Data Redis 3.2, Lettuce 6.3" | Peer §14 scenarios are pinned to "Spring Boot 4.1 / Java 25" (`bean_lifecycle`, `dependency_injection`, `spring_proxies`, `spring_data_jpa`, `spring_boot_actuator`). Boot 4.1 manages Spring Data BOM 2026.0.0. House-style inconsistency that dates the section's newest data module. | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes |

### Verified NOT stale — do not "fix" these

- **RestTemplate.** `spring_http_clients.md` and `spring_hateoas_rest_maturity.md` both say it is in
  maintenance mode, carries no `@Deprecated` today, with `@Deprecated` scheduled for Framework 7.1
  and removal for 8.0. Exactly right — Framework 7.1 (Nov 2026) is the release that formally
  deprecates it. https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.1-Release-Notes
- **Jackson 3.** `spring_mvc_architecture.md` and `request_handling.md` already present
  `tools.jackson` / `JacksonJsonHttpMessageConverter` / `JsonMapper` as the default. No
  `com.fasterxml`-as-current anywhere in the web stack.
- **WebFlux vs virtual threads.** `spring_webflux.md` gives the honest 2026 verdict ("Yes —
  backpressure and streaming composition, which virtual threads do not provide at all"), covers
  JEP 491, and names the HikariCP-pool-exhaustion trap that unbounded virtual threads create.
  Matches current consensus.
- **JEP 491 / virtual-thread pinning.** Correct and consistent in `spring_performance`,
  `spring_webflux`, `gateway_server_webmvc`, `spring_events_and_scheduling`: `synchronized` stopped
  pinning in JDK 24, `jdk.tracePinnedThreads` removed, `jdk.VirtualThreadPinned` JFR event at 20 ms.
- **Netflix OSS.** Ribbon/Hystrix/Zuul correctly framed as removed/dead; Eureka correctly framed as
  still shipping in Spring Cloud Netflix 5.0.x. No fabricated deprecations found anywhere.
- **Security legacy APIs.** Zero occurrences of `WebSecurityConfigurerAdapter`, `antMatchers()`,
  `@EnableGlobalMethodSecurity`, `AccessDecisionVoter`/`AccessDecisionManager` in either security
  module. `@EnableMethodSecurity` + `AuthorizationManager` + lambda DSL throughout.
- **OAuth2 grants.** The §4 grant table lists only Authorization Code, Code+PKCE, Client
  Credentials, Refresh Token and Device Code — implicit and ROPC correctly omitted. (It is
  OAuth-2.1-compliant in substance; it just never says so — see §1.)
- **Hibernate/JPA.** `spring_data_jpa.md` says "Hibernate 7.x; JPA 3.2 provider" and its §14 runs
  "Hibernate 7.x; Spring Boot 4.1 / Java 25". Current.
- **`spring.factories`.** Appears in `spring_boot_configuration.md` and
  `spring_boot_autoconfiguration.md` only as history; `AutoConfiguration.imports` is used as current.
- **`@MockBean`.** `spring_testing.md` uses `@MockitoBean`/`@MockitoSpyBean` throughout and names
  JUnit 6. The residual `@MockBean` strings are the deprecation being taught, not used.

---

## 3. Reordering — SEQUENCE and TIER changes

The section's own phase diagram admits the problem: a base Phase 4 of five modules plus a
stapled-on "Phase 4 additions" block of four more. Phase 4 is **9 modules**, the largest cluster,
interleaving request-pipeline fundamentals with four specialist protocols. Two consequences:

- A reader hits `spring_webflux` (1,205 lines, the section's hardest module) at **position 14 of
  39** — before transactions, before security, before testing. That is the get-lost point.
- `validation_and_error_handling` sits at **position 19**, six modules *after* `request_handling`
  (12), behind GraphQL, HATEOAS, HTTP clients and gRPC — even though the README's own phase diagram
  annotates it "(extends request_handling)".

### Sequence moves

| Module | Current position | Proposed | Why |
|---|---|---|---|
| `validation_and_error_handling` | 19 | **12** (directly after `request_handling`) | It *is* the second half of `request_handling` — `@Valid`, `@ControllerAdvice`, `ProblemDetail`. The README already annotates it as an extension of that module. Highest-value, lowest-risk move in the section. |
| `spring_webflux` | 14 | **25** (open Phase 4b, after Security) | Reactive is a branch off the servlet stack, not step 14 of the fundamentals. Its own honest verdict is "use MVC + virtual threads unless you need backpressure or streaming" — which the reader cannot evaluate before seeing transactions, JDBC and the security filter chain. |
| `spring_graphql` | 15 | **26** (Phase 4b) | Its central lesson (N+1 via `@BatchMapping`/DataLoader) only lands after `spring_data_jpa`. |
| `spring_grpc` | 18 | **27** (Phase 4b) | Specialist protocol; cross-links `observability_and_tracing` and `spring_security_architecture`, both currently later. |
| `spring_hateoas_rest_maturity` | 16 | **28** (Phase 4b) | Richardson maturity + API versioning is design judgment, not a fundamentals step. Also the proposed home for first-class API versioning. |
| `spring_modulith` | 10 (Phase 3, Boot) | **35** (after `spring_events_and_scheduling`) | Its two core mechanisms — `@ApplicationModuleListener` (a `@TransactionalEventListener` wrapper) and the event publication registry (an outbox) — are unexplained at position 10; `spring_events_and_scheduling` is at 33 and `spring_transactions` at 23. It is also a principal-tier architecture topic parked in the beginner's Boot phase. |
| `spring_ai` | 34 (inside "Cloud & Messaging") | **38**, opening a new Phase 9 | Spring AI is neither cloud nor messaging. Given Spring I/O 2026's session mix it deserves a terminal phase with its MCP sub-file. |
| `spring_boot_4_migration` (new) | — | **39** | The capstone: everything else has been taught, now migrate it. |

**If the owner wants a minimal change**, do only two moves — `validation_and_error_handling` 19→12
and `spring_modulith` 10→35 — and skip the Phase 4b split. That captures most of the benefit for a
fraction of the churn. The Phase 4b split is the largest and most arguable proposal in this report.

### Proposed `STUDY_ORDER["spring"]`

Assumes the two P0 module additions. Sub-files need no entry.

```js
spring: [
  // Phase 1 — Core Container
  "spring/ioc_container","spring/bean_lifecycle","spring/dependency_injection","spring/spring_configuration",
  // Phase 2 — Proxies & AOP
  "spring/spring_proxies","spring/spring_aop",
  // Phase 3 — Spring Boot
  "spring/spring_boot_autoconfiguration","spring/spring_boot_configuration","spring/spring_boot_actuator",
  // Phase 4 — Spring Web (servlet fundamentals)
  "spring/spring_mvc_architecture","spring/request_handling","spring/validation_and_error_handling",
  "spring/filters_and_interceptors","spring/spring_http_clients",
  // Phase 5 — Data & Transactions
  "spring/spring_data_jpa","spring/spring_data_nosql","spring/database_migrations",
  "spring/spring_transactions","spring/spring_caching",
  // Phase 6 — Security
  "spring/spring_security_architecture","spring/spring_security_jwt_oauth",
  "spring/spring_security_modern_auth","spring/spring_session",
  // Phase 4b — Alternative protocols & API style
  "spring/spring_webflux","spring/spring_graphql","spring/spring_grpc","spring/spring_hateoas_rest_maturity",
  // Phase 7 — Cloud & Messaging
  "spring/spring_cloud_config","spring/spring_cloud_patterns","spring/spring_messaging",
  "spring/spring_websocket_stomp","spring/spring_batch","spring/spring_events_and_scheduling",
  "spring/spring_integration","spring/spring_modulith",
  // Phase 8 — Testing & Production
  "spring/spring_testing","spring/spring_performance","spring/observability_and_tracing","spring/spring_native_graalvm",
  // Phase 9 — AI & Modernization
  "spring/spring_ai","spring/spring_boot_4_migration",
]
```

### Tier moves (`<!-- study-paths -->` block in `spring/README.md`)

| Module | Current tier | Proposed | Why |
|---|---|---|---|
| `spring_ai` | `-` (Full only) | **senior** | A third of Spring I/O 2026's main track was AI/MCP/agents. Keeping it Full-only means the Interview toggle hides the section's most-asked new topic. |
| `observability_and_tracing` | principal | **senior, principal** | Instrumenting a service with the `Observation` API and reading a trace is craft, not judgment. Every Spring JD names Micrometer/OTel. |
| `spring_webflux` | senior | **senior, principal** | "Should this service be reactive at all, now that virtual threads exist?" is the canonical principal-tier framework-judgment question — and the module already answers it well. |
| `database_migrations` | principal | **senior, principal** | Writing a Flyway migration and knowing why a checksum changed is day-one craft; expand-contract is the principal half. It belongs in both. |
| `spring_grpc` | `-` | **senior** | Boot 4.1 made gRPC first-class with its own starters, `@ImportGrpcClients` and a test slice. No longer exotic. |
| `spring_batch` | `-` | **senior** | Enterprise Java JDs name Spring Batch constantly. Lower confidence than the rows above — depends on the owner's target job profile. |
| `spring_security_modern_auth` (new) | — | **senior, principal** | Craft (wire a passkey ceremony) and judgment (bearer vs DPoP, buy vs build the IdP) in equal parts. |
| `spring_boot_4_migration` (new) | — | **principal** | Pure judgment: sequencing, blast radius, the Spring Cloud lag, when *not* to migrate. |
| `startup_and_aot_cache.md` (new sub-file) | — | **principal** (under `spring_performance`) | Startup-cost engineering is a platform decision. |
| `mcp_servers_and_clients.md` (new sub-file) | — | **senior** (under `spring_ai`) | Follows its parent into the senior tier. |

The tier tables between `<!-- study-path-table -->` markers are generated — regenerate with
`python3 game/extract.py --write-paths`, never hand-edit.

---

## 4. Depth — THIN modules and BLOAT

Section average is ~999 lines/module and 18.7 Q&As/module. **No module is below the hard floor**;
every file below is a relative-thinness call. Six independent full-read passes returned zero THIN
verdicts on content quality — the gaps are topical, not structural.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `observability_and_tracing` | **611** | 15 | Thinnest module in the section, on the topic Boot 4 changed most: `spring-boot-starter-opentelemetry`, OTLP logs, exemplar/batch-processor config, `management.opentelemetry.enabled`, the automatic `@Async` context propagation that retires its own §6.7 fix, Micrometer never pinned past "1.x". Also missing the Micrometer-Tracing-bridge vs OTel-Java-agent decision — the actual deployment choice. | +250 lines, +5 Q&As; update `cross_cutting/otel_observability_for_spring.md` in the same pass. |
| `validation_and_error_handling` | **625** | 16 | §14 is a **stub** (`**Quick scenario:**` + a pointer) where peer modules carry 100+ line inline case studies. No Jakarta Validation 3.1 / Hibernate Validator 9.1 pin. | Real §14 (a public API's validation + `ProblemDetail` contract across three client types). +150 lines. |
| `spring_ai` | **650** | 16 | Superseded generation (§2) plus: no MCP at all, no advisor-chain tool loop, no evaluation, no cost/token budgeting, no agent-loop architecture. | Rewrite to 2.0 (+250 lines) and add the two proposed sub-files. **Highest-leverage single file in the section.** |
| `spring_events_and_scheduling` | **674** | 16 | §14 is a **stub** (`**Quick scenario:**`). No numbers comparing ShedLock vs Quartz clustering vs a DB advisory lock. | Real §14 (a scheduler that double-fired across a rolling deploy). +150 lines. |
| `spring_batch` | **712** | 15 | §14 is a **stub** (`**Quick summary:**`), plus the whole Batch 5→6 rewrite from §2. | Rewrite for Batch 6 + real §14. +250 lines, +3 Q&As. |
| `spring_boot_autoconfiguration` | **642** | 15 | No coverage of Boot 4's **modular starter** restructuring — the single most visible Boot 4 change to a reader's `pom.xml`. | +150 lines in §4/§6; cross-link the migration module. |
| `spring_modulith` | **709** | 16 | Version rewrite (2.1), `@ModuleSlicing`, externalization engines (JobRunr, Namastack). | +150 lines. |
| `spring_native_graalvm` | **742** | 16 | GraalVM 22.3 → 25, the "exact reachability metadata" format change, and **no comparison against the JDK AOT cache as the cheaper first option** — §8's table has only two columns. | +200 lines; cross-link the new startup sub-file. |
| `spring_transactions` | 1259 | 20 | Deep and current, but **zero** virtual-thread content on a Java 25 baseline (see §1). | +200 lines. |
| `ioc_container` | **683** | 15 | `BeanRegistrar` (Framework 7 programmatic bean registration) belongs here or in `spring_configuration` — currently in neither, though `spring/CLAUDE.md` lists it as a Framework 7 feature. | +80 lines. |
| `spring_integration` | **721** | 16 | Complete for its scope; flagged only for line count and the version tag. | Version tag only. |

### Bloat / merge / delete

- **`spring_configuration/spring_configuration.md` (826 lines) has a real duplication defect.** A
  bolted-on "Additional war stories and interview Q&As" block *after* `## 14` re-explains
  `@Configuration` full-vs-lite mode **three times** total, and `@Import` vs `@ComponentScan` and
  `@Conditional` mechanics **twice each**. This reads as an unmerged accretion, not intentional
  depth. **Fix: merge the addendum into §10/§12 and delete the duplicates** (net −120 lines). This
  is the only genuine bloat in the section.
- **`dependency_injection.md` Q7 and Q14 are near-duplicate questions** — both "`ObjectProvider` vs
  `@Autowired(required=false)`". The bank draws distractors from sibling first sentences, so two
  near-identical answers weaken both items. Merge into one and write a replacement Q&A.
- **Little's Law is derived in full three separate times** — `spring_mvc_architecture` §8,
  `spring_webflux` §2, `filters_and_interceptors` §6.5. Defensible for standalone modules, but one
  should own the derivation and the other two cite it. Low priority.
- **No module should be deleted or merged.** All 39 have a distinct topic. `spring_data_nosql` vs
  `spring_data_jpa` and `spring_integration` vs `spring_messaging` both looked like merge candidates
  on the module table and are not on inspection — each contrasts itself with its sibling explicitly
  and cross-links it.

---

## 5. Cross-section notes

| Item | Other section | Note |
|---|---|---|
| `spring/database_migrations/` vs `backend/database_migrations/` | `backend` | **Direct directory-name collision** across sections. The Spring file already self-scopes ("how Flyway/Liquibase and Spring wire that theory into an actual deploy pipeline") but the Spring Cross-Reference Map does **not** link to the backend module. Add a reciprocal cross-link. |
| `spring_native_graalvm/` vs `java/graalvm_native_image/` | `java` | Nearest-neighbour pair in the repo. The proposed AOT-cache sub-file should live on the **Spring** side (it is a `spring-boot-maven-plugin` + training-run story), but `java/graalvm_native_image` needs a pointer — "native image or AOT cache?" is now the live question, and the Java module may carry the same GraalVM-22.3-era staleness. **Flag for the `java` lead auditor.** |
| `spring_testing/` vs `java/testing_junit_mockito/` + `backend/backend_testing_strategies/` | `java`, `backend` | Three-way overlap, currently cross-linked cleanly. The Boot 4 test changes (`@MockitoBean`, `@SpringBootTest` no longer auto-providing MockMvc, `RestTestClient`, JUnit 6) belong to the Spring one; the other two should not duplicate them. |
| `spring_security_modern_auth` (proposed) vs `backend/auth_and_authorization_systems/` | `backend` | Passkeys/DPoP/OAuth 2.1 are protocol topics the backend module may also want. Keep the Spring module about the **DSL and the `AuthorizationManager` factories**; cite the backend module for the protocol. **Worth a note to the `backend` lead auditor** so both are not written from scratch. |
| `spring_ai/` vs `llm/` | `llm` | Already cross-links `llm/advanced_rag`, `llm/agentic_frameworks`, `llm/agents_and_tool_use`, `llm/embeddings_and_similarity_search` — theory there, framework wiring here. **The MCP addition needs coordination**: if `llm/` grows an MCP module (protocol-level), the Spring one must stay Spring-specific (`@McpTool` on a bean, OAuth2 on the endpoint) and cross-link rather than re-explain the protocol. |
| `observability_and_tracing/` vs `backend/observability_and_monitoring/` + `devops/observability_tracing_and_otel/` | `backend`, `devops` | Angles are distinct (app-side config here, collector policy in devops, SLI/SLO in backend) but sampling strategy is covered in real depth in two places. The `spring-boot-starter-opentelemetry` gap is Spring-side. |
| `spring_transactions/` vs `database/concurrency_control_and_locking/` + `backend/distributed_transactions_and_consistency/` | `database`, `backend` | Clean split (proxy/propagation semantics here, MVCC and 2PC there), already cross-linked. No action. |
| `spring_grpc/` | `java`, `backend` | Correctly delegates wire format to `java/grpc_protobuf` and LB/mesh to `backend/grpc_and_protobuf`. Model cross-link — copy this pattern elsewhere. |
| `spring_webflux/` | `java` | Cross-links `java/reactive_programming` and `java/structured_concurrency_and_loom`. Clean. |

---

## 6. Execution plan

**Ordering constraint from the root `CLAUDE.md`:** any pass that changes an answer MUST re-read and
update the `**Short:**` line above it in the same edit. Every correction below touches answer
bodies, so this applies throughout.

### Wave 1 — Corrections (fully parallel, one agent per file, no structural changes) — ~16 h

1. `spring_batch/spring_batch.md` — Batch 5 → Batch 6 rewrite (`JobLauncher`→`JobOperator`,
   `JobExplorer` merged into `JobRepository`, `ChunkOrientedStep`, `CommandLineJobOperator`,
   `recover()`, graceful shutdown, package relocations), plus a real §14. **3 h**
2. `spring_ai/spring_ai.md` — Spring AI 1.0 → 2.0 rewrite (advisor-chain tool loop, Jackson 3,
   JSpecify, Boot 4.0/4.1 baseline, the 8 core providers, `Observation`-in-Boot-**4**). **3 h**
3. `spring_native_graalvm/spring_native_graalvm.md` — GraalVM 22.3 → 25 (header L7 and §14 L633),
   exact-reachability-metadata format, add the AOT-cache column to §8's tradeoff table. **2 h**
4. Redis serializer sweep, one commit across 3 files — `GenericJackson2JsonRedisSerializer` →
   `GenericJacksonJsonRedisSerializer` in `spring_data_nosql.md` (7 sites), `spring_session.md`
   (5 sites), `spring_performance.md` (1 site). `spring_caching.md` is already correct; use it as
   the reference. **1.5 h**
5. `spring_modulith/spring_modulith.md` — 1.x/Boot 3.1 → 2.1/Boot 4.1, `@ModuleSlicing`,
   externalization engines. **1.5 h**
6. `spring_session/spring_session.md` — Session 3.x→4.1; **remove Hazelcast and MongoDB from the
   first-class backend list** and note they left Boot in 4.0. **1.5 h**
7. `spring_grpc/spring_grpc.md` — add Boot 4.1 native auto-config, `@ImportGrpcClients`, the gRPC
   test slice, `GrpcChannelBuilderCustomizer`, observation interceptors; demote the
   standalone/`net.devh` framing to "if you are on Boot 4.0 or earlier". Read
   https://docs.spring.io/spring-boot/reference/io/grpc.html for the literal artifact ids. **2 h**
8. `observability_and_tracing/` + `case_studies/cross_cutting/otel_observability_for_spring.md` —
   `spring-boot-starter-opentelemetry`, OTLP logs/exemplars, Micrometer 1.17/Tracing 1.7, and
   replace the manual `@Async` fix with "Boot 4.1 does this for you; here is what still needs a
   wrapper". Both files in one commit. **2 h**
9. `spring_cloud_patterns.md` + `spring_messaging.md` — the **Spring Cloud train lag** caveat
   (Oakwood → Boot 4.0.x only) and the core-`@Retryable` name-collision note. **1 h**
10. `spring_cloud_config.md` reframe Boot 3.x→4.1; `spring_integration.md` header
    "Integration 6.x / Boot 3.x" → "7.1 / Boot 4.1"; `spring_data_nosql.md` §14 repin to Boot 4.1 /
    Java 25. **1.5 h**
11. Index fixes, one commit: `spring/README.md` L525 SAS `1.x`→`7.1`;
    `spring_security_jwt_oauth.md` §11 L680 SAS `1.5.x`→`7.1`; `spring/CLAUDE.md` rows for
    `spring_ai` (1.0→2.0), `spring_modulith` (Boot 3.1+→2.1/Boot 4.1), `spring_native_graalvm`
    (Boot 3.0+→Boot 4.1); `spring/CLAUDE.md` sub-file list 2→4 files. **1 h**

### Wave 2 — Fill the promised-but-untaught Framework 7 surface (parallel) — ~11 h

Everything the `README.md` version matrix advertises and no module teaches.

12. `spring_proxies.md` — `@Proxyable` + CGLIB-as-framework-wide-default in §4/§6; add the reference
    note to `pointcut_designators.md` §1's baseline paragraph. **2 h**
13. `spring_configuration.md` — `BeanRegistrar`; **and in the same commit, merge the post-§14
    addendum and delete the triple full-vs-lite explanation** (§4). **2.5 h**
14. `spring_hateoas_rest_maturity.md` — first-class API versioning (`@RequestMapping(version=…)`,
    `ApiVersionConfigurer`, `spring.mvc.apiversion.*`) into the existing §4 table and §12 Q&A; add
    `@ImportHttpServices`. **2 h**
15. `spring_transactions.md` — `@Transactional` under virtual threads: `ThreadLocal`-bound
    `EntityManager`/connection binding, the HikariCP-pool relocation of the queue, broken→fixed.
    **1.5 h**
16. `spring_http_clients.md` — promote `InetAddressFilter` from a §11 table row to a real §10
    pitfall with a broken→fixed SSRF example. **1 h**
17. `spring_testing.md` — `RestTestClient`, the `@SpringBootTest`-no-longer-gives-you-MockMvc trap,
    and rewrite the `@DynamicPropertySource` examples to lead with `@ServiceConnection`. **1.5 h**
18. `spring_boot_autoconfiguration.md` — Boot 4 modular starters. **1.5 h**
19. `dependency_injection.md` — merge duplicate `ObjectProvider` Q&As, write a replacement. **0.5 h**

### Wave 3 — New content (parallel; 22 and 25 depend on item 2) — ~34 h

20. **P0** `spring_security_modern_auth/` — new module, 950 lines, 20+ Q&As. **8 h**
21. **P0** `spring_boot_4_migration/` — new module, 900 lines, 18+ Q&As. **8 h**
22. **P0** `spring_ai/mcp_servers_and_clients.md` — sub-file, 800 lines, 15+ Q&As. **6 h**
23. **P0** `spring_performance/startup_and_aot_cache.md` — sub-file, 550 lines, 15+ Q&As. **4 h**
24. **P1** `spring_cloud_patterns/core_resilience_framework7.md` — sub-file, 500 lines. **4 h**
25. **P1** `spring_ai/agents_and_tool_calling.md` — sub-file, 600 lines. **4 h**

### Wave 4 — Depth top-ups (parallel) — ~7 h

26. Real §14 case studies for `validation_and_error_handling` and `spring_events_and_scheduling`
    (and `spring_batch`'s, if not done in item 1). **3 h**
27. `spring_graphql` — federation code + the "no native federation in Spring for GraphQL" caveat,
    trusted-documents terminology, a real `maxQueryDepth`/complexity config sample. **2 h**
28. `ioc_container` — `BeanRegistrar` cross-reference if item 13 put it in `spring_configuration`.
    **0.5 h**
29. Little's Law: make `spring_mvc_architecture` the owner; `spring_webflux` and
    `filters_and_interceptors` cite it. **1 h**

### Wave 5 — Restructure (SERIAL, must be last) — ~11 h

30. **P1** `case_studies/design_mcp_tool_platform.md` — 11-section principal case study, 1000 lines;
    update `case_studies/case_studies.md` (all 5 mandated sections + dependency map + interview
    shortcuts) and its `<!-- study-paths -->` block **in the same commit**. **6 h**
31. Apply the sequence + tier changes from §3. One commit, touching:
    - `STUDY_ORDER.spring` in `game/app.js`
    - the `<!-- study-paths -->` block in `spring/README.md` (2 new modules, 4 new sub-files,
      10 tier moves, 1 new case study)
    - the 8-Phase diagram, module table and Cross-Reference Map in `spring/README.md`
    - the 5-week Study Plan in `spring/README.md` (it names modules by phase)
    - the module table, sub-file list, Q&A minimums and Planned-Topics table in `spring/CLAUDE.md`
    - the Spring phase tables in the **root** `README.md`

    Then `python3 game/extract.py --write-paths`, then `python3 game/extract.py --strict`, then
    **the whole of `scripts/build_banks.sh`** — `--strict` passing does not mean CI passes, the
    script's own verifier runs after it. Also run `node scripts/check_render.mjs <file>` on every
    file with a touched Mermaid fence. **5 h**

**Parallelism.** Waves 1, 2 and 4 are fully internally parallel — one agent per file, no shared
files, except items 8, 9 and 10 which each bundle 2–3 files into one commit. Wave 3's items 22 and
25 depend on item 2. **Wave 5 item 31 must be serial and last**: every earlier wave that adds a file
makes the `<!-- study-paths -->` block and `STUDY_ORDER` stale, and a file on disk missing from that
block is **fatal** under `--strict` and takes the Pages deploy red.

**Total: ~79 hours.** The P0-only path (Wave 1 + Wave 2 + items 20–23 + item 31) is ~58 h. The
single highest-value 9 hours is items 2 + 22 (Spring AI 2.0 + MCP).

---

## 7. Confidence and gaps

**High confidence.** Every version claim in §2 was web-verified against a primary source (Spring
blog, project reference docs, GitHub release notes/wiki) with the URL cited inline. The structural
facts — 14-section compliance, Q&A counts, `**Short:**` coverage, Mermaid counts, tier membership,
`STUDY_ORDER` positions, line counts — were measured programmatically over the live tree, not
estimated. Every one of the 43 module files was read in full by a subagent, and I additionally read
`spring_grpc`, `spring_batch`, `spring_session`, `spring_modulith`, `spring_ai`,
`spring_security_architecture` and `spring_security_jwt_oauth` myself for the claims I make about
them. The absence findings (MCP, passkeys, MFA, DPoP, one-time token, `@Proxyable`, `BeanRegistrar`,
AOT cache, API versioning, virtual threads in `spring_transactions`) were established by
case-insensitive greps over every `.md` in the section **and** independently confirmed by subagents
reading the candidate files.

**The four internal contradictions in §2 are the strongest findings**, because they need no external
judgment — the section already disagrees with itself: `spring_batch` vs its own case study on Batch
6; `spring_native_graalvm` (GraalVM 22.3) vs its own README (GraalVM 25); `spring_security_jwt_oauth`
(SAS 1.5.x) vs its own case study (SAS 7.1); three files on `GenericJackson2JsonRedisSerializer` vs
`spring_caching` on `GenericJacksonJsonRedisSerializer`.

**What I could not verify.**

- **Spring Boot 4.2 / Framework 7.1 status.** Framework 7.1 is scheduled for November 2026 and is
  not out. If it ships milestones before this plan executes, the `RestTemplate` deprecation line in
  `spring_http_clients.md` / `spring_hateoas_rest_maturity.md` moves from "scheduled" to "shipped"
  and must be re-checked. Nothing in the plan depends on it.
- **Whether Spring Cloud has cut a Boot 4.1 train since 2026-08-20.** The supported-versions wiki I
  fetched reads as partially stale (it lists Oakwood as "under development" though it GA'd
  2025-11-25). I anchored the lag claim on the 2025.1.3 release post (2026-08-20, Boot 4.0.8), which
  is five days old. Re-check before writing the migration module's §8.
- **Exact Boot 4.1 gRPC starter artifact ids.** InfoQ and the Boot reference confirm the feature,
  `@ImportGrpcClients`, and the test slice, but I did not obtain the literal `groupId:artifactId`
  strings. Whoever executes item 7 should read the Boot gRPC reference page directly rather than
  trusting any starter name inferred here.
- **Whether `spring-boot-starter-webmvc` is the exact Boot 4 name** for the split web starter. The
  section's README already asserts it and the migration guide confirms the modular-starter pattern
  in general (`spring-boot-starter-flyway`, `-liquibase` are quoted verbatim), but I did not
  independently confirm every starter name. Verify against start.spring.io before item 18.
- **Job-post evidence.** I used conference-track composition (Spring I/O 2026, 13th edition,
  Barcelona, April 13-15, 60+ sessions) and release content as the demand signal rather than scraped
  job posts, which I could not access. The Spring AI/MCP prioritisation would be stronger with 2026
  JD data; the Boot 4 migration and modern-auth prioritisations stand on the release notes alone.
- **Flyway/Liquibase current majors.** `database_migrations.md`'s §14 pins "Flyway 10.x" as a
  scenario stack and dates itself to Boot 3.3 / Java 17. I did not verify whether Flyway 11+ is out
  and whether the §11 table needs a bump. Cheap to check; deliberately excluded from §2 because I
  could not confirm it.
