# Monotonic Stack (and Monotonic Deque)

## Pattern Snapshot

Maintain a stack whose elements are kept in strictly increasing or decreasing order by popping elements that violate the order *before* pushing a new one. Each pop represents "this element finally found its next greater/smaller element." The deque variant extends this to sliding-window min/max. **Cue**: "next greater/smaller element", "largest rectangle", "stock span", "sliding window maximum". **Typical complexity**: O(n) — each element is pushed and popped at most once.

---

## 1. Recognition Signals

**Reach for a monotonic stack when you see:**

- "Next greater element" / "next smaller element" / "previous greater element" — for *every* element, find the nearest element to its left/right satisfying a comparison
- "Largest rectangle in histogram" / "maximal rectangle in binary matrix" — for each bar, find how far it can extend left and right while remaining the limiting height
- "Daily temperatures" — "how many days until a warmer temperature?"
- "Stock span" — "how many consecutive previous days had price <= today's price?"
- "Trapping rain water" (stack-based alternative to the two-pointer solution)
- "Remove k digits to make the smallest number" / "create the maximum number" — greedy removal maintaining a monotonic digit sequence
- "Sliding window maximum/minimum" — **monotonic deque** (a stack open at both ends)

**Anti-signals — looks like monotonic stack but isn't:**

- "K-th largest/smallest element" — a stack only tracks *relative order with neighbors*, not *global rank*; use **[Top-K Elements](top_k_elements.md)** (heap)
- "Is the array sorted / can it be sorted with k swaps" — different family entirely; monotonic stack answers "next/previous X relative to position", not global sortedness
- The problem wants the **maximum/minimum over a fixed window**, but the window **doesn't slide** (a single static range) — just take `max()`/`min()` directly, O(1) extra structure needed
- "Valid parentheses" / "balanced brackets" — uses a stack, but it's a **matching** stack (LIFO matching of opens/closes), not a monotonic one — no ordering invariant on stack contents

The defining test: **for each element, do you need to find the nearest element (to the left or right) that is greater/smaller than it?** This "nearest greater/smaller" framing is the signature of monotonic stack — and it generalizes to 2D (histogram → maximal rectangle) and to sliding windows (deque).

---

## 2. Mental Model & Intuition

```
"Next Greater Element" -- decreasing stack

  nums = [2, 1, 2, 4, 3]
  result = [4, 2, 4, -1, -1]   (next greater for each index, -1 if none)

  stack holds INDICES, and nums[stack] is kept DECREASING (top to bottom
  is increasing -- i.e., the stack, read from bottom to top, has
  decreasing values... let's just trace it)

  i=0 (val=2): stack=[] -> push 0.            stack=[0] (vals: [2])
  i=1 (val=1): nums[1]=1 < nums[stack_top]=2 -> push 1.
               stack=[0,1] (vals: [2,1])  -- still decreasing top-to-bottom? [2,1] yes, 1<2
  i=2 (val=2): nums[2]=2 >= nums[stack_top]=1 (nums[1]=1)
               -> POP 1: result[1] = nums[2] = 2  (found next greater for index 1!)
               nums[2]=2 >= nums[stack_top]=2 (nums[0]=2)? 2>=2 -> POP 0: result[0]=2
               stack=[] -> push 2.            stack=[2] (vals: [2])
  i=3 (val=4): nums[3]=4 >= nums[stack_top]=2 (nums[2]=2)
               -> POP 2: result[2] = 4
               stack=[] -> push 3.            stack=[3] (vals: [4])
  i=4 (val=3): nums[4]=3 < nums[stack_top]=4 -> push 4.
               stack=[3,4] (vals: [4,3])

  End of array. Remaining stack indices [3,4] have no next greater -> result stays -1.

  result = [2, 2, 4, -1, -1]
```

