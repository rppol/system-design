# GPGPU multi-vendor expansion of `cuda/` — design

Date: 2026-11-25. Branch: `feat/gpgpu-multivendor`. Status: approved by owner in chat
(sections 1-2 explicitly; section 3 delegated: "do what you think is best").

## Goal

Turn the `cuda/` section from an NVIDIA-only guide into a GPGPU guide that covers AMD ROCm,
Apple Silicon (Metal) and Vulkan compute at the same kernel-author altitude, without losing
any learner's spaced-repetition state.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Section slug | Keep `cuda/` | `qid()` hashes `module|question`; renaming the folder re-keys every question and deletes review state. About 39 inbound links also stay valid |
| Display name | `SECTION_LABELS.cuda` = `"GPGPU Programming"`; README H1 = "GPGPU Programming: CUDA, ROCm, Metal and Vulkan" | Vendor-neutral, short enough for the section chips |
| Integration model | CUDA stays the core path (phases 1-6). Each other vendor gets its own phase. Existing modules get only a short "On other vendors" table | Smallest re-audit risk; no existing answer changes |
| Intel / WebGPU | Not separate phases. Stay inside `gpu_portability_hip_sycl_and_beyond` | Owner did not select them |
| Tiers | Keep Senior only; no Principal tier | Section CLAUDE.md rule; YAGNI |

## New modules (12) — 14-section template

Phase 7, AMD ROCm and HIP:
1. `amd_gpu_architecture_cdna_and_rdna` — CDNA 3/4 (MI300X, MI325X, MI355X), RDNA, chiplets
   and XCDs, wave64 vs wave32, CU/SIMD layout, LDS, MFMA matrix cores, Infinity Fabric and
   Infinity Cache, gfx targets.
2. `hip_programming_and_porting` (18 Q&As) — HIP runtime API, hipcc / amdclang++, hipify-clang
   and hipify-perl, the warpSize-64 porting traps, `__shfl` and ballot widths, code objects
   and `--offload-arch`, HIP on NVIDIA.
3. `rocm_libraries_and_kernel_dsls` — hipBLASLt, rocBLAS, MIOpen, rocPRIM / hipCUB,
   rocWMMA, Composable Kernel, AITER, Triton on AMD, PyTorch on ROCm.
4. `rocm_profiling_and_debugging` — rocprofv3, rocprof-compute, rocprof-sys, rocgdb,
   amd-smi, hardware counters vs Nsight equivalents.
5. `multi_gpu_on_amd_rccl_and_xgmi` — RCCL, xGMI / Infinity Fabric links, rocSHMEM, peer
   access, topology.

Phase 8, Apple Silicon and Metal:
6. `apple_silicon_gpu_architecture` — unified memory, TBDR, SIMD-groups (32 wide),
   threadgroup memory, Dynamic Caching, GPU vs Neural Engine vs AMX/SME.
7. `metal_compute_programming` (18 Q&As) — MSL, `MTLDevice` / command queues / command
   buffers / compute encoders, pipelines, threadgroups, SIMD-group functions,
   `simdgroup_matrix`, Metal 4 tensor APIs, resource storage modes.
8. `metal_performance_shaders_mlx_and_pytorch_mps` — MPS, MPSGraph, MLX arrays and
   `mx.fast.metal_kernel`, PyTorch MPS backend, Core ML hand-off.
9. `metal_profiling_and_debugging` — Xcode GPU frame capture, Metal System Trace,
   shader profiler and cost graph, Metal API / shader validation.

Phase 9, Vulkan compute:
10. `vulkan_compute_fundamentals` — instance / device / queues, compute pipelines,
    descriptor sets, push constants, memory types and heaps, barriers and timeline
    semaphores, GLSL / HLSL / Slang to SPIR-V.
11. `vulkan_compute_advanced_and_ecosystem` — subgroup ops, cooperative matrix, integer dot
    product, buffer device address, MoltenVK, llama.cpp / IREE / ncnn backends, RenderDoc
    and vendor profilers.

Phase 10, Cross-vendor:
12. `cross_vendor_gpu_concept_map` (18 Q&As) — one vocabulary and one numbers table:
    warp / wavefront / SIMD-group / subgroup; shared / LDS / threadgroup / workgroup memory;
    Tensor Core / MFMA / `simdgroup_matrix` / cooperative matrix; streams / queues /
    command buffers; barriers and atomics per API.
