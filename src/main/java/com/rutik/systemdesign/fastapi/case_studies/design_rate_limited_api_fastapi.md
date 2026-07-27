# Design a Rate-Limited API with FastAPI

---

## Problem Statement

Design a production-grade rate limiter for a public API with the following requirements:

**Functional requirements:**
- Limit each API key to 100 requests per minute (sliding window) and 1000 requests per day.
- Enforce limits across all pods in a horizontally scaled deployment — no in-process state.
- Return HTTP 429 with a `Retry-After` header and an RFC 9457 Problem Details error body on limit breach.
- Fail open: if Redis is unavailable, allow traffic rather than blocking the entire API.
- Expose the limiter as a `Depends()`-injected service so individual routes can override their own limits.

**Non-functional requirements:**
- P99 overhead added by the rate-limiter check: under 2 ms at 5000 req/s.
- No single point of failure — Redis Sentinel or Cluster is acceptable; the app must not crash if Redis goes down.
- The counter must be exact under concurrency: at 5000 req/s no client may be admitted past its limit
  inside a single window because two requests raced (no check-then-act).

**Out of scope:**
- IP-based limiting (shown as extension in Interview Discussion Points).
- Rate-limit bypass for internal services (handled at the API gateway layer).
- Persistent audit log of limit breaches.

---

## Architecture Overview

The `RateLimiter` dependency sits between the router and the route handler on every request; the two decision diamonds are what make it a rate limiter — a Redis outage takes the dotted fail-open bypass instead of blocking the 100 req/min and 1000 req/day checks that protect the API.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Client([Client]) -->|"X-Api-Key header"| LB(Load Balancer<br/>nginx / AWS ALB)
    LB -->|"round-robin"| Router(Router<br/>resolves Depends)

    subgraph Pod["FastAPI Pod"]
        Router --> RL(RateLimiter call<br/>extract key + build Redis key)
        RL --> Lua(Lua script<br/>atomic INCR + EXPIRE)
        Lua --> Down{Redis unavailable?}
        Down -->|"yes"| FailOpen(Fail-open<br/>log warning + continue)
        Down -->|"no"| Limit{count over limit?}
        Limit -->|"yes"| Raise(raise RateLimitExceeded)
        Limit -->|"no"| SetState(set request.state)
        FailOpen -.->|"bypass"| Handler(Route Handler)
        SetState --> Handler
        Raise --> ExcHandler(Exception Handler)
    end

    Lua -->|"eval"| Redis
    Redis@{ icon: "logos:redis", form: "square", label: "Redis<br/>Standalone / Sentinel / Cluster", pos: "b", h: 44 }
    Handler --> Resp200([200 response])
    ExcHandler --> Resp429([429 + Retry-After])

    class Client io
    class LB,RL,Lua,Down,Limit mathOp
    class Router req
    class FailOpen,Raise,ExcHandler,Resp429 lossN
    class SetState,Handler,Resp200 train
```

Each of the two windows gets its own Redis key so the minute and day counters expire independently; every TTL is set to twice the window length so a key can never vanish while it is still the authoritative counter.

```
Redis key layout
-----------------
  rate::{api_key}::minute::{window_start_unix_second}
    - type: string (integer counter)
    - TTL:  120 s  (2x window, allows one full window overlap)

  rate::{api_key}::day::{utc_date_string}
    - type: string (integer counter)
    - TTL:  172800 s (48 h, same 2x-window safety margin)

Example keys
  rate::sk-abc123::minute::1717516260
  rate::sk-abc123::day::2024-06-04
```

---

## Key Design Decisions

**1. Fixed window counter vs token bucket vs sliding window log**

This design uses a **fixed window counter**: the key is bucketed to the start of the current 60-second window (`floor(now/60)*60`), and all requests inside that window share one counter, which disappears when the window rolls. That is O(1) memory per key, one Redis round-trip, and trivially inspectable — but it keeps the well-known boundary spike. A client can fire 100 requests in the last second of window N and 100 more in the first second of window N+1: 200 requests in 2 seconds, each window individually compliant. The worst case is bounded at 2x the limit over a 2-window span, and that is the price this design pays for O(1) state.

The two alternatives buy a smoother curve at a cost. A **sliding window log** (a sorted set of per-request timestamps, `ZREMRANGEBYSCORE` + `ZCARD`) is exact over any 60-second span but uses O(requests-per-window) memory per key. A **weighted sliding window counter** reads both the current and previous window keys and prorates the older one by the fraction of the window that has elapsed — near-exact at O(1) memory, but it touches two keys, which on Redis Cluster forces a hash tag so both land on the same slot. Pick the log when a hard bound matters (billing, quota enforcement), the fixed window when throughput protection is the goal.

The counter is compliant inside each window, yet the client still bursts 200 requests into a 2-second span at the seam between them:

```mermaid
xychart-beta
    title "Fixed-window boundary spike (100 req/min limit)"
    x-axis ["Window N (last 1s)", "Window N+1 (first 1s)", "Combined (2s span)"]
    y-axis "Requests allowed" 0 --> 220
    bar [100, 100, 200]
