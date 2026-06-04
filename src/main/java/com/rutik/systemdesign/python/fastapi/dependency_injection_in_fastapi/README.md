# Dependency Injection in FastAPI

## 1. Concept Overview

Dependency Injection (DI) in FastAPI is a first-class mechanism for declaring shared logic — database sessions, auth checks, configuration objects, pagination parameters — as reusable callables that the framework resolves automatically before each request handler runs.

FastAPI's DI system is built on `Depends()`, a special marker that tells the framework: "call this callable, resolve its own dependencies recursively, inject the result here." Every injected dependency can itself declare further dependencies, forming a directed acyclic graph (DAG). FastAPI topologically sorts that graph at startup and executes it at request time, caching results within the request by default.

Core capabilities covered in this module:

- `Depends()` — the primary injection mechanism
- Sub-dependencies — arbitrary depth DAG resolution
- `yield`-based dependencies — setup/teardown lifecycle per request
- Dependency caching and scope control (`use_cache=False`)
- Class-based dependencies — callable classes as parameter bundles
- `dependency_overrides` — test-time substitution without monkey-patching
- Router-level and application-level global dependencies
- `Security()` — `Depends()` variant that exposes OpenAPI security schemes
- OAuth2 + JWT dependency chains

Python version: 3.11/3.12. FastAPI version: 0.110+. Pydantic version: v2.

---

## 2. Intuition

> Think of `Depends()` as a restaurant mise en place: before the chef (route handler) starts cooking, the kitchen staff (dependency functions) have already prepped the cutting board, sharpened the knives, and laid out the ingredients. The chef just picks up what is ready and works.

**Mental model.** A FastAPI route function is the leaf of a tree. Every `Depends(fn)` annotation is an edge pointing to a parent node. FastAPI walks the tree at startup to build a static dependency graph, then at request time does a topological traversal: innermost nodes execute first, their results flow outward, and the route handler receives all resolved values as normal function arguments.

**Why it matters.** Without DI, every route handler would repeat the same boilerplate: open a DB session, parse a JWT, validate a tenant ID, close the session. Copy-paste across 50 routes means 50 places to fix a bug. With `Depends()`, that logic lives once and is tested once.

**Key insight.** FastAPI's DI is pure Python: it uses `inspect.signature()` and `typing.get_type_hints()` to inspect callables at import time, not at runtime hot-path. Resolution cost is paid upfront. At request time, FastAPI executes a pre-computed execution plan, making the overhead negligible compared to network I/O.

---

## 3. Core Principles

**1. Inversion of control.** Route handlers declare what they need; the framework decides how and when to provide it. Handlers do not call `get_db()` themselves — they receive a session already open.

**2. Explicit over implicit.** Every dependency is visible in the function signature. There is no magic container or global registry. Reading a function signature fully describes all its inputs.

**3. Composability.** Dependencies are plain Python callables. They can be functions, async functions, classes, or lambdas. They compose by declaring other `Depends()` arguments.

**4. Single execution per request (default).** Within one request, a dependency callable is invoked at most once regardless of how many routes or sub-dependencies reference it. This is the caching guarantee.

**5. Lifecycle ownership.** A `yield` dependency owns setup and teardown of a resource. Teardown runs after the response is sent, in LIFO order, even if an exception occurred in the handler.

**6. Testability by design.** `app.dependency_overrides` is a dict mapping any dependency callable to a replacement. Tests can substitute a real DB session with an in-memory one without modifying production code.

---

## 4. Types / Architectures / Strategies

### 4.1 Function-based dependencies

The simplest form: a plain function (sync or async) that returns a value.

```python
from fastapi import Depends, FastAPI

def get_settings() -> dict[str, str]:
    return {"env": "production", "debug": "false"}

app = FastAPI()

@app.get("/info")
def info(settings: dict[str, str] = Depends(get_settings)) -> dict[str, str]:
    return settings
```

### 4.2 yield-based (context-manager) dependencies

Used when a resource must be cleaned up after the request. The body before `yield` is setup; the body after `yield` (in a `finally` block) is teardown.

```python
from collections.abc import Generator
from sqlalchemy.orm import Session
from db import SessionLocal

def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
```

### 4.3 Async yield dependencies

When using an async ORM (SQLAlchemy async, Tortoise, etc.):

```python
from collections.abc import AsyncGenerator
from sqlalchemy.ext.asyncio import AsyncSession
from db import async_session_factory

async def get_async_db() -> AsyncGenerator[AsyncSession, None]:
    async with async_session_factory() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()
```

### 4.4 Class-based dependencies

A class whose `__init__` declares query parameters or sub-dependencies. FastAPI instantiates the class per request.

```python
class Paginator:
    def __init__(self, skip: int = 0, limit: int = 100) -> None:
        self.skip = skip
        self.limit = limit

@app.get("/items")
def list_items(paginator: Paginator = Depends()) -> dict:
    return {"skip": paginator.skip, "limit": paginator.limit}
```

Note: `Depends()` with no argument tells FastAPI to instantiate the type annotation. `Depends(Paginator)` is equivalent and more explicit.

### 4.5 Sub-dependency chains

Dependencies declare their own `Depends()` arguments, creating a DAG:

```python
from fastapi.security import OAuth2PasswordBearer

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/token")

def get_current_user(
    db: Session = Depends(get_db),
    token: str = Depends(oauth2_scheme),
) -> User:
    return decode_and_lookup(db, token)

def get_current_active_superuser(
    current_user: User = Depends(get_current_user),
) -> User:
    if not current_user.is_superuser:
        raise HTTPException(status_code=403, detail="Not a superuser")
    return current_user
```

