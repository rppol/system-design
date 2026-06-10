# Backtracking (Subsets, Permutations, Constraint Search)

## Pattern Snapshot

**What it is**: Recursive search that builds a candidate solution
incrementally — make a choice, recurse, then **undo** the choice (backtrack)
before trying the next one. The "undo" is what distinguishes backtracking
from plain DFS: it ensures shared state is restored so sibling branches start
from a clean slate.

**One-line cue**: "Generate all subsets / permutations / combinations" or
"find all valid arrangements satisfying constraints" (N-Queens, Sudoku).

**Typical complexity**: Exponential or factorial — `O(2^n)` for subsets,
`O(n!)` for permutations. Only tractable because problem constraints are
small (`n <= 12-20`).

---

## 1. Recognition Signals

**Use backtracking when you see:**
- "Return **all** possible subsets / permutations / combinations"
- "Generate all valid ___" (parentheses, IP addresses, palindrome
  partitions)
- "Find all paths from A to B" (not just whether one exists)
- N-Queens, Sudoku Solver — place items under constraints, backtrack on
  violation
- Constraints explicitly small: `1 <= n <= 12` (subsets/permutations) or
  `n <= 9` (Sudoku) — a strong signal the intended solution is exponential
- "Letter Combinations of a Phone Number" — Cartesian product of choices,
  built incrementally

**Anti-signals (looks similar, use a different pattern):**
- "**Count** the number of ways" (not "list all ways") AND the same
  subproblem recurs with different parent choices ->
  [`dynamic_programming.md`](dynamic_programming.md) — memoize instead of
  re-exploring
- "**Does** a valid arrangement exist?" (boolean, not enumeration) — often
  solvable with a single DFS/greedy pass without needing to enumerate all
  possibilities
- "Generate all subsets" with `n` up to ~20-30 and only need existence/sum
  properties -> meet-in-the-middle or
  [`bit_manipulation.md`](bit_manipulation.md) bitmask DP may be faster than
  raw enumeration
