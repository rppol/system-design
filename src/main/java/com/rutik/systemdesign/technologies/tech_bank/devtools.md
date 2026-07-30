# Build & test tooling — technology bank

<!-- tech-bank tier: devtools -->

The 320 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Build & test tooling** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### @ApplicationModuleTest
**Short:** Spring Modulith annotation bootstrapping a single application module for testing, isolated from the rest of the app.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @3

### @EmbeddedKafka
**Short:** Spring Kafka annotation starting an in-process broker for tests, lighter than a Testcontainers Kafka.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, data-movement/event-streaming-and-processing @3

### @GraphQlTest slice
**Short:** Spring test slice that loads only the GraphQL layer so resolvers can be tested without the full context.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/rpc-graphql-and-streaming @2

### @GuardedBy
**Short:** Annotation documenting which lock guards a field, so static analysers can flag unsynchronized access.
**Kind:** api
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/concurrency-and-async @2

### @ImportRuntimeHints
**Short:** Spring AOT annotation registering reflection, resource and proxy hints a GraalVM native image needs.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @3

### @MockitoBean
**Short:** Spring Boot annotation replacing a bean in the test ApplicationContext with a Mockito mock; the successor to @MockBean.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### @RegisterReflectionForBinding
**Short:** Spring AOT annotation registering types for reflective binding so they survive a GraalVM native-image build.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### @SpringBootTest
**Short:** Annotation that loads a full Spring application context for integration tests, with @MockitoBean for bean overrides.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/dependency-injection-and-config @3

### @SpringIntegrationTest
**Short:** Spring Integration test annotation that boots the integration context with endpoints under test control.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### aioresponses
**Short:** Pytest-friendly mock that intercepts aiohttp requests so async HTTP calls can be stubbed in tests.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### ansible-lint
**Short:** Linter for Ansible playbooks and roles catching non-idempotent tasks and config-management anti-patterns.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, platform-delivery/infrastructure-as-code-and-config @2

### ANTLR
**Short:** Parser generator that turns a grammar into an LL(*) lexer and recursive-descent parser in many target languages.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @3

### ANTLR 4
**Short:** Parser generator: a .g4 grammar becomes a lexer, parser, parse tree and visitor/listener base classes.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @3, runtime-systems/text-encoding-and-regex @3

### ANTLR 4 generated BaseVisitor
**Short:** Generated visitor base class for an ANTLR grammar; you control recursion and return a value per parse-tree node.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### AopTestUtils.getTargetObject
**Short:** Spring test utility that unwraps an AOP proxy to reach the underlying target object.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/aop-middleware-and-scheduling @2

### AotProcessor
**Short:** Spring AOT entry point that generates bean-definition and reflection code at build time for GraalVM native images.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/dependency-injection-and-config @3

### Apache Maven
**Short:** Declarative JVM build tool: POM-driven dependency resolution, lifecycle phases and artifact publishing.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### ApplicationContextRunner
**Short:** Spring Boot test harness that boots a throwaway context to assert auto-configuration outcomes.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/dependency-injection-and-config @2

### ApplicationModules.verify
**Short:** Spring Modulith check that fails a test when code crosses a module boundary it is not allowed to cross.
**Kind:** api
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/testing-and-mocking @2

### ArchUnit
**Short:** Java library that asserts architecture rules (layering, package cycles, forbidden access) as ordinary JUnit tests.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @3

### argparse
**Short:** Python standard-library command-line argument parser with subcommands, types and generated help.
**Kind:** api
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1

### Artifact registries
**Short:** Category of services that store, version and promote build artifacts and container images between environments.
**Kind:** concept
**Lang:** *
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @2, platform-delivery/container-and-image @3

### ASM
**Short:** Low-level visitor-based bytecode reader/writer; the substrate under most JVM instrumentation and proxy libraries.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### ASM ClassVisitor
**Short:** ASM's streaming bytecode visitor; chain visitors to inspect or rewrite class files as they are parsed.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### AssertJ
**Short:** Fluent Java assertion library with chainable matchers and far clearer failure messages than JUnit asserts.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### AutoValue
**Short:** Google annotation processor that generates immutable value classes with equals/hashCode/toString and builders.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @3

### Awaitility
**Short:** Java test DSL for asserting asynchronous outcomes: await().atMost(5, SECONDS).until(...) instead of Thread.sleep.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @3

### AWS Fault Injection Service
**Short:** Managed chaos-engineering service injecting EC2, ECS, RDS and EKS faults with CloudWatch stop conditions.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @3, platform-delivery/cloud-platform-and-cost @3

### bandit
**Short:** Python SAST linter that flags shell=True, pickle.loads, hardcoded passwords and other insecure code patterns.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2

### BaseListener
**Short:** ANTLR-generated no-op listener base class: override the rule callbacks you care about while the walker drives recursion.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### bash
**Short:** The GNU shell: interactive command environment and the scripting language most automation and container entrypoints use.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, runtime-systems/memory-processes-and-os @3

### Bazel
**Short:** Hermetic, cached, multi-language build system whose dependency graph enables affected-target builds in monorepos.
**Kind:** tech
**Lang:** *
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @3

### bison
**Short:** GNU parser generator that turns a grammar file into an LALR bottom-up parser, usually paired with flex for lexing.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @3

### Bruno
**Short:** Open-source API client that stores collections as plain files in your repo, a git-friendly Postman alternative.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @2, devtools/version-control-and-workbench @3

### Byte Buddy
**Short:** Typed DSL for generating and subclassing Java classes at runtime or build time; the engine behind Mockito.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2, devtools/testing-and-mocking @3

### ByteBuddy
**Short:** Runtime bytecode generation library used to build dynamic proxies and subclasses; powers Mockito and Hibernate.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, devtools/testing-and-mocking @3

### Bytecode Viewer
**Short:** GUI tool that decompiles and edits Java class files so you can inspect the bytecode the compiler emitted.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### cassandra-stress
**Short:** Cassandra's bundled load-generation and benchmarking tool for stressing a cluster with a chosen schema and workload mix.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, data-stores/wide-column @2, observability/profiling-and-performance @3

### CGLIB
**Short:** Bytecode library generating runtime subclasses; the proxy mechanism Spring AOP uses for classes without interfaces.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/aop-middleware-and-scheduling @2

