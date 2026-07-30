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

### cuBLAS
**Short:** NVIDIA's tuned dense BLAS library for GPUs; the GEMM path behind torch.matmul and automatic Tensor Core routing.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/kernel-programming @3

### cuBLASLt
**Short:** NVIDIA's descriptor-based GEMM API above cuBLAS: fused epilogues, mixed precision, Tensor Core algorithm search.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/gpu-math-libraries @1, gpu/gpu-portability-and-precision @2

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

### DCGM
**Short:** NVIDIA Data Center GPU Manager: fleet GPU telemetry (utilization, memory, power, thermals, ECC).
**Kind:** tech
**Lang:** *
**Roles:** gpu/gpu-profiling-and-debugging @1, observability/metrics-and-monitoring @2

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

### Flash Attention 2/3
**Short:** Fused IO-aware attention CUDA kernels that avoid materializing the score matrix; effectively required for long context.
**Kind:** tech
**Lang:** python
**Roles:** gpu/gpu-math-libraries @1, inference/compiler-and-runtime-optimization @2, model-training/deep-learning-framework @3, gpu/kernel-programming @3

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

### nccl-tests
**Short:** NVIDIA's benchmark suite that measures achieved collective bandwidth (all-reduce busbw) against a topology's ceiling.
**Kind:** tech
**Lang:** cpp
**Roles:** gpu/multi-gpu-and-collectives @1, gpu/gpu-profiling-and-debugging @2

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

### Numba CUDA
**Short:** Numba's CUDA backend: write GPU kernels in Python with JIT compilation, shared memory and warp intrinsics.
**Kind:** tech
**Lang:** python
**Roles:** gpu/kernel-programming @1, devtools/compiler-toolchain-and-codegen @3

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

### XMX
**Short:** Intel Xe Matrix Extensions: the matrix engines on Arc/Flex/Max GPUs that accelerate FP16 and INT8 math.
**Kind:** spec
**Lang:** *
**Roles:** gpu/gpu-portability-and-precision @1, inference/quantization-and-compression @3
