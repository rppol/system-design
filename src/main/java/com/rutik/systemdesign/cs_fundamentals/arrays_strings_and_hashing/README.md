# Arrays, Strings & Hashing

---

## 1. Concept Overview

Arrays and hash tables are the two most fundamental data structures in all of computing. Together they underlie every caching layer, every database index, every programming language's built-in container, and the majority of interview problems.

An **array** is a contiguous block of memory where each element is the same size, enabling O(1) random access by index (base + index × element_size = address). A **dynamic array** (Python `list`, Java `ArrayList`, C++ `vector`) grows automatically by allocating a larger block and copying — with amortized O(1) append.

A **hash table** maps arbitrary keys to values using a hash function that converts any key to a bucket index. With a good hash function and bounded load factor, all core operations (insert, lookup, delete) are O(1) average. Hash tables trade memory for speed: they are the canonical example of the time-space tradeoff.

---

## 2. Intuition

> **One-line analogy**: An array is a numbered parking lot — any space by number in O(1); a hash table is a valet parking system that derives your spot from your car's license plate and remembers it instantly.

**Mental model**: Arrays give you O(1) by position but O(n) by value. Hash tables give you O(1) by value (key) but lose position ordering. The moment you need "is X present?" or "how many times does X appear?", reach for a hash table. The moment you need "what's at index i?" or "iterate in order", reach for an array.

**Why it matters**: Hash tables appear in almost every medium or hard interview problem as the data structure that reduces an O(n²) brute-force scan to an O(n) solution. Knowing when to use one (any "count", "seen before", "complement lookup") is the single most impactful interview pattern.

**Key insight**: The trick in most hash-table interview problems is deciding what to hash and what to store. For "two sum" you hash the number, storing its index. For "anagram grouping" you hash the sorted word. For "longest consecutive sequence" you hash all the numbers, then probe for sequence starts.

---

## 3. Core Principles

- **Contiguous memory = O(1) random access**: element at index i is at `base_address + i × element_size`. No pointer chasing.
- **Dynamic array growth**: Python `list` and Java `ArrayList` allocate 1.5–2× capacity when full and copy all elements. Amortized O(1) append; worst-case single append is O(n).
- **Hash function**: maps a key to an integer. Requirements: deterministic, fast (O(1)), and distributes keys uniformly. Python uses `__hash__`; Java uses `hashCode()`.
- **Hash collision**: two keys map to the same bucket. Resolved by chaining (bucket holds a linked list of entries) or open addressing (probe for the next empty slot).
- **Load factor**: `n / capacity`. Python dict resizes at ~2/3 load; Java HashMap at 0.75 (default). Higher load = more collisions = slower. Resize copies all entries to a new, larger table.
- **Key immutability**: hash table keys must be **hashable** (hash must not change). Mutable keys (Python list, unhashable) cannot be used — use tuples instead.
- **String immutability**: strings in Python and Java are immutable. Repeated string concatenation (`s += char` in a loop) creates O(n) new objects — total O(n²). Use `''.join(parts)` or `StringBuilder`.

---

## 4. Types / Strategies

### 4.1 Collision Resolution

**Separate chaining (Python dict, Java HashMap up to Java 7)**:
- Each bucket holds a linked list (Java 8+: converts to a balanced BST when ≥ 8 entries in one bucket).
- Lookup: compute bucket, scan the chain — O(1) average, O(n) worst case (all keys in one bucket).
- Load factor controls chain length; at load factor 0.75 average chain length is 0.75 ≈ O(1).

**Open addressing (linear probing, quadratic probing, double hashing)**:
- All entries stored in the main array; no separate chains.
- On collision: probe the next slot according to a formula. Linear: `(h + i) mod cap`. Quadratic: `(h + i²) mod cap`. Double hashing: `(h1 + i × h2) mod cap`.
- Deletion: cannot simply remove — must leave a tombstone/sentinel, or rehash all following entries.
- Better cache performance than chaining (no pointer chasing), but clustering problems with linear probing.

### 4.2 Key Design Patterns

