# PEFT Methods

## 1. Concept Overview

Parameter-Efficient Fine-Tuning (PEFT) is a family of techniques that fine-tune large pre-trained models by training only a small fraction of parameters (0.001-1%) while keeping most of the model frozen. This contrasts with full fine-tuning, which updates all parameters. PEFT methods enable adapting large models on limited hardware while preserving the majority of pre-trained knowledge.

The core problem PEFT solves: a 70B model has 70B × 2 bytes × 3 (weights + gradients + optimizer states) ≈ 420GB of training memory requirements. Full fine-tuning of 70B models requires 6-8× A100 80GB GPUs. PEFT makes this same model trainable on 1-2 GPUs by training only 0.1-1% of parameters.

**What this actually says.** "Training a model costs about three copies of it, not one — so the model you can *load* is far bigger than the model you can *train*."

That factor of 3 is the whole reason fine-tuning is a different hardware problem from inference. Freeze the base and two of the three copies shrink to almost nothing, because gradients and optimizer state are sized by the *trainable* parameter count, not the total one.

| Symbol | What it is |
|--------|------------|
| `70B` | Total parameter count of the base model |
| `× 2 bytes` | BF16/FP16 storage — 2 bytes per parameter |
| `× 3` | Weights + gradients + optimizer state, each roughly one full copy |
| `420GB` | Resulting training-time footprint, before activations are counted |
| `0.1-1%` | Fraction of parameters PEFT actually leaves trainable |

**Walk one example.** Where the 420GB goes, and what freezing the base removes at 0.5% trainable:

```
  trainable params = 0.5% x 70e9 = 350,000,000

                            full fine-tune          PEFT, 4-bit frozen base
  weights      70e9 x 2 B  =  140.0 GB      70e9 x 0.5 B  =   35.0 GB
  gradients    70e9 x 2 B  =  140.0 GB     350e6 x 2 B    =    0.7 GB
  optimizer    70e9 x 2 B  =  140.0 GB     350e6 x 2 B    =    0.7 GB
                              --------                        -------
                              420.0 GB                         36.4 GB

  420.0 / 36.4 = 11.5x smaller  ->  fits one 80GB A100 instead of six
```

The gradient and optimizer rows collapse by 200× because they scale with 350M, not 70B. The weight row only shrinks by quantizing it — which is exactly the division of labour between PEFT and QLoRA: PEFT kills two rows, quantization shrinks the third.

---

## Intuition

> **One-line analogy**: PEFT methods are like editing footnotes instead of rewriting the textbook — you add targeted changes that alter the behavior without touching the core content.

**Mental model**: A pre-trained LLM encodes billions of general representations. Fine-tuning for a specific task doesn't require changing all of these — the task-relevant update is small relative to the full model. PEFT methods exploit this: instead of updating 70B parameters, update 70M (0.1%) that capture the task-relevant change. Different PEFT methods differ in where and how they inject the trainable parameters: LoRA adds low-rank matrices inside weight layers; adapters insert new bottleneck layers; prefix tuning prepends learned vectors to the context; prompt tuning adds learnable input tokens.

**Why it matters**: PEFT democratized LLM adaptation. Before PEFT (pre-2021), fine-tuning GPT-3-scale models required massive compute budgets. After PEFT (and especially LoRA + QLoRA), a single consumer GPU can fine-tune 7B-13B models, and teams without ML infrastructure can produce specialized models.

**Key insight**: Different PEFT methods have different tradeoffs in quality, memory, latency overhead, and mergeability. The "best" PEFT method depends on the specific constraints — LoRA is the dominant default, but adapters, prefix tuning, and prompt tuning each have scenarios where they're preferred.

---

## 2. Core Principles

- **Frozen base weights preserve general capabilities**: All PEFT methods keep the majority of parameters frozen, reducing catastrophic forgetting.
- **Task-relevant updates are low-dimensional**: The empirical finding underlying PEFT — fine-tuning changes are concentrated in a low-dimensional subspace of the full parameter space.
- **Inference overhead varies**: Merged LoRA has zero overhead; adapters always add forward pass cost; prefix tuning adds context window tokens.
- **Each method has a different inductive bias**: LoRA injects changes into weight matrices; adapters inject changes into layer outputs; prefix tuning injects changes into attention keys and values.
- **PEFT methods can be combined**: DoRA (LoRA + magnitude scaling) and LoftQ (LoRA + quantization) combine PEFT ideas for better quality or memory efficiency.

---

## 3. How It Works — Detailed Mechanics

### 3.1 Adapter Layers (Houlsby et al. 2019)

Insert small bottleneck modules after attention and FFN sublayers:

```
Transformer block (original):
  Input → [Self-Attention] → Add+Norm → [FFN] → Add+Norm → Output

Transformer block with adapters (Houlsby):
  Input → [Self-Attention] → Add+Norm → [Adapter] → [FFN] → Add+Norm → [Adapter] → Output

Adapter architecture:
  Input (d-dim) → [Down-project: d → r] → [Activation: ReLU/GELU] → [Up-project: r → d] → Add to input

Where r is the bottleneck dimension (typically r=8 to r=256)

Parameters per adapter:
  Down: d × r
  Up:   r × d
  Total per adapter: 2 × d × r

For 7B model (d=4096), 32 layers, 2 adapters per layer, r=8:
  2 × 4096 × 8 × 32 × 2 = 4,194,304 ≈ 4M trainable parameters (~0.06%)

Inference overhead: ALWAYS present — adapter cannot be merged into base model
  Additional forward pass through down-project and up-project per layer
  Adds ~5-10% inference latency per adapter insertion
```

**Read it like this.** "Squeeze the layer's output down to a tiny width, learn the correction there, then blow it back up — the correction costs two skinny matrices instead of one fat one."

The `2 × d × r` is the entire cost model. Both projections are `d`-by-`r`, so the parameter count is linear in the bottleneck `r` while the layer it corrects is quadratic in `d`. That asymmetry is where all the savings come from.

| Symbol | What it is |
|--------|------------|
| `d` | Model hidden dimension — 4096 for a 7B model |
| `r` | Bottleneck width the adapter squeezes down to, typically 8 to 256 |
| `d × r` | The down-projection matrix, one per adapter |
| `r × d` | The up-projection matrix, one per adapter |
| `2 × d × r` | Both projections together — the per-adapter parameter cost |
| `× 32 × 2` | 32 transformer layers, 2 adapters inserted per layer |

**Walk one example.** Building the 4M figure from the bottom up at `d=4096, r=8`:

