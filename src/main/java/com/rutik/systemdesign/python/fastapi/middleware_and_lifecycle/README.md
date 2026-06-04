# Middleware and Lifecycle

---

## 1. Concept Overview

FastAPI (built on Starlette) exposes two complementary extension points for cross-cutting concerns:
**middleware** and the **application lifecycle**.

Middleware intercepts every HTTP request and response that flows through the application. It runs
before routing, before dependency injection, and before any route handler executes. Each middleware
wraps the entire ASGI app as a callable layer — forming a chain of responsibility where each layer
has the chance to inspect, modify, short-circuit, or augment the request and response.

The application lifecycle controls what happens when the ASGI server starts and stops: database
connection pools must be created before any request arrives, Redis clients must be initialized,
machine learning models must be loaded into GPU memory, and all of these resources must be cleanly
released when the process exits.

Key capabilities covered in this module:

- ASGI middleware stack: wrap order, execution order, `scope`/`receive`/`send` call chain
- `app.middleware("http")` decorator for simple cases
- `BaseHTTPMiddleware` (Starlette) for structured middleware with `Request`/`Response` objects
- Pure ASGI middleware for zero-overhead use cases
- `add_middleware()` ordering — bottom-up execution (last-added = outermost = first on request)
- Built-in middleware: `CORSMiddleware`, `GZipMiddleware`, `TrustedHostMiddleware`, `HTTPSRedirectMiddleware`
- `lifespan` async context manager [FastAPI 0.93+] replacing `@app.on_event`
- `Request.state` for passing data from middleware to route handlers
- `BackgroundTasks` in routes vs middleware
- Session middleware and CORS deep dive: preflight, `allow_origins`, `allow_credentials`
- Exception handler ordering relative to middleware

Python version: 3.11/3.12. FastAPI version: 0.93+. Starlette version: 0.20+.

Cross-references:
- ASGI fundamentals: `../fastapi_fundamentals_asgi/README.md`
- Dependency injection and yield dependencies: `../dependency_injection_in_fastapi/README.md`
- REST API design principles: `../../../hld/case_studies/`

---

## 2. Intuition

> A FastAPI application is like a series of airport security checkpoints before you reach the gate.
> Every passenger (request) passes through each checkpoint in sequence — luggage scanning,
> passport control, boarding pass check — before reaching the plane (route handler). On the way out
> (response), the checkpoints run in reverse order. The gate is the destination; the checkpoints are
> middleware.

**Mental model.** Imagine each middleware as a Russian nesting doll (matryoshka). When you call
`add_middleware(A)` then `add_middleware(B)`, the app becomes `B(A(app))`. On an incoming request,
the outermost doll (B) opens first. When yielding to the inner layer, A opens. Finally the real app
runs. The response travels outward: A processes the response first, then B. The last middleware
added runs first on the request — a counter-intuitive but consistent rule.

**Why it matters.** Without middleware, cross-cutting concerns — request timing, correlation IDs,
authentication tokens, response compression, CORS headers — must be duplicated in every route
handler or dependency. Middleware centralizes these concerns in exactly one place, runs them for
every request unconditionally, and keeps route handlers focused on business logic.

**Key insight.** ASGI middleware operates at the transport layer, below FastAPI's routing,
validation, and dependency injection. This makes it the right place for concerns that must run
regardless of whether the route exists (404s still need CORS headers, timing, and correlation IDs).
It is the wrong place for concerns that need access to the parsed request body or dependency-resolved
objects — use dependencies for those.

---

## 3. Core Principles

**1. The ASGI contract.** Every ASGI middleware must accept `(scope, receive, send)` and call the
inner app with the same or modified versions of those arguments. Forgetting `await
app(scope, receive, send)` breaks the chain entirely — the client receives no response.

**2. Wrap order equals execution order inversion.** Middleware added last wraps the app outermost.
Outermost middleware executes first on request and last on response. The mnemonic: think of a stack
of pancakes — the last one placed is on top and is eaten first.

**3. Middleware runs before routing.** The router, path matching, and dependency injection are all
inside the innermost app. Middleware cannot access `request.path_params`, the matched route, or
resolved dependencies because none of that has happened yet.

**4. `Request.state` is the message bus between layers.** Middleware that needs to pass data to a
route handler (e.g., a decoded user ID from a JWT) attaches it to `request.state.user_id`. The
route handler reads it via `request.state`. No global variables, no thread-locals.

**5. Lifecycle brackets all requests.** Startup runs once before the first request; shutdown runs
once after the last request completes. Any resource initialized in startup is available for the
entire lifetime of the process. Use `lifespan` over `@app.on_event` — the latter is deprecated as
of FastAPI 0.93+.

**6. Exception handlers are not middleware.** `@app.exception_handler` hooks into Starlette's
exception handler chain, which runs inside the routing layer. Middleware wrapping the whole app can
catch exceptions that bubble past all exception handlers. Order matters: uncaught exceptions from
middleware do not reach `@app.exception_handler`.

---

## 4. Types / Architectures / Strategies

### 4.1 `@app.middleware("http")` decorator

The simplest form. FastAPI converts the decorated async function into a `BaseHTTPMiddleware` under
the hood. Suitable for logging, timing, and header injection. Not suitable for streaming responses
(see §10 for the double-buffering pitfall).

```python
@app.middleware("http")
async def timing_middleware(request: Request, call_next: Callable) -> Response:
    start = time.perf_counter()
    response = await call_next(request)
    elapsed = (time.perf_counter() - start) * 1000
    response.headers["X-Process-Time-Ms"] = f"{elapsed:.2f}"
    return response
```

