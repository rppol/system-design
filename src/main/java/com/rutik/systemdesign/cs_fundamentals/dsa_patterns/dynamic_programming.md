# Dynamic Programming (Knapsack, LIS, LCS, Grid, Interval, State-Machine)

## Pattern Snapshot

**What it is**: Solve a problem by breaking it into **overlapping
subproblems**, solving each one **once**, and caching ("memoizing") the
result. Two implementations of the same idea: **top-down** (recursion +
memo) and **bottom-up** (iterative table-filling, "tabulation").

**One-line cue**: "Number of ways to..." / "minimum/maximum cost to..." /
"can you reach/partition..." — combined with **choices** at each step whose
subproblems **recur** regardless of the order choices were made.

**Typical complexity**: Polynomial — `O(n)`, `O(n*W)`, `O(n^2)`, or `O(n^3)`
— where naive recursion would be exponential.

---

## 1. Recognition Signals

**Use dynamic programming when you see:**
- "Number of distinct ways to..." (climbing stairs, decode ways, coin change
  II)
- "Minimum/maximum cost/sum/length to..." (coin change, min path sum,
  longest increasing subsequence)
- "Can you partition/reach/form..." (partition equal subset sum, word break,
  target sum)
- Two strings/sequences being compared element-by-element (longest common
  subsequence, edit distance)
- A sequence of decisions where **today's optimal choice depends on
  yesterday's state** (stock buy/sell with cooldown)
- The brute-force recursive solution, when you draw its call tree, has the
  **same `(state)` arguments appearing in multiple branches** — that's
  "overlapping subproblems," the green light for memoization

**Anti-signals (looks similar, use a different pattern):**
- "Generate/return **all** ways" (not count or optimum) ->
  [`backtracking.md`](backtracking.md) — though DP can sometimes *prune* a
  backtracking search
- At each step, the choice that looks best **right now** is *provably*
  optimal overall (exchange-argument provable) ->
  [`greedy.md`](greedy.md) — simpler and faster when it applies
- Pure cumulative/prefix computation with **no choices** ->
  [`prefix_sum.md`](prefix_sum.md)
- Shortest/longest path on a **DAG** (no cycles) — topological order + DP is
  a special, more efficient case -> [`topological_sort.md`](topological_sort.md) §6

---

## 2. Mental Model & Intuition

The classic illustration: naive recursive Fibonacci recomputes `fib(2)` and
`fib(1)` many times. DP computes each distinct `(state)` exactly once.

```
Naive recursion tree for fib(5) -- note fib(2) computed THREE times:

                    fib(5)
                  /        \
             fib(4)          fib(3)
            /     \          /     \
        fib(3)   fib(2)  fib(2)   fib(1)
        /   \     /  \    /  \
   fib(2) fib(1) fib(1)fib(0) fib(1)fib(0)
    /  \
fib(1)fib(0)

With memoization: each fib(k) is computed ONCE, cached, and reused --
the tree COLLAPSES into a chain of 6 distinct subproblems: fib(0)..fib(5).

dp[0]=0, dp[1]=1, dp[2]=dp[1]+dp[0]=1, dp[3]=dp[2]+dp[1]=2,
dp[4]=dp[3]+dp[2]=3, dp[5]=dp[4]+dp[3]=5
```

**The DP recipe**: (1) define `dp[state]` = the answer to the subproblem
identified by `state`; (2) write the **recurrence** — `dp[state]` in terms of
`dp[smaller states]`; (3) identify **base cases**; (4) decide **order of
evaluation** (top-down via recursion+memo, or bottom-up via iteration in an
order that guarantees dependencies are ready).

---

## 3. The Template

