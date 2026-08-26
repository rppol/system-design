# CUDA Audit — 2026-08-25

## 0. Snapshot

The section is a genuinely strong, structurally clean **Volta-through-Ampere kernel-authoring
course with a Hopper/Blackwell veneer**. Every one of the 24 modules carries the full
14-section template, clears the Q&A floor, has BROKEN→FIX pairs, real diagrams and dense
concrete numbers; the factual audit that closed on 2026-08-04 left the *stated facts* in
good shape — architecture tables name Blackwell CC 10.0/10.3/12.0, `tcgen05`, TMA and FP4,
and `tensor_cores` already carries PyTorch's `fp32_precision` API. Its real strength is the
performance-engineering spine (Phase 3): coalescing, bank conflicts, occupancy, warp
primitives and profiling are taught at a depth most published material does not reach, and
`profiling_and_performance_analysis` teaches an actual Profile→Hypothesize→Fix→Re-measure
loop rather than a tool inventory.

**The single biggest weakness is that the modern asynchronous kernel is missing entirely.**
Not stale — *absent*. Grep the whole section and you get **zero** hits for `mbarrier`,
`cuda::pipeline`, `cuda::barrier`, `cp.async.bulk`, `DSMEM`, `wgmma`, `cuTile`, `Tile IR`,
`torch.library`, `green context`, `CUPTI`, `NVFP4` (outside one cross-cutting file), and
`block-scaled`. `tcgen05` appears **once**, in a table row. `TMEM` appears **once**.
"thread block cluster" appears as a feature-table entry and a `cg::cluster_group` line but
is never programmed. TMA is the sharpest example of the pattern: it is *named* in 6 files
(architecture tables, the portability module's "what you give up" argument, a Triton
`make_tensor_descriptor` Q&A, the FlashAttention case study's prose) and **programmed in
none** — no `CUtensorMap`, no `cudaCreateTensorMap`, no `cp.async.bulk.tensor` anywhere in
the section. The reader is told repeatedly that TMA is why Hopper kernels are fast and is
never shown one. `tensor_cores_and_mixed_precision` — the module that owns this — spends
§6.1–6.3 on the `nvcuda::wmma` fragment API, an interface that has no FP8 type on any
architecture and that no peak kernel has used since Ampere; on SM100 the instruction it
models does not exist at all. The result is a section that teaches you to *read* a 2019
kernel excellently and to *write* a 2026 one not at all. Everything a 2026 kernel author
actually composes — TMA descriptor + `mbarrier` + warp-specialized producer/consumer
warpgroups + `tcgen05` MMA into Tensor Memory — has no home here.

**And the section already knows this, one layer up.** This is the finding that reframes
everything below. `case_studies/build_a_flash_attention_kernel.md:639` describes
FlashAttention-4 in real technical detail — written in CuTe-DSL, Blackwell tensor memory
for backward-pass intermediates, 2-CTA MMA mode, three specialized warpgroups ping-ponging
query tiles, software `exp` emulation across `MUFU.EX2` — and `:600` explains
FA3's producer/consumer TMA warp specialization with a sequence diagram. The **case-study
layer is current to March 2026.** The **module layer that is supposed to prepare a reader
for it** teaches `nvcuda::wmma` and never names warp specialization, TMA programming,
tensor memory, or `wgmma` at all. A reader who completes all 24 modules in order cannot
follow the case study those modules exist to build up to. The gap is not that the section
is unaware of modern GPU programming — it is that the knowledge sits only in the capstone
and was never pushed down into the curriculum.

| Metric | Value |
|--------|-------|
| Modules | 24 |
| Deep-dive sub-files | **0** |
| Case studies | 6 (+ 5 `cross_cutting/` + 1 index) |
| Module lines | 27,420 |
| Case-study lines | 7,687 |
| Total section lines | 35,107 |
| Module Q&As (in the bank) | 427 |
| Case-study Q&As (correctly excluded) | 76 |
| Avg lines/module | 1,142 (range 952–1,314) |
| Mermaid fences | 165 |
| Senior tier / Full-only | 16 / 8 |
| Principal tier | none (deliberate; revisited in §3) |

Zero sub-files is itself the finding behind most of §1: this section is the only large one in
the repo with a completely flat module list. Every gap below has been forced either into a
module that is already at 1,100–1,300 lines, or out of the section altogether.

---

## 1. Missing — proposed ADDITIONS

| Proposed file path | Type | Why now (cited) | Pri | Est. lines |
|---|---|---|---|---|
| `cuda/asynchronous_execution_and_warp_specialization/asynchronous_execution_and_warp_specialization.md` | **module** (new, Phase 3/4 boundary) | TMA + `mbarrier` + warp-specialized producer/consumer warpgroups is *the* structure of every Hopper/Blackwell kernel. "Specialized warps are used on Hopper and Blackwell to issue either TMA copies or Tensor Core matrix-multiplies… the TMA warp issues copies and notifies the Tensor Core warps" — https://research.colfax-intl.com/tutorial-hopper-tma/ ; automatic warp specialization is now an active compiler research area (Tawa, https://arxiv.org/pdf/2510.14719) and a shipped Triton/Gluon tutorial (https://triton-lang.org/main/getting-started/tutorials/gluon/warp-specialization.html). Section hits for `mbarrier`/`cuda::pipeline`/`cp.async.bulk`: **0** | **P0** | 1,150 |
| `cuda/tensor_cores_and_mixed_precision/modern_mma_wgmma_and_tcgen05.md` | sub-file | "WGMMA no longer exists on SM100: it has been replaced with TCGEN05 tensor core instructions, and tensor core ops require different memory spaces (tensor memory)" — https://modal.com/blog/reverse-engineer-flash-attention-4 . Module teaches `nvcuda::wmma` only; `wgmma` hits = 0, `tcgen05` hits = 1 (a table row in a different module) | **P0** | 750 |
| `cuda/python_gpu_ecosystem/pytorch_custom_ops_and_integration.md` | sub-file | `torch.library.custom_op` + `register_fake` + `register_autograd` is the supported way to make a hand-written CUDA kernel survive `torch.compile`; without it Dynamo graph-breaks or traces into the kernel — https://docs.pytorch.org/tutorials/advanced/cpp_custom_ops.html , https://docs.pytorch.org/docs/main/library.html . Section hits for `torch.library`: **0**; the module teaches only `cpp_extension`+pybind11 | **P0** | 700 |
| `cuda/triton_and_kernel_dsls/cutile_and_tile_ir.md` | sub-file | CUDA 13.1 (Dec 2025) shipped **cuTile**, a tile-level programming model + Python DSL; NVIDIA then open-sourced **CUDA Tile IR**, an MLIR-based IR that "models the GPU as a tile-based processor" rather than SIMT — https://developer.nvidia.com/blog/focus-on-your-algorithm-nvidia-cuda-tile-handles-the-hardware/ , https://github.com/NVIDIA/cuda-tile , https://docs.nvidia.com/cuda/tile-ir/latest/sections/introduction.html . GTC 2026 session S81433. Section hits: **0** | **P0** | 600 |
| `cuda/multi_gpu_programming_and_nccl/device_initiated_communication.md` | sub-file | NCCL 2.28 added a **device-side API** callable from inside a CUDA kernel (LSA / Multimem / GIN) over symmetric-memory windows — https://developer.nvidia.com/blog/fusing-communication-and-compute-with-new-device-api-and-copy-engine-collectives-in-nvidia-nccl-2-28/ . NVSHMEM + DeepEP is how frontier MoE dispatch/combine is written and lifted DeepSeek-V3 MFU ~65% on 256 B200s — https://nebius.com/blog/posts/inside-the-nebius-pytorch-deepseek-v3-recipe , https://github.com/deepseek-ai/DeepEP . Section hits: `NVSHMEM` 1, `symmetric memory` 0, `IBGDA` 0, `expert parallel` 0 | P1 | 800 |
| `cuda/profiling_and_performance_analysis/nsight_compute_metrics_reference.md` | sub-file | The module runs `--set full` / `--section SpeedOfLight` but never names a counter. ncu 13.3 ships a dedicated `roofline` section set and `SpeedOfLight_HierarchicalTensorRooflineChart` for Tensor-Core kernels — https://docs.nvidia.com/nsight-compute/ProfilingGuide/index.html . `CUPTI` hits: 0 | P1 | 550 |
| `cuda/streams_events_and_concurrency/green_contexts_and_sm_partitioning.md` | sub-file | Green contexts are now a documented first-class CUDA feature (Programming Guide §4.6) partitioning SMs *within one process* — the in-process answer where the section only offers MPS/MIG — https://docs.nvidia.com/cuda/cuda-programming-guide/04-special-topics/green-contexts.html . Section hits: **0** | P1 | 500 |
| `cuda/case_studies/write_a_blackwell_gemm_with_cute_dsl.md` | case study | **Narrower than it first looks — see the correction below.** `build_a_flash_attention_kernel.md:639` *already* covers FA4 well (CuTe-DSL, 2-CTA MMA, tensor memory, the B200 ~1,605 TFLOP/s figure). What no case study does is make the reader **write** CuTe DSL, or apply it to a GEMM. CUTLASS is at 4.6.1 (2026-07-13) with SM100/SM103/SM120 2SM-MMA and blockscaled support — https://github.com/nvidia/cutlass ; CuTe DSL graduates beta end of summer 2026 | P1 | 1,000 |
| `cuda/case_studies/optimize_moe_expert_parallel_dispatch.md` | case study | "In 2026, nearly all frontier models are mixture-of-experts… every MoE layer needs two all-to-all exchanges" and the dispatch/combine kernels are the bottleneck — https://nebius.com/blog/posts/inside-the-nebius-pytorch-deepseek-v3-recipe . The 6 existing case studies are all single-GPU; none exercises `multi_gpu_programming_and_nccl` | P1 | 1,000 |
| `cuda/gpu_hardware_architecture/blackwell_and_rubin_architecture.md` | sub-file | Vera Rubin VR200 samples shipped, volume H2 2026, 288 GB HBM4/GPU, NVLink 6 — https://www.techpowerup.com/346786/nvidia-ships-first-vera-rubin-vr200-samples-to-customers , https://www.nvidia.com/en-us/data-center/gb200-nvl72/ . `HBM4` hits: 0; `Rubin` hits: 0 | P2 | 500 |
| `cuda/cuda_graphs/conditional_nodes_and_device_graph_launch.md` | sub-file | Conditional (IF/WHILE) graph nodes and device-side graph launch are the features that made graphs usable for dynamic-shape LLM decode; the module covers capture/instantiate/update only | P2 | 450 |
| `cuda/gpu_portability_hip_sycl_and_beyond/` (extend in place, no new file) | edit | ROCm 7.0 supports MI350X/MI355X; MI400 (432 GB HBM4) is H2 2026 dev-preview; ROCm 8.0 targets 90–95% CUDA source compatibility — https://rocm.docs.amd.com/en/docs-7.0.1/about/release-notes.html . Module names MI300X/MI250X only | P2 | +120 |

### What each P0 would actually contain

**`asynchronous_execution_and_warp_specialization`** — §1 the shift from "every thread loads
its own data" to "one warp issues a bulk copy and signals the rest"; §2 the factory analogy
(a dedicated loading dock feeding assembly-line crews, decoupled by a ticket counter); §3
principles — asynchrony needs an explicit completion object, TMA is single-threaded by
design, clusters make another block's SMEM addressable, warp roles are a compile-time
partition of `threadIdx.y`; §4 the ladder `cp.async` → `cuda::pipeline` → TMA
(`cp.async.bulk.tensor`) → cluster/DSMEM → full warp specialization; §5 an ASCII timeline of
a 3-stage pipelined mainloop with producer and consumer warp rows, plus a Mermaid state
diagram of an `mbarrier` phase flip; §6 code — a `cuda::pipeline` double-buffered tile loop,
a `CUtensorMap` built host-side and consumed by `cp.async.bulk.tensor.2d.shared::cluster`, an
`mbarrier` arrive/wait pair with the parity bit spelled out (*using*, not re-teaching, the
`cuda::barrier` vocabulary Wave 1b adds to `synchronization_and_atomics`), a `cluster.map_shared_rank()`
DSMEM read, and the same mainloop expressed in Triton `num_stages` and in Gluon; §7
FlashAttention-3/4, CUTLASS Hopper/Blackwell mainloops, DeepGEMM; §8 sync-copy vs
`cp.async` vs TMA (registers consumed, instruction count, who can issue); §9 use when the
mainloop is latency-bound and the tile is ≥ a few KB, not for small pointwise kernels; §10
BROKEN→FIX — an `mbarrier` waited on the wrong parity so the consumer reads stale SMEM, and
a TMA descriptor built in device memory instead of the required host/`__grid_constant__`
path; §11 CUTLASS/CuTe, Gluon, `libcu++`, `cuda::pipeline`, `ncu`'s async-copy counters; §12
18 Q&As led by "why does `cp.async` not need registers" and "what breaks if you forget the
parity flip"; §14 converting the Phase-3 tiled GEMM into a warp-specialized one and measuring
the SOL delta.

**`modern_mma_wgmma_and_tcgen05`** — the three-generation MMA lineage the parent module
stops short of: `wmma` (warp, register-resident, no FP8) → `wgmma.mma_async` (warpgroup =
128 threads, Hopper, SMEM operands, asynchronous) → `tcgen05` (Blackwell, operands and
accumulators in **Tensor Memory**, 2-SM MMA across a cluster pair). Then block-scaled
NVFP4/MXFP8 MMA — what a scale factor block *is*, where it lives, and why FP4 without
microscaling is unusable. Diagrams: an ASCII grid of where the accumulator physically sits
in each generation (RF → RF → TMEM), and a bar chart of dense peak per generation. Code in
CuTe DSL rather than raw PTX, because that is what FA4 uses. BROKEN→FIX: an `sm_90` build of
a `wgmma` kernel that compiles only under `sm_90a` and silently falls off the fast path.
Closes with the honest guidance — you will read these in SASS and select them through
CUTLASS/Triton, you will rarely hand-write them.

**`pytorch_custom_ops_and_integration`** — how a `.cu` file becomes an op that
`torch.compile` will not break on. `load_inline` for the 30-second loop; `torch.library.custom_op`
with a `mutates_args` spec; `register_fake` for meta/shape inference under symbolic tracing;
`register_autograd` with `setup_context`; the namespace rule (use `myapp::`, never
`flash_attn::` — re-registering there raises `RuntimeError`); and the measured tradeoff that
`@torch.library.custom_op` costs ~36 us/call against ~10 us for direct `torch.Library`
registration, so the decorator is the default and the raw API is the hot-path escape hatch.
BROKEN→FIX: a pybind11-only extension that makes Dynamo graph-break, versus the same kernel
registered as a custom op with a fake impl, shown as a `TORCH_LOGS=graph_breaks` diff. Also
CUDA-graph safety and why a custom op must not allocate.

**`cutile_and_tile_ir`** — what changes when the IR stops modelling the GPU as SIMT. The
tile as the unit of computation; how cuTile lowers a tile op to TMA + `tcgen05` without the
author naming either; Tile IR's position beside PTX (both are targets; PTX is thread-level,
Tile IR is tile-level, MLIR-based, and open source); a side-by-side of the same GEMM in
CUDA C++, Triton, and cuTile; the portability claim (one tile kernel across generations) and
its honest limit; and where it sits against Triton, which it visibly overlaps — including the
public disagreement about that overlap. Kept as a sub-file rather than a module because it is
a *fourth column* in `triton_and_kernel_dsls`' existing comparison table, not a separate
discipline.