```
  down-project   4096 x 8                 =     32,768
  up-project        8 x 4096              =     32,768
                                             ---------
  per adapter    2 x 4096 x 8             =     65,536

  per layer      65,536 x 2 adapters      =    131,072
  whole model   131,072 x 32 layers       =  4,194,304   (64 adapters total)

  as % of base   4,194,304 / 7,000,000,000  =  0.0599%
```

The full-width layer this sits beside is `4096 x 4096 = 16,777,216` parameters — so one adapter is `1/256` the size of the matrix it corrects. Doubling `r` from 8 to 16 doubles the adapter to 8.4M and 0.12%; the cost stays linear no matter how far you push it, which is why bottleneck width is such a safe dial to turn.

**Why the bottleneck exists at all.** Without it — a full `d × d` correction matrix — you would be training 16.8M parameters per adapter and 1.07B across the model, which is 15% of the base and defeats the entire point. The narrow waist is what forces the update to be low-rank, and the low-rank constraint is the assumption every PEFT method rests on.

### 3.2 Prefix Tuning (Li and Liang, 2021)

Learn soft prefix tokens prepended to keys and values in each attention layer:

```
Standard attention:
  Q = X × W_q, K = X × W_k, V = X × W_v
  Attention(Q, K, V) = softmax(QK^T / √d_k) × V

Prefix tuning:
  P_k = learned prefix for keys    (l × d, where l = prefix length)
  P_v = learned prefix for values  (l × d)

  K_prefixed = concat([P_k, X × W_k])  ← prepend prefix to keys
  V_prefixed = concat([P_v, X × W_v])  ← prepend prefix to values

  Attention(Q, K_prefixed, V_prefixed) = softmax(Q × K_prefixed^T / √d_k) × V_prefixed

Each query token now attends to both:
  - Normal document tokens
  - Learned prefix tokens (which carry task-specific context)

Parameters:
  l × d_k per layer (keys) + l × d_v per layer (values) × num_layers
  For l=10, d=4096, 32 layers: 10 × 4096 × 2 × 32 = 2,621,440 ≈ 2.6M params

Inference overhead:
  Prefix tokens occupy context window space: l tokens per layer
  For l=10, 32 layers: 320 "virtual tokens" of KV cache memory
  Doesn't physically lengthen input but increases KV cache size
```

**In plain terms.** "Invent a handful of fake tokens that every real token is allowed to look at, and let gradient descent decide what they should say — separately in every layer."

The `× 2` is keys *and* values; the `× num_layers` is the part people forget. Prefix tuning is not one prefix — it is 32 independent prefixes, one per layer, which is what separates it from prompt tuning and multiplies its parameter count by the model depth.

| Symbol | What it is |
|--------|------------|
| `l` | Prefix length — how many virtual tokens per layer, e.g. 10 |
| `d_k`, `d_v` | Key and value dimensions, both 4096 here |
| `P_k`, `P_v` | The learned prefix matrices, each `l × d`, one pair per layer |
| `× 2` | Keys and values are learned separately |
| `num_layers` | 32 — each layer gets its own independent prefix |
| `l × d × 2 × num_layers` | Total trainable parameters |

**Walk one example.** The 2.6M figure at `l=10, d=4096, 32 layers`:

```
  P_k per layer      10 x 4096            =     40,960
  P_v per layer      10 x 4096            =     40,960
                                              --------
  per layer          10 x 4096 x 2        =     81,920

  whole model     81,920 x 32 layers      =  2,621,440

  as % of base     2,621,440 / 7,000,000,000  =  0.0374%

  KV cache cost   10 virtual tokens x 32 layers  =  320 prefix KV entries
```

Compare the same `l=10` under prompt tuning, which learns one prefix at the input only: `10 x 4096 = 40,960` parameters. Prefix tuning costs 64× more (`2 × 32`) for the identical prefix length — the `× 2 × 32` is the entire difference between the two methods.

**What the depth buys, and what it costs.** Per-layer prefixes let the task conditioning re-assert itself at every depth instead of being diluted as the signal propagates, which is why prefix tuning outperforms prompt tuning on mid-sized models. The bill arrives as KV cache: 320 entries of permanently-resident cache per sequence, present on every request, that no amount of merging can remove.

### 3.3 Prompt Tuning (Lester et al. 2021)

Learn soft prompt tokens prepended to the input embedding layer only (not all layers):

```
Standard input:
  X = [x_1, x_2, ..., x_n] (word token embeddings)

Prompt tuning:
  X_prompted = concat([P, x_1, x_2, ..., x_n])
  P = learned "soft prompt" tokens (k × d, where k = prompt length)
  P is not drawn from the vocabulary; it's a free-floating embedding

  Only input layer is modified; all transformer layers remain identical

Parameters:
  k × d: e.g., 10 × 768 = 7,680 parameters (extremely lightweight)

Key finding: prompt tuning achieves full fine-tuning quality at large model scales
  (≥10B parameters) but degrades at smaller scales (<1B)
  At GPT-3 scale: prompt tuning with k=100 ≈ full fine-tuning quality
  At T5-Small: prompt tuning << fine-tuning quality

Inference overhead:
  k extra tokens in input → k extra tokens of KV cache per layer
  Minimal overhead; often negligible
```

**Put simply.** "Glue a few learned vectors onto the front of the input embeddings and change absolutely nothing else — the transformer never knows they weren't real words."

`k × d` is the smallest parameter budget in all of PEFT, and the reason is that there is no `× num_layers` term. One prefix, at the input, full stop.

| Symbol | What it is |
|--------|------------|
| `k` | Soft prompt length in tokens, e.g. 10 or 100 |
| `d` | Embedding dimension — 768 for T5-base, 4096 for a 7B model |
| `P` | The learned `k × d` embedding block, not tied to any vocabulary entry |
| `k × d` | Total trainable parameters — no per-layer multiplier |

**Walk one example.** The same `k` at two model scales, showing why scale changes the verdict:

```
                    k        d        k x d        as % of that model
  BERT-base        10      768        7,680        0.0070%  of 110M
  7B model         10     4096       40,960        0.00059% of 7B
  7B model        100     4096      409,600        0.00585% of 7B
```

At `k=100` on a 7B model you are steering 7 billion frozen parameters with 409,600 trainable ones — a ratio of about 1 to 17,000. Nothing else in this file comes close.

**Why that ratio is also the failure mode.** The whole method assumes the frozen model already contains the behaviour you want and merely needs to be pointed at it; the soft prompt is a pointer, not new capacity. Large models satisfy that assumption, which is why `k=100` matches full fine-tuning at GPT-3 scale. Small models do not — there is nothing to point at, and 7,680 parameters cannot supply it, which is exactly the T5-Small collapse the section describes.

