# DSA Pattern Playbooks — The Recognition Engine

The pattern-recognition and strategy-selection layer for coding interviews. This section answers the question every L5 candidate must answer in under 5 minutes: *"Given this problem, what pattern do I apply, and why?"*

> **Prerequisites**: [Phase 1–3 concept modules](../) (complexity, data structures, algorithms). Those teach *what* a heap or sliding window is. This section teaches *when* to reach for which one.

> **Scope**: language-agnostic. Code examples are Python-first (type-hinted, runnable-shaped). Java notes are added where the implementation diverges meaningfully.

> **Note on problem links**: this sub-section intentionally uses real LeetCode hyperlinks — e.g., `[Two Sum (LC 1)](https://leetcode.com/problems/two-sum/)` — because navigating to problems is part of the drill workflow. The rest of the repo uses plain-text `LeetCode N` references.

---

## How to Use This Section

**The pattern-recognition workflow:**

```
1. Read the problem statement → identify constraints (n, k, range, sorted?)
2. Consult §3 (Constraints → Complexity table) — read n, derive required complexity
3. Consult §4 (Cue → Pattern table) — match phrases / data shapes in the problem
4. If unsure, walk §5 (decision tree) — branching on input shape
5. Open the matched pattern's .md file — read §1 (Recognition Signals) to confirm
6. Apply §3 (Template) → adapt to the specific problem
7. Fill §7 (Problem Bank) — pick an easy instance of the same pattern to warm up first
```

This section is organized so steps 2–4 are mechanical: you are not "figuring out" the pattern from scratch; you are *looking it up* using the signals you read in the problem.

---

## 1. The Universal Problem-Solving Method — UMPIRE

Use UMPIRE as a timed scaffold during interviews. Each stage has a target time budget for a 45-minute loop.

**U — Understand (3–5 min)**
Clarify requirements before writing any code. Ask: What are the input types and ranges? Can the array be empty? Are values unique? Is the array sorted? Is it a 0-indexed or 1-indexed problem? Can I modify the input in place? What should I return — an index, a value, a list? Restate the problem in your own words and confirm. Write down 2 concrete examples (one normal, one edge).

**M — Match (2–3 min)**
Map the problem to a pattern using §3–§5 below. State the pattern aloud: *"This looks like a sliding window because we need the longest subarray satisfying a constraint and the array is non-negative."* Name the data structure(s) the pattern requires. State the target complexity (O(n), O(n log n), etc.) before you start.

**P — Plan (3–5 min)**
Describe the approach in plain English before coding. Outline the brute-force first (always), state its complexity, then describe the optimization and why it is correct. For medium/hard problems: write pseudocode or note the key invariant you will maintain.

**I — Implement (15–20 min)**
Write clean, typed Python (or the agreed language). One logical step at a time. Name variables to reflect their role (`left`, `right`, `freq`, `seen`). Add a one-line comment at any non-obvious step. Do not micro-optimize during writing — get the correct solution first.

**R — Review (3–5 min)**
Trace through your two examples from U. Check boundary conditions: empty input, single element, all-equal elements, maximum n. Check that your loop terminates. Check that indices do not go out of bounds. Fix bugs in-line.

**E — Evaluate (2–3 min)**
State time and space complexity with justification. Mention any further optimization if you have time. If using extra space, discuss whether it can be eliminated.

See [interview_execution_playbook.md](interview_execution_playbook.md) for the L5 rubric, communication scripts, and "what to say when stuck."

---

## 2. Read `n` Before You Write Anything

The single highest-leverage habit: **look at the input size constraint before thinking about the algorithm**. It tells you the required complexity, which halves the pattern search space.

### Constraints → Complexity → Pattern Table

| Input size `n` | Required complexity | Likely approach |
|---|---|---|
| n ≤ 10 | O(n!) | brute permutations, exhaustive search |
| n ≤ 12 | O(n!) | backtracking (permutations), bitmask enumeration |
| n ≤ 20 | O(2^n) | subsets, bitmask DP, meet-in-the-middle |
| n ≤ 25 | O(2^(n/2)) | meet-in-the-middle (split and hash halves) |
| n ≤ 100 | O(n^3) | triple nested loop, Floyd-Warshall, matrix-chain DP |
| n ≤ 500 | O(n^3) | interval DP, all-pairs shortest path |
| n ≤ 5,000 | O(n^2) | two-pointer + sort, O(n^2) DP (LCS, edit distance small), bubble/insertion sort |
| n ≤ 10^4 | O(n^2) | O(n^2) DP (e.g., naive LIS), brute two-pass scan |
| n ≤ 10^5 | O(n log n) | sort, binary search, heap, balanced BST, merge sort, segment tree |
| n ≤ 5 x 10^5 | O(n log n) | same; constant factor matters — prefer arrays over linked structures |
| n ≤ 10^6 | O(n) | sliding window, two pointers, prefix sum, hashing, counting sort, linear DP |
| n ≤ 10^7 | O(n) tight | linear scan, sieve of Eratosthenes (n/ln n), Radix sort |
| n ≤ 10^8 | O(n) or O(log n) | math / formula, binary search on answer |
| n > 10^9 | O(log n) or O(1) | binary search on answer, modular arithmetic, matrix exponentiation |

