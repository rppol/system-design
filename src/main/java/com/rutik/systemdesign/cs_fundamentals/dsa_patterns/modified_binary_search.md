# Modified Binary Search

## Pattern Snapshot

Binary search is not just "find a value in a sorted array." The deeper form is:
**given a search space where a yes/no predicate is monotonic (all `False` then all
`True`, or vice versa), find the boundary in O(log(space size))**. The "search
space" can be array indices, but it can equally be a *range of possible answers*
(a speed, a capacity, a distance, a day count) — this reframing is called
**binary search on the answer**.

- **One-line cue**: "sorted/rotated array" OR "minimize/maximize X such that
  some condition holds, and the condition gets easier/harder as X grows."
- **Typical complexity**: O(log n) for index search; O(n log(range)) for binary
  search on the answer (one O(n) feasibility check per O(log(range)) iterations).

---

## 1. Recognition Signals

**Strong signals — reach for modified binary search:**

- The array is **sorted**, or **rotated-sorted** (sorted then rotated at a pivot).
- "Find the first/last position of...", "find the insertion point", "find the
  smallest index such that...".
- "Find peak element", "find the minimum in a rotated sorted array".
- The phrase **"minimize the maximum"** or **"maximize the minimum"** combined
  with a feasibility check — e.g., "minimize the largest sum among k
  subarrays", "maximize the minimum distance between gas stations".
- Constraints suggest the answer itself can be searched: the answer lies in a
  bounded numeric range `[lo, hi]`, and you can write a function
  `feasible(x) -> bool` that is **monotonic** in `x` (once true, stays true —
  or once false, stays false).
- `n` up to `10^5`–`10^9` but the *answer range* is small enough that
  `O(n * log(range))` fits the time limit — a strong tell that brute force
  (`O(n * range)`) is intended to be optimized via binary search.
- Two sorted arrays/lists + "find the k-th element" or "find the median" —
  binary search on a *partition point*.

**Anti-signals — looks like binary search but isn't:**

- The array is **unsorted with no derivable monotonic structure** (e.g.,
  "find any pair that sums to target" in an unsorted array) — use
  [hashing_patterns](hashing_patterns.md), not binary search.
- "Find the k-th largest element" in an **unsorted** array — usually a
  [heap](top_k_elements.md) or quickselect problem, not binary search (unless
  the problem is phrased as "k-th smallest *pair distance*" or similar, where
  binary search on the answer + a counting pass *is* the right tool — see
  §7).
- The feasibility function is **not monotonic** — e.g., "can you partition the
  array into two subsets with equal sum" depends on subset *contents*, not a
  single threshold; that's a [dynamic_programming](dynamic_programming.md)
  problem (subset-sum DP), not binary search on the answer.
- "Find all pairs/triples" — binary search finds *one* boundary, not an
  enumeration; combine with another pattern (e.g., binary search per element
  inside a loop is fine, but the overall enumeration is driven by something
  else).

---

## 2. Mental Model & Intuition

**Classic binary search** — narrow a window `[lo, hi]` over *array indices* by
comparing the middle element to the target:

```
index:   0   1   2   3   4   5   6
arr:    [1,  3,  5,  7,  9, 11, 13]    target = 9

lo=0, hi=6 -> mid=3 -> arr[3]=7  < 9  -> lo=4
lo=4, hi=6 -> mid=5 -> arr[5]=11 > 9  -> hi=4
lo=4, hi=4 -> mid=4 -> arr[4]=9  == 9 -> FOUND at index 4
```

Each comparison eliminates half the remaining space — `log2(7) ~= 3` steps to
search 7 elements.

**Binary search on the answer** — the *same* narrowing, but `lo`/`hi` are not
array indices; they are the smallest and largest *possible answers*. A helper
`feasible(x)` tells you whether answer `x` "works." Because `feasible` is
monotonic, the sequence of results over `[lo, hi]` looks like:

```
candidate answer x:    1    2    3    4    5    6   ...  11
feasible(x):           F    F    F    T    T    T   ...   T
                                       ^
                                       |
                          smallest x where feasible(x) == True
                          ----> THIS is the answer we binary-search for
```

The search doesn't look at array *contents* directly each step — it calls
`feasible(mid)`, which itself may do an O(n) pass over the input. That is why
the overall complexity is `O(n * log(range))`, not `O(log n)`.

---

## 3. The Template

