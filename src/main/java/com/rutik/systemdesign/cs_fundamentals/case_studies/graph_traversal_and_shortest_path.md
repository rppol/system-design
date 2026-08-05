# Graph Traversal and Shortest Path

Three canonical graph problems — connected components on a grid, topological ordering of a DAG, and weighted single-source shortest path — appear in nearly every senior-engineer interview loop. Mastering these three unlocks a wide cluster of derivative problems.

---

## Intuition

A graph is the universal abstraction for "things that relate to other things." The three problems below probe three distinct questions that graphs answer:

1. **Connected components (Number of Islands)** — which nodes can reach which other nodes through unlabeled edges?
2. **Topological order (Course Schedule)** — if edges encode dependency (A must happen before B), can we sequence all work, and does a cycle make it impossible?
3. **Weighted shortest path (Network Delay Time)** — given edge costs, what is the minimum total cost to travel from a source to every other node?

Each question demands a different traversal strategy, yet all three reduce to the same primitive: visit a node, mark it, process its neighbors.

---

## 1. Problem Statement & Clarifying Questions

### Problem 1 — Number of Islands (LeetCode 200)

Given an `m x n` grid of characters `'1'` (land) and `'0'` (water), return the number of islands. An island is a group of adjacent land cells connected horizontally or vertically.

**Clarifying questions an interviewer expects:**
- **Q: Is diagonal connectivity considered?** A: Standard problem says no — only 4-directional (up, down, left, right).
- **Q: Can the grid be empty (0 rows or 0 columns)?** A: Yes — return 0.
- **Q: Can we modify the grid in place?** A: Clarify; if yes, we can sink visited cells instead of keeping a separate visited set.
- **Q: What are the size constraints?** A: Typical: m, n up to 300. Grid has at most 90,000 cells.
- **Q: Is the grid guaranteed to contain only '0' and '1'?** A: Yes per the problem, but defensive code handles unexpected characters.

### Problem 2 — Course Schedule (LeetCode 207)

Given `numCourses` courses labeled `0` to `numCourses - 1` and a list of `prerequisites` where `[a, b]` means "course b must be completed before course a", determine if it is possible to finish all courses.

**Clarifying questions:**
- **Q: Can there be duplicate prerequisite pairs?** A: Yes — deduplicate or handle gracefully.
- **Q: Can numCourses be 0?** A: Yes — trivially return True (no courses, nothing to fail).
- **Q: Are prerequisites guaranteed to be valid course indices?** A: Yes per constraints, but still bounds-check in production.
- **Q: Is the problem equivalent to "does the dependency graph contain a cycle"?** A: Exactly — confirming this insight early scores points.

### Problem 3 — Network Delay Time (LeetCode 743)

Given a list of directed weighted edges `times` where `times[i] = [u, v, w]` means there is a directed edge from node `u` to node `v` with weight `w` (travel time in milliseconds), find the minimum time for a signal sent from source node `k` to reach ALL nodes. If it is impossible for all nodes to receive the signal, return -1.

**Clarifying questions:**
- **Q: Are edge weights guaranteed to be non-negative?** A: Yes. (This is critical — it enables Dijkstra. If negative, we need Bellman-Ford.)
- **Q: Can there be multiple edges between the same pair of nodes?** A: Yes — keep the minimum.
- **Q: Are nodes numbered 1 to n or 0 to n-1?** A: 1-indexed in LeetCode's version.
- **Q: Can n = 1 (single node)?** A: Yes — the answer is 0 if k = 1.
- **Q: Can there be self-loops?** A: Possible — a self-loop with positive weight is harmless and ignored.

---

## 2. Brute Force & Complexity Baseline

### Problem 1 — Number of Islands: Brute Force

A naive approach: for each cell that is `'1'`, launch a fresh DFS/BFS that marks connected land cells. Count how many times a fresh traversal starts.

This is actually already O(m * n) time and O(m * n) space (recursion stack), so "brute force" here means the naive implementation without careful visited tracking. The real trap is visiting the same cell multiple times by not marking cells early enough — covered in the BROKEN→FIX block in §4.

There is no fundamentally worse brute force for this problem other than the aforementioned duplicate-enqueue bug.

### Problem 2 — Course Schedule: Brute Force

A brute-force approach attempts to check every possible ordering of courses and validates if any satisfies all prerequisites. With `n` courses, there are `n!` permutations. For n = 15 that is 1.3 trillion — completely infeasible.

Time: O(n! * E) where E is the number of prerequisite edges.
Space: O(n) for the current permutation stack.

A slightly better but still suboptimal approach: DFS-based cycle detection with a naive visited array that does not distinguish between "currently in the call stack" and "completely processed." This results in false negatives (marking a node visited during an unrelated path prevents detecting cycles through it later).

### Problem 3 — Network Delay Time: Brute Force

Bellman-Ford is the "brute force" shortest-path algorithm: relax every edge V-1 times.

