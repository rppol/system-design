# Reasoning Models

## 1. Concept Overview

Reasoning models are LLMs that spend significantly more computation at inference time by generating extended internal "thinking" before producing a final answer. Unlike standard LLMs that generate the next token as fast as possible, reasoning models simulate deliberate, step-by-step thinking — exploring multiple approaches, self-correcting errors, and verifying answers before committing.

The key insight: **more test-time compute = better answers**, at least for tasks with verifiable solutions (math, code, logic). OpenAI's o1 demonstrated that spending 10-100× more tokens on "thinking" could solve problems that GPT-4 routinely failed on. This sparked a new scaling paradigm — not just training more, but thinking more at inference time.

---

## Intuition

> **One-line analogy**: Reasoning models are like a student who thinks through a problem carefully before answering, versus one who blurts out the first thing that comes to mind.

**Mental model**: Standard LLMs generate tokens as fast as possible — the next token is predicted from the current context. Reasoning models (o1, DeepSeek-R1) generate a long "thinking" sequence first, exploring multiple approaches, backtracking when something doesn't work, verifying intermediate steps. This extended thinking is like scratch paper — the model works through the problem before committing to an answer. More thinking tokens = better answers on hard problems.

**Why it matters**: Reasoning models solved problems that stumped standard LLMs — olympiad math, complex code, multi-step logic. They represent a new scaling axis: instead of training bigger models, spend more compute at inference time. This shifts costs from training to inference, and enables dynamic compute allocation (spend more thinking time on harder problems).

**Key insight**: Test-time compute scaling ("thinking more") is a fundamentally different and complementary axis to pre-training scale ("training more") — both improve performance, but in different regimes and on different problem types.

---

## 2. Core Principles

- **Test-time compute scaling**: Performance scales with inference-time compute (thinking tokens), analogous to how training-time scaling improves base models.
- **Chains of thought are emergent but trainable**: Base models exhibit CoT; reasoning models are explicitly trained to use extended CoT as a first-class capability.
- **Process reward models (PRM)**: Score intermediate reasoning steps, not just final answers — enables training models to reason correctly, not just reach correct conclusions by any path.
- **Reinforcement learning on verifiable tasks**: Math and code provide ground truth for RL training — the model can be trained to reason without human labeling of the thinking process.
- **Exploration vs. exploitation**: During training, models explore diverse reasoning paths; during inference, they search for high-reward paths.

---

## 3. Types / Strategies

### 3.1 OpenAI o1 / o3

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

### 3.2 DeepSeek-R1

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

### 3.3 Process Reward Models (PRM)

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

### 3.4 Self-Consistency

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

### 3.5 Tree of Thought (ToT)

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

### 3.6 MCTS (Monte Carlo Tree Search) for Reasoning

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

---

## 4. Architecture Diagrams

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

## 5. How It Works — Detailed Mechanics

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

## 6. Real-World Examples

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

## 7. Tradeoffs

| Approach | Accuracy | Latency | Cost | Best For |
|----------|---------|---------|------|---------|
| Standard GPT-4o | Good | 2-5s | Low | General tasks |
| CoT prompting | Better | 3-8s | 1.5× | Medium reasoning |
| o1-mini | Very good | 5-20s | 3× | Code, math |
| o1 | Excellent | 10-60s | 10× | Hard reasoning |
| o3 | SOTA | 30-300s | 50× | Expert-level problems |
| Self-consistency (N=10) | Better | 10× | 10× | High-stakes |

---

## 8. When to Use / When NOT to Use

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

## 9. Common Pitfalls

1. **Using reasoning models for everything**: They're 10-50× more expensive. Reserve for tasks that benefit.
2. **Benchmarking on contaminated tasks**: AMC/AIME problems may appear in training data. Use held-out problems.
3. **Hiding thinking from users**: Reasoning traces are valuable for debugging and trust. Consider exposing them.
4. **Reasoning ≠ factual accuracy**: Reasoning models can reason flawlessly to a wrong conclusion if the premise is wrong. RAG + reasoning is better than reasoning alone for fact-intensive tasks.
5. **Assuming RL-trained models generalize**: RL on math/code improves math/code; may not transfer to unrelated domains.

