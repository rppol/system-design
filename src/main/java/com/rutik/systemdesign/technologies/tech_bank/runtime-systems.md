# Runtime & OS — technology bank

<!-- tech-bank tier: runtime-systems -->

The 484 tools whose PRIMARY role — the first, best-weighted one — sits in
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

### aioitertools
**Short:** Async counterparts of itertools for async generators; used to build streaming asyncio pipelines.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/collections-and-algorithms @2

### aiomonitor
**Short:** Live introspection for a running asyncio loop over a telnet REPL: task list, ready-queue length and stack dumps.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

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

### anyio 4.x
**Short:** Backend-agnostic async library giving task groups and structured cancel scopes on top of asyncio or trio.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

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

### Apache Pekko (or Akka) actors
**Short:** JVM actor toolkit: single-threaded actors with mailboxes and supervision hierarchies for message-passing concurrency.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

### Apache Pekko (or Akka) FSM
**Short:** Actor-based finite state machine where mailbox-serialized messages drive transitions, so no locking is needed.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @2

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

### array module
**Short:** Python stdlib module giving compact, typed, C-backed numeric arrays instead of boxed lists.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/memory-processes-and-os @3

### array of ints
**Short:** A plain int array used as a compact bit set - the hand-rolled backing for Bloom filters and bitmask DP.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

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

### asyncio.TaskGroup [3.11]
**Short:** Python 3.11 structured-concurrency scope that awaits child tasks and raises an ExceptionGroup on failure.
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

### AVX-512 VNNI
**Short:** x86 instruction-set extension fusing INT8 multiply-accumulate, accelerating quantized inference on Xeon.
**Kind:** spec
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, inference/quantization-and-compression @2, inference/compiler-and-runtime-optimization @3

### awk
**Short:** Pattern-action text processing language for column extraction and record-level transformation in shell pipelines.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/version-control-and-workbench @3

### AWS VPC Reachability Analyzer
**Short:** AWS service that traces the virtual path between two VPC resources and names the rule blocking it.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/alerting-and-incident-response @3, security/authorization-and-policy @3

### B+Tree index
**Short:** Balanced, high-fanout tree with linked leaves; the default database index because it serves range scans in O(log n).
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, data-stores/key-value-and-embedded @2, data-stores/relational @3

### beartype
**Short:** Near-zero-overhead runtime type checker that enforces Python annotations at call time.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/static-analysis-and-linting @3

### big-O Python library
**Short:** Runs a function over growing inputs and fits the timings to infer its empirical complexity class.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, observability/profiling-and-performance @2

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

### bisect module
**Short:** Python stdlib binary search and sorted-insert helpers over an already-sorted sequence in O(log n).
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### Bitset
**Short:** Compact bit-array structure storing one bit per element; the backing for Bloom filters and bitmask dynamic programming.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

### BlockHound
**Short:** Java agent that instruments the JDK to throw when blocking calls run on Reactor/Netty non-blocking threads.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, devtools/testing-and-mocking @2

### boltons
**Short:** Pure-Python utility collection filling stdlib gaps, including iterutils helpers like chunked_iter and windowed_iter.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

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

### cgroups cpu.cfs_quota_us
**Short:** Linux cgroup knob setting a hard CPU ceiling per scheduling period; what Docker --cpus writes underneath.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### cgroups cpu.shares
**Short:** Linux cgroup knob setting a group's relative CPU weight under contention; behind Docker's --cpu-shares.
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

### chars
**Short:** Java String.chars() streaming UTF-16 code units; contrast with codePoints() when correctness above the BMP matters.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1

### charset-normalizer
**Short:** Pure-Python statistical character-encoding detector; heuristic replacement for chardet.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1

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

### chrt
**Short:** Linux CLI that sets a process's real-time scheduling policy and priority (SCHED_FIFO, SCHED_RR, SCHED_DEADLINE).
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

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

