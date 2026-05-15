# Database Caching Patterns

## 1. Concept Overview

Database caching places a fast, in-memory store (typically Redis or Memcached) between the application and the database to reduce database load and lower read latency. A cache hit returns data in ~0.5ms versus ~5–50ms for a database read. At high throughput, effective caching can reduce database read QPS by 90–99%, enabling a single database to serve traffic that would otherwise require 10–100× the compute.

The key engineering decisions: which data to cache, how long to cache it (TTL), when to invalidate it, and which read/write pattern to use. Every caching choice trades consistency for performance.

---

## 2. Intuition

A database is a filing cabinet: reliable, complete, but slow to access. A cache is a sticky-note collection on your desk: fast to access, limited space, and you must remember to update the sticky note when the filing cabinet changes. If you forget to update the sticky note, someone reads stale information. The art of caching is deciding which sticky notes to keep, how long to trust them, and when to throw them away.

---

## 3. Core Principles

**Cache hit rate is the primary metric**: A 90% hit rate means 10% of requests hit the database. A 99% hit rate means 1%. Going from 90% to 99% reduces database load by 10x.

**TTL is the consistency dial**: A shorter TTL keeps data fresher but increases cache miss rate and database load. A longer TTL reduces database load but increases the stale data window.

**Cache invalidation is one of the hardest problems in CS**: Invalidating a cache entry at exactly the right time without missing invalidations or over-invalidating is fundamentally hard. Design for bounded staleness (TTL) rather than exact invalidation when possible.

**Cold cache amplifies failures**: After a deployment, cache flush, or Redis restart, every request is a cache miss. The database receives traffic equivalent to 100× its normal load. Plan for graceful cache warming.

---

## 4. Types / Architectures / Strategies

```
Pattern         | Read Path                  | Write Path            | Consistency
----------------|----------------------------|-----------------------|-------------
Cache-aside     | App checks cache, on miss  | App writes to DB only | Eventual (TTL)
(Lazy loading)  | reads DB, populates cache  |                       |
Read-through    | Cache checks DB on miss    | App writes to DB only | Eventual (TTL)
Write-through   | Cache checks DB on miss    | App writes to both    | Strong (on write)
Write-behind    | Cache checks DB on miss    | App writes to cache,  | Eventual (async)
(Write-back)    |                            | async persist to DB   |
Write-around    | App reads from DB (bypass) | App writes to DB only | N/A (no cache for writes)
```

---

## 5. Architecture Diagrams

```
Cache-Aside (Most Common)
==========================

Read:
  Application → Redis GET(key)
                    │
              ┌─────┴──────┐
           HIT │            │ MISS
              ↓            ↓
         return data    DB query
                            │
                      populate cache
                      SETEX key value TTL
                            │
                       return data

Write:
  Application → DB UPDATE(row)
              → Redis DEL(key)  [or SET new value]
  (invalidate or update cache after DB write)


Write-Through
==============

Write:
  Application → DB UPDATE(row)  [wait for commit]
              → Redis SET(key, new_value, TTL)  [update cache atomically with write]

Risk: if Redis SET fails after DB commit, cache is stale until TTL expires


Write-Behind (Write-Back)
==========================

Write:
  Application → Redis SET(key, value)  [fast, returns immediately]
              ← success
              [async background]
              → DB UPDATE(row)  [persisted later]

Risk: data loss if Redis crashes before async persist completes
Use: write-heavy counters, analytics (not financial data)


Cache Stampede Prevention
==========================

Normal flow (10K requests hit same expired key simultaneously):
  All 10K requests → Redis MISS → All 10K query DB → DB overloaded

Probabilistic Early Expiration:
  Read:
    value, ttl = Redis GET_WITH_TTL(key)
    if ttl < random(0, delta) * -log(random()):
      recalculate and refresh proactively before expiration
    return value

Mutex / Distributed Lock:
  value = Redis GET(key)
  if miss:
    if Redis SET(key:lock, 1, NX, PX=5000):  ← acquire lock
      value = DB_query()
      Redis SET(key, value, TTL)
      Redis DEL(key:lock)
    else:
      sleep(100ms); retry  ← other requests wait
  return value
```