### 3.4 LoRA (Low-Rank Adaptation)

(See [lora.md](lora.md) for full details; the 4-bit training variant is covered in [qlora.md](qlora.md))

```
Summary:
  W_adapted = W_frozen + B × A × (alpha/r)
  B ∈ ℝ^(d×r), A ∈ ℝ^(r×k), r ≪ min(d, k)

  Key advantage: CAN BE MERGED after training (zero inference overhead)
  Best balance of quality, memory, and mergeability

  Trainable params (7B, r=16, all-attn+FFN): ~0.5%
```

**The idea behind it.** "Leave the original matrix alone and learn a thin correction beside it — then, once training is done, just add the correction in and throw the scaffolding away."

The reason LoRA merges and adapters do not is visible right in the formula: `B × A` has the same shape as `W_frozen`, so the sum is one matrix again. An adapter's down-up pair sits *between* layers with a nonlinearity in the middle, so there is nothing to add it into.

| Symbol | What it is |
|--------|------------|
| `W_frozen` | The original pre-trained weight matrix, `d × k`, never updated |
| `A` | Down-projection `r × k`, initialized random — the "read" side |
| `B` | Up-projection `d × r`, initialized to zeros — the "write" side |
| `r` | Rank of the update; `r ≪ min(d, k)` is the whole trick |
| `alpha` | Scaling numerator, a fixed hyperparameter (commonly `2 × r`) |
| `alpha/r` | Scale factor applied to the update, so changing `r` doesn't rescale it |
| `B × A` | The rank-`r` correction, shaped exactly like `W_frozen` |

**Walk one example.** Trainable count for a 7B model (`d = 4096`, 32 layers), taking every targeted projection as `4096 × 4096`:

```
  per module, rank r      r x (4096 + 4096)  =  r x 8192

  r=16, one module        16 x 8192          =    131,072
  vs the module itself    4096 x 4096        = 16,777,216     ->  128x smaller

  target q,k,v,o only     131,072 x 4 x 32   = 16,777,216     ->  0.240% of 7B
  target attn + FFN (7)   131,072 x 7 x 32   = 29,360,128     ->  0.419% of 7B
```

That bracket, 0.24% to 0.42%, is the `0.2-0.5%` the comparison table quotes — the spread is not rank, it is *which modules you target*.

**Reading the `alpha/r` scale.** Its only job is to decouple two knobs that would otherwise be tangled. `B × A` grows with `r` simply because more rank means more terms in the sum, so without the divisor, raising `r` would silently raise the effective learning rate too. Dividing by `r` cancels that, letting you sweep rank without re-tuning anything else:

```
  billing adapter    r=16, alpha=32   ->  scale = 32/16 = 2.0
  returns adapter    r= 8, alpha=16   ->  scale = 16/ 8 = 2.0

  Same effective update strength at half the rank -- the point of the ratio.
```

Both configurations in the Section 12 case study use `alpha = 2r` for exactly this reason. Set `alpha = r` instead and the scale is `1.0`; the convention `alpha = 2r` is just a mildly aggressive default that survived experimentation.

**Why `B` starts at zero.** At step 0, `B × A = 0`, so `W_adapted = W_frozen` exactly — training begins from the pre-trained model, not from a randomly perturbed one. Initialize both `A` and `B` randomly and the first forward pass injects noise into every targeted layer, which is the same initialization-gap problem LoftQ solves for the quantized case.

### 3.5 BitFit (Selective Parameter Fine-Tuning)

Fine-tune only the bias terms of the model:

```
Standard linear layer: y = W × x + b
  W: frozen (huge, not trained)
  b: trainable (tiny)

For BERT-base (110M params):
  Bias terms: ~0.1M parameters (<0.1%)

Performance:
  BitFit achieves surprisingly good results for many NLP tasks
  Comparable to adapter fine-tuning on GLUE benchmarks
  Fails for tasks requiring significant representational change

Why it works (hypothesis):
  Bias terms act as global "offset" for layer outputs
  Adjusting biases shifts the distribution of features in task-relevant directions
  Equivalent to fine-tuning the "mean" of each layer's output distribution

Use cases:
  Sentiment classification, text classification, NER
  NOT suitable for: generation tasks, domain adaptation, reasoning
  Primarily academic interest; LoRA dominates in production
```

**What it means.** "Every layer already has a knob that shifts its output up or down. Train only those knobs and leave every multiplication in the network untouched."

In `y = Wx + b`, `W` is `d × d` and `b` is `d` — biases are a `1/d` sliver of the layer. That is why BitFit needs no new parameters at all, and also why its ceiling is low: you can translate each layer's output distribution, never rotate or reshape it.

| Symbol | What it is |
|--------|------------|
| `W` | The weight matrix, `d × d` — frozen, and the overwhelming majority of the layer |
| `b` | The bias vector, `d` values — the only thing BitFit trains |
| `y = Wx + b` | A standard linear layer; only the additive term is left unfrozen |
| `d` | Hidden dimension — 768 for BERT-base |

**Walk one example.** Counting every bias in BERT-base (`d=768`, FFN width 3072, 12 layers):

```
  per encoder layer
    q, k, v, o biases        4 x 768        =   3,072
    attention LayerNorm bias     768        =     768
    FFN intermediate bias       3072        =   3,072
    FFN output bias              768        =     768
    output LayerNorm bias        768        =     768
                                               -------
                                                 8,448

  whole model      8,448 x 12 layers        = 101,376

  as % of base   101,376 / 110,000,000      =  0.0922%
```

That is the `~0.1M parameters (<0.1%)` the section quotes, assembled term by term.

**The gotcha nobody sees coming.** BitFit needs biases to exist. LLaMA, Mistral, and most modern decoder-only models ship with `bias=False` on every projection and use RMSNorm, which has a scale but no shift — so a 7B LLaMA has essentially no bias parameters to train and BitFit degenerates into a no-op. This, more than the quality ceiling, is why the section calls it academic: the architectures it was designed for are the ones production stopped using.

### 3.6 DoRA (Weight-Decomposed Low-Rank Adaptation)

Decompose weight matrices into magnitude and direction components, apply LoRA to direction:

```
DoRA decomposition:
  W = m × (V / ||V||_c)

  Where:
    m: magnitude vector (d-dimensional, scalar per output dimension)
    V / ||V||_c: unit direction matrix (normalized column-wise)

  Fine-tuning:
    Direction update: V_adapted = V + B × A  ← standard LoRA
    Magnitude update: m is directly trainable (d parameters, tiny)

  m is trained freely; V's direction is updated via LoRA

Why it's better than LoRA:
  Separate magnitude and direction changes improve parameter efficiency
  Captures more expressive updates per parameter than LoRA alone
  DoRA (r=8) often matches or exceeds LoRA (r=16) quality
  Useful when rank must be minimized (very memory-constrained)

Parameters: slightly more than LoRA (adds d magnitude params per layer)
  Overhead: negligible vs. LoRA
```

