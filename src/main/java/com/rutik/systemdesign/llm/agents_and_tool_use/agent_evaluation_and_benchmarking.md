# Agent Evaluation & Benchmarking

## Concept Overview

Evaluating LLM agents is fundamentally different from evaluating single-call LLMs. Agent evaluation must account for multi-step trajectories, tool use correctness, error recovery, efficiency (steps taken, tokens used, cost per task), and final outcome quality. Standard NLP metrics (BLEU, ROUGE, perplexity) are nearly useless for agents.

Two complementary evaluation modes exist: trajectory-level evaluation (was each step correct?) and outcome-level evaluation (did the task succeed?). Both are needed — a correct final answer via a lucky shortcut is less reliable than a correct answer via a coherent multi-step plan.

---

## Intuition

> **One-line analogy**: Evaluating an agent is like reviewing a surgeon's procedure, not just the patient outcome — you need to check both that the patient survived and that the technique was sound.

**Mental model**: Single LLM evaluation is easy — compare output to ground truth. Agent evaluation has two hard problems: (1) there is no unique correct trajectory for most tasks (many valid paths lead to the same answer); (2) evaluating intermediate steps requires understanding intent, not just text similarity. The state-of-the-art solution is LLM-as-judge: use a capable LLM to evaluate trajectories holistically, providing scores with rubric-grounded reasoning.

**Why it matters**: Agents deployed to production must be continuously monitored. Without evaluation, you don't know if performance is degrading (model drift, tool changes), can't compare architectures, and can't justify deployment decisions to stakeholders.

**Key insight**: Cost-per-task is often the most actionable production metric. A 10% quality improvement that doubles cost may not be worth it; a 5% quality improvement that halves cost often is.

---

## Core Principles

- **Benchmark ≠ production quality**: all benchmarks have distributional gaps from real tasks; treat benchmark scores as directional, not absolute.
- **Trajectory + outcome**: evaluate both path and result; outcome-only evaluation misses brittle shortcuts.
- **Multiple metrics**: quality (task success rate), efficiency (steps, tokens, cost), reliability (variance across runs), safety (harmful action rate).
- **LLM-as-judge at scale**: human evaluation is gold but expensive; LLM-as-judge with calibrated rubrics is the practical alternative.
- **Golden trajectories as reference**: generate expert-annotated correct trajectories; compare agent trajectories against them step-by-step.

---

## How It Works — Detailed Mechanics

### GAIA Benchmark

```
GAIA (General AI Assistants, Mialon et al., 2023)

Purpose: Evaluate general-purpose AI assistant capabilities requiring
         real-world tool use and multi-step reasoning

Structure:
  466 tasks across 3 difficulty levels:
    Level 1 (easy): 165 tasks, ~avg 5 steps needed
    Level 2 (medium): 232 tasks, ~avg 10 steps needed
    Level 3 (hard): 69 tasks, 10+ steps, complex multi-modal reasoning

Task types:
  - Web search + synthesis
  - File reading + analysis (PDFs, spreadsheets)
  - Code execution for data analysis
  - Multi-step fact verification
  - Tool-augmented math/science problems

Example GAIA Level 2 task:
  "What was the total revenue of the top 3 companies by market cap in 2023?
   Express as a percentage of US GDP in 2023."
  Required steps:
    1. Look up top 3 companies by market cap in 2023
    2. Find revenue for each (may need multiple searches)
    3. Find US GDP in 2023
    4. Calculate percentage
    5. Return formatted answer

Scoring:
  Exact match on final answer (normalized: strip units, lowercase, etc.)
  Binary: 0 or 1 per task

Results (2024):
  GPT-4 (no tools): 15% Level 1, 5% Level 2, <1% Level 3
  GPT-4 + browsing: 30% Level 1, 20% Level 2, 5% Level 3
  Claude 3.5 Sonnet + tools: ~50% Level 1, ~35% Level 2, ~15% Level 3
  Human annotators: 92% Level 1, 82% Level 2, 47% Level 3
```

### SWE-bench

