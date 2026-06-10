# Interval and Scheduling Problems

Three core problems: Merge Intervals, Meeting Rooms II, and Task Scheduler with Cooldowns.
All three share a common spine — sort first, then greedily process events in order.

---

## Intuition

Intervals represent ownership of a resource over time. Merging intervals means collapsing overlapping claims into the minimal set of distinct spans. Allocating rooms means tracking how many claims are active simultaneously. Scheduling tasks with cooldowns means ensuring no resource (CPU, rate-limited API, worker type) is reused before it is ready.

The key mental model: **convert intervals into sorted events, then sweep a pointer across time**. At each decision point, the greedy choice is either to extend the current span (merge) or to release the earliest-ending resource (rooms) or to pick the highest-priority ready task (scheduler).

The exchange argument — "swapping any two adjacent decisions in the optimal solution never improves it" — is the informal proof that greedy works for all three problems.

---

## 1. Problem Statement & Clarifying Questions

### Problem 1 — Merge Intervals

Given a list of intervals `[[s1,e1],[s2,e2],...]`, merge all overlapping intervals and return the merged list in sorted order. Two intervals `[a,b]` and `[c,d]` overlap if `c <= b` (the second starts before or when the first ends).

**Clarifying questions to ask the interviewer:**

**Q: Are the intervals already sorted?**
A: Not necessarily — assume arbitrary order and handle sorting internally.

**Q: Can intervals be adjacent (touching endpoints, e.g., [1,3] and [3,5])?**
A: Clarify the overlap definition. The convention `c <= b` treats touching as overlapping and merges [1,3]+[3,5] into [1,5]. Some interviewers use `c < b` (strict). Confirm before coding.

**Q: Can intervals have zero length (e.g., [3,3])?**
A: Yes — represent a single point. Handle gracefully; they merge normally.

**Q: Can the list be empty or contain one interval?**
A: Yes — return as-is.

**Q: Are start and end times integers or floats?**
A: Integers unless stated otherwise.

### Problem 2 — Meeting Rooms II

Given a list of meeting intervals `[[start,end],...]`, find the minimum number of conference rooms required so all meetings can run simultaneously without conflicts.

**Clarifying questions:**

**Q: Is a room freed at exactly `end` or just after `end`?**
A: A meeting `[s,e]` occupies the room for `[s, e)` — the room is free at time `e`. A new meeting starting at `e` can reuse the same room. Confirm with interviewer.

**Q: Can two meetings start at exactly the same time?**
A: Yes — each needs its own room.

**Q: Are meetings on the same day (bounded domain) or arbitrary timestamps?**
A: Usually same day; algorithm is identical either way.

### Problem 3 — Task Scheduler with Cooldowns

Given a list of CPU tasks (characters A–Z, each representing a task type), and a non-negative integer `n` representing the cooldown period, find the minimum number of time intervals the CPU needs to finish all tasks. Within each interval, the CPU executes exactly one task or stays idle. Between two executions of the same task type, at least `n` intervals must pass.

**Clarifying questions:**

**Q: Can tasks of different types run back-to-back with no restriction?**
A: Yes — only same-type tasks have a cooldown constraint.

**Q: What is n = 0?**
A: No cooldown; minimum intervals = number of tasks.

**Q: Are task execution times all 1 unit?**
A: Yes — uniform execution time.

**Q: Can we reorder tasks freely?**
A: Yes — find optimal ordering.

---

## 2. Brute Force & Complexity Baseline

### Merge Intervals — O(n^2) brute force

Iterate over every pair `(i, j)` where `i != j`. If they overlap, merge them into a single interval and restart the scan. Repeat until no merges occur.

```
for each pass:
    merged = False
    for i in 0..n:
        for j in i+1..n:
            if overlaps(intervals[i], intervals[j]):
                intervals[i] = merge(intervals[i], intervals[j])
                remove intervals[j]
                merged = True
                break
    if not merged:
        break
```

Time: O(n^2) per pass, up to O(n) passes in the worst case → O(n^3) total.
Space: O(n) for output (in-place mutation).
This is impractical for n > 10,000.

### Meeting Rooms II — O(n^2) brute force

For each meeting, count how many other meetings are running at the same time (i.e., overlap with it). The answer is `max(concurrent_count_at_any_point)`.

```
max_rooms = 0
for each interval A:
    concurrent = 1
    for each interval B != A:
        if overlaps(A, B):
            concurrent += 1
    max_rooms = max(max_rooms, concurrent)
```

Time: O(n^2). Space: O(1).
For a calendar with 10,000 events, this is 10^8 comparisons — about 10 seconds at 10^7 ops/sec in Python.

### Task Scheduler — naive simulation

Generate all permutations of task orderings (O(n!)) and test each for cooldown validity. Return the minimum length. Obviously exponential — only illustrative of the problem space.

---

## 3. Optimal Approach & Key Insight

### Merge Intervals — Sort + Linear Scan

**Key insight**: once intervals are sorted by start time, an interval can only overlap with the immediately preceding merged interval — no earlier interval can reach it because its start is already behind.

Algorithm:
1. Sort by start time: O(n log n).
2. Initialize `result = [intervals[0]]`.
3. For each subsequent interval `curr`:
   - If `curr.start <= result[-1].end`: overlap — extend result[-1].end = max(result[-1].end, curr.end).
   - Otherwise: no overlap — append curr to result.
4. Return result.

Time: O(n log n). Space: O(n) output.

### Meeting Rooms II — Min-Heap on End Times

**Key insight**: we only need to know when the earliest-ending meeting finishes. If the next meeting starts after that ending time, reuse the room; otherwise allocate a new one.

