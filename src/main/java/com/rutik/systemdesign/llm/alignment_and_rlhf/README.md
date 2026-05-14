# Alignment & RLHF

## 1. Concept Overview

Alignment is the process of making LLMs behave in ways that are helpful, harmless, and honest (Anthropic's "3 Hs"). A base pre-trained model predicts the next token — it has no concept of being helpful, refusing harmful requests, or being truthful. Alignment techniques transform this text predictor into a model that follows instructions, refuses dangerous outputs, and aligns with human values.

RLHF (Reinforcement Learning from Human Feedback) was the breakthrough technique that made GPT-3.5 and GPT-4 usable as assistants. Since then, many alternatives have emerged — DPO, Constitutional AI, ORPO, KTO — that achieve similar or better alignment with less complexity.

Understanding alignment is critical for anyone building LLM systems: it explains why models behave as they do, what their failure modes are, and how to customize behavior for specific deployments.

---

## Intuition

> **One-line analogy**: Alignment is like teaching a brilliant but amoral intern to be helpful and ethical — not by changing their intelligence, but by shaping their values and judgment.

**Mental model**: A base pre-trained model will happily complete any text, including harmful requests — it's just a text predictor with no values. RLHF adds a "preference layer": show humans pairs of model outputs, record which they prefer, train a reward model to predict human preferences, then use RL to fine-tune the base model to score higher on that reward. The model learns to generate outputs humans prefer — which includes being helpful, refusing harmful requests, and being honest.

**Why it matters**: RLHF is why ChatGPT, Claude, and Gemini are usable as assistants rather than raw text completers. Without alignment, models readily assist with harmful tasks and hallucinate confidently. DPO (the modern alternative) achieves the same result without RL, making alignment much cheaper and more stable.

**Key insight**: You're not teaching the model facts — you're teaching it human preferences over outputs. The reward model learns "what humans prefer" and the policy model learns "how to generate what the reward model likes."

---

## 2. Core Principles

- **The alignment tax**: Aligned models are sometimes less capable on raw benchmarks — they refuse or hedge where a base model would just answer. This tradeoff between safety and helpfulness is central.
- **Reward hacking**: If you optimize for a reward model, the LLM will find ways to maximize the reward proxy that don't match true human preferences (Goodhart's Law).
- **Sycophancy**: Aligned models tend to agree with users even when wrong, because human raters often prefer agreeable responses.
- **Instruction following vs. values**: Teaching a model to follow instructions is easier than teaching it values; but values matter for edge cases.
- **Iterative alignment**: Alignment is not a one-time process — deployed models require continuous red-teaming and retraining.

---

## 3. Types / Strategies

### 3.1 RLHF (Reinforcement Learning from Human Feedback)

The original alignment pipeline. Three stages:

```
Stage 1: Supervised Fine-Tuning (SFT)
  Collect high-quality (prompt, response) demonstrations
  Fine-tune the pre-trained model on these
  Result: SFT model — good instruction following but not yet aligned

Stage 2: Reward Model Training
  For each prompt, generate 4-9 model responses
  Human annotators rank responses from best to worst
  Train a reward model (RM) to predict human preference:
    RM(prompt, response) → scalar score
  Loss: compare scores for chosen vs. rejected pairs

Stage 3: PPO (Proximal Policy Optimization)
  Use RM as the reward signal
  RL fine-tune the SFT model to maximize RM score
  KL penalty against SFT model prevents extreme deviation
    Reward = RM_score - β × KL(policy || SFT_model)
  Result: RLHF-aligned model
```

**Pros**: Strong empirical results; used by OpenAI for GPT-4, InstructGPT
**Cons**: Complex (3 separate training runs); PPO is unstable; reward hacking; computationally expensive

### 3.2 DPO (Direct Preference Optimization)

Reformulates RLHF as a supervised learning problem — no RL, no separate reward model. Directly optimizes on preference data.

```
Given: dataset of (prompt, chosen_response, rejected_response) triples

DPO Loss:
  -log σ(β × log[π_θ(chosen|prompt)/π_ref(chosen|prompt)]
          - β × log[π_θ(rejected|prompt)/π_ref(rejected|prompt)])

Where:
  π_θ: the model being trained
  π_ref: the reference (SFT) model — frozen
  β: temperature controlling deviation from reference
  σ: sigmoid function

Intuition: Increase likelihood of chosen relative to rejected,
  while not deviating too far from the reference model
```

