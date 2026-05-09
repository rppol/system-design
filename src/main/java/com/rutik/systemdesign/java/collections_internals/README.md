# Collections Internals

## 1. Concept Overview

Java's collections framework is the most-used part of the standard library. Every Java developer uses `HashMap`, `ArrayList`, `HashSet`, and `LinkedList` daily — but senior engineers understand *why* they work the way they do. HashMap internals, the treeification threshold, ArrayList growth factor, and the fail-fast iterator mechanism appear in nearly every senior Java interview.

This module covers the internal implementation of the major collection types: their data structures, time/space complexity, thread safety characteristics, and the subtle behaviors that cause production bugs.

---

## 2. Intuition

> **One-line analogy**: A HashMap is a well-organized filing cabinet — you compute a bucket number from the key (hashCode), go directly to that drawer (bucket), and look through the few items inside. The art is in choosing a good hash function so every drawer has roughly the same number of items.

**Mental model**: `HashMap` is a hash table: an array of buckets, each bucket a linked list (or tree for large buckets). `ArrayList` is a dynamic array that grows 1.5× when full. `TreeMap` is a red-black tree for sorted key access. Understanding the data structure behind each collection tells you immediately what the time complexity of each operation is.

**Why it matters**: Using `LinkedList` for random access is O(n); `ArrayList` is O(1). Using `HashMap` with a broken `hashCode()` that always returns the same value turns every operation into O(n). Choosing `LinkedHashMap` for an LRU cache vs writing a custom one. These are real engineering decisions with performance consequences.

**Key insight**: Java 8 changed `HashMap` significantly: treeification converts linked list buckets (O(n) worst case) to red-black trees (O(log n)) when bucket size exceeds 8. This was a response to hash-flooding DoS attacks that deliberately caused O(n) HashMap behavior.

---

## 3. Core Principles

- **Hash function**: `HashMap` applies a secondary spread function to `hashCode()` to reduce clustering.
- **Load factor**: When occupied buckets / total capacity > load factor (default 0.75), resize doubles capacity.
- **Treeification**: A linked list bucket is converted to a red-black tree at size 8; reverts to list at size 6.
- **Fail-fast iterators**: Collection modification during iteration (except via iterator itself) throws `ConcurrentModificationException` via `modCount`.
- **Structural modification**: Any operation that changes the collection's *size* (add/remove) is structural; pure updates (set) are not.
- **Null handling**: `HashMap` allows one null key (always bucket 0) and null values. `Hashtable`/`TreeMap` do not allow null keys.

---

## 4. Types / Architectures / Strategies

### 4.1 Collection Complexity

| Collection | get | add | remove | contains | Notes |
|------------|-----|-----|--------|----------|-------|
| ArrayList | O(1) | O(1) amort. | O(n) | O(n) | Backed by array; great random access |
| LinkedList | O(n) | O(1) | O(1) w/iter | O(n) | Doubly-linked; large pointer overhead |
| HashMap | O(1) | O(1) | O(1) | O(1) | Amortized; O(log n) with treeification |
| TreeMap | O(log n) | O(log n) | O(log n) | O(log n) | Sorted; NavigableMap |
| HashSet | O(1) | O(1) | O(1) | O(1) | HashMap wrapper (value = dummy object) |
| TreeSet | O(log n) | O(log n) | O(log n) | O(log n) | TreeMap wrapper |
| ArrayDeque | O(1) | O(1) | O(1) | O(n) | Better than Stack/LinkedList for queue |
| PriorityQueue | O(log n) | O(log n) | O(n) | O(n) | Min-heap; O(n) for arbitrary remove |

### 4.2 Thread-Safe Alternatives

| Non-thread-safe | Thread-safe Alternative | Notes |
|-----------------|------------------------|-------|
| HashMap | ConcurrentHashMap | Java 8: CAS on empty, sync on head |
| ArrayList | CopyOnWriteArrayList | Read-heavy; write is O(n) |
| LinkedList | ConcurrentLinkedDeque | Lock-free, CAS-based |
| TreeMap | ConcurrentSkipListMap | O(log n), sorted, concurrent |
| HashSet | ConcurrentHashMap.newKeySet() | No ConcurrentHashSet exists |
| PriorityQueue | PriorityBlockingQueue | Blocking operations |

### 4.3 Immutable vs Unmodifiable

