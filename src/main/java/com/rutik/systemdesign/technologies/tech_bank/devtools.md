# Build & test tooling — technology bank

<!-- tech-bank tier: devtools -->

The 297 tools whose PRIMARY role — the first, best-weighted one — sits in
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

It patches the request method inside `aiohttp.ClientSession`, so a URL you registered returns a canned status, headers and body without a socket ever opening. Registrations match on method plus an exact URL or a regex, several responses can be queued for the same URL so a retry loop sees a failure then a success, and every intercepted call is recorded for assertions on what was actually sent.

Use it for unit tests of code that calls an aiohttp client directly. Because the patch sits below your session configuration, it never exercises timeouts, connector limits or the DNS path, so keep at least one test against a real local server. On httpx, `respx` is the direct equivalent.

### ansible-lint
**Short:** Linter for Ansible playbooks and roles catching non-idempotent tasks and config-management anti-patterns.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, platform-delivery/infrastructure-as-code-and-config @2

It parses playbooks, roles and collections and applies a rule set that goes well beyond YAML syntax: a `command` or `shell` task with no `changed_when` or `creates` guard, a bare variable in a `when`, missing `become` scoping, deprecated module names, unnamed tasks. Rules carry tags so a team can enable or skip whole categories, and many have an autofix that rewrites the file.

Run it in pre-commit and CI on any repository holding Ansible content. It is static, so it cannot tell you a role converges correctly on a real host — Molecule with a container driver is what proves idempotence, and the linter only catches the mistakes that make idempotence unlikely.

### ANTLR
**Short:** Parser generator that turns a grammar into an LL(*) lexer and recursive-descent parser in many target languages.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @3

You write a `.g4` grammar naming lexer tokens and parser rules, and ANTLR generates the tokenizer, a recursive-descent parser, and listener/visitor base classes for walking the resulting parse tree in Java, Python, Go, C# or JavaScript. Its adaptive LL(*) strategy resolves alternatives with runtime lookahead, so it accepts directly left-recursive expression rules that classic LL parsers force you to rewrite by hand.

Reach for it when the language is yours — a DSL, a query or filter syntax, an expression evaluator, a tool that has to read legacy source. For a format that already has a mature parser, use that instead; a generated grammar is a maintenance commitment worth making only when nobody else has written one.

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

Everything follows from convention over configuration: a `pom.xml` declares coordinates, dependencies and plugins, and the fixed lifecycle (`validate`, `compile`, `test`, `package`, `verify`, `install`, `deploy`) binds plugin goals to phases so `mvn verify` means the same thing in any project. Dependency resolution is transitive with nearest-wins conflict mediation, and `dependencyManagement` or an imported BOM pins versions across a multi-module reactor.

The rigidity is the point — a new engineer can build an unfamiliar Maven project without reading the build file. It is also the limit: anything outside the lifecycle means writing or configuring a plugin, and Maven has no incremental task graph or build cache, so Gradle or Bazel wins on a large repository where build time is the constraint.

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

A registry sits between your builds and the outside world, doing three jobs at once: it proxies and caches public registries so an upstream outage or a yanked version cannot break a build, it hosts the artifacts you publish, and it exposes both behind one URL your tooling points at. Because each artifact is immutable and content-addressed, promotion between environments moves the same binary rather than rebuilding it, which is what makes the thing you tested the thing you ship.

That choke point is also where retention, access control, signature verification and vulnerability scanning attach. The cost is another stateful service to run and a storage bill that grows quietly. JFrog Artifactory and Sonatype Nexus are the general-purpose options; a single-language team is usually better served by the cloud or forge-native registry.

### ASM
**Short:** Low-level visitor-based bytecode reader/writer; the substrate under most JVM instrumentation and proxy libraries.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

ASM reads a class file and pushes events at a chain of visitor callbacks — `visitMethod`, `visitFieldInsn`, `visitMaxs` — with no intermediate object tree unless you ask for the tree API, which is why it is fast and low-allocation enough to sit in a Java agent transforming every class as it loads. Writing works the same way: a `ClassWriter` at the end of the chain emits bytes, and `COMPUTE_FRAMES` recomputes the stack map table you would otherwise have to derive by hand.

It is the substrate under CGLIB, Byte Buddy, Mockito, Jacoco and most APM agents. Use it directly only when you need that level of control or the allocation profile; for ordinary proxying and instrumentation, Byte Buddy's typed DSL removes an entire class of verifier errors.

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

### Awaitility
**Short:** Java test DSL for asserting asynchronous outcomes: await().atMost(5, SECONDS).until(...) instead of Thread.sleep.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @3

Instead of sleeping a guessed interval, you state the condition and let the library poll it: `await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> assertThat(repo.count()).isEqualTo(1))`. It returns the moment the condition holds, so the fast path costs one poll rather than the full timeout, and on failure it reports the last observed value instead of a bare assertion error.

This is what you reach for when the result arrives on another thread — a message consumer, an async event listener, an eventually-consistent read. It cannot fix a genuinely racy design; a test that only passes with a longer timeout is telling you the code has no completion signal, and exposing a future, a latch or a callback is the better fix.

### AWS Fault Injection Service
**Short:** Managed chaos-engineering service injecting EC2, ECS, RDS and EKS faults with CloudWatch stop conditions.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @3, platform-delivery/cloud-platform-and-cost @3

An experiment template names the targets by tag, resource ID or filter, the actions to run against them, and stop conditions bound to CloudWatch alarms. Actions cover EC2 instance stop and terminate, ECS and EKS task or node kill, RDS failover and reboot, plus SSM-agent-driven CPU, memory and disk pressure inside an instance and network latency or packet loss between subnets. IAM controls who may run what, so the blast radius is a permissions question rather than a convention.

The stop condition is the reason to prefer it over a homegrown script: an experiment that trips your own alarm halts and rolls back automatically. It only reaches AWS resources, so a multi-cloud or on-prem estate still needs Gremlin, LitmusChaos or Chaos Toolkit.

### bandit
**Short:** Python SAST linter that flags shell=True, pickle.loads, hardcoded passwords and other insecure code patterns.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2

It parses Python to an AST and matches plugins against node patterns, each finding carrying a severity and a confidence: `subprocess` with `shell=True`, `yaml.load` without a safe loader, `pickle` on untrusted data, `assert` used for a security check (it vanishes under `-O`), a hardcoded password or a bound host of `0.0.0.0`, weak hashes, `requests` with `verify=False`.

Run it in pre-commit or CI and tune it, because the defaults produce noise on test code and on legitimate `subprocess` use; `# nosec` with a reason comment is the escape hatch and a baseline file keeps existing findings from blocking adoption. It knows nothing about your dependencies, so pair it with a dependency scanner, and it does no dataflow, so a taint-tracking tool such as Semgrep or CodeQL catches what it cannot.

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

Bash is the GNU Bourne-Again shell: a command interpreter and a full scripting language with functions, arrays, associative arrays, arithmetic evaluation, process substitution and job control layered on top of the POSIX shell. Its real job is composition — pipelines, redirection and exit statuses let you assemble programs you did not write into something new, which is why it stays the glue for CI steps, container entrypoints and operational runbooks.

The traps are word splitting and globbing on unquoted expansion, and a pipeline whose exit status is only the last command's; `set -euo pipefail` plus quoting every expansion removes most of them, and `shellcheck` finds the rest. Once a script grows past a few hundred lines or needs real data structures, move it to Python.

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

A collection is a directory of `.bru` files, one per request, in a plain text format that diffs and merges like source, so requests live in the repository next to the code they exercise instead of in a vendor's cloud workspace. It is an offline desktop app with no account and no sync service; environments and secrets are files you choose whether to commit, and `bru` runs a collection headlessly in CI.

Reach for it when API collections should be reviewed in pull requests and versioned with the branch that changed the endpoint. The tradeoff is ecosystem: Postman has far more integrations, importers and team features, and a large existing Postman workspace has to be converted rather than opened.

### Byte Buddy
**Short:** Typed DSL for generating and subclassing Java classes at runtime or build time; the engine behind Mockito.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2, devtools/testing-and-mocking @3

Byte Buddy generates bytecode at runtime through a fluent, type-checked API — `new ByteBuddy().subclass(Service.class).method(named("charge")).intercept(...)` — so you express the transformation you want instead of writing ASM visitor callbacks and hand-computing stack frames. It can define the class into a live classloader, emit it at build time, or apply it through a Java agent that transforms classes as they are loaded.

This is the machinery under a lot of the JVM ecosystem: Mockito creates mock subclasses with it, and most APM and tracing agents use it to weave instrumentation into methods they do not own. Reach for it when you must proxy a concrete class — JDK dynamic proxies only implement interfaces — or instrument third-party code. For ordinary application logic, generated types are hard to debug and hard for the next reader to find, and plain composition wins.

### Bytecode Viewer
**Short:** GUI tool that decompiles and edits Java class files so you can inspect the bytecode the compiler emitted.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

It bundles several independent Java decompilers behind one window and shows their output side by side against the raw bytecode, which matters because decompilers disagree — one will reconstruct a lambda or a switch-on-string cleanly where another emits unreadable control flow, and comparing them is how you work out what the compiler really did. It also disassembles to instruction level, browses the constant pool, and can patch and reassemble a class.

Use it to inspect a third-party jar with no sources attached, or to confirm what desugaring the compiler applied to your own code. For everyday questions about your own build, `javap -c -p -v` is faster and already installed; the GUI earns its keep on obfuscated or source-less code.

### cassandra-stress
**Short:** Cassandra's bundled load-generation and benchmarking tool for stressing a cluster with a chosen schema and workload mix.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, data-stores/wide-column @2, observability/profiling-and-performance @3

It ships with Cassandra and drives a cluster with a generated workload: either the built-in key-value schema or, more usefully, a YAML profile in which you declare your real table, the distribution of each column's values, and a mix of named queries with relative weights. It ramps client threads, warms up, then reports throughput, latency percentiles and per-operation timing, so you can find the concurrency level at which the cluster stops scaling.

Use it to size a cluster and to validate a data model before it is in production, because most Cassandra performance problems are partition-design problems that only appear under real cardinality. Results are only as honest as the value distributions in the profile — uniform keys hide the hot partition that will actually hurt you.

### CGLIB
**Short:** Bytecode library generating runtime subclasses; the proxy mechanism Spring AOP uses for classes without interfaces.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/aop-middleware-and-scheduling @2

It generates a subclass of your class at runtime and overrides the non-final methods to insert interception, which is the mechanism behind Spring AOP on a class with no interface. Two consequences follow directly: a final class or final method cannot be advised at all, and a self-invocation through `this` never leaves the object so it never crosses the proxy, which is the usual reason an inner `@Transactional` call quietly does nothing.

You almost never call it yourself; Spring repackages it inside `spring-core` and picks it over JDK dynamic proxies when there is no interface to proxy. Understanding it matters because it explains proxy behaviour you will otherwise treat as a Spring bug.

### Chaos Mesh
**Short:** CNCF Kubernetes-native chaos platform: CRD-defined pod, network, IO and stress faults with a web UI and scheduled experiments.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/kubernetes-and-orchestration @2

An experiment is a custom resource naming a fault type, a selector for the pods it applies to, and a duration or a schedule -- pod and container kill, network latency, loss and partition, IO delay and fault, CPU and memory stress, clock skew. Because it is a CRD, the experiment is reviewed and versioned like any other manifest, and the selector is what bounds the blast radius.

What decides whether a run is useful is everything around it: a measurable steady state, a hypothesis the fault can actually falsify, and an automatic abort. Without observability good enough to state the steady state, an experiment only tells you that something broke.

### Chaos Monkey
**Short:** Netflix tool that randomly kills production instances to prove failover and circuit breakers actually work.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @3

Part of Netflix's Simian Army, it terminates a randomly chosen instance inside a group during working hours, on the argument that instances will die anyway and it is better to find out on a Tuesday morning than at 3am. The forcing function is organisational rather than technical: once random termination is continuous, stateless services, health-checked replacement and automated failover stop being an aspiration because nothing else survives.

The current implementation is tied to Spinnaker for cluster discovery and termination. Treat instance kill as the entry-level fault — it is the easiest one to survive, and latency, packet loss, dependency brownouts and partial failures are what actually take systems down, which is where Gremlin, LitmusChaos or Chaos Toolkit go further.

### Chaos Monkey for Spring Boot
**Short:** Library injecting latency, exceptions and killed beans into a running Spring Boot app for chaos experiments.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @3

Rather than killing a process, it works inside one: enabled through a profile and Spring properties, it advises beans by stereotype — `@Controller`, `@Service`, `@Repository`, `@Component` — and applies assaults to their method calls. The latency assault delays a call by a configured range, the exception assault throws, the kill-application assault shuts the context down, and a memory assault grows the heap. Watchers and the assault configuration can be flipped at runtime over Actuator, so an experiment starts and stops without a redeploy.

It is the cheapest way to prove a timeout, retry, bulkhead or circuit breaker actually fires, since you can inject the slow dependency directly. Keep it out of production dependencies except behind an explicitly disabled profile.

### Chaos Toolkit
**Short:** Open-source chaos experiment runner driving JSON/YAML fault-injection experiments from CI with a plugin ecosystem.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @2

An experiment is a declarative document with a steady-state hypothesis, a method of actions and probes that injects the fault, and rollbacks that undo it. The probes are checked before and after, so a run either confirms the system tolerated the fault or fails with a deviation -- which is what makes it runnable as a job in CI rather than a manual game day. Drivers exist for Kubernetes, the major clouds, Prometheus and Gremlin.

Reach for it when you want chaos experiments reviewed and versioned like tests. It is only as useful as your observability: without a metric that defines steady state, the hypothesis is a guess.

### ChaosBlade
**Short:** Open-source chaos engineering toolkit injecting network, CPU, memory, process and container faults to test resilience.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, traffic-edge/rate-limiting-and-resilience @2

An Alibaba-originated CLI and operator that expresses a fault as a target, an action and a matcher — `blade create cpu fullload --cpu-percent 80`, or network delay, packet loss and DNS failure on a chosen interface and port. Each creation returns an experiment UID that `blade destroy` uses to undo it, and a `--timeout` bounds the fault so a lost session cannot leave a host degraded. Beyond OS-level faults it has a JVM sandbox that injects exceptions and delays into specific Java methods, and Kubernetes and container targets.

That method-level JVM injection is the differentiator against purely infrastructural tools. It is agent- and privilege-heavy on the host, so scope it carefully and prefer the Kubernetes operator, which makes each experiment a reviewable CRD.

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

Clang is the C, C++ and Objective-C front end of the LLVM project: it parses to a typed AST, lowers to LLVM IR, and hands off to the shared optimizer and backends, which is why the same middle end serves Swift, Rust and CUDA. Keeping a full-fidelity AST with source locations is what gives it precise, quotable diagnostics with fix-it hints, and it is also the API behind clang-tidy, clang-format and every LSP server built on `clangd`.

The sanitizers are the day-to-day reason to build with it even when you ship a GCC build: AddressSanitizer, UndefinedBehaviorSanitizer and ThreadSanitizer instrument the binary and turn memory corruption or a data race into an immediate stack trace, at roughly two to twenty times slowdown depending on the sanitizer.

### click
**Short:** Python library for building command-line interfaces from decorators, with nested subcommands and rich help.
**Kind:** tech
**Lang:** python
**Roles:** devtools/version-control-and-workbench @1

Commands are ordinary functions wearing decorators: `@click.command()`, then `@click.option`/`@click.argument` declare parameters that Click parses, type-converts and validates before your function is called, so the body receives real values rather than raw strings. `@click.group()` nests subcommands arbitrarily, help text comes from the docstring, and it handles the awkward parts — prompting, hidden password input, a `Context` object passed down the command tree, progress bars, paging and colour.

Reach for it for any CLI that has grown past a couple of flags; the stdlib `argparse` needs far more code for the same nesting. Typer wraps Click and derives the same interface from type hints, which is less boilerplate again for a modern codebase.

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

### conda
**Short:** Cross-language package and environment manager that resolves binary dependencies, including non-Python system libraries.
**Kind:** tech
**Lang:** *
**Roles:** devtools/build-and-dependency-management @1

Its distinguishing property is that a package may be any binary, not only a Python wheel, so a
single environment specification can pin a compiler runtime, a BLAS implementation or a CUDA
toolkit alongside the libraries that link against them. That is why it persists in scientific
and machine-learning stacks where a pip-only environment cannot express the dependency.

The costs are real and well known: solving a large environment is slow, channel mixing produces
subtly incompatible builds, and licence terms on the default channel have pushed many teams to
community channels or to faster reimplementations of the solver. Reach for it when non-Python
binaries are genuinely part of the dependency graph, and prefer a lockfile plus a container
image when they are not.

