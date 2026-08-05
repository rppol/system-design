# Runtime & OS — technology bank

<!-- tech-bank tier: runtime-systems -->

The 454 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Runtime & OS** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### /proc/<pid>/sched
**Short:** Linux procfs file exposing live per-process scheduler stats such as vruntime and context-switch counts.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @3

### @Async
**Short:** Spring annotation that runs the annotated method on an executor, returning immediately or via a CompletableFuture.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/aop-middleware-and-scheduling @2, data-movement/task-queue-and-jobs @3

### @Contended
**Short:** JDK annotation that pads a field or class onto its own cache line to eliminate false sharing between threads.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/concurrency-and-async @2

### abc
**Short:** Python stdlib module for abstract base classes; @abstractmethod enforces hook contracts at instantiation.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### abc.ABCMeta
**Short:** Python metaclass behind abstract base classes: abstract methods, virtual subclass registration and subclasshook.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### AbstractMap
**Short:** JDK skeletal Map implementation: implement entrySet and inherit the rest instead of writing a collection from scratch.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### AbstractSet
**Short:** Java skeletal Set implementation: extend it and supply iterator and size instead of writing a collection from scratch.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### aiofiles
**Short:** Async file I/O for Python; wraps blocking file operations in a thread pool so they do not stall the event loop.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/concurrency-and-async @2

File system calls block, and there is no portable asynchronous file API to build on, so a plain open and read inside a coroutine stalls the event loop and every other task on it. This library delegates each operation to a worker thread and gives you an awaitable, which keeps the loop free; the underlying system call releases the interpreter lock while it waits, so the threads are not fighting for it.

Reach for it when a coroutine has to touch the filesystem on a request path, such as streaming an upload or reading a file per request. It is not free, since each call costs a thread handoff, so reading a few small configuration files at startup is better served by ordinary blocking calls before the loop is doing anything else.

### aioitertools
**Short:** Async counterparts of itertools for async generators; used to build streaming asyncio pipelines.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @2

Its combinators accept either a plain iterable or an async iterable and are consumed with `async for`, so `chain`, `islice`, `groupby` and `zip` keep working once a stage of a pipeline becomes a coroutine. Everything stays lazy, pulling one item at a time.

Reach for it when a pipeline reads from an async source too large to hold in memory, such as a paginated API or a database cursor. Be clear about what it does not do: composing these operators adds no concurrency, since each stage awaits the one before it, so fan-out still needs a task group. For two or three steps a hand-written async generator is shorter.

### aiomonitor
**Short:** Live introspection for a running asyncio loop over a telnet REPL: task list, ready-queue length and stack dumps.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

It starts a small server alongside your running loop and accepts a terminal connection into a REPL that has the loop in scope, where one command lists every live task with its state and another prints a chosen task's stack. It inspects task objects, so it answers the question a hung service cannot answer for itself.

Reach for it when a long-running asyncio service stops making progress and restarting would destroy the evidence. The cost is that an unauthenticated console inside your process is a remote code execution primitive, so bind it to loopback. On recent Pythons, `python -m asyncio ps` attaches to a PID with no cooperation arranged in advance.

### aiter
**Short:** Python builtin returning an async iterator from an object, the async half of the iteration protocol.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @3

### anext
**Short:** Python builtin that advances an async iterator, the await-able counterpart of next().
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @3

### anyio
**Short:** Backend-agnostic async library running on asyncio or trio; task groups, timeouts and to_thread offloading.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, devtools/testing-and-mocking @3

One API - `create_task_group`, `move_on_after` and `fail_after`, `to_thread.run_sync`, memory object streams - runs unchanged on asyncio or trio, which is what lets a library support both without duplicating its concurrency code. More importantly it brings structured concurrency to asyncio: a task group's block cannot exit until every child has finished, and a failing child cancels its siblings and propagates, so tasks cannot be silently orphaned or swallowed the way a bare `create_task` whose reference is dropped can be.

Starlette and FastAPI use it internally, which explains behaviour you will meet directly: a synchronous `def` endpoint or a synchronous dependency is run in anyio's worker thread pool, whose default limiter allows 40 concurrent threads - once blocking calls exceed that, requests queue there rather than in your code. Its bundled pytest plugin runs the same async tests on both backends.

### Apache Commons Collections 4 IteratorUtils
**Short:** Commons Collections helper producing filtering, chaining and looping iterator decorators over any source.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### Apache Commons Text
**Short:** Java text utility library: string similarity, WordUtils, escaping and StringSubstitutor templating.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

The pieces worth knowing are `StringSubstitutor`, which expands `${name}` placeholders from a map, environment or system properties; `StringEscapeUtils`, which moved here from Commons Lang and escapes HTML, XML, JSON and CSV; `WordUtils` for wrapping; and a similarity package with Levenshtein and Jaro-Winkler behind one interface.

Reach for it for config templating and text munging where a full template engine would be overkill. The trap is worth naming: the interpolating substitutor enables `script`, `url` and `dns` lookups, so expanding an untrusted template becomes remote code execution, which is CVE-2022-42889 and the reason to stay current and build substitutors with a minimal lookup set.

### Apache Pekko (or Akka) actors
**Short:** JVM actor toolkit: single-threaded actors with mailboxes and supervision hierarchies for message-passing concurrency.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

Each actor owns private state and a mailbox, and a dispatcher assigns actors to a shared thread pool so exactly one message is processed at a time per actor. That serialization is the trick: state inside an actor needs no lock because there is never a second thread in it. A parent supervises its children and decides to resume, restart or stop on a crash.

Reach for it when the domain really is many independent stateful entities exchanging messages, such as devices, sessions or trading instruments. The costs: mailboxes are unbounded by default, so a slow actor becomes a memory leak; you lose the call stack; and state is gone on restart without persistence. For plain request-response work, virtual threads are far less machinery.

### Apache Pekko (or Akka) FSM
**Short:** Actor-based finite state machine where mailbox-serialized messages drive transitions, so no locking is needed.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

The behaviour is a function from current state and incoming message to the next state plus side effects, and since the mailbox already serializes delivery, each transition runs to completion before the next message is examined. No lock and no interleaving are possible by construction, which removes the class of bug where two threads observe a half-applied transition.

Reach for it when a protocol genuinely has named states with legal and illegal transitions, such as an order lifecycle or a saga, because encoding it this way makes an illegal transition an explicit testable case rather than a missing branch. The cost is durability: state is lost on crash unless paired with event sourcing. Where durability matters more than throughput, a state column plus a transition table is simpler.

### Arena
**Short:** Java FFM API scoped allocator: allocates native MemorySegments and deterministically frees them when closed.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/runtime-internals-and-types @3

### array
**Short:** Python stdlib typed C array: compact unboxed storage for a single primitive type.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### array of ints
**Short:** A plain int array used as a compact bit set - the hand-rolled backing for Bloom filters and bitmask DP.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

Bit i lives in word `a[i >> 5]` at position `i & 31`, so setting is an OR with a shifted one and testing is an AND. Packing this way costs one bit per element where a Java `boolean[]` costs a byte, so the same information fits in far fewer cache lines. For bitmask dynamic programming a single `int` instead enumerates all subsets of up to 32 items.

Reach for it when the universe is dense and known, or when implementing something a library does not provide, such as the bit array inside a Bloom filter. The costs are all safety: no bounds checking on the bit index, an off-by-one in the shift silently corrupts a neighbour, and nothing is thread safe. `BitSet` and `EnumSet` do the same packing with a real API.

### ArrayDeque
**Short:** Resizable circular-array deque; the preferred JDK stack and queue implementation over Stack and LinkedList.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### ArrayList
**Short:** Java's resizable array list: contiguous storage, amortized O(1) append, growing by roughly 1.5x when full.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Arrays.sort
**Short:** JDK array sort: dual-pivot quicksort for primitives and a stable TimSort-style merge sort for objects.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Asymptotic Complexity Calculator
**Short:** Aid for deriving the theoretical big-O cost of an algorithm from its recurrence or loop structure.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

### Async generators
**Short:** Python coroutine functions that yield values, consumed with async for; added in PEP 525 for streaming async data.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### asyncio
**Short:** Python's standard-library async runtime: event loop, tasks, futures and primitives for high-concurrency I/O.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/io-networking-and-syscalls @3

### asyncio.create_subprocess_exec
**Short:** asyncio API that spawns an external process without blocking the loop, the safe form inside async handlers.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### asyncio.gather
**Short:** Python asyncio call that runs awaitables concurrently and collects results, optionally isolating per-task failures.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### asyncio.Lock
**Short:** Coroutine-safe mutex for asyncio; guards await-spanning critical sections where threading.Lock would deadlock.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### asyncio.set_debug
**Short:** Turns on asyncio debug mode: logs callbacks slower than 100ms and surfaces never-awaited coroutines.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

### asyncio.TaskGroup
**Short:** Structured-concurrency context manager that spawns child tasks and awaits them all, cancelling on failure.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### asyncio.wait_for
**Short:** Python coroutine wrapper that cancels an awaitable after a deadline and raises TimeoutError.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, traffic-edge/rate-limiting-and-resilience @3

### AtomicReference.compareAndSet
**Short:** Java CAS primitive for lock-free updates, used for lazy init and optimistic in-memory reservation races.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @3

### attrs
**Short:** Class-boilerplate library with validators, converters and slots; a richer, faster alternative to dataclasses.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/data-formats-and-api-contracts @2, apis-frameworks/design-patterns-and-principles @3

`@define` generates `__init__`, `__repr__`, and `__eq__` from declared attributes and adds what dataclasses do not have: per-field validators and converters that run at construction, so an invalid instance cannot exist; `slots=True` by default, which cuts memory, speeds attribute access, and makes a typo'd assignment an error instead of a new attribute; and factories, aliases, and `kw_only` for awkward signatures.

It deliberately stops at classes — no parsing, no serialization, no coercion of untrusted input (pair it with `cattrs` for that), which is the line between it and pydantic. Reach for attrs for internal domain models and value objects where you want cheap, strict, well-behaved classes; dataclasses are enough when you need none of the extras, and pydantic is the right tool at an API boundary where data arrives untyped.

### AVX-512 VNNI
**Short:** x86 instruction-set extension fusing INT8 multiply-accumulate, accelerating quantized inference on Xeon.
**Kind:** spec
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @3

The Vector Neural Network Instructions add `VPDPBUSD`, which multiplies pairs of 8-bit integers and accumulates into 32-bit lanes in one instruction. Before it the same INT8 dot product needed a three-instruction widen, multiply-add and accumulate sequence, so instructions per accumulate drop by roughly a factor of three. It arrived on Cascade Lake server parts.

You almost never write it by hand: oneDNN, OpenVINO and ONNX Runtime detect it and dispatch quantized kernels to it, so the work is quantizing the model and confirming the runtime chose the right kernel. The costs are that it does nothing for a floating-point model, that heavy AVX-512 use can lower sustained clocks, and that AMX supersedes it for large matrix multiplications on the newest Xeons.

### awk
**Short:** Pattern-action text processing language for column extraction and record-level transformation in shell pipelines.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/version-control-and-workbench @3

It reads one record at a time, by default a line, splits it on whitespace or the separator given to `-F`, and runs every pattern-action block whose pattern matches. Fields are `$1` through `$NF` and `BEGIN` and `END` blocks run before and after the stream. Because it also has associative arrays, accumulating into `sum[$1] += $2` computes a group-by over a stream of any size without sorting it.

Reach for it when the data is delimiter separated and the job is extraction or a running aggregate in a pipeline, where it is far shorter than the equivalent script and starts instantly. Its limits are sharp: it has no concept of quoted CSV fields. Past about ten lines of program, readability and speed both favour Python, and `cut` is clearer for pure column selection.

### AWS VPC Reachability Analyzer
**Short:** AWS service that traces the virtual path between two VPC resources and names the rule blocking it.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/alerting-and-incident-response @3, security/authorization-and-policy @3

It sends no packets. It reads the configuration of your network, route tables, security groups, network ACLs, interfaces, gateways and peering connections, and performs static analysis on that model between a source and destination you name. A reachable result comes back as the hop-by-hop path; an unreachable one names the specific component and rule that stops the packet.

Reach for it for the common ticket where two things cannot talk and nobody agrees whose layer is at fault, especially across transit gateways. Because it is configuration-only, it will call a path reachable when the host firewall drops the packet or no process is listening, so a clean result narrows the problem to the endpoints rather than proving health. Analyses are billed per run.

### B+Tree index
**Short:** Balanced, high-fanout tree with linked leaves; the default database index because it serves range scans in O(log n).
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-stores/key-value-and-embedded @2, data-stores/relational @3

Internal nodes hold only separator keys and child pointers, so a page a few kilobytes wide fans out to hundreds of children and a billion-row table is about four levels deep, with the upper levels resident so a lookup costs one or two real reads. Values live in the leaves, which are chained left to right, so a range scan descends once and walks sideways sequentially.

It is the default because one structure serves point lookups, ranges, prefix matches and sorted output at predictable cost. The costs are on the write side: every insert must locate and possibly split a page, so write-heavy workloads pay random I/O, which is the gap LSM trees fill. A random UUID key scatters inserts and fragments the tree. It also cannot answer substring queries.

### beartype
**Short:** Near-zero-overhead runtime type checker that enforces Python annotations at call time.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/static-analysis-and-linting @3

Decorate a function, or install its import hook over a whole package, and beartype generates a wrapper that validates the annotated types at call time, so bad data fails at the boundary it entered rather than as an inscrutable error several frames later. Its speed comes from checking a constant amount of work per call: for a `list[str]` it validates a single sampled element instead of walking the list, which keeps the overhead near a microsecond and makes leaving it enabled in production defensible.

Its limits follow from what Python can actually check at runtime. A `Protocol` parameter degrades to what `isinstance` can do — a shallow check that the attribute names exist, not that their signatures match — and nothing it does replaces a static checker, which finds contradictions before the code runs at all. Use both: mypy or pyright at the desk, beartype at the edges where untyped data arrives.

### big-O Python library
**Short:** Runs a function over growing inputs and fits the timings to infer its empirical complexity class.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, observability/profiling-and-performance @2

You supply the function and a data generator that produces an input of size n, and it times repeated runs across a range of sizes, then fits each candidate complexity class, constant through exponential, by least squares. It reports the best-fitting class along with the residuals of all of them, so you can judge how confident the fit actually is.

Reach for it to confirm that an implementation behaves the way its analysis claims, a useful check when a library call hidden inside a loop has quietly made something quadratic. Treat the answer as a hypothesis: what it measures is your machine, so caches and constant factors dominate at small n, and the sizes you can afford to time may never reach asymptotic behaviour.

### BigDecimal
**Short:** Java arbitrary-precision decimal type; the correct representation for money, with explicit scale and RoundingMode.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### bin(n), hex(n), oct
**Short:** Python built-ins converting an int to its binary/hex/octal string; bin(n).count('1') is the quick popcount.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @3

### bisect
**Short:** Python stdlib binary search and sorted-insert module; also the name of git's binary-search regression hunt.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, devtools/version-control-and-workbench @2

### Bitset
**Short:** Compact bit-array structure storing one bit per element; the backing for Bloom filters and bitmask dynamic programming.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

Packing one bit per element turns set operations into word operations: union, intersection and difference become OR, AND and ANDNOT over the backing words, so combining two million-element sets is tens of thousands of instructions rather than a million comparisons, and cardinality is a popcount. Java's `BitSet` grows automatically and offers `nextSetBit` for sparse iteration.

Reach for it when the universe is dense and bounded and the work is set algebra rather than lookup, which is why it backs Bloom filters, bitmask dynamic programming and postings intersection. The cost is that memory is proportional to the largest index present, not the number of members, so ten values with an index near two billion allocate hundreds of megabytes. Sparse universes belong in a hash set or a Roaring bitmap.

### BlockHound
**Short:** Java agent that instruments the JDK to throw when blocking calls run on Reactor/Netty non-blocking threads.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, devtools/testing-and-mocking @2

It instruments JDK methods that are known to block — socket reads, file I/O, `Thread.sleep`, JDBC, synchronized waits — and throws an error the moment one is called from a thread marked non-blocking, which for Reactor and Netty means the event-loop threads. The failure it catches is the one that is invisible otherwise: a blocking JDBC or `RestTemplate` call buried in a reactive chain does not break correctness, it quietly serializes your whole service onto a handful of event-loop threads under load.

Install it in tests with `BlockHound.install()`, add allow-list customizers for calls you know are safe, and expect to pass a JVM flag permitting the instrumentation on current JDKs. It is a test-time and development tool — the agent's overhead and its habit of failing hard make it wrong to leave enabled in production.

### boltons
**Short:** Pure-Python utility collection filling stdlib gaps, including iterutils helpers like chunked_iter and windowed_iter.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

It is pure Python with no dependencies, organized as modules named after the standard library ones they extend. `iterutils` carries `chunked`, `windowed` and the excellent `remap`, which recursively visits and rewrites an arbitrarily nested structure of dicts and lists in one call; `fileutils` has an atomic save that writes to a temporary file and renames, so a crash never leaves a half-written file.

Reach for it when you want one of these behaviours without adding a compiled dependency, and note that because each module stands alone you can vendor a single file rather than take the package. The costs are that it is a grab-bag, so you will not know a utility exists until you look, and that the standard library keeps absorbing this ground.

### BreakIterator
**Short:** Java/ICU4J class finding locale-aware grapheme, word, line and sentence boundaries instead of splitting on chars.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, applied-ml/nlp-and-text @3

### btop
**Short:** Terminal resource monitor showing live CPU, memory, disk, network and per-process usage; first glance at load.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/metrics-and-monitoring @3

It reads `/proc` and draws four panels: per-core CPU with a rolling history graph, memory and swap with disk, network throughput, and a filterable, sortable process tree. The history graphs are the real difference from `top`, because you see the shape of the last minute rather than one instantaneous sample, which distinguishes a steady load from a spike that has already passed.

Reach for it as the first screen on a host you have a terminal into and a vague complaint about, since it answers CPU, memory, disk and network in one view. The costs: it has only its own short history, drawing it costs measurably more CPU than `top` on an already saturated machine, and it is frequently absent from a minimal image where `htop` or `top` will be present.

### bytes.decode
**Short:** Python bytes method that turns raw bytes into str using an explicit codec at the I/O boundary.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

### C++ std::priority_queue
**Short:** C++ standard-library binary heap adaptor; max-heap by default, std::greater<> for a min-heap.
**Kind:** api
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

### cat /proc/<pid>/status
**Short:** Linux procfs read exposing per-process VmPeak, VmRSS and VmSwap to separate resident from virtual memory.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### cgroup v2 cpu.max
**Short:** cgroup v2 knob taking "<quota> <period>" for a hard CPU ceiling; the v1 spelling was cpu.cfs_quota_us, and Docker --cpus writes it.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### cgroup v2 cpu.weight
**Short:** cgroup v2 knob (1-10000, default 100) setting a group's relative CPU share under contention; the v1 spelling was cpu.shares.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### ChannelHandler
**Short:** Netty's per-connection pipeline stage: a reconfigurable chain separating decode, framing and business logic.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/aop-middleware-and-scheduling @3

### chardet
**Short:** Python library that statistically guesses a byte stream's character encoding; heuristic, never guaranteed.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

It is a port of Mozilla's universal charset detector and runs several probers over the byte stream at once: an escape-sequence prober, a multi-byte prober checking whether byte sequences are valid for each candidate, and a single-byte prober comparing character frequencies against per-language models. Each returns a confidence and the highest wins, so the result is a guess with a number attached rather than a fact.

Reach for it only where the encoding is genuinely unknown, such as legacy uploads or scraped pages whose charset header is absent or lying. It needs a reasonable amount of text, the single-byte Windows codepages are frequently indistinguishable on short input, and it returns a confident wrong answer rather than admitting defeat. Where a BOM or header declares the encoding, believe the declaration.

### charset-normalizer
**Short:** Pure-Python statistical character-encoding detector; heuristic replacement for chardet.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

Instead of language frequency models it works by brute force and scoring: decode the bytes with each plausible codec, then rate the result for mess, meaning the proportion of unlikely sequences and suspicious character transitions, and for coherence against known languages. The least implausible decoding wins. It is pure Python and MIT licensed, which is why `requests` adopted it in place of chardet.

Reach for it at an ingestion boundary where the encoding really is unknown, and treat the output as a decision to record rather than a property of the data. The same limits apply as to every detector: short strings carry too little signal, and the Windows single-byte codepages overlap so heavily that distinguishing them from bytes alone is often impossible. Where an encoding is declared, honour it and skip detection.

### checkedList
**Short:** Collections.checkedList - a decorating view that enforces the element type at runtime, catching heap pollution early.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### cholesky
**Short:** Cholesky decomposition of a positive-definite matrix; gives stable covariance handling and log-density math.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

