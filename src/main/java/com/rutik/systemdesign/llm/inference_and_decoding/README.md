# Inference & Decoding

## Deep Dive Files

| File | Topic |
|------|-------|
| [constrained_decoding_and_structured_outputs.md](constrained_decoding_and_structured_outputs.md) | Logit masking, FSM/CFG grammar compilation, XGrammar/llguidance internals, jump-forward decoding, provider structured outputs, quality tradeoffs |
| [sampling_and_decoding_strategies.md](sampling_and_decoding_strategies.md) | Min-p/typical/eta/epsilon/Mirostat sampling, repetition/presence/frequency penalties, DRY, contrastive search vs. contrastive decoding, XTC, beam search, sampler-ordering gotcha, determinism |
| [kv_cache_optimization.md](kv_cache_optimization.md) | KV cache memory formula and capacity planning, GQA/MQA/MLA impact, KV quantization impact, H2O/SnapKV/StreamingLLM/Scissorhands eviction, cross-layer KV sharing (YOCO/CLA) |
| [speculative_decoding.md](speculative_decoding.md) | Rejection-sampling exactness proof, EAGLE/EAGLE-2/EAGLE-3, Medusa, lookahead/Jacobi decoding, prompt-lookup/ngram decoding, self-speculative/LayerSkip, DeepSeek-V3 MTP, production tuning |

---

## 1. Concept Overview

LLM inference is the process of generating tokens from a trained model. Unlike training (which is parallelizable across the sequence), inference is inherently sequential — each token can only be generated after the previous one. This fundamental constraint drives most of the complexity in LLM serving systems.

Understanding inference mechanics is critical for system design: the KV cache, batching strategies, speculative decoding, and quantization are all responses to the unique computational characteristics of autoregressive generation. Getting inference right is the difference between a model that costs $10/hour to serve and one that costs $1,000/hour.

---

## 2. Intuition

> **One-line analogy**: LLM inference is like a chef cooking one dish at a time — each plate (token) must finish before the next starts, so you optimize by keeping the kitchen (GPU) constantly busy with multiple orders.

**Mental model**: Training is parallel — process the whole sequence at once. Inference is sequential — generate one token, feed it back, generate the next. Each token requires a full model forward pass (loading 140GB of weights). The bottleneck isn't the math; it's loading weights from GPU memory. Solutions: batch many users together (amortize the weight load), cache intermediate results (KV cache), use small draft models to speculatively generate multiple tokens (speculative decoding), and manage memory efficiently (PagedAttention).

**Why it matters**: Inference is where 90%+ of LLM compute cost occurs post-training. Getting inference right means serving 10× more users at 10× less cost. The gap between naive serving (50 tok/s, $25/hr) and optimized serving (600 tok/s, $2/hr) is entirely engineering.

**Key insight**: The fundamental constraint is that decoding is memory-bandwidth bound, not compute bound — GPUs are 150× better at compute than memory bandwidth for LLM decode. Every optimization targets reducing or amortizing memory access.

---

## 3. Core Principles

- **Sequential bottleneck**: Each output token depends on all previous tokens — inference can't be parallelized across tokens.
- **Two phases**: Prefill (process input tokens, one forward pass) and decode (generate output tokens one at a time).
- **Memory bandwidth bound**: For most models, GPU memory bandwidth is the bottleneck during decoding, not compute.
- **Batching is key**: Sharing computation across multiple requests dramatically improves throughput.
- **KV cache**: The key-value tensors computed during prefill can be cached to avoid recomputation during decoding.

---

## 4. Concepts

### 4.1 Autoregressive Generation

```
Prefill phase: Process all input tokens in parallel
  Input: "The capital of France is"
  → One forward pass through transformer
  → Produces KV cache for each layer
  → Generates first output token

Decode phase: Generate one token at a time
  Token 1: "Paris"  → KV cache updated
  Token 2: "."      → KV cache updated
  Token 3: EOS      → stop

Latency decomposition:
  TTFT (Time To First Token) = prefill time (proportional to input length)
  TPOT (Time Per Output Token) = decode time per token
  Total latency = TTFT + TPOT × output_length
```

**The idea behind it.** "You wait once for the model to read your prompt, then you wait a little bit again for every single word it writes — so total time is one fixed charge plus a per-token meter."

Keeping the two terms separate is the point. They scale off different inputs (input length vs output length), sit in different bottleneck regimes (prefill is compute-bound, decode is memory-bound), and have completely different fixes. A single "latency" number averages away the only information you could have acted on.

| Symbol | What it actually is |
|--------|---------------------|
| `TTFT` | Prefill wall-clock. Scales with *input* length. What the user experiences as "is it broken?" |
| `TPOT` | Decode cost of one token. Set by model size, batch, and bandwidth — not by input length |
| `output_length` | Tokens generated. Usually the term nobody budgets for, and the one that dominates |
| `1 / TPOT` | Reading speed in tokens/sec. 30 ms/token → 33 tok/s, comfortably above human reading |

**Walk one example with real numbers.** Two requests against the same 70B deployment:

```
  Short chat turn:      TTFT = 280 ms,  TPOT = 20 ms,  output =   150 tokens
    total = 280 ms + (20 ms x 150)   =  280 + 3,000    =  3,280 ms
    split:  TTFT is  8.5% of the wait, decode is 91.5%

  RAG answer over a long document:  TTFT = 1,800 ms,  TPOT = 20 ms,  output = 150
    total = 1,800 ms + (20 ms x 150) = 1,800 + 3,000   =  4,800 ms
    split:  TTFT is 37.5% of the wait, decode is 62.5%

  Same TPOT fix (-5 ms/token) applied to each:
    short chat:  280 + (15 x 150) = 2,530 ms   ->  23% faster
    RAG:       1,800 + (15 x 150) = 4,050 ms   ->  16% faster

  Same TTFT fix (prompt caching, 1,800 -> 200 ms) applied to each:
    short chat:  no change worth measuring
    RAG:         200 + 3,000      = 3,200 ms   ->  33% faster
```

The two levers pay off on opposite workloads, which is why Section 13 insists on monitoring them separately. Optimize the wrong one and you ship a change that measures as nothing.

### 4.2 Sampling Strategies

The sampler turns a logit vector into a token. Core knobs:

- **Greedy** (`argmax(logits)`): deterministic, fastest, but repetitive — use for factual/structured tasks.
- **Temperature**: `logits / T` before softmax. T<1 sharpens the distribution (more conservative), T>1 flattens it (more random). T=0 is special-cased to greedy, not a literal division by zero.
- **Top-k**: keep only the k highest-probability tokens — a fixed-count cut that doesn't adapt to how peaked or flat the distribution is.
- **Top-p (nucleus)**: keep the smallest set of tokens whose cumulative probability ≥ p (p≈0.9 typical) — adaptive to distribution mass, the most common production default.
- **Min-p**: keep tokens with probability ≥ min_p × max_token_prob (min_p≈0.05) — thresholds *relative to the mode*, so it tightens on peaked distributions and widens on flat ones using the same parameter.

**Temperature — reading it in plain English.** "Divide every logit by T before the softmax: shrinking T stretches the gaps between candidates so the leader runs away with the vote; growing T squeezes the gaps so underdogs get a real chance."

The reason this works is that softmax is exponential, not linear. A gap of 2.0 logits becomes a gap of 2.9 logits at T=0.7 and only 1.33 logits at T=1.5 — so temperature is a *contrast* knob on the distribution, not a coin-flip randomness knob bolted on afterwards.

| Symbol | What it actually is |
|--------|---------------------|
| `z_i` | The raw logit for token `i` — the model's unnormalized score, any real number |
| `T` | The divisor. T<1 sharpens; T=1 leaves the model's own distribution; T>1 flattens |
| `softmax(z)_i` | `exp(z_i) / Σ_j exp(z_j)` — turns arbitrary scores into probabilities summing to 1 |
| `Σ_j` | Add up the term for every token `j` in the vocabulary (~128,000 of them) |
| `p_i` | Final probability the sampler draws token `i` with |

**Walk one example with real numbers.** Four candidate tokens after "The capital of France is":

```
  token        logit z_i    T=0.7            T=1.0            T=1.5
  ---------    ---------    -------------    -------------    -------------
  Paris          4.0        z/T =  5.714     z/T =  4.000     z/T =  2.667
  Lyon           2.0        z/T =  2.857     z/T =  2.000     z/T =  1.333
  Nice           1.5        z/T =  2.143     z/T =  1.500     z/T =  1.000
  Berlin         1.0        z/T =  1.429     z/T =  1.000     z/T =  0.667

  exp() of each:
  Paris                          303.1            54.60            14.39
  Lyon                            17.41            7.389            3.793
  Nice                             8.524           4.482            2.718
  Berlin                           4.174           2.718            1.948
  sum                            333.2            69.19            22.85

  p_i = exp / sum:
  Paris                            0.910            0.789            0.630
  Lyon                             0.052            0.107            0.166
  Nice                             0.026            0.065            0.119
  Berlin                           0.013            0.039            0.085

  Paris:Lyon odds                17.5 : 1         7.4 : 1          3.8 : 1
```

Same logits, three different worlds. T=0 is not a division at all — it is special-cased to `argmax`, because dividing by zero would be undefined; conceptually it is the T→0 limit where Paris gets probability 1.000.

**Top-k, top-p, and min-p — reading them in plain English.** All three answer the same question, "which tokens are even allowed to be drawn?", and differ only in how they draw the line:

> **Top-k**: "keep the k best, no matter what their probabilities look like."
> **Top-p**: "sort descending, keep adding tokens until you have covered p of the total probability mass, then stop."
> **Min-p**: "keep every token worth at least a fraction `min_p` of what the *front-runner* is worth."

| Symbol | What it actually is |
|--------|---------------------|
| `k` | Fixed count of survivors. Typical 40-50. Ignores the shape of the distribution |
| `p` | Cumulative-probability target for the nucleus. Typical 0.9-0.95 |
| `Σ_{i≤m} p_i` | Running total down the sorted list — the "cumulative" column |
| `m*` | Smallest cut point where the running total first reaches `p`. The nucleus is tokens 1..m* |
| `min_p` | Relative floor, typical 0.05. Threshold = `min_p × p_max`, so it moves with the distribution |
| `p_max` | Probability of the single most likely token — the front-runner the floor is scaled against |

