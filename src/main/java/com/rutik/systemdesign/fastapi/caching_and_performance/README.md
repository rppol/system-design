# Caching and Performance

---

## 1. Concept Overview

Caching is the practice of storing the result of an expensive computation or I/O operation so
that future requests for the same data can be served faster. In Python and FastAPI services,
caching operates at multiple levels — inside the process (in-memory), across processes
(distributed cache), and at the HTTP protocol layer (browser and CDN caches).

Performance in FastAPI extends beyond caching to include serialization speed, connection pool
management, response payload shaping, and avoiding common async pitfalls that waste CPU and
memory. A service that caches correctly but serializes slowly, or one that exhausts its Redis
connection pool under load, degrades just as severely as an uncached service.

This module covers:

- In-process caching: `functools.lru_cache`, `cachetools.TTLCache`, `cachetools.LRUCache`
- Distributed caching with `redis.asyncio`: connection pools, pipelines, Lua scripts
- Cache key design: namespacing, versioning, collision avoidance
- TTL strategy: short vs long TTL; event-based invalidation
- Redis write patterns: cache-aside, write-through, write-behind, read-through
- Cache stampede / thundering herd: mutex lock (`SET NX EX`), probabilistic early expiry
- Decorator-based caching: `fastapi-cache2`, `cashews`
- HTTP caching: `ETag`, `Last-Modified`, `Cache-Control`, `Vary`
- Serialization performance: `orjson`, `ujson`, `exclude_unset=True`
- Connection pool sizing and Redis max connections

---

## 2. Intuition

> A cache is a short-term memory that trades freshness for speed — the closer memory is to the
> CPU (or the user), the faster it reads but the staler it can become.

**Mental model:** Think of caches as a hierarchy of assistants. Your in-process cache is a
sticky note on your desk — instant to read, lost when you leave. Redis is a shared whiteboard
in the office — a few milliseconds away but survives your departure. The HTTP cache in the
browser is the user's local copy — zero network cost but possibly days old.

**Why it matters:** A single database call that takes 20ms can be served from Redis in 0.3ms.
A CPU-bound computation taking 50ms can be served from `lru_cache` in under 1 microsecond.
At 1,000 RPS those differences compound: 20ms × 1,000 = 20 seconds of cumulative DB load per
second eliminated entirely.

**Key insight:** Caching never eliminates stale reads — it trades consistency for latency. The
engineering decision is always: what staleness can this data tolerate, and what is the cost of
a cache miss?

---

## 3. Core Principles

1. **Cache what is expensive and read frequently.** Writing to cache adds overhead; the
   benefit only materializes when the cache hit rate is high enough to justify it.

2. **Explicit invalidation beats TTL alone.** TTL is a safety net, not a primary strategy.
   Where possible, delete or update the cache entry when the underlying data changes.

3. **Key design is a contract.** A poorly designed key that two callers compute differently
   causes silent miss-or-collision bugs. Keys must be deterministic and fully qualified.

4. **In-process caches are not shared.** With multiple worker processes (Gunicorn + Uvicorn),
   each process has its own `lru_cache`. Updates in process A are invisible to process B until
   the TTL expires or the cache is invalidated — which never happens across processes without
   an external signal.

5. **Stampede protection is mandatory for high-traffic keys.** When a popular cache entry
   expires, hundreds of concurrent requests may simultaneously attempt to recompute it,
   hammering the database.

6. **Measure before caching.** Profile with `cProfile` or `py-spy` to identify actual hot
   spots. Caching the wrong function wastes memory without improving latency.

---

## 4. Types / Architectures / Strategies

### In-Process Caches

| Type | Module | TTL Support | Bounded | Thread-Safe | Async-Safe |
|------|--------|-------------|---------|-------------|------------|
| LRU eviction | `functools.lru_cache` | No | Yes | Yes (GIL) | No (caches coroutine) |
| LRU eviction | `cachetools.LRUCache` | No | Yes | No (use RLock) | Manual |
| TTL eviction | `cachetools.TTLCache` | Yes | Yes | No (use RLock) | Manual |
| TTL eviction | `aiocache.SimpleMemoryCache` | Yes | No | Yes | Yes |

`lru_cache` wraps a synchronous callable and stores its return value. Wrapping an `async def`
function with `lru_cache` caches the coroutine object, not the awaited result — a critical
pitfall (see Section 10).

### Distributed Cache (Redis)

Redis is the de-facto distributed cache for Python web applications. The `redis.asyncio` client
(formerly `aioredis`) provides a non-blocking interface compatible with FastAPI's event loop.

**Redis write patterns:**

- **Cache-aside (lazy loading):** Application checks cache first; on miss, loads from DB and
  populates cache. Cache population is lazy — only entries that are actually requested get
  cached. Stale data is possible between writes and TTL expiry.

- **Write-through:** On every write to the database, also write to the cache. Reads always hit
  cache (after first write). Adds write latency; cache may hold entries that are never read.

- **Write-behind (write-back):** Write to cache immediately; flush to DB asynchronously.
  Lowest write latency; risk of data loss if cache crashes before flush.

