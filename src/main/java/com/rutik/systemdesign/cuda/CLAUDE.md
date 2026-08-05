# CUDA / GPGPU Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/cuda/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check the NEXT UP pointer and per-file status before starting a new module.

This section teaches GPU programming from the **kernel author's** viewpoint. It deliberately **cross-references** the GPU material in `ml/`, `llm/`, and `devops/` instead of duplicating it — see the non-overlap boundary in `README.md` §2 and the GPU Non-Overlap Boundary below.

## Scope — unparked 2026-08-04; the factual audit is COMPLETE

Parked 2026-07-29, re-opened 2026-08-04, and audited the same day.

| Work | State |
|------|-------|
| Factual audit | **DONE — all 24 modules**, ~1,000 claims verified, ~145 corrections. Commits `312d0b4`, `ed1400a`, `e984f8c`, `60eed53` |
| `**Short:**` MCQ summaries | **DONE — 427 of 427**, run after the audit as the ordering rule requires. Commit `6ce35a6` |
| Case studies | **AUDITED.** All 11 files (6 case studies + 5 `cross_cutting/`) — first pass 2026-08-04 in `c563ef86`, `13f9f445`, `4c1d2ccd`; re-audited 2026-08-05, which found two gaps the first pass left: the FlashAttention lineage stopped at FA3 (FA4 shipped 2026-03) and PyTorch's `allow_tf32` booleans are deprecated after 2.9 |

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

- **NVIDIA's own two sources disagree on compute capability 12.x** — the Programming Guide
  says 24 resident blocks/SM and 100 KB shared memory; the Blackwell Tuning Guide says 32
  and 128 KB. `occupancy_and_launch_configuration` follows the Programming Guide. No side
  was picked.
- CC **10.3 and 11.0** are missing from the capability enumerations. Incomplete, not wrong;
  not filled in because guessing a hardware number is the failure mode this audit prevents.
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

**Two items left open on purpose, needing an owner decision:**
- NVIDIA's Programming Guide and Blackwell Tuning Guide **disagree on compute capability
  12.x** — 24 vs 32 resident blocks/SM, and 100 KB vs 128 KB shared memory per SM.
  `occupancy_and_launch_configuration` follows the Programming Guide. No side was picked.
- The capability enumerations in that module omit **CC 10.3 and 11.0**, which now exist in
  the Programming Guide table. Incomplete rather than wrong; not filled in because
  guessing a hardware number is the failure mode this audit exists to prevent.

One correction to record while it is fresh: this file has claimed the section missed the
repo-wide `Q: ` prefix sweep. All five audited modules already carry the prefix, so that
claim is at least partly stale — verify before scheduling any bulk relabel.

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

## Learning Paths (Full + Senior)

`README.md` documents the **Full Path** (all 24 modules = "6-Phase Learning Path") plus
one curated tier: **Senior** (16 modules). **This section has no Principal tier and needs
none** — no module declares one, `check_wiring()` skips a tier whose markers declare zero
modules, and adding a Principal heading with no members is a false alarm, not a gap.
Membership is declared ONCE per module, in a `<!-- study-paths -->` block in that module's
own page (`<module>.md`) naming the files each tier takes; listing a tier joins it, omitting the tier
opts out, and the module page (`<module>.md`) must always be listed. Order is never declared — it comes from
`STUDY_ORDER.cuda` in `game/app.js`, so a tier is an ordered subset by construction.
**There is no path array in `app.js` to edit**: `extract.py` walks the markers and emits
the gitignored `questions/paths.json`, which the game fetches at boot. The Senior table in
`README.md` sits between `<!-- study-path-table senior -->` / `<!-- /study-path-table -->`
and is **generated** — regenerate with `python3 game/extract.py --write-paths`; a
hand-edited or stale block fails `extract.py --strict` and the Pages deploy. The 6 case
studies carry no tier markers, so the Case Studies tab shows all of them with no Level
filter. The README also carries a Knowledge-Question Map and a 5-week Study Plan (prose;
no path impact).

Every module dir MUST also appear in `STUDY_ORDER.cuda` in `game/app.js` at its
learning-path position, or it sorts to the end of the Study browser.

---

## Case Studies — 6 Total

`case_studies/` — all use the 11-section principal template.
Reference: `../llm/case_studies/design_gpu_inference_platform.md`
Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).

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
