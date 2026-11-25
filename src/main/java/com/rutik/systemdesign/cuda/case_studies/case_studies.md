# GPGPU Case Studies — Learning Path

11 principal-grade case studies + 6 cross-cutting kernel-engineering deep-dives. Eight are CUDA-native; three carry a kernel to AMD MI300X, to Apple silicon, and to all three vendors at once. Every case study uses the **11-section principal template** (Intuition + §1–11); reference file: [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md). These are *kernel-optimization* walkthroughs — the "optimize X" counterpart to the module READMEs' "understand X".

> **Build status:** COMPLETE - all 11 case studies and 6 cross-cutting primitives authored. Case-study Q&As are intentionally excluded from the game quiz bank (the `case_studies/` path is skipped by `extract.py`), but every file is reachable in the reader via relative links.

---

## Quick Start (if you only have time for three)

| Order | File | Why start here |
|-------|------|----------------|
| 1 | `optimize_matrix_multiplication_kernel.md` | The single most-asked CUDA optimization: naive → coalesced → tiled → register-blocked → Tensor Core, with a roofline reading at each rung. Every performance concept appears once. |
| 2 | `implement_high_performance_reduction.md` | The canonical "optimize this kernel" interview walkthrough — the reduction ladder makes divergence, bank conflicts, and warp shuffle concrete. |
| 3 | `port_a_cpu_pipeline_to_gpu.md` | The "how would you approach GPU acceleration" narrative — profiling, Amdahl budgeting, incremental porting, transfer overlap. |

If the role is multi-vendor, swap the third for `ship_one_kernel_across_nvidia_amd_and_apple.md`: one fused kernel through Triton, HIP and MSL with one correctness and performance matrix in CI, which is the shape of the "would this run on AMD or a Mac" follow-up.

---

## Full Learning Path

Grouped by the engineering concern each case study centers on. Read the paired modules first (see [../README.md](../README.md) §4).

### Phase A — Core Kernel Optimization

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| `optimize_matrix_multiplication_kernel.md` | Arithmetic intensity & the memory→compute climb | The full GEMM optimization ladder to Tensor Cores; roofline at each step |
| `implement_high_performance_reduction.md` | Divergence, bank conflicts, warp shuffle | The 7-rung reduction ladder from divergent addressing to cooperative-groups grid reduction |
| `accelerate_2d_convolution_and_stencil.md` | Shared-memory tiling with halos | 2D convolution/stencil, halo regions, separable filters, constant memory |

### Phase B — Applied & Fused Kernels

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| `build_a_flash_attention_kernel.md` | Kernel fusion to avoid HBM round-trips | Fused softmax-attention, online softmax, shared-memory tiling; the "why fuse" argument |
| `optimize_llm_inference_kernels.md` | Low-arithmetic-intensity & quantized kernels | GEMV/attention/KV-cache kernels, INT8/FP8 matmul, kernel fusion — the kernel-level twin of the LLM inference platform case study |

### Phase C — Methodology

| File | Primary Engineering Concern | What It Teaches |
|------|-----------------------------|-----------------|
| `port_a_cpu_pipeline_to_gpu.md` | End-to-end acceleration methodology | Profile → Amdahl budget → incremental port → transfer overlap → verify numerics |

---

### Phase D — Modern Architecture & Multi-GPU

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Write a Blackwell GEMM with the CuTe DSL](write_a_blackwell_gemm_with_cute_dsl.md) | Writing for an architecture whose fast path is asynchronous and warp-specialized | TMA descriptors, `tcgen05` and TMEM, the producer/consumer warp split, and why the naive port of a Hopper kernel leaves most of the machine idle. |
| [Optimize MoE Expert-Parallel Dispatch](optimize_moe_expert_parallel_dispatch.md) | Data-dependent communication as the algorithm | Why all-to-all is the bottleneck once routing is dynamic: token permutation kernels, capacity-factor padding waste, and overlapping dispatch with expert compute. |

### Phase E — Other Vendors

Read the paired Phase 7-10 modules first ([../README.md](../README.md) §4), and keep `cross_cutting/vendor_terminology_and_numbers_reference.md` open.

| Case Study | Primary Concern | What It Teaches |
|---|---|---|
| [Port an LLM Kernel to AMD MI300X](port_an_llm_kernel_to_amd_mi300.md) | Re-tuning a correct port for a different machine | Takes CUDA attention and GEMM kernels to MI300X: hipify, the wave64 retune (64-bit lane masks), the 64 KB LDS budget against H100's 227 KB per block, MFMA through Composable Kernel or Triton, and the rocprof-compute loop. |
| [Run LLM Inference on Apple Silicon](run_llm_inference_on_apple_silicon.md) | A bandwidth ceiling with a shared memory-capacity ceiling on top | On-device decode on M-series with MLX and a custom Metal kernel: the unified-memory budget shared with every other app, 4-bit quantized matmul, and the SIMD-group matrix path; decode is capped by 153 GB/s on an M5 and 614 GB/s on an M5 Max. |
| [Ship One Kernel Across NVIDIA, AMD and Apple](ship_one_kernel_across_nvidia_amd_and_apple.md) | Portability as a property of the test matrix | One fused residual-add plus RMSNorm kernel in Triton, HIP and MSL; the warp width as a template parameter, a per-vendor correctness and performance matrix, and multi-vendor CI with pinned toolchains. |

