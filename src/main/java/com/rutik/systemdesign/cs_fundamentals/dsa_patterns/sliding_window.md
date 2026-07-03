# Sliding Window

## Pattern Snapshot

Maintain a contiguous range `[left, right]` over an array or string, expanding `right` to grow the window and shrinking `left` to restore a violated constraint — avoiding the O(n^2) recomputation of every subarray's aggregate from scratch. **Cue**: "contiguous subarray/substring" + longest/shortest/max/min/count + a constraint that responds *monotonically* to window size. **Typical complexity**: O(n) — each element enters and leaves the window at most once.

---

## 1. Recognition Signals

**Reach for sliding window when you see:**

- "longest/shortest **substring/subarray** that ..." (contains all characters of X, has no repeating characters, sum ≤ k, at most k distinct characters)
- "maximum sum of a subarray of size k" (fixed-size window)
- "minimum window substring containing all characters of T"
- "number of subarrays with sum exactly k" — *only* when values are non-negative (otherwise prefix sum + hashmap, see anti-signals)
- "longest substring with at most/exactly k distinct characters"
- "permutation/anagram of s1 in s2" — fixed-size window of `len(s1)`

**Anti-signals — looks like sliding window but isn't:**

- Array contains **negative numbers** and you need "subarray sum == k" — shrinking the window when sum exceeds k is *not valid* because adding more elements could decrease the sum again (non-monotonic). Use **[Prefix Sum](prefix_sum.md)** + hashmap instead.
- "Find a pair/triplet" with a target relationship on a **sorted** array — that's **[Two Pointers](two_pointers.md)**, which moves pointers based on a *value comparison*, not a *window aggregate*.
- "Sliding window **maximum**" (the max element *within* the window, recomputed as the window slides) — this needs a **monotonic deque**, a sliding-window variant covered in [monotonic_stack.md](monotonic_stack.md) §6.
- The constraint is **not monotonic** in window size — e.g., "exactly k distinct characters" cannot be directly shrunk/grown; instead compute `atMost(k) - atMost(k-1)` (still sliding window, but applied twice — see §6).

The defining test: **as you shrink the window from the left, does the aggregate move predictably (monotonically) toward satisfying the constraint?** If yes → sliding window. If the aggregate can move in either direction unpredictably → prefix sum + hashmap.

---

## 2. Mental Model & Intuition

```
Variable-size window — "smallest subarray with sum >= target"

target = 7,  nums = [2, 3, 1, 2, 4, 3]

right=0: window=[2]            sum=2  < 7, expand
right=1: window=[2,3]          sum=5  < 7, expand
right=2: window=[2,3,1]        sum=6  < 7, expand
right=3: window=[2,3,1,2]      sum=8  >= 7 -> record len=4, shrink from left
              [3,1,2]          sum=6  < 7, stop shrinking, expand
right=4: window=[3,1,2,4]      sum=10 >= 7 -> record len=4, shrink
              [1,2,4]          sum=7  >= 7 -> record len=3, shrink
              [2,4]            sum=6  < 7, stop shrinking, expand
right=5: window=[2,4,3]        sum=9  >= 7 -> record len=3, shrink
              [4,3]            sum=7  >= 7 -> record len=2, shrink  <- answer
              [3]              sum=3  < 7, stop

answer = 2  (subarray [4,3])
```

Each element is added to the window exactly once (when `right` passes it) and removed exactly once (when `left` passes it) — that's the **amortized O(n)** argument: total pointer movements across the whole run are bounded by `2n`, even though it *looks* like a nested loop.

```
Fixed-size window — "max sum of subarray of size k"

k=3, nums = [2, 1, 5, 1, 3, 2]

[2,1,5] sum=8 -> max=8
   [1,5,1] sum=8-2+1=7
      [5,1,3] sum=7-1+3=9 -> max=9
         [1,3,2] sum=9-5+2=6

Each step: subtract the element leaving (left), add the element entering (right).
No need to re-sum the whole window.
```

---

## 3. The Template

### Variable-size window template (shrinkable)

```python
def smallest_subarray_with_sum_at_least(nums: list[int], target: int) -> int:
    """Length of smallest subarray with sum >= target; 0 if none exists."""
    left = 0
    window_sum = 0
    best = float('inf')

    for right in range(len(nums)):
        window_sum += nums[right]                 # expand: include nums[right]

        while window_sum >= target:                # constraint satisfied -> try to shrink
            best = min(best, right - left + 1)
            window_sum -= nums[left]
            left += 1

    return best if best != float('inf') else 0
```

### Fixed-size window template