### Conventional Commits
**Short:** Commit-message convention (feat:, fix:, BREAKING CHANGE) that drives automated semantic versioning and changelogs.
**Kind:** spec
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, platform-delivery/ci-cd-and-release @3

The format is a structured first line — a type, an optional scope, an optional `!`, then a description — followed by an optional body and footers. Types such as `feat`, `fix`, `docs`, `refactor`, `chore` and `perf` are the parseable part, and `!` or a `BREAKING CHANGE:` footer marks an incompatible change. Because the subject is machine-readable, a release tool derives the next semantic version from the commits since the last tag, generates a grouped changelog, and tags and publishes without anyone deciding a number.

Adopt it by enforcing the format in a commit-msg hook, since it degrades the moment half the commits are freeform. It says nothing about whether the message is informative — a `fix: bug` still passes the linter.

### CUP
**Short:** Java LALR parser generator with grammar actions written inline in Java; used for legacy grammars.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

The Constructor of Useful Parsers is a Java port of the yacc model: you declare terminals and non-terminals, write productions with Java semantic actions inline, and it generates an LALR(1) table-driven parser class, conventionally paired with JFlex for the scanner. Its conflict reports are the classic shift/reduce and reduce/reduce messages, resolved with precedence and associativity declarations or by restructuring the grammar.

It survives mainly in academic compiler courses and long-lived codebases that adopted it years ago. For new Java work ANTLR 4 is the better default: the grammar stays free of embedded Java, the generated visitor separates traversal from semantics, and adaptive LL(*) accepts left-recursive expression rules without the precedence gymnastics LALR requires.

### DataGrip
**Short:** JetBrains database IDE for browsing schemas, writing SQL and reviewing generated migration diffs before merge.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, data-access/schema-and-migration @2

It builds a live model of the connected database — tables, columns, keys, routines — and uses it the way an IDE uses a compiled project: completion knows which columns exist in scope, a rename refactors the references, and it flags a query against a column that is not there before you run it. One window connects to PostgreSQL, MySQL, Oracle, SQL Server, Snowflake, BigQuery and more through JDBC, with a diff tool that compares two schemas and generates the migration script between them.

Reach for it when you work across several engines and want real static analysis of SQL. It is a paid JetBrains product bundled into IntelliJ Ultimate; DBeaver is the free cross-database alternative, with weaker completion and refactoring.

### DBeaver
**Short:** Cross-database GUI client for browsing schemas, running SQL and reviewing generated migration or changelog diffs.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, data-access/schema-and-migration @2, data-stores/relational @3

A Java desktop client that speaks to anything with a JDBC driver, plus native support for the major NoSQL and cloud warehouses in the commercial edition. Beyond browsing and editing, it gives you an ER diagram generated from the live catalog, a data editor that writes back through generated DML, visual query plans, CSV and JSON import/export, and a schema compare that produces the DDL to reconcile two databases.

It is the pragmatic default when a team touches several engines and nobody wants a licence per person: Community Edition is free and open source. The tradeoff against DataGrip is polish — completion and SQL refactoring are noticeably weaker, and the Eclipse-based UI is heavy on a large schema.

### Develocity build cache
**Short:** Gradle Develocity's remote build cache, sharing task and goal outputs across machines and CI to skip repeat work.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/ci-cd-and-release @3

Gradle and Maven already hash a task's inputs to decide whether to rerun it; the remote cache moves that keyed result off the machine, so a task whose inputs someone else already built is downloaded rather than executed. The practical effect is that a CI agent with a cold workspace, or a developer switching branches, replays most of the build instead of compiling it, and the win concentrates in the expensive tasks — compilation, annotation processing, test execution with predictive test selection.

Correctness rests entirely on honest input and output declarations, so a task that reads an undeclared file or embeds a timestamp will serve a stale result. It is a commercial part of Develocity; Gradle's built-in local cache and a self-hosted HTTP cache node cover the same idea for free.

### diff2html
**Short:** JavaScript library that renders a unified git diff as side-by-side or inline HTML.
**Kind:** tech
**Lang:** js
**Roles:** devtools/version-control-and-workbench @1

It parses a unified diff — the output of `git diff`, or a patch file — into a structured model and renders it as HTML with per-file summaries, line-level intra-word highlighting, and a choice of side-by-side or inline layout. It ships as a browser and Node library plus a `diff2html-cli` that turns a commit range into a standalone page, so nothing about it requires a hosting platform.

Reach for it when a diff has to be readable outside a code-review tool: an emailed release note, a CI artifact showing what a codegen step changed, a report page. It only renders — computing the diff, resolving renames and applying syntax highlighting are your job or another library's.

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

Eclipse's distinguishing piece is that it does not use `javac`: the JDT ships its own incremental compiler that keeps a resolved model of the workspace and recompiles only the affected compilation units on save, which is what allows running code that still has errors elsewhere in the project. That model also drives its refactorings, quick fixes and the generators for constructors, `equals`/`hashCode` and delegate methods.

It is free, open source and extensible through OSGi plugins, and it remains the base for vendor tooling in enterprise and embedded work. For everyday Java the momentum is with IntelliJ IDEA and VS Code — inspections and framework support are stronger there, and the Eclipse Compiler for Java occasionally accepts or rejects code differently from `javac`, so build with the JDK compiler in CI.

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

### faker
**Short:** Generates realistic fake names, addresses and other field data for test fixtures and factories.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, ml-lifecycle/labeling-and-synthetic-data @3

A `Faker()` instance exposes providers grouped by domain — `name()`, `address()`, `email()`, `company()`, `iban()`, `date_between()`, `text()` — with dozens of locales, so a Japanese or German fixture set looks plausible in that locale rather than being ASCII noise. Seeding the instance makes the whole sequence reproducible, which is what keeps a generated fixture from turning a test flaky, and custom providers extend it for domain-specific fields.

Use it so test data reads like real data, which surfaces the bugs uniform `test1`, `test2` values hide: unicode names, apostrophes, long addresses, field-length limits. Do not use it for load-bearing uniqueness — collisions happen — and never confuse plausible data with statistically realistic data for model training.

### fakeredis
**Short:** In-process fake implementing the Redis command surface so tests run without a real Redis server.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, caching/distributed-cache @3

It implements the Redis command surface in pure Python against an in-process dictionary, and exposes it through the same client classes `redis-py` provides, so the code under test connects as usual and nothing above the client changes. Expiries, transactions, pipelines, pub/sub and the common data types work, and each test can hold its own isolated instance instead of flushing a shared server.

Reach for it in unit tests where the cache is incidental to what you are testing and a container per test run is not worth the seconds. It is a reimplementation, so coverage of newer commands, Lua scripting semantics, cluster behaviour and eviction is approximate — anything whose correctness depends on real Redis behaviour belongs in a Testcontainers-backed integration test.

### fatbinary
**Short:** CUDA toolchain utility bundling several cubins and PTX images into one fat binary; nvcc invokes it internally.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1, gpu/kernel-programming @3

A single CUDA binary usually has to run on several GPU architectures, so `nvcc` compiles the device code once per target and then calls `fatbinary` to pack the resulting SASS cubins, plus one or more PTX images, into a fat binary blob that is embedded in the host object file. At launch the CUDA runtime picks the cubin matching the device; if none matches, it JIT-compiles the embedded PTX for that architecture.

That is the mechanism behind `-gencode` and CMake's `CUDA_ARCHITECTURES`: each `sm_XX` you list adds a compiled image and grows the binary, while a `compute_XX` virtual arch adds PTX for forward compatibility at the cost of first-launch JIT time. You rarely invoke it yourself — `cuobjdump` is the tool for inspecting what ended up inside.

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

It is not an embedded database but a process manager: at test startup it downloads a real `mongod` binary for the platform, caches it, starts it on a free port, and hands your test the connection string, then kills the process and deletes the data directory afterwards. Because it is genuine MongoDB, query semantics, indexes and the aggregation pipeline behave exactly as in production.

The catch is what the default single-node deployment cannot do: no replica set means no transactions and no change streams, which is exactly the surface a lot of applications rely on. It also needs a download on a cold cache, which is awkward in a locked-down CI network. Testcontainers with the official MongoDB image is the more common modern choice and configures a replica set for you.

### flex
**Short:** Lexer generator that emits C scanning code from a regular-expression grammar; usually paired with bison.
**Kind:** tech
**Lang:** cpp
**Roles:** devtools/compiler-toolchain-and-codegen @1

You write a file of regular expressions paired with C actions, and flex compiles them into a single deterministic finite automaton, emitting a `yylex()` function that scans the input with the longest-match rule and runs the action for the winning pattern. Because it is one DFA rather than a sequence of regex attempts, scanning cost is proportional to input length regardless of how many rules there are, and start conditions let you switch rule sets for contexts like a string literal or a comment.

It pairs with bison, which calls `yylex()` for each token. For a new project a hand-written lexer is often simpler and gives better error messages, but a generated scanner remains hard to beat for a large token set with tricky overlapping patterns.

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

GCC is a collection of front ends — C, C++, Fortran, Ada, Go, D — feeding a shared middle end that optimizes the GIMPLE and RTL intermediate representations before per-architecture backends emit machine code. Optimization is selected in bands: `-O2` for production, `-O3` for aggressive inlining and vectorization, `-Os` for size, `-Og` for a debuggable build, with `-march=native` letting it use the instruction set of the machine it is compiling on.

It is the system compiler on Linux and the reference for the kernel and most distribution packages, so it is what your users' binaries were built with. Clang gives clearer diagnostics and a stronger tooling ecosystem, and building under both is a cheap way to catch code that leans on one compiler's tolerance.

### GenAI-Perf
**Short:** NVIDIA's LLM-aware load generator: measures TTFT, inter-token latency and throughput against a streaming endpoint.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, inference/model-server @2, observability/profiling-and-performance @3

LLM serving cannot be judged by request latency, so this tool measures the numbers that describe a streaming generation: time to first token, inter-token latency, output tokens per second per request, and total system throughput, all reported as distributions. It generates the load itself from a synthetic prompt distribution or a real dataset, so you control input and output token lengths — the variables that dominate prefill and decode cost — and sweeps concurrency to find where the server saturates.

It speaks the OpenAI-compatible chat and completions APIs as well as Triton's, so the same run compares vLLM, TensorRT-LLM and a hosted endpoint. Use it to pick a batch size and concurrency limit; generic HTTP load tools report a single latency number that hides the prefill/decode split entirely.

### git
**Short:** The distributed version control system - branching, history and the baseline for versioning code, config and prompts.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

Every clone holds the entire object database and history, so commit, branch, diff, blame and log are local operations and the network appears only at fetch, pull and push. That model is why branching is cheap enough to be the default unit of work, and why history can be rewritten freely before it is shared and only with care afterwards. Beyond code it is the honest baseline for versioning anything textual, including prompts, Terraform, Kubernetes manifests and config, which get review, blame and rollback for free; GitOps is built on exactly that observation. Large binaries are its weak spot, which is what LFS exists to patch.

### git bisect
**Short:** Binary search over commit history: mark one good and one bad commit and git walks you to the one that broke it.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

You give it a known-bad and a known-good revision and it checks out the midpoint, you report the result, and it halves the remaining range each time - so a thousand-commit window is about ten tests. `git bisect run <cmd>` automates the loop entirely: any command whose exit status distinguishes good from bad turns the whole search into one invocation.

Reach for it whenever a regression has a cheap reproducible test and the introducing commit is not obvious from the diff, which is most performance regressions and most flaky-behaviour changes. It needs a history that builds at every step - a broken intermediate commit forces `git bisect skip` and widens the answer to a range. Unrelated to Python's `bisect` module, which shares only the name.

### git filter-repo
**Short:** Fast git history rewriter that purges leaked secrets or large blobs from every commit; replaces filter-branch.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, security/secrets-and-cryptography @2

It streams the repository through `fast-export`, rewrites the object stream according to your path, blob and message filters, and imports the result into a fresh object database — which is why it runs in one pass, orders of magnitude faster than `filter-branch`, and why it refuses by default to operate on anything but a fresh clone. It also rewrites tags and refs consistently and can strip blobs by size or replace secret strings in file contents.

Use it to purge a leaked credential or a checked-in multi-gigabyte binary from history. Understand the consequence: every commit hash after the rewrite point changes, so every clone must be re-cloned, open pull requests break, and a leaked secret must still be rotated — the old objects survive in forks and caches.

### Git LFS
**Short:** Git extension storing large binaries outside the repo and leaving pointer files under version control.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, ml-lifecycle/experiment-tracking-and-tuning @3, data-movement/data-quality-and-lineage @3

A tracked pattern is intercepted by a clean filter on commit: the real bytes are uploaded to an LFS server and what lands in the git object database is a small pointer file naming the object's SHA-256 and size. On checkout the smudge filter fetches the real content back. History therefore stores pointers, so a clone downloads only the versions of the large files the checked-out commits actually need rather than every revision ever committed.

It is the standard answer to a repository choked by binaries — model weights, media, test fixtures. The costs are real: an extra server with its own auth and storage quota, a clone that fails oddly when LFS is not installed, and rewriting existing history to move files into LFS after the fact.

### git reflog
**Short:** Git's local log of where HEAD and branch tips pointed; the recovery path after a bad reset or rebase.
**Kind:** api
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

### GitHub/GitLab/Bitbucket
**Short:** Hosted Git platforms providing pull requests, protected branches, CODEOWNERS review rules and CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, platform-delivery/ci-cd-and-release @2

What these add on top of git is the review and policy layer: a merge request as a first-class object with discussion threads and required approvals, branch protection that forbids force pushes and direct commits to the default branch, CODEOWNERS mapping paths to the reviewers who must sign off, and required status checks that block a merge until CI is green. Each also runs the CI itself, hosts packages and container images, and issues short-lived tokens so a pipeline can authenticate without a stored secret.

That combination is what makes trunk-based development and GitOps workable, since the branch rules are enforced by the server rather than by agreement. The cost is lock-in: issues, pipelines and permissions are platform-specific in a way the git history itself never is.

### Google AutoService
**Short:** Annotation processor that generates META-INF/services entries so a class is discoverable by ServiceLoader.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/dependency-injection-and-config @3

Registering a service implementation means writing its fully qualified name into a `META-INF/services/<interface>` file, and the failure mode is silent — rename or move the class and `ServiceLoader` simply stops finding it, with no compile error. Annotating the implementation `@AutoService(MyInterface.class)` makes an annotation processor generate and maintain that file at build time, and it also verifies the annotated class really implements the interface.

Use it in anything that publishes a plugin over `ServiceLoader`: JDBC drivers, annotation processors, SPI extensions, a Java agent's providers. Two caveats: shading several jars together overwrites rather than merges the service files unless the build's transformer is configured, and a JPMS module declares services with `provides ... with ...` in `module-info` instead.

### Google AutoValue
**Short:** Java annotation processor generating immutable value classes with equals, hashCode, toString and a builder.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

The processor reads the abstract accessors you declared and generates a package-private concrete subclass, so the value semantics are derived from the class's own definition and cannot drift: add a field and `equals`, `hashCode` and `toString` include it on the next compile. Nothing is reflective or rewritten — the generated file is plain readable Java on the source path, and a static factory or a generated builder is the only way to construct it.

Extensions cover the surrounding needs: `@Memoized` for a derived property computed once, and generated Gson or Moshi adapters. Compared with Lombok it generates ordinary source rather than mutating the compiler's AST, so IDEs and other processors see it without a plugin — at the cost of more boilerplate in the abstract class you write.

### Google compile-testing
**Short:** Test library that compiles source in-process and asserts on annotation-processor output and diagnostics.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/compiler-toolchain-and-codegen @2

It runs `javac` in-process over source you supply as strings or resources, then lets you assert on the outcome with Truth: that compilation succeeded or failed, that a specific diagnostic was emitted on a particular line, and that the generated file is equivalent to an expected one — compared as parsed syntax trees, so formatting and import order do not make the test brittle.

This is how you test an annotation processor properly, because the alternative is compiling a fixture project in a separate build and diffing text. Cover the failure paths as deliberately as the happy path: a processor's most valuable behaviour is usually the clear compile error it raises on a misuse, and that error is only ever exercised by a test like this.

### GraalVM
**Short:** Polyglot JVM with an AOT native-image compiler producing standalone binaries with millisecond startup and low RSS.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, platform-delivery/container-and-image @3

GraalVM is an OpenJDK distribution whose JIT is the Graal compiler, written in Java, plus two things a stock JDK lacks: the `native-image` ahead-of-time compiler and Truffle, a language-implementation framework whose interpreters for JavaScript, Python, Ruby and WebAssembly are partially evaluated by Graal into optimized machine code and can share objects with Java in one process.