**Frequency count**: `Counter({})` or `defaultdict(int)`. O(n) to build.
**Two-sum complement lookup**: store `target - x` as you scan; check if current `x` is in the map.
**Sliding window with frequency map**: track character counts in a window; adjust as window slides.
**Canonical key for grouping**: sort characters of a word to produce an anagram group key.
**XOR / sum of unique elements**: works when all but one element appears an even number of times.

### 4.3 Special Hash Table Variants

| Variant | Description | Language |
|---------|-------------|---------|
| `OrderedDict` / `LinkedHashMap` | Preserves insertion order | Python / Java |
| `Counter` | Frequency map with arithmetic ops | Python |
| `defaultdict` | Auto-initialises missing keys | Python |
| `TreeMap` / `SortedDict` | Sorted iteration + range queries in O(log n) | Java / Python `sortedcontainers` |
| `WeakHashMap` | Entries eligible for GC when keys have no other refs | Java |
| `ConcurrentHashMap` | Thread-safe, segment-level locking | Java |

---

## 5. Architecture Diagrams

### Hash Table with Separate Chaining

```
  Key: "cat"  →  hash("cat") mod 8 = 3
  Key: "act"  →  hash("act") mod 8 = 6
  Key: "tac"  →  hash("tac") mod 8 = 3  (collision with "cat"!)

  Bucket array (capacity=8):
  [0] -> null
  [1] -> null
  [2] -> null
  [3] -> ["cat": 1] -> ["tac": 2] -> null   (chain)
  [4] -> null
  [5] -> null
  [6] -> ["act": 3] -> null
  [7] -> null

  Load factor = 3 / 8 = 0.375 (below 0.75 threshold — no resize needed)
```

### Dynamic Array Growth

```
Append sequence:  [1] → [1,2] → [1,2,3] → [1,2,3,4] → [1,2,3,4,5]

capacity:          1     2       4          4            8
size:              1     2       3          4            5
copy cost:         0     1       2          0            4
                   ^            ^                        ^
                   initial   resize (2→4,             resize (4→8,
                             copy 2 elements)          copy 4 elements)

Total copies after 5 appends: 0 + 1 + 2 + 0 + 4 = 7 < 2×5 = 10
Amortized cost per append: O(1)
```

### Two Sum — Hash Table Solution

```
arr = [2, 7, 11, 15]   target = 9

Step 1: x=2, need (9-2)=7, seen={},       7 not in seen, add seen[2]=0
Step 2: x=7, need (9-7)=2, seen={2:0},    2 IS in seen → return (seen[2]=0, current=1)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Dynamic Array Append (Amortized Analysis)

```python
from __future__ import annotations

class DynArray:
    """Minimal dynamic array to illustrate amortized O(1) append."""

    GROWTH_FACTOR = 2  # Python CPython uses ~1.125 with a formula; conceptually 2x

    def __init__(self) -> None:
        self._data: list[object] = [None]
        self._size: int = 0
        self._cap: int = 1

    def append(self, val: object) -> None:
        if self._size == self._cap:
            new_cap = self._cap * self.GROWTH_FACTOR
            new_data: list[object] = [None] * new_cap
            for i in range(self._size):
                new_data[i] = self._data[i]  # O(n) copy
            self._data = new_data
            self._cap = new_cap
        self._data[self._size] = val
        self._size += 1

    def __getitem__(self, idx: int) -> object:
        if not 0 <= idx < self._size:
            raise IndexError(idx)
        return self._data[idx]  # O(1) direct access
```

Amortised proof: each element is copied at most once per doubling step. Total copies for n appends = n/2 + n/4 + ... ≤ n. Total work = n appends + n copies = 2n = O(n). Amortised per-append: O(1).

### 6.2 Hash Table Implementation

```python
from __future__ import annotations
from typing import Iterator

