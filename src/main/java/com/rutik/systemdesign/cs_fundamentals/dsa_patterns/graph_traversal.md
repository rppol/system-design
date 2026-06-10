# Graph Traversal (Grid BFS/DFS, Islands, Multi-Source BFS)

## Pattern Snapshot

**What it is**: Systematically visit every reachable cell/node from one or more
starting points using BFS or DFS, marking each as `visited` so it is processed
exactly once. The grid is just a graph where each cell is a node and its 4 (or
8) neighbors are edges.

**One-line cue**: "Count islands / connected regions / flood fill / spread
from multiple sources simultaneously" on a grid or general graph, with **no
edge weights**.

**Typical complexity**: `O(rows * cols)` for grids, `O(V + E)` for general
graphs — every cell/node and edge is visited at most once (or twice for
undirected edges).

---

## 1. Recognition Signals

**Use graph traversal when you see:**
- "Number of islands" / "connected components" / "count regions"
- "Flood fill" — repaint a connected region
- "Rotting oranges" / "walls and gates" — something **spreads** from multiple
  starting cells simultaneously, layer by layer
- "Clone a graph" — rebuild a graph structure node-by-node
- "Surrounded regions" — mark cells based on connectivity to the border
- "Word ladder" — each word is a node; an edge exists between words that
  differ by one letter (an *implicit* graph, built on the fly)
- "Pacific Atlantic water flow" — reachability from two different border sets
- A 2D grid of `0`/`1`, `'O'`/`'X'`, or similar, with neighbor relationships
- The graph is **unweighted** — every edge "costs" the same to traverse

**Anti-signals (looks similar, use a different pattern):**
- The structure is a **tree** (each node has exactly one parent, no cycles to
  worry about, no `visited` set needed) -> [`tree_bfs.md`](tree_bfs.md) /
  [`tree_dfs.md`](tree_dfs.md)
- Edges have **weights** and you need shortest distance ->
  [`shortest_path.md`](shortest_path.md) (Dijkstra / Bellman-Ford / 0-1 BFS)
- "Build order" / "course prerequisites" / "can finish all tasks" — a DAG
  dependency problem -> [`topological_sort.md`](topological_sort.md)
- "Are these two nodes ever in the same group, with unions arriving over
  time" — incremental/dynamic connectivity ->
  [`union_find.md`](union_find.md) (DFS recomputes from scratch each time;
  Union-Find amortizes near O(1) per query)
- "Find all words in a dictionary that are prefixes of X" — trie, not
  traversal -> [`trie_patterns.md`](trie_patterns.md)

---

## 2. Mental Model & Intuition

A grid is a graph in disguise. Each cell `(r, c)` is a node; its up/down/
left/right neighbors are its edges. The `visited` set (or in-place mutation)
is what turns a graph with cycles into something a simple recursive/queue-
based walk can handle without looping forever.

```
Grid (1 = land, 0 = water):          Island #1 (DFS flood fill from (0,0)):

  0   1   2   3                        0   1   2   3
0 1 . 1 . 0 . 0                      0 [#] . [#] . 0 . 0
1 1 . 1 . 0 . 0                      1 [#] . [#] . 0 . 0
2 0 . 0 . 1 . 0                      2  0  .  0  . 1 . 0
3 0 . 0 . 0 . 1                      3  0  .  0  . 0 . 1

DFS from (0,0): visit (0,0) -> mark visited
   -> push neighbors (0,1)? out of land -> skip
   -> (1,0) is land, visited it too
   -> from (1,0): (1,1) is land too... etc.
All four '1's connected to (0,0) get swallowed into ONE island.
The lone '1' at (2,2) and the lone '1' at (3,3) are separate islands.
```

**Multi-source BFS** is the same idea but the queue starts with *all* sources
at once — every cell expands in lockstep, so when a cell is first reached, it
is reached via the shortest possible number of steps from *any* source:

```
Rotting Oranges (2 = rotten, 1 = fresh, 0 = empty):

  Minute 0          Minute 1          Minute 2
  2 1 1             2 2 1             2 2 2
  1 1 0      ->     2 2 0      ->     2 2 0
  0 1 1             0 2 1             0 2 2

Queue starts with BOTH rotten oranges (multi-source).
Each BFS "level" = one minute. Answer = number of levels until no fresh left.
```

---

## 3. The Template