---

## 6. How It Works — Detailed Mechanics

### Cache-Aside (Lazy Loading) in Java

```java
@Service
public class UserService {

    private final RedisTemplate<String, User> redis;
    private final UserRepository db;
    private static final Duration TTL = Duration.ofMinutes(30);

    public User getUser(long userId) {
        String key = "user:" + userId;

        // 1. Check cache
        User cached = redis.opsForValue().get(key);
        if (cached != null) {
            return cached;  // cache hit: ~0.5ms
        }

        // 2. Cache miss: query DB
        User user = db.findById(userId)
            .orElseThrow(() -> new NotFoundException("User " + userId));

        // 3. Populate cache
        redis.opsForValue().set(key, user, TTL);  // expires in 30 minutes
        return user;
    }

    public void updateUser(long userId, UpdateUserRequest req) {
        // 1. Update DB (source of truth)
        User updated = db.save(/* ... */);

        // 2. Invalidate or update cache
        String key = "user:" + userId;
        redis.delete(key);  // invalidate; next read populates fresh
        // Alternative: redis.opsForValue().set(key, updated, TTL); // update
    }
}
```

**Delete vs update on write**: Deleting the cache key is safer than updating it because update requires the new value to be computed correctly (risk: stale data from a race if two concurrent updates write different values). Delete ensures the next read fetches fresh data from DB. Update avoids one DB round-trip but introduces a race window.

### Write-Through

```java
@Transactional
public void updateProduct(long productId, ProductUpdate req) {
    // 1. Write to DB (atomic)
    Product product = productRepository.findById(productId)
        .orElseThrow();
    product.apply(req);
    productRepository.save(product);

    // 2. Update cache synchronously (before returning success)
    String key = "product:" + productId;
    redis.opsForValue().set(key, product, Duration.ofHours(1));
}
// Risk: if Redis fails, the DB has the new data but cache is stale/old
// Fix: use cache.delete() as fallback if SET fails (degraded but safe)
```

### Write-Behind Pattern

```java
// Write immediately to cache
public void incrementPageView(long articleId) {
    String key = "pageviews:" + articleId;
    redis.opsForValue().increment(key);
    // No DB write here — fast path
}

// Background job persists to DB every 30 seconds
@Scheduled(fixedDelay = 30_000)
public void flushPageViews() {
    Set<String> keys = redis.keys("pageviews:*");
    for (String key : keys) {
        Long views = redis.opsForValue().get(key);
        if (views != null) {
            long articleId = extractId(key);
            db.updatePageViews(articleId, views);
            // Note: do NOT delete the key — counter continues accumulating
            // Alternatively: use GETSET to atomically read and reset
        }
    }
}
// Risk: Redis crash between increments and flush = lost view counts
// Acceptable for analytics; NOT acceptable for financial counters
```

### Cache Stampede — The Thundering Herd

A cache stampede occurs when a popular cache entry expires and many concurrent requests simultaneously miss the cache, all query the database, and all try to repopulate the cache at the same time.

```
Without protection:
  10K req/s to a popular page
  Page TTL expires
  → 10K requests simultaneously hit DB (normal DB load = 100 req/s)
  → DB overwhelmed → cascade failure

Protection option 1: Mutex lock
  First request acquires Redis lock, queries DB, repopulates cache, releases lock
  Other requests wait (with sleep+retry) → serialize DB access to 1 request
  Drawback: waiting requests see increased latency during lock hold

Protection option 2: Background refresh
  Monitor TTL proactively:
    if remaining_ttl < 20% of original TTL:
      trigger background refresh (non-blocking)
      serve stale value to current request
  Result: cache is refreshed before expiration; no stampede, no stale serving

Protection option 3: Add jitter to TTL
  TTL = base_ttl + random(0, base_ttl * 0.2)
  Example: base=3600s → actual TTL between 3600s and 4320s
  Prevents all entries set at the same time from expiring simultaneously
  Most effective for bulk cache loads (e.g., after a cache flush)
```

### Hot Key Problem

A hot key is a cache key accessed at a rate far exceeding what a single Redis node can handle (typically > 100K ops/second per key).