---

## 2. Stale — verified CORRECTIONS

| File | Line/heading | What it says | True in Aug 2026 | Source |
|---|---|---|---|---|
| `cuda/README.md` | 7–10 (banner) | "**PARKED — to be done later (owner-set 2026-07-29).** …Paused: **14 of 19 audit units** and **427 Q&As awaiting one-line MCQ summaries**." | Section was unparked 2026-08-04; the audit is DONE for all 24 modules and `**Short:**` is 427/427 (`cuda/CLAUDE.md`, commits `312d0b4`/`ed1400a`/`e984f8c`/`60eed53`/`6ce35a6`). The section README contradicts its own CLAUDE.md on its front page | in-repo (`cuda/CLAUDE.md` "Scope — unparked 2026-08-04") |
| `cuda/README.md` | 405 (§8 banner) | "`game/extract.py` yields **425** CUDA MCQs" | 427 — `grep -c '^\*\*Q'` across the 24 module pages | in-repo count |
| `cuda/README.md` | 359 (arch table) | Blackwell row: "B100/B200, GB200 \| **10.0**" | `gpu_hardware_architecture.md:108` has the fuller "10.0 (B300/GB300 = 10.3; RTX 50 = 12.0)". Two tables in one section, one strictly less correct | in-repo inconsistency |
| `cuda/debugging_correctness_and_numerics/…md` | 590–591 | `torch.backends.cuda.matmul.allow_tf32 = False` / `torch.backends.cudnn.allow_tf32 = False` | Deprecated after PyTorch 2.9 in favour of `torch.backends.*.fp32_precision`; **PyTorch 2.11 does not support mixing old and new settings**. **This file is the ONLY site in the section still on the old API** — `tensor_cores_and_mixed_precision.md:511-517`, `case_studies/cross_cutting/numerical_precision_and_determinism.md:143-144,155-162,277,324-325,359` and `case_studies/implement_high_performance_reduction.md:802` all use `fp32_precision`. `cuda/CLAUDE.md` records this exact fix landing in the 2026-08-05 case-study re-audit; it never propagated to the module page | https://docs.pytorch.org/docs/2.11/notes/cuda.html |
| `cuda/debugging_correctness_and_numerics/…md` | 864 (§12 answer) | "Opt out with `torch.backends.cuda.matmul.allow_tf32 = False`." | Same. This one ships as a **quiz answer**, so the deprecated form is taught directly to every player. Contributing cause worth recording: this file names **no version number anywhere** — no CUDA, PyTorch, cuDNN or Nsight version — so nothing about it looks dated to a reviewer scanning for stale pins | https://docs.pytorch.org/docs/2.11/notes/cuda.html |
| `cuda/multi_gpu_programming_and_nccl/…md` | 56, 82–84, 92–99, 325, 361 (and the xychart at 61–67) | Every bandwidth baseline is **NVLink 4 / H100 SXM5, ~900 GB/s bidirectional, ~450 GB/s per direction, 18 links × 25 GB/s** | NVLink 5 (B200/B300/GB200) is **1.8 TB/s bidirectional per GPU, 18 links × 100 GB/s**; GB200 NVL72 gives 130 TB/s all-to-all across 72 GPUs. The *arithmetic and the bidirectional-vs-per-direction lesson are correct and worth keeping* — the baseline generation is one behind | https://www.nvidia.com/en-us/data-center/gb200-nvl72/ , https://www.nvidia.com/en-us/data-center/nvlink/ |
| `cuda/multi_gpu_programming_and_nccl/…md` | 945 (§11 NCCL API table) | NCCL surface presented as host-initiated only (`ncclCommInitRankConfig`, `ncclCommSplit`, …) | NCCL 2.28+ ships a **device-side API** (LSA / Multimem / GIN) callable from inside a kernel over symmetric-memory windows; NCCL is at 2.30.x. Not wrong, but the module's framing ("all NCCL operations are host-initiated") is now false | https://developer.nvidia.com/blog/fusing-communication-and-compute-with-new-device-api-and-copy-engine-collectives-in-nvidia-nccl-2-28/ , https://docs.nvidia.com/deeplearning/nccl/user-guide/docs/usage/deviceapi.html |
| `cuda/triton_and_kernel_dsls/…md` | 1061 (§11 table) | "ThunderKittens / CuTe (CUTLASS 3.x and 4.x) … CuTe also has a Python DSL in CUTLASS 4.x" | Understates it. CUTLASS is at **4.6.1** (2026-07-13); CuTe DSL is public beta graduating end of summer 2026 with SM100/SM103/SM120 2SM-MMA and blockscaled support, and **FlashAttention-4 is written entirely in it**. It belongs as a first-class row, not a parenthetical | https://github.com/nvidia/cutlass , https://tridao.me/blog/2026/flash4/ |
| `cuda/gpu_portability_hip_sycl_and_beyond/…md` | 5, 19, 546, 556, 880–1019 (the whole §14) | AMD represented **only** by MI300X / MI250X / "MI200/MI300 series"; **no ROCm version is named anywhere in the file** | ROCm 7.0 fully supports **MI350X/MI355X**; MI400 (432 GB HBM4) is H2 2026 dev-preview; ROCm 8.0 targets 90–95% CUDA source compatibility, which materially changes the module's central "how much do you pay for portability" argument. "The world moved" — the *reasoning* is intact and the wave64-vs-warp32 BROKEN→FIX is timeless; the roster and the porting-cost estimate are a generation stale | https://rocm.docs.amd.com/en/docs-7.0.1/about/release-notes.html |
| `cuda/gpu_hardware_architecture/…md` | 108, 130–133 (timeline) + `README.md:359` | Generation table and Mermaid timeline end at Blackwell | Vera Rubin VR200 samples shipped to customers; production shipments on track for H2 2026, 288 GB HBM4/GPU, NVLink 6. A "current generations" table with no Rubin row is now behind | https://www.techpowerup.com/346786/nvidia-ships-first-vera-rubin-vr200-samples-to-customers |

