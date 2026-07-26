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

**Evaluator**: given the current state and a candidate thought, produce a value estimate. The ToT paper names two approaches:
- *Value each state independently*: the LLM scores or classifies one state at a time — a 1–10 scalar, a confidence probability, or the paper's "sure / maybe / impossible" classification for the 24-game; allows soft ranking
- *Vote across states*: all sibling candidates go into a single vote prompt and the LLM picks the most promising one; sampled repeatedly, this is a step-wise self-consistency vote rather than a per-state score

**Search strategy**: determines which nodes to expand next (BFS, DFS, beam, MCTS — see Section 4).

**Termination**: a node is terminal when the agent reaches a goal state (task solved), a depth limit is exceeded, or the evaluator marks a node as impossible.

**State representation**: each node stores (problem, path_so_far, depth, value_estimate). The path encodes the full history so the generator and evaluator have complete context.

---

## 4. Types / Architectures / Strategies

### 4.1 Breadth-First Search (BFS)

Expand all nodes at depth k before moving to depth k+1. Maintains a frontier queue. Guaranteed to find the shallowest solution. Cost: branching_factor^depth thought evaluations at the deepest level, plus one generate call per expanded node (exponential either way). Practical only for small trees (branching factor 2–3, depth 2–3).

### 4.2 Depth-First Search (DFS)

Commit to the highest-scoring child at each step, recurse until terminal or depth limit, backtrack if the node is evaluated as impossible or a dead end. Much cheaper than BFS — at most depth * branching_factor nodes explored in the best case. Risk: may miss globally better paths discovered via other branches.

### 4.3 Beam Search

BFS with a beam width B: at each level, expand all nodes in the current beam, generate k children per node, score all k*B candidates, keep only the top B for the next level. Cost: O(B * k * depth) candidate evaluations. Beam_width=3 with branching factor=3 and depth=3 yields 21 evaluations (the root level contributes only k=3) plus 7 generate calls — 28 LLM calls, tractable. This is the most common practical strategy.

**In plain terms.** "BFS pays a price that multiplies with every level you go down; beam search pays one that merely adds — because it throws away all but `B` nodes before descending."

That single swap, from `b^d` to `B x k x d`, is the entire reason beam search is the default in production and BFS is a paper result.

| Symbol | What it is |
|--------|------------|
| `b` (or `k`) | Branching factor — candidate thoughts generated per node |
| `d` | Depth — how many levels of lookahead |
| `B` | Beam width — how many nodes survive to the next level |
| `b^d` | BFS frontier size at depth `d`. Exponential: the level count is in the exponent |
| `B x k x d` | Beam candidate count (upper bound — the root level contributes only `k`). Linear in `d`, because `B` caps the frontier at every level |

**Walk one example.** The same tree shape, searched both ways:

```
              level 1    level 2    level 3    total nodes
  BFS b=4  :     4         16         64          84       <- 4^1 + 4^2 + 4^3
  BFS b=3  :     3          9         27          39
  Beam B=3,k=3:  3          9          9          21       <- root x 3, then 3 kept x 3

  BFS at b=4,d=5 would be 4+16+64+256+1024 = 1,364 nodes.
  Beam B=3,k=3,d=5 stays at 3 + 9 x 4 = 39.
```

The pruning is the point, and it is also the risk: beam search discards `k x B - B` candidates per level without ever expanding them, so a thought that scores 4/10 now but leads to the only solution is gone permanently. BFS cannot make that mistake — it is complete — which is why the tradeoff table credits it with an optimality guarantee and beam search with only an approximate one. You are buying a linear cost curve with the possibility of missing the answer.

### 4.4 Monte Carlo Tree Search (MCTS)

Four phases per iteration:
1. **Selection**: traverse the tree from root, choosing children by UCB1 score = value + C * sqrt(ln(N_parent) / N_node), balancing exploitation (high value) and exploration (low visit count).
2. **Expansion**: at an unexplored leaf, generate k candidate thoughts and add them as children.
3. **Simulation (rollout)**: from the new node, run a fast (greedy or sampled) rollout to a terminal state and compute a reward signal.
4. **Backpropagation**: update value and visit-count statistics for every node on the path from root to the simulated leaf.

MCTS is sample-efficient for deep trees and naturally balances exploration/exploitation. It is the search behind AlphaZero, and AlphaZero-inspired variants drive LLM search in work such as HyperTree Proof Search (Lample et al., 2022) for neural theorem proving. Note that AlphaCode 2 is *not* an MCTS system — it is massive sampling plus filtering, clustering and reranking (see Section 7).