## Cross-Cutting Infrastructure Files

Shared primitives reused across the case studies. Read them when the referenced concern first appears.

| When to Read | File | What It Covers |
|--------------|------|----------------|
| Before any optimization | `cross_cutting/roofline_and_arithmetic_intensity.md` | The roofline model as a kernel-optimization loop; computing arithmetic intensity |
| Before touching shared/global memory | `cross_cutting/cuda_memory_hierarchy_reference.md` | Latency/bandwidth/scope cheat-sheet (registers → shared → L2 → HBM) |
| First kernel you write | `cross_cutting/cuda_error_handling_and_launch_config_patterns.md` | The `CUDA_CHECK` macro, launch-config computation, grid-stride idiom |
| When measuring | `cross_cutting/nsight_profiling_workflow.md` | The profile→fix→re-measure loop with Nsight Systems/Compute |
| When precision matters | `cross_cutting/numerical_precision_and_determinism.md` | FP formats, FMA, atomics-order nondeterminism, reproducibility knobs |
| Before the first port off NVIDIA (Phase E) | `cross_cutting/vendor_terminology_and_numbers_reference.md` | Term, API and numbers cards across CUDA, HIP, Metal and Vulkan: lockstep width, group-local memory, ballot width, per-device figures |

---

## Dependency Map

```
roofline_and_arithmetic_intensity  (read first — the lens for everything below)
cuda_memory_hierarchy_reference
        |
        v
optimize_matrix_multiplication_kernel   (the flagship — all perf concepts)
        |
        +--> implement_high_performance_reduction   (reduction/warp-shuffle)
        |
        +--> accelerate_2d_convolution_and_stencil  (shared-mem tiling + halos)
        |
        +--> build_a_flash_attention_kernel         (fusion; needs tiling + softmax)
        |            |
        |            v
        |    optimize_llm_inference_kernels          (quantized + fused; needs attention)
        |
        +--> write_a_blackwell_gemm_with_cute_dsl   (TMA + tcgen05 + warp specialization)

port_a_cpu_pipeline_to_gpu   (methodology — read alongside any of the above)
optimize_moe_expert_parallel_dispatch  (multi-GPU all-to-all; standalone)

vendor_terminology_and_numbers_reference  (read before any port off NVIDIA)
        |
        +--> port_an_llm_kernel_to_amd_mi300    (needs flash_attention + matmul; wave64, LDS, MFMA)
        |
        +--> run_llm_inference_on_apple_silicon (needs optimize_llm_inference_kernels; MLX + Metal)
        |
        +--> ship_one_kernel_across_nvidia_amd_and_apple
                     (builds on both studies above; Triton + HIP + MSL, one test matrix)
nsight_profiling_workflow    (referenced by every case study §4)
numerical_precision_and_determinism  (referenced when precision/repro comes up)
```

---

## Interview Prep Shortcuts

Map a likely interview prompt to the case study that rehearses it.

| Interview Topic | Best Case Study |
|-----------------|-----------------|
| "Optimize this matrix multiply / GEMM on a GPU." | `optimize_matrix_multiplication_kernel.md` |
| "Write a fast parallel reduction / sum." | `implement_high_performance_reduction.md` |
| "How would you implement FlashAttention / a fused attention kernel?" | `build_a_flash_attention_kernel.md` |
| "Speed up this image filter / stencil / convolution." | `accelerate_2d_convolution_and_stencil.md` |
| "How would you accelerate this CPU pipeline on a GPU?" | `port_a_cpu_pipeline_to_gpu.md` |
| "Optimize the kernels behind LLM inference (GEMV, KV-cache, quantized matmul)." | `optimize_llm_inference_kernels.md` |
| Write a GEMM for Blackwell / what changed with tcgen05 and TMA? | [Write a Blackwell GEMM with the CuTe DSL](write_a_blackwell_gemm_with_cute_dsl.md) |
| How do MoE dispatch/combine kernels work, and why is all-to-all the bottleneck? | [Optimize MoE Expert-Parallel Dispatch](optimize_moe_expert_parallel_dispatch.md) |
| "Port this CUDA attention or GEMM kernel to an MI300X." | [Port an LLM Kernel to AMD MI300X](port_an_llm_kernel_to_amd_mi300.md) |
| "Run an LLM locally on a Mac — what limits tokens per second?" | [Run LLM Inference on Apple Silicon](run_llm_inference_on_apple_silicon.md) |
| "Ship one kernel on NVIDIA, AMD and Apple — how do you keep it correct and fast?" | [Ship One Kernel Across NVIDIA, AMD and Apple](ship_one_kernel_across_nvidia_amd_and_apple.md) |

**Maintenance rule:** update this file every time a case study is added — flip its marker, place it in the correct phase group, update the Dependency Map, and add an Interview Prep Shortcut row. Same commit, no exceptions.