### 4.6 Router-level global dependencies

```python
from fastapi import APIRouter

router = APIRouter(
    prefix="/admin",
    dependencies=[Depends(get_current_active_superuser)],
)
```

All routes registered on this router automatically require the superuser check.

### 4.7 Application-level global dependencies

```python
app = FastAPI(dependencies=[Depends(check_maintenance_mode)])
```

Runs before every route in the application. Useful for rate limiting, maintenance windows, or request ID injection.

### 4.8 Security() dependencies

`Security()` is a subclass of `Depends()` that carries an additional `scopes` parameter, used to declare OAuth2 scope requirements in OpenAPI docs.

```python
from fastapi import Security
from fastapi.security import SecurityScopes

def get_current_user(
    security_scopes: SecurityScopes,
    token: str = Depends(oauth2_scheme),
) -> User:
    if "items:read" not in security_scopes.scopes:
        raise HTTPException(status_code=403)
    return decode_token(token)

@app.get("/items")
def read_items(user: User = Security(get_current_user, scopes=["items:read"])):
    ...
```

---

## 5. Architecture Diagrams

### Diagram 1: Dependency Graph for a Protected Route

```
GET /admin/items
       |
       v
 route handler: list_admin_items
       |
       +---> get_current_active_superuser (Depends)
       |             |
       |             +---> get_current_user (Depends)
       |                         |
       |                         +---> get_db (Depends)  <---+
       |                         |         |                 |
       |                         |    [SessionLocal()]       |
       |                         |    yield db               |
       |                         |                           |
       |                         +---> oauth2_scheme (Depends)
       |                                   |
       |                              Authorization: Bearer <token>
       |
       +---> paginator: Paginator (Depends)
                   |
              __init__(skip, limit) from query params


Cache boundary: get_db is called ONCE per request even if referenced
by both get_current_user and any other sub-dep in the same request.
```

### Diagram 2: yield Dependency Lifecycle

```
  HTTP Request arrives
          |
          v
  [Dependency setup phase]
  get_db() called
     db = SessionLocal()   <-- setup
     yield db              <-- value injected into handler
          |
          v
  [Route handler executes]
  list_admin_items(db=<session>, ...)
     ... business logic ...
     return response_data
          |
          v
  [Response serialized and sent to client]
          |
          v
  [Dependency teardown phase — AFTER response sent]
  finally: db.close()     <-- teardown (LIFO if multiple yield deps)

  If handler raised an exception:
     Exception propagates to FastAPI
     Response sent (500 or re-raised HTTPException)
     finally block STILL runs — session is closed
```

### Diagram 3: Dependency Caching Within a Request

```
Request R1:
  dep_graph.resolve(get_db)
    -> calls get_db() -> session_1
    -> caches: {get_db: session_1}
  dep_graph.resolve(get_current_user)
    -> needs get_db
    -> cache HIT: returns session_1  (NOT a new call)
  dep_graph.resolve(some_other_dep_that_also_needs_db)
    -> cache HIT: returns session_1

Request R2:
  cache is empty (per-request scope)
    -> calls get_db() -> session_2
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Startup: building the dependency graph

When `app = FastAPI()` imports your route modules, the `@app.get(...)` decorator registers the route. FastAPI calls `get_typed_signature()` on the endpoint function, which uses `inspect.signature()` and `typing.get_type_hints()` to enumerate every parameter.

For each parameter whose default is a `Depends(callable)` instance, FastAPI:
1. Records the callable as a dependency node.
2. Recursively inspects that callable's own signature for further `Depends()` parameters.
3. Builds a `Dependant` object (internal FastAPI model) storing the full sub-tree.

This graph is built once at startup and stored on the route. No reflection happens on the hot path.

### 6.2 Request time: topological resolution

When a request arrives, FastAPI's `solve_dependencies()` function:
1. Creates a per-request cache dict: `dependency_cache: dict[tuple, Any] = {}`.
2. Traverses the `Dependant` tree depth-first.
3. For each node, checks the cache using `(callable, use_cache)` as the key.
4. If not cached (or `use_cache=False`), calls the callable with its own resolved args.
5. If the callable is a generator (yield dep), wraps it in a context manager; stores the teardown on the `BackgroundTasks`-adjacent cleanup stack.

### 6.3 yield dependency teardown order

Multiple yield dependencies in a single request are tracked in a list. Teardown executes in LIFO (last-in, first-out) order after the response is committed to the transport. This mirrors Python context manager stacking semantics.

```python
async def dep_a() -> AsyncGenerator[str, None]:
    print("a setup")
    yield "a"
    print("a teardown")   # runs SECOND

async def dep_b() -> AsyncGenerator[str, None]:
    print("b setup")
    yield "b"
    print("b teardown")   # runs FIRST

@app.get("/test")
async def handler(
    a: str = Depends(dep_a),
    b: str = Depends(dep_b),
) -> dict:
    return {"a": a, "b": b}
# Output order: a setup, b setup, [response sent], b teardown, a teardown
```

### 6.4 Simple sync DB dependency

```python
from collections.abc import Generator
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session

DATABASE_URL = "postgresql+psycopg2://user:pw@localhost/db"
engine = create_engine(DATABASE_URL, pool_size=10, max_overflow=20)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)

def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