Wait — let me double check against the expected `[4, 2, 4, -1, -1]`. At `i=0`, after popping, `result[0] = nums[2] = 2`. But the array is `[2,1,2,4,3]` — for index 0 (value 2), is there a value to its right strictly greater than 2? Yes: `4` at index 3. So `result[0]` should be `4`, not `2`. The trace above incorrectly resolved index 0 too early — **index 0's next-greater is `nums[2]=2`, but `2 >= 2` is not `2 > 2`**. This depends on whether the problem wants *strictly* greater or *greater-or-equal*. Using **strictly greater** (`nums[i] > nums[stack_top]`, i.e., pop only when strictly greater): at `i=2` (val=2), `nums[2]=2 > nums[1]=1` → pop index 1, `result[1]=2`. Then `nums[2]=2 > nums[0]=2`? **No** (`2 > 2` is False) → stop popping, push index 2. Stack is now `[0, 2]`. Continue: `i=3` (val=4): `4 > nums[2]=2` → pop 2, `result[2]=4`. `4 > nums[0]=2` → pop 0, `result[0]=4`. Push 3. `i=4` (val=3): `3 > nums[3]=4`? No → push 4. End: `result = [4, 2, 4, -1, -1]` ✓ — matches expected. **The lesson**: the strict-vs-non-strict comparison in the pop condition changes the answer — always clarify "strictly greater" vs "greater or equal" with the interviewer.

```
Sliding Window Maximum -- monotonic DEQUE (decreasing values)

  nums = [1,3,-1,-3,5,3,6,7], k=3

  deque holds INDICES, nums[deque] kept decreasing front-to-back.
  Front of deque = index of current window's maximum.

  i=0 (1): deque=[0]
  i=1 (3): nums[1]=3 > nums[0]=1 -> pop 0. deque=[1]
  i=2 (-1): nums[2]=-1 < nums[1]=3 -> push. deque=[1,2]. window=[0,1,2] complete.
            result: nums[deque[0]] = nums[1] = 3
  i=3 (-3): push (smaller than tail). deque=[1,2,3].
            check front: deque[0]=1, is 1 <= i-k=0? 1<=0 false, front still valid.
            result: nums[1]=3
  i=4 (5): pop 3,2,1 (all < 5). deque=[4]. result: nums[4]=5
  i=5 (3): push. deque=[4,5]. result: nums[4]=5
  i=6 (6): pop 5,4 (both <6). deque=[6]. result: nums[6]=6
  i=7 (7): pop 6. deque=[7]. result: nums[7]=7

  results = [3,3,5,5,6,7]
```

---

## 3. The Template

### Next Greater Element (to the right, strictly greater)

```python
def next_greater_elements(nums: list[int]) -> list[int]:
    n = len(nums)
    result = [-1] * n
    stack: list[int] = []          # holds indices; nums[stack] strictly decreasing

    for i in range(n):
        while stack and nums[i] > nums[stack[-1]]:
            result[stack.pop()] = nums[i]
        stack.append(i)

    return result
```

### Daily Temperatures (distance to next greater)

```python
def daily_temperatures(temperatures: list[int]) -> list[int]:
    n = len(temperatures)
    result = [0] * n
    stack: list[int] = []

    for i, temp in enumerate(temperatures):
        while stack and temp > temperatures[stack[-1]]:
            prev_index = stack.pop()
            result[prev_index] = i - prev_index
        stack.append(i)

    return result
```

### Sliding Window Maximum (monotonic deque)

```python
from collections import deque

def max_sliding_window(nums: list[int], k: int) -> list[int]:
    dq: deque[int] = deque()       # holds indices; nums[dq] strictly decreasing
    result = []

    for i, num in enumerate(nums):
        while dq and nums[dq[-1]] < num:
            dq.pop()                # remove smaller elements from the back
        dq.append(i)

        if dq[0] <= i - k:           # front index has fallen out of the window
            dq.popleft()

        if i >= k - 1:
            result.append(nums[dq[0]])

    return result
```

---

## 4. Annotated Walkthrough

