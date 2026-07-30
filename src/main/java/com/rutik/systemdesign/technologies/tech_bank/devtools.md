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

You write a `.g4` grammar naming lexer tokens and parser rules, and ANTLR generates the tokenizer, a recursive-descent parser, and listener/visitor base classes for walking the resulting parse tree in Java, Python, Go, C# or JavaScript. Its adaptive LL(*) strategy resolves alternatives with runtime lookahead, so it accepts directly left-recursive expression rules that classic LL parsers force you to rewrite by hand.

Reach for it when the language is yours — a DSL, a query or filter syntax, an expression evaluator, a tool that has to read legacy source. For a format that already has a mature parser, use that instead; a generated grammar is a maintenance commitment worth making only when nobody else has written one.

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

Rules read as fluent predicates over the compiled classes on the test classpath - no classes in the domain package may depend on the infrastructure package, no package cycles, no controller may reference a repository type directly. Because a rule is an ordinary test, a violation fails the build exactly like a broken assertion, which is what turns an architecture diagram nobody enforces into something that cannot be merged past.

It reasons about bytecode, so it sees types, packages and method calls but not string-based or reflective wiring; rules must be expressed structurally. For a codebase that already violates a rule in many places, freeze the current set of violations and fail only on new ones, so the rule can go in today rather than after a cleanup that never happens.

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

AssertJ gives you one entry point, `assertThat(actual)`, that returns an assertion object typed to whatever you passed, so completion offers only the assertions that make sense — collection assertions for a list, temporal ones for an `Instant` — and they chain into a single readable line. The practical payoff is the failure message: instead of "expected true but was false" you get the actual value, the expected value and the diff, which is often enough to diagnose without rerunning under a debugger.

Beyond simple values it covers exceptions with `assertThatThrownBy`, soft assertions that collect several failures in one run instead of stopping at the first, and recursive field-by-field comparison for whole object graphs. It is the default assertion library in Spring Boot's test starter.
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

A build is a graph of fine-grained targets whose inputs and outputs are declared explicitly, so every action is hermetic and content-addressed. That buys two things a monorepo needs: results cache locally and in a shared remote cache so an unchanged target is never rebuilt, and querying the reverse dependency graph tells CI precisely which targets a commit can affect - so CI time scales with the size of the change rather than the size of the repository.

The price is that everything must be declared. Undeclared dependencies a Makefile tolerated become hard errors, and non-JVM ecosystems need rulesets and pinned lockfiles that somebody has to maintain. Reach for it for a large polyglot repository where build times or flaky incremental builds are actually hurting; a single-language project is nearly always better served by its native tool.

### bison
**Short:** GNU parser generator that turns a grammar file into an LALR bottom-up parser, usually paired with flex for lexing.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @3

You write a grammar of tokens and production rules with semantic actions attached, and it generates a table-driven LALR(1) parser that reduces bottom-up as input arrives; the lexer normally comes from flex. It targets C, C++ or Java, and offers a GLR mode for grammars that are genuinely ambiguous at any fixed lookahead.

Reading its diagnostics is the actual skill. A shift/reduce or reduce/reduce conflict means the grammar is ambiguous at one token of lookahead, and the fix is precedence declarations or restructuring the rules - never suppressing the warning, because the generator resolves it silently and the parser then accepts the wrong tree. Most modern production compilers hand-write recursive-descent parsers instead, for better error messages and recovery, but a generator is still the fastest route to a correct parser for a grammar specification you control.

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

Byte Buddy generates bytecode at runtime through a fluent, type-checked API — `new ByteBuddy().subclass(Service.class).method(named("charge")).intercept(...)` — so you express the transformation you want instead of writing ASM visitor callbacks and hand-computing stack frames. It can define the class into a live classloader, emit it at build time, or apply it through a Java agent that transforms classes as they are loaded.

This is the machinery under a lot of the JVM ecosystem: Mockito creates mock subclasses with it, and most APM and tracing agents use it to weave instrumentation into methods they do not own. Reach for it when you must proxy a concrete class — JDK dynamic proxies only implement interfaces — or instrument third-party code. For ordinary application logic, generated types are hard to debug and hard for the next reader to find, and plain composition wins.

### ByteBuddy
**Short:** Runtime bytecode generation library used to build dynamic proxies and subclasses; powers Mockito and Hibernate.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, devtools/testing-and-mocking @3

A fluent API describes the class you want - subclass this type, intercept methods matching this matcher, delegate them to this interceptor - and it emits the bytecode, so you get code generation without writing ASM visitors by hand. The same API drives Java agents: `AgentBuilder` can retransform classes that are already loaded, which is how APM and tracing agents instrument an application they never compiled against.

Reach for it when you need a proxy over a concrete class, since JDK dynamic proxies only cover interfaces. The subclassing approach carries its own limits, and they are the ones people are surprised by: final and private methods cannot be intercepted, and a call from one method of the target to another goes to the real object, bypassing the proxy entirely - the same self-invocation trap as Spring AOP.

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

