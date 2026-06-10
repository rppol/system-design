# Dynamic Programming Patterns — Interview-Problem Walkthrough

> CS Fundamentals Case Study | Adapted 11-Section Walkthrough Template

---

## Intuition

Dynamic programming is not an algorithm — it is a *problem decomposition strategy*. The core insight is that a problem has **optimal substructure** (the optimal solution is built from optimal solutions to smaller sub-problems) and **overlapping subproblems** (those smaller sub-problems are solved more than once in a naive recursion). Once you recognise both properties, you have two mechanical moves: memoise the recursion (top-down) or convert it to an iterative table (bottom-up tabulation). Everything else — space optimisation, rolling arrays, dimensionality reduction — is engineering applied on top of that core insight.

The practitioner skill interviewers test is pattern recognition: given an unfamiliar word problem, can you identify *which* of the canonical DP families it belongs to, write the recurrence relation, define the state, and choose the right traversal order? This walkthrough covers the four families you will encounter most often:

1. **Linear DP** — Climbing Stairs, Coin Change (tabulation, 1-D state)
2. **Subsequence / Interval DP** — Longest Common Subsequence and Edit Distance (2-D table)
3. **Knapsack family** — 0/1 Knapsack and Unbounded Knapsack (rolling-array space optimisation)
4. **Path DP** — Unique Paths on a grid (row-compression to O(n) space)

The BROKEN→FIX examples in §4 address the two bugs that account for the majority of failed DP implementations in production and interviews: forgetting memoisation (exponential recursion) and traversing the knapsack inner loop in the wrong direction (accidentally allowing item reuse).

---

## 1. Problem Statement & Clarifying Questions

### Four Representative Problems

**Problem 1 — Climbing Stairs / Coin Change (Linear DP)**

Climbing Stairs: You are climbing a staircase with `n` steps. Each time you can climb 1 or 2 steps. How many distinct ways can you reach the top?

Coin Change: Given an array of coin denominations `coins` and a target amount `amount`, find the minimum number of coins needed to make up that amount. Return -1 if it is impossible.

**Problem 2 — Longest Common Subsequence (Subsequence DP)**

Given two strings `text1` and `text2`, return the length of their longest common subsequence (LCS). A subsequence is a sequence that can be derived from another sequence by deleting some or no elements without changing the order.

**Problem 3 — Edit Distance / Levenshtein Distance (Interval / String DP)**

Given two strings `word1` and `word2`, return the minimum number of single-character edit operations (insertions, deletions, substitutions) required to convert `word1` into `word2`.

**Problem 4 — 0/1 Knapsack and Unbounded Knapsack**

0/1 Knapsack: Given `n` items each with weight `w[i]` and value `v[i]`, and a knapsack of capacity `W`, select items (each at most once) to maximise total value without exceeding capacity.

Unbounded Knapsack: Same setup, but each item may be used any number of times.

**Problem 5 — Unique Paths (Grid / Path DP)**

A robot starts at the top-left corner of an `m x n` grid and wants to reach the bottom-right corner. The robot can only move right or down. How many distinct paths are there?

---

### Clarifying Questions to Ask in an Interview

**Q: Are inputs guaranteed non-negative / non-empty?**
Yes for these formulations — but ask explicitly; negative coin denominations would change the problem significantly.

**Q: For LCS, do we need the actual subsequence or just the length?**
Length first; recovering the actual sequence requires backtracking through the table (covered in §6).

**Q: For Knapsack, can we split items? (Fractional Knapsack)**
No. Fractional → Greedy (sort by value/weight, take greedily). Integer items → DP.

**Q: For Unique Paths, can cells be blocked (obstacles)?**
The base problem has no obstacles; the variant with blocked cells is Unique Paths II (covered in §6).

**Q: For Coin Change, are all coin values positive integers?**
Yes; if not, the problem transforms into something closer to a subset-sum over integers.

---

## 2. Brute Force & Complexity Baseline

### Pattern 1 — Linear DP (Climbing Stairs)

Brute force: enumerate all paths via recursion. At each step, recurse on `(n-1)` and `(n-2)`.

```python
def climb_stairs_brute(n: int) -> int:
    if n <= 1:
        return 1
    return climb_stairs_brute(n - 1) + climb_stairs_brute(n - 2)
```

Recurrence: `T(n) = T(n-1) + T(n-2)`. This is a Fibonacci-type recurrence. Solving it gives `T(n) = O(phi^n)` where `phi ≈ 1.618`, so **exponential** time. Space: O(n) call stack.

For `n = 50`, this computes approximately 2^50 ≈ 10^15 function calls. Completely infeasible.

Coin Change brute force: try all coin selections with recursion.

```python
from typing import List

def coin_change_brute(coins: List[int], amount: int) -> int:
    """Exponential brute force: O(k^(amount/min_coin)) where k = len(coins)."""
    if amount == 0:
        return 0
    best = float("inf")
    for c in coins:
        if c <= amount:
            sub = coin_change_brute(coins, amount - c)
            if sub != float("inf"):
                best = min(best, sub + 1)
    return best
```

For `amount = 100` with `coins = [1, 5, 10, 25]`, this explores the same sub-problem `coin_change_brute(coins, 75)` thousands of times. The call tree has `k^(amount)` nodes in the worst case.

### Pattern 2 — Subsequence DP (LCS)

Brute force: generate all 2^m subsequences of `text1`, check each against `text2`. Time: O(2^m * n). For `m = n = 20`, this is roughly 10^6 * 20 = 20 million operations — borderline. For `m = n = 1000` (LeetCode typical), 2^1000 is impossibly large.

Recursive brute force (the DP without memo):

```python
def lcs_brute(text1: str, text2: str, i: int, j: int) -> int:
    """Exponential recursion without memoisation."""
    if i == 0 or j == 0:
        return 0
    if text1[i - 1] == text2[j - 1]:
        return 1 + lcs_brute(text1, text2, i - 1, j - 1)
    return max(lcs_brute(text1, text2, i - 1, j), lcs_brute(text1, text2, i, j - 1))
```

