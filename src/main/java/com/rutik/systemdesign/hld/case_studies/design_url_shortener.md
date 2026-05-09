# System Design: URL Shortener (like bit.ly)

## Table of Contents
1. Requirements Clarification
2. Scale Estimation
3. High-Level Architecture
4. URL Shortening Algorithm
5. Redirect Mechanism
6. Database Design
7. Caching Strategy
8. Consistent Hashing
9. Analytics
10. Custom Aliases
11. Expiration and Cleanup
12. Rate Limiting
13. Bottlenecks and Solutions
14. Trade-offs
15. Interview Discussion Tips

---

## Intuition

> **Design intuition**: A URL shortener is the "Hello World" of system design — it seems simple (store a mapping, redirect on lookup) but teaches all the fundamentals: hash function choice, database schema, caching strategy (reads are 100:1 over writes), and analytics pipeline. Master this and you understand distributed system basics.

**Key insight**: The redirect is the hot path — 1 billion redirects/day means ~12,000 RPS. A Redis cache in front of the database turns this from a 10ms database lookup into a 1ms cache hit, enabling the system to handle massive read scale on modest hardware.

---

## 1. Requirements Clarification

### Functional Requirements
- **Shorten URL**: Given a long URL, generate a short unique alias (e.g., `bit.ly/abc1234`)
- **Redirect**: When a user visits the short URL, redirect them to the original long URL
- **Analytics**: Track click count, geographic distribution, device type, referrer per short URL
- **Custom Aliases**: Allow users to specify a custom short code (e.g., `bit.ly/my-brand`)
- **Expiration**: Short URLs can have an optional expiry date after which they no longer redirect
- **User Accounts**: Registered users can view and manage their shortened URLs

### Non-Functional Requirements
- **High Availability**: Redirects must work 99.99% of the time — downtime means broken links
- **Low Latency**: Redirect should complete in < 10ms (users expect instant redirects)
- **Read-Heavy**: 100:1 read-to-write ratio (most traffic is redirects, not URL creation)
- **Durable**: Once created, short URLs must not be lost
- **Unpredictable IDs**: Short codes should not be guessable (security)
- **Scale**: Handle 10B redirects/day at peak

### Out of Scope
- QR code generation (separate feature, can be layered on top)
- Link-in-bio pages
- Team/collaboration features

---

## 2. Scale Estimation

### Write Traffic
- 100M new URLs shortened per day
- 100M / 86,400 sec = **1,157 writes/sec** (~1,200/sec)
- Peak: 3x average = **3,600 writes/sec**

### Read Traffic
- 10B redirects per day (100:1 read-to-write ratio)
- 10B / 86,400 sec = **115,740 reads/sec** (~116K/sec)
- Peak: 3x = **350K reads/sec**

### Storage Calculation
- Per URL record:
  - short_url: 7 bytes
  - long_url: avg 100 bytes (URLs vary, 2048 max)
  - user_id: 8 bytes
  - created_at: 8 bytes
  - expires_at: 8 bytes
  - metadata: ~20 bytes
  - **Total: ~150 bytes per URL**
- 5-year storage:
  - 100M URLs/day * 365 * 5 = **182.5B URLs**
  - 182.5B * 150 bytes = **27 TB** for URL mapping data
  - Analytics data (click events): much larger (~1 KB per click event)
  - 10B clicks/day * 1 KB * 365 * 5 = **18.25 PB** (aggregated, not raw: ~18 TB)

### URL Space
- Short code length 7, Base62 alphabet (a-z, A-Z, 0-9):
- 62^7 = **3.5 trillion** unique short codes — sufficient for decades

---

## 3. High-Level Architecture