class HashMap:
    """Open-addressing hash map with linear probing and tombstones."""

    _DELETED = object()   # sentinel for deleted slots

    def __init__(self, initial_cap: int = 8) -> None:
        self._cap = initial_cap
        self._keys: list[object] = [None] * self._cap
        self._vals: list[object] = [None] * self._cap
        self._size = 0

    def _probe(self, key: object) -> int:
        h = hash(key) % self._cap
        while self._keys[h] is not None and self._keys[h] is not self._DELETED and self._keys[h] != key:
            h = (h + 1) % self._cap   # linear probing
        return h

    def put(self, key: object, val: object) -> None:
        if self._size / self._cap >= 0.75:
            self._resize()
        idx = self._probe(key)
        if self._keys[idx] is None or self._keys[idx] is self._DELETED:
            self._size += 1
        self._keys[idx] = key
        self._vals[idx] = val

    def get(self, key: object) -> object | None:
        idx = self._probe(key)
        if self._keys[idx] == key:
            return self._vals[idx]
        return None

    def delete(self, key: object) -> None:
        idx = self._probe(key)
        if self._keys[idx] == key:
            self._keys[idx] = self._DELETED  # tombstone
            self._size -= 1

    def _resize(self) -> None:
        old_keys, old_vals = self._keys, self._vals
        self._cap *= 2
        self._keys = [None] * self._cap
        self._vals = [None] * self._cap
        self._size = 0
        for k, v in zip(old_keys, old_vals):
            if k is not None and k is not self._DELETED:
                self.put(k, v)
```

### 6.3 String Building — Common O(n²) Trap

```python
# BROKEN: O(n^2) — each += creates a new string object
def build_string_broken(chars: list[str]) -> str:
    result = ""
    for c in chars:
        result += c   # new string object created each time
    return result
# n concatenations of lengths 0,1,...,n-1 = O(n^2) total characters copied

# FIX: O(n) — collect parts and join at the end
def build_string(chars: list[str]) -> str:
    parts: list[str] = []
    for c in chars:
        parts.append(c)   # O(1) amortized append to list
    return ''.join(parts)  # single O(n) scan
```

### 6.4 Anagram Grouping — Canonical Key Pattern

```python
from collections import defaultdict

def group_anagrams(strs: list[str]) -> list[list[str]]:
    """
    O(n × k log k) where n = number of strings, k = max string length.
    Key insight: two strings are anagrams iff they have the same sorted characters.
    """
    groups: dict[str, list[str]] = defaultdict(list)
    for s in strs:
        key = ''.join(sorted(s))   # canonical key: sorted characters
        groups[key].append(s)
    return list(groups.values())
# ["eat","tea","tan","ate","nat","bat"] → [["eat","tea","ate"],["tan","nat"],["bat"]]
```

### 6.5 Longest Consecutive Sequence — O(n) with Hash Set

```python
def longest_consecutive(nums: list[int]) -> int:
    """
    O(n) time. Key: only start a streak from a number that has no predecessor.
    """
    num_set = set(nums)   # O(n) build
    best = 0
    for n in num_set:
        if (n - 1) not in num_set:   # n is the start of a streak
            current = n
            streak = 1
            while (current + 1) in num_set:
                current += 1
                streak += 1
            best = max(best, streak)
    return best
