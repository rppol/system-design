# CUDA Memory Hierarchy Reference

A cross-cutting cheat-sheet for the number every kernel-optimization case study eventually needs:
where does this byte live, how many cycles does it cost to touch, and how much bandwidth is
behind it. Read this before touching shared or global memory in any case study; for the full
narrative treatment (with diagrams, code, and Q&A) see
[`../../cuda_memory_model_and_hierarchy/README.md`](../../cuda_memory_model_and_hierarchy/README.md),
[`../../memory_coalescing_and_access_patterns/README.md`](../../memory_coalescing_and_access_patterns/README.md),
and [`../../shared_memory_and_bank_conflicts/README.md`](../../shared_memory_and_bank_conflicts/README.md).

---

## 1. The Hierarchy at a Glance

The single authoritative table. Numbers are ballparks for a data-center Ampere/Hopper part
(A100/H100) — exact cycle counts vary by generation and are always best confirmed with
Nsight Compute, but these are the figures to reason with in an interview.

| Level | Approx. Latency | Approx. Bandwidth | Scope | Lifetime | Cached? |
|-------|-----------------|--------------------|-------|----------|---------|
| Registers | ~1 cycle | effectively unlimited on-chip (64K x 32-bit regs/SM = 256 KB) | thread | thread (kernel launch) | N/A — dedicated storage, not a cache |
| Shared memory / L1 | ~20-30 cycles | tens of TB/s aggregate across all SMs (on-chip SRAM) | thread block | block (until the block retires) | Shared mem: not cached, it IS the scratchpad; L1: caches local/global loads |
| L2 cache | ~200 cycles | several TB/s, device-wide (shared by every SM) | device (all SMs) | until evicted | Cached, hardware-managed, unified across the chip |
| Global memory (HBM) | ~400-800 cycles | ~3 TB/s (HBM3, H100) | device / grid | application (until `cudaFree` or context teardown) | Backed by L2 (and L1 if not bypassed) |
| Host memory over PCIe | ~microseconds round-trip | PCIe Gen5 x16 ~64 GB/s; NVLink (GPU-GPU) ~900 GB/s | system (host + device) | process lifetime (pinned) | Not cached by the GPU; pinned memory is DMA'd directly |

**Read the table as a ladder, not a list.** Each step down is roughly one to two orders of
magnitude higher latency and one to two orders of magnitude more capacity than the step above.
A kernel that reuses data one level higher than it needs to is the single biggest lever in
GPU performance engineering — this is precisely what tiling (registers reuse a value across
an inner loop, shared memory reuses a tile across a thread block) buys you.

---

## 2. The Memory Hierarchy Pyramid

```
                              +-------------------+
                              |     REGISTERS     |     ~1 cycle
                              |  64K x 32-bit/SM   |     thread-private
                              +-------------------+
                             /                     \
                            +-----------------------+
                            |  SHARED MEMORY / L1   |     ~20-30 cycles
                            |   32 banks x 4B/SM    |     block-scoped, on-chip
                            +-----------------------+
                           /                         \
                          +---------------------------+
                          |         L2 CACHE          |     ~200 cycles
                          |    shared by all SMs      |     device-wide
                          +---------------------------+
                         /                             \
                        +-------------------------------+
                        |    GLOBAL MEMORY (HBM3)       |     ~400-800 cycles
                        |        ~3 TB/s (H100)         |     device / grid
                        +-------------------------------+
                       /                                 \
                      +-----------------------------------+
                      |   HOST MEMORY (over PCIe/NVLink)   |    ~microseconds
                      |   PCIe Gen5 ~64 GB/s, NVLink ~900  |    system-scoped
                      +-----------------------------------+

  Each descending tier: ~10-100x more capacity, ~10-100x higher latency, lower BW per byte.
  Performance engineering = keep the working set as high in this pyramid as it will fit.
```

The pyramid widens going down because capacity grows (KB of registers per SM, up to tens of
GB of HBM, up to TB of host RAM) while speed shrinks — the same shape as a CPU's L1/L2/L3/DRAM
hierarchy, except a GPU has orders of magnitude more parallel demand hitting it at once, which
is why coalescing and tiling matter so much more here than on a CPU.

---

## 3. The Six Logical Memory Spaces

Where the pyramid describes physical tiers, this table describes the **programmer-visible
spaces** the CUDA memory model exposes — several of which alias onto the same physical tier
(local memory, for instance, is physically global memory despite its name).