### 4.5 Original ToT Paper Tasks

- **24-game**: given four numbers (e.g., 4, 9, 10, 13), combine with +, -, *, / to produce 24. At each step the thought is a partial arithmetic expression; evaluation classifies the remaining numbers as sure/maybe/impossible for reaching 24. On GPT-4, ToT with BFS at breadth limit b=5 over 3 thought steps solved 74%, versus 4.0% for chain-of-thought and 7.3% for plain IO prompting (ToT at b=1 already reached 45%).
- **Creative writing**: given four random sentences, generate a coherent 4-paragraph passage ending in those sentences. Thoughts are plans then passages; evaluation is an LLM coherency score plus a vote. The paper used BFS with depth 2 and b=1 (not beam search); GPT-4 coherency averaged 7.56 for ToT against 6.93 for CoT and 6.19 for IO, and human raters preferred ToT in 41 of 100 passage pairs against 21 for CoT.
- **Mini crosswords**: 5x5 crosswords solved as a DFS with pruning and backtracking over 20 games. ToT reached 60% word-level success against 15.6% for CoT, and solved 20% of games outright (4 of 20) against 1% for CoT.

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

    Total calls: 1 gen (returns 3 thoughts) + 3 eval  at depth 1
               + 3 gen (returns 2 each)   + 6 eval  at depth 2
               = 4 generate + 9 evaluate = 13 LLM calls
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

**The idea behind it.** "Pick the node with the best score, plus a bonus for how badly you have neglected it — so a mediocre node you have barely tried can still outrank a good node you have already checked ten times."

UCB1 is a two-term sum, and reading it as two terms in tension is the whole trick: the first term is what you believe, the second is how little you know.

| Symbol | What it is |
|--------|------------|
| `value` | Average reward backpropagated through this node so far. The exploitation term |
| `N_parent` | How many times the parent has been visited. Grows as the search runs |
| `N_node` | How many times this specific child has been tried. Small = under-explored |
| `sqrt(ln(N_parent) / N_node)` | Uncertainty bonus. Shrinks as `N_node` grows; `ln` makes it grow slowly with total effort |
| `C` | Exploration weight. `1.41` (that is `sqrt(2)`) is the textbook default |

**Walk one example.** The diagram's numbers, with `C = 1.41` and `ln(6) = 1.7918`:

```
  node    value   N_node   sqrt(ln(6)/N_node)   C x bonus   UCB1
  ----   ------   ------   ------------------   ---------   ------
  A1a      5.0       2           0.9465           1.3346    6.3346
  A1a      5.0       1           1.3386           1.8874    6.8874   <- if less visited

  Halving the visit count lifts A1a's score by 6.8874 - 6.3346 = 0.5528
  without its value estimate changing at all.
```

**Why the `ln` and not a plain ratio.** The bonus uses `ln(N_parent)`, not `N_parent`, so as the search does more total work the exploration pressure grows very slowly — total visits have to multiply by `e` (about 2.72x) to add 1 to the numerator inside the square root. Drop `ln` and the bonus swamps the value term on long searches, degenerating MCTS into round-robin. Set `C = 0` and the opposite happens: the search becomes pure greedy DFS, locking onto whichever child happened to get a lucky first rollout. The tradeoff table calls this "UCB1-balanced"; the balance lives entirely in `C` and that logarithm.

### Cost Comparison

| Strategy | Branching | Depth | LLM Calls (approx) |
|---|---|---|---|
| BFS | 4 | 3 | 21 generate + 84 evaluate = 105 |
| DFS | 4 | 3 | 15 (best) / 105 (worst) |
| Beam (B=3) | 4 | 3 | 7 generate + 28 evaluate = 35 |
| MCTS (I=20) | 4 | 3 | 20 iterations * ~3 calls = ~60 |

**What the formula is telling you.** "Each strategy has a different shape of cost curve, and at `b=4, d=3` they happen to land within 7x of each other — which is exactly why this table is misleading if you read only this row."

Every number above comes from a different formula. Worth separating them, because they diverge violently as `d` grows. One `generate` call returns `b` thoughts, and each thought costs one `evaluate` call — so generate calls count *expanded nodes*, evaluate calls count *candidates*.