In practice teams adopt it for one of two very different reasons. Run it as a JVM and you get the Graal JIT's stronger escape analysis and inlining on some workloads. Build with `native-image` and you trade peak throughput and every dynamic feature for millisecond startup and a small resident set — the right bargain for CLIs, functions and scale-to-zero services, and usually the wrong one for a long-lived throughput-bound server.

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

Native image needs to be told about every reflective lookup, dynamic proxy, resource and JNI call, and most of those live in libraries you did not write. This repository is a community-maintained store of that metadata, keyed by group, artifact and version, so the Maven and Gradle Native Build Tools plugins fetch the right JSON for each dependency at build time instead of you tracing the library yourself.

It is why a Spring Boot or Micronaut application with common dependencies builds natively out of the box. Two limits to expect: coverage is only as good as contributions, so an unmaintained or niche library still needs hand-written hints or a tracing-agent run, and metadata is pinned per version, so a dependency bump can outrun the entry that covered it.

### GraalVM tracing agent
**Short:** JVM agent recording reflection, resource, proxy and JNI use so native-image gets the metadata it needs.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

Attached with `-agentlib:native-image-agent=config-output-dir=...`, it observes a running JVM and records every reflective lookup, dynamic proxy, resource load, serialization and JNI call as it happens, writing the JSON configuration files `native-image` reads. Repeated runs can be merged into one directory, so a suite of scenarios accumulates rather than overwriting.

The method is what limits it: it records what your execution actually did, so any path the run never took contributes no metadata and fails at run time in the native binary with a missing-class or missing-method error rather than at build time. Drive it with the full integration test suite, not a smoke test, and treat the output as a starting point to review and commit — not as proof of completeness.

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

A build is a directed graph of tasks, each declaring its inputs and outputs; Gradle hashes those and skips any task whose inputs are unchanged, replaying results from a local or remote build cache when it has them. The configuration script is a real program in Kotlin or Groovy, and the configuration cache stores the resulting task graph so subsequent runs skip evaluating it. A long-lived daemon keeps the JVM and file-system watches warm.

That model is why it is the default for Android and for large multi-project JVM builds: incremental builds are fast and the graph is arbitrarily extensible. The price is that a build file is code, so it can become an unreviewable program, and a task with undeclared inputs silently produces a stale cached result.

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

The product that added a shared remote build cache, build scans and test distribution to Gradle and Maven builds was renamed Develocity in 2024; the name Gradle Enterprise refers to the same platform before that rename. Its central idea is observability for builds — every run publishes a scan containing the full task graph, timings, cache hits and misses, dependency resolution and test results, so a slow or non-reproducible build is diagnosed from data rather than from someone's local reproduction.

On top of that sit the accelerators: the remote cache, test distribution across agents, and predictive test selection that runs only the tests a change can affect. It is commercial and self-hosted or SaaS; the free equivalents are Gradle's own local cache and a self-run HTTP cache node.

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

Applying `org.graalvm.buildtools.native` adds a `nativeCompile` task that runs `native-image` over the runtime classpath, a `nativeRun` for the binary, and `nativeTest`, which compiles the JUnit suite itself into a native image and runs the tests there. A `graalvmNative` block configures build arguments, the main class and the toolchain, and the plugin wires in metadata from the GraalVM Reachability Metadata Repository automatically.

`nativeTest` is the part worth insisting on: a native image can differ from the JVM run in exactly the places static analysis had to guess, so a suite that passes on the JVM proves nothing about the binary you ship. Expect native compilation to take minutes and a lot of memory, so keep it on a separate CI job rather than the default build.

### Gradle protobuf plugin
**Short:** Gradle plugin running protoc during the build to generate Java and gRPC stubs from .proto into a source set.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2, apis-frameworks/rpc-graphql-and-streaming @3

It resolves `protoc` and any codegen plugins as platform-classified artifacts from a repository, so the compiler version is pinned in the build file rather than installed on each machine, then compiles every `.proto` in a source set and registers the output directory as generated sources. The `protobuf` block selects the artifact version and declares plugins such as `grpc`, with per-task options for the generated variants.

The reason to wire it in rather than commit generated code is that the `.proto` file stays the only source of truth and schema drift becomes impossible. Keep three versions aligned — the `protoc` artifact, the codegen plugin and the protobuf runtime dependency — because mismatches surface as `NoSuchMethodError` at run time, not as a build failure.

### Gradle Shadow plugin
**Short:** Gradle plugin that builds a fat/uber JAR and relocates package names to avoid dependency clashes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

The `shadowJar` task unpacks every runtime dependency and repacks the classes into one executable jar, and its distinguishing feature is relocation: a configured prefix rewrites package names in both the class files and the bytecode references, so your bundled Guava or Jackson cannot collide with a different version already on the consumer's classpath. Merge transformers handle the files that must be combined rather than overwritten, notably `META-INF/services` entries and Netty's descriptors.

Relocate whenever you publish a library that shades dependencies; skip it for a self-contained application jar, where the flattening alone is the point. Shading always breaks signed jars, hides the real dependencies from vulnerability scanners, and makes stack traces name relocated packages, so use it for genuine conflicts rather than convenience.

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

The wrapper is a small checked-in script plus `gradle-wrapper.properties` naming a distribution URL and, ideally, a SHA-256, and a tiny jar that downloads and caches that exact distribution on first use. Running `./gradlew` therefore builds with the version the repository declares, whatever is installed on the machine — which is the whole point, since Gradle's behaviour changes materially between majors and a build that works locally must work identically on CI.

Commit all four wrapper files and upgrade with `./gradlew wrapper --gradle-version=X`, never by hand-editing the properties. The wrapper jar is executable code in your repository, so pin `distributionSha256Sum` and treat a wrapper change in a pull request as a security-relevant diff.

### Grafana Cloud k6
**Short:** Managed k6 service running large geo-distributed load tests with hosted results and trend comparison.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

This is k6 run as a managed service inside Grafana Cloud: `k6 cloud run script.js` uploads the same script you run locally, and the platform allocates load generators across chosen regions, aggregates their output, and stores the result timeseries with the full percentile breakdown. The value over running k6 yourself is scale and origin — hundreds of thousands of virtual users without provisioning agents, and traffic arriving from the regions your users are actually in.

Because it is part of the Grafana stack, a test run correlates directly with the Prometheus metrics, logs and traces from the system under test on the same dashboard and time axis, which is what turns a latency spike into a diagnosis. It is metered by virtual-user-hours, so keep exploratory runs local.

### graphql-code-generator
**Short:** Generates typed clients, resolvers and operation types from a GraphQL schema and documents.
**Kind:** tech
**Lang:** js
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/rpc-graphql-and-streaming @2, apis-frameworks/data-formats-and-api-contracts @3

Point it at a schema (a file, or introspection against a running server) and your `.graphql` documents, and a pipeline of plugins emits typed artifacts: TypeScript types for every schema type, per-operation variable and result types, typed React or Vue hooks on the client, and resolver signatures on the server. Because the types come from the schema itself, a field that is renamed or made nullable becomes a compile error at every use site instead of a runtime `undefined`.

Run it in the build and in a CI check that regenerates and fails on a diff, so generated output cannot drift from the schema. The cost is another codegen step and a large generated surface; a small client that touches two queries is usually better off with hand-written types.

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

An agent on each host or as a Kubernetes DaemonSet executes attacks selected from a catalogue — CPU, memory, disk and I/O pressure, process kill, host shutdown, and network latency, packet loss, blackhole and DNS failure scoped to specific hosts and ports. Targeting is by tag, and every attack carries a duration with a server-side halt, plus a global one-click stop, so blast radius and rollback are enforced by the platform rather than by the operator's discipline.

Scenarios chain attacks with escalating magnitude and check a health signal between steps, and results are recorded for audit. Reach for it when chaos engineering has to be a governed practice across many teams. It is commercial and agent-based; Chaos Toolkit or LitmusChaos cover the same ground for free if you can operate them yourself.

### hatch
**Short:** Modern Python project manager: PEP 517 builds, environment management, scripts and publishing from pyproject.toml.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

Hatch drives a project from `pyproject.toml` alone: `hatchling` is a PEP 517 backend that builds wheels and sdists with no `setup.py`, environments are declared in the same file and created on demand so `hatch run test:cov` builds a matching environment and runs the script inside it, and `hatch version` reads and bumps the version from the source of truth you point it at. Matrix environments run the same script across several Python versions.

It is the tool to reach for when you publish libraries and care most about build backend and environment matrices. It deliberately does not lock dependencies the way Poetry and uv do, so an application that needs a reproducible install set is better served by one of those.

### Hurl
**Short:** Plain-text HTTP test runner that chains requests and asserts on status, headers and JSON bodies from CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

A `.hurl` file is a sequence of plain-text requests, each optionally followed by asserts on the status, headers, and body via JSONPath, XPath, regex or a byte comparison, plus captures that pull a value out of one response into a variable used by the next — which is how a login, a create and a fetch become one runnable file. It is a single Rust binary built on libcurl, so there is no runtime to install, and `--test` mode prints a report and sets the exit code.

That makes it a good fit for CI smoke tests and health checks that should live in the repository as reviewable text. It is deliberately not a programming language: anything needing loops or real logic outgrows the format and belongs in a test framework.

### Husky
**Short:** Node.js git hook manager that wires lint, format and test commands into commit and push hooks.
**Kind:** tech
**Lang:** js
**Roles:** devtools/version-control-and-workbench @1, devtools/static-analysis-and-linting @2

Husky writes git's `core.hooksPath` to a checked-in directory, so hooks are versioned with the repository and installed for everyone by an ordinary `npm install` rather than by each developer copying scripts into `.git/hooks`. A hook is just a shell script in that directory, usually invoking `lint-staged` so formatters and linters run against staged files only, or `commitlint` to reject a commit message that breaks the Conventional Commits format.

It is the JavaScript ecosystem's answer to the problem; `pre-commit` is the language-agnostic one and manages tool installation as well. Either way the local hook is a convenience, not a gate — `--no-verify` bypasses it — so the same checks must run in CI.

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

It converts a JSON Schema into a Hypothesis strategy that generates instances satisfying it, honouring `type`, `enum`, numeric and length bounds, `required`, `patternProperties` and the combinators, and it deliberately biases toward the awkward values a hand-written example set omits: empty objects, boundary numbers, unicode strings, the missing optional field.

That makes it the shortest path to property-testing an API against its own contract — take the OpenAPI request schema, or `Model.model_json_schema()` from a Pydantic model, and assert that every generated instance validates and round-trips. Where it strains is schemas full of `$ref` indirection or regex-constrained strings, which can slow generation or exhaust it; narrow the schema for the test rather than fighting the generator.

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

By default Gradle treats an annotation processor as opaque and recompiles every source file whenever any of them changes. A processor opts out of that by declaring itself in `META-INF/gradle/incremental.annotation.processors` as either isolating — its output for a given element depends only on that element, so changing one class regenerates one file — or aggregating, where it consumes many elements to produce a shared artifact and Gradle must reprocess all originating elements.

Isolating is what you want; it is also a real constraint, because a processor that reads unrelated types or writes a registry cannot honestly claim it. Getting this wrong is a correctness bug, not just a speed one, so verify with `-Dorg.gradle.debug.annotation.processing=true`, which reports which processors forced a full recompile.

### Insomnia
**Short:** Desktop API client for composing and replaying HTTP, GraphQL and gRPC requests against a running service.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, devtools/version-control-and-workbench @2, apis-frameworks/web-framework-and-http-client @3

A desktop client organised around workspaces, environments and request chaining: a response can be templated into a later request through a tag, so an auth token captured from a login flows into subsequent calls without manual copying, and OAuth 2, JWT and client-certificate auth are built in. Beyond REST it speaks GraphQL with schema-driven completion, gRPC from a `.proto` or reflection, WebSocket and SSE, and it can generate config for Kong from an OpenAPI spec.

Reach for it as a lighter, less account-driven alternative to Postman for exploring an API. If keeping collections in git and reviewing them in pull requests matters more than the GUI, Bruno stores everything as plain files by design.

### InSpec
**Short:** Executable compliance framework that asserts the actual state of a machine matches policy after configuration.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/infrastructure-as-code-and-config @2, security/privacy-and-compliance @3

A control is a Ruby DSL block that describes desired state — a package installed, a port not listening, a file's mode and owner, a kernel parameter, an AWS security group without an open ingress rule — and the runner evaluates it against a target reached locally, over SSH or WinRM, into a container, or through a cloud API, reporting pass, fail or skip per control with a machine-readable output for a compliance dashboard.

The distinction that matters is that it asserts observed state rather than configuration intent, so it catches drift that a converged Ansible or Terraform run would report as fine. Profiles can inherit and override each other, which is how a team specialises a CIS benchmark. It only checks; remediation stays with your configuration tool.

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

Inspections run continuously against the IDE's resolved type model, so they see more than a text-based linter: a nullability contract violated across a call, a stream chain that can be simplified, a `String` comparison with `==`, an inheritance hierarchy that would be clearer as delegation. Most carry a quick fix that performs the change as a refactoring, and the same engine runs headlessly over a whole project for a CI report.

The refactorings are safe because they operate on the type graph rather than on text — rename, extract method or interface, change signature, inline, move — updating references, imports and even string usages where it can prove them. Two habits pay off: run structural search and replace for a pattern no shipped inspection covers, and check in a shared inspection profile so the whole team sees the same warnings.

### IntelliJ Stream Trace Debugger
**Short:** IntelliJ debugger view that shows the elements entering and leaving each stage of a Java Stream pipeline.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, observability/profiling-and-performance @3

Pause on a line containing a stream pipeline, open the trace view, and the IDE re-evaluates the pipeline while recording what each stage received and emitted, then draws the elements as columns with lines linking an input to the output it produced. A `filter` that dropped everything, a `flatMap` that fanned out more than expected, a `distinct` collapsing on the wrong `equals` — each is visible as a stage where the column empties or changes shape.

It exists because a stream is a single expression with no intermediate variables to inspect, so the usual debugger technique of stepping and watching does not apply. Note that it re-runs the pipeline to collect the trace, so a stream over a consumed source or with side-effecting stages will not behave identically.

### IntelliJ structural replace
**Short:** IntelliJ feature matching and rewriting code by AST pattern, e.g. finding == comparisons on reference types.
**Kind:** tech
**Lang:** java
**Roles:** devtools/version-control-and-workbench @1, devtools/static-analysis-and-linting @2

Structural search matches on the parsed syntax tree rather than on characters: a template such as `$a$ == $b$` with a filter constraining `$a$` to a reference type finds every reference comparison regardless of spacing, comments or line breaks, and structural replace rewrites each match to a supplied template. Variables can be constrained by type, name regex, occurrence count and whether they are read or written.

That is the difference from a regex sweep, which cannot tell a match inside a string literal or a comment from real code and cannot reason about types at all. Reach for it for a mechanical migration across a large codebase — a deprecated API, an idiom the team has abandoned — and always review the preview list before applying, because a too-loose template matches more than you meant.

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

It compares two jars class by class at the bytecode level and classifies every difference by the compatibility it breaks: removing a method or narrowing its visibility, changing a return type or a parameter, adding an abstract method to an interface without a default, changing a field's type. Binary and source compatibility are reported separately, because they differ — adding an overload is source-incompatible in some call shapes yet binary-compatible.

As a Maven or Gradle plugin it fails the build when a change violates the rules you configured, which is what makes a semantic-versioning promise enforceable rather than aspirational. Wire it into any published library, and use the exclusion list for genuinely internal packages so the check stays about the public API.

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

The reference Java compiler parses source into an AST, resolves and type-checks it, desugars the language features that have no bytecode representation — generics erased, lambdas turned into `invokedynamic` call sites, inner classes given synthetic accessors, enhanced `for` rewritten — and writes class files. It resolves against a classpath, a module path, or both, and `--release N` compiles against the historical API of an older JDK rather than merely targeting its bytecode version.

It is also an extension point: annotation processors run in rounds before compilation completes and may generate new sources, and a compiler plugin such as Error Prone inspects the same AST to turn bug patterns into errors. Two flags earn their keep in any build — `-Xlint:all` and `-Werror`.

### JavaCC
**Short:** Java parser generator producing recursive-descent LL(k) parsers from a grammar with embedded Java actions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

JavaCC generates a top-down LL(k) recursive-descent parser from a `.jj` grammar in which productions are written as Java methods with embedded actions, so the generated parser reads much like code a person would write and a stack trace points at a recognisable production. Lookahead is configurable globally and per-production with `LOOKAHEAD(k)` or a syntactic predicate, which is how you resolve a choice conflict the default single-token lookahead cannot.

