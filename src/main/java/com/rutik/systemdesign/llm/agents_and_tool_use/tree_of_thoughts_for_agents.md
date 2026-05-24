# Tree of Thoughts for Agents — Deep Dive

---

## 1. Concept Overview

Tree of Thoughts (ToT) is a deliberate problem-solving framework for LLMs introduced by Yao et al. (2023) that generalizes chain-of-thought prompting by maintaining a tree of intermediate reasoning steps (thoughts) rather than a single linear chain. Applied to agent planning, ToT transforms the flat "generate one action, execute, repeat" loop into a search problem: at each planning step the agent generates multiple candidate next actions, evaluates them with a value function, selects the most promising branches, and recurses — enabling backtracking, lookahead, and global search across the space of possible plans.

Key properties:
- **Exploration over exploitation**: considers multiple alternatives before committing
- **Evaluation-guided search**: a value function (LLM-based scorer or domain verifier) prunes bad branches early
- **Search strategy independence**: BFS, DFS, beam search, or MCTS can all drive the tree traversal
- **Verifiable tasks**: most effective when correctness can be checked (math, code, games)

---

## 2. Intuition

One-line analogy: ToT is to chain-of-thought what a chess engine's minimax tree is to a player who only looks one move ahead.

Mental model: imagine planning a road trip. A greedy planner picks the next turn that looks best locally and commits. A ToT planner sketches three possible routes from the current city, scores each on distance and traffic, keeps the two most promising, extends those two to the next city with three options each, scores again, and converges on the globally best path — even if the optimal first turn looked slightly worse at first glance.

Why it matters: LLMs generating plans greedily often get stuck in locally consistent but globally suboptimal sequences. ToT allows the model to explore, evaluate, and backtrack — recovering from early mistakes before they propagate into irreversible actions.

Key insight: the LLM is used in two distinct roles — as a generator (propose thoughts) and as an evaluator (score thoughts). Separating these two roles is what makes structured search possible.

---

## 3. Core Principles

**Thought decomposition**: a "thought" is a coherent intermediate step — a candidate next action, a reasoning fragment, or a partial plan. Thoughts must be granular enough that multiple candidates are plausible but coarse enough that each makes meaningful progress.

**Generator**: given the current state (problem description + path taken so far), produce k candidate thoughts. Two prompting strategies:
- *Sample independently*: call LLM k times with temperature > 0 to get diverse candidates
- *Propose in bulk*: single call with instruction "propose k distinct next actions" — cheaper but candidates may cluster

**Evaluator**: given the current state and a candidate thought, produce a value estimate. Two approaches:
- *Scoring*: LLM outputs 1–10 or a confidence probability; allows soft ranking
- *Vote*: LLM samples multiple evaluations and takes the majority verdict ("sure / maybe / impossible")

**Search strategy**: determines which nodes to expand next (BFS, DFS, beam, MCTS — see Section 4).

**Termination**: a node is terminal when the agent reaches a goal state (task solved), a depth limit is exceeded, or the evaluator marks a node as impossible.

**State representation**: each node stores (problem, path_so_far, depth, value_estimate). The path encodes the full history so the generator and evaluator have complete context.

---

## 4. Types / Architectures / Strategies

### 4.1 Breadth-First Search (BFS)

Expand all nodes at depth k before moving to depth k+1. Maintains a frontier queue. Guaranteed to find the shallowest solution. Cost: branching_factor^depth LLM calls for generation alone (exponential). Practical only for small trees (branching factor 2–3, depth 2–3).

### 4.2 Depth-First Search (DFS)

Commit to the highest-scoring child at each step, recurse until terminal or depth limit, backtrack if the node is evaluated as impossible or a dead end. Much cheaper than BFS — at most depth * branching_factor nodes explored in the best case. Risk: may miss globally better paths discovered via other branches.

### 4.3 Beam Search

BFS with a beam width B: at each level, expand all nodes in the current beam, generate k children per node, score all k*B candidates, keep only the top B for the next level. Cost: B * k * depth LLM calls. Beam_width=3 with branching factor=3 and depth=3 yields ~27 candidate evaluations — tractable. This is the most common practical strategy.

### 4.4 Monte Carlo Tree Search (MCTS)

Four phases per iteration:
1. **Selection**: traverse the tree from root, choosing children by UCB1 score = value + C * sqrt(ln(N_parent) / N_node), balancing exploitation (high value) and exploration (low visit count).
2. **Expansion**: at an unexplored leaf, generate k candidate thoughts and add them as children.
3. **Simulation (rollout)**: from the new node, run a fast (greedy or sampled) rollout to a terminal state and compute a reward signal.
4. **Backpropagation**: update value and visit-count statistics for every node on the path from root to the simulated leaf.

