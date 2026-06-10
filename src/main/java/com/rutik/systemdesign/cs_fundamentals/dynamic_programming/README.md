# Dynamic Programming

> Breaking a hard problem into overlapping sub-problems, solving each once, and remembering the answer.

---

## 1. Concept Overview

Dynamic Programming (DP) is an algorithm design technique that solves optimisation and counting problems by decomposing them into overlapping sub-problems with optimal sub-structure. It trades space for time: store the result of every sub-problem so it is never recomputed.

DP is the answer to problems where naive recursion re-solves the same sub-problem exponentially many times. The canonical tell: the recursion tree has repeated nodes. DP prunes those repetitions by memoising (top-down) or filling a table in dependency order (bottom-up, tabulation).

This module covers the two structural properties that enable DP, the two implementation styles (memoisation and tabulation), space optimisation via rolling arrays, and the four canonical DP families that cover 80% of interview problems: 0/1 knapsack, longest common subsequence (LCS), edit distance, and coin change.

---

## 2. Intuition

> **One-line analogy**: DP is like a contractor who writes down every subcontract price so the same sub-job is never re-quoted — the final building estimate is assembled from the notebook, not re-derived from scratch.

**Mental model**: Draw the recursion tree. If you see the same node appearing multiple times, DP will help. The state space is the set of distinct sub-problem identifiers; the transition is the recurrence relation. DP solves each state exactly once and stores it.

**Why it matters**: DP solves problems that would otherwise require exponential brute force — from sequence alignment in bioinformatics, to spell-checking (edit distance), to compiler register allocation, to route optimisation in logistics. In interviews, DP is the most common "hard" category.

**Key insight**: The hardest part of DP is defining the state and the transition. If you define the state as "what I know at this point in the problem," the transition becomes "what choices do I have and how do they reduce to smaller states?" Explicitly write `dp[i]` = English description before writing code.

---

## 3. Core Principles

**Optimal substructure**: An optimal solution to the problem contains optimal solutions to its sub-problems. If you can cut the problem smaller and the optimal pieces assemble into the overall optimum, DP applies. Counterexample: longest simple path in a graph does not have optimal substructure (shortest sub-path may use vertices excluded from other sub-paths).