| Method | Type | Modification | Null-hostile |
|--------|------|-------------|-------------|
| `List.of(...)` (Java 9+) | Immutable | UnsupportedOperationException | Yes (NPE on null) |
| `Set.of(...)` | Immutable | UnsupportedOperationException | Yes |
| `Map.of(...)` | Immutable | UnsupportedOperationException | Yes |
| `Collections.unmodifiableList(list)` | View (unmodifiable wrapper) | UnsupportedOperationException | Depends on wrapped list |
| `List.copyOf(list)` (Java 10+) | Immutable copy | UnsupportedOperationException | Yes |

Key difference: `Collections.unmodifiableList()` is a *view* — modifications to the underlying list are reflected in the unmodifiable wrapper. `List.of()` is a fully independent immutable instance.

---

## 5. Architecture Diagrams

### HashMap Internals (Java 8)
```
HashMap:
  table: Node<K,V>[]  (capacity = 16 initially)
  size: int
  modCount: int
  loadFactor: float (0.75)
  threshold: int (capacity * loadFactor = 12)

Bucket layout after put("foo", 1):
  hash("foo") = 97  -->  bucket = (16-1) & 97 = 1

  table[1] -> Node{key="foo", value=1, hash=97, next=null}

After collision:
  table[1] -> Node{"foo"} -> Node{"bar"} -> null  (linked list, up to 8 nodes)

After 8+ collisions in same bucket:
  table[1] -> TreeNode (red-black tree)

Resize at threshold (12 entries):
  newCapacity = 32
  For each entry: newBucket = (32-1) & hash
  Clever trick: only bit 5 (oldCap=16) determines new bucket:
    (hash & oldCap) == 0  -->  same bucket index
    (hash & oldCap) != 0  -->  old index + oldCap
```

### ArrayList Internal
```
ArrayList:
  elementData: Object[]  (default capacity = 10)
  size: int

add(e):
  if size == elementData.length: grow()
  elementData[size++] = e

grow():
  newCapacity = oldCapacity + (oldCapacity >> 1)  // 1.5x growth
  elementData = Arrays.copyOf(elementData, newCapacity)  // System.arraycopy

remove(index):
  numMoved = size - index - 1
  System.arraycopy(elementData, index+1, elementData, index, numMoved)
  elementData[--size] = null  // avoid memory leak
```

### LinkedHashMap for LRU Cache
```
LinkedHashMap extends HashMap:
  Adds a doubly-linked list overlay on all entries:

  head <-> entry1 <-> entry2 <-> entry3 <-> tail

  In access-order mode (accessOrder=true):
    Every get() moves the accessed entry to the tail
    Head = Least Recently Used (LRU)

  Override removeEldestEntry() to evict when capacity exceeded:

  new LinkedHashMap<K,V>(capacity, 0.75f, true) {
      protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
          return size() > maxSize;  // evict LRU when over capacity
      }
  };
```

---

## 6. How It Works — Detailed Mechanics

### HashMap Secondary Hash Spread

```java
// HashMap doesn't use hashCode() directly
// It applies a spread function to reduce clustering from bad hashCode() implementations

static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
// XOR upper 16 bits into lower 16 bits: spreads high bits into bucket calculation
// Bucket index: (n - 1) & hash  (n = capacity, power of 2)
// (n-1) is all 1s in low bits: effectively a fast modulo for power-of-2 sizes
```

### HashMap Resize — Bit-Split Trick

```java
// After resize (capacity doubles: 16 -> 32):
// New bucket = hash & (newCap - 1) = hash & 31 (5 bits)
// Old bucket = hash & 15 (4 bits)
// Extra bit: (hash & oldCap)  i.e., (hash & 16) -- bit 5

// If (hash & oldCap) == 0: entry stays in same bucket
// If (hash & oldCap) != 0: entry moves to (oldBucket + oldCap)

// Java 8 processes entries in place: no hashing needed, just bit check
// Entries in same bucket split into two lists: lo (stay) and hi (move)
// This is O(n) but with minimal allocations
```

### ConcurrentHashMap Java 8 Internals

```java
// Empty bucket: insert via CAS (no lock)
if (casTabAt(tab, i, null, new Node<>(hash, key, value))) break;

// Non-empty bucket: synchronized on HEAD node only
synchronized (f) {  // f = head node of bucket
    // Traverse list or tree to insert/update
}

// Size tracking: LongAdder-like mechanism
// No global counter; each segment tracks count; sum() aggregates

// computeIfAbsent atomicity: guaranteed — creates value ONCE even under contention
// put() with CAS/sync is atomic; size() may be slightly stale (eventually consistent)
```