Its LL nature is the constraint: left recursion must be rewritten by hand, and expression grammars therefore need an explicit precedence cascade of productions. It is still used in long-lived projects and its JJTree companion builds an AST for you, but ANTLR 4 handles left recursion directly and keeps the grammar free of Java, which makes it the better default for new work.

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

Emitting Java as concatenated strings breaks on the details — imports, generic type arguments, nested classes, escaping — so JavaPoet models the output instead: `TypeSpec`, `MethodSpec`, `FieldSpec` and `ParameterSpec` builders assembled into a `JavaFile` that formats and indents itself. Its format specifiers are the reason to use it: `$T` takes a `TypeName` or a `ClassName` and the writer records it, collecting the import list and shortening the reference automatically, while `$L`, `$S` and `$N` handle literals, quoted strings and named members.

Use it inside an annotation processor, writing through the `Filer` so the compiler picks the file up in the next round. KotlinPoet is the same design for Kotlin output.

### Javassist
**Short:** Bytecode manipulation library that edits classes from Java source strings; still used by some legacy frameworks.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

Javassist's distinguishing feature is a source-level API: `CtMethod.insertBefore("...")` takes a fragment of Java-like source, compiles it against the class's own context, and splices the resulting bytecode in, so simple instrumentation needs no understanding of the operand stack. A lower-level bytecode API exists underneath for cases the compiler cannot express.

That convenience costs correctness at the margins: its embedded compiler supports a restricted dialect with no generics, no lambdas and limited inner-class handling, and errors surface as compile failures inside a string at run time. It remains in older frameworks and in Hibernate's history, but new instrumentation work should use Byte Buddy, whose typed DSL is checked by `javac` and whose agent support is far better developed.

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

Point it at a jar, a directory of class files or a single class and it reports every use of a JDK API marked `@Deprecated`, with `--for-removal` narrowing the output to the ones actually scheduled to disappear and `--release N` asking what a given JDK version considers deprecated. It reads bytecode, so it covers dependencies you have no source for — which is where the upgrade blockers usually are.

Run it as the first step of a JDK upgrade, before anything else: the answer tells you whether a library still on the classpath calls something that has already been removed in the target release. It only knows about JDK APIs and only sees static references, so reflection and your own deprecations are invisible to it.

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

Given a C header, jextract parses it with libclang and generates Java classes for the Foreign Function and Memory API: a method handle per function, `MemoryLayout` descriptors and typed accessors per struct, and constants for the macros it can evaluate. Calling into the native library then means loading it and invoking generated methods on `MemorySegment` values, with no C shim and no `native` method to implement.

Compared with JNI this removes the whole hand-written glue layer and its associated crashes, and off-heap memory becomes explicitly scoped rather than manually freed. Expect the generated surface to be large and low-level — it mirrors C, pointers and all — so wrap it in a small idiomatic Java facade rather than exposing it, and regenerate whenever the header changes.

### JFlex
**Short:** Java lexer generator that turns a regex-based specification into a fast scanner, usually paired with a parser generator.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

You declare macros and regular-expression rules with Java actions, and JFlex compiles the whole rule set into one DFA driving a generated scanner class that returns tokens on each call, applying longest-match with earliest-rule tie-breaking. Lexical states let a rule set switch context — inside a string, inside a block comment — which is how you tokenise constructs a single flat regex list cannot.

It is the Java successor to flex and pairs with CUP or a hand-written parser through a standard token interface. For a new project weigh it against ANTLR 4, which generates the lexer and the parser from one grammar file; JFlex is worth it when you want fine control of the scanner or already have a parser expecting a `Scanner` interface.

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

A pure-Java reimplementation of git — object database, packfiles, refs, index, merge and rebase, and the HTTP and SSH transports — exposed both as a low-level object API and as a fluent porcelain layer where `Git.cloneRepository()` or `git.pull().call()` mirrors the command line. Because it runs in-process there is no forked binary, no shell quoting, and no git installation required on the host.

That is why it underpins Eclipse's git support, Gerrit, and Spring Cloud Config Server, which clones and pulls a configuration repository at run time. It lags the C implementation on newer features and on raw performance for very large repositories, so a build tool that can simply shell out to `git` usually should.

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

A `.jmod` file is a packaging format for a module that a jar cannot carry: alongside classes and resources it holds native libraries, legacy command-line tools, config files and header files, in a zip-based layout with a dedicated section per kind. The JDK's own modules ship this way, which is why `jlink` can assemble a runtime image containing native code and launchers rather than just class files.

Its scope is deliberately narrow. A `.jmod` is a link-time artifact only — you cannot put one on the module path and run it, and you cannot publish it as an ordinary dependency. Package normally as a modular jar and reach for `jmod create` only when a module carries native libraries that must end up inside a `jlink` image.

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

It registers as a JUnit Platform engine, so `@Property` methods live beside ordinary `@Test` methods in the same suite and run under the same tooling. Parameters are supplied by `Arbitrary` generators — built in for the common types, composed with `map`, `filter`, `flatMap` and `combine` for domain objects, or supplied by an `@Provide` method — and a failing property is shrunk to a minimal counterexample, then recorded so the same input is retried first on subsequent runs.

Use it where invariants are stronger than examples: a round-trip through a serializer, a comparator's transitivity, a state machine's reachable transitions. `Statistics.collect` is worth using early, because generators frequently produce a distribution far narrower than you assumed and the property then proves very little.

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

JUnit 5 is three parts rather than one library: the Platform, which discovers and runs tests and is what build tools and IDEs integrate with; Jupiter, the programming model you write against; and Vintage, an engine that runs JUnit 4 tests unchanged so migration can be incremental. Jupiter dropped the runner-and-rule mechanism for a single `@ExtendWith` extension model with lifecycle callbacks and parameter resolution, which is why a test can now use several extensions at once where JUnit 4 allowed one runner.

The everyday additions are `@ParameterizedTest` with its argument sources, `@Nested` classes for grouping context, `@DisplayName`, conditional execution and `assertAll`. Note that assertions stay deliberately minimal — AssertJ is what most projects add for readable failures.

### JUnit 5 lifecycle
**Short:** JUnit's fixed test skeleton of BeforeAll/BeforeEach/test/AfterEach/AfterAll into which your fixture steps are injected.
**Kind:** concept
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @2

The skeleton is fixed and the framework owns it: `@BeforeAll` once per class on a static method, then for each test `@BeforeEach`, the test method, `@AfterEach`, and finally `@AfterAll`. Your setup and teardown steps are injected into that sequence rather than sequenced by you, which is the template method pattern in its most widely used form — and why an instance is created fresh per test method unless `@TestInstance(PER_CLASS)` says otherwise.

The consequence to internalise is isolation: shared mutable state that survives between tests produces order-dependent failures that only appear when someone runs the class in a different order or in parallel. Extensions hook the same phases, so a `@BeforeEach` and an extension callback compose predictably rather than fighting each other.

### JUnit 6
**Short:** The current Java test framework: @Test, @ParameterizedTest, lifecycle callbacks and the @ExtendWith extension model.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

JUnit 6 keeps the Jupiter programming model — `@Test`, `@ParameterizedTest`, `@Nested`, `@ExtendWith` — and spends its major version on the baseline instead: it requires a modern JDK rather than the Java 8 floor JUnit 5 carried for a decade, which lets the framework use records, sealed types and the newer language features internally and in its APIs. Long-deprecated APIs from the 5.x line are removed, and the Platform, Jupiter and Vintage artifacts are versioned together.

Migration from a recent 5.x is mostly a version bump plus removing whatever the deprecation warnings named, and the BOM is the way to do it so the engine and API cannot drift apart. Check third-party extensions for a compatible release before upgrading; that is the usual blocker.

### JUnit Jupiter
**Short:** The JUnit 5 programming model: test lifecycle, assertions, parameterized and nested tests on a Java 17 baseline.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

Jupiter is the API and engine pair — `junit-jupiter-api` for what you compile against, `junit-jupiter-engine` for what runs on the Platform at test time — and its central design decision is the extension model. A single `Extension` interface family covers lifecycle callbacks, conditional execution, parameter resolution, exception handling and test-instance post-processing, registered declaratively with `@ExtendWith` or automatically through `ServiceLoader`, so several extensions compose where JUnit 4 permitted exactly one runner.

That is what lets Spring, Mockito and Testcontainers all participate in the same test class. The rest of the model follows from it: `@ParameterizedTest` is an extension supplying arguments, and `@Nested` gives an inner class its own lifecycle so a test hierarchy can mirror the states it describes.

### JUnit Platform
**Short:** JUnit 5's launcher and engine SPI: discovers, filters and runs test engines such as Jupiter and Vintage.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

The Platform is the launcher layer everything else sits on. `junit-platform-launcher` discovers tests through a `TestEngine` SPI, applies filters by tag, package or class, and emits a stream of execution events; build tools and IDEs integrate with this API once and thereby run any engine. Jupiter and Vintage are two engines, but so are jqwik, Spock, Cucumber and Kotest, which is why they interoperate in one suite and one report.

The practical touchpoints are `junit-platform-console-standalone` for running tests without a build tool, and the configuration parameters that enable parallel execution. Note that a missing launcher or an engine version mismatched with the API most often shows up as zero tests discovered rather than as an error, so pin everything through `junit-bom`.

### k6
**Short:** CI-native load and performance testing tool: Go engine, JavaScript test scripts, thresholds as pass/fail gates.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, observability/profiling-and-performance @3

Tests are JavaScript modules -- a default exported function each virtual user runs in a loop, plus an `options` object declaring stages, ramps and thresholds -- executed by a Go runtime, so VUs are goroutines rather than OS threads and one machine drives far more concurrency than a thread-per-user tool. Thresholds are what make it CI-native: a rule such as a p95 request duration under 300 ms fails the run with a non-zero exit code, so a performance regression breaks the build instead of sitting in a report nobody opens. Its executors include constant and ramping arrival rate, not just VU counts, which is the correct shape when you care about requests per second rather than concurrency, and results stream out to Prometheus and other backends. Reach for it when load tests should live in the pipeline beside the code; note the script runtime is not Node, so most npm libraries do not work, and browser-level testing needs its separate browser module.

### k6-operator
**Short:** Kubernetes operator that splits a k6 load test across N runner pods and aggregates the results.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/kubernetes-and-orchestration @2

A `TestRun` custom resource names a ConfigMap holding the script and a parallelism count; the operator splits the test into that many runner pods, passes each an execution segment so together they generate exactly the declared load once, and runs an initializer pod first to validate the script. Results are aggregated from the runners, typically streamed to Prometheus or another output rather than collected on one node.

This is how you exceed a single machine's virtual-user ceiling using the cluster you already have instead of a managed service. Two things to watch: the generators compete with the system under test for cluster resources unless you isolate them by node or namespace, and per-runner percentiles cannot simply be averaged, so aggregate through a real metrics backend.

### KAPT
**Short:** Kotlin Annotation Processing Tool, the Kotlin bridge to Java annotation processors (now largely superseded by KSP).
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @3

The Kotlin compiler cannot feed Java annotation processors directly, so KAPT generates Java stubs for every Kotlin declaration, runs the standard `javax.annotation.processing` pipeline over those stubs, and merges the generated sources back into the compilation. Stub generation is the whole cost: it is effectively an extra compile of the entire module, it defeats incremental compilation in common cases, and errors sometimes point at synthetic stub code rather than your source.

It exists for compatibility with the Java ecosystem — Dagger, Room, older Retrofit-adjacent processors. KSP is the replacement, reading Kotlin symbols directly with no stubs and running substantially faster; move any dependency that publishes a KSP processor, and keep KAPT only for the ones that do not.

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

The Kotlin analogue of JavaPoet, and the difference is that it models Kotlin's own vocabulary rather than approximating it through Java: `FunSpec`, `PropertySpec`, `TypeSpec` for classes, objects, interfaces and enums, plus nullable types, default parameter values, extension functions, suspend modifiers, delegation and annotations with named arguments. `%T` records the type it formats so imports and aliases are collected automatically, with `%L`, `%S`, `%N` and `%P` for literals, strings, names and templates.

It is the standard emitter for KSP processors. Generating Kotlin as text is the alternative and it fails in exactly the places the model handles — nullability markers, generic variance, import collisions between same-named classes.

### KSP
**Short:** Kotlin Symbol Processing - the Kotlin-native replacement for annotation processing, generating code from symbols.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

KSP runs as a Kotlin compiler plugin and exposes the program through a Kotlin-shaped API — `KSClassDeclaration`, `KSFunctionDeclaration`, resolved types with nullability and variance — so a processor sees suspend functions, default arguments, sealed hierarchies and extension receivers as themselves rather than as the erased Java stubs KAPT produced. Skipping stub generation is where the speed comes from; it also reports dependencies per generated file so Gradle can process incrementally.

Write new processors against it and prefer a library's KSP artifact when one exists. The constraints to know: it is Kotlin-first, so a mixed-source module may still need KAPT for the Java side, and processors run before type-checking is complete, so resolution is explicit through `resolve()` and errors are reported rather than thrown.

### lark
**Short:** Python parsing toolkit with Earley and LALR parsers built from an EBNF-style grammar.
**Kind:** tech
**Lang:** python
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @2

Lark takes an EBNF-style grammar and can run it with either an Earley parser, which handles any context-free grammar including ambiguous ones and returns the ambiguity explicitly, or a LALR(1) parser that is far faster but rejects grammars with conflicts — switching between them is a one-argument change, so you prototype with Earley and tighten to LALR once the grammar stabilises. It builds a parse tree automatically, and a `Transformer` walks it bottom-up to produce your own objects.

It is pure Python with no build step and no code generation, which makes it the pragmatic choice for a DSL, a config or query syntax, or a data format inside a Python application. For parsing a mainstream programming language, `tree-sitter` has maintained grammars and incremental reparsing.

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

BellSoft's Native Image Kit is a downstream distribution of GraalVM's native-image toolchain, released alongside Liberica JDK on the same schedule as OpenJDK security updates and available for a wider spread of platforms than the upstream builds, including ARM and musl-based Linux for small container images. Functionally it compiles the same way, so a build script switching to it needs no change beyond the toolchain.

Choose between the downstream distributions on support and platform coverage rather than on features: Mandrel is Red Hat's, aligned with Quarkus, and Oracle GraalVM adds proprietary optimizations. Liberica NIK is the usual pick when you already standardise on Liberica JDK or need a musl or ARM target the upstream community build does not publish.

### LitmusChaos
**Short:** Kubernetes-native chaos engineering platform: CRD-defined fault experiments with Argo Workflow orchestration.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, platform-delivery/kubernetes-and-orchestration @2

Everything is a Kubernetes custom resource: a `ChaosExperiment` defines a fault, a `ChaosEngine` binds it to an application selected by label with a `ChaosResult` recording the outcome, and Argo Workflows chains several into a scenario. Faults are injected by a helper pod entering the target's namespaces — pod delete, container kill, CPU and memory hog, disk fill, network latency, loss and partition, node drain — and probes checked before, during and after decide whether the hypothesis held.

The consequence of the CRD shape is that a chaos experiment is a manifest under GitOps, reviewed and versioned like any other deployment. It is Kubernetes-only by design, so faults outside the cluster — a managed database failover, a cloud AZ outage — need a different tool.

### llc
**Short:** LLVM's static compiler driver: runs optimization passes over LLVM IR and emits target assembly or objects.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1

`llc` is where LLVM IR becomes machine code: it reads a textual `.ll` or bitcode `.bc` file, runs the target-independent and target-specific codegen pipeline — instruction selection, register allocation, scheduling — and emits assembly or an object file for the triple you name with `-march` and `-mcpu`. Optimization level is `-O0` through `-O3`, separate from whatever the front end already applied.

You rarely see it in a normal build, because `clang` drives the whole pipeline. It matters when you are inspecting or debugging codegen: `-print-after-all` shows the IR after each pass, and comparing `llc` output across `-mcpu` values is the direct way to check whether a loop actually vectorized. Pair it with `opt`, which runs the middle-end passes.

### LLVM opt
**Short:** LLVM's IR tool: run individual optimization passes over .ll/.bc and inspect what each transformation does.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1

`opt` is the middle end on its own: it takes LLVM IR in, runs the passes you name through the pass-manager pipeline syntax, and writes IR out, so a transformation can be studied in isolation rather than inferred from the final assembly. `-print-after-all`, `-print-changed` and the per-pass statistics and remarks flags show exactly which pass rewrote what, and `-passes='default<O2>'` reproduces the standard pipeline.