It generates a subclass of your class at runtime and overrides the non-final methods to insert interception, which is the mechanism behind Spring AOP on a class with no interface. Two consequences follow directly: a final class or final method cannot be advised at all, and a self-invocation through `this` never leaves the object so it never crosses the proxy, which is the usual reason an inner `@Transactional` call quietly does nothing.

You almost never call it yourself; Spring repackages it inside `spring-core` and picks it over JDK dynamic proxies when there is no interface to proxy. Understanding it matters because it explains proxy behaviour you will otherwise treat as a Spring bug.

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

An experiment is a declarative document with a steady-state hypothesis, a method of actions and probes that injects the fault, and rollbacks that undo it. The probes are checked before and after, so a run either confirms the system tolerated the fault or fails with a deviation -- which is what makes it runnable as a job in CI rather than a manual game day. Drivers exist for Kubernetes, the major clouds, Prometheus and Gremlin.

Reach for it when you want chaos experiments reviewed and versioned like tests. It is only as useful as your observability: without a metric that defines steady state, the hypothesis is a guess.

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

It parses Java source into an AST and applies a configured rule set, so everything it can tell you is syntactic: it will fail a build over a 400-line class, a nine-parameter method, four levels of nesting or a bare magic number, and it has no opinion at all on whether the code is correct. That narrowness is the strength, because it is fast, deterministic and the cheapest first gate to put in CI, and those metrics are decent proxies for the design smells that SRP and KISS arguments circle around. Pair it with a semantic analyzer for real bug and security findings, and adopt it incrementally on a large codebase: a full rule set switched on at once produces thousands of violations that everyone promptly learns to ignore.

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

It hooks into javac as a plugin and inspects the compiler's own AST, so its checks run on every build and a violation is a compile error rather than a report someone reads later. The catalogue is bug patterns rather than style: reference equality on boxed types, a missing `@Override`, a format string that does not match its arguments, an `equals` inconsistent with `hashCode`, a mutable field escaping through a getter. Many checks carry a suggested fix the build can apply for you.

Adopt it on an existing codebase by demoting the noisy checks to warnings first, since enabling the full set at error severity usually stops the build on day one.

### factory_boy
**Short:** Python test-object factory library that builds model/ORM instances for fixtures instead of hand-written setup.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

A factory is a class describing how to build one model instance: fields get defaults or sequences, `SubFactory` builds the related object a foreign key needs, and `post_generation` hooks fill many-to-many links -- so a test asks for `UserFactory(is_admin=True)` and receives a valid object with every other field filled plausibly. That is the point: a test should state only the attribute it is about, whereas hand-written fixtures restate every required column and therefore all break together the next time the model changes. It has ORM-specific bases for Django, SQLAlchemy, MongoEngine and plain objects, and pairs with Faker for realistic values. Reach for it once test setup turns repetitive; keep factories minimal and let each test override what it cares about, and remember `build()` never touches the database while `create()` does.

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

`@freeze_time("2024-01-01")` on a test, or `with freeze_time(...) as clock: clock.tick(60)`, patches `datetime.now`, `date.today` and `time.time` for the duration, including in modules that already imported them. That makes tests of token expiry, cache TTLs, retention windows and "created 30 days ago" logic deterministic without threading a clock object through the code under test.

Know its edges. It patches this process only, so a timestamp generated by the database server or a subprocess is unaffected, and code that captured a time at import can escape it. Where you own the design, injecting a clock is still cleaner and faster; freezegun is what you reach for when the code already calls `datetime.now()` in twenty places.

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

A Gatling test is code — a Scala, Java or Kotlin class describing scenarios (a sequence of requests, with feeders supplying data and checks asserting responses) and an injection profile such as ramping to N concurrent users over two minutes. It runs on non-blocking I/O rather than a thread per virtual user, so a single machine sustains far more concurrency than a thread-bound tool, and the output is an HTML report with percentile latency plotted against time and load.

That time axis is what you actually read: it shows the point where the response-time curve bends and where errors begin, which is the saturation point you were looking for. Because the simulation lives in the repository next to the service, it can be reviewed, refactored, and run as a performance gate in CI instead of being a manual exercise before each release.

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

Every clone holds the entire object database and history, so commit, branch, diff, blame and log are local operations and the network appears only at fetch, pull and push. That model is why branching is cheap enough to be the default unit of work, and why history can be rewritten freely before it is shared and only with care afterwards. Beyond code it is the honest baseline for versioning anything textual, including prompts, Terraform, Kubernetes manifests and config, which get review, blame and rollback for free; GitOps is built on exactly that observation. Large binaries are its weak spot, which is what LFS exists to patch.

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

It performs closed-world static analysis over your application and its dependencies, compiles everything reachable into a single native executable with a small embedded runtime, and snapshots the build-time-initialized heap into the binary. Startup drops from seconds to milliseconds and memory footprint falls sharply, because there is no class loading, no bytecode verification, and no JIT warmup at run time.