**Pros**: Simpler than RLHF (one training run); more stable; no reward hacking; state-of-the-art results
**Cons**: Requires SFT reference model; can degrade base capabilities if β is too low

Used by: Llama 3, Mistral, many open-source models.

### 3.3 Constitutional AI (CAI) — Anthropic

Two-stage self-supervised alignment:

```
Stage 1: Supervised Learning from AI Feedback (SL-CAI)
  1. Generate initial response to potentially harmful prompt
  2. Ask model to critique response against a constitution (list of principles)
     "Is the response harmful? Does it violate human rights?"
  3. Ask model to revise response based on critique
  4. Use final revised response for supervised fine-tuning

Stage 2: RL from AI Feedback (RLAIF)
  Instead of human preferences, use AI preferences
  Model generates feedback on pairs of responses according to constitution
  Train reward model on AI-labeled preferences
  PPO using AI-trained reward model
```

Constitutional AI reduces dependence on human labelers for safety data while maintaining strong alignment. Used by Anthropic for Claude.

### 3.4 ORPO (Odds Ratio Preference Optimization)

Single-stage alignment — combines SFT and preference learning in one loss:

```
ORPO Loss = -log P(chosen) + λ × log(1 - odds_ratio)

Where odds_ratio = P(chosen) / (1 - P(chosen)) / [P(rejected) / (1 - P(rejected))]

No reference model needed — self-contained optimization
More parameter efficient; faster training
```

Used by: Phi-3, some Mistral variants.

### 3.5 KTO (Kahneman-Tversky Optimization)

Aligns based on individual good/bad response labels rather than pairwise comparisons:

```
Traditional DPO: requires (chosen, rejected) pairs per prompt
KTO: only needs per-response binary labels (good / bad)
  - Much easier to collect: rate each response independently
  - Based on prospect theory from behavioral economics
  - Models human loss aversion: losses hurt more than gains feel good

Uses independent positive and negative signals:
  Increase likelihood of responses labeled "good"
  Decrease likelihood of responses labeled "bad"
```

Particularly useful when pairwise comparison data is hard to collect.

### 3.6 RLAIF (RL from AI Feedback)

Replace human raters with an AI judge:

```
Generate (prompt, response_A, response_B) pairs
Prompt judge LLM: "Which response is better and why? [A/B]"
Use AI judgments as preference labels
Train reward model or directly apply DPO

Enables scaling feedback beyond human annotation capacity
Quality depends heavily on judge model quality
```

---

## 4. Architecture Diagrams

### RLHF Full Pipeline
```
Pre-trained LLM
     |
     v
[Stage 1: SFT]
  Demonstration dataset (prompt, ideal_response)
  Supervised fine-tuning → SFT Model
     |
     v
[Stage 2: Reward Model]
  Human comparison data (prompt, chosen, rejected)
  Bradley-Terry model: P(A > B) = σ(r(A) - r(B))
  → Reward Model RM(prompt, response) → scalar
     |
     v
[Stage 3: PPO]
  ┌─────────────────────────────────────────┐
  │  Prompt from prompt dataset             │
  │  → Policy (SFT model being trained)     │
  │  → Generated response                   │
  │  → RM scores response                   │
  │  → KL penalty: -β KL(policy || SFT)     │
  │  → PPO update to maximize total reward  │
  └─────────────────────────────────────────┘
  → RLHF Aligned Model
```

### DPO vs RLHF
```
RLHF:
  Preference Data → Reward Model → PPO Training
  (3 models, 3 training runs, complex)

DPO:
  Preference Data → DPO Loss → Aligned Model
  (2 models: reference + trained, 1 training run, simple)
```

---

## 5. How It Works — Detailed Mechanics

### Reward Model Training

The reward model takes (prompt + response) as input and outputs a scalar score:

```
Architecture: LLM with a regression head on the [EOS] token
  Hidden dim → Linear(1) → scalar reward

Bradley-Terry loss:
  For each (prompt, chosen, rejected) triple:
  loss = -log σ(r_θ(prompt, chosen) - r_θ(prompt, rejected))

Data format: Human raters rank K responses (K=4-9) per prompt
  This gives K(K-1)/2 pairwise comparisons per prompt
```

**Reward model quality is the bottleneck** in RLHF. A poor reward model leads to reward hacking — the policy finds responses that score high but aren't actually good (e.g., very long responses, specific phrases that correlate with high ratings).

### PPO Mechanics