### PriorityQueue — Binary Min-Heap

```java
// Internal: Object[] queue (1-indexed, root at index 1... actually 0 in Java)
// Parent of node i: (i-1) >>> 1
// Left child: 2*i + 1
// Right child: 2*i + 2

// add(e): add to end, siftUp (swim up to restore heap property)
// poll(): remove root, move last element to root, siftDown (sink down)
// peek(): O(1) — just return queue[0]

// Custom comparator:
PriorityQueue<Task> pq = new PriorityQueue<>(
    Comparator.comparingInt(Task::getPriority).reversed()  // max-heap
);
```

### Load Factor Mathematical Justification

```
Default load factor = 0.75 is based on Poisson distribution analysis of hash collisions.

For a hash table with load factor α = n/m (n entries, m buckets):
  Expected entries per bucket ≈ α (Poisson parameter λ = α)
  Probability of exactly k entries in a bucket: P(k) = e^(-α) × α^k / k!

At α = 0.75:
  P(0 entries) = e^(-0.75) ≈ 0.47  (47% of buckets are empty)
  P(1 entry)   ≈ 0.35               (35% have exactly 1 entry — ideal, O(1) lookup)
  P(2 entries) ≈ 0.13               (13% have 2 entries — O(2) lookup, fast)
  P(3+ entries) ≈ 0.05              (5% have 3+ — treeification threshold at 8)

At α = 0.5 (lower load factor):
  More empty buckets → less collision → faster lookup
  BUT: 50% of allocated memory is empty → wasteful
  More frequent resize operations

At α = 0.9 (higher load factor):
  Fewer empty buckets → more collisions → O(n) chains more common
  Less memory wasted → more space efficient
  More expensive lookups

0.75 is the mathematical sweet spot:
  Expected bucket load ≈ e^(-0.75) × 0 + e^(-0.75) × 0.75 ≈ 1 entry/bucket
  On average, one comparison per lookup
  Memory efficiency acceptable (~25% overhead for empty buckets)

For known-size maps (n entries known upfront):
  initialCapacity = ceil(n / 0.75) + 1 = n * 1.334...
  new HashMap<>(expectedEntries * 2) is a safe approximation (over-allocates ~50%)
```

### Spliterator Characteristics for Collections

```java
// Spliterator.characteristics() returns an int bitmask of flags.
// These flags tell Stream infrastructure what it can assume about the source.

// SIZED (0x40):     exactly knows its size (estimateSize() == actual size)
// SUBSIZED (0x4000): sub-spliterators after split() also have SIZED
// ORDERED (0x10):   elements have a defined encounter order (maintain that order)
// SORTED (0x4):     elements are sorted; provides Comparator (null = natural order)
// DISTINCT (0x1):   no duplicate elements
// NONNULL (0x100):  no null elements
// IMMUTABLE (0x400): source structure won't change during traversal
// CONCURRENT (0x1000): source supports concurrent modification during traversal

// ArrayList characteristics: SIZED | SUBSIZED | ORDERED
//   - SIZED: ArrayList.size() is exact and cheap
//   - SUBSIZED: after trySplit(), both halves have exact known sizes
//   - ORDERED: encounter order = insertion order
//   Why these matter for parallel streams:
//   - SIZED + SUBSIZED → stream infrastructure can split into exactly equal halves
//     without needing to traverse to count → O(1) split, efficient parallel execution
//   - ORDERED → parallel intermediate results must be assembled in order

// HashMap.KeySet characteristics: SIZED | DISTINCT
//   - NOT ORDERED (iteration order is undefined)
//   - NOT SUBSIZED (after split, sub-spliterators don't have exact size known cheaply)
//   Consequence: parallel stream over a HashMap key set is less efficient to split

// TreeSet/TreeMap characteristics: SIZED | DISTINCT | SORTED | ORDERED
//   - SORTED: enables sorted stream optimizations (e.g., findFirst() returns minimum)
//   - Parallel splitting is O(log n) via tree traversal

// Checking characteristics:
Spliterator<String> sp = list.spliterator();
boolean hasOrdering = sp.hasCharacteristics(Spliterator.ORDERED);

// Custom Spliterator for a database cursor (streaming rows):
class ResultSetSpliterator implements Spliterator<Row> {
    @Override
    public int characteristics() {
        return NONNULL;  // rows are non-null; not SIZED (unknown count until end)
    }
    // NOT SIZED: don't know row count without scanning all rows
    // Without SIZED: parallel splitting is disabled (can't split unknown-size source)
}
```

