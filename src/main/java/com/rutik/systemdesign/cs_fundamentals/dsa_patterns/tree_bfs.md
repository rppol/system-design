# Tree BFS

## Pattern Snapshot

**Tree BFS** (breadth-first search / level-order traversal) processes a tree
**one level at a time** using a queue. Whenever a problem cares about
*horizontal* structure — what's at depth 0, depth 1, depth 2, ... — or needs
the **shortest path to a node** (fewest edges from the root), BFS is the
natural fit.

- **One-line cue**: "level order", "zigzag", "right/left side view", "connect
  nodes at the same level", "minimum depth", "average/sum per level."
- **Typical complexity**: O(n) time (every node visited once), O(w) space
  where `w` is the maximum width of the tree (up to O(n) for a complete tree's
  last level).

---

## 1. Recognition Signals

**Strong signals — reach for tree BFS:**

- "**Level order** traversal" — [Binary Tree Level Order Traversal (LC 102)](https://leetcode.com/problems/binary-tree-level-order-traversal/).
- "**Zigzag** level order" (alternate left-to-right, right-to-left per level).
- "**Right side view**" / "left side view" — the last (or first) node visited
  at each level.
- "**Connect** nodes at the same level" / "populate next right pointers."
- "**Minimum depth**" of a tree — BFS finds the first leaf encountered, which
  is guaranteed to be at the minimum depth (DFS would have to explore
  everything to be sure).
- "**Average / sum / max** of each level."
- Any **N-ary tree** "level order" variant — the same queue template
  generalizes directly (push all children, not just left/right).
- "**Cousins**" in a binary tree (same depth, different parents) — BFS
  naturally tracks both depth and parent per node.

**Anti-signals — looks like tree BFS but isn't:**

- "**Path sum** from root to leaf", "**diameter**", "**lowest common
  ancestor**", "**maximum path sum**" — these are about *vertical* paths
  through the tree and need a return value bubbled up from children, which is
  [tree_dfs](tree_dfs.md)'s recursive shape, not a queue.
- "**Serialize / deserialize**" — usually DFS (preorder) for a compact,
  recursive encoding, though a BFS-based encoding is also possible; check
  which the problem's expected format implies.
- The structure has **cycles** or is a general **graph**, not a tree — you
  need a `visited` set, which is [graph_traversal](graph_traversal.md)'s job
  (trees never need `visited` since there's exactly one path between any two
  nodes).

---

## 2. Mental Model & Intuition

A queue holds exactly the nodes of the **current level**. Before processing,
record `level_size = len(queue)` — that many `popleft()` calls consume
*exactly* this level, while any children pushed during those pops become
*next* level's queue contents.

```
        3
       / \
      9   20
         /  \
        15   7

queue = [3]                       level_size = 1
  pop 3 -> level = [3]
  push 9, 20
queue = [9, 20]                   level_size = 2
  pop 9  -> level = [9]   (no children)
  pop 20 -> level = [9, 20]
  push 15, 7 (children of 20)
queue = [15, 7]                   level_size = 2
  pop 15 -> level = [15]
  pop 7  -> level = [15, 7]
  (no children)
queue = []  -> done

result = [[3], [9, 20], [15, 7]]
```

The `level_size = len(queue)` snapshot is the entire trick: it's a fixed
boundary computed *before* any children get appended, so the inner loop
processes precisely one level no matter how many children get pushed during
it.

---

## 3. The Template

```python
from __future__ import annotations
from collections import deque
from typing import List, Optional


class TreeNode:
    def __init__(self, val: int = 0, left: "TreeNode | None" = None, right: "TreeNode | None" = None) -> None:
        self.val = val
        self.left = left
        self.right = right


def level_order(root: Optional[TreeNode]) -> List[List[int]]:
    """Return node values grouped by level, top to bottom. O(n) time, O(w) space."""
    if not root:
        return []

    result: List[List[int]] = []
    queue: deque[TreeNode] = deque([root])

    while queue:
        level_size = len(queue)          # snapshot BEFORE pushing children
        level: List[int] = []
        for _ in range(level_size):
            node = queue.popleft()
            level.append(node.val)
            if node.left:
                queue.append(node.left)
            if node.right:
                queue.append(node.right)
        result.append(level)

    return result


def zigzag_level_order(root: Optional[TreeNode]) -> List[List[int]]:
    """Alternate left-to-right and right-to-left per level."""
    if not root:
        return []

    result: List[List[int]] = []
    queue: deque[TreeNode] = deque([root])
    left_to_right = True

    while queue:
        level = deque()
        for _ in range(len(queue)):
            node = queue.popleft()
            if left_to_right:
                level.append(node.val)
            else:
                level.appendleft(node.val)
            if node.left:
                queue.append(node.left)
            if node.right:
                queue.append(node.right)
        result.append(list(level))
        left_to_right = not left_to_right

    return result


def right_side_view(root: Optional[TreeNode]) -> List[int]:
    """Value of the last (rightmost) node visited at each level."""
    if not root:
        return []

    result: List[int] = []
    queue: deque[TreeNode] = deque([root])

    while queue:
        level_size = len(queue)
        for i in range(level_size):
            node = queue.popleft()
            if i == level_size - 1:      # last node processed in this level
                result.append(node.val)
            if node.left:
                queue.append(node.left)
            if node.right:
                queue.append(node.right)

    return result


def min_depth(root: Optional[TreeNode]) -> int:
    """Minimum depth = depth of the FIRST leaf BFS encounters."""
    if not root:
        return 0

    queue: deque[tuple] = deque([(root, 1)])
    while queue:
        node, depth = queue.popleft()
        if not node.left and not node.right:   # leaf
            return depth
        if node.left:
            queue.append((node.left, depth + 1))
        if node.right:
            queue.append((node.right, depth + 1))

    return 0   # unreachable if root is not None
```

---

## 4. Annotated Walkthrough

**Problem**: [Binary Tree Level Order Traversal (LC 102)](https://leetcode.com/problems/binary-tree-level-order-traversal/)
on the tree:

```
        3
       / \
      9   20
         /  \
        15   7
```

**Step 1 — initialize.** `queue = deque([3])`, `result = []`.

**Step 2 — level 0.**

```
level_size = len(queue) = 1
level = []
  pop 3 -> level=[3]; push 9, push 20
queue = [9, 20]
result = [[3]]
```

**Step 3 — level 1.**

```
level_size = len(queue) = 2
level = []
  pop 9  -> level=[9];     9 has no children, nothing pushed
  pop 20 -> level=[9, 20]; push 15, push 7
queue = [15, 7]
result = [[3], [9, 20]]
```

**Step 4 — level 2.**

```
level_size = len(queue) = 2
level = []
  pop 15 -> level=[15]; no children
  pop 7  -> level=[15, 7]; no children
queue = []
result = [[3], [9, 20], [15, 7]]
```

**Step 5 — `queue` is empty, loop ends.** Final result:
`[[3], [9, 20], [15, 7]]` — exactly the tree's three levels, top to bottom.

---

## 5. Complexity

| Operation | Time | Space | Why |
|---|---|---|---|
| `level_order` | O(n) | O(w) | Every node enqueued/dequeued exactly once; queue never holds more than one level (`w` = max nodes in any level) |
| `zigzag_level_order` | O(n) | O(w) | Same traversal, only the per-level append direction changes |
| `right_side_view` | O(n) | O(w) | Same traversal; only the *last* index per level is recorded |
| `min_depth` | O(n) worst case, often less | O(w) | Returns as soon as the first leaf is dequeued — for a tree with a shallow leaf, this terminates long before visiting all `n` nodes |

For a **complete binary tree**, the last level holds about `n/2` nodes, so
`O(w) = O(n)` in the worst case — the queue can briefly hold half the tree.

---

## 6. Variations & Sub-patterns

**1. Zigzag level order (LC 103).**
Alternate the append direction (`append` vs `appendleft` into a `deque` for
the current level) every other level — the BFS traversal order itself never
changes, only how each level's values are arranged in the output.

**2. Right/left side view (LC 199).**
The "view" from one side is just the first or last node processed at each
level — track `i == 0` (left view) or `i == level_size - 1` (right view)
inside the per-level loop.

**3. Connect next right pointers (LC 116 / LC 117).**
Within the per-level loop, link `queue[i].next = queue[i+1]` for consecutive
nodes *before popping* — or link `prev.next = node` while iterating, resetting
`prev = None` at the start of each level. This achieves "level-linking" using
the same level-size loop, no extra data structure beyond the queue itself.

**4. N-ary tree level order (LC 429).**
Generalizes directly: instead of `if node.left / if node.right`, push **all**
of `node.children`. The level-size snapshot trick is identical.

**5. Cousins in binary tree (LC 993).**
Track `(node, parent, depth)` triples in the queue. Two nodes are cousins iff
they have the same `depth` but different `parent` — both pieces of
information are naturally available during a single BFS pass.

**6. Maximum width of binary tree (LC 662).**
Assign each node a positional index as if the tree were a complete binary
tree (`root = 1`, `left child = 2*i`, `right child = 2*i + 1`). The width of a
level is `last_index - first_index + 1` for nodes at that level. Track these
indices in the queue alongside each node; subtract a per-level offset to
avoid the indices growing unboundedly large for deep, sparse trees.

**7. Recursive ("DFS-flavored") level order.**
You *can* compute level order with DFS by passing a `depth` parameter and
appending to `result[depth]` (creating a new list if `depth == len(result)`).
This avoids an explicit queue but processes nodes in a different order
(depth-first, not strictly level-by-level) — fine when only the *grouping* by
level matters, not the visiting order within a level.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Binary Tree Level Order Traversal (LC 102)](https://leetcode.com/problems/binary-tree-level-order-traversal/) | Medium | Canonical | The baseline template |
| [Binary Tree Level Order Traversal II (LC 107)](https://leetcode.com/problems/binary-tree-level-order-traversal-ii/) | Medium | Bottom-up | Same BFS, reverse the final result |
| [Binary Tree Zigzag Level Order Traversal (LC 103)](https://leetcode.com/problems/binary-tree-zigzag-level-order-traversal/) | Medium | Alternate direction | `deque` + alternate `append`/`appendleft` |
| [Binary Tree Right Side View (LC 199)](https://leetcode.com/problems/binary-tree-right-side-view/) | Medium | Last node per level | Track `i == level_size - 1` |
| [Average of Levels in Binary Tree (LC 637)](https://leetcode.com/problems/average-of-levels-in-binary-tree/) | Easy | Per-level aggregate | Sum / count per level |
| [Minimum Depth of Binary Tree (LC 111)](https://leetcode.com/problems/minimum-depth-of-binary-tree/) | Easy | Early termination | Return at the first leaf found |
| [Populating Next Right Pointers in Each Node (LC 116)](https://leetcode.com/problems/populating-next-right-pointers-in-each-node/) | Medium | Level-linking | Link siblings within the level-size loop |
| [N-ary Tree Level Order Traversal (LC 429)](https://leetcode.com/problems/n-ary-tree-level-order-traversal/) | Medium | N-ary generalization | Push `node.children`, not `left`/`right` |
| [Cousins in Binary Tree (LC 993)](https://leetcode.com/problems/cousins-in-binary-tree/) | Easy | Track parent + depth | Same depth, different parent |
| [Maximum Width of Binary Tree (LC 662)](https://leetcode.com/problems/maximum-width-of-binary-tree/) | Medium | Positional indexing | Track `2*i`/`2*i+1` indices per node |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: not snapshotting `level_size` before the inner loop collapses all
levels into one.**

```python
# BROKEN -- uses `while queue` for the inner loop instead of a fixed-size range
def level_order_broken(root: Optional[TreeNode]) -> List[List[int]]:
    if not root:
        return []

    result: List[List[int]] = []
    queue: deque[TreeNode] = deque([root])

    while queue:
        level: List[int] = []
        while queue:                      # BUG: drains the ENTIRE queue, not one level
            node = queue.popleft()
            level.append(node.val)
            if node.left:
                queue.append(node.left)
            if node.right:
                queue.append(node.right)
        result.append(level)

    return result
```

Trace on the same tree (`3 -> 9, 20 -> 15, 7`):

```
queue = [3]
outer while queue:
  level = []
  inner while queue:
    pop 3 -> level=[3]; push 9, 20      queue=[9,20]
    pop 9 -> level=[3,9]                 queue=[20]
    pop 20 -> level=[3,9,20]; push 15,7  queue=[15,7]
    pop 15 -> level=[3,9,20,15]          queue=[7]
    pop 7 -> level=[3,9,20,15,7]         queue=[]
  inner while ends (queue empty)
  result = [[3,9,20,15,7]]
outer while queue: queue is empty -> ends

return [[3, 9, 20, 15, 7]]
```

The inner `while queue` has **no boundary** — as long as the queue is
non-empty (which it is, continuously, because every pop that has children
immediately pushes more), it keeps consuming nodes from *every* level into a
single flat list. The result is one giant "level" containing the entire tree
in BFS order, instead of `[[3], [9, 20], [15, 7]]`.

```python
# FIX -- snapshot level_size BEFORE the inner loop, then bound the loop by it
def level_order(root: Optional[TreeNode]) -> List[List[int]]:
    if not root:
        return []

    result: List[List[int]] = []
    queue: deque[TreeNode] = deque([root])

    while queue:
        level_size = len(queue)           # FIX: fixed boundary, computed once
        level: List[int] = []
        for _ in range(level_size):       # exactly level_size pops, regardless
            node = queue.popleft()        # of how many children get pushed
            level.append(node.val)
            if node.left:
                queue.append(node.left)
            if node.right:
                queue.append(node.right)
        result.append(level)

    return result
```

`range(level_size)` is evaluated **once**, capturing the queue's length at
that instant — children pushed during the loop extend the queue *past* that
range, so they're correctly deferred to the next iteration of the outer
`while`. This is the single most common bug in level-order BFS code, and it
produces a *plausible-looking but wrong* output (one list instead of many)
rather than crashing — easy to miss if the test only checks total node count.

---

## 9. Related Patterns & When to Switch

- **[Tree DFS](tree_dfs.md)** — for path sums, diameter, LCA, or any property
  that requires combining results from a node's subtrees (a return value
  bubbling up), DFS's recursive shape is natural; BFS would need to
  reconstruct parent/path information manually.
- **[Graph Traversal](graph_traversal.md)** — same queue-based BFS mechanics,
  but on a general graph: requires a `visited` set (trees don't, since
  there's only one path to any node) and works on grids, adjacency lists, etc.
- **[Topological Sort](topological_sort.md)** — Kahn's algorithm is BFS with
  in-degree counting instead of a tree's parent/child structure; the
  level-by-level structure here is analogous to "process nodes whose
  dependencies are satisfied, layer by layer."
- **[Two Heaps](two_heaps.md)** — unrelated data structure, but if a tree-BFS
  problem also asks for "the k-th something per level," combine the level
  loop with a heap of size k (see [top_k_elements](top_k_elements.md)).

---

## 10. Cross-links

- Concept module: [trees_and_binary_search_trees](../trees_and_binary_search_trees/)
  — tree terminology (depth, height, balance), traversal order proofs.
- Applied: [database/indexing_deep_dive](../../database/indexing_deep_dive/)
  — B+Tree node layout is inherently level-structured; range scans traverse
  leaf-level linked lists, conceptually similar to "process one level, move
  to the next."
- Applied: [java/collections_internals](../../java/collections_internals/) —
  `ArrayDeque` is the standard Java BFS queue (avoid `LinkedList` as a queue
  for performance); `TreeMap`'s red-black tree balancing relates to the
  `trees_and_binary_search_trees` concept module.
- Master recognition engine: [dsa_patterns/README.md](README.md).
- Sibling pattern: [tree_dfs.md](tree_dfs.md).

---

## 11. Interview Q&A

**Why a queue (FIFO) and not a stack (LIFO) for level-order traversal?**
A queue processes nodes in the **order they were discovered**, which is
exactly "all of level `d` before any of level `d+1`" — a node's children are
enqueued *after* all of its same-level siblings, so they naturally wait their
turn. A stack (LIFO) would process the *most recently discovered* node next,
which produces a depth-first order — diving into one branch before finishing
a level.

**Why must `level_size = len(queue)` be captured *before* the inner loop, and what happens if you don't?**
Because the inner loop both **pops** nodes from the current level and
**pushes** their children (next level) onto the same queue. If the loop
condition re-evaluates `len(queue)` on each iteration (or uses `while
queue:`), it sees the growing queue and never stops at the level boundary —
collapsing all levels into one flat list (see §8). Snapshotting `level_size`
once gives the inner loop a fixed iteration count, decoupling "how many to
pop this round" from "how many got pushed."

**Zigzag level order — why use a `deque` for the per-level result instead of a list with `reverse()`?**
Both work, but `deque.appendleft()` is O(1), while building a list normally
and then calling `.reverse()` on alternate levels is O(level_size) extra work
per reversed level. Using a `deque` and choosing `append` vs `appendleft`
based on the current direction avoids the separate reversal pass entirely —
marginal for small trees, but it's the "why settle for O(n) extra work when
O(1) per element suffices" instinct interviewers like to see.

**Right side view — why does "last node processed in the level" give the rightmost visible node?**
Because BFS processes each level **left to right** (left children pushed
before right children, assuming you push `left` then `right`), the last node
popped in a level's iteration is the rightmost node at that depth — which is
exactly the node visible if you stood to the right of the tree and looked
through it horizontally.

**Why does BFS find `min_depth` correctly while a naive DFS might not, without extra logic?**
BFS visits nodes in **non-decreasing depth order** — the very first leaf it
encounters is *guaranteed* to be at the minimum depth across the whole tree,
so you can return immediately. A naive DFS that returns
`1 + min(left_depth, right_depth)` would incorrectly treat a missing child
(`None`) as depth 0, potentially reporting a shorter "depth" through a
non-existent branch — DFS solutions for this problem need an explicit check
for "this node has only one child" to avoid that trap, which BFS sidesteps
entirely by construction.

**How do you link "next" pointers between siblings using only the level-size loop?**
Within the per-level loop, maintain a `prev` pointer (reset to `None` at the
start of each level). For each node popped, if `prev is not None:
prev.next = node`, then set `prev = node`. After the level's loop, the last
node's `next` remains `None` (its default), correctly marking the end of that
level's chain. No extra queue or array is needed beyond the BFS queue itself.

**What's the worst-case space complexity, and which tree shape triggers it?**
O(n) in the worst case. A **complete binary tree** has roughly `n/2` leaves,
all at the last level — when the queue is processing the second-to-last
level, it simultaneously holds (about to push) all `n/2` last-level nodes,
giving `O(n)` queue size at that instant. A skewed (linked-list-shaped) tree,
by contrast, has `w = 1` at every level — `O(1)` queue space.

**How does the template generalize to N-ary trees (LC 429)?**
Replace `if node.left: queue.append(node.left)` / `if node.right: ...` with a
single loop: `for child in node.children: queue.append(child)`. Everything
else — the `level_size` snapshot, the per-level result list — is unchanged.
This is a good example of how the *shape* of the pattern (queue +
level-size snapshot) is independent of the tree's branching factor.

**Cousins in Binary Tree — why track both `parent` and `depth` in the queue, and isn't depth alone enough?**
"Cousins" requires same depth **and different parents** (siblings have the
same parent and are excluded). Depth alone can't distinguish siblings from
cousins — two nodes at the same depth could be siblings (same parent,
disqualified) or cousins (different parents, qualified). Carrying
`(node, parent, depth)` triples in the queue lets you check both conditions
in O(1) once you've located the two target nodes during the single BFS pass.

**Maximum Width of Binary Tree — why use positional indices, and how do you prevent them from overflowing for deep trees?**
The "width" of a level is defined by the *positions* of the leftmost and
rightmost nodes **as if the tree were complete** — including gaps from
missing nodes. Assigning `root = 1`, `left child = 2*i`, `right child = 2*i +
1` encodes this complete-tree position. For deep, sparse trees these indices
can grow exponentially (`2^depth`), risking overflow in fixed-width integer
languages. The fix: at the start of each level, **subtract the first index of
that level from every index** before using it — this re-bases indices to
start near 0 each level, keeping them small while preserving relative
differences (which is all `last - first + 1` needs).

**When would you prefer the recursive (DFS-based) "level order" formulation over an explicit queue?**
When you're already writing a DFS for another reason (e.g., computing depth
for a different purpose) and only need to *group* values by level — not
preserve strict left-to-right discovery order *across* levels relative to
each other (within a level, a preorder DFS still visits left before right,
so grouping by a `depth` parameter into `result[depth]` produces the same
per-level lists). It avoids allocating an explicit queue, but conceptually
it's still doing the same grouping — pick whichever is more natural given the
rest of your code.