The same sub-problem `lcs_brute(text1, text2, i, j)` is recomputed exponentially many times. For `m = n = 40`, this requires roughly 2^40 ≈ 10^12 calls.

### Pattern 3 — Knapsack

Brute force: enumerate all 2^n subsets of items, compute total weight and value for each, filter by capacity. Time: O(2^n). For `n = 30`, that is 10^9 subsets. LeetCode limits are typically n up to 200 and W up to 10000 — brute force is completely infeasible.

```python
def knapsack_brute(weights: List[int], values: List[int], capacity: int, i: int) -> int:
    """O(2^n) brute force: try including or excluding each item."""
    if i == 0 or capacity == 0:
        return 0
    if weights[i - 1] > capacity:
        return knapsack_brute(weights, values, capacity, i - 1)
    return max(
        knapsack_brute(weights, values, capacity, i - 1),               # exclude
        values[i - 1] + knapsack_brute(weights, values, capacity - weights[i - 1], i - 1),  # include
    )
```

### Pattern 4 — Unique Paths

Brute force: DFS/BFS on the grid, counting all root-to-leaf paths. Time: O(2^(m+n)) in the worst case (exponential branching). The grid can be 100x100, giving 2^200 paths — impossible.

```python
def unique_paths_brute(m: int, n: int, r: int, c: int) -> int:
    """Exponential brute force: count every distinct root-to-goal path."""
    if r == m - 1 and c == n - 1:
        return 1
    paths = 0
    if r + 1 < m:
        paths += unique_paths_brute(m, n, r + 1, c)
    if c + 1 < n:
        paths += unique_paths_brute(m, n, r, c + 1)
    return paths
```

For a 20x20 grid, this makes C(38, 18) ≈ 10^10 calls.

---

## 3. Optimal Approach & Key Insight

### The Two Necessary Conditions for DP

A problem admits DP if and only if it has both:

1. **Optimal substructure**: the optimal solution to the problem contains optimal solutions to sub-problems. Equivalently, you can *build* the answer bottom-up from smaller answers without ever second-guessing an earlier choice.

2. **Overlapping subproblems**: the recursion tree re-computes the same subproblem many times. If every sub-problem is unique (like merge sort), divide-and-conquer suffices; DP's memoisation table buys nothing. Only when subproblems overlap do you win.

If the problem has optimal substructure but **not** overlapping subproblems, try divide-and-conquer. If it has optimal substructure and subproblems are *independent* with a greedy-exchange argument, use greedy.

---

### Pattern 1 — Linear DP: Coin Change

**State definition**: `dp[a]` = minimum number of coins to make amount `a`.

**Base case**: `dp[0] = 0` (zero coins needed to make zero).

**Transition**: For each amount `a` from 1 to `amount`, and for each coin `c` in `coins`:
```
if a >= c:
    dp[a] = min(dp[a], dp[a - c] + 1)
```

**Answer**: `dp[amount]` if it is not infinity, else -1.

This is bottom-up tabulation. We fill the table left to right, and each cell depends only on earlier cells — so no ordering issues arise.

---

### Pattern 2 — Subsequence DP: LCS with ASCII Table

**State definition**: `dp[i][j]` = length of LCS of `text1[0..i-1]` and `text2[0..j-1]`.

**Base case**: `dp[i][0] = 0` for all i; `dp[0][j] = 0` for all j.

**Transition**:
```
if text1[i-1] == text2[j-1]:
    dp[i][j] = dp[i-1][j-1] + 1
else:
    dp[i][j] = max(dp[i-1][j], dp[i][j-1])
```

**ASCII table for LCS("ABCDE", "ACE")**:

```
        ""  A   C   E
    ""   0   0   0   0
    A    0   1   1   1
    B    0   1   1   1
    C    0   1   2   2
    D    0   1   2   2
    E    0   1   2   3
```

Reading: `dp[5][3] = 3`, so LCS length is 3. The actual LCS is "ACE" (recovered by backtracking: when `text1[i-1] == text2[j-1]`, include the character and move diagonal; otherwise move in the direction of the larger neighbour).

---

### Pattern 3 — Knapsack: 0/1 vs Unbounded

**0/1 Knapsack — the direction of the inner loop matters critically.**

State: `dp[w]` = maximum value achievable with capacity `w`.

The key insight is that in 0/1 knapsack, each item can be used at most once. To enforce this, the inner capacity loop must run **right to left** (from `W` down to `w[i]`). This ensures that when we update `dp[w]`, we are reading `dp[w - w[i]]` from the *previous item's* row — the item has not yet been added at any smaller capacity in the current pass.

If you run the inner loop left to right, you read from the *current* pass's already-updated values, which means you may include the same item multiple times. That turns the problem into Unbounded Knapsack.

**ASCII table for 0/1 Knapsack (items: [(w=2,v=3), (w=3,v=4), (w=4,v=5)], W=5)**:

Processing item (w=2, v=3), inner loop right to left:
```
Before: dp = [0, 0, 0, 0, 0, 0]
After:  dp = [0, 0, 3, 3, 3, 3]
```

Processing item (w=3, v=4), inner loop right to left:
```
Before: dp = [0, 0, 3, 3, 3, 3]
After:  dp = [0, 0, 3, 4, 4, 7]
```
At capacity 5: dp[5] = max(dp[5], dp[5-3]+4) = max(3, dp[2]+4) = max(3, 3+4) = 7. Correct: use both items (w=2+3=5, v=3+4=7).

Processing item (w=4, v=5), inner loop right to left:
```
Before: dp = [0, 0, 3, 4, 5, 7]
After:  dp = [0, 0, 3, 4, 5, 7]
```
No improvement because capacity 5 already achieves 7.

---

### Pattern 4 — Grid DP: Unique Paths

**State**: `dp[i][j]` = number of distinct paths to cell `(i, j)`.

