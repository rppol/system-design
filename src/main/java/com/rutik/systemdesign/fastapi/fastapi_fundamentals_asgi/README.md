# FastAPI Fundamentals & ASGI

---

## 1. Concept Overview

FastAPI is a modern, high-performance Python web framework built on top of Starlette (the ASGI toolkit)
and Pydantic (the data validation library). It generates OpenAPI documentation automatically and
enforces request/response contracts through Python type annotations alone.

ASGI (Asynchronous Server Gateway Interface) is the spiritual successor to WSGI, designed to serve
asynchronous Python web applications. Where WSGI defines a synchronous callable interface between
a web server and a Python application, ASGI defines an asynchronous, bidirectional interface that
supports HTTP, WebSocket, and Server-Sent Events from a single protocol.

Key capabilities covered in this module:

- The ASGI 3 protocol: `scope`, `receive`, `send`
- How Starlette wraps raw ASGI into a routing framework
- How FastAPI layers automatic validation and serialization on top of Starlette
- Uvicorn as the production-grade ASGI server
- The `lifespan` context manager for startup and shutdown resource management
- Path parameter types and how FastAPI resolves them via reflection
- `response_model` and response class hierarchy
- Auto-generated OpenAPI/Swagger at `/docs` and `/openapi.json`

Cross-references:
- For dependency injection on top of ASGI, see `../dependency_injection_in_fastapi/README.md`
- For REST API design principles behind these endpoints, see `../../../backend/rest_api_design/README.md`

---

## 2. Intuition

> WSGI is a post office counter — one clerk handles one customer at a time, and the line grows.
> ASGI is a single barista managing ten espresso machines simultaneously — while one shot pulls,
> another order is taken, and a finished drink is handed off, all without hiring more staff.

**Mental model:** An ASGI application is a coroutine factory. Uvicorn accepts a TCP connection,
parses bytes into an HTTP scope dictionary, then calls `await app(scope, receive, send)`. The
`receive` coroutine yields incoming events (request body chunks). The `send` coroutine accepts
outgoing events (response start, response body). FastAPI sits entirely above this two-coroutine
handshake; it never sees raw sockets.

**Why it matters:** Python's GIL prevents true CPU parallelism per process, but I/O-bound work —
database queries, external HTTP calls, file reads — releases the GIL. ASGI lets a single-threaded
event loop handle thousands of concurrent I/O-bound requests that would stall a WSGI server blocked
waiting on each query to return.

**Key insight:** Uvicorn's event loop and ASGI's `receive`/`send` model mean that HTTP/1.1 keep-alive
connections, WebSocket upgrades, and long-lived Server-Sent Event streams all share the same transport
abstraction. FastAPI exposes this through `WebSocket`, `StreamingResponse`, and `EventSourceResponse`
without any protocol-level changes on the developer's part.

---

## 3. Core Principles

**Protocol-first design.** ASGI is a specification, not a library. Any server that implements the
spec (Uvicorn, Hypercorn, Daphne) can run any ASGI application (Starlette, Django Channels, Quart)
without glue code.

**Type annotations as the source of truth.** FastAPI reads the type hints on every route function
using `inspect.signature()` and `typing.get_type_hints()` at application startup, not at request
time. This single pass produces: the Pydantic validation schema, the OpenAPI parameter definitions,
and the response serialization rules.

**Composition over configuration.** Starlette composes middleware as a stack of nested ASGI
callables. FastAPI adds routers, dependency injection, and serialization on top without replacing
the middleware mechanism. You can always drop down to raw Starlette or even raw ASGI when needed.

**Fail fast, fail loudly.** A missing required path parameter or a wrong type raises a `422
Unprocessable Entity` with a structured JSON body listing every validation error. The server never
silently accepts malformed input.

**Zero-cost abstractions for synchronous handlers.** FastAPI runs `def` (synchronous) route handlers
in a thread pool executor so they do not block the event loop. `async def` handlers run directly on
the loop. You pay for thread overhead only when you opt in to synchronous code.

---

## 4. Types / Architectures / Strategies

### ASGI vs WSGI Comparison

| Dimension           | WSGI                                              | ASGI                                              |
|---------------------|---------------------------------------------------|---------------------------------------------------|
| Interface           | sync callable `app(environ, start_response)`      | async callable `app(scope, receive, send)`        |
| Concurrency model   | one thread/process per request                    | single event loop, many concurrent requests       |
| WebSocket support   | not supported natively                            | first-class (`scope["type"] == "websocket"`)      |
| Long-polling / SSE  | requires gevent/eventlet monkey-patching          | native streaming via `send` events                |
| Servers             | gunicorn, uWSGI, mod_wsgi                         | uvicorn, hypercorn, daphne                        |
| Python version      | 2.7+                                              | 3.6+ (practical minimum 3.10)                     |
| Startup/shutdown    | no standard lifecycle hook                        | `lifespan` scope with startup/shutdown events     |
| Django support      | Django 1.0+ (primary interface)                   | Django 3.0+ (Channels or ASGI entry point)        |
| Flask support       | native                                            | Quart (async reimplementation)                    |

### Response Class Hierarchy

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    respBase("Response<br/>(base)")
    json("JSONResponse")
    html("HTMLResponse")
    plain("PlainTextResponse")
    redirect("RedirectResponse")
    file("FileResponse")
    stream("StreamingResponse")

    respBase -->|"default;<br/>orjson/json"| json
    respBase -->|"text/html"| html
    respBase -->|"text/plain"| plain
    respBase -->|"307/301/302"| redirect
    respBase -->|"disk + Range<br/>header"| file
    respBase -->|"async gen;<br/>SSE / large"| stream

    class respBase base
    class json,html,plain,redirect,file,stream io