### Compact strings
**Short:** JDK 9 JEP 254: String backed by byte[] plus a coder, storing Latin-1 text at one byte per character.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/text-encoding-and-regex @2

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

### concurrent.futures.ProcessPoolExecutor
**Short:** Python executor that runs work in separate processes, sidestepping the GIL for CPU-bound tasks behind a futures API.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/memory-processes-and-os @2

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

### Counter
**Short:** Python stdlib dict subclass for frequency counting, with arithmetic and most_common(k).
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

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

### curl --http3
**Short:** curl flag that forces an HTTP/3 over QUIC request, for testing HTTP/3 endpoints from the command line.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @3, devtools/testing-and-mocking @3

### curl -v
**Short:** Verbose curl invocation that prints the request/response headers and TLS handshake for endpoint debugging.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @3

### curl -v --http2
**Short:** curl invocation that negotiates HTTP/2 and prints the handshake, so you can verify protocol and headers.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @2, devtools/testing-and-mocking @3

### Cython with nogil
**Short:** Compiles annotated Python to C and releases the GIL inside nogil blocks so CPU hotspots run truly in parallel.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, devtools/compiler-toolchain-and-codegen @2, observability/profiling-and-performance @3

### cytoolz
**Short:** C-accelerated build of toolz: lazy functional utilities for composing iterator pipelines over Python data.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1

### dataclasses
**Short:** Python stdlib decorator that generates init/repr/eq from annotations, giving structured mutable record types.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/collections-and-algorithms @2, apis-frameworks/design-patterns-and-principles @3

### dataclasses [3.7]
**Short:** Python decorator generating __init__, __repr__, __eq__ and optional slots/frozen from annotated class attributes.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/data-formats-and-api-contracts @3

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

### dk.brics.automaton
**Short:** Java library building explicit DFAs and NFAs from regular expressions, with determinization and minimization.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/collections-and-algorithms @3

### dmesg
**Short:** Prints the Linux kernel ring buffer - the place OOM kills, driver faults and hardware errors show up first.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/logging @2

### DoubleStream
**Short:** Primitive-specialized Java stream of doubles; avoids boxing and offers sum, average and summaryStatistics.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, observability/profiling-and-performance @3

### Eclipse Collections persistent collections
**Short:** Immutable JVM collections using structural sharing, so a copy reuses unchanged parts instead of duplicating them.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### Eclipse Collections primitive collections
**Short:** Eclipse Collections' int/long/double collections that store primitives unboxed, cutting memory over boxed types.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

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

### Executors
**Short:** java.util.concurrent factory class for thread pools, scheduled pools and virtual-thread-per-task executors.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, apis-frameworks/design-patterns-and-principles @3

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

### G1 for native
**Short:** GraalVM Native Image option using the G1 collector instead of Serial GC, for large-heap native Java services.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, devtools/compiler-toolchain-and-codegen @2

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

### google/re2
**Short:** DFA-based regex engine with linear-time matching, so untrusted patterns cannot backtrack catastrophically.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/text-encoding-and-regex @1, security/supply-chain-and-runtime-security @3, runtime-systems/collections-and-algorithms @3

### GraalVM Polyglot
**Short:** Embeds JavaScript, Python or Ruby inside a JVM app with host-access control and resource limits.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2, security/supply-chain-and-runtime-security @3

### greenlet
**Short:** Low-level coroutine primitive with switchable stacks; powers SQLAlchemy's sync-inside-async bridge.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, data-access/orm-and-data-mapping @3

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

### hand-written META-INF/services
**Short:** Manually authored ServiceLoader registration file listing implementations of a service interface on the classpath.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/dependency-injection-and-config @3

### hashids
**Short:** Library encoding integer ids into short reversible strings; obfuscation for public URLs, never a security boundary.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, data-access/schema-and-migration @2, security/privacy-and-compliance @3

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

### HTTP/2
**Short:** Binary, multiplexed HTTP revision with header compression and server push over a single TCP connection.
**Kind:** spec
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @2, apis-frameworks/rpc-graphql-and-streaming @3