- `gpu_portability_hip_sycl_and_beyond` moves to Phase 10 (order change only, ids
  unchanged). Its HIP / Metal subsections are trimmed to a paragraph plus links to the new
  phases; questions there are kept, never deleted.

## Case studies (3) — 11-section principal template

- `port_an_llm_kernel_to_amd_mi300.md` — CUDA attention/GEMM to MI300X: hipify, wave64
  retune, LDS budget, MFMA via Composable Kernel or Triton, rocprof-compute loop.
- `run_llm_inference_on_apple_silicon.md` — on-device LLM on M-series with MLX and a custom
  Metal kernel: unified-memory budget, quantized matmul, SIMD-group matrix path.
- `ship_one_kernel_across_nvidia_amd_and_apple.md` — one fused kernel via Triton, HIP and
  MSL/Vulkan; correctness and performance matrix; multi-vendor CI.

New cross-cutting file: `case_studies/cross_cutting/vendor_terminology_and_numbers_reference.md`
(14-section template).

## Changes to existing files

- About 10 modules get one table "On other vendors" at the end of §4 (term, key number,
  link): `gpu_hardware_architecture`, `cuda_programming_model_and_kernels`,
  `warps_and_simt_execution`, `cuda_memory_model_and_hierarchy`,
  `shared_memory_and_bank_conflicts`, `synchronization_and_atomics`,
  `warp_level_primitives_and_cooperative_groups`, `tensor_cores_and_mixed_precision`,
  `profiling_and_performance_analysis`, `multi_gpu_programming_and_nccl`. No §12 edits.
- `cuda/CLAUDE.md`: scope statement, 36-module list in 10 phases, code-language rules per
  phase, version-pin table, boundary rows (AMD GPU Operator stays in `mlops/`, MLX as an
  edge runtime choice stays in `llm/small_language_models_and_edge_ai`).
- `cuda/README.md`: H1, module table, phase diagram, `<!-- study-paths -->` block, §8
  manifest, cross-reference map; regenerate tier table with `extract.py --write-paths`.
- `cuda/case_studies/case_studies.md`: all 5 sections.
- `game/app.js`: `SECTION_LABELS.cuda`, `STUDY_ORDER.cuda` (new ids; portability moved to
  the end), acronym map (`rocm`, `hip`, `msl`, `mlx`, `lds`, `mfma`, `spirv`), `LANG_ALIAS`
  (`hip`, `metal`, `msl`, `glsl`, `hlsl`, `slang` → `cpp`). Swift fences render
  unhighlighted; keep Swift snippets short.
- Root `README.md` and root `CLAUDE.md` CUDA rows.

## Content rules for the new phases

- Code: HIP C++ + Python (PyTorch ROCm / Triton) for Phase 7; MSL + Swift or Python (MLX)
  for Phase 8; GLSL or Slang + C++ host for Phase 9.
- Every version, product name, spec number and hardware figure is fetched from a primary
  source (AMD ROCm docs / release notes, AMD CDNA whitepapers, Apple Developer docs and
  WWDC sessions, Khronos registry, MLX releases) before it is written. Each vendor module
  carries a dated version-anchor blockquote under the H1. Unverifiable facts are cut.
- Section house style otherwise unchanged: 14-section template, `**Q: ...?**` + `**Short:**`
  (15-220 chars), 15 Q&A floor (18 where marked), BROKEN→FIX in §10 and §14, Mermaid with
  One-Dark classDef, ASCII for grids, no emojis.
- Keep first sentences lexically distinct (warp vs wavefront vs SIMD-group answers cluster).
- No file named `*_deep_dive.md` / `*_internals.md`.

## Tiers

Senior adds: `cross_vendor_gpu_concept_map`, `amd_gpu_architecture_cdna_and_rdna`,
`hip_programming_and_porting`, `apple_silicon_gpu_architecture`,
`metal_compute_programming`, `vulkan_compute_fundamentals`. Case studies stay `-`.

## Validation

`python3 game/extract.py --write-paths`, then `python3 game/extract.py --strict` (exit 0,
cuda count grows, Short N == M); `node scripts/check_render.mjs` per touched file;
`diagram_tools.py check` on the section; `game/build_tech.py` §11 lint; link check of new
relative links.

## Build order

1. Pin table (versions fetched) + `cross_vendor_gpu_concept_map` (fixes vocabulary).
2. Phases 7, 8, 9 in parallel — one implementer per phase, own files only.
3. Case studies + cross-cutting reference.
4. Integrator: light-touch tables, portability trim, CLAUDE/README/app.js wiring, validation,
   one local commit. No push.
