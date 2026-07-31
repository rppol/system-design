# GPU & parallel — technology bank

<!-- tech-bank tier: gpu -->

The 200 tools whose PRIMARY role — the first, best-weighted one — sits in
the **GPU & parallel** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### activemask
**Short:** CUDA warp intrinsic returning the mask of currently converged lanes, the basis for warp-vote and shuffle patterns.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### AMX
**Short:** Intel Advanced Matrix Extensions: tile-based BF16 and INT8 matrix instructions on Sapphire Rapids and later Xeon.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-portability-and-precision @1, inference/compiler-and-runtime-optimization @2, runtime-systems/memory-processes-and-os @3

The extension adds eight two-dimensional tile registers, each 1 KB laid out as 16 rows of 64 bytes, plus a TMUL unit that multiplies two tiles and accumulates into a third in FP32. A configuration register declares how many rows and column bytes are actually in use, so the same registers hold a BF16 block or a wider INT8 one; software loads tiles with `tileloadd`, issues `tdpbf16ps` or `tdpbusd`, and stores the accumulator back.

It matters when inference has to run on CPU: an INT8 or BF16 GEMM through oneDNN, OpenVINO or PyTorch reaches throughput AVX-512 cannot, with no accelerator in the box. It needs Sapphire Rapids or later Xeon plus kernel support for the AMX register state, and the advantage disappears for anything not shaped like a matrix multiply, where the memory system is the limit.

### Apex
**Short:** NVIDIA PyTorch extension with fused optimizers such as FusedAdam and the original mixed-precision training tools.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, model-training/distributed-training @2, model-training/deep-learning-framework @3

Apex has been outlived by most of what it introduced. What remains useful is the fused CUDA kernels: `FusedAdam` and `FusedLAMB` collapse the optimizer's elementwise math into one launch over flattened parameter buffers, and `FusedLayerNorm` and fused softmax cut launch count and memory traffic inside transformer blocks. Its distributed data-parallel wrapper overlapped gradient reduction with the backward pass before core PyTorch did.

The `apex.amp` mixed-precision layer, with its O0 through O3 opt levels, is deprecated; `torch.amp` is the supported path and is built into PyTorch. Reach for Apex only when a framework still imports a specific fused kernel from it, as Megatron-LM and older NeMo builds do, and expect to compile it from source against your exact CUDA and PyTorch versions.

### atomicAdd
**Short:** CUDA device intrinsic doing a read-modify-write add atomically; the building block for counters and ready flags.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### atomicAdd/atomicCAS/... intrinsics
**Short:** CUDA device intrinsics for hardware read-modify-write on global or shared memory; support varies by architecture.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### atomicCAS
**Short:** CUDA device intrinsic doing an atomic compare-and-swap, the building block for locks, counters and ring buffers.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @3

### atomicExch
**Short:** CUDA atomic exchange intrinsic used to build work-queue counters, locks and ring-buffer ready flags.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @3

### ballot_sync
**Short:** CUDA warp-vote intrinsic returning the mask of lanes whose predicate is true; base of warp-level patterns.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### Codeplay
**Short:** Vendor of the oneAPI SYCL-for-CUDA and SYCL-for-HIP plugins, letting one SYCL source target NVIDIA and AMD GPUs.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2

Codeplay builds the compiler plugins that give the oneAPI DPC++ toolchain backends beyond Intel's own hardware: install the NVIDIA plugin and `icpx -fsycl -fsycl-targets=nvptx64-nvidia-cuda` lowers SYCL kernels to PTX, or the AMD plugin lowers them to GCN, from unmodified single-source C++. The company is part of Intel and also maintains oneMKL and oneDNN interface layers that dispatch to cuBLAS or rocBLAS underneath.

This is the path when one SYCL codebase must run on Intel, NVIDIA and AMD accelerators instead of maintaining CUDA and HIP versions side by side. The catch is performance rather than correctness: a kernel tuned for one vendor's memory hierarchy and subgroup width rarely lands at native speed on another, so benchmark on every target before committing to the portability story.

### community SYCL backends
**Short:** SYCL-for-CUDA and SYCL-for-HIP plugins that let one SYCL source target NVIDIA and AMD GPUs, not just Intel.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1

A SYCL implementation splits between a compiler front end and per-target backends, and these plugins supply the backends the vendor does not ship: the CUDA plugin lowers kernels through the NVPTX target to PTX, the HIP plugin to AMD GCN. Nothing in the SYCL source changes, the target list is a compiler flag, and ahead-of-time compilation for several targets produces one binary that picks a device at run time.

They exist so a vendor-neutral codebase can keep a single kernel source across a mixed fleet. Expect a lag behind each vendor's own toolchain, since new hardware features and recent SYCL extensions land here last, and expect to leave performance on the table against hand-tuned CUDA or HIP. Where one vendor dominates the deployment, its native stack is still the faster answer.

### compute-sanitizer
**Short:** CUDA Toolkit runtime checker whose memcheck, racecheck, synccheck and initcheck tools catch kernel OOB access and races.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, devtools/testing-and-mocking @3

Run the binary under it and device code is instrumented so a fault is reported with the kernel, the offending thread and address, and a stack trace instead of a silent wrong answer or a much later crash. `memcheck` finds out-of-bounds and misaligned accesses, `racecheck` finds shared-memory data races from a missing `__syncthreads()`, `synccheck` finds illegal or divergent barrier usage, and `initcheck` finds reads of uninitialized device memory. It ships with the CUDA Toolkit and replaces the old cuda-memcheck.

Compile with `-lineinfo` so reports point at source lines, and expect a substantial slowdown - this is a targeted run on a small input, not something left enabled. The tools worth reaching for first are `racecheck` and `synccheck`, because those bugs are timing- and input-dependent: they pass a thousand times and then produce garbage on a different GPU or a different block size.

### compute-sanitizer --tool racecheck
**Short:** NVIDIA compute-sanitizer mode detecting shared-memory data races from missing or misplaced kernel barriers.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

### compute-sanitizer --tool synccheck
**Short:** CUDA sanitizer mode that flags illegal or divergent barrier use before it deadlocks a kernel.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

### compute-sanitizer racecheck
**Short:** CUDA compute-sanitizer mode that detects shared-memory data races caused by missing __syncthreads().
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

The tool instruments shared-memory accesses and reports pairs that conflict: a write and a read, or two writes, reaching the same address from different threads of a block with no `__syncthreads()` ordering them. Both source locations are named, and a hazard-analysis mode points at the barrier that should have been there rather than only listing the addresses involved.

Reach for it whenever a kernel using shared memory returns an answer that changes with block size, GPU model or a rebuild, because that is what an unsynchronized tile handoff looks like. Compile with `-lineinfo` so hazards map to source lines, and run the smallest input that reproduces: instrumenting every shared access costs an order of magnitude or more. It sees shared memory only, so global-memory races need different reasoning.

### Cooperative Groups
**Short:** CUDA API for composable thread groups and barriers, including warp tiles and grid-wide synchronization.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/multi-gpu-and-collectives @3

### Cooperative Groups coalesced_threads
**Short:** CUDA Cooperative Groups call capturing the currently converged threads; safer than hand-rolled activemask().
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cooperative_groups::grid_group::sync
**Short:** CUDA cooperative-groups call giving a true grid-wide barrier across all resident blocks of a kernel.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### CUB
**Short:** CUDA C++ template library of tuned warp/block/device primitives - reduce, scan, sort, histogram - usable in your kernel.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2, runtime-systems/collections-and-algorithms @3

It ships the same algorithms at three levels of granularity: warp-wide (`cub::WarpScan`), block-wide (`cub::BlockReduce`, `cub::BlockScan`) and device-wide (`cub::DeviceReduce`, `cub::DeviceRadixSort`, `cub::DeviceHistogram`), so you can call a whole-array primitive or drop a tuned block-level reduction into the middle of a kernel you wrote yourself. Each is templated on block size and items per thread and specialized per architecture, which is why a hand-rolled shuffle reduction usually loses once tail cases and a second GPU generation are accounted for. It is the layer Thrust sits on, and it now ships inside CCCL alongside Thrust and libcu++ in the CUDA Toolkit. Make it the default for reduction, scan, sort and histogram, and hand-write only when the operator or data layout genuinely does not fit.

### cuBLAS
**Short:** NVIDIA's tuned dense BLAS library for GPUs; the GEMM path behind torch.matmul and automatic Tensor Core routing.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @3

cuBLAS provides BLAS levels 1 through 3 on the GPU, and level-3 GEMM is the one that matters: `cublasGemmEx` and the newer `cublasLt` interface select a tiled kernel and, given suitable dtypes and alignment, route the multiply through tensor cores automatically. It is what `torch.matmul` and `nn.Linear` ultimately call, so most matmul performance observed in PyTorch is a cuBLAS heuristic choosing a kernel.

Two practical notes: it is column-major and 1-indexed, inherited from Fortran BLAS, so a row-major matrix is handled by swapping operands or transposing; and shapes that are not multiples of the tile size waste tensor-core throughput, which is why padding a hidden dimension to a multiple of 8 or 16 can make a model measurably faster.

### cuBLASLt
**Short:** NVIDIA's descriptor-based GEMM API above cuBLAS: fused epilogues, mixed precision, Tensor Core algorithm search.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-portability-and-precision @2

Where cuBLAS gives you one `gemm` call, cuBLASLt makes you build descriptors — the operation, each matrix layout, and a preference with a workspace budget — then ask `cublasLtMatmulAlgoGetHeuristic` for ranked candidate algorithms and run one. That indirection is the point: it allows mixed input, compute, and output precisions (FP16, BF16, or FP8 in with FP32 accumulation), unusual layouts and strides, and fused epilogues such as bias, ReLU, GELU, or scaling applied inside the GEMM, which removes an entire extra kernel launch and a round trip through global memory.

It is the path a framework's `matmul` and `nn.Linear` already take for Tensor Core work, and you link it separately with `-lcublasLt`. Reach for it when you are writing C++ that needs a fused or low-precision GEMM and the heuristic search is worth caching per shape; otherwise the framework is calling it on your behalf.

### CUBLASLT_LOG_LEVEL
**Short:** cuBLASLt environment variable that logs which matmul algorithm was chosen, confirming Tensor Core use.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-profiling-and-debugging @2

### cublasLtMatmulAlgoGetHeuristic
**Short:** cuBLASLt call that reports which matmul algorithm would be picked, confirming Tensor Core use without a profiler.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-profiling-and-debugging @2

### CUDA
**Short:** NVIDIA's GPU computing platform: C++ kernel language, nvcc toolchain, runtime/driver APIs and device libraries.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @3, gpu/gpu-math-libraries @3