The closed-world assumption is the whole cost: reflection, dynamic proxies, JNI, and resources must be declared in configuration or the class is simply not in the binary. Frameworks solve this with build-time AOT processing — Spring Boot generates most of the hints for you — but an unprepared library will still fail at run time rather than at build time. Reach for it for CLIs and scale-to-zero or serverless services; a long-lived throughput-bound server usually does better on the JVM, where C2 eventually out-optimizes the AOT compiler.

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

Instead of asserting on the examples you happened to think of, you state a property that should hold for all inputs and let Hypothesis generate them from strategies — integers, text, composites you build up, or a strategy derived from a schema. When it finds a counterexample it shrinks it to the smallest input that still fails, so you get an empty string or a zero rather than the 400-character blob that first broke, and it saves the failing case so the same input is retried on every later run.

The skill is choosing properties that are not just a reimplementation of the code under test. Round-trip identities, invariants that must always hold, and comparison against a slow obviously-correct reference are the three that consistently pay.

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

An interface or abstract class annotated `@Value.Immutable` triggers an annotation processor at compile time, which generates a final class with private fields, a builder, `equals`/`hashCode`/`toString`, and defensive copies of collections. Mandatory attributes are checked in `build()`, which throws listing every field still unset, so forgetting to set an amount fails at construction rather than as a null three layers away.

Reach for it for value types where construction correctness matters and there are enough optional fields that telescoping constructors have stopped scaling. Records cover the simple cases in modern Java with no dependency; Immutables earns its keep when you want the generated builder, defaults, derived attributes and staged builders that force required fields to be supplied in order.

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

The IDE builds a resolved index of the project, so navigation, find-usages, and refactorings like rename, extract method and change signature operate on the type graph rather than on text — which is why they are safe across a large codebase in a way that search-and-replace is not. Inspections flag likely bugs as you type, and generation covers the boilerplate that is easy to get subtly wrong, notably `equals`/`hashCode`/`toString` from selected fields.

Its debugger is the part worth learning deliberately: conditional and field-watch breakpoints, expression evaluation in a paused frame, and the stream trace view, which shows the elements entering and leaving each stage of a Stream pipeline — the fastest way to find which `filter` or `flatMap` dropped what you expected. Community Edition covers Java and Kotlin; Ultimate adds the Spring, JPA, HTTP client and database tooling.

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

JaCoCo attaches as a JVM agent and instruments bytecode as classes load, recording which lines and branches actually executed; the Maven or Gradle plugin merges that execution data into HTML and XML reports and can fail the build below a threshold. It answers one narrow question well: which code did the test suite never touch at all.

Read it as a hint about where invariants are untested — an anemic domain model or a god object usually shows up as a large uncovered region — and never as a target. Line coverage is trivially gamed by tests with no assertions, and even branch coverage says nothing about whether the combination of states that actually breaks was exercised.
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

A test is a tiny class with two or more actor methods that touch shared state plus an arbiter that records what was observed; the harness runs the pair millions of times across real threads with fuzzed timing and interleavings, then reports which outcomes actually occurred against the ones you declared acceptable or forbidden.

This is effectively the only practical way to catch a memory-model bug. A missing `volatile`, a broken double-checked lock or an unsafe publication is correct on x86's strong ordering and fails on ARM or under a different JIT decision, and an ordinary unit test will never observe it - the reordering needs specific timing that only brute force finds. Reach for it when you write lock-free code, a custom synchronizer, or anything whose correctness rests on happens-before reasoning, and read the result honestly: zero forbidden outcomes is evidence, not a proof.

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

It reads class files or a jar and reports the package and class dependencies it finds, flags uses of internal JDK APIs such as `sun.misc.Unsafe` with `-jdkinternals`, and can emit a `module-info` skeleton for a jar you are modularising. It ships in the JDK, so there is nothing to install.

Reach for it before a module-path migration, a JDK upgrade, or a GraalVM native-image attempt, to see what a library actually depends on. Its blind spot is that the analysis is static: reflection, service loading and dynamic proxies are invisible to it, so a clean report is evidence and not proof.

### Jepsen
**Short:** Distributed-systems correctness harness that injects partitions and clock skew, then checks histories for anomalies.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, data-access/transactions-and-consistency @2

A Jepsen test drives a real cluster with concurrent client operations while a nemesis process injects faults — network partitions, clock skew, process pauses and kills — recording a history of every operation's invocation and completion, including the ones whose outcome is genuinely unknown. A checker then searches that history for anomalies the claimed isolation or consistency level forbids: lost updates, stale reads, cycles in the dependency graph that prove the run was not serializable.

It matters to engineers who will never write a test with it, because its published reports are the reason many databases' real guarantees are documented at all. Read the report for a datastore before believing its marketing on consistency.

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

It hosts every artifact type behind one server: local repositories for your own builds, remote repositories that proxy and cache Maven Central, npm, PyPI, or Docker Hub, and virtual repositories that expose both under a single URL your build tools point at. The cache alone earns it — builds stop breaking when an upstream registry has an outage or yanks a version.

