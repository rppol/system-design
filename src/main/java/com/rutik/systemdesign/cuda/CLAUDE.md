# CUDA / GPGPU Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/cuda/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check the NEXT UP pointer and per-file status before starting a new module.

This section teaches GPU programming from the **kernel author's** viewpoint. Its scope is **GPGPU programming** (display name "GPGPU Programming"; the folder slug stays `cuda/` because `qid()` hashes `module|question` and a rename would re-key every question and wipe review state): NVIDIA CUDA is the core path (Phases 1-6), and AMD ROCm/HIP (Phase 7), Apple Silicon and Metal (Phase 8) and Vulkan compute (Phase 9) each get their own phase at the same altitude, joined by a cross-vendor phase (Phase 10). Intel oneAPI/SYCL and WebGPU are not separate phases; they stay inside `gpu_portability_hip_sycl_and_beyond`. It deliberately **cross-references** the GPU material in `ml/`, `llm/`, `mlops/` and `devops/` instead of duplicating it — see the non-overlap boundary in `README.md` §2 and the GPU Non-Overlap Boundary below.

## Scope — unparked 2026-08-04; the factual audit is COMPLETE

Parked 2026-07-29, re-opened 2026-08-04, and audited the same day.

| Work | State |
|------|-------|
| Factual audit | **DONE — all 24 modules**, ~1,000 claims verified, ~145 corrections. Commits `312d0b4`, `ed1400a`, `e984f8c`, `60eed53` |
| `**Short:**` MCQ summaries | **DONE — 544 of 544** (477 in the 24 module pages + 67 in the 4 deep-dive sub-files; counted 2026-11-25 as `**Q`/`**Short:**` lines inside the interview sections). The first pass was 427 of 427, run after the audit as the ordering rule requires, in commit `6ce35a6` |
| Case studies | **AUDITED.** All 13 files (8 case studies + 5 `cross_cutting/`) — first pass 2026-08-04 in `c563ef86`, `13f9f445`, `4c1d2ccd`; re-audited 2026-08-05. Both gaps that pass found are now **CLOSED**: the FlashAttention lineage reaches FA4 (`build_a_flash_attention_kernel.md:639` — CuTe DSL, 2-CTA MMA, Blackwell tensor memory, warpgroup ping-pong), and PyTorch's deprecated `allow_tf32` booleans are gone section-wide, replaced by `fp32_precision` (2026-08-25; the fix had landed in the case studies and never propagated to `debugging_correctness_and_numerics` or `tensor_cores_and_mixed_precision`). Do not re-open either |
| GPGPU multi-vendor expansion | **DONE 2026-11-25.** 12 new modules (Phases 7-10), 3 case studies and `cross_cutting/vendor_terminology_and_numbers_reference.md`, every version and hardware figure fetched from a primary source that day (pins below). Re-audited the same day: about 25 claims checked against AMD, Apple, Khronos, LLVM and GitHub sources, all matched; the residual fixes (RDNA LDS is per WGP, MI300X's 8 listed Infinity Fabric links, ROCm 10.2.0 in `gpu_portability`, Triton 3.9.0, CUTLASS 4.8.1, CCCL 3.5 scan determinism, the CUDA 13.0 vs 13.1 driver-unbundling disagreement) were applied in the integration pass. 10 CUDA modules gained an "On other vendors" table at the end of §4 (no §12 edits there); the only existing §12 answer changed was the `triton_and_kernel_dsls` tensor-descriptor answer, because Triton 3.9.0 removed block pointers |

### What the audit found here, and why it is not what you would guess

The stale-version findings were real but ordinary. **The expensive defects were explanations
that had quietly stopped matching the hardware, while the code they justified stayed
correct** — which makes them invisible to a reader who is checking whether the code works:

- `warp_level_primitives` justified the `_sync` shuffle idioms with "the lanes never leave
  lockstep". Independent Thread Scheduling ended that in 2017; NVIDIA's wording is that
  `_sync` "first synchronize[s] the threads if they are not already synchronized". The
  recipes were right, the mental model was pre-Volta, and a reader who learned the model
  writes a race. The same file pushed `__activemask()` in six places against NVIDIA's
  explicit "Don't just use `__activemask()`".
- `tensor_cores` taught that Tensor Cores engage only on M/N/K multiples of 8 with a silent
  CUDA-core fallback otherwise. No such restriction has existed since cuBLAS 11.0 outside
  FP8; what survives is a 16-byte-alignment PERFORMANCE cliff. The padding advice was right
  for the wrong reason.
- `cuda_toolkit_and_compilation`'s headline gotcha was inverted: `-arch=sm_80` DOES embed
  PTX (Shorthand 2 expands to `code=sm_80,compute_80`). The trap belongs to explicit
  `-gencode ...,code=sm_XX`. It warned readers off the safe form.
- `multi_gpu_programming_and_nccl` compared NVLink bidirectionally against PCIe
  per-direction — two conventions in one comparison, overstating the gap by exactly 2x
  across a table, an xychart, two ASCII diagrams and two Q&As.
- `gpu_portability` cited "WebCUDA", an NVIDIA proposal that does not exist. The real
  history is Khronos WebCL and WebGL 2.0 Compute.
- `dynamic_parallelism` described `cudaLimitDevRuntimeSyncDepth` as the nesting-depth knob
  in eight places (it is not, does not exist under CDP2, and errors on cc >= 9.0) — and the
  host code called it.

**Two files came back with ZERO corrections** after ~120 assertions each
(`parallel_patterns_reduction_scan_histogram`, `profiling_and_performance_analysis`). That
is a result. Do not re-audit them looking for something.

### Open, needing an owner decision

- **NVIDIA's own two sources still disagree on compute capability 12.x** — the CUDA Programming
  Guide v13.5 compute-capability tables (last updated 2026-10-31) say 24 resident blocks/SM and
  100 KB shared memory/SM; the Blackwell Tuning Guide (last updated 2026-11-02) says 32 and
  128 KB for 12.0. Both say 48 warps and 99 KB per block. `occupancy_and_launch_configuration`
  §4.1 and `blackwell_and_rubin_architecture` §4.3 now state both sources with dates and keep
  the Programming Guide values in their arithmetic. No side was picked.
- CC **11.0** (Jetson Thor) is now **filled** in `occupancy_and_launch_configuration` from the
  Programming Guide table (2026-11-25): 24 blocks, 48 warps (1536 threads) and 228 KB shared
  memory per SM, 227 KB per block — data-center shared memory with consumer-tier warp limits.
- Unverified and deliberately left: the 3-8 us device-side child-launch figure (no NVIDIA
  source, and §5/§14 arithmetic depend on it) and a K=4095 cuBLAS sweep whose 8x drop
  likely overstates reality — now labelled illustrative with a re-measure instruction.
- **The `Q: ` prefix claim in this file was stale and is now settled:** cuda is at 100%.
  Measured 2026-08-04 across the whole repo; no relabel is needed here.

**THE TRAP IS CLEARED (2026-08-04).** The section was in three states; it is now in two.

| Modules | State | Commit |
|---------|-------|--------|
| `cuda_memory_model_and_hierarchy`, `cuda_programming_model_and_kernels`, `debugging_correctness_and_numerics`, `gpu_computing_foundations`, `gpu_hardware_architecture` | **Audited.** 175 claims checked, verdicts recorded | `b223d21` |
| `memory_coalescing_and_access_patterns`, `memory_management_and_data_transfer`, `occupancy_and_launch_configuration`, `parallel_patterns_reduction_scan_histogram`, `profiling_and_performance_analysis` | **Audited.** Re-done from §1 treating `2fca64f` as unverified; ~360 assertions, 12 corrections, verdict recorded | `312d0b4` |
| everything else | Never audited | — |

**Why the re-audit was necessary, since it will look like duplicated work in `git log`.**
`2fca64f` was an interrupted partial batch: real corrections applied, then stopped
mid-pass with no verdict record, so nobody could tell which claims had been checked. Its
visible legacy — nvprof and the legacy Visual Profiler gone from all five files — was
genuine but incomplete: `memory_coalescing` still had six sites instructing the reader to
find "Global Load/Store Efficiency" in Nsight Compute, which is an nvprof-era metric name
that does not exist in ncu. A pass that looks finished because its most visible edit
landed is exactly what the re-audit was for.

---

## Module List — 36 Modules (10 Phases)

NVIDIA CUDA is the default platform in Phases 1-6, which every later phase assumes. Phases 7-9 give AMD ROCm/HIP, Apple Metal and Vulkan compute their own modules; Phase 10 holds the cross-vendor concept map and the portability survey (`gpu_portability_hip_sycl_and_beyond`, moved there from Phase 6 on 2026-11-25 with its ids unchanged; its HIP and Metal subsections are a paragraph plus links, and its Q&As are kept, never deleted). Code languages per phase are in Content Rules.

| Phase | Modules |
|-------|---------|
| 1 — GPU Foundations | gpu_computing_foundations, gpu_hardware_architecture, cuda_toolkit_and_compilation |
| 2 — Core CUDA Programming | cuda_programming_model_and_kernels, warps_and_simt_execution, cuda_memory_model_and_hierarchy, memory_management_and_data_transfer |
| 3 — Performance Engineering (interview core) | memory_coalescing_and_access_patterns, shared_memory_and_bank_conflicts, occupancy_and_launch_configuration, synchronization_and_atomics, parallel_patterns_reduction_scan_histogram, warp_level_primitives_and_cooperative_groups |
| 4 — Advanced Execution & Multi-GPU | streams_events_and_concurrency, cuda_graphs, multi_gpu_programming_and_nccl, dynamic_parallelism_and_advanced_kernels |
| 5 — Libraries, Tensor Cores & Ecosystem | tensor_cores_and_mixed_precision, cuda_math_and_dnn_libraries, python_gpu_ecosystem, triton_and_kernel_dsls |
| 6 — Profiling & Correctness | profiling_and_performance_analysis, debugging_correctness_and_numerics |
| 7 — AMD ROCm & HIP | amd_gpu_architecture_cdna_and_rdna, hip_programming_and_porting, rocm_libraries_and_kernel_dsls, rocm_profiling_and_debugging, multi_gpu_on_amd_rccl_and_xgmi |
| 8 — Apple Silicon & Metal | apple_silicon_gpu_architecture, metal_compute_programming, metal_performance_shaders_mlx_and_pytorch_mps, metal_profiling_and_debugging |
| 9 — Vulkan Compute | vulkan_compute_fundamentals, vulkan_compute_advanced_and_ecosystem |
| 10 — Cross-Vendor | cross_vendor_gpu_concept_map, gpu_portability_hip_sycl_and_beyond |

**Deep modules requiring 18 Q&As:** memory_coalescing_and_access_patterns, shared_memory_and_bank_conflicts, occupancy_and_launch_configuration, tensor_cores_and_mixed_precision, profiling_and_performance_analysis, hip_programming_and_porting, metal_compute_programming, cross_vendor_gpu_concept_map. All other modules: 15 Q&A floor. In the three multi-vendor deep modules, keep first sentences lexically distinct: warp, wavefront, SIMD-group and subgroup answers cluster, and near-identical openers feed each other's distractor pools.

### Deep-dive sub-files — 4 files

Each sub-file groups under its parent module (no `STUDY_ORDER` entry) and is listed in the
`README.md` `<!-- study-paths -->` block. All four had their first factual pass on 2026-11-25
against NVIDIA primary sources (CUDA Programming Guide and driver/runtime API references, CUDA
and NCCL release notes, NVIDIA product pages and blogs, the CUTLASS and DeepEP repositories).

| Sub-file | Parent module | Covers | Q&As / Short | Fact pass 2026-11-25 |
|----------|---------------|--------|--------------|----------------------|
| `cuda_graphs/conditional_nodes_and_device_graph_launch.md` | `cuda_graphs` | IF/WHILE/SWITCH conditional nodes; device graph launch (fire-and-forget, tail, sibling) | 16 / 16 | IF/WHILE date from CUDA 12.3, not 12.4; conditional bodies cannot use device graph launch and device graphs cannot hold conditional nodes, so the two are alternatives, not layers |
| `gpu_hardware_architecture/blackwell_and_rubin_architecture.md` | `gpu_hardware_architecture` | Blackwell two-die design and compute-capability map (10.0/10.3/11.0/12.x); Rubin 10.7 | 16 / 16 | Rubin is shipping and GA in CUDA 13.5; NVLink 6 is 3 TB/s per GPU on the product page (3.6 TB/s at launch); L2 has been split into two partitions since A100, so the "single-pool H100" contrast was cut |
| `multi_gpu_programming_and_nccl/device_initiated_communication.md` | `multi_gpu_programming_and_nccl` | NCCL device API (LSA, Multimem, GIN), symmetric windows, Copy Engine collectives, NVSHMEM/IBGDA, DeepEP, NCCL EP | 19 / 19 | Device-API code rewritten to NCCL's documented signatures; CE collectives need `NCCL_CTA_POLICY_ZERO`; DeepEP V2.5 dropped NVSHMEM and needs NCCL 2.32.3+ |
| `streams_events_and_concurrency/green_contexts_and_sm_partitioning.md` | `streams_events_and_concurrency` | Green contexts (driver and CUDA 13.1+ runtime API), SM split granularity, MPS/MIG comparison | 16 / 16 | `cuGreenCtxStreamCreate` requires `CU_STREAM_NON_BLOCKING`; disjoint SMs do not guarantee concurrency; two documented cases add SMs |

---

## Learning Paths (Full + Senior)

`README.md` documents the **Full Path** (all 36 modules = "10-Phase Learning Path") plus
one curated tier: **Senior** (23 modules: the 17 CUDA modules plus `amd_gpu_architecture_cdna_and_rdna`,
`hip_programming_and_porting`, `apple_silicon_gpu_architecture`, `metal_compute_programming`,
`vulkan_compute_fundamentals` and `cross_vendor_gpu_concept_map`). **This section has no Principal tier and needs
none** — no module declares one, `check_wiring()` skips a tier whose markers declare zero
modules, and adding a Principal heading with no members is a false alarm, not a gap.
Membership is declared ONCE per section, in the `<!-- study-paths -->` block in this section's
`README.md`, naming the files each tier takes; listing a tier joins it, omitting the tier
opts out, and the module page (`<module>.md`) must always be listed. Order is never declared — it comes from
`STUDY_ORDER.cuda` in `game/app.js`, so a tier is an ordered subset by construction.
**There is no path array in `app.js` to edit**: `extract.py` walks the markers and emits
the gitignored `questions/paths.json`, which the game fetches at boot. The Senior table in
`README.md` sits between `<!-- study-path-table senior -->` / `<!-- /study-path-table -->`
and is **generated** — regenerate with `python3 game/extract.py --write-paths`; a
hand-edited or stale block fails `extract.py --strict` and the Pages deploy. The 11 case
studies are all tagged `-`, so the Case Studies tab shows all of them with no Level
filter. The README also carries a Knowledge-Question Map and a 5-week Study Plan (prose;
no path impact).

Every module dir MUST also appear in `STUDY_ORDER.cuda` in `game/app.js` at its
learning-path position, or it sorts to the end of the Study browser.

---

## Case Studies — 11 Total

`case_studies/` — all use the 11-section principal template.
Reference: `../llm/case_studies/design_gpu_inference_platform.md`
Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).

