# Numerical Precision & Determinism (Cross-Cutting Reference)

A GPU kernel that produces a different bit pattern than a CPU reference implementation, or a
different result on two runs of the same input, is not automatically buggy — floating-point
arithmetic is not associative, and GPUs execute reductions in a different order and with
different fused-instruction behavior than a CPU. This file is the reusable reference for
reasoning about **which precision to pick**, **why results legitimately differ**, and **how to
make a kernel or training run reproducible when the business requires it** (regulatory audit,
bit-exact CI regression tests, debugging a divergence between runs). Any case study that touches
mixed precision, custom reductions/atomics, or a "why don't my results match" bug should link
here instead of re-deriving these facts.

---

## 1. Floating-Point Formats on GPUs

Every GPU numeric format trades mantissa bits (precision) against exponent bits (dynamic range)
against total width (throughput and memory bandwidth). Tensor Cores exist specifically to make
the narrower formats fast: a Tensor Core FP16/BF16 matrix-multiply-accumulate can be 8-16x the
FLOP/s of the equivalent FP32 CUDA-core path on the same die.

| Format | Bit layout (sign / exponent / mantissa) | Total bits | Approx. dynamic range | Approx. decimal precision | Typical use |
|--------|------------------------------------------|-----------|------------------------|----------------------------|-------------|
| FP64 | 1 / 11 / 52 | 64 | ~1e-308 .. 1e308 | ~15-17 digits | Scientific/HPC accumulation, reference correctness checks |
| FP32 | 1 / 8 / 23 | 32 | ~1e-38 .. 3.4e38 | ~7 digits | Default training/inference math, CPU-reference comparisons |
| TF32 (Ampere+) | 1 / 8 / 10 (stored in a 32-bit register) | 19 used | ~1e-38 .. 3.4e38 (FP32 range) | ~3 digits | **Default** Tensor-Core path for FP32 matmul on Ampere+ (opt-out, see §4) |
| FP16 | 1 / 5 / 10 | 16 | ~6.0e-5 .. 65504 | ~3 digits | Mixed-precision training/inference; needs loss scaling (narrow range underflows/overflows) |
| BF16 | 1 / 8 / 7 | 16 | ~1e-38 .. 3.4e38 (FP32 range) | ~2 digits | Mixed-precision training without loss scaling (same range as FP32, less precision) |
| FP8 E4M3 | 1 / 4 / 3 | 8 | ~1.5e-2 .. 448 | ~1-2 digits | Forward-pass weights/activations (Hopper/Ada Transformer Engine, inference) |
| FP8 E5M2 | 1 / 5 / 2 | 8 | ~1.5e-5 .. 57344 | ~1 digit | Gradients/backward pass (needs more range, tolerates less precision) |
| FP4 / FP6 (Blackwell) | FP4 E2M1: 1/2/1 (4 bits); FP6 E2M3/E3M2 (6 bits) | 4 / 6 | Very narrow — requires microscaling | <1 digit alone | Inference-only, always paired with a per-block scale factor (NVFP4/MXFP4); 5th-gen Tensor Cores |

**Reading the table as an interview gotcha:** BF16 and FP16 are both "16 bits," but they are not
interchangeable. BF16 keeps FP32's exponent range (so it overflows/underflows at roughly the same
magnitudes FP32 does) at the cost of precision; FP16 keeps more mantissa precision but has a
dynamic range narrow enough that gradients in deep networks routinely underflow to zero without
loss scaling. This is why BF16 became the default for LLM pretraining on Ampere+/TPU while FP16
still needs `GradScaler`-style loss scaling.

```
FP16  (half precision, 16 bits): sign(1) + exponent(5)  + mantissa(10)
        |S|EEEEE|MMMMMMMMMM|
         1   5        10          range ~6.0e-5 .. 65504   (~3 decimal digits)

BF16  (bfloat16, 16 bits):      sign(1) + exponent(8)  + mantissa(7)
        |S|EEEEEEEE|MMMMMMM|
         1     8        7          range ~1e-38 .. 3.4e38  (~2 decimal digits, FP32's range)
```
*BF16 spends 3 more bits on exponent and 3 fewer on mantissa than FP16 — same 16-bit budget, an
explicit range-vs-precision trade. This is the whole reason BF16 does not need loss scaling: it
cannot underflow at FP16's magnitudes because it shares FP32's exponent field width.*