MCTS is sample-efficient for deep trees and naturally balances exploration/exploitation. Used in AlphaCode 2 for code generation search and in reasoning models (o1-class) implicitly.

### 4.5 Original ToT Paper Tasks

- **24-game**: given four numbers (e.g., 4, 9, 10, 13), combine with +, -, *, / to produce 24. At each step the thought is a partial arithmetic expression; evaluation checks if remaining numbers can still reach 24. GPT-4 with BFS ToT solved 74% vs 4% for chain-of-thought.
- **Creative writing**: given four random sentences, generate a coherent short story. Thoughts are paragraph drafts; evaluation is an LLM-based coherence score. ToT + beam search produced passages rated higher by human evaluators.

---

## 5. Architecture Diagrams

### BFS — Level-by-Level Expansion

```
                        [Root: Problem]
                              |
          +-----------+-------+-----------+
          |           |                   |
       [A1]         [A2]               [A3]     <- depth 1, score all 3
        val=7        val=4               val=9
          |           |                   |
      +---+---+   +---+---+          +---+---+
      |       |   |       |          |       |
    [A1a] [A1b][A2a] [A2b]        [A3a] [A3b]  <- depth 2, score all 6
    val=5  val=8 val=3 val=6      val=9  val=7

    Total calls: 3 (gen) + 6 (eval) at depth 1
               + 6 (gen) + 6*2 (eval) at depth 2
```

### Beam Search (beam_width=2)

```
                        [Root]
                           |
         +----------+------+----------+
         |          |                 |
       [A1]       [A2]             [A3]       <- generate 3, score all 3
       val=7      val=4            val=9
                                              <- KEEP TOP 2: A3(9), A1(7)
         +----+                +----+
         |    |                |    |
      [A3a] [A3b]           [A1a] [A1b]      <- generate 2 from each kept
      val=8  val=6           val=5  val=8
                                              <- KEEP TOP 2: A3a(8), A1b(8)
```

### DFS with Backtracking

```
                [Root]
                  |
               [A1] val=7
                  |
               [A1a] val=3  <- evaluator: "impossible"
                  |
              BACKTRACK
                  |
               [A1b] val=8
                  |
               [A1b-i] val=9  <- terminal, GOAL REACHED
```

### MCTS — Four Phases

```
SELECTION                EXPANSION              SIMULATION           BACKPROP
                                                                     
[Root N=10 V=6.2]        [Root]                 [Root]              [Root V=6.5]
   |                        |                      |                    |
[A1 N=6 V=7.1]           [A1]                   [A1]                [A1 V=7.2]
   |                        |                      |                    |
[A1a N=2 V=5.0]  -->  [A1a][A1b*new]  -->  rollout->reward=8  --> [A1a V=5.0]
                                                                   [A1b V=8.0]

UCB1(A1a) = 5.0 + C*sqrt(ln(6)/2) = selected for expansion
```

### Cost Comparison

```
Strategy      | Branching | Depth | LLM Calls (approx)
--------------|-----------|-------|--------------------
BFS           |     4     |   3   | 4 + 16 + 64 = 84
DFS           |     4     |   3   | up to 12 (best) / 84 (worst)
Beam (B=3)    |     4     |   3   | 3*4=12 generate + 12 score = ~24
MCTS (I=20)   |     4     |   3   | 20 iterations * ~3 calls = ~60
```

---

## 6. How It Works — Detailed Mechanics