```

*All response types inherit from Starlette's base `Response`; FastAPI defaults to `JSONResponse` for dict/`BaseModel` returns unless `response_class` or a different return type selects another (§6.6).*

### Path Parameter Type Resolution

FastAPI maps Python built-in types and Pydantic types to OpenAPI schema types:

| Python annotation        | OpenAPI type  | Validation rule                          |
|--------------------------|---------------|------------------------------------------|
| `int`                    | integer       | must be a valid integer                  |
| `float`                  | number        | must be a valid float                    |
| `str`                    | string        | any string                               |
| `bool`                   | boolean       | `true`/`false`/`1`/`0`                  |
| `UUID`                   | string/uuid   | must be a valid UUID4                    |
| `Annotated[int, Path(ge=1)]` | integer  | integer >= 1                             |
| Pydantic `BaseModel`     | object        | full JSON schema from model              |

---

## 5. Architecture Diagrams

### Full ASGI Request Flow

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    client(["Client<br/>browser / curl / httpx"])

    subgraph UV["Uvicorn"]
        loop("libuv / asyncio<br/>event loop")
        parser("HTTP parser h11:<br/>builds scope dict")
        loop --> parser
    end

    subgraph ST["Starlette"]
        mw("Middleware stack:<br/>CORS, GZip, etc.")
        router("Router:<br/>Radix-trie match")
        mw --> router
    end

    subgraph FA["FastAPI"]
        dep("Dependency solver:<br/>DI graph per request")
        val("Pydantic validator:<br/>body / path / query")
        handler("async route<br/>handler runs")
        dep --> handler
        val --> handler
    end

    subgraph RS["Response Layer"]
        serialize("Pydantic v2<br/>serialization")
        respClass(["JSONResponse or<br/>StreamingResponse"])
        serialize --> respClass
    end

    clientDone(["Client receives<br/>response bytes"])

    client -->|"TCP bytes<br/>HTTP/1.1 or HTTP/2"| UV
    UV -->|"await app(scope, receive, send)"| ST
    ST --> FA
    FA -->|"return value or<br/>raise HTTPException"| RS
    RS -->|"send(response.start),<br/>send(response.body)"| clientDone

    class client,clientDone,respClass io
    class loop req
    class parser,mw,router,dep,val,serialize mathOp
    class handler train
```

*Each layer wraps the next: Uvicorn parses raw bytes into the ASGI `scope` dict, Starlette resolves middleware and routing, and FastAPI's dependency/validation pass hands off to the handler before the response is serialized and streamed back over the same connection.*

### Starlette Middleware Stack

Each middleware is an ASGI callable that receives the inner app as a constructor argument
and wraps it. The outermost middleware is called first on the way in and last on the way out.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    reqIn(["incoming<br/>request"])
    trusted("TrustedHost<br/>Middleware")
    gzip("GZip<br/>Middleware")
    cors("CORS<br/>Middleware")
    router("FastAPI<br/>Router")
    respOut(["outgoing<br/>response"])

    reqIn --> trusted --> gzip --> cors --> router
    router -.->|"response"| cors
    cors -.->|"response"| gzip
    gzip -.->|"response"| trusted
    trusted -.->|"response"| respOut

    class reqIn,respOut io
    class trusted,gzip,cors mathOp
    class router base
```

*Outgoing response flows back through the same chain in reverse order — the middleware added last (innermost, closest to the router) is called first on the way out.*

### lifespan State Machine

```mermaid
stateDiagram-v2
    [*] --> Startup: process starts

    state "Lifespan Startup" as Startup
    Startup --> Running: yield db + model pool

    state "Running (serving HTTP)" as Running
    Running --> Shutdown: SIGTERM / SIGINT received

    state "Lifespan Shutdown" as Shutdown
    Shutdown --> [*]: process exits cleanly

    note right of Startup
        connect DB
        load ML model
        warm caches
    end note

    note right of Running
        request.state.db / .model<br/>available to every handler
    end note

    note right of Shutdown
        close DB pool
        flush buffers
        release GPU
    end note
```

*The `dict` yielded between startup and shutdown becomes `app.state` / `request.state` for the entire `Running` period; the shutdown half only runs on graceful termination (SIGTERM/SIGINT), not `SIGKILL` (see Pitfall 1).*

---

## 6. How It Works — Detailed Mechanics

### 6.1 The Raw ASGI 3 Interface

A minimal ASGI application that returns "Hello, ASGI" demonstrates the full protocol:

```python
# raw_asgi.py  — no FastAPI, no Starlette, pure ASGI 3
from collections.abc import Awaitable, Callable
from typing import Any

Scope = dict[str, Any]
Receive = Callable[[], Awaitable[dict[str, Any]]]
Send = Callable[[dict[str, Any]], Awaitable[None]]


async def app(scope: Scope, receive: Receive, send: Send) -> None:
    """Minimal ASGI 3 application."""
    assert scope["type"] == "http"

    # Drain the request body (required even if unused)
    event = await receive()
    while not event.get("more_body", False):
        break

    body = b"Hello, ASGI"
    await send(
        {
            "type": "http.response.start",
            "status": 200,
            "headers": [
                (b"content-type", b"text/plain"),
                (b"content-length", str(len(body)).encode()),
            ],
        }
    )
    await send(
        {
            "type": "http.response.body",
            "body": body,
            "more_body": False,
        }
    )
```

Run with: `uvicorn raw_asgi:app --port 8000`

### 6.2 The HTTP Scope Dictionary

Uvicorn populates this dict before calling your application:

```python
scope = {
    "type": "http",                       # "http" | "websocket" | "lifespan"
    "asgi": {"version": "3.0"},
    "http_version": "1.1",               # "1.0" | "1.1" | "2"
    "method": "GET",
    "headers": [                         # list of (name, value) byte tuples
        (b"host", b"api.example.com"),
        (b"accept", b"application/json"),
    ],
    "path": "/items/42",
    "raw_path": b"/items/42",
    "query_string": b"limit=10&offset=0",
    "root_path": "",                     # set when deployed behind a prefix
    "scheme": "https",
    "server": ("127.0.0.1", 8000),
    "client": ("10.0.0.5", 54321),
    "extensions": {},
    "state": {},                         # populated by lifespan (see below)
}
```

### 6.3 The lifespan Context Manager (FastAPI 0.110+)

```python
# main.py
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI, Request


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[dict]:
    """
    Runs at server startup (before first request) and on shutdown.
    The dict yielded becomes app.state and is accessible per-request
    via request.state.
    """
    # --- startup ---
    pool: asyncpg.Pool = await asyncpg.create_pool(
        dsn="postgresql://user:pass@localhost/mydb",
        min_size=5,
        max_size=20,
    )

    yield {"db": pool}         # everything in this dict is injected into app.state

    # --- shutdown (runs after yield when SIGTERM is received) ---
    await pool.close()