This is the tool for two jobs: understanding why an optimization did or did not fire — the missed-optimization remarks name the reason, such as a loop the vectorizer refused because of a possible aliasing dependence — and developing a custom pass against a small reproducer. For end-to-end compilation, `clang` orchestrates `opt` and `llc` for you.

### Locust
**Short:** Python load-testing tool where user behaviour is code, scaling over master/worker nodes with a live web UI.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

A test is Python: a `User` class with `@task`-decorated methods, weights choosing between them, and a wait time between tasks, so user behaviour is expressed as code with real branching and state rather than as a recorded request list. It runs users as greenlets on gevent, so one process holds thousands of them, and scaling further means starting worker processes that a master coordinates while a web UI shows live request rates, failure rates and percentiles.

Reach for it when the workload is a realistic user journey with conditional logic, or when the team is Python-first. Two limits: throughput per core is lower than a Go- or JVM-based generator, and the built-in reporting is thinner than Gatling's, so treat the live UI as the interface and export the CSV for analysis.

### Lombok
**Short:** Annotation processor that mutates the javac AST to generate getters, builders, constructors and logging fields.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @3

Lombok is not an ordinary annotation processor: it hooks into the compiler and mutates the AST in place, so `@Getter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j` and `@Data` add members to the class you wrote rather than generating a separate file. That is why the source has no visible methods yet the bytecode does, and why every IDE and any other tool reading the same AST needs its own Lombok plugin to agree with the compiler.

Weigh that dependence on unsupported compiler internals against the boilerplate saved; a JDK upgrade can require a Lombok upgrade before anything compiles. Records cover immutable carriers natively now, and `@Data` on a JPA entity is an outright bug — the generated `equals`, `hashCode` and `toString` touch every field and trigger lazy loading.

### Lombok @Value
**Short:** Lombok annotation generating an immutable class: final fields, getters, equals, hashCode and an all-args constructor.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/design-patterns-and-principles @2

### Mandrel
**Short:** Red Hat's GraalVM downstream distribution, trimmed to the native-image toolchain Quarkus builds against.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

Mandrel is Red Hat's build of GraalVM's `native-image`, deliberately trimmed: it drops Truffle and the polyglot language runtimes and ships only what a JVM application needs to be compiled ahead of time, tracking an OpenJDK release stream so it inherits the same security updates. Quarkus builds and tests against it, which is why Quarkus native builds default to a Mandrel container image.

Functionally the binaries it produces are GraalVM native images; choosing between distributions is a support and platform question. Take Mandrel if you are in the Red Hat or Quarkus ecosystem and want a supported, narrower toolchain; Oracle GraalVM if you need the proprietary AOT optimizations and profile-guided builds; upstream Community Edition when you just want to evaluate whether native image fits at all.

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

Central is the default remote repository for the JVM, addressed by the `groupId`, `artifactId` and `version` coordinate triple, which maps directly to a path holding the jar, the POM, checksums and a signature. Its two structural guarantees are what the ecosystem rests on: a published version is immutable — the same coordinate always resolves to the same bytes — and every artifact is GPG-signed by a publisher who proved ownership of the namespace.

Do not point production builds straight at it. A caching proxy such as Artifactory or Nexus removes the dependency on the public internet being reachable, gives you a record of what entered a build, and is where policy and scanning attach. Immutability also means a compromised release can only be flagged, never silently replaced.

### Maven Enforcer
**Short:** Maven plugin failing the build on banned dependencies, duplicate classes, or wrong Java/Maven versions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/static-analysis-and-linting @3

The plugin runs declarative rules during the build and fails it when one is violated: `requireMavenVersion` and `requireJavaVersion` so the build cannot run on the wrong toolchain, `bannedDependencies` to keep an artifact out entirely — a logging backend, a vulnerable version, a transitive that conflicts — `dependencyConvergence` to reject a graph where two paths demand different versions of the same library, and `requireUpperBoundDeps` to catch a resolved version older than something in the tree asked for.

These matter because Maven's nearest-wins mediation resolves conflicts silently, so a downgrade shows up at run time as `NoSuchMethodError` rather than at build time. Enforcer turns that into a failure at the point the graph changed, with the offending path in the message.

### Maven Reproducible Builds
**Short:** Maven config (fixed timestamps, stable ordering) making a rebuild produce byte-identical artifacts.
**Kind:** concept
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, security/supply-chain-and-runtime-security @2

A rebuild from the same source normally differs byte for byte because jars record file timestamps and enumerate entries in filesystem order. Setting `project.build.outputTimestamp` in the POM makes the plugins that honour it write a fixed timestamp, normalise entry ordering and drop environment-specific manifest fields, so the artifact becomes a deterministic function of its inputs; `artifact:compare` then checks a rebuild against what was published.

The value is supply-chain evidence: a third party can rebuild the source and confirm the released binary contains nothing extra, which is the only real defence against a compromised build machine. Achieving it in full is fussier than the one property suggests — the JDK version, locale, line endings and any plugin that embeds a build number all have to be pinned too.

### Maven Wrapper
**Short:** Checked-in script and properties that pin and download the exact Maven version a build requires.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

`mvnw` and `mvnw.cmd` plus `.mvn/wrapper/maven-wrapper.properties` are committed to the repository; the scripts read the declared distribution URL, download and cache that exact Maven version on first run, and then delegate to it. A contributor with no Maven installed can build, and CI cannot quietly use a different version than the one the project was tested against.

Generate and upgrade it with `mvn wrapper:wrapper -Dmaven=X.Y.Z` rather than editing the properties by hand, and set `distributionSha256Sum` so the download is verified — the wrapper fetches and executes code from a URL in your repository, which makes any change to those files security-relevant. It pins Maven, not the JDK; toolchains or a CI matrix handle that separately.

### maven-assembly-plugin
**Short:** Maven plugin that packages an application and its dependencies into a single distributable uber-jar or archive.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

It builds a distribution from a descriptor: either one of the built-in ones such as `jar-with-dependencies`, or your own XML naming the file sets, dependency sets, output directory layout and archive format — zip, tar.gz, dir — so it can produce a full install tree with `bin`, `conf` and `lib` directories, not only a fat jar.

That generality is the reason to pick it, and the fat-jar mode is its weakest use. Assembly simply overlays entries, so two dependencies contributing the same `META-INF/services` file or a signed jar's manifest end up broken, and package names collide silently. For an uber-jar use `maven-shade-plugin`, which merges service files and relocates packages; for a Spring Boot application use `spring-boot-maven-plugin`, whose nested-jar layout avoids the problem entirely.

### maven-shade-plugin
**Short:** Maven plugin building an uber-jar and relocating package names to avoid dependency conflicts at runtime.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @3

Bound to the `package` phase, it unpacks the dependencies you select and writes one jar, applying transformers on the way — appending `META-INF/services` entries instead of overwriting them, merging `reference.conf`, setting a `Main-Class`. Its distinguishing feature is relocation: a configured pattern rewrites package names inside the class files and every bytecode reference to them, so your bundled copy of a library cannot clash with a different version on the consumer's classpath.

Relocate when publishing a library; a plain application uber-jar does not need it. Understand the permanent costs: signatures break, the real dependency list disappears from the POM so scanners and dependency tools stop seeing it, stack traces name shaded packages, and any code doing reflection on the relocated package's name by string will fail.

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

You import an existing artifact — an OpenAPI or AsyncAPI document, a Postman collection, a SoapUI project, a gRPC descriptor — and Microcks turns its examples into a running mock endpoint immediately, so a consumer team can develop against a URL the day the contract is agreed. The same examples then run in the other direction as a contract test: it replays them against the real implementation and reports every response that deviates.

That symmetry is the point — one artifact drives both the mock and the conformance check, so the mock cannot drift into fiction. It covers asynchronous protocols too, publishing mock messages to Kafka or MQTT. Where it is weaker than WireMock is dynamic behaviour: it is example-driven, not a programmable stub server.

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

### Mockito with constructor injection
**Short:** Testing dependencies as constructor-injected mocks; difficulty doing so is the signal that DIP has been violated.
**Kind:** concept
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/design-patterns-and-principles @2

When collaborators arrive through the constructor, a test constructs the class under test with `new` and passes mocks directly, needing no framework, no container and no reflection into private fields. That is why the ease of doing it is diagnostic: if a test cannot supply a substitute, the class is reaching for its dependency itself — a static call, a `new` in the middle of a method, a service locator — which is the dependency inversion principle being violated in code rather than in the abstract.

Field injection produces exactly that failure and then hides it, because an annotation-driven runner reflects the mock into the private field and the test passes. Take the pain as information: making a class testable with plain constructor wiring is the same change as making its dependencies explicit.

### mockito-subclass
**Short:** Mockito's subclass mock maker, used instead of the inline maker under GraalVM native image.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

Mockito's default inline mock maker attaches a Java agent and retransforms loaded classes, which is how it mocks final classes and static methods. Neither instrumentation nor class retransformation exists in a GraalVM native image, so that maker cannot work there; `mockito-subclass` is the older strategy — generate a subclass and override methods — packaged as the artifact you swap in for native test runs.

Switching costs you the capabilities that depended on retransformation: final classes, final methods and `mockStatic` are all out, and the tests that used them must be restructured or excluded from the native run. That constraint is a reasonable argument for keeping test doubles to types you own and interfaces you control, which the subclass maker handles fine.

### Mockoon
**Short:** Desktop and CLI tool spinning up mock REST APIs from a config, so clients can be built before the server exists.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @3

A desktop app and companion CLI that starts a local HTTP server from a JSON environment file: routes with methods and paths, response bodies from inline text, a file or a templating helper that fabricates data per request, configurable status codes and latency, sequential or rule-based responses so successive calls differ. No account and no cloud service is involved, and the same environment file runs headlessly in CI or a Docker image.

Use it to unblock client development before a server exists and to reproduce error and timeout responses that a real API will not produce on demand. It is a standalone mock rather than a test framework — for stubbing inside a JVM test with request verification, WireMock is the closer fit, and Microcks derives mocks from the contract itself.

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

JPMS resolves a jar without a `module-info` as an automatic module named after the file, which is legal but blocks `jlink` and gives you a name you do not control. Moditect fixes that from the outside: it can generate a `module-info` descriptor for a third-party jar from configuration you supply and inject the compiled descriptor back into a copy of the jar, add one to your own artifact after compilation, and drive `jlink` to produce a runtime image from the result.

Use it when a dependency you cannot patch stands between you and a modular build or a minimal runtime image. It is a workaround with maintenance attached — the injected descriptor must be revisited on every dependency upgrade — so prefer a version of the library that ships its own.

### Mojo
**Short:** Modular's language with Python-superset syntax, covering high-level code and hand-tuned CPU/GPU kernels in one language.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, gpu/kernel-programming @2, gpu/gpu-portability-and-precision @3

Mojo is built on MLIR rather than on CPython, so it compiles ahead of time and exposes the machine directly: `fn` functions with required static types and compile-time metaprogramming through `parameter` values, ownership and borrow semantics for memory, first-class SIMD vector types, and constructs for tiling, vectorizing and targeting GPUs — the intent being that a kernel and the code calling it live in one language instead of Python calling into C++ or CUDA.

Python-superset compatibility is a goal it has not fully reached, so treat existing Python interop as a bridge rather than a drop-in path. It is young and tied to Modular's platform; today the safe use is performance-critical kernels within that ecosystem, with PyTorch, Triton or CUDA C++ still the defaults elsewhere.

### Molecule
**Short:** Test framework for Ansible roles: converge a role inside a container, then assert idempotence and final state.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, platform-delivery/infrastructure-as-code-and-config @2

A scenario declares a driver — usually Docker or Podman — and Molecule runs a fixed sequence against a freshly created instance: `create`, `converge` to apply the role, `idempotence` which converges a second time and fails if any task reports changed, `verify` for assertions written in Ansible or Testinfra, then `destroy`. The idempotence step is the one that earns its keep, because a role that reports changes on every run is the most common Ansible defect and nothing else catches it.

Multiple scenarios cover different distributions or variable sets. Container instances are the tradeoff: systemd, kernel modules and networking behave differently there than on a VM, so anything depending on those needs a VM driver or a real staging host.

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

Oracle's official desktop client, and its distinguishing features are the modelling and administration halves rather than the SQL editor. It reverse-engineers a live schema into an EER diagram, lets you edit the model, and forward-engineers the ALTER script to reconcile the two; the admin side exposes users and privileges, server variables, and a performance dashboard reading `performance_schema` and `sys`.

For query work the visual explain is what you open it for: it renders the plan as a diagram with cost and row estimates per node, and a full table scan or a missing index is visible without parsing `EXPLAIN FORMAT=JSON` by eye. It is MySQL-only and can be slow on very large schemas; DBeaver or DataGrip are the multi-engine alternatives.

### Native Build Tools
**Short:** GraalVM's Maven and Gradle plugins binding native-image to a build profile and running tests in the native binary.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2, devtools/testing-and-mocking @3

The GraalVM project's official Maven and Gradle plugins, which exist so `native-image` is invoked with the right classpath, module path and arguments instead of by a hand-written exec step. They add a native compile goal or task bound to a profile, resolve reachability metadata for your dependencies automatically, let you accumulate build arguments across the build, and provide a JUnit integration that compiles the test suite into its own native image and runs it there.

That native test run is the part to insist on, since the differences between a JVM run and an AOT binary appear exactly where the closed-world analysis had to guess. Budget for it separately in CI: native compilation is measured in minutes and gigabytes of RAM, so it belongs in its own job rather than in the default build.

### Native Image Build Output report
**Short:** The summary GraalVM native-image prints per build: reachable class/method counts and image-heap breakdown.
**Kind:** api
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, observability/profiling-and-performance @3

### native-image-agent
**Short:** GraalVM agent recording reflection, proxy, resource and JNI access and writing native-image config files.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

Run the application on a normal JVM with `-agentlib:native-image-agent=config-output-dir=…` and it intercepts the reflective, proxy, resource, serialization and JNI operations as they occur, writing them out as the JSON configuration files the AOT build consumes. A merge directory lets several runs accumulate, and access filters keep test-harness and JDK-internal noise out of the result.

Its coverage equals your execution coverage, and nothing warns you about the gap: a branch the run never took produces no entry, and the native binary then throws at run time on a class that was never included. Drive it with the full integration suite, review the generated JSON rather than committing it blindly, and prefer library-supplied metadata from the reachability repository where it exists.

### native-image-configure
**Short:** GraalVM tool that merges reflection/resource trace files from the agent into native-image configuration.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1

The tracing agent emits one configuration set per run, and a realistic application needs several runs to cover its paths, so this tool merges those directories — `generate` combines trace files or existing configs into one set, deduplicating entries, and filter rules drop packages you do not want recorded. It is what makes an agent-based workflow incremental rather than a single all-or-nothing capture.

Use it when building metadata for a real application: run the agent across your integration suite in separate output directories, merge, then commit the reviewed result under `META-INF/native-image`. It only combines what the agent observed, so merging many partial runs still cannot prove completeness — the only real check is compiling the tests into a native image and running them there.

### native-maven-plugin
**Short:** GraalVM Maven plugin that runs native-image to build an ahead-of-time compiled executable.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/build-and-dependency-management @2

The Maven half of GraalVM's Native Build Tools. Its `compile-no-fork` goal, conventionally bound to a `native` profile so ordinary builds stay fast, runs `native-image` over the project's runtime classpath with the build arguments declared in the plugin configuration, and `test` compiles the JUnit suite into a native image and executes it there. It also pulls reachability metadata for known dependencies automatically.

Keep it behind a profile and give it a dedicated CI job: a native build takes minutes and several gigabytes, so putting it on the default lifecycle punishes every developer for a step that matters at release time. It needs a GraalVM JDK as the build JDK and, on Linux, the C toolchain and static libraries the linker requires.

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

`nm` lists the symbol table of an object file, static library or shared object, one line per symbol with an address and a single-letter type: `T` for a defined function in text, `U` for undefined and therefore expected from elsewhere, `D` and `B` for initialised and uninitialised data, lowercase for local rather than global. `-D` reads the dynamic table of a shared library, `-C` demangles C++ names, and `--defined-only` trims the noise.

It answers the question behind most link failures directly: does this library actually export the symbol the linker says is undefined, and is the name mangled as the caller expects — the usual culprit being a C++ declaration that needed `extern "C"`. For sections, relocations and dynamic dependencies, use `readelf` or `objdump`.

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

`objdump -d` disassembles the executable sections of an object file or binary, `-S` interleaves the original source when debug info is present, and `-h`, `-r` and `-t` dump section headers, relocations and symbols. It reads the file itself rather than a running process, so it shows exactly what the compiler and linker produced.

