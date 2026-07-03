# Prefix Sum

## Pattern Snapshot

Precompute cumulative sums (`prefix[i] = sum(nums[0..i-1])`) so that the sum of any range `[i, j]` becomes `prefix[j+1] - prefix[i]` — an O(1) lookup instead of an O(n) re-sum. Combined with a hashmap, this turns "count subarrays with sum == k" (which can have negative numbers, breaking sliding window) into a single O(n) pass. **Cue**: "range sum query", "subarray sum equals k", "can the array contain negatives?" **Typical complexity**: O(n) precompute + O(1) per query, or O(n) total with a hashmap for counting/existence checks.

---

## 1. Recognition Signals

**Reach for prefix sum when you see:**

- "Range sum query" — given `(i, j)`, return `sum(nums[i..j])`, possibly with **many queries** on a **static** array
- "Subarray sum equals k" / "count subarrays with sum divisible by k" / "count subarrays with sum at most k" — especially when the array **can contain negative numbers** (rules out sliding window)
- "Equilibrium index" / "find a pivot where left sum == right sum"
- "Product of array except self" — same idea, but with running products (prefix product * suffix product)
- 2D variants: "range sum query 2D" — 2D prefix sums (a.k.a. summed-area tables)
- "Number of ways to split the array into two parts with equal sum"

**Anti-signals — looks like prefix sum but isn't:**

- The array is **non-negative** and the question asks for the **longest/shortest** contiguous subarray under a sum constraint — **[Sliding Window](sliding_window.md)** is more direct (O(1) space vs O(n) for the hashmap) when monotonicity holds
- The array is **mutable** (point updates interleaved with range queries) — plain prefix sum requires O(n) to rebuild after each update; use a **Fenwick tree (Binary Indexed Tree)** or **segment tree** instead (covered in [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/))
- "Maximum subarray sum" with **no target** — that's Kadane's algorithm (a DP/greedy hybrid), not prefix sum + hashmap (though prefix sum *can* solve it: `max(prefix[j] - min(prefix[i] for i < j))`, this is more naturally framed as DP)
- You need the actual **subarray itself**, not just a count or existence check, AND the subarray boundaries depend on more than two prefix values — may need a different DP formulation

The defining test: **can the answer be expressed as a difference of two cumulative values, `prefix[j] - prefix[i]`, where you're searching over pairs `(i, j)`?** If yes — and especially if values can be negative — prefix sum (+ hashmap to find matching `prefix[i]` values in O(1)) is the pattern.

---

## 2. Mental Model & Intuition

```
Building the prefix array

  nums   = [ 1,  2,  3,  4,  5]
  prefix = [0,  1,  3,  6, 10, 15]
            ^   ^   ^   ^   ^   ^
            |   |   |   |   |   +-- prefix[5] = sum(nums[0..4]) = 15
            |   |   |   |   +------ prefix[4] = sum(nums[0..3]) = 10
            |   |   |   +---------- prefix[3] = sum(nums[0..2]) = 6
            |   |   +-------------- prefix[2] = sum(nums[0..1]) = 3
            |   +------------------ prefix[1] = sum(nums[0..0]) = 1
            +---------------------- prefix[0] = 0 (empty prefix -- CRITICAL sentinel)

  sum(nums[1..3]) = sum of indices 1,2,3 = 2+3+4 = 9
                   = prefix[4] - prefix[1] = 10 - 1 = 9   (O(1)!)

  General formula: sum(nums[i..j]) = prefix[j+1] - prefix[i]
```

```
The hashmap trick for "count subarrays with sum == k"

  If sum(nums[i..j]) == k, then prefix[j+1] - prefix[i] == k,
  i.e., prefix[i] == prefix[j+1] - k.

  As we scan j from left to right (computing prefix[j+1] incrementally),
  we ask: "how many earlier prefixes equal (current prefix - k)?"
  -- answer is a hashmap lookup, O(1).

  nums = [1, 2, 3], k = 3
  seen = {0: 1}     <- prefix[0] = 0, count 1 (the sentinel!)
  prefix=0
  j=0 'nums[0]=1': prefix=1. need prefix[i]=1-3=-2. seen has 0 of -2. count=0
                   seen = {0:1, 1:1}
  j=1 'nums[1]=2': prefix=3. need prefix[i]=3-3=0. seen[0]=1 -> count += 1 (=1)
                   subarray found: nums[0..1] = [1,2], sum=3 [OK]
                   seen = {0:1, 1:1, 3:1}
  j=2 'nums[2]=3': prefix=6. need prefix[i]=6-3=3. seen[3]=1 -> count += 1 (=2)
                   subarray found: nums[2..2] = [3], sum=3 [OK]
                   seen = {0:1, 1:1, 3:1, 6:1}

  total count = 2  (subarrays [1,2] and [3])
```