Algorithm:
1. Sort meetings by start time.
2. Maintain a min-heap of end times (one entry per active room).
3. For each meeting `m`:
   - If heap is non-empty and `heap[0] <= m.start`: pop (room is freed) and push `m.end`.
   - Otherwise: push `m.end` (new room needed).
4. Answer is heap size at the end.

Time: O(n log n) — sort + n heap operations each O(log n).
Space: O(n) heap.

### Task Scheduler — Greedy Idle-Count Formula

**Key insight**: the bottleneck is the most frequent task. Place the most frequent task as anchor points separated by cooldown slots. Fill slots with other tasks; any remaining slots are idle.

Let `max_count` = frequency of the most common task.
Let `max_count_tasks` = number of task types that share this maximum frequency.

Minimum intervals formula:
```
idle_slots = (max_count - 1) * n
idle_slots -= sum of min(count, max_count - 1) for all other task types
idle_slots = max(0, idle_slots)
result = len(tasks) + idle_slots
```

This formula derives from: there are `(max_count - 1)` "frames" between the last execution of the most frequent task. Each frame has `n` slots for other tasks or idle. The final frame is just the `max_count_tasks` most-frequent tasks together.

Equivalently: `result = max(len(tasks), (max_count - 1) * (n + 1) + max_count_tasks)`.

The heap-based simulation approach (see §4) is also O(n log n) and more generalizable to weighted tasks.

---

## 4. Implementation

### Merge Intervals

```python
from __future__ import annotations
from typing import List


def merge_intervals(intervals: List[List[int]]) -> List[List[int]]:
    """
    Merge all overlapping intervals.

    Args:
        intervals: List of [start, end] pairs. May be unsorted.

    Returns:
        Sorted list of merged non-overlapping intervals.

    Time:  O(n log n)
    Space: O(n) for output
    """
    if not intervals:
        return []

    # Critical: sort by start time first
    intervals.sort(key=lambda x: x[0])

    merged: List[List[int]] = [intervals[0][:]]  # copy to avoid mutating input

    for curr in intervals[1:]:
        last = merged[-1]
        if curr[0] <= last[1]:          # overlap: curr starts before or when last ends
            last[1] = max(last[1], curr[1])   # extend
        else:
            merged.append(curr[:])

    return merged


# --- Quick verification ---
if __name__ == "__main__":
    tests = [
        ([[1,3],[2,6],[8,10],[15,18]], [[1,6],[8,10],[15,18]]),
        ([[1,4],[4,5]],               [[1,5]]),           # touching endpoints
        ([[1,4],[2,3]],               [[1,4]]),           # containment
        ([],                          []),                # empty
        ([[5,5]],                     [[5,5]]),           # single point
        ([[3,5],[1,2],[2,4]],         [[1,5]]),           # unsorted input
    ]
    for inp, expected in tests:
        result = merge_intervals(inp)
        status = "PASS" if result == expected else "FAIL"
        print(f"{status}: {inp} -> {result} (expected {expected})")
```

---

### BROKEN -> FIX: merging without sorting

**BROKEN — omitting the sort step:**

```python
def merge_intervals_broken(intervals: List[List[int]]) -> List[List[int]]:
    """BROKEN: no sort — produces incorrect results for unsorted input."""
    if not intervals:
        return []

    merged: List[List[int]] = [intervals[0][:]]

    for curr in intervals[1:]:
        last = merged[-1]
        if curr[0] <= last[1]:
            last[1] = max(last[1], curr[1])
        else:
            merged.append(curr[:])

    return merged


# Demonstration of the failure:
intervals = [[3, 5], [1, 2], [2, 4]]
print(merge_intervals_broken(intervals))
# Outputs: [[3, 5], [1, 4]]  -- WRONG: [1,2] and [2,4] were not merged with [3,5]
# Correct: [[1, 5]]
```

The problem: when input is `[[3,5],[1,2],[2,4]]`, the algorithm starts with `[3,5]` as the running interval. Then it sees `[1,2]` — start 1 is not `<= 5`... wait, it is. Actually the check `curr[0] <= last[1]` passes (1 <= 5), so it merges into `[3,5]` → extends last[1] to max(5,2) = 5, producing `[3,5]`. Then `[2,4]` comes — 2 <= 5, extends to max(5,4) = 5. Output: `[[3,5]]`. But the correct merged range should start at 1, not 3. The broken version misses that the merged interval's start should be the minimum start seen.

A subtler broken case: `[[1,2],[5,7],[3,4]]`. Without sorting:
- Start with `[1,2]`.
- `[5,7]`: 5 > 2, append. Result so far: `[[1,2],[5,7]]`.
- `[3,4]`: 3 > 2 (last.end=7, actually 3 <= 7), merges into [5,7] → [5, max(7,4)] = [5,7]. Output: `[[1,2],[5,7]]`.
- But [3,4] should have merged with [1,2] to give [1,4], then [1,4] does NOT overlap [5,7]. Correct output: `[[1,4],[5,7]]`.

**FIX — always sort by start time first:**

```python
def merge_intervals(intervals: List[List[int]]) -> List[List[int]]:
    """FIX: sort by start time before scanning."""
    if not intervals:
        return []
    intervals.sort(key=lambda x: x[0])   # <-- the one essential line
    merged = [intervals[0][:]]
    for curr in intervals[1:]:
        last = merged[-1]
        if curr[0] <= last[1]:
            last[1] = max(last[1], curr[1])
        else:
            merged.append(curr[:])
    return merged
```