```
Example: "trending:top10" accessed 500K times/second
Single Redis node: ~100K ops/second limit for one key
Result: Redis node CPU-bound, latency spikes for all keys on that node

Solutions:
1. Local in-process cache (L1):
   Caffeine cache with 100ms TTL for hot keys
   Application first checks Caffeine, then Redis
   10K RPS × 100 application instances → 1M Caffeine hits, 10K Redis hits
   Trade-off: 100ms stale window; memory per JVM instance

2. Key replication (read fan-out):
   Store key as: "trending:top10:0" through "trending:top10:9"
   Random shard selection on read: key + rand(0, 9)
   Writes update all 10 shards
   Trade-off: 10× storage; write complexity; temporary inconsistency across shards

3. Redis Cluster read from replicas:
   redis.opsForValue().get(key, ReadFrom.REPLICA_PREFERRED)
   Distributes reads across primary + replicas
   Trade-off: slight replication lag (async)
```

### Cache Invalidation Strategies

```
Strategy              | Mechanism                           | Tradeoff
----------------------|-------------------------------------|----------------------------
TTL (time-based)      | Entry expires after N seconds       | Simple; bounded staleness
Event-driven          | Write event triggers cache DEL       | Low latency; complex routing
CDC-based             | DB change → Debezium → cache DEL    | Accurate; infrastructure cost
Version-based keys    | key = "user:42:v7" (include version)| No invalidation needed; old
                      | Version in DB, incremented on write | versions naturally expire
Two-level (L1+L2)     | Caffeine (local) + Redis (shared)   | High hit rate; stale risk
```

**Version-based key approach**:
```java
// DB stores: user_version alongside user data
public User getUser(long userId) {
    // 1. Get current version (lightweight — cached separately or from DB)
    int version = versionCache.get(userId); // short TTL

    String key = "user:" + userId + ":v" + version;
    User cached = redis.opsForValue().get(key);
    if (cached != null) return cached;

    User user = db.findById(userId).orElseThrow();
    redis.opsForValue().set("user:" + userId + ":v" + user.getVersion(), user, TTL);
    return user;
}

public void updateUser(long userId, UpdateUserRequest req) {
    // DB: UPDATE users SET ... , version = version + 1 WHERE id = userId
    db.updateWithVersionIncrement(userId, req);
    // Old cache keys become unreachable (version changed) and expire via TTL
    // No explicit delete needed
}
```

### CDN as Cache Tier

For read-heavy content (product pages, static assets, API responses for public data), CDN functions as a distributed HTTP cache.

```
Cache-Control headers:
  Cache-Control: public, max-age=3600, stale-while-revalidate=60
  → CDN caches for 1 hour; after expiry, serves stale for 60s while revalidating

  Cache-Control: private, no-store
  → Not cached by CDN; appropriate for user-specific data

ETag / If-None-Match:
  Server: ETag: "abc123"  (hash of content)
  Client: If-None-Match: "abc123"
  Server: 304 Not Modified  → CDN serves cached version, no bandwidth used

CDN purge API:
  On product price update:
    curl -X POST "https://api.cloudflare.com/zones/{id}/purge_cache"
          -d '{"files":["https://example.com/products/42"]}'
```

---

## 7. Real-World Examples

**Facebook**: TAO (The Associations and Objects) cache is a write-through cache of the social graph. Over 1 billion reads per second with ~99% cache hit rate. Write-through ensures cache is always consistent with the database on writes.

**Twitter**: Used a multi-level cache: Memcached for tweet objects, Redis for timelines (sorted sets), and CDN for media. Timeline cache precomputed fan-out at write time for users with < 10K followers.

**Stack Overflow**: Relies on SQL Server in-memory OLTP tables + application-layer cache (Redis/Memcached). 99.9% of traffic served from cache; database handles only cache misses and writes.

**Shopify**: Uses Redis with cache-aside pattern. Every cache key has TTL-based expiration. Critical data (product prices, inventory) uses shorter TTL (60 seconds); static data (product descriptions) uses longer TTL (1 hour).

---

## 8. Tradeoffs