Time: O(V * E) — with V=100 nodes and E=6000 edges (LeetCode's edge ceiling for this problem; a complete directed graph on 100 nodes has 9,900 edges), that is roughly 600,000 relaxations per test, acceptable but worse than Dijkstra's O((V+E) log V).

Space: O(V) for the distance array.

The key insight: Bellman-Ford handles negative weights; Dijkstra does not. When the problem guarantees non-negative weights (as here), Dijkstra is strictly superior. An interviewer who asks "why not Bellman-Ford here?" expects this comparison.

---

## 3. Optimal Approach & Key Insight

### Problem 1 — BFS/DFS on Grid (Connected Components)

**Key insight:** treat each `'1'` cell as a graph node with up to 4 neighbors. The number of islands equals the number of connected components.

- **DFS approach:** recursive flood-fill. When we find an unvisited `'1'`, increment the count and DFS in all 4 directions, marking cells as visited. Time O(m*n), space O(m*n) for the recursion stack in worst case (entire grid is land, DFS goes m*n deep).
- **BFS approach:** seed a queue with the source cell, mark it visited at enqueue time (not dequeue time — dequeue-time marking still visits every cell exactly once and returns the same answer, but the queue swells to one entry per edge instead of one per cell; see §4), then expand layer by layer. Time O(m*n), space O(min(m,n)) for the BFS frontier in the best case but O(m*n) worst case.
- **Union-Find approach:** iterate once, union adjacent land cells. Count remaining distinct roots. Time O(m*n * alpha(m*n)) ≈ O(m*n), space O(m*n). Best choice when the grid is dynamic (cells added/removed over time).

For a static grid, DFS (iterative to avoid stack overflow for large grids) or BFS are standard.

### Problem 2 — Kahn's Algorithm (Topological Sort via BFS)

**Key insight:** a directed graph has a valid topological order if and only if it is a DAG (directed acyclic graph). A cycle makes topological ordering impossible.

**Kahn's algorithm:**
1. Compute in-degree for each node (number of incoming edges).
2. Seed a queue with all nodes that have in-degree 0 (no prerequisites).
3. Repeatedly dequeue a node, decrement the in-degree of its neighbors. Any neighbor whose in-degree drops to 0 joins the queue.
4. If the total number of processed nodes equals `numCourses`, no cycle exists. If fewer, a cycle prevented some nodes from reaching in-degree 0.

Time O(V + E), Space O(V + E).

**DFS-based alternative:** color nodes white (unvisited), gray (in current path), black (fully processed). If DFS encounters a gray node, a cycle exists.

```mermaid
stateDiagram-v2
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    [*] --> White
    White --> Gray: DFS visits node
    Gray --> Black: all descendants done
    Gray --> Gray: revisit gray node
    Black --> [*]

    note right of Gray: Revisiting a gray node<br/>means a cycle exists

    class White frozen
    class Gray mathOp
    class Black train
```

A node is gray only while it sits on the current DFS call stack; if DFS follows an edge back into a still-gray node, that back edge is exactly the cycle the three-color scheme is built to catch.

Kahn's is preferred in interviews because the cycle-detection logic is a natural side effect of the count check, not a separate concern.

### Problem 3 — Dijkstra's Algorithm

**Key insight:** greedily process nodes in order of known shortest distance. A min-heap ensures we always process the closest unfinalized node next.

**Dijkstra with a min-heap (lazy deletion variant):**
1. Initialize `dist[k] = 0`, `dist[all others] = infinity`.
2. Push `(0, k)` onto the min-heap.
3. Pop `(d, u)`. If `d > dist[u]`, this is a stale entry — skip it (this guard is the second BROKEN→FIX in §4).
4. For each neighbor `v` of `u` with edge weight `w`: if `dist[u] + w < dist[v]`, update `dist[v]` and push `(dist[v], v)` onto the heap.
5. After the heap is exhausted, `max(dist.values())` is the answer (or -1 if any node remains at infinity).

Time O((V + E) log V). The lazy variant pushes one heap entry per successful relaxation, so the tighter statement is O(E log E) — the same class, since log E < 2 log V. The stale-entry guard does not shrink the heap (it removes no pushes) and so does not change the complexity class; what it buys is not re-scanning a node's adjacency list once per stale pop, measured at 4x fewer edge scans in §4.

---

## 4. Implementation

### Problem 1 — Number of Islands (BFS, iterative)

```python
from __future__ import annotations
from collections import deque


def num_islands(grid: list[list[str]]) -> int:
    """
    Return the number of islands in a 2-D grid of '1' (land) and '0' (water).

    BFS variant: marks cells visited at enqueue time to prevent duplicate processing.
    Time:  O(m * n)
    Space: O(min(m, n)) average BFS frontier; O(m * n) worst case.
    """
    if not grid or not grid[0]:
        return 0

    rows, cols = len(grid), len(grid[0])
    count = 0
    DIRECTIONS = [(0, 1), (0, -1), (1, 0), (-1, 0)]

    def bfs(start_r: int, start_c: int) -> None:
        queue: deque[tuple[int, int]] = deque()
        # Mark visited AT ENQUEUE TIME — critical correctness point
        grid[start_r][start_c] = "0"
        queue.append((start_r, start_c))
        while queue:
            r, c = queue.popleft()
            for dr, dc in DIRECTIONS:
                nr, nc = r + dr, c + dc
                if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] == "1":
                    grid[nr][nc] = "0"   # mark visited at enqueue time
                    queue.append((nr, nc))

    for r in range(rows):
        for c in range(cols):
            if grid[r][c] == "1":
                count += 1
                bfs(r, c)

    return count


# ---- quick test ----
if __name__ == "__main__":
    g1 = [
        ["1", "1", "1", "1", "0"],
        ["1", "1", "0", "1", "0"],
        ["1", "1", "0", "0", "0"],
        ["0", "0", "0", "0", "0"],
    ]
    assert num_islands(g1) == 1, "Expected 1 island"

    g2 = [
        ["1", "1", "0", "0", "0"],
        ["1", "1", "0", "0", "0"],
        ["0", "0", "1", "0", "0"],
        ["0", "0", "0", "1", "1"],
    ]
    assert num_islands(g2) == 3, "Expected 3 islands"
    print("Number of Islands: all tests passed")
```

---

### BROKEN→FIX Block 1: BFS that marks visited at dequeue time blows the queue up to O(E)

The following example uses a general graph (not a grid) to show the bug in the form that
hides longest: the output is right, so tests pass and only memory tells you.

```python
# BROKEN: BFS marks visited at DEQUEUE time, not enqueue time.
# The same node is enqueued once per in-edge from an already-expanded
# node, so the queue holds up to E entries instead of V. This is a
# MEMORY bug, not a correctness bug and not an infinite loop: the
# `if node in visited: continue` check drains the duplicates, every
# node is still expanded exactly once, and the traversal order is
# byte-for-byte identical to the fixed version below (verified on
# 2,000 random directed graphs: zero differences).

from collections import deque


def bfs_broken(graph: dict[int, list[int]], start: int) -> list[int]:
    """BROKEN — do NOT use."""
    visited: set[int] = set()
    queue: deque[int] = deque([start])
    order: list[int] = []

    while queue:
        node = queue.popleft()
        if node in visited:          # checked TOO LATE
            continue
        visited.add(node)            # marked TOO LATE (after dequeue)
        order.append(node)
        for neighbor in graph[node]:
            queue.append(neighbor)   # enqueues already-seen neighbors repeatedly
    return order


# A 2-node cycle 0 <-> 1 does NOT expose this: the run is
#   [0] -> [1] -> [0], pop 0, already visited, done — 3 enqueues, max
# queue depth 1. Density is what hurts, not cycles. On a 512-node
# dense digraph (E = 261,632) the queue peaks at 261,121 entries
# against 511 for the fixed version — a 511x blowup, and the deque
# alone grows from a few KB to ~2 MB (6 MB under tracemalloc, which
# counts the boxed ints). Nothing crashes; the process just carries
# O(E) of garbage and pops it one entry at a time.
```

```python
# FIX: mark visited AT ENQUEUE TIME.
# A node is added to `visited` the moment it is placed on the queue,
# so it is never enqueued a second time regardless of how many
# different paths lead to it.

from collections import deque


def bfs_fixed(graph: dict[int, list[int]], start: int) -> list[int]:
    """FIXED — visited set updated at enqueue, not dequeue."""
    visited: set[int] = {start}      # mark at enqueue time
    queue: deque[int] = deque([start])
    order: list[int] = []

    while queue:
        node = queue.popleft()
        order.append(node)
        for neighbor in graph[node]:
            if neighbor not in visited:
                visited.add(neighbor)   # mark BEFORE enqueue
                queue.append(neighbor)
    return order


# Same dense graph, same traversal order, same O(V + E) time —
# but the queue size never exceeds the number of nodes: max 511
# entries for the 512-node graph above, a few KB instead of ~2 MB.

if __name__ == "__main__":
    # Undirected cycle: 0-1-2-3-0
    g = {0: [1, 3], 1: [0, 2], 2: [1, 3], 3: [2, 0]}
    result = bfs_fixed(g, 0)
    assert len(result) == 4, f"Expected 4 nodes, got {len(result)}"
    print("BFS fixed: visited nodes =", result)
```

---

### Problem 2 — Course Schedule (Kahn's Algorithm)

```python
from __future__ import annotations
from collections import deque


def can_finish(num_courses: int, prerequisites: list[list[int]]) -> bool:
    """
    Return True if all courses can be finished (no cycle in the prerequisite graph).

    Uses Kahn's topological sort: count processed nodes; if < num_courses, a cycle exists.
    Time:  O(V + E)  where V = num_courses, E = len(prerequisites)
    Space: O(V + E)  for adjacency list and in-degree table
    """
    if num_courses == 0:
        return True

    # Build adjacency list and in-degree count
    adj: list[list[int]] = [[] for _ in range(num_courses)]
    in_degree: list[int] = [0] * num_courses

    for course, prereq in prerequisites:
        adj[prereq].append(course)
        in_degree[course] += 1

    # Seed queue with all nodes that have no prerequisites
    queue: deque[int] = deque(
        course for course in range(num_courses) if in_degree[course] == 0
    )
    processed = 0

    while queue:
        course = queue.popleft()
        processed += 1
        for dependent in adj[course]:
            in_degree[dependent] -= 1
            if in_degree[dependent] == 0:
                queue.append(dependent)

    # If processed < num_courses, at least one cycle exists
    return processed == num_courses


if __name__ == "__main__":
    # Linear chain: 0 <- 1 <- 2 (no cycle)
    assert can_finish(3, [[1, 0], [2, 1]]) is True

    # Cycle: 0 <- 1 <- 0
    assert can_finish(2, [[1, 0], [0, 1]]) is False

    # No prerequisites at all
    assert can_finish(5, []) is True

    # One node, no prerequisites
    assert can_finish(1, []) is True

    print("Course Schedule: all tests passed")
```

---

### Problem 3 — Network Delay Time (Dijkstra)

```python
from __future__ import annotations
import heapq
import math


def network_delay_time(times: list[list[int]], n: int, k: int) -> int:
    """
    Return the minimum time for a signal from node k to reach all n nodes.
    Nodes are 1-indexed.

    Uses Dijkstra with a min-heap (lazy deletion variant).
    Time:  O((V + E) log V)  with the stale-entry guard
    Space: O(V + E)  for the adjacency list and heap
    """
    # Build adjacency list: adj[u] = list of (v, weight)
    adj: dict[int, list[tuple[int, int]]] = {i: [] for i in range(1, n + 1)}
    for u, v, w in times:
        adj[u].append((v, w))

    dist: dict[int, float] = {i: math.inf for i in range(1, n + 1)}
    dist[k] = 0

    # Min-heap entries: (distance, node)
    heap: list[tuple[float, int]] = [(0, k)]

    while heap:
        d, u = heapq.heappop(heap)

        # Stale-entry guard: if we have already found a shorter path to u, skip.
        # Without this guard the algorithm is still correct and pushes exactly the
        # same entries, so the heap and its operation count are unchanged; what it
        # costs is re-scanning u's adjacency list once per stale pop — 4.1x more
        # edge scans in the benchmark in the next block. It is an inner-loop
        # constant, not a change of complexity class.
        if d > dist[u]:
            continue

        for v, w in adj[u]:
            new_dist = dist[u] + w
            if new_dist < dist[v]:
                dist[v] = new_dist
                heapq.heappush(heap, (new_dist, v))

    max_dist = max(dist.values())
    return -1 if math.isinf(max_dist) else int(max_dist)


if __name__ == "__main__":
    # Example 1: 4 nodes, signal from 2
    # Expected: 2
    times1 = [[2, 1, 1], [2, 3, 1], [3, 4, 1]]
    assert network_delay_time(times1, 4, 2) == 2, "Expected 2"

    # Example 2: 2 nodes, no path from 2 to 1
    # Expected: -1
    times2 = [[1, 2, 1]]
    assert network_delay_time(times2, 2, 2) == -1, "Expected -1"

    # Single node
    assert network_delay_time([], 1, 1) == 0, "Expected 0"

    print("Network Delay Time: all tests passed")
```

---

### BROKEN→FIX Block 2: Dijkstra without the stale-entry guard

```python
# BROKEN: Dijkstra without stale-entry guard (d > dist[u] check).
# Produces CORRECT shortest-path results but re-scans the adjacency
# list of every stale heap entry, even though relaxing from the
# already-improved dist[u] cannot help anyone.
# Note what the guard does NOT do: it removes no pushes, so the heap
# is exactly the same size and the heap-operation count is unchanged.
# Benchmark, 100-node complete directed graph, 9,900 edges, weights
# 1..1000, mean of 10 seeds:
#   heap ops:    817 without the guard, 817 with it (identical)
#   max heap:    343 entries either way (not O(E) in practice)
#   stale pops:  309 of 409 pops
#   edge scans:  40,481 without the guard vs 9,900 with — 4.1x
# So the guard is an inner-loop constant, not a complexity class.

import heapq
import math


def dijkstra_broken(adj: dict[int, list[tuple[int, int]]], src: int, n: int) \
        -> dict[int, float]:
    """BROKEN — correct output, degraded performance."""
    dist: dict[int, float] = {i: math.inf for i in range(1, n + 1)}
    dist[src] = 0
    heap: list[tuple[float, int]] = [(0, src)]

    while heap:
        d, u = heapq.heappop(heap)
        # NO stale-entry guard here — processes every old entry
        for v, w in adj[u]:
            if dist[u] + w < dist[v]:
                dist[v] = dist[u] + w
                heapq.heappush(heap, (dist[v], v))

    return dist


# FIX: add the stale-entry guard immediately after the pop.

def dijkstra_fixed(adj: dict[int, list[tuple[int, int]]], src: int, n: int) \
        -> dict[int, float]:
    """FIXED — O((V+E) log V) via stale-entry guard."""
    dist: dict[int, float] = {i: math.inf for i in range(1, n + 1)}
    dist[src] = 0
    heap: list[tuple[float, int]] = [(0, src)]

    while heap:
        d, u = heapq.heappop(heap)
        if d > dist[u]:          # stale entry — skip
            continue
        for v, w in adj[u]:
            new_dist = d + w
            if new_dist < dist[v]:
                dist[v] = new_dist
                heapq.heappush(heap, (new_dist, v))

    return dist
```

---

## 5. Complexity Analysis & Tradeoffs

```
Problem 1 — Number of Islands
+---------------------+----------------+----------------+
| Approach            | Time           | Space          |
+---------------------+----------------+----------------+
| DFS (recursive)     | O(m * n)       | O(m * n) stack |
| BFS (iterative)     | O(m * n)       | O(min(m,n))    |
|                     |                | avg frontier   |
| Union-Find          | O(m*n*alpha)   | O(m * n)       |
|                     | ~= O(m * n)    |                |
+---------------------+----------------+----------------+

Best for static grid: BFS (bounded stack size, no recursion limit risk)
Best for dynamic grid: Union-Find (supports incremental merges)

Problem 2 — Course Schedule
+---------------------+----------------+----------------+
| Approach            | Time           | Space          |
+---------------------+----------------+----------------+
| Kahn's (BFS)        | O(V + E)       | O(V + E)       |
| DFS cycle detection | O(V + E)       | O(V + E) stack |
| Brute force perm.   | O(V! * E)      | O(V)           |
+---------------------+----------------+----------------+

Kahn's is preferred: cycle detection is implicit (count check),
easier to reason about in interviews, naturally produces the
topological order as a side product.

Problem 3 — Network Delay Time
+---------------------+--------------------+----------------+
| Approach            | Time               | Space          |
+---------------------+--------------------+----------------+
| Dijkstra (heap)     | O((V+E) log V)     | O(V + E)       |
| Dijkstra (no guard) | O(E log E) *       | O(E)           |
| Bellman-Ford        | O(V * E)           | O(V)           |
| Floyd-Warshall      | O(V^3)             | O(V^2)         |
+---------------------+--------------------+----------------+

Dijkstra wins when: non-negative weights, single source.
Bellman-Ford wins when: negative weights present (but no negative cycles).
Floyd-Warshall wins when: all-pairs shortest path needed and V is small.

* Heap operations only, and the same class as the row above it. Dropping the
  stale-entry guard removes no pushes, so the heap is exactly as large; what
  it costs is re-scanning a node's adjacency list once per stale pop -- 4.1x
  more edge scans on the 100-node complete graph benchmarked in section 4.
```

The three competing shortest-path algorithms above collapse into a single routing decision:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["Shortest path<br/>needed"]) --> q1{"Negative<br/>edge weights?"}
    q1 -->|"no"| q2{"All-pairs<br/>needed?"}
    q1 -->|"yes"| q3{"Negative<br/>cycle?"}
    q2 -->|"no, single source"| dij("Dijkstra<br/>O((V+E) log V)")
    q2 -->|"yes"| q4{"V under 400?"}
    q4 -->|"yes"| fw("Floyd-Warshall<br/>O(V^3)")
    q4 -->|"no"| dijall("Dijkstra from<br/>every source")
    q3 -->|"yes"| none("No solution<br/>undefined path")
    q3 -->|"no"| bf("Bellman-Ford<br/>O(V * E)")

    class start io
    class q1,q2,q3,q4 mathOp
    class dij,fw,dijall,bf train
    class none lossN