**Walk one example with real numbers.** One decode step, vocabulary sorted descending, p=0.9, k=2, min_p=0.05:

```
  rank  token        p_i      cumulative Σ    top-p (0.9)?   top-k (k=2)?   min-p (0.05)?
  ----  ---------   ------    ------------    ------------   ------------   -------------
   1    Paris        0.62         0.62        keep           keep           keep
   2    Lyon         0.14         0.76        keep           keep           keep
   3    Nice         0.09         0.85        keep           CUT            keep
   4    Marseille    0.06         0.91  <- crosses 0.9       CUT            keep
   5    Toulouse     0.04         0.95        CUT            CUT            keep
   6    Nantes       0.02         0.97        CUT            CUT            CUT
  7..N  long tail    0.03         1.00        CUT            CUT            CUT

  top-p:  m* = 4, because 0.85 < 0.90 <= 0.91.  Nucleus = {Paris, Lyon, Nice, Marseille}
          renormalize by 0.91:  0.681 / 0.154 / 0.099 / 0.066   (sums to 1.000)

  top-k:  keeps 2 tokens regardless -- it never even looks at the 0.62/0.14 gap

  min-p:  threshold = 0.05 x p_max = 0.05 x 0.62 = 0.031
          keep every p_i >= 0.031  ->  ranks 1-5.  Nantes at 0.02 falls below
```

Notice all three cut points differ on the *same* distribution: 4, 2, and 5 tokens. That is the entire argument between the samplers. Now flatten the distribution — say `p_max` drops to 0.10 because the model is genuinely unsure. Top-k still keeps exactly 2 (too narrow, the model had 30 reasonable options). Top-p keeps whatever it takes to reach 0.9, which could be 60 tokens including junk. Min-p's threshold auto-drops to `0.05 × 0.10 = 0.005`, widening in proportion — which is why it is the same parameter that works on both peaked and flat steps.

**Why renormalization exists.** After truncation the surviving probabilities sum to 0.91, not 1.0. Dividing each by 0.91 restores a valid distribution. Skip it and your sampler has a 9% chance of drawing nothing, or — depending on implementation — silently biases toward whichever token the RNG's fallback branch picks.

**Repetition and frequency penalties — reading them in plain English.** Two different formulas that get confused constantly:

```
  Repetition penalty (CTRL-style, MULTIPLICATIVE, applied to logits):
    z_i' = z_i / r     if z_i > 0
    z_i' = z_i x r     if z_i <= 0            (for any token i already seen)

  Frequency + presence penalty (OpenAI-style, ADDITIVE, applied to logits):
    z_i' = z_i - alpha_presence x 1[count_i > 0] - alpha_frequency x count_i
```

| Symbol | What it actually is |
|--------|---------------------|
| `r` | Repetition penalty, typical 1.05-1.2. `r=1.0` disables it. Multiplicative |
| `count_i` | How many times token `i` already appeared in the generated text so far |
| `1[count_i > 0]` | 1 if the token has appeared at all, else 0. Flat one-time charge |
| `alpha_presence` | Fixed subtraction for *having appeared*, typical 0-1.0. Encourages new topics |
| `alpha_frequency` | Subtraction *per occurrence*, typical 0-1.0. Escalates with each repeat |

**Walk one example with real numbers.** Token `" the"` currently has logit 3.0 and has already appeared 4 times:

```
  Repetition penalty, r = 1.2:
    z' = 3.0 / 1.2 = 2.500        <- one flat haircut, no matter how many repeats

  Frequency + presence, alpha_presence = 0.5, alpha_frequency = 0.3:
    presence term  = 0.5 x 1        = 0.5      (it has appeared, so charge once)
    frequency term = 0.3 x 4        = 1.2      (charge again for every occurrence)
    z' = 3.0 - 0.5 - 1.2            = 1.300    <- escalates with the 5th, 6th, ... repeat

  Effect on a 2-token race against "a" at logit 2.8 (never seen):
    no penalty         " the" 3.0 vs "a" 2.8  ->  " the" wins
    repetition r=1.2   " the" 2.5 vs "a" 2.8  ->  "a" wins, narrowly
    freq+presence      " the" 1.3 vs "a" 2.8  ->  "a" wins decisively
```

**Why the sign branch in the repetition penalty exists.** Dividing a *negative* logit by `r>1` makes it less negative — i.e. it would *reward* the repeated token. The `z_i <= 0 → z_i × r` branch flips to multiplication so the penalty always pushes the logit down. Implementations that forget the branch produce the notorious bug where turning up `repetition_penalty` makes a model repeat itself *more*.

→ **Deep dive**: [Sampling & Decoding Strategies](sampling_and_decoding_strategies.md) covers the sampler-ordering gotcha (why identical temperature/top_p values produce different output across inference engines), repetition/presence/frequency penalties, no-repeat-ngram, the DRY sampler, contrastive search vs. contrastive decoding, typical/eta/epsilon/tail-free/Mirostat/XTC sampling, beam search mechanics and cost, and determinism/batch-invariance.

### 4.3 KV Cache — Internals & Memory

The KV cache stores the K and V tensors computed during prefill so that decode only computes K/V for the *new* token and attends over the cached rest — turning O(n²) redundant recomputation into O(n) total work (see Section 4.7 for the Q/K/V asymmetry that makes this possible).

**Canonical memory formula:**
```
2 (K+V) × num_layers × num_kv_heads × head_dim × bytes_per_element

LLaMA 3 70B (80 layers, 8 KV heads via GQA, head_dim=128, BF16):
  Per token: 2 × 80 × 8 × 128 × 2 = 327,680 bytes ≈ 320 KB

  At 8K context, 1 request:    2.56 GB
  At 32K context, 1 request:   10.2 GB
  At 128K context, 1 request:  40.9 GB
  At 128K context, 10 users:   409 GB ← requires ~6× H100 just for KV cache
```

This is why KV cache, not model weights, is the **primary memory bottleneck** in production LLM serving.

**Stated plainly.** "For every token you have ever seen, every layer had to stash two vectors — one key, one value — per KV head. Multiply that stash by how many tokens and how many concurrent requests you are holding, and that is the GPU memory you can never get back until the request finishes."

The framing that matters in an interview: weights are a *fixed* cost you pay once, KV cache is a *per-token, per-user* cost that grows linearly in both. Weights set your minimum GPU count; KV cache sets your maximum concurrency. Interviewers ask this because it is the one calculation that decides how many users a box actually serves.

| Symbol | What it actually is |
|--------|---------------------|
| `batch` | Concurrent requests holding cache right now. Not requests/sec — requests *in flight* |
| `seq_len` | Tokens cached for that request = prompt + tokens generated so far. Grows every step |
| `L` | Transformer blocks. Each keeps its own independent KV — 80 for LLaMA 3 70B |
| `H_kv` | K/V heads per layer. **Not** the attention-head count once GQA/MQA is in play |
| `d_head` | Width of one head's vector, typically 128. `H_attn × d_head = d_model` |
| `× 2` | One K tensor **plus** one V tensor. The most-forgotten factor in the whole formula |
| `bytes` | 2 for FP16/BF16, 1 for FP8/INT8, 0.5 for INT4. The quantization lever |

**Walk one example with real numbers.** Lay the multiplication out vertically with units and there is nowhere to hide an error. LLaMA 2 13B (MHA, no GQA) on one A100 80GB:

```
  KV bytes = batch x seq_len x L x H_kv x d_head x 2 x bytes

    batch          16     concurrent requests
    seq_len     4,096     tokens cached per request
    L              40     transformer layers
    H_kv           40     KV heads per layer   (MHA: H_kv = H_attn = 40)
    d_head        128     elements per head
    x 2                   one K tensor + one V tensor
    bytes           2     FP16

  Step 1 -- per token, per request (drop batch and seq_len):
    2 x 40 x 40 x 128 x 2 bytes
      = 2 x 40                    =        80
      x 40                        =     3,200
      x 128                       =   409,600
      x 2 bytes                   =   819,200 bytes
                                  =       800 KB per token

  Step 2 -- one full request at 4K context:
    800 KB/token x 4,096 tokens   = 3,276,800 KB
                                  =     3,200 MB
                                  =     3.125 GB per request

  Step 3 -- the whole batch:
    3.125 GB x 16 requests        =      50.0 GB of KV cache

  Step 4 -- does it fit on one 80 GB A100?
    weights   13B x 2 bytes       =      26.0 GB
    KV cache                      =      50.0 GB
    activations + fragmentation   =    ~  4.0 GB
    total                         =      80.0 GB   <- exactly at the edge; batch 17 OOMs
```

Now the same walk for a 7B, which is the config interviewers reach for most often. LLaMA 2 7B: L=32, H_kv=32, d_head=128, FP16:

```
  per token   = 2 x 32 x 32 x 128 x 2 bytes  = 524,288 bytes = 512 KB
  4K context  = 512 KB x 4,096               = 2.0 GB per request
  weights     = 7B x 2 bytes                 = 14.0 GB

  On one 80 GB A100:   (80 - 14 - 4) / 2.0 GB  = 31 concurrent 4K requests
  Same card at 32K:    per request = 512 KB x 32,768 = 16 GB  ->  only 3 requests
```

**Why GQA shows up in this formula and not anywhere else.** `H_kv` is the only term a model architect can shrink without touching quality much. Compare the 13B above against the LLaMA 3 70B already computed in this section: 70B is *bigger* in every other term (80 layers vs 40) yet its per-token cost is **320 KB against the 13B's 800 KB** — because GQA cuts `H_kv` from 64 attention heads down to 8 shared KV heads, an 8× saving that swamps the 2× layer increase. Remove GQA from the formula and 70B would cost 2.56 MB/token, making 128K context economically impossible. Every other lever (INT8 KV → `bytes` 2→1, eviction → `seq_len`, PagedAttention → removes the padding waste *around* the formula) attacks one of the other terms.

**The interview trap.** Candidates multiply by attention heads instead of KV heads on a GQA model and land 8× too high, or drop the `× 2` and land 2× too low. Say the terms aloud in order — "batch, sequence, layers, KV heads, head dim, times two for K and V, times bytes" — and both errors disappear.

→ **Deep dive**: [KV Cache Optimization](kv_cache_optimization.md) covers the full capacity-planning formula, how GQA/MQA/MLA shrink this formula's `num_kv_heads` term (with pointers to the attention-mechanism derivations), KV cache quantization (INT8/FP8/KIVI), eviction strategies (H2O, SnapKV, StreamingLLM, Scissorhands — see also Section 4.10), and cross-layer KV sharing (YOCO, CLA).