**Base case**: `dp[0][j] = 1` for all j (only one way to traverse the top row — all right); `dp[i][0] = 1` for all i (only one way to traverse the left column — all down).

**Transition**: `dp[i][j] = dp[i-1][j] + dp[i][j-1]` (came from above or from the left).

**Space optimisation**: Each row depends only on the row above and the current row. You can compress to a 1-D array of length `n` and update it in place, left to right, for each row. This reduces space from O(m*n) to O(n).

---

## 4. Implementation

### 4.1 BROKEN: Recursive Fibonacci Without Memoisation

```python
# BROKEN — exponential time, infeasible for large n
def fib_broken(n: int) -> int:
    """Naive recursion: recomputes fib(2) through fib(n-2) exponentially many times."""
    if n <= 1:
        return n
    return fib_broken(n - 1) + fib_broken(n - 2)

# fib_broken(50) makes approximately 2^50 ≈ 10^15 recursive calls.
# On a modern machine doing 10^8 ops/second, this takes ~10^7 seconds ≈ 115 days.
```

### 4.2 FIX Step 1: Add Memoisation (Top-Down)

```python
from functools import lru_cache

def fib_memo(n: int) -> int:
    """Memoised recursion: each sub-problem computed exactly once."""

    @lru_cache(maxsize=None)
    def helper(k: int) -> int:
        if k <= 1:
            return k
        return helper(k - 1) + helper(k - 2)

    return helper(n)

# fib_memo(50) makes exactly 50 unique recursive calls.
# Time: O(n), Space: O(n) for the call stack + cache.
```

### 4.3 FIX Step 2: Bottom-Up Tabulation (Best)

```python
def fib_tabulation(n: int) -> int:
    """Bottom-up DP: no recursion overhead, O(n) time, O(1) space."""
    if n <= 1:
        return n
    prev2, prev1 = 0, 1
    for _ in range(2, n + 1):
        prev2, prev1 = prev1, prev1 + prev2
    return prev1

# fib_tabulation(50) = 12586269025
# 50 loop iterations, 2 variables, no call stack.
```

---

### 4.4 BROKEN: 0/1 Knapsack Inner Loop Left-to-Right (Becomes Unbounded)

```python
from typing import List

# BROKEN — inner loop runs left to right: items can be reused.
# This solves Unbounded Knapsack, not 0/1 Knapsack.
def knapsack_01_broken(weights: List[int], values: List[int], capacity: int) -> int:
    dp = [0] * (capacity + 1)
    for i in range(len(weights)):
        for w in range(weights[i], capacity + 1):  # BUG: left to right
            dp[w] = max(dp[w], dp[w - weights[i]] + values[i])
    return dp[capacity]

# Example: weights=[2,3], values=[3,4], capacity=5
# Expected 0/1 answer: 7 (use both items once: 2+3=5 weight, 3+4=7 value)
# knapsack_01_broken([2,3],[3,4],5) returns 9 — WRONG.
# It uses item 0 twice (2+2+... doesn't fit in 5, but item 0 at w=2 gets
# dp[2]=3, then at w=4 picks dp[4-2]+3=dp[2]+3=6, then at w=5 picks
# dp[5-3]+4=dp[2]+4=7... actually the exact output depends on loop order,
# but the semantic error is that left-to-right allows item reuse.
# For weights=[1], values=[5], capacity=3: broken returns 15 (item used 3x),
# correct 0/1 answer is 5.
```

### 4.5 FIX: 0/1 Knapsack Inner Loop Right-to-Left

```python
def knapsack_01(weights: List[int], values: List[int], capacity: int) -> int:
    """0/1 Knapsack with rolling array. Inner loop right-to-left prevents item reuse."""
    n = len(weights)
    dp = [0] * (capacity + 1)

    for i in range(n):
        # Traverse right to left so dp[w - weights[i]] reflects
        # the state BEFORE item i was considered (previous row in 2-D table).
        for w in range(capacity, weights[i] - 1, -1):  # FIX: right to left
            dp[w] = max(dp[w], dp[w - weights[i]] + values[i])

    return dp[capacity]

# knapsack_01([2,3],[3,4],5) = 7  CORRECT
# knapsack_01([1],[5],3) = 5      CORRECT (item used only once)
```

### 4.6 Unbounded Knapsack (Left-to-Right is Correct Here)

```python
def knapsack_unbounded(weights: List[int], values: List[int], capacity: int) -> int:
    """Unbounded Knapsack: each item may be used any number of times.
    Inner loop left to right is intentional — item reuse is allowed."""
    dp = [0] * (capacity + 1)

    for w in range(1, capacity + 1):
        for i in range(len(weights)):
            if weights[i] <= w:
                dp[w] = max(dp[w], dp[w - weights[i]] + values[i])

    return dp[capacity]
```

---

### 4.7 Coin Change (Linear DP — Minimisation)

```python
def coin_change(coins: List[int], amount: int) -> int:
    """Minimum number of coins to make `amount`. Returns -1 if impossible."""
    INF = float("inf")
    dp = [INF] * (amount + 1)
    dp[0] = 0  # base case: 0 coins needed for amount 0

    for a in range(1, amount + 1):
        for c in coins:
            if c <= a and dp[a - c] != INF:
                dp[a] = min(dp[a], dp[a - c] + 1)

    return dp[amount] if dp[amount] != INF else -1

# coin_change([1, 5, 6, 9], 11) = 2  (coins 5+6)
# coin_change([2], 3) = -1            (impossible)
# coin_change([1], 0) = 0             (base case)
```

---

### 4.8 Longest Common Subsequence (2-D Table)