# Each number is visited at most twice (once in the outer loop, once in the while loop).
# Total iterations: O(2n) = O(n).
```

---

## 7. Real-World Examples

**CPython dict** — uses open addressing with random probing (not linear) to avoid clustering. As of Python 3.7, dicts are insertion-ordered: a compact index array points into a dense entries array. This gives good cache performance and O(1) iteration. Resize happens at 2/3 load factor; the table size is always a power of 2.

**Java HashMap internals** — uses separate chaining. Each bucket starts as a singly-linked list. In Java 8+, when a bucket has ≥ 8 entries, it converts to a red-black tree, giving O(log n) worst-case lookup for that bucket. This prevents hash-collision DoS attacks. The default initial capacity is 16; load factor 0.75 means resize at 12 entries.

**Database hash join** — when joining two tables, the DBMS builds a hash table from the smaller table (build phase), then probes it with each row from the larger table (probe phase). O(n + m) total vs O(n × m) for a nested-loop join. PostgreSQL uses this for equi-joins; the hash table is partitioned across memory buffers if it doesn't fit in RAM.

**DNS resolution caching** — a local DNS resolver caches name→IP mappings in a hash table keyed by the domain name. Cache hit: O(1) hash lookup, no network round-trip (~0 ms). Cache miss: recursive resolution ~50–200 ms. The TTL field in DNS records determines how long an entry stays in the cache.

**Rate limiting with sliding window counter** — a rate limiter tracks request counts per (user, time-bucket) pair using a hash map. Key = `user_id:minute_bucket`, value = request count. Each request increments the count and checks it against the limit. O(1) per request with constant memory per user.

---

## 8. Tradeoffs

### Hash Table vs Sorted Array vs Balanced BST

| Operation | Hash table (avg) | Sorted array | Balanced BST |
|-----------|-----------------|--------------|--------------|
| Lookup | O(1) | O(log n) | O(log n) |
| Insert | O(1) amortized | O(n) (shift) | O(log n) |
| Delete | O(1) | O(n) | O(log n) |
| Min/Max | O(n) | O(1) | O(log n) |
| Range [lo,hi] | O(n) | O(log n + k) | O(log n + k) |
| Sorted iteration | O(n log n) | O(n) | O(n) |
| Space | O(n) + overhead | O(n) | O(n) |

### Chaining vs Open Addressing

| Dimension | Chaining | Open addressing |
|-----------|---------|----------------|
| Cache performance | Poor (pointer chasing) | Better (contiguous) |
| Deletion | Simple (remove node) | Requires tombstone |
| Load factor sensitivity | Tolerates > 1.0 | Degrades sharply near 1.0 |
| Memory overhead | Extra pointer per entry | None |
| Clustering | No primary clustering | Linear probing suffers clustering |

---

## 9. When to Use / When NOT to Use

**Use hash table when:**
- O(1) lookup, insert, delete by key is needed and ordering is not.
- Counting frequencies, deduplication, caching, or memoisation.
- "Two-sum", "find duplicates", "longest subarray" pattern problems.

**Use array when:**
- O(1) access by index is needed.
- Data has a known fixed size or grows predictably.
- Cache-efficient iteration is important.

**Do NOT use hash table when:**
- You need sorted order, range queries, or floor/ceiling lookups — use BST (TreeMap/SortedDict).
- Keys are mutable (lists, dicts) — hash cannot be computed.
- Deterministic worst-case latency is required — hash table O(n) worst case (all collisions). Use a balanced BST.
- Memory is extremely constrained — hash tables use ~2–4× the raw data size due to load-factor headroom and pointer overhead.

---

## 10. Common Pitfalls

### Pitfall 1: Mutating a Dict While Iterating

```python
# BROKEN: RuntimeError: dictionary changed size during iteration
d = {'a': 1, 'b': 2, 'c': 3}
for key in d:
    if d[key] == 2:
        del d[key]   # BROKEN: mutate during iteration

# FIX: iterate over a copy of keys
for key in list(d.keys()):
    if d[key] == 2:
        del d[key]   # safe: iterating the copy
```

### Pitfall 2: Using a List as a Dict Key

```python
# BROKEN: TypeError — list is unhashable
d = {}
key = [1, 2, 3]
d[key] = "value"   # TypeError: unhashable type: 'list'

# FIX: use a tuple (immutable, hashable)
d[tuple(key)] = "value"   # ✓
```

### Pitfall 3: String Concatenation in a Loop — O(n²)

```python
# BROKEN: O(n^2) — each += creates a new string
result = ""
for word in words:
    result += word + " "   # BROKEN for large n

# FIX: join at the end — O(n) total
result = " ".join(words)
```

### Pitfall 4: Off-by-One in Sliding Window

```python
# BROKEN: window size calculation is off by one
def max_sum_k(arr: list[int], k: int) -> int:
    window = sum(arr[:k-1])   # BROKEN: should be arr[:k]
    best = window
    for i in range(k-1, len(arr)):  # BROKEN: should start at k
        window += arr[i] - arr[i-(k-1)]  # BROKEN: should be arr[i-k]
        best = max(best, window)
    return best

# FIX: clear invariant — window always covers arr[i-k+1 .. i]
def max_sum_k_fixed(arr: list[int], k: int) -> int:
    window = sum(arr[:k])   # initial window of size k
    best = window
    for i in range(k, len(arr)):
        window += arr[i] - arr[i - k]  # add new right, remove old left
        best = max(best, window)
    return best