The programming model is a grid of thread blocks, each block resident on one SM with its own shared memory and barrier, and threads executing in warps of 32 lanes. Around it sits a toolchain, with `nvcc` splitting host and device code, PTX as a forward-compatible intermediate and SASS as the real instruction set, two host APIs, and the math libraries most applications actually call: cuBLAS, cuDNN, cuFFT, cuSPARSE, Thrust and CUB.

It is the default because the ecosystem is here first, from every deep-learning framework to the profilers and kernel libraries. The costs are that it is NVIDIA-only, so portability means SYCL, HIP or an abstraction layer that trades ecosystem for reach, and that version coupling is an operational tax: driver, toolkit, cuDNN and the framework build all have to agree before anything runs.

### CUDA C++
**Short:** NVIDIA's C++ dialect for authoring device kernels with full control of memory, launch config and intrinsics.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @3

You mark functions `__global__`, `__device__` or `__host__`, launch with the triple-chevron syntax, and index work off `blockIdx`, `blockDim` and `threadIdx`. The dialect adds what the hardware exposes and standard C++ has no words for: `__shared__` tiles, `__syncthreads()`, warp shuffle and vote intrinsics, scoped atomics, texture and constant memory, and inline PTX. Templates, lambdas and much of the standard library work in device code within the compiler's supported subset.

Write it when you need control the layers above cannot express, such as a fused kernel, an unusual data layout, or an explicit shared-memory staging plan. The price is that every performance decision becomes yours: occupancy, coalescing, bank conflicts and register pressure. For block-level tiled kernels Triton usually reaches comparable speed with far less code, and for standard GEMM and convolution the vendor libraries already win.

### CUDA Driver API
**Short:** Low-level CUDA API for explicit context, module and launch control; how nvrtc-compiled PTX is loaded and launched.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @3, runtime-systems/runtime-internals-and-types @3

### CUDA Events
**Short:** GPU-side timestamps recorded into a stream; the correct way to time kernel execution without a full profiler.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### CUDA graphs
**Short:** CUDA feature that records a DAG of kernel launches once and replays it with near-zero per-launch CPU overhead.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, observability/profiling-and-performance @3

### CUDA P2P API
**Short:** CUDA peer-to-peer API letting one GPU read and write another GPU's memory directly within a node.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/multi-gpu-and-collectives @1, gpu/kernel-programming @2

### CUDA Runtime API
**Short:** The high-level CUDA API for device memory, streams and kernel launches; simpler than the Driver API.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

This is the `cudaXxx` layer in `libcudart` that most code uses. It creates a context implicitly on first use, so there is no explicit initialization step, and it hides module loading: kernels compiled into the binary by `nvcc` are launched by name through the triple-chevron syntax rather than looked up as handles. Memory, streams, events, graphs and peer access all have runtime entry points, and every call returns a `cudaError_t` that an asynchronous launch may only surface later.

Use it unless you specifically need what it hides. The Driver API is the alternative when contexts must be explicit, when modules are loaded from PTX or cubins produced at run time, or when a library must not assume anything about the process's context, which is why JIT-oriented stacks such as CuPy and PyCUDA sit on the driver layer instead.

### cuda-gdb
**Short:** Source-level device debugger for CUDA kernels: step threads and blocks, inspect shared memory, catch races.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

It extends gdb with device awareness: you break inside a kernel and then move the debugger's focus between threads and blocks, inspecting registers, local variables and shared memory for one specific lane. That is the only way to answer questions like which lane wrote the garbage value, or what the shared-memory tile actually contained when the kernel produced a wrong result.

It needs a build with device debug information, which disables optimization, so a bug that depends on timing or on a particular scheduling order can disappear under it. For out-of-bounds accesses, races and uninitialized reads, reach for compute-sanitizer first: it catches those at runtime on a normal build and usually names the offending line directly.

### cuda-memcheck
**Short:** Legacy CUDA memory checker for out-of-bounds and misaligned device accesses; superseded by compute-sanitizer.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

It ran an application under device-side instrumentation and reported out-of-bounds and misaligned accesses to global, shared and local memory, invalid device frees, and hardware exceptions, naming the offending block and thread. Sub-tools extended the same harness to shared-memory races, barrier misuse and reads of uninitialized device memory, which is the structure its successor kept unchanged.

It has been removed from the CUDA Toolkit and its functionality moved wholesale into `compute-sanitizer`, which has better SASS-level attribution on current architectures. If a script or a runbook still invokes it, substitute `compute-sanitizer --tool memcheck`; nothing is lost in the move, and on recent GPU generations the old binary will not run at all.

### cuda-memcheck/compute-sanitizer
**Short:** CUDA memory checker that catches out-of-bounds accesses, misaligned pointers and race conditions in kernels.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

Both names refer to one facility: a runtime that instruments device code so an illegal access is reported at the instruction that made it, with the kernel name, the block and thread, the address and a host stack trace, instead of surfacing as a corrupted result or an unrelated crash much later. Four sub-tools split the work, covering out-of-bounds and misalignment, shared-memory races, barrier misuse, and uninitialized reads.

Reach for it the moment a kernel's output depends on block size, GPU model or build flags. Build with `-lineinfo` for source attribution and run the smallest reproducing input, because instrumentation costs a large multiple of normal runtime. The older name is the retired binary; `compute-sanitizer` is what ships in current toolkits, and the sub-tool names carried over unchanged.

### cuda.laneid
**Short:** Python-side lane-id intrinsic for GPU kernels, compiling to the same PTX as the CUDA C++ warp primitives.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1

### cuda.syncthreads
**Short:** Numba CUDA block-level barrier, the Python equivalent of __syncthreads(); no grid-wide cooperative sync binding.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @3

### cuda.syncwarp()
**Short:** Numba CUDA warp-level barrier and lane-id API; compiles to the same PTX as the CUDA C++ __syncwarp intrinsic.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1

### CUDA_LAUNCH_BLOCKING=1
**Short:** Environment variable forcing synchronous kernel launches so errors report at the real call site; kills overlap.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

### cudaDeviceGetAttribute
**Short:** CUDA runtime call that queries one device limit at a time so kernels can size tiles to the actual GPU.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### cudaDeviceGetStreamPriorityRange
**Short:** CUDA runtime call returning the device's valid stream priority range; lower numeric value means higher priority.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaDeviceSetLimit
**Short:** CUDA runtime call that raises per-device limits such as device heap size and CDP nesting depth before first launch.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaDeviceSynchronize
**Short:** Blocks the host until all device work finishes and surfaces queued errors; kills concurrency vs per-stream sync.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### cudaEvent_t timers
**Short:** CUDA events recorded into a stream: the correct way to time kernels on the GPU clock, separately from host transfers.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### cudaEventCreate
**Short:** CUDA call allocating an event handle for ~0.5us GPU-side timestamping and cross-stream synchronization.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### cudaEventElapsedTime
**Short:** CUDA call measuring elapsed GPU time between two events at ~0.5us; the correct way to time kernels.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

### cudaEventRecord
**Short:** CUDA API that timestamps a point in a stream; the correct way to time GPU work at sub-microsecond resolution.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### cudaEventSynchronize
**Short:** CUDA call that blocks until an event is recorded; the correct way to time GPU work to ~0.5 us.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @2

### cudaFree
**Short:** CUDA runtime call releasing a device allocation made by cudaMalloc; pairs with it to avoid leaking GPU memory.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaFreeHost
**Short:** CUDA call that frees page-locked host memory previously obtained from cudaHostAlloc or cudaMallocHost.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaFuncAttributes
**Short:** CUDA struct reporting a compiled kernel's register count, shared memory and max block size at run time.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @2

### cudaFuncGetAttributes
**Short:** CUDA runtime call returning a compiled kernel's real register count, shared memory and max block size for launch tuning.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @2

### cudaFuncSetAttribute
**Short:** CUDA call that sets per-kernel attributes such as the dynamic shared-memory carveout above the default 48 KB cap.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaGetDeviceProperties
**Short:** CUDA call returning SM count, registers and shared memory per SM and compute capability instead of hardcoding.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### cudaGetLastError
**Short:** CUDA call that returns and clears the last error flag; the way to detect an asynchronous kernel launch failure.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### cudaGraphAddDependencies
**Short:** CUDA Graph API call that wires an explicit edge between graph nodes when building a launch DAG by hand.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaGraphAddKernelNode
**Short:** CUDA Graphs API for building a launch graph node by node, amortizing per-launch CPU overhead.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @3

### cudaGraphAddMemcpyNode
**Short:** CUDA Graph call adding an explicit memcpy node when building a graph node by node instead of by stream capture.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaGraphCreate
**Short:** CUDA API that builds a graph node by node so a repeated launch sequence replays with near-zero CPU overhead.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @3

### cudaGraphDestroy
**Short:** CUDA call that frees a captured graph or its instantiated executable, releasing the resources the graph held.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaGraphExecDestroy
**Short:** CUDA call releasing an instantiated graph executable and its resources when the captured launch sequence is retired.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaGraphExecKernelNodeSetParams
**Short:** Patches one node's kernel arguments inside an instantiated CUDA graph so it can be replayed without re-capturing.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaGraphExecUpdate
**Short:** CUDA call that patches an instantiated graph's node parameters in bulk when the topology is unchanged.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2

### cudaGraphInstantiate
**Short:** Compiles a captured cudaGraph_t into an executable graph, replaying many launches with one submission.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @3

### cudaGraphLaunch
**Short:** Replays an instantiated CUDA graph with one CPU call, removing per-kernel launch overhead in short repeated pipelines.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, observability/profiling-and-performance @3

### cudaHostAlloc
**Short:** CUDA call allocating pinned host memory so transfers can be async and reach full PCIe/NVLink bandwidth.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaHostGetDevicePointer
**Short:** Returns the device-side pointer aliasing mapped host memory, enabling zero-copy access from a kernel.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaLaunchCooperativeKernel
**Short:** The launch entry point required for cooperative-groups kernels that call grid.sync() for a grid-wide barrier.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/multi-gpu-and-collectives @3

### cudaLaunchHostFunc
**Short:** Enqueues a host callback into a CUDA stream; the callback must not call CUDA APIs itself.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @3

### cudaMalloc
**Short:** The basic CUDA runtime call allocating linear device-resident global memory, freed with cudaFree.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @2

### cudaMallocHost
**Short:** Allocates page-locked host memory so host-device copies can go asynchronously over DMA.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaMallocManaged
**Short:** Allocates CUDA Unified Memory addressable from host and device, with pages migrated on demand by the driver.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaMallocPitch
**Short:** CUDA allocator that pads 2D array rows to an aligned pitch so row-major access stays coalesced across rows.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaMemAdvise
**Short:** Unified-memory hint API marking a range read-mostly, setting its preferred location, or granting a device direct access.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @2

### cudaMemcpy
**Short:** CUDA host/device memory copy; the synchronous default form blocks the host until the transfer completes.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaMemcpy2D
**Short:** CUDA copy for pitched 2D regions, preserving row alignment so row-major access stays coalesced across row boundaries.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/memory-processes-and-os @3