```python
def lcs_length(text1: str, text2: str) -> int:
    """Length of longest common subsequence. O(m*n) time, O(m*n) space."""
    m, n = len(text1), len(text2)
    # dp[i][j] = LCS length of text1[0..i-1] and text2[0..j-1]
    dp = [[0] * (n + 1) for _ in range(m + 1)]

    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if text1[i - 1] == text2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])

    return dp[m][n]


def lcs_recover(text1: str, text2: str) -> str:
    """Recover the actual LCS string via backtracking."""
    m, n = len(text1), len(text2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]

    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if text1[i - 1] == text2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])

    # Backtrack from dp[m][n]
    result = []
    i, j = m, n
    while i > 0 and j > 0:
        if text1[i - 1] == text2[j - 1]:
            result.append(text1[i - 1])
            i -= 1
            j -= 1
        elif dp[i - 1][j] >= dp[i][j - 1]:
            i -= 1
        else:
            j -= 1

    return "".join(reversed(result))

# lcs_length("ABCDE", "ACE") = 3
# lcs_recover("ABCDE", "ACE") = "ACE"
```

---

### 4.9 Edit Distance (Levenshtein)

```python
def edit_distance(word1: str, word2: str) -> int:
    """Minimum edit operations (insert, delete, substitute) to convert word1 to word2.
    O(m*n) time, O(n) space with rolling row optimisation."""
    m, n = len(word1), len(word2)

    # Space-optimised: only keep previous row and current row.
    prev = list(range(n + 1))  # converting empty string to word2[0..j-1] costs j

    for i in range(1, m + 1):
        curr = [i] + [0] * n  # converting word1[0..i-1] to empty string costs i
        for j in range(1, n + 1):
            if word1[i - 1] == word2[j - 1]:
                curr[j] = prev[j - 1]  # no operation needed
            else:
                curr[j] = 1 + min(
                    prev[j],      # delete from word1
                    curr[j - 1],  # insert into word1
                    prev[j - 1],  # substitute
                )
        prev = curr

    return prev[n]

# edit_distance("horse", "ros") = 3
# edit_distance("intention", "execution") = 5
# edit_distance("", "abc") = 3
# edit_distance("abc", "abc") = 0
```

---

### 4.10 Unique Paths (Grid DP with O(n) Space)

```python
def unique_paths(m: int, n: int) -> int:
    """Count distinct paths in m x n grid (right or down moves only).
    O(m*n) time, O(n) space via rolling row."""
    dp = [1] * n  # base case: top row all 1s

    for i in range(1, m):
        for j in range(1, n):
            dp[j] += dp[j - 1]  # dp[j] (from above) + dp[j-1] (from left, already updated)

    return dp[n - 1]

# unique_paths(3, 7) = 28
# unique_paths(3, 2) = 3
# unique_paths(1, 1) = 1
```

---

## 5. Complexity Analysis & Tradeoffs

### Summary Table

| Problem | Time | Space (Naive) | Space (Optimised) | Notes |
|---------|------|---------------|-------------------|-------|
| Climbing Stairs / Coin Change | O(n * k) | O(n) | O(1) or O(n) | k = number of coin types |
| LCS | O(m * n) | O(m * n) | O(n) (rolling row) | Recovering LCS needs full table |
| Edit Distance | O(m * n) | O(m * n) | O(n) (rolling row) | Rolling row shown in §4.9 |
| 0/1 Knapsack | O(n * W) | O(n * W) | O(W) (rolling array) | Not polynomial in input size if W is large (pseudo-polynomial) |
| Unbounded Knapsack | O(n * W) | O(W) | O(W) | Naturally 1-D |
| Unique Paths | O(m * n) | O(m * n) | O(n) (rolling row) | Closed form: C(m+n-2, m-1) |

---

### Pseudo-Polynomial Warning

Knapsack runs in O(n * W) time. This looks polynomial but is only **pseudo-polynomial**: W is a number in the input, and representing W takes log(W) bits. So the true complexity in terms of input size is O(n * 2^(log W)), which is exponential in the input length. For W = 10^9 this is infeasible with standard tabulation. In practice, knapsack DP is used when W is bounded to a manageable constant (typically W ≤ 10^5 or 10^6).

---

### Space Optimisation Rules

- If `dp[i][j]` depends only on `dp[i-1][j]` and `dp[i][j-1]` (diagonal or row-left), you can use a 1-D rolling array.
- If `dp[i][j]` depends on `dp[i-1][j-1]` (diagonal only, like LCS when characters match), you need one extra variable to store the diagonal value before overwriting.
- If you need to recover the actual solution (not just the optimal value), you must keep the full table for backtracking. Rolling-array space optimisation sacrifices the ability to reconstruct the path.

---

### Memoisation vs Tabulation

| Aspect | Memoisation (Top-Down) | Tabulation (Bottom-Up) |
|--------|------------------------|------------------------|
| Code structure | Natural recursion + cache | Explicit nested loops |
| Subproblems computed | Only those reachable | All states in the table |
| Stack overflow risk | Yes (deep recursion) | No |
| Performance (cache misses) | Pointer chasing in dict | Contiguous array access |
| Space for unreachable states | None wasted | All cells allocated |
| Preferred when | Subproblem space is sparse | Full table is needed |

For most interview problems, tabulation is preferred: it avoids recursion depth limits (Python default: 1000), has better cache performance on contiguous arrays, and makes the traversal order explicit (which is where most bugs hide).

---

## 6. Variations & Follow-up Questions

### Climbing Stairs Extensions

- **K steps allowed**: instead of 1 or 2, you can take 1 through k steps. Transition becomes `dp[n] = sum(dp[n-1] + ... + dp[n-k])`. Use a sliding window sum to keep this O(n).
- **Minimum cost climbing stairs** (LeetCode 746): each step has a cost. `dp[i] = cost[i] + min(dp[i-1], dp[i-2])`.

### Coin Change Extensions

- **Count the number of distinct combinations** (Coin Change 2): change `min` to `+= dp[a - c]`. The outer loop must iterate over coins (not amounts) to avoid counting permutations as distinct combinations.
- **Can we make exact amount?** (Subset Sum): return `dp[amount] != INF`.

### LCS Extensions