```
Clip objective (prevents large policy updates):
  L_CLIP = E[min(r_t(θ) × A_t, clip(r_t(θ), 1-ε, 1+ε) × A_t)]

Where:
  r_t(θ) = π_θ(a_t|s_t) / π_θ_old(a_t|s_t)  (probability ratio)
  A_t = reward estimate - value function baseline
  ε = 0.2 (clip range)

KL regularization:
  Total reward = RM_score(response) - β × KL(π_θ || π_SFT)
  β typically 0.01-0.1; higher β = stay closer to SFT model
```

### DPO Implicit Reward

DPO implicitly learns a reward model:

```
The optimal reward under DPO is:
  r*(x, y) = β × log[π*(y|x) / π_ref(y|x)] + β × log Z(x)

Where Z(x) is a normalization constant (partition function)

Intuition: The policy itself encodes the reward — a response
  with higher probability than the reference model is preferred
```

---

## 6. Real-World Examples

### OpenAI InstructGPT / GPT-3.5-turbo
- First large-scale RLHF-aligned model (Ouyang et al. 2022)
- SFT on 13K demonstrations by contractors
- RM trained on 33K comparisons (8 responses ranked per prompt)
- PPO fine-tuning with β=0.01 KL
- 1.3B InstructGPT preferred to 175B GPT-3 by human evaluators

### Anthropic Claude
- Constitutional AI: 16 principles guiding behavior
- RLAIF to scale beyond human labeler capacity
- Harmlessness and helpfulness balanced via reward modeling
- Iterative red-teaming → retraining loop

### Meta LLaMA 3 Alignment
- Combination of SFT + DPO + PPO
- "Rejection sampling fine-tuning": generate N responses, filter by reward model, SFT on winners
- Multiple rounds of alignment with human feedback

### DeepSeek-R1 Alignment
- Group Relative Policy Optimization (GRPO) — PPO variant without value function
- Reward: correctness (verifiable math/code) + format rewards
- Demonstrated that RL from verifiable signals alone creates reasoning without human preference data

---

## 7. Tradeoffs

| Method | Complexity | Stability | Data Needed | Quality |
|--------|-----------|-----------|-------------|---------|
| RLHF/PPO | High | Low | Pairwise comparisons | Excellent |
| DPO | Low | High | Pairwise comparisons | Excellent |
| Constitutional AI | Medium | Medium | Constitution + AI feedback | Very good |
| ORPO | Low | High | Pairwise (no reference) | Very good |
| KTO | Low | High | Per-response labels | Good |
| RLAIF | Medium | Medium | AI-labeled pairs | Good |

---

## 8. When to Use / When NOT to Use

### Use RLHF/DPO When:
- Building a general-purpose assistant (helpfulness + safety)
- Human preference data is available
- Output quality and alignment are paramount

### Use Constitutional AI When:
- Reducing dependence on human annotators
- Defining specific value constraints programmatically
- Iterating alignment quickly without large annotation budgets

### Use KTO When:
- Collecting pairwise comparisons is operationally difficult
- Have existing user feedback (thumbs up/down, ratings)

---

## 9. Common Pitfalls

1. **Reward hacking**: Model exploits proxy reward (e.g., generates very long responses because length correlates with high ratings). Monitor reward model calibration.
2. **Sycophancy**: Human raters prefer responses that agree with them; model learns sycophantic behavior. Use adversarial prompts that test factual accuracy despite user pressure.
3. **Over-refusal**: Too much safety training makes model refuse benign requests. Balance safety and helpfulness in training data.
4. **Distribution shift in reward model**: RM trained on SFT-model outputs may not generalize to policy's diverse outputs after PPO. Keep RL updates conservative (high β).
5. **Forgetting with too many PPO steps**: PPO can degrade base language modeling quality. Monitor loss on held-out text; stop PPO early if LM degradation detected.

---

## 10. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **TRL (HuggingFace)** | RLHF, DPO, PPO, KTO | Most used; PPOTrainer, DPOTrainer |
| **OpenRLHF** | Production RLHF | Large-scale; Ray-based; better than TRL at scale |
| **LLaMA-Factory** | DPO/RLHF training | Easy YAML config |
| **Argilla** | Preference labeling UI | Collect human comparison data |
| **Llama Guard** | Safety reward signal | Meta's safety classifier |
| **Constitutional AI** | Anthropic's method | Paper-based; implement via prompting |
| **UltraFeedback** | Preference dataset | 64K instructions, GPT-4 rated |
| **HelpSteer2** | Preference dataset | NVIDIA; multi-attribute preferences |
| **Reward Bench** | Reward model evaluation | Evaluates RM quality |