- A simple connectivity/path-existence question on a grid ->
  [`graph_traversal.md`](graph_traversal.md) (though Word Search-style "find
  THIS exact path" still uses the mark/unmark backtracking mechanism)

---

## 2. Mental Model & Intuition

Backtracking explores a **state-space tree**: each node is a partial
solution, each edge is a "choice," and leaves are complete (or invalid)
candidates. The recursion visits this tree depth-first; the "undo" step is
what lets the same `path` variable represent a *different* node as the
recursion returns up and back down.

```
Subsets of [1, 2] -- "include or exclude each element":

                          path=[]
                    /                \
            include 1                exclude 1
              path=[1]                 path=[]
             /        \               /        \
      include 2    exclude 2   include 2    exclude 2
      path=[1,2]   path=[1]    path=[2]     path=[]

Leaves (left to right): [1,2]  [1]  [2]  []
All 4 = 2^2 subsets, one per leaf.

The "undo" (path.pop()) is what turns path from [1,2] back into [1],
and from [1] back into [], so the SAME list object can represent
every node in the tree as the DFS moves through it.
```

---

## 3. The Template

```python
from __future__ import annotations

# ---------------------------------------------------------------------------
# Template 1: Subsets (include/exclude recursion)
# ---------------------------------------------------------------------------
def subsets(nums: list[int]) -> list[list[int]]:
    result: list[list[int]] = []
    path: list[int] = []

    def backtrack(start: int) -> None:
        result.append(path[:])  # record a COPY -- path keeps mutating
        for i in range(start, len(nums)):
            path.append(nums[i])      # CHOOSE
            backtrack(i + 1)           # EXPLORE
            path.pop()                 # UN-CHOOSE (backtrack)

    backtrack(0)
    return result


# ---------------------------------------------------------------------------
# Template 2: Permutations (used[] array)
# ---------------------------------------------------------------------------
def permute(nums: list[int]) -> list[list[int]]:
    result: list[list[int]] = []
    path: list[int] = []
    used = [False] * len(nums)

    def backtrack() -> None:
        if len(path) == len(nums):
            result.append(path[:])
            return
        for i in range(len(nums)):
            if used[i]:
                continue
            used[i] = True            # CHOOSE
            path.append(nums[i])
            backtrack()                 # EXPLORE
            path.pop()                  # UN-CHOOSE
            used[i] = False

    backtrack()
    return result


# ---------------------------------------------------------------------------
# Template 3: Combination Sum (reuse allowed -- pass `i`, not `i+1`)
# ---------------------------------------------------------------------------
def combination_sum(candidates: list[int], target: int) -> list[list[int]]:
    result: list[list[int]] = []
    path: list[int] = []

    def backtrack(start: int, remaining: int) -> None:
        if remaining == 0:
            result.append(path[:])
            return
        if remaining < 0:
            return
        for i in range(start, len(candidates)):
            path.append(candidates[i])
            backtrack(i, remaining - candidates[i])  # `i` -- can reuse candidates[i]
            path.pop()

    backtrack(0, target)
    return result


# ---------------------------------------------------------------------------
# Template 4: N-Queens -- constraint propagation via column/diagonal sets
# ---------------------------------------------------------------------------
def solve_n_queens(n: int) -> list[list[str]]:
    result: list[list[str]] = []
    cols: set[int] = set()
    diag1: set[int] = set()  # r - c is constant along a "/" diagonal
    diag2: set[int] = set()  # r + c is constant along a "\" diagonal
    placement: list[int] = []  # placement[r] = column of queen in row r

    def backtrack(row: int) -> None:
        if row == n:
            board = []
            for c in placement:
                board.append("." * c + "Q" + "." * (n - c - 1))
            result.append(board)
            return
        for col in range(n):
            if col in cols or (row - col) in diag1 or (row + col) in diag2:
                continue  # PRUNE -- constraint violated, skip immediately
            cols.add(col); diag1.add(row - col); diag2.add(row + col)
            placement.append(col)

            backtrack(row + 1)

            cols.remove(col); diag1.remove(row - col); diag2.remove(row + col)
            placement.pop()

    backtrack(0)
    return result
```

---

## 4. Annotated Walkthrough

**Problem**: [N-Queens (LC 51)](https://leetcode.com/problems/n-queens/),
`n = 4` — place 4 queens on a 4x4 board so none attack each other.

**Trace** (using Template 4's column/diagonal sets):

```
backtrack(row=0): try col=0,1,2,3 for row 0

  col=0: cols={0}, diag1={0-0=0}, diag2={0+0=0}, placement=[0]
    backtrack(row=1): try col=0,1,2,3 for row 1
      col=0: 0 in cols -> PRUNE
      col=1: 1-1=0 in diag1 -> PRUNE  (row-col=0 already used by row 0's queen)
      col=2: cols={0,2}, diag1={0,-1}, diag2={0,3}, placement=[0,2]
        backtrack(row=2): try col=0,1,2,3 for row 2
          col=0: 0 in cols -> PRUNE
          col=1: 2-1=1 not in diag1{0,-1}; 2+1=3 in diag2{0,3} -> PRUNE
          col=2: 2 in cols -> PRUNE
          col=3: 2-3=-1 in diag1{0,-1} -> PRUNE
          -- all 4 columns pruned, backtrack(row=2) returns with NO solution
        undo col=2 -> cols={0}, diag1={0}, diag2={0}, placement=[0]
      col=3: cols={0,3}, diag1={0,-2}, diag2={0,4}, placement=[0,3]
        backtrack(row=2): try col=0,1,2,3
          col=0: PRUNE (in cols)
          col=1: 2-1=1 not in diag1{0,-2}; 2+1=3 not in diag2{0,4} -> OK
            cols={0,3,1}, diag1={0,-2,1}, diag2={0,4,3}, placement=[0,3,1]
            backtrack(row=3): try col=0,1,2,3
              col=0: PRUNE; col=1: PRUNE; col=2: 3-2=1 in diag1 -> PRUNE
              col=3: PRUNE (in cols)
              -- all pruned, returns with NO solution
            undo col=1
          col=2: 2-2=0 in diag1{0,-2} -> PRUNE
          col=3: PRUNE (in cols)
          -- backtrack(row=2) returns with NO solution
        undo col=3
    undo col=2 (from row=1) -- wait, already undone above; continue row=1 loop
    -- row=1 loop exhausted (col=0,1,2,3 all tried) -> backtrack(row=1) returns NO solution
  undo col=0 (row=0)

  col=1: cols={1}, diag1={-1}, diag2={1}, placement=[1]
    backtrack(row=1): ... (symmetric exploration) ...
      Eventually finds: row1->col=3, row2->col=0, row3->col=2
      placement = [1, 3, 0, 2]  -> ONE SOLUTION FOUND
      board = [".Q..", "...Q", "Q...", "..Q."]

  col=2: (mirror of col=1 by symmetry)
      placement = [2, 0, 3, 1] -> SECOND SOLUTION FOUND
      board = ["..Q.", "Q...", "...Q", ".Q.."]

  col=3: (mirror of col=0 -- exhaustively pruned, NO solution, by symmetry)

Final result: 2 solutions for n=4.
```

The key efficiency win is in the `if col in cols or ... continue` line —
**pruning happens BEFORE recursing**, not after building a complete (invalid)
board. This is what keeps N-Queens far below the naive `O(n^n)` (try every
column for every row with no checks).

---

## 5. Complexity

| Template | Time | Space | Notes |
|---|---|---|---|
| Subsets | O(2^n · n) | O(n) recursion depth + O(2^n · n) output | Each of `2^n` subsets costs O(n) to copy |
| Permutations | O(n! · n) | O(n) recursion depth + O(n! · n) output | Each of `n!` permutations costs O(n) to copy |
| Combination Sum | O(2^target) worst case | O(target) recursion depth | Bounded by `target / min(candidates)` in practice |
| N-Queens | O(n!) worst case, much better with pruning | O(n) for `cols`/`diag1`/`diag2`/`placement` | Pruning eliminates huge subtrees early — empirically far below `n!` |

The `path[:]` (or `list(path)`) copy when recording a result is **essential**
— without it, every entry in `result` would be a reference to the *same*
mutating `path` list, and by the time backtracking finishes, all entries
would show the final (empty) state. This is a milder cousin of the §8 bug.

---

## 6. Variations & Sub-patterns

**Subsets II / Permutations II (with duplicates)**
([LC 90](https://leetcode.com/problems/subsets-ii/),
[LC 47](https://leetcode.com/problems/permutations-ii/)): sort the input
first, then **skip duplicate choices at the same recursion level** — `if i >
start and nums[i] == nums[i-1]: continue` (Subsets II) or the analogous check
using the `used` array (Permutations II). This prevents generating the same
subset/permutation multiple times via different "paths" to the same multiset.

**Combination Sum vs. Combination Sum II**: Combination Sum (Template 3)
allows reusing the same element — recurse with `backtrack(i, ...)` (the
*same* index can be chosen again). Combination Sum II
([LC 40](https://leetcode.com/problems/combination-sum-ii/)) — each element
used at most once *and* the input may contain duplicates — recurse with
`backtrack(i + 1, ...)` *and* skip same-level duplicates as in Subsets II.

**Constraint propagation** (N-Queens' `cols`/`diag1`/`diag2` sets, Sudoku's
row/column/box sets): instead of checking the *entire* board for validity at
each step (`O(n^2)` per check), maintain `O(1)`-checkable sets that are
updated incrementally as choices are made and undone. This turns "is this
placement valid?" from an `O(n)` or `O(n^2)` scan into an `O(1)` set lookup.

**Substring-based backtracking** (Palindrome Partitioning,
[LC 131](https://leetcode.com/problems/palindrome-partitioning/)): the
"choices" at each step are *substring lengths* rather than discrete elements
— `for end in range(start, len(s)): if is_palindrome(s[start:end+1]):
path.append(s[start:end+1]); backtrack(end+1); path.pop()`.

**Grid-based backtracking** (Word Search,
[LC 79](https://leetcode.com/problems/word-search/)): "choices" are grid
directions; "undo" is restoring the cell's character after marking it
visited — see [`graph_traversal.md`](graph_traversal.md)'s BROKEN->FIX, which
covers the *same* mark/unmark discipline in a DFS-without-enumeration
context.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue/twist |
|---|---|---|---|
| [Subsets (LC 78)](https://leetcode.com/problems/subsets/) | Medium | Include/exclude | The foundational template |
| [Subsets II (LC 90)](https://leetcode.com/problems/subsets-ii/) | Medium | Duplicates | Sort + skip same-level duplicates |
| [Permutations (LC 46)](https://leetcode.com/problems/permutations/) | Medium | `used[]` array | Order matters, all elements used |
| [Permutations II (LC 47)](https://leetcode.com/problems/permutations-ii/) | Medium | Duplicates | Sort + skip same-level duplicates with `used[]` |
| [Combination Sum (LC 39)](https://leetcode.com/problems/combination-sum/) | Medium | Reuse allowed | Recurse with `i`, not `i+1` |
| [Combination Sum II (LC 40)](https://leetcode.com/problems/combination-sum-ii/) | Medium | No reuse + duplicates | Recurse with `i+1` AND skip duplicates |
| [Letter Combinations of a Phone Number (LC 17)](https://leetcode.com/problems/letter-combinations-of-a-phone-number/) | Medium | Cartesian product | Each digit contributes 3-4 choices |
| [Palindrome Partitioning (LC 131)](https://leetcode.com/problems/palindrome-partitioning/) | Medium | Substring choices | Choice = substring length, not element |
| [Word Search (LC 79)](https://leetcode.com/problems/word-search/) | Medium | Grid + mark/unmark | Shares mechanism with `graph_traversal.md` |
| [N-Queens (LC 51)](https://leetcode.com/problems/n-queens/) | Hard | Constraint propagation | The signature problem — `cols`/`diag1`/`diag2` sets |
| [Sudoku Solver (LC 37)](https://leetcode.com/problems/sudoku-solver/) | Hard | 2D constraint propagation | Row/column/3x3-box sets, same idea as N-Queens extended |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake**: forgetting the "un-choose" step (`path.pop()`) after the
recursive call returns. The shared `path` list keeps growing across
*every* branch of the recursion tree, corrupting all subsequent results.

```python
# BROKEN: missing path.pop() -- the "undo" half of backtracking
def subsets_broken(nums):
    result = []
    path = []

    def backtrack(start):
        result.append(path[:])
        for i in range(start, len(nums)):
            path.append(nums[i])     # CHOOSE
            backtrack(i + 1)          # EXPLORE
            # BUG: missing path.pop() -- UN-CHOOSE never happens
    backtrack(0)
    return result
```

**Trace the bug** on `nums = [1, 2]` (expected: `[[], [1], [1,2], [2]]`):

```
backtrack(0): path=[] -> result.append([]) -> result=[[]]
  i=0: path.append(1) -> path=[1]
    backtrack(1): path=[1] -> result.append([1]) -> result=[[],[1]]
      i=1: path.append(2) -> path=[1,2]
        backtrack(2): path=[1,2] -> result.append([1,2]) -> result=[[],[1],[1,2]]
          for i in range(2,2): nothing -- return
        # NO POP -- path stays [1,2]
      # i=1 was last in range(1,2) -- backtrack(1) returns
    # NO POP -- path stays [1,2]  (should be [] again here!)
  i=1: path.append(2) -> path=[1,2,2]   <-- CORRUPTED: should be [2], is [1,2,2]
    backtrack(2): path=[1,2,2] -> result.append([1,2,2]) -> result=[[],[1],[1,2],[1,2,2]]
      for i in range(2,2): nothing -- return
    # NO POP -- path stays [1,2,2]

Final (WRONG): result = [[], [1], [1,2], [1,2,2]]
Expected:                [[], [1], [1,2], [2]]
```

Without the pop, `path` never returns to a prior state — by the time the
outer loop reaches `i=1`, `path` is still `[1, 2]` from the *previous*
branch, so appending `2` produces `[1, 2, 2]` instead of the intended `[2]`.
The subset `[2]` is never generated, and a spurious `[1,2,2]` appears
instead.

**Fix**: add `path.pop()` immediately after the recursive call — this is the
"backtrack" in backtracking.

```python
# FIXED: path.pop() restores state for the next iteration of the loop
def subsets_fixed(nums):
    result = []
    path = []

    def backtrack(start):
        result.append(path[:])
        for i in range(start, len(nums)):
            path.append(nums[i])     # CHOOSE
            backtrack(i + 1)          # EXPLORE
            path.pop()                 # UN-CHOOSE -- FIX
    backtrack(0)
    return result
```

**Re-trace with the fix** on `nums = [1, 2]`:

```
backtrack(0): path=[] -> result=[[]]
  i=0: path.append(1) -> path=[1]
    backtrack(1): path=[1] -> result=[[],[1]]
      i=1: path.append(2) -> path=[1,2]
        backtrack(2): path=[1,2] -> result=[[],[1],[1,2]]
        (no i in range(2,2))
      path.pop() -> path=[1]                    <- FIX restores [1]
    backtrack(1) returns
  path.pop() -> path=[]                          <- FIX restores []
  i=1: path.append(2) -> path=[2]
    backtrack(2): path=[2] -> result=[[],[1],[1,2],[2]]
    (no i in range(2,2))
  path.pop() -> path=[]

Final (CORRECT): result = [[], [1], [1,2], [2]]   -- exactly 2^2 = 4 subsets
```

---

## 9. Related Patterns & When to Switch

- **[`recursion_and_problem_solving_patterns`](../recursion_and_problem_solving_patterns/README.md)** —
  backtracking is recursion with an explicit "undo" discipline; if you're
  comfortable with plain recursive DFS but unsure why results get corrupted,
  the missing piece is almost always the undo step (§8).
- **[`graph_traversal.md`](graph_traversal.md)** — Word Search and similar
  grid problems use the *same* mark/unmark mechanism, but the goal is
  existence (`return True` on first match) rather than enumeration — you can
  often short-circuit instead of exploring the full tree.
- **[`dynamic_programming.md`](dynamic_programming.md)** — if a problem asks
  to **count** or find the **min/max** of something (not list all
  possibilities), and the same `(remaining_target, index)` state recurs
  across different choice sequences, memoize it — turning exponential
  backtracking into polynomial DP.
- **[`bit_manipulation.md`](bit_manipulation.md)** — for `n <= ~20`, subsets
  can be enumerated *iteratively* via bitmasks (`for mask in
  range(2**n)`), avoiding recursion entirely — useful when subset
  *order* doesn't matter and you just need to iterate over all `2^n`
  combinations.

---

## 10. Cross-links

- Concept module: [`recursion_and_problem_solving_patterns/`](../recursion_and_problem_solving_patterns/README.md) —
  recursion fundamentals, call-stack visualization, base-case design

---

## 11. Interview Q&A

**What's the precise difference between "backtracking" and "plain recursive
DFS"?**
Plain DFS explores a tree and returns; backtracking explores a tree **while
mutating shared state** (a path, a board, a set of used elements) and
explicitly **restores that state** before trying the next sibling branch. The
"restore" (undo) step is the defining characteristic — without it, sibling
branches would see corrupted state left over from previously-explored
branches (exactly the bug in §8).

**Why is backtracking exponential, and why is that considered "fine" for
these problems?**
Each recursive call branches into multiple choices, and the recursion depth
equals the solution length — so the total number of leaves in the search
tree is `O(branching_factor ^ depth)`, which is `2^n` (subsets) or `n!`
(permutations). This is "fine" because problems that call for backtracking
explicitly constrain `n` to be small (typically `<= 12-20`) — `2^20 ~ 10^6`
is fast, but `2^50` would not be. The small constraint *is the signal* that
exponential is the intended complexity class.

**Walk through why `path.pop()` (or `used[i] = False`) is necessary — what
breaks without it?**
The recursive calls share a *single* mutable `path` (or `used` array) by
reference — there's only one list object, repeatedly appended to and read
from at different points in the recursion. Without popping, every "exclude"
or "try the next choice" branch starts from whatever state the *previous*
branch left behind, rather than the state that branch's parent had. See §8
for a full traced example showing exactly how this corrupts results.

**Subsets II — how do you avoid generating the same subset twice when the
input has duplicates, e.g., `nums = [1, 2, 2]`?**
Sort `nums` first so duplicates are adjacent. In the `for i in range(start,
len(nums))` loop, add `if i > start and nums[i] == nums[i-1]: continue`. This
specifically skips choosing the *same value* as a sibling choice **at the
same recursion level** (same `start`) — it does NOT prevent using duplicates
at *different* levels (i.e., `[2, 2]` as a subset is still generated, just
only once).

**Permutations: `used[]` array vs. swapping elements in place — what's the
tradeoff?**
The `used[]` array approach (Template 2) is more intuitive and naturally
supports the duplicate-skipping check for Permutations II. The swap-based
approach (`for i in range(k, n): swap(nums, k, i); backtrack(k+1);
swap(nums, k, i)` to undo) avoids the extra `O(n)` `used` array and an `O(n)`
`path` list — the permutation IS `nums` itself at each leaf — but makes
duplicate-handling trickier (requires a `set()` per recursion level to skip
repeated swap values).

**How does N-Queens' constraint propagation (`cols`, `diag1`, `diag2` sets)
make the search dramatically faster than checking the whole board at each
step?**
Without the sets, validating "can I place a queen at `(row, col)`?" requires
scanning all previously-placed queens — `O(row)` per check, `O(n)` per row,
`O(n^2)` total per leaf, `O(n^2 * n!)` overall. With the sets, each check is
`O(1)` (set membership), AND — more importantly — **invalid branches are
pruned immediately** rather than being fully built and then rejected. The
`row - col` and `row + col` invariants are the standard trick for identifying
"/" and "\" diagonals in O(1).

**When should you reach for DP instead of backtracking — what's the concrete
signal?**
If the problem asks to **count** or find an **optimal value** (not enumerate
all solutions), AND the recursive calls are made with the *same arguments*
multiple times via different choice orderings (overlapping subproblems) —
e.g., Combination Sum's "count the number of ways to reach `target`" would
recompute `backtrack(target=5)` regardless of which earlier choices got you
to `remaining=5`. Memoizing on `(start_index_irrelevant, remaining)` turns
exponential backtracking into `O(target * len(candidates))` DP. If the
problem needs the *actual combinations themselves*, you still need
backtracking (possibly DP-assisted for pruning).

**For Combination Sum, why does the recursive call use `backtrack(i, ...)`
(same index) instead of `backtrack(i + 1, ...)`?**
`backtrack(i, ...)` allows `candidates[i]` to be chosen **again** in the next
recursive level — modeling "unlimited supply" of each candidate (e.g.,
`[2,2,3]` is valid for `Combination Sum` if `2+2+3` sums to target). Combined
with `for i in range(start, ...)` (not re-trying indices `< start`), this
still avoids generating `[2,3,2]` as a *different* combination from
`[2,2,3]` — order within a combination doesn't matter, only the multiset of
values chosen.

**Word Search backtracking vs. graph_traversal's flood fill — both mark and
unmark grid cells. What's actually different?**
Mechanically, almost nothing — both mark `board[r][c]` before recursing into
neighbors and restore it after. The difference is **purpose**: flood fill
(graph_traversal) marks cells *permanently* within one connected-component
pass (no restore needed across components) to avoid re-counting. Word Search
restores cells because the *same* cell might need to be available again for
a *different* candidate path — e.g., if the first DFS path from a starting
cell fails to spell the word, a different direction from the same start must
see the original board.

**Why must `result.append(path[:])` use a copy (`path[:]` or `list(path)`)
instead of `result.append(path)`?**
`path` is a single mutable list object, repeatedly appended to and popped
from throughout the entire recursion. `result.append(path)` would store a
**reference** to that same object in every position of `result` — by the
time backtracking finishes (and `path` has been popped back to `[]`), every
entry in `result` would appear as `[]`, since they all point to the same
(now-empty) list. `path[:]` creates an independent snapshot at that moment.

**For very small `n` (say `n <= 20`), when would an iterative bitmask
approach beat recursive backtracking for generating subsets?**
When you don't need subsets in any particular order and want to avoid Python
recursion overhead (function call costs add up across `2^n` calls). `for
mask in range(2**n): subset = [nums[i] for i in range(n) if mask & (1 <<
i)]` iterates all `2^n` subsets with simple loops and bit tests — often
faster in practice for `n` up to ~20-22, and trivially parallelizable since
each `mask` is independent. See [`bit_manipulation.md`](bit_manipulation.md)
for the bitmask mechanics.