The sort guarantees: any interval that could overlap with the current running interval has start time within [running.start, running.end] and will be encountered consecutively. No interval seen after an utter gap can retroactively overlap anything already closed.

---

### Meeting Rooms II

```python
from __future__ import annotations
import heapq
from typing import List


def min_meeting_rooms(intervals: List[List[int]]) -> int:
    """
    Minimum number of conference rooms needed.

    Uses a min-heap keyed on end times.  The heap size at any point equals
    the number of rooms in use.

    Args:
        intervals: List of [start, end] meeting times.

    Returns:
        Minimum rooms required (integer >= 0).

    Time:  O(n log n)  — sort + n heap ops
    Space: O(n)        — heap holds up to n end times
    """
    if not intervals:
        return 0

    # Sort by start time
    intervals_sorted = sorted(intervals, key=lambda x: x[0])

    # Min-heap of end times — each element = end time of a room currently in use
    heap: List[int] = []

    for start, end in intervals_sorted:
        if heap and heap[0] <= start:
            # Earliest-ending room is free by the time this meeting starts
            heapq.heapreplace(heap, end)    # reuse room: pop old end, push new end
        else:
            # No room free — allocate a new one
            heapq.heappush(heap, end)

    return len(heap)


# --- Heap state ASCII trace (see §5) ---
# --- Verification ---
if __name__ == "__main__":
    tests = [
        ([[0,30],[5,10],[15,20]], 2),
        ([[7,10],[2,4]],          1),
        ([[0,10],[10,20]],        1),  # rooms reused when end == next start
        ([[0,10],[0,10],[0,10]], 3),  # three simultaneous meetings
        ([],                      0),
        ([[1,5]],                 1),
    ]
    for inp, expected in tests:
        result = min_meeting_rooms(inp)
        status = "PASS" if result == expected else "FAIL"
        print(f"{status}: {inp} -> {result} (expected {expected})")
```

### BROKEN -> FIX: O(n^2) brute-force room check

**BROKEN — quadratic overlap check:**

```python
def min_meeting_rooms_broken(intervals: List[List[int]]) -> int:
    """
    BROKEN: O(n^2) approach — counts concurrent overlaps for each interval.

    Problems:
    1. O(n^2) time — unusable for n > 50,000.
    2. Double-counts symmetric overlaps, requiring careful logic.
    3. Misses the structure that allows the greedy heap to reuse rooms.
    """
    if not intervals:
        return 0

    max_rooms = 0
    for i, (s1, e1) in enumerate(intervals):
        concurrent = 1
        for j, (s2, e2) in enumerate(intervals):
            if i != j and s2 < e1 and s1 < e2:   # strict overlap check
                concurrent += 1
        max_rooms = max(max_rooms, concurrent)

    return max_rooms

# This counts, for each meeting, how many other meetings overlap it.
# On [[0,30],[5,10],[15,20]]:
#   [0,30] overlaps [5,10] and [15,20]: concurrent = 3. But only 2 rooms are needed!
# The brute force overcounts because [5,10] and [15,20] do NOT overlap each other.
# max_concurrent for [0,30] is 3 only if all three run simultaneously — they don't.
# This approach is WRONG for this reason: it doesn't verify simultaneous overlap.
# (A correct O(n^2) would check at every event point, not per-interval.)
```

**FIX — min-heap on end times:**

```python
def min_meeting_rooms(intervals: List[List[int]]) -> int:
    """FIX: O(n log n) heap approach — see full implementation above."""
    if not intervals:
        return 0
    intervals_sorted = sorted(intervals, key=lambda x: x[0])
    heap: List[int] = []
    for start, end in intervals_sorted:
        if heap and heap[0] <= start:
            heapq.heapreplace(heap, end)
        else:
            heapq.heappush(heap, end)
    return len(heap)
```

---

### Task Scheduler with Cooldowns

```python
from __future__ import annotations
import heapq
from collections import Counter, deque
from typing import List


def task_scheduler_formula(tasks: List[str], n: int) -> int:
    """
    Greedy idle-count formula: O(n) time.

    Args:
        tasks: List of task type characters.
        n:     Cooldown period. Same task type must be separated by >= n intervals.

    Returns:
        Minimum total intervals to execute all tasks.
    """
    counts = Counter(tasks)
    max_count = max(counts.values())
    # How many task types share the maximum frequency?
    max_count_tasks = sum(1 for c in counts.values() if c == max_count)

    # Frame model:
    # (max_count - 1) complete frames of size (n + 1), plus a final partial frame.
    # Each complete frame can absorb n other task slots before idling.
    return max(len(tasks), (max_count - 1) * (n + 1) + max_count_tasks)


def task_scheduler_simulation(tasks: List[str], n: int) -> int:
    """
    Max-heap + cooldown queue simulation: O(m log m) where m = number of distinct task types.

    More generalizable — can be extended to weighted tasks or variable execution times.

    Args:
        tasks: List of task type characters.
        n:     Cooldown period.

    Returns:
        Minimum total intervals to execute all tasks.
    """
    counts = Counter(tasks)
    # Max-heap: Python only has min-heap, so store negated counts
    heap: List[int] = [-c for c in counts.values()]
    heapq.heapify(heap)

    time = 0
    # cooldown queue: (available_at_time, negated_count_remaining)
    cooldown_queue: deque[tuple[int, int]] = deque()

    while heap or cooldown_queue:
        time += 1

        # Release tasks whose cooldown has expired
        if cooldown_queue and cooldown_queue[0][0] <= time:
            _, neg_count = cooldown_queue.popleft()
            heapq.heappush(heap, neg_count)

        if heap:
            neg_count = heapq.heappop(heap)
            neg_count += 1  # execute one unit: count decreases by 1 (less negative)
            if neg_count < 0:
                # Task still has remaining executions — put in cooldown
                cooldown_queue.append((time + n + 1, neg_count))
            # else: task type exhausted, do not re-queue
        # else: CPU idles this interval (heap empty, tasks in cooldown)

    return time


# --- Verification ---
if __name__ == "__main__":
    tests = [
        (["A","A","A","B","B","B"], 2, 8),   # classic: ABABAB + 2 idles? No: ABCABC or ABABXAB
        (["A","A","A","B","B","B"], 0, 6),   # no cooldown: 6 tasks = 6 intervals
        (["A","A","A","A","A","A","B","C","D","E","F","G"], 2, 16),
        (["A","A","B","B"], 2, 5),
        (["A"], 5, 1),
    ]
    for tasks_in, n_in, expected in tests:
        r1 = task_scheduler_formula(tasks_in, n_in)
        r2 = task_scheduler_simulation(tasks_in, n_in)
        s1 = "PASS" if r1 == expected else "FAIL"
        s2 = "PASS" if r2 == expected else "FAIL"
        print(f"formula={s1}({r1}) sim={s2}({r2}) | tasks={tasks_in}, n={n_in}, expected={expected}")
```