**Reading a grid (m × n):** treat the total cells m*n as your effective n. A 1000×1000 grid has n=10^6; O(m*n) is fine, O(m*n * log(mn)) may be tight.

**Reading k (number of results / window size):** constraints on k (e.g., k ≤ 10^4 separately from n ≤ 10^5) can open O(n*k) windows. Read both.

---

## 3. Cue → Pattern Lookup Table

Scan the problem statement for these signals. The match does not have to be word-for-word — match the *shape*.

### Array / String Signals

| Signal / phrase in the problem | Pattern to reach for | Notes |
|---|---|---|
| Sorted array + "find pair/triplet summing to target" | **Two pointers** | Two Sum on sorted array; deduplicate with `if nums[i] == nums[i-1]: continue` |
| Sorted array + "find closest pair" / "remove duplicates" | **Two pointers** | One pointer writes, one reads |
| Unsorted + "pair summing to target" | **Hashing (complement)** | `seen = {}; if target - x in seen` |
| "Contiguous subarray/substring" + longest/shortest/max/min under a constraint | **Sliding window** | Constraint must be monotonic (expandable/shrinkable) |
| "Subarray sum equals k" / "count subarrays with sum k" | **Prefix sum + hashmap** | `prefix[i] - prefix[j] == k` → `seen[prefix[i] - k]` |
| "Range sum query" / "point update + range query" | **Prefix sum** (static) or **Fenwick / Segment tree** (dynamic) | Static = precompute; dynamic updates need Fenwick |
| "Find missing number" / "find duplicate" + numbers in [1..n] + O(1) space | **Cyclic sort** | Place each number at index `num-1`; scan for mismatch |
| "Next greater element" / "next smaller element" / "stock span" / "largest rectangle in histogram" | **Monotonic stack** | Decreasing stack for next-greater; increasing for next-smaller |
| "Sliding window maximum/minimum" | **Monotonic deque** | `collections.deque`; pop from back when new value dominates |
| "Count of subarrays/substrings satisfying condition" — condition is hard to maintain directly | **Sliding window with at-most(k) trick** | `count(exactly k) = count(at most k) - count(at most k-1)` |
| "Frequency" / "anagram" / "group by" / "two elements differ by k" | **Hashing (frequency map / grouping)** | `Counter`, `defaultdict(list)` for grouping |
| "Subarray with product/sum < k" | **Sliding window** (shrink left when constraint violated) | Non-negative values required |
| Binary search candidate: "minimum X such that condition holds" / "maximum X such that condition holds" | **Binary search on answer** | Predicate must be monotonic: `f(x)` true → `f(x+1)` true |
| "Search in sorted/rotated array" / "find first/last position" | **Modified binary search** | Handle rotation: one half is always sorted |

### Linked List Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "Detect cycle" / "find cycle start" | **Fast & slow pointers** | Floyd: fast=2, slow=1; after meeting, reset slow to head |
| "Find middle of linked list" | **Fast & slow pointers** | Fast at end → slow at middle |
| "Nth node from end" | **Two pointers with gap n** | Advance first pointer n steps, then move both |
| "Reverse a linked list" / "reverse in k-group" / "reorder list" | **In-place LL reversal** | `prev, curr, nxt` triple |
| "Palindrome linked list" | **Fast/slow + in-place reversal** | Find mid with fast/slow, reverse second half, compare |

