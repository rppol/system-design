# Greedy Algorithms and Divide & Conquer

> Greedy bets on the local optimum; divide and conquer bets on clean sub-problem independence.

---

## 1. Concept Overview

**Greedy algorithms** make the locally optimal choice at each step, never reconsidering, and claim the result is globally optimal. They are fast (usually O(n log n) or O(n)) and simple — when they are correct. The hard part is proving correctness, not implementing.

**Divide and Conquer (D&C)** splits a problem into independent sub-problems of the same type, solves each recursively, and combines the results. It works when the sub-problems are truly independent (unlike DP where they overlap) and the combination step is efficient.

This module covers: greedy proof techniques (exchange argument, matroid theory concept), canonical greedy problems (interval scheduling, activity selection, Huffman coding, fractional knapsack), D&C recurrences (Master theorem application), and the most important D&C algorithms (merge sort as a proof, Karatsuba multiplication, closest pair of points).

---

## 2. Intuition

> **One-line analogy**: Greedy is like a hiker who always walks uphill toward what looks like the peak — works perfectly on a convex hill, fails on a landscape with false summits. Divide and conquer is like solving a jigsaw by splitting it into independent corner sections, finishing each, then combining them — valid because the sections don't share pieces.

**Mental model for greedy**: Imagine sorting all choices by some criterion, then greedily taking the "best" available option, eliminating conflicted options, and repeating. The challenge is choosing the right sorting criterion — earliest finish time, highest ratio, minimum cost — and proving that this greedy selection never forfeits a better global answer.

**Mental model for D&C**: Draw the recursion tree. Each level does O(f(n)) work; there are O(log n) levels. Total work = O(f(n) × log n) if work per level is constant, or O(n^c) by the Master theorem if sub-problem sizes compound. The combination step (merge in merge sort) is often where the algorithmic insight lives.

**Key insight**: The exchange argument is the fundamental proof technique for greedy: assume there exists an optimal solution that differs from the greedy solution; swap the greedy choice in — show the swapped solution is no worse. If you can always swap without losing optimality, greedy is provably correct.

---

## 3. Core Principles

### Greedy Correctness Properties

**Greedy choice property**: There exists an optimal solution that includes the greedy choice. Proof technique: exchange argument — take any optimal solution, swap in the greedy choice, show the result is still optimal.

**Optimal substructure**: After making the greedy choice, the remaining sub-problem has optimal substructure (the greedy choice + optimal solution to the remainder = overall optimal).

**Matroid theory (concept)**: Matroids formalise the class of problems for which greedy works optimally — problems with an independence structure satisfying the hereditary property (subsets of feasible sets are feasible) and the augmentation property (if A and B are feasible and |A| < |B|, there exists an element in B\A that can be added to A while staying feasible). Kruskal's MST and fractional knapsack satisfy matroid conditions; 0/1 knapsack does not.

### Divide and Conquer Structure

A D&C algorithm has three parts:
1. **Divide**: split the problem of size n into a sub-problems of size n/b each.
2. **Conquer**: solve each sub-problem recursively; base case for small n.
3. **Combine**: merge the a sub-problem solutions into one solution, costing O(n^d).

The Master theorem determines the total complexity from (a, b, d).

---

## 4. Types / Architectures / Strategies

### Greedy Patterns

| Pattern | Sorting criterion | Example |
|---------|-------------------|---------|
| Interval scheduling (max selection) | Earliest finish time | Activity selection, non-overlapping meetings |
| Interval partitioning (min resources) | Earliest start time | Minimum meeting rooms needed |
| Scheduling to minimise lateness | Earliest deadline first | Job scheduling with deadlines |
| Minimum spanning tree | Minimum edge weight | Kruskal's (also uses union-find), Prim's |
| Shortest path | Minimum distance | Dijkstra (greedy relaxation) |
| Optimal encoding | Minimum frequency | Huffman coding |
| Fractional knapsack | Max value/weight ratio | Load optimisation with divisible items |

### Divide and Conquer Patterns

| Pattern | Recurrence | Result | Example |
|---------|------------|--------|---------|
| Binary search | T(n) = T(n/2) + O(1) | O(log n) | Find element in sorted array |
| Merge sort | T(n) = 2T(n/2) + O(n) | O(n log n) | Sort |
| Quicksort (expected) | T(n) = 2T(n/2) + O(n) | O(n log n) | Sort (pivoting) |
| Karatsuba multiplication | T(n) = 3T(n/2) + O(n) | O(n^1.585) | Integer multiplication |
| Closest pair of points | T(n) = 2T(n/2) + O(n log n) | O(n log²n) | Computational geometry |
| Strassen matrix mult | T(n) = 7T(n/2) + O(n²) | O(n^2.807) | Matrix multiplication |