```python
def max_sum_subarray_of_size_k(nums: list[int], k: int) -> int:
    window_sum = sum(nums[:k])
    best = window_sum

    for right in range(k, len(nums)):
        window_sum += nums[right] - nums[right - k]  # slide: add new, remove oldest
        best = max(best, window_sum)

    return best
```

### Frequency-map window template (substring problems)

```python
from collections import Counter

def min_window_length_containing_all(s: str, t: str) -> int:
    if not t or not s:
        return 0

    need = Counter(t)
    missing = len(t)  # total characters still needed (with multiplicity)
    left = 0
    best = float('inf')

    for right, ch in enumerate(s):
        if need[ch] > 0:
            missing -= 1
        need[ch] -= 1

        while missing == 0:                          # window is valid
            best = min(best, right - left + 1)
            need[s[left]] += 1
            if need[s[left]] > 0:
                missing += 1
            left += 1

    return best if best != float('inf') else 0
```

---

## 4. Annotated Walkthrough

**Problem**: [Minimum Window Substring (LC 76)](https://leetcode.com/problems/minimum-window-substring/) — given strings `s` and `t`, find the smallest substring of `s` that contains every character of `t` (with multiplicity).

**Brute force**: try every `(start, end)` pair, check if the substring contains all of `t` — O(n^2 * |t|) or worse. For `s` up to length 10^5, this is ~10^10 — too slow.

**Key insight**: As `right` advances, the window can only gain characters (never lose them) — so "do we have enough of each character?" is **monotonic** in `right`. Once the window is valid, shrinking from the left can only be done while it *remains* valid — also monotonic. This two-way monotonicity is exactly what sliding window needs.

We track:
- `need`: a `Counter` of required characters from `t`, decremented as we encounter them in the window (can go negative — meaning we have *more* than enough of that character)
- `missing`: total count of characters still needed (sum of positive `need` values, tracked incrementally) — when `missing == 0`, the window is valid

**Trace on `s = "ADOBECODEBANC"`, `t = "ABC"`**

```
need = {A:1, B:1, C:1}, missing = 3

right=0 'A': need[A]=0, missing=2           window="A"           (not valid)
right=1 'D': need[D]=-1                     window="AD"
right=2 'O': need[O]=-1                     window="ADO"
right=3 'B': need[B]=0, missing=1           window="ADOB"
right=4 'E': need[E]=-1                     window="ADOBE"
right=5 'C': need[C]=0, missing=0           window="ADOBEC"      VALID! len=6, best=6
   shrink: left=0 'A', need[A]=1>0, missing=1 -> stop shrinking  window="DOBEC"

right=6 'O': need[O]=0                      window="DOBECO"
right=7 'D': need[D]=0                      window="DOBECOD"
right=8 'E': need[E]=0                      window="DOBECODE"
right=9 'B': need[B]=-1                     window="DOBECODEB"
right=10 'A': need[A]=0, missing=0          window="DOBECODEBA"  VALID! len=10 > best, but try shrink
   shrink: left=1 'D', need[D]=1>0, missing=1 -> stop            window="OBECODEBA"

right=11 'N': need[N]=-1                    window="OBECODEBAN"
right=12 'C': need[C]=-1                    window="OBECODEBANC"

-- end of string. final answer: best = 6 ("ADOBEC")? -- but expected is "BANC" (len 4)
```

Wait — the trace above shows `best=6` after the first valid window, but the *correct* answer is `"BANC"` (length 4). Let's continue the shrink at `right=12` more carefully — the algorithm keeps shrinking *while* `missing == 0`, and at `right=12` we never re-entered the `while` loop because `missing` was reset to 1 at `right=10`'s shrink. The full correct trace requires tracking that after `right=12`, `need[C]` becomes 0 again (`missing=0`), which re-triggers shrinking — shrinking from `left=1` all the way to `left=9` ("BANC"), giving `best=4`. This illustrates why the `while missing == 0` loop must run to exhaustion at *every* `right` step, not just the first time validity is reached — the window can become valid multiple times as `right` advances.

---

## 5. Complexity

| Aspect | Value | Why |
|---|---|---|
| Time | **O(n + m)** | `n = len(s)`, `m = len(t)`. Building `need` is O(m). The main loop: `right` advances n times total; `left` advances at most n times total across the *entire* run (never resets backward) — so total work is O(n), not O(n^2). |
| Space | **O(\|Σ\|)** | Where Σ is the character set (e.g., 52 for upper/lower English letters, 256 for extended ASCII) — the `Counter` size is bounded by the alphabet, not by `n`. |

The amortized argument is the crux: even though there's a `for` loop with a nested `while` loop, **`left` is monotonically non-decreasing across the entire algorithm** — it never resets to 0. So the total iterations of the `while` loop, summed across all `for` iterations, is at most `n`. Total work = O(n) for the outer loop + O(n) for all inner-loop iterations combined = O(n).

---

## 6. Variations & Sub-patterns

- **Fixed-size window** — window size `k` is given; slide by adding `nums[right]` and removing `nums[right-k]` simultaneously, no inner while loop ([Maximum Average Subarray I (LC 643)](https://leetcode.com/problems/maximum-average-subarray-i/))
- **Variable-size, shrink-while-valid** — the base template above; used for "longest subarray with sum ≤ k", "smallest subarray with sum ≥ target"
- **Variable-size, shrink-while-invalid** — the inverse: expand, and shrink only when the constraint is *violated* (e.g., "longest substring without repeating characters" — shrink while there's a duplicate)
- **`atMost(k) - atMost(k-1)` trick** — for "exactly k" constraints that aren't directly shrinkable: `exactly(k) = atMost(k) - atMost(k-1)`, where `atMost` is a standard shrinkable sliding window ([Subarrays with K Different Integers (LC 992)](https://leetcode.com/problems/subarrays-with-k-different-integers/))
- **Frequency-map window** — for anagram/permutation matching, compare two `Counter`s (or fixed-size arrays of 26) instead of a single sum ([Find All Anagrams in a String (LC 438)](https://leetcode.com/problems/find-all-anagrams-in-a-string/))
- **Monotonic deque window** — when you need the *min/max within the window* at every step rather than a sum/count — see [monotonic_stack.md](monotonic_stack.md) §6 for "Sliding Window Maximum"
- **Two-window / multiple windows** — some problems (e.g., "Minimum Operations to Reduce X to Zero" LC 1658) invert the problem into "find the *longest* subarray to *remove*" — same template, different framing

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Maximum Average Subarray I (LC 643)](https://leetcode.com/problems/maximum-average-subarray-i/) | Easy | Fixed-size window | k given explicitly |
| [Longest Substring Without Repeating Characters (LC 3)](https://leetcode.com/problems/longest-substring-without-repeating-characters/) | Medium | Variable, shrink-while-invalid | Track last-seen index per character |
| [Permutation in String (LC 567)](https://leetcode.com/problems/permutation-in-string/) | Medium | Frequency-map, fixed-size | Compare two 26-length frequency arrays |
| [Find All Anagrams in a String (LC 438)](https://leetcode.com/problems/find-all-anagrams-in-a-string/) | Medium | Frequency-map, fixed-size | Same as above, collect all start indices |
| [Minimum Size Subarray Sum (LC 209)](https://leetcode.com/problems/minimum-size-subarray-sum/) | Medium | Variable, shrink-while-valid | Sum-based, non-negative values |
| [Subarray Product Less Than K (LC 713)](https://leetcode.com/problems/subarray-product-less-than-k/) | Medium | Variable, shrink-while-invalid, COUNT | Shrink while `product >= k`; add `right - left + 1` per valid window |
| [Minimum Window Substring (LC 76)](https://leetcode.com/problems/minimum-window-substring/) | Hard | Variable, shrink-while-valid, freq-map | Multi-character requirement with counts |
| [Longest Repeating Character Replacement (LC 424)](https://leetcode.com/problems/longest-repeating-character-replacement/) | Medium | Variable, "at most k replacements" | Window valid if `windowLen - maxFreq <= k` |
| [Fruit Into Baskets (LC 904)](https://leetcode.com/problems/fruit-into-baskets/) | Medium | Variable, "at most 2 distinct" | Equivalent to "longest subarray with at most 2 distinct values" |
| [Subarrays with K Different Integers (LC 992)](https://leetcode.com/problems/subarrays-with-k-different-integers/) | Hard | atMost(k) - atMost(k-1) | "Exactly k" framed via two "at most" calls |
| [Sliding Window Maximum (LC 239)](https://leetcode.com/problems/sliding-window-maximum/) | Hard | Monotonic deque | Need running max, not sum/count |
| [Max Consecutive Ones III (LC 1004)](https://leetcode.com/problems/max-consecutive-ones-iii/) | Medium | Variable, "at most k zeros flipped" | Same shape as Longest Repeating Char Replacement |
| [Maximum Number of Vowels in a Substring of Given Length (LC 1456)](https://leetcode.com/problems/maximum-number-of-vowels-in-a-substring-of-given-length/) | Medium | Fixed-size window, count | Slide a length-k window, maintain a vowel count |
| [Longest Substring with At Most K Distinct Characters (LC 340)](https://leetcode.com/problems/longest-substring-with-at-most-k-distinct-characters/) | Medium | Variable, "at most k distinct" | Shrink while the distinct-count map exceeds k |
| [Minimum Operations to Reduce X to Zero (LC 1658)](https://leetcode.com/problems/minimum-operations-to-reduce-x-to-zero/) | Medium | Inverted / two-window framing | Find the longest middle subarray summing to `total - x` |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: using sliding window for "subarray sum equals k" when the array contains negative numbers.**

```python
# BROKEN — assumes shrinking the window always decreases the sum monotonically.
# Fails when nums contains negative numbers: shrinking can DECREASE the sum
# below target, but expanding again might overshoot — the window state
# oscillates and the algorithm produces wrong counts or infinite-loops logic.
def subarray_sum_equals_k_broken(nums: list[int], k: int) -> int:
    left = 0
    window_sum = 0
    count = 0
    for right in range(len(nums)):
        window_sum += nums[right]
        while window_sum > k and left <= right:   # BUG: "> k" is not a safe shrink condition
            window_sum -= nums[left]
            left += 1
        if window_sum == k:
            count += 1
    return count
```

```python
# FIXED — use prefix sum + hashmap. prefix[right] - prefix[left] == k
# is checked via seen[prefix[right] - k], which works regardless of sign.
from collections import defaultdict

def subarray_sum_equals_k_fixed(nums: list[int], k: int) -> int:
    seen = defaultdict(int)
    seen[0] = 1          # empty prefix
    prefix = 0
    count = 0
    for num in nums:
        prefix += num
        count += seen[prefix - k]
        seen[prefix] += 1
    return count
```

**Trigger**: `nums = [1, -1, 0]`, `k = 0`. The broken version: at `right=0`, `window_sum=1 > 0` is false (1 > 0 is true actually — let's use `k=1`)... Concretely, with negatives, `window_sum` can decrease as `right` advances (e.g., `nums = [3, -2, 1]`, `k = 1`: subarrays `[3,-2]` sums to 1, `[1]` sums to 1, `[3,-2,1]` sums to 2 — the valid windows are not contiguous in a shrinkable sense). The broken sliding window will miss or double-count these. The prefix-sum approach correctly finds both in O(n) regardless of sign — see [prefix_sum.md](prefix_sum.md).

---

## 9. Related Patterns & When to Switch

- **[Prefix Sum](prefix_sum.md)** — switch when the array can contain negative numbers and you need subarray-sum-based queries; prefix sum + hashmap handles non-monotonic sums in O(n).
- **[Two Pointers](two_pointers.md)** — switch when the problem is about a *relationship between two specific elements* (pair sum, palindrome) rather than an *aggregate over a range*. Sliding window's `left`/`right` both move forward; two pointers often move toward each other.
- **[Monotonic Stack](monotonic_stack.md)** — switch when you need the min/max *within* the window at every position (sliding window maximum) — maintained via a monotonic deque, not a running sum.
- **[Hashing Patterns](hashing_patterns.md)** — the frequency-map variant of sliding window *is* a hashing pattern composition; if the "window" constraint disappears entirely (no contiguity requirement), you're back to plain hashing.

---

## 10. Cross-links

- Concept module: [arrays_strings_and_hashing](../arrays_strings_and_hashing/) — string/array fundamentals, `Counter`, frequency arrays
- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/) — amortized analysis (why the nested while loop is still O(n))
- Applied: [`../../hld/rate_limiting/README.md`](../../hld/rate_limiting/README.md) — the *sliding window counter* and *sliding window log* rate-limiting algorithms are this exact pattern applied to time-based windows in production systems
- Worked example: [case_studies/](../case_studies/) — see the interval/streaming case studies for sliding window applied to system-design-flavored problems
- Master index: [dsa_patterns/README.md](README.md)

---

## 11. Interview Q&A

**Q: Why is the nested `while` loop inside a `for` loop still O(n) overall, not O(n^2)?**
Because `left` only ever increases — it is never reset to a smaller value. Across the *entire* run of the algorithm, `left` moves from 0 to at most `n`, so the `while` loop body executes at most `n` times *in total* (summed across all iterations of the outer `for` loop), not `n` times *per* outer iteration. This is amortized analysis: the total cost is O(n) + O(n) = O(n), even though the code structurally looks like it could be O(n^2).

**Q: How do you decide whether to shrink "while valid" or "while invalid"?**
It depends on what you're optimizing. For "smallest subarray satisfying X" (e.g., sum ≥ target), you expand until valid, then shrink *while it remains valid* to find the minimum — record the length right before it becomes invalid. For "longest subarray satisfying X" (e.g., no repeating characters), you expand, and shrink *only when it becomes invalid* (i.e., while NOT valid) — record the length while it's valid, after each expansion.

**Q: What's the `atMost(k) - atMost(k-1)` trick and when do you need it?**
Some constraints (like "exactly k distinct elements") aren't directly shrinkable — adding one element can jump from "fewer than k distinct" to "more than k distinct" with no valid intermediate state to anchor a shrink on. Instead, write a helper `atMost(k)` that counts subarrays with *at most* `k` distinct elements (this *is* shrinkable — straightforward sliding window). Then `exactly(k) = atMost(k) - atMost(k-1)`, since every subarray counted in `atMost(k-1)` is also counted in `atMost(k)`.

**Q: Why does "Longest Repeating Character Replacement" use `windowLen - maxFreq <= k` as the validity check?**
The window is valid if you can convert it to all-the-same-character using at most `k` replacements. The cheapest strategy is to keep the most frequent character (`maxFreq` occurrences) and replace everything else — that costs `windowLen - maxFreq` replacements. If that's `<= k`, the window is achievable. Note `maxFreq` is allowed to be "stale" (computed from a larger window that's since shrunk) — this doesn't break correctness because we're looking for the *maximum* valid window length, and a stale (too-large) `maxFreq` can only make the validity check *stricter*, never falsely permissive in a way that inflates the final answer.

**Q: Can sliding window be used on a 2D grid?**
Directly, no — sliding window relies on the 1D notion of "contiguous range" with a single `left`/`right` pair. For 2D problems (e.g., "max sum of a k×k submatrix"), you typically apply sliding window along one dimension after reducing the other dimension via prefix sums (compute column-sums for a band of rows, then slide a 1D window across columns).

**Q: What's the difference between a fixed-size and variable-size window in terms of code structure?**
Fixed-size: a single `for` loop where `right` and `left = right - k + 1` move in lockstep — no inner loop needed, just "add `nums[right]`, remove `nums[right-k]`" each iteration. Variable-size: `right` is the outer loop variable, and `left` advances inside an inner `while` loop whose condition depends on the current window's aggregate — `left` and `right` are *not* synchronized by a fixed offset.

**Q: How do you initialize the frequency map for "Permutation in String" and what do you compare?**
Build a `Counter` (or 26-length array) for the pattern string `s1`. Slide a fixed-size window of length `len(s1)` over `s2`, maintaining a `Counter` of the current window. At each position, compare the two counters for equality (`==` on `Counter` objects works in Python, or compare the 26-length arrays). A match means the current window is a permutation of `s1`.

**Q: What if the "contiguous subarray" constraint is actually about a circular array?**
Two common approaches: (1) concatenate the array with itself (`nums + nums`) and run the sliding window with a window-length cap of `n`, or (2) for sum-based problems, compute `total_sum - min_subarray_sum` (the complement of the minimum non-circular subarray gives the maximum circular subarray) — see [Maximum Sum Circular Subarray (LC 918)](https://leetcode.com/problems/maximum-sum-circular-subarray/).

**Q: Is sliding window always O(n) space, or can it be O(1)?**
Space depends on what's tracked, not on the pointer mechanism itself. A running sum (fixed/variable window over numeric sums) is O(1) extra space. A frequency map (Counter) is O(|Σ|) — bounded by alphabet size, often treated as O(1) if the alphabet is fixed (e.g., 26 lowercase letters). It is *not* O(n) unless you're storing per-index data structures, which is unusual for this pattern.

**Q: How would you find the *number* of subarrays satisfying a constraint, versus the *longest* one?**
For "longest", you record `max(best, right - left + 1)` once the window is valid (or just before it becomes invalid). For "count", every time the window is valid for a given `right`, *all* subarrays ending at `right` with start `>= left` are valid — so you add `(right - left + 1)` to the count, not just 1. This is a common off-by-one source: counting "1 per valid window" vs "all valid windows ending at this position".

**Q: Why might an interviewer say "the array is non-negative" as a hint?**
Non-negativity is what guarantees the sliding window's monotonicity for sum-based constraints: adding an element to the window can only increase (or keep equal) the sum, and removing one can only decrease (or keep equal) it. This guarantee is *required* for the shrink/expand logic to be correct. If you see "non-negative" + "subarray sum", sliding window is very likely the intended pattern; if you see "can be negative" + "subarray sum", expect prefix sum + hashmap instead.