---

## 5. Complexity Analysis & Tradeoffs

### Merge Intervals

```
Operation           Time            Space
Sort                O(n log n)      O(log n) — timsort in Python
Linear scan         O(n)            O(n) — output list
-------------------------------------------------
Total               O(n log n)      O(n)
```

The sort dominates. If input arrives pre-sorted (e.g., Google Calendar stores events ordered by start), the linear scan alone is O(n). For online (streaming) insertion, maintain a sorted structure (balanced BST / SortedList) for O(log n) per insert + O(k) to merge k overlapping intervals.

### Meeting Rooms II

```
Operation           Time            Space
Sort                O(n log n)      O(log n) timsort
n heap ops          O(n log n)      O(n) heap
-------------------------------------------------
Total               O(n log n)      O(n)
```

Alternative: coordinate compression (line sweep). Create events `(time, type)` where type is +1 for start and -1 for end. Sort events (with -1 before +1 at ties to allow room reuse). Sweep and track running sum. Peak running sum = answer. Same asymptotic complexity, slightly different constant.

### Task Scheduler

```
Approach            Time            Space       Notes
Formula             O(T)            O(26)=O(1)  T = len(tasks); only for uniform task times
Heap simulation     O(T log 26)     O(26)=O(1)  26 distinct task types max for A-Z
                  = O(T)            O(1)        since log 26 is constant
```

The formula is O(T) and the simplest to code correctly under pressure. The simulation is more general — it handles variable execution times and non-uniform cooldowns if extended.

### Comparison Table

```
Problem             Brute Force     Optimal         Key Data Structure
Merge Intervals     O(n^3)          O(n log n)      Sort + last pointer
Meeting Rooms II    O(n^2)          O(n log n)      Min-heap on end times
Task Scheduler      O(n!)           O(T)            Counter + math formula
```

---

## ASCII Diagrams

### Merge Intervals — Visual

Input: `[[1,3],[2,6],[8,10],[15,18]]` (already sorted here for clarity)

```
Time axis:
 1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18
 |-----|                                               [1,3]
    |-----------|                                      [2,6]
                         |------|                      [8,10]
                                            |--------|  [15,18]

After merge:
 |-----------|                                         [1,6]
                         |------|                      [8,10]
                                            |--------|  [15,18]

Merge step trace:
  result = [[1,3]]
  curr=[2,6]:  2 <= 3? YES -> extend: result = [[1,6]]
  curr=[8,10]: 8 <= 6? NO  -> append: result = [[1,6],[8,10]]
  curr=[15,18]:15 <= 10? NO -> append: result = [[1,6],[8,10],[15,18]]
```

### Meeting Rooms II — Heap State Evolution

Input: `[[0,30],[5,10],[15,20]]` sorted by start = same order.

```
Meetings on timeline:
 0          10    15    20              30
 |-----------------------------Room A----|   [0,30]
       |----Room B----|                      [5,10]
                  |----Room C----|           [15,20]

Heap trace (min-heap of end times, showing heap after each meeting):
  Process [0,30]:  heap empty           -> push 30   -> heap=[30]          rooms=1
  Process [5,10]:  heap[0]=30 > 5      -> push 10   -> heap=[10,30]       rooms=2
  Process [15,20]: heap[0]=10 <= 15    -> replace 10 with 20 -> heap=[20,30] rooms=2

Final heap size = 2.  Answer: 2 rooms.

Explanation of step 3:
  Meeting [15,20] starts at 15. Heap top = 10 (earliest room frees at 10).
  Since 10 <= 15, that room is free. We reuse it: replace end-time 10 with 20.
  No new room allocated.
```

### Task Scheduler — Frame Model

Tasks: `[A,A,A,B,B,B]`, n=2 (cooldown). max_count=3 (A or B), max_count_tasks=2.

```
Frames of size (n+1) = 3:

Frame 1:  [ A | B | _ ]    (A and B placed; 1 idle slot)
Frame 2:  [ A | B | _ ]    (A and B placed; 1 idle slot)
Last bit: [ A | B ]        (max_count_tasks = 2 tasks remaining)

Timeline: A B _ A B _ A B
           1 2 3 4 5 6 7 8

Total = (max_count - 1) * (n + 1) + max_count_tasks
      = (3 - 1) * (2 + 1) + 2
      = 2 * 3 + 2 = 8
```

---

## 6. Variations & Follow-up Questions