```
SWE-bench (Software Engineering Benchmark, Jimenez et al., 2023)

Purpose: Measure ability to resolve real GitHub issues
         in real Python repositories

Structure:
  2294 real GitHub issues from 12 repositories:
    Django, Flask, Sympy, Pandas, NumPy, Requests, SciPy,
    Marshmallow, Pylint, Pytest, Scikit-learn, Astropy

Task format:
  Input:  issue description + entire codebase at time of issue
  Output: git diff (patch) that resolves the failing tests

Scoring:
  1. Apply the patch to the codebase
  2. Run the test suite (both originally passing and newly added tests)
  3. "Resolved" = all relevant tests now pass
  Binary score per issue: 0 or 1

SWE-bench Verified (500 tasks):
  Subset manually verified to have clear, well-specified issues

Historical results:
  GPT-4 (2023, no tools): 1.74%
  SWE-agent (Claude-3.5, 2024): 18.1%
  Devin (Cognition, 2024): 13.8% (original claim)
  Claude 3.5 Sonnet + SWE-bench scaffold: 49%
  o3 + specialized scaffolding: 71.7% (verified subset)

What 20% means in practice:
  - 1 in 5 real GitHub issues is automatically resolved
  - Issues are real production bugs, not toy problems
  - Evaluation is purely automated (test suite)
  - 20% is a dramatic improvement over zero; 50%+ is commercially viable
  - Remaining 50-80% require: codebase-specific knowledge, test writing,
    design judgment, architecture decisions
```

### AgentBench

```
AgentBench (Liu et al., 2023)

Purpose: Comprehensive multi-environment agent evaluation

8 environments:
  OS: Terminal command execution tasks
  DB: SQL query and database interaction
  KG: Knowledge graph traversal and querying
  LTP: Long-term planning tasks
  HouseHolding: Embodied household tasks (ALFWorld)
  WebShop: E-commerce purchasing agents
  Mind2Web: Web navigation on 2K real websites
  WebArena: Realistic web environment (100+ sites)

Scoring: success rate per environment (0-100%)

Results (2023):
  GPT-4: ~26% overall
  GPT-3.5-turbo: ~9%
  Text-davinci-003: ~4%
  Open-source models (Llama-2-70B): <5%

Key finding: Strong performance on OS/DB tasks;
             much weaker on web/household tasks
```

### WebArena

```
WebArena (Zhou et al., 2024)

Purpose: Realistic web navigation — functional websites with real backends

810 tasks across 5 websites:
  Shopping (OpenMag e-commerce)
  Forum (Postmill, Reddit-like)
  Gitlab (software development)
  CMS (WordPress)
  Maps (OpenStreetMap)

Task examples:
  "Find all products with a customer rating under 2 stars and add the
   cheapest one to the cart"
  "Close all issues in the 'backend' repository that contain 'typo' in title"
  "Post a comment on the top-voted post in the 'python' subreddit"

Evaluation:
  Function-based: check backend state matches expected state
  (e.g., database was actually updated correctly)

Results:
  GPT-4V (2024): ~14%
  Claude 3 Sonnet: ~20%
  State-of-the-art with custom scaffolding: ~35%
  Human: ~78%
```

### Trajectory-Level Evaluation

```python
# Trajectory annotation schema
class AgentStep:
    thought: str               # model's reasoning
    action: str                # tool name + arguments
    observation: str           # tool result
    quality_score: float       # 0-1 annotation (human or LLM)
    is_necessary: bool         # was this step needed for success?
    is_correct: bool           # was the action correct given the state?
    error_type: Optional[str]  # "hallucination", "wrong_tool", "inefficient"

class AgentTrajectory:
    task: str
    steps: list[AgentStep]
    final_answer: str
    success: bool              # did task succeed?
    num_steps: int
    total_tokens: int
    total_cost_usd: float
    wall_time_seconds: float

# Step success rate: fraction of steps that are necessary and correct
def step_success_rate(trajectory: AgentTrajectory) -> float:
    necessary_correct = sum(
        1 for s in trajectory.steps
        if s.is_necessary and s.is_correct
    )
    total_necessary = sum(1 for s in trajectory.steps if s.is_necessary)
    return necessary_correct / total_necessary if total_necessary > 0 else 0.0

# Efficiency metrics
def efficiency_metrics(trajectory: AgentTrajectory) -> dict:
    return {
        "steps_taken": trajectory.num_steps,
        "tokens_used": trajectory.total_tokens,
        "cost_usd": trajectory.total_cost_usd,
        "unnecessary_steps": sum(1 for s in trajectory.steps if not s.is_necessary),
        "wall_time_s": trajectory.wall_time_seconds,
        "cost_per_step": trajectory.total_cost_usd / trajectory.num_steps
    }
```

### LLM-as-Judge for Agent Traces