```python
from __future__ import annotations
from functools import lru_cache
import bisect

# ---------------------------------------------------------------------------
# Template 1: Top-down (memoization) vs Bottom-up (tabulation) -- same problem
# ---------------------------------------------------------------------------
def fib_memo(n: int) -> int:
    @lru_cache(maxsize=None)
    def helper(k: int) -> int:
        if k <= 1:
            return k
        return helper(k - 1) + helper(k - 2)
    return helper(n)

def fib_tabulation(n: int) -> int:
    if n <= 1:
        return n
    dp = [0] * (n + 1)
    dp[1] = 1
    for i in range(2, n + 1):
        dp[i] = dp[i - 1] + dp[i - 2]
    return dp[n]


# ---------------------------------------------------------------------------
# Template 2: Unbounded knapsack -- Coin Change (minimum coins)
# ---------------------------------------------------------------------------
def coin_change(coins: list[int], amount: int) -> int:
    dp = [0] + [float("inf")] * amount  # dp[0]=0; everything else "unreached"
    for i in range(1, amount + 1):
        for coin in coins:
            if coin <= i:
                dp[i] = min(dp[i], dp[i - coin] + 1)
    return dp[amount] if dp[amount] != float("inf") else -1


# ---------------------------------------------------------------------------
# Template 3: 0/1 knapsack -- Partition Equal Subset Sum (each item once)
# ---------------------------------------------------------------------------
def can_partition(nums: list[int]) -> bool:
    total = sum(nums)
    if total % 2 != 0:
        return False
    target = total // 2
    dp = [False] * (target + 1)
    dp[0] = True
    for num in nums:
        # iterate target DOWNWARDS -- ensures each num used at most once
        for t in range(target, num - 1, -1):
            dp[t] = dp[t] or dp[t - num]
    return dp[target]


# ---------------------------------------------------------------------------
# Template 4: LCS (2D DP) -- Longest Common Subsequence
# ---------------------------------------------------------------------------
def longest_common_subsequence(text1: str, text2: str) -> int:
    m, n = len(text1), len(text2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if text1[i - 1] == text2[j - 1]:
                dp[i][j] = dp[i - 1][j - 1] + 1
            else:
                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
    return dp[m][n]


# ---------------------------------------------------------------------------
# Template 5: LIS -- O(n log n) via patience-sorting / binary search
# ---------------------------------------------------------------------------
def length_of_lis(nums: list[int]) -> int:
    tails: list[int] = []  # tails[k] = smallest possible tail of an
                            # increasing subsequence of length k+1
    for num in nums:
        pos = bisect.bisect_left(tails, num)
        if pos == len(tails):
            tails.append(num)
        else:
            tails[pos] = num
    return len(tails)
```

---

## 4. Annotated Walkthrough

