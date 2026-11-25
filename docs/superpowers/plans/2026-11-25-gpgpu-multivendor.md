# GPGPU Multi-vendor Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add AMD ROCm, Apple Metal and Vulkan compute phases, a cross-vendor concept map and 3 case studies to the `cuda/` section, rebranded "GPGPU Programming", without changing any existing question id.

**Architecture:** 12 new module pages + 3 case studies + 1 cross-cutting file written in parallel by content agents (each owns only its files), then one integrator wires README, CLAUDE.md, `game/app.js`, root docs and adds "On other vendors" tables to 10 existing modules.

**Tech Stack:** Markdown (14-section module template, 11-section case-study template), Mermaid, ASCII diagrams, HIP C++, MSL, Swift, GLSL/Slang, Python (PyTorch ROCm, Triton, MLX).

**Spec:** `docs/superpowers/specs/2026-11-25-gpgpu-multivendor-design.md`

## Global Constraints

- Repo root `C:\rutik\system-design`. Section root `src\main\java\com\rutik\systemdesign\cuda\`. Never rename or move an existing module directory.
- Read before writing: root `CLAUDE.md` (14-section template, Q&A rules, `**Short:**` rules, Mermaid traps), `cuda\CLAUDE.md` (content rules), and two reference pages: `cuda\gpu_portability_hip_sycl_and_beyond\gpu_portability_hip_sycl_and_beyond.md` and `cuda\cuda_graphs\cuda_graphs.md` (Mermaid style). For case studies also read `cuda\case_studies\build_a_flash_attention_kernel.md`.
- Module page path: `cuda\<module>\<module>.md`. No README.md in a module dir. No `*_deep_dive.md` / `*_internals.md` names.
- Headings exactly `## 1. Concept Overview` ... `## 14. Case Study`, with `## 6. How It Works — Detailed Mechanics`; `---` between top-level sections.
- Version anchor blockquote directly under the H1: `> **Version anchor (fetched 2026-11-25):** ...` stating only facts you fetched.
- Q&A: under `## 12. Interview Questions with Answers`; each `**Q: ...?**` then `**Short:** <15-220 chars, one sentence, no code, no list>` as the first non-blank answer line, then the full answer. Never a numbered list. Floor 15; 18 for `hip_programming_and_porting`, `metal_compute_programming`, `cross_vendor_gpu_concept_map`. Order: traps first, then mechanics, then edge cases. Keep first sentences lexically distinct across the module.
- At least one BROKEN → FIX code pair in §10 and one in §14.
- Concrete numbers everywhere. Every version, spec number, hardware figure and API name is checked against a primary source with web_fetch (AMD ROCm docs/release notes/CDNA whitepapers, Apple Developer docs and WWDC session pages, Khronos registry/spec, MLX GitHub releases, PyTorch docs). If a fact cannot be confirmed, cut it. No "verify" markers.
- Fence tags: `cpp` / `cuda` for CUDA and HIP, `metal` for MSL, `swift`, `glsl`, `hlsl`, `slang`, `python`, `bash`. ASCII diagrams in a fence with no language tag, <= 100 cols, no tabs, captioned.
- Mermaid: One-Dark classDef on every node, `flowchart LR` default, `<br/>` for wraps, rounded nodes, no `;` in sequence notes, no `call`/`end`/`default`/`class`/`style`/`click` node ids or classDef names, quote labels containing `<`, `>`, `:`, `#`, `{}`, `&`. No `\"` (use `#quot;`). One space after an edge label's closing `|`.
- No emojis.
- Links: relative to the file, to PAGES (`../hip_programming_and_porting/hip_programming_and_porting.md`), never bare dirs. Link at least 3 sibling cuda modules per page.
- Do not edit any file you do not own. Do not run git. No content structural metadata (`<!-- study-paths -->`).
- **Only validation: the Mermaid render check.** After writing each file run
  `powershell -NoProfile -ExecutionPolicy Bypass -File "C:\Users\rp261119\.copilot\session-state\c99c0e91-9ce3-40f4-b656-ddd29a8be9de\files\mmcheck.ps1" "<absolute file path>"`
  and fix until it prints `OK: N mermaid fence(s) render.` (or `no mermaid fences`). Run nothing else (no extract.py, no compilers).

## Final file inventory (names fixed; other tasks link to them)