**One recorded gap is now CLOSED — do not re-open it.** `cuda/CLAUDE.md` says the
2026-08-05 case-study re-audit found "the FlashAttention lineage stopped at FA3 (FA4 shipped
2026-03)". It no longer does: `build_a_flash_attention_kernel.md:639` carries a full FA4
paragraph (CuTe-DSL, 2-CTA MMA, Blackwell tensor memory, softmax-warpgroup ping-pong,
software `exp` emulation, ~1,605 TFLOP/s on B200, 1.1–1.3x over cuDNN 9.13), plus FA4
references at `:31` and `:957`. That item should be struck from the section CLAUDE.md's open
list. The *other* item from the same re-audit — the `allow_tf32` booleans — is the one still
open, and only in `debugging_correctness_and_numerics` (see the two rows above).

**Explicitly NOT findings** (checked and clean): the CUDA toolkit references (`CUDA 13.0`,
the `TORCH_CUDA_ARCH_LIST` of `"7.5;8.0;8.6;9.0;10.0;12.0+PTX"` at
`cuda_toolkit_and_compilation.md:643`) are consistent with the CUDA 13.x line that is
current (13.3 GA, 13.4 developer preview —
https://docs.nvidia.com/cuda/developer-preview/13.4/cuda-toolkit-release-notes/index.html);
the "cuBLAS 11.0 removed the M/N/K multiple-of-8 rule" correction from the 2026-08-04 audit
is correct and well-written; `nvprof` and the legacy Visual Profiler survive only in two
case-study files as *historical* references, which is fine; `numba-cuda`'s out-of-tree split
is already documented at `python_gpu_ecosystem.md:798`.

---

## 3. Reordering — SEQUENCE and TIER changes

The current order is pedagogically sound and I am proposing **no reshuffle of Phases 1–3**.
Three changes, one of which matters much more than the others.

| Module | Current | Proposed | Why |
|---|---|---|---|
| `triton_and_kernel_dsls` | position **21**, Phase 5, **Full-only** | position **14**, end of Phase 3, **senior** | **The single most important move.** Triton is what TorchInductor emits, what most open-source fused kernels are written in, and increasingly the first kernel language an engineer touches. Sitting it at 21 behind streams, graphs, NCCL and dynamic parallelism says it is an advanced afterthought; it is the *other half of Phase 3*. Its stated prerequisites (coalescing, shared memory, occupancy — `triton_and_kernel_dsls.md:27-32`) are all satisfied at position 14. Leaving it out of Senior is the tier error I am most confident about: a senior GPU engineer in 2026 who cannot write a Triton kernel is not credible |
| `cuda_math_and_dnn_libraries` | position **19**, Full-only | position **18** (before `tensor_cores`), **senior** | "Call cuBLASLt/cuDNN before you hand-write anything" is the professional default, and `tensor_cores` §6.4 already forward-references CUTLASS. Teaching WMMA fragments *before* the library layer inverts the decision order every real engineer uses. Craft, not judgment — belongs in Senior |
| `cuda_toolkit_and_compilation` | position 3, **Full-only** | position 3, **senior** | Pure craft, and the highest-frequency real-world failure in the section (`-gencode` fallback PTX, JIT on an unlisted arch, driver/runtime skew). Everyone hits this; excluding it from Senior while including `parallel_patterns` is backwards |
| `multi_gpu_programming_and_nccl` | position 16, **Full-only** | position 16, **senior** | Any ML-infra or LLM-serving role interviews on ring all-reduce, NVLink topology and `NCCL_DEBUG=INFO`. This is craft |
| *new* `asynchronous_execution_and_warp_specialization` | — | position **14** (or 15, after Triton), **senior** | See §1. It is the natural sequel to `shared_memory_and_bank_conflicts` and the prerequisite for the new `modern_mma_wgmma_and_tcgen05` sub-file |

### Proposed `STUDY_ORDER["cuda"]`

```js
cuda: [
  // Phase 1 — GPU Foundations
  "cuda/gpu_computing_foundations","cuda/gpu_hardware_architecture","cuda/cuda_toolkit_and_compilation",
  // Phase 2 — Core CUDA Programming
  "cuda/cuda_programming_model_and_kernels","cuda/warps_and_simt_execution","cuda/cuda_memory_model_and_hierarchy","cuda/memory_management_and_data_transfer",
  // Phase 3 — Performance Engineering (interview core)
  "cuda/memory_coalescing_and_access_patterns","cuda/shared_memory_and_bank_conflicts","cuda/occupancy_and_launch_configuration","cuda/synchronization_and_atomics","cuda/parallel_patterns_reduction_scan_histogram","cuda/warp_level_primitives_and_cooperative_groups",
  // Phase 3b — Modern kernel authoring  (NEW placement)
  "cuda/triton_and_kernel_dsls","cuda/asynchronous_execution_and_warp_specialization",
  // Phase 4 — Advanced Execution & Multi-GPU
  "cuda/streams_events_and_concurrency","cuda/cuda_graphs","cuda/multi_gpu_programming_and_nccl","cuda/dynamic_parallelism_and_advanced_kernels",
  // Phase 5 — Libraries & Tensor Cores
  "cuda/cuda_math_and_dnn_libraries","cuda/tensor_cores_and_mixed_precision","cuda/python_gpu_ecosystem",
  // Phase 6 — Profiling, Correctness & Portability
  "cuda/profiling_and_performance_analysis","cuda/debugging_correctness_and_numerics","cuda/gpu_portability_hip_sycl_and_beyond",
],
```

(25 entries: 24 existing + 1 new module. `triton_and_kernel_dsls` moves 21→14,
`cuda_math_and_dnn_libraries` 19→20 relative but now *precedes* `tensor_cores`,
`python_gpu_ecosystem` 20→22.)

### Tier changes in the `<!-- study-paths -->` block

Senior goes 16 → 21 of 25. Add `senior` to: `cuda_toolkit_and_compilation`,
`multi_gpu_programming_and_nccl`, `cuda_math_and_dnn_libraries`, `triton_and_kernel_dsls`,
`asynchronous_execution_and_warp_specialization` (new). Sub-files inherit their parent's
tiers explicitly — the four new P0/P1 sub-files should all be `senior` except
`blackwell_and_rubin_architecture.md` and `conditional_nodes_and_device_graph_launch.md`,
which stay `-`.

### On the missing Principal tier — an owner decision, not a defect

`cuda/CLAUDE.md` states the section "has no Principal tier and needs none", and it is right
that an *empty* one is a false alarm. But the brief's definition (senior = craft; principal =
judgment; different cuts, not nested depths) fits this section unusually well, and a
**populated** principal tier is now defensible on six modules where the content is already
judgment-shaped:

`gpu_portability_hip_sycl_and_beyond` (how much peak do you trade for how much reach —
literally the module's closing sentence), `cuda_math_and_dnn_libraries` (§ "The
Library-vs-Custom-Kernel Decision"), `triton_and_kernel_dsls` (which altitude to write at),
`occupancy_and_launch_configuration` (the "occupancy is not performance" call),
`multi_gpu_programming_and_nccl` (topology and algorithm selection), and
`profiling_and_performance_analysis` (where to spend the next engineering week). I am
proposing this as **P2 and flagging it as an owner decision**, not executing it: it changes
the section's declared shape and the CLAUDE.md paragraph that governs it.

---

## 4. Depth — THIN modules and BLOAT

**No module is thin.** The floor is `warps_and_simt_execution` at 952 lines / 19 Q&As, which
is a tight, well-scoped module rather than an underweight one. Every module clears its Q&A
target (427 across 24, against a 15/18 floor summing to 375). This is the healthiest section
in the repo on the quality-bar axis, and the audit trail explains why.

The problems are the inverse — **content in the wrong module**, and one module whose §6 is
teaching the wrong API.

| Module | Lines | Q&As | What is wrong | Fix |
|---|---|---|---|---|
| `tensor_cores_and_mixed_precision` | 1,128 | 22 | Not thin — **misaimed**. §6.1 WMMA fragment API, §6.2 the `mma.sync` PTX underneath, §6.3 compute-capability gating of fragment types, §6.4 CUTLASS. `wgmma` = 0 mentions; `tcgen05` = 0; TMEM = 0; NVFP4/MXFP/block-scaled = 0. The module correctly notes at line 35 that WMMA has no FP8 type on any architecture — and then never says what you use instead | Add the `modern_mma_wgmma_and_tcgen05.md` sub-file (§1 P0) and add one forward-pointing paragraph at the end of §4.1. Do **not** rewrite §6.1–6.3: WMMA is still the right teaching ladder, it is just no longer the top rung |
| `gpu_hardware_architecture` | 1,148 | 16 | **Genuine bloat.** §4.3 "Occupancy-Limiting Resource Types" (171–183) plus §6 "The Occupancy Math — Three Independent Ceilings" (389–523) and "Letting the Driver Pick a Block Size" (524–541) is ~150 lines of occupancy material sitting seven positions ahead of the dedicated 1,200-line `occupancy_and_launch_configuration` module | Cut §6's occupancy math to ~30 lines (state the three ceilings, show `cudaOccupancyMaxPotentialBlockSize`, link out). Keep §4.3 as a hardware-resource taxonomy. Saves ~120 lines and removes the "which module owns occupancy" ambiguity |
| `gpu_computing_foundations` | 1,019 | 16 | §4.4 "Interconnect Strategies: PCIe vs NVLink" (138–155) restates the interconnect table from `multi_gpu_programming_and_nccl` §4 (54–99), including the same PCIe Gen5 ~64 GB/s figure — and does it *before* the reader has any multi-GPU context | Reduce to a two-row orientation table + link. The bidirectional-vs-per-direction lesson (the thing the 2026-08-04 audit fixed) should live in exactly one place |
| `python_gpu_ecosystem` | 1,050 | 16 | Not thin, but §4.4 teaches `torch.utils.cpp_extension.load()` + pybind11 as *the* PyTorch integration path and §4.5 teaches `torch.compile` — **without ever connecting them**. A kernel bound the §4.4 way graph-breaks under the §4.5 compiler, and the file never says so. Also absent: `load_inline` (only `load()`), `Pallas`, NVIDIA Warp, nvmath-python. `cuda.core`/`cuda.bindings`/`cuda.pathfinder` **are** covered well (§7:676) — not a gap | Add the `pytorch_custom_ops_and_integration.md` sub-file (§1 P0) and one cross-reference sentence in §4.4 |
| `cuda_math_and_dnn_libraries` | 1,134 | 17 | Strong and current where it counts — cuDNN **graph API** as the supported surface with the pre-cuDNN-8 imperative API marked deprecated (§11:889), cuBLASLt fused epilogues, CUTLASS 2.x→3.x→4.x + CuTe + CuTe DSL (:495). The hole is precision: **FP4 is absent entirely** from the library module, FP8 gets one table cell (:701), and there is no cuBLASDx/cuFFTDx, no nvmath-python, no SDPA (`scaled_dot_product_attention`) despite "fused attention" appearing in two tables | Add ~60 lines to §4 on the FP8/FP4 library paths and one §6 cuBLASLt FP8 example. Cross-links to the new `modern_mma` sub-file rather than re-deriving |
| `gpu_portability_hip_sycl_and_beyond` | 1,025 | 18 | Argument and structure are excellent (four-way CUDA/HIP/SYCL/Python Rosetta Stone; the wave64-vs-warp32 BROKEN→FIX is a genuinely good bug). But **no ROCm or HIP version number appears anywhere in the file**, the AMD roster stops at MI300X/MI250X, and OpenACC and Mojo are absent while Vulkan gets one line | Wave 0 correction (§2) refreshes the roster and the porting-cost estimate. OpenACC/Mojo are P2 one-liners in the §11 table, not new prose |
| `cuda_memory_model_and_hierarchy` | 1,100 | 19 | Covers six memory spaces thoroughly and stops at UVA (§6.6). No `cp.async`, no async-copy path into shared memory | The new async module owns `cp.async`; add a §4.9 pointer |
| `shared_memory_and_bank_conflicts` | 1,144 | 20 | **On-topic gap.** Lines 639–647 discuss Hopper's 228 KB per-block opt-in via `cudaFuncSetAttribute` — i.e. the module already reaches Hopper — and then never mentions **distributed shared memory**, the Hopper feature that makes another SM's shared memory addressable. A module named "shared memory" that stops at one SM in 2026 | Add a §4.4 + §6.7 on cluster-scoped SMEM (`cluster.map_shared_rank()`), ~80 lines, cross-linking the new async module which owns the barrier semantics |
| `synchronization_and_atomics` | 1,214 | 18 | Has genuinely deep `libcu++` / `cuda::atomic` / `memory_order` coverage (§6.4, 544–591, with a working acquire/release producer-consumer) — and **zero** `mbarrier` / `cuda::barrier` / `arrive_and_wait`. It has the vocabulary and stops one rung short of the async-barrier model | Add ~90 lines on `cuda::barrier` + `arrive_and_wait` + `mbarrier` phase parity as §6.6; it is the natural home, and the new async module then *uses* it rather than re-teaching it |
| `parallel_patterns_reduction_scan_histogram` | 1,229 | 16 | Best CUB/Thrust-vs-hand-rolled treatment in the section, but **decoupled look-back scan is absent** (verified: zero hits). Line 184 even says "CUB's and Thrust's scan implementations use work-efficient multi-block variants derived from this [Blelloch] structure" without naming the single-pass algorithm they actually use | Add a §6.5b (~70 lines) on decoupled look-back: why a multi-block scan needs two passes naively, how the look-back flag/aggregate/prefix state collapses it to one, and the memory-model requirement that makes it correct. This module was one of the two that came back with **zero** audit corrections — the gap is coverage, not accuracy |
| `memory_management_and_data_transfer` | 1,238 | 16 | Deep on Unified Memory (§6.3, page-fault lifecycle, `cudaMemAdvise`/`cudaMemPrefetchAsync`) and correctly covers `cudaMallocAsync` + pools — but **no Grace-Hopper coherent unified memory / ATS** (verified: zero hits), and no VMM API (`cuMemCreate`/`cuMemMap`). UM on a coherent GH200/GB200 has different performance semantics from PCIe-attached UM, which is the whole §6.3 mental model | Add ~70 lines to §4.4 distinguishing PCIe-attached UM (page migration on fault) from coherent NVLink-C2C UM (no migration needed). P1 — bundle with the Wave 3 NVLink refresh since both are Grace-Blackwell-facing |
| `case_studies/*` | 890–1,125 | 11–15 each | **Structurally clean** — all six carry the full 12 headings (Intuition + §1–11), all clear the 4-cross_cutting-reference bar comfortably (6–9 each), all clear 10+ Q&As, and `case_studies.md` has all five required index sections. The gap is *scope*: all six are single-GPU. Case-study coverage of `multi_gpu_programming_and_nccl`, `cuda_graphs` and `python_gpu_ecosystem` is zero. Minor: `accelerate_2d_convolution_and_stencil` at 1,125 lines is 25 over the 900–1,100 bar, `implement_high_performance_reduction` at 890 is 10 under | Add the two P1 case studies (§1). Do **not** open a pass just to trim 25 lines |

**No merges or deletions recommended — and I checked the obvious candidate properly.** The
`cuda_memory_model_and_hierarchy` / `memory_coalescing_and_access_patterns` /
`shared_memory_and_bank_conflicts` trio (3,385 lines across three adjacent modules) looks
like a merge target and is not one. A line-level diff shows a deliberate "mental model → two
deep dives" split with explicit hand-offs, not three files re-deriving the same material:

- `cuda_memory_model_and_hierarchy:53` states global memory's identity and lifetime, then
  says outright that "access-pattern performance lives in
  `../memory_coalescing_and_access_patterns/`" — no coalescing mechanics there. `:47` does
  the same for bank structure.
- The one real overlap is a *conclusion vs. derivation* pair, not duplicated text:
  `cuda_memory_model_and_hierarchy:936-938` states the 32-byte-sector worst case in one
  paragraph ("moving 1,024 bytes for 128 useful ones") and hands off;
  `memory_coalescing:270-336` derives it with the full stride ladder, the `sectors(warp)`
  formula and the cap-at-32 reasoning. Different worked examples, no shared sentences.
- The two files' *own* bank-conflict content is complementary rather than overlapping:
  `cuda_memory_model_and_hierarchy:245-260` derives an SM-aggregate bandwidth argument
  (128 B/cycle × 1.41 GHz × 108 SMs = 19.5 TB/s vs HBM's ~2–3 TB/s) to motivate tiling at
  the device level; `shared_memory_and_bank_conflicts:228-247` uses a completely separate
  framing (`bank(row,col) = (row*S+col) mod 32`, `gcd(S,32)` conflict degree). Neither
  repeats the other's math.

Merging would produce one 3,000-line module, destroy the ability to tier the three
independently, and collapse a spiral that is doing real pedagogical work. Leave it.

---

## 5. Cross-section notes

- **`llm/`** — `llm/optimization_and_quantization` owns quantization *formats and accuracy*;
  the proposed `modern_mma_wgmma_and_tcgen05.md` must own only the *instruction and memory
  layout* side of NVFP4/MXFP (what a scale block is, where it lives, which MMA consumes it)
  and link out for "which quantization scheme for which model". The existing GPU Non-Overlap
  Boundary in `cuda/CLAUDE.md` already states this rule; the new sub-file needs a row added
  to that table.
- **`llm/vllm_deep_dive` and `llm/inference_engines`** — the proposed
  `optimize_moe_expert_parallel_dispatch` case study overlaps `llm/mixture_of_experts`.
  Boundary: `llm/` owns routing, capacity factors, load balancing and expert placement;
  `cuda/` owns the dispatch/combine **kernels** and the NVSHMEM/DeepEP communication
  substrate. Cross-link both ways.
- **`ml/distributed_training`** — owns ZeRO/FSDP/parallelism *strategy*. The proposed
  `device_initiated_communication.md` must stay at the wire: symmetric-memory windows, the
  NCCL device API's three modes, one-shot vs two-shot allreduce. Add a "see also" from
  `ml/distributed_training` to it.
- **`devops/ml_platform_and_gpu_infrastructure`** — owns MIG on Kubernetes and the GPU
  Operator. The proposed `green_contexts_and_sm_partitioning.md` is the *in-process*
  counterpart and is correctly CUDA-side; it should explicitly contrast green contexts (one
  process, driver API) against MPS (multi-process) and MIG (hardware partition, ops-owned)
  and link to devops for the last.
- **`ml/gpu_and_hardware_optimization`** — checked: it carries **no** Blackwell/Rubin
  architecture table, so `blackwell_and_rubin_architecture.md` would not duplicate it. But it
  has the opposite problem, and this is a **finding for the `ml` auditor**: line 34 states
  "Tensor Cores on A100 perform 4x4 matrix multiplications in one cycle, achieving 312
  TFLOP/s in BF16… **ensure matrix dimensions are multiples of 8 (for BF16) or 16 (for INT8)
  — misaligned dimensions fall back to CUDA cores**." That is *exactly* the claim cuda's
  2026-08-04 audit established is false (no such restriction since cuBLAS 11.0 outside FP8;
  what survives is a 16-byte-alignment performance cliff, not a functional fallback — see
  `cuda/tensor_cores_and_mixed_precision.md:30` and `cuda/CLAUDE.md`). The correction landed
  in `cuda/` and never propagated. Line 32's memory-hierarchy numbers are A100-only and line
  36's "~16 GB/s for PCIe 4.0" is a per-direction x16 figure quoted without the convention —
  the same trap cuda's `multi_gpu` module was corrected for. Hand this to whoever audits `ml`.
- **Repo-wide**: `cuda/README.md`'s stale PARKED banner (§2) is the same class of defect the
  2026-08-04 root-CLAUDE.md cleanup fixed elsewhere. Worth checking whether any *other*
  section README still carries an expired parking notice.

---

## 6. Execution plan

Effort is in agent-sessions at this repo's demonstrated rate (~1,100-line module ≈ 1 session
including Q&As, diagrams and `--write-paths`/`--strict` verification).

### Wave 0 — Corrections only (≈1 session, do first, fully parallel)
1. Fix `cuda/README.md` lines 7–10 (delete the PARKED banner), 405 (425→427), 359 (Blackwell
   CC row to match `gpu_hardware_architecture.md:108`).
2. Fix `debugging_correctness_and_numerics.md` lines 590–591 and 864 to the `fp32_precision`
   API, **and re-read the `**Short:**` line above the §12 Q&A in the same edit** (root
   CLAUDE.md: "the audit owns the Short line").
3. Refresh the AMD roster in `gpu_portability_hip_sycl_and_beyond` (MI355X, ROCm 7.0, MI400
   H2 2026) — prose only, no structural change.
4. Add a Rubin row to both architecture tables + the Mermaid timeline in
   `gpu_hardware_architecture.md`.
   *(1–4 touch four different files and can run as four parallel agents. No `STUDY_ORDER`,
   no `study-paths`, no root README impact. Run `node scripts/check_render.mjs` on the
   timeline change — it is a Mermaid edit.)*

### Wave 1 — The P0 gap (≈4 sessions; 1 sequential + 3 parallel)
5. **Write `asynchronous_execution_and_warp_specialization/`** (1.5 sessions). Do this
   **first and alone** — the `modern_mma` sub-file depends on its `mbarrier`/TMA vocabulary.
   Requires: `STUDY_ORDER["cuda"]` insert, a new module block in the README
   `<!-- study-paths -->` (tagged `senior`), README §3 module table + §8 manifest row, root
   `README.md` CUDA phase table, root `CLAUDE.md` module count 24→25, `cuda/CLAUDE.md`
   module list + Phase table + deep-modules 18-Q&A list.
6. Then, in parallel (3 agents):
   - `tensor_cores_and_mixed_precision/modern_mma_wgmma_and_tcgen05.md`
   - `python_gpu_ecosystem/pytorch_custom_ops_and_integration.md`
   - `triton_and_kernel_dsls/cutile_and_tile_ir.md`
   Each needs only a `<!-- study-paths -->` line under its parent module (sub-files need no
   `STUDY_ORDER` entry) plus a `## 11` row and one §4 forward-pointer in the parent page.

### Wave 2 — Reordering (≈0.5 session, sequential, after Wave 1)
7. Apply the `STUDY_ORDER["cuda"]` array from §3, the five `senior` tier additions, and the
   `cuda_math_and_dnn_libraries` ↔ `tensor_cores` swap. Update `cuda/README.md` §3 phase
   table and `cuda/CLAUDE.md`'s Phase list (Triton leaves Phase 5 for a new "Phase 3b").
   Then `python3 game/extract.py --write-paths` followed by `bash scripts/build_banks.sh`
   — **the whole script, not just `--strict`** (repo memory: `--strict` passing does not mean
   CI passes).
   *Must be one commit: `STUDY_ORDER`, the study-paths block and the generated Senior table
   are checked against each other and a partial change is fatal.*

### Wave 1b — In-place §4 depth fixes (≈1 session total, 4 parallel agents, may overlap Wave 2)
Each is a ~70–90-line addition to an existing module, closing a gap that is *on that module's
own topic*. Cheap, no structural wiring beyond a Q&A or two, and two of them are
prerequisites the new async module should link to rather than re-teach.
- 5a. `synchronization_and_atomics` §6.6 — `cuda::barrier` / `arrive_and_wait` / `mbarrier`
  phase parity. **Do this before or with item 5 — the async module depends on it.**
- 5b. `shared_memory_and_bank_conflicts` §4.4 + §6.7 — distributed shared memory /
  `cluster.map_shared_rank()`.
- 5c. `parallel_patterns_reduction_scan_histogram` §6.5b — decoupled look-back scan.
- 5d. `memory_management_and_data_transfer` §4.4 — coherent (NVLink-C2C) vs PCIe-attached
  Unified Memory.
Each adds 2–3 Q&As (all four modules sit above their floor, so this is headroom not
pressure), and every new Q&A needs an authored `**Short:**` line.

### Wave 3 — P1 depth (≈4 sessions, fully parallel)
8. `multi_gpu_programming_and_nccl/device_initiated_communication.md` **plus** the NVLink-5
   baseline refresh in the parent page (§2 rows 6–7). Bundle these: the sub-file's numbers
   and the parent's table must agree, and the parent has an xychart and two ASCII diagrams
   carrying the old figures. Re-render every fence touched.
9. `profiling_and_performance_analysis/nsight_compute_metrics_reference.md`.
10. `streams_events_and_concurrency/green_contexts_and_sm_partitioning.md`.
11. `case_studies/write_a_blackwell_gemm_with_cute_dsl.md` — **also** update
    `case_studies/case_studies.md` (all 5 sections: Quick Start if it displaces one, phase
    group, cross-cutting table, dependency map, interview-prep row) in the same commit.

### Wave 4 — P1/P2 tail (≈3 sessions, parallel)
12. `case_studies/optimize_moe_expert_parallel_dispatch.md` + `case_studies.md` update.
    Depends on #8 landing (it will reference the device-API sub-file).
13. `gpu_hardware_architecture/blackwell_and_rubin_architecture.md` **and** the §6
    occupancy-math trim from §4 in the same pass — same file, one agent.
14. `cuda_graphs/conditional_nodes_and_device_graph_launch.md`; the
    `gpu_computing_foundations` §4.4 interconnect trim.
15. Owner decision on the Principal tier (§3). If yes: ~0.5 session to add the markers and
    regenerate.
16. **Housekeeping in `cuda/CLAUDE.md`** (~10 min, any wave): strike the "FlashAttention
    lineage stopped at FA3" item — it is closed (§2). Leave the two genuine open items (CC
    12.x 24-vs-32 blocks / 100-vs-128 KB, and CC 10.3/11.0 missing) exactly as they are.

