# Matrix Traversal & Manipulation

## Pattern Snapshot

Walk or rewrite a 2D grid using **coordinate arithmetic** rather than a visited-set search — spiral order, layer rotation, diagonal sweeps, and in-place rewrites that achieve O(1) extra space by encoding state inside the cells themselves. **Cue**: "spiral", "rotate the image 90°", "diagonal order", "set entire row/column to zero", "transpose", "in place / without allocating another matrix". **Typical complexity**: O(m·n) time — every cell is touched a constant number of times — and frequently O(1) extra space.

This pattern is the *index-math* sibling of [graph_traversal.md](graph_traversal.md): graph traversal asks "which cells are **connected/reachable**?" and needs a queue or visited-set; matrix manipulation asks "how do I **re-index or overwrite** the grid?" and needs only careful boundary and coordinate bookkeeping.

---

## 1. Recognition Signals

**Reach for matrix traversal/manipulation when you see:**

- "Return the elements in **spiral order**" / "generate an n×n matrix filled in spiral order"
- "**Rotate** the image by 90 degrees, **in place**"
- "Traverse the matrix **diagonally**" (anti-diagonals, or top-left→bottom-right diagonals)
- "If an element is 0, **set its entire row and column to 0**" — often with an explicit O(1)-space follow-up
- "**Transpose** the matrix" / "reflect across the diagonal"
- "Compute the **next state** of the board (Game of Life) **in place**"
- "Search a matrix whose rows (and maybe columns) are **sorted**" — *value*-ordered, not connectivity
- A constraint demanding **O(1) extra space** on a grid problem (the strongest signal that the answer is coordinate math, not an auxiliary structure)

**Anti-signals — looks like this pattern but isn't:**

- "Number of **islands** / connected regions / flood fill / shortest path on a grid" — that is connectivity, use **[Graph Traversal](graph_traversal.md)** (DFS/BFS) or **[Shortest Path](shortest_path.md)** (BFS / Dijkstra / 0-1 BFS), not coordinate rewriting.
- "Sum of a **submatrix** / many range-sum queries" — precompute a **[2D prefix sum](prefix_sum.md)** (summed-area table), a manipulation *technique* but its own pattern.
- "Dynamic programming **on a grid**" (unique paths, minimum path sum) — that is **[Dynamic Programming](dynamic_programming.md)**; the grid is the DP table, not the object being rewritten.
- "Find a value in a fully sorted matrix" — that is **[Modified Binary Search](modified_binary_search.md)** (flatten to 1D); the staircase walk for *row-and-column-sorted* matrices is covered here in §6 as a boundary case.

The defining test: **is the answer produced by reading/writing cells at computed coordinates (with no notion of "neighbor reachability"), ideally in place?** If yes → matrix manipulation. If you need a visited-set and a frontier → graph traversal.

---

## 2. Mental Model & Intuition