### hwloc
**Short:** Portable hardware locality library and CLI that renders CPU, cache and NUMA topology for pinning decisions.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### IANA tzdata
**Short:** The IANA time-zone database of offsets and DST transition rules, shipped with the JDK and most OSes.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/runtime-internals-and-types @3

### iconv
**Short:** POSIX library and CLI that converts a byte stream or file between two named character encodings.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

### ICU
**Short:** Reference Unicode library (C/C++/Java) for normalization, collation, segmentation and transliteration.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

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

### ip
**Short:** The iproute2 CLI for inspecting and configuring Linux interfaces, addresses, routes and neighbours; replaces ifconfig.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

### ip tcp_metrics
**Short:** iproute2 subcommand showing the kernel's cached per-peer TCP metrics (cwnd, RTT, ssthresh) used to warm new connections.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

### iperf3
**Short:** Client-server tool that saturates a link to measure achievable TCP or UDP throughput, jitter and loss.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, devtools/testing-and-mocking @2

### iperf3 -c server
**Short:** iperf3 client mode: drives a TCP or UDP stream against a server to measure achievable throughput.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, devtools/testing-and-mocking @3

### iptables
**Short:** Linux netfilter CLI configuring L3/L4 packet filtering, NAT and port-forwarding rules inside the kernel.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/supply-chain-and-runtime-security @3, traffic-edge/proxy-and-load-balancer @3

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

### java.util.ArrayDeque
**Short:** Resizable circular-array deque; the fastest Java stack and queue and the standard iterative DFS/BFS structure.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

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

### java.util.concurrent.locks.ReentrantLock
**Short:** JDK mutex offering tryLock, timed acquisition and optional fairness, beyond what synchronized provides.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

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

### java.util.stream.Collectors
**Short:** The JDK's built-in terminal collectors for streams: toList, groupingBy, joining, partitioningBy and friends.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### java.util.stream.Stream
**Short:** Java's core stream API: lazy pipelines of map/filter/reduce over a source, with an opt-in parallel execution mode.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/concurrency-and-async @3

### java.util.TreeMap
**Short:** Red-black tree map giving sorted iteration and O(log n) floor, ceiling, headMap and subMap range queries.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### javap -p -s <class>
**Short:** JDK disassembler printing the erased type descriptors and members the JVM actually links against.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

### jcmd <pid> GC.class_histogram
**Short:** jcmd diagnostic printing live object count and bytes per class; the fast first check before a full heap dump.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

### JFR event jdk.VirtualThreadPinned
**Short:** JFR event that fires when a virtual thread pins its carrier, the main scalability trap in Loom code.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/profiling-and-performance @2

### jstat -gcutil <pid> 1s
**Short:** JDK command printing live heap-region utilization and GC counts each second, with no agent attached.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2, runtime-systems/runtime-internals-and-types @3

### JVM stack size -Xss
**Short:** JVM flag setting per-thread stack size (default ~512 KB-1 MB); raise it for deeply recursive algorithms.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/concurrency-and-async @2

### LAPACK
**Short:** Fortran linear-algebra library (solvers, factorizations, eigenproblems) sitting under NumPy and SciPy.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1, gpu/gpu-math-libraries @3

### ldd
**Short:** Prints the shared libraries a dynamically linked binary needs and where the loader resolves each one.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/compiler-toolchain-and-codegen @2

### Levenshtein PyPI package
**Short:** C-extension edit-distance and string-similarity library, roughly 100x faster than a pure-Python dynamic program.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, runtime-systems/text-encoding-and-regex @2

### Linked list sentinel/dummy node
**Short:** A placeholder head node that removes special-case handling for empty lists and head deletion in linked-list code.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

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

### LMAX Disruptor
**Short:** Lock-free ring-buffer inter-thread messaging library; powers Log4j2 async loggers at very low latency.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/logging @3, data-movement/message-broker @3

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

### lsof -i
**Short:** lsof invocation listing open network sockets with owning process, port and connection state.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