**Problem**: [Coin Change (LC 322)](https://leetcode.com/problems/coin-change/)
`coins = [1, 2, 5]`, `amount = 11`. Find the minimum number of coins to make
`11`, or `-1` if impossible.

**Setup**: `dp[i]` = minimum coins to make amount `i`. `dp[0] = 0`
(zero coins needed for amount 0); all other `dp[i]` start at `inf`
("not yet reachable").

**Trace** (abbreviated — showing key transitions):

```
dp = [0, inf, inf, inf, inf, inf, inf, inf, inf, inf, inf, inf]  (indices 0-11)

i=1: coin=1: dp[1]=min(inf, dp[0]+1)=min(inf,1)=1
     coin=2,5: i-coin < 0, skip
     dp[1] = 1

i=2: coin=1: dp[2]=min(inf, dp[1]+1)=min(inf,2)=2
     coin=2: dp[2]=min(2, dp[0]+1)=min(2,1)=1
     coin=5: skip
     dp[2] = 1

i=3: coin=1: dp[3]=min(inf, dp[2]+1)=min(inf,2)=2
     coin=2: dp[3]=min(2, dp[1]+1)=min(2,2)=2
     coin=5: skip
     dp[3] = 2

i=4: coin=1: dp[4]=min(inf,dp[3]+1)=3
     coin=2: dp[4]=min(3,dp[2]+1)=min(3,2)=2
     dp[4] = 2

i=5: coin=1: dp[5]=min(inf,dp[4]+1)=3
     coin=2: dp[5]=min(3,dp[3]+1)=min(3,3)=3
     coin=5: dp[5]=min(3,dp[0]+1)=min(3,1)=1
     dp[5] = 1

... (i=6..10 follow the same pattern) ...

i=11: coin=1: dp[11]=min(inf, dp[10]+1)
      coin=2: dp[11]=min(..., dp[9]+1)
      coin=5: dp[11]=min(..., dp[6]+1)
      Working through the table: dp[6]=2 (5+1), so dp[6]+1=3
      dp[11] = 3   (corresponds to 5 + 5 + 1)

Final: dp[11] = 3
```

`dp[11] = 3` because `5 + 5 + 1 = 11` uses 3 coins — and no combination of
`{1, 2, 5}` reaches 11 in fewer than 3 coins.

---

## 5. Complexity

| Template | Time | Space | Notes |
|---|---|---|---|
| Fibonacci (memo or tabulation) | O(n) | O(n) (or O(1) with two rolling variables) | Each state computed once |
| Coin Change (unbounded knapsack) | O(amount * len(coins)) | O(amount) | 1D DP, each amount tries every coin |
| Partition Equal Subset Sum (0/1 knapsack) | O(n * target) | O(target) with reverse iteration | Reverse iteration over `t` is what makes it "0/1" (see §6) |
| LCS / Edit Distance (2D DP) | O(m * n) | O(m * n), or O(min(m,n)) with rolling rows | Each cell depends only on the row above and current row |
| LIS (O(n log n)) | O(n log n) | O(n) | `tails` array + binary search; see §11 for why it works |

---

## 6. Variations & Sub-patterns

This is less a single algorithm than a **family** of recurrence shapes.
Recognizing which family a problem belongs to is most of the battle:

**0/1 Knapsack** (each item used **at most once**) — Partition Equal Subset
Sum, Target Sum, Last Stone Weight II. The hallmark implementation detail:
when space-optimizing to 1D, **iterate the capacity dimension downward**
(`for t in range(target, num-1, -1)`) — this ensures `dp[t - num]` still
refers to *last iteration's* (pre-this-item) value, simulating "haven't used
this item yet for this capacity."

**Unbounded Knapsack** (each item reusable) — Coin Change, Coin Change II,
Perfect Squares. Iterate the capacity dimension **upward** — `dp[t - num]`
*can* already reflect this item being used (that's the point: unlimited
supply).

**LIS family** — Longest Increasing Subsequence (`O(n^2)` DP: `dp[i] = 1 +
max(dp[j] for j < i if nums[j] < nums[i])`, or `O(n log n)` patience
sorting), Russian Doll Envelopes (2D LIS — sort by one dimension, LIS on the
other), Number of LIS (track counts alongside lengths).

**LCS family** — Longest Common Subsequence, Edit Distance (insert/
delete/replace = three transitions instead of LCS's two), Longest Palindromic
Subsequence (LCS of a string with its reverse).

**Grid DP** — Unique Paths, Minimum Path Sum, Dungeon Game. `dp[r][c]`
depends on `dp[r-1][c]` and `dp[r][c-1]` (or vice-versa for "minimum HP
needed," computed backward from the destination).

**Interval DP** — Matrix Chain Multiplication, Burst Balloons, Palindrome
Partitioning II. `dp[i][j]` = answer for the subarray/substring `[i, j]`,
computed from `dp[i][k]` and `dp[k+1][j]` for all `k` in between. **The outer
loop must iterate over interval LENGTH**, not start index — `dp[i][j]`
depends on *shorter* intervals, which must already be computed regardless of
where they start.

**State-machine DP** — Best Time to Buy and Sell Stock (with cooldown / fee /
at most k transactions). State = `(day, holding_or_not, ...)`; transitions =
"buy," "sell," "hold," "cooldown." Drawing the state machine explicitly
(states as nodes, transitions as edges with costs) makes the recurrence
almost mechanical to write down.

**Bitmask DP** — Traveling Salesman, assignment problems, "minimum cost to
visit all of these `n` items" where `n <= ~20`. State =
`(bitmask_of_visited, current_position)`; `2^n * n` states, each with `O(n)`
transitions = `O(2^n * n^2)`. See [`bit_manipulation.md`](bit_manipulation.md)
for bitmask mechanics.

For a fully worked, line-by-line treatment of several of these families,
see [`../case_studies/dynamic_programming_patterns.md`](../case_studies/dynamic_programming_patterns.md)
— the deep companion to this playbook.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue/twist |
|---|---|---|---|
| [Climbing Stairs (LC 70)](https://leetcode.com/problems/climbing-stairs/) | Easy | Fibonacci-shaped | `dp[i] = dp[i-1] + dp[i-2]` |
| [House Robber (LC 198)](https://leetcode.com/problems/house-robber/) | Medium | Take-or-skip | `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` |
| [Coin Change (LC 322)](https://leetcode.com/problems/coin-change/) | Medium | Unbounded knapsack, minimize | The signature problem — see §4 |
| [Coin Change II (LC 518)](https://leetcode.com/problems/coin-change-ii/) | Medium | Unbounded knapsack, count combinations | Loop order matters — see §11 |
| [Partition Equal Subset Sum (LC 416)](https://leetcode.com/problems/partition-equal-subset-sum/) | Medium | 0/1 knapsack, boolean | Reverse iteration — see §6 |
| [Longest Increasing Subsequence (LC 300)](https://leetcode.com/problems/longest-increasing-subsequence/) | Medium | LIS | O(n^2) DP or O(n log n) patience sorting |
| [Longest Common Subsequence (LC 1143)](https://leetcode.com/problems/longest-common-subsequence/) | Medium | LCS, 2D | Two-string DP grid |
| [Edit Distance (LC 72)](https://leetcode.com/problems/edit-distance/) | Hard | LCS variant, 3 transitions | Insert/delete/replace |
| [Minimum Path Sum (LC 64)](https://leetcode.com/problems/minimum-path-sum/) | Medium | Grid DP | `dp[r][c] = grid[r][c] + min(dp[r-1][c], dp[r][c-1])` |
| [Longest Palindromic Substring (LC 5)](https://leetcode.com/problems/longest-palindromic-substring/) | Medium | Interval DP | `dp[i][j] = dp[i+1][j-1] and s[i]==s[j]` |
| [Burst Balloons (LC 312)](https://leetcode.com/problems/burst-balloons/) | Hard | Interval DP, "last to burst" | Think backward: which balloon is burst LAST in `[i,j]`? |
| [Best Time to Buy and Sell Stock with Cooldown (LC 309)](https://leetcode.com/problems/best-time-to-buy-and-sell-stock-with-cooldown/) | Medium | State-machine DP | States: held / sold / cooldown |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake**: initializing a "minimize" DP array with `0` instead of
`infinity`. For Coin Change, `dp[i] = min(dp[i], dp[i-coin]+1)` against a
`dp[i]` that starts at `0` can never improve — `min(0, anything+1)` is
**always** `0`, since `anything + 1 >= 1 > 0`. The DP table never updates
from its initial state.

```python
# BROKEN: dp initialized to all zeros instead of infinity
def coin_change_broken(coins, amount):
    dp = [0] * (amount + 1)  # BUG: should be inf for i > 0
    for i in range(1, amount + 1):
        for coin in coins:
            if coin <= i:
                dp[i] = min(dp[i], dp[i - coin] + 1)
    return dp[amount] if dp[amount] != 0 else -1  # also wrong: dp[0]==0 is valid!
```

**Trace the bug** on `coins = [1, 2, 5]`, `amount = 11`:

```
dp = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]  (12 zeros)

i=1: coin=1: dp[1] = min(dp[1], dp[0]+1) = min(0, 0+1) = min(0, 1) = 0
     coin=2,5: skip (coin > i)
     dp[1] stays 0   <- WRONG, should become 1

i=2: coin=1: dp[2] = min(dp[2], dp[1]+1) = min(0, 0+1) = min(0,1) = 0
     coin=2: dp[2] = min(0, dp[0]+1) = min(0,1) = 0
     dp[2] stays 0   <- WRONG, should become 1

... every dp[i] for i=1..11 stays 0, because min(0, X) == 0 for any X >= 0.

Final: dp[11] = 0
return: dp[amount] != 0 is False -> returns -1 (WRONG -- answer should be 3)
```

`min(dp[i], dp[i-coin]+1)` can never *increase* `dp[i]` from `0` to anything
positive — `0` looks like "already optimal" to `min()`, even though `0`
actually means "uninitialized," not "zero coins needed."

**Fix**: initialize `dp[0] = 0` (correct — zero coins for amount zero) and
`dp[i] = infinity` for `i > 0` ("not yet known to be reachable"). Check
`dp[amount] != infinity` (not `!= 0`) to detect unreachability.

```python
# FIXED: dp[0]=0 (valid base case), dp[1..amount]=inf ("unreached")
def coin_change_fixed(coins, amount):
    dp = [0] + [float("inf")] * amount
    for i in range(1, amount + 1):
        for coin in coins:
            if coin <= i:
                dp[i] = min(dp[i], dp[i - coin] + 1)
    return dp[amount] if dp[amount] != float("inf") else -1
```

**Re-trace with the fix** on the same input:

```
dp = [0, inf, inf, inf, inf, inf, inf, inf, inf, inf, inf, inf]

i=1: coin=1: dp[1] = min(inf, dp[0]+1) = min(inf, 1) = 1     <- correctly becomes 1
i=2: coin=1: dp[2] = min(inf, dp[1]+1) = min(inf, 2) = 2
     coin=2: dp[2] = min(2, dp[0]+1) = min(2, 1) = 1          <- correctly becomes 1
...
i=5: coin=5: dp[5] = min(dp[5], dp[0]+1) = min(prior, 1) = 1
i=10: coin=5: dp[10] = min(dp[10], dp[5]+1) = min(prior, 2) = 2
i=11: coin=1: dp[11] candidate = dp[10]+1 = 3
      coin=5: dp[11] candidate = dp[6]+1 -- dp[6] = 2 (5+1) -> candidate = 3
      dp[11] = 3

Final: dp[11] = 3 (CORRECT -- 5+5+1)
```

The general lesson: **the initial value of a DP array encodes "not yet
computed" — for `min`-DP that must be `+infinity` (so any real value
improves it); for `max`-DP that must be `-infinity`; for boolean
"reachability" DP, `False`.** Using `0` as a sentinel only works if `0` is
never a *valid* answer — and for "minimum number of coins," `0` is a valid
answer (for `amount=0`), making it a uniquely bad sentinel here.

---

## 9. Related Patterns & When to Switch

- **[`recursion_and_problem_solving_patterns`](../recursion_and_problem_solving_patterns/README.md)** —
  DP IS memoized recursion. If you can write the brute-force recursive
  solution and identify its repeated `(state)` arguments, you've already
  done 80% of the DP work — the rest is adding a cache (top-down) or
  reordering into a loop (bottom-up).
- **[`backtracking.md`](backtracking.md)** — "count the number of ways" (DP)
  vs. "list all the ways" (backtracking) often share the *exact same*
  recursive structure — the difference is `return sum(...)` / `return
  max(...)` (DP, memoizable) vs. `result.append(path[:])` (backtracking, not
  memoizable because the *path itself* is part of the state).
- **[`greedy.md`](greedy.md)** — if a problem has optimal substructure AND
  the locally-best choice is *provably* always part of *some* global optimum
  (the "greedy choice property"), greedy solves it in less time with no
  table. DP is the fallback when you *can't* prove that property — you keep
  all options open and let the recurrence decide.
- **[`topological_sort.md`](topological_sort.md)** §6 — shortest/longest
  path in a DAG is DP where the "order of evaluation" is exactly the
  topological order — a clean illustration of "bottom-up DP needs a
  dependency-respecting iteration order."

---

## 10. Cross-links

- Concept module: [`dynamic_programming/`](../dynamic_programming/README.md) —
  formal recurrence derivations, state-design heuristics
- Case study: [`../case_studies/dynamic_programming_patterns.md`](../case_studies/dynamic_programming_patterns.md) —
  the 900-1100 line deep worked-example companion to this playbook
- Applied: [`../../database/sql_query_optimization/`](../../database/sql_query_optimization/README.md) —
  query planners use DP to choose join order (the optimal order to join `n`
  tables is itself an interval-DP-shaped problem over subsets of tables)

---

## 11. Interview Q&A

**Top-down (memoization) vs. bottom-up (tabulation) — how do you choose?**
Top-down (recursion + `@lru_cache` or a dict) is often easier to *write*
directly from the recurrence — it mirrors the brute-force solution with one
line added. Bottom-up (iterative table-filling) avoids recursion-depth limits
and has no per-call overhead, and makes space optimization (collapsing a 2D
table to two 1D rows, or one row) more natural. In interviews, starting
top-down to *get the recurrence right*, then converting to bottom-up if asked
about stack depth or space, is a strong demonstration of understanding.

**How do you systematically identify "optimal substructure" and "overlapping
subproblems" in a new problem?**
Write the brute-force recursive solution first: "to solve for `state X`, what
smaller states do I need answers to, and how do I combine them?" That
combination step IS the optimal substructure (the answer to `X` is built from
optimal answers to smaller states — not from re-deriving them). Then look at
the *arguments* to your recursive calls across different branches: if
`solve(5)` gets called from both `solve(7)` and `solve(8)` (e.g.,
`solve(7) -> solve(5)` via one path, `solve(8) -> solve(5)` via another),
that's an overlapping subproblem — memoize on those arguments.

**Walk through why `dp = [0] * (amount+1)` is wrong for Coin Change but
RIGHT for, say, "number of ways" DP (Coin Change II)?**
For **minimize** DP (Coin Change), `0` looks like "already the best possible
value" to `min()`, so nothing can ever improve it — `0` must mean "not yet
computed," requiring `+infinity`. For **count/sum** DP (Coin Change II,
"number of ways"), `0` correctly means "zero ways found so far" — and
`dp[i] += dp[i - coin]` (addition, not `min`) correctly *accumulates* from
that `0` baseline. The right sentinel value depends on the recurrence's
combining operator: `min`/`max` need `+inf`/`-inf`; `+=`/`or` need `0`/`False`.

**0/1 knapsack vs. unbounded knapsack — why does the capacity-loop DIRECTION
matter when space-optimizing to 1D?**
In 2D, `dp[i][t]` (using items `0..i`) depends on `dp[i-1][t - weight]`
(previous item's row). Collapsing to 1D `dp[t]`, you need `dp[t - weight]` to
still hold the *previous item's* value when computing the *current* item's
`dp[t]`. Iterating `t` **downward** (high to low) ensures `dp[t-weight]`
(a *smaller* index) hasn't been overwritten yet *for this item* — giving 0/1
behavior (each item considered once per capacity). Iterating **upward**
means `dp[t-weight]` may already reflect *this item* having been used —
giving unbounded behavior (item reusable).

**For LIS, what's the key insight that makes the O(n log n) `tails` array
approach correct — it doesn't even look like it's tracking actual
subsequences?**
`tails[k]` = the **smallest possible tail value** among all increasing
subsequences of length `k+1` seen so far. Crucially, `tails` is always sorted
— a longer increasing subsequence's tail can't be smaller than a shorter
one's *smallest possible* tail (if it were, the longer one's prefix would
itself be a shorter subsequence with an even smaller tail, contradiction).
This sortedness is what makes binary search (`bisect_left`) valid: for each
new number, binary search finds where it *would* extend or improve some
existing subsequence — `len(tails)` at the end is the LIS length, even though
`tails` itself is generally NOT a valid subsequence of the input.

**How do you reconstruct the actual sequence of choices (not just the
optimal value) from a DP table?**
Either (1) store a `parent`/`choice` table alongside `dp`, recording *which*
transition achieved `dp[state]`'s value, then walk backward from the final
state following `parent` pointers; or (2) re-derive it by walking the `dp`
table from the end, at each step checking *which* recurrence term equals the
current `dp` value (e.g., for LCS, check if `dp[i][j] == dp[i-1][j-1]+1`
implies a "match" move diagonally, else move toward whichever of
`dp[i-1][j]`/`dp[i][j-1]` equals `dp[i][j]`).

**State-machine DP for stock problems — how do you derive the recurrence
without memorizing it?**
Explicitly enumerate the **states** (e.g., "holding a share," "just sold
[in cooldown]," "not holding, free to buy") and the **transitions** between
them with their costs/gains. For "Buy/Sell with Cooldown": `held[i] =
max(held[i-1], free[i-1] - price[i])` (keep holding, or buy today from a
free state); `sold[i] = held[i-1] + price[i]` (sell today); `free[i] =
max(free[i-1], sold[i-1])` (stay free, or cooldown ends). Drawing this as an
actual state diagram with labeled edges turns "memorize the formula" into
"read off the formula from the diagram."

**Interval DP — why must the outer loop iterate over interval LENGTH rather
than start index?**
`dp[i][j]` (interval `[i,j]`) is computed from `dp[i][k]` and `dp[k+1][j]`
for `i <= k < j` — both of which are **shorter** intervals than `[i,j]`. If
the outer loop iterated over `i` (start index) with `j` ranging freely, when
processing `dp[i][j]` for a large `j`, the needed `dp[k+1][j]` (same end,
smaller start `k+1 > i`) might not be computed yet (it has a *larger* start
index, processed *later* in an `i`-ordered loop). Iterating by **length**
guarantees every dependency (strictly shorter interval) is already filled in.

**When does greedy work where DP would also "work" — how do you decide which
to reach for first?**
Try to articulate an **exchange argument**: "if an optimal solution did NOT
make the greedy choice at this step, could I modify it to make the greedy
choice without making it worse?" If you can prove this for every step, greedy
is correct and you should use it (faster, simpler). If you find a
counterexample where the locally-best choice forecloses a better global
option, you need DP (or backtracking) to keep multiple options alive. In an
interview, if you're not sure, mention both: "greedy MIGHT work here if X
holds — let me check with a small counterexample; if it fails, here's the DP
fallback."

**Coin Change (minimize, LC 322) vs. Coin Change II (count combinations, LC
518) — why does the loop order (coins outer vs. amount outer) matter for the
LATTER but the former works either way?**
For **counting combinations** (order doesn't matter — `{1,2}` and `{2,1}`
are the same combination), the outer loop must be over **coins**: this
ensures each coin is "added to the mix" before amounts are computed using it,
preventing the same combination from being counted in different *orders* as
distinct. For **minimize** (Coin Change), `min()` is commutative and
order-independent — trying coins in any order for a given amount yields the
same minimum, so loop order doesn't change correctness (only minor cache
behavior). Swapping the loops in Coin Change II would instead count
*permutations* (ordered sequences) — a different (also valid, but different)
problem, [Combination Sum IV (LC 377)](https://leetcode.com/problems/combination-sum-iv/).

**Bitmask DP — when is `O(2^n * n^2)` actually a reasonable complexity to
propose, and what's the giveaway in the problem statement?**
When `n <= ~18-20` (so `2^20 ~ 10^6`, times `n^2 ~ 400` is `~4*10^8` —
borderline but often acceptable with simple per-state work), AND the problem
involves "visit all of these `n` items" with order/subset mattering
(Traveling Salesman, "minimum cost to assign `n` workers to `n` tasks"). The
state `(mask, last_item)` — "which subset have I used, and what was the most
recent choice" — captures everything needed for the recurrence, with `2^n`
possible masks times `n` possible "last items."
