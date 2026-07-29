# Pre-Training

## 1. Concept Overview

Pre-training is the first and most expensive phase of building an LLM — the process of training a neural network on massive amounts of text data so it learns language, world knowledge, reasoning patterns, and common-sense understanding. A pre-trained model is a general-purpose "foundation" that can be specialized for downstream tasks through fine-tuning.

Pre-training is fundamentally a self-supervised learning problem: the training signal comes from the data itself (predicting the next token), not from human-labeled examples. This allows training on virtually unlimited amounts of text.

The scale of pre-training is staggering: GPT-4 was trained on trillions of tokens; Llama 3 405B on 15.6 trillion text tokens for 3.8e25 FLOPs (Meta, Llama 3 paper §3); total compute often costs tens of millions of dollars. Getting pre-training right — data quality, training stability, hyperparameter choices — has an outsized impact on the final model's capability.

---

## 2. Intuition

> **One-line analogy**: Pre-training is like reading every book, article, and website ever written — the model doesn't memorize, it absorbs patterns, facts, and reasoning styles at enormous scale.

**Mental model**: The model starts with random weights and is shown trillions of tokens of text. Its only task: predict the next token. Over billions of updates, it learns grammar, facts, code syntax, reasoning patterns, world knowledge — all encoded in its weights. It's self-supervised because the "labels" (the next token) come from the data itself. No human annotation needed.

**Why it matters**: Pre-training is the "expensive once" foundation that everything else builds on. The quality of pre-training data and the scale of training compute determines the ceiling of what the model can ever learn. Fine-tuning and alignment just redirect capabilities — they can't create capabilities that weren't learned during pre-training.

**Key insight**: Predicting the next token is a deceptively powerful objective — to predict text well, the model must implicitly learn almost everything about the world that can be expressed in language.

---

## 3. Core Principles

- **Self-supervised learning**: Training signal from predicting next tokens; no human labels needed at scale.
- **Data quality > quantity**: A well-curated 1T token dataset beats a poorly filtered 10T token dataset. Quality filtering impact: a 10x smaller high-quality dataset can match or exceed a 10x larger unfiltered dataset on downstream benchmarks (the Phi "textbooks are all you need" line of work — note that LIMA is a *fine-tuning* result about 1,000 curated SFT examples and says nothing about pre-training data scale).
- **Data mixture optimization**: Not all data domains are equal — code data at 10-15% of the mix improves reasoning even on non-code tasks. The optimal mixture depends on target capabilities, and algorithms like DoReMi can find better domain weights automatically.
- **Training dynamics matter**: Loss curves, gradient norms, and learning rate schedules determine stability and final quality.
- **Irreversibility**: Pre-training mistakes are expensive to fix — a contaminated training set or wrong architectural choice is hard to undo at scale.
- **Compute-optimal training**: Per Chinchilla, the optimal strategy allocates compute equally between model size and tokens trained. The Chinchilla-optimal ratio is roughly 20 tokens per parameter.
- **Emergent capabilities**: Many capabilities appear only at sufficient scale and look like phase transitions — performance is near chance then jumps. Wei et al. (2022, "Emergent Abilities of Large Language Models") catalogue tasks whose emergence thresholds cluster in the 10B-100B parameter range, and the chain-of-thought paper (Wei et al., 2022) reports that CoT prompting only helps at roughly the 100B scale. Schaeffer et al. (2023, "Are Emergent Abilities a Mirage?") argue the sharpness is partly an artifact of discontinuous metrics; the practical effect is still that certain capabilities are unavailable below a threshold model size. Treat any specific per-capability parameter threshold as task- and metric-dependent, not a constant.

---

## 4. Training Objectives

### 4.1 Causal Language Modeling (CLM) — GPT-style

Predict the next token given all previous tokens. The loss is the cross-entropy over the full sequence:

```
Text: "The quick brown fox"

Inputs:  [BOS] "The" "quick" "brown"
Targets:        "The" "quick" "brown" "fox"

Loss = -1/T × Σ log P(token_t | token_1, ..., token_{t-1})
```

**The idea behind it.** "For every position in the text, ask the model how much probability it gave to the token that actually came next, and average how surprised it was. Low loss = the real text was unsurprising."

That is the entire training signal for a trillion-dollar industry. There is no human label anywhere — the "answer key" is just the next token that the corpus already contains, so any text at all is training data.

| Symbol | What it is |
|--------|------------|
| `Σ` | Add up the term once for every token position `t` in the sequence |
| `T` | Sequence length — how many token positions contributed |
| `1/T × Σ` | Mean per-token loss, so long and short batches are comparable |
| `P(token_t \| ...)` | The softmax probability the model assigned to the *correct* next token |
| `log P` | Turns probability into a score. `P = 1` -> `0`; `P = 0.1` -> `-2.30`; `P -> 0` -> `-infinity` |
| `-` (leading minus) | Flips it so loss is positive and *smaller is better* |
| `L` | The single number gradient descent pushes down |

**Walk one example.** Three predictions on `"The quick brown fox"`, showing what probability the
model gave the token that actually came next:

```
  position   context              true next   P(true next)   log P     surprise
  ---------  -------------------  ----------  -------------  --------  --------
  t=1        "The"                "quick"        0.40        -0.916     low
  t=2        "The quick"          "brown"        0.25        -1.386     medium
  t=3        "The quick brown"    "fox"          0.80        -0.223     very low

  sum of log P  = -0.916 + -1.386 + -0.223 = -2.525
  L = -1/T x sum = -(1/3) x (-2.525)       =  0.842
```

Notice the model was *most* confident at `t=3`: after "The quick brown" the idiom nearly forces
"fox", so it contributes almost no loss. The `t=2` step, where many colours were plausible, carries
most of the penalty. Training is nothing but pushing the `P(true next)` column toward `1.00`.

**Why the `1/T` averaging matters.** Log-probabilities are negative and accumulate, so without
dividing by `T` a 4,096-token sequence would always report a "worse" loss than a 512-token one
purely from length. Sequences in a batch have different real lengths after padding and packing;
averaging is what makes the number comparable across batches, across runs, and across model sizes.

**Loss and perplexity — the same number in two costumes.** Perplexity is the loss exponentiated:

```
  ppl = exp(L)          and equivalently          L = ln(ppl)
```

**Stated plainly.** "Perplexity is the effective number of tokens the model is choosing
between at each step — as if it narrowed a 32,000-token vocabulary down to a shortlist of that size
and then guessed uniformly from the shortlist."

| Symbol | What it is |
|--------|------------|
| `exp(x)` | The inverse of `ln`. Undoes the log that turned probabilities into scores |
| `ppl` | Effective branching factor — the size of the model's shortlist |
| `L` | Mean per-token cross-entropy, in nats (natural log units) |

**Walk one example.** Reuse the loss values this module already quotes:

```
  loss L    ppl = exp(L)   read as
  --------  -------------  ---------------------------------------------------
  0.00       1.0           perfect: exactly one candidate, always right
  0.84       2.3           our 3-token example above: coin-flip-ish
  2.00       7.4           "choosing among ~7 plausible tokens" (case study 2 final)
  2.10       8.2           the 200B-token checkpoint
  3.10      22.2           the 10B-token checkpoint
 10.40  32,900            random init: ~ the whole 32k vocab, uniform
```

Two things fall out of this table that interviewers probe for. First, **loss is logarithmic, so
small-looking loss drops are large**: 2.10 -> 2.00 looks like nothing but shrinks the shortlist from
8.2 to 7.4 candidates, roughly a 10% cut in branching. Second, **a random model's perplexity is its
vocabulary size** — `ln(32000) = 10.37`, which is why the 10.4 starting loss in case study 2 is the
expected "knows nothing" value and not a bug. A reported starting loss meaningfully *above* `ln(V)`
is the bug: it means the initialization is not producing a near-uniform output distribution. The same identity explains the case study 1 target:
domain perplexity 24.3 -> 15.7 is a loss drop of `ln(24.3) - ln(15.7) = 3.19 - 2.75 = 0.44`.

Properties:
- Naturally autoregressive — model generates text by repeating this prediction
- All tokens in a batch contribute to loss (efficient)
- Used by: GPT, LLaMA, Mistral, Claude, Gemini, all modern generation models

### 4.2 Masked Language Modeling (MLM) — BERT-style

Randomly mask 15% of tokens; predict the masked tokens:

```
Input:  "The [MASK] brown fox [MASK] over"
Target:      "quick"           "jumps"
```

Properties:
- Bidirectional context — better for understanding tasks
- Only ~15% of tokens contribute to loss (less efficient)
- Cannot generate text autoregressively
- Used by: BERT, RoBERTa, DeBERTa, embedding models

### 4.3 Fill-in-the-Middle (FIM) — Code models

Rearrange training examples so the model learns to complete a middle section given prefix + suffix:

```
Original: [PREFIX] [MIDDLE] [SUFFIX]
FIM-SPM:  [SUFFIX] [PREFIX] [MIDDLE]   (suffix-prefix-middle)

Example:
Prefix:  "def factorial(n):\n    if n == 0:\n"
Middle:  "        return 1\n"
Suffix:  "    return n * factorial(n-1)"

Model must predict the middle given prefix and suffix
```

Used by: CodeLLaMA, Starcoder, DeepSeek-Coder. Enables IDE completion features where the cursor is in the middle of existing code.

### 4.4 Multi-Token Prediction (MTP)