```

**2. `Depends()` injection vs middleware**

Middleware runs for every request unconditionally. It cannot inspect the resolved route or its declared limits without duplicating the routing logic. `Depends()` injection lets each route specify its own `RateLimiter(limit=10, window=60)` instance; the FastAPI dependency system handles instantiation and caching. The trade-off is that a route that forgets to declare the dependency is unprotected. The recommended pattern is to set the default limiter as a global dependency on the `APIRouter` and allow per-route overrides.

**3. Lua script for atomicity**

The naive implementation does:
```
count = await redis.get(key)
if count >= limit:
    raise 429
await redis.incr(key)
await redis.expire(key, ttl)
```
This is a classic TOCTOU race: two concurrent requests both read `count = 99`, both pass the limit check, and both increment to 100 and 101. The fix is to run GET + INCR + EXPIRE as a single atomic Lua script. Redis is single-threaded in its command execution; a Lua script is a transaction — no other command can interleave.

**4. Fail-open on Redis unavailability**

If Redis is down, the alternative is to fail-closed (return 503 to all traffic). For most public APIs, blocking all traffic because the rate limiter is unavailable is worse than temporarily allowing excess traffic. The implementation catches `redis.exceptions.RedisError` and logs a `WARNING` metric (so an alert fires), then continues to the route handler. Operators can decide to fail-closed by re-raising the exception.

**5. Two independent time windows**

The minute window (100 req/min) prevents burst abuse. The day window (1000 req/day) caps sustained usage. Each is a separate Redis key with its own TTL. Both checks run inside the same `__call__` coroutine. If either limit is exceeded, the 429 response body identifies which window was hit.

---

## Implementation

### Dependencies and models

```python
# requirements: fastapi>=0.140, redis>=8.0, pydantic>=2.13, uvicorn>=0.51

from __future__ import annotations

import time
import logging
from contextlib import asynccontextmanager
from typing import Annotated, AsyncGenerator

import redis.asyncio as aioredis
from fastapi import Depends, FastAPI, HTTPException, Request, Response, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel

logger = logging.getLogger(__name__)
```

### Redis connection pool via `lifespan`

```python
class AppState:
    redis: aioredis.Redis


app_state = AppState()


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Create the Redis connection pool once at startup; close it on shutdown."""
    pool = aioredis.ConnectionPool.from_url(
        "redis://localhost:6379",
        max_connections=50,
        decode_responses=True,
        socket_connect_timeout=1.0,
        socket_timeout=0.5,
    )
    app_state.redis = aioredis.Redis(connection_pool=pool)
    logger.info("Redis connection pool created (max_connections=50)")
    yield
    await app_state.redis.aclose()
    logger.info("Redis connection pool closed")


app = FastAPI(lifespan=lifespan)
```

### RFC 9457 Problem Details error model and custom exception

```python
class ProblemDetail(BaseModel):
    type: str = "https://example.com/errors/rate-limit-exceeded"
    title: str = "Too Many Requests"
    status: int = 429
    detail: str
    instance: str
    retry_after: int


class RateLimitExceeded(Exception):
    def __init__(self, retry_after: int, detail: str, path: str) -> None:
        self.retry_after = retry_after
        self.detail = detail
        self.path = path


@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded) -> JSONResponse:
    body = ProblemDetail(
        detail=exc.detail,
        instance=exc.path,
        retry_after=exc.retry_after,
    )
    return JSONResponse(
        status_code=status.HTTP_429_TOO_MANY_REQUESTS,
        content=body.model_dump(),
        media_type="application/problem+json",  # required by RFC 9457
        headers={"Retry-After": str(exc.retry_after)},
    )
```

### Lua script for atomic sliding-window check-and-increment

```python
# Single Lua script executes atomically on the Redis server.
# KEYS[1] = the rate-limit key
# ARGV[1] = the TTL in seconds
# ARGV[2] = the per-window request limit
# Returns: [current_count, limit_exceeded_flag]
#   limit_exceeded_flag = 1 if the NEW count exceeds the limit, 0 otherwise.

RATE_LIMIT_LUA = """
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if current > tonumber(ARGV[2]) then
    return {current, 1}