- **Read-through:** Cache sits in front of the database; on miss, the cache itself loads from
  DB (not the application). Requires a cache layer that understands the data model (e.g.,
  custom Redis functions or Momento).

For FastAPI services, cache-aside is the most common pattern because it keeps the application
in control of cache population and invalidation.

### HTTP Caching

HTTP caching is free performance: the browser or CDN stores the response and does not make a
network request at all for subsequent identical requests.

- `Cache-Control: max-age=60` — cache for 60 seconds
- `ETag: "abc123"` + `If-None-Match: "abc123"` — conditional request; server returns 304 if
  unchanged (no body transfer)
- `Last-Modified: <date>` + `If-Modified-Since: <date>` — time-based conditional request
- `Vary: Accept-Language` — cache separately per language header

FastAPI does not set `Cache-Control` by default. You must add it explicitly via response
headers or a middleware.

---

## 5. Architecture Diagrams

### Cache Hierarchy in a FastAPI Service

```
 Client (browser / mobile)
    │
    │  HTTP Cache-Control / ETag
    ▼
 CDN / Reverse Proxy (nginx, CloudFront)
    │
    │  cache HIT → return cached response
    │  cache MISS ↓
    ▼
 FastAPI Worker Process
    │
    │  In-Process Check (lru_cache / TTLCache)
    │  HIT → return immediately (sub-microsecond)
    │  MISS ↓
    ▼
 Redis (distributed cache)
    │
    │  HIT → return, update in-process cache
    │  MISS ↓
    ▼
 Database / External API
    │
    └──→ Store result in Redis (SETEX key value ttl)
         Store result in in-process cache
         Return to client
```

### Cache-Aside Pattern (Request Flow)

```
 Request ──► Check Redis ──► HIT ──────────────────────► Response
                │
                └── MISS ──► Query DB ──► SETEX key val ttl ──► Response
```

### Stampede Protection with Mutex Lock

```
 T=0ms  Key expires
 T=1ms  Request A: cache miss → acquires Redis lock (SET lock:key 1 NX EX 5)
 T=1ms  Request B: cache miss → lock held → returns stale value or waits
 T=1ms  Request C: cache miss → lock held → returns stale value or waits
 T=3ms  Request A: query DB → SETEX key val 300 → release lock
 T=3ms  Request B/C: re-check cache → HIT → serve immediately
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Redis Connection Pool with `redis.asyncio`

```python
# app/cache.py
import redis.asyncio as redis
from redis.asyncio import ConnectionPool

_pool: ConnectionPool | None = None

def get_pool() -> ConnectionPool:
    global _pool
    if _pool is None:
        _pool = ConnectionPool.from_url(
            "redis://localhost:6379/0",
            max_connections=20,       # tune based on worker count × expected concurrency
            decode_responses=True,    # return str, not bytes
            socket_connect_timeout=1, # fail fast on connection timeout
            socket_timeout=1,
        )
    return _pool

def get_redis() -> redis.Redis:
    return redis.Redis(connection_pool=get_pool())
```

**Why `decode_responses=True`?** Without it, Redis returns `bytes`. Every key lookup requires
explicit `.decode("utf-8")`. `decode_responses=True` handles this automatically, eliminating
a common source of `AttributeError` when comparing keys.

**Pool sizing rule of thumb:** `max_connections = num_workers × max_concurrent_cache_ops`.
For 4 Uvicorn workers each handling up to 100 concurrent requests with 5% making Redis calls
simultaneously: `4 × 100 × 0.05 = 20` connections.

### 6.2 Basic Cache-Aside Pattern

```python
import json
import redis.asyncio as redis
from app.cache import get_redis
from app.db import get_user_from_db

async def get_user(user_id: int) -> dict:
    r = get_redis()
    key = f"v1:user:{user_id}:profile"

    cached = await r.get(key)
    if cached is not None:
        return json.loads(cached)

    user = await get_user_from_db(user_id)
    await r.setex(key, 300, json.dumps(user))  # TTL = 300 seconds
    return user
```

**Key design breakdown:**

- `v1:` — schema version prefix; bump to `v2:` when user profile shape changes instead of
  running a cache flush operation across all keys
- `user:` — entity namespace; prevents collision with `product:{id}:profile`
- `{user_id}:` — primary key
- `profile` — sub-resource; allows `v1:user:{id}:settings` to coexist

### 6.3 Stampede Protection: Mutex Lock Pattern

```python
import asyncio
import json
import redis.asyncio as redis

LOCK_TIMEOUT = 5      # seconds the lock is held maximum
WAIT_INTERVAL = 0.05  # seconds to sleep while waiting for lock

async def get_user_with_lock(user_id: int) -> dict:
    r = get_redis()
    key = f"v1:user:{user_id}:profile"
    lock_key = f"lock:{key}"

    cached = await r.get(key)
    if cached is not None:
        return json.loads(cached)

    # Attempt to acquire lock: SET lock_key 1 NX EX 5
    acquired = await r.set(lock_key, "1", nx=True, ex=LOCK_TIMEOUT)

    if acquired:
        try:
            user = await get_user_from_db(user_id)
            await r.setex(key, 300, json.dumps(user))
            return user
        finally:
            await r.delete(lock_key)
    else:
        # Another worker is computing; wait and re-check
        for _ in range(int(LOCK_TIMEOUT / WAIT_INTERVAL)):
            await asyncio.sleep(WAIT_INTERVAL)
            cached = await r.get(key)
            if cached is not None:
                return json.loads(cached)
        # Lock expired without a result; fall through to DB
        return await get_user_from_db(user_id)