**Total: ≈14 agent-sessions.** Waves 0, 1 and 1b (6.5 sessions) capture most of the value —
they close the "no modern asynchronous kernel" gap, the four on-topic depth holes, and every
verified correction.

**Files needing structural updates across the whole plan:** `game/app.js`
(`STUDY_ORDER["cuda"]` — Wave 1 insert, Wave 2 reorder), `cuda/README.md`
(`<!-- study-paths -->` block, §3 module table, §8 manifest, the stale banner, and the
**generated** Senior table via `--write-paths` — never hand-edited), `cuda/CLAUDE.md` (module
list, phase table, 18-Q&A deep-module list, cross-reference map, GPU Non-Overlap Boundary
row for NVFP4), root `README.md` (CUDA phase table), root `CLAUDE.md` (module count 24→25,
and "1 deep-dive sub-file"→8 in the section table line).

---

## 7. Confidence and gaps

**High confidence.** The coverage gaps in §1 are grep-verified against the whole section
(zero hits for `mbarrier`, `cuda::pipeline`, `cp.async.bulk`, `DSMEM`, `wgmma`, `cuTile`,
`Tile IR`, `torch.library`, `green context`, `CUPTI`, `block-scaled`; one hit each for
`tcgen05` and `TMEM`), and every "why now" carries a 2026 source I fetched. The
`allow_tf32` correction is verified against PyTorch 2.11 docs and is doubly strong because
the sibling module was already migrated — it is an internal inconsistency, not a judgment
call. The README banner and MCQ-count staleness are verifiable in-repo in one command.

