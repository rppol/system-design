# Union-Find (Disjoint Set Union)

## Pattern Snapshot

**What it is**: A data structure that maintains a collection of disjoint
(non-overlapping) sets, supporting two operations near-O(1) amortized:
`find(x)` — which set is `x` in? — and `union(x, y)` — merge `x`'s and `y`'s
sets. Implemented as a forest where each tree is one set and the root is the
set's representative.

**One-line cue**: "Are these two things in the same group, where groups merge
incrementally as edges/connections arrive?"

**Typical complexity**: `O(α(n))` amortized per operation — `α` is the
inverse Ackermann function, which is `<= 4` for any `n` you'll ever encounter
in practice. Effectively O(1).

---

## 1. Recognition Signals

**Use Union-Find when you see:**
- "Redundant connection" — find the edge that, when added, creates a cycle
- "Number of connected components" given as a **list of edges** (not a grid
  or adjacency list you'd traverse once)
- "Accounts merge" / "merge similar items" — group elements that share some
  attribute, transitively
- "Are X and Y in the same group/circle/network?" with **incremental**
  unions arriving one at a time
- "Number of islands II" — cells/edges are added **dynamically** over time,
  and you need the component count *after each addition*
- "Minimum cost to connect all points/cities" — Kruskal's MST
- "Smallest equivalent string" / "evaluate division" — relationships that
  need to be grouped (and sometimes weighted) transitively
- "Graph valid tree" — exactly `n-1` edges AND no cycle AND fully connected

**Anti-signals (looks similar, use a different pattern):**
- A **single, static** connectivity query over a graph/grid you only need to
  traverse once -> [`graph_traversal.md`](graph_traversal.md) (DFS/BFS is
  simpler — no need for the union-find machinery)
- The graph is **directed** and you care about ordering/cycles that respect
  direction -> [`topological_sort.md`](topological_sort.md) (Union-Find
  cannot represent edge direction)
- You need actual **shortest path / distance** between two nodes, not just
  "are they connected" -> [`shortest_path.md`](shortest_path.md)
- The structure is a **tree** already (no cycles possible by definition) ->
  [`tree_dfs.md`](tree_dfs.md) / [`tree_bfs.md`](tree_bfs.md)

---

## 2. Mental Model & Intuition

Each set is a tree; the tree's root is that set's "representative." `find(x)`
walks up parent pointers to the root. `union(x, y)` finds both roots and
attaches one under the other. **Path compression** flattens the tree every
time `find` is called, so future calls are nearly O(1).

```
Initial: every node is its own root (5 separate sets)

  0   1   2   3   4

union(0, 1):  attach 1 under 0

  0     2   3   4
  |
  1

union(2, 3):  attach 3 under 2

  0     2     4
  |     |
  1     3

union(0, 2):  attach 2's tree under 0's tree (union by rank)

      0         4
     / \
    1   2
        |
        3

find(3) BEFORE compression: 3 -> 2 -> 0   (2 hops to root)

find(3) WITH path compression: while walking 3 -> 2 -> 0,
rewrite parent[3] = 0 directly (and parent[2] = 0 too).

      0         4
    / | \
   1  2  3      <- both 2 and 3 now point directly at root 0

Next find(3) is now O(1): 3 -> 0 directly.
```

This is why repeated `find`/`union` calls on the same structure get *faster*
over time — the tree gets flatter with every `find`.

---

## 3. The Template

```python
from __future__ import annotations

# ---------------------------------------------------------------------------
# Template 1: Union-Find with path compression + union by rank
# ---------------------------------------------------------------------------
class UnionFind:
    def __init__(self, n: int) -> None:
        self.parent = list(range(n))   # parent[i] == i means i is a root
        self.rank = [0] * n            # upper bound on tree height
        self.count = n                 # number of disjoint sets remaining

    def find(self, x: int) -> int:
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])  # path compression
        return self.parent[x]

    def union(self, x: int, y: int) -> bool:
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return False  # already in the same set -- this edge is redundant

        # union by rank: attach the shorter tree under the taller one
        if self.rank[root_x] < self.rank[root_y]:
            root_x, root_y = root_y, root_x
        self.parent[root_y] = root_x
        if self.rank[root_x] == self.rank[root_y]:
            self.rank[root_x] += 1

        self.count -= 1
        return True

    def connected(self, x: int, y: int) -> bool:
        return self.find(x) == self.find(y)


# ---------------------------------------------------------------------------
# Template 2: Iterative find (path halving) + union by size
# ---------------------------------------------------------------------------
class UnionFindBySize:
    def __init__(self, n: int) -> None:
        self.parent = list(range(n))
        self.size = [1] * n

    def find(self, x: int) -> int:
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]  # path halving
            x = self.parent[x]
        return x

    def union(self, x: int, y: int) -> None:
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return
        if self.size[root_x] < self.size[root_y]:
            root_x, root_y = root_y, root_x
        self.parent[root_y] = root_x
        self.size[root_x] += self.size[root_y]
```

---

## 4. Annotated Walkthrough

**Problem**: [Redundant Connection (LC 684)](https://leetcode.com/problems/redundant-connection/)
`edges = [[1,2],[1,3],[2,3]]`, `n = 3` nodes (1-indexed). Find the edge that,
if removed, leaves a tree (i.e., the edge that creates the cycle).

**Setup**: `parent = [0, 1, 2, 3]` — size `n + 1 = 4` so node `n` (here, node
3) is a valid index. (Node 0 is unused since nodes are 1-indexed.)

**Trace**:

```
Process edge [1, 2]:
  find(1) = 1, find(2) = 2  -> different roots
  union(1, 2): attach 2 under 1 -> parent = [0, 1, 1, 3]

Process edge [1, 3]:
  find(1) = 1, find(3) = 3  -> different roots
  union(1, 3): attach 3 under 1 -> parent = [0, 1, 1, 1]

Process edge [2, 3]:
  find(2): parent[2]=1, parent[1]=1 -> root = 1
  find(3): parent[3]=1, parent[1]=1 -> root = 1
  SAME ROOT (both = 1) -> this edge creates a cycle!

Return [2, 3]
```

`[2, 3]` is the last edge in the input that connects two nodes already in the
same component — exactly the edge LC 684 expects (the *last* such edge,
since edges are processed in input order and the first two already form a
spanning tree over `{1, 2, 3}`).

---

## 5. Complexity

| Operation | Time (amortized) | Notes |
|---|---|---|
| `find(x)` | O(α(n)) | With path compression; `α` = inverse Ackermann, `<= 4` for `n < 2^65536` |
| `union(x, y)` | O(α(n)) | Two `find` calls + O(1) pointer update |
| `n` operations total | O(n · α(n)) ≈ O(n) | Effectively linear |

**With only union by rank (no path compression)**: O(log n) per operation —
the rank bound guarantees tree height `<= log2(n)`.

**With only path compression (no union by rank)**: O(log n) amortized — still
very fast, but the rank/size heuristic combined with compression is what
achieves the (better) inverse-Ackermann bound.

**With neither**: O(n) worst case — a pathological sequence of unions can
produce a degenerate linked-list-shaped tree.

---

## 6. Variations & Sub-patterns

**Tracking component count** (`self.count`): initialize to `n`, decrement by
1 on every *successful* `union` (i.e., when the two roots were different).
[Number of Provinces (LC 547)](https://leetcode.com/problems/number-of-provinces/)
and [Number of Operations to Make Network Connected (LC 1319)](https://leetcode.com/problems/number-of-operations-to-make-network-connected/)
both reduce to reading `self.count` (and, for LC 1319, also counting "extra"
redundant edges — you need at least `count - 1` extra edges to connect
everything).

**Union-Find on a 2D grid**
([Number of Islands II (LC 305)](https://leetcode.com/problems/number-of-islands-ii/)):
map each cell `(r, c)` to a single integer index `r * cols + c`, and run
`UnionFind(rows * cols)`. When a new land cell is added, union it with any
already-land neighbors. This shines over DFS here because the grid changes
**incrementally** — re-running DFS from scratch after every addition would be
O(k · rows · cols) for `k` additions; Union-Find amortizes to near O(k).

**Two-pass union-find**
([Satisfiability of Equality Equations (LC 990)](https://leetcode.com/problems/satisfiability-of-equality-equations/)):
process all `"=="` constraints first (union the variables), *then* check all
`"!="` constraints (verify the two variables are NOT in the same set — if they
are, the constraints are unsatisfiable). Order matters: you can't check `!=`
constraints before all `==` unions are complete.

**Weighted Union-Find**
([Evaluate Division (LC 399)](https://leetcode.com/problems/evaluate-division/)):
alongside `parent[x]`, store `weight[x]` = the ratio of `x` to `parent[x]`.
`find(x)` accumulates the product of weights along the path to the root
*and* updates `weight[x]` during path compression to be the ratio of `x` to
the (new) root directly. `union` combines two chains by computing the
relative ratio between their roots.

**Kruskal's MST**
([Min Cost to Connect All Points (LC 1584)](https://leetcode.com/problems/min-cost-to-connect-all-points/)):
sort all candidate edges by weight ascending. Iterate edges; for each, call
`union(u, v)` — if it returns `True` (they were in different components),
*accept* this edge and add its weight to the total. If it returns `False`
(already connected), *skip* it (adding it would create a cycle). Stop early
once `count == 1`.

**Redundant Connection II** ([LC 685](https://leetcode.com/problems/redundant-connection-ii/)):
the *directed* variant. A node can have at most one parent in a valid rooted
tree, so first check for a node with **two** incoming edges (in-degree 2) —
if found, one of those two edges is a *candidate* for removal. Then run
union-find as in LC 684 to check for a cycle, with extra logic to pick which
of the two candidates to actually remove. Notably trickier than the
undirected version — direction adds a second failure mode (two parents)
beyond just cycles.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue/twist |
|---|---|---|---|
| [Redundant Connection (LC 684)](https://leetcode.com/problems/redundant-connection/) | Medium | Cycle-edge detection | The signature problem — first edge whose endpoints already share a root |
| [Number of Provinces (LC 547)](https://leetcode.com/problems/number-of-provinces/) | Medium | Count components from adjacency matrix | `self.count` after unioning all matrix entries |
| [Graph Valid Tree (LC 261)](https://leetcode.com/problems/graph-valid-tree/) | Medium | n-1 edges AND fully connected | Both conditions needed — edge count alone isn't sufficient |
| [Satisfiability of Equality Equations (LC 990)](https://leetcode.com/problems/satisfiability-of-equality-equations/) | Medium | Two-pass union-find | Process `==` before `!=` |
| [Accounts Merge (LC 721)](https://leetcode.com/problems/accounts-merge/) | Medium | Union by shared attribute | Emails are the "elements," not account indices directly |
| [Number of Operations to Make Network Connected (LC 1319)](https://leetcode.com/problems/number-of-operations-to-make-network-connected/) | Medium | Components + redundant-edge counting | Need `count - 1` spare edges to connect everything |
| [Evaluate Division (LC 399)](https://leetcode.com/problems/evaluate-division/) | Medium | Weighted union-find | Track ratio/weight alongside parent pointer |
| [Number of Islands II (LC 305)](https://leetcode.com/problems/number-of-islands-ii/) | Hard | Dynamic grid connectivity | `(r, c) -> r * cols + c`; union-find shines for incremental adds |
| [Min Cost to Connect All Points (LC 1584)](https://leetcode.com/problems/min-cost-to-connect-all-points/) | Medium | Kruskal's MST | Sort all pairwise Manhattan distances, union-find skips cycle edges |
| [Redundant Connection II (LC 685)](https://leetcode.com/problems/redundant-connection-ii/) | Hard | Directed variant | Two failure modes: a node with 2 parents, or a cycle |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake**: initializing `parent` with size `n` for a problem where nodes are
**1-indexed** from `1` to `n` (so valid node values are `1..n`, requiring
indices `0..n` — size `n + 1`).

```python
# BROKEN: nodes are 1-indexed (1..n), but parent only has indices 0..n-1
class UnionFindBroken:
    def __init__(self, n):
        self.parent = list(range(n))  # size n: valid indices 0..n-1

    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])
        return self.parent[x]

    def union(self, x, y):
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return False
        self.parent[root_y] = root_x
        return True
```

**Trace the bug** on `n = 3`, `edges = [[1,2],[2,3],[3,1]]`:

```
uf = UnionFindBroken(3)
self.parent = [0, 1, 2]   # size 3, valid indices: 0, 1, 2

Process edge [1, 2]:
  find(1) -> parent[1]=1 -> returns 1   (OK, index 1 is valid)
  find(2) -> parent[2]=2 -> returns 2   (OK, index 2 is valid)
  union(1, 2): parent[2] = 1 -> parent = [0, 1, 1]

Process edge [2, 3]:
  find(2) -> parent[2]=1, parent[1]=1 -> returns 1   (OK)
  find(3) -> parent[3] -> IndexError: list index out of range
```

`parent` only has 3 slots (indices `0`, `1`, `2`), but node `3` is a valid
1-indexed node. `parent[3]` reaches past the end of the list.

**Fix**: allocate `n + 1` slots so index `n` is valid. The extra slot at
index `0` is simply unused.

```python
# FIXED: allocate n + 1 slots for 1-indexed nodes 1..n
class UnionFindFixed:
    def __init__(self, n):
        self.parent = list(range(n + 1))  # size n+1: valid indices 0..n

    def find(self, x):
        if self.parent[x] != x:
            self.parent[x] = self.find(self.parent[x])
        return self.parent[x]

    def union(self, x, y):
        root_x, root_y = self.find(x), self.find(y)
        if root_x == root_y:
            return False
        self.parent[root_y] = root_x
        return True
```

**Re-trace with the fix**: `self.parent = [0, 1, 2, 3]` (size 4, indices
`0..3`). `find(3)` now safely returns `parent[3] = 3` on first access. The
rest of the trace proceeds as shown in §4, correctly identifying `[2, 3]` as
the redundant edge.

This off-by-one is one of the most common Union-Find bugs in interviews —
**always check whether the problem's node numbering is 0-indexed or
1-indexed before sizing the `parent` array**, and size it `max_node_value + 1`
to be safe.

---

## 9. Related Patterns & When to Switch

- **[`graph_traversal.md`](graph_traversal.md)** — for a *single* static
  connectivity query (e.g., "how many islands are in this grid right now?"),
  DFS/BFS is simpler and just as fast. Reach for Union-Find when edges arrive
  **incrementally** or you need **many** repeated "are X and Y connected?"
  queries.
- **[`topological_sort.md`](topological_sort.md)** — Union-Find has no
  notion of edge *direction*. If the problem is about a directed graph
  (dependencies, prerequisites), you need 3-color DFS or Kahn's algorithm,
  not Union-Find.
- **[`merge_intervals.md`](merge_intervals.md)** — conceptually similar
  ("merge things that overlap/connect into groups"), but operates on sorted
  ranges along a number line, not on graph edges. Different data, different
  technique, same spirit of "grouping."
- **[`shortest_path.md`](shortest_path.md)** — Kruskal's MST *uses*
  Union-Find, but answers "minimum total edge weight to connect everything,"
  which is a different question from "shortest path between two specific
  nodes" (Dijkstra/Bellman-Ford answer the latter).

---

## 10. Cross-links

- Concept module: [`graphs_tries_and_advanced_structures/`](../graphs_tries_and_advanced_structures/README.md) —
  Disjoint Set Union data structure, amortized complexity proof (inverse
  Ackermann)
- Concept module: [`graph_and_string_algorithms/`](../graph_and_string_algorithms/README.md) —
  Kruskal's MST algorithm, where Union-Find is the core supporting structure

---

## 11. Interview Q&A

**Why is Union-Find described as "near O(1)" when `find` walks up a tree —
isn't that O(log n) or worse?**
Without any optimization, yes — a degenerate union sequence can produce an
O(n)-deep tree. But **path compression** (every `find` call rewires nodes on
its path directly to the root) combined with **union by rank/size** (always
attach the smaller tree under the larger) together bound the amortized cost
per operation to `O(α(n))`, where `α` is the inverse Ackermann function.
`α(n) <= 4` for any `n` smaller than the number of atoms in the observable
universe — so "near O(1)" is not an exaggeration, it's a tight, proven bound.

**When would you choose DFS/BFS over Union-Find for a connectivity problem,
and vice versa?**
If you're given the *whole* graph upfront and need to answer connectivity
questions **once** (e.g., "how many connected components does this static
graph have?"), DFS/BFS is simpler to write and equally efficient — O(V+E).
Reach for Union-Find when: (1) edges/connections arrive **incrementally** and
you need the answer to update after each one (Number of Islands II), or (2)
you need to answer **many** "are X and Y connected?" queries efficiently
without re-traversing the graph each time.

**Walk through path compression step by step — what actually changes in the
`parent` array?**
`find(x)` recurses up to the root, then on the way back *down* the call
stack, sets `parent[node] = root` for every node visited. So if `3 -> 2 -> 0`
(0 is root), after `find(3)`: `parent[3] = 0` AND `parent[2] = 0` (both
nodes visited during the call get repointed directly to the root) — not just
the node you originally called `find` on.

**Union by rank vs. union by size — does the choice matter?**
Both achieve the same asymptotic bound (O(α(n)) with path compression). Rank
tracks an *upper bound on tree height*; size tracks the *number of elements*
in the tree. Size is sometimes more directly useful (e.g., "what's the size
of the largest connected component?" falls out for free). In practice, either
is fine — pick whichever the problem's follow-up questions make more
convenient.

**Why initialize `parent[i] = i` for every `i`?**
Each element starts in its **own** singleton set, and the representative
(root) of a singleton set `{i}` is `i` itself. `parent[i] == i` is exactly
the base case / termination condition for `find` — "if I am my own parent, I
am the root, stop here."

**Walk through the off-by-one bug in §8 — why specifically does `find(3)`
crash, and what's the general lesson?**
`UnionFindBroken(3)` allocates `parent = [0, 1, 2]` — valid indices are `0`,
`1`, `2`. But the problem's nodes are 1-indexed up to `n=3`, so node `3` is a
legal input — and `parent[3]` is out of bounds. The general lesson: **before
writing `UnionFind(n)`, check the problem's node-numbering convention** and
size the array to `max_possible_node_value + 1`, regardless of what `n`
"means" in the problem statement.

**How does Union-Find detect a cycle, and why does that NOT generalize to
directed graphs?**
For an undirected edge `(u, v)`: if `find(u) == find(v)` *before* you union
them, then `u` and `v` were already connected via some other path — adding
edge `(u, v)` would create a cycle. This works because undirected
connectivity is symmetric and transitive — exactly what Union-Find's
equivalence-class structure models. Directed graphs need **direction-aware**
cycle detection (3-color DFS in [`topological_sort.md`](topological_sort.md))
because `a -> b` and `b -> a` together form a cycle, but `a -> b` and `a -> c`
(with `b` and `c` unrelated) do not — Union-Find would incorrectly treat both
scenarios the same way (both unions of `{a,b,c}`).

**How does Union-Find fit into Kruskal's MST algorithm?**
Sort all edges by weight ascending. For each edge `(u, v, w)` in that order,
call `union(u, v)`. If it returns `True` (they were in different
components), this edge doesn't create a cycle — accept it into the MST and
add `w` to the total cost. If it returns `False`, accepting this edge would
create a cycle — skip it. The greedy correctness (cheapest non-cycle-forming
edge is always safe to add) is the Cut Property of MSTs; Union-Find is just
the efficient mechanism for the cycle check.

**What extra state does weighted Union-Find (Evaluate Division) need beyond
plain Union-Find?**
A `weight[x]` array, where `weight[x]` represents the ratio `x / parent[x]`.
`find(x)` must accumulate the product of `weight` values along the path to
the root (this is the ratio `x / root`), and during path compression, update
`weight[x]` to that accumulated product directly (so future lookups are O(1)
again). `union(x, y, value)` — given `x / y == value` — computes the relative
weight between `x`'s root and `y`'s root using the already-known ratios
`x/root_x` and `y/root_y`.

**For Number of Islands II, why is Union-Find preferred over re-running DFS
after every cell addition?**
Re-running DFS from scratch after each of `k` additions costs
`O(k * rows * cols)` in the worst case. With Union-Find, each addition is one
`union` call per neighboring land cell — O(α(rows*cols)) amortized — so `k`
additions cost roughly `O(k * α(rows*cols))`, which is dramatically cheaper
for large grids with many incremental updates.

**What does `self.count` track, and what's the subtlety in maintaining it
correctly?**
`self.count` is the number of disjoint sets (connected components) currently.
It starts at `n` (everything isolated) and decrements by exactly 1 **only
when `union` actually merges two previously-different sets** — i.e., only
when `find(x) != find(y)` *before* the union. If you decrement unconditionally
on every `union` call (even when `x` and `y` were already connected), `count`
will undercount the true number of components.