| Space | Qualifier | Scope | Lifetime | Typical Use | Gotcha |
|-------|-----------|-------|----------|--------------|--------|
| Register | (implicit — automatic scalar variables) | thread | thread (kernel launch) | loop counters, accumulators, intermediate values | Register spilling: exceed the 64K x 32-bit/SM budget (or an `__launch_bounds__` cap) and the compiler silently spills to **local memory** — same latency as global, no warning at compile time beyond `-Xptxas -v` output |
| Local | (implicit — spilled registers, large/indexed automatic arrays) | thread | thread | large per-thread scratch arrays, register overflow | Despite the name, "local" memory is physically **global memory (HBM)** — it is thread-private in scope only, not in physical location or speed |
| Shared | `__shared__` | thread block | block (until the block retires) | tile staging for GEMM/convolution, reduction scratch, inter-thread communication within a block | Bank conflicts: 32 banks of 4 bytes each — threads in a warp hitting *different addresses in the same bank* serialize; the classic fix is padding a `[N][N+1]` array |
| Global | `__device__` / `cudaMalloc` | device (grid) | application (until `cudaFree` or context destroyed) | primary data storage — input/output tensors, model weights, activations | Uncoalesced access: a warp whose 32 threads touch non-contiguous addresses fragments one 128-byte transaction into many, up to 8-32x slower |
| Constant | `__constant__` | device (grid), read-only inside kernels | application | values every thread reads identically — convolution coefficients, scaling factors, small lookup tables | Fast only on **broadcast** (every thread in the warp reads the same address); divergent addresses serialize through the small (64 KB) constant cache |
| Texture / Surface | texture object (`cudaTextureObject_t`) / surface object | device | application (bound for the object's lifetime) | 2D/3D spatially-local reads — image processing, stencils, hardware bilinear interpolation | The win depends on 2D/3D spatial locality; a purely linear-access kernel gets no benefit over a plain global read and still pays object-creation overhead |

---

## 4. How to Choose a Memory Space

A short decision path for "which space should this data live in," read top to bottom — try
each rung before falling through to the next:

1. **Does the value live only in one thread's working set for a short time (a loop
   accumulator, an index, a partial sum)?** Put it in a plain local variable and let the
   compiler keep it in a **register**. This is free — don't fight the compiler by
   over-allocating shared memory for something a register would hold.