### cudaMemcpyAsync
**Short:** CUDA call for stream-ordered host-device and device-device copies; truly asynchronous only from pinned memory.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/multi-gpu-and-collectives @3

### cudaMemPrefetchAsync
**Short:** CUDA call that migrates unified-memory pages to a device ahead of use, avoiding on-demand fault stalls.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaOccupancyMaxPotentialBlockSize
**Short:** CUDA Runtime call returning the block size and grid size that maximize theoretical occupancy for a kernel.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### cudaPeekAtLastError
**Short:** CUDA runtime call returning the last error without clearing it; the check to place right after an async kernel launch.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### cudaStreamBeginCapture
**Short:** Starts recording ordinary stream operations into a CUDA graph instead of executing them immediately.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @3

### cudaStreamCreate
**Short:** Creates a non-default CUDA stream so kernels and copies overlap; the NonBlocking flag opts out of legacy sync.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### cudaStreamCreateWithFlags
**Short:** Creates a non-default CUDA stream; the cudaStreamNonBlocking flag opts out of legacy default-stream sync.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaStreamCreateWithPriority
**Short:** Creates a CUDA stream with a scheduling priority, optionally non-blocking against the legacy default stream.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### cudaStreamEndCapture
**Short:** Ends CUDA stream capture and returns the recorded cudaGraph_t, turning a stream of launches into a replayable graph.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaStreamSynchronize
**Short:** CUDA call that blocks the host until one stream's queued work finishes, surfacing any deferred kernel errors.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### cudaStreamWaitEvent
**Short:** Makes one CUDA stream wait on an event recorded in another, giving partial ordering without a full device sync.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### cuDNN
**Short:** NVIDIA's tuned deep-learning primitive library: convolution, pooling, RNN, normalization and fused attention.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-portability-and-precision @3

cuDNN implements the primitives deep learning frameworks are built from -- convolution in its forward and both backward passes, pooling, normalization, activations, RNN cells, fused multi-head attention -- each with several algorithms whose relative speed depends on tensor shapes, layout and precision. That is why it offers both a heuristic mode that picks an algorithm from a model and a benchmark mode that times the candidates for your exact shapes and caches the winner (`torch.backends.cudnn.benchmark = True` is this switch), which pays off for fixed input sizes and hurts when shapes change every step. It routes eligible operations onto Tensor Cores automatically once the precision and dimension constraints are met, which is the mechanism behind most of the speedup mixed precision delivers. You almost never call it directly since PyTorch, TensorFlow and JAX link it for you -- but its version matters, because it must match the CUDA toolkit and is the usual culprit when a GPU container fails to load a shared library at import.

### cuFFT
**Short:** NVIDIA's GPU FFT library: plan-based 1D/2D/3D real and complex transforms, plans reused across calls.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1

Work is organized around a plan: you describe the transform once, giving dimensionality, sizes, batch count, in-place or out-of-place, real-to-complex or complex-to-complex, and the library selects algorithms and sizes its workspace at plan time so the repeated call is just an execute on the same handle. Lengths factorable into small primes take the fast radix paths, while a large prime factor falls back to a slower general algorithm.

Reach for it for signal processing, convolution through the frequency domain, and spectral solvers where the data is already on the device. Create plans once and reuse them, since planning is the expensive part, and watch the real-to-complex layout: the output is the half spectrum with a non-obvious padding rule, and misreading it is the usual source of wrong results.

### cuobjdump
**Short:** CUDA CLI that inspects fatbinaries and objects: SASS disassembly and the list of embedded architectures.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, devtools/compiler-toolchain-and-codegen @2

It reads the fatbinary embedded in an executable, an object file or a cubin and reports what is inside: the list of architectures compiled in, SASS disassembly for each, and the PTX the driver would JIT for an architecture not listed. It also prints per-kernel resource usage, which is how you learn a shipped binary's register and shared-memory footprint without having its source.

The common use is diagnosing a launch failure on new hardware: if the fatbinary carries SASS only for older architectures and no PTX, there is nothing for the driver to JIT and the kernel simply cannot run. It is the container-aware tool of the pair, while `nvdisasm` works on an extracted cubin and is the one that produces control-flow graphs and per-instruction analysis.

### cuobjdump --dump-sass
**Short:** Disassembles a CUDA binary to SASS so you can see the exact instructions and memory paths the compiler emitted.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, devtools/compiler-toolchain-and-codegen @3

### cuobjdump --resource-usage <binary>
**Short:** Inspects a compiled cubin/fatbin to report per-kernel register and shared-memory usage when you have no source.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, devtools/compiler-toolchain-and-codegen @2

### CuPy
**Short:** NumPy/SciPy-compatible GPU array library; every op dispatches CUDA kernels via cuBLAS/cuFFT/cuSPARSE or generated code.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2, runtime-systems/collections-and-algorithms @3, gpu/gpu-portability-and-precision @3

`import cupy as cp` gives you `cp.ndarray` with the NumPy surface, so much array code ports by changing the import — but the data now lives in device memory and every operation launches a CUDA kernel. Underneath are the NVIDIA libraries (cuBLAS for linear algebra, cuFFT, cuSPARSE, cuRAND, CUB for reductions) plus kernels CuPy generates and caches itself, and when the fused kernel you need does not exist, `ElementwiseKernel`, `ReductionKernel` and `RawKernel` let you write CUDA C++ inline and call it on the same arrays.

It also exposes the machinery that decides whether the GPU actually wins: memory pools, pinned-memory pools for faster host transfers, and streams so a copy can overlap compute. The common disappointment is a workload that is transfer-bound or works on small arrays, where PCIe traffic and per-kernel launch overhead dominate and NumPy on the CPU is simply faster.

### CuPy cupy.cuda.Device.attributes
**Short:** CuPy dict of device properties (SM count, shared memory, compute capability) queried from Python.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### CuPy nccl bindings
**Short:** Thin CuPy wrapper exposing raw NCCL collectives over NumPy-like GPU arrays.
**Kind:** tech
**Lang:** python
**Roles:** gpu/multi-gpu-and-collectives @1

`cupy.cuda.nccl` is a thin wrapper over the NCCL C library: build a communicator from a unique id broadcast to every rank, then call `allReduce`, `broadcast`, `reduceScatter` or `allGather` passing the raw device pointer from an array's `data.ptr`, an element count, a dtype code and a stream. There is no autograd, no process-group abstraction and no launcher, so rendezvous and rank numbering are yours to arrange.

Reach for it when a CuPy-based multi-GPU program needs real collectives and pulling in a training framework's distributed stack just for them would be absurd. If the workload is already PyTorch, use `torch.distributed`, which wraps the same library with process groups, timeouts and a launcher, and gets far more testing across topologies.

### CuPy RawKernel
**Short:** CuPy entry point for compiling and launching hand-written CUDA C++ source, including shared memory, from Python.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1

### cupy.cuda.Event
**Short:** CuPy's Python binding for CUDA events: stream synchronization and GPU-side timing, near 1:1 with the C++ runtime.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, observability/profiling-and-performance @2

### cupy.cuda.Stream
**Short:** CuPy's stream object, a near 1:1 Python mapping of the CUDA C++ runtime stream API.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1

### cuRAND
**Short:** NVIDIA random number generation library with host-side bulk generation and per-thread device generators.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1

It offers two very different interfaces. The host API fills a device array in bulk from one generator, choosing among XORWOW, MRG32k3a, MTGP32, Philox and Sobol quasi-random sequences, which suits a kernel that consumes a pre-generated array. The device API instead gives every thread its own state, initialized from a seed plus a per-thread sequence number and offset, and drawn from inside the kernel with calls such as `curand_uniform`.

The device path is the one that bites: state is large, so initializing it on every launch costs real time and keeping it live pressures registers and occupancy. Allocate the states once and reuse them across launches, and give threads distinct sequence numbers rather than distinct seeds, which is what keeps the substreams independent and the run reproducible.

### cuSPARSE
**Short:** NVIDIA's GPU sparse linear algebra library covering CSR, COO and block-sparse matrix operations.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1

It implements sparse linear algebra over the standard layouts, CSR, CSC, COO and blocked ELL, with the generic API expressing operations through descriptors: sparse matrix times dense vector, sparse times dense matrix, sparse-sparse products, format conversions and triangular solves. Many routines run in phases, a buffer-size query and an analysis pass whose result can be reused across every call sharing the same sparsity pattern.

The performance reality is that these kernels are bandwidth- and irregularity-bound: scattered access and load imbalance across rows mean a sparse multiply reaches a small fraction of what a dense GEMM does on the same card. Sparsity has to be very high before an unstructured sparse multiply beats a dense one on a GPU, which is why structured 2:4 sparsity through cuSPARSELt is the path that actually converts pruning into speed.

### cuSPARSELt
**Short:** NVIDIA library for structured-sparse (2:4) matrix multiply on tensor cores, turning pruned weights into real speedup.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, inference/quantization-and-compression @2

It targets exactly one pattern, 2:4 sparsity, meaning two zeros in every group of four contiguous weights, because that is what the sparse tensor cores on Ampere and later can consume. Pruned weights are compressed offline into a values array roughly half the original size plus a small metadata array of index bits, and the matmul reads that metadata to skip zero operands in hardware rather than in software, so the multiply itself runs faster instead of merely fitting in less memory.

The workflow is prune to the 2:4 pattern, usually with a fine-tuning pass to recover accuracy, compress once, then call the library at inference. Reach for it when weights are already in that pattern and the GEMM is the bottleneck. Arbitrary unstructured sparsity gets nothing from it, and the realized speedup lands well below the ideal because only the matmul portion of a layer changes.

### CUTE
**Short:** CUTLASS's C++ template layer of tensor and layout abstractions for building Hopper/Blackwell Tensor Core kernels.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2

CuTe's core abstraction is the layout, a nested pair of shapes and strides mapping a logical coordinate to an offset, with a tensor being a pointer plus a layout. Because layouts compose, divide and tile algebraically, partitioning a threadblock tile among warps, then among threads, then into the fragments a matrix instruction expects becomes layout arithmetic the compiler resolves at compile time rather than index expressions you derive by hand and get wrong.

It is the foundation CUTLASS 3.x was rebuilt on, and it is what makes recent hardware features expressible without hand-written address math: asynchronous bulk copies, warpgroup matrix instructions, and swizzled shared-memory layouts that avoid bank conflicts. Reach for it when writing a tensor-core kernel from scratch. The price is a steep learning curve, since the layout algebra must be understood before the code reads as anything but templates.

### CUTLASS
**Short:** NVIDIA's header-only C++ template library for building near-cuBLAS GEMM and convolution kernels with fused epilogues.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2, gpu/gpu-portability-and-precision @3