A symmetric positive-definite matrix factors as a lower-triangular matrix times its transpose, at about half the work of a general LU factorization and needing no pivoting. What you buy is that everything downstream becomes triangular: a solve is two substitutions, the log-determinant is twice the sum of the logs of the diagonal and cannot overflow, and sampling a multivariate Gaussian is a matrix-vector product against standard normal noise.

That makes it the workhorse wherever a covariance or Gram matrix appears, in Gaussian log-likelihoods, Gaussian process regression and Kalman filters. Its failure mode is its diagnostic: it fails when the matrix is not positive definite, usually because rounding made a near-singular covariance indefinite. Adding a small multiple of the identity is the standard remedy, and if that jitter must keep growing, the model is rank-deficient.

### chrt
**Short:** Linux CLI that sets a process's real-time scheduling policy and priority (SCHED_FIFO, SCHED_RR, SCHED_DEADLINE).
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It wraps `sched_setscheduler`, changing which scheduling class a task belongs to. Under `SCHED_FIFO` a task with a static priority from 1 to 99 preempts every ordinary task and runs until it blocks or yields; `SCHED_RR` adds a time slice among equal priorities; `SCHED_DEADLINE` takes a runtime, period and deadline triple and the kernel refuses the request if the budget is not feasible.

Reach for it when a thread has a genuine latency bound, in audio or a packet-processing loop, where being scheduled late is a correctness failure. The danger is proportionate: a real-time thread that spins without blocking locks out everything below it including your shell, and the kernel's real-time throttle, 950 milliseconds of each second by default, is what saves the machine. For ordinary services cgroup weights express what you actually want.

### close
**Short:** generator.close() - raises GeneratorExit in a suspended generator so its cleanup runs and the generator finishes.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2

### codecs module
**Short:** Python stdlib codecs module: streaming encoders/decoders, codec lookup and custom error handlers for text.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

### collections
**Short:** Python stdlib module of specialized containers: deque, Counter, defaultdict, OrderedDict, namedtuple, ChainMap.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/runtime-internals-and-types @3

### collections.abc
**Short:** Python stdlib abstract base classes for the container protocols, supplying mixins and virtual-subclass registration.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/runtime-internals-and-types @2, apis-frameworks/design-patterns-and-principles @3

### Collections.binarySearch
**Short:** JDK helper doing O(log n) binary search over an already-sorted List, returning an insertion point when absent.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### collections.deque
**Short:** Python double-ended queue with O(1) append/pop at both ends; the correct sliding-window and BFS queue structure.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Collections.frequency
**Short:** JDK static helper counting how many elements of a collection equal a given object; a linear O(n) scan.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### collections.OrderedDict
**Short:** Python dict subclass with move_to_end and order-sensitive equality; plain dict has kept order since 3.7.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Collections.sort
**Short:** JDK list sort backed by TimSort: stable, adaptive, O(n log n), and the standard entry point before List.sort existed.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Collections.synchronizedList/Map
**Short:** Legacy JDK wrappers adding a single lock around a collection; superseded by java.util.concurrent types.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @2

### Collectors
**Short:** java.util.stream factory of terminal reduction strategies: toList, groupingBy, joining, plus custom collectors.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Collectors.teeing()
**Short:** Java Stream collector that feeds elements to two downstream collectors and merges both results in a single pass.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### com.google.re2j
**Short:** Java port of RE2: automaton-based regex with guaranteed linear-time matching and no catastrophic backtracking.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

It compiles the pattern into a finite automaton and simulates it, tracking the set of states reachable as it consumes each character, so it never backtracks and time is bounded by input length times pattern size however the quantifiers nest. It is pure Java with no native library to ship, and the API mirrors `java.util.regex` closely enough that most migrations are an import change.

Reach for it wherever a pattern or its input comes from outside your program, such as a user-supplied search or a rule loaded from configuration, because one catastrophic pattern on a request thread is a single-input denial of service. The trade is stated up front: backreferences and lookaround are unsupported, so patterns need auditing. On short trusted patterns the JDK engine is often faster.

### Compact strings
**Short:** JDK 9 JEP 254: String backed by byte[] plus a coder, storing Latin-1 text at one byte per character.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/text-encoding-and-regex @2

Before Java 9 a `String` always held a `char[]`, two bytes per character, even for pure ASCII. JEP 254 replaced that with a `byte[]` plus a one-byte coder field: if every character fits in Latin-1 the array holds one byte per character, otherwise UTF-16 as before. Every operation branches on the coder, and the whole thing is invisible through the API.

For a typical server, where strings and their arrays are usually the largest live category in a heap dump, the saving is substantial and free. The check is per string, so a single code point above U+00FF, a CJK character, an emoji or a curly quote, forces that whole string to two bytes each. Pair it with string deduplication when the heap also holds many equal strings.

### comparing
**Short:** Comparator.comparing and its thenComparing chain: the JDK's canonical way to compose ordering strategies.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### CompletableFuture
**Short:** JDK async primitive: a composable future with combinators for chaining, combining and handling failures.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### concurrent.futures
**Short:** Python stdlib executor abstraction over thread and process pools with submit, map and as_completed.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### concurrent.interpreters
**Short:** Python stdlib API for multiple interpreters in one process, each with its own GIL, for CPU-bound work without IPC.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @2

### ConcurrentHashMap
**Short:** Java's thread-safe hash map using CAS and per-bin locking; the default in-process store for shared mutable state.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2, caching/in-process-cache @3

### contextlib
**Short:** Python stdlib module for context managers: contextmanager, asynccontextmanager, ExitStack, suppress and nullcontext.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2, runtime-systems/concurrency-and-async @3

### contextlib.asynccontextmanager
**Short:** Python decorator turning an async generator into an async context manager; behind FastAPI's yield dependencies.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/dependency-injection-and-config @2

### contextvars
**Short:** CPython stdlib module carrying per-task state such as trace IDs and auth tokens across await boundaries.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, observability/tracing-apm-and-llm-observability @3

### Continuation
**Short:** JDK internal stack-snapshot primitive letting a virtual thread yield and resume; the mechanism under Loom.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @2

### Coq
**Short:** Proof assistant for machine-checked mathematics; used to verify inductive proofs about algorithms.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

It is a dependently typed language in which a proposition is a type and a proof is a term inhabiting it, so checking a proof is type-checking. You build the term interactively with tactics, and a small trusted kernel re-checks the finished term, so a buggy tactic cannot produce an unsound proof. Programs extract to OCaml or Haskell, making a verified algorithm into code you can run.

Reach for it when correctness is worth an order of magnitude more effort than testing and the artifact is small and formal, such as a compiler pass or a cryptographic protocol. The costs are honest: verification is measured in person-years, refactoring drags the proofs along, and a proof is only as meaningful as a specification that can itself be wrong. For distributed protocols, TLA+ finds most real bugs far more cheaply.

### Counter
**Short:** Python stdlib dict subclass for frequency counting, with arithmetic and most_common(k).
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### CPLEX
**Short:** IBM's commercial LP and mixed-integer optimizer, shipped in CPLEX Optimization Studio with the OPL language and a docplex API.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

It solves the continuous relaxation with simplex or a barrier method and then branches, adding
cutting planes and running presolve and heuristics until the gap between the incumbent
solution and the best proven bound falls below a tolerance you set. The controls are the usual
ones — time limit, MIP gap, emphasis, thread count — and it always reports a solution together
with how far from optimal it might be. IBM packages it with the OPL modelling language, a
`docplex` Python API, and a separate CP Optimizer engine for constraint programming.

Reach for it where the organisation is already licensed, or where OPL models and CP Optimizer
are part of the estate. It is commercial, with a free academic edition and a size-limited
community edition. Against Gurobi the decision is usually procurement and existing tooling
rather than a decisive performance gap; for pure scheduling and sequencing a
constraint-programming engine is often the better model.

### CPython ast module
**Short:** Python stdlib module that parses source into Python's own abstract syntax tree for inspection or rewriting.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

### CPython dis module
**Short:** Standard-library disassembler that prints the CPython bytecode a function compiles to.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, observability/profiling-and-performance @3

### CRaC
**Short:** Coordinated Restore at Checkpoint - snapshot a warmed-up JVM and restore it in milliseconds to kill cold-start latency.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, platform-delivery/container-and-image @2, observability/profiling-and-performance @3

It uses the kernel checkpoint machinery to dump a running process, heap, initialized classes and JIT-compiled code included, to disk, then restores that image in a fresh process, so the JVM comes back warm with no class loading and no re-profiling. The coordinated half is an API: components implement before-checkpoint and after-restore hooks to close descriptors and rebuild pools, because a live connection cannot survive being dumped.

Reach for it when JVM cold start is the actual problem, in serverless functions and fleets that scale out under load, taking time to first request from seconds to tens of milliseconds. The costs: the image holds everything in memory including secrets, every library must be checkpoint-aware, and it needs Linux and a supporting JDK. GraalVM native image solves the same problem at build time instead.

### CryptoMiniSat
**Short:** CDCL SAT solver with native XOR-clause handling and Gaussian elimination, aimed at cryptographic and algebraic instances.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

Encoding a long XOR constraint into conjunctive normal form costs an exponential number of
clauses, and XOR constraints are exactly what cryptanalysis produces. CryptoMiniSat keeps them
as first-class clauses and runs Gaussian elimination over them alongside ordinary
conflict-driven clause learning, so a system of parity constraints is reasoned about
algebraically instead of searched. It also does substantial inprocessing and ships Python
bindings.

Reach for it when the problem naturally contains parity constraints — cryptographic attacks,
error-correcting codes, and the approximate model counting that samples solutions of such
systems, where it is a common backend. On instances with no XOR structure it offers no
advantage over a mainstream CDCL solver, and a modern competition solver will usually be
faster.

### ctypes
**Short:** Python stdlib FFI: call C libraries and lay out or inspect raw C-style memory buffers from pure Python.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/runtime-internals-and-types @2

### curl
**Short:** Ubiquitous CLI and library for HTTP and other protocols; the default tool for probing endpoints and inspecting TLS.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @2, devtools/testing-and-mocking @3

The command line is a thin shell over libcurl, which implements HTTP/1.1, HTTP/2 and HTTP/3 along with FTP, SMTP and more, so anything the tool does is available from a program or a language binding. The options compose: `-H` adds a header, `-L` follows redirects, `--resolve` pins a hostname to an address so you can hit one backend directly, and `-w` prints timing fields such as `time_starttransfer`.

Its real value is as a reproduction case: a curl line is a bug report anyone can run, and if curl reproduces the failure the client library is not the cause. The timing breakdown separates slow DNS from slow connection setup from slow server think time. Two cautions: `-k` disables certificate verification and has no business in a committed script, and curl is a client, not a load generator.

### Cython with nogil
**Short:** Compiles annotated Python to C and releases the GIL inside nogil blocks so CPU hotspots run truly in parallel.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, devtools/compiler-toolchain-and-codegen @2, observability/profiling-and-performance @3

Cython compiles annotated Python into C. Inside a `nogil` block the generated code releases the interpreter lock, so that block runs genuinely in parallel with Python on other cores, but the compiler enforces the condition that makes this safe: no Python object may be touched inside, restricting it to C-typed scalars, typed memoryviews and calls into C. Combining it with `prange` emits an OpenMP loop.

Reach for it when a numeric hotspot is a tight loop NumPy cannot vectorize, such as a stencil update or a custom distance, and moving data to a process pool would cost more than the computation. The costs are a build step and a compiler per platform, a second dialect for reviewers, and the discipline that any accidental object access is a compile error. Numba gets much of this with no build step.

### cytoolz
**Short:** C-accelerated build of toolz: lazy functional utilities for composing iterator pipelines over Python data.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

It exposes the same API as `toolz` — `curry`, `pipe`, `compose`, `groupby`, `partition_all`, `merge_with`, `unique`, and iterator forms that stay lazy so a pipeline over a huge file never materializes it — with the implementation rewritten in Cython. `import cytoolz as toolz` is a drop-in swap that moves the hot loops out of the interpreter.

Reach for it when data-munging code is genuinely a chain of many small transformations and the per-step function-call overhead is showing up in a profile. It is not a general speedup: for numeric work, NumPy or Pandas vectorization beats any per-element functional pipeline, and for two or three steps a plain generator expression is faster to read than a composed one.

### dataclasses
**Short:** Python stdlib decorator that generates init/repr/eq from annotations, giving structured mutable record types.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/collections-and-algorithms @2, apis-frameworks/design-patterns-and-principles @3

### datetime
**Short:** Python stdlib module for date and time values, arithmetic with timedelta, time zones and ISO parsing/formatting.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @3

### defaultdict
**Short:** Python dict subclass that auto-initializes missing keys from a factory, removing setdefault boilerplate.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Deque<Command>
**Short:** A Java deque of command objects used as an undo/redo history stack in the Command pattern.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### Deque<Long>
**Short:** Java double-ended queue of timestamps, the data structure behind a sliding-window-log rate limiter.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, traffic-edge/rate-limiting-and-resilience @2

### dict
**Short:** Python's hash map: insertion-ordered since 3.7, open addressing with a compact index, resized at two-thirds load.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### difflib.SequenceMatcher
**Short:** Python stdlib sequence differ using the Ratcliff/Obershelp heuristic; fast similarity ratios, not a true LCS.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @3

### dis module
**Short:** Python stdlib bytecode disassembler; shows what the interpreter actually executes, e.g. is vs == at opcode level.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, observability/profiling-and-performance @3

### dit
**Short:** Python library computing information-theoretic quantities (entropy, mutual information) over discrete distributions.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

You build an explicit joint distribution over discrete outcomes and then ask questions of it, so the numbers returned are exact for the distribution you supplied rather than estimates from samples. Beyond entropy and mutual information it implements the multivariate measures no other Python library really covers: total correlation, co-information, and partial information decomposition, which splits what two sources tell you about a target into unique, redundant and synergistic parts.

Reach for it when the object of study is the information structure itself, for instance whether two features carry redundant or genuinely synergistic information about a label, which a pair of ordinary mutual-information scores cannot distinguish. The cost follows from the design: you must supply or estimate the joint first, and estimating a joint over more than a handful of variables from finite samples is exactly where entropy estimates go badly biased.

### dk.brics.automaton
**Short:** Java library building explicit DFAs and NFAs from regular expressions, with determinization and minimization.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/collections-and-algorithms @3

It compiles a regular expression into an explicit finite automaton and exposes the operations regex libraries do not: determinize and minimize, intersect, complement and union automata, test whether a language is empty or whether two patterns accept the same strings, and enumerate the strings accepted up to a given length. Matching walks the automaton one character at a time with no backtracking.

Reach for it when the question is about the language rather than one match, such as whether two firewall rules overlap or generating inputs a pattern accepts. The costs: the syntax is its own rather than Perl-compatible, there are no capture groups or lookaround, and determinization can blow up exponentially in memory, moving a hostile pattern's denial of service from time to space. For plain linear-time matching, RE2J is the smaller change.

### dmesg
**Short:** Prints the Linux kernel ring buffer - the place OOM kills, driver faults and hardware errors show up first.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/logging @2

It prints the kernel's ring buffer, an in-memory log the kernel writes to before and independently of any userspace logging daemon, which is why it holds the events nothing else recorded. The OOM killer's report is here, naming the process it chose and the memory state at the time; so are block-device I/O errors, filesystems remounting read-only, driver resets and connection-tracking overflows.

Reach for it first when a process died with no application log entry, the signature of something outside the process killing it: a container exiting with status 137 is almost always an out-of-memory kill visible here. Two limits: the buffer is a fixed-size ring, so older messages are silently overwritten and absence of evidence means nothing, and inside a container you are reading the host kernel's log.

### DoubleStream
**Short:** Primitive-specialized Java stream of doubles; avoids boxing and offers sum, average and summaryStatistics.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, observability/profiling-and-performance @3

### Eclipse Collections persistent collections
**Short:** Eclipse Collections' immutable containers: copy-on-write ImmutableList, Set and Map with size-specialized forms for small sizes.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

Calling `toImmutable()` returns a type whose interface simply has no mutating methods, so immutability is enforced by the compiler rather than by a wrapper that throws at run time the way `Collections.unmodifiableList` does. For sizes zero through ten there are dedicated implementations holding elements in fields, avoiding an array object entirely. Derived instances come from `newWith` and `newWithout`, which copy the contents.

Reach for it where a collection is genuinely shared and must not change, such as configuration, a lookup table or a value object's field. The cost is the copying: every derivation duplicates the elements, so building one item at a time is quadratic, and the right pattern is to build in a mutable collection and convert once. For cheap repeated derivation, a genuinely persistent library such as Vavr shares structure instead.

### Eclipse Collections primitive collections
**Short:** Eclipse Collections' int/long/double collections that store primitives unboxed, cutting memory over boxed types.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

The library ships the full cross-product of primitive containers, from `IntList` to `IntIntHashMap`, storing values directly in primitive arrays with open addressing rather than as boxed objects behind references. The saving compounds: a boxed `Integer` is about sixteen bytes on a 64-bit JVM and a hash entry node roughly thirty-two more. The values also sit contiguously, so iteration reads cache lines instead of chasing pointers.

Reach for it for large collections of numbers, such as identifier sets, counters, adjacency lists and feature indexes, where boxing dominates both the heap and the time. The costs are that these types do not implement the `java.util` interfaces, so values must be converted at any boundary expecting a `List` or `Map`, and that it is another dependency. fastutil and HPPC fill the same niche.

### EEVDF base_slice_ns
**Short:** Linux EEVDF's base time slice, 700 microseconds in current mainline, exposed at /sys/kernel/debug/sched/base_slice_ns and scaled per thread by weight.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### enum.Enum
**Short:** Python enumeration base class whose EnumMeta metaclass and _EnumDict enforce unique, singleton named members.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

### EnumMap
**Short:** Array-backed Map keyed by enum constants; used to express a state machine's transition table as data.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### EnumSet
**Short:** JDK Set implementation for enum types backed by a bit vector; compact and far faster than a HashSet of enums.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### ethtool
**Short:** Linux CLI for reading NIC driver statistics and toggling hardware offloads, ring sizes and link settings.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

It talks to the network driver beneath the IP stack: `-S` dumps driver and hardware counters, including the per-queue drop counters that explain loss no application log records; `-g` and `-G` read and resize the receive and transmit rings; `-k` and `-K` toggle offloads such as TSO and checksum offload; `-C` tunes interrupt coalescing; and the bare command shows negotiated speed, duplex and link state.

Reach for it when packets are disappearing and you need to know whether the NIC dropped them before the kernel ever saw them; the classic finding is an undersized receive ring under bursty traffic. The costs: counter names are driver-specific so there is no portable meaning, changes do not survive a reboot, and disabling an offload to make a capture readable will cost real throughput if you forget to restore it.

### Executors.newVirtualThreadPerTaskExecutor
**Short:** Java 21 executor that starts one virtual thread per submitted task instead of pooling platform threads.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### Fenwick/BIT
**Short:** Binary indexed tree giving O(log n) prefix sums and point updates; simpler to code than a segment tree.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

Each cell of the backing array stores the sum of a block whose length is the lowest set bit of its index, which is what makes the whole structure a single array with no pointers. A prefix sum walks down by repeatedly clearing the lowest set bit and an update walks up by adding it, so both touch about log n cells using the `i & -i` trick and nothing else.

Reach for it when the aggregate is invertible, meaning sums, counts or XOR, and you need prefix aggregates with point updates, which covers counting inversions, order statistics over a compressed value range and running rank queries. That invertibility is exactly its limit: a range minimum cannot be answered by subtracting one prefix from another, so minimum and gcd need a segment tree, at roughly twice the memory and several times the code.

### file -i
**Short:** POSIX command printing a file's guessed MIME type and character encoding from its magic bytes.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

### FileChannel.map
**Short:** Java call that memory-maps a file region; since Java 22 it can return an arbitrarily large MemorySegment.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/memory-processes-and-os @2

### Files.walk
**Short:** Java NIO method returning a lazy, closeable Stream over a directory tree without materializing the file list.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2

### Files.walkFileTree
**Short:** Java NIO directory walk driven by a FileVisitor with pre/post hooks and FileVisitResult pruning.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2

### fn.py
**Short:** Small functional-programming toolkit for Python: composition, currying, partial application and lazy structures.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

It brings a Scala-flavoured functional vocabulary to Python: an underscore placeholder that turns an expression into a function without a lambda, a wrapper for composition and partial application, currying, a lazy stream type that can refer to itself, immutable linked structures, and trampoline decorators that make deep recursion safe by turning it into a loop.