| Symbol | What it is |
|--------|------------|
| BFS eval `= b + b^2 + ... + b^d` | Every node at every level scored. `4 + 16 + 64 = 84` |
| BFS generate `= 1 + b + ... + b^(d-1)` | One call per expanded node. `1 + 4 + 16 = 21`. Total `105` |
| DFS best `= d + b x d` | One path down: `3` generate + `4 x 3 = 12` evaluate `= 15` |
| DFS worst | Backtracks through the whole tree — identical to BFS, `105` |
| Beam `= (1 + B(d-1))` gen `+ (k + Bk(d-1))` eval | `7` generate + `4 + 12 + 12 = 28` evaluate `= 35` |
| MCTS `= I x calls_per_iter` | `20 x 3 = 60`. Set by iteration budget, not by tree shape |

**Walk one example.** Hold `b = 4` and push `d` from 3 to 5:

```
  strategy        d = 3               d = 5
  ------------  ---------  ------------------------------
  BFS             105       341 gen + 1,364 eval = 1,705
  DFS (best)       15       5 gen + 20 eval      =    25
  Beam (B=3)       35       13 gen + 52 eval     =    65
  MCTS (I=20)      60       20 x 3               =    60      <- unchanged

  BFS grows 16x; beam grows under 2x; MCTS does not grow at all.
```

MCTS is the odd one out and that is its defining property: its cost is set by the iteration budget `I` you choose, not by `b` or `d` at all. Going deeper does not cost more calls, it just means each of the 20 iterations covers less of the tree — you trade coverage for depth rather than paying for it. That is precisely why the tradeoff table rates MCTS "Best" at handling deep trees while BFS rates "Poor", even though at `d = 3` BFS looks only modestly more expensive.

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
        model="gpt-5.6-terra",
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
        model="gpt-5.6-terra",
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
        model="gpt-5.6-terra",
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

    LLM call budget: one generate call per expanded node (each returns k thoughts)
                   + one evaluate call per generated thought.
    With defaults (beam 3, branching 3, depth 3):
      generate: 1 + 3 + 3            =  7 calls
      evaluate: 3 + 9 + 9            = 21 calls
                                       -------
                                        28 calls maximum.
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

Depth 1: 1 node in beam (the root) -> 1 generate call returning 3 thoughts
         3 thoughts * 1 eval call            = 3 evaluate calls
         Kept: top 3

Depth 2: 3 nodes -> 3 generate calls, 9 thoughts = 9 evaluate calls
         Kept: top 3

Depth 3: 3 nodes -> 3 generate calls, 9 thoughts = 9 evaluate calls
         Kept: top 3 (final beam)

Total: 28 LLM calls  (7 generate + 21 evaluate)
At gpt-5.6-terra list pricing ($2.50 / 1M input, $15 / 1M output) and
roughly 400 input + 150 output tokens per generate call, 400 input +
5 output per evaluate call: ~$0.05 per planning run (~$0.002 per call)
```

---

## 7. Real-World Examples

**AlphaCode 2 (Google DeepMind, 2023)**: competitive programming, built on fine-tuned Gemini Pro. Samples up to one million candidate programs per problem, executes them against the public tests to filter out roughly 95%, clusters the survivors by runtime behaviour, keeps the 10 largest clusters, and reranks with a fine-tuned scoring model to pick 10 submissions. It is a wide sample-and-rerank pipeline rather than a tree search, but shares ToT's core move: a domain verifier (test execution) as the value function. Solved 43% of problems on 12 Codeforces contests, an estimated 85th percentile among entrants.

**OpenAI reasoning models (o1 onward)**: these models spend extra test-time compute on internal "reasoning tokens" before answering (see [Reasoning Models](../reasoning_models/README.md)). OpenAI does not publish the internal mechanism — a tree search over drafts scored by a process reward model is a community hypothesis, not a documented architecture. What the API exposes is a `reasoning.effort` control and `max_output_tokens`, which bounds reasoning plus visible output together; OpenAI states reasoning token counts range from a few hundred to tens of thousands depending on the problem, with no fixed budget.

**SWE-bench agents**: iterative agents such as SWE-agent generate a patch, run the test suite, and revise on failure — effectively DFS with backtracking, where the value function is binary (tests pass / fail) rather than LLM-scored. Agentless is the deliberate counterexample: a fixed localize-repair-validate pipeline with sampling and reranking and no agent loop or backtracking at all, which shows how much of the gain comes from the verifier rather than from the search.

**Mathematical proof assistants**: LLM tactic generation searches proof trees with the proof assistant's kernel as the evaluator — each tactic is a "thought", and type checking is deterministic and cheap. HyperTree Proof Search (Lample et al., 2022) applies AlphaZero-style search to Lean and Metamath proofs; simpler systems use best-first or beam search over tactic candidates.

---

## 8. Tradeoffs

| Dimension | BFS | DFS | Beam Search | MCTS |
|---|---|---|---|---|
| Optimality guarantee | Yes (shallowest) | No | No (approximate) | Asymptotic (with enough iterations) |
| LLM call count (b=4, d=3) | 105 | 15–105 | ~35 (B=3) | ~60 (20 iter) |
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
- **Conversational agents**: users expect low-latency responses (< 2 s). Even beam_width=2 with branching 2 and depth=2 adds 9 LLM calls (3 generate + 6 evaluate) before the first response.
- **Cost-sensitive applications**: at ~$0.002 per call (gpt-5.6-terra list pricing, short prompts), 28 calls = $0.056 per query. For a 1M query/day system that is $56K/day.
- **Tasks without a useful value function**: if the evaluator is just restating the generator's output, scoring noise dominates and beam selection becomes random.
- **Long-horizon open-ended tasks**: trees with depth > 5 and branching > 3 become intractable even with beam search. Use hierarchical planning instead (decompose into sub-problems, apply ToT to each — see [Plan-and-Execute](plan_and_execute.md)).

---

## 10. Common Pitfalls

### Pitfall 1: Unbounded tree expansion — broken version

```python
# BROKEN: no cost controls, will exhaust rate limits and budget