CUDA-native (8): optimize_matrix_multiplication_kernel, implement_high_performance_reduction, build_a_flash_attention_kernel, accelerate_2d_convolution_and_stencil, port_a_cpu_pipeline_to_gpu, optimize_llm_inference_kernels, write_a_blackwell_gemm_with_cute_dsl, optimize_moe_expert_parallel_dispatch

Multi-vendor (3, added 2026-11-25, Phase E in `case_studies.md`): port_an_llm_kernel_to_amd_mi300, run_llm_inference_on_apple_silicon, ship_one_kernel_across_nvidia_amd_and_apple

---

## Cross-Cutting Shared Primitives — 6 Files

`case_studies/cross_cutting/`:

| File | When Relevant |
|------|--------------|
| `roofline_and_arithmetic_intensity.md` | Any case study reasoning about memory-bound vs compute-bound |
| `cuda_memory_hierarchy_reference.md` | Any kernel touching shared memory / global memory tuning |
| `cuda_error_handling_and_launch_config_patterns.md` | Every case study (the `CUDA_CHECK` macro + launch-config idiom) |
| `nsight_profiling_workflow.md` | Any case study with a profile-driven optimization loop |
| `numerical_precision_and_determinism.md` | Any case study with mixed precision or reproducibility concerns |
| `vendor_terminology_and_numbers_reference.md` | Any case study that runs a kernel on more than one vendor (the term, API and numbers cards; the long form is `cross_vendor_gpu_concept_map`) |

