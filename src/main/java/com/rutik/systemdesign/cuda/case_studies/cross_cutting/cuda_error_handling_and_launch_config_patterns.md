# CUDA Error Handling & Launch-Config Patterns

> Cross-cutting reference for: every case study in this section (`../optimize_matrix_multiplication_kernel.md`,
> `../implement_high_performance_reduction.md`, `../build_a_flash_attention_kernel.md`,
> `../accelerate_2d_convolution_and_stencil.md`, `../port_a_cpu_pipeline_to_gpu.md`,
> `../optimize_llm_inference_kernels.md`) — every kernel launch in this section's code
> samples assumes the `CUDA_CHECK` macro defined below.
>
> Related modules: [memory_management_and_data_transfer](../../memory_management_and_data_transfer/README.md)
> (where error checking is first introduced against `cudaMalloc`/`cudaMemcpy`),
> [debugging_correctness_and_numerics](../../debugging_correctness_and_numerics/README.md)
> (compute-sanitizer as the tool that catches what these macros only report),
> [occupancy_and_launch_configuration](../../occupancy_and_launch_configuration/README.md)
> (the full theory behind the launch-config numbers computed here).

---

## Why This File Exists

Every CUDA case study in this section launches kernels. Every one of those launches needs
the same two things: a way to fail loudly instead of silently, and a way to compute
`<<<blocks, threads>>>` correctly. Rather than re-deriving the `CUDA_CHECK` macro and the
ceil-div launch-config idiom in each case study, they all link back here. This is the single
place that idiom is defined, explained, and stress-tested against the mistake that produces
the most confusing bug in CUDA: a kernel that "runs" — no error, no crash — and silently
writes garbage.

---

## The CUDA_CHECK Macro

Every CUDA Runtime API call returns a `cudaError_t`. Ignoring that return value is the single
most common CUDA bug pattern in the wild — `cudaMalloc` silently returns a null pointer on
OOM, the next kernel dereferences it, and the failure surfaces three function calls later as
an illegal-memory-access crash with a stack trace pointing at the *wrong* kernel. The fix is
mechanical: wrap **every** Runtime API call in a macro that checks the return code immediately
and fails at the call site.

```cpp
#include <cstdio>
#include <cstdlib>
#include <cuda_runtime.h>

// Wrap EVERY CUDA Runtime API call in this macro. No exceptions.
#define CUDA_CHECK(call)                                                      \
    do {                                                                      \
        cudaError_t err__ = (call);                                          \
        if (err__ != cudaSuccess) {                                          \
            fprintf(stderr, "CUDA error at %s:%d — %s (%d): %s\n",           \
                    __FILE__, __LINE__,                                      \
                    cudaGetErrorName(err__),                                 \
                    static_cast<int>(err__),                                 \
                    cudaGetErrorString(err__));                              \
            std::exit(EXIT_FAILURE);                                        \
        }                                                                    \
    } while (0)

// Usage: wrap allocation, copy, and every other Runtime call.
float* d_data = nullptr;
CUDA_CHECK(cudaMalloc(&d_data, n * sizeof(float)));
CUDA_CHECK(cudaMemcpy(d_data, h_data, n * sizeof(float), cudaMemcpyHostToDevice));
CUDA_CHECK(cudaFree(d_data));
```

The `do { } while (0)` wrapper is not decoration — it makes the macro a single statement, so
`if (cond) CUDA_CHECK(x); else CUDA_CHECK(y);` parses correctly. `cudaGetErrorName` gives the
symbolic constant (`cudaErrorMemoryAllocation`); `cudaGetErrorString` gives the human-readable
sentence. Print both — the name is what you grep the CUDA docs for, the string is what
explains it to whoever is reading the log at 2 a.m.

**Python (CuPy) equivalent.** CuPy raises `cupy.cuda.runtime.CUDARuntimeError` automatically
on every failed Runtime call — there is no manual check needed for CuPy allocations and
copies. The pattern below is for the case where you launch a raw kernel string and want the
same fail-fast behavior as the C++ macro:

```python
import cupy as cp

def cuda_check(result_code: int, context: str) -> None:
    if result_code != 0:  # cudaSuccess == 0
        name = cp.cuda.runtime.getErrorName(result_code)
        raise RuntimeError(f"CUDA error in {context}: {name} ({result_code})")

# CuPy raises on its own for cp.cuda.runtime.malloc / memcpy, so this helper
# is mainly for cp.RawKernel launches you dispatch manually — see below.
```