app = FastAPI(lifespan=lifespan)


@app.get("/users/{user_id}")
async def get_user(user_id: int, request: Request) -> dict:
    pool: asyncpg.Pool = request.state.db          # access pool from lifespan state
    row = await pool.fetchrow("SELECT * FROM users WHERE id = $1", user_id)
    if row is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="User not found")
    return dict(row)
```

### 6.4 Path Parameter Resolution via Reflection

FastAPI calls `inspect.signature()` and `typing.get_type_hints()` exactly once per route
at application startup (inside `__init__` of the router). The resolved signature drives both
the OpenAPI schema and the runtime validator:

```python
import inspect
import typing

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()


class ItemResponse(BaseModel):
    item_id: int
    name: str
    price: float


@app.get("/items/{item_id}", response_model=ItemResponse)
async def read_item(item_id: int, name: str = "default") -> ItemResponse:
    # FastAPI internally does roughly:
    #   sig = inspect.signature(read_item)
    #   hints = typing.get_type_hints(read_item)
    #   for param_name, param in sig.parameters.items():
    #       annotation = hints[param_name]
    #       if param_name in path_params:  -> PathField validator
    #       elif param.default is inspect.Parameter.empty: -> required query param
    #       else: -> optional query param with default
    return ItemResponse(item_id=item_id, name=name, price=9.99)
```

The route `/items/abc` returns `422` because `abc` fails `int` validation before the
handler function is ever called.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    sig("route signature<br/>inspected once<br/>at startup")
    inPath{"name appears as<br/>a path placeholder?"}
    pathP("path parameter:<br/>PathField validator")
    hasDefault{"parameter has<br/>a default value?"}
    reqQ("required<br/>query parameter")
    optQ("optional query<br/>parameter + default")

    sig --> inPath
    inPath -->|"yes"| pathP
    inPath -->|"no"| hasDefault
    hasDefault -->|"no"| reqQ
    hasDefault -->|"yes"| optQ

    class sig base
    class inPath,hasDefault mathOp
    class pathP,reqQ,optQ req
```

*This per-parameter classification runs once at startup, in the same reflection pass shown below — it decides whether Pydantic validates a value as a path segment, a required query key, or an optional one (the three-way split Q14 asks about).*

### 6.5 response_model and Field Filtering

```python
from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI()


class UserDB(BaseModel):
    id: int
    username: str
    hashed_password: str    # must NOT be sent to clients
    email: str


class UserPublic(BaseModel):
    id: int
    username: str
    email: str


# response_model=UserPublic causes FastAPI to serialize the return value
# through UserPublic, which excludes hashed_password automatically.
@app.get("/users/{user_id}", response_model=UserPublic)
async def get_user(user_id: int) -> UserDB:
    return UserDB(
        id=user_id,
        username="alice",
        hashed_password="$2b$12$...",
        email="alice@example.com",
    )
# The client receives: {"id": 1, "username": "alice", "email": "alice@example.com"}
# hashed_password is stripped by Pydantic during response_model serialization.
```

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    handler("handler returns<br/>UserDB instance")
    declared{"response_model<br/>declared?"}
    filter("model_validate +<br/>model_dump via UserPublic")
    safe(["client gets<br/>id, username, email"])
    leaked(["hashed_password<br/>would leak"])

    handler --> declared
    declared -->|"yes"| filter
    filter --> safe
    declared -.->|"no (see Pitfall 3)"| leaked

    class handler io
    class declared mathOp
    class filter mathOp
    class safe train
    class leaked lossN
```

*`response_model` runs every return value through `UserPublic.model_validate().model_dump()`, silently dropping any field the response model does not declare (Q6); skip the declaration entirely and the full internal model — `hashed_password` included — serializes as-is (Pitfall 3).*

### 6.6 Response Classes

```python
from pathlib import Path

from fastapi import FastAPI
from fastapi.responses import (
    FileResponse,
    HTMLResponse,
    JSONResponse,
    StreamingResponse,
)

app = FastAPI()


# JSONResponse — explicit; FastAPI uses this by default for dict/BaseModel returns
@app.get("/json")
async def json_endpoint() -> JSONResponse:
    return JSONResponse(content={"status": "ok"}, status_code=200)


# HTMLResponse — serve rendered templates or static HTML
@app.get("/health", response_class=HTMLResponse)
async def health() -> str:
    return "<html><body><p>OK</p></body></html>"


# FileResponse — streams a file with correct Content-Type and Range support
@app.get("/download/{filename}")
async def download(filename: str) -> FileResponse:
    return FileResponse(
        path=Path("/tmp") / filename,
        filename=filename,
        media_type="application/octet-stream",
    )


# StreamingResponse — async generator for Server-Sent Events or large bodies
async def event_generator():
    for i in range(5):
        yield f"data: tick {i}\n\n"


@app.get("/stream")
async def stream() -> StreamingResponse:
    return StreamingResponse(event_generator(), media_type="text/event-stream")
```

### 6.7 Auto-OpenAPI Customization

```python
from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(
    title="Inventory API",
    version="1.0.0",
    description="Manages warehouse inventory",
    openapi_tags=[
        {"name": "items", "description": "CRUD operations for items"},
        {"name": "health", "description": "Liveness and readiness probes"},
    ],
)


class Item(BaseModel):
    item_id: int = Field(..., ge=1, description="Unique item identifier", example=42)
    name: str = Field(..., min_length=1, max_length=128, example="Widget Pro")
    price: float = Field(..., gt=0, example=19.99)


@app.get(
    "/items/{item_id}",
    response_model=Item,
    summary="Fetch a single item",
    description="Returns item details by integer ID. Returns 404 if not found.",
    tags=["items"],
    responses={
        404: {"description": "Item not found"},
        422: {"description": "Validation error — item_id must be a positive integer"},
    },
)
async def read_item(item_id: int) -> Item:
    return Item(item_id=item_id, name="Widget Pro", price=19.99)