```
Pattern          | Consistency     | Write Latency | Read Latency  | Complexity
-----------------|-----------------|---------------|---------------|------------
Cache-aside      | Eventual (TTL)  | DB only       | Cache + DB on miss | Low
Read-through     | Eventual (TTL)  | DB only       | Cache + DB on miss | Medium
Write-through    | Strong on write | Cache + DB    | Cache only    | Medium
Write-behind     | Eventual        | Cache only    | Cache only    | High
Write-around     | N/A             | DB only       | DB only       | Low
```

---

## 9. When to Use / When NOT to Use

**Use caching for**: Read-heavy data with tolerable staleness (user profiles, product catalogs, configuration). Computationally expensive queries whose results change infrequently. Rate limiting (Redis counters). Session state.

**Avoid caching for**: Data requiring strict freshness (financial balances, real-time inventory for checkout). Data that is never read twice (unique per-request). Very large objects that don't fit in a cache tier cost-effectively. Data where cache inconsistency causes correctness bugs that the business cannot tolerate.

**Cache-aside vs write-through**: Cache-aside is simpler; the cache is populated on demand. Write-through ensures the cache is always warm after writes. Choose write-through when read latency on cache misses is unacceptable (e.g., initial user session load must be < 50ms).

---

## 10. Common Pitfalls

**Cache stampede on Black Friday**: A popular product page cached for 5 minutes. At midnight, the TTL was set identically for all products loaded in one batch. At exactly 00:05, all 1 million product cache entries expire simultaneously. 50K requests/second all miss cache and query the database. Database saturates. Fix: TTL jitter (`base_ttl + random(0, base_ttl * 0.2)`) prevents synchronized expiration.

**Thundering herd after Redis restart**: Redis is restarted for maintenance. All 10M cache keys are gone. Application traffic continues at full rate. Every request hits the database. Database saturates within seconds. Fix: pre-warm cache before cutting traffic back; use blue-green Redis with data migration; implement circuit breaker on DB if Redis is unavailable.

**Write-behind losing financial data**: Team uses write-behind (async persist) for order counts. Redis server crashes before the 30-second flush. 3 minutes of order count increments are lost. Financial reports are incorrect. Fix: write-behind is only appropriate for data where loss is acceptable (view counts, non-critical analytics). Financial data must use write-through or cache-aside with synchronous DB writes.

**Hot key causing Redis node saturation**: A "flash sale active" flag is checked on every request (100K/second). All requests go to one Redis key on one node. That node hits 100% CPU. Other keys on that node also slow down. Fix: cache the flag locally in the application process (Caffeine/Guava cache) with a 100ms TTL; check Redis only on miss. This reduces Redis reads for that key by 99%.

**Cache invalidation race condition**: Application reads user from DB, starts writing to cache. Simultaneously, another request updates the user in DB and deletes the cache key. The first request finishes and writes the OLD user to cache (the delete happened before the write). Cache now has stale data until TTL. Fix: use `SET NX` (set only if not exists) when repopulating after a delete, or use version-based keys.

---

## 11. Technologies & Tools

| Tool          | Purpose                          | Key Feature                    |
|---------------|----------------------------------|--------------------------------|
| Redis         | Primary cache store              | Rich data structures, Cluster  |
| Memcached     | Simple string cache              | Multi-threaded, low overhead   |
| Caffeine      | JVM in-process cache (L1)        | W-TinyLFU algorithm, high hit  |
| Ehcache       | JVM in-process cache             | JCache (JSR-107) compatible    |
| Varnish       | HTTP reverse proxy cache         | VCL configuration language     |
| Nginx         | HTTP caching + proxy             | proxy_cache, fastcgi_cache     |
| CloudFront    | CDN for HTTP responses           | Global edge caching            |
| Hazelcast     | Distributed in-memory cache grid | Near-cache, partition-aware    |

---

## 12. Interview Questions with Answers

**What is a cache stampede and how do you prevent it?**
A cache stampede (thundering herd) occurs when a popular cache entry expires and many concurrent requests simultaneously miss the cache, all query the database at once. With 10K requests/second hitting the database instead of 100 (normal non-cached load), the database saturates. Prevention strategies: (1) Mutex lock: only one request queries the database when a key is missing; others wait and retry. (2) Background refresh: detect entries nearing expiration and refresh before they expire, serving stale data to current requests. (3) TTL jitter: add random variance to TTL so bulk-loaded entries don't all expire at the same time. (4) Local L1 cache: a short-TTL in-process cache means cache misses are rare even when Redis is unavailable.