---

## 10. Technologies & Tools

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

## 11. Interview Questions with Answers

**Q: What is test-time compute scaling and why is it significant?**
A: Test-time compute scaling means that inference performance improves predictably with more compute spent on generation (more thinking tokens, more rollouts, tree search). Before o1, the primary scaling axis was training compute. Now there are two independent scaling axes: (1) train more → smarter base model; (2) think more at inference → better answer from the same model. This is significant because it provides an immediate path to better performance on hard tasks without retraining.

**Q: What is the difference between outcome reward models (ORM) and process reward models (PRM)?**
A: An ORM scores only the final answer — correct (+1) or wrong (0). A PRM scores each intermediate reasoning step. PRM advantages: (1) provides denser training signal (many step scores vs. one final score); (2) can identify WHERE reasoning went wrong, not just that it did; (3) enables MCTS by scoring partial paths. PRM disadvantage: requires annotating reasoning step quality, which is expensive.

**Q: How was DeepSeek-R1 trained without supervised reasoning data?**
A: DeepSeek-R1 used GRPO (Group Relative Policy Optimization) with only two rewards: (1) correctness — +1 if final answer matches ground truth; (2) format — small bonus for using `<think>...</think>` tags. No human annotation of reasoning chains was required. The model spontaneously developed extended chain-of-thought, self-correction, and reflection behaviors purely from the RL training signal. This demonstrated that reasoning can emerge from incentivizing correct answers, not from imitating human reasoning.

**Q: What is self-consistency and when is it worth the cost?**
A: Self-consistency generates N reasoning chains (N=10-40) and takes the majority vote. It improves accuracy by 5-15% on reasoning tasks. It's worth the cost when: (1) the task has a single correct answer (math, factual); (2) accuracy is more important than latency; (3) you can't afford a full reasoning model. Not worth it for: creative tasks, very hard problems where models consistently fail (single chain or 10 chains, all wrong), or latency-sensitive applications.

---

## 12. Best Practices

1. **Use reasoning models selectively** — profile which queries benefit from extended thinking, route only those to o1/o3.
2. **Expose reasoning traces** — for trust and debugging, show users the thinking process (o1 hides it; R1 shows it — R1's approach is more transparent).
3. **Combine with RAG for fact-intensive tasks** — reasoning solves logic; RAG provides facts; combining gives best accuracy.
4. **Set budget limits** — reasoning models can use arbitrarily many tokens; set max_tokens to avoid runaway costs.
5. **Use self-consistency for high-stakes decisions** — voting across 5-10 generations is cheap insurance for critical queries.

---

## 13. Case Study: Automated Math Competition Solver

**Problem:** Educational platform wants to provide step-by-step solutions for AMC/AIME competition math. Previous LLM (GPT-4o) solved ~40% of AMC 12 problems.

**Solution Architecture:**
```
Query → difficulty classifier
  Easy (AMC 10): → GPT-4o with CoT → 78% accuracy
  Medium (AMC 12): → o1-mini → 89% accuracy
  Hard (AIME): → o1 → 82% accuracy (AIME is extremely hard)

With PRM-guided self-consistency (N=5):
  Easy: 82% (+4%), cost 5× → use only for wrong answers
  Hard: 91% (+9%), cost 5× → worth it

For wrong answers (as judged by answer checker):
  Retry with o3 (highest quality): 97% accuracy on retry
```

**Results:**
- Overall AMC 12 accuracy: 92% (vs 40% GPT-4o baseline)
- AIME accuracy: 71% (vs 13% GPT-4o baseline)
- Average latency: 12 seconds (users accept given it's replacing hours of work)
- Cost per query: $0.08 avg (acceptable for tutoring app)