```

The full JSON schema is served at `GET /openapi.json`. Swagger UI at `/docs`.
ReDoc at `/redoc`.

### 6.8 Uvicorn Startup

```bash
# Development: single worker, hot reload
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1 --reload

# Production: multiple workers (use gunicorn as process manager)
gunicorn main:app \
    --worker-class uvicorn.workers.UvicornWorker \
    --workers 4 \
    --bind 0.0.0.0:8000 \
    --timeout 30 \
    --graceful-timeout 30
```

`--workers 1` is the correct default for async apps; multiple workers multiply memory
usage without improving concurrency for I/O-bound workloads unless CPU is the bottleneck.

---

## 7. Real-World Examples

**Stripe's internal tooling.** Stripe adopted FastAPI for several internal microservices after
benchmarking its throughput at roughly 3x Django REST Framework under async I/O workloads.
The auto-generated OpenAPI schema is fed into their internal SDK generator, eliminating manual
spec maintenance.

**Netflix Dispatch (incident management).** Uses FastAPI 0.100+ with Starlette lifespan to
manage startup of Celery task connections and SQLAlchemy async engines. The OpenAPI spec is
used to drive integration tests against a generated TypeScript client.

**CERN's accelerator data API.** Switched from a Tornado-based WSGI API to FastAPI for its
real-time streaming endpoints. `StreamingResponse` wrapping an async generator reduced memory
allocation per request by 60% compared to buffering entire responses.

**Microsoft semantic-kernel Python.** Uses FastAPI as the HTTP transport layer for its plugin
server. Route decorators on tool functions are auto-inspected to produce OpenAPI plugin manifests
that ChatGPT and Copilot can consume.

**Uber's ML feature serving.** Deployed FastAPI behind a Kubernetes ingress to serve feature
vectors. The `response_model` enforcement ensures that downstream model training code never
receives unexpected feature keys that could silently corrupt training data.

---

## 8. Tradeoffs

### FastAPI vs Alternatives

| Dimension               | FastAPI 0.110+        | Django REST Framework | Flask + marshmallow   | Litestar              |
|-------------------------|-----------------------|-----------------------|-----------------------|-----------------------|
| Async-native            | Yes (ASGI)            | Partial (ASGI mode)   | No (WSGI; Quart fork) | Yes (ASGI)            |
| Auto OpenAPI            | Yes, zero config      | drf-spectacular plugin| apispec plugin        | Yes, zero config      |
| Validation              | Pydantic v2 (Rust)    | DRF serializers       | marshmallow           | Pydantic/attrs        |
| Startup time            | ~0.3s (small app)     | ~1.5s                 | ~0.1s                 | ~0.2s                 |
| Learning curve          | Low-medium            | Medium                | Low                   | Medium                |
| Ecosystem maturity      | High (2019+)          | Very high (2011+)     | Very high (2010+)     | Medium (2023+)        |
| WebSocket               | Native                | Via Channels addon    | Via Flask-SocketIO    | Native                |

### ASGI Concurrency Model Tradeoffs

| Scenario                     | Async (ASGI) wins | Sync (WSGI) wins   |
|------------------------------|-------------------|--------------------|
| Many concurrent DB queries   | Yes               |                    |
| CPU-bound image processing   |                   | Yes (no GIL relief)|
| WebSocket / SSE connections  | Yes               |                    |
| Simple CRUD, low traffic     | Tie               | Tie                |
| Legacy sync ORM (SQLAlchemy 1)| Needs executor   | Native             |
| Teams unfamiliar with async  |                   | Lower risk         |

---

## 9. When to Use / When NOT to Use

### Use FastAPI + ASGI when:

- Building I/O-bound microservices — database reads, external API calls, cache lookups.
- You need WebSocket or Server-Sent Events alongside REST on the same server.
- Auto-generated OpenAPI is required (client SDK generation, contract testing).
- The team knows Python 3.10+ typing well; they will benefit from inline validation.
- You want Pydantic v2's Rust-backed validation performance (10–50x faster than v1 for
  large payloads).
- Service needs graceful startup/shutdown for resource pools (DB, ML models, Kafka consumers).

### Do NOT use FastAPI + ASGI when:

- The application is primarily CPU-bound (video encoding, numerical computation). A single-process
  async server will not parallelize CPU work; reach for multiprocessing or a task queue instead.
- The team is not comfortable with `async`/`await` semantics; mixing sync and async incorrectly
  blocks the event loop silently and is hard to debug.
- You are integrating a legacy ORM that has no async driver. Running every query through
  `run_in_executor` works but negates most async benefits.
- You need Django's batteries (admin panel, ORM migrations, auth framework) — Django's ASGI mode
  is available but Django REST Framework is better supported in the sync path.
- The endpoint must be called from a synchronous context that cannot `await`, such as a CLI
  script or a pytest fixture without `anyio` — use a WSGI framework or expose a sync wrapper.

---

## 10. Common Pitfalls

### Pitfall 1 — BROKEN: Using deprecated `on_startup` / `on_shutdown` event handlers

FastAPI 0.95 deprecated `@app.on_event("startup")` and `@app.on_event("shutdown")` in favour
of the `lifespan` context manager. The old handlers have no way to share state with request
handlers.

```python
# BROKEN — deprecated API, no way to share pool with route handlers
from fastapi import FastAPI
import asyncpg

app = FastAPI()
pool = None  # module-level global — threading / import issues

@app.on_event("startup")           # DeprecationWarning in FastAPI 0.95+
async def startup():
    global pool
    pool = await asyncpg.create_pool(dsn="postgresql://...")

@app.on_event("shutdown")
async def shutdown():
    await pool.close()

@app.get("/users")
async def list_users():
    return await pool.fetch("SELECT id FROM users")  # pool may be None in tests
```

```python
# FIX — lifespan context manager (FastAPI 0.110+)
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import asyncpg
from fastapi import FastAPI, Request


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[dict]:
    pool = await asyncpg.create_pool(dsn="postgresql://...")
    yield {"db": pool}
    await pool.close()