CUTLASS decomposes a GEMM into the hierarchy the hardware actually has — threadblock tile, warp tile, instruction tile — and exposes each level as a C++ template parameter, so you assemble a kernel by choosing tile shapes, data types, a swizzle and an epilogue instead of hand-writing the loads and the tensor-core instructions. It is headers compiled into your binary with no runtime library to link, and its layout algebra is what keeps shared-memory tiles correctly padded and double-buffered.

Reach for it when cuBLAS gives you the matmul but not the fusion you need: a custom epilogue, an unusual precision, an activation folded into the output stage. The price is long compile times and template error messages that take practice to read.

### DCGM
**Short:** NVIDIA Data Center GPU Manager: fleet GPU telemetry (utilization, memory, power, thermals, ECC).
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/metrics-and-monitoring @2

A host daemon collects GPU telemetry, and dcgm-exporter turns fields like `DCGM_FI_DEV_GPU_UTIL`, framebuffer used, power, temperature and ECC error counts into Prometheus metrics, usually as a Kubernetes DaemonSet. It also runs health checks and diagnostics, which is how you find a degrading card before a multi-hour training job lands on it.

Reach for it for fleet-level monitoring, capacity accounting and alerting across many GPUs. It will not tell you why one kernel is slow -- utilization can read high while the kernel is memory-bound -- so pair it with Nsight Compute or Nsight Systems for per-kernel work.

### deviceQuery
**Short:** CUDA sample binary that prints every cudaDeviceProp field, the fastest sanity check of a machine's GPU limits.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

It is one of the CUDA samples, a short program that walks every visible device and prints the whole `cudaDeviceProp` structure: name and compute capability, SM count, global memory, shared memory and registers per block and per SM, maximum threads per block, warp size, clock rates, L2 size, whether unified addressing and peer access are available, and the driver and runtime versions it was built against.

It answers the first question of any GPU triage, whether the runtime sees the hardware at all and whether it is the card you think it is, which makes it the standard smoke test inside a container or after a driver change. Modern toolkits no longer ship it as a binary, so you build it from the samples repository, or get the same facts from `nvidia-smi -q` and a few lines calling `cudaGetDeviceProperties` yourself.

### DPC++ compiler
**Short:** Intel's SYCL implementation and compiler (icpx/dpcpp) for single-source C++ that targets CPUs, Intel GPUs and beyond.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2, gpu/kernel-programming @2

`icpx -fsycl` compiles single-source C++ in which host code and kernels share a translation unit: the front end splits the device code out and compiles it either ahead of time for named targets or to SPIR-V for finalization by the driver, then embeds the result beside the host object. DPC++ is SYCL 2020 plus Intel extensions such as unified shared memory, subgroups and reductions, several of which were later folded into the standard itself.

Reach for it when the deployment is Intel CPUs, Arc or Data Center GPUs, or when one source genuinely must span vendors through the NVIDIA and AMD plugins. Against CUDA it trades a narrower ecosystem for portability, and the SPIR-V path means kernels are finalized at run time unless you compile ahead of time for the exact device, which is worth doing where startup latency matters.

### Flash Attention 2
**Short:** IO-aware fused attention kernel that cuts memory traffic and runs 2-4x faster than naive attention.
**Kind:** tech
**Lang:** python, cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2, inference/compiler-and-runtime-optimization @3

The insight is that standard attention is bound by memory traffic, not arithmetic: materializing the full sequence-by-sequence score matrix in high-bandwidth memory dominates the cost. The kernel instead walks the sequence in tiles that fit in on-chip SRAM and accumulates the softmax online, so the big intermediate matrix never exists. It is an exact algorithm, not an approximation, so results match standard attention up to floating-point reordering. The second version improves how work is partitioned across warps and cuts the non-matmul operations that the first version spent too much time on.

Reach for it whenever sequence length is long enough to matter, in training and in prefill. In practice you may already be using it without asking, because PyTorch's scaled dot product attention dispatches to a flash kernel when the dtype, head dimension and mask shape qualify; an unusual attention bias is exactly what makes it fall back to the slower path.

### Flash Attention 2/3
**Short:** Fused IO-aware attention CUDA kernels that avoid materializing the score matrix; effectively required for long context.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3, gpu/kernel-programming @3

FlashAttention computes exact attention without ever materializing the full sequence-by-sequence score matrix in HBM. It tiles the query, key and value blocks into on-chip SRAM and accumulates the softmax with a running maximum and denominator, so memory traffic falls from quadratic to linear in sequence length and the kernel stops being bandwidth-bound. The result is numerically the same attention, not an approximation, so there is no quality tradeoff to weigh.

Version 2 improves how the work is partitioned across warps and thread blocks so the GPU stays busy at long sequence lengths; version 3 targets Hopper specifically, overlapping asynchronous memory movement with computation and supporting lower-precision formats. It is the default attention path in modern training and serving stacks, and long context is impractical without it; support is per head dimension and dtype, so an unusual configuration may fall back.

### flash-attn-3
**Short:** FlashAttention-3: Hopper-optimized fused attention kernels with async pipelining and FP8, ~75% FLOP utilization.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, gpu/gpu-portability-and-precision @3

The Hopper rewrite exploits hardware the earlier kernels could not: the Tensor Memory Accelerator moves tiles asynchronously so loads overlap the matmul instead of stalling it, warp specialization splits producer and consumer roles across warpgroups, and the softmax of one tile is interleaved with the GEMM of the next so non-matmul work leaves the critical path. FP8 is supported with block-level scaling to keep the accuracy loss small.

It pays only on H100-class hardware, since on Ampere and earlier the instructions it is built around do not exist and version 2 remains the kernel. Coverage is also narrower than version 2 across head dimensions, masking modes and backward support, so a serving stack normally keeps both and dispatches per configuration rather than switching over wholesale.

### flash-linear-attention
**Short:** Triton fused-kernel library for linear-attention architectures such as GLA, RetNet, RWKV and Mamba-2.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3

Linear-attention and state-space architectures replace softmax attention with a recurrent state update, which is cheap in theory and terrible as naive PyTorch, because a sequential scan over thousands of timesteps launches thousands of tiny kernels. The library provides the chunked formulation in Triton instead: the sequence is split into chunks, the within-chunk contribution is computed as a matmul and the cross-chunk contribution through the recurrent state, so the GPU sees a few large tensor-core operations.

It covers GLA, RetNet, RWKV variants, Mamba-2 style updates and related designs, with fused forward and backward kernels and both recurrent and chunked modes so decoding and training each use the right one. Reach for it when training or serving one of these architectures; for standard softmax attention the flash-attention kernels are the equivalent layer and are far more widely tested.

### FlashAttention
**Short:** IO-aware fused attention kernels that tile QK^V in SRAM, avoiding the materialized N-by-N attention matrix.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @2, gpu/kernel-programming @3

The kernel fuses the whole attention block, the query-key product, the scaling, the mask, the softmax and the value multiply, into one pass that tiles the sequence and keeps intermediates in shared memory and registers. What makes it exact rather than approximate is online softmax: each tile updates a running row maximum and a running normalizer, and the already-accumulated output is rescaled whenever the maximum moves, so the result matches the unfused computation up to floating-point reassociation.

The backward pass makes the same bargain in the other direction, recomputing scores from saved statistics instead of storing the score matrix, trading arithmetic that is cheap here for bandwidth that is not. That is why it cuts activation memory during training as much as it improves speed, and why long-context training plans simply assume it is present rather than treating it as an optimization.

### FlashAttention and FlashInfer
**Short:** Fused IO-aware attention kernel libraries; FlashInfer additionally targets paged KV layouts used by serving engines.
**Kind:** tech
**Lang:** python, cpp
**Roles:** gpu/gpu-math-libraries @1, inference/inference-engine @2, gpu/kernel-programming @3

They solve the same problem at different points of the pipeline. The FlashAttention kernels assume contiguous query, key and value tensors, which fits training and prefill. FlashInfer targets decoding inside a server, where the KV cache is paged: one sequence's keys and values live in scattered fixed-size blocks, sequences in a batch have different lengths, and blocks are shared between requests with a common prefix. Its kernels take the block table as an input and gather as they go.

A serving engine usually carries both and dispatches per phase, prefill through one and decode through the other. FlashInfer additionally generates and caches kernels for a given page size, head configuration and mask type, so an unusual configuration compiles on first use rather than silently falling back to a slow generic path.

### Global Load/Store Efficiency
**Short:** Legacy CUDA profiler metric: requested bytes over transacted bytes, where 100% means perfectly coalesced access.
**Kind:** concept
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1

The metric is the ratio of bytes the threads requested to bytes the memory system actually moved. A warp issues one instruction across 32 lanes and the hardware coalesces those addresses into 32-byte sector transactions; when consecutive lanes touch consecutive addresses the sectors are fully used and the ratio approaches one, while a strided or scattered pattern pulls a whole sector for each useful element and the ratio collapses.

A low value is a data-layout problem, not a code-tuning problem: the fixes are converting an array of structures into a structure of arrays, transposing through shared memory, and padding rows so each warp's segment stays aligned. The name comes from the retired nvprof metric set; Nsight Compute reports the same idea as sectors per request and in its memory workload tables.

### GPUDirect RDMA
**Short:** NVIDIA capability letting a NIC DMA straight into GPU memory, skipping host bounce buffers on inter-node traffic.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, runtime-systems/io-networking-and-syscalls @3

The GPU exposes a window of its memory through a PCIe base-address register, and a kernel module, either `nvidia-peermem` or the newer dma-buf path, hands those physical addresses to the NIC driver so the adapter's DMA engine reads and writes device memory directly. An arriving message therefore lands in GPU memory without a staging copy into host RAM and a second copy across PCIe, removing both the latency of the bounce and the CPU work of driving it.

It is what lets multi-node NCCL and MPI collectives reach line rate, and topology decides whether you get it: the GPU and the NIC should sit under the same PCIe switch or root complex, which is why dense nodes are built with a NIC per GPU pair. Check the paths with `nvidia-smi topo -m`, because when a transfer has to cross sockets it silently falls back to the host path and only the throughput tells you.

### hipify-clang
**Short:** AMD tool that translates CUDA source to HIP by parsing the real AST, more accurate than the text-pattern hipify-perl.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2

It is a Clang-based tool, so it parses the translation unit into a real syntax tree using your include paths and macro definitions, then rewrites CUDA constructs from that: allocation and copy calls become their HIP equivalents, kernel launches are rewritten, and CUDA library calls map onto the ROCm counterparts. Because it type-checks first it handles macros, templates and conditional compilation correctly, and it reports what it could not translate instead of leaving a plausible-looking wrong token behind.