### 4.2 `BaseHTTPMiddleware` class [Starlette 0.12+]

Subclasses `starlette.middleware.base.BaseHTTPMiddleware` and overrides `dispatch`. Provides full
access to `Request` and `Response` objects. Same double-buffering limitation as the decorator form
for streaming responses.

```python
from starlette.middleware.base import BaseHTTPMiddleware

class CorrelationIDMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        correlation_id = request.headers.get("X-Correlation-ID", str(uuid.uuid4()))
        request.state.correlation_id = correlation_id
        response = await call_next(request)
        response.headers["X-Correlation-ID"] = correlation_id
        return response
```

### 4.3 Pure ASGI middleware

Operates directly on `scope`, `receive`, `send`. No `Request`/`Response` abstraction overhead.
Approximately 20-30 µs faster per request than `BaseHTTPMiddleware` on a fast path because it
avoids the double-wrapping of the receive and send callables. Required for streaming responses where
you must not buffer the body.

```python
class RawTimingMiddleware:
    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(
        self, scope: Scope, receive: Receive, send: Send
    ) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        start = time.perf_counter()

        async def send_with_timing(message: Message) -> None:
            if message["type"] == "http.response.start":
                elapsed = (time.perf_counter() - start) * 1000
                headers = MutableHeaders(scope=message)
                headers.append("X-Process-Time-Ms", f"{elapsed:.2f}")
            await send(message)

        await self.app(scope, receive, send_with_timing)
```

### 4.4 Built-in Starlette / FastAPI middleware

| Middleware | Import | Primary Use |
|---|---|---|
| `CORSMiddleware` | `starlette.middleware.cors` | Cross-origin resource sharing |
| `GZipMiddleware` | `starlette.middleware.gzip` | Response compression (saves 70-80% on JSON >1 KB) |
| `TrustedHostMiddleware` | `starlette.middleware.trustedhost` | Reject requests with bad Host headers |
| `HTTPSRedirectMiddleware` | `starlette.middleware.httpsredirect` | 301 redirect HTTP to HTTPS |
| `SessionMiddleware` | `starlette.middleware.sessions` | Cookie-backed signed sessions |

### 4.5 `lifespan` context manager [FastAPI 0.93+]

An `asynccontextmanager` function passed to `FastAPI(lifespan=...)`. Code before `yield` runs at
startup; code after `yield` runs at shutdown. Resources are stored on `app.state` and accessible
via `request.app.state` in route handlers.

---

## 5. Architecture Diagrams

### Request / Response flow through the middleware stack

```
HTTP Request arrives at Uvicorn
           |
           v
  +-------------------+   add_middleware called LAST (outermost)
  |  CORSMiddleware   |   Runs FIRST on request, LAST on response
  +-------------------+
           |
           v
  +----------------------------+
  |  CorrelationIDMiddleware   |   add_middleware called second-to-last
  +----------------------------+
           |
           v
  +------------------------+
  |  TimingMiddleware      |   add_middleware called first (innermost after app)
  +------------------------+
           |
           v
  +------------------------+
  |   Starlette Router     |   Route matching, path param extraction
  +------------------------+
           |
           v
  +----------------------------+
  |  Exception Handler chain   |   @app.exception_handler hooks here
  +----------------------------+
           |
           v
  +----------------------------+
  |  Dependency Injection DAG  |   Depends() resolved here
  +----------------------------+
           |
           v
  +------------------------+
  |   Route Handler        |   Business logic runs here
  +------------------------+
           |
           v
        Response travels back UP through the same stack in reverse
```

### Lifespan timeline

```
Process start
    |
    v
[lifespan startup block executes]
    |  - DB pool created
    |  - Redis client connected
    |  - ML model loaded (e.g., 2.3 GB VRAM)
    v
[yield — app is now serving requests]
    |
    |  Request 1 --> Route handler reads app.state.db
    |  Request 2 --> Route handler reads app.state.redis
    |  ...
    v
[SIGTERM received — Uvicorn drains connections]
    |
    v
[lifespan shutdown block executes]
    |  - DB pool closed (all connections returned)
    |  - Redis connection closed
    v
Process exit
```

### CORS preflight sequence

```
Browser (origin: app.example.com)        Server (api.example.com)
         |                                         |
         |  OPTIONS /api/items                     |
         |  Origin: app.example.com                |
         |  Access-Control-Request-Method: POST    |
         |  Access-Control-Request-Headers: Auth   |
         | --------- preflight request ----------> |
         |                                         |
         |  200 OK (empty body, ~1 ms overhead)    |
         |  Access-Control-Allow-Origin: ...       |
         |  Access-Control-Allow-Methods: POST     |
         |  Access-Control-Max-Age: 600            |
         | <-------- preflight response ---------- |
         |                                         |
         |  POST /api/items                        |
         |  Origin: app.example.com                |
         |  Authorization: Bearer <token>          |
         | --------- actual request ------------> |
         |                                         |
         |  201 Created                            |
         |  Access-Control-Allow-Origin: ...       |
         | <-------- actual response ------------ |
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 `add_middleware` ordering and the matryoshka wrap

```python
from fastapi import FastAPI, Request
from starlette.middleware.cors import CORSMiddleware
from starlette.middleware.gzip import GZipMiddleware
import time
import uuid

app = FastAPI()

