# LLM Evaluation Harness in Production

---

## 1. Concept Overview

A production LLM evaluation harness is a systematic infrastructure layer that continuously measures whether an LLM-powered application is producing outputs of acceptable quality. It spans three environments: offline (pre-deploy), CI/CD (gate before merge), and online (sample from live traffic). The harness is the quality control layer that makes model upgrades, prompt changes, and RAG tuning safe to ship.

Offline unit-test-style evaluation breaks for LLMs for three structural reasons. First, non-determinism: two identical API calls to GPT-4o may return outputs that differ in phrasing, ordering, and length while being equally correct — simple string equality fails. Second, subjective quality: "good" often means "helpful, accurate, and appropriately concise" — none of these are computable from ground truth strings. Third, emergent capabilities and regressions: a model update that improves factual accuracy may silently degrade instruction-following on long prompts — only a layered metric suite catches both.

The eval harness solves these problems by composing multiple measurement layers: lexical metrics (ROUGE, BLEU, exact match) for cases where ground truth is deterministic; semantic metrics (embedding cosine similarity, BERTScore) for paraphrase-tolerant correctness; LLM-as-judge for subjective quality and reasoning; and human raters as the calibration source for the LLM judge. Each layer has different cost, latency, and reliability characteristics. A production harness runs all layers in the right contexts — not blindly applying the most expensive to every trace.

Related modules: [`../../evaluation_and_benchmarks/README.md`](../../evaluation_and_benchmarks/README.md), [`../../llm_testing_strategies/README.md`](../../llm_testing_strategies/README.md), [`../../llm_observability_and_monitoring/README.md`](../../llm_observability_and_monitoring/README.md), [`../../prompt_management_and_promptops/README.md`](../../prompt_management_and_promptops/README.md).

---

## 2. Intuition

**One-line analogy**: An LLM eval harness is the statistical quality-control line in a semiconductor fab — you cannot inspect every chip (token), but systematic sampling at each stage of manufacturing (development, CI, production) catches defects before customers see them.

**Mental model**: Think of eval as a three-layer defense. The golden dataset is the regression test suite. The LLM judge is the senior code reviewer who reads outputs for subtlety. The online sampler is the production monitoring that catches drift in the wild. All three must agree before you declare a change safe.

**Why it matters**: Teams that skip formal evals ship regressions constantly. A prompt that improves ROUGE score by 4 points may simultaneously increase hallucination rate from 3% to 11% on long-tail queries — no unit test catches this. Teams that build the harness once can ship model upgrades in 2 hours instead of 2 weeks.

**Key insight**: The eval harness is not a test suite you run before launch. It is a continuous measurement system that runs in parallel with your application in production. The golden dataset is never frozen — it grows with every production failure you investigate.

---

## 3. Core Principles

**Separation of eval from inference**: The eval pipeline runs on separate infrastructure with separate API quotas. Never share rate-limit budget between your application serving path and your eval runs. A bad eval run should not degrade user-facing latency.

**Eval-as-code**: All eval configuration — dataset pointers, judge prompts, metric thresholds, significance levels — lives in version control alongside application code. The eval definition is reviewed in the same PR as the application change it gates.

**Dataset versioning**: Every golden dataset has a semantic version (v1.2.3) and a content hash. The eval runner logs both at run time. A regression alarm is only meaningful if you know which dataset version was used. Dataset v1 and dataset v2 are not directly comparable.

**Metric layering**: Apply metrics in ascending cost order. Run lexical metrics ($0.00) on every example. Run semantic similarity ($0.001/call for embedding) on examples that fail lexical. Run LLM-as-judge ($0.05–0.30/call) on a stratified sample. Reserve human review for model releases, safety incidents, and judge calibration.