### Tree Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "Level-order traversal" / "left view" / "right view" / "zigzag" / "average per level" | **Tree BFS** | `deque`, process level by level (`n = len(deque)` at start of each level) |
| "Connect next right pointers" | **Tree BFS** | Level-order; link last of each level to sentinel `None` |
| "Lowest common ancestor" / "path from root to node" / "all root-to-leaf paths" | **Tree DFS** | Pre/in/post-order; carry state (path, sum) down |
| "Diameter" / "maximum path sum" / "balanced check" | **Tree DFS (post-order)** | Return value from children; update global max |
| "Binary search tree" + validate / find kth / convert to sorted array | **In-order DFS** | In-order BST traversal = sorted sequence |
| "Serialize / deserialize tree" | **Tree BFS or DFS** | BFS → easy level-order string; DFS → pre-order + null marker |

### Graph Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "Shortest path" in **unweighted** graph / grid | **Graph BFS** | BFS guarantees shortest path in unweighted graphs |
| "Shortest path" with **non-negative weights** | **Dijkstra** | Min-heap; `dist = [inf]*n; dist[src] = 0` |
| "Shortest path" with **negative weights** | **Bellman-Ford** | Relax all edges V-1 times; check V-th for negative cycle |
| "Shortest path" + **0/1 edge weights** | **0-1 BFS (deque)** | Weight 0 → push front; weight 1 → push back |
| "Shortest path between all pairs" | **Floyd-Warshall** | O(V^3); use when V ≤ 500 |
| "Number of islands" / "connected components" / "flood fill" | **Graph DFS or BFS** | DFS/BFS from each unvisited cell; mark visited in-place |
| "Connected components" + **dynamic union/split** | **Union-find (DSU)** | Path compression + union by rank |
| "Redundant connection" / "detect cycle in undirected graph" | **Union-find** | Cycle exists when unioning two already-connected nodes |
| "Course schedule" / "build order" / "task dependency" | **Topological sort** | Kahn (BFS + in-degree array) for cycle detection; DFS coloring for order |
| "Word ladder" / "min mutations" | **BFS on implicit graph** | Each word = node; differing by 1 char = edge |
| "Multi-source BFS" (distance from any 1 to all 0s) | **Multi-source BFS** | Initialize queue with ALL sources at distance 0 |
| "Minimum spanning tree" | **Kruskal or Prim** | Kruskal = sort edges + DSU; Prim = greedy min-heap from any start |
| "Word dictionary" / "starts with" / autocomplete | **Trie** | Insert → O(L); search → O(L); prefix → O(L) |

### Heap / Priority Queue Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "K-th largest / smallest" / "top K frequent" | **Heap (top-K)** | Min-heap of size k: push and pop when size > k |
| "K-th largest in a stream" | **Heap (top-K, running)** | Same min-heap; answer = heap[0] |
| "Merge k sorted lists/arrays" | **K-way merge (heap)** | Push (val, list_idx, elem_idx) into min-heap |
| "Find median in a data stream" / "balance two halves" | **Two heaps** | Max-heap for lower half, min-heap for upper half; rebalance after each insert |
| "Reorganize string" / "task scheduler with cooldown" | **Greedy + max-heap** | Always pick the most frequent available character/task |
| "Cheapest / fastest path with limited stops / budget" | **Modified Dijkstra / BFS with state** | State = (cost, node, stops_remaining) in heap |

### Recursion / Search Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "All subsets" / "power set" | **Backtracking (subsets template)** | Include/exclude each element; O(2^n * n) |
| "All permutations" | **Backtracking (permutations template)** | Swap-based or path+remaining; O(n! * n) |
| "All combinations of size k" | **Backtracking (combinations template)** | Start index moves forward; O(C(n,k) * k) |
| "N-Queens" / "Sudoku solver" / "word search in grid" | **Backtracking (constraint search)** | Prune aggressively; use sets for O(1) conflict check |
| "Generate valid parentheses" | **Backtracking** | Count open/close; prune when count invalid |

### DP Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "Number of ways to reach / make change / climb stairs" | **DP (counting)** | 1-D: `dp[i] = sum(dp[i-coins])` |
| "Maximum/minimum cost to reach destination" | **DP (optimization)** | Identify what dp[i] means; transition from subproblem |
| "0/1 choice: take or skip" (items, strings, jobs) | **DP (0/1 knapsack family)** | 2-D: dp[i][j] = with first i items, capacity j |
| "Longest common subsequence" / "edit distance" / "regex matching" | **DP on two sequences** | 2-D dp[i][j]; string alignment |
| "Longest increasing subsequence" | **DP (LIS)** or **Binary search (patience sorting)** | O(n^2) DP; O(n log n) with `bisect` |
| "Matrix path" / "unique paths" / "minimum path sum" | **DP on grid** | dp[r][c] = best way to reach (r, c) |
| "Interval DP" / "burst balloons" / "matrix chain" / "minimum cost to merge" | **Interval DP** | dp[i][j] = best for subarray [i..j]; try all split points k |
| "Palindrome partitioning / minimum cuts" | **Interval DP** | dp[i][j] = is s[i..j] a palindrome |
| "Maximum sum of non-adjacent" / "house robber" | **DP (no-adjacent)** | `dp[i] = max(dp[i-1], dp[i-2] + nums[i])` |
| "Stock buy/sell with cooldown / fee / at most k transactions" | **State-machine DP** | States: holding / not-holding / cooldown |
| "Bitmask over subsets of items" | **Bitmask DP** | dp[mask] = state with this subset; O(2^n * n) |