# Usage
@app.get("/users/{user_id}")
def read_user(user_id: int, db: Session = Depends(get_db)) -> UserSchema:
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    return UserSchema.model_validate(user)
```

### 6.5 Async DB dependency

```python
from collections.abc import AsyncGenerator
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

async_engine = create_async_engine(
    "postgresql+asyncpg://user:pw@localhost/db",
    pool_size=10,
    max_overflow=20,
)
AsyncSessionLocal = async_sessionmaker(
    bind=async_engine,
    expire_on_commit=False,
)

async def get_async_db() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise
```

### 6.6 Dependency caching: `use_cache=False`

```python
import secrets

def gen_nonce() -> str:
    return secrets.token_hex(16)

# WRONG — both deps get the same nonce because caching is on by default
@app.get("/double")
def double(
    n1: str = Depends(gen_nonce),
    n2: str = Depends(gen_nonce),   # cache HIT: returns same value as n1
) -> dict:
    return {"n1": n1, "n2": n2}  # n1 == n2 — BUG

# CORRECT
@app.get("/double")
def double_fixed(
    n1: str = Depends(gen_nonce),
    n2: str = Depends(gen_nonce, use_cache=False),  # forces fresh call
) -> dict:
    return {"n1": n1, "n2": n2}  # n1 != n2
```

### 6.7 Class-based dependency with validation

```python
from fastapi import Query

class Paginator:
    def __init__(
        self,
        skip: int = Query(default=0, ge=0),
        limit: int = Query(default=20, ge=1, le=200),
    ) -> None:
        self.skip = skip
        self.limit = limit

class FilterParams:
    def __init__(
        self,
        paginator: Paginator = Depends(),
        q: str | None = Query(default=None, max_length=100),
    ) -> None:
        self.skip = paginator.skip
        self.limit = paginator.limit
        self.q = q

@app.get("/search")
def search(filters: FilterParams = Depends()) -> dict:
    return {
        "skip": filters.skip,
        "limit": filters.limit,
        "query": filters.q,
    }
```

### 6.8 `dependency_overrides` in tests

```python
# conftest.py
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from collections.abc import Generator

from myapp.main import app
from myapp.database import get_db, Base

TEST_DB_URL = "sqlite:///./test.db"
test_engine = create_engine(TEST_DB_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(bind=test_engine, autocommit=False, autoflush=False)

@pytest.fixture(autouse=True)
def setup_db():
    Base.metadata.create_all(bind=test_engine)
    yield
    Base.metadata.drop_all(bind=test_engine)

@pytest.fixture
def db_session() -> Generator[Session, None, None]:
    session = TestingSessionLocal()
    try:
        yield session
    finally:
        session.close()

@pytest.fixture
def client(db_session: Session):
    def override_get_db() -> Generator[Session, None, None]:
        try:
            yield db_session
        finally:
            pass  # teardown managed by db_session fixture

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides = {}   # always reset after each test
```

### 6.9 Router-level and app-level dependencies

```python
from fastapi import APIRouter, FastAPI, Header, HTTPException

async def verify_api_key(x_api_key: str = Header(...)) -> None:
    if x_api_key != "secret-key":
        raise HTTPException(status_code=401, detail="Invalid API key")

async def check_maintenance_mode() -> None:
    if MAINTENANCE_MODE:
        raise HTTPException(status_code=503, detail="Maintenance window")

# Router-level: all routes under /v1/admin require API key
admin_router = APIRouter(
    prefix="/v1/admin",
    dependencies=[Depends(verify_api_key)],
)

@admin_router.get("/stats")
async def admin_stats() -> dict:
    return {"users": 42}

# App-level: every route checks maintenance mode
app = FastAPI(dependencies=[Depends(check_maintenance_mode)])
app.include_router(admin_router)
```

### 6.10 Full OAuth2 + JWT dependency chain

```python
from datetime import datetime, timezone
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from pydantic import BaseModel

SECRET_KEY = "super-secret"
ALGORITHM = "HS256"

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/token")

class TokenData(BaseModel):
    sub: str
    scopes: list[str] = []

class User(BaseModel):
    id: int
    username: str
    is_active: bool
    is_superuser: bool

def decode_token(token: str) -> TokenData:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        return TokenData(sub=payload["sub"], scopes=payload.get("scopes", []))
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expired",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token",
            headers={"WWW-Authenticate": "Bearer"},
        )

async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    db: Annotated[AsyncSession, Depends(get_async_db)],
) -> User:
    token_data = decode_token(token)
    user = await db.get(UserModel, int(token_data.sub))
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    return User.model_validate(user)

async def get_current_active_user(
    current_user: Annotated[User, Depends(get_current_user)],
) -> User:
    if not current_user.is_active:
        raise HTTPException(status_code=400, detail="Inactive user")
    return current_user

async def get_current_active_superuser(
    current_user: Annotated[User, Depends(get_current_active_user)],
) -> User:
    if not current_user.is_superuser:
        raise HTTPException(status_code=403, detail="Not enough permissions")
    return current_user
```

---

## 7. Real-World Examples

### 7.1 Multi-tenant SaaS — tenant context injection

A SaaS platform with per-tenant DB schemas uses a dependency to extract the tenant from the JWT and attach it to the request context:

```python
class TenantContext(BaseModel):
    tenant_id: str
    schema_name: str

async def get_tenant_context(
    token_data: TokenData = Depends(get_token_data),
) -> TenantContext:
    tenant_id = token_data.tenant_id
    return TenantContext(
        tenant_id=tenant_id,
        schema_name=f"tenant_{tenant_id}",
    )