**Stated plainly.** "Split each weight column into *how long it is* and *where it points*, then let LoRA handle the pointing while a single scalar per column handles the length."

LoRA has to spend rank on both jobs at once. DoRA hands the length job to a `d`-vector that costs almost nothing, freeing the low-rank budget to do only what low rank is good at.

| Symbol | What it is |
|--------|------------|
| `W = m × (V / ‖V‖_c)` | The decomposition: a magnitude times a unit-length direction |
| `m` | Magnitude vector, one scalar per output column — `d` trainable parameters |
| `V` | The direction matrix, same shape as `W` |
| `‖V‖_c` | Column-wise norm — divide by it and every column has length 1 |
| `V / ‖V‖_c` | Pure direction, magnitude stripped out |
| `V + B × A` | Direction updated by ordinary LoRA |

**Walk one example.** DoRA at `r=8` on `q,k,v,o` of a 7B model, priced against its LoRA core:

```
  LoRA direction update
    per module    8 x (4096 + 4096)        =    65,536
    per layer     65,536 x 4 modules       =   262,144
    whole model  262,144 x 32 layers       = 8,388,608     0.1198% of 7B

  magnitude vector
    per module    4096                     =     4,096
    whole model   4,096 x 4 x 32           =   524,288     0.0075% of 7B
                                             ---------
  DoRA total                               = 8,912,896     0.1273% of 7B

  magnitude cost as a share of LoRA:  524,288 / 8,388,608  =  6.25%
```

Now the payoff. LoRA at `r=16` on the same modules costs `16 x 8192 x 4 x 32 = 16,777,216`, or 0.240%. DoRA `r=8` reaches comparable quality at 8,912,896 — **1.88× fewer trainable parameters**:

```
  LoRA r=16     16,777,216   0.240%
  DoRA r= 8      8,912,896   0.127%     <- matches or beats, at 53% of the params
```

That 6.25% surcharge is what "negligible vs. LoRA" means numerically, and it is bought back many times over by halving the rank.

**Why magnitude wanted its own parameter.** A rank-8 update can only move `W` inside an 8-dimensional subspace, so if the task mostly needs "make this column stronger," LoRA must burn rank approximating a rescaling it could never express cleanly. Giving magnitude a free, unconstrained `d`-vector removes that waste entirely — and since `m` and `V` recombine into a single matrix, DoRA still merges to zero inference overhead exactly like LoRA.

### 3.7 Comparison Table

```
Method           Trainable %  Merge   Inference  Best For
-----------      -----------  ------  ---------  --------
Adapter (r=8)    0.06%        No      +5-10%     NLP classification
Prefix (l=10)    0.03%        No      +ctx window Pre-generation conditioning
Prompt tuning    ~0.001%      No      +ctx window Very large models only (>10B)
LoRA (r=16)      0.2-0.5%     Yes     0% merged   Most tasks; gold standard
QLoRA (r=16)     0.2-0.5%     Yes*    0% merged*  Memory-constrained training
BitFit           <0.1%        Yes     0%          Simple classification
DoRA (r=8)       ~0.12%       Yes     0% merged   LoRA quality at lower rank
(*QLoRA merged requires dequantize first)
```

**What the formula is telling you.** "Every one of these percentages is the same three-part product — how many parameters per site, times how many sites per layer, times 32 layers — and the methods differ only in what counts as a site."

Percentages alone hide the structure. Written as absolute counts against one fixed model, the whole family lines up on a single axis and the reason for each method's position becomes arithmetic rather than folklore.

| Symbol | What it is |
|--------|------------|
| Base model | 7B parameters, `d = 4096`, 32 layers, FFN width 11008 |
| Per-site cost | Parameters one injection point costs — `2dr`, `r(d+k)`, `d`, etc. |
| Sites per layer | How many places the method injects — 1 input prefix, 4 projections, 7 modules |
| Depth multiplier | `× 32` for every per-layer method; absent for prompt tuning |
| Trainable % | Absolute count divided by 7,000,000,000 |

**Walk one example.** All seven methods costed on that one 7B model, cheapest first:

```
  method                 per-site x sites x layers            absolute      % of 7B
  -------------------------------------------------------------------------------
  IA3 (k,v,ffn)          4,096  x  3  x  32                    393,216       0.0056
  Prompt tuning k=100    100 x 4096   (no depth term)          409,600       0.0059
  BitFit (biases only)   39,680 per layer x 32               1,269,760       0.0181
  Prefix l=10            10 x 4096 x 2  x  32                2,621,440       0.0374
  Adapter r=8            2 x 4096 x 8  x  2  x  32           4,194,304       0.0599
  DoRA r=8   (q,k,v,o)   8 x 8192 x 4 x 32  + 4096 x 4 x 32  8,912,896       0.1273
  LoRA r=16  (q,k,v,o)   16 x 8192  x  4  x  32             16,777,216       0.2397
  LoRA r=16  (attn+FFN)  16 x 8192  x  7  x  32             29,360,128       0.4194
  -------------------------------------------------------------------------------
  span, cheapest to dearest:  29,360,128 / 409,600  =  71.7x
```

Three readings fall straight out of that column. **IA3 undercuts LoRA by 32× per site** because a scaling vector costs `d = 4096` where a rank-16 update costs `16 × 8192 = 131,072` — the file's "roughly 32× fewer" is exactly `131,072 / 4,096`. **Prompt tuning is the only row with no `× 32`**, which is simultaneously why it is cheapest and why it needs an enormous frozen model to work. And **every method above 0.1% is a mergeable one** — LoRA and DoRA — which is the real pattern in the table: the methods that buy the most capacity are also the ones that cost nothing at inference.

The BitFit row assumes a GPT-style 7B that has bias terms at all; on a LLaMA-style model with `bias=False` everywhere, that row is zero and the method does not apply.

### Trainable-Parameter Spectrum
```
Trainable params as % of a 7B model (LOG scale — each step is roughly 10×):

Prompt tuning  ~0.001% │█
Prefix (l=10)   ~0.03% │███
Adapter (r=8)    0.06% │████
BitFit           <0.1% │████
DoRA (r=8)      ~0.12% │█████
LoRA (r=16)   0.2-0.5% │███████  ◄ most trainable params, highest quality
QLoRA (r=16)  0.2-0.5% │███████    (same param count as LoRA; frozen base is 4-bit)
                       └──────────────────────────
                        fewer params ──► more adaptation capacity
```
The spread spans ~500× (0.001% to 0.5%). Prompt/prefix tuning sit at the extreme-thin
end and only reach LoRA-level quality at very large model scales; LoRA's slightly
higher parameter budget is what buys its reliable quality across model sizes.