### 4.4 Continuous Batching (PagedAttention)

**Problem with naive batching:**
```
Request A: input=100 tokens, output=50 tokens  → done at step 50
Request B: input=200 tokens, output=500 tokens → done at step 500

Naive batch: A and B together from step 1 to step 500
  A is done at step 50 but GPU keeps allocating memory for it until B finishes
  New Request C can't start until the batch finishes
```

**Continuous batching (used by vLLM):**
```
Step 1-50:  Process [A, B] together
Step 50:    A finishes → immediately add Request C to the batch
Step 51+:   Process [B, C] together
No wasted compute; new requests fill slots as old ones complete
```

**What the formula is telling you.** "Throughput is just how many sequences are alive in the batch divided by how long one step takes — and since a decode step costs the same whether one slot or sixty-four are filled, every empty slot is throughput you paid for and threw away."

```
  Slot utilization (naive, batch-level scheduling):

                  sum of tokens actually generated
    U_naive  =  -------------------------------------
                 batch_size x longest_output_in_batch

  Throughput:   tokens/sec = batch_size x U x (1 / step_time)
```

| Symbol | What it actually is |
|--------|---------------------|
| `B` | Sequence slots the engine runs in one forward pass |
| `U` | Fraction of slot-steps doing real work rather than idling on a finished request |
| `step_time` | Wall-clock for one decode iteration. Roughly constant below the ridge point |
| `L_max` | Longest output in the batch — the request everyone else waits on under naive batching |
| `1 / step_time` | Steps per second. 70 ms/step → 14.3 steps/sec |

**Walk one example with real numbers.** Four requests with outputs 50, 120, 300, and 2,000 tokens; 70B on an A100 with a 70 ms decode step:

```
  Naive batching (batch completes as a unit):
    useful token-steps   =  50 + 120 + 300 + 2,000     =  2,470
    slot-steps paid for  =  4 slots x 2,000 steps      =  8,000
    U_naive              =  2,470 / 8,000              =  0.31   (31%)

    throughput = 4 x 0.31 x (1 / 0.070 s) = 4 x 0.31 x 14.3 =  17.7 tokens/sec

  Continuous batching (finished slots refill at the next iteration boundary):
    request A frees its slot at step   50, E enters at step   51
    request B frees its slot at step  120, F enters at step  121
    request C frees its slot at step  300, G enters at step  301
    U_cont               =  ~0.90   (only scheduler gaps and the tail are lost)

    throughput = 4 x 0.90 x 14.3                       =  51.5 tokens/sec

  Ratio from scheduling alone:  0.90 / 0.31            =  2.9x
```

That 2.9× is the *scheduling* half of the story and is exactly the "utilization from roughly 30% to 90%" figure quoted in Section 12. The remaining gap to vLLM's headline 24× comes from PagedAttention raising `B` itself: cutting KV fragmentation from 60-80% waste down to under 4% lets the same GPU hold roughly 8× more concurrent sequences, and `2.9 × 8 ≈ 23`. The two optimizations multiply because one fixes *time* wasted and the other fixes *space* wasted — which is why "continuous batching alone gets you 24×" is a wrong answer.

**PagedAttention** (vLLM innovation — see [vLLM Deep Dive](../vllm_deep_dive/README.md) for scheduler and block-manager internals):
```
KV cache is managed like OS virtual memory
Physical GPU memory divided into "blocks" (pages)
  Each block: 16 contiguous tokens × KV tensors for all layers
  Block size tunable (default: 16 tokens)

Logical blocks assigned on demand as sequence grows
Non-contiguous physical memory → contiguous logical view
Benefits:
  - Near-zero KV cache fragmentation
  - Share KV cache across parallel sampling (same prefix → same blocks)
  - Enable preemption: swap out KV cache to CPU when GPU full
```

### 4.5 Speculative Decoding

A small draft model proposes K tokens; the large target model verifies all K in a single forward pass (parallel, like prefill) and accepts the matching prefix, generating a correction at the first mismatch. Net: 2-4 tokens produced per target forward pass instead of 1 — and because the accepted/corrected tokens are chosen via rejection sampling, the output distribution is **provably identical** to pure target decoding (no quality tradeoff, only throughput).

```
Acceptance rate α (probability draft token matches target) drives the speedup:
  α=0.90 → E[accepted]≈3.5 tokens/pass | α=0.75 → ≈2.6 | α=0.60 → ≈2.1 | α=0.50 → ≈1.7
  Break-even ≈ α=0.45 for K=4 draft tokens (below this, draft overhead exceeds gains)

Practical: code α≈0.75-0.90 (use it) | chat α≈0.60-0.75 (marginal) | creative writing α≈0.40-0.55 (usually not worth it)
```

**What this actually says.** "Each drafted token is a coin flip that lands 'accept' with probability α, and the run ends at the first tails — so the tokens you harvest per target pass is just the expected length of a winning streak, capped at how many tokens you bothered to draft."

That framing explains the shape of every number in the table above. Streaks are geometric, so the *first* drafted token is worth a lot and the *fifth* is worth almost nothing — drafting more is subject to hard diminishing returns while its cost stays strictly linear. Speculative decoding is a bet on streak length, and α is the only thing that sets the odds.

```
  Expected tokens produced per target forward pass (geometric streak, capped at gamma):

                    1 - alpha^(gamma+1)
    E[tokens]  =  -----------------------
                        1 - alpha

  Net speedup, once you charge for the draft passes:

                       E[tokens]
    Speedup    =  ---------------------
                    1 + gamma x c
```

| Symbol | What it actually is |
|--------|---------------------|
| `alpha` (α) | Acceptance rate — probability one drafted token survives verification. 0 to 1 |
| `gamma` (γ) | Draft length, how many tokens you speculate per round. Called K elsewhere in this file |
| `alpha^(gamma+1)` | Probability the *entire* draft is accepted; shrinks fast, which is why long drafts stop helping |
| `1 / (1 - alpha)` | The ceiling. Infinite drafting can never beat this — the streak's expected length |
| `c` | Draft cost as a fraction of one target pass. A 1B draft against a 70B target is ~0.1 |
| `1 + gamma x c` | Total cost of a round: one target verify pass plus gamma draft passes |
| `E[tokens]` | Average accepted tokens harvested per target pass. Numerator of the win |

**Walk one example with real numbers.** α = 0.70 (roughly the 0.71 code acceptance rate measured in the Section 14 case study), draft cost c = 0.1:

```
  Numerator first -- powers of alpha = 0.70:
    0.70^2 = 0.490    0.70^3 = 0.343    0.70^4 = 0.2401
    0.70^5 = 0.16807  0.70^6 = 0.117649 0.70^7 = 0.0823543

  Denominator is fixed:  1 - alpha = 0.30

  gamma   1 - alpha^(g+1)      E[tokens]       cost 1+g(0.1)     net speedup
  -----   ----------------     -----------     -------------     -----------
    1     1 - 0.490 = 0.510    0.510/0.3=1.70      1.1              1.55x
    2     1 - 0.343 = 0.657    0.657/0.3=2.19      1.2              1.83x
    3     1 - 0.2401= 0.760    0.760/0.3=2.53      1.3              1.95x
    4     1 - 0.1681= 0.832    0.832/0.3=2.77      1.4              1.98x  <- peak
    5     1 - 0.1176= 0.882    0.882/0.3=2.94      1.5              1.96x
    6     1 - 0.0824= 0.918    0.918/0.3=3.06      1.6              1.91x
    inf   1 - 0     = 1.000    1.000/0.3=3.33      grows forever    -> 0

  Ceiling:  1 / (1 - 0.70) = 3.33 tokens per target pass, no matter how long the draft
```

Read the two right-hand columns against each other. `E[tokens]` climbs 1.70 → 2.77 → 3.06 and is already 83% of its ceiling by γ=4, while cost climbs 1.1 → 1.4 → 1.6 without ever slowing down. Their ratio peaks at γ=4 and decays after. This is why production configs cluster at K=4-5 (the case study uses K=5) rather than K=20 — γ is not "more is better," it has an optimum that moves with α.

**Where break-even comes from.** Speedup > 1 requires `E[tokens] > 1 + γc`. At γ=4, c=0.1 the bar is 1.4 tokens per pass, and solving `(1 - α^5)/(1 - α) = 1.4` lands near α ≈ 0.45 — precisely the break-even quoted above. Below that, the draft model is burning GPU on tokens the target throws away.

**Why α is not a knob you can turn.** Nothing in your config sets α; it emerges from how closely the draft's distribution tracks the target's on *this* traffic. Code is templated and predictable (α ≈ 0.75-0.90), open-ended creative text is not (α ≈ 0.40-0.55), and the same deployment therefore sees different speedups per route. It also collapses if you break the match — a T=0 draft against a T=0.7 target drops α to ~0.35 (Section 14), which the formula turns into `(1 - 0.35^5)/0.65 / 1.4 = 1.09×`, essentially nothing for the extra GPU. Monitor α in production the way you monitor cache hit rate; it is the same kind of number.

→ **Deep dive**: [Speculative Decoding](speculative_decoding.md) covers the rejection-sampling exactness proof, the full draft-strategy landscape (independent draft models, EAGLE/EAGLE-2/EAGLE-3, Medusa, lookahead/Jacobi decoding, prompt-lookup/ngram decoding, self-speculative/LayerSkip, DeepSeek-V3 multi-token prediction), tree-based verification, and production tuning (adaptive K, acceptance-rate monitoring, when to disable).

### 4.6 Flash Attention

Flash Attention reorders attention computation into SRAM-resident tiles with an online-softmax accumulation, never materializing the full O(n²) attention matrix in HBM — O(n) memory and 2-4× faster for long sequences, since decode is memory-bandwidth bound (Section 6). Flash Attention 2/3 add further kernel-level optimizations for A100/H100.

→ Full mechanics and the online-softmax derivation: [Attention Mechanisms](../foundations_and_architecture/attention_mechanisms.md). Kernel-level and quantization context: [Optimization & Quantization](../optimization_and_quantization/README.md).

### 4.7 Q/K/V Roles During Inference

Understanding why the KV cache exists requires understanding what happens to Q, K, V during decode:

```
Prefill phase (processing input tokens):
  ALL tokens in input are processed SIMULTANEOUSLY
  → Q, K, V computed for ALL positions in one forward pass
  → K and V are SAVED to the KV cache (layer by layer)
  → Q is used immediately for attention, then discarded

Decode phase (generating output, one token at a time):
  New token is added at position n
  → Only the NEW token's Q is computed
  → Only the NEW token's K, V are computed and appended to cache
  → Attention: Q[new] × (K[0:n+1] from cache) → weights × V[0:n+1]
  → Output: new token prediction

Why only Q is "live":
  The new token asks: "Given everything that came before (K_cache),
  what should I attend to?" The K and V of previous tokens never change —
  that's why they can be cached. Only the new token's Q changes each step.
```

This asymmetry is fundamental: K and V can be cached because past context doesn't change; Q cannot because each new token asks a different "question."

### 4.8 Prompt Caching (Anthropic-Style)

For applications with repeated system prompts or shared prefixes across requests, recomputing KV for the shared prefix on every request wastes compute:

```
Without prompt caching:
  Request 1: [system_prompt (2000 tokens)] + [user_query_1]
    → compute KV for all 2000 + user tokens
  Request 2: [system_prompt (2000 tokens)] + [user_query_2]
    → recompute KV for all 2000 tokens AGAIN
  → 2000 tokens × TTFT × N_requests wasted

With prompt caching (Anthropic Claude):
  Request 1: KV for system_prompt computed and STORED server-side
  Request 2+: system_prompt KV loaded from server cache
    → only compute KV for new user query tokens
```

**How it works in the API:**
- Mark cache breakpoints in your prompt with `cache_control: {type: "ephemeral"}`
- Cache persists for ~5 minutes (Anthropic) or up to 1 hour with refreshes
- Cached tokens billed at 10% of base cost; cache reads at ~10% latency

**Practical savings:**
```
System prompt: 2,000 tokens, billed at $3/1M tokens
Per-request cost without caching: 2,000 × $3/1M = $0.006
Per-request cost with caching: 2,000 × $0.30/1M = $0.0006 (cache write: 1.25×, cache read: 0.1×)
Effective: 90% cost reduction on shared prefix for high-traffic applications
```

**In plain terms.** "Pay a small premium the first time to have the server keep your prefix warm, then pay a tenth of list price every time you reuse it — so the cache pays for itself the moment a second request touches the same prompt."

| Symbol | What it actually is |
|--------|---------------------|
| `N` | Requests sharing the prefix inside one cache lifetime (~5 min TTL for Anthropic) |
| `T_p` | Prefix length in tokens — the part being cached (2,000 here) |
| `C_base` | List price per token. $3 per 1M tokens in this example |
| `1.25x` | Cache-*write* multiplier. The premium on the first request only |
| `0.1x` | Cache-*read* multiplier. The 90% discount on every subsequent request |
| `N_be` | Requests needed before caching is cheaper than not caching |

**Walk one example with real numbers.** 2,000-token system prompt at $3/1M tokens, so one uncached pass costs `2,000 × $3/1M = $0.0060`:

```
  no cache:      N x 0.0060
  with cache:    0.0060 x 1.25  +  (N - 1) x 0.0060 x 0.1
                 = 0.0075       +  (N - 1) x 0.00060

  N = 1     no cache 0.0060   cached 0.00750   -> caching LOSES  ($0.0015 wasted)
  N = 2     no cache 0.0120   cached 0.00810   -> caching wins    32% cheaper
  N = 10    no cache 0.0600   cached 0.01290   -> caching wins    78% cheaper
  N = 100   no cache 0.6000   cached 0.06690   -> caching wins    89% cheaper
  N -> inf                                     -> approaches the 90% floor

  Break-even:  0.0075 + (N-1)(0.0006) = 0.006N  ->  N_be = 1.27, i.e. the 2nd request
```

**Why the 1.25× write premium matters.** It makes caching a *net loss* on any prefix touched exactly once inside the TTL. Mark a per-user or per-document prefix that never repeats and you have added 25% to your bill while the dashboard says "caching enabled." Cache only prefixes you can prove are shared, and check the cache-hit ratio, not the cache-enabled flag.

**Best for**: AI assistants with large system prompts, applications with shared document context, multi-turn conversations with long histories.

### 4.9 Prefix Caching (SGLang RadixAttention)

SGLang (Stanford) takes prefix caching further with **RadixAttention** — automatic, fine-grained prefix reuse without explicit API breakpoints:

```
Radix tree structure (indexed by token sequences):
  Root
    └── "You are a helpful assistant. " (tokens 0-8)
          ├── "Translate to French: " → [request A KV blocks]
          └── "Summarize: " → [request B KV blocks]
                  └── "The paper discusses..." → [request C KV blocks]

Any new request shares KV blocks for all matching prefix tokens.
Match is at block granularity (e.g., 16 tokens per block).
```

**Contrast with Anthropic's approach:**
| | Anthropic Prompt Caching | SGLang RadixAttention |
|---|---|---|
| Breakpoints | Explicit (user marks them) | Automatic |
| Granularity | Coarse (whole prefix) | Fine (per-block) |
| Cache scope | Cross-request, server-side | Within-instance, per-engine |
| Use case | API users, shared prefixes | Inference engines, tree-structured workloads |

RadixAttention is especially effective for **tree-structured programs** (e.g., LLM-generated code that branches: same setup, different function implementations to evaluate).

### 4.10 KV Cache Eviction Strategies