### Merge Intervals Variations

**Insert Interval (LeetCode 57)**: Given sorted non-overlapping intervals, insert a new interval and merge. Walk through the sorted list: append all intervals that end before new.start; then merge all that overlap the new interval; then append the rest. O(n) time.

**Number of Overlapping Pairs**: Return count of pairs `(i,j)` where intervals overlap. Sort + suffix max of end times, then binary search. O(n log n).

**Maximum Overlap at Any Point**: Equivalent to Meeting Rooms II (min rooms = max overlap depth). Coordinate sweep gives O(n log n).

**Non-overlapping Intervals (LeetCode 435)**: Minimum removals to make all intervals non-overlapping. Greedy: sort by end time (not start — this is the activity selection problem), greedily keep the interval ending earliest. O(n log n).

### Meeting Rooms Variations

**Meeting Rooms I (LeetCode 252)**: Can one person attend all meetings? Sort by start; check adjacent pairs for overlap. O(n log n).

**Meeting Rooms III (LeetCode 2402)**: N rooms, numbered 0..n-1. Meetings assigned to lowest-numbered available room; if none free, delay until earliest room frees. Return room that hosted most meetings. Two heaps: free rooms (min-heap by room id), busy rooms (min-heap by release time). O(m log n) where m = number of meetings.

**Minimum Meeting Rooms with Priorities**: Some meetings are non-cancellable; others can be delayed. Multi-priority heap scheduling.

### Task Scheduler Variations

**Task Scheduler II (LeetCode 2365)**: Tasks with individual cooldowns (not global). Sort by deadline-like approach using a priority queue.

**Reorganize String (LeetCode 767)**: Rearrange characters so no two adjacent chars are the same. Special case of n=1 cooldown. Answer exists iff max_count <= ceil(len/2). Greedy: always place the most frequent remaining character.

**Maximum CPU Load**: Given tasks with start, end, and load, find the maximum CPU load at any instant. Sweep line or sort-by-start + min-heap.

**Weighted Job Scheduling**: Jobs have weights (profits). Maximize total profit with no overlaps. DP + binary search: O(n log n). Greedy does NOT work here because a low-profit short job might block a high-profit long job.

---

## 7. Real-World Usage

### Google Calendar — Merge Conflict Detection

Google Calendar stores events sorted by start time (in per-user indexes). When rendering a day view, it must compute visual "columns" — sets of non-overlapping events that can be displayed side by side. The underlying algorithm is interval graph coloring, which reduces to meeting rooms: how many columns (rooms) are needed? With ~100M active users and ~10 events per day per user, this is executed billions of times daily. The O(n log n) sweep makes it feasible at per-request granularity with n typically < 20 events.

For the backend, Calendar's conflict-detection API (the "check availability" call in GSuite) runs merge-interval logic across multiple calendars: merge each person's busy intervals, then check if a proposed slot is free for all attendees. A naive O(n^2) pairwise check across thousands of accounts would be prohibitive.

### Airbnb / Booking.com — Availability Windows

A property has a list of booked intervals (check-in, check-out dates). The system must compute contiguous available windows and answer queries like "find all properties available for [June 10, June 15]". The approach: merge all booked intervals for a property, then the gaps between merged intervals are available windows. This is pure merge-intervals. At Airbnb's scale (~6M active listings), availability checks run with pre-merged interval sets cached per listing and invalidated on new bookings. The difference between O(n^2) and O(n log n) per booking insertion determined their cache invalidation budget.

### Kubernetes Pod Scheduling — Resource Intervals

Kubernetes resource requests and limits model compute slots as intervals in time. The scheduler must place pods on nodes without exceeding resource bounds. A simplified sub-problem: given a node's scheduled maintenance windows (intervals during which pods should not be running), determine the merged maintenance blackout windows and schedule pods only in the gaps. The cluster autoscaler uses interval-merge to consolidate PodDisruptionBudget windows across replicas before deciding safe scale-down times.

### Database Maintenance Windows — Vacuum / ANALYZE Scheduling

PostgreSQL autovacuum and Oracle's automatic maintenance tasks are scheduled within maintenance windows (e.g., "any time in [2:00–4:00 AM] or [14:00–16:00 PM]"). When multiple databases share a host, DBAs configure per-database windows that may overlap. The operations team merges these to find total maintenance load periods and avoid scheduling heavy backups during overlapping windows. This is merge-intervals applied to maintenance schedules.

### CI/CD Pipeline — Job Scheduling with Dependencies

GitHub Actions and Jenkins pipelines define jobs with `needs` dependencies. The scheduler tracks which jobs are running (intervals of execution time on agents). To maximize parallelism, it finds the maximum concurrent job count (meeting rooms II) to determine minimum agent pool size. Cooldown-aware retries (task scheduler concept) appear in GitHub Actions' `wait-timer` on environments, where deployments must cool down between re-runs to prevent cascading failures.

### CPU Task Scheduling — Linux CFS Concept

The Linux Completely Fair Scheduler (CFS) uses a red-black tree of tasks keyed by `vruntime` (virtual runtime). The scheduling decision is: pick the task with lowest vruntime (most deserving of CPU time). This is conceptually the task-scheduler greedy: always run the task with the most remaining "debt." Rate-limited I/O tasks (e.g., NVMe queue depth limits) introduce per-task cooldown windows analogous to the cooldown parameter `n`. The formula `(max_count-1)*(n+1)+max_count_tasks` has a direct analogue in CFS's calculation of minimum scheduling latency.

### Video Editing — Timeline Track Allocation