---

## 4. Architecture Diagram

### PEFT Methods Architecture Overview
```
Transformer Layer

Original:
  Input → [Self-Attention (W_q, W_k, W_v, W_o)] → [LayerNorm] → [FFN] → [LayerNorm] → Output

Adapter:
  Input → [Self-Attention] → [LayerNorm] → [Adapter ↑↓] → [FFN] → [LayerNorm] → [Adapter ↑↓] → Output
  (+ symbols = skip connection inside adapter)

LoRA:
  Input → [Self-Attention (W_q + B_q×A_q, W_k, W_v + B_v×A_v, W_o)] → [LayerNorm] → [FFN] → [LayerNorm] → Output
  (LoRA matrices B×A added to specific weight matrices; no new layers)

Prefix Tuning:
  Prefix P_k, P_v ──────────────────────┐
                                         ↓
  Input → [Self-Attention (Q attends to [P_k|K] and [P_v|V])] → [LayerNorm] → [FFN] → [LayerNorm] → Output

Prompt Tuning:
  [Learned Prompt P | User Input X] → [Full Transformer (frozen)] → Output
  (only input embeddings modified; all layers unchanged)
```

### Inference Overhead Comparison
```
LoRA (merged):
  W_query ─────────────────────> output
  (single matmul; same as original)

Adapter:
  x → W_main → + [down_proj × up_proj of residual] → output
  (always 2 extra matmuls per adapter per forward pass)

Prefix (at inference):
  [P_k | K], [P_v | V]  ← extra KV cache allocation per layer
  Same compute; more memory
```

---

## 5. Real-World Examples

### LoRA in Production (HuggingFace Hub)
- Thousands of LoRA adapters for LLaMA-3, Mistral, Qwen on HuggingFace Hub
- Single adapter file (50-200MB) vs. 14GB full model
- Users apply adapters on top of their own base model download

### Adapter Layers in Cross-Lingual Transfer
- AdapterHub: library for sharing and combining adapter modules
- Language adapters: one per language, stacked on top of shared base model
- Task adapters: one per task, combined with language adapters
- Enables zero-shot cross-lingual transfer for NLP tasks

### Prefix Tuning for Conditional Generation
- Original prefix tuning paper (Li and Liang 2021): table-to-text and summarization tasks
- Prefix captures the "generation mode" (formal vs. casual, domain of content)
- Useful for switching between generation styles without separate models

---

## 6. Tradeoffs

| Dimension | Adapter | Prefix | Prompt | LoRA | BitFit | DoRA |
|-----------|---------|--------|--------|------|--------|------|
| Trainable % | 0.06% | 0.03% | 0.001% | 0.2-0.5% | <0.1% | ~0.12% |
| Quality | Good | Moderate | Good (large) | Best | Low | Very good |
| Inference overhead | +5-10% | Context | Minimal | 0% (merged) | 0% | 0% (merged) |
| Mergeability | No | No | No | Yes | Yes | Yes |
| Multi-task stacking | Yes (stack) | No | No | Yes (switch) | No | Yes |
| Memory during training | Low | Very low | Minimal | Low | Minimal | Low |

---

## 7. When to Use / When NOT to Use

### Use LoRA When:
- General fine-tuning task requiring good quality
- Need merged model for deployment (zero inference overhead)
- Multiple task-specific adapters from same base model

### Use Adapters When:
- Multi-task stacking required (language adapter + task adapter composition)
- Cannot merge model weights post-training
- Research setting exploring PEFT composition

### Use Prompt Tuning When:
- Very large model (>10B parameters)
- Cannot modify any model weights at all (API access only to embedding layer — rare)
- Extremely low memory budget where even LoRA is too large

### Use BitFit When:
- Simple classification task on medium-sized model
- Want minimal parameter change with some performance gain
- Research / ablation study

### Use DoRA When:
- LoRA quality is insufficient for available memory budget
- Need better quality at the same rank as LoRA
- Memory is the hard constraint and quality must be maximized

---

## 8. Common Pitfalls

**1. Adapter inference overhead in latency-sensitive production**
Adapter layers add 5-10% latency on every inference call. At high QPS, this adds up.
Fix: Prefer merged LoRA over adapters for production deployments where latency is measured.

**2. Prefix tuning on small models**
Prefix tuning achieves full fine-tuning quality only at large scales (>10B parameters). At smaller scales, it underperforms LoRA significantly.
Fix: Use LoRA for models under 10B. Only consider prefix tuning for very large models where even LoRA memory is a concern.

**3. Prompt tuning with improper initialization**
Randomly initialized soft prompt tokens learn slowly and may not converge to good solutions.
Fix: Initialize soft prompts from the vocabulary's most task-relevant words. For a classification task: initialize with class name tokens. This provides a better starting point for gradient-based optimization.

**4. Applying the wrong PEFT method for the task complexity**
BitFit for a complex generation task (insufficient capacity) or LoRA r=64 for a simple classification task (excessive parameters, slower training).
Fix: Match PEFT method and rank/bottleneck size to task complexity. Start with LoRA r=8 for classification, r=16 for generation, r=32-64 for domain adaptation.

**5. Stacking too many adapters without considering compositionality**
Stacking a language adapter, a domain adapter, and a task adapter — without evaluating whether they compose coherently — can produce interference rather than complementary effects.
Fix: Evaluate each adapter independently before stacking. Check that adapter combinations produce better results than any single adapter alone.

---

## 9. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **HuggingFace PEFT** | All PEFT methods | LoRA, QLoRA, adapters, prefix tuning, prompt tuning |
| **AdapterHub** | Adapter sharing/composition | Pre-trained adapters; language + task composition |
| **OpenDelta** | PEFT methods library | Supports many PEFT methods including Delta-tuning |
| **LLaMA-Adapter** | Prefix-style adaptation for LLaMA | Vision + language adaptation for multimodal LLaMA |
| **DoRA (HuggingFace PEFT)** | DoRA implementation | `use_dora=True` in LoraConfig |

---

## 10. Interview Questions with Answers