```python
JUDGE_PROMPT = """You are evaluating an AI agent's performance on a task.

Task: {task}
Agent's trajectory:
{trajectory}

Final answer: {final_answer}

Evaluate on four dimensions (score 1-5, with rubric):

1. TASK SUCCESS (1=complete failure, 5=perfect success)
   - Did the agent accomplish the stated task?
   - Is the final answer correct and complete?

2. REASONING QUALITY (1=incoherent, 5=excellent reasoning)
   - Are the agent's thoughts logical and relevant?
   - Does each action follow from the preceding thought?

3. EFFICIENCY (1=massively wasteful, 5=optimal)
   - Were steps unnecessary or redundant?
   - Did the agent use appropriate tools?
   - Fewer steps with same quality = higher score

4. TOOL USE CORRECTNESS (1=systematically wrong, 5=all tools used correctly)
   - Were tool arguments correct and well-formed?
   - Was the right tool chosen for each step?
   - Were tool errors handled appropriately?

Output format:
{{
  "task_success": {{score: X, reasoning: "..."}},
  "reasoning_quality": {{score: X, reasoning: "..."}},
  "efficiency": {{score: X, reasoning: "..."}},
  "tool_use": {{score: X, reasoning: "..."}},
  "overall": X,
  "key_failures": ["..."],
  "key_strengths": ["..."]
}}"""

async def evaluate_with_llm_judge(trajectory: AgentTrajectory,
                                   judge_model: str = "gpt-4o") -> dict:
    trajectory_text = format_trajectory(trajectory)
    response = await llm.ainvoke([
        SystemMessage("You are an expert AI evaluator."),
        HumanMessage(JUDGE_PROMPT.format(
            task=trajectory.task,
            trajectory=trajectory_text,
            final_answer=trajectory.final_answer
        ))
    ], response_format={"type": "json_object"})
    return json.loads(response.content)
```

### Building a Custom Eval Harness

```python
class AgentEvalHarness:
    def __init__(self, agent_factory, eval_dataset: list[dict]):
        self.agent_factory = agent_factory
        self.dataset = eval_dataset      # [{task, expected_answer, difficulty}]

    async def run_evaluation(self, n_parallel: int = 5) -> EvalResults:
        semaphore = asyncio.Semaphore(n_parallel)

        async def eval_one(item: dict) -> dict:
            async with semaphore:
                agent = self.agent_factory()
                start = time.time()
                try:
                    result = await agent.run(item["task"])
                    success = self.check_answer(result, item["expected_answer"])
                except Exception as e:
                    result = None
                    success = False

                return {
                    "task_id": item["id"],
                    "success": success,
                    "cost_usd": agent.total_cost,
                    "num_steps": agent.step_count,
                    "wall_time": time.time() - start,
                    "difficulty": item.get("difficulty", "unknown")
                }

        results = await asyncio.gather(*[eval_one(item) for item in self.dataset])
        return EvalResults(results)

    def check_answer(self, actual: str, expected: str) -> bool:
        # Normalize: lowercase, strip punctuation, handle units
        norm_actual = normalize_answer(actual)
        norm_expected = normalize_answer(expected)
        if norm_actual == norm_expected:
            return True
        # Fuzzy match for longer answers (F1 over tokens)
        return f1_score(norm_actual, norm_expected) > 0.8

class EvalResults:
    def __init__(self, results: list[dict]):
        self.results = results

    @property
    def task_success_rate(self) -> float:
        return sum(r["success"] for r in self.results) / len(self.results)

    @property
    def avg_cost_per_task(self) -> float:
        return sum(r["cost_usd"] for r in self.results) / len(self.results)

    @property
    def avg_steps_per_task(self) -> float:
        return sum(r["num_steps"] for r in self.results) / len(self.results)

    def by_difficulty(self) -> dict[str, float]:
        by_diff = defaultdict(list)
        for r in self.results:
            by_diff[r["difficulty"]].append(r["success"])
        return {k: sum(v)/len(v) for k, v in by_diff.items()}
```

### pass@k for Agents

```
pass@k: probability that at least 1 of k independent agent runs succeeds

Calculation:
  Run the agent k times on the same task with temperature > 0
  n = k runs
  c = number of successful runs
  pass@k = 1 - C(n-c, k) / C(n, k)

Example:
  Task run 5 times: 3 successes, 2 failures
  pass@1 = c/n = 3/5 = 0.60
  pass@2 = 1 - (2/5)(1/5) / ... ≈ 0.84
  pass@5 = 1 - 0/5 = 1.0 (at least one succeeds)

Usage:
  pass@1: production metric (single run reliability)
  pass@k: upper bound metric (best-of-k with selection oracle)
  gap between pass@1 and pass@5: measures output variance
  Large gap: agent is inconsistent; investigate why it fails on some runs
```