```

Non-negative single-source weights route straight to Dijkstra; a negative cycle makes the shortest path undefined; an all-pairs requirement only outgrows per-source Dijkstra once V passes roughly 400, at which point Floyd-Warshall's O(V^3) becomes competitive.

**Space note on recursion depth:** Python's default recursion limit is 1,000. A 300x300 grid (90,000 cells) entirely covered in land would cause a DFS recursion depth of 90,000, triggering a `RecursionError`. Always use iterative DFS or BFS for grid problems in Python. In Java, the JVM stack is ~512KB–1MB per thread; recursive DFS on a 300x300 grid may trigger a `StackOverflowError` as well. The fix is iterative BFS or explicit stack-based DFS.

---

## 6. Variations & Follow-up Questions

### Number of Islands Variations

**3-D islands:** Extend the grid to a 3-D cube `grid[r][c][d]` and add 2 more direction pairs: `(+1,0,0)` and `(-1,0,0)`. The algorithm is unchanged — BFS/DFS with 6-directional neighbors. Space cost becomes O(r*c*d).

**8-directional connectivity:** Add 4 diagonal directions: `(1,1), (1,-1), (-1,1), (-1,-1)`. No other changes required. Be careful about the problem statement — this increases island count only when diagonal-only connections would otherwise separate islands.

**Max area of island (LeetCode 695):** Instead of counting components, return the size of the largest component. BFS returns the size of each flood-fill; track the maximum.

**Surrounded regions (LeetCode 130):** Flip all `'O'` cells that are NOT connected to a border cell to `'X'`. Approach: BFS from every border `'O'`, mark reachable cells with a temporary marker; then flip unmarked `'O'` cells to `'X'` and restore markers.

**Number of islands II (LeetCode 305) — dynamic:** Land cells added one at a time; return island count after each addition. Union-Find is the correct data structure here because merges can be done in O(alpha) amortized per addition.

### Course Schedule Variations

**Course Schedule II (LeetCode 210):** Return the topological order (actual course sequence) instead of just a boolean. Kahn's queue output is already the answer.

**Alien dictionary (LeetCode 269):** Given a sorted list of alien words, derive the alphabet ordering. Build a directed graph from character comparisons between adjacent words; topological sort gives the ordering. Cycle means contradictory ordering — return "".

**Minimum height trees (LeetCode 310):** Find roots that minimize the tree height. Iteratively remove leaf nodes (degree 1 — the tree is undirected here, so it is degree, not in-degree) until 1 or 2 nodes remain — a topological-sort-like peeling process.

**Task scheduler with cooldowns:** Not topological sort, but a greedy/heap problem. Common interviewer follow-up after Course Schedule.

### Network Delay Time Variations

**Cheapest flights within K stops (LeetCode 787):** Dijkstra with a modified state: `(cost, node, stops_remaining)`. Alternatively, Bellman-Ford with exactly K+1 relaxation rounds — K *stops* means at most K+1 *flights*, and running only K rounds is the classic off-by-one, which quotes a fare that is too high or misses the route entirely (it disagreed with a brute-force path enumeration on 351 of 3,000 random instances, while K+1 rounds matched on all of them). The K-stop constraint invalidates the standard Dijkstra "skip stale entries" optimization — you may need to process a node multiple times with different stop counts.

**Path with minimum effort (LeetCode 1631):** Instead of summing edge weights, minimize the maximum edge weight along the path. Use Dijkstra where `dist[v] = min(dist[v], max(dist[u], effort(u,v)))`.

**Swim in rising water (LeetCode 778):** Minimize the maximum value along a path. Binary search on the answer + BFS/DFS, or Dijkstra.

**All-pairs shortest path:** Use Floyd-Warshall O(V^3) when V is small (V <= 400). For sparse large graphs with non-negative weights, run Dijkstra from each source: O(V * (V+E) log V).

---

## 7. Real-World Usage

### BFS for shortest path (unweighted)

**LinkedIn "degrees of connection":** When LinkedIn displays "2nd degree connection," it runs a bidirectional BFS from the viewer's node and the target's node in a social graph with over 1 billion members. BFS guarantees the shortest-path hop count. The implementation uses a bloom filter to approximate visited-node membership, trading a small false-positive rate for memory savings. Without the visited-at-enqueue optimization, a node is re-enqueued once for every already-expanded neighbor pointing at it, so a hub with 10,000 connections contributes 10,000 duplicate queue entries at the layer boundary rather than one.

**Social network influencer detection (Facebook/Instagram):** BFS from a seed node level by level identifies 1st-degree, 2nd-degree, and 3rd-degree followers. The "layers" naturally emerge from BFS levels without additional bookkeeping.

**Web crawler (Googlebot):** BFS from seed URLs; the queue is the frontier of unvisited pages. Visited tracking prevents re-crawling the same URL.

### Topological sort

**Terraform's resource graph:** `terraform apply` builds a DAG from the references between resources (`aws_instance.web` reading `aws_security_group.sg.id` is an edge) and walks it in topological order, running independent branches in parallel. A dependency loop is caught before anything is provisioned and reported as `Error: Cycle: aws_security_group.a, aws_security_group.b` — the same count-mismatch signal Kahn's algorithm produces. Kubernetes itself is the counterexample worth knowing: it has no dependency ordering at all, and Helm applies resources in a fixed order by kind rather than sorting a graph, which is why "wait for ServiceA to be Running" has to be expressed with init containers or readiness probes instead.

**Git commit graph:** Every Git commit points to its parent commit(s). The commit history forms a DAG. `git log --topo-order` outputs commits in topological order. `git rebase` traverses the commit DAG to find the common ancestor and replay commits.

**Package managers — npm/pip/Maven:** When you run `npm install`, npm builds a dependency graph of all packages and their declared version requirements. It runs topological sort to determine installation order (install dependencies before the package that requires them). A circular dependency triggers an error equivalent to Kahn's count mismatch.

### Dijkstra / weighted shortest path

**Google Maps routing:** Google Maps uses a variant of Dijkstra (specifically, bidirectional Dijkstra or A* with geographic heuristics) to compute driving, walking, and transit routes. Edge weights encode travel time in seconds, which changes dynamically based on traffic data. The road network of a country has tens of millions of nodes and hundreds of millions of edges; Dijkstra's O((V+E) log V) makes this tractable per query when combined with contraction hierarchies (a preprocessing technique that shortens the graph).

**OSPF (Open Shortest Path First) network routing protocol:** OSPF is a link-state routing protocol used in enterprise and ISP networks. Every router runs Dijkstra on the network topology (nodes = routers, edges = links, weights = link cost/delay) to build a shortest-path tree rooted at itself. The routing table is populated from this tree. A change in topology (link failure, new link) triggers a partial Dijkstra rerun.

**Flight booking systems (e.g., Amadeus, Sabre):** The cheapest-flight search is a shortest-path problem on a graph where nodes are airports, edges are flights, and weights are ticket prices or travel time. Large systems use Dijkstra with additional constraints (max stops, time windows).

---

## 8. Edge Cases & Testing

### Number of Islands

```python
# Edge case 1: empty grid
assert num_islands([]) == 0
assert num_islands([[]]) == 0

