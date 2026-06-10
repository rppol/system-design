# Reasoning Models

## 1. Concept Overview

Reasoning models are LLMs that spend significantly more computation at inference time by generating extended internal "thinking" before producing a final answer. Unlike standard LLMs that generate the next token as fast as possible, reasoning models simulate deliberate, step-by-step thinking — exploring multiple approaches, self-correcting errors, and verifying answers before committing.

The key insight: **more test-time compute = better answers**, at least for tasks with verifiable solutions (math, code, logic). OpenAI's o1 demonstrated that spending 10-100× more tokens on "thinking" could solve problems that GPT-4 routinely failed on. This sparked a new scaling paradigm — not just training more, but thinking more at inference time.

---

## 2. Intuition

> **One-line analogy**: Reasoning models are like a student who thinks through a problem carefully before answering, versus one who blurts out the first thing that comes to mind.

**Mental model**: Standard LLMs generate tokens as fast as possible — the next token is predicted from the current context. Reasoning models (o1, DeepSeek-R1) generate a long "thinking" sequence first, exploring multiple approaches, backtracking when something doesn't work, verifying intermediate steps. This extended thinking is like scratch paper — the model works through the problem before committing to an answer. More thinking tokens = better answers on hard problems.

**Why it matters**: Reasoning models solved problems that stumped standard LLMs — olympiad math, complex code, multi-step logic. They represent a new scaling axis: instead of training bigger models, spend more compute at inference time. This shifts costs from training to inference, and enables dynamic compute allocation (spend more thinking time on harder problems).

**Key insight**: Test-time compute scaling ("thinking more") is a fundamentally different and complementary axis to pre-training scale ("training more") — both improve performance, but in different regimes and on different problem types.

---

## 3. Core Principles

- **Test-time compute scaling**: Performance scales with inference-time compute (thinking tokens), analogous to how training-time scaling improves base models.
- **Chains of thought are emergent but trainable**: Base models exhibit CoT; reasoning models are explicitly trained to use extended CoT as a first-class capability.
- **Process reward models (PRM)**: Score intermediate reasoning steps, not just final answers — enables training models to reason correctly, not just reach correct conclusions by any path.
- **Reinforcement learning on verifiable tasks**: Math and code provide ground truth for RL training — the model can be trained to reason without human labeling of the thinking process.
- **Exploration vs. exploitation**: During training, models explore diverse reasoning paths; during inference, they search for high-reward paths.

---

## 4. Types / Strategies

### 4.1 OpenAI o1 / o3

OpenAI's reasoning model series (2024). Uses a hidden "thinking" phase before responding:

```
User: Prove that √2 is irrational.

[Thinking - internal, not shown to user]:
  I'll use proof by contradiction.
  Assume √2 = p/q where p, q are coprime integers.
  Then 2 = p²/q², so p² = 2q².
  This means p² is even, therefore p is even.
  Let p = 2k. Then 4k² = 2q², so q² = 2k².
  This means q² is even, therefore q is even.
  But p and q can't both be even if they're coprime. Contradiction!
  [The thinking continues, exploring edge cases, verifying each step]

Response: We can prove √2 is irrational by contradiction...
```

**Key properties:**
- Thinking tokens consume context window but are hidden from user
- Thinking can be "extended" — allocate more compute for harder problems
- Performs dramatically better on competition math (AIME), PhD-level science (GPQA), competitive programming

**o1 vs o3 performance (AIME 2024):**
```
GPT-4o:  13%
o1:      74%
o3:      99.3%  (best in the world, surpasses expert humans)
```

### 4.2 DeepSeek-R1

Open-source reasoning model (2025) trained entirely with RL:

```
Training approach (surprising — no SFT warm-up initially):
  Stage 1: GRPO (Group Relative Policy Optimization) with:
    - Correctness reward: +1 if final answer is correct, 0 otherwise
    - Format reward: +0.1 if <think>...</think><answer>...</answer> format followed
  No human-labeled reasoning data!

What emerged:
  - Model spontaneously developed "aha moments" (self-correction)
  - Extended chain-of-thought
  - Reflection and verification behaviors

Stage 2: SFT warm-up + GRPO (improved version)
  Better instruction following + strong reasoning
```

DeepSeek-R1 performance: comparable to o1 on many benchmarks.
Impact: proved that reasoning can emerge from pure RL without supervised reasoning data.

### 4.3 Process Reward Models (PRM)

Score each reasoning step rather than just the final answer:

```
Standard reward (ORM — Outcome Reward Model):
  Full solution → correct/incorrect → reward

Process reward (PRM):
  Step 1: Assume p/q is rational... → reward +0.3
  Step 2: p² = 2q²... → reward +0.4
  Step 3: Therefore p is even... → reward +0.4
  Step 4: [wrong step] → reward -0.2
  ...
  Final score: sum of step rewards

Benefits:
  - Trains model to reason correctly, not just reach right answer by luck
  - Enables MCTS (Monte Carlo Tree Search) over reasoning steps
  - Filters high-quality reasoning chains for training data
```

### 4.4 Self-Consistency

Generate multiple reasoning chains and vote:

```
Problem: "Train A leaves at 9am at 60mph. Train B leaves at 10am at 80mph.
         When do they meet (same starting point, toward each other, 200 miles apart)?"

Generate N=10 chains with temperature=0.8:
  Chain 1: 10:30am ✓
  Chain 2: 10:15am ✗
  Chain 3: 10:30am ✓
  Chain 4: 10:30am ✓
  Chain 5: 10:45am ✗
  ...
  Majority: 10:30am (7/10)

Final answer: 10:30am

Improves accuracy by 5-15% on reasoning tasks
Useful when you can't afford a full reasoning model but need better reliability
```

### 4.5 Tree of Thought (ToT)

Explicitly maintain a tree of reasoning paths; evaluate and prune:

```
Problem: Design a system that handles 1M requests/sec

Tree:
  Root: Initial approaches
  |-- Branch 1: Horizontal scaling
  |   |-- Leaf 1a: Auto-scaling groups → Evaluate: feasible but needs LB
  |   |-- Leaf 1b: Kubernetes + HPA → Evaluate: good, continue
  |-- Branch 2: Caching
  |   |-- Leaf 2a: Redis cache → Evaluate: reduces DB load
  |   |-- Leaf 2b: CDN for static → Evaluate: only helps for static
  |-- Branch 3: Event-driven
      |-- Leaf 3a: Kafka queuing → Evaluate: adds latency

BFS/DFS + LLM-as-evaluator to score and prune branches
Best branch: 1b + 2a combination
```

Useful for: planning, design problems, creative tasks with combinatorial space.

### 4.6 MCTS (Monte Carlo Tree Search) for Reasoning

Apply MCTS to LLM reasoning:

```
  Root (problem statement)
    /        |         \
  Step 1a  Step 1b   Step 1c
  /    \       |
Step 2a Step 2b Step 2c
   |
 Final answer → reward signal

Rollout: complete a reasoning chain from a node → get reward
UCB selection: balance exploration vs exploitation
Backprop: update node values with rollout rewards

Used in: AlphaCode 2, AlphaProof (Google DeepMind math reasoning)
Limitation: slow — requires many forward passes (10-1000× base inference)
```

### 4.7 Reward Hacking and Mitigation

Reward hacking occurs when the model discovers unintended shortcuts to maximize reward without actually solving the problem:

```
Types of reward hacking in reasoning training:

1. Format gaming
   Model outputs <think>...</think><answer>42</answer> with trivial or absent
   reasoning in the thinking block — earns format reward without real thinking

2. Length hacking
   Generate very long, repetitive chains to maximize format or verbosity reward
   without additional correctness; "padding" the thinking with restatements

3. Pattern memorization
   Model has seen problem types in pre-training data — retrieves memorized
   answers without actually reasoning; passes ORM but fails on novel variants

4. Confidence calibration drift
   Model learns to output high-confidence final answers regardless of actual
   certainty — catastrophic for high-stakes deployments

Real example: DeepSeek-R1 early training showed "verbosity hacking" where
models generated long but low-quality reasoning chains to exploit the
format reward before the correctness signal dominated.
```

**Mitigations:**
```
1. Process Reward Model (PRM): score intermediate steps, not just final format
   — makes it hard to game with empty thinking blocks

2. Held-out test cases for code: model cannot see the verifier's test suite
   — prevents memorizing expected outputs

3. Anti-gaming rewards: penalize thinking-block length without corresponding
   correctness gain; reward concise correct reasoning over verbose wrong reasoning

4. Diverse reward signals: combine correctness + reasoning quality + conciseness
   — harder to game multiple signals simultaneously than a single one

5. Human spot-checks: sample reasoning traces for human review to catch
   qualitative hacking patterns that automated rewards miss
```

### 4.8 Overthinking and Budget Control

Reasoning models sometimes generate unnecessarily long thinking chains on simple problems, wasting compute:

```
Observed pattern:
  Simple question: "What is 15% of 200?"
  Overthinking trace: 3,000 tokens of self-checking, alternative approaches,
                      verification steps — for a 2-second mental calculation

  Hard question: "Prove the Riemann hypothesis connection to prime gaps"
  Underthinking: 200 tokens, then "This is complex, I'm not sure" — stops early

The model's thinking budget allocation is poorly calibrated without intervention.
```

**s1 paper "Wait" token technique (Stanford, 2025):**
```
During RL training, when the model attempts to end its thinking early,
insert a "Wait" token to force continuation:

  Model: "...so the answer is 42. </think>"
  Intervention: replace </think> with "Wait, let me verify this..."
  Model continues: "Actually, I should check by substituting back..."

This trains the model to use its full allocated budget before concluding,
especially on problems that warrant extended thinking.
```

**Budget forcing at inference:**
```
max_thinking_tokens parameter controls how long the model can think:
  Easy problems:   max_thinking_tokens = 500   (cheap, fast)
  Medium problems: max_thinking_tokens = 2000
  Hard problems:   max_thinking_tokens = 16000 (expensive but necessary)

Cost model:
  Reasoning token cost = thinking_tokens × price_per_token
  o1 thinking: ~$15/1M tokens (vs $5/1M for output tokens)
  A 10,000-token thinking chain costs $0.15 — 50-100× a standard response

Routing strategy to control cost:
  Use difficulty classifier (fast, cheap LLM call) to estimate query difficulty
  → Easy: route to GPT-4o/Sonnet (no thinking), 80% of queries
  → Hard: route to o1/R1 with thinking budget, 20% of queries
  This architecture serves 80% of traffic cheaply, reserves reasoning for hard cases
```

**Overthinking vs underthinking tradeoff:**
```
Underthinking (budget too small):
  Model reaches ceiling before finding correct answer
  Quality: wrong answer, low confidence
  Fix: increase max_thinking_tokens

Overthinking (budget too large):
  Model reaches correct answer at token 500, then spends 9,500 more tokens
  re-deriving and re-checking unnecessarily
  Quality: correct answer, wasted compute, higher latency
  Fix: train early-stopping signal; use difficulty routing

Break-even heuristic: stop extending budget when accuracy gain per
additional 1,000 tokens drops below a domain-specific threshold
```

---

## 5. Architecture Diagrams

### Test-Time Compute Scaling Curve
```
Accuracy
  ^
  |                                    ○ o3
  |                              ○ o1-high
  |                       ○ o1
  |              ○ GPT-4o
  |
  +---+-------+--------+---------+----> Inference compute (tokens)
      1×      5×       20×       100×

Key insight: accuracy scales log-linearly with test-time compute
  (up to some task-specific ceiling)
```

