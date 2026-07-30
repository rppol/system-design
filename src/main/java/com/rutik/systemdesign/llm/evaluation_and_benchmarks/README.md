# Evaluation & Benchmarks

<!-- study-paths
senior: README.md
principal: README.md
files this module contributes to each curated path; omit a tier to leave it out
-->
## 1. Concept Overview

Evaluating LLMs is one of the hardest problems in AI. Unlike classification models with clear accuracy metrics, LLMs generate open-ended text that may be helpful, harmful, correct, incorrect, or something in between. The evaluation challenge has three dimensions: (1) what to evaluate (capabilities, safety, alignment, cost); (2) how to evaluate (automated vs. human, reference-based vs. reference-free); (3) evaluation contamination (test sets leak into training data, inflating scores).

Understanding evaluation is critical for both building systems (how do you know your RAG pipeline improved?) and system design interviews (how do you measure production quality?).

---

## 2. Intuition

> **One-line analogy**: Evaluating LLMs is like grading essays — unlike math tests with clear right answers, quality is multidimensional, subjective, and context-dependent.

**Mental model**: Traditional ML has clear metrics (accuracy, F1, AUROC). LLMs generate open-ended text, so evaluation is hard: "Is this response helpful?" requires human judgment. Benchmark suites (MMLU, HumanEval) automate evaluation on specific tasks, but they get "contaminated" — if test questions appear in training data, scores inflate. LLM-as-judge (using a stronger model to score responses) scales evaluation but introduces bias. Chatbot Arena (human preferences via ELO) is the gold standard but slow and expensive.

**Why it matters**: You can't improve what you can't measure. Without rigorous evaluation, you don't know if your prompt change, fine-tuning, or RAG improvement actually helped — or just changed outputs. Production LLM systems need evaluation pipelines that run continuously to detect regressions.

**Key insight**: No single benchmark captures "intelligence" — MMLU tests knowledge, HumanEval tests code, MT-Bench tests instruction following. A model that tops one may underperform on others. Always evaluate on domain-specific tasks that match your actual use case.

---

## 3. Core Principles

- **No single benchmark captures everything**: MMLU measures knowledge; HumanEval measures coding; TruthfulQA measures honesty. No benchmark measures all.
- **Benchmark contamination is pervasive**: If a model trains on data containing benchmark answers, scores are inflated. New benchmarks become contaminated within months.
- **Human evaluation is gold but expensive**: Human judgments are the ground truth but don't scale.
- **LLM-as-judge is useful but biased**: a strong model can judge responses but carries systematic biases (position, verbosity, self-preference — all measured in Zheng et al. 2023, arXiv 2306.05685).
- **Task-specific evaluation beats generic**: Your production metric (SQL execution accuracy, code pass rate, customer satisfaction) matters more than MMLU.

---

## 4. Evaluation Frameworks

### 4.1 Standard Benchmarks

**MMLU (Massive Multitask Language Understanding)**:
```
57 tasks × 4 multiple choice options per question
Domains: STEM, humanities, social science, professional (law, medicine, finance)
Metric: accuracy (0-100%)
Difficulty: elementary -> advanced professional (NOT uniformly graduate level)

Human baselines from the MMLU paper (Hendrycks et al., arXiv 2009.03300):
  Unspecialized humans (Mechanical Turk):  34.5%
  Expert-level estimate (95th percentile): ~89.8%

2024-era reference scores -- kept as historical anchors, not current SOTA.
Every one depends on the prompting setup, so the setup is part of the number:
  GPT-4:       86.4%  (5-shot, GPT-4 technical report)
  LLaMA 3 70B: 82.0%  (5-shot, Meta)
  Claude 3.5 Sonnet: 90.4% (5-shot CoT) / 88.7% (5-shot) / 88.3% (0-shot CoT)
    -- all three are the SAME model on the SAME benchmark, from Table 1 of
       Anthropic's Claude 3.5 Sonnet Model Card Addendum. 2.1 points of spread
       bought purely by changing the prompting setup.
A 5-shot number and a 0-shot-CoT number are not comparable; frontier models
have since moved well past all three.

Limitations: multiple choice; doesn't test reasoning or generation;
  widely assumed contaminated by 2024 (LLMs trained on MMLU-like data)
```

**HellaSwag (commonsense reasoning)**:
```
Pick the most likely continuation of a situation description
Tests: commonsense reasoning, everyday knowledge
Score: 95%+ for frontier models (essentially "solved")
```

**GPQA Diamond (Graduate-level Questions)**:
```
GPQA main set: 448 expert-written multiple choice questions (bio, chem, physics)
GPQA Diamond:  the hardest 198 of those 448 -- both experts right, at most one
               of three web-enabled non-experts right. Diamond is what leaderboards
               report; do not quote 448 as the Diamond size.
Written by domain experts (PhDs, PhD-track researchers)

Human accuracy (GPQA paper, arXiv 2311.12022):
  non-expert with unrestricted web access: ~34% (<=33% on Diamond)
  domain expert: ~65% on the main set, ~69.7% on Diamond

GPT-4o: 53.6% (OpenAI, May 2024)
o3:     87.7% on Diamond (OpenAI's Dec 2024 preview livestream)

Designed to resist saturation -- but frontier scores have climbed well past the
expert baseline since 2024, so check a live leaderboard before quoting a "SOTA"
```

**BBH (BIG-Bench Hard)**:
```
23 challenging reasoning tasks from BIG-Bench that LLMs historically failed
Requires multi-step reasoning, spatial understanding, logical deduction
Current SOTA: ~90%+ with CoT
```

### 4.2 Code Evaluation

**HumanEval**:
```
164 Python functions; docstring → implement the function
Metric: pass@k = probability at least 1 of k samples passes all tests
pass@1 scores (2024-era anchors, 0-shot):
  GPT-4o:      90.2%  (OpenAI's own simple-evals table, gpt-4o-2024-08-06;
                       the 2024-05-13 snapshot scored 91.0)
  Claude 3.5 Sonnet: 92.0%  (Anthropic, Model Card Addendum Table 1)
  LLaMA 3 70B: 81.7%  (Meta's Llama model card, 70B Instruct)

Limitation: saturated for frontier models -- everything current clusters at the
  ceiling, so HumanEval no longer separates systems. Use it as a smoke test and
  move to repo-level benchmarks (SWE-bench and successors) for real signal.
```

The unbiased pass@k estimator used by the HumanEval paper is:

```
pass@k = 1 - C(n - c, k) / C(n, k)

  n = total samples generated per problem
  c = how many of those n samples passed all unit tests
  C(a, b) = "a choose b" = number of ways to pick b items from a
```

**The idea behind it.** "Instead of asking 'did it pass?', ask 'if I drew k of my n
attempts at random, what is the chance I would have drawn at least one working solution?'"

The formula computes the *opposite* event and subtracts it from 1: `C(n-c, k)` counts the draws
made entirely out of the failing samples, and dividing by `C(n, k)` — all possible draws — turns
that count into the probability of drawing k duds. One minus that is "at least one worked." You
generate `n` far larger than `k` (the paper uses n = 200) so the estimate is stable; estimating
pass@10 from exactly 10 samples would give you a noisy 0-or-1 answer per problem.

| Symbol | What it is |
|--------|------------|
| `n` | How many completions you actually sampled per problem. Bigger n = less noisy estimate |
| `c` | How many of those n completions passed every unit test |
| `k` | How many attempts the *product* gets to show the user. This is the number you report |
| `C(n, k)` | Count of distinct k-sized picks from n items, order ignored |
| `C(n-c, k)` | Count of k-sized picks that land entirely in the failing pile |
| `C(n-c,k)/C(n,k)` | Probability all k draws fail. Subtract from 1 to get "at least one passes" |

**Walk one example.** One HumanEval problem, n = 20 samples drawn, c = 6 of them pass:

```
  pass@1  = 1 - C(14, 1) / C(20, 1)
          = 1 - 14 / 20
          = 1 - 0.700      = 0.300     <- same as c/n, as it must be

  pass@5  = 1 - C(14, 5) / C(20, 5)
          = 1 - 2 002 / 15 504
          = 1 - 0.129      = 0.871

  pass@10 = 1 - C(14, 10) / C(20, 10)
          = 1 - 1 001 / 184 756
          = 1 - 0.005      = 0.995

  same model, same problem:  30% at k=1  ->  99% at k=10
```

That 30 -> 99 jump is why the metric's `k` must match your product. A single-completion IDE
autocomplete lives at pass@1; a "generate 10 candidates, run the tests, show the survivor" agent
genuinely earns pass@10. Reporting pass@10 for a pass@1 product is the most common way code
benchmark numbers get inflated without anyone technically lying.

**Why the combinatorics exist at all.** The naive alternative — sample k times, record whether any
passed, repeat — is an unbiased estimate too, but its variance is brutal at small k, and doubling
k means doubling your inference bill. Sampling n once and re-deriving every k analytically gives
you the whole pass@1 / pass@5 / pass@10 curve from a single generation run.

**SWE-bench (Real GitHub Issues)**:
```
SWE-bench full test set: 2,294 real GitHub issues from 12 Python repos
SWE-bench Verified:      a 500-issue human-validated subset (OpenAI, Aug 2024)
Evaluation: the repo's own test suite must pass after the model's patch

IMPORTANT: essentially every headline "% resolved" is on VERIFIED (500), not on
the full 2,294. Quoting a Verified score next to the 2,294 figure is the single
most common way this benchmark gets misreported.

% resolved on SWE-bench Verified:
  Claude 3.5 Sonnet + 2 general tools: 49.0%  (Anthropic, Oct 2024)
  o3 + scaffolding:                    71.7%  (OpenAI, Dec 2024 preview)
  Claude Opus 4.6:                     76.2%  (Anthropic, Feb 2026, mean of 25
                                       trials) -- and 81.42% for the SAME weights
                                       after only a prompt modification
  best official leaderboard submission: 396 / 500 = 79.2%
                                       (SWE-bench/experiments, Dec 2025)

Verified is NOT saturated in the mid-90s. Treat any such figure as an
unattributed aggregator number until you can point at the submission behind it;
the official leaderboard's own top entry is still under 80%. What IS true in 2026
is that labs have begun reporting other coding benchmarks instead -- Anthropic's
Claude Opus 5 launch reports Frontier-Bench and CursorBench and no SWE-bench
Verified score at all.

Scaffold matters as much as the model: the same weights score very differently
under different agent harnesses -- the 76.2 -> 81.42 gap above is 5 points bought
by editing a prompt -- so a score without its scaffold is not a result.
```

**MBPP (Mostly Basic Python Programming)**:
```
974 crowd-sourced Python problems; the standard test split is 500 (task IDs
  11-510). A 427-problem hand-verified "sanitized" subset is also widely used
Simpler than HumanEval; good for smaller models
pass@1: most 7B+ models score 60-80%
```

### 4.3 Human Preference Evaluation

**Chatbot Arena** (originally LMSYS; now run as **LMArena**):
```
Methodology:
  Real users submit prompts
  Two anonymous model responses displayed side by side
  User votes: A is better / B is better / Tie
  Ratings fitted on the accumulated votes with the Bradley-Terry model
    (reported on an Elo-style scale)

Why it's valuable:
  Real user prompts (not curated benchmarks)
  Real user preferences (not researcher's judgment)
  Difficult to contaminate (novel prompts constantly)
  Hundreds of models ranked; millions of pairwise votes accumulated

Ratings are NOT stable reference points -- the board is refit continuously and
the top of the scale has drifted up by hundreds of points since 2023. Any
specific number in a document like this is stale the week it is written; read
the live board. The worked example below uses two invented ratings purely to
show the arithmetic.

Limitations: user base is self-selected (technical users); biases toward
  verbose, confident responses; not task-specific; ratings only mean something
  relative to the models on the board at the same time
```

The rating machinery is two formulas — an expected-score curve and an update rule:

```
Expected score:   E_A = 1 / (1 + 10^((R_B - R_A) / 400))
Rating update:    R_A' = R_A + K x (S_A - E_A)

  R_A, R_B = current ratings of model A and model B
  S_A      = actual outcome: 1 = A won, 0.5 = tie, 0 = A lost
  K        = step size ("K-factor")
```

**Stated plainly.** "Guess how likely each model was to win from their current
ratings, then move each rating by how much the real result surprised you."

The whole system is a surprise meter. Beating an opponent you were already expected to beat moves
you barely at all; beating one you were expected to lose to moves you a lot. That is what makes
Elo self-correcting on a leaderboard where different models are compared wildly different numbers
of times — a model with few votes drifts fast toward its true level, then settles.

| Symbol | What it is |
|--------|------------|
| `R_A` | Model A's current rating. Only *differences* between ratings mean anything |
| `R_B - R_A` | How far apart the two models are. 400 points = 10:1 predicted odds |
| `400` | The scale constant that *defines* what one Elo point is worth. Pure convention |
| `10^(x/400)` | Turns a rating gap into an odds ratio |
| `E_A` | Predicted win probability for A, between 0 and 1. `E_A + E_B = 1` always |
| `S_A` | What actually happened: 1, 0.5, or 0 |
| `S_A - E_A` | Result minus prediction. Positive = did better than expected |
| `K` | How many rating points one full unit of surprise is worth. Chess uses 32 |

**Walk one match.** Two hypothetical models, A at 1290 and B at 1310 (invented ratings — the
point is the arithmetic, not the models). One arena vote comes in and the user picks A:

```
  gap        = R_B - R_A       = 1310 - 1290 =   20
  odds       = 10^(20 / 400)   = 10^0.05     =    1.122
  E_A        = 1 / (1 + 1.122) = 1 / 2.122   =    0.471   <- A "should" win 47% of the time
  E_B        = 1 - 0.471                     =    0.529

  user votes A  ->  S_A = 1, S_B = 0

  K = 32 (chess default)          K = 4 (arena-style, many votes)
    A: 32 x (1 - 0.471) = +16.9     A: 4 x (1 - 0.471) =  +2.1
    B: 32 x (0 - 0.529) = -16.9     B: 4 x (0 - 0.529) =  -2.1
    R_A' = 1290 + 16.9  = 1306.9    R_A' = 1290 + 2.1  = 1292.1
    R_B' = 1310 - 16.9  = 1293.1    R_B' = 1310 - 2.1  = 1307.9
```

