# Case Study: Design a Rate Limiter in Pure Java

## Problem Statement

Design a thread-safe rate limiter that:
- Supports **token bucket** algorithm (allows short bursts)
- Configurable: N tokens per second, max burst B tokens
- `tryAcquire()` — non-blocking, returns `true` if allowed
- `acquire()` — blocking, waits until a token is available
- Works correctly under high concurrency (thousands of threads)
- Minimal latency overhead in the common (not-rate-limited) path

**Constraints**: Pure Java, no external libraries, production-ready.

---

## Key Java Concepts Used

| Concept | Module | Why Used |
|---------|--------|---------|
| `AtomicLong` | [Concurrency](../concurrency/README.md) | CAS-based token counter — lock-free |
| `ScheduledExecutorService` | [Concurrency](../concurrency/README.md) | Periodic token refill |
| `LockSupport.parkNanos()` | [Concurrency](../concurrency/README.md) | Precise sleeping in acquire() |
| `volatile` | [Concurrency](../concurrency/README.md) | `lastRefillTime` visibility |
| `System.nanoTime()` | [Performance & Tuning](../performance_and_tuning/README.md) | High-resolution monotonic time |
| CAS loop pattern | [Concurrency](../concurrency/README.md) | Lock-free token decrement |
| `ReentrantLock` + `Condition` | [Concurrency](../concurrency/README.md) | Blocking `acquire()` implementation |

---

## Architecture

```
TokenBucketRateLimiter
  |-- tokens: AtomicLong          (current token count × SCALE_FACTOR)
  |-- maxTokens: long             (bucket capacity × SCALE_FACTOR)
  |-- refillRateNanos: long       (nanos per token)
  |-- lastRefillNanos: AtomicLong (last refill timestamp)
  |-- lock: ReentrantLock         (for blocking acquire)
  |-- tokenAvailable: Condition   (signal on refill)

Token Bucket Semantics:
  Bucket holds up to maxTokens tokens
  Tokens arrive at rate R tokens/second
  tryAcquire(): consume 1 token if available; return false if empty
  acquire():    wait until a token is available
  Burst: bucket fills up to capacity, enabling short bursts > R
```

---

## Step-by-Step Design Decisions

### Decision 1: Lock-free vs lock-based token counter

**Lock-free** (`AtomicLong` + CAS): `tryAcquire()` on the common path is a single CAS — minimal overhead, no thread blocking, works correctly under high concurrency.

**Lock-based** (`synchronized` + `long`): simpler but every `tryAcquire()` acquires a lock — serializes all callers, contention bottleneck at high QPS.

**Choice**: `AtomicLong` + CAS for `tryAcquire()`. `ReentrantLock` + `Condition` only for the blocking `acquire()` path.

### Decision 2: Refill strategy — scheduled vs lazy

**Scheduled refill**: background thread calls `refill()` every `1/R` seconds. Simple, predictable, but adds background thread and jitter from scheduler granularity.

**Lazy refill**: compute how many tokens to add based on elapsed time on every `tryAcquire()` call. No background thread; refill is computed on-demand.

**Choice**: Lazy refill — simpler, no background thread, more accurate for sub-millisecond rates. One subtlety: must handle concurrent refill carefully with CAS.

### Decision 3: Fractional tokens

For very high rates (e.g., 1M tokens/second), tokens arrive faster than milliseconds. Use nanosecond precision with `System.nanoTime()`. For very low rates (e.g., 1 token/hour), the bucket must track partial tokens. Scale up by `SCALE_FACTOR = 1000` to support fractional tokens as long integers.

### Decision 4: Blocking `acquire()`

`tryAcquire()` returns false when no tokens. `acquire()` must sleep until tokens are available. Options:
- `Thread.sleep()` — millisecond precision, wakes too late
- `LockSupport.parkNanos()` — nanosecond precision
- `Condition.awaitNanos()` — accurate + can be signaled early when tokens arrive

**Choice**: `ReentrantLock` + `Condition.awaitNanos()`. The refill logic signals the condition when new tokens arrive.

---

## Core Implementation

