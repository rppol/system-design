# Shortest Path (Dijkstra, Bellman-Ford, 0-1 BFS, Floyd-Warshall)

## Pattern Snapshot

**What it is**: A family of algorithms for finding the minimum-cost path
between nodes in a **weighted** graph. Algorithm choice is dictated entirely
by the **shape of the edge weights**: non-negative -> Dijkstra; negative
allowed -> Bellman-Ford; only `{0, 1}` -> 0-1 BFS; need *all pairs* ->
Floyd-Warshall.

**One-line cue**: "Shortest/cheapest/minimum cost path" with **weighted**
edges (if unweighted, it's plain BFS — see
[`graph_traversal.md`](graph_traversal.md)).

**Typical complexity**: Dijkstra `O((V+E) log V)`; Bellman-Ford `O(V * E)`;
0-1 BFS `O(V+E)`; Floyd-Warshall `O(V^3)`.

---

## 1. Recognition Signals

**Use a shortest-path algorithm when you see:**
- "Network Delay Time" — time for a signal to reach all nodes from a source
- "Cheapest Flights Within K Stops" — minimum cost with a step constraint
- "Minimum effort path" / "path with maximum probability" — the "cost" of a
  path is some aggregate (max, product) of edge values, not a simple sum
- Edge weights are explicitly given (distances, costs, times, probabilities)
- "All pairs shortest path" / "for every pair of cities, find the shortest
  route"