The honest guidance is that this is not the default choice in new code. The package is old and has seen little activity for years, and most of what it offers is now covered by `functools`, `itertools` and comprehensions, with `toolz` as the maintained option for pipeline composition. The placeholder syntax also produces functions that are hard to introspect, so a traceback tells you much less than a named function would.

### fnmatch
**Short:** Python stdlib module matching strings against shell glob patterns such as *.log.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/io-networking-and-syscalls @3

### free -h
**Short:** Linux CLI showing total, used and available RAM plus swap; 'available' is the number that actually matters.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It formats a handful of fields from `/proc/meminfo`. The one to read is available, the kernel's own estimate of how much a new workload could allocate without swapping, because it counts reclaimable page cache and slab that free does not. A healthy busy Linux machine shows almost no free memory by design, since the kernel uses everything spare as cache and hands it back on demand.

Reach for it as a five-second check on whether a host is under genuine memory pressure, reading available together with the swap row: a low available figure alongside growing swap use is the shape of a machine about to thrash. Its limits are that it is one instantaneous sample with no history and no attribution, so `vmstat 1` gives the trend and `ps` sorted by resident size names the culprit.

### functools.total_ordering
**Short:** Python class decorator that fills in the remaining rich-comparison dunders from __eq__ plus one ordering method.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/collections-and-algorithms @3

### future annotations
**Short:** PEP 563 future import that postpones annotation evaluation so annotations stay strings until inspected.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

### fuzzysearch PyPI
**Short:** Python library for approximate substring matching within a Levenshtein distance, for typo-tolerant search.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/collections-and-algorithms @2, search-retrieval/lexical-and-hybrid-search @3

Its core call searches for occurrences of a substring inside a longer text within a bounded Levenshtein distance and returns each match's start, end and distance. It chooses among several algorithms depending on the constraints given, and separate limits on substitutions, insertions and deletions are considerably cheaper than a general edit-distance budget. The inner loops are a C extension.

Reach for it when you need the location of an approximate occurrence inside a document, such as finding a quoted passage in text that OCR mangled, which is a different problem from scoring how similar two whole strings are; RapidFuzz is far faster at that. Its cost grows quickly with the allowed distance, so keep the budget small, and put an n-gram index in front of it for search across many documents.

### G1 for native
**Short:** GraalVM Native Image option using the G1 collector instead of Serial GC, for large-heap native Java services.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, devtools/compiler-toolchain-and-codegen @2

A GraalVM native image defaults to a serial collector, fine for a command-line tool but stopping the world for a full collection whose pause scales with the live set, so a long-running service with a multi-gigabyte heap sees pauses grow uncomfortable. Building with the G1 option compiles a generational, region-based, mostly concurrent collector into the image instead, with the same pause-target tuning a JVM-hosted service would have.

Reach for it when the native image is a server rather than a short-lived process, which in practice means once the live heap passes roughly a gigabyte. The costs are that it is not available in every distribution or on every platform, and that it makes both the image and its runtime footprint larger, eating into the small-footprint argument for a native image at all.

### gc module
**Short:** CPython's cyclic garbage collector control surface: thresholds, freeze, disable and forced collect.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/runtime-internals-and-types @2

### Generator expressions
**Short:** Python syntax (x for x in ...) producing a lazy iterator, avoiding the list a comprehension would build.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/memory-processes-and-os @3

### Glucose
**Short:** MiniSat-derived CDCL SAT solver whose learned-clause quality metric, the literal block distance, became standard everywhere.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

Its contribution is a way to judge a learned clause. The literal block distance is the number
of distinct decision levels appearing in the clause, and a low value marks a clause worth
keeping. That single measure drives two things at once: aggressive clause-database reduction,
which throws the rest away and keeps propagation fast, and an adaptive restart policy that
restarts when recent conflicts look unproductive instead of on a fixed schedule.

Both ideas are now in essentially every competitive CDCL solver, which is the reason the name
is worth knowing. As a solver to actually run it has been overtaken by its own descendants; a
parallel variant exists, and for a hard instance today Kissat or CaDiCaL is the sharper
tool.

### Go goroutines
**Short:** Go's built-in lightweight threads: 2-8 KB growable stacks multiplexed onto OS threads by an M:N runtime scheduler.
**Kind:** api
**Lang:** go
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @3

### Google Guice TypeLiteral
**Short:** Guice class capturing a full generic type at runtime, working around erasure so List<String> can be a binding key.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/dependency-injection-and-config @2

### GraalVM Polyglot
**Short:** Embeds JavaScript, Python or Ruby inside a JVM app with host-access control and resource limits.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2, security/supply-chain-and-runtime-security @3

The Truffle framework runs guest languages on the same VM as your Java code, so values cross the boundary without serialization: a Java object can be handed to a script and have its methods called, and a guest function comes back as a value you can execute. A context is constructed with explicit policy over which host classes are reachable, whether the guest may do I/O or create threads, plus limits on statements executed and memory used.

The use case is user-supplied logic inside a JVM service, such as pricing rules or plugins, where you want an expressive language with a sandbox and a kill switch. The costs are substantial: guest startup and warmup are far from free, memory per context is not small, and language completeness varies. It also pushes deployment toward a GraalVM-based JDK rather than any JDK.

### greenlet
**Short:** Low-level coroutine primitive with switchable stacks; powers SQLAlchemy's sync-inside-async bridge.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, data-access/orm-and-data-mapping @3

A greenlet is a lightweight coroutine with its own C stack that switches explicitly via `switch()`. The consequence that matters is that the switch can happen deep inside an ordinary call chain, so synchronous-looking code can be suspended without every enclosing frame being declared `async def` — the opposite of asyncio, where the colour of a function propagates all the way up.

That is precisely how SQLAlchemy's async support works: its ORM internals are written synchronously and `greenlet_spawn` lets them yield to the event loop at the database-driver boundary, which is why installing async SQLAlchemy pulls in greenlet and why `run_sync` exists. gevent is built on the same primitive. You rarely call it yourself; you meet it in a traceback, in a dependency you must not remove, or in a version conflict after a Python upgrade.

### Guava BloomFilter
**Short:** Production-grade Java Bloom filter with configurable false-positive rate for cheap probabilistic membership tests.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, caching/in-process-cache @3

### Guava Interners.newWeakInterner
**Short:** Guava factory for a weak-keyed interner pool; canonicalizes objects without the permanent leak of String.intern().
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, apis-frameworks/design-patterns-and-principles @2, runtime-systems/collections-and-algorithms @3

### Gurobi
**Short:** Commercial mathematical-programming solver for LP, MIP, QP and MIQP, consistently at or near the top of independent benchmarks.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

It solves the continuous relaxation with simplex or a barrier method, then branches and cuts,
running presolve, cutting-plane separation, heuristics and node processing across threads
until the gap between the incumbent and the best proven bound closes to your tolerance. That
gap is the point: you can stop at any time with a solution and a statement of how far from
optimal it might be. APIs cover Python, C++, Java, C# and the modelling layers built above
them.

Reach for it when a model is large enough that solver performance decides whether the problem
is tractable at all, and a licence is affordable — it is commercial, with free academic
licences and a size-limited evaluation. HiGHS and SCIP are the open-source alternatives for
models that fit them, and a constraint-programming engine is usually the better model for pure
scheduling, rostering and sequencing.

### hand-written META-INF/services
**Short:** Manually authored ServiceLoader registration file listing implementations of a service interface on the classpath.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/dependency-injection-and-config @3

The file is plain text named for the fully qualified service interface, with one implementation class per line. `ServiceLoader.load` scans every such file on the classpath, loads each named class, instantiates it through its public no-argument constructor and yields the instances lazily. That is how JDBC drivers, charset providers and annotation processors register themselves, with no framework and no classpath scanning.

The costs all come from it being an untyped text file. A typo or a class renamed in a refactor becomes a runtime configuration error rather than a compile error, and building a fat jar silently overwrites one provider file with another unless the shade plugin concatenates them, which is the classic cause of a driver that works in the IDE and vanishes when deployed. An annotation processor removes the drift.

### hashids
**Short:** Library encoding integer ids into short reversible strings; obfuscation for public URLs, never a security boundary.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, data-access/schema-and-migration @2, security/privacy-and-compliance @3

Despite the name it is neither a hash nor encryption: it is a reversible encoding of one or more non-negative integers into a short string, using an alphabet shuffled from a salt, with a guard character and an optional minimum length. Given the same salt you decode the string straight back to the original numbers, so nothing needs to be stored.

Use it to keep sequential database keys out of public URLs so a customer cannot count your orders or walk to the next one. Be precise about the guarantee: the salt is not a key, the algorithm is public, and it has been reversed from a handful of observed outputs, so this defeats casual enumeration and nothing more. Every fetch still needs an authorization check. The project is unmaintained and its author points at `sqids`.

### HashMap
**Short:** Java's hash table: default capacity 16 and load factor 0.75, converting a long bucket chain to a red-black tree.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### heapq
**Short:** Python stdlib binary min-heap over a plain list: O(log n) push/pop for priority queues, Dijkstra and Huffman.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### helgrind
**Short:** Valgrind tool detecting data races and lock-ordering violations at runtime; heavyweight, test-time only.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/concurrency-and-async @1, devtools/testing-and-mocking @2, observability/profiling-and-performance @3

It runs the binary on Valgrind's synthetic CPU, watching every memory access and pthread operation, and builds a happens-before relation from locks, condition variables and thread creation. From that it reports accesses to memory not consistently protected by any single lock, misuse of the pthread API, and lock-order inversions that could deadlock even though this run did not.

That third check is the reason to prefer it over a pure race detector, because it finds a potential ABBA deadlock from one non-deadlocking execution. The cost is Valgrind's cost, commonly a twenty-fold slowdown or worse, so it belongs in a targeted test and never in production. It also covers native code only, so for a JVM service it inspects the JNI side. ThreadSanitizer is much faster but needs recompilation.

### Hooks.onOperatorDebug
**Short:** Reactor hook capturing assembly-time stack traces so a reactive error shows where the chain was built.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/tracing-apm-and-llm-observability @2

### htop
**Short:** Interactive terminal process and resource viewer: per-core load, memory, and per-thread breakdown.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @3

htop is an interactive process viewer: per-core load bars, memory and swap gauges, and a sortable, scrollable process list with function keys to search, renice or kill without dropping to another command. Two toggles do most of the work — thread display, which resolves a process sitting at 400% CPU into which of its threads is actually spinning, and tree view, which attributes a runaway child to the parent that spawned it.

Use it as the first ten seconds of triage on a box: is this CPU, memory pressure or one specific process. Once you know which process, move to a real profiler, because htop reads `/proc` and shows current state with no history — it tells you what is happening now, never what happened at 03:00.

### hwloc
**Short:** Portable hardware locality library and CLI that renders CPU, cache and NUMA topology for pinning decisions.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It builds a tree model of the machine from the operating system and firmware: packages, NUMA nodes with their attached memory, the L3, L2 and L1 caches and which cores share each, physical cores, and the SMT threads inside each core. It also attaches I/O devices, so you can ask which NUMA node a given network card or GPU hangs off, which is how MPI and OpenMP runtimes place workers.

Reach for it whenever placement matters: pinning a latency-sensitive pool so its threads share an L3, or keeping packet-processing threads on the node the NIC attaches to. The costs are that it describes hardware rather than recommending anything, so the decision and the measurement remain yours and a wrong pinning is worse than none. Inside a container it reports only the cpuset you were granted. `lstopo` is its CLI.

### HyperLogLog
**Short:** Probabilistic cardinality estimator counting distinct items in fixed memory, trading about one percent error for flat cost.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-stores/key-value-and-embedded @2

It never stores the members. Each item is hashed and its leading-zero run recorded in one of a fixed number of small registers, and the harmonic mean of those registers estimates how many distinct items must have been seen to produce them. Error falls with the square root of the register count, so accuracy is expensive: halving the error costs four times the memory, which is why implementations pick one register count and stop.

Reach for it for unique visitors, distinct IPs, distinct search terms — anything where the count matters and the members do not, at a cardinality where an exact set would cost gigabytes. It cannot tell you whether a specific item was seen, and unions are cheap while intersections are not.

### IANA tzdata
**Short:** The IANA time-zone database of offsets and DST transition rules, shipped with the JDK and most OSes.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/runtime-internals-and-types @3

It is a set of source files, maintained collaboratively and published by IANA, defining each zone as a history of rules: UTC offsets, the dates daylight saving starts and ends, and every change back to the adoption of standard time. A compiler turns them into the binary files under the system zoneinfo directory and the copy the JDK carries internally. Because zone rules are political, releases appear several times a year with little notice.

The practical consequence is that a correct future local time is a function of data your runtime shipped with, not of arithmetic, so a container image pinned two years ago produces wrong local times once a government moves a boundary. Keep every copy patched: the OS package, a JDK patch release, and the tzdata package for Python. Store an instant in UTC alongside the zone identifier, never a fixed offset.

### iconv
**Short:** POSIX library and CLI that converts a byte stream or file between two named character encodings.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

Given a source and a target encoding it decodes the input to an internal representation and re-encodes it, and a list option enumerates the several hundred encodings the local C library knows. A character with no representation in the target is an error by default, which is the right default; a transliterate suffix substitutes a similar character and an ignore suffix drops it. The same conversion is available as a C API.

Reach for it at an ingest boundary where both encodings are known, converting a legacy export to UTF-8 once so nothing downstream needs to know about cp1252. Its trap is that it cannot tell you what the input encoding is and will produce plausible-looking mojibake if you name the wrong one. The ignore option silently discards data, and on some platforms also sets a nonzero exit status, so check both output and status.

### ICU
**Short:** Reference Unicode library (C/C++/Java) for normalization, collation, segmentation and transliteration.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

It is the reference implementation of Unicode and CLDR behaviour and covers the operations that are wrong when done naively: normalization in all four forms, collation that sorts by a locale's real rules rather than code-point order, boundary analysis for grapheme clusters, words and sentences, case mapping that respects locale such as the Turkish dotless i, transliteration between scripts, and message formatting with plural selection.

Reach for it whenever text must be correct for humans rather than merely processed as bytes: sorting names for a locale, counting characters the way a user perceives them, or case-folding for comparison. The costs are size and versioning. The data tables run to tens of megabytes. And two systems on different versions can order the same strings differently, the mechanism behind database index corruption after a collation upgrade, so pin the version.

### id() built-in
**Short:** Python built-in returning an object's identity, in CPython its memory address; used to reason about aliasing.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/memory-processes-and-os @3

### ifconfig
**Short:** Legacy Unix CLI for viewing and configuring network interfaces, addresses and flags.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

It reads and writes interface state through the old ioctl interface: address, netmask, MTU, flags such as UP and PROMISC, and per-interface counters. Its limits are structural rather than cosmetic, because that interface predates most of modern Linux networking: it shows only one IPv4 address per interface even when several are configured, its IPv6 support is incomplete, and it knows nothing about policy routing, network namespaces or VRFs.

It survives because a decade of runbooks use it and because it remains the native tool on macOS and the BSDs. On Linux it belongs to the deprecated net-tools package and is frequently not installed in a container image, so `ip addr`, `ip link` and `ip route` are the commands worth learning. If you do use it on Linux, treat its output as a hint and confirm anything surprising with `ip`.

### inspect
**Short:** Python stdlib introspection module (signatures, closures, members); also the name of AISI's Inspect eval framework.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, ml-lifecycle/evaluation-and-benchmarks @2

### int
**Short:** Python's arbitrary-precision integer type; int(s, base) also parses a string in any base from 2 to 36.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @3

### Integer.bitCount
**Short:** JDK popcount for an int: counts set bits and is intrinsified to the POPCNT instruction on modern CPUs.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Integer.toBinaryString
**Short:** Java method rendering an int as its unsigned 32-bit binary string, used when reasoning about bit manipulation.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @3

### IntStream
**Short:** Primitive int stream in java.util.stream that avoids Integer boxing and adds sum, average and range operations.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### IntStream.range
**Short:** Java factory creating a primitive int stream over a half-open range, avoiding boxing in index-driven pipelines.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### iostat
**Short:** Linux CLI reporting per-device I/O and per-CPU utilization; the first stop for saturation triage.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2, runtime-systems/io-networking-and-syscalls @3

It is part of sysstat, and its first report is the average since boot rather than current activity, so the useful invocation samples at an interval with the first block discarded. The extended columns decide a diagnosis: reads and writes per second give the shape of the load, the await columns give the time a request spent queued plus serviced, and utilization is the fraction of the interval with at least one request in flight.

Read utilization carefully, because it is the most misinterpreted number here. On a single spinning disk it approximates saturation, but on an SSD servicing many requests concurrently, one hundred percent only means never idle and the device may be far from its limit. Judge saturation instead from await rising while service time stays flat. It reports per device, so the next question, which process, needs `pidstat -d` or `iotop`.

### ip
**Short:** The iproute2 CLI for inspecting and configuring Linux interfaces, addresses, routes and neighbours; replaces ifconfig.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

It is the iproute2 front end to the kernel's netlink interface, organized as objects with subcommands: `ip addr` for addresses, several per interface and both families, `ip link` for interfaces and state, `ip route` for the routing table, `ip neigh` for the neighbour cache, `ip rule` for policy routing and `ip netns` for namespaces. Because it speaks netlink it can express everything modern Linux networking supports.

This is the tool to know on Linux, because it is present where `ifconfig` and `route` frequently are not, and because it is what container runtimes and CNI plugins manipulate underneath. Two notes: changes take effect immediately but do not survive a reboot unless written into the network configuration, and inside a container you see one namespace, so a route that exists on the host is invisible rather than broken.

### ip tcp_metrics
**Short:** iproute2 subcommand showing the kernel's cached per-peer TCP metrics (cwnd, RTT, ssthresh) used to warm new connections.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

The kernel keeps a small cache of what it learned from previous connections to each peer: smoothed round-trip time and variance, the slow-start threshold and congestion window it converged on, the path MSS, and whether the peer supports features such as Fast Open. A new connection to the same address starts from those values rather than cold defaults, which is why a second connection often ramps faster than the first.

It matters most when the cache works against you: a transient loss event that drove the slow-start threshold very low is remembered, and every subsequent connection to that peer starts conservatively and looks mysteriously slow while other destinations behave normally. Flushing the entry and watching the behaviour change confirms that quickly. The cache is per host, does not survive a reboot, and is keyed by address, so backends behind one load balancer share an entry.

### iperf3
**Short:** Client-server tool that saturates a link to measure achievable TCP or UDP throughput, jitter and loss.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, devtools/testing-and-mocking @2

One side runs as a server and the other as a client; the client opens a control connection and pushes generated data over one or more TCP streams for a fixed duration, reporting throughput per interval plus retransmits and the congestion window on Linux. Switching to UDP with a target rate measures jitter and loss instead, the right mode for voice and video paths. Parallel streams and a reverse-direction flag cover unrepresentative cases.

Reach for it to establish what a path can carry before blaming an application: a service moving forty megabytes per second across a link that saturates above a gigabit has an application problem. The traps: it needs a cooperating server at the far end, it deliberately saturates the link so it will hurt production traffic sharing that path, and a single TCP stream over a long path is limited by window size rather than the link.

### iptables
**Short:** Linux netfilter CLI configuring L3/L4 packet filtering, NAT and port-forwarding rules inside the kernel.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/supply-chain-and-runtime-security @3, traffic-edge/proxy-and-load-balancer @3

It is a front end to netfilter. Rules live in chains, the built-in ones covering input, output, forwarding and the two NAT hook points, grouped into tables by purpose: filter for accept and drop, nat for translation, mangle for editing packet fields, raw for bypassing connection tracking. A packet traverses chains in a fixed order and takes the first matching rule's target, so ordering is semantics rather than style.

You still meet it everywhere, because Docker's published ports and kube-proxy's default mode are generated iptables rules, even though nftables has replaced it as the kernel implementation. The costs: rules in a chain are evaluated linearly, so a large service set produces thousands of rules and measurable per-packet CPU, which drove IPVS and eBPF alternatives; nothing persists across a reboot; and a mistaken default policy locks you out of a remote host.

### Iterable
**Short:** Java's iteration contract; implementing it is what makes a custom collection usable in the enhanced for loop.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### Iterables
**Short:** Guava's iterable helpers plus AbstractIterator's computeNext/endOfData, removing hasNext/next boilerplate.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### itertools
**Short:** Python stdlib module of lazy iterator combinators: chain, islice, groupby, product, permutations, tee.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### itertools.combinations, permutations
**Short:** Python stdlib combinatorial generators - use instead of hand-rolled backtracking when there are no custom constraints.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### java --module-path
**Short:** JVM launcher flag that resolves JPMS modules from the module path and names the initial module and main class.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/build-and-dependency-management @3