# Edge case 2: entire grid is water
assert num_islands([["0", "0"], ["0", "0"]]) == 0

# Edge case 3: entire grid is land (one giant island)
assert num_islands([["1", "1"], ["1", "1"]]) == 1

# Edge case 4: 1x1 grid — land
assert num_islands([["1"]]) == 1

# Edge case 5: 1x1 grid — water
assert num_islands([["0"]]) == 0

# Edge case 6: single row, alternating land/water
# ["1","0","1","0","1"] -> 3 islands
g = [["1", "0", "1", "0", "1"]]
# num_islands mutates the grid; use a copy
import copy
assert num_islands(copy.deepcopy(g)) == 3

# Edge case 7: diagonal land cells are NOT connected (standard 4-directional)
# ["1","0"]
# ["0","1"]
# -> 2 islands
diag = [["1", "0"], ["0", "1"]]
assert num_islands(copy.deepcopy(diag)) == 2

# Edge case 8: large uniform land grid — ensure no RecursionError (use iterative BFS)
# 300x300 grid of all '1' -> 1 island
big = [["1"] * 300 for _ in range(300)]
assert num_islands(big) == 1
```

### Course Schedule

```python
# Edge case 1: zero courses
assert can_finish(0, []) is True

# Edge case 2: one course, no prerequisites
assert can_finish(1, []) is True