end
return {current, 0}
"""
```

### BROKEN vs FIX: why two separate Redis calls are not safe

```python
# BROKEN: TOCTOU race between GET and INCR.
# Two requests arriving simultaneously both read count=99, both pass the check,
# both write count=100 and count=101 — one over the limit silently slips through.

async def _check_limit_broken(redis_client: aioredis.Redis, key: str, limit: int, ttl: int) -> bool:
    count_raw = await redis_client.get(key)               # read
    count = int(count_raw) if count_raw else 0
    if count >= limit:
        return True                                        # over limit
    await redis_client.incr(key)                          # write — NOT atomic with the read above
    await redis_client.expire(key, ttl)                   # separate round-trip for TTL
    return False

# FIX: single Lua script — Redis executes it as one atomic operation.
# INCR, conditional EXPIRE, and limit check happen in one server-side transaction.

async def _check_limit_lua(
    redis_client: aioredis.Redis,
    key: str,
    limit: int,
    ttl: int,
) -> tuple[int, bool]:
    """Returns (current_count, is_exceeded)."""
    result: list[int] = await redis_client.eval(  # type: ignore[assignment]
        RATE_LIMIT_LUA,
        1,         # number of KEYS
        key,       # KEYS[1]
        ttl,       # ARGV[1]
        limit,     # ARGV[2]
    )
    current, exceeded = int(result[0]), bool(result[1])
    return current, exceeded
```

### `RateLimiter` dependency class

```python
def _minute_key(api_key: str) -> str:
    window_start = int(time.time()) // 60 * 60   # floor to 60-second boundary
    return f"rate::{api_key}::minute::{window_start}"


def _day_key(api_key: str) -> str:
    # UTC, not local time: the Retry-After below counts down to UTC midnight,
    # so the bucket must roll at UTC midnight too.
    from datetime import datetime, timezone
    return f"rate::{api_key}::day::{datetime.now(tz=timezone.utc).date().isoformat()}"


class RateLimiter:
    """
    FastAPI dependency that enforces per-minute and per-day request limits.

    Usage:
        @router.get("/items", dependencies=[Depends(RateLimiter())])

    Per-route override:
        strict_limiter = RateLimiter(minute_limit=10, day_limit=100)

        @router.get("/sensitive", dependencies=[Depends(strict_limiter)])
    """

    def __init__(
        self,
        minute_limit: int = 100,
        day_limit: int = 1000,
    ) -> None:
        self.minute_limit = minute_limit
        self.day_limit = day_limit

    async def __call__(self, request: Request) -> None:
        api_key: str | None = request.headers.get("X-Api-Key")
        if not api_key:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="X-Api-Key header is required.",
            )

        redis_client: aioredis.Redis = app_state.redis

        try:
            # Check minute window
            m_key = _minute_key(api_key)
            m_count, m_exceeded = await _check_limit_lua(
                redis_client, m_key, self.minute_limit, ttl=120
            )
            if m_exceeded:
                retry_after = 60 - (int(time.time()) % 60)
                # Persist counts in request.state for downstream middleware/logging
                request.state.rate_limit_minute = m_count
                raise RateLimitExceeded(
                    retry_after=retry_after,
                    detail=f"Minute limit of {self.minute_limit} requests exceeded. "
                           f"Retry in {retry_after} seconds.",
                    path=request.url.path,
                )

            # Check day window
            d_key = _day_key(api_key)
            d_count, d_exceeded = await _check_limit_lua(
                redis_client, d_key, self.day_limit, ttl=172800
            )
            if d_exceeded:
                from datetime import datetime, timezone, timedelta
                tomorrow_midnight = (
                    datetime.now(tz=timezone.utc).replace(
                        hour=0, minute=0, second=0, microsecond=0
                    ) + timedelta(days=1)
                )
                retry_after = int(
                    (tomorrow_midnight - datetime.now(tz=timezone.utc)).total_seconds()
                )
                request.state.rate_limit_day = d_count
                raise RateLimitExceeded(
                    retry_after=retry_after,
                    detail=f"Daily limit of {self.day_limit} requests exceeded. "
                           f"Retry in {retry_after} seconds.",
                    path=request.url.path,
                )

            # Attach counts to request.state for optional X-RateLimit-* response headers
            request.state.rate_limit_minute = m_count
            request.state.rate_limit_minute_limit = self.minute_limit
            request.state.rate_limit_day = d_count
            request.state.rate_limit_day_limit = self.day_limit

        except RateLimitExceeded:
            raise  # propagate to the registered exception handler
        except aioredis.RedisError as exc:
            # Fail-open: log and continue; do not block the request
            logger.warning(
                "Redis unavailable for rate limiting — failing open. error=%s", exc
            )
```

### Routes — default limit and per-route override

```python
# Reusable type alias for the injected dependency
RateLimitDep = Annotated[None, Depends(RateLimiter())]

# Stricter limiter for a sensitive endpoint
strict_limiter = RateLimiter(minute_limit=10, day_limit=100)
StrictRateLimitDep = Annotated[None, Depends(strict_limiter)]


@app.get("/api/v1/items", dependencies=[Depends(RateLimiter())])
async def list_items(request: Request) -> dict[str, object]:
    return {
        "items": ["a", "b", "c"],
        "rate_limit": {
            "minute_used": request.state.rate_limit_minute,
            "minute_limit": request.state.rate_limit_minute_limit,
        },
    }


@app.post("/api/v1/admin/reset", dependencies=[Depends(strict_limiter)])
async def admin_reset(request: Request) -> dict[str, str]:
    # 10 req/min, 100 req/day limit applied here
    return {"status": "reset complete"}
```

### pytest: testing with dependency override

```python
# tests/test_rate_limiter.py
from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from httpx import AsyncClient, ASGITransport

from main import app, RateLimiter, app_state


@pytest.fixture
def mock_redis_under_limit() -> AsyncMock:
    """Simulate Redis returning count=1 (under any reasonable limit)."""
    redis_mock = AsyncMock()
    redis_mock.eval = AsyncMock(return_value=[1, 0])   # count=1, exceeded=False
    return redis_mock


@pytest.fixture
def mock_redis_over_limit() -> AsyncMock:
    """Simulate Redis returning count=101 (over the 100 req/min limit)."""
    redis_mock = AsyncMock()
    redis_mock.eval = AsyncMock(return_value=[101, 1])  # count=101, exceeded=True
    return redis_mock


@pytest.mark.asyncio
async def test_request_allowed_under_limit(mock_redis_under_limit: AsyncMock) -> None:
    app_state.redis = mock_redis_under_limit
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get(
            "/api/v1/items", headers={"X-Api-Key": "test-key-123"}
        )
    assert response.status_code == 200
    assert response.json()["rate_limit"]["minute_used"] == 1


@pytest.mark.asyncio
async def test_rate_limit_exceeded_returns_429(mock_redis_over_limit: AsyncMock) -> None:
    app_state.redis = mock_redis_over_limit
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get(
            "/api/v1/items", headers={"X-Api-Key": "test-key-123"}
        )
    assert response.status_code == 429
    body = response.json()
    assert body["title"] == "Too Many Requests"
    assert "Retry-After" in response.headers
    assert int(response.headers["Retry-After"]) > 0


@pytest.mark.asyncio
async def test_missing_api_key_returns_401() -> None:
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get("/api/v1/items")  # no X-Api-Key
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_fail_open_on_redis_error() -> None:
    """If Redis raises, the request must still succeed (fail-open)."""
    import redis.asyncio as aioredis
    broken_redis = AsyncMock()
    broken_redis.eval = AsyncMock(side_effect=aioredis.RedisError("timeout"))
    app_state.redis = broken_redis
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get(
            "/api/v1/items", headers={"X-Api-Key": "test-key-123"}
        )
    assert response.status_code == 200
```

---

## Python/FastAPI Components Used

| Component | Role in this design |
|-----------|---------------------|
| `Depends()` | Injects `RateLimiter.__call__` into the route handler; supports per-route limit customization by constructing different `RateLimiter(...)` instances |
| `lifespan` context manager | Creates the `redis.asyncio.ConnectionPool` once at startup and closes it cleanly at shutdown; avoids per-request connection overhead |
| Custom exception handler (`@app.exception_handler`) | Converts `RateLimitExceeded` into a structured 429 JSON response with `Retry-After` header; decouples error shaping from business logic |
| `HTTPException` | Used for the 401 missing-API-key case; FastAPI converts it to a JSON error automatically |
| `Request.state` | Carries per-request metadata (current counts, limits) from the `RateLimiter` to the route handler and to any downstream middleware that wants to add `X-RateLimit-*` response headers |
| `redis.asyncio.Redis` | Non-blocking async Redis client; `eval()` executes the Lua script; `ConnectionPool` is shared across all concurrent requests |
| `BaseModel` (Pydantic v2) | Defines the `ProblemDetail` response schema; `model_dump()` serializes it to a dict for `JSONResponse` |
| `Annotated` type alias | `RateLimitDep = Annotated[None, Depends(RateLimiter())]` — clean, reusable dependency declaration that avoids repeating `Depends(RateLimiter())` on every route |

---

## Tradeoffs and Alternatives

| Dimension | This design | Alternative | When to switch |
|-----------|-------------|-------------|----------------|
| Enforcement point | `Depends()` per route | ASGI middleware | When all routes share identical limits and you want zero risk of a route forgetting to declare the dependency |
| Atomicity | Lua script (`EVAL`) | `MULTI`/`EXEC` transaction | Never for this job — `MULTI`/`EXEC` is atomic and isolated, but queued commands return nothing until `EXEC`, so you cannot branch on the count inside it; that needs `WATCH` + a client retry loop. Lua does the read, the branch and the write server-side in one pass |
| State store | Redis (distributed) | In-process `dict` + `asyncio.Lock` | Single-pod deployments only; breaks immediately with a second pod |
| Algorithm | Fixed window counter | Sliding window log (true sliding) | When a hard bound over any 60-second span is required; log uses O(N) memory per key where N = requests per window |
| Algorithm | Fixed window counter | Token bucket | When burst capacity is a feature, not a bug (e.g., allow 20-request bursts then refill at 1.67 req/s) |
| Fail behavior | Fail-open (allow on Redis error) | Fail-closed (503 on Redis error) | High-security APIs (financial, auth) where an uncontrolled burst is worse than temporary unavailability |

---

## Interview Discussion Points

**Q: Why use a Lua script instead of separate GET + INCR commands?**
The separate approach has a TOCTOU race: two concurrent requests can both read `count = N`, both pass the `N < limit` check, and both increment — writing `N+1` and `N+2` while one of them should have been rejected. The Lua script runs as a single atomic operation on the Redis server (which is single-threaded for command execution), so no other command can interleave. It also saves one network round-trip compared to a GET + INCR + EXPIRE sequence.

**Q: What is the fixed window counter algorithm and how does it differ from a true sliding window log?**
The fixed window counter buckets time into discrete windows (e.g., each 60-second interval). All requests in the same window share one counter. At the exact second a new window starts, the counter resets. A true sliding window log stores the timestamp of every request in a sorted set; to check the limit you count entries from `now - 60s` to `now` and evict the rest. The log gives perfect accuracy but uses O(requests) memory per key and requires two Redis commands (`ZREMRANGEBYSCORE` + `ZCARD`). The counter uses O(1) memory but admits up to 2x the limit across a window boundary when requests cluster on the seam.

**Q: How would you add per-IP limits alongside per-API-key limits?**
Add a second `RateLimiter` variant that keys on `request.client.host` instead of the `X-Api-Key` header. Apply it as an additional dependency or stack both: `dependencies=[Depends(ip_limiter), Depends(key_limiter)]`. The two limiters are independent; either one can trigger a 429. Use separate key namespaces — `rate::ip::{ip}::minute::...` — to avoid collisions. Behind a proxy, `request.client.host` is the proxy's address, so you must run Uvicorn with `--proxy-headers --forwarded-allow-ips=<lb-cidr>` and let it rewrite `client.host` from `X-Forwarded-For`. Never read `X-Forwarded-For` yourself and never trust the leftmost entry: a client can send any header it likes, so an attacker rotates a fake value per request and the IP limit becomes a no-op. Take the rightmost hop your own infrastructure appended, and pin `forwarded-allow-ips` to the load balancer's addresses so unproxied traffic cannot spoof it at all.

**Q: What happens if Redis goes down mid-deployment?**
The `except aioredis.RedisError` block in `__call__` catches all connection errors, timeouts, and cluster failures. It logs a warning (which should trigger a PagerDuty alert via your log aggregator) and returns without raising, allowing the request to proceed to the route handler. This means the API stays up but rate limiting is suspended. If you prefer fail-closed, replace the `logger.warning` with `raise HTTPException(status_code=503)`.

**Q: How would you implement burst capacity (token bucket) on top of this?**
Store two values per key: `tokens_remaining` and `last_refill_timestamp`. In the Lua script: compute `elapsed = now - last_refill`; compute `refill = elapsed * refill_rate`; set `tokens = min(bucket_capacity, tokens_remaining + refill)`; if `tokens >= 1`, decrement and allow; otherwise deny. The Lua script must be updated to accept `bucket_capacity` and `refill_rate` as arguments. Token bucket allows short bursts up to `bucket_capacity` while still enforcing a long-run average rate.

The refill arithmetic is three sequential computations feeding one threshold check — easy to skim past in prose, so laid out as steps:

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    Req([Request arrives]) --> Stored(tokens_remaining<br/>last_refill_timestamp)
    Stored --> Elapsed(elapsed = now minus last_refill)
    Elapsed --> Refill(refill = elapsed times refill_rate)
    Refill --> Tokens("tokens = min(capacity, remaining + refill)")
    Tokens --> Check{tokens at least 1?}
    Check -->|"yes"| Allow(decrement + allow)
    Check -->|"no"| Deny(deny with 429)

    class Req req
    class Stored base
    class Elapsed,Refill,Tokens,Check mathOp
    class Allow train
    class Deny lossN
```

**Q: How do you test a rate limiter reliably in pytest?**
Inject a mock Redis into `app_state.redis` before each test. Use `AsyncMock` with `eval` returning a controlled `[count, exceeded]` tuple. This avoids spinning up a real Redis in CI. For integration tests, use `fakeredis` (an in-process Redis emulator with Lua support) or Testcontainers with a real Redis image. Never rely on `time.sleep` loops in tests — they are flaky; instead mock `time.time` or use `freezegun`.

**Q: How would you add `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers to every successful response?**
Add an ASGI middleware that runs after the `RateLimiter` dependency has populated `request.state`. The middleware calls `await call_next(request)` and then inspects `request.state.rate_limit_minute` and `request.state.rate_limit_minute_limit` to compute `remaining = limit - used`. Set the headers on the `Response` object before returning. The middleware runs globally so all routes get the headers without per-route wiring.

**Q: What is the difference between HTTP 429 and HTTP 503?**
429 (Too Many Requests) means the client has exceeded the rate limit defined by the server's policy — the server is healthy, but this specific client is making requests too frequently. The `Retry-After` header tells the client exactly when it may retry. 503 (Service Unavailable) means the server itself is overloaded or temporarily down — it communicates nothing about the client's behavior. Returning 503 from a rate limiter is technically incorrect; 429 is the proper status code (RFC 6585).

**Q: Why use `RateLimiter` as a class with `__call__` rather than a plain async function?**
A class instance can hold configuration (`minute_limit`, `day_limit`) set at construction time. A plain function has a fixed signature; to parameterize it you need `functools.partial` or a closure factory, both of which are harder to read and introspect. `Depends(RateLimiter(minute_limit=10))` reads as a self-documenting declaration. FastAPI resolves callable instances exactly like coroutine functions.

**Q: If you need rate limiting at 50,000 req/s across 20 pods, does a single Redis instance hold up?**
Do the arithmetic before answering, and note that this design issues **two** `eval` calls per request — one for the minute window, one for the day window. 50,000 req/s is therefore 100,000 Redis operations/s, not 50,000. Redis's own published benchmark guidance puts a single node in the low hundreds of thousands of simple ops/s on commodity hardware without pipelining, so 100,000 evals/s is plausible but leaves little headroom and must be measured on your own hardware with `redis-benchmark` before you commit to it. Cheap fixes first: collapse the two windows into one script (needs a hash tag so both keys share a slot), or check the day window only on a sampled fraction of requests. Beyond that, shard by API key across a Redis Cluster so the same key always lands on the same shard, and keep every script single-key so it never spans slots.

**Q: How would you handle a Redis Cluster where Lua scripts cannot span multiple keys?**
Each rate-limit key (`rate::{api_key}::minute::...`) is a single key, not a multi-key operation, so there is no cross-slot problem. If you needed to atomically update two keys (e.g., minute key and day key in one script), you would have to use Redis hash tags — `{api_key}` — to force both keys onto the same slot. The current design runs two independent Lua scripts (one per window) and accepts that they are not atomically linked; the practical risk (one window passes, the other fails, leaving a partial increment) is acceptable because each window's counter is independently consistent.