```python
from __future__ import annotations

import heapq
import os
from dataclasses import dataclass, field
from typing import Callable

from openai import OpenAI

client = OpenAI(api_key=os.environ["OPENAI_API_KEY"])

# ---------------------------------------------------------------------------
# Data structures
# ---------------------------------------------------------------------------

@dataclass(order=True)
class ThoughtNode:
    """A node in the Tree of Thoughts."""
    # negative value for max-heap via heapq (min-heap by default)
    neg_value: float
    depth: int = field(compare=False)
    thought: str = field(compare=False)
    path: list[str] = field(compare=False, default_factory=list)
    children: list["ThoughtNode"] = field(compare=False, default_factory=list)

    @property
    def value(self) -> float:
        return -self.neg_value

    @classmethod
    def root(cls, problem: str) -> "ThoughtNode":
        return cls(neg_value=0.0, depth=0, thought=problem, path=[])


# ---------------------------------------------------------------------------
# Generator: produce k candidate next thoughts
# ---------------------------------------------------------------------------

GENERATOR_SYSTEM = """You are a planning assistant.
Given a problem and the steps taken so far, propose {k} distinct, concrete next actions.
Output exactly {k} actions, one per line, numbered 1. 2. 3. etc.
Be specific. Each action must be meaningfully different."""

def generate_thoughts(
    problem: str,
    path: list[str],
    k: int = 3,
    temperature: float = 0.8,
) -> list[str]:
    """Call LLM to generate k candidate next actions."""
    history = "\n".join(f"Step {i+1}: {s}" for i, s in enumerate(path))
    user_msg = (
        f"Problem: {problem}\n\n"
        f"Steps taken so far:\n{history if history else '(none)'}\n\n"
        f"Propose {k} distinct next actions to make progress."
    )
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": GENERATOR_SYSTEM.format(k=k)},
            {"role": "user", "content": user_msg},
        ],
        temperature=temperature,
        max_tokens=512,
    )
    raw = response.choices[0].message.content or ""
    lines = [ln.strip() for ln in raw.splitlines() if ln.strip()]
    # strip leading "1. " "2. " numbering
    thoughts: list[str] = []
    for ln in lines:
        if ln and ln[0].isdigit() and ". " in ln:
            thoughts.append(ln.split(". ", 1)[1])
        elif ln:
            thoughts.append(ln)
    return thoughts[:k]


# ---------------------------------------------------------------------------
# Evaluator: score a candidate thought 1–10
# ---------------------------------------------------------------------------

EVALUATOR_SYSTEM = """You are a critical planning evaluator.
Given a problem, the path taken so far, and a proposed next action, rate the action
on a scale of 1 to 10 (10 = excellent, 1 = terrible/impossible).
Respond with ONLY a single integer between 1 and 10."""

def evaluate_thought(
    problem: str,
    path: list[str],
    candidate: str,
) -> float:
    """Return a value estimate in [1, 10] for the candidate action."""
    history = "\n".join(f"Step {i+1}: {s}" for i, s in enumerate(path))
    user_msg = (
        f"Problem: {problem}\n\n"
        f"Steps taken so far:\n{history if history else '(none)'}\n\n"
        f"Proposed next action: {candidate}\n\n"
        f"Rate this action 1-10."
    )
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": EVALUATOR_SYSTEM},
            {"role": "user", "content": user_msg},
        ],
        temperature=0.0,
        max_tokens=8,
    )
    raw = (response.choices[0].message.content or "").strip()
    try:
        score = float(raw.split()[0])
        return max(1.0, min(10.0, score))
    except (ValueError, IndexError):
        return 5.0  # neutral fallback


# ---------------------------------------------------------------------------
# Goal checker
# ---------------------------------------------------------------------------

GOAL_SYSTEM = """You are a task completion checker.
Given a problem and the steps taken so far, respond with ONLY "YES" if the problem
is fully solved by these steps, or "NO" if more work is needed."""

def is_goal(problem: str, path: list[str]) -> bool:
    history = "\n".join(f"Step {i+1}: {s}" for i, s in enumerate(path))
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[
            {"role": "system", "content": GOAL_SYSTEM},
            {
                "role": "user",
                "content": f"Problem: {problem}\n\nSteps:\n{history}",
            },
        ],
        temperature=0.0,
        max_tokens=4,
    )
    return (response.choices[0].message.content or "").strip().upper().startswith("YES")


# ---------------------------------------------------------------------------
# Beam search over the tree
# ---------------------------------------------------------------------------

def tot_beam_search(
    problem: str,
    max_depth: int = 3,
    beam_width: int = 3,
    branching_factor: int = 3,
    pruning_threshold: float = 4.0,
) -> tuple[list[str], float]:
    """
    Tree of Thoughts with beam search.

    Returns the best path found and its value.

    LLM call budget: beam_width * branching_factor * max_depth  (generate)
                   + beam_width * branching_factor * max_depth  (evaluate)
    With defaults: 3 * 3 * 3 * 2 = 54 calls maximum.
    """
    # beam holds ThoughtNode objects; start with root
    root = ThoughtNode.root(problem)
    beam: list[ThoughtNode] = [root]

    best_path: list[str] = []
    best_value: float = -1.0

    for depth in range(1, max_depth + 1):
        candidates: list[ThoughtNode] = []

        for node in beam:
            # Early exit: already at goal
            if depth > 1 and is_goal(problem, node.path):
                if node.value > best_value:
                    best_value = node.value
                    best_path = node.path[:]
                continue

            # Generate branching_factor thoughts from this node
            thoughts = generate_thoughts(
                problem=problem,
                path=node.path,
                k=branching_factor,
            )

            for thought in thoughts:
                score = evaluate_thought(
                    problem=problem,
                    path=node.path,
                    candidate=thought,
                )
                # Prune obviously bad thoughts immediately
                if score < pruning_threshold:
                    continue
                new_path = node.path + [thought]
                child = ThoughtNode(
                    neg_value=-score,
                    depth=depth,
                    thought=thought,
                    path=new_path,
                )
                candidates.append(child)

                # Track best solution seen so far
                if score > best_value:
                    best_value = score
                    best_path = new_path

        if not candidates:
            break  # no viable branches remain

        # Keep top beam_width candidates (heapq gives smallest neg_value = largest value)
        beam = heapq.nsmallest(beam_width, candidates)

    # Final goal check on the surviving beam
    for node in beam:
        if is_goal(problem, node.path):
            if node.value > best_value:
                best_value = node.value
                best_path = node.path[:]

    return best_path, best_value


# ---------------------------------------------------------------------------
# Example usage
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    problem = (
        "Design a Python microservice that exposes a REST endpoint /summarize "
        "which accepts a JSON body {text: str} and returns {summary: str} "
        "using an LLM. The service must handle errors gracefully and log requests."
    )

    plan, score = tot_beam_search(
        problem=problem,
        max_depth=3,
        beam_width=3,
        branching_factor=3,
        pruning_threshold=4.0,
    )

    print(f"Best plan (score={score:.1f}):")
    for i, step in enumerate(plan, 1):
        print(f"  {i}. {step}")
```

