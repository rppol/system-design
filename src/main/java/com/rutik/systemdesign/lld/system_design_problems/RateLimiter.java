package com.rutik.systemdesign.lld.system_design_problems;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

// =============================================================================
//  RATE LIMITER — Low-Level Design
//  Patterns used:
//    - Strategy : RateLimiter interface with 4 interchangeable algorithms
//    - Factory  : RateLimiterFactory (selects algorithm by RateLimiterType)
//
//  Four classic algorithms, each implementing the same single-method contract:
//    boolean allowRequest(String clientId)
//
//    1. FixedWindowCounterRateLimiter   — O(1) memory, allows 2x burst at edges
//    2. SlidingWindowLogRateLimiter     — O(N) memory, exact
//    3. SlidingWindowCounterRateLimiter — O(1) memory, weighted approximation
//    4. TokenBucketRateLimiter          — O(1) memory, controlled bursts
// =============================================================================

// ─────────────────────────────────────────────
//  STRATEGY PATTERN — common contract
// ─────────────────────────────────────────────

interface RateLimiter {
    /**
     * Returns true if the request from clientId should be allowed under the
     * current rate-limiting policy, false if it should be rejected.
     */
    boolean allowRequest(String clientId);
}

// ─────────────────────────────────────────────
//  CONFIG + ENUM
// ─────────────────────────────────────────────

enum RateLimiterType {
    FIXED_WINDOW,
    SLIDING_WINDOW_LOG,
    SLIDING_WINDOW_COUNTER,
    TOKEN_BUCKET
}

/**
 * Single config holder passed to the factory. Not every field is relevant to
 * every algorithm — each implementation reads only what it needs.
 *
 *   limit                  - max requests per window  (Fixed Window, Sliding Window Log/Counter)
 *   windowSizeMs           - size of the rolling/fixed window in milliseconds
 *   bucketCapacity         - max tokens a bucket can hold (Token Bucket)
 *   refillTokensPerSecond  - steady-state refill rate (Token Bucket)
 */
class RateLimiterConfig {
    private final int limit;
    private final long windowSizeMs;
    private final int bucketCapacity;
    private final double refillTokensPerSecond;

    private RateLimiterConfig(int limit, long windowSizeMs, int bucketCapacity, double refillTokensPerSecond) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
        this.bucketCapacity = bucketCapacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    /** Config for window-based algorithms (Fixed Window, Sliding Window Log/Counter). */
    public static RateLimiterConfig windowBased(int limit, long windowSizeMs) {
        return new RateLimiterConfig(limit, windowSizeMs, 0, 0.0);
    }

    /** Config for Token Bucket: capacity = max burst, refillTokensPerSecond = sustained rate. */
    public static RateLimiterConfig tokenBucket(int bucketCapacity, double refillTokensPerSecond) {
        return new RateLimiterConfig(0, 0L, bucketCapacity, refillTokensPerSecond);
    }

    public int getLimit()                       { return limit; }
    public long getWindowSizeMs()               { return windowSizeMs; }
    public int getBucketCapacity()              { return bucketCapacity; }
    public double getRefillTokensPerSecond()    { return refillTokensPerSecond; }
}

// ─────────────────────────────────────────────
//  1. FIXED WINDOW COUNTER
//  Memory: O(1) per client. Accuracy: weak at window edges (2x burst).
// ─────────────────────────────────────────────

class FixedWindowCounterRateLimiter implements RateLimiter {

    /** Per-client state: which window we're in, and how many requests so far. */
    private static class WindowCounter {
        long windowStartEpochSeconds;
        int count;

        WindowCounter(long windowStartEpochSeconds, int count) {
            this.windowStartEpochSeconds = windowStartEpochSeconds;
            this.count = count;
        }
    }

    private final int limit;
    private final long windowSizeMs;
    private final ConcurrentHashMap<String, WindowCounter> windows = new ConcurrentHashMap<>();

    public FixedWindowCounterRateLimiter(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
    }

