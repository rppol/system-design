# Evaluation & Benchmarks

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
- **LLM-as-judge is useful but biased**: GPT-4 can judge responses but has systematic biases (prefers longer, more confident responses; prefers its own style).
- **Task-specific evaluation beats generic**: Your production metric (SQL execution accuracy, code pass rate, customer satisfaction) matters more than MMLU.

---

## 4. Evaluation Frameworks

### 4.1 Standard Benchmarks

**MMLU (Massive Multitask Language Understanding)**:
```
57 tasks × 4 multiple choice options per question
Domains: STEM, humanities, social science, professional (law, medicine, finance)
Metric: accuracy (0-100%)
Questions: graduate-level knowledge

GPT-4:       86.4%
Claude 3.5:  88.7%
LLaMA 3 70B: 82.0%
Humans:      ~89%

Limitations: multiple choice; doesn't test reasoning or generation;
  heavily contaminated by 2024 (LLMs trained on MMLU-like data)
```

**HellaSwag (commonsense reasoning)**:
```
Pick the most likely continuation of a situation description
Tests: commonsense reasoning, everyday knowledge
Score: 95%+ for frontier models (essentially "solved")
```

**GPQA Diamond (Graduate-level Questions)**:
```
448 expert-level multiple choice questions in biology, chemistry, physics
Written by domain experts (PhDs, researchers)
Human (non-expert) accuracy: ~34%
Human (expert) accuracy: ~65%

GPT-4o: 53%
o3:     87%  (superhuman)

Designed to resist saturation: very hard even for LLMs
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
pass@1 scores (single attempt):
  GPT-4o: 90%
  o1:     95%+
  LLaMA 3 70B: 80%

Limitation: mostly "solved" for frontier models; needs harder successor
```

**SWE-bench (Real GitHub Issues)**:
```
2294 real GitHub issues from Python repos
Evaluation: automated test suite pass rate
% resolved:
  Claude 3.5 Sonnet (tools): 49%
  o3 + scaffolding: 71.7%
  Human programmers: ~100% (over unlimited time)

Gold standard for "can the model write code that actually works?"
```

**MBPP (Mostly Basic Python Programming)**:
```
500 crowd-sourced Python problems
Simpler than HumanEval; good for smaller models
pass@1: most 7B+ models score 60-80%
```

### 4.3 Human Preference Evaluation

**LMSYS Chatbot Arena**:
```
Methodology:
  Real users submit prompts
  Two anonymous model responses displayed side by side
  User votes: A is better / B is better / Tie
  Elo rating system (like chess) ranks models

Why it's valuable:
  Real user prompts (not curated benchmarks)
  Real user preferences (not researcher's judgment)
  Difficult to contaminate (novel prompts constantly)
  Includes >100 models

2024 Elo rankings (approximate):
  o3: ~1370
  GPT-4o: ~1290
  Claude 3.5 Sonnet: ~1310
  LLaMA 3.1 405B: ~1270

Limitations: user base is self-selected (technical users); biases toward
  verbose, confident responses; not task-specific
```

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
from ragas.metrics import faithfulness, answer_relevancy, context_recall, context_precision

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
    metrics=[faithfulness, answer_relevancy, context_recall, context_precision],
    llm=gpt4_judge  # LLM used as judge
)
# Returns: {"faithfulness": 0.87, "answer_relevancy": 0.93, ...}
```

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

    result = gpt4.complete(prompt)
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
Self-preference: GPT-4 prefers GPT-4 style responses
Instruction-following bias: prefers well-formatted responses regardless of accuracy
```

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
    BENCH["Automated Benchmark Suite\nMMCL / domain (knowledge)\nHumanEval / SWE-bench (code)\nRAGAs (RAG) · safety (AdvBench, WildGuard)"]
    REG["Automated Regression Check\nvs. current production model\ngate: no regression > 2%"]
    HUM["Human Evaluation Sample\n1 000 production-representative queries\nLLM-as-judge (gpt-4o) + 10% human review"]
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
  Use new benchmarks regularly (GPQA, FrontierMath rotate questions)
  Open vs. closed benchmarks: closed (held-out) more trustworthy