### Chaos Monkey
**Short:** Netflix tool that randomly kills production instances to prove failover and circuit breakers actually work.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @3

### Chaos Monkey for Spring Boot
**Short:** Library injecting latency, exceptions and killed beans into a running Spring Boot app for chaos experiments.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @3

### Chaos Toolkit
**Short:** Open-source chaos experiment runner driving JSON/YAML fault-injection experiments from CI with a plugin ecosystem.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @2

### Chaos tools
**Short:** Umbrella label for fault-injection tooling used to validate SLOs and resilience under induced failure.
**Kind:** concept
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, observability/alerting-and-incident-response @3

### ChaosBlade
**Short:** Open-source chaos engineering toolkit injecting network, CPU, memory, process and container faults to test resilience.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @2

### Checkstyle
**Short:** Syntactic Java style/complexity gate: class and method length, nesting depth, magic numbers, parameter counts.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, apis-frameworks/design-patterns-and-principles @3

### clang
**Short:** LLVM's C/C++/Objective-C compiler front end: AOT native compilation plus sanitizers and static analysis.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/static-analysis-and-linting @3

### click
**Short:** Python library for building command-line interfaces from decorators, with nested subcommands and rich help.
**Kind:** tech
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1

### CMake CUDA_ARCHITECTURES
**Short:** CMake target property selecting GPU architectures to compile for; -real/-virtual map to sm_XX/compute_XX.
**Kind:** api
**Lang:** cpp
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @2, gpu/kernel-programming @3

### CodeQL java/redos
**Short:** CodeQL query that dataflow-traces untrusted input into a catastrophically backtracking regex in GitHub code scanning.
**Kind:** api
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2, runtime-systems/text-encoding-and-regex @3

### Conventional Commits
**Short:** Commit-message convention (feat:, fix:, BREAKING CHANGE) that drives automated semantic versioning and changelogs.
**Kind:** spec
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, platform-delivery/ci-cd-and-release @3

### Coverage delta
**Short:** Per-PR coverage of newly added lines only, the figure worth gating on instead of total project coverage.
**Kind:** concept
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### covered
**Short:** Coverage terminology: a statement counted as covered because the test run executed it at least once.
**Kind:** concept
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### CUP
**Short:** Java LALR parser generator with grammar actions written inline in Java; used for legacy grammars.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### DataGrip
**Short:** JetBrains database IDE for browsing schemas, writing SQL and reviewing generated migration diffs before merge.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, data-access/schema-and-migration @2

### DBeaver
**Short:** Cross-database GUI client for browsing schemas, running SQL and reviewing generated migration or changelog diffs.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, data-access/schema-and-migration @2, data-stores/relational @3

### Develocity build cache
**Short:** Gradle Develocity's remote build cache, sharing task and goal outputs across machines and CI to skip repeat work.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @3

### diff2html
**Short:** JavaScript library that renders a unified git diff as side-by-side or inline HTML.
**Kind:** tech
**Lang:** js
**Roles:** devtools/version-control-and-workbench @1

### Documenter
**Short:** Spring Modulith class that renders module structure and dependencies as C4/PlantUML diagrams and canvases.
**Kind:** api
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, apis-frameworks/design-patterns-and-principles @3

### Eclipse
**Short:** Java IDE with refactoring, its own incremental compiler and generators for equals/hashCode/toString.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, devtools/compiler-toolchain-and-codegen @3

### Error Prone
**Short:** Google's javac plugin that turns a large catalogue of Java bug patterns into compile errors with suggested fixes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/compiler-toolchain-and-codegen @3

### factory_boy
**Short:** Python test-object factory library that builds model/ORM instances for fixtures instead of hand-written setup.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### fail_under = 80
**Short:** Coverage threshold setting that makes pytest exit non-zero, and CI fail, below the given percentage.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, platform-delivery/ci-cd-and-release @3

### faker
**Short:** Generates realistic fake names, addresses and other field data for test fixtures and factories.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, ml-lifecycle/labeling-and-synthetic-data @3

### fakeredis
**Short:** In-process fake implementing the Redis command surface so tests run without a real Redis server.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, caching/distributed-cache @3

### fatbinary
**Short:** CUDA toolchain utility bundling several cubins and PTX images into one fat binary; nvcc invokes it internally.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1, gpu/kernel-programming @3

### Feature
**Short:** GraalVM build-time hook for registering reflection, resource and other reachability metadata programmatically.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### FieldVisitor
**Short:** ASM visitor for class fields, chainable so each visitor inspects or rewrites bytecode and delegates onward.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### Flapdoodle Embedded MongoDB
**Short:** Starts a real MongoDB process inside a JVM test run for fast integration tests; no replica set, so no transactions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, data-stores/document @3

### flex
**Short:** Lexer generator that emits C scanning code from a regular-expression grammar; usually paired with bison.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1

### freezegun
**Short:** Pytest-friendly library that freezes or travels datetime.now() so time-sensitive code is deterministic.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### FunctionModel
**Short:** Pydantic AI test double replacing the LLM with your own function so agent logic is deterministic.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, llm-apps/agent-framework @2

### Gatling
**Short:** JVM load-testing tool with a code-first simulation DSL, high per-node throughput and detailed HTML reports.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, observability/profiling-and-performance @3

### gcc
**Short:** The GNU Compiler Collection: ahead-of-time compiler turning C/C++ source into native machine code.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1

### GenAI-Perf
**Short:** NVIDIA's LLM-aware load generator: measures TTFT, inter-token latency and throughput against a streaming endpoint.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, inference/model-server @2, observability/profiling-and-performance @3

### git
**Short:** The distributed version control system - branching, history and the baseline for versioning code, config and prompts.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

### git filter-repo
**Short:** Fast git history rewriter that purges leaked secrets or large blobs from every commit; replaces filter-branch.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, security/secrets-and-cryptography @2

### Git LFS
**Short:** Git extension storing large binaries outside the repo and leaving pointer files under version control.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, ml-lifecycle/experiment-tracking-and-tuning @3, data-movement/data-quality-and-lineage @3

### git reflog
**Short:** Git's local log of where HEAD and branch tips pointed; the recovery path after a bad reset or rebase.
**Kind:** api
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