The `seen[0] = 1` sentinel is what allows subarrays *starting at index 0* to be counted — without it, a subarray `nums[0..j]` with `sum == k` (i.e., `prefix[j+1] == k`, so `prefix[i] == 0` for `i=0`) would never be found.

---

## 3. The Template

### Static range sum queries (no hashmap needed)

```python
class PrefixSumArray:
    def __init__(self, nums: list[int]):
        self.prefix = [0] * (len(nums) + 1)
        for i, num in enumerate(nums):
            self.prefix[i + 1] = self.prefix[i] + num

    def range_sum(self, left: int, right: int) -> int:
        """Inclusive range [left, right], 0-indexed."""
        return self.prefix[right + 1] - self.prefix[left]
```

### Count subarrays with sum == k (hashmap)

```python
from collections import defaultdict

def subarray_sum_equals_k(nums: list[int], k: int) -> int:
    seen = defaultdict(int)
    seen[0] = 1            # CRITICAL: empty-prefix sentinel
    prefix = 0
    count = 0

    for num in nums:
        prefix += num
        count += seen[prefix - k]   # how many earlier prefixes match prefix - k?
        seen[prefix] += 1

    return count
```

### Subarray sum divisible by k (modular variant)

```python
def subarray_divisible_by_k(nums: list[int], k: int) -> int:
    seen = defaultdict(int)
    seen[0] = 1
    prefix = 0
    count = 0

    for num in nums:
        prefix = (prefix + num) % k
        prefix = (prefix + k) % k    # normalize negative remainders to [0, k)
        count += seen[prefix]
        seen[prefix] += 1

    return count
```

---

## 4. Annotated Walkthrough