**When would you use write-behind caching and what are the durability risks?**
Write-behind (write-back) caches writes in Redis and asynchronously persists them to the database. Use it when write throughput far exceeds database capacity and the data can tolerate loss — view counts, like counts, non-financial analytics counters. The durability risk: if Redis crashes before the async persist completes, all unsynced writes are lost. For a 30-second flush interval, up to 30 seconds of data is at risk. Never use write-behind for financial transactions, inventory counts that affect checkout, or any data where loss causes business or compliance issues.

**How do you invalidate cache entries in a microservices architecture where multiple services write to the same data?**
Options: (1) Event-driven invalidation: the service that owns the data publishes a change event to a message bus (Kafka); all services with cached copies subscribe and invalidate. (2) CDC-based invalidation: Debezium tails the database WAL, detects row changes, publishes invalidation events to Kafka; a cache invalidation service consumes events and deletes keys. (3) TTL-only: accept bounded staleness (e.g., 60-second TTL) and rely on TTL expiration. (4) Version-based keys: the DB version column is part of the cache key; old versions expire naturally. Event-driven is most accurate but requires reliable message delivery and idempotent consumers. TTL-only is simplest and handles most cases.

**Explain the difference between cache-aside and read-through caching.**
Cache-aside: the application manages the cache explicitly. On a read miss, the application queries the database, then populates the cache. On a write, the application updates the database and optionally invalidates/updates the cache. The cache is populated on demand. Read-through: the cache layer transparently queries the database on a miss, returning the result and caching it. The application interacts only with the cache interface. Difference: cache-aside gives the application full control (useful for complex caching logic or non-standard data types); read-through is simpler for the application (implemented by frameworks like Spring Cache, Caffeine LoadingCache) but requires the cache layer to know how to query the database.

**What is the hot key problem in Redis and how do you solve it?**
A hot key is a single Redis key receiving more traffic than a single Redis node can handle (typically > 100K ops/second). Since Redis keys are pinned to specific nodes in a cluster, one node becomes a CPU bottleneck regardless of cluster size. Solutions: (1) Local in-process cache (Caffeine with 100–500ms TTL): application checks in-process cache before Redis; reduces Redis access rate by 99% for hot keys. (2) Key sharding: replicate the hot key across N Redis keys (e.g., trending:1 through trending:10), read from a randomly chosen shard, write to all. (3) Read from Redis replicas: use `ReadFrom.REPLICA_PREFERRED` to distribute reads across primary and replicas. (4) Use Redis Cluster's read-from-replica mode.

**How does the write-through pattern ensure cache consistency?**
Write-through updates both the database and the cache synchronously during each write, ensuring the cache always reflects the current database state for any key that was previously cached. If the write to the database succeeds but the cache update fails, the cache entry should be explicitly deleted (fallback) to prevent stale data. The benefit: any key in the cache is guaranteed to be current as of the last write. The cost: every write pays the latency of updating both the database and cache; writes are slower than pure cache-aside (which only writes to the database). Most appropriate when cache misses are expensive and reads far outnumber writes.

**How do you handle cache warming after a Redis restart?**
Strategies: (1) Lazy warming: let the cache fill naturally from cache misses. Use a circuit breaker on the database to shed load while the cache warms. (2) Pre-warming script: before cutting traffic over, run a script that reads frequently accessed keys from the database and populates the cache. Identify hot keys from historical access logs. (3) Redis persistence: configure RDB or AOF so Redis restores its state from disk on restart — no warming needed for data that was cached before shutdown. (4) Blue-green cache: maintain a second Redis instance, gradually shift traffic while the new instance warms from the primary's replication stream. (5) Staggered deployment: deploy to a subset of servers, let them warm the cache, then expand.

