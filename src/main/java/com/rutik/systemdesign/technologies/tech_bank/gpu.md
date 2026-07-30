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

### Apex
**Short:** NVIDIA PyTorch extension with fused optimizers such as FusedAdam and the original mixed-precision training tools.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, model-training/distributed-training @2, model-training/deep-learning-framework @3

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

### community SYCL backends
**Short:** SYCL-for-CUDA and SYCL-for-HIP plugins that let one SYCL source target NVIDIA and AMD GPUs, not just Intel.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1

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

### CUDA C++
**Short:** NVIDIA's C++ dialect for authoring device kernels with full control of memory, launch config and intrinsics.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @3

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

### cuda-memcheck/compute-sanitizer
**Short:** CUDA memory checker that catches out-of-bounds accesses, misaligned pointers and race conditions in kernels.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

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

### cuobjdump
**Short:** CUDA CLI that inspects fatbinaries and objects: SASS disassembly and the list of embedded architectures.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1, devtools/compiler-toolchain-and-codegen @2

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

### cuSPARSE
**Short:** NVIDIA's GPU sparse linear algebra library covering CSR, COO and block-sparse matrix operations.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1

### cuSPARSELt
**Short:** NVIDIA library for structured-sparse (2:4) matrix multiply on tensor cores, turning pruned weights into real speedup.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, inference/quantization-and-compression @2

### CUTE
**Short:** CUTLASS's C++ template layer of tensor and layout abstractions for building Hopper/Blackwell Tensor Core kernels.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2

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

### DPC++ compiler
**Short:** Intel's SYCL implementation and compiler (icpx/dpcpp) for single-source C++ that targets CPUs, Intel GPUs and beyond.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2, gpu/kernel-programming @2

### Flash Attention
**Short:** IO-aware fused attention kernels that tile QKV in SRAM, cutting memory traffic and enabling long sequences.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-math-libraries @1, inference/inference-engine @2, gpu/kernel-programming @3, model-training/deep-learning-framework @3

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
### flash-attention
**Short:** The pip-installable FlashAttention kernels: fused, IO-aware attention with sub-linear memory for long sequences.
**Kind:** tech
**Lang:** python, cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @2, inference/compiler-and-runtime-optimization @3

### flash-attn-3
**Short:** FlashAttention-3: Hopper-optimized fused attention kernels with async pipelining and FP8, ~75% FLOP utilization.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, gpu/gpu-portability-and-precision @3

### flash-linear-attention
**Short:** Triton fused-kernel library for linear-attention architectures such as GLA, RetNet, RWKV and Mamba-2.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3

### flash_attn
**Short:** The pip package shipping Flash Attention 1/2/3 CUDA kernels; install with --no-build-isolation against your torch build.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3

### FlashAttention
**Short:** IO-aware fused attention kernels that tile QK^V in SRAM, avoiding the materialized N-by-N attention matrix.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @2, gpu/kernel-programming @3

### FlashAttention and FlashInfer
**Short:** Fused IO-aware attention kernel libraries; FlashInfer additionally targets paged KV layouts used by serving engines.
**Kind:** tech
**Lang:** python, cpp
**Roles:** gpu/gpu-math-libraries @1, inference/inference-engine @2, gpu/kernel-programming @3

### Global Load/Store Efficiency
**Short:** Legacy CUDA profiler metric: requested bytes over transacted bytes, where 100% means perfectly coalesced access.
**Kind:** concept
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1

### GPUDirect RDMA
**Short:** NVIDIA capability letting a NIC DMA straight into GPU memory, skipping host bounce buffers on inter-node traffic.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, runtime-systems/io-networking-and-syscalls @3

### hipify-clang
**Short:** AMD tool that translates CUDA source to HIP by parsing the real AST, more accurate than the text-pattern hipify-perl.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2

### hipify-perl
**Short:** Text-pattern translator rewriting CUDA source into HIP for AMD GPUs; the lighter sibling of hipify-clang.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, devtools/compiler-toolchain-and-codegen @2

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