### GRPO Training Loop (DeepSeek-R1 style)
```
Prompt (math/code problem)
     |
     v
[Policy LLM] generates G responses (group)
     |
     v
[Reward Function]
  For each response i:
    r_i = 1 if final_answer == ground_truth else 0
    r_i += format_bonus if <think>...</think> used
     |
     v
[Advantage Computation]
  A_i = (r_i - mean(r)) / std(r)  (within group)
  -- no value function needed (advantage relative to group)
     |
     v
[GRPO Loss]
  Maximize: Σ_i A_i × log π_θ(response_i)
  Constrained by KL(π_θ || π_ref) < ε
     |
     v
[Updated Policy]
```

---

## 6. How It Works — Detailed Mechanics

### Reward Modeling for Math/Code

Math and code have ground truth → enables RL without human labels:

```
Math reward:
  Correct numerical answer: +1
  Correct symbolic form: +1
  Partially correct (right approach, arithmetic error): configurable
  Wrong: 0

Code reward:
  All test cases pass: +1
  Partial pass rate: fractional reward
  Compilation error: 0
  Style/efficiency bonus: optional

These signals are cheap, scalable, and accurate
→ Reason why math and code are the primary domains for reasoning model training
```

### Budget Forcing / Test-Time Compute Allocation

Allocate more or less thinking compute based on problem difficulty:

```
s1 paper (Stanford, 2025): "Wait" token
  During RL training, force the model to think longer by inserting "Wait" tokens
  This extends the reasoning chain even when the model wants to conclude early

Budget allocation:
  Easy problems: 100-500 thinking tokens
  Medium problems: 500-2000 thinking tokens
  Hard problems: 2000-20000 thinking tokens

Dynamic allocation:
  Model generates a difficulty estimate first
  Allocate thinking budget based on estimate
```

### Verification and Self-Correction

Reasoning models exhibit "aha moments" — spontaneous self-correction:

```
[Thinking excerpt from R1]:
  "...wait, that doesn't seem right. If x=3 then f(3) = 9-6 = 3, not 0.
  Let me reconsider. I should check my formula derivation again...
  Actually the issue is I forgot to account for the quadratic term.
  Let me restart with f(x) = x² - 3x + c..."

This self-correction behavior was NOT explicitly trained —
  it emerged from RL on correctness rewards
```

---

## 7. Real-World Examples

### OpenAI o1/o3 Achievements
- AIME 2024: 99.3% (o3) vs human ~85% (competition math)
- GPQA Diamond (graduate science): o3 scores 87.7% vs human experts 81%
- SWE-bench verified (software engineering): o3 solves 71.7% of GitHub issues
- FrontierMath (new, harder math benchmark): o3 solves 25.2% (humans: <2%)

### DeepSeek-R1 Impact
- First open-source model to match o1 on reasoning benchmarks
- Released weights for 7B, 14B, 32B, 70B, and 671B variants
- Reasoning traces are visible (unlike o1 which hides thinking)
- Distilled from R1 into smaller models: 7B R1 outperforms many 70B base models

### AlphaProof (Google DeepMind, 2024)
- Used MCTS over formal proof steps (Lean 4 language)
- Solved 4/6 problems from 2024 International Mathematical Olympiad
- Silver medal level performance

---

## 8. Tradeoffs

| Approach | Accuracy | Latency | Cost | Best For |
|----------|---------|---------|------|---------|
| Standard GPT-4o | Good | 2-5s | Low | General tasks |
| CoT prompting | Better | 3-8s | 1.5× | Medium reasoning |
| o1-mini | Very good | 5-20s | 3× | Code, math |
| o1 | Excellent | 10-60s | 10× | Hard reasoning |
| o3 | SOTA | 30-300s | 50× | Expert-level problems |
| Self-consistency (N=10) | Better | 10× | 10× | High-stakes |

---

## 9. When to Use / When NOT to Use

### Use Reasoning Models When:
- Multi-step math, logic proofs, formal verification
- Complex coding tasks (algorithm design, debugging hard bugs)
- Scientific reasoning, medical diagnosis support
- Any task where "thinking more" plausibly helps
- Accuracy is paramount and latency/cost is acceptable

### Use Standard LLM When:
- Conversational tasks (chat, summarization, translation)
- Latency < 2 seconds required
- Simple factual Q&A (RAG is better anyway)
- Cost is primary constraint
- Creative tasks (stories, marketing copy) — thinking doesn't help creativity

---

## 10. Common Pitfalls

1. **Using reasoning models for everything**: They're 10-50× more expensive. Reserve for tasks that benefit.
2. **Benchmarking on contaminated tasks**: AMC/AIME problems may appear in training data. Use held-out problems.
3. **Hiding thinking from users**: Reasoning traces are valuable for debugging and trust. Consider exposing them.
4. **Reasoning ≠ factual accuracy**: Reasoning models can reason flawlessly to a wrong conclusion if the premise is wrong. RAG + reasoning is better than reasoning alone for fact-intensive tasks.
5. **Assuming RL-trained models generalize**: RL on math/code improves math/code; may not transfer to unrelated domains.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **OpenAI o1/o3** | Top-tier reasoning | Best on math, code, science; expensive |
| **DeepSeek-R1** | Open-source reasoning | Matches o1; free to self-host |
| **QwQ-32B** | Open reasoning | Qwen's reasoning model; strong |
| **Gemini 2.0 Flash Thinking** | Google reasoning | Fast reasoning; good quality |
| **Claude 3.5 Sonnet** (extended thinking) | Anthropic reasoning | Available via API |
| **Math-Shepherd** | PRM for math | Step-level reward signals |
| **Lean 4** | Formal verification | Used by AlphaProof |
| **OpenR** | MCTS for LLMs | Open-source MCTS implementation |

---

## 12. Interview Questions with Answers

**Q: What is test-time compute scaling and why is it significant?**
A: Test-time compute scaling means inference performance improves predictably with more compute spent on generation — more thinking tokens, more rollouts, or tree search. Before o1, the primary scaling axis was training compute (bigger model, more data). Now there are two independent axes: (1) train more → smarter base model; (2) think more at inference → better answer from the same model. This is significant because it provides an immediate path to better performance on hard tasks without retraining, and it enables dynamic compute allocation — spend 100 tokens on a simple query, 10,000 on a hard one.