---

## Cross-Reference Map

| CUDA Module | See Also (other sections) |
|-------------|---------------------------|
| `gpu_hardware_architecture/` | `../../ml/gpu_and_hardware_optimization/` — training-time hardware use; `../../cs_fundamentals/computer_architecture_and_memory_hierarchy/` |
| `tensor_cores_and_mixed_precision/` | `../../llm/optimization_and_quantization/` — quantization; `../../ml/model_compression_and_efficiency/` |
| `multi_gpu_programming_and_nccl/` | `../../ml/distributed_training/` — parallelism strategy; `../../devops/ml_platform_and_gpu_infrastructure/` |
| `triton_and_kernel_dsls/` | `../../llm/inference_engines/`, `../../llm/vllm_deep_dive/` — where custom kernels ship |
| `profiling_and_performance_analysis/` | `../../llm/case_studies/design_gpu_inference_platform.md` — platform-level GPU serving |
| `amd_gpu_architecture_cdna_and_rdna/`, `multi_gpu_on_amd_rccl_and_xgmi/` | `../../mlops/gpu_cluster_scheduling_and_quotas/` — AMD GPU Operator and fleet scheduling; `../../ml/distributed_training/` |
| `metal_performance_shaders_mlx_and_pytorch_mps/`, `vulkan_compute_advanced_and_ecosystem/` | `../../llm/small_language_models_and_edge_ai/` — MLX, llama.cpp and Core ML as on-device runtime choices |