### Master Theorem

For `T(n) = aT(n/b) + O(n^d)`:

```
Case 1: d > log_b(a)  =>  T(n) = O(n^d)           (combine dominates)
Case 2: d = log_b(a)  =>  T(n) = O(n^d * log n)   (equal work at each level)
Case 3: d < log_b(a)  =>  T(n) = O(n^log_b(a))    (leaves dominate)
```

Intuition: compare the "work at each level" (n^d grows as levels widen) with the "number of leaves" (a^(log_b n) = n^(log_b a)). The larger one wins.

---

## 5. Architecture Diagrams

### Interval Scheduling — Earliest Finish Time

```
Intervals (sorted by finish time):
  A: [1, 3]
  B: [2, 4]
  C: [3, 5]
  D: [4, 6]
  E: [5, 7]

Greedy (earliest finish):
  Take A (finishes at 3). Mark [1,3] used.
  B [2,4]: starts at 2, overlaps A (ends 3? B starts 2 < A finishes 3 -> conflict).
  C [3,5]: starts at 3 >= A's finish 3 -> TAKE C.
  D [4,6]: starts at 4 < C's finish 5 -> skip.
  E [5,7]: starts at 5 >= C's finish 5 -> TAKE E.
  Selected: {A, C, E} -> 3 intervals (maximum possible)

Why earliest finish? Taking A leaves the maximum remaining time for future intervals.
If we took B instead (finishes later at 4), C [3,5] would conflict.
```

### Huffman Coding — Greedy Tree Construction

```
Characters and frequencies:
  a:5  b:9  c:12  d:13  e:16  f:45

Step 1: min-heap = [(5,a),(9,b),(12,c),(13,d),(16,e),(45,f)]
Step 2: Pop 5,9 -> create node(14) with children a,b
        heap = [(12,c),(13,d),(14,node),(16,e),(45,f)]
Step 3: Pop 12,13 -> create node(25) with children c,d
        heap = [(14,node),(16,e),(25,node),(45,f)]
Step 4: Pop 14,16 -> create node(30) with children (a,b node),e
        heap = [(25,node),(30,node),(45,f)]
Step 5: Pop 25,30 -> create node(55) with children (c,d),(a,b,e)
        heap = [(45,f),(55,node)]
Step 6: Pop 45,55 -> root(100)

Final tree:          root(100)
                    /         \
                 f(45)       node(55)
                            /       \
                       node(25)   node(30)
                       /    \     /     \
                     c(12) d(13) node  e(16)
                                 / \
                               a(5) b(9)

Codes: f=0 (1 bit), c=100, d=101, e=111 (3 bits), a=1100, b=1101 (4 bits)
Weighted average code length = (45*1 + 12*3 + 13*3 + 16*3 + 5*4 + 9*4)/100
                             = (45+36+39+48+20+36)/100 = 224/100 = 2.24 bits/char
vs. fixed 3-bit encoding = 3 bits/char (Huffman saves ~25%)
```

### Merge Sort — D&C Recursion Tree

```
T(n) = 2T(n/2) + O(n)

Level 0:  n          (1 problem of size n, O(n) merge work)
Level 1:  n/2  n/2   (2 problems of size n/2, total O(n) merge work)
Level 2: n/4 n/4 n/4 n/4  (4 problems, total O(n) work)
...
Level log n: 1 1 1 ... 1  (n problems of size 1, O(n) work to merge)

Each level: O(n) work
Number of levels: log n
Total: O(n log n)

Master theorem: a=2, b=2, d=1
  log_b(a) = log_2(2) = 1 = d -> Case 2 -> O(n^1 * log n) = O(n log n). Confirmed.
```

---

## 6. How It Works — Detailed Mechanics

### Activity Selection (Interval Scheduling Maximisation)

```python
from __future__ import annotations
from typing import List, Tuple


def activity_selection(intervals: List[Tuple[int, int]]) -> List[Tuple[int, int]]:
    """
    Maximum number of non-overlapping intervals.
    Greedy: always pick the interval that finishes earliest.
    O(n log n) for sort, O(n) for selection.
    """
    intervals.sort(key=lambda x: x[1])   # sort by finish time
    selected: List[Tuple[int, int]] = []
    last_finish = float("-inf")

    for start, finish in intervals:
        if start >= last_finish:          # non-overlapping: starts after last finish
            selected.append((start, finish))
            last_finish = finish

    return selected


def min_meeting_rooms(intervals: List[Tuple[int, int]]) -> int:
    """
    Minimum rooms to hold all meetings. Greedy: track when rooms free up.
    O(n log n).
    """
    import heapq
    intervals.sort(key=lambda x: x[0])   # sort by start time
    heap: List[int] = []                  # min-heap of finish times

    for start, finish in intervals:
        if heap and heap[0] <= start:
            heapq.heapreplace(heap, finish)   # reuse the earliest-finishing room
        else:
            heapq.heappush(heap, finish)       # need a new room

    return len(heap)
```