```python
from __future__ import annotations
from collections import deque
from typing import Optional

# ---------------------------------------------------------------------------
# Template 1: DFS flood fill (recursive) — count connected components
# ---------------------------------------------------------------------------
def num_islands(grid: list[list[str]]) -> int:
    if not grid:
        return 0

    rows, cols = len(grid), len(grid[0])

    def dfs(r: int, c: int) -> None:
        # Bounds check + already-water/visited check, all in one guard
        if r < 0 or r >= rows or c < 0 or c >= cols or grid[r][c] != "1":
            return
        grid[r][c] = "0"  # mark visited BEFORE recursing (sink the land)
        dfs(r + 1, c)
        dfs(r - 1, c)
        dfs(r, c + 1)
        dfs(r, c - 1)

    islands = 0
    for r in range(rows):
        for c in range(cols):
            if grid[r][c] == "1":
                islands += 1
                dfs(r, c)
    return islands


# ---------------------------------------------------------------------------
# Template 2: BFS shortest path on an unweighted grid
# ---------------------------------------------------------------------------
DIRECTIONS_4 = [(1, 0), (-1, 0), (0, 1), (0, -1)]

def bfs_shortest_path(grid: list[list[int]], start: tuple[int, int],
                       end: tuple[int, int]) -> int:
    rows, cols = len(grid), len(grid[0])
    queue: deque[tuple[int, int, int]] = deque([(start[0], start[1], 0)])
    visited = {start}

    while queue:
        r, c, dist = queue.popleft()
        if (r, c) == end:
            return dist
        for dr, dc in DIRECTIONS_4:
            nr, nc = r + dr, c + dc
            if (0 <= nr < rows and 0 <= nc < cols
                    and (nr, nc) not in visited and grid[nr][nc] != 1):
                visited.add((nr, nc))
                queue.append((nr, nc, dist + 1))
    return -1  # unreachable


# ---------------------------------------------------------------------------
# Template 3: Multi-source BFS (Rotting Oranges style)
# ---------------------------------------------------------------------------
def multi_source_bfs(grid: list[list[int]]) -> int:
    rows, cols = len(grid), len(grid[0])
    queue: deque[tuple[int, int]] = deque()
    fresh = 0

    for r in range(rows):
        for c in range(cols):
            if grid[r][c] == 2:
                queue.append((r, c))   # seed ALL sources up front
            elif grid[r][c] == 1:
                fresh += 1

    minutes = 0
    while queue and fresh > 0:
        minutes += 1
        for _ in range(len(queue)):       # process one full layer (BFS level)
            r, c = queue.popleft()
            for dr, dc in DIRECTIONS_4:
                nr, nc = r + dr, c + dc
                if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] == 1:
                    grid[nr][nc] = 2
                    fresh -= 1
                    queue.append((nr, nc))

    return minutes if fresh == 0 else -1


# ---------------------------------------------------------------------------
# Template 4: Clone Graph (DFS + hashmap old-node -> new-node)
# ---------------------------------------------------------------------------
class GraphNode:
    def __init__(self, val: int = 0, neighbors: Optional[list["GraphNode"]] = None):
        self.val = val
        self.neighbors = neighbors or []

def clone_graph(node: Optional[GraphNode]) -> Optional[GraphNode]:
    if node is None:
        return None

    old_to_new: dict[GraphNode, GraphNode] = {}

    def dfs(curr: GraphNode) -> GraphNode:
        if curr in old_to_new:
            return old_to_new[curr]
        copy = GraphNode(curr.val)
        old_to_new[curr] = copy            # register BEFORE recursing (cycles!)
        for neighbor in curr.neighbors:
            copy.neighbors.append(dfs(neighbor))
        return copy

    return dfs(node)
```

---

## 4. Annotated Walkthrough