2. **Is the same tile of data read repeatedly by *multiple threads in the same block* (a GEMM
   operand tile, a convolution halo, a reduction's partial sums)?** Stage it in **shared
   memory** once per block, then have every thread read the on-chip copy instead of re-issuing
   redundant global loads. This is the single highest-leverage optimization in the section —
   it is what turns a memory-bound naive kernel into a compute-bound tiled one.
3. **Is the data read once per thread (or in a pattern that shared-memory tiling can't help),
   and does it need to be as large as the whole problem (input/output arrays, weights)?** It
   belongs in **global memory** — but arrange the *access pattern* so that consecutive threads
   in a warp touch consecutive addresses (coalescing), and consider `float4`/`float2` vectorized
   loads to shrink the transaction count.
4. **Is it a small, read-only value that every thread (or every thread in a warp) needs the
   identical copy of — a kernel radius, a set of filter coefficients, a scalar hyperparameter?**
   Put it in **constant memory** and structure the kernel so all threads in a warp dereference
   it at the same address, so the access broadcasts in one cycle instead of serializing.
5. **Is the access pattern 2D/3D and spatially local (a stencil neighborhood, an image
   patch) rather than linear?** Consider a **texture/surface object** — it adds a dedicated
   cache tuned for 2D locality and free hardware interpolation, at the cost of setup overhead
   that only pays off when the spatial-locality win is real.
6. **Did none of the above fit and the compiler is now spilling registers?** You've landed in
   **local memory** by default, not by choice — that's a signal to lower per-thread register
   pressure (smaller unroll factor, `__launch_bounds__`, fewer live temporaries) rather than to
   accept the spill.

---

## 5. Worked Latency Comparison

Using the section's canonical constants (`../../README.md` §5): a kernel that reads one
`float` per thread from **global memory** with a cold L2 pays roughly 400-800 cycles before
the value is usable — during which the SM's warp scheduler ideally swaps in other resident
warps to keep the ALUs fed (this is *why* occupancy exists). The same read served from
**shared memory** after a single cooperative load-and-`__syncthreads()` costs ~20-30 cycles —
a 15-25x latency reduction — which is the entire arithmetic behind why a tiled GEMM
outperforms the naive one-global-read-per-multiply-add version. A **register**-resident value
(the running accumulator in that same GEMM tile) costs ~1 cycle: effectively free, which is
why the register-blocking rung of the GEMM optimization ladder (each thread computes a small
output micro-tile instead of one element) is the step that gets a kernel from "good" to
"near peak."

---

## 6. Cache Load Modifiers (a Fast Lever You Control)

Global-memory loads aren't all-or-nothing cached — `nvcc`/`ptxas` expose per-load cache
behavior through PTX load qualifiers, reachable in CUDA C++ via `__ldg()` or compiler cache
hints:

| Modifier | Meaning | When to reach for it |
|----------|---------|------------------------|
| `.ca` (cache all) | Default: cache in L1 and L2 | General-purpose reads with reuse at the block level |
| `.cg` (cache global) | Cache in L2 only, bypass L1 | Data too large to profit from L1, or to avoid evicting a tile you deliberately staged in L1/shared |
| `.cs` (cache streaming) | Evict-first hint — cached but marked for early eviction | Streaming reads touched exactly once (a pass over a huge array with no reuse) |
| `__ldg()` intrinsic | Route through the read-only texture-path cache | Read-only global data aliased with `const __restrict__` — frees up the normal L1/L2 path |

The practical takeaway: if a kernel's profiled DRAM throughput is far below the HBM ceiling and
the access pattern is already coalesced, check whether it is fighting itself for L1/L2 space
with data that would be better marked streaming or routed through `__ldg()`.

---

## 7. Generation Notes (Volta -> Blackwell)

The hierarchy's shape is constant across generations; the numbers and a few extra rungs are
not. Reuse the compute-capability table in `../../README.md` §5 for the full generation list —
the memory-relevant deltas are:

| Generation | Memory-hierarchy delta from the previous generation |
|-----------|--------------------------------------------------------|
| Volta (7.0) | Independent thread scheduling changes what "the same warp" means for shared-memory races — `__syncwarp()` becomes necessary where lockstep used to be implicit |
| Ampere (8.0/8.6) | Adds asynchronous copy (`cp.async`) — global-to-shared transfers that bypass registers entirely, freeing the register file and overlapping the copy with compute; L2 grows to 40 MB (A100) |
| Hopper (9.0) | Adds the Tensor Memory Accelerator (TMA) — a dedicated engine for bulk async global-to-shared tile copies with hardware-managed bounds checking; adds thread-block clusters and **distributed shared memory**, where one SM can address another SM's shared memory directly within a cluster — a new rung between "shared memory" and "L2" that did not exist before |
| Blackwell (10.0) | Extends the Transformer Engine and NVLink domain size; the tiered latency/bandwidth *shape* in §1 still applies, with a larger register file and L2 budget |

The one number worth internalizing beyond the §1 table: on Hopper+, a well-written tiled
kernel increasingly issues its shared-memory loads via TMA rather than per-thread `ld.shared`
instructions — the hierarchy is the same, but *who* moves the data between tiers is
shifting from "every thread" to "a dedicated copy engine."

---

## 8. Pitfalls

- **Optimizing occupancy before memory pattern.** Raising occupancy doesn't help if the
  access pattern itself wastes bandwidth — fix coalescing and bank conflicts first; occupancy
  tuning is what you reach for once the access pattern is already good.
- **Treating "local" as on-chip.** Local memory is a *scope*, not a *tier* — it's just as
  slow as global memory. A kernel with heavy register spilling into local memory can be
  slower than a naive global-memory kernel with no spilling at all.
- **Assuming constant memory is always fast.** It is a 64 KB cache tuned for broadcast; a
  kernel where each thread in a warp indexes a *different* constant-memory address gets no
  benefit over global memory and can be worse due to the smaller cache.
- **Sizing a shared-memory tile without checking the per-SM budget.** Shared memory is a
  finite per-SM resource shared with the blocks resident on that SM — an oversized
  `__shared__` array silently caps how many blocks can be co-resident, which caps occupancy,
  independent of the register budget.
- **Forgetting the L2 cache exists.** Repeated global reads to the same address across
  different blocks/waves are still much cheaper than a cold HBM fetch because L2 (tens of MB
  on modern data-center GPUs) sits between global memory and every SM — a kernel with poor
  reuse *within* a block can still perform acceptably if the *cross-block* working set fits
  in L2.

---

## Related Files

- [`../../cuda_memory_model_and_hierarchy/README.md`](../../cuda_memory_model_and_hierarchy/README.md) — full narrative treatment of every memory space, with code and Q&A
- [`../../memory_coalescing_and_access_patterns/README.md`](../../memory_coalescing_and_access_patterns/README.md) — the global-memory access-pattern deep dive this reference's global-memory row depends on
- [`../../shared_memory_and_bank_conflicts/README.md`](../../shared_memory_and_bank_conflicts/README.md) — the shared-memory tiling and bank-conflict deep dive this reference's shared-memory row depends on
- [`roofline_and_arithmetic_intensity.md`](roofline_and_arithmetic_intensity.md) — pairs with this file: memory hierarchy tells you *where* data lives, roofline tells you *whether that placement matters* for your kernel's arithmetic intensity