Use it on real projects, where a pattern matcher would mangle anything hidden behind a macro. The price is that the source must actually compile, so the CUDA headers and the right flags have to be present, which is more setup than the alternative. Either way the output is a starting point: tuning for a different memory hierarchy and a 64-lane wavefront is not something a translator can do for you.

### hipify-perl
**Short:** Text-pattern translator rewriting CUDA source into HIP for AMD GPUs; the lighter sibling of hipify-clang.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2

It is a text substitution script, a large table of CUDA identifiers mapped to HIP names applied with regular expressions and no understanding of the code. Because it needs no compiler, no headers and no build configuration, it runs over a directory of sources in seconds and can convert files that would not even compile on the machine running it, which is exactly why it is the tool people try first.

That is also its limit. Anything hidden behind a macro, a template parameter or a conditional include is invisible to it, and a wrongly substituted token looks correct until the build fails or, worse, succeeds and behaves differently. Reach for it to survey how much of a codebase is mechanically portable; for the migration you intend to keep, hipify-clang's syntax-tree translation is the one to trust.

### In-kernel assert
**Short:** assert() inside a CUDA kernel: aborts and prints file, line and thread on failure; compiled out under NDEBUG.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### In-kernel printf
**Short:** Device-side printf writing into a fixed circular FIFO; unordered across threads and silently overwritten on overflow.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### Intel oneAPI
**Short:** Intel's SYCL toolchain and library stack: icpx/dpcpp compilers plus oneMKL and oneDNN math and DNN libraries.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, gpu/gpu-math-libraries @2, devtools/compiler-toolchain-and-codegen @3

The toolkit is the `icx` and `icpx` compilers plus a set of libraries with one interface across devices: oneMKL for BLAS, FFT and solvers, oneDNN for deep-learning primitives, oneTBB for host threading, oneCCL for collectives and oneDAL for classical analytics. VTune and Advisor come with it for hotspot, roofline and offload analysis, and the same library call dispatches to the CPU's AVX-512 and AMX paths or to an Intel GPU depending on the queue it is submitted to.

Reach for it when the hardware is Intel and one source should span CPU, integrated GPU and discrete GPU. Outside that case the libraries are the more common entry point than the compiler, since PyTorch and OpenVINO call oneDNN and oneMKL underneath whether or not you ever write a line of SYCL yourself.

### kernel<<<grid, block, shmem, stream>>>()
**Short:** CUDA triple-chevron launch syntax choosing grid, block, dynamic shared memory and stream; also launches child kernels.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1

### Khronos OpenCL SDK
**Short:** Vendor-neutral OpenCL headers and loader that enumerate installable client drivers for cross-vendor GPU compute.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, gpu/kernel-programming @2

The SDK is not a driver. It packages the official headers, the C++ bindings and the ICD loader, the library your program links against, which at run time reads the vendor entries installed on the machine and forwards every call into whichever installable client drivers are present. That indirection is what lets a single binary enumerate an NVIDIA GPU, an AMD GPU, an Intel integrated GPU and a CPU runtime as platforms and choose among them.

Reach for it when cross-vendor reach matters more than peak performance, particularly on hardware CUDA does not cover such as FPGAs, embedded GPUs and older silicon. The realities to plan for are that kernels are strings compiled at run time, that vendor support for newer OpenCL versions is uneven, and that vendor-specific tuning still decides speed even though the same source compiles everywhere.

### launch_bounds
**Short:** CUDA per-kernel compiler hint capping registers for a target block size; preferred over a file-wide maxrregcount.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @3

### libcu++
**Short:** CUDA's C++ standard library for device code: C++20-style atomics with explicit memory order and thread scope.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

It is the CUDA C++ standard library: heterogeneous implementations of standard components usable in both host and device code under `cuda::std::`, plus CUDA-specific extensions under `cuda::`. The concurrency layer is the important part, because `cuda::atomic` and `cuda::atomic_ref` carry an explicit thread scope alongside the memory order, so you state whether a synchronization must be visible within a block, across the device or across the system, and `cuda::barrier` and `cuda::pipeline` express asynchronous shared-memory staging.

Reach for it instead of raw `atomicAdd` and `__threadfence` whenever a kernel does real inter-thread synchronization: a block-scoped atomic compiles to weaker and faster instructions than a system-scoped one, and the memory model is specified rather than folklore. It ships with the toolkit inside CCCL alongside Thrust and CUB, and the subset of the standard library it covers grows with each release.

### libcudadevrt
**Short:** CUDA device runtime library that must be linked for any translation unit launching kernels from the device.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @2

Dynamic parallelism, a kernel launching another kernel, is not free-standing code. The device-side launch compiles to a call into this static library, which holds the runtime that queues the child grid, tracks the parent-child dependency and implements the device-side portion of the runtime API. Because it is a separate library linked into the device image, the translation unit must be compiled as relocatable device code with `-rdc=true` and then linked with `-lcudadevrt`.

Getting it wrong shows up as a link error naming a device launch symbol, which is why the two flags are always mentioned together. It only matters if you actually issue device-side launches. Note that relocatable device code also disables some whole-program device optimizations, so enable it for the translation units that need it rather than across the entire build.

### Metal
**Short:** Apple's GPU compute and graphics API with MSL kernels, plus MPS and MPSGraph tuned primitive libraries.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, gpu/kernel-programming @2, gpu/gpu-math-libraries @3

Kernels are written in the Metal Shading Language, a C++-based dialect, and compiled either ahead of time into a library or from source at run time. The host API is explicit and object-based: a device, a command queue, command buffers holding encoders, and pipeline state objects built once from a compiled function. Apple silicon has unified memory, so a buffer is visible to CPU and GPU with no copy, and the cost model is cache coherence and storage mode rather than PCIe transfers.

It is the only first-class compute path on Apple hardware, and it is what PyTorch's MPS backend, llama.cpp's Metal backend and Core ML's GPU path sit on. Reach for it for on-device inference and Mac-native tooling. There is no portability story at all, so cross-platform code targets it indirectly through an abstraction layer or through one of those higher-level runtimes.

### Metal Performance Shaders
**Short:** Apple's tuned GPU kernel library plus the MPSGraph API, the compute stack for Apple silicon.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-portability-and-precision @2

MPS is the tuned kernel library, covering matrix multiply, convolution, pooling, normalization, reductions and image operations, hand-written per GPU family so you are not authoring shading-language kernels for standard work. MPSGraph sits above it as a deferred graph API: you build the computation symbolically and it fuses and schedules the operations and places them on the GPU or the Neural Engine, so a whole model runs from one compile rather than one encoder per layer.

This is the layer PyTorch's `mps` device and Core ML's GPU path call into, so most people use it without writing against it directly. Reach for it when building a Mac or iOS application around a model and you want the tuned primitives without a framework. Expect gaps, since coverage of unusual operations lags CUDA's and the fallback is a hand-written Metal kernel.

### MIG
**Short:** NVIDIA Multi-Instance GPU: hardware partitioning of an A100/H100 into isolated slices shared by several workloads.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, platform-delivery/kubernetes-and-orchestration @3, inference/model-server @3

The partition is in hardware rather than in the scheduler: a GPU instance receives its own slice of SMs, its own memory-controller channels and L2 slices, and its own framebuffer, so one tenant's memory traffic cannot steal bandwidth from another and a fault in one instance does not take the others down. Each instance appears as a separate device with its own identifier, and containers are assigned one through the device plugin exactly as they would be assigned a whole GPU.

Reach for it when several small models or several users must share a card with hard isolation and predictable latency, which is the case MPS handles badly because it shares SMs. The constraints are real: profiles are fixed sizes rather than arbitrary fractions, reconfiguration requires draining the GPU, a job cannot span instances, and no workload can use more than its partition even when the rest of the card sits idle.

### MIG partitioning per replica
**Short:** Splitting an A100/H100 into MIG slices and pinning one server replica per slice for hard memory and SM isolation.
**Kind:** concept
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, inference/model-server @2, platform-delivery/cloud-platform-and-cost @3

The pattern is to configure a card into fixed instances, expose them through the Kubernetes device plugin, and schedule one model-server replica per instance so a replica's request for a GPU resolves to a slice rather than the whole board. Each replica then owns a private framebuffer and a private share of SMs and memory bandwidth, so a traffic spike on one model cannot evict another's weights or starve its decode loop.

It suits fleets of small and medium models that individually cannot fill a card and must not interfere with each other. The tradeoffs are that a replica stays capped at its slice even when neighbours are idle, that the coarse profile sizes strand some capacity, and that changing the layout means draining the node. Where isolation is not actually required, MPS or plain multi-instance serving packs the same GPU more tightly.

### mma.sync
**Short:** The PTX warp-level matrix-multiply-accumulate instruction that Tensor Cores actually execute.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-portability-and-precision @2

### MPI
**Short:** The HPC message-passing standard for multi-process, multi-node jobs; commonly bootstraps NCCL rendezvous across nodes.
**Kind:** spec
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, model-training/distributed-training @2, data-movement/batch-and-distributed-compute @3

The model is fixed-size process groups, ranks within a communicator, and explicit messages: point-to-point sends and receives in blocking and non-blocking forms, and collectives including barrier, broadcast, reduce, allreduce, allgather and alltoall, over derived datatypes that describe non-contiguous buffers. It is a specification with several implementations, notably Open MPI and MPICH with their vendor derivatives, and CUDA-aware builds accept device pointers so a transfer can use GPUDirect instead of staging through the host.

In GPU work its usual job is the bootstrap rather than the data path: a launcher starts one rank per GPU and supplies the rank, world size and rendezvous information that NCCL or `torch.distributed` then use for the collectives carrying the tensors. Reach for MPI itself in HPC codes and for host-side coordination; for gradient reduction between GPUs, NCCL is the faster and better-tuned path.

### mpirun
**Short:** MPI process launcher that starts one rank per GPU and sets the rendezvous environment NCCL and torch.distributed need.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, model-training/distributed-training @2

It is the launcher: given a host list or a scheduler allocation it starts the requested number of processes across nodes, wires up their standard streams, and injects the environment each rank needs, namely its rank, the world size and the addresses used to find the others. Binding options control which cores and NUMA node a rank lands on, which matters because a rank pinned to the wrong socket reaches both its GPU and its NIC the long way around.

In GPU training it is often used purely as a bootstrap for a stack that does its own communication, one rank per GPU with NCCL carrying the tensors. Under Slurm, `srun` usually replaces it and takes the allocation directly, and `torchrun` is the PyTorch-native equivalent that is simpler when the job is a single PyTorch script rather than a mixed MPI application.

### MPS
**Short:** NVIDIA Multi-Process Service letting several processes share one GPU's SMs, packing inference fleets tighter.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, inference/model-server @2, runtime-systems/memory-processes-and-os @3