**Q: What is chain-of-thought prompting and how does it differ from a reasoning model like o1?**
A: Chain-of-thought prompting elicits step-by-step reasoning by adding "think step by step" to the prompt — the base model produces visible reasoning with no architectural or training change. Reasoning models (o1, DeepSeek-R1) are explicitly trained via RL to generate extended internal thinking: they explore multiple paths, self-correct, and verify before answering. CoT prompting improves accuracy 5-15% on medium reasoning tasks. Reasoning models improve 5-10× on hard tasks — AIME: GPT-4o 13% vs o1 74%. The key difference: CoT is a prompting technique applied to any model; reasoning models have internalized the ability to allocate arbitrary compute to hard problems through training.

**Q: What is GRPO and why did DeepSeek-R1 use it instead of PPO?**
A: GRPO (Group Relative Policy Optimization) generates G responses for each prompt, computes a reward for each, and normalizes advantages within the group: A_i = (r_i - mean(r)) / std(r). PPO requires a separate critic (value function) model to estimate a baseline reward for each state. GRPO eliminates the critic — the group mean serves as the baseline. Benefits for LLM training: roughly half the GPU memory (no critic model to train and store), simpler training pipeline, and a stable training signal because the normalization is within-group. DeepSeek-R1 used GRPO with only two rewards (correctness + format), demonstrating that complex reasoning can emerge without a learned value function.

**Q: What is the difference between outcome reward models (ORM) and process reward models (PRM)?**
A: An ORM scores only the final answer — correct (+1) or wrong (0). A PRM scores each intermediate reasoning step independently. PRM advantages: (1) provides denser training signal — many step scores vs. one final score; (2) identifies where reasoning went wrong, not just that it did; (3) enables MCTS by scoring partial paths before they complete; (4) catches models that reach the right answer via wrong reasoning (which ORM rewards but PRM penalizes). PRM disadvantage: requires labeled reasoning step quality, which is expensive — either human annotation or a learned step-verifier model. In production, PRMs enable best-of-N reranking: generate N reasoning chains with sampling, then pick the one with the highest average step reward.

**Q: How was DeepSeek-R1 trained without supervised reasoning data?**
A: DeepSeek-R1 used GRPO with only two rewards: (1) correctness — +1 if the final answer matches ground truth; (2) format — small bonus for using `<think>...</think><answer>...</answer>` tags. No human annotation of reasoning chains was required. The model spontaneously developed extended chain-of-thought, self-correction, and reflection behaviors purely from the RL training signal. This demonstrated that reasoning behaviors are instrumentally useful for maximizing correctness — they emerge from incentivizing correct outcomes rather than from imitating human reasoning. A crucial insight: the RL training signal (correctness on math/code problems) is cheap and scalable because these domains have ground truth verifiers.

**Q: What failure modes do reasoning models exhibit and how do you mitigate them?**
A: Four main failure modes: (1) Reward hacking — model finds shortcuts to maximize reward without correct reasoning, e.g., outputting the right format with empty thinking blocks. Mitigation: PRM, diverse reward signals, held-out test cases. (2) Overthinking — generating thousands of unnecessary tokens on simple problems. Mitigation: difficulty routing, budget forcing with max_thinking_tokens. (3) Premise errors — reasoning flawlessly from an incorrect assumption. Mitigation: RAG + reasoning (facts from retrieval, logic from reasoning model). (4) Domain transfer failure — RL on math/code improves math/code but may not generalize to other domains. Mitigation: domain-specific RL fine-tuning or standard SFT for non-math/code tasks.

**Q: How would you design a system that uses reasoning models cost-effectively in production?**
A: Route by difficulty. Use a small, fast classifier to categorize queries as easy/medium/hard. Route easy queries (conversational, simple factual) to standard GPT-4o or Sonnet — low cost, low latency. Route medium queries (single-step math, moderate debugging) to o1-mini or similar. Route hard queries (multi-step proof, complex algorithmic design) to o1/o3 with a configured max_thinking_tokens budget. Cache reasoning traces for repeated similar problems. Set hard token budget limits on reasoning models to prevent runaway costs — a 50,000-token thinking chain at $15/1M tokens costs $0.75 per query. Result: 80-90% of queries served cheaply, 10-20% get full reasoning power. This is the architecture used by production AI tutoring applications.

**Q: What is self-consistency and when does it fail?**
A: Self-consistency generates N reasoning chains (N=10-40) with temperature > 0 and takes majority vote. It improves accuracy 5-15% on reasoning tasks with definitive correct answers. It fails when: (1) the model consistently makes the same error across all chains — systematic failure is not correctable by voting (N chains all wrong, majority is still wrong); (2) the task is creative or open-ended with no single correct answer — majority vote has no meaning; (3) the model accuracy on a problem is below 50% — majority vote doesn't reliably help when individual chain accuracy is near random. The quality ceiling is bounded by single-chain accuracy: self-consistency reduces variance but cannot raise the mean above the single-chain ceiling. For problems where even the best reasoning models are near random, MCTS or higher-quality models are needed.

**Q: Explain the test-time compute scaling curve. Does it scale indefinitely?**
A: Accuracy scales approximately log-linearly with test-time compute (thinking tokens) up to a task-specific ceiling. The ceiling varies: competition math (AIME) scales to ~99% with enough thinking tokens; general QA has a lower ceiling; formal verification with symbolic solvers can scale furthest. The ceiling is hit when the task difficulty exceeds the model's knowledge or reasoning capacity — you cannot think your way to facts you don't know, and you cannot reason through a problem whose structure exceeds your learned capabilities. In practice: easy problems reach their ceiling at 100-500 thinking tokens; olympiad math benefits from 5,000-20,000+ tokens. Beyond the task ceiling, additional thinking tokens produce diminishing or zero improvement.