async def get_tenant_db(
    ctx: TenantContext = Depends(get_tenant_context),
) -> AsyncGenerator[AsyncSession, None]:
    async with tenant_session(ctx.schema_name) as session:
        yield session
```

All data-access routes inject `get_tenant_db` and are automatically scoped to the correct schema.

### 7.2 Rate limiting — per-user token bucket

```python
import time
from collections import defaultdict

_buckets: dict[str, tuple[float, int]] = defaultdict(lambda: (time.time(), 10))

def rate_limit(user: User = Depends(get_current_active_user)) -> None:
    last_refill, tokens = _buckets[str(user.id)]
    now = time.time()
    refill = int((now - last_refill) * 2)   # 2 tokens/second
    tokens = min(10, tokens + refill)
    if tokens < 1:
        raise HTTPException(status_code=429, detail="Rate limit exceeded")
    _buckets[str(user.id)] = (now, tokens - 1)

router = APIRouter(dependencies=[Depends(rate_limit)])
```

### 7.3 Feature flags — gradual rollout

```python
class FeatureFlags(BaseModel):
    new_search: bool = False
    beta_ui: bool = False

async def get_feature_flags(
    user: User = Depends(get_current_active_user),
    redis: Redis = Depends(get_redis),
) -> FeatureFlags:
    raw = await redis.get(f"flags:{user.id}")
    if raw:
        return FeatureFlags.model_validate_json(raw)
    return FeatureFlags()
```

Routes in the beta router declare `Depends(get_feature_flags)` and check flags before executing new code paths.

---

## 8. Tradeoffs

| Aspect | `Depends()` approach | Manual calls in handler |
|---|---|---|
| Reusability | High — one definition, used anywhere | Low — copy-paste per route |
| Testability | `dependency_overrides` — zero-mock overhead | Must patch module globals |
| Readability | Signature declares all inputs explicitly | Logic is co-located with handler |
| Startup cost | Graph built once at startup | No startup cost |
| Debugging complexity | Stack traces include dependency frames | Flat call stack |
| Circular deps | FastAPI raises at startup | Not applicable |

| Scope | Caching behavior | Typical use |
|---|---|---|
| Request (default) | Cached per request | DB sessions, auth tokens |
| `use_cache=False` | Called every invocation | Nonces, timestamps, idempotency keys |
| App startup (`lifespan`) | Once per process | DB engine, connection pools |
| Background task | Not cached — new context | Heavy async jobs |

| Dependency style | Best for | Limitation |
|---|---|---|
| Function | Simple values, no state | Cannot encapsulate multiple params cleanly |
| Class | Query param bundles, stateless config | Slightly more verbose |
| yield function | Resources requiring teardown | Teardown runs post-response; cannot abort response |
| `Security()` | OAuth2 scopes in OpenAPI | Cosmetic over `Depends()` at runtime |

---

## 9. When to Use / When NOT to Use

### Use `Depends()` when:

- Any resource requires per-request setup and teardown (DB sessions, file handles, HTTP clients).
- The same auth, permission, or context logic applies to multiple routes.
- You want a clean testing boundary via `dependency_overrides`.
- You need to enforce invariants across all routes in a router (use router-level deps).
- You are building a plugin-style system where behavior can be swapped per environment.

### Do NOT use `Depends()` when:

- The logic is trivial (one line) and only used in one route — inline it for clarity.
- You need the result before the route layer (e.g., in a middleware) — use `request.state` or a pure middleware instead.
- The dependency has side effects that must complete before the response is committed — `yield` deps run teardown after the response. Use `BackgroundTasks` for post-response work you initiate.
- You want app-level singletons (a DB engine, a connection pool) — create those in a `lifespan` context manager, not in a `Depends()`.
- The "dependency" is configuration that never changes per request — inject it once at startup via `lifespan` and close over it.

---

## 10. Common Pitfalls

### Pitfall 1 (BROKEN): Module-level singleton DB session

```python
# BROKEN: single session shared across all requests — not thread-safe,
# no cleanup, stale transaction state bleeds between requests

from sqlalchemy.orm import Session
from db import engine

db = Session(engine)   # created once at import time

@app.get("/users")
def list_users() -> list[UserSchema]:
    return db.query(User).all()  # same session for every request
```

**Problem:** A `Session` is not thread-safe. Concurrent requests corrupt each other's transaction state. The session is never closed; connections leak.

```python
# FIX: yield dependency creates a fresh session per request

from collections.abc import Generator
from sqlalchemy.orm import Session, sessionmaker
from db import engine

SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False)

def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()   # guaranteed, even on exception

@app.get("/users")
def list_users(db: Session = Depends(get_db)) -> list[UserSchema]:
    return [UserSchema.model_validate(u) for u in db.query(User).all()]
```

---

### Pitfall 2 (BROKEN): Relying on caching for a dependency that must run fresh

```python
# BROKEN: gen_nonce is cached — both n1 and n2 receive the identical value

import secrets

def gen_nonce() -> str:
    return secrets.token_hex(16)

@app.post("/sign")
def sign_payload(
    n1: str = Depends(gen_nonce),
    n2: str = Depends(gen_nonce),  # cache hit: same nonce
) -> dict:
    return {"n1": n1, "n2": n2}
    # {"n1": "abc123...", "n2": "abc123..."}  <-- n1 == n2 always
```

**Problem:** FastAPI's default caching key is the callable object. Both `Depends(gen_nonce)` references point to the same callable, so the second call is skipped.

```python
# FIX: use_cache=False on the second invocation

