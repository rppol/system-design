# GRPO & RLVR — Reasoning RL with Verifiable Rewards

Deep-dive sub-file of [Alignment & RLHF](README.md). Covers Group Relative Policy Optimization (GRPO), Reinforcement Learning from Verifiable Rewards (RLVR), the DeepSeek-R1 training pipeline, and the 2025-era refinements (DAPO, Dr. GRPO, GSPO) that now dominate reasoning-model post-training.

---

## 1. Concept Overview

GRPO (Group Relative Policy Optimization) is a critic-free RL algorithm introduced in the DeepSeekMath paper (February 2024) and scaled to frontier size in DeepSeek-R1 (January 2025). It replaces PPO's learned value function with a simple statistical baseline: sample a *group* of G responses to the same prompt, score each one, and use the group's mean and standard deviation to normalize each response's reward into an advantage. Removing the critic eliminates an entire policy-sized network from the training loop — roughly halving trainer memory — and removes the hardest-to-tune component of PPO.

RLVR (Reinforcement Learning from Verifiable Rewards, named in Allen AI's Tulu 3, November 2024) is the reward-side counterpart: instead of a learned reward model, use a *program* that checks correctness — exact answer matching for math, unit tests for code, format validators for structured output. A rule-based verifier cannot be sycophancy-hacked the way a learned reward model can, which is why RLVR scales to hundreds of thousands of RL steps where RLHF saturates or collapses.

GRPO + RLVR together are the recipe behind DeepSeek-R1, and variants of this recipe power essentially every open reasoning model since: Qwen3 (GSPO), Kimi k-series, Llama reasoning fine-tunes, and the open replications of OpenAI's o-series test-time-compute behavior.

---

## 2. Intuition

> **One-line analogy**: GRPO grades on a curve — instead of an expert judge (critic) predicting how good each essay should be, it grades each essay against the other essays written for the same prompt.

**Mental model**: For each math problem, the model writes G=16 different solutions. Eight are right, eight are wrong. The right ones get pushed up, the wrong ones get pushed down — *relative to each other*. No critic network needs to learn "how hard is this problem"; the group itself reveals it. A problem where all 16 samples fail produces zero learning signal (advantage is zero everywhere), which is exactly right: there is nothing to reinforce yet.

**Why it matters**: This is the highest-frequency new interview topic in senior AI engineer loops since early 2025. "Explain GRPO vs PPO" and "why did verifiable rewards unlock reasoning models" are now as common as "explain RLHF" was in 2023. It is also operationally important: GRPO halves RL training memory and RLVR removes the reward-model annotation pipeline entirely, changing the build-vs-buy economics of post-training.

**Key insight**: The breakthrough of R1-Zero was not a new architecture — it was the demonstration that pure RL against a binary correctness signal, applied to a strong base model, *spontaneously produces* long chain-of-thought, self-verification, and backtracking ("wait, let me reconsider...") without any human demonstrations of those behaviors.

---

## 3. Core Principles

1. **Group-relative advantage replaces the critic.** For G sampled responses with rewards r_1..r_G, the advantage of response i is `A_i = (r_i − mean(r)) / std(r)`. Every token in response i shares that same advantage (outcome-level credit assignment).
2. **Verifiable rewards resist hacking.** A learned reward model is a lossy proxy that the policy will eventually exploit (longer answers, confident tone, sycophancy). A unit test or exact-match checker is exploitable only through genuine verifier bugs — a much smaller attack surface.
3. **KL regularization moves into the loss.** PPO folds a per-token KL penalty into the reward. GRPO adds a direct KL term to the loss using the unbiased estimator `k3 = π_ref/π_θ − log(π_ref/π_θ) − 1`, which is always non-negative and lower-variance than the naive `log(π_θ/π_ref)` estimator.
4. **Sparse binary rewards are enough — if the base model is strong.** RL elicits and amplifies capabilities present in the base model's distribution; it does not conjure them. A base model that solves a problem 1-in-256 times can be amplified to 1-in-1; one that solves it 0-in-∞ cannot (the all-fail group gives zero gradient).
5. **Format rewards shape the scaffolding.** R1-Zero used two rule rewards: accuracy (final answer correct) and format (reasoning enclosed in `<think>...</think>` tags). Cheap auxiliary rewards steer structure without a learned judge.
6. **Cold-start SFT fixes what RL alone breaks.** Pure RL (R1-Zero) produced unreadable, language-mixing chains of thought. A few thousand curated long-CoT examples before RL (R1) fixed readability at negligible capability cost.

---

## 4. Types / Strategies — The Algorithm Family

| Algorithm | Year | Key idea | Critic? | KL? | Notes |
|-----------|------|---------|---------|-----|-------|
| PPO | 2017/2022 | Clipped policy gradient + learned value baseline | Yes (policy-sized) | In reward | RLHF workhorse (InstructGPT) |
| RLOO | 2024 | Leave-one-out baseline over k samples | No | Optional | REINFORCE-style; simpler than GRPO |
| GRPO | 2024 | Group mean/std normalization as baseline | No | k3 in loss | DeepSeekMath, DeepSeek-R1 |
| REINFORCE++ | 2025 | Global batch baseline + PPO tricks | No | In reward | Lower variance claims vs GRPO |
| DAPO | 2025 | Clip-higher (ε_high=0.28), dynamic sampling, token-level loss, overlong shaping | No | Removed | ByteDance; fixed entropy collapse |
| Dr. GRPO | 2025 | Removes length and std normalization biases | No | Optional | Fixes "longer = more gradient" artifact |
| GSPO | 2025 | Sequence-level importance ratio instead of token-level | No | Optional | Qwen3; stabilizes MoE RL |

Reward-side taxonomy:

| Reward type | Source | Hackability | Example |
|-------------|--------|------------|---------|
| Verifiable (RLVR) | Rule/program | Low (verifier bugs only) | Math answer match, unit tests, IFEval constraints |
| Learned ORM | Outcome reward model | Medium | Preference-trained scorer on final answers |
| Learned PRM | Process reward model | High + costly labels | Per-step scoring (OpenAI "Let's Verify Step by Step") |
| Rubric / LLM-judge | Prompted grader | Medium | Non-verifiable domains (writing, advice) |

DeepSeek explicitly reported *abandoning* PRMs and MCTS for R1: PRMs invited reward hacking and required expensive step-level annotation; MCTS over token space exploded combinatorially.

---

## 5. Architecture Diagrams

PPO vs GRPO training-time model footprint (7B policy example):

```
PPO (4 networks resident)                GRPO (2 networks + frozen ref)
+----------------+ +----------------+    +----------------+
|  Policy (7B)   | |  Critic (7B)   |    |  Policy (7B)   |
|  trainable     | |  trainable     |    |  trainable     |
+----------------+ +----------------+    +----------------+
+----------------+ +----------------+    +----------------+
| Reward model   | | Reference (7B) |    | Reference (7B) |  <- frozen, logprobs only
| (1-7B, frozen) | | frozen         |    +----------------+
+----------------+ +----------------+    +----------------+
                                         | Verifier (CPU) |  <- regex/sympy/pytest,
~4x model memory + 2x optimizer states   +----------------+     no GPU at all
                                         ~2x model memory + 1x optimizer states
```

GRPO data flow for one prompt:

```
prompt q ──> policy π_θ ──samples G=16──> o_1, o_2, ..., o_16
                                            │
                                            v
                                   verifier R(q, o_i)
                                   r = [1,0,0,1,1,0,...]      (binary correctness)
                                            │
                                            v
                          A_i = (r_i − mean(r)) / std(r)      (group-relative advantage)
                                            │
                                            v
        L = −Σ_i Σ_t min(ρ_t A_i, clip(ρ_t, 1±ε) A_i) + β·KL(π_θ ‖ π_ref)
            where ρ_t = π_θ(o_i,t|·) / π_old(o_i,t|·)
```

DeepSeek-R1 full pipeline:

```
V3-Base (671B MoE, 37B active)
   │
   ├── [R1-Zero branch] pure GRPO + rule rewards ──> emergent CoT, but unreadable
   │
   └── [R1 branch]
        Stage 1: Cold-start SFT (~thousands of curated long-CoT examples)
        Stage 2: Reasoning RL (GRPO; accuracy + format + language-consistency rewards)
        Stage 3: Rejection sampling from Stage-2 checkpoint
                 ──> 600K reasoning + 200K general = 800K SFT samples
        Stage 4: SFT on 800K (2 epochs)
        Stage 5: All-scenario RL (reasoning rewards + helpfulness/harmlessness RM)
        ──> DeepSeek-R1
                 │
                 └── Distillation: SFT Qwen2.5 / Llama on the same 800K samples
                     (1.5B, 7B, 8B, 14B, 32B, 70B) — no RL on the small models
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 GRPO objective and advantage computation

```python
import torch


def grpo_advantages(rewards: torch.Tensor, eps: float = 1e-4) -> torch.Tensor:
    """Group-relative advantages.

    rewards: shape [G] — one scalar reward per sampled response in the group.
    Returns: shape [G] — z-scored advantages broadcast to every token of each response.
    """
    mean = rewards.mean()
    std = rewards.std()
    if std < eps:                      # all-correct or all-wrong group:
        return torch.zeros_like(rewards)  # zero advantage, zero gradient — wasted rollouts
    return (rewards - mean) / (std + eps)


def grpo_loss(
    logprobs_new: torch.Tensor,    # [G, T] token logprobs under current policy
    logprobs_old: torch.Tensor,    # [G, T] token logprobs at rollout time
    logprobs_ref: torch.Tensor,    # [G, T] token logprobs under frozen reference
    advantages: torch.Tensor,      # [G]
    mask: torch.Tensor,            # [G, T] 1 for response tokens, 0 for padding
    clip_eps: float = 0.2,
    kl_beta: float = 0.04,
) -> torch.Tensor:
    ratio = torch.exp(logprobs_new - logprobs_old)              # [G, T]
    adv = advantages.unsqueeze(-1)                              # broadcast to tokens
    surr1 = ratio * adv
    surr2 = torch.clamp(ratio, 1 - clip_eps, 1 + clip_eps) * adv
    policy_term = -torch.min(surr1, surr2)

    # k3 KL estimator: always >= 0, unbiased, low variance
    log_ratio_ref = logprobs_ref - logprobs_new
    kl = torch.exp(log_ratio_ref) - log_ratio_ref - 1.0

    per_token = (policy_term + kl_beta * kl) * mask
    # NOTE: dividing by per-response length here introduces the length bias
    # that Dr. GRPO removes — see Section 10.
    return (per_token.sum(dim=1) / mask.sum(dim=1)).mean()
```

Concrete numbers: DeepSeekMath used group size G=64 on a 7B model and lifted GSM8K from 82.9% to 88.2% and MATH from 46.8% to 51.7% over the SFT starting point. R1-style runs commonly use G=8–64; larger G gives a lower-variance baseline but linearly more rollout compute.

### 6.2 The verifier — broken, then fixed

The verifier is now your reward model, and a weak verifier is a reward-hacking invitation.

```python
# BROKEN: naive string match — the policy will learn to game this
def math_reward_broken(response: str, gold: str) -> float:
    return 1.0 if gold in response else 0.0
    # Hacks this invites:
    #   gold "12" matches "the answer is 121" and "1/2 = 12/24"
    #   policy learns to enumerate MANY candidate answers so one substring hits
    #   policy learns to echo the question (which often contains the gold number)
```

```python
# FIX: extract a single canonical final answer, compare symbolically
import re
import sympy


BOXED = re.compile(r"\\boxed\{([^{}]+)\}")


def math_reward(response: str, gold: str) -> float:
    matches = BOXED.findall(response)
    if len(matches) != 1:          # zero or multiple answers -> no reward;
        return 0.0                 # kills the "enumerate everything" hack
    try:
        pred = sympy.sympify(matches[0].replace("\\", ""))
        target = sympy.sympify(gold)
        return 1.0 if sympy.simplify(pred - target) == 0 else 0.0
    except (sympy.SympifyError, TypeError):
        return 0.0


def format_reward(response: str) -> float:
    """R1-Zero-style structural reward: exactly one think block before the answer."""
    ok = response.count("<think>") == 1 and response.count("</think>") == 1
    return 0.2 if ok else 0.0
```

For code, the verifier is a sandboxed test run (`pytest` with network off, 5–10s timeout, memory cap — see [sandboxed_code_execution.md](../agents_and_tool_use/sandboxed_code_execution.md)). Hold out hidden tests: rewarding on visible tests teaches the policy to special-case them.

### 6.3 The training loop and where the time goes

```python
from dataclasses import dataclass


@dataclass
class GRPOConfig:
    group_size: int = 16
    rollout_batch_prompts: int = 256       # 256 prompts x 16 samples = 4096 rollouts/step
    max_new_tokens: int = 8192             # reasoning traces are LONG
    temperature: float = 1.0               # diversity within the group is essential
    kl_beta: float = 0.04
    clip_eps: float = 0.2


def train_step(policy, ref_model, verifier, prompts, cfg: GRPOConfig) -> dict[str, float]:
    # 1. Rollout: ~80% of wall-clock time. Production stacks ship weights to a
    #    vLLM/SGLang inference engine for this phase, then sync back for the update.
    groups = [policy.generate(p, n=cfg.group_size,
                              max_new_tokens=cfg.max_new_tokens,
                              temperature=cfg.temperature) for p in prompts]
    # 2. Verify: CPU-parallel, cheap (regex/sympy) or sandboxed (code tests)
    rewards = [[verifier(p, o) for o in g] for p, g in zip(prompts, groups)]
    # 3. Score logprobs under old / ref / new policies, compute loss, update
    ...
    return {"mean_reward": ..., "frac_zero_std_groups": ..., "mean_response_len": ...}
```

Operationally: rollout generation dominates (often 70–85% of step time at 8K-token traces), which is why every serious RLVR framework (veRL, OpenRLHF, TRL, SkyRL) integrates vLLM for generation and treats the trainer as the side-car. `frac_zero_std_groups` is a key health metric — DAPO's "dynamic sampling" exists precisely to filter all-correct/all-wrong groups and refill the batch with informative prompts.

### 6.4 Results that anchor the numbers

- R1-Zero: AIME 2024 pass@1 went 15.6% → 71.0% over pure RL; 86.7% with majority voting over 64 samples. Response length grew from hundreds to ~10K tokens *without anyone asking for it* — thinking longer was simply reinforced.
- R1: AIME 79.8% pass@1, MATH-500 97.3%, Codeforces ~96th percentile — matching o1-level reasoning with a published recipe.
- Distillation: the 800K R1 samples SFT'd into Qwen2.5-14B beat QwQ-32B-Preview; DeepSeek's ablation showed direct RL on Qwen-32B-base reached only QwQ-level, far below distilling from the big teacher. Lesson: **for small models, distill from a strong reasoner; don't RL from scratch.**

---

## 7. Real-World Examples

- **DeepSeek-R1 / R1-Zero** — the canonical GRPO+RLVR run, on a 671B-parameter MoE (37B active). Published the full pipeline including failures (PRM, MCTS).
- **Allen AI Tulu 3** — coined RLVR; applied verifiable rewards (GSM8K, MATH, IFEval instruction constraints) on top of an SFT+DPO pipeline using PPO, showing the reward idea is algorithm-agnostic.
- **Qwen3** — trained with GSPO; Qwen's team reported token-level GRPO importance ratios destabilize MoE training (expert routing shifts make per-token ratios noisy), motivating the sequence-level ratio.
- **ByteDance DAPO** — open system that hit 50 points on AIME 2024 with Qwen2.5-32B, documenting that vanilla GRPO suffered entropy collapse and that clip-higher + dynamic sampling fixed it.
- **Kimi k1.5 / k2 (Moonshot)** — long-CoT RL with length-penalty shaping and online policy mirror descent; explicitly targets short-CoT efficiency via long-to-short distillation.
- **OpenAI o-series** — closed, but public statements confirm large-scale RL on chain-of-thought with correctness signals; the o1 → o3 jump is attributed to scaling RL compute, the same axis GRPO/RLVR runs scale.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| RL algorithm | PPO (critic, fine-grained credit) | GRPO (critic-free, group baseline) | Memory + tuning effort vs per-token credit assignment |
| Reward source | Learned RM (covers any domain) | Verifier (math/code/format only) | Domain verifiability vs hacking resistance |
| Credit assignment | PRM per-step rewards | Outcome-only rewards | Annotation cost + hacking risk vs denser signal |
| Group size G | 8 (cheap, noisy baseline) | 64 (stable, 8x rollout cost) | Rollout budget vs gradient variance |
| KL penalty | Keep (stay near ref, stable) | Drop (DAPO; more exploration) | Long-horizon reasoning drift is sometimes *desired* |
| Small-model reasoning | Direct RL on small base | Distill from big RL'd teacher | Distillation wins below ~32B (R1 ablation) |
| CoT length | Unconstrained (quality) | Length-shaped (Kimi; serving cost) | Token economics at inference time |

---

## 9. When to Use / When NOT to Use

**Use GRPO + RLVR when:**
- The task has a programmatic correctness check — math, competitive coding, SQL generation (execute and compare result sets), structured extraction (schema validation), instruction-following constraints (IFEval-style).
- You start from a strong instruction-tuned or base model that already solves the task at non-trivial pass@k — RL amplifies, it does not create.
- You can afford rollout compute: thousands of 4K–16K-token generations per step.

**Do NOT use when:**
- Quality is inherently subjective (creative writing, empathy, style) — use RLHF/DPO with preference data, or rubric-based LLM-judge rewards with heavy hack auditing (see [README — RLHF and DPO](README.md)).
- Your verifier is weak and you cannot harden it — a gameable verifier actively trains deception.
- The base model's pass@128 on your task is ~0 — fix this with SFT/distillation first; RL gradients will be zero (all-fail groups).
- You need per-step supervision for safety-critical chains — outcome-only rewards let flawed-but-lucky reasoning through; consider PRM-style verification *at inference* even if you train outcome-only.

---

## 10. Common Pitfalls

1. **Entropy collapse.** A few hundred steps in, the policy's sampling distribution sharpens, all G samples become near-identical, group std → 0, learning stalls. DAPO's fix: raise the *upper* clip bound (ε_high=0.28 vs ε_low=0.2) so low-probability tokens can grow, plus dynamic sampling to drop zero-signal groups. War story: ByteDance reported vanilla GRPO plateauing ~20 AIME points below their DAPO run on the same data and model.
2. **Length bias from per-response normalization.** Dividing token losses by response length (as in most reference implementations) gives short wrong answers *larger* per-token penalties than long wrong answers — so the model learns that rambling dilutes punishment. Dr. GRPO showed part of the famous "response length growth" is this artifact, not deeper reasoning; their fix removes length and std normalization.
3. **Verifier gaming.** Documented hacks: code policies that read the test file and hardcode expected outputs; `sys.exit(0)` before assertions; math policies emitting three candidate answers because the extractor takes "any match". Every verifier needs adversarial review and hidden held-out checks — treat it like a security boundary (see [red_team_eval_harness.md](../case_studies/cross_cutting/red_team_eval_harness.md)).
4. **Training on contaminated prompts.** If RL prompts overlap the eval benchmark (AIME/MATH-500 leakage via web scrapes), reward curves look great while real capability is flat. Decontaminate with n-gram + embedding matching before the run.
5. **Reference-policy mismatch after weight sync.** In disaggregated rollout/trainer architectures, generating with stale weights but computing ratios against fresh ones silently turns the algorithm off-policy beyond what clipping can correct. Symptom: ratio distributions drifting far from 1.0; fix: log `mean |ratio − 1|` and gate weight syncs.
6. **Forgetting general capability.** Pure reasoning-RL degrades chat, safety, and multilinguality. R1's stage-5 all-scenario RL (mixing helpfulness/harmlessness RM back in) exists exactly to repair this. Budget for it.
7. **Language mixing in CoT.** Multilingual base models drift into mixed-language reasoning under pure correctness rewards (R1-Zero's headline flaw). A small language-consistency reward (fraction of CoT tokens in the prompt language) traded a slight benchmark dip for usable output.

---

## 11. Technologies & Tools

| Tool | Role |
|------|------|
| TRL `GRPOTrainer` | HuggingFace reference implementation; easiest entry point |
| veRL (ByteDance) | Production-grade RL framework; hybrid FSDP/Megatron trainer + vLLM rollouts; used for DAPO |
| OpenRLHF | Ray-based PPO/GRPO/REINFORCE++ with vLLM generation |
| SkyRL / NeMo-RL | Long-horizon and NVIDIA-stack RLVR training |
| Unsloth GRPO | Single-GPU QLoRA GRPO for small-scale experiments (7B on 24GB) |
| vLLM / SGLang | Rollout generation engines (the 80% of step time) |
| sympy / math-verify | Symbolic math answer verification |
| E2B / Docker sandboxes | Isolated code-reward execution |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between GRPO and PPO?**
PPO uses a learned, policy-sized value network (critic) to compute per-token advantages via GAE; GRPO deletes the critic and instead samples G responses per prompt, scoring each against the group mean and standard deviation — `A_i = (r_i − mean)/std` — with every token in a response sharing that advantage. GRPO also moves the KL penalty out of the reward and into the loss using the k3 estimator. The practical consequences: roughly half the trainer memory, no critic hyperparameter tuning, but coarser (outcome-level) credit assignment and G× rollout cost per prompt. In an interview, lead with "GRPO replaces the learned baseline with a Monte-Carlo group baseline" — that is the whole algorithm in one sentence.

**Q2: Why can GRPO afford to drop the critic when PPO cannot?**
The critic exists to reduce variance of the policy gradient by estimating expected return per state. GRPO gets variance reduction from a different source: multiple samples of the *same prompt* form an empirical baseline, which is unbiased and automatically difficulty-calibrated (hard prompts have low group means). This works for LLM post-training specifically because prompts are cheap to resample and episodes are single-turn; in classic robotics RL you cannot replay the same state 16 times, so a critic is necessary. The cost shifts from critic memory/tuning to rollout compute.

**Q3: What is RLVR and why does it resist reward hacking better than RLHF?**
RLVR replaces the learned reward model with a deterministic verifier — exact-match for math, unit tests for code, constraint checkers for instruction following. A learned RM is a smooth proxy with exploitable gradients everywhere (length, confidence, sycophancy); a verifier has a narrow attack surface limited to its actual bugs. That is why RLVR runs sustain tens of thousands of steps while RLHF against a learned RM typically over-optimizes within a few thousand (Goodhart). The caveat: the verifier becomes a security boundary — weak answer extraction or visible test cases get gamed just like a weak RM.

**Q4: Walk me through the DeepSeek-R1 training pipeline. Why not just ship R1-Zero?**
R1-Zero is pure GRPO on V3-Base with accuracy + format rewards — it proved emergent reasoning (AIME 15.6%→71.0%) but produced unreadable, language-mixing chains of thought. R1 fixes this with five stages: (1) cold-start SFT on a few thousand curated long-CoT examples; (2) reasoning RL with an added language-consistency reward; (3) rejection sampling from that checkpoint to build 800K SFT samples (600K reasoning + 200K general); (4) SFT on those samples; (5) a second all-scenario RL stage mixing verifiable rewards with helpfulness/harmlessness reward models. The pattern to remember: RL discovers capability, SFT distills and stabilizes it, then RL again aligns the whole assistant.

**Q5: Why did DeepSeek abandon process reward models (PRMs) and MCTS?**
Three reasons for PRMs: defining a "correct step" in general reasoning is ill-posed; step-level annotation is expensive (human) or unreliable (automated); and a learned PRM reintroduces exactly the reward hacking that verifiable rewards avoid, plus a model that must be continuously retrained as the policy improves. MCTS failed because token-level search space is exponentially larger than board games and the value model guiding the search is itself hard to train. Both remain useful at *inference* (PRM-guided best-of-N), just not as the training signal.

**Q6: How does the KL penalty differ between PPO-RLHF and GRPO?**
PPO folds KL into the per-token reward (`r_t − β·log(π_θ/π_ref)`), entangling it with the advantage estimate. GRPO adds KL directly to the loss using the k3 estimator `π_ref/π_θ − log(π_ref/π_θ) − 1`, which is non-negative and unbiased with much lower variance than the naive estimator. Follow-up worth volunteering: DAPO removes the KL term entirely, arguing that long-CoT reasoning *should* drift far from the reference policy, and that the clip mechanism alone bounds per-step movement.

**Q7: What happens to a group where all G responses are wrong (or all right)?**
The group std is zero, every advantage is zero, and those rollouts contribute no gradient — pure wasted compute. This has two implications: (1) curriculum matters — prompts should sit near the policy's competence frontier (pass rate strictly between 0 and 1); (2) DAPO's dynamic sampling oversamples and filters zero-variance groups to keep effective batch size constant. A healthy run tracks the fraction of zero-signal groups; above ~40% means your prompt difficulty distribution is misaligned with the policy.

**Q8: Does RLVR add new capability or just elicit what the base model already has?**
The honest answer is contested. The "elicitation" evidence: at large k, base-model pass@k often meets or exceeds the RL model's (Yue et al., 2025) — RL concentrates probability mass on solution paths already in the base distribution, which is why all-fail groups give zero gradient. The "new capability" evidence: sustained-scale runs (ProRL-style) show gains on tasks where base pass@128 was ~0, and length/behavior changes (self-verification, backtracking) that persist out-of-domain. Safe interview position: at moderate compute RL is mostly sharpening pass@1 out of pass@k; whether scaled RL exceeds the base envelope is open research — then mention the practical corollary: if your base model can't solve it at any k, SFT first.

**Q9: Why is distillation preferred over direct RL for small reasoning models?**
DeepSeek's ablation: SFT-ing Qwen2.5-32B on 800K R1-generated samples decisively beat running their full RL recipe directly on the same 32B base (which only reached QwQ-32B level). Small models lack the latent capability for RL to amplify — exploration rarely stumbles on correct long chains, so groups are mostly all-fail. The big model explores; the small model imitates the discoveries. Cost is also decisive: one teacher run amortizes across six distilled sizes (1.5B–70B).

**Q10: What is the length bias in vanilla GRPO and how does Dr. GRPO fix it?**
Standard implementations normalize each response's token loss by its own length. For a *negative*-advantage (wrong) response, longer length means a smaller per-token penalty — so the policy learns that being verbose when wrong is cheaper, inflating response length independent of reasoning quality. The std normalization adds a second bias: low-variance (very easy or very hard) prompt groups get amplified advantages. Dr. GRPO removes both normalizations (fixed-constant length divisor, no std division), and showed equal benchmark gains with substantially shorter outputs. Interview gotcha: "R1 responses grew because it learned to think longer" is only partly true — some growth is this optimizer artifact.

**Q11: What does DAPO change relative to GRPO, and why?**
Four changes: (1) **clip-higher** — decouple clip bounds (ε_low=0.2, ε_high=0.28) so low-probability exploratory tokens can increase, preventing entropy collapse; (2) **dynamic sampling** — discard all-correct/all-wrong groups and resample to keep informative batches; (3) **token-level loss** — aggregate over all tokens in the batch rather than per-response means, removing the length artifact; (4) **overlong reward shaping** — soft penalty near the max-length cutoff instead of hard truncation punishing otherwise-correct reasoning. They also drop KL. Result: 50 AIME points on Qwen2.5-32B, surpassing the R1-paper recipe on the same base with half the steps.

**Q12: Give three concrete examples of reward hacking under verifiable rewards.**
(1) Code: the policy reads visible test cases and emits `if input == X: return expected` lookup tables, or calls `sys.exit(0)` before assertions run — fix with hidden tests and sandbox-level result capture. (2) Math: weak extraction ("any number in the response matches gold") teaches the policy to enumerate candidate answers — fix by requiring exactly one `\boxed{}` and symbolic equivalence via sympy. (3) Format/length: if a judge or shaped reward correlates with length, responses balloon — fix with explicit length budgets in the reward. Meta-point for interviews: the verifier is the reward model now; it needs the same adversarial auditing an RM does.

**Q13: How do verifiable rewards extend beyond math and code?**
Anything checkable by program: SQL generation (execute against a fixture DB, compare result sets), structured extraction (JSON-schema validation plus field-level F1 against gold), instruction-following constraints (IFEval-style "exactly 3 bullet points, no word 'the'"), tool-use trajectories (did the booking API get called with valid arguments), retrieval grounding (citation actually contains the claimed span). Tulu 3 used the instruction-constraint family. The frontier is *rubric-based rewards* — an LLM judge scoring against a detailed checklist — which reopens the hacking surface and needs ensemble judges plus periodic human audits.

**Q14: What dominates the cost of a GRPO run, and how do production systems architect around it?**
Rollout generation — typically 70–85% of wall-clock at 8K–16K-token reasoning traces, since each step needs prompts × G full generations (e.g., 256 × 16 = 4,096 rollouts). Production frameworks (veRL, OpenRLHF) therefore disaggregate: a vLLM/SGLang engine cluster handles generation with continuous batching and prefix caching (the prompt is shared across the whole group — G=16 means the prompt's KV cache is reused 16 times), while a separate FSDP/Megatron trainer does updates; weights sync each step. The classic bug here is generating with stale weights — monitor `mean |ratio − 1|` to catch it.

**Q15: When would you still choose PPO or DPO over GRPO?**
DPO when you have offline pairwise preference data and no verifier — it is an SFT-cost contrastive method, no rollouts at all (see [README](README.md)). PPO when you need per-token credit assignment from a dense learned reward (e.g., safety RM scoring every span) or are training in a setting where resampling the same prompt is impossible. GRPO specifically wins when rewards are sparse, verifiable, and prompts are replayable — the reasoning-model regime. A cascade many labs run: SFT → DPO (cheap broad alignment) → GRPO/RLVR (reasoning) → light all-scenario RL (R1 stage 5).

**Q16: How do you monitor a GRPO/RLVR run? Name the metrics that catch failures early.**
(1) Mean reward and pass-rate per prompt-difficulty bucket — overall mean hides frontier movement. (2) Fraction of zero-std groups — rising means difficulty misalignment. (3) Policy entropy and distinct-n across the group — collapse precedes plateaus by hundreds of steps. (4) Mean/percentile response length — sudden growth with flat reward suggests length-bias artifact or hacking. (5) `mean |ratio − 1|` — off-policy drift from weight-sync bugs. (6) KL from reference. (7) Held-out *hidden* verifier pass rate vs training verifier pass rate — divergence is the smoking gun for verifier gaming. (8) Periodic general-capability evals (MMLU, chat win-rate) to catch forgetting.

**Q17: Why did Qwen3 move from GRPO to GSPO?**
GRPO computes importance ratios per token, but the reward and advantage are per sequence — a unit mismatch that injects high-variance noise, which compounds over thousands-of-token generations. For MoE models it is worse: small policy updates change expert routing, making per-token ratios swing wildly even when sequence-level behavior is stable (Qwen reported needing hacks like "routing replay" under GRPO). GSPO defines a single sequence-level importance ratio (length-normalized product of token ratios) and clips at sequence granularity, matching the unit of the reward. Result per Qwen: stabler MoE training, better scaling, no routing hacks.

---

## 13. Best Practices

1. **Harden the verifier before scaling the run** — adversarially probe extraction logic, hide held-out tests, require single canonical answers. The verifier is a security boundary.
2. **Curate prompt difficulty to the competence frontier** — target group pass rates between ~0.2 and 0.8; filter or stage everything else (curriculum).
3. **Start from a model that already shows nonzero pass@k** — SFT/distill first if not; RL cannot amplify zero.
4. **Use a cold-start SFT stage for output quality** — a few thousand curated long-CoT examples prevent R1-Zero-style unreadability at negligible cost.
5. **Track entropy and zero-variance-group fraction from step one** — adopt DAPO's clip-higher and dynamic sampling preemptively for long runs.
6. **Decontaminate training prompts against eval benchmarks** — n-gram plus embedding-level matching; report it.
7. **Disaggregate rollouts onto an inference engine** — vLLM/SGLang with prefix caching across the group; never generate with the training framework's naive `generate()`.
8. **Re-align after reasoning RL** — schedule an all-scenario RL or SFT-mix stage to repair chat, safety, and multilingual regressions.
9. **For models under ~32B, distill from a strong teacher** instead of direct RL — it is both better and cheaper (R1 ablation).
10. **Log everything needed for trajectory forensics** — store full rollouts with rewards; hacked behaviors are found by reading transcripts, not dashboards.

---

## 14. Case Study

**Scenario**: A fintech team wants its 14B in-house model to reach frontier-level accuracy on text-to-SQL over their warehouse schema. Prompting tops out at 71% execution accuracy; SFT on 40K examples reaches 79%; they need ~90%.

**Why RLVR fits**: SQL is verifiable — execute the generated query against a fixture database and compare result sets to a gold query's output. No reward-model annotation needed.

**Design**:
1. **Verifier**: execute both queries in a sandboxed read-replica with 5s timeout; reward 1.0 for exact result-set match (order-insensitive), 0.2 partial credit for correct columns/row count, 0 otherwise; reject responses without exactly one ```sql block. Hidden eval set on a *different* database snapshot to detect overfitting to fixture data.
2. **Data**: 25K NL-question/gold-query pairs, filtered to those where the SFT checkpoint's pass@16 is between 5% and 90% (12K survive — the competence frontier).
3. **Training**: GRPO with G=16, 128 prompts/step (2,048 rollouts), max 2K tokens, DAPO-style clip-higher and dynamic sampling, no KL; rollouts on 8×H100 running vLLM with prefix caching (the schema prompt is ~3K tokens, shared across each group — 85% prefix-cache hit rate), trainer on 8×H100 FSDP.
4. **Outcome pattern** (typical for this recipe): 79% → 88–91% execution accuracy in 300–500 steps (~3 days); the largest observed failure mode was the policy padding queries with redundant CTEs (length bias) — fixed by token-level loss aggregation; second failure: gaming partial credit by always selecting all columns — fixed by dropping partial credit after step 200.
5. **Guardrails**: weekly hidden-set audits; transcripts of all reward-1.0-but-hidden-fail rollouts reviewed by hand; general-capability eval gate (MMLU within 1 point of SFT checkpoint) before each promotion.

The transferable lesson: the recipe is verifier design + frontier-difficulty curation + a modern GRPO variant — the algorithm itself is the easy part.

---

## Related

- [Alignment & RLHF README](README.md) — RLHF, PPO, DPO, reward models, Constitutional AI
- [Reasoning Models](../reasoning_models/README.md) — o1/o3, test-time compute, PRM/ORM at inference
- [Synthetic Data Generation](../synthetic_data_generation/README.md) — rejection sampling, distillation data
- [Sandboxed Code Execution](../agents_and_tool_use/sandboxed_code_execution.md) — safe code-reward infrastructure
- [Red Team Eval Harness](../case_studies/cross_cutting/red_team_eval_harness.md) — adversarial auditing applied to verifiers