**Problem**: [Number of Islands (LC 200)](https://leetcode.com/problems/number-of-islands/)
Given a 4x4 grid, count islands (groups of `'1'`s connected 4-directionally).

```
Grid:
  0 1 2 3
0 1 1 0 0
1 1 1 0 0
2 0 0 1 0
3 0 0 0 1
```

**Trace**:

```
Outer scan reaches (0,0) = "1"  -> islands = 1, dfs(0,0)

dfs(0,0): grid[0][0]="1" -> sink to "0"
  -> dfs(1,0): grid[1][0]="1" -> sink to "0"
       -> dfs(2,0): grid[2][0]="0" -> return (out of land)
       -> dfs(0,0): now "0" -> return (already sunk)
       -> dfs(1,1): grid[1][1]="1" -> sink to "0"
            -> dfs(2,1): "0" -> return
            -> dfs(0,1): grid[0][1]="1" -> sink to "0"
                 -> dfs(1,1): now "0" -> return
                 -> dfs(-1,1): out of bounds -> return
                 -> dfs(0,2): "0" -> return
                 -> dfs(0,0): now "0" -> return
            -> dfs(1,2): "0" -> return
            -> dfs(1,0): now "0" -> return
       -> dfs(0,0): now "0" -> return
  -> dfs(-1,0): out of bounds -> return
  -> dfs(0,1): now "0" -> return
  -> dfs(0,-1): out of bounds -> return

Grid is now:
  0 0 0 0
  0 0 0 0
  0 0 1 0
  0 0 0 1

Outer scan continues:
(2,2) = "1" -> islands = 2, dfs(2,2) sinks just that one cell
(3,3) = "1" -> islands = 3, dfs(3,3) sinks just that one cell

Final answer: islands = 3
```

The four connected `1`s in the top-left collapse into a **single** DFS call
tree because each recursive call sinks the cell to `"0"` *before* fanning out
to neighbors — that's what prevents re-visiting and what lets the outer scan
treat the whole blob as already counted.

---

## 5. Complexity

| Template | Time | Space | Notes |
|---|---|---|---|
| `num_islands` (DFS) | O(rows × cols) | O(rows × cols) worst-case recursion depth (one long snake-shaped island) | In-place mutation avoids a separate `visited` set |
| `bfs_shortest_path` | O(rows × cols) | O(rows × cols) for `visited` + queue | BFS guarantees first-visit = shortest path (unweighted) |
| `multi_source_bfs` | O(rows × cols) | O(rows × cols) for queue | Each cell enqueued at most once |
| `clone_graph` | O(V + E) | O(V) for hashmap + recursion stack | Hashmap also breaks infinite recursion on cycles |

A recursive DFS on a grid can hit Python's recursion limit (~1000) for a grid
with a path longer than ~1000 cells (e.g., a 32x32 fully-connected grid is
1024 cells). For large grids, prefer the **iterative DFS with an explicit
stack** or BFS with a deque — same complexity, no recursion-depth risk.

---

## 6. Variations & Sub-patterns

**Multi-source BFS** ([Rotting Oranges (LC 994)](https://leetcode.com/problems/rotting-oranges/),
[Walls and Gates (LC 286)](https://leetcode.com/problems/walls-and-gates/)):
seed the queue with *every* source cell before the first BFS level runs. The
number of levels processed = the answer (minutes elapsed, or distance to
nearest gate).

**Flood fill** ([LC 733](https://leetcode.com/problems/flood-fill/)): identical
to `num_islands`'s DFS, but instead of sinking to `"0"`, repaint to a new
color. Watch for the edge case where `new_color == old_color` — without a
guard, this causes infinite recursion (the "already painted" check never
triggers because the cell never changes).

**Surrounded Regions** ([LC 130](https://leetcode.com/problems/surrounded-regions/)):
flip the problem — instead of finding regions surrounded by `'X'`, find
regions connected to the **border** (which can never be surrounded) and mark
those as safe. Run DFS/BFS from every border `'O'` first, then flip all
*unmarked* `'O'`s to `'X'` in a final pass.

**Pacific Atlantic Water Flow** ([LC 417](https://leetcode.com/problems/pacific-atlantic-water-flow/)):
two separate multi-source BFS/DFS runs — one seeded from all Pacific-adjacent
border cells, one from all Atlantic-adjacent border cells — flowing
*uphill* (reverse of water flow). The answer is the intersection of the two
reachable sets. Running this border-inward (2 traversals) is far cheaper than
checking, for every cell, whether water can reach both oceans (which would be
O((rows·cols)²)).

**Word Ladder** ([LC 127](https://leetcode.com/problems/word-ladder/)): the
graph is **implicit** — there's no adjacency list to begin with. An edge
exists between two words iff they differ by exactly one letter. Rather than
checking all O(n²) word pairs, generate each word's neighbors by trying all
26 letters at each position (`O(L · 26)` per word) and looking up the result
in a word set — this turns an O(n² · L) edge-discovery cost into
O(n · L · 26).

**4-directional vs. 8-directional**: most grid problems use
`DIRECTIONS_4` (up/down/left/right). Problems involving diagonal adjacency
(rare, but occurs in some "image region" problems) extend the directions list
with `(1,1), (1,-1), (-1,1), (-1,-1)`.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue/twist |
|---|---|---|---|
| [Number of Islands (LC 200)](https://leetcode.com/problems/number-of-islands/) | Medium | DFS flood fill | The canonical signature problem |
| [Flood Fill (LC 733)](https://leetcode.com/problems/flood-fill/) | Easy | DFS repaint | Guard against `new_color == old_color` infinite loop |
| [Max Area of Island (LC 695)](https://leetcode.com/problems/max-area-of-island/) | Medium | DFS with size accumulation | DFS returns `1 + sum(dfs(neighbors))` |
| [Rotting Oranges (LC 994)](https://leetcode.com/problems/rotting-oranges/) | Medium | Multi-source BFS | Seed ALL rotten oranges at once; track `fresh` count |
| [Walls and Gates (LC 286)](https://leetcode.com/problems/walls-and-gates/) | Medium | Multi-source BFS, in-place distance | Seed all gates (0s); fill INF cells with BFS distance |
| [Clone Graph (LC 133)](https://leetcode.com/problems/clone-graph/) | Medium | DFS + hashmap | Hashmap maps old node -> new node, registered before recursing (cycle-safe) |
| [Surrounded Regions (LC 130)](https://leetcode.com/problems/surrounded-regions/) | Medium | Border-first DFS | Invert the problem: mark border-connected 'O's as safe first |
| [Pacific Atlantic Water Flow (LC 417)](https://leetcode.com/problems/pacific-atlantic-water-flow/) | Medium | Two multi-source traversals + intersection | Flow "uphill" from both oceans' borders |
| [Word Ladder (LC 127)](https://leetcode.com/problems/word-ladder/) | Hard | Implicit graph BFS | Generate neighbors via 26-letter substitution, not pairwise comparison |
| [Number of Connected Components in an Undirected Graph (LC 323)](https://leetcode.com/problems/number-of-connected-components-in-an-undirected-graph/) | Medium | General-graph DFS/BFS | Build adjacency list first; same "count components" idea as islands |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake**: marking a cell as visited *after* recursing into it, instead of
*before*. This causes the same cell to be revisited by every neighbor that
points back to it, leading to infinite recursion (or a `RecursionError`) on
any region with a cycle (which a 2x2+ block of land always has).

```python
# BROKEN: marks visited too late
def dfs_broken(grid, r, c, rows, cols):
    if r < 0 or r >= rows or c < 0 or c >= cols or grid[r][c] != "1":
        return
    dfs_broken(grid, r + 1, c, rows, cols)
    dfs_broken(grid, r - 1, c, rows, cols)
    dfs_broken(grid, r, c + 1, rows, cols)
    dfs_broken(grid, r, c - 1, rows, cols)
    grid[r][c] = "0"   # too late — neighbors already recursed back here
```

**Trace the bug** on a 2x2 all-land grid:

```
1 1
1 1

dfs_broken(0,0): grid[0][0]="1", not yet sunk
  -> dfs_broken(1,0): grid[1][0]="1", not yet sunk
       -> dfs_broken(2,0): out of bounds, return
       -> dfs_broken(0,0): grid[0][0] is STILL "1" (not sunk yet!)
            -> dfs_broken(1,0): grid[1][0] is STILL "1" (not sunk yet!)
                 -> ... infinite recursion -> RecursionError
```

Because `grid[r][c] = "0"` only happens *after* all four recursive calls
return, every cell in a cycle keeps calling its neighbors which call it back,
forever.

**Fix**: mark the cell visited (sink it) **immediately**, as the very first
mutation, before any recursive call:

```python
# FIXED: marks visited BEFORE recursing
def dfs_fixed(grid, r, c, rows, cols):
    if r < 0 or r >= rows or c < 0 or c >= cols or grid[r][c] != "1":
        return
    grid[r][c] = "0"   # mark visited FIRST
    dfs_fixed(grid, r + 1, c, rows, cols)
    dfs_fixed(grid, r - 1, c, rows, cols)
    dfs_fixed(grid, r, c + 1, rows, cols)
    dfs_fixed(grid, r, c - 1, rows, cols)
```

**Re-trace with the fix** on the same 2x2 grid:

```
dfs_fixed(0,0): grid[0][0]="1" -> sink to "0" immediately
  -> dfs_fixed(1,0): grid[1][0]="1" -> sink to "0" immediately
       -> dfs_fixed(2,0): out of bounds, return
       -> dfs_fixed(0,0): grid[0][0] is now "0" -> guard returns immediately
       -> dfs_fixed(1,1): grid[1][1]="1" -> sink to "0" immediately
            -> ... (similarly terminates)
       -> dfs_fixed(1,-1): out of bounds, return
  -> dfs_fixed(-1,0): out of bounds, return
  -> dfs_fixed(0,1): grid[0][1]="1" -> sink to "0" immediately
       -> ... terminates similarly
  -> dfs_fixed(0,-1): out of bounds, return

All 4 cells sunk to "0" in a single, terminating DFS call tree.
```

The same "mark before you recurse/enqueue" rule applies to BFS: mark a cell
visited (or add it to the `visited` set) **at the moment you enqueue it**, not
when you dequeue it — otherwise the same cell can be enqueued multiple times
by different neighbors before it's ever processed.

---

## 9. Related Patterns & When to Switch

- **[`tree_bfs.md`](tree_bfs.md) / [`tree_dfs.md`](tree_dfs.md)** — if the
  structure is a tree (no cycles, single parent per node), you don't need a
  `visited` set at all. Reach for these when the problem explicitly gives you
  a `TreeNode` with `left`/`right`.
- **[`topological_sort.md`](topological_sort.md)** — if the problem talks
  about *dependencies*, *prerequisites*, or "build order" on a **directed**
  graph, you need cycle detection + ordering, not just connectivity.
- **[`union_find.md`](union_find.md)** — if connectivity queries arrive
  *incrementally* (edges added one at a time, "are these now connected?"),
  Union-Find amortizes to near O(1) per query versus re-running DFS from
  scratch each time.
- **[`shortest_path.md`](shortest_path.md)** — the moment edges have
  *weights*, plain BFS no longer guarantees shortest distance; you need
  Dijkstra (non-negative weights), Bellman-Ford (negative weights allowed), or
  0-1 BFS (only weights 0/1).
- **[`backtracking.md`](backtracking.md)** — if the problem asks you to
  *generate all paths* (not just whether a path/connection exists), and the
  search space requires undoing choices (un-visiting cells to explore other
  branches), that's backtracking, not plain traversal.

---

## 10. Cross-links

- Concept module: [`graphs_tries_and_advanced_structures/`](../graphs_tries_and_advanced_structures/README.md) —
  adjacency list/matrix representations, BFS/DFS complexity proofs
- Concept module: [`graph_and_string_algorithms/`](../graph_and_string_algorithms/README.md) —
  formal BFS/DFS pseudocode, proof of BFS shortest-path correctness on
  unweighted graphs
- Applied: [`../../hld/`](../../hld/README.md) — Bloom filters as a
  probabilistic "have I seen this node?" structure at scale, conceptually
  related to the `visited` set here but trading correctness for memory

---

## 11. Interview Q&A

**Why do graph traversals need a `visited` set but tree traversals don't?**
A tree has no cycles and exactly one path from the root to any node, so a
recursive/BFS walk can never revisit a node. A graph (and a grid, which is
just a graph) can have cycles — e.g., a 2x2 block of land has 4 cells each
adjacent to 2 others, forming a cycle. Without marking visited cells, a
traversal would loop forever, which is exactly the bug in §8.

**When do you use BFS vs. DFS for grid problems?**
For *counting* connected components (islands) or *generating a path*
(backtracking-style), DFS is simpler and uses less code. For finding the
*shortest* path/distance in an unweighted grid, or for "spreads simultaneously
from multiple sources" problems (Rotting Oranges), BFS is required because it
explores in increasing-distance order — DFS would find *a* path, not
necessarily the *shortest* one.

**Why seed a multi-source BFS with all sources at once instead of running BFS
from each source separately?**
Running BFS once per source and taking the minimum would be correct but
wasteful — O(k · rows · cols) for k sources. Seeding the queue with all k
sources at the start means the BFS naturally computes, for every cell, the
distance to the *nearest* source in a single O(rows · cols) pass, because BFS
processes cells in increasing-distance order regardless of which source they
came from.

**In the BROKEN→FIX example, why exactly does marking visited "too late" cause
infinite recursion, and not just extra work?**
Because the guard condition `grid[r][c] != "1"` is the *only* thing that stops
recursion, and it's checked at the *top* of each call using the grid's current
state. If `grid[r][c]` is still `"1"` when a neighbor recurses back into
`(r, c)`, the guard passes, and that call recurses back to its own neighbors —
including the original caller — again with `grid` unchanged. There's no
decreasing quantity (no "smaller subproblem"), so the recursion never reaches
a base case — it's not just slow, it never terminates (until Python's stack
limit raises `RecursionError`).

**Why does Clone Graph need a hashmap instead of just a `visited` set?**
A `visited` set only answers "have I processed this node?" — it doesn't tell
you *what the corresponding new node is*. When neighbor B's clone needs to
point back to neighbor A's clone (a cycle), you need to look up "the new node
that corresponds to old node A," which requires a mapping (`old_to_new`), not
just a boolean set. Registering the new node in the map *before* recursing
into its neighbors is what makes cycles terminate correctly.

**Why does Word Ladder generate neighbors via 26-letter substitution instead
of comparing every pair of words?**
Comparing every pair of n words to check "differs by one letter" costs
O(n² · L) where L is word length. Generating neighbors by trying all 26
letters at each of L positions costs O(L · 26) per word, or O(n · L · 26)
total — and a hash-set lookup confirms whether the generated word exists in
the dictionary. For n in the thousands, this is the difference between
roughly 10^7 and 10^10+ operations.

**How do you choose between 4-directional and 8-directional movement?**
Read the problem statement's definition of "adjacent" — it will explicitly
say "horizontally or vertically adjacent" (4-directional, the overwhelming
majority of problems) or mention diagonal connections (8-directional). When
in doubt, 4-directional (`DIRECTIONS_4`) is the default; extending to 8
directions is a one-line change to the directions list.

**Why is Pacific Atlantic Water Flow solved with two traversals from the
borders instead of one traversal per cell?**
Checking, from each cell, whether water can reach both oceans would require a
traversal *from* that cell — O(rows · cols) work per cell, O((rows·cols)²)
total. Instead, run two multi-source traversals *backwards* (uphill) from
every Pacific-adjacent border cell and every Atlantic-adjacent border cell —
O(rows · cols) each. A cell can reach an ocean iff that ocean's
backwards-traversal reached the cell. The answer is the intersection of the
two reachable sets — O(rows · cols) total instead of quadratic.

**What's the difference between Surrounded Regions and Number of Islands —
both involve counting/marking regions of the same character?**
Number of Islands counts *all* connected regions independently. Surrounded
Regions requires knowing whether a region touches the border — which is a
*global* property, not determinable by looking at the region in isolation.
The trick is to invert the search: instead of checking "is this region
surrounded," find all regions connected to the border (which can never be
surrounded) first, mark them safe, then flip everything else.

**Can recursive DFS on a grid cause a stack overflow, and how do you avoid
it?**
Yes — Python's default recursion limit is ~1000, and a single long, winding
island can have a DFS call depth equal to its cell count. A grid as small as
32x32 (1024 cells) can exceed the limit if fully connected in a snake shape.
The fix is an **iterative DFS using an explicit stack** (a list, push/pop) or
switching to BFS with a `deque` — both have identical time/space complexity
but no call-stack depth limit (bounded only by available memory).

**How would you adapt `num_islands` to also return the size of the largest
island (Max Area of Island, LC 695)?**
Change `dfs` from a `None`-returning side-effecting function to one that
*returns* `1 + dfs(up) + dfs(down) + dfs(left) + dfs(right)` (with the same
"sink before recurse" rule, returning `0` for out-of-bounds/water cells).
Track `max(area, dfs(r, c))` in the outer loop instead of just incrementing a
counter.

**In `bfs_shortest_path`, why must you check `(nr, nc) not in visited` *before*
appending to the queue, rather than when popping?**
If you only check `visited` when popping, the same cell can be appended to the
queue multiple times by different neighbors *before* any of those copies are
popped — wasting memory and (for the *first* discovered distance to be
correct) potentially processing a cell at a non-minimal distance if a shorter
path's copy is processed later. Marking visited at *enqueue* time guarantees
each cell is enqueued exactly once, with its true shortest distance (BFS
processes cells in non-decreasing distance order).