@app.post("/sign")
def sign_payload_fixed(
    n1: str = Depends(gen_nonce),
    n2: str = Depends(gen_nonce, use_cache=False),
) -> dict:
    return {"n1": n1, "n2": n2}
    # {"n1": "abc123...", "n2": "def456..."}  <-- distinct nonces
```

---

### Pitfall 3 (BROKEN): `dependency_overrides` not cleared between tests

```python
# BROKEN: override leaks from test_a into test_b

def test_a(client):
    app.dependency_overrides[get_db] = lambda: fake_db_a
    response = client.get("/users")
    assert response.status_code == 200
    # No cleanup — override persists

def test_b(client):
    # Still using fake_db_a from test_a — wrong database state
    response = client.get("/users")
    assert response.json() == []   # may fail with stale data
```

**Problem:** `dependency_overrides` is a mutable dict on the `app` singleton. Tests that set overrides without clearing them poison subsequent tests.

```python
# FIX: use a yield fixture that resets overrides unconditionally

@pytest.fixture
def client(db_session: Session):
    def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    with TestClient(app) as c:
        yield c
    app.dependency_overrides = {}   # always reset, even if test raises
```

---

### Pitfall 4: Async dependency in a sync route

```python
# BROKEN: calling an async dependency from a sync route creates a coroutine object,
# not a resolved value.

async def get_data() -> dict:
    return {"key": "value"}

@app.get("/data")
def sync_route(data = Depends(get_data)):  # FastAPI handles this correctly
    return data
```

FastAPI actually handles mixed sync/async dependencies correctly by running async deps in the event loop and sync deps in a thread pool. This is NOT a bug by itself. The pitfall is calling `await dep()` manually inside a sync function, which raises `SyntaxError`. Trust `Depends()` to handle the async boundary.

---

### Pitfall 5: Returning mutable default in a class-based dependency

```python
# BROKEN: mutable default argument shared across instances

class QueryFilter:
    def __init__(self, tags: list[str] = []) -> None:  # mutable default!
        self.tags = tags

# FIX
class QueryFilter:
    def __init__(self, tags: list[str] | None = None) -> None:
        self.tags = tags or []