```
                         +------------------+
                         |   DNS / CDN      |
                         +--------+---------+
                                  |
                         +--------v---------+
                         |  Load Balancer   |
                         +---+----------+---+
                             |          |
               +-------------v---+  +---v-------------+
               |  Write API      |  |  Read API       |
               | (URL Shortening)|  | (Redirects)     |
               +--------+--------+  +--------+--------+
                        |                    |
           +------------v--+          +------v-------+
           |  URL Service  |          |  Cache Layer |
           | (ID Gen +     |          |  (Redis)     |
           |  Storage)     |          +------+-------+
           +------+--------+                 |
                  |                  (miss)  |
         +--------v--------+         +-------v------+
         | ID Generator     |         | Database     |
         | (Counter +       |         | (Cassandra)  |
         | Zookeeper)       |         +--------------+
         +--------+--------+
                  |
         +--------v--------+
         | Database Write  |
         | (Cassandra)     |
         +-----------------+

         +--------------------------------------+
         | Analytics Pipeline                   |
         | Click Event → Kafka → Flink →        |
         | Cassandra (time-series) → Dashboard  |
         +--------------------------------------+
```

---

## 4. URL Shortening Algorithm

### The Core Problem
Given a long URL, generate a short unique code of length 7.

### Option A: Cryptographic Hash (MD5/SHA256)
```
long_url = "https://example.com/very/long/path"
hash = MD5(long_url)  # = "e3d70bc1b4ece4..."
short_code = hash[:7] = "e3d70bc"
```
**Pros**: Stateless, no central coordinator needed, same URL always gets same short code

**Cons**:
- **Hash collisions**: Two different URLs can produce same first 7 chars
  - With 3.5T possibilities and birthday paradox, collisions become likely after ~2.5M URLs
- Must query DB to check if short_code is taken (on collision, take next 7 chars)
- Non-sequential: no natural ordering

### Option B: Base62 Encoding of Auto-Increment ID
```
auto_increment_id = 100000
Base62 encoding:
  100000 % 62 = 18 → 'S'
  1612   % 62 = 8  → '8'
  26     % 62 = 26 → 'Q'
  0      % 62 = 0  → 'a'
  ...
short_code = "aaQ8Sa"  (padded to 6-7 chars)
```
**Pros**: No collisions, predictable length, naturally unique

**Cons**:
- Sequential IDs are guessable (attacker can enumerate all URLs: `aaaaaa1`, `aaaaaa2`, ...)
- Single auto-increment DB is a bottleneck at high write rates
- Solution: Use distributed ID generators

### Option C: Pre-Generated Random IDs (ID Pool)
```
Background job:
  1. Generate random 7-char Base62 strings
  2. Verify they are not already used
  3. Store in a "key pool" table: id_pool(key VARCHAR(7), used BOOLEAN)

URL Shortening:
  1. Pop an unused key from the pool
  2. Mark it as used
  3. Associate with the long URL
```
**Pros**: Truly random (not guessable), no collision risk at point of use, fast lookup

**Cons**: Key pool must be maintained; potential for pool exhaustion under extreme load

### Recommended Approach: Counter + Base62 with Distributed Counter

Use a distributed counter (Zookeeper or dedicated counter service) with Base62 encoding:

```
Setup:
  - Multiple counter servers, each assigned a range by Zookeeper
  - Counter Server A: range 1 - 1,000,000
  - Counter Server B: range 1,000,001 - 2,000,000
  - Counter Server C: range 2,000,001 - 3,000,000

URL Creation:
  1. API Server asks Counter Server for next ID
  2. Counter Server atomically increments and returns ID (e.g., 1000042)
  3. API Server encodes ID in Base62: 1000042 → "4c92"
  4. Pad to 7 chars: "000c92" or add prefix: "4c9200a"
  5. Write (short_code, long_url, metadata) to Cassandra

Zookeeper manages range assignments:
  - When Counter Server exhausts its range, it requests a new range from Zookeeper
  - Zookeeper maintains a global counter of which ranges have been assigned
  - If a Counter Server dies, its remaining range is abandoned (tiny waste, acceptable)
```

**Why this is best**: No collisions, distributed (no single bottleneck), not easily guessable (IDs are non-sequential when interleaved across multiple counter servers), fast.

---

## 5. Redirect Mechanism