See [`../../tensor_cores_and_mixed_precision/README.md`](../../tensor_cores_and_mixed_precision/README.md)
for how these formats map onto WMMA fragments, `mma` PTX instructions, and cuBLAS/cuDNN
Tensor-Core code paths.

---

## 2. FMA & Why GPU Results Legitimately Differ from the CPU

A **fused multiply-add (FMA)**, `d = a * b + c`, computes the product and sum in one hardware
instruction with a **single rounding** at the end, instead of rounding the product and then
rounding the sum separately (two roundings). GPUs aggressively contract `a * b + c` patterns into
FMA instructions because it is both faster (one instruction instead of two) and, in isolation,
*more* accurate (one rounding error instead of two).

```cpp
// Separate multiply then add: TWO roundings
float p = a * b;        // rounds here
float d_two_round = p + c;  // rounds again

// Fused multiply-add: ONE rounding, full-precision product kept internally
float d_fma = fmaf(a, b, c);   // nvcc emits this automatically for a*b+c unless told not to

// d_fma and d_two_round can differ in the last bit(s) — this is not a bug,
// it is two different, both-correctly-rounded, IEEE-754 operations.
```

This single-vs-double-rounding difference is one root cause of GPU-vs-CPU mismatches, but it is
not the only one, and often not the dominant one:

- **x86 CPUs historically used 80-bit extended precision internally** for scalar FP math (x87),
  rounding down to 32/64-bit only at store time — a different intermediate precision than the
  GPU's strict 32-bit path. Modern SSE/AVX code mostly avoids this, but older CPU reference code
  can still show it.
- **Reduction order differs.** `sum(a[0..N])` on a CPU is typically a strict sequential loop.
  A GPU reduction is a **tree**: pairs are summed in parallel, then pairs-of-pairs, and so on
  (see [`../../parallel_patterns_reduction_scan_histogram/README.md`](../../parallel_patterns_reduction_scan_histogram/)).
  Floating-point addition is **not associative** — `(a+b)+c` is not bit-identical to `a+(b+c)` in
  general — so a tree-order sum and a sequential sum of the *same* N numbers can differ starting
  in low-order bits, growing with N and with the dynamic-range spread of the inputs.
- **Compilers make different contraction choices.** `nvcc` and `gcc`/`clang` do not always fuse
  the same multiply-add expressions into FMA at the same optimization level, so the *same* C++
  source can compile to different instruction sequences on host vs device even before any
  parallel reduction is involved.

**The practical takeaway for a kernel author:** GPU vs CPU mismatch at the 1e-5–1e-7 relative
scale (FP32) is expected and is not, by itself, evidence of a bug. Treat a numeric correctness
check as "within a tolerance" (`allclose` with `rtol`/`atol`), never as bit-exact equality, unless
you have deliberately built a deterministic, order-fixed kernel (§6) and are comparing against
another deterministic, order-fixed reference.

---

## 3. Compiler Flags: `--fmad` and `--use_fast_math`

`nvcc` exposes explicit knobs over this contraction and over transcendental-function accuracy:

