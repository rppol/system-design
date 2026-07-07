# CUDA / GPGPU Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/cuda/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check the NEXT UP pointer and per-file status before starting a new module.

This section teaches GPU programming from the **kernel author's** viewpoint. It deliberately **cross-references** the GPU material in `ml/`, `llm/`, and `devops/` instead of duplicating it — see the non-overlap boundary in `README.md` §2 and the GPU Non-Overlap Boundary below.

---

## Module List — 24 Modules (6 Phases)

NVIDIA CUDA is the default platform in worked examples; exactly one module (`gpu_portability_hip_sycl_and_beyond`) surveys cross-vendor alternatives. Code is dual CUDA C++ + Python (see Content Rules).

| Phase | Modules |
|-------|---------|
| 1 — GPU Foundations | gpu_computing_foundations, gpu_hardware_architecture, cuda_toolkit_and_compilation |
| 2 — Core CUDA Programming | cuda_programming_model_and_kernels, warps_and_simt_execution, cuda_memory_model_and_hierarchy, memory_management_and_data_transfer |
| 3 — Performance Engineering (interview core) | memory_coalescing_and_access_patterns, shared_memory_and_bank_conflicts, occupancy_and_launch_configuration, synchronization_and_atomics, parallel_patterns_reduction_scan_histogram, warp_level_primitives_and_cooperative_groups |
| 4 — Advanced Execution & Multi-GPU | streams_events_and_concurrency, cuda_graphs, multi_gpu_programming_and_nccl, dynamic_parallelism_and_advanced_kernels |
| 5 — Libraries, Tensor Cores & Ecosystem | tensor_cores_and_mixed_precision, cuda_math_and_dnn_libraries, python_gpu_ecosystem, triton_and_kernel_dsls |
| 6 — Profiling, Correctness & Portability | profiling_and_performance_analysis, debugging_correctness_and_numerics, gpu_portability_hip_sycl_and_beyond |

**Deep modules requiring 18 Q&As:** memory_coalescing_and_access_patterns, shared_memory_and_bank_conflicts, occupancy_and_launch_configuration, tensor_cores_and_mixed_precision, profiling_and_performance_analysis. All other modules: 15 Q&A floor.

---

## Learning Paths (Full + Interview-Specific)