Without it, kernels from different processes time-slice the GPU: only one context is active at a time, and the switching wastes the SMs that a small kernel could never fill on its own. MPS inserts a server process that funnels clients' work into a single context so their kernels are genuinely resident together. On Volta and later each client keeps its own address space, and an active-thread-percentage setting caps how much of the SM pool one client may occupy.

Reach for it when several processes each run kernels too small to fill the GPU, such as an inference fleet of small models or MPI ranks sharing a card. The isolation is soft: clients share memory capacity with no hard quota, and a fatal fault in one can take down the server and its peers. Where the blast radius must be bounded, MIG's hardware partitioning is the correct answer instead.

### NCCL
**Short:** NVIDIA's topology-aware GPU collective library (AllReduce, AllGather) under most multi-GPU training stacks.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/multi-gpu-and-collectives @1, model-training/distributed-training @2

NCCL implements the collective operations distributed training is built from — AllReduce, AllGather, ReduceScatter, Broadcast — and picks a ring or tree algorithm matched to the machine's actual topology, so a reduction crosses NVLink where it can and PCIe or the network only where it must. PyTorch DDP, FSDP, DeepSpeed and tensor-parallel inference all bottom out here.

You rarely call it directly; you notice it when scaling stalls, and the first move is to measure achieved bus bandwidth with `nccl-tests` before blaming the model. Topology also dictates architecture: an interconnect that is fast inside a node and slow between nodes is why tensor parallelism stays within a node while data or pipeline parallelism spans them.

### nccl-tests
**Short:** NVIDIA's benchmark suite that measures achieved collective bandwidth (all-reduce busbw) against a topology's ceiling.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/multi-gpu-and-collectives @1, gpu/gpu-profiling-and-debugging @2

These are MPI-launched benchmark binaries — `all_reduce_perf`, `all_gather_perf`, `broadcast_perf` and friends — that sweep a range of message sizes and report, for each, the achieved algorithm bandwidth and bus bandwidth. Bus bandwidth is the number you compare against hardware: NVLink or PCIe between GPUs inside a node, and the NIC line rate across nodes.

Run it before you profile a slow distributed training job. In a few minutes it separates "the interconnect, topology or NCCL environment is misconfigured" from "the model code is the problem", which is otherwise an expensive thing to work out from training throughput alone. It is also the standard smoke test after any driver, firmware, topology or container-image change on a GPU cluster, precisely because a silent fallback to a slower transport looks exactly like a normal run.

### ncu-ui
**Short:** Nsight Compute's GUI for exploring .ncu-rep kernel profiles; the same data the CLI collects, browsable per kernel.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1

It opens the report files the command-line kernel profiler writes, which is the normal workflow on a cluster: collect headless on the GPU node, copy the report to a laptop, explore it there. The interface groups a kernel's counters into sections, covering speed of light against the roofline, memory workload with its transaction and sector breakdown, scheduler and warp-stall statistics, and occupancy with its limiting factor, each metric carrying a description and a rule-based hint.

Two things are hard to get from the command line. Source view interleaves CUDA, PTX and SASS with per-line stall attribution when the binary was built with `-lineinfo`, and baseline comparison diffs two reports so an optimization's effect on every counter is visible at once. Reach for it after the CLI has told you which kernel is worth studying, not as the way to find that kernel.

### NPP
**Short:** NVIDIA Performance Primitives: GPU image and signal routines for resize, colour conversion and JPEG decode.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, applied-ml/vision-speech-and-multimodal @2

The library is a large set of primitives for images and signals, grouped by data type and channel count, with the names encoding both so that a call site states the pixel format and the region-of-interest convention it is using. Coverage runs to resizing and warping, colour-space conversion, filtering and morphology, arithmetic and logical operations, statistics and histograms, plus one-dimensional signal routines, and the calls take stream contexts so preprocessing composes into the same stream as the model.

Reach for it to keep a vision pipeline's preprocessing on the GPU, so decoded frames never make a round trip to the CPU for a resize and a normalize before inference. Two cautions: the functions are low level and easy to misuse on channel order and ROI arithmetic, and for a full decode-and-transform pipeline the higher-level DALI is usually far less code for the same result.

### Nsight Compute
**Short:** NVIDIA per-kernel CUDA profiler: occupancy, memory throughput, bank conflicts, warp-stall and Tensor-Core metrics.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

It works by replaying each kernel launch several times, collecting a different set of hardware counters on each pass and combining them into one report. That is why the kernel must be deterministic and free of cross-launch side effects, and why the wall time it reports is not the wall time your application sees. Scoping matters: without a kernel-name filter or a launch count, a run tries to profile every launch in the program.

The report answers one question well, which is what limits this kernel. Speed of light gives compute and memory utilization against the hardware roofline, the memory section shows requested versus transferred sectors, the scheduler section attributes cycles to specific stall reasons, and the occupancy section names its own limiter. It says nothing about host code, multi-GPU behaviour or which kernel matters, so start with a timeline profiler.

### Nsight Compute Roofline chart
**Short:** Nsight Compute view plotting a kernel against the memory and compute roofs, showing which limit it is hitting.
**Kind:** api
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @3

### Nsight Compute — "Occupancy" section
**Short:** Profiler section giving theoretical vs achieved occupancy, the binding limiter and warp-stall reasons.
**Kind:** api
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### Nsight Compute — Memory Workload Analysis
**Short:** Nsight Compute report section: load/store transactions, sectors requested vs needed, DRAM/L2 throughput vs peak.
**Kind:** api
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1

### Nsight Systems
**Short:** NVIDIA whole-application timeline profiler showing CPU/GPU overlap, kernel launches, copies and stream concurrency.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @2

It samples the whole process (CUDA API calls, kernel executions, memory copies, NVTX ranges, CPU threads and OS scheduling) onto one correlated timeline, so what you actually see is where the gaps are: a GPU idle while the CPU prepares the next launch, copies that never overlap compute, a wall of tiny kernels produced by a Python loop. Overhead is low enough, a few percent, to profile a realistic run instead of a microbenchmark. Start every investigation here to find which phase is slow, then move to Nsight Compute for the per-kernel counters that explain why that one kernel is slow. Reaching for Compute first is the common mistake, since it will happily help you perfect a kernel the timeline shows contributes almost nothing.

### nsys-ui
**Short:** GUI viewer for Nsight Systems and Nsight Compute reports, for interactive exploration of timelines and kernel counters.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @2

It is the desktop front end for the Nsight family's report files. It reads timeline reports with rows for CPU threads, CUDA API calls, kernels, memory copies, NVTX ranges and NCCL activity, and it can also open kernel-profiler reports. The timeline is where the analysis happens: zoom into one training step, correlate a launch on the API row with its execution on the GPU row, and read the gaps between them rather than the bars themselves.

The normal workflow is headless collection on the machine with the GPU and interactive analysis elsewhere, which matters because a trace of a realistic step is large; remote profiling over SSH is also supported. What it cannot tell you is why one kernel is slow, because that needs the replayed hardware counters the kernel profiler collects.

### Numba
**Short:** JIT compiler that turns decorated Python functions into machine code or CUDA PTX via LLVM/NVVM.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2

Decorate a function with `@njit` and Numba compiles it through LLVM to machine code on its first call, specializing on the argument types it sees; loops over NumPy arrays and scalar math then run at roughly C speed with the GIL optionally released. It understands a subset of Python and NumPy — dictionaries of mixed types, arbitrary objects, and most library calls are outside it, and nopython mode makes that a hard error rather than a silent slow path.

`@cuda.jit` compiles a function to PTX through NVVM, and there you write real CUDA: thread and block indices, shared memory, explicit device transfers. Reach for it for tight numeric loops that vectorization cannot express, or to prototype a kernel in Python before writing C++; remember that the first call pays compilation, so measure the second.

### Numba CUDA
**Short:** Numba's CUDA backend: write GPU kernels in Python with JIT compilation, shared memory and warp intrinsics.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @3

You write the kernel as a Python function under `@cuda.jit`, index it with `cuda.grid(1)` or the raw block and thread attributes, and launch it with the `kernel[blocks, threads]` subscript syntax; Numba compiles to PTX on first call, so you get the same thread-level model as CUDA C++ without leaving Python or invoking nvcc. It exposes the pieces that make a kernel fast rather than merely correct -- `cuda.shared.array` for static shared memory (a zero-shaped array plus a launch-time byte count for dynamic), `cuda.syncthreads()`, atomics, and the warp intrinsics such as `shfl_sync` and ballot. What it does not give you is the Cooperative Groups grid-wide API, and the Python subset allowed inside a kernel is narrow: typed arrays and scalars, no objects, no allocation. Reach for it to prototype or ship a custom kernel inside a Python stack; CuPy is the better answer when a library routine already exists, and Triton is usually less work for block-level tiled kernels.

### Numba cuda.atomic
**Short:** Numba's device-side atomic operations for CUDA kernels written in Python; no grid-wide cooperative sync binding.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1

### Numba cuda.jit
**Short:** Numba decorator that compiles a Python function into a CUDA kernel, exposing shared and local memory directly.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2

### numba.cuda device attributes
**Short:** Numba CUDA device query exposing SM count, register file and shared-memory limits for launch heuristics in Python.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### NUMBA_CUDA_DEBUGINFO
**Short:** Numba env flag emitting device debug info so register and local-memory placement is visible for Python kernels.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2

### nvcc
**Short:** The CUDA compiler driver: splits host/device code and compiles .cu sources to PTX and SASS.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @1

nvcc is a driver rather than a compiler: it splits a `.cu` file into host and device code, hands the host part to the system compiler (gcc, clang or MSVC), compiles the device part through NVVM to PTX and then `ptxas` to SASS, and links everything into one binary. `-arch` and `-gencode` decide what goes into that binary — SASS for the architectures you target, plus PTX as a forward-compatible fallback the driver JITs on newer GPUs — which is why a kernel silently fails to launch on hardware nobody compiled for.

Three flags earn their keep: `-lineinfo` so profilers can attribute samples to source lines, `--ptxas-options=-v` to print per-kernel register and shared-memory usage (the numbers that decide occupancy), and `-G` for device-side debugging, which disables optimization and must never be used for measurements.

### nvcc --ptxas-options=-v
**Short:** nvcc flag printing per-kernel register, shared-memory and local-memory spill usage at compile time, before any launch.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @2, devtools/compiler-toolchain-and-codegen @3

### nvcc -rdc=true
**Short:** The relocatable-device-code compile/link flag required for any translation unit that issues device-side kernel launches.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @2

### nvcuda::wmma
**Short:** CUDA C++ warp-level matrix-multiply-accumulate API exposing tensor-core fragments for hand-written fused kernels.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, gpu/kernel-programming @2

### nvdisasm
**Short:** CUDA toolkit disassembler that prints SASS from a .cubin, independent of cuobjdump.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, devtools/compiler-toolchain-and-codegen @2