### Java records
**Short:** Java language feature declaring an immutable data carrier with generated equals, hashCode and canonical constructor.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### java.io.InputStream
**Short:** Java's abstract byte-input source; a template class whose only abstract primitive is read().
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2

### java.io.InputStreamReader
**Short:** JDK bridge presenting a byte stream as a character stream under a given charset; the canonical Adapter example.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/io-networking-and-syscalls @2, apis-frameworks/design-patterns-and-principles @2

### java.lang.foreign package
**Short:** Panama's FFM API (Java 22 GA, JEP 454): Arena, MemorySegment, MemoryLayout and Linker for native calls.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/memory-processes-and-os @2, runtime-systems/io-networking-and-syscalls @3

### java.lang.instrument
**Short:** JDK agent API for transforming bytecode at load time or retransforming loaded classes; the basis of APM agents.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2, observability/profiling-and-performance @3

### java.lang.reflect
**Short:** Java's reflection API for inspecting and invoking classes, fields and methods, including erased signatures.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1

### java.lang.reflect.ParameterizedType
**Short:** Reflection interface exposing a generic type's actual type arguments at runtime, past erasure.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1

### java.lang.Thread
**Short:** The JVM thread class; before Java 21 every instance mapped 1:1 to an OS thread, which virtual threads changed.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### java.lang.Thread.ofVirtual()
**Short:** JDK 21 factory for virtual threads, enabling a thread per task at a few KB of stack instead of ~1 MB.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### java.lang.VirtualThread
**Short:** JDK lightweight thread scheduled M:N onto carriers with a heap-allocated stack, for high-concurrency blocking I/O.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @3

### java.nio.channels
**Short:** Java NIO channel and Selector API for non-blocking socket and file I/O with readiness-based multiplexing.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/concurrency-and-async @2

### java.nio.charset.Charset.forName
**Short:** Looks up a character set by name through the JDK's charset registry, a classic string-keyed factory.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, apis-frameworks/design-patterns-and-principles @2

### java.nio.file.DirectoryStream
**Short:** Closeable, lazily-iterated view of a directory's entries, so huge directories are walked without materializing a list.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2

### java.nio.file.Files
**Short:** JDK utility class for modern file operations: read/write, copy, move, walk, attributes and directory streams.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1

### java.nio.file.FileSystems
**Short:** JDK entry point returning a FileSystem provider whose Path, WatchService and PathMatcher objects match each other.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2

### java.nio.file.Path
**Short:** NIO.2 filesystem path abstraction, walked by a FileVisitor with pruning via FileVisitResult.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @3

### java.nio.file.spi.FileSystemProvider
**Short:** The SPI behind Path/Files, letting the same file code run over local disk, a zip archive or an object store.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2, data-stores/object-and-file-storage @3

### java.nio.file.WatchService
**Short:** JDK API that watches directories and delivers create/modify/delete filesystem events.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1

### java.text.Normalizer
**Short:** JDK class applying Unicode NFC, NFD, NFKC or NFKD normalization so visually equal strings compare equal.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### java.time
**Short:** Java's JSR-310 date/time API: immutable Instant, LocalDate, ZonedDateTime, Duration and Period value types.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/collections-and-algorithms @2

### java.time.DateTimeFormatter
**Short:** Immutable, thread-safe formatter and parser for java.time types with locale and pattern support.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### java.time.format.DateTimeFormatter
**Short:** Immutable, thread-safe Java formatter/parser for date-time values using ISO forms, custom patterns and locales.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/text-encoding-and-regex @2

### java.util.AbstractList
**Short:** Skeletal List implementation: supply get and size and inherit iteration, equals, hashCode and the rest.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### java.util.Arrays.asList
**Short:** Wraps an array as a fixed-size List view: writes pass through to the backing array, add and remove throw.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### java.util.Collections.enumeration
**Short:** JDK adapter exposing a modern Collection to legacy APIs that still expect an Enumeration.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### java.util.Collections.unmodifiableList
**Short:** Read-only decorator view over a list; it is a view, so the backing list can still change underneath.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### java.util.Comparator
**Short:** The JDK's canonical Strategy interface for ordering, composable via comparing/thenComparing/reversed.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### java.util.concurrent
**Short:** Java's concurrency toolkit: executors, futures, locks, atomics, latches and concurrent collections.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @3

### java.util.concurrent.atomic
**Short:** JDK package of lock-free atomics (AtomicInteger, AtomicReference, VarHandle-backed CAS) for shared mutable state.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### java.util.concurrent.BlockingQueue
**Short:** Thread-safe queue whose put/take block when full or empty; the standard producer-consumer handoff in Java.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2, runtime-systems/collections-and-algorithms @3

### java.util.concurrent.Callable<V>
**Short:** Runnable's counterpart that returns a value and may throw checked exceptions; submitted for a Future.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

### java.util.concurrent.Executors
**Short:** JDK factory class producing ExecutorService thread pools while hiding the concrete implementation.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @3

### java.util.concurrent.Executors.callable
**Short:** Adapts a Runnable into a Callable<Object> so it can be submitted where the executor API requires a result type.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

### java.util.concurrent.Flow
**Short:** JDK's Reactive Streams interfaces - Publisher, Subscriber, Subscription.request(n) - giving observers backpressure.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/rpc-graphql-and-streaming @3

### java.util.concurrent.locks.StampedLock
**Short:** JDK lock offering an optimistic read mode with validation, plus upgradable read and write locks.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### java.util.concurrent.PriorityBlockingQueue
**Short:** Unbounded thread-safe priority queue: heap ordering plus a blocking take() for producer/consumer scheduling.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2

### java.util.concurrent.Semaphore
**Short:** Java counting semaphore limiting concurrent access to a resource; permits may be released by any thread.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### java.util.concurrent.ThreadFactory
**Short:** Injectable factory that names threads and sets daemon status and the uncaught-exception handler for a pool.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

### java.util.concurrent.ThreadPoolExecutor
**Short:** Java's configurable thread pool: core/max threads, work queue, keep-alive and rejection policy.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### java.util.Iterator
**Short:** Java's iteration protocol; implementing Iterable over it makes a custom type usable in the enhanced for loop.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### java.util.Optional
**Short:** Container type expressing a possibly-absent return value; intended for return types, not fields or parameters.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @3

### java.util.PriorityQueue
**Short:** JDK binary min-heap queue ordered by a comparator; O(log n) offer/poll, not thread-safe, iteration unordered.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### java.util.regex
**Short:** Java's backtracking regex engine with lookaround, backreferences and atomic groups; audit for catastrophic patterns.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### java.util.regex, PCRE, Python re
**Short:** Backtracking-NFA regex engines; expressive but exponential on pathological patterns.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

### java.util.Spliterator
**Short:** Splittable iterator with size and characteristic hints; the source contract that makes parallel streams possible.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2, apis-frameworks/design-patterns-and-principles @3

### java.util.stream.Collector
**Short:** Java Streams interface for custom terminal reductions, built from supplier, accumulator, combiner and finisher.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### java.util.stream.Stream
**Short:** Java's core stream API: lazy pipelines of map/filter/reduce over a source, with an opt-in parallel execution mode.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @3

### javap -p -s <class>
**Short:** JDK disassembler printing the erased type descriptors and members the JVM actually links against.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

It disassembles a compiled class file rather than reading source, so it shows what the JVM will actually link against. The private flag includes non-public members and the signature flag prints the internal descriptor of each field and method, the encoded form every invoke instruction references. Adding the code flag prints bytecode and the verbose flag adds the constant pool and the signature attribute that preserves generic types the descriptor erases.

This is how you answer questions the source cannot: what a lambda compiled to, which is a synthetic method plus an `invokedynamic` call site; why two overloads collide after erasure; whether the compiler inserted a bridge method for an inner class; and what a string concatenation became. It reads class files only, so anything generated at run time by a proxy or agent is invisible.

### jcmd <pid> GC.class_histogram
**Short:** jcmd diagnostic printing live object count and bytes per class; the fast first check before a full heap dump.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

### JVM stack size -Xss
**Short:** JVM flag setting per-thread stack size (default ~512 KB-1 MB); raise it for deeply recursive algorithms.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/concurrency-and-async @2

### Kissat
**Short:** Heavily engineered sequential CDCL SAT solver, a C rewrite of CaDiCaL's ideas that has topped recent SAT competitions.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

It is a from-scratch reimplementation in C of the design behind CaDiCaL, tuned for the things
that decide real runtimes: compact clause and watch-list representations, cache-friendly unit
propagation, and an inprocessing schedule that interleaves subsumption, vivification, bounded
variable elimination and probing with the search itself. It is single-threaded by design, and
that single thread is what keeps winning the main track.

Reach for it when you have a hard CNF instance and want the strongest sequential solver: a
file in, a satisfying assignment or `UNSAT` out, with DRAT proof logging available for the
unsatisfiable answer. If instead you need to call a solver repeatedly from inside an
application, adding and retracting assumptions between calls, CaDiCaL's incremental interface
is the better-trodden path.

### LAPACK
**Short:** Fortran linear-algebra library (solvers, factorizations, eigenproblems) sitting under NumPy and SciPy.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, gpu/gpu-math-libraries @3

It is the Fortran library of dense linear algebra routines layered on BLAS: LU, QR, Cholesky and singular-value factorizations, linear solves, least squares and eigenvalue problems. Its performance comes from being blocked, operating on submatrices sized to fit cache and expressing the inner work as level-three BLAS matrix-matrix operations, which an optimized BLAS turns into vectorized, multithreaded kernels.

You rarely call it directly, since NumPy, SciPy, R and MATLAB all dispatch into it, but knowing it is underneath explains real behaviour. Numerical results differ slightly between machines because a different BLAS chose a different order of operations. And plain NumPy code can suddenly consume every core because the BLAS is multithreaded, which is why thread-count environment variables matter when you also run process-level parallelism.

### ldd
**Short:** Prints the shared libraries a dynamically linked binary needs and where the loader resolves each one.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

It reports the dynamic dependencies of an ELF binary and, for each one, which file the loader resolves it to, or reports it as not found. It obtains this by asking the dynamic loader to perform a trial resolution, which is why the output reflects the real search order: the RPATH baked into the binary, then the library path environment variable, then the loader cache, then the default directories.

It is the first command for the container failure where a binary built against one C library runs on an image with another. Two cautions. Because it may invoke the loader against the binary, running it on an untrusted executable can execute code, so `readelf -d` is the safe equivalent. And it shows only what is linked at startup, so libraries opened later through `dlopen`, which is how plugins load, never appear.

### Levenshtein PyPI package
**Short:** C-extension edit-distance and string-similarity library, roughly 100x faster than a pure-Python dynamic program.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @2

It is a C extension computing edit distance and related metrics using bit-parallel algorithms, so for patterns up to a machine word it evaluates a whole row of the dynamic-programming matrix in a few word operations instead of cell by cell, which is where the large constant-factor win over pure Python comes from. It also provides normalized ratios, Hamming, Jaro-Winkler, and the edit operations themselves.

Reach for it when scoring one-to-one string similarity in bulk, for deduplication keys, fuzzy joins and spell-check ranking. The cost to plan around is not the individual comparison but the number of them: the algorithm is quadratic in string length and comparing every pair is quadratic again, so block on a cheap key or an n-gram index first. This package and `python-Levenshtein` are one lineage maintained under RapidFuzz.

### Linked list sentinel/dummy node
**Short:** A placeholder head node that removes special-case handling for empty lists and head deletion in linked-list code.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

You allocate one node that carries no data and always sits before the first real element, so the head pointer is never null and every real node has a predecessor. That single invariant collapses the two special cases responsible for most linked-list bugs: inserting into an empty list and deleting the first element both become the ordinary operation of rewiring a predecessor's next pointer.

The everyday idiom is to build a result behind a local dummy and return the node after it, which is why merging two sorted lists or removing the nth node from the end is short with one and fiddly without. The costs are small but real: one extra allocation per list, and a discipline that every traversal must remember the sentinel is not an element, since a length count that forgets is off by one.

### LinkedHashMap
**Short:** Java hash map preserving insertion or access order; the usual base class for an LRU cache.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, caching/in-process-cache @3

### LinkedList
**Short:** Java's doubly-linked List and Deque; O(1) insert/remove at a held position, O(n) random access.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Linker
**Short:** Java 22 FFM API type creating downcall handles into native C functions and upcall stubs back into Java.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/io-networking-and-syscalls @3

### list
**Short:** Python's built-in dynamic array; amortized O(1) append and pop, the default stack.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### list.sort
**Short:** Python's in-place list sort: stable Timsort, O(n log n), with key= and reverse= and no copy of the list.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### listpack
**Short:** A contiguous, pointer-free byte-array encoding for small collections, which replaced Redis's ziplist in 7.0.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-stores/key-value-and-embedded @2

Entries sit back to back with no per-element pointer or allocation, so a small hash or set costs a few hundred bytes instead of a hash table's node-per-entry overhead, and a linear scan over a couple of kilobytes of sequential memory beats a hash lookup's three or four dependent cache misses. Big-O favours the hash table; the cache line favours the scan until the collection grows.

Its predecessor stored each entry's length inside its neighbour, so growing one element could force every following element to grow in a cascade with quadratic worst-case cost. Listpack removes the cause: each element records only its own size, so a change can never propagate. The tradeoff is that the encoding switch is one-way — cross the threshold once and the memory is not reclaimed by shrinking back.

### LMAX Disruptor
**Short:** Lock-free ring-buffer inter-thread messaging library; powers Log4j2 async loggers at very low latency.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/logging @3, data-movement/message-broker @3

It replaces a queue of nodes with a pre-allocated ring buffer of reusable event objects addressed by a monotonically increasing sequence. A producer claims a slot by advancing a sequence with a compare-and-swap, writes into the object already there, then publishes; each consumer tracks its own sequence. So there is no per-message allocation and no garbage, no lock on the fast path, and the sequence counters are padded onto their own cache lines.

Reach for it when a single process must move millions of events per second through a known pipeline at low and predictable latency, which is why matching engines and asynchronous loggers use it. The costs: the ring is fixed size, so you must decide what happens when it fills; events are recycled, so holding a reference past your handler is a correctness bug; and busy-spinning costs a whole core per consumer.

### LongAdder
**Short:** Striped, padded counter that beats AtomicLong under heavy contention by avoiding cache-line ping-pong.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### LongStream
**Short:** Primitive long stream avoiding Long boxing, with range, sum and summary-statistics terminal operations.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### lsof
**Short:** Unix tool listing open files, sockets and their owning processes; the fix for FD leaks and port conflicts.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/memory-processes-and-os @2

It walks every process's file-descriptor table and prints one row per open object with the owning process and user, the descriptor number, its type and its name. Because on Unix nearly everything is a file descriptor, that listing covers regular files, pipes, sockets, memory-mapped libraries and deleted-but-still-open inodes alike, and it filters by address, by process or by directory.

Two situations make it indispensable. A too-many-open-files error is a descriptor leak, and counting a process's rows against its limit shows which kind is accumulating, usually sockets stuck in CLOSE_WAIT because the code never closed its side. And a filesystem reporting full while directory sizes account for nothing is a deleted file still held open, which shows here marked deleted. It is slow on a large host and needs root to see other users.

### lsquic
**Short:** LiteSpeed's C implementation of QUIC and HTTP/3, embeddable as a client or server transport library.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @2

It implements QUIC and HTTP/3 as an engine you drive rather than a server you run: it owns no sockets and no event loop, so the embedding application reads UDP datagrams and feeds them in, and the library calls back when it has packets to send and when to arm a timer. That design lets it drop into an existing server architecture without dictating its threading model.

Reach for it when adding HTTP/3 or a QUIC-based protocol to a C or C++ program and you want a stack that carries production traffic. The cost is that the integration work is substantial and yours: the UDP socket, the timer wheel, and routing by connection identifier across a load balancer so migrated connections land on the right instance. For a client, a curl build with HTTP/3 support is far less work.

### lstopo
**Short:** hwloc CLI that draws the machine's CPU, cache and NUMA topology so you can pin threads sensibly.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It renders the topology hwloc discovered as nested boxes: the machine, NUMA nodes with their memory sizes, packages, the L3 shared by a group of cores, then L2 and L1 per core, and finally the SMT threads inside each. It opens a graphical window by default, prints an ASCII tree when run without graphics, which is the form you want over ssh, and writes an image for a runbook.

Look at it before making any pinning decision, because two facts are only visible here. Which logical CPUs are hyperthread siblings of one physical core determines whether pinning a producer and consumer pair gives you two cores or half of one, and which cores share an L3 determines whether a pool shares cache or pays cross-socket latency. Inside a container it shows only the cpuset you were granted.

### ltrace
**Short:** Linux tracer that prints a process's library and system calls, answering why a process appears stuck.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @2

It works one layer above `strace`: instead of intercepting system calls it intercepts calls into dynamically linked shared libraries by manipulating the procedure linkage table, printing each function with its arguments and return value, and a flag interleaves the system calls so you see both layers. That answers questions `strace` cannot, such as which TLS library function a process is sitting inside.

Reach for it when a process is stuck inside a library you have no source-level visibility into. Its limits are real: it only sees calls crossing a dynamic library boundary, so a statically linked or inlined binary shows nothing. Argument decoding depends on prototypes it may not have, so pointers frequently print as addresses. Overhead is heavier than `strace`, and eBPF tooling gives the same visibility far more cheaply.

### madvise
**Short:** Linux syscall hinting the kernel about a mapping's access pattern, e.g. MADV_SEQUENTIAL or MADV_WILLNEED.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/io-networking-and-syscalls @2

### Map.computeIfAbsent
**Short:** Atomic get-or-compute on a Java Map, the idiomatic way to build a memo table or lazily initialize a value.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, caching/in-process-cache @3

### Map.getOrDefault
**Short:** Java Map method returning a fallback when the key is absent, avoiding null checks in counting loops.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Map.merge
**Short:** JDK Map method inserting a value or applying a remapping function to the existing one; the idiomatic counter.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### match/case
**Short:** Python 3.10 structural pattern matching: destructures a value by shape and binds names, well beyond a switch.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### Matcher.quoteReplacement
**Short:** Escapes $ and backslash in a regex replacement string, so untrusted text cannot inject group references.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### math.comb
**Short:** Python stdlib exact binomial coefficient, the combinatorics primitive for counting and probability problems.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### math.perm
**Short:** Python stdlib exact permutation count P(n, k); pairs with math.comb for combinatorics without overflow.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Matplotlib
**Short:** Python's foundational plotting library: figures, axes and every static chart type used in analysis and reports.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @3

It has two layers. A figure owns a set of axes; each axes owns its scales and ticks and a list of artists, the lines, patches, text and images that a backend rasterizes to PNG or emits as vectors. The `pyplot` module is a thin stateful wrapper tracking a current figure, which makes a quick plot a one-liner, while creating the figure and axes explicitly is what you want in anything reusable.

Reach for it when you need exact control and a static, publication-quality artifact, since every element is addressable and the output is deterministic. The costs are a large and historically inconsistent API, defaults that need work to look modern, and rendering that slows past tens of thousands of points. Seaborn sits on top for statistical plots in fewer calls, and Plotly or Altair answer the need for interactivity.

### MemoryLayout
**Short:** Panama FFM type describing a C struct, union or sequence so Java reads native memory at correct offsets.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/runtime-internals-and-types @2

### MemorySegment
**Short:** Java FFM API type giving a bounded, typed, lifetime-scoped view over native, heap or mapped memory.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/io-networking-and-syscalls @3

### MinHash
**Short:** Locality-sensitive hashing sketch estimating Jaccard similarity; the standard near-duplicate corpus dedup method.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, search-retrieval/rag-and-document-processing @2, ml-lifecycle/labeling-and-synthetic-data @3

Represent each document as a set of shingles, meaning overlapping n-grams. Apply k independent hash functions and keep the minimum value produced by each. The probability that two sets share the same minimum under a given hash is exactly their Jaccard similarity, so the fraction of the k positions on which two documents agree estimates it without bias. Every document collapses to a fixed-size signature regardless of length.

What makes it work at scale is banding: split the signature into bands of several rows, hash each band, and treat any pair colliding in a band as a candidate. That is locality-sensitive hashing, and it turns an all-pairs comparison into a lookup, which is how pretraining corpora are deduplicated. The costs: it is blind to word order and paraphrase, and the band and row choice implicitly sets a similarity threshold.

### MiniSat
**Short:** The minimal reference CDCL SAT solver whose small, readable C++ source is the template most modern solvers descend from.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

It implements conflict-driven clause learning in a couple of thousand lines: assign a
variable, propagate forced consequences using two watched literals per clause, and on a
falsified clause analyse the implication graph, learn a clause that explains the conflict, add
it and backjump non-chronologically rather than undoing one level. Activity-based branching,
periodic restarts and clause-database reduction are all present in the form later solvers
refined.