### lsquic
**Short:** LiteSpeed's C implementation of QUIC and HTTP/3, embeddable as a client or server transport library.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @2

### lstopo
**Short:** hwloc CLI that draws the machine's CPU, cache and NUMA topology so you can pin threads sensibly.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### ltrace
**Short:** Linux tracer that prints a process's library and system calls, answering why a process appears stuck.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @2

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

### MiniSat, Glucose, CryptoMiniSat, Kissat
**Short:** The standard CDCL SAT solvers: take a CNF formula and find a satisfying assignment or prove there is none.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/collections-and-algorithms @1

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

### mpstat
**Short:** sysstat CLI printing per-CPU utilization breakdowns over time; first stop for CPU saturation and core imbalance.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

### msquic
**Short:** Microsoft's cross-platform C implementation of the QUIC transport, used as the base for HTTP/3 stacks.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @3

### mtr
**Short:** Combined traceroute and ping that continuously reports per-hop loss and latency along a network path.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

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

### netstat
**Short:** Classic CLI that lists sockets, listening ports, connection states, TIME_WAIT counts and listen-queue depth.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

### netstat -s
**Short:** Per-protocol network statistics dump showing retransmits, resets and drops accumulated by the TCP/IP stack.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/metrics-and-monitoring @3

### Netty
**Short:** Asynchronous NIO network framework underlying WebFlux, gRPC-Java and high-throughput WebSocket/UDP/QUIC servers.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @2, runtime-systems/concurrency-and-async @3, apis-frameworks/web-framework-and-http-client @3

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

### nftables
**Short:** The modern Linux packet filtering and NAT framework that replaces iptables, with one unified rule syntax.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/supply-chain-and-runtime-security @3, traffic-edge/proxy-and-load-balancer @3

### nghttp2
**Short:** HTTP/2 C library shipping client, server and debug CLIs for inspecting frames, streams and HPACK on the wire.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @2, apis-frameworks/rpc-graphql-and-streaming @3

### nghttp3
**Short:** C library implementing HTTP/3 framing and QPACK on top of a QUIC transport such as ngtcp2.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/web-framework-and-http-client @3

### ngtcp2
**Short:** C library implementing the QUIC transport protocol, pairing with a separate TLS library for HTTP/3 stacks.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/io-networking-and-syscalls @1, apis-frameworks/rpc-graphql-and-streaming @3

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

### nmap --scan-flags SYN
**Short:** nmap invocation sending bare SYN packets to detect open ports without completing the TCP handshake.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/supply-chain-and-runtime-security @2

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

### nslookup
**Short:** CLI DNS query tool for resolving names and debugging resolver, record and cluster-DNS problems.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, traffic-edge/service-mesh-and-discovery @2

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

### numastat
**Short:** Linux CLI reporting per-NUMA-node allocation and hit/miss counts; shows memory landing on a remote node.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

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

### O(1) strategy
**Short:** beartype's default runtime type-check strategy: spot-check one element of a container instead of every element.
**Kind:** concept
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

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

### openssl s_client
**Short:** OpenSSL CLI that opens a TLS connection and dumps the handshake, cipher, certificate chain and expiry.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, security/secrets-and-cryptography @2

### operator
**Short:** Python stdlib module of function forms of the operators, e.g. itemgetter and attrgetter as sort and map keys.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### OR-Tools CP-SAT, Gurobi, CPLEX
**Short:** Constraint-programming and integer-programming solvers for scheduling, routing, packing and coloring at scale.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

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

### pgrep
**Short:** Linux CLI that finds process IDs by name, user or other attributes for scripting and quick lookups.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

### ping
**Short:** ICMP echo utility for testing host reachability and round-trip latency.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

### Pingouin
**Short:** User-friendly Python statistics package: hypothesis tests, effect sizes, power analysis and tidy result tables.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @3

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

### PriorityQueue
**Short:** Java's binary min-heap; ordered by comparator on poll, not FIFO despite the name.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