app = FastAPI(lifespan=lifespan)


@app.get("/users")
async def list_users(request: Request) -> list[dict]:
    rows = await request.state.db.fetch("SELECT id, username FROM users")
    return [dict(r) for r in rows]
```

The `lifespan` approach: no global state, the pool is explicitly scoped to the process
lifetime, and tests can override the lifespan with `app.router.lifespan_context`.

---

### Pitfall 2 — BROKEN: Synchronous blocking call in an `async def` route

A `def` route handler is run in a thread pool by FastAPI. But an `async def` handler runs
directly on the event loop. Calling a blocking function inside `async def` freezes the
entire server until the call returns.

```python
# BROKEN — blocks the event loop; all concurrent requests stall
import psycopg2
from fastapi import FastAPI

app = FastAPI()


@app.get("/products/{product_id}")
async def get_product(product_id: int) -> dict:
    # psycopg2 is a synchronous driver; this blocks the event loop
    conn = psycopg2.connect("dbname=mydb user=postgres")
    cursor = conn.cursor()
    cursor.execute("SELECT id, name FROM products WHERE id = %s", (product_id,))
    row = cursor.fetchone()
    conn.close()
    return {"id": row[0], "name": row[1]}
```

```python
# FIX — use asyncpg (native async PostgreSQL driver)
import asyncpg
from fastapi import FastAPI, Request, HTTPException

app = FastAPI()      # lifespan sets up the pool as shown in Pitfall 1


@app.get("/products/{product_id}")
async def get_product(product_id: int, request: Request) -> dict:
    row = await request.state.db.fetchrow(
        "SELECT id, name FROM products WHERE id = $1", product_id
    )
    if row is None:
        raise HTTPException(status_code=404, detail="Product not found")
    return {"id": row["id"], "name": row["name"]}
```

If you truly must use a synchronous library, wrap it in `asyncio.get_event_loop().run_in_executor(None, blocking_fn, arg)` or switch the handler to `def` (not `async def`) so FastAPI puts it on the thread pool automatically.

---

### Pitfall 3 — BROKEN: Returning a Pydantic model without `response_model` — extra fields leak

```python
# BROKEN — internal fields exposed to clients
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()


class UserInternal(BaseModel):
    id: int
    username: str
    hashed_password: str
    stripe_customer_id: str


@app.get("/users/{user_id}")       # no response_model declared
async def get_user(user_id: int) -> UserInternal:
    return UserInternal(
        id=user_id,
        username="alice",
        hashed_password="$2b$12$secret",
        stripe_customer_id="cus_abc123",
    )
# Response body includes hashed_password and stripe_customer_id — data breach.
```

```python
# FIX — always declare response_model to enforce field filtering
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()


class UserInternal(BaseModel):
    id: int
    username: str
    hashed_password: str
    stripe_customer_id: str


class UserPublic(BaseModel):
    id: int
    username: str


@app.get("/users/{user_id}", response_model=UserPublic)
async def get_user(user_id: int) -> UserInternal:
    return UserInternal(
        id=user_id,
        username="alice",
        hashed_password="$2b$12$secret",
        stripe_customer_id="cus_abc123",
    )
