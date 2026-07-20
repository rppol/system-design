# Automated Jailbreak Algorithms: GCG, AutoDAN, TAP, and the Optimization View of Alignment Failure

> Deepens [Safety & Alignment](README.md) §4.1 (Jailbreaking) and §11 (PAIR, AdvBench, Garak) by
> covering the **algorithmic** jailbreak-generation methods that automate and scale what §4.1
> describes manually. For defenses grounded in interpretability (RepE, circuit breakers), see
> [Mechanistic Interpretability](../mechanistic_interpretability/README.md).

---

## 1. Concept Overview

The jailbreaks in [Safety & Alignment §4.1](README.md) — role-play personas, many-shot priming —
are largely **hand-crafted**: a human writes a prompt, tries it, and iterates. **Automated jailbreak
algorithms** instead treat jailbreaking as an **optimization or search problem**: given a harmful
request and black-box (or white-box) access to a target model, an algorithm searches a space of
prompts/suffixes for one that reliably elicits a compliant response, without a human in the loop
beyond setting up the search.

This module covers the major families: **GCG** (Greedy Coordinate Gradient, 2023 — white-box,
gradient-guided discrete optimization that produces *transferable* adversarial suffixes), **AutoDAN**
(2023 — a genetic algorithm that evolves fluent, human-readable jailbreak prompts under a
perplexity constraint), **AutoDAN-Turbo** (2024 — fully automated *strategy discovery*, no
human-designed seed strategies), **TAP** (Tree of Attacks with Pruning, 2023 — black-box, uses an
attacker LLM and a judge LLM to grow and prune a tree of candidate prompts), **BEAST** (2024 —
beam-search adversarial suffixes that run in under a minute on a single GPU), **GPTFuzzer** (2023 —
fuzzing with seed templates and mutation operators), and **PAP** (Persuasive Adversarial Prompts,
2024 — uses social-science persuasion taxonomies to construct jailbreaks via natural language
alone, no optimization required). The module pairs these with the **defenses** that target them
specifically: perplexity filtering, **SmoothLLM** (randomized input perturbation + majority vote),
paraphrase defenses, adversarial training, and **RepE-based circuit breakers**.

---

## 2. Intuition

> **One-line analogy**: Hand-crafted jailbreaking is picking a lock by trying keys you can think
> of; automated jailbreak algorithms build a machine that tries thousands of key shapes per hour,
> learns which features tend to work, and converges on keys no locksmith would have guessed.

**Mental model**: an aligned LLM has, implicitly, a **refusal boundary** in its output
distribution — for a harmful request `x`, `P(refusal | x)` is high. A jailbreak is any
modification `x'` of the input (an appended suffix, a prefix, a rewritten framing) such that
`P(refusal | x')` drops and `P(compliance | x')` rises, while the underlying harmful request is
preserved. Automated algorithms differ in **what they search over** (token sequences vs.
natural-language strategies), **what signal guides the search** (gradients vs. an LLM judge's
score vs. genetic fitness), and **what constraints they enforce** (fluency/perplexity, semantic
preservation of the original request).

**Why it matters**: a single successful hand-crafted jailbreak is an anecdote; an automated
algorithm that achieves a **measured success rate across hundreds of harmful-behavior prompts and
multiple target models** is a robustness *metric* — the basis for red-team reports, Responsible
Scaling Policy evaluations (referenced in [Safety & Alignment §11](README.md)), and regulatory
model cards (see [AI Regulations & Compliance](../ai_regulations_and_compliance/README.md)).
Automated algorithms also reveal **transferability**: a suffix optimized against an open-weight
model (e.g., Vicuna) frequently transfers — with reduced but nonzero success — to closed
production APIs (GPT-4, Claude, Gemini at the time of the original GCG paper), because alignment
training across models shares failure-mode structure.