2024 example: LLaMA 3 MMLU scores higher than expected
  Investigation: several MMLU subsets found in CommonCrawl training data
```

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
  Cost: ~$0.01-0.05 per comparison with GPT-4o judge

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

### Model Drift Detection

Model drift is a silent production killer — API providers update models, weights shift during continued training, and capabilities quietly regress on specific tasks without any alert.

```
Capability regression:
  Model updates or API changes silently degrade specific capabilities
  Example: OpenAI GPT-4 performance on coding and math tasks reportedly
    degraded between March-June 2023 — users noticed before OpenAI acknowledged
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
  │ Model v2.1   │    │ Candidate v2.2     │
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
              │ v2.1 vs   │
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

---

## 7. Real-World Examples

### OpenAI Evals
- Open-source evaluation framework for GPT models
- Community-contributed evals: 1000+ task-specific evaluations
- Used internally at OpenAI to track regressions
- Structured format: jsonl files with input/ideal output

### Scale AI HELM (Holistic Evaluation of Language Models)
- Stanford CRFM initiative
- 42 scenarios × multiple models
- Standardized evaluation across models
- Public leaderboard

### LiveBench (2024)
- New benchmark questions added monthly (from recent news, competition problems)
- Minimizes contamination by design (questions are too new to be in training data)
- Covers: reasoning, math, coding, language comprehension

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
3. **Using the same judge model as the model being evaluated**: GPT-4 judging GPT-4 responses is biased.
4. **Single-metric optimization**: Optimizing MMLU causes capability regression on other tasks (Goodhart's Law).
5. **Not testing on your domain**: A model scoring 86% on MMLU might score 60% on your medical QA domain.
6. **Ignoring latency in evaluation**: A model that scores 5% better but runs 3× slower may be worse for production.
7. **Treating LLM evaluation as deterministic**: Same prompt with temperature=0 can still vary across runs due to floating-point non-determinism, GPU batching differences, and provider-side model updates. A team at a fintech company saw their "deterministic" evaluation suite produce scores ranging from 82% to 87% on the same model across consecutive runs — they were making launch decisions on noise. Mitigation: run each evaluation 3-5 times, report mean and 95% confidence intervals, and only flag changes that exceed the confidence interval.
8. **Trusting single-run LLM-as-judge scores**: Judge models disagree with themselves 10-20% of the time on borderline cases. One production team discovered their "improved" prompt was indistinguishable from the baseline when they ran the judge evaluation three times — the initial "improvement" was within judge variance. Mitigation: use majority voting with 3+ independent judge evaluations per sample, and report the agreement rate alongside quality scores. If inter-judge agreement drops below 70%, the evaluation rubric needs refinement, not more samples.
9. **Ignoring evaluation prompt sensitivity**: Changing the wording of an LLM-as-judge evaluation prompt can shift aggregate scores by 5-15%. A team changed "Rate the helpfulness of this response" to "How helpful is this response?" and saw average scores jump from 3.8/5 to 4.2/5 — same model, same test set, same judge. Best practice: version-lock evaluation prompts, judge models, and all parameters (temperature, max_tokens, system prompt). Treat evaluation infrastructure as production code with the same rigor around versioning, testing, and change management.

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **RAGAS** | RAG evaluation | Faithfulness, relevance, recall |
| **LangSmith** | Evaluation + tracing | Annotation workflow, online eval |
| **TruLens** | LLM evaluation | RAG triad: context relevance, groundedness |
| **DeepEval** | LLM test framework | pytest-like; many metrics |
| **OpenAI Evals** | Open-source eval framework | 1000+ community evals |
| **Eleuther Harness** | Language model eval | Standard open-source benchmarks |
| **HELM** | Holistic evaluation | Stanford; multi-scenario |
| **Chatbot Arena** | Human preference | Real users, Elo ratings |
| **LiteLLM** | Multi-provider eval | Run same eval across multiple models |
| **Weights & Biases** | Experiment tracking | Track eval metrics over time |

---

## 12. Interview Questions with Answers

**Q: What is LLM-as-judge and what are its limitations?**
A: LLM-as-judge uses a capable model (usually GPT-4) to evaluate another model's responses — rating quality, comparing two responses, or checking correctness. Limitations: (1) self-preference bias — GPT-4 rates GPT-4 style responses higher; (2) verbosity bias — longer responses rated higher regardless of quality; (3) position bias — first response shown often preferred; (4) instruction-following bias — well-formatted responses preferred; (5) can't catch factual errors the judge model also makes. Mitigations: use diverse judges, randomize position, include explicit rubrics, validate against human judgments.

**Q: Why is benchmark contamination a problem and how do you detect it?**
A: Contamination occurs when benchmark test examples appear in training data, so the model "memorizes" answers rather than demonstrating the underlying capability. It inflates scores and makes models look better than they are. Detection: (1) n-gram overlap analysis between training data and benchmarks; (2) membership inference — does the model reproduce benchmark examples verbatim?; (3) performance anomalies — unusually high scores on specific subsets. Solution: use held-out benchmarks released after the model's training cutoff, or continuously refreshed benchmarks (LiveBench, competitive math).

**Q: What is RAGAS and what does it measure?**
A: RAGAS is an evaluation framework for RAG systems. It measures four dimensions: (1) Faithfulness — is the generated answer supported by the retrieved context (no hallucination)?; (2) Answer Relevancy — does the answer address the question asked?; (3) Context Recall — did the retrieval system find all relevant documents?; (4) Context Precision — what fraction of retrieved documents are actually relevant? Together, these diagnose whether failures come from retrieval (bad recall/precision) or generation (low faithfulness/relevancy).

**Q: How would you build a custom evaluation system for a production LLM application?**
A: (1) Define task-specific metrics aligned with business goals (e.g., SQL execution accuracy, customer resolution rate, factual accuracy on domain Q&A); (2) Build a golden test set: 200-500 examples with human-verified correct answers; (3) Automated evaluation: run on every model/prompt change; fail if regression > threshold; (4) LLM-as-judge for open-ended aspects: helpfulness, clarity; validate judge against human labels; (5) Online evaluation: sample 1-5% of production traffic, use user feedback (implicit: session continuation, explicit: ratings); (6) Weekly human review sample: manually inspect 50-100 cases for systematic issues automated metrics miss.

**Q: How reliable is LLM-as-judge evaluation and what are its biases?**
LLM-as-judge achieves 80-85% agreement with human evaluators on pairwise preference tasks, comparable to inter-human agreement. Known biases: (1) position bias — GPT-4 prefers the first response in a comparison 60% of the time (mitigate by evaluating both orders and averaging); (2) verbosity bias — judges prefer longer, more detailed responses even when shorter ones are more accurate; (3) self-preference — models rate their own outputs higher than competitors' outputs (don't use GPT-4 to judge GPT-4 vs Claude); (4) sycophancy — judges agree with confident-sounding responses regardless of accuracy. Mitigation: (1) use reference-based judging (provide the correct answer for comparison); (2) use structured rubrics with explicit criteria and scoring scales; (3) average across multiple judge models; (4) calibrate with a human-evaluated validation set. For production: LLM-as-judge is practical for automated quality monitoring at scale, but high-stakes evaluations (model selection, launch decisions) should include human evaluation on a representative sample.