### Fractional Knapsack

```python
def fractional_knapsack(weights: List[float], values: List[float], capacity: float) -> float:
    """
    Fractional knapsack: items can be taken partially.
    Greedy: sort by value/weight ratio descending, take as much as possible.
    O(n log n).
    """
    items = sorted(
        zip(values, weights),
        key=lambda x: x[0] / x[1],
        reverse=True
    )
    total_value = 0.0
    remaining = capacity
    for v, w in items:
        if remaining <= 0:
            break
        take = min(w, remaining)
        total_value += take * (v / w)
        remaining -= take
    return total_value
```

### Huffman Coding

```python
import heapq
from dataclasses import dataclass, field
from typing import Optional, Dict


@dataclass(order=True)
class HuffNode:
    freq: int
    char: Optional[str] = field(default=None, compare=False)
    left: Optional["HuffNode"] = field(default=None, compare=False)
    right: Optional["HuffNode"] = field(default=None, compare=False)


def huffman_codes(frequencies: Dict[str, int]) -> Dict[str, str]:
    """
    Build Huffman tree and return char -> binary code mapping.
    O(n log n) for heap operations.
    """
    heap = [HuffNode(freq=f, char=c) for c, f in frequencies.items()]
    heapq.heapify(heap)

    while len(heap) > 1:
        lo = heapq.heappop(heap)
        hi = heapq.heappop(heap)
        merged = HuffNode(freq=lo.freq + hi.freq, left=lo, right=hi)
        heapq.heappush(heap, merged)

    root = heap[0]
    codes: Dict[str, str] = {}

    def _build_codes(node: Optional[HuffNode], prefix: str) -> None:
        if node is None:
            return
        if node.char is not None:
            codes[node.char] = prefix or "0"   # single-char case
            return
        _build_codes(node.left, prefix + "0")
        _build_codes(node.right, prefix + "1")

    _build_codes(root, "")
    return codes
```

### Divide and Conquer — Merge Sort (Reference Implementation)

```python
def merge_sort_dc(arr: List[int]) -> List[int]:
    if len(arr) <= 1:
        return arr
    mid = len(arr) // 2
    left = merge_sort_dc(arr[:mid])
    right = merge_sort_dc(arr[mid:])
    return _merge_dc(left, right)


def _merge_dc(left: List[int], right: List[int]) -> List[int]:
    result: List[int] = []
    i = j = 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            result.append(left[i]); i += 1
        else:
            result.append(right[j]); j += 1
    result.extend(left[i:])
    result.extend(right[j:])
    return result
```

### Counting Inversions (D&C application)

```python
def count_inversions(arr: List[int]) -> Tuple[List[int], int]:
    """
    Count pairs (i, j) where i < j and arr[i] > arr[j].
    Piggybacks on merge sort. O(n log n).
    """
    if len(arr) <= 1:
        return arr, 0
    mid = len(arr) // 2
    left, left_inv = count_inversions(arr[:mid])
    right, right_inv = count_inversions(arr[mid:])
    merged, split_inv = _merge_count(left, right)
    return merged, left_inv + right_inv + split_inv


def _merge_count(left: List[int], right: List[int]) -> Tuple[List[int], int]:
    result: List[int] = []
    inversions = 0
    i = j = 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:
            result.append(left[i]); i += 1
        else:
            # left[i] > right[j]: all remaining left[i:] are inversions with right[j]
            inversions += len(left) - i
            result.append(right[j]); j += 1
    result.extend(left[i:])
    result.extend(right[j:])
    return result, inversions
```

### Closest Pair of Points