---

## Kernel Launches Are Asynchronous — Check BOTH Halves

`CUDA_CHECK` around a kernel launch line is a trap for the unwary, because a kernel launch
`kernel<<<blocks, threads>>>(args)` does **not** return a `cudaError_t` — it returns `void`.
The launch is asynchronous: the host thread queues the launch and moves on immediately,
before the kernel has even started running on the device. This creates **two independent
places an error can surface**, and a correct launch checks both:

1. **Configuration error, reported synchronously** — bad grid/block dimensions, too much
   shared memory requested, no kernel image for this device's compute capability. Caught by
   `cudaGetLastError()` immediately after the launch — this call *is* synchronous and clears
   the last-error flag.
2. **Execution error, reported only after the kernel actually runs** — an out-of-bounds
   write, a misaligned access, an assertion failure inside the kernel, a divide-by-zero.
   Because the launch already returned control to the host, the host does not know about
   this yet. You must force the host to wait with `cudaDeviceSynchronize()`, *then* check
   `cudaGetLastError()` again.

```cpp
__global__ void addKernel(const float* a, const float* b, float* c, int n) {
    int idx = blockIdx.x * blockDim.x + threadIdx.x;
    if (idx < n) {
        c[idx] = a[idx] + b[idx];
    }
}

void launchAdd(const float* d_a, const float* d_b, float* d_c, int n) {
    int threads = 256;
    int blocks  = (n + threads - 1) / threads;   // ceil-div — see next section

    addKernel<<<blocks, threads>>>(d_a, d_b, d_c, n);

    // (1) Launch/configuration error — synchronous, cheap, always call this.
    CUDA_CHECK(cudaGetLastError());

    // (2) Execution error — requires a sync point to surface at all.
    // In hot loops, gate this behind a DEBUG flag so you are not
    // serializing the GPU on every launch in production.
    CUDA_CHECK(cudaDeviceSynchronize());
    CUDA_CHECK(cudaGetLastError());
}
```

**Why two `cudaGetLastError()` calls, not one.** The first call clears whatever error the
*launch itself* produced (config-time). The second call, made only after the forced sync,
reports whatever the *kernel body* produced (run-time). Skipping the first call and only
syncing later means a config error and an execution error can be conflated into a single
generic report, which makes debugging launch-parameter mistakes (see the BROKEN→FIX example
below) much harder than it needs to be.

**Production pattern — debug vs release checking.** Synchronizing after every kernel launch
serializes the GPU and defeats the purpose of a stream-based pipeline, so most production
codebases wrap the expensive sync in a compile-time or environment flag:

```cpp
#ifdef CUDA_DEBUG_SYNC
  #define CUDA_CHECK_KERNEL()                     \
      do {                                        \
          CUDA_CHECK(cudaGetLastError());         \
          CUDA_CHECK(cudaDeviceSynchronize());    \
          CUDA_CHECK(cudaGetLastError());         \
      } while (0)
#else
  #define CUDA_CHECK_KERNEL() CUDA_CHECK(cudaGetLastError())
#endif

addKernel<<<blocks, threads>>>(d_a, d_b, d_c, n);
CUDA_CHECK_KERNEL();
```

In release builds this still catches every configuration error for free (it is synchronous
and essentially zero-cost) while deferring the expensive full-sync execution check to a
debug build or a `compute-sanitizer` run — see
[debugging_correctness_and_numerics](../../debugging_correctness_and_numerics/README.md) for
the sanitizer workflow that replaces manual sync-and-check during active debugging.

---

## Launch-Config Computation

### The Ceil-Div Idiom

The number of blocks must cover `n` elements when `threads` does not evenly divide `n`.
Integer division truncates, so a plain `n / threads` under-launches and silently drops the
tail elements. The fix is the ceil-div idiom, and it is a fixed one-liner you should never
have to re-derive:

```cpp
int n = 1'000'000;
int threads = 256;                          // multiple of the 32-wide warp
int blocks  = (n + threads - 1) / threads;  // ceil-div: rounds UP, never truncates

// 1,000,000 + 255 = 1,000,255 ; 1,000,255 / 256 = 3907 (integer division)
// 3907 * 256 = 1,000,192 >= 1,000,000 -- every element covered, with a
// partially-full last block guarded by the `if (idx < n)` bounds check inside the kernel.
kernel<<<blocks, threads>>>(d_data, n);
```

### Choosing `threads`

`threads` should always be a **multiple of 32** (the warp size) — a non-multiple wastes
lanes in the last warp of every block (e.g. `threads = 100` launches 4 warps per block but
the 4th warp only has 4 active lanes, wasting 28 of 32). In practice, **128–256** threads
per block is the default starting point for most kernels on Volta-through-Blackwell GPUs:
large enough to give the SM's warp scheduler several resident warps to hide the ~400–800
cycle global-memory latency, small enough to leave room for multiple blocks to co-reside on
one SM (max 1024 threads/block, but SM occupancy is bounded by registers and shared memory
too — see the next section). Start at 256 and tune from measured occupancy, not guesswork.

### Occupancy-Driven Sizing

Guessing `threads = 256` is a fine default, but the CUDA Runtime can compute the
*occupancy-maximizing* block size for a specific kernel given its actual register and shared
memory usage, via `cudaOccupancyMaxPotentialBlockSize`:

```cpp
int minGridSize = 0;   // minimum grid size needed for max occupancy
int blockSize   = 0;   // suggested block size

CUDA_CHECK(cudaOccupancyMaxPotentialBlockSize(
    &minGridSize,
    &blockSize,
    addKernel,   // the kernel function pointer — its register usage is inspected
    0,           // dynamic shared memory per block, in bytes (0 if none)
    0));         // block-size limit (0 = no limit beyond hardware max)

int n = 1'000'000;
int blocks = (n + blockSize - 1) / blockSize;   // still ceil-div, with the tuned blockSize

addKernel<<<blocks, blockSize>>>(d_a, d_b, d_c, n);
CUDA_CHECK(cudaGetLastError());
```

This is the right tool once a kernel is written and its resource footprint (registers per
thread, shared memory per block) is fixed — it removes the guesswork of hand-tuning
`threads` against the occupancy calculator spreadsheet. Full theory (why occupancy plateaus,
register spilling, `__launch_bounds__`) lives in
[occupancy_and_launch_configuration](../../occupancy_and_launch_configuration/README.md);
this file only covers the API call you reach for.

---

## The Grid-Stride Loop

A kernel launched with exactly `blocks = ceil(n / threads)` only works correctly if that
grid size is achievable — for very large `n`, the computed `blocks` can exceed the hardware
grid-dimension limit, and even when it does not, launching a fresh, precisely-sized grid for
every problem size prevents you from tuning grid size independently (e.g., to match the
number of SMs for a persistent-kernel style workload). The **grid-stride loop** solves both
problems: launch a *fixed*, reasonably-sized grid regardless of `n`, and have each thread
process multiple elements by striding forward by the total number of threads in the grid.

```cuda
__global__ void gridStrideAdd(const float* a, const float* b, float* c, int n) {
    int idx    = blockIdx.x * blockDim.x + threadIdx.x;
    int stride = gridDim.x * blockDim.x;   // total threads launched across the whole grid

    for (int i = idx; i < n; i += stride) {
        c[i] = a[i] + b[i];
    }
}

// Launch a FIXED grid sized to the device (e.g. a small multiple of the SM count),
// independent of n -- the loop above handles any n, including n > blocks*threads.
int threads = 256;
int blocks  = 132 * 4;   // e.g. 4 blocks/SM on a 132-SM H100 -- tune, don't guess
gridStrideAdd<<<blocks, threads>>>(d_a, d_b, d_c, n);
CUDA_CHECK(cudaGetLastError());
```

**Why this is the idiom, not just an alternative.** (1) It decouples correctness from grid
size — the kernel handles `n` larger than `blocks * threads` for free, with no risk of
exceeding the grid-dimension limit for huge `n`. (2) It improves **hardware reuse** — the
same warp stays resident and keeps issuing work instead of the block finishing and a new one
being scheduled, amortizing the per-block launch/teardown overhead. (3) It lets you **tune
grid size independently of problem size** — e.g., pin `blocks` to a multiple of the SM count
for persistent-kernel patterns, or shrink it to leave SM capacity for a concurrent kernel on
another stream.