### Cost accounting in practice

```
beam_width=3, branching_factor=3, max_depth=3

Depth 1: 3 nodes in beam * 3 thoughts = 9 generate calls
         9 thoughts * 1 eval call     = 9 evaluate calls
         Kept: top 3

Depth 2: 3 * 3 = 9 generate + 9 evaluate calls
         Kept: top 3

Depth 3: 3 * 3 = 9 generate + 9 evaluate calls
         Kept: top 3 (final beam)

Total: 54 LLM calls  (27 generate + 27 evaluate)
At $0.005/1K tokens avg: ~$0.05–$0.15 per planning run
```

---

## 7. Real-World Examples

**AlphaCode 2 (DeepMind, 2023)**: competitive programming. Generates hundreds of candidate programs, scores each with a filtering model on test cases, re-ranks top candidates. Functionally equivalent to ToT with a domain verifier (test execution) as the value function. Reached 85th percentile on Codeforces.

**OpenAI o1/o3 (2024–2025)**: these models implicitly implement test-time compute scaling via a ToT-like internal search over reasoning chains. The model generates multiple chain-of-thought drafts, evaluates them with a process reward model (PRM), and surfaces the best. The 2048-token "thinking" budget gates how deep the tree runs.

**SWE-bench agents**: Agentless, SWE-agent, and similar systems that score above 40% on SWE-bench use iterative patch generation + test-execution feedback — effectively DFS with backtracking when tests fail. The value function is binary (tests pass / fail) rather than LLM-scored.

**ARC-AGI benchmark**: ToT with BFS achieves substantially higher scores than greedy decoding on abstract reasoning tasks, because the correct transformation must be identified before any application — requiring lookahead.

**Mathematical proof assistants**: Lean + LLM tactic generation uses beam search over proof trees, with the Lean type checker as the evaluator. Each tactic is a "thought"; type checking is deterministic and cheap.

---

## 8. Tradeoffs

| Dimension | BFS | DFS | Beam Search | MCTS |
|---|---|---|---|---|
| Optimality guarantee | Yes (shallowest) | No | No (approximate) | Asymptotic (with enough iterations) |
| LLM call count (b=4, d=3) | 84 | 12–84 | ~24 (B=3) | ~60 (20 iter) |
| Memory usage | O(b^d) nodes | O(d) stack | O(B) nodes | O(nodes explored) |
| Handles deep trees | Poor | Good | Good | Best |
| Exploration/exploitation | Pure exploration | Pure exploitation | Tunable via B | UCB1-balanced |
| Implementation complexity | Low | Low | Low-Medium | High |
| Backtracking | Natural | Explicit | None (prune) | Via backprop |
| Works without value fn | No | No | No | Partially (random rollout) |
| Best for | Short tasks, verifiable | Deep tasks, cheap eval | Most practical cases | Long-horizon, games |

---

## 9. When to Use / When NOT to Use

### When to Use ToT

- **Mathematical problem solving**: 24-game, competition math (AMC, AIME), symbolic integration — correctness is verifiable and branching factors are manageable.
- **Code generation and debugging**: the value function is test execution (binary, cheap, deterministic). Beam search over k candidate patches, keep those that pass more unit tests.
- **Strategic planning with reversible steps**: if each action can be undone (file editing with version control, database migrations with rollback), backtracking costs are low.
- **Game playing**: Chess, Go, text-based games where tree search is the native paradigm.
- **Novel task routing**: when the optimal sequence of tool calls for an unprecedented query is genuinely unknown, ToT lets the agent discover it rather than guess.

### When NOT to Use ToT