Its importance today is pedagogical and architectural rather than competitive: it is the code
people read to understand CDCL, and its interface is one many tools still expose. For a hard
instance reach for a modern descendant such as CaDiCaL or Kissat; MiniSat's value is that you
can read the whole thing in an afternoon.

### mlock
**Short:** Syscall pinning pages in physical RAM so they are never swapped; needs root or CAP_IPC_LOCK.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### mlockall
**Short:** Linux syscall that pins a process's pages in RAM so they are never swapped; needs root or CAP_IPC_LOCK.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### module-info.java
**Short:** JPMS module declaration listing a module's name and its requires, exports, opens, uses and provides clauses.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/build-and-dependency-management @2

### more-itertools
**Short:** Python library of 170+ iterator recipes (chunked, windowed, partition, peekable) beyond what itertools ships.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

It is a pure-Python library built on `itertools` shipping both the recipes from the standard library documentation and many more, staying lazy wherever possible. The ones you reach for repeatedly are `chunked` for batching, `windowed` for sliding windows, `peekable` for looking ahead without consuming, `partition` for dividing on a predicate, `unique_everseen`, `first` and `one` with sensible errors, and `collapse` for nesting.

Reach for it whenever you are about to write a small iterator helper, because the version here is tested, lazy and named the same thing the rest of the ecosystem uses. Two costs: it is pure Python, so per-element overhead is real in a hot numeric loop, and the surface is large enough that you should check the standard library first, since `itertools.batched` arrived in Python 3.12.

### mpstat
**Short:** sysstat CLI printing per-CPU utilization breakdowns over time; first stop for CPU saturation and core imbalance.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

It is sysstat's per-CPU reporter. Asking for all processors at a one-second interval prints, for every logical CPU, the split of time into user, system, iowait, hard and soft interrupt, steal and idle. As with the rest of sysstat the first sample is the average since boot and should be discarded.

The reason to use it rather than the aggregate in `top` is that averaging hides the two most common CPU pathologies. One core pinned at a hundred percent while the rest idle means single-threaded work or an interrupt bound to one CPU, and on a sixteen-core box that reads as six percent system-wide. And nonzero steal on a virtual machine means the hypervisor is not giving you the CPU you pay for, which no application tuning fixes.

### msquic
**Short:** Microsoft's cross-platform C implementation of the QUIC transport, used as the base for HTTP/3 stacks.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @3

It is a cross-platform QUIC implementation in C with a callback-driven API over connections and streams. Unlike the library-only stacks it owns its own UDP sockets, worker threads and datapath, using platform features such as segmentation offload for throughput, and delegates only the handshake to a TLS provider. It is the QUIC layer beneath Windows' HTTP stack and .NET's HTTP/3 APIs, and it is MIT licensed.

Reach for it to add HTTP/3 or a custom QUIC protocol to a native or .NET application, particularly on Windows where it is the supported path. The costs are QUIC's rather than the library's: congestion control runs in user space, so CPU per gigabit is well above kernel TCP; middleboxes block UDP on port 443 often enough that a TCP fallback is mandatory; and connection migration complicates load balancing.

### mtr
**Short:** Combined traceroute and ping that continuously reports per-hop loss and latency along a network path.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

Rather than one pass like traceroute, it probes continuously and keeps a live table of every hop with packets sent, percentage lost and best, average and worst round-trip time. That is what exposes intermittent problems: a path that looks fine in a single traceroute shows two percent loss at one hop after a minute of sampling.

Read the loss column carefully, because the most common misreading is treating loss at a middle hop as the fault. Routers deprioritize or rate-limit the ICMP replies they generate for themselves while forwarding traffic perfectly, so loss that appears at one hop and vanishes at later hops is an artifact; only loss that persists all the way to the final hop is real. Reach for it when a service is intermittently slow and you need to establish whether the network path or the application is responsible.

### multiprocessing
**Short:** Python stdlib module giving true CPU-bound parallelism by running each worker in its own process, sidestepping the GIL.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### multiprocessing.Pool
**Short:** Python stdlib pool of worker processes for CPU-bound parallelism, sidestepping the GIL at the cost of pickling.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### multiprocessing.Queue
**Short:** Pickle-based IPC queue moving objects between Python processes over a pipe with a feeder thread.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### multiprocessing.shared_memory
**Short:** Python API for a named shared-memory block, letting processes exchange large arrays without pickling them.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/concurrency-and-async @2

### multiprocessing.Value/Array
**Short:** Shared-memory primitives for multiprocessing: a single value or array visible to all child processes.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### nc
**Short:** Netcat: opens raw TCP or UDP connections from the shell, the quickest port-reachability and protocol test.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

It connects standard input and output to a TCP or UDP socket, either dialling out or listening, which turns a shell pipeline into a network client or a throwaway server. That is enough to hand-type an HTTP exchange and read the raw reply, to test whether a port accepts a connection at all, to pipe a file between two hosts, or to stand up a fake backend and see exactly what a client sends.

Reach for it as the layer beneath curl, because it proves TCP reachability with no HTTP client's interpretation in the way, separating a firewall drop, nothing listening, and an application answering badly. Two cautions: several incompatible implementations exist whose flags differ; and a build with the option to execute a program on connect is a remote shell, which is why it is often absent from hardened images.

### netstat
**Short:** Classic CLI that lists sockets, listening ports, connection states, TIME_WAIT counts and listen-queue depth.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

`netstat -an` lists every socket with local and remote address and TCP state, which is how you see the states that explain a failure: a pile of `TIME_WAIT` after a burst of short-lived connections, `CLOSE_WAIT` accumulating because the application never closed its side of a connection the peer already closed, or a listening socket whose accept queue is full because the process is not accepting fast enough. It is usually the first command to run when the service is up but connections are failing or hanging.

On Linux it is superseded by `ss` from iproute2, which reads socket state over netlink instead of parsing `/proc` and is dramatically faster on a host with many connections; the flags are close enough that `ss -tan` reads the same as `netstat -tan`. Learn to read the states themselves - that knowledge transfers to whichever tool is installed.

### Netty
**Short:** Asynchronous NIO network framework underlying WebFlux, gRPC-Java and high-throughput WebSocket/UDP/QUIC servers.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @2, runtime-systems/concurrency-and-async @3, apis-frameworks/web-framework-and-http-client @3

Netty gives you event loop groups, a `Channel` per connection, and a `ChannelPipeline` of handlers through which inbound bytes and outbound messages flow -- so a protocol is built by composing decoders, encoders and business handlers instead of managing selectors and buffers by hand. Its pooled, reference-counted `ByteBuf` avoids per-message allocation and allows zero-copy composition, which is why it holds up at connection counts where thread-per-socket blocking I/O collapses. It ships implementations of HTTP/1 and HTTP/2, WebSocket, TLS, DNS and UDP, plus native epoll and kqueue transports that beat plain NIO on their platforms.

You rarely pick it directly -- it sits under Reactor Netty and Spring WebFlux, gRPC-Java, Cassandra and Elasticsearch -- but reach for it when implementing a custom or binary protocol. The two things to internalise first are that a blocking call inside a handler stalls every connection sharing that event loop, and that reference-counted buffers must be released or they leak.

### Netty ChannelPipeline
**Short:** Netty's per-connection handler chain, separating decode, framing and business logic into reconfigurable stages.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2, runtime-systems/concurrency-and-async @3, apis-frameworks/aop-middleware-and-scheduling @3

### Netty PooledByteBufAllocator
**Short:** Netty's pooled reference-counted buffer allocator, reusing direct buffers instead of allocating per message.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/memory-processes-and-os @2, apis-frameworks/design-patterns-and-principles @3

### NetworkX
**Short:** Pure-Python graph library: BFS/DFS, shortest paths, MST, topological sort; prototyping, not scale.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, applied-ml/recommenders-and-graph-ml @2, data-stores/graph-db @3

Graphs are dictionaries of dictionaries, so nodes can be any hashable Python object and both nodes and edges carry arbitrary attributes, which makes modelling a domain almost free. The algorithm coverage is the other draw: traversal, shortest paths, minimum spanning trees, centrality measures, community detection, matching and flow, all with a consistent interface.

That flexibility is also the cost. It is pure Python with substantial memory per node and edge, so graphs in the hundreds of thousands of edges get slow and graphs in the millions are impractical. Reach for it for prototyping, teaching and analysis at modest scale, then move to a compiled library such as igraph when the same analysis needs to be fast, or to a graph database when the graph has to be persistent, shared and queried.

### nftables
**Short:** The modern Linux packet filtering and NAT framework that replaces iptables, with one unified rule syntax.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/supply-chain-and-runtime-security @3, traffic-edge/proxy-and-load-balancer @3

It replaces the four separate iptables tools with one command and one syntax over a single kernel subsystem. Tables and chains are created by you rather than being fixed, a chain declares which netfilter hook it attaches to and at what priority, and rules compile into bytecode run by a small in-kernel virtual machine. The structural improvement is sets and maps: one lookup replaces a linear chain of thousands of rules, and sets update atomically while traffic flows.

It is the kernel's supported implementation now, and a compatibility layer translates legacy syntax onto it, so most systems already run nftables whether anyone chose it or not. Reach for the native syntax when writing a firewall from scratch, especially with large or frequently changing address sets. The costs are that the syntax is a genuine rewrite, and that mixing legacy and native rules on one host produces evaluation-order surprises.

### nghttp2
**Short:** HTTP/2 C library shipping client, server and debug CLIs for inspecting frames, streams and HPACK on the wire.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @2, apis-frameworks/rpc-graphql-and-streaming @3

The C library implements the HTTP/2 protocol state machine only, meaning frames, streams, flow control and HPACK, and calls back into the application to read and write bytes, so it embeds under any I/O model. That is why curl and several language runtimes build on it. Alongside it ship the tools that are the practical reason to know the name: a verbose client that prints every frame, a test server, a benchmarking client and a translating proxy.

Reach for the verbose client when curl says a request failed and you need frame-level truth: which SETTINGS the peer announced, how the flow-control windows moved, whether a stream reset or GOAWAY arrived and with what error code. That is the layer at which complaints about HTTP/2 being slow through a proxy resolve, typically as a small initial window or a low concurrent-stream limit at one hop.

### nghttp3
**Short:** C library implementing HTTP/3 framing and QPACK on top of a QUIC transport such as ngtcp2.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @3

It implements only the HTTP/3 layer: the control and request streams, frame encoding, and QPACK header compression with its separate encoder and decoder streams. It expects a QUIC transport underneath, normally ngtcp2, to provide the streams. The split is deliberate, because HTTP/3 is essentially HTTP/2's semantics remapped onto QUIC's streams, while loss recovery, congestion control and migration are a separate concern.

You usually meet it as a dependency rather than a decision, since curl's HTTP/3 support is built on nghttp3 plus ngtcp2 and that pairing is the reference stack people test against. Reach for it directly only when building an HTTP/3 endpoint in C where you already own a QUIC transport. The costs are integration: two libraries plus a TLS library exposing the QUIC handshake interface, which is why single-package stacks exist.

### ngtcp2
**Short:** C library implementing the QUIC transport protocol, pairing with a separate TLS library for HTTP/3 stacks.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @3

It implements the QUIC transport itself: the handshake driven by an external TLS 1.3 library, separate packet number spaces, loss detection and congestion control, stream multiplexing and flow control, connection identifiers and migration, and zero round-trip resumption. What it deliberately does not own is sockets, an event loop or cryptography, so the application feeds it datagrams and timer expiries and receives packets to send.

The pairing to remember is ngtcp2 for transport plus nghttp3 for HTTP/3, which is how curl speaks HTTP/3. Reach for it when you need QUIC in C and want control of the I/O path. The cost is glue code: the socket, the timer wheel, demultiplexing by connection identifier behind a load balancer, and the TLS integration, which together is more work than an all-in-one stack. QUIC also costs more CPU per byte than kernel TCP.

### nice
**Short:** POSIX command setting a process's scheduling niceness (-20 to +19); without root a user can only lower its own priority.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### nmap
**Short:** Network scanner for host discovery, port scanning, service fingerprinting and OS detection.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/supply-chain-and-runtime-security @2

It sends crafted packets and infers state from what comes back: a SYN scan tells open from closed from filtered, `-sV` matches banners and probe responses to a service and version, `-O` fingerprints the operating system from TCP/IP stack quirks, and NSE scripts run deeper checks such as enumerating TLS ciphers or known weak configurations.

Reach for it to verify what a host actually exposes against what the security group or firewall rule claims -- the two disagree more often than anyone expects. Scanning infrastructure you do not own or have written permission to test is hostile traffic and in many jurisdictions illegal.

### node --trace-opt --trace-deopt
**Short:** Node flags that log V8 TurboFan optimization and deoptimization decisions, showing which functions keep bailing out.
**Kind:** api
**Lang:** js
**Roles:** runtime-systems/runtime-internals-and-types @1, observability/profiling-and-performance @2

### nsenter
**Short:** Linux utility that enters another process's namespaces; the way to debug a distroless container from the host.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, platform-delivery/container-and-image @2

Given a target process id it opens that process's namespace files under `/proc`, calls `setns` for each namespace you select, network, mount, PID, UTS or IPC, then executes a program inside them. The program itself comes from wherever you invoked it, which is the whole point: you run the host's tools inside a container that contains none of them.

That is the answer to debugging a distroless image. Entering only the network namespace gives you the container's network view while keeping the host's filesystem, whereas also entering the mount namespace hands back the empty filesystem you were trying to escape. The costs are that it needs root on the host and effectively grants container access from outside, so it is a break-glass tool; an ephemeral debug container is the auditable equivalent.

### nslookup
**Short:** CLI DNS query tool for resolving names and debugging resolver, record and cluster-DNS problems.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, traffic-edge/service-mesh-and-discovery @2

It queries a DNS resolver directly instead of going through the C library's `getaddrinfo`, so it shows what a resolver returns for a given name and record type, and you can aim it at a specific server to prove whether a stale or split-horizon answer is the problem. In Kubernetes it is the standard way to test cluster DNS from inside a pod by resolving a service's `svc.cluster.local` name, which separates "the service has no endpoints" from "DNS is broken". Because it bypasses the resolver library, a good answer here does not guarantee the application resolves the same way, since `/etc/hosts` and NSS configuration are not consulted. `dig` shows more detail and is preferred wherever it is installed.

### ntohl
**Short:** POSIX call converting a 32-bit integer from network byte order to host byte order when parsing wire protocols.
**Kind:** api
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1

### numactl
**Short:** Linux CLI that pins a process's CPUs and memory to NUMA nodes and reports the machine's NUMA topology.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

On a multi-socket machine each CPU socket has memory attached directly to it, and reaching another socket's memory crosses the interconnect at measurably higher latency and lower bandwidth. Printing the hardware view shows the nodes, their memory and the distance matrix, while binding a process to a node keeps its threads and its pages together — the standard fix for a latency-sensitive service that the scheduler spread across sockets.

Interleaving is the opposite choice, spreading pages across all nodes for a large shared heap no single node can hold, trading locality for even bandwidth. Measure before and after, and note that none of this applies on a single-socket machine.

### numastat
**Short:** Linux CLI reporting per-NUMA-node allocation and hit/miss counts; shows memory landing on a remote node.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

It prints the kernel's per-node allocation counters: hits, where an allocation was satisfied on the node the process asked for, misses and foreign allocations where it wanted one node and was served by another, and the local and remote totals. With a process argument it switches to a per-process view of resident memory by node, the picture that shows one process's pages split across sockets.

Use it to confirm or refute a NUMA hypothesis before you start pinning anything. Rising misses mean memory pressure is pushing allocations to a remote node, and a supposedly pinned service with most of its resident set on the other node means either the binding did not take or the memory was touched first, since Linux allocates a page on the node that writes to it. Difference two samples, since the counters are cumulative.

### NumberFormat
**Short:** java.text formatter for locale-aware numbers, currency and percentages; instances are not thread-safe.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, apis-frameworks/web-framework-and-http-client @3

### NumPy
**Short:** Python's n-dimensional array and linear-algebra library; releases the GIL in its C layer.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @3

An `ndarray` is one contiguous typed buffer plus a shape and a stride tuple, so slicing and reshaping produce views over the same memory and a whole-array operation runs as a single loop in C rather than a Python loop over boxed objects. That is where the speed comes from and it is also the discipline: the moment you write a `for` over elements you have given it back. Linear algebra dispatches to a BLAS or LAPACK backend, and because those loops release the GIL, numeric work in threads genuinely runs in parallel — one of the few places threading helps in CPython.

Essentially every other array library in Python builds on it or copies its interface, which is why learning its broadcasting and dtype-promotion rules properly pays off once and forever.

### NumPy 2D arrays
**Short:** Contiguous 2D ndarrays used for large DP tables; roughly 10x faster than nested Python lists for numeric work.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### NumPy default_rng
**Short:** NumPy's modern PCG64 random-generator factory; the recommended seedable replacement for legacy np.random calls.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### numpy order parameter
**Short:** NumPy argument choosing C row-major or Fortran column-major memory layout, which decides cache behaviour.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/memory-processes-and-os @2

### numpy.linalg.slogdet
**Short:** Returns sign and log-magnitude of a determinant, the stable form used in Gaussian log-likelihoods.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### numpy.ndarray
**Short:** NumPy's contiguous typed N-dimensional array, the substrate for vectorized BLAS/LAPACK numeric work in Python.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Objects.hash
**Short:** JDK helper that composes a null-safe hashCode from several fields, matching an equals implementation.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/runtime-internals-and-types @3

### Objects.requireNonNull
**Short:** JDK helper that throws immediately on a null argument, so the failure points at the caller not a later NPE.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @3

### Objenesis
**Short:** Instantiates classes without calling a constructor, which is how CGLIB proxies and mocks avoid a no-arg requirement.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/aop-middleware-and-scheduling @2, devtools/compiler-toolchain-and-codegen @3

It allocates an instance of a class without running any constructor, choosing a strategy at run time for the JVM it finds itself on, typically the serialization machinery's constructor factory or a low-level allocation intrinsic. The resulting object has all fields at their default values, so no initialization logic, no validation and no constructor side effects have run.

That is exactly what proxying and mocking libraries need: Mockito and the CGLIB proxies behind Spring must produce a subclass instance without knowing which constructor arguments to pass, and serialization frameworks such as Kryo need it for the same reason. The cost is that such an object has skipped every invariant its constructor established, so final fields are null, making it safe only when something else populates the object immediately afterwards.

### openssl s_client
**Short:** OpenSSL CLI that opens a TLS connection and dumps the handshake, cipher, certificate chain and expiry.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/secrets-and-cryptography @2

Running `openssl s_client -connect host:443 -servername host` opens a real TLS connection and prints the negotiated protocol version and cipher, the certificate chain the server actually presented, the verification result, and then leaves the socket open so you can type an HTTP request into it. `-showcerts` dumps the full chain, which is how you catch a server sending a leaf without its intermediate -- the classic "works in my browser, fails in the JVM" bug.

Reach for it when a client reports a TLS failure and you need the server's real view rather than a monitoring summary: expiry, chain order, SNI selection, protocol and cipher negotiation. It is a debugging tool for one endpoint, not a scanner.

### operator
**Short:** Python stdlib module of function forms of the operators, e.g. itemgetter and attrgetter as sort and map keys.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### OR-Tools CP-SAT
**Short:** Google OR-Tools' constraint-programming solver: it encodes the model to SAT and runs clause learning beside global propagators.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

You declare integer variables over finite domains, constraints relating them — linear
constraints, reification, and global ones such as `AllDifferent`, `NoOverlap` and `Cumulative`
— and an objective. The model is then encoded into a satisfiability problem and solved by
clause learning running alongside dedicated propagators for the global constraints, with a
portfolio of differently configured workers searching in parallel. It reports a status, the
objective value and a bound, so the remaining gap is always visible.

Reach for it for scheduling, rostering, packing and assignment, where constraints are
combinatorial rather than smoothly linear; it is Apache-licensed and has Python, Java, C++ and
C# APIs. Two caveats decide whether it works: modelling is the real effort, since how a
constraint is expressed determines whether an instance solves in seconds or never, and
variables are integer only, so continuous quantities must be scaled.

### OutputStreamWriter
**Short:** JDK bridge presenting a byte OutputStream as a character Writer using a charset; the canonical adapter example.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/io-networking-and-syscalls @2, apis-frameworks/design-patterns-and-principles @2

### Path.of()
**Short:** NIO factory creating a Path from string segments; the modern replacement for Paths.get().
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1

