# Rate Limiting In Depth

<!-- study-paths
senior: README.md
files this module contributes to each curated path; omit a tier to leave it out
-->
## 1. Concept Overview

Rate limiting is the practice of controlling how many requests a client can make to a service within a given time window. Without rate limiting, a single misbehaving client — whether an automated bot, a buggy application, a denial-of-service attacker, or a legitimate customer that grew unexpectedly fast — can consume all available capacity and starve other clients. Rate limiting is also the primary mechanism for enforcing API pricing tiers: free users get 100 requests per minute, paid users get 10,000, enterprise users get 1,000,000.

Rate limiting is distinct from load shedding (dropping all traffic above a threshold regardless of source) and circuit breaking (stopping calls to a failing downstream service). Rate limiting controls how much traffic a specific identity is allowed to generate.

The five canonical algorithms are:

- **Token bucket** — burst-friendly, natural for API throttling
- **Leaky bucket** — smooths traffic to a constant output rate
- **Fixed window counter** — simplest, but vulnerable to boundary bursts
- **Sliding window log** — exact, but memory-intensive
- **Sliding window counter** — hybrid approximation, practical for high-scale systems

---

## 2. Intuition

One-line analogy: A highway on-ramp traffic light (metering signal) lets one car through every 3 seconds — regardless of how many cars are waiting — to prevent the freeway from becoming a parking lot.

Mental model: The token bucket is a bucket with a hole in it. Tokens (permits) accumulate in the bucket at a fixed rate. Each request consumes one token. When the bucket is full, new tokens are discarded (the burst has a hard cap at bucket capacity). When the bucket is empty, requests are rejected. The bucket allows bursts up to its capacity but enforces a long-run average rate equal to the token refill rate.

Why it matters: consider the shape of a failure that any unlimited API invites — a single customer's misconfigured client falls into an infinite retry loop and sends 2 million requests per minute at a third-party data provider. With no per-client limit, that one bug makes the provider's API unusable for every other customer for hours. With a per-client limit of 10,000 requests per minute and automatic throttling, the blast radius is exactly one client. The numbers here are illustrative; the asymmetry they show — one client's bug versus every client's outage — is the entire argument for rate limiting.

Key insight: Rate limiting that is only applied at the edge (API gateway) without client-side adaptive throttling is incomplete. Clients that receive 429 errors and immediately retry add more load rather than less. A complete rate limiting system includes server-side enforcement and client-side backoff.

---

## 3. Core Principles

**Enforce at the closest point to the source.** Apply rate limits at the API gateway or load balancer before requests reach application servers. Requests that hit application code have already consumed network bandwidth, load balancer capacity, and connection pool resources.

**Use different limits for different identities.** Rate limit by API key (most common for APIs), by IP address (for unauthenticated endpoints), by user ID (for authenticated endpoints), or by organization. A single user generating 1,000 requests per minute should not affect other users.

**Define tiered limits.** Free tier: 100 req/min. Standard tier: 1,000 req/min. Enterprise tier: 50,000 req/min. Rate limits are a product decision as much as an engineering one.

**Return informative headers.** Clients need to know their current limit, how many requests they have remaining, and when the limit resets. Without this information, clients implement aggressive polling to detect when the limit has lifted, making the problem worse.

**Design for distributed enforcement.** In a multi-instance deployment, a counter stored in a single instance's memory is inaccurate — other instances do not see it. Use a shared store (Redis) with atomic operations for accurate distributed rate limiting.

**Separate read and write limits.** Write operations (POST, PUT, DELETE) are more expensive and have side effects. Apply tighter rate limits to writes than to reads.

---

## 4. Types / Architectures / Strategies

### Token Bucket

A bucket holds up to `capacity` tokens. Tokens are added at `refillRate` tokens per second. Each request consumes one token (or more for expensive operations). If the bucket has tokens, the request is allowed and a token is consumed. If the bucket is empty, the request is rejected (or queued).

Allows burst traffic up to `capacity`. Long-run average throughput is capped at `refillRate`. This is the natural fit for APIs where occasional bursts are acceptable but sustained high rates must be limited.

### Leaky Bucket

Requests enter a queue (bucket) from the top. The queue drains at a fixed `outflowRate` requests per second. If the queue is full, new requests overflow (are rejected). The outflow is perfectly smooth — `outflowRate` requests per second, no more, no less.

Useful for smoothing bursty traffic before sending it to a downstream service that cannot handle bursts (e.g., a legacy system that processes messages at a fixed rate). The tradeoff is that it adds latency (requests queue before being processed) and discards bursts rather than serving them quickly.

### Fixed Window Counter

Divide time into fixed-size windows (e.g., 1-minute slots: 00:00–01:00, 01:00–02:00). Count requests per identity per window. Reset the counter at the window boundary.

Simple to implement and understand. However, vulnerable to the boundary burst problem: a client can send `limit` requests in the last second of one window and `limit` requests in the first second of the next window — effectively `2 * limit` requests in 2 seconds, which violates the intent of the limit.

### Sliding Window Log

Maintain a log (sorted set) of request timestamps for each identity. On each request, remove all timestamps older than `windowSize`, count remaining entries, and reject if count >= limit. Otherwise, add the current timestamp and allow.

Exact — there is no boundary burst problem. Memory-intensive: each request is stored as a timestamp. For a limit of 1,000 requests per minute with 1 million users, this is up to 1 billion timestamps in memory. Only practical for low-traffic services or when combined with aggressive TTLs.

### Sliding Window Counter (Hybrid)

Combine two adjacent fixed windows to approximate a sliding window without storing individual timestamps. The formula is:

```
current_count = previous_window_count * (1 - elapsed_fraction) + current_window_count
```

Where `elapsed_fraction` is how far into the current window we are (0.0 to 1.0). It needs only two counters per identity instead of per-request storage. Cloudflare, which runs this algorithm at its edge, published the only large-scale accuracy measurement: over 400 million requests, 0.003% were wrongly allowed or wrongly rate limited, with an average 6% gap between the real rate and the approximated one.

**What the formula is telling you.** "Keep whatever fraction of the previous window still
overlaps the last 60 seconds, and add everything in the current one."

It is a straight-line guess: assume the previous window's requests were spread evenly across
it, so the portion still inside the sliding window is proportional to how little of the new
window has elapsed. Two integers replace a per-request timestamp log.

| Symbol | What it is |
|--------|------------|
| `previous_window_count` | Total requests counted in the window that just closed |
| `current_window_count` | Requests so far in the window now open |
| `elapsed_fraction` | How far into the current window you are, `0.0` to `1.0` |
| `1 - elapsed_fraction` | The share of the previous window still inside the sliding view |
| `current_count` | The estimate compared against the limit |

**Walk one example.** Previous window 80, current window 30, limit 100, as the window advances:

```
   elapsed_fraction   prev x (1 - frac)   + current   =  estimate    vs limit 100
        0.00            80 x 1.00 = 80        30          110          REJECT
        0.25            80 x 0.75 = 60        30           90          allow
        0.50            80 x 0.50 = 40        30           70          allow
        0.75            80 x 0.25 = 20        30           50          allow
        1.00            80 x 0.00 =  0        30           30          allow

  The old window's weight bleeds off linearly instead of vanishing at a boundary.
  That single change is what removes the 2x boundary burst.
```