### Process management
**Short:** Supervising worker processes for a server: spawning, restarting and reaping them via a built-in master or systemd.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, apis-frameworks/web-framework-and-http-client @3

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

### pyparsing
**Short:** Python library for building recursive-descent grammars in code, an alternative to unreadable regexes.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/compiler-toolchain-and-codegen @2

### Python
**Short:** General-purpose interpreted language; the default escalation when shell automation outgrows shell scripts.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/version-control-and-workbench @3

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

### python3.14t
**Short:** The free-threaded CPython 3.14 build with the GIL removed, so CPU-bound Python threads run in parallel.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, runtime-systems/concurrency-and-async @2

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

### re module
**Short:** Python's regular expression engine - a backtracking NFA, so adversarial patterns can blow up to exponential time.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, runtime-systems/collections-and-algorithms @3

### RE2
**Short:** Google's automaton-simulating regex engine: linear-time matching, immune to catastrophic backtracking.
**Kind:** tech
**Lang:** cpp
**Roles:** runtime-systems/text-encoding-and-regex @1

### RE2/J
**Short:** Java port of RE2 giving linear-time regex matching - no catastrophic backtracking - at the cost of lookaround.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/text-encoding-and-regex @1, traffic-edge/rate-limiting-and-resilience @3

### Reactor Core
**Short:** Reactive Streams implementation for the JVM: Flux and Mono, operators, backpressure and schedulers.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

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

### ReactorDebugAgent
**Short:** Java agent instrumenting Reactor operators to capture assembly-time stack traces, making reactive traces readable.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1, observability/tracing-apm-and-llm-observability @2, observability/profiling-and-performance @3

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

### regex101
**Short:** Web regex workbench with step-by-step match debugging and a backtracking step counter for catastrophic patterns.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1, devtools/version-control-and-workbench @3

### renice
**Short:** Unix CLI that changes a running process's nice value (-20 to +19); only root can raise priority.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1

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

### RxJava 3
**Short:** JVM reactive-streams library of composable Observable/Flowable operators with backpressure; common on Android.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

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

### SciPy stats
**Short:** SciPy's statistics submodule: probability distributions, hypothesis tests, confidence intervals, entropy and KL.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, ml-lifecycle/evaluation-and-benchmarks @2

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

### scipy.spatial.distance
**Short:** SciPy module of pairwise distance metrics (cdist/pdist: euclidean, cosine, hamming, edit-like) over vectors.
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

### Segment tree
**Short:** Tree structure supporting range aggregate queries and point updates in O(log n) over an array.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/collections-and-algorithms @1

### send
**Short:** Generator method from PEP 342 that pushes a value into a paused generator, making coroutines bidirectional.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1, runtime-systems/runtime-internals-and-types @3

### ServiceLoader
**Short:** JDK service discovery API that loads implementations declared by module provides clauses or META-INF/services.
**Kind:** api
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/dependency-injection-and-config @2, apis-frameworks/design-patterns-and-principles @3

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

### SortedDict
**Short:** sortedcontainers' sorted mapping: O(log n) lookup with sorted iteration; not part of the Python stdlib.
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

### ss
**Short:** Linux CLI that lists sockets and their state: listening ports, TIME_WAIT counts, accept-queue depth.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

### ss -tan
**Short:** Linux socket-statistics command listing every TCP socket with its state and addresses; the modern netstat.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @2

### ss -tn
**Short:** Linux ss invocation listing real TCP sockets numerically, used to see actual connections to a database.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, data-access/drivers-and-connection-pooling @3

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

### struct module
**Short:** Python stdlib module packing and unpacking binary data with explicit endianness and C-style field layouts.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/text-encoding-and-regex @1, apis-frameworks/data-formats-and-api-contracts @2, runtime-systems/io-networking-and-syscalls @3

### Structured Concurrency
**Short:** Concurrency model binding a task's lifetime to a lexical scope, so no subtask outlives it and errors propagate.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/concurrency-and-async @1

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