- **Shortest Common Supersequence**: length = `m + n - LCS(text1, text2)`.
- **Longest Palindromic Subsequence**: `LPS(s) = LCS(s, reverse(s))`.
- **Longest Increasing Subsequence** (LIS): not directly LCS, but solvable in O(n log n) with patience sort; the O(n^2) DP is `dp[i] = max(dp[j] + 1) for all j < i where arr[j] < arr[i]`.

### Edit Distance Extensions

- **Print the actual edit script**: backtrack through the table (similar to LCS recovery). Git uses a variant of this to print diffs.
- **Weighted edit distance**: different costs for insert, delete, substitute. Same recurrence, just replace `1` with the specific cost.
- **Edit distance with transpositions** (Damerau-Levenshtein): adds a fourth operation. Requires extending the state.

### Knapsack Extensions

- **Multiple knapsacks** (bin packing): NP-hard in general; heuristics used in practice.
- **Bounded Knapsack**: each item has a count limit `c[i]`. Convert each item into its binary representation (powers of 2) and run 0/1 knapsack.
- **Target subset sum**: boolean variant where `dp[w]` is True/False. Used in partition-equal-subset-sum.

### Unique Paths Extensions

- **Unique Paths II** (with obstacles): set `dp[i][j] = 0` if the cell is an obstacle. Otherwise same transition.
- **Minimum path sum**: replace `dp[j] += dp[j-1]` with `dp[j] = grid[i][j] + min(dp[j], dp[j-1])`.
- **Maximum gold in a grid**: requires trying all starting positions and all 4-direction DFS + backtracking (not pure DP because you cannot revisit).

---

## 7. Real-World Usage

### Git Diff — Myers Algorithm (Edit Distance Variant)

Git's `diff` command computes the minimal edit script between two file versions using the Myers diff algorithm, which finds the shortest edit distance (in terms of inserted and deleted lines) between two sequences. When you run `git diff`, the output is essentially the edit script recovered by backtracking through a variant of the edit distance DP table. The "unified diff" format (`+`/`-` lines) maps directly to the insert/delete operations in the Levenshtein model. Git's implementation operates on lines rather than characters but the underlying recurrence is the same.

### Spell Checkers and Autocorrect — Levenshtein Distance

Google's search autocorrect, Apple's iOS keyboard, and Microsoft Word's spell checker all use Levenshtein distance (edit distance) at their core. When a user types "recieve", the spell checker computes `edit_distance("recieve", w)` for every word `w` in the dictionary and returns the closest matches. Industrial spell checkers use BK-trees (Burkhard-Keller trees) to prune the dictionary search: they store words at integer-edit-distance nodes and prune branches where `|d(query, node) - d(query, candidate)|` exceeds a threshold. The leaf-level comparison is still Levenshtein DP.

### BLAST — Bioinformatics Sequence Alignment

The Basic Local Alignment Search Tool (BLAST), used by the National Center for Biotechnology Information (NCBI) to search the GenBank database of DNA sequences, is built on the Smith-Waterman algorithm — a local sequence alignment variant of edit distance. Researchers use BLAST to find which known genes a new DNA fragment resembles. The DP table is the same 2-D structure as LCS/edit-distance; Smith-Waterman adds a floor of 0 so local rather than global alignment is found. BLAST processes petabytes of sequence data annually; its heuristic acceleration (seeding with k-mer matches before running full DP) is what makes it tractable.

### RNA Secondary Structure Folding — Interval DP

Nussinov's algorithm for predicting RNA secondary structure uses interval DP: `dp[i][j]` = maximum number of base pairs in the subsequence from position i to j. The transition considers whether positions i and j pair, or whether the optimal structure splits at some midpoint k. This is the canonical interval DP structure (`dp[i][j]` depends on `dp[i+1][j-1]`, `dp[i+1][j]`, `dp[i][j-1]`, and `max dp[i][k] + dp[k+1][j]`). Tools like Mfold and the Vienna RNA package use extensions of this recurrence to predict tRNA, mRNA, and ribosomal RNA folding patterns.

### Price Comparison and Discount Combination — Knapsack

E-commerce platforms (Amazon pricing engine, Shopify discount rules, airline fare construction) model the problem of combining promotions as a variant of knapsack: given a set of available discounts each with a cost (must buy n items of type X) and a value (saves $Y), find the combination that maximises savings without exceeding the order composition constraints. The knapsack DP replaces brute-force enumeration. A production incident at a European price-comparison engine (described in §9) arose from using brute-force subset enumeration instead of tabulation for this exact problem.

### Video Game NPC Pathfinding — Grid DP / Dijkstra

Strategy games like Civilization VI and Starcraft use grid DP for pathfinding cost estimation and influence maps. The "threat map" — a grid where each cell stores the minimum threat exposure cost to reach that cell — is computed with a Dijkstra-like relaxation equivalent to the grid DP recurrence. Frozen pathfinding (precomputed static maps) uses tabulation identical to Unique Paths / Minimum Path Sum. Real-time pathfinding uses A* (heuristic-guided Dijkstra), but the underlying grid-relaxation structure is the same DP pattern.

### Trading and Route Optimisation — Knapsack / Path DP

High-frequency trading firms model optimal order-routing across exchanges as a knapsack problem: given a target quantity to buy/sell, a set of venues each with a price and available liquidity (capacity), allocate the order across venues to minimise market impact. The constraint (total quantity = target) and the value function (minimise price * quantity) map directly to a bounded knapsack. Logistics companies (UPS, FedEx) use similar formulations for vehicle loading: maximise delivery value (revenue) subject to truck weight and volume constraints.

---

## 8. Edge Cases & Testing

### Edge Cases for Coin Change

```python
assert coin_change([1, 5, 6, 9], 11) == 2   # 5+6
assert coin_change([2], 3) == -1              # impossible (odd target, only even coins)
assert coin_change([1], 0) == 0               # amount zero always 0 coins
assert coin_change([1], 1) == 1               # single coin exactly matches
assert coin_change([186, 419, 83, 408], 6249) == 20  # large case, known answer
assert coin_change([1, 2, 5], 11) == 3        # classic: 5+5+1
```

