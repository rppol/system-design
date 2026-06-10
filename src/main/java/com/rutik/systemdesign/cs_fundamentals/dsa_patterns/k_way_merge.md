# K-Way Merge

## Pattern Snapshot

When you have **k already-sorted sequences** (lists, arrays, or rows of an
implicit matrix) and need to produce a globally sorted result — or just find
the k-th smallest element across all of them — maintain a **min-heap holding
one "frontier" element per sequence**. Repeatedly pop the global minimum,
emit it, and push the next element from the sequence it came from.

- **One-line cue**: "merge k sorted ...", "k-th smallest across k sorted
  lists/arrays", or a problem that can be reframed as several independent
  sorted streams.
- **Typical complexity**: O(N log k), where `N` = total elements across all
  sequences and `k` = number of sequences. The heap never holds more than `k`
  elements.

---

## 1. Recognition Signals

**Strong signals — reach for k-way merge:**

- The problem literally says **"merge k sorted lists / arrays"** —
  [Merge k Sorted Lists (LC 23)](https://leetcode.com/problems/merge-k-sorted-lists/).
- "Find the **k-th smallest** element across **multiple sorted** arrays/lists/rows."
- A **matrix where every row (and column) is sorted** — each row is a sorted
  sequence, so the matrix is `k` sorted lists where `k = number of rows` —
  [Kth Smallest Element in a Sorted Matrix (LC 378)](https://leetcode.com/problems/kth-smallest-element-in-a-sorted-matrix/).
- "Find the **smallest range** that includes at least one element from each
  of k lists" — [LC 632](https://leetcode.com/problems/smallest-range-covering-elements-from-k-lists/).
  The k-way merge gives you a sliding "current element from each list" frontier
  for free.
- A sequence can be **decomposed into multiple sorted streams** even if not
  stated explicitly — e.g., "ugly numbers" (multiples of 2, 3, 5) are three
  interleaved sorted streams ([LC 264](https://leetcode.com/problems/ugly-number-ii/),
  [LC 313](https://leetcode.com/problems/super-ugly-number/)).
- The **merge step of merge sort** itself, applied to `k` runs instead of 2 —
  this is literally how **external sort** merges sorted chunks larger than
  memory.

**Anti-signals — looks like k-way merge but isn't:**

- **Only one or two sorted sequences** — for `k = 2`, a plain two-pointer
  merge ([Merge Two Sorted Lists (LC 21)](https://leetcode.com/problems/merge-two-sorted-lists/),
  [Merge Sorted Array (LC 88)](https://leetcode.com/problems/merge-sorted-array/))
  is `O(n)` with `O(1)` extra space — a heap of size 2 only adds `log 2 = 1`
  overhead for nothing. Reach for [two_pointers](two_pointers.md) instead.
- **"Top k from a single unsorted collection"** — that's
  [top_k_elements](top_k_elements.md): one heap of size k holding "the best k
  seen so far," not "one element per source."
- **"Median of a stream"** — that's [two_heaps](two_heaps.md): two heaps that
  split *one* stream in half, not k heaps/sources.
- The sequences are **not sorted** and can't cheaply be made into sorted
  streams — k-way merge's entire benefit comes from each source being sorted
  so its "next smallest" is always at the front.

---

## 2. Mental Model & Intuition

Picture `k` sorted lists laid out side by side, each with a pointer at its
current ("frontier") element. The heap holds exactly these `k` frontier
values. The smallest value across *all* frontiers must be the smallest
remaining value overall — pop it, emit it, and advance that one list's
pointer to reveal its next frontier element.

```
List A: [1, 4, 5]      pointer -> 1
List B: [1, 3, 4]      pointer -> 1
List C: [2, 6]         pointer -> 2

heap = {1(A), 1(B), 2(C)}     <- one entry per list

pop 1(A) -> emit 1 -> advance A's pointer to 4
heap = {1(B), 2(C), 4(A)}

pop 1(B) -> emit 1 -> advance B's pointer to 3
heap = {2(C), 3(B), 4(A)}

pop 2(C) -> emit 2 -> advance C's pointer to 6
heap = {3(B), 4(A), 6(C)}

... continues until all lists are exhausted ...

merged output so far: [1, 1, 2, ...]
```

At every step, the heap contains **at most k elements** — one per list still
having unconsumed elements — which is why each pop/push is `O(log k)`
regardless of how large `N` (total elements) is.

---

## 3. The Template

```python
from __future__ import annotations
import heapq
from typing import List, Optional


class ListNode:
    def __init__(self, val: int = 0, next: Optional["ListNode"] = None) -> None:
        self.val = val
        self.next = next


def merge_k_sorted_lists(lists: List[Optional[ListNode]]) -> Optional[ListNode]:
    """Merge k sorted linked lists into one sorted list. O(N log k)."""
    heap: List[tuple] = []
    for i, node in enumerate(lists):
        if node:
            # tie-break on `i` so we never compare ListNode objects directly
            heapq.heappush(heap, (node.val, i, node))

    dummy = ListNode()
    tail = dummy
    while heap:
        _val, i, node = heapq.heappop(heap)
        tail.next = node
        tail = tail.next
        if node.next:
            heapq.heappush(heap, (node.next.val, i, node.next))

    return dummy.next


def merge_k_sorted_arrays(arrays: List[List[int]]) -> List[int]:
    """Merge k sorted arrays into one sorted array. O(N log k)."""
    heap: List[tuple] = []
    for i, arr in enumerate(arrays):
        if arr:
            heapq.heappush(heap, (arr[0], i, 0))   # (value, array_index, element_index)

    result: List[int] = []
    while heap:
        val, i, j = heapq.heappop(heap)
        result.append(val)
        if j + 1 < len(arrays[i]):
            heapq.heappush(heap, (arrays[i][j + 1], i, j + 1))

    return result


def kth_smallest_in_sorted_matrix(matrix: List[List[int]], k: int) -> int:
    """K-th smallest element in a row- and column-sorted matrix. O(k log n)."""
    n = len(matrix)
    heap: List[tuple] = [(matrix[i][0], i, 0) for i in range(min(k, n))]
    heapq.heapify(heap)

    val = -1
    for _ in range(k):
        val, r, c = heapq.heappop(heap)
        if c + 1 < n:
            heapq.heappush(heap, (matrix[r][c + 1], r, c + 1))
    return val
```

---

## 4. Annotated Walkthrough

**Problem**: [Merge k Sorted Lists (LC 23)](https://leetcode.com/problems/merge-k-sorted-lists/)
— merge `[1,4,5]`, `[1,3,4]`, `[2,6]` (as linked lists) into one sorted list.

**Step 1 — seed the heap with each list's head, tagged with a list index.**

```
heap = [(1, 0, A0), (1, 1, B0), (2, 2, C0)]
        value=1,list=A   value=1,list=B   value=2,list=C
```

The `list_index` (`0`, `1`, `2`) breaks ties when values are equal — without
it, Python would try to compare `ListNode` objects directly (see §8).

**Step 2 — repeatedly pop the minimum, emit it, push that list's next node.**

```
pop (1,0,A0) -> output=[1]            push A's next: (4,0,A1)
   heap = {(1,1,B0), (2,2,C0), (4,0,A1)}

pop (1,1,B0) -> output=[1,1]          push B's next: (3,1,B1)
   heap = {(2,2,C0), (3,1,B1), (4,0,A1)}

pop (2,2,C0) -> output=[1,1,2]        push C's next: (6,2,C1)
   heap = {(3,1,B1), (4,0,A1), (6,2,C1)}

pop (3,1,B1) -> output=[1,1,2,3]      push B's next: (4,1,B2)
   heap = {(4,0,A1), (4,1,B2), (6,2,C1)}

pop (4,0,A1) -> output=[1,1,2,3,4]    push A's next: (5,0,A2)
   heap = {(4,1,B2), (5,0,A2), (6,2,C1)}

pop (4,1,B2) -> output=[1,1,2,3,4,4]  B exhausted, nothing to push
   heap = {(5,0,A2), (6,2,C1)}

pop (5,0,A2) -> output=[...,5]        A exhausted
   heap = {(6,2,C1)}

pop (6,2,C1) -> output=[...,6]        C exhausted
   heap = {}  -> done
```

**Final result**: `[1, 1, 2, 3, 4, 4, 5, 6]` — the correct merge of all three
lists. Total elements `N = 8`, number of lists `k = 3`: `O(N log k) = O(8 *
log 3)`, vs. concatenating everything and sorting at `O(N log N) = O(8 * log
8)` — the heap approach saves work as `k` shrinks relative to `N`.

---

## 5. Complexity

| Operation | Time | Space | Why |
|---|---|---|---|
| Seed the heap | O(k log k) (or O(k) with `heapify`) | O(k) | One entry per source sequence |
| Each pop + push | O(log k) | — | Heap never exceeds size k |
| Total for N elements | O(N log k) | O(k) extra (+ O(N) for output) | N pop/push pairs, each O(log k) |
| Concatenate + sort (alternative) | O(N log N) | O(N) | Ignores that each source is already sorted |

`O(N log k)` beats `O(N log N)` whenever `k < N` — which is always true when
there's more than one element per list. The gap widens as `k` shrinks
relative to `N` (e.g., merging 3 lists of 1000 elements each: `log 3 ~= 1.6`
vs `log 3000 ~= 11.5`).

---

## 6. Variations & Sub-patterns

**1. K = 2 special case — use two pointers, not a heap.**
[Merge Two Sorted Lists (LC 21)](https://leetcode.com/problems/merge-two-sorted-lists/)
and [Merge Sorted Array (LC 88)](https://leetcode.com/problems/merge-sorted-array/)
are `k=2` merges. A heap of size 2 adds unnecessary `O(log 2)` overhead and
allocation; a direct two-pointer comparison is simpler, faster in practice,
and (for LC 88) allows in-place merging from the back of the array.

**2. K-th smallest without fully merging.**
[Kth Smallest Element in a Sorted Matrix (LC 378)](https://leetcode.com/problems/kth-smallest-element-in-a-sorted-matrix/)
doesn't need the full merged sequence — only pop `k` times and return the
last popped value (`kth_smallest_in_sorted_matrix` above), giving `O(k log
n)` instead of `O(n^2 log n)` for a full merge.

**3. Smallest range covering all k lists (LC 632).**
Seed the heap with the first element of each list, **and separately track the
running maximum** of the current frontier. At each step, the current range is
`[heap_min, running_max]`; record it if it's the smallest range seen so far,
then pop the minimum, advance that list, update the running max, and push the
new frontier element. Stop when any list is exhausted (you can no longer have
"at least one from each list").

**4. Decomposable single sequences (Ugly Number II, Super Ugly Number).**
"The n-th ugly number" (only prime factors 2, 3, 5) can be generated by
maintaining **three pointers** into the *result sequence itself*: the next
candidate is `min(result[p2]*2, result[p3]*3, result[p5]*5)`. This is k-way
merge with `k = 3` (or `k = len(primes)` for Super Ugly Number) **streams
generated on the fly** rather than given as input arrays.

**5. External merge sort.**
When data is too large to fit in memory, sort it in chunks that *do* fit,
write each sorted chunk to disk, then k-way merge the chunks during a single
sequential read pass — this is exactly the template above with `k` = number
of chunks, and is the classic algorithm behind large-scale sorts (Hadoop,
database sort-merge joins on huge tables).

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Merge Two Sorted Lists (LC 21)](https://leetcode.com/problems/merge-two-sorted-lists/) | Easy | k=2 special case | Two pointers beat a heap here |
| [Merge Sorted Array (LC 88)](https://leetcode.com/problems/merge-sorted-array/) | Easy | k=2, in-place from the back | Avoids overwriting unread elements |
| [Merge k Sorted Lists (LC 23)](https://leetcode.com/problems/merge-k-sorted-lists/) | Hard | Canonical signature problem | Heap of (val, list_index, node) |
| [Kth Smallest Element in a Sorted Matrix (LC 378)](https://leetcode.com/problems/kth-smallest-element-in-a-sorted-matrix/) | Medium | k-th smallest, partial merge | Pop k times, don't merge fully |
| [Find K Pairs with Smallest Sums (LC 373)](https://leetcode.com/problems/find-k-pairs-with-smallest-sums/) | Medium | Implicit sum-matrix rows | Each row of `nums1[i]+nums2[j]` is sorted |
| [Smallest Range Covering Elements from K Lists (LC 632)](https://leetcode.com/problems/smallest-range-covering-elements-from-k-lists/) | Hard | Heap + running max | Track max alongside heap min |
| [Ugly Number II (LC 264)](https://leetcode.com/problems/ugly-number-ii/) | Medium | 3 implicit streams (x2, x3, x5) | Streams generated from the result itself |
| [Super Ugly Number (LC 313)](https://leetcode.com/problems/super-ugly-number/) | Medium | k implicit streams (k primes) | Generalizes Ugly Number II |
| [Sort List (LC 148)](https://leetcode.com/problems/sort-list/) | Medium | Merge sort on a linked list | The merge step is a k=2 k-way merge |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: pushing `(value, node)` tuples without a tie-breaker raises
`TypeError` when values are equal.**

```python
# BROKEN -- ties on node.val fall through to comparing ListNode objects
def merge_k_sorted_lists_broken(lists: List[Optional[ListNode]]) -> Optional[ListNode]:
    heap: List[tuple] = []
    for node in lists:
        if node:
            heapq.heappush(heap, (node.val, node))   # BUG: no tiebreaker

    dummy = ListNode()
    tail = dummy
    while heap:
        _val, node = heapq.heappop(heap)
        tail.next = node
        tail = tail.next
        if node.next:
            heapq.heappush(heap, (node.next.val, node.next))
    return dummy.next
```

Trace with lists `[1,4,5]`, `[1,3,4]`, `[2,6]` — both list heads have
`val == 1`:

```
heapq.heappush(heap, (1, nodeA))   # heap = [(1, nodeA)]
heapq.heappush(heap, (1, nodeB))   # comparing (1, nodeB) vs (1, nodeA):
                                    #   first elements equal (1 == 1)
                                    #   -> falls through to nodeB < nodeA
                                    #   -> TypeError: '<' not supported
                                    #      between instances of 'ListNode'
                                    #      and 'ListNode'
```

This crashes the **moment two values tie** — which is common with real input
data (duplicate values across lists are the norm, not the exception).

```python
# FIX -- add a unique tiebreaker (the source list's index) as the 2nd element
def merge_k_sorted_lists(lists: List[Optional[ListNode]]) -> Optional[ListNode]:
    heap: List[tuple] = []
    for i, node in enumerate(lists):
        if node:
            heapq.heappush(heap, (node.val, i, node))   # FIX: i is always unique

    dummy = ListNode()
    tail = dummy
    while heap:
        _val, i, node = heapq.heappop(heap)
        tail.next = node
        tail = tail.next
        if node.next:
            heapq.heappush(heap, (node.next.val, i, node.next))
    return dummy.next
```

Now `(1, 0, nodeA)` vs `(1, 1, nodeB)`: first elements tie (`1 == 1`), Python
compares the second elements (`0 < 1`) and never touches the `ListNode`
objects. **Any time you push application objects into a heap alongside a
sort key, include a tiebreaker** (an index or counter) as the second tuple
element — this is one of the most common runtime crashes in heap-based
solutions, and it only manifests on inputs with duplicate keys, so it can
pass small hand-written test cases and fail on real data.

---

## 9. Related Patterns & When to Switch

- **[Top-K Elements](top_k_elements.md)** — a single heap of *fixed size k*
  holding "the best k seen so far" from one stream, vs. k-way merge's heap of
  *size k* holding "one frontier element per source." Different invariant,
  same data structure.
- **[Two Pointers](two_pointers.md)** — for `k = 2`, always prefer two
  pointers over a heap; the heap's `O(log k)` overhead is pure waste when
  `k` is a small constant.
- **[Two Heaps](two_heaps.md)** — for "median of a stream" or "balance two
  halves," not "merge k sources."
- **[Modified Binary Search](modified_binary_search.md)** — for "k-th
  smallest in a sorted matrix," binary search on the value range is an
  alternative to the partial-merge heap approach, and asymptotically better
  for large `k` (see §6.2).

---

## 10. Cross-links

- Concept module: [heaps_and_priority_queues](../heaps_and_priority_queues/)
  — heap mechanics, `heapify`, why `O(log k)` per operation.
- Concept module: [sorting_and_searching](../sorting_and_searching/) — merge
  sort's merge step generalizes directly to k-way merge; external sort.
- Applied: [java/collections_internals](../../java/collections_internals/) —
  `PriorityQueue` as the heap implementation; `Comparator` for tie-breaking
  without raw tuples.
- Master recognition engine: [dsa_patterns/README.md](README.md).
- Sibling patterns: [top_k_elements.md](top_k_elements.md),
  [two_heaps.md](two_heaps.md), [two_pointers.md](two_pointers.md).

---

## 11. Interview Q&A

**Why a heap of size k instead of just concatenating all lists and sorting?**
Concatenating and sorting is `O(N log N)` and throws away the fact that each
input is *already sorted*. A heap of size k exploits that: at any moment, the
global minimum among all unconsumed elements must be the minimum of the `k`
"frontier" elements (one per list) — so you never need to compare against
elements deeper in any list. This gives `O(N log k)`, which is asymptotically
better whenever `k < N`.

**Why does pushing `(node.val, node)` into a heap crash on duplicate values, and how do you fix it?**
`heapq` compares tuples element-by-element; if the first elements
(`node.val`) are equal, it falls through to comparing the second elements —
here, `ListNode` objects, which have no `__lt__` defined, raising
`TypeError`. The fix is to insert a unique, always-comparable tiebreaker
(commonly the source list's index, or a monotonically increasing counter) as
the *second* tuple element: `(node.val, i, node)`. Ties on `node.val` then
resolve via `i` (always distinct), and Python never needs to compare the
`ListNode`s themselves.

**For k = 2, why is a two-pointer merge better than a 2-element heap?**
A 2-element heap still pays `O(log 2) = O(1)` overhead per operation plus the
constant-factor cost of heap push/pop machinery (list operations,
sift-up/down). A direct `if a[i] <= b[j]` comparison is a single branch with
no auxiliary data structure — strictly cheaper in practice, and for
[Merge Sorted Array (LC 88)](https://leetcode.com/problems/merge-sorted-array/)
it also enables an in-place merge (writing from the back) that a heap-based
approach can't easily replicate.

**Smallest Range Covering Elements from K Lists — how do you track the range without re-scanning the heap?**
Maintain the heap minimum (via `heap[0]`) as the range's lower bound, and
separately maintain a variable `current_max` updated incrementally every time
you push a new frontier element (`current_max = max(current_max, new_val)`).
The candidate range at each step is `[heap[0][0], current_max]` — both
endpoints available in O(1) without scanning. Compare this candidate's width
to the best-so-far and update if smaller.

**How is "the n-th ugly number" a k-way merge problem when there's only one input array?**
There is no input array — the "lists" are *generated on the fly* as multiples
of the result sequence itself. Maintain three pointers `p2, p3, p5` into the
growing `result` array; the next ugly number is
`min(result[p2]*2, result[p3]*3, result[p5]*5)`. Whichever pointer(s)
produced that minimum advance by one. This is k-way merge with `k=3` streams
where each stream's "next element" is computed lazily instead of read from a
pre-built array.

**Kth Smallest in a Sorted Matrix — heap-based partial merge vs. binary search on the answer: when is each better?**
The heap approach (`kth_smallest_in_sorted_matrix`) is `O(k log n)` — great
when `k` is small relative to `n^2`. Binary search on the value range is
`O(n log(max-min))` (using a per-row binary search to count elements `<=
mid`) — its cost is *independent of k*, so it wins when `k` is large (close
to `n^2`). If asked "what if k is `n^2 / 2`?", binary search on the answer is
the better follow-up.

**What if one of the k input lists/arrays is empty?**
Skip it during heap seeding — only push frontier elements for non-empty
sources (`if node:` / `if arr:` in the templates above). An empty source
contributes nothing to the heap and is naturally never selected; no special
casing is needed beyond the initial guard.

**Why is the heap's space complexity O(k) and not O(N)?**
The heap holds exactly one element per *currently active* source — never
more than `k` regardless of how large each individual list is. Each pop
immediately triggers at most one push (the popped source's next element), so
the heap size is invariant at `<= k` throughout. The `O(N)` cost is for the
*output*, not the heap.

**How does this generalize to external (disk-based) sorting?**
Split data too large for memory into chunks that fit, sort each chunk
in-memory (`O(chunk log chunk)`), and write each sorted chunk to disk as a
"run." Then open all `k` runs simultaneously and k-way merge them with a
single sequential pass — `O(N log k)` comparisons, `O(N)` sequential disk I/O
(no random access). This is the standard algorithm behind large-scale
external sorts and the merge phase of MapReduce-style shuffles.

**Could you parallelize a k-way merge across machines?**
Partition the value range into disjoint buckets (e.g., by a hash or range
partitioner), have each machine independently sort the elements that fall
into its bucket, and concatenate the per-bucket sorted results in bucket
order — no merge step needed *across* buckets because the buckets themselves
partition the value space. Within a single machine handling multiple sorted
runs, the k-way merge heap still applies. This bucket-then-sort approach is
how distributed sorts (e.g., Spark's `sortByKey`) avoid a single-machine
k-way merge bottleneck.

**Is a heap always required, or can k-way merge be done with k pointers and a linear scan?**
For small, fixed `k` (say `k <= 4`), a linear scan over `k` pointers to find
the minimum each step is `O(N * k)` — simpler code, and for small constant
`k`, `O(N * k)` and `O(N log k)` are both effectively `O(N)`. The heap only
provides an asymptotic win once `k` is large enough that `log k` is
meaningfully cheaper than a linear scan over `k` candidates (roughly `k > 8`
in practice).