### NavigableMap Operations Depth

```java
TreeMap<Integer, String> map = new TreeMap<>();
// Fill with 1..10
for (int i = 1; i <= 10; i++) map.put(i, "v" + i);

// Point lookups (O(log n)):
map.floorKey(5);    // 5 — greatest key ≤ 5 (inclusive)
map.floorKey(4);    // 4
map.ceilingKey(5);  // 5 — smallest key ≥ 5 (inclusive)
map.lowerKey(5);    // 4 — greatest key < 5 (strictly)
map.higherKey(5);   // 6 — smallest key > 5 (strictly)

// Range views (O(1) to create; O(log n + k) to iterate k elements):
map.headMap(5);              // keys < 5: {1,2,3,4}   (exclusive upper bound)
map.headMap(5, true);        // keys ≤ 5: {1,2,3,4,5} (inclusive upper bound)
map.tailMap(7);              // keys ≥ 7: {7,8,9,10}  (inclusive lower bound)
map.tailMap(7, false);       // keys > 7: {8,9,10}    (exclusive lower bound)
map.subMap(3, 7);            // 3 ≤ key < 7: {3,4,5,6} (inclusive lower, exclusive upper)
map.subMap(3, true, 7, true);// 3 ≤ key ≤ 7: {3,4,5,6,7}

// Range views are live views (mutations reflect in original map):
NavigableMap<Integer, String> sub = map.subMap(3, 7);
sub.put(4, "updated");   // updates the original map too
sub.put(8, "v8");        // throws IllegalArgumentException — outside view range

// Descending navigation:
map.descendingKeySet();     // NavigableSet iterating in reverse order
map.descendingMap();        // NavigableMap with reversed key order

// Practical use: find all events in a time range
TreeMap<Instant, Event> events = new TreeMap<>();
Instant start = Instant.parse("2024-01-01T00:00:00Z");
Instant end   = Instant.parse("2024-02-01T00:00:00Z");
NavigableMap<Instant, Event> january = events.subMap(start, true, end, false);
// O(log n) to find start, then O(k) to iterate k events in range
```

---

## 7. Real-World Examples

- **LRU Cache**: `LinkedHashMap` with `accessOrder=true` + `removeEldestEntry()` override — used in HTTP response caching, DNS caches, browser history.
- **Word frequency count**: `HashMap<String, Integer>` with `merge()` or `getOrDefault()` + `compute()`.
- **Task scheduling**: `PriorityQueue<Task>` or `PriorityBlockingQueue` with custom priority comparator.
- **Sorted query results**: `TreeMap<LocalDate, List<Event>>` for date-range queries with `headMap`/`tailMap`/`subMap`.
- **Enum flag sets**: `EnumSet.of(Permission.READ, Permission.WRITE)` — bit-vector implementation, much faster than `HashSet<Enum>`.

---

## 8. Tradeoffs

| HashMap vs alternatives | When to use |
|------------------------|-------------|
| HashMap | General key-value, O(1) ops |
| LinkedHashMap | Preserve insertion/access order, LRU |
| TreeMap | Sorted keys, range queries |
| EnumMap | Enum keys — fastest possible, array-backed |
| IdentityHashMap | Reference equality for keys (rare) |
| WeakHashMap | Keys GC'd when no strong ref — caches |
| ConcurrentHashMap | Thread-safe, high-throughput |

---

## 9. When to Use / When NOT to Use

**Use `ArrayList`** (almost always for lists): O(1) random access, cache-friendly array traversal.

**Use `LinkedList`** only when: frequent O(1) insertions/deletions at the head/tail and you will NOT do random access. Even then, `ArrayDeque` is usually better.

**Use `ArrayDeque`** instead of `Stack` (synchronized, legacy) and `LinkedList` as a Deque (pointer overhead).

**Use `TreeMap`** when you need sorted traversal or range queries (`headMap`, `tailMap`, `floorKey`, `ceilingKey`).