**Q: What is PEFT and why is it used instead of full fine-tuning?**
A: Parameter-Efficient Fine-Tuning (PEFT) trains only a small fraction of model parameters (0.001-1%) while freezing the rest. Full fine-tuning of a 70B model requires ~420GB GPU memory (weights + gradients + optimizer states) — six to eight A100 80GB GPUs. PEFT reduces this to under 40GB by training only adapter matrices or low-rank updates. The quality loss is typically 1-3% compared to full fine-tuning, which is acceptable for most applications. Additional benefits: frozen base weights prevent catastrophic forgetting; small adapter files (50-200MB) can be versioned and deployed separately from the 140GB base model; multiple task-specific adapters can be maintained without duplicating the base model.

**Q: Compare adapter layers, prefix tuning, prompt tuning, and LoRA — when would you choose each?**
A: LoRA is the correct default for most production fine-tuning: good quality, merges to zero inference overhead, works well at any model scale, supported by all major training frameworks. Choose adapters when you need modular compositionality — stacking language adapters with task adapters for cross-lingual, cross-task generalization; the inference overhead (~5-10%) is acceptable. Choose prefix tuning when you need to condition generation behavior (writing style, domain register) without changing weights; works well at large scales (>10B). Choose prompt tuning only for very large models (>10B) and scenarios with minimal training capacity — soft prompts at GPT-3 scale achieve full fine-tuning quality; at smaller scales, LoRA is significantly better. Choose BitFit only for simple classification tasks as a minimal baseline.

**Q: What is DoRA and how does it improve on LoRA?**
A: DoRA (Weight-Decomposed Low-Rank Adaptation) decomposes weight matrices W into magnitude m and direction V/||V|| components, then applies LoRA to the direction component while separately training the magnitude. This decomposition mirrors how the model makes "how much" (magnitude) and "in what direction" (direction) decisions — updating them separately with appropriate parameterizations produces more expressive updates per parameter. Empirically: DoRA at rank r=8 often matches or exceeds LoRA at rank r=16 on downstream tasks, providing the same quality with 2× fewer trainable parameters. Like LoRA, DoRA adapters can be merged post-training for zero inference overhead.

**Q: Why does prompt tuning fail at small model scales but work at large scales?**
A: Prompt tuning learns soft prompt tokens (free-floating embeddings in the input layer) through gradient descent. At small model scales (<1B parameters), the model lacks the capacity to leverage these soft prompts effectively — the model's representations are not expressive enough to interpret arbitrary soft vectors as meaningful task conditioning. The soft prompts gradient landscape is complex, and small models don't have the capacity to find good solutions. At large model scales (>10B), the model's representations are rich enough that soft prompts can effectively steer behavior — they act as "continuous" versions of few-shot demonstrations. The original prompt tuning paper (Lester et al. 2021) showed this scaling behavior clearly: at T5-11B, prompt tuning matches full fine-tuning; at T5-Small, it performs much worse.

**Q: Can PEFT methods be combined? What are the considerations?**
A: Yes, PEFT methods can be combined, with important caveats. Additive combination: multiple LoRA adapters for different tasks can be switched or merged independently — vLLM supports hot-swapping LoRA adapters at serving time. Sequential stacking: language adapter followed by task adapter (AdapterHub approach) — the language adapter is always active; the task adapter is task-specific; both contribute to the final output. Combination considerations: (1) Weight interference — two adapters trained independently may interfere when stacked; evaluate the combination, not just each adapter alone; (2) Memory at inference — multiple adapters in memory simultaneously; (3) Training independence — adapters should be trained on their respective tasks separately, not jointly. PEFT combination is a research-active area; simple use cases (task switching with LoRA) are production-ready; complex stacking is still experimental.

**Q: What is the inference overhead of each PEFT method and how does it affect production deployment?**
A: Merged LoRA: zero overhead — merged weights are a single matrix, identical to running the base model. Adapters: 5-10% latency increase per forward pass — two extra matmuls per layer (down + up projection) always execute, regardless of whether the adapter is "active." Prefix tuning: memory overhead (larger KV cache) but minimal compute overhead — the KV cache must store extra prefix entries. Prompt tuning: k extra tokens in the input, propagated as full context through all layers — effectively reduces effective context window by k tokens. For production inference: LoRA (merged) is the only PEFT method with truly zero overhead. Adapters should be avoided in latency-sensitive applications unless the 5-10% overhead is acceptable.

**Q: How does LoRA rank relate to full fine-tuning expressiveness?**
A: LoRA at rank r restricts the weight update ΔW to rank ≤ r. Full fine-tuning has no rank restriction — ΔW can be any arbitrary matrix. The relationship: at r=d (where d is the weight dimension), LoRA has the same expressiveness as full fine-tuning but loses the efficiency benefit. In practice, fine-tuning updates empirically have low rank (the intrinsic dimensionality hypothesis), so r=16 to r=64 captures 90-98% of the expressiveness of full fine-tuning. The remaining 2-10% quality gap matters most for: tasks requiring significant distributional shift, very complex task learning, or scenarios where the fine-tuning objective changes many layers' representations simultaneously. For these, higher rank (r=64 to r=128) or full fine-tuning is justified.

**Q: What are the key differences between PEFT for fine-tuning LLMs vs. fine-tuning smaller models like BERT?**
A: Scale differences drive significant practical differences. For BERT-scale models (110M-400M params): PEFT offers modest memory savings (full FT requires ~1-2GB; PEFT reduces to hundreds of MB — not the critical bottleneck); training latency is not the primary concern; even BitFit can be practical. For LLM-scale models (7B-70B+ params): PEFT is essential — without it, training requires multi-GPU or unavailable hardware; memory savings of 10-100× are the key enabler; adapter inference overhead matters because serving at scale amplifies small per-call costs. The quality-efficiency tradeoff also differs: for BERT, full fine-tuning is routine and PEFT is mainly for serving flexibility; for LLMs, PEFT with minimal quality loss is the production standard.

**Q: What is LoftQ and how does it combine quantization with LoRA?**
A: LoftQ (LoRA-Fine-Tuning-Aware Quantization, 2023) addresses a specific problem in QLoRA: when the base model is quantized to 4-bit NF4, the LoRA adapter is initialized randomly. The quantization error in the 4-bit base model and the random LoRA initialization create a gap at training start — the model needs several steps to recover from the quantization-initialization mismatch. LoftQ initializes the LoRA adapter using the quantization error: initialize A and B such that W_quantized + B×A ≈ W_original, where W_quantized is the 4-bit quantized weight. This minimizes the initialization gap and allows training to immediately optimize for the fine-tuning objective rather than first recovering from quantization error. Result: LoftQ achieves marginally better final quality than QLoRA on most benchmarks, especially noticeable at lower quantization bits (2-3 bit).

