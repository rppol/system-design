# Sampling & Decoding Strategies — Token Selection Internals

Deep-dive sub-file of [Inference & Decoding](README.md). Covers the full token-selection toolbox beyond temperature and top-p — min-p, typical/eta/epsilon sampling, Mirostat, repetition/presence/frequency penalties, DRY, contrastive search and contrastive decoding, XTC, beam search, the sampler-ordering gotcha, and determinism in production LLM APIs.

---

## 1. Concept Overview

After the model produces a logit vector over the vocabulary, *something* must turn that vector into a single token. That "something" is a chain of transformations — penalty application, temperature scaling, distribution truncation, renormalization, and sampling — collectively the **sampler** (or "logits processor pipeline"). Every production inference engine (HF `transformers`, vLLM, llama.cpp, the OpenAI/Anthropic APIs) implements a version of this chain, and the *order* and *parameters* of that chain are a first-class API surface, not an implementation detail.

This matters because the same model checkpoint can feel completely different — coherent vs. rambling, creative vs. robotic, looping vs. fresh — purely as a function of sampler configuration, with zero change to weights. Senior interviews probe this because it's one of the few "free" levers a team controls without retraining, and because sampler misconfiguration is a disproportionately common source of production quality incidents (repetition loops, truncated JSON, "the model got dumber after we switched inference engines").

This file is the decoding-*algorithm* half of the picture. For how a grammar mask interacts with this pipeline, see [constrained_decoding_and_structured_outputs.md](constrained_decoding_and_structured_outputs.md). For how draft and target models must agree on sampler configuration, see [speculative_decoding.md](speculative_decoding.md).

---

## 2. Intuition

> **One-line analogy**: The logit vector is a ranked shortlist of "what to say next"; the sampler is the editorial policy that decides how far down that shortlist you're allowed to pick from, and how harshly you penalize an author who keeps repeating themselves.

**Mental model**: Think of the raw logits as a *shape* — sometimes a sharp single spike (only one sensible next token, e.g., after "The capital of France is"), sometimes a broad plateau (many plausible next tokens, e.g., after "My favorite color is"). A good sampler *adapts its behavior to that shape*: it should pick the spike almost every time, but explore the plateau. The historical mistake (top-k, fixed top-p) is applying the *same absolute rule* regardless of shape — cutting a sharp spike too wide (admitting garbage) or a broad plateau too narrow (forcing repetitive "safe" choices). The newer methods (min-p, typical sampling, Mirostat) are all attempts to make the cutoff *shape-relative*.

**Why it matters**: Sampler configuration is the cheapest lever in the entire LLM stack — a config change, redeployed in minutes, with no GPU cost — yet it directly drives perceived quality metrics (repetition rate, coherence, "feels robotic" complaints) that teams otherwise chase with expensive fine-tuning. It's also a frequent silent regression vector: migrating from one inference engine to another with "the same" temperature and top_p values can produce measurably different outputs if the engines apply the sampler chain in a different order (Section 6.3).

**Key insight**: Almost every sampling technique beyond temperature is solving the same underlying problem — **the tail of an LLM's distribution contains both "creative but valid" and "incoherent garbage" tokens, and probability alone cannot distinguish them**. Top-p tries to draw a line by cumulative mass; min-p draws it relative to the mode; typical sampling draws it relative to the distribution's entropy; Mirostat draws it dynamically to hit a target entropy over time. None of these are "solved" — they're different heuristics for the same unsolved discrimination problem, which is why new samplers keep appearing.

---

## 3. Core Principles

1. **Sampling is post-hoc; it never changes the model's beliefs.** Every method here operates on the logit vector the forward pass already produced — none of them make the model "think" differently, only how its output distribution is *read*.
2. **Truncation methods trade coherence for diversity along a shape-awareness spectrum.** Top-k (fixed count) is shape-*unaware*; top-p (cumulative mass) is partially shape-aware; min-p and typical sampling are explicitly shape-relative.
3. **Order of operations is part of the contract.** Penalties, temperature, and truncation methods compose, but composing them in a different order produces a different final distribution from the *same* parameter values (Section 6.3) — this is a common cross-engine migration bug.
4. **Penalties operate on logits using generation history; truncation operates on the post-penalty distribution.** Confusing these layers (e.g., expecting a repetition penalty to "filter" tokens the way top-p does) is a common misunderstanding.
5. **T=0 is not "temperature scaling with T=0"** — it's a special-cased argmax (division by zero is undefined), and is the only configuration that's fully deterministic *given identical floating-point execution* (Section 6.8).
6. **Beam search optimizes a different objective than sampling** — highest joint sequence probability, not "a plausible draw from the distribution" — which is why it produces qualitatively different (often more repetitive) text for open-ended tasks.
7. **Sampler choices interact with constrained decoding and speculative decoding**, not just with output quality — masks must be applied at the right point in the chain, and draft/target models must share sampler configuration for speculative decoding's correctness guarantee to hold.

---

## 4. Types / Approaches