**Overlapping sub-problems**: The same sub-problems recur many times in the recursive solution. If all sub-problems are distinct (like merge sort's divide), simple recursion is sufficient — DP's memoisation adds overhead without benefit.

**State definition**: A state encodes the minimum information needed to determine which sub-problem we are in. Poorly chosen state = exponential state space; well-chosen state = polynomial (usually O(n), O(n²), or O(n³) states).

**Transition (recurrence)**: The value of dp[state] expressed as a function of smaller states. Prove the transition handles the base cases and that the dependency graph is acyclic (for tabulation, topological order of the DAG).

**Base cases**: The values of dp for trivially small inputs. Missing or incorrect base cases are the most common source of DP bugs.

---

## 4. Types / Architectures / Strategies

### Top-Down Memoisation

```
Define recursive function solve(state).
Check if state in memo → return cached result.
Compute result from recursive calls on smaller states.
Store in memo before returning.
```

Advantages: only computes states that are actually reached (useful when the reachable portion of the state space is small); natural to write; easy to debug by reading the recursion.

### Bottom-Up Tabulation

```
Allocate dp table indexed by state.
Fill base cases.
Iterate over states in topological order (dependency-first).
Answer is dp[target_state].
```

Advantages: no recursion overhead or stack overflow risk; amenable to space optimisation (rolling array); easier to profile (loop, not stack).

### Space Optimisation (Rolling Array)

When dp[i] only depends on dp[i-1] (or dp[i][j] only on dp[i-1][...]), you only need to keep the previous row. Reduces O(n²) space to O(n) in many 2D DP problems (knapsack, LCS, edit distance).

### DP on Intervals

State: dp[l][r] = answer for subarray/substring [l, r]. Transition: try all split points k in [l, r). Required: solve smaller intervals before larger. Fill order: increasing interval length.

### Digit DP

Count integers in [L, R] satisfying a digit-level constraint (e.g., no repeated digits, digit sum divisible by k). State: (position, tight_constraint, running_value). Common in competitive programming.

---

## 5. Architecture Diagrams

### Fibonacci — Naive vs Memoised vs Tabulated

```
Naive fib(5) recursion tree (exponential — 15 calls for fib(5)):
                  fib(5)
               /          \
           fib(4)          fib(3)
          /     \         /     \
       fib(3)  fib(2)  fib(2) fib(1)
       /   \
   fib(2) fib(1)

Memoised — each unique fib(k) computed once:
  fib(5) -> fib(4) -> fib(3) -> fib(2) -> fib(1) [base]
                              -> fib(0) [base]
                   -> fib(2) [CACHED]
              -> fib(3) [CACHED]

Tabulated (bottom-up, left to right):
  dp: [0, 1, 1, 2, 3, 5]
       ^  ^  ^  ^  ^  ^
       0  1  2  3  4  5   (index)
  Each cell computed once; O(n) time, O(n) space.
  Space-optimised: only keep prev two values -> O(1) space.
```

### 0/1 Knapsack — Table Fill Pattern

```
items = [(w=2,v=3), (w=3,v=4), (w=4,v=5)], capacity W=5

dp[i][w] = max value using first i items with capacity w

     w:  0  1  2  3  4  5
  i=0:   0  0  0  0  0  0   (no items)
  i=1:   0  0  3  3  3  3   (item1: w=2,v=3; take if w>=2)
  i=2:   0  0  3  4  4  7   (item2: w=3,v=4; take + dp[i-1][w-3])
  i=3:   0  0  3  4  5  7   (item3: w=4,v=5)

  Answer: dp[3][5] = 7
  Reconstruction: compare dp[i][w] vs dp[i-1][w]; if equal, item i not taken.
```

### LCS — Diagonal Propagation

```
s1 = "ABCB", s2 = "BDCAB"

dp[i][j] = LCS length of s1[:i] and s2[:j]

      ""  B  D  C  A  B
  ""   0  0  0  0  0  0
  A    0  0  0  0  1  1
  B    0  1  1  1  1  2
  C    0  1  1  2  2  2
  B    0  1  1  2  2  3  <- LCS length = 3 ("BCB")

  Rule: if s1[i-1]==s2[j-1]: dp[i][j] = dp[i-1][j-1] + 1
        else: dp[i][j] = max(dp[i-1][j], dp[i][j-1])
```

---

## 6. How It Works — Detailed Mechanics

### Fibonacci (Baseline DP)

```python
from __future__ import annotations
from functools import lru_cache
from typing import Dict


# Top-down memoisation
@lru_cache(maxsize=None)
def fib_memo(n: int) -> int:
    if n <= 1:
        return n
    return fib_memo(n - 1) + fib_memo(n - 2)


# Bottom-up tabulation, O(n) space
def fib_tab(n: int) -> int:
    if n <= 1:
        return n
    dp = [0] * (n + 1)
    dp[1] = 1
    for i in range(2, n + 1):
        dp[i] = dp[i - 1] + dp[i - 2]
    return dp[n]


# Space-optimised: O(1)
def fib_opt(n: int) -> int:
    if n <= 1:
        return n
    prev, curr = 0, 1
    for _ in range(2, n + 1):
        prev, curr = curr, prev + curr
    return curr
```

### 0/1 Knapsack

```python
def knapsack_01(weights: list[int], values: list[int], capacity: int) -> int:
    """
    Maximum value achievable by selecting items (each usable at most once)
    with total weight <= capacity.
    O(n * capacity) time and space.
    """
    n = len(weights)
    # dp[i][w] = max value using first i items with capacity w
    dp = [[0] * (capacity + 1) for _ in range(n + 1)]

    for i in range(1, n + 1):
        w_i, v_i = weights[i - 1], values[i - 1]
        for w in range(capacity + 1):
            # Option 1: don't take item i
            dp[i][w] = dp[i - 1][w]
            # Option 2: take item i (if it fits)
            if w >= w_i:
                dp[i][w] = max(dp[i][w], dp[i - 1][w - w_i] + v_i)

    return dp[n][capacity]


def knapsack_01_optimised(weights: list[int], values: list[int], capacity: int) -> int:
    """Space-optimised: O(capacity) space using rolling array."""
    dp = [0] * (capacity + 1)
    for w_i, v_i in zip(weights, values):
        # Iterate right-to-left to avoid using item i twice
        for w in range(capacity, w_i - 1, -1):
            dp[w] = max(dp[w], dp[w - w_i] + v_i)
    return dp[capacity]
```

### Longest Common Subsequence

```python
def lcs_length(s1: str, s2: str) -> int:
    """
    Length of the longest common subsequence of s1 and s2.
    O(m*n) time, O(min(m,n)) space (rolling row).
    """
    m, n = len(s1), len(s2)
    if m < n:
        s1, s2, m, n = s2, s1, n, m   # ensure s2 is shorter for space opt

    # dp[j] = LCS length of s1[:i] and s2[:j]
    dp = [0] * (n + 1)
    for i in range(1, m + 1):
        prev = 0   # dp[i-1][j-1]
        for j in range(1, n + 1):
            temp = dp[j]
            if s1[i - 1] == s2[j - 1]:
                dp[j] = prev + 1
            else:
                dp[j] = max(dp[j], dp[j - 1])
            prev = temp
    return dp[n]


def lcs_string(s1: str, s2: str) -> str:
    """Reconstruct the actual LCS string. O(m*n) space required."""
    m, n = len(s1), len(s2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if s1[i - 1] == s2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
    # Reconstruct
    result = []
    i, j = m, n
    while i > 0 and j > 0:
        if s1[i - 1] == s2[j - 1]:
            result.append(s1[i - 1]); i -= 1; j -= 1
        elif dp[i - 1][j] >= dp[i][j - 1]:
            i -= 1
        else:
            j -= 1
    return "".join(reversed(result))
```

### Edit Distance (Levenshtein)

```python
def edit_distance(word1: str, word2: str) -> int:
    """
    Minimum number of single-character operations (insert, delete, replace)
    to transform word1 into word2.
    O(m*n) time, O(min(m,n)) space.
    """
    m, n = len(word1), len(word2)
    if m < n:
        word1, word2, m, n = word2, word1, n, m

    # dp[j] = edit distance between word1[:i] and word2[:j]
    dp = list(range(n + 1))   # base case: transform empty string to word2[:j] = j insertions

    for i in range(1, m + 1):
        prev = dp[0]           # dp[i-1][j-1]
        dp[0] = i              # base case: transform word1[:i] to "" = i deletions
        for j in range(1, n + 1):
            temp = dp[j]
            if word1[i - 1] == word2[j - 1]:
                dp[j] = prev   # no operation needed
            else:
                dp[j] = 1 + min(
                    prev,       # replace
                    dp[j],      # delete from word1
                    dp[j - 1],  # insert into word1
                )
            prev = temp
    return dp[n]
```

### Coin Change (Unbounded Knapsack variant)

```python
def coin_change(coins: list[int], amount: int) -> int:
    """
    Minimum number of coins to make amount. Unlimited supply of each coin.
    Returns -1 if impossible.
    O(amount * len(coins)) time, O(amount) space.
    """
    INF = float("inf")
    dp = [INF] * (amount + 1)
    dp[0] = 0

    for a in range(1, amount + 1):
        for coin in coins:
            if coin <= a:
                dp[a] = min(dp[a], dp[a - coin] + 1)

    return dp[amount] if dp[amount] != INF else -1


def coin_change_count(coins: list[int], amount: int) -> int:
    """Number of distinct ways to make amount (order does not matter)."""
    dp = [0] * (amount + 1)
    dp[0] = 1
    for coin in coins:
        for a in range(coin, amount + 1):
            dp[a] += dp[a - coin]
    return dp[amount]
```

### Longest Increasing Subsequence (LIS)

```python
def lis_n2(nums: list[int]) -> int:
    """O(n^2) DP: dp[i] = LIS ending at index i."""
    n = len(nums)
    dp = [1] * n
    for i in range(1, n):
        for j in range(i):
            if nums[j] < nums[i]:
                dp[i] = max(dp[i], dp[j] + 1)
    return max(dp)


import bisect

def lis_nlogn(nums: list[int]) -> int:
    """
    O(n log n) patience sorting: maintain tails[] where tails[i] is the
    smallest tail element of all LIS of length i+1.
    """
    tails: list[int] = []
    for x in nums:
        pos = bisect.bisect_left(tails, x)
        if pos == len(tails):
            tails.append(x)
        else:
            tails[pos] = x
    return len(tails)
```

---

## 7. Real-World Examples

**Spell-checking (edit distance)**: GNU `aspell`, Microsoft Word, and search engines compute Levenshtein distance between the mistyped word and dictionary entries to suggest corrections. Optimisation: use a trie of the dictionary and traverse it alongside filling the DP row, pruning branches where the minimum possible distance exceeds a threshold.

**Sequence alignment (bioinformatics)**: Smith-Waterman and Needleman-Wunsch algorithms are edit-distance variants with affine gap penalties. Used in BLAST (Basic Local Alignment Search Tool) to align DNA/protein sequences. At genomics scale, the full O(mn) DP is replaced with heuristics (seed-extend) because m and n can be billions.

**Text diff (git diff)**: The Myers diff algorithm (used by Git, GNU diff) finds the shortest edit script (minimum insertions + deletions) between two files. It is a space-efficient variant of edit distance that finds the path in the edit graph.

**Resource planning / project scheduling**: Knapsack-style DP optimises resource allocation: which projects to fund given a budget constraint (unbounded knapsack = projects can be funded partially; 0/1 knapsack = binary choice). Used in portfolio optimisation, CPU cache partitioning between processes, and cloud spot-instance bidding.

**Route optimisation (TSP DP)**: The Held-Karp algorithm solves the Travelling Salesman Problem exactly in O(2^n × n²) using bitmask DP — exponential, but practical for n ≤ 20. Used in parcel delivery route planning and PCB drilling path optimisation.

**Parsing (CYK algorithm)**: Context-free grammar parsing uses a 3D DP table (chart parsing) — dp[i][j][A] = True if non-terminal A generates substring s[i..j]. Used in natural language processing and compiler front-ends.

---

## 8. Tradeoffs

### Memoisation vs Tabulation

| Aspect | Memoisation (top-down) | Tabulation (bottom-up) |
|--------|------------------------|------------------------|
| Code style | Recursive (natural) | Iterative (loop) |
| States computed | Only reachable states | All states in the table |
| Space optimisation | Hard (memo dict stores all) | Easy (rolling array) |
| Stack overflow risk | Yes (Python default recursion limit 1000) | No |
| Cache behaviour | Worse (dict hashing, pointer chasing) | Better (array, sequential access) |
| Debugging | Easy (print recursion tree) | Harder (need to inspect table) |

**Practical rule**: Use memoisation when: the reachable state space is much smaller than the full table; you need to write the solution quickly. Use tabulation when: you need space optimisation; n is large (risk of recursion limit); performance matters.

### State Space Complexity

| Problem | State space | Transition | Total |
|---------|-------------|------------|-------|
| Fibonacci | O(n) | O(1) | O(n) |
| 0/1 Knapsack | O(n × W) | O(1) | O(n × W) |
| LCS / Edit distance | O(m × n) | O(1) | O(m × n) |
| Coin change | O(amount) | O(k) coins | O(amount × k) |
| LIS (n²) | O(n) | O(n) | O(n²) |
| TSP (Held-Karp) | O(2^n × n) | O(n) | O(2^n × n²) |

---

## 9. When to Use / When NOT to Use

**Use DP when**: the problem asks for "minimum/maximum/count of ways" under constraints that can be broken into overlapping sub-problems; brute force would explore exponential combinations; the recurrence relation can be written explicitly.

**Recognition signals**: "How many ways to...?" (counting DP), "Minimum cost to reach...?" (optimisation DP), "Is it possible to...?" (feasibility DP, often converted to boolean table). If the problem has "decisions at each step that affect future choices," DP is likely the right lens.

**Use greedy instead of DP when**: the locally optimal choice is provably globally optimal (exchange argument); no backtracking is needed (e.g., interval scheduling maximisation, Huffman). Greedy is simpler; reach for it first and fall back to DP if the greedy fails a counterexample.

**Do NOT use DP when**: sub-problems are independent (divide and conquer without overlap — merge sort, binary search); the problem does not have optimal substructure (longest simple path); the state space is unbounded or grows with input in a way that makes the table infeasible.

**Complexity warning**: DP with O(n × W) where W can be 10^9 (unbounded integer) is pseudo-polynomial — it depends on the numeric value, not just the number of items. This is why knapsack is NP-hard in the general case despite the DP solution working well in practice when W is bounded.

---

## 10. Common Pitfalls

### Pitfall 1 — Off-by-one in base case / table size

```python
# BROKEN: table too small; dp[amount] is out of bounds
def broken_coin_change(coins, amount):
    dp = [float("inf")] * amount     # size = amount, indices 0..amount-1
    dp[0] = 0
    for a in range(1, amount + 1):   # accesses dp[amount] -> IndexError
        for c in coins:
            if c <= a:
                dp[a] = min(dp[a], dp[a - c] + 1)   # IndexError when a == amount
    return dp[amount]
```

```python
# FIX: allocate amount + 1 entries (indices 0..amount inclusive)
def fixed_coin_change(coins, amount):
    dp = [float("inf")] * (amount + 1)   # size = amount + 1
    dp[0] = 0
    for a in range(1, amount + 1):
        for c in coins:
            if c <= a:
                dp[a] = min(dp[a], dp[a - c] + 1)
    return dp[amount] if dp[amount] != float("inf") else -1
```

### Pitfall 2 — Unbounded vs 0/1 knapsack iteration order

```python
# BROKEN: outer loop over items, inner loop left-to-right -> allows using item multiple times
def broken_01_knapsack(weights, values, capacity):
    dp = [0] * (capacity + 1)
    for w, v in zip(weights, values):
        for cap in range(w, capacity + 1):   # left-to-right = unbounded knapsack!
            dp[cap] = max(dp[cap], dp[cap - w] + v)
    return dp[capacity]
```

```python
# FIX: right-to-left ensures each item is used at most once
def fixed_01_knapsack(weights, values, capacity):
    dp = [0] * (capacity + 1)
    for w, v in zip(weights, values):
        for cap in range(capacity, w - 1, -1):   # right-to-left = 0/1 knapsack
            dp[cap] = max(dp[cap], dp[cap - w] + v)
    return dp[capacity]
```

### Pitfall 3 — Forgetting Python recursion limit for large memoised DP

```python
# BROKEN: fib(10000) exceeds Python's default recursion limit of 1000
@lru_cache(maxsize=None)
def broken_fib(n):
    if n <= 1:
        return n
    return broken_fib(n - 1) + broken_fib(n - 2)   # RecursionError at ~1000 depth
```

```python
# FIX option 1: use bottom-up tabulation (no recursion)
def fixed_fib_tab(n):
    if n <= 1:
        return n
    prev, curr = 0, 1
    for _ in range(2, n + 1):
        prev, curr = curr, prev + curr
    return curr

# FIX option 2: increase recursion limit (use cautiously; not always safe)
import sys
sys.setrecursionlimit(20000)
```

### Pitfall 4 — Using global state in memoised DP

```python
# BROKEN: memo dict is shared across calls; different test cases corrupt each other
memo = {}

def broken_dp(n):
    if n in memo:
        return memo[n]
    ...
    memo[n] = result
    return result
```

```python
# FIX: use @lru_cache on the function, or pass memo as a local variable
from functools import lru_cache

@lru_cache(maxsize=None)
def fixed_dp(n):
    ...
# lru_cache is per-function-object; clear between test cases with fixed_dp.cache_clear()
```

### Pitfall 5 — Reconstructing the DP solution after space optimisation

```python
# BROKEN: after rolling-array space optimisation, you cannot reconstruct the path
# because you've discarded earlier rows.

# FIX: if you need the actual solution (not just the value), keep the full table.
# If memory is the bottleneck, use Hirschberg's algorithm (LCS in O(m+n) space
# while still allowing path reconstruction via divide-and-conquer on the DP).
```

---

## 11. Technologies & Tools

| Tool / Library | Use case | Notes |
|----------------|----------|-------|
| Python `functools.lru_cache` | Memoisation | Per-function cache; clear with `.cache_clear()` |
| Python `functools.cache` (3.9+) | Memoisation (unbounded) | Simpler alias for `lru_cache(maxsize=None)` |
| NumPy 2D arrays | Large DP tables | ~10× faster than Python lists for numeric DP |
| `scipy.spatial.distance` | Edit distance at scale | For small strings; BLAST for biological sequences |
| `difflib.SequenceMatcher` | Python diff / LCS | Ratcliff/Obershelp — not true LCS but fast |
| `Levenshtein` PyPI package | Fast edit distance | C extension; ~100× faster than pure Python |

---

## 12. Interview Questions with Answers

**Q1: How do you recognise a DP problem?**
Three signals: (1) the problem asks for an optimal value (min/max), count of ways, or feasibility under constraints; (2) there are "decisions at each step" that affect later choices; (3) brute force requires exponential search. Confirm by identifying overlapping subproblems (the same sub-problem recurs in the recursion tree) and optimal substructure (an optimal solution contains optimal sub-solutions).

**Q2: What is the difference between top-down memoisation and bottom-up tabulation?**
Memoisation: write the recursive solution, add a cache (dict or `lru_cache`). Computes only reachable states; natural to code; risks stack overflow for large n. Tabulation: fill a table iteratively in dependency order. Computes all states; no stack risk; amenable to space optimisation (rolling array). Both have the same asymptotic complexity; tabulation usually has better constant factors due to cache-friendly array access vs dict lookup.

**Q3: Explain the 0/1 knapsack problem and its DP recurrence.**
Given n items each with weight w_i and value v_i, and a capacity W, maximise total value with total weight ≤ W. Each item is used at most once. Recurrence: `dp[i][w] = max(dp[i-1][w], dp[i-1][w - w_i] + v_i)` if `w >= w_i`, else `dp[i-1][w]`. Complexity: O(n × W) time, O(W) space with rolling array. This is pseudo-polynomial (depends on W's numeric value, not its bit-length).

**Q4: Why is knapsack NP-hard if it has a polynomial DP solution?**
The DP runs in O(nW) which depends on the numeric value of W. If W = 2^30, that's ~10^9 operations — exponential in the bit-length of the input. "Pseudo-polynomial" means polynomial in the input value, not the input size. True polynomial algorithms run in time polynomial in the number of input bits (log W). No such algorithm is known for knapsack, which is why it is NP-hard in the strong sense.

**Q5: What is the recurrence for edit distance and what do the three choices represent?**
`dp[i][j] = min(dp[i-1][j-1] + cost, dp[i-1][j] + 1, dp[i][j-1] + 1)` where `cost = 0` if `word1[i-1] == word2[j-1]` else 1. Three choices: replace (`dp[i-1][j-1] + cost` — align characters i and j), delete from word1 (`dp[i-1][j] + 1` — consume character i from word1 with a deletion), insert into word1 (`dp[i][j-1] + 1` — consume character j from word2 with an insertion).

**Q6: What is the key difference between LCS and edit distance?**
LCS finds the length of the longest common subsequence; edit distance (with insert/delete only, no replace) equals `m + n - 2 × LCS(s1, s2)`. With replace allowed, they diverge. LCS uses `dp[i][j] = dp[i-1][j-1] + 1` when characters match; edit distance adds the replace transition. Both are O(mn) time and space.

**Q7: How does the coin change DP differ from 0/1 knapsack?**
Coin change is unbounded knapsack: each coin can be used unlimited times. The difference in code: the 0/1 knapsack rolls the inner loop right-to-left (so dp[cap - w] refers to the previous item's row — can't use current item again). The unbounded knapsack iterates left-to-right (dp[cap - w] already incorporates the current item — can use it again). The coin change count variant (number of ways) also uses left-to-right iteration.

**Q8: What is patience sorting and how does it give O(n log n) LIS?**
Maintain an array `tails` where `tails[k]` is the smallest tail element of all increasing subsequences of length `k+1` seen so far. For each new element x: binary search (lower_bound) for its insertion position in `tails`. If past the end, extend `tails` (LIS length increases). Otherwise, replace `tails[pos] = x` (maintain the invariant). The length of `tails` at the end is the LIS length. Each of n elements requires O(log n) binary search → O(n log n) total.

**Q9: How do you reconstruct the actual DP solution (not just the value)?**
Keep a `choice` table alongside the `dp` table. At each state, record which transition was chosen (e.g., for knapsack: `taken[i][w] = True` if item i was taken). After filling, trace back from `dp[n][W]`: if `taken[i][w]` is True, include item i and move to `(i-1, w - w_i)`, else move to `(i-1, w)`. For rolling-array optimisations, reconstruction requires keeping the full `choice` table — you cannot reconstruct from the rolling dp row alone.

**Q10: What is interval DP and give an example?**
Interval DP: `dp[l][r]` = optimal answer for the subproblem on range [l, r]. Fill in order of increasing interval length. Example: Matrix Chain Multiplication — `dp[l][r]` = minimum multiplications to compute matrices l through r. Transition: try every split k in [l, r-1]: `dp[l][r] = min over k of (dp[l][k] + dp[k+1][r] + dims[l-1]*dims[k]*dims[r])`. O(n³) time. Other examples: Burst Balloons (LeetCode 312), Zuma Game, Palindrome Partitioning.

**Q11: When should you use DP vs greedy?**
Use DP when: locally optimal choices may not lead to global optimum (test with a counterexample); backtracking is needed. Use greedy when: an exchange argument proves local = global (the greedy choice never leaves you worse off); e.g., interval scheduling (earliest finish first), Huffman coding (minimum frequency characters merged first). Rule of thumb: try greedy first (simpler), find a counterexample, fall back to DP if one exists.

**Q12: What is bitmask DP and when is it used?**
State includes a bitmask encoding a subset of elements. Used when n ≤ 20 and the state is "which subset has been visited/used." Example: Travelling Salesman Problem — `dp[mask][v]` = minimum cost path visiting exactly the cities in `mask` and ending at `v`. Transition: `dp[mask | (1 << u)][u] = min(..., dp[mask][v] + dist[v][u])`. O(2^n × n²) time, O(2^n × n) space. Bitmask DP is also used for assignment problems and scheduling with state constraints.

**Q13: How do you optimise DP when transitions take O(n) instead of O(1)?**
Several techniques: (1) prefix/suffix arrays to precompute range sums/mins so the transition becomes O(1); (2) segment tree or BIT to query over a range of previous states in O(log n); (3) monotone deque (sliding window minimum) to optimise "take the best of the last k states" from O(k) to O(1) amortised; (4) divide-and-conquer optimisation when the optimal split point for dp[i] is monotone in i (reduces O(n²) transition to O(n log n)).

**Q14: Explain the relationship between edit distance and version control.**
Git's diff algorithm (Myers diff) finds the shortest edit script — minimum number of insertions and deletions to transform one file version into another. It works on the edit distance DAG where diagonal moves (matching lines) are free and horizontal/vertical moves (delete/insert) cost 1. Myers algorithm finds the shortest D-path (path using D non-diagonal moves) in O(D × n) time using the "furthest reaching D-path" observation. The human-readable diff output is the path reconstruction.

**Q15: What is the space complexity of edit distance and how can it be improved?**
Standard: O(m × n) space (full table). Space-optimised with rolling array: O(min(m, n)) — only keep two rows. For actual path reconstruction with O(m + n) space: Hirschberg's algorithm divides the problem at the midpoint, computes score from both ends (O(n) each), finds the optimal midpoint in O(mn) time, then recurses on two halves of O(n/2) space each — total O(mn) time, O(m + n) space.

**Q16: What are the four DP problem families and their canonical representatives?**
(1) 0/1 knapsack family: subset selection with constraints (0/1 knapsack, partition equal subset sum, target sum). (2) LCS/sequence alignment family: comparing two sequences character-by-character (LCS, edit distance, regex matching, wildcard matching). (3) Unbounded selection family: unlimited repetition of choices (coin change, perfect squares, climbing stairs). (4) Interval DP family: range-based sub-problems (matrix chain multiplication, burst balloons, palindrome partitioning). Most DP problems in interviews belong to one of these four shapes.

**Q17: Describe a DP problem you can solve greedily by accident and explain the failure.**
0/1 knapsack with the greedy heuristic "take highest value-to-weight ratio first." Example: capacity=10, items={(weight=6,value=8), (weight=5,value=5), (weight=5,value=5)}. Greedy takes the first item (ratio 1.33) and cannot fit either remaining item → value=8. Optimal: take items 2 and 3 → value=10. Greedy fails because picking one item blocks the combination that provides higher total value. The exchange argument breaks down because items are indivisible (0/1) — fractional knapsack (where you can take partial items) is solvable by greedy.

**Q18: How does DP apply in string problems like regex or wildcard matching?**
Regex matching (`.` = any char, `*` = zero or more of previous): `dp[i][j]` = True if pattern[:j] matches string[:i]. Transition for `pattern[j-1] == '*'`: either use zero times (`dp[i][j] = dp[i][j-2]`) or use one more time (`dp[i][j] = dp[i-1][j] if pattern[j-2] matches string[i-1]`). The key insight: `*` can match zero or more — the "zero" case is the base; the "more" case consumes a character from the string. O(mn) time, O(mn) space.

---

## 13. Best Practices

**Start with the state definition, not the code**: Write `dp[i]` = "(English description)" before writing any code. If the English description is ambiguous or has multiple interpretations, the state is not well-defined. A clear state definition makes the transition obvious.

**Verify the recurrence with a small example by hand**: Trace through the table for a 3–4 element example before trusting the code. Most DP bugs are in the transition or base cases, not in the loop structure.

**Prefer tabulation for production code**: Memoisation is great for rapid prototyping and interviews, but tabulation avoids recursion limits, enables space optimisation, and typically has better cache performance. In Python, tabulation also avoids the overhead of dictionary lookups in `lru_cache`.

**Apply space optimisation only after correctness is confirmed**: Write the full 2D table first. Once correct, derive the rolling-array version. Optimising prematurely obscures the DP structure and makes debugging much harder.

**Distinguish counting DP from optimisation DP**: They often look similar (same table shape) but the transitions differ. Counting DP: `dp[a] += dp[a - coin]` (sum all ways). Optimisation DP: `dp[a] = min(dp[a], dp[a - coin] + 1)` (take the best single option). Mixing these produces wrong answers.

---

## 14. Case Study

### Scenario: Autocorrect Feature — Minimum Edit Distance with Custom Costs

A mobile keyboard's autocorrect must suggest the closest dictionary word to a mistyped input. Unlike standard edit distance (all operations cost 1), the product team wants: substitutions cost 1, adjacent-key substitutions (e.g., 's' for 'a' on a QWERTY keyboard) cost 0.5, insertions/deletions cost 1.5 (users more often mistype a key than add/drop one).

**State**: `dp[i][j]` = minimum edit cost to transform `typed[:i]` into `dictionary_word[:j]`.

**Transition**:

```python
from __future__ import annotations

ADJACENT_KEYS: dict[str, set[str]] = {
    "a": {"s", "q", "w", "z"},
    "s": {"a", "d", "w", "e", "x", "z"},
    # ... (abbreviated)
}


def edit_distance_weighted(typed: str, target: str) -> float:
    """
    Weighted edit distance. O(m*n) time and space.
    Insert / delete cost 1.5; substitute cost 1; adjacent-key substitute cost 0.5.
    """
    m, n = len(typed), len(target)
    INS_DEL = 1.5
    SUB = 1.0
    ADJ_SUB = 0.5

    dp = [[0.0] * (n + 1) for _ in range(m + 1)]
    for i in range(m + 1):
        dp[i][0] = i * INS_DEL        # delete all chars from typed
    for j in range(n + 1):
        dp[0][j] = j * INS_DEL        # insert all chars into empty typed

    for i in range(1, m + 1):
        for j in range(1, n + 1):
            c_typed = typed[i - 1]
            c_target = target[j - 1]
            if c_typed == c_target:
                sub_cost = 0.0
            elif c_target in ADJACENT_KEYS.get(c_typed, set()):
                sub_cost = ADJ_SUB
            else:
                sub_cost = SUB
            dp[i][j] = min(
                dp[i - 1][j - 1] + sub_cost,    # substitute (or match)
                dp[i - 1][j] + INS_DEL,          # delete from typed
                dp[i][j - 1] + INS_DEL,          # insert into typed
            )

    return dp[m][n]
```

**BROKEN — using plain Hamming distance (wrong algorithm)**:

```python
# BROKEN: Hamming distance only works for equal-length strings with no insertions/deletions
def broken_autocorrect(typed: str, candidate: str) -> float:
    if len(typed) != len(candidate):
        return float("inf")    # rejects "helo" vs "hello" (length differs by 1)
    return sum(a != b for a, b in zip(typed, candidate))
    # Misses: "helo" → "hello" (one insertion, distance=1) mapped to infinity
    # Suggests "help" (Hamming=1) over "hello" (true edit distance=1) incorrectly
```

```python
# FIX: use edit distance to handle insertions and deletions
def fixed_autocorrect_top_k(typed: str, dictionary: list[str], k: int = 5) -> list[str]:
    scored = [(edit_distance_weighted(typed, w), w) for w in dictionary]
    scored.sort()
    return [w for _, w in scored[:k]]
```

**Performance optimisation — early termination**:

```python
def edit_distance_bounded(typed: str, target: str, max_cost: float) -> float:
    """
    Same as edit_distance_weighted but returns max_cost+1 if the minimum
    possible cost exceeds max_cost, enabling early pruning.
    Uses the observation that dp[i][j] >= |i - j| * INS_DEL.
    """
    if abs(len(typed) - len(target)) * 1.5 > max_cost:
        return max_cost + 1.0   # length difference alone exceeds budget
    return edit_distance_weighted(typed, target)
```

**Scaling to a full dictionary (100K words)**:

```
Naive: compare typed against all 100K words -> 100K * O(m*n) per keystroke.
Optimised pipeline:
  1. Filter by length: abs(len(typed) - len(candidate)) > threshold -> skip.
     Cuts 90%+ of candidates for threshold=2.
  2. BK-Tree (metric tree on edit distance): find all words within distance k
     in O(k^2 * log(dict_size)) using the triangle inequality.
  3. Cache: memoize (typed_prefix, candidate_prefix) pairs across keystrokes.

Latency target: < 20ms per keystroke (perceived as instantaneous).
With BK-tree + length filter: ~2ms on a modern phone CPU.
```

**Discussion questions**:
1. How would you extend this to support transpositions (swapped adjacent characters) — e.g., "teh" → "the" at cost 0.5?
2. How does the BK-Tree exploit the triangle inequality to prune candidates?
3. If the dictionary has 1M words (multilingual), what data structure would you use?

---

## See Also

- [recursion_and_problem_solving_patterns](../recursion_and_problem_solving_patterns/) — recursion mechanics, memoisation concept
- [greedy_and_divide_and_conquer](../greedy_and_divide_and_conquer/) — when greedy works vs when DP is required
- [sorting_and_searching](../sorting_and_searching/) — binary search + DP for O(n log n) LIS
- [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/) — DP on graphs (shortest path = DP on DAG)
- [`python/collections_and_data_structures`](../../python/collections_and_data_structures/) — Python `functools.lru_cache` internals
- [DSA Pattern Playbooks](../dsa_patterns/) — [Dynamic Programming pattern playbook](../dsa_patterns/dynamic_programming.md) (recognition signals + DP family templates: knapsack, LIS, LCS, grid, interval, palindrome, bitmask) and [`case_studies/dynamic_programming_patterns.md`](../case_studies/dynamic_programming_patterns.md) for a full worked walkthrough