The approximation's error comes entirely from the even-spread assumption. If the previous
window's 80 requests all landed in its *first* second, the true overlap is zero but the
formula still charges you 60 at `frac = 0.25` — it over-counts, so it fails closed. That is
the right direction to be wrong in for a limiter.

### Adaptive Throttling (Client-Side)

Google's approach from SRE: clients track their own accept rate and self-throttle based on the server's observed rejection ratio.

```
throttle_probability = max(0, (requests - K * accepts) / (requests + 1))
```

Where K is typically 2. When the server starts rejecting requests, clients automatically reduce their send rate. This prevents retry amplification: clients that receive 429 errors do not immediately retry — they probabilistically skip sending new requests.

---

## 5. Architecture Diagrams

### Token Bucket

```mermaid
xychart-beta
    title "Token Bucket Fill Level (refill 10 tokens/sec, capacity 100)"
    x-axis ["t=0", "t=1s", "t=5s", "t=10s"]
    y-axis "Tokens available" 0 --> 100
    bar [10, 20, 60, 100]
```

*The bucket refills at 10 tokens/s toward a 100-token cap. At t=0 a burst has just drained it to 10 tokens; with no further requests it earns 10 more every second — 20 at t=1s, 60 at t=5s. By t=10s it has earned 100 more, but the capacity clamp discards the excess and holds it at 100: an idle client cannot bank more burst than `capacity`.*

### Fixed Window Boundary Burst

```mermaid
sequenceDiagram
    participant C as Client
    participant RL as Rate Limiter<br/>Fixed Window

    Note over C,RL: Limit = 100 requests per minute per window
    Note over RL: Window 1: 00:00-01:00
    C->>RL: 100 requests at 00:59
    RL-->>C: 100 allowed
    Note over RL: Window 2: 01:00-02:00
    C->>RL: 100 requests at 01:01
    RL-->>C: 100 allowed
    Note over C,RL: 200 requests in 2 seconds - double the intended 100/min limit
```

*A client sends 100 requests in the final second of Window 1 and 100 more in the first second of Window 2 — the hard counter reset at the boundary lets 200 requests through in 2 seconds, double the intended 100/min limit.*

**Read it like this.** "A fixed window promises `limit` per window, but what a client can
actually extract in one continuous stretch is `2 x limit`, because two full windows can be
adjacent to the boundary."

The limiter is not broken — it never let more than 100 through in either window. The bug is
that "window" and "any 60-second interval" are different things, and the client gets to choose
which interval to aim at.

| Symbol | What it is |
|--------|------------|
| `limit` | Requests permitted per window, `100` here |
| `windowSize` | Window length, `60` s |
| `2 x limit` | Worst-case requests in one continuous span across the boundary |
| intended rate | `limit / windowSize` — the rate you thought you configured |
| achieved rate | `2 x limit / burstSpan` — what a boundary-aware client gets |

**Walk one example.** Limit 100 per 60 s, a client that knows where the boundary is:

```
  intended sustained rate :  100 / 60 s        =   1.67 requests/second

  00:59.0   100 requests  ->  window 1 counter 0 -> 100, all allowed
  01:00.0   counter resets to 0
  01:01.0   100 requests  ->  window 2 counter 0 -> 100, all allowed

  achieved  :  200 requests in a 2-second span  =  100 requests/second

  100 / 1.67  =  60x the intended rate, and the limiter reports zero violations
```

The `2x` is the headline, but the burst-span ratio is the number that actually hurts: the
shorter the client makes the span around the boundary, the higher the instantaneous rate,
while the "2x per window" framing stays constant. That is why a downstream service sized for
1.67 rps still falls over.

### Sliding Window Counter Approximation

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    prev([Previous window<br/>count = 80]) --> weight["x 0.25<br/>1 - elapsed_fraction"]
    frac(["elapsed_fraction = 0.75<br/>75% through window"]) -.-> weight
    weight --> sum((" + "))
    curr([Current window<br/>count = 30]) --> sum
    sum --> est["estimated_count<br/>20 + 30 = 50"]
    est --> cmp{"50 under limit of 100?"}
    cmp -->|"yes"| allow(["Allow request"])
    cmp -->|"no"| reject(["Reject 429"])

    class prev,curr,frac io
    class weight,sum,est,cmp mathOp
    class allow train
    class reject lossN
```

*Worked example at 01:45 (75% through the 60-second window): the previous window's 80 requests are discounted by 25%, added to the current window's 30, yielding an estimated count of 50 — under the limit of 100, so the request is allowed.*

### Distributed Rate Limiting with Redis

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    ca([Client A]) --> s1["API Server #1"]
    cb([Client B]) --> s2["API Server #2"]
    cc([Client C]) --> s3["API Server #3"]
    s1 --> redis@{ icon: "logos:redis", form: "square", label: "Redis Cluster", pos: "b", h: 44 }
    s2 --> redis
    s3 --> redis

    class ca,cb,cc io
    class s1,s2,s3 req
```

*All three API servers share one counter in Redis; a Lua script executes `ZREMRANGEBYSCORE` + `ZCARD` + `ZADD` atomically — trim the window, count what is left, then admit — so no race condition is possible across instances.*

### Rate Limit Response Headers

```
HTTP/1.1 200 OK
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1698765432       (Unix timestamp when window resets)

HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1698765432
Retry-After: 23                     (seconds until requests are accepted again)
```

The `X-RateLimit-*` triple above is a de facto convention, not a standard — it came from
early versions of the IETF work and is what GitHub, X and most public APIs actually ship.
The standards-track effort is `draft-ietf-httpapi-ratelimit-headers`, still an
Internet-Draft (draft-11, May 2026) and **not yet an RFC**. It defines two unprefixed
Structured Fields (no `X-`), and the current syntax is not the old `1000;w=60` form:

```
RateLimit-Policy: "burst";q=100;w=60, "daily";q=1000;w=86400
RateLimit: "burst";r=50;t=30
```

`q` is the quota allocated, `w` the window in seconds, `r` the remaining quota and `t` the
seconds until it resets. Emit the `X-RateLimit-*` set for compatibility today; add the
standard fields alongside them rather than replacing them, since the draft can still change.

### Adaptive Throttling (Client-Side)

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    reqs([requests count]) --> calc["compute<br/>throttle_probability"]
    acc([accepts count]) --> calc
    k(["K = 2"]) --> calc
    calc --> roll{"random draw under<br/>throttle_probability?"}
    roll -->|"yes"| skip["Skip send<br/>self-throttle"]
    roll -->|"no"| send(["Send request"])

    class reqs,acc,k io
    class calc,roll mathOp
    class send train
    class skip lossN