### libcudadevrt
**Short:** CUDA device runtime library that must be linked for any translation unit launching kernels from the device.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @2

### Metal
**Short:** Apple's GPU compute and graphics API with MSL kernels, plus MPS and MPSGraph tuned primitive libraries.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1, gpu/kernel-programming @2, gpu/gpu-math-libraries @3

### Metal Performance Shaders
**Short:** Apple's tuned GPU kernel library plus the MPSGraph API, the compute stack for Apple silicon.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-portability-and-precision @2

### MIG
**Short:** NVIDIA Multi-Instance GPU: hardware partitioning of an A100/H100 into isolated slices shared by several workloads.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, platform-delivery/kubernetes-and-orchestration @3, inference/model-server @3

### MIG partitioning per replica
**Short:** Splitting an A100/H100 into MIG slices and pinning one server replica per slice for hard memory and SM isolation.
**Kind:** concept
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, inference/model-server @2, platform-delivery/cloud-platform-and-cost @3

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

### mpirun
**Short:** MPI process launcher that starts one rank per GPU and sets the rendezvous environment NCCL and torch.distributed need.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, model-training/distributed-training @2

### MPS
**Short:** NVIDIA Multi-Process Service letting several processes share one GPU's SMs, packing inference fleets tighter.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1, inference/model-server @2, runtime-systems/memory-processes-and-os @3

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

### NPP
**Short:** NVIDIA Performance Primitives: GPU image and signal routines for resize, colour conversion and JPEG decode.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, applied-ml/vision-speech-and-multimodal @2

### Nsight Compute
**Short:** NVIDIA per-kernel CUDA profiler: occupancy, memory throughput, bank conflicts, warp-stall and Tensor-Core metrics.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, gpu/kernel-programming @3

It profiles one kernel at a time by replaying each launch and collecting hardware counters, which is why overhead is enormous and you must scope a run with `-k` or `--launch-count`. In return you get achieved occupancy, memory throughput against the roofline, shared-memory bank conflicts, warp-stall reasons, and Tensor-Core activity -- and with a `-lineinfo` build it correlates those stalls back to source lines.

Reach for it once you already know which kernel matters, which is a question for Nsight Systems or a timeline. It answers "why is this kernel slow", never "where does my program spend its time", and it says nothing about host-side or multi-GPU behaviour.

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

### NVIDIA cuDNN
**Short:** NVIDIA's tuned deep-learning primitive library; picks the fastest convolution algorithm behind PyTorch and TensorFlow.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3

### NVIDIA Nsight
**Short:** NVIDIA's GPU profiling and debugging tool family (Systems, Compute, Graphics) for kernel and timeline analysis.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @3

Two of the tools are the ones you actually reach for. Nsight Systems is the timeline profiler: it shows CPU threads, CUDA API calls, kernel executions, memory copies and your own annotated ranges on one axis, which is how you discover that the GPU is idle waiting on the data loader rather than slow. Nsight Compute is the per-kernel profiler, reporting occupancy, achieved memory and compute throughput, roofline position and the stall reasons behind them.

Use them in that order: the timeline tells you which kernel matters, the kernel profiler tells you why it is slow. They replace nvprof on current GPU architectures. One thing to keep in mind is that the kernel profiler replays each kernel several times to collect its counters, so its reported wall clock is not your application's wall clock.

### NVIDIA Nsight Compute
**Short:** NVIDIA's kernel-level profiler reporting occupancy, memory access patterns, stall reasons and roofline position.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @3

### NVIDIA Nsight Systems
**Short:** System-wide GPU timeline profiler showing kernels, memcopies, NVTX ranges and CPU-GPU gaps.
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/profiling-and-performance @2

`nsys profile` samples CPU threads, CUDA API calls, kernel launches, memory copies, NCCL collectives and OS runtime activity onto one shared timeline. The value is in the gaps rather than the kernels: you see the GPU idle while the data loader catches up, a synchronize that serialized what should have overlapped, or a collective where one rank arrives late and everyone waits. Annotating your own phases with NVTX ranges makes the timeline readable instead of a wall of anonymous kernels.