---

## GPU Non-Overlap Boundary

GPUs appear in five sections at five altitudes. This section owns the **kernel** altitude only, for every vendor:

| Topic area | Owned by | This section adds |
|-----------|----------|-------------------|
| Multi-tenant GPU serving, KV-cache paging, fleet economics | `llm/case_studies/design_gpu_inference_platform.md` | The kernels underneath (GEMV/attention/quant-matmul) |
| Roofline for transformer inference cost; quantization | `llm/optimization_and_quantization/` | Roofline as a per-kernel optimization loop |
| Training-time hardware use, mixed-precision *training* recipes | `ml/gpu_and_hardware_optimization/` | Writing/optimizing the CUDA kernels themselves |
| Distributed-training parallelism strategy (ZeRO/FSDP) | `ml/distributed_training/` | NCCL/NVLink and RCCL/xGMI from the kernel-programming viewpoint |
| GPU cluster operations (Operator, MIG on K8s) | `devops/ml_platform_and_gpu_infrastructure/` | On-device MIG/streams as a programming concern |
| AMD GPU Operator, AMD device plugin and DRA driver, GPU fleet scheduling on Kubernetes | `mlops/gpu_cluster_scheduling_and_quotas/` (stays in `mlops/`) | ROCm, HIP, rocprof and RCCL as programming tools (Phase 7) |
| MLX, llama.cpp and Core ML as an edge / on-device runtime *choice* | `llm/small_language_models_and_edge_ai/` (stays in `llm/`) | MLX arrays, `mx.fast.metal_kernel`, MSL and Vulkan kernels as programming tools (Phases 8-9) |