def naive_tot(problem: str, depth: int = 5, branching: int = 5) -> list[str]:
    """BFS with depth=5, branching=5 => 5^5 = 3,125 leaf paths, ~4,700 LLM calls."""
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
- 781 generate calls (one per expanded node) + 3,905 evaluate calls = 4,686 LLM calls per planning run
- No pruning threshold — expands dead branches identically to promising ones
- No beam width — memory grows exponentially
- Value tracking discarded; winner chosen by path length (meaningless)

**Stated plainly.** "Two innocuous-looking default arguments — `depth=5, branching=5` — are a bill for ~4,700 LLM calls, because they sit in an exponent."

The docstring's `5^5 = 3,125` counts *leaf paths*, not calls: every expanded node costs one generate call, and every thought it returns costs one evaluate call.

| Symbol | What it is |
|--------|------------|
| `branching = 5` | The base. Thoughts generated per node |
| `depth = 5` | The exponent. Levels of recursion |
| `5^5` | Leaf paths explored: `3,125` |
| generate `= 1 + 5 + 25 + 125 + 625` | One call per expanded node: `781` |
| evaluate `= 5 + 25 + 125 + 625 + 3,125` | One call per generated thought: `3,905`. Total `4,686` |
| No pruning | Nothing is discarded, so the exponent applies to the full tree, not a beam |

**Walk one example.** What each parameter costs, at the `~$0.002` per call this file quotes:

```
  depth  branching   generate   evaluate   total calls   cost at $0.002
  -----  ---------   --------   --------   -----------   --------------
    3        3            13         39            52         $0.10
    3        5            31        155           186         $0.37
    5        3           121        363           484         $0.97
    5        5           781      3,905         4,686         $9.37

  Bumping depth 3 -> 5 at b=5:  186 -> 4,686 calls.  25x, from one keyword arg.
```

The asymmetry is worth internalizing before an interview: raising the branching factor by 2 multiplies cost by roughly `(5/3)^d`, but raising depth by 2 multiplies it by `b^2` almost exactly (186 -> 4,686 is 25x, and `5^2 = 25`). Depth is the more expensive knob at every branching factor above 1, which is why the fix below caps `max_depth` at 3 first and only then adds a beam.

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
- `max_depth=3` caps recursion (worst case 7 generate + 21 evaluate = 28 calls)
- `beam_width=3` caps memory and branching at each level
- `pruning_threshold=5.0` discards the bottom half of candidates immediately
- `max_calls=100` is a hard circuit breaker — safe for production
- `best_value` tracked throughout; winner is highest-scored node, not longest path

**Read it like this.** "Four independent brakes, each one bounding a different term: depth bounds the exponent, beam width bounds the base, the threshold bounds what survives, and `max_calls` bounds everything at once regardless of the other three."