**Use `EnumSet`/`EnumMap`** whenever keys are enums — they are backed by bit vectors/arrays and are 5-10× faster than `HashSet`/`HashMap` for enum keys.

**Do NOT use `LinkedList`** for: indexed access (`get(i)`), iteration where cache behavior matters, or as a `Queue` when `ArrayDeque` would do.

---

## 10. Common Pitfalls

### War Story 1: HashMap with bad hashCode() → O(n) before Java 8
A team deployed a class used as a HashMap key whose `hashCode()` always returned a constant (a common "always failing" hashCode). Before Java 8, this created a single linked list bucket of length n for all entries — O(n) lookup. Production performance degraded to unusable under load. Fix: correct `hashCode()` with field distribution; in Java 8+, treeification at bucket size 8 mitigates to O(log n) but it's still much worse than O(1).

### War Story 2: ConcurrentModificationException in production
A service iterated a `List` and called `list.remove()` inside the loop. In testing (single-threaded sequential), it worked. In production, the iterator's `modCount` check triggered `ConcurrentModificationException`. **Fix**: use `Iterator.remove()`, `removeIf()`, or collect to-remove items and bulk-remove after iteration.

### War Story 3: `List.of()` null element
A team migrated from `Arrays.asList()` to `List.of()`. `Arrays.asList()` allows null elements; `List.of()` does not — throws `NullPointerException` on null. Production code that built lists with possible null elements started failing. **Fix**: use `ArrayList` or filter nulls before `List.of()`.

### War Story 4: `toMap()` in Collectors with duplicate keys
```java
// Throws IllegalStateException if two persons have the same department as key
Map<String, Person> byDept = people.stream()
    .collect(Collectors.toMap(Person::getDept, Function.identity()));
// Fix: provide merge function
.collect(Collectors.toMap(Person::getDept, Function.identity(), (a,b) -> a));
```

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `Collections.frequency()` | Count occurrences in a collection |
| `Collections.sort()` / `List.sort()` | Sort (TimSort, stable, O(n log n)) |
| `Collections.binarySearch()` | O(log n) binary search on sorted lists |
| `Map.getOrDefault()` | Null-safe get with default |
| `Map.computeIfAbsent()` | Get or compute and store |
| `Map.merge()` | Get, apply function, store result |
| `Collections.synchronizedList/Map` | Legacy thread-safe wrappers (prefer JUC) |

---

## 12. Interview Questions with Answers

**Q1: How does HashMap handle collisions internally?**
When two keys hash to the same bucket, HashMap stores them as a linked list (chain) within that bucket. Each node has `(key, value, hash, next)`. On `get(key)`, it computes the bucket index, then traverses the chain comparing `hash` and `key.equals()`. In Java 8+, when a bucket's linked list exceeds 8 entries, it converts to a red-black tree (treeification), reducing worst-case lookup from O(n) to O(log n).

**Q2: What is treeification and when does it happen?**
Treeification converts a HashMap bucket's linked list into a red-black tree. Triggered when a single bucket's chain length exceeds `TREEIFY_THRESHOLD = 8` AND the table capacity is at least `MIN_TREEIFY_CAPACITY = 64` (otherwise resize instead). The tree reverts to a linked list when size drops below `UNTREEIFY_THRESHOLD = 6`. TreeNodes are larger than regular Nodes (more fields for tree pointers), so only high-collision buckets pay the overhead.

**Q3: How does HashMap resize without losing entries?**
When `size > capacity * loadFactor` (default: 0.75), the table doubles in size. Java 8 uses a clever bit-split: for each entry, check `(hash & oldCapacity)`. If 0, the entry stays in the same bucket index. If 1, the entry moves to `oldIndex + oldCapacity`. This requires only a bit check per entry (no re-hashing) and naturally splits the chain into two groups placed in the two new buckets.

**Q4: What is the load factor and how does it affect HashMap performance?**
Load factor determines when resize triggers: resize when `size / capacity > loadFactor`. Default 0.75 balances space vs time: too low (e.g., 0.5) wastes memory (frequent resize, sparse table); too high (e.g., 0.9) increases collision probability, degrading O(1) to O(n) in worst case. Increasing load factor reduces memory but increases average chain length. For known-size maps where no resize is desired, use `new HashMap<>(initialCapacity, 1.1f)` after computing the right capacity.