```java
public class TokenBucketRateLimiter {
    private static final long SCALE = 1_000_000L; // scale factor for fractional tokens

    private final long maxTokens;
    private final long refillRatePerNano; // tokens * SCALE per nanosecond
    private final AtomicLong tokens;
    private final AtomicLong lastRefillNanos;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition tokenAvailable = lock.newCondition();

    /**
     * @param tokensPerSecond rate (e.g., 1000 = 1000 req/sec)
     * @param burstCapacity    max burst size (e.g., 500 tokens)
     */
    public TokenBucketRateLimiter(long tokensPerSecond, long burstCapacity) {
        this.maxTokens = burstCapacity * SCALE;
        this.refillRatePerNano = tokensPerSecond * SCALE / 1_000_000_000L;
        this.tokens = new AtomicLong(burstCapacity * SCALE); // start full
        this.lastRefillNanos = new AtomicLong(System.nanoTime());
    }

    /**
     * Non-blocking: returns true if token acquired, false if rate-limited.
     */
    public boolean tryAcquire() {
        refill();
        // CAS loop: decrement by SCALE if tokens >= SCALE
        while (true) {
            long current = tokens.get();
            if (current < SCALE) return false; // no tokens available
            if (tokens.compareAndSet(current, current - SCALE)) return true;
            // CAS failed (concurrent modification): retry
        }
    }

    /**
     * Blocking: waits until a token is available, then acquires it.
     */
    public void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            lock.lockInterruptibly();
            try {
                // Compute wait time until next token
                long waitNanos = waitTimeForNextToken();
                if (waitNanos > 0) {
                    tokenAvailable.awaitNanos(waitNanos);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Acquire with timeout. Returns false if timeout exceeded.
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            if (tryAcquire()) return true;
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) return false;

            lock.lockInterruptibly();
            try {
                long waitNanos = Math.min(waitTimeForNextToken(), remainingNanos);
                if (waitNanos > 0) {
                    tokenAvailable.awaitNanos(waitNanos);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    // Lazy refill: compute tokens earned since last refill, add them
    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        long elapsed = now - last;
        if (elapsed <= 0) return;

        long tokensToAdd = elapsed * refillRatePerNano;
        if (tokensToAdd <= 0) return; // not enough time elapsed for a token

        // CAS on lastRefillNanos to prevent concurrent over-refill
        if (lastRefillNanos.compareAndSet(last, now)) {
            // We won the refill race; add tokens (capped at maxTokens)
            long current = tokens.get();
            long newValue = Math.min(maxTokens, current + tokensToAdd);
            tokens.set(newValue);

            // Signal any waiting threads
            if (newValue >= SCALE) {
                lock.lock();
                try { tokenAvailable.signalAll(); }
                finally { lock.unlock(); }
            }
        }
        // If CAS failed: another thread is refilling; they'll add the tokens
    }

    private long waitTimeForNextToken() {
        long current = tokens.get();
        if (current >= SCALE) return 0; // token already available
        long deficit = SCALE - current; // scaled tokens needed
        // time = deficit / rate
        return deficit / Math.max(1L, refillRatePerNano);
    }

    // For monitoring
    public double getCurrentTokens() { return (double) tokens.get() / SCALE; }
    public double getMaxTokens() { return (double) maxTokens / SCALE; }
}
```

---

## Usage Examples

```java
// Rate limiter: 1000 requests/second, burst up to 500
TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1000, 500);

// Non-blocking usage (in request handler):
if (!limiter.tryAcquire()) {
    throw new RateLimitExceededException("Rate limit: 1000 req/s");
}
processRequest(request);

// Blocking usage (in background job):
try {
    limiter.acquire();  // waits for token
    callExternalApi();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}

// With timeout:
if (!limiter.tryAcquire(100, TimeUnit.MILLISECONDS)) {
    return Response.status(429).entity("Rate limited").build();
}
```

---

## Advanced: Per-Key Rate Limiting