# Response body: {"id": 1, "username": "alice"}  — safe.
```

---

### Pitfall 4 — Using `--reload` in production

`uvicorn main:app --reload` starts a watchdog thread that polls the filesystem. This increases
CPU usage, delays startup on large codebases, and is not safe in containerised deployments
where the source directory is read-only. Always omit `--reload` in production images.

---

### Pitfall 5 — Mutating global state during request handling

ASGI servers run in a single process with a single event loop. A class-level or module-level
list mutated in a route handler is shared across all concurrent requests. Use a proper cache
(Redis) or request-scoped state via `request.state` for per-request data.

---

## 11. Technologies & Tools

### ASGI Server Comparison

| Dimension              | Uvicorn 0.29+              | Hypercorn 0.16+            | Daphne 4.0+             | Gunicorn + UvicornWorker   |
|------------------------|----------------------------|----------------------------|-------------------------|----------------------------|
| ASGI compliance        | Full ASGI 3                | Full ASGI 3                | Full ASGI 3             | Full ASGI 3                |
| HTTP/2                 | No (HTTP/1.1 only)         | Yes                        | No                      | No (Uvicorn limitation)    |
| WebSocket              | Yes                        | Yes                        | Yes (Django Channels)   | Yes                        |
| Graceful reload        | Yes (`--reload` dev only)  | Yes                        | No                      | Yes (SIGHUP)               |
| Worker model           | Single process + asyncio   | Single process + asyncio   | Twisted reactor         | Multi-process              |
| Production readiness   | High                       | Medium                     | Medium (Django-centric) | Very High                  |
| Process management     | Manual / systemd           | Manual / systemd           | Manual                  | Built-in (gunicorn)        |
| Typical use case       | Dev + K8s pods             | HTTP/2 required            | Django Channels         | Traditional VM deployments |
| gRPC support           | No                         | No                         | No                      | No                         |

### Related Libraries

| Library         | Role                                         | Version |
|-----------------|----------------------------------------------|---------|
| Starlette       | ASGI toolkit FastAPI is built on             | 0.37+   |
| Pydantic        | Data validation / serialization              | 2.7+    |
| asyncpg         | Async PostgreSQL driver (no thread pool)     | 0.29+   |
| SQLAlchemy      | ORM with async support via `async_engine`    | 2.0+    |
| httpx           | Async HTTP client for external calls         | 0.27+   |
| anyio           | Backend-agnostic async primitives            | 4.3+    |
| pytest-anyio    | Async test support                           | 4.3+    |

---

## 12. Interview Questions with Answers

**Q1: What is the difference between WSGI and ASGI?**
WSGI is a synchronous interface: `app(environ, start_response)` is a regular Python callable
that blocks the server thread until the response is ready. ASGI is an asynchronous interface:
`await app(scope, receive, send)` is a coroutine that yields control back to the event loop
while waiting for I/O. WSGI requires one thread per concurrent request; ASGI can handle
thousands of concurrent connections on a single thread. ASGI also supports WebSockets and
Server-Sent Events natively; WSGI does not.

**Q2: What are the three arguments in the ASGI 3 callable signature, and what does each carry?**
`scope` is a plain `dict` describing the connection — type (`http`, `websocket`, `lifespan`),
method, path, headers, and query string. `receive` is an async callable that returns the next
incoming event (request body chunk or WebSocket message) as a `dict`. `send` is an async
callable that accepts an outgoing event dict (`http.response.start` with status and headers,
then `http.response.body` with the body bytes). The application awaits `receive` to read input
and awaits `send` for each piece of output.

**Q3: What is Starlette and how does FastAPI relate to it?**
Starlette is a lightweight ASGI framework providing routing, middleware, request/response
abstractions, sessions, background tasks, and test client. FastAPI subclasses Starlette's
`APIRouter` and `Request` and adds automatic Pydantic validation, dependency injection, and
OpenAPI generation on top. You can add Starlette middleware directly to a FastAPI app, and
a FastAPI route can return any Starlette `Response` subclass.

**Q4: Why use `lifespan` instead of `on_startup` / `on_shutdown`?**
The `lifespan` context manager was introduced to replace the deprecated event handlers in
FastAPI 0.95. It solves two problems the old API could not: (1) it provides a `yield`-based
structure so resources created in startup are automatically closed in shutdown even if an
exception occurs; (2) the `dict` yielded from the context manager is injected into
`app.state` and accessible via `request.state` in every route handler, removing the need
for module-level globals. The old handlers could not share state with routes without globals.

**Q5: What happens when FastAPI receives a request for `/items/abc` with a route defined as `@app.get("/items/{item_id}")` where `item_id: int`?**
FastAPI resolves the path parameter at startup, noting that `item_id` maps to `int`. At
request time, Pydantic v2 attempts to coerce the string `"abc"` to `int`. Coercion fails;
FastAPI raises `RequestValidationError`, which is caught by the built-in exception handler
and serialized as a `422 Unprocessable Entity` JSON body with a `detail` array listing the
exact field, location (`path`), and message (`Input should be a valid integer`). The route
handler function is never called.

**Q6: How does FastAPI filter extra fields when `response_model` is set?**
FastAPI passes the route handler's return value to `response_model.model_validate(return_value)`,
producing a Pydantic instance containing only the fields declared in `response_model`. It then
calls `.model_dump()` and serializes the resulting dict to JSON. Any field present in the
handler's return value but absent from `response_model` is silently dropped. This is Pydantic v2
behaviour; in v1 the method was `.dict()`.

**Q7: When should you use `def` versus `async def` for a route handler in FastAPI?**
Use `async def` when the handler calls other `async` functions — awaiting an async DB driver,
making async HTTP calls with `httpx`, or reading from a Redis async client. FastAPI runs `async
def` handlers directly on the event loop. Use `def` when calling a synchronous library that
cannot be made async — synchronous ORM calls, CPU-intensive work, legacy SDKs. FastAPI runs
`def` handlers in a thread pool executor (`anyio.to_thread.run_sync`), preventing event loop
blockage. Never call a blocking function directly inside `async def`.

**Q8: What does `response_class=HTMLResponse` do on a route decorator?**
It tells FastAPI to wrap the handler's return value in an `HTMLResponse` instead of the
default `JSONResponse`. This sets `Content-Type: text/html; charset=utf-8`. It also updates
the OpenAPI schema to show `text/html` as the response media type. The handler can return
a plain `str` and FastAPI will wrap it; or it can return an `HTMLResponse` instance directly
for full control over status code and headers.

**Q9: How does Uvicorn handle concurrent requests without multiple threads?**
Uvicorn runs Python's `asyncio` event loop in a single thread. When an `await` expression
suspends a coroutine (waiting for a socket read, a DB query, or an external HTTP response),
the event loop picks up the next ready coroutine from its queue and resumes it. This
cooperative multitasking means thousands of coroutines can be "in flight" simultaneously
with one thread, as long as none of them block synchronously. The OS handles the underlying
non-blocking I/O via `select`/`epoll`/`kqueue`; asyncio wraps these in awaitable
`Future` objects.

**Q10: Where does FastAPI generate the OpenAPI schema and how is it customised?**
FastAPI builds the OpenAPI 3.1 schema object in memory at startup by inspecting every
registered route: path, method, path/query parameters, request body (inferred from the
first Pydantic parameter), `response_model`, status codes, and tags. The schema is
serialised to JSON and served at `GET /openapi.json`. Swagger UI is served at `GET /docs`
and ReDoc at `GET /redoc`. Customisation points include: `title`, `version`, `description`,
`openapi_tags` on the `FastAPI` constructor; `summary`, `description`, `tags`, `responses`,
`include_in_schema=False` on individual route decorators; and `Field(description=...,
example=...)` on Pydantic model fields.

**Q11: What is `StreamingResponse` used for and how does it work under ASGI?**
`StreamingResponse` wraps an async generator (or sync generator run in a thread) that
yields `bytes` or `str` chunks. When FastAPI serializes a `StreamingResponse`, it calls
`send({"type": "http.response.start", ...})` once, then iterates the generator, calling
`send({"type": "http.response.body", "body": chunk, "more_body": True})` for each chunk,
and finally `send({"type": "http.response.body", "body": b"", "more_body": False})`. The
client receives chunks incrementally. This is the mechanism behind Server-Sent Events,
large file downloads, and real-time LLM token streaming.

**Q12: How does Starlette middleware interact with FastAPI routes?**
Each middleware is an ASGI callable added via `app.add_middleware(SomeMiddleware, **kwargs)`.
FastAPI wraps the middleware around the application in reverse order of addition — last added
is outermost. The middleware receives the same `(scope, receive, send)` triple. It can read
and modify scope, wrap `receive` to inspect the request body, wrap `send` to inspect or
modify response headers, or short-circuit the chain by sending a response directly without
calling the inner app. This mechanism is identical to how Django middleware works conceptually,
but purely async.

**Q13: What is `root_path` in the ASGI scope and when does it matter?**
`root_path` is the URL prefix that the reverse proxy strips before forwarding to Uvicorn.
If Nginx is configured to proxy `https://api.example.com/v1/` to `http://localhost:8000/`,
the `root_path` is `/v1`. Without it, FastAPI generates OpenAPI paths starting at `/`, and
the Swagger UI's "Try it out" buttons send requests to `/items/1` instead of `/v1/items/1`,
failing. Set `root_path` via `app = FastAPI(root_path="/v1")` or via Uvicorn's `--root-path`
flag. Alternatively, configure the proxy to pass the `X-Forwarded-Prefix` header and use
Starlette's `ProxyHeadersMiddleware`.