### Greedy / Math Signals

| Signal / phrase | Pattern | Notes |
|---|---|---|
| "Interval scheduling" / "non-overlapping intervals" / "minimum meeting rooms" | **Greedy (intervals)** | Sort by end time; greedily pick earliest-ending; rooms = heap |
| "Jump game" / "can reach end" / "minimum jumps" | **Greedy (reach-based)** | Track `max_reach` or `current_end` |
| "Gas station" / "car tour" | **Greedy (circular scan)** | Total gas >= total cost → solution exists; find start greedily |
| "Single number" / "element appearing once while others appear 3x" | **XOR / bit manipulation** | `a ^ a = 0`; XOR all → unique element |
| "Power of 2/3/4" / "number of set bits" / "reverse bits" | **Bit manipulation** | `n & (n-1)` removes lowest set bit; `bin(n).count('1')` |
| "Encode/decode with prefix codes" / "compress data" / "minimum cost to connect ropes/sticks" | **Greedy (Huffman)** | Min-heap + repeatedly merge the two smallest; O(n log n) |
| "Product of array except self" | **Prefix + suffix product** | Two passes; no division |

---

## 4. Master Decision Tree

Use this when the cue table does not immediately converge. Branch on the primary structure of the problem.

```
What is the PRIMARY data shape / constraint?
|
+-- Array or String?
|   |
|   +-- Contiguous subarray/substring?  ---> SLIDING WINDOW
|   |
|   +-- Sorted array, pair/triplet?     ---> TWO POINTERS
|   |
|   +-- Two indices sweeping same direction? --> TWO POINTERS (remove dups, partition)
|   |
|   +-- Frequency / grouping / complement?  --> HASHING
|   |
|   +-- Range sum / prefix query?       ---> PREFIX SUM (+ Fenwick if updates)
|   |
|   +-- Binary search candidate?        ---> MODIFIED BINARY SEARCH
|   |       (search on sorted array, or search on ANSWER SPACE)
|   |
|   +-- Numbers in [1..n], find missing/dup?  --> CYCLIC SORT
|   |
|   +-- Stack-based ordering (next greater)?  --> MONOTONIC STACK
|   |
|   +-- Window max/min?                 ---> MONOTONIC DEQUE
|
+-- Linked List?
|   |
|   +-- Cycle / middle / kth from end?  ---> FAST & SLOW POINTERS
|   |
|   +-- Reverse / reorder?              ---> IN-PLACE LL REVERSAL
|
+-- Tree?
|   |
|   +-- Level-by-level / width / view?  ---> TREE BFS
|   |
|   +-- Path / depth / diameter / LCA?  ---> TREE DFS (usually post-order)
|   |
|   +-- BST property (sorted order)?    ---> IN-ORDER DFS
|
+-- Graph / Grid?
|   |
|   +-- Unweighted shortest path?       ---> GRAPH BFS
|   |
|   +-- Weighted shortest path?
|   |   +-- All non-negative?           ---> DIJKSTRA
|   |   +-- Negative edges?             ---> BELLMAN-FORD
|   |   +-- 0/1 weights?                ---> 0-1 BFS (deque)
|   |   +-- All pairs, V ≤ 500?         ---> FLOYD-WARSHALL
|   |
|   +-- Connected components / islands? ---> GRAPH DFS or BFS
|   |
|   +-- Dependencies / ordering (DAG)?  ---> TOPOLOGICAL SORT
|   |
|   +-- Dynamic grouping / cycle?       ---> UNION-FIND
|   |
|   +-- String word search / prefix?    ---> TRIE
|   |
|   +-- MST (span all nodes min cost)?  ---> KRUSKAL or PRIM
|
+-- "Optimize a value" (min cost, max value, #ways)?
|   |
|   +-- Locally greedy always optimal?  ---> GREEDY
|   |   (verify with exchange argument)
|   |
|   +-- Overlapping subproblems?        ---> DYNAMIC PROGRAMMING
|       +-- 1 sequence?                 ---> 1-D DP (house robber, LIS, coin change)
|       +-- 2 sequences?                ---> 2-D DP (LCS, edit distance)
|       +-- Grid?                       ---> GRID DP
|       +-- Intervals?                  ---> INTERVAL DP
|       +-- Bitmask / subset of items?  ---> BITMASK DP
|       +-- State machine?              ---> STATE DP (stocks)
|
+-- "Generate all" (subsets / permutations / combinations)?
|   |
|   +-- Fixed-size or constrained?      ---> BACKTRACKING (combinations)
|   +-- All arrangements?               ---> BACKTRACKING (permutations)
|   +-- Constraint satisfaction?        ---> BACKTRACKING (with pruning)
|
+-- K things / K-th something?
|   |
|   +-- K-th largest/smallest / top K?  ---> HEAP (top-K, size k min-heap)
|   +-- Median (balance halves)?         ---> TWO HEAPS
|   +-- Merge K sorted sources?          ---> K-WAY MERGE (heap)
|
+-- Single number / XOR / bit trick?    ---> BIT MANIPULATION
```