**What metrics indicate caching is working and when it is degrading?**
Primary metric: cache hit rate = hits / (hits + misses). Target: ≥ 95% for frequently accessed data. Alert if it drops below 90%. Secondary metrics: (1) Cache eviction rate — high evictions (from Redis INFO: evicted_keys) indicate cache is undersized. (2) Average cache miss latency — a spike indicates the database is slow on cache misses. (3) Key TTL distribution — if most keys have very short TTL, they expire before being accessed, contributing to low hit rate. (4) Memory usage vs maxmemory — if approaching 90%, add capacity or reduce TTL. (5) Per-key access frequency (Redis --hotkeys flag) — identify hot keys for dedicated treatment.

**What is the stale-while-revalidate CDN pattern?**
`stale-while-revalidate` is an HTTP Cache-Control directive that tells CDN edge nodes: serve the stale (expired) cached version immediately to the current request while simultaneously revalidating (fetching a fresh copy) in the background. This eliminates the latency spike that occurs when an entry expires and the CDN must wait for the origin server to respond before serving the request. The syntax: `Cache-Control: max-age=3600, stale-while-revalidate=60` means: fresh for 1 hour; after expiry, serve stale for up to 60 more seconds while revalidating. The user always gets a fast response; the stale-serving window is bounded to 60 seconds.

**How does Spring Cache abstraction simplify caching?**
Spring Cache (`@Cacheable`, `@CachePut`, `@CacheEvict`) provides declarative caching as an AOP-based abstraction. Annotate methods; Spring intercepts calls, checks the cache, and either returns cached results or calls the method and caches the result. The backing store (Redis, Caffeine, EhCache) is swappable via `CacheManager` configuration.

```java
@Cacheable(value = "products", key = "#productId", unless = "#result == null")
public Product getProduct(long productId) {
    return productRepository.findById(productId).orElse(null);
}

@CacheEvict(value = "products", key = "#product.id")
public void updateProduct(Product product) {
    productRepository.save(product);
}

@CachePut(value = "products", key = "#result.id")
public Product createProduct(CreateProductRequest req) {
    return productRepository.save(new Product(req));
}
```
Limitation: Spring Cache does not handle distributed stampede prevention, TTL per-entry, or cache-aside logic for complex multi-key operations.

**What is the N+1 caching problem and how do you fix it?**
The N+1 caching problem occurs when an application fetches N entity IDs and then makes N individual cache lookups (one per ID). With 100 entities, this is 100 separate Redis round trips (N×RTT). Fix: use `MGET` (Redis multi-get) to fetch all N keys in a single round trip. The cache miss handling: for missing keys, query the database in a single `WHERE id IN (...)` query (not N individual queries). Then populate all N missing keys with a pipeline of SET commands.

```java
public List<User> getUsers(List<Long> userIds) {
    List<String> keys = userIds.stream().map(id -> "user:" + id).toList();

    // Single MGET: one network round trip for all N keys
    List<User> cached = redis.opsForValue().multiGet(keys);

    // Find which IDs had cache misses
    List<Long> missingIds = new ArrayList<>();
    for (int i = 0; i < userIds.size(); i++) {
        if (cached.get(i) == null) missingIds.add(userIds.get(i));
    }

    if (!missingIds.isEmpty()) {
        // Single DB query for all missing IDs
        List<User> dbUsers = userRepository.findAllById(missingIds);

        // Populate cache for misses (pipeline: single round trip)
        Map<String, User> toCache = dbUsers.stream()
            .collect(toMap(u -> "user:" + u.getId(), identity()));
        redis.opsForValue().multiSet(toCache);

        // Merge DB results into cached list
        Map<Long, User> dbMap = dbUsers.stream().collect(toMap(User::getId, identity()));
        for (int i = 0; i < userIds.size(); i++) {
            if (cached.get(i) == null) {
                cached.set(i, dbMap.get(userIds.get(i)));
            }
        }
    }
    return cached;
}
```

**How do you prevent cache-related security issues (cache poisoning)?**
Cache poisoning: a malicious user causes an incorrect response to be cached and served to other users. Prevention: (1) Never cache responses that vary per-user or include authorization — use `Cache-Control: private` or `Vary: Authorization`. (2) Validate all cache keys: if the cache key contains user input, sanitize it to prevent key collision between users. (3) Use separate cache namespaces per tenant in multi-tenant systems: key prefix = `tenant:{tenant_id}:user:{user_id}`. (4) For CDN: validate `X-Forwarded-Host` and `X-Forwarded-For` headers before using them in cache keys — these can be spoofed to poison other users' caches.

