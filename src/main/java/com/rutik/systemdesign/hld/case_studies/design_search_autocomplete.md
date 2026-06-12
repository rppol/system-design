# System Design: Search Autocomplete (Typeahead)

## Intuition

> **Design intuition**: Autocomplete looks like a tiny feature bolted onto a search box, but it is one of the most latency-sensitive systems any engineer will build — every single keystroke is a query, and the response has to feel instantaneous or the feature feels broken and gets disabled by the user. The entire design pivots on one observation from the scale estimation: the data structure that answers "what are the top suggestions for this prefix?" is small enough to fit entirely in memory and be replicated everywhere, which eliminates the sharding problem that dominates almost every other large-scale system in this repo.

**Key insight**: Because the precomputed suggestion trie for the top 5M queries fits in roughly 1.25GB of RAM, the right architecture is "replicate the whole thing on every node, rebuild it offline every ~10 minutes, and hot-swap it in" — not "shard it by prefix." Sharding by prefix (e.g., "a-m" on shard 1, "n-z" on shard 2) looks tempting but creates a permanent hotspot, because query prefixes are not uniformly distributed across the alphabet (far more English queries start with "s" than with "x"). Combine the replicated trie with a fast, separate ~1-minute trending-detection pipeline, and you get a system that is both extremely fast on the common case and responsive to breaking news within a minute — without ever needing the full 10-minute rebuild to complete first.

---

## 1. Requirements Clarification

### Functional Requirements
- **Prefix suggestions**: As a user types into a search box, return the top-K (typically 5-10) suggestions that complete or extend the current prefix, ranked by popularity/relevance.
- **Near-real-time freshness**: Suggestions should reflect recent query trends within minutes, not hours — a term that suddenly spikes (breaking news, a viral event) should start appearing in autocomplete quickly.
- **Personalization**: Incorporate a per-user recent-search history so that a user's own past searches can surface ahead of (or alongside) globally popular suggestions for the same prefix.
- **Debounced interaction**: The client should avoid firing a request on every single keystroke if the user is typing quickly — wait for a short pause before issuing the request.
- **(Stretch) Typo tolerance**: Suggest corrections or completions even when the prefix contains a minor typo (e.g., "neflix" -> "netflix"), via edit-distance or n-gram matching.

### Non-Functional Requirements
- **Extremely low latency**: p99 < 100ms for a suggestion request — this must "feel instant" on every keystroke; anything slower and the UI feels laggy and users stop trusting/using the dropdown.
- **Very high read QPS**: Every keystroke is a read request, so QPS is roughly an order of magnitude higher than the underlying search QPS itself (see §2 for the exact multiplier).
- **Eventual consistency for trends**: It is fine for the global "top suggestions" list to be a few minutes stale under normal conditions — but a sudden spike in one query's popularity (a "celebrity" event or breaking news) needs a much faster update path than the normal rebuild cadence.
- **High availability**: The autocomplete service must degrade gracefully — if it's slow or down, the search box should simply show no suggestions, never block or slow down the underlying search itself.
- **Spike resilience**: A single query suddenly becoming extremely popular (a "thundering herd" on one prefix) must not degrade latency for everyone else.

### Out of Scope
- The underlying search/ranking engine itself (we assume it exists and just needs a typeahead layer in front of it).
- Voice search and image search suggestions.
- Multi-language tokenization nuances (CJK segmentation, RTL languages) — noted as a real-world complexity in §6 but not designed in depth here.

---

## 2. Scale Estimation

### Baseline: The Parent Search Engine
- The parent search engine handles **1 billion searches/day**.
- 1B / 86,400 sec = **~11,600 searches/sec average**.

### Typeahead Request Volume
- Typeahead fires roughly **once per keystroke** as the user types (after debouncing collapses bursts of keystrokes into one request per ~100-150ms pause).
- An average submitted query is **~20 characters** long.
- Even with debouncing, a user typically pauses enough times while typing a 20-character query to generate roughly **20 typeahead requests** per submitted search (one per "settled" prefix length, since users type in bursts and pause between words/syllables).
- Total typeahead volume: **1B searches/day * 20 = 20B typeahead requests/day**.
- 20B / 86,400 sec = **~231,000 requests/sec average (~231K/sec)**.
- Peak (assume ~2.2x average for typing-heavy peak hours): **~500,000 requests/sec (~500K/sec)**.

This is the number that drives everything else in this design: **500K QPS at <100ms p99** is roughly 20x the QPS of the underlying search engine itself, which is why the typeahead layer cannot simply proxy every keystroke to the main search index — it needs its own, radically simpler, radically faster data path.