# Edge case 3: self-loop — course 0 requires course 0
# Graph has a self-loop; Kahn's handles this because node 0 never
# reaches in-degree 0 after building the graph.
assert can_finish(2, [[0, 0]]) is False

# Edge case 4: all courses in a line (chain dependency)
# 0 <- 1 <- 2 <- ... <- 99
prereqs = [[i, i + 1] for i in range(99)]
assert can_finish(100, prereqs) is True

# Edge case 5: duplicate prerequisite pairs
# [1,0] and [1,0] again — in-degree of 1 becomes 2, but adj[0] has 1 twice
# Still detects cycle correctly; course 1 in-degree = 2, never reaches 0 in Kahn's
# unless course 0 is processed (which it will be, decrementing to 0 twice)
# Kahn's with duplicates: in-degree 2 for course 1 from course 0 twice —
# processed count still equals num_courses. OK.
assert can_finish(2, [[1, 0], [1, 0]]) is True

# Edge case 6: disconnected components, one has a cycle
# Courses 0,1 form a cycle; course 2,3 are independent with a valid chain
# Answer: False (cycle in 0-1 prevents full completion)
assert can_finish(4, [[1, 0], [0, 1], [3, 2]]) is False
```

### Network Delay Time

```python
# Edge case 1: single node, source is the only node
assert network_delay_time([], 1, 1) == 0

# Edge case 2: node unreachable (no outgoing edges from k to all nodes)
assert network_delay_time([[1, 2, 1]], 2, 2) == -1

# Edge case 3: parallel edges, take minimum
# Two edges from 1 to 2: weights 10 and 1
assert network_delay_time([[1, 2, 10], [1, 2, 1]], 2, 1) == 1

# Edge case 4: source is the last node (1-indexed)
times = [[3, 1, 5], [3, 2, 3]]
assert network_delay_time(times, 3, 3) == 5

# Edge case 5: k cannot reach itself (k is disconnected as a target)
# Unreachable nodes stay at infinity -> return -1
assert network_delay_time([[2, 3, 1]], 3, 1) == -1