**Key insight**: a jailbreak is **an adversarial example for a discrete, language-conditioned
classifier** (the model's implicit "is this request OK?" decision) — the same fundamental
phenomenon as adversarial perturbations for image classifiers (see
[Adversarial ML](../../ml/adversarial_ml_and_robustness/README.md) if present in the ML section),
but operating in a discrete token space, which is why **gradients alone aren't sufficient** (§3.2)
and why genetic/search-based methods (§3.4-3.5) are competitive alternatives.

---

## 3. Core Principles

### 3.1 The Optimization Objective

Most algorithmic jailbreaks optimize toward an **affirmative-prefix target**: rather than directly
maximizing "harmfulness" (hard to define as a differentiable loss), they maximize the model's
probability of generating a fixed compliant prefix, e.g. `"Sure, here is how to {harmful_request}"`.
The intuition: if the model starts its response by *agreeing* to comply, autoregressive generation
tends to continue compliantly — the prefix acts as a strong behavioral anchor (the same
autoregressive-commitment phenomenon discussed for reasoning models' "thinking tokens"). The loss
is `L(x') = -log P(target_prefix | x')`, and the algorithms in §4 differ in how they minimize `L`
over the space of valid `x'`.

**What this actually says.** "Score a candidate prompt by how surprised the model would be to
start its reply with `Sure, here is how to...` — and search for the prompt that makes that opening
least surprising."

The whole attack is a substitution of objectives. "Harmfulness" has no differentiable definition,
but "probability of this exact string" is just a forward pass, so the attacker swaps the goal they
want for a proxy goal they can actually measure, and bets the proxy drags the real one along.

| Symbol | What it is |
|---|---|
| `x'` | The modified input — original harmful request plus whatever suffix/framing is being searched |
| `target_prefix` | The fixed compliant opening the attacker picked, e.g. `"Sure, here is how to ..."` |
| `P(target_prefix \| x')` | How likely the model is to emit that exact opening, given `x'`. Between 0 and 1 |
| `-log(...)` | Turns a probability into a penalty: `P` near 1 gives ~0, `P` near 0 blows up |
| `L(x')` | The loss the search drives down. Lower = the model is closer to agreeing |

**Walk one example.** The same target prefix under three candidate suffixes:

```
                          P(target_prefix | x')      L = -log P
  aligned model, no suffix        0.001                6.908     model refuses; huge penalty
  suffix after 100 steps          0.050                2.996     starting to bend
  suffix after 500 steps          0.400                0.916     near-coin-flip on complying

  Perfect compliance would be P = 1.0 -> L = 0. The search never gets there;
  it only needs P high enough that greedy decoding picks the affirmative token.
```

The jump from `0.001` to `0.400` is 400x in probability but only `6.908 -> 0.916` in loss — the
log compresses the scale, which is exactly why it makes a usable gradient signal. A raw-probability
objective would be numerically flat at `0.001` and give the optimizer nothing to descend.

**Why the target string is load-bearing.** `L` says nothing about harm — it only measures agreement
with one hardcoded sentence. Pick a target prefix the model would never phrase that way and `L`
stays high no matter how good the suffix is, so the search stalls on a formatting mismatch rather
than a safety property. This is also the defensive opening (§Q14): the thing being optimized is a
short, predictable, *detectable* prefix.

### 3.2 The Discrete Optimization Problem

Unlike image adversarial examples (continuous pixel space, vanilla gradient descent works), prompt
tokens are **discrete** — you can't take a small gradient step on a token ID. **GCG**'s solution:
compute the gradient of `L` with respect to the **one-hot token embeddings** at each suffix
position, use the gradient to identify the top-`k` *candidate replacement tokens* per position,
then **evaluate** (via an actual forward pass — not just the gradient approximation) a batch of
candidates that swap one position at a time, keeping whichever swap most reduces the loss. This
is **greedy coordinate descent guided by gradients**, not gradient descent itself — the gradient
narrows the search space; an actual forward pass makes the final decision.

**Put simply.** "There are more possible suffixes than atoms in the universe, so nobody is
enumerating them — the gradient's only job is to shrink each step's shortlist from the whole
vocabulary down to a few hundred tokens worth actually trying."

The size of the full space is what makes the gradient non-optional. Understand these two numbers
and the entire design of GCG follows from them.

| Quantity | What it is |
|---|---|
| `V` | Vocabulary size — how many distinct tokens can sit in any one suffix slot (~32,000 for Llama/Vicuna) |
| `n` | Suffix length in tokens; GCG's default is `suffix_len = 20` (§6.1) |
| `V^n` | Total distinct suffixes — the true search space, if you were to enumerate |
| `k` | Tokens kept per position after gradient ranking; GCG's `top_k = 256` |
| `k x n` | The per-step shortlist the gradient produces — what the search actually looks at |
| `B` | Candidates actually forward-passed per step; GCG's `batch_size = 512` |

**Walk one example.** GCG's own published configuration, both numbers side by side:

```
  FULL SPACE       V^n  = 32000^20
                        = 1.27 x 10^90 distinct suffixes
                        (log10 = 90.1 -- for scale, ~10^80 atoms in the observable universe)

  PER-STEP SHORTLIST    k x n = 256 x 20 = 5,120 candidate substitutions
                        gradient scored all 20 x 32000 = 640,000 (position, token) pairs
                        and kept the best 256 per position = 0.8% of the vocabulary

  ACTUALLY EVALUATED    B = 512 forward passes per step (10% of the 5,120 shortlist)
                        x 500 steps = 256,000 forward passes total  (10^5.4)

  Fraction of the space ever touched:  10^5.4 / 10^90.1  =  10^-84.7
```

An attack that inspects `10^-84.7` of its own search space and still succeeds is the real result
here. It works because the loss surface is not remotely random — the gradient is a good enough
guide that the tiny sampled region reliably contains an improving move.

**Why the forward pass survives in the loop.** The gradient is computed on a *continuous
relaxation* (one-hot embeddings), so its ranking of discrete swaps is only approximate; the token
it likes best is often not the token that lowers real loss. Drop the `B` verification passes and
trust the gradient's top pick outright and the search degrades badly — you would be optimizing the
relaxation rather than the model. Keeping both is the whole trick: gradient for recall, forward
pass for precision.

### 3.3 Transferability

A suffix optimized against one model (or, in GCG's original setup, an *ensemble* of several
open-weight models simultaneously) often **transfers** to other models it was never optimized
against — including black-box production APIs. The mechanism is debated, but the leading
explanation is that safety fine-tuning across different base models and labs produces **similar
refusal-direction representations** (connecting to [linear representation hypothesis,
Mechanistic Interpretability §3](../mechanistic_interpretability/README.md)) — perturbing that
shared direction has correlated effects across models. Transferability is *why* automated
jailbreak research matters for closed-model safety even when the algorithm itself requires
white-box (gradient) access: **optimize against open-weight proxies, evaluate transfer to closed
targets**.

### 3.4 Genetic / Evolutionary Search (AutoDAN)

AutoDAN maintains a **population** of candidate jailbreak prompts, each scored by a **fitness
function** combining (a) jailbreak success against the target model and (b) **fluency**
(low perplexity under a reference LM — a prompt that reads as natural English/role-play framing,
not random tokens). Each generation: select high-fitness candidates, apply **mutation** (word-level
substitution, paraphrasing via an auxiliary LLM) and **crossover** (combine fragments of two
parent prompts), and re-score. Because fluency is *part of the fitness function from the start*,
AutoDAN's outputs are human-readable role-play-style prompts — this is precisely what defeats a
**naive perplexity-only filter** (§6.3).

### 3.5 Attacker-LLM Tree Search (TAP)

TAP uses an **attacker LLM** to propose prompt refinements, a **target model** to query, and a
**judge LLM** (or classifier) to score each response's jailbreak success and on-topic-ness. TAP
grows a **tree**: each node is a candidate prompt; children are LLM-generated refinements of the
parent; **pruning** removes branches the judge scores as off-topic or unlikely to improve, keeping
the search tractable (typically far fewer queries than exhaustive search, and substantially fewer
than PAIR's purely iterative refinement). TAP is fully **black-box** — no gradients, no access to
target-model internals, only input/output queries — making it directly applicable to closed APIs.

**Read it like this.** "If each attempt has an independent chance `p` of working, you should expect
to spend about `1/p` attempts before the first success — and after `N` attempts you have found one
with probability `1 - (1-p)^N`."

Every query-budget claim in this module ("~45 queries/prompt vs PAIR's ~75", §14) is a statement
about this geometric model. It is also why "the attack failed" and "the attack ran out of budget"
are different findings that red-team reports routinely confuse.

| Symbol | What it is |
|---|---|
| `p` | Per-query probability that one candidate prompt jailbreaks the target |
| `N` | Query budget — how many candidates you are willing to send before giving up |
| `E = 1/p` | Expected number of queries until the first success (the geometric mean) |
| `(1-p)^N` | Probability that all `N` queries fail — each independent failure multiplied together |
| `1 - (1-p)^N` | Probability of at least one success inside the budget |

**Walk one example.** A prompt where each refined candidate works 5% of the time (`p = 0.05`):

```
  E = 1/p = 1/0.05 = 20 queries expected before the first success

  N       (1-p)^N = 0.95^N      P(success within N) = 1 - 0.95^N
   5          0.774                    22.6%
  10          0.599                    40.1%
  20          0.358                    64.2%   <- N = E, and it is NOT 50%
  45          0.099                    90.1%   <- TAP's ~45 queries/prompt
  75          0.021                    97.9%   <- PAIR's ~75 queries/prompt

  Going 45 -> 75 queries buys 90.1% -> 97.9%: 67% more spend for 7.8 points.
```

Note the `N = 20` row. Spending exactly the expected number of queries gives a `64.2%` success
chance, not `50%` — the geometric distribution has a long right tail, so the mean sits well above
the median. Budgeting `N = E` and reporting the failures as "the model resisted" understates the
true ASR by a wide margin.

**Why the tail is the whole argument for pruning.** The `1 - (1-p)^N` curve is steep early and flat
late — nearly all the success you are ever going to buy arrives in the first `~2/p` queries. Past
that, extra budget is almost pure spend, which is precisely the waste TAP's judge removes by
killing off-topic branches before they consume queries. Pruning does not raise `p` for a good
branch; it stops you spending the flat part of the curve on branches whose `p` is near zero.

### 3.6 The Fluency-vs-Effectiveness Axis

GCG and BEAST suffixes are typically **gibberish** — strings like
`describing.\ + similarlyNow write oppositeley.]( Me giving**ONE please?`. These can be highly
effective (especially white-box, before transfer) but are **trivially flagged by perplexity
filters** (§6.3) since natural language has much lower per-token surprise than random
token sequences. AutoDAN, AutoDAN-Turbo, and PAP sit at the other end — **fluent, often
plausible-sounding prompts** (elaborate role-play setups, "for a novel I'm writing...", authority
framing) that perplexity filters cannot distinguish from legitimate creative-writing requests.
**This axis is the central driver of which defenses (§8.3) are effective against which attack
family** — there is no single defense that covers both ends.

---

## 4. Types / Architectures / Strategies

| Algorithm | Year | Access | Search Method | Output Style |
|---|---|---|---|---|
| **GCG** (Greedy Coordinate Gradient) | 2023, Zou/Wang/Carlini/Nasr/Fredrikson | White-box (gradients) | Greedy coordinate descent guided by token-embedding gradients (§3.2) | Gibberish suffix; universal + transferable |
| **AutoDAN** | 2023 | Black-box (queries only) | Genetic algorithm with fluency-constrained fitness (§3.4) | Fluent role-play-style prompts |
| **AutoDAN-Turbo** | 2024 | Black-box | Fully automated *strategy discovery* — no human-seeded jailbreak strategies; builds a growing strategy library via lifelong exploration | Diverse, self-discovered strategies; highest reported ASR (attack success rate) among black-box methods at publication |
| **TAP** (Tree of Attacks with Pruning) | 2023 | Black-box | Attacker LLM + judge LLM, pruned tree search (§3.5) | Natural-language jailbreak prompts; far fewer queries than PAIR |
| **BEAST** | 2024 | Gray-box (token probabilities) | Beam search over adversarial suffix tokens | Semi-readable suffixes; runs in under a minute on one GPU |
| **GPTFuzzer** | 2023 | Black-box | Fuzzing: seed templates + mutation operators (synonym swap, role-play rewrites), success-driven seed selection | Mutated natural-language templates |
| **PAP** (Persuasive Adversarial Prompts) | 2024 | Black-box, **no optimization** | Applies social-science persuasion techniques (authority endorsement, logical appeal, foot-in-the-door, etc.) directly via prompt templates | Fully natural, persuasion-framed requests |
| **Many-shot jailbreaking** | 2024, Anthropic | Black-box | Long-context in-context priming with many compliant examples — see [Safety & Alignment §4.1](README.md) | Long conversational context, no suffix |

---

## 5. Architecture Diagrams

### 5.1 GCG Optimization Loop

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    Init(["harmful_request + suffix tokens (initially garbage)\nx = [request || x_1 x_2 … x_10]"]) --> Forward
    Forward["Forward pass\nL(x') = –log P('Sure, here is…' | x')"] --> Backprop
    Backprop["Backprop → gradient w.r.t.\none-hot embedding of each suffix position"] --> TopK
    TopK["For each position i:\ntop-K candidate replacement tokens\n(largest negative gradient)"] --> Sample
    Sample["Sample B candidate suffixes\n(each swaps ONE position with a top-K token)\nrun actual forward pass on all B"] --> Best
    Best["Keep candidate with LOWEST actual loss\n→ new current suffix"] --> Done{"N steps complete\nor jailbreak\nsucceeds?"}
    Done -- NO --> Forward
    Done -- YES --> Out([Adversarial suffix found])

    classDef io     fill:#282c34,stroke:#61afef,color:#abb2bf
    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef llm    fill:#1e2127,stroke:#c678dd,color:#abb2bf
    classDef decide fill:#1e2127,stroke:#e5c07b,color:#abb2bf
    classDef warn   fill:#1e2127,stroke:#e06c75,color:#abb2bf

    class Init,Out io
    class Forward,Backprop llm
    class TopK,Sample,Best proc
    class Done decide
```

GCG typically runs N=500 steps; the suffix is whitebox-optimized against one model and often transfers to black-box targets with reduced but non-zero attack success rate.

### 5.2 AutoDAN Genetic Loop

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    Pop(["Population: P role-play-style seed prompts"]) --> Score
    Score["Score each prompt\nfitness = w1 × jailbreak_success(target, prompt)\n       + w2 × (1 / perplexity(prompt))"] --> Select
    Select["Select top-fitness prompts"] --> Mutate & Cross
    Mutate["MUTATION\nword-level swap\nLLM-paraphrase"] --> NewPop
    Cross["CROSSOVER\ncombine fragments of two parents"] --> NewPop
    NewPop["New population (generation t+1)"] --> Done{"success or budget\nexhausted?"}
    Done -- NO --> Score
    Done -- YES --> Out([Fluent adversarial prompt found])

    classDef io     fill:#282c34,stroke:#61afef,color:#abb2bf
    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef llm    fill:#1e2127,stroke:#c678dd,color:#abb2bf
    classDef decide fill:#1e2127,stroke:#e5c07b,color:#abb2bf

    class Pop,Out io
    class Score,Select proc
    class Mutate,Cross llm
    class NewPop proc
    class Done decide
```

### 5.3 TAP Tree Search with Pruning

```
                    [root: initial harmful request]
                              |
            +-----------------+-----------------+
            v                 v                 v
       [child 1]          [child 2]         [child 3]
     attacker-LLM       attacker-LLM       attacker-LLM
       refinement         refinement         refinement
            |                 |                 |
       judge score:       judge score:      judge score:
       on-topic, low      on-topic, med     OFF-TOPIC -> PRUNED
       jailbreak score    jailbreak score        x
            |                 |
       expand further    expand further
       (depth+1)         (depth+1, becomes
                          best candidate)
```

### 5.4 Layered Defense Pipeline

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart TD
    Prompt([Incoming Prompt]) --> Perplexity
    Perplexity["Perplexity Filter\nDetects gibberish / high-perplexity suffixes\n(GCG, BEAST)"] -- "gibberish / high perplexity" --> Reject1([REJECT])
    Perplexity -- "passes (fluent text — AutoDAN, PAP pass here)" --> Smooth
    Smooth["SmoothLLM\nRandomized character-level perturbations\nMajority-vote over multiple perturbed copies\n(breaks fragile optimized suffixes)"] -- "passes" --> RepE
    RepE["RepE / Circuit Breaker\nActivation-level detector\nInternal representations → harmful-generation state\nInterrupts generation regardless of prompt phrasing\n(catches novel / unseen strategies)"] -- "passes" --> Model
    Model([Model generates response])

    classDef io     fill:#282c34,stroke:#61afef,color:#abb2bf
    classDef proc   fill:#1e2127,stroke:#98c379,color:#abb2bf
    classDef llm    fill:#1e2127,stroke:#c678dd,color:#abb2bf
    classDef warn   fill:#1e2127,stroke:#e06c75,color:#abb2bf

    class Prompt,Model io
    class Perplexity,Smooth,RepE warn
    class Reject1 warn
```

Each layer catches a different attack class — no single filter stops everything; GCG bypasses fluency checks while AutoDAN bypasses perplexity filters, requiring stacked defenses.

### 5.5 The Fluency x Effectiveness Map — Why No Single Defense Covers Both

```mermaid
quadrantChart
    title Fluency vs effectiveness of jailbreak attacks
    x-axis Gibberish --> Fluent
    y-axis Ineffective --> Effective
    quadrant-1 Fluent and effective — top risk
    quadrant-2 Gibberish but effective
    quadrant-3 Gibberish and weak
    quadrant-4 Fluent but weak
    GCG / BEAST: [0.2, 0.85]
    AutoDAN / AutoDAN-Turbo / PAP / TAP: [0.82, 0.85]
    Random junk: [0.15, 0.15]
    Naive role-play: [0.82, 0.2]
```

A perplexity filter (§6.3) can only cut the left (gibberish) column — the top-right quadrant
(fluent AND effective: AutoDAN, PAP) sails straight through, which is why it needs a semantic /
intent classifier or a representation-level defense, not perplexity. This axis, not the search
algorithm, is what determines which §8.3 defense works.

The single most important consequence: an attack's *output style* (which axis it lives
on), not its *search method*, dictates the defense — which is why layered defenses (§5.4)
pair a perplexity filter with an intent classifier rather than relying on either alone.

---

## 6. How It Works — Detailed Mechanics

### 6.1 GCG: Coordinate-Gradient Suffix Optimization

```python
import torch
from dataclasses import dataclass

@dataclass
class GCGConfig:
    suffix_len: int = 20
    top_k: int = 256          # candidate tokens per position (by gradient)
    batch_size: int = 512     # candidates evaluated per step
    num_steps: int = 500

def gcg_step(
    model, tokenizer, prompt_ids: torch.Tensor, suffix_ids: torch.Tensor,
    target_ids: torch.Tensor, config: GCGConfig,
) -> torch.Tensor:
    """One step of §3.2's greedy coordinate descent. Returns the updated suffix."""
    full_ids = torch.cat([prompt_ids, suffix_ids, target_ids])
    embeds = model.get_input_embeddings()(full_ids).detach().requires_grad_(True)

    logits = model(inputs_embeds=embeds.unsqueeze(0)).logits
    target_slice = slice(len(prompt_ids) + len(suffix_ids), len(full_ids))
    loss = torch.nn.functional.cross_entropy(
        logits[0, target_slice[0] - 1 : target_slice[-1]], target_ids
    )
    loss.backward()

    suffix_slice = slice(len(prompt_ids), len(prompt_ids) + len(suffix_ids))
    grad = embeds.grad[suffix_slice]                       # (suffix_len, hidden_dim)

    # Top-k candidate REPLACEMENT tokens per position: most loss-reducing
    # direction = most NEGATIVE dot-product with the gradient.
    vocab_embeds = model.get_input_embeddings().weight     # (vocab, hidden_dim)
    scores = -grad @ vocab_embeds.T                         # (suffix_len, vocab)
    top_k_per_pos = scores.topk(config.top_k, dim=-1).indices

    # Sample B candidates: each swaps ONE random position with one of
    # its top-k candidates. Evaluate each with an ACTUAL forward pass
    # (the gradient is only a heuristic for narrowing candidates).
    best_suffix, best_loss = suffix_ids.clone(), loss.item()
    for _ in range(config.batch_size):
        pos = torch.randint(0, config.suffix_len, (1,)).item()
        new_token = top_k_per_pos[pos, torch.randint(0, config.top_k, (1,)).item()]
        candidate = suffix_ids.clone()
        candidate[pos] = new_token
        cand_loss = _evaluate_loss(model, prompt_ids, candidate, target_ids)
        if cand_loss < best_loss:
            best_suffix, best_loss = candidate, cand_loss

    return best_suffix


def _evaluate_loss(model, prompt_ids, suffix_ids, target_ids) -> float:
    full_ids = torch.cat([prompt_ids, suffix_ids, target_ids]).unsqueeze(0)
    logits = model(full_ids).logits
    target_slice = slice(len(prompt_ids) + len(suffix_ids) - 1, full_ids.shape[1] - 1)
    return torch.nn.functional.cross_entropy(logits[0, target_slice], target_ids).item()
```

**Concrete numbers**: the original GCG paper used `suffix_len=20`, `top_k=256`, `batch_size=512`,
and `num_steps=500` against an ensemble of open-weight models (Vicuna-7B/13B), achieving high
attack-success rates on the open models and **nonzero transfer** (tens of percent ASR) to GPT-3.5
and, with lower rates, GPT-4 and Claude — at the time of publication, before model providers
deployed countermeasures informed by this research.

**In plain terms.** "Each of the 500 steps costs one backward pass plus 512 forward passes, so the
published configuration is roughly a quarter of a million model evaluations to produce one suffix."

That total is the honest price tag on GCG, and it is the number that makes GCG a *lab* method
rather than an *online* one. It also explains why the field kept searching: BEAST's headline claim
(§4) is the same job in under a minute on one GPU.

| Quantity | What it is |
|---|---|
| `num_steps = 500` | Outer loop iterations — how many single-token swaps get committed |
| `batch_size = 512` | Candidate suffixes forward-passed per step, each swapping one position |
| `top_k = 256` | Replacement tokens kept per position after gradient ranking |
| `suffix_len = 20` | Positions available to swap, so `20 x 256 = 5,120` legal moves per step |
| backward pass | One per step, to produce `grad` — the `(suffix_len, hidden_dim)` gradient |

**Walk one example.** Costing out the paper's exact config:

```
  per step   1 backward pass          (gradient over 20 x 32000 = 640,000 pairs)
           + 512 forward passes       (the _evaluate_loss call, once per candidate)

  total      500 x 512 = 256,000 forward passes
           +       500 = 500 backward passes

  Candidates seen per step:  512 of the 5,120 legal moves = 10.0%
  So GCG commits a swap after sampling one tenth of even its own shortlist.
```

The `10.0%` figure is the underappreciated part. GCG is not greedy over the shortlist — it is
greedy over a *random sample* of the shortlist, which is what keeps the per-step cost at 512 rather
than 5,120 forward passes and injects enough stochasticity to avoid getting stuck. Re-running with a
different seed genuinely produces a different suffix, which is exactly the fact that defeats
adversarial training on a fixed suffix corpus (Pitfall 10.2).

### 6.2 AutoDAN: Fluency-Constrained Genetic Mutation

```python
import math
from dataclasses import dataclass, field

@dataclass
class AutoDANCandidate:
    text: str
    jailbreak_score: float = 0.0   # from target-model probe (§3.4)
    perplexity: float = 0.0        # from reference LM

    @property
    def fitness(self, w_jb: float = 0.7, w_fluency: float = 0.3) -> float:
        fluency_term = 1.0 / (1.0 + self.perplexity)   # lower perplexity -> higher term
        return w_jb * self.jailbreak_score + w_fluency * fluency_term


def autodan_generation(
    population: list[AutoDANCandidate], target_model, reference_lm, paraphraser_lm,
    mutation_rate: float = 0.3,
) -> list[AutoDANCandidate]:
    """One generation of AutoDAN's genetic loop (§3.4)."""
    # 1. Score current population
    for c in population:
        c.jailbreak_score = _probe_jailbreak(target_model, c.text)
        c.perplexity = _compute_perplexity(reference_lm, c.text)

    # 2. Select top performers (tournament selection)
    population.sort(key=lambda c: c.fitness, reverse=True)
    survivors = population[: len(population) // 2]

    # 3. Mutation: LLM-paraphrase a fraction of survivors, preserving intent
    #    but altering surface form -- this is what keeps fluency HIGH
    #    while exploring new phrasings (unlike GCG's token-level edits).
    offspring = []
    for c in survivors:
        if torch.rand(1).item() < mutation_rate:
            mutated_text = paraphraser_lm.paraphrase(c.text, preserve_intent=True)
            offspring.append(AutoDANCandidate(text=mutated_text))
        else:
            offspring.append(c)

    # 4. Crossover: splice fragments of two parents (sentence-level)
    for i in range(0, len(survivors) - 1, 2):
        crossed = _crossover(survivors[i].text, survivors[i + 1].text)
        offspring.append(AutoDANCandidate(text=crossed))

    return offspring + survivors


def _probe_jailbreak(target_model, text: str) -> float: ...
def _compute_perplexity(reference_lm, text: str) -> float: ...
def _crossover(text_a: str, text_b: str) -> str: ...
```

**The idea behind it.** "Rank each candidate mostly on whether it jailbreaks, but add a small bonus
for reading like real English — so gibberish can never win a tie."

The `1.0 / (1.0 + perplexity)` shape matters more than the weights do. It maps any perplexity onto
`(0, 1]` without dividing by zero, and it is monotonically decreasing, so lower perplexity always
scores higher. What it is *not* is linear.

| Symbol | What it is |
|---|---|
| `jailbreak_score` | Target-model probe result, `0` to `1`. Higher = closer to a successful jailbreak |
| `perplexity` | Reference-LM surprise at the prompt's wording. ~15-25 fluent (§6.3), 1000s for gibberish |
| `1/(1 + perplexity)` | The fluency term. Perfect fluency (`ppl = 0`) gives 1; huge perplexity gives ~0 |
| `w_jb = 0.7` | Weight on jailbreak success — the dominant term |
| `w_fluency = 0.3` | Weight on the fluency term — the tiebreaker, not the driver |
| `fitness` | `w_jb x jailbreak_score + w_fluency x fluency_term`; population sorts on this |

**Walk one example.** A gibberish candidate that jailbreaks well, against a fluent one that is a
little weaker:

```
                        jb_score   ppl     1/(1+ppl)   0.7 x jb   0.3 x term   fitness
  GCG-style gibberish     0.90     1500     0.00067     0.630      0.00020     0.63020
  AutoDAN-style fluent    0.70       20     0.04762     0.490      0.01429     0.50429

  The gibberish candidate WINS this comparison: 0.630 > 0.504.
```

That result is the point, and it is counterintuitive. The fluency term is worth at most `0.3`, but
at realistic perplexities it delivers `0.014` versus `0.0002` — a gap of `0.0141`, which a
jailbreak-score advantage of only `0.0141 / 0.7 = 0.020` completely erases. A candidate just two
points of jailbreak score better can be arbitrarily less fluent and still win.

**Why AutoDAN's outputs are fluent anyway.** Not because this term forces it — the arithmetic above
shows it barely can — but because the *mutation operator* is an LLM paraphrase (`paraphraser_lm`),
which can only produce fluent text in the first place. The population never contains gibberish for
the fluency term to have to reject. The term is a guardrail against drift, while the real fluency
constraint is structural: unlike GCG's token-level swaps, no operator in this loop can leave the
manifold of natural language. Swap the paraphraser for random token edits and this fitness function
would not save you.

### 6.3 BROKEN -> FIX: Perplexity Filter Bypassed by Fluent Jailbreaks

```python
# BROKEN: a single-layer perplexity filter. This STOPS GCG/BEAST-style
# gibberish suffixes (perplexity 1000s) but does NOTHING against
# AutoDAN/PAP-style fluent role-play prompts (perplexity comparable to
# normal creative-writing requests, §3.6) -- they sail through.
def is_safe_broken(prompt: str, reference_lm, threshold: float = 50.0) -> bool:
    return _compute_perplexity(reference_lm, prompt) < threshold
    # AutoDAN's "You are a method actor preparing for a film role about
    # a chemistry teacher who explains, in great technical detail..."
    # has perplexity ~15-25 -- well under threshold. PASSES. Jailbreak succeeds.
```

```python
# FIX: layered defense (§5.4) -- perplexity filter catches GIBBERISH
# attacks; SmoothLLM catches OPTIMIZED-BUT-FRAGILE attacks (both GCG
# suffixes AND AutoDAN's specific phrasing, which is brittle to small
# perturbations even though FLUENT); RepE circuit breaker catches
# NOVEL strategies that bypass both, by monitoring internal activations
# rather than the input text at all.
def is_safe_fixed(
    prompt: str, reference_lm, target_model, circuit_breaker,
    threshold: float = 50.0, num_perturbations: int = 5,
) -> bool:
    if _compute_perplexity(reference_lm, prompt) >= threshold:
        return False                                    # layer 1: gibberish

    # Layer 2: SmoothLLM -- randomly perturb a few characters in K copies,
    # query the target on each, majority-vote the refusal decision.
    # Optimized suffixes/phrasings are FRAGILE to small perturbations:
    # a single swapped character can collapse an AutoDAN prompt's
    # carefully-tuned fitness back toward "refuse."
    votes = []
    for _ in range(num_perturbations):
        perturbed = _random_char_perturb(prompt, rate=0.05)
        votes.append(target_model.would_refuse(perturbed))
    if sum(votes) / len(votes) < 0.5:                    # majority says "complies"
        return False                                     # layer 2: fragile-optimized attack

    # Layer 3: activation-level circuit breaker -- runs DURING generation,
    # independent of the input's surface form (catches strategies #1
    # and #2 never anticipated).
    return not circuit_breaker.would_trigger(prompt)


def _random_char_perturb(text: str, rate: float) -> str: ...
```

---

## 7. Real-World Examples

- **GCG (2023)** — demonstrated that suffixes optimized against an ensemble of Vicuna-7B/13B
  transferred to **GPT-3.5, GPT-4, Bard (Gemini predecessor), and Claude** with measurable
  (though reduced) attack success rates at publication — the result that made automated,
  transferable jailbreaks a mainstream safety concern rather than a curiosity.
- **AutoDAN-Turbo (2024)** — reported the **highest black-box attack success rate** among
  evaluated methods at publication by *discovering its own strategy library* through lifelong
  exploration rather than relying on human-seeded strategies (e.g., "DAN", role-play) —
  demonstrating that the space of effective jailbreak strategies extends well beyond known
  human-discovered patterns.
- **TAP (2023)** — achieved comparable or higher attack success rates than the earlier **PAIR**
  algorithm (referenced in [Safety & Alignment §11](README.md)) while requiring **substantially
  fewer queries** to the target model, due to the tree-pruning step (§3.5) avoiding wasted
  exploration of off-topic branches.
- **SmoothLLM (2023)** — showed that randomized character-level perturbation + majority voting
  reduces GCG attack success rates from near-100% to single digits on tested models, at the cost
  of a small utility/latency hit from running multiple forward passes per query — directly
  motivating §6.3's layered defense.
- **RepE / Circuit Breakers (2024, research from groups including Anthropic interpretability
  teams)** — demonstrated defenses operating on **internal activations** rather than input text,
  remaining effective against jailbreak strategies the defense was never trained against,
  because the defense targets the *internal representation of "about to produce harmful
  content"* rather than surface-level input patterns — see
  [Mechanistic Interpretability §6](../mechanistic_interpretability/README.md) for the underlying
  activation-steering mechanism.

---

## 8. Tradeoffs

### 8.1 White-Box vs. Black-Box Attacks

| | White-box (GCG) | Black-box (TAP, AutoDAN, PAP, GPTFuzzer) |
|---|---|---|
| Access required | Model weights/gradients | Query access only (input/output) |
| Directly applicable to closed APIs? | No — must rely on transfer (§3.3) | Yes — designed for this |
| Optimization signal | Exact gradients (precise but expensive per step) | LLM judge scores / fitness functions (noisier, cheaper per query in some cases) |
| Typical use in red-teaming | Optimize against open-weight proxy, test transfer | Direct evaluation of the target itself |

### 8.2 Output Style: Gibberish vs. Fluent

| | Gibberish (GCG, BEAST) | Fluent (AutoDAN, PAP, AutoDAN-Turbo) |
|---|---|---|
| Detectable by perplexity filter? | Yes — easily | No (§3.6) |
| Detectable by SmoothLLM? | Often — fragile to perturbation | Often — also fragile (the *exact phrasing* matters even if fluent) |
| Human-interpretable (useful for understanding *why* it works)? | Low | High — reveals exploitable framing patterns (authority, fiction, gradual escalation) |
| Typical effectiveness pre-transfer (white-box) | Very high | Moderate-to-high, more model-dependent |

### 8.3 Defense Comparison

| Defense | Stops Gibberish (GCG) | Stops Fluent (AutoDAN/PAP) | Stops Novel/Unseen Strategies | Cost |
|---|---|---|---|---|
| Perplexity filter | Yes | No | No | Negligible (single forward pass on a small LM) |
| SmoothLLM | Yes (fragile suffixes) | Partial (fragile phrasings) | No | K extra forward passes on the target model |
| Paraphrase defense (rewrite input before passing to target) | Yes | Partial | No | One LLM call |
| Adversarial training (train on known attack outputs) | Yes, for trained attacks | Yes, for trained attacks | No — "whack-a-mole" (Pitfall 10.2) | High (retraining) |
| RepE / circuit breakers | Yes | Yes | **Yes** — activation-level, strategy-agnostic | Moderate (inference-time activation monitoring) |

---

## 9. When to Use / When NOT to Use

**Use automated jailbreak algorithms when:**

- Conducting **pre-launch and recurring red-team evaluations** — automated algorithms provide
  reproducible, quantifiable attack-success-rate metrics across hundreds of harmful-behavior
  prompts, feeding directly into Responsible Scaling Policy gates and regulatory model cards
  ([AI Regulations & Compliance](../ai_regulations_and_compliance/README.md)).
- **Testing transferability** of safety mitigations — running GCG against an open-weight proxy
  and checking transfer to your production model reveals whether your safety training shares
  failure modes with the proxy (§3.3).
- **Stress-testing a specific defense layer** — e.g., run AutoDAN specifically to verify a
  perplexity filter's blind spot (§6.3) before relying on it in production.
- Building the **automated component of a [red-team eval harness](../case_studies/cross_cutting/red_team_eval_harness.md)** —
  these algorithms are designed to run at scale (thousands of variants), unlike manual red-teaming.

**Do NOT rely on automated jailbreak algorithms alone when:**

- **As your only red-teaming method** — automated algorithms find *optimization-discoverable*
  vulnerabilities; domain-expert manual red-teaming (per [Safety & Alignment
  §12](README.md)'s discussion of red-team structure) finds *domain-specific* vulnerabilities
  (e.g., a biosecurity expert recognizing a subtly dangerous synthesis route that no generic
  algorithm would target).
- **As a one-time gate** — per [Safety & Alignment §10 Pitfall
  4](README.md) ("one-time red teaming"), new algorithms (AutoDAN-Turbo's strategy discovery is
  itself evidence the space keeps expanding) are published continuously; a defense validated
  against 2023-era GCG/AutoDAN may have known gaps against 2025-era methods.
- **As justification for adversarial training as your primary defense** — training against known
  attack outputs creates a whack-a-mole dynamic (Pitfall 10.2); use these algorithms to validate
  **representation-level** defenses (circuit breakers) that don't require enumerating attacks.

---

## 10. Common Pitfalls

**10.1 Trusting a Single Defense Layer (see §6.3 BROKEN -> FIX)**

The single most common production gap: a perplexity filter alone, deployed because it's cheap and
stops the most *visually obvious* attacks (gibberish GCG suffixes), while AutoDAN/PAP-style fluent
jailbreaks — which look like legitimate creative-writing or research requests — pass through
unimpeded. **Defense-in-depth (§5.4) is not optional** once any single algorithm's output style is
known to bypass a given layer.

**10.2 Adversarial Training as a Whack-a-Mole**

Fine-tuning a model to refuse the *specific outputs* of a known jailbreak algorithm (e.g.,
training against a corpus of GCG suffixes) produces a model that's robust to **that algorithm's
current configuration** but not to minor variants, new algorithms, or the same algorithm re-run
with a different random seed/target prefix. This is the discrete-optimization analogue of
overfitting — the model has memorized specific adversarial examples, not learned a general
refusal *representation*. RepE/circuit-breaker approaches (§8.3) attempt to target the
representation directly instead.

**10.3 Measuring Only Attack Success Rate, Not Severity**

A 5% attack success rate sounds low until you consider *what* the 5% of successful jailbreaks
elicit. An algorithm that succeeds rarely but, when it does, extracts detailed CBRN synthesis
information is a more severe finding than one that succeeds often but only elicits mildly
impolite text. Red-team reports should pair **success rate** with **severity distribution**
(per [Safety & Alignment §3](README.md): "safety is not binary").

**Stated plainly.** "Attack success rate is just successes divided by prompts tried — which means
it is an estimate with an error bar, and on a small prompt set that error bar is wide enough to
swallow most of the differences people report."

ASR is a proportion estimated from a binomial sample, so it carries the standard error every
proportion carries. Reporting `ASR = 22%` without `n` is reporting half a result.

| Symbol | What it is |
|---|---|
| `k` | Number of prompts where the attack succeeded (judge-scored as a jailbreak) |
| `n` | Number of harmful-behavior prompts attempted — the denominator that is usually omitted |
| `ASR = k/n` | The point estimate. A sample proportion, not the true underlying rate |
| `SE = sqrt(ASR(1-ASR)/n)` | Standard error — how much `k/n` would bounce across repeated runs |
| `95% CI = ASR +/- 1.96 x SE` | Normal-approximation interval; the range the true rate plausibly sits in |
| `1.96` | The z-value cutting off 2.5% in each tail of a normal distribution |

**Walk one example.** The same attack measured on 50 prompts and on §14's 520:

```
  n = 50,  k = 42
    ASR = 42/50           = 84.0%
    SE  = sqrt(.84 x .16 / 50)  = 0.0518  = 5.18%
    CI  = 84.0 +/- 1.96 x 5.18  = [73.8%, 94.2%]     width 20.3 points

  n = 520, k = 114
    ASR = 114/520         = 21.9%
    SE  = sqrt(.219 x .781 / 520) = 0.0181 = 1.81%
    CI  = 21.9 +/- 1.96 x 1.81    = [18.4%, 25.5%]   width 7.1 points
```

Now the comparison that matters. At `n = 50`, an attack scoring `22%` has CI `[10.5%, 33.5%]` and
one scoring `14%` has CI `[4.4%, 23.6%]` — those intervals overlap across nearly their whole range,
so "AutoDAN beat TAP" is not a finding, it is noise. Run the same two attacks at `n = 520` and the
intervals become `[18.4%, 25.5%]` and `[11.1%, 17.0%]`, which do not overlap at all, and the
ordering is now real.

**Why `n` belongs in every reported ASR.** The `sqrt(n)` in the denominator means precision improves
only with the square root of effort: at a fixed underlying rate, going from 50 to 520 prompts is
10.4x the compute for `sqrt(10.4) = 3.2x` tighter bounds. That is the actual reason AdvBench-style suites are sized in the hundreds
rather than the dozens — and the reason a red-team report claiming a mitigation "cut ASR from 18%
to 14%" on 50 prompts has demonstrated nothing at all.

**10.4 Ignoring Transferability When Evaluating Closed Models**

Teams sometimes assume "we only need to test attacks designed for *our* model architecture." GCG's
foundational result (§3.3, §7) is that this assumption is false — attacks optimized against
open-weight proxies transfer with nonzero success to architecturally different closed models.
Closed-model teams should still run open-weight-proxy-based attacks (GCG against a similar-size
open model) as part of their evaluation suite, not only black-box methods against their own model.

**10.5 Conflating "Jailbreak" Severity Across Use Cases**

A jailbreak that makes a general-purpose assistant produce mildly inappropriate content is a
different severity class than the same technique succeeding against a model with **agentic tool
access** (where compliance might mean executing a harmful action, not just generating text) — see
[Multi-Agent Security](../multi_agent_systems/multi_agent_security.md) for the agentic extension of
this threat model. Evaluation suites for agentic systems must extend beyond text-generation
jailbreak benchmarks (AdvBench-style) to action-level harm.

---

## 11. Technologies & Tools

| Tool / Resource | Role |
|---|---|
| **llm-attacks (GCG reference implementation)** | Original GCG codebase; basis for most follow-on white-box research |
| **nanoGCG** | Lightweight, modern GCG reimplementation — common starting point for red-team automation |
| **AutoDAN / AutoDAN-Turbo (open repos)** | Reference genetic-algorithm and strategy-discovery implementations |
| **EasyJailbreak** | Unified framework implementing GCG, AutoDAN, PAIR, TAP, GPTFuzzer, and others under one interface — standard for comparative red-team evaluation |
| **Garak** | LLM vulnerability scanner referenced in [Safety & Alignment §11](README.md); includes probes for several algorithms in this module |
| **SmoothLLM (reference implementation)** | Randomized-smoothing defense (§8.3) |
| **RepE / circuit-breaker tooling** | See [Mechanistic Interpretability §11](../mechanistic_interpretability/README.md) for activation-steering and representation-engineering tooling |

---

## 12. Interview Questions with Answers

**Q1: What's the fundamental difference between hand-crafted jailbreaks (DAN, role-play) and automated jailbreak algorithms?**
Hand-crafted jailbreaks are individual prompts a human writes and iterates on manually — they don't scale to systematic robustness measurement and can't be "re-run" against a new model without human effort. Automated algorithms (GCG, AutoDAN, TAP, etc.) frame jailbreaking as an optimization or search problem with a measurable objective (§3.1) — they produce *reproducible attack-success-rate metrics* across large prompt sets and models, which is what feeds into Responsible Scaling Policy gates and red-team reports. The categories aren't mutually exclusive: many automated algorithms' search spaces include role-play-style framings as building blocks (AutoDAN's population, PAP's persuasion templates).

**Q2: Walk through GCG's optimization loop — why use gradients if the final output space is discrete?**
GCG computes the gradient of the loss (probability of NOT producing an affirmative-prefix target, §3.1) with respect to the *one-hot embedding* of each suffix token position — this gradient indicates which token *substitutions* would most reduce the loss, for each position, in a continuous relaxation. But you can't just "take a gradient step" on a discrete token, so GCG uses the gradient only to narrow each position down to its top-k candidate replacements (e.g., k=256), then runs actual forward passes on a batch of candidates (each swapping one position) and keeps whichever candidate has the lowest *actual* loss. This is greedy coordinate descent with gradient-guided candidate selection — the gradient is a heuristic for "where to look," not the update itself.

**Q3: Why do GCG suffixes transfer to models they weren't optimized against, including closed APIs?**
The leading explanation connects to the linear representation hypothesis (see [Mechanistic Interpretability](../mechanistic_interpretability/README.md)): safety fine-tuning across different base models and labs tends to produce similar "refusal direction" representations in activation space, because they're solving a structurally similar problem (suppress compliance for a class of requests). A suffix that perturbs this shared direction in one model has a correlated, if weaker, effect in others. Practically, this means white-box attacks against open-weight proxies remain a meaningful evaluation signal for closed models (Pitfall 10.4) — you don't need access to the target to learn something about its vulnerabilities.

**Q4: A team deploys only a perplexity filter and reports "we're protected against jailbreaks." What's wrong with this claim?**
Perplexity filters detect *statistically unusual token sequences* — they catch GCG/BEAST-style gibberish suffixes (perplexity in the thousands) but do nothing against AutoDAN, AutoDAN-Turbo, or PAP, whose entire design optimizes for (or inherently produces) *fluent, low-perplexity* prompts (§3.6) — a well-crafted role-play framing has perplexity comparable to a legitimate creative-writing request and sails through unchanged (§6.3 BROKEN example). The claim conflates "defended against one attack family" with "defended against jailbreaks" generally — defense-in-depth (§5.4, §8.3) covering both gibberish and fluent attack styles, plus representation-level defenses for novel strategies, is required for a meaningful protection claim.

**Q5: How does AutoDAN's genetic algorithm avoid producing gibberish, unlike GCG?**
AutoDAN's fitness function explicitly weights *fluency* (inverse perplexity under a reference LM) alongside jailbreak success (§3.4) — candidates that drift toward gibberish are selected against from generation one, not as an afterthought. Its mutation operator is also LLM-based paraphrasing (preserving intent while varying surface form) rather than GCG's token-level swaps, which naturally stays within the manifold of fluent text. The tradeoff (§8.2): AutoDAN typically requires more queries/generations to converge than GCG's gradient-guided search, but produces outputs that are both effective AND human-interpretable — useful for understanding *which framings* (role-play, fictional context, gradual escalation) are exploitable.

**Q6: What does TAP's "pruning" actually prune, and why does it matter for query efficiency?**
At each tree node, TAP's judge LLM scores the candidate prompt on two axes: jailbreak progress (is the response moving toward compliance?) and on-topic-ness (does the prompt still relate to the original harmful request, or has the attacker LLM's refinement drifted into an unrelated topic?). Branches scored as off-topic or unlikely to improve are pruned — not expanded further. Without pruning (PAIR's approach — purely iterative, no tree), the search either commits to a single refinement path (missing better alternatives) or would need to expand all branches (expensive). TAP's pruned-tree search reported comparable-or-better attack success rates than PAIR with substantially fewer target-model queries — query count matters directly for cost when evaluating against paid APIs.

**Q7: Why is "transferability" both a research finding and a red-teaming methodology?**
As a *finding* (§3.3, §7), transferability revealed that jailbreak vulnerabilities aren't fully model-specific — they reflect something structural about how current alignment techniques work. As a *methodology*, it's actionable: a team without white-box access to their production model (most teams using closed APIs, or even teams protecting their own weights from internal red-teamers) can still run GCG against a same-family open-weight model and use the transfer rate as a *proxy* signal for their production model's vulnerability — cheaper and faster than exhaustively running black-box methods (TAP, AutoDAN) against the expensive production endpoint for every evaluation cycle, though black-box methods remain necessary for a complete picture.

**Q8: How does SmoothLLM defend against optimized suffixes without knowing which algorithm produced them?**
SmoothLLM applies small random perturbations (e.g., randomly flipping ~5% of characters) to the input, queries the target model on several perturbed copies, and takes a majority vote on whether the response constitutes a refusal (§6.3, layer 2). The insight: GCG/BEAST suffixes are the result of *fine-grained optimization* — they work because of very specific token sequences, and small perturbations often destroy the optimized structure, causing the model to revert to its default (refusing) behavior on most perturbed copies. This is algorithm-agnostic — SmoothLLM doesn't need to recognize "this is a GCG suffix," it just exploits that *optimized* attacks tend to be fragile. The limitation: AutoDAN/PAP's fluent prompts can be *more* robust to character-level perturbation since their effectiveness comes from semantic framing, not exact token sequences — hence SmoothLLM alone is "partial" in §8.3, not complete.

**Q9: What makes RepE/circuit-breaker defenses qualitatively different from the other defenses in §8.3?**
Every other defense in §8.3 operates on the *input text* (perplexity, perturbation, paraphrasing) or on *training-time exposure to known attacks* (adversarial training) — meaning each has a corresponding attack category it's blind to (gibberish-only, fragile-only, known-attacks-only). RepE/circuit-breaker defenses instead monitor the model's *internal activations during generation*, looking for representations associated with "about to produce harmful content" regardless of what input text triggered that internal state (connecting to [Mechanistic Interpretability's activation steering, §6](../mechanistic_interpretability/README.md)). This makes them robust to *novel* strategies (like AutoDAN-Turbo's self-discovered ones) that were never seen during defense construction — at the cost of requiring activation-level access (not available for third-party closed APIs you don't control) and ongoing research into false-positive rates on legitimate requests that touch similar topics.

**Q10: AutoDAN-Turbo claims the "highest" attack success rate by discovering its own strategies. What does this imply about the completeness of human-curated jailbreak taxonomies?**
It implies human-curated taxonomies (role-play, many-shot, crescendo — per [Safety & Alignment §4.1](README.md)) are necessarily incomplete — they're the strategies humans have *thought of and published*, not the full space of effective strategies. AutoDAN-Turbo's lifelong exploration discovers novel strategies by searching the space directly rather than starting from a human-seeded list, and reportedly finds approaches outside the known taxonomy. The practical implication for defenses: any defense validated only against a fixed, named list of attack *categories* has an unknown-sized blind spot — which is the core argument for representation-level defenses (§Q9) that don't require enumerating categories at all.

**Q11: How would you design a red-team evaluation pipeline incorporating automated jailbreak algorithms for a new model release?**
Combine white-box and black-box, gibberish and fluent: run GCG (white-box, against an open-weight proxy if the target is closed) for transferability signal; run AutoDAN and TAP (black-box, fluent) directly against the target for realistic attack-surface coverage; include AutoDAN-Turbo or similar strategy-discovery methods to probe beyond known categories; run PAP to test susceptibility to pure social-engineering framing with zero optimization. Score each on attack success rate AND severity (Pitfall 10.3) across a harm-category-stratified prompt set (AdvBench-style, per [Safety & Alignment §11](README.md)). Layer this with manual domain-expert red-teaming (Pitfall not covered by automation, §9) and re-run on a recurring cadence (Pitfall 10.2/§10 Pitfall 4 in the parent README) — not as a one-time pre-launch gate.

**Q12: What's the relationship between this module's content and prompt injection (covered in [Safety & Alignment §4.4](README.md))?**
They're related but distinct threat models. Jailbreak algorithms (this module) target the *deploying user's own* request — the attacker is the end user trying to get the model to violate its own safety training. Prompt injection's attacker controls *data the model processes* (a webpage, document, email) and tries to make the model follow attacker instructions instead of the developer's — the "victim" is the application/developer, and the end user may be unaware. Both exploit instruction-following, and defenses sometimes overlap (input sanitization, activation monitoring), but the threat models, attacker positions, and primary defenses (instruction hierarchy for injection vs. perplexity/SmoothLLM/circuit-breakers for jailbreaks) differ — see [Multi-Agent Security](../multi_agent_systems/multi_agent_security.md) for how both compound in agentic systems.

**Q13: Why might a defense effective against 2023-era algorithms (GCG, original AutoDAN) be insufficient by 2026?**
Because the field is adversarial and ongoing — AutoDAN-Turbo (2024) demonstrated that strategy discovery can find approaches outside the categories defenses were validated against (§Q10), and this trend continues. A defense validated via adversarial training (Pitfall 10.2) against 2023 attack *outputs* specifically does not generalize to 2024-2025 algorithms' outputs, which look different even if the underlying exploited weakness (e.g., "model commits to compliance once it starts an affirmative response," §3.1) is similar. The practical guidance: prioritize defenses that target the *underlying mechanism* (representation-level, §Q9) over defenses that pattern-match *specific historical outputs*, and budget for recurring re-evaluation against current-generation algorithms.

**Q14: How does the choice of "affirmative prefix" target (§3.1) affect an automated jailbreak's effectiveness, and what's the defensive implication?**
The target prefix (e.g., "Sure, here is how to...") exploits the autoregressive model's tendency to continue in a manner consistent with its own prior tokens — once "Sure, here is" is in the context, continuing with a refusal becomes a much larger distributional jump than continuing with compliance. This is the same "commitment" dynamic seen in reasoning models' chain-of-thought (early tokens constrain later ones). The defensive implication: some mitigations specifically detect and interrupt generation *if an affirmative-compliance prefix begins forming for a harmful request*, even before the harmful content itself appears — an early-intervention point that's cheaper to monitor than the full generated content.

**Q15: If you only had budget to run ONE automated jailbreak algorithm for a pre-launch evaluation, which would you choose, and what would you explicitly NOT be testing for?**
There's no single algorithm covering both axes of §8.2 (gibberish vs. fluent) and both access models (§8.1), so any single choice has a known blind spot you must document. A reasonable default for a closed-model team is TAP or AutoDAN (black-box, fluent, directly applicable, reasonably query-efficient) — but explicitly note you are NOT testing: white-box/gradient-based transferability signal (GCG), pure social-engineering with zero optimization (PAP, which sometimes succeeds where optimized methods fail because it doesn't trigger optimization-detection heuristics), and self-discovered novel strategies (AutoDAN-Turbo). In an interview, the value is in *naming the gap explicitly* — "we tested X, which means we have no signal on Y" — rather than implying single-algorithm coverage is comprehensive.

**Q16: How does this module's threat model change when the target isn't a chatbot but an agent with tool access?**
Everything in §3-§8 still applies to getting the *underlying model* to produce a harmful response, but for an agent, "harmful response" can mean "the model decides to call a destructive tool" rather than "the model generates harmful text" — the action surface is much larger and often higher-stakes (file deletion, financial transactions, sending messages). Attack-success-rate benchmarks designed for text generation (AdvBench-style) don't directly measure this; agentic evaluation needs action-level harm classification, and defenses need to extend beyond the text-generation pipeline to tool-call authorization (least-privilege scoping, human-in-the-loop for high-risk actions) — see [Multi-Agent Security](../multi_agent_systems/multi_agent_security.md) and [Agent Reliability](../agents_and_tool_use/agent_reliability.md) for the agentic extension.

---

## 13. Best Practices

1. **Never deploy a single-layer defense** — perplexity filters alone (Pitfall 10.1) leave fluent automated jailbreaks (AutoDAN, PAP) completely unaddressed; layer perplexity + SmoothLLM + representation-level defenses (§5.4).
2. **Use white-box algorithms (GCG) against open-weight proxies even for closed production models** — transferability (§3.3) makes this a valid, cheap signal (§Q7).
3. **Pair attack success rate with severity scoring** (Pitfall 10.3) — a low success rate against high-severity harms can be worse than a higher rate against low-severity ones.
4. **Treat adversarial training as a supplement, not a foundation** — it addresses known-attack-output patterns (Pitfall 10.2) but doesn't generalize; pair with representation-level defenses.
5. **Re-run the evaluation suite on every model update and on a recurring schedule** — new algorithms (AutoDAN-Turbo) and new model versions both invalidate prior results (Pitfall not bounded by a single test date).
6. **Include at least one strategy-discovery method (AutoDAN-Turbo or equivalent)** to probe outside known jailbreak taxonomies (§Q10), not just named-category methods.
7. **For agentic systems, extend evaluation beyond text-generation ASR to action-level harm** (§Q16) — text-jailbreak benchmarks don't measure tool-call risk.
8. **Document explicitly which attack families a given evaluation run did NOT cover** (§Q15) — false confidence from partial coverage is a recurring failure mode.
9. **Use EasyJailbreak or similar unified frameworks for comparative evaluation** — running each algorithm with consistent prompt sets and scoring makes attack-success-rate numbers comparable across runs.
10. **Cross-reference findings with [Mechanistic Interpretability](../mechanistic_interpretability/README.md)** when investigating *why* a jailbreak succeeded — activation patching/steering can reveal whether a successful attack suppressed a specific "refusal" direction, informing whether a circuit-breaker defense would generalize to it.

---

## 14. Case Study

**Scenario**: Before a model release, a safety team runs a structured automated-jailbreak
evaluation across 520 harmful-behavior prompts (AdvBench-style) spanning 8 harm categories.

**Pipeline**: (1) GCG against an open-weight proxy of similar architecture, checking transfer to
the release candidate — baseline transfer ASR 9%. (2) TAP directly against the release candidate
(black-box) — ASR 14%, with the tree-pruning step keeping the run to ~45 queries/prompt on
average vs. PAIR's prior ~75. (3) AutoDAN — ASR 22%, concentrated in role-play-framed prompts. (4)
PAP — ASR 11%, notably succeeding on 3 prompts where the optimization-based methods (1)-(3) all
failed (no optimization artifact for detection heuristics to flag).

**Finding and fix**: cross-referencing AutoDAN's 22% successes against the deployed perplexity
filter showed **100% of these passed the filter** (fluent role-play, §6.3 BROKEN) — the team added
SmoothLLM (reducing AutoDAN ASR to 6%) and a RepE circuit breaker trained on activation patterns
from this evaluation's successful jailbreaks across all four methods (reducing overall ASR to
2%, including the 3 PAP-only successes the circuit breaker caught despite no optimization
signature). The team scheduled this exact pipeline to re-run **monthly** against the production
model and on every fine-tune, per Best Practice 5.

### 14.1 Reading This Evaluation's Numbers Honestly

**What the formula is telling you.** "Every percentage above is `k/520`, so attach the Pitfall 10.3
confidence interval to each one before deciding which differences are real and which are sampling
noise."

At `n = 520` the intervals are tight enough that most of this report's conclusions survive — but
not all of them, and the exception is the one the team would most want to believe.

| Quantity | What it is |
|---|---|
| `n = 520` | AdvBench-style prompt set size, shared by all four attacks and both defense states |
| `k` | Successful jailbreaks per method — recovered as `ASR x 520`, rounded to whole prompts |
| `SE` | `sqrt(ASR(1-ASR)/520)`, this evaluation's per-method standard error |
| `CI` | `ASR +/- 1.96 x SE`, the 95% normal-approximation interval |

**Walk one example.** Every headline number in the case study, with its error bar:

```
  method / state        k     ASR      SE       95% CI            width
  GCG transfer         47    9.04%   1.26%   [ 6.6%, 11.5%]       4.9
  TAP                  73   14.04%   1.52%   [11.1%, 17.0%]       6.0
  PAP                  57   10.96%   1.37%   [ 8.3%, 13.6%]       5.4
  AutoDAN             114   21.92%   1.81%   [18.4%, 25.5%]       7.1

  AutoDAN after SmoothLLM   31    5.96%   1.04%   [ 3.9%,  8.0%]   4.1
  overall after full stack  10    1.92%   0.60%   [ 0.7%,  3.1%]   2.4
```

Two readings fall out. First, the defense worked and the evidence is solid: AutoDAN's
`[18.4%, 25.5%]` and its post-SmoothLLM `[3.9%, 8.0%]` are nowhere near touching, so the drop is
real, not a lucky re-run. Same for AutoDAN being genuinely worse than TAP — `[18.4%, 25.5%]` versus
`[11.1%, 17.0%]` do not overlap.

Second, the comparison the report treats as meaningful is not. TAP at `14.04%` `[11.1%, 17.0%]` and
PAP at `10.96%` `[8.3%, 13.6%]` overlap across `[11.1%, 13.6%]` — this evaluation does **not**
establish that TAP outperforms PAP, and ranking them in a summary table would be overclaiming. Note
also that PAP's 3 unique successes are `3/520 = 0.58%`, a count small enough that its own interval
is dominated by having seen only three events; the qualitative finding (optimization-free attacks
reach prompts optimized ones miss) is what carries weight there, not the rate.

**Why the final `1.92%` needs the severity pairing, not a tighter interval.** Its CI is
`[0.7%, 3.1%]` — the narrowest in the table, because low rates have small standard errors. That
precision is real but says nothing about what those 10 successes *elicited*, which is exactly
Pitfall 10.3: a well-measured `1.92%` of CBRN-grade completions is a worse result than a sloppily
measured `20%` of mild impoliteness. Narrow error bars measure confidence, never consequence.

---

## Related

- [Safety & Alignment README](README.md) — parent module: jailbreak taxonomy (§4.1), red-teaming process (§5, §12), AdvBench/PAIR/Garak (§11)
- [Mechanistic Interpretability](../mechanistic_interpretability/README.md) — RepE, activation steering, circuit breakers (§6, §11) underlying the representation-level defenses in §8.3
- [LLM Security](../../llm_security/README.md) — adversarial robustness and broader attack-surface coverage beyond jailbreaking
- [Multi-Agent Security](../multi_agent_systems/multi_agent_security.md) — extension of this threat model to agentic/tool-using systems (§Q16)
- [Red Team Eval Harness](../case_studies/cross_cutting/red_team_eval_harness.md) — production infrastructure for running this module's algorithms at scale
- [AI Regulations & Compliance](../ai_regulations_and_compliance/README.md) — how attack-success-rate evaluations feed regulatory model cards and RSP gates