### Trie Sizing — The Number That Shapes the Whole Architecture
- Track the **top 5 million unique queries** (covers the overwhelming majority of real-world prefix traffic; the long tail of one-off queries doesn't need to appear in autocomplete).
- Each trie node costs roughly:
  - **~50 bytes** for the node itself (character, child-pointer map/array, frequency counter, bookkeeping).
  - Plus a cached **top-10 completions list**, each entry ~20 bytes (a compact reference to a query string + a frequency score) -> **~200 bytes**.
  - **Total per node: ~250 bytes**.
- 5M nodes (one per unique prefix/query path through the trie, conservatively treating each tracked query as roughly contributing one "interesting" node) * 250 bytes = **~1.25 GB**.

> **This single number — ~1.25GB — is the architectural pivot of the whole design.** A data structure that's ~1.25GB:
> - Fits comfortably in the RAM of a single commodity server (even a modest instance has 16-64GB RAM).
> - Can be **fully replicated** on every read replica in the fleet, rather than sharded.
> - Can be rebuilt from scratch and shipped to every replica every ~10 minutes without straining network bandwidth (1.25GB to, say, 25 replicas every 10 minutes = ~3.1GB/min aggregate transfer — trivial for a modern data center network).
>
> Compare this to a system where the index is, say, 5TB — there, replicating the full index everywhere would be absurd, and sharding by prefix range (with the attendant hot-shard problems discussed in §4.2 and §9 War Story 4) becomes unavoidable. The entire shape of this design follows from the index being small enough to replicate.

### Query Log Volume (for Trend Aggregation)
- 1B searches/day, each generating a query-log event of roughly **~100 bytes** (query text, timestamp, anonymized user/session bucket, result-click signal).
- 1B * 100 bytes = **~100 GB/day raw query-log volume**.
- This stream is aggregated **hourly** (for the full corpus) by an offline batch job, with a much faster **~1-minute sliding window** aggregation running in parallel for trend detection (§4.4).

### Summary Table

| Metric | Value |
|---|---|
| Parent search engine volume | 1B searches/day (~11.6K/sec avg) |
| Typeahead requests/day | ~20B/day |
| Typeahead QPS (avg) | ~231K/sec |
| Typeahead QPS (peak) | ~500K/sec |
| Tracked unique queries in trie | 5M |
| Bytes per trie node (incl. top-10 cache) | ~250 bytes |
| Total trie size | ~1.25 GB |
| Trie replication strategy | Full replica on every node (not sharded) |
| Raw query-log volume | ~100 GB/day |
| Full rebuild cadence | ~10 minutes |
| Trending-detection window | ~1 minute |

---

## 3. High-Level Architecture

```
                                +----------------------+
                                |       Client        |
                                |  (debounces ~100-150ms |
                                |   between keystrokes) |
                                +----------+-----------+
                                           |
                                           | GET /autocomplete?q=<prefix>
                                           v
                          +--------------------------------+
                          |        Load Balancer           |
                          +----------------+----------------+
                                           |
                       +-------------------+-------------------+
                       |                                       |
                       v                                       v
            +---------------------+                +---------------------+
            |  Redis hot-prefix   |  cache miss /  |  Typeahead Service  |
            |  cache (absorbs     |  bypass for    |  Replica 1..N       |
            |  spikes on the few  |---------------> |  (in-memory TRIE,  |
            |  hottest prefixes,  |  rare prefixes  |   FULL replica of  |
            |  e.g. "a", "th")    |                 |   ~1.25GB index)   |
            +---------------------+                +----------+----------+
                       ^                                       ^
                       |                                       | hot-swap new
                       | overlay results                       | trie snapshot
                       |                                       | every ~10 min
            +---------------------+                +----------+----------+
            |  Trending Detector  |                |  Aggregator Pipeline |
            |  (~1-min sliding    |<---------------|  (Spark/Flink batch  |
            |  window over the    |   query log    |   job every ~10 min: |
            |  query-log stream;  |   stream       |   recompute top-K    |
            |  flags sudden spikes|                |   per prefix, build  |
            |  and OVERLAYS them  |                |   new trie snapshot) |
            |  onto trie results) |                +----------+----------+
            +----------+----------+                           ^
                       ^                                       |
                       |                                       |
                       +------------------+--------------------+
                                           |
                                  +--------+---------+
                                  |   Kafka Topic    |
                                  |  (query log /    |
                                  |  search events)  |
                                  +------------------+
```

### Read Path (the hot path — must be <100ms p99)
1. Client debounces keystrokes (waits ~100-150ms after the last keystroke before firing a request) — see §4.5 and §11 for why this matters on both sides.
2. Request hits the load balancer, which routes to any Typeahead Service replica (since every replica holds an identical full trie, **any replica can answer any prefix** — no routing-by-prefix needed).
3. For the tiny set of extremely hot single/double-character prefixes, a thin Redis cache in front absorbs the bulk of the load (mostly to protect against load *spikes*, not because the trie lookup itself is slow — see §4.2 and §5).
4. The Typeahead Service walks the in-memory trie to the node for the given prefix — O(prefix length), typically 1-20 character lookups — and returns that node's **precomputed** top-K list. No subtree traversal happens at request time (§4.1).
5. The Trending Detector overlays any currently-spiking term that matches the prefix onto the front of the result list (§4.4).
6. The response also merges in the user's personalized recent searches, if any (§4.5).

### Write / Update Path (offline, off the hot path)
1. Every search request (and, optionally, every typeahead request that resulted in a click) is logged as an event to a Kafka topic.
2. The **Aggregator Pipeline** (Spark or Flink) consumes this stream in ~10-minute batches, recomputes per-prefix frequency counts and top-K lists, and builds a brand-new trie snapshot from scratch on a separate host/process.
3. The new snapshot is **hot-swapped** into the live replica fleet (build-then-swap — never mutate the live trie in place; see §4.3 and §9 War Story 1).
4. In parallel, the **Trending Detector** runs a much faster (~1-minute) sliding-window aggregation over the same Kafka stream, looking for terms whose frequency has spiked sharply relative to their recent baseline, and pushes "override" entries that the Typeahead Service overlays onto trie results in real time (§4.4).

---

## 4. Component Deep Dives

### 4.1 Trie with Precomputed Top-K Completions

The central data structure is a trie (prefix tree) where **every node caches the top-K most popular completions for the prefix that ends at that node**. This precomputation happens **offline**, during the aggregation pass (§4.3) — so an online query is reduced to:

1. Walk down the trie one character at a time for each character in the user's prefix — O(prefix length), typically 1-20 steps.
2. Return the precomputed top-K list stored at that node.

Critically, step 2 does **not** require traversing the subtree under that node to find the most popular completions — that work was already done offline. This is what makes a query at 500K QPS with a <100ms budget feasible: the online cost is a handful of hash-map lookups, not a search over potentially millions of descendant nodes.

```java
import java.util.*;

/**
 * A single node in the autocomplete trie. Each node represents one
 * character position along some prefix, and caches the top-K most
 * popular full queries that share that prefix.
 */
public class TrieNode {

    // Children keyed by the next character.
    private final Map<Character, TrieNode> children = new HashMap<>();

    // The precomputed top-K completions for the prefix ending at this node,
    // kept as a small min-heap of size <= K, ordered by frequency (lowest at root).
    // Using a min-heap of fixed size K means: to insert a new candidate,
    // compare against the current minimum and evict if the new one is larger.
    private final int topK;
    private final PriorityQueue<QueryFrequency> topKHeap;

    // Fast lookup to update an existing query's frequency within the heap
    // without a full linear scan.
    private final Map<String, QueryFrequency> topKByQuery = new HashMap<>();

    public TrieNode(int topK) {
        this.topK = topK;
        // Min-heap: smallest frequency at the head, so we can cheaply
        // evict the least-popular entry when a more popular one arrives.
        this.topKHeap = new PriorityQueue<>(
                Comparator.comparingLong(qf -> qf.frequency)
        );
    }

    public Map<Character, TrieNode> children() {
        return children;
    }

    /**
     * Update this node's top-K cache with a (query, frequency) pair.
     * Called once per ancestor node during insert(), so that every
     * prefix along the path "knows" about this completion if it's
     * popular enough to make that prefix's top-K.
     */
    void offerCandidate(String query, long frequency) {
        QueryFrequency existing = topKByQuery.get(query);
        if (existing != null) {
            // Update frequency in place; re-heapify by removing and re-adding.
            topKHeap.remove(existing);
            existing.frequency = frequency;
            topKHeap.offer(existing);
            topKByQuery.put(query, existing);
            return;
        }

        QueryFrequency candidate = new QueryFrequency(query, frequency);

        if (topKHeap.size() < topK) {
            topKHeap.offer(candidate);
            topKByQuery.put(query, candidate);
            return;
        }

        // Heap is full — only replace the minimum if this candidate is more popular.
        QueryFrequency currentMin = topKHeap.peek();
        if (currentMin != null && frequency > currentMin.frequency) {
            topKHeap.poll();
            topKByQuery.remove(currentMin.query);
            topKHeap.offer(candidate);
            topKByQuery.put(query, candidate);
        }
    }

    /**
     * Return the cached top-K completions for this node, sorted by
     * descending frequency (most popular first) — this is the O(K log K)
     * sort done once per request over a tiny K (e.g., K=10), not over
     * the whole subtree.
     */
    public List<String> getTopK() {
        List<QueryFrequency> snapshot = new ArrayList<>(topKHeap);
        snapshot.sort((a, b) -> Long.compare(b.frequency, a.frequency));
        List<String> result = new ArrayList<>(snapshot.size());
        for (QueryFrequency qf : snapshot) {
            result.add(qf.query);
        }
        return result;
    }

    /** Simple holder for a (query, frequency) pair stored in the top-K heap. */
    private static final class QueryFrequency {
        final String query;
        long frequency;

        QueryFrequency(String query, long frequency) {
            this.query = query;
            this.frequency = frequency;
        }
    }
}
```

```java
/**
 * The autocomplete trie itself. Built/rebuilt OFFLINE by the aggregation
 * pipeline (see §4.3); the live service only ever performs read-only
 * getTopK(prefix) lookups against a fully-built snapshot.
 */
public class AutocompleteTrie {

    private static final int DEFAULT_TOP_K = 10;

    private final TrieNode root;
    private final int topK;

    public AutocompleteTrie() {
        this(DEFAULT_TOP_K);
    }

    public AutocompleteTrie(int topK) {
        this.topK = topK;
        this.root = new TrieNode(topK);
    }

    /**
     * Insert a (query, frequency) pair into the trie. This is called once
     * per unique query during the offline build, with `frequency` being
     * the aggregated count from the query log for that exact query string.
     *
     * For every ancestor node along the path to the full query (including
     * the root, representing the empty prefix), we offer this query as a
     * candidate for that node's top-K cache. This "bubble up" is what makes
     * getTopK(prefix) an O(1)-ish lookup later: the work is paid once here,
     * at build time, for every prefix length.
     */
    public void insert(String query, long frequency) {
        TrieNode current = root;
        // The empty prefix (root) also tracks global top-K, useful as a
        // "trending overall" fallback when the user hasn't typed anything yet.
        current.offerCandidate(query, frequency);

        for (char ch : query.toCharArray()) {
            current = current.children()
                    .computeIfAbsent(ch, c -> new TrieNode(topK));
            current.offerCandidate(query, frequency);
        }
    }

    /**
     * Online query path: walk to the node for `prefix` and return its
     * precomputed top-K list. O(prefix.length()) trie traversal +
     * O(K log K) sort of a tiny fixed-size list — comfortably within
     * the <100ms p99 budget even at 500K QPS.
     *
     * Returns an empty list if no indexed query starts with this prefix
     * (the "long tail" case discussed in §11).
     */
    public List<String> getTopK(String prefix) {
        TrieNode current = root;
        for (char ch : prefix.toCharArray()) {
            current = current.children().get(ch);
            if (current == null) {
                return Collections.emptyList();
            }
        }
        return current.getTopK();
    }
}
```

A few important notes on this design:

- **`insert` is O(query length * K)** because it touches every ancestor node and each `offerCandidate` call is O(log K) for the heap operations. For 5M queries with average length ~15 and K=10, that's roughly `5M * 15 * log2(10) ≈ 5M * 15 * 3.3 ≈ 250M` heap operations — a batch job running every 10 minutes has ample time for this (well under a minute on a single modern core, and trivially parallelizable across query shards by first character).
- **`getTopK` is O(prefix length + K log K)** with no dependency on how many total queries share that prefix — this is the entire point. A naive approach (walk the subtree under the prefix node and find the K most frequent leaves on every request) would be O(subtree size), which for a popular single-character prefix like "a" could mean scanning hundreds of thousands of nodes *per request* — utterly incompatible with 500K QPS.
- The trie built by `insert` is **immutable once built** — the live service treats `AutocompleteTrie` instances as read-only snapshots and never calls `insert` on a snapshot that's actively serving traffic (see §4.3 for the build-then-swap process this implies).

---

### 4.2 Why "Fully Replicated, Not Sharded" Works Here

Given the ~1.25GB trie size from §2, the design choice is: **every Typeahead Service replica holds a complete, identical copy of the trie in memory.**

**What this buys us:**
- A request for **any** prefix can be answered by **any** replica — the load balancer can use simple round-robin or least-connections, with zero awareness of which prefixes "live" where.
- No cross-shard fan-out: a single in-memory trie walk answers the query completely. Compare this to a sharded design, where a query for prefix "se" might need to ask shards for "se*" *and* merge partial top-K lists from multiple shards if the shard boundary falls in an awkward place.
- Replica failure is trivial to handle: any healthy replica is a complete substitute for any other.

**What it costs us:**
- Every rebuild (§4.3) must push a new ~1.25GB snapshot to **every** replica, not just one shard's worth of replicas. At, say, 25 replicas (see §10), that's ~31GB of data movement every 10 minutes — easily handled by a modern data-center network (a single 10Gbps link moves 1.25GB in ~1 second), but it is a cost that scales linearly with replica count, which matters when planning for 10x growth.
- Memory cost is "wasted" in the sense that 25 replicas each hold the same 1.25GB — but at ~1.25GB per node, this is a rounding error compared to the memory most services already allocate for connection pools, JIT-compiled code, JVM heap overhead, etc.

**The alternative — sharding by prefix — and why it's worse here:**

Imagine instead the index were too large to replicate (say, 500GB), forcing a sharded design: shard 1 handles prefixes "a"-"i", shard 2 handles "j"-"r", shard 3 handles "s"-"z", for example. This immediately runs into the **hot-shard problem**: query-prefix distribution is wildly non-uniform across the alphabet. In English, a disproportionate share of words and queries begin with "s" — historically, naive first-character sharding has put as much as **~15% of all autocomplete traffic onto a single shard** (see §9 War Story 4), creating a permanent capacity imbalance that no amount of replica tuning within that shard can fully fix without either (a) further sub-sharding "s" specifically (a special case that has to be re-tuned as language usage drifts), or (b) abandoning prefix-based sharding for a hash-based scheme — which then breaks the property that a single trie walk can answer a prefix query, since a hash-sharded index has no notion of "prefix" locality at all.

The fully-replicated approach sidesteps this problem **entirely**: there is no shard key, so there is no possibility of a hot shard. This is the single biggest architectural payoff of the ~1.25GB size constraint from §2 — and it's why, if the design ever needs to track 500M unique queries instead of 5M (a 100x increase, pushing the trie toward ~125GB), this whole section would need to be revisited and a sharded (or Redis-sorted-set-based, §5) design would become necessary.

---

### 4.3 Offline Aggregation Pipeline (Build-Then-Swap)

The trie is **never mutated in place** while serving traffic. Instead:

```
 1. Query events flow continuously into a Kafka topic ("query-log").
 2. Every ~10 minutes, a Spark/Flink batch job:
      a. Reads the last window of query-log events (plus carries forward
         decayed historical counts, so popularity doesn't reset every 10 min).
      b. Aggregates frequency counts per unique query string.
      c. Builds a brand-new AutocompleteTrie from scratch, on a SEPARATE
         host/process — NOT on any of the live-serving replicas.
      d. Serializes the new trie to a compact binary snapshot (~1.25GB).
 3. The new snapshot is distributed to every Typeahead Service replica
    (e.g., via a blob store + pull, or a push-based fan-out).
 4. Each replica loads the new snapshot into memory ALONGSIDE the old one
    (briefly, during the swap window), then ATOMICALLY swaps a pointer/
    reference so that new requests are served from the new trie, and the
    old trie's memory is released once in-flight requests on it complete.
 5. The old trie is garbage collected; the cycle repeats in ~10 minutes.
```

This **build-then-swap** pattern is the answer to two of the four war stories in §9:

- **It guarantees the live trie is always either the "old, complete" snapshot or the "new, complete" snapshot — never a half-built one.** A query during a rebuild is never served partial/inconsistent results.
- **It bounds the memory overhead of a rebuild to a brief, controlled window** (the moment of swap, where both old and new snapshots are momentarily resident) rather than letting old and new tries coexist for the entire multi-minute build duration (§9 War Story 1).

The "carry forward decayed historical counts" detail in step 2a matters: if each 10-minute window only counted *that window's* queries with no memory of history, a query that's popular every day but didn't happen to occur in the last 10 minutes would vanish from the trie entirely. A simple exponential decay (`new_score = 0.9 * old_score + new_window_count`) keeps long-term-popular queries stable while still letting genuinely new trends rise.

---

### 4.4 Trending / Real-Time Layer

The 10-minute rebuild cadence is far too slow for breaking news — if a major event happens, users start typing related queries almost immediately, and a 10-minute lag before those queries appear in autocomplete is a visibly broken experience (§9 War Story 2).

The **Trending Detector** is a *separate*, *faster* pipeline that runs alongside the aggregation pipeline:

```
 1. Consumes the same Kafka query-log stream as the aggregator.
 2. Maintains a ~1-MINUTE sliding window of per-query counts.
 3. For each query, compares its count in the current 1-minute window
    against its expected/baseline count (e.g., its rolling average over
    the last few hours at this time of day).
 4. If a query's current-window count exceeds its baseline by a large
    multiplier (e.g., 20x or more) AND crosses a minimum absolute
    threshold (to avoid flagging low-volume noise), it is flagged as
    "trending" and pushed to a small, fast-access "trending overrides"
    store (e.g., a Redis hash or an in-memory map replicated via pub/sub).
 5. The Typeahead Service, on every request, checks this trending-overrides
    store for entries matching the current prefix and OVERLAYS them onto
    (typically prepends them to) the trie's precomputed top-K results.
```

This overlay mechanism is what makes "breaking news appears in autocomplete within ~1 minute" possible **despite** the 10-minute full-rebuild cadence: the trending layer doesn't wait for or require a rebuild at all. It's a thin, fast, additive layer on top of the (slightly stale, by design) trie.

The trending-overrides store is intentionally tiny — at any given moment, only a handful of terms are genuinely "spiking" — so it can be held in memory on every replica (via a lightweight pub/sub broadcast) or in a small shared Redis structure with negligible lookup cost.

---

### 4.5 Personalization

Each user has a small, per-user list of recent searches (e.g., the last 10-20 queries they've issued), stored either client-side (local storage, for instant display with zero network round-trip) or server-side keyed by user/session ID (for cross-device consistency).

The response-merging logic is:

```
function getSuggestions(prefix, userId):
    global_results   = trie.getTopK(prefix)              // §4.1
    trending_results = trendingStore.getOverrides(prefix) // §4.4
    personal_results  = personalHistory.matchPrefix(userId, prefix)

    // Merge order: personal matches first (most relevant to THIS user),
    // then trending overrides (timely/relevant to everyone right now),
    // then global top-K, deduplicated, truncated to top-K total.
    merged = dedupe(personal_results + trending_results + global_results)
    return merged[:K]
```

Because the personal history list is small (10-20 entries per user) and the prefix-matching against it is a simple linear scan over short strings, this adds negligible latency even at 500K QPS — and critically, **it never touches the shared trie**, so one user's personalization can never leak into or affect another user's results (a property explicitly called out in §11).

### 4.6 Sharded Trie Design — What Changes If the Corpus Grows 100x

§4.2 was explicit that "fully replicated, not sharded" works *because* the corpus is ~1.25GB. If the tracked-query count grew from 5M to 500M (e.g., expanding coverage from "head + torso" queries to most of the "long tail" too), the trie balloons to roughly ~125GB — too large to replicate on every one of ~35 replicas without an unreasonable memory footprint per host. This subsection sketches the design that *replaces* §4.2 at that scale, because it's one of the most common interview follow-ups (§11) and because the failure mode of getting it wrong (re-introducing §9 War Story 4's hot-shard problem) is a direct trap.

**The key constraint that survives from §9 War Story 4**: sharding by *prefix* (first character, or any prefix-range partitioning) is still wrong, for the same reason — query-prefix distribution is wildly non-uniform, and a "s"-shard would still absorb ~15% of traffic regardless of corpus size. The fix at 500M queries is to shard by **`hash(full query string)`** instead — this distributes load evenly across shards, but it has a structural consequence: completions of the *same prefix* (e.g., "sea -> seattle", "sea -> search", "sea -> seafood") now land on **different shards**, because each full query string hashes independently. A single shard can no longer answer "what's the global top-K for prefix 'sea'?" on its own.

**The fix is scatter-gather**: broadcast every `getTopK(prefix)` request to *all* shards in parallel, let each shard return its own local top-K candidates for that prefix (computed via the same precomputed-trie mechanism from §4.1, just over its slice of the corpus), and merge the per-shard results into a single global top-K using a small bounded min-heap — structurally the same heap-eviction pattern as `TrieNode.offerCandidate` (§4.1), just applied across shard responses instead of across a single trie's children:

```java
import java.util.*;
import java.util.concurrent.*;

/**
 * Scatter-gather client used ONLY once the corpus exceeds full-replication
 * size (~125GB+, see Q in section 11). Each of N shards holds a complete
 * trie for a hash-partitioned slice of the corpus, partitioned by
 * hash(full query string) -- NOT by prefix, to avoid the first-letter
 * hot-shard problem from War Story 4. Because completions of the same
 * prefix can land on different shards, every request is broadcast to all
 * shards and the per-shard top-K lists are merged into one global top-K.
 */
public class ShardedAutocompleteClient {

    private final List<AutocompleteShardClient> shardClients;
    private final int topK;
    private final ExecutorService executor;

    public ShardedAutocompleteClient(List<AutocompleteShardClient> shardClients,
                                      int topK, ExecutorService executor) {
        this.shardClients = shardClients;
        this.topK = topK;
        this.executor = executor;
    }

    /**
     * Broadcasts getTopK(prefix) to every shard in parallel, then merges up
     * to (shardCount * topK) candidates into a single global top-K list
     * using a min-heap of size topK.
     */
    public List<ScoredSuggestion> getTopK(String prefix) {
        List<Future<List<ScoredSuggestion>>> futures = new ArrayList<>();
        for (AutocompleteShardClient shard : shardClients) {
            futures.add(executor.submit(() -> shard.getTopK(prefix, topK)));
        }

        PriorityQueue<ScoredSuggestion> merged = new PriorityQueue<>(
                Comparator.comparingLong(s -> s.frequency)
        );

        for (Future<List<ScoredSuggestion>> future : futures) {
            // A slow or failed shard contributes an empty list rather than
            // failing the whole request -- partial results beat none, per
            // the "must degrade gracefully" NFR in section 1.
            for (ScoredSuggestion candidate : safeGet(future)) {
                if (merged.size() < topK) {
                    merged.offer(candidate);
                } else if (merged.peek() != null
                        && candidate.frequency() > merged.peek().frequency()) {
                    merged.poll();
                    merged.offer(candidate);
                }
            }
        }

        List<ScoredSuggestion> result = new ArrayList<>(merged);
        result.sort((a, b) -> Long.compare(b.frequency(), a.frequency()));
        return result;
    }

    private List<ScoredSuggestion> safeGet(Future<List<ScoredSuggestion>> future) {
        try {
            return future.get(30, TimeUnit.MILLISECONDS);
        } catch (Exception timeoutOrError) {
            return Collections.emptyList();
        }
    }

    /** A suggestion plus its global frequency score, returned by one shard. */
    public record ScoredSuggestion(String query, long frequency) {}

    /** Per-shard RPC client -- wraps a network call to one shard's Typeahead Service. */
    public interface AutocompleteShardClient {
        List<ScoredSuggestion> getTopK(String prefix, int topK);
    }
}
```

**What this costs, relative to §4.2's design**: (1) every request now fans out to N shards instead of hitting one replica — tail latency becomes `max()` over N parallel RPCs rather than a single in-process trie walk, which is why each per-shard call gets an aggressive 30ms timeout (well inside the <100ms p99 budget) and a missing/slow shard simply contributes nothing rather than blocking the merge; (2) the merge step itself is `O(N * topK log topK)` — for N=16-32 shards and topK=10, that's a few hundred heap operations per request, still cheap relative to the network round-trips; (3) operationally, this reintroduces a real shard count to manage (rebalancing, hot-shard monitoring) that §4.2 deliberately avoided. **The decision rule**: stay on §4.2's fully-replicated design as long as the trie fits comfortably in replica memory (low tens of GB is a reasonable practical ceiling); cross over to this scatter-gather design only when corpus growth genuinely forces it, since it trades a strictly simpler, lower-latency architecture for one that scales further.

### 4.7 Multi-Region Replication

A single-region deployment of ~35 replicas (§10) doesn't serve a global user base well — a user in Singapore querying a Typeahead Service replica in `us-east` pays 150-200ms of pure network round-trip, which alone blows the entire <100ms p99 budget (§1) before the trie lookup even happens. The fix is the familiar one: deploy replica pools in multiple regions and route each user to the nearest one.

```
                     +-------------------------------+
                     |   Global Aggregator (us-east)  |
                     |   builds canonical trie         |
                     |   snapshot every ~10 min (4.3)  |
                     +---------------+-----------------+
                                      |
                snapshot pushed to each region's blob
                store (~1.25GB, async; same-region is
                near-instant, cross-region adds latency)
                                      |
        +--------------+--------------+--------------+
        |              |              |              |
        v              v              v              v
  +-----------+  +-----------+  +-----------+  +-----------+
  | us-east    |  | us-west   |  | eu-west   |  | ap-south  |
  | replicas   |  | replicas  |  | replicas  |  | replicas  |
  | (~12)      |  | (~9)      |  | (~9)      |  | (~5)      |
  | local      |  | local     |  | local     |  | local     |
  | Trending   |  | Trending  |  | Trending  |  | Trending  |
  | Detector   |  | Detector  |  | Detector  |  | Detector  |
  +-----------+  +-----------+  +-----------+  +-----------+
        ^              ^              ^              ^
        |              |              |              |
   geo-routed     geo-routed     geo-routed     geo-routed
   user traffic   user traffic   user traffic   user traffic
```

**One global aggregator, but per-region trending detectors**: the 10-minute rebuild pipeline (§4.3) runs **once, globally** — query trends for the "head" of the distribution (the top 5M queries) are overwhelmingly global or at least cross-regional (a spike in "world cup" queries matters everywhere), so running N independent rebuild pipelines per region would mostly duplicate work and risk the regions' tries drifting apart in subtle ways. The **Trending Detector (§4.4)**, by contrast, runs **independently per region** — a regional event (a local election, a regional sports final, a local weather emergency) produces a query spike that's highly relevant to *that region's* users and largely irrelevant elsewhere; a global trending detector would either dilute the regional signal (the spike is a rounding error in global volume) or, worse, surface a hyper-local term to users worldwide who have no context for it.

**Replication lag as an additional staleness term**: §4.3's "snapshot reaches all replicas within the 10-minute cadence" assumption holds easily within a region (a 1.25GB transfer over a data-center-local network completes in ~1 second), but cross-region transfers over a transoceanic link (e.g., us-east to eu-west) can add tens of seconds for the same payload. This is a small addition relative to the 10-minute cadence (§8's "resulting staleness window" metric should be tracked **per region**, not just fleet-wide, precisely so this kind of regional skew is visible) — but it's the kind of detail that distinguishes "I understand the single-region design" from "I understand what changes when you go global" in an interview.