**Spiral = four shrinking walls.** Keep `top, bottom, left, right` boundaries; walk the top row left→right, the right column top→bottom, then (if walls haven't crossed) the bottom row right→left and the left column bottom→top — shrinking the relevant wall after each edge.

```
top=0                  After walking the top row, top -> 1
+----------------->    +  →  →  →  +
| 1   2   3   4  |     | 1   2   3   4 |
| 5   6   7   8  |     | 5   6   7   8 |     order so far: 1 2 3 4
| 9  10  11  12  |     | 9  10  11  12 |
+----------------+     +----------------+

Then right column (top..bottom), bottom row (right..left), left column up.
Each pass shrinks one wall; stop when top>bottom or left>right.
Full order: 1 2 3 4 8 12 11 10 9 5 6 7
```

**Rotate 90° clockwise = transpose, then reverse each row.** Transpose mirrors across the main diagonal (`a[i][j] <-> a[j][i]`); reversing each row then maps the mirrored grid to the rotated one — both steps are in place.

```
original        transpose (swap i,j)     reverse each row  = rotated 90° CW
1 2 3            1 4 7                     7 4 1
4 5 6     -->    2 5 8           -->       8 5 2
7 8 9            3 6 9                     9 6 3
```

**Diagonals share an index sum/difference.** On an anti-diagonal, `r + c` is constant; on a main diagonal, `r - c` is constant. That single fact turns "traverse diagonally" into "bucket cells by `r+c`."

**In-place state needs encoding, not a copy.** Game of Life must compute the *next* board from the *current* one without a second matrix. Store both states in each cell using 2 bits: bit 0 = current, bit 1 = next. Write `cell |= next << 1` in pass 1, then `cell >>= 1` in pass 2.

```
cell value (2 bits):  [ next | current ]
0b01 = alive now, dead next      0b11 = alive now, alive next
0b10 = dead now,  alive next     0b00 = dead now,  dead next
Pass 1 reads bit 0 (untouched);  Pass 2 shifts right to commit bit 1.
```

---

## 3. The Template

### Spiral traversal (read in spiral order)

```python
def spiral_order(matrix: list[list[int]]) -> list[int]:
    if not matrix or not matrix[0]:
        return []
    top, bottom = 0, len(matrix) - 1
    left, right = 0, len(matrix[0]) - 1
    out: list[int] = []

    while top <= bottom and left <= right:
        for c in range(left, right + 1):          # top row, L -> R
            out.append(matrix[top][c])
        top += 1
        for r in range(top, bottom + 1):          # right col, T -> B
            out.append(matrix[r][right])
        right -= 1
        if top <= bottom:                          # guard: row still exists
            for c in range(right, left - 1, -1):   # bottom row, R -> L
                out.append(matrix[bottom][c])
            bottom -= 1
        if left <= right:                          # guard: col still exists
            for r in range(bottom, top - 1, -1):   # left col, B -> T
                out.append(matrix[r][left])
            left += 1
    return out
```

### Rotate 90° clockwise in place (transpose + reverse rows)

```python
def rotate(matrix: list[list[int]]) -> None:
    n = len(matrix)
    for i in range(n):                             # transpose (upper triangle)
        for j in range(i + 1, n):
            matrix[i][j], matrix[j][i] = matrix[j][i], matrix[i][j]
    for row in matrix:                             # reverse each row
        row.reverse()
```

### Set matrix zeroes in O(1) space (first row/col as markers)

```python
def set_zeroes(matrix: list[list[int]]) -> None:
    rows, cols = len(matrix), len(matrix[0])
    first_row_zero = any(matrix[0][c] == 0 for c in range(cols))
    first_col_zero = any(matrix[r][0] == 0 for r in range(rows))

    for r in range(1, rows):                       # mark in the border
        for c in range(1, cols):
            if matrix[r][c] == 0:
                matrix[r][0] = 0
                matrix[0][c] = 0

    for r in range(1, rows):                       # apply from the marks
        for c in range(1, cols):
            if matrix[r][0] == 0 or matrix[0][c] == 0:
                matrix[r][c] = 0

    if first_row_zero:
        for c in range(cols):
            matrix[0][c] = 0
    if first_col_zero:
        for r in range(rows):
            matrix[r][0] = 0
```

---

## 4. Annotated Walkthrough

**Problem**: [Spiral Matrix (LC 54)](https://leetcode.com/problems/spiral-matrix/) — return all elements of an `m × n` matrix in spiral order.

**Brute force**: simulate with a `visited` grid and a direction vector that turns right whenever the next cell is out of bounds or already visited. That works in O(m·n) time but uses O(m·n) extra space — exactly the auxiliary structure this pattern lets us avoid.

**Key insight**: the spiral is four nested walls that shrink inward. We never need a visited-set because the boundaries themselves guarantee we never revisit a cell — after consuming the top row we raise `top`, so the rightward pass below can never touch it again.

**Trace on the 3×4 matrix**

```
matrix =
 1  2  3  4
 5  6  7  8
 9 10 11 12

Initial walls: top=0 bottom=2 left=0 right=3

Pass 1  top row r=0, c=0..3      -> 1 2 3 4        top=1
Pass 2  right col c=3, r=1..2    -> 8 12           right=2
        top(1) <= bottom(2):
Pass 3  bottom row r=2, c=2..0   -> 11 10 9        bottom=1
        left(0) <= right(2):
Pass 4  left col c=0, r=1..1     -> 5             left=1

Loop check: top=1 bottom=1 left=1 right=2  -> still valid
Pass 1  top row r=1, c=1..2      -> 6 7            top=2
Pass 2  right col: range(2,2) empty                right=1
        top(2) <= bottom(1)?  NO -> skip pass 3, skip pass 4

Loop check: top=2 > bottom=1 -> stop
Result: 1 2 3 4 8 12 11 10 9 5 6 7
```

The two `if` guards (`if top <= bottom`, `if left <= right`) are the crux: in a non-square or odd-dimension matrix, after shrinking two walls the remaining strip is a single row or column, and re-walking it would double-emit elements. The guards skip the bottom/left passes precisely when their wall has already been consumed.

---

## 5. Complexity

| Aspect | Value | Why |
|---|---|---|
| Time | **O(m·n)** | Every cell is read (and, for in-place transforms, written) a constant number of times. Spiral touches each once; rotate touches each in the transpose pass and once in the row-reverse; set-zeroes makes two O(m·n) passes. |
| Space | **O(1) extra** | The marker tricks (first row/col, 2-bit encoding, boundary variables) reuse the grid itself. The only O(m·n) "space" is the *required output* list for read-only traversals like spiral. |

The whole reason this is a distinct pattern is the **space** column: the naive version of nearly every problem here allocates a second matrix or a visited grid, and the interview follow-up is always "now do it in O(1) extra space," which forces the coordinate-arithmetic insight.

---

## 6. Variations & Sub-patterns

- **Spiral traversal** — read an existing matrix in spiral order; four shrinking walls with the two parity guards ([Spiral Matrix (LC 54)](https://leetcode.com/problems/spiral-matrix/)).
- **Spiral generation** — write `1..n²` into a fresh matrix in spiral order; same wall logic, assigning instead of reading ([Spiral Matrix II (LC 59)](https://leetcode.com/problems/spiral-matrix-ii/)).
- **Expanding spiral from a point** — walk outward with step lengths `1,1,2,2,3,3,...`, collecting only in-bounds cells ([Spiral Matrix III (LC 885)](https://leetcode.com/problems/spiral-matrix-iii/)).
- **Layer rotation (in place)** — rotate 90° via transpose + row-reverse, or rotate four corner cells per layer in a single pass ([Rotate Image (LC 48)](https://leetcode.com/problems/rotate-image/)). Counter-clockwise = transpose + *column* reverse (reverse the order of rows).
- **Transpose / reflection** — swap across the main diagonal (`a[i][j] <-> a[j][i]`); reflecting across the anti-diagonal uses `a[i][j] <-> a[n-1-j][n-1-i]` ([Transpose Matrix (LC 867)](https://leetcode.com/problems/transpose-matrix/)).
- **Diagonal traversal** — bucket cells by `r + c` (anti-diagonals) or `r - c` (main diagonals); alternate the within-diagonal direction for zig-zag output ([Diagonal Traverse (LC 498)](https://leetcode.com/problems/diagonal-traverse/)).
- **In-place state encoding** — pack current + next state into spare bits (Game of Life's 2-bit trick), or negate / use a sentinel to mark "to be changed," so one matrix holds two generations ([Game of Life (LC 289)](https://leetcode.com/problems/game-of-life/)).
- **Marker-row/column rewrite** — use row 0 and column 0 as the bookkeeping for "this row/column must be zeroed," achieving O(1) space ([Set Matrix Zeroes (LC 73)](https://leetcode.com/problems/set-matrix-zeroes/)).
- **Index-decomposition validation** — map a cell to its sub-block via `(r // 3) * 3 + (c // 3)`; the engine behind Sudoku-style row/column/box checks ([Valid Sudoku (LC 36)](https://leetcode.com/problems/valid-sudoku/)).
- **Sorted-matrix search (boundary case)** — for a *fully* sorted matrix, flatten to 1D and binary search ([Search a 2D Matrix](https://leetcode.com/problems/search-a-2d-matrix/), see [modified_binary_search.md](modified_binary_search.md)); for a *row-and-column-sorted* matrix, walk a staircase from the top-right corner in O(m+n) ([Search a 2D Matrix II](https://leetcode.com/problems/search-a-2d-matrix-ii/)).
- **"It's actually a graph"** — the moment the problem is about reachability, adjacency, or connected regions, drop this pattern and switch to [graph_traversal.md](graph_traversal.md).

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Transpose Matrix (LC 867)](https://leetcode.com/problems/transpose-matrix/) | Easy | Reflection across main diagonal | Output may be non-square: `result[c][r] = a[r][c]` |
| [Flipping an Image (LC 832)](https://leetcode.com/problems/flipping-an-image/) | Easy | Row reverse + transform | Reverse each row, then invert each bit (`1 - x`) |
| [Toeplitz Matrix (LC 766)](https://leetcode.com/problems/toeplitz-matrix/) | Easy | Diagonal-constant check | Every cell equals its up-left neighbor `a[r-1][c-1]` |
| [Matrix Diagonal Sum (LC 1572)](https://leetcode.com/problems/matrix-diagonal-sum/) | Easy | Both diagonals via index | Add `a[i][i]` and `a[i][n-1-i]`; subtract the center if `n` is odd |
| [Spiral Matrix (LC 54)](https://leetcode.com/problems/spiral-matrix/) | Medium | Spiral traversal | Four shrinking walls + two parity guards |
| [Spiral Matrix II (LC 59)](https://leetcode.com/problems/spiral-matrix-ii/) | Medium | Spiral generation | Same walls, assign `1..n²` instead of reading |
| [Rotate Image (LC 48)](https://leetcode.com/problems/rotate-image/) | Medium | In-place layer rotation | Transpose + reverse each row (or rotate 4 corners per layer) |
| [Set Matrix Zeroes (LC 73)](https://leetcode.com/problems/set-matrix-zeroes/) | Medium | Marker-row/column rewrite | First row/col store the flags; handle them last |
| [Diagonal Traverse (LC 498)](https://leetcode.com/problems/diagonal-traverse/) | Medium | Diagonal sweep | Group by `r + c`; reverse alternate diagonals |
| [Game of Life (LC 289)](https://leetcode.com/problems/game-of-life/) | Medium | In-place state encoding | 2-bit pack: write `next << 1`, then shift all cells right |
| [Valid Sudoku (LC 36)](https://leetcode.com/problems/valid-sudoku/) | Medium | Index decomposition | Box id = `(r // 3) * 3 + (c // 3)`; three sets of seen-values |
| [Search a 2D Matrix (LC 74)](https://leetcode.com/problems/search-a-2d-matrix/) | Medium | Fully sorted → 1D binary search | Treat as one flattened array — see [modified_binary_search.md](modified_binary_search.md) |
| [Search a 2D Matrix II (LC 240)](https://leetcode.com/problems/search-a-2d-matrix-ii/) | Medium | Staircase search | Start top-right; go left if too big, down if too small — O(m+n) |
| [Spiral Matrix III (LC 885)](https://leetcode.com/problems/spiral-matrix-iii/) | Medium | Expanding spiral from a point | Step lengths grow 1,1,2,2,3,3...; keep only in-bounds cells |
| [Spiral Matrix IV (LC 2326)](https://leetcode.com/problems/spiral-matrix-iv/) | Medium | Fill spiral from a linked list | Walk the spiral writing node values; pad the rest with -1 |
| [Range Sum Query 2D - Immutable (LC 304)](https://leetcode.com/problems/range-sum-query-2d-immutable/) | Medium | 2D prefix sum (summed-area table) | Inclusion-exclusion on 4 corners — see [prefix_sum.md](prefix_sum.md) |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: zeroing rows and columns *during* the scan in "Set Matrix Zeroes," which corrupts cells you haven't examined yet.**

```python
# BROKEN — the moment we see a 0, we zero its whole row and column in place.
# Those freshly-written zeros are then read by later iterations and trigger
# MORE rows/columns to be zeroed — the zeros "bleed" across the matrix.
def set_zeroes_broken(matrix: list[list[int]]) -> None:
    rows, cols = len(matrix), len(matrix[0])
    for r in range(rows):
        for c in range(cols):
            if matrix[r][c] == 0:
                for cc in range(cols):        # BUG: mutates cells still to be read
                    matrix[r][cc] = 0
                for rr in range(rows):
                    matrix[rr][c] = 0
```

```python
# FIXED — separate "detect" from "apply." Record which rows/cols to zero,
# THEN apply in a second pass. (O(m+n) extra here; the §3 template pushes
# this to O(1) by storing the flags in row 0 / column 0.)
def set_zeroes_fixed(matrix: list[list[int]]) -> None:
    rows, cols = len(matrix), len(matrix[0])
    zero_rows, zero_cols = set(), set()
    for r in range(rows):
        for c in range(cols):
            if matrix[r][c] == 0:
                zero_rows.add(r)
                zero_cols.add(c)
    for r in range(rows):
        for c in range(cols):
            if r in zero_rows or c in zero_cols:
                matrix[r][c] = 0
```

**Trigger**: `matrix = [[1,1,1],[1,0,1],[1,1,1]]`. Correct output zeros only the middle row and middle column, leaving the four corners as 1. The broken version zeros the middle cross, but when the scan reaches `(1,0)` — now a 0 it just wrote — it zeros row 1 and column 0 again, and the cascade eventually flips the entire matrix to 0. The same "mutate-while-scanning" bug is exactly why **Game of Life** must encode the next state in spare bits instead of overwriting cells live.

---

## 9. Related Patterns & When to Switch

- **[Graph Traversal](graph_traversal.md)** — switch the instant the problem is about connectivity: islands, flood fill, regions, reachability. Those need a visited-set and a frontier; matrix manipulation does not.
- **[Shortest Path](shortest_path.md)** — switch for "fewest steps / minimum cost to move across the grid" (BFS, 0-1 BFS, Dijkstra). Movement-with-cost is not coordinate rewriting.
- **[Prefix Sum](prefix_sum.md)** — switch (or compose) for repeated submatrix-sum queries; the 2D summed-area table is the tool.
- **[Modified Binary Search](modified_binary_search.md)** — switch for value lookups in a sorted matrix; flatten-and-search for fully sorted, staircase for row/column sorted.
- **[Dynamic Programming](dynamic_programming.md)** — switch when the grid is a *DP table* (paths, min cost), not the object being physically transformed.

---

## 10. Cross-links

- Concept module: [arrays_strings_and_hashing](../arrays_strings_and_hashing/) — 2D array layout, row-major storage, index arithmetic
- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/) — why O(m·n) is optimal (you must read every cell at least once)
- Sibling patterns: [graph_traversal.md](graph_traversal.md) (grid connectivity), [prefix_sum.md](prefix_sum.md) (2D summed-area table), [modified_binary_search.md](modified_binary_search.md) (sorted-matrix search)
- Applied: [`../../cs_fundamentals/computer_architecture_and_memory_hierarchy/`](../computer_architecture_and_memory_hierarchy/) — row-major vs column-major access and cache behavior (why iterating along rows is faster than along columns)
- Master index: [dsa_patterns/README.md](README.md)

---

## 11. Interview Q&A

**Q: Why is matrix manipulation a separate pattern from graph traversal if both operate on a grid?**
Because the *mechanism* differs. Graph traversal explores **adjacency/reachability** and needs a frontier (queue/stack) plus a visited-set; matrix manipulation reads or rewrites cells at **computed coordinates** with no notion of "neighbor reachable from here," frequently in O(1) extra space. "Number of islands" is graph traversal; "rotate the image in place" is matrix manipulation. The giveaway is the O(1)-space follow-up — you cannot do connectivity in O(1) space, but you can rotate, spiral, and zero in O(1) space via coordinate arithmetic.

**Q: How do you rotate an n×n matrix 90° clockwise in place, and why does transpose + reverse work?**
Transpose swaps `a[i][j]` with `a[j][i]`, mirroring across the main diagonal; reversing each row then sends column `j` to column `n-1-j`. Composing a diagonal mirror with a horizontal mirror yields a 90° rotation. For counter-clockwise, transpose then reverse the *order of the rows* (vertical flip) instead. Both are O(n²) time, O(1) space, and avoid the four-way corner-cycle bookkeeping that is easy to get wrong under pressure.

**Q: In "Set Matrix Zeroes," what breaks if you zero rows/columns as you scan, and how do you fix it to O(1) space?**
Zeroing live corrupts cells you haven't read, so the zeros cascade and over-zero the matrix (see §8). The two-pass fix detects first, applies second. To reach O(1) space, use row 0 and column 0 as the marker arrays: a `0` at `(r,c)` sets `matrix[r][0]=0` and `matrix[0][c]=0`. Handle the first row and first column separately (with two boolean flags) because they double as storage and as real data.

**Q: How do you traverse a matrix diagonally, and what's the key indexing fact?**
All cells on the same anti-diagonal share a constant `r + c` (and main diagonals share constant `r - c`). Bucket cells by `r + c` into groups `0 .. m+n-2`; within each group, emit top-to-bottom or bottom-to-top. For LC 498's zig-zag, reverse every other diagonal — e.g., emit even-sum diagonals upward and odd-sum diagonals downward.

**Q: Explain the 2-bit encoding trick for Game of Life.**
You must compute the next generation from the current one without a second matrix. Reserve bit 0 for the current state and bit 1 for the next state. In pass 1, compute each cell's next state by counting neighbors using only `cell & 1` (the untouched current bit), and write the next state into bit 1 via `cell |= next_state << 1`. In pass 2, do `cell >>= 1` everywhere to commit. This is the in-place answer to the standard follow-up; the general principle is "encode two states per cell when you must read the old while writing the new."

**Q: For a sorted matrix, when do you flatten-and-binary-search versus walk a staircase?**
If the matrix is **fully** sorted (each row sorted, and each row's first element greater than the previous row's last — LC 74), treat it as one array of length `m·n` and binary search in O(log(m·n)); map index `k` to `(k // cols, k % cols)`. If it is only **row-and-column sorted** (LC 240), there's no global order, so start at the **top-right** corner: if the value is larger than the target move left, if smaller move down — eliminating a row or column each step for O(m+n).

**Q: Why does the spiral template need those two `if` guards, and what bug do they prevent?**
After walking the top row and right column you shrink `top` and `right`. The guards `if top <= bottom` and `if left <= right` ensure the bottom row and left column still exist before walking them. Without the guards, a single remaining row or column (common in non-square or odd-dimension matrices) gets walked twice and elements are emitted in duplicate. Concretely, for a single-row matrix the bottom-row pass would re-emit the row you already consumed.

**Q: What's the time and space complexity of these problems, and can space always be O(1)?**
Time is O(m·n) — you must touch every cell at least once, so this is optimal. Extra space is O(1) for in-place transforms (rotate, set-zeroes with marker rows, Game of Life), since the grid stores its own bookkeeping. Read-only traversals (spiral, diagonal) are O(1) *auxiliary* but produce an O(m·n) output list, which is required, not overhead. The only time you truly can't hit O(1) extra space is when the problem fundamentally needs a separate structure — but most "rewrite the grid" problems can.

**Q: How do you map a 1D index to 2D coordinates and back, and where does that matter?**
For a matrix with `cols` columns, cell `(r, c)` is linear index `r * cols + c`; inversely, `r = k // cols` and `c = k % cols`. This underpins flatten-and-binary-search (LC 74) and any "treat the grid as a flat array" trick. The analogous decomposition `(r // 3) * 3 + (c // 3)` maps a Sudoku cell to its 3×3 box id — the same index-arithmetic idea applied to sub-blocks.

**Q: How would you rotate the four corners of a layer in a single pass instead of transpose + reverse?**
For each layer `l` from `0` to `n//2 - 1`, and each offset `i` within the layer, cycle four cells: `top-left -> top-right -> bottom-right -> bottom-left -> top-left`, using a temp. The index map for clockwise is `a[l][l+i] <- a[n-1-l-i][l] <- a[n-1-l][n-1-l-i] <- a[l+i][n-1-l] <- (saved top-left)`. It's O(1) space like transpose+reverse but touches each cell exactly once; most engineers find transpose+reverse easier to write correctly, so state both and pick the clearer one aloud.

**Q: What's the difference between reflecting across the main diagonal versus the anti-diagonal?**
Main-diagonal reflection (transpose) swaps `a[i][j]` with `a[j][i]`. Anti-diagonal reflection swaps `a[i][j]` with `a[n-1-j][n-1-i]`. They are different mirror lines, and composing each with a row/column reverse yields different rotations — main-diagonal + row-reverse gives 90° CW, while anti-diagonal + row-reverse gives 90° CCW. Naming the exact mirror prevents the classic "rotated the wrong direction" bug.

**Q: When iterating a large matrix, why can row-major traversal be much faster than column-major even at the same O(m·n)?**
Memory layout. In row-major storage (C, Python lists of lists approximately, NumPy default), consecutive elements of a row are contiguous, so iterating `for r: for c:` walks memory sequentially and is cache-friendly; iterating `for c: for r:` jumps `cols` elements each step, causing cache misses and TLB pressure. Big-O is identical, but the constant factor can differ by an order of magnitude — see [computer_architecture_and_memory_hierarchy](../computer_architecture_and_memory_hierarchy/). Mentioning this signals systems awareness beyond raw algorithmic correctness.