That is why it settles arguments no amount of reading source can: whether a call was inlined, whether a loop was unrolled or vectorized into SIMD instructions, whether a supposedly atomic operation compiled to a locked instruction or to a library call. Reach for it after a profiler has already told you which function is hot; disassembly is a poor way to find a problem and an excellent way to explain one. Compile with `-g` so the source interleaving works.

### OkHttp MockWebServer
**Short:** Embedded scriptable HTTP server that lets client tests assert on real wire requests and canned responses.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

It starts a real HTTP server on an ephemeral local port and serves responses you enqueue in order, each with a status, headers, body, optional throttling or a deliberate disconnect; after the call, `takeRequest()` returns the request the server actually received, so the test asserts on the method, path, headers and body that went over the wire.

Because it is real HTTP, the client under test uses its own configuration — interceptors, connection pool, timeouts, retry policy, serialization — which is exactly the layer that a mocked client object skips and where the bugs are. The queue model keeps it small and makes it a poor fit for many interleaved endpoints; WireMock's matcher-based stubbing handles that shape better, and MockWebServer wins on being a single test dependency with no server to configure.

### Oracle GraalVM
**Short:** Oracle's GraalVM distribution: a JDK with the Graal JIT and the native-image ahead-of-time compiler.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2

Since the 2023 licence change, Oracle GraalVM is distributed free for production use under the GraalVM Free Terms and Conditions, and it is the build that carries the optimizations kept out of Community Edition: profile-guided optimization, where an instrumented native binary records a profile that a second build compiles against, the G1 collector for native images instead of only the serial GC, and additional compiler work in the Graal JIT.

Those matter specifically for long-running native services, where PGO and a generational collector close much of the throughput gap with HotSpot. Start on Community Edition to find out whether native image suits the application at all; move here when the binary's steady-state throughput and pause behaviour are what you are tuning, or take Mandrel or Liberica NIK if you need a particular vendor's support.

### os-maven-plugin
**Short:** Maven plugin exposing OS and architecture properties so protoc and other native classifiers resolve correctly.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, devtools/compiler-toolchain-and-codegen @2

Some dependencies are native binaries published per platform and distinguished by a Maven classifier — `protoc` itself, the gRPC codegen plugin, `netty-tcp-native`. This extension detects the operating system and CPU architecture at build time, normalises them into properties such as `os.detected.classifier` (`linux-x86_64`, `osx-aarch_64`, `windows-x86_64`), and makes them available for interpolation, so one POM resolves the right binary on every developer machine and CI agent.

Declare it as a build extension rather than a plugin, since the properties must exist before dependency resolution runs. The normalisation is the value: raw `os.name` and `os.arch` differ across JDKs and platforms in ways that break naive profile activation, and Apple silicon is where a hand-rolled version usually falls over first.

### Pact
**Short:** Consumer-driven contract testing: the consumer records expectations as a pact file the provider must verify in CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/data-formats-and-api-contracts @2

The consumer's test runs against a Pact mock, and each interaction it exercises is written to a pact file describing the request it sent and the response shape it depends on. That file is published to a broker, and the provider's CI replays every interaction against the real service, setting up the required state through named provider-state hooks. The provider therefore learns it is about to break a consumer before deploying, and `can-i-deploy` turns that into a release gate.

The direction is what distinguishes it: the contract records only what consumers actually use, so a provider may change anything nobody depends on. It fits a handful of known internal consumers; a public API with unknown clients needs schema-based compatibility checking instead, and it verifies shape rather than semantics.

### perf_analyzer
**Short:** Triton's load generator: sweeps concurrency and batch size, reporting throughput, latency percentiles and queue time.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, inference/model-server @2, observability/profiling-and-performance @2

It generates inference requests against a Triton endpoint over HTTP or gRPC and sweeps a variable — concurrency with `--concurrency-range`, or request rate — reporting for each step the throughput, latency percentiles and, crucially, Triton's own server-side breakdown of queue time versus compute time per request. That split is the diagnosis: queue time dominating means you are batch- or instance-starved, compute time dominating means the model or the GPU is the limit.

Use it to choose `max_batch_size`, the dynamic-batching delay and the instance-group count in `config.pbtxt`, re-running the sweep after each change. Input generation is shape-driven by default, so for models whose cost depends on content — variable sequence lengths, an LLM — supply real input data or use GenAI-Perf instead.

### pgTAP
**Short:** xUnit-style test framework running inside PostgreSQL to assert schema, constraint and data-integrity rules.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, data-access/schema-and-migration @2

It installs as a PostgreSQL extension providing assertion functions that run inside the database and emit TAP: `has_table`, `has_column`, `col_type_is`, `has_index`, `fk_ok`, `col_is_pk`, plus `results_eq` and `throws_ok` for behaviour. A test is therefore ordinary SQL in a transaction that is rolled back afterwards, and `pg_prove` runs the files and aggregates the output.

Because it executes in the server, it can assert on things an application-level test cannot easily see: a constraint's exact definition, a trigger's effect, a row-level security policy, the plan-relevant existence of an index. Reach for it when the schema and its constraints are the product — a migration-heavy database or one with logic in functions. It is a poor fit for testing application logic, which belongs in the application's own suite.

### pint
**Short:** Cloudflare's linter for Prometheus rule files: validates config, catches broken queries and flags cardinality risks.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, observability/alerting-and-incident-response @2, observability/metrics-and-monitoring @3

Prometheus will happily load a rule file whose query is valid PromQL but wrong in context, and the failure is silent: an alert that can never fire. Pint parses rule files and checks them against a live Prometheus — that the metric names and labels a query references actually exist, that a `rate()` window is at least a few scrape intervals, that an aggregation does not drop the labels the alert's annotations use, that a recording rule's name follows the level:metric:operation convention, and that alerts carry the required annotations and labels.

Run it as `pint ci` against a pull request so a broken rule is caught at review. Its metadata checks need a reachable Prometheus, and it validates the rule rather than the alert's usefulness — thresholds and paging policy remain yours.

### pip
**Short:** Python's standard package installer, resolving and installing distributions from PyPI or a local wheel or index.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

pip resolves a requirement set and installs it into the active environment: it queries an index for the available versions of each distribution, prefers a matching wheel and falls back to building an sdist through PEP 517, then unpacks into `site-packages` and writes the metadata and console-script entry points. Since the 2020 resolver it backtracks properly, reporting a genuine conflict instead of installing an incompatible pair.

What it is not is a project manager: it does not create environments, does not produce a lockfile, and `pip freeze` captures whatever happens to be installed rather than a resolved dependency graph. For reproducible installs use a real lock — pip-tools, Poetry or uv — and in a container always install with `--no-cache-dir` from a pinned requirements file.

### pip-tools
**Short:** pip-compile/pip-sync: turns loose requirements into a fully pinned lock file and installs exactly that set.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

Two commands with one idea between them. `pip-compile` takes your loose direct requirements from `requirements.in` or `pyproject.toml`, resolves the full transitive graph once, and writes a `requirements.txt` pinning every package to an exact version, annotated with which requirement pulled it in and optionally with hashes for verified installs. `pip-sync` then makes the environment match that file exactly, uninstalling anything not listed — which is the part `pip install -r` never does.

That separation of declared intent from resolved output is the whole value, and it keeps the standard pip toolchain rather than replacing it. It is slower than uv, which implements the same workflow in Rust with `uv pip compile`, and it does not manage environments or Python versions.

### PIT
**Short:** Mutation-testing tool for Java: seeds faults into bytecode and reports which mutants the test suite fails to kill.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @3

Mutation testing asks whether the tests would notice a bug, and PIT answers it empirically: it makes small changes to the compiled bytecode — flipping a conditional boundary, negating a condition, returning a default, removing a void call — runs the tests that cover the mutated line, and records whether any test failed. A mutant that survives marks behaviour no assertion constrains. Working on bytecode with per-mutant test selection is what makes it fast enough to run in CI on a changed subset.

The mutation score is a far harder number than line coverage, which a test with no assertions maximises trivially. Expect equivalent mutants that no test could kill, and run it incrementally against the diff rather than over a whole legacy codebase.

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

Poetry keeps dependencies in `pyproject.toml` and resolves the whole graph into `poetry.lock`, which pins every transitive package with hashes, so `poetry install` reproduces an identical environment on any machine and `poetry lock` is the only step that consults the index. It also creates and manages the virtualenv, distinguishes dependency groups such as dev and docs, builds wheels and sdists, and publishes them, all from the same file.

It was the tool that made lockfiles normal in Python and remains a solid default for libraries and applications alike. The two complaints are speed — resolution can take minutes on a wide graph — and its historically non-standard configuration table; uv covers the same ground far faster if you are choosing today.

### POSIX sh
**Short:** The POSIX-standard shell language; the interpreter available in minimal container images where bash is absent.
**Kind:** spec
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1

The POSIX shell command language is the intersection every conforming shell implements: parameter expansion, command substitution, pipelines, `test`, functions and positional parameters, without bash's arrays, `[[ ]]`, `local`, process substitution or `${var,,}`. On Debian and Ubuntu `/bin/sh` is dash and on Alpine it is BusyBox ash, so a script whose shebang says `#!/bin/sh` gets that reduced language regardless of whether bash exists on the machine.

That is the practical trap: a script developed on macOS or in a Debian container where `/bin/sh` happens to behave, then failing in a distroless or Alpine image on a bashism. Either commit to POSIX and check with `shellcheck -s sh`, or declare `#!/usr/bin/env bash` and make sure the image contains it.

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

The build runs twice. An instrumented binary is executed against a representative workload and records which branches were taken, which calls were hot and which functions were never reached; the second compilation reads that profile and uses it to make decisions it could otherwise only guess — inline the hot callee, lay out the taken branch to fall through, group hot functions so the instruction cache and TLB behave, and leave cold paths unoptimized or out of line. Typical gains are in the single-digit to low-double-digit percent range on branch-heavy code.

The cost is a two-stage pipeline and a profile that must be representative and refreshed as the code changes; a stale profile can be worse than none. Sampling-based collection from production avoids the instrumented run, and GraalVM applies the same idea to native images.

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

PTX is a virtual ISA, not machine code, so something has to lower it to the SASS a specific GPU executes; `ptxas` is that assembler, and it is where register allocation, instruction scheduling and most architecture-specific optimization actually happen. `nvcc` invokes it once per `sm_XX` target listed in `-gencode`, and the same code runs at driver JIT time when only PTX is embedded.

`-Xptxas -v` is the flag worth remembering: it prints per-kernel register count, shared-memory and constant-memory use, and spill load and store bytes. Registers per thread determine occupancy, and any spill traffic means the working set exceeded the register file and is now hitting local memory. Cap it with `--maxrregcount` or `__launch_bounds__` and re-measure, since fewer registers buys occupancy at the cost of spills.

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

A Pydantic model's `__init__`, its field defaults and its validators are constructed at class-creation time, so a static checker reading the class body sees only annotations and no constructor. The plugin teaches mypy what the metaclass will build: it synthesises the `__init__` signature from the fields, so a missing required argument or a wrong type is a type error, and it understands `Field` defaults, aliases, and the strictness settings that decide whether a value is coerced.

Enable it in the mypy config for any codebase where Pydantic models are the data layer — FastAPI request and response models, settings classes — otherwise the checker silently accepts any keyword argument. Note that it reasons about the model's declaration; runtime validation of untrusted input is still Pydantic's job, not mypy's.

### pylance
**Short:** VS Code's Python language server and type checker (Pyright-based), often stricter than mypy on edge cases.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/runtime-internals-and-types @2

Pylance is the Python language server Microsoft ships with the VS Code Python extension, built on the Pyright type-checking engine and adding the editor features Pyright alone does not provide: completion informed by inferred types, inlay hints, semantic highlighting, auto-import, docstring rendering and call-signature help, with bundled type stubs for popular libraries that publish none.

Because it is Pyright underneath, it flags the same errors, and `python.analysis.typeCheckingMode` selects off, basic or strict. The gap to close deliberately: the editor's setting is local, so a strict Pylance and a lenient CI check disagree with each other. Run Pyright or mypy in CI with a checked-in configuration and match the editor to it. Pylance itself is proprietary and VS Code only; Pyright is the open-source part.

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

The plugin adds one fixture, `mocker`, which wraps `unittest.mock` with automatic cleanup: `mocker.patch("module.func")`, `mocker.patch.object`, `mocker.spy` and `mocker.MagicMock` behave as their stdlib counterparts, but every patch is undone at test teardown by the fixture's own finalizer rather than by a decorator stack or a nest of `with` blocks. Failures and early returns therefore cannot leak a patch into the next test.

The readability gain is real — patching three collaborators is three flat lines instead of three levels of indentation and three shadowed arguments. The old trap survives unchanged: patch where the name is looked up, not where it is defined, so a module that did `from x import f` needs `mocker.patch("consumer.f")`.

### pytest-xdist
**Short:** pytest plugin distributing tests across CPU cores or remote hosts with -n auto.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

`-n auto` forks one worker process per CPU and distributes tests across them, collecting results back into a single report; `--dist loadfile` or `loadgroup` keeps related tests together when they share expensive per-file or per-group setup. Because workers are separate processes, a session-scoped fixture is built once per worker rather than once per run, and `--looponfail` reruns only the failures as you edit.

The suite has to be genuinely independent for this to work, and parallelising is how you discover it is not: a shared database, a fixed port, a temp file with a constant name, or an ordering assumption turns into a flake that appears only under `-n`. Fix those rather than serialising, and note the per-worker startup cost makes it a loss on a small fast suite.

### QUIC Tracker
**Short:** Interoperability suite probing a QUIC implementation against the spec and publishing a results matrix.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, runtime-systems/io-networking-and-syscalls @2

### react-diff-viewer
**Short:** React component rendering side-by-side or inline text diffs, used to show an agent's proposed edits.
**Kind:** tech
**Lang:** js
**Roles:** devtools/version-control-and-workbench @1, llm-apps/agentic-environments @3

A React component that takes old and new strings, computes a line diff, and renders it as side-by-side or inline columns with additions and deletions highlighted, word-level changes marked within a line, optional line-folding of unchanged regions and a custom renderer hook for syntax highlighting. Everything runs in the browser with no server round trip.

It is the standard building block for a review-before-apply UI, which is why it turns up in agent tooling: an LLM proposes an edit and the user has to see exactly what would change before approving. Diffing very large files in the browser is the limit — computation and DOM size both grow with the input — so truncate or virtualise, and remember it displays a diff rather than producing a patch you can apply.

### Reactive Streams TCK
**Short:** Conformance test kit proving a custom Publisher/Subscriber obeys the Reactive Streams backpressure specification.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @2, apis-frameworks/data-formats-and-api-contracts @3

The Reactive Streams specification is a set of rules about signal ordering and demand — `onSubscribe` before any `onNext`, never more elements than `request(n)` asked for, `onComplete` and `onError` terminal and never followed by anything, no unbounded recursion between `request` and `onNext`. The TCK is the executable form of those rules: TestNG classes you subclass, supplying a factory for your `Publisher` or `Subscriber`, which then run dozens of scenarios including cancellation races and demand boundaries.

Run it against any operator or adapter you write yourself, because these rules are exactly the ones that look satisfied under a friendly consumer and break under backpressure. Passing proves protocol conformance only; it says nothing about whether your operator computes the right values.

### Reactor Test
**Short:** Project Reactor's StepVerifier and virtual time scheduler for deterministic assertions on reactive streams.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, runtime-systems/concurrency-and-async @2

`StepVerifier` turns a `Flux` or `Mono` into an assertable script: subscribe, then declare the expected sequence of signals — `expectNext`, `expectNextCount`, `expectError`, `expectComplete` — and `verify()` runs it and fails if the actual signals differ. That matters because a reactive pipeline is asynchronous and lazy, so an ordinary assertion after the call runs before anything has been emitted, and a bare `block()` throws away the ordering and error information you wanted to check.

`withVirtualTime` replaces the scheduler's clock so a `delayElements` of an hour is verified in milliseconds by advancing time explicitly, and `expectSubscription` plus `thenRequest` drives demand so backpressure behaviour is testable. `TestPublisher` supplies the other side when the source is what you are stubbing.

### readelf
**Short:** binutils CLI that dumps ELF headers, sections, symbols and dynamic linking info from a binary.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/runtime-internals-and-types @2, observability/profiling-and-performance @3

`readelf` decodes the ELF format itself: `-h` for the header, `-S` for section headers, `-l` for the program headers the loader uses, `-s` for symbol tables, `-d` for the dynamic section, and `-n` for notes including the build ID. Unlike `objdump` it is written against the ELF specification directly rather than through BFD, so it reads files that other tools reject and is the more reliable choice for a malformed or unusual binary.