`README.md` documents two routes: the **Full Path** (all 24 modules = "6-Phase Learning
Path") and a curated **Interview-Specific Path** (16 modules). The interview subset is a
**dual-source list** — it lives in both `README.md` ("## Learning Paths") and
`game/app.js` (`STUDY_PATHS.cuda.interview`, which drives the game's Study
Full/Interview toggle). **Change one, change the other** — same modules, same order.
Non-Q&A narrative only; no `extract.py` re-run needed. The README also carries a
Knowledge-Question Map and a 5-week Study Plan (interview-readiness prose; no toggle impact).

Every module dir MUST also appear in `STUDY_ORDER.cuda` in `game/app.js` at its
learning-path position, or it sorts to the end of the Study browser.

---

## Case Studies — 6 Total

`case_studies/` — all use the 11-section principal template.
Reference: `../llm/case_studies/design_gpu_inference_platform.md`
Learning-path index: `case_studies/README.md` (mandatory; update with every new case study).

optimize_matrix_multiplication_kernel, implement_high_performance_reduction, build_a_flash_attention_kernel, accelerate_2d_convolution_and_stencil, port_a_cpu_pipeline_to_gpu, optimize_llm_inference_kernels

---

## Cross-Cutting Shared Primitives — 5 Files

`case_studies/cross_cutting/`:

| File | When Relevant |
|------|--------------|
| `roofline_and_arithmetic_intensity.md` | Any case study reasoning about memory-bound vs compute-bound |
| `cuda_memory_hierarchy_reference.md` | Any kernel touching shared memory / global memory tuning |
| `cuda_error_handling_and_launch_config_patterns.md` | Every case study (the `CUDA_CHECK` macro + launch-config idiom) |
| `nsight_profiling_workflow.md` | Any case study with a profile-driven optimization loop |
| `numerical_precision_and_determinism.md` | Any case study with mixed precision or reproducibility concerns |

---

## Cross-Reference Map

| CUDA Module | See Also (other sections) |
|-------------|---------------------------|
| `gpu_hardware_architecture/` | `../../ml/gpu_and_hardware_optimization/` — training-time hardware use; `../../cs_fundamentals/computer_architecture_and_memory_hierarchy/` |
| `tensor_cores_and_mixed_precision/` | `../../llm/optimization_and_quantization/` — quantization; `../../ml/model_compression_and_efficiency/` |
| `multi_gpu_programming_and_nccl/` | `../../ml/distributed_training/` — parallelism strategy; `../../devops/ml_platform_and_gpu_infrastructure/` |
| `triton_and_kernel_dsls/` | `../../llm/inference_engines/`, `../../llm/vllm_deep_dive/` — where custom kernels ship |
| `profiling_and_performance_analysis/` | `../../llm/case_studies/design_gpu_inference_platform.md` — platform-level GPU serving |

---

## GPU Non-Overlap Boundary

GPUs appear in four sections at four altitudes. This section owns the **kernel** altitude only:

| Topic area | Owned by | This section adds |
|-----------|----------|-------------------|
| Multi-tenant GPU serving, KV-cache paging, fleet economics | `llm/case_studies/design_gpu_inference_platform.md` | The kernels underneath (GEMV/attention/quant-matmul) |
| Roofline for transformer inference cost; quantization | `llm/optimization_and_quantization/` | Roofline as a per-kernel optimization loop |
| Training-time hardware use, mixed-precision *training* recipes | `ml/gpu_and_hardware_optimization/` | Writing/optimizing the CUDA kernels themselves |
| Distributed-training parallelism strategy (ZeRO/FSDP) | `ml/distributed_training/` | NCCL/NVLink from the CUDA-programming viewpoint |
| GPU cluster operations (Operator, MIG on K8s) | `devops/ml_platform_and_gpu_infrastructure/` | On-device MIG/streams as a programming concern |

When a module would restate one of the above, link it and add only the kernel-level angle.

---

## Content Rules (CUDA-specific)

- **Dual-language code.** Show CUDA C++ and Python (CuPy / Numba / Triton / PyTorch) side-by-side where both teach the same concept (vector add, reduction, tiled GEMM, coalescing). Use **C++ alone** where the concept is inherently C++ (WMMA/`mma`, PTX/SASS, `__launch_bounds__`, cooperative-group templates). Use **Python alone** where inherently Python (Triton kernels, CuPy raw kernels, PyTorch extensions). Fence with ` ```cpp `/` ```cuda `, ` ```python `, ` ```ptx `.
- **Concrete numbers everywhere.** warp = 32; 128-byte coalesced transaction; 32 shared-memory banks (4 bytes each); 64K 32-bit registers/SM (256 KB); max 1024 threads/block; ~400-800 cycle global-memory latency; HBM3 ~3 TB/s (H100); TF32/BF16/FP8 Tensor-Core paths. No "a few"/"some".
- **Mandatory BROKEN→FIX** — at least one in §10 and one in §14. Canonical CUDA pairs: uncoalesced→coalesced access; bank-conflict→padded shared array; divergent branch→predicated/warp-aligned; missing `cudaGetLastError`→`CUDA_CHECK` macro; race→`__syncthreads`/atomic.
- **Diagrams — appeal-first (Mermaid preferred, ASCII for grids).** Use Mermaid for flows/lifecycles/pipelines (nvcc pipeline, stream overlap timeline, graph capture, kernel-launch lifecycle, roofline as xychart-beta). Use **ASCII grids** (fenced, no language tag) for the CUDA-native shapes where character alignment carries the meaning: memory-coalescing transaction maps, shared-memory bank-conflict grids, warp-divergence masks, thread-index grids, tiling layouts. Validate ASCII with `.claude/skills/visual-intuition-diagrams/diagram_tools.py check`. Run `/mermaid-diagrams` before writing any mermaid fence (One-Dark classDef, color-all-nodes-or-none). **No mermaid fences in this CLAUDE.md** — study files only.
- **No emojis.** `---` between every top-level section. Em-dash in §6 heading: `## 6. How It Works — Detailed Mechanics`.

---

## Adding a New CUDA Module

1. Create `<module_name>/README.md` — 14-section template; 15 Q&As minimum (18 for the deep modules listed above), ordered by interview frequency (gotchas first).
2. Follow the CUDA-specific content rules above (dual-language code, concrete numbers, BROKEN→FIX, diagram policy).
3. Update `README.md` §3 module table AND flip the file's status in the §8 build manifest.
4. Add the module dir to `STUDY_ORDER.cuda` in `game/app.js` at its learning-path position (and to `STUDY_PATHS.cuda.interview` if it belongs in the interview cut — keep in sync with README).
5. Update root `README.md` CUDA phase table and root `CLAUDE.md` CUDA module count.
6. Re-run `python3 game/extract.py`; confirm `questions/cuda.json` grows.

## Adding a New CUDA Case Study

1. Write `case_studies/<verb>_<name>.md` — 11-section principal template (Intuition + §1–11); 900–1100 lines; ≥4 cross_cutting refs; real kernel code in §4 with a broken→fix; 10+ Q&As in §11.
2. Update the section master `README.md` §8 Case Study file status.
3. **Update `case_studies/README.md`** — add to the correct phase group, update the Dependency Map, add an Interview Prep Shortcut row (same commit).
4. Case study Q&As are NOT extracted into the game (case_studies/ is excluded) — but the file is reachable in the reader via relative links.

---

## Diagrams — appeal-first

Mermaid is preferred for anything with a flow/lifecycle/pipeline topology; ASCII is retained
for constraint/value grids (coalescing, bank conflicts, warp masks, tiling) where alignment
is the message. The full decision table, One-Dark palette, and gotchas live in the
`/mermaid-diagrams` skill; the ASCII archetype catalog and `diagram_tools.py` validator live
in root `CLAUDE.md` → "Visual Intuition Diagrams". This section is expected to use ASCII grids
more heavily than any other because so many CUDA concepts are literally X×Y access patterns.