### Python (Numba CUDA) Analog

```python
from numba import cuda
import numpy as np

@cuda.jit
def grid_stride_add(a, b, c, n):
    idx = cuda.grid(1)                     # blockIdx.x * blockDim.x + threadIdx.x
    stride = cuda.gridDim.x * cuda.blockDim.x
    for i in range(idx, n, stride):
        c[i] = a[i] + b[i]

n = 1_000_000
threads = 256
blocks = 132 * 4                            # fixed grid, independent of n

d_a = cuda.to_device(np.random.rand(n).astype(np.float32))
d_b = cuda.to_device(np.random.rand(n).astype(np.float32))
d_c = cuda.device_array(n, dtype=np.float32)

grid_stride_add[blocks, threads](d_a, d_b, d_c, n)
cuda.synchronize()   # Numba's analog of cudaDeviceSynchronize + last-error check;
                      # raises a CudaAPIError on execution failure
```

Numba's `cuda.synchronize()` raises a Python exception on a device-side error, so the
try/except around a Numba kernel launch plays the same role as the two-`cudaGetLastError()`
pattern above — the exception is Numba's mechanism for surfacing what would otherwise be a
silent execution failure.

---

## Common cudaError_t Values

A short list of the codes that actually show up in interviews and in production logs — know
what each one implies about *where* to look, not just its name.

| `cudaError_t` | What it means | Where to look first |
|---|---|---|
| `cudaErrorMemoryAllocation` | `cudaMalloc`/`cudaMallocAsync` could not satisfy the request — device out of memory. | Check total allocation vs `cudaMemGetInfo`; look for a leaked allocation from a prior iteration. |
| `cudaErrorIllegalAddress` | A kernel dereferenced an out-of-bounds or unaligned device pointer. **Sticky** — see below. | Re-run under `compute-sanitizer --tool memcheck`; check every array-index bounds guard. |
| `cudaErrorLaunchOutOfResources` | The requested `<<<blocks, threads>>>` needs more registers or shared memory per block than the SM has, given the block size. | Reduce `threads`, reduce shared-memory-per-block request, or add `__launch_bounds__` to cap register usage. |
| `cudaErrorNoKernelImageForDevice` | The binary has no compiled code path (SASS/PTX) for this GPU's compute capability. | Check the `-gencode`/`-arch` flags used to compile; add the target `sm_XX`/`compute_XX`. |
| `cudaErrorInvalidConfiguration` | Grid or block dimensions exceed hardware limits, or a dimension is zero. | Check `blocks`/`threads` computation — a ceil-div bug or a stray `0` is the usual cause. |
| `cudaErrorInvalidValue` | A Runtime API argument is invalid (e.g. negative size to `cudaMalloc`). | Check the argument that produced it — usually an upstream `int` overflow or unset variable. |
| `cudaErrorCudartUnloading` | The CUDA Runtime is tearing down (often during process exit) and a call raced it. | Usually benign at shutdown; if seen mid-run, check for a destructor ordering bug. |

### Sticky Errors and Context Reset

`cudaErrorIllegalAddress` and a handful of other execution-time errors (asserts, misaligned
accesses, some double-free patterns inside a kernel) are **"sticky"**: once one occurs, the
CUDA context is corrupted, and **every subsequent Runtime API call in that process returns
the same sticky error**, no matter what it is, until the process exits and a fresh context is
created. This is the single most confusing symptom in CUDA debugging: a `cudaMemcpy` call
three functions away from the actual bug reports `cudaErrorIllegalAddress`, and it *looks*
like the copy itself is broken, when the real fault happened in a kernel launched much
earlier and only surfaced at the next call that happened to synchronize.

```cpp
badKernel<<<blocks, threads>>>(d_data, n);   // writes out of bounds internally
CUDA_CHECK(cudaGetLastError());              // may still report cudaSuccess (async!)

// ... later, unrelated code ...
CUDA_CHECK(cudaMemcpy(h_out, d_result, sz, cudaMemcpyDeviceToHost));
// --> reports cudaErrorIllegalAddress here, NOT at badKernel's call site,
//     because nothing forced a sync between the two.
```