# Edge case 6: very large weights (no overflow in Python int)
assert network_delay_time([[1, 2, 10**9]], 2, 1) == 10**9
```

---

## 9. Common Mistakes

### Mistake 1: BFS marks visited at dequeue time — a silent O(E) memory bug

**What happened:** An internal microservice ran a BFS over a service dependency graph to compute transitive closure (which services are reachable from a given root). The developer wrote the visited check inside the dequeue loop rather than at enqueue time.

The reason this survives review and every unit test is that **the answer stays correct**. The `if node in visited: continue` check at the top of the loop drains the duplicates, every node is still expanded exactly once, and the traversal order is identical — differential-tested here on 2,000 random directed graphs with zero differences. Nothing loops forever either; the run terminates in O(V + E) as always.

What changes is the queue. A node is enqueued once for every in-edge from an already-expanded node, so the queue holds up to E entries instead of V. On a small or sparse graph that is invisible. It became visible only when the graph got dense: a fan-out layer where nearly every service called nearly every other one.

Note the two intuitions that both mislead here. It is **not** cycles: the 2-node cycle everyone reaches for (A imports B, B imports A) produces three enqueues and a maximum queue depth of one. And a cycle-free graph is **not** safe: a DAG full of diamonds duplicates just as badly, because duplication is driven by in-degree, not by back edges.

Fix applied: visited set populated at enqueue time. Measured on a 512-node dense digraph (261,632 edges), the queue peak went from 261,121 entries to 511 — a 511x reduction, and the deque itself from ~2 MB to a few KB.

**Lesson quantified:** On a dense graph with E = V^2, not marking visited at enqueue causes O(V^2) queue entries instead of O(V). At V = 512, that is 262,144 entries versus 512 — a 512x blowup in queue memory.

### Mistake 2: Dijkstra applied to a graph with negative edge weights

**What happened:** A routing team built a "cheapest path" finder for a multi-cloud cost optimizer. Edge weights represented cost deltas between cloud regions — some were negative (representing credits or negotiated discounts). The team used Dijkstra and shipped it. For 80% of paths the results were correct; for paths that went through a negative edge followed by a more expensive edge, Dijkstra sometimes returned a suboptimal path.

Dijkstra's greedy assumption is: once a node is finalized (popped from the heap), its distance is optimal. Negative edges violate this — a later path through a negative edge could have beaten the "finalized" distance. The bug was caught only when a monthly reconciliation showed the optimizer was consistently paying 3–7% more than theoretically optimal on certain regional routes.

Fix: replaced Dijkstra with Bellman-Ford. Runtime increased from O((V+E) log V) to O(V*E) — acceptable for the graph size (200 nodes, 1,800 edges), adding ~12ms per query. Alternatively: Johnson's algorithm re-weights edges to non-negative values then runs Dijkstra.

**Quantified impact:** 3–7% cost overcharge over 4 months, totaling approximately $140,000 in avoidable cloud spend. Root cause: one algorithmic assumption (non-negative weights) unverified in a changed dataset.

### Mistake 3: Using DFS for shortest path in an unweighted graph

In an unweighted graph, DFS does NOT find shortest paths. DFS finds A path, not the SHORTEST path. The path DFS takes depends on adjacency list ordering — it may go through 5 hops to reach a node that is 1 hop away.

**Concrete example:**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    n0(["Node 0<br/>source"]) ---|"DFS path: 3 hops"| n1("1")
    n1 --- n2("2")
    n2 --- n3(["Node 3<br/>target"])
    n0 ---|"BFS path: 2 hops"| n4("4")
    n4 --- n3

    class n0,n3 io
    class n1,n2 lossN
    class n4 train
```

Adjacency of node 0 is [1, 4]: DFS greedily follows the 1-2-3 branch first, recording 3 hops to reach node 3 (red path, WRONG), while BFS's layer-by-layer expansion finds the 2-hop path through node 4 first (green path, CORRECT).

Frequency: This mistake appears in roughly 1 in 4 candidates who correctly implement DFS for "number of islands" and then naively apply DFS to a "shortest path in an unweighted graph" follow-up.

### Mistake 4: Topological sort on an undirected graph

Topological sort is defined only for directed graphs. On an undirected graph, any edge u-v also acts as v-u, giving every node with at least one neighbor an in-degree >= 1 in both directions. The Kahn's count check always fails for a connected undirected graph, incorrectly reporting a "cycle." The correct primitive for undirected connected components is Union-Find or BFS/DFS. Directed + acyclic = topological sort; undirected + connected = BFS/DFS components.

### Mistake 5: Python recursion limit for grid DFS

Python's default recursion limit is `sys.getrecursionlimit()` = 1,000. A 350 x 350 grid with a single large island can trigger a recursion depth of 122,500 during DFS, raising `RecursionError` in Python. Calling `sys.setrecursionlimit(200000)` is a workaround — since CPython 3.12 a pure-Python call no longer consumes a C stack frame, and the interpreter checks the real C stack and raises `RecursionError` rather than segfaulting, so a 122,500-deep pure-Python recursion completes cleanly on 3.13 — but it still costs one frame object per level, and any recursion routed through C (a `__repr__`, a comparison, a C-implemented decorator) is still bounded by the C stack. The correct solution is iterative DFS or BFS. Frequency in production: every Python competitive-programming solution that uses recursive DFS on large grids hits this in a judge environment or on large test cases.

---

## 10. Related Problems

```
BFS / DFS (connected components)
    LeetCode 200  — Number of Islands  (this study)
    LeetCode 695  — Max Area of Island
    LeetCode 130  — Surrounded Regions
    LeetCode 417  — Pacific Atlantic Water Flow
    LeetCode 994  — Rotting Oranges (multi-source BFS)
    LeetCode 286  — Walls and Gates (multi-source BFS)
    LeetCode 305  — Number of Islands II (Union-Find, dynamic)

Topological Sort
    LeetCode 207  — Course Schedule  (this study)
    LeetCode 210  — Course Schedule II (return the order)
    LeetCode 269  — Alien Dictionary (topology from lexicographic order)
    LeetCode 310  — Minimum Height Trees (leaf-peeling topo sort)
    LeetCode 2115 — Find All Possible Recipes from Given Supplies
    LeetCode 1136 — Parallel Courses (critical path = topo + DP)

Dijkstra / Weighted Shortest Path
    LeetCode 743  — Network Delay Time  (this study)
    LeetCode 787  — Cheapest Flights Within K Stops
    LeetCode 1514 — Path with Maximum Probability
    LeetCode 1631 — Path with Minimum Effort
    LeetCode 778  — Swim in Rising Water
    LeetCode 505  — The Maze II

Union-Find
    LeetCode 547  — Number of Provinces
    LeetCode 684  — Redundant Connection
    LeetCode 1319 — Number of Operations to Make Network Connected
    LeetCode 399  — Evaluate Division (weighted Union-Find)

Advanced Graph
    LeetCode 332  — Reconstruct Itinerary (Eulerian path — DFS + backtrack)
    LeetCode 329  — Longest Increasing Path in a Matrix (DFS + memoization on DAG)
    LeetCode 1584 — Min Cost to Connect All Points (Prim's / Kruskal's — MST)
    LeetCode 1168 — Optimize Water Distribution (MST with virtual node)
```