When a module would restate one of the above, link it and add only the kernel-level angle.

---

## Content Rules (CUDA-specific)

- **Dual-language code.** Show CUDA C++ and Python (CuPy / Numba / Triton / PyTorch) side-by-side where both teach the same concept (vector add, reduction, tiled GEMM, coalescing). Use **C++ alone** where the concept is inherently C++ (WMMA/`mma`, PTX/SASS, `__launch_bounds__`, cooperative-group templates). Use **Python alone** where inherently Python (Triton kernels, CuPy raw kernels, PyTorch extensions). Fence with ` ```cpp `/` ```cuda `, ` ```python `, ` ```ptx `.
- **Code languages per phase.** Phases 1-6: CUDA C++ + Python as above. Phase 7 (AMD): HIP C++ (fence ` ```cpp `) + Python (PyTorch on ROCm, Triton). Phase 8 (Apple): MSL (fence ` ```metal `) + Swift (` ```swift `) or Python (MLX); Swift fences render unhighlighted in the reader, so keep Swift snippets short. Phase 9 (Vulkan): GLSL (` ```glsl `) or Slang (` ```slang `), HLSL (` ```hlsl `) where DXC is the point, + a C++ host. Phase 10: one kernel in all four (CUDA, HIP, MSL, GLSL) where the comparison is the lesson. `game/app.js` `LANG_ALIAS` maps `hip`, `metal`, `msl`, `glsl`, `hlsl` and `slang` to the C++ highlighter.
- **Vendor facts are pinned and dated.** Every new vendor module carries a `> **Version anchor (fetched YYYY-MM-DD):** ...` blockquote under the H1 stating only facts fetched from a primary source (AMD ROCm docs and release notes, AMD CDNA white papers and product pages, Apple Developer docs, Metal Feature Set Tables, Apple Newsroom, Khronos registry and spec, GitHub releases). An unverifiable figure is cut, not hedged. Quote link bandwidth per direction or say "bidirectional" (AMD's 128 GB/s per xGMI link and NVIDIA's NVLink totals are both bidirectional sums).
- **Concrete numbers everywhere.** warp = 32; 128-byte coalesced transaction; 32 shared-memory banks (4 bytes each); 64K 32-bit registers/SM (256 KB); max 1024 threads/block; ~400-800 cycle global-memory latency; HBM3 ~3 TB/s (H100); TF32/BF16/FP8 Tensor-Core paths. No "a few"/"some".
- **Mandatory BROKEN→FIX** — at least one in §10 and one in §14. Canonical CUDA pairs: uncoalesced→coalesced access; bank-conflict→padded shared array; divergent branch→predicated/warp-aligned; missing `cudaGetLastError`→`CUDA_CHECK` macro; race→`__syncthreads`/atomic.
- **Diagrams — appeal-first (Mermaid preferred, ASCII for grids).** Use Mermaid for flows/lifecycles/pipelines (nvcc pipeline, stream overlap timeline, graph capture, kernel-launch lifecycle, roofline as xychart-beta). Use **ASCII grids** (fenced, no language tag) for the CUDA-native shapes where character alignment carries the meaning: memory-coalescing transaction maps, shared-memory bank-conflict grids, warp-divergence masks, thread-index grids, tiling layouts. Validate ASCII with `.claude/skills/visual-intuition-diagrams/diagram_tools.py check`. Run `/mermaid-diagrams` before writing any mermaid fence (One-Dark classDef, color-all-nodes-or-none). **No mermaid fences in this CLAUDE.md** — study files only.
- **No emojis.** `---` between every top-level section. Em-dash in §6 heading: `## 6. How It Works — Detailed Mechanics`.