| Method | Mechanism | Shape-adaptive? | Typical use |
|--------|-----------|-----------------|-------------|
| **Greedy (T=0)** | argmax(logits) | N/A | Factual QA, code, structured extraction, reproducibility |
| **Temperature** | `logits / T` before softmax | No (uniform rescale) | Baseline creativity knob, T=0.7-1.0 for chat |
| **Top-k** | keep fixed-count highest-probability tokens | No | Legacy default; poor at distribution extremes |
| **Top-p (nucleus)** | keep smallest set with cumulative prob ≥ p | Partially | Most common production default (p≈0.9-0.95) |
| **Min-p** | keep tokens with prob ≥ min_p × max_prob | Yes | Newer default (llama.cpp, some chat UIs); good on both peaked and flat distributions |
| **Typical / locally-typical sampling** | keep tokens whose −log p is closest to the distribution's entropy | Yes | Avoids both "too obvious" and "too surprising" tokens |
| **Eta sampling** | entropy-scaled dynamic probability floor | Yes | llama.cpp option, rarely used alone |
| **Epsilon sampling** | absolute probability floor | No | Simple tail-cutting, often combined with others |
| **Tail-free sampling (TFS)** | cut where the second derivative of sorted probs indicates the "tail" begins | Yes | Rare; precursor to min-p |
| **Mirostat (v1/v2)** | feedback-controlled top-k that targets a constant perplexity | Yes (dynamic over time) | Long-form generation, avoids drift to repetition or incoherence |
| **Repetition penalty** | divide/multiply logits of previously-seen tokens | N/A | Blunt anti-repetition |
| **Presence penalty** | fixed subtraction for any token seen ≥1 time | N/A | OpenAI-style anti-repetition |
| **Frequency penalty** | subtraction proportional to occurrence count | N/A | OpenAI-style, penalizes heavy reuse more |
| **No-repeat-ngram** | hard-ban tokens that would recreate a seen n-gram | N/A | Anti-loop, can over-trigger on legitimate repeats |
| **DRY (Don't Repeat Yourself)** | exponential penalty scaled by length of the repeated sequence it would recreate | N/A | Targets verbatim-loop degeneration specifically |
| **Contrastive search** | maximize `(1-α)·p(token) − α·max_sim(hidden_token, hidden_history)` | N/A | Open-ended generation, reduces repetition at the representation level |
| **Contrastive decoding** | amplify `log p_expert − α·log p_amateur` (two models) | N/A | Reduce generic/hallucinated continuations |
| **XTC (Exclude Top Choices)** | probabilistically *remove* the most-likely tokens before sampling | N/A | Creative writing — forces exploration of "second-best" continuations |
| **Beam search** | keep top-k highest joint-probability sequences | N/A | Translation, ASR — maximize sequence likelihood, not diversity |

---

## 5. Architecture Diagrams

### 5.1 The sampler pipeline (canonical order)

```
raw logits [V]
   │
   v
┌──────────────────────────────────────────┐
│ 1. Logit bias / grammar mask              │  add/subtract per-token offsets;
│    (set disallowed tokens to -inf)        │  MUST run before truncation, else
└──────────────────────────────────────────┘  masked tokens can dominate the nucleus
   │
   v
┌──────────────────────────────────────────┐
│ 2. Repetition / presence / frequency      │  history-dependent logit adjustments
│    penalties, no-repeat-ngram, DRY        │  (operate on raw logit SCALE)
└──────────────────────────────────────────┘
   │
   v
┌──────────────────────────────────────────┐
│ 3. Temperature scaling: logits / T        │  reshapes the distribution's
└──────────────────────────────────────────┘  sharpness BEFORE truncation
   │
   v
┌──────────────────────────────────────────┐
│ 4. Top-k truncation (if set)              │  fixed-count cut
└──────────────────────────────────────────┘
   │
   v
┌──────────────────────────────────────────┐
│ 5. Top-p (nucleus) truncation (if set)    │  cumulative-mass cut
└──────────────────────────────────────────┘
   │
   v
┌──────────────────────────────────────────┐
│ 6. Min-p truncation (if set)              │  relative-to-mode cut
└──────────────────────────────────────────┘
   │
   v
softmax (renormalize survivors) -> sample -> token
```

### 5.2 Why shape matters: peaked vs. flat distributions

```
PEAKED distribution (after "The capital of France is")
  "Paris" = 0.92, "the" = 0.03, "located" = 0.01, ... (long flat tail)

  top-p=0.9:  needs only {"Paris"}              -> good, decisive
  top-k=50:   keeps 50 tokens incl. near-zero-prob tail -> wastes budget
  min-p=0.05: threshold = 0.05*0.92=0.046 -> keeps {"Paris"} only -> good

FLAT distribution (after "My favorite color is")
  "blue"=0.12, "green"=0.11, "red"=0.10, "purple"=0.09, ... (20 similar)

  top-p=0.9:  needs ~15-18 tokens to reach 0.9   -> reasonable
  top-k=50:   keeps 50, including genuinely implausible tail -> noise
  min-p=0.05: threshold = 0.05*0.12=0.006 -> keeps ~18 tokens -> reasonable

min-p adapts its ABSOLUTE cutoff to the mode's probability in both cases;
top-k never adapts; top-p adapts but can still admit low-absolute-probability
tokens right at its cumulative boundary in flat distributions.
```

### 5.3 Beam search vs. sampling

```
Sampling (single path, stochastic):
  step1: sample "Paris"
  step2: sample "is"
  step3: sample "the"
  -> ONE sequence, drawn from the model's distribution

Beam search (k=3, deterministic given beams):
  step1: top-3 by logprob: ["Paris"(-0.1), "France"(-1.8), "It"(-2.1)]
  step2: expand EACH beam by all tokens, keep global top-3 by
         CUMULATIVE logprob across all 3*V candidates:
         ["Paris is"(-0.3), "Paris,"(-0.9), "Paris -"(-1.1)]
  step3: repeat... each step costs k forward passes (or 1 pass with
         k*batch), and k* the KV cache of a single sequence

  -> k candidate sequences; return highest cumulative (length-normalized)
     logprob sequence. Tends toward generic/repetitive high-confidence text.
```

### 5.4 Contrastive search vs. contrastive decoding (commonly confused)

```
CONTRASTIVE SEARCH (one model, looks at its OWN hidden states):
  score(x) = (1-alpha) * p_model(x)
             - alpha   * max_cos_sim(hidden_state(x), hidden_states(history))
  -> penalizes tokens whose REPRESENTATION is too similar to recent
     context, even if the token ITSELF hasn't appeared before
     (catches semantic repetition, not just verbatim repetition)

CONTRASTIVE DECODING (two models: expert + amateur):
  score(x) = log p_expert(x) - alpha * log p_amateur(x)
  -> amplifies whatever the EXPERT finds likely that the AMATEUR
     (smaller/weaker model, or same model with truncated context)
     does NOT -- sharpens toward "expert-specific" content, away
     from generic continuations any model would produce
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Temperature and the T=0 special case

```python
import torch


def apply_temperature(logits: torch.Tensor, temperature: float) -> torch.Tensor:
    """
    logits: [V] raw model output for the next token.
    temperature == 0 is special-cased to greedy (argmax) because
    dividing by zero is undefined -- it is NOT "softmax of logits/0".
    """
    if temperature == 0.0:
        # Greedy: deterministic argmax, equivalent to T -> 0+ limit
        # where softmax collapses to a one-hot on the max logit.
        out = torch.full_like(logits, float("-inf"))
        out[torch.argmax(logits)] = 0.0
        return out
    return logits / temperature


# T < 1: sharpens the distribution (e.g., logits [2,1,0] at T=0.5
#        -> [4,2,0] -> softmax more peaked toward the max)
# T = 1: unmodified model distribution
# T > 1: flattens the distribution (logits [2,1,0] at T=2
#        -> [1,0.5,0] -> softmax closer to uniform)
```

### 6.2 Top-k, top-p, and min-p — the failure modes each one fixes

```python
import torch
import torch.nn.functional as F


def top_k_filter(logits: torch.Tensor, k: int) -> torch.Tensor:
    """Keep the k highest logits, set the rest to -inf.
    FAILS when the true distribution has <k plausible tokens (admits
    garbage from the tail) OR >k plausible tokens (cuts off valid options)."""
    if k <= 0:
        return logits
    top_k_vals, _ = torch.topk(logits, k)
    threshold = top_k_vals[-1]
    return torch.where(logits < threshold, torch.full_like(logits, float("-inf")), logits)


def top_p_filter(logits: torch.Tensor, p: float) -> torch.Tensor:
    """Nucleus sampling: keep the smallest set of tokens whose
    cumulative probability >= p. ADAPTS to distribution shape, but
    on a FLAT distribution can still admit the last token added to
    cross the threshold even if its individual probability is tiny."""
    probs = F.softmax(logits, dim=-1)
    sorted_probs, sorted_idx = torch.sort(probs, descending=True)
    cumsum = torch.cumsum(sorted_probs, dim=-1)
    # keep the first token that crosses p, plus everything before it
    cutoff = (cumsum > p).float().argmax().item()
    mask = torch.full_like(logits, float("-inf"))
    keep_idx = sorted_idx[: cutoff + 1]
    mask[keep_idx] = logits[keep_idx]
    return mask


def min_p_filter(logits: torch.Tensor, min_p: float) -> torch.Tensor:
    """Keep tokens with probability >= min_p * max_token_probability.
    Threshold SCALES with the mode -- naturally narrow on peaked
    distributions, naturally wide on flat ones. min_p=0.05 is a
    common default (llama.cpp)."""
    probs = F.softmax(logits, dim=-1)
    threshold = min_p * probs.max()
    return torch.where(probs < threshold, torch.full_like(logits, float("-inf")), logits)
```

**Concrete numeric comparison** (from Section 5.2's distributions):

```
Peaked: p(Paris)=0.92, tail of 50K tokens summing to 0.08
  top_k=50  -> keeps 50 tokens, ~49 of them near-zero-probability noise
  top_p=0.9 -> keeps {Paris} only (0.92 >= 0.9)         <- correct
  min_p=0.05-> threshold=0.046, keeps {Paris} only       <- correct

Flat: 20 tokens each ~0.05 (sums to 1.0)
  top_k=50  -> keeps all 20 plus 30 near-zero noise tokens
  top_p=0.9 -> keeps ~18 tokens to cross 0.9             <- reasonable
  min_p=0.05-> threshold=0.05*0.05=0.0025, keeps ~20      <- reasonable

top_k is the only one that behaves badly in BOTH cases -- it has no
information about where the "real" distribution ends. This is why
top_k=50 was a near-universal default for years despite never being
optimal: it's a safety net, not a principled cutoff.
```

### 6.3 Sampler ordering — the classic cross-engine gotcha

The order in Section 5.1 (penalties → temperature → top-k → top-p → min-p) is the de facto convention, but it is **not universal** — different engines historically applied these steps in different orders, and the *same numeric parameters* produce *different final distributions* depending on order:

```
Example: logits after softmax (T=1) = [0.5, 0.3, 0.15, 0.04, 0.01]
         temperature = 2.0,  top_p = 0.9

Order A (temperature THEN top_p) -- recommended:
  1. Apply T=2 to RAW LOGITS, re-softmax -> flatter distribution,
     e.g. [0.35, 0.27, 0.20, 0.11, 0.07]
  2. top_p=0.9 on the FLATTENED distribution -> needs 4 tokens to
     reach 0.9 -> keeps 4 tokens
  Result: temperature's flattening effect WIDENS the nucleus.

Order B (top_p THEN temperature) -- some legacy implementations:
  1. top_p=0.9 on the ORIGINAL distribution -> needs 3 tokens
     (0.5+0.3+0.15=0.95) -> keeps 3 tokens, DISCARDS the rest
  2. Apply T=2 to the 3 SURVIVING logits, re-softmax -> flatter
     among only those 3
  Result: temperature can ONLY redistribute mass among the 3 tokens
  top_p already selected -- it can never "rescue" a 4th token that
  Order A would have included.

Same (T=2, top_p=0.9) parameters; genuinely different candidate sets.
This is a real source of "we ported our prompts to vLLM/llama.cpp and
quality changed even though we used the same sampling config" reports.
```

The practical rule: **temperature should be applied before truncation** (it changes *which* tokens are even candidates), and **min-p should be computed on the post-temperature distribution** (since its threshold is relative to the post-temperature max). Always check your serving engine's documented order when migrating, and re-run your eval suite — don't assume "same numbers" means "same behavior."

### 6.4 Repetition, presence, frequency penalties, no-repeat-ngram, and DRY

```python
from collections import Counter


def repetition_penalty(logits, generated_ids, penalty: float = 1.15):
    """CTRL-style: for tokens already in the generated sequence,
    divide positive logits / multiply negative logits by `penalty`
    (penalty > 1 discourages repeats). Applied ONCE per unique token
    regardless of how many times it occurred."""
    for tid in set(generated_ids):
        if logits[tid] > 0:
            logits[tid] /= penalty
        else:
            logits[tid] *= penalty
    return logits


def presence_penalty(logits, generated_ids, penalty: float = 0.6):
    """OpenAI-style: subtract a FIXED amount from any token that has
    appeared >= 1 time. Binary -- doesn't scale with repeat count."""
    for tid in set(generated_ids):
        logits[tid] -= penalty
    return logits


def frequency_penalty(logits, generated_ids, penalty: float = 0.6):
    """OpenAI-style: subtract an amount PROPORTIONAL to occurrence
    count. A token used 5 times is penalized 5x more than one used
    once -- stronger cumulative anti-repetition than presence penalty."""
    counts = Counter(generated_ids)
    for tid, count in counts.items():
        logits[tid] -= penalty * count
    return logits


def no_repeat_ngram_mask(logits, generated_ids, n: int = 3):
    """Hard ban: if generating token x would recreate an n-gram that
    already exists in generated_ids, set logits[x] = -inf.
    FAILS on legitimate repetition: code with repeated boilerplate,
    lists with repeated structure, or any text where the SAME n-gram
    legitimately recurs (e.g. "the the" is rare, but "def __init__(self"
    appearing 3 times in generated code is normal and gets banned)."""
    if len(generated_ids) < n - 1:
        return logits
    prefix = tuple(generated_ids[-(n - 1):])
    seen_continuations = {
        tuple(generated_ids[i:i + n])[-1]
        for i in range(len(generated_ids) - n + 1)
        if tuple(generated_ids[i:i + n - 1]) == prefix
    }
    for tid in seen_continuations:
        logits[tid] = float("-inf")
    return logits


def dry_penalty(logits, generated_ids, base: float = 1.75, allowed_length: int = 2):
    """DRY (Don't Repeat Yourself): if the token x, appended to the
    sequence, would create a suffix that EXACTLY matches a substring
    seen earlier (length >= allowed_length), apply a penalty that
    grows EXPONENTIALLY with the length of the repeated run:
        penalty(x) = base ^ (repeat_length - allowed_length)
    Unlike no-repeat-ngram (hard ban at fixed n), DRY only fires on
    genuinely LONG verbatim repeats and scales severity with length --
    a 2-token coincidental repeat is barely penalized; an 8-token
    verbatim loop is penalized severely. Targets the specific failure
    mode of models getting stuck regenerating an entire sentence.
    """
    # Simplified: real implementations use a suffix-automaton / Z-array
    # over generated_ids for O(n) repeat-length lookup per candidate.
    ...
```

**Why DRY exists despite repetition/frequency penalties already existing**: those penalties operate per-*token*, uniformly. A model stuck in a loop repeating an entire 12-token sentence has each individual token only modestly penalized (each token also appears in normal text) — the *loop* isn't punished, only common words are. DRY detects the *structural* repetition (an exact long substring recurrence) and penalizes it specifically, regardless of whether the individual tokens are common.

### 6.5 Contrastive search and contrastive decoding

**Contrastive search** (single model) re-scores top-k candidates by combining model confidence with a *degeneration penalty* computed from hidden states:

```
score(x) = (1 - alpha) * p_theta(x | context)
           - alpha * max_{j < t} cos_sim(h_x, h_j)

where h_x is the hidden state the model WOULD have if x were chosen,
and h_j ranges over hidden states of all previously generated tokens.

alpha typically 0.6; top-k typically 4-8 (small -- this re-scores a
short candidate list, it doesn't replace a wide sampler).
```

This catches *semantic* repetition — a token that's superficially different from anything said before but whose representation is nearly identical to recent context (e.g., paraphrasing the same sentence with synonyms), which surface-level penalties (Section 6.4) cannot detect since they key on token IDs.

**Contrastive decoding** (two models) instead amplifies the *difference* between a strong "expert" and a weak "amateur" (a smaller model, or the same model given less context):

```
score(x) = log p_expert(x | context) - alpha * log p_amateur(x | context)
```

Tokens that BOTH models find likely (generic, high-frequency continuations — "the", "and", common phrases) get suppressed because the amateur also assigns them high probability; tokens the expert specifically favors get amplified. This is the same family of idea as classifier-free guidance in diffusion models, applied to language model logits — and it's a useful interview connection to draw.

### 6.6 Typical, eta, epsilon, tail-free, Mirostat, and XTC

```
Typical (locally-typical) sampling:
  H = -sum_x p(x) * log p(x)            # entropy of the distribution
  For each token x: deviation(x) = | -log p(x) - H |
  Keep tokens with smallest deviation, up to cumulative mass tau.
  -> Targets tokens near the "expected surprise" -- excludes both the
     MOST predictable token (boring, can cause loops) and the LEAST
     predictable tokens (incoherent), even if top-p would keep both.

Eta sampling:
  eta = min(epsilon, sqrt(epsilon) * exp(-H))
  Keep tokens with p(x) > eta.
  -> An entropy-SCALED absolute floor: the floor tightens automatically
     when the distribution is low-entropy (peaked).

Epsilon sampling:
  Keep tokens with p(x) > epsilon  (a FIXED absolute floor, e.g. 0.0003)
  -> Simplest tail-cutting; doesn't adapt to distribution shape at all,
     often combined with top-p as a final safety net.

Tail-free sampling (TFS):
  Look at the second derivative of the SORTED probability curve --
  the point where the curve's curvature flattens marks where the
  "real" distribution ends and the "tail" begins. Cut there.
  -> A precursor to min-p's goal, computed via curvature instead of
     a fixed relative threshold; rarely used today.

Mirostat (v1/v2):
  Maintains a target "surprise" value tau (≈ desired perplexity).
  At each step: sample from the current top-k, observe the actual
  surprise (-log p) of the sampled token, compute the ERROR vs tau,
  and adjust k for the NEXT step via a feedback control update:
     k_{t+1} = k_t - learning_rate * (observed_surprise_t - tau)
  -> Unlike static top-k/top-p, Mirostat is DYNAMIC over the sequence:
     if generation starts drifting toward repetition (surprise too
     low), k automatically grows; if it drifts toward incoherence
     (surprise too high), k shrinks. Popular for long-form creative
     generation where static cutoffs drift over thousands of tokens.

XTC (Exclude Top Choices):
  With probability p_xtc, REMOVE all tokens with probability above a
  threshold (except the single most-probable token is sometimes
  exempted to preserve coherence on "obvious" continuations) BEFORE
  the rest of the sampler runs.
  -> Counter-intuitive: instead of cutting the TAIL (every other
     method here), XTC occasionally cuts the HEAD -- forcing the
     model to choose among its "second-best" continuations. Designed
     specifically to counter "the model always picks the same safe
     phrasing" in creative-writing communities (llama.cpp ecosystem).
```

### 6.7 Beam search — mechanics, cost, and why it's rare in production

Beam search maintains `k` candidate sequences ("beams"). At each step, every beam is expanded by all `V` vocabulary tokens, scored by cumulative (often length-normalized) log-probability, and only the global top-`k` survive:

```
score(sequence) = (1/len^alpha) * sum_t log p(token_t | token_<t)
                  (length normalization alpha ~0.6-1.0 prevents
                   beam search from always preferring shorter
                   sequences, which have fewer negative log-probs
                   to sum)
```

**Cost**: `k×` the KV cache of a single sequence (each beam maintains its own cache) and `k×` the compute per step. For `k=5`, that's 5× the memory footprint of greedy/sampling for the same request.

**Why rare in production serving**: (1) memory — incompatible with tight KV cache budgets at scale; (2) **incompatible with continuous batching** — all `k` beams for a request must be tracked and typically must finish together, holding a scheduling slot until the *longest* beam completes, defeating the iteration-level scheduling that gives continuous batching its throughput gains (see [vLLM Deep Dive](../vllm_deep_dive/README.md)); (3) quality — for *open-ended* generation, beam search empirically produces more generic, repetitive text than sampling, because maximizing joint probability favors safe, high-confidence-at-every-step phrasing over the more varied paths a human writer would take. It remains the right tool where maximizing sequence likelihood genuinely *is* the objective: machine translation, speech-to-text transcription, and some constrained/structured generation tasks.

### 6.8 Determinism, seeds, and why "temperature=0" isn't always reproducible

```
Two distinct sources of non-determinism in LLM sampling:

1. SAMPLING non-determinism (T > 0): controlled by an RNG seed.
   Same seed + same model + same hardware + same batch composition
   -> same sampled token, in principle.

2. FLOATING-POINT / BATCHING non-determinism (affects even T=0):
   GPU kernels (especially attention and matmul reductions) sum
   floating-point numbers in an order that depends on the BATCH SIZE
   and which OTHER REQUESTS are co-batched with yours. Different
   summation order -> different rounding -> the logit values for the
   top-1 and top-2 tokens, if very close, can FLIP which one is
   technically larger.

Practical consequence: an API call at temperature=0 with a fixed seed
is NOT guaranteed bit-identical across two calls if the SERVER-SIDE
BATCH COMPOSITION differs between calls -- which it almost always does
under continuous batching with other tenants' traffic. This is the
correct answer to "why doesn't my reproducible eval reproduce exactly
against a production API," and it's a batching/infrastructure issue,
not a sampler issue.
```

### 6.9 Interplay with constrained decoding and speculative decoding

**Constrained decoding**: the grammar mask (Section 5.1, step 1) sets disallowed-token logits to `-inf` and **must run before any truncation step**. If top-p or min-p ran first on the *unmasked* distribution, the surviving nucleus could consist entirely of grammar-invalid tokens — masking them afterward would leave an empty candidate set and the sampler would fail or fall back to an arbitrary token. See [constrained_decoding_and_structured_outputs.md §6](constrained_decoding_and_structured_outputs.md) for the full masking architecture.

**Speculative decoding**: the rejection-sampling correctness proof in [speculative_decoding.md §6.1](speculative_decoding.md) assumes the draft and target models' probabilities `q(x)` and `p(x)` are computed *under the same sampler configuration* — same temperature, same penalties, same logit_bias. If the draft samples at a different temperature or with different penalty state (which diverges after the first rejection in a multi-round generation), the comparison `p(x)/q(x)` is no longer comparing the "same" distribution modulo model quality, and the exactness guarantee degrades. This is the single most common cause of "speculative decoding made output quality worse" reports.

---

## 7. Real-World Examples

- **OpenAI API** — `temperature` (0-2), `top_p`, `presence_penalty` and `frequency_penalty` (-2.0 to 2.0), `logit_bias` (token-id → -100..100 offset map), `seed` (best-effort reproducibility, with the batching caveat from Section 6.8).
- **Anthropic API** — `temperature` (0-1), `top_p`, `top_k`; deliberately a smaller surface than OpenAI's, reflecting a preference for fewer, well-understood knobs.
- **llama.cpp** — the most feature-complete open sampler chain: `top_k`, `top_p`, `min_p`, `typical_p`, `tfs_z` (tail-free), `mirostat` (v1/v2 with `mirostat_tau`/`mirostat_eta`), DRY (`dry_multiplier`, `dry_base`, `dry_allowed_length`), and XTC (`xtc_probability`, `xtc_threshold`) — and an explicit, documented sampler *order* configuration (`--samplers` flag lists the chain order).
- **vLLM `SamplingParams`** — `temperature`, `top_p`, `top_k`, `min_p`, `repetition_penalty`, `presence_penalty`, `frequency_penalty`, `seed`, plus `guided_*` parameters for constrained decoding (composed per Section 6.9).
- **HuggingFace `transformers` `GenerationConfig`** — the reference implementation; includes `penalty_alpha`/`top_k` for contrastive search, `typical_p`, `no_repeat_ngram_size`, and beam search (`num_beams`, `length_penalty`, `early_stopping`).

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Truncation method | Top-p (cumulative mass) | Min-p (relative to mode) | Min-p is more robust across both peaked and flat distributions; top-p remains the most widely supported default |
| Anti-repetition | Frequency/presence penalty (per-token, cheap) | DRY (per-sequence, catches verbatim loops) | Does your failure mode look like "overused words" or "looping sentences"? |
| Anti-loop hard constraint | No-repeat-ngram (hard ban) | DRY / penalties (soft, scaled) | Hard bans can break legitimate repetition (code, lists) |
| Output objective | Sampling (diverse draws) | Beam search (max joint probability) | Open-ended generation vs. translation/ASR/likelihood-maximizing tasks |
| Determinism | Temperature=0 (greedy) | Temperature>0 + fixed seed | T=0 is closer to reproducible but still subject to batching nondeterminism (Section 6.8) |
| Long-form stability | Static top-p/min-p | Mirostat (dynamic feedback) | Does generation length exceed a few hundred tokens where drift matters? |
| Repetition reduction depth | Surface (token-ID penalties) | Representation-level (contrastive search) | Catching paraphrased/semantic repetition vs. verbatim |

---

## 9. When to Use / When NOT to Use

**Use greedy (T=0) when:**
- Factual Q&A, code generation, structured/JSON extraction, classification — anywhere a single "correct" answer exists.
- Reproducibility across evaluation runs matters (with the batching caveat from Section 6.8 acknowledged).

**Use temperature + top-p/min-p sampling when:**
- Creative writing, brainstorming, conversational chat — diversity across responses has value and no single "best" answer exists.
- min-p over top-p when your traffic spans both highly-constrained prompts (peaked distributions) and open-ended prompts (flat distributions) with one global config.

**Use DRY / contrastive search / Mirostat when:**
- Long-form generation (model writing, multi-page documents, extended agent transcripts) where static cutoffs drift and verbatim loops are a recurring complaint.

**Use beam search when:**
- Translation, ASR transcription, or any task where the evaluation metric directly rewards sequence likelihood (BLEU-style) — and you can afford k× memory and accept incompatibility with continuous batching.

**Do NOT layer many exotic samplers by default:**
- Each additional sampler is another parameter to tune, eval, and explain when something goes wrong. Start with temperature + min-p (or top-p) + a mild frequency penalty; add DRY/Mirostat/contrastive methods only when a *specific, measured* failure mode (loops, drift) justifies the complexity.

---

## 10. Common Pitfalls

1. **Cross-engine migration with "the same" sampler params produces different outputs.** Different default sampler *orders* (Section 6.3) mean identical `temperature`/`top_p` values select different candidate sets. Always re-run your eval suite after switching inference engines, even with unchanged config.
2. **`no_repeat_ngram_size` bans legitimate repeated structure.** Generated code with repeated boilerplate (`def __init__(self`), lists with parallel structure, or any domain where exact phrase recurrence is normal will hit hard bans, producing forced and awkward rewordings. Prefer DRY (length-scaled, exponential) over a hard n-gram ban for general-purpose chat.
3. **Truncation before temperature narrows the nucleus permanently.** If top-p/min-p run on the *pre-temperature* distribution, increasing temperature afterward cannot "rescue" tokens already excluded — temperature appears to have no effect on diversity. Verify your engine applies temperature first (Section 6.3).
4. **Grammar mask applied after truncation can produce an empty candidate set.** If top-p/min-p already excluded every grammar-valid token, masking afterward leaves nothing to sample — engines must apply the grammar mask *first* (Section 6.9, and [constrained_decoding_and_structured_outputs.md](constrained_decoding_and_structured_outputs.md)).
5. **Mismatched draft/target sampler config silently degrades speculative decoding.** Temperature or penalty-state divergence between draft and target breaks the rejection-sampling exactness proof — see [speculative_decoding.md §6.9](speculative_decoding.md).
6. **Expecting `seed` to give bit-exact reproducibility against a production API.** Batch-composition-dependent floating-point rounding (Section 6.8) means even `temperature=0` + fixed `seed` can occasionally diverge across calls — this is infrastructure, not a sampler bug, and eval harnesses should tolerate small divergence rather than assert exact string equality.
7. **min_p set too low (e.g., 0.01) on already-low-entropy outputs effectively becomes top-1**, silently removing all diversity — tune relative to your *typical* distribution shape, not in isolation.
8. **Mirostat's feedback loop can oscillate on short generations.** Its control loop needs several tokens to converge toward the target perplexity; on very short outputs (a few tokens), behavior is closer to its initial `k` than to its steady-state target — don't expect Mirostat's benefits on single-sentence completions.

---

## 11. Technologies & Tools

| Tool | Notes |
|------|-------|
| llama.cpp | Most complete open sampler chain — min-p, typical, TFS, Mirostat v1/v2, DRY, XTC, with configurable order |
| vLLM `SamplingParams` | Production-grade: temperature, top_p, top_k, min_p, repetition/presence/frequency penalties, seed |
| HuggingFace `transformers` `GenerationConfig` | Reference implementation; contrastive search (`penalty_alpha`), typical_p, beam search |
| OpenAI API | temperature, top_p, presence/frequency penalty, logit_bias, best-effort seed |
| Anthropic API | temperature, top_p, top_k — minimal, well-understood surface |
| SGLang | Sampling params compatible with OpenAI-style API plus structured-output integration |

---

## 12. Interview Questions with Answers

**Q1: Why is temperature=0 special-cased instead of just dividing logits by zero?**
Dividing by zero is mathematically undefined, so temperature=0 is implemented as a direct `argmax` over the logits — equivalent to the *limit* of softmax(logits/T) as T→0+, which collapses to a one-hot distribution on the highest-logit token. It is the only sampling mode that is fully deterministic *given identical floating-point execution*, which is why it's the default for factual Q&A, code generation, and reproducible evaluation — though true bit-exact reproducibility in production APIs still depends on batch-composition-invariant kernels (Section 6.8).

**Q2: Compare top-k, top-p, and min-p. Which performs best in practice and why?**
Top-k keeps a fixed count of tokens regardless of the actual distribution shape — it wastes its budget on near-zero-probability tail tokens when the distribution is peaked (e.g., k=50 after "capital of France is", where only "Paris" is plausible) and can cut off valid options when the distribution is flat (k=50 when 100 tokens are genuinely plausible). Top-p adapts to cumulative probability mass, which handles the peaked case well, but on a flat distribution it can still admit individually low-probability tokens right at its cumulative cutoff boundary. Min-p sets its threshold *relative to the most likely token's probability* (`prob >= min_p * max_prob`), so it naturally tightens on peaked distributions and widens on flat ones using the SAME parameter value. In practice min-p (often ~0.05) tends to outperform top-p on creative tasks and is increasingly the default in llama.cpp-based stacks, while top-p (~0.9) remains the most widely supported production default across APIs.

**Q3: Walk through the sampler-ordering gotcha — why can the same temperature and top_p values produce different outputs on two different inference engines?**
The sampler is a *chain* of operations, and each step operates on the output of the previous one. If temperature is applied *before* top-p, it reshapes the distribution (flattening or sharpening it) and top-p's nucleus is computed on that reshaped distribution — temperature can widen or narrow which tokens survive. If top-p runs *first* on the raw distribution and temperature is applied *after*, temperature can only redistribute probability mass *among the tokens top-p already selected* — it can never bring back a token top-p excluded, so increasing temperature appears to have little effect on output diversity. Different engines historically chose different default orders, so identical numeric configs ported across engines can produce measurably different candidate sets and outputs — always re-run evals after an engine migration, don't assume parameter parity implies behavioral parity.

**Q4: What's the difference between repetition penalty, presence penalty, and frequency penalty?**
Repetition penalty (CTRL-style) divides positive logits / multiplies negative logits of any token already seen by a fixed factor (>1 discourages repeats), applied once per unique token regardless of count. Presence penalty (OpenAI-style) subtracts a fixed amount from the logit of any token that has appeared at least once — binary, doesn't scale with repeat count. Frequency penalty subtracts an amount *proportional to occurrence count* — a token used 5 times is penalized 5× more than one used once, giving it the strongest cumulative anti-repetition effect of the three. In practice, frequency penalty is the right tool when heavy reuse of specific words is the complaint; presence penalty is gentler and won't over-penalize a naturally-recurring term used twice.

**Q5: Why does `no_repeat_ngram_size` sometimes make output worse rather than better?**
It's a *hard ban*: if generating a token would recreate an n-gram seen earlier in the output, that token's logit is set to -inf, period — regardless of whether the repetition is a degenerate loop or entirely legitimate (repeated code patterns like `def __init__(self`, parallel list structure, a technical term that legitimately recurs). When the legitimate continuation is banned, the model is forced into its next-best (often awkward) alternative, sometimes cascading into worse output than the "repetition" would have been. DRY is the more targeted alternative — it penalizes based on the *length* of the repeated run, so short coincidental repeats are barely touched while long verbatim loops (the actual degeneration failure mode) are heavily penalized.

**Q6: What problem does the DRY sampler solve that frequency penalty doesn't?**
Frequency penalty operates per-token: a model stuck in a loop repeating an entire 12-token sentence has each *individual* token (mostly common words) only modestly penalized, because those same words appear throughout normal text too — the *structural loop* itself isn't punished. DRY instead detects when generating the next token would recreate a long exact substring already present in the output, and applies a penalty that grows *exponentially* with the length of that repeated run. A 2-token coincidental match is nearly unpenalized; an 8-token verbatim repeat is penalized severely. It targets the loop as a structural pattern, not the words composing it.

**Q7: Explain contrastive search and what failure mode it catches that token-level penalties miss.**
Contrastive search re-scores a small top-k candidate list using `score(x) = (1-α)·p(x) − α·max_cos_sim(hidden_state(x), hidden_states(history))` — it penalizes tokens whose *hidden-state representation* is highly similar to recently-generated tokens' representations, not just tokens with the same token ID. This catches *semantic* repetition: a model rephrasing the same idea with different words (no repeated token IDs, so frequency/presence penalties see nothing) but producing a nearly-identical hidden-state trajectory, which contrastive search detects and suppresses. It operates at a deeper representational level than any surface token-history method.

**Q8: How does contrastive decoding differ from contrastive search, and why are they often confused?**
Both have "contrastive" in the name and both aim to reduce generic/repetitive output, but they operate on entirely different signals. Contrastive search uses ONE model and contrasts a candidate token's *probability* against the *hidden-state similarity* of that token to recent history. Contrastive decoding uses TWO models — a strong "expert" and a weaker "amateur" (smaller model or same model with less context) — and amplifies `log p_expert(x) - α·log p_amateur(x)`, suppressing tokens both models find equally likely (generic continuations) and amplifying tokens the expert specifically prefers. The naming collision is unfortunate but the mechanisms are unrelated; contrastive decoding is conceptually closer to classifier-free guidance in diffusion models than to contrastive search.

**Q9: What is Mirostat and why would you choose it over static top-p for long-form generation?**
Mirostat is a feedback-controlled sampler that targets a constant "surprise" value (≈ desired perplexity) over the course of generation. At each step it samples from a top-k window, measures the actual surprise of the sampled token, computes the error against the target, and adjusts `k` for the next step via a control-loop update — if generation drifts toward repetition (surprise too low), `k` grows to inject more diversity; if it drifts toward incoherence (surprise too high), `k` shrinks. Static top-p/min-p apply the *same* cutoff rule at token 10 and token 2,000, but a model's "comfortable" distribution shape can drift over a long generation — Mirostat's dynamic adjustment is specifically designed to counteract that drift, making it popular for multi-page creative generation where static cutoffs tend to degrade into either loops or rambling over length.

**Q10: What is XTC and why is "removing the most likely tokens" a sensible sampling strategy?**
XTC (Exclude Top Choices) probabilistically removes tokens *above* a probability threshold before the rest of the sampler runs — the opposite of every other method in this file, which cut the *tail*. The motivation: models often have a strong, "safe" default continuation (the most probable token) that is technically correct but generic — and if it's always chosen, output becomes formulaic ("the model always says the same thing"). By occasionally removing that top choice (with some probability, often exempting only the single best token to preserve coherence on genuinely unambiguous continuations), the model is forced to articulate its *second-best* idea, which is often more interesting without being incoherent — XTC never touches the incoherent tail, only the over-represented head.

**Q11: Why is beam search rarely used in production LLM serving despite guaranteeing higher sequence probability than greedy decoding?**
Three reasons. First, memory: beam width `k` requires `k×` the KV cache of a single sequence — at `k=5`, 5× the memory footprint per request. Second, scheduling: beam search is incompatible with continuous batching's iteration-level scheduling, because all `k` beams for a request typically must complete together, holding a slot until the longest beam finishes — exactly the head-of-line problem continuous batching exists to solve. Third, and most subtly, quality: for *open-ended* generation, maximizing joint sequence probability empirically produces more generic, repetitive, "safe-at-every-step" text than sampling — beam search is the right objective for translation/ASR (where the evaluation metric directly rewards likelihood) but the wrong objective for creative or conversational generation.

**Q12: Why can't you get bit-exact reproducible outputs from a production LLM API even at temperature=0 with a fixed seed?**
Temperature=0 is deterministic *given identical floating-point execution* — but GPU kernels (especially attention and matmul reductions) sum floating-point values in an order that depends on batch size and which other requests happen to be co-batched with yours under continuous batching. Different summation orders produce different rounding, and if the top-1 and top-2 logits are extremely close, rounding differences can flip which one is technically larger — changing the argmax. Since production batch composition varies request-to-request based on concurrent traffic, this is a batching/infrastructure non-determinism, not a sampler configuration issue, and eval harnesses comparing production outputs should tolerate small divergence rather than assert exact string equality.

**Q13: How does logit_bias interact with a grammar mask in constrained decoding?**
A grammar mask sets disallowed-token logits to `-inf` to guarantee syntactic validity (see [constrained_decoding_and_structured_outputs.md](constrained_decoding_and_structured_outputs.md)); `logit_bias` adds a bounded offset (e.g., -100 to +100 in the OpenAI API) to specific token logits to encourage or discourage them. Since `-inf + anything = -inf`, the grammar mask always dominates — you cannot use `logit_bias` to "force" a grammar-invalid token back into consideration. The two compose safely as long as the grammar mask is applied at the point in the pipeline where it can't be bypassed by a later truncation step admitting only masked tokens (Section 6.9) — order matters here just as it does for temperature/top-p.

**Q14: Why must speculative decoding's draft and target models use identical sampler configuration?**
The rejection-sampling correctness proof compares `p_target(x)` and `q_draft(x)` for the same token under the assumption that both are probabilities from comparable sampling configurations — if the draft samples at temperature=0 (greedy, sharply peaked) while the target samples at temperature=1.0 (broad), the draft's proposals are systematically biased toward the target's mode and the comparison `p/q` no longer reflects "how much does the target agree with the draft," producing a collapsed acceptance rate. The same applies to penalty state: repetition/frequency penalties depend on generation history, and after the first token rejection the draft's and target's histories diverge, meaning their effective distributions are no longer the "same modulo model quality" — see [speculative_decoding.md §6.9](speculative_decoding.md) for the production fallout.

**Q15: A team complains their chat model "feels robotic and repetitive" after months of using temperature=0.7, top_p=1.0 as defaults. What would you investigate and change?**
First, `top_p=1.0` means no nucleus truncation is happening at all — the model is sampling from its *entire* vocabulary at T=0.7, including low-probability tail tokens that can occasionally produce incoherent outputs, while simultaneously NOT addressing the "robotic" complaint, which usually comes from the model defaulting to high-confidence generic phrasing. I'd introduce min-p≈0.05 (cuts true noise without the top-p flat-distribution issue) and a modest frequency penalty (~0.3-0.5) to discourage the model's tendency to reuse the same transitional phrases ("I understand that...", "It's important to note..."). If verbatim sentence-level loops appear in long conversations specifically, add DRY rather than increasing the frequency penalty further (which would start penalizing common words too broadly). Validate every change against an eval harness measuring both repetition rate AND task accuracy — anti-repetition tuning that's too aggressive trades looping for incoherence, which is a worse user experience.

**Q16: What's the relationship between typical sampling and entropy, and what failure mode does it specifically target?**
Typical sampling computes the distribution's entropy `H = -Σ p(x) log p(x)` (the "expected surprise") and keeps tokens whose own surprise `-log p(x)` is *closest* to `H`, up to a cumulative mass budget — rather than simply keeping the highest-probability tokens (top-p/top-k). This deliberately *excludes* the single most-probable token if it's "too predictable" relative to the distribution's overall entropy, on the theory that always picking the most-obvious-by-far token is what produces dull, loop-prone text, while tokens near the entropy "match" the level of information a human author would typically convey at that point. It's targeting the same "the model always picks the safe answer" problem XTC addresses, via an information-theoretic rather than probabilistic-head-trimming approach.

**Q17: How would you choose sampler defaults for a multi-tenant API serving both code-generation and creative-writing customers from the same model?**
Per-route defaults, not one global config: code-generation routes should default near temperature=0-0.2 with min-p tight (or top-p≈0.95) — correctness matters more than diversity, and repetition in code (boilerplate) is often *legitimate*, so avoid DRY/no-repeat-ngram on these routes. Creative-writing routes benefit from temperature≈0.8-1.0, min-p≈0.05, a modest frequency penalty, and optionally DRY for long-form generations where verbatim loops are the dominant complaint. The key engineering point is that sampler config should be a per-route (or per-request) parameter exposed through your API layer, validated by route-specific eval sets (see [llm_eval_harness_in_production.md](../case_studies/cross_cutting/llm_eval_harness_in_production.md)) — a single global default optimized for one workload will measurably hurt the other.

---

## 13. Best Practices

1. **Start simple**: temperature + min-p (or top-p) + a mild frequency penalty covers most chat/creative needs. Add DRY, Mirostat, or contrastive methods only for a *measured* failure mode.
2. **Set per-route/per-task defaults**, not one global config — code, extraction, and creative-writing routes have different correct answers for "how diverse should sampling be."
3. **Apply temperature before truncation, and the grammar mask before all truncation** (Section 6.3, 6.9) — verify your engine's documented order, don't assume.
4. **Re-run evals after any inference-engine migration**, even with "identical" sampler parameters — sampler order differences are silent.
5. **Prefer DRY over `no_repeat_ngram_size`** for general chat — hard n-gram bans break legitimate repeated structure (code, lists).
6. **Use temperature=0 for anything feeding a parser or business logic** — but don't assert bit-exact reproducibility against a production API; batch-composition nondeterminism is real (Section 6.8).
7. **Match sampler configuration between speculative decoding's draft and target models** — see [speculative_decoding.md](speculative_decoding.md).
8. **Avoid beam search in interactive serving** — k× KV cache and incompatibility with continuous batching make it a poor fit outside translation/ASR-style offline batch jobs.
9. **Track repetition rate and "feels robotic" complaints as eval metrics**, not just task accuracy — sampler tuning is the cheapest lever for both, but only if you can measure the regression.

---

## 14. Case Study

**Scenario**: A consumer chat product (Qwen2.5-32B, self-hosted on vLLM) running at `temperature=0.7, top_p=1.0` (no truncation) receives two persistent complaints from the product team: (1) "the bot feels repetitive in long conversations" — users report the same transitional phrases ("I understand", "Let's break this down") appearing every few turns, and occasionally entire sentences repeating verbatim after 20+ turns; (2) "the bot sometimes goes off the rails" — rare but visible incoherent tangents, traced to `top_p=1.0` occasionally sampling from the extreme tail at `temperature=0.7`.

**v1 measurement**: An eval harness sampled 2,000 production conversations and measured: repetition rate (fraction of responses containing a ≥6-token substring repeated from the same response or the prior 3 turns) = 4.1%; "incoherent tangent" rate (flagged by an LLM-judge for topic drift + nonsensical phrasing) = 0.7%; baseline user satisfaction (thumbs-up rate) = 71.2%.

**v2 design**:
1. Replaced `top_p=1.0` (no-op) with `min_p=0.05` — addresses the tail-sampling incoherence directly, with minimal effect on the "obvious continuation" cases where the distribution is already peaked (min-p barely trims those).
2. Added `frequency_penalty=0.3` — targets the "overused transitional phrase" complaint (these phrases recur across many turns, so frequency-based penalty accumulates appropriately) without the over-broad effect a higher repetition_penalty would have on common words needed for grammatical text.
3. Added DRY (`dry_multiplier=0.8, dry_base=1.75, dry_allowed_length=3`) specifically for the verbatim-sentence-repeat failure mode in 20+-turn conversations — exponential penalty only engages on genuinely long repeated runs, leaving short natural repetition (e.g., a user's name, a recurring topic noun) untouched.
4. Left `temperature=0.7` unchanged — the team's A/B testing showed temperature itself wasn't the driver of either complaint; the truncation and penalty configuration was.
5. Did NOT add Mirostat or contrastive search — both complaints were addressed by the above, and the team's policy (Best Practice #1) is to avoid adding samplers beyond what a measured failure mode justifies.

**Outcome**: repetition rate 4.1% → 0.6% (verbatim loops, the DRY-targeted failure, dropped from 1.8% to 0.1%; transitional-phrase repetition from 2.3% to 0.5%); incoherent tangent rate 0.7% → 0.2% (min-p removing extreme-tail sampling); user satisfaction 71.2% → 76.8%. Latency impact: unmeasurable — all sampler operations are CPU-side, O(vocab_size) per step, and add microseconds compared to the ~20ms/token GPU decode cost. The team's retrospective note: the *entire* improvement came from a config change deployed in an afternoon with zero GPU cost, vs. an earlier (shelved) proposal to fine-tune against a "don't repeat yourself" preference dataset — sampler tuning should be the first lever tried for this class of complaint, not the last.

---

## Related

- [Inference & Decoding README](README.md) — the broader serving picture: KV cache, batching, speculative decoding
- [Speculative Decoding](speculative_decoding.md) — why draft and target models must share sampler configuration
- [Constrained Decoding & Structured Outputs](constrained_decoding_and_structured_outputs.md) — how grammar masks compose with the sampler pipeline
- [LLM Eval Harness in Production](../case_studies/cross_cutting/llm_eval_harness_in_production.md) — measuring repetition rate and quality regressions from sampler changes
- [vLLM Deep Dive](../vllm_deep_dive/README.md) — continuous batching and why beam search doesn't fit it