```python
import math
from typing import List, Tuple

Point = Tuple[float, float]


def closest_pair(points: List[Point]) -> float:
    """
    Minimum Euclidean distance between any two points.
    O(n log^2 n). Can be O(n log n) with a sorted-y strip scan.
    """
    def dist(p: Point, q: Point) -> float:
        return math.hypot(p[0] - q[0], p[1] - q[1])

    def brute(pts: List[Point]) -> float:
        min_d = float("inf")
        for i in range(len(pts)):
            for j in range(i + 1, len(pts)):
                min_d = min(min_d, dist(pts[i], pts[j]))
        return min_d

    def rec(pts: List[Point]) -> float:  # pts sorted by x
        n = len(pts)
        if n <= 3:
            return brute(pts)
        mid = n // 2
        mid_x = pts[mid][0]
        dl = rec(pts[:mid])
        dr = rec(pts[mid:])
        d = min(dl, dr)
        # Check strip within distance d of the dividing line
        strip = [p for p in pts if abs(p[0] - mid_x) < d]
        strip.sort(key=lambda p: p[1])           # sort by y
        for i in range(len(strip)):
            j = i + 1
            while j < len(strip) and strip[j][1] - strip[i][1] < d:
                d = min(d, dist(strip[i], strip[j]))
                j += 1
        return d

    sorted_pts = sorted(points)
    return rec(sorted_pts)
```

---

## 7. Real-World Examples

**Huffman coding in compression**: Used in the DEFLATE algorithm (underlying gzip, PNG, zlib), JPEG Huffman entropy coding, and MP3 audio compression. In practice, static Huffman codes are precomputed for a corpus, while adaptive Huffman builds the tree dynamically as data is read. The codes produced are prefix-free: no code is a prefix of another, enabling unambiguous decoding.

**Interval scheduling in operating systems**: The Linux Completely Fair Scheduler (CFS) assigns CPU time using a red-black tree of runnable tasks sorted by virtual runtime. This is a continuous greedy selection: always run the task with the smallest virtual runtime. See [`devops/linux_and_os_fundamentals`](../../devops/linux_and_os_fundamentals/) for CFS in production.

**Kruskal's MST in networking**: Network topology optimisation (finding the minimum-cost spanning tree for a backbone network), cluster analysis (single-linkage hierarchical clustering), and approximating TSP solutions use Kruskal's greedy MST algorithm. See [`graphs_tries_and_advanced_structures`](../graphs_tries_and_advanced_structures/) for Kruskal implementation.

**Merge sort in external databases**: PostgreSQL and MySQL use external merge sort for queries that exceed memory — see [`sorting_and_searching`](../sorting_and_searching/) case study. The merge phase is D&C: recursively merge pairs of sorted runs.

**Karatsuba multiplication in cryptography**: RSA key generation and elliptic curve operations require multiplying 2048–4096-bit integers. The naive O(n²) algorithm is too slow. Karatsuba's O(n^1.585) D&C algorithm is used in GMP (GNU Multiple Precision Arithmetic Library), which underlies OpenSSL's bignum operations.

**Divide and Conquer in parallel computing**: MapReduce is a D&C framework: map splits data into independent chunks (divide), each worker processes its chunk (conquer), and reduce combines results (combine). The independence of the map step is the key property that enables massive parallelism.

---

## 8. Tradeoffs

### Greedy vs DP

| Criterion | Greedy | Dynamic Programming |
|-----------|--------|---------------------|
| Correctness proof needed | Yes (exchange argument required) | Recurrence + base case |
| Time complexity | Usually O(n log n) or O(n) | O(n²) or O(n × W) typical |
| Space complexity | O(1) – O(n) | O(n) – O(n²) |
| Implementation complexity | Low | Medium to high |
| When applicable | Locally optimal = globally optimal | Overlapping sub-problems |
| Backtracking | Never | Not needed (table stores all states) |

### Divide and Conquer vs Dynamic Programming

| Criterion | Divide and Conquer | Dynamic Programming |
|-----------|-------------------|---------------------|
| Sub-problem overlap | Independent (no overlap) | Overlapping (memoisation needed) |
| Memoisation needed | No (each sub-problem solved once) | Yes (sub-problems recur) |
| Parallelisable | Yes (independent sub-trees) | Usually no (dependencies) |
| Classic examples | Merge sort, binary search, FFT | Knapsack, edit distance, LCS |

---

## 9. When to Use / When NOT to Use

**Use greedy when**: you can prove via exchange argument that the locally optimal choice is globally optimal; the problem has matroid structure; the problem involves intervals, scheduling, or compression with a clear sorting criterion; trying brute force and DP first doesn't yield a polynomial solution but greedy does.

**Use D&C when**: the problem naturally decomposes into independent sub-problems of the same type; combining sub-solutions is efficient; the recursion tree has O(log n) depth; parallelism is available.

**Do NOT use greedy when**: there are counterexamples to local optimality (0/1 knapsack, shortest path with negative weights, graph coloring). Test greedy by trying to find a counterexample before coding.