- **Routine RAG Q&A**: single-turn retrieval + generation. The answer space is dominated by information access, not planning search. Chain-of-thought is sufficient.
- **Email drafting, summarization, classification**: no meaningful branching; any reasonable next token is acceptable. Beam search on tokens is already built into the decoder.
- **Conversational agents**: users expect low-latency responses (< 2 s). Even beam_width=2 with depth=2 adds 8 LLM calls before the first response.
- **Cost-sensitive applications**: at $0.01 per call, 54 calls = $0.54 per query. For a 1M query/day system that is $540K/day.
- **Tasks without a useful value function**: if the evaluator is just restating the generator's output, scoring noise dominates and beam selection becomes random.
- **Long-horizon open-ended tasks**: trees with depth > 5 and branching > 3 become intractable even with beam search. Use hierarchical planning instead (decompose into sub-problems, apply ToT to each).

---

## 10. Common Pitfalls

### Pitfall 1: Unbounded tree expansion — broken version

```python
# BROKEN: no cost controls, will exhaust rate limits and budget

def naive_tot(problem: str, depth: int = 5, branching: int = 5) -> list[str]:
    """BFS with depth=5, branching=5 => 5^5 = 3125 LLM calls."""
    def expand(path: list[str], d: int) -> list[list[str]]:
        if d == 0:
            return [path]
        # 5 thoughts * 5 evaluate calls per node, recursed 5 levels deep
        thoughts = generate_thoughts(problem, path, k=branching)
        results = []
        for t in thoughts:
            score = evaluate_thought(problem, path, t)
            results.extend(expand(path + [t], d - 1))  # no pruning!
        return results

    all_paths = expand([], depth)
    return max(all_paths, key=lambda p: len(p))  # no value tracking either
```

Problems:
- 5^5 = 3125 generate calls + 3125 evaluate calls = 6250 LLM calls per planning run
- No pruning threshold — expands dead branches identically to promising ones
- No beam width — memory grows exponentially
- Value tracking discarded; winner chosen by path length (meaningless)

### Fixed version

```python
# FIXED: beam search with pruning threshold and call budget

def safe_tot(
    problem: str,
    max_depth: int = 3,        # hard limit: never go deeper than 3
    beam_width: int = 3,       # keep at most 3 nodes at each level
    branching_factor: int = 3, # generate at most 3 thoughts per node
    pruning_threshold: float = 5.0,  # discard anything scored below 5/10
    max_calls: int = 100,      # hard budget: abort if exceeded
) -> list[str]:
    """Safe ToT: bounded calls, pruned branches, tracked value."""
    call_count = 0
    beam: list[ThoughtNode] = [ThoughtNode.root(problem)]
    best_path: list[str] = []
    best_value: float = -1.0

    for depth in range(1, max_depth + 1):
        if call_count >= max_calls:
            break  # hard budget stop

        candidates: list[ThoughtNode] = []
        for node in beam:
            if call_count >= max_calls:
                break

            # generate
            thoughts = generate_thoughts(problem, node.path, k=branching_factor)
            call_count += 1

            for thought in thoughts:
                if call_count >= max_calls:
                    break
                score = evaluate_thought(problem, node.path, thought)
                call_count += 1

                if score < pruning_threshold:  # prune immediately
                    continue

                child = ThoughtNode(
                    neg_value=-score,
                    depth=depth,
                    thought=thought,
                    path=node.path + [thought],
                )
                candidates.append(child)
                if score > best_value:
                    best_value = score
                    best_path = child.path[:]

        if not candidates:
            break

        beam = heapq.nsmallest(beam_width, candidates)  # keep top beam_width

    print(f"ToT used {call_count} LLM calls (budget: {max_calls})")
    return best_path
```

Fix summary:
- `max_depth=3` caps recursion (3^3 = 27 generate + 27 evaluate = 54 max)
- `beam_width=3` caps memory and branching at each level
- `pruning_threshold=5.0` discards the bottom half of candidates immediately
- `max_calls=100` is a hard circuit breaker — safe for production
- `best_value` tracked throughout; winner is highest-scored node, not longest path

### Pitfall 2: Evaluator and generator use the same system prompt

If the same prompt drives both roles, the evaluator tends to confirm whatever the generator produced (sycophancy). Fix: use separate system prompts with explicitly adversarial framing for the evaluator ("critically assess flaws in this plan step").

### Pitfall 3: Thoughts that are too granular

Generating thoughts at the level of individual words or sentences produces a tree that is too wide and shallow to be useful — the model is doing token-level beam search, which the decoder already does natively. Thoughts should correspond to complete actions ("write a FastAPI endpoint", "add error handling middleware") not sub-word fragments.

### Pitfall 4: Ignoring path context in the evaluator

Evaluating a thought in isolation (without the path so far) produces scores that are locally sensible but globally incoherent — a step rated 9/10 in isolation may contradict a prior step. Always include the full `path` in the evaluator prompt.

---

## 11. Technologies & Tools