---

## Version Pins — merged, dated 2026-11-25

The single list of what the section treats as current. Each row was read from the named primary
source on the date shown; the module that carries the fact is the one to update when it moves.
Re-check before quoting any Rubin, ROCm or Apple figure in new work.

| Stack | Pin (as of 2026-11-25) | Where it lives |
|-------|------------------------|----------------|
| CUDA Toolkit | 13.5 (driver branch R620, PTX ISA 9.5, CCCL 3.5 with `cub::DeviceScan` run-to-run determinism); CUDA Programming Guide v13.5 (updated 2026-10-31) | `cuda_toolkit_and_compilation`, `parallel_patterns_reduction_scan_histogram`, `cross_vendor_gpu_concept_map` |
| Driver unbundling | Linux from CUDA 13.4; Windows: the 13.0 release notes say 13.0, the current 13.5 notes say 13.1 — both are stated, no side picked | `cuda_toolkit_and_compilation` §10 |
| Rubin | cc 10.7 (`sm_107`), developer preview in CUDA 13.4, GA in 13.5; 288 GB HBM4 at 19.2 TB/s, NVLink 6 at 3 TB/s per GPU, shipping (NVIDIA Vera Rubin NVL72 page) | `blackwell_and_rubin_architecture`, `gpu_hardware_architecture`, `README.md` §5 |
| CUTLASS | 4.8.1 stable (2026-10-21); 4.9.0 dev (2026-11-23) adds Rubin low-latency features; CuTe DSL labeled public beta in the 4.8.0 notes | `triton_and_kernel_dsls`, `write_a_blackwell_gemm_with_cute_dsl` |
| Triton | 3.9.0 (2026-10-27): block pointers removed, E8M0 decoding changed, `tl.atomic_load`/`atomic_store`/`atomic_poll`; AMD gfx10 targets dropped | `triton_and_kernel_dsls`, `rocm_libraries_and_kernel_dsls`, `ship_one_kernel_across_nvidia_amd_and_apple` |
| NCCL | 2.32.3 cited (2.32.7-1 patch tagged 2026-11-24) | `multi_gpu_programming_and_nccl` |
| ROCm | ROCm Core SDK 10.2.0 (2026-11-18), HIP 7.17.0; legacy stream installs to `/opt/rocm-7.2`. Every cuda file uses this version | Phase 7 modules, `gpu_portability_hip_sycl_and_beyond`, `port_an_llm_kernel_to_amd_mi300` |
| HIPIFY | CUDA up to 13.2.0 with LLVM 23.1.x (Clang 23.1.5 recommended) | `hip_programming_and_porting` |
| ROCm libraries | hipBLASLt 1.5.0, rocBLAS 5.8.0, hipBLAS 3.8.0, MIOpen 3.6.2, Composable Kernel 1.3.0, rocWMMA 2.2.2, rocPRIM 4.8.0, hipCUB 4.7.0; AITER v0.1.30 (2026-11-22); PyTorch 2.15.0 validated | `rocm_libraries_and_kernel_dsls` |
| ROCm tools | ROCprofiler-SDK 1.4.2 (`rocprofv3`), ROCm Compute Profiler 3.10.0, ROCm Systems Profiler 1.10.0 (`rocsys`), ROCgdb 16.3, AMD SMI 27.2.0 | `rocm_profiling_and_debugging` |
| AMD multi-GPU | RCCL 2.32.3, rocSHMEM 3.8.0; MI300X: 8 Infinity Fabric links listed, 7 to peers at 128 GB/s bidirectional each (64 GB/s per direction), 896 GB/s aggregate; MI355X 144 GB/s per link | `multi_gpu_on_amd_rccl_and_xgmi` |
| AMD hardware | MI300X `gfx942` 304 CUs, 64 KiB LDS/CU, 192 GB HBM3 at 5.3 TB/s; MI355X `gfx950` 256 CUs, 160 KiB LDS/CU, 288 GB HBM3E at 8 TB/s; RDNA 4 `gfx1201`: 128 KiB LDS per WGP (two CUs), 64 KiB max per workgroup | `amd_gpu_architecture_cdna_and_rdna` |
| Metal | MSL 4.1 (spec dated 2026-06-04); Metal Feature Set Tables dated 2026-10-23, families Apple7 (A14, M1) to Apple11 (M6); Metal 4 needs Apple7+ and OS 26; 32 KB threadgroup memory, SIMD width 32 | `metal_compute_programming`, `apple_silicon_gpu_architecture` |
| Apple hardware | M5 153 GB/s; M5 Max up to 40 GPU cores, 614 GB/s; M6 170 GB/s; M5 Ultra up to 80 GPU cores, 1.2 TB/s (Apple Newsroom) | `apple_silicon_gpu_architecture` |
| Apple ML stack | MLX v0.32.5 (2026-11-25); PyTorch 2.15.0 MPS (macOS 15+, no `float64`); coremltools 9.0; Xcode 27.3 | `metal_performance_shaders_mlx_and_pytorch_mps`, `metal_profiling_and_debugging` |
| Vulkan | Vulkan 1.4 (2024-12-03), spec tag `v1.4.369` (2026-11-20), SDK 1.4.363.1; `VK_KHR_cooperative_matrix` rev 2, required by Roadmap 2026 (which raises `maxComputeSharedMemorySize` to 32768 B) | `vulkan_compute_fundamentals`, `vulkan_compute_advanced_and_ecosystem` |
| Vulkan toolchain and ecosystem | glslang 16.6.0, DXC v1.9.2609, Slang v2026.22.1; llama.cpp b12722, IREE v3.14.0, ncnn 20261020, MoltenVK v1.4.2, RenderDoc v1.47, Radeon GPU Profiler v2.7.1 | `vulkan_compute_advanced_and_ecosystem` |