**Q: When would you use PEFT for inference rather than fine-tuning?**
A: PEFT for inference means applying pre-trained adapters at deployment time rather than training new ones. Use cases: (1) Multi-task serving — a single base model with multiple task-specific LoRA adapters, swapped per request type (vLLM supports this with minimal overhead); (2) Personalization — per-user or per-tenant LoRA adapters stored cheaply (50MB each) and loaded on-demand; (3) Style switching — different adapters for different response styles (formal, casual, domain-specific) applied dynamically; (4) AB testing — comparing adapter variants in production without deploying separate model versions. The key advantage: all adapter variants share the same base model loaded once on GPU; adapter weights are loaded into a small memory region and applied as matrix additions, costing effectively zero inference latency with LoRA (merged at load time).

**Q: When should you choose LoRA over prefix tuning, and what determines that decision?**
A: LoRA and prefix tuning have different inductive biases that make each better suited to different scenarios. LoRA modifies weight matrices directly, making it effective for behavioral changes — teaching the model a new output format, response style, or task-specific reasoning pattern. Prefix tuning prepends learned virtual tokens to attention keys and values, making it better for task-specific conditioning of generation — guiding what the model generates (domain register, writing style) without explicitly changing how it reasons. Choose LoRA when: the task requires consistent structured outputs (JSON, SQL, code), domain-specific terminology or factual grounding, or multi-step reasoning chains. Choose prefix tuning when: you need to condition generation style or domain without modifying the model's knowledge (e.g., "always respond formally," "generate in the style of legal contracts"), the model scale is large (>10B, where prefix tuning becomes competitive), or you need multiple conditioning modes that cannot be merged. A practical decision rule: if you can describe the desired change as "the model should think differently," use LoRA; if you can describe it as "the model should respond in a certain register," consider prefix tuning.

**Q: What is IA3 and how does it differ from LoRA in parameter efficiency?**
A: IA3 (Infused Adapter by Inhibiting and Amplifying Inner Activations, Liu et al. 2022) learns element-wise scaling vectors that multiply into keys, values, and FFN intermediate activations — not matrix multiplications. Instead of ΔW = B×A (rank-r matrix requiring r×(d+k) parameters), IA3 learns a single vector l ∈ ℝ^d that scales activations: h_adapted = l ⊙ h_original. This is a Hadamard (element-wise) product, not a matrix multiply. Parameter count: for each targeted activation (keys, values, FFN intermediate), IA3 requires only d parameters versus LoRA's r×(d+k) per layer. For a 7B model with d=4096 and r=16: IA3 uses 4096 per target; LoRA uses 16×(4096+4096) = 131,072 per target — roughly 32× fewer parameters with IA3. The tradeoff: element-wise scaling is less expressive than low-rank matrix updates; IA3 works well for simple task adaptation (classification, format changes) but underperforms LoRA on tasks requiring significant behavioral change. IA3 is particularly useful for few-shot in-context learning scenarios where you want efficient adaptation to each example's context.

**Q: How does AdapterFusion enable multi-task serving and what are its limitations?**
A: AdapterFusion (Pfeiffer et al. 2021) trains a learned weighted combination of multiple task-specific adapters. The training process has two phases: first, train separate adapters for each task independently; second, freeze all adapters and the base model, then train a small attention-based fusion layer that learns to weight each adapter's output based on the current input. The fusion layer computes: h_fused = sum(attention_weight_i × adapter_i(h)) where attention weights are input-dependent. This enables a single model to handle multiple tasks by dynamically weighting adapter contributions per input. Limitations: (1) inference latency — all adapters execute on every forward pass regardless of which task is relevant, making inference cost scale linearly with number of tasks; (2) training complexity — fusion training requires all task-specific training data simultaneously to learn good attention weights; (3) task interference ceiling — adapters trained on highly dissimilar tasks interfere even with learned fusion weights, capping maximum performance below single-task adapters. AdapterFusion is primarily a research technique; production multi-task deployments typically prefer per-request adapter routing (vLLM LoRA hot-swapping) over fusion because it avoids the latency and interference problems.

**Q: How do you use PEFT for multi-task learning where different inputs need different fine-tuned behaviors?**
A: The standard production pattern is to train separate LoRA adapters per task and route at inference time based on task classification. Architecture: (1) train one LoRA adapter per task using the same base model; (2) at inference, run a lightweight intent classifier (BERT-scale model or even regex rules) to determine the task type for each incoming request; (3) load the appropriate adapter for the request using vLLM's LoRA hot-swap API (serving internals: [vLLM Deep Dive](../vllm_deep_dive/README.md)). vLLM maintains a pool of adapter slots in GPU memory (configurable, typically 4-8 simultaneous adapters) and evicts least-recently-used adapters from GPU to pinned CPU memory when the pool is full; adapter swap latency is 5-20ms depending on adapter size. This approach provides isolation — a change to one task's adapter does not affect other tasks — and independent scaling (high-traffic tasks can use dedicated serving instances). The alternative, training a single adapter on mixed multi-task data, typically underperforms per-task adapters by 3-7% on each individual task because the adapter must compromise between task-specific optimization directions.