| Flag | Default | What it changes |
|------|---------|-------------------|
| `--fmad=true` (implicit default) | on | `nvcc` is free to contract `a*b+c` into a single `fma.rn` PTX instruction. Faster and normally *more* accurate (one rounding), but changes bit patterns vs a non-fused reference and can change results between compiler versions. |
| `--fmad=false` | — | Forces separate multiply and add instructions — two roundings, matching a naive "textbook" reference implementation bit-for-bit more often. Use this when debugging a numerics mismatch against a scalar CPU reference, never for production performance. |
| `--use_fast_math` | off | An umbrella flag: implies `--fmad=true`, forces low-precision **fast approximations** of transcendental intrinsics (`__sinf`, `__cosf`, `__expf`, `__logf`, `__powf` in place of `sinf`/`cosf`/...), flushes denormals to zero, and disables strict IEEE rounding checks in some paths. Materially faster for math-heavy kernels but the approximate transcendentals can have relative error up to ~2^-11 (roughly 3-4 decimal digits) instead of the ~1 ULP of the precise intrinsics — do not use it upstream of a numerically sensitive normalization (e.g. softmax denominators feeding a loss) without validating error budget. |
| `-prec-div=false` / `-prec-sqrt=false` | true (precise) | Individually relax division and `sqrt` to faster, slightly-less-precise variants; both are also flipped by `--use_fast_math`. |

```
# Debugging a CPU/GPU mismatch: force the least-fused, most CPU-like codegen
nvcc -O3 --fmad=false -prec-div=true -prec-sqrt=true kernel.cu -o kernel_strict

# Production throughput build once numerics are validated
nvcc -O3 --use_fast_math kernel.cu -o kernel_fast
```

---

## 4. TF32 — the Silent Default on Ampere+

**TF32 (TensorFloat-32) is the default math mode for FP32 matrix multiplies on Ampere and later**
whenever the operation goes through a Tensor-Core-eligible path (cuBLAS GEMM, cuDNN convolution,
`torch.matmul`/`nn.Linear` on CUDA). Your code still stores tensors as 32-bit FP32 — TF32 is a
computation mode, not a storage format: cuBLAS/cuDNN round the FP32 inputs down to TF32's 10
mantissa bits internally, multiply-accumulate at that reduced precision (usually still
accumulating in full FP32), and hand back an FP32 result. This is the single most common
"why did my accuracy silently drop" / "why is this suddenly 4-8x faster with no code change"
gotcha when moving a model from a Volta/Turing GPU to Ampere+ or from CPU to Ampere+ GPU.

```python
import torch

# On an Ampere+ GPU, torch.matmul on FP32 tensors uses TF32 by DEFAULT — you did
# not ask for reduced precision, but you are getting ~3 decimal digits of mantissa.
a = torch.randn(4096, 4096, device="cuda")          # dtype=torch.float32
b = torch.randn(4096, 4096, device="cuda")
c_tf32 = a @ b                                        # TF32 path, fast, ~3-digit mantissa

# Opt out explicitly to get the full FP32 (single-rounding-per-MAC, IEEE) path:
torch.backends.cuda.matmul.allow_tf32 = False
torch.backends.cudnn.allow_tf32 = False               # convolutions go through cuDNN separately
c_fp32 = a @ b                                        # true FP32, slower, ~7-digit mantissa
```

In raw CUDA C++, the same choice is made explicitly at the `mma`/cuBLAS API level — the default
`CUBLAS_COMPUTE_32F` mode on Ampere+ opts into TF32 automatically for eligible shapes; requesting
`CUBLAS_COMPUTE_32F_PEDANTIC` disables the TF32 shortcut and forces full FP32 accumulation.

**Rule of thumb:** if a numerical-correctness test or a scientific-computing kernel that used to
pass on Volta/Turing starts failing an `atol`/`rtol` check after moving to an Ampere+ box, check
TF32 first — it is often the entire explanation, and it is on by default, silently.

---

## 5. Sources of Nondeterminism

Nondeterminism means **the same inputs on the same hardware produce different bit patterns on
different runs** — a strictly stronger and more debugging-hostile problem than the
CPU-vs-GPU mismatch in §2 (which is *consistent*, just different from the CPU). The common
causes, roughly ordered by how often they bite in practice:

1. **`atomicAdd` accumulation order.** Thousands of threads racing to add into one memory
   location via `atomicAdd` are serialized by the hardware, but the *order* in which they arrive
   is not fixed — it depends on scheduling, which can vary run to run. Because floating-point
   addition is not associative (§2), a different arrival order produces a different low-order-bit
   result.

   ```cpp
   // NONDETERMINISTIC: 100k threads atomicAdd into one accumulator in
   // scheduling-dependent (i.e. run-to-run varying) order.
   __global__ void sum_atomic(const float* x, float* out, int n) {
       int i = blockIdx.x * blockDim.x + threadIdx.x;
       if (i < n) atomicAdd(out, x[i]);       // order of arrival is not fixed
   }

   // DETERMINISTIC: fixed-topology tree reduction — every run sums the same
   // pairs in the same order, so the result is bit-identical run over run
   // (still may differ from a CPU sequential sum, per Section 2).
   __global__ void sum_tree(const float* x, float* out, int n) {
       extern __shared__ float sdata[];
       int tid = threadIdx.x;
       int i = blockIdx.x * blockDim.x + threadIdx.x;
       sdata[tid] = (i < n) ? x[i] : 0.0f;
       __syncthreads();
       for (int s = blockDim.x / 2; s > 0; s >>= 1) {   // fixed halving order
           if (tid < s) sdata[tid] += sdata[tid + s];
           __syncthreads();
       }
       if (tid == 0) atomicAdd(out, sdata[0]);   // only one atomic per block: fixed
                                                   // block count -> fixed set of partial
                                                   // sums, but their arrival order across
                                                   // blocks is still nondeterministic;
                                                   // true determinism needs a fixed-order
                                                   // final combine (e.g. write per-block
                                                   // partials and sum them sequentially).
   }
   ```
   Note the comment in `sum_tree`: eliminating nondeterminism *within* a block is easy (fixed
   tree), but the final cross-block combine still needs a fixed-order pass (write partials to an
   array indexed by `blockIdx.x`, then sum that array sequentially or with another fixed tree) to
   be fully deterministic end to end.

2. **Some cuDNN/cuBLAS algorithms are inherently non-deterministic.** Certain convolution and
   GEMM algorithms (particularly ones that use atomic-based reductions across thread blocks
   internally, and some Winograd convolution variants) trade determinism for speed by design.
   cuDNN exposes both — you must explicitly request the deterministic variant.

3. **Non-deterministic reductions in library primitives.** `cub::DeviceReduce`,
   Thrust reductions, and multi-GPU NCCL all-reduce can select tree topologies or ring-vs-tree
   algorithms based on runtime heuristics (buffer size, GPU count, link topology), which can
   change the summation order between runs or between different NCCL versions/topologies even
   with identical input data.

4. **Uninitialized memory / race-condition bugs masquerading as "precision issues."** A genuine
   data race (missing `__syncthreads()`, a read-after-write hazard) produces run-to-run varying
   *garbage*, not just varying rounding — always rule this out with
   `compute-sanitizer --tool racecheck` before assuming a numerics issue is "just floating point."
   See [`../../debugging_correctness_and_numerics/README.md`](../../debugging_correctness_and_numerics/README.md).

---

## 6. Making Kernels Reproducible

Full reproducibility costs performance — deterministic algorithms are usually slower than the
non-deterministic ones they replace, because they give up freedom to reorder work. Enable it only
where the requirement demands it (regulatory audit trail, bit-exact CI regression test, debugging
a training divergence), not by default in production.