The worst case is 28 calls, and the interesting question is why `max_calls=100` sits above that ceiling rather than below it.

| Symbol | What it is |
|--------|------------|
| `max_depth = 3` | Caps `d`. Turns the exponent from 5 into 3 |
| `beam_width = 3` | Caps surviving nodes per level, so the frontier never exceeds 3 |
| `branching_factor = 3` | Caps children per node, so candidates per level are at most `3 x 3 = 9` |
| `pruning_threshold = 5.0` | Drops anything below 5/10 before it can be expanded |
| `max_calls = 100` | Circuit breaker. Checked in both loops; aborts mid-level if tripped |

**Walk one example.** The fixed function's worst case, level by level:

```
  level   nodes in beam   generate calls   evaluate calls   running total
  -----   -------------   --------------   --------------   -------------
    1           1               1                3                4
    2           3               3                9               16
    3           3               3                9               28

  worst case total = 28 calls   (budget 100 -> 72 calls of headroom)

  Broken version, same problem : 4,686 calls
  Fixed version                :    28 calls    -> 167x reduction
```

Note the accounting trap worth catching: a naive reading of `beam_width=3, branching_factor=3, max_depth=3` gives `3 x 3 x 3 = 27` generate calls, but the code calls `generate_thoughts(..., k=branching_factor)` **once per node** and gets 3 thoughts back — so generation is 7 calls, not 27, and the true ceiling is 28. Either way the `max_calls=100` breaker sits comfortably above the worst case, which is the correct design: a circuit breaker that trips during normal operation is not a safety net, it is a silent truncation of your search. Set it above the analytical worst case so it only fires when an assumption (a retry loop, a mis-set parameter) has already broken.

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
| OpenAI GPT-5.6 (`gpt-5.6-sol` / `-terra` / `-luna`) | Generator + evaluator LLM | Strong instruction following; `temperature=0` for eval; use the cheaper `-luna` tier for the evaluator |
| Anthropic Claude Sonnet 5 / Opus 5 | Alternative LLM | Comparable quality; 1M-token context on Claude 4.6 and later for deep paths |
| [LangGraph](../agentic_frameworks/langgraph.md) | Graph-based agent orchestration | Native support for branching and backtracking via conditional edges |
| LlamaIndex | RAG + agent framework | `TreeSummarize` uses ToT-like aggregation; custom agent steps |
| Guidance (Microsoft) | Constrained generation | Forces structured thought proposals (JSON, numbered lists) |
| DSPy | Programmatic LLM optimization | `ChainOfThought` + `Refine` / `BestOfN` approximate sampling-with-reranking (the older `Retry` module has been removed) |
| Ray | Parallel LLM calls | Distribute `generate_thoughts` and `evaluate_thought` across workers |
| vLLM | High-throughput inference | Critical for BFS where many parallel calls are made simultaneously |
| lm-eval-harness | Benchmarking | Measure ToT vs CoT on ARC, GSM8K, MATH, HumanEval |
| Process Reward Models (PRM) | Fast value function | Smaller trained model replaces the LLM evaluator: one forward pass instead of a full generation call |

---

## 12. Interview Questions with Answers

**Q: What problem does Tree of Thoughts solve that chain-of-thought does not?**
Chain-of-thought generates a single linear reasoning path greedily — once a reasoning step is produced, the model cannot revisit it. ToT solves the inability to explore alternatives and backtrack by maintaining a tree of candidate thoughts and using search to navigate it.

**Q: How does the original ToT paper demonstrate the benefit quantitatively?**
On the 24-game benchmark (combine 4 numbers to reach 24 using arithmetic), GPT-4 with chain-of-thought solved 4.0% of problems, while GPT-4 with ToT + BFS at breadth b=5 solved 74%. Plain IO prompting scored 7.3% and ToT at b=1 already reached 45%, so most of the gain comes from pruning infeasible partial expressions rather than from breadth alone. The paper also reports 60% word-level success on mini crosswords versus 15.6% for CoT.

**Q: What is the role of the value function in ToT and what are the two main implementation strategies?**
The value function scores candidate thoughts to guide the search. The ToT paper defines two strategies: (1) value each state independently — the LLM emits a numeric score (e.g., 1–10) or a classification such as sure/maybe/impossible, allowing soft ranking; (2) vote across states — all sibling candidates go into one vote prompt and the LLM picks the most promising, sampled repeatedly for a step-wise self-consistency vote. Independent valuing suits continuous search strategies (beam, MCTS); voting suits picking a single child to follow (DFS).

