# Top-K Elements

## Pattern Snapshot

When a problem asks for the "k largest", "k smallest", "k most frequent", or
"k closest" items out of `n`, you almost never need to fully sort all `n`
items. Maintain a **heap of size k** as you scan — it gives you the top-k in
**O(n log k)**, strictly better than the O(n log n) of sorting everything when
`k` is small.

- **One-line cue**: "k-th largest/smallest", "top K", "K most frequent",
  "K closest" -> heap of size k.
- **Typical complexity**: O(n log k) time, O(k) extra space (plus O(n) for any
  frequency map you build first).
- **Counter-intuitive core rule**: to find the **k LARGEST** elements, you
  maintain a **MIN-heap** of size k (the smallest of your current top-k sits
  at the root, ready to be evicted). Symmetrically, for the **k SMALLEST**,
  maintain a **MAX-heap** of size k.

---

## 1. Recognition Signals

**Strong signals — reach for top-k with a heap:**

- "Find the **k-th largest/smallest** element" — [LC 215](https://leetcode.com/problems/kth-largest-element-in-an-array/).
- "Return the **k most frequent**..." (elements, words, characters) —
  [LC 347](https://leetcode.com/problems/top-k-frequent-elements/),
  [LC 692](https://leetcode.com/problems/top-k-frequent-words/).
- "Find the **k closest** points/elements to X" —
  [LC 973](https://leetcode.com/problems/k-closest-points-to-origin/).
- A **stream** of numbers where you repeatedly need "the k-th largest so far"
  — [LC 703](https://leetcode.com/problems/kth-largest-element-in-a-stream/).
  The heap *persists* across calls; this is a strong streaming tell.
- `k` is given as part of the input and `k << n` — a hint that `O(n log k)`
  is the intended complexity, not `O(n log n)`.
- You need the top-k **in any order**, or only need to know *which* elements
  are in the top-k, not their fully sorted order — sorting everything would
  do unnecessary extra work.

**Anti-signals — looks like top-k but isn't:**

- You need **all** elements fully sorted — just call `sorted()`; a heap of
  size k buys you nothing if `k ~= n`.
- "Median of a stream" or "balance two halves of a dataset" —
  that's [Two Heaps](two_heaps.md), which maintains *two* heaps that split the
  data in half, not one heap of fixed size k.
- "Merge k sorted lists" or "k-th smallest element across k sorted arrays" —
  that's [K-Way Merge](k_way_merge.md): the heap holds **one element per
  list/array**, not "the best k elements seen so far."
- "Find the k-th smallest **pair sum/distance**" where the value space (not
  the element count) is what's large — often
  [modified_binary_search](modified_binary_search.md) on the answer, possibly
  combined with a heap or two-pointer counting pass.
- You need the *exact* k-th value only, with no streaming and no need for the
  other k-1 — **quickselect** (average O(n)) can beat O(n log k); see §6.

---

## 2. Mental Model & Intuition

**Why a MIN-heap for the k LARGEST elements?** The heap's root is always the
*smallest* element currently in your "top-k so far." When a new element
arrives, compare it to the root:

- If the new element is **smaller** than the root, it can't be in the top-k —
  discard it.
- If the new element is **larger** than the root, the root is no longer in the
  top-k — pop it, push the new element. The heap's new root becomes the new
  "weakest link" of the top-k.

```
Maintaining the 3 LARGEST elements of [5, 1, 2, 3, 4] with a min-heap of size 3:

push 5 -> heap = {5}                          (size 1 <= 3, just add)
push 1 -> heap = {1, 5}                       (size 2 <= 3, just add)
push 2 -> heap = {1, 2, 5}                    (size 3 <= 3, just add)
push 3 -> heap = {1, 2, 5, 3} -> evict root 1 -> {2, 3, 5}
push 4 -> heap = {2, 3, 4, 5} -> evict root 2 -> {3, 4, 5}

Final heap = {3, 4, 5}  -- exactly the 3 largest elements.
   root (3) = the SMALLEST of the top-3 = the "k-th largest overall"
```

The root of a min-heap of size k holding the k largest elements *is* the
overall k-th largest value — that's the whole trick behind LC 215 and LC 703.

---

## 3. The Template

```python
from __future__ import annotations
import heapq
from collections import Counter
from typing import List, Tuple


def top_k_largest(nums: List[int], k: int) -> List[int]:
    """Return the k largest elements (unordered). O(n log k)."""
    heap: List[int] = []
    for num in nums:
        heapq.heappush(heap, num)
        if len(heap) > k:
            heapq.heappop(heap)        # evict the smallest -- keeps top-k
    return heap


def kth_largest(nums: List[int], k: int) -> int:
    """Return the k-th largest element. The root of the size-k min-heap."""
    heap = top_k_largest(nums, k)
    return heap[0]


class KthLargest:
    """
    Streaming variant (LC 703): the heap PERSISTS across add() calls.
    """
    def __init__(self, k: int, nums: List[int]) -> None:
        self.k = k
        self.heap: List[int] = []
        for num in nums:
            self.add(num)

    def add(self, val: int) -> int:
        heapq.heappush(self.heap, val)
        if len(self.heap) > self.k:
            heapq.heappop(self.heap)
        return self.heap[0]


def top_k_frequent(nums: List[int], k: int) -> List[int]:
    """Return the k most frequent elements. O(n log k)."""
    freq = Counter(nums)
    heap: List[Tuple[int, int]] = []       # (frequency, value)
    for val, count in freq.items():
        heapq.heappush(heap, (count, val))
        if len(heap) > k:
            heapq.heappop(heap)            # evict the LEAST frequent
    return [val for _count, val in heap]


def k_closest_points(points: List[List[int]], k: int) -> List[List[int]]:
    """Return the k points closest to the origin. O(n log k)."""
    heap: List[Tuple[int, List[int]]] = []   # (-distance_sq, point)
    for x, y in points:
        dist_sq = x * x + y * y
        heapq.heappush(heap, (-dist_sq, [x, y]))
        if len(heap) > k:
            heapq.heappop(heap)              # evict the FARTHEST (largest dist)
    return [point for _neg_dist, point in heap]
```

---

## 4. Annotated Walkthrough

**Problem**: [Top K Frequent Elements (LC 347)](https://leetcode.com/problems/top-k-frequent-elements/)
— `nums = [1, 1, 1, 2, 2, 3]`, `k = 2`. Return the 2 most frequent values.

**Step 1 — build the frequency map.**

```
Counter(nums) = {1: 3, 2: 2, 3: 1}
```

**Step 2 — push `(count, val)` tuples into a min-heap, evicting when size > k.**

```
heap starts empty

push (3, 1) -> heap = [(3,1)]                       size=1 <= 2, OK

push (2, 2) -> heap = [(2,2), (3,1)]                size=2 <= 2, OK
               (heapified: root = (2,2), the smallest count so far)

push (1, 3) -> heap = [(1,3), (3,1), (2,2)]         size=3 > 2 -> evict root
               root = (1,3)  (count 1 is the smallest)
               heappop -> heap = [(2,2), (3,1)]
```

**Step 3 — read off the values.**

```
heap = [(2,2), (3,1)]  -->  values = [2, 1]
```

The two most frequent elements are `1` (count 3) and `2` (count 2) — the
element with count 1 (`3`) was correctly evicted because it was the weakest
member of the "top-2 so far" once the heap exceeded size `k = 2`.

---

## 5. Complexity

| Approach | Time | Space | When to use |
|---|---|---|---|
| Heap of size k | O(n log k) | O(k) (+ O(u) for a frequency map of `u` unique values) | `k` small relative to `n`; streaming; need a live "top-k so far" |
| Full sort | O(n log n) | O(n) | `k` close to `n`, or you need everything sorted anyway |
| Quickselect (Hoare partition) | O(n) average, O(n^2) worst | O(1) extra | Need only the k-th value (or unordered top-k as a group), one-shot, not streaming |
| Bucket sort by frequency | O(n) | O(n) | Frequencies are bounded by `n` (true for "top k frequent" problems) — see §6 |

For `k = n`, the heap approach degenerates to O(n log n) — the same as
sorting, so prefer sorting directly when `k` is large.

---

## 6. Variations & Sub-patterns

**1. K-th largest in a static array (LC 215).**
The heap-of-size-k approach (`O(n log k)`) works, but **quickselect**
(a partial Quicksort that only recurses into the side containing the k-th
index) achieves average `O(n)`. Quickselect is the better answer when the
interviewer pushes for the optimal complexity and there's no streaming
requirement — but it has `O(n^2)` worst case without randomized pivots and it
mutates the input array.

**2. Streaming k-th largest (LC 703).**
The `KthLargest` class above must keep the heap alive *between* `add()`
calls — this is the strongest signal that a heap (not quickselect, which
needs the whole array up front) is required.

**3. Bucket sort by frequency (alternative to heap for LC 347).**
Since frequencies are bounded by `n`, create `n+1` buckets where
`buckets[f]` holds all values with frequency `f`. Iterate buckets from `n`
down to `1`, collecting values until you have `k`. This is `O(n)`, strictly
better than `O(n log k)` — a good "can you do better?" follow-up answer.

**4. Tie-breaking with custom comparators (Top K Frequent Words, LC 692).**
When frequencies tie, the problem usually asks for lexicographic order. Since
`heapq` is a min-heap and you want the *least* frequent / *lexicographically
largest* word evicted first (to keep the most frequent / lexicographically
smallest), push `(count, word)` for a min-heap eviction of "smallest count,
then largest word" — or push `(-count, word)` into a min-heap and take the
first `k` after popping, depending on which end you're filtering from. Get
this right by writing out the desired final order first, then deriving the
eviction rule.

**5. K closest points / K-th smallest pair sum.**
[K Closest Points to Origin (LC 973)](https://leetcode.com/problems/k-closest-points-to-origin/)
uses a max-heap of size k (negate distances, as in `k_closest_points` above).
[Find K Pairs with Smallest Sums (LC 373)](https://leetcode.com/problems/find-k-pairs-with-smallest-sums/)
is structurally a [K-Way Merge](k_way_merge.md) problem — the heap holds
*candidate pairs*, one "frontier" per row of the implicit sum matrix.

**6. K-th smallest in a sorted matrix (LC 378).**
Two valid approaches: (a) a min-heap seeded with the first element of each
row (a K-Way Merge-style heap, `O(k log n)`), or (b) binary search on the
*value range* `[matrix[0][0], matrix[n-1][n-1]]`, counting how many elements
are `<= mid` in each row via binary search
([modified_binary_search](modified_binary_search.md), `O(n log n log(range))`).
The binary-search approach is asymptotically better for large `k`.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Kth Largest Element in an Array (LC 215)](https://leetcode.com/problems/kth-largest-element-in-an-array/) | Medium | Heap of size k or quickselect | The canonical signature problem |
| [Kth Largest Element in a Stream (LC 703)](https://leetcode.com/problems/kth-largest-element-in-a-stream/) | Easy | Persistent min-heap | Heap state must survive across `add()` calls |
| [Top K Frequent Elements (LC 347)](https://leetcode.com/problems/top-k-frequent-elements/) | Medium | Frequency map + heap | Bucket sort gets O(n) |
| [K Closest Points to Origin (LC 973)](https://leetcode.com/problems/k-closest-points-to-origin/) | Medium | Max-heap of size k | Distance metric, evict the farthest |
| [Top K Frequent Words (LC 692)](https://leetcode.com/problems/top-k-frequent-words/) | Medium | Frequency + lexicographic tie-break | Custom comparator on tuples |
| [Sort Characters By Frequency (LC 451)](https://leetcode.com/problems/sort-characters-by-frequency/) | Medium | Full sort by frequency | `k = n` here, so just sort |
| [Kth Smallest Element in a Sorted Matrix (LC 378)](https://leetcode.com/problems/kth-smallest-element-in-a-sorted-matrix/) | Medium | Heap or binary search on answer | Compare both approaches |
| [Find K Pairs with Smallest Sums (LC 373)](https://leetcode.com/problems/find-k-pairs-with-smallest-sums/) | Medium | K-way merge with a heap | "k pairs" = k-way merge over an implicit matrix |
| [Reorganize String (LC 767)](https://leetcode.com/problems/reorganize-string/) | Medium | Max-heap greedy | Always place the most frequent remaining char |
| [Task Scheduler (LC 621)](https://leetcode.com/problems/task-scheduler/) | Medium | Max-heap + cooldown queue | Heap models "most remaining work first" |
| [Last Stone Weight (LC 1046)](https://leetcode.com/problems/last-stone-weight/) | Easy | Max-heap simulation | Repeatedly pop the two largest |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: using a max-heap (via negation) to track the "k largest" elements
inverts the eviction rule.**

```python
# BROKEN -- tries to track the 3 largest elements with a max-heap
def top_k_largest_broken(nums: List[int], k: int) -> List[int]:
    heap: List[int] = []
    for num in nums:
        heapq.heappush(heap, -num)        # max-heap via negation
        if len(heap) > k:
            heapq.heappop(heap)           # pops the SMALLEST of -heap...
    return [-x for x in heap]
```

Trace on `nums = [5, 1, 2, 3, 4]`, `k = 3` (correct answer should be `{3,4,5}`):

```
push -5 -> heap = [-5]
push -1 -> heap = [-5, -1]
push -2 -> heap = [-5, -1, -2]                 size=3, OK
push -3 -> heap = [-5, -1, -2, -3] -> pop smallest
           smallest of [-5,-1,-2,-3] is -5  (represents 5!)
           heap becomes [-3, -1, -2]
push -4 -> heap = [-3, -1, -2, -4] -> pop smallest
           smallest is -4  (represents 4!)
           heap becomes [-3, -1, -2]

result = [-x for x in heap] = [3, 1, 2]
```

`heapq.heappop` on a min-heap of *negated* values pops the **most negative**
entry — which corresponds to the **largest original number**. So every time
the heap overflows, this code evicts the biggest element seen so far instead
of the smallest, ending up with `{1, 2, 3}` (the bottom-3) instead of `{3, 4,
5}` (the top-3) — a silent, completely wrong answer with no exception raised.

```python
# FIX -- a MIN-heap of raw values evicts the smallest, correctly keeping the top-k
def top_k_largest(nums: List[int], k: int) -> List[int]:
    heap: List[int] = []
    for num in nums:
        heapq.heappush(heap, num)
        if len(heap) > k:
            heapq.heappop(heap)           # evicts the smallest -- correct
    return heap
```

Re-tracing with the fix gives `heap = [3, 5, 4]` -> `{3, 4, 5}`, the correct
top-3. **Rule of thumb**: "k LARGEST" -> min-heap (evict small). "k SMALLEST"
-> max-heap, i.e., negate values (evict large). Negation flips which end of
the heap you're discarding from — get this backwards and the result is wrong
in a way that won't crash, only silently return the wrong set.

---

## 9. Related Patterns & When to Switch

- **[Two Heaps](two_heaps.md)** — when you need the median or to balance a
  dataset into two halves *as it streams in*, not just "the top k." Two heaps
  is a different shape: both halves grow with the input, not capped at a
  fixed `k`.
- **[K-Way Merge](k_way_merge.md)** — when the heap should hold "one
  candidate per source" (one per list, one per row) rather than "the best k
  overall." If the problem involves multiple *already-sorted* sequences,
  it's k-way merge even if the word "heap" feels similar.
- **[Modified Binary Search](modified_binary_search.md)** — when `k` is large
  (close to `n`) or the value range is small/discrete, binary search on the
  answer can beat `O(n log k)` (e.g., LC 378's matrix variant).
- **Quickselect** (covered in [sorting_and_searching](../sorting_and_searching/))
  — for a one-shot (non-streaming) "k-th value only" query on a static array,
  average `O(n)` beats `O(n log k)`.

---

## 10. Cross-links

- Concept module: [heaps_and_priority_queues](../heaps_and_priority_queues/)
  — binary heap internals, `heapify` in O(n), heap invariant proofs.
- Applied: [java/collections_internals](../../java/collections_internals/) —
  `PriorityQueue` is a binary heap array under the hood; note Java's
  `PriorityQueue` is a *max-heap* by default only with a reversed comparator
  (default is min-heap, like Python's `heapq`).
- Applied: [hld/caching](../../hld/caching/) — LFU cache eviction is a live
  "top-k by frequency" problem at system scale, often implemented with a
  frequency-bucketed doubly linked list instead of a heap for O(1) operations.
- Master recognition engine: [dsa_patterns/README.md](README.md).
- Sibling patterns: [k_way_merge.md](k_way_merge.md), [two_heaps.md](two_heaps.md).

---

## 11. Interview Q&A

**Why does finding the "k largest" elements use a MIN-heap — isn't that backwards?**
The heap doesn't represent "the answer" directly; it represents your *current
best guess* at the top-k, and its root is the **weakest member of that
guess** — the element most likely to be displaced by a better candidate. A
min-heap keeps that weakest member at the root in O(1), so each new element
only needs one comparison (`new > heap[0]`) to decide whether it belongs.
Using a max-heap would put the *strongest* member at the root, which tells
you nothing about whether a new element should be added.

**How does the heap-of-size-k approach compare to sorting and to quickselect?**
Heap: `O(n log k)` time, `O(k)` space, works on streams, gives you all k
elements (unordered). Full sort: `O(n log n)` time, `O(n)` space (or `O(1)`
in-place), gives total order — better when `k` is close to `n` or you need
the order anyway. Quickselect: `O(n)` average / `O(n^2)` worst case, `O(1)`
extra space, but requires the full array up front (no streaming) and mutates
it in place — best when you need only the k-th value (or an unordered top-k
group) from a static array.

**When would an interviewer expect quickselect instead of a heap?**
When the problem is explicitly "find the k-th largest element" (singular,
LC 215) on a static array with a follow-up "can you do better than `O(n log
n)`?" — quickselect's average `O(n)` is the textbook answer. If the problem
mentions a stream, repeated queries, or "return the k elements" (not just the
k-th), the heap is usually the cleaner and intended answer because quickselect
doesn't naturally support incremental updates.

**Top K Frequent Words requires lexicographic tie-breaking — how do you encode that in a heap comparison?**
Decide the final iteration order first: you want the result sorted by
(frequency descending, word ascending). If using a min-heap of size k that
*evicts* the worst candidate, "worst" means (lowest frequency, then
lexicographically *largest* word) — so push `(count, word)` won't directly
work because Python compares tuples lexicographically and you'd need to
invert the word ordering for ties. A common trick: push
`(count, ReverseStr(word))` with a small wrapper class implementing reversed
`__lt__`, or push `(-count, word)` into a min-heap of *all* items and just
take the first `k` (no eviction) — simpler when `k` is close to `n` anyway.

**Why does the streaming `KthLargest` class need to keep the heap between calls?**
Because each `add(val)` must answer "what is the k-th largest *over all
values seen so far*," not just over the latest batch. Recomputing from
scratch on every call would be `O(n log n)` per call. By keeping a persistent
min-heap of size k, each `add` is `O(log k)`: push the new value, and if the
heap now has more than k elements, pop the smallest — the root is always the
current k-th largest.

**What's the bucket-sort alternative to a heap for Top K Frequent Elements, and when is it strictly better?**
Since element frequencies are bounded by `n` (an element can appear at most
`n` times), allocate `n + 1` buckets indexed by frequency. Place each unique
value into `buckets[frequency]`. Then iterate `buckets` from index `n` down
to `1`, collecting values until you have `k`. This is `O(n)` total — strictly
better than `O(n log k)` — and is a strong "optimize further" answer once the
heap solution is established.

**K Closest Points to Origin — why a max-heap of size k instead of a min-heap of all points?**
A min-heap of *all* `n` points sorted by distance and popping `k` times is
`O(n + k log n)` — fine, but it processes and heapifies all `n` points
upfront. A max-heap of size k mirrors the "k largest" pattern but inverted:
keep the k *closest* (smallest distances) by evicting the *farthest*
(largest distance) when the heap exceeds size k. This is `O(n log k)`,
better when `k << n`.

**What's the space complexity tradeoff — O(k) heap vs O(n) frequency map?**
For "top-k frequent" problems, you need `O(u)` space for the frequency map
(`u` = number of unique elements, `u <= n`) regardless of approach — that's
unavoidable since you must count occurrences. The heap itself only adds
`O(k)` on top of that. The "O(k) space" claim usually refers to the
*additional* structure beyond any necessary preprocessing, not the total
memory footprint.

**How does the K-th Smallest in a Sorted Matrix heap approach work, and how does it compare to binary search on the answer?**
Seed a min-heap with the first element of each row (or just row 0's first
`min(k, n)` elements with pointers), each tagged with its row and column.
Pop the smallest `k` times, each time pushing that element's right neighbor
in the same row. After `k` pops, the last popped value is the answer —
`O(k log n)`. Binary search on the answer instead searches the *value range*
`[matrix[0][0], matrix[n-1][n-1]]`, counting elements `<= mid` via a per-row
binary search or staircase walk — `O(n log(max-min))` or
`O(n log n log(range))`. For large `k` (close to `n^2`), binary search on the
answer is usually faster since its cost doesn't scale with `k`.

**What happens if `k == n` or `k > n`?**
If `k == n`, the heap-of-size-k approach degenerates to holding the entire
input — equivalent to sorting, so `O(n log n)`; prefer sorting directly in
that case. If `k > n` (more requested than available), most problems either
guarantee `1 <= k <= n` as a constraint, or expect you to clamp `k = min(k,
n)` and return everything — always check the constraints section for this
edge case before coding.

**Python's `heapq` only provides a min-heap — how do tuple comparisons behave when the second element is a non-comparable object (e.g., a list of points)?**
Python compares tuples element-by-element, falling through to the second
element only if the first elements are equal. If two points have the *exact
same* `(-dist_sq)` value and the second tuple element is a `list` (as in
`k_closest_points`), Python *will* attempt to compare the lists, which works
for lists of comparable types (ints) but raises `TypeError` for
non-comparable objects (e.g., custom class instances without `__lt__`). The
fix is to break ties explicitly — push a third tiebreaker element (e.g., an
insertion index) into the tuple, e.g., `(-dist_sq, i, point)`, so the heap
never needs to compare `point` objects directly.