The other half is promotion: a binary is built once and moved between repositories as it passes stages, rather than rebuilt per environment, with build-info metadata linking each artifact back to the source revision and the dependencies that went into it. Reach for it when many teams and languages need one governed artifact store with access control and retention; a single-language team is usually fine with GitHub Packages or the language's native registry.

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

Given a set of root modules it resolves their transitive `requires` graph and links just those modules into a self-contained runtime image with its own `bin/java`, so a service ships without the full JDK and without needing one installed on the target host. The usual result is tens of megabytes instead of a few hundred, which matters most in container images and cold-start-sensitive deployments. The blocker on a classpath-era codebase is that everything must be resolvable as real modules, so `jdeps` comes first to find what your dependencies actually need, then `jlink`, then `jpackage` on top if you want a platform installer.

### JMeter
**Short:** Long-established load and performance testing tool with a GUI, broad protocol support and distributed load generation.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, observability/profiling-and-performance @3

A test plan is a tree of thread groups, samplers, timers, assertions and listeners, built in the GUI and then run headless for the actual load, with a controller coordinating several load generators when one box is not enough. Its breadth is the reason it survives: alongside HTTP it drives JDBC, JMS, FTP, LDAP and more, so it can load-test the parts of a system that a browser-shaped tool cannot reach.

Reach for it when you need those protocols or you have inherited a plan. For HTTP-only work, code-first tools such as k6 or Gatling give you tests that live in version control and read like programs. Two practical traps: never run the GUI for a real test, since it distorts the results, and remember each virtual user is an OS thread, so one JMeter host saturates far earlier than an event-loop-based generator.

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

Both tools read your compiled code and build metadata and turn it into a dependency graph you can interrogate, rather than reading imports by hand. jQAssistant scans the artifact into a graph database and lets you write rules as queries — no package under `domain` may reference `infrastructure`, no cycles between modules — and fail the build when a rule matches. Structure101 is a commercial visual tool for the same material: you see the tangles and cycles, model the architecture you intended, and measure drift from it.

Use them at architecture review cadence rather than per commit. The findings are structural and slow-moving, and the value is in noticing that a layering rule everyone believes in stopped being true three releases ago.
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

Typing `jshell` opens a REPL that evaluates Java snippets — expressions, statements, declarations — with no class, no `main` and no build; it prints the value and inferred type of each expression, keeps history, and lets you redefine a method and carry on. `/env --class-path` puts a jar on the path so you can poke at a real library, and `/save` and `/open` move a session to and from a file.

It is the fastest way to settle a small question with certainty instead of a guess: what a `Cipher` transformation string actually accepts, how a regex behaves on an edge case, whether `LocalDate` rounds the way you assumed. It is not a testbed for anything concurrent or long-running — that still belongs in a real project.

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

Tests are JavaScript modules -- a default exported function each virtual user runs in a loop, plus an `options` object declaring stages, ramps and thresholds -- executed by a Go runtime, so VUs are goroutines rather than OS threads and one machine drives far more concurrency than a thread-per-user tool. Thresholds are what make it CI-native: a rule such as a p95 request duration under 300 ms fails the run with a non-zero exit code, so a performance regression breaks the build instead of sitting in a report nobody opens. Its executors include constant and ramping arrival rate, not just VU counts, which is the correct shape when you care about requests per second rather than concurrency, and results stream out to Prometheus and other backends. Reach for it when load tests should live in the pipeline beside the code; note the script runtime is not Node, so most npm libraries do not work, and browser-level testing needs its separate browser module.

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

Its Gherkin steps are HTTP verbs and JSON assertions rather than sentences you have to bind to code, so there are no step definitions to write. Payload matching is its strongest feature: you compare against an expected JSON document with wildcards and type markers for the fields you cannot predict, which is far shorter than asserting field by field. It also stands up mock servers from the same syntax and runs scenarios in parallel.

Reach for it on the JVM when API tests should be readable and maintainable by people who are not writing the service, or when contract-style checks live beside the build. The tradeoff is a bespoke DSL: complex logic ends up awkward, and you are relying on the framework's own expression language rather than plain Java.

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

Lean 4 is a dependently typed functional language that doubles as an interactive proof assistant. Because types can express propositions, a term of that type is a proof of it, and the ordinary type checker is what verifies the argument — nothing is accepted unless every step checks mechanically. Mathlib supplies a large body of already-formalized mathematics to build on, and tactics automate the routine steps.

Reach for it where being confident is not enough and correctness must be established: an induction proof over an algorithm, a protocol invariant, a safety property. It is also the checker in automated theorem-proving loops, where a model proposes proofs and Lean decides which ones are real. The cost is time — a proof takes far longer to write than a test that probably would have caught the same bug.
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

You declare a `@Mapper` interface with method signatures such as `TargetDto toDto(SourceEntity e)`, and the annotation processor writes the implementation at compile time -- plain field assignments, no reflection -- so the mapping costs what hand-written code costs and appears in stack traces and debuggers as ordinary Java. Matching names and types map automatically; `@Mapping` covers renames, nested paths, expressions and formatting, and unmapped target properties can be escalated from a warning to a build error. That escalation is the real value: adding a field to a DTO and forgetting to populate it fails the build instead of shipping a null. Reach for it wherever entity-to-DTO conversion is repetitive and wide; for two small objects a hand-written mapper is less machinery, and any mapping with genuine branching logic should stay hand-written anyway.

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