---

## 5. Complexity Cheat Sheet

### Core Data Structure Operations

| Structure | Access | Search | Insert | Delete | Space | Notes |
|---|---|---|---|---|---|---|
| Array (unsorted) | O(1) | O(n) | O(n) amort. | O(n) | O(n) | O(1) amortized append (1.5–2× growth) |
| Sorted array | O(1) | O(log n) | O(n) | O(n) | O(n) | Binary search; static or rare insert |
| Hash table | O(1) avg | O(1) avg | O(1) avg | O(1) avg | O(n) | O(n) worst; load factor 0.75 triggers resize |
| Linked list | O(n) | O(n) | O(1) at head | O(1) at ptr | O(n) | Cache-unfriendly; pointer chasing |
| Stack (array) | O(1) top | — | O(1) amort. | O(1) | O(n) | LIFO |
| Queue (deque) | O(1) front | — | O(1) amort. | O(1) | O(n) | FIFO; `collections.deque` in Python |
| Binary heap | O(1) min | O(n) | O(log n) | O(log n) | O(n) | `heapq` in Python (min-heap); negate for max-heap |
| BST (balanced) | O(log n) | O(log n) | O(log n) | O(log n) | O(n) | Python `sortedcontainers.SortedList` |
| Trie | — | O(L) | O(L) | O(L) | O(A*L*n) | L = word length, A = alphabet size |
| Union-Find | — | O(α(n)) | O(α(n)) | — | O(n) | Effectively O(1); α = inverse Ackermann |
| Segment tree | — | O(log n) | O(log n) | O(log n) | O(n) | Range query + point update |
| Fenwick tree | — | O(log n) | O(log n) | O(log n) | O(n) | Prefix sum + point update; simpler than segment tree |

*Cross-link:* [`../../java/collections_internals/README.md`](../../java/collections_internals/README.md) — per-collection Java Big-O table, HashMap load factor deep dive, PriorityQueue internals.
*Concept module:* [`../complexity_analysis_and_big_o/README.md`](../complexity_analysis_and_big_o/README.md)

### Sorting Algorithms

| Algorithm | Best | Average | Worst | Space | Stable | Notes |
|---|---|---|---|---|---|---|
| Merge sort | O(n log n) | O(n log n) | O(n log n) | O(n) | Yes | Python's `sort()` uses Timsort (merge + insertion); Java Arrays.sort for objects |
| Quick sort | O(n log n) | O(n log n) | O(n^2) | O(log n) | No | O(n^2) on sorted input without random pivot; Java Arrays.sort for primitives |
| Heap sort | O(n log n) | O(n log n) | O(n log n) | O(1) | No | In-place; cache-unfriendly |
| Counting sort | O(n+k) | O(n+k) | O(n+k) | O(k) | Yes | k = value range; use when k ≤ n |
| Radix sort | O(n*d) | O(n*d) | O(n*d) | O(n+k) | Yes | d = digits; effective for integers |
| Timsort | O(n) | O(n log n) | O(n log n) | O(n) | Yes | Python/Java default; O(n) on nearly-sorted |
| Insertion sort | O(n) | O(n^2) | O(n^2) | O(1) | Yes | Best for nearly-sorted or n < ~32 (Timsort uses it for small runs) |