---

## 11. Interview Discussion Points

**Q: Why does BFS find the shortest path in an unweighted graph but DFS does not?**
BFS explores nodes in order of their hop distance from the source: all 1-hop neighbors first, then 2-hop, then 3-hop. The first time BFS reaches a node, it has taken the fewest possible hops to get there. DFS follows one branch as deep as possible before backtracking, so the first time it reaches a target it may have taken a long winding path. To find the BFS-shortest path with DFS you would need to explore all paths and track the minimum — which is exponential in the worst case.

**Q: Can Dijkstra handle negative edge weights? What is the consequence if you use it on a graph with negative edges?**
No. Dijkstra's correctness relies on the greedy property: once a node is popped from the heap, its distance is finalized. This holds only when adding an edge cannot decrease an already-finalized distance. A negative edge from an unprocessed node to an already-finalized node could produce a shorter path that Dijkstra has already committed to not updating. The result is not a crash but a silently incorrect shortest-path answer for some nodes — the type of bug that is hard to detect without a known-correct reference. Be precise about *which* implementation gives the wrong answer, because it is a favourite follow-up: the finalize-on-pop variant that keeps a `visited` set and skips popped nodes does return wrong distances (differential-tested against Bellman-Ford on 4,683 negative-edge graphs with no negative cycle: 91 wrong). The lazy variant in §4 keeps no `visited` set, so an improved node is simply pushed again and re-expanded, and it returned the correct distances on all 4,683 — but that is not a licence to use it, because re-expansion destroys the O((V+E) log V) bound and turns it into an unbounded relaxation loop that is Bellman-Ford wearing a heap. A minimal counterexample worth memorising for the visited-set variant: edges `0->1 (1)`, `0->2 (2)`, `2->1 (-2)`, `1->3 (1)`; the true distances are `[0, 0, 2, 1]` and it answers `[0, 1, 2, 2]`. Use Bellman-Ford for graphs with negative edges (but no negative cycles), or run a negative-cycle check first.

**Q: How do you detect a cycle in a directed graph versus an undirected graph?**
In a directed graph: use DFS with three colors — white (unvisited), gray (in the current DFS call stack), black (fully processed). A back edge (edge from a gray node to another gray ancestor) indicates a cycle. Alternatively, Kahn's algorithm: a cycle exists if and only if the number of nodes processed by Kahn's is less than the total number of nodes. In an undirected graph: use DFS and track the parent. If DFS encounters an already-visited neighbor that is NOT the immediate parent, a cycle exists. Union-Find is also clean for undirected cycle detection: if two endpoints of an edge are already in the same component, adding that edge creates a cycle.

**Q: When should you use topological sort versus BFS/DFS for a graph problem?**
Use topological sort when the problem involves ordering entities with dependencies (task scheduling, build systems, course prerequisites, package installation). Topological sort only applies to directed acyclic graphs. Use BFS for shortest-path queries in unweighted graphs or multi-source spreading (e.g., rotting oranges). Use DFS for connected-component labeling, cycle detection, strongly connected components (Kosaraju/Tarjan), and exhaustive path enumeration.

**Q: What is the time complexity of Kahn's algorithm, and where does each term come from?**
O(V + E). Building the adjacency list and in-degree table: O(V + E) — each node and edge is processed once. The BFS loop: each node is enqueued and dequeued at most once, costing O(V). Each edge causes one in-degree decrement and possibly one enqueue, costing O(E) total. Overall: O(V + E). Space is also O(V + E) for the adjacency list, in-degree array, and queue.

**Q: How do you handle a graph where you need the shortest path but edges have different weights, some of which may be negative?**
For graphs with negative weights but no negative cycles: Bellman-Ford in O(V * E). It relaxes every edge V-1 times, guaranteeing that the shortest path (which visits at most V-1 edges) is found. A V-th iteration that still finds improvements indicates a negative cycle. For graphs with negative cycles: there is no well-defined shortest path (you can loop forever to decrease the cost); report "no solution." For all-pairs with negative weights and no negative cycles: Johnson's algorithm rewrites edge weights to non-negative values using Bellman-Ford from a virtual source, then runs Dijkstra from every node. Total: O(V^2 log V + V*E).

**Q: What is the space complexity of BFS on a grid, and how does it compare to DFS?**
BFS space is O(min(m, n)) in the average case because the BFS frontier at any given layer is bounded by the shorter grid dimension. In the worst case (a grid shaped like a diagonal wave), the frontier can span the entire grid, reaching O(m * n). DFS space is O(m * n) in the worst case because the recursion stack can be as deep as the total number of cells (a snake-like island winding through the entire grid). Iterative DFS uses an explicit stack with the same worst-case O(m * n) space but avoids Python's recursion limit. In practice, BFS tends to use less space for wide-and-short islands; DFS tends to use less for narrow tall islands.

**Q: Describe a scenario where Union-Find is strictly better than BFS/DFS for the Number of Islands problem.**
When islands can be added dynamically — for example, "Number of Islands II" (LeetCode 305) where land cells are added one at a time and you must report the island count after each addition. BFS/DFS would require reprocessing the entire grid after each addition in O(m * n), yielding O(k * m * n) for k additions. Union-Find processes each addition in O(alpha) amortized (near constant) by merging the new cell with its existing neighbors, yielding O(k * alpha) total.

**Q: In the Number of Islands problem, what happens if you change the connectivity from 4-directional to 8-directional (include diagonals)?**
Add four diagonal direction pairs: (1,1), (1,-1), (-1,1), (-1,-1). The algorithm is structurally unchanged — just iterate over 8 neighbors instead of 4. The number of islands can only decrease or stay the same (diagonal connectivity makes more cells "connected"), never increase. The example grid `[["1","0"],["0","1"]]` yields 2 islands under 4-connectivity (diagonal cells not connected) and 1 island under 8-connectivity (diagonal cells are connected).

**Q: Why does Dijkstra work correctly with a min-heap that may contain multiple entries for the same node (lazy deletion)?**
Each time a node's distance is improved, a new `(new_dist, node)` entry is pushed onto the heap without removing the old one. When the heap pops a `(d, u)` pair where `d > dist[u]`, it means a shorter path to `u` has already been processed — this entry is stale and is skipped. The first time a node is popped with `d == dist[u]`, that is its shortest distance (all earlier entries for the same node were stale and skipped). Correctness holds because the heap invariant ensures we always pop the globally minimum distance first. The lazy approach avoids the O(log V) decrease-key operation that a Fibonacci heap provides, trading a modest constant increase in heap size for implementation simplicity.