### git-lfs
**Short:** Git extension storing large binaries out of band as pointers so clones stay small.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, data-stores/object-and-file-storage @3

### GitHub/GitLab/Bitbucket
**Short:** Hosted Git platforms providing pull requests, protected branches, CODEOWNERS review rules and CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, platform-delivery/ci-cd-and-release @2

### Google AutoService
**Short:** Annotation processor that generates META-INF/services entries so a class is discoverable by ServiceLoader.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/dependency-injection-and-config @3

### Google AutoValue
**Short:** Java annotation processor generating immutable value classes with equals, hashCode, toString and a builder.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### Google compile-testing
**Short:** Test library that compiles source in-process and asserts on annotation-processor output and diagnostics.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/compiler-toolchain-and-codegen @2

### GraalVM
**Short:** Polyglot JVM with an AOT native-image compiler producing standalone binaries with millisecond startup and low RSS.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, platform-delivery/container-and-image @3

### GraalVM CE
**Short:** JDK distribution whose native-image AOT compiler turns a JVM application into a fast-starting native binary.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### GraalVM native-image
**Short:** Ahead-of-time compiler that turns a JVM application into a self-contained native binary with fast startup.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### GraalVM Reachability Metadata Repository
**Short:** Shared reflection/resource metadata for popular libraries so native-image builds work without hand-written hints.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @3

### GraalVM tracing agent
**Short:** JVM agent recording reflection, resource, proxy and JNI use so native-image gets the metadata it needs.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### gradio ChatInterface
**Short:** Gradio helper that wraps a Python chat function in a ready-made web chat UI for demos.
**Kind:** api
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1, llm-apps/agent-framework @2

### Gradle
**Short:** JVM build tool with a Kotlin or Groovy DSL, incremental tasks and a build cache; also builds Android and native code.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @3

### Gradle annotationProcessor
**Short:** Gradle configuration that puts a jar on the annotation-processor path so it runs during compilation.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @2

### gradle dependencies
**Short:** Gradle task printing the resolved dependency tree per configuration, showing conflicts and version selection.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### gradle dependencyInsight
**Short:** Gradle task explaining why a dependency version was selected and which path pulled it in - the conflict-resolution tool.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Gradle Enterprise
**Short:** Commercial Gradle/Maven build platform (now Develocity): remote build cache, build scans and test acceleration.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Gradle failOnVersionConflict
**Short:** Gradle resolution strategy that fails the build on any transitive dependency version conflict instead of picking one.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Gradle GraalVM plugin
**Short:** Gradle plugin wiring native-image compilation, metadata and native tests into the normal build.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @2

### Gradle protobuf plugin
**Short:** Gradle plugin running protoc during the build to generate Java and gRPC stubs from .proto into a source set.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2, apis-frameworks/rpc-graphql-and-streaming @3

### Gradle Shadow plugin
**Short:** Gradle plugin that builds a fat/uber JAR and relocates package names to avoid dependency clashes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Gradle version catalogs
**Short:** Gradle feature centralizing dependency coordinates and versions in one TOML file shared across all modules of a build.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Gradle Wrapper
**Short:** Checked-in script and properties file that pin the Gradle version, so every machine builds with the same toolchain.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Grafana k6 Cloud
**Short:** Managed k6 service running large geo-distributed load tests with hosted results and trend comparison.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### graphql-code-generator
**Short:** Generates typed clients, resolvers and operation types from a GraphQL schema and documents.
**Kind:** tech
**Lang:** js
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/rpc-graphql-and-streaming @2, apis-frameworks/data-formats-and-api-contracts @3

### GraphQlTester
**Short:** Spring for GraphQL test client that executes documents against the schema and asserts on JSON paths and errors.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/rpc-graphql-and-streaming @2

### Gremlin
**Short:** Commercial chaos-engineering SaaS injecting latency, CPU, and shutdown faults with a scheduled blast-radius control.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @2, observability/alerting-and-incident-response @3

### hatch
**Short:** Modern Python project manager: PEP 517 builds, environment management, scripts and publishing from pyproject.toml.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

### Hurl
**Short:** Plain-text HTTP test runner that chains requests and asserts on status, headers and JSON bodies from CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

### Husky
**Short:** Node.js git hook manager that wires lint, format and test commands into commit and push hooks.
**Kind:** tech
**Lang:** js
**Roles:** devtools/version-control-and-workbench @1, devtools/static-analysis-and-linting @2

### hypothesis
**Short:** Python property-based testing library that generates and shrinks inputs to find edge cases your examples missed.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### hypothesis-jsonschema
**Short:** Generates Hypothesis property-test inputs directly from a JSON Schema, such as a Pydantic model's schema.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @2

### IDE "expand inferred type
**Short:** IDE action that displays the compiler's inferred generic type inline, exposing an unintended Object inference.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, runtime-systems/runtime-internals-and-types @2

### Immutables
**Short:** Annotation processor that generates immutable value classes with builders and build-time mandatory-field checks.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### incremental APT
**Short:** Declaring an annotation processor incremental so Gradle can rerun it only for changed sources, not the whole build.
**Kind:** concept
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2

### Insomnia
**Short:** Desktop API client for composing and replaying HTTP, GraphQL and gRPC requests against a running service.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, devtools/version-control-and-workbench @2, apis-frameworks/web-framework-and-http-client @3

### InSpec
**Short:** Executable compliance framework that asserts the actual state of a machine matches policy after configuration.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/infrastructure-as-code-and-config @2, security/privacy-and-compliance @3

### IntelliJ IDEA
**Short:** JetBrains IDE with code generation, refactoring, inspections and debuggers including a stream-stage trace debugger.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, devtools/static-analysis-and-linting @3, observability/profiling-and-performance @3

### IntelliJ IDEA inspections and refactorings
**Short:** IDE static analysis with one-click fixes such as replace inheritance with delegation and duplicate detection.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, devtools/version-control-and-workbench @2

### IntelliJ Stream Trace Debugger
**Short:** IntelliJ debugger view that shows the elements entering and leaving each stage of a Java Stream pipeline.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, observability/profiling-and-performance @3

### IntelliJ structural replace
**Short:** IntelliJ feature matching and rewriting code by AST pattern, e.g. finding == comparisons on reference types.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, devtools/static-analysis-and-linting @2

