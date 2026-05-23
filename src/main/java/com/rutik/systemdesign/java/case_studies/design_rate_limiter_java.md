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

---

## Failure Scenarios

| Component | Failure | Symptom | Recovery | Time-to-Recovery |
|-----------|---------|---------|----------|------------------|
| Redis (distributed limiter) | Down/unreachable | Limiter throws -> all requests rejected | Fail-open + circuit breaker | immediate |
| Lua script | Error/eval failure | Per-request exceptions | Fail-open + alert | immediate |
| App clock | Skew across servers | Inconsistent refill rates | Server-side Redis TIME | immediate |
| Hot key | Single key saturates one Redis shard | Latency spike on that key | Shard/partition the key space | minutes |

### Redis goes down

BROKEN — limiter failure cascades into a full outage:

```java
// BROKEN: any Redis error rejects the request (fail-closed) -> total outage
public boolean tryAcquire(String key) {
    Long allowed = redis.eval(LUA, List.of(key), args); // throws if Redis is down
    return allowed == 1L;                                // exception propagates -> 500/reject
}
// Redis blip -> 100% of traffic rejected even though backends are healthy.
```

FIX — fail-open behind a circuit breaker, and alert:

```java
// FIX: when the limiter is unavailable, ALLOW traffic (fail-open) and trip a breaker
public boolean tryAcquire(String key) {
    if (breaker.isOpen()) return true;          // limiter degraded -> allow, protect availability
    try {
        Long allowed = redis.eval(LUA, List.of(key), args);
        breaker.onSuccess();
        return allowed == 1L;
    } catch (Exception e) {
        breaker.onError();                       // repeated errors open the breaker
        alerts.fire("rate-limiter Redis unavailable, failing open", e);
        return true;                             // fail-open: availability > strict limiting
    }
}
```

Rationale and recovery: a rate limiter exists to protect backends from overload, but rejecting 100% of traffic because the limiter is down is usually worse than briefly under-limiting. Fail-open keeps the service available; the circuit breaker stops hammering a dead Redis and retries periodically (half-open) so the limiter reactivates within seconds of Redis returning. For limiters protecting a genuinely fragile backend (e.g., a paid third-party API with hard quotas), fail-closed may be correct instead — this is a deliberate per-limiter policy choice.

---

## Capacity Planning Math

### Sizing the bucket

```
Traffic:   1,000,000 API calls/day
Average:   1,000,000 / 86,400 s = ~11.6 req/sec
Peak:      ~100 req/sec (observed burst factor ~8.6x)

Token bucket:
   capacity      = 100   (absorb a 1-second burst at peak)
   refill_rate   = 11.6 tokens/sec (sustained average)
=> bursts up to 100 are allowed instantly; sustained rate is clamped to ~11.6/sec.
```

### Overhead of the distributed limiter

```
Redis Lua round trip: ~0.3 ms/call (one network RTT + script eval)
At peak 100 req/sec:   100 x 0.3 ms = 30 ms of limiter time per wall-clock second
   => 30 ms / 1000 ms = 3% overhead at peak. Negligible.

At 8M checks/sec (in-process AtomicLong), overhead is ~0 network and ~125 ns/check.
```

The distributed limiter's cost is dominated by network RTT, so co-locate Redis in the same AZ and pipeline where possible; the in-process limiter's cost is a handful of CPU cycles.

---

## Benchmark Comparisons — Limiter Implementation

JMH-style, single key, contended, Java 17:

| Implementation | Throughput | Latency | Scope | When to use |
|----------------|-----------|---------|-------|-------------|
| `AtomicLong` token bucket (in-process) | ~8M checks/sec | ~125 ns | Per-JVM | Single instance or per-instance sub-limit |
| `synchronized` token bucket (in-process) | ~2M checks/sec | ~500 ns | Per-JVM | Simpler code, lower contention needs |
| Redis Lua (network RTT ~0.5ms) | ~2k checks/sec per key | ~0.5 ms | Cluster-wide | Shared limit across many app instances |

The in-process `AtomicLong` bucket is ~4x faster than `synchronized` because CAS lets many threads progress concurrently instead of serializing through a monitor. But both are per-JVM only: with 20 instances, an in-process limit of 100/sec becomes an effective 2000/sec cluster-wide, which is usually not what you want. The Redis Lua limiter enforces a single shared limit atomically across the fleet at the cost of a network round trip (~0.5ms) and far lower per-key throughput. The production pattern is two-tier: a generous in-process limiter sheds obvious abuse cheaply, and Redis enforces the precise cluster-wide quota.

---

## Production War Stories

### War story 1 — Clock skew causing inconsistent refill rates

Symptom: one app server allowed roughly 2x the intended rate; limits "leaked" depending on which server handled the request.

BROKEN — refill is computed from each server's local wall clock, which drifts:

```java
// BROKEN: uses application System.currentTimeMillis() inside the Lua args / app logic
long now = System.currentTimeMillis();        // server B's clock is 400ms ahead of A
long elapsed = now - lastRefill;              // inflated on the fast-clock server
long refill = elapsed * refillRatePerMs;      // server B refills MORE tokens than earned
// Across a fleet with NTP skew, the limit becomes whatever the fastest clock thinks.
```

FIX — use Redis's server-side `TIME` as the single source of truth inside the Lua script:

```lua
-- FIX: refill math uses Redis TIME, not any app server's clock
local t = redis.call('TIME')                  -- {seconds, microseconds} from Redis itself
local now_ms = (t[1] * 1000) + (t[2] / 1000)
local last = tonumber(redis.call('HGET', KEYS[1], 'ts')) or now_ms
local elapsed = now_ms - last
local refill = elapsed * tonumber(ARGV[1])    -- refillRatePerMs
-- ... clamp to capacity, decrement on allow, HSET ts = now_ms ...
```

Because every app instance defers to one clock (Redis), refill is consistent regardless of NTP drift on the app servers. Lesson: in a distributed limiter, time must come from one authority; never trust per-node clocks for rate math.

### War story 2 — Rate limiting per IP behind a corporate NAT

Symptom: an enterprise customer reported the whole company getting 429s after one employee ran a script.

BROKEN — the limit key is the client IP, but thousands of users share one NAT egress IP:

```java
// BROKEN: per-IP key collapses an entire company onto one bucket
String key = "rl:" + request.getRemoteAddr();   // all of ACME shares 203.0.113.5
boolean ok = limiter.tryAcquire(key);            // one user's burst blocks everyone
```

FIX — key on authenticated identity, not network origin:

```java
// FIX: limit per API key / user id (falls back to IP only for anonymous traffic)
String principal = auth.apiKey() != null ? "rl:key:" + auth.apiKey()
                 : auth.userId() != null ? "rl:user:" + auth.userId()
                 : "rl:ip:" + request.getRemoteAddr();   // anonymous fallback only
boolean ok = limiter.tryAcquire(principal);
```

Lesson: IP is a poor identity — NAT, mobile carrier CGNAT, and shared proxies collapse many users onto one address, while a single user can rotate IPs. Rate limit on the strongest identity you have (API key > user id > IP).

---

## Evolution / Scalability at 10x Load

At 10M+ calls/day and stricter accuracy needs, the fixed/token-bucket approach evolves:

```
Accuracy vs cost spectrum:

  Fixed window  ->  Token bucket  ->  Sliding window  ->  Sliding window  ->  Adaptive
  (boundary       (current,          counter            log               (auto-tunes
   burst bug)      burst-friendly)    (good balance)     (most accurate,    limits to
                                                         most memory)       backend health)
  least memory ------------------------------------------------------> most memory/accuracy
```

1. Sliding window log — store a timestamp per request (Redis sorted set, score = time); count entries within the window and trim older ones. Most accurate (no boundary burst), but memory grows with request volume in the window.
2. Sliding window counter — weight the current and previous fixed windows by overlap; near sliding-log accuracy at fixed-window memory cost. The common production choice.
3. Adaptive rate limiting — auto-tune limits from backend health signals (latency, error rate, queue depth): tighten when the backend is stressed, relax when healthy. Turns the limiter into a feedback controller (additive-increase/multiplicative-decrease style), protecting the backend dynamically rather than at a static threshold.

Technical debt to track: a single hot key (one giant customer) can saturate a single Redis shard; partition hot keys (e.g., `key:{shard}`) or use local pre-aggregation. The fixed-window variant's boundary burst (2x limit at the window edge) is acceptable for coarse protection but should be replaced by a sliding window counter where precision matters (billing, quota enforcement).

---

## Additional Interview Questions

**Q: Should a rate limiter fail open or fail closed when Redis is down?**
Default to fail-open behind a circuit breaker: the limiter protects backends from overload, but rejecting 100% of traffic because the limiter died is usually a worse outage than briefly under-limiting. The breaker stops hammering dead Redis and recovers within seconds when it returns. Fail-closed is correct only when the protected resource has hard external quotas (a paid third-party API) where exceeding the limit is worse than dropping requests — a deliberate per-limiter policy.

**Q: In-process AtomicLong vs Redis limiter — when each?**
The in-process `AtomicLong` bucket does ~8M checks/sec with no network cost but only enforces a per-JVM limit, so across 20 instances the cluster limit multiplies by 20. The Redis Lua limiter enforces one atomic cluster-wide limit at ~0.5ms RTT and far lower per-key throughput. Use in-process for per-instance sub-limits or cheap abuse-shedding, Redis for precise shared quotas, and often both in a two-tier design.

**Q: Why must distributed-limiter time come from Redis, not the app servers?**
Each app server's wall clock drifts under NTP, so computing token refill from local time lets a fast-clock server grant more tokens than earned, and the effective limit becomes whatever the fastest clock believes. Computing refill inside the Lua script using Redis's `TIME` command gives every instance one authoritative clock, making refill consistent fleet-wide. Time in a distributed limiter must come from a single authority.

**Q: Why is rate limiting by IP a problem, and what's better?**
Many users share one egress IP behind corporate NAT or carrier CGNAT, so an IP-keyed limit can block an entire company when one user bursts, while a single user can also rotate IPs to evade it. Key on the strongest identity available — API key, then user id, falling back to IP only for anonymous traffic. Identity-based keys make the limit fair and harder to evade.

**Q: Compare fixed window, sliding window log, and sliding window counter.**
Fixed window is cheapest but allows up to 2x the limit at a window boundary (a burst at the end of one window plus the start of the next). Sliding window log stores every request timestamp for exact accuracy but uses memory proportional to in-window volume. Sliding window counter weights the current and previous windows by overlap, achieving near-log accuracy at fixed-window memory cost — usually the best production balance.