Mockito builds test doubles at runtime: `mock()` returns an object whose methods return type defaults until you stub one with `when(...).thenReturn(...)`, and `verify()` asserts that a collaborator was called the way you expected, with the arguments you expected. The point is isolating the class under test from collaborators that are slow, remote or nondeterministic, so a failure names one unit.

Since 5.0 the inline mock maker is the default, so final classes and final methods mock without an extra dependency. Two habits keep it healthy: prefer stubbing types you own — wrap a third-party client in your own interface rather than mocking its API surface — and treat heavy use of `mockStatic` as a signal that a static dependency should have been injected.
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

mypy reads your annotations and checks the program without running it: argument and return types at every call site, `Optional` handling, generics and variance, and structural conformance to `Protocol` classes. Most of what it catches is the None that was never handled and the refactor that missed a caller, which is exactly the class of bug that unit tests reach last.

It is gradual by design — unannotated functions are skipped, so a large codebase can adopt it module by module — and that also means the default configuration proves very little. `--strict` is where it starts being load-bearing, since it disallows untyped definitions and implicit `Any`. Plugins cover frameworks whose types only exist at runtime, such as the pydantic plugin that teaches it what a model's generated `__init__` looks like.
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

It derives a project graph from imports and configuration, and combines it with a git diff to work out which projects a change can actually affect, so CI runs the tests and builds for those projects only. On top of that it hashes each task's inputs and caches its outputs, locally and optionally on a shared remote cache, so an unchanged target is replayed from cache instead of executed, including across machines and CI runs.

Reach for it when a monorepo's pipeline has grown to rebuilding everything on every commit and the feedback loop is the bottleneck. It is strongest in the JavaScript and TypeScript ecosystem it grew from, with plugins extending it to other toolchains; a repo built around Bazel or Gradle already has its own answer to the same problem.

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

It drives browsers over their own debug protocols rather than the WebDriver wire protocol, which is what lets it auto-wait for an element to be actionable, intercept and stub network requests, capture traces and video, and run isolated browser contexts inside one process. The auto-waiting is the practical difference day to day, because it removes most of the arbitrary sleeps that make an end-to-end suite flaky. It is the default choice for a new browser test suite, and the same API underpins LLM browser agents, which run it headless and read the accessibility tree instead of pixels. Bindings for Python, Java and .NET track the JavaScript API closely, though the JavaScript ecosystem sees new features first.

### plotly
**Short:** Interactive charting library for notebooks and Dash dashboards; common for 2-D/3-D cluster and result visualization.
**Kind:** tech
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1, observability/alerting-and-incident-response @3

A figure is a JSON specification rendered by plotly.js, so charts are interactive by default -- hover values, zoom, toggling series from the legend, rotating a 3-D scatter -- inside a notebook or exported as a self-contained HTML file. `plotly.express` produces a chart from a dataframe in one line, and Dash turns the same figures into a full web dashboard.

Reach for it when interaction is what makes the plot useful, which is exactly the case for exploring clusters or projected embeddings where a static image hides the structure. For figures destined for a paper, a PDF or a README, matplotlib is lighter and prints better.

### PMD
**Short:** Java-centric static analyzer with tunable rulesets for long methods, excessive coupling, god classes and dead code.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, apis-frameworks/design-patterns-and-principles @3

PMD parses source into an AST and runs rules over it — long methods, long parameter lists, unused private members, cyclomatic complexity, `CouplingBetweenObjects`, `GodClass`, `ExcessivePublicCount` — alongside CPD, its copy-paste detector, which finds duplicated blocks across the codebase. Rules are XPath expressions or small Java classes, so a project can encode its own conventions instead of only the shipped ones.

That is how design smells get numbers attached: an SRP or ISP argument stops being a matter of taste once a class trips a coupling threshold. Run it with a tuned ruleset, though — the defaults are noisy on real code, and a build failing on hundreds of low-value warnings gets ignored, which is worse than not running it at all.

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

Requests are saved into collections with folders, variables and per-environment values, so the same call runs against local, staging and production by switching an environment instead of editing URLs. Pre-request and test scripts run JavaScript around each call -- capture a token from a login response into a variable, assert a status code and response shape afterwards -- and a whole collection can be executed headlessly by Newman, which is how those assertions become a CI smoke test. It imports and generates OpenAPI, and speaks gRPC (building a request from a `.proto` file or from server reflection), GraphQL and WebSocket, not only REST. Reach for it to explore and share an API and to keep a runnable example of every endpoint; treat it as a client and a light test harness, not as your API documentation or your load-testing tool.

### pre-commit
**Short:** Git hook manager that runs language-agnostic lint, format and secret-scan checks before a commit lands.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, devtools/version-control-and-workbench @2, platform-delivery/ci-cd-and-release @3