### HTTP 301 vs. 302
| | 301 Permanent Redirect | 302 Temporary Redirect |
|---|---|---|
| Browser behavior | Caches the redirect; future clicks go directly to long URL (bypasses short URL server) | Always hits the short URL server |
| Analytics | Breaks analytics after first click — browser never calls our server again | Every click goes through our server → can track all clicks |
| Performance | Faster for repeat visits (no server hit) | Slight latency on every visit (server roundtrip) |
| Use case | When the mapping is permanent and analytics aren't needed | When analytics tracking is required |

**Decision: Use 302 for analytics tracking.** This is the expected answer in system design interviews when analytics is a requirement.

```
HTTP Request:  GET http://short.url/abc1234
HTTP Response: HTTP/1.1 302 Found
               Location: https://original-long-url.com/path
               Cache-Control: no-cache
```

For link owners who prefer performance over analytics, offer an option to use 301 (common in enterprise plans).

### Redirect Flow
```
Client GET /abc1234
    |
    v
Load Balancer → Read API Server
    |
    | 1. Look up "abc1234" in Redis cache
    | 2. Cache HIT: return 302 with cached long_url + record click event async
    | 3. Cache MISS:
    |       - Query Cassandra for "abc1234"
    |       - If found: populate cache, return 302 redirect, record click async
    |       - If not found or expired: return 404
    v
Client follows redirect → Original URL
```

---

## 6. Database Design

### Why Cassandra?
- Write-heavy (1,200 new URLs/sec) + read-heavy (116K redirects/sec after cache)
- Simple key-value access pattern (lookup by short_url)
- No complex joins needed
- Horizontal scalability across multiple data centers
- Cassandra is a perfect fit for this access pattern

### Schema
```sql
-- Primary URL mapping
CREATE TABLE url_mapping (
    short_url   VARCHAR(7) PRIMARY KEY,
    long_url    TEXT NOT NULL,
    user_id     UUID,
    created_at  TIMESTAMP,
    expires_at  TIMESTAMP,        -- NULL means no expiration
    is_active   BOOLEAN DEFAULT TRUE,
    custom_alias BOOLEAN DEFAULT FALSE
);

-- User's URL history (lookup all URLs by user)
CREATE TABLE urls_by_user (
    user_id     UUID,
    created_at  TIMESTAMP,
    short_url   VARCHAR(7),
    long_url    TEXT,
    click_count BIGINT,
    PRIMARY KEY (user_id, created_at, short_url)
) WITH CLUSTERING ORDER BY (created_at DESC);

-- Analytics: click events (time-series)
CREATE TABLE click_events (
    short_url   VARCHAR(7),
    clicked_at  TIMESTAMP,
    country     VARCHAR(50),
    device      VARCHAR(20),    -- mobile, desktop, tablet
    browser     VARCHAR(50),
    referrer    TEXT,
    ip_address  INET,
    PRIMARY KEY (short_url, clicked_at)
) WITH CLUSTERING ORDER BY (clicked_at DESC)
  AND default_time_to_live = 7776000;  -- 90 days TTL for raw click events

-- Analytics: aggregated stats
CREATE TABLE url_stats_hourly (
    short_url   VARCHAR(7),
    hour_bucket TIMESTAMP,       -- truncated to hour
    click_count BIGINT,
    country_counts MAP<TEXT, BIGINT>,
    device_counts  MAP<TEXT, BIGINT>,
    PRIMARY KEY (short_url, hour_bucket)
) WITH CLUSTERING ORDER BY (hour_bucket DESC);
```

---

## 7. Caching Strategy

### Why Cache?
- 116K reads/sec — even fast Cassandra can't handle this efficiently without caching
- 80/20 rule: **20% of URLs get 80% of traffic** (viral links, popular campaigns)
- A cached redirect = ~0.1ms vs. a DB read = ~5-10ms

### Redis Cache Configuration
```bash
# Set URL in cache with TTL matching expiration
SET url:abc1234 "https://original-url.com/path" EX 86400  # 1 day TTL

# For URLs with explicit expiration: TTL = time until expiry
SET url:xyz789 "https://example.com" EXAT 1735689600  # Unix timestamp of expiry

# For permanent URLs: use long TTL (30 days), rely on LRU eviction for cold URLs
SET url:pqr123 "https://example.com" EX 2592000  # 30 days
```