```

`SET key value NX EX seconds` is atomic in Redis. `NX` means "set only if Not eXists". This
ensures exactly one caller acquires the lock per cache miss event.

### 6.4 In-Process TTL Cache (Thread/Async Safe)

```python
import asyncio
from cachetools import TTLCache

_user_cache: TTLCache[int, dict] = TTLCache(maxsize=1024, ttl=60)
_cache_lock = asyncio.Lock()

async def get_user_config(user_id: int) -> dict:
    if user_id in _user_cache:
        return _user_cache[user_id]
    async with _cache_lock:
        # Double-check: another coroutine may have populated while we waited
        if user_id in _user_cache:
            return _user_cache[user_id]
        result = await db.fetch_user_config(user_id)
        _user_cache[user_id] = result
        return result
```

`asyncio.Lock` is correct here because FastAPI runs on a single-threaded event loop.
`threading.Lock` would block the event loop; `asyncio.Lock` yields control while waiting.

### 6.5 Redis Pipeline for Batch Operations

```python
async def get_multiple_users(user_ids: list[int]) -> dict[int, dict | None]:
    r = get_redis()
    keys = [f"v1:user:{uid}:profile" for uid in user_ids]

    async with r.pipeline(transaction=False) as pipe:
        for key in keys:
            pipe.get(key)
        results = await pipe.execute()

    return {
        uid: json.loads(val) if val else None
        for uid, val in zip(user_ids, results)
    }
```

Pipelines send all commands in one network round trip instead of N round trips. For 100 keys
this reduces Redis latency from `100 × 0.3ms = 30ms` to roughly `0.5ms`.

### 6.6 `fastapi-cache2` Decorator Pattern

```python
from fastapi import FastAPI
from fastapi_cache import FastAPICache
from fastapi_cache.backends.redis import RedisBackend
from fastapi_cache.decorator import cache
import redis.asyncio as redis

app = FastAPI()

@app.on_event("startup")
async def startup():
    r = redis.from_url("redis://localhost:6379", decode_responses=True)
    FastAPICache.init(RedisBackend(r), prefix="fastapi-cache:")

@app.get("/users/{user_id}")
@cache(expire=300)
async def get_user(user_id: int) -> dict:
    return await db.fetch_user(user_id)
```

`fastapi-cache2` automatically builds cache keys from the function name and arguments.
The `expire` parameter sets the TTL in seconds. Cache invalidation requires calling
`FastAPICache.clear()` or deleting the key directly from Redis.

### 6.7 HTTP Caching Headers in FastAPI

```python
from fastapi import Response
from fastapi.responses import JSONResponse
import hashlib, json

@app.get("/products/{product_id}")
async def get_product(product_id: int, response: Response) -> dict:
    product = await db.fetch_product(product_id)
    body = json.dumps(product, sort_keys=True)
    etag = hashlib.md5(body.encode()).hexdigest()

    response.headers["Cache-Control"] = "public, max-age=60"
    response.headers["ETag"] = f'"{etag}"'
    return product
```

For conditional requests, check `If-None-Match` and return 304 when the ETag matches:

```python
from fastapi import Request

@app.get("/products/{product_id}")
async def get_product(product_id: int, request: Request, response: Response) -> dict:
    product = await db.fetch_product(product_id)
    body = json.dumps(product, sort_keys=True)
    etag = f'"{hashlib.md5(body.encode()).hexdigest()}"'

    if request.headers.get("if-none-match") == etag:
        return Response(status_code=304)

    response.headers["Cache-Control"] = "public, max-age=60"
    response.headers["ETag"] = etag
    return product
```

### 6.8 Serialization Performance with `orjson`

```python
# requirements: orjson>=3.9.0
import orjson
from fastapi.responses import ORJSONResponse
from fastapi import FastAPI

app = FastAPI(default_response_class=ORJSONResponse)

# Per-endpoint override:
@app.get("/large-dataset", response_class=ORJSONResponse)
async def large_dataset() -> list[dict]:
    return await db.fetch_large_dataset()
```

`orjson` is implemented in Rust and serializes Python objects 3–5x faster than the stdlib
`json` module. For a 10KB JSON payload, `json.dumps` takes approximately 120µs while
`orjson.dumps` takes approximately 30µs. The benefit scales linearly with payload size:
a 1MB response saves ~90ms of pure serialization time per request.

Setting `default_response_class=ORJSONResponse` on the `FastAPI` constructor applies `orjson`
to all routes without per-endpoint changes.

### 6.9 `exclude_unset=True` to Reduce Serialization Cost

```python
from pydantic import BaseModel
from fastapi import FastAPI

class UserProfile(BaseModel):
    id: int
    name: str
    email: str
    bio: str | None = None
    avatar_url: str | None = None
    preferences: dict | None = None