```

*The client tracks its own requests and accepts; as the server's rejection rate rises, `throttle_probability` climbs and a per-request random draw decides whether to self-throttle — stopping the retry storm on the client side before the server is overwhelmed. The probability only leaves zero once `requests > K * accepts`, so K=2 means the client tolerates the backend rejecting up to half its requests before it begins throttling itself.*

---

## 6. How It Works — Detailed Mechanics

### Token Bucket Implementation (Java)

```java
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TokenBucket {
    private final long capacity;
    private final double refillRatePerNano;  // tokens per nanosecond
    private AtomicLong tokens;
    private volatile long lastRefillTime;
    private final ReentrantLock lock = new ReentrantLock();

    public TokenBucket(long capacity, long refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerNano = (double) refillRatePerSecond / 1_000_000_000L;
        this.tokens = new AtomicLong(capacity);
        this.lastRefillTime = System.nanoTime();
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(int tokensRequested) {
        lock.lock();
        try {
            refill();
            long current = tokens.get();
            if (current >= tokensRequested) {
                tokens.set(current - tokensRequested);
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTime;
        long newTokens = (long) (elapsed * refillRatePerNano);
        if (newTokens > 0) {
            tokens.set(Math.min(capacity, tokens.get() + newTokens));
            lastRefillTime = now;
        }
    }
}
```

**In plain terms.** "You may spend what has piled up in the bucket all at once, but you can
only ever earn it back at the refill rate."

Two numbers, two independent guarantees: `capacity` is the biggest burst you tolerate, and
`refillRate` is the sustained rate you are actually willing to serve. Conflating them is the
usual configuration mistake.

| Symbol | What it is |
|--------|------------|
| `capacity` | Maximum tokens the bucket holds. Equals the largest single burst allowed |
| `refillRatePerSecond` | Tokens added per second. The long-run throughput ceiling |
| `elapsed x refillRatePerNano` | Tokens earned since the last refill — computed lazily, not by a timer |
| `Math.min(capacity, ...)` | The cap. Tokens earned above `capacity` are thrown away |
| `tokensRequested` | Cost of this call. Expensive endpoints can charge more than 1 |

**Walk one example.** `capacity = 100`, `refillRate = 10/s`, a client that opens at full tilt:

```
  t = 0.0 s   bucket full at 100.  Client sends a 100-request burst.
              100 allowed instantly.  bucket -> 0

  t = 0.0 s   client keeps pushing at 60 rps, bucket empty
              earns  10 tokens/s , spends 60 tokens/s
              allowed rate settles at exactly 10 rps ; 50 rps rejected

  refill from empty back to full :  100 tokens / 10 per s  =  10.0 s
              so a second 100-burst is only available 10 s after the first
```

Note what `refill()` does *not* do: there is no background thread. Tokens are computed from
elapsed time on the next request, which is why an idle client costs nothing and why the
`Math.min(capacity, ...)` clamp exists — an hour of idleness would otherwise mint 36,000
tokens and let one client replay a whole hour of quota in a single second.

### Redis Lua Script — Sliding Window Log (Exact)

```lua
-- Sliding window log using Redis sorted set
-- KEYS[1]: rate limit key (e.g., "ratelimit:user:123")
-- ARGV[1]: current timestamp in milliseconds
-- ARGV[2]: window size in milliseconds  (e.g., 60000 for 60 seconds)
-- ARGV[3]: max allowed requests in window
-- ARGV[4]: unique member for THIS request (e.g. a client-generated UUID).
--          The sorted set is keyed by member, so two requests landing in the
--          same millisecond with the same member overwrite each other and the
--          window silently under-counts. Do NOT mint the member from a second
--          Redis key such as INCR key..':seq': that key is not in KEYS, so it
--          breaks on Redis Cluster (different hash slot) and, because nothing
--          ever sets a TTL on it, it leaks one immortal key per identity.
-- Returns: 1 = allowed, 0 = rejected, also returns remaining count

local key     = KEYS[1]
local now     = tonumber(ARGV[1])
local window  = tonumber(ARGV[2])
local limit   = tonumber(ARGV[3])
local member  = ARGV[4]

-- Remove all timestamps that are outside the current window
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Count how many requests are in the current window
local count = redis.call('ZCARD', key)

if count < limit then
    -- Score = timestamp (drives the window trim); member = the caller's unique id
    redis.call('ZADD', key, now, member)
    -- Set expiry to avoid orphaned keys. Refreshed on every admitted request,
    -- so an idle identity's key disappears one window after its last request.
    redis.call('PEXPIRE', key, window)
    return {1, limit - count - 1}  -- allowed, remaining after this request
else
    return {0, 0}  -- rejected, 0 remaining
end
```

### Redis Lua Script — Sliding Window Counter (Approximate, Low Memory)

```lua
-- Sliding window counter: uses two fixed-window counters + interpolation
-- KEYS[1]: current window key  (e.g., "ratelimit:user:123:1698765420")
-- KEYS[2]: previous window key (e.g., "ratelimit:user:123:1698765360")
-- ARGV[1]: current window start timestamp (seconds)
-- ARGV[2]: window size in seconds
-- ARGV[3]: limit
-- ARGV[4]: current unix timestamp in seconds -- pass redis.call('TIME')[1],
--          never the caller's clock (see Pitfall 4 on clock skew)

local curr_key    = KEYS[1]
local prev_key    = KEYS[2]
local window_start = tonumber(ARGV[1])
local window_size  = tonumber(ARGV[2])
local limit        = tonumber(ARGV[3])
local now          = tonumber(ARGV[4])  -- current unix timestamp in seconds

-- Fraction of current window that has elapsed
local elapsed_fraction = (now - window_start) / window_size

-- Counts in each window (default 0 if key doesn't exist)
local curr_count = tonumber(redis.call('GET', curr_key) or 0)
local prev_count = tonumber(redis.call('GET', prev_key) or 0)

-- Weighted estimate: previous window contributes less as we advance into current window
local estimated = math.floor(prev_count * (1 - elapsed_fraction) + curr_count)

if estimated < limit then
    -- Increment current window counter
    redis.call('INCR', curr_key)
    redis.call('EXPIRE', curr_key, window_size * 2)
    return {1, limit - estimated - 1}
else
    return {0, 0}
end
```

### Spring Boot Rate Limiting Filter with Redis

```java
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_SCRIPT =
        "local key = KEYS[1]\n" +
        "local now = tonumber(ARGV[1])\n" +
        "local window = tonumber(ARGV[2])\n" +
        "local limit = tonumber(ARGV[3])\n" +
        // ARGV[4] is a caller-generated unique member. Do not use math.random()
        // for it: before Redis 7.0 the Lua PRNG is re-seeded identically on every
        // script execution unless effects replication is active, so two requests
        // in the same millisecond can produce the same member, overwrite each
        // other in the sorted set, and undercount the window.
        "local member = ARGV[4]\n" +
        "redis.call('ZREMRANGEBYSCORE', key, 0, now - window)\n" +
        "local count = redis.call('ZCARD', key)\n" +
        "if count < limit then\n" +
        "  redis.call('ZADD', key, now, member)\n" +
        "  redis.call('PEXPIRE', key, window)\n" +
        "  return {1, limit - count - 1}\n" +
        "else return {0, 0} end";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> script;
    private final RateLimitProperties properties;

    public RateLimitingFilter(RedisTemplate<String, String> redisTemplate,
                              DefaultRedisScript<List> script,
                              RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.script = script;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {

        String clientKey = resolveClientKey(request);
        RateLimitTier tier = properties.getTierFor(clientKey);

        long now = System.currentTimeMillis();
        long windowMs = tier.getWindowSeconds() * 1000L;
        String redisKey = "ratelimit:" + clientKey;

        List<Long> result = redisTemplate.execute(script,
            List.of(redisKey),
            String.valueOf(now),
            String.valueOf(windowMs),
            String.valueOf(tier.getLimit()),
            UUID.randomUUID().toString());

        boolean allowed = result.get(0) == 1L;
        long remaining = result.get(1);
        long resetAt = (now / windowMs + 1) * windowMs / 1000;

        response.setHeader("X-RateLimit-Limit", String.valueOf(tier.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetAt));

        if (!allowed) {
            long retryAfterSeconds = (resetAt - now / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"," +
                "\"retry_after\":" + retryAfterSeconds + "}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        // Priority: API key header > authenticated user ID > IP address
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null) return "apikey:" + apiKey;

        String userId = SecurityContextHolder.getContext()
            .getAuthentication() != null ?
            SecurityContextHolder.getContext().getAuthentication().getName() : null;
        if (userId != null) return "user:" + userId;

        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
```

### Nginx Rate Limiting Configuration

```nginx
# Define rate limit zones
# Zone "api_limit": keyed by $http_x_api_key, 10MB state space, 100 req/s limit
# nginx state is 128 bytes per key on 64-bit platforms (64 bytes on 32-bit), so
# 10MB holds ~80,000 concurrent keys on 64-bit -- NOT the ~160,000 you get if you
# assume the 32-bit figure. Size the zone from the 64-bit number or nginx will
# start evicting the oldest states (and silently under-limit) once it fills.
http {
    limit_req_zone $http_x_api_key zone=api_limit:10m rate=100r/s;
    limit_req_zone $binary_remote_addr zone=ip_limit:10m rate=10r/s;

    # limit_req is only valid in http / server / location context -- NOT inside
    # an `if` block, where nginx refuses to start with "limit_req directive is
    # not allowed here". Select write methods with a map instead: nginx does not
    # account a request whose limit_req_zone key evaluates to the empty string,
    # so reads pass through the write zone without consuming any budget.
    map $request_method $write_key {
        default   "";
        POST      $http_x_api_key;
        PUT       $http_x_api_key;
        PATCH     $http_x_api_key;
        DELETE    $http_x_api_key;
    }
    limit_req_zone $write_key zone=write_limit:10m rate=10r/s;

    # Return 429 (not 503) for rate limit rejections
    limit_req_status 429;

    server {
        listen 443 ssl;

        # Apply read rate limit with burst buffer
        # burst=200: allow up to 200 excess requests to queue
        # nodelay: serve burst requests immediately (don't delay them)
        #          without nodelay, burst requests are served at 1/rate intervals
        location /api/ {
            limit_req zone=api_limit burst=200 nodelay;
            limit_req zone=ip_limit burst=20 nodelay;
            proxy_pass http://backend;
        }

        # Tighter limits for write operations -- the $write_key map above makes
        # this a no-op for GET/HEAD, so no `if` is needed (or permitted) here.
        location ~* ^/api/(users|orders|payments) {
            limit_req zone=write_limit burst=10 nodelay;
            proxy_pass http://backend;
        }

        # $limit_req_status (nginx 1.17.6+) is PASSED / DELAYED / REJECTED --
        # it is a diagnostic, NOT the configured limit. Expose it as its own
        # header (or log it); nginx has no built-in variable for the numeric
        # limit or the remaining count, so emit those from the app or OpenResty.
        add_header X-RateLimit-Decision $limit_req_status always;
    }
}
```

### Adaptive Throttling (Client-Side)

```java
public class AdaptiveThrottler {
    // Google SRE adaptive throttling
    // throttle_probability = max(0, (requests - K * accepts) / (requests + 1))
    // The probability only leaves zero once requests > K * accepts, i.e. once
    // the accept ratio drops below 1/K. So:
    //   K = 1 -> throttles as soon as ANY request is rejected (accept < 100%)
    //   K = 2 -> throttles once the backend rejects more than half (accept < 50%)
    // K = 2 is what the SRE book recommends. Lower K throttles more aggressively
    // and wastes fewer backend resources, but propagates backend state to the
    // client more slowly.

    private static final double K = 2.0;
    private static final int WINDOW_SECONDS = 120;
    private static final long MIN_SAMPLES = 10;   // warm-up before throttling

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong acceptedRequests = new AtomicLong(0);
    private final ScheduledExecutorService scheduler;

    public AdaptiveThrottler() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        // Reset counters every 2 minutes to use a sliding approximation
        scheduler.scheduleAtFixedRate(this::decay, WINDOW_SECONDS, WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    public boolean shouldSendRequest() {
        // The SRE definition of "requests" is every attempt made by the
        // application layer ON TOP of the throttler -- including the ones this
        // method rejects locally. Counting only the requests actually sent (the
        // common mistake) makes the ratio self-correcting in the wrong
        // direction: locally dropped attempts stop pushing the probability up,
        // so the client under-throttles exactly when the backend is worst off.
        long requests = totalRequests.incrementAndGet();
        long accepts  = acceptedRequests.get();

        if (requests <= MIN_SAMPLES) return true;  // not enough history yet

        double throttleProbability = Math.max(0.0,
            (requests - K * accepts) / (requests + 1.0));

        return ThreadLocalRandom.current().nextDouble() >= throttleProbability;
    }

    public void recordResult(boolean accepted) {
        if (accepted) {
            acceptedRequests.incrementAndGet();
        }
    }

    private void decay() {
        // Exponential decay: halve counters every window
        totalRequests.updateAndGet(v -> v / 2);
        acceptedRequests.updateAndGet(v -> v / 2);
    }
}
```

---

## 7. Real-World Examples

### GitHub API: Multi-Tier Rate Limiting

GitHub's REST API uses multiple simultaneous rate limits. Unauthenticated requests: 60 requests per hour per IP. Authenticated requests: 5,000 requests per hour per user (15,000 for users acting on behalf of a GitHub Enterprise Cloud organization). GitHub App installations: 5,000 per hour baseline, scaling by 50 requests/hour for each repository beyond 20 and each user beyond 20, capped at 12,500 per hour. Search API: 30 requests per minute authenticated, 10 unauthenticated (separate pool; code search is 10/minute even authenticated). GraphQL API: based on query complexity points (not request count). GitHub also uses secondary rate limits that fire on too many concurrent requests (max 100), more than 900 points per minute against REST endpoints, or more than 90 seconds of CPU time per 60 seconds of real time, even if the hourly limit has not been reached. Primary limits are returned via `x-ratelimit-*` headers on every response.

### Stripe API: Layered Global, Per-Endpoint and Concurrency Limits

Stripe's published account-wide limit is 100 requests per second in live mode and 25 requests per second in sandbox. On top of that, individual endpoints default to 25 requests per second, with documented exceptions (Files: 20 reads and 20 writes/sec; Payouts: 15 creates/sec and 30 concurrent requests per business; Search: 20 reads/sec). A throttled request returns `429 Too Many Requests` with a `Stripe-Rate-Limited-Reason` header naming which limiter fired — `global-rate`, `endpoint-rate`, `global-concurrency`, `endpoint-concurrency` or `resource-specific` — which is what makes the 429 actionable instead of merely discouraging. Stripe's engineering post "Scaling your API with rate limiters" describes the mechanism: a Redis-backed token bucket per user, plus a concurrent-request limiter and two load shedders that reserve fleet capacity for critical traffic. Separately, Stripe caches idempotency-key results for at least 24 hours and replays the original response on retry, so a retry storm during error recovery re-reads a cached result rather than re-executing the charge; note that Stripe does not publish any rate-limit *exemption* for idempotent retries, so budget for them as ordinary requests.

### X (Twitter) API: Per-Endpoint Limits Split by Auth Type

The X API rate limits per endpoint, and the same endpoint carries two separate budgets depending on how you authenticate: a per-user limit (OAuth user token) and a per-app limit (app-only bearer token). Most endpoints use a 15-minute window, but the window is a per-endpoint property, not a global one — the docs annotate the exceptions explicitly (`/24hrs` for media upload at 50,000, `/sec` for some streaming endpoints). Every response carries `x-rate-limit-limit`, `x-rate-limit-remaining` and `x-rate-limit-reset`; exceeding a limit returns HTTP `429` with error code 88, "Rate limit exceeded". Note that X separates rate limiting from billing — since the February 2026 move to pay-per-usage as the default model, staying inside a rate limit does not mean staying inside a budget, and the two must be monitored independently.

### Slack Web API: Method Tiers Scoped Per Workspace

Slack's published limits are the clearest example of choosing the *unit of isolation* deliberately. Every Web API method is assigned a tier — Tier 1 (1+ per minute), Tier 2 (20+), Tier 3 (50+), Tier 4 (100+), plus per-method "Special" tiers — and the budget is enforced **per method, per workspace, per app**, not per user account, so one busy workspace cannot starve another workspace using the same app. `chat.postMessage` is a special tier at roughly one message per second per channel, with short bursts tolerated. Exceeding a limit returns HTTP `429 Too Many Requests` with a `Retry-After` header, and the same tier applies on every Slack plan — paying more does not buy a higher API rate. The design lesson generalizes: key the limiter on the tenant boundary you actually want to protect, give expensive methods their own tier rather than one global budget, and implement the counter with a Redis Lua script so the check-and-increment is atomic across app instances.

---

## 8. Tradeoffs

### Algorithm Comparison

| Algorithm                | Burst Handling | Boundary Burst Problem | Memory per Identity | Accuracy  | Implementation Complexity |
|--------------------------|----------------|------------------------|---------------------|-----------|---------------------------|
| Token bucket             | Yes            | No                     | O(1)                | Exact      | Medium                    |
| Leaky bucket             | No (smoothing) | No                     | O(queue_depth)      | Exact      | Medium                    |
| Fixed window counter     | Partial        | Yes (2x burst)         | O(1)                | Approximate | Low                      |
| Sliding window log       | Yes            | No                     | O(limit)            | Exact      | Medium                    |
| Sliding window counter   | Partial        | Minimal (interpolated) | O(1)                | 0.003% wrong decisions over 400M reqs (Cloudflare) | Medium |

### Rate Limiting Location Comparison

| Location         | Pros                                                | Cons                                                    |
|------------------|-----------------------------------------------------|---------------------------------------------------------|
| Client-side only | Zero network round-trips                            | Unenforceable — malicious clients bypass                |
| API Gateway      | Enforced before reaching app servers                | Gateway becomes a bottleneck; needs distributed state   |
| Application code | Access to business context (user tier, plan type)   | Adds latency to every request; complex distributed state |
| Service mesh     | No code changes required; policy-driven             | Coarse-grained (per service, not per user/endpoint)     |

### Redis vs. In-Memory Rate Limiting

| Dimension              | In-Memory (per instance)    | Redis (shared)                        |
|------------------------|-----------------------------|---------------------------------------|
| Latency                | Sub-microsecond             | 0.5–1ms round-trip                    |
| Accuracy               | Inaccurate (per-instance)   | Accurate across all instances         |
| Failure mode           | Survives Redis outage       | Rate limiting fails open or closed    |
| Complexity             | Simple                      | Requires Lua scripts for atomicity    |
| Horizontal scaling     | Limits scale proportionally | Single global limit enforced          |

---

## 9. When to Use / When NOT to Use

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    q1{"Boundary burst<br/>(2x limit) unacceptable?"}
    q1 -->|"yes - fraud/financial"| q2{"Need exact<br/>enforcement?"}
    q1 -->|"no"| q3{"Clients need<br/>burst tolerance?"}

    q2 -->|"yes"| log(["Sliding window log<br/>O(limit) memory, exact"])
    q2 -->|"no, ~1% error ok"| counter(["Sliding window counter<br/>O(1) memory"])

    q3 -->|"yes"| token(["Token bucket<br/>burst + avg rate"])
    q3 -->|"no"| q4{"Need constant<br/>smooth output?"}

    q4 -->|"yes"| leaky(["Leaky bucket<br/>queues + smooths"])
    q4 -->|"no"| fixed(["Fixed window counter<br/>simplest, O(1)"])

    class q1,q2,q3,q4 mathOp
    class log,counter,token,leaky,fixed train
```

*A quick decision guide synthesized from the rules below: start with whether a 2x boundary burst is tolerable, then branch on exactness, burst tolerance, and output smoothness to land on one of the five algorithms.*

### Token Bucket — Use when:
- API clients legitimately need burst capacity (e.g., a user refreshing many widgets at login)
- You want to allow short bursts while enforcing a long-run average rate
- You are rate limiting by API key with tiered burst sizes (free: burst=10, paid: burst=100)

### Leaky Bucket — Use when:
- You need to smooth traffic before sending to a downstream system with no burst tolerance
- You are implementing a queue-based smoothing layer, not a client-facing rate limit
- Output rate must be exactly constant (e.g., billing batch jobs at exactly 100 operations/sec)

### Fixed Window — Use when:
- Simplicity is paramount and the 2x boundary burst is acceptable (e.g., internal admin tools)
- You need the lowest possible implementation complexity for non-critical endpoints

### Sliding Window — Use when:
- Boundary burst behavior is unacceptable (financial transactions, fraud prevention)
- You need exact enforcement (use sliding window log) or high-accuracy approximation (use sliding window counter)

### Do NOT use per-instance in-memory rate limiting when:
- Your service runs on more than one instance behind a load balancer
- The rate limit is important enough that under-counting by N instances matters
- You need accurate accounting for billing or compliance purposes

### Do NOT enforce rate limits without proper headers when:
- You have API clients who need to implement their own backoff
- You want to avoid turning a 429 response into an immediate retry storm

---

## 10. Common Pitfalls

### Pitfall 1: The Race Condition in Non-Atomic Counter Increments (Production War Story)

An API platform team implemented rate limiting using a non-atomic Redis GET-then-SET pattern:

```java
// BROKEN: non-atomic GET + conditional SET creates a race condition
Long count = redisTemplate.opsForValue().get(key);
if (count == null || count < limit) {
    redisTemplate.opsForValue().increment(key);  // NOT atomic with the check above
    filterChain.doFilter(request, response);      // allow
} else {
    response.setStatus(429);
}
```

Under high concurrency, 50 threads could all read `count = 999`, all decide the limit (1000) had not been reached, and all increment — resulting in a final count of 1049 for that window. The overshoot repeats every window under sustained concurrency, so the effective limit sits well above the configured one; how far above depends on thread count and Redis round-trip time, so treat any single multiple as illustrative rather than a constant. The fix: use a Lua script or Redis `MULTI/EXEC` transaction to make the check-and-increment atomic.

```java
// FIX: atomic increment + check with Lua script
Long newCount = redisTemplate.execute(incrementScript, List.of(key), String.valueOf(limit));
if (newCount == null || newCount > limit) {
    response.setStatus(429);
    return;
}
filterChain.doFilter(request, response);
```

### Pitfall 2: Rate Limiting on IP Address Behind a NAT or Proxy

A SaaS company applied rate limits of 100 requests per minute per IP address. A large enterprise customer had 500 employees behind a corporate NAT gateway — all sharing the same public IP address. The entire enterprise was effectively limited to 100 req/min shared across all 500 users. The first 3–4 active users would exhaust the budget and the rest would receive 429 errors for the remainder of the minute. The fix: for authenticated endpoints, always rate limit by user ID or API key, not by IP address. Use IP-based rate limiting only for unauthenticated endpoints as a DDoS protection backstop.

### Pitfall 3: Ignoring `Retry-After` and Triggering Retry Storms

A mobile application received a 429 response and immediately retried with a 1-second fixed delay. Under load, this meant every throttled client was retrying at t+1s, t+2s, t+3s simultaneously. The retries added 20–30% additional load on top of the organic traffic, pushing more clients into the rate limited state, generating more retries — a classic feedback loop. The fix: parse the `Retry-After` header and add jitter. If `Retry-After: 30`, wait 30 seconds plus random(0, 10) seconds before retrying.

### Pitfall 4: Clock Skew in Distributed Systems Corrupting Window Boundaries

A team used a fixed window counter keyed on Unix timestamp rounded to the minute (e.g., `ratelimit:user123:1698765420`). When they deployed across two data centers in different regions, a 3-second clock skew between data center clocks caused some requests to be counted in the wrong window. At the boundary of a minute, one data center's clock rolled over while another did not. Requests near the boundary were counted in different windows depending on which data center served them, effectively doubling the burst capacity. The fix: use Redis server time (`TIME` command) as the authoritative clock source instead of the application server clock. All Lua scripts should call `redis.call('TIME')` internally.

### Pitfall 5: Rate Limiting Without Observability

A team deployed rate limiting but did not add metrics. Three weeks later, a legitimate paying enterprise customer complained that their integration was intermittently failing with unexplained errors. Investigation revealed they had been hitting the rate limit (which was set at the free-tier level by mistake) for 3 weeks. Because there was no alert on high 429 rates per API key, no one noticed. The fix: emit a metric `rate_limit.rejected` tagged with `tier`, `endpoint`, and `client_id` (masked for privacy). Alert when any single client hits the rate limit more than 5% of the time in a 5-minute window — that signals either misconfiguration or a client that needs to upgrade to a higher tier.

### Pitfall 6: Leaky Bucket Adding Unacceptable Latency

A team used a leaky bucket to smooth requests to a third-party payment processor. The outflow rate was 50 requests per second (their contracted limit). Under normal load (30 req/s), requests queued briefly and were processed within 20ms. Under a burst of 200 requests, the queue filled and requests waited up to 4 seconds to be processed. But the HTTP client on the calling service had a 2-second timeout. Requests were accepted by the leaky bucket, queued for 3–4 seconds, and then the HTTP client timed out — resulting in the worst of both worlds: the requests consumed a queue slot but never completed. The fix: set the leaky bucket queue depth to `outflow_rate * max_acceptable_wait_seconds`. If max wait is 1 second and outflow rate is 50/s, set queue depth to 50. Reject (429) immediately when the queue is full instead of queuing indefinitely.

---

## 11. Technologies & Tools

| Technology                   | Role                                                         | Notes                                               |
|------------------------------|--------------------------------------------------------------|-----------------------------------------------------|
| Redis + Lua                  | Distributed atomic rate limiting (sliding window)            | Single-threaded Lua execution guarantees atomicity  |
| Redis `INCR` + `EXPIRE`      | Simple fixed window counter                                  | Simplest approach; prone to boundary burst          |
| Nginx `limit_req`            | Edge rate limiting before reaching application               | `limit_req_zone`, `burst`, `nodelay`                |
| HAProxy `stick-table`        | Connection-level and request-level rate limiting             | More complex config than Nginx                      |
| Bucket4j                     | Java in-memory and Redis-backed token bucket                 | Good for application-level limiting                 |
| Resilience4j `RateLimiter`   | Per-service rate limiting in application code                | Integrates with circuit breaker / retry              |
| Spring Cloud Gateway         | Built-in `RequestRateLimiter` filter using Redis             | Plug-and-play for Spring microservices              |
| AWS API Gateway               | Managed rate limiting (10,000 req/s per account per Region, burst 5,000) | Rate is adjustable on request; the burst quota is not. Some newer Regions default to 2,500 rps / 1,250 burst |
| Cloudflare Rate Limiting      | Edge-level rate limiting with geo-awareness                  | DDoS protection + API limiting in one; counters are kept per data center, not globally |
| Kong                         | API gateway with rate limiting plugin                        | Supports Redis backend for distributed limiting     |
| Envoy                        | Rate limiting via external RLS (Rate Limit Service)          | Integrates with Lyft's `ratelimit` service          |

### Bucket4j with Spring Boot

```java
@Configuration
public class RateLimiterConfig {

    @Bean
    public Map<String, Bucket> userBuckets() {
        return new ConcurrentHashMap<>();
    }

    // Per-user token bucket: 100 tokens, refilled at 100/minute.
    // Bucket4j's staged Bandwidth.builder() forces capacity before refill, so an
    // under-specified bandwidth will not compile.
    public Bucket getBucketForUser(String userId) {
        return userBuckets().computeIfAbsent(userId, k ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(100)
                    .refillGreedy(100, Duration.ofMinutes(1))
                    .build())
                .build()
        );
    }
}

@Service
public class RateLimitService {
    private final RateLimiterConfig config;

    public boolean tryConsume(String userId) {
        Bucket bucket = config.getBucketForUser(userId);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        // probe.getRemainingTokens() for X-RateLimit-Remaining header
        // probe.getNanosToWaitForRefill() for Retry-After header
        return probe.isConsumed();
    }
}
```

---

## 12. Interview Questions with Answers

**Q: What is the boundary burst problem with fixed window counters and how do sliding windows solve it?**
**Short:** A fixed window counter lets clients double their limit by bursting requests across the window boundary.

A fixed window counter lets a client extract `2 * limit` requests in a very short span by straddling the window boundary. It sends `limit` requests in the last millisecond of one window and `limit` more in the first millisecond of the next, because the counter resets hard at the boundary. A sliding window tracks requests over a rolling window ending at the current moment, so the window always contains at most `limit` requests in the most recent `windowSize` duration, regardless of where the clock boundary falls.

**Q: Explain the token bucket algorithm. How does it differ from a leaky bucket?**
**Short:** Token bucket allows bursts up to capacity at a fixed refill rate; leaky bucket smooths output to a constant rate.

The token bucket accumulates tokens at a fixed rate up to a maximum capacity. Each request consumes one or more tokens. Requests are allowed when tokens are available and rejected when the bucket is empty. This allows bursts up to the bucket capacity while enforcing a long-run average equal to the refill rate. The leaky bucket processes requests at a constant outflow rate regardless of input rate — it does not allow bursts, it smooths them by queueing. Token bucket: variable output, constant average, burst-friendly. Leaky bucket: constant output, no burst, smoothing-focused.

**Q: How do you implement atomic rate limiting in Redis without race conditions?**
**Short:** Wrap the check-and-update logic in a single atomic Redis Lua script to eliminate race conditions.

Use a Lua script. Redis executes Lua scripts atomically in a single-threaded manner, making the entire check-and-update a single uninterruptible operation. The alternative is Redis transactions (`MULTI/EXEC`), but they do not prevent other clients from modifying keys between `WATCH` and `EXEC`, requiring retry logic. Lua scripts are the correct approach: wrap `ZREMRANGEBYSCORE`, `ZCARD`, `ZADD`, and `PEXPIRE` in a single Lua script loaded with `SCRIPT LOAD` and called with `EVALSHA`.

**Q: Why is rate limiting by IP address problematic for enterprise customers?**
**Short:** Per-IP limits punish an entire enterprise sharing one NAT IP; rate limit by API key or user ID instead.

Enterprise customers often have hundreds or thousands of employees behind a shared corporate NAT or proxy, meaning all their traffic originates from a single public IP address. Applying per-IP rate limits treats the entire enterprise as a single user. The correct approach is to rate limit by API key or authenticated user ID for all authenticated traffic, and only fall back to IP-based limiting for unauthenticated endpoints as a DDoS backstop.

**Q: What rate limit response headers should you return and what do each mean?**
**Short:** Return X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, and Retry-After on every 429 response.

`X-RateLimit-Limit`: the maximum requests allowed in the window. `X-RateLimit-Remaining`: how many requests the client can still make in the current window. `X-RateLimit-Reset`: Unix timestamp when the window resets and the full limit is available again. `Retry-After`: on a 429 response, the number of seconds the client must wait before retrying. These headers allow well-behaved clients to pre-emptively back off before exhausting their limit and to retry at exactly the right time rather than polling.

**Q: How would you rate limit a GraphQL API where requests have variable cost?**
**Short:** Assign each GraphQL field a cost and cap total cost points per window instead of counting raw requests.

Assign a cost to each field and operation based on complexity (number of database queries it triggers, depth of nested resolvers, number of objects returned). Limit by total cost points per window rather than by request count. For example, a simple field lookup costs 1 point; a paginated list query costs 10 points per page; a query that fetches nested relationships costs multiplicatively. A client with a budget of 1,000 points per minute can make many simple queries but only a few complex ones. GitHub's GraphQL API uses this exact approach.

**Q: What is the sliding window counter approximation and what is its error bound?**
**Short:** It estimates the current rate from two adjacent fixed windows, discounting the prior one by its remaining overlap.

It approximates a sliding window from two adjacent fixed-window counters, discounting the previous one by how much of it still overlaps. The formula is `estimated = prev_count * (1 - elapsed_fraction) + curr_count`, where `elapsed_fraction` is how far through the current window you are. The error comes entirely from assuming the previous window's traffic was spread evenly across it, and it over-counts when that traffic was front-loaded — so the limiter errs toward failing closed, the right direction. The only large-scale published measurement is Cloudflare's: across 400 million requests, 0.003% were wrongly allowed or wrongly limited, with an average 6% gap between the real rate and the approximated one.

**Q: How does Google's adaptive throttling work and why is it superior to static rate limiting for preventing cascading failures?**
**Short:** Clients throttle themselves based on their own request-to-accept ratio, stopping retry storms before overload.

Google's adaptive throttling tracks requests and accepts on the client side. The client probabilistically skips sending requests when `throttle_probability = max(0, (requests - K * accepts) / (requests + 1))` is high. When the server starts rejecting requests, the client automatically reduces its send rate proportionally — before the server is overwhelmed with retries. Static server-side rate limiting returns 429 errors, which well-behaved clients retry after a delay. Adaptive throttling prevents the retry storm itself: clients that are already seeing rejections do not send new requests, reducing load automatically.

**Q: How do you handle rate limiting for long-polling or streaming connections?**
**Short:** Rate limit long-lived connections by bandwidth, concurrent count, or events per second, not request count.

Standard request count rate limiting does not work well for long-lived connections. Instead, rate limit by bandwidth (bytes per second), by number of concurrent connections per identity, or by the number of events emitted per second. For WebSocket connections, rate limit the initial handshake (connection rate limiting) and then rate limit messages within the connection. A client that maintains 100 WebSocket connections to circumvent per-connection rate limits should be detected and limited at the identity level by tracking total concurrent connections per API key.

**Q: What is the difference between rate limiting and throttling?**
**Short:** Rate limiting hard-rejects requests past a cap; throttling delays them instead of dropping them.

Rate limiting enforces a hard cap: once the limit is exceeded, requests are rejected with a 429. Throttling slows requests down: it artificially delays processing (e.g., sleeping before processing) to stay within capacity. Rate limiting is more common for API quotas because it is simple and deterministic for clients. Throttling is used in queue-based systems and leaky bucket implementations where requests are deferred rather than dropped. In practice, the terms are often used interchangeably in API contexts.

**Q: How would you implement rate limiting across multiple data centers without requiring cross-DC synchronization on every request?**
**Short:** Enforce a local share of the global limit per data center and periodically resync counts across regions.

Use a two-level approach: a local Redis cluster per data center enforces a fraction of the total limit (`total_limit / num_datacenters`). Each data center enforces its local fraction without cross-DC calls. Periodically synchronize counts across data centers (every 5–10 seconds) to rebalance. This means clients can exceed the global limit by up to `(N-1) / N * limit` in the worst case during a synchronization interval, but eliminates cross-DC latency on every request. Cloudflare has published the edge version of this tradeoff: it keeps an isolated counting system inside each PoP rather than reporting every counter to one central service, because a central counter cannot meet edge latency and availability requirements — and it accepts the consequence that traffic spread across many data centers can stay under the per-data-center threshold while exceeding the aggregate.

**Q: What happens to your rate limiter when Redis is unavailable?**
**Short:** Fail open for authenticated traffic and fail closed for unauthenticated endpoints when Redis is down.

You must decide: fail open (allow all requests when Redis is down) or fail closed (reject all requests when Redis is down). Fail open is typically correct for API rate limiting: a brief Redis outage should not take down the entire API. However, fail open during an extended outage may allow abuse. A good compromise: fail open for authenticated users (who have agreed to terms of service) and fail closed for unauthenticated endpoints (to prevent DDoS amplification during outages). Always alert immediately when Redis is unreachable so the outage is detected quickly.

**Q: What happens when clients ignore Retry-After and retry with a fixed delay?**
**Short:** Fixed-delay retries synchronize into a wave that reamplifies load; add jitter on top of Retry-After.

Fixed-delay retries synchronize into a wave, so every throttled client hammers the API again at the same instant instead of spreading their retries out. A mobile application that retried exactly 1 second after every 429 added 20 to 30 percent extra load on top of organic traffic, pushing more clients into the rate-limited state and triggering more retries in a self-reinforcing feedback loop. The retry storm can end up costing more capacity than the traffic spike that triggered throttling in the first place. Parse the `Retry-After` header and add random jitter — wait the specified duration plus a random 0 to 10 seconds — instead of retrying on a fixed schedule.

**Q: Why is observability critical for a rate limiter, and what should you alert on?**
**Short:** Without a rejection-rate alert, a misconfigured tier limit can silently break a paying customer for weeks.

Without metrics on rejections, a misconfigured or wrong-tier rate limit can silently fail a paying customer for weeks before anyone notices. One team only discovered that an enterprise account had been mistakenly capped at the free tier's limit after the customer complained — three weeks of intermittent failures had gone unnoticed because no alert existed for elevated 429 rates on a single client. Rate limit rejections are a signal worth watching in their own right, not just an error code to return: a spike can mean misconfiguration, an abusive client, or a legitimate customer who needs to upgrade tiers. Emit a `rate_limit.rejected` metric tagged by tier, endpoint, and client ID, and alert when any single client is rejected more than 5 percent of the time within a 5-minute window.

**Q: How can clock skew between data centers break a fixed-window rate limiter?**
**Short:** A few seconds of clock skew between data centers can double a fixed window's effective limit at the boundary.

A fixed window counter keyed by a rounded timestamp assigns requests to the wrong window whenever two data centers' clocks disagree, doubling the effective burst at the boundary. One team saw a 3-second clock skew between two data center clocks cause requests near a minute boundary to land in different windows depending on which data center happened to serve them, doubling the effective limit for clients whose requests split across both. Application server clocks are not reliably synchronized enough for window boundaries that matter, especially across regions. Use Redis's own `TIME` command as the authoritative clock inside the Lua script rather than trusting each application server's local clock.

**Q: Why can a leaky bucket rate limiter make latency worse instead of smoothing it?**
**Short:** A leaky bucket queue deeper than the caller's timeout wastes capacity on requests that time out anyway.

If the bucket's queue is deeper than the caller's own timeout allows, requests get queued, wait, and time out anyway — wasting a slot on a request that never completes. A queue tuned for a 50 requests-per-second outflow rate could make requests wait up to 4 seconds under a 200-request burst, but if the calling HTTP client has only a 2-second timeout, those requests are "successfully" queued yet still fail from the caller's perspective. This is worse than rejecting immediately, because the request consumed capacity and added latency without ever producing a usable response. Size queue depth as `outflow_rate * max_acceptable_wait_seconds` and reject with a 429 immediately once the queue is full instead of queuing indefinitely.

---

## 13. Best Practices

**Use different rate limit windows for different purposes.** A burst limit (10 requests per second) prevents automated scrapers. A sustained limit (1,000 requests per hour) prevents excessive API usage. A daily limit (10,000 requests per day) enforces pricing tiers. Deploy all three simultaneously for important APIs.

**Always return `Retry-After` in 429 responses.** Clients that receive 429 without a Retry-After header will either implement their own (potentially aggressive) backoff or poll continuously. Providing `Retry-After` makes clients predictable and reduces retry load.

**Log rate limit rejections with client identity.** Each 429 response should log the client key, the endpoint, the limit, and the current count. This data is essential for: detecting misconfigured clients, identifying limits that need adjustment, and building dashboards that show which API consumers are approaching their limits.

**Provide a "warning" threshold.** When a client has used 80% of their rate limit, return a header like `X-RateLimit-Warning: approaching_limit`. This allows proactive clients to self-throttle before hitting the limit and receiving errors.

**Test rate limit behavior explicitly.** Write integration tests that exercise the rate limit boundary: N-1 requests (allowed), Nth request (allowed), N+1 request (rejected with 429), waiting for window reset (allowed again). These tests catch atomicity bugs and off-by-one errors that are common in rate limiter implementations.

**Separate rate limiting from authorization.** Rate limiting determines how much a client can do; authorization determines what they are allowed to do. Keep these concerns in separate filters/middlewares. A client that is authenticated but rate limited should receive a 429, not a 401 or 403.

**Use consistent key naming in Redis.** Use a scheme like `ratelimit:{type}:{identity}:{endpoint}:{window}` (e.g., `ratelimit:apikey:abc123:POST:/orders:60`). This makes debugging rate limit issues in Redis straightforward and allows targeted inspection of specific clients.

---

## 14. Case Study

### High-Traffic Public API: Multi-Tier Rate Limiting at Scale

**Problem:** A public data API serving 50 million requests per day across 100,000 registered API keys with three tiers: Free (100 req/min), Standard (5,000 req/min), Enterprise (custom). The API runs on 20 Spring Boot instances behind an AWS Application Load Balancer. The engineering team needs accurate cross-instance rate limiting, burst tolerance for paying customers, and protection against DDoS from unauthenticated endpoints.

**Architecture:**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    net([Internet]) --> cf["Cloudflare<br/>DDoS + IP limit<br/>50 req/s per IP"]
    cf --> alb["AWS ALB<br/>TLS termination"]
    alb --> s1["API #1"]
    alb --> s2["API #2"]
    alb --> s3["API #3<br/>...20 total"]
    s1 --> redis@{ icon: "logos:redis", form: "square", label: "Redis Cluster", pos: "b", h: 44 }
    s2 --> redis
    s3 --> redis

    class net io
    class cf frozen
    class alb mathOp
    class s1,s2,s3 req
```

*Three defense layers: the external Cloudflare edge filters bots by IP before origin, the ALB terminates TLS and routes to 20 Spring Boot instances, and every instance enforces tier limits against a shared 6-node Redis cluster (3 primary, 3 replica) averaging 0.8ms latency (2ms p99).*

**Rate Limiting Implementation:**

Three layers of rate limiting:
1. Cloudflare edge: IP-based, 50 req/s per IP (catches bots and scanners before they touch origin)
2. Spring Boot filter: per-API-key sliding window counter in Redis (enforces tier limits)
3. Application layer: per-user-per-endpoint limits for expensive operations (search: 10 req/min even for Enterprise)

**Tier Configuration:**

```yaml
rate-limiting:
  tiers:
    free:
      requests-per-minute: 100
      burst: 20
      daily-limit: 1000
    standard:
      requests-per-minute: 5000
      burst: 500
      daily-limit: 100000
    enterprise:
      requests-per-minute: 50000
      burst: 5000
      daily-limit: -1  # unlimited
  expensive-endpoints:
    /api/search:
      requests-per-minute: 10  # all tiers
    /api/export:
      requests-per-minute: 2   # all tiers
```

**Key Design Decisions:**

1. Sliding window counter (not log) for tier limits: O(1) memory per key vs. O(limit) for sliding window log. At 100,000 API keys, sliding window log would require 500 million Redis entries at peak; sliding window counter requires 200,000 entries (two per key).

2. Lua scripts for all Redis operations: eliminates race conditions completely. Scripts are loaded once at startup with `SCRIPT LOAD` and called via `EVALSHA`.

3. Separate daily limit counter: the per-minute counter is a sliding window; the daily counter is a fixed window (reset at midnight UTC). Both must pass for a request to be allowed.

4. Rate limit metadata in Redis: each API key's tier is cached in Redis alongside the counter (`ratelimit:meta:apikey` with TTL=5min) to avoid a database lookup on every request. Cache is invalidated when the customer upgrades their tier.

**Results:**
- Rate limiter adds 1.2ms average latency (0.8ms Redis round-trip + 0.4ms Lua execution)
- Zero race conditions: atomic Lua scripts eliminate all boundary violations
- During a credential leak incident, the compromised API key's burst of 50,000 requests in 10 seconds was blocked after 100 requests (free tier limit). Without rate limiting, the leak would have scraped 50,000 records.
- Layered edge-then-origin limiting meant the CDN's IP rules absorbed the overwhelming majority of a volumetric HTTP flood and Nginx `limit_req` caught the residue, so no attack traffic reached the application servers. For scale context, the published records for HTTP request floods are Cloudflare's 17.2M rps (2021), 26M rps (2022), 71M rps (Feb 2023) and just over 201M rps (Aug 2023, HTTP/2 Rapid Reset) — a level the Aisuru-Kimwolf botnet reached again in December 2025, with HTTP floods Cloudflare reports as exceeding 200M rps. Volumetric records are measured in Tbps: Cloudflare blocked 7.3 Tbps in May 2025 and a record 31.4 Tbps in Q4 2025. Size the edge tier against those numbers, not against origin capacity.