### IPython autoawait
**Short:** IPython/Jupyter feature allowing top-level await in a cell because the notebook already runs an event loop.
**Kind:** api
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1, runtime-systems/concurrency-and-async @2

### Jacoco
**Short:** JVM code-coverage agent and report generator, used as a signal for untested regions rather than a target.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @2

### japicmp
**Short:** Build plugin that diffs two JARs and fails the build on binary- or source-incompatible API changes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/build-and-dependency-management @2

### java.time.Clock
**Short:** Injectable source of the current instant, with fixed, offset and tick variants that make time-dependent code testable.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @3

### javac
**Short:** The Java compiler: source to bytecode, and the host for annotation processors and plugins like Error Prone.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @3

### javac --module-path
**Short:** javac flag compiling against JPMS modules on a module path rather than (or alongside) the flat classpath.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javac -g
**Short:** Compiles with full debug info, keeping LocalVariableTable and LineNumberTable in the class file for debuggers.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javac -Xdiags:verbose
**Short:** javac flag expanding terse inference errors into the required/found/reason block with capture variables named.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @3

### javac -Xlint:all
**Short:** javac flag enabling every lint category, surfacing unchecked, deprecation and rawtypes warnings builds usually hide.
**Kind:** api
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/compiler-toolchain-and-codegen @2

### JavaCC
**Short:** Java parser generator producing recursive-descent LL(k) parsers from a grammar with embedded Java actions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### javap -c -v -p
**Short:** JDK disassembler printing a class file's bytecode, constant pool, attributes and private members.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javap -c -verbose
**Short:** Disassembles class files with bytecode and constant pool, revealing invokedynamic, bridge and synthetic methods.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javap -p -v <class>
**Short:** javap invocation that disassembles a class, showing the Signature attribute and synthetic/bridge method flags.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javap -v
**Short:** JDK disassembler flag printing a class file's constant pool, bytecode and attributes such as Module.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javap -verbose
**Short:** JDK disassembler flag that prints the constant pool and bytecode, exposing bridge and synthetic members.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### JavaParser VoidVisitorAdapter
**Short:** JavaParser base visitor that recurses by default, so a codemod overrides only the AST node types it cares about.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2, devtools/static-analysis-and-linting @3

### JavaPoet
**Short:** Java source-generation library with a typed builder API, used inside annotation processors to emit classes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### Javassist
**Short:** Bytecode manipulation library that edits classes from Java source strings; still used by some legacy frameworks.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### javax.lang.model
**Short:** The Java API modelling program elements and types that annotation processors read at compile time.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### javax.lang.model.element.ElementVisitor
**Short:** Visitor interface over Java language elements, the traversal API an annotation processor writes against.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### jcstress
**Short:** OpenJDK harness for Java Memory Model litmus tests; stress-runs concurrent code to expose illegal reorderings.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @2

### jdeprscan
**Short:** JDK CLI that scans class files or jars for uses of deprecated and removed JDK APIs before an upgrade.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/build-and-dependency-management @3

### jdeps
**Short:** JDK tool that statically analyzes class-file dependencies, JDK-internal API use and module requirements.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/build-and-dependency-management @2, runtime-systems/runtime-internals-and-types @3

### Jepsen
**Short:** Distributed-systems correctness harness that injects partitions and clock skew, then checks histories for anomalies.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, data-access/transactions-and-consistency @2

### jextract
**Short:** JDK tool generating Java FFM bindings straight from C header files, replacing hand-written JNI glue.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/io-networking-and-syscalls @3

### JFlex
**Short:** Java lexer generator that turns a regex-based specification into a fast scanner, usually paired with a parser generator.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### JFrog Artifactory
**Short:** Universal binary repository hosting and proxying Maven, npm, Docker and other artifacts, with promotion.
**Kind:** tech
**Lang:** *
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/container-and-image @2, platform-delivery/ci-cd-and-release @2

### JGit
**Short:** Pure-Java Git implementation; Spring Cloud Config Server uses it to clone and pull the config repository.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1

### jlink
**Short:** JDK tool that assembles a minimal custom Java runtime image from the JPMS modules an application actually needs.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2, runtime-systems/runtime-internals-and-types @2, platform-delivery/container-and-image @3

### JMeter
**Short:** Long-established load and performance testing tool with a GUI, broad protocol support and distributed load generation.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, observability/profiling-and-performance @3

### jmod
**Short:** JDK tool and packaging format for modules carrying native libraries or config, consumed as jlink input.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, runtime-systems/runtime-internals-and-types @2, devtools/compiler-toolchain-and-codegen @3

### JPA metamodel
**Short:** Generated Entity_ classes from an annotation processor, giving type-safe attribute references in Criteria queries.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, data-access/orm-and-data-mapping @2

### jQAssistant or Structure101
**Short:** Architecture analysis tools that graph package dependencies, detect cycles and flag drift from the design.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, apis-frameworks/design-patterns-and-principles @3

### jqwik
**Short:** QuickCheck-style property-based testing engine for JUnit 5: generators, shrinking and statistics.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### jshell
**Short:** The JDK's REPL for evaluating Java snippets interactively without a build.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1

### JSR 269: javax.annotation.processing
**Short:** Java's standard annotation processing API, letting a processor read declarations and generate sources at compile time.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### JUnit 5
**Short:** The standard JVM test framework: Jupiter annotations, lifecycle callbacks, parameterized and nested tests.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### JUnit 5 lifecycle
**Short:** JUnit's fixed test skeleton of BeforeAll/BeforeEach/test/AfterEach/AfterAll into which your fixture steps are injected.
**Kind:** concept
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @2

### JUnit 5.10
**Short:** The Jupiter test framework for the JVM: lifecycle annotations, parameterized and nested tests, extensions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### JUnit 6
**Short:** The current Java test framework: @Test, @ParameterizedTest, lifecycle callbacks and the @ExtendWith extension model.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### JUnit Jupiter
**Short:** The JUnit 5 programming model: test lifecycle, assertions, parameterized and nested tests on a Java 17 baseline.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### JUnit Platform
**Short:** JUnit 5's launcher and engine SPI: discovers, filters and runs test engines such as Jupiter and Vintage.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### k6
**Short:** CI-native load and performance testing tool: Go engine, JavaScript test scripts, thresholds as pass/fail gates.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, observability/profiling-and-performance @3