A config file names hook repositories and pinned revisions, and the framework clones each one into its own isolated environment — so a Python formatter, a Go secret scanner and a shell linter coexist without anyone installing three toolchains globally. Hooks run against staged files only, which keeps the commit-time check fast, and a run-all-files mode is what you use when adopting it or when a hook set changes.

Run it in CI as well as locally. A developer can bypass the hook with a no-verify flag, so the local run is a convenience and the CI run is the actual gate.

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

It wires `protoc` into the Maven lifecycle: the plugin resolves a pinned protoc binary (and the gRPC codegen plugin) as a platform-classified artifact, compiles every `.proto` under `src/main/proto`, and adds the generated sources to the compile path so message classes and service stubs simply exist as ordinary Java types. The `compile` goal produces the message classes; `compile-custom` runs the gRPC plugin for the service base classes and stubs.

The point is that generated code never gets committed: the `.proto` file is the single source of truth and drift between schema and Java becomes impossible. Pin the protobuf runtime dependency alongside the protoc version, since generated code and the runtime library it calls into have to agree.

### protoc
**Short:** Protocol Buffers compiler: turns .proto contracts into generated message and gRPC stub code for many languages.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/data-formats-and-api-contracts @2, apis-frameworks/rpc-graphql-and-streaming @2

It reads `.proto` files and emits language-specific message classes with generated serialization and parsing code, and with the gRPC plugin it also emits client stubs and server base classes. Plugins are the extension mechanism — `--plugin` plus a `--<lang>_out` flag is how every non-core language, and every extra codegen step like validation or documentation, hooks into the same compile.

In practice you rarely run it by hand: a Maven or Gradle plugin, a Bazel rule, or `buf` drives it, and `buf` adds the two things bare `protoc` lacks, linting and breaking-change detection against a stored schema. What the tool really buys is that the `.proto` file, not any implementation, is the contract between services. The classic build failure is version skew between the compiler, the plugin, and the runtime library, so pin all three.

### protoc-gen-grpc-java
**Short:** protoc plugin that generates Java gRPC service stubs and clients from .proto definitions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/rpc-graphql-and-streaming @2

protoc itself generates the message classes for a `.proto`; this plugin generates the service side -- for each service a `FooGrpc` class holding the abstract base you extend to implement the server, the blocking, future and async client stubs, and the method descriptors and marshallers binding them to the wire. It runs as a protoc plugin, which in practice means it is wired into the Gradle or Maven protobuf plugin and downloaded as a platform-specific binary rather than invoked by hand. The generated code is a build artifact: it belongs in the build directory and on the generated-source path, not in version control, and its version must line up with the grpc-java runtime you depend on. Reach for it in any JVM project speaking gRPC -- it is not optional, it is how a `.proto` becomes callable Java.

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

Pyright type-checks a Python codebase from its annotations and inference without importing or running it, and it is fast enough to re-check on every keystroke, which is why it powers the type analysis in VS Code's Python extension. Its narrowing is thorough — `isinstance` checks, walrus assignments, `assert`, literal comparisons and `TypeGuard` all refine a type along a branch — and it implements `Protocol` structural typing fully, so a class satisfies a protocol without inheriting from it.

Expect it to reject code that mypy accepts, particularly around generics, variance and unreachable branches; `basic` and `strict` modes let you pick how much of that you want. Types are erased at runtime, so it catches contract mistakes rather than bad data — validating untrusted input still needs Pydantic or explicit checks.

### pytest
**Short:** Python test runner and fixture engine; the standard harness for unit, integration and parametrized golden-dataset tests.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

A test is a plain function whose checks are plain `assert` statements -- pytest rewrites them so a failure prints the actual operand values instead of a bare assertion error -- and setup comes from fixtures: functions marked `@pytest.fixture` that a test requests by naming them as parameters, with scopes (function, class, module, session) controlling how often they are built and `yield` providing teardown. `@pytest.mark.parametrize` turns one function into many separately reported cases, which is how a golden dataset or a table of edge cases becomes a suite rather than a loop that stops at the first failure. Its plugin ecosystem carries much of the practical value: `pytest-asyncio` for coroutine tests, `pytest-cov`, `pytest-mock`, `pytest-timeout`, `pytest-xdist` for parallel runs -- none of them built in, so a `--timeout` flag that is rejected means the plugin is missing rather than the flag being wrong. Reach for it as the default Python runner; it also executes `unittest`-style classes, so an existing suite can migrate without being rewritten first.

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

Async tests need someone to start an event loop and run the coroutine, which plain pytest will not do. This plugin does it through an anyio marker plus a backend fixture, and because the backend is a fixture you can parametrize it so the very same test body runs once on asyncio and once on trio.

Reach for it when your library must support both, which is the case for anything written against AnyIO's abstractions rather than asyncio directly. If you only ever target asyncio, pytest-asyncio is the narrower dependency and the more common choice.

### pytest-asyncio
**Short:** pytest plugin that runs async test functions and fixtures on an event loop.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