@app.patch("/users/{user_id}")
async def update_user(user_id: int, update: UserProfile) -> dict:
    # Without exclude_unset, all None fields are serialized:
    # {"id": 1, "name": "Alice", "email": "a@b.com", "bio": null, "avatar_url": null, ...}

    # With exclude_unset, only fields the client sent are included:
    return update.model_dump(exclude_unset=True)
    # {"name": "Alice"}  — if client only sent name
```

`exclude_unset=True` reduces response payload by omitting fields that were not explicitly set
by the caller. For models with 20+ optional fields, this can reduce payload size by 60-80%,
cutting both serialization time and network transfer cost.

---

## 7. Real-World Examples

**Stripe** uses a two-level cache architecture: an in-process Caffeine cache (JVM equivalent:
`TTLCache`) for sub-millisecond reads of rate-limit counters, backed by Redis for cross-process
consistency. The in-process layer absorbs 95% of reads; Redis handles the remaining 5% that
cross process boundaries.

**Discord** caches user presence data (online/offline status) in Redis with a 5-second TTL.
The choice of 5s means presence data is at most 5 seconds stale — acceptable for social
features. A longer TTL would cause "ghost online" bugs; a shorter TTL would triple Redis load.

**Cloudflare** uses HTTP caching aggressively for API responses that change infrequently.
Their API returns `Cache-Control: max-age=300, s-maxage=60` — 5 minutes in browser, 1 minute
at edge CDN. `s-maxage` overrides `max-age` for shared caches (CDNs) only.

**Uber's** matching service caches driver location data in a Redis cluster with a 2-second TTL.
Location data older than 2 seconds is stale enough to cause incorrect ETAs. At Uber's scale
(millions of active drivers), this cache absorbs 40M reads/second that would otherwise hit
the geospatial database.

---

## 8. Tradeoffs

| Strategy | Latency | Consistency | Complexity | Failure Mode |
|----------|---------|-------------|------------|--------------|
| No cache | High (DB speed) | Perfect | Low | DB overload under load |
| In-process TTL | ~0µs | Stale up to TTL; per-process inconsistency | Low | Memory pressure; stale after deploy |
| Redis cache-aside | ~0.3–1ms | Stale up to TTL | Medium | Redis outage → DB fallback needed |
| Redis write-through | ~0.3–1ms added to writes | Strong (reads) | Medium-High | Write amplification |
| Redis write-behind | Lowest write latency | Weak (durability risk) | High | Data loss on Redis crash |
| HTTP cache (CDN) | ~0ms (edge hit) | Stale up to max-age | Low | Stale content until purge |

| Serializer | Throughput (10KB payload) | Notes |
|------------|--------------------------|-------|
| `json` (stdlib) | ~8,000 req/s | Safe default |
| `orjson` | ~35,000 req/s | 3-5x faster; handles datetime natively |
| `ujson` | ~20,000 req/s | Faster than stdlib; less accurate floats |

---

## 9. When to Use / When NOT to Use

**Use in-process cache (`lru_cache` / `TTLCache`) when:**

- The data is read-only or changes very infrequently (configuration, feature flags)
- You run a single process (local dev, single-worker deployment)
- Latency budget is extremely tight (sub-millisecond) and Redis RTT is too slow
- Cache size fits comfortably in memory (hundreds to low thousands of entries)

**Do NOT use in-process cache when:**

- You run multiple worker processes — updates in one process are invisible to others
- The data changes in response to user actions (user profiles, account balances)
- You need explicit invalidation (in-process caches can only expire by TTL or restart)

**Use Redis cache when:**

- Multiple workers or services share the same cached data
- You need explicit invalidation (delete the key when data changes)
- Cache size exceeds what can reasonably fit in a single process's heap
- You need cache persistence across deployments

**Do NOT use Redis cache when:**

- Redis becomes a single point of failure without a retry/fallback path
- The cache miss penalty (DB query) is cheaper than the Redis round trip for your P50
- Data is user-session-scoped and already stored in a session store

**Use HTTP caching when:**

- The endpoint is a public read API (product catalog, static content)
- CDN or reverse proxy sits in front of the application
- Response is identical across users (no personalization)

**Do NOT use HTTP caching when:**

- Response contains user-specific data (authentication token required)
- Data changes faster than the minimum sensible `max-age` (real-time feeds)

---

## 10. Common Pitfalls

### Pitfall 1: `lru_cache` on Async Functions (BROKEN / FIX)

```python
# BROKEN: lru_cache on async function caches the coroutine object, not the result.
# Every call returns the same coroutine which is already exhausted after first await.
from functools import lru_cache

@lru_cache(maxsize=128)
async def get_user_config(user_id: int) -> dict:
    # lru_cache stores the coroutine object returned by the async function.
    # Awaiting the same coroutine twice raises RuntimeError on the second call.
    return await db.fetch_user_config(user_id)

# Usage:
config = await get_user_config(1)   # works once
config = await get_user_config(1)   # RuntimeError: cannot reuse already awaited coroutine
```

```python
# FIX: use an async-aware TTLCache with asyncio.Lock for double-checked locking.
from cachetools import TTLCache
import asyncio

_cache: TTLCache[int, dict] = TTLCache(maxsize=128, ttl=300)
_lock = asyncio.Lock()