### k6 Cloud
**Short:** Managed service that runs k6 load tests distributed across regions and stores the result timeseries.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### k6-operator
**Short:** Kubernetes operator that splits a k6 load test across N runner pods and aggregates the results.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/kubernetes-and-orchestration @2

### KAPT
**Short:** Kotlin Annotation Processing Tool, the Kotlin bridge to Java annotation processors (now largely superseded by KSP).
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @3

### karate
**Short:** JVM BDD-style API test framework with its own Gherkin DSL for HTTP assertions, mocks and contract checks.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

### KotlinPoet
**Short:** Kotlin source-generation library used by annotation processors and KSP to emit type-safe generated code.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### KSP
**Short:** Kotlin Symbol Processing - the Kotlin-native replacement for annotation processing, generating code from symbols.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### lark
**Short:** Python parsing toolkit with Earley and LALR parsers built from an EBNF-style grammar.
**Kind:** tech
**Lang:** python
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @2

### Lean 4
**Short:** Dependently typed language and proof assistant for machine-checked mathematical and program correctness proofs.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/collections-and-algorithms @3

### Liberica NIK
**Short:** BellSoft's Native Image Kit: a GraalVM-based AOT compiler producing native executables from Java applications.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, platform-delivery/container-and-image @3

### LitmusChaos
**Short:** Kubernetes-native chaos engineering platform: CRD-defined fault experiments with Argo Workflow orchestration.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/kubernetes-and-orchestration @2

### llc
**Short:** LLVM's static compiler driver: runs optimization passes over LLVM IR and emits target assembly or objects.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1

### LLVM opt
**Short:** LLVM's IR tool: run individual optimization passes over .ll/.bc and inspect what each transformation does.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1

### Locust
**Short:** Python load-testing tool where user behaviour is code, scaling over master/worker nodes with a live web UI.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### Lombok
**Short:** Annotation processor that mutates the javac AST to generate getters, builders, constructors and logging fields.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @3

### Lombok @Value
**Short:** Lombok annotation generating an immutable class: final fields, getters, equals, hashCode and an all-args constructor.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### Managed load testing
**Short:** Cloud-hosted distributed load-generation services that run test agents on managed compute such as Fargate.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/cloud-platform-and-cost @2

### Mandrel
**Short:** Red Hat's GraalVM downstream distribution, trimmed to the native-image toolchain Quarkus builds against.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### MapStruct
**Short:** Annotation processor that generates reflection-free entity-to-DTO mappers and reports unmapped fields at build time.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, data-access/orm-and-data-mapping @2, apis-frameworks/design-patterns-and-principles @3

### Maven annotationProcessorPaths
**Short:** maven-compiler-plugin setting that declares annotation processors separately from the compile classpath.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @2

### Maven Central
**Short:** The default public remote repository for JVM artifacts, resolved by Maven and Gradle.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, security/supply-chain-and-runtime-security @3

### Maven Enforcer
**Short:** Maven plugin failing the build on banned dependencies, duplicate classes, or wrong Java/Maven versions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/static-analysis-and-linting @3

### Maven Reproducible Builds
**Short:** Maven config (fixed timestamps, stable ordering) making a rebuild produce byte-identical artifacts.
**Kind:** concept
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, security/supply-chain-and-runtime-security @2

### Maven Wrapper
**Short:** Checked-in script and properties that pin and download the exact Maven version a build requires.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### maven-assembly-plugin
**Short:** Maven plugin that packages an application and its dependencies into a single distributable uber-jar or archive.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### maven-shade-plugin
**Short:** Maven plugin building an uber-jar and relocating package names to avoid dependency conflicts at runtime.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @3

### MethodVisitor
**Short:** ASM visitor over a single method's bytecode, chainable so each stage inspects or rewrites instructions.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### Microcks
**Short:** Mocks and contract-tests APIs from OpenAPI, AsyncAPI or Postman definitions, so consumers can develop early.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @2

### MockIntegration
**Short:** Spring Integration test helper providing mock message channels, handlers and sources for flow assertions.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### Mockito
**Short:** Java mocking framework for stubs, spies and interaction verification; the inline mock maker is default since 5.0.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### Mockito and other test doubles
**Short:** Mocking libraries that generate proxies recording invocations and returning stubbed values to isolate a unit under test.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @3

### Mockito with constructor injection
**Short:** Testing dependencies as constructor-injected mocks; difficulty doing so is the signal that DIP has been violated.
**Kind:** concept
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @2

### mockito-subclass
**Short:** Mockito's subclass mock maker, used instead of the inline maker under GraalVM native image.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### Mockoon
**Short:** Desktop and CLI tool spinning up mock REST APIs from a config, so clients can be built before the server exists.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

### MockRestServiceServer
**Short:** Spring test double that intercepts at the ClientHttpRequestFactory boundary to script RestTemplate responses.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### moditect
**Short:** Maven and Gradle plugin retrofitting a module-info onto a third-party JAR that ships none, for JPMS builds.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, runtime-systems/runtime-internals-and-types @2

### Mojo
**Short:** Modular's language with Python-superset syntax, covering high-level code and hand-tuned CPU/GPU kernels in one language.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, gpu/kernel-programming @2, gpu/gpu-portability-and-precision @3

### Molecule
**Short:** Test framework for Ansible roles: converge a role inside a container, then assert idempotence and final state.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, platform-delivery/infrastructure-as-code-and-config @2

### mvn dependency:tree
**Short:** Maven goal printing the resolved dependency tree, used to find version conflicts and unwanted transitives.
**Kind:** api
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### mypy
**Short:** Reference static type checker for Python; validates annotations, Protocols, generics and variance without running code.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/runtime-internals-and-types @2

### MySQL Workbench
**Short:** MySQL's GUI client for schema design, query editing and visual EXPLAIN plans against a live server.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, observability/profiling-and-performance @2, data-stores/relational @3

### Native Build Tools
**Short:** GraalVM's Maven and Gradle plugins binding native-image to a build profile and running tests in the native binary.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2, devtools/testing-and-mocking @3

### Native Image Build Output report
**Short:** The summary GraalVM native-image prints per build: reachable class/method counts and image-heap breakdown.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, observability/profiling-and-performance @3