**How this was produced.** Module profiling was split between Sonnet subagents and my own
direct reads (subagent capacity was heavily contended, so several batches I ran myself).
Every factual claim in this report — every "zero hits", every line number, every quoted
string — I re-verified directly against the files with `grep -w` or `sed` before writing it
down, including the claims subagents reported. Nothing here rests on a subagent summary
alone. Two subagent claims were checked and one materially changed the report: the assertion
that FA4 is already covered was **correct**, and it forced me to rewrite a P1 justification
and reframe §0.

**A methodology note worth passing on.** My first coverage sweep used a plain substring
grep and reported TMA present in 22 files. It is present in **6** — `grep -r "TMA"` matches
`sof**tma**x`. Every coverage count in this report was re-run with `grep -w`. If anyone
repeats this audit on another section, use word boundaries; a substring sweep on a
three-letter acronym will tell you a gap is covered when it is not.

**Medium confidence.** The NVLink-5 correction: I verified the Blackwell numbers, but I did
not re-derive every downstream arithmetic block in `multi_gpu_programming_and_nccl` (the
worked 7B-gradient example at line 325, the ratio table at 92–99). Whoever executes must
recompute all of them, not substitute the headline number — the 2026-08-04 audit already
found one 2x error in exactly this file from mixing bandwidth conventions, and this is the
same trap.

**What I could not verify.**
1. **The exact per-module `**Short:**` lines that any correction will invalidate.** I read
   Q&A bodies, not the summary lines above them, at most sites. The two
   `debugging_correctness_and_numerics` fixes definitely need their Shorts re-read; the
   NVLink refresh may touch several in `multi_gpu_programming_and_nccl` §12.
2. **CC 10.3 / 11.0 in the capability enumerations, and the 12.x
   24-vs-32-blocks/100-vs-128-KB disagreement.** `cuda/CLAUDE.md` records both as open owner
   decisions from the 2026-08-04 audit. I deliberately did **not** re-open them: NVIDIA's
   own two sources still disagree, and guessing a hardware number is the failure mode that
   audit existed to prevent. They remain owner decisions.
3. **Whether the six existing case studies' §4 code still compiles** against CUDA 13.x. Not
   checked; out of scope for a read-only planning pass, but worth one `nvcc` sweep if
   anyone ever has hardware.
4. **The 3–8 us device-side child-launch figure** in `dynamic_parallelism_and_advanced_kernels`
   and the K=4095 cuBLAS sweep — both flagged unverified by the previous audit and both
   still unverifiable without hardware. Left alone.