Modules (new): `amd_gpu_architecture_cdna_and_rdna`, `hip_programming_and_porting`, `rocm_libraries_and_kernel_dsls`, `rocm_profiling_and_debugging`, `multi_gpu_on_amd_rccl_and_xgmi`, `apple_silicon_gpu_architecture`, `metal_compute_programming`, `metal_performance_shaders_mlx_and_pytorch_mps`, `metal_profiling_and_debugging`, `vulkan_compute_fundamentals`, `vulkan_compute_advanced_and_ecosystem`, `cross_vendor_gpu_concept_map`.

Case studies (new): `case_studies\port_an_llm_kernel_to_amd_mi300.md`, `case_studies\run_llm_inference_on_apple_silicon.md`, `case_studies\ship_one_kernel_across_nvidia_amd_and_apple.md`, `case_studies\cross_cutting\vendor_terminology_and_numbers_reference.md`.

Case-study template: `## Intuition`, `## 1. Requirements Clarification`, `## 2. Scale Estimation`, `## 3. High-Level Architecture`, `## 4. Component Deep Dives`, `## 5. Design Decisions & Tradeoffs`, `## 6. Real-World Implementations`, `## 7. Technologies & Tools`, `## 8. Operational Playbook`, `## 9. Common Pitfalls & War Stories`, `## 10. Capacity Planning`, `## 11. Interview Discussion Points`. 900-1100 lines; >= 4 links into `case_studies\cross_cutting\` files (existing: `roofline_and_arithmetic_intensity.md`, `cuda_memory_hierarchy_reference.md`, `cuda_error_handling_and_launch_config_patterns.md`, `nsight_profiling_workflow.md`, `numerical_precision_and_determinism.md`; new: `vendor_terminology_and_numbers_reference.md`); executable code in §4 with BROKEN → FIX; named companies with real published facts in §6; quantified impact in §9; >= 10 Q&As in §11 each with a `**Short:**` line.

---

### Task 1: Cross-vendor concept map + terminology reference
Files: create `cross_vendor_gpu_concept_map\cross_vendor_gpu_concept_map.md` (18 Q&As) and `case_studies\cross_cutting\vendor_terminology_and_numbers_reference.md`.
- [ ] Fetch numbers: warp 32; CDNA wavefront 64, RDNA wave32/wave64; Apple SIMD-group 32; Vulkan subgroup size range; LDS per CU (MI300X, MI355X); Apple threadgroup memory limit; shared memory per SM (H100, B200); typical Vulkan `maxComputeSharedMemorySize`; matrix units (Tensor Core, MFMA, `simdgroup_matrix`, `VK_KHR_cooperative_matrix`).
- [ ] Module: master mapping table (execution unit, thread group, memory spaces, sync, atomics, matrix units, queue/stream model, profiler, compiler and IR: PTX/SASS vs AMDGPU ISA/code object vs AIR/metallib vs SPIR-V); numbers table; vector-add and reduction in CUDA, HIP, MSL, GLSL; porting traps (wave width, ballot mask width, memory-model scopes, missing features).
- [ ] Cross-cutting reference: 14-section template, compact quick-lookup version for case studies.
- [ ] Mermaid check both files; report fetched pins (tool, version/fact, URL).

### Task 2: AMD architecture + HIP
Files: `amd_gpu_architecture_cdna_and_rdna\…md`, `hip_programming_and_porting\…md` (18 Q&As).
- [ ] Fetch: CDNA 3/4 whitepapers, MI300X/MI325X/MI355X specs, RDNA 4 basics, current ROCm release, HIP docs, hipify docs, `--offload-arch`, current `warpSize` semantics.
- [ ] Write both. HIP covers the CUDA→HIP API map, hipcc/amdclang++, hipify-clang vs hipify-perl, wave64 traps (64-bit ballot masks, shuffle widths, LDS banks), code objects and gfx targets, HIP on NVIDIA, a `HIP_CHECK` macro.
- [ ] Mermaid check; report pins.

### Task 3: ROCm libraries + profiling
Files: `rocm_libraries_and_kernel_dsls\…md`, `rocm_profiling_and_debugging\…md`.
- [ ] Fetch: hipBLASLt, rocBLAS, MIOpen, rocPRIM, hipCUB, rocWMMA, Composable Kernel, AITER, Triton AMD backend, PyTorch ROCm; rocprofv3, rocprof-compute, rocprof-sys, rocgdb, amd-smi.
- [ ] Write both: library ↔ CUDA equivalent table and when to drop to CK vs Triton vs hipBLASLt; a profiling loop mirroring the Nsight workflow, counters, rocprof-compute roofline.
- [ ] Mermaid check; report pins.

### Task 4: AMD multi-GPU + ROCm case study
Files: `multi_gpu_on_amd_rccl_and_xgmi\…md`, `case_studies\port_an_llm_kernel_to_amd_mi300.md`.
- [ ] Fetch: RCCL docs, MI300X platform xGMI topology and bandwidth (state direction convention), rocSHMEM, peer access APIs.
- [ ] Write module and case study.
- [ ] Mermaid check; report pins.

### Task 5: Apple architecture + Metal compute
Files: `apple_silicon_gpu_architecture\…md`, `metal_compute_programming\…md` (18 Q&As).
- [ ] Fetch: Metal docs, MSL spec version, Metal 4 compute and tensor APIs, Apple-published M-series GPU facts, Dynamic Caching, SIMD-group functions, `simdgroup_matrix`, storage modes, threadgroup memory limit.
- [ ] Write both: Swift host snippets, MSL kernels, command-buffer lifecycle diagram, unified memory and zero-copy.
- [ ] Mermaid check; report pins.

### Task 6: MPS/MLX/PyTorch MPS + Metal profiling
Files: `metal_performance_shaders_mlx_and_pytorch_mps\…md`, `metal_profiling_and_debugging\…md`.
- [ ] Fetch: MPS, MPSGraph, MLX latest release and `mx.fast.metal_kernel`, PyTorch MPS backend docs, Xcode GPU tools (frame capture, Metal System Trace, shader cost graph, validation).
- [ ] Write both.
- [ ] Mermaid check; report pins.

### Task 7: Vulkan compute
Files: `vulkan_compute_fundamentals\…md`, `vulkan_compute_advanced_and_ecosystem\…md`.
- [ ] Fetch: Vulkan 1.4, `VK_KHR_cooperative_matrix`, subgroups, `VK_KHR_shader_integer_dot_product`, buffer device address, timeline semaphores, Slang, glslang/DXC, MoltenVK, llama.cpp Vulkan backend, IREE, ncnn, RenderDoc, RGP / Nsight Graphics.
- [ ] Write both: minimal C++ host dispatch, GLSL kernels, barrier and memory-model section.
- [ ] Mermaid check; report pins.

### Task 8: Apple + cross-vendor case studies
Files: `case_studies\run_llm_inference_on_apple_silicon.md`, `case_studies\ship_one_kernel_across_nvidia_amd_and_apple.md`.
- [ ] Fetch: MLX LM, Apple memory-bandwidth figures, llama.cpp Metal backend, Triton backend status.
- [ ] Write both.
- [ ] Mermaid check; report pins.

### Task 9: Integration (after Tasks 1-8)
Files: `cuda\CLAUDE.md`, `cuda\README.md`, `cuda\case_studies\case_studies.md`, `game\app.js`, root `README.md`, root `CLAUDE.md`, 10 existing modules (§4 table only), `gpu_portability_hip_sycl_and_beyond.md` (trim prose, keep every Q&A).
- [ ] `game\app.js`: `SECTION_LABELS.cuda = "GPGPU Programming"`; `STUDY_ORDER.cuda` adds phases 7-10, portability moved after the concept map; acronym map adds missing keys among `rocm, hip, msl, mlx, lds, mfma, spirv, xgmi, rccl, cdna, rdna`; `LANG_ALIAS` adds `hip, metal, msl, glsl, hlsl, slang` → `"cpp"`.
- [ ] README: H1, overview, module table, 10-phase path, study-paths block (new files; senior tags per spec), reference table rows, cross-ref map, §8 rows done; keep the generated Senior table consistent by hand.
- [ ] CLAUDE.md: scope, 36-module 10-phase list, Q&A floors, per-phase content rules, merged pin table, boundary rows.
- [ ] case_studies.md: all 5 sections.
- [ ] 10 existing modules: "On other vendors" table at end of §4.
- [ ] Root README/CLAUDE rows.
- [ ] Mermaid check every touched file. Commit locally on `feat/gpgpu-multivendor` with the Co-authored-by trailer. No push.
