# CUDA / GPGPU Case Studies — Learning Path

6 principal-grade case studies + 5 cross-cutting kernel-engineering deep-dives. Every case study uses the **11-section principal template** (Intuition + §1–11); reference file: [`../../llm/case_studies/design_gpu_inference_platform.md`](../../llm/case_studies/design_gpu_inference_platform.md). These are *kernel-optimization* walkthroughs — the "optimize X" counterpart to the module READMEs' "understand X".

> **Build status:** COMPLETE (2026-07-07) — all 6 case studies and 5 cross-cutting primitives authored. Case-study Q&As are intentionally excluded from the game quiz bank (the `case_studies/` path is skipped by `extract.py`), but every file is reachable in the reader via relative links.

---

## Quick Start (if you only have time for three)

| Order | File | Why start here |
|-------|------|----------------|
| 1 | `optimize_matrix_multiplication_kernel.md` | The single most-asked CUDA optimization: naive → coalesced → tiled → register-blocked → Tensor Core, with a roofline reading at each rung. Every performance concept appears once. |
| 2 | `implement_high_performance_reduction.md` | The canonical "optimize this kernel" interview walkthrough — the reduction ladder makes divergence, bank conflicts, and warp shuffle concrete. |
| 3 | `port_a_cpu_pipeline_to_gpu.md` | The "how would you approach GPU acceleration" narrative — profiling, Amdahl budgeting, incremental porting, transfer overlap. |

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

## Cross-Cutting Infrastructure Files

Shared primitives reused across the case studies. Read them when the referenced concern first appears.

| When to Read | File | What It Covers |
|--------------|------|----------------|
| Before any optimization | `cross_cutting/roofline_and_arithmetic_intensity.md` | The roofline model as a kernel-optimization loop; computing arithmetic intensity |
| Before touching shared/global memory | `cross_cutting/cuda_memory_hierarchy_reference.md` | Latency/bandwidth/scope cheat-sheet (registers → shared → L2 → HBM) |
| First kernel you write | `cross_cutting/cuda_error_handling_and_launch_config_patterns.md` | The `CUDA_CHECK` macro, launch-config computation, grid-stride idiom |
| When measuring | `cross_cutting/nsight_profiling_workflow.md` | The profile→fix→re-measure loop with Nsight Systems/Compute |
| When precision matters | `cross_cutting/numerical_precision_and_determinism.md` | FP formats, FMA, atomics-order nondeterminism, reproducibility knobs |

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
                     |
                     v
             optimize_llm_inference_kernels          (quantized + fused; needs attention)

port_a_cpu_pipeline_to_gpu   (methodology — read alongside any of the above)
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

**Maintenance rule:** update this file every time a case study is added — flip its marker, place it in the correct phase group, update the Dependency Map, and add an Interview Prep Shortcut row. Same commit, no exceptions.