It disassembles a cubin, an ELF holding device code for one architecture, into SASS, the instruction set the SM actually executes. Beyond a linear listing it emits control-flow and call graphs in DOT form for rendering, prints the per-instruction control information the scheduler encodes, and, given a `-lineinfo` build, attributes each instruction to the source line it came from.

Reach for it when the question is what the compiler really emitted: whether a loop was unrolled, whether a local array spilled into local memory, whether the tensor-core instruction you expected is present at all. Note the division of labour with `cuobjdump`, which understands fatbinary containers inside executables and delegates the disassembly, while this tool works on an extracted cubin and owns the graph and analysis output.

### NVIDIA Nsight
**Short:** NVIDIA's GPU profiling and debugging tool family (Systems, Compute, Graphics) for kernel and timeline analysis.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @3

Two of the tools are the ones you actually reach for. Nsight Systems is the timeline profiler: it shows CPU threads, CUDA API calls, kernel executions, memory copies and your own annotated ranges on one axis, which is how you discover that the GPU is idle waiting on the data loader rather than slow. Nsight Compute is the per-kernel profiler, reporting occupancy, achieved memory and compute throughput, roofline position and the stall reasons behind them.

Use them in that order: the timeline tells you which kernel matters, the kernel profiler tells you why it is slow. They replace nvprof on current GPU architectures. One thing to keep in mind is that the kernel profiler replays each kernel several times to collect its counters, so its reported wall clock is not your application's wall clock.

### nvidia-smi
**Short:** NVIDIA's CLI for GPU inventory and telemetry: driver version, utilization, memory, power and thermals.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/metrics-and-monitoring @2

It is the command-line face of NVML and reports what GPUs are present and what they are doing: model and compute capability, driver and CUDA versions, memory used against total, SM utilization, power draw, temperature and clock throttling reasons, ECC errors, and which processes hold memory on which device. It also configures: persistence mode, MIG partitioning, ECC on or off, and power and clock limits. `nvidia-smi --query-gpu=... --format=csv -l 1` is the scriptable form that feeds a monitoring pipeline.

Reach for it for fleet-level questions — is this GPU busy, is its memory full, whose process is that, is it thermally throttling. Read the utilization number carefully: it is the fraction of time at least one kernel was resident, not how well that kernel uses the SMs, so a 100% figure is fully compatible with a badly under-occupied GPU. Real efficiency work needs Nsight Systems or Nsight Compute.

### nvidia-smi dmon
**Short:** nvidia-smi's device monitor mode, streaming per-GPU utilization, memory, power and clocks over time.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/metrics-and-monitoring @2

It prints one line per device per interval, by default every second, with SM and memory-controller utilization, encoder and decoder activity, framebuffer use, power draw, temperature, SM and memory clocks and PCIe throughput, with flags to choose the metric groups and restrict the device list. Unlike the default table it is a stream rather than a snapshot, so it pipes straight to a file and lines up against a job's own timestamps.

Reach for it as the cheap first look at a long run: whether the GPU is busy at all, whether power or thermal limits are capping the clocks, whether memory is creeping up over hours. Read the utilization column as carefully here as anywhere else, since it counts intervals in which a kernel was resident rather than how well the SMs were used, so a flat line at 100 is a starting point rather than a verdict.

### nvJPEG
**Short:** NVIDIA GPU library for JPEG decode and image preprocessing, keeping the pipeline off the CPU.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, applied-ml/vision-speech-and-multimodal @2

Decoding runs on the GPU, and on datacentre parts there is a dedicated hardware JPEG engine that performs the entropy decode and inverse transform outside the SMs, leaving them free for the model. The library is batch-oriented, so hundreds of images are decoded in one call with the Huffman phase and the pixel phase pipelined, and the output is a device tensor in the colour format you asked for, which means decode, resize and normalize can all stay on the device.

Reach for it when input decoding is the bottleneck of a vision pipeline, which happens sooner than people expect once the model itself is fast. It is the decoder underneath DALI, and DALI is usually the easier entry point because it wraps decode, augmentation and batching into one pipeline. For a handful of images per second, CPU decoding is entirely adequate.

### NVLink
**Short:** NVIDIA's high-bandwidth GPU-to-GPU interconnect (~900 GB/s per H100) and the NVSwitch crossbar fabric.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1

It is a point-to-point serial link and a GPU carries many of them, aggregated so that one peer connection uses several links at once. Against PCIe it is not only wider but coherent enough for direct peer loads and stores, so one GPU addresses another's memory without a staged copy and a collective library can reduce across it at close to the raw rate. Grace Hopper parts extend the same fabric to the CPU rather than stopping at the GPUs.

The consequence for system design is a bandwidth cliff at the node boundary: inside the node the links are fast, between nodes you have the network. That is why tensor parallelism, which exchanges activations at every layer, is kept within an NVLink domain while pipeline and data parallelism, which communicate far less often, are allowed to span nodes. Check what a machine actually has with `nvidia-smi topo -m`, since some SKUs ship fewer links or none.

### nvrtc
**Short:** NVIDIA's runtime CUDA C++ to PTX compiler library; backs JIT kernel paths in CuPy, Numba, PyCUDA and PyTorch.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @2, inference/compiler-and-runtime-optimization @3

NVRTC compiles a CUDA C++ source string to PTX inside your own process -- create a program, compile it, read back the PTX, then load and launch it through the driver API -- so kernels can be generated and specialised at run time with constants, types and loop bounds baked in, without shipping nvcc or writing temporary files. This is the machinery behind the JIT paths in the Python GPU stack: CuPy's `RawKernel` and `ElementwiseKernel`, PyCUDA's `SourceModule`, and the runtime-generated kernels in Numba and PyTorch all route through it. It is a library rather than a CLI, it does not handle the host-side compilation nvcc performs, and headers must be supplied as strings through its include mechanism rather than found on disk. Reach for it when kernel source is only known at run time; if the shapes are known at build time, offline nvcc compilation is simpler and costs nothing at startup.

### NVSwitch
**Short:** NVLink crossbar switch giving all-to-all GPU bandwidth (~900 GB/s per H100) inside a node.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1

With direct links alone an eight-GPU node is a partial mesh: some pairs are adjacent and others are not, so a collective's achieved bandwidth depends on which GPUs happen to be talking. NVSwitch is a crossbar that terminates every GPU's links, so any pair communicates at the full per-GPU rate simultaneously and topology stops being a factor in the algorithm the collective library picks. Rack-scale systems extend this with switch trays placing many GPUs in one domain.

This is what makes all-to-all patterns practical inside a node rather than throttled by the worst pair, which matters for mixture-of-experts routing and large tensor-parallel groups. It is a property of the machine rather than something you configure, and the practical implication is that a switched node and an unswitched one with the same GPUs are different machines for collective-heavy work; measure with `nccl-tests` instead of assuming.

### nvtx
**Short:** NVIDIA annotation library that names ranges and markers so profiler timelines show application phases.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

You push and pop named ranges around regions of your own code, and the profiler draws them as labelled bands above the kernel timeline, so a trace reads as data loading, forward, backward and optimizer step instead of thousands of anonymous kernel launches. PyTorch and other frameworks expose thin wrappers over the same calls, and the overhead is negligible when no profiler is attached, so the annotations can stay in the code.

Add the ranges before you profile rather than after. Without them the hardest part of reading a large GPU trace is working out which phase of your program a given burst of kernels belongs to, and that is exactly the question the timeline cannot answer on its own.

### OpenAI Triton
**Short:** Python-embedded DSL for writing block-level GPU kernels without CUDA C++; also the torch.compile codegen backend.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, gpu/gpu-math-libraries @3, gpu/gpu-portability-and-precision @3

You write a kernel as a Python function decorated `@triton.jit` that operates on blocks of elements — pointer arithmetic, `tl.load`/`tl.store` with masks, `tl.dot` for the matmul — and the compiler handles what CUDA C++ makes you do by hand: mapping work onto threads within a block, staging data through shared memory, and selecting the tensor-core instruction. You still choose block sizes and tiling, which is where the performance lives, and `triton.autotune` sweeps those configurations.

Its most common role is not hand-written at all: `torch.compile`'s Inductor backend generates Triton for fused elementwise and reduction kernels. Write it yourself when a fusion the compiler will not find is the bottleneck — a custom attention variant, a fused normalization — and stay with cuBLAS or cuDNN for the standard shapes they already tune better than you will. Note the name collision with NVIDIA Triton, an unrelated model-serving product.

### Pallas
**Short:** JAX's block-level Python kernel DSL for writing fused custom kernels targeting both GPU and TPU.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-portability-and-precision @2

Pallas is a JAX extension for writing kernels at block level: you declare a grid and, through `BlockSpec`s, how each program instance's slice of the inputs and outputs is carved out, then write the body against refs with explicit loads and stores. That hands you control over tiling and on-chip memory -- the decisions an XLA fusion makes for you -- while staying inside JAX, so a `pallas_call` composes with `jit`, `vmap` and autodiff like any other primitive. The same source targets both backends: on GPU it lowers through Triton, on TPU it compiles to Mosaic, which is why it is the natural home for a fused attention or normalisation kernel in a JAX codebase. Reach for it when XLA will not fuse something the way you need and the arithmetic intensity justifies a hand-written kernel; it is a lower-level, fast-moving API, and most JAX code should never need it.

### PTX ISA reference
**Short:** NVIDIA's PTX virtual instruction-set spec, read when checking what nvcc --ptx emitted for a kernel.
**Kind:** spec
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @2, gpu/gpu-profiling-and-debugging @3

PTX is a virtual instruction set rather than machine code: the compiler emits it, and either `ptxas` compiles it ahead of time to SASS for a named architecture or the driver compiles it at load time for a newer one. The specification defines the instructions, the state spaces for global, shared, local, parameter and constant memory, the type system, the memory consistency model, and the asynchronous copy, barrier and matrix-multiply instructions that have no C++ surface at all.

You read it for two reasons: to check what the compiler emitted from a `--ptx` build, and to write inline PTX for something the intrinsics do not expose, such as a particular matrix-multiply shape, a cache hint on a load, or an asynchronous copy. Do not mistake it for the real instruction set. SASS is what executes, `ptxas` reorders and allocates registers freely, and a PTX instruction count says very little about the final schedule.

### py3nvml
**Short:** Python bindings over NVML exposing the same GPU utilization, memory and power queries as nvidia-smi.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/metrics-and-monitoring @3

It wraps NVML, the same C library `nvidia-smi` is a front end for, so a Python process queries the driver directly instead of shelling out and parsing text: device count and handles, utilization rates, memory used and total, power draw and limits, temperature, clocks and throttle reasons, ECC counters, and the list of compute processes with their per-process memory. Everything is read from the driver, so it works whether or not the calling process holds a CUDA context.