### native-image
**Short:** GraalVM ahead-of-time compiler turning classes and jars into a self-contained native executable with fast startup.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, platform-delivery/container-and-image @3

### native-image-agent
**Short:** GraalVM agent recording reflection, proxy, resource and JNI access and writing native-image config files.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### native-image-configure
**Short:** GraalVM tool that merges reflection/resource trace files from the agent into native-image configuration.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

### native-maven-plugin
**Short:** GraalVM Maven plugin that runs native-image to build an ahead-of-time compiled executable.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2

### nativeTest
**Short:** GraalVM build task that compiles your JUnit suite into a native image and runs the tests there, not on the JVM.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/compiler-toolchain-and-codegen @2, devtools/build-and-dependency-management @3

### nm
**Short:** Binutils command listing the symbols in an object file or binary, used to check linkage and visibility.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @3

### Nx
**Short:** Monorepo build system that computes the affected project graph so CI rebuilds and tests only what changed.
**Kind:** tech
**Lang:** js
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @2

### objdump
**Short:** binutils CLI that disassembles binaries and dumps ELF sections and symbols to see what the compiler actually emitted.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, observability/profiling-and-performance @3

### OkHttp MockWebServer
**Short:** Embedded scriptable HTTP server that lets client tests assert on real wire requests and canned responses.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

### omit
**Short:** coverage.py setting excluding files from the report; it shrinks the denominator and inflates the percentage.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### Oracle GraalVM
**Short:** Oracle's GraalVM distribution: a JDK with the Graal JIT and the native-image ahead-of-time compiler.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### os-maven-plugin
**Short:** Maven plugin exposing OS and architecture properties so protoc and other native classifiers resolve correctly.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @2

### Pact
**Short:** Consumer-driven contract testing: the consumer records expectations as a pact file the provider must verify in CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @2

### perf_analyzer
**Short:** Triton's load generator: sweeps concurrency and batch size, reporting throughput, latency percentiles and queue time.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, inference/model-server @2, observability/profiling-and-performance @2

### pgTAP
**Short:** xUnit-style test framework running inside PostgreSQL to assert schema, constraint and data-integrity rules.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, data-access/schema-and-migration @2

### pint
**Short:** Cloudflare's linter for Prometheus rule files: validates config, catches broken queries and flags cardinality risks.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, observability/alerting-and-incident-response @2, observability/metrics-and-monitoring @3

### pip
**Short:** Python's standard package installer, resolving and installing distributions from PyPI or a local wheel or index.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

### pip-tools
**Short:** pip-compile/pip-sync: turns loose requirements into a fully pinned lock file and installs exactly that set.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

### PIT
**Short:** Mutation-testing tool for Java: seeds faults into bytecode and reports which mutants the test suite fails to kill.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @3

### Playwright
**Short:** Microsoft's cross-browser automation library (Chromium/Firefox/WebKit) for E2E tests and web agents.
**Kind:** tech
**Lang:** js, python
**Roles:** devtools/testing-and-mocking @1, llm-apps/agentic-environments @1

### plotly
**Short:** Interactive charting library for notebooks and Dash dashboards; common for 2-D/3-D cluster and result visualization.
**Kind:** tech
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1, observability/alerting-and-incident-response @3

### PMD
**Short:** Java-centric static analyzer with tunable rulesets for long methods, excessive coupling, god classes and dead code.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, apis-frameworks/design-patterns-and-principles @3

### poetry
**Short:** All-in-one Python project tool: dependency resolution with a lockfile, virtualenv management, build and publish.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

### POSIX sh
**Short:** The POSIX-standard shell language; the interpreter available in minimal container images where bash is absent.
**Kind:** spec
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

### Postman
**Short:** GUI client for exercising REST and gRPC APIs, with saved collections, environments and scripted assertions.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

### pre-commit
**Short:** Git hook manager that runs language-agnostic lint, format and secret-scan checks before a commit lands.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, devtools/version-control-and-workbench @2, platform-delivery/ci-cd-and-release @3

### Profile-Guided Optimization
**Short:** Compiling with a recorded execution profile so the compiler optimizes the branches and paths that actually run hot.
**Kind:** concept
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, observability/profiling-and-performance @2

### protobuf-maven-plugin
**Short:** Maven plugin that runs protoc to generate Java message and gRPC stub classes from .proto files.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2, apis-frameworks/rpc-graphql-and-streaming @2

### protoc
**Short:** Protocol Buffers compiler: turns .proto contracts into generated message and gRPC stub code for many languages.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/data-formats-and-api-contracts @2, apis-frameworks/rpc-graphql-and-streaming @2

### protoc-gen-grpc-java
**Short:** protoc plugin that generates Java gRPC service stubs and clients from .proto definitions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/rpc-graphql-and-streaming @2

### ptxas
**Short:** NVIDIA's PTX-to-SASS assembler invoked by nvcc; -v reports per-kernel registers, spills and shared memory.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1, gpu/kernel-programming @2, gpu/gpu-profiling-and-debugging @3

### py_compile
**Short:** Python stdlib module that compiles source to .pyc bytecode ahead of time and populates the __pycache__ directory.
**Kind:** api
**Lang:** python
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### pydantic mypy plugin
**Short:** mypy plugin teaching the checker about Pydantic models so generated __init__ signatures and fields type-check.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1

### pylance
**Short:** VS Code's Python language server and type checker (Pyright-based), often stricter than mypy on edge cases.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/runtime-internals-and-types @2

### pyright
**Short:** Microsoft's fast static type checker for Python; stricter than mypy on generics and narrowing.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/runtime-internals-and-types @2

### pytest
**Short:** Python test runner and fixture engine; the standard harness for unit, integration and parametrized golden-dataset tests.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### pytest fixtures
**Short:** pytest's dependency-injected setup/teardown mechanism; yield fixtures tear down even when the test fails.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### pytest-anyio
**Short:** AnyIO's pytest plugin for running async tests against either asyncio or trio backends.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @3

### pytest-asyncio
**Short:** pytest plugin that runs async test functions and fixtures on an event loop.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### pytest-cov
**Short:** pytest plugin that measures code coverage via coverage.py and reports missing lines or enforces a threshold.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @3