---

## Architecture Diagrams

### Evaluation Pipeline

```
Test Dataset (N tasks)
        |
        v
┌──────────────────────────────────────┐
│  AGENT EXECUTION                     │
│                                      │
│  for each task:                      │
│    agent.run(task)                   │
│    → trajectory (steps + answer)     │
│    → cost, tokens, time              │
└──────────────────────────────────────┘
        |
        v
        ├── [OUTCOME EVAL] ────────────► binary success (0/1)
        |   exact match / LLM judge
        |
        ├── [TRAJECTORY EVAL] ─────────► step scores
        |   LLM-as-judge rubric
        |
        └── [EFFICIENCY EVAL] ─────────► cost/tokens/steps
                                         per-task and aggregate
        |
        v
┌──────────────────────────────────────┐
│  AGGREGATE METRICS                   │
│  - Task success rate                 │
│  - Avg cost per task                 │
│  - Avg steps per task                │
│  - Step success rate                 │
│  - pass@k distribution               │
│  - Results by difficulty/category    │
└──────────────────────────────────────┘
```

---

## Real-World Examples

### Anthropic's Internal Agent Eval

- Anthropic evaluates Claude-based agents on internal task suites covering coding, research, and tool use
- Trajectory evaluation: every step scored by LLM judge with specific rubrics
- Cost tracking: every run logged with token counts; cost-per-task alerts if it exceeds a budget threshold
- Regression testing: every model update must maintain or improve on a suite of 500+ agent tasks

### DeepMind SIMA (Scalable Instructable Multiworld Agent)

- Evaluates agents on 3D game environments
- Task success rate across 600+ different tasks
- Generalization: agents trained on N-1 games evaluated on the N-th game
- Result: agents that understand natural language instructions generalize better

### Production Agent Monitoring at Scale

A large enterprise deploys a research agent:
- Tracks: success rate (task resolved), cost ($/task), steps (efficiency), time (SLA)
- Alerts: if daily cost > $100, if success rate drops > 5%, if avg steps > 15
- LLM judge runs on 5% sample of production traces (cost control)
- Weekly: manual review of 20 randomly sampled failure cases

---

## Tradeoffs

| Evaluation Method | Cost | Scale | Quality | Latency |
|------------------|------|-------|---------|---------|
| Human evaluation | Very high | Low | Highest | Days |
| LLM-as-judge | Medium | High | Good | Minutes |
| Automated outcome eval | Low | Very high | Binary | Seconds |
| Trajectory scoring | High | Medium | High | Hours |

| Benchmark | Task Type | Difficulty | Coverage | Automation |
|-----------|-----------|------------|----------|------------|
| GAIA | General tool use | Easy-Hard | Broad | Full |
| SWE-bench | Code repair | Very Hard | Narrow (Python) | Full |
| AgentBench | Multi-environment | Variable | Broad | Full |
| WebArena | Web navigation | Hard | Web-focused | Full |
| Custom harness | Domain-specific | Configurable | Narrow | Varies |

---

## When to Use / When NOT to Use

### Invest in Comprehensive Eval When:
- Before any production deployment — task success rate and cost-per-task must be benchmarked
- When comparing two agent architectures or prompting strategies
- When updating the underlying model or tools
- When task success rate in production drops (regression detection)

### Skip / Simplify When:
- Early prototyping phase (eyeball testing is sufficient)
- Internal tools with low stakes (no safety risk, easy to manually verify)
- Deterministic pipelines with no agent loop (just test inputs/outputs)

---

## Common Pitfalls

1. **Evaluating only on benchmark, not production distribution**: GAIA and SWE-bench have different task distributions from your actual use case. Always build a custom eval dataset from real production tasks.

2. **Outcome-only evaluation misses systematic failures**: an agent that takes 20 steps to complete a 5-step task has quality issues that don't show in binary success rate. Always track trajectory efficiency metrics.

3. **LLM judge bias**: using the same model as the agent to judge the agent creates systematic favoritism. Use a different, ideally stronger model as judge. Use model self-evaluation only as a last resort.