```

---

## 11. Technologies & Tools

| Tool / Class | Language | Notes |
|-------------|---------|-------|
| `dict` | Python | Insertion-ordered (3.7+), open addressing, resize at 2/3 |
| `defaultdict` | Python | Auto-init missing keys |
| `Counter` | Python | Frequency map, arithmetic, `most_common(k)` |
| `set` | Python | Hash-set, same O(1) ops as dict |
| `HashMap` | Java | Default cap 16, load 0.75; converts to tree-bin at 8 entries |
| `LinkedHashMap` | Java | Insertion order preserved |
| `TreeMap` | Java | Red-black BST; O(log n) all ops; sorted iteration |
| `ConcurrentHashMap` | Java | Thread-safe; segment locking (Java 7) / CAS (Java 8+) |
| `ArrayList` | Java | Dynamic array; amortized O(1) append (growth factor 1.5) |
| `array` module | Python | Typed, compact C-backed arrays — not hash maps |
| `collections.OrderedDict` | Python | Legacy; regular dict is ordered since 3.7 |

---

## 12. Interview Questions with Answers

**Q1: What is the time complexity of Python dict lookup, and what is the worst case?**
O(1) average. Worst case O(n) if all keys hash to the same bucket (e.g., adversarial keys with crafted hash collisions). Python randomises the hash seed per process (since Python 3.3) to make collision attacks impractical. For integer keys, `hash(n) == n` (for small integers) so there's no randomisation — be aware when using integer-keyed dicts for security-sensitive applications.

**Q2: What is the difference between `defaultdict` and `dict.get(key, default)`?**
`defaultdict(list)` automatically inserts a new empty `list` when a key is missing and you access it with `d[key]`. `dict.get(key, [])` returns an empty list but does NOT insert it. Use `defaultdict` when you want to immediately modify the value (e.g., `d[key].append(x)` without a prior existence check). Use `dict.get` when you only want to read a default without mutating the dict.

**Q3: Why does Java's HashMap resize at 75% capacity and not 100%?**
At 100% load factor (with open addressing or long chains), the probability of collision is very high, making average lookup close to O(n). The 0.75 threshold balances time (shorter chains, fewer collisions) against space (table is 25% empty on average). Empirically, 0.75 gives roughly 1.0 extra comparisons per lookup at steady state. The resize doubles the capacity, restoring the load factor to ~0.375.

**Q4: Two Sum — what is the O(n) hash-table solution?**
Iterate through the array; for each element `x`, check if `target - x` is in a hash map of previously seen values; if yes, return the pair. If no, store `x → index` in the map. One pass, O(n) time, O(n) space. Key insight: instead of asking "is there any y such that x + y = target?", rephrase as "was `target - x` seen before?" — a point lookup, not a search.

**Q5: How does Java HashMap's treeification (Java 8) help?**
When a single bucket's chain grows to ≥ 8 entries, it is converted to a red-black BST, giving O(log n) operations on that bucket instead of O(n). This prevents hash-collision DoS attacks (an attacker sending many keys with the same hash value) from degrading the whole map to O(n) per operation. When the bucket shrinks below 6 entries, it converts back to a linked list.

**Q6: What are the time and space complexities of `sorted()` in Python?**
O(n log n) time (Timsort). O(n) space (a separate list is returned — the original is not modified). For sorting a string: `sorted("anagram")` → O(k log k) where k = string length; then `''.join(sorted(s))` is the canonical anagram key.

**Q7: What is the longest substring without repeating characters, and what is the approach?**
Sliding window. Maintain a set of characters in the current window [left, right]. Expand right; when a duplicate is found, shrink left until the duplicate is removed. Track the maximum window size seen. O(n) time — each character enters and leaves the window at most once.

**Q8: What makes a good hash function?**
A good hash function is: (a) deterministic — same input always gives the same output; (b) fast — O(1) or O(k) for a k-byte key; (c) uniform — maps keys uniformly across the table, minimising collisions; (d) avalanche effect — a single bit change in the key changes ~50% of the hash bits, preventing clustering. Bad: summing character ASCII values (all anagrams collide). Better: polynomial rolling hash (used in Rabin-Karp and Java String's `hashCode`).

**Q9: You need to find all pairs in an array that sum to zero. What is the O(n) approach?**
Build a frequency map (`Counter`). For each distinct value x, if `-x` exists in the map, it forms a pair. Handle duplicates carefully: the pair (0, 0) requires at least two zeros; (x, -x) for x ≠ 0 requires both x and -x to be present. Deduplicate results by storing pairs with `x <= -x`.

**Q10: How does Python's `set` differ from a `frozenset`?**
Both are hash sets with O(1) membership testing. `set` is mutable (supports `add`, `discard`, `update`). `frozenset` is immutable and therefore hashable — it can be used as a dictionary key or as an element of another set. `frozenset` is useful when you need a set as a cache key (e.g., grouping states in a BFS problem).

**Q11: What is the time complexity of `in` for Python list vs set?**
`x in list` — O(n) linear scan. `x in set` — O(1) average hash lookup. The mistake of using a list where a set is appropriate is one of the most common sources of accidental O(n²) code: checking membership inside a for loop.

**Q12: How would you implement an LRU cache in O(1) time for all operations?**
Combine a `dict` (for O(1) key lookup) with a doubly-linked list (for O(1) LRU eviction). On access: move the node to the head of the list (most recently used). On eviction: remove the tail node (least recently used). Python's `OrderedDict` has `move_to_end` and `popitem(last=False)` which implement this pattern with a single built-in structure. See `case_studies/design_lru_cache.md` for the full walkthrough.

**Q13: What is Java's `LinkedHashMap` and how does it maintain insertion order?**
`LinkedHashMap` extends `HashMap` with a doubly-linked list connecting all entries in insertion (or access) order. Each entry stores `before` and `after` pointers in addition to `next` (for the hash chain). This adds O(1) overhead per operation and O(n) extra memory. Iteration is O(n) in insertion order. It is the standard Java building block for an LRU cache (`LinkedHashMap(cap, 0.75, true)` in access-order mode).

**Q14: What happens when you use a mutable default argument in Python?**
This is a Python gotcha related to hashing: `def f(lst=[])` — the default `lst` is created ONCE when the function is defined, not on each call. All calls that use the default share the same list. Mutations in one call persist in the next. Fix: `def f(lst=None): if lst is None: lst = []`. The same issue occurs with dicts and sets as default arguments.

**Q15: Given an array of integers 1..n with one duplicate, find the duplicate in O(n) time and O(1) space without modifying the array.**
Two approaches: (a) Floyd's cycle detection — treat the array as a linked list where `arr[i]` points to the next node. The duplicate creates a cycle; find the cycle entry with fast-slow pointers. (b) Sum: sum(arr) - n(n+1)/2 = the duplicate if exactly one number appears twice (fails if multiple duplicates or numbers outside 1..n).

**Q16: What is the time complexity of Python's list `.pop()` vs `.pop(0)`?**
`list.pop()` — O(1) amortized (removes the last element, no shifting). `list.pop(0)` — O(n) (removes the first element, shifts all remaining elements left). For a FIFO queue, use `collections.deque` which gives O(1) popleft. This distinction is a common performance bug in sliding window or BFS implementations.

**Q17: How do you detect if two strings are anagrams in O(n) time?**
Two strings are anagrams if they contain the same characters with the same frequencies. Approaches: (a) sort both and compare — O(k log k); (b) build a frequency counter for one, decrement for the other, check all zeros — O(k) time, O(1) space (26 characters for lowercase alphabet). The O(k) approach is preferred.

**Q18: What is the difference between `==` and `is` for checking key equality in Python dicts?**
Dict lookup uses `hash(key)` first, then `key == stored_key` (the `__eq__` method). `is` checks identity (same object in memory), not equality. Two distinct objects with the same value compare equal (`"abc" == "abc"`) but `is` would be False unless Python interns them. Never use `is` for value comparisons. For small integers (-5 to 256) and interned strings, Python caches objects so `is` incidentally returns True, but this is an implementation detail, not a language guarantee.

---

## 13. Best Practices

1. **Default to `dict` + `set`** for lookup problems — they are almost always the right tool for O(n) solutions.
2. **Use `Counter` for frequency counting** — `Counter(arr).most_common(k)` gives top-k in O(n + k log k).
3. **Use `collections.deque` for O(1) popleft** — never `list.pop(0)` in hot loops.
4. **Define the sliding window invariant in a comment** before coding the loop — it prevents off-by-one errors.
5. **Prefer `''.join(parts)` over string concatenation** in any loop that builds a string.
6. **Use `frozenset` or `tuple` as dict keys** when you need a collection as a key.
7. **For large n, pre-allocate arrays** — `[0] * n` in Python or `new int[n]` in Java to avoid repeated resizing.
8. **Normalise keys for grouping** — sort, canonical form, or a frozenset captures equivalence classes without collision.

---

## 14. Case Study: Minimum Window Substring

**Problem**: given strings `s` and `t`, find the minimum window in `s` that contains all characters of `t`. Return `""` if no such window exists.

**Approach**: sliding window with two frequency maps — `t_count` (required), `window_count` (current window). Track `formed` = number of character types that have reached their required frequency.

```python
from collections import Counter