### pytest-mock
**Short:** Pytest plugin exposing unittest.mock through a mocker fixture that undoes every patch at test teardown.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### pytest-xdist
**Short:** pytest plugin distributing tests across CPU cores or remote hosts with -n auto.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### QUIC Tracker
**Short:** Interoperability suite probing a QUIC implementation against the spec and publishing a results matrix.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, runtime-systems/io-networking-and-syscalls @2

### Reachability Metadata Repository
**Short:** Shared GraalVM repository of reflection and resource metadata for popular libraries, so native builds work unmodified.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### react-diff-viewer
**Short:** React component rendering side-by-side or inline text diffs, used to show an agent's proposed edits.
**Kind:** tech
**Lang:** js
**Roles:** devtools/version-control-and-workbench @1, llm-apps/agentic-environments @3

### Reactive Streams TCK
**Short:** Conformance test kit proving a custom Publisher/Subscriber obeys the Reactive Streams backpressure specification.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @2, apis-frameworks/data-formats-and-api-contracts @3

### Reactor Test
**Short:** Project Reactor's StepVerifier and virtual time scheduler for deterministic assertions on reactive streams.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @2

### reactor-test
**Short:** Project Reactor's test module: StepVerifier asserts a Flux/Mono's exact signal sequence, with virtual time support.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @2

### readelf
**Short:** binutils CLI that dumps ELF headers, sections, symbols and dynamic linking info from a binary.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, observability/profiling-and-performance @3

### Recaf
**Short:** GUI decompiler and bytecode editor for inspecting and patching compiled JVM classes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/version-control-and-workbench @3

### redos-detector
**Short:** Analyzes a regex for catastrophic backtracking, returning either a safety proof or a concrete attack string.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/text-encoding-and-regex @2, security/supply-chain-and-runtime-security @3

### Renovate
**Short:** Bot that opens automated pull requests to bump dependency, action and plugin versions across a repo.
**Kind:** tech
**Lang:** *
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @2, security/supply-chain-and-runtime-security @3

### respx
**Short:** Mock layer for httpx that intercepts requests at the transport level instead of monkey-patching.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### RestAssured
**Short:** Java DSL for black-box REST API testing: given/when/then request building with JSON path assertions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @3

### Revapi
**Short:** API compatibility checker that fails the build on binary or source breaks, including generic descriptor changes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/build-and-dependency-management @2

### ruff
**Short:** Rust-written Python linter and formatter, 10-100x faster than flake8; one tool for lint rules and formatting.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1

### ruff linter
**Short:** Rust-based Python linter and formatter that reimplements flake8, isort and pyupgrade rules at very high speed.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1

### Run generators on
**Short:** Table-row fragment, not a product: where load-test generator processes are hosted, such as ECS/Fargate or GKE.
**Kind:** concept
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### RuntimeHintsRegistrar
**Short:** Spring AOT interface for declaring reflection, resource and proxy hints needed by a GraalVM native image.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

### Scenario
**Short:** Spring Modulith test API that publishes an event and waits for the expected downstream outcome across modules.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### Selenium
**Short:** W3C WebDriver browser automation with the widest language and browser coverage; drives real browsers for tests or bots.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, llm-apps/agentic-environments @2

### Semgrep
**Short:** Pattern-based multi-language static analysis; rules look like the code they match, so custom SAST rules are cheap.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2

### shellcheck
**Short:** Static linter for shell scripts catching quoting, word-splitting and error-handling bugs before they reach CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1

### shfmt
**Short:** Formatter for shell scripts, enforcing consistent indentation and style across bash/POSIX sh in CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1

### SonarCloud
**Short:** Hosted SAST and code-quality gate on pull requests, detecting SQL injection, XSS and insecure patterns.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2, platform-delivery/ci-cd-and-release @3

### SonarLint
**Short:** IDE static-analysis plugin flagging cognitive complexity, duplication, god classes and SOLID smells as you type.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1

### SonarQube
**Short:** Static-analysis server that gates a build on code smells, complexity, duplication and SAST security findings.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2

### SonarQube duplicate code detection
**Short:** SonarQube's copy-paste detector reporting duplicated blocks and density, the mechanical proxy for DRY violations.
**Kind:** api
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, apis-frameworks/design-patterns-and-principles @3

### SonarQube rule S5852
**Short:** SonarQube rule flagging super-linear regular expressions, the CI gate against catastrophic backtracking (ReDoS).
**Kind:** api
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/text-encoding-and-regex @2, security/supply-chain-and-runtime-security @3

### Sonatype Nexus
**Short:** Universal artifact repository: proxies and hosts Maven/npm/Docker/PyPI artifacts with promotion and replication.
**Kind:** tech
**Lang:** *
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/container-and-image @3, security/supply-chain-and-runtime-security @3

### SpotBugs
**Short:** Bytecode-level static analyser for Java bug patterns: equals/hashCode gaps, null derefs, synchronisation errors.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1

### Spring Boot @MockitoBean
**Short:** Spring Boot annotation replacing a bean with a Mockito mock in a slice test, without static-state surgery.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/dependency-injection-and-config @2

### Spring Boot DevTools
**Short:** Development-only Spring Boot module restarting the app on classpath changes and disabling caches for a fast inner loop.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, devtools/build-and-dependency-management @3

### Spring Initializr
**Short:** Project generator at start.spring.io producing a build file and skeleton with the right Spring Boot starters.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, apis-frameworks/dependency-injection-and-config @3

### Spring MockMvc
**Short:** Exercises Spring MVC controllers, filters and validation through the full dispatch chain without binding a port.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @3

### spring-batch-test
**Short:** Spring Batch test support: JobLauncherTestUtils to run jobs/steps and listeners that supply step scope in tests.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/aop-middleware-and-scheduling @2

### spring-boot-autoconfigure-processor
**Short:** Annotation processor generating auto-configuration metadata so the IDE can autocomplete Boot properties.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/dependency-injection-and-config @2

### spring-boot-maven-plugin
**Short:** Maven plugin that repackages an executable fat jar, runs the app, builds OCI images and drives Spring AOT processing.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/container-and-image @3, devtools/compiler-toolchain-and-codegen @3

### spring-boot-test
**Short:** Spring Boot's core test support: @SpringBootTest, test slices, TestRestTemplate and test auto-config.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### spring-integration-test
**Short:** Test support for Spring Integration: mock message sources, intercept channels, assert on messages in a flow.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