    @Override
    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowSizeMs) * windowSizeMs;

        WindowCounter counter = windows.computeIfAbsent(clientId, id -> new WindowCounter(currentWindowStart, 0));

        synchronized (counter) {
            if (counter.windowStartEpochSeconds != currentWindowStart) {
                // New window has started — reset the counter.
                counter.windowStartEpochSeconds = currentWindowStart;
                counter.count = 0;
            }
            if (counter.count < limit) {
                counter.count++;
                return true;
            }
            return false;
        }
    }

    /** Exposes the current count for the demo's printed output. */
    public int getCurrentCount(String clientId) {
        WindowCounter counter = windows.get(clientId);
        return (counter == null) ? 0 : counter.count;
    }

    public int getLimit() { return limit; }
}

// ─────────────────────────────────────────────
//  2. SLIDING WINDOW LOG
//  Memory: O(limit) per client (one timestamp per request in-window).
//  Accuracy: exact — never over- or under-counts.
// ─────────────────────────────────────────────

class SlidingWindowLogRateLimiter implements RateLimiter {

    private final int limit;
    private final long windowSizeMs;
    private final ConcurrentHashMap<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
    }

    @Override
    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        Deque<Long> log = requestLogs.computeIfAbsent(clientId, id -> new ArrayDeque<>());

        synchronized (log) {
            // Evict all timestamps that have fallen outside the rolling window.
            long windowStart = now - windowSizeMs;
            Iterator<Long> it = log.iterator();
            while (it.hasNext()) {
                long ts = it.next();
                if (ts <= windowStart) {
                    it.remove();
                } else {
                    // Deque is ordered oldest-first; once we hit one inside the
                    // window, everything after it is also inside the window.
                    break;
                }
            }

            if (log.size() < limit) {
                log.addLast(now);
                return true;
            }
            return false;
        }
    }

    /** Exposes the current log size for the demo's printed output. */
    public int getCurrentLogSize(String clientId) {
        Deque<Long> log = requestLogs.get(clientId);
        return (log == null) ? 0 : log.size();
    }

    public int getLimit() { return limit; }
}

// ─────────────────────────────────────────────
//  3. SLIDING WINDOW COUNTER
//  Memory: O(1) per client (current window count + previous window count).
//  Accuracy: approximation via weighted overlap — the approach used by
//  Cloudflare and Kong in production.
// ─────────────────────────────────────────────

class SlidingWindowCounterRateLimiter implements RateLimiter {

    /** Per-client state: counts for the current and immediately preceding window. */
    private static class WindowCounter {
        long currentWindowStart;
        int currentCount;
        int previousCount;

        WindowCounter(long currentWindowStart) {
            this.currentWindowStart = currentWindowStart;
            this.currentCount = 0;
            this.previousCount = 0;
        }
    }

    private final int limit;
    private final long windowSizeMs;
    private final ConcurrentHashMap<String, WindowCounter> windows = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(int limit, long windowSizeMs) {
        this.limit = limit;
        this.windowSizeMs = windowSizeMs;
    }

    @Override
    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();
        long currentWindowStart = (now / windowSizeMs) * windowSizeMs;

        WindowCounter counter = windows.computeIfAbsent(clientId, id -> new WindowCounter(currentWindowStart));

        synchronized (counter) {
            if (currentWindowStart != counter.currentWindowStart) {
                if (currentWindowStart - counter.currentWindowStart == windowSizeMs) {
                    // Exactly one window advanced — current becomes previous.
                    counter.previousCount = counter.currentCount;
                } else {
                    // More than one window has elapsed — previous window is stale.
                    counter.previousCount = 0;
                }
                counter.currentCount = 0;
                counter.currentWindowStart = currentWindowStart;
            }

            // Fraction of the previous window that still "overlaps" the
            // sliding window ending at `now`.
            double elapsedIntoCurrentWindow = now - currentWindowStart;
            double overlapPercentage = 1.0 - (elapsedIntoCurrentWindow / windowSizeMs);

            double weightedCount = (counter.previousCount * overlapPercentage) + counter.currentCount;

            if (weightedCount < limit) {
                counter.currentCount++;
                return true;
            }
            return false;
        }
    }

    /** Exposes the weighted count for the demo's printed output. */
    public double getWeightedCount(String clientId) {
        WindowCounter counter = windows.get(clientId);
        if (counter == null) return 0.0;
        synchronized (counter) {
            long now = System.currentTimeMillis();
            double elapsedIntoCurrentWindow = now - counter.currentWindowStart;
            double overlapPercentage = 1.0 - (elapsedIntoCurrentWindow / windowSizeMs);
            return (counter.previousCount * overlapPercentage) + counter.currentCount;
        }
    }

    public int getLimit() { return limit; }
}