**In raw CUDA:**
- Replace scattered `atomicAdd` accumulation with a fixed-topology reduction (tree within a
  block, then a fixed-order sequential or tree combine across blocks — §5's `sum_tree` pattern).
- Fix the grid/block configuration. Occupancy-driven, input-size-dependent launch configs that
  change the number of partial sums between runs break determinism even with a "fixed" reduction
  tree, because the tree shape itself changes.
- Prefer `cub::DeviceReduce` and Thrust's algorithms only when their documentation states a
  deterministic guarantee for your CUDA/library version — do not assume it.

**In PyTorch / cuDNN / cuBLAS (the common case in practice):**

```python
import os
# Must be set BEFORE the CUDA context / cuBLAS handle is created — a workspace
# config cuBLAS needs to guarantee deterministic algorithm selection.
os.environ["CUBLAS_WORKSPACE_CONFIG"] = ":4096:8"   # or ":16:8" (smaller, slower)

import torch

torch.manual_seed(0)                                  # fix RNG for weight init / dropout / shuffling
torch.use_deterministic_algorithms(True)              # raises RuntimeError if any op has no
                                                       # deterministic implementation, instead of
                                                       # silently running the nondeterministic one
torch.backends.cudnn.deterministic = True             # forces cuDNN to pick deterministic conv algos
torch.backends.cudnn.benchmark = False                # benchmark mode profiles multiple algorithms
                                                       # and its choice can vary run to run — must
                                                       # be off for determinism
torch.backends.cuda.matmul.allow_tf32 = False          # optional: also remove TF32's own precision
torch.backends.cudnn.allow_tf32 = False                # loss from the comparison (see Section 4)
```

`torch.use_deterministic_algorithms(True)` is the load-bearing call: it makes PyTorch **fail
loudly** (a clear `RuntimeError` naming the offending op) rather than silently returning a
nondeterministic result, which turns "why doesn't my run reproduce" from a days-long bisection
into an immediate, actionable error at the exact call site.

**Cost to expect:** deterministic cuDNN convolution algorithms are commonly 10-30% slower than
the fastest available non-deterministic algorithm for the same shape, and `CUBLAS_WORKSPACE_CONFIG`
forces cuBLAS to skip some larger, faster workspace-hungry GEMM algorithms. Budget for this in any
CI gate that also checks wall-clock performance.

---

## 7. Fast-Math Tradeoffs — Summary

| Choice | Speed | Precision / determinism cost | When to use |
|--------|-------|-------------------------------|-------------|
| FMA contraction (`--fmad=true`, default) | faster (fewer instructions) | usually *more* accurate per-op (single rounding), but diverges from a non-fused reference | Always, unless bit-matching a naive reference |
| `--use_fast_math` | up to several x on transcendental-heavy kernels | approximate `sin`/`cos`/`exp`/`log`/`div`/`sqrt`, denormals flushed to zero | Throughput-critical kernels where the error budget has been validated end-to-end (e.g. inside a normalization whose output is itself re-normalized) |
| TF32 (Ampere+ default) | ~4-8x FP32 CUDA-core throughput on eligible GEMMs | mantissa drops from 23 to 10 bits (~7 to ~3 decimal digits) | Training/inference where the model's own noise floor dwarfs a 3-digit rounding error (the common case) — opt out (Section 4) for scientific/finance-grade FP32 correctness |
| Deterministic algorithms (`torch.use_deterministic_algorithms`, fixed-tree reductions) | 10-30%+ slower | none beyond speed — this is the "give up speed to get reproducibility" trade | Regulatory audit trails, bit-exact regression CI, bisecting a training divergence |
| BF16 vs FP16 mixed precision | comparable to each other | BF16: less mantissa precision, no loss scaling needed; FP16: more mantissa precision, needs loss scaling to avoid gradient underflow | BF16 default on Ampere+/LLM training; FP16 still common on older GPUs (Volta/Turing lack native BF16 Tensor-Core support) |

---

## 8. Pitfalls

- **BROKEN: assuming a GPU/CPU mismatch is a bug and chasing it as one.** A relative difference
  at the 1e-5-1e-7 scale (FP32) between a CUDA kernel and a scalar CPU reference is expected from
  FMA contraction and reduction-order differences (§2) — comparing with `==` instead of
  `allclose(rtol=1e-5, atol=1e-8)` (or the FP16/BF16-appropriate looser tolerance) manufactures a
  "bug" out of correct IEEE-754 behavior.
  **FIX:** always compare with an explicit, precision-appropriate tolerance; use `--fmad=false`
  only as a temporary debugging aid to isolate whether contraction is the source of a difference,
  never as the production build.

- **BROKEN: silently training/serving at TF32 precision without realizing it.** A model ported
  from a Volta box (no TF32 hardware) to an A100/H100 gets a free 4-8x GEMM speedup and a
  simultaneous, unannounced mantissa cut from 23 to 10 bits — teams have chased "accuracy
  regressions after a GPU upgrade" for days before finding TF32 was the cause.
  **FIX:** treat `torch.backends.cuda.matmul.allow_tf32` (and the cuDNN equivalent) as an explicit
  decision to record in the experiment config, not an invisible default.

- **BROKEN: calling a training run "flaky" when it is a `benchmark=True` + non-deterministic-op
  interaction.** `torch.backends.cudnn.benchmark = True` profiles several convolution algorithms
  per shape and picks the fastest — a legitimate and usually desirable optimization — but combined
  with algorithms that are themselves non-deterministic, it produces run-to-run divergence that
  looks like a race condition.
  **FIX:** when reproducibility is required, set `benchmark = False` and
  `torch.use_deterministic_algorithms(True)` together; don't debug one without checking the other.

- **BROKEN: treating a genuine data race as "just floating-point imprecision."** A missing
  `__syncthreads()` before a shared-memory reduction reads produces silently wrong, run-to-run
  *varying* values that look exactly like a rounding-order nondeterminism problem from the
  outside.
  **FIX:** run `compute-sanitizer --tool racecheck` (and `--tool initcheck` for uninitialized
  reads) before spending time on a numerics investigation — rule out races first, since the
  symptom is identical to §5's legitimate nondeterminism sources.

---

## 9. Q&A

**Why does my CUDA kernel give a different answer than my CPU reference implementation for the
same input?** Because FMA contraction gives a single rounding instead of two, and a parallel
reduction sums the same numbers in a different (tree) order than a sequential CPU loop — floating
point addition is not associative, so both differences are expected, not bugs; compare with a
tolerance, not equality.

**Why did my model's accuracy change after moving from a V100 to an A100 with no code changes?**
Ampere+ defaults FP32 matrix multiplies through the TF32 Tensor-Core path, silently cutting the
mantissa from 23 to 10 bits; set `torch.backends.cuda.matmul.allow_tf32 = False` (and the cuDNN
equivalent) to restore full FP32 precision if the accuracy delta matters.

**What is the actual difference between FP16 and BF16 if both are 16 bits?** FP16 spends more
bits on mantissa (10 vs 7, more precision) and fewer on exponent (5 vs 8), so it has a narrower
dynamic range than FP32 and needs loss scaling to avoid gradient underflow; BF16 keeps FP32's
full exponent range at the cost of precision, which is why it needs no loss scaling and became
the default for large-model training.

**How do I make an `atomicAdd`-based reduction reproducible across runs?** You generally cannot
make raw scattered `atomicAdd` deterministic — replace it with a fixed-topology tree reduction
(fixed intra-block tree, then a fixed-order sequential or tree combine of the per-block partial
sums) so the same set of additions happens in the same order every run.

**What does `torch.use_deterministic_algorithms(True)` actually guarantee?** It makes every op
either use a deterministic implementation or raise a `RuntimeError` naming the offending op — it
does not silently fall back to a nondeterministic path, which is what makes it useful for
bisecting reproducibility bugs instead of masking them.

**Is `--use_fast_math` safe to use everywhere for a speed boost?** No — it swaps precise
transcendental intrinsics for lower-accuracy approximations (up to ~2^-11 relative error) and
flushes denormals to zero, which is fine for a final activation function but can compound
dangerously if used upstream of a sensitive normalization (e.g. a softmax denominator feeding a
loss); validate the error budget end-to-end before adopting it in a numerically sensitive path.

---

## Cross-References

- [`../../tensor_cores_and_mixed_precision/README.md`](../../tensor_cores_and_mixed_precision/README.md) — WMMA/`mma` fragments, cuBLAS/cuDNN Tensor-Core code paths, loss scaling for FP16 training.
- [`../../synchronization_and_atomics/README.md`](../../synchronization_and_atomics/README.md) — `atomicAdd`/CAS mechanics, memory fences, and the contention costs behind Section 5's nondeterminism sources.
- [`../../debugging_correctness_and_numerics/README.md`](../../debugging_correctness_and_numerics/README.md) — `compute-sanitizer` (`racecheck`/`initcheck`) for ruling out races before chasing a "precision" bug.