**Q14: How are path parameters differentiated from query parameters in a FastAPI route signature?**
FastAPI determines the source of each parameter at startup. A parameter is a path parameter
if its name appears as a `{placeholder}` in the path string of the route decorator. Every
other parameter becomes a query parameter (if it has no default or a non-`Body` default) or
a request body field (if its type is a Pydantic `BaseModel` with no default). This resolution
is purely positional: changing `@app.get("/items/{item_id}")` to `@app.get("/items")` turns
`item_id` from a path parameter into a required query parameter with no code change to the
function body.

**Q15: What is the difference between `FileResponse` and `StreamingResponse` for serving large files?**
`FileResponse` uses `aiofiles` (or `anyio`) to send a file from disk using the OS's
`sendfile` syscall when available, which copies data from disk to the socket buffer without
passing through Python's heap. It also handles `Range` requests for partial content (required
for video seek). `StreamingResponse` wraps a Python generator and allocates chunks in the
Python heap. For files already on disk, `FileResponse` is more memory-efficient. For
dynamically generated content (S3 proxy streaming, LLM token output, live sensor data),
use `StreamingResponse`.

**Q16: Why is `--workers 1` the recommended default for Uvicorn, and when should you increase it?**
A single Uvicorn worker runs one asyncio event loop and handles thousands of concurrent
I/O-bound requests efficiently. Adding workers spawns new OS processes, each with its own
event loop and memory copy of the application — including ML models, DB connection pools,
and in-memory caches. For I/O-bound services, scaling is better achieved horizontally (more
pods/containers) via the orchestrator. Increase `--workers` (or use Gunicorn + UvicornWorker)
when the bottleneck is CPU — Python parsing, Pydantic validation of very large payloads, or
synchronous business logic — since each extra worker can utilise an additional CPU core
independently of the GIL.

---

## 13. Best Practices

**Always declare `response_model` on every public endpoint.** Even if the handler currently
returns only safe fields, explicit declaration prevents future model changes from accidentally
leaking new fields.

**Use `lifespan` for all application-scoped resources.** DB connection pools, ML model weights,
Redis clients, and Kafka producers must be created once and reused. Module-level globals work
but cannot be easily overridden in tests; `lifespan` state is injectable.

**Prefer `async def` over `def` for route handlers, and use async libraries throughout.** A single
synchronous DB call inside `async def` can stall hundreds of concurrent requests. Audit every
I/O call for a corresponding async driver.

**Validate at the boundary with `Annotated` constraints.** Use `Annotated[int, Path(ge=1, le=10000)]`
instead of validating inside the function body. Constraints appear in the OpenAPI schema and
are enforced before the handler runs.

**Set `openapi_tags` and per-route `tags` from day one.** Swagger UI groups routes by tag;
teams without tags end up with one unnavigable flat list of 200 endpoints.

**Use `response_class=ORJSONResponse` for high-throughput JSON endpoints.** FastAPI ships with
`from fastapi.responses import ORJSONResponse`, which uses the `orjson` library and is roughly
3-5x faster than the stdlib `json` serializer for typical API payloads.

**Add `include_in_schema=False` to internal endpoints.** Health checks, internal metrics
scrapers, and readiness probes (`/healthz`, `/readyz`) should not appear in the public API
spec. Set `include_in_schema=False` on those routes.

**Set `root_path` when running behind a reverse proxy.** Failing to set it produces broken
Swagger UI "Try it out" links and incorrect `servers` entries in the OpenAPI spec.

**Separate `APIRouter` instances by domain.** Define routers in `routers/items.py`,
`routers/users.py`, etc., and `include_router` in `main.py`. This keeps route files
small and allows per-router prefix and dependency overrides.

**Pin Pydantic to v2 explicitly.** FastAPI 0.110+ defaults to Pydantic v2, but library
dependencies may install v1. Pin `pydantic>=2.0` in `pyproject.toml` and test with `pydantic
--version` in CI.

---

## 14. Case Study

**Title: Building a FastAPI Service with Full lifespan, OpenAPI, and Async Resource Management**

### Context

A small inventory management service needs: an async PostgreSQL connection pool initialised
at startup, proper graceful shutdown, response models that prevent internal field leakage,
custom OpenAPI tags, and a health check endpoint.

### BROKEN version — using deprecated `on_startup`

```python
# BROKEN: inventory_broken.py — FastAPI 0.94 style (deprecated)
import asyncpg
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()
_pool: asyncpg.Pool | None = None   # module-level global — test unfriendly


@app.on_event("startup")            # DeprecationWarning in FastAPI 0.95+
async def startup() -> None:
    global _pool
    _pool = await asyncpg.create_pool(
        dsn="postgresql://app:secret@localhost/inventory",
        min_size=2,
        max_size=10,
    )


@app.on_event("shutdown")           # no guarantee this runs on SIGKILL
async def shutdown() -> None:
    if _pool:
        await _pool.close()


class ItemDB(BaseModel):
    id: int
    sku: str
    quantity: int
    cost_price: float               # internal — must not be exposed


@app.get("/items/{item_id}")        # MISSING response_model — cost_price leaks
async def get_item(item_id: int) -> ItemDB:
    row = await _pool.fetchrow(     # _pool could be None if startup failed silently
        "SELECT id, sku, quantity, cost_price FROM items WHERE id = $1", item_id
    )
    return ItemDB(**dict(row))
```