**Q5: Why is HashMap not thread-safe and what can happen under concurrent access?**
HashMap has no synchronization. Two concurrent `put()` calls can cause: (1) Lost update: both threads read `size`, both increment to same value, one increment is lost. (2) In Java 6/7: concurrent resize creates a cycle in a bucket's linked list → infinite loop in `get()`. (3) Visibility: one thread's write is not visible to another without happens-before. Use `ConcurrentHashMap` for any shared HashMap; it uses CAS for empty buckets and per-bucket synchronization for collisions.

**Q6: How does LinkedHashMap implement an LRU cache?**
In `accessOrder=true` mode, every `get()` moves the accessed entry to the tail of a doubly-linked list overlay (in addition to its bucket). The head is always the least-recently-used entry. Override `removeEldestEntry(Map.Entry eldest)` to return `true` when `size() > maxSize` — `LinkedHashMap` will automatically remove the head entry (LRU) on each new `put()`. This gives an O(1) LRU cache with minimal code.

**Q7: What is the difference between TreeMap and HashMap?**
HashMap: O(1) average get/put/remove via hash table; unordered. TreeMap: O(log n) get/put/remove via red-black tree; keys maintained in sorted order (natural or by Comparator). TreeMap implements `NavigableMap` with `floorKey`, `ceilingKey`, `headMap`, `tailMap`, `subMap` — essential for range queries. Use HashMap for raw performance; TreeMap for sorted traversal or range lookups.

**Q8: Why is ArrayList preferred over LinkedList in most cases?**
(1) Random access: ArrayList O(1), LinkedList O(n). (2) Cache locality: ArrayList stores elements contiguously in memory — CPU cache prefetch works well; LinkedList nodes scattered in heap — cache misses on traversal. (3) Memory: LinkedList nodes have 24B overhead each (object header + 2 pointers + value pointer) vs ArrayList's 4B per element reference. Only use LinkedList when you need O(1) insertions at both ends AND never need random access — and even then, `ArrayDeque` is usually better.

**Q9: What is fail-fast iteration and what causes ConcurrentModificationException?**
Collections track structural modifications with `modCount`. The iterator captures `modCount` at creation (`expectedModCount`). Before each `next()`, it checks `modCount == expectedModCount`. If the collection was structurally modified outside the iterator (add/remove), `modCount` changed, and `ConcurrentModificationException` is thrown. Fix: use `Iterator.remove()` to remove during iteration; use `removeIf()` (Java 8); or copy the collection before iterating.

**Q10: How does HashSet work internally?**
`HashSet<E>` is backed by a `HashMap<E, Object>` where all values are a single shared dummy object (`PRESENT = new Object()`). `add(e)` calls `map.put(e, PRESENT)`. `contains(e)` calls `map.containsKey(e)`. `remove(e)` calls `map.remove(e)`. All O(1) average complexity. `LinkedHashSet` is backed by `LinkedHashMap`; `TreeSet` is backed by `TreeMap`. They are thin wrappers — all behavior (null handling, load factor, treeification) follows the backing map.

**Q11: What is the difference between `List.of()` and `new ArrayList()`?**
`List.of()` (Java 9): creates an immutable list backed by a compact internal structure (array for small sizes, no `ArrayList` overhead). Cannot add/set/remove — throws `UnsupportedOperationException`. Does not allow null elements. `new ArrayList<>()` creates a mutable list. Use `List.of()` for constant lists, API return values you don't want modified; use `ArrayList` when mutation is needed.

**Q12: When would you use EnumMap and EnumSet?**
`EnumMap<K extends Enum, V>`: all keys are enum constants → backed by a plain array indexed by ordinal. O(1) all operations with virtually zero overhead; iteration in declaration order. Use whenever the key type is an enum — it's 5–10× faster than `HashMap` for this use case. `EnumSet`: bit-vector backed — an `EnumSet` with up to 64 enums fits in a single `long` bitmask. `contains`, `add`, `remove` are `&`, `|`, `& ~` bitmask operations. Fastest possible set for enums; use for permission/flag sets.

**Q13: What is the initial capacity of HashMap and why does it matter for performance?**
Default initial capacity is 16. If you know the expected number of entries N, set initial capacity to `N / loadFactor + 1` (e.g., 100 entries: `100 / 0.75 + 1 ≈ 134`, round up to power of 2 = 256). Providing correct initial capacity avoids multiple expensive resize operations. Each resize is O(n): allocate new array, rehash/redistribute all entries. In tight loops inserting many entries, this matters — a HashMap loaded to 10,000 entries from default 16 resizes ~10 times.