### taskset
**Short:** Linux command that pins a process or thread to specific CPU cores, cutting cache misses in latency-sensitive services.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @3

### tcpdump
**Short:** Command-line packet capture; the low-overhead ground truth for what actually crossed the wire.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

### tcpdump -i eth0 port 5432
**Short:** tcpdump invocation capturing PostgreSQL wire traffic on eth0 to inspect connection churn, TLS and stalls.
**Kind:** api
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

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

### ThreeTen-Extra
**Short:** Add-on types for java.time by its author: Interval, Quarter, DayOfMonth and other calendar values the JDK omits.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/collections-and-algorithms @1

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

### toolz
**Short:** Functional utility library for Python: lazy pipe, compose, curry and iterator combinators; cytoolz is the C build.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/collections-and-algorithms @1, apis-frameworks/design-patterns-and-principles @3

### top
**Short:** Live Unix process and resource monitor - load average, per-process CPU and memory, threads with the htop H toggle.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

### tracepath
**Short:** Traces the hop-by-hop path to a host and discovers the path MTU, without needing root like traceroute.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1

### traceroute
**Short:** CLI that maps the hop-by-hop path to a host and shows per-hop latency and loss; also used for MTU discovery.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

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

### trio 0.33
**Short:** Alternative Python async runtime built on structured concurrency: nurseries and strict cancel-scope semantics.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/concurrency-and-async @1

### typeguard
**Short:** Runtime type checker that enforces annotations on call, using ABCMeta virtual subclass checks.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, devtools/testing-and-mocking @3

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

### typing.Protocol [3.8]
**Short:** Python structural subtyping: a class satisfies a Protocol by having the right methods, with no inheritance needed.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1, apis-frameworks/design-patterns-and-principles @2

### typing_extensions
**Short:** Backports newer typing constructs (TypeIs, PEP 696 defaults, override) to still-supported older Pythons.
**Kind:** tech
**Lang:** python
**Roles:** runtime-systems/runtime-internals-and-types @1

### tzupdater
**Short:** Oracle tool patching a JDK's bundled tzdata so date handling stays correct when governments change DST rules.
**Kind:** tech
**Lang:** java
**Roles:** runtime-systems/runtime-internals-and-types @1

### Unicode Character Database
**Short:** The Unicode Consortium's authoritative data files defining every code point's category and properties.
**Kind:** spec
**Lang:** *
**Roles:** runtime-systems/text-encoding-and-regex @1

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

### Virtual threads
**Short:** JDK 21 lightweight threads scheduled by the JVM, letting blocking IO code scale to millions of tasks.
**Kind:** concept
**Lang:** java
**Roles:** runtime-systems/concurrency-and-async @1

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

### vmstat 1
**Short:** Per-second virtual memory statistics; nonzero si/so columns mean the machine is swapping and thrashing.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/memory-processes-and-os @1, observability/profiling-and-performance @2

### VPN
**Short:** Encrypted tunnel joining networks; in cloud designs the site-to-site link giving a VPC reach into on-prem.
**Kind:** concept
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, platform-delivery/cloud-platform-and-cost @2, security/authentication-and-identity @3

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

### weakref module
**Short:** Python stdlib weak references and weak containers: cache or observe an object without keeping it alive.
**Kind:** api
**Lang:** python
**Roles:** runtime-systems/memory-processes-and-os @1, runtime-systems/runtime-internals-and-types @2, runtime-systems/collections-and-algorithms @3

### Wireshark
**Short:** GUI packet capture and protocol analyzer; dissects HTTP/2, QUIC, WebSocket and TLS traffic down to the frame.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @3

### Wireshark TCP stream stats
**Short:** Wireshark's TCP stream graphs: round-trip time, retransmissions and window size over the life of a connection.
**Kind:** tech
**Lang:** *
**Roles:** runtime-systems/io-networking-and-syscalls @1, observability/profiling-and-performance @2

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