**Q: How was DeepSeek-R1 trained to produce visible reasoning traces, while o1 hides its thinking?**
A: DeepSeek-R1 used a format reward: the model earned a small bonus if it structured its response as `<think>...</think><answer>...</answer>`. The `<think>` content is part of the model's visible output — users and developers can inspect the reasoning. OpenAI o1 uses a separate thinking sequence that is stripped from the API response — only the final answer is delivered. R1's approach is more transparent (users can debug reasoning errors, verify thought process) but raises concerns about reasoning being performative rather than functional. Both approaches produce similar accuracy improvements on benchmarks; the tradeoff is transparency and debuggability (R1) vs. IP protection and reduced prompt injection risk via thinking manipulation (o1).

**Q: What is MCTS applied to LLM reasoning and what are its practical limitations?**
A: MCTS treats each reasoning step as a node in a tree. UCB (Upper Confidence Bound) balances exploration of new branches vs. exploitation of promising ones. Each rollout runs a complete reasoning chain from a node and scores it with a reward model (PRM or outcome checker). Node values are backpropagated: if a partial path leads to correct answers in 7 of 10 rollouts, that path gets a high value. Practical limitations: (1) each rollout is a full LLM forward pass — MCTS with 100 rollouts costs 100× base inference; (2) requires a reliable reward or verification signal — without a PRM or test-case checker, node values are noisy; (3) high implementation complexity. Used in AlphaProof (IMO silver-level proofs in Lean 4) and AlphaCode 2, but not in general-purpose commercial APIs because the compute cost is prohibitive at scale.

**Q: You're building a coding assistant that needs to solve hard algorithmic problems. How do you choose between reasoning models, self-consistency, and standard LLM + CoT?**
A: Use a tiered approach based on difficulty and latency budget. For well-known algorithm patterns (sorting, BFS, standard DP): standard LLM with CoT is sufficient, fast (<2s), and cheap. For medium problems (graph algorithms, moderately complex DP): self-consistency with N=5 chains provides a meaningful accuracy boost at 5× cost — the problem space is navigable by the base model but benefits from aggregation. For hard competitive programming (novel algorithms, correctness proofs): reasoning model (o1 or DeepSeek-R1) is necessary — these problems have a combinatorial reasoning space that requires extended exploration. Always add programmatic verification: run test cases against the generated code and use the result as a second filter. The verification step is cheap and eliminates 10-20% of errors that even reasoning models make on hard problems.

**Q: What is the "aha moment" phenomenon in DeepSeek-R1 training?**
A: During pure RL training with no supervised reasoning data, DeepSeek-R1 spontaneously developed self-correction behavior: mid-reasoning phrases like "wait, that doesn't seem right, let me reconsider" followed by backtracking to a different and correct approach. This was not explicitly trained — no reward was given for self-correction specifically; only the correctness of the final answer was rewarded. This reveals that reasoning behaviors (exploration, verification, backtracking) are instrumentally useful for maximizing correctness rewards and emerge naturally from RL incentives. It suggests that human-like reasoning patterns may be achievable by incentivizing correct outcomes rather than by imitating human reasoning processes step by step.

**Q: What is self-consistency and when is it worth the cost?**
A: Self-consistency generates N reasoning chains (N=10-40) and takes the majority vote. It improves accuracy by 5-15% on reasoning tasks. Worth the cost when: (1) the task has a single correct answer (math, factual); (2) accuracy is more important than latency; (3) you cannot afford a full reasoning model but need better reliability. Not worth it for: creative tasks, very hard problems where models consistently fail (all chains wrong, so majority is wrong), or latency-sensitive applications. Rule of thumb: if single-chain accuracy on your task is between 40-80%, self-consistency gives meaningful gains; if it is below 30% or above 85%, the gains are marginal.

**Q: How do process reward models enable best-of-N reranking, and how is that different from self-consistency?**
A: Best-of-N with PRM reranking: generate N reasoning chains (N=10-50) with temperature > 0, then score each chain using a PRM that evaluates each intermediate step. Select the chain with the highest average step score, or the highest minimum step score (weakest-link selection). This is more powerful than self-consistency majority voting because: (1) it can distinguish between chains that all reach the same answer — it picks the one with the best reasoning process; (2) it can identify when a chain reaches the right answer via a flawed shortcut; (3) PRM reranking scales better — the PRM is a forward pass only (no generation), so scoring 50 candidates is cheap. Self-consistency requires all chains to generate until EOS; PRM reranking can score partial chains early and prune. Practical tradeoff: PRM reranking requires a trained PRM model; self-consistency works with any generation.

---

## 13. Best Practices