---

## Adding a New CUDA Module

1. Create `<module_name>/<module_name>.md` — 14-section template; 15 Q&As minimum (18 for the deep modules listed above), ordered by interview frequency (gotchas first). **Prefix every §12 question with `Q: ` inside the bold** (`**Q: <question>?**`) — repo-wide convention (root `CLAUDE.md` → Interview Q&A Rules); `extract.py` strips the label for the MCQ bank.
2. Follow the CUDA-specific content rules above (dual-language code, concrete numbers, BROKEN→FIX, diagram policy).
3. Update `README.md` §3 module table AND flip the file's status in the §8 build manifest.
4. Add the module dir to `STUDY_ORDER.cuda` in `game/app.js` at its learning-path position. Add the module and EVERY file it owns to this section's `README.md` `<!-- study-paths -->` block, tagging each `senior` or `-` (the module page must carry every tier the module is in; this section has no Principal tier). **Never put a block in the module page — content files hold only content**, then run `python3 game/extract.py --write-paths` to regenerate `README.md`'s Senior table. Never hand-edit that table; a stale block fails `--strict`.
5. Update root `README.md` CUDA phase table and root `CLAUDE.md` CUDA module count.
6. Re-run `python3 game/extract.py`; confirm `questions/cuda.json` grows.

## Adding a New CUDA Case Study