```python
from __future__ import annotations
from typing import Callable, List
import math


def binary_search(arr: List[int], target: int) -> int:
    """Classic binary search. Returns index of target, or -1 if absent."""
    lo, hi = 0, len(arr) - 1
    while lo <= hi:
        mid = lo + (hi - lo) // 2
        if arr[mid] == target:
            return mid
        elif arr[mid] < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return -1


def lower_bound(arr: List[int], target: int) -> int:
    """First index i such that arr[i] >= target (equivalent to bisect_left)."""
    lo, hi = 0, len(arr)
    while lo < hi:
        mid = lo + (hi - lo) // 2
        if arr[mid] < target:
            lo = mid + 1
        else:
            hi = mid
    return lo


def upper_bound(arr: List[int], target: int) -> int:
    """First index i such that arr[i] > target (equivalent to bisect_right)."""
    lo, hi = 0, len(arr)
    while lo < hi:
        mid = lo + (hi - lo) // 2
        if arr[mid] <= target:
            lo = mid + 1
        else:
            hi = mid
    return lo


def binary_search_on_answer(lo: int, hi: int, feasible: Callable[[int], bool]) -> int:
    """
    Generic 'binary search on the answer space' template.

    Precondition: feasible(x) is monotonic over [lo, hi] -- i.e. it looks like
    False, False, ..., False, True, True, ..., True.

    Returns the SMALLEST x in [lo, hi] for which feasible(x) is True.
    (To find the LARGEST x for which feasible(x) is True when the pattern is
    True...True, False...False, search for the smallest x where NOT feasible(x)
    and subtract 1.)
    """
    while lo < hi:
        mid = lo + (hi - lo) // 2
        if feasible(mid):
            hi = mid          # mid works -- the answer is mid or smaller
        else:
            lo = mid + 1       # mid doesn't work -- answer must be larger
    return lo


def search_rotated(arr: List[int], target: int) -> int:
    """Search in a rotated sorted array with no duplicates. O(log n)."""
    lo, hi = 0, len(arr) - 1
    while lo <= hi:
        mid = lo + (hi - lo) // 2
        if arr[mid] == target:
            return mid
        if arr[lo] <= arr[mid]:               # left half [lo..mid] is sorted
            if arr[lo] <= target < arr[mid]:
                hi = mid - 1
            else:
                lo = mid + 1
        else:                                  # right half [mid..hi] is sorted
            if arr[mid] < target <= arr[hi]:
                lo = mid + 1
            else:
                hi = mid - 1
    return -1


def find_peak_element(arr: List[int]) -> int:
    """Any index i such that arr[i] > arr[i-1] and arr[i] > arr[i+1]. O(log n)."""
    lo, hi = 0, len(arr) - 1
    while lo < hi:
        mid = lo + (hi - lo) // 2
        if arr[mid] > arr[mid + 1]:
            hi = mid            # peak is at mid or to the left
        else:
            lo = mid + 1        # peak is strictly to the right
    return lo
```

---

## 4. Annotated Walkthrough