```

---

## 11. Technologies & Tools

| Tool / Library | Role | Notes |
|---|---|---|
| FastAPI 0.110+ | DI framework | `Depends`, `Security`, `dependency_overrides` |
| Pydantic v2 | Data validation for dep outputs | `model_validate`, `BaseModel` |
| SQLAlchemy 2.x | DB sessions via yield deps | Both sync `Session` and async `AsyncSession` |
| `pytest` + `httpx` | Test client + async test support | `AsyncClient` for async routes |
| `pytest-asyncio` | Async test execution | `asyncio_mode = "auto"` |
| Starlette | Underlying request/response lifecycle | `Request.state` for cross-dep context |

| Comparison | FastAPI `Depends()` | Flask `g` object | Django `request` attr | Spring `@Autowired` |
|---|---|---|---|---|
| Scope | Per-request (default) | Per-request | Per-request | Singleton / Prototype |
| Explicit in signature | Yes | No (global) | No (passed implicitly) | No (field injection) |
| Testable without framework | Yes (`dependency_overrides`) | Requires app context | Requires Django test runner | Yes (manual wiring) |
| Teardown support | Yes (yield) | No native | No native | Yes (destroy callbacks) |

Cross-reference: Compare with Spring's `@Autowired` and `@Bean` scopes in [`../../../spring/dependency_injection/README.md`](../../../spring/dependency_injection/README.md).

---

## 12. Interview Questions with Answers

**Q1: How does FastAPI resolve dependencies at runtime?**
FastAPI builds a `Dependant` graph at startup by recursively inspecting `Depends()` markers using `inspect.signature()` and `typing.get_type_hints()`. At request time, `solve_dependencies()` does a depth-first traversal of this pre-built graph, executing each node in topological order. Results are cached in a per-request dict keyed by callable identity so each dependency runs at most once per request. This means the reflection cost is paid at startup, not on every request.

**Q2: What is a yield dependency and when does teardown run?**
A yield dependency is a generator function (sync or async) that performs setup before `yield`, produces the injected value at `yield`, and performs teardown after `yield` (typically in a `finally` block). Teardown runs after the HTTP response has been sent to the client — not before. FastAPI collects all yield dependency generators on a cleanup stack and drains it in LIFO order once the response is committed. This means teardown cannot modify the response, but it is guaranteed to run even if the handler raised an exception.

**Q3: What is the difference between `Depends(fn)` and `Depends(fn, use_cache=False)`?**
By default, `Depends(fn)` caches the result of `fn` for the lifetime of the request. All sub-dependencies and the main handler that reference the same callable receive the same object. `Depends(fn, use_cache=False)` bypasses this cache and calls `fn` fresh every time it appears in the dependency graph for that request. Use `use_cache=False` for dependencies whose result must be unique per call site — nonces, timestamps, or idempotency-key generators. Use the default caching for DB sessions, auth tokens, and any resource that should be shared within a request.

**Q4: How do you inject a DB session per request and ensure cleanup?**
Use a `yield` dependency: create a `SessionLocal()` in the setup phase, `yield` it to the handler, and call `db.close()` in a `finally` block after the `yield`. This guarantees the session is closed after every request, regardless of whether the handler succeeds or raises. Rollback on exception should also be added before re-raising so the connection returns to the pool in a clean state. Register with `Depends(get_db)` on any route that needs DB access.

**Q5: How do class-based dependencies work?**
When you write `param: MyClass = Depends()` (or `Depends(MyClass)`), FastAPI treats the class itself as the callable. It inspects `MyClass.__init__` for parameters, resolves them the same way as a function's parameters (including nested `Depends()` in `__init__`), then instantiates the class. The instance is injected into the route handler. Class-based dependencies are ideal for grouping related query parameters (pagination, filters) or for callable classes that need to hold configuration injected at init time.

**Q6: How does `dependency_overrides` work in testing?**
`app.dependency_overrides` is a plain dict mapping a callable to a replacement callable. When FastAPI resolves `Depends(original_fn)` and finds `original_fn` in `dependency_overrides`, it calls the replacement instead. This requires no monkey-patching of modules or mocking frameworks. The override must be reset to `{}` after each test to prevent state leakage. The standard pattern is a pytest `yield` fixture that sets the override, yields the `TestClient`, then clears the dict in the fixture's teardown.

**Q7: How do you add authentication to all routes in a router without repeating it per endpoint?**
Pass `dependencies=[Depends(auth_dep)]` to `APIRouter(...)`. Every route registered on that router will execute `auth_dep` before the handler, as if it had been declared on each endpoint individually. The dependency can raise `HTTPException` to abort the request. Router-level dependencies do not need to return a value that the handler consumes; they are commonly used for side-effectful checks like auth, rate limiting, or IP allowlisting.

**Q8: What is the teardown order when multiple yield dependencies are active in a single request?**
Teardown executes in LIFO (last-in, first-out) order, mirroring the nesting semantics of Python's `contextlib.ExitStack`. If route handler declares `a = Depends(dep_a)` followed by `b = Depends(dep_b)`, setup runs: `dep_a` then `dep_b`. Teardown runs: `dep_b` first, then `dep_a`. This is important for resource dependencies — if `dep_b` holds a lock that `dep_a`'s teardown needs, the LIFO order ensures `dep_b` releases the lock before `dep_a` attempts to use it.

**Q9: How does `Security()` differ from `Depends()`?**
`Security()` is a subclass of `Depends()` that accepts an additional `scopes` keyword argument (a list of OAuth2 scope strings). At runtime, FastAPI injects a `SecurityScopes` object as a special parameter to the security dependency function, carrying the scopes declared at the call site. This populates the OpenAPI schema with the correct security requirements for each endpoint. Functionally, `Security()` behaves identically to `Depends()` — if you do not need scopes to appear in generated docs, using `Depends()` for auth functions is fine.

**Q10: How do you handle exceptions in a yield dependency's teardown?**
Exceptions raised in the `finally` block of a yield dependency propagate and can mask the original exception from the handler. Best practice: catch and log in `finally`, never re-raise unless you intend to replace the original error. For DB sessions, swallow all teardown errors after logging since the session close may fail if the connection is already broken. Use `contextlib.suppress` or explicit try/except inside `finally` for robust teardown.

**Q11: What is the difference between `Depends` and `app.on_event("startup")` (or `lifespan`)?**
`Depends()` creates a new resource instance per request (or per invocation if `use_cache=False`). `lifespan` (the modern replacement for `on_event`) creates a resource once for the lifetime of the application process. Use `lifespan` for expensive singleton resources: DB engine, connection pool, ML model, HTTP client session. Use `Depends()` for per-request resources: individual DB sessions checked out from the pool, per-request tokens, and context objects derived from the incoming request.

**Q12: Can a dependency raise an `HTTPException`? What happens?**
Yes. An `HTTPException` raised inside a dependency short-circuits the resolution chain. FastAPI's exception handlers catch it, and the route handler never executes. This is the standard pattern for auth guards: `get_current_user` raises `401` if the token is missing or invalid, and the protected handler is never called. All yield dependency teardowns that have already been set up still run in LIFO order before the error response is sent.

**Q13: How do you share a single DB connection across nested dependencies?**
By default, FastAPI caches the result of each dependency callable once per request. If `get_db` is declared at the innermost level and multiple sub-dependencies all declare `Depends(get_db)`, they all receive the same `Session` object. This is the correct behavior — one transaction per request, shared across all data-access operations. No additional coordination is needed as long as all dependencies reference the same callable object.

**Q14: What happens if you declare a sync `yield` dependency but the route is async?**
FastAPI runs sync dependencies in a thread pool executor (using `anyio.to_thread.run_sync`) and async dependencies on the event loop. You can freely mix sync and async dependencies in the same DAG. FastAPI handles the thread-pool/event-loop boundary transparently. The one constraint: a sync dependency cannot `await` — it must be either fully sync or fully async.

**Q15: How do you implement a per-request request ID for tracing?**
```python
import uuid
from starlette.requests import Request

def get_request_id(request: Request) -> str:
    # Starlette's Request object is injectable as a special Depends parameter
    return request.headers.get("X-Request-ID") or str(uuid.uuid4())

@app.get("/data")
def get_data(
    request_id: str = Depends(get_request_id),
) -> dict:
    return {"request_id": request_id}
```
`Request` and `Response` are special FastAPI parameters that do not require `Depends()` wrapping — they are injected automatically when declared in a handler or dependency signature.

**Q16: How do you test a route that has a router-level dependency?**
Router-level dependencies are attached to the `APIRouter` and appear in the route's dependency list the same way per-endpoint dependencies do. `dependency_overrides` applies to them identically: `app.dependency_overrides[router_dep_fn] = mock_fn`. There is no separate mechanism needed for router vs endpoint dependencies.

**Q17: How do you inject settings from environment variables exactly once?**
Use `functools.lru_cache` on the settings factory and inject with `Depends`:
```python
from functools import lru_cache
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    database_url: str
    secret_key: str
    class Config:
        env_file = ".env"