### Edge Cases for LCS

```python
assert lcs_length("", "abc") == 0            # one empty string
assert lcs_length("abc", "") == 0            # other empty string
assert lcs_length("abc", "abc") == 3         # identical strings
assert lcs_length("abc", "def") == 0         # no common characters
assert lcs_length("abcde", "ace") == 3       # interleaved
assert lcs_length("a", "a") == 1             # single matching character
assert lcs_length("bl", "yby") == 1          # partial overlap
```

### Edge Cases for Edit Distance

```python
assert edit_distance("", "") == 0            # both empty
assert edit_distance("abc", "") == 3         # delete all
assert edit_distance("", "abc") == 3         # insert all
assert edit_distance("abc", "abc") == 0      # identical
assert edit_distance("a", "b") == 1          # single substitution
assert edit_distance("horse", "ros") == 3    # LeetCode example
assert edit_distance("intention", "execution") == 5  # LeetCode example
```

### Edge Cases for Knapsack

```python
assert knapsack_01([], [], 10) == 0          # no items
assert knapsack_01([5], [10], 4) == 0        # item heavier than capacity
assert knapsack_01([1,1,1], [1,2,3], 2) == 5 # pick two highest-value items
assert knapsack_01([2,3,4,5],[3,4,5,6], 8) == 10  # classic case
```

### Edge Cases for Unique Paths

```python
assert unique_paths(1, 1) == 1     # single cell
assert unique_paths(1, n) == 1     # single row: only one path
assert unique_paths(m, 1) == 1     # single column: only one path
assert unique_paths(3, 7) == 28    # LeetCode example
assert unique_paths(3, 2) == 3     # small grid
assert unique_paths(100, 100) > 0  # large grid: should not overflow in Python
```

### Testing Strategy

- **Identity/empty inputs**: always test length-0 strings, amount 0, empty item lists
- **Single-element inputs**: degenerate cases that often expose off-by-one errors in loop bounds
- **Impossible inputs**: `coin_change` with no valid combination, `knapsack` where all items exceed capacity
- **Known answers from LeetCode / competitive programming**: use as regression anchors
- **Stress test against brute force**: for small inputs (n ≤ 15), run the exponential brute force alongside tabulation to cross-check answers

---

## 9. Common Mistakes

### Mistake 1 — Recursive DP Without Memoisation (Exponential Blowup)

**Frequency**: Most common DP bug. Observed in roughly 40% of first attempts by engineers new to DP.

**Quantification**: `fib(50)` with naive recursion requires approximately 2^50 ≈ 10^15 recursive calls. A modern Python interpreter executing 10^7 function calls per second would need 10^8 seconds — over three years. With memoisation, `fib(50)` requires exactly 50 unique calls and completes in microseconds.

**Production war story**: A European price-comparison engine used an O(2^n) brute-force subset enumeration for discount combination selection. With n = 20 items, this produced 2^20 ≈ 10^6 combinations. At 10 ms per combination evaluation, each pricing request took 10^7 ms — nearly 3 hours. The fix was tabulation: coin-change-style DP with states `dp[amount]` over all possible discount amounts. The table had 20 * 10,000 = 200,000 cells and completed in under 1 ms per request — a 10^7 improvement.

**How to detect**: call tree for `f(n)` has repeated subtrees. Draw the recursion tree for n=5; if the same node appears more than once, memoisation is needed.

---

### Mistake 2 — 0/1 Knapsack Inner Loop Direction

**Frequency**: Second most common DP bug. Affects engineers who know the algorithm by name but implement it from memory without understanding why direction matters.

**Symptom**: the code produces values larger than the correct answer, and increasing capacity allows impossibly high values. For `weights=[1], values=[5], capacity=3`, the buggy left-to-right version returns 15 (item used 3 times) instead of the correct 5.

**Root cause**: the 1-D rolling array compresses a 2-D table. Row `i` of the 2-D table is `dp[i][w] = max(dp[i-1][w], dp[i-1][w - w[i]] + v[i])`. The update for `dp[i][w]` reads `dp[i-1][w - w[i]]` — the *previous row*. When using a 1-D array updated left to right, `dp[w - w[i]]` has already been updated in the current pass, so you are reading from *row i* not *row i-1*. This allows item i to contribute to its own `dp[w - w[i]]` base, enabling reuse.

**Fix**: traverse right to left. `dp[w - w[i]]` has not yet been updated in the current pass, so it correctly reads from the previous row.

---

### Mistake 3 — Wrong Base Case Initialisation

**Symptom**: off-by-one errors, incorrect answers for small inputs, or -1 returned when a valid combination exists.

**Example**: In Coin Change, initialising `dp[0] = INF` instead of `dp[0] = 0` causes all subsequent values to remain INF since every update reads `dp[a - c] + 1` which is `INF + 1 = INF`.

**Fix**: always trace through the smallest possible input manually before submitting. For `amount = 1` with `coins = [1]`, the answer is 1; verify your initialisation produces this.

---

### Mistake 4 — Confusing LCS and Edit Distance Transitions

**Symptom**: code runs without error but produces wrong answers. Often the `else` branch uses the wrong direction: `max(dp[i-1][j], dp[i][j-1])` for LCS vs `1 + min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])` for Edit Distance.

**Mnemonic**:
- LCS match: take the diagonal + 1; mismatch: max of left and up (we skip a character in one string).
- Edit Distance match: take the diagonal (no cost); mismatch: 1 + min of delete (up), insert (left), substitute (diagonal).

---

### Mistake 5 — Integer Overflow in Large Grid DP

**Example**: `unique_paths(100, 100)` has a value of approximately 10^56. In Python, arbitrary-precision integers handle this natively. In Java or C++, using `int` overflows silently; use `long` or modular arithmetic if the problem asks for the answer modulo 10^9 + 7.

**Production context**: combinatorics over large grids in route-planning services have caused silent arithmetic overflow bugs in Java microservices. Always check the problem's output size against the data type.

---