async def get_user_config(user_id: int) -> dict:
    if user_id in _cache:
        return _cache[user_id]
    async with _lock:
        if user_id in _cache:  # double-check after acquiring lock
            return _cache[user_id]
        result = await db.fetch_user_config(user_id)
        _cache[user_id] = result
        return result
```

**Why double-check?** Between the first miss check and acquiring the lock, another coroutine
may have already populated the cache. Without the second check, every coroutine that was
waiting for the lock re-fetches from the DB — exactly the stampede we tried to avoid.

---

### Pitfall 2: Missing `decode_responses=True` (BROKEN / FIX)

```python
# BROKEN: Redis client returns bytes by default.
# Key comparisons and JSON parsing fail silently or with confusing TypeErrors.
import redis.asyncio as redis

r = redis.Redis(host="localhost")

async def get_user(user_id: int) -> dict | None:
    key = f"user:{user_id}"
    val = await r.get(key)
    # val is b'{"name": "Alice"}' (bytes), not '{"name": "Alice"}' (str)
    if val:
        return json.loads(val)  # works but requires explicit decode for key ops
    ...
    # Setting: if key == b"user:1" and you compare key == "user:1" → False
    # Bug: pattern matching, key listing, Lua scripts all break with bytes keys
```

```python
# FIX: set decode_responses=True at pool creation.
from redis.asyncio import ConnectionPool
import redis.asyncio as redis

pool = ConnectionPool.from_url(
    "redis://localhost:6379",
    decode_responses=True,   # all responses are str, not bytes
)
r = redis.Redis(connection_pool=pool)

async def get_user(user_id: int) -> dict | None:
    key = f"user:{user_id}"
    val = await r.get(key)   # val is str | None
    return json.loads(val) if val else None
```

---

### Pitfall 3: Using a Global `asyncio.Lock` Across Multiple Keys

```python
# BROKEN: single lock serializes ALL cache misses, not just misses for the same key.
# Under high concurrency, all concurrent requests queue behind one lock regardless of key.
_lock = asyncio.Lock()

async def get_item(item_id: int) -> dict:
    async with _lock:           # serializes lookups for ALL item_ids
        if item_id in _cache:
            return _cache[item_id]
        result = await db.fetch(item_id)
        _cache[item_id] = result
        return result
```

```python
# FIX: use a per-key lock via a dict of locks.
import asyncio
from collections import defaultdict
from cachetools import TTLCache

_cache: TTLCache[int, dict] = TTLCache(maxsize=1024, ttl=300)
_key_locks: dict[int, asyncio.Lock] = defaultdict(asyncio.Lock)

async def get_item(item_id: int) -> dict:
    if item_id in _cache:
        return _cache[item_id]
    async with _key_locks[item_id]:
        if item_id in _cache:
            return _cache[item_id]
        result = await db.fetch(item_id)
        _cache[item_id] = result
        return result
    # Optional: clean up stale locks to prevent unbounded growth
    # _key_locks.pop(item_id, None) — only safe if no other coroutine is waiting
```

---

### Pitfall 4: Not Handling Redis Unavailability

Cache failures should degrade gracefully, not crash the service.

```python
# BROKEN: Redis error propagates and returns 500 to the client.
async def get_user(user_id: int) -> dict:
    r = get_redis()
    cached = await r.get(f"user:{user_id}")  # raises ConnectionError if Redis is down
    ...
```

```python
# FIX: wrap cache operations in try/except with fallback to the source of truth.
import logging

logger = logging.getLogger(__name__)

async def get_user(user_id: int) -> dict:
    r = get_redis()
    try:
        cached = await r.get(f"user:{user_id}")
        if cached:
            return json.loads(cached)
    except Exception as exc:
        logger.warning("Redis unavailable, falling back to DB: %s", exc)

    return await db.fetch_user(user_id)