@lru_cache
def get_settings() -> Settings:
    return Settings()

@app.get("/health")
def health(settings: Settings = Depends(get_settings)) -> dict:
    return {"env": settings.database_url[:10]}
```
`lru_cache` ensures the `Settings` object is constructed once per process. `Depends(get_settings)` is still per-request in theory but the `lru_cache` short-circuits the actual `Settings()` constructor call.

**Q18: How would you implement a dependency that conditionally skips expensive work?**
```python
from fastapi import Header

async def maybe_verify_signature(
    x_signature: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> bool:
    if not settings.signature_verification_enabled:
        return True   # fast path: skip in dev
    if x_signature is None:
        raise HTTPException(status_code=400, detail="Missing signature")
    return verify_hmac(x_signature, settings.secret_key)
```
The conditional inside the dependency lets you toggle behavior via configuration without changing the route handler. The route handler only sees the boolean result; it is unaware of whether verification ran.

---

## 13. Best Practices

**1. One yield dependency per resource type.** Never open two DB sessions in the same request from different yield deps that reference the same pool — use shared caching (the default) to ensure one session per request.

**2. Always use `finally` in yield dependencies.** A bare `yield` without a `try/finally` block leaks resources when the handler raises an exception.

**3. Prefer `Annotated` syntax for complex signatures (FastAPI 0.95+).**
```python
from typing import Annotated
CurrentUser = Annotated[User, Depends(get_current_active_user)]

@app.get("/me")
def me(user: CurrentUser) -> UserSchema:
    return UserSchema.model_validate(user)
```
This defines the type alias once and reuses it without repeating `Depends(...)` everywhere.

**4. Keep dependencies single-responsibility.** `get_db` opens a session. `get_current_user` decodes a token and loads the user. Do not combine them into one mega-dependency — composing small pieces is more testable.

**5. Reset `dependency_overrides` in every test teardown.** Use a yield fixture that clears `app.dependency_overrides = {}` unconditionally, whether the test passes or fails.

**6. Do not use `Depends()` for app-level singletons.** Heavy objects (DB engine, ML model, Redis pool) belong in `lifespan`, not in a dependency that recreates them on every request.

**7. Use `Security()` only when you need OpenAPI scope documentation.** For internal auth checks that do not need to appear in Swagger UI, `Depends()` is cleaner and avoids the extra `SecurityScopes` parameter boilerplate.

**8. Validate dependency output with Pydantic.** Return typed Pydantic models from dependencies so callers get validated, IDE-typed objects rather than raw dicts. Use `model_validate` for ORM-to-schema conversion inside the dependency.

**9. Avoid circular dependencies.** FastAPI raises a `ValueError` at startup for circular `Depends()` chains. Design your dependency graph as a DAG: settings → db → user → permissions, never back-edges.

**10. Use `Depends()` on `APIRouter` for cross-cutting concerns.** Auth, rate limiting, tenant resolution, and audit logging all belong at the router level, not repeated on every endpoint.

---

## 14. Case Study

### Building a Multi-Layer Auth + DB Dependency Chain

**Goal:** A FastAPI application serving a B2B SaaS API with per-request DB sessions, JWT auth, role-based access control, and pagination. Fully testable via `dependency_overrides`.

#### BROKEN version: module-level singletons

```python
# BROKEN: module-level singletons — do not do this

from sqlalchemy.orm import Session
from db import engine

_db = Session(engine)          # single session, not thread-safe
_current_user: User | None = None  # global mutable state — wrong

@app.get("/admin/users")
def list_admin_users() -> list[UserSchema]:
    # both _db and _current_user may contain stale state from a prior request
    if _current_user is None or not _current_user.is_superuser:
        raise HTTPException(status_code=403)
    return [UserSchema.model_validate(u) for u in _db.query(User).all()]
```

**Problems:** `_db` is shared across concurrent requests (race condition on transactions). `_current_user` is set by one request and read by another. No cleanup — connections pool starves under load.

#### FIX: full dependency chain

```python
# FIX: main.py
from collections.abc import AsyncGenerator
from typing import Annotated
from functools import lru_cache

import jwt
from fastapi import APIRouter, Depends, FastAPI, HTTPException, Query, Security, status
from fastapi.security import OAuth2PasswordBearer
from pydantic import BaseModel
from pydantic_settings import BaseSettings
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

# ---------- Settings ----------

class Settings(BaseSettings):
    database_url: str = "postgresql+asyncpg://user:pw@localhost/db"
    jwt_secret: str = "change-me"
    jwt_algorithm: str = "HS256"

    class Config:
        env_file = ".env"

@lru_cache
def get_settings() -> Settings:
    return Settings()

SettingsDep = Annotated[Settings, Depends(get_settings)]

# ---------- Database ----------

def _make_engine(settings: Settings):
    return create_async_engine(settings.database_url, pool_size=10, max_overflow=20)

@lru_cache
def get_engine(settings: Settings = Depends(get_settings)):
    return _make_engine(settings)

async def get_db(
    settings: SettingsDep,
) -> AsyncGenerator[AsyncSession, None]:
    engine = _make_engine(settings)
    factory = async_sessionmaker(engine, expire_on_commit=False)
    async with factory() as session:
        try:
            yield session
        except Exception:
            await session.rollback()
            raise

AsyncDB = Annotated[AsyncSession, Depends(get_db)]

# ---------- Auth ----------

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/token")

class TokenPayload(BaseModel):
    sub: str
    scopes: list[str] = []

def _decode_token(token: str, settings: Settings) -> TokenPayload:
    try:
        payload = jwt.decode(
            token,
            settings.jwt_secret,
            algorithms=[settings.jwt_algorithm],
        )
        return TokenPayload(**payload)
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token expired",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )

async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    db: AsyncDB,
    settings: SettingsDep,
) -> "UserModel":
    payload = _decode_token(token, settings)
    user = await db.get(UserModel, int(payload.sub))
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    return user