Reach for it to build a metrics exporter, to log GPU state alongside training steps, or to pick a free device at startup. Two cautions: it reports device-wide state, so per-process attribution is limited to memory, and this package is a third-party fork rather than the official binding, with `pynvml` and the newer `nvidia-ml-py` being what most projects now depend on.

### PyCUDA
**Short:** Thin Python bindings over the CUDA driver API with explicit contexts, memory and inline kernel compilation.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-math-libraries @3

It exposes the driver API rather than the runtime: contexts are pushed and popped explicitly, device memory is allocated and copied through direct calls, and a source module compiles a CUDA C++ string at run time and hands back a callable kernel you launch with explicit block and grid tuples. A thin array type adds elementwise operations on top, and Python's garbage collector is wired into device deallocation so buffers free when they go out of scope.

Reach for it when you want the CUDA model itself from Python with nothing in between, for teaching, an experiment or a one-off kernel in a script. For array-oriented work CuPy is the more complete library and interoperates with the rest of the ecosystem, and for a kernel inside a training stack, `torch.utils.cpp_extension` or Triton fits better than managing your own contexts.

### pycuda.tools.OccupancyRecord
**Short:** PyCUDA occupancy calculator from a kernel's register and shared-memory use; its DeviceData limits are hardcoded.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @2

### PyTorch AMP
**Short:** torch.amp autocast: runs each op in fp16/bf16 or fp32 by allowlist so Tensor Cores stay busy without hand-casting.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, model-training/deep-learning-framework @2, inference/quantization-and-compression @3

### PyTorch torch.channels_last
**Short:** PyTorch NHWC memory format that, with the right dtype and alignment, unlocks cuDNN's fastest Tensor Core convs.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3

### ROCm
**Short:** AMD's GPU compute stack: HIP runtime plus rocBLAS/MIOpen/rocProf, mirroring the CUDA library and tooling roles.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, gpu/gpu-math-libraries @2, gpu/kernel-programming @2, gpu/gpu-profiling-and-debugging @3

HIP is the portability layer: an API deliberately shaped like CUDA's, so the allocation, copy and launch calls map one to one and the same source compiles for AMD through `hipcc` or for NVIDIA through nvcc. Underneath sit the counterparts to the CUDA libraries, with rocBLAS and hipBLASLt for dense linear algebra, MIOpen for deep-learning primitives, RCCL for collectives, rocFFT and rocSPARSE, plus `rocprof` and `rocgdb` for profiling and debugging.

PyTorch and JAX have ROCm builds and vLLM runs on it, so mainstream training and inference work without source changes. What still bites is coverage at the edges: a hand-written CUDA kernel, a dependency pinned to a CUDA-only library, or a very new model implementation may have no equivalent yet. Check the supported-hardware list carefully, since support is per architecture target and consumer cards are often outside it.

### Sectors per Request
**Short:** Nsight Compute metric: 32-byte sectors fetched per warp memory instruction; 4 is ideal, higher means waste.
**Kind:** api
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

### syncthreads
**Short:** CUDA's __syncthreads block barrier: every thread must reach it, which is why divergent calls hang the kernel.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @3

### syncwarp
**Short:** CUDA warp-scoped barrier and memory fence, required on Volta+ where warp lockstep is no longer implicit.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @3

### threadfence
**Short:** CUDA memory fence making a thread's prior global writes visible device-wide; needed for cross-block handoff.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### Thrust
**Short:** Header-only CUDA C++ template library with STL-like parallel algorithms - reduce, scan, sort - over device_vector.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, runtime-systems/collections-and-algorithms @2, gpu/kernel-programming @3

It gives CUDA the shape of the C++ standard library: device_vector owns device memory with RAII and copies to and from a host vector by assignment, while algorithms such as reduce, sort, transform, inclusive_scan and reduce_by_key take iterators and dispatch to tuned kernels for the device. Fancy iterators are what keep it efficient, since a transform_iterator or zip_iterator lets you fuse an elementwise operation into a reduction instead of writing an intermediate array back to memory.

Reach for it so that you never hand-write a reduction or a scan, both of which are easy to get subtly wrong. Drop below it to CUB, the library Thrust is built on, when you need block-level or warp-level primitives inside a kernel you are already writing, or when you need control over the launch configuration that the high-level API does not give you.

### Thrust zip_iterator
**Short:** Thrust iterator that views several SoA arrays as one tuple sequence, keeping coalesced access without an AoS layout.
**Kind:** api
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2

### ThunderKittens
**Short:** C++ template library of tile primitives for writing Hopper and Blackwell Tensor Core kernels concisely.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, gpu/gpu-math-libraries @2

The abstraction is the tile rather than the thread. You declare register tiles and shared tiles of a fixed small shape and call operations on them, including load, store, multiply-accumulate, transpose and reductions, while the library handles the lane mapping, the swizzled shared-memory layout that avoids bank conflicts, and the asynchronous copies that keep the pipeline fed. A fused attention kernel then reads as a few dozen tile operations instead of hundreds of lines of index arithmetic.

It targets recent tensor-core hardware specifically, building on the asynchronous copy and warpgroup matrix instructions that Hopper and Blackwell provide, so it is not a portability layer. Reach for it to write a custom fused kernel where CUTLASS feels too heavy and Triton does not give enough control over the memory pipeline. It is a research-led project, so expect the API to keep moving.

### torch.amp.autocast
**Short:** PyTorch context manager that runs eligible ops in bf16/fp16 automatically while keeping reductions in fp32.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, model-training/deep-learning-framework @2, inference/quantization-and-compression @3

### torch.amp.GradScaler
**Short:** Scales the loss before backward so small fp16 gradients do not underflow, then unscales before the optimizer step.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, model-training/deep-learning-framework @2

### torch.autograd.set_detect_anomaly
**Short:** PyTorch debug mode that traces a NaN or Inf gradient back to the forward op that produced it; very slow.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, model-training/deep-learning-framework @2

### torch.cuda.CUDAGraph
**Short:** PyTorch's raw CUDA graph capture object (capture_begin/capture_end/replay) that removes per-kernel launch overhead.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3

### torch.cuda.Event
**Short:** PyTorch wrapper over CUDA events for lightweight on-GPU timing and cross-stream synchronization from Python.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

### torch.cuda.get_device_properties
**Short:** PyTorch query returning SM count, compute capability and memory limits for launch-configuration decisions.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-profiling-and-debugging @3

### torch.cuda.graph
**Short:** PyTorch context manager that captures a block of GPU work into a CUDA graph for low-overhead replay each step.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2

### torch.cuda.Stream
**Short:** PyTorch handle on a CUDA stream, used to overlap compute with pinned-memory host-device copies.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, runtime-systems/concurrency-and-async @2

### torch.cuda.synchronize
**Short:** PyTorch call blocking until queued CUDA work finishes; pins async errors and timings to the right call site.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @2, observability/profiling-and-performance @3

### torch.profiler
**Short:** PyTorch's built-in CPU+CUDA profiler: per-operator timing, memory and FLOPs, exported as a Chrome trace.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @2

### torch.use_deterministic_algorithms
**Short:** PyTorch switch forcing deterministic kernels and raising on ops that have none; run-to-run reproducibility.
**Kind:** api
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, model-training/deep-learning-framework @2

### torch.utils.cpp_extension
**Short:** PyTorch utility that JIT- or AOT-compiles hand-written .cu/.cpp sources into a pybind11-bound custom op.
**Kind:** api
**Lang:** python
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @2

### vendor ICDs
**Short:** OpenCL's Installable Client Driver mechanism: each vendor ships an ICD, host code enumerates all at runtime.
**Kind:** concept
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1

An OpenCL program links against a loader, not a driver. Each vendor installs a shared library plus a small entry naming it, and at platform enumeration the loader reads those entries, loads each driver it finds, and returns all of them as platforms. Every subsequent call is dispatched through the object's own function table, which is what allows one process to hold contexts on several vendors' devices at the same time.

This is why a single OpenCL binary runs on whatever hardware a machine happens to have, and it is also the usual cause of a program reporting no platforms at all: the runtime package is missing, a vendor entry points at a library that is not there, or a container never mounted the host's driver. Debug it by listing the vendor directory and running `clinfo` before suspecting your own code.

### wgpu
**Short:** Rust implementation of the WebGPU standard, giving portable GPU compute in browsers and in native apps.
**Kind:** tech
**Lang:** rust
**Roles:** gpu/gpu-portability-and-precision @1, gpu/kernel-programming @2

It implements the WebGPU API in Rust over native backends, namely Vulkan, Metal, DirectX 12 and OpenGL, so the same code runs as a desktop binary or, compiled to WebAssembly, against the browser's own WebGPU implementation. Compute work is written in WGSL and dispatched as workgroups over storage buffers bound through explicit bind groups and pipelines, with resource state and lifetime validated rather than left to the programmer.

Reach for it when the target is the browser or a portable desktop application and shipping vendor drivers is not an option. It is not a datacentre compute stack: the portable API has no tensor-core-class matrix instructions, buffer size and workgroup limits are conservative, and support for the newest hardware features only arrives once the standard defines them.

### When each wins
**Short:** Table-row fragment, not a product: MIG versus MPS, hard blast-radius isolation against shared small-model throughput.
**Kind:** concept
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1

### xformers
**Short:** Meta's library of memory-efficient attention kernels and transformer building blocks for PyTorch.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @3, model-training/deep-learning-framework @3

Its memory-efficient attention entry point implements the same tiled, never-materialize-the-score-matrix idea as flash attention while accepting shapes and biases the more specialized kernels reject, including block-sparse patterns and arbitrary additive attention biases. Alongside it are fused building blocks such as fused SwiGLU and normalization layers that cut launch overhead and intermediate tensors.

PyTorch's built-in scaled dot product attention now covers the common case, so reach for xformers when your model needs an attention mask or bias pattern the built-in kernels fall back on, or when you are working in a diffusion or vision transformer codebase that already depends on it.

### XMX
**Short:** Intel Xe Matrix Extensions: the matrix engines on Arc/Flex/Max GPUs that accelerate FP16 and INT8 math.
**Kind:** spec
**Lang:** *
**Roles:** gpu/gpu-portability-and-precision @1, inference/quantization-and-compression @3

These are systolic matrix engines inside each Xe-core, separate from the vector engines, executing a dot-product-and-accumulate instruction over a small matrix block with FP16, BF16, INT8 and INT4 inputs accumulating into a wider type. They fill the same role tensor cores do on NVIDIA parts and AMX does on Xeon, letting matrix work run on dedicated hardware while the vector units handle everything that is not a matrix multiply.

You reach them through libraries rather than directly, since oneDNN, OpenVINO and the SYCL joint-matrix extension all target them, so the practical question is whether your precision and layout qualify. Note that not every Intel GPU has them: they are on the Arc, Flex and Max parts, while integrated graphics without XMX fall back to the vector path and lose most of the low-precision advantage.