Note the update is exactly zero-sum: whatever A gains, B loses. A 20-point gap is a near coin flip
(47/53), which is why the top of the arena leaderboard is genuinely unsettled — separating two
models by 20 Elo needs thousands of votes.

**Why K exists, and why the arena keeps it small.** K is the tradeoff between responsiveness and
stability. Large K (32) lets a new model find its level in a few dozen games, but one lucky streak
whipsaws the leaderboard. Small K (single digits) makes ratings stable enough to publish, at the
cost of new entrants taking thousands of votes to converge. In practice Chatbot Arena does not run
this online update at all for its published board — it refits all votes at once with maximum
likelihood under the Bradley-Terry model (the same logistic curve as `E_A`), which removes the
dependence on vote *order* that the sequential K-update introduces.

**MT-Bench (Multi-Turn Benchmark)**:
```
80 multi-turn conversations across 8 categories
  (writing, reasoning, coding, math, roleplay, STEM, humanities, extraction)
GPT-4 as judge: rates each response 1-10

Why: tests multi-turn capability (most chatbot use is multi-turn)
Score: most frontier models: 8.5-9.5/10
```

### 4.4 RAG Evaluation (RAGAS)

```python
from ragas import evaluate
# Metrics are classes, each constructed with its own judge LLM.
from ragas.metrics.collections import (
    Faithfulness, AnswerRelevancy, ContextRecall, ContextPrecision,
)

# RAGAS metrics:
# faithfulness: Is the answer supported by the retrieved context?
#   (detected via NLI or LLM-as-judge)
#   Score 0-1; 1 = fully grounded; 0 = hallucinated

# answer_relevancy: Does the answer address the actual question?
#   (LLM-as-judge or embedding similarity between question and answer)
#   Score 0-1

# context_recall: Did the retrieval find all relevant information?
#   (requires ground truth answer)

# context_precision: What fraction of retrieved context is relevant?
#   (requires ground truth relevant documents)

result = evaluate(
    dataset,
    metrics=[
        Faithfulness(llm=judge_llm),
        AnswerRelevancy(llm=judge_llm),
        ContextRecall(llm=judge_llm),
        ContextPrecision(llm=judge_llm),
    ],
)
# Returns: {"faithfulness": 0.87, "answer_relevancy": 0.93, ...}
```

Each of those four scores is a ratio, not a black box:

```
faithfulness      = (claims in answer supported by context) / (total claims in answer)
answer_relevancy  = mean cosine( original question, question_i reverse-generated from answer )
context_precision = mean over relevant ranks r of  Precision@r
                    where Precision@r = (relevant chunks in top r) / r
context_recall    = (ground-truth sentences attributable to context) / (ground-truth sentences)
```

**What the formula is telling you.** "Faithfulness asks 'did you make anything up?', answer relevancy
asks 'did you answer the question I asked?', context precision asks 'is the good stuff near the
top?', and context recall asks 'did you fetch everything you needed?'"

The split is diagnostic, not decorative. Two of the four (precision, recall) grade the retriever
and two (faithfulness, relevancy) grade the generator, so the pair that drops tells you which half
of the pipeline to fix. Low recall with high faithfulness means the model is being honest about
bad context — fix retrieval. High recall with low faithfulness means the model is ignoring good
context and hallucinating — fix the prompt or the model.

| Symbol | What it is |
|--------|------------|
| claim | One atomic factual statement the judge splits the answer into |
| `cosine(a, b)` | Angle between two embeddings. 1 = same direction, 0 = unrelated |
| `question_i` | A question the judge *reverse-engineers* from the answer alone |
| `Precision@r` | Of the top r retrieved chunks, what fraction were relevant |
| "attributable" | The judge can point at a context sentence that supports this ground-truth sentence |
| mean over relevant ranks | Only positions holding a relevant chunk contribute to the average |

**Walk one example.** One question, 5 chunks retrieved, relevant ones at ranks 1, 2, and 4:

```
  faithfulness       answer has 8 claims, judge finds 7 supported by context
                     = 7 / 8                                        = 0.875 -> 0.87

  answer_relevancy   3 questions reverse-generated from the answer,
                     cosine to the real question = 0.95, 0.92, 0.92
                     = (0.95 + 0.92 + 0.92) / 3                     = 0.930 -> 0.93

  context_precision  rank: 1    2    3    4    5
                     rel:  yes  yes  no   yes  no
                     P@1 = 1/1 = 1.00
                     P@2 = 2/2 = 1.00
                     P@4 = 3/4 = 0.75      (rank 3 and 5 contribute nothing)
                     = (1.00 + 1.00 + 0.75) / 3                     = 0.917

  context_recall     ground truth is 5 sentences, 4 traceable to context
                     = 4 / 5                                        = 0.800
```

The 0.87 and 0.93 are exactly the numbers the `evaluate()` call above returns — the framework is
doing this arithmetic, not something more exotic.

**Why context_precision is rank-weighted instead of a plain fraction.** A plain "3 of 5 chunks
were relevant" scores 0.60 whether the good chunks sit at ranks 1-3 or ranks 3-5. Position matters
because generators attend most strongly to the head of the context window, so burying the answer
at rank 5 degrades the final answer even though the retriever "found" it. Averaging Precision@r
over the relevant ranks pays out more for hits near the top: the same 3-of-5 at ranks 3, 4, 5
scores `(0.33 + 0.50 + 0.60)/3 = 0.48`, versus `0.917` for ranks 1, 2, 4. Drop the rank weighting
and you lose the only signal that tells you your reranker stopped working.

### 4.5 LLM-as-Judge

Use a capable LLM to evaluate another LLM's responses:

```python
def llm_judge(question: str, response: str, criteria: list) -> dict:
    prompt = f"""Evaluate the following response on these criteria.
Question: {question}
Response: {response}

Rate each criterion 1-5:
{chr(10).join(criteria)}

Return JSON: {{"criterion": score, ...}}"""

    result = judge_llm.complete(prompt)
    return json.loads(result)

# Common criteria for helpfulness:
criteria = [
    "Accuracy: Is the response factually correct?",
    "Completeness: Does it fully address the question?",
    "Clarity: Is it clear and well-organized?",
    "Appropriateness: Is the tone/format appropriate?"
]

# Pairwise comparison (preferred over absolute scoring):
def pairwise_judge(question, response_a, response_b) -> str:
    prompt = f"""Which response better answers the question?
Question: {question}
Response A: {response_a}
Response B: {response_b}
Answer with A, B, or Tie. Then explain why."""
    return judge_llm.complete(prompt)
```

**LLM-as-judge biases:**
```
Position bias: prefers the first response shown (show both orders, average)
Verbosity bias: prefers longer responses (explicitly penalize verbosity in rubric)
Self-preference: a judge prefers responses written in its own family's style
Instruction-following bias: prefers well-formatted responses regardless of accuracy
```

#### The judge is a reward model wearing a different hat

This is the framing that makes every judge pathology predictable instead of surprising. An RLHF
reward model and an LLM judge are the *same artifact*: a learned, imperfect proxy for human
preference, deployed as the scoring function for an optimization process. Everything the
alignment literature knows about reward hacking transfers directly — see
[Alignment & RLHF](../alignment_and_rlhf/README.md) for the reward-model side, including the
overoptimization curve and the calibration and length-bias measurements.

| | Reward model (RLHF) | LLM judge (evaluation) |
|---|---|---|
| What it approximates | human preference over responses | human preference over responses |
| Who optimizes against it | PPO/GRPO, thousands of gradient steps per hour | your team, iterating prompts and models until the number goes up |
| Optimization loop speed | hours | weeks |
| Failure name | reward hacking | benchmark gaming, judge overfitting |
| Bias that dominates | length | length |
| The only non-circular check | agreement with fresh human labels | agreement with fresh human labels |

The slower loop is the only real difference, and it is why judge overfitting is so easy to miss:
nobody watches a metric climb over three months the way they watch a reward curve climb over three
hours. The same corrective applies — a proxy score is evidence only while it still tracks a gold
signal you measure separately.

#### Measuring judge bias as a quantity

Naming the four biases is table stakes. What separates a working eval programme is that each one
is a **number in the eval report**, recomputed whenever the judge, the rubric, or the model under
test changes.

```
1. POSITION BIAS  ->  flip rate
     run every pairwise item twice, in both orders
     flip_rate = fraction where the verdict changes when A and B swap
     consistency = 1 - flip_rate
     also report the ASYMMETRY: of the flipped items, how many favour
     position 1 vs position 2 (a symmetric flip is noise; a skewed one is bias)
     healthy: flip_rate low AND balanced. Report both, never just one.

2. VERBOSITY BIAS  ->  slope, not a claim
     regress judge score on response token count across the eval set
     report the slope in score-points-per-100-tokens and the partial R^2
     control: score two responses with identical content, one padded with
     150 tokens of on-topic filler. Delta should be 0.

3. SELF-PREFERENCE  ->  win-rate delta against a fixed human ground truth
     take items where humans already scored both responses
     measure the judge's win rate for own-family responses minus the
     win rate a neutral third-family judge gives the same responses

4. FORMATTING BIAS  ->  presentation-only A/B
     hold the content byte-identical, vary only presentation:
     markdown headers + bullets vs one plain paragraph
     any non-zero score delta is pure formatting bias
```