### Cache Eviction Policy
- Use **LRU (Least Recently Used)** eviction — evict the least recently accessed URLs
- In Redis: `maxmemory-policy allkeys-lru`
- Memory sizing:
  - 20% of 100M URLs = 20M URLs * (7 + 100 bytes avg) = **~2 GB**
  - Allocate 10-20 GB Redis cluster to cache the hot 20% comfortably

### Cache-Aside Pattern
```
function getRedirectUrl(short_url):
  1. Try Redis: url = cache.get("url:" + short_url)
  2. If found:
       log_click_event_async(short_url)
       return url
  3. If not found (cache miss):
       url = cassandra.get(short_url)
       if url is None or url.expired:
           return 404
       cache.set("url:" + short_url, url.long_url, ttl=...)
       log_click_event_async(short_url)
       return url.long_url
```

### Cache Hit Rate
- Target: > 95% cache hit rate (only 5% of redirects hit Cassandra)
- Monitor: track cache hit/miss ratio in metrics dashboard

---

## 8. Consistent Hashing

### The Problem
Multiple Redis cache nodes. When we add or remove a node, how do we redistribute keys without invalidating most of the cache?

### Consistent Hashing Solution
```
                  0
               /    \
          300 /      \ 60
             /   (A)  \
            /          \
       240 |            | 120
            \    (B)   /
             \        /
          180 \      / (C)
               \    /
                180
```
- Hash ring with virtual nodes (150 virtual nodes per physical node)
- Key `url:abc1234` is hashed to a point on the ring
- Key is served by the first physical node clockwise from that point
- Adding a new node: only keys between the new node and its predecessor need to migrate
- Removing a node: its keys are absorbed by the next clockwise node

**Benefit**: Adding or removing a Redis node invalidates only ~1/N of the cache (not the whole thing).

---

## 9. Analytics

### Requirements
- Click count per URL (near-real-time)
- Geographic distribution (country-level)
- Device and browser breakdown
- Referrer analysis
- Time-series chart (clicks over time)

### Pipeline
```
Client Redirect Request
    |
    v
Read API Server — returns redirect immediately (< 10ms)
    |
    | async (non-blocking)
    v
Kafka (click_events topic)
    |
    v
Flink Stream Processor (real-time aggregation)
    |
    | 1. Count clicks per URL per minute
    | 2. Group by country, device, browser
    | 3. Update aggregated counters
    v
Cassandra (url_stats_hourly table) — aggregated time-series
    +
Cassandra (click_events table) — raw events (90-day TTL)

Dashboard Service:
    - Reads from url_stats_hourly for charts
    - Total click count maintained in Redis counter (INCR url:count:abc1234)
```

### Why Kafka?
- Decouples click recording from redirect response (redirect is synchronous; analytics is async)
- Handles bursts (viral link gets 100K clicks/sec momentarily — Kafka buffers the spike)
- Multiple consumers: one for Cassandra writes, one for real-time dashboard, one for fraud detection

### Real-Time Click Counter
For the simple click count display (most frequent analytics query):
```bash
INCR url:count:abc1234  # O(1), atomic increment in Redis
GET url:count:abc1234   # returns current count
```
Persist to Cassandra via periodic flush (every minute), not per-click.

---

## 10. Custom Aliases

### Requirements
- User specifies desired short code: e.g., `bit.ly/launch-event`
- Must be unique — check against existing URLs
- Restricted characters: alphanumeric + hyphen, length 4-50 chars
- Reserved words blocked: `api`, `admin`, `health`, `login`, `static`, etc.

### Implementation
```python
def create_custom_alias(custom_code, long_url, user_id):
    # 1. Validate format
    if not is_valid_format(custom_code):
        raise ValidationError("Invalid format")

    # 2. Check reserved words
    if custom_code in RESERVED_WORDS:
        raise ValidationError("Reserved alias")

    # 3. Atomic check-and-insert (avoid race condition)
    success = cassandra.insert_if_not_exists(
        short_url=custom_code,
        long_url=long_url,
        user_id=user_id,
        custom_alias=True
    )
    # Cassandra LWT (IF NOT EXISTS):
    # INSERT INTO url_mapping (...) VALUES (...) IF NOT EXISTS

    if not success:
        raise ConflictError("Alias already taken")

    return custom_code
```