### Pattern.compile
**Short:** Compiles a Java regex; with UNICODE_CHARACTER_CLASS, \d and \w match Unicode rather than ASCII.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### Pattern.quote
**Short:** Escapes user-supplied text so it is matched literally inside a regex, the pattern-side counterpart to quoteReplacement.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### PEP 479
**Short:** Python standard making a StopIteration that escapes a generator raise RuntimeError instead of ending it.
**Kind:** spec
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/concurrency-and-async @3

Before this change, a `StopIteration` raised anywhere inside a generator body was indistinguishable from the generator returning normally, including one that escaped from a nested `next` call. The consuming loop simply stopped, quietly and successfully, so a bug deep inside a pipeline truncated the data with no error anywhere. The rule makes the machinery catch such an exception and re-raise it as a `RuntimeError` chained to the original.

It arrived behind a future import in Python 3.5 and became unconditional in 3.7, and the same reasoning was applied to asynchronous generators. The practical consequence is that a bare `next(iterator)` inside a generator must be given a default or have its exception caught explicitly. That migration was mildly annoying and removed an entire class of incident where a consumer silently received a short result.

### pgrep
**Short:** Linux CLI that finds process IDs by name, user or other attributes for scripting and quick lookups.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It matches processes by an extended regular expression against the process name, or against the full command line when asked, and prints the matching process ids, with selectors for user, parent, session and terminal and options to print the command line or count matches.

It exists to replace the fragile pipeline of listing processes, grepping, filtering out the grep itself and cutting a column. Its sibling `pkill` takes the same selectors and sends a signal. Two cautions save real incidents. The default matches only the first fifteen characters of the process name, taken from the kernel's truncated field, which is why matching the full command line is often necessary for anything launched through an interpreter. And a loose pattern can select far more than intended.

### ping
**Short:** ICMP echo utility for testing host reachability and round-trip latency.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

It sends ICMP echo requests and reports the replies, one line per packet with a sequence number and a round-trip time, then a summary with loss and minimum, average and maximum. The sequence numbers make it diagnostic: gaps show exactly which packets went missing and out-of-order arrivals show reordering. Setting the do-not-fragment bit while varying payload size is how you bisect a path MTU smaller than you think.

It answers exactly one question, whether an IP path works and with what latency and loss, which is why it is the first command and rarely the last. A host that does not answer may be healthy behind a policy dropping ICMP, and one that answers may have every application port closed. Routers also deprioritize the ICMP they generate themselves, so latency to an intermediate device can look far worse than traffic passing through it.

### Pingouin
**Short:** User-friendly Python statistics package: hypothesis tests, effect sizes, power analysis and tidy result tables.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @3

It is built on pandas and SciPy, and its distinguishing choice is that every test returns a tidy DataFrame containing not merely the statistic and p-value but what a careful reader asks for and SciPy omits: the effect size, a confidence interval, degrees of freedom, achieved power, and for several tests a Bayes factor. The catalogue covers t-tests, analysis of variance including repeated-measures designs, post-hoc comparisons and correlation variants.

Reach for it when doing conventional inferential statistics and you want the complete result from one call rather than assembling effect sizes and intervals around `scipy.stats` yourself, which is where reporting errors creep in. The costs are that it is a smaller project than statsmodels and does not attempt general regression modelling, time series or econometrics, and that its convenience does nothing to tell you which test the design calls for.

### pmap -x <pid>
**Short:** Linux command listing every memory mapping of a process - text, data, heap, stack, mmap - with resident sizes.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### POSIX pthread_mutex_t
**Short:** The POSIX C mutex type, with attributes such as PTHREAD_PRIO_INHERIT to avoid priority inversion.
**Kind:** api
**Lang:** cpp
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

### ProcessPoolExecutor
**Short:** Pool of worker processes executing submitted callables in parallel, sidestepping the GIL at IPC cost.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### Project Reactor
**Short:** Reactive Streams library for the JVM: Mono/Flux, operators, schedulers and backpressure; powers Spring WebFlux.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/web-framework-and-http-client @3

`Mono` carries zero or one value and `Flux` zero to many, and both are cold: building a chain of `map`, `flatMap`, `zip`, `timeout` and `retryWhen` only describes the pipeline, and nothing executes until something subscribes. Backpressure is part of the Reactive Streams contract, so a slow consumer requests fewer elements rather than being flooded, and `subscribeOn`/`publishOn` decide which scheduler each segment of the chain runs on.

It is the engine under Spring WebFlux and the reactive Spring Data drivers, where a handful of event-loop threads carry a large number of concurrent connections because no thread is parked waiting on I/O. The costs are real and worth stating plainly: one blocking call anywhere in a chain stalls an event-loop thread and can take the service down, stack traces are hard to read without `checkpoint()` or the debug agent, and on Java 21 and later virtual threads deliver much of the same concurrency with ordinary blocking code and none of that.

### Project Reactor Flux
**Short:** Reactor's 0..N reactive stream type: push-based with backpressure and composable map/filter/retry operators.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/rpc-graphql-and-streaming @3

### Proxy.newProxyInstance
**Short:** Creates a JDK dynamic proxy implementing given interfaces and routing every call to an InvocationHandler.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/aop-middleware-and-scheduling @2

### ps aux
**Short:** Standard Unix process listing showing every process with its PID, owner, CPU and memory share, and full command line.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### pycache
**Short:** The __pycache__ directory where CPython caches compiled .pyc bytecode to skip re-parsing on the next import.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

### pyitlib
**Short:** Python library of 19 discrete information measures (entropy, mutual information, JS divergence) with bias correction.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, applied-ml/interpretability-fairness-and-causal @3

It computes discrete information-theoretic quantities from sample data rather than from an explicit distribution: entropy, joint and conditional entropy, mutual and conditional mutual information, divergences and variation of information, over NumPy arrays of discrete symbols. Its distinguishing feature is a choice of estimators that correct the bias plain frequency counting introduces, rather than only the maximum-likelihood estimate.

That bias correction is the reason to use it instead of counting frequencies yourself. With finite samples the naive entropy estimate is systematically too low and the naive mutual information too high, and the error grows with alphabet size, so two genuinely independent variables with many levels will show a confident and entirely spurious dependence. The costs are that everything must be discretized first and the binning choice usually changes the answer more than the estimator does.

### pyparsing
**Short:** Python library for building recursive-descent grammars in code, an alternative to unreadable regexes.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/compiler-toolchain-and-codegen @2

A grammar is built by composing Python objects rather than writing a grammar file: literals, character-class words, regexes, optionals, repetitions, groups and a forward declaration for recursion, combined with operators for sequence and alternation. Parse actions attach a callback to a matched element so it is transformed as it is recognized, which is how an evaluator or a syntax tree falls out of the grammar itself rather than a second pass.

Reach for it when a format has real structure, meaning nesting, recursion or operator precedence, because that is precisely what a regular expression cannot express and where a regex-based parser becomes a liability. The costs are that it is pure Python and considerably slower than a compiled parser, so it is wrong for gigabytes of log lines, and that for a genuine language a parser generator gives a readable grammar and better error messages.

### Python
**Short:** General-purpose interpreted language; the default escalation when shell automation outgrows shell scripts.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/version-control-and-workbench @3

CPython compiles source to bytecode, caches it so imports skip re-parsing, and executes it in an interpreter loop over a stack machine. Objects are reference counted with a cyclic collector behind them, so most memory is freed deterministically. Until the free-threaded builds a global interpreter lock served one thread of bytecode at a time, which explains the concurrency advice: threads suit I/O because the lock is released while waiting, while CPU-bound parallelism needs processes.

As the successor to a shell script it wins the moment the task needs data structures, error handling or anything beyond string pipelines, and its standard library covers most of what automation reaches for with no dependency. Two costs decide when a shell script is still right: interpreter startup is measurably slower than a small binary, and anything importing third-party packages needs an environment on the target machine.

### python -m asyncio
**Short:** Stdlib async REPL that starts with a running event loop so top-level await works interactively.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, devtools/version-control-and-workbench @2

### python -m asyncio ps/pstree
**Short:** Python 3.14 CLI attaching to a running PID to print its live await graph, without restarting the process.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

### Python built-ins
**Short:** The always-available Python functions and types (map, filter, zip, sorted, enumerate) used for iterator pipelines.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### python-dateutil
**Short:** Python date/time extension providing flexible ISO 8601 and fuzzy parsing, relative deltas and tz database access.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

It adds four things the standard library historically lacked. A parser that reads almost any written date, with flags to resolve day-first and year-first ambiguity. A relative delta performing calendar arithmetic a fixed-duration timedelta cannot, such as adding one month or jumping to the next Friday. An implementation of the iCalendar recurrence rule. And time zone objects, which predated the standard library having any.

Reach for it for the parser and the relative delta, which still have no standard equivalent. Stop reaching for the time zones, since `zoneinfo` is the right source now, and stop using the fuzzy parser on machine-generated input, where `datetime.fromisoformat` is far faster and raises on a malformed value rather than guessing. Guessing is the real cost: given an ambiguous numeric date it will be silently wrong for half the world's conventions.

### python3.14t
**Short:** The free-threaded CPython 3.14 build with the GIL removed, so CPU-bound Python threads run in parallel.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/concurrency-and-async @2

The trailing letter marks the free-threaded build, in which the global interpreter lock is removed and reference counting is made safe by other means, including biased reference counting and immortal objects, with fine-grained locks where internal structures require them. Threads therefore execute bytecode genuinely in parallel across cores. It is a separate binary with a separate ABI, so extension modules must be rebuilt and declare support or the lock is re-enabled at import.

Reach for it when work is CPU-bound, shares a large in-memory structure that would be expensive to pickle into worker processes, and the dependency stack has free-threaded wheels. The costs are real: single-threaded performance is somewhat lower because reference counting costs more per operation, the wheel ecosystem is still catching up, and code that quietly relied on the lock for atomicity is now a genuine race needing an explicit lock.

### queue.Queue
**Short:** Python's thread-safe in-process FIFO with blocking put/get, the standard producer-consumer handoff.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @2, data-movement/task-queue-and-jobs @3

### quiche
**Short:** Cloudflare's Rust QUIC and HTTP/3 implementation with a C API for embedding in other stacks.
**Kind:** tech
**Lang:** rust
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @3

It is structured as a state machine with no I/O of its own: you hand it received UDP datagrams and it produces datagrams to send plus the deadline at which to call it back, so it drops into whatever event loop the host program already runs. Alongside the Rust crate it exposes a C API, which is how it is embedded into nginx through Cloudflare's patch, and it uses BoringSSL for the TLS 1.3 handshake.

Reach for it when you want a QUIC stack that carries a very large share of a major CDN's edge traffic while keeping control of the I/O path. The costs are the standard QUIC ones plus integration: you own the socket, the timers and connection-identifier routing so a migrated connection reaches the right instance, user-space congestion control burns more CPU per gigabit than kernel TCP, and public deployments need a plan for networks blocking UDP.

### quicklist
**Short:** Redis's doubly-linked list of listpack nodes, with a per-node byte cap and optional compression of the middle nodes.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-stores/key-value-and-embedded @2

A plain linked list pays two pointers and an allocation per element, which is ruinous for a million small items; one giant contiguous array pays a full memory move on every push. A quicklist splits the difference by chaining bounded compact nodes, so pushes and pops touch only the node at one end and memory overhead is amortised across the whole node.

Because a queue only ever touches the two ends, the middle nodes can be compressed and never decompressed on the hot path, which can shrink a large backlog dramatically. That setting is exactly wrong for indexed access into the middle, where every read would decompress a node.

### random
**Short:** Python stdlib pseudo-random generator used for sampling, shuffling and Monte Carlo simulation.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### re
**Short:** Python's standard-library regular expression module for matching, searching, splitting and substitution.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

### RE2
**Short:** Google's automaton-simulating regex engine: linear-time matching, immune to catastrophic backtracking.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/text-encoding-and-regex @1

It takes seriously the fact that a regular expression describes a regular language, which means it can be recognized by a finite automaton advancing through the input once, keeping track of the set of states currently reachable. Time is therefore bounded by input length times pattern size regardless of how the quantifiers nest, which removes the exponential blowup a backtracking engine suffers on nested repetition over a long non-matching string.

The rule of thumb is simple: if either the pattern or the input comes from outside your program, use an engine with this guarantee, because otherwise a single crafted input is a CPU exhaustion attack against the thread evaluating it. The price is that backreferences and lookaround are not regular constructs and are unsupported, and a pattern genuinely needing them is usually a parser in disguise. Go's regexp package applies the same design.

### RE2/J
**Short:** Java port of RE2 giving linear-time regex matching - no catastrophic backtracking - at the cost of lookaround.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, traffic-edge/rate-limiting-and-resilience @3

It is a pure-Java reimplementation, so there is no native library to ship and no JNI boundary, and its surface deliberately mirrors `java.util.regex` with the same pattern, matcher, find and replace vocabulary, which makes most migrations an import change plus a review. Matching simulates the automaton rather than backtracking, so the cost is linear in the input and independent of how the pattern nests quantifiers.

Reach for it wherever patterns are untrusted or supplied by configuration, such as user search filters or log-parsing rules, since one catastrophic pattern on a request thread is a single-input denial of service against the JDK's backtracking engine. The costs: lookahead, lookbehind and backreferences are unsupported, so existing patterns need an audit; and on short trusted patterns the JDK engine is frequently faster, so this is a safety choice rather than a performance one.

### Reactor Core
**Short:** Reactive Streams implementation for the JVM: Flux and Mono, operators, backpressure and schedulers.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

This is the artifact that actually contains the reactive types, the operator library and the scheduler factory, and it depends only on the Reactive Streams interfaces, so a plain library can expose an asynchronous API built on it with no framework in sight. An operator chain is assembled eagerly but executes nothing until something subscribes, at which point demand travels back up the chain as explicit requests, which makes backpressure structural rather than an add-on.

Reach for it when a library must offer a cancellable, backpressured asynchronous API without dragging in a web framework, which is what the reactive database drivers do. The costs: thread locals do not propagate, so logging and security context need explicit plumbing; stack traces name operator internals unless you add checkpoints; and on Java 21 virtual threads give comparable concurrency to ordinary blocking code.

### Reactor Sinks.Many
**Short:** Project Reactor's multi-subscriber sink: a backpressure-aware in-JVM broadcast point for pushing events.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

### reactor-tools
**Short:** Reactor debug agent that captures assembly-time stack traces so an async error points at the operator that built it.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

It is the artifact containing Reactor's debug agent, which rewrites operator call sites as classes load so each assembled operator records the stack trace of the line in your code that declared it. When an error later propagates through the chain, that assembly trace is attached to the exception, turning a stack full of internal operator frames into a direct pointer at the pipeline step that failed.

It exists because a reactive stack trace is close to useless by default, since the throwing frame is inside an operator and nothing names the code that built the pipeline. Reach for it in development and anywhere a production reactive error must be diagnosed, because it is far cheaper than the hook that captures a stack trace at every operator assembly. It must run before any pipeline is assembled, and for a single suspect chain a checkpoint suffices.

### ReactorDebugAgent
**Short:** Java agent instrumenting Reactor operators to capture assembly-time stack traces, making reactive traces readable.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/tracing-apm-and-llm-observability @2, observability/profiling-and-performance @3

It instruments classes as they load, rewriting Reactor's operator assembly points so each publisher captures the stack trace of where it was declared. Because the instrumentation happens once at class load rather than on every subscription, its runtime cost is far below the equivalent global hook, which captures a fresh stack trace at every assembly for the life of the process. Errors are then decorated with a suppressed exception naming the assembly line.

Initialize it as the very first statement in main, before any publisher is built, or classes already loaded remain uninstrumented, which is the most common reason people report it appears to do nothing. Its limits follow: chains assembled by libraries whose classes loaded earlier are not covered, and it clarifies where an error came from rather than what it means. A named checkpoint gives the same information with no agent.

### Records
**Short:** Java record classes: shallowly immutable data carriers with generated equals, hashCode, toString and accessors.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### Redis Bloom filter
**Short:** RedisBloom's probabilistic set (BF.ADD/BF.EXISTS): membership tests with false positives but no false negatives.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-stores/key-value-and-embedded @2

The filter is a bit array plus a set of hash functions, sized from the error rate and capacity you declare when reserving the key. Adding an item sets several bits; testing checks those bits, so a negative answer is certain and a positive answer is right except at approximately the configured false-positive rate. The scaling variant chains a larger sub-filter when capacity is exceeded, at the cost of a rising error rate.

The point is memory: a few bits per element instead of a whole key, so hundreds of millions of members fit where a set would not. That makes it right where a false positive merely costs a wasted lookup, such as guarding a cache against penetration or asking whether a URL has been seen. The costs: it cannot delete or enumerate, sizing is committed up front, and the module must actually be loaded.

### ReentrantLock
**Short:** Java explicit mutual-exclusion lock with tryLock, timeouts, interruptibility, fairness and multiple condition queues.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### regex
**Short:** Third-party replacement for Python's re adding atomic groups, possessive quantifiers, grapheme segmentation.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

It is a drop-in superset of the standard library's `re` — import it as `re` and existing patterns keep working — that adds what the stdlib lacks: atomic groups and possessive quantifiers, which discard backtrack points and are the direct fix for a catastrophic-backtracking pattern; variable-length lookbehind; fuzzy matching with an error budget; nested character sets and set operations; and full Unicode support including `\X` for a grapheme cluster, `\p{...}` property classes, and correct case folding.

The grapheme support is why text that must respect user-perceived characters — emoji with skin-tone or ZWJ sequences, combining marks, Indic scripts — reaches for it, since `re` will happily cut such a character in half. Use the stdlib when the pattern is simple and one fewer dependency is worth more; reach for `regex` when a pattern is a backtracking hazard on untrusted input or when correctness across scripts matters.

### regex101
**Short:** Web regex workbench with step-by-step match debugging and a backtracking step counter for catastrophic patterns.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/version-control-and-workbench @3

It compiles your pattern against a chosen engine, with PCRE2, ECMAScript, Python, Java, Go and .NET all available, highlights matches and capture groups live, and explains the pattern token by token. The feature that earns it a place here is the debugger, which steps through the match and shows every backtrack with a running step counter and a cap that aborts a runaway pattern.

Use the engine selector deliberately, because the same pattern behaves differently across engines: lookbehind support, named-group syntax and whether the digit class is Unicode-aware all vary, so testing in PCRE2 while shipping in Java is how a pattern passes here and fails in production. The step counter is a practical backtracking check: hundreds of thousands of steps against a short string is a denial of service waiting to happen. Never paste production data into it.

### renice
**Short:** Unix CLI that changes a running process's nice value (-20 to +19); only root can raise priority.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It changes the scheduling niceness of a running process, process group or user's processes, from minus twenty for most favourable to plus nineteen for least. Under Linux's fair scheduler the value is a weight rather than a cap: each step changes the relative CPU weight by roughly a quarter, so a process at plus ten receives on the order of a tenth of the CPU a default-priority process gets when both are runnable, and nothing is taken away while the machine is idle.

Reach for it to stop a batch job or a runaway build from stealing time from an interactive service on the same box, since deprioritizing the offender is often faster and less disruptive than killing it. Three limits: an unprivileged user may only raise their own processes' nice value and never lower it again; it affects CPU only, so `ionice` is the counterpart for disk; and cgroup quotas dominate anything nice can express.

### reversed
**Short:** Comparator.reversed(): flips an ordering strategy and composes with thenComparing; the JDK's canonical Strategy.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### RuntimeReflection
**Short:** GraalVM build-time API registering classes and members for reflection so native image keeps them reachable.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

### RxJava
**Short:** Reactive Extensions for the JVM: composable async streams with operators, schedulers and backpressure.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

It brings the ReactiveX vocabulary to the JVM as `Observable`, `Single`, `Maybe`, `Completable` and the backpressure-aware `Flowable`, composed with a large operator set covering mapping, flattening, combining, debouncing and retrying. Nothing executes until subscription, the subscription is a disposable handle used to cancel, and errors travel the chain as a terminal signal rather than as thrown exceptions, which lets a retry be an operator rather than a try block.

Its strength is composing events over time: combining a network call with a cache, debouncing user input, coordinating concurrent calls under one timeout policy. That is why it became the standard on Android before coroutines, and Project Reactor occupies the same slot on the server. The costs are a large operator surface with a genuine learning curve, the easy mistake of using the non-backpressured type, and traces that name operators rather than your code.

### RxJava 3 Flowable
**Short:** Reactive Streams publisher with backpressure: the push-based dual of Iterator for unbounded async sources.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

### schedtool
**Short:** Linux CLI to inspect or change a process or thread's scheduling policy, priority and CPU affinity.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