**Q: Explain BFS vs DFS in the context of ToT and when you would choose each.**
BFS expands all nodes at depth k before proceeding to depth k+1, guaranteeing the shallowest solution but costing on the order of branching_factor^depth LLM calls. DFS commits to one branch and backtracks on failure, evaluating at most depth * branching_factor candidates in the best case. Choose BFS for shallow trees where global optimality matters; choose DFS for deep trees where early commitment is acceptable and cost is constrained.

**Q: What is beam search and why is it the most practical ToT strategy?**
Beam search is BFS with a fixed beam width B: at each level, generate k children per node, score all B*k candidates, keep only the top B. Cost is O(B * k * depth), which is linear in depth rather than exponential. With B=3, k=3, depth=3, this yields 28 LLM calls (7 generate + 21 evaluate) — tractable in production. Pure BFS with branching 4, depth 3 costs 105 calls (21 generate + 84 evaluate) even before pruning.

**Q: How does MCTS differ from beam search for agent planning?**
MCTS uses UCB1 to balance exploration and exploitation across iterations — nodes with high value but low visit count are preferentially expanded. Beam search is purely greedy at each level and does not revisit discarded branches. MCTS amortizes evaluation across many rollouts and is more sample-efficient for deep trees, but is harder to implement and reason about. Beam search is simpler and faster for shallow trees.

**Q: What is UCB1 and how does it drive MCTS node selection?**
UCB1 = V(node) + C * sqrt(ln(N_parent) / N_node), where V is the node's average value, N_node is its visit count, N_parent is the parent's visit count, and C is an exploration constant (commonly sqrt(2)). Nodes with high V are exploited; nodes with low N_node are explored. This formula ensures every node is eventually visited.

**Q: How do process reward models (PRMs) relate to ToT?**
PRMs are small models trained to score intermediate reasoning steps rather than only final answers. They replace the LLM evaluator in ToT with a dedicated, fast, cheap model — reducing evaluation cost from one full LLM call to one forward pass through a small classifier. OpenAI's "Let's Verify Step by Step" (Lightman et al., 2023) showed step-level supervision beats outcome-level supervision on MATH and released the PRM800K dataset; whether the shipped o1 models use a PRM at inference time has never been published, so treat that as a hypothesis.

**Q: What is the cost of naive BFS ToT with branching factor 4 and depth 3, and how does beam search reduce it?**
Naive BFS explores 4 + 16 + 64 = 84 nodes, costing 21 generate calls (one per expanded node) plus 84 evaluate calls = 105 LLM calls. Beam search with beam_width=3 expands only 1 + 3 + 3 = 7 nodes: 7 generate calls plus 4 + 12 + 12 = 28 evaluate calls = 35 LLM calls, a 3x reduction. A pruning threshold that discards low-scoring candidates before they enter the beam drops it further, since a pruned candidate is never expanded at the next level.

**Q: When is ToT not worth the extra LLM calls?**
ToT is not worth it when: (1) the task has a single obvious correct next step (RAG Q&A, summarization); (2) latency requirements preclude multiple sequential LLM calls (< 2 s response expected); (3) no meaningful value function exists to differentiate candidates; (4) per-query economics are too tight (high-volume consumer applications at low margins).

**Q: How does ToT relate to the "test-time compute scaling" narrative for models like o1?**
Test-time compute scaling refers to spending more inference compute to improve answer quality. ToT is one mechanism: by generating and evaluating multiple reasoning branches at inference time, the model effectively runs longer before producing an output. o1-class models do this internally with "reasoning tokens" the API bills but does not show; OpenAI exposes a `reasoning.effort` knob and `max_output_tokens`, and states reasoning length varies from a few hundred to tens of thousands of tokens rather than sitting at a fixed budget. Whether the internal mechanism is a tree search, a single long chain, or something else is not published — say so in an interview rather than asserting a tree.

**Q: What makes a good "thought" granularity in agent ToT?**
A thought should represent a complete, coherent agent action — one that advances the plan meaningfully and is independently evaluable. Too fine-grained (individual sentences) and the tree is too wide; the decoder's built-in beam search already handles sub-word diversity. Too coarse-grained (multi-step sub-plans) and the evaluator cannot distinguish good from bad candidates accurately. In practice, one thought = one tool call or one implementation step.