# Registration order — IMPORTANT: last registered = outermost = runs first on request
app.add_middleware(GZipMiddleware, minimum_size=1000)       # wraps app first (innermost)
app.add_middleware(CorrelationIDMiddleware)                 # wraps GZip(app)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://app.example.com"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)  # wraps Correlation(GZip(app)) — outermost, runs first

# Effective chain: CORSMiddleware -> CorrelationIDMiddleware -> GZipMiddleware -> Router
```

### 6.2 Request timing middleware with concrete numbers

```python
import time
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.types import ASGIApp


class TimingMiddleware(BaseHTTPMiddleware):
    """Adds X-Process-Time-Ms header. BaseHTTPMiddleware adds ~20-30 µs overhead."""

    def __init__(self, app: ASGIApp, precision: int = 2) -> None:
        super().__init__(app)
        self.precision = precision

    async def dispatch(self, request: Request, call_next) -> Response:
        t0 = time.perf_counter()
        response = await call_next(request)
        elapsed_ms = (time.perf_counter() - t0) * 1_000
        response.headers["X-Process-Time-Ms"] = f"{elapsed_ms:.{self.precision}f}"
        return response
```

### 6.3 Correlation ID middleware with `Request.state`

```python
import uuid
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import Response


class CorrelationIDMiddleware(BaseHTTPMiddleware):
    HEADER = "X-Correlation-ID"

    async def dispatch(self, request: Request, call_next) -> Response:
        # Accept from client or generate a new one
        cid = request.headers.get(self.HEADER) or str(uuid.uuid4())
        # Attach to request state — readable by route handlers and dependencies
        request.state.correlation_id = cid
        response = await call_next(request)
        response.headers[self.HEADER] = cid
        return response


# In a route handler:
@app.get("/items")
async def list_items(request: Request) -> dict:
    cid = request.state.correlation_id  # available because middleware ran first
    return {"correlation_id": cid, "items": []}
```

### 6.4 `lifespan` context manager [FastAPI 0.93+]

```python
from contextlib import asynccontextmanager
from typing import AsyncGenerator
from fastapi import FastAPI
import asyncpg
import redis.asyncio as aioredis


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    # --- Startup ---
    app.state.db_pool = await asyncpg.create_pool(
        dsn="postgresql://user:pass@localhost/mydb",
        min_size=5,
        max_size=20,
    )
    app.state.redis = await aioredis.from_url("redis://localhost:6379", decode_responses=True)
    print("Resources initialized")

    yield  # Application serves requests here

    # --- Shutdown ---
    await app.state.db_pool.close()
    await app.state.redis.aclose()
    print("Resources released")


app = FastAPI(lifespan=lifespan)


@app.get("/users/{user_id}")
async def get_user(user_id: int, request: Request) -> dict:
    pool: asyncpg.Pool = request.app.state.db_pool
    row = await pool.fetchrow("SELECT id, email FROM users WHERE id = $1", user_id)
    if row is None:
        raise HTTPException(status_code=404, detail="User not found")
    return dict(row)
```

### 6.5 Pure ASGI middleware (zero double-buffering)

```python
from starlette.types import ASGIApp, Scope, Receive, Send, Message
from starlette.datastructures import MutableHeaders


class SecurityHeadersMiddleware:
    """Pure ASGI — no BaseHTTPMiddleware overhead, safe for streaming responses."""

    HEADERS = {
        "X-Content-Type-Options": "nosniff",
        "X-Frame-Options": "DENY",
        "Strict-Transport-Security": "max-age=63072000; includeSubDomains",
        "Referrer-Policy": "strict-origin-when-cross-origin",
    }

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        async def send_with_headers(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = MutableHeaders(scope=message)
                for name, value in self.HEADERS.items():
                    headers.append(name, value)
            await send(message)

        await self.app(scope, receive, send_with_headers)


app.add_middleware(SecurityHeadersMiddleware)
```

### 6.6 CORS configuration

```python
from starlette.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://app.example.com", "https://admin.example.com"],
    allow_origin_regex=r"https://.*\.example\.com",  # wildcard subdomain
    allow_credentials=True,          # sends Access-Control-Allow-Credentials: true
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Correlation-ID"],
    expose_headers=["X-Process-Time-Ms", "X-Correlation-ID"],
    max_age=600,                     # preflight cached for 10 minutes; default 600 s
)
# Preflight (OPTIONS) responses add ~1 ms RTT for cross-origin requests.
# max_age=600 means the browser reuses the preflight result for 10 minutes,
# cutting the per-request CORS overhead to near zero for repeat callers.
```

### 6.7 Exception handler vs middleware exception handling

```python
from fastapi import HTTPException, Request
from fastapi.responses import JSONResponse


# This runs INSIDE the routing layer — catches HTTPException from route handlers
@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={"detail": exc.detail, "correlation_id": getattr(request.state, "correlation_id", None)},
    )


# Middleware can catch exceptions that escape ALL exception handlers
class UnhandledExceptionMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        try:
            return await call_next(request)
        except Exception as exc:
            # Log the full traceback here before returning 500
            import logging
            logging.exception("Unhandled exception for %s %s", request.method, request.url)
            return JSONResponse(status_code=500, content={"detail": "Internal server error"})