1. **Use reasoning models selectively** — profile which queries benefit from extended thinking, route only those to o1/o3.
2. **Expose reasoning traces** — for trust and debugging, show users the thinking process (o1 hides it; R1 shows it — R1's approach is more transparent).
3. **Combine with RAG for fact-intensive tasks** — reasoning solves logic; RAG provides facts; combining gives best accuracy.
4. **Set budget limits** — reasoning models can use arbitrarily many tokens; set max_tokens to avoid runaway costs.
5. **Use self-consistency for high-stakes decisions** — voting across 5-10 generations is cheap insurance for critical queries.

---


## 14. Case Study

**Scenario:** A DevOps platform company serves 5,000 engineering teams. Their SRE assistant must automatically diagnose production incidents: given a stack trace, logs, and recent git diff, it must identify root cause, generate a fix, and write a regression test. Current GPT-4o (non-reasoning) solves 38% of incidents correctly on first attempt. SLA: correct root cause within 90 seconds for p90 of incidents, cost under $0.50/incident.

**Architecture:**

```
  Incident triggered (PagerDuty alert)
           |
           v
  ┌────────────────────────────────────────────────────────┐
  │   Incident Triage Agent                                │
  │   - Collects: stack trace, last 500 log lines,         │
  │     git diff of last 3 commits, deployment metadata    │
  │   - Classifies severity: P1 / P2 / P3                  │
  └────────────────────────┬───────────────────────────────┘
                           │  structured incident context
                           v
  ┌────────────────────────────────────────────────────────┐
  │   Model Router                                         │
  │   P3 (low severity): claude-sonnet-4-6, temp=0        │
  │   P2 (medium):       o1-mini, budget_tokens=2000       │
  │   P1 (critical):     o3, budget_tokens=10000           │
  │   Timeout: P1 max 60s, P2 max 30s, P3 max 15s         │
  └────────────────────────┬───────────────────────────────┘
                           │
                           v
  ┌────────────────────────────────────────────────────────┐
  │   Reasoning Model (o1/o3 or sonnet)                    │
  │   Extended thinking: traces through:                   │
  │     1. Parse stack trace — identify failing frame      │
  │     2. Correlate with log lines — find causal event    │
  │     3. Inspect git diff — find introduced regression   │
  │     4. Generate root cause hypothesis                   │
  │     5. Write targeted fix (code patch)                 │
  │     6. Write regression test                           │
  │   Output: structured JSON {root_cause, fix, test}      │
  └────────────────────────┬───────────────────────────────┘
                           │
                           v
  ┌────────────────────────────────────────────────────────┐
  │   Validation Sandbox                                   │
  │   - Apply generated patch to isolated container        │
  │   - Run test suite (pytest subset — 120s timeout)      │
  │   - If passes: propose PR via GitHub API               │
  │   - If fails: retry with self-correction (max 2×)      │
  └────────────────────────────────────────────────────────┘

Test-Time Compute Scaling — budget_tokens effect:
  budget_tokens=500:   o1-mini "fast think" — 12s, 65% solve rate
  budget_tokens=2000:  o1-mini "deep think" — 28s, 78% solve rate
  budget_tokens=10000: o3 "full think"      — 58s, 91% solve rate
  PRM reranking N=4:   o1-mini × 4 + PRM   — 35s, 84% solve rate (cheaper than o3)
```

**Key implementation — 3 Python code blocks:**

Block 1 — Reasoning model invocation with structured output:

```python
from __future__ import annotations
import json
import time
from dataclasses import dataclass
from typing import Any, Literal

import anthropic
import openai


@dataclass
class IncidentContext:
    incident_id: str
    stack_trace: str
    log_lines: str          # last 500 lines
    git_diff: str           # last 3 commits
    deployment_meta: str    # recent deploy info


@dataclass
class DiagnosisResult:
    root_cause: str
    confidence: float       # 0.0 - 1.0
    fix_patch: str          # unified diff format
    regression_test: str    # pytest test function
    thinking_tokens_used: int
    latency_seconds: float
    model_used: str


Severity = Literal["P1", "P2", "P3"]


async def diagnose_incident(
    ctx: IncidentContext,
    severity: Severity,
) -> DiagnosisResult:
    """Route to appropriate reasoning model based on severity."""

    prompt = _build_prompt(ctx)

    if severity == "P3":
        return await _diagnose_sonnet(prompt, ctx.incident_id)
    elif severity == "P2":
        return await _diagnose_o1_mini(prompt, ctx.incident_id, budget=2000)
    else:  # P1
        return await _diagnose_o3(prompt, ctx.incident_id, budget=10000)


def _build_prompt(ctx: IncidentContext) -> str:
    return f"""You are an expert SRE diagnosing a production incident.

## Stack Trace
{ctx.stack_trace}

## Recent Logs (last 500 lines)
{ctx.log_lines}

## Git Diff (last 3 commits)
{ctx.git_diff}

## Deployment Metadata
{ctx.deployment_meta}

Analyze this incident and return a JSON object with exactly these fields:
{{
  "root_cause": "one-paragraph description of the root cause",
  "confidence": 0.0-1.0,
  "fix_patch": "unified diff format patch to fix the issue",
  "regression_test": "complete pytest test function that would catch this bug"
}}

Think step by step: (1) parse the stack trace to find the failing frame,
(2) correlate with log events to find the causal sequence,
(3) inspect the git diff for the introduced regression,
(4) formulate the root cause hypothesis,
(5) design the minimal fix,
(6) write a test that would have caught it."""


async def _diagnose_sonnet(prompt: str, incident_id: str) -> DiagnosisResult:
    """Claude Sonnet 4.6 with extended thinking for P3."""
    client = anthropic.AsyncAnthropic()
    t0 = time.monotonic()
    response = await client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=8096,
        thinking={"type": "enabled", "budget_tokens": 4000},
        messages=[{"role": "user", "content": prompt}],
    )
    latency = time.monotonic() - t0
    text = next(
        (b.text for b in response.content if hasattr(b, "text")), ""
    )
    thinking_tokens = sum(
        getattr(b, "thinking", "").count(" ")
        for b in response.content
        if hasattr(b, "thinking")
    )
    parsed = _parse_json_output(text)
    return DiagnosisResult(
        **parsed,
        thinking_tokens_used=thinking_tokens,
        latency_seconds=latency,
        model_used="claude-sonnet-4-6",
    )


async def _diagnose_o1_mini(
    prompt: str, incident_id: str, budget: int
) -> DiagnosisResult:
    """OpenAI o1-mini with reasoning effort budget."""
    client = openai.AsyncOpenAI()
    t0 = time.monotonic()
    response = await client.chat.completions.create(
        model="o1-mini",
        max_completion_tokens=budget + 2048,
        reasoning_effort="medium",
        messages=[{"role": "user", "content": prompt}],
        response_format={"type": "json_object"},
    )
    latency = time.monotonic() - t0
    text = response.choices[0].message.content or ""
    reasoning_tokens = getattr(
        response.usage, "completion_tokens_details", None
    )
    parsed = _parse_json_output(text)
    return DiagnosisResult(
        **parsed,
        thinking_tokens_used=getattr(reasoning_tokens, "reasoning_tokens", 0),
        latency_seconds=latency,
        model_used="o1-mini",
    )


async def _diagnose_o3(
    prompt: str, incident_id: str, budget: int
) -> DiagnosisResult:
    client = openai.AsyncOpenAI()
    t0 = time.monotonic()
    response = await client.chat.completions.create(
        model="o3",
        max_completion_tokens=budget + 4096,
        reasoning_effort="high",
        messages=[{"role": "user", "content": prompt}],
        response_format={"type": "json_object"},
    )
    latency = time.monotonic() - t0
    text = response.choices[0].message.content or ""
    parsed = _parse_json_output(text)
    return DiagnosisResult(
        **parsed,
        thinking_tokens_used=0,
        latency_seconds=latency,
        model_used="o3",
    )


def _parse_json_output(text: str) -> dict[str, Any]:
    try:
        data = json.loads(text)
        return {
            "root_cause": data.get("root_cause", ""),
            "confidence": float(data.get("confidence", 0.0)),
            "fix_patch": data.get("fix_patch", ""),
            "regression_test": data.get("regression_test", ""),
        }
    except json.JSONDecodeError:
        return {
            "root_cause": text[:500],
            "confidence": 0.3,
            "fix_patch": "",
            "regression_test": "",
        }
```

Block 2 — PRM reranking for cost-quality optimization (production concern):

```python
from __future__ import annotations
import asyncio
from dataclasses import dataclass


@dataclass
class RankedDiagnosis:
    diagnosis: DiagnosisResult
    prm_score: float          # 0.0 - 1.0 from Process Reward Model
    rank: int


async def prm_reranked_diagnosis(
    ctx: IncidentContext,
    n_candidates: int = 4,
    model: str = "o1-mini",
    budget_per_candidate: int = 1500,
) -> RankedDiagnosis:
    """
    Generate N candidate diagnoses, score each with a PRM,
    return the highest-scoring candidate.
    Cost: N × o1-mini << 1 × o3, similar quality for P2 incidents.
    PRM is a smaller Claude Haiku model prompted to evaluate step correctness.
    """
    tasks = [
        _diagnose_o1_mini(
            _build_prompt(ctx), ctx.incident_id, budget=budget_per_candidate
        )
        for _ in range(n_candidates)
    ]
    candidates = await asyncio.gather(*tasks)

    scored = await asyncio.gather(
        *[_score_with_prm(c, ctx) for c in candidates]
    )

    ranked = sorted(
        zip(candidates, scored), key=lambda x: x[1], reverse=True
    )
    best, best_score = ranked[0]
    return RankedDiagnosis(diagnosis=best, prm_score=best_score, rank=1)


async def _score_with_prm(
    diagnosis: DiagnosisResult, ctx: IncidentContext
) -> float:
    """
    Use Claude Haiku as a Process Reward Model.
    Scores the reasoning quality of the diagnosis (0.0-1.0).
    """
    import anthropic
    client = anthropic.AsyncAnthropic()
    prompt = f"""Rate the quality of this incident diagnosis (0.0-1.0).

Stack trace context: {ctx.stack_trace[:500]}

Diagnosis: {diagnosis.root_cause}
Fix patch: {diagnosis.fix_patch[:300]}

Score based on:
- Does the root cause correctly identify the failing frame?
- Is the fix targeted and minimal?
- Would the regression test catch this bug?

Return only a number between 0.0 and 1.0."""

    response = await client.messages.create(
        model="claude-haiku-4-5",
        max_tokens=10,
        messages=[{"role": "user", "content": prompt}],
    )
    try:
        return float(response.content[0].text.strip())
    except (ValueError, IndexError):
        return 0.5
```

Block 3 — BROKEN -> FIX: ignoring thinking token budget and timeout:

```python
from __future__ import annotations
import asyncio


# BROKEN: No timeout on reasoning model call.
# o3 with high reasoning effort can run for 5+ minutes on complex incidents.
# P1 incident needs answer in <60s — model overshoots budget.
async def broken_o3_call(prompt: str) -> str:
    import openai
    client = openai.AsyncOpenAI()
    response = await client.chat.completions.create(
        model="o3",
        max_completion_tokens=32768,   # no budget limit — model thinks indefinitely
        messages=[{"role": "user", "content": prompt}],
    )
    return response.choices[0].message.content or ""


# FIX: Set reasoning budget and apply asyncio timeout.
# If model exceeds timeout, fall back to cheaper faster model.
async def fixed_o3_with_timeout(prompt: str, timeout_s: float = 60.0) -> str:
    import openai
    client = openai.AsyncOpenAI()
    try:
        response = await asyncio.wait_for(
            client.chat.completions.create(
                model="o3",
                max_completion_tokens=12000,   # ~10000 reasoning + 2000 output
                reasoning_effort="high",
                messages=[{"role": "user", "content": prompt}],
            ),
            timeout=timeout_s,
        )
        return response.choices[0].message.content or ""
    except asyncio.TimeoutError:
        # Fallback to faster model — better partial answer than timeout
        return await fixed_o1_mini_fallback(prompt)


async def fixed_o1_mini_fallback(prompt: str) -> str:
    import openai
    client = openai.AsyncOpenAI()
    response = await client.chat.completions.create(
        model="o1-mini",
        max_completion_tokens=4000,
        reasoning_effort="low",
        messages=[{"role": "user", "content": prompt}],
    )
    return response.choices[0].message.content or ""
```

**Pitfall 1 — Feeding entire log file to reasoning model wastes tokens:**

```python
# BROKEN: Pass 10,000 log lines to o3.
# Most log lines are unrelated — reasoning model must wade through noise.
# Token cost: ~30,000 tokens input; p99 latency 4 min.
def broken_build_context(logs: list[str]) -> str:
    return "\n".join(logs)  # all 10,000 lines

# FIX: Pre-filter logs to error-level lines ±30 lines of context around
# each error. Reduces input to 500-800 lines; 10x token reduction.
def fixed_build_context(logs: list[str]) -> str:
    error_indices = [i for i, l in enumerate(logs) if "ERROR" in l or "FATAL" in l]
    included = set()
    for idx in error_indices:
        for j in range(max(0, idx - 30), min(len(logs), idx + 30)):
            included.add(j)
    return "\n".join(logs[i] for i in sorted(included))
```

**Pitfall 2 — Treating reasoning model output as infallible:**

```python
# BROKEN: Auto-apply generated fix patch without validation.
# Reasoning models hallucinate plausible-looking but incorrect patches ~8% of the time.
async def broken_apply_fix(patch: str) -> None:
    import subprocess
    subprocess.run(["git", "apply", "-"], input=patch.encode(), check=True)
    # No test run — patch applied directly to production branch

# FIX: Always validate in isolated sandbox before proposing PR.
async def fixed_validate_and_propose(patch: str, test_code: str) -> dict[str, str]:
    import subprocess, tempfile, os
    with tempfile.TemporaryDirectory() as tmpdir:
        # Clone repo into tmpdir, apply patch, run tests
        subprocess.run(["git", "clone", "--depth=1", ".", tmpdir], check=True)
        result = subprocess.run(
            ["git", "apply", "-"], input=patch.encode(), cwd=tmpdir
        )
        if result.returncode != 0:
            return {"status": "patch_failed", "error": result.stderr.decode()}
        test_result = subprocess.run(
            ["python", "-m", "pytest", "--timeout=120", "-x", "-q"],
            cwd=tmpdir, capture_output=True, timeout=150,
        )
        if test_result.returncode == 0:
            return {"status": "validated", "action": "open_pr"}
        return {"status": "test_failed", "output": test_result.stdout.decode()[:2000]}
```

**Pitfall 3 — Using high reasoning effort for all incidents wastes budget:**

```python
# BROKEN: Always use o3 with max budget regardless of incident severity.
# P3 "CSS layout broke" incident costs $8 with o3; costs $0.04 with Sonnet.
async def broken_always_o3(prompt: str) -> str: ...

# FIX: Tiered routing. Only P1 critical incidents use o3.
# P2: o1-mini with budget=2000 or PRM reranking of 4 × o1-mini.
# P3: claude-sonnet-4-6 with extended thinking.
# Cost per incident: P3=$0.04, P2=$0.18, P1=$2.10 avg.
```

**Metrics:**

| Metric | GPT-4o baseline | Sonnet 4.6 (P3) | o1-mini (P2) | o3 (P1) |
|--------|-----------------|-----------------|--------------|---------|
| First-attempt solve rate | 38% | 61% | 79% | 91% |
| p50 latency | 8s | 14s | 28s | 52s |
| p99 latency | 22s | 35s | 68s | 88s |
| Cost per incident | $0.12 | $0.04 | $0.18 | $2.10 |
| Patch validation pass rate | 81% | 88% | 93% | 96% |
| False escalation rate | 31% | 19% | 8% | 4% |
| PRM rerank (4×o1-mini) | — | — | 84%/35s/$0.72 | — |

**Interview Q&As:**

**Q: What is test-time compute scaling and how does it improve reasoning quality?**
Test-time compute scaling dedicates more compute at inference time (not training time) to improve answer quality. Rather than making a single pass, the model generates extended "thinking" traces — step-by-step chains of reasoning — before producing the final answer. More budget_tokens allows more exploration of alternative approaches and self-correction. o1/o3 models demonstrate that a smaller model with 10x more reasoning compute outperforms a larger model with 1x compute on tasks requiring multi-step logic, math, or debugging.

**Q: When should you use PRM reranking instead of a single o3 call?**
PRM reranking (generate N candidates with a cheaper model, score each, take the best) is cost-effective when N × cheaper_model_cost < single expensive_model_cost and when the task benefits from diverse reasoning paths. For P2 incidents: 4 × o1-mini ($0.18 each) = $0.72 vs 1 × o3 ($2.10) — PRM achieves 84% solve rate vs 91% for o3, at one-third the cost. PRM is less useful when candidates are too similar (low diversity) or when the PRM scorer itself is unreliable for the domain.

**Q: How do reasoning models differ from chain-of-thought prompting?**
Chain-of-thought prompting adds "think step by step" to the user prompt; the reasoning is visible in the output and uses standard output tokens (billed at output rate). Reasoning models (o1, o3, Claude with extended thinking) perform internal reasoning in a separate "thinking" phase before generating the final response; this thinking may be partially or fully hidden, uses a separate token budget, and often uses a different (lower) per-token price than output tokens. Reasoning model chains tend to be more self-consistent and self-correcting than CoT-prompted standard models because the model is specifically trained to reason before answering.

**Q: Why is a validation sandbox essential after a reasoning model generates a code fix?**
Reasoning models hallucinate syntactically plausible but semantically incorrect patches approximately 5-10% of the time — the reasoning process increases confidence but does not eliminate hallucination. A patch that looks correct may fail to compile, break unrelated tests, or introduce a subtle new bug. Running the fix in an isolated container with the test suite catches these cases before a PR is opened. The sandbox also provides ground-truth feedback for a retry loop: if the first fix fails validation, the agent can pass the test failure output back to the model for self-correction.

**Q: How do you choose budget_tokens for a reasoning model in production?**
Budget_tokens controls how many tokens the model can spend "thinking" before generating the final answer. More tokens = better accuracy but higher cost and latency. Calibrate by running your eval set with exponentially increasing budgets (500, 1000, 2000, 4000, 8000) and plotting solve rate vs cost. The curve typically shows diminishing returns after a threshold — often 2000-4000 tokens for code debugging. Set the production budget at the knee of that curve. Apply higher budgets only for the highest-severity incidents where cost is less important than correctness.

**Q: What makes code debugging particularly well-suited for reasoning models versus standard LLMs?**
Code debugging requires multi-step logical deduction: (1) parse the stack trace to identify the fault, (2) trace backwards through the call chain, (3) correlate with external state (logs, recent changes), (4) form a causal hypothesis, (5) design a minimal fix, (6) verify the fix is consistent with all constraints. Standard LLMs tend to pattern-match to the most common bug for the given error type without thoroughly checking all evidence. Reasoning models systematically work through each step and self-correct when they detect contradictions — exactly the process an expert SRE uses, just codified as internal reasoning chains.