Without this plugin an `async def` test is collected, never awaited, and reported as passing while executing nothing — a silent false green. pytest-asyncio runs such tests on an event loop and does the same for async fixtures, and setting `asyncio_mode = "auto"` in your config removes the need for a per-test marker.

Use it for anything exercising asyncio code: async database drivers, an async HTTP client, async agent or chain calls. The trap is loop scope. By default each test gets a fresh event loop, so a session-scoped fixture holding a connection bound to an earlier loop fails at use, and the fix is matching the fixture's loop scope to the resource's lifetime.
### pytest-cov
**Short:** pytest plugin that measures code coverage via coverage.py and reports missing lines or enforces a threshold.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @3

`pytest --cov=app --cov-report=term-missing` runs coverage.py under pytest and prints, per file, the exact line numbers no test executed — which is the output you act on, unlike the headline percentage. It combines data correctly across `pytest-xdist` workers and subprocesses, `--cov-branch` adds branch coverage so a half-taken `if` stops counting as covered, and `--cov-fail-under=80` turns the number into a CI gate.

Use it to find code no test touches — error paths and fallbacks are the usual finds. Do not manage it as a target: coverage records that a line ran, not that its behaviour was asserted, so a suite with no assertions can report very high numbers while testing nothing.

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

Renovate scans a repository for every manifest it recognizes — npm, Maven, Gradle, pip and Poetry, Go modules, Dockerfiles and base images, GitHub Actions, Terraform providers — and opens a pull request per update with the version diff and release notes attached, so CI decides whether the bump is safe rather than a human guessing. `renovate.json` controls the policy: group related packages into one PR, restrict runs to a schedule, automerge patch and dev-dependency updates, pin digests, or hold a package back.

The point is turning upgrades into a continuous trickle instead of a yearly migration, which is also what keeps known-vulnerable transitive dependencies out. The failure mode is PR volume on a large repo, and grouping plus a schedule is the fix. Dependabot is GitHub's built-in equivalent with less configuration surface.

### respx
**Short:** Mock layer for httpx that intercepts requests at the transport level instead of monkey-patching.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

respx patches httpx at the transport layer, so a mocked route intercepts the request an httpx client would send and returns a response you declared -- routes match on method, URL, host, path pattern, query parameters, headers or JSON body, and each records the calls it received so you can assert on what was sent, not only on what came back. Intercepting at the transport is what keeps the rest of httpx real: your own client configuration, timeouts, base URL, auth flows and event hooks still run, whereas monkey-patching `client.get` skips exactly the layer where the bugs live. It supports sync and async clients and streaming responses, and works as a decorator, a context manager or a pytest fixture. Reach for it to test code that calls external HTTP APIs, and turn on the assertion that all declared routes were called so a mocked route cannot quietly outlive the code path that used it.

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

It is a single compiled binary that reimplements the rules of flake8 and a long list of its plugins, along with isort, pyupgrade and pydocstyle checks, plus a formatter that matches Black's style. Many rules carry autofixes, so a check run can rewrite the code, and it is fast enough to run on every save and over the whole repository in a pre-commit hook rather than only on changed files.

Reach for it to collapse a stack of lint and format tools into one configured in pyproject.toml. Be clear about what it is not: it does not do type inference, so it cannot tell you a call violates a Protocol or that a variable is the wrong type. It complements mypy or pyright rather than replacing them.

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

It talks the W3C WebDriver protocol to a per-browser driver process, and that explains both its strength and its weakness. The protocol is a standard the browser vendors implement themselves, so coverage across browsers, languages and cloud device grids is unmatched, and Grid fans a suite out across many machines. The cost is that WebDriver is request/response with no notion of "wait until this element is ready", so suites accumulate explicit waits and turn flaky under load. Reach for it when you need an unusual browser or language binding, or when a Grid already exists; for a new suite Playwright is the easier default.

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

It runs the same rule engine as SonarQube inside the editor, so cognitive complexity, duplicated blocks, magic numbers, god classes and the smells that signal a SOLID break surface while you type rather than in a pipeline an hour later. In connected mode it pulls the quality profile and rule set from the server, so what the IDE flags and what the build gate enforces cannot drift apart.

Reach for it on any team already running SonarQube. Point the quality gate at new code only: a gate applied to a whole legacy codebase produces thousands of findings, which everybody mutes on the first day.

### SonarQube
**Short:** Static-analysis server that gates a build on code smells, complexity, duplication and SAST security findings.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2

A scanner runs in CI and uploads findings to the server, which applies a quality gate, a pass/fail rule set over the analysis, and blocks the pull request that introduces new bugs, security hotspots, duplication or untested lines. It mixes lint-style smells with taint-tracking analysis that follows untrusted input from a request parameter through to a sink, which is how it flags injection and XSS rather than only style. The operational lesson is to gate on new code only, because a strict gate switched on over a legacy codebase produces a backlog nobody will ever burn down and the gate gets muted within a week. The self-hosted server is now branded SonarQube Server, with SonarQube Cloud as the hosted equivalent and SonarQube for IDE as the editor plugin.

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