```

---

## 11. Technologies & Tools

| Tool | Purpose | Strengths | Weaknesses |
|------|---------|-----------|------------|
| `redis.asyncio` | Async Redis client | Full Redis feature set; production-grade; connection pool | Requires Redis server; adds infra dependency |
| `cachetools` | In-process TTL/LRU | Pure Python; zero infra; TTL + size bound | Not shared across processes; no persistence |
| `functools.lru_cache` | In-process LRU | Built-in; zero-overhead decorator | No TTL; sync only; caches stale data forever |
| `fastapi-cache2` | Decorator-based response caching | Easy to use; pluggable backends (Redis, in-memory) | Limited control over key logic; hard to invalidate selectively |
| `cashews` | Async-first cache with decorators | Tags, soft TTL, anti-stampede built in; async-native | Less widely used; smaller ecosystem |
| `aiocache` | Async caching library | Multiple backends; serializers; TTL support | Less active maintenance; API surface is larger |
| `orjson` | Fast JSON serializer | 3-5x faster than stdlib; handles `datetime`/`UUID` natively | Requires Rust toolchain for source install |
| `ujson` | Fast JSON serializer | 2-3x faster than stdlib; drop-in replacement | Known float precision edge cases |
| `dogpile.cache` | Anti-stampede + multi-backend | `dogpile` locking prevents stampede natively | Sync-first; async support is limited |

---

## 12. Interview Questions with Answers

**Q1: What is the thundering herd problem in caching, and how do you prevent it in Redis?**
When a popular cache key expires, all concurrent requests simultaneously detect a miss and
attempt to recompute the value, overwhelming the database. Prevent it with a Redis mutex:
`SET lock:<key> 1 NX EX 5` — the first caller acquires the lock atomically; others either
return a stale value or wait briefly before re-checking. In practice, returning a slightly
stale value ("soft TTL") is often better than making all callers wait, because it maintains
availability without adding lock wait time.

**Q2: Why can't you use `functools.lru_cache` on an `async def` function?**
`lru_cache` stores whatever the decorated callable returns. An `async def` function returns a
coroutine object, not the result. The first call caches the coroutine; the second call returns
the same exhausted coroutine object, which raises `RuntimeError: cannot reuse already awaited
coroutine`. Use `cachetools.TTLCache` with `asyncio.Lock` for async-safe in-process caching,
or use `aiocache`/`cashews` which are async-native.

**Q3: In a Gunicorn + Uvicorn deployment with 4 worker processes, why is in-process caching
often insufficient for invalidation?**
Each worker process has an independent memory space. When user data changes (e.g., profile
update), you can invalidate the cache entry in the process that handled the write, but the
other three processes still hold the stale entry until their TTL expires. For data that
requires prompt invalidation, Redis is required because it is a single shared store visible to
all workers. In-process caches are still useful as an L1 layer in front of Redis for
high-frequency reads where per-process staleness of a few seconds is acceptable.

**Q4: What is the difference between `Cache-Control: max-age` and `s-maxage`?**
`max-age` sets the freshness lifetime for all caches, including the browser. `s-maxage`
overrides `max-age` for shared caches only (CDNs, reverse proxies) while leaving the browser
behavior unchanged. Use `Cache-Control: max-age=300, s-maxage=60` when you want the browser
to cache for 5 minutes but the CDN edge to refresh every 60 seconds — useful for content that
CDN purge scripts control but browsers should cache longer.

**Q5: How does `ETag` reduce bandwidth even when `max-age` has expired?**
When `max-age` expires the browser sends a conditional `GET` with `If-None-Match: "<etag>"`.
The server computes the current ETag; if it matches, it returns `304 Not Modified` with no
body. The browser uses its cached copy. This saves the response body transfer (potentially
hundreds of KB) even though the cache is technically "expired". Round-trip still occurs, but
bandwidth and server serialization cost are eliminated.

**Q6: How do you design a cache key that is both collision-free and invalidation-friendly?**
Use a hierarchical namespace: `{version}:{entity}:{id}:{sub-resource}`. Example:
`v1:user:42:profile`. The version prefix allows a "schema invalidation" by bumping `v1` to
`v2` — new writes go to `v2:*`, old `v1:*` keys expire naturally. The entity and sub-resource
segments allow targeted invalidation: delete `v1:user:42:*` to invalidate all cached data for
user 42 using Redis `SCAN` + `DEL`, or use a tagging system like `cashews`.

**Q7: What is the write-behind (write-back) cache pattern and when is it dangerous?**
Write-behind writes to the cache immediately and flushes to the database asynchronously via a
background job. Write latency to the client is minimized because the DB round trip is removed
from the critical path. It is dangerous because if the cache crashes before the flush, writes
since the last flush are lost. It is appropriate only for data where some loss is tolerable
(event counters, analytics) or where the cache has AOF/RDB persistence with durability
guarantees (Redis with `appendfsync always`).

**Q8: How does `orjson` improve FastAPI performance, and when does it not help?**
`orjson` serializes Python objects to JSON 3-5x faster than stdlib `json` because it is
implemented in Rust. It natively handles `datetime`, `UUID`, and `dataclass` without custom
encoders. Set `default_response_class=ORJSONResponse` on the `FastAPI` constructor to apply
globally. It does not help when the bottleneck is database I/O or network transfer rather than
serialization — profile first with `py-spy` to confirm serialization is the actual hot path.

**Q9: What is probabilistic early expiration (XFetch) and how does it differ from a mutex?**
XFetch extends a key's TTL probabilistically before it expires. When a key has TTL remaining
of `delta`, each fetch has a probability of recomputing equal to `exp(-remaining / (delta × beta))`.
As expiry approaches, more requests volunteer to recompute, spreading the load. No explicit
lock is needed; the key is never fully expired, so no stampede window exists. A mutex is
simpler but creates a "lock wait" period; XFetch eliminates this at the cost of occasionally
doing redundant recomputations.

**Q10: How do you size a Redis connection pool for a FastAPI application?**
The formula is: `max_connections ≥ num_workers × max_simultaneous_redis_ops_per_worker`.
For 4 Uvicorn workers each handling 200 concurrent requests, where 10% of requests touch
Redis: `4 × 200 × 0.1 = 80` minimum connections. Add 20% headroom: pool of 100. Monitor
`redis_connected_clients` in Prometheus; if it regularly exceeds 80% of `max_connections`,
scale the pool or add Redis replicas for read traffic. Setting `max_connections` too low causes
`redis.exceptions.ConnectionError: too many connections`.

**Q11: When should you use `response_model_exclude_unset=True` at the router level vs
`model.model_dump(exclude_unset=True)` at the handler level?**
`response_model_exclude_unset=True` on the route decorator (`@app.get(..., response_model_exclude_unset=True)`)
applies `exclude_unset` automatically during FastAPI's response model serialization — you do
not touch the return value. `model.model_dump(exclude_unset=True)` in the handler gives finer
control: you can apply different exclusions per field or merge the partial update dict. Use
the route-level option for read endpoints where the model directly maps to the response; use
handler-level `model_dump` for PATCH endpoints that merge partial updates before writing.

**Q12: How do you implement cache invalidation on write without a dedicated invalidation
service?**
In the same DB transaction (or immediately after a successful write), issue `await r.delete(key)`.
This removes the stale cache entry; the next read will miss and repopulate. For complex
invalidation (e.g., "invalidate all keys for user 42"), use a Redis key tag scheme: prefix all
related keys with `user:42:*` and run `SCAN` with `MATCH user:42:*` + `DEL`. Alternatively,
bump the version component in the key (`v1:` → `v2:`) at deploy time to mass-invalidate
without `SCAN`. Note: `SCAN` is O(N) where N is the total keyspace; on large Redis instances
this can be slow — prefer key-tagging libraries (`cashews`, custom sets of tagged keys) for
production-grade selective invalidation.

---

## 13. Best Practices

1. **Profile before caching.** Use `py-spy top --pid <pid>` or `cProfile` to confirm where
   time is actually spent. Caching the wrong code path wastes memory without improving p99.

2. **Always set a TTL.** An unbounded cache entry that never expires is a memory leak in
   disguise. The only exception is truly immutable data (e.g., a content-addressed asset hash).

3. **Design keys as a contract.** Document the key schema (namespace, version, entity, sub-resource)
   in a `CACHE_KEYS` constant or docstring. Treat a key format change as a breaking change
   requiring a version bump.

4. **Use `decode_responses=True` at pool creation**, not per-command. Inconsistent decode
   handling is a persistent source of `TypeError` bugs.

5. **Wrap all cache operations in `try/except`.** Caches should be transparent accelerators.
   A Redis outage must degrade to slower (DB) reads, not 500 errors.

6. **Use `orjson` for large payloads.** Set `default_response_class=ORJSONResponse` globally;
   the cost is a Rust dependency; the benefit is 3-5x faster serialization for free.

7. **Combine in-process (L1) and Redis (L2) for hot data.** The L1 cache absorbs repeated
   reads within a single request burst; L2 serves cross-process consistency. L1 TTL should be
   shorter than L2 TTL (e.g., 5s in-process, 300s in Redis).

8. **Avoid `KEYS` in production Redis.** `KEYS *` blocks the entire Redis server while
   scanning. Use `SCAN cursor MATCH pattern COUNT 100` instead — it is incremental.

9. **Test cache behavior explicitly.** Write tests that: (a) verify a second call hits the
   cache (mock the DB and confirm it is called exactly once), and (b) verify invalidation
   removes the stale entry.

10. **Monitor cache hit rate in production.** Emit a `cache_hit` / `cache_miss` counter
    (Prometheus Counter or Datadog increment). A hit rate below 80% for hot-path data
    indicates a TTL that is too short, poor key design, or the cache is being bypassed
    unintentionally.

---

## 14. Case Study

### Multi-Tier Caching for a Product Catalog API

A FastAPI service powers a product catalog for an e-commerce platform. The catalog is read
heavily (50,000 RPS at peak) but updated infrequently (product edits a few hundred times per
day). The database is PostgreSQL. Without caching, the DB runs at 100% CPU at peak. Goal:
reduce DB load to under 10% of peak at steady state.

#### Architecture

```
 Browser / Mobile Client
        │
        │  Cache-Control: public, max-age=60, s-maxage=30
        │  ETag: "<product-hash>"
        ▼
   CDN (CloudFront / Fastly)
        │
        │  Cache HIT: ~0ms (edge, no origin request)
        │  Cache MISS (30s TTL expired) ↓
        ▼
   FastAPI Service (4 Uvicorn workers)
        │
        │  L1: In-process TTLCache (maxsize=2000, ttl=10s)
        │  HIT: ~0µs
        │  MISS ↓
        ▼
   Redis Cluster (3 shards, decode_responses=True)
        │
        │  Key: "v2:product:{id}:detail"
        │  TTL: 300s
        │  HIT: ~0.5ms
        │  MISS ↓
        ▼
   PostgreSQL (read replica)
        │
        └── SETEX to Redis, populate L1, return response