**Statistical validity over heuristic thresholds**: A score of 0.81 vs 0.79 is noise. A regression is only real when a statistical test (Wilson score confidence interval for proportions, Welch's t-test for continuous scores) rules out sampling variability at p < 0.05.

---

## 4. Types / Architectures / Strategies

### Four Eval Modes

| Mode | When | Coverage | Latency | Cost |
|------|------|----------|---------|------|
| Offline batch | Pre-deploy, CI | Full golden set (200–500 examples) | Minutes | High (all examples judged) |
| Online shadow | Continuous production | 2–5% of live traffic | Async, minutes lag | Medium (sampled) |
| A/B-gated eval | Model or prompt variant comparison | Both variants on same golden set | Minutes | Double (two runs) |
| Human-in-the-loop | Model release, safety incident, judge calibration | Curated subset (50–100 examples) | Hours to days | Very high |

### Deterministic vs Probabilistic Metrics

| Category | Metric | When to Use | Limitations |
|----------|--------|------------|-------------|
| Lexical | Exact match, F1, ROUGE-L | Extractive QA, structured output fields | Fails on paraphrase |
| Lexical | BLEU-4 | Machine translation | Sentence-level, not semantic |
| Semantic | Cosine similarity (embedding) | Open-ended QA, summarization | Embedding model bias |
| Semantic | BERTScore | NLG tasks where order matters | Slower, requires GPU for speed |
| LLM judge | Correctness, helpfulness, faithfulness, safety | Subjective quality, reasoning | Positional/verbosity bias, cost |
| Human | All dimensions | Ground truth for judge calibration | Slow, expensive, not scalable |

### Judge Prompt Strategies

- **Reference-based scoring**: Judge sees (question, reference answer, model output) and rates 1–5. Lower variance than reference-free but requires curated references.
- **Reference-free scoring**: Judge sees (question, model output) and rates quality. Required for creative tasks where no single reference exists.
- **Pairwise comparison**: Judge sees (question, output A, output B) and picks the better one. Higher inter-rater agreement (85–92%) than absolute scoring, but only yields rankings, not absolute scores.
- **Rubric-grounded scoring**: Judge receives a detailed rubric (e.g., "Score 5 if all facts are grounded in context; score 1 if any hallucination is present"). Reduces judge interpretation variance.

---

## 5. Architecture Diagrams

### End-to-End Eval Pipeline

```
  Dataset Registry (versioned golden sets)
          |
          | load(version="v2.1.0")
          v
  +----------------+
  |  Eval Runner   |  -- reads dataset, invokes application under test
  |  (batch mode)  |
  +----------------+
          |
          | (question, context, expected_answer, model_output)
          v
  +----------------------+
  |  Judge Orchestrator  |  -- routes each example to correct judge tier
  |  lexical / semantic  |
  |  / LLM-as-judge      |
  +----------------------+
          |
          | (scores per example per metric)
          v
  +--------------------+
  |  Metric Aggregator |  -- computes mean, P10/P50/P90, pass rate
  +--------------------+
          |
          v
  +----------------------+
  |  Regression Detector |  -- compares to baseline run; runs t-test
  |  p < 0.05 threshold  |
  +----------------------+
          |
     pass?   fail?
       |         |
       v         v
  CI Gate     Block merge + notify Slack
       |
       v
  Dashboard (Braintrust / LangSmith / Arize)
```

### Online Shadow Eval Architecture

```
  Live Traffic (100% of requests)
          |
          +---------------------------+
          |                           |
          v                           v
  Application Serving          Sampling Layer
  Path (synchronous)           (2-5% sampled)
          |                           |
          v                           v
  Response to User         Async Eval Queue
                                      |
                                      v
                           +------------------+
                           |  Shadow Eval     |
                           |  Runner          |
                           |  (async, no SLA) |
                           +------------------+
                                      |
                                      v
                           LLM Judge + Metrics
                                      |
                                      v
                           Quality Dashboard
                           + Drift Alerts
```

### Dataset Versioning Schema

```
  Dataset Registry
  ┌────────────────────────────────────────────────┐
  │  name:     "customer_support_golden_v2"        │
  │  version:  "v2.1.0"                            │
  │  sha256:   "a3f9...c81d"                       │
  │  created:  2025-03-14T10:00:00Z                │
  │  splits:                                        │
  │    train:  400 examples (80%)                  │
  │    eval:   100 examples (20%)                  │
  │  composition:                                   │
  │    routine:     60%  (120 examples)            │
  │    adversarial: 20%  ( 40 examples)            │
  │    edge_cases:  20%  ( 40 examples)            │
  └────────────────────────────────────────────────┘
```

---

## 6. How It Works — Detailed Mechanics

### Core Data Structures

```python
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class GoldenRecord:
    """A single versioned example in the golden dataset."""
    id: str
    question: str
    context: Optional[str]           # retrieval context if applicable
    reference_answer: str            # human-curated ground truth
    tags: list[str] = field(default_factory=list)  # ["adversarial", "edge_case", "routine"]
    min_acceptable_score: float = 3.0  # judge score threshold for this example (1-5 scale)


@dataclass
class EvalDataset:
    """Versioned, content-hashed golden dataset."""
    name: str
    version: str
    records: list[GoldenRecord]
    created_at: datetime = field(default_factory=datetime.utcnow)

    @property
    def content_hash(self) -> str:
        payload = json.dumps(
            [{"id": r.id, "question": r.question, "reference": r.reference_answer}
             for r in self.records],
            sort_keys=True
        )
        return hashlib.sha256(payload.encode()).hexdigest()[:16]

    def stratified_sample(self, n: int) -> list[GoldenRecord]:
        """Return n records preserving tag distribution (for online eval)."""
        import random
        tags = list({tag for r in self.records for tag in r.tags})
        sampled: list[GoldenRecord] = []
        per_tag = max(1, n // len(tags))
        for tag in tags:
            pool = [r for r in self.records if tag in r.tags]
            sampled.extend(random.sample(pool, min(per_tag, len(pool))))
        return sampled[:n]
```

### LLM Judge

```python
import re
from openai import OpenAI

JUDGE_PROMPT_TEMPLATE = """You are an expert evaluator. Score the model response on a 1-5 scale.

QUESTION: {question}
REFERENCE ANSWER: {reference_answer}
MODEL OUTPUT: {model_output}

RUBRIC:
5 - Fully correct, grounded in facts, concise, no hallucination.
4 - Mostly correct, minor omission or slight verbosity.
3 - Partially correct, at least one factual claim supported.
2 - Mostly incorrect or mostly hallucinated.
1 - Completely wrong or harmful.

Respond in this exact format:
SCORE: <integer 1-5>
REASON: <one sentence explanation>"""


class LLMJudge:
    """LLM-as-judge with agreement scoring across N samples."""

    def __init__(
        self,
        model: str = "gpt-4o",
        n_samples: int = 3,
        temperature: float = 0.2,
        agreement_threshold: float = 0.85,
    ) -> None:
        self.client = OpenAI()
        self.model = model
        self.n_samples = n_samples
        self.temperature = temperature
        self.agreement_threshold = agreement_threshold

    def _parse_score(self, response_text: str) -> Optional[int]:
        match = re.search(r"SCORE:\s*([1-5])", response_text)
        return int(match.group(1)) if match else None

    def score(
        self,
        question: str,
        reference_answer: str,
        model_output: str,
    ) -> dict[str, float | bool]:
        """
        Returns median score across n_samples, agreement flag, and raw scores.
        agreement_threshold=0.85 means >=85% of samples within 1 point of median.
        """
        prompt = JUDGE_PROMPT_TEMPLATE.format(
            question=question,
            reference_answer=reference_answer,
            model_output=model_output,
        )
        scores: list[int] = []
        for _ in range(self.n_samples):
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=self.temperature,
                max_tokens=100,
            )
            parsed = self._parse_score(resp.choices[0].message.content or "")
            if parsed is not None:
                scores.append(parsed)

        if not scores:
            return {"score": 0.0, "agreement": False, "raw_scores": []}

        median = sorted(scores)[len(scores) // 2]
        within_one = sum(1 for s in scores if abs(s - median) <= 1)
        agreement = (within_one / len(scores)) >= self.agreement_threshold

        return {
            "score": float(median),
            "agreement": agreement,
            "raw_scores": scores,
        }
```

### Regression Detector

```python
import math
from scipy import stats  # type: ignore


@dataclass
class EvalRunResult:
    run_id: str
    dataset_version: str
    dataset_hash: str
    model_tag: str              # e.g. "gpt-4o-2024-08-06" or "prompt-v3"
    scores: list[float]         # one score per example
    pass_count: int             # examples scoring >= min_acceptable_score
    total: int

    @property
    def pass_rate(self) -> float:
        return self.pass_count / self.total if self.total else 0.0

    @property
    def mean_score(self) -> float:
        return sum(self.scores) / len(self.scores) if self.scores else 0.0


class RegressionDetector:
    """
    Compares a candidate run to a baseline run.
    Uses Welch's t-test for continuous scores and Wilson score interval
    for pass/fail proportions. Regression is flagged only when p < 0.05.
    """

    def __init__(self, significance_level: float = 0.05, min_effect_size: float = 0.1) -> None:
        self.alpha = significance_level
        self.min_effect = min_effect_size  # absolute score units; ignore noise below this

    def _wilson_lower_bound(self, successes: int, total: int, confidence: float = 0.95) -> float:
        if total == 0:
            return 0.0
        z = 1.96  # 95% confidence
        p_hat = successes / total
        denominator = 1 + z**2 / total
        center = p_hat + z**2 / (2 * total)
        margin = z * math.sqrt(p_hat * (1 - p_hat) / total + z**2 / (4 * total**2))
        return (center - margin) / denominator

    def detect(
        self,
        baseline: EvalRunResult,
        candidate: EvalRunResult,
    ) -> dict[str, bool | float | str]:
        if baseline.dataset_hash != candidate.dataset_hash:
            return {
                "regression": False,
                "skipped": True,
                "reason": "Dataset hashes differ — not a valid comparison",
            }

        # Continuous score regression: Welch's t-test
        t_stat, p_value = stats.ttest_ind(
            baseline.scores, candidate.scores, equal_var=False
        )
        score_delta = candidate.mean_score - baseline.mean_score
        score_regression = (
            p_value < self.alpha
            and score_delta < -self.min_effect
        )

        # Pass-rate regression: Wilson score lower bounds
        baseline_lb = self._wilson_lower_bound(baseline.pass_count, baseline.total)
        candidate_lb = self._wilson_lower_bound(candidate.pass_count, candidate.total)
        passrate_regression = candidate_lb < baseline_lb - self.min_effect

        regression = score_regression or passrate_regression
        return {
            "regression": regression,
            "score_delta": round(score_delta, 4),
            "p_value": round(p_value, 4),
            "baseline_pass_rate": round(baseline.pass_rate, 4),
            "candidate_pass_rate": round(candidate.pass_rate, 4),
            "reason": (
                f"Score dropped {-score_delta:.2f} points (p={p_value:.3f})"
                if score_regression
                else "Pass rate regression detected"
                if passrate_regression
                else "No regression"
            ),
        }
```

### EvalPipeline Orchestrator

```python
import uuid
from typing import Callable

ApplicationFn = Callable[[str, Optional[str]], str]  # (question, context) -> answer


class EvalPipeline:
    """
    Orchestrates: dataset load -> model invoke -> judge score -> regression check.
    Minimum golden set: 200 examples for statistical validity.
    """

    def __init__(
        self,
        dataset: EvalDataset,
        application: ApplicationFn,
        judge: LLMJudge,
        detector: RegressionDetector,
        baseline: Optional[EvalRunResult] = None,
    ) -> None:
        self.dataset = dataset
        self.application = application
        self.judge = judge
        self.detector = detector
        self.baseline = baseline

    def run(self, model_tag: str, max_workers: int = 10) -> dict:
        from concurrent.futures import ThreadPoolExecutor, as_completed

        run_id = str(uuid.uuid4())[:8]
        scores: list[float] = []
        pass_count = 0
        failures: list[dict] = []

        def eval_one(record: GoldenRecord) -> dict:
            output = self.application(record.question, record.context)
            result = self.judge.score(
                question=record.question,
                reference_answer=record.reference_answer,
                model_output=output,
            )
            passed = result["score"] >= record.min_acceptable_score
            return {
                "id": record.id,
                "score": result["score"],
                "passed": passed,
                "agreement": result["agreement"],
                "output": output,
            }

        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {executor.submit(eval_one, r): r for r in self.dataset.records}
            for future in as_completed(futures):
                res = future.result()
                scores.append(res["score"])
                if res["passed"]:
                    pass_count += 1
                else:
                    failures.append(res)

        candidate = EvalRunResult(
            run_id=run_id,
            dataset_version=self.dataset.version,
            dataset_hash=self.dataset.content_hash,
            model_tag=model_tag,
            scores=scores,
            pass_count=pass_count,
            total=len(self.dataset.records),
        )

        regression_report = {}
        if self.baseline:
            regression_report = self.detector.detect(self.baseline, candidate)

        return {
            "run_id": run_id,
            "mean_score": candidate.mean_score,
            "pass_rate": candidate.pass_rate,
            "failures": failures[:10],          # top 10 worst examples
            "regression": regression_report,
            "passed": not regression_report.get("regression", False),
        }
```

### CI Integration (GitHub Actions)

```yaml
# .github/workflows/eval_gate.yml
name: LLM Eval Gate

on:
  pull_request:
    paths:
      - "src/prompts/**"
      - "src/rag/**"
      - "src/application/**"

jobs:
  eval:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Python 3.11
        uses: actions/setup-python@v5
        with:
          python-version: "3.11"

      - name: Install dependencies
        run: pip install -r requirements-eval.txt

      - name: Run eval harness
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          EVAL_DATASET_VERSION: "v2.1.0"
          BASELINE_RUN_ID: ${{ vars.BASELINE_RUN_ID }}   # set after each promoted release
        run: |
          python -m eval.run_pipeline \
            --dataset-version $EVAL_DATASET_VERSION \
            --baseline-run-id $BASELINE_RUN_ID \
            --output eval_results.json

      - name: Check regression gate
        run: |
          python -c "
          import json, sys
          results = json.load(open('eval_results.json'))
          if results['regression'].get('regression'):
              print('REGRESSION DETECTED:', results['regression']['reason'])
              sys.exit(1)
          if results['pass_rate'] < 0.85:
              print('PASS RATE TOO LOW:', results['pass_rate'])
              sys.exit(1)
          print('Eval passed. Pass rate:', results['pass_rate'])
          "

      - name: Upload eval results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: eval-results
          path: eval_results.json
```

**Key numbers**: golden set minimum 200 examples for 80% power at p=0.05; judge agreement threshold 0.85; pass rate CI gate at 85%; online sample rate 2–5%; judge runs at temperature 0.2 (not 0.0 — zero temperature makes all samples identical, defeating the agreement check); regression significance p < 0.05; minimum effect size 0.1 absolute score points to ignore noise.

---

## 7. Real-World Examples

**Anthropic — Claude model releases**: Anthropic's eval suite is the primary release gate for every Claude model revision. The suite covers thousands of task categories using both automated metrics and human raters, with LLM-as-judge used for subjective quality dimensions (helpfulness, honesty, harmlessness). A candidate model must not regress on any category by more than 2 percentage points relative to the current production model. The eval results are reviewed by a release committee before deployment.

**OpenAI Evals framework**: OpenAI open-sourced its internal eval framework (github.com/openai/evals) in 2023. The framework encodes evals as YAML specifications (model, dataset, grading function) that run against any OpenAI model. Community-contributed evals cover medical, legal, coding, and reasoning tasks. The pairwise model comparison mode is the mechanism behind the OpenAI model leaderboard on the platform — the same model that beats the baseline on the Evals suite is promoted to production.

**Braintrust**: Braintrust positions itself as the "CI/CD for LLMs." Teams define evals in code, run them against any LLM via the Braintrust SDK, and review results in a web dashboard that shows score trends over time, example-level diffs, and A/B comparisons. Braintrust stores eval runs immutably — you can compare a PR branch to any past run by ID. Companies using Braintrust (as of 2025) run 50–500 eval examples per PR merge on average.

**LangSmith**: Eval datasets in LangSmith are versioned collections. The `langsmith.evaluate()` function runs a chain against a dataset and scores with built-in evaluators (exact match, embedding distance, LLM-as-judge via the `criteria` evaluator). Results surface in the Experiments tab with per-run breakdowns and diff views. LangSmith's annotation queue routes low-scoring production traces to human reviewers, and their corrections are added to the dataset — closing the flywheel loop.

---

## 8. Tradeoffs

### Metric Tier Comparison

| Dimension | LLM-as-Judge | Human Raters | Lexical (ROUGE/F1) | Semantic (Embedding) |
|-----------|-------------|-------------|-------------------|---------------------|
| Cost per example | $0.05–0.30 | $2–15 | $0.00 | $0.001 |
| Latency | 2–5 seconds | Hours to days | <1ms | 50–200ms |
| Accuracy vs human | 75–90% agreement | Ground truth | 40–60% on open-ended | 65–80% |
| Handles paraphrase | Yes | Yes | No | Yes |
| Handles subjectivity | Partially | Yes | No | No |
| Bias risks | Verbosity, positional, self-serving | Annotator fatigue, instruction-following | None | Embedding model bias |
| Scales to CI/CD | Yes | No | Yes | Yes |

### The Metric Cost Cascade — Run Cheap First, Escalate on Uncertainty

The four tiers span five orders of magnitude in cost per example, so you never run all
of them on every example — you cascade, resolving as many as possible at the cheap top:

```
 cost/example:  $0.00        $0.001        $0.05-0.30        $2-15
                  |            |               |               |
 tier:         Lexical      Semantic       LLM-Judge        Human
 latency:      <1ms         50-200ms       2-5s             hours-days
 vs human:     40-60%       65-80%         75-90%           ground truth

   ALL examples --> [Lexical]    pass?      --> done   (free)
                       | fail / ambiguous
                       v
                    [Semantic]   pass?      --> done   ($0.001)
                       | still uncertain
                       v
                    [LLM-Judge]  confident? --> done   ($0.05-0.30)
                       | judge disagrees / high-stakes sample
                       v
                    [Human]      final arbiter         ($2-15, only the last few %)

 Each tier is ~10-300x pricier than the one above it. Resolving most examples at
 the top keeps the BLENDED cost per example near the cheap tiers while reserving
 expensive human review for the handful of cases that truly need it.
```

### Offline vs Online Eval Comparison

| Dimension | Offline Batch Eval | Online Shadow Eval |
|-----------|-------------------|-------------------|
| Coverage | 200–500 golden examples | 2–5% of all production queries |
| Freshness | Reflects golden set (may be stale) | Reflects live distribution |
| Cost | Fixed per run | Proportional to traffic |
| Latency | Runs in minutes before deploy | Async, results lag by minutes |
| Catches distribution shift | No | Yes |
| Catches prompt regressions | Yes | Only on sampled queries |
| Human reference required | Yes | Not always (reference-free judge) |

---

## 9. When to Use / When NOT to Use

**Use offline batch eval + LLM-as-judge gate when:**
- Changing the system prompt, RAG chunking strategy, or model version
- Adding a new tool or modifying a tool's description in an agent
- Tuning retrieval parameters (top-k, reranking threshold)
- Rolling back a change and verifying the rollback restores baseline scores

**Use online shadow eval when:**
- Monitoring for distribution shift (user queries changing over time)
- Running A/B tests between two model versions in production
- Building the feedback flywheel — discovering failure modes not in the golden set
- Tracking quality KPIs on a per-feature or per-user-segment basis

**Use human-in-the-loop eval when:**
- Performing a major model version upgrade (GPT-4o → next generation)
- Calibrating a new LLM judge (compare judge scores to human scores on 100 examples)
- Investigating a safety incident where automated eval is insufficient
- Building the adversarial examples for your golden set

**Do NOT use LLM-as-judge as the sole gate for:**
- Safety-critical classification (CSAM detection, suicide/self-harm screening) — use deterministic classifiers with human review loops
- Latency-sensitive paths where the eval judge call would add 3–5 seconds to a user-facing response
- Cases where the judge model is the same as the application model — self-serving bias inflates scores by 10–15%
- Compliance audits — LLM-judge scores are not legally defensible; human rater logs are

---

## 10. Common Pitfalls

**Pitfall 1: Self-serving bias — judge model evaluating itself**

Teams use GPT-4o to judge GPT-4o outputs because it is the strongest available judge. The bias: GPT-4o rates its own outputs 12–15% higher than an independent judge (Claude-3.5-Sonnet) rates the same outputs when phrasing, ordering, and verbosity are matched. The effect is large enough to mask real regressions. In a 2024 analysis across 500 examples, GPT-4o self-judged pass rate was 91%; Claude-judged pass rate on the same examples was 78%. The 13-point gap represented actual failures hidden by self-serving bias.

Fix: always use a judge model from a different provider than the application model. If the application uses GPT-4o, judge with Claude-3.5-Sonnet or Gemini 1.5 Pro. If the application uses Claude, judge with GPT-4o. For the highest-stakes evals, use two independent judges and report disagreements.

**Pitfall 2: Golden-set contamination from training data leakage**

A team built a 300-example golden set for their code generation application by sampling from public GitHub repositories. They later discovered that 47 examples were present in the fine-tuning dataset used by the model being evaluated. The model scored 94% on those 47 examples and 71% on the remaining 253 — but the contaminated aggregate of 83% looked healthy. The regression that would have been caught was invisible.

Fix: For any golden set built from public data, run a deduplication check against the training data of every model you intend to evaluate. Use n-gram overlap (minimum 8-gram) and embedding similarity (threshold 0.92 cosine) to flag potential contamination. For internally generated golden sets, use data generated after the model's training cutoff date.

**Pitfall 3: Golden set passes, production tail fails**

A customer support team had a 250-example golden set with 94% pass rate. Production showed 23% escalation rate on long-tail queries — 3x the target. Investigation revealed the golden set was composed entirely of routine queries from the first month of deployment. Edge cases — account lockouts during high-traffic events, compound queries spanning multiple support topics, non-English queries with mixed scripts — were absent from the golden set entirely.

Fix: Maintain a mandatory composition policy for the golden set. At least 20% of examples must be adversarial or edge cases. After every production incident, add 3–5 examples derived from the failure to the golden set before closing the incident ticket. Track the "golden set coverage score" — the fraction of distinct query clusters (from k-means on embeddings) that have at least 1 golden example.

**Pitfall 4: Judge prompt drift triggers false regression alarms**

A team upgraded their judge prompt to be more detailed (from 80 tokens to 220 tokens) to reduce false positives on edge cases. The new judge prompt assigned scores that averaged 0.4 points lower than the old prompt on the same examples — not because outputs got worse, but because the rubric was stricter. The regression detector flagged every PR for 3 weeks. Developers started ignoring the alarm. A real regression shipped in week 4 while the team assumed it was another judge prompt artifact.

Fix: Pin the judge prompt with a version identifier the same way you pin the model version. When changing the judge prompt, run both old and new prompts on a calibration set of 50 examples with known human ratings. Accept the new prompt only if it improves agreement with human ratings. Never change the judge prompt and the application model in the same PR — you cannot separate the signal.

---

## 11. Technologies & Tools

| Tool | Category | Key Feature | Pricing Model |
|------|----------|-------------|---------------|
| Braintrust | Eval platform | Immutable run history, A/B comparisons, CI integration | Per-run / enterprise |
| LangSmith | Eval + tracing | Tight LangChain integration, annotation queues, dataset versioning | Per-trace + storage |
| Arize Phoenix | Eval + observability | OTEL-native, embedding drift, RAG tracing | Open source + cloud |
| Confident AI / DeepEval | Eval framework | Pytest-based, 14+ built-in metrics, CI gate | Open source + cloud |
| Patronus AI | Eval + safety | Automated red-teaming, custom evaluator fine-tuning | Enterprise |
| Comet Opik | Eval + tracking | MLflow-inspired, LLM-specific metrics, experiment tracking | Open source + cloud |
| Humanloop | Eval + prompt mgmt | Human feedback collection, prompt versioning, A/B testing | Per-seat |
| RAGAS | RAG-specific eval | Faithfulness, answer relevancy, context recall metrics | Open source |
| promptfoo | CLI eval tool | YAML-defined evals, multi-model comparison, CI/CD native | Open source |

**Choosing between tools**: Use promptfoo for simple prompt comparison in CI (zero infrastructure). Use DeepEval for pytest-integrated evals with custom metrics. Use LangSmith if the application already uses LangChain (tracing and eval share the same infrastructure). Use Braintrust or Arize Phoenix for enterprise-scale eval with dashboards, role-based access, and audit trails. RAGAS is the default for RAG-specific faithfulness and recall metrics regardless of which platform you use for orchestration.

---

## 12. Interview Questions with Answers

**Q: Why do unit tests fail for LLM quality assurance, and what does a production eval harness add?**
Unit tests check deterministic string equality — they pass or fail based on exact output matching, which breaks immediately with non-deterministic LLMs that produce valid paraphrases. A production eval harness layers lexical, semantic, and LLM-judge metrics to capture correctness at different abstraction levels, runs statistical regression detection to distinguish real quality drops from sampling noise, and operates continuously in CI and on live traffic rather than only at test time. The harness does not replace tests for deterministic behavior (structured output schema validation, tool call format checks) but adds the probabilistic quality measurement layer that unit tests cannot provide.

**Q: What makes a good golden dataset for LLM eval?**
A good golden dataset has four properties: diversity (covers all major query types including at least 20% adversarial or edge case examples), freshness (no examples predating the model training cutoff to prevent contamination), human-curated references (not model-generated, not scraped without curation), and sufficient size (minimum 200 examples for 80% statistical power at p=0.05 with typical effect sizes of 0.15–0.20). In practice, the hardest property to maintain is freshness — as the application evolves, examples that were edge cases in month 1 become routine in month 6, requiring periodic rebalancing.

**Q: How does LLM-as-judge work and what are its main failure modes?**
LLM-as-judge presents the judge model with a question, reference answer, and model output, asking it to score on a rubric (typically 1–5) and provide a one-sentence rationale. The main failure modes are: self-serving bias (15% score inflation when judge and application share the same model family), verbosity bias (longer answers score higher independent of correctness — mitigated by rubric-grounded prompts that penalize unnecessary length), positional bias (in pairwise comparison, option A scores higher than option B when identical — mitigated by randomizing order and averaging both orderings), and prompt drift (changing the judge prompt changes scores without any application change — mitigated by pinning judge prompt version). Agreement across 3 samples at temperature 0.2 with threshold 0.85 is the standard calibration check.

**Q: How do you detect a statistically significant regression in LLM eval scores?**
Use Welch's t-test for continuous scores (does not assume equal variances — appropriate because baseline and candidate may differ in score distribution shape). Regression is flagged when p < 0.05 AND the score delta exceeds the minimum effect size threshold (typically 0.1 absolute points on a 1–5 scale). For binary pass/fail outcomes, compute Wilson score confidence intervals for each proportion and flag regression when the candidate lower bound falls below the baseline lower bound by more than the minimum effect size. The dual threshold (p-value + effect size) is critical — with 500 examples, a 0.01-point difference is statistically significant but practically irrelevant.

**Q: What is the difference between offline batch eval and online shadow eval?**
Offline batch eval runs against a fixed golden dataset before deployment — it catches regressions on known failure modes but cannot detect distribution shift (queries evolving over time). Online shadow eval samples 2–5% of live traffic, runs the LLM judge asynchronously, and reports quality metrics with a 5–15 minute lag — it catches distribution shift and novel failure modes but provides lower coverage and requires reference-free judging (no ground truth for live queries). Production systems need both: offline as the CI gate, online as the continuous quality monitor. Online eval also feeds the golden set flywheel — low-scoring production examples become new golden records after human review.

**Q: How do you prevent golden-set contamination from model training data?**
Run deduplication between every golden-set example and the suspected training corpus before adding the example to the dataset. Use 8-gram overlap detection for exact or near-exact matches, and embedding cosine similarity at threshold 0.92 for semantic duplicates. For models with known training cutoffs, only use examples generated after that cutoff as ground truth. When sourcing examples from public data (StackOverflow, GitHub), assume contamination and verify by checking whether the model reproduces the exact reference answer verbatim — a strong contamination signal.

**Q: How do you implement an eval-gated CI/CD pipeline?**
The eval gate runs in CI on pull requests that modify prompts, RAG configuration, model selection, or application logic. The gate: (1) loads the versioned golden dataset from the dataset registry, (2) invokes the candidate application against all examples using a thread pool (10–20 concurrent workers), (3) scores each output with the LLM judge, (4) runs the regression detector against the promoted baseline run, (5) fails the PR if regression detected OR if pass rate drops below 85%. The baseline run ID is stored as a CI variable and updated only when a release is promoted to production. Eval gate run time target: under 8 minutes for a 250-example golden set with 20 concurrent workers.

**Q: How do you handle flaky evals — evals that intermittently fail without application changes?**
Flakiness in LLM evals comes from judge non-determinism (temperature > 0), application non-determinism (sampling parameters), and small golden sets (high sampling variance). Mitigations: run the judge at temperature 0.2 and sample 3 responses, using the median score — this reduces judge score standard deviation from 0.6 to 0.2 on a 1–5 scale. Require that a regression be detected in 2 consecutive CI runs before blocking merge. Set a minimum effect size for regression detection (0.1 points) to ignore noise. Track the false-positive rate of the eval gate over 30 days — if it exceeds 5%, recalibrate the threshold. A flaky eval gate that developers learn to re-run until it passes is worse than no gate.

**Q: How do LLM eval scores correlate with business KPIs?**
The correlation is real but indirect and requires explicit measurement. A customer support application measured the relationship between eval judge score (1–5) and CSAT (1–5 customer satisfaction survey): Pearson r = 0.61 (moderate positive correlation). Concretely, examples scoring 4–5 in eval had mean CSAT of 4.2; examples scoring 1–2 in eval had mean CSAT of 2.8. However, 22% of high-scoring eval examples received low CSAT (model was correct but response was too long for the support context), and 14% of low-scoring eval examples received high CSAT (customer accepted an incorrect but confident answer). The implication: eval scores are a leading indicator of business KPIs, not a direct substitute. Calibrate the mapping quarterly using 200+ paired (eval score, KPI) observations.

**Q: How do you calibrate an LLM judge to align with human raters?**
Calibration is a three-step process run on a 100-example calibration set with human ratings. Step 1: collect 3 human ratings per example using a specific rubric and compute inter-rater agreement (target: Krippendorff's alpha > 0.70). Step 2: run the candidate judge prompt on the same 100 examples and compute agreement with the majority human rating. Step 3: if judge–human agreement is below 75%, modify the rubric (add examples, clarify scoring criteria) and repeat. After calibration, the judge is accepted if it achieves 80%+ agreement with human majority on the calibration set. Re-run calibration quarterly and after any judge model upgrade.

**Q: What is the cost of running a production eval harness and how do you contain it?**
For a 300-example golden set with GPT-4o as judge at 3 samples per example: 300 examples × 3 samples × 500 tokens per judge call = 450K tokens per eval run. At $5/1M input + $15/1M output (assume 400 input / 100 output split): cost per run ≈ $0.90. At 20 CI runs per day (active team) = $18/day. For online eval at 3% sample rate with 10K requests/day = 300 shadow evals/day = $0.90/day. Total: under $20/day for a mid-sized team. Cost reduction levers: use a smaller judge model (GPT-4o-mini at $0.15/1M input) for routine regression checks, reserving GPT-4o for edge cases; reduce judge samples from 3 to 1 for stable, well-calibrated runs; apply eval only to changed sections of the application.

**Q: How should you version and manage eval datasets over time?**
Use semantic versioning: MAJOR.MINOR.PATCH. MAJOR: complete rebuild of the dataset (new composition policy, different task definition). MINOR: addition of 20+ new examples or removal of contaminated examples. PATCH: metadata fixes, tag corrections. Store each version as an immutable file in object storage (S3, GCS) with a content hash. Never modify a published version in place — create a new version. The regression detector requires the same dataset hash for baseline and candidate; comparing across dataset versions is a separate migration analysis. Quarterly, audit the dataset against the current production query distribution and bump MINOR if coverage has dropped below 80% of active query clusters.

**Q: How do you design a golden dataset for a RAG application specifically?**
A RAG golden dataset must test both retrieval quality and generation quality independently. Each example needs: (1) a question, (2) the ground-truth document set that should be retrieved (to measure recall@k), (3) a reference answer grounded only in those documents (to measure faithfulness), and (4) a relevance label (is the question answerable from the corpus?). Include examples where the answer is not in the corpus — the correct model behavior is to acknowledge the gap, not hallucinate. Include examples that require synthesizing information from 3+ documents — this tests multi-hop reasoning, the hardest RAG failure mode. RAGAS provides built-in metrics for context recall (are the right documents retrieved?), faithfulness (does the answer stay in the retrieved context?), and answer relevance (does the answer address the question?).

**Q: What happens when a model provider updates a model silently?**
Model providers occasionally update models in-place (e.g., gpt-4o points to a new checkpoint). This produces apparent regressions or improvements that have nothing to do with your application changes. Defense: pin model versions explicitly (gpt-4o-2024-08-06, not gpt-4o). Monitor your eval dashboard for score changes on days when no application code changed — a score change without a code change is almost always a provider-side model update. When detected, update the baseline run ID to the post-update scores so future regressions are measured against the new model's baseline, not the old one.

**Q: How do you evaluate an agentic application where outputs are not single text strings?**
Agent outputs are multi-step — plan, tool calls, intermediate observations, final answer. Evaluate each layer: (1) tool selection accuracy (did the agent call the right tools in the right order?) using exact-match or set-overlap against a reference tool call sequence; (2) intermediate observation quality (did tool calls return useful information?) using the same RAG faithfulness metrics; (3) final answer quality using LLM judge on the (question, tool results, final answer) triple. The judge prompt for agents must include the full tool call trace so the judge can assess whether the reasoning chain supports the final answer. Trajectory-level evaluation — did the agent take the optimal path, or did it take a longer path that still reached the right answer? — is the hardest open problem in agent eval as of 2025.

**Q: How do you reduce bias from verbosity in LLM-as-judge scoring?**
Verbosity bias causes the judge to prefer longer, more detailed responses regardless of accuracy. Mitigations: (1) rubric-grounded scoring — specify "a score of 5 does not require exhaustive detail; concise and accurate is preferred over verbose and partially accurate"; (2) reference normalization — when computing similarity to a reference, normalize response length to the reference length and penalize >2x verbosity; (3) pairwise comparison with explicit instructions — "prefer the shorter response if both are equally accurate"; (4) length-controlled sampling — when building the golden set, sample model outputs at different temperature and max_token settings and label the shortest correct response as the reference. Studies show verbosity bias inflates scores by 0.2–0.4 absolute points on a 5-point scale, enough to mask real regressions.

---

## 13. Best Practices

1. **Always include adversarial examples in the golden set** — at minimum 20% of examples should be edge cases or known failure modes. A golden set composed entirely of routine queries will miss the regressions that matter most.

2. **Pin the judge model version explicitly** — use `gpt-4o-2024-08-06`, not `gpt-4o`. A judge model upgrade changes eval scores independent of any application change; unpinned judges make regression detection meaningless.

3. **Never use the same model family as both the application model and the judge model** — self-serving bias inflates scores by 12–15%. If the application uses GPT-4o, judge with Claude-3.5-Sonnet or Gemini 1.5 Pro.

4. **Run eval as code in version control, not as a manual checklist** — the eval dataset version, judge prompt version, metric thresholds, and significance levels must all be checked into the same repository as the application code and reviewed in PRs.

5. **Set both a p-value threshold AND a minimum effect size for regression** — a 0.01-point score change is statistically significant with 500 examples but practically irrelevant. Require p < 0.05 AND absolute delta > 0.1 points to trigger a regression alarm.

6. **Update the golden set after every production incident** — when a user finds a failure mode, add 3–5 examples derived from that failure to the golden set before closing the incident ticket. This prevents the same failure from shipping twice.

7. **Separate the regression baseline from the deployment baseline** — the baseline run ID for regression detection is updated when a release is promoted, not when code is merged. This ensures you detect regressions against what is actually running in production, not against the previous PR.

8. **Run online shadow eval at 2–5% sample rate, not higher** — beyond 5%, the eval cost approaches the cost of running the application itself, and the marginal information gain flattens. Below 2%, sample sizes are too small for statistical confidence within a 24-hour window.

9. **Calibrate the judge against human raters quarterly** — the judge's agreement with human ratings decays as the model is updated and as your application domain evolves. A judge calibrated at 82% agreement in Q1 may drop to 68% by Q4 without recalibration.

10. **Track the false-positive rate of the eval gate** — if more than 5% of CI runs fail without a real regression, developers learn to re-run until the gate passes. A distrusted gate is worse than no gate. Reduce false positives by increasing the minimum effect size, requiring 2 consecutive failures, or adding more examples to reduce sampling variance.

---

## 14. Case Study

This eval harness primitive is used as the quality control layer across multiple case studies in this repository. The following examples show how each application instantiates the harness for its specific quality dimensions.

### [`../design_chatgpt.md`](../design_chatgpt.md) — Model Release Gating at Scale

ChatGPT's model release process gates every checkpoint on a multi-tier eval suite before traffic is shifted. The offline eval tier runs 10,000+ examples across categories including instruction following, factual QA, coding, math, and safety. LLM-as-judge (using a separate judge model) scores subjective quality; automated metrics score structured outputs. The regression detector compares candidate checkpoint to the currently deployed model with statistical significance testing. A category-level regression (not just aggregate) blocks the release — a model that improves coding but regresses on instruction following fails the gate. The A/B-gated eval tier then runs both models on live traffic (1% canary) before full traffic shift, with real-time quality monitoring closing the loop. The golden dataset at this scale has millions of examples versioned by capability domain, and the eval runs are parallelized across GPU clusters.

### [`../design_ai_search_engine.md`](../design_ai_search_engine.md) — Answer Faithfulness Evaluation

An AI search engine must ensure that synthesized answers are grounded in retrieved documents rather than hallucinated. The eval harness here focuses on two metrics: faithfulness (is every claim in the answer attributable to a retrieved source?) and answer relevance (does the answer address the user's query?). The RAGAS framework provides reference-free versions of both metrics using LLM-as-judge. The golden dataset includes examples where the answer is not in the index — the expected behavior is a "not found" response, not a hallucination. Faithfulness must exceed 0.90 on the golden set before any retrieval or reranking change is merged. Online shadow eval runs at 3% sample rate and alerts when faithfulness drops below 0.85 in any 4-hour window — indicating retrieval quality degradation on evolving queries.

### [`../design_notion_ai.md`](../design_notion_ai.md) — Workspace-Grounded Q&A Evaluation

Notion AI evaluates answers grounded in a user's specific workspace content, making golden dataset construction complex: examples are synthetic because real user data cannot be used. The eval harness uses a workspace simulator that generates 300 synthetic workspaces of varying size and topic (engineering wikis, product specs, meeting notes), then generates question-answer pairs grounded in each workspace. The judge evaluates whether the model's answer is supported exclusively by the simulated workspace content (no world knowledge leakage). A separate eval dimension measures whether the model correctly declines to answer when the answer is not in the workspace. The eval gate requires workspace-grounding accuracy > 92% — failures here constitute a privacy trust violation, not just a quality issue.

### [`../design_customer_support_bot.md`](../design_customer_support_bot.md) — Escalation Decision Evaluation

The customer support bot's most critical decision is when to escalate to a human agent. The eval harness uses a binary escalation classifier eval with a specialized golden set: 40% of examples are cases where escalation is correct (upset customer, billing dispute exceeding $200, account security concern), 60% are cases where self-service resolution is correct. The LLM judge is replaced by a rubric-based classifier (deterministic rules based on the judge's output) to reduce variance on this binary decision. Recall on the escalation-required class must exceed 0.95 — missing an escalation is a worse failure than unnecessary escalation. The online eval samples 5% of closed support tickets and uses the resolved/unresolved outcome as a weak label to measure whether escalation decisions correlate with ticket resolution success.

### [`../design_ai_code_review.md`](../design_ai_code_review.md) — Code Review Quality and SWE-bench Integration

The AI code review case study uses SWE-bench as the primary offline eval benchmark — a standardized benchmark of 2,294 real GitHub issues where the model must generate a patch that passes the repository's existing test suite. The eval harness wraps SWE-bench with the production application's code review pipeline (retrieval of relevant files, context assembly, patch generation) rather than running SWE-bench in isolation. This detects when changes to the RAG retrieval logic or prompt template degrade patch quality even when the underlying model scores are stable. The LLM judge scores code review comments on correctness, specificity, and actionability using a rubric calibrated against senior engineer ratings. A code review comment that is technically correct but too vague to act on scores 2/5 — the same as an incorrect comment. This distinction required 3 iterations of rubric refinement to achieve 80% judge–human agreement.

---

*Related modules:*
- [`../../evaluation_and_benchmarks/README.md`](../../evaluation_and_benchmarks/README.md) — MMLU, HumanEval, Chatbot Arena, benchmark methodology
- [`../../llm_testing_strategies/README.md`](../../llm_testing_strategies/README.md) — golden datasets, regression eval, CI/CD integration strategies
- [`../../llm_observability_and_monitoring/README.md`](../../llm_observability_and_monitoring/README.md) — tracing, quality monitoring, cost attribution
- [`../../prompt_management_and_promptops/README.md`](../../prompt_management_and_promptops/README.md) — prompt versioning, eval-gated CI, A/B testing
- [`../../data_flywheels_and_continuous_learning/README.md`](../../data_flywheels_and_continuous_learning/README.md) — closing the production feedback loop into training data