**Problem**: [Koko Eating Bananas (LC 875)](https://leetcode.com/problems/koko-eating-bananas/)
— `piles = [3, 6, 7, 11]`, `h = 8`. Koko eats at speed `k` bananas/hour; from
each pile she needs `ceil(pile / k)` hours. Find the **minimum** integer `k`
such that she finishes all piles within `h` hours.

**Step 1 — frame as binary search on the answer.**
The answer `k` lies in `[1, max(piles)] = [1, 11]` (eating faster than the
biggest pile in one hour never helps). Define:

```python
def min_eating_speed(piles: List[int], h: int) -> int:
    def feasible(k: int) -> bool:
        hours = sum(math.ceil(pile / k) for pile in piles)
        return hours <= h

    return binary_search_on_answer(lo=1, hi=max(piles), feasible=feasible)
```

**Step 2 — verify monotonicity.** As `k` increases, each `ceil(pile/k)` can
only decrease or stay the same, so `hours` is non-increasing in `k`. That
means `feasible(k)` is `False, False, ..., False, True, True, ..., True` —
exactly the shape `binary_search_on_answer` requires.

**Step 3 — trace the search.**

```
lo=1, hi=11

mid=6: hours = ceil(3/6)+ceil(6/6)+ceil(7/6)+ceil(11/6)
             =   1     +   1     +   2     +   2      = 6   <= 8  -> feasible
   hi = 6

mid=(1+6)//2=3: hours = ceil(3/3)+ceil(6/3)+ceil(7/3)+ceil(11/3)
                       =   1     +   2     +   3     +   4    = 10  > 8  -> not feasible
   lo = 4

mid=(4+6)//2=5: hours = ceil(3/5)+ceil(6/5)+ceil(7/5)+ceil(11/5)
                       =   1     +   2     +   2     +   3    = 8   <= 8  -> feasible
   hi = 5

mid=(4+5)//2=4: hours = ceil(3/4)+ceil(6/4)+ceil(7/4)+ceil(11/4)
                       =   1     +   2     +   2     +   3    = 8   <= 8  -> feasible
   hi = 4

lo == hi == 4  ->  return 4
```

`k = 4` is the answer: at `k = 3`, `hours = 10 > 8` (too slow); at `k = 4`,
`hours = 8 <= 8` (just fits).

---

## 5. Complexity

| Variant | Time | Space | Why |
|---|---|---|---|
| Classic binary search (`binary_search`, `lower_bound`, `upper_bound`) | O(log n) | O(1) | Each step halves the index range `[lo, hi]`. |
| Search in rotated sorted array | O(log n) | O(1) | Same halving; one extra O(1) comparison per step to find the sorted half. |
| Binary search on the answer | O(n * log(range)) | O(1) extra | `log(range)` iterations, each running an O(n) `feasible` check. |
| Median of two sorted arrays (partition search) | O(log(min(m, n))) | O(1) | Binary searches the *smaller* array's partition point only. |

The dominant cost in "binary search on the answer" problems is almost always
the `feasible` check, not the search itself — when optimizing, look there
first (e.g., can `feasible` be made O(log n) with a prefix-sum + binary search
instead of O(n)?).

---

## 6. Variations & Sub-patterns

**1. Lower bound / upper bound (`bisect_left` / `bisect_right`).**
Python's `bisect` module implements exactly `lower_bound`/`upper_bound`.
`bisect_left(arr, x)` is the first index where `x` could be inserted keeping
the array sorted (before any equal elements); `bisect_right(arr, x)` is after
any equal elements. [Find First and Last Position of Element in Sorted Array (LC 34)](https://leetcode.com/problems/find-first-and-last-position-of-element-in-sorted-array/)
is `lower_bound(target)` and `upper_bound(target) - 1`.

**2. Rotated sorted array.**
At each step, exactly one of `[lo, mid]` or `[mid, hi]` is internally sorted
(compare `arr[lo]` to `arr[mid]`). Decide which half is sorted, then check if
the target lies within that half's range — if yes, recurse into it; if no,
recurse into the other half. With duplicates (`arr[lo] == arr[mid] == arr[hi]`),
you cannot tell which half is sorted — fall back to `lo += 1` (LC 81).

**3. Peak finding.**
"Go uphill": if `arr[mid] < arr[mid+1]`, a peak must exist to the right
(because the array either keeps climbing into a peak, or the boundary
`arr[n-1] = -inf` guarantees one). This finds *a* peak, not necessarily the
global maximum — O(log n) instead of O(n).

**4. Binary search on the answer — minimize the maximum / maximize the minimum.**
"Minimize the largest sum when splitting into k subarrays"
([Split Array Largest Sum (LC 410)](https://leetcode.com/problems/split-array-largest-sum/)),
"minimize the max distance" — all share the template: `lo = max(arr)` (or 0),
`hi = sum(arr)` (or some bound), `feasible(x)` greedily checks whether the
target can be achieved with budget/threshold `x`.

**5. Binary search on a partition (Median of Two Sorted Arrays).**
Instead of searching for a *value*, binary search for a *split point* `i` in
the smaller array such that `left_part` and `right_part` across both arrays
are balanced and cross-sorted (`max(left) <= min(right)`). This is the
hardest common variant — O(log(min(m, n))).

**6. 2D matrix search.**
If a matrix is row-sorted *and* column-sorted such that it can be viewed as
one flattened sorted array (`matrix[i][j]` corresponds to flat index
`i * cols + j`), run classic binary search on the flattened index
([Search a 2D Matrix (LC 74)](https://leetcode.com/problems/search-a-2d-matrix/)).
For matrices sorted only row-wise *and* column-wise independently (LC 240),
binary search per row, or use a staircase walk from the top-right corner
instead.

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Binary Search (LC 704)](https://leetcode.com/problems/binary-search/) | Easy | Classic | The baseline template |
| [Search Insert Position (LC 35)](https://leetcode.com/problems/search-insert-position/) | Easy | Lower bound | "Where would target be inserted" = `lower_bound` |
| [First Bad Version (LC 278)](https://leetcode.com/problems/first-bad-version/) | Easy | Binary search on answer | `isBadVersion` is the monotonic `feasible` |
| [Find First and Last Position of Element in Sorted Array (LC 34)](https://leetcode.com/problems/find-first-and-last-position-of-element-in-sorted-array/) | Medium | Lower + upper bound | Two binary searches back to back |
| [Search in Rotated Sorted Array (LC 33)](https://leetcode.com/problems/search-in-rotated-sorted-array/) | Medium | Rotated array | Identify which half is sorted each step |
| [Find Minimum in Rotated Sorted Array (LC 153)](https://leetcode.com/problems/find-minimum-in-rotated-sorted-array/) | Medium | Rotated array | Binary search for the rotation pivot |
| [Find Peak Element (LC 162)](https://leetcode.com/problems/find-peak-element/) | Medium | Peak finding | "Go uphill" toward a local max |
| [Koko Eating Bananas (LC 875)](https://leetcode.com/problems/koko-eating-bananas/) | Medium | Binary search on answer | Minimize max speed; `feasible` sums `ceil` |
| [Capacity To Ship Packages Within D Days (LC 1011)](https://leetcode.com/problems/capacity-to-ship-packages-within-d-days/) | Medium | Binary search on answer | Minimize capacity; `feasible` greedily packs days |
| [Find K-th Smallest Pair Distance (LC 719)](https://leetcode.com/problems/find-k-th-smallest-pair-distance/) | Hard | Binary search on answer + counting | Binary search on *distance*, count pairs `<= mid` with two pointers |
| [Split Array Largest Sum (LC 410)](https://leetcode.com/problems/split-array-largest-sum/) | Hard | Binary search on answer | Minimize the maximum subarray sum across k splits |
| [Search a 2D Matrix (LC 74)](https://leetcode.com/problems/search-a-2d-matrix/) | Medium | 2D as 1D | Treat the matrix as one flattened sorted array |
| [Single Element in a Sorted Array (LC 540)](https://leetcode.com/problems/single-element-in-a-sorted-array/) | Medium | Binary search on pair parity | Before the singleton, pairs start at even indices; after, they shift |
| [Minimum Number of Days to Make m Bouquets (LC 1482)](https://leetcode.com/problems/minimum-number-of-days-to-make-m-bouquets/) | Medium | Binary search on answer | Search the day; `feasible(d)` greedily counts adjacent bloomed runs |
| [Median of Two Sorted Arrays (LC 4)](https://leetcode.com/problems/median-of-two-sorted-arrays/) | Hard | Binary search on a partition | O(log(min(m,n))); balance + cross-sorted halves |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: using `lo = mid` instead of `lo = mid + 1` causes an infinite loop.**

```python
# BROKEN -- infinite loop when lo + 1 == hi and feasible(mid) is False
def binary_search_on_answer_broken(lo: int, hi: int, feasible: Callable[[int], bool]) -> int:
    while lo < hi:
        mid = lo + (hi - lo) // 2
        if feasible(mid):
            hi = mid
        else:
            lo = mid          # BUG: should be mid + 1
    return lo
```

Trace the failure with `lo=4, hi=5`:

```
lo=4, hi=5 -> mid = 4 + (5-4)//2 = 4
   feasible(4) is False
   lo = mid = 4          <- lo did not change!
lo=4, hi=5 -> mid = 4 again -> same result -> lo stuck at 4 forever
```

Because Python's floor division rounds `mid` *down* toward `lo`, the
"not feasible" branch must strictly advance past `mid` — otherwise `lo` can
equal `mid` again on the next iteration with the same `hi`, and the loop never
terminates.

```python
# FIX -- the not-feasible branch must move lo past mid
def binary_search_on_answer(lo: int, hi: int, feasible: Callable[[int], bool]) -> int:
    while lo < hi:
        mid = lo + (hi - lo) // 2
        if feasible(mid):
            hi = mid
        else:
            lo = mid + 1      # FIX: guarantees lo strictly increases
    return lo
```

The symmetric rule: when narrowing toward the *upper* boundary (the largest
`x` for which `feasible(x)` is True), use `mid = lo + (hi - lo + 1) // 2`
(round up) and set `lo = mid` / `hi = mid - 1` — rounding the *other* way to
avoid the same stall on the opposite side.

---

## 9. Related Patterns & When to Switch

- **[Two Pointers](two_pointers.md)** — if the array is sorted and you're
  looking for a *pair* that satisfies a condition (not a single boundary),
  two pointers is O(n) and simpler than nested binary search.
- **[Top-K Elements](top_k_elements.md)** — "k-th largest in an unsorted
  array" is usually a heap or quickselect problem (O(n log k) or average
  O(n)). Binary search on the answer becomes relevant only when the *value
  space* is what you're searching (e.g., LC 719's pair-distance problem).
- **[Dynamic Programming](dynamic_programming.md)** — if "can we achieve X"
  depends on *which elements* are chosen (not just a threshold), the
  feasibility function is not a simple monotonic predicate over a single
  number — that's subset-sum / partition DP, not binary search.
- **[Cyclic Sort](cyclic_sort.md)** — for "find the missing/duplicate number
  in `[1..n]`" with O(1) extra space, cyclic sort is O(n) and avoids binary
  search entirely (though "Find the Duplicate Number" can also be solved with
  binary search on the value range as a fallback).

---

## 10. Cross-links

- Concept module: [sorting_and_searching](../sorting_and_searching/) — binary
  search fundamentals, time complexity proofs, comparison-based search lower
  bounds.
- Applied: [python/collections_and_data_structures](../../python/collections_and_data_structures/)
  — the `bisect` module (`bisect_left`, `bisect_right`, `insort`) is a direct,
  production-ready implementation of `lower_bound`/`upper_bound`.
- Master recognition engine: [dsa_patterns/README.md](README.md) — see the
  Constraints -> Complexity -> Pattern table (large `n` with small answer
  range is the strongest "binary search on the answer" tell).
- Sibling patterns: [two_pointers.md](two_pointers.md),
  [top_k_elements.md](top_k_elements.md).

---

## 11. Interview Q&A

**Q: How do I know whether to use `lo <= hi` or `lo < hi` as the loop condition?**
Use `lo <= hi` (with `hi = mid - 1` / `lo = mid + 1`) when you're searching
for an *exact match* and want to detect "not found" (loop exits with
`lo > hi`). Use `lo < hi` (with `hi = mid` / `lo = mid + 1`) when you're
searching for a *boundary* — the loop exits with `lo == hi` pointing exactly
at the boundary, which is always a valid answer (assuming `[lo, hi]` is
chosen so the answer is guaranteed to exist in range).

**Q: What does "binary search on the answer" actually mean, and how do I spot it?**
It means the thing you binary search over is not the input array but a
*range of possible output values*. The tell is a problem phrased as
"minimize/maximize X such that some condition on the whole input holds," where
checking the condition for a *fixed* X is easy (often a greedy O(n) scan), and
the condition flips monotonically as X increases. If you can write
`feasible(x) -> bool` and argue it's monotonic, you can binary search on `x`
even if `x` was never an index into anything.

**Q: How do you prove a `feasible` function is monotonic before relying on it?**
Argue about the underlying quantity: in Koko Eating Bananas, increasing the
eating speed `k` can only decrease or keep equal each `ceil(pile/k)` term, so
total hours is non-increasing in `k` — hence `feasible(k) = (hours <= h)` goes
from False to True exactly once as `k` increases. If you can't construct this
kind of "increasing X can only help/hurt, never both" argument, binary search
on the answer is unsound — look for a DP formulation instead.

**Q: Walk through Search in Rotated Sorted Array — how do you find which half is sorted?**
Compare `arr[lo]` to `arr[mid]`. If `arr[lo] <= arr[mid]`, the left half
`[lo, mid]` is internally sorted (no rotation point inside it) — check if
`target` falls in `[arr[lo], arr[mid])`; if so, search left, else search
right. If `arr[lo] > arr[mid]`, the rotation point is inside `[lo, mid]`, so
the *right* half `[mid, hi]` must be sorted instead — apply the symmetric
check. Exactly one half is always guaranteed sorted (absent duplicates),
which is what keeps this O(log n).

**Q: Why `mid = lo + (hi - lo) // 2` instead of `(lo + hi) // 2`?**
In languages with fixed-width integers (Java, C++), `lo + hi` can overflow if
both are near `INT_MAX`, wrapping to a negative number and corrupting `mid`.
`lo + (hi - lo) // 2` never exceeds `hi`, so it can't overflow. Python integers
are arbitrary precision, so this specific bug can't occur here — but writing
it the overflow-safe way is a habit worth keeping since interviewers often
ask "does this work in Java/C++ too?"

**Q: What's the difference between `bisect_left` and `bisect_right`, and when do I use each?**
`bisect_left(arr, x)` returns the leftmost position where `x` can be inserted
— i.e., the first index `i` with `arr[i] >= x` (this is `lower_bound`).
`bisect_right(arr, x)` returns the rightmost such position — the first index
`i` with `arr[i] > x` (this is `upper_bound`). Use `bisect_left` to find "the
first occurrence of x" or "how many elements are strictly less than x"; use
`bisect_right` to find "the count of elements `<= x`" or "the insertion point
that places x *after* any duplicates" (useful for maintaining a sorted list of
non-decreasing values via `insort_right`, the default).

**Q: Binary search on floating-point answers — how do you decide when to stop?**
Two options: (1) fixed iteration count — `for _ in range(100): mid = (lo+hi)/2; ...`
(100 iterations of halving a reasonable range converges far past double
precision, ~`10^-30`); or (2) epsilon termination —
`while hi - lo > 1e-7: ...`. Fixed iteration count is preferred in interviews
because it sidesteps debates about what epsilon is "small enough" and always
terminates in a known number of steps.

**Q: Why is Median of Two Sorted Arrays a binary search problem and not a merge problem?**
Merging is O(m + n) — correct but not optimal. The O(log(min(m,n)))
insight is: the median is defined by a *partition* that splits the combined
array into two halves of equal size where every element on the left is
`<= ` every element on the right. You binary search for the partition index
in the *smaller* array (the partition index in the larger array is then
determined: `total_left - i`). Each candidate partition is checked in O(1) by
comparing four boundary elements — that's what makes it O(log(min(m,n)))
instead of O(m+n).

**Q: How do you handle Search in Rotated Sorted Array with duplicates (LC 81)?**
When `arr[lo] == arr[mid] == arr[hi]`, you cannot tell which half is sorted
(both could look "flat" while the rotation point hides inside either). The
standard fix is to shrink the search space by one: `lo += 1` (or `hi -= 1`),
degrading worst-case complexity to O(n) (e.g., for an array of all-equal
values with one different element), but average case remains close to
O(log n).

**Q: Find Peak Element finds *a* peak — why does "go uphill" guarantee correctness for *any* peak, not just the global max?**
The problem only asks for *a* local peak (`arr[i] > arr[i-1]` and
`arr[i] > arr[i+1]`, with `arr[-1] = arr[n] = -infinity` conceptually). If
`arr[mid] < arr[mid+1]`, the sequence is "still climbing" at `mid` — there
must be a peak somewhere to the right (worst case, the climb continues to
`arr[n-1]`, which is then a peak relative to the implicit `-infinity` boundary).
Symmetric logic applies leftward. This guarantees *some* peak exists in the
direction you move, even though you skip over the rest of the array.

**Q: When is binary search the wrong choice even though the array is sorted?**
When you need to examine *all* elements that satisfy a condition (not just a
boundary) and that set isn't contiguous, or when the relationship you care
about is between *pairs* of elements rather than a single threshold (use
[two_pointers](two_pointers.md) instead). Also, if `n` is small (say `<= 20`)
and the real difficulty is *which combination* of elements to pick, binary
search on a threshold won't capture combinatorial structure — that calls for
[backtracking](backtracking.md) or [dynamic_programming](dynamic_programming.md).

**Q: What's the complexity tradeoff of "binary search on the answer + O(n) feasibility check" versus "sort + two pointers"?**
Binary search on the answer is `O(n * log(range))`. If the same problem can be
solved by sorting once (`O(n log n)`) and then a single linear pass with two
pointers (`O(n)`), the sort-based approach is `O(n log n)` total — often
*faster* than `O(n * log(range))` when `range` is very large (e.g.,
`10^9`). Always compare `log(range)` to `log n`: if `range >> n`, prefer a
sort-based or counting-based approach if one exists; if `range` is small or
the array can't be usefully sorted (e.g., it represents independent items, not
comparable values), binary search on the answer wins.