Problems:
- `_pool` is a global; tests that import the module share one pool instance.
- `on_startup`/`on_shutdown` are deprecated; `on_shutdown` may not fire on SIGKILL.
- No `response_model` — `cost_price` is sent to every client.
- No 404 handling — `fetchrow` returning `None` causes an unhandled `TypeError`.

### FIX — lifespan, response_model, error handling, and OpenAPI tags

```python
# FIX: inventory_service.py — FastAPI 0.110+, Python 3.11+
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

import asyncpg
from fastapi import FastAPI, HTTPException, Path, Request
from fastapi.responses import JSONResponse, ORJSONResponse
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Response models (public surface — no internal fields)
# ---------------------------------------------------------------------------

class ItemPublic(BaseModel):
    id: int
    sku: str
    quantity: int


class ItemCreate(BaseModel):
    sku: str = Field(..., min_length=1, max_length=64, example="WIDGET-001")
    quantity: int = Field(..., ge=0, example=100)
    cost_price: float = Field(..., gt=0, example=4.99)   # accepted on write


class HealthResponse(BaseModel):
    status: str
    db_pool_size: int


# ---------------------------------------------------------------------------
# Lifespan — manages the async PostgreSQL pool
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[dict]:
    """
    Startup: create asyncpg pool.
    Yield: pool is available via request.state.db.
    Shutdown: drain and close the pool cleanly.
    """
    pool: asyncpg.Pool = await asyncpg.create_pool(
        dsn="postgresql://app:secret@localhost/inventory",
        min_size=5,
        max_size=20,
        command_timeout=10.0,
    )
    try:
        yield {"db": pool}
    finally:
        await pool.close()   # always runs, even if the app raises during startup


# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Inventory Service",
    version="2.0.0",
    description="Manages warehouse inventory with async PostgreSQL backend.",
    openapi_tags=[
        {"name": "items", "description": "Read and write inventory records."},
        {"name": "ops", "description": "Health and readiness probes."},
    ],
    lifespan=lifespan,
    default_response_class=ORJSONResponse,   # orjson for all JSON responses
)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get(
    "/items/{item_id}",
    response_model=ItemPublic,
    summary="Fetch a single item",
    tags=["items"],
    responses={
        404: {"description": "Item not found"},
        422: {"description": "item_id must be a positive integer"},
    },
)
async def get_item(
    item_id: Annotated[int, Path(ge=1, description="Unique item ID")],
    request: Request,
) -> ItemPublic:
    pool: asyncpg.Pool = request.state.db
    row = await pool.fetchrow(
        "SELECT id, sku, quantity FROM items WHERE id = $1", item_id
    )
    if row is None:
        raise HTTPException(status_code=404, detail=f"Item {item_id} not found")
    return ItemPublic(**dict(row))


@app.post(
    "/items",
    response_model=ItemPublic,
    status_code=201,
    summary="Create a new item",
    tags=["items"],
)
async def create_item(body: ItemCreate, request: Request) -> ItemPublic:
    pool: asyncpg.Pool = request.state.db
    row = await pool.fetchrow(
        """
        INSERT INTO items (sku, quantity, cost_price)
        VALUES ($1, $2, $3)
        RETURNING id, sku, quantity
        """,
        body.sku,
        body.quantity,
        body.cost_price,
    )
    return ItemPublic(**dict(row))
    # cost_price is stored but not returned — response_model=ItemPublic filters it


@app.get(
    "/healthz",
    response_model=HealthResponse,
    include_in_schema=False,   # do not expose in public OpenAPI spec
    tags=["ops"],
)
async def health(request: Request) -> HealthResponse:
    pool: asyncpg.Pool = request.state.db
    await pool.fetchval("SELECT 1")   # lightweight connectivity check
    return HealthResponse(
        status="ok",
        db_pool_size=pool.get_size(),
    )
```

### Key points demonstrated

- `lifespan` replaces `on_startup`/`on_shutdown` entirely; the `finally` block guarantees
  pool closure even if the application panics during startup after pool creation.
- `request.state.db` is the pool injected by `lifespan`; no global variables.
- `response_model=ItemPublic` on `create_item` means `cost_price` is never included in
  the response even though the handler processes it internally.
- `include_in_schema=False` keeps `/healthz` out of the Swagger UI.
- `default_response_class=ORJSONResponse` applies orjson serialisation globally.
- `Annotated[int, Path(ge=1)]` validates path parameters with constraints visible in OpenAPI.

### Running the service

```bash
# Install dependencies
pip install "fastapi[standard]>=0.110" asyncpg "pydantic>=2.7" orjson

# Start with Uvicorn (development)
uvicorn inventory_service:app --host 0.0.0.0 --port 8000 --reload

# Production — Gunicorn process manager + Uvicorn workers
gunicorn inventory_service:app \
    --worker-class uvicorn.workers.UvicornWorker \
    --workers 2 \
    --bind 0.0.0.0:8000 \
    --graceful-timeout 30 \
    --timeout 60
```

### Testing with pytest-anyio

```python
# test_inventory.py
import pytest
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from httpx import AsyncClient, ASGITransport

from inventory_service import app, lifespan


@asynccontextmanager
async def test_lifespan(app) -> AsyncIterator[dict]:
    """Override lifespan with an in-memory stub for tests."""
    import unittest.mock as mock
    fake_pool = mock.AsyncMock()
    fake_pool.fetchrow.return_value = {"id": 1, "sku": "T-001", "quantity": 50}
    fake_pool.fetchval.return_value = 1
    fake_pool.get_size.return_value = 5
    yield {"db": fake_pool}


@pytest.mark.anyio
async def test_get_item_returns_public_fields() -> None:
    app.router.lifespan_context = test_lifespan   # inject test lifespan
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get("/items/1")
    assert response.status_code == 200
    data = response.json()
    assert "cost_price" not in data        # response_model filtering confirmed
    assert data["sku"] == "T-001"
```

The `lifespan` override pattern shows one of the main advantages over the old `on_startup`
approach: tests can inject a stub resource pool without monkey-patching global state.

---

*Cross-references:*
- *For dependency injection on top of ASGI, see `../dependency_injection_in_fastapi/README.md`*
- *For REST API design principles behind these endpoints, see `../../../backend/rest_api_design/README.md`*