```

#### Implementation

```python
# app/services/product_service.py
import asyncio
import json
import hashlib
import logging
import redis.asyncio as redis
from cachetools import TTLCache
from app.db import get_product_from_db

logger = logging.getLogger(__name__)

# L1: in-process, per-worker, 10 second TTL
_l1_cache: TTLCache[int, dict] = TTLCache(maxsize=2000, ttl=10)
_l1_locks: dict[int, asyncio.Lock] = {}

def _get_lock(product_id: int) -> asyncio.Lock:
    if product_id not in _l1_locks:
        _l1_locks[product_id] = asyncio.Lock()
    return _l1_locks[product_id]

async def get_product(product_id: int, r: redis.Redis) -> dict:
    # L1 check
    if product_id in _l1_cache:
        return _l1_cache[product_id]

    async with _get_lock(product_id):
        if product_id in _l1_cache:  # double-check
            return _l1_cache[product_id]

        # L2 check (Redis)
        redis_key = f"v2:product:{product_id}:detail"
        try:
            cached = await r.get(redis_key)
            if cached:
                product = json.loads(cached)
                _l1_cache[product_id] = product
                return product
        except Exception as exc:
            logger.warning("Redis L2 miss (error): %s", exc)

        # DB fallback
        product = await get_product_from_db(product_id)
        _l1_cache[product_id] = product

        try:
            await r.setex(redis_key, 300, json.dumps(product))
        except Exception as exc:
            logger.warning("Redis write failed (non-fatal): %s", exc)

        return product