It is the right tool for "where is the time going" across a whole process or a whole multi-GPU job. Once you know which kernel to blame, switch to Nsight Compute, which profiles inside a single kernel — occupancy, memory throughput, instruction mix — and answers a different question.
### NVIDIA Transformer Engine
**Short:** NVIDIA library automating FP8 scaling for transformer layers on Hopper/Blackwell; used inside Megatron-LM and NeMo.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, gpu/gpu-math-libraries @2, model-training/distributed-training @2, inference/quantization-and-compression @3

### NVIDIA TransformerEngine
**Short:** NVIDIA library running transformer layers in FP8 on Hopper and later, handling scaling factors automatically.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-portability-and-precision @1, inference/quantization-and-compression @2, model-training/distributed-training @3

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

### nvJPEG
**Short:** NVIDIA GPU library for JPEG decode and image preprocessing, keeping the pipeline off the CPU.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, applied-ml/vision-speech-and-multimodal @2

### NVLink
**Short:** NVIDIA's high-bandwidth GPU-to-GPU interconnect (~900 GB/s per H100) and the NVSwitch crossbar fabric.
**Kind:** tech
**Lang:** *
**Roles:** gpu/multi-gpu-and-collectives @1

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

### nvtx
**Short:** NVIDIA annotation library that names ranges and markers so profiler timelines show application phases.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-profiling-and-debugging @1

You push and pop named ranges around regions of your own code, and the profiler draws them as labelled bands above the kernel timeline, so a trace reads as data loading, forward, backward and optimizer step instead of thousands of anonymous kernel launches. PyTorch and other frameworks expose thin wrappers over the same calls, and the overhead is negligible when no profiler is attached, so the annotations can stay in the code.

Add the ranges before you profile rather than after. Without them the hardest part of reading a large GPU trace is working out which phase of your program a given burst of kernels belongs to, and that is exactly the question the timeline cannot answer on its own.

### OpenAI Triton
**Short:** Python DSL and compiler for writing GPU kernels at block level; most of vLLM's kernels are written in it.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, gpu/gpu-portability-and-precision @3

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

### py3nvml
**Short:** Python bindings over NVML exposing the same GPU utilization, memory and power queries as nvidia-smi.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/metrics-and-monitoring @3

### PyCUDA
**Short:** Thin Python bindings over the CUDA driver API with explicit contexts, memory and inline kernel compilation.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, gpu/gpu-math-libraries @3

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

### Triton
**Short:** Python-embedded DSL for writing block-level GPU kernels without CUDA C++; also the torch.compile codegen backend.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, inference/compiler-and-runtime-optimization @2, gpu/gpu-math-libraries @3

You write a kernel as a Python function decorated `@triton.jit` that operates on blocks of elements — pointer arithmetic, `tl.load`/`tl.store` with masks, `tl.dot` for the matmul — and the compiler handles what CUDA C++ makes you do by hand: mapping work onto threads within a block, staging data through shared memory, and selecting the tensor-core instruction. You still choose block sizes and tiling, which is where the performance lives, and `triton.autotune` sweeps those configurations.

Its most common role is not hand-written at all: `torch.compile`'s Inductor backend generates Triton for fused elementwise and reduction kernels. Write it yourself when a fusion the compiler will not find is the bottleneck — a custom attention variant, a fused normalization — and stay with cuBLAS or cuDNN for the standard shapes they already tune better than you will. Note the name collision with NVIDIA's Triton Inference Server, an unrelated model-serving product.

### vendor ICDs
**Short:** OpenCL's Installable Client Driver mechanism: each vendor ships an ICD, host code enumerates all at runtime.
**Kind:** concept
**Lang:** cpp
**Roles:** gpu/gpu-portability-and-precision @1

### wgpu
**Short:** Rust implementation of the WebGPU standard, giving portable GPU compute in browsers and in native apps.
**Kind:** tech
**Lang:** rust
**Roles:** gpu/gpu-portability-and-precision @1, gpu/kernel-programming @2

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