def min_window(s: str, t: str) -> str:
    """
    O(|s| + |t|) time. O(|t|) space for the frequency maps.
    """
    if not t or not s:
        return ""

    t_count = Counter(t)
    required = len(t_count)   # distinct chars in t that must be satisfied

    left = right = 0
    formed = 0   # how many chars in window have reached required frequency
    window_count: dict[str, int] = {}
    best = (float('inf'), 0, 0)  # (length, left, right)

    while right < len(s):
        c = s[right]
        window_count[c] = window_count.get(c, 0) + 1
        if c in t_count and window_count[c] == t_count[c]:
            formed += 1

        # Contract the window from the left while it is valid
        while left <= right and formed == required:
            if right - left + 1 < best[0]:
                best = (right - left + 1, left, right)
            lc = s[left]
            window_count[lc] -= 1
            if lc in t_count and window_count[lc] < t_count[lc]:
                formed -= 1
            left += 1

        right += 1

    return "" if best[0] == float('inf') else s[best[1]:best[2] + 1]
```

**BROKEN — naive O(n² × m) approach**:
```python
# BROKEN: check every substring — O(n^2 * m) where m = len(t)
def min_window_brute(s: str, t: str) -> str:
    best = ""
    for i in range(len(s)):
        for j in range(i + 1, len(s) + 1):
            window = s[i:j]
            if all(window.count(c) >= t.count(c) for c in set(t)):
                if not best or len(window) < len(best):
                    best = window
    return best