The day-to-day use is diagnosing dynamic linking: `-d` lists the `NEEDED` shared libraries and the `RPATH` or `RUNPATH` that decide where they are looked for, which is what a container that runs locally and fails in a distroless image usually comes down to. For actual disassembly use `objdump -d`.

### Recaf
**Short:** GUI decompiler and bytecode editor for inspecting and patching compiled JVM classes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/version-control-and-workbench @3

A GUI workbench for compiled JVM classes: it opens a jar, decompiles with a choice of engines, and — the part that distinguishes it from a plain decompiler — lets you edit and save the result, either by recompiling the decompiled source against the rest of the jar or by editing the bytecode instructions directly, with a verifier catching stack and frame errors before the class is written back.

Reach for it to patch a binary you cannot rebuild: a hardcoded endpoint in a vendor jar, a check in a legacy artifact whose source is lost, or an obfuscated class you are studying. Treat any patched jar as unsupported and unreproducible, and note that editing signed jars invalidates the signature.

### redos-detector
**Short:** Analyzes a regex for catastrophic backtracking, returning either a safety proof or a concrete attack string.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/text-encoding-and-regex @2, security/supply-chain-and-runtime-security @3

Catastrophic backtracking happens when a regex can match the same input along more than one path, so a non-matching suffix forces the engine to try an exponential number of them. A detector reasons about the pattern's automaton rather than timing it: it searches for that ambiguity — nested or adjacent quantifiers over overlapping character sets — and returns either a proof that no super-linear path exists or a concrete input string that triggers one.

A proof is stronger than a benchmark, which only tells you the inputs you tried were fast. Wire it into CI for any pattern applied to user input. The structural fixes are the same ones it points at: anchor the pattern, make quantifiers possessive or atomic, bound the input length, or use a linear-time engine such as RE2.

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

The DSL reads as `given().header(...).body(...).when().post("/orders").then().statusCode(201).body("id", notNullValue())`, and the assertions run over the parsed response using GPath expressions for JSON and XML, so you address a nested field or filter a list without deserializing into a class first. It handles the surrounding mechanics too — auth schemes, multipart, cookies, request and response logging on failure, and JSON Schema validation of the whole body.

It is black-box: the service must be running, which makes it right for testing a deployed environment or a component test against a container, and wrong for a fast unit-level check where Spring's `MockMvc` or `WebTestClient` exercise the same controllers in-process. Keep GPath expressions shallow, since deep ones break on any response reshaping.

### Revapi
**Short:** API compatibility checker that fails the build on binary or source breaks, including generic descriptor changes.
**Kind:** tech
**Lang:** java
**Roles:** devtools/static-analysis-and-linting @1, devtools/build-and-dependency-management @2

Revapi builds a model of each API version from the bytecode and compares them, and its distinguishing feature is depth: beyond added and removed members it inspects generic signatures, type parameter bounds, annotation changes, thrown checked exceptions and default methods, classifying each difference by whether it breaks binary compatibility, source compatibility or the semantic contract. Findings are configurable per difference code, and an ignore file records the ones you have consciously accepted.

As a Maven or Gradle plugin it fails the build on an unaccepted break, which is how a library's semantic-versioning promise becomes enforceable. Compared with japicmp it catches more and is correspondingly noisier to configure, so scope it to your published packages and treat the accepted-differences file as part of the API review.

### ruff
**Short:** Rust-written Python linter and formatter, 10-100x faster than flake8; one tool for lint rules and formatting.
**Kind:** tech
**Lang:** python
**Roles:** devtools/static-analysis-and-linting @1

It is a single compiled binary that reimplements the rules of flake8 and a long list of its plugins, along with isort, pyupgrade and pydocstyle checks, plus a formatter that matches Black's style. Many rules carry autofixes, so a check run can rewrite the code, and it is fast enough to run on every save and over the whole repository in a pre-commit hook rather than only on changed files.

Reach for it to collapse a stack of lint and format tools into one configured in pyproject.toml. Be clear about what it is not: it does not do type inference, so it cannot tell you a call violates a Protocol or that a variable is the wrong type. It complements mypy or pyright rather than replacing them.

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

A rule is written in the syntax of the target language with metavariables standing in for subexpressions, so `exec($CMD)` matches that call however it is formatted or commented; matching happens on the parsed AST, not on text, and rules compose with `pattern-inside`, `pattern-not` and metavariable comparisons. Beyond syntactic matching it does intraprocedural taint tracking from declared sources to sinks, and it supports around thirty languages with a large community rule registry.

That low authoring cost is the point: a team can encode its own rule — never call this deprecated helper, never construct SQL by concatenation, always pass a timeout — in minutes, which is not true of CodeQL. The tradeoff is depth, since cross-function and cross-file dataflow is where CodeQL's whole-program database wins.

### shellcheck
**Short:** Static linter for shell scripts catching quoting, word-splitting and error-handling bugs before they reach CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1

It parses a script rather than grepping it, then reports each finding with a stable SC number and an explanation of the failure mode: an unquoted expansion that will word-split on a path containing a space, `$?` checked after the wrong command, a pipeline whose exit status hides a failing middle stage, `[ ]` used where the test needs `[[ ]]`, a bashism in a script whose shebang says `#!/bin/sh`. It follows the shebang, so the dialect it checks is the one the script declares.

Run it in pre-commit and CI on every script. Shell fails silently and keeps going by default, so these are exactly the bugs that surface at 3am in a deploy script rather than during review. Suppress a finding with a `# shellcheck disable=SCxxxx` comment carrying a reason.

### shfmt
**Short:** Formatter for shell scripts, enforcing consistent indentation and style across bash/POSIX sh in CI.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1

A parser-based formatter for shell: it reparses the script and prints it canonically, so indentation, `then` and `do` placement, spacing around redirections and case-statement layout are decided by flags rather than by argument. `-i` sets indent width, `-bn` puts binary operators at line starts, `-ci` indents switch cases, `-s` simplifies redundant syntax, and `-d` prints a diff for a CI check while `-w` rewrites in place.

It understands bash, POSIX sh and mksh dialects and will refuse to format a file it cannot parse — which doubles as a syntax check. Pair it with `shellcheck`: one settles the layout arguments, the other finds the bugs, and neither substitutes for the other.

### SonarCloud
**Short:** Hosted SAST and code-quality gate on pull requests, detecting SQL injection, XSS and insecure patterns.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, security/supply-chain-and-runtime-security @2, platform-delivery/ci-cd-and-release @3

The hosted form of the SonarQube analysis platform, now branded SonarQube Cloud. A CI job runs the scanner and uploads findings, and the service decorates the pull request in place — inline comments on new issues plus a pass/fail quality gate as a status check — so the analysis lands where the review is happening rather than in a dashboard nobody opens. It mixes lint-style maintainability rules with taint analysis that follows untrusted input to a sink, which is how it reports injection and XSS rather than only style.

Configure the gate on new code only. Applied to a legacy codebase in full it produces a backlog nobody will burn down and gets muted in a week; scoped to the diff it ratchets quality up with no cleanup project.

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

Adding the dependency splits the application across two class loaders: unchanging jar dependencies stay in a base loader, your own classes go in a restart loader, and when the IDE writes new class files DevTools discards and rebuilds only the second one. A context restart therefore takes a fraction of a cold boot. It also flips the development-hostile defaults — template and static-resource caching off, `spring.thymeleaf.cache=false` and friends — and runs a LiveReload server so a browser refreshes itself.

It disables itself when the application is started from a fully packaged jar, and the dependency is marked optional so it is not transitive, but keep it in the `developmentOnly` or provided scope anyway. A restart is not a reload: schema or bean changes still need a real one.

### Spring Initializr
**Short:** Project generator at start.spring.io producing a build file and skeleton with the right Spring Boot starters.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, apis-frameworks/dependency-injection-and-config @3

The service behind start.spring.io generates a project skeleton from four choices — build tool, language, Spring Boot version and a set of starters — and the version you pick determines everything else, because the generated build imports the matching `spring-boot-dependencies` BOM and every starter is then declared without a version. That is the real output: a dependency set already proven to work together, rather than a folder of files.

It is also an API, which is why the same generator sits inside IntelliJ IDEA, VS Code and `spring init` on the CLI, and why an organisation can host its own instance with internal starters and defaults. Generate once at the start; upgrading later is a matter of changing the parent or BOM version, not regenerating the project.

### Spring MockMvc
**Short:** Exercises Spring MVC controllers, filters and validation through the full dispatch chain without binding a port.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @3

MockMvc builds the `DispatcherServlet` and the surrounding chain in-process against a mock servlet API, so a request goes through URL mapping, argument resolvers, validation, message converters, interceptors, filters, the controller, and then exception handling and view or body serialization — everything except a socket. That is what distinguishes it from calling the controller method directly, which tests none of the machinery where the bugs actually are.

It runs as a slice with `@WebMvcTest`, which loads only the web layer and expects services to be mocked, or over a full context. The mock servlet container is the limit: it is single-threaded and synchronous, so async dispatch needs explicit handling, and anything depending on real HTTP — connection behaviour, a filter registered by the container — needs `WebTestClient` or a running port.

### spring-batch-test
**Short:** Spring Batch test support: JobLauncherTestUtils to run jobs/steps and listeners that supply step scope in tests.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/aop-middleware-and-scheduling @2

Batch code is awkward to test because a step's behaviour depends on the framework's transaction and chunk boundaries and on step-scoped beans that only exist while a step runs. This module supplies both halves: `JobLauncherTestUtils` launches a whole job or a single named step with the `JobParameters` you build and returns the `JobExecution` for assertions on status, exit code and read, write and skip counts; `JobRepositoryTestUtils` clears repository state between tests.

The piece people miss is `StepScopeTestExecutionListener`, which supplies a step context so a bean using `@Value("#{jobParameters['date']}")` resolves outside a real step. Test steps individually first — a whole-job test that fails tells you very little about which chunk misbehaved.

### spring-boot-autoconfigure-processor
**Short:** Annotation processor generating auto-configuration metadata so the IDE can autocomplete Boot properties.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, apis-frameworks/dependency-injection-and-config @2

An annotation processor that runs while your auto-configuration is compiled and writes `META-INF/spring-autoconfigure-metadata.properties`, recording each auto-configuration's conditions. Boot reads that file at startup to reject classes whose `@ConditionalOnClass` cannot possibly match, before loading them — which measurably cuts startup time by avoiding class loading for the majority of auto-configurations that do not apply.

It is one of a pair, and the pair is easy to confuse: this one produces the filtering metadata for auto-configuration authors, while `spring-boot-configuration-processor` reads `@ConfigurationProperties` classes and produces `spring-configuration-metadata.json`, which is what gives an IDE completion and documentation for `application.yml` keys. A starter library should declare both, as optional dependencies so they do not leak to consumers.

### spring-boot-maven-plugin
**Short:** Maven plugin that repackages an executable fat jar, runs the app, builds OCI images and drives Spring AOT processing.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, platform-delivery/container-and-image @3, devtools/compiler-toolchain-and-codegen @3

Its `repackage` goal rewrites the plain jar produced by `maven-jar-plugin` into Boot's executable layout: your classes under `BOOT-INF/classes`, untouched dependency jars under `BOOT-INF/lib`, and a launcher plus a nested-jar class loader in the root, with the original jar kept as `.jar.original`. Nesting rather than flattening is why Boot avoids the file-collision and signature problems a shaded uber-jar has.

The other goals matter as much: `run` starts the application in the build's context, `build-image` produces an OCI image with Cloud Native Buildpacks and no Dockerfile, and `process-aot` runs Spring's ahead-of-time processing for a GraalVM native build. Enable layered jars so a container rebuild only replaces the application layer, not the dependency layer.

### spring-boot-test
**Short:** Spring Boot's core test support: @SpringBootTest, test slices, TestRestTemplate and test auto-config.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

The starter's centrepiece is `@SpringBootTest`, which finds your `@SpringBootApplication` and boots the full context, optionally on a real port with `webEnvironment = RANDOM_PORT` and a `TestRestTemplate` to call it. Around that sit the test slices — `@WebMvcTest`, `@DataJpaTest`, `@JsonTest`, `@RestClientTest` — each loading only the auto-configuration for one layer, which is far faster and forces the dependencies you have to mock into the open.

The framework caches a context per unique configuration and reuses it across test classes, so context startup is paid once — which is exactly why `@DirtiesContext` and ad-hoc property overrides are expensive: each distinct combination is another cached context. Prefer `@MockitoBean` and slices over dirtying and reloading.

### spring-integration-test
**Short:** Test support for Spring Integration: mock message sources, intercept channels, assert on messages in a flow.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1

Testing a message flow means controlling both ends of it, and this module provides the handles. `MockIntegration.mockMessageSource` substitutes an inbound adapter with canned messages so a flow can be driven without a real broker or filesystem, `mockMessageHandler` replaces an outbound handler and captures what reached it, and `MockIntegrationContext` installs those substitutions into a running context and rolls them back after the test. `@SpringIntegrationTest` with `noAutoStartup` patterns keeps the real adapters from firing while the mocks are in place.

A `QueueChannel` plus a bounded `receive()` is the simplest assertion point for an asynchronous flow. Test channel by channel rather than end to end: a whole-flow test that times out rarely tells you which handler swallowed the message.

### spring-modulith-starter-test
**Short:** Spring Modulith test starter: verify module boundaries and run bootstrap tests slicing one application module.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/static-analysis-and-linting @3

It brings two capabilities. `ApplicationModules.of(App.class).verify()` runs as an ordinary test and fails when code crosses a module boundary the model forbids — a package outside the module's exposed API being referenced, or a cycle between modules — so the architecture is enforced by the build instead of by review. `@ApplicationModuleTest` boots only one module's beans, with the neighbours it depends on either excluded or stubbed, which keeps a module test genuinely scoped.

The third piece is `Scenario`, which publishes an event and waits for the expected downstream state, the right shape for modules that communicate by events rather than calls. Verification is structural and static, so reflective or string-based wiring across a boundary is invisible to it.

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

The module's most consequential feature is not an API but the `TestContext` framework's context cache: an `ApplicationContext` is keyed by its full configuration — classes, active profiles, property sources, bean overrides — and reused across every test class that asks for the same one, so a suite of two hundred tests boots a handful of contexts rather than two hundred. Anything that makes a configuration unique, and `@DirtiesContext` above all, costs a fresh boot.

Around it sit MockMvc and `WebTestClient` for the web layer, `@Sql` for per-test data setup, `@Transactional` on a test method to roll back automatically at the end, `@MockitoBean` to replace a bean in the context, and `@DynamicPropertySource`, which is how a Testcontainers-assigned port reaches the context's properties.

### starlette.testclient.TestClient
**Short:** Synchronous in-process test client that drives an ASGI app end to end without starting a server.
**Kind:** api
**Lang:** python
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

### stress-ng
**Short:** Linux load generator stressing CPU, memory, I/O and other subsystems; used for chaos and capacity experiments.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1, runtime-systems/memory-processes-and-os @2

It exposes hundreds of named stressors, each hammering one part of the system: `--cpu` with a selectable arithmetic method, `--vm` for anonymous memory pressure, `--io` and `--hdd` for the page cache and block layer, plus stressors for context switching, futexes, timers, sockets and filesystem metadata. Each is bounded by `--timeout`, sized with `--cpu-load` or a byte figure, and can be pinned to specific CPUs.

It has two distinct uses. As a chaos tool it manufactures the resource starvation that reveals whether your timeouts, health checks and autoscaling behave — a node under memory pressure is a far more common incident than a node that vanished. As a benchmark its `bogo-ops` figures are only comparable between runs of the same version on the same hardware, never as an absolute score.

### Surefire plugin
**Short:** The Maven plugin that discovers and runs unit tests during the test phase and writes the reports the build gate reads.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, devtools/build-and-dependency-management @2

Bound to the `test` phase, Surefire scans the test classpath for classes matching its includes — `*Test`, `Test*`, `*Tests`, `*TestCase` by default — runs them in a forked JVM, and writes XML and text reports under `target/surefire-reports` that CI reads to render results. `forkCount` and `reuseForks` control parallelism and isolation, `argLine` is where the JaCoCo agent and JVM flags are injected, and by default any test failure fails the build.

The pairing to know is Failsafe, its sibling bound to `integration-test` and `verify`: Failsafe runs `*IT` classes and, crucially, does not fail the build immediately, so `post-integration-test` can still tear down the environment. Putting slow integration tests in Surefire is the usual reason a build leaks containers.

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