- Edge weights are **only 0 or 1** (e.g., "0 cost to continue straight, 1
  cost to change direction")
- Graph may contain **negative weights** (and you need to detect "negative
  cycle" — an arbitrage-style infinite-improvement loop)

**Anti-signals (looks similar, use a different pattern):**
- All edges have the **same weight** (or no weight at all) ->
  [`graph_traversal.md`](graph_traversal.md) — plain BFS already gives
  shortest path in O(V+E), no heap needed
- The graph is a **DAG** (no cycles) — topological-order DP gives shortest
  *and* longest path in O(V+E), strictly better than Dijkstra ->
  [`topological_sort.md`](topological_sort.md) §6
- "Minimum cost to **connect all** nodes" (not a path between two specific
  nodes) — that's Minimum Spanning Tree -> [`union_find.md`](union_find.md)
  (Kruskal's)
- "Generate the actual sequence of moves/choices" with backtracking needed ->
  [`backtracking.md`](backtracking.md)

---

## 2. Mental Model & Intuition

Dijkstra grows a "settled" region outward from the source, always picking the
closest *unsettled* node next — a min-heap replaces "scan all nodes for the
minimum."

```
Graph (directed, weighted):           Dijkstra from A:

   A --1--> B --1--> D                dist = {A:0, B:inf, C:inf, D:inf}
   |                 ^                heap = [(0,A)]
   4                 |
   |                 2                pop (0,A): relax B (0+1=1), relax C (0+4=4)
   v                 |                heap = [(1,B), (4,C)]
   C ----------------+
                                       pop (1,B): relax D (1+1=2)
                                       heap = [(2,D), (4,C)]

                                       pop (2,D): D has no outgoing edges
                                       heap = [(4,C)]

                                       pop (4,C): relax D via C (4+2=6),
                                                   but dist[D]=2 < 6, no update
                                       heap = []

                                       Final: dist = {A:0, B:1, C:4, D:2}
```

**The greedy invariant**: when a node is popped from the heap, its `dist`
value is *final* — no future relaxation can improve it, **because all
remaining unpopped nodes have `dist >= ` the popped node's `dist`, and edge
weights are non-negative**, so any path through an unpopped node would already
be at least as long. **This invariant is exactly what breaks with negative
edges** — see §8.

---

## 3. The Template

```python
from __future__ import annotations
import heapq
from collections import deque

# ---------------------------------------------------------------------------
# Template 1: Dijkstra (non-negative weights, single source)
# ---------------------------------------------------------------------------
def dijkstra(n: int, edges: list[tuple[int, int, int]], src: int) -> list[int]:
    """edges: list of (u, v, weight), directed. Returns dist[] from src."""
    graph: list[list[tuple[int, int]]] = [[] for _ in range(n)]
    for u, v, w in edges:
        graph[u].append((v, w))

    dist = [float("inf")] * n
    dist[src] = 0
    heap: list[tuple[int, int]] = [(0, src)]

    while heap:
        d, u = heapq.heappop(heap)
        if d > dist[u]:
            continue  # stale heap entry -- a shorter path to u was already found
        for v, w in graph[u]:
            nd = d + w
            if nd < dist[v]:
                dist[v] = nd
                heapq.heappush(heap, (nd, v))

    return dist


# ---------------------------------------------------------------------------
# Template 2: Bellman-Ford (negative weights allowed, detects negative cycles)
# ---------------------------------------------------------------------------
def bellman_ford(n: int, edges: list[tuple[int, int, int]], src: int) -> list[float] | None:
    dist: list[float] = [float("inf")] * n
    dist[src] = 0

    for _ in range(n - 1):                 # relax all edges, n-1 times
        for u, v, w in edges:
            if dist[u] != float("inf") and dist[u] + w < dist[v]:
                dist[v] = dist[u] + w

    # one more round: if anything STILL improves, there's a negative cycle
    for u, v, w in edges:
        if dist[u] != float("inf") and dist[u] + w < dist[v]:
            return None  # negative cycle detected

    return dist


# ---------------------------------------------------------------------------
# Template 3: 0-1 BFS (edge weights are only 0 or 1)
# ---------------------------------------------------------------------------
def zero_one_bfs(n: int, graph: list[list[tuple[int, int]]], src: int) -> list[int]:
    """graph[u] = list of (v, weight) where weight in {0, 1}."""
    dist = [float("inf")] * n
    dist[src] = 0
    dq: deque[int] = deque([src])

    while dq:
        u = dq.popleft()
        for v, w in graph[u]:
            nd = dist[u] + w
            if nd < dist[v]:
                dist[v] = nd
                if w == 0:
                    dq.appendleft(v)  # 0-weight: same "layer", process next
                else:
                    dq.append(v)      # 1-weight: next "layer"

    return dist


# ---------------------------------------------------------------------------
# Template 4: Floyd-Warshall (all-pairs shortest path)
# ---------------------------------------------------------------------------
def floyd_warshall(n: int, edges: list[tuple[int, int, int]]) -> list[list[float]]:
    INF = float("inf")
    dist = [[0 if i == j else INF for j in range(n)] for i in range(n)]
    for u, v, w in edges:
        dist[u][v] = min(dist[u][v], w)

    for k in range(n):          # intermediate node -- MUST be outermost loop
        for i in range(n):
            for j in range(n):
                if dist[i][k] + dist[k][j] < dist[i][j]:
                    dist[i][j] = dist[i][k] + dist[k][j]

    return dist
```

---

## 4. Annotated Walkthrough

**Problem**: [Network Delay Time (LC 743)](https://leetcode.com/problems/network-delay-time/)
`times = [[2,1,1],[2,3,1],[3,4,1]]`, `n = 4` nodes, `k = 2` (source).
Each `[u, v, w]` means a signal travels from `u` to `v` in `w` time. Find the
time for the signal to reach **all** nodes (or `-1` if impossible).

**Build graph** (1-indexed nodes 1-4): `graph[2] = [(1,1), (3,1)]`,
`graph[3] = [(4,1)]`, `graph[1] = []`, `graph[4] = []`.

**Dijkstra trace from node 2**:

```
dist = [inf, inf, inf, inf, inf]  (index 0 unused, 1-indexed)
dist[2] = 0
heap = [(0, 2)]

pop (0, 2): d=0 == dist[2]=0, proceed
  relax (1,1): nd=0+1=1 < dist[1]=inf -> dist[1]=1, push(1,1)
  relax (3,1): nd=0+1=1 < dist[3]=inf -> dist[3]=1, push(1,3)
heap = [(1,1), (1,3)]

pop (1,1): d=1 == dist[1]=1, proceed
  graph[1] = [] -- no relaxations
heap = [(1,3)]

pop (1,3): d=1 == dist[3]=1, proceed
  relax (4,1): nd=1+1=2 < dist[4]=inf -> dist[4]=2, push(2,4)
heap = [(2,4)]

pop (2,4): d=2 == dist[4]=2, proceed
  graph[4] = [] -- no relaxations
heap = []

Final dist = [inf, 1, 0, 1, 2]  (indices 1..4)
```

**Answer**: the signal must reach *all* nodes — the answer is
`max(dist[1..4]) = max(1, 0, 1, 2) = 2`. If any `dist[i]` were still `inf`,
the answer would be `-1` (unreachable node).

---

## 5. Complexity

| Algorithm | Time | Space | When to use |
|---|---|---|---|
| Dijkstra (binary heap) | O((V+E) log V) | O(V+E) | Non-negative weights, single source |
| Bellman-Ford | O(V * E) | O(V) | Negative weights allowed; detects negative cycles |
| 0-1 BFS | O(V+E) | O(V+E) | Edge weights are exactly `{0, 1}` |
| Floyd-Warshall | O(V^3) | O(V^2) | All-pairs shortest path; `V` small (typically <= 400) |
| DAG shortest/longest path (topo + DP) | O(V+E) | O(V) | Graph has NO cycles — see [`topological_sort.md`](topological_sort.md) §6 |

**Why 0-1 BFS beats Dijkstra for `{0,1}` weights**: a `deque` with
`appendleft` for 0-weight edges and `append` for 1-weight edges maintains the
same "process in non-decreasing distance order" invariant as a heap — but
insertion/removal from a deque is O(1) vs. O(log V) for a heap, and there's
no `log V` factor at all.

---

## 6. Variations & Sub-patterns

**Cheapest Flights Within K Stops** ([LC 787](https://leetcode.com/problems/cheapest-flights-within-k-stops/)):
plain Dijkstra doesn't directly respect the "at most K stops" constraint,
because its greedy "first pop is final" invariant doesn't account for a
*cheaper-but-more-hops* path being disallowed while a *pricier-but-fewer-hops*
path is allowed. Two correct approaches: **(1)** Bellman-Ford limited to
`K + 1` rounds of relaxation (each round = one more allowed edge), or **(2)**
Dijkstra where the heap state is `(cost, node, stops_used)` and a node can be
popped multiple times with different `stops_used` values.

**Path with Maximum Probability** ([LC 1514](https://leetcode.com/problems/path-with-maximum-probability/)):
"shortest path" becomes "path that **maximizes the product** of edge
probabilities." Use a **max-heap** (negate probabilities, or use
`heapq` with negated values) and replace the relaxation `dist[u] + w <
dist[v]` with `prob[u] * w > prob[v]`. The greedy invariant still holds
because probabilities are in `[0, 1]` (multiplying by a value `<= 1` can only
decrease or maintain the product — the analog of "non-negative weights").

**Path With Minimum Effort** ([LC 1631](https://leetcode.com/problems/path-with-minimum-effort/)):
the "cost" of a path is the **maximum** absolute height difference along it
(a minimax objective), not a sum. Relaxation becomes
`max(effort[u], abs(height[v]-height[u])) < effort[v]`. This "minimize the
maximum edge on the path" pattern also appears in
[Swim in Rising Water (LC 778)](https://leetcode.com/problems/swim-in-rising-water/).
Binary search on the answer (combined with BFS/DFS reachability check) is an
alternative approach for both.

**0-1 BFS in practice** ([Minimum Cost to Make at Least One Valid Path in a
Grid (LC 1368)](https://leetcode.com/problems/minimum-cost-to-make-at-least-one-valid-path-in-a-grid/)):
moving in the direction a grid cell's arrow points costs `0`; moving in any
other direction costs `1` (you "redirect" the arrow). This is exactly a
`{0,1}`-weighted grid graph — 0-1 BFS applies directly.

**All-pairs with a node-count threshold**
([Find the City With the Smallest Number of Neighbors at a Threshold Distance
(LC 1334)](https://leetcode.com/problems/find-the-city-with-the-smallest-number-of-neighbors-at-a-threshold-distance/)):
classic Floyd-Warshall — compute all-pairs shortest distances, then for each
city count how many other cities are reachable within the threshold.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue/twist |
|---|---|---|---|
| [Shortest Path in Binary Matrix (LC 1091)](https://leetcode.com/problems/shortest-path-in-binary-matrix/) | Medium | Unweighted BFS (contrast) | No weights → BFS by hops, not Dijkstra; 8-directional |
| [Network Delay Time (LC 743)](https://leetcode.com/problems/network-delay-time/) | Medium | Plain Dijkstra | The signature problem — answer is `max(dist)` |
| [Cheapest Flights Within K Stops (LC 787)](https://leetcode.com/problems/cheapest-flights-within-k-stops/) | Medium | Bellman-Ford with round limit | Dijkstra's greedy invariant doesn't respect "K stops" |
| [Path with Maximum Probability (LC 1514)](https://leetcode.com/problems/path-with-maximum-probability/) | Medium | Max-heap, multiplicative relaxation | Use a max-heap directly; relax with `*` and `>` |
| [Path With Minimum Effort (LC 1631)](https://leetcode.com/problems/path-with-minimum-effort/) | Medium | Minimax Dijkstra | Relax with `max(...)` instead of `+` |
| [Number of Ways to Arrive at Destination (LC 1976)](https://leetcode.com/problems/number-of-ways-to-arrive-at-destination/) | Medium | Dijkstra + path counting | Track `ways[v]` alongside `dist[v]`, reset count on strict improvement |
| [Find the City ... at a Threshold Distance (LC 1334)](https://leetcode.com/problems/find-the-city-with-the-smallest-number-of-neighbors-at-a-threshold-distance/) | Medium | Floyd-Warshall | All-pairs (V≤100), then count reachable-within-threshold per city |
| [The Maze II (LC 505)](https://leetcode.com/problems/the-maze-ii/) | Medium | Dijkstra, edge = rolling distance | A "move" rolls until a wall; edge weight is the roll length |
| [Find the Safest Path in a Grid (LC 2812)](https://leetcode.com/problems/find-the-safest-path-in-a-grid/) | Medium | Multi-source BFS + max-min Dijkstra | BFS distance-to-threat, then maximize the minimum safeness |
| [Swim in Rising Water (LC 778)](https://leetcode.com/problems/swim-in-rising-water/) | Hard | Minimax Dijkstra on a grid | "Time to swim" = max elevation along the path |
| [Minimum Cost to Make at Least One Valid Path in a Grid (LC 1368)](https://leetcode.com/problems/minimum-cost-to-make-at-least-one-valid-path-in-a-grid/) | Hard | 0-1 BFS | Following the arrow costs 0 (push front); redirecting costs 1 (push back) |
| [Shortest Path in a Grid with Obstacles Elimination (LC 1293)](https://leetcode.com/problems/shortest-path-in-a-grid-with-obstacles-elimination/) | Hard | BFS with state | State = (row, col, eliminations_left); visited keyed on state |
| [Bus Routes (LC 815)](https://leetcode.com/problems/bus-routes/) | Hard | BFS on a route graph | Nodes are routes, not stops; answer = min transfers |
| [Minimum Cost to Reach Destination in Time (LC 1928)](https://leetcode.com/problems/minimum-cost-to-reach-destination-in-time/) | Hard | Dijkstra/DP with budget state | State = (cost, node, time_used); prune when over the time limit |
| [Second Minimum Time to Reach Destination (LC 2045)](https://leetcode.com/problems/second-minimum-time-to-reach-destination/) | Hard | BFS tracking two best distances | Plus traffic-light timing: wait when the signal is red |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake**: using Dijkstra (with a `visited`/"finalized" set that skips
relaxing edges into already-finalized nodes) on a graph that contains a
**negative edge weight**. Dijkstra's core correctness invariant — "once
popped, a node's distance is final" — **requires non-negative weights**, and
silently produces **wrong (too-large) distances** when violated.

```python
# BROKEN: Dijkstra with a visited set, used on a graph with a negative edge
import heapq

def dijkstra_broken(n, edges, src):
    graph = [[] for _ in range(n)]
    for u, v, w in edges:
        graph[u].append((v, w))

    dist = [float("inf")] * n
    dist[src] = 0
    visited = set()
    heap = [(0, src)]

    while heap:
        d, u = heapq.heappop(heap)
        if u in visited:
            continue
        visited.add(u)
        for v, w in graph[u]:
            if v not in visited and d + w < dist[v]:  # BUG: skips relaxing
                dist[v] = d + w                       #      already-visited nodes
                heapq.heappush(heap, (dist[v], v))

    return dist
```

**Trace the bug** on graph `A=0, B=1, C=2, D=3` with edges
`A->B (1)`, `A->C (2)`, `C->B (-5)`, `B->D (1)`:

```
True shortest distances from A:
  A->B: min(1, 2 + (-5)) = min(1, -3) = -3
  A->C: 2
  A->D: dist[B] + 1 = -3 + 1 = -2

dijkstra_broken trace:
dist = [0, inf, inf, inf], heap = [(0,A)]

pop (0,A): not visited. visited={A}.
  relax B: 0+1=1 < inf -> dist[B]=1, push(1,B)
  relax C: 0+2=2 < inf -> dist[C]=2, push(2,C)
heap = [(1,B), (2,C)]

pop (1,B): not visited. visited={A,B}.
  relax D: 1+1=2 < inf -> dist[D]=2, push(2,D)
heap = [(2,C), (2,D)]

pop (2,C): not visited. visited={A,B,C}.
  relax B via C: candidate = dist[C] + (-5) = 2 - 5 = -3
  BUT "B not in visited" is FALSE (B is already in visited)
  -> relaxation SKIPPED. dist[B] stays 1 (WRONG -- true value is -3)
heap = [(2,D)]

pop (2,D): not visited. visited={A,B,C,D}.
  graph[D] = [] -- nothing to relax
  (dist[D]=2 is also WRONG -- true value is -2, since it depends on dist[B])

Final (WRONG): dist = [0, 1, 2, 2]
True answer:   dist = [0, -3, 2, -2]
```

The negative edge `C->B (-5)` offers a cheaper route to `B` *after* `B` was
already "finalized" — but the `visited` check throws that improvement away.

**Fix**: this isn't a one-line patch — **Dijkstra is the wrong algorithm**
when negative edges are possible. Use **Bellman-Ford** (Template 2), which
has no `visited`/finalization concept at all — it simply relaxes *every*
edge, *every* round, for `V-1` rounds, allowing distances to keep improving
until they provably can't anymore.

```python
# FIXED: Bellman-Ford -- no visited set, relax ALL edges repeatedly
def bellman_ford(n, edges, src):
    dist = [float("inf")] * n
    dist[src] = 0
    for _ in range(n - 1):
        for u, v, w in edges:
            if dist[u] + w < dist[v]:
                dist[v] = dist[u] + w
    return dist
```

**Re-trace with the fix** on the same graph,
`edges = [(A,B,1), (A,C,2), (C,B,-5), (B,D,1)]`, `n=4` so `n-1=3` rounds:

```
dist = [0, inf, inf, inf]

Round 1 (process edges in order A->B, A->C, C->B, B->D):
  A->B: 0+1=1 < inf -> dist[B]=1
  A->C: 0+2=2 < inf -> dist[C]=2
  C->B: dist[C]+(-5)=2-5=-3 < dist[B]=1 -> dist[B]=-3
  B->D: dist[B]+1=-3+1=-2 < inf -> dist[D]=-2

dist after round 1 = [0, -3, 2, -2]   <- already correct!

Rounds 2-3: no edge relaxes further (already optimal) -- dist unchanged.

Final dist = [0, -3, 2, -2]  -- matches the TRUE shortest distances.
```

The key difference: Bellman-Ford relaxed `C->B` *after* `B` already had a
tentative distance, and **happily overwrote it** — there's no notion of
"finalized." This is strictly more robust (at the cost of `O(V*E)` instead of
`O((V+E) log V)`), which is exactly the tradeoff for allowing negative edges.

---

## 9. Related Patterns & When to Switch

- **[`graph_traversal.md`](graph_traversal.md)** — if every edge has the
  same weight (or no weight), plain BFS computes shortest path in O(V+E) —
  Dijkstra's heap adds an unnecessary `log V` factor.
- **[`topological_sort.md`](topological_sort.md)** — if the graph is a
  **DAG**, process nodes in topological order and relax edges with a single
  DP pass — O(V+E), handles negative weights fine (no "negative cycle"
  possible in a DAG), and gives **longest** path too (just flip the
  comparison).
- **[`union_find.md`](union_find.md)** — Minimum Spanning Tree (Kruskal's)
  answers a *different* question ("cheapest way to connect ALL nodes") than
  shortest path ("cheapest way from A to B"). Don't conflate the two.
- **[`dynamic_programming.md`](dynamic_programming.md)** — Bellman-Ford
  *is* a DP: `dist_k[v]` = shortest path to `v` using at most `k` edges,
  with `dist_k[v] = min(dist_{k-1}[v], min over (u,v) of dist_{k-1}[u] + w)`.
  Floyd-Warshall is a DP over "allowed intermediate nodes." Recognizing this
  helps explain *why* `V-1` rounds suffice (no shortest simple path uses more
  than `V-1` edges).

---

## 10. Cross-links

- Concept module: [`graph_and_string_algorithms/`](../graph_and_string_algorithms/README.md) —
  formal correctness proofs for Dijkstra's greedy invariant and Bellman-Ford's
  relaxation bound
- Applied: [`../../backend/osi_model_and_networking/`](../../backend/osi_model_and_networking/README.md) —
  link-state routing protocols (OSPF) run Dijkstra on a graph of routers;
  distance-vector protocols (RIP) run a distributed form of Bellman-Ford —
  the "negative cycle = arbitrage" intuition maps to "routing loop" detection

---

## 11. Interview Q&A

**Why doesn't Dijkstra work with negative edge weights — what specifically
breaks?**
Dijkstra's correctness relies on the invariant "when a node is popped, its
distance is final," which is proven using the fact that all *unpopped* nodes
have `dist >= ` the popped node's distance, and edges are non-negative — so no
future path through an unpopped node can be shorter. A negative edge can make
a path *through* a higher-distance node end up *shorter* than an
already-finalized distance — exactly the scenario traced in §8, where
`C->B (-5)` makes the true `dist[B] = -3`, but `B` was already finalized at
`1`.

**Dijkstra vs. Bellman-Ford — beyond "negative weights," what's the practical
tradeoff?**
Dijkstra is `O((V+E) log V)` — much faster for large graphs, but requires
non-negative weights. Bellman-Ford is `O(V*E)` — works with negative weights
and can *detect* negative cycles (a cycle whose total weight is negative,
meaning "shortest path" is undefined — you could loop forever decreasing
cost). If you know weights are non-negative (the overwhelming majority of
real-world "distance"/"time"/"cost" problems), always prefer Dijkstra.

**Why is 0-1 BFS O(V+E) instead of O((V+E) log V) like Dijkstra?**
A `deque` supports O(1) push/pop from both ends, vs. O(log V) for a heap.
0-1 BFS maintains the same "process nodes in non-decreasing distance order"
invariant as Dijkstra, but because weights are only 0 or 1, a 0-weight edge
keeps a node in the *same* distance "tier" (push to front) and a 1-weight
edge moves it to the *next* tier (push to back) — the deque's two ends
naturally represent "current tier" and "next tier" without needing a heap's
full ordering capability.

**When is Floyd-Warshall's O(V^3) actually acceptable, and what extra
information does it give you that running Dijkstra V times doesn't?**
O(V^3) is fine for `V` up to a few hundred (10^6-10^8 operations). Running
Dijkstra from every node is `O(V * (E log V))` — for dense graphs
(`E ~ V^2`), that's `O(V^3 log V)`, actually *worse* than Floyd-Warshall's
`O(V^3)`. Floyd-Warshall also handles negative edges (though not negative
cycles) more simply than running Bellman-Ford from every node
(`O(V^2 * E)`).

**Why doesn't plain Dijkstra directly solve "Cheapest Flights Within K
Stops"?**
Dijkstra's greedy invariant assumes that once the cheapest path to a node is
found, it's final — but here, the cheapest *overall* path to a node might use
*more* stops than allowed, while a *more expensive* path uses fewer stops and
is still the best *valid* answer. The state needs to be `(cost, node, stops)`
— the same node can be legitimately revisited with a different `stops` count
and a *worse* cost but still be useful, which violates "first pop is final."
Bellman-Ford limited to `K+1` rounds sidesteps this naturally — round `i`
represents "best cost using at most `i` edges."

**Why use a min-heap in Dijkstra instead of just scanning all unvisited nodes
for the minimum each iteration (the textbook O(V^2) version)?**
Both are correct. The O(V^2) scan is actually *better* for **dense** graphs
(`E ~ V^2`) because `O(V^2)` beats `O((V+E) log V) = O(V^2 log V)`. The
heap-based version wins for **sparse** graphs (`E ~ V`), where
`O((V+E) log V) = O(V log V)` beats `O(V^2)`. Most LeetCode graphs are sparse,
so the heap version is the default — but knowing the dense-graph alternative
is a strong signal of depth in an interview.

**How does Bellman-Ford detect a negative cycle, and why does it take exactly
`V-1 + 1` rounds?**
Any shortest *simple* path (no repeated nodes) in a graph with `V` nodes has
at most `V-1` edges. So after `V-1` rounds of relaxing all edges, every
shortest simple path's distance must be final — **unless** a negative cycle
exists, in which case "shortest path" is unbounded (you can loop the cycle
infinitely to keep decreasing cost). The `V`-th round (one extra relaxation
pass) checks: if *anything* still improves after `V-1` rounds, that
improvement can only come from a negative cycle.

**Path with Maximum Probability uses a max-heap and multiplies probabilities
— why does the greedy "pop is final" invariant still hold here?**
The invariant requires that extending a path can never *help* relative to
stopping early — i.e., the "combine" operation must be monotonically
non-improving. For sums with non-negative weights, `dist[u] + w >= dist[u]`.
For products of probabilities in `[0,1]`, `prob[u] * w <= prob[u]` (since
`w <= 1`). Both satisfy "extending can't make it look better than the prefix
already was" — which is the real requirement, not "addition" specifically.

**Is BFS a "special case" of Dijkstra? In what sense?**
Yes — BFS is Dijkstra where all edge weights equal 1 (or 0, for 0-1 BFS). A
plain `deque`-based queue, where every node is appended to one end, IS
implicitly maintaining "process in non-decreasing distance order" — because
each BFS layer corresponds to exactly one unit of distance. Dijkstra
generalizes this to arbitrary non-negative weights by replacing the queue's
implicit ordering with an explicit min-heap.

**The `if d > dist[u]: continue` line in Template 1 — what does it do, and
does omitting it cause wrong answers or just inefficiency?**
It skips "stale" heap entries — a node can be pushed onto the heap multiple
times (once per relaxation that improved its distance), but only the
*smallest* pushed distance for each node is still relevant once a smaller one
has been processed. Omitting this check does **not** cause wrong answers
(the `if nd < dist[v]` check in relaxation already prevents using a stale,
larger `d` to "worsen" `dist[v]`) — it only causes redundant heap operations
on entries that will immediately fail the relaxation check anyway. It's a
performance optimization, not a correctness requirement.

**For DAG shortest path via topological sort, why is it both faster AND more
general (handles negative weights) than Dijkstra?**
A DAG has no cycles, so "negative cycle" is impossible by definition —
Bellman-Ford's only advantage over Dijkstra (negative-weight tolerance) comes
"for free" with no extra cost. Processing nodes in topological order
guarantees that when you relax `u`'s outgoing edges, `dist[u]` is *already
final* — every predecessor of `u` was processed earlier in topo order, so
nothing can improve `dist[u]` after the fact. This gives Dijkstra's
"pop is final" guarantee *without* needing a heap or non-negative weights —
hence O(V+E).