```

---

## 7. Real-World Examples

**Stripe.** Every inbound webhook request passes through middleware that verifies the
`Stripe-Signature` HMAC header against the raw request body. The middleware short-circuits with 400
if the signature is invalid, before any route handler runs. Raw body access requires reading
`receive` before passing it to the inner app — a pure ASGI middleware pattern, not `BaseHTTPMiddleware`
(which consumes `receive` once and cannot provide the raw bytes to the route handler afterward).

**Notion.** Multi-tenant APIs use middleware to extract the `X-Workspace-ID` header and set
`request.state.workspace_id`. Every downstream database query then automatically scopes to that
workspace. The lifespan block initializes a per-process connection pool pointing at a read-replica
for GET requests and the primary for mutations — that decision is made once at startup, not per
request.

**OpenAI API gateway.** `GZipMiddleware` with `minimum_size=1000` compresses streaming JSON
responses (token-by-token SSE). On a 10 KB JSON payload, gzip achieves 70-80% compression,
reducing bandwidth from ~10 KB to ~2 KB. For token streams, chunked transfer encoding interacts
with GZip buffering — the Starlette GZipMiddleware correctly buffers only when the full response
is available, passing through streaming responses untouched.

**Cloudflare Workers (Python ASGI target).** The `TrustedHostMiddleware` with
`allowed_hosts=["api.example.com", "*.example.com"]` ensures requests that bypass the CDN and
hit the origin directly are rejected with 400. This prevents host-header injection attacks where
an attacker forges the `Host:` header to manipulate URL generation or routing logic.

**Internal ML serving platform.** The lifespan block loads a 7B-parameter model into GPU memory
(takes ~8 seconds on an A10G GPU). Without lifespan, a naive `@app.on_event("startup")` would
fail silently if the GPU was unavailable at startup — the exception would be swallowed. The
lifespan `asynccontextmanager` propagates startup exceptions, causing Uvicorn to exit with a
non-zero code, triggering a Kubernetes `CrashLoopBackOff` and alerting on-call.

---

## 8. Tradeoffs

### Middleware approach comparison

| Approach | Overhead | Streaming safe | Complexity | Access to Request obj |
|---|---|---|---|---|
| `@app.middleware("http")` | ~20-30 µs | No (buffers body) | Low | Yes |
| `BaseHTTPMiddleware` subclass | ~20-30 µs | No (buffers body) | Medium | Yes |
| Pure ASGI middleware | ~1-2 µs | Yes | High | No (raw scope only) |

### Lifecycle approach comparison

| Approach | Status | Startup exception handling | Type safety | Test-ability |
|---|---|---|---|---|
| `@app.on_event("startup")` | Deprecated (0.93+) | Swallowed in some Uvicorn versions | Weak | Hard to mock |
| `lifespan` + `asynccontextmanager` | Recommended | Propagated — crashes process cleanly | Strong | Easy via `app.state` injection |
| `dependency_overrides` in tests | Test-only | N/A | Strong | Designed for this |

### CORS tradeoffs

| Setting | Permissive value | Secure value | Risk of permissive |
|---|---|---|---|
| `allow_origins` | `["*"]` | explicit list | Credential theft if combined with `allow_credentials=True` |
| `allow_credentials` | `True` | `False` | Cannot use `allow_origins=["*"]` with credentials |
| `max_age` | `86400` (24 h) | `600` (10 min) | Stale CORS policy cached in browser after policy changes |
| `allow_methods` | `["*"]` | explicit list | Allows DELETE/PATCH from unexpected origins |

---

## 9. When to Use / When NOT to Use

### Use middleware when:

- The concern applies to **every** request, including 404s and 500s (timing, CORS, security headers,
  correlation IDs)
- You need to act **before routing** (host validation, IP allowlisting, rate limiting by IP)
- You need to act on **every response** regardless of which route handler generated it (response
  compression, adding standard headers)
- You need to catch exceptions that escape all route-level exception handlers

### Do NOT use middleware when:

- The concern only applies to **some routes** — use a dependency on those routes instead
- You need access to **parsed request body** or **Pydantic-validated models** — the body is a
  stream; middleware that reads it prevents the route handler from reading it again unless you
  re-wrap the receive callable (complex and error-prone)
- You need **route-level context** such as `path_params`, the matched `APIRoute`, or a
  dependency-resolved object — none of these exist yet when middleware runs
- You are writing **per-user auth logic** — put it in a `Security()` dependency so it is visible
  in OpenAPI and testable in isolation

### Use `lifespan` when:

- Initializing database connection pools, cache clients, HTTP session pools
- Loading large artifacts (ML models, configuration files, compiled regex patterns)
- Starting background tasks that must run for the lifetime of the process
- Registering cleanup callbacks (closing pools, flushing log buffers)

### Do NOT use `lifespan` for:

- Per-request setup/teardown — use `yield` dependencies for that
- Configuration that should reload without a process restart — use `@RefreshScope`-style patterns
  with a background thread watching config files

---

## 10. Common Pitfalls

### Pitfall 1: Using deprecated `@app.on_event` instead of `lifespan`

```python
# BROKEN: deprecated since FastAPI 0.93; startup exceptions may be swallowed
@app.on_event("startup")
async def startup() -> None:
    app.state.db = await asyncpg.create_pool(dsn=settings.database_url)

@app.on_event("shutdown")
async def shutdown() -> None:
    await app.state.db.close()

# Problems:
# 1. Deprecated — will be removed in a future version.
# 2. Startup and shutdown logic is split across two functions with no guarantee
#    that shutdown runs if startup raises an exception midway.
# 3. In some Uvicorn + pytest combinations, the shutdown hook is never called,
#    leaking connections in CI.
```

```python
# FIX: use lifespan [FastAPI 0.93+]
from contextlib import asynccontextmanager
from typing import AsyncGenerator
import asyncpg
from fastapi import FastAPI


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    # Startup — exception here aborts startup cleanly; process exits non-zero
    app.state.db = await asyncpg.create_pool(dsn=settings.database_url)
    try:
        yield  # app serves requests
    finally:
        # Shutdown always runs, even if an exception occurred during serving
        await app.state.db.close()