**Q: What is the cost of switching between PEFT methods mid-project, and when is it justified?**
A: Switching PEFT methods (e.g., from LoRA to prefix tuning) costs primarily in data preparation and hyperparameter search, not in architectural code changes. The HuggingFace PEFT library abstracts the method differences — changing from LoRA to prefix tuning is a configuration change (swap LoraConfig for PrefixTuningConfig), not a code rewrite. The real switching costs: (1) hyperparameter re-search — LoRA hyperparameters (rank, alpha, target modules) do not transfer to prefix tuning (prefix length, reparameterization MLP hidden size); expect 5-10 training runs to find good prefix tuning hyperparameters; (2) data re-formatting — prefix tuning and LoRA use identical data formats, so no reformatting cost; (3) adapter re-training — prior adapters cannot be converted between PEFT methods; full retraining is required; (4) evaluation re-run — re-evaluate all benchmarks with the new method. Switching is justified when: the current PEFT method has hit a quality ceiling that cannot be overcome by hyperparameter tuning (e.g., LoRA r=64 still underperforms by >5% vs. full FT), or when production constraints change (e.g., merge capability becomes required and you're using adapters). In practice, most projects choose LoRA initially and tune rank rather than switching methods.

---

## 11. Best Practices

1. **Default to LoRA** — the best balance of quality, memory efficiency, merge capability, and tooling support; only deviate with specific justification.
2. **Avoid adapters in latency-sensitive production** — the 5-10% per-call overhead from adapter layers compounds at scale; use merged LoRA instead.
3. **Use DoRA when memory severely constrains rank** — DoRA achieves better quality at the same rank; reduces the quality gap when r must be very low (r=4 to r=8).
4. **Match PEFT method to task complexity** — BitFit for classification, LoRA r=8-16 for instruction tuning, LoRA r=32-64 for domain adaptation.
5. **Use prompt tuning only for >10B models** — at smaller scales, prompt tuning significantly underperforms LoRA; never use it as a lazy alternative to LoRA for small/medium models.
6. **Evaluate PEFT quality against full FT baseline** — know the quality gap; if LoRA r=64 still falls 5% below full FT on your eval set, full fine-tuning may be justified.
7. **Store adapter metadata** — base model name, rank, alpha, target modules, training dataset, training date; essential for reproducing results and correct deployment.

---

## 12. Case Study: Multi-Task Customer Service Platform Using Adapter Composition

**Problem Statement**: A large e-commerce company runs a customer service platform handling three distinct query types: billing disputes, technical product support, and returns/refunds. Each domain has different vocabulary, reasoning patterns, and output structures. A single generalist fine-tuned model performs at 81% average accuracy across all three domains. Domain-specific full fine-tuned models each achieve 91-93% accuracy but require three separate 14GB model deployments — 42GB total just for model weights, plus separate serving infrastructure per domain. The team needs to serve all three domains from a single GPU instance while achieving per-domain accuracy comparable to single-task fine-tuned models.

**Architecture Overview**:
```
Training Phase (independent, parallel):

Base Model: Mistral 7B Instruct (shared across all adapters)

Task-Specific LoRA Adapters:
  billing_adapter     (r=16, q+k+v+o, 50MB)
  technical_adapter   (r=16, q+k+v+o+FFN, 80MB)
  returns_adapter     (r=8,  q+v, 30MB)

(Technical adapter targets FFN because product troubleshooting
 requires domain-specific factual associations; billing and returns
 are format/behavior changes only)

Inference Routing:

  Incoming Request
       |
       v
  Intent Classifier (DistilBERT, 66M params, <10ms latency)
       |
       +--[billing]--> load billing_adapter ──> vLLM ──> response
       |
       +--[technical]-> load technical_adapter ──> vLLM ──> response
       |
       +--[returns]--> load returns_adapter ──> vLLM ──> response

vLLM Multi-Adapter Serving:
  Base model (Mistral 7B, 14GB BF16) loaded once
  Adapter pool: 3 adapters in GPU memory simultaneously (160MB total)
  Adapter hot-swap: <5ms P99 (adapter already in GPU pool)
```

**Key Design Decisions**:
1. Separate adapters per domain rather than a single mixed-domain adapter: per-domain accuracy improved from 81% (single mixed adapter) to 88-91% per domain — a 7-10 percentage point improvement that justified the added routing complexity.
2. Different configurations per task: billing (r=16, attention-only) because billing dispute handling is primarily format and tone; technical (r=16, attention+FFN) because product-specific troubleshooting requires factual associations in FFN layers; returns (r=8, q+v only) because returns queries follow highly templated patterns that simple attention adaptation handles adequately.
3. Intent classifier choice: DistilBERT fine-tuned on 2,000 labeled routing examples achieves 97.3% routing accuracy in under 10ms; incorrect routing adds latency but does not produce catastrophically wrong outputs — the base model handles off-domain queries gracefully.
4. All adapters trained on identical base model checkpoint: any version mismatch between adapters and the serving base model causes incorrect behavior. Pinned the base model to a specific commit hash.

**Implementation**:
```python
# Training each adapter independently (same pattern for all three)
from peft import LoraConfig, get_peft_model
from trl import SFTTrainer

# Billing adapter — attention only, simple format adaptation
billing_config = LoraConfig(
    r=16, lora_alpha=32,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj"],
    lora_dropout=0.05, bias="none", task_type="CAUSAL_LM"
)

# Technical adapter — attention + FFN for factual associations
technical_config = LoraConfig(
    r=16, lora_alpha=32,
    target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                    "gate_proj", "up_proj", "down_proj"],
    lora_dropout=0.05, bias="none", task_type="CAUSAL_LM"
)

# Returns adapter — minimal, q+v only for templated patterns
returns_config = LoraConfig(
    r=8, lora_alpha=16,
    target_modules=["q_proj", "v_proj"],
    lora_dropout=0.05, bias="none", task_type="CAUSAL_LM"
)

# Serving with vLLM multi-adapter support
from vllm import LLM, SamplingParams

llm = LLM(
    model="mistralai/Mistral-7B-Instruct-v0.3",
    enable_lora=True,
    max_lora_rank=16,
    max_loras=3,                    # keep all 3 adapters in GPU pool
)

# Route and serve
def handle_request(query: str) -> str:
    domain = intent_classifier.predict(query)   # billing/technical/returns
    adapter_map = {
        "billing": "./billing_adapter",
        "technical": "./technical_adapter",
        "returns": "./returns_adapter"
    }
    request = LoRARequest(domain, 1, adapter_map[domain])
    outputs = llm.generate([query], SamplingParams(max_tokens=512),
                            lora_request=request)
    return outputs[0].outputs[0].text
```

**Results**:
- Billing domain accuracy: 88.4% (vs. 93% single-task full FT; vs. 81% single mixed adapter)
- Technical domain accuracy: 91.2% (vs. 93% single-task full FT; best result across domains due to FFN targeting)
- Returns domain accuracy: 87.1% (vs. 91% single-task full FT; r=8 adapter sufficient for templated task)
- GPU memory: 14GB (base) + 0.16GB (3 adapters) = 14.16GB — single A100 40GB handles production load
- Inference latency P50: 340ms (vs. 330ms with single generalist adapter — 10ms overhead for intent classification)
- Infrastructure savings: 1 GPU instance vs. 3 separate instances; 68% cost reduction in serving infrastructure
- Total adapter training time: 6 hours across 3 adapters (parallel training on 3 GPUs — 2 hours each)

**Tradeoffs and Alternatives**:
- Single mixed-domain LoRA adapter: 81% average accuracy — 7-10 percentage points lower; avoids routing complexity but unacceptable quality for this application.
- Three separate full model deployments: 91-93% accuracy per domain — the quality ceiling; costs 3× more in GPU serving infrastructure and requires 3× the model storage.
- AdapterFusion: evaluated in a research spike — all three adapters execute on every request (3× compute), routing accuracy from attention weights was only 91% (below the 97.3% of a dedicated classifier), and managing training complexity was significantly higher. Not adopted for production.
- Prompt engineering (no fine-tuning): 74% average accuracy — insufficient for production customer service where incorrect resolution advice generates escalations and refund costs that exceed the fine-tuning investment.