1. Write `case_studies/<verb>_<name>.md` — 11-section principal template (Intuition + §1–11); 900–1100 lines; ≥4 cross_cutting refs; real kernel code in §4 with a broken→fix; 10+ Q&As in §11.
2. Update the section master `README.md` §8 Case Study file status.
3. **Update `case_studies/case_studies.md`** — add to the correct phase group, update the Dependency Map, add an Interview Prep Shortcut row (same commit).
4. Case study Q&As are NOT extracted into the game (case_studies/ is excluded) — but the file is reachable in the reader via relative links.

---

## Diagrams — appeal-first

Mermaid is preferred for anything with a flow/lifecycle/pipeline topology; ASCII is retained
for constraint/value grids (coalescing, bank conflicts, warp masks, tiling) where alignment
is the message. The full decision table, One-Dark palette, and gotchas live in the
`/mermaid-diagrams` skill; the ASCII archetype catalog and `diagram_tools.py` validator live
in root `CLAUDE.md` → "Visual Intuition Diagrams". This section is expected to use ASCII grids
more heavily than any other because so many CUDA concepts are literally X×Y access patterns.

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY
file that module owns (the module page AND every deep-dive sub-file), and EVERY case
study, each tagged with the tiers it belongs to — `-` means Full path only. Reading that
one block tells you every file in the section and which paths it is on.

**A content file carries NO structural metadata.** A module page or a deep-dive sub-file
holds the content of its topic and nothing else — no `<!-- study-paths -->` block, no tier
declaration, no path membership. That metadata used to live in each module page; it was
moved here so there is one place to look and one place to change.

**Adding a file? Add its line to the section README's block in the same commit.** A file on
disk that is missing from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. That check exists
because the old failure was silent: a new sub-file was invisible to the curated paths, or
silently dragged into every tier its parent was in, with a green build either way.

Order is never declared in the block: it comes from `STUDY_ORDER` in `game/app.js`. The
tier TABLES further down the README are GENERATED from the block by
`python3 game/extract.py --write-paths` — never hand-edit them.