// ─────────────────────────────────────────────
//  4. TOKEN BUCKET
//  Memory: O(1) per client (tokens + last refill timestamp).
//  Accuracy: N/A (rate-based, not window-based). Best burst handling.
// ─────────────────────────────────────────────

class TokenBucketRateLimiter implements RateLimiter {

    /** Per-client state: how many tokens remain, and when we last refilled. */
    static class Bucket {
        double tokens;
        long lastRefillTimestampMs;

        Bucket(double tokens, long lastRefillTimestampMs) {
            this.tokens = tokens;
            this.lastRefillTimestampMs = lastRefillTimestampMs;
        }
    }

    private final int capacity;
    private final double refillTokensPerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    @Override
    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();

        // New clients start with a full bucket — they get the initial burst allowance.
        Bucket bucket = buckets.computeIfAbsent(clientId, id -> new Bucket(capacity, now));

        synchronized (bucket) {
            refill(bucket, now);

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /** Lazily refills tokens based on elapsed time since the last refill, capped at capacity. */
    private void refill(Bucket bucket, long now) {
        long elapsedMs = now - bucket.lastRefillTimestampMs;
        if (elapsedMs <= 0) return;

        double tokensToAdd = (elapsedMs / 1000.0) * refillTokensPerSecond;
        bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
        bucket.lastRefillTimestampMs = now;
    }

    /** Exposes the current token count for the demo's printed output. */
    public double getCurrentTokens(String clientId) {
        Bucket bucket = buckets.get(clientId);
        if (bucket == null) return capacity;
        synchronized (bucket) {
            refill(bucket, System.currentTimeMillis());
            return bucket.tokens;
        }
    }

    public int getCapacity() { return capacity; }
}

// ─────────────────────────────────────────────
//  FACTORY PATTERN — algorithm selection
//  Centralises the mapping from RateLimiterType to a concrete RateLimiter,
//  decoupling callers from the four implementation classes.
// ─────────────────────────────────────────────

class RateLimiterFactory {
    public static RateLimiter create(RateLimiterType type, RateLimiterConfig config) {
        return switch (type) {
            case FIXED_WINDOW          -> new FixedWindowCounterRateLimiter(config.getLimit(), config.getWindowSizeMs());
            case SLIDING_WINDOW_LOG     -> new SlidingWindowLogRateLimiter(config.getLimit(), config.getWindowSizeMs());
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(config.getLimit(), config.getWindowSizeMs());
            case TOKEN_BUCKET           -> new TokenBucketRateLimiter(config.getBucketCapacity(), config.getRefillTokensPerSecond());
        };
    }
}

// ─────────────────────────────────────────────
//  DEMO / MAIN
// ─────────────────────────────────────────────

class RateLimiterDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========================================");
        System.out.println("   Rate Limiter — LLD Demo");
        System.out.println("========================================\n");

        demoFixedWindowCounter();
        demoSlidingWindowLog();
        demoSlidingWindowCounter();
        demoTokenBucket();