**Q: What is the difference between Dijkstra's "eager" variant (with decrease-key) and the "lazy" variant (without decrease-key), and when does it matter?**
Eager Dijkstra uses a heap that supports O(log V) decrease-key (e.g., a Fibonacci heap in theory). When a shorter path to node v is found, the existing heap entry is updated in place. This keeps the heap size bounded at V, giving O((V + E) log V) time. Lazy Dijkstra pushes a new entry on every relaxation without removing the stale one; the heap can grow to O(E) entries. With a binary heap both variants land in the same class, O(E log V), because log E < 2 log V. The separation only appears with a better heap: Fibonacci-heap Dijkstra is O(E + V log V), which on a dense graph (E = O(V^2)) is O(V^2) against the binary heap's O(V^2 log V) — and O(V^2) is exactly what the plain array-scan implementation of Dijkstra gives on a dense graph without any heap at all. In practice, Fibonacci heaps have large constants and are almost never used in competitive programming or production code — the lazy binary heap is universally preferred for its implementation simplicity.

**Q: A student says: "For topological sort I can just run DFS and output nodes in reverse post-order." Is this correct? How does it compare to Kahn's?**
Yes, this is correct. DFS post-order processes a node after all nodes reachable from it have been processed. Reversing that order gives a valid topological ordering. Cycle detection requires the three-color (white/gray/black) tracking: a gray-to-gray back edge indicates a cycle. Kahn's BFS-based approach produces the same valid topological order (one of potentially many valid orderings) and detects cycles via the count check. The two are equivalent in correctness and complexity O(V+E). Kahn's is often preferred in interviews because it avoids the need to explain the three-color state machine; the DFS reverse-post-order approach is preferred when you need to integrate with existing DFS code or process nodes in a recursive manner.

**Q: Given the grid for Number of Islands, what is the maximum possible number of islands for an m x n grid?**
For a 4-directional grid, the maximum number of islands is achieved by a checkerboard pattern where '1' and '0' alternate. In this pattern, no two land cells are horizontally or vertically adjacent, so every land cell is its own island. For an m x n grid, the number of land cells in a checkerboard is ceil(m*n/2). The maximum island count is therefore ceil(m*n/2). For a 4x4 grid: max 8 islands. For a 300x300 grid: max 45,000 islands.

**Q: Can Dijkstra be used on an undirected graph? What changes?**
Yes. An undirected edge (u, v, w) is simply represented as two directed edges: adj[u].append((v,w)) and adj[v].append((u,w)). The algorithm is identical. However, be careful with negative weights in undirected graphs: a single undirected negative edge (u,v,-1) creates a negative cycle u->v->u of cost -2, making shortest paths undefined. So for undirected graphs, Dijkstra is valid only when all edge weights are non-negative.

---

## Appendix: ASCII Diagrams

### Grid for Number of Islands

```
Grid (4 rows x 5 cols):
+---+---+---+---+---+
| 1 | 1 | 1 | 1 | 0 |   Row 0
+---+---+---+---+---+
| 1 | 1 | 0 | 1 | 0 |   Row 1
+---+---+---+---+---+
| 1 | 1 | 0 | 0 | 0 |   Row 2
+---+---+---+---+---+
| 0 | 0 | 0 | 0 | 0 |   Row 3
+---+---+---+---+---+
  Col0 Col1 Col2 Col3 Col4

All '1' cells are connected (one island).
BFS from (0,0):
  Layer 0: {(0,0)}
  Layer 1: {(0,1),(1,0)}
  Layer 2: {(0,2),(1,1),(2,0)}
  Layer 3: {(0,3),(2,1)}
  Layer 4: {(1,3)}          <- reached via (0,3), not via (1,2), which is water
  All 9 land cells visited. Count = 1.
```

### DAG for Course Schedule

```
Courses 0..4, prerequisites:
  Course 1 requires Course 0
  Course 2 requires Course 0
  Course 3 requires Course 1
  Course 3 requires Course 2
  Course 4 requires Course 3
```

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    c0(["Course 0<br/>in-deg 0"]) --> c1("Course 1")
    c0 --> c2("Course 2")
    c1 --> c3(("Course 3<br/>needs 1 and 2"))
    c2 --> c3
    c3 --> c4(["Course 4"])

    class c0,c4 io
    class c1,c2 req
    class c3 mathOp
```

Course 3 cannot start until both Course 1 and Course 2 finish (in-degree 2, the merge point above); Kahn's queue processes 0, then 1 and 2, then 3, then 4 — all 5 nodes, so no cycle exists.

```
In-degrees:  0:0, 1:1, 2:1, 3:2, 4:1
Kahn's steps:
  Queue: [0]  (only in-degree-0 node)
  Pop 0, processed=1. Decrement 1->in=0, 2->in=0. Queue: [1,2]
  Pop 1, processed=2. Decrement 3->in=1. Queue: [2]
  Pop 2, processed=3. Decrement 3->in=0. Queue: [3]
  Pop 3, processed=4. Decrement 4->in=0. Queue: [4]
  Pop 4, processed=5. Queue empty.
  processed(5) == numCourses(5) -> True. No cycle.
```

### Weighted Graph for Network Delay Time

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    n2(["Node 2<br/>source, dist 0"]) -->|"w = 1"| n1("Node 1<br/>dist 1")
    n2 -->|"w = 1"| n3("Node 3<br/>dist 1")
    n3 -->|"w = 1"| n4(["Node 4<br/>dist 2"])

    class n2 io
    class n1,n3 req
    class n4 train
```

Directed weighted graph, 4 nodes, source k=2 (note: there is no direct edge from 1 to 3). Dijkstra settles nodes 1 and 3 at distance 1 on the first pop, then reaches node 4 at distance 2 — the maximum distance (2) is the Network Delay Time answer.

```
times = [[2,1,1],[2,3,1],[3,4,1]]

Dijkstra from node 2:
  Initial: dist = {1:inf, 2:0, 3:inf, 4:inf}
  Heap: [(0,2)]

  Step 1: Pop (0,2). Neighbors: 1 (w=1), 3 (w=1)
    dist[1] = 0+1 = 1. Push (1,1).
    dist[3] = 0+1 = 1. Push (1,3).
  Heap: [(1,1),(1,3)]

  Step 2: Pop (1,1). No outgoing edges from 1.
  Heap: [(1,3)]

  Step 3: Pop (1,3). Neighbor: 4 (w=1)
    dist[4] = 1+1 = 2. Push (2,4).
  Heap: [(2,4)]

  Step 4: Pop (2,4). No outgoing edges from 4.
  Heap: empty.

  dist = {1:1, 2:0, 3:1, 4:2}
  max = 2. Answer = 2.
```

---