async def get_current_active_user(
    user: Annotated["UserModel", Depends(get_current_user)],
) -> "UserModel":
    if not user.is_active:
        raise HTTPException(status_code=400, detail="Inactive user")
    return user

async def get_current_active_superuser(
    user: Annotated["UserModel", Depends(get_current_active_user)],
) -> "UserModel":
    if not user.is_superuser:
        raise HTTPException(status_code=403, detail="Not enough permissions")
    return user

CurrentUser = Annotated["UserModel", Depends(get_current_active_user)]
Superuser = Annotated["UserModel", Depends(get_current_active_superuser)]

# ---------- Pagination ----------

class Paginator:
    def __init__(
        self,
        skip: int = Query(default=0, ge=0, description="Items to skip"),
        limit: int = Query(default=20, ge=1, le=200, description="Max items to return"),
    ) -> None:
        self.skip = skip
        self.limit = limit

# ---------- Routers ----------

user_router = APIRouter(
    prefix="/users",
    tags=["users"],
    dependencies=[Depends(get_current_active_user)],   # all routes need auth
)

admin_router = APIRouter(
    prefix="/admin",
    tags=["admin"],
    dependencies=[Depends(get_current_active_superuser)],  # all routes need superuser
)

@user_router.get("/me")
async def read_me(current_user: CurrentUser) -> dict:
    return {"id": current_user.id, "username": current_user.username}

@admin_router.get("/users")
async def list_all_users(
    db: AsyncDB,
    paginator: Paginator = Depends(),
    _superuser: Superuser = Depends(get_current_active_superuser),
) -> list[dict]:
    from sqlalchemy import select
    result = await db.execute(
        select(UserModel).offset(paginator.skip).limit(paginator.limit)
    )
    return [{"id": u.id, "username": u.username} for u in result.scalars()]

# ---------- Application ----------

app = FastAPI(title="Multi-Layer Auth Demo")
app.include_router(user_router)
app.include_router(admin_router)
```

#### Test fixture with `dependency_overrides`

```python
# tests/conftest.py
import pytest
from collections.abc import AsyncGenerator
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from main import app, get_db, get_current_active_user, get_settings, Settings

TEST_DB_URL = "sqlite+aiosqlite:///./test.db"

@pytest.fixture(scope="session")
def test_settings() -> Settings:
    return Settings(database_url=TEST_DB_URL, jwt_secret="test-secret")

@pytest.fixture(scope="session")
async def test_engine(test_settings: Settings):
    engine = create_async_engine(TEST_DB_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield engine
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    await engine.dispose()

@pytest.fixture
async def db_session(test_engine) -> AsyncGenerator[AsyncSession, None]:
    factory = async_sessionmaker(test_engine, expire_on_commit=False)
    async with factory() as session:
        yield session
        await session.rollback()

@pytest.fixture
async def client(db_session: AsyncSession, test_settings: Settings):
    async def override_get_db() -> AsyncGenerator[AsyncSession, None]:
        yield db_session

    def override_get_settings() -> Settings:
        return test_settings

    fake_superuser = UserModel(
        id=1, username="admin", is_active=True, is_superuser=True
    )

    async def override_get_current_user():
        return fake_superuser

    app.dependency_overrides[get_db] = override_get_db
    app.dependency_overrides[get_settings] = override_get_settings
    app.dependency_overrides[get_current_active_user] = override_get_current_user

    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as ac:
        yield ac

    app.dependency_overrides = {}   # FIX: always reset — prevents cross-test contamination

# ---------- Tests ----------

@pytest.mark.asyncio
async def test_list_users_paginated(client: AsyncClient):
    response = await client.get("/admin/users?skip=0&limit=5")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)
    assert len(data) <= 5

@pytest.mark.asyncio
async def test_read_me(client: AsyncClient):
    response = await client.get("/users/me")
    assert response.status_code == 200
    assert response.json()["username"] == "admin"
```

#### Dependency graph for this case study

```
GET /admin/users
      |
      +---> get_current_active_superuser (router-level dep)
      |             |
      |             +---> get_current_active_user
      |                         |
      |                         +---> get_current_user
      |                                     |
      |                                     +---> oauth2_scheme
      |                                     +---> get_db  <------ (cached)
      |                                     +---> get_settings <-- (lru_cache)
      |
      +---> db: AsyncDB = Depends(get_db)  <-- cache HIT (same session as above)
      |
      +---> paginator: Paginator = Depends()
                  |
            __init__(skip: int, limit: int)  from query params
```

All sub-components are testable in isolation by overriding only the relevant dependency. The route handler contains zero auth logic, zero DB connection management — it is pure business logic.

Cross-reference: For auth dependencies specifically, see [`../authentication_and_security/README.md`](../authentication_and_security/README.md).

---