| Tool / Library | Role | Notes |
|---|---|---|
| OpenAI GPT-4o / GPT-4 Turbo | Generator + evaluator LLM | Strong instruction following; `temperature=0` for eval |
| Anthropic Claude 3.5 Sonnet | Alternative LLM | Comparable quality; supports long context for deep paths |
| LangGraph | Graph-based agent orchestration | Native support for branching and backtracking via conditional edges |
| LlamaIndex | RAG + agent framework | `TreeSummarize` uses ToT-like aggregation; custom agent steps |
| Guidance (Microsoft) | Constrained generation | Forces structured thought proposals (JSON, numbered lists) |
| DSPy | Programmatic LLM optimization | `ChainOfThought` + `Retry` modules approximate DFS with backtracking |
| Ray | Parallel LLM calls | Distribute `generate_thoughts` and `evaluate_thought` across workers |
| vLLM | High-throughput inference | Critical for BFS where many parallel calls are made simultaneously |
| lm-eval-harness | Benchmarking | Measure ToT vs CoT on ARC, GSM8K, MATH, HumanEval |
| Process Reward Models (PRM) | Fast value function | Smaller trained model replaces LLM evaluator; 10-100x cheaper |

---

## 12. Interview Questions with Answers

**What problem does Tree of Thoughts solve that chain-of-thought does not?**
Chain-of-thought generates a single linear reasoning path greedily — once a reasoning step is produced, the model cannot revisit it. ToT solves the inability to explore alternatives and backtrack by maintaining a tree of candidate thoughts and using search to navigate it.

**How does the original ToT paper demonstrate the benefit quantitatively?**
On the 24-game benchmark (combine 4 numbers to reach 24 using arithmetic), GPT-4 with chain-of-thought solved 4% of problems, while GPT-4 with ToT + BFS solved 74%. The key difference was the ability to backtrack when intermediate arithmetic was evaluated as infeasible.

**What is the role of the value function in ToT and what are the two main implementation strategies?**
The value function scores candidate thoughts to guide the search. The two strategies are: (1) scoring — the LLM outputs a numeric score (e.g., 1–10) allowing soft ranking across candidates; (2) voting — the LLM is sampled multiple times and the majority verdict (sure/maybe/impossible) is used. Scoring suits continuous search strategies (beam, MCTS); voting suits binary pruning (DFS).

**Explain BFS vs DFS in the context of ToT and when you would choose each.**
BFS expands all nodes at depth k before proceeding to depth k+1, guaranteeing the shallowest solution but costing branching_factor^depth LLM calls. DFS commits to one branch and backtracks on failure, costing at most depth * branching_factor calls in the best case. Choose BFS for shallow trees where global optimality matters; choose DFS for deep trees where early commitment is acceptable and cost is constrained.

**What is beam search and why is it the most practical ToT strategy?**
Beam search is BFS with a fixed beam width B: at each level, generate k children per node, score all B*k candidates, keep only the top B. Cost is O(B * k * depth), which is linear in depth rather than exponential. With B=3, k=3, depth=3, this yields ~54 LLM calls — tractable in production. Pure BFS with branching 4, depth 3 costs 84 calls even before pruning.

**How does MCTS differ from beam search for agent planning?**
MCTS uses UCB1 to balance exploration and exploitation across iterations — nodes with high value but low visit count are preferentially expanded. Beam search is purely greedy at each level and does not revisit discarded branches. MCTS amortizes evaluation across many rollouts and is more sample-efficient for deep trees, but is harder to implement and reason about. Beam search is simpler and faster for shallow trees.

**What is UCB1 and how does it drive MCTS node selection?**
UCB1 = V(node) + C * sqrt(ln(N_parent) / N_node), where V is the node's average value, N_node is its visit count, N_parent is the parent's visit count, and C is an exploration constant (commonly sqrt(2)). Nodes with high V are exploited; nodes with low N_node are explored. This formula ensures every node is eventually visited.

**How do process reward models (PRMs) relate to ToT?**
PRMs are small models trained to score intermediate reasoning steps rather than only final answers. They replace the LLM evaluator in ToT with a dedicated, fast, cheap model — reducing evaluation cost from one full LLM call to one forward pass through a small classifier. OpenAI's o1 training used PRMs to provide step-level reward signals during RLHF, which is conceptually the offline training analogue of online ToT evaluation.

**What is the cost of naive BFS ToT with branching factor 4 and depth 3, and how does beam search reduce it?**
Naive BFS: 4 + 16 + 64 = 84 nodes, each requiring a generate call and an evaluate call = 168 LLM calls minimum. With beam_width=3: 3*4=12 candidates at depth 1, keep 3; 3*4=12 at depth 2, keep 3; 3*4=12 at depth 3 = 36 candidates total, ~72 LLM calls. Pruning drops this further to ~54 calls in practice — a 3x reduction.