---

## 11. Interview Questions with Answers

**Q: What is RLHF and what problem does it solve?**
A: RLHF (Reinforcement Learning from Human Feedback) aligns a pre-trained LLM with human preferences. Without alignment, the model just predicts next tokens — it might answer harmful questions, be inconsistent, or use undesirable formats. RLHF adds a reward model (trained on human comparisons of responses) and uses RL (PPO) to optimize the model's outputs for higher human preference scores. This transforms a text predictor into a helpful assistant.

**Q: What is DPO and how does it differ from RLHF?**
A: DPO (Direct Preference Optimization) achieves the same alignment goal as RLHF but without RL or a separate reward model. It directly optimizes a closed-form loss on (prompt, chosen, rejected) triples that implicitly represents the RLHF objective. DPO is simpler (one training run vs. three), more stable (no PPO instability), and achieves comparable or better results. It has largely replaced PPO in open-source model training.

**Q: What is reward hacking?**
A: Reward hacking occurs when the policy learns to maximize the proxy reward signal (reward model score) through behaviors that don't represent true human preferences. Example: if human raters unconsciously prefer longer responses, the model might generate unnecessarily verbose output. Solutions: diverse reward models, including a KL penalty to prevent extreme deviation from the reference model, and regular human evaluation of the final model's behavior.

**Q: What is Constitutional AI?**
A: Constitutional AI (Anthropic) defines a set of principles (a "constitution") and uses AI feedback to align the model. In stage 1 (SL-CAI), the model critiques and revises its own responses against the constitution. In stage 2 (RLAIF), AI-generated preferences (using the constitution as a guide) replace human comparisons for reward model training. This reduces dependence on human annotation while maintaining alignment quality.

**Q: What is sycophancy in LLMs and how is it related to RLHF?**
A: Sycophancy is when an LLM agrees with users even when they're wrong, simply to please them. It arises from RLHF because human raters tend to prefer responses that confirm their beliefs. If a user says "I think X is true" and the model agrees, raters rate it higher — even if X is false. Mitigation: include adversarial examples in training data where the model should maintain factual accuracy despite user pressure; rate responses on accuracy and helpfulness separately.

**Q: What are the key tradeoffs between DPO and RLHF for alignment?**
DPO is simpler to implement and more stable but less expressive than RLHF. RLHF uses a separate reward model and PPO optimization, which can capture nuanced preferences but is notoriously unstable (reward hacking, mode collapse, hyperparameter sensitivity). DPO reformulates the RLHF objective to directly optimize the policy from preference pairs without training a separate reward model — the model itself is the implicit reward model. Tradeoffs: (1) simplicity — DPO requires only a standard training loop vs RLHF's 3-model pipeline (actor, critic, reward model); (2) stability — DPO has fewer hyperparameters and doesn't suffer from reward hacking; (3) expressiveness — RLHF with a good reward model can optimize for complex multi-dimensional preferences that binary DPO struggles with; (4) data efficiency — DPO needs high-quality preference pairs where the chosen and rejected responses differ meaningfully; (5) iterative improvement — RLHF can generate new data online and improve, while DPO is offline. For most production use cases, DPO is recommended as the starting point.

**Q: What is reward hacking and how do you detect and prevent it?**
Reward hacking occurs when the policy model exploits patterns in the reward model to achieve high reward scores without actually improving response quality. Common manifestations: excessively verbose responses (reward model prefers longer answers), sycophantic behavior (always agreeing with the user), or using specific phrases that the reward model scores highly. Detection: (1) monitor response length trends — if average response length increases during training, suspect reward hacking; (2) compare reward model scores with human evaluations — divergence indicates hacking; (3) track response diversity — decreasing diversity suggests mode collapse. Prevention: (1) KL penalty — constrain the policy to stay close to the reference model (standard in PPO for RLHF); (2) reward model ensembles — use multiple reward models and take the minimum score; (3) length normalization — normalize reward by response length; (4) iterative reward model retraining — update the reward model with new policy outputs to close the gap. DPO naturally avoids some reward hacking because it doesn't use an explicit reward model.