### Graph Algorithm Complexities

| Algorithm | Time | Space | Condition |
|---|---|---|---|
| BFS / DFS | O(V+E) | O(V) | Any graph |
| Dijkstra (min-heap) | O((V+E) log V) | O(V) | Non-negative weights |
| Bellman-Ford | O(V*E) | O(V) | Any weights; detects negative cycles |
| 0-1 BFS | O(V+E) | O(V) | Edge weights 0 or 1 only |
| Floyd-Warshall | O(V^3) | O(V^2) | All-pairs; V ≤ ~500 |
| Kruskal MST | O(E log E) | O(V) | Undirected; sort edges + DSU |
| Prim MST | O((V+E) log V) | O(V) | Undirected; min-heap |
| Topological sort (Kahn) | O(V+E) | O(V) | DAG only |
| Union-Find (path compression + rank) | O(α(n)) per op | O(n) | — |

---

## 6. Pattern Index

One row per pattern file. Click the pattern name to open the playbook. Each playbook has recognition signals, a reusable template, an annotated walkthrough, and a problem bank with LeetCode links.

| # | Pattern | Cue (one line) | Concept module | Signature problem |
|---|---|---|---|---|
| 1 | [Two Pointers](two_pointers.md) | Sorted array + pair/triplet summing to target | [arrays_strings_and_hashing](../arrays_strings_and_hashing/) | [3Sum (LC 15)](https://leetcode.com/problems/3sum/) |
| 2 | [Sliding Window](sliding_window.md) | Contiguous subarray/substring under a monotonic constraint | [arrays_strings_and_hashing](../arrays_strings_and_hashing/) | [Minimum Window Substring (LC 76)](https://leetcode.com/problems/minimum-window-substring/) |
| 3 | [Fast & Slow Pointers](fast_and_slow_pointers.md) | Linked list: cycle, middle, or nth-from-end | [linked_lists_stacks_and_queues](../linked_lists_stacks_and_queues/) | [Linked List Cycle II (LC 142)](https://leetcode.com/problems/linked-list-cycle-ii/) |
| 4 | [Prefix Sum](prefix_sum.md) | Range sum query / count subarrays with exact sum | [arrays_strings_and_hashing](../arrays_strings_and_hashing/) | [Subarray Sum Equals K (LC 560)](https://leetcode.com/problems/subarray-sum-equals-k/) |
| 5 | [Hashing Patterns](hashing_patterns.md) | Frequency / complement (unsorted pair-sum) / grouping / anagram | [arrays_strings_and_hashing](../arrays_strings_and_hashing/) | [Two Sum (LC 1)](https://leetcode.com/problems/two-sum/) |
| 6 | [Cyclic Sort](cyclic_sort.md) | Numbers in [1..n], find missing/duplicate, O(1) space | [arrays_strings_and_hashing](../arrays_strings_and_hashing/) | [Find the Duplicate Number (LC 287)](https://leetcode.com/problems/find-the-duplicate-number/) |
| 7 | [Monotonic Stack](monotonic_stack.md) | Next greater/smaller element; largest rectangle | [linked_lists_stacks_and_queues](../linked_lists_stacks_and_queues/) | [Largest Rectangle in Histogram (LC 84)](https://leetcode.com/problems/largest-rectangle-in-histogram/) |
| 8 | [In-Place LL Reversal](in_place_linked_list_reversal.md) | Reverse a linked list / reverse in k-groups / reorder | [linked_lists_stacks_and_queues](../linked_lists_stacks_and_queues/) | [Reverse Nodes in k-Group (LC 25)](https://leetcode.com/problems/reverse-nodes-in-k-group/) |
| 9 | [Merge Intervals](merge_intervals.md) | Merge / insert / overlap check on intervals | [sorting_and_searching](../sorting_and_searching/) | [Non-overlapping Intervals (LC 435)](https://leetcode.com/problems/non-overlapping-intervals/) |
| 10 | [Modified Binary Search](modified_binary_search.md) | Sorted/rotated array; binary search on the answer space | [sorting_and_searching](../sorting_and_searching/) | [Koko Eating Bananas (LC 875)](https://leetcode.com/problems/koko-eating-bananas/) |
| 11 | [Top-K Elements](top_k_elements.md) | K-th largest/smallest / top K frequent | [heaps_and_priority_queues](../heaps_and_priority_queues/) | [Top K Frequent Elements (LC 347)](https://leetcode.com/problems/top-k-frequent-elements/) |
| 12 | [K-Way Merge](k_way_merge.md) | Merge k sorted lists / find k-th in k sorted arrays | [heaps_and_priority_queues](../heaps_and_priority_queues/) | [Merge K Sorted Lists (LC 23)](https://leetcode.com/problems/merge-k-sorted-lists/) |
| 13 | [Two Heaps](two_heaps.md) | Median of a stream / balance two halves | [heaps_and_priority_queues](../heaps_and_priority_queues/) | [Find Median from Data Stream (LC 295)](https://leetcode.com/problems/find-median-from-data-stream/) |
| 14 | [Tree BFS](tree_bfs.md) | Level-order / zigzag / right view / connect level pointers | [trees_and_binary_search_trees](../trees_and_binary_search_trees/) | [Binary Tree Level Order Traversal (LC 102)](https://leetcode.com/problems/binary-tree-level-order-traversal/) |
| 15 | [Tree DFS](tree_dfs.md) | Path sum / LCA / diameter / serialize-deserialize tree | [trees_and_binary_search_trees](../trees_and_binary_search_trees/) | [Binary Tree Maximum Path Sum (LC 124)](https://leetcode.com/problems/binary-tree-maximum-path-sum/) |
| 16 | [Graph Traversal](graph_traversal.md) | BFS/DFS on grid/graph; islands; flood fill; multi-source BFS | [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/) | [Number of Islands (LC 200)](https://leetcode.com/problems/number-of-islands/) |
| 17 | [Topological Sort](topological_sort.md) | Course schedule / build order / DAG dependency | [graph_and_string_algorithms](../graph_and_string_algorithms/) | [Course Schedule II (LC 210)](https://leetcode.com/problems/course-schedule-ii/) |
| 18 | [Union-Find](union_find.md) | Dynamic connected components / redundant connection | [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/) | [Redundant Connection (LC 684)](https://leetcode.com/problems/redundant-connection/) |
| 19 | [Trie Patterns](trie_patterns.md) | Word dictionary / starts-with / autocomplete | [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/) | [Word Search II (LC 212)](https://leetcode.com/problems/word-search-ii/) |
| 20 | [Shortest Path](shortest_path.md) | Weighted graph: Dijkstra / Bellman-Ford / 0-1 BFS recognition | [graph_and_string_algorithms](../graph_and_string_algorithms/) | [Network Delay Time (LC 743)](https://leetcode.com/problems/network-delay-time/) |
| 21 | [Backtracking](backtracking.md) | Generate all subsets / permutations / combinations / constraint search | [recursion_and_problem_solving_patterns](../recursion_and_problem_solving_patterns/) | [N-Queens (LC 51)](https://leetcode.com/problems/n-queens/) |
| 22 | [Dynamic Programming](dynamic_programming.md) | Count ways / min-max cost / can-you-reach + overlapping subproblems | [dynamic_programming](../dynamic_programming/) | [Coin Change (LC 322)](https://leetcode.com/problems/coin-change/) |
| 23 | [Greedy](greedy.md) | Interval scheduling / jump game / locally optimal = globally optimal | [greedy_and_divide_and_conquer](../greedy_and_divide_and_conquer/) | [Jump Game II (LC 45)](https://leetcode.com/problems/jump-game-ii/) |
| 24 | [Bit Manipulation](bit_manipulation.md) | Single number / XOR tricks / bitmask enumeration / counting bits | [number_systems_and_bit_manipulation](../number_systems_and_bit_manipulation/) | [Single Number (LC 136)](https://leetcode.com/problems/single-number/) |

---

## 7. Pattern Anti-Signals — Common Mis-Matches

These are the traps: problems that *look* like one pattern but require a different one.

| "Looks like" | But actually | Why |
|---|---|---|
| Sliding window (subarray sum = k) | Prefix sum + hashmap | Constraint is not monotonic — can add negatives which shrink window unpredictably |
| Two pointers (unsorted, find pair sum) | Hashing | Two pointers requires sorted or monotonic; use complement hash for unsorted |
| Greedy (LCS / Edit distance) | DP | No exchange argument holds; you must explore all sub-choices |
| Greedy (0/1 knapsack) | DP | Greedy by weight/value ratio is wrong for 0/1 (works for fractional knapsack) |
| DP (interval scheduling maximisation) | Greedy (earliest deadline first) | Greedy provably optimal via exchange argument; DP would work but is overkill |
| BFS (shortest path with weights) | Dijkstra | BFS finds shortest by *hops*, not by *weight*; use Dijkstra for weighted |
| DFS (shortest path in unweighted) | BFS | DFS does not guarantee shortest path; BFS does |
| Union-Find (topological order) | Topological sort | DSU tells you whether connected, not the order |
| Binary search (unsorted array) | Linear scan or hashing | Binary search requires sorted or monotonic predicate |
| Monotonic stack (k-th largest) | Heap | Stack finds next-greater/smaller; heap answers k-th-order queries |

---

## 8. Pattern Interaction Map

Some problems require composing two patterns. Knowing which patterns combine is an L5-level signal.

```
Pattern A              + Pattern B                 = Composed Problem
--------------           -------------------         -----------------------
Sorting                + Two pointers               3Sum / 4Sum
BFS                    + Hashing (visited set)       Word Ladder
Heap (top-K)           + Hashing (freq map)          Top K Frequent Elements
In-place LL reversal   + Fast/slow pointers          Palindrome Linked List
Prefix sum             + Hashing (seen map)           Subarray Sum Equals K
Binary search (answer) + Greedy (feasibility check)  Koko Eating Bananas / Minimize Max
Tree DFS               + DP (path states)            Binary Tree Max Path Sum
Graph BFS              + DP (state = (node, k))      Cheapest Flights Within K Stops
Trie                   + DFS/backtracking            Word Search II
Union-Find             + Sorting (edge sort)          Kruskal MST / Redundant Connection
Two heaps              + Sliding window              Sliding Window Median (LC 480)
Topological sort       + DP (longest path in DAG)    Longest Increasing Path in Matrix
```

---

## 9. Interview Execution Quick Reference

### The 5-Minute Opening Ritual (before any code)

```
1. Restate the problem in your own words.
2. Write 2 examples (one normal, one edge case).
3. State constraints: n ≤ ?, sorted?, duplicates?, negative values?
4. Announce your pattern: "I think this is a [pattern] because [signal]."
5. State complexity target: "This should be O(n log n)."
```

### When You're Stuck

```
Stuck at recognition?  → Re-read problem. Look for the primary data shape.
                          Run the decision tree from §4.
Stuck at optimization? → State the brute force. Ask: "what am I recomputing?"
                          If a subarray: try sliding window / prefix sum.
                          If a tree: try passing state down / returning up.
                          If a min/max: try heap or binary search.
Stuck at implementation?→ Write the invariant in a comment. Write the base case.
                          One loop variable at a time.
Completely blank?       → Say: "Let me start with brute force and optimize."
                          Never sit silent for >90 seconds.
```

### Complexity Communication Script

> "The time complexity is O(n log n) because we sort the array once (O(n log n)) and then do a single pass with two pointers (O(n)), and O(n log n) dominates. Space is O(1) — we sort in place and use only a few pointers."

Always justify, never just state. The interviewer wants to see that you know *why*.

See [interview_execution_playbook.md](interview_execution_playbook.md) for the full L5 rubric and mock dialogue examples.

---

## 10. Cross-Links

- [case_studies/](../case_studies/) — 6 deep single-problem walkthroughs; treat these as the "worked examples" for the patterns above
- [recursion_and_problem_solving_patterns](../recursion_and_problem_solving_patterns/) — original seed of this section; covers recursion mechanics and 6 core patterns at depth
- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/) — Master theorem, amortized analysis, recurrences
- [dynamic_programming](../dynamic_programming/) — DP family deep-dive: memoisation, tabulation, 4 families
- [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/) — Union-find, trie, segment tree, Fenwick tree
- [study_plans.md](study_plans.md) — Blind 75 + NeetCode 150, pattern-mapped with links and suggested order
- [`../../java/collections_internals/README.md`](../../java/collections_internals/README.md) — HashMap, PriorityQueue, TreeMap, ArrayDeque internals
- [`../../hld/caching/README.md`](../../hld/caching/README.md) — LRU/LFU as applied data structures (heap + hashmap pattern)
- [`../../hld/rate_limiting/README.md`](../../hld/rate_limiting/README.md) — sliding window counter in production systems
- [`../../backend/osi_model_and_networking/README.md`](../../backend/osi_model_and_networking/README.md) — Dijkstra in OSPF routing, Bellman-Ford in BGP
- [`../../devops/infrastructure_as_code_terraform/README.md`](../../devops/infrastructure_as_code_terraform/README.md) — topological sort in Terraform's plan DAG