```java
// Rate limit per user or per IP
public class MultiKeyRateLimiter {
    private final long tokensPerSecond;
    private final long burstCapacity;
    private final ConcurrentHashMap<String, TokenBucketRateLimiter> limiters =
        new ConcurrentHashMap<>();

    public MultiKeyRateLimiter(long tokensPerSecond, long burstCapacity) {
        this.tokensPerSecond = tokensPerSecond;
        this.burstCapacity = burstCapacity;
    }

    public boolean tryAcquire(String key) {
        // computeIfAbsent is atomic in ConcurrentHashMap
        TokenBucketRateLimiter limiter = limiters.computeIfAbsent(
            key, k -> new TokenBucketRateLimiter(tokensPerSecond, burstCapacity)
        );
        return limiter.tryAcquire();
    }

    // Periodic cleanup: remove limiters for inactive keys
    public void evictInactive(Duration inactivityThreshold) {
        // Implementation: track lastUsed timestamp per limiter
        // Remove entries not used for > threshold
    }
}
```

---

## Tradeoffs Considered

| Aspect | Choice | Alternative | Tradeoff |
|--------|--------|------------|---------|
| Refill | Lazy (on-demand) | Scheduled background thread | Lazy: no background thread, more accurate; Scheduled: simpler |
| Token counter | AtomicLong + CAS | synchronized long | CAS: lock-free fast path; sync: simpler but serializes |
| Blocking wait | Condition.awaitNanos | Thread.sleep | Condition: can be signaled early; sleep: always waits full duration |
| Token granularity | SCALE=1M (sub-millisecond) | Integer tokens | Finer granularity for high-frequency rates |
| Concurrent refill | CAS on lastRefillNanos | Lock on refill | CAS: one refill per interval, others skip; Lock: simpler |

---

## Interview Questions for This Case Study

**Q: Why use AtomicLong + CAS instead of synchronized for the token counter?**
The `tryAcquire()` path is on the hot path — called for every request. `synchronized` would serialize all callers through a single lock, creating a bottleneck under high concurrency. `AtomicLong.compareAndSet()` is implemented as a single hardware instruction (`LOCK CMPXCHG` on x86) — it's lock-free and allows many threads to make progress concurrently. The CAS loop handles contention by retrying, but retries are rare under typical loads.

**Q: What is the ABA problem here, and does it apply?**
The ABA problem: CAS succeeds when value returns to original after intermediate changes. In `tryAcquire()`, the CAS is `tokens.compareAndSet(current, current - SCALE)`. If `current = 100 SCALE`, it's decremented to 99 SCALE, then refilled back to 100 SCALE, and our stale CAS would succeed. In this case: it's *acceptable* — the effect is that we consume a token that was just refilled. The bucket semantics are preserved (we consume exactly 1 token, which exists). The ABA problem causes an issue only when you care about *which specific* value transition occurred; here we only care that "a token exists and we consumed it."

**Q: How does the lazy refill handle concurrent threads both trying to refill?**
The CAS on `lastRefillNanos.compareAndSet(last, now)` ensures exactly one thread performs the refill for any given interval. The winner performs the refill; the others skip it (their CAS fails). This is correct: the winner computes elapsed time since `last` and adds the correct number of tokens. Losers see the updated timestamp on their next call. The refill amount is deterministic from `(now - last)`.

**Q: How would you implement a sliding window rate limiter instead?**
A sliding window uses a `ConcurrentLinkedQueue<Long>` (or `ArrayDeque` with a lock) to store timestamps of each request. On `tryAcquire()`: remove timestamps older than the window (now - windowMs), check if remaining count < limit, add current timestamp if allowed. More memory-intensive (stores all timestamps in the window) but more precise than fixed window (no "double burst" at window boundary).

**Q: What is the `SCALE` factor and why is it needed?**
`AtomicLong` stores integers. At 1000 tokens/second, tokens arrive at 1 per millisecond. Without scaling, you lose sub-millisecond precision. By scaling all values by `SCALE = 1_000_000`, you represent fractional tokens as integers. For example: at 1 token/second, `refillRatePerNano = 1 * SCALE / 1e9 = 1` (1 scaled-unit per nanosecond), and tokens accumulate properly. Without scaling, at rates > 1 token/nanosecond or rates that don't divide evenly, you'd have rounding errors causing incorrect limiting.