Nexus hosts three kinds of repository: proxies that cache an upstream registry so your build does not depend on the public internet being reachable, hosted repositories for artifacts you publish yourself, and groups that present several of them behind one URL that build tools point at. That single choke point is where you enforce which versions may be consumed, promote a build from a staging repository to release, and replicate artifacts to another site.

Point every build at the group URL rather than at Maven Central or npm directly. Both the reproducibility and the supply-chain control come from the fact that nothing enters the build without passing through it.

### SpotBugs
**Short:** Bytecode-level static analyser for Java bug patterns: equals/hashCode gaps, null derefs, synchronisation errors.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1

SpotBugs is the maintained continuation of FindBugs: it analyses compiled bytecode rather than source, matching several hundred bug patterns grouped into categories -- correctness, bad practice, multithreaded correctness, performance, security -- each reported with a confidence and a priority. Working on bytecode is what lets it catch what a reader skims past: an `equals` with no `hashCode`, boxed types compared with `==`, a resource not closed on every path, a field read outside synchronization that is written inside it, a null dereference on one branch. It is also the limitation -- it sees what the compiler emitted, so generated or heavily rewritten code produces noise, and the `find-sec-bugs` plugin is what adds injection and crypto-misuse detectors. Wire it into the build with an exclusion filter and a failure threshold, and treat it as a complement to a source-level linter and never as a substitute for tests.

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

Testing a secured endpoint otherwise means either disabling security, so the rules are never tested, or hand-building tokens in every test. This artifact populates the SecurityContext before the test method runs, so a method annotated with @PreAuthorize or a MockMvc call to a protected route sees an authenticated principal with the roles you asked for; @WithUserDetails goes through your real UserDetailsService when the test depends on the actual user object.

For MockMvc it adds request post-processors that attach a CSRF token, an OAuth2 login or a JWT to a request, which is what makes a POST against a CSRF-protected endpoint pass for the right reason. Reach for it so authorization rules are covered by the same slice tests as the controllers, and remember to test the denial path as well as the allowed one.

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

A test declares the dependency it needs — `PostgreSQLContainer`, `KafkaContainer`, or `GenericContainer` for anything else — and the library starts that image, waits on a real readiness signal, hands the test a generated JDBC URL or bootstrap-servers string, and tears the container down afterwards. The test therefore runs against the actual engine, so migrations, dialect quirks, isolation-level behavior and serializer wiring are exercised rather than approximated by H2 or an embedded broker.

The cost is startup time and a Docker daemon in CI; container reuse and singleton containers exist to amortize it across a class or a suite. Reach for it for integration tests where fidelity is the point, and keep unit tests container-free.

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

Turborepo runs tasks across a JavaScript or TypeScript monorepo in dependency order. Each task's inputs — source files, dependencies' outputs, environment variables — are hashed, and if that hash already has a stored result the task is skipped and its output replayed from cache, locally or from a cache shared with CI. Combined with filtering by what changed since a git ref, a pull request rebuilds and retests only the packages it can affect.

Reach for it when the full monorepo build has become the CI bottleneck and most pull requests touch one package. It orchestrates and caches; the actual compiling and bundling is still your existing toolchain, and cache correctness depends on declaring each task's inputs and outputs honestly.
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

A single Rust binary that covers what pip, pip-tools, virtualenv, pipx and a version manager used to do between them: `uv venv` and `uv add` manage the project, `uv lock` and `uv sync --frozen` install exactly the locked set, `uv run` executes a script with its dependencies, and it downloads CPython builds itself when the required version is absent. The speed comes from a fast resolver and a global cache that hardlinks packages into each environment rather than recopying them.

Reach for it for new projects and, particularly, for Docker builds and CI, where install time dominates the pipeline. Use `--frozen` in images so a build never silently resolves a different version than the one you tested.

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

WireMock starts a real HTTP server on a local port and answers requests according to stubs you define by method, URL, headers or body matchers. Because it is real HTTP, the code under test uses its real client, real serialization, real connection pool and real timeout configuration — all the machinery that is skipped when you mock the client object instead, which is where integration bugs hide.

It also does the awkward cases: stateful scenarios where the second call returns something different, injected latency and dropped connections for resilience tests, request verification after the fact, and record-and-playback against a live API to seed stubs. Reach for it to test outbound HTTP clients and to develop against an API that does not exist yet.
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

yacc takes a grammar written as BNF-like productions with fragments of C attached to each rule, and emits the source of a bottom-up LALR(1) parser — the tables, the state machine, and the driver loop — traditionally paired with lex, which generates the tokenizer it pulls from. Instead of hand-writing a parser you declare the language's structure and its operator precedence and let the tool derive the automaton.

Its vocabulary outlived it: shift-reduce and reduce-reduce conflicts, precedence declarations to resolve the dangling else, and the whole LALR mental model come from here, and they still describe what modern generators do. New work normally uses a descendant such as bison or a parser-combinator library, but reading a conflict report is the same skill.