Adobe Premiere Pro and DaVinci Resolve's timeline engine allocates clips to tracks. Clips are intervals. When importing overlapping clips, the engine auto-assigns them to the minimum number of tracks needed so no track has two clips at the same time — this is meeting rooms II with clips as meetings and tracks as rooms. DaVinci Resolve's Fusion compositor extends this to layered effect intervals with priority ordering.

---

## 8. Edge Cases & Testing

### Merge Intervals

```python
# Empty input
assert merge_intervals([]) == []

# Single interval
assert merge_intervals([[1, 5]]) == [[1, 5]]

# All intervals identical
assert merge_intervals([[2, 4], [2, 4], [2, 4]]) == [[2, 4]]

# All intervals disjoint (no merging)
assert merge_intervals([[1, 2], [3, 4], [5, 6]]) == [[1, 2], [3, 4], [5, 6]]

# All intervals overlap into one giant interval
assert merge_intervals([[1, 100], [2, 50], [30, 200]]) == [[1, 200]]

# Touching endpoints (convention: merge)
assert merge_intervals([[1, 3], [3, 5]]) == [[1, 5]]

# Containment (inner interval fully inside outer)
assert merge_intervals([[1, 10], [2, 3], [4, 6]]) == [[1, 10]]

# Reverse sorted input
assert merge_intervals([[5, 6], [3, 4], [1, 2]]) == [[1, 2], [3, 4], [5, 6]]

# Zero-length interval (point)
assert merge_intervals([[2, 2], [2, 5]]) == [[2, 5]]

# Two intervals, first contains second
assert merge_intervals([[0, 10], [1, 9]]) == [[0, 10]]

# Large n to verify O(n log n) doesn't time out
import random
big_input = sorted([[i, i + random.randint(1, 5)] for i in range(0, 100000, 3)])
result = merge_intervals(big_input)
assert len(result) <= len(big_input)
```

### Meeting Rooms II

```python
assert min_meeting_rooms([]) == 0
assert min_meeting_rooms([[1, 5]]) == 1

# Room reused when end == next start
assert min_meeting_rooms([[0, 10], [10, 20]]) == 1

# All meetings simultaneous
assert min_meeting_rooms([[0, 100], [0, 50], [0, 75]]) == 3

# Two non-overlapping meetings — one room
assert min_meeting_rooms([[1, 3], [4, 6]]) == 1

# Duplicate meetings
assert min_meeting_rooms([[1, 5], [1, 5], [1, 5]]) == 3
```

### Task Scheduler

```python
# No cooldown
assert task_scheduler_formula(["A", "A", "A"], 0) == 3

# Single task type with cooldown
assert task_scheduler_formula(["A", "A", "A"], 2) == 7  # A _ _ A _ _ A

# n >= count-1, only the most frequent task limits
assert task_scheduler_formula(["A", "A", "B", "C"], 2) == 4

# More tasks than idle slots needed
# tasks=AAABBBCCC, n=1 -> formula: (3-1)*(2)+3=7, len=9, max(9,7)=9
assert task_scheduler_formula(["A"]*3 + ["B"]*3 + ["C"]*3, 1) == 9

# n=0 always returns len(tasks)
tasks = ["A", "B", "C", "A", "B", "C"]
assert task_scheduler_formula(tasks, 0) == len(tasks)
```

### Tricky Cases

1. **Interval start == interval end** (zero-length): treated as a valid point; two zero-length intervals at the same point merge.
2. **Negative timestamps** (e.g., historical UTC timestamps): algorithms work unchanged; sort handles negatives correctly.
3. **Integer overflow**: Python has arbitrary precision integers, so no overflow concern. Java/C++ need `long`.
4. **Meeting starts exactly when another ends**: whether `<=` or `<` matters for room reuse. The convention `heap[0] <= start` (reuse room) vs `heap[0] < start` (new room) changes the answer for `[[0,10],[10,20]]` from 1 to 2.
5. **All tasks identical**: `task_scheduler_formula(["A"]*6, 3)` = `(6-1)*(4)+1 = 21`. Verify: A _ _ _ A _ _ _ A _ _ _ A _ _ _ A _ _ _ A = 21 intervals.

---

## 9. Common Mistakes

### Mistake 1 — Sorting by end instead of start for merge-intervals

**Frequency**: seen in approximately 30-40% of first attempts in whiteboard sessions.

Sorting by end time instead of start time for merge-intervals produces incorrect results on inputs where a long interval starting early is encountered after short intervals starting later. Example: input `[[1,10],[2,3],[4,8]]`. Sorted by end: `[[2,3],[4,8],[1,10]]`. The algorithm starts with `[2,3]`. Then `[4,8]`: 4 > 3, no overlap, appends. Then `[1,10]`: 1 <= 8, merges `[4,8]` into `[4,10]`. Output: `[[2,3],[4,10]]`. Correct output: `[[1,10]]`. The fix is always to sort by start time. The end-time sort is correct for the *activity selection problem* (maximize number of non-overlapping intervals selected), not for merging.

### Mistake 2 — Off-by-one in overlap check for touching intervals

**Frequency**: ~25% of candidates omit the `=` in `curr[0] <= last[1]` and use strict `<`. This fails the case `[[1,3],[3,5]]`, outputting `[[1,3],[3,5]]` instead of `[[1,5]]`. Always confirm with the interviewer whether touching endpoints count as overlapping, then implement consistently.

### Mistake 3 — Not comparing against heap top before pushing (meeting rooms)

Writing `heapq.heappush(heap, end)` unconditionally every iteration allocates a new room for every meeting, giving the wrong answer of n (number of meetings). The check `if heap and heap[0] <= start: heapreplace(...)` is the entire logic for reuse. Missing this conditional is the single most common meeting-rooms bug.