Terratest is a Go library, not a framework: a test is an ordinary `go test` function that calls `terraform.InitAndApply`, reads outputs, then makes real assertions against the provisioned infrastructure — an HTTP request to the load balancer, an SSH command on the instance, an API call checking a bucket's encryption — with `defer terraform.Destroy` guaranteeing teardown. Retry helpers with timeouts handle the fact that cloud resources become ready asynchronously.

That it deploys for real is both the value and the cost: it validates what the provider actually created rather than what the plan said, and it charges money and takes tens of minutes. Use unique namespaced resource names so parallel runs cannot collide, and reserve it for modules worth that expense — `tflint`, `tfsec` and plan-level policy checks are the fast gates.

### Testcontainers
**Short:** Starts real dependencies (PostgreSQL, Kafka, Redis) in throwaway Docker containers for integration tests.
**Kind:** tech
**Lang:** java, python, go
**Roles:** devtools/testing-and-mocking @1, platform-delivery/container-and-image @3

A test declares the dependency it needs — `PostgreSQLContainer`, `KafkaContainer`, or `GenericContainer` for anything else — and the library starts that image, waits on a real readiness signal, hands the test a generated JDBC URL or bootstrap-servers string, and tears the container down afterwards. The test therefore runs against the actual engine, so migrations, dialect quirks, isolation-level behavior and serializer wiring are exercised rather than approximated by H2 or an embedded broker.

The cost is startup time and a Docker daemon in CI; container reuse and singleton containers exist to amortize it across a class or a suite. Reach for it for integration tests where fidelity is the point, and keep unit tests container-free.

### Testcontainers Kafka
**Short:** Testcontainers module that boots a real Kafka broker in Docker for integration tests instead of a mock.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, data-movement/event-streaming-and-processing @3, platform-delivery/container-and-image @3

The module starts a real broker — the Confluent image or Apache's, in recent versions KRaft-based with no ZooKeeper container — and hands the test a `getBootstrapServers()` string with the advertised listeners already configured so both the host and other containers on the network can reach it. That last part is the fiddly bit it removes: hand-rolled Kafka containers usually fail on advertised-listener configuration.

Against a real broker you actually exercise consumer group rebalancing, offset commits, partition assignment, serializer and schema-registry wiring and transaction semantics — none of which an embedded broker or a mocked producer reproduces faithfully. The cost is startup time measured in seconds, so share one container across the test class and create a fresh topic per test rather than a fresh broker.

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

`terraform validate` only checks that the configuration is syntactically valid and internally consistent; it will happily accept an instance type that does not exist, an AMI from the wrong region, or an IAM policy document with an invalid shape. TFLint adds provider-aware rules for AWS, Azure and Google that catch those before `plan` reaches the API, plus generic rules for unused declarations, missing variable types, deprecated syntax, and naming conventions you configure.

Run it in pre-commit and CI, with the relevant provider plugin enabled in `.tflint.hcl` — without a plugin you get only the generic rules. It is a linter, not a security scanner and not a policy engine: `tfsec` or Trivy covers misconfiguration, and OPA or Sentinel covers organisational policy on the plan.

### TLA+, Coq
**Short:** Formal methods tools: TLA+ model-checks a concurrent specification, Coq proves theorems interactively.
**Kind:** tech
**Lang:** *
**Roles:** devtools/static-analysis-and-linting @1, runtime-systems/collections-and-algorithms @3

They attack correctness from opposite ends. TLA+ describes a system as a state machine over variables, and the TLC model checker exhaustively explores every reachable state of a bounded instance, reporting a concrete counterexample trace when a safety invariant or liveness property fails — which is why it finds the six-step interleaving no reviewer imagined. Coq is an interactive proof assistant: you state a theorem in a dependently typed logic and build a proof term with tactics, and the kernel checks it, so the result holds for all inputs rather than for a bounded model.

Use TLA+ for concurrent and distributed protocols, where the bugs are interleavings; use Coq or Lean where an algorithm or a compiler pass must be proved. Both specify a model, so neither guarantees the implementation matches it.

### tools.jackson:jackson-bom
**Short:** Jackson's bill of materials: import it as a platform so every Jackson module resolves to one aligned version.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1, apis-frameworks/data-formats-and-api-contracts @2

Jackson ships as a family — `jackson-core`, `jackson-databind`, `jackson-annotations`, the dataformat and datatype modules — and they are only compatible when their versions line up. The BOM is a POM of type `pom` listing every module at one release; import it as a platform in Gradle or under `dependencyManagement` with scope `import` in Maven, then declare Jackson artifacts with no version at all and the BOM decides them together.

That is the fix for the classic failure where a transitive dependency drags in an older `jackson-databind` alongside a newer `jackson-core` and the result is a `NoSuchMethodError` at run time, not a build error. The `tools.jackson` group is Jackson 3's coordinate; Jackson 2 remains under `com.fasterxml.jackson`, and the two do not mix.

### tree-sitter
**Short:** Incremental parser with grammars for many languages; gives an AST for editor tooling and code-aware chunking.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, search-retrieval/rag-and-document-processing @2, devtools/static-analysis-and-linting @3

Tree-sitter is a GLR parser generator plus a runtime library whose defining property is incremental reparsing: given the previous tree and the edit, it reuses the untouched subtrees and reparses only the affected region, in the low milliseconds even on a large file. It is also error-tolerant, producing a usable tree with error nodes for code that is mid-edit and syntactically broken, which is the normal state of a file in an editor. Grammars are separate C libraries with bindings for Rust, Python, JavaScript, Go and more, and a declarative S-expression query language extracts nodes by pattern.

That combination made it the standard for editor syntax highlighting, structural selection and code navigation, and for splitting source into semantically whole chunks — a function or class rather than a fixed token window — when indexing code for retrieval.

### trunk-based development
**Short:** Branching model where everyone merges small changes to one trunk daily, keeping releases continuous and cheap.
**Kind:** concept
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, platform-delivery/ci-cd-and-release @2

Everyone commits to a single shared branch at least daily, and the branches that exist are short-lived — hours to a day or two — so merge conflicts stay small and integration happens continuously instead of at a painful merge at the end. Because trunk must always be releasable, unfinished work ships disabled behind a feature flag rather than waiting on a branch, which decouples deploying code from releasing behaviour.

The prerequisites are what make or break it: a fast reliable test suite, small reviewable changes, and branch protection so trunk cannot go red. Without them, continuous integration into one branch just distributes the breakage. Release branches are cut from trunk for versioned products and only cherry-picked into, never developed on — that is the difference from GitFlow, whose long-lived branches this model exists to avoid.

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

Twine exists because `setup.py upload` sent your credentials over the wire during a build step and could only ship what that command had just produced. Twine separates the two: you build artifacts however you like — `python -m build`, hatch, Poetry — then `twine check dist/*` validates the metadata and README rendering, and `twine upload dist/*` transfers the already-built files over HTTPS with the credentials read from `~/.pypirc`, an environment variable, or a keyring.

The modern practice is not to hold a token at all: PyPI's trusted publishing lets a GitHub Actions workflow exchange an OIDC identity for a short-lived upload token. Always upload to TestPyPI first, because a filename on PyPI is permanent — a released version can be yanked but never replaced.

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

The load generator bundled with Valkey, and command-compatible with `redis-benchmark` so existing invocations carry over: `-c` sets client connections, `-n` total requests, `-t` the command mix, `-P` the pipeline depth, `-d` the value size, and `-r` randomises keys across a keyspace so the run is not hammering one key. Output is requests per second plus a latency distribution per command type.

Read the numbers carefully. Pipelining inflates throughput enormously and is only meaningful if your client actually pipelines; a small `-r` keyspace measures cache-resident best case; and `--cluster` mode is required for a real cluster or you are measuring one shard. It exercises the server, so a slow result often reflects the network or the client host rather than Valkey.

### vcrpy
**Short:** Records real HTTP interactions to cassette files and replays them, making API-dependent tests deterministic.
**Kind:** tech
**Lang:** python
**Roles:** devtools/testing-and-mocking @1

On the first run it lets the real HTTP request through and records the request and response — method, URI, headers, body, status — into a YAML or JSON cassette file; on every later run the request is matched against the cassette and the stored response is replayed with no network involved. Record modes control the policy: `once` records if the cassette is absent, `none` fails on any unmatched request, `new_episodes` appends. It hooks the transport layer of `requests`, `httpx`, `urllib3` and `aiohttp`.

The appeal is fidelity you did not have to write by hand — a real API's exact response shape. The costs are the familiar ones: cassettes go stale silently as the upstream API evolves, and they capture whatever secrets were in the headers, so configure `filter_headers` before the first recording.

### Vegeta
**Short:** Go CLI and library for constant-rate HTTP load testing, reporting latency percentiles from a target list.
**Kind:** tech
**Lang:** *
**Roles:** devtools/testing-and-mocking @1

Vegeta drives a constant request rate rather than a fixed number of concurrent users — `vegeta attack -rate=500/s -duration=30s` opens as many connections as it takes to sustain that rate — which is the correct model for a service whose input is arrivals per second, and it avoids coordinated omission, where a thread-per-user tool slows its own request rate when the server slows and hides the latency it caused. Targets come from a plain text list on stdin, results are a binary stream, and `vegeta report` or `plot` turns them into percentiles or an HTML chart.

It is a single Go binary and also importable as a library, so it drops into a pipeline trivially. It is HTTP-only and deliberately scriptless — no user journeys, no conditional logic — so anything stateful belongs in k6 or Gatling.

### Velocity/StringTemplate
**Short:** Java template engines used to emit generated source files from annotation processors and codegen tools.
**Kind:** tech
**Lang:** java
**Roles:** devtools/compiler-toolchain-and-codegen @1, runtime-systems/text-encoding-and-regex @3

Both fill a template with values to produce text, and the difference in philosophy matters for code generation. Velocity's VTL allows conditionals, loops and method calls on the objects you pass, which is flexible and lets presentation logic accumulate in the template. StringTemplate deliberately enforces strict model-view separation — no side effects, no arbitrary expressions, only attribute references, conditionals on presence and iteration — which is why ANTLR uses it to emit parsers in a dozen target languages from one set of templates.

Use a template engine when the generated output is mostly fixed text with holes, such as a configuration file or a boilerplate class. For generating Java or Kotlin, JavaPoet and KotlinPoet model the language instead, so imports, generics and escaping are handled rather than hand-written.

### versions-maven-plugin
**Short:** Maven plugin that reports and bulk-updates dependency and plugin versions across a multi-module project.
**Kind:** tech
**Lang:** java
**Roles:** devtools/build-and-dependency-management @1

It compares each declared dependency, plugin, parent and property-driven version against what the repositories offer, and reports or applies updates: `display-dependency-updates` and `display-plugin-updates` list what is behind, `use-latest-releases` and `update-properties` rewrite the POMs in place across a multi-module reactor, and `set` changes the project version everywhere at once for a release. `revert` restores the backup POMs when a bulk update turns out badly.

Control it with version rules — an XML ruleset excluding alphas, release candidates and vendor-suffixed versions — because without one it will happily propose a milestone build. It is a manual, whole-repository sweep; Renovate or Dependabot do the same job continuously as pull requests that CI can actually judge one at a time.

### virtualenv
**Short:** Tool that creates isolated Python environments, each with its own interpreter link and site-packages directory.
**Kind:** tech
**Lang:** python
**Roles:** devtools/build-and-dependency-management @1

The mechanism is deliberately small: a directory holding a link to a base interpreter and its
own `site-packages`, activated by putting its `bin` directory first on the path. Nothing is
containerised and nothing is virtualised, which is why creation is fast and why an environment
still depends on the system interpreter it was built from.

It predates the standard-library `venv` and remains faster and more configurable, including
support for interpreters other than the one running it. Reach for it when the isolation you
need is Python-level and the rest of the dependency graph is wheels. It says nothing about
system libraries or drivers, so a reproducible build still needs a lockfile and, for anything
with native dependencies, an image.

### Visual Studio Code
**Short:** Microsoft's editor; also a first-class MCP client and coding-agent host through GitHub Copilot chat.
**Kind:** tech
**Lang:** *
**Roles:** devtools/version-control-and-workbench @1, llm-apps/agentic-environments @2, llm-apps/tool-use-and-mcp @3

An Electron editor whose architecture is the reason for its reach: language intelligence is not built in but supplied by external servers speaking the Language Server Protocol, and debugging by the Debug Adapter Protocol, so a language gets first-class support by implementing a protocol rather than by anyone writing an editor plugin. Extensions run in a separate host process, and Remote Development runs that host inside a container, a WSL distribution or over SSH while the UI stays local.

It is also where much of the current agentic tooling lands first: Copilot Chat hosts coding agents in the editor, and it acts as an MCP client so an agent can reach external tools. For deep Java or Kotlin refactoring, a full IDE's resolved project model still does more than a language server can.

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

A browser-based client for hand-testing a WebSocket endpoint: enter a `ws://` or `wss://` URL and any headers or subprotocol, connect, then send frames — saved as reusable messages — and watch the full bidirectional traffic log with timestamps and direction. Because the connection originates from the browser, the same origin, cookie and TLS rules your real client faces apply, so it also exposes handshake failures a command-line tool would not reproduce.

Use it for exploration and for reproducing a reported problem interactively: confirming the upgrade handshake succeeds, checking what the server pushes after a subscribe frame, watching ping and pong behaviour. It is manual, so anything repeatable belongs in an automated test — a `WebSocketClient` in the service's own suite, or `websocat` in a script.

### WebTestClient
**Short:** Spring's non-blocking test client for exercising WebFlux (or MVC) endpoints end to end with fluent response assertions.
**Kind:** tech
**Lang:** java
**Roles:** devtools/testing-and-mocking @1, apis-frameworks/web-framework-and-http-client @2

The reactive counterpart to MockMvc, and the more flexible of the two: bound to a controller, a router function, an application context or a real base URL, so the same fluent assertions run either against a mock request-response cycle with no server or over real HTTP against a running port — including a WebFlux application, which MockMvc's servlet-based machinery cannot exercise at all. Since Spring Framework 6.2 it can also drive a Spring MVC application in mock mode.

Assertions chain from `exchange()`: `expectStatus()`, `expectHeader()`, `expectBody(Foo.class)` with a value assertion, or `expectBodyList` and JSONPath. For a streaming endpoint, `returnResult().getResponseBody()` gives you the `Flux` so `StepVerifier` can assert the sequence rather than a collected list.

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

It reads whitespace- or newline-separated items from stdin and appends them as arguments to a command, batching as many as fit within the system's argument-length limit and re-invoking as needed — which is precisely what a naive `cmd $(find ...)` cannot do, since that fails outright with an argument list too long. `-P N` runs the batches concurrently, turning a serial loop into N-way parallelism with no job-control code.

The correctness rule is one pairing: `find -print0` with `xargs -0`, using NUL as the separator, because the default splits on whitespace and mangles any filename containing a space or a quote. Also use `-r` so an empty input does not run the command with no arguments, and `-n` to control how many items go into each invocation.

### Xcode
**Short:** Apple's toolchain and IDE, including the Metal compiler and MPSGraph for GPU compute on Apple silicon.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1, devtools/version-control-and-workbench @2, gpu/gpu-portability-and-precision @2

Apple's toolchain, and the parts that matter beyond the IDE are the compilers and profilers it bundles. `clang` and the Swift compiler build for every Apple target, `xcodebuild` drives the same builds headlessly in CI, and Instruments profiles CPU, allocations, leaks and energy on device. For GPU work it carries the Metal shader compiler, which compiles `.metal` sources to a metallib, the Metal debugger for capturing and stepping a frame or compute dispatch, and the MPS and MPSGraph libraries of optimized kernels that back accelerated machine-learning on Apple silicon.

It is macOS-only and large, and command-line-only work needs just the Command Line Tools package. Signing and provisioning remain the part that most often blocks an automated build rather than compilation itself.

### yacc
**Short:** Classic parser generator that turns an LALR grammar specification into parser source code.
**Kind:** tech
**Lang:** *
**Roles:** devtools/compiler-toolchain-and-codegen @1

yacc takes a grammar written as BNF-like productions with fragments of C attached to each rule, and emits the source of a bottom-up LALR(1) parser — the tables, the state machine, and the driver loop — traditionally paired with lex, which generates the tokenizer it pulls from. Instead of hand-writing a parser you declare the language's structure and its operator precedence and let the tool derive the automaton.

Its vocabulary outlived it: shift-reduce and reduce-reduce conflicts, precedence declarations to resolve the dangling else, and the whole LALR mental model come from here, and they still describe what modern generators do. New work normally uses a descendant such as bison or a parser-combinator library, but reading a conflict report is the same skill.