        System.out.println("\n========================================");
        System.out.println("              Demo complete");
        System.out.println("========================================");
    }

    // ── 1. Fixed Window Counter: limit=5 requests per 10-second window ──────
    private static void demoFixedWindowCounter() {
        System.out.println("--- Fixed Window Counter (limit=5, window=10s) ---");

        RateLimiterConfig config = RateLimiterConfig.windowBased(5, 10_000L);
        FixedWindowCounterRateLimiter limiter =
                (FixedWindowCounterRateLimiter) RateLimiterFactory.create(RateLimiterType.FIXED_WINDOW, config);

        String client = "client-A";
        for (int i = 1; i <= 7; i++) {
            boolean allowed = limiter.allowRequest(client);
            System.out.printf("Request %d from %s -> %-7s (count=%d/%d)%n",
                    i, client, allowed ? "ALLOWED" : "DENIED",
                    limiter.getCurrentCount(client), limiter.getLimit());
        }
        System.out.println();
    }

    // ── 2. Sliding Window Log: limit=5 requests per 10-second rolling window ─
    private static void demoSlidingWindowLog() {
        System.out.println("--- Sliding Window Log (limit=5, window=10s) ---");

        RateLimiterConfig config = RateLimiterConfig.windowBased(5, 10_000L);
        SlidingWindowLogRateLimiter limiter =
                (SlidingWindowLogRateLimiter) RateLimiterFactory.create(RateLimiterType.SLIDING_WINDOW_LOG, config);

        String client = "client-B";
        for (int i = 1; i <= 7; i++) {
            boolean allowed = limiter.allowRequest(client);
            System.out.printf("Request %d from %s -> %-7s (log size=%d/%d)%n",
                    i, client, allowed ? "ALLOWED" : "DENIED",
                    limiter.getCurrentLogSize(client), limiter.getLimit());
        }
        System.out.println();
    }

    // ── 3. Sliding Window Counter: limit=5 requests per 10-second window ────
    private static void demoSlidingWindowCounter() {
        System.out.println("--- Sliding Window Counter (limit=5, window=10s) ---");

        RateLimiterConfig config = RateLimiterConfig.windowBased(5, 10_000L);
        SlidingWindowCounterRateLimiter limiter =
                (SlidingWindowCounterRateLimiter) RateLimiterFactory.create(RateLimiterType.SLIDING_WINDOW_COUNTER, config);

        String client = "client-C";
        for (int i = 1; i <= 7; i++) {
            boolean allowed = limiter.allowRequest(client);
            System.out.printf("Request %d from %s -> %-7s (weighted count=%.2f/%d)%n",
                    i, client, allowed ? "ALLOWED" : "DENIED",
                    limiter.getWeightedCount(client), limiter.getLimit());
        }
        System.out.println();
    }

    // ── 4. Token Bucket: capacity=5, refill=1 token/sec — demonstrates refill ─
    private static void demoTokenBucket() throws InterruptedException {
        System.out.println("--- Token Bucket (capacity=5, refill=1 token/sec) ---");

        RateLimiterConfig config = RateLimiterConfig.tokenBucket(5, 1.0);
        TokenBucketRateLimiter limiter =
                (TokenBucketRateLimiter) RateLimiterFactory.create(RateLimiterType.TOKEN_BUCKET, config);

        String client = "client-D";

        // Drain the bucket: first 5 allowed (full capacity), 6th denied (empty).
        for (int i = 1; i <= 6; i++) {
            boolean allowed = limiter.allowRequest(client);
            System.out.printf("Request %d from %s -> %-7s (tokens left=%.2f)%n",
                    i, client, allowed ? "ALLOWED" : "DENIED",
                    limiter.getCurrentTokens(client));
        }

        // Wait long enough to refill ~2 tokens at 1 token/sec, then try again.
        System.out.println("  ... waiting 2000ms for refill ...");
        Thread.sleep(2000L);

        boolean allowed7 = limiter.allowRequest(client);
        System.out.printf("Request 7 from %s -> %-7s (tokens left=%.2f after refill)%n",
                client, allowed7 ? "ALLOWED" : "DENIED", limiter.getCurrentTokens(client));

        boolean allowed8 = limiter.allowRequest(client);
        System.out.printf("Request 8 from %s -> %-7s (tokens left=%.2f)%n",
                client, allowed8 ? "ALLOWED" : "DENIED", limiter.getCurrentTokens(client));
    }
}