### Mistake 4 — Calendar merge: O(n^2) pairwise at scale

A production incident at a mid-size SaaS company (2019, ~200K users): their "find a time" feature used an O(n^2) pairwise overlap check to compute available windows across attendees' calendars. With n=10,000 events per user and 10 attendees per meeting invitation, the computation was 10^9 operations per request. At 100 requests/second peak, this saturated 4 CPU cores continuously. The fix — switch to sorted merge for each user's calendar (O(n log n)) then merge across users (O(k * log n) with k attendees) — reduced this to 10,000 × 13 × 10 = 1,300,000 operations per request (a factor of 769 reduction), bringing latency from ~8 seconds to under 11 milliseconds.

Quantified: O(n^2) on 10,000 events = 10^8 comparisons. At 10^7 operations/sec in Python = 10 seconds.
O(n log n) on 10,000 events = 10,000 × 13 ≈ 130,000 operations = 0.013 seconds.
Speedup: approximately 770x.

### Mistake 5 — Task scheduler: confusing n with n+1 in the frame size

The frame size is `n+1` (n cooldown slots PLUS 1 slot for the task itself). Writing `(max_count - 1) * n` instead of `(max_count - 1) * (n + 1)` undercounts by `(max_count - 1)` intervals. For `tasks=AAABBB, n=2`: correct = `(3-1)*(3)+2 = 8`. Wrong formula gives `(3-1)*(2)+2 = 6`. The formula fails because it doesn't account for the execution slot of the anchor task itself in each frame.

### Mistake 6 — Meeting rooms: not handling the case where the heap is empty

Calling `heap[0]` or `heappop` on an empty heap raises IndexError. Always guard with `if heap and ...`. This is a runtime crash during the interview demo — very visible.

### Mistake 7 — Merge intervals: mutating input list

Calling `intervals.sort(...)` mutates the caller's list. In production code (and in some interview rubrics), always sort a copy: `sorted_intervals = sorted(intervals, key=lambda x: x[0])`. Similarly, the output should be new lists, not references to input sub-lists, to avoid aliasing bugs.

---

## 10. Related Problems

| Problem | Connection | Key Difference |
|---------|-----------|----------------|
| Non-overlapping Intervals (LC 435) | Same setup as merge; sort by **end** for greedy removal | Activity selection: sort by end, not start |
| Insert Interval (LC 57) | Merge a single new interval into a sorted list | O(n) scan instead of O(n log n) sort |
| Meeting Rooms I (LC 252) | Can one person attend all? Sort + adjacent check | Simpler: no count needed |
| Meeting Rooms III (LC 2402) | Rooms numbered; assign lowest free; count per room | Two heaps: free rooms + busy rooms |
| Minimum Number of Arrows to Burst Balloons (LC 452) | Intervals on a 1D line; arrows pierce all overlapping | Sort by end; greedy arrow placement |
| Employee Free Time (LC 759) | Merge per-employee intervals, find gaps | Multi-list merge + gap extraction |
| Reorganize String (LC 767) | Task scheduler with n=1, character version | Same formula; also solvable with interleaving |
| Weighted Job Scheduling | Intervals with profits; maximize profit | DP + binary search; greedy fails |
| Jump Game (LC 55) | Intervals of reach on an array | Greedy with max-reach pointer |
| Data Stream as Disjoint Intervals (LC 352) | Online merge: insert one value at a time | Sorted container (SortedList) or BST |

---

## 11. Interview Discussion Points

**Q: Why do we sort by start time for merge intervals, but by end time for the activity selection / non-overlapping intervals problem?**
Merge intervals combines all overlapping intervals into one — you want to process intervals left to right on the time axis, extending the current merged span. Sorting by start ensures you always encounter the interval that starts earliest next, so the check `curr.start <= last.end` is correct. Activity selection maximizes the number of non-overlapping intervals you can keep — the greedy choice is to keep the interval that ends earliest, leaving the most room for future intervals. Sorting by end enables this greedy selection. The two sorts serve opposite goals: one enables extension, the other enables early release.

**Q: What is the heap invariant in meeting rooms II, and why does a min-heap on end times suffice?**
The heap invariant is: every element is the end time of a currently occupied room. The minimum element is the room that will become free soonest. When a new meeting arrives, we only need to check whether the soonest-freeing room is free by the new meeting's start time. We do not need to check all rooms — any room with end time > new meeting's start is still busy, and they will remain busier than the minimum. If the minimum is still busy, all rooms are busy (new room needed). If the minimum is free, reuse it (and now its new end time may or may not be the new minimum — heappush handles that). This is an O(log n) decision per meeting because it exploits the heap's total order on end times.

**Q: Is there an analogy between meeting rooms II and Dijkstra's algorithm?**
Both use a min-heap to greedily process the "cheapest" or "earliest available" element next. In Dijkstra, the heap holds (distance, node) pairs and we always extend from the nearest unvisited node. In meeting rooms, the heap holds end times and we always check the earliest-freeing room. Both algorithms rely on the same property: once an element is popped from the heap, processing it greedily is globally optimal (no future information can make a different choice better at that moment). The formal similarity is that both operate on a sorted frontier where the minimum-cost item is processed first.

**Q: Walk through the task scheduler formula derivation.**
The most frequent task type (count = max_count) acts as an anchor. To respect the cooldown, we create (max_count - 1) "frames" where each frame has one execution of the anchor task plus n cooldown slots. After all frames, we place the final anchor executions. Frame size = (n + 1). Total slots in frames = (max_count - 1) * (n + 1). The last partial frame holds all task types with frequency equal to max_count, so we add max_count_tasks. This gives a lower bound. But if other tasks are so numerous they fill all cooldown slots with no idle time, the answer is just len(tasks). So: `max(len(tasks), (max_count-1)*(n+1) + max_count_tasks)`.

