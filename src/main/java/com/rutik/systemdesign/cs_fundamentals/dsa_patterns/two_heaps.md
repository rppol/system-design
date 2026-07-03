# Two Heaps

## Pattern Snapshot

When you need to track the **median of a stream**, or more generally **split a
growing dataset into two balanced halves** and repeatedly query a boundary
value between them, maintain **two heaps**: a **max-heap for the smaller
("left") half** and a **min-heap for the larger ("right") half**, kept within
one element of each other in size. The boundary between them — the tops of
the two heaps — is always your answer.

- **One-line cue**: "median of a data stream", "find the median as elements
  arrive", "balance two halves", or "running middle value."
- **Typical complexity**: O(log n) per insertion, O(1) per median query.

---

## 1. Recognition Signals

**Strong signals — reach for two heaps:**

- "Find the **median** of a **stream**" (numbers arrive one at a time, query
  the median after each) — [Find Median from Data Stream (LC 295)](https://leetcode.com/problems/find-median-from-data-stream/).
- "Sliding window median" — the same idea, but elements also **leave** the
  window — [LC 480](https://leetcode.com/problems/sliding-window-median/).
- The problem describes **two pools that elements move between** based on a
  changing threshold — e.g., "projects you can currently afford" vs. "all
  other projects" ([IPO / Maximize Capital, LC 502](https://leetcode.com/problems/ipo/)).
- A **scheduling/simulation** problem with two distinct orderings of the same
  set of items — e.g., "available servers ordered by index" vs. "busy servers
  ordered by free-time" ([Single-Threaded CPU, LC 1834](https://leetcode.com/problems/single-threaded-cpu/),
  [Process Tasks Using Servers, LC 1882](https://leetcode.com/problems/process-tasks-using-servers/)).
- You repeatedly need **both** "the largest of the small half" and "the
  smallest of the large half" — any single heap only gives you one extreme.

**Anti-signals — looks like two heaps but isn't:**

- "Top k elements" with a **fixed** k — that's [top_k_elements](top_k_elements.md):
  one heap of size k, not two heaps that both grow with the input.
- "Merge k sorted..." — that's [k_way_merge](k_way_merge.md): one heap with
  one entry per source, not two heaps splitting a single dataset in half.
- **Median of two already-sorted arrays** ([LC 4](https://leetcode.com/problems/median-of-two-sorted-arrays/))
  — technically *could* be solved by dumping both arrays into a two-heap
  structure (`O((m+n) log(m+n))`), but the optimal solution is
  [binary search on a partition point](modified_binary_search.md)
  (`O(log(min(m,n)))`) — using two heaps here is a correct-but-suboptimal
  trap.
- "Sliding window **maximum**" (not median) — that's a
  [monotonic deque](monotonic_stack.md#6-variations--sub-patterns), which is
  O(1) amortized per element; two heaps would be O(log k) and is overkill for
  a single extreme.

---

## 2. Mental Model & Intuition

Picture the dataset split into two halves at the median:

```
        LEFT (smaller half)        |        RIGHT (larger half)
     max-heap (root = LARGEST       |     min-heap (root = SMALLEST
       of the small values)         |       of the large values)
              <= median             |              >= median

   {1, 2, 3}                        |              {5, 10}
        ^                           |               ^
        |                           |               |
   root = 3                         |          root = 5

Invariant:  len(left) == len(right)   OR   len(left) == len(right) + 1
            (left is allowed to hold at most one extra element)

Median:
  if len(left) > len(right): median = left's root   (odd total count)
  else:                       median = (left's root + right's root) / 2
```

Every insertion does three things: (1) push the new value into whichever heap
it belongs to (compare against `left`'s root), (2) **rebalance** if one heap
has grown more than one larger than the other by moving its root to the other
heap, (3) read the median in O(1) from the two roots.

---

## 3. The Template

```python
from __future__ import annotations
import heapq
from typing import List


class MedianFinder:
    """
    Two-heap running median (LC 295).
    `left`  -- max-heap of the smaller half, stored as NEGATED values.
    `right` -- min-heap of the larger half, stored as-is.
    Invariant: len(left) == len(right)  or  len(left) == len(right) + 1.
    """

    def __init__(self) -> None:
        self.left: List[int] = []    # max-heap (negated)
        self.right: List[int] = []   # min-heap

    def add_num(self, num: int) -> None:
        # Step 1: route the new value to the correct heap.
        if not self.left or num <= -self.left[0]:
            heapq.heappush(self.left, -num)
        else:
            heapq.heappush(self.right, num)

        # Step 2: rebalance so sizes differ by at most 1, left >= right.
        if len(self.left) > len(self.right) + 1:
            val = -heapq.heappop(self.left)
            heapq.heappush(self.right, val)
        elif len(self.right) > len(self.left):
            val = heapq.heappop(self.right)
            heapq.heappush(self.left, -val)

    def find_median(self) -> float:
        if len(self.left) > len(self.right):
            return float(-self.left[0])
        return (-self.left[0] + self.right[0]) / 2.0
```

---

## 4. Annotated Walkthrough

**Problem**: [Find Median from Data Stream (LC 295)](https://leetcode.com/problems/find-median-from-data-stream/)
— stream `5, 15, 1, 3`. After each insertion, report the running median.

```
INSERT 5
  left empty -> push -5 to left.       left = {5}        right = {}
  sizes: 1, 0 -> within invariant (1 == 0 + 1), no rebalance
  median: len(left) > len(right) -> 5

INSERT 15
  -left[0] = 5.  Is 15 <= 5?  No -> push 15 to right.
  left = {5}        right = {15}
  sizes: 1, 1 -> balanced, no rebalance
  median: equal sizes -> (5 + 15) / 2 = 10.0

INSERT 1
  -left[0] = 5.  Is 1 <= 5?  Yes -> push -1 to left.
  left = {5, 1}     right = {15}
  sizes: 2, 1 -> within invariant (2 == 1 + 1), no rebalance
  median: len(left) > len(right) -> top of left = 5

INSERT 3
  -left[0] = 5.  Is 3 <= 5?  Yes -> push -3 to left.
  left = {5, 1, 3}  right = {15}
  sizes: 3, 1 -> 3 > 1 + 1  -> REBALANCE:
     pop left's root (5) -> push 5 to right
     left = {1, 3}   right = {5, 15}
  sizes: 2, 2 -> balanced
  median: equal sizes -> (top(left) + top(right)) / 2 = (3 + 5) / 2 = 4.0
```

**Verification**: after `[5, 15, 1, 3]`, the sorted stream is `[1, 3, 5, 15]`
— the median of 4 elements is `(3 + 5) / 2 = 4.0`. Matches.

---

## 5. Complexity

| Operation | Time | Space | Why |
|---|---|---|---|
| `add_num` | O(log n) | O(1) amortized | One heap push (O(log n)), at most one rebalance (one pop + one push, O(log n)) |
| `find_median` | O(1) | — | Both heap roots are O(1) to read |
| Overall for n insertions | O(n log n) | O(n) | Every element lives in one of the two heaps |

Compare to the naive approach — keep a sorted list and binary-insert each new
element: insertion is `O(n)` (shifting elements) even though finding the
insertion point is `O(log n)`. Two heaps avoids the O(n) shift entirely.

---

## 6. Variations & Sub-patterns

**1. Sliding Window Median (LC 480) — lazy deletion.**
Elements must also **leave** the window. Heaps don't support efficient
arbitrary removal, so use **lazy deletion**: keep a hashmap of "elements
pending removal" with counts. When an element should leave, increment its
count in the map instead of removing it immediately. Before reading either
heap's root (or rebalancing), **clean** the root: while the root is marked
for removal, pop it and decrement its pending-removal count. Track the
"effective size" of each heap (`actual_size - pending_removals`) for the
balance invariant.

```
heap top is "stale" (marked for removal)?
   -> pop it, decrement its pending-removal counter, repeat
   -> THEN read/rebalance using the now-clean top
```

**2. IPO / Maximize Capital (LC 502) — "pool of available items" two heaps.**
A different two-heap shape: a **min-heap of projects ordered by required
capital** (the "not yet affordable" pool) and a **max-heap of profits** (the
"currently affordable, ready to pick" pool). At each step: move every project
whose capital requirement `<=` current capital from the min-heap to the
max-heap, then pop the max-heap's root (highest profit) and add it to your
capital. Repeat `k` times. Here the two heaps represent **two different
orderings of one moving frontier**, not "smaller half / larger half."

**3. Two heaps for discrete-event simulation.**
[Single-Threaded CPU (LC 1834)](https://leetcode.com/problems/single-threaded-cpu/)
and [Process Tasks Using Servers (LC 1882)](https://leetcode.com/problems/process-tasks-using-servers/)
use one heap ordered by **arrival/availability time** and another ordered by
**selection priority** (processing time + index, or server index). At each
simulated time step, move newly-available items from the time-heap into the
priority-heap, then pop the priority-heap for the next item to process. This
"availability heap feeds a selection heap" shape recurs across many
simulation problems.

**4. Running percentile (generalizing the median).**
To track the running **p-th percentile** instead of the median, change the
size invariant: `left` should hold roughly `p%` of the elements and `right`
the remaining `(100-p)%`. The rebalancing logic is identical — only the
target size ratio changes. (In production monitoring systems, this exact
two-heap idea is one building block for streaming percentile estimators,
though specialized structures like t-digest are more common at very large
scale.)

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Single-Threaded CPU (LC 1834)](https://leetcode.com/problems/single-threaded-cpu/) | Medium | Simulation two heaps | Arrival-time heap feeds a selection heap (min by processing time, then index) |
| [Process Tasks Using Servers (LC 1882)](https://leetcode.com/problems/process-tasks-using-servers/) | Medium | Simulation two heaps | Free-server heap (weight, id) + busy-server heap (free-time, weight, id) |
| [Total Cost to Hire K Workers (LC 2462)](https://leetcode.com/problems/total-cost-to-hire-k-workers/) | Medium | Head + tail candidate heaps | Two min-heaps shrink toward the middle; pick the cheaper front |
| [Stock Price Fluctuation (LC 2034)](https://leetcode.com/problems/stock-price-fluctuation/) | Medium | Max-heap + min-heap, lazy deletion | Both heaps store (price, ts); discard stale tops against a `latest` map |
| [Maximum Number of Events That Can Be Attended (LC 1353)](https://leetcode.com/problems/maximum-number-of-events-that-can-be-attended/) | Medium | Heap-driven greedy scheduling | Sort by start; min-heap of end-days, attend the soonest-ending each day |
| [Furthest Building You Can Reach (LC 1642)](https://leetcode.com/problems/furthest-building-you-can-reach/) | Medium | Related — single min-heap allocation | Spend ladders on the biggest gaps; min-heap holds the gaps bricks cover |
| [Find Median from Data Stream (LC 295)](https://leetcode.com/problems/find-median-from-data-stream/) | Hard | Canonical two-heap median | Max-heap (lower half) + min-heap (upper half); rebalance so sizes differ by ≤1 |
| [Sliding Window Median (LC 480)](https://leetcode.com/problems/sliding-window-median/) | Hard | Two heaps + lazy deletion | Elements both enter and leave; defer removals with a to-delete map |
| [IPO (LC 502)](https://leetcode.com/problems/ipo/) | Hard | "Available pool" two heaps | Min-heap by capital unlocks projects into a max-heap by profit |
| [Meeting Rooms III (LC 2402)](https://leetcode.com/problems/meeting-rooms-iii/) | Hard | Free + busy room simulation | Min-heap of free room ids + min-heap of (free-time, id) busy rooms |
| [Finding MK Average (LC 1825)](https://leetcode.com/problems/finding-mk-average/) | Hard | Running percentile (3 partitions) | Generalizes the median to a trimmed mean via balanced multiset/heaps |
| [The Skyline Problem (LC 218)](https://leetcode.com/problems/the-skyline-problem/) | Hard | Running max via max-heap + lazy deletion | Sweep x-events; max-heap of active heights, discard expired tops |
| [Maximum Performance of a Team (LC 1383)](https://leetcode.com/problems/maximum-performance-of-a-team/) | Hard | Related — sort + min-heap | Sort by efficiency desc; min-heap keeps the k largest speeds |
| [Minimize Deviation in Array (LC 1675)](https://leetcode.com/problems/minimize-deviation-in-array/) | Hard | Related — single heap, range shrink | Normalize odds up, then repeatedly shrink the current max via a max-heap |
| [Median of Two Sorted Arrays (LC 4)](https://leetcode.com/problems/median-of-two-sorted-arrays/) | Hard | Anti-signal — NOT two heaps here | Binary search on a partition is O(log(min(m,n))) — see [modified_binary_search](modified_binary_search.md) |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: comparing the new value to the heap's *internal* (negated)
representation instead of its actual value.**

```python
# BROKEN -- compares num directly to self.left[0], which is NEGATED
def add_num_broken(self, num: int) -> None:
    if not self.left or num <= self.left[0]:     # BUG: should be -self.left[0]
        heapq.heappush(self.left, -num)
    else:
        heapq.heappush(self.right, num)

    if len(self.left) > len(self.right) + 1:
        val = -heapq.heappop(self.left)
        heapq.heappush(self.right, val)
    elif len(self.right) > len(self.left):
        val = heapq.heappop(self.right)
        heapq.heappush(self.left, -val)
```

Trace on the stream `[10, 1, 2]` (correct median after all three should be `2`,
since sorted `[1, 2, 10]` has middle element `2`):

```
INSERT 10
  left empty -> push -10.   left = {10}   right = {}
  sizes 1,0 -> OK.  median = 10  (only element so far, correct)

INSERT 1
  self.left[0] = -10.  Is 1 <= -10?  NO (1 > -10)
  -> push 1 to right (WRONG -- 1 should go to left, the smaller half!)
  left = {10}   right = {1}
  sizes 1,1 -> balanced, no rebalance triggers
  median: equal sizes -> (-left[0] + right[0]) / 2 = (10 + 1) / 2 = 5.5

INSERT 2
  self.left[0] = -10.  Is 2 <= -10?  NO
  -> push 2 to right (again wrong)
  left = {10}   right = {1, 2}   (right heapified, root = 1)
  sizes: left=1, right=2 -> len(right) > len(left) -> rebalance:
     pop right's root (1) -> push -1 to left
     left = {10, 1}  (heapified, root = -10, i.e. max = 10)
     right = {2}
  sizes 2,1 -> OK
  median: len(left) > len(right) -> -left[0] = 10
```

**Result: `10`. Correct answer: `2`.** The broken comparison
`num <= self.left[0]` compares `1` to `-10` (always false for any reasonable
positive `num` once `left` holds a positive maximum), so almost every new
value is misrouted to `right` on arrival. Rebalancing only fixes *sizes*, not
*which values* ended up on which side — so `left` ends up holding `{10, 1}`
(max = 10) while `right` holds `{2}`, violating the core invariant
`max(left) <= min(right)` (`10 > 2`).

```python
# FIX -- negate self.left[0] back to compare against the ACTUAL max of left
def add_num(self, num: int) -> None:
    if not self.left or num <= -self.left[0]:    # FIX: -self.left[0] is the true max
        heapq.heappush(self.left, -num)
    else:
        heapq.heappush(self.right, num)

    if len(self.left) > len(self.right) + 1:
        val = -heapq.heappop(self.left)
        heapq.heappush(self.right, val)
    elif len(self.right) > len(self.left):
        val = heapq.heappop(self.right)
        heapq.heappush(self.left, -val)
```

Re-tracing `[10, 1, 2]` with the fix: insert 10 -> `left={10}`. Insert 1:
`1 <= -(-10)=10`? Yes -> push to left -> `left={10,1}`, sizes 2,0 -> rebalance
moves `10` to right -> `left={1}`, `right={10}` -> median `(1+10)/2=5.5`
(correct for `[10,1]`). Insert 2: `2 <= -(-1)=1`? No -> push to right ->
`right={2,10}` (root 2) -> sizes 1,2 -> rebalance moves `2` to left ->
`left={1,2}` (root -2, max=2), `right={10}` -> median: `len(left) >
len(right)` -> `2`. **Correct.** The lesson: **whenever a heap stores negated
values to simulate a max-heap, every comparison against that heap's root must
re-negate it back to the real value** — mixing negated and non-negated values
in the same comparison silently corrupts the invariant without raising an
error.

---

## 9. Related Patterns & When to Switch

- **[Top-K Elements](top_k_elements.md)** — a single heap of *fixed size k*
  for "the best k so far," vs. two heaps of *growing, balanced* size for "the
  middle of everything so far." If `k` doesn't change as the stream grows,
  it's top-k, not two heaps.
- **[K-Way Merge](k_way_merge.md)** — a heap with one entry *per source
  sequence*, an entirely different invariant from "split one stream into two
  balanced halves."
- **[Modified Binary Search](modified_binary_search.md)** — for **Median of
  Two Sorted Arrays**, binary search on the partition point is
  `O(log(min(m,n)))`, strictly better than any heap-based `O((m+n)
  log(m+n))` approach. Two heaps are for *streaming* medians; binary search
  is for *static, pre-sorted* medians.
- **Monotonic deque** (see [monotonic_stack.md §6](monotonic_stack.md#6-variations--sub-patterns))
  — for sliding window **maximum/minimum** (a single extreme), not median;
  O(1) amortized vs. O(log k).

---

## 10. Cross-links

- Concept module: [heaps_and_priority_queues](../heaps_and_priority_queues/)
  — heap invariants, why root access is O(1) but pop/push is O(log n).
- Applied: [java/collections_internals](../../java/collections_internals/) —
  `PriorityQueue` (min-heap by default, reverse `Comparator` for a max-heap);
  `TreeMap`/`TreeSet` as an alternative order-statistics structure for sliding
  window median (supports `O(log n)` removal of arbitrary elements, avoiding
  lazy deletion).
- Applied: [hld/caching](../../hld/caching/) — frequency- and recency-based
  eviction policies (LFU) share the "two ordered structures, one frontier"
  shape with the IPO/simulation variants in §6.
- Master recognition engine: [dsa_patterns/README.md](README.md).
- Sibling patterns: [top_k_elements.md](top_k_elements.md),
  [k_way_merge.md](k_way_merge.md).

---

## 11. Interview Q&A

**Q: Why a max-heap for the smaller half and a min-heap for the larger half — why not the other way around?**
The median sits at the *boundary* between the two halves. To read the
boundary in O(1), you need the **largest** element of the smaller half
(closest to the boundary from below) and the **smallest** element of the
larger half (closest from above). A max-heap exposes its largest element at
the root in O(1); a min-heap exposes its smallest. Swapping them would put
the *farthest-from-the-median* elements at the roots, useless for computing
the median.

**Q: How does the size-balance invariant guarantee O(1) median lookup?**
The invariant `len(left) == len(right)` or `len(left) == len(right) + 1`
means there are only two cases: equal sizes (median = average of both roots)
or `left` has exactly one more element (median = `left`'s root, the
"middle" element of an odd-sized dataset). Both cases read directly from the
two roots — no traversal needed. The `O(log n)` rebalancing on every insert
is what *maintains* this invariant so the O(1) read remains valid.

**Q: In the BROKEN→FIX trace, why does rebalancing (which only depends on sizes) fail to fix a values-based invariant violation?**
Rebalancing moves elements between heaps based purely on **counts**
(`len(left)` vs `len(right)`) to restore the size invariant — it has no
mechanism to verify `max(left) <= min(right)`. If the *initial routing*
(before rebalancing) puts a value on the wrong side, rebalancing can restore
the correct *sizes* while leaving the *wrong elements* on each side, silently
violating `max(left) <= min(right)` without throwing any error. This is why
the routing comparison (`num <= -self.left[0]`) must be correct independent
of rebalancing — rebalancing is a size-correction mechanism, not a
value-correction mechanism.

**Q: Sliding Window Median — why can't you just call a `heap.remove(x)` when an element leaves the window?**
Python's `heapq` (and most binary heap implementations) only support O(1)
peek and O(log n) pop **of the root** — removing an *arbitrary* element
requires an O(n) linear scan to find it, then O(log n) to re-heapify, making
each removal O(n) overall. **Lazy deletion** avoids this: mark the element as
"to be removed" in a hashmap (O(1)), and only actually pop it from a heap
when it happens to surface at the root during a future operation — at which
point popping the root is back to O(log n).

**Q: IPO/Maximize Capital — how is "two heaps" used differently here than in median-finding?**
In median-finding, the two heaps represent a **size-balanced split of one
sorted sequence** (smaller half / larger half), and elements move between
them to *maintain* that balance. In IPO, the two heaps represent **two
different states of the same item set** — "not yet affordable" (ordered by
capital requirement, min-heap) and "currently affordable" (ordered by profit,
max-heap). Items move *permanently* from the first to the second as your
capital grows; there's no "balance" invariant, just a one-way migration
driven by a threshold.

**Q: What's the time/space complexity of `add_num` and `find_median`, and why the asymmetry?**
`add_num` is `O(log n)`: one `heappush` (`O(log n)`) plus at most one
rebalance (`heappop` + `heappush`, also `O(log n)`). `find_median` is `O(1)`:
it only reads `heap[0]` from each heap, which is a plain array index, not a
heap operation. The asymmetry is the entire point of the pattern — you pay
`O(log n)` *once per insertion* so that *every* median query afterward is
free, which is far better than re-sorting (`O(n log n)`) on every query.

**Q: Why is Median of Two Sorted Arrays not typically solved with two heaps in the optimal solution?**
Both input arrays are *already fully sorted and static* — there's no
streaming. Pouring `m + n` elements into two heaps costs `O((m+n) log(m+n))`,
which is **worse** than just merging the two arrays directly (`O(m+n)`), and
far worse than the optimal `O(log(min(m,n)))` binary-search-on-a-partition
approach (see [modified_binary_search](modified_binary_search.md)). Two heaps
shine when data *arrives incrementally*; for static, pre-sorted data, a
one-shot algorithm tailored to that structure wins.

**Q: How would you extend this to track the running p-th percentile instead of the median?**
Change the target size ratio: instead of keeping `left` and `right` within
one element of equal size, keep `len(left) ~= p% * n` and
`len(right) ~= (100-p)% * n`. The rebalancing logic is structurally identical
— move the boundary element between heaps whenever the ratio drifts outside
tolerance. The median is the special case `p = 50`.

**Q: Does the algorithm still work correctly with duplicate values?**
Yes — heaps handle duplicates natively (multiple entries with the same value
are valid), and the comparison `num <= -self.left[0]` uses `<=`, so a
duplicate of the current boundary value is deterministically routed to
`left` without any special-casing. The invariant `max(left) <= min(right)`
permits `max(left) == min(right)` when duplicates straddle the boundary.

**Q: Single-Threaded CPU and Process Tasks Using Servers — how do these "two heap" problems differ structurally from median-finding?**
These are **discrete-event simulations**, not balanced-partition problems.
One heap orders items by *when they become available* (arrival time, or
server free-time); the other orders *available* items by *selection
priority* (shortest processing time, or smallest server index). Each
simulation step: pop newly-available items from the first heap into the
second, then pop the second heap to pick the next item to process. There is
no size-balance invariant between the two heaps — they represent
"not yet eligible" vs. "eligible and ranked," a fundamentally different
relationship than "smaller half / larger half."

**Q: What's the space complexity, and can it be reduced?**
`O(n)` — every inserted element lives in exactly one of the two heaps,
forever (for the streaming median problem, you must retain all history to
support the running median). This cannot be reduced for *exact* medians of an
unbounded stream; approximate streaming-median algorithms (e.g., t-digest,
P-square) trade exactness for `O(1)` or `O(log n)` space when only an
approximate percentile is acceptable — relevant for production metrics
systems tracking p50/p99 latencies over millions of events.

**Q: `find_median` returns a `float` even when `len(left) > len(right)` (an integer value) — is that necessary?**
LeetCode's `MedianFinder.findMedian()` is typed to return `double`
(Python: `float`) because the **even-size case** averages two integers and
may produce a `.5` result. For consistency (a single return type regardless
of parity), the odd-size case is also cast to `float`. If your language or
problem statement allows a union return type, you could return `int` for the
odd case and `float` for the even case — but matching the declared return
type is usually required by the test harness.