### 4.8 Query Normalization and Multi-Language Tokenization

§1 listed multi-language tokenization as out of scope for deep design, but leaving it completely unaddressed creates an obvious gap: the trie in §4.1 is keyed character-by-character, and "café" vs. "Cafe" vs. "CAFE" would, without normalization, occupy three separate trie paths with separately-tracked (and each individually under-counted) frequencies. A normalization step, applied identically at **both** build time (§4.3, when `insert` is called) and query time (when `getTopK` is called), collapses these into one path:

```java
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes raw input BEFORE it is inserted into (build time) or looked up
 * against (query time) the trie (4.1). Applying the SAME normalization on
 * both paths is what makes "Cafe" and "café" -- or "NETFLIX" and "netflix"
 * -- resolve to the same trie path and accumulate into the same frequency
 * counter, instead of splitting one query's popularity across variants.
 */
public class QueryNormalizer {

    // Matches combining diacritical marks left behind after NFKD
    // decomposition (e.g., the acute accent separated from "e" in "café").
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{Mn}+");

    /** "Café" -> "cafe", "NETFLIX" -> "netflix". */
    public String normalize(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFKD);
        return COMBINING_MARKS.matcher(decomposed).replaceAll("");
    }
}
```

**CJK languages break a different assumption**: Chinese, Japanese, and Korean text has no whitespace between words, and "one trie unit per character" (the implicit assumption in §4.1's `children.get(ch)`) produces a trie where a "prefix" of a few Hanzi characters matches an enormous and largely unrelated set of completions — the structure that makes English prefixes informative (a few letters narrow the space dramatically) doesn't hold the same way for character-based CJK prefixes. Production systems handle this with a **pluggable tokenizer**: the trie structure from §4.1 is unchanged, but the function that turns a normalized string into the sequence of keys used for `children.get(...)` traversal is locale-dependent:

```java
import java.util.List;

/**
 * Produces the sequence of trie-traversal keys for a normalized query.
 * Latin-script locales: one Unicode character per key (matches 4.1 as-is).
 * CJK locales: word-level keys from a dictionary-based segmenter, OR
 * transliterated syllables for pinyin-input users (see the Baidu example
 * in section 6).
 */
public interface LocaleTokenizer {
    List<String> tokenize(String normalizedQuery);
}

/** Default tokenizer: one Unicode code point per trie-traversal key. */
public class CharacterTokenizer implements LocaleTokenizer {
    @Override
    public List<String> tokenize(String normalizedQuery) {
        return normalizedQuery.codePoints()
                .mapToObj(cp -> new String(Character.toChars(cp)))
                .toList();
    }
}

/**
 * CJK tokenizer: delegates to an external dictionary-based segmenter (e.g.,
 * maximum-matching against a word-frequency dictionary) to split
 * whitespace-free text into word-level trie-traversal keys. The
 * segmentation algorithm itself is out of scope (1) -- the point is that
 * AutocompleteTrie (4.1) is untouched; only the build-time and query-time
 * tokenization step changes per locale.
 */
public class SegmentingTokenizer implements LocaleTokenizer {
    private final ExternalSegmenter segmenter;

    public SegmentingTokenizer(ExternalSegmenter segmenter) {
        this.segmenter = segmenter;
    }

    @Override
    public List<String> tokenize(String normalizedQuery) {
        return segmenter.segment(normalizedQuery);
    }

    public interface ExternalSegmenter {
        List<String> segment(String text);
    }
}
```

**How this combines with §4.7's regional topology**: a user's locale determines which `LocaleTokenizer` is applied at both build time and query time, and since different locales produce structurally different key sequences for the same underlying text, each major locale effectively needs its own trie namespace — either a fully separate trie per locale-group, or a shared trie keyed by `(locale, tokens)`. The ~1.25GB figure in §2 was implicitly single-locale (English); a multi-locale deployment multiplies that by the number of actively-supported locale groups, though in practice most non-English locales' corpora are smaller than English's, so the total rarely scales linearly with locale count.

---

## 5. Design Decisions & Tradeoffs

### Precomputed Top-K (Offline) vs. On-the-Fly Top-K (Online)

| | Precomputed top-K per node (chosen) | Compute top-K on the fly per query |
|---|---|---|
| **Online query cost** | O(prefix length) trie walk + O(K log K) sort of a cached list | O(subtree size under the prefix node) — could be huge for short/popular prefixes |
| **Latency at 500K QPS** | Comfortably <100ms p99 | Infeasible — even a few-millisecond subtree scan per request, multiplied by 500K/sec, would require enormous compute |
| **Freshness** | Only as fresh as the last rebuild (~10 min), mitigated by the trending overlay (§4.4) for spikes | Always perfectly fresh |
| **Implementation complexity** | Higher (offline pipeline, build-then-swap, trending overlay) | Lower (no offline pipeline needed) |

**Decision**: Precompute. The latency budget (<100ms at 500K QPS) makes on-the-fly computation a non-starter; the freshness gap is small (10 minutes) and explicitly patched by the trending layer for the one case (sudden spikes) where it would otherwise matter.

### In-Memory Trie (chosen) vs. Redis Sorted Sets per Prefix Bucket

| | In-memory trie, fully replicated (chosen) | Redis sorted sets (`ZADD`/`ZRANGEBYSCORE` per prefix key) |
|---|---|---|
| **Data structure** | Custom trie with precomputed top-K per node | One sorted set per prefix (e.g., key `prefix:"sea"`, members are completions, scores are frequencies); `ZREVRANGE prefix:"sea" 0 9` returns top-10 |
| **Replication model** | Full replica per node (works because it's only ~1.25GB, §2) | Redis itself can be sharded (Redis Cluster) — handles much larger corpora |
| **Operational simplicity** | Requires a custom build/serialize/swap pipeline (§4.3) | Uses off-the-shelf Redis; "rebuild" = re-running `ZADD` commands, which Redis handles natively with no custom serialization format |
| **Latency** | O(prefix length), in-process memory access — typically sub-millisecond | One Redis round-trip (~0.5-2ms over a local network) per `ZREVRANGE` |
| **When it wins** | Corpus small enough to replicate fully (this design's case) | Corpus too large to replicate; need Redis Cluster's native sharding; want to avoid building/maintaining a custom trie data structure and serialization format |

**Decision for this design**: in-memory trie, because the ~1.25GB corpus size makes full replication feasible and the trie's O(prefix length) lookup with zero network hops is strictly faster than even a sub-millisecond Redis round-trip at 500K QPS (which would require a very large Redis fleet just to handle the connection/request volume).

**However**, it's worth being explicit in an interview: **many real production systems choose the Redis-sorted-set approach precisely because it's operationally simpler** — no custom binary trie format, no custom build-then-swap pipeline, just `ZADD`/`ZINCRBY`/`ZREVRANGE` against a well-understood, battle-tested data store. The Redis approach becomes the *better* choice once the suggestion corpus grows past the point where full replication is practical (e.g., tens of GB+), because Redis Cluster's native sharding handles that scale without requiring a from-scratch sharding scheme for a custom trie.

### Periodic Full Rebuild + Trending Overlay (chosen) vs. Continuous Incremental Updates

| | Periodic rebuild + trending overlay (chosen) | Continuous incremental updates to the live trie |
|---|---|---|
| **Consistency** | Live trie is always a complete, consistent snapshot (build-then-swap, §4.3); trending overlay is a small additive layer | Risk of partial/inconsistent reads if a query arrives mid-update to a node whose top-K list is being mutated |
| **Complexity** | Two pipelines (10-min rebuild + 1-min trending), but each is conceptually simple and isolated | One pipeline, but requires careful concurrency control (locks or lock-free structures) on every trie node, on the hot read path |
| **Freshness** | 10-min for the bulk corpus, ~1-min for spikes | Could be near-instant for everything, in principle |
| **Failure isolation** | A bug in the trending detector can't corrupt the base trie (it's a separate, additive layer); a bug in the rebuild pipeline just means the *next* rebuild is skipped, serving slightly-staler-than-usual data | A bug in the incremental-update path can corrupt the live trie that's actively serving 500K QPS — much higher blast radius |

**Decision**: periodic rebuild + trending overlay, because it isolates the hot read path from any update-related concurrency concerns entirely (reads never contend with writes — there are no "writes" to the live trie at all), at the cost of a small (and explicitly compensated-for) freshness gap.

---

## 6. Real-World Implementations

- **Google Search autocomplete**: Operates at a scale far beyond this design's baseline (Google handles on the order of tens of billions of searches per day globally, with autocomplete requests at a correspondingly higher multiple). Google's autocomplete is heavily **personalized** — incorporating a signed-in user's search history, location, and even time of day — and applies a **safe-search / content-policy filter** to the suggestion list *before* it's returned, specifically to avoid surfacing offensive, hateful, or otherwise policy-violating completions even if they are technically "popular" by raw query volume. This filtering step is a mandatory post-processing stage on top of whatever ranking produces the raw top-K (directly relevant to §11's question on filtering).
- **Elasticsearch Completion Suggester**: A widely-used off-the-shelf alternative to a hand-rolled trie, built on a **Finite State Transducer (FST)** — a compressed, trie-like automaton that maps input strings (prefixes) to weighted outputs (suggestions) in a highly memory-efficient serialized form. An FST achieves much of what §4.1's `AutocompleteTrie` does (fast prefix-walk to a precomputed/weighted result set) but with significantly better memory density for large vocabularies, because shared suffixes across many entries are merged in the automaton rather than duplicated. The Completion Suggester is extremely popular for **e-commerce search bars** (product name/SKU autocomplete), where the corpus is the product catalog (often millions of SKUs with rich metadata) rather than a query log.
- **Amazon's search bar**: A canonical example of **business-driven ranking** layered on top of pure popularity. Amazon's autocomplete doesn't just surface the most-searched-for completions — it weights suggestions toward products that are **currently in stock** (suggesting an out-of-stock item's exact query just to show "Currently unavailable" on the results page is a poor experience and a lost-sale signal) and toward **higher-margin or sponsored items** (autocomplete suggestions are themselves a monetizable surface, similar to sponsored search results). This illustrates that the "top-K by frequency" model in §4.1 is the *starting point*, not the end state — production ranking functions blend frequency with business signals (inventory, margin, promotions, personalization) in a weighted scoring function evaluated at trie-build time (so the *precomputed* top-K already reflects these business weights, keeping the online path just as fast).

- **Baidu Suggest and pinyin transliteration**: Baidu's autocomplete must additionally handle **pinyin-to-Hanzi conversion** — many users type pinyin (Latin-alphabet phonetic spelling of Chinese) and expect suggestions in Chinese characters, so the suggestion index has to map pinyin-prefix sequences to Hanzi completions rather than matching the raw input alphabet directly. This is the production-scale instance of §4.8's `LocaleTokenizer` abstraction: the trie *structure* is unchanged, but the build-time and query-time token sequences are pinyin syllables rather than individual Hanzi or Latin characters, and a single underlying query often has multiple valid pinyin spellings that all need to map to the same trie path.

- **Mobile keyboard predictive text (Gboard, SwiftKey)**: A related but architecturally distinct problem worth contrasting in an interview. Next-word prediction on a mobile keyboard runs **entirely on-device**, using a small (megabytes, not gigabytes) on-device language model — a keyboard cannot tolerate even a 50ms network round trip per keystroke, and typed text is sensitive enough that keeping it off the network entirely is a deliberate privacy choice. This design's <100ms server-side budget (§1) is achievable specifically *because* search-query autocomplete is less latency-sensitive than per-character keyboard prediction, and because search queries are a bounded, shared corpus that benefits from cross-user aggregation (§4.3) the way a private, per-device language model never can — the trending layer (§4.4) has no on-device equivalent.

---

## 7. Technologies & Tools

| Component | Technology | Why |
|---|---|---|
| In-memory suggestion index | Custom in-memory trie (§4.1) | Full control over the precomputed top-K data structure; O(prefix length) reads with zero network hops |
| In-memory suggestion index (off-the-shelf alternative) | Elasticsearch Completion Suggester (FST-based, §6) | Mature, operationally supported, memory-efficient for very large vocabularies; good fit when not building a custom trie |
| Hot-prefix cache | Redis | Absorbs load spikes on the handful of extremely hot single/double-character prefixes (§4.2, §5); also a viable primary index via sorted sets (§5) for corpora too large to replicate |
| Query-log stream | Kafka | Durable, high-throughput, multi-consumer log — both the 10-minute aggregator and the 1-minute trending detector read from the same topic independently |
| Offline aggregation / trie build | Spark or Flink | Batch (Spark) or streaming-batch-hybrid (Flink) processing of the query-log stream into per-prefix frequency counts, every ~10 minutes |
| Trending detection | Flink (streaming) or a lightweight custom stream processor | ~1-minute sliding-window aggregation with baseline comparison; lower latency requirement than the full rebuild, so a leaner/faster pipeline than the Spark-based aggregator |
| Snapshot distribution | Blob store (e.g., S3-compatible) + pull, or push-based fan-out | Distributing a ~1.25GB trie snapshot to ~25+ replicas every 10 minutes (§4.3, §10) |

---

## 8. Operational Playbook

### Key Metrics to Monitor

| Metric | Why It Matters |
|---|---|
| **p99 suggestion latency** | The core SLA (<100ms) — directly tied to user-perceived responsiveness on every keystroke |
| **Redis hot-prefix cache hit rate** | A drop indicates either a cache issue or a sudden shift in traffic pattern (e.g., a new prefix becoming hot) |
| **Trie rebuild duration** | Must stay well under the 10-minute cadence — if rebuild duration approaches or exceeds 10 minutes, rebuilds start overlapping or falling behind, widening the staleness window |
| **Resulting staleness window** | The actual end-to-end time from "a query happens" to "it's reflected in the live trie" — should track close to the 10-minute target; cross-reference [observability](../observability/README.md) for how to instrument and alert on this as an SLO |
| **Trending-detector flag rate** | Sudden increases may indicate either a genuine news event (good) or an abuse pattern (bad — see runbook below) |
| **Snapshot distribution time** | How long it takes the new ~1.25GB snapshot to reach all replicas — if this grows, the swap window (and momentary double-memory usage) grows with it |

### Runbook: Trie Rebuild Is Running Long

**Symptom**: The aggregation job for the current 10-minute window has been running for, say, 18 minutes and hasn't produced a new snapshot yet.

**Action**:
1. **Do nothing to the live-serving trie.** Every replica continues serving the *previous* (now ~18-minute-old, instead of ~10-minute-old) snapshot. This is the entire point of the build-then-swap design (§4.3) — a slow rebuild degrades *freshness*, not *availability*. **Never** serve an empty or partially-built trie just because a rebuild is overdue.
2. Investigate the rebuild job: is it a data-volume spike (more query-log events than usual), a resource contention issue on the build host, or a code regression in the aggregation logic?
3. If the rebuild job is stuck/crashed entirely, the on-call engineer should be alerted (staleness window now growing unbounded) but the live trie keeps serving — this is a "fix it before it gets too stale" issue, not a "the site is down" issue.
4. Once root-caused, either let the current job finish, kill and restart it, or (if the build host itself is unhealthy) fail over to a standby build host.

### Runbook: Trending Detector Flags a False Positive

**Symptom**: The Trending Detector has flagged a query as "spiking" and it's now appearing prominently in autocomplete results, but it's actually being driven by a bot/scraper hammering the same query, not genuine user interest.

**Action**:
1. **Rate-limit query-log ingestion per client/IP/session *before* it reaches the trending pipeline** — this is the primary preventive control. A single client (or small IP range) generating an outsized share of the events for one query should have those events capped or discarded before they ever influence the 1-minute window's counts. (Cross-reference [rate limiting](../rate_limiting/README.md).)
2. If a false positive slips through anyway, the trending-overrides store entry can be manually cleared/expired by an on-call engineer — since it's a small, fast-access overlay structure (§4.4), this is a quick, low-risk operation that doesn't touch the base trie at all.
3. Post-incident, tune the trending detector's threshold (the "20x baseline" multiplier and minimum absolute-count floor from §4.4) if the false-positive pattern recurs — e.g., raise the absolute floor so that low-baseline queries (which are easiest for a small bot to spike) need a larger absolute count to trigger.

### Runbook: Snapshot Distribution Partial Failure

**Symptom**: A new trie snapshot (§4.3) builds successfully, but a subset of replicas — say 4 of 35, or an entire remote region in the §4.7 multi-region topology — fail to download or load it (network blip, disk-full, a corrupted partial download that fails deserialization).

**Action**:
1. **Affected replicas keep serving their previous snapshot.** The build-then-swap design (§4.3) means a failed download just means that replica never reaches the swap step — it continues on its current, now relatively staler, trie. This is a *freshness* problem for that replica, not a *correctness* problem.
2. The distribution job retries the affected replicas with backoff. If a replica fails across several consecutive rebuild cycles (e.g., 3 cycles, ~30 minutes of relative staleness), mark it unhealthy and pull it from the load-balancer rotation — running at slightly reduced capacity beats serving a replica whose trie has drifted materially from its peers.
3. Because every replica is interchangeable (§4.2 — any replica answers any prefix), removing a handful of stale replicas degrades capacity slightly but never correctness; remaining replicas absorb the load. Track the **per-replica** (not just fleet-wide) staleness-window metric from §8 specifically so a partial-distribution failure like this is visible before it reaches the 30-minute mark.

---

## 9. Common Pitfalls & War Stories

### War Story 1: Trie Rebuild Memory Spike (Broken -> Fixed)

**Broken**: An early implementation rebuilt the trie **in place**, on each live-serving replica — the rebuild process ran on the same host that was actively serving the *current* trie out of memory, and constructed the *new* trie alongside it before swapping references. During the multi-minute build window, each replica held **both the old and new tries simultaneously** — roughly doubling memory usage (from ~1.25GB to ~2.5GB) for the duration of every rebuild. Under normal conditions this was uncomfortable but survivable; during a period of elevated query-log volume (and therefore a larger-than-usual new trie, plus a slower build), several replicas crossed their memory limits mid-rebuild and were **OOM-killed** — taking those replicas out of the serving pool *during* a rebuild, which is exactly when capacity is most needed (the remaining replicas had to absorb the OOM-killed replicas' traffic while also running their own in-place rebuilds).

**Fixed**: Build the new trie snapshot on a **separate host/process**, entirely outside the serving fleet. Once the new snapshot is fully built and serialized, distribute it to each replica as a **file/blob** (not as a live in-memory object competing with the live trie for heap space). Each replica then loads the new snapshot (a brief, bounded memory bump while loading — not while *building*, which is far more memory- and CPU-intensive) and atomically swaps the pointer. **No serving host ever runs the expensive build process**, and the only "two tries in memory" window is the brief load-and-swap moment, not the entire multi-minute build duration.

### War Story 2: Stale Suggestions During Breaking News (Broken -> Fixed)

**Broken**: Before the trending-overlay layer (§4.4) existed, the **only** mechanism for new or surging queries to appear in autocomplete was the 10-minute full rebuild. When a major news event occurred, users immediately started typing related queries — but autocomplete continued showing the *previous* (pre-event) top suggestions for matching prefixes for up to **10 minutes**, because the event's queries hadn't yet been counted, aggregated, and baked into a new trie snapshot. For a feature whose entire value proposition is "feels instant and relevant," a 10-minute lag on exactly the queries users care about *most* in that moment was a glaring, frequently-noticed failure — user feedback specifically called out "autocomplete doesn't know about [the news event] yet" as a trust-eroding experience.

**Fixed**: Introduced the **Trending Detector** (§4.4) — a separate, much faster (~1-minute sliding window) pipeline reading the same query-log stream, which detects queries whose frequency has spiked sharply above their baseline and pushes them into a small "trending overrides" store. The Typeahead Service overlays (prepends) matching trending entries onto the trie's results **independent of the rebuild cadence**. This decoupled "is this query suddenly popular right now" (≈1-minute latency) from "what is this query's stable, long-term popularity ranking" (≈10-minute latency, via the full rebuild) — solving the user-facing problem without requiring the full rebuild to become faster (which would have its own costs, per §5's discussion of rebuild-vs-incremental tradeoffs).

### War Story 3: Cache Stampede on a Hot Prefix (Broken -> Fixed)

**Broken**: The Redis cache in front of the Typeahead Service (§3, §4.2) cached results for the handful of extremely hot single-character prefixes (e.g., "a", "the most-typed first letters") with a fixed TTL. When the cache entry for prefix `"a"` — which receives a disproportionately large share of all single-character-prefix traffic, since it's the first letter typed by every user whose query starts with "a" — **expired**, the next instant saw **thousands of concurrent requests** all miss the cache simultaneously (because they were all checking the same key, which had just expired at the same moment for everyone). All of them fell through to the Typeahead Service at once, creating a sharp, synchronized load spike that, while the trie lookup itself is cheap (§4.1), still represented thousands of redundant identical computations and a momentary multiplier on request-handling overhead (connection setup, deserialization, etc.) across the fleet.

**Fixed**: Two complementary changes:
1. **Jittered TTL**: instead of a fixed TTL (e.g., exactly 5 seconds for every hot-prefix cache entry), add random jitter (e.g., 5 seconds +/- 1 second, randomized per key). This spreads expirations out over time instead of having many keys (or, worse, the *same* key being repopulated with a deterministic TTL each time) expire in lockstep.
2. **Single-flight / lock pattern on cache repopulation**: when a cache miss occurs for a given prefix, only the *first* request that observes the miss actually queries the Typeahead Service and repopulates the cache; concurrent requests for the same prefix that arrive during this brief window wait (briefly) for that first request's result rather than all independently querying the backend. This is the standard cache-stampede-prevention pattern (cross-referenced in [database caching patterns](../../database/database_caching_patterns/README.md)).

### War Story 4: Uneven Load from Naive Prefix Sharding (Broken -> Fixed)

**Broken**: An early design — built before the team had worked through the scale estimation in §2 and realized the full corpus would fit in ~1.25GB — assumed the suggestion index would be too large to replicate, and sharded it by **first character** of the prefix: shard 1 served prefixes starting "a"-"i", shard 2 served "j"-"r", shard 3 served "s"-"z" (a deliberately simplified illustrative split). In production, this immediately produced a severe **hotspot on the "s" shard**: because so many common English words and queries begin with "s" ("search", "shoes", "south park", "spotify", "stock market", etc.), roughly **15% of all autocomplete traffic** landed on a single shard — far more than its "fair share" under any reasonable partitioning of the 26-letter alphabet across however many shards existed. No amount of adding more replicas *behind* the "s" shard's partition fully fixed this, because the imbalance was in the *partitioning scheme itself*, not in any individual shard's capacity — every other shard was comparatively under-loaded while "s" stayed hot regardless of fleet size.

**Fixed**: Once the scale estimation in §2 established that the **full corpus (~1.25GB) fits comfortably in memory on a single host**, the team abandoned prefix-based sharding entirely in favor of the **fully-replicated trie** approach (§4.2) — every replica holds the *entire* trie, so there is no shard key, no partition scheme, and therefore no possibility of a hot partition. This war story is, in effect, the justification for the architectural decision in §4.2: it's not just "replication is convenient because the data is small," it's "replication actively *avoids a class of problem* (hot shards from non-uniform key distributions) that sharding would otherwise introduce."

### War Story 5: Client Debounce Bypass Floods a Region (Broken -> Fixed)

**Broken**: The <100ms p99 budget (§1) and the 500K QPS peak estimate (§2) both assumed clients respect the ~100-150ms debounce described in §4.5 and §11. A third-party keyboard-app integration that called the autocomplete endpoint directly shipped a build with **no debounce at all** — it fired a request on every keystroke. For a 20-character query, that's roughly 20x the per-query request volume §2's "~20 requests per submitted search" figure assumed for that client.

**Impact**: The integration's user base was a small fraction of total traffic, but it was concentrated in one region (§4.7), pushing that region's request rate to roughly 3-4x its provisioned capacity (~9 replicas x ~20K QPS/replica, §10). Because the regional replicas shared a bounded connection-handling thread pool, the flood didn't just slow the integration's own requests — it exhausted available connections for *all* requests in that region, including ordinary users, pushing regional p99 latency from ~40ms to over 800ms for roughly 20 minutes before on-call traced the source to the single API key.

**Fixed**: Two changes, neither of which depends on client cooperation. First, **per-API-key rate limiting** (cross-reference [rate limiting](../rate_limiting/README.md)) at the edge layer caps any single client identity to a request rate consistent with debounced human typing (e.g., ~10 requests/sec/session); a client exceeding this gets a 429 rather than consuming shared capacity. Second, a **minimum-prefix-length floor** — refusing to serve suggestions for prefixes shorter than 2 characters, enforced server-side regardless of what the client sends — caps the worst-case per-query request multiplier from any client by roughly 10x on its own, since single-character prefixes are both the highest-volume and lowest-precision case (§4.2). The lesson generalizes well beyond this system: capacity plans (§10) that assume client-side behaviors like debounce need server-side enforcement of those assumptions, not just client-side cooperation.

---

## 10. Capacity Planning

Because the full ~1.25GB trie is replicated on **every** node (§2, §4.2), capacity planning for this system is fundamentally about **replica count for the read-QPS target**, not about sharding or partition counts — a notably different exercise from most other systems in this repo.

### Replica Count for Peak QPS

- Peak typeahead QPS: **~500,000/sec** (§2).
- Assume a single replica, given the cheap O(prefix length) trie-walk cost per request (§4.1), can sustain roughly **~20,000 requests/sec** before CPU/connection-handling overhead (not the trie lookup itself) becomes the bottleneck.
- Baseline replica count: 500,000 / 20,000 = **25 replicas** to exactly meet peak demand.
- Add headroom for (a) rolling deploys/rebuilds taking individual replicas briefly out of rotation, and (b) traffic spikes beyond the modeled peak (e.g., an unusually newsworthy day driving overall search volume up): a **+40% headroom** factor brings this to **~35 replicas**.

### Memory Footprint

- Each of the ~35 replicas holds a full ~1.25GB trie -> **~44GB total trie memory across the fleet** (1.25GB * 35) — trivial in absolute terms; the dominant memory cost per host is far more likely to be JVM heap overhead, connection buffers, and OS page cache than the trie itself.
- During a rebuild's load-and-swap window (§4.3, post-War-Story-1 fix), each replica briefly holds ~2.5GB (old + new snapshot) instead of ~1.25GB — a transient ~1.25GB bump per host, easily accommodated by provisioning hosts with, say, 8-16GB RAM (vastly more than the ~2.5GB peak need, with the remainder for OS/JVM/connection overhead).

### Redis Hot-Prefix Cache Sizing

- The Redis cache exists to absorb **load spikes**, not because the trie lookup is itself slow (§4.2, §5) — so it only needs to hold entries for the small number of prefixes that dominate traffic disproportionately (single-character and common double-character prefixes — on the order of tens to low hundreds of keys, e.g., 26 single-character + a few hundred common two-character combinations).
- Each cached entry is a serialized top-K list — at K=10 and ~20 bytes/entry plus overhead, call it ~500 bytes/entry. Even **1,000 hot-prefix entries** -> ~500KB. This fits trivially in a small Redis instance (or even a single shard of a larger Redis cluster used for other purposes) — **megabytes, not gigabytes**, is the right order of magnitude here.

### Rebuild Pipeline Sizing

- The aggregation pipeline (§4.3) processes ~100GB/day of raw query-log data (§2), but each individual rebuild only needs to process the **~10-minute window's worth** plus carry-forward state from the previous trie: 100GB/day / (24*6) ≈ **~700MB per 10-minute window** of new raw events.
- The build itself (§4.1's `insert` complexity analysis: ~250M heap operations for 5M queries at K=10) completes in well under a minute on a single modern core, and is trivially parallelizable (e.g., partition the 5M queries by first character across worker tasks, then merge — note this internal parallelization is *not* the same as sharding the *serving* trie; it's purely a build-time optimization).
- **Sizing target**: the rebuild (read window -> aggregate -> build -> serialize -> distribute to ~35 replicas) should comfortably complete within, say, **5 minutes** — leaving 2x headroom against the 10-minute cadence, so that transient slowdowns (a slightly larger-than-usual query-log window, a brief network hiccup during snapshot distribution) don't cause rebuilds to start overlapping or falling behind (§8's runbook for "rebuild running long").
- As query-log volume grows (e.g., the parent search engine grows from 1B to 10B searches/day), this 700MB/window figure grows proportionally to ~7GB/window — still comfortably within a single beefy build host's capacity, but worth re-benchmarking the build duration against the 10-minute cadence at that scale.

### Multi-Region Capacity

Building on §4.7's regional topology, the ~35-replica fleet is distributed across regions roughly proportional to each region's share of the 500K QPS peak:

| Region | Share of Peak Traffic | Replicas (of ~35) | Notes |
|---|---|---|---|
| us-east (aggregator region) | ~35% | ~12 | Receives new snapshots first (§4.3) — zero cross-region replication lag |
| us-west | ~25% | ~9 | ~10-20ms additional snapshot-distribution lag vs. us-east |
| eu-west | ~25% | ~9 | ~60-100ms transoceanic snapshot-distribution lag; local Trending Detector (§4.7) compensates for regional spikes despite the added base-trie staleness |
| ap-south | ~15% | ~5 | Smallest regional pool — monitor closely for headroom during regional traffic-pattern shifts (e.g., a regional holiday) |

**Cross-region snapshot distribution cost**: pushing a ~1.25GB snapshot to 3 remote regions every ~10 minutes adds roughly `3 * 1.25GB = 3.75GB` of inter-region egress per cycle, or **~540GB/day** — a real but modest line item next to the regional serving infrastructure itself, and the kind of "hidden cost of going global" detail that distinguishes a single-region answer from a multi-region one in an interview.

### Summary Table

| Resource | Sizing |
|---|---|
| Replica count (peak 500K QPS @ ~20K QPS/replica + 40% headroom) | ~35 replicas |
| Trie memory per replica (steady state) | ~1.25GB |
| Trie memory per replica (during swap window) | ~2.5GB transient |
| Total trie memory across fleet | ~44GB (steady state) |
| Redis hot-prefix cache size | Low hundreds of KB to a few MB |
| Rebuild pipeline input per 10-min window | ~700MB |
| Rebuild target completion time | <=5 min (2x headroom vs. 10-min cadence) |
| Cross-region snapshot egress (3 remote regions) | ~3.75GB/cycle (~540GB/day) |

---

## 11. Interview Discussion Points

**Q: Why precompute the top-K completions for each prefix instead of computing them on the fly at query time?**
A: At 500K QPS with a <100ms p99 budget, computing top-K on the fly would require traversing the subtree of all completions under a prefix node for *every single request* — for a short, popular prefix like "a", that subtree could contain hundreds of thousands of entries, making per-request computation completely infeasible at this scale. Precomputing during the offline aggregation pass (§4.3) means the online path is just an O(prefix-length) trie walk to a node that already has its answer cached — O(1)-ish regardless of how popular or broad the prefix is. The cost of this approach is that results are only as fresh as the last rebuild (~10 minutes), which is why the trending overlay (§4.4) exists specifically to patch the one case (sudden spikes) where that staleness would actually matter to users.

**Q: How do you keep autocomplete suggestions fresh for breaking news without waiting for the next full trie rebuild?**
A: A separate, faster pipeline — the Trending Detector (§4.4) — runs a ~1-minute sliding-window aggregation over the same query-log stream that feeds the 10-minute rebuild, comparing each query's current-window count against its historical baseline. Queries that spike sharply (e.g., 20x+ above baseline, with a minimum absolute-count floor to avoid noise) get pushed into a small "trending overrides" store that the Typeahead Service overlays onto the trie's normal results at request time. This is purely additive — it doesn't touch or wait for the base trie at all — so a breaking-news term can appear in autocomplete within about a minute of starting to spike, independent of where the 10-minute rebuild cycle currently is.

**Q: How do you handle the long tail of rare or never-before-seen prefixes that aren't in the trie at all?**
A: The trie only tracks the top 5M queries (§2) — by design, it doesn't cover every possible prefix, and `getTopK` returns an empty list for a prefix with no matching node (§4.1's code explicitly handles this). For these long-tail prefixes, the UI typically shows no suggestions (which is fine — there's no strong popular signal to surface anyway), or falls back to the underlying full search index for a "did you mean" / direct-search experience instead of a typeahead dropdown. The 5M-query cutoff is itself a capacity/relevance tradeoff: it's chosen to cover the overwhelming majority of real prefix traffic (the "head" and "torso" of the query distribution) while keeping the trie small enough to fully replicate (§2's ~1.25GB figure) — covering more of the long tail would grow the trie size and eventually force the sharding tradeoffs discussed in §4.2 and §5.

**Q: Trie vs. Redis sorted sets for the suggestion index — which would you pick, and when does the answer change?**
A: For this design, an in-memory trie wins because the ~1.25GB corpus fits entirely in RAM and can be fully replicated, giving O(prefix-length) lookups with zero network hops — strictly faster than even a sub-millisecond Redis round-trip at 500K QPS. However, Redis sorted sets (one `ZSET` per prefix, `ZREVRANGE` for top-K) are a very reasonable — and operationally simpler — choice in practice: no custom binary trie format, no custom build-then-swap pipeline, just standard `ZADD`/`ZINCRBY`/`ZREVRANGE` against a well-understood store. The answer flips toward Redis once the suggestion corpus grows too large to replicate fully (tens of GB+) — Redis Cluster's native sharding handles that scale without requiring you to invent a sharding scheme for a custom trie, whereas a trie that no longer fits on one host re-introduces the hot-shard problem from §4.2 and §9 War Story 4.

**Q: How do you avoid a cache stampede on an extremely hot prefix like a single popular letter?**
A: Two techniques together: jittered TTLs (randomizing each cache entry's expiration slightly so that many keys don't expire in lockstep, spreading out the resulting cache-miss load over time) and a single-flight/lock pattern (when a cache miss occurs, only the first request actually queries the backend and repopulates the cache; concurrent requests for the same prefix wait briefly for that result instead of each independently hitting the backend). This is the standard cache-stampede-prevention pattern and is exactly what fixed War Story 3 in §9 — without it, an expiring cache entry for a hot prefix causes thousands of simultaneous redundant backend hits at the moment of expiration.

**Q: How would you add personalization (per-user recent searches) without one user's history leaking into another user's results?**
A: Personalization is implemented as a small, separate per-user data structure (a list of the user's last 10-20 searches, §4.5) that's merged into the response *after* the shared trie lookup — it's never written into or mixed with the shared trie itself. The shared trie's precomputed top-K lists are aggregated, anonymous, population-level statistics (built from the offline aggregation pipeline, §4.3); per-user data lives in a completely separate store keyed by user/session ID and is only ever read in the context of that specific user's request. Because the personalization layer is read-only with respect to the shared trie and keyed strictly per-user, there's no code path by which one user's search history could appear in another user's merged results.

**Q: How do you filter offensive or inappropriate suggestions before they're shown to users?**
A: A content-policy filter runs as a post-processing step on the precomputed top-K lists during the offline aggregation pass (§4.3) — before a candidate query is allowed into a node's top-K cache, it's checked against a blocklist/classifier for offensive, hateful, or policy-violating content, similar to how Google's autocomplete applies a safe-search filter (§6). Doing this offline (rather than per-request) keeps the online path simple and fast — by the time a query reaches the live trie, it's already been vetted. The trending overlay (§4.4) needs the same filter applied to its much-faster pipeline too, since a sudden spike could in principle be driven by a coordinated campaign around an offensive term — the filter has to run on *both* update paths, not just the slow one.

**Q: Why is "fully replicated, not sharded" the right call for this system, and when would that stop being true?**
A: It's the right call because the scale estimation in §2 shows the entire precomputed-suggestion corpus (5M queries, ~250 bytes/node) fits in only ~1.25GB — small enough that every replica can hold a complete copy, eliminating both cross-shard fan-out on reads and the hot-shard problem that prefix-based sharding introduces (non-uniform first-letter distribution, §9 War Story 4). It would stop being true if the tracked-query count grew by roughly two orders of magnitude (e.g., from 5M to 500M, pushing the trie toward ~125GB) — at that point, full replication becomes impractical (both the per-host memory cost and the per-rebuild distribution cost to every replica become prohibitive), and the design would need to move to either a sharded trie (accepting the hot-shard mitigation challenge) or the Redis-sorted-set approach (§5), which can be sharded natively via Redis Cluster.

**Q: How do you achieve a zero-downtime trie rebuild?**
A: Build the new trie snapshot entirely on a separate host/process from the live-serving fleet (§4.3) — the serving replicas never run the expensive build computation themselves. Once the new snapshot is fully built and serialized, distribute it to each replica as a static blob; each replica loads it into memory and then **atomically swaps a pointer/reference** from the old trie to the new one, so any given request is served entirely by either the old or the new snapshot — never a mix. The brief "both snapshots loaded" window during load-and-swap is bounded and small compared to the multi-minute build duration, which is exactly the fix that resolved War Story 1's OOM-kill problem.

**Q: How would you add typo tolerance (e.g., users typing "neflix" and getting "netflix" suggestions)?**
A: Two complementary approaches layer on top of the existing trie: (1) at query time, in addition to walking the trie for the exact prefix typed, also generate a small set of "edit-distance-1" variants of the prefix (single character insertions/deletions/substitutions/transpositions) and look those up too, merging in any results found — this catches single-typo prefixes cheaply since the variant set for a short prefix is small; (2) offline, build an n-gram index (e.g., trigrams) alongside the trie, mapping character n-grams to the queries that contain them, so a prefix can be matched against queries that share enough n-grams even if the prefix itself doesn't appear verbatim — this is closer to how full-text search "did you mean" features work and is more powerful but more expensive, so it's typically reserved for the case where the exact-prefix trie walk returns zero or very few results (a fallback path, not the primary path).

**Q: Why does client-side debounce matter for both UX and backend load?**
A: For UX, firing a request on every single keystroke (especially for fast typists) means the UI is constantly receiving and re-rendering responses for prefixes the user has already typed past by the time the response arrives — this can cause visible flicker/jank in the suggestion dropdown as stale responses for shorter prefixes arrive after the request for a longer prefix. For backend load, debouncing (waiting ~100-150ms after the last keystroke before firing) directly reduces the request multiplier in §2's scale estimation — without it, the "~20 requests per 20-character query" figure could be much higher (potentially one request per character = 20, or more if a user backspaces and retypes), and at 500K QPS even a modest reduction in requests-per-query translates to a proportional reduction in required replica count (§10). Debounce is, in effect, a free win on both axes — better perceived UX *and* lower infrastructure cost — which is why it's listed as a functional requirement in §1 rather than just an optimization detail. **However, debounce is a client-side optimization, not a safety guarantee** — §9's War Story 5 shows what happens when a client ignores it, and why the server-side mitigations (rate limiting, minimum-prefix-length floor) can't assume it holds.

**Q: You said the corpus fits in ~1.25GB and should be fully replicated — but what if it didn't? Walk through what changes.**
A: Once the corpus is too large to replicate on every replica (§4.6 puts a rough ceiling at "low tens of GB"), the design shifts from "any replica answers any prefix" to a **sharded trie**, partitioned by `hash(full query string)` — critically *not* by prefix, since prefix-based partitioning reproduces the exact hot-shard failure from War Story 4 (§9) regardless of corpus size. The cost of hash-based sharding is that completions of one prefix now live on multiple shards, so a single request becomes a **scatter-gather**: broadcast to all N shards in parallel, each returns its local top-K, and a merge step combines them into the global top-K via a bounded min-heap (§4.6's `ShardedAutocompleteClient`). This trades a strictly simpler, single-hop, in-process design for one with fan-out latency (bounded via aggressive per-shard timeouts, e.g., 30ms) and a real shard-rebalancing operational surface — a trade worth making only when corpus growth genuinely forces it.

**Q: How does this design change for a global, multi-region user base?**
A: Deploy regional replica pools (§4.7) and geo-route users to the nearest one — a user in Singapore hitting a `us-east` replica pays 150-200ms of network round trip alone, which already exceeds the entire <100ms budget (§1) before any trie lookup happens. The 10-minute rebuild pipeline (§4.3) stays **global and singular** — query trends for the head of the distribution are mostly cross-regional, so N independent rebuild pipelines would mostly duplicate work and risk the regions' tries drifting apart. The **Trending Detector (§4.4)**, in contrast, runs **per region** — a regional event (a local election, a regional sports final) is highly relevant to that region's users and would be diluted to noise by a global trending pipeline. The main new cost is cross-region snapshot distribution latency and egress (§10's multi-region capacity table) — same-region distribution of the ~1.25GB snapshot completes in about a second, while a transoceanic hop can add tens of seconds, which is why per-replica (not just fleet-wide) staleness tracking matters once you go multi-region.

---

## Cross-References

- **Hot-prefix Redis cache, jittered TTLs, and the single-flight stampede-prevention pattern (§4.2, §5, §9 War Story 3)** -> [`../caching/README.md`](../caching/README.md), [`../../database/database_caching_patterns/README.md`](../../database/database_caching_patterns/README.md)
- **Why prefix-based sharding creates hot shards, and the alternative partitioning strategies (§4.2, §9 War Story 4)** -> [`../database_sharding/README.md`](../database_sharding/README.md)
- **In-memory data structures and Redis sorted sets as an alternative suggestion index (§5)** -> [`../../database/in_memory_databases/README.md`](../../database/in_memory_databases/README.md)
- **Elasticsearch Completion Suggester / FST and search-engine indexing internals (§6)** -> [`../../database/search_engines/README.md`](../../database/search_engines/README.md)
- **Rate-limiting query-log ingestion to prevent trending false positives (§8)** -> [`../rate_limiting/README.md`](../rate_limiting/README.md)