Instead of predicting only the next single token, the model predicts the next N tokens (typically N=4 in Meta's formulation). There are **two distinct designs, and interviews confuse them constantly**:

- **Parallel heads (Gloeckle et al. 2024, Meta):** N independent output heads (each a transformer layer) sit on one shared trunk and predict positions +1..+N *in parallel*, independently of each other. This is the design drawn below.
- **Sequential modules (DeepSeek-V3, 2024):** the extra module for depth k takes the previous depth's hidden state, so the **full causal chain is preserved at each prediction depth**. The paper is explicit that this differs from Gloeckle et al.'s parallel independent heads. Each MTP module contains a Transformer block, and shares the embedding layer and output head with the main model — it is not a bare linear projection.

The parallel form:

```
Text: "The quick brown fox jumps over"

Standard CLM (next-1):
  Input:  "The quick brown fox"
  Target: "jumps"

Multi-Token Prediction (N=4):
  Input:  "The quick brown fox"
  Head 1 target: "jumps"    (position +1)
  Head 2 target: "over"     (position +2)
  Head 3 target: "the"      (position +3)
  Head 4 target: "lazy"     (position +4)

Architecture:
  [Shared Transformer Trunk]
          |
    +-----+-----+-----+
    |     |     |     |
  [H1]  [H2]  [H3]  [H4]
   +1    +2    +3    +4

Loss = Σ_{k=1}^{N} L_k (cross-entropy for each head)
```

**What the formula is telling you.** "Run the same next-token loss once per head — head 1 graded on the token one step ahead, head 2 on two steps ahead, and so on — then add the grades together."

Each `L_k` is exactly the cross-entropy from §4.1; the only change is which target it is compared against. Nothing new is being optimized, the same trunk is just being asked a harder question N ways at once.

| Symbol | What it is |
|--------|------------|
| `Σ_{k=1}^{N}` | Loop the head index `k` from 1 to N and add every term |
| `k` | Which head — equivalently, how many positions into the future it predicts |
| `N` | Number of heads, typically 4 |
| `L_k` | Head `k`'s own cross-entropy against the token at position `+k` |

**Walk one example.** N = 4 on `"The quick brown fox"`, per-head cross-entropy:

```
  head   predicts   target   L_k     comment
  -----  ---------  -------  ------  --------------------------------------
  H1        +1      "jumps"   1.90   easiest: nearest token
  H2        +2      "over"    2.40   harder
  H3        +3      "the"     2.80   harder still
  H4        +4      "lazy"    3.30   hardest: furthest into the future

  Loss = 1.90 + 2.40 + 2.80 + 3.30 = 10.40
```

The `L_k` column rising monotonically with `k` is the expected, healthy shape — the future gets less
predictable the further out you look. If `L_4` were as low as `L_1` you would suspect a target
off-by-one bug, not a brilliant model.

Properties:
- Forces the model to plan ahead — to predict token +3 correctly, the model must implicitly reason about what tokens +1 and +2 will be, improving coherence on longer sequences
- Enables faster inference via self-speculative decoding: the auxiliary heads draft candidates that the main head verifies. Gloeckle et al. measure **up to 3x** (3.0x on code, ~2.5 of 3 suggested tokens accepted). DeepSeek-V3 reports an **85-90% acceptance rate** for its second-token prediction and **1.8x TPS**.
- Training overhead is implementation-dependent, not inherent: Gloeckle et al. report **no training-time overhead** using a memory-efficient implementation that runs the per-head forward/backward sequentially, cutting peak head-related GPU memory from O(nV+d) to O(V+d)
- Can be used as either the primary training objective or as an auxiliary loss alongside standard CLM
- Quality: Gloeckle et al.'s 13B 4-token model solves **12% more HumanEval** and **17% more MBPP** problems than a comparable next-token model
- Used by: DeepSeek-V3 (sequential MTP as an auxiliary loss, depth D=1 — i.e. it predicts 2 tokens total — with loss weight lambda 0.3 for the first 10T tokens then 0.1 for the remaining 4.8T), Meta's research models

---

## 5. Architecture Diagrams

### Pre-Training Data Pipeline

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    WEB["Web Crawls\n(Common Crawl)"]
    BOOKS["Books\n(Books3, Project Gutenberg)"]
    CODE["Code\n(GitHub, The Stack)"]
    SCI["Scientific Papers\n(arXiv, PubMed)"]
    WIKI["Wikipedia + Wikidata\n+ curated datasets"]

    DEDUP["Collection & Deduplication\nURL dedup · MinHash · exact substring dedup"]
    FILTER["Quality Filtering\nlang-ID · perplexity · classifier · heuristics · PII removal"]
    MIX["Data Mixing & Sampling\nweb 50% · code 20% · books 15% · oversample high-quality"]
    PACK["Tokenization & Packing\n4096-token fixed-length sequences, shuffled across docs"]
    TRAIN["Pre-Training"]

    WEB & BOOKS & CODE & SCI & WIKI --> DEDUP
    DEDUP --> FILTER --> MIX --> PACK --> TRAIN

    class WEB,BOOKS,CODE,SCI,WIKI io
    class DEDUP,FILTER,MIX,PACK mathOp
    class TRAIN train
```

Roughly 10 % of raw Common Crawl survives the one published end-to-end pipeline (RefinedWeb, whose Figure 2 reports that Macrodata Refinement "removes nearly 90 % of the documents originally in CommonCrawl"); more aggressive pipelines keep only a few percent. High-quality sources (Wikipedia, curated books) are oversampled to compensate for their small volume.

### Learning Rate Schedule
```mermaid
xychart-beta
    title "LR schedule: linear warmup, then cosine decay (peak 3e-4)"
    x-axis "percent of total training steps" [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    y-axis "learning rate (x 1e-4)" 0 --> 3.2
    line [0, 2.96, 2.78, 2.49, 2.12, 1.69, 1.27, 0.87, 0.57, 0.37, 0.30]
```
Peak LR: 1e-4 to 3e-4 (depends on model size); warmup: 1-2% of total steps; final LR: ~10% of peak (or 0). The short linear ramp protects Adam's uncalibrated moment estimates early on; the long cosine tail keeps mid-training exploration high and anneals gently at the end. (Sampled every 10% of steps, so the 1-2% warmup spike to 3.0 sits inside the first segment — the ramp is far steeper than one chart segment wide.)

The curve above is two formulas glued at the warmup boundary (this is exactly what `lr_at_step` in the §14 code implements):

```
  if step < warmup:
      lr = peak_lr x (step / warmup)                          <- linear ramp up

  else:
      progress = (step - warmup) / (total_steps - warmup)     <- 0.0 .. 1.0
      cosine   = 0.5 x (1 + cos(pi x progress))               <- 1.0 .. 0.0
      lr       = min_lr + cosine x (peak_lr - min_lr)         <- decay down
```

**What this actually says.** "Ramp the step size up from zero over the first couple of percent of training, then ride it back down along the first half of a cosine wave — fast in the middle, gentle at both ends — until it lands on a small floor instead of zero."

| Symbol | What it is |
|--------|------------|
| `lr` | How big a step the optimizer takes along the gradient |
| `peak_lr` | The maximum, reached at the end of warmup. 1e-4 to 3e-4 here |
| `min_lr` | The floor, `min_lr_ratio x peak_lr` = 10% of peak in the §14 config |
| `warmup` | Length of the linear ramp. 2,000 steps in both case studies |
| `progress` | Fraction of post-warmup training done, 0 at the start, 1 at the end |
| `pi x progress` | Maps that fraction onto 0 .. pi radians — the first half of a cosine |
| `cos` | Wave that runs `+1 -> 0 -> -1` over 0 .. pi |
| `0.5 x (1 + cos(...))` | Rescales that `+1 .. -1` swing onto a clean `1.0 .. 0.0` multiplier |

**Walk one example.** Case study 1's schedule: peak 1e-5, min_lr_ratio 0.1 (so min 1e-6),
warmup 2,000, total 250,000 steps:

```
  step      progress   cos(pi x progress)   multiplier   lr
  --------  ---------  -------------------  -----------  --------
  0            --             --               --        0.0        (ramp start)
  1,000        --             --               --        0.5e-5     (halfway up ramp)
  2,000       0.000        +1.000            1.000       1.00e-5    (peak, ramp done)
  64,000      0.250        +0.707            0.854       0.87e-5
  126,000     0.500         0.000            0.500       0.55e-5    (halfway: half of peak)
  188,000     0.750        -0.707            0.146       0.23e-5
  250,000     1.000        -1.000            0.000       0.10e-5    (the min_lr floor)
```

**Why the cosine shape rather than a straight line.** A linear decay spends its whole life falling;
the cosine is deliberately *flat at both ends*. The flat top holds the LR near peak through
mid-training, where the model is still exploring and large steps pay off — note that at 25% of the
way through it is still at 85% of peak, whereas linear would be at 75%. The flat bottom means the
last few percent of steps barely move the weights, letting the model settle into a minimum instead
of bouncing around it. Remove the warmup half and the classic failure appears: Adam's variance
estimate is built from a handful of gradients, its preconditioner is garbage, and a full-size step
from random init sends the loss to NaN in the first few hundred steps. Remove the decay half and
the loss plateaus noisily forever — the model keeps stepping over the minimum it is trying to reach.

---

## 6. How It Works — Detailed Mechanics

### Data Quality and Filtering

**Web data (Common Crawl) quality pipeline:**
```
Raw CC crawl: ~2.0-2.3B pages / ~345 TiB uncompressed HTML per monthly snapshot
               (CC-MAIN-2026-12: 1.97B pages, 344.64 TiB)
               (RedPajama-V2 spans 84 snapshots -> 30T deduplicated tokens, ~360B/snapshot)
  |
  v  URL filtering (known-quality domains upweighted)
  |
  v  Language identification (fastText or CLD3)
  |
  v  Deduplication:
     MinHash over n-gram shingles (5-grams are typical), Jaccard threshold 0.8
     Remove documents with >80% overlap with any other
  |
  v  Quality classifier (trained on curated positive examples):
     Reddit upvotes as proxy for quality (WebText/OpenWebText)
     Wikipedia/books as high-quality reference
  |
  v  ~10% of raw CC survives RefinedWeb's published pipeline; more aggressive
     pipelines keep 1-5% (GPT-3 kept 570GB out of 45TB of compressed plaintext)
     — either way, still trillions of tokens
```

### Data Mixture Optimization

The composition and weighting of training data domains has a direct, measurable impact on model capabilities. Not all tokens are equally valuable — a carefully optimized mixture produces significantly better models than uniform sampling.

**Known effective mixtures:**
```
LLaMA 1 recipe (Touvron et al. 2023, Table 1 — sampling proportions):
  67.0% English CommonCrawl  (general knowledge, conversational ability)
  15.0% C4                    (a second, differently-filtered web crawl)
   4.5% GitHub                (reasoning, structured output, logic)
   4.5% Wikipedia             (factual accuracy, entity knowledge)
   4.5% Books                 (Gutenberg + Books3; long-range coherence)
   2.5% ArXiv                 (technical depth, citation patterns)
   2.0% StackExchange         (Q&A, applied problem solving)
  Note: 82% of the mix is web text; code is only 4.5%. Later models moved
  sharply the other way — Llama 3's final mix is ~50% general knowledge,
  25% math/reasoning, 17% code, 8% multilingual (Llama 3 paper §3.1.2).

The Pile (EleutherAI):
  Manually curated mixture across 22 sources
  Explicit upsampling of high-quality domains (books, Wikipedia)
  Deliberate inclusion of niche domains (GitHub, StackExchange, USPTO patents)
```

**Algorithmic mixture optimization — DoReMi (Xie et al., 2023):**
1. Train a small reference model (280M params) on the baseline domain weights
2. Train a second 280M proxy model with group-DRO, which upweights domains where the proxy's excess loss over the reference is largest
3. Apply the learned domain weights to train the full-scale model (8B in the paper — 30x the proxy)
4. Result on The Pile: **+6.5 percentage points** average one-shot downstream accuracy, and the baseline accuracy is reached with **2.6x fewer training steps** — without increasing total data volume

**Key findings on domain weighting:**
- Raising the code fraction is widely reported to improve non-code reasoning as well, and frontier mixes have moved that way (Llama 3: 17% code + 25% math/reasoning). The size of the transfer is model- and eval-specific; treat any single "+X% on GSM8K" number as an internal measurement, not a published constant.
- Diminishing returns apply: after enough tokens of a single domain, adding more of the same domain yields decreasing benefit — better to diversify
- Quality filtering is a multiplier: a 10x smaller high-quality dataset can match a 10x larger unfiltered dataset (the Phi "textbooks are all you need" insight)
- Late-stage mixture shifts (increasing code/math fraction in the final 10-20% of training) can sharpen specific capabilities without degrading general performance

### Training Dynamics

**Gradient clipping**: Clip gradient norm to ~1.0. Prevents gradient explosion, especially early in training.

**Loss spikes**: Loss occasionally spikes up then recovers. It is tempting to blame "bad data," but PaLM's authors ran the ablation and rejected that explanation: replaying the same batches from an earlier checkpoint produced no spike, so a spike is an interaction between a specific batch and a specific parameter state, not a property of the batch alone (PaLM, Chowdhery et al. 2022, §5.1). The practical mitigation is the same either way — roll back a few hundred steps and skip the surrounding batches.

**Batch size ramp-up**: Start with small batch size (256K tokens), linearly increase to target (4M tokens) over first few billion tokens. Improves training stability.

**Effective batch size and gradient accumulation**: no GPU can hold a 4M-token batch, so the batch is assembled in pieces and the gradients are summed before a single optimizer step:

```
  tokens_per_gpu_step = micro_batch_sequences x sequence_length
  effective_tokens    = tokens_per_gpu_step x num_gpus x grad_accum_steps
```

**In plain terms.** "Each GPU chews a small slice it can actually fit, you do that several times in a row without stepping the optimizer, and the gradients pile up until together they represent the huge batch you actually wanted."

| Symbol | What it is |
|--------|------------|
| `micro_batch_sequences` | Sequences one GPU processes in one forward/backward. Limited by VRAM |
| `sequence_length` | Tokens per sequence — 4,096 in case study 1, 8,192 in case study 2 |
| `num_gpus` | Data-parallel replicas, each on a different slice |
| `grad_accum_steps` | Backward passes accumulated before one `optimizer.step()` |
| `effective_tokens` | The batch size that actually matters for the LR — the *only* one to quote |

**Walk one example.** Case study 1's config, straight from `ContinuedPretrainingConfig`:

```
  micro_batch_sequences   =        2
  sequence_length         =    4,096
  tokens_per_gpu_step     = 2 x 4,096              =      8,192 tokens
  num_gpus                =       32
  tokens_per_gpu_pass     = 8,192 x 32             =    262,144 tokens
  grad_accum_steps        = 2,000,000 / 262,144    =          7 (integer division)
  effective_tokens        = 262,144 x 7            =  1,835,008 tokens per step

  total_steps             = 500e9 / 2e6            =    250,000 optimizer steps
```

The trap this arithmetic exposes: `grad_accum_steps` is an integer, so the *realized* batch is
1.84M tokens, not the 2M the config asks for — an 8% shortfall that quietly changes the step count
and the LR schedule's endpoint. Also note that doubling `grad_accum_steps` makes each optimizer step
twice as expensive in wall-clock but does **not** change how many steps you take per token, so the
schedule must be recomputed whenever it moves. And the LR is tuned against `effective_tokens`, never
against `micro_batch_sequences` — someone who halves the micro-batch to fix an OOM, doubles
accumulation to compensate, and leaves the LR alone has changed nothing and should see no drift; the
person who halves the micro-batch and forgets the accumulation bump has silently halved the batch
and is now training at double the effective LR.

**AdamW update rule**: the optimizer that actually applies those gradients keeps two running averages per weight:

```
  m_t = beta1 x m_{t-1} + (1 - beta1) x g_t              <- momentum (mean of gradients)
  v_t = beta2 x v_{t-1} + (1 - beta2) x g_t^2            <- variance (mean of squared gradients)

  m_hat = m_t / (1 - beta1^t)                            <- bias correction
  v_hat = v_t / (1 - beta2^t)

  w_t = w_{t-1} - lr x [ m_hat / (sqrt(v_hat) + eps) + weight_decay x w_{t-1} ]
```

**Read it like this.** "Step in the direction gradients have been pointing lately, but scale that step down for any weight whose gradient has been noisy or large — and separately shrink every weight a little each step regardless."

| Symbol | What it is |
|--------|------------|
| `g_t` | This step's gradient for one weight |
| `m_t` | Running mean of gradients. The momentum term — smooths out batch noise |
| `v_t` | Running mean of *squared* gradients. A per-weight noise/magnitude meter |
| `beta1` | Momentum decay, 0.9. Memory of roughly the last 10 steps |
| `beta2` | Variance decay, 0.95 here (0.999 in generic Adam). Memory of ~20 steps |
| `m_hat`, `v_hat` | Bias-corrected versions — `m_0` starts at zero, so early estimates read too small |
| `eps` | Tiny floor, 1e-8. Stops division by zero when a weight's gradient is dead |
| `sqrt(v_hat)` | Typical gradient magnitude for this weight — the per-weight step normalizer |
| `weight_decay` | 0.1 here. Pulls weights toward zero; the "W" in AdamW keeps it out of `m`/`v` |

**Walk one example.** One weight over three steps, `beta1 = 0.9`, `beta2 = 0.95`, `lr = 1e-5`:

```
  step  g_t     m_t                        v_t                          m_hat/(sqrt(v_hat)+eps)
  ----  ------  -------------------------  ---------------------------  -----------------------
  1     0.020   0.9x0      +0.1x0.020      0.95x0     +0.05x0.000400
                = 0.00200                  = 0.0000200
                m_hat = 0.00200/0.100      v_hat = 0.0000200/0.0500
                      = 0.0200                   = 0.000400              0.0200/0.0200 = +1.00
  2     0.018   0.9x0.00200+0.1x0.018      0.95x0.0000200+0.05x0.000324
                = 0.00360                  = 0.0000352
                m_hat = 0.00360/0.190      v_hat = 0.0000352/0.0975
                      = 0.0189                   = 0.000361              0.0189/0.0190 = +0.995
  3     -0.040  0.9x0.00360+0.1x(-0.040)   0.95x0.0000352+0.05x0.001600
                = -0.00076                 = 0.000113
                m_hat = -0.00076/0.271     v_hat = 0.000113/0.143
                      = -0.00280                 = 0.000795              -0.00280/0.0282 = -0.099

  step 1 weight change = -1e-5 x (+1.00)  = -1.00e-5
  step 3 weight change = -1e-5 x (-0.099) = +9.90e-7
```

Two behaviours to name in an interview. First, **the normalized step is roughly +/-1 whenever the
gradient is behaving consistently** (steps 1 and 2), which is why Adam's `lr` is a near-absolute
bound on how far any weight can move per step — that is what makes 3e-4 a sane number across wildly
different layers. Second, when a **large outlier gradient arrives** (step 3), `v_t` jumps
immediately while `m_t` barely turns, so the normalized step *shrinks* to 0.099 rather than
exploding: Adam automatically distrusts weights whose gradients just got noisy. This is also why
`beta2` is lowered from 0.999 to 0.95 for LLMs — a shorter variance memory lets the optimizer react
to a loss spike within tens of steps instead of thousands.

**Why `eps` and bias correction exist.** Drop `eps` and any weight whose gradients have been exactly
zero for a while divides by zero on its first nonzero gradient — instant NaN, and a NaN in one
weight propagates to the whole model in one forward pass. Drop bias correction and `m_1 = 0.1 x g_1`
while `v_1 = 0.05 x g_1^2`, so the very first steps are scaled by roughly `0.1 / sqrt(0.05) = 0.45`
of what they should be, wrongly and inconsistently across the two moments — the training run starts
with hundreds of miscalibrated steps, which is precisely the hole that LR warmup was invented to
paper over.

**BF16 vs FP16 training**: BF16 (Brain Float16) has the same exponent range as FP32 but fewer mantissa bits. More numerically stable than FP16 for training. Standard for modern LLM training.

### Training Loss Diagnostics

Monitoring the loss curve and related signals is critical for catching problems early and avoiding wasted compute.

**Healthy loss curve**: Smooth exponential decay with small noise. The curve follows a power law: L(t) ~ t^(-alpha), where alpha depends on model size and data quality. Noise amplitude should be consistent — increasing noise suggests data pipeline issues.

**What it means.** "Every time you multiply the training tokens (or parameters, or compute) by some fixed factor, the loss shrinks by a fixed factor — never by a fixed amount. Progress is bought in multiples, not in increments."

The same shape appears in all three scaling-law forms, which is why the Chinchilla work could fit them jointly:

```
  L(t) ~ t^(-alpha)          loss vs training steps / tokens seen
  L(N) = (N_c / N)^alpha_N   loss vs parameter count      (N_c = a fitted constant)
  L(D) = (D_c / D)^alpha_D   loss vs dataset size         (D_c = a fitted constant)
```

| Symbol | What it is |
|--------|------------|
| `L(N)` | The loss you would reach with `N` parameters, trained properly |
| `N` | Model parameters. `D` = training tokens, `t` = steps, `C` = compute FLOPs |
| `N_c`, `D_c` | Fitted constants that set the scale — where the curve crosses `L = 1` |
| `alpha` | The exponent. How steeply loss falls; measured near 0.05-0.10 for LLMs |
| `x^(-alpha)` | The power law itself. Negative exponent = grows -> loss falls |
| `~` | Proportional to, ignoring the constant out front |

**Walk one example.** Take `alpha = 0.076` (Kaplan et al. 2020's fitted parameter exponent
`alpha_N`; Chinchilla's joint fit uses a different parametrization and reports a steeper exponent
for `N`, so do not quote 0.076 as "Chinchilla's") and
ask what each 10x in model size buys:

```
  N          relative loss = N^(-0.076)      loss (scaled to 3.00 at 1B)   delta
  ---------  ------------------------------  ---------------------------   ------
  1B         reference                            3.00                       --
  10B        10^(-0.076) = 0.839                  2.52                     -0.48
  100B       10^(-0.152) = 0.705                  2.11                     -0.41
  1T         10^(-0.228) = 0.592                  1.78                     -0.33

  ppl at 1B  = exp(3.00) = 20.1
  ppl at 1T  = exp(1.78) =  5.9
```

**What this means practically.** Every 10x in parameters buys roughly the same *fraction* off the
loss — about 16% each time — so the absolute gains visibly shrink (0.48, then 0.41, then 0.33) even
though the underlying law has not changed at all. Straight-line progress on a loss chart therefore
requires *exponentially* growing budgets, which is the entire economics of frontier training in one
sentence. It also tells you how to read a run in flight: plot loss vs tokens on **log-log axes** and
a healthy run is a straight line whose slope is `-alpha`. A curve that bends *up* off that line
means the run is degrading (bad data, LR too high); a curve that flattens *early* means you have
saturated what this model size can extract and the fix is a bigger `N`, not more `D`.

**Loss spike classification:**

| Spike Type | Magnitude | Duration | Cause | Action |
|------------|-----------|----------|-------|--------|
| Transient spike | 1-5% | 10-50 steps | Bad data batch | Self-recovers; log and investigate batch |
| Moderate spike | 5-20% | 50-500 steps | Data corruption or LR issue | Roll back checkpoint, skip data |
| Divergence | >20% | Does not recover | LR too high, NaN gradients | Stop training, diagnose, restart |
| Plateau | 0% change | 500+ steps | Underfitting or data exhaustion | Check data pipeline, adjust LR |

**Gradient norm monitoring**: Sudden gradient norm spikes often lead the loss spike by some steps, providing an early warning signal (the lead time is run-specific — measure it on your own run rather than assuming a fixed number of steps). Tracking per-layer gradient norms helps isolate which part of the network is destabilizing — attention layers and the final output projection are common culprits.

**Checkpoint strategy**: Save full checkpoints every 1000-2000 steps (in addition to the 30-60 minute hardware-failure checkpoints). After detecting a spike, reload the most recent clean checkpoint and skip the problematic data shard. Some teams maintain a rolling window of the last 3-5 checkpoints to avoid losing too much progress.

**Production heuristic**: If the training loss has not decreased in 500 steps, investigate the data pipeline first (corrupted shards, tokenizer issues, data loader stalls) before adjusting hyperparameters — pipeline faults are cheap to rule out and expensive to miss, whereas an LR change invalidates the schedule for the rest of the run.

### Multi-Token Prediction — Training Mechanics

When using MTP as an auxiliary loss, the total training loss combines the standard next-token loss with the multi-token heads:

```
L_total = L_CLM + lambda × (1/N) × Σ_{k=2}^{N} L_k

Where:
  L_CLM   = standard next-token prediction loss
  L_k     = cross-entropy loss for the k-th prediction head
  lambda  = auxiliary loss weight (DeepSeek-V3: 0.3, then 0.1 late in training)
  N       = number of prediction heads (4 in Meta's formulation; DeepSeek-V3 used
            depth D=1, i.e. exactly one extra module)
```

**Put simply.** "Train normally on the next token, then average the extra heads' losses together and add a small fraction of that as a nudge — the future-planning signal helps, but it must never outvote the objective you actually care about."

| Symbol | What it is |
|--------|------------|
| `L_total` | What gradient descent actually minimizes |
| `L_CLM` | The §4.1 next-token loss. The real objective, weight fixed at 1 |
| `lambda` | Auxiliary weight, 0.1-0.3. How loud the side objective is allowed to be |
| `(1/N) x Σ` | Mean, not sum, so changing `N` does not change the auxiliary's loudness |
| `Σ_{k=2}^{N}` | Starts at **2** — head 1 is already counted as `L_CLM` |

**Walk one example.** `lambda = 0.2`, `N = 4`, using the per-head numbers from §4.4:

```
  L_CLM (= head 1)                       = 1.90
  heads 2..4:  2.40, 2.80, 3.30

  mean of aux heads  = (2.40 + 2.80 + 3.30) / 4   = 2.125
  aux contribution   = 0.2 x 2.125                = 0.425
  L_total            = 1.90 + 0.425               = 2.325

  aux share of total = 0.425 / 2.325              = 18%
```

**Why `lambda` and the `1/N` both exist.** Without `lambda`, the three auxiliary heads collectively
carry more loss mass than the head you will actually serve at inference, and the trunk optimizes for
predicting four steps ahead at the expense of predicting one — measurable as worse next-token
perplexity on the exact metric you ship. Without the `1/N`, raising `N` from 4 to 8 would silently
double the auxiliary's influence, so any head-count experiment would be confounded with a lambda
change. Note also the `k=2` lower bound: including head 1 twice would double-weight the primary
objective and make `lambda` mean something different than intended.

In Meta's parallel formulation each auxiliary head is its own transformer layer over the shared trunk's hidden state, and the heads do not attend to each other — they independently predict their assigned future position. Running the heads' forward/backward passes sequentially keeps peak memory (and, per Gloeckle et al., training time) close to the next-token baseline. In DeepSeek-V3's sequential formulation the depth-k module instead consumes the depth-(k-1) hidden state, preserving the causal chain, and shares the embedding and output head with the main model. During inference, only the next-token head is required for standard autoregressive generation, but the extra heads can drive self-speculative decoding — up to 3x in Gloeckle et al., 1.8x TPS in DeepSeek-V3.

### Compute Scaling

The Chinchilla (Hoffmann et al. 2022) formula for compute-optimal training, applied to the `C = 6ND` compute identity from Kaplan et al. 2020:
```
For compute budget C (in FLOPs), using C = 6ND and D = 20N:
  C = 6N(20N) = 120N^2
  N_optimal ≈ (C / 120)^0.5   (model params)
  D_optimal ≈ 20 × N_optimal  (training tokens)

For 1e24 FLOPs:
  N ≈ 91B parameters
  D ≈ 1.8T tokens
  check: 6 × 91e9 × 1.8e12 ≈ 1e24 FLOPs

For 5.9e23 FLOPs (the budget behind the widely-quoted pair):
  N ≈ 70B parameters
  D ≈ 1.4T tokens

In practice:
  Llama 3 8B trained on ~15T tokens = ~1,875 tokens/param, i.e. ~94x the
  Chinchilla ratio of 20 tokens/param (Chinchilla-optimal for 8B is ~160B tokens)
  Rationale: inference on a smaller, longer-trained model is cheaper per token
```

Everything above rests on one budget identity that every pre-training interview reaches for:

```
  C ~= 6 x N x D
```

**The idea behind it.** "The total cost of a training run is just: how big the model is, times how much text it reads, times six. Nothing about architecture, optimizer, or cluster enters — only params and tokens."

| Symbol | What it is |
|--------|------------|
| `C` | Total training compute, in FLOPs (floating-point operations) |
| `N` | Model parameters — 7e9 for a 7B model |
| `D` | Training tokens seen — counted with repeats, not unique tokens |
| `6` | FLOPs spent per parameter per token. Derived below |
| `~=` | Ignores attention's quadratic term, negligible until context >> hidden dim |

**Where the 6 comes from.** Each weight participates in one multiply and one add — 2 FLOPs — every
time a token passes through it. That happens three times per training token:

```
  pass                 what it computes                         FLOPs per param per token
  -------------------  ---------------------------------------  -------------------------
  forward              activations from inputs and weights                 2
  backward (inputs)    gradient w.r.t. the layer's input                   2
  backward (weights)   gradient w.r.t. the layer's weights                 2
                                                                          --
                                                            total          6
```

This also settles a question interviewers like: **inference costs 2N FLOPs per token, training costs
6N** — the backward pass is exactly twice the forward pass, so training a token is 3x the cost of
generating one. (Gradient checkpointing trades memory for a repeated forward pass and pushes the
constant toward 8, which is one reason real MFU lands below the 6ND-implied ceiling.)

**Walk one example — 7B model, Chinchilla-optimal 140B tokens.** The §12 answer quotes 140B as
compute-optimal for 7B (20 tokens per parameter); here is that budget end to end on A100s:

```
  N  = 7e9 params
  D  = 140e9 tokens                (= 20 x N, the Chinchilla ratio)
  C  = 6 x 7e9 x 1.4e11            = 5.88e21 FLOPs

  A100 80GB BF16 peak              = 312 TFLOPS = 3.12e14 FLOPS
  ideal GPU-seconds  = 5.88e21 / 3.12e14           = 1.88e7 s
  ideal GPU-hours    = 1.88e7 / 3600               =  5,235 GPU-hours

  at 45% MFU (the realistic figure this module uses):
  real GPU-hours     = 5,235 / 0.45                = 11,630 GPU-hours
  on 512 GPUs        = 11,630 / 512                =     23 hours wall clock
  at $2/GPU-hour     = 11,630 x 2                  = $23,300
```

Now re-run the same arithmetic for case study 2's actual choice of 400B tokens and the shape of the
tradeoff appears immediately: `C = 6 x 7e9 x 4e11 = 1.68e22` FLOPs, 2.86x the Chinchilla budget,
33,000 GPU-hours at 45% MFU. That is the exact number §14 quotes — and the 3x spend buys a model
that is cheaper to *serve* forever, because serving cost scales with `2N` and is completely
indifferent to how many tokens it was trained on.

**Where the `(C/120)^0.5` comes from.** You never memorize it — you derive it in ten seconds from
`C = 6ND` by substituting Chinchilla's `D = 20N`, which leaves one unknown. Writing `(C/6)^0.5`
instead (forgetting the substitution) overshoots badly: it returns 408B params at `C = 1e24`,
more than four times the right answer.

```
  C = 6 x N x (20 x N) = 120 x N^2
  N = sqrt(C / 120)

  for C = 1e24:   N = sqrt(1e24 / 120) = sqrt(8.33e21) = 9.1e10  ~= 70-90B params
                  D = 20 x N                           = 1.8e12  ~= 1.4-1.8T tokens
```

**Why the square root is the whole story.** Because `C` grows with `N^2` once you hold the token
ratio fixed, **10x more compute buys only ~3.2x more parameters** — the other 3.2x has to go into
tokens. That is Chinchilla's central correction to GPT-3: OpenAI spent its 10x almost entirely on
`N`, giving a 175B model trained on 300B tokens (a ratio of 1.7 tokens per parameter, not 20), which
is why a 70B Chinchilla beat it. Split the budget wrong in either direction and you waste compute:
too much `N` and the model is undertrained (the GPT-3 failure), too much `D` and you are paying to
re-teach a model that has run out of capacity to absorb it (the flattening curve from the power-law
section above).

### Staged Pre-Training: Main Run, Context Extension, Annealing

Everything above describes one continuous run. Frontier pre-training is not one run — it is three
stages with different sequence lengths and different data mixes, and the split exists for a purely
economic reason: attention cost is quadratic in sequence length, so training the whole corpus at the
final context length would be unaffordable.

```
  stage                    context     share of tokens    what it is buying
  ----------------------   ---------   ---------------    -------------------------------
  1. initial pre-training  4K -> 8K    ~95 %              knowledge, language, reasoning
  2. context extension     8K -> 128K  ~5 %               the ability to use a long window
  3. annealing / decay     128K        <<1 %              final quality on the hardest data
```

**Walk the real numbers — Llama 3 405B (Meta, paper §3.4).** 15.6T tokens total:

```
  1. INITIAL PRE-TRAINING
       batch 4M tokens, sequences of 4,096
       -> 8M tokens / 8,192 after the first     252 B tokens
       -> 16M tokens after                    2,870 B tokens
       The ramp is deliberate: short sequences and small batches while the loss
       surface is still violent, then longer and larger once it is stable.

  2. LONG-CONTEXT PRE-TRAINING              ~  800 B tokens   (~5 % of the run)
       six stages, 8K -> 128K
       advance to the next stage only when BOTH hold:
         a. short-context evals have fully recovered
         b. needle-in-a-haystack is solved perfectly at the current length

  3. ANNEALING                                    40 M tokens  (0.00026 % of the run)
       LR linearly -> 0, context held at 128K
       data mix re-weighted to upsample the highest-quality sources
       final weights = Polyak average of the checkpoints across this stage
       measured effect on the 8B: +24.0 % GSM8k, +6.4 % MATH validation
       measured effect on the 405B: negligible
```

**The three lessons an interviewer is probing for.** First, **long context is bought, not baked in** —
the 128K window came from 5% of the tokens at the end, which is why "what is your context length"
is a question about the last stage of pre-training, not the architecture alone. Second, **the
extension is gated on not regressing**: a longer window that costs short-context quality is a
failed stage, not a tradeoff to accept. Third, **annealing is disproportionate leverage** — 40M
tokens, 0.00026% of the run, moved an 8B model 24% on GSM8k, which is the sharpest available
statement of "data quality > quantity". Note the scale dependence in the last line: the same
annealing did almost nothing for the 405B, so treat late-stage data tricks as a small-model
amplifier, not a universal law.

---

## 7. Real-World Examples

### GPT-3 (OpenAI, 2020)
- 175B parameters, 570GB of text data (~300B tokens)
- Data mix: CommonCrawl (60%), WebText2 (22%), Books (16%), Wikipedia (3%)
- Training: 3.14 × 10²³ FLOPs on V100 GPUs
- ~$4-5M estimated training cost
- Launched the LLM era; demonstrated few-shot learning at scale

### Llama 3 (Meta, 2024)
- 8B, 70B, 405B variants; 405B pre-trained on 15.6T text tokens for 3.8e25 FLOPs
- Data mix (paper §3.1.2): ~50% general knowledge, 25% math and reasoning, 17% code, 8% multilingual
- 128K context via RoPE scaling
- Open weights (community license)
- 405B trained on up to 16K H100s (80GB, 700W); reported BF16 MFU of 43% / 41% / 38% across the three parallelism stages (paper Table 4)

### Mistral 7B (2023)
- 7B params, outperforms LLaMA 2 13B
- Sliding window attention (SWA) for memory efficiency
- GQA for fast inference
- Apache 2.0 license — fully open

### DeepSeek-V3 (2024)
- 671B parameters, 37B activated per token (MoE); trained on 14.8T tokens
- 2.788M H800 GPU-hours (2.664M pre-training + 119K context extension + 5K post-training) = **$5.576M at an assumed $2/GPU-hour**. The paper is explicit that this "includes only the official training of DeepSeek-V3, excluding the costs associated with prior research and ablation experiments" — it is not the all-in program cost.
- Multi-token prediction as an auxiliary objective (sequential, depth D=1)
- FP8 mixed precision training

---

## 8. Tradeoffs

| Decision | Option A | Option B | Consider |
|----------|----------|----------|---------|
| Model size | Larger (better quality) | Smaller (cheaper inference) | Inference budget |
| Training tokens | More (better quality) | Fewer (cheaper training) | Is model undertrained? |
| Data filtering | Aggressive (cleaner) | Permissive (more data) | Model quality vs. diversity |
| Context length | Short (4K, cheaper) | Long (128K, expensive) | Use case requirements |
| Precision | BF16 (faster) | FP32 (exact) | Always use BF16 for training |

---

## 9. When to Use / When NOT to Use

### Pre-Train From Scratch When:
- Building a truly domain-specialized model (medical, legal, finance) where the knowledge base differs fundamentally
- You have access to billions of domain tokens not available elsewhere
- Regulatory/IP requirements prevent using third-party model weights
- You can afford $1M+ in compute

### Fine-Tune Instead When:
- Adapting a general model for a specific task (cheaper by 100-1000x)
- You have <100B tokens of domain data
- The task is about format/style/following instructions (not learning new knowledge)
- You need results in weeks not months

---

## 10. Common Pitfalls

1. **Training data contamination**: If benchmark test sets are in your training data, evaluation scores are inflated. Run deduplication between training data and all evaluation sets.
2. **Epoch repetition**: For very large models, repeating data (>1 epoch) degrades quality. Use different data mixes across passes.
3. **Imbalanced domain sampling**: Too much low-quality web content drowns out high-quality signal. Careful mixing ratios matter.
4. **Ignoring context packing artifacts**: Naively packing documents can create cross-document attention (token at end of doc A attends to doc B). Use attention masks to prevent this.
5. **Not monitoring training loss curves**: A flat loss for many steps indicates a learning rate issue or data issue.
6. **Hardware failure planning**: With 1000+ GPUs, some will fail. Have checkpointing every 30-60 minutes and automatic restart scripts.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| Megatron-LM | Large-scale LLM training | NVIDIA; tensor/pipeline parallel |
| DeepSpeed | ZeRO optimization, mixed precision | Microsoft; works with PyTorch |
| FSDP | Fully Sharded Data Parallel | PyTorch native; replaces DDP for large models |
| GPT-NeoX | Open-source LLM training | EleutherAI framework |
| Nanotron | LLM training framework | HuggingFace; modern replacement |
| torchtitan | PyTorch-native training platform | `pytorch/torchtitan`; released on PyPI (0.2.x), actively developed |
| Common Crawl | Web data source | ~2.0-2.3B pages, ~345 TiB uncompressed per monthly snapshot |
| The Pile | Curated training dataset | EleutherAI; 825GB diverse text |
| DCLM | DataComp for LM | Curated CC dataset (DCLM-Baseline), strong quality |
| RedPajama-v2 | Open training dataset | Together AI; 30T tokens |

---

## 12. Interview Questions with Answers

**Q: What is the difference between CLM and MLM training objectives?**
**Short:** CLM predicts the next token from only prior context for generation, while MLM masks random tokens and predicts them bidirectionally, useful for understanding but not generation.

A: CLM (Causal Language Modeling) predicts the next token given only previous tokens — unidirectional, autoregressive, enables text generation. MLM (Masked Language Modeling) masks random tokens and predicts them using bidirectional context — better for understanding tasks but can't generate text. Modern LLMs use CLM; embedding/classification models use MLM.

**Q: What are Chinchilla scaling laws and what did they change?**
**Short:** Chinchilla showed compute-optimal training splits equally between model size and tokens (~20 tokens per parameter), proving earlier models like GPT-3 were over-parameterized for their data.

A: Chinchilla (Hoffmann et al. 2022) showed that previous models like GPT-3 were over-parametrized relative to their training data. The optimal compute allocation splits equally between model size and training tokens. For a given compute budget, training a smaller model on more tokens is better than a larger model on fewer tokens. This led to LLaMA-style training: smaller models trained on much more data.

**Q: How do you handle training instability / loss spikes?**
**Short:** Gradient clipping at norm 1.0 is the first defense; recovering from a spike means rolling back to the last checkpoint and skipping the offending batch rather than just lowering the LR.

A: First line of defense is gradient clipping (clip norm to 1.0). For spikes, roll back to the last checkpoint (every 30-60 min) and skip or filter the problematic batch. Long-term, improve data quality filtering to remove pathological examples. Some teams also use gradient norm monitoring to detect spikes before they destabilize training.

**Q: What is data contamination and why is it a problem?**
**Short:** Data contamination is when benchmark test examples leak into training data, inflating evaluation scores; the fix is n-gram deduplication between training data and all benchmarks.

A: Data contamination occurs when evaluation benchmark examples appear in the training set. The model has "seen" the answers, inflating benchmark scores. This is why LLM evaluation is difficult to trust — most teams don't fully audit their training data. Mitigation: run n-gram deduplication between training data and all benchmarks before training.

**Q: Why does repeating pre-training data for multiple epochs hurt large models?**
**Short:** Beyond roughly one epoch, repeated data pushes large models toward memorization instead of generalization, with meaningful gains continuing to about 16 epochs before returns collapse.

A: Beyond roughly one pass, repeated data shifts the model from generalization toward memorization — benchmark gains flatten while verbatim regurgitation risk rises, and heavy repetition can actively degrade quality relative to training on fewer unique tokens. Empirically (Muennighoff et al., 2023, data-constrained scaling), differences are insignificant up to ~4 epochs, meaningful gains continue to roughly 16 epochs, and beyond that returns collapse — so frontier labs plan data volume upfront to keep the main run near 1 epoch. This is the opposite of classic small-data deep learning, where dozens of epochs are normal — at trillion-token scale, unique data is the binding constraint. If you must repeat, repeat only the highest-quality subsets with a different mix per pass.

**Q: Why can naive sequence packing silently hurt model quality even though it improves throughput?**
**Short:** Packing documents without a block-diagonal attention mask lets tokens attend across document boundaries, teaching spurious cross-document dependencies that hurt single-document evaluation.

A: Packing multiple documents into one fixed-length sequence without a block-diagonal attention mask lets tokens at the end of document A attend to document B — the model learns spurious cross-document dependencies that never exist at inference. Training loss can even look better (extra context to exploit) while single-document evaluation gets worse; the code-model case study in §14 measured +2.1 perplexity on single-file eval from exactly this bug. The fix is a per-document attention mask (each document attends only to itself) plus resetting position IDs at document boundaries. Always validate packing changes with an eval on unpacked, single-document inputs.

**Q: Why is BF16 preferred over FP16 for LLM training?**
**Short:** BF16 shares FP32's 8-bit exponent range so it rarely overflows or underflows during training, while FP16's narrower exponent needs loss scaling to stay stable.

A: BF16 has the same 8-bit exponent range as FP32 (handles the dynamic range of gradients and activations), while FP16 has a smaller 5-bit exponent and frequently overflows/underflows during training. FP16 requires loss scaling to avoid underflow; BF16 doesn't. On modern GPUs (A100, H100), BF16 is as fast as FP16 but more numerically stable.

**Q: How does data deduplication impact pre-training quality and what methods are used?**
**Short:** Deduplication removes near-duplicate documents via exact hashing, MinHash/LSH on n-gram shingles, or suffix arrays, cutting memorization risk and wasted compute per token.

Data deduplication removes near-duplicate documents from the training corpus, improving model quality per token while reducing wasted compute. Without dedup, models memorize repeated passages (increasing regurgitation risk) and waste compute on redundant data. Methods: (1) exact dedup — hash each document, remove duplicates (fast but misses paraphrases); (2) MinHash/LSH — approximate dedup using locality-sensitive hashing on n-gram shingles, catches near-duplicates with >80% overlap; (3) suffix array — finds repeated substrings across documents (used by LLaMA). RefinedWeb (Falcon's dataset) demonstrated that an aggressive filtering-plus-dedup pipeline retaining only about 10% of raw Common Crawl produces a corpus that matches curated datasets on downstream quality — note that the ~90% removal is the whole pipeline, with language ID and quality filtering each roughly halving the data before dedup runs. The Pile uses a combination of MinHash and exact dedup.

**Q: How does the Chinchilla scaling law differ from the LLaMA over-training approach, and which is better?**
**Short:** Chinchilla optimizes training compute at ~20 tokens per parameter, while LLaMA deliberately over-trains smaller models on far more tokens to minimize the cheaper, continuous cost of inference.

Chinchilla (Hoffmann et al., 2022) found the compute-optimal ratio is roughly 20 tokens per parameter — a 70B model should train on 1.4T tokens. LLaMA 1 deliberately over-trained smaller models on much more data (6.7B and 13B on 1.0T tokens, 32.5B and 65.2B on 1.4T — far beyond Chinchilla-optimal for the small models). The LLaMA approach is better for inference efficiency: a smaller over-trained model achieves the same quality as a larger Chinchilla-optimal model but is cheaper to serve. Chinchilla optimizes for training compute; LLaMA optimizes for inference compute. Since inference cost dominates in production (training is one-time, inference is continuous), the industry has shifted toward the LLaMA strategy. Llama 3 8B was trained on ~15T tokens — about 1,875 tokens per parameter, roughly 94x the Chinchilla ratio of 20.

**Q: What is curriculum learning in pre-training and does it help?**
**Short:** Easy-to-hard data ordering shows little proven benefit versus random sampling at scale; what reliably helps is optimizing the fixed domain mixture and raising code/math share late in training.

Curriculum learning orders training data from easy to hard, hypothesizing that models learn better with structured progression. In LLM pre-training, this might mean training on simple Wikipedia first, then academic papers, then code. Evidence is mixed: optimizing the domain *mixture* clearly helps — DoReMi reports +6.5 percentage points average one-shot downstream accuracy on The Pile and reaches baseline accuracy 2.6x faster — but that is a better fixed mixture, not an easy-to-hard ordering. Most frontier models (GPT-4, Llama 3) use random sampling with fixed domain proportions, suggesting that at sufficient scale, ordering effects diminish. What does work: starting with high-quality data and maintaining quality throughout training, rather than starting with low-quality data. The most impactful "curriculum" choice is increasing the fraction of code and math data in later training stages, which several models (CodeLLaMA, DeepSeek) use successfully.

**Q: How do you diagnose and recover from training instability (loss spikes) during pre-training?**
**Short:** Diagnose spikes by logging per-layer gradient norms and inspecting the offending batch, then recover by rolling back to a checkpoint 100-1,000 steps earlier and skipping that data.

Training instability manifests as sudden loss spikes — the training loss jumps by 0.5-2.0 and may or may not recover. Causes: (1) learning rate too high for current training stage; (2) data quality issues — a batch with corrupted or adversarial data; (3) numerical overflow in FP16/BF16 (especially with large gradient norms); (4) attention logits growing too large. Diagnosis: log gradient norms per layer (spikes in specific layers indicate the source), check the specific training examples in the spike batch, monitor attention entropy. Recovery strategies: (1) skip the problematic batch and resume; (2) roll back to a checkpoint 100-1000 steps before the spike; (3) reduce learning rate temporarily; (4) add gradient clipping (max_grad_norm=1.0). Prevention: use BF16 instead of FP16 (larger dynamic range), pre-attention LayerNorm (as in LLaMA), and z-loss regularization on attention logits. PaLM's training paper documented 20+ loss spikes during training, each requiring checkpoint rollback.

**Q: What is the impact of training data composition (web, books, code, academic) on model capabilities?**
**Short:** Training data composition directly shapes capabilities — Llama 3's shift to ~50% general knowledge, 25% math/reasoning, and 17% code over Llama 1's 82% web mix improved reasoning broadly.

Training data composition directly determines model strengths — models are what they eat. The two published reference points bracket the range: LLaMA 1 (2023) sampled 82% web (67% CommonCrawl + 15% C4), 4.5% GitHub, 4.5% Wikipedia, 4.5% books, 2.5% arXiv, 2% StackExchange; Llama 3 (2024) shifted hard toward reasoning data with ~50% general knowledge, 25% math and reasoning, 17% code, 8% multilingual. Raising the code and math share is widely reported to improve non-code reasoning too, because code requires explicit step-by-step logic — but the size of that transfer is model- and eval-specific, so treat any single "+X% on GSM8K" figure as an internal measurement. The Phi models ("textbooks are all you need") demonstrated that training on high-quality synthetic textbook data can produce remarkably capable small models. Conversely, too much web crawl without filtering leads to toxic, low-quality outputs. The key insight: beyond a threshold, data quality matters more than raw token count.

**Q: Why do LLMs need learning-rate warmup at the start of training?**
**Short:** Adam's early variance estimates are built from too few gradients to be reliable, so a linear LR warmup over 1-2% of steps prevents full-size updates from diverging a randomly initialized model.

Adam's second-moment estimates are unreliable in the first few hundred steps (built from too few gradient samples), so full-size steps early on are effectively steps with a miscalibrated preconditioner — a common cause of immediate divergence from random initialization. Linear warmup over 1-2% of total steps (e.g., 2,000 of 200,000) lets the moment estimates stabilize before the peak LR (1e-4 to 3e-4) is reached, and it also protects freshly initialized output layers from huge early gradients. Continued pre-training from a converged checkpoint needs a much shorter warmup (hundreds of steps) because the loss landscape is already benign. If training diverges in the first 1% of steps, lengthen warmup before touching the peak LR.

**Q: How does multi-token prediction (MTP) change training, and why did DeepSeek-V3 adopt it?**
**Short:** MTP adds auxiliary heads predicting several future tokens as a weighted extra loss; DeepSeek-V3's sequential version reports an 85-90% draft-acceptance rate and 1.8x inference throughput.

MTP adds auxiliary prediction modules for future positions, trained with a weighted extra loss on top of the standard next-token loss. Two designs exist and are often conflated: Gloeckle et al. (Meta, 2024) use N independent output heads (N=4) predicting +1..+N in parallel from one shared trunk, and report no training-time overhead with a memory-efficient sequential per-head backward, up to 3× self-speculative decoding speedup, and +12% HumanEval / +17% MBPP at 13B. DeepSeek-V3 instead predicts sequentially, keeping the full causal chain at each depth, with depth D=1 and lambda 0.3 falling to 0.1 late in training; it reports an 85-90% acceptance rate on the second token and 1.8× TPS. The planning signal — to predict token +2 the model must implicitly commit to +1 — improves the trunk's representations. DeepSeek-V3's MTP was one of several compounding efficiency choices (with FP8 training and MoE) behind its reported $5.576M official training cost.

**Q: Why must fill-in-the-middle (FIM) be trained during pre-training rather than bolted on later?**
**Short:** FIM transforms roughly half of training examples at near-zero cost to left-to-right perplexity, but adding it only after pre-training measurably underperforms baking it in from token zero.

FIM rearranges training examples into (prefix, suffix, middle) or (suffix, prefix, middle) order with sentinel tokens, teaching the model to condition on both sides of a gap — the core capability behind IDE cursor-position completion. Applying FIM transforms to ~50% of training examples costs essentially nothing in left-to-right perplexity (the "FIM-for-free" result), but adding FIM only in a short post-training phase leaves a measurable gap — the code-model case study in §14 measured 18% lower FIM pass@1 versus training it from the start. The sentinel format at inference must exactly match training (PSM vs SPM ordering matters). If a code model will ever serve infill requests, bake FIM into pre-training from token zero.

**Q: What is MFU and what values should you expect at scale?**
**Short:** Model FLOPs Utilization is useful model FLOPs divided by theoretical peak GPU FLOPs over the same time; well-tuned large-scale runs typically achieve 40-55% MFU.

Model FLOPs Utilization is the ratio of useful model FLOPs (≈ 6 × params × tokens for a dense transformer) to the theoretical peak FLOPs of the GPUs over the same wall-clock time. Well-tuned large-scale runs achieve 40-55% MFU (the case studies in §14 assume 45-50%); the gap versus 100% goes to communication (all-reduces, pipeline bubbles), data loading, kernel inefficiency, and recomputation from gradient checkpointing. MFU is the honest metric for training-stack quality because it cannot be gamed the way raw tokens/sec can. Compute expected wall-clock as 6·N·D / (peak_FLOPs × MFU) before committing to a budget, and treat sustained MFU regressions as an infrastructure bug to be diagnosed, not noise.

**Q: Why is long-context capability trained as a separate late stage rather than throughout pre-training?**
**Short:** Because attention cost is quadratic in sequence length, models train at short context for most tokens and add long-context capability in a brief late extension-and-annealing stage instead.

A: Because attention cost is quadratic in sequence length, so paying 128K-context prices across the whole corpus would multiply the training bill for capability that only the final window needs. The standard recipe is three stages: an initial run at 4K-8K that consumes roughly 95% of the tokens and does all the knowledge learning, a context-extension stage, and a short annealing stage. Llama 3 405B spent about 800B of its 15.6T tokens extending 8K to 128K in six increments, advancing to the next increment only when short-context evaluations had fully recovered and needle-in-a-haystack was solved perfectly at the current length — extension that costs short-context quality is treated as a failed stage, not an acceptable tradeoff. The final 40M tokens are the annealing stage: learning rate decayed linearly to zero at 128K context, the mix re-weighted to upsample the highest-quality sources, and the shipped weights taken as a Polyak average of checkpoints across the stage. Meta measured that annealing alone moved Llama 3 8B by +24.0% on GSM8k and +6.4% on MATH validation while doing essentially nothing for the 405B, so budget the stage generously for small models and treat it as a rounding error for frontier ones.

**Q: Are emergent abilities real, and how should that change how you plan a pre-training run?**
**Short:** Emergent capability jumps are partly an artifact of discontinuous exact-match metrics, so plan by instrumenting continuous metrics rather than sizing a model off a published parameter threshold.

A: Treat emergence as a real planning constraint but not as a magic threshold, because the sharp jumps are partly an artifact of how the metric is scored. Wei et al. (2022) catalogued tasks where performance sits at chance and then rises steeply, with thresholds clustering in the 10B-100B parameter range, and reported that chain-of-thought prompting only helps at roughly the 100B scale. Schaeffer et al. (2023, "Are Emergent Abilities a Mirage?") showed that discontinuous metrics — exact-match on a multi-step answer, where every intermediate step must be right — manufacture the cliff, and that swapping to a continuous metric such as token-level edit distance or per-step accuracy often turns the same data into a smooth curve. The practical consequences are concrete: instrument your evaluation suite with continuous metrics so you can see progress before the pass/fail metric moves, do not size a model from a published per-capability threshold because those thresholds are task- and metric-dependent, and never conclude from a flat exact-match line at an early checkpoint that a capability will never appear.

---

## 13. Best Practices

1. **Deduplicate aggressively** — both exact duplicates (substring match) and near-duplicates (MinHash). Repeated data hurts generalization.
2. **Use high-quality data for the final 10% of training** — the "annealing" phase used by Llama 3 and others: the last few billion tokens, drawn from the highest-quality sources, disproportionately shape the model's final behaviour. (Do not cite LIMA here — that result is about 1,000 curated SFT examples, not pre-training data.)
3. **Checkpoint frequently** — every 30-60 minutes at scale; rolling restarts after hardware failures are inevitable.
4. **Monitor per-domain losses** — track validation loss separately on code, math, web text to detect if any domain is being under/over-fit.
5. **Run eval benchmarks every N billion tokens** — validate that capabilities emerge and don't regress as training progresses.
6. **Plan for multi-epoch carefully** — repeating data more than twice at scale hurts; plan data volume upfront.

---


## 14. Case Study

**Scenario:** A biotech company continues pre-training Mistral-7B-v0.3 (existing open-source model) on 500B domain-specific tokens: scientific literature (PubMed, biorXiv), patent filings, clinical trial data, and internal lab reports. Goal: improve domain perplexity from 24.3 (baseline Mistral-7B on biomedical text) to < 16.0, maintain general language benchmark scores within 5% of baseline, training cost < $90,000.

**Architecture:**

```
  Mistral-7B-v0.3 (Starting Checkpoint)
  Params: 7.25B, Vocab: 32,768, Context: 32k
  (v0.2 removed sliding-window attention; v0.3 config has sliding_window: null)
         |
         v Data Preparation
  ┌─────────────────────────────────────────────────────────────┐
  │  500B Token Dataset Composition:                            │
  │  PubMed abstracts (2000-2024): 180B tokens                  │
  │  Full-text open-access papers (PMC): 120B tokens            │
  │  US/EU patent biotech filings: 80B tokens                   │
  │  Clinical trial data (ClinicalTrials.gov): 40B tokens       │
  │  BioRxiv preprints (2013-2024): 40B tokens                  │
  │  Internal lab reports (anonymized): 15B tokens              │
  │  General text (5% to prevent catastrophic forgetting):      │
  │    C4/RedPajama mixture: 25B tokens                         │
  │                                                             │
  │  Data Quality Pipeline:                                     │
  │  1. Dedup: MinHash LSH, threshold=0.80 → removed 12%        │
  │  2. Quality: perplexity filter (base LLM < 150 ppl) → -8%   │
  │  3. PII: regex + NER → remove patient names, study IDs      │
  │  4. License check: CC BY, CC0, public domain only           │
  └────────────────────────────────┬────────────────────────────┘
                                   │
                                   v Training
  ┌─────────────────────────────────────────────────────────────┐
  │  Infrastructure: 32 × A100 80GB (4 nodes × 8 GPUs)          │
  │  Framework: Megatron-LM + DeepSpeed ZeRO Stage 2            │
  │  Parallelism: DP=32 (7B fits on 1 GPU in BF16)              │
  │  Batch: 2M tokens (smaller than base pre-training ~4M)      │
  │    rationale: domain data is less noisy → smaller batch ok  │
  │  LR: 1e-5 (30× lower than base pre-training's 3e-4)         │
  │    rationale: continued pre-training — don't overshoot      │
  │  LR schedule: cosine decay, 2000-step warmup                │
  │  Epochs: ~1.0 (500B tokens / 500B dataset = 1 pass)         │
  │  Gradient clipping: 1.0                                     │
  │  Mixed precision: BF16 weights, FP32 optimizer states       │
  └────────────────────────────────┬────────────────────────────┘
                                   │
  ┌────────────────────────────────▼────────────────────────────┐
  │  Training Timeline                                          │
  │  32 × A100 at ~50% MFU: 32 × 312 × 0.5 = 4992 TFLOPS        │
  │  7B model: 42B FLOPs/token (6N training, not 2N forward)    │
  │  Tokens/sec: 4992e12 / 42e9 = 118,857 tok/s                 │
  │  500B / 118,857 = 4,206,731 sec = 48.7 days                 │
  │  With 85% availability: 57.3 days                           │
  │  Cost: 32 GPUs × $2/hr × 24h × 57.3 days = $88,000          │
  └─────────────────────────────────────────────────────────────┘
```

**Key implementation — 3 Python code blocks:**

Block 1 — Continued pre-training configuration and learning rate setting:

```python
from __future__ import annotations
import math
from dataclasses import dataclass
from pathlib import Path
import json


@dataclass
class ContinuedPretrainingConfig:
    """
    Configuration for continued pre-training (domain adaptation).
    Key differences from full pre-training:
    - Much lower LR (1e-5 vs 3e-4): avoid catastrophic forgetting
    - Shorter warmup (2000 vs 10000 steps): already converged base
    - Include 5% general text: preserve general capabilities
    - 1 epoch (not multiple): domain data is limited, overfitting risk
    """

    # Model
    model_path: str = "mistralai/Mistral-7B-v0.3"
    output_dir: str = "./mistral-7b-biomedical-v1"

    # Data
    domain_data_tokens: int = 475_000_000_000      # 475B domain tokens
    general_data_tokens: int = 25_000_000_000       # 25B general tokens (5%)
    total_tokens: int = 500_000_000_000

    # Training hyperparameters
    learning_rate: float = 1e-5           # 30× lower than base pre-training
    warmup_steps: int = 2_000
    lr_schedule: str = "cosine"
    min_lr_ratio: float = 0.1            # decay to 10% of peak LR
    gradient_clip: float = 1.0
    weight_decay: float = 0.1
    beta1: float = 0.9
    beta2: float = 0.95

    # Batch
    global_batch_tokens: int = 2_000_000  # 2M tokens per step (vs 4M for base)
    micro_batch_sequences: int = 2         # sequences per GPU step
    sequence_length: int = 4096
    num_gpus: int = 32

    @property
    def total_steps(self) -> int:
        return self.total_tokens // self.global_batch_tokens

    @property
    def grad_accum_steps(self) -> int:
        tokens_per_gpu_step = self.micro_batch_sequences * self.sequence_length
        return self.global_batch_tokens // (self.num_gpus * tokens_per_gpu_step)

    def lr_at_step(self, step: int) -> float:
        """Cosine LR schedule with warmup."""
        if step < self.warmup_steps:
            return self.learning_rate * step / self.warmup_steps
        progress = (step - self.warmup_steps) / max(
            self.total_steps - self.warmup_steps, 1
        )
        cosine = 0.5 * (1 + math.cos(math.pi * progress))
        min_lr = self.learning_rate * self.min_lr_ratio
        return min_lr + cosine * (self.learning_rate - min_lr)

    def to_deepspeed_config(self) -> dict:
        # DeepSpeed asserts train_batch_size == micro_batch_per_gpu * grad_accum * world_size.
        # Deriving it from global_batch_tokens // sequence_length (488) instead of from the
        # realized product (2 * 7 * 32 = 448) trips that assertion at engine init — the
        # integer floor in grad_accum_steps is exactly the 8% the two numbers disagree by.
        return {
            "train_batch_size": (
                self.micro_batch_sequences * self.grad_accum_steps * self.num_gpus
            ),
            "train_micro_batch_size_per_gpu": self.micro_batch_sequences,
            "gradient_accumulation_steps": self.grad_accum_steps,
            "gradient_clipping": self.gradient_clip,
            "bf16": {"enabled": True},
            "zero_optimization": {
                "stage": 2,
                "overlap_comm": True,
                "reduce_scatter": True,
                "allgather_bucket_size": 2e8,
                "reduce_bucket_size": 2e8,
            },
            "optimizer": {
                "type": "AdamW",
                "params": {
                    "lr": self.learning_rate,
                    "betas": [self.beta1, self.beta2],
                    "weight_decay": self.weight_decay,
                },
            },
        }


def validate_config(config: ContinuedPretrainingConfig) -> list[str]:
    """Check for common continued pre-training configuration mistakes."""
    warnings = []
    if config.learning_rate > 5e-5:
        warnings.append(
            f"LR {config.learning_rate:.0e} may be too high for continued pre-training; "
            f"recommend 1e-5 to 5e-5 to prevent catastrophic forgetting."
        )
    if config.general_data_tokens / config.total_tokens < 0.03:
        warnings.append(
            "General text fraction < 3% — high risk of catastrophic forgetting on MMLU/reasoning tasks."
        )
    if config.total_steps < 10_000:
        warnings.append(
            f"Only {config.total_steps} steps — may not be enough for meaningful domain adaptation."
        )
    return warnings
```

Block 2 — Data quality filtering pipeline (production concern):

```python
from __future__ import annotations
import hashlib
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Generator


@dataclass
class DocumentFilter:
    """
    Multi-stage quality filter for biomedical pre-training data.
    Removes: near-duplicates, boilerplate, PII, low-quality text.
    """

    # MinHash parameters
    num_perm: int = 128
    dedup_threshold: float = 0.80

    # Quality thresholds
    min_length_chars: int = 200
    max_perplexity: float = 150.0      # above = noisy/garbage text

    # PII patterns
    _pii_patterns: list[re.Pattern] = None

    def __post_init__(self) -> None:
        self._pii_patterns = [
            re.compile(r'\b\d{3}-\d{2}-\d{4}\b'),         # SSN
            re.compile(r'\bNCT\d{8}\b'),                    # ClinicalTrials ID (keep structure, remove value)
            re.compile(r'\b[A-Z][a-z]+ [A-Z][a-z]+, M\.?D\.?\b'),  # Doctor names
            re.compile(r'\b(?:patient|subject|participant) #\s*\d+\b', re.IGNORECASE),
        ]

    def filter_document(self, text: str, source: str) -> tuple[bool, str]:
        """
        Returns (keep, reason_if_rejected).
        Reasons: "too_short", "pii_detected", "boilerplate", "low_quality"
        """
        if len(text) < self.min_length_chars:
            return False, "too_short"

        # PII check
        for pattern in self._pii_patterns:
            if pattern.search(text):
                return False, "pii_detected"

        # Boilerplate detection
        boilerplate_signals = [
            "this article is protected by copyright",
            "all rights reserved",
            "unauthorized reproduction prohibited",
            "click here to download",
            "subscribe to access",
        ]
        text_lower = text.lower()
        if sum(1 for sig in boilerplate_signals if sig in text_lower) >= 2:
            return False, "boilerplate"

        # Scientific quality signal: must have domain vocabulary
        scientific_terms = ["patients", "study", "results", "conclusion", "methods",
                             "hypothesis", "treatment", "clinical", "analysis", "data"]
        term_density = sum(1 for t in scientific_terms if t in text_lower) / max(len(text.split()), 1)
        if term_density < 0.001 and source not in ("patents", "internal"):
            return False, "low_domain_density"

        return True, ""

    def anonymize_pii(self, text: str) -> str:
        """Replace PII patterns with generic placeholders."""
        for pattern in self._pii_patterns:
            text = pattern.sub("[REDACTED]", text)
        # Also redact email addresses
        text = re.sub(r'\b[\w.-]+@[\w.-]+\.\w{2,4}\b', "[EMAIL]", text)
        return text


def estimate_data_mix(
    domain_tokens: int,
    general_tokens: int,
) -> dict[str, float]:
    """Compute and validate the training data mixture."""
    total = domain_tokens + general_tokens
    domain_fraction = domain_tokens / total
    general_fraction = general_tokens / total
    return {
        "domain_fraction": domain_fraction,
        "general_fraction": general_fraction,
        "recommendation": (
            "OK" if 0.85 <= domain_fraction <= 0.97
            else "WARN: general fraction outside 3-15% range for domain adaptation"
        ),
    }
```

Block 3 — BROKEN -> FIX: catastrophic forgetting and LR too high:

```python
from __future__ import annotations
import torch


# BROKEN: Use full pre-training LR (3e-4) for continued pre-training.
# At 3e-4, model updates are large enough to overwrite base pre-training knowledge.
# After 50B tokens: MMLU drops from 62.3% to 41.2% (catastrophic forgetting).
# BioASQ (biomedical QA) improves to 68.4% — but general capability is destroyed.
def broken_lr_for_continued_pretraining() -> float:
    return 3e-4   # same as base pre-training — too high


# FIX: Use 10-30× lower LR for continued pre-training.
# 1e-5 provides meaningful domain learning (BioASQ +18.2 pp) while limiting
# general capability loss (MMLU -1.7 pp — within the 5% acceptable threshold).
def fixed_lr_continued() -> float:
    return 1e-5   # 30× lower than base pre-training


# BROKEN: Train on 100% domain-specific data.
# After 200B tokens of pure biomedical text, the model's distribution
# shifts entirely to biomedical — general text generation capability collapses.
# "The capital of France is..." → model generates medical jargon instead.
def broken_pure_domain_data(domain_dataset: list, n_tokens: int) -> list:
    return domain_dataset[:n_tokens]   # no general text


# FIX: Mix 5% general text to act as forgetting prevention.
# This is the "replay buffer" approach from continual learning:
# by periodically seeing general text, the model retains those representations.
# 5% general text: MMLU loss reduced from -21.1 pp (62.3 -> 41.2) to -1.7 pp.
def fixed_mixed_data(domain_dataset: list, general_dataset: list) -> list:
    # Interleave: 95% domain, 5% general (every 20th sample is general)
    mixed = []
    gen_idx = 0
    for i, doc in enumerate(domain_dataset):
        mixed.append(doc)
        if i % 20 == 19:   # every 20th sample
            if gen_idx < len(general_dataset):
                mixed.append(general_dataset[gen_idx])
                gen_idx += 1
    return mixed


# BROKEN: Start continued pre-training from a generic optimizer state.
# Reinitializing Adam optimizer states (m1, m2 moment buffers) from scratch
# causes the LR warmup to take much longer than necessary — model is already at
# a good loss landscape region, but optimizer has no momentum information.
# Result: first 5,000 steps are wasteful re-convergence.
def broken_reset_optimizer(model: torch.nn.Module, lr: float) -> torch.optim.Optimizer:
    return torch.optim.AdamW(model.parameters(), lr=lr)  # fresh optimizer


# FIX: Initialize optimizer from checkpoint (if fine-tuning the same model family).
# If optimizer states are available from the base model checkpoint, load them.
# If not (cross-model family): at least use appropriate LR from step 0 —
# since model weights are pre-converged, warmup can be shorter (1000 steps vs 10000).
def fixed_load_optimizer_checkpoint(
    checkpoint_path: str,
    model: torch.nn.Module,
    lr: float,
) -> torch.optim.Optimizer:
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr)
    try:
        checkpoint = torch.load(checkpoint_path, map_location="cpu")
        if "optimizer_state_dict" in checkpoint:
            optimizer.load_state_dict(checkpoint["optimizer_state_dict"])
            # Override LR to new value (checkpoint may have different LR)
            for group in optimizer.param_groups:
                group["lr"] = lr
            print("Optimizer states loaded from checkpoint — faster convergence")
        else:
            print("No optimizer state in checkpoint — starting fresh (use short warmup)")
    except FileNotFoundError:
        print("Checkpoint not found — starting fresh optimizer")
    return optimizer
```

**Pitfall 1 — Data leakage from internal lab reports:**

```python
# BROKEN: Include internal lab reports without PII/IP review.
# Lab reports contain: patient identifiers in clinical studies, proprietary compound names,
# unpublished experimental results, employee names.
# Pre-trained model can regurgitate proprietary information verbatim.
# IP/legal risk: unpublished compound IC50 values extracted by competitors.

# FIX: Mandatory review pipeline before including internal documents.
# 1. Legal review: confirm data can be used for model training.
# 2. PII scrubbing: patient IDs, names, dates → [REDACTED].
# 3. IP classification: redact proprietary compound identifiers, replace with generic names.
# 4. Differential privacy: add noise to numerical values (IC50, yield%) to prevent verbatim memorization.
```

**Pitfall 2 — Unbalanced data mix causing token repetition:**

```python
# BROKEN: Simple concatenation of 5 data sources without mixing strategy.
# Source sizes vary: PubMed (180B) >> Internal reports (15B).
# With sequential concatenation, model sees PubMed first for 18 epochs equivalent
# (if batch draws from beginning), then internal reports — heavily overrepresents PubMed style.

# FIX: Proportional sampling from each source throughout training.
# WeightedRandomSampler needs ONE weight PER DATASET SAMPLE — it indexes into the
# weights list, so a list of per-source weights (or that list repeated) samples
# meaningless indices. Divide the target share by the source's sample count so each
# source is drawn at its target rate regardless of its raw size.
from collections import Counter
from torch.utils.data import WeightedRandomSampler

def build_weighted_sampler(
    sample_sources: list[str],          # source name for each sample, in dataset order
    target_mix: dict[str, float],       # desired sampling fraction per source, sums to 1
) -> WeightedRandomSampler:
    counts = Counter(sample_sources)
    weights = [target_mix[s] / counts[s] for s in sample_sources]
    # replacement=True: a large source is not exhausted before a small one is reached
    return WeightedRandomSampler(
        weights=weights, num_samples=len(weights), replacement=True
    )
```

**Metrics:**

| Metric | Baseline Mistral-7B | After Continued Pre-training |
|--------|--------------------|-----------------------------|
| BioASQ accuracy | 48.2% | 66.4% (+18.2 pp) |
| PubMedQA accuracy | 61.3% | 74.8% (+13.5 pp) |
| MedMCQA accuracy | 54.7% | 68.1% (+13.4 pp) |
| Domain perplexity (biomedical) | 24.3 | 15.7 (-35%) |
| MMLU (general) | 62.3% | 60.6% (-1.7 pp) |
| HellaSwag | 81.2% | 79.8% (-1.4 pp) |
| GSM8K (math) | 46.8% | 45.2% (-1.6 pp) |
| Training cost | — | $88,000 (44,000 GPU-hours at $2/GPU-hour) |
| Tokens trained | — | 500B |
| Training time | — | 57 days (32×A100; 48.7 days of compute at 85% availability) |

**Interview Q&As:**

**Q: What is catastrophic forgetting in continued pre-training and how do you prevent it?**
Catastrophic forgetting (McCloskey & Cohen, 1989) occurs when a neural network trained on a new data distribution loses performance on the original distribution. In continued pre-training, training exclusively on biomedical text causes the model to shift its weight distribution toward biomedical patterns, overwriting general language representations. Prevention: (1) Lower learning rate (1e-5 vs 3e-4) — smaller updates limit deviation from the base; (2) Replay buffer — mix 5% general text throughout training; (3) Early stopping — monitor general benchmarks during training, stop if degradation exceeds 5%; (4) EWC (Elastic Weight Consolidation) — penalize updates to weights important for general tasks (computationally expensive, rarely used in practice).

**Q: Why is the learning rate so much lower for continued pre-training than for full pre-training?**
Full pre-training starts from random initialization — the model needs large gradient steps to move from a random loss landscape to a meaningful one. Learning rates of 1e-4 to 3e-4 are appropriate. Continued pre-training starts from a model already at a local minimum of the general pre-training loss. Large learning rates would: (1) push the model away from this well-optimized point, (2) cause large weight updates that overwrite general capabilities, (3) trigger instability in already-converged layers. Learning rates 10-30× lower (1e-5 to 5e-5) provide meaningful domain learning while limiting deviation from the base checkpoint.

**Q: How do you choose the data mixture ratio between domain-specific and general text?**
The ratio is an empirical trade-off calibrated on validation benchmarks: (1) Train several small-scale runs (5B tokens each) with different ratios (100/0, 95/5, 90/10, 80/20) and measure both domain performance (BioASQ) and general performance (MMLU). (2) Find the ratio where domain gain is maximized while general loss stays within tolerance (typically 5%). For most domain adaptation tasks, 90-97% domain / 3-10% general achieves the best trade-off. Pure domain data (100%) consistently causes >10% general capability loss. Very high general fractions (>25%) dilute domain signal and reduce domain gains by 30-50%.

**Q: What data quality filters are most important for scientific/biomedical pre-training data?**
In priority order: (1) Near-deduplication — biomedical literature has massive duplication (same abstract appears in PubMed, PMC, institutional repositories, biorXiv); MinHash deduplication at threshold 0.8 removes 10-15% of tokens. (2) PII removal — patient identifiers, clinical study IDs, and personal health information must be removed or anonymized for HIPAA compliance and to prevent memorization. (3) License filtering — only CC BY, CC0, and public domain content can be included; non-commercial licenses are insufficient for commercial model training. (4) Quality filtering — perplexity-based filtering (using a base LLM) removes retracted papers, boilerplate, and garbled text.

**Q: How do you estimate whether 500B tokens is sufficient for meaningful domain adaptation?**
The Chinchilla scaling law (Hoffmann et al. 2022) was derived for full pre-training but provides a rough guideline: a 7B model is compute-optimal at ~140B training tokens. For domain adaptation, you are supplementing not replacing — the key question is domain perplexity convergence. Track domain-specific validation perplexity (on held-out biomedical text) during training; convergence (perplexity stops decreasing) indicates sufficient exposure. For most 7B models, 50-200B domain tokens achieves meaningful adaptation; beyond 500B yields diminishing returns unless domain data is highly diverse. If domain data is repetitive, training beyond 1 epoch risks memorization rather than generalization.

**Q: Why should you evaluate catastrophic forgetting on multiple general benchmarks rather than just one?**
Different benchmarks probe different capabilities: MMLU tests knowledge recall, HellaSwag tests commonsense reasoning, GSM8K tests mathematical reasoning, HumanEval tests code generation. A model can retain MMLU performance (knowledge is domain-independent) while losing 15% on GSM8K (mathematical reasoning is in the tail of the biomedical data distribution). Single-benchmark evaluation gives a false sense of security. Track at minimum: a knowledge benchmark (MMLU), a reasoning benchmark (GSM8K or HellaSwag), and a language benchmark (writing quality via LLM judge). Alert if any drops > 5%.

### Case Study 2: Pre-Training a 7B Parameter Code-Specialized LLM

**Problem Statement and Scale**

A software tooling company wants to pre-train a 7B parameter code-specialized LLM to power an internal Copilot for 4,000 engineers writing Python, Java, Go, and SQL. The model must:
- Outperform GPT-3.5-turbo on HumanEval (baseline: 48.1% pass@1)
- Pre-training budget: $120,000 (512 A100 80GB GPUs × $2.50/GPU-hour — roughly 3.5 days of wall clock, since `6ND` at 45% MFU needs only 33,000 GPU-hours)
- Training tokens: 400B tokens from curated code corpus
- Context length: 8,192 tokens (supports full file context)
- Inference target: < 40 ms p99 per output token on a single A10G (7B in BF16 = 14 GB of weights re-read per decoded token against the A10G's 600 GB/s, a hard floor near 23 ms/token — a 64-token completion therefore lands around 2 s, not 150 ms)

**Data Curation Pipeline**

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    GH(["GitHub\n(500B tokens)"]) --> LIC["License Filter\nRemove GPL/AGPL\nKeep MIT/Apache/BSD"]
    SE(["Stack Exchange\n(40B)"]) --> SCORE["Score Filter\nKeep score >= 3\nRemove non-code Q&A"]
    IC(["Internal Codebase\n(8B)"]) --> DEDUP["Deduplication\nMinHash LSH\nJaccard >= 0.85 → drop"]
    LIC --> LANG
    SCORE --> LANG
    DEDUP --> LANG["Language Detection\nlangdetect, pycld3\nKeep: Python, Java, Go, SQL, Bash, JS, TS"]
    LANG --> QUAL["Quality Scoring\nline length (under 1000) · comment ratio (0.05–0.5)\nfunction density · perplexity filter · AST parse success"]
    QUAL --> DEDUP2["Near-Dedup Pass 2\nBM25 + SimHash\nfile-level + chunk-level, removes copy-paste code"]
    DEDUP2 --> CORPUS(["Final Corpus: 400B tokens\nPython 38%, Java 22%,\nGo 14%, SQL 9%, other 17%"])

    class GH,SE,IC,CORPUS io
    class LIC,SCORE,DEDUP,LANG,QUAL,DEDUP2 mathOp
```

Each raw source passes its own source-specific filter (license, score, or dedup) before the merged stream goes through language detection, quality scoring, and a second near-dedup pass — reducing ~548B raw tokens to the final 400B-token corpus.

**Architecture Overview**

```
Pre-Training Cluster
  512 × A100 80GB (64 nodes × 8 GPUs)
         |
         v
  [Data Loader]          ─── WebDataset, 256 shards, async prefetch
  Context packing         ─── pack multiple docs into 8192-token windows
  FIM augmentation        ─── 50% fill-in-the-middle transforms
         |
         v
  [Llama-2 Architecture, 7B]
  - 32 transformer layers
  - 32 attention heads, 8 KV heads (GQA)
  - Hidden dim 4096, FFN dim 11008
  - RoPE positional encoding, θ=10000
  - SwiGLU activation (no bias in FFN)
  - RMSNorm (no LayerNorm)
  - Vocab: 32,000 (BPE, code-optimized)
         |
         v
  [Distributed Training]
  - DDP across 8 GPUs per node (ZeRO Stage 1)
  - Tensor Parallelism: tp=2 within node
  - Pipeline Parallelism: pp=2 across nodes
  - Gradient accumulation: 4 steps
  - Effective batch: 512 × 8192 × 4 / (tp×pp) = 4M tokens/step
         |
         v
  [Checkpoint & Eval]    ─── every 5B tokens
  HumanEval pass@1       ─── temperature=0.2, n=20 samples
  MBPP pass@1            ─── same sampling
```

**Key Design Decisions**

1. **Fill-in-the-Middle (FIM) at 50% of training steps**: Transforms `[prefix][suffix]` samples into `<PRE>prefix<SUF>suffix<MID>middle` format. This teaches the model to complete code in the middle of a file — critical for IDE Copilot use cases where the cursor is rarely at the end. Rejected alternative: post-training FIM fine-tuning only — FIM capability degrades 18% in pass@1 when not trained from scratch.

2. **Grouped Query Attention (GQA, 8 KV heads)**: 32 attention heads share 8 KV head groups. Reduces KV cache memory by 4× during inference. Do the arithmetic: with MHA, K+V per token per layer = 2 × 4,096 dims × 2 bytes (BF16) = 16 KiB, × 32 layers = 512 KiB/token, so a full 8,192-token context costs 4 GiB per request — 400 GiB for 100 concurrent requests. GQA with 8 KV heads cuts the KV width 4× to 128 KiB/token, i.e. ~100 GiB for the same 100 requests. Quality: < 0.3% perplexity regression vs full MHA.

3. **ZeRO Stage 1 + TP=2 over ZeRO Stage 3**: ZeRO Stage 3 (full parameter sharding) gives better memory efficiency but incurs a 50% increase in communication volume over plain data parallelism (ZeRO paper, Rajbhandari et al. 2020) — less justified at 7B than at 70B. Mixed-precision Adam costs 16 bytes/param (2 BF16 weights + 2 BF16 grads + 12 bytes of FP32 master/momentum/variance), so an unsharded 7B replica needs ~112 GB — over the A100's 80 GB. ZeRO Stage 1 shards only the 12-byte optimizer state across data-parallel ranks, leaving ~4 bytes/param resident: ~28 GB per GPU, comfortably inside 80 GB, and stages 1 and 2 carry the same communication volume as plain DP.

4. **Context packing to 8,192 tokens**: Multiple code files packed into one context window with `<|file_sep|>` delimiter tokens. Without packing, most training steps are dominated by files far shorter than the window — a 512-token file leaves 94% of an 8,192-token slot as padding, and real tokens per step fall to roughly 1.8M of the 4.2M-token batch. Packing recovers essentially the full 4.2M.

5. **BPE vocabulary with code-specific tokens**: Standard LLaMA vocabulary treats Python indentation as individual space tokens (4-space indent = 4 tokens). Training the 32,000-entry BPE vocabulary on the code corpus itself — with roughly 2,048 entries spent on code-specific tokens (common identifiers, operators, indentation runs) — reduces tokenized length for code by 23%, which directly translates to 23% more effective code content per training context.

6. **Warmup 2,000 steps + cosine decay**: Learning rate 3e-4 peak, cosine decay to 3e-5 over 400B tokens. Linear warmup prevents early gradient explosion. Cosine decay is preferable to linear because it maintains higher LR during mid-training (better exploration) and slowly anneals at the end (better convergence).

**Implementation**

```python
from __future__ import annotations

import torch
from torch import nn
from dataclasses import dataclass
from typing import Optional


@dataclass
class ModelConfig:
    vocab_size: int = 32_000
    dim: int = 4096
    n_layers: int = 32
    n_heads: int = 32
    n_kv_heads: int = 8          # GQA: 4 query heads per KV head
    ffn_dim: int = 11_008
    max_seq_len: int = 8_192
    rope_theta: float = 10_000.0


class RMSNorm(nn.Module):
    def __init__(self, dim: int, eps: float = 1e-6) -> None:
        super().__init__()
        self.eps = eps
        self.weight = nn.Parameter(torch.ones(dim))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        norm = x.float().pow(2).mean(-1, keepdim=True).add(self.eps).rsqrt()
        return (x.float() * norm).type_as(x) * self.weight


def build_fim_sample(
    prefix: str,
    middle: str,
    suffix: str,
    mode: str = "PSM",   # Prefix-Suffix-Middle format
) -> str:
    """
    Build a fill-in-the-middle training sample.
    50% of training data is transformed with this function.
    """
    if mode == "PSM":
        return f"<PRE>{prefix}<SUF>{suffix}<MID>{middle}"
    elif mode == "SPM":   # Suffix-Prefix-Middle: improves suffix conditioning
        return f"<SUF>{suffix}<PRE>{prefix}<MID>{middle}"
    else:
        raise ValueError(f"Unknown FIM mode: {mode}")


class ContextPacker:
    """
    Pack multiple code documents into fixed-length context windows.
    Avoids wasting GPU cycles on short files (< 512 tokens).
    """
    def __init__(self, seq_len: int = 8192, sep_token_id: int = 2) -> None:
        self.seq_len = seq_len
        self.sep = sep_token_id
        self._buffer: list[int] = []

    def add(self, token_ids: list[int]) -> list[list[int]]:
        completed: list[list[int]] = []
        self._buffer.extend(token_ids + [self.sep])
        while len(self._buffer) >= self.seq_len:
            completed.append(self._buffer[: self.seq_len])
            self._buffer = self._buffer[self.seq_len :]
        return completed

    def flush(self) -> Optional[list[int]]:
        if self._buffer:
            padded = self._buffer + [0] * (self.seq_len - len(self._buffer))
            self._buffer = []
            return padded
        return None
```

**BROKEN: Training diverges at step 8,000 due to gradient explosion without clipping**

```python
# BROKEN: no gradient clipping, no loss spike detection
optimizer.zero_grad()
loss.backward()
optimizer.step()   # catastrophic divergence if loss spikes — NaN in weights
scheduler.step()
# At step 8,243: loss spikes from 2.1 to 18.7, then NaN
# Recovery: roll back to checkpoint from step 7,000 and restart — 1,243 steps at
# ~3 s/step (95,000 steps over 84 hours) plus restart overhead, ~1.5 hours lost
```

**FIX: Gradient clipping + loss spike detection with auto-rollback**

```python
optimizer.zero_grad()
loss.backward()

# Clip gradients before optimizer step
grad_norm = torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)

# Detect loss spikes before they corrupt weights
if loss.item() > 3 * running_avg_loss:
    logger.warning(f"Loss spike at step {step}: {loss.item():.2f} vs avg {running_avg_loss:.2f}")
    # Skip this batch — do not apply gradients
    optimizer.zero_grad()
    spike_count += 1
    if spike_count > 3:
        logger.error("3 consecutive spikes — rolling back to last checkpoint")
        load_checkpoint(model, optimizer, last_safe_checkpoint_path)
        spike_count = 0
else:
    optimizer.step()
    scheduler.step()
    running_avg_loss = 0.95 * running_avg_loss + 0.05 * loss.item()
```

**BROKEN: Context packing causes cross-document attention contamination**

```python
# BROKEN: documents packed without attention masking between them
# Model attends across file boundaries → learns spurious cross-file patterns
packed_tokens = pack_documents(docs, seq_len=8192)
# All 8192 tokens attend to each other — document A attends to document B
# Result: +2.1 perplexity on single-file eval despite lower training loss
```

**FIX: Document-level attention masking (block-diagonal mask)**

```python
def build_document_mask(doc_lengths: list[int], seq_len: int) -> torch.Tensor:
    """Block-diagonal attention mask — each document attends only to itself."""
    mask = torch.zeros(seq_len, seq_len, dtype=torch.bool)
    offset = 0
    for length in doc_lengths:
        end = min(offset + length, seq_len)
        mask[offset:end, offset:end] = True
        offset = end
        if offset >= seq_len:
            break
    return mask   # True = attend, False = block (additive mask: 0 or -inf)
```

**Training Progression**

| Checkpoint (tokens seen) | HumanEval pass@1 | MBPP pass@1 | Loss |
|---|---|---|---|
| 0 (random init) | 0.0% | 0.0% | 10.4 |
| 10B | 12.3% | 9.8% | 3.1 |
| 50B | 31.7% | 28.4% | 2.4 |
| 100B | 41.2% | 38.6% | 2.2 |
| 200B | 49.8% | 46.1% | 2.1 |
| 400B (final) | 61.4% | 57.9% | 2.0 |
| GPT-3.5 (baseline, Code Llama paper Table 2) | 48.1% | 52.2% | — |

**Metrics and Results**

| Resource | Amount | Notes |
|---|---|---|
| Training duration | 3.5 days (84 h) | 512 A100 80GB; 65 h of it is compute at 45% MFU |
| Total compute | 43,000 GPU-hours | 512 × 84 hours |
| Effective tokens/sec | 1.32M | 400B / 302,400 s — 35% end-to-end MFU |
| Total cost | $107,500 | 43,008 GPU-hours × $2.50, under the $120K budget |
| Final HumanEval pass@1 | 61.4% | +13.3 pp over GPT-3.5 |
| Inference (per output token) | 31 ms p50, 38 ms p99 | A10G, BF16, GQA KV cache (23 ms bandwidth floor) |

**Common Pitfalls**

1. **Not deduplicating training data leads to memorization, not generalization.** If the model sees the same file 100 times, it memorizes verbatim rather than learning patterns. MinHash LSH deduplication is mandatory — remove files with Jaccard similarity > 0.85. Effect: ~548B raw tokens → 400B after the full filter-plus-dedup pipeline; HumanEval +4.1 pp for deduped vs non-deduped at the same token count.

2. **Using left-padding instead of right-padding (or packing) for short sequences.** Left-padding shifts the absolute position of code tokens — RoPE positional encodings become misaligned. Always pack or right-pad. Never left-pad for decoder-only models using RoPE.

3. **Not monitoring GPU memory fragmentation during long training runs.** After 100k steps, PyTorch allocator fragmentation can cause OOM even when theoretical memory usage is within budget. Fix: call `torch.cuda.empty_cache()` after every checkpoint save; use `PYTORCH_CUDA_ALLOC_CONF=max_split_size_mb:512` to reduce fragmentation.

4. **Over-filtering data for "quality" removes domain-specific patterns.** Perplexity-based filtering that removes low-perplexity code (which seems "too easy") also removes boilerplate — which engineers write constantly. Filter for correctness (parses, compiles) not difficulty.

**Interview Discussion Points**

**Q: How do you calculate the compute budget required to pre-train a 7B model on 400B tokens?**
The 6ND compute identity (Kaplan et al. 2020, not Chinchilla) gives C ≈ 6 × N × D, where N = model parameters and D = training tokens. Chinchilla supplies the separate question of how to split a budget between N and D. For N=7B, D=400B: C ≈ 6 × 7×10^9 × 4×10^11 = 1.68×10^22 FLOPs. To convert to GPU-hours: A100 80GB at BF16 peak = 312 TFLOPS = 3.12×10^14 FLOPS/second. GPU-hours needed = 1.68×10^22 / (3.12×10^14 × 3600) ≈ 15,000 GPU-hours, but accounting for ~45% MFU (Model FLOPS Utilization): 15,000 / 0.45 = 33,000 GPU-hours. At 512 GPUs: 33,000 / 512 = 65 hours ≈ 2.7 days wall time. Actual training was 3.5 days (84 hours) — the extra 19 hours is data loading overhead, checkpoint saves, evaluation runs, and hardware failures, i.e. 77% end-to-end availability. Be suspicious of any plan whose wall clock is many times the 6ND estimate: a 14-day run for this budget would imply about 7% MFU, not 45%.

**Q: Why is 400B tokens chosen rather than 1T tokens for a 7B model?**
Chinchilla shows the optimal token count for a 7B model is approximately 140B tokens (20× model parameters). 400B is already ~2.9× Chinchilla-optimal — returns are diminishing. Going to 1T tokens is 2.5× the compute, so about $270K against this run's $107.5K, and would yield a HumanEval improvement of perhaps 3–4 pp — not worth the extra $160K. The exception: if this model will be used as a foundation for many fine-tuning variants, over-training the base gives fine-tuning a better starting point.

**Q: How do you prevent data leakage from the evaluation benchmark (HumanEval) into the training corpus?**
HumanEval problems are on GitHub. Exact deduplication via SHA-256 hash removes exact copies. Near-deduplication via MinHash catches paraphrased versions. Additionally: (1) Download a specific HumanEval commit and date-filter training data to exclude any GitHub repos created or modified after the HumanEval publication date (July 2021); (2) Monitor for suspiciously high pass@1 on very early training checkpoints — if pass@1 > 30% at 10B tokens, contamination is likely; (3) Create a held-out internal benchmark of original problems not on GitHub.

---

## See Also
- [Data Pipelines & Processing (ML)](../../ml/data_pipelines_and_processing/README.md) — PySpark, Great Expectations, schema evolution — the data engineering behind LLM pre-training datasets
- [Training Infrastructure](../training_infrastructure/README.md) — ZeRO/FSDP, tensor and pipeline parallelism, checkpointing at cluster scale
- [Fine-Tuning](../fine_tuning/README.md) — the 100-1000× cheaper alternative when you do not need a new base model
- [Tokenization & Embeddings](../tokenization_and_embeddings/README.md) — BPE vocabulary design decisions that precede any pre-training run