**Q: How does benchmark contamination occur and how do you detect it?**
Benchmark contamination happens when test set data appears in the model's training corpus, inflating benchmark scores beyond true capability. Sources: (1) web crawl — popular benchmarks (MMLU, HumanEval) appear on blogs, forums, and GitHub discussions; (2) synthetic data — models trained on GPT-4 outputs may inherit GPT-4's memorized benchmark answers; (3) data pipeline leaks — evaluation datasets accidentally included in training splits. Detection: (1) n-gram overlap analysis — check for exact or near-exact matches between training data and benchmark questions; (2) canary strings — embed unique identifiers in evaluation data and check if models reproduce them; (3) performance gap analysis — if a model scores 90% on public benchmarks but only 70% on held-out private tests of similar difficulty, suspect contamination; (4) memorization probing — test if the model can complete benchmark questions from partial prompts. Frontier labs (OpenAI, Anthropic, Google) now maintain private evaluation suites specifically to avoid contamination. For your own evaluations: always create domain-specific test sets from data generated after your model's training cutoff.

**Q: How do you interpret RAGAS metrics for RAG evaluation?**
RAGAS (Retrieval-Augmented Generation Assessment) provides four automated metrics: (1) Faithfulness — what fraction of claims in the generated answer are supported by the retrieved context (target: >0.85); (2) Answer Relevancy — how relevant the answer is to the question, measured by generating questions from the answer and checking similarity to the original question (target: >0.80); (3) Context Precision — are the relevant chunks ranked higher in the retrieved set (target: >0.75); (4) Context Recall — what fraction of the ground-truth answer can be attributed to the retrieved context (target: >0.80). Interpretation: low faithfulness + high context recall = the LLM is ignoring retrieved context and hallucinating; low context recall + high faithfulness = retrieval is the bottleneck (model is faithful to what it gets, but it's not getting the right information); low answer relevancy = the model is generating off-topic responses. RAGAS uses an LLM (GPT-4 recommended) to compute these metrics, so scores are approximate. Calibrate RAGAS scores against human judgments on 50-100 examples before trusting them for automated monitoring.

**Q: How does the Chatbot Arena / ELO methodology work and why is it considered the gold standard?**
Chatbot Arena uses blind pairwise comparisons where users submit a prompt to two anonymous models simultaneously, then vote for the better response. ELO ratings are computed from these votes using the Bradley-Terry model — each vote updates both models' ratings based on the expected vs actual outcome (upset victories cause larger rating changes). Why it's the gold standard: (1) it uses real user prompts (not synthetic benchmarks), reflecting actual use cases; (2) blind evaluation eliminates brand bias; (3) the ELO system naturally handles the fact that different models are compared different numbers of times; (4) diverse evaluators (thousands of users) average out individual biases. Limitations: (1) English-centric — most users submit English prompts; (2) conversational bias — favors chatty, helpful responses over concise expert answers; (3) recency bias — users may favor newer models; (4) sample size — rare model pairs may have insufficient comparisons for reliable ratings. As of 2025, Chatbot Arena has collected 1M+ votes, making it the largest human evaluation of LLMs.

**Q: How do you design an evaluation suite for a production LLM application?**
A production evaluation suite needs three tiers: (1) unit tests — deterministic checks for format (valid JSON, required fields present), safety (no PII leakage, no harmful content), and basic accuracy (known fact lookups with exact match); (2) automated quality scoring — LLM-as-judge evaluation on 100-500 representative queries covering all use case categories, run on every model update or weekly; (3) human evaluation — expert review of 50-100 cases quarterly, focusing on edge cases and failure modes. Structure: define 5-10 evaluation categories matching your product's use cases (e.g., for a customer support bot: greeting, FAQ, troubleshooting, escalation, refund requests). For each category, maintain 20-50 test cases with expected behavior descriptions. Track metrics per category over time to detect category-specific regressions. Automation: integrate tier-1 tests into CI/CD pipeline; run tier-2 evaluations on model updates and weekly in production; schedule tier-3 reviews quarterly. Alert on: >5% regression in any category, new failure modes not seen in previous evaluations.

**Q: What is the difference between held-out evaluation and online evaluation for LLMs?**
Held-out evaluation tests the model on a fixed dataset before deployment, while online evaluation measures quality in production with real user traffic. Held-out evaluation is controlled and reproducible but may not reflect real usage patterns — users ask questions that evaluation designers never anticipated. Online evaluation captures real-world performance but is noisier and harder to control. Online evaluation methods: (1) implicit signals — regeneration rate (user clicks "try again"), conversation abandonment, task completion rate; (2) explicit feedback — thumbs up/down buttons, star ratings; (3) A/B testing — serve different models to different users and compare metrics. Key challenge: online metrics can be misleading — users may give thumbs-up to incorrect but confident-sounding answers. Best practice: use held-out evaluation for model selection and gate-keeping (don't deploy a model that regresses on held-out tests), and use online evaluation for continuous monitoring and detecting issues that held-out tests miss. The two complement each other.

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
  │  - 500 code snippets per language (Python, JS, Go, Rust)      │
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
  │    - Code snippet + language + context (file name, git diff)  │
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
  │  - ROUGE-L   │ │  - Severity acc  │ │  - Rate 1-5 per example  │
  │  - Issue type│ │  - False positive│ │  - Calibrates LLM judge  │
  │    F1 score  │ │    rate          │ │    bias                   │
  └──────────────┘ └──────────────────┘ └──────────────────────────┘
                                 │
                                 v
  ┌────────────────────────────────────────────────────────────────┐
  │  Regression Detection                                          │
  │  - Compare to previous model version scores                    │
  │  - Alert if any metric drops > 3% (block release)             │
  │  - Track per-language, per-issue-type breakdown               │
  │  - Trend dashboard: Grafana + PostgreSQL eval history          │
  └────────────────────────────────────────────────────────────────┘

Cost Breakdown (per eval run, 2000 examples):
  Model under test (claude-sonnet-4-6):
    2000 × 1500 tokens input = 3M tokens = $9
  LLM Judge (claude-opus-4):
    2000 × 2000 tokens = 4M tokens = $60
  Human spot-check: 20 × $15/hour × 0.25 hr = $75
  Total per run: $144 (well under $500 budget)
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
        model="claude-opus-4-5",    # Use strongest model as judge for calibration
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
# Evaluating Claude claude-sonnet-4-6 with a Claude judge → sycophancy bias.
# Claude judge rates Claude claude-sonnet-4-6 outputs 8% higher than GPT-4 judges
# on identical outputs — familial bias inflates scores.
async def broken_judge_with_same_family(model_output: str, gold: str) -> float:
    import anthropic
    client = anthropic.AsyncAnthropic()
    # Judge is claude-sonnet-4-6, same family as the model under test
    response = await client.messages.create(
        model="claude-sonnet-4-6",  # SAME family as tested model — biased
        max_tokens=100,
        messages=[{"role": "user", "content": f"Rate this: {model_output}. Gold: {gold}"}],
    )
    return 0.8   # inflated due to familial bias


# FIX: Use a different model family as judge (GPT-4 judging Claude, or vice versa).
# Alternatively: calibrate judge scores against human ratings on 500-example sample.
# If judge scores consistently diverge from human scores by > 5%, apply calibration.
async def fixed_cross_family_judge(model_output: str, gold: str) -> float:
    import openai
    client = openai.AsyncOpenAI()
    response = await client.chat.completions.create(
        model="gpt-4o",   # Different family from the Claude model under test
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

| Metric | Baseline (Claude claude-sonnet-4-6 v1) | v2 (prompt improved) | v3 (model updated) |
|--------|-------------------------------------|---------------------|-------------------|
| Python issue F1 | 0.61 | 0.71 | 0.74 |
| JavaScript issue F1 | 0.43 | 0.58 | 0.69 |
| Go issue F1 | 0.55 | 0.62 | 0.67 |
| False positive rate | 22% | 14% | 9% |
| Severity accuracy | 0.58 | 0.67 | 0.72 |
| Adversarial pass rate | 71% | 83% | 89% |
| Judge-human agreement | 0.78 | 0.79 | 0.81 |
| Cost per eval run | $144 | $144 | $144 |
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
Three complementary methods: (1) Reference-based: ROUGE-L, BLEU, BERTScore measure similarity to gold references — fast and cheap but penalizes valid paraphrases. (2) LLM-as-judge: strong model rates outputs on a rubric — captures semantic quality beyond surface similarity but expensive (~$0.03/example) and needs calibration. (3) Human evaluation: highest signal but most expensive; use for calibrating LLM judges and for high-stakes decisions. For production eval pipelines, combine reference-based metrics (for regression detection speed) with LLM judge (for quality measurement) and human spot-check (for judge calibration). Weight the three methods based on cost-quality trade-off for your specific task.

---

## See Also
- [Model Evaluation & Selection (ML)](../../ml/model_evaluation_and_selection/README.md) — cross-validation, AUC-ROC/PR, calibration, bias-variance — classical evaluation theory