The published anchors for how large these get: Zheng et al. 2023
([arXiv 2306.05685](https://arxiv.org/abs/2306.05685)) measured GPT-4 returning the same pairwise
verdict after a position swap only **65.0%** of the time, and when it flipped it favoured the
first position in 30.0% of cases against the second in 5.0% — a heavily skewed flip, which is the
signature of bias rather than noise. Wang et al. ([arXiv 2305.17926](https://arxiv.org/abs/2305.17926))
made the stakes concrete: by reordering responses alone, they made Vicuna-13B "beat" ChatGPT on
**66 of 80** tested queries with ChatGPT as the evaluator. For self-preference, Panickssery,
Bowman & Feng ([arXiv 2404.13076](https://arxiv.org/abs/2404.13076)) showed GPT-4 and Llama 2 can
distinguish their own outputs from other models' at non-trivial accuracy without any training, and
established a linear relationship between self-recognition ability and self-preference strength via
fine-tuning — the bias is not stylistic coincidence, it is recognition. For verbosity, the
length-controlled AlpacaEval work (Dubois et al.,
[arXiv 2404.04475](https://arxiv.org/abs/2404.04475)) fits a GLM with length difference as a
mediator and reports the preference at zero length difference, which lifted Spearman correlation
with LMSYS Chatbot Arena from **0.94 to 0.98**.

```mermaid
%%{init: {'flowchart': {'curve': 'basis'}, 'theme': 'dark'}}%%
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    ANCH(["Anchor set<br/>human-labelled<br/>frozen"])
    PROBE(["Bias probes<br/>swap · pad · reformat"])
    JUDGE(["Pinned judge<br/>snapshot + prompt<br/>+ temp + parser"])
    NUM(["Bias report<br/>flip rate · slope<br/>self-pref · format delta"])
    GATE{"Any probe<br/>past threshold?"}
    FIX(["Fix rubric<br/>or swap judge"])
    SHIP(["Publish scores<br/>with bias report"])

    ANCH --> JUDGE
    PROBE --> JUDGE --> NUM --> GATE
    GATE -->|"yes"| FIX --> JUDGE
    GATE -->|"no"| SHIP

    class ANCH,PROBE io
    class JUDGE frozen
    class NUM mathOp
    class GATE base
    class FIX lossN
    class SHIP train
```

The loop that matters: bias probes run against the **same pinned judge** that produces the scores,
on every run, and the bias report ships alongside the scores rather than living in a one-off
notebook. A score without its flip rate is not interpretable.

#### Judge drift — the failure that invalidates your history

A judge is a dependency with a version, and hosted judge models are deprecated on the provider's
schedule, not yours. The day you swap `judge-v1` for `judge-v2`, every score you have ever
recorded silently changes meaning, and the first symptom is a "regression" in a product that did
not change. Worse is the attribution problem: when the number drops, you cannot tell whether the
system got worse or the judge got different.

What to pin, and hash into every run record, is not just the model:

```
  judge identity = model snapshot id
                 + judge prompt text (hash it, do not eyeball it)
                 + temperature and top_p
                 + rubric version
                 + output parser version
                 + tie-handling rule

  Change ANY component -> it is a new judge -> the trend line breaks.
```

The migration procedure when you are forced to upgrade:

```
1. Freeze an ANCHOR SET: 200-500 items with human labels, never edited.
2. Dual-run: score the anchor set with BOTH old and new judge.
3. Report three numbers, not one:
     - per-item score delta (mean and spread)
     - rank correlation between the two judges' orderings (Spearman)
     - each judge's agreement with the frozen human labels
4. If human agreement improved and Spearman is high -> re-baseline: re-score
   the historical comparison points you still care about under the new judge.
5. If Spearman is low -> the two judges measure different things. Do NOT
   convert. Cut the trend line, tag the boundary, start a new series.
6. Never mix judges within a single comparison. Never.
```

Step 3's middle number is the one teams skip and the one that decides everything. A high rank
correlation with a shifted mean is a units change you can re-baseline through; a low rank
correlation means the new judge disagrees about *which response is better*, and no amount of
rescaling fixes that. Recent auditing work (Yang, Hou & Yang,
[arXiv 2607.08535](https://arxiv.org/abs/2607.08535), 2026) makes the same point empirically
across four judgment datasets and two upgrade paths — Qwen3 1.7B through 32B, and successive
MiniMax API releases — finding that judge upgrades are **not interchangeable**: only the Qwen3
1.7B-to-4B step gave a robust adjacent gain, and adjacent MiniMax releases did not. Two further
findings from that audit are worth internalising: stronger judges **reduce but do not remove**
position and verbosity bias, so "we upgraded the judge" is never a bias mitigation; and
repeated-sample juries add little when the sampled errors are correlated, which is the judge-side
restatement of the reward-model ensemble result that ensembles sharing a base model share their
mistakes.

#### Rubric design that reduces variance

Judge variance is mostly a prompt-design problem, and five choices carry almost all of it.

| Choice | Lower variance | Why |
|---|---|---|
| Pairwise vs absolute | **pairwise** for model selection | comparison is easier than calibration; absolute scores drift between runs and across topics |
| Absolute rubric | use for **monitoring** a single system over time | pairwise needs a fixed opponent; absolute survives the opponent being retired |
| Reasoning placement | **reason first, score last** | a score emitted before its justification is a prior, not a judgement |
| Scale | **discrete 1-5 with anchored labels**, or continuous via token probabilities | free-form 0-100 clusters on multiples of 5 and carries fake precision |
| Few-shot | **one worked example per scale point** | anchors what a "3" means; without it the scale drifts between batches |

The reason-first rule has direct empirical support. Wang et al.'s *multiple evidence calibration*
requires the evaluator to generate supporting evidence **before** assigning a rating, and it was one
of the interventions that reduced their measured bias. G-Eval (Liu et al.,
[arXiv 2303.16634](https://arxiv.org/abs/2303.16634)) builds the same ordering into its design —
chain-of-thought evaluation steps first, then a form-filling score — reaching Spearman 0.514 with
humans on SummEval summarization, ahead of prior methods; the same paper flags that LLM-based
evaluators carry a bias toward LLM-generated text, which is the self-preference result arriving
from another direction.

G-Eval also supplies the fix for the discrete-scale problem: instead of taking the sampled integer,
take the **probability-weighted sum** of the score tokens, `sum(p(s) * s)`. A judge that is torn
between 3 and 4 emits 3.62 rather than flipping between 3 and 4 across runs, which removes an
entire source of run-to-run variance without adding a call.

```
BROKEN rubric — every failure mode in six lines
  "Rate this response 1-10 for quality. Reply with just the number."
    - no criteria      -> "quality" means something different per item
    - 1-10             -> raters cluster on 7 and 8; the tails go unused
    - score only       -> no reasoning to audit, and no reason-before-score
    - absolute         -> no fixed comparison point across runs
    - one order        -> position bias uncontrolled and unmeasured
    - no tie rule      -> the judge invents one, differently each time

FIXED rubric
  For each of {factual accuracy, instruction compliance, conciseness}:
    1. Quote the specific span of the response that determines the score.
    2. State the reason in one sentence.
    3. THEN emit an integer 1-5 against these anchors:
         5 = fully correct and complete   4 = correct, one minor omission
         3 = mostly correct, one real error
         2 = substantially wrong          1 = fails the instruction entirely
    4. Ties are allowed and must be emitted as "TIE", never broken arbitrarily.
  Run every item in both orders; report the mean and the flip rate.
  Penalise length only via the conciseness axis; never mention length elsewhere.
```

Note what the fixed version does *not* do: it does not tell the judge to "be objective" or "avoid
bias". Instructions to not be biased are unmeasurable and do not survive contact with a flip-rate
probe. Structure the task so the bias has nowhere to act instead.

#### Ensembles, and the cheap-judge/expensive-judge cascade

A panel beats a single judge for the same reason a reward-model ensemble beats a single reward
model, and it fails for the same reason too. Verga et al.
([arXiv 2404.18796](https://arxiv.org/abs/2404.18796)) show that a **Panel of LLM evaluators
(PoLL)** built from several smaller models drawn from **disjoint model families** outperforms a
single large judge across three judge settings and six datasets, at **over seven times lower cost**,
and with less intra-model bias precisely because the families are disjoint. The constraint is the
one the 2026 audit above restates: repeated samples of the *same* judge add little, because the
errors are correlated. Diversity has to be across families, not across seeds or samples — the exact
analogue of the reward-model finding that pretraining-seed diversity generalises better than
fine-tuning-seed diversity.

The cascade is the cost-driven cousin: a cheap judge scores everything, and only ambiguous items
are escalated. The arithmetic is decisive, in units normalised to one expensive-judge call:

```
  10,000 eval items.  expensive judge = 1.00 unit/item.  cheap judge = 0.05.

  all-expensive              10,000 x 1.00                     = 10,000 units
  cascade, 15% escalated     10,000 x 0.05  +  1,500 x 1.00     =  2,000 units  (-80%)
  cascade, 40% escalated     10,000 x 0.05  +  4,000 x 1.00     =  4,500 units  (-55%)
  3-family cheap panel       10,000 x 3 x 0.05                  =  1,500 units  (-85%)

  break-even escalation rate:  0.05 + x = 1.00  ->  x = 0.95
```

The cascade stays cheaper until 95% of items escalate, so cost is never the reason not to build
one — the whole design question is whether the escalation trigger is *accurate*. A judge's
self-reported confidence is the worst available trigger. Three that work: disagreement between two
cheap judges from different families; a score sitting within one point of the decision threshold;
and a pairwise verdict that flips when the order is swapped. That third one is free — you are
already running both orders to measure the flip rate.

#### When a judge is the wrong tool

The most common eval mistake is not a biased judge, it is a judge deployed where an assertion
belongs. If a property can be checked by code that fails deterministically, checking it with an
LLM makes it slower, more expensive, non-reproducible, and *less* accurate.

| Property | Wrong: judge | Right: deterministic assertion |
|---|---|---|
| Output is valid JSON matching a schema | "does this look well-formed?" | `json.loads` + `jsonschema.validate` |
| Required fields present | "does it include the price?" | key presence check |
| No PII leaked | "does this contain personal data?" | regex/NER detector with a fixed pattern set, plus a fixed leak corpus |
| SQL is correct | "is this query right?" | execute both queries, compare result sets |
| Code is correct | "does this code work?" | run the test suite |
| Citation actually supports the claim | "is this well-cited?" | substring/span check that the quoted text exists in the cited chunk |
| Answer matches a known fact | "is this accurate?" | exact or normalised match against the golden answer |
| Refusal on a known-unsafe prompt | "was this a refusal?" | classifier with a frozen threshold, validated once |
| Latency, cost, token count | never | measure it |

```
BROKEN: an LLM judge asked to check structural validity
  judge("Does this response contain a valid ISO-8601 timestamp and a
         non-negative integer 'count' field? Answer YES or NO.")
    - costs a call per item and adds seconds of latency
    - non-deterministic: the same input can flip between runs
    - it will say YES for "2026-13-45T99:00:00Z" often enough to matter

FIXED: assert it, and spend the judge budget where nothing else works
  schema_ok = validate(payload, SCHEMA)            # deterministic, free, exact
  ts_ok     = parse_iso8601(payload["ts"]) is not None
  judge_score = judge(question, payload["answer"], rubric=TONE_RUBRIC)
  # the judge now scores ONE thing no assertion can: whether the explanation
  # is genuinely helpful to a non-expert reader.
```

The rule of thumb: a judge earns its cost only on axes that are irreducibly subjective — helpfulness,
tone, explanation quality, whether a summary preserved the *point* rather than the words. Everything
with a decidable answer should be an assertion, and every assertion you add makes the remaining
judge scores easier to interpret, because the judge is no longer averaging a formatting failure and
a reasoning failure into one number.

---

## 5. Architecture Diagrams

### Evaluation Pipeline

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

    NEW["New Model / Prompt Change"]
    BENCH["Automated Benchmark Suite\nMMLU / domain (knowledge)\nHumanEval / SWE-bench (code)\nRAGAS (RAG) · safety (AdvBench, WildGuard)"]
    REG["Automated Regression Check\nvs. current production model\ngate: no regression > 2%"]
    HUM["Human Evaluation Sample\n1 000 production-representative queries\nLLM-as-judge (cross-family) + 10% human review"]
    AB["A/B Test in Production\n5% traffic · real user feedback\nrun minimum 48 hours"]
    PASS["Gradual Rollout"]
    FAIL["Investigate and Fix"]

    NEW --> BENCH --> REG --> HUM --> AB
    AB -->|"all gates pass"| PASS
    AB -->|"fails any gate"| FAIL

    class NEW,PASS io
    class BENCH,REG,HUM,AB mathOp
    class FAIL lossN
```

---

## 6. How It Works — Detailed Mechanics

### Benchmark Contamination

```
Problem: LLM training data (web crawl) contains benchmark answer pages
  MMLU answers are on Reddit, exam prep sites, Stack Exchange
  HumanEval problems are on LeetCode, GitHub, blogs
  → Models "memorize" answers instead of demonstrating capability

Detection methods:
  n-gram overlap: check if benchmark examples appear in training data
  Membership inference attack: can model reproduce training data exactly?
  Canary tests: insert synthetic fake examples; if model reproduces them → contaminated

Mitigation:
  Hold-out new benchmarks until training data cutoff
  Rotating benchmarks: LiveBench replaces ~1/6 of its questions every month, so
    the set is fully refreshed roughly twice a year
  Private/held-out benchmarks: GPQA and FrontierMath do NOT rotate questions --
    they resist contamination by keeping answers unpublished, which is a
    different mechanism from rotation. Don't conflate the two.
  Open vs. closed benchmarks: closed (held-out) more trustworthy

Note: "model X was caught training on benchmark Y" claims circulate widely and
are rarely backed by a published overlap analysis. Frontier labs do publish
their own contamination estimates in model papers -- cite those rather than
repeating a rumour.
```

### How Precise Is a Benchmark Score?

Every number in Section 4 is an estimate from a finite question set, and those sets are small:
GPQA Diamond is 198 questions, HumanEval 164, SWE-bench Verified 500, MT-Bench 80. A score is
therefore a point estimate with an error bar, and most published model comparisons are inside it.

```
  SE      = sqrt( s_bar (1 - s_bar) / n )        binary-scored benchmark (pass/fail)
  95% CI  = s_bar +/- 1.96 x SE

  comparing two models on the SAME questions:
    SE_unpaired = sqrt( SE_A^2 + SE_B^2 )
    SE_paired   = sqrt( SE_A^2 + SE_B^2 - 2 x SE_A x SE_B x Corr(s_A, s_B) )
```

**Put simply.** "You did not measure the model's ability, you measured how it did on 198
questions drawn from a much larger space of questions you could have asked. Ask a different 198
and the number moves."

| Symbol | What it is |
|--------|------------|
| `n` | Number of questions in the benchmark. GPQA Diamond: 198, not 448 |
| `s_bar` | The reported score, as a fraction. 70% -> 0.70 |
| `SE` | Standard error — how far the score would move on a fresh draw of n questions |
| `Corr(s_A, s_B)` | Per-question correlation between the two models. High for similar models |
| paired | Both models answered the *same* questions, so the shared difficulty cancels |

**Walk one example.** Two models, 71% and 67% on GPQA Diamond:

```
  SE at n = 198, s_bar = 0.70   = sqrt(0.70 x 0.30 / 198)   = 0.0326  -> +/- 3.3 pp
  95% CI on one score                = 0.71 +/- 0.064       -> [64.6%, 77.4%]

  unpaired difference   SE = sqrt(0.0326^2 + 0.0326^2)      = 0.0461  -> +/- 9.0 pp at 95%
      4-point gap vs a 9-point interval  ->  NOT significant

  paired, Corr = 0.9    SE = 0.0326 x sqrt(2 - 2 x 0.9)     = 0.0146  -> +/- 2.9 pp at 95%
      4-point gap vs a 2.9-point interval  ->  significant

  for reference: HumanEval n = 164 at 0.90 -> SE 2.3 pp;  SWE-bench Verified
                 n = 500 at 0.50 -> SE 2.2 pp;  MT-Bench n = 80 is the noisiest of all
```

**Why pairing is the whole game.** Report only the two headline percentages and a 4-point lead is
indistinguishable from luck; keep the per-question results and the same 4 points become solid,
because the questions both models got wrong cancel out of the difference. This is free precision
that is thrown away by every leaderboard row. Two further corrections matter in practice: when
questions come in clusters (MMLU's 57 subjects, several questions per reading passage) the naive
`SE` understates the true one — Miller's "Adding Error Bars to Evals" (arXiv 2411.00640) reports
clustered standard errors over 3x larger than the naive figure — and running each question K times
divides the sampling component of the variance by K. Report `n`, the standard error, and the
pairwise correlation alongside any score you publish internally.

### Evaluation at Different Stages

```
Development:
  Fast feedback: automated unit tests on 100 representative examples
  Cost: cheap models or local models for evaluation
  Goal: catch regressions quickly

Pre-production:
  Full benchmark suite: MMLU, HumanEval, domain benchmarks
  Human eval: 500-1000 examples, 2 annotators per example
  Safety eval: adversarial test suite
  Goal: ensure quality bar before deployment

Production:
  Online metrics: user satisfaction, task completion
  LLM-as-judge: ongoing sample (1-5% of traffic)
  Error analysis: sample failures for root cause
  Goal: continuous monitoring and improvement signal
```

### Custom Evaluation for Production Systems

```python
class ProductionEvaluator:
    def __init__(self, judge_model, domain_test_set):
        self.judge = judge_model
        self.test_set = domain_test_set  # (question, ground_truth) pairs

    def evaluate_accuracy(self, model):
        """Domain-specific factual accuracy."""
        correct = 0
        for question, ground_truth in self.test_set:
            response = model(question)
            # Exact match (for factual Q&A) or LLM judge
            if self.is_correct(response, ground_truth):
                correct += 1
        return correct / len(self.test_set)

    def is_correct(self, response, ground_truth):
        # For verifiable tasks: string match or execution
        # For open-ended: LLM judge
        prompt = f"""Is the following response equivalent to the reference answer?
Reference: {ground_truth}
Response: {response}
Answer: [yes/no]"""
        return "yes" in self.judge(prompt).lower()

    def evaluate_safety(self, model, attack_suite):
        """Rate of unsafe responses on adversarial prompts."""
        failures = 0
        for attack_prompt in attack_suite:
            response = model(attack_prompt)
            if self.safety_classifier(response) == "unsafe":
                failures += 1
        return 1 - failures / len(attack_suite)  # Safety rate
```

### A/B Testing for LLM Systems

A/B testing LLMs is fundamentally harder than A/B testing click-through rates or conversion funnels because text outputs have high variance and quality is multidimensional.

```
Challenge: High variance in text outputs
  A button color A/B test: binary outcome (click or not), low variance
  An LLM A/B test: open-ended text, quality is subjective, variance is enormous
  → Statistical significance is much harder to achieve

Sample sizes:
  Click-through A/B test: ~200-500 samples per variant often sufficient
  LLM quality A/B test: 1000-5000+ comparisons per variant typically needed
  For small effect sizes (2-5% improvement): may need 10,000+ comparisons
  Power analysis must account for high output variance — standard calculators underestimate

Metrics for LLM A/B tests:
  Win rate: pairwise comparison — what % of time does variant B beat variant A?
  Elo rating: continuous rating derived from pairwise comparisons (Chatbot Arena style)
  Quality score distribution: histogram of LLM-as-judge scores per variant
  Implicit signals: regeneration rate, session length, task completion, thumbs up/down

LLM-as-judge for A/B:
  Use a judge model to compare outputs from variant A vs B on same input
  Run both orderings (A first, B first) to cancel position bias
  Aggregate win rates with confidence intervals
  Cost: derive it, don't quote it. On a mid-tier 2026 judge (~$2.50 per MTok
    input, ~$15 per MTok output) one judge call at 1,500 in / 200 out costs
    1500 x 2.5e-6 + 200 x 15e-6 = $0.0068; long responses (5,000 in / 400 out)
    push it to ~$0.019. Position-swapped = two calls per comparison, so
    budget roughly $0.014-0.04 per comparison.

Stratification (critical for LLM A/B tests):
  Split results by query type (factual, creative, reasoning, code)
  Split by complexity (simple, medium, hard)
  Split by domain (finance, medical, general)
  Why: a model can improve 10% on creative tasks but regress 5% on factual
    — aggregate metric shows 3% improvement, masking a real regression
  Always report per-category results alongside aggregate

Duration:
  Minimum 7-14 days to capture temporal patterns
  Weekend vs weekday usage patterns differ (consumer apps)
  Business hours vs off-hours (enterprise apps)
  New model "novelty effect" — users initially engage more, then revert
  Run at least 2 full weekly cycles before making rollout decisions
```

Those sample-size numbers are not folklore — they fall out of the standard two-proportion power
formula:

```
n per variant = (z_(alpha/2) + z_beta)^2 x 2 x p(1 - p) / delta^2

with the usual alpha = 0.05 (two-sided) and 80% power:
  z_(alpha/2) = 1.96,  z_beta = 0.84,  (1.96 + 0.84)^2 = 7.84
  -> n approximately 16 x p(1 - p) / delta^2
```

**What this actually says.** "The number of comparisons you need grows with how noisy each
comparison is, and explodes as the square of how small an effect you are trying to see."

The `delta^2` in the denominator is the part people get wrong in planning meetings. Halving the
effect you want to detect does not double the sample — it quadruples it. That single fact explains
why "we'll just eyeball 200 comparisons" fails for LLM A/B tests and why chasing a 2% win-rate
improvement is a fundamentally different project from chasing a 10% one.

| Symbol | What it is |
|--------|------------|
| `n` | Comparisons needed *per variant*, so budget 2n judge calls total |
| `alpha` | False-positive rate you accept. 0.05 = "1 in 20 chance I cry wolf" |
| `z_(alpha/2)` | 1.96 — how many standard errors out the 95% cutoff sits |
| `beta` | False-negative rate. Power = `1 - beta`; 80% power means beta = 0.20 |
| `z_beta` | 0.84 — the extra margin needed to *reliably see* a real effect, not just not-deny it |
| `p` | Baseline win rate. `p(1-p)` is the variance of a coin flip, maximal at p = 0.5 |
| `delta` | The smallest win-rate lift you care about detecting. Squared in the denominator |

**Walk one example.** Pairwise A/B, baseline win rate `p = 0.50` (a true coin flip — the
worst case, maximum variance):

```
  detect delta = 0.10   n = 16 x 0.25 / 0.01     =    400 per variant
  detect delta = 0.05   n = 16 x 0.25 / 0.0025   =  1 600 per variant
  detect delta = 0.03   n = 16 x 0.25 / 0.0009   =  4 444 per variant
  detect delta = 0.02   n = 16 x 0.25 / 0.0004   = 10 000 per variant

  halve delta (0.04 -> 0.02)  ->  4x the comparisons. Not 2x.
```

That lands squarely on the "1000-5000+ comparisons per variant" and "10,000+ for 2-5% effects"
guidance above. Priced with the arithmetic in the box above — two position-swapped judge calls per
comparison at $0.007-0.019 each — the 2% test's 10,000 comparisons cost roughly $140-400 in judge
calls alone, and that is before any human review of the disagreements.

**Why `z_beta` is in there at all.** Drop it and you get the far smaller `n = 4 p(1-p)/delta^2`,
which is the sample size at which a real effect is merely *not ruled out* — a coin flip as to
whether your test detects it. Adding `z_beta = 0.84` roughly doubles the requirement and buys you
an 80% chance of actually catching the improvement you shipped. Teams that skip it run
underpowered tests, see "no significant difference," and conclude their improvement did nothing.

```python
class LLMABTest:
    def __init__(self, judge_model, categories: list[str]):
        self.judge = judge_model
        self.categories = categories
        self.results = {cat: {"a_wins": 0, "b_wins": 0, "ties": 0}
                        for cat in categories}

    def compare(self, query: str, response_a: str, response_b: str,
                category: str):
        """Pairwise comparison with position-bias cancellation."""
        # Run both orderings
        verdict_ab = self._judge_pair(query, response_a, response_b)
        verdict_ba = self._judge_pair(query, response_b, response_a)

        # Aggregate: only count if both orderings agree
        if verdict_ab == "A" and verdict_ba == "B":
            self.results[category]["a_wins"] += 1
        elif verdict_ab == "B" and verdict_ba == "A":
            self.results[category]["b_wins"] += 1
        else:
            self.results[category]["ties"] += 1

    def report(self) -> dict:
        """Win rates per category with confidence intervals."""
        report = {}
        for cat, counts in self.results.items():
            total = counts["a_wins"] + counts["b_wins"] + counts["ties"]
            if total == 0:
                continue
            b_win_rate = counts["b_wins"] / total
            # Wilson score interval for binomial proportion
            ci = self._wilson_ci(counts["b_wins"], total, z=1.96)
            report[cat] = {
                "b_win_rate": b_win_rate,
                "ci_lower": ci[0], "ci_upper": ci[1],
                "n": total,
                "significant": ci[0] > 0.5 or ci[1] < 0.5
            }
        return report
```

The `_wilson_ci` call above hides the actual arithmetic. The Wilson score interval for a
proportion is:

```
                p_hat + z^2/(2n)              z                  p_hat(1 - p_hat)     z^2
  center =  ----------------------    hw = ---------- x  sqrt(  ------------------ + ------ )
                  1 + z^2/n                1 + z^2/n                    n             4n^2

  interval = center +/- hw          (z = 1.96 for 95% confidence)
```

**In plain terms.** "Take your observed win rate, drag it a little toward 50/50
because small samples lie, and put an error bar around it that stays inside 0 and 1."

The shrink-toward-the-middle is the whole reason to prefer Wilson over the textbook
`p_hat +/- z x sqrt(p_hat(1-p_hat)/n)`. The naive interval collapses to zero width when `p_hat`
hits 0 or 1 — 20 comparisons, 20 wins, and it reports "win rate is exactly 100%, no uncertainty,"
which is exactly the situation where you are least sure of anything.

| Symbol | What it is |
|--------|------------|
| `p_hat` | The *observed* win rate. The hat means "estimated from data", not the true value |
| `n` | Number of comparisons that produced `p_hat` |
| `z` | 1.96 for 95% confidence. Widen to 2.576 if you want 99% |
| `z^2/(2n)` | The nudge toward 0.5. Shrinks as n grows, vanishing on large samples |
| `1 + z^2/n` | Divides everything, pulling the whole interval inward for small n |
| `sqrt(...)` | Converts variance back to the units of the win rate itself |
| `hw` | Half the error bar. Report `center +/- hw` |

**Walk one example.** Variant B won 530 of 1,000 comparisons, so `p_hat = 0.530`:

```
  z^2        = 1.96^2                              = 3.8416
  z^2/n      = 3.8416 / 1000                       = 0.00384
  z^2/(2n)   = 0.00192

  center     = (0.530 + 0.00192) / 1.00384         = 0.5299

  variance   = 0.530 x 0.470 / 1000                = 0.0002491
  z^2/(4n^2) = 3.8416 / 4 000 000                  = 0.0000010
  sqrt(sum)  = sqrt(0.0002501)                     = 0.01581
  hw         = 1.96 x 0.01581 / 1.00384            = 0.0309

  95% CI     = [0.4990, 0.5608]
```

The lower bound sits at 0.4990 — a hair *below* 0.5 — so `ci[0] > 0.5` is false and the code
correctly refuses to call it significant. A 53% win rate over a thousand comparisons is still
consistent with the two variants being identical. This is the concrete version of the sample-size
math above: to make a 3-point lift significant you needed roughly 4,444 comparisons, and you ran
1,000.

### Model Drift Detection

Model drift is a silent production killer — API providers update models, weights shift during continued training, and capabilities quietly regress on specific tasks without any alert.

```
Capability regression:
  Model updates or API changes silently degrade specific capabilities
  Documented example: Chen, Zaharia & Zou, "How Is ChatGPT's Behavior Changing
    over Time?" (arXiv 2307.09009) measured GPT-4 in March vs June 2023 and
    found prime-vs-composite identification fell 84% -> 51%, reduced
    responsiveness to chain-of-thought prompting, and more code-formatting
    mistakes. OpenAI publicly disputed that the model had gotten worse, and
    critics noted the code metric penalised non-executable markdown fences --
    so treat this as a well-known dispute, not a settled regression.
  Root cause: model updates optimize for aggregate quality but can regress
    on specific subcategories (Goodhart's Law at scale)

Detection methods:
  1. Weekly benchmark tracking:
     Run golden dataset evaluation every 7 days (or on every model version change)
     Track per-category scores, not just aggregate
     Plot trend lines — gradual 1% weekly drift adds up to 10%+ over a quarter

  2. Per-category quality metrics:
     Don't just track "overall accuracy" — break down by:
       - Task type (QA, summarization, code, reasoning)
       - Domain (medical, legal, financial, general)
       - Difficulty tier (easy, medium, hard)
     A 2% aggregate improvement can mask a 15% regression in a critical category

  3. Automated regression suite:
     Run golden dataset evaluation on every model update
     Golden set: 200-500 curated examples with verified correct answers
     Must cover all critical use cases and edge cases
     Version-control the golden set alongside application code

Alert thresholds:
  >3% drop on any single benchmark category → automated investigation trigger
  >5% drop on any category → block deployment, require human review
  >2% drop on aggregate score → flag for review within 24 hours
  Consecutive 1% weekly drops for 3+ weeks → trend alert (slow drift)

Shadow evaluation pattern (production best practice):
  ┌──────────────┐    ┌───────────────────┐
  │ Production    │    │ Shadow Pipeline    │
  │ Model v2.1   │    │ Candidate v2.2      │
  │ (serves users)│    │ (no user traffic)  │
  └──────┬───────┘    └──────┬────────────┘
         │                    │
         │    ┌───────────┐   │
         └───→│ Golden Set │←──┘
              │ Evaluator  │
              └─────┬─────┘
                    │
              ┌─────v─────┐
              │ Compare    │
              │ v2.1 vs    │
              │ v2.2       │
              └─────┬─────┘
                    │
         Pass: promote v2.2 to production
         Fail: investigate regressions before any user exposure
```

```python
class DriftDetector:
    def __init__(self, golden_set, judge_model, alert_threshold=0.03):
        self.golden_set = golden_set  # {category: [(query, expected), ...]}
        self.judge = judge_model
        self.threshold = alert_threshold
        self.history = {}  # {category: [score_t0, score_t1, ...]}

    def evaluate_and_check(self, model, model_version: str) -> dict:
        """Run golden set evaluation and check for regressions."""
        alerts = []
        for category, examples in self.golden_set.items():
            score = self._evaluate_category(model, examples)

            if category in self.history and len(self.history[category]) > 0:
                prev_score = self.history[category][-1]
                delta = score - prev_score
                if delta < -self.threshold:
                    alerts.append({
                        "category": category,
                        "current": score,
                        "previous": prev_score,
                        "delta": delta,
                        "severity": "critical" if delta < -0.05 else "warning"
                    })

            self.history.setdefault(category, []).append(score)

        return {
            "model_version": model_version,
            "scores": {cat: scores[-1] for cat, scores in self.history.items()},
            "alerts": alerts,
            "deploy_ok": len([a for a in alerts if a["severity"] == "critical"]) == 0
        }
```

### Reference-Based Text Metrics — BLEU, ROUGE, METEOR

These are the cheap metrics referenced throughout this module (ROUGE-L appears in the case study's
automated-metrics box). All three compare a generated string to a human reference, and all three
are built from n-gram overlap — they differ in what they do about length and paraphrase.

```
BLEU  = BP x exp( sum_{n=1..4} w_n x log p_n )         w_n = 1/4 for each n

  p_n = (n-grams in candidate that also appear in reference) / (n-grams in candidate)
  BP  = 1                  if c > r          <- brevity penalty
        exp(1 - r/c)       if c <= r
  c   = candidate length in tokens,  r = reference length

ROUGE-N = (overlapping n-grams) / (n-grams in the REFERENCE)      <- recall, not precision
ROUGE-L = F1 over the Longest Common Subsequence:
          P = LCS/c,  R = LCS/r,  F1 = 2PR / (P + R)

METEOR  = F_mean x (1 - penalty)
  F_mean  = 10 P R / (R + 9 P)                <- recall weighted 9x more than precision
  penalty = 0.5 x (chunks / matches)^3        <- chunks = contiguous runs of matched words
```

**Read it like this.** "BLEU asks 'how much of what you wrote appears in the reference?'
(precision, so it needs a brevity penalty or you'd game it by writing three words). ROUGE flips
that and asks 'how much of the reference did you cover?' (recall, so it needs a length cap or
you'd game it by writing everything). METEOR asks the same question but punishes you for
scrambling the word order."

The precision/recall split is why BLEU became the translation metric and ROUGE the summarization
metric. A translation that omits half the sentence is broken, so you police length; a summary that
omits half the source is doing its job, so you police coverage instead.

| Symbol | What it is |
|--------|------------|
| `p_n` | Fraction of the candidate's n-grams found in the reference. `p_1` = single words |
| `sum_{n=1..4}` | Add up the log precisions for 1-, 2-, 3-, 4-grams |
| `w_n` | Weight per n-gram order. Uniform 0.25 each in standard BLEU-4 |
| `exp(sum w_n log p_n)` | Multiplying the p_n and taking the 4th root. One zero -> whole score zero |
| `BP` | A multiplier <= 1 that punishes candidates shorter than the reference |
| `c`, `r` | Candidate length, reference length, in tokens |
| `LCS` | Longest word sequence appearing in both, order preserved, gaps allowed |
| `F_mean` | METEOR's recall-heavy harmonic mean. The 9 makes recall count 9x precision |
| chunks | Number of contiguous matched runs. 1 chunk = perfect order, many chunks = scrambled |

**Walk one example.** One candidate of `c = 9` tokens against a reference of `r = 12` tokens, with
7 unigram matches forming 3 contiguous chunks, and an LCS of length 7:

```
  BLEU
    p_1 = 7/9 = 0.778     p_2 = 5/8 = 0.625
    p_3 = 3/7 = 0.429     p_4 = 2/6 = 0.333
    geometric mean = (0.778 x 0.625 x 0.429 x 0.333)^(1/4)
                   = 0.0695^(0.25)                          = 0.513
    BP: c=9 <= r=12  ->  exp(1 - 12/9) = exp(-0.333)        = 0.717
    BLEU = 0.717 x 0.513                                    = 0.368  -> reported as 36.8

  ROUGE-L
    P  = 7/9  = 0.778        R = 7/12 = 0.583
    F1 = 2 x 0.778 x 0.583 / (0.778 + 0.583)
       = 0.907 / 1.361                                      = 0.667

  METEOR
    F_mean  = 10 x 0.778 x 0.583 / (0.583 + 9 x 0.778)
            = 4.537 / 7.583                                 = 0.598
    penalty = 0.5 x (3/7)^3 = 0.5 x 0.0787                  = 0.039
    METEOR  = 0.598 x (1 - 0.039)                           = 0.575

  Same output, same reference:  BLEU 0.37 | ROUGE-L 0.67 | METEOR 0.58
```

Three metrics, one output, scores spread across 30 points. Never compare a BLEU number against a
ROUGE number, and never compare BLEU across papers that used different tokenizers — `p_n` is
defined over tokens, so the tokenizer is part of the metric.

**Why the brevity penalty exists.** Delete it and BLEU is trivially gamed: emit the single most
predictable word of the reference and `p_1 = 1.0`. The 2002 BLEU paper added `BP` precisely
because precision alone rewards truncation, and unlike ROUGE there is no recall term to stop it.
Note `BP` is capped at 1 — being *longer* than the reference costs you nothing directly, it just
drags the `p_n` down naturally as the extra n-grams miss.

### Perplexity

The intrinsic metric reported for base models, before any task benchmark:

```
                       1   N
  PPL(x) = exp( -  ---  sum  log p(x_i | x_<i) )
                       N  i=1
```

**What it means.** "On average, how many equally-likely words was the model torn
between at each step? Lower is better; 1 would mean perfect certainty."

Perplexity is the exponential of average cross-entropy loss, which is why it is essentially free —
it is your training loss, re-expressed in a unit humans can reason about. "Loss 1.10" means
nothing intuitively; "the model was choosing among about 3 options per token" does.

| Symbol | What it is |
|--------|------------|
| `PPL` | Effective branching factor: how many options the model was hedging across |
| `N` | Number of tokens scored |
| `sum_{i=1..N}` | Add up the log-probabilities the model assigned to the true tokens |
| `p(x_i \| x_<i)` | The model's confidence in the token that actually came next |
| `log p(...)` | Always negative (probabilities < 1). Very wrong -> very large negative |
| `-(1/N) sum` | This *is* cross-entropy loss, in nats |
| `exp(...)` | Undoes the log, converting nats back into a token count |

**Walk one example.** Five tokens, with the model assigning these probabilities to the tokens that
actually appeared:

```
  token   p        log p
    1     0.50    -0.693
    2     0.25    -1.386
    3     0.40    -0.916
    4     0.10    -2.303      <- the token it did not see coming
    5     0.80    -0.223
                  -------
           sum  =  -5.521

  mean log p  = -5.521 / 5     = -1.104     <- this is the cross-entropy loss
  PPL         = exp(1.104)     =  3.02      <- "about 3 plausible next tokens"
```

**Why perplexity alone is not enough.** It is defined over *your* tokenizer and *your* held-out
corpus, so PPL 3.02 is not comparable across models with different vocabularies, and a model can
lower perplexity by getting better at predicting boilerplate it will never be asked to generate.
It is the right metric for "is pre-training converging?" and the wrong one for "is this assistant
useful?" — which is what the entire rest of this module exists to answer.

### Inter-Annotator Agreement — Cohen's and Fleiss' Kappa

The case study's "judge-human agreement 0.78" row and every "our annotators agreed 78% of the
time" claim need chance-correction before they mean anything:

```
  kappa = (p_o - p_e) / (1 - p_e)

  p_o = observed agreement  = (times the two raters gave the same label) / (total items)
  p_e = expected agreement by chance
      = sum over categories c of  P(rater A picks c) x P(rater B picks c)

  Fleiss' kappa: same formula, generalized to more than 2 raters --
    p_e uses the overall proportion of each category across ALL raters.
```

**Put simply.** "Of the agreement that was actually available to be earned, what
fraction did the raters earn? Two people who both label everything 'pass' agree 100% of the time
and have learned nothing."

The `(1 - p_e)` denominator is the entire idea: it is the headroom above coin-flipping. Raw
agreement is inflated by whatever the base rate happens to be, and eval sets are almost always
imbalanced — most responses are fine — so raw agreement on a 90%-pass set starts near 0.90 before
either annotator thinks at all.

| Symbol | What it is |
|--------|------------|
| `kappa` | Chance-corrected agreement. 1 = perfect, 0 = no better than chance, < 0 = worse |
| `p_o` | The raw agreement rate. The number people quote and shouldn't |
| `p_e` | Agreement you'd get if both raters guessed independently at their own base rates |
| `p_o - p_e` | How far past chance the raters got |
| `1 - p_e` | The headroom. Small when the labels are lopsided |
| `sum over categories` | Multiply each label's two marginal rates, then add across labels |

**Walk one example.** 100 responses double-labeled pass/fail. The two raters gave the same verdict
on 78 of them. Rater A said "pass" 70 times, rater B said "pass" 74 times:

```
  p_o = 78 / 100                                              = 0.780

  chance agreement on "pass"   = 0.70 x 0.74                  = 0.518
  chance agreement on "fail"   = 0.30 x 0.26                  = 0.078
  p_e                                                         = 0.596

  kappa = (0.780 - 0.596) / (1 - 0.596)
        = 0.184 / 0.404                                       = 0.455

  headline "78% agreement"  ->  kappa 0.46, only MODERATE
```

Conventional reading: `< 0.20` slight, `0.21-0.40` fair, `0.41-0.60` moderate, `0.61-0.80`
substantial, `> 0.80` almost perfect. A judge-vs-human `kappa` of 0.46 is not a validated judge,
even though 78% sounds like one.

**Why chance correction is load-bearing here specifically.** Push both raters' pass rate to 90%
and chance agreement becomes `p_e = 0.90 x 0.90 + 0.10 x 0.10 = 0.82`. Two raters agreeing 82% of
the time then score `kappa = (0.82 - 0.82) / 0.18 = 0.00` — they agreed exactly as often as two
people flipping the same biased coin, despite an 82% headline. Report raw agreement on a
lopsided eval set and you will conclude your rubric is reliable when your annotators are in fact
providing no information at all. Use Cohen's kappa for exactly two raters, Fleiss' when three or
more raters each label the same items, and Krippendorff's alpha when raters skip items or the
labels are ordinal (1-5 rubric scores) rather than categorical.

---

## 7. Real-World Examples

### OpenAI Evals
- Open-source evaluation framework and registry (github.com/openai/evals)
- Community-contributed registry: 463 eval YAML files under
  `evals/registry/evals` as of July 2026 (each may define several variants)
- Structured format: jsonl files with input/ideal output

### HELM (Holistic Evaluation of Language Models)
- Stanford CRFM initiative (Liang et al., arXiv 2211.09110)
- HELM classic: 42 scenarios (16 core + 26 targeted) and 7 metrics — accuracy,
  calibration, robustness, fairness, bias, toxicity, efficiency
- The 7-metric-per-core-scenario grid is the point: one number per model is
  exactly what HELM is arguing against
- Public leaderboard; has since branched into several HELM variants

### LiveBench (2024)
- "LiveBench: A Challenging, Contamination-Limited LLM Benchmark", arXiv 2406.19314
- Roughly one sixth of the questions are replaced each month, so the set fully
  turns over about every six months
- Sources questions from recent math competitions, arXiv papers, news articles
  and recently modified repos — too new to be in older training data
- Ground truth is objective and machine-checkable, so there is no LLM judge in
  the scoring loop
- Covers: reasoning, math, coding, language comprehension, data analysis

---

## 8. Tradeoffs

| Evaluation Method | Accuracy | Scalability | Cost | Bias |
|------------------|---------|-------------|------|------|
| Human evaluation | Highest | Low | High | Human rater bias |
| LLM-as-judge | Good | High | Medium | Self-preference, verbosity |
| Automated benchmarks | Limited | Very High | Very Low | Contamination risk |
| A/B user testing | Real-world | Medium | Infra | Selection bias |
| Task-specific metrics | Domain-specific | High | Low | Narrow scope |

---

## 9. When to Use / When NOT to Use

### Use MMLU / Standard Benchmarks When:
- Comparing models for initial selection
- Need a standard for stakeholder communication
- Tracking broad capability over time

### Use LLM-as-Judge When:
- Open-ended tasks (no ground truth)
- Scaling to thousands of examples
- Comparing relative quality between two approaches

### Use Human Evaluation When:
- High-stakes deployment decision
- New capability where automated eval isn't calibrated yet
- Validating LLM-as-judge reliability

---

## 10. Common Pitfalls

1. **Benchmark shopping**: Reporting only the benchmarks where your model looks good. Best practice: report a standardized suite and disclose any that are unfavorable.
2. **Ignoring benchmark contamination**: Not checking if test set examples are in training data.
3. **Using the same judge model as the model being evaluated**: a model judging its own outputs is biased (self-enhancement bias).
4. **Single-metric optimization**: Optimizing MMLU causes capability regression on other tasks (Goodhart's Law).
5. **Not testing on your domain**: A model scoring 86% on MMLU might score 60% on your medical QA domain.
6. **Ignoring latency in evaluation**: A model that scores 5% better but runs 3× slower may be worse for production.
7. **Treating LLM evaluation as deterministic**: Same prompt with temperature=0 can still vary across runs due to floating-point non-associativity, GPU batching differences, and provider-side model updates. *Illustrative:* a team's "deterministic" suite swings several points on the same unchanged model across consecutive runs, and launch decisions get made on that noise. Mitigation: run each evaluation 3-5 times, report mean and 95% confidence intervals, and only flag changes that exceed the confidence interval.
8. **Trusting single-run LLM-as-judge scores**: Judges are not self-consistent — Zheng et al. 2023 measured GPT-4 returning the same pairwise verdict after a position swap only 65% of the time, so a single run's score carries real variance. *Illustrative:* an "improved" prompt turns out to be indistinguishable from the baseline once the judge evaluation is repeated three times. Mitigation: use majority voting with 3+ independent judge evaluations per sample, and report the agreement rate alongside quality scores. If inter-judge agreement is low, the evaluation rubric needs refinement, not more samples.
9. **Ignoring evaluation prompt sensitivity**: Rewording an LLM-as-judge prompt shifts aggregate scores even when model, test set and judge are unchanged — the rubric wording is part of the metric. *Illustrative:* "Rate the helpfulness of this response" versus "How helpful is this response?" moving a mean rubric score by a few tenths of a point. Best practice: version-lock evaluation prompts, judge models, and all parameters (temperature, max_tokens, system prompt). Treat evaluation infrastructure as production code with the same rigor around versioning, testing, and change management.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **RAGAS** | RAG evaluation | Faithfulness, relevance, recall |
| **LangSmith** | Evaluation + tracing | Annotation workflow, online eval |
| **TruLens** | LLM evaluation | RAG triad: context relevance, groundedness |
| **DeepEval** | LLM test framework | pytest-like; many metrics |
| **OpenAI Evals** | Open-source eval framework | Community registry, hundreds of eval definitions |
| **Eleuther Harness** | Language model eval | Standard open-source benchmarks |
| **HELM** | Holistic evaluation | Stanford; multi-scenario |
| **Chatbot Arena** | Human preference | Real users, Elo ratings |
| **LiteLLM** | Multi-provider eval | Run same eval across multiple models |
| **Weights & Biases** | Experiment tracking | Track eval metrics over time |

---

## 12. Interview Questions with Answers

**Q: What is LLM-as-judge and what are its limitations?**
**Short:** LLM-as-judge suffers self-preference, verbosity, and position biases, so it needs diverse judges, randomized order, and validation against human labels.
A: LLM-as-judge uses a capable model to evaluate another model's responses — rating quality, comparing two responses, or checking correctness. Limitations: (1) self-preference bias — a judge rates responses in its own family's style higher; (2) verbosity bias — longer responses rated higher regardless of quality; (3) position bias — first response shown often preferred; (4) instruction-following bias — well-formatted responses preferred; (5) can't catch factual errors the judge model also makes. Mitigations: use diverse judges, randomize position, include explicit rubrics, validate against human judgments.

**Q: Why is benchmark contamination a problem and how do you detect it?**
**Short:** Benchmark contamination is test data leaking into training, detected via n-gram overlap analysis and avoided with continuously refreshed held-out benchmarks.
A: Contamination occurs when benchmark test examples appear in training data, so the model "memorizes" answers rather than demonstrating the underlying capability. It inflates scores and makes models look better than they are. Detection: (1) n-gram overlap analysis between training data and benchmarks; (2) membership inference — does the model reproduce benchmark examples verbatim?; (3) performance anomalies — unusually high scores on specific subsets. Solution: use held-out benchmarks released after the model's training cutoff, or continuously refreshed benchmarks (LiveBench, competitive math).

**Q: Why can the same evaluation suite at temperature=0 produce different scores across runs?**
**Short:** Temperature=0 isn't deterministic because floating-point non-associativity and GPU batching still shift outputs, so report a mean over several runs.
A: Because temperature=0 does not make LLM inference deterministic — floating-point non-associativity, GPU batching differences, and silent provider-side model updates all shift outputs between runs. A suite that swings several points on the same unchanged model across consecutive runs turns launch decisions into coin flips; LLM judges compound this, since Zheng et al. 2023 measured GPT-4 giving the same pairwise verdict after a position swap only 65% of the time. Run each evaluation 3-5 times, report the mean with a 95% confidence interval, and only act on changes that exceed that interval.

**Q: Model A scores 71% and model B 67% on GPQA Diamond — is A actually better?**
**Short:** A 4-point GPQA Diamond gap sits inside the noise on 198 questions unless the paired per-question correlation shrinks the interval enough to be significant.
A: Not from those two numbers alone — GPQA Diamond has only 198 questions, so each score carries about 3.3 points of standard error and a 4-point gap sits inside the noise. Concretely, `SE = sqrt(0.70 x 0.30 / 198) = 0.033`, the 95% interval on a single score is roughly plus or minus 6.4 points, and the unpaired difference of two such scores has a 95% interval of about 9 points. The gap becomes decidable only if you keep the per-question results: because both models answered the same 198 questions, the paired standard error is `sqrt(SE_A^2 + SE_B^2 - 2 SE_A SE_B Corr)`, which at a plausible per-question correlation of 0.9 shrinks the interval to about 2.9 points and makes the 4-point lead significant. Two further corrections: clustered questions (MMLU's 57 subjects, several questions per passage) inflate the true standard error well above the naive one, and small suites like MT-Bench's 80 prompts are noisier still. Ask for `n`, the standard error, and the pairwise correlation before believing any leaderboard delta.

**Q: Why can an aggregate A/B test metric hide a real regression?**
**Short:** An aggregate A/B metric can average a real regression in one category against a gain in another, so LLM tests must report stratified per-category win rates.
A: Because LLM quality changes are rarely uniform across query types, so a gain in one category can fully hide a loss in another. A model can improve 10% on creative tasks while regressing 5% on factual tasks, and the blended metric reports a ~3% "improvement" that masks the regression. This is why stratification is mandatory for LLM A/B tests: split results by task type (factual, creative, reasoning, code), difficulty tier, and domain, since a 2% aggregate gain can coexist with a 15% drop in a critical category. Always report per-category win rates alongside the aggregate, and block rollout on any critical-category regression even when the aggregate improves.

**Q: What is RAGAS and what does it measure?**
**Short:** RAGAS scores RAG systems on faithfulness, answer relevancy, context recall, and context precision to separate retrieval failures from generation failures.
A: RAGAS is an evaluation framework for RAG systems. It measures four dimensions: (1) Faithfulness — is the generated answer supported by the retrieved context (no hallucination)?; (2) Answer Relevancy — does the answer address the question asked?; (3) Context Recall — did the retrieval system find all relevant documents?; (4) Context Precision — what fraction of retrieved documents are actually relevant? Together, these diagnose whether failures come from retrieval (bad recall/precision) or generation (low faithfulness/relevancy).

**Q: How would you build a custom evaluation system for a production LLM application?**
**Short:** A production eval system layers task-specific metrics, a golden test set, automated regression runs, LLM-as-judge scoring, and periodic human review.
A: Build it in six layers: task-specific metrics, a golden test set, automated regression runs, LLM-as-judge scoring for open-ended aspects, online sampling, and a weekly human review. In order: (1) Define task-specific metrics aligned with business goals (e.g., SQL execution accuracy, customer resolution rate, factual accuracy on domain Q&A); (2) Build a golden test set: 200-500 examples with human-verified correct answers; (3) Automated evaluation: run on every model/prompt change; fail if regression > threshold; (4) LLM-as-judge for open-ended aspects: helpfulness, clarity; validate judge against human labels; (5) Online evaluation: sample 1-5% of production traffic, use user feedback (implicit: session continuation, explicit: ratings); (6) Weekly human review sample: manually inspect 50-100 cases for systematic issues automated metrics miss.

**Q: How reliable is LLM-as-judge evaluation and what are its biases?**
**Short:** LLM-as-judge reaches over 80% pairwise agreement with humans, matching human-human agreement, but position bias alone flips consistency to only 65%.
LLM-as-judge reaches over 80% agreement with human evaluators on pairwise preference tasks, the same level humans agree with each other (Zheng et al. 2023, arXiv 2306.05685). Known biases: (1) position bias — in that paper's Table 2, GPT-4 returns the same verdict after swapping the two responses only 65.0% of the time, and when it flips it favours the first position in 30.0% of cases versus the second in 5.0% (mitigate by evaluating both orders and averaging); (2) verbosity bias — judges prefer longer, more detailed responses even when shorter ones are more accurate; (3) self-preference — models rate their own outputs higher than competitors' outputs (don't use GPT-4 to judge GPT-4 vs Claude); (4) sycophancy — judges agree with confident-sounding responses regardless of accuracy. Mitigation: (1) use reference-based judging (provide the correct answer for comparison); (2) use structured rubrics with explicit criteria and scoring scales; (3) average across multiple judge models; (4) calibrate with a human-evaluated validation set. For production: LLM-as-judge is practical for automated quality monitoring at scale, but high-stakes evaluations (model selection, launch decisions) should include human evaluation on a representative sample.

**Q: How does benchmark contamination occur and how do you detect it?**
**Short:** Contamination enters via web-crawled benchmark text, GPT-4-derived synthetic data, or pipeline leaks, and is caught by canary strings and score-gap analysis.
Benchmark contamination happens when test set data appears in the model's training corpus, inflating benchmark scores beyond true capability. Sources: (1) web crawl — popular benchmarks (MMLU, HumanEval) appear on blogs, forums, and GitHub discussions; (2) synthetic data — models trained on GPT-4 outputs may inherit GPT-4's memorized benchmark answers; (3) data pipeline leaks — evaluation datasets accidentally included in training splits. Detection: (1) n-gram overlap analysis — check for exact or near-exact matches between training data and benchmark questions; (2) canary strings — embed unique identifiers in evaluation data and check if models reproduce them; (3) performance gap analysis — if a model scores 90% on public benchmarks but only 70% on held-out private tests of similar difficulty, suspect contamination; (4) memorization probing — test if the model can complete benchmark questions from partial prompts. Frontier labs (OpenAI, Anthropic, Google) now maintain private evaluation suites specifically to avoid contamination. For your own evaluations: always create domain-specific test sets from data generated after your model's training cutoff.

**Q: How do you interpret RAGAS metrics for RAG evaluation?**
**Short:** Reading RAGAS as two retriever scores and two generator scores tells you which pipeline half to fix, such as low recall pointing at retrieval.
Read the four RAGAS metrics as two retriever scores plus two generator scores, then let whichever pair drops tell you which half of the pipeline to fix. RAGAS (Retrieval-Augmented Generation Assessment) provides: (1) Faithfulness — what fraction of claims in the generated answer are supported by the retrieved context (target: >0.85); (2) Answer Relevancy — how relevant the answer is to the question, measured by generating questions from the answer and checking similarity to the original question (target: >0.80); (3) Context Precision — are the relevant chunks ranked higher in the retrieved set (target: >0.75); (4) Context Recall — what fraction of the ground-truth answer can be attributed to the retrieved context (target: >0.80). Interpretation: low faithfulness + high context recall = the LLM is ignoring retrieved context and hallucinating; low context recall + high faithfulness = retrieval is the bottleneck (model is faithful to what it gets, but it's not getting the right information); low answer relevancy = the model is generating off-topic responses. RAGAS uses a judge LLM (use a capable frontier model) to compute these metrics, so scores are approximate. Calibrate RAGAS scores against human judgments on 50-100 examples before trusting them for automated monitoring.

**Q: How does the Chatbot Arena / ELO methodology work and why is it considered the gold standard?**
**Short:** Chatbot Arena ranks models via blind pairwise votes on real user prompts, aggregated into ELO ratings with the Bradley-Terry model.
Chatbot Arena uses blind pairwise comparisons where users submit a prompt to two anonymous models simultaneously, then vote for the better response. ELO ratings are computed from these votes using the Bradley-Terry model — each vote updates both models' ratings based on the expected vs actual outcome (upset victories cause larger rating changes). Why it's the gold standard: (1) it uses real user prompts (not synthetic benchmarks), reflecting actual use cases; (2) blind evaluation eliminates brand bias; (3) the ELO system naturally handles the fact that different models are compared different numbers of times; (4) diverse evaluators (thousands of users) average out individual biases. Limitations: (1) English-centric — most users submit English prompts; (2) conversational bias — favors chatty, helpful responses over concise expert answers; (3) recency bias — users may favor newer models; (4) sample size — rare model pairs may have insufficient comparisons for reliable ratings. The platform (now run as LMArena) has accumulated millions of blind pairwise votes across hundreds of models, making it the largest running human evaluation of LLMs; quote the live board rather than a remembered vote count or rating.

**Q: How do you design an evaluation suite for a production LLM application?**
**Short:** A production eval suite layers deterministic unit tests, LLM-as-judge scoring on representative queries, and quarterly expert human review.
A production evaluation suite needs three tiers: deterministic unit tests, automated LLM-as-judge quality scoring, and periodic human review. Concretely: (1) unit tests — deterministic checks for format (valid JSON, required fields present), safety (no PII leakage, no harmful content), and basic accuracy (known fact lookups with exact match); (2) automated quality scoring — LLM-as-judge evaluation on 100-500 representative queries covering all use case categories, run on every model update or weekly; (3) human evaluation — expert review of 50-100 cases quarterly, focusing on edge cases and failure modes. Structure: define 5-10 evaluation categories matching your product's use cases (e.g., for a customer support bot: greeting, FAQ, troubleshooting, escalation, refund requests). For each category, maintain 20-50 test cases with expected behavior descriptions. Track metrics per category over time to detect category-specific regressions. Automation: integrate tier-1 tests into CI/CD pipeline; run tier-2 evaluations on model updates and weekly in production; schedule tier-3 reviews quarterly. Alert on: >5% regression in any category, new failure modes not seen in previous evaluations.

**Q: What is the difference between held-out evaluation and online evaluation for LLMs?**
**Short:** Held-out evaluation gates deployment on a fixed reproducible dataset, while online evaluation monitors real traffic with implicit and explicit feedback.
Held-out evaluation tests the model on a fixed dataset before deployment, while online evaluation measures quality in production with real user traffic. Held-out evaluation is controlled and reproducible but may not reflect real usage patterns — users ask questions that evaluation designers never anticipated. Online evaluation captures real-world performance but is noisier and harder to control. Online evaluation methods: (1) implicit signals — regeneration rate (user clicks "try again"), conversation abandonment, task completion rate; (2) explicit feedback — thumbs up/down buttons, star ratings; (3) A/B testing — serve different models to different users and compare metrics. Key challenge: online metrics can be misleading — users may give thumbs-up to incorrect but confident-sounding answers. Best practice: use held-out evaluation for model selection and gate-keeping (don't deploy a model that regresses on held-out tests), and use online evaluation for continuous monitoring and detecting issues that held-out tests miss. The two complement each other.

**Q: What is pass@k and why do code benchmarks use it instead of plain accuracy?**
**Short:** pass@k is the probability at least one of k sampled completions passes all tests, the right metric when generation is stochastic and machine-verifiable.
Pass@k is the probability that at least one of k sampled completions passes all unit tests — the natural metric when generation is stochastic and correctness is machine-verifiable by execution. HumanEval pass@1 was already 90.2% for GPT-4o in mid-2024 and the benchmark is saturated now, but pass@1 and pass@10 can differ by tens of points for the same model because sampling multiple candidates raises the chance that one passes. Report pass@1 for product decisions (users typically see a single completion), and use higher k only when your product actually samples and filters multiple candidates.

**Q: How do you detect silent capability drift in an API-hosted model you don't control?**
**Short:** Silent drift in a hosted API model is caught by running a fixed golden dataset on a schedule and alerting on any single category dropping over 3%.
Run a version-controlled golden dataset (200-500 curated examples) against the API on a fixed schedule — weekly, plus on any announced model change — and track per-category scores, not just the aggregate. Providers update hosted models without notice; Chen, Zaharia & Zou (arXiv 2307.09009) measured GPT-4's prime-vs-composite accuracy falling from 84% to 51% between March and June 2023 — a finding OpenAI publicly disputed, which is exactly why you need your own instrumented baseline — and a gradual 1%-per-week drift compounds to 10%+ over a quarter if only point-in-time scores are eyeballed. Alert on a >3% drop in any single category, block dependent releases at >5%, and treat three consecutive weekly ~1% declines as a slow-drift trend alert.

**Q: How many comparisons does a statistically significant LLM A/B test need?**
**Short:** A significant LLM A/B test typically needs 1,000-5,000+ pairwise comparisons per variant because open-ended text output has far more variance than clicks.
Typically 1,000-5,000+ pairwise comparisons per variant — an order of magnitude more than the 200-500 samples that suffice for a click-through test — because open-ended text output has enormous variance. Detecting small effects (2-5% improvements) can require 10,000+ comparisons; judge each pair in both orderings to cancel position bias (roughly $0.01-0.05 per comparison with a GPT-4o judge) and run at least two full weekly cycles so weekday/weekend patterns and the new-model novelty effect wash out. Do a power analysis that accounts for output variance up front — standard calculators tuned to binary conversion metrics will badly underestimate the required sample size.

**Q: Why has SWE-bench largely displaced HumanEval as the primary code-capability benchmark?**
**Short:** SWE-bench displaced HumanEval because HumanEval is saturated near 90%+ pass@1, while SWE-bench's real multi-file GitHub issues still have real headroom.
Because HumanEval is effectively saturated: frontier models score 90%+ pass@1 on its 164 self-contained docstring-to-function problems, leaving little headroom to distinguish models. SWE-bench's real GitHub issues require repo-level context, cross-file edits, and passing the project's actual test suite; almost all published figures are on SWE-bench **Verified**, the 500-issue human-validated subset rather than the 2,294-issue full set — Claude 3.5 Sonnet with two tools resolved 49.0% there in Oct 2024 and o3 with scaffolding 71.7% in Dec 2024, while in 2026 Anthropic reports 76.2% for Claude Opus 4.6 (mean of 25 trials, 81.42% after a prompt change) and the top submission on the official SWE-bench/experiments leaderboard is 396/500 = 79.2%. Verified therefore still has headroom — the mid-90s figures that circulate on aggregator sites do not trace to a submission. Always state which SWE-bench variant and which agent scaffold a number came from, keep HumanEval as a cheap smoke test, and expect Verified itself to need a harder successor.

**Q: How do you measure a judge's position bias instead of just naming it?**
**Short:** Run every pairwise item in both orders and report the flip rate together with its asymmetry — a balanced flip is noise, a skewed one is position bias.
Run every pairwise item twice with A and B swapped, then report two numbers, not one: the flip rate (the fraction of items whose verdict changes on swap) and the asymmetry of those flips (how many favour position 1 versus position 2). The asymmetry is the part people omit and the part that carries the diagnosis — a flip rate that splits evenly between the two positions is judge noise you can average away, while a skewed one is a systematic preference for a slot. Zheng et al. 2023 (arXiv 2306.05685) measured GPT-4 giving the same verdict after a swap only 65.0% of the time, and of the flips, 30.0% favoured the first position against 5.0% for the second — heavily skewed, so clearly bias. Wang et al. (arXiv 2305.17926) showed how much that is worth in practice: by reordering responses alone, they made Vicuna-13B beat ChatGPT on 66 of 80 queries with ChatGPT as evaluator. Mitigation is the measurement itself — score both orders and average, which costs 2x calls and removes the bias from the aggregate — plus generating supporting evidence before the rating, the "multiple evidence calibration" intervention from the same paper. Two operational rules: recompute the flip rate on every eval run rather than once, since it changes when the judge, rubric, or model under test changes; and treat the swap-flipped items as free escalation candidates for a stronger judge, because they are exactly the ambiguous cases.

**Q: Your judge model is being deprecated mid-programme — how do you upgrade without invalidating your score history?**
**Short:** Dual-run the old and new judge over a frozen human-labelled anchor set, then re-baseline only if their rank correlation is high; if it is low, cut the trend line instead.
Treat the judge as a pinned dependency and the upgrade as a migration. First recognise what "the judge" actually is: the model snapshot id plus the judge prompt, temperature, rubric version, output parser and tie-handling rule — change any one and the trend line breaks, so hash all of them into every run record. To migrate: (1) freeze an anchor set of 200-500 items carrying human labels that never change; (2) dual-run it through both the old and the new judge; (3) report three numbers — the per-item score delta with its spread, the Spearman rank correlation between the two judges' orderings, and each judge's agreement with the frozen human labels. The rank correlation is the decider. High correlation with a shifted mean is a units change, so re-baseline: re-score the historical comparison points you still care about under the new judge and continue the series. Low correlation means the two judges disagree about which response is better, which no rescaling can repair — tag the boundary, start a new series, and never mix judges inside a single comparison. Auditing work across four judgment datasets (Yang, Hou & Yang, arXiv 2607.08535, 2026) found judge upgrades are not interchangeable — only the Qwen3 1.7B-to-4B step gave a robust adjacent gain and successive MiniMax API releases did not — and that stronger judges reduce but do not remove position and verbosity bias, so an upgrade is never itself a bias mitigation.

**Q: Why is an LLM judge structurally the same problem as an RLHF reward model?**
**Short:** Both are learned, imperfect proxies for human preference used as a scoring function, so both get Goodharted — the judge just on a weeks-long human loop rather than an hourly gradient loop.
Because both are a learned proxy for human preference standing in as the objective of an optimization process, which is the precise setup Goodhart's law describes. In RLHF, PPO optimizes the reward model thousands of gradient steps per hour and finds its blind spots in an afternoon. With a judge, the optimizer is your team iterating prompts, retrieval settings and models until the number goes up — a loop that turns over in weeks rather than hours, which is exactly why judge overfitting goes unnoticed: nobody watches a metric over three months the way they watch a reward curve over three hours. The shared failure modes follow directly: length bias dominates both (a purely length-based reward reproduces most of RLHF's downstream gain, and length-controlled AlpacaEval raised Spearman correlation with Chatbot Arena from 0.94 to 0.98 just by regressing it out); ensembling helps both but is capped by correlated errors, so diversity must come from disjoint model families rather than extra samples or seeds; and in both cases the only non-circular validation is agreement with fresh human labels produced by people who did not generate the tuning data. The practical consequence is to run a judge programme the way you would run a reward model: pin it, probe it for bias on every run, keep a frozen human-labelled anchor set, and treat a rising score as evidence only for as long as it still tracks that gold signal.

**Q: When is an LLM judge the wrong tool, and what should you use instead?**
**Short:** Whenever the property is decidable by code — schema validity, PII patterns, SQL result equality, a test suite — a deterministic assertion is cheaper, reproducible and strictly more accurate.
Whenever the property has a decidable answer. An assertion is cheaper, deterministic, instant, and strictly more accurate than a judge on anything a program can check: JSON schema validity via a validator, required-field presence via a key check, PII leakage via a fixed detector plus a frozen leak corpus, SQL correctness by executing both queries and comparing result sets, code correctness by running the test suite, citation support by checking that the quoted span actually exists in the cited chunk, factual answers by normalised exact match against a golden answer, and latency, cost and token count by measuring them. The failure of doing it with a judge is not only cost — it is that the same input can flip between runs, so a red build cannot be reproduced, and a judge asked whether a timestamp is valid ISO-8601 will accept something like "2026-13-45T99:00:00Z" often enough to matter. Reserve the judge for axes that are irreducibly subjective: helpfulness, tone, explanation quality, whether a summary preserved the point rather than the words. There is a second-order benefit: every assertion you move out of the judge makes the remaining judge score easier to interpret, because it is no longer averaging a formatting failure and a reasoning failure into one number.

**Q: Does a panel of cheap judges beat a single expensive judge?**
**Short:** Yes when the panel spans disjoint model families — PoLL beat a single large judge at over seven times lower cost — but repeated samples of one judge add almost nothing.
Yes, provided the diversity is across model families rather than across samples. Verga et al. (arXiv 2404.18796) found that a Panel of LLM evaluators built from several smaller models drawn from disjoint families outperformed a single large judge across three judge settings and six datasets, at over seven times lower cost, with less intra-model bias precisely because the families do not share a lineage. The constraint is symmetric with the reward-model ensemble literature: repeated samples of the same judge add little once the errors are correlated, the same reason RM ensembles varying only by fine-tuning seed generalise worse than ensembles varying by pretraining seed. The cheaper variant is a cascade — a cheap judge scores everything and only ambiguous items escalate. Normalising the expensive judge to 1.00 unit per item and a cheap one to 0.05, scoring 10,000 items all-expensive costs 10,000 units, while a cascade escalating 15% costs 10,000 x 0.05 + 1,500 = 2,000 units, an 80% saving; break-even is at a 95% escalation rate, so a cascade is essentially always cheaper and the only real design question is whether the escalation trigger is accurate. Judge self-reported confidence is the worst trigger available. Use disagreement between two cheap judges from different families, a score within one point of the decision threshold, or a pairwise verdict that flips when the order is swapped — the last is free, since you are already running both orders to measure the flip rate.

---

## 13. Best Practices

1. **Evaluate on task-specific data, not just general benchmarks** — your production metric is the truth.
2. **Use multiple evaluation methods** — combine automated + human + LLM-as-judge for complete picture.
3. **Track regressions, not just absolute performance** — the important question is "is it better/worse than before?"
4. **Validate LLM-as-judge against human labels** — measure judge accuracy on your domain before trusting it.
5. **Separate retrieval from generation evaluation in RAG** — diagnose where failures occur.
6. **Build evaluation before you build the product** — define what "good" means before you start.

---


## 14. Case Study

**Scenario:** A developer tools company ships an LLM-powered code review product. The model suggests code improvements for Python, JavaScript, Go, and Rust. They need an eval pipeline that catches regressions before release, measures quality improvement over model versions, costs < $500/eval run, and produces results in < 2 hours. Initial eval: MMLU (irrelevant) gave 78.3% — looked good, but production users reported poor JavaScript suggestions. They need task-specific evaluation.

**Architecture:**

```
  Code Review Eval Pipeline
  ┌────────────────────────────────────────────────────────────────┐
  │  Golden Dataset (human-authored, never shown to model)         │
  │  - 500 code snippets per language (Python, JS, Go, Rust)       │
  │  - Each snippet has 3-5 expert-written review comments         │
  │  - Snippet categories: security bugs, style, performance,      │
  │    correctness, maintainability                                │
  │  - Adversarial set: 100 already-good snippets (expect no issues│
  │    raised, or only minor style suggestions)                    │
  └──────────────────────────────┬─────────────────────────────────┘
                                 │
                                 v
  ┌────────────────────────────────────────────────────────────────┐
  │  Eval Runner (async, 50 concurrent requests)                   │
  │  Input per example:                                            │
  │    - Code snippet + language + context (file name, git diff)   │
  │    - Reference expert reviews (gold standard)                  │
  │  Model output: list of review comments with severity           │
  └──────────────────────────────┬─────────────────────────────────┘
                                 │
                    ┌────────────┼──────────────────┐
                    │            │                  │
                    v            v                  v
  ┌──────────────┐ ┌──────────────────┐ ┌──────────────────────────┐
  │  Automated   │ │  LLM-as-Judge    │ │  Human Spot-Check         │
  │  Metrics     │ │  (Claude Opus)   │ │  (20 examples/run)        │
  │  - ExactMatch│ │  - Review quality│ │  - 2 senior engineers     │
  │  - ROUGE-L   │ │  - Severity acc  │ │  - Rate 1-5 per example   │
  │  - Issue type│ │  - False positive│ │  - Calibrates LLM judge   │
  │    F1 score  │ │    rate          │ │    bias                   │
  └──────────────┘ └──────────────────┘ └──────────────────────────┘
                                 │
                                 v
  ┌────────────────────────────────────────────────────────────────┐
  │  Regression Detection                                          │
  │  - Compare to previous model version scores                    │
  │  - Alert if any metric drops > 3% (block release)              │
  │  - Track per-language, per-issue-type breakdown                │
  │  - Trend dashboard: Grafana + PostgreSQL eval history          │
  └────────────────────────────────────────────────────────────────┘

Cost Breakdown (per eval run, 2000 examples):
  Model under test (claude-sonnet-5, $3 / MTok input):
    2000 × 1500 tokens input = 3M tokens = $9
  LLM Judge (claude-opus-5, $5 / MTok input):
    2000 × 2000 tokens = 4M tokens = $20
  Human spot-check: 20 × $15/hour × 0.25 hr = $75
  Total per run: $104 (well under $500 budget)
  Runtime: 2000 examples / 50 concurrent / 3s avg = 2 min model
           + 5 min judge + 2 min analysis = 9 min total
```

**Key implementation — 3 Python code blocks:**

Block 1 — LLM-as-judge evaluation framework:

```python
from __future__ import annotations
import asyncio
import json
from dataclasses import dataclass, field
from typing import Any
import anthropic


@dataclass
class CodeReviewExample:
    example_id: str
    language: str
    code_snippet: str
    gold_reviews: list[dict[str, str]]   # [{type, severity, description}]
    is_adversarial: bool = False          # True = no issues expected


@dataclass
class ModelOutput:
    example_id: str
    reviews: list[dict[str, str]]        # model's review comments
    raw_response: str


@dataclass
class JudgeScore:
    example_id: str
    relevance: float         # 0-1: are reviews relevant to actual code?
    accuracy: float          # 0-1: are identified issues real issues?
    completeness: float      # 0-1: did model catch all gold issues?
    false_positive_rate: float  # 0-1: how often does model raise non-issues?
    severity_accuracy: float    # 0-1: severity labels correct?
    overall: float           # weighted average
    judge_reasoning: str


async def judge_code_review(
    client: anthropic.AsyncAnthropic,
    example: CodeReviewExample,
    model_output: ModelOutput,
) -> JudgeScore:
    """
    Use Claude Opus as LLM judge to evaluate code review quality.
    Judge sees: code, gold reviews, model reviews.
    Rates: relevance, accuracy, completeness, false_positives, severity.
    """
    gold_block = "\n".join(
        f"- [{r['severity'].upper()}] {r['type']}: {r['description']}"
        for r in example.gold_reviews
    )
    model_block = "\n".join(
        f"- [{r.get('severity', 'INFO').upper()}] {r.get('type', 'general')}: {r.get('description', '')}"
        for r in model_output.reviews
    ) if model_output.reviews else "(no issues found)"

    adversarial_note = ""
    if example.is_adversarial:
        adversarial_note = "\nNOTE: This is a GOOD code snippet with no real issues. False positive rate is the primary metric."

    prompt = f"""You are evaluating an AI code reviewer. Rate its performance on this {example.language} code snippet.{adversarial_note}

Code:
```{example.language}
{example.code_snippet[:2000]}
```

Expert reviews (gold standard):
{gold_block if not example.is_adversarial else "(none — this code is already correct)"}

AI reviewer output:
{model_block}

Rate the AI reviewer on these dimensions (0.0 to 1.0):
1. relevance: Are the AI's review comments relevant to actual code issues?
2. accuracy: Are the identified issues real problems (not hallucinated)?
3. completeness: Did the AI catch all the issues in the gold standard?
4. false_positive_rate: What fraction of AI's comments are non-issues? (0.0 = no false positives, 1.0 = all false positives)
5. severity_accuracy: Are the severity labels (critical/major/minor) correct?

Return JSON: {{"relevance": 0.0, "accuracy": 0.0, "completeness": 0.0, "false_positive_rate": 0.0, "severity_accuracy": 0.0, "reasoning": "..."}}"""

    response = await client.messages.create(
        model="claude-opus-5",    # Use a strong model as judge for calibration
        max_tokens=500,
        messages=[{"role": "user", "content": prompt}],
    )
    try:
        data = json.loads(response.content[0].text)
        relevance = float(data.get("relevance", 0))
        accuracy = float(data.get("accuracy", 0))
        completeness = float(data.get("completeness", 0))
        fp_rate = float(data.get("false_positive_rate", 0))
        sev_acc = float(data.get("severity_accuracy", 0))
        overall = (
            0.25 * relevance
            + 0.30 * accuracy
            + 0.25 * completeness
            + 0.10 * (1 - fp_rate)   # lower FP rate is better
            + 0.10 * sev_acc
        )
        return JudgeScore(
            example_id=example.example_id,
            relevance=relevance,
            accuracy=accuracy,
            completeness=completeness,
            false_positive_rate=fp_rate,
            severity_accuracy=sev_acc,
            overall=overall,
            judge_reasoning=data.get("reasoning", ""),
        )
    except (json.JSONDecodeError, KeyError):
        return JudgeScore(
            example_id=example.example_id,
            relevance=0.5, accuracy=0.5, completeness=0.5,
            false_positive_rate=0.5, severity_accuracy=0.5, overall=0.5,
            judge_reasoning="parse_error",
        )
```

Block 2 — Regression detection and eval CI integration (production concern):

```python
from __future__ import annotations
import asyncio
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
import json
import statistics


@dataclass
class EvalRunResult:
    run_id: str
    model_version: str
    timestamp: str
    scores_by_language: dict[str, dict[str, float]]   # lang -> {metric: score}
    scores_by_issue_type: dict[str, dict[str, float]] # type -> {metric: score}
    aggregate: dict[str, float]    # overall metrics
    regression_detected: bool
    blocking_regressions: list[str]


@dataclass
class RegressionDetector:
    """
    Compare current eval run against baseline (previous release).
    Block release if any metric drops > 3% on overall or > 5% per language.
    """

    threshold_overall: float = 0.03     # 3% overall regression → block
    threshold_per_language: float = 0.05  # 5% per-language regression → block
    history_file: Path = Path("eval_history.jsonl")

    def load_baseline(self, model_version: str) -> dict[str, float] | None:
        """Load the most recent successful release scores for this model family."""
        if not self.history_file.exists():
            return None
        records = []
        for line in self.history_file.read_text().splitlines():
            try:
                r = json.loads(line)
                if r.get("model_version", "").startswith(model_version.split(".")[0]):
                    records.append(r)
            except json.JSONDecodeError:
                pass
        if not records:
            return None
        # Return the most recent passing run
        passing = [r for r in records if not r.get("regression_detected", True)]
        return passing[-1]["aggregate"] if passing else None

    def detect_regressions(
        self,
        current: EvalRunResult,
        baseline: dict[str, float] | None,
    ) -> list[str]:
        if baseline is None:
            return []   # no baseline = first run, no regression possible

        regressions = []
        # Check overall metrics
        for metric, current_score in current.aggregate.items():
            baseline_score = baseline.get(metric)
            if baseline_score is None:
                continue
            drop = baseline_score - current_score
            if drop > self.threshold_overall:
                regressions.append(
                    f"OVERALL {metric}: {baseline_score:.3f} → {current_score:.3f} "
                    f"(drop: {drop:.1%}, threshold: {self.threshold_overall:.1%})"
                )

        # Check per-language breakdown
        for lang, lang_scores in current.scores_by_language.items():
            for metric, current_score in lang_scores.items():
                baseline_lang = baseline.get(f"{lang}_{metric}")
                if baseline_lang is None:
                    continue
                drop = baseline_lang - current_score
                if drop > self.threshold_per_language:
                    regressions.append(
                        f"LANGUAGE {lang} {metric}: {baseline_lang:.3f} → {current_score:.3f}"
                    )

        return regressions

    def save_run(self, result: EvalRunResult) -> None:
        record = {
            "run_id": result.run_id,
            "model_version": result.model_version,
            "timestamp": result.timestamp,
            "aggregate": result.aggregate,
            "scores_by_language": result.scores_by_language,
            "regression_detected": result.regression_detected,
        }
        with self.history_file.open("a") as f:
            f.write(json.dumps(record) + "\n")
```

Block 3 — BROKEN -> FIX: benchmark contamination and judge bias:

```python
from __future__ import annotations


# BROKEN: Use MMLU as the primary quality benchmark for a code review product.
# MMLU tests general knowledge (history, science, law) — completely irrelevant
# to code review quality. Model can score 78% MMLU and generate poor JS reviews.
# "We improved MMLU from 78.3% to 79.1%" — meaningless for the product.
def broken_eval_with_mmlu() -> dict[str, float]:
    return {"mmlu_score": 0.783}   # irrelevant to product quality


# FIX: Task-specific benchmark. For code review:
# - Issue detection F1 (precision × recall on real code bugs)
# - Language-specific scores (Python/JS/Go/Rust separately)
# - Adversarial pass rate (no false positives on clean code)
# - Severity classification accuracy (critical vs minor)
def fixed_task_specific_eval() -> dict[str, float]:
    return {
        "python_issue_f1": 0.0,
        "javascript_issue_f1": 0.0,
        "go_issue_f1": 0.0,
        "rust_issue_f1": 0.0,
        "overall_false_positive_rate": 0.0,
        "severity_accuracy": 0.0,
        "adversarial_pass_rate": 0.0,  # clean code correctly identified as clean
    }


# BROKEN: LLM judge uses same model family as the model under test.
# Evaluating claude-sonnet-5 with a Claude judge → self-preference bias.
# Self-enhancement bias is documented in Zheng et al. 2023 (arXiv 2306.05685);
# the size of the inflation is model- and task-specific, so measure it on your
# own data rather than assuming a fixed percentage.
async def broken_judge_with_same_family(model_output: str, gold: str) -> float:
    import anthropic
    client = anthropic.AsyncAnthropic()
    # Judge is claude-sonnet-5, same family as the model under test
    response = await client.messages.create(
        model="claude-sonnet-5",  # SAME family as tested model — biased
        max_tokens=100,
        messages=[{"role": "user", "content": f"Rate this: {model_output}. Gold: {gold}"}],
    )
    return 0.8   # inflated due to familial bias


# FIX: Use a different model family as judge (an OpenAI model judging Claude, or vice versa).
# Alternatively: calibrate judge scores against human ratings on 500-example sample.
# If judge scores consistently diverge from human scores by > 5%, apply calibration.
async def fixed_cross_family_judge(model_output: str, gold: str) -> float:
    import openai
    client = openai.AsyncOpenAI()
    response = await client.chat.completions.create(
        model="gpt-5.6-terra",   # Different family from the Claude model under test
        max_tokens=100,
        messages=[{"role": "user", "content": f"Rate this code review: {model_output}. Gold: {gold}. Return 0.0-1.0."}],
    )
    return float(response.choices[0].message.content.strip())


# BROKEN: Golden dataset used for both development and evaluation.
# Team iterates model prompt on the "eval" set → data contamination.
# Model implicitly overfits to eval patterns. Scores inflate; production quality doesn't improve.
def broken_single_dataset() -> dict[str, list]:
    full_dataset = _load_all_examples()
    # Same dataset for dev AND eval — contamination
    return {"dev": full_dataset, "eval": full_dataset}


# FIX: Strict train/dev/eval splits. Eval set is LOCKED — never shown to developers
# during model iteration. Dev set used for iteration; eval set used for release decisions only.
def fixed_split_dataset(full_dataset: list) -> dict[str, list]:
    import random
    random.seed(42)   # reproducible split
    random.shuffle(full_dataset)
    n = len(full_dataset)
    return {
        "dev": full_dataset[:int(n * 0.7)],    # 70% for development
        "val": full_dataset[int(n * 0.7):int(n * 0.9)],   # 20% for tuning
        "eval": full_dataset[int(n * 0.9):],   # 10% LOCKED — release gate only
    }


def _load_all_examples() -> list:
    return []   # placeholder
```

**Pitfall 1 — Golden dataset drift over time:**

```python
# BROKEN: Use same 2000-example golden set for 18 months.
# Over time: (1) coding best practices evolve (ESLint rules change),
# (2) new language features added (Python 3.12 walrus operator in more patterns),
# (3) model's training distribution shifts with new data.
# Eval scores stay stable but production quality drifts — the benchmark is stale.

# FIX: Quarterly golden dataset refresh.
# Add 200 new examples per quarter covering new patterns, frameworks, language versions.
# Retire 200 oldest examples that no longer reflect current codebase patterns.
# Re-baseline all historical model scores on the new dataset before comparing.
# Never remove adversarial examples — these test for systematic failures that persist.
```

**Pitfall 2 — Not measuring false positive rate (only precision/recall on positive examples):**

```python
# BROKEN: Eval only on code snippets that DO have issues.
# Model that flags every single line of code scores 100% recall.
# False positive rate: unmeasured.
# In production: model raises 12 issues per PR → engineers disable it after 2 days.

# FIX: 20% of eval set should be adversarial — clean code with no real issues.
# False positive rate measured separately: FP rate should be < 10%.
# If model raises issues on clean code > 10% of the time → fails eval gate.
def build_eval_set(positive_examples: list, clean_examples: list) -> list:
    target_clean_fraction = 0.20
    n_clean = int(len(positive_examples) * target_clean_fraction / (1 - target_clean_fraction))
    import random
    return positive_examples + random.sample(clean_examples, min(n_clean, len(clean_examples)))
```

**Metrics:**

| Metric | Baseline (claude-sonnet-5 v1) | v2 (prompt improved) | v3 (model updated) |
|--------|-------------------------------------|---------------------|-------------------|
| Python issue F1 | 0.61 | 0.71 | 0.74 |
| JavaScript issue F1 | 0.43 | 0.58 | 0.69 |
| Go issue F1 | 0.55 | 0.62 | 0.67 |
| False positive rate | 22% | 14% | 9% |
| Severity accuracy | 0.58 | 0.67 | 0.72 |
| Adversarial pass rate | 71% | 83% | 89% |
| Judge-human agreement | 0.78 | 0.79 | 0.81 |
| Cost per eval run | $104 | $104 | $104 |
| Runtime | 9 min | 9 min | 9 min |
| Regressions caught (vs production) | — | 2 | 1 |

**Interview Q&As:**

**Q: Why is MMLU an inappropriate benchmark for most production LLM applications?**
MMLU (Massive Multitask Language Understanding) tests knowledge across 57 academic domains including history, law, medicine, and science. It measures general knowledge breadth, not task-specific capability. A code review product needs high precision in identifying security vulnerabilities and style issues in Python — MMLU scores predict this capability poorly. The fundamental issue: capability on a general benchmark does not transfer reliably to specialized tasks. Always evaluate on your task distribution: code review → code review benchmarks; SQL generation → SQL benchmarks; customer support → customer support scenarios.

**Q: What makes a good LLM judge for evaluation, and what are its failure modes?**
A good LLM judge: uses a stronger model than the one being tested (judge should not struggle with the task being evaluated), uses a different model family to avoid sycophancy bias, operates on structured rubrics not vague "rate this" prompts, and is calibrated against human ratings on a representative sample. Failure modes: (1) Sycophancy — judge gives higher scores to the same text when told it's from a prestigious source; (2) Length bias — longer responses rated higher regardless of quality; (3) Position bias — first option in a comparison rated higher; (4) Familial bias — Claude judging Claude gives inflated scores. Mitigate by cross-family judging, multi-judge ensembles, and periodic human calibration.

**Q: How do you design a golden evaluation dataset that remains valid over time?**
Four principles: (1) Domain coverage — examples should cover all task types (security, style, performance, correctness) with intentional distribution control, not random sampling; (2) Difficulty distribution — include easy (blatant bugs), medium (subtle issues), and hard (architectural problems) examples; (3) Adversarial inclusion — 20% clean code to measure false positive rate; (4) Temporal refresh — add new examples quarterly reflecting current language versions and frameworks, retire stale examples. The eval set must be version-controlled alongside the model, never shown to developers during prompt iteration, and re-baselined when substantially refreshed.

**Q: How do you prevent eval contamination when iterating on prompts?**
Strict data splits with access controls: the eval set (10% of data) is stored separately, accessible only to the CI system, never loaded by development scripts. Engineers iterate on the dev set (70%) and validate on the val set (20%); the eval set is queried only during a release gate run. Operationally: store dev/val in one data store, eval in a separate repository with different credentials. If an engineer accidentally sees eval examples, retire those examples and replace with new ones. Treat eval set like production credentials — locked down, audited access.

**Q: What is the right threshold for declaring an evaluation regression that should block a release?**
Threshold should be calibrated based on: (1) The metric's variance across multiple eval runs on the same model (run the same eval 10 times on an unchanged model — the standard deviation sets the noise floor); (2) The minimum regression users would notice in production (instrument user feedback signals to learn this); (3) The severity of different metrics (false positive rate regression is more user-visible than recall regression — false positives cause users to disable the tool). Typical settings: 3% overall F1 regression blocks release; 5% per-language regression blocks release; any increase in false positive rate > 5% blocks release. Never set thresholds so tight that every release is blocked — this leads to threshold inflation.

**Q: How do you evaluate LLM outputs when there is no single correct answer (open-ended generation)?**
Three complementary methods: (1) Reference-based: ROUGE-L, BLEU, BERTScore measure similarity to gold references — fast and cheap but penalizes valid paraphrases. (2) LLM-as-judge: strong model rates outputs on a rubric — captures semantic quality beyond surface similarity but costs roughly a cent per example at 2026 mid-tier judge prices, and needs calibration. (3) Human evaluation: highest signal but most expensive; use for calibrating LLM judges and for high-stakes decisions. For production eval pipelines, combine reference-based metrics (for regression detection speed) with LLM judge (for quality measurement) and human spot-check (for judge calibration). Weight the three methods based on cost-quality trade-off for your specific task.

---

## See Also
- [Model Evaluation & Selection (ML)](../../ml/model_evaluation_and_selection/README.md) — cross-validation, AUC-ROC/PR, calibration, bias-variance — classical evaluation theory
- [LLM Testing Strategies](../llm_testing_strategies/README.md) — the engineering layer on top of this module: golden datasets, regression suites, flakiness detection, eval-gated CI/CD
- [Data Flywheels & Continuous Learning](../data_flywheels_and_continuous_learning/README.md) — production A/B testing and drift detection feeding evaluation signals back into training