async def invalidate_product(product_id: int, r: redis.Redis) -> None:
    """Call after a product update."""
    # Remove from L1 in this worker
    _l1_cache.pop(product_id, None)

    # Remove from L2 (other workers' L1 expires naturally in ≤10s)
    redis_key = f"v2:product:{product_id}:detail"
    try:
        await r.delete(redis_key)
    except Exception as exc:
        logger.error("Redis invalidation failed: %s", exc)
```

```python
# app/routers/products.py
import hashlib
import json
from fastapi import APIRouter, Depends, Request, Response
from fastapi.responses import ORJSONResponse
from app.services.product_service import get_product, invalidate_product
from app.cache import get_redis

router = APIRouter(default_response_class=ORJSONResponse)

@router.get("/products/{product_id}")
async def product_detail(
    product_id: int,
    request: Request,
    response: Response,
    r=Depends(get_redis),
) -> dict:
    product = await get_product(product_id, r)

    # Build ETag from content hash
    body = json.dumps(product, sort_keys=True)
    etag = f'"{hashlib.sha1(body.encode()).hexdigest()[:16]}"'

    if request.headers.get("if-none-match") == etag:
        return Response(status_code=304)

    response.headers["Cache-Control"] = "public, max-age=60, s-maxage=30"
    response.headers["ETag"] = etag
    return product

@router.put("/products/{product_id}")
async def update_product(
    product_id: int,
    update: dict,
    r=Depends(get_redis),
) -> dict:
    updated = await db.update_product(product_id, update)
    await invalidate_product(product_id, r)   # explicit invalidation
    return updated
```

#### BROKEN / FIX: Stampede on Startup

```python
# BROKEN: on cold start, all workers simultaneously miss L1 and L2,
# and fire concurrent DB queries for the same product_id before any result is cached.
# No lock protection → 4 workers × N concurrent requests = 4N simultaneous DB queries.

async def get_product_broken(product_id: int, r: redis.Redis) -> dict:
    if product_id in _l1_cache:
        return _l1_cache[product_id]
    cached = await r.get(f"v2:product:{product_id}:detail")
    if cached:
        return json.loads(cached)
    product = await get_product_from_db(product_id)  # no per-key lock
    _l1_cache[product_id] = product
    await r.setex(f"v2:product:{product_id}:detail", 300, json.dumps(product))
    return product
```

```python
# FIX: per-key asyncio.Lock in the get_product() function above ensures only one
# coroutine per worker populates L1/L2 for a given product_id.
# Redis-level stampede is handled by the SET NX EX lock pattern if needed for
# very high per-key concurrency across workers.
```

#### Results (measured with `locust`)

| Metric | Before caching | After L1+L2+HTTP cache |
|--------|---------------|------------------------|
| DB CPU at peak | 100% | 7% |
| P50 latency | 18ms | 0.4ms (L1 hit) |
| P99 latency | 120ms | 3ms (L2 hit) |
| Redis hit rate | — | 94% |
| L1 hit rate | — | 78% |

**Discussion Questions:**

1. The L1 TTL is 10 seconds and the Redis TTL is 300 seconds. If a product price is updated,
   the DB reflects the new price immediately, Redis holds the old price for up to 300 seconds,
   and L1 holds it for up to 10 seconds. How do you make `invalidate_product()` propagate the
   invalidation to all four workers' L1 caches, not just the one handling the write?
   *(Answer: use Redis Pub/Sub — workers subscribe to an `invalidation` channel and delete the
   L1 entry when they receive a message. Redis Keyspace Notifications are an alternative.)*

2. The CDN has a 30-second `s-maxage`. After a product price update + cache invalidation, CDN
   edges may serve the old price for up to 30 seconds. What mechanism allows instant CDN
   invalidation? *(Answer: CDN cache purge API — Fastly `instant-purge`, CloudFront
   `create_invalidation`. Call it from `invalidate_product()` with the product URL pattern.)*

3. Why is `asyncio.Lock` correct here instead of `threading.Lock`?
   *(Answer: FastAPI runs on a single-threaded event loop per worker. `threading.Lock` would
   block the event loop thread while waiting; `asyncio.Lock` yields control to other
   coroutines while waiting, preserving concurrency.)*