# For |s|=10000, |t|=100: ~10^8 × 100 = 10^10 ops — infeasible
# FIX: sliding window above: O(|s|+|t|) — 10000+100 = 10100 ops
```

**Complexity**:

| Approach | Time | Space |
|----------|------|-------|
| Brute force (all substrings) | O(n² × m) | O(m) |
| Sliding window (this solution) | O(n + m) | O(m) |

**Interview discussion**: "Why does the sliding window work here?" — the window is valid if `formed == required`. Once valid, we can safely shrink from the left (any left-contracted window that becomes invalid will need to re-expand). The monotonic property: making the window larger can only keep it valid or make it valid; making it smaller can only keep it valid or make it invalid. The two-pointer never backtracks → O(n) total moves.

---

## See Also

- [complexity_analysis_and_big_o](../complexity_analysis_and_big_o/README.md) — amortized O(1) analysis of dynamic array append
- [linked_lists_stacks_and_queues](../linked_lists_stacks_and_queues/README.md) — `LinkedHashMap` uses a linked list inside the hash table
- [`java/collections_internals`](../../java/collections_internals/README.md) — HashMap secondary hash, ConcurrentHashMap, TreeMap
- [`python/collections_and_data_structures`](../../python/collections_and_data_structures/README.md) — CPython dict internals, compact dict design
- [DSA Pattern Playbooks](../dsa_patterns/README.md) — apply these structures: [Two Pointers](../dsa_patterns/two_pointers.md), [Sliding Window](../dsa_patterns/sliding_window.md), [Prefix Sum](../dsa_patterns/prefix_sum.md), [Hashing Patterns](../dsa_patterns/hashing_patterns.md), [Cyclic Sort](../dsa_patterns/cyclic_sort.md), [Matrix Traversal & Manipulation](../dsa_patterns/matrix_traversal.md)