**When is ToT not worth the extra LLM calls?**
ToT is not worth it when: (1) the task has a single obvious correct next step (RAG Q&A, summarization); (2) latency requirements preclude multiple sequential LLM calls (< 2 s response expected); (3) no meaningful value function exists to differentiate candidates; (4) per-query economics are too tight (high-volume consumer applications at low margins).

**How does ToT relate to the "test-time compute scaling" narrative for models like o1?**
Test-time compute scaling refers to spending more inference compute to improve answer quality. ToT is one mechanism: by generating and evaluating multiple reasoning branches at inference time, the model effectively runs longer before producing an output. o1-class models implement this internally via a search over chain-of-thought trajectories scored by a PRM, controlled by a "thinking token budget" that gates tree depth.

**What makes a good "thought" granularity in agent ToT?**
A thought should represent a complete, coherent agent action — one that advances the plan meaningfully and is independently evaluable. Too fine-grained (individual sentences) and the tree is too wide; the decoder's built-in beam search already handles sub-word diversity. Too coarse-grained (multi-step sub-plans) and the evaluator cannot distinguish good from bad candidates accurately. In practice, one thought = one tool call or one implementation step.

**How do you prevent the evaluator from confirming whatever the generator produced (sycophancy)?**
Use separate, explicitly adversarial system prompts for the evaluator ("identify flaws and risks in this proposed action"). Ask the evaluator to reason about failure modes before assigning a score. Use a different model (or different temperature) for evaluation than generation. In high-stakes applications, use a trained discriminator or domain verifier (test execution, type checker) instead of an LLM evaluator altogether.

**How would you implement ToT for a code debugging agent?**
Generator: given a failing test + current code, propose 3 candidate patches. Evaluator: run the unit test suite against each patch; value = fraction of tests passing (0.0–1.0). This replaces LLM scoring with a deterministic, cheap verifier — far more reliable. Use DFS: apply the highest-scoring patch, run tests, backtrack if no improvement. Depth limit = 5 attempts. This is essentially how SWE-bench top performers operate.

**What is the relationship between ToT and classical AI search algorithms?**
ToT is a direct application of classical heuristic search (A*, BFS, DFS, beam search, MCTS) to the space of LLM-generated reasoning steps. The only LLM-specific adaptation is: (1) the branching factor is generated by sampling rather than enumerated from a fixed action space; (2) the heuristic function is an LLM or trained model rather than a hand-coded function. The underlying search theory (completeness, optimality, complexity) is identical.

**How do you decide between beam search and MCTS for a new agent task?**
Use beam search when: depth is shallow (< 5), latency matters (serial expansion is faster), the task is relatively straightforward, and engineering simplicity is a priority. Use MCTS when: depth is large (5+), exploration/exploitation balance is critical (the optimal first step is not obvious), the rollout is cheap and informative, or you are dealing with game-like tasks where future rewards are highly uncertain. In practice, start with beam search and escalate to MCTS only if quality is insufficient.

---

## 13. Best Practices

**Start with beam search, not BFS.** BFS is theoretically clean but practically expensive. Beam_width=3 with branching_factor=3 and depth=3 gives 54 calls — a sensible default. Adjust beam_width up if quality is insufficient; bring branching_factor down first if cost is the constraint.

**Always set a hard call budget.** Implement a `max_calls` circuit breaker. Rate limits, network failures, and recursive bugs can cause unbounded expansion. Log every LLM call and terminate gracefully when the budget is exhausted, returning the best path found so far.

**Use separate prompts for generator and evaluator.** The evaluator must be adversarial or at least neutral — not a restatement of the generator's framing. Include explicit instruction to "identify risks and failure modes" before assigning a score.

**Prefer domain verifiers over LLM evaluators when available.** Unit test execution, type checkers, and mathematical verifiers (SymPy, Lean) are deterministic, cheap, and reliable. LLM-based evaluators are noisy; even GPT-4 has inter-call variance of 1–2 points on a 10-point scale.

**Include the full path in every generator and evaluator call.** Without path context, generated thoughts ignore prior steps, and evaluated scores are incoherent with the actual plan state. Pass the complete history of actions, not just the most recent one.

**Tune the pruning threshold empirically.** A threshold of 5/10 (discard the bottom half) is a reasonable starting point. If the evaluator is conservative (rarely scores above 7), lower the threshold to 4. If paths converge too quickly (beam collapses to identical nodes), lower the threshold and increase beam width.

**Parallelize within a depth level.** All nodes at the same beam level are independent — their generate and evaluate calls can run concurrently. Use `asyncio.gather` or `concurrent.futures.ThreadPoolExecutor` to reduce wall-clock time from O(calls) to O(max_calls_per_level).