app = FastAPI(lifespan=lifespan)
# Benefits:
# 1. Single cohesive block — startup and shutdown logic together.
# 2. `finally` ensures cleanup runs even if an unhandled exception occurs.
# 3. Startup exceptions propagate — Uvicorn exits with code 1; Kubernetes restarts.
# 4. Works correctly with httpx.AsyncClient / TestClient in pytest.
```

---

### Pitfall 2: `BaseHTTPMiddleware` double-buffering breaks streaming responses

```python
# BROKEN: BaseHTTPMiddleware (and @app.middleware) buffer the entire response body.
# For a StreamingResponse that yields token-by-token, call_next() does not return
# until the generator is exhausted, defeating the purpose of streaming.

from starlette.middleware.base import BaseHTTPMiddleware

class BrokenStreamingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        response = await call_next(request)
        # call_next() here waits for ALL chunks from StreamingResponse to be produced
        # before this line is reached — the client sees no tokens until the model finishes.
        response.headers["X-Done"] = "true"
        return response

# Symptom: LLM token stream that should appear word-by-word arrives all at once
# after a 30-second wait. Memory usage spikes because the full response is buffered.
```

```python
# FIX: use pure ASGI middleware — intercept send() directly, never buffer the body

from starlette.types import ASGIApp, Scope, Receive, Send, Message
from starlette.datastructures import MutableHeaders