### Reserved Words Blocklist
Store in-memory (loaded from config file at startup):
```python
RESERVED_WORDS = {
    "api", "admin", "login", "logout", "static", "assets",
    "dashboard", "health", "metrics", "signup", "register",
    # ... ~500 words
}
```

---

## 11. Expiration and Cleanup

### TTL in Redis
- Set TTL when caching: `SET url:abc1234 "..." EX {seconds_until_expiry}`
- Redis automatically evicts the key when TTL expires
- After expiry, cache misses fall through to Cassandra, which checks `expires_at`

### Database Cleanup
- Expired URLs remain in Cassandra (DB doesn't auto-delete)
- Cleanup approach: **Lazy deletion + Background sweep**

**Lazy deletion**: On every read, check `expires_at`. If expired, return 410 Gone, and mark `is_active = false`.

**Background sweep** (runs nightly):
```sql
-- Query and delete URLs expired more than 30 days ago
SELECT short_url FROM url_mapping
WHERE expires_at < (NOW() - 30 days)
AND is_active = false
LIMIT 10000;
-- Delete batch
```

### Soft Delete vs. Hard Delete
- **Choice**: Soft delete first (is_active = false), hard delete after 30 days
- **Reason**: User accidentally creates URL with wrong expiry, can restore within grace period
- **Hard delete**: reclaims storage and frees short_url for reuse (important for custom aliases)

---

## 12. Rate Limiting

### Why Rate Limiting?
- Prevent abuse: bots creating millions of URLs to spam
- Protect service from overload
- Enforce plan limits (free tier: 100 URLs/hour, paid: unlimited)

### Implementation (Token Bucket per User)
```bash
# Redis-based rate limiter (sliding window approach)
function is_rate_limited(user_id, limit=100, window=3600):
    key = "ratelimit:" + user_id
    count = redis.INCR(key)
    if count == 1:
        redis.EXPIRE(key, window)  # set TTL on first request
    if count > limit:
        return True  # rate limited
    return False
```

### For Anonymous Users
- Rate limit by IP address: `ratelimit:ip:{ip_address}`
- Stricter limits: 10 URLs/hour per IP
- Use X-Forwarded-For header (behind load balancer) but be careful of shared IPs (NAT)

### Distributed Rate Limiting
- Redis is shared across all API servers
- Atomic INCR ensures no double-counting across servers
- For more sophisticated rate limiting: use Redis + Lua script for atomic check-and-increment

---

## 13. Bottlenecks and Solutions

| Bottleneck | Impact | Solution |
|---|---|---|
| 116K redirect reads/sec | Cassandra overloaded | Redis cache (>95% hit rate reduces DB load by 20x) |
| ID generator is a single point of failure | URL creation fails | Distributed counter with multiple servers + Zookeeper range assignment |
| Write hotspot on counter | Sequential IDs go to same DB shard | Base62 encoding distributes across character space; hash short_url for sharding |
| Cache invalidation on expiry | Expired URLs still served from cache | Set cache TTL = min(expires_at TTL, max_cache_ttl); short TTL for near-expiry URLs |
| Analytics Kafka consumer lag | Click data delayed | Partition Kafka by short_url; multiple consumer instances; increase partitions |
| Custom alias race condition | Two users get same alias | Cassandra INSERT IF NOT EXISTS (LWT); single-writer mutex for hot aliases |
| Viral link (100K clicks/sec on one URL) | Cache stampede on first click | Cache warming: detect high-traffic URLs, proactively cache; mutex on cache miss |

---

## 14. Trade-offs Made

### 302 vs. 301 Redirect
- **Choice**: 302 for analytics, option to use 301 for users who opt out
- **Reason**: Analytics is a key feature; 302 ensures every click is tracked
- **Trade-off**: Slight latency on every click vs. data completeness

### Cassandra vs. MySQL for URL Storage
- **Choice**: Cassandra
- **Reason**: Simple key-value access by short_url, 116K reads/sec post-cache, write-heavy, no complex queries
- **Trade-off**: No ACID transactions (use Cassandra LWT for custom alias creation)

### Counter + Base62 vs. Pre-generated Pool
- **Choice**: Counter + Base62 with distributed counter service
- **Reason**: No key pool maintenance, predictable behavior, no risk of pool exhaustion
- **Trade-off**: Sequential IDs with slight predictability (mitigated by multiple counter servers interleaving)

### Redis LRU Cache Eviction
- **Choice**: LRU eviction for URL cache
- **Reason**: Recently accessed URLs are likely to be accessed again (recency = popularity for viral links)
- **Trade-off**: An old URL that becomes viral again will have a cold start (one cache miss before re-caching)

### Async Analytics vs. Synchronous
- **Choice**: Log click event to Kafka asynchronously, return redirect immediately
- **Reason**: Redirect latency is critical (must be < 10ms); analytics can tolerate a few seconds delay
- **Trade-off**: If Kafka consumer is down, click events may be lost temporarily (use Kafka's durability to mitigate)

---

## 15. Interview Discussion Tips

### Complete Interview Answer Structure (45 minutes)
1. **Requirements** (5 min): shorten, redirect, analytics, custom alias, expiration
2. **Scale estimation** (5 min): write out numbers explicitly — 1.2K writes/sec, 116K reads/sec, 100:1 ratio, 27TB storage
3. **High-level architecture** (5 min): draw the diagram with write path and read path separated
4. **URL shortening algorithm** (10 min): this is the most interview-worthy part — walk through all 3 options and justify your choice
5. **Redirect (301 vs 302)** (3 min): explain the trade-off clearly
6. **Caching strategy** (5 min): Redis cache-aside, LRU eviction, 80/20 rule
7. **Database design** (5 min): Cassandra schema with partition key reasoning
8. **Analytics pipeline** (3 min): Kafka → Flink → Cassandra
9. **Rate limiting** (3 min): token bucket, Redis INCR
10. **Trade-offs summary** (1 min)

### Key Things Interviewers Look For
- Explicit discussion of **hash collision** problem with MD5 approach
- Understanding of **Base62 encoding** (know the math: 62^7 = 3.5 trillion)
- Clear explanation of **301 vs 302** trade-off
- **Cache-aside pattern** for Redis (not write-through)
- Awareness of the **cache stampede** problem for viral links
- Correct partition key choice in Cassandra (short_url, not user_id for redirect table)

### Common Mistakes to Avoid
- Using MD5 without acknowledging collisions
- Choosing 301 when analytics is required
- Putting everything in MySQL (won't scale to 116K reads/sec)
- Not mentioning Redis caching (the single most important optimization)
- Forgetting the expiration mechanism
- Not mentioning rate limiting (allows the system to be abused)
- Using same table for both "lookup by short_url" and "lookup by user" — need separate tables in Cassandra

### Follow-up Questions You May Get
- "How would you handle a cache stampede when a viral link is first clicked?" — Mutex/lock on cache miss, or pre-warm cache for new URLs
- "How would you scale the ID generator to 100K writes/sec?" — Pre-allocate larger ranges per counter server, reduce Zookeeper coordination frequency
- "How would you implement link previews (OGP metadata)?" — Fetch and cache OGP tags from long URL when shortening; serve preview from cache
- "How would you detect malicious URLs?" — Integrate VirusTotal API on creation, maintain blocklist, add async scanning via Kafka consumer

### Numbers to Remember
- 100M writes/day = 1,200 writes/sec
- 10B reads/day = 116K reads/sec
- 100:1 read-to-write ratio
- 62^7 = 3.5 trillion possible short codes
- 5-year storage: ~27 TB for URL data
- 80/20 rule: 20% of URLs = 80% of traffic
- Cache size for hot 20%: ~2 GB (fits easily in Redis)
- Rate limit: 100 URLs/hour for free tier