**Do NOT use D&C when**: sub-problems are not independent (use DP instead); the combination step is expensive (O(n²) combine negates the log-n depth benefit unless Master theorem Case 1 doesn't hold); n is small (recursion overhead dominates for n < 10).

---

## 10. Common Pitfalls

### Pitfall 1 — Applying greedy to 0/1 knapsack

```python
# BROKEN: greedy by value/weight ratio fails for 0/1 knapsack
def broken_01_knapsack_greedy(weights, values, capacity):
    items = sorted(
        zip(values, weights),
        key=lambda x: x[0] / x[1],
        reverse=True
    )
    total = 0
    for v, w in items:
        if w <= capacity:          # must take whole item
            total += v
            capacity -= w
    return total
    # Counterexample: items=[(v=60,w=10),(v=100,w=20),(v=120,w=30)], capacity=50
    # Greedy: takes ratio 6, 5, 4 -> first item (v=60,w=10) + second (v=100,w=20)
    #         + third (v=120,w=30) but capacity=50-10-20=20 < 30 -> only 160
    # Optimal: take items 2+3 = 220  -> greedy fails!
```

```python
# FIX: use DP for 0/1 knapsack (greedy only valid for fractional variant)
def fixed_01_knapsack_dp(weights, values, capacity):
    dp = [0] * (capacity + 1)
    for w, v in zip(weights, values):
        for cap in range(capacity, w - 1, -1):
            dp[cap] = max(dp[cap], dp[cap - w] + v)
    return dp[capacity]
```

### Pitfall 2 — Wrong sorting criterion for interval scheduling

```python
# BROKEN: sort by start time (intuitive but wrong for maximisation)
def broken_interval_scheduling(intervals):
    intervals.sort(key=lambda x: x[0])   # sort by start -- WRONG
    selected = [intervals[0]]
    for start, finish in intervals[1:]:
        if start >= selected[-1][1]:
            selected.append((start, finish))
    return selected
    # Counterexample: [(1,10),(2,3),(4,5)] -> broken takes (1,10), misses (2,3)+(4,5)
    # Optimal: (2,3),(4,5) -> 2 intervals. Broken: only (1,10) -> 1 interval.
```

```python
# FIX: sort by FINISH time for maximisation
def fixed_interval_scheduling(intervals):
    intervals.sort(key=lambda x: x[1])   # earliest finish time
    selected = []
    last_finish = float("-inf")
    for start, finish in intervals:
        if start >= last_finish:
            selected.append((start, finish))
            last_finish = finish
    return selected
```

### Pitfall 3 — Stack overflow in deep D&C recursion

```python
# BROKEN: merge sort on 10^6 elements — recursion depth ~ log2(10^6) ≈ 20, OK
# BUT: if the split is uneven (not balanced), depth can be much larger.
# Quicksort on sorted input (without randomization): depth = O(n) -> stack overflow

# FIX: for D&C on untrusted input, either use iterative (bottom-up) approach
# or add an explicit depth guard + fall back to a non-recursive algorithm.
```

### Pitfall 4 — Forgetting to handle the strip in closest pair

```python
# BROKEN: after finding min(dl, dr), only check pairs within the strip
# but skip the y-sorted constraint — O(n^2) strip scan, negating D&C benefit.
def broken_strip_scan(strip, d):
    for i in range(len(strip)):
        for j in range(i + 1, len(strip)):    # O(n^2) — no y cutoff!
            d = min(d, dist(strip[i], strip[j]))
    return d
```

```python
# FIX: stop inner loop when y-distance exceeds d (at most 8 points in the strip
# within distance d of each other — a geometric argument guarantees O(n) total)
def fixed_strip_scan(strip, d):
    strip.sort(key=lambda p: p[1])   # sort by y
    for i in range(len(strip)):
        j = i + 1
        while j < len(strip) and strip[j][1] - strip[i][1] < d:
            d = min(d, dist(strip[i], strip[j]))
            j += 1
    return d
```

### Pitfall 5 — Misapplying the Master theorem

```python
# Master theorem requires: T(n) = aT(n/b) + O(n^d)
# Common mistakes:
# 1. T(n) = T(n-1) + O(n) -> NOT Master theorem form (not dividing by constant b)
#    This is a recurrence for insertion sort: O(n^2).
# 2. T(n) = 2T(n/2) + O(n log n) -> not a clean O(n^d) term (it's O(n^1 * log n))
#    Need extended Master theorem (Akra-Bazzi) or direct expansion.
# 3. Confusing a (number of sub-problems) with b (division factor).
#    Binary search: a=1, b=2, d=0 -> T(n) = O(1) * O(log n) = O(log n). Correct.
#    Merge sort: a=2, b=2, d=1 -> log_2(2)=1=d -> Case 2 -> O(n log n). Correct.
```

---

## 11. Technologies & Tools

| Tool / Library | Use case | Notes |
|----------------|----------|-------|
| `heapq` (Python) | Huffman coding, Prim's/Dijkstra, interval scheduling | Min-heap; negate for max-heap |
| `sortedcontainers.SortedList` | Interval management with O(log n) insert | Mutable sorted structure |
| `zlib` / `gzip` | DEFLATE (LZ77 + Huffman) compression | Industry standard; Huffman codes applied |
| `brotli` | Brotli compression (improved DEFLATE) | Better than gzip for web; also uses Huffman |
| `scipy.spatial.KDTree` | Nearest-neighbour / closest pair | C extension; faster than pure Python D&C |
| NetworkX (`minimum_spanning_tree`) | Kruskal's / Prim's MST | Python graph library |

---

## 12. Interview Questions with Answers

**Q1: What is the exchange argument and how do you apply it to prove greedy correctness?**
The exchange argument proves that the greedy choice can always be swapped into any optimal solution without making it worse. Steps: (1) Assume there is an optimal solution O that differs from the greedy solution G. (2) Find the first point of difference — greedy made choice x, optimal made choice y. (3) Show that swapping y for x in O produces a solution O' that is at least as good as O. (4) By induction, the fully swapped solution equals G and is optimal.

**Q2: Why does earliest-finish-time greedy work for interval scheduling?**
Proof by exchange argument: take any optimal solution O. If O includes a different first interval than greedy, swap in the greedy's first interval (earliest finish time). Because the greedy interval finishes no later, it cannot block more future intervals than the original choice — the number of intervals selected stays the same or increases. Repeat until O equals the greedy solution. Therefore greedy achieves the maximum count.

**Q3: When does greedy fail for knapsack and why?**
Greedy by value/weight ratio fails for 0/1 (indivisible items) knapsack. Counterexample: capacity=10, items={(v=6,w=4), (v=5,w=3), (v=5,w=3)}. Greedy (ratio: 1.5, 1.67, 1.67) takes the two ratio-1.67 items (total w=6, v=10). Optimal: all three items, total w=10, v=16. The greedy fails because committing to the highest-ratio item may leave capacity that combines better with lower-ratio items. Fractional knapsack (items divisible) is solvable by greedy.

**Q4: State the Master theorem and apply it to merge sort and binary search.**
T(n) = aT(n/b) + O(n^d). Compare d vs log_b(a). Merge sort: a=2, b=2, d=1. log_2(2)=1=d → Case 2 → O(n log n). Binary search: a=1, b=2, d=0. log_2(1)=0=d → Case 2 → O(log n). Karatsuba: a=3, b=2, d=1. log_2(3)≈1.585 > 1=d → Case 3 → O(n^1.585).

**Q5: What is Huffman coding and why is it optimal?**
Huffman assigns shorter bit codes to higher-frequency symbols. It is optimal among prefix-free codes (no code is a prefix of another). Optimality proof: suppose a non-Huffman code is better. The two symbols with lowest frequency must have the longest codes (exchange argument: swapping a longer code to a lower-frequency symbol cannot increase average code length). Huffman always assigns the two lowest-frequency symbols the longest codes (siblings at max depth) — this is the greedy induction step. By induction, the greedy tree minimises expected code length.

**Q6: What is Karatsuba multiplication and why is it faster than the naive O(n²) algorithm?**
Naive multiplication of two n-digit numbers: n² multiply-and-add operations. Karatsuba's insight: break each number into two halves (high and low digits). Instead of 4 multiplications of n/2-digit numbers (still O(n²)), use 3 multiplications via algebraic identity: (a+b)(c+d) - ac - bd = ad + bc. T(n) = 3T(n/2) + O(n) → O(n^log_2(3)) ≈ O(n^1.585). Used in GMP (GNU Multiple Precision) for large integer arithmetic in cryptography.

**Q7: How do you count inversions in an array in O(n log n)?**
Piggyback on merge sort. An inversion is a pair (i, j) with i < j and arr[i] > arr[j]. During the merge step: when an element from the right subarray is placed before all remaining left subarray elements, the number of inversions is the count of remaining left elements. This can be accumulated during the merge pass. Total inversions = left inversions + right inversions + split inversions (counted during merge). O(n log n) — same as merge sort.

**Q8: What is the closest pair of points problem and why is the divide step non-trivial?**
Given n points, find the pair with minimum Euclidean distance. Brute force: O(n²). D&C: split points by x-coordinate, find min dist in each half, then check the strip around the dividing line. The key insight is that within the strip of width 2d (where d = min of two halves), each point has at most 7 other points within distance d — a geometric packing argument (points in a d×2d rectangle, each pair ≥ d apart, fit at most 8 points). This makes the strip scan O(n) rather than O(n²).

**Q9: Explain the D&C approach to the maximum subarray problem (Kadane's context).**
D&C: split the array at midpoint. Maximum subarray is either fully in the left half, fully in the right half, or crosses the midpoint. The crossing case: find the maximum suffix sum of the left half and the maximum prefix sum of the right half in O(n) by scanning from midpoint outward. Recurrence: T(n) = 2T(n/2) + O(n) → O(n log n). Kadane's algorithm is O(n) (greedy/DP). D&C is useful when the query must be answered for arbitrary sub-ranges (offline segment tree variant).

**Q10: How does the activity selection problem differ from the weighted interval scheduling problem?**
Activity selection (unweighted): maximise the count of non-overlapping intervals — solvable by greedy (earliest finish time) in O(n log n). Weighted interval scheduling: each interval has a weight; maximise total weight of non-overlapping intervals. Greedy fails (a high-weight interval may block many medium-weight intervals). Requires DP: sort by finish time, dp[i] = max weight using intervals up to i, with binary search to find the last non-conflicting interval. O(n log n).

**Q11: What is the fractional relaxation of 0/1 knapsack, and why is its LP value an upper bound?**
Fractional knapsack allows taking fractions of items, solved greedily by value/weight ratio. This is the linear programming (LP) relaxation of 0/1 knapsack. The LP value ≥ the integer (0/1) optimum because the feasible set of the LP strictly contains the integer feasible set. This is exploited in branch-and-bound algorithms: LP relaxation gives an upper bound at each node; prune branches where the LP bound < current best integer solution.

**Q12: What is the Master theorem's Case 3 (leaves dominate) and give an example?**
Case 3: d < log_b(a) → T(n) = O(n^log_b(a)). The number of leaves dominates — the recursion tree fans out so fast that the work at the leaf level overwhelms all other levels. Example: Strassen's matrix multiplication: a=7, b=2, d=2. log_2(7) ≈ 2.807 > 2 → Case 3 → O(n^2.807). The 7 recursive calls on n/2 sub-matrices (instead of 8 in naive) produce a sub-quadratic algorithm.

**Q13: How does greedy apply to Dijkstra's shortest path algorithm?**
Dijkstra's is a greedy algorithm: at each step, extract the unvisited vertex with the minimum known distance (greedy choice), then relax its outgoing edges. The greedy choice property holds because: once a vertex is extracted with distance d, no shorter path can be found to it later — any alternative path through an unvisited vertex must pass through a vertex with distance ≥ d (assuming non-negative weights), so the alternative total path length ≥ d + non-negative = ≥ d. This fails with negative weights, which is why Bellman-Ford (which is not greedy) is required for negative-weight graphs.

**Q14: What is the time complexity of Huffman encoding and why?**
Building the Huffman tree: O(n log n) using a min-heap of size n. Each of the n-1 merge steps requires two heap pops and one push, each O(log n) → total O(n log n). Encoding: O(L) per symbol where L is the code length. The average code length is O(H) where H is the entropy of the source — Huffman achieves within 1 bit of the entropy (Shannon's source coding theorem).

**Q15: Explain divide and conquer in the context of parallel algorithms.**
D&C maps directly to fork-join parallelism. Divide the problem, spawn a thread/task per sub-problem (fork), wait for all to finish (join), then combine. Speedup: if each level takes O(n) work and there are log n levels, and we have P processors, the parallel time is O(n/P × log n + combine time). MapReduce is exactly this pattern at cluster scale. The independence of sub-problems is the key property — it is what distinguishes parallelisable D&C from DP (which has data dependencies between states).

---

## 13. Best Practices

**Prove correctness before coding**: Write the exchange argument sketch before implementing any greedy algorithm. If you cannot construct the exchange argument, greedy is probably wrong — find a small counterexample (3–5 items) and switch to DP.

**Test greedy on the classic counterexamples**: For interval scheduling — verify you sort by finish time, not start or duration. For knapsack — always verify with a 0/1 vs fractional example.

**Use the Master theorem to quickly classify D&C complexity**: Before analysing a D&C algorithm by hand, identify (a, b, d) and apply the three cases. This takes 30 seconds and avoids summing geometric series by hand.

**Prefer iterative D&C (bottom-up) when recursion depth is a concern**: Binary search is naturally iterative. Bottom-up merge sort (see `sorting_and_searching`) eliminates the call stack entirely. For closest-pair and counting inversions, the recursion depth is O(log n) — safe for n ≤ 10^7.

**For Huffman, verify the prefix-free property**: A Huffman tree is a full binary tree (every internal node has exactly 2 children). The codes read from root to leaf are prefix-free by construction. If your tree has internal nodes with one child, the implementation is wrong.

---

## 14. Case Study

### Scenario: Meeting Room Allocation + Compression for a Video Platform

A video platform needs to (1) schedule live streams across a finite set of encoding servers (minimum servers problem = interval partitioning), and (2) compress stream metadata (JSON event logs) using Huffman coding.

**Part 1 — Minimum encoding servers (interval partitioning)**:

```python
import heapq
from typing import List, Tuple


def min_encoding_servers(streams: List[Tuple[int, int]]) -> int:
    """
    Each stream is (start_time, end_time). Find minimum servers needed
    so no two concurrent streams share a server.
    O(n log n).
    """
    streams.sort(key=lambda x: x[0])
    # min-heap: earliest finishing server's end time
    active_servers: List[int] = []

    for start, end in streams:
        if active_servers and active_servers[0] <= start:
            heapq.heapreplace(active_servers, end)   # reuse server
        else:
            heapq.heappush(active_servers, end)       # allocate new server

    return len(active_servers)


# Example: 10 concurrent streams -> 3 servers needed
streams = [(1, 4), (2, 5), (3, 8), (5, 9), (6, 10), (7, 11), (8, 12), (9, 13), (4, 7), (1, 6)]
print(min_encoding_servers(streams))   # 4
```

**Part 2 — Huffman compression of metadata keys**:

```python
from collections import Counter


def compress_metadata_keys(logs: List[str]) -> dict:
    """
    Build Huffman codes for the most frequent JSON keys in log lines.
    Returns char->code mapping for use in downstream compression.
    """
    freq: dict[str, int] = Counter()
    for line in logs:
        for ch in line:
            freq[ch] += 1

    codes = huffman_codes(freq)   # from §6 implementation above

    # Compute compression ratio
    original_bits = sum(freq[c] * 8 for c in freq)   # 8 bits per ASCII char
    compressed_bits = sum(freq[c] * len(codes[c]) for c in freq)
    ratio = compressed_bits / original_bits

    return {"codes": codes, "compression_ratio": ratio}
```

**BROKEN — using fixed-width codes instead of Huffman**:

```python
# BROKEN: fixed 5-bit codes for 32 distinct characters (alphabet assumed)
def broken_compress(text: str) -> str:
    chars = list(set(text))
    if len(chars) > 32:
        raise ValueError("Too many distinct characters for 5-bit coding")
    fixed_codes = {c: format(i, "05b") for i, c in enumerate(chars)}
    return "".join(fixed_codes[ch] for ch in text)
    # 5 bits/char regardless of frequency -- vs Huffman's 2.2 bits/char average
    # for typical text -> 2.3x worse than Huffman on natural language
```

```python
# FIX: use Huffman coding to assign shorter codes to frequent characters
def fixed_compress(text: str) -> Tuple[str, dict[str, str]]:
    freq = Counter(text)
    codes = huffman_codes(freq)
    encoded = "".join(codes[ch] for ch in text)
    return encoded, codes   # return codebook for decompression
```

**Performance metrics**:

| Metric | Fixed-width (5-bit) | Huffman |
|--------|--------------------|----|
| Bits per char (English text) | 5.0 | ~2.2 |
| Compression ratio vs 8-bit ASCII | 62.5% | 27.5% |
| Codebook size | 32 entries | n entries (n = distinct chars) |
| Build time | O(1) | O(n log n) |
| Decode time | O(L) | O(L) (with pre-built trie) |

**Discussion questions**:
1. How does the greedy proof for Huffman (always merge lowest-frequency pair) guarantee global optimality?
2. If stream durations are not known in advance (online scheduling), how would you modify the server allocation algorithm?
3. Huffman codes are optimal for symbol-by-symbol encoding. Why is LZ77 (used in gzip) better for real text?

---

## See Also

- [sorting_and_searching](../sorting_and_searching/) — merge sort as a D&C proof; binary search
- [dynamic_programming](../dynamic_programming/) — when greedy fails, DP is the fallback
- [graph_and_string_algorithms](../graph_and_string_algorithms/) — Dijkstra (greedy), Kruskal/Prim (greedy MST)
- [heaps_and_priority_queues](../heaps_and_priority_queues/) — Huffman min-heap; Prim's priority queue
- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/) — Master theorem for D&C recurrences
- [DSA Pattern Playbooks](../dsa_patterns/) — apply these techniques: [Greedy](../dsa_patterns/greedy.md), [Merge Intervals](../dsa_patterns/merge_intervals.md)