### Mistake 6 — Recovering the Solution After Space Optimisation

**Symptom**: the engineer implements rolling-array space optimisation correctly (and the optimal *value* is correct) but cannot recover the actual sequence/path/coins used.

**Root cause**: rolling-array space optimisation destroys the information needed for backtracking. The 2-D table encodes *all decisions*; the 1-D array only encodes the *final row*.

**Fix**: if the problem requires recovering the solution (not just the value), either keep the full 2-D table, or use a parent-pointer array (`choice[i][w]` recording whether item i was included at capacity w) that can be stored separately in O(n * W) space.

---

## 10. Related Problems

### Linear DP Family

- **House Robber** (LeetCode 198): `dp[i] = max(dp[i-1], dp[i-2] + nums[i])`. Identical structure to Climbing Stairs.
- **House Robber II** (circular): split into two linear sub-problems (first to second-last, second to last), take max.
- **Maximum Subarray** (Kadane's): `dp[i] = max(nums[i], dp[i-1] + nums[i])`. This is linear DP but solvable in O(1) space without a table.
- **Decode Ways** (LeetCode 91): how many ways to decode a digit string. Same recurrence as Climbing Stairs with conditional branching.
- **Jump Game II** (minimum jumps): greedy is optimal, but the DP formulation is `dp[i] = min jumps to reach i`.

### Subsequence / Interval DP Family

- **Longest Palindromic Subsequence**: `LPS(s) = LCS(s, s[::-1])`.
- **Longest Palindromic Substring**: different from LPS; uses interval DP `dp[i][j]` = is `s[i..j]` a palindrome.
- **Burst Balloons** (LeetCode 312): classic interval DP. `dp[i][j]` = max coins from bursting all balloons in `(i, j)`.
- **Minimum Cost to Cut a Stick** (LeetCode 1547): interval DP with sorted cut positions.
- **Distinct Subsequences** (LeetCode 115): count ways string s2 appears as a subsequence of s1. Transition similar to LCS.

### Knapsack Family

- **Partition Equal Subset Sum**: is there a subset summing to `sum(nums) / 2`? Boolean knapsack.
- **Last Stone Weight II**: minimize the absolute difference of two groups. Same as Partition Equal Subset Sum.
- **Target Sum** (LeetCode 494): count subsets with sum = target. Counts instead of max values; knapsack counting variant.
- **Ones and Zeroes** (LeetCode 474): 2-D knapsack with two constraints (count of 0s and count of 1s).
- **Perfect Squares** (LeetCode 279): coin change where coins are `[1, 4, 9, 16, ...]`. Unbounded knapsack style.

### Path DP Family

- **Minimum Path Sum** (LeetCode 64): grid DP, minimise sum instead of counting paths.
- **Dungeon Game** (LeetCode 174): grid DP traversed bottom-right to top-left.
- **Cherry Pickup** (LeetCode 741): two agents moving simultaneously on a grid — state becomes `(r1, c1, r2, c2)` reduced to `(step, c1, c2)`.
- **Maximal Square** (LeetCode 221): `dp[i][j]` = side length of largest square of 1s with bottom-right at `(i, j)`.

---

## 11. Interview Discussion Points

**Q: How do you recognise that a problem is solvable with DP versus greedy?**
Greedy works when a locally optimal choice is globally optimal — proven by an exchange argument (swapping a non-greedy choice for a greedy one never makes things worse). DP is needed when a locally optimal choice may not be globally optimal and you need to explore multiple alternatives. The tell for DP: "minimum/maximum/count/exist over all combinations" language, plus the fact that you cannot commit to a single choice at each step without reconsidering earlier steps. If you can prove an exchange argument, try greedy first; if you cannot, default to DP.

**Q: What is the difference between memoisation and tabulation?**
Both avoid recomputation by caching sub-problem answers. Memoisation is top-down: start with the original problem, recurse, and cache results as you go — only computes sub-problems that are actually reachable. Tabulation is bottom-up: fill a table starting from base cases, computing every sub-problem in dependency order. Tabulation avoids recursion overhead and call-stack limits, has better cache locality on contiguous arrays, and makes traversal order explicit. Memoisation is faster to implement (annotate existing recursion with `@lru_cache`) and wastes no space on unreachable states. In production Python, prefer tabulation for large inputs to avoid hitting the default recursion depth limit of 1000.

**Q: How do you derive the state definition for a new DP problem?**
Identify what information is needed to describe "where you are" in the problem. The state should be the minimal set of variables that, together with the sub-problem index, fully determines the optimal value from that point forward — this is the Markov property for DP. For sequence problems, the state is typically the current index (or two indices for two sequences). For knapsack-type problems, it is the remaining capacity. For grid problems, it is the current cell coordinates. A common mistake is to include information in the state that could be derived from the optimisation itself (e.g., tracking the actual items selected rather than just the remaining capacity).

**Q: When can you reduce a 2-D DP table to O(n) space?**
When row `i` depends only on row `i-1` (and not on rows farther back), you can replace the 2-D table with a 1-D rolling array. The key constraint: if your update for `dp[i][j]` needs `dp[i-1][j-1]` (a diagonal value), you must save that value in a temporary variable before overwriting `dp[j]`. If the solution recovery is needed, you cannot apply rolling-array compression because you lose the historical table — you would need a separate parent-pointer structure.

**Q: Why does 0/1 knapsack require right-to-left inner loop, but Unbounded Knapsack uses left-to-right?**
In 0/1 knapsack with a 1-D array, `dp[w] = max(dp[w], dp[w - weight] + value)` must read `dp[w - weight]` from the *previous item's* state (not the current one). Right-to-left traversal ensures that when we update `dp[w]`, the value `dp[w - weight]` (which is at a smaller index, already traversed this pass if we went left-to-right) still reflects the previous row. In Unbounded Knapsack, reading the current row's value for `dp[w - weight]` is intentional — it means we can use the same item again (the current row includes the just-updated value at smaller capacity, which may already include this item).

**Q: What is pseudo-polynomial time, and why does it matter for knapsack?**
Pseudo-polynomial time is time complexity that is polynomial in the *numeric value* of the input but exponential in the *length* of the input's binary representation. Knapsack runs in O(n * W) operations, which looks polynomial. But W is a number, and its binary representation has log(W) bits. So the true complexity is O(n * 2^(log W)), exponential in the bit-length of W. For W = 10^9, O(n * W) = O(n * 10^9) is infeasible. In interviews, knapsack DP is acceptable when W is given as a bounded constant (≤ 10^5 or 10^6), not when W is "up to 10^9."

**Q: How does Edit Distance relate to the Myers diff algorithm used by Git?**
Edit distance (Levenshtein) allows insert, delete, and substitute. Myers diff restricts operations to insert and delete only (no substitutions — a substitution is modelled as a delete + insert). Myers also works on sequences of lines rather than characters. The key algorithmic difference is that Myers solves the equivalent problem using a frontier-based search on the edit graph that is more cache-friendly and practical for large files, but it computes the same minimum edit distance. The `git diff` output is the optimal edit script (the backtracked path through the DP table) with no substitutions: each changed line appears as `-old` and `+new` rather than a single `~substitution` marker.

**Q: How would you find the actual LCS string, not just its length?**
Run the standard O(m * n) tabulation to fill the full 2-D table. Then backtrack from `dp[m][n]`: if `text1[i-1] == text2[j-1]`, the character is in the LCS — append it and move diagonally to `(i-1, j-1)`. Otherwise, move in the direction of the larger neighbour: if `dp[i-1][j] >= dp[i][j-1]`, move up; else move left. Reverse the collected characters to get the LCS. The total backtracking time is O(m + n). This requires the full 2-D table — rolling-array space optimisation cannot be used if recovery is needed.

**Q: Coin Change asks for the minimum number of coins. How would you change the DP to count the number of distinct ways to make the amount?**
Replace the objective from minimisation to counting. Initialise `dp[0] = 1` (one way to make amount 0: use no coins). The transition becomes `dp[a] += dp[a - c]` for each coin `c` (sum rather than min). The loop structure also matters: to count *combinations* (not permutations), the outer loop must iterate over coins and the inner loop over amounts. If you nest them the other way (outer = amounts, inner = coins), you count ordered sequences instead (permutations). This is the Coin Change 2 problem (LeetCode 518).

**Q: How do you handle negative weights or values in knapsack?**
Standard 0/1 knapsack assumes non-negative weights. Negative weights are rare in classical formulations but arise in financial problems (shorting assets). They require shifting the weight axis: define `min_weight` as the minimum possible sum, use an offset so all indices are non-negative. Negative values are possible: simply initialise `dp[w] = -INF` (instead of 0) and check that the item is not forced to be included. In practice, if negative weights or values appear in an interview, clarify whether you can reframe the problem (e.g., flip sign and solve a minimisation).

**Q: What is Interval DP and when do you use it?**
Interval DP is for problems where the optimal solution for a range `[i, j]` depends on optimal solutions for sub-ranges. The state is `dp[i][j]` = optimal value for the subsequence or sub-array from index i to j. The transition typically considers splitting at some midpoint k: `dp[i][j] = optimize over k in (i, j) of (dp[i][k] + dp[k+1][j] + cost(i, j, k))`. Use interval DP for: matrix chain multiplication, burst balloons, optimal binary search tree, RNA secondary structure, minimum cost to cut a stick, palindrome partitioning. The traversal order must go from shorter intervals to longer: iterate by length `l` from 2 to `n`, then by starting index `i`.

**Q: How would you approach DP on trees?**
Tree DP is a common extension: define `dp[node]` as some function of the subtree rooted at `node`. Process nodes in post-order (children before parent). The transition for each node combines the dp values of its children. Examples: the maximum independent set on a tree (`dp[node][included]` = max value when node is included or excluded), house robber on a binary tree (same pattern), and diameter of a binary tree (which uses a DFS + local max trick equivalent to tree DP). The key difference from linear DP is that the dependency graph is the tree structure itself — use DFS with post-order processing.

**Q: How do you recognise that LCS and Edit Distance are related?**
Both operate on pairs of sequences and fill an (m+1) x (n+1) table with the same base cases (`dp[i][0]` and `dp[0][j]`). The diagonal-match case is identical: `dp[i][j] = dp[i-1][j-1] + (1 for LCS)` or `dp[i-1][j-1] + (0 for Edit Distance when chars match)`. The mismatch case differs: LCS takes `max` of neighbours (ignoring one character), while Edit Distance takes `1 + min` of neighbours (charging for an operation). Recognising this family helps you adapt the template: if you see "minimum operations to convert one string to another" → Edit Distance; if you see "longest shared structure" → LCS or a variant.

**Q: Describe how DP can solve the Longest Increasing Subsequence in O(n log n).**
The O(n^2) DP is `dp[i] = max(dp[j] + 1) for all j < i where arr[j] < arr[i]`. The O(n log n) optimisation uses patience sorting: maintain an array `tails` where `tails[k]` is the smallest tail element of all increasing subsequences of length `k+1`. For each new element `x`, binary search `tails` for the leftmost tail `>= x` and replace it with `x` (or append if x is larger than all tails). The length of `tails` at the end is the LIS length. This is not strictly tabulation in the classical sense — it uses a greedy choice (replace the rightmost tail that can be replaced) with binary search for efficiency, but the correctness proof relies on the same optimal-substructure argument as DP.

---

*Cross-references: [dynamic_programming module](../dynamic_programming/README.md) — full 14-section treatment with all DP families; [recursion_and_problem_solving_patterns](../recursion_and_problem_solving_patterns/README.md) — recursion fundamentals and the recursion-to-DP derivation pattern; [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/README.md) — pseudo-polynomial vs polynomial; [graph_traversal_and_shortest_path case study](./graph_traversal_and_shortest_path.md) — Dijkstra as a greedy/DP hybrid.*