**Q14: Why is 0.75 the default load factor for `HashMap`?**
The 0.75 default is based on Poisson distribution analysis of hash collision probabilities. At load factor α = 0.75, the expected number of entries per bucket approaches 1 — giving O(1) average lookup. The probability of a bucket having 8 or more entries (treeification threshold) is extremely small (~6×10⁻⁸). Too low (e.g., 0.5): more memory wasted (50% empty buckets), more frequent resizes. Too high (e.g., 0.9): more collisions, longer chains, O(n) degradation. The JDK source comment says the 0.75 value is a compromise between time and space that results in approximately 1 comparison per lookup on average with a good hash function.

**Q15: What Spliterator characteristics does `ArrayList` have, and why do they matter for parallel streams?**
`ArrayList`'s `Spliterator` has three characteristics: `SIZED` (knows exact size via `size()` in O(1)), `SUBSIZED` (sub-spliterators after `trySplit()` also have exact known sizes), and `ORDERED` (encounter order = insertion order). These matter for parallel streams because: (1) `SIZED + SUBSIZED` allow the stream framework to split into exactly equal halves at O(1) cost — each half has known size without traversal. This enables efficient work-stealing in `ForkJoinPool`. (2) Without `SIZED`, splitting would require traversal to count elements. (3) `ORDERED` means the final result must preserve encounter order, which adds merge overhead in parallel — sometimes removing ordering (via `.unordered()`) improves parallel performance.

---

## 13. Best Practices

1. **Use `HashMap` by default** for key-value; switch to `TreeMap` only when sorted order is needed.
2. **Override both `equals()` and `hashCode()`** for HashMap keys — IDE can generate these.
3. **Specify initial capacity** for large maps to avoid resize: `new HashMap<>(expectedSize * 2)`.
4. **Use `computeIfAbsent()`** for lazy initialization in maps (atomic in `ConcurrentHashMap`).
5. **Prefer `ArrayDeque` over `Stack` and `LinkedList`** as a queue/deque.
6. **Use `EnumSet`/`EnumMap`** when keys are enums.
7. **Use `List.of()` / `Set.of()` / `Map.of()`** for known-at-construction-time immutable collections.
8. **Use `Collections.unmodifiableList()` only as a defensive view** — not as a true immutable collection.
9. **Use `removeIf()`** instead of iterating and removing — cleaner and avoids `ConcurrentModificationException`.
10. **Monitor `ConcurrentHashMap` `size()`** — it's approximate; use carefully in capacity-tracking logic.

---

## 14. Case Study

### Implementing an O(1) Frequency Counter with HashMap

**Problem**: Given a stream of events, answer `topK(int k)` in O(k log k), and answer `frequency(Event e)` in O(1).

```java
public class FrequencyCounter<T> {
    private final Map<T, Integer> freqMap = new HashMap<>();

    public void record(T item) {
        freqMap.merge(item, 1, Integer::sum);  // Java 8: increment or insert 1
    }

    public int frequency(T item) {
        return freqMap.getOrDefault(item, 0);
    }

    public List<Map.Entry<T, Integer>> topK(int k) {
        return freqMap.entrySet().stream()
            .sorted(Map.Entry.<T, Integer>comparingByValue().reversed())
            .limit(k)
            .collect(Collectors.toList());
        // O(n log n) — use PriorityQueue for O(n log k) in large maps
    }

    public List<Map.Entry<T, Integer>> topKOptimized(int k) {
        // O(n log k) using min-heap of size k
        PriorityQueue<Map.Entry<T, Integer>> heap =
            new PriorityQueue<>(k + 1, Map.Entry.comparingByValue());
        for (Map.Entry<T, Integer> entry : freqMap.entrySet()) {
            heap.offer(entry);
            if (heap.size() > k) heap.poll();  // remove min
        }
        List<Map.Entry<T, Integer>> result = new ArrayList<>(heap);
        result.sort(Map.Entry.<T, Integer>comparingByValue().reversed());
        return result;
    }
}
```

**Key collections concepts**: `merge()` for atomic-ish increment, `getOrDefault()` for null-safe get, `PriorityQueue` as min-heap for top-K.