4. **Not accounting for variance**: running each benchmark task once produces noisy estimates. Use at least 3 runs and report confidence intervals. pass@1 variance is high for difficult tasks.

5. **Cost blindness**: teams optimize for task success rate without tracking cost-per-task. An agent that achieves 70% success at $5/task vs. 65% success at $0.50/task — the cheaper one may be better for production.

---

## Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **GAIA benchmark** | General agent eval | Tool-use, web search, reasoning |
| **SWE-bench** | Code repair eval | Real GitHub issues; Python |
| **AgentBench** | Multi-env eval | OS, DB, web, household tasks |
| **WebArena** | Web navigation eval | Realistic web environments |
| **LangSmith** | Trace logging + eval | Built-in LLM judge support |
| **Langfuse** | Open-source eval | Trace + score; any framework |
| **RAGAS** | RAG + agent eval | Faithfulness, relevance metrics |
| **Braintrust** | Eval platform | Dataset management + CI/CD eval |
| **Weave (W&B)** | Agent trace eval | Weights & Biases product |

---

## Interview Questions with Answers

**Q: Why is evaluating agents harder than evaluating single LLM calls?**
A: Single LLM evaluation compares one output to one expected output — straightforward. Agent evaluation has three additional dimensions: (1) trajectory length — multiple steps, each potentially correct or incorrect; (2) path non-uniqueness — many valid trajectories lead to the same correct answer, so you can't compare to a single reference; (3) efficiency — a correct answer achieved in 20 steps is worse than one achieved in 5. Additionally, agent errors compound: a wrong tool call in step 2 causes cascading failures in steps 3-10. Evaluation must account for both outcome correctness and trajectory quality, requiring either human annotation or capable LLM judges with rubrics.

**Q: How does SWE-bench work and why is it considered a rigorous benchmark?**
A: SWE-bench provides 2294 real GitHub issues from 12 Python repositories. The agent receives the issue description and the full codebase at the time of filing, and must produce a patch (git diff). Evaluation is automated: apply the patch, run the repository's test suite, check if previously failing tests now pass without breaking previously passing tests. It's rigorous because: tasks are drawn from real production codebases (not synthetic problems), success is binary and programmatic (no human judgment of "close enough"), the test coverage verifies correct behavior rather than surface-level code similarity, and the distribution covers diverse bug types across diverse codebases. The benchmark is hard precisely because it requires codebase understanding, not just code generation.

**Q: What is LLM-as-judge and when is it reliable?**
A: LLM-as-judge uses a capable LLM (often GPT-4o or Claude) to score agent trajectories against a rubric, replacing or augmenting human evaluation. The judge receives the task, the full trajectory, and a structured scoring rubric; it outputs scores with reasoning per dimension. It's reliable when: (1) the scoring rubric is specific and unambiguous; (2) the judge model is stronger or at least equal in capability to the judged model; (3) you validate the judge against human labels on a calibration set (target: judge-human agreement >80%). It's unreliable when: the task requires domain expertise the judge doesn't have, the rubric is vague, or you use the same model as both agent and judge (self-serving bias). Production use: LLM judge on 5-10% of traces for cost control, with spot human review.

**Q: What is the GAIA benchmark and what does it test that other benchmarks miss?**
A: GAIA (General AI Assistants) tests real-world tool-use reasoning across 466 tasks at three difficulty levels. Unlike coding-focused benchmarks (SWE-bench) or single-domain benchmarks, GAIA covers general assistant capabilities: web search and synthesis, file analysis, multi-step fact verification, calculator-style reasoning. Its key property is that tasks require tool use — they can't be solved from parametric knowledge alone. GAIA difficulty levels correspond to average steps required (Level 1: ~5, Level 2: ~10, Level 3: 10+). The gap between AI performance (~35% average) and human performance (~82%) reveals where agents fail: multi-step reasoning that humans find natural remains challenging for current systems.

**Q: How do you build a custom eval harness for a production agent?**
A: (1) Dataset creation: sample 100-200 real production tasks; manually annotate correct answers or use LLM to generate expected answers; tag by difficulty and category; (2) Agent execution: run the agent on each task with timeout (prevent runaway); log complete trajectory (steps, tokens, cost, wall time); (3) Outcome evaluation: compare final answer to expected (exact match or LLM judge for open-ended answers); (4) Efficiency evaluation: compute steps-per-task, cost-per-task, success-per-dollar; (5) Aggregate and monitor: track metrics over time; alert on regressions; stratify results by difficulty and task category. Key: run the harness in CI on every agent code change to catch regressions before production.