### spring-modulith-starter-test
**Short:** Spring Modulith test starter: verify module boundaries and run bootstrap tests slicing one application module.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @3

### spring-security-test
**Short:** Spring test support for security: @WithMockUser, @WithUserDetails and MockMvc CSRF/auth post-processors.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, security/authentication-and-identity @2

### spring-test
**Short:** Spring's test module: MockMvc, cached test contexts, @Sql, @DirtiesContext and @MockitoBean bean overrides.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/dependency-injection-and-config @3

### starlette.testclient.TestClient
**Short:** Synchronous in-process test client that drives an ASGI app end to end without starting a server.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

### Static checking
**Short:** Verifying types and errors before execution with tools like mypy or pyright, rather than discovering them at runtime.
**Kind:** concept
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/runtime-internals-and-types @2

### stress-ng
**Short:** Linux load generator stressing CPU, memory, I/O and other subsystems; used for chaos and capacity experiments.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, runtime-systems/memory-processes-and-os @2

### Surefire plugin
**Short:** The Maven plugin that discovers and runs unit tests during the test phase and writes the reports the build gate reads.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/build-and-dependency-management @2

### tc netem
**Short:** Linux traffic-control queue discipline that injects latency, jitter, loss, corruption and reordering for chaos tests.
**Kind:** api
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, runtime-systems/io-networking-and-syscalls @2

### Terratest
**Short:** Go test library that really deploys Terraform or Kubernetes resources, asserts against them, then tears them down.
**Kind:** tech
**Lang:** go
**Roles:** devtools/testing-and-mocking @1, platform-delivery/infrastructure-as-code-and-config @2

### Testable without framework
**Short:** Being unit-testable with plain constructor wiring, without needing the framework's context to run a test.
**Kind:** concept
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### Testcontainers
**Short:** Starts real dependencies (PostgreSQL, Kafka, Redis) in throwaway Docker containers for integration tests.
**Kind:** tech
**Lang:** java, python, go
**Roles:** devtools/testing-and-mocking @1, platform-delivery/container-and-image @3

### Testcontainers 1.20.x
**Short:** Library that starts real dependencies (Postgres, Kafka, Redis) in throwaway containers for integration tests.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, platform-delivery/container-and-image @2

### Testcontainers Kafka
**Short:** Testcontainers module that boots a real Kafka broker in Docker for integration tests instead of a mock.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, data-movement/event-streaming-and-processing @3, platform-delivery/container-and-image @3

### TestModel
**Short:** A stub LLM returning canned responses, so agent unit tests run deterministically without calling a provider.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, llm-apps/agent-framework @2

### tflint
**Short:** Terraform linter catching provider-specific mistakes, invalid instance types and deprecated syntax before plan.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, platform-delivery/infrastructure-as-code-and-config @2

### tfsec
**Short:** Static security scanner for Terraform HCL that flags misconfigured cloud resources before apply.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2, platform-delivery/infrastructure-as-code-and-config @2

### TLA+, Coq
**Short:** Formal methods tools: TLA+ model-checks a concurrent specification, Coq proves theorems interactively.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/collections-and-algorithms @3

### tools.jackson:jackson-bom
**Short:** Jackson's bill of materials: import it as a platform so every Jackson module resolves to one aligned version.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, apis-frameworks/data-formats-and-api-contracts @2

### tree-sitter
**Short:** Incremental parser with grammars for many languages; gives an AST for editor tooling and code-aware chunking.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, search-retrieval/rag-and-document-processing @2, devtools/static-analysis-and-linting @3

### trunk-based development
**Short:** Branching model where everyone merges small changes to one trunk daily, keeping releases continuous and cheap.
**Kind:** concept
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, platform-delivery/ci-cd-and-release @2

### Turborepo
**Short:** JavaScript monorepo build orchestrator: task graph, content-hash remote caching and affected-target builds.
**Kind:** tech
**Lang:** js
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @2

### twine
**Short:** CLI that uploads built Python distributions to PyPI or a private index.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

### TypeVisitor
**Short:** javax.lang.model visitor over Java types during annotation processing, with versioned abstract base classes.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### uv
**Short:** Rust-based Python installer, resolver and project manager; 10-100x faster than pip, with locked syncs.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

### valkey-benchmark
**Short:** Valkey's load-generation CLI, command-compatible with redis-benchmark, for measuring throughput and latency.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, caching/distributed-cache @2, observability/profiling-and-performance @3

### vcrpy
**Short:** Records real HTTP interactions to cassette files and replays them, making API-dependent tests deterministic.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

### Vegeta
**Short:** Go CLI and library for constant-rate HTTP load testing, reporting latency percentiles from a target list.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

### Velocity/StringTemplate
**Short:** Java template engines used to emit generated source files from annotation processors and codegen tools.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @3

### versions-maven-plugin
**Short:** Maven plugin that reports and bulk-updates dependency and plugin versions across a multi-module project.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

### Visual Studio Code
**Short:** Microsoft's editor; also a first-class MCP client and coding-agent host through GitHub Copilot chat.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, llm-apps/agentic-environments @2, llm-apps/tool-use-and-mcp @3

### WebMvcTest
**Short:** Spring test slice booting only the MVC layer with MockMvc, leaving services and repositories to be mocked.
**Kind:** api
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

### WebSocket King
**Short:** Browser client for hand-testing WebSocket endpoints: connect, send frames and watch the traffic.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/rpc-graphql-and-streaming @2

### WebTestClient
**Short:** Spring's non-blocking test client for exercising WebFlux (or MVC) endpoints end to end with fluent response assertions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

### WireMock
**Short:** HTTP mock server that stubs external REST APIs over real HTTP, with scenarios and contract-style verification.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

### xargs
**Short:** Shell tool building command lines from stdin, with -P to run the resulting commands in parallel.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, runtime-systems/memory-processes-and-os @3

### Xcode
**Short:** Apple's toolchain and IDE, including the Metal compiler and MPSGraph for GPU compute on Apple silicon.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/version-control-and-workbench @2, gpu/gpu-portability-and-precision @2

### yacc
**Short:** Classic parser generator that turns an LALR grammar specification into parser source code.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1