It is a small wrapper over the scheduling system calls that queries and sets in one place what `chrt` and `taskset` set separately: the scheduling policy, static priority for the real-time policies, nice value, and CPU affinity, either against an existing process or on a command it launches. Run against a process id with no other arguments it prints the current policy, priority and affinity mask on one line.

Its distinct value is easy access to the two policies the common tools expose less conveniently. The batch policy tells the scheduler a task is throughput-oriented and should never be treated as interactive. The idle policy runs a task only when nothing else wants the CPU, the cleanest way to run a background scrubber that must never disturb the workload. It is a small third-party utility frequently not installed where `chrt` and `taskset` always are.

### ScheduledExecutorService
**Short:** Java executor running tasks after a delay or at a fixed rate/delay, replacing polling loops and Timer.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/aop-middleware-and-scheduling @2

### SciPy
**Short:** Scientific computing stack for Python: sparse linear algebra, optimization, signal processing and statistical tests.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, applied-ml/interpretability-fairness-and-causal @3, ml-lifecycle/drift-and-production-monitoring @3, applied-ml/timeseries-and-anomaly @3

SciPy is the algorithm layer on top of NumPy arrays. `scipy.sparse` and its linear-algebra module handle matrices too large to store densely, including truncated decompositions such as `svds` that give you the top k singular vectors without forming the full factorization. `scipy.stats` provides distributions and hypothesis tests, `optimize` covers root finding and minimization, and there are modules for signal processing, interpolation, integration and spatial structures.

In machine-learning work it is usually the statistics and sparse pieces that get used: fitting a distribution to a tail for thresholding, running a two-sample test between a reference and a live window to detect drift, or a truncated SVD over a term-document matrix. The heavy routines are compiled C and Fortran that release the interpreter lock, so they genuinely parallelize across threads.

### SciPy stats.bootstrap
**Short:** SciPy function computing bootstrap confidence intervals, including BCa, for any statistic you supply.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @2

### scipy.optimize.minimize
**Short:** SciPy's general numerical optimizer (SLSQP, L-BFGS-B and more), often used to fit ensemble blend weights.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, model-training/classical-ml-and-boosting @2

### scipy.sparse.csgraph
**Short:** SciPy's C-backed graph algorithms over sparse adjacency matrices: shortest paths, components, MST.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### scipy.spatial.distance.hamming
**Short:** SciPy's Hamming distance over two equal-length sequences; it is not edit distance and cannot handle inserts or deletes.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### scipy.spatial.KDTree
**Short:** SciPy k-d tree for exact nearest-neighbour and radius queries in low dimensions, implemented as a C extension.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, search-retrieval/ann-index-library @2

### scipy.special.logsumexp
**Short:** Numerically stable log-sum-exp that prevents underflow when normalizing log-space probabilities.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, model-training/classical-ml-and-boosting @3

### scipy.stats
**Short:** SciPy's statistics module: distributions, hypothesis and correlation tests; the workhorse for A/B significance checks.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @2, applied-ml/interpretability-fairness-and-causal @3

### scipy.stats.multivariate_normal
**Short:** SciPy multivariate Gaussian object: logpdf, pdf and sampling; the stable per-component density inside a GMM.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### scipy.stats.spearmanr
**Short:** SciPy rank-correlation test, used to score embedding similarity against human judgements on sets like SimLex-999.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @2

### ScopedValue
**Short:** Java 25 API (JEP 506) for immutable scoped context propagation to subtasks; the ThreadLocal successor.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @3

### seaborn
**Short:** Statistical plotting library over matplotlib: distributions, cluster scatter, correlation heatmaps in a few calls.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, applied-ml/interpretability-fairness-and-causal @3

It takes a tidy DataFrame and column names rather than arrays, and maps columns to visual roles such as position, hue, size and facet, so grouping and faceting are arguments instead of loops. It performs the statistics the plot implies: aggregating with a bootstrap confidence interval, fitting a regression line, estimating a kernel density. Figure-level functions build a whole grid of axes while axes-level functions draw into an axes you own, and confusing the two is the main source of frustration.

Reach for it for exploratory statistical graphics, where distributions, category comparisons and correlation heatmaps are a couple of lines against dozens in raw Matplotlib. The costs are that convenience ends where customization begins, at which point you need both APIs; that automatic bootstrapping is slow on large data; and that it produces static images, so interactive charts belong to Plotly or Altair.

### Sealed interfaces
**Short:** Java feature closing a type hierarchy so the compiler checks exhaustive matching; replaces double dispatch.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### sed
**Short:** Unix stream editor applying regex substitutions and line operations to text piped through it.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/version-control-and-workbench @2

It reads input a line at a time into a pattern space, applies your commands and prints the result. Substitution is the command everyone uses, with a flag for every occurrence on the line and references to the match and its capture groups in the replacement. Addresses restrict a command to a line number, a range or a regex match. In-place editing exists but its backup-suffix argument differs between GNU and BSD, the usual reason a script works on Linux and fails on macOS.

Reach for it for line-oriented, regex-shaped edits in a pipeline or across many files: rewriting a configuration value, stripping a prefix, extracting a group. Two limits define where it stops. It is line-based, so a pattern spanning lines requires the hold space and becomes unreadable immediately. And its dialect is POSIX basic regular expressions, so the digit class and lazy quantifiers are absent. Structured formats deserve a parser.

### Segment tree
**Short:** Tree structure supporting range aggregate queries and point updates in O(log n) over an array.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

It is a binary tree over an array in which each node stores the aggregate of a contiguous range, the root covering everything and each leaf a single element. A range query descends and combines a logarithmic number of canonical nodes that exactly tile the requested interval, and a point update rewrites one root-to-leaf path. Lazy propagation, where a pending modification is parked at a node and pushed down only when a child is visited, makes range updates logarithmic too.

It is the general structure where a Fenwick tree is the specialised one: any associative combine works, including minimum, maximum and gcd, none of which a Fenwick tree can express because they have no inverse to subtract. Choose the Fenwick tree when the operation is a sum and you want half the memory and a tenth of the code; choose this when the operation is not invertible or a node must hold something richer than a number.

### send
**Short:** Generator method from PEP 342 that pushes a value into a paused generator, making coroutines bidirectional.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @3

### set
**Short:** Python's built-in hash set: average O(1) add, membership and removal, plus union/intersection/difference operators.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### SimpleFileVisitor
**Short:** NIO base class for walking a directory tree with pre/post hooks and a FileVisitResult to prune or stop the walk.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/design-patterns-and-principles @2

### Sinks, RxJava 3 Observable
**Short:** Reactive multicast entry points (Reactor Sinks, RxJava Observable) adding filter, buffer and retry over observers.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2, apis-frameworks/rpc-graphql-and-streaming @3

### socket
**Short:** Python's stdlib BSD socket API for raw TCP and UDP: echo servers, custom protocols and low-level networking.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/io-networking-and-syscalls @1

### socket.htonl
**Short:** Python stdlib call converting a 32-bit integer from host byte order to network (big-endian) order.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/collections-and-algorithms @3

### sorted
**Short:** Python builtin returning a new sorted list using stable TimSort, with key and reverse options.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### sortedcontainers.SortedDict
**Short:** Pure-Python dict kept in sorted key order with O(log n) lookup, insert and ordered range iteration.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### sortedcontainers.SortedList
**Short:** Pure-Python sorted sequence with O(log n) insert, search and index lookup; an order-statistics structure.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Spliterators
**Short:** Java's splittable iterator abstraction; implement one to make a custom source decompose well for parallel streams.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2

### spring-integration-file
**Short:** Spring Integration adapters that poll a directory into messages and write messages back out as files.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/aop-middleware-and-scheduling @2, data-movement/message-broker @3

The inbound adapter polls a directory on a trigger and emits each file as a message, with filters controlling what it picks up: a name pattern, and an accept-once filter which, in its persistent form backed by a metadata store, prevents reprocessing everything after a restart. Outbound, the writing handler persists a payload through a configurable filename generator, writing to a temporary name and renaming so a consumer never sees a partial file.

Reach for it when a genuine integration boundary is a drop directory, such as a partner batch feed or an SFTP landing zone, and you want retries, error channels and transactional semantics around it rather than a hand-written poller. The costs are inherent to the pattern: polling races against writers, a shared directory across instances needs the persistent filter plus locking, and the metadata store grows unless pruned. An object-storage notification is a better trigger where available.

### spring.threads.virtual.enabled=true
**Short:** Spring Boot 3.2+ property that switches Tomcat request handling and @Async execution onto virtual threads.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/dependency-injection-and-config @3

### sqids
**Short:** Encodes integer ids into short reversible URL-safe strings; obfuscation for public ids, not a security boundary.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, data-stores/relational @3

It is the successor to Hashids by the same author. It encodes one or more non-negative integers into a short URL-safe string using an alphabet you supply that is shuffled per call, so consecutive inputs do not produce visibly consecutive outputs, and decodes exactly back with no stored mapping. It also carries a blocklist and re-encodes when a generated string would contain an offensive word, a real problem for anything shown to users.

Use it to keep sequential primary keys out of public URLs so a customer cannot count your records or walk to the next one. Be exact about what it is not: there is no key and no secret, and it is neither encryption nor an access control. Every fetch still needs an authorization check. When unguessability is a real requirement use a random UUID, and UUIDv7 or ULID when you also want sortability.

### ss
**Short:** Linux CLI that lists sockets and their state: listening ports, TIME_WAIT counts, accept-queue depth.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

`ss -tan` lists TCP sockets and their states, `ss -ltn` just the listeners, `ss -s` prints a summary by state, and `-i` adds per-socket TCP internals such as congestion window and smoothed RTT. It reads socket state over netlink rather than parsing `/proc/net/tcp`, which is why it returns instantly on a host with a hundred thousand connections where `netstat` crawls.

The columns that answer real questions are `Recv-Q` and `Send-Q`. On a listening socket they are the current accept-queue depth and the backlog limit — a full queue is direct proof that the application is not accepting fast enough and connections are being dropped, not merely served slowly. Elsewhere, a large pile of `TIME_WAIT` points at connection churn from missing keep-alive, while accumulating `CLOSE_WAIT` sockets means your code is not closing sockets the peer already closed, which is a leak in your application, not a kernel problem.

### Stack
**Short:** Legacy java.util.Stack, a synchronized Vector subclass; ArrayDeque is the modern LIFO replacement.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### statsmodels
**Short:** Python statistics library: OLS/GLM, ANOVA, hypothesis tests, ARIMA/STL and econometric estimators like 2SLS.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, applied-ml/timeseries-and-anomaly @2, applied-ml/interpretability-fairness-and-causal @2, ml-lifecycle/evaluation-and-benchmarks @3

It brings R-style statistical modelling to Python: an R-like formula API, and fits that return full inferential output -- coefficients with standard errors, t and p values, confidence intervals, information criteria and residual diagnostics -- which scikit-learn deliberately omits because it optimises for prediction. Alongside regression it carries time-series estimators (ARIMA and SARIMAX, STL decomposition, ADF and other tests) and econometric tools such as two-stage least squares.

Reach for it when the question is whether an effect is real and how large it is: A/B significance tests, McNemar's test comparing two classifiers, difference-in-differences, or a forecasting baseline that a neural model must beat. Use scikit-learn instead when you only need the prediction.

### std::sort
**Short:** C++ standard sort: introsort, a quicksort that falls back to heapsort on bad pivots and insertion sort on small ranges.
**Kind:** api
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

### std::stable_sort
**Short:** C++ standard algorithm performing a guaranteed-stable sort, typically merge sort with extra memory.
**Kind:** api
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

### str.encode
**Short:** Turns a Python str into bytes under an explicit codec; the boundary where encoding must be stated, not guessed.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

### strace
**Short:** Linux tracer that prints every syscall a process makes; the first tool for 'why is this process stuck'.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @2, runtime-systems/memory-processes-and-os @3

It attaches via `ptrace` and prints every system call a process makes with arguments, return value and errno, so a hang or an unexplained failure resolves into a concrete line: blocked in `read` on a socket, looping on `ENOENT` for a config file it cannot find, or parked on a `futex` behind a lock. `-f` follows child processes, `-c` prints a summary count per call, and `-p` attaches to something already running, which is how you diagnose production without a restart. The cost is severe, because every syscall traps into the tracer, so a syscall-heavy process can slow down by an order of magnitude and this is a targeted tool rather than something to leave attached. It is Linux-only; macOS has `dtruss`, and eBPF tooling is the lower-overhead modern route.

### Stream.of
**Short:** Static factory creating a Stream from explicit values or a single element, for ad-hoc pipelines.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### StreamSupport.stream
**Short:** Java factory turning a Spliterator into a Stream, which is how a custom source becomes parallel-capable.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2, runtime-systems/concurrency-and-async @3

### String deduplication
**Short:** JVM feature (-XX:+UseStringDeduplication) that shares identical byte[] backing arrays between equal Strings.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/text-encoding-and-regex @2

When enabled, the collector queues strings that have survived a threshold number of young collections, and a background thread hashes each one's backing array, looks it up in a weak table, and if an equal array exists repoints the string at the shared one. Only the arrays are shared: the string objects stay distinct, so identity and reference equality are unaffected, which makes it safe to switch on without auditing code. It is off by default.

Reach for it when a heap dump shows character or byte arrays dominating and many strings are equal but you cannot intern them or restructure the code producing them, the shape of a service parsing many documents with a small set of repeated values. Where it applies the saving can be a large fraction of the heap; where strings are mostly unique it saves nothing. There is also a delay before any string is deduplicated.

### String Templates
**Short:** Java's previewed string interpolation (JEP 430/459), withdrawn after Java 22 - no shipping interpolation syntax today.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.chars
**Short:** Java 8 method returning an IntStream of a string's UTF-16 code units, which is not the same as its code points.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.codePoints
**Short:** Java 8 method returning an IntStream of Unicode code points, correct for emoji unlike char iteration.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.formatted
**Short:** Java 15 instance method equivalent to String.format, letting a text block be interpolated fluently.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.indent
**Short:** Java 12 method adjusting each line's leading whitespace and normalizing line terminators to \n.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.intern
**Short:** JDK method returning the canonical pooled instance of a String; the pool has lived on the heap since Java 7u40.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/text-encoding-and-regex @2, apis-frameworks/design-patterns-and-principles @3

### String.intern() and the JVM string table
**Short:** JVM native string pool returning one canonical instance per value, trading a table lookup for deduplicated memory.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/memory-processes-and-os @2, runtime-systems/runtime-internals-and-types @3

### String.isBlank
**Short:** Java 11 String method returning true when the string is empty or contains only Character.isWhitespace characters.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.repeat
**Short:** Java 11 String method repeating the string n times, backed by a single Arrays.copyOf instead of a loop.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### String.strip
**Short:** Java 11 Unicode-aware whitespace trim; correct where trim() only removes characters at or below U+0020.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### StringConcatFactory
**Short:** JEP 280 invokedynamic bootstrap that the compiler emits for string concatenation instead of StringBuilder chains.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/runtime-internals-and-types @2

### Structured Concurrency
**Short:** Concurrency model binding a task's lifetime to a lexical scope, so no subtask outlives it and errors propagate.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/concurrency-and-async @1

It applies to concurrency the discipline that made structured programming work. A task can only be started inside a scope, the scope's block cannot exit until every child has completed, been cancelled or failed, and a child's failure cancels its siblings and propagates out like an ordinary exception. That one rule eliminates a task nobody awaits, an error that disappears into a discarded future, and a timeout that returns while the work continues invisibly.

It also restores a relationship between the code you read and the work that runs, since the concurrency tree matches the call tree, which is what makes a stack trace and a cancellation mean something again. Trio's nurseries pioneered it, and Python's task groups and Java's structured task scope are the standard-library forms. The cost is that long-lived background work must be modelled deliberately, keeping a scope alive at the level that owns the lifetime.

### StructuredTaskScope
**Short:** JDK structured-concurrency API: fork subtasks in a scope that joins, cancels and propagates failure as a unit.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### subprocess
**Short:** Python stdlib module for spawning and communicating with external processes; use shell=False.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/io-networking-and-syscalls @3

### sun.misc.Unsafe
**Short:** The JDK's internal raw-memory API, now being retired in favour of the Panama FFM API and VarHandles.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/memory-processes-and-os @2

### SymbolLookup
**Short:** Java FFM API for finding a native symbol address in a loaded library or the default process before linking.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/io-networking-and-syscalls @3

### sympy
**Short:** Symbolic mathematics library; used in RLHF pipelines to verify a model's math answer is algebraically equal.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @3

It is a computer algebra system written in pure Python. Expressions are trees of immutable objects partially simplified as they are built, and everything downstream operates on the tree exactly rather than numerically: simplification, factoring, solving, differentiation, integration, limits, series and matrices. Rational and arbitrary-precision arithmetic mean no floating-point error creeps in, and a lambdify step compiles an expression into a fast NumPy-backed callable when you do want numbers.

In machine-learning pipelines the common use is verification: parse a model's answer and the reference into expressions and ask whether their difference simplifies to zero, which accepts an algebraically equivalent form a string comparison would reject. The costs: being pure Python it is slow, general simplification is a heuristic that can hang on a large expression, and symbolic equivalence is undecidable, so a not-equal verdict can be a failure to simplify.

### SymPy rsolve
**Short:** SymPy solver that finds closed-form solutions to recurrence relations symbolically.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### sympy.ntheory
**Short:** SymPy's number theory module: modular arithmetic, modular inverses, Miller-Rabin primality and factorization.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### synchronizedList
**Short:** Collections wrapper returning a mutex-guarded view of a list; iteration still needs manual synchronization.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @2, apis-frameworks/design-patterns-and-principles @2

### sys.intern
**Short:** CPython call that puts a string in the interned table, making repeated keys share one object and compare by identity.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/memory-processes-and-os @2

### sys.setrecursionlimit
**Short:** Raises CPython's interpreter recursion cap (default 1000); the C stack can still overflow, so raise it carefully.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/memory-processes-and-os @3

### sysctl net.ipv4
**Short:** Linux kernel knobs for the TCP/IP stack: backlog sizes, keepalive, TIME_WAIT reuse, buffer autotuning.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, runtime-systems/memory-processes-and-os @2

### sysctl vm.swappiness
**Short:** Linux kernel tunable (0-100) controlling how aggressively the kernel swaps anonymous pages out.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### systemctl
**Short:** systemd CLI to start, stop, enable and inspect services and their unit state on a Linux host.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, platform-delivery/infrastructure-as-code-and-config @3

It is the front end to systemd, operating on units, of which services are one type alongside sockets, timers, mounts and targets. Starting and stopping change the current state while enabling and disabling change whether a unit starts at boot, and the two being independent is the distinction people trip over most. Status shows the unit's state, its main process, its full cgroup with every child, and recent log lines; a daemon reload is required after editing a unit file.

What makes this different from an init script is that a service is a cgroup, so systemd knows every process it spawned, can apply memory and CPU limits declaratively, and can reliably stop the whole tree rather than leaving orphans. The costs: unit semantics are easy to get subtly wrong, particularly the service type that decides when systemd believes the service is ready, and inside a container the orchestrator plays this role instead.

### taskset
**Short:** Linux command that pins a process or thread to specific CPU cores, cutting cache misses in latency-sensitive services.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @3

`taskset -c 0-3 ./app` launches a process with a CPU affinity mask restricting the scheduler to those cores, and `taskset -pc 0-3 <pid>` changes the mask of something already running. The reason to bother is locality: a thread that stays on one core keeps its L1 and L2 caches warm and its memory local to the right NUMA node, so you stop paying for cold caches and remote memory access after every migration — which shows up as lower tail latency rather than higher average throughput.

To get the benefit you usually have to pair it with keeping other work off those cores, through `isolcpus` or a cpuset, or the scheduler will happily place everything else there anyway. Do not reach for it by default: you are taking away the scheduler's freedom to balance load, and on a general-purpose or oversubscribed machine that normally costs more than it gains.

### tcpdump
**Short:** Command-line packet capture; the low-overhead ground truth for what actually crossed the wire.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

tcpdump installs a BPF filter in the kernel and copies only matching packets to userspace, which is why an expression like `tcp port 5432 and host 10.0.0.7` is cheap enough to run on a production box -- the filtering happens before the copy, not after. It settles arguments logs cannot: whether the SYN ever left, who sent the RST, whether the TLS handshake completed, how long the server really took to answer, whether retransmissions or a zero receive window explain the latency. Write to a file with `-w` and open it in Wireshark rather than trying to follow a stream on a terminal, and always bound the capture with a narrow filter, a packet count, or a ring buffer so a debugging session does not fill the disk. Reach for it when the two ends of a connection disagree about what happened; it needs elevated privileges and captures payloads that may be sensitive, and on an encrypted connection you see timing and sizes rather than content.