**Problem**: [Subarray Sum Equals K (LC 560)](https://leetcode.com/problems/subarray-sum-equals-k/) — given an array of integers (possibly negative) and a target `k`, return the number of contiguous subarrays whose sum equals `k`.

**Brute force**: for every pair `(i, j)`, compute `sum(nums[i..j])` — O(n^2) (or O(n^3) if you re-sum from scratch each time, O(n^2) if you extend the running sum). For n up to 2*10^4, O(n^2) = 4*10^8 — borderline, often TLE in Python.

**Why not sliding window?** `nums` can contain negatives. Consider `nums = [3, 4, -7, 1, 3, 3, 1, -4], k = 7`. As `right` advances, the window sum can go up, then down (due to `-7`), then up again — there's no monotonic relationship between window size and sum, so "shrink when sum > k" is not a valid operation (shrinking might make the sum *smaller* than k when it was already too small due to a later negative).

**Key insight**: reframe "subarray `[i+1..j]` sums to `k`" as "`prefix[j+1] - prefix[i] == k`", i.e., "`prefix[i] == prefix[j+1] - k`". As we scan and compute `prefix[j+1]` incrementally, we just need to know **how many times** the value `prefix[j+1] - k` has appeared as a prefix sum *before* — a hashmap gives O(1) lookups.

**Trace on `nums = [3, 4, -7, 1, 3, 3, 1, -4]`, `k = 7`**

```
seen = {0: 1}, prefix = 0, count = 0

num=3:  prefix=3.  need = 3-7=-4.  seen[-4]=0. count=0. seen={0:1, 3:1}
num=4:  prefix=7.  need = 7-7=0.   seen[0]=1.  count=1. seen={0:1,3:1,7:1}
        -> subarray nums[0..1]=[3,4] sums to 7. CORRECT.
num=-7: prefix=0.  need = 0-7=-7.  seen[-7]=0. count=1. seen={0:2,3:1,7:1}
        -> prefix returned to 0! seen[0] is now 2 (the sentinel + this one)
num=1:  prefix=1.  need = 1-7=-6.  seen[-6]=0. count=1. seen={...,1:1}
num=3:  prefix=4.  need = 4-7=-3.  seen[-3]=0. count=1. seen={...,4:1}
num=3:  prefix=7.  need = 7-7=0.   seen[0]=2.  count=1+2=3.
        -> TWO subarrays end here with sum 7:
           nums[0..5] = [3,4,-7,1,3,3] sum=7  (prefix[0]=0 match)
           nums[2..5] = [-7,1,3,3] sum=0... wait let's recheck: prefix[6]=7, prefix[i]=0 for i in {0, 2}
           nums[i..5] for i=0: [3,4,-7,1,3,3] sum = 7 [OK]
           nums[i..5] for i=2: [-7,1,3,3] sum = 0... that's wrong, expected 7

```

Let me recompute carefully: `prefix[2] = 3+4 = 7`, `prefix[3] = 7 + (-7) = 0`. So `seen[0]` becomes 2 *after* processing index 2 (`prefix[3]=0`, plus the original `seen[0]=1` sentinel for `prefix[0]=0`). At `num=3` (6th element, index 5), `prefix[6] = 7`. We need `prefix[i] == prefix[6] - k == 0`, and `seen[0] = 2` at this point — meaning `i ∈ {0, 3}`. Subarray for `i=0`: `nums[0..5] = [3,4,-7,1,3,3]`, sum = `3+4-7+1+3+3 = 7` ✓. Subarray for `i=3`: `nums[3..5] = [1,3,3]`, sum = `1+3+3 = 7` ✓. Both correct — `count=3` after this step is right (the original analysis above had an indexing slip; the hashmap-based count is correct because it tracks `prefix[i]` values by their *index in the prefix array*, not by re-deriving subarray boundaries by hand).

This walkthrough illustrates the most important practical lesson: **trust the prefix-index algebra, not manual subarray reconstruction** — manual reconstruction is error-prone exactly because of off-by-one prefix/array index shifts, which is precisely what the algorithm abstracts away.

---

## 5. Complexity

| Approach | Time | Space |
|---|---|---|
| Brute force (all pairs, running sum) | O(n^2) | O(1) |
| Prefix array, static range queries | O(n) precompute + O(1) per query | O(n) |
| Prefix sum + hashmap (count/existence) | O(n) | O(n) |
| Mutable array + Fenwick tree | O(log n) per update/query | O(n) |

The hashmap variant trades O(n) space for collapsing O(n^2) brute force into O(n) — one of the most favorable tradeoffs in the entire pattern catalog, which is why "subarray sum" problems are interview staples.

---

## 6. Variations & Sub-patterns

- **Static range sum queries** — precompute once, answer each query in O(1) ([Range Sum Query - Immutable (LC 303)](https://leetcode.com/problems/range-sum-query-immutable/))
- **Count subarrays with sum == k** — the hashmap template, handles negatives ([Subarray Sum Equals K (LC 560)](https://leetcode.com/problems/subarray-sum-equals-k/))
- **Subarray sum divisible by k** — track `prefix % k` instead of raw `prefix`; pigeonhole guarantees a match within `k` distinct remainders ([Subarray Sums Divisible by K (LC 974)](https://leetcode.com/problems/subarray-sums-divisible-by-k/))
- **Equilibrium / pivot index** — find `i` where `prefix[i] == total - prefix[i+1]` ([Find Pivot Index (LC 724)](https://leetcode.com/problems/find-pivot-index/))
- **Prefix XOR** — same idea with XOR instead of sum (`a XOR a = 0`, so `prefix[j] XOR prefix[i] == target` checks ranges) ([Count Triplets That Can Form Two Arrays of Equal XOR (LC 1442)](https://leetcode.com/problems/count-triplets-that-can-form-two-arrays-of-equal-xor/))
- **Prefix product / suffix product** — "Product of Array Except Self" computes `prefix_product[i] * suffix_product[i]` for each `i`, avoiding division ([Product of Array Except Self (LC 238)](https://leetcode.com/problems/product-of-array-except-self/))
- **2D prefix sum (summed-area table)** — `prefix[i][j] = sum of all cells (0,0) to (i-1,j-1)`; range query via inclusion-exclusion: `prefix[r2+1][c2+1] - prefix[r1][c2+1] - prefix[r2+1][c1] + prefix[r1][c1]` ([Range Sum Query 2D - Immutable (LC 304)](https://leetcode.com/problems/range-sum-query-2d-immutable/))
- **Binary array tricks** — "max subarray with equal 0s and 1s": map `0 -> -1`, then "subarray sums to 0" finds equal counts ([Contiguous Array (LC 525)](https://leetcode.com/problems/contiguous-array/))

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Range Sum Query - Immutable (LC 303)](https://leetcode.com/problems/range-sum-query-immutable/) | Easy | Static prefix array | Many queries, no updates |
| [Find Pivot Index (LC 724)](https://leetcode.com/problems/find-pivot-index/) | Easy | Equilibrium index | `left_sum == total - left_sum - nums[i]` |
| [Product of Array Except Self (LC 238)](https://leetcode.com/problems/product-of-array-except-self/) | Medium | Prefix/suffix product | No division allowed |
| [Subarray Sum Equals K (LC 560)](https://leetcode.com/problems/subarray-sum-equals-k/) | Medium | Hashmap count | Array CAN have negatives |
| [Contiguous Array (LC 525)](https://leetcode.com/problems/contiguous-array/) | Medium | Map 0→-1, sum==0 | Equal count of 0s and 1s |
| [Subarray Sums Divisible by K (LC 974)](https://leetcode.com/problems/subarray-sums-divisible-by-k/) | Medium | Modular prefix | Normalize negative `% k` results |
| [Continuous Subarray Sum (LC 523)](https://leetcode.com/problems/continuous-subarray-sum/) | Medium | Modular prefix, length >= 2 | Track earliest INDEX of each remainder |
| [Range Sum Query 2D - Immutable (LC 304)](https://leetcode.com/problems/range-sum-query-2d-immutable/) | Medium | 2D prefix sum | Inclusion-exclusion on 4 corners |
| [Maximum Size Subarray Sum Equals k (LC 325)](https://leetcode.com/problems/maximum-size-subarray-sum-equals-k/) | Medium | Track earliest index per prefix value | "Length" not "count" — store first occurrence index |
| [Count Number of Nice Subarrays (LC 1248)](https://leetcode.com/problems/count-number-of-nice-subarrays/) | Medium | Prefix count of odd numbers | Reduces to "subarray sum == k" on a transformed array |
| [Maximum Subarray (LC 53)](https://leetcode.com/problems/maximum-subarray/) | Medium | Prefix-min variant | `max(prefix[i] - min_prefix_so_far)`; equivalent to Kadane — see [dynamic_programming.md](dynamic_programming.md) |
| [Binary Subarrays With Sum (LC 930)](https://leetcode.com/problems/binary-subarrays-with-sum/) | Medium | Prefix-count hashmap | Or the at-most(k) sliding-window trick — see [sliding_window.md](sliding_window.md) |
| [Count Triplets That Can Form Two Arrays of Equal XOR (LC 1442)](https://leetcode.com/problems/count-triplets-that-can-form-two-arrays-of-equal-xor/) | Medium | Prefix XOR | `prefix[i] == prefix[k]` ⇒ any split between them works |
| [Range Sum Query - Mutable (LC 307)](https://leetcode.com/problems/range-sum-query-mutable/) | Medium | Contrast — point updates | A static prefix array breaks on updates; use a Fenwick/segment tree |
| [Number of Submatrices That Sum to Target (LC 1074)](https://leetcode.com/problems/number-of-submatrices-that-sum-to-target/) | Hard | 2D collapse + 1D prefix hashmap | Fix a row band, collapse to a 1D "subarray sum == target" |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: forgetting the `seen[0] = 1` sentinel, which silently undercounts subarrays starting at index 0.**

```python
# BROKEN — no sentinel for the empty prefix.
from collections import defaultdict

def subarray_sum_equals_k_broken(nums: list[int], k: int) -> int:
    seen = defaultdict(int)        # BUG: missing seen[0] = 1
    prefix = 0
    count = 0
    for num in nums:
        prefix += num
        count += seen[prefix - k]
        seen[prefix] += 1
    return count
```

```python
# FIXED — seed seen with {0: 1} BEFORE the loop, representing the
# "empty prefix" (sum of zero elements), so subarrays starting at
# index 0 (where prefix[i] == 0 for i == 0) are counted.
from collections import defaultdict

def subarray_sum_equals_k_fixed(nums: list[int], k: int) -> int:
    seen = defaultdict(int)
    seen[0] = 1                    # FIX: empty-prefix sentinel
    prefix = 0
    count = 0
    for num in nums:
        prefix += num
        count += seen[prefix - k]
        seen[prefix] += 1
    return count
```

**Trigger**: `nums = [1, 2, 3]`, `k = 3`. The subarray `[1, 2]` (indices 0-1) sums to 3. At `num=2` (second iteration), `prefix = 3`, and we need `seen[prefix - k] = seen[0]`. With the broken version, `seen[0] = 0` (never seeded), so this valid subarray is missed — `count` stays 0 instead of incrementing to 1. The fixed version has `seen[0] = 1` from the start, correctly counting it. Final correct answer for this input is `2` (subarrays `[1,2]` and `[3]`); the broken version returns `1`.

---

## 9. Related Patterns & When to Switch

- **[Sliding Window](sliding_window.md)** — switch when all values are **non-negative** and you need the longest/shortest subarray (not a count) — sliding window is O(1) space vs prefix sum's O(n).
- **[Hashing Patterns](hashing_patterns.md)** — prefix sum + hashmap *is* a hashing pattern; the "complement" being looked up is `prefix[j] - k` instead of `target - nums[i]`. If you understand Two Sum's complement trick, prefix sum's hashmap trick is the same idea applied to cumulative sums.
- **Fenwick tree / Segment tree** (see [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/)) — switch when the array is **mutable** (point updates interleaved with range sum queries); plain prefix sums require O(n) rebuild per update, Fenwick trees do both in O(log n).
- **[Dynamic Programming](dynamic_programming.md)** — "maximum subarray sum" (Kadane's) can be derived from prefix sums (`max(prefix[j] - min_prefix_so_far)`) but is usually taught and recognized as a 1-D DP / greedy hybrid.

---

## 10. Cross-links

- Concept module: [arrays_strings_and_hashing](../arrays_strings_and_hashing/) — array fundamentals, hashmap internals (`defaultdict`, average O(1) operations)
- [graphs_tries_and_advanced_structures](../graphs_tries_and_advanced_structures/) — Fenwick tree / segment tree for the *mutable* version of this problem
- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/) — why O(n) + O(n) hashmap beats O(n^2) brute force
- Applied: [`../../database/indexing_deep_dive/README.md`](../../database/indexing_deep_dive/README.md) — materialized aggregate columns / running totals in databases are a real-world prefix-sum analog
- Master index: [dsa_patterns/README.md](README.md)

---

## 11. Interview Q&A

**Q: Why is `prefix[0] = 0` ("the empty prefix") so important, and what does it represent?**
`prefix[0] = 0` represents the sum of zero elements — it's the identity element for addition. It allows the formula `sum(nums[i..j]) = prefix[j+1] - prefix[i]` to work uniformly even when `i = 0` (a subarray starting at the very first element): `sum(nums[0..j]) = prefix[j+1] - prefix[0] = prefix[j+1] - 0 = prefix[j+1]`. In the hashmap variant, seeding `seen = {0: 1}` before the loop is the same idea — it lets subarrays starting at index 0 be "found" as a match against this sentinel.

**Q: Why doesn't sliding window work for "subarray sum equals k" when the array has negative numbers?**
Sliding window's correctness relies on the window sum changing *monotonically* as you expand/shrink — specifically, "if the sum is too big, shrinking from the left makes it smaller (or equal), never bigger." With negative numbers, adding an element to the window (`right += 1`) could *decrease* the sum, and removing an element from the left (`left += 1`) could *increase* it (if `nums[left]` was negative). This breaks the "shrink while too big" logic — you could shrink past a valid window or fail to find one that exists. Prefix sum + hashmap has no such monotonicity requirement.

**Q: How do you adapt the template for "subarray sum divisible by k"?**
Track `prefix % k` instead of raw `prefix`. Two prefixes with the same remainder mod `k` mean the subarray between them has a sum divisible by `k` (since `(a - b) % k == 0` iff `a % k == b % k`). Watch out for negative remainders in languages where `%` can return negative values for negative operands (Python's `%` always returns non-negative for positive `k`, but other languages like Java/C++ may not — normalize with `((x % k) + k) % k`).

**Q: What's the difference between "count subarrays" and "find the maximum length subarray" in terms of what you store in the hashmap?**
For *counting*, the hashmap stores `prefix_value -> count of occurrences` (a `defaultdict(int)`), because multiple earlier indices can have the same prefix value, each contributing one valid subarray. For *maximum length*, the hashmap stores `prefix_value -> earliest index where it occurred` (a regular dict, only the first occurrence matters, since you want the longest — i.e., earliest-starting — subarray for a given prefix match).

**Q: How would you extend prefix sums to 2D (range sum of a submatrix)?**
Build a 2D prefix array where `prefix[i][j] = sum of all cells (r, c)` with `r < i` and `c < j` — i.e., the sum of the submatrix from `(0,0)` to `(i-1, j-1)`. Compute it via `prefix[i][j] = matrix[i-1][j-1] + prefix[i-1][j] + prefix[i][j-1] - prefix[i-1][j-1]` (inclusion-exclusion to avoid double-counting the overlap). A range query for rows `[r1,r2]` and cols `[c1,c2]` is `prefix[r2+1][c2+1] - prefix[r1][c2+1] - prefix[r2+1][c1] + prefix[r1][c1]`.

**Q: When would you choose a Fenwick tree over a prefix sum array?**
When the array is **mutated** between queries (point updates: `nums[i] = new_value`). A plain prefix sum array requires O(n) to recompute everything after a single update (everything from index `i` onward shifts). A Fenwick tree (Binary Indexed Tree) supports both point updates and prefix-sum queries in O(log n) each, at the cost of slightly more complex implementation and O(n) extra space (similar to the prefix array's space, but structured as an implicit tree).

**Q: Can prefix sum be combined with binary search?**
Yes — if the array is non-negative, the prefix sum array is *monotonically non-decreasing*, which means you can binary search over it. For example, "find the smallest subarray length with sum >= target" can binary search for the first `prefix[j]` that is `>= prefix[i] + target`, for each `i`. (Though for non-negative arrays, sliding window is usually simpler and equally efficient.)

**Q: What's the "prefix XOR" variant, and when is it used?**
Same structure as prefix sum, but using XOR (`prefix[i] = nums[0] XOR nums[1] XOR ... XOR nums[i-1]`). The key property is `a XOR a = 0`, so `prefix[j] XOR prefix[i] = nums[i] XOR ... XOR nums[j-1]` (the XOR of the subarray). Used for problems like "find the XOR of a range" or "count subarrays with a given XOR value" — same hashmap structure as sum-based problems, but with XOR as the combining operator.

**Q: How do you compute "Product of Array Except Self" without division, using a prefix-style approach?**
Compute `prefix_product[i] = product(nums[0..i-1])` (product of everything to the left of `i`) and `suffix_product[i] = product(nums[i+1..n-1])` (product of everything to the right). The answer for index `i` is `prefix_product[i] * suffix_product[i]`. This avoids division (which would fail if any element is 0) and can be done in O(1) extra space by computing the suffix product in a second pass that overwrites the output array (which initially holds the prefix products).

**Q: What's the time complexity of building a prefix sum array, and is it ever worth it for a single query?**
Building is O(n). For a *single* range-sum query, this is no better than just summing the range directly (also O(n) in the worst case for a large range). Prefix sums pay off when there are **multiple queries** on a **static** array — O(n) to build once, then O(1) per query, versus O(n) per query without precomputation. If asked "what if there's only one query," say so explicitly — it signals you understand the amortization argument.

**Q: How does the prefix-sum-with-hashmap technique generalize beyond "sum"? What's the underlying principle?**
The general principle: if a property `P` of a range `[i, j]` can be expressed as `f(cumulative(j)) op f(cumulative(i))` for some combinable/invertible operation (sum with subtraction, XOR with XOR, product with division when no zeros), then you can precompute `cumulative` values and use a hashmap to find pairs `(i, j)` satisfying the target relationship in O(n). This generalizes to sums, XORs, parity counts (treat as sums mod 2), and products (with care for zeros/negatives).