**Problem**: [Largest Rectangle in Histogram (LC 84)](https://leetcode.com/problems/largest-rectangle-in-histogram/) — given heights of histogram bars (width 1 each), find the area of the largest rectangle.

**Brute force**: for each bar `i`, expand left and right while `height >= heights[i]`, compute `width * heights[i]` — O(n^2) (or O(n^3) if you also vary the right boundary independently).

**Key insight**: for each bar `i`, the largest rectangle *with height `heights[i]`* extends from the **first bar to its left that is shorter** (exclusive) to the **first bar to its right that is shorter** (exclusive). That's exactly "previous smaller element" and "next smaller element" — a monotonic stack computes both in O(n). A clever single-pass formulation: maintain an **increasing** stack of indices; when a shorter bar arrives, it triggers the "next smaller" resolution for everything taller than it currently on the stack.

```python
def largest_rectangle_area(heights: list[int]) -> int:
    stack: list[int] = []          # indices; heights[stack] strictly increasing
    max_area = 0

    for i, h in enumerate(heights + [0]):  # sentinel 0 forces final flush
        while stack and heights[stack[-1]] >= h:
            height = heights[stack.pop()]
            # width: from the element AFTER the new stack top, to i-1
            width = i if not stack else i - stack[-1] - 1
            max_area = max(max_area, height * width)
        stack.append(i)

    return max_area
```

**Trace on `heights = [2, 1, 5, 6, 2, 3]` (with appended sentinel `0`)**

```
i=0 (h=2): stack=[] -> push 0.                 stack=[0] (heights: [2])
i=1 (h=1): heights[0]=2 >= 1 -> POP 0.
           width = i (stack empty) = 1.  area = 2*1 = 2.  max_area=2
           stack=[] -> push 1.                 stack=[1] (heights: [1])
i=2 (h=5): heights[1]=1 < 5 -> push 2.         stack=[1,2] (heights: [1,5])
i=3 (h=6): heights[2]=5 < 6 -> push 3.         stack=[1,2,3] (heights: [1,5,6])
i=4 (h=2): heights[3]=6 >= 2 -> POP 3.
           width = i - stack[-1] - 1 = 4 - 2 - 1 = 1. area = 6*1=6. max_area=6
           heights[2]=5 >= 2 -> POP 2.
           width = i - stack[-1] - 1 = 4 - 1 - 1 = 2. area = 5*2=10. max_area=10
           heights[1]=1 < 2 -> push 4.         stack=[1,4] (heights: [1,2])
i=5 (h=3): heights[4]=2 < 3 -> push 5.         stack=[1,4,5] (heights: [1,2,3])
i=6 (h=0, sentinel): heights[5]=3>=0 -> POP 5.
           width = i - stack[-1] - 1 = 6-4-1=1. area=3*1=3. max_area=10
           heights[4]=2>=0 -> POP 4.
           width = i - stack[-1] - 1 = 6-1-1=4. area=2*4=8. max_area=10
           heights[1]=1>=0 -> POP 1.
           width = i (stack empty) = 6. area=1*6=6. max_area=10
           stack=[] -> push 6.

max_area = 10  (the rectangle of height 5, width 2, covering bars [5,6] at indices 2-3)
```

The `width` formula `i - stack[-1] - 1` captures "everything strictly between the new stack top (the previous smaller element) and the current position `i` (the next smaller element)" — exactly the maximal horizontal extent at the popped bar's height.

---

## 5. Complexity

| Operation | Time | Space |
|---|---|---|
| Next greater/smaller element (single pass) | O(n) | O(n) (stack) |
| Largest rectangle in histogram | O(n) | O(n) |
| Sliding window maximum (deque) | O(n) | O(k) (deque holds at most k indices) |

The O(n) bound (despite the `while` loop nested in the `for` loop) follows the same amortized argument as cyclic sort and sliding window: **each element is pushed exactly once and popped at most once**, so total push+pop operations are bounded by `2n`.

---

## 6. Variations & Sub-patterns

- **Next/previous greater/smaller element** — the base template; direction (left-to-right or right-to-left scan) and comparison (`>`, `>=`, `<`, `<=`) determine which of the four variants you get ([Next Greater Element I (LC 496)](https://leetcode.com/problems/next-greater-element-i/))
- **Distance to next greater** — instead of the *value*, return the *index distance* ([Daily Temperatures (LC 739)](https://leetcode.com/problems/daily-temperatures/))
- **Stock span** — "previous greater or equal" framed as a running count ([Online Stock Span (LC 901)](https://leetcode.com/problems/online-stock-span/))
- **Largest rectangle / maximal rectangle** — "previous/next smaller" used to bound a rectangle's width; extends to 2D by treating each row as a histogram ([Maximal Rectangle (LC 85)](https://leetcode.com/problems/maximal-rectangle/))
- **Trapping rain water (stack variant)** — pop when a taller bar is found, computing trapped water layer by layer ([Trapping Rain Water (LC 42)](https://leetcode.com/problems/trapping-rain-water/) — also solvable with two pointers, see [two_pointers.md](two_pointers.md))
- **Monotonic deque for sliding window max/min** — the deque variant; front of deque is always the current window's extremum ([Sliding Window Maximum (LC 239)](https://leetcode.com/problems/sliding-window-maximum/))
- **Greedy digit removal** — maintain an increasing (or decreasing) stack of digits, popping when a smaller digit arrives and removals remain ([Remove K Digits (LC 402)](https://leetcode.com/problems/remove-k-digits/))
- **Circular array variants** — "next greater element II" iterates `2*n` times (mod `n`) to handle wraparound ([Next Greater Element II (LC 503)](https://leetcode.com/problems/next-greater-element-ii/))

---

## 7. Problem Bank

| Problem | Difficulty | Variation | Recognition cue / twist |
|---|---|---|---|
| [Next Greater Element I (LC 496)](https://leetcode.com/problems/next-greater-element-i/) | Easy | Base template | Map results back via a lookup of `nums2` |
| [Daily Temperatures (LC 739)](https://leetcode.com/problems/daily-temperatures/) | Medium | Distance, not value | Store index, return `i - prev_index` |
| [Online Stock Span (LC 901)](https://leetcode.com/problems/online-stock-span/) | Medium | Streaming "previous greater or equal" | Stack stores `(price, span)` pairs |
| [Next Greater Element II (LC 503)](https://leetcode.com/problems/next-greater-element-ii/) | Medium | Circular array | Iterate `2n` times, index `i % n` |
| [Largest Rectangle in Histogram (LC 84)](https://leetcode.com/problems/largest-rectangle-in-histogram/) | Hard | Width via prev/next smaller | Append sentinel 0 to flush stack |
| [Maximal Rectangle (LC 85)](https://leetcode.com/problems/maximal-rectangle/) | Hard | 2D extension | Per-row histogram + LC 84 |
| [Trapping Rain Water (LC 42)](https://leetcode.com/problems/trapping-rain-water/) | Hard | Layer-by-layer water calc | Stack of "valleys"; also two-pointer solvable |
| [Remove K Digits (LC 402)](https://leetcode.com/problems/remove-k-digits/) | Medium | Greedy increasing stack | Pop while top > current AND k > 0 |
| [Sliding Window Maximum (LC 239)](https://leetcode.com/problems/sliding-window-maximum/) | Hard | Monotonic deque | Front of deque = current max |
| [132 Pattern (LC 456)](https://leetcode.com/problems/132-pattern/) | Medium | Decreasing stack + tracking a "second max" | Subtle — track candidate "3" values while scanning right-to-left |
| [Asteroid Collision (LC 735)](https://leetcode.com/problems/asteroid-collision/) | Medium | Stack as pairwise-collision simulation | Not "next greater" framed, but same push/pop-while-condition shape: pop smaller right-movers while the new left-mover survives |
| [Final Prices With a Special Discount in a Shop (LC 1475)](https://leetcode.com/problems/final-prices-with-a-special-discount-in-a-shop/) | Easy | Next smaller-or-equal element | Plain next-smaller template applied as a discount |
| [Remove Duplicate Letters (LC 316)](https://leetcode.com/problems/remove-duplicate-letters/) | Medium | Greedy increasing stack | Pop while top > current AND top appears again later; `seen` set guards duplicates |
| [Sum of Subarray Minimums (LC 907)](https://leetcode.com/problems/sum-of-subarray-minimums/) | Medium | Prev/next smaller, contribution counting | Each element's contribution = (left span)·(right span)·value |
| [Shortest Subarray with Sum at Least K (LC 862)](https://leetcode.com/problems/shortest-subarray-with-sum-at-least-k/) | Hard | Monotonic deque over prefix sums | Increasing deque of prefix sums; pop front when `prefix[i]-prefix[front] >= k` |

---

## 8. Common Mistakes (BROKEN -> FIX)

**Mistake: forgetting that the stack stores INDICES, not VALUES — needed for distance-based answers and to detect "is this the same element."**

```python
# BROKEN — stores VALUES on the stack. "Daily Temperatures" needs the
# DISTANCE (i - prev_index), but with values on the stack, you cannot
# recover prev_index after popping.
def daily_temperatures_broken(temperatures: list[int]) -> list[int]:
    n = len(temperatures)
    result = [0] * n
    stack: list[int] = []          # BUG: will hold temperature VALUES

    for i, temp in enumerate(temperatures):
        while stack and temp > stack[-1]:
            stack.pop()
            result[i] = 1   # WRONG: no way to know which earlier index this corresponds to
        stack.append(temp)

    return result
```

```python
# FIXED — store INDICES on the stack. Compare temperatures[stack[-1]]
# for the ordering check, but the stack itself holds indices, so
# popping gives you prev_index directly, enabling i - prev_index.
def daily_temperatures_fixed(temperatures: list[int]) -> list[int]:
    n = len(temperatures)
    result = [0] * n
    stack: list[int] = []          # FIX: holds INDICES

    for i, temp in enumerate(temperatures):
        while stack and temp > temperatures[stack[-1]]:
            prev_index = stack.pop()
            result[prev_index] = i - prev_index
        stack.append(i)

    return result
```

**Trigger**: `temperatures = [73, 74, 75]`. Broken version: `i=0`, stack=[] → push `73`. `i=1`, `74 > 73` → pop, `result[1] = 1` (hardcoded, wrong — should be `result[0] = 1`, since day 0's next warmer day is day 1, distance 1). The broken version writes to `result[i]` (the *current* index) instead of the index that was popped, and can't even compute the correct distance because the popped value `73` doesn't tell you it came from index 0. The fixed version correctly sets `result[0] = 1 - 0 = 1`.

---

## 9. Related Patterns & When to Switch

- **[Sliding Window](sliding_window.md)** — the monotonic *deque* variant directly extends sliding window to track running min/max; if your sliding window problem only needs a sum/count (not a min/max), you don't need the deque — a running aggregate suffices.
- **[Two Pointers](two_pointers.md)** — "Trapping Rain Water" and similar problems often have *both* a monotonic-stack solution and a two-pointer solution with `left_max`/`right_max`; the two-pointer version is usually O(1) space vs the stack's O(n), but the stack version generalizes more easily to "largest rectangle"-style problems.
- **[Top-K Elements](top_k_elements.md)** — if the question becomes "k-th largest" rather than "next greater", you need *global* ordering (heap), not *local/relative* ordering (stack).
- **[Dynamic Programming](dynamic_programming.md)** — "Largest Rectangle in Histogram" can also be approached with DP (precompute left/right boundaries via DP arrays), but the monotonic stack achieves the same O(n) with less state.

---

## 10. Cross-links

- Concept module: [linked_lists_stacks_and_queues](../linked_lists_stacks_and_queues/) — stack/queue fundamentals, `collections.deque`
- [arrays_strings_and_hashing](../arrays_strings_and_hashing/) — array scanning patterns
- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/) — amortized analysis (push/pop bound)
- Applied: [`../../java/collections_internals/README.md`](../../java/collections_internals/README.md) — `ArrayDeque` as the standard Java stack/deque (avoid legacy `Stack` class, which is synchronized)
- Master index: [dsa_patterns/README.md](README.md)

---

## 11. Interview Q&A

**Q: Why is a monotonic stack O(n) when there's a `while` loop inside a `for` loop?**
Each element is **pushed exactly once** (once per iteration of the outer loop) and **popped at most once** (across the entire algorithm's lifetime, total pops <= total pushes <= n). So the total number of stack operations (pushes + pops) is bounded by `2n`, regardless of how the pops are distributed across iterations of the outer loop. This is the same amortized argument used in sliding window and cyclic sort.

**Q: What's the difference between an "increasing" and "decreasing" monotonic stack, and how do you choose?**
A **decreasing stack** (top-to-bottom values decrease, i.e., pop when a *larger* element arrives) is used to find **next greater elements** — when you pop, the new element is the popped one's "next greater". An **increasing stack** (pop when a *smaller* element arrives) finds **next smaller elements**. Choose based on what you're looking for: "next greater" → decreasing stack; "next smaller" / "largest rectangle" (which needs "next/previous *smaller*" to bound width) → increasing stack.

**Q: In "Next Greater Element," does it matter whether you scan left-to-right or right-to-left?**
Both work, but the bookkeeping differs. Left-to-right (as in the template): when you pop an element, the *current* element is its "next greater" — you resolve answers for *past* elements as you go. Right-to-left: the stack represents "candidates for *my* next greater", and when you process element `i`, you pop everything `<= nums[i]` (they can never be the answer for anything to `i`'s left, since `nums[i]` is closer and at least as large), then the new stack top (if any) is `nums[i]`'s next greater, then push `nums[i]`. Both are O(n); left-to-right is more common because it resolves answers eagerly.

**Q: Why does Largest Rectangle in Histogram append a sentinel value of 0?**
The algorithm only computes the area for a bar when a *shorter* bar causes it to be popped. Without a sentinel, bars that are part of an increasing sequence at the *end* of the array (e.g., `[1,2,3]`) are never popped — their rectangles are never computed. Appending a `0` (shorter than any real height) guarantees that every remaining bar on the stack gets popped and evaluated during the final iteration, "flushing" the stack.

**Q: How do you compute the "width" when popping a bar in Largest Rectangle in Histogram?**
`width = i - stack[-1] - 1` if the stack is non-empty after popping (where `stack[-1]` is the new top — the previous *smaller* element's index, and `i` is the current index — the next *smaller* element's index); the `-1` excludes both boundary indices, leaving only the bars strictly between them, which are all `>= ` the popped bar's height. If the stack is empty after popping, the popped bar extends all the way from index 0 to `i-1`, so `width = i`.

**Q: What's a monotonic deque, and how does it differ from a monotonic stack?**
A monotonic deque is "open at both ends": you can push/pop from the back (like a stack, to maintain monotonicity when adding new elements) AND pop from the front (to remove elements that have "expired" — fallen outside a sliding window). A monotonic stack only needs the back operations. The front-popping is what enables "sliding window maximum" — the front of the deque is always the index of the current window's maximum, and it gets evicted once the window slides past it.

**Q: In Sliding Window Maximum, why do we check `dq[0] <= i - k` (expiry) AFTER pushing the new element, not before?**
Order doesn't actually matter for correctness here (the new element's index `i` is always `> i - k`, so pushing it can't itself be "expired"), but checking after is slightly more natural in code flow: first maintain monotonicity (pop smaller elements from the back, then push), then handle window-boundary expiry (pop the front if it's now out of range), then record the result if the window is fully formed (`i >= k-1`). Some implementations check expiry before pushing — both orderings are correct as long as all three steps happen each iteration.

**Q: Can a monotonic stack solve "previous smaller element" and "next smaller element" simultaneously in one pass?**
Yes, for "Largest Rectangle in Histogram" specifically: when bar `j` is popped because bar `i` (current) is smaller, bar `i` is `j`'s "next smaller", AND the new stack top after popping `j` is `j`'s "previous smaller" (because the stack is maintained in increasing order — everything below `j` on the stack is smaller than `j`, and the closest one is the new top). This is why the single-pass histogram algorithm works — both boundaries for each bar's rectangle are determined at the moment that bar is popped.

**Q: How would "Remove K Digits" use a monotonic stack?**
To form the smallest possible number by removing `k` digits, greedily maintain an increasing stack of digits: for each new digit, while the stack's top digit is *greater* than the new digit AND `k > 0` (removals remaining), pop the top (this is a "removal") and decrement `k`. Push the new digit. After processing all digits, if `k > 0` still, remove the last `k` digits (they're the largest remaining, at the end). Finally, strip leading zeros. This greedily ensures the most significant digits are as small as possible.

**Q: What's the "132 Pattern" problem, and why does it need a non-trivial monotonic stack variant?**
"132 Pattern" asks: does there exist `i < j < k` with `nums[i] < nums[k] < nums[j]`? The trick: scan **right to left**, maintaining a decreasing stack AND a variable `third = -infinity` representing the best candidate for "nums[k]" (the "2" in "132"). When popping elements smaller than `nums[i]` from the stack (because they're smaller than the current element, meaning the current element could be a "3" for them), update `third = max(third, popped_value)`. If at any point `nums[i] < third`, you've found `nums[i]` as the "1" with valid "3" and "2" to its right — pattern found. This shows monotonic stacks can track auxiliary "best discarded value" state, not just the stack contents themselves.

**Q: Is the monotonic stack pattern ever combined with binary search?**
Yes — since the stack maintains a sorted (monotonic) sequence of values, you *can* binary search within it. A canonical example is "Longest Increasing Subsequence" (patience sorting): maintain a list of "pile tops" that is sorted, and binary search for where each new element belongs (`bisect_left`) — this is structurally similar to a monotonic stack but the "pop" is replaced with "overwrite at the binary-searched position", giving O(n log n) instead of O(n^2).