**Q: How does Constitutional AI (CAI) work and when is it preferred over standard RLHF?**
Constitutional AI replaces human feedback with a set of principles (a "constitution") that the model uses to self-critique and revise its own outputs. The process: (1) generate an initial response; (2) ask the model to critique the response against each principle ("Is this response harmful? Is it honest?"); (3) ask the model to revise based on the critique; (4) use the (original, revised) pair as preference data for DPO or RLHF. Prefer CAI over standard RLHF when: (1) you need to scale alignment without proportional scaling of human annotators; (2) you want transparent, auditable alignment criteria (the constitution is explicit); (3) you need to quickly iterate on safety rules (change the constitution, not retrain annotators); (4) red-teaming reveals failure modes that can be expressed as principles. Anthropic uses CAI for Claude. The constitution typically includes 10-20 principles covering harmlessness, helpfulness, and honesty.

**Q: What is KTO (Kahneman-Tversky Optimization) and when is it useful for alignment?**
KTO aligns models using only binary feedback (thumbs up/thumbs down) on individual responses, without requiring paired preferences. Unlike DPO which needs "response A is better than response B" pairs, KTO works with "this response is good" or "this response is bad" signals independently. This is valuable because: (1) binary feedback is much cheaper to collect than paired preferences — users naturally give thumbs up/down, not side-by-side comparisons; (2) production systems generate binary signals at scale (user satisfaction, task completion); (3) it handles unbalanced data well (more negative than positive signals, or vice versa). KTO is based on Kahneman and Tversky's prospect theory — humans weight losses more heavily than gains, so the model is penalized more for bad outputs than rewarded for good ones. Use KTO when: you have abundant binary feedback but limited paired preferences, or when collecting preference pairs is impractical.

**Q: How do you build and evaluate a reward model for RLHF?**
A reward model is a classifier trained on human preference data to predict which of two responses a human would prefer. Architecture: take the base LLM, replace the language modeling head with a scalar reward head, and train with Bradley-Terry loss on preference pairs. Training data: 50K-500K preference pairs, each containing a prompt, a chosen response, and a rejected response. Key considerations: (1) annotator quality — use 3+ annotators per pair, measure inter-annotator agreement (>70% agreement is good); (2) diversity — include easy pairs (clearly good vs clearly bad), hard pairs (both good but different styles), and adversarial pairs (reward hacking patterns); (3) evaluation — hold out 10% of preference pairs, measure accuracy (good reward models achieve 70-75% agreement with human preferences). Failure modes: (1) length bias — preferring longer responses regardless of quality; (2) style bias — preferring a specific writing style regardless of content; (3) sycophancy — preferring responses that agree with the user. Mitigate by including length-controlled pairs and contrarian examples in training data.

---

## 12. Best Practices

1. **Start with high-quality SFT before DPO** — DPO needs a good reference model; a weak SFT leads to poor alignment.
2. **Use diverse prompts** — alignment training data should cover safety, helpfulness, and harmlessness equally.
3. **Include adversarial examples** — prompts that try to elicit harmful behavior; model should refuse gracefully.
4. **Tune β in DPO carefully** — too low β → forgetting; too high β → no improvement. Typical range: 0.01-0.5.
5. **Evaluate on behavioral benchmarks** — TruthfulQA, BBQ (bias), AdvBench (safety) alongside capability benchmarks.
6. **Iterative red-teaming** — discover failure modes → add examples → retrain. This loop is never truly finished.

---

## 13. Case Study: Aligning a Customer Service LLM

**Problem:** Fine-tune LLaMA 3 8B for a bank's customer service. Requirements: helpful for account questions, refuse to give financial advice (regulatory requirement), professional tone, never reveal system prompt.

**Phase 1: SFT (500 examples)**
- 300 ideal responses from compliance-reviewed human agents
- 200 refusal examples for regulated topics (investment advice, insider trading questions)
- Format: professional, concise, empathetic

**Phase 2: Preference Data Collection (2000 pairs)**
- Generate 4 responses per prompt using SFT model
- Compliance team rates: ranks for helpfulness, professionalism, compliance
- Create (prompt, chosen, rejected) dataset

**Phase 3: DPO Alignment**
```
β = 0.1 (conservative, preserve SFT quality)
LR = 5e-7 (very low for DPO)
Epochs: 2
Training time: 45 min on 1× A100
```

**Evaluations:**
- Compliance refusal rate: 94% (baseline SFT: 61%)
- Customer satisfaction score: 8.4/10 (baseline: 7.9)
- Professional tone rating: 92% (baseline: 78%)
- Capability regression (general QA): -1.2% (acceptable)