**Q: What is the difference between overlap and containment, and does it matter for merge intervals?**
Overlap means the intervals share at least one point (one starts before the other ends). Containment is a subset relationship: `[2,3]` is contained within `[1,10]`. The merge intervals algorithm handles containment correctly by using `max(last[1], curr[1])` for the merged end: if curr is contained in last, `max(last[1], curr[1]) = last[1]` — last is unchanged. If curr extends beyond last, the max extends correctly. The key: never assign `curr[1]` directly; always take the max.

**Q: Can the task scheduler problem be solved with DP? Is greedy provably optimal?**
The greedy formula is provably optimal. Proof sketch via lower bound: the answer is at least `len(tasks)` (must execute all tasks) and at least `(max_count-1)*(n+1)+max_count_tasks` (the most frequent task creates unavoidable gaps). The greedy construction achieves exactly this lower bound — it never introduces unnecessary idle time. DP would work but is overkill: states would be the remaining count of each task type, which is exponential in the number of distinct task types. The greedy beats DP both in complexity and code simplicity.

**Q: How do you detect calendar conflicts at scale (millions of users)?**
At scale, the approach is: (1) Per user, store events in a sorted structure (B+Tree or sorted list by start time). (2) When checking conflict for a new event, binary search for the predecessor and successor events; check overlap with those two neighbors only — O(log n). (3) For "find a time" (multi-user availability), fetch each user's events in the requested time window, merge each user's interval list (O(k log k) per user), then compute the intersection of free windows across all users by merging their busy intervals and finding gaps. Total: O(U * k log k) where U = users and k = events per user per day window. (4) Cache merged daily busy intervals per user, invalidated on event changes.

**Q: Why does weighted interval scheduling require DP instead of greedy?**
In unweighted interval scheduling (maximize count of non-overlapping intervals), all intervals have equal value, so the greedy choice of "pick the one ending earliest" is globally optimal — it never sacrifices a more valuable interval. In weighted interval scheduling, a long high-value interval might be worth more than several short low-value intervals combined. The greedy "end earliest" choice might skip the long interval to keep more short ones — but if the long one has higher total weight, that is suboptimal. DP is required: `dp[i]` = max profit using the first `i` intervals (sorted by end), where `dp[i] = max(dp[i-1], value[i] + dp[p(i)])` and `p(i)` is the last non-overlapping interval before `i`. Binary search finds `p(i)` in O(log n) per step, giving O(n log n) total.

**Q: How would you solve merge intervals if new intervals are being inserted one at a time (streaming/online)?**
Use a sorted container (Python's `sortedcontainers.SortedList` or Java's `TreeMap`) keyed on start time. On each insertion: (1) binary search for the new interval's position, (2) scan backward to find any intervals that overlap on the left, (3) scan forward to find any that overlap on the right, (4) merge all found + new into one interval and replace. Expected cost: O(log n) to find position + O(k) to merge k overlapping intervals. Amortized O(log n) if the output has few overlaps. This is the approach used by Data Stream as Disjoint Intervals (LeetCode 352).

**Q: What is the connection between meeting rooms II and graph coloring?**
Meeting rooms II is equivalent to interval graph coloring: create a vertex for each interval and an edge between every pair of overlapping intervals. The chromatic number (minimum colors to color the graph so no two adjacent vertices share a color) equals the minimum number of rooms. Interval graphs are perfect graphs, meaning their chromatic number equals their clique number (maximum clique size = maximum simultaneous overlap). The heap algorithm computes this clique number greedily in O(n log n) without explicitly constructing the graph.

**Q: In the task scheduler simulation, why use a cooldown queue rather than tracking available-at time directly in the heap?**
The heap is keyed on remaining count (negated) — we always want to run the most frequent remaining task. But after running a task, it cannot be re-used until `current_time + n + 1`. If we stored `(available_at, neg_count)` in the heap, we would need to pop and re-push elements while advancing time, and we might miss releasing tasks at the right moment. The cooldown queue (a deque sorted by available-at time, since we always add elements with monotonically increasing available-at = current_time + n + 1) lets us release tasks back to the heap at exactly the right time without scanning. The two-structure approach (heap for priority, deque for timing) is the classic pattern for priority scheduling with time delays.

**Q: For Google Calendar's day view rendering, why does the column assignment problem require more than just counting maximum overlap?**
Counting maximum overlap (meeting rooms II) tells you the minimum number of columns needed, but not which events go in which column. Assigning events to columns requires interval graph coloring: a greedy scan assigns each event to the lowest-numbered column that does not conflict. The result is a valid k-coloring where k = max overlap. In practice, Google Calendar also applies aesthetic constraints (events in the same column must be visually coherent; short events may span multiple columns) that turn this into a constrained coloring problem solved with a modified sweep. The O(n log n) base algorithm remains, but the column assignment needs an additional O(n log k) pass where k is the number of columns.

---

*Cross-references: [Heaps and Priority Queues](../heaps_and_priority_queues/README.md) — heap invariants and heapify; [Greedy and Divide and Conquer](../greedy_and_divide_and_conquer/README.md) — exchange argument proofs; [Sorting and Searching](../sorting_and_searching/README.md) — sort stability and timsort; [Dynamic Programming Patterns](./dynamic_programming_patterns.md) — weighted interval scheduling DP.*