**Q: How do you prevent the evaluator from confirming whatever the generator produced (sycophancy)?**
Use separate, explicitly adversarial system prompts for the evaluator ("identify flaws and risks in this proposed action"). Ask the evaluator to reason about failure modes before assigning a score. Use a different model (or different temperature) for evaluation than generation. In high-stakes applications, use a trained discriminator or domain verifier (test execution, type checker) instead of an LLM evaluator altogether.

**Q: How would you implement ToT for a code debugging agent?**
Generator: given a failing test + current code, propose 3 candidate patches. Evaluator: run the unit test suite against each patch; value = fraction of tests passing (0.0–1.0). This replaces LLM scoring with a deterministic, cheap verifier — far more reliable. Use DFS: apply the highest-scoring patch, run tests, backtrack if no improvement. Depth limit = 5 attempts. This is essentially how iterative SWE-bench agents such as SWE-agent operate, though non-agentic pipelines like Agentless show sampling plus reranking against the same verifier can be competitive without any backtracking.

**Q: What is the relationship between ToT and classical AI search algorithms?**
ToT is a direct application of classical heuristic search (A*, BFS, DFS, beam search, MCTS) to the space of LLM-generated reasoning steps. The only LLM-specific adaptation is: (1) the branching factor is generated by sampling rather than enumerated from a fixed action space; (2) the heuristic function is an LLM or trained model rather than a hand-coded function. The underlying search theory (completeness, optimality, complexity) is identical.

**Q: How do you decide between beam search and MCTS for a new agent task?**
Use beam search when: depth is shallow (< 5), latency matters (serial expansion is faster), the task is relatively straightforward, and engineering simplicity is a priority. Use MCTS when: depth is large (5+), exploration/exploitation balance is critical (the optimal first step is not obvious), the rollout is cheap and informative, or you are dealing with game-like tasks where future rewards are highly uncertain. In practice, start with beam search and escalate to MCTS only if quality is insufficient.

---

## 13. Best Practices

**Start with beam search, not BFS.** BFS is theoretically clean but practically expensive. Beam_width=3 with branching_factor=3 and depth=3 gives 28 calls — a sensible default. Adjust beam_width up if quality is insufficient; bring branching_factor down first if cost is the constraint.

**Always set a hard call budget.** Implement a `max_calls` circuit breaker. Rate limits, network failures, and recursive bugs can cause unbounded expansion. Log every LLM call and terminate gracefully when the budget is exhausted, returning the best path found so far.

**Use separate prompts for generator and evaluator.** The evaluator must be adversarial or at least neutral — not a restatement of the generator's framing. Include explicit instruction to "identify risks and failure modes" before assigning a score.

**Prefer domain verifiers over LLM evaluators when available.** Unit test execution, type checkers, and mathematical verifiers (SymPy, Lean) are deterministic, cheap, and reliable. LLM-based evaluators are noisy — re-scoring the same candidate at temperature 0 can still move the score, so measure your own evaluator's variance before trusting a threshold.

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

The figures below are an **illustrative composite**, not measured results from a published study — they show the shape of the tradeoff on a hypothetical 200-PR annotated benchmark. Measure your own numbers before quoting any of them.

| Metric | Greedy CoT | ToT Beam (B=3, d=3) | Delta |
|---|---|---|---|
| Issues found (recall@3) | 61% | 79% | +18% |
| Redundant suggestions (%) | 34% | 9% | -25% |
| Fix correctness (human eval) | 52% | 71% | +19% |
| Avg LLM calls per review | 3 | 43 | +40 |
| Avg latency (parallel exec) | 1.2 s | 4.1 s | +2.9s |
| Cost per review ($0.002/call) | $0.006 | $0.086 | +$0.08 |

In this composite, ToT improves issue recall by 18 percentage points and fix correctness by 19 points at 14x higher LLM call count. For a code review product where fix quality is the value driver and $0.09/review is within budget, that tradeoff is justified. For a high-volume automated linting tool processing 10K PRs/day ($800/day cost delta), the greedy baseline with targeted ToT on high-complexity diffs is the right architecture.

**Implementation Note on Parallelism**

At depth 2, the 3 surviving depth-1 nodes are independent — their 4*3=12 candidate generation calls can run concurrently with `asyncio.gather`. This reduces wall-clock latency from 4.1 s to ~1.8 s at the cost of higher peak API concurrency (12 simultaneous requests). Most production LLM APIs enforce per-minute token limits that make this feasible without rate-limit errors.