When the KV cache fills GPU memory, older or less-important entries must be evicted. **LRU** (evict the request used longest ago, vLLM's default for preemption) is simple but request-grained — it can evict an entire long, expensive-to-recompute prefix. Within a single request's cache, attention-aware methods exploit the fact that a small fraction of tokens ("heavy hitters") and the first few tokens ("attention sinks") receive most of the attention mass:

```
Production numbers (LLaMA 3 70B, 128K context, full KV cache ~40 GB/request):
  Method       | Memory kept | Quality loss | Overhead        | Adaptive?
  -------------|-------------|-------------|-----------------|----------
  H2O          | ~3% (~10GB) | <1%         | 5-10% per step  | Yes (dynamic)
  SnapKV       | ~20% (~8GB) | <1%         | 2-5% one-time   | No (static)
  StreamingLLM | ~5-20%      | 2-5%        | ~0%             | No (fixed sink+window)
```

→ **Deep dive**: [KV Cache Optimization](kv_cache_optimization.md) covers H2O, SnapKV, StreamingLLM/attention sinks, and Scissorhands mechanics in full, the static-vs-dynamic tradeoff, why naive sliding windows without sink tokens cause a perplexity cliff, cross-layer KV sharing (YOCO/CLA), and a worked case study of serving 128K context under KV-OOM pressure.

### 4.11 Chunked Prefill

**Problem:** A single long-context prefill (e.g., 32K tokens of a RAG document) monopolizes the GPU for 1-2 seconds. During this time, all ongoing decode-phase requests stall — their TPOT spikes from 30ms to 2,000ms, which is visible to users as a freeze.

```
Without chunked prefill:
  Time  0ms: Long prefill request arrives (32K tokens)
  Time  0ms - 1800ms: GPU 100% occupied doing prefill for this ONE request
  Time  0ms - 1800ms: ALL other decode requests frozen (TPOT = ∞ during this window)
  Time 1800ms: Prefill done; other requests resume
  User B experience: "why did the response freeze for 2 seconds?"
```

**Solution:** Split the prefill into chunks, interleave with decode steps:

```
With chunked prefill (chunk_size = 512 tokens):
  Step 1: Prefill chunk 0 (tokens 0-511) + decode for users B, C, D
  Step 2: Prefill chunk 1 (tokens 512-1023) + decode for users B, C, D
  Step 3: Prefill chunk 2 (tokens 1024-1535) + decode for users B, C, D
  ...
  Step 63: Last prefill chunk + decode for users B, C, D

GPU time is shared: long prefill takes the same total compute, but spreads
its impact across many steps, keeping decode TPOT stable for other users.
```

**Impact on key metrics:**
```
Concurrent decode TPOT:
  Without chunking: spikes 10-50× during long prefill (unacceptable)
  With chunking: increases 5-15% (acceptable overhead)

Long prefill TTFT:
  Without chunking: ~1800ms (0 decode competition)
  With chunking: ~2000ms (10% overhead from interleaving)

Net: small TTFT regression for one user, massive TPOT improvement for all others
```

**vLLM configuration:**
```yaml
vllm serve llama3-70b \
  --enable-chunked-prefill \
  --max-num-batched-tokens 512   # chunk size
```

Chunked prefill is enabled by default in vLLM >= 0.4.0 for models above 7B. SGLang also implements chunked prefill with similar semantics.

### 4.12 Request Scheduling Strategies

How requests are ordered and batched determines both average and tail latency:

**FCFS (First Come First Served):**
```
Requests processed in arrival order.
Problem: Head-of-line blocking — one 32K-token request arriving first
         delays all subsequent short requests behind it.
Queue: [32K request] → [100-token request] → [200-token request]
       The 100-token request waits behind the 32K one.
Advantage: Simple; fair; no estimation needed.
```

**Shortest Job First (SJF) / Shortest Remaining Time:**
```
Estimate output length (using input length as proxy) and prioritize shorter jobs.
Problem: Requires length estimation; can starve long requests under heavy load.
Use: Minimizes average TTFT at cost of potential starvation.
```

**Priority Queues (SLA-tier routing):**
```
Premium tier: queue priority = 10 (always served next)
Standard tier: queue priority = 5
Batch tier:    queue priority = 1 (accept high latency for low cost)

Implementation: weighted fair queuing across priority levels
Result: premium users experience near-zero queue time; batch users deferred to idle periods
```

**Length-Bucketing (padding reduction):**
```
Group incoming requests by similar input length (±20% of each other):
  Bucket A: 50-60 tokens     → batch these together
  Bucket B: 200-240 tokens   → batch these together
  Bucket C: 1000-1200 tokens → batch these together

Without bucketing: batch of [50, 1200, 200, 60] tokens
  → pad ALL to 1200 tokens → 75% of compute is wasted on padding

With bucketing: [50, 60] batched together → minimal padding
                [200] served separately → no padding waste
Result: 15-25% throughput improvement on heterogeneous workloads
```

**Preemption (vLLM):**
```
When GPU KV cache is full, a new high-priority request cannot start.
Options:
  1. Swap: move lowest-priority request's KV blocks to CPU RAM (high latency to resume)
  2. Recompute: drop preempted request's KV blocks; recompute prefill when resumed
                (fast resume start, but wastes the prefill compute already done)
  3. Queue: reject new request if KV cache full (simplest, but poor user experience)

vLLM default: swap to CPU. Recompute is better when prefill is cheap (short inputs).
```

**Production systems combine all strategies:**
```
Incoming request
    |
    v
[Priority classifier] → premium | standard | batch tier
    |
    v
[Length estimator] → assign to length bucket
    |
    v
[Chunked prefill scheduler] → interleave with ongoing decode
    |
    v
[KV cache monitor] → if > 90% full → preempt lowest priority
    |
    v
[Continuous batching] → add to active batch at next iteration boundary
```

### 4.13 Streaming Architectures

Streaming delivers tokens to the client as they are generated rather than buffering the entire response. This is critical for perceived latency — a user waiting 3 seconds for a complete response feels slower than seeing the first token at 300ms with subsequent tokens flowing in.

**Server-Sent Events (SSE):**
```
Protocol: HTTP/1.1 compatible, unidirectional (server → client)
Content-Type: text/event-stream
Connection: keep-alive

Server sends:
  data: {"token": "Paris", "index": 0}\n\n
  data: {"token": " is", "index": 1}\n\n
  data: {"token": " the", "index": 2}\n\n
  data: [DONE]\n\n

Characteristics:
  - Built on standard HTTP — works through CDNs, proxies, load balancers
  - Auto-reconnect built into browser EventSource API
  - Text-only (no binary frames)
  - One-way: client cannot send data mid-stream without a separate request
  - OpenAI, Anthropic, and most LLM APIs use SSE for streaming
```

**WebSocket:**
```
Protocol: Upgrade from HTTP/1.1, bidirectional (full duplex)
Connection: persistent TCP

Client sends:  {"action": "generate", "prompt": "Tell me about..."}
Server sends:  {"token": "Paris", "index": 0}
Server sends:  {"token": " is", "index": 1}
Client sends:  {"action": "cancel"}     ← mid-stream cancellation
Server sends:  {"status": "cancelled"}

Characteristics:
  - Lower per-message overhead (2-6 byte frame header vs. SSE text framing)
  - Bidirectional: client can cancel, steer, or send new input mid-generation
  - Binary and text support
  - Does not auto-reconnect; application must handle reconnection
  - More complex proxy/load balancer configuration (sticky sessions required)
```

**TTFT (Time to First Token) optimization:**
```
Target TTFT by scenario:
  Cached/short prompts (<1K tokens):  < 500ms
  Cold prompts (1K-8K tokens):        < 2s
  Long context (32K+ tokens):         < 5s (chunked prefill helps)

Optimization levers:
  1. Prompt caching: reuse KV for shared prefixes → TTFT drops 80-90%
  2. Chunked prefill: prevents long prefill from blocking first token
  3. Model routing: send simple queries to smaller, faster models
  4. Hardware: prefill is compute-bound → more FLOPS = lower TTFT
```

**Chunked streaming (token batching to client):**
```
Per-token streaming:
  Token 1 → send → Token 2 → send → Token 3 → send
  Network overhead: 1 HTTP chunk per token
  Latency to first token: minimal
  Network cost: high (headers/framing per token at 30-80 tok/s = 30-80 chunks/s)

Chunked streaming (batch every 3-5 tokens):
  Tokens 1-4 → buffer → send → Tokens 5-8 → buffer → send
  Network overhead: 1 chunk per 4 tokens
  Latency to first visible token: slightly higher (adds ~100-150ms for 4-token buffer)
  Network cost: 75% reduction in chunk overhead
  Common for: mobile clients on poor connections, cost-sensitive deployments

Production pattern:
  - Chat UIs: per-token streaming (users expect character-by-character appearance)
  - API consumers: chunked streaming acceptable (programmatic consumers batch anyway)
  - Mobile apps: chunk every 3-5 tokens to reduce battery and bandwidth usage
```

**When to use SSE vs WebSocket:**
```
SSE (default choice for most LLM applications):
  - Chat interfaces, API streaming responses
  - Read-only token streams
  - Stateless deployments behind load balancers
  - When CDN/proxy compatibility matters

WebSocket (when bidirectionality is required):
  - Collaborative editing with LLM suggestions (e.g., Cursor-style code editors)
  - Real-time voice/audio streaming with interruption support
  - Applications where client sends follow-up context mid-generation
  - Gaming or interactive applications with continuous input/output

Rule of thumb: use SSE unless you need the client to talk back mid-stream.
```

### 4.14 Semantic Caching

Semantic caching avoids redundant LLM inference by detecting when a new prompt is semantically equivalent to a previously cached prompt and returning the cached response directly. The full caching stack (exact, semantic, KV/prefix) is covered in [LLM Caching](../llm_caching/README.md).

**How it works:**
```
1. Incoming prompt → embed using sentence embedding model (e.g., text-embedding-3-small)
2. Search vector DB for cached prompts with cosine similarity > threshold
3. If match found → return cached response (skip LLM entirely)
4. If no match → run LLM inference → cache (embedding, prompt, response)

                   ┌─────────────────────────────────────────┐
  User prompt ───→ │ Embed prompt → Search vector cache       │
                   │   Match (sim > 0.95)? → Return cached    │
                   │   No match? → LLM inference → Cache it   │
                   └─────────────────────────────────────────┘

Similarity threshold selection:
  0.98+:  Very strict — only near-identical prompts match (safe, low hit rate)
  0.95:   Standard threshold — good balance for most applications
  0.90:   Aggressive — higher hit rate but risk of returning wrong cached answer
  < 0.90: Dangerous — semantically different prompts start matching
```

**Cache hit rates by application type:**
```
Customer support bots:    15-40% hit rate
  (users ask same questions: "where is my order", "how to reset password")

FAQ / knowledge base:     25-50% hit rate
  (highly repetitive queries against same corpus)

General chat:             5-15% hit rate
  (diverse conversations, low repetition)

Code generation:          8-20% hit rate
  (similar boilerplate requests, common patterns)
```

**Invalidation strategies:**
```
TTL-based:
  Short TTL (1-4 hours): for rapidly changing information
  Long TTL (1-7 days): for stable knowledge (documentation, tutorials)
  Per-entry TTL based on query type (factual=short, conceptual=long)

Model version change:
  Invalidate entire cache when underlying model is updated
  New model may produce different (better) answers for same prompts

Semantic drift detection:
  Periodically re-run a sample of cached prompts through the model
  If new response diverges significantly from cached → invalidate that entry
  Catches cases where model updates or fine-tuning changed behavior
```

**Cost savings:**
```
Without semantic caching:
  1000 requests/hour × avg 500 output tokens × $15/1M tokens = $7.50/hour

With semantic caching (30% hit rate):
  700 LLM calls × $7.50/1000 = $5.25/hour
  300 cache hits × ~$0 (embedding lookup cost negligible) = ~$0
  Total: $5.25/hour → 30% cost reduction

For highly repetitive workloads (40% hit rate):
  600 LLM calls → 40% cost reduction
  Plus latency improvement: cache hit returns in ~50ms vs. 1-3s for LLM call
```

**Read it like this.** "Blend the two prices by how often you hit: a hit costs almost nothing and a miss costs full freight, so your effective cost per request is just the miss price scaled down by the fraction you avoided."

The consequence people miss: cost savings are *linear* in hit rate and can never exceed it. A 30% hit rate is a 30% discount, full stop — no threshold tuning turns it into 60%. Latency, by contrast, improves non-linearly, because a hit collapses 1-3 s down to ~50 ms.

| Symbol | What it actually is |
|--------|---------------------|
| `h` | Fraction of requests answered from cache. 0.05-0.50 depending on workload |
| `C_miss` | Full inference cost of one request — LLM tokens, the expensive path |
| `C_hit` | Embed + vector search. ~$0 against LLM tokens, so it usually drops out |
| `C_eff` | Blended cost per request after caching. What actually lands on the invoice |
| `(1 - h)` | Miss rate. The fraction still paying full price |

**Walk one example with real numbers.** Reusing this section's workload — 1,000 requests/hour, 500 output tokens each, $15/1M tokens:

```
  C_eff = (1 - h) x C_miss  +  h x C_hit

  Baseline hourly spend:
    1,000 req x 500 tok x $15/1M  =  1,000 x $0.0075  =  $7.50 / hour

  h = 0.30 (customer support bot):
    (1 - 0.30) x $7.50  +  0.30 x ~$0.00  =  $5.25 / hour   -> 30% saved
  h = 0.40 (FAQ / knowledge base):
    (1 - 0.40) x $7.50  +  0.40 x ~$0.00  =  $4.50 / hour   -> 40% saved
  h = 0.10 (general chat):
    (1 - 0.10) x $7.50  +  0.10 x ~$0.00  =  $6.75 / hour   -> 10% saved

  Blended latency at h = 0.30, miss = 2,000 ms, hit = 50 ms:
    0.70 x 2,000  +  0.30 x 50  =  1,400 + 15  =  1,415 ms   -> 29% faster
```

**Why `C_hit` is written in rather than dropped.** It is negligible on *cost* but not on *risk*: every miss also pays the embedding and vector-search latency before falling through to the LLM. At h=0.10 you have added ~10 ms to 90% of requests to save 10% of spend — which is why the general-chat row above is usually not worth deploying, and why hit rate is the number to measure before turning semantic caching on.

**Tools and implementations:**
```
GPTCache:        Open-source, pluggable embedding + cache backends
Redis + vector:  RediSearch with vector similarity (HNSW index)
Custom:          Embedding model + FAISS/Qdrant + TTL logic
LiteLLM:        Built-in caching layer for multi-provider setups
```

---

## 5. Architecture Diagrams

### Prefill vs Decode Phases
```
Prefill: "What is the capital of France?"
  |
  +-- All 8 tokens processed in PARALLEL → one forward pass
  |   Q, K, V computed for all 8 tokens simultaneously
  |   KV cache: K[0:8], V[0:8] stored
  |   Output: first generated token = "Paris"

Decode: generating each subsequent token
  Token 1 "Paris":    Q[8] × (K[0:9], V[0:9]) → "is"      [cached K/V reused]
  Token 2 "is":       Q[9] × (K[0:10], V[0:10]) → "the"    [cached K/V reused]
  Token 3 "the":      Q[10] × (K[0:11], V[0:11]) → "capital" [...]
  ...
  (Only one new token's Q computed each step; all previous K,V reused from cache)
```

### Speculative Decoding Timeline
```
Without speculative decoding (large model only):
  |--T1--| |--T2--| |--T3--| |--T4--|   (4 separate forward passes)

With speculative decoding:
  Small model: |---draft 4 tokens fast---|
  Large model: |-------verify all 4 in one pass + correct---------|
  Net: ~4 tokens per large model pass → 3-4× speedup
```

### Streaming Delivery: SSE vs WebSocket
```
SSE (Server-Sent Events):
  Client ──HTTP GET──→ Server
  Client ←─text/event-stream─── Server
  Client ←─data: token1─────── Server
  Client ←─data: token2─────── Server
  Client ←─data: [DONE]──────── Server
  (unidirectional: server → client only)

WebSocket:
  Client ──HTTP Upgrade──→ Server
  Client ←→ Full duplex TCP ←→ Server
  Client ←─ token1 ─── Server
  Client ──── cancel ──→ Server     ← client can interrupt
  Client ←─ cancelled ─ Server
  (bidirectional: both can send at any time)

Chunked Streaming (batched tokens):
  Per-token:   T1→send  T2→send  T3→send  T4→send   (4 network chunks)
  Chunked(4):  T1,T2,T3,T4 → send                    (1 network chunk)
  Tradeoff:    ~100-150ms added latency, 75% fewer network operations
```

### KV Cache Eviction: H2O vs SnapKV
```
Full KV cache (128K context, 70B model):
  [tok_0][tok_1][tok_2]...[tok_131071]  = ~40 GB

H2O eviction (dynamic, per-step scoring):
  [sink_0..3][heavy_hitter_64..191][recent_384..511]  = ~10 GB
   ^first 4    ^highest cumulative     ^sliding window
   tokens       attention scores

SnapKV eviction (static, one-time pruning after observation):
  [observation window] → identify important tokens → prune
  [important_0..127][recent_128..383]  = ~8 GB
   ^consistently high  ^sliding window
    attention across
    all layers
```

---

## 6. How It Works — Detailed Mechanics

### Memory Bandwidth Bottleneck and Arithmetic Intensity

```
During decoding, each step requires:
  - Load ALL model weights from HBM: 70B × 2 bytes = 140GB
  - Perform ~140B FLOPs of computation per token
  - A100 80GB HBM bandwidth: 2 TB/s
  - A100 compute: 312 TFLOPS (BF16)

Time to load weights: 140GB / 2TB/s = 70ms
Time to compute: 140B FLOPs / 312 TFLOPS = 0.45ms

→ Memory bandwidth is 150× slower than compute
→ GPU is starved for data, not compute
→ Solution: better batching (amortize weight loading across more requests)
→ Solution: quantization (load less data per weight)
```

**What it means.** "A decode step cannot finish faster than the time it takes to drag the model's weights across the memory bus once — so your token rate is a division problem, bandwidth divided by bytes-per-token, and the GPU's arithmetic units have nothing to do with the answer."

This is the single most useful reframe in inference: stop asking "how fast is this GPU?" and start asking "how many times per second can this GPU read its own weights?" It converts every hardware and quantization decision into one division you can do in your head.

| Symbol | What it actually is |
|--------|---------------------|
| `BW` | HBM read bandwidth in bytes/sec. A100 = 2 TB/s, H100 = 3.35 TB/s |
| `bytes/token` | Everything read from HBM for one decode step: weights + the KV that step touches |
| `BW / bytes` | Steps per second — the hard ceiling on tokens/sec at batch 1 |
| `AI` | FLOPs performed per byte loaded. Low = starved for data, high = starved for math |
| `ridge point` | `peak FLOPS / BW`, in FLOPs/byte. The AI at which the two ceilings cross |
| `2 x params` | FLOPs per token: one multiply + one add per weight. 70B params → 140 GFLOPs |

**Walk one example with real numbers.** Watch the units cancel — that is the whole proof:

```
  Token ceiling = memory bandwidth / bytes moved per token

  70B model, BF16 weights, batch = 1, on an A100:

      2 TB/s          2,000 GB/s          GB     1
    ----------  =  ---------------  =  -------- x --  =  14.3 tokens/sec
      140 GB           140 GB             GB      s
                                        (cancels)

  Same model, same card, INT4 weights (70B x 0.5 bytes = 35 GB):

    2,000 GB/s / 35 GB                                =  57.1 tokens/sec   (4.0x)

  Same BF16 model moved to an H100 (3.35 TB/s):

    3,350 GB/s / 140 GB                               =  23.9 tokens/sec   (1.67x)
```

Note what the second line proves: INT4 delivered a 4× speedup on a card whose FLOPS did not change at all. Quantization is a *bandwidth* optimization that people mistakenly file under "compute." And the third line explains why H100 beats A100 for decode by 1.67× rather than by its 3× FLOPS advantage — you get the bandwidth ratio, not the compute ratio, because bandwidth is the binding constraint.

**Why the FLOPs side of the ledger loses.** The same step does `2 × 70B = 140 GFLOPs` of math. At 312 TFLOPS that is `140e9 / 312e12 = 0.45 ms`, against `140 GB / 2 TB/s = 70 ms` of loading. The compute unit finishes and then waits `70 - 0.45 = 69.55 ms` — idle for better than 99% of the step. Every optimization in this module exists to fill that gap: batching gives the idle units other requests' math to chew on while the *same* weight bytes stream past once.

**Roofline model — batch size as the crossover lever:**
```
Arithmetic intensity = FLOPs / bytes_loaded

Decode (batch_size = 1):
  FLOPs:  2 × 70B = 140B per token
  Bytes:  140GB weights (BF16)
  Intensity: 140B / 140G = 1 FLOP/byte  ← deeply memory-bandwidth-bound

Decode (batch_size = 64):
  FLOPs:  64 × 140B = 8.96T
  Bytes:  140GB weights (shared across batch) + KV cache
  Intensity: ~64 FLOPs/byte

Decode (batch_size = 160):
  Intensity: ~160 FLOPs/byte

A100 hardware ridge point: 312 TFLOPS / 2 TB/s = 156 FLOPs/byte
→ Batch size ~156 is the crossover from memory-bound to compute-bound

Implication:
  - At batch=1 (single user): GPU compute is 99.4% idle, waiting for data
  - At batch=156: compute and memory are balanced — maximum efficiency
  - Beyond batch=156: compute becomes the bottleneck (diminishing returns on batching)
  - KV cache grows with batch and context → practical limit is usually KV OOM before
    the compute crossover is reached
```

**Put simply.** "Arithmetic intensity asks how much math you get done per byte you dragged off the bus; the hardware has its own fixed ratio of math-to-bandwidth, and whichever side of that ratio you fall on names your bottleneck."

The trick is that batching moves *your* intensity without changing the *hardware's* ratio. The weights get loaded once and B requests all do their math against them, so intensity is almost exactly B — which makes "what batch size do I need?" and "where is the ridge point?" literally the same question.

| Symbol | What it actually is |
|--------|---------------------|
| `AI` | `FLOPs / bytes_loaded` for your workload. Yours to change, via batching |
| `peak FLOPS` | The card's compute ceiling. A100 BF16 = 312 TFLOPS |
| `ridge point` | `peak FLOPS / BW`. The card's own ratio — fixed in silicon, nothing you do moves it |
| `AI < ridge` | Memory-bound. Adding batch is nearly free throughput |
| `AI > ridge` | Compute-bound. Adding batch now buys latency, not throughput |

**Walk one example with real numbers.** Both sides of the comparison, on an A100:

```
  Hardware ridge point (a property of the card, not your model):

      312 TFLOPS       312e12 FLOP/s      FLOP
    --------------  =  --------------  =  ----  = 156 FLOPs/byte
        2 TB/s          2e12 byte/s       byte

  Your workload's intensity at batch B (70B BF16):

              FLOPs        B x 140e9 FLOPs
    AI  =  -----------  =  ----------------  =  B x 1.0 FLOPs/byte
             bytes            140e9 bytes

    B =   1   ->  AI =   1  FLOP/byte    1/156  =  0.6% of compute used   memory-bound
    B =  64   ->  AI =  64                64/156 = 41%                    memory-bound
    B = 156   ->  AI = 156               156/156 = 100%                   BALANCED
    B = 256   ->  AI = 256               bandwidth now idles              compute-bound

  Throughput while memory-bound (step time pinned at 70 ms):
    B =   1   ->    1 / 0.070  =    14.3 tokens/sec
    B =  64   ->   64 / 0.070  =   914   tokens/sec     (64x, essentially free)
    B = 156   ->  156 / 0.070  = 2,229   tokens/sec     (the ceiling of this regime)
```

Every token from B=1 to B=156 is free throughput — the weight bytes were being loaded anyway. Past 156 you are paying real compute per added request and step time starts to stretch, so throughput flattens while latency climbs.

**Why you almost never reach the ridge point.** Serving B=156 concurrent 8K requests on LLaMA 3 70B needs `156 × 8,192 × 320 KB ≈ 409 GB` of KV cache by the Section 4.3 formula — five H100s of KV alone, on top of the 140 GB of weights. You hit KV OOM around B=30-40 and stop there. This is the punchline that ties the whole module together: the *compute* crossover is theoretical, the *memory* wall is what you actually operate against, and that is why PagedAttention, GQA, KV quantization, and eviction all attack the same term.

### Latency vs Throughput Optimization

```
Optimize for latency (single user):
  - Small batch size (1-4)
  - Large model → single query, fast response
  - Streaming: send tokens as generated

Optimize for throughput (many users):
  - Large batch size (32-256)
  - Continuous batching
  - PagedAttention: maximize KV cache utilization
  - Token/second × GPU is the key metric
```

### Streaming Delivery Mechanics

```
SSE implementation (server-side, Python/FastAPI):
  @app.get("/v1/chat/completions")
  async def stream_chat(request: ChatRequest):
      async def token_generator():
          async for token in model.generate_stream(request.prompt):
              yield f"data: {json.dumps({'token': token})}\n\n"
          yield "data: [DONE]\n\n"
      return StreamingResponse(token_generator(), media_type="text/event-stream")

SSE client-side (JavaScript):
  const source = new EventSource("/v1/chat/completions?prompt=...");
  source.onmessage = (event) => {
      if (event.data === "[DONE]") { source.close(); return; }
      const { token } = JSON.parse(event.data);
      appendToUI(token);
  };
  // Auto-reconnects on network failure (built into EventSource API)

WebSocket implementation (for bidirectional needs):
  @app.websocket("/ws/chat")
  async def websocket_chat(websocket: WebSocket):
      await websocket.accept()
      while True:
          msg = await websocket.receive_json()
          if msg["action"] == "generate":
              async for token in model.generate_stream(msg["prompt"]):
                  if await check_cancel(websocket):  # client can cancel
                      break
                  await websocket.send_json({"token": token})
          elif msg["action"] == "cancel":
              break  # stop generation immediately
```

**TTFT optimization in production:**
```
Scenario: Chat application, target TTFT < 500ms for 90% of requests

Lever 1 — Prompt caching:
  System prompt (2000 tokens) cached → skip prefill for shared prefix
  TTFT for cached prefix: ~50ms (load KV from cache) vs ~800ms (compute KV)

Lever 2 — Model routing for TTFT:
  Input < 500 tokens → small model (8B), TTFT ~100ms
  Input 500-4K tokens → medium model (70B), TTFT ~500ms
  Input 4K+ tokens → large model with chunked prefill, TTFT ~2-5s

Lever 3 — Chunked prefill interaction with streaming:
  Without chunking: TTFT for 32K input = ~1800ms (user waits, sees nothing)
  With chunking: TTFT for 32K input = ~2000ms total, but other users unblocked

Lever 4 — Speculative prefill:
  Start generating with a smaller model immediately (low TTFT)
  Switch to larger model output once its prefill completes
  User sees tokens from small model first → perceived TTFT ~100ms
  Quality tokens from large model arrive ~500ms later and replace if needed
```

### Semantic Caching — Detailed Pipeline

```
Step-by-step flow:

1. Normalize prompt:
   - Strip whitespace, lowercase (optional), remove conversation metadata
   - Hash the normalized prompt for exact-match cache (fast path)

2. Exact match check (Redis/in-memory):
   - Hash lookup: O(1), ~1ms
   - If hit → return cached response immediately
   - Common for: repeated API calls, retry logic, identical user queries

3. Semantic match check (vector DB):
   - Embed normalized prompt → 768-1536 dim vector (~5-10ms)
   - HNSW search in vector DB for top-1 nearest neighbor (~2-5ms)
   - If cosine_similarity > threshold (0.95) → return cached response
   - If below threshold → proceed to LLM inference

4. LLM inference (cache miss):
   - Generate response normally (~1-5s)
   - Store: (prompt_embedding, prompt_text, response, timestamp, model_version)
   - Embedding storage: ~6KB per entry (1536 dims × 4 bytes)

5. Cache maintenance:
   - TTL expiry: scan and remove entries older than TTL
   - Model version tag: invalidate all entries from previous model version
   - Size limit: evict LRU entries when cache exceeds budget
   - Drift check (weekly): re-run 1% sample, compare to cached, invalidate if diverged

Production gotchas:
  - Threshold too low (0.90): "How do I reset my password?" matches
    "How do I change my email?" → wrong cached answer served
  - Threshold too high (0.99): Only exact paraphrases match → 2% hit rate, not worth it
  - Embedding model mismatch: cache built with text-embedding-ada-002, switched to
    text-embedding-3-small → all similarity scores shift, cache effectively invalid
  - Multi-turn context: cache key must include conversation history, not just last message
    → dramatically reduces hit rate for multi-turn conversations
```

---

## 7. Real-World Examples

### vLLM (UC Berkeley, 2023)
- PagedAttention + continuous batching
- 24× higher throughput than naive serving
- Used by Anyscale, Scale AI, Together AI, and thousands of self-hostings
- Open source; de facto standard for open-source model serving

### OpenAI API
- Continuous batching across thousands of users
- Speculative decoding for common patterns
- Dynamic model routing (easy → gpt-4o-mini, hard → gpt-4o)
- Custom CUDA kernels for attention and matmul

### Anthropic Claude
- Flash Attention 2/3 for long context (200K)
- Custom inference stack (not public)
- Streaming response delivery; tokens arrive in real-time

---

## 8. Tradeoffs

| Strategy | Latency | Throughput | Memory | Complexity |
|----------|---------|-----------|--------|------------|
| Greedy decode | Fastest | High | Low | None |
| Sampling (top-p) | Fast | High | Low | Low |
| Beam search | Slow | Low | High | Medium |
| Speculative decode | ~Same | 2-3× more | +draft model | High |
| Continuous batching | Medium | Very high | Efficient | Medium |
| PagedAttention | Medium | Highest | Most efficient | High |

---

## 9. When to Use / When NOT to Use

### Use Greedy Decoding (temp=0) When:
- Factual Q&A, code generation, structured outputs
- Reproducibility is important
- Deterministic behavior is expected

### Use Sampling When:
- Creative writing, brainstorming, conversational AI
- Diversity in outputs is valuable
- Single "best" answer doesn't exist

### Use Speculative Decoding When:
- Serving large models (>70B) with throughput constraints
- Output distribution is predictable (code, templates)
- You have a compatible small model available

---

## 10. Common Pitfalls

1. **KV cache OOM**: Underestimating KV cache memory consumption causes OOM in production. Plan for 30-40% of GPU memory for KV cache.
2. **Not streaming**: Buffering full response before sending → perceived latency is very high. Always stream tokens.
3. **Ignoring TTFT**: Optimizing TPOT while ignoring Time-to-First-Token. For chat, TTFT < 1s is critical for perceived responsiveness.
4. **Wrong batch size**: Very small batches (1-2) waste GPU utilization. Very large batches increase latency. Find the sweet spot.
5. **Beam search in production**: Beam search (k=5) uses 5× KV cache memory. Only use for offline batch jobs.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **vLLM** | High-throughput serving | PagedAttention; de facto standard |
| **Flash Attention 2/3** | Efficient attention | Tri Dao; required for long context |
| **SGLang** | Structured generation | Radix attention; constraint generation |
| **TensorRT-LLM** | NVIDIA inference | INT8/FP8; H100-optimized |
| **llama.cpp** | CPU/GPU inference | Quantized; edge; MacOS Metal |
| **ExLlamaV2** | Fast quantized inference | Best speed/quality for consumer GPUs |
| **GGUF** | Model format | llama.cpp compatible; quantized |
| **Safetensors** | Model serialization | Fast, safe loading |

---

## 12. Interview Questions with Answers

**Q: Why is LLM decode memory-bandwidth-bound rather than compute-bound? What does this mean for optimization?**
A: During decode, each token generation requires loading all model weights from HBM. For a 70B BF16 model that is 140GB of data. An A100 loads this in 70ms (140GB / 2TB/s bandwidth) but computes it in 0.45ms (140B FLOPs / 312 TFLOPS). The GPU's compute units are idle 99.4% of the time waiting for data. The primary optimization lever is therefore reducing data movement: quantization (load INT4 instead of BF16, 4× fewer bytes), batching (amortize the 140GB weight load across many requests so each request "pays" 1/N of the bandwidth cost), and KV cache efficiency (fewer KV bytes transferred per step). Compute-bound optimizations like better algorithms have near-zero impact on memory-bandwidth-bound workloads.

**Q: What is the KV cache and why is it important?**
A: The KV cache stores the key and value tensors computed for each token during the prefill phase. During decoding, each new token only needs to compute its own query, then attend to all cached K, V tensors. Without KV cache, you'd recompute K and V for all past tokens at every decoding step — O(n²) work for a length-n response. With KV cache, it's O(n) total. The trade-off: KV cache consumes significant GPU memory (320KB per token for LLaMA 3 70B in BF16; at 8K context and 100 concurrent users, that is 256GB just for KV — more than the model weights).

**Q: What is the difference between TTFT and TPOT, and how do you optimize each independently?**
A: TTFT (Time To First Token) is the prefill time — dominated by input length and the GPU's prefill throughput. TPOT (Time Per Output Token) is the decode time per token — dominated by model size, batch size, and memory bandwidth. Optimize TTFT: reduce input length (summarize long contexts before passing), use chunked prefill (share GPU time between prefill and ongoing decode), or route long-context requests to dedicated prefill hardware. Optimize TPOT: increase batch size (amortize memory bandwidth cost across more requests), use quantization (load fewer bytes per token), and use speculative decoding (generate multiple tokens per large model pass). In chat applications, TTFT under 1s is critical for perceived responsiveness; TPOT of 20-50ms/token is generally acceptable.

**Q: What is chunked prefill and when is it critical?**
A: Chunked prefill splits a long prefill (e.g., 32K tokens from a RAG document) into smaller chunks (e.g., 512 tokens each) that interleave with decode steps for other concurrent requests. Without chunking, a single 32K-token prefill monopolizes the GPU for roughly 1-2 seconds, causing TPOT for all concurrent users to spike during that window. With chunking, the GPU alternates between prefill chunks and decode steps, keeping TPOT stable for ongoing users. Critical when: serving high-concurrency workloads with mixed context lengths, especially in RAG systems where documents are 4K+ tokens. The long-prefill user sees about 10-15% higher TTFT; all other users see stable TPOT. Implemented in vLLM >= 0.4.0 and SGLang.

**Q: What is the difference between prefill and decode phases?**
A: Prefill processes all input tokens in parallel in one forward pass — it is compute-bound (similar to training) because all tokens are processed simultaneously. Decode generates one output token at a time, attending to all previous tokens via the KV cache — it is memory-bandwidth-bound because loading model weights dominates. Prefill latency is proportional to input length; decode latency is proportional to output length × TPOT. For large inputs, prefill dominates total latency (TTFT); for long outputs, decoding dominates total wall-clock time.

**Q: How does temperature affect output quality and when should it be set to zero?**
A: Temperature scales logits before softmax: logits_scaled = logits / T. T=0 (greedy) always picks the highest-probability token — deterministic, coherent, but can be repetitive and may miss valid alternatives at branching points. T=1 samples from the raw model distribution. T>1 flattens the distribution (more random, potentially incoherent). T<1 sharpens it (more conservative). Use T=0 for: factual Q&A, structured JSON extraction, code generation where correctness is binary, any task requiring reproducibility. Use T=0.7-1.0 for: creative writing, brainstorming, conversational chat where diversity adds value. Rule of thumb: when there is one right answer, use T=0; when diversity is valuable and quality tolerates variation, use T=0.7-1.0.

**Q: Compare top-k, top-p (nucleus), and min-p sampling. Which performs best in practice?**
A: Top-k keeps exactly k tokens regardless of probability gaps — poor when the true distribution has 3 dominant tokens (wastes budget on long tail) or 100 plausible tokens (k=50 misses valid options). Top-p dynamically chooses the smallest set of tokens with cumulative probability >= p — adaptive, but can include very low-probability tokens when the distribution is flat. Min-p keeps tokens with probability >= min_p × max_token_probability — scales the threshold relative to the most likely token, naturally handling both peaked and flat distributions. With min_p=0.05: in a peaked distribution, only the top 2-3 tokens survive; in a flat distribution, a wider set survives proportionally. Min-p tends to outperform top-p on creative tasks empirically and is the default in many llama.cpp builds. Top-p with p=0.9 remains the most widely used default in production APIs.

**Q: Explain PagedAttention. What problem does it solve that continuous batching alone does not?**
A: Continuous batching solves GPU utilization — requests fill slots as others complete, eliminating idle time between requests. PagedAttention solves KV cache memory fragmentation within the GPU. Without paging, each sequence pre-allocates a contiguous memory block for max_seq_len tokens — a sequence using 1,024 of 4,096 allocated tokens wastes 75%. At high concurrency, this fragmentation can waste 60-80% of KV cache memory, limiting the number of concurrent users. PagedAttention manages KV cache like OS virtual memory: fixed-size physical blocks (default 16 tokens), allocated on demand, non-contiguous in physical memory, shared across sequences with the same prefix via copy-on-write. Fragmentation drops to below 4%. The combined effect of continuous batching plus PagedAttention is the 24× throughput improvement reported in the vLLM paper vs. HuggingFace naive serving.

**Q: How does speculative decoding maintain output distribution equivalence with the target model?**
A: The draft model generates K tokens with its own distribution p_draft. The target model verifies each draft token t_i and accepts with probability min(1, p_target(t_i) / p_draft(t_i)). If the draft token is very likely under the target model, it is accepted with high probability. If rejected, a correction token is sampled from the residual distribution max(0, p_target - p_draft) normalized, guaranteeing the final output matches exactly what the target model would have produced independently. This acceptance-rejection scheme is mathematically proven to produce the identical distribution as pure target model decoding — there is no quality tradeoff, only a throughput benefit. The speedup comes solely from fewer serial target model passes.

**Q: How does speculative decoding work and what are its requirements?**
A: A small draft model quickly generates K tokens. The large target model verifies all K tokens in a single forward pass (parallel, like prefill). Tokens matching the draft distribution are accepted; at the first rejection, the target's correction token is used and the process restarts. Requirements: (1) draft and target must share the same tokenizer; (2) draft must approximate the target's distribution well (similar model family) for high acceptance rate; (3) acceptance rate must exceed roughly 0.5 for K=4 to break even on overhead. Best speedup on predictable outputs (code, boilerplate, structured text): 2-3×. Falls below break-even for creative writing where the draft and target diverge frequently.

**Q: What is prompt caching (Anthropic-style) and how does it differ from vLLM's prefix caching?**
A: Anthropic prompt caching: the user explicitly marks cache breakpoints in the API request using `cache_control: {type: "ephemeral"}`. The server stores KV tensors for the marked prefix and reuses them for subsequent requests within a ~5-minute TTL. Benefit: 90% cost reduction on cached tokens, approximately 10% latency reduction. vLLM prefix caching and SGLang RadixAttention: automatic and fine-grained — the inference engine maintains a radix tree of KV blocks indexed by token sequences; any matching prefix automatically reuses cached blocks without user annotation. vLLM's approach is transparent to users and works at block granularity (16 tokens per block); Anthropic's requires explicit API integration. Use Anthropic-style caching for shared system prompts in production APIs. Use RadixAttention for inference engines serving structured workloads like multi-turn chat with common prefixes or RAG with shared document context.

**Q: A production LLM service is experiencing high P99 latency but median latency is fine. What are the likely causes?**
A: P99 latency outliers typically come from three sources: (1) Long prefill blocking short requests — one 32K-token request monopolizes the GPU for seconds while short requests queue behind it. Diagnose: plot TTFT distribution against input length; outlier TTFT values correlate with long inputs. Fix: chunked prefill, request timeout limits. (2) KV cache pressure causing preemption — when GPU KV cache fills, vLLM preempts low-priority requests (swaps to CPU), then resumes — causing latency spikes. Diagnose: monitor vLLM's `gpu_cache_usage_perc` metric; P99 spikes correlate with cache usage approaching 100%. Fix: reduce max concurrent requests or add KV cache quantization. (3) HBM bandwidth saturation at high batch sizes — too many concurrent decode requests causes memory bandwidth contention. Fix: reduce batch size ceiling and accept lower throughput.

**Q: What is the KV cache memory formula for LLaMA 3 70B at 100 concurrent users with 8K context?**
A: Per-token KV cache = 2 (K+V) × num_layers × num_kv_heads × head_dim × bytes = 2 × 80 × 8 × 128 × 2 = 327,680 bytes ≈ 320KB per token. At 8K context with 100 concurrent users = 819,200 total tokens. Total KV cache = 320KB × 819,200 ≈ 256GB. An H100 has 80GB HBM — this scenario requires more than 3 H100s just for KV cache, before counting model weights (140GB in BF16, requiring 2 H100s). Practical solution: INT8 KV quantization (halves KV to 128GB) plus 2× H100 tensor parallel for model weights, limiting active concurrency to roughly 30-40 users at full 8K context. This arithmetic is what drives production capacity planning and the decision to use GQA (fewer KV heads) in model architecture.

**Q: What is beam search and why is it rarely used in production LLM serving?**
A: Beam search maintains k candidate sequences simultaneously, expanding each at every step and keeping the k highest-probability partial sequences. It guarantees a higher-probability final sequence than greedy decoding. Problems for production: (1) k× KV cache memory — beam width 5 needs 5× the KV cache of greedy; (2) k× compute per step; (3) empirically produces lower-quality outputs than sampling for open-ended generation — beam search tends toward repetitive, high-confidence but generic text; (4) incompatible with continuous batching because all k beams must complete together, holding a GPU slot until the longest beam finishes. Reserved for: offline batch translation, speech recognition transcription, or structured generation where maximizing sequence log-probability is the correct objective and memory is not a constraint.

**Q: What is continuous batching and why does it improve throughput?**
A: Naive batching waits for all requests in a batch to complete before accepting new ones. Short requests finish early but hold their GPU slot until the longest request in the batch completes. Continuous batching (iteration-level scheduling) adds new requests to the batch as soon as any request finishes. This eliminates the GPU idle time caused by waiting for slow requests before starting fast new ones. For a realistic workload with output lengths ranging from 50 to 2,000 tokens, continuous batching increases GPU utilization from roughly 30% to 90%, which translates to the 24× throughput improvement vLLM demonstrated over HuggingFace's generate() API.

---

## 13. Best Practices

1. **Always stream responses** — don't buffer; latency perception improves dramatically with streaming.
2. **Size your KV cache budget** — 30-40% of total GPU memory is a good rule of thumb.
3. **Monitor TTFT and TPOT separately** — they have different optimization levers.
4. **Use PagedAttention (vLLM)** for any production serving with variable-length outputs.
5. **Profile before optimizing** — use NVIDIA Nsight or vLLM's metrics endpoint to identify the actual bottleneck.
6. **Set max_tokens** — unbounded generation can consume unbounded KV cache memory.

---

## 14. Case Study

**Scenario**: An AI infrastructure company serves Llama-3-70B as a shared inference API on 8×H100 80GB (TP=4, FP8 weights). Baseline: single-model vLLM deployment at 180 RPS, p99 TTFT 1,800ms, p99 decode 95ms/token. Speculative decoding (Llama-3.2-1B FP8 draft, K=5 speculative tokens, multinomial rejection sampling) is layered on top of the existing continuous batching, chunked prefill, and prefix caching to target 3× throughput.

**Results:**

| Metric | Baseline (no SD) | + SD (K=5, 1B draft) | + Adaptive K |
|--------|-------------------|----------------------|--------------|
| p50 TTFT | 280 ms | 270 ms | 268 ms |
| p99 TTFT | 1,800 ms | 680 ms | 650 ms |
| p50 decode | 20 ms/token | 7.8 ms/token | 7.2 ms/token |
| p99 decode | 95 ms/token | 38 ms/token | 35 ms/token |
| Throughput | 180 RPS | 510 RPS | 540 RPS |
| Code acceptance rate | — | 0.71 | 0.74 |
| Chat acceptance rate | — | 0.52 | 0.55 |
| Expected speedup | 1× | 2.8× | 3.0× |
| Cost reduction (same GPU count) | — | 65% | 68% |

**Key lessons:**
- **Match draft and target sampling configuration.** A greedy (T=0) draft against a T=0.7 sampling target collapses acceptance to ~0.35 — unprofitable. vLLM passes the target's temperature to the draft automatically when `draft_temperature=None`.
- **Disable speculative decoding for requests carrying logit processors.** Repetition/presence/frequency penalties and `logit_bias` create history-dependent state that diverges between draft and target after the first rejection, subtly corrupting the accepted-token distribution.
- **Disable for short outputs and high concurrency.** Below ~50 expected output tokens, draft overhead exceeds the benefit; above batch size ~32-64, draft-model batching overhead outweighs the per-request acceptance gain (`speculative_disable_by_batch_size` in vLLM).
- **Acceptance rate is task-dependent and must be monitored per route, not globally** — code (~0.7-0.9) and chat (~0.5-0.6) populations mixed in one batch drag the effective speedup toward the lower-acceptance task's rate.

→ **Full case study** — architecture diagram, vLLM engine configuration, a production `SpeculativeDecodingMonitor` (rolling acceptance rate, adaptive K, auto-disable), three broken→fix pairs (temperature mismatch, logit-processor incompatibility, short-output gating), and dedicated interview Q&As for this deployment: [Speculative Decoding §14](speculative_decoding.md#14-case-study).