**The fix is the same discipline as the async section above**: sync-and-check immediately
after every kernel launch during development (or under `compute-sanitizer`), so the sticky
error is attributed to the launch that actually caused it, not to an innocent bystander call
made later. Once sticky, the only recovery within the process is destroying and recreating
the CUDA context (in practice: exit and restart the process) — there is no in-process reset
API for a corrupted context.

---

## BROKEN -> FIX: The Silent Kernel Launch

```cpp
// BROKEN — launch has no error check at all. If `threads` or `blocks` is
// miscomputed (e.g. threads = 2000 > the 1024/block hardware max), or the
// kernel body writes out of bounds, this code reports NOTHING. The host
// proceeds straight to reading `d_result` back, which may contain stale or
// partially-written data with zero indication that anything went wrong.
void runReduction(const float* d_in, float* d_result, int n) {
    int threads = 2048;                          // BUG: exceeds 1024 threads/block max
    int blocks  = (n + threads - 1) / threads;

    reduceKernel<<<blocks, threads>>>(d_in, d_result, n);
    cudaMemcpy(h_out, d_result, sizeof(float), cudaMemcpyDeviceToHost);
    // Program "succeeds", prints a wrong number, and the bug hides for weeks.
}
```

```cpp
// FIX — check the launch AND force a sync-and-check during development,
// so the invalid configuration is caught at the call site, not three
// functions later as an unrelated-looking error.
void runReduction(const float* d_in, float* d_result, int n) {
    int threads = 256;                           // fixed: valid, warp-aligned size
    int blocks  = (n + threads - 1) / threads;   // ceil-div

    reduceKernel<<<blocks, threads>>>(d_in, d_result, n);
    CUDA_CHECK(cudaGetLastError());              // catches launch/config errors here
    CUDA_CHECK(cudaDeviceSynchronize());         // forces execution errors to surface now
    CUDA_CHECK(cudaGetLastError());              // reports the execution error, if any

    CUDA_CHECK(cudaMemcpy(h_out, d_result, sizeof(float), cudaMemcpyDeviceToHost));
}
```

With the original `threads = 2048`, the fixed version fails immediately at the first
`CUDA_CHECK(cudaGetLastError())` with `cudaErrorInvalidConfiguration`, naming the exact
line that launched the bad configuration — instead of a silently wrong number discovered
during a code review weeks later.

---

## Pitfalls

- **Checking the kernel launch's return value directly.** A kernel launch is `void` — there
  is nothing to check on the call itself. The pattern is always
  `kernel<<<...>>>(...); CUDA_CHECK(cudaGetLastError());`, never
  `CUDA_CHECK(kernel<<<...>>>(...))` (which will not even compile).
- **Only calling `cudaGetLastError()` once, right after the launch.** That single call only
  catches configuration errors; execution errors (illegal address, assert) require a
  `cudaDeviceSynchronize()` first. See "Kernel Launches Are Asynchronous" above.
- **`threads` not a multiple of 32.** Wastes lanes in the final warp of every block — always
  round up to the nearest multiple of 32, and default to 128–256 absent a measured reason to
  deviate.
- **Plain `n / threads` instead of ceil-div.** Silently drops the tail elements whenever
  `threads` does not evenly divide `n` — always use `(n + threads - 1) / threads`.
- **Assuming a sticky error is caused by the call that reported it.** Because kernel launches
  are async, the call that *reports* `cudaErrorIllegalAddress` is very often not the call
  that *caused* it — the real cause is an earlier, unchecked kernel launch. Sync-and-check
  after every launch during debugging to pin the fault to its true origin.
- **Leaving `cudaDeviceSynchronize()` checks in hot production loops.** Forcing a full sync
  after every launch serializes the GPU and destroys stream-level overlap. Gate the expensive
  sync-and-check behind a debug build flag (`CUDA_CHECK_KERNEL()` pattern above) or run it
  under `compute-sanitizer` instead of leaving it always-on in the release path.
- **Ignoring `cudaOccupancyMaxPotentialBlockSize`'s suggested block size.** Hand-picking 256
  and never revisiting it leaves performance on the table once a kernel's actual register
  footprint is known — re-run the occupancy query whenever the kernel body changes
  meaningfully (more locals, more shared memory).