**How does two-level caching (L1 + L2) work?**
L1 (local in-process cache, e.g., Caffeine) is checked first. L2 (shared distributed cache, e.g., Redis) is checked on L1 miss. Database is queried only on L2 miss.

```
Read:
  App → Caffeine (L1): hit → return (0.1ms)
       → Redis (L2): hit → populate Caffeine, return (0.5ms)
       → DB: query, populate Redis, populate Caffeine, return (10ms)

L1 TTL: 30–100 seconds (short — local memory, bounded staleness)
L2 TTL: 5–30 minutes (longer — shared, lower miss rate)
```

L1 caches ultra-hot data locally, reducing Redis network traffic by 90%+ for the hottest keys. Tradeoff: L1 entries on different application instances may be stale relative to each other for up to the L1 TTL. On L2 invalidation (explicit delete), L1 entries continue serving stale data until their own TTL expires. Acceptable for configuration data and slowly changing reference data; not acceptable for user-facing profile data that must reflect writes quickly.

---

## 13. Best Practices

- **Monitor cache hit rate continuously** and alert on drops below 90% — a silent hit rate drop means the database absorbs unexpected load.
- **Set maxmemory and an appropriate eviction policy** (allkeys-lfu for general caches) before production; never run without memory limits.
- **Add TTL jitter** to all bulk-loaded cache entries to prevent synchronized expiration.
- **Use MGET/pipeline** for bulk cache operations — individual round trips for N keys cost N×RTT.
- **Cache at the right granularity** — cache full objects rather than individual fields; avoid partial object caching which leads to inconsistency.
- **Implement circuit breaker** on the database layer for cache miss path — if the database is slow, don't let cache misses cascade into database overload.
- **Test cache failure modes** — disable Redis and verify the application degrades gracefully (slower, not broken).
- **Never cache unbounded results** — cache paginated results, not full table scans; set reasonable value size limits.

---

## 14. Case Study

**Scenario**: An e-commerce platform with 2M daily active users serves product detail pages at 50K requests/second. Each page requires: product data (DB), inventory count (DB), pricing data (DB), and user-specific discount (DB with user_id). Database handles 8K queries/second at 70% CPU utilization. A seasonal event will triple traffic to 150K requests/second in 2 days.

**Before caching**: 4 DB queries per page × 50K req/s = 200K DB queries/second. Impossible.

**Caching design**:

```
Layer 1: Caffeine (JVM in-process, 500ms TTL)
  Keys: product:{id}, pricing:{product_id}:{user_segment}
  Hit rate target: 80% (ultra-hot products)
  Benefit: 0.05ms lookup, no network call

Layer 2: Redis (shared, 5-minute TTL)
  Keys: product:{id}, pricing:{product_id}:{user_segment}, inventory:{product_id}
  Hit rate target: 95%
  Note: user-specific discounts NOT cached here (vary per user; use Caffeine)

Layer 3: PostgreSQL (cache miss path only)
  Expected QPS after caching: ~2,500 (5% miss rate × 50K req/s)
```

**Cache invalidation**:
- Product data updated: outbox event → Debezium → Kafka → cache invalidation service deletes Redis key
- Pricing updated: delete Redis key; Caffeine entries expire within 500ms
- Inventory updated: delete Redis key; show 60-second staleness for inventory (acceptable)

**Results at 50K req/s**:
- Caffeine hit rate: 82% (41K requests served from JVM)
- Redis hit rate: 94% of remaining 9K (8.5K from Redis)
- DB QPS: 500 (from 200K theoretical; 99.75% reduction)
- DB CPU: 8% (from 70%)

**At 150K req/s (3× event)**:
- Caffeine: 123K requests
- Redis: 24.3K requests
- DB QPS: 1,500 (well within capacity)
- Action: increase Redis memory from 16GB to 32GB for larger working set; no DB scaling needed