### Text Blocks
**Short:** Java multi-line string literals (JEP 378) that strip incidental indentation, for embedded SQL, JSON and HTML.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### TextDecoder
**Short:** Web/Node API decoding a byte buffer into a JavaScript string for a given encoding, with streaming and BOM handling.
**Kind:** api
**Lang:** js
**Roles:** runtime-systems/text-encoding-and-regex @1

### TextEncoder
**Short:** Web and Node API converting a JavaScript string to UTF-8 bytes, with TextDecoder doing the reverse.
**Kind:** api
**Lang:** js
**Roles:** runtime-systems/text-encoding-and-regex @1

### thenComparing
**Short:** Comparator method chaining a tie-breaker onto an existing ordering; the JDK's canonical Strategy composition.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @2

### Thread.ofVirtual
**Short:** Java 21 builder that creates virtual threads - lightweight JVM-scheduled threads with a few-KB stack instead of ~1MB.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

### threading
**Short:** Python stdlib OS-thread module - Thread, Lock, Event - good for I/O concurrency but bounded by the GIL for CPU work.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### threading.Barrier
**Short:** Python synchronization primitive releasing all parties only once N threads have arrived; used for synchronized starts.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### threading.Condition
**Short:** Python condition variable bound to a lock: wait/notify handoff, always re-checked inside a while loop.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### threading.Lock
**Short:** Python's primitive mutex for guarding a critical section; the GIL does not make explicit locking unnecessary.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### threading.RLock
**Short:** Python reentrant mutex: the owning thread may acquire it repeatedly and must release it the same number of times.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### threading.Semaphore
**Short:** Python counting semaphore gating access to a fixed-size resource pool; BoundedSemaphore catches over-release.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### threading.Thread
**Short:** Python's OS-thread wrapper; concurrency for I/O-bound work since the GIL is released during blocking I/O.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### ThreadLocal
**Short:** Java per-thread variable used to carry request context implicitly; a leak source on pooled threads if never cleared.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/aop-middleware-and-scheduling @3

### ThreadMXBean
**Short:** JMX bean exposing thread state, CPU time and deadlock detection for a live JVM.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

### ThreadPoolExecutor
**Short:** Bounded pool of worker threads that executes submitted tasks and returns futures.
**Kind:** api
**Lang:** java, python
**Roles:** runtime-systems/concurrency-and-async @1

### ThreadPoolExecutor.getQueue().size
**Short:** Java call exposing pending task count in a thread pool's queue, the key saturation metric to alert on.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/metrics-and-monitoring @2

### ThreadPoolTaskExecutor
**Short:** Spring's configurable thread pool backing @Async and @Scheduled work: core and max size, queue, rejection policy.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/aop-middleware-and-scheduling @2

### ThreadSanitizer
**Short:** Compiler-instrumented runtime data-race detector for native code, enabled with -fsanitize=thread.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/concurrency-and-async @1, devtools/testing-and-mocking @2, observability/profiling-and-performance @3

Build with `-fsanitize=thread` on Clang or GCC and the compiler instruments every memory access and synchronization operation; at run time TSan maintains vector clocks and shadow memory to decide whether two accesses to the same location are ordered by a happens-before edge, and prints both stacks when they are not. That makes it a real race detector rather than a heuristic -- it reports a race on the executed path even when the interleaving that would corrupt data did not occur in that run, which is precisely the bug ordinary testing misses. The price is roughly a five-to-fifteen-times slowdown and several times the memory, so it belongs in a CI job or a soak test and never in production, and it only sees code paths your tests actually execute. Its coverage is native code -- C, C++, Go, Rust -- so for a JVM application it checks the JNI and native library side, not the Java heap.

### ThreeTen-Extra
**Short:** Add-on types for java.time by its author: Interval, Quarter, DayOfMonth and other calendar values the JDK omits.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

It is a companion library by the author of the standard date and time API, carrying the value types the JDK deliberately left out: an interval between two instants with proper contains, overlaps and abuts semantics; year-week and year-quarter types; separate typed amounts for days, weeks, months and years rather than one general period; and a mutable clock for tests. Everything implements the standard temporal interfaces, so it composes with the JDK types.

Reach for it for the two types people otherwise reimplement badly: an interval with correct half-open comparison, and quarter or week-based dates in reporting code, where the ISO week-year rules produce off-by-one-year bugs at January boundaries with impressive reliability. The costs are an extra dependency and that its types will not survive a serialization boundary unless both sides have it, so two instants is the safer wire form.

### throw
**Short:** generator.throw() - raises an exception at the generator's suspension point, part of PEP 342 bidirectional generators.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2

### TLA+
**Short:** Formal specification language whose model checker explores every reachable state to test a protocol invariant.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-access/transactions-and-consistency @3

You write a specification as a state machine: variables, an initial-state predicate, and a next-state relation, expressed in set theory and temporal logic rather than in code. The model checker then enumerates every reachable state of a finite instance you configure, perhaps three nodes and two clients, checks your invariants against all of them, and produces a minimal counterexample trace when one fails. PlusCal is a pseudocode-flavoured front end that compiles to the same specification.

It earns its cost on concurrent and distributed designs, where the bug is an interleaving nobody imagined and no test would generate; AWS has published on using it to find serious flaws in designs that had already survived review. Reach for it before implementing a protocol, not afterwards. The costs: state-space explosion means you verify a small instance and argue that it generalizes, and it verifies a design rather than the code you ship.

### toolz
**Short:** Functional utility library for Python: lazy pipe, compose, curry and iterator combinators; cytoolz is the C build.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

A small library of composable functions over iterables, dictionaries and functions: `pipe`, `compose` and `curry` for building pipelines, `groupby`, `partition`, `sliding_window` and `unique` for sequences, `valmap`, `keyfilter` and `merge_with` for dicts, plus memoization. Everything is lazy and returns iterators, so a pipeline streams over an input far larger than memory. `cytoolz` is a drop-in C-accelerated build of the same API.

Reach for it when a data-munging step would otherwise become nested comprehensions and temporary lists. Be aware that `itertools`, comprehensions and `functools` cover much of it in the standard library, and heavily point-free code is harder for the next reader.

### top
**Short:** Live Unix process and resource monitor - load average, per-process CPU and memory, threads with the htop H toggle.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

It refreshes a sorted process table every few seconds with load average, per-process CPU and resident memory, and a breakdown of where CPU time actually goes, including the `wa` iowait and `st` steal columns that distinguish a genuinely busy machine from one waiting on disk or losing cycles to a noisy neighbour on shared hardware. Two readings mislead beginners: the CPU percentage is per core, so 400% on a four-core box means saturated, and `RES` counts shared pages in every process that maps them, so summing it double-counts memory. It is the right first command on a slow host, but it only narrows the question; `htop` is friendlier and toggles threads with `H`, and a profiler or `strace` is where the real answer comes from.

### tracepath
**Short:** Traces the hop-by-hop path to a host and discovers the path MTU, without needing root like traceroute.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

Like traceroute it sends probes with increasing time-to-live and reads the resulting ICMP time-exceeded replies to name each hop, but it differs in two useful ways. It uses ordinary unprivileged UDP sockets, so it needs no special capability, and it tracks the path MTU as it goes, starting at the interface MTU and reacting to fragmentation-needed replies, printing the new value where the maximum drops.

That MTU discovery is the reason to reach for it. A path MTU black hole, typically a tunnel with a smaller MTU combined with a device dropping the ICMP that would have said so, produces a distinctive symptom: the connection establishes, small requests work, and the transfer hangs the moment a large response is sent. Its limits are one sample per hop, so it says nothing about intermittent loss, where `mtr` is the right tool.

### traceroute
**Short:** CLI that maps the hop-by-hop path to a host and shows per-hop latency and loss; also used for MTU discovery.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

traceroute sends probes with a time-to-live of one, then two, then three, and so on. Each router that decrements the TTL to zero replies with an ICMP time-exceeded message, so the sequence of replies reveals the routers along the path and the round-trip time to each. That is how you localize where latency or loss enters — your network, your provider, a transit peer, or the far end.

Read the output carefully, because it lies in a specific way: routers deprioritize or rate-limit the ICMP replies they generate, so a single hop showing asterisks or a high time is normal and means nothing on its own. Only latency or loss that appears at one hop and persists through every hop after it is evidence. For intermittent loss, `mtr` runs the same probes continuously and gives you a distribution instead of a single sample.

### TreeMap
**Short:** Java red-black-tree sorted map: O(log n) operations plus ordered iteration and floorKey/ceilingKey/subMap range queries.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### TreeSet
**Short:** JDK sorted set backed by a red-black tree: O(log n) add/contains plus floor, ceiling and range views.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### trio
**Short:** Alternative Python async runtime enforcing structured concurrency with nurseries and no detached-task escape hatch.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

Its central mechanism is the checkpoint: every operation that can block is a point where the task yields to the scheduler and where cancellation may be delivered, and the library guarantees each of its own async functions contains at least one, so cancellation is both prompt and predictable. It is delivered as an ordinary exception at a checkpoint, so it unwinds through your finally blocks exactly as synchronous cleanup does. Nurseries supply the other half, since a task can only be spawned into one.

The payoff is that asyncio's characteristic failure modes largely cannot occur: a task cannot be orphaned, an exception cannot vanish into a dropped reference, and a timeout genuinely stops the work. The obstacle is the ecosystem, since asyncio-only libraries need a bridge, so most production Python stays on asyncio and imports the ideas through anyio. Reach for trio when the concurrency structure is the hard part and you control the dependency stack.

### typeguard
**Short:** Runtime type checker that enforces annotations on call, using ABCMeta virtual subclass checks.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/testing-and-mocking @3

It enforces annotations at run time. A decorator on a function, or its import hook installed across a package, rewrites the function so arguments, return values and yielded values are checked against their annotations, with an error naming the parameter and the type expected. Unlike a sampling checker it validates containers deeply by default, walking the elements of a list, and it ships a pytest plugin that turns any annotation violation during a test run into a failure.

Reach for it in tests and continuous integration, where the deep checking is exactly what you want and its cost does not matter: it converts annotations you already wrote into assertions and catches the mismatch a static checker cannot see because the value arrived from untyped JSON. In production that same deep walk is the cost, since checking every element of a large list is linear work per call, which is why beartype's sampling exists.

### TypeVariable.getBounds
**Short:** Reflection call returning a type variable's full bound list at runtime, including bounds erased from the descriptor.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1

### typing.Concatenate
**Short:** Typing construct prepending parameters to a ParamSpec, so a decorator can inject an argument and stay typed.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

### typing.ParamSpec
**Short:** Python typing construct that carries a callable's parameter signature through a decorator so wrappers stay type-checked.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/static-analysis-and-linting @2

### typing.Protocol
**Short:** Python structural-typing construct: a type conforms by having the methods, with no inheritance or registration.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### typing_extensions
**Short:** Backports newer typing constructs (TypeIs, PEP 696 defaults, override) to still-supported older Pythons.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

It is a single runtime dependency maintained alongside CPython's own typing module, providing new typing constructs on older interpreters and the newest ones before they appear in any release. Anything added to the standard module lands here first, and it re-exports the standard version when the interpreter already has it, so an import works identically across versions with no conditional. Type checkers treat the two as equivalent by design.

Reach for it in a library supporting several Python versions that wants current typing, such as the self type, an override marker or type-narrowing predicates, without version-gated imports scattered through the codebase. The costs are that it is a genuine runtime dependency, so an otherwise dependency-free library gives that up, and that its behaviour tracks the installed version, so declare a floor and drop the import once your minimum Python includes the construct.

### tzupdater
**Short:** Oracle tool patching a JDK's bundled tzdata so date handling stays correct when governments change DST rules.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1

It patches an installed JDK's bundled time-zone data in place, replacing the compiled database the runtime reads with one built from a newer IANA release, so an existing installation picks up current daylight-saving rules without a full JDK upgrade. It can also report the version currently installed and validate rather than write, which is how you audit a fleet.

It exists because the JDK carries its own copy of the time-zone database rather than reading the operating system's, which surprises teams every time a government moves a daylight-saving boundary: patching the OS package changes nothing for a Java process, and a scheduler quietly fires an hour off. The costs are that it is version-sensitive and modifies an installation in place, which a container rebuild undoes anyway. Taking a newer JDK patch release is the maintainable answer.

### Unicode Character Database
**Short:** The Unicode Consortium's authoritative data files defining every code point's category and properties.
**Kind:** spec
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

It is the set of data files published with each Unicode version that define, for every assigned code point, its name and general category, canonical and compatibility decompositions, case mappings including those that change a string's length, bidirectional class, script, numeric value and boolean properties such as alphabetic and whitespace, along with the rules for grapheme, word and line breaking. Every regex property class and every normalization and collation table is generated from these files.

The reason to know it exists is that what counts as a letter or as whitespace is a data question with a version attached, not a constant. Two systems on different Unicode versions can disagree about whether a code point is alphabetic or where a grapheme boundary falls, which is how the same validation rule passes in one service and fails in another. Pin the version wherever stored order or validation results depend on it.

### unicodedata.normalize
**Short:** Python stdlib call applying Unicode NFC, NFD, NFKC or NFKD normalization before comparing or hashing strings.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

### uvloop
**Short:** Drop-in libuv-backed asyncio event loop giving 2-4x throughput on I/O-heavy Python workloads.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/web-framework-and-http-client @3

uvloop replaces asyncio's pure-Python event loop with one built on libuv, the same C library behind Node.js, so the per-callback and per-socket overhead of the loop itself drops and I/O-heavy servers see roughly two to four times the throughput without a line of application code changing. You enable it by running your entry point through uvloop or installing its event loop policy; uvicorn's standard extra pulls it in and selects it automatically.

It only helps where the loop is the bottleneck — CPU-bound handlers gain nothing — and it does not support Windows, so a team developing there runs a different loop than production does.

### VarHandle
**Short:** Java 9+ typed accessor giving plain, opaque, acquire/release and volatile memory-ordering modes on fields and arrays.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @2, runtime-systems/memory-processes-and-os @3

### Vavr
**Short:** Functional library for Java: Option, Either, Try and persistent collections with structural sharing.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

Vavr brings a functional standard library to Java: `Option` in place of nullable returns, `Either` for a result carrying a typed failure, `Try` that captures a thrown exception as a value so it composes, plus tuples and pattern matching the language does not provide. Its collections are persistent -- an update returns a new structure that shares the unchanged parts rather than copying, so repeatedly "modifying" a large immutable structure stops costing a full copy each time. The main cost is that they are a separate hierarchy from `java.util`: values cross the boundary through explicit conversions, and half-adopting it across a codebase is worse than committing to one side. Reach for it where typed error handling and immutability are central and the team is comfortable with the style; much of what it once uniquely offered -- `Optional`, records, sealed types, pattern matching -- has since arrived in the language itself.

### Virtual threads
**Short:** JDK 21 lightweight threads scheduled by the JVM, letting blocking IO code scale to millions of tasks.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

A virtual thread is a thread whose stack lives on the heap as a continuation rather than in an operating-system thread's fixed allocation. When it blocks on something the runtime knows about, a socket read, a lock, a sleep, the JVM unmounts it, parks its stack and returns the carrier platform thread to a scheduler. The stack starts at a few hundred bytes and grows on demand, against a platform thread's roughly one megabyte of reserved stack plus a kernel scheduling entity.

The point is that thread-per-request blocking code, the style everyone can read and debug with real stack traces, now scales to concurrency that previously demanded reactive style. Two costs matter. Pinning: a virtual thread that cannot be unmounted holds its carrier and starves the pool, and while newer JDKs removed the synchronized-block case, native frames still pin. And they do nothing for CPU-bound work, where a fixed pool remains correct.

### Virtual threads (Thread.ofVirtual())
**Short:** Java 21 JVM-scheduled lightweight threads letting blocking code scale to millions of concurrent tasks.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @3

### vmstat
**Short:** Linux CLI sampling memory, swap, I/O and CPU counters over an interval; the first look at system saturation.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

It reads kernel counters and prints, per interval, six groups on one line: runnable and uninterruptibly blocked process counts, memory free and cache, swap in and out, block I/O in and out, interrupts and context switches, and the CPU split into user, system, idle, iowait and steal. The first line is the average since boot rather than current activity and should be ignored.

The value is that one screen separates four saturation stories that look identical from inside the application. A run queue persistently above the core count is CPU saturation. Nonzero swap in and out turns memory pressure into disk latency and is nearly always the worst item on the list. Large block I/O with high iowait is an I/O-bound workload. And a high context-switch rate with low user time is thrash from far too many threads.

### VPN
**Short:** Encrypted tunnel joining networks; in cloud designs the site-to-site link giving a VPC reach into on-prem.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, platform-delivery/cloud-platform-and-cost @2, security/authentication-and-identity @3

A VPN carries private-network traffic inside an encrypted tunnel across a network you do not trust, and the two shapes matter differently. A site-to-site tunnel joins two networks by terminating on a gateway at each end with routes pointing the relevant prefixes at it, so hosts on either side address each other as if adjacent. A remote-access VPN terminates on a user's device instead. IPsec and WireGuard operate at the IP layer, while TLS-based options tunnel over port 443.

In cloud design it is the pragmatic bridge during a migration, giving a VPC reach to an on-premises database in hours rather than the weeks a circuit takes. The costs explain why it is usually temporary: the tunnel runs over the public internet, so throughput is unpredictable and a single tunnel is capped by its gateway, redundancy needs a second tunnel and dynamic routing, and overlapping address ranges are a painful blocker.

### walrus operator :=
**Short:** Python 3.8 assignment expression that binds a name and yields its value inside a condition or comprehension.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

### weakref
**Short:** CPython module for references that do not keep an object alive; WeakSet and WeakValueDictionary stop listener leaks.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/memory-processes-and-os @1, apis-frameworks/design-patterns-and-principles @2

### Wireshark
**Short:** GUI packet capture and protocol analyzer; dissects HTTP/2, QUIC, WebSocket and TLS traffic down to the frame.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

Wireshark captures frames from an interface, or opens a `pcap` someone else took, and dissects every layer, so you can follow a TCP stream as it was reassembled, read the TLS handshake and the cipher that was negotiated, and inspect individual HTTP/2 or WebSocket frames. Display filters such as `tcp.port == 443 && http2` are how you find anything at all in a capture of a busy host.

Encrypted traffic you own becomes readable by pointing it at the key log file a client writes when `SSLKEYLOGFILE` is set, which is the standard way to look inside TLS 1.3 and QUIC. Reach for it when the logs on the two ends disagree and you need ground truth about what crossed the wire — retransmissions, a zero window, an RST, a handshake that failed for a reason neither side logged. In production the usual pattern is to capture headlessly with `tcpdump` on the server and analyze the file locally.

### Wireshark TCP stream stats
**Short:** Wireshark's TCP stream graphs: round-trip time, retransmissions and window size over the life of a connection.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @2

The stream graphs plot one direction of one conversation over the life of the capture: a time-sequence graph showing bytes sent against time alongside the receiver's advertised window, a throughput graph, and a round-trip-time graph derived from each segment and its acknowledgement. Separately, the expert analysis tags segments as retransmissions, duplicate acknowledgements, out-of-order or zero-window, so one display filter pulls every anomaly out of a large capture.

These turn a vague complaint that a transfer is slow into a named cause. A sequence graph flattening against a window line that keeps closing is a receiver too slow to drain its buffer, an application problem at the far end rather than a network one. A staircase punctuated by retransmissions is loss driving congestion control down. You need a capture taken near the right end, since loss looks different from each side.

### yield
**Short:** Python keyword that suspends a function and hands a value to the caller, making it a lazy generator.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @2

### yield from
**Short:** Python generator delegation (PEP 380): forwards iteration, sends and exceptions to a sub-generator.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/concurrency-and-async @2

### Z3
**Short:** Microsoft Research SMT solver over arithmetic, arrays and bitvectors, used for verification and constraint solving.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

You assert constraints over typed variables (integers, reals, bitvectors, arrays, uninterpreted functions) and ask whether they can all hold; Z3 answers `sat` with a concrete model, `unsat`, or `unknown`, which turns "does an input exist that makes this go wrong?" into something a machine decides rather than something a team argues about. That is why it sits underneath symbolic execution, program verification, exploit generation and some type checkers, and it is directly usable through its Python bindings for scheduling, packing and configuration problems. It is MIT-licensed and very capable, but satisfiability over these theories is undecidable in general, so expect `unknown` or a timeout on nonlinear arithmetic and heavy quantifiers and encode problems with that in mind.

### zoneinfo
**Short:** Python stdlib access to the IANA time zone database for timezone-aware datetimes.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### ZoneRules
**Short:** java.time class holding a zone's UTC offsets and DST transition rules from the JDK's bundled tzdb.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/runtime-internals-and-types @2