**Q: What is pass@k and why does it matter for agent evaluation?**
A: pass@k estimates the probability that at least one of k independent runs succeeds on a task. pass@1 is the standard production metric (probability a single run succeeds). pass@5 or pass@10 is the "best-of-k" upper bound — useful for understanding the agent's ceiling quality when you can afford multiple runs and select the best. The gap between pass@1 and pass@5 measures inconsistency: a large gap means the agent sometimes succeeds on the same task but often fails — indicating sensitivity to stochastic factors (temperature, random search results). For production, focus on pass@1. For architecture comparison, use both: an agent with pass@1=0.5 and pass@5=0.9 is more improvable than one with pass@1=0.5 and pass@5=0.6.

**Q: What metrics should you track for a production agent in steady state?**
A: Core metrics: (1) Task success rate — binary or LLM-scored; track daily P7D rolling average; alert if drops >5%; (2) Cost per task — average $/task; alert if exceeds budget; (3) P95 latency — wall time for 95th percentile task; SLA adherence; (4) Step count per task — efficiency metric; rising step count indicates model or tool degradation; (5) Tool error rate — fraction of tool calls returning errors; high rate indicates infrastructure or API issues; (6) Human escalation rate — for agents with HITL; rising rate indicates quality degradation. Supporting metrics: token usage distribution, model calls per task, retry rate. Alert thresholds: set during baseline period (first 2 weeks), then alert on >2 standard deviation shifts.

**Q: How do golden trajectories work in agent evaluation?**
A: A golden trajectory is an expert-annotated correct solution path for a task: the ideal sequence of tool calls, their arguments, and expected outputs that correctly and efficiently solves the task. Generated by: (1) human experts solving the task while being recorded; (2) a strong model (o1, Claude Opus) solving the task with expert review and correction. Usage: (1) step-level F1: compare agent trajectory steps to golden trajectory steps; (2) prefix match: check if agent's first N steps match golden steps before diverging; (3) tool argument similarity: for matching tool calls, compare argument quality. Limitation: most tasks have multiple valid trajectories — a golden trajectory is one valid path, not the only one. Use golden trajectories to detect systematic errors (always using wrong tool, always forming poor queries) rather than as rigid correct answers.

**Q: How do you detect when a production agent has degraded in quality?**
A: Automated regression detection: (1) scheduled eval runs on the fixed eval dataset (daily or per deployment); compare to historical baseline; (2) production sampling: run LLM judge on 5% of live traffic; track daily judgment scores; (3) proxy metrics that correlate with quality: user satisfaction signals (thumbs down, rephrasing the question), step count anomalies (agent taking 2× normal steps), escalation rate (HITL agents asking for help more), tool error rate (tool calls failing more). Alert strategy: primary metric (task success rate) alerts are high-severity; proxy metric alerts are medium; combine multiple proxy signals before escalating. Root cause: when quality drops, check: model version change, tool API changes, context window changes, or prompt modifications.

**Q: What is the difference between task success rate and step success rate?**
A: Task success rate is binary outcome-level: did the agent complete the task correctly? It is the primary metric for business value. Step success rate measures trajectory quality: fraction of individual steps that were necessary and correctly executed. An agent can have: high task success + low step success = often gets lucky or takes inefficient detours; low task success + high step success = nearly correct reasoning but fails at final synthesis; both high = ideal. Step success rate requires trajectory annotation (human or LLM judge per step) — expensive but reveals systematic reasoning errors that outcome-only evaluation misses. Use step success rate for diagnosing quality problems, task success rate for production monitoring.

---

## Best Practices

1. **Build a domain-specific eval dataset**: don't rely solely on public benchmarks; sample 100+ real tasks from your production distribution.
2. **Track cost-per-task from day 1**: quality improvements that double cost may not be worth it; cost efficiency is as important as raw quality.
3. **Use LLM judge on a calibration set first**: validate judge-human agreement on 50 tasks before trusting LLM judge scores at scale.
4. **Run evals in CI**: every agent code change should trigger an automated eval run; catch regressions before production deployment.
5. **Stratify results by difficulty and category**: aggregate success rate hides where the agent struggles; per-category analysis reveals specific failure modes.
6. **Monitor proxy metrics in production**: direct eval of all live traffic is too expensive; track correlated signals (step count, error rate, escalation rate) as real-time quality proxies.