**Log tree structure for debugging.** Persist the full tree (each node's thought, path, depth, value) to a structured log. When ToT underperforms, inspecting discarded branches reveals whether the evaluator is miscalibrated or the generator is not diverse enough.

**Apply ToT selectively, not universally.** Use a routing layer: classify incoming tasks as "routine" (use direct chain-of-thought) vs "complex" (use ToT). Classification can be a simple LLM call or a fine-tuned classifier on task features (novelty, number of constraints, expected solution length).

---

## 14. Case Study

### Automated Code Review Agent Using ToT

**Problem Statement**: build an agent that reviews a submitted pull request, identifies the top 3 issues, and generates specific, actionable fix suggestions. A greedy agent (generate one review comment at a time) produces redundant or inconsistently prioritized feedback. The goal is to find the globally best set of 3 issue-fix pairs, where "best" means highest severity, highest fix confidence, and minimal overlap.

**Architecture Overview**

```
Pull Request Diff + Context
          |
          v
   [ToT Planning Layer]
          |
     +---------+
     | Beam    |  beam_width=3, branching_factor=4, depth=3
     | Search  |
     +---------+
          |
   Depth 1: Generate 4 candidate "issue focus areas"
            (security, performance, correctness, style)
            Evaluate: severity score 1-10 via LLM
            Keep top 3 (e.g., security=9, correctness=8, perf=6)
          |
   Depth 2: For each kept area, generate 4 candidate specific issues
            (e.g., security: SQL injection, missing auth, insecure deserialization, ...)
            Evaluate: exploitability + confidence score
            Keep top 3 across all 12 candidates
          |
   Depth 3: For each kept issue, generate 4 candidate fix suggestions
            Evaluate: fix correctness via static analysis stub + LLM
            Keep top 3 across all 12 candidates
          |
   [Output]: top 3 (issue, fix) pairs with scores

ASCII view:

[Diff]
  |
  +--[Security]--+--[Correctness]--+--[Performance]--+--[Style]  <- depth 1, score all 4
       val=9          val=8             val=6            val=3
                                                        PRUNED (< 5)
  |
  +--[SQL inj]--[No auth]--[Bad deser]  <- depth 2 from Security (val=9)
       val=8      val=9       val=5
  |
  +--[CorrectBug1]--[CorrectBug2]--...  <- depth 2 from Correctness
  |
  ... beam keeps top 3 across all depth-2 candidates ...
  |
  +--[Fix for No auth: add @PreAuthorize]  <- depth 3: fix suggestion
       val=9, verified by Spring Security static check
```

**Key Design Decisions**

1. Thought granularity: depth 1 = issue category (4 options), depth 2 = specific bug within category, depth 3 = concrete fix. Each level is evaluable independently.

2. Hybrid evaluator: at depth 3, a static analysis tool (SpotBugs, SonarQube API) is called first; the LLM evaluator is called only if static analysis returns no signal. This reduces LLM calls at the most expensive depth by ~40%.

3. Deduplication at beam selection: before keeping top B nodes, check cosine similarity of thought embeddings. If two nodes score identically but embed similarly (> 0.9 cosine), keep only one to ensure diverse feedback.

4. Call budget: max_calls=60. Typical run: 4+4+4=12 generate, 4+12+12=28 evaluate, 3 dedup checks = ~43 calls. Budget provides ~40% headroom for retries.

**Results vs Greedy Baseline**

Evaluated on 200 real GitHub pull requests with known bug annotations:

```
Metric                        | Greedy CoT | ToT Beam (B=3, d=3) | Delta
------------------------------|------------|----------------------|------
Issues found (recall@3)       |    61%     |         79%         |  +18%
Redundant suggestions (%)     |    34%     |          9%         |  -25%
Fix correctness (human eval)  |    52%     |         71%         |  +19%
Avg LLM calls per review      |     3      |         43          |  +40
Avg latency (parallel exec)   |    1.2 s   |        4.1 s        |  +2.9s
Cost per review ($0.005/call) |   $0.015   |        $0.215       |  +$0.20
```

ToT improves issue recall by 18 percentage points and fix correctness by 19 points at 14x higher LLM call count. For a code review product where fix quality is the value driver and $0.20/review is within budget, this tradeoff is justified. For a high-volume automated linting tool processing 10K PRs/day ($2K/day cost delta), the greedy baseline with targeted ToT on high-complexity diffs is the right architecture.

**Implementation Note on Parallelism**

At depth 2, the 3 surviving depth-1 nodes are independent — their 4*3=12 candidate generation calls can run concurrently with `asyncio.gather`. This reduces wall-clock latency from 4.1 s to ~1.8 s at the cost of higher peak API concurrency (12 simultaneous requests). Most production LLM APIs enforce per-minute token limits that make this feasible without rate-limit errors.