class StreamingSafeHeaderMiddleware:
    """Appends a header to every response without buffering the body."""

    def __init__(self, app: ASGIApp, header_name: str, header_value: str) -> None:
        self.app = app
        self.header_name = header_name
        self.header_value = header_value

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        async def patched_send(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = MutableHeaders(scope=message)
                headers.append(self.header_name, self.header_value)
            await send(message)

        await self.app(scope, receive, patched_send)

# The send() hook fires once per chunk — the client receives each chunk
# immediately without waiting for the full response body to be produced.
```

---

### Pitfall 3: `add_middleware` order confusion

```python
# BROKEN: developer expects TimingMiddleware to measure the time INCLUDING
# CORS processing, but added it last (making it outermost — runs first)

app.add_middleware(CORSMiddleware, allow_origins=["*"])
app.add_middleware(TimingMiddleware)  # LAST added = OUTERMOST = runs before CORS

# Effective chain: TimingMiddleware -> CORSMiddleware -> Router
# Timer starts BEFORE CORS runs — but developer wanted to time only the business logic.
# Also, if CORS rejects the request (preflight), TimingMiddleware still records time.
```

```python
# FIX: add outermost-intended middleware LAST

app.add_middleware(TimingMiddleware)    # add FIRST -> innermost -> closest to route handler
app.add_middleware(CORSMiddleware, allow_origins=["*"])  # add LAST -> outermost -> runs first

# Effective chain: CORSMiddleware -> TimingMiddleware -> Router
# CORS preflight is handled and rejected before timing starts for non-preflight requests.
# Timing now measures only requests that pass CORS.
```

---

### Pitfall 4: Reading request body in middleware leaks the stream

```python
# BROKEN: reading request.body() in BaseHTTPMiddleware consumes the async stream.
# The route handler then reads an empty body.

class BrokenBodyLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        body = await request.body()       # consumes the receive stream
        print(f"Request body: {body}")
        response = await call_next(request)  # route handler gets empty body
        return response
```

```python
# FIX: cache the body bytes and re-wrap the receive callable

class BodyLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        body = await request.body()  # consumes stream once
        # Re-inject body bytes so the route handler can read them
        async def receive_override():
            return {"type": "http.request", "body": body, "more_body": False}

        request._receive = receive_override  # type: ignore[assignment]
        print(f"Request body ({len(body)} bytes): {body[:200]}")
        return await call_next(request)
# Note: body logging in production middleware is a security risk (credentials in bodies).
# Prefer logging only metadata (URL, method, content-type, content-length).
```

---

## 11. Technologies & Tools

| Tool / Library | Role | Key Feature | Limitation |
|---|---|---|---|
| Starlette `BaseHTTPMiddleware` | Structured middleware base class | `Request`/`Response` objects; simple `dispatch` override | Buffers response body — not safe for streaming |
| Starlette pure ASGI | Low-level middleware | No buffering; direct `scope/receive/send` access | Verbose; no `Request` abstraction |
| `CORSMiddleware` (Starlette) | CORS header injection | Handles preflight automatically; `allow_origin_regex` | Must be outermost to work correctly |
| `GZipMiddleware` (Starlette) | Response compression | 70-80% size reduction on JSON >1 KB; `minimum_size` threshold | Adds latency for small payloads (<1 KB); skip for SSE/WebSocket |
| `slowapi` | Rate limiting middleware | Redis-backed; `@limiter.limit("100/minute")` per route | Not built-in; adds Redis dependency |
| `starlette-csrf` | CSRF protection | Cookie + header double-submit pattern | Must coordinate with SPA frameworks |
| `asgi-correlation-id` | Correlation ID propagation | Structlog integration; UUID v4 auto-generation | Third-party library; audit before production use |

---

## 12. Interview Questions with Answers

**Q1: What is the execution order of middleware in FastAPI when you call `add_middleware` three times?**
The last middleware added is the outermost layer and executes first on the request. If you call
`add_middleware(A)`, `add_middleware(B)`, `add_middleware(C)`, the effective wrap is `C(B(A(app)))`.
On a request: C runs first, then B, then A, then the router. On a response: A processes first,
then B, then C. The mnemonic is a stack — last in, first out on the request side. Always trace
the `add_middleware` call order carefully when debugging middleware interactions.

**Q2: Why does `BaseHTTPMiddleware` break streaming responses, and what is the fix?**
`BaseHTTPMiddleware.dispatch()` calls `call_next(request)` which returns only after all response
chunks have been collected — the framework buffers the entire body before returning a `Response`
object. For `StreamingResponse` or SSE, this means the client waits until the generator is
exhausted, defeating the purpose of streaming. The fix is to write a pure ASGI middleware that
intercepts the `send` callable directly — it fires once per chunk and passes each chunk downstream
immediately without buffering.

**Q3: How do you pass data from middleware to a route handler?**
Attach data to `request.state` in middleware (e.g., `request.state.user_id = decoded_jwt["sub"]`).
The route handler reads it via `request: Request` parameter as `request.state.user_id`. Starlette's
`State` is a simple attribute bag scoped to the request lifetime. Avoid thread-locals or module-level
globals — they break under async concurrency and multiple worker processes.

**Q4: What is the difference between `@app.exception_handler` and a try/except in middleware?**
`@app.exception_handler` hooks into Starlette's exception handling chain, which runs inside the
routing layer after middleware. It only catches exceptions raised by route handlers and inner
exception handlers. Middleware wraps the entire routing layer, so a try/except in middleware can
catch exceptions that escape all `@app.exception_handler` registrations — including ones raised by
other middleware layers below it. For a global last-resort 500 handler, use middleware; for HTTP
semantics tied to specific routes, use `@app.exception_handler`.

**Q5: Why was `@app.on_event("startup")` deprecated in favor of `lifespan`?**
Three reasons: (1) `on_event` splits startup and shutdown into separate functions, making it easy
to forget cleanup if startup raises partway through; (2) exceptions in `on_event("startup")`
were silently swallowed in some Uvicorn/pytest combinations; (3) `lifespan` uses the standard
`asynccontextmanager` pattern — its `try/finally` guarantees cleanup always runs. The ASGI
lifespan spec also defines a standard protocol that servers implement, making `lifespan` more
portable than framework-specific event hooks.

**Q6: How do you initialize a database connection pool at startup and make it available to all route handlers?**
Use the `lifespan` context manager: create the pool before `yield` and assign it to `app.state.db`.
Route handlers access it via `request.app.state.db`. Alternatively, define a dependency
`async def get_db(request: Request)` that returns `request.app.state.db` — this makes the pool
injectable through the standard `Depends()` mechanism, which is easier to mock in tests via
`dependency_overrides`.

**Q7: What does `allow_credentials=True` in CORSMiddleware do, and why can it not be combined with `allow_origins=["*"]`?**
`allow_credentials=True` causes CORSMiddleware to add `Access-Control-Allow-Credentials: true`
to responses, which instructs browsers to include cookies and Authorization headers in
cross-origin requests. The CORS specification forbids the wildcard `"*"` value for
`Access-Control-Allow-Origin` when credentials are enabled — the browser requires a specific
origin to be reflected. Combining both is a browser-enforced security restriction to prevent
credential theft by malicious origins. Always specify an explicit origin list when using
`allow_credentials=True`.

**Q8: What is the overhead of `BaseHTTPMiddleware` compared to pure ASGI middleware?**
`BaseHTTPMiddleware` wraps the `receive` and `send` callables twice — once internally to collect
the response body and once to present a `Response` object to `dispatch()`. This adds approximately
20-30 µs of overhead per request on fast paths. For a service handling 10,000 req/s, that is
200-300 ms of cumulative CPU overhead per second. Pure ASGI middleware adds roughly 1-2 µs.
The difference is rarely the bottleneck (database queries dominate at 1-50 ms), but matters for
high-frequency internal services where P99 latency targets are tight.

**Q9: How do you write middleware that correctly handles both HTTP and WebSocket connections?**
Check `scope["type"]` at the start of `__call__`. For `"http"` connections, apply HTTP-specific
logic. For `"websocket"` connections, either pass through unchanged (`await self.app(scope, receive, send)`)
or apply WebSocket-specific logic. Always handle the `"lifespan"` type as a pass-through —
failing to do so prevents the lifespan startup/shutdown protocol from running.
`BaseHTTPMiddleware` handles this automatically; pure ASGI middleware requires explicit type checking.

**Q10: How do you test a FastAPI application that uses `lifespan` for startup?**
Use Starlette's `TestClient` or `httpx.AsyncClient` as an async context manager:
`async with AsyncClient(app=app, base_url="http://test") as client:` — entering the context
triggers lifespan startup; exiting triggers shutdown. For `pytest-asyncio`, use the
`@pytest.fixture(scope="module")` pattern with `async with AsyncClient(...)` to share a single
initialized app across all tests in a module, avoiding repeated startup/shutdown overhead.

**Q11: What is `Request.state` and what is its scope?**
`Request.state` is a `starlette.datastructures.State` instance — a simple object that accepts
arbitrary attribute assignments (`request.state.foo = "bar"`). It is created fresh for each
request and destroyed when the request completes. It is not shared between requests, not thread-safe
(because each async request has its own coroutine stack), and not accessible outside the request
lifecycle. Use it to pass middleware-set values (correlation IDs, authenticated user objects) to
route handlers without function-signature coupling.

**Q12: When should you use `BackgroundTasks` in a route handler vs a dedicated task queue?**
`BackgroundTasks` runs the task in the same Uvicorn worker process after the response is sent,
blocking that worker for the duration of the background work. It is appropriate for fast, non-critical
tasks: sending a confirmation email (50-200 ms), writing an audit log entry, or invalidating a
cache key. Use a dedicated task queue (Celery, ARQ, Dramatiq) when: the task takes longer than
1-2 seconds, failure must be retried with exponential backoff, tasks must survive process restarts,
or task volume exceeds what a single worker can handle. `BackgroundTasks` in middleware is an
anti-pattern — middleware's `call_next` must complete before the response is sent, so tasks
registered there block the response.

---

## 13. Best Practices

**Keep middleware lean.** Middleware runs on every single request. Even 1 µs of unnecessary
work at 10,000 req/s is 10 ms of wasted CPU per second. Avoid database calls, cache lookups,
or JSON parsing in middleware — those belong in dependencies.

**Use pure ASGI middleware for streaming routes.** Any route that returns `StreamingResponse`
or uses SSE must not be wrapped by `BaseHTTPMiddleware`. Audit your middleware stack when adding
streaming endpoints. Consider using pure ASGI middleware uniformly for performance-sensitive services.

**Register CORSMiddleware last (outermost).** CORS headers must be present on every response,
including 400s and 500s. If CORSMiddleware is not the outermost layer, an error response from
an inner middleware may be returned without CORS headers, causing the browser to report a CORS
error instead of the actual error, making debugging much harder.

**Always use `lifespan` over `@app.on_event`.** New code should never use `@app.on_event`. The
deprecation was intentional and the replacement is more correct. In existing codebases, migrate
proactively — the refactor is mechanical and takes under 10 minutes.

**Store shared resources on `app.state`, not module globals.** Module-level globals make testing
difficult (they persist between test runs), complicate multi-process deployments, and can cause
subtle race conditions during startup. `app.state` is attached to the specific `FastAPI` instance,
making it possible to create isolated instances in tests with `dependency_overrides`.

**Inject `request.state` values via a thin dependency.** Rather than having every route handler
import and read `request.state.correlation_id` directly, wrap it:
```python
def get_correlation_id(request: Request) -> str:
    return request.state.correlation_id
```
This makes the contract explicit in function signatures and is trivially testable.

**Set `max_age` on CORS preflight responses.** The default `max_age=600` (10 minutes) means
browsers re-issue the `OPTIONS` preflight once per 10 minutes per endpoint. For stable APIs in
production, `max_age=86400` (24 hours) further reduces OPTIONS round-trips. Reducing it during
active API changes prevents stale preflight responses from blocking deployments.

**Log structured metadata, never raw bodies.** Request body logging in middleware is tempting for
debugging but dangerous in production (passwords, tokens, PII). Log only: method, URL path,
status code, response time, correlation ID, content-type, and content-length. For selective body
logging, use a dedicated route-level dependency that is opt-in per route.

---

## 14. Case Study

### Scenario: Multi-tenant SaaS API with request tracing, rate limiting, and hot-reload-safe startup

A B2B SaaS company operates a FastAPI service that serves 40 enterprise tenants. Each tenant sends
up to 500 req/s. The requirements:

1. Every request must carry a correlation ID for distributed tracing.
2. Responses must include timing headers for SLA reporting.
3. CORS must allow tenant-specific frontend origins.
4. Security headers must be present on all responses.
5. Database connection pool and Redis client must be initialized once at startup and
   cleanly released on shutdown.
6. Startup failures must crash the process (not silently continue with no DB connection).

#### ASCII Architecture

```
Incoming Request
       |
       v
+---------------------------+  add_middleware LAST (outermost)
|   CORSMiddleware          |  preflight returns here; actual requests pass through
+---------------------------+
       |
       v
+-------------------------------+
|  SecurityHeadersMiddleware    |  pure ASGI — adds CSP, HSTS, X-Frame-Options
+-------------------------------+
       |
       v
+-------------------------------+
|  CorrelationIDMiddleware      |  injects or generates X-Correlation-ID
|  (BaseHTTPMiddleware)         |  sets request.state.correlation_id
+-------------------------------+
       |
       v
+---------------------------+  add_middleware FIRST (innermost non-app)
|   TimingMiddleware        |  measures time after CORS + security pass
+---------------------------+
       |
       v
+---------------------------+
|   FastAPI Router          |  route matching, path params
+---------------------------+
       |
       v
+---------------------------+
|   Depends() graph         |  DB session, tenant context, auth
+---------------------------+
       |
       v
+---------------------------+
|   Route Handler           |  business logic only
+---------------------------+
```

#### Implementation

```python
from __future__ import annotations

import time
import uuid
from contextlib import asynccontextmanager
from typing import AsyncGenerator

import asyncpg
import redis.asyncio as aioredis
from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.datastructures import MutableHeaders
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.middleware.gzip import GZipMiddleware
from starlette.types import ASGIApp, Message, Receive, Scope, Send

# ---------------------------------------------------------------------------
# 1. Lifespan — startup / shutdown
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Initialize shared resources. Exception here aborts startup — process exits non-zero."""
    app.state.db = await asyncpg.create_pool(
        dsn="postgresql://user:pass@db:5432/saas",
        min_size=5,
        max_size=20,
        command_timeout=30,
    )
    app.state.redis = await aioredis.from_url(
        "redis://redis:6379", decode_responses=True, max_connections=50
    )
    try:
        yield
    finally:
        await app.state.db.close()
        await app.state.redis.aclose()


# ---------------------------------------------------------------------------
# 2. Pure ASGI security headers middleware
# ---------------------------------------------------------------------------

class SecurityHeadersMiddleware:
    HEADERS = [
        ("X-Content-Type-Options", "nosniff"),
        ("X-Frame-Options", "DENY"),
        ("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload"),
        ("Referrer-Policy", "strict-origin-when-cross-origin"),
        ("Content-Security-Policy", "default-src 'self'"),
    ]

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        async def patched_send(message: Message) -> None:
            if message["type"] == "http.response.start":
                headers = MutableHeaders(scope=message)
                for name, value in self.HEADERS:
                    headers.append(name, value)
            await send(message)

        await self.app(scope, receive, patched_send)


# ---------------------------------------------------------------------------
# 3. Correlation ID middleware
# ---------------------------------------------------------------------------

class CorrelationIDMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> JSONResponse:
        cid = request.headers.get("X-Correlation-ID") or str(uuid.uuid4())
        request.state.correlation_id = cid
        response = await call_next(request)
        response.headers["X-Correlation-ID"] = cid
        return response


# ---------------------------------------------------------------------------
# 4. Timing middleware
# ---------------------------------------------------------------------------

class TimingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        t0 = time.perf_counter()
        response = await call_next(request)
        ms = (time.perf_counter() - t0) * 1_000
        response.headers["X-Process-Time-Ms"] = f"{ms:.2f}"
        return response


# ---------------------------------------------------------------------------
# 5. App assembly — middleware registration order matters
# ---------------------------------------------------------------------------

app = FastAPI(lifespan=lifespan)

# Innermost non-app layer — added first
app.add_middleware(TimingMiddleware)

# Middle layer
app.add_middleware(CorrelationIDMiddleware)
app.add_middleware(SecurityHeadersMiddleware)   # pure ASGI; use add_middleware too
app.add_middleware(GZipMiddleware, minimum_size=1000)

# Outermost — added last — handles preflight before anything else
app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://tenant-a.example.com", "https://tenant-b.example.com"],
    allow_origin_regex=r"https://.*\.example\.com",
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "DELETE"],
    allow_headers=["Authorization", "Content-Type", "X-Correlation-ID"],
    expose_headers=["X-Process-Time-Ms", "X-Correlation-ID"],
    max_age=600,
)


# ---------------------------------------------------------------------------
# 6. Exception handler — runs inside routing layer, after middleware
# ---------------------------------------------------------------------------

@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "detail": exc.detail,
            "correlation_id": getattr(request.state, "correlation_id", None),
        },
    )


# ---------------------------------------------------------------------------
# 7. Sample route — accesses app.state resources
# ---------------------------------------------------------------------------

@app.get("/tenants/{tenant_id}/users")
async def list_users(tenant_id: str, request: Request) -> dict:
    pool: asyncpg.Pool = request.app.state.db
    rows = await pool.fetch(
        "SELECT id, email FROM users WHERE tenant_id = $1 ORDER BY id LIMIT 100",
        tenant_id,
    )
    return {
        "tenant_id": tenant_id,
        "users": [dict(r) for r in rows],
        "correlation_id": request.state.correlation_id,
    }
```

#### BROKEN→FIX in the case study

```python
# BROKEN: SecurityHeadersMiddleware registered via add_middleware but defined as BaseHTTPMiddleware
# — will buffer streaming responses from any tenant route that returns StreamingResponse.

class BrokenSecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        response = await call_next(request)   # buffers entire body even for StreamingResponse
        response.headers["X-Frame-Options"] = "DENY"
        return response

# This causes tenant CSV export endpoints (StreamingResponse, multi-MB files) to block
# until the full CSV is generated before the first byte reaches the browser.
# Memory usage spikes to O(file size) per concurrent export.
```

```python
# FIX: rewrite as pure ASGI middleware — patches the send callable, never buffers
# (exact implementation shown in the SecurityHeadersMiddleware class above)
# Pure ASGI add_middleware still works:
app.add_middleware(SecurityHeadersMiddleware)
# The __call__ method fires per chunk; streaming responses are not buffered.
```

#### Discussion Questions

1. A new tenant's frontend is deployed at `https://tenant-c.example.com`. The CORS policy uses
   `allow_origin_regex`. What happens to their browser requests? What is the security risk of
   an overly permissive regex (e.g., `r"https://.*example\.com"`)?

2. The `asyncpg` pool `create_pool` call in `lifespan` raises `asyncpg.exceptions.ConnectionDoesNotExistError`
   because the database is not yet ready when the pod starts. The Kubernetes pod goes into
   `CrashLoopBackOff`. Is this the desired behavior? What is the alternative?

3. A developer adds a `BodyLoggingMiddleware` using `BaseHTTPMiddleware` that calls
   `await request.body()`. The `POST /tenants/{id}/users` endpoint suddenly returns empty
   `name` and `email` fields for all new users. What is the cause and fix?

4. The on-call engineer needs to reduce cold-start latency from 8 seconds (dominated by
   loading a 2 GB tenant-specific ML model in lifespan) to under 500 ms. What architectural
   change moves the model loading out of the lifespan block without losing the single-load guarantee?
