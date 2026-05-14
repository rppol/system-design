# Evaluation & Benchmarks

## 1. Concept Overview

Evaluating LLMs is one of the hardest problems in AI. Unlike classification models with clear accuracy metrics, LLMs generate open-ended text that may be helpful, harmful, correct, incorrect, or something in between. The evaluation challenge has three dimensions: (1) what to evaluate (capabilities, safety, alignment, cost); (2) how to evaluate (automated vs. human, reference-based vs. reference-free); (3) evaluation contamination (test sets leak into training data, inflating scores).

Understanding evaluation is critical for both building systems (how do you know your RAG pipeline improved?) and system design interviews (how do you measure production quality?).

---

## Intuition

> **One-line analogy**: Evaluating LLMs is like grading essays — unlike math tests with clear right answers, quality is multidimensional, subjective, and context-dependent.

**Mental model**: Traditional ML has clear metrics (accuracy, F1, AUROC). LLMs generate open-ended text, so evaluation is hard: "Is this response helpful?" requires human judgment. Benchmark suites (MMLU, HumanEval) automate evaluation on specific tasks, but they get "contaminated" — if test questions appear in training data, scores inflate. LLM-as-judge (using a stronger model to score responses) scales evaluation but introduces bias. Chatbot Arena (human preferences via ELO) is the gold standard but slow and expensive.

**Why it matters**: You can't improve what you can't measure. Without rigorous evaluation, you don't know if your prompt change, fine-tuning, or RAG improvement actually helped — or just changed outputs. Production LLM systems need evaluation pipelines that run continuously to detect regressions.

**Key insight**: No single benchmark captures "intelligence" — MMLU tests knowledge, HumanEval tests code, MT-Bench tests instruction following. A model that tops one may underperform on others. Always evaluate on domain-specific tasks that match your actual use case.

---

## 2. Core Principles

- **No single benchmark captures everything**: MMLU measures knowledge; HumanEval measures coding; TruthfulQA measures honesty. No benchmark measures all.
- **Benchmark contamination is pervasive**: If a model trains on data containing benchmark answers, scores are inflated. New benchmarks become contaminated within months.
- **Human evaluation is gold but expensive**: Human judgments are the ground truth but don't scale.
- **LLM-as-judge is useful but biased**: GPT-4 can judge responses but has systematic biases (prefers longer, more confident responses; prefers its own style).
- **Task-specific evaluation beats generic**: Your production metric (SQL execution accuracy, code pass rate, customer satisfaction) matters more than MMLU.

---

## 3. Evaluation Frameworks

### 3.1 Standard Benchmarks

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

### 3.2 Code Evaluation

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

### 3.3 Human Preference Evaluation

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

### 3.4 RAG Evaluation (RAGAS)

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

### 3.5 LLM-as-Judge

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

## 4. Architecture Diagrams

### Evaluation Pipeline
```
New Model / Prompt Change
         |
         v
[Automated Benchmark Suite]
  ├── MMLU / domain benchmarks (knowledge)
  ├── HumanEval / SWE-bench (code)
  ├── RAGAS (if RAG system)
  └── Safety benchmarks (AdvBench, WildGuard)
         |
         v
[Automated Regression Check]
  Compare vs. current production model
  Gate: must not regress >2% on any benchmark
         |
         v
[Human Evaluation Sample]
  1000 production-representative queries
  LLM-as-judge scoring (gpt-4o)
  10% human review of edge cases
         |
         v
[A/B Test in Production]
  5% traffic to new model
  Real user feedback (thumbs, ratings, edits)
  Run for minimum 48 hours
         |
         v
[Rollout Decision]
  Pass all gates → gradual rollout
  Fail any gate → investigate and fix
```

---

## 5. How It Works — Detailed Mechanics

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

---

## 6. Real-World Examples

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

## 7. Tradeoffs

| Evaluation Method | Accuracy | Scalability | Cost | Bias |
|------------------|---------|-------------|------|------|
| Human evaluation | Highest | Low | High | Human rater bias |
| LLM-as-judge | Good | High | Medium | Self-preference, verbosity |
| Automated benchmarks | Limited | Very High | Very Low | Contamination risk |
| A/B user testing | Real-world | Medium | Infra | Selection bias |
| Task-specific metrics | Domain-specific | High | Low | Narrow scope |

---

## 8. When to Use / When NOT to Use

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

## 9. Common Pitfalls

1. **Benchmark shopping**: Reporting only the benchmarks where your model looks good. Best practice: report a standardized suite and disclose any that are unfavorable.
2. **Ignoring benchmark contamination**: Not checking if test set examples are in training data.
3. **Using the same judge model as the model being evaluated**: GPT-4 judging GPT-4 responses is biased.
4. **Single-metric optimization**: Optimizing MMLU causes capability regression on other tasks (Goodhart's Law).
5. **Not testing on your domain**: A model scoring 86% on MMLU might score 60% on your medical QA domain.
6. **Ignoring latency in evaluation**: A model that scores 5% better but runs 3× slower may be worse for production.

---

## 10. Technologies & Tools

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

## 11. Interview Questions with Answers

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

## 12. Best Practices

1. **Evaluate on task-specific data, not just general benchmarks** — your production metric is the truth.
2. **Use multiple evaluation methods** — combine automated + human + LLM-as-judge for complete picture.
3. **Track regressions, not just absolute performance** — the important question is "is it better/worse than before?"
4. **Validate LLM-as-judge against human labels** — measure judge accuracy on your domain before trusting it.
5. **Separate retrieval from generation evaluation in RAG** — diagnose where failures occur.
6. **Build evaluation before you build the product** — define what "good" means before you start.

---

## 13. Case Study: Evaluating a Code Review LLM

**Product:** LLM that reviews code PRs and identifies bugs, security issues, and style violations.

**Evaluation Design:**

```
Task-specific metrics:
  1. Bug detection recall: % of real bugs found (ground truth from bug database)
  2. False positive rate: % of flagged non-issues (human review sample)
  3. Security issue recall: % of security vulnerabilities caught
  4. Actionability: are suggestions specific enough to act on? (human rated)

Test set construction:
  - 500 PRs with known bugs (from bug tracker — bug introduced before fix commit)
  - 500 PRs with no bugs (high-quality reviewed PRs)
  - 100 PRs with security vulnerabilities (CVE database)
  - Human annotation: 2 engineers rate each suggestion (1-5 actionability)

Automated evaluation:
  Bug recall: model reviews buggy PRs → did it catch the known bug?
    Evaluated by: string matching against bug location + LLM judge
  False positive: model reviews clean PRs → count flags / total PRs
  Security: model reviews vulnerable PRs → CVE recall

LLM-as-judge for actionability:
  Validated first: judge vs. human label agreement = 0.81 (good)
  Then: judge 10× more reviews per week than human-only

A/B test in production:
  Model A (gpt-4o): bug recall 78%, FP rate 12%, actionability 3.9/5
  Model B (fine-tuned gpt-4o on 5K code reviews): recall 86%, FP rate 8%, actionability 4.3/5
  → Ship Model B
```
