# Async Database with SQLAlchemy 2.0

> See `../dependency_injection_in_fastapi/README.md` for `yield`-based session dependencies and `use_cache=False` scoping.
> See `../pydantic_v2_deep_dive/README.md` for Pydantic model integration with SQLModel.

---

## 1. Concept Overview

SQLAlchemy 2.0 (released January 2023) unified the Core and ORM APIs under a single expression language and introduced first-class async support through `AsyncEngine` and `AsyncSession`. The old 1.x `Query` object is gone; all queries now use the same `select()` / `insert()` / `update()` / `delete()` constructors regardless of whether you are running synchronously or asynchronously.

Core capabilities covered in this module:

- `create_async_engine()` and `async_sessionmaker()` — async engine/session factory setup
- `AsyncSession` — all DML operations are `await`-ed; no implicit I/O
- Driver landscape: `asyncpg` (PostgreSQL), `aiopg`, `aiomysql`, `aiosqlite` — each with its own SQLAlchemy dialect
- Session-per-request pattern in FastAPI via `yield` dependency
- Connection pool sizing: `pool_size`, `max_overflow`, `pool_timeout`, `pool_recycle` with concrete defaults
- N+1 query detection and elimination: `selectinload`, `joinedload`, `subqueryload`
- Transaction scoping with `AsyncSession.begin()` context manager
- `expire_on_commit=False` — mandatory for async sessions
- Alembic async migrations: `run_sync` wrapper in `env.py`
- Lazy loading trap: `MissingGreenlet` error and its fix
- Bulk inserts via `session.execute(insert(Model), [...])`
- SQLModel: Pydantic+SQLAlchemy bridge — when it helps and when it hurts

Python version: 3.11/3.12. SQLAlchemy version: 2.0+. FastAPI version: 0.110+.

---

## 2. Intuition

> SQLAlchemy async is like a courier service that only dispatches trucks (database calls) when you explicitly say "go" — it will never silently sneak out to the warehouse in the background.

**Mental model.** In synchronous SQLAlchemy 1.x, the ORM could issue database queries at arbitrary points: accessing an unloaded relationship would transparently trigger a new `SELECT`. In async Python that is impossible — there is no implicit I/O. Every database round-trip must be an `await` expression. SQLAlchemy 2.0 enforces this contract: any code path that would issue implicit I/O raises a `MissingGreenlet` error immediately, forcing you to declare all data needs upfront via eager loading options.

**Why it matters.** FastAPI runs on an async event loop (uvicorn). If database calls block the event loop for even 5 ms per request, at 500 RPS you accumulate 2.5 seconds of stalled processing per second — enough to saturate the loop and cascade into latency spikes. Async database access (`asyncpg` delivers ~3× the throughput of psycopg2 in connection-saturated benchmarks) keeps the event loop free for other coroutines while waiting on network I/O.

**Key insight.** The SQLAlchemy 2.0 unified API is the same in sync and async: `select(User).where(User.id == 1)` is identical — only the execution step differs (`session.execute(...)` vs `await session.execute(...)`). This means you can write query logic once in a framework-agnostic function and call it from either context.

---

## 3. Core Principles

**1. No implicit I/O.** Every database operation is an awaitable coroutine. There are no hidden round-trips. Relationship loading must be declared before query execution using `options(selectinload(...))` or `options(joinedload(...))`.

**2. Session-per-request.** An `AsyncSession` is not thread-safe and not coroutine-safe across concurrent tasks. One session is opened at the start of each request and closed when the request completes — whether by normal return or exception. FastAPI `yield` dependencies manage this lifecycle automatically.

**3. `expire_on_commit=False` is required.** By default, SQLAlchemy expires all loaded attributes after `commit()`. In sync code this triggers a lazy reload on next access. In async code that reload would require an `await` that Python cannot insert transparently, so the attribute raises `MissingGreenlet`. Disabling expiry means attributes stay valid post-commit, which is the correct behavior for request-response cycles where the session closes immediately after.

**4. Connection pool belongs to the engine, not the session.** The `AsyncEngine` holds the pool. Sessions borrow connections from the pool only for the duration of an active transaction or query. Sessions should be short-lived (one request); engines should be long-lived (application lifetime).

**5. Eager loading by default in async.** Assume any relationship you will touch in a handler must be listed in the query's `options(...)`. Lazy loading is not available; `raise_on_load=True` can be set on relationships to surface accidental lazy access at test time rather than in production.

---

## 4. Types / Architectures / Strategies

### 4.1 Driver matrix

| Driver | Database | SQLAlchemy dialect | Notes |
|--------|----------|-------------------|-------|
| `asyncpg` | PostgreSQL | `postgresql+asyncpg` | Binary protocol, fastest PG driver; no `psycopg2` compatibility |
| `aiopg` | PostgreSQL | `postgresql+aiopg` | libpq-based; slower than asyncpg; use only if psycopg2 features required |
| `aiomysql` | MySQL / MariaDB | `mysql+aiomysql` | Pure-Python async MySQL |
| `asyncmy` | MySQL / MariaDB | `mysql+asyncmy` | Modern replacement for aiomysql, better maintained |
| `aiosqlite` | SQLite | `sqlite+aiosqlite` | Dev/test only; single-writer, not for production |

### 4.2 Session lifecycle strategies

**Per-request session (recommended for FastAPI).**
A new `AsyncSession` is created at the start of each request via a `yield` dependency. The session is committed or rolled back before the dependency's `finally` block closes it. This provides transaction isolation per request with zero leakage.

**Per-operation session.**
A new session is created and closed around each individual query. Suitable for background jobs that issue isolated queries, not for request handlers (too many pool checkouts per request).

**Scoped session (sync only, not applicable in async).**
SQLAlchemy's `scoped_session` uses thread-locals, which have no equivalent in async. Do not use `scoped_session` with async code.

### 4.3 Eager loading strategies

| Strategy | SQL generated | Best for |
|----------|--------------|---------|
| `selectinload` | `SELECT ... WHERE id IN (...)` | One-to-many, avoids cartesian product |
| `joinedload` | `LEFT OUTER JOIN` | Many-to-one, single row result (parent + one child) |
| `subqueryload` | `SELECT ... WHERE parent_id IN (SELECT id ...)` | Legacy; usually worse than selectinload |
| `raiseload` | No SQL, raises on access | Defensive default to catch accidental lazy access |
| `noload` | No SQL, returns `None`/`[]` | When relationship data is never needed |

### 4.4 Transaction patterns

**Auto-begin (default in 2.0).** `AsyncSession` auto-begins a transaction on the first DML or `execute()`. You call `await session.commit()` to finalize.

**Explicit `begin()` block.** Use `async with session.begin():` to scope a transaction to a block. The transaction commits on exit, rolls back on exception. Preferred for clarity in service-layer code.

**Savepoints.** `async with session.begin_nested():` creates a `SAVEPOINT`. Useful for partial rollback within a larger transaction (e.g., attempt an insert, roll back only that insert if it fails, continue the outer transaction).

---

## 5. Architecture Diagrams

### 5.1 Request lifecycle with async session

```
FastAPI Request
      |
      v
 DI Graph Resolution
      |
      +---> get_async_session() [yield dependency]
      |           |
      |           v
      |     AsyncEngine
      |           |
      |           v
      |     Connection Pool  <----> PostgreSQL (asyncpg)
      |           |
      |     AsyncSession (borrowed connection)
      |           |
      v           v
 Route Handler receives session
      |
      +---> await session.execute(select(...))
      |           |
      |           v
      |     SQL compiled + sent via asyncpg
      |           |
      |     rows returned
      |           |
      +---> await session.commit()  [or rollback on error]
      |
      v
 Response serialized (Pydantic)
      |
 [finally block in yield dep]
      |
      v
 await session.close()  [connection returned to pool]
```

### 5.2 N+1 query problem: before and after

```
BROKEN — N+1 pattern:
  SELECT * FROM users  (1 query, returns 100 users)
  SELECT * FROM posts WHERE user_id = 1
  SELECT * FROM posts WHERE user_id = 2
  ...
  SELECT * FROM posts WHERE user_id = 100
  Total: 101 queries

FIX — selectinload:
  SELECT * FROM users  (1 query)
  SELECT * FROM posts WHERE user_id IN (1,2,3,...,100)  (1 query)
  Total: 2 queries
```

### 5.3 Connection pool sizing

```
PostgreSQL server
  max_connections = 200 (default)
       |
       | accepts up to 200 simultaneous connections
       |
  Load Balancer / PgBouncer (optional)
       |
  +---------+---------+---------+
  |         |         |         |
Worker-1  Worker-2  Worker-3  Worker-4   (4 Uvicorn workers)
pool_size=5, max_overflow=10
each worker: up to 15 connections
total: 4 × 15 = 60 connections
(leaves headroom for admin, migrations, monitoring)
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Engine and session factory setup

```python
# database.py
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    AsyncEngine,
    create_async_engine,
    async_sessionmaker,
)
from sqlalchemy.orm import DeclarativeBase

DATABASE_URL = "postgresql+asyncpg://user:pass@localhost:5432/mydb"

engine: AsyncEngine = create_async_engine(
    DATABASE_URL,
    echo=False,           # set True during development to log all SQL
    pool_size=5,          # base persistent connections (default: 5)
    max_overflow=10,      # additional connections allowed above pool_size (default: 10)
    pool_timeout=30,      # seconds to wait for a connection before TimeoutError (default: 30)
    pool_recycle=1800,    # recycle connections older than 30 min (prevents stale TCP)
    pool_pre_ping=True,   # issue SELECT 1 before checkout to detect dead connections
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,  # REQUIRED: prevent MissingGreenlet after commit
    autoflush=False,         # manual control; flush before query if needed
    autocommit=False,        # always use explicit transactions
)


class Base(DeclarativeBase):
    pass
```

### 6.2 Models

```python
# models.py
from __future__ import annotations
from typing import TYPE_CHECKING
from sqlalchemy import String, ForeignKey, Text
from sqlalchemy.orm import Mapped, mapped_column, relationship
from database import Base

if TYPE_CHECKING:
    from sqlalchemy.ext.asyncio import AsyncSession


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(100), nullable=False)

    # lazy="raise" catches accidental lazy access at test time
    posts: Mapped[list[Post]] = relationship(
        "Post", back_populates="author", lazy="raise"
    )


class Post(Base):
    __tablename__ = "posts"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    author_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False)

    author: Mapped[User] = relationship("User", back_populates="posts", lazy="raise")
```

### 6.3 FastAPI yield dependency

```python
# dependencies.py
from collections.abc import AsyncGenerator
from sqlalchemy.ext.asyncio import AsyncSession
from database import AsyncSessionLocal


async def get_async_session() -> AsyncGenerator[AsyncSession, None]:
    """
    Yield a database session for the duration of one HTTP request.
    Commits on success, rolls back on any exception, always closes.
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        # AsyncSessionLocal context manager calls session.close() on exit
```

Usage in a route:

```python
# routes/users.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload

from dependencies import get_async_session
from models import User, Post

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/{user_id}/posts")
async def list_user_posts(
    user_id: int,
    session: AsyncSession = Depends(get_async_session),
) -> list[dict]:
    result = await session.execute(
        select(User)
        .options(selectinload(User.posts))
        .where(User.id == user_id)
    )
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=404, detail="User not found")
    return [{"id": p.id, "title": p.title} for p in user.posts]
```

### 6.4 CRUD operations with 2.0 API

```python
# repositories/user_repo.py
from sqlalchemy import select, update, delete
from sqlalchemy.ext.asyncio import AsyncSession
from models import User


async def get_user_by_id(session: AsyncSession, user_id: int) -> User | None:
    result = await session.execute(select(User).where(User.id == user_id))
    return result.scalar_one_or_none()


async def create_user(session: AsyncSession, email: str, name: str) -> User:
    user = User(email=email, name=name)
    session.add(user)
    await session.flush()  # assigns user.id without committing
    return user


async def update_user_name(
    session: AsyncSession, user_id: int, new_name: str
) -> int:
    """Returns number of rows updated."""
    result = await session.execute(
        update(User).where(User.id == user_id).values(name=new_name)
    )
    return result.rowcount


async def delete_user(session: AsyncSession, user_id: int) -> int:
    result = await session.execute(delete(User).where(User.id == user_id))
    return result.rowcount
```

### 6.5 Bulk insert

```python
from sqlalchemy.dialects.postgresql import insert as pg_insert

async def bulk_create_users(
    session: AsyncSession, users: list[dict]
) -> None:
    """
    Insert many rows in a single round-trip.
    `users` is a list of dicts: [{"email": "...", "name": "..."}, ...]
    """
    await session.execute(
        pg_insert(User).on_conflict_do_nothing(index_elements=["email"]),
        users,
    )
    # Equivalent for non-Postgres:
    # from sqlalchemy import insert
    # await session.execute(insert(User), users)
```

Bulk insert sends all rows in one protocol message. For 10,000 rows, `asyncpg` with bulk execute takes ~80 ms vs ~4,000 ms for a Python loop of individual `session.add()` calls.

### 6.6 Alembic async migrations

`alembic/env.py` patch for async engine:

```python
# alembic/env.py  (relevant sections only)
import asyncio
from logging.config import fileConfig
from sqlalchemy.ext.asyncio import async_engine_from_config
from sqlalchemy import pool
from alembic import context

config = context.config
fileConfig(config.config_file_name)

# Import all models so Base.metadata is populated
from models import Base  # noqa: F401 (import for side effects)
target_metadata = Base.metadata


def run_migrations_offline() -> None:
    url = config.get_main_option("sqlalchemy.url")
    context.configure(url=url, target_metadata=target_metadata, literal_binds=True)
    with context.begin_transaction():
        context.run_migrations()


def do_run_migrations(connection):
    context.configure(connection=connection, target_metadata=target_metadata)
    with context.begin_transaction():
        context.run_migrations()


async def run_migrations_online() -> None:
    connectable = async_engine_from_config(
        config.get_section(config.config_ini_section),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,  # migrations don't need pooling
    )
    async with connectable.connect() as connection:
        # run_sync bridges async connection to synchronous Alembic migration runner
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()


if context.is_offline_mode():
    run_migrations_offline()
else:
    asyncio.run(run_migrations_online())
```

---

## 7. Real-World Examples

**FastAPI official tutorial.** The FastAPI documentation uses `async_sessionmaker` + `yield` dependency as the canonical session pattern. Session commits are either in the dependency itself or delegated to service functions.

**Tiangolo's full-stack FastAPI template.** Uses `create_async_engine` with `postgresql+asyncpg`, a global `engine` and `async_sessionmaker` at module level, and a single `get_db` yield dependency injected into every route. `expire_on_commit=False` is explicitly set.

**Pydantic's internal test suite.** Uses `aiosqlite` with `sqlite+aiosqlite` dialect for in-memory database tests, demonstrating the same `async_sessionmaker` pattern across all supported backends.

**Encode's Starlette.** While Starlette itself uses `databases` (a thin async wrapper), many production Starlette-based apps have migrated to SQLAlchemy 2.0 async after the 2.0 release stabilized `asyncpg` dialect support.

**Litestar (formerly Starlite).** Provides first-class SQLAlchemy 2.0 integration via `AdvancedAlchemy` plugin, which generates `AsyncSession` dependencies, health checks, and repository base classes automatically.

---

## 8. Tradeoffs

| Concern | Async SQLAlchemy 2.0 | Sync SQLAlchemy 2.0 | SQLModel | Tortoise ORM |
|---------|---------------------|---------------------|----------|-------------|
| **Throughput** | High (non-blocking I/O) | Moderate (blocks event loop) | Same as underlying SA | High |
| **Complexity** | High (eager loading required) | Low (lazy loading works) | Medium | Low |
| **ORM maturity** | Very high (20+ years) | Very high | Medium (built on SA) | Medium |
| **Pydantic integration** | Manual (separate schemas) | Manual | Native (model = schema) | Manual |
| **Alembic support** | Yes (with run_sync wrapper) | Yes (native) | Yes (via SA) | Limited (own migration tool) |
| **Driver ecosystem** | asyncpg, aiopg, aiomysql | psycopg2, pymysql, cx_Oracle | Inherits SA | asyncpg, aiopg |
| **Django-style ActiveRecord** | No | No | Partial | Yes |

**SQLModel tradeoffs in detail.**

Pros: One class serves as both Pydantic schema and SQLAlchemy table — eliminates the duplication of maintaining separate `UserCreate`, `UserRead`, `UserInDB` models. Reduces boilerplate for small to medium projects.

Cons: The Pydantic model and database model are the same object, which breaks down when validation rules and database constraints diverge (e.g., a `password` field that should never be serialized back). SQLModel also lags behind SQLAlchemy releases; as of 2024, full Mapped[]/mapped_column() support in SQLModel is still incomplete. For production systems with complex validation logic, separate Pydantic schemas plus plain SQLAlchemy models is the more maintainable pattern.

---

## 9. When to Use / When NOT to Use

**Use async SQLAlchemy 2.0 when:**

- Your FastAPI/Starlette application is I/O-bound and handles hundreds of concurrent requests
- You are using PostgreSQL and want maximum throughput via `asyncpg`
- You need SQLAlchemy's full ORM feature set: relationships, events, hybrid properties, polymorphism
- You need Alembic for schema migrations (the ecosystem is incomparably mature)
- Your team already knows SQLAlchemy 1.x and can accept the 2.0 migration cost

**Do NOT use async SQLAlchemy 2.0 when:**

- You are writing a synchronous script, CLI, or batch job — synchronous SQLAlchemy is simpler and lazy loading works
- Your schema is document-oriented or schema-less — MongoDB with Motor is a better fit
- You need an ActiveRecord pattern with migrations on a small project — Tortoise ORM or SQLModel may be simpler
- You are blocked on an `asyncpg` bug and need `psycopg3` async support — `psycopg3` has its own async driver and SA 2.0 dialect but was less mature at 2.0 launch
- Your team is unfamiliar with async Python and the `MissingGreenlet` errors will slow development unacceptably — start with sync and migrate later

---

## 10. Common Pitfalls

### Pitfall 1: Lazy loading in async context (MissingGreenlet)

```python
# BROKEN: accessing lazy relationship in async context
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from models import User, Post


async def get_user_posts(session: AsyncSession, user_id: int) -> list[Post]:
    result = await session.execute(select(User).where(User.id == user_id))
    user = result.scalar_one()
    return user.posts  # MissingGreenlet: lazy load attempted in async context
    # SQLAlchemy tries to issue SELECT * FROM posts WHERE user_id=?
    # but cannot find a greenlet/event-loop to run it in


# FIX: use selectinload for eager loading
from sqlalchemy.orm import selectinload


async def get_user_posts(session: AsyncSession, user_id: int) -> list[Post]:
    result = await session.execute(
        select(User)
        .options(selectinload(User.posts))  # loads posts in a second SELECT ... IN (...)
        .where(User.id == user_id)
    )
    user = result.scalar_one()
    return user.posts  # eager-loaded, safe in async
```

**Root cause.** SQLAlchemy's lazy loader tries to issue a synchronous DB call. In async mode there is no greenlet context to run it in, so it raises `sqlalchemy.exc.MissingGreenlet`. The fix is always to declare the relationship load strategy in the query.

### Pitfall 2: Forgetting `expire_on_commit=False`

```python
# BROKEN: default expire_on_commit=True with async session
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession)

async def create_and_return_user(session: AsyncSession, email: str) -> dict:
    user = User(email=email, name="Alice")
    session.add(user)
    await session.commit()
    # After commit, SQLAlchemy expires all attributes by default.
    # Accessing user.email here triggers a lazy reload => MissingGreenlet
    return {"id": user.id, "email": user.email}  # MissingGreenlet on user.email


# FIX: set expire_on_commit=False in the session factory
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,  # attributes remain valid after commit
)

async def create_and_return_user(session: AsyncSession, email: str) -> dict:
    user = User(email=email, name="Alice")
    session.add(user)
    await session.commit()
    return {"id": user.id, "email": user.email}  # safe: no lazy reload attempted
```

### Pitfall 3: Creating `AsyncEngine` inside a request handler

```python
# BROKEN: engine created per-request — no connection pooling benefit
from fastapi import APIRouter
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker

router = APIRouter()

@router.get("/users/{user_id}")
async def get_user(user_id: int):
    engine = create_async_engine("postgresql+asyncpg://...")  # new engine every call
    SessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)
    async with SessionLocal() as session:
        ...


# FIX: engine and session factory are module-level singletons
# (in database.py, created once at startup)
engine = create_async_engine("postgresql+asyncpg://...", pool_size=5, max_overflow=10)
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)

@router.get("/users/{user_id}")
async def get_user(
    user_id: int,
    session: AsyncSession = Depends(get_async_session),
):
    ...
```

### Pitfall 4: N+1 queries from looping over results

```python
# BROKEN: N+1 — fetches each user's posts in a separate query inside a loop
async def get_all_users_with_post_count(session: AsyncSession) -> list[dict]:
    result = await session.execute(select(User))
    users = result.scalars().all()
    output = []
    for user in users:
        # BROKEN: each iteration triggers a new SELECT (or MissingGreenlet)
        posts_result = await session.execute(
            select(Post).where(Post.author_id == user.id)
        )
        posts = posts_result.scalars().all()
        output.append({"user": user.name, "post_count": len(posts)})
    return output


# FIX: use selectinload to load posts in bulk, then aggregate in Python
from sqlalchemy.orm import selectinload

async def get_all_users_with_post_count(session: AsyncSession) -> list[dict]:
    result = await session.execute(
        select(User).options(selectinload(User.posts))
    )
    users = result.scalars().all()
    return [{"user": u.name, "post_count": len(u.posts)} for u in users]
    # 2 queries total regardless of user count
```

### Pitfall 5: Sharing a session across concurrent tasks

```python
# BROKEN: same session used in two concurrent coroutines
import asyncio

async def broken_concurrent_reads(session: AsyncSession, ids: list[int]):
    tasks = [
        session.execute(select(User).where(User.id == uid))
        for uid in ids
    ]
    results = await asyncio.gather(*tasks)  # session is not concurrency-safe!
    # May raise: sqlalchemy.exc.InvalidRequestError: "This transaction is closed"
    # or produce undefined results from interleaved state


# FIX: use separate sessions per concurrent task, or use sequential execution
async def safe_concurrent_reads(ids: list[int]) -> list[User | None]:
    async def fetch_one(uid: int) -> User | None:
        async with AsyncSessionLocal() as session:
            result = await session.execute(select(User).where(User.id == uid))
            return result.scalar_one_or_none()

    return await asyncio.gather(*[fetch_one(uid) for uid in ids])
```

---

## 11. Technologies & Tools

| Tool | Role | Key feature | Limitation |
|------|------|------------|-----------|
| **SQLAlchemy 2.0** | ORM + Core | Unified API, full async, Alembic | Higher learning curve vs Django ORM |
| **asyncpg** | PostgreSQL async driver | Binary protocol, fastest throughput | No `psycopg2` API compatibility |
| **aiosqlite** | SQLite async driver | Zero-config for tests | Single writer; not for production |
| **aiomysql / asyncmy** | MySQL/MariaDB async driver | asyncmy is actively maintained | Smaller ecosystem vs asyncpg |
| **Alembic** | Schema migration | Autogenerate, version control | Requires `run_sync` wrapper for async |
| **SQLModel** | Pydantic+SA bridge | One class = schema + table | Lags SA releases; limited Mapped[] support |
| **GreenletIO** | Enables sync-in-async | Powers SA's `run_sync` | Internal; not a user-facing API |
| **PgBouncer** | Connection pooling proxy | Transaction-mode pooling reduces PG connections | Adds infrastructure complexity |

---

## 12. Interview Questions with Answers

**Q1: Why does accessing a lazy relationship in async SQLAlchemy raise `MissingGreenlet` instead of just blocking?**

SQLAlchemy's lazy loader is implemented using greenlets — lightweight coroutines provided by the `greenlet` library — to issue synchronous SQL from within async execution contexts. When there is no active greenlet context (which is the case in native async code that runs on Python's `asyncio` event loop), the lazy loader cannot execute and raises `MissingGreenlet`. The root cause is that implicit I/O requires a synchronous execution context, which does not exist in a pure asyncio coroutine. Fix: always declare relationship loading strategies (`selectinload`, `joinedload`) in the query before execution.

**Q2: What is the difference between `selectinload` and `joinedload`, and when do you choose each?**

`selectinload` issues a second `SELECT ... WHERE id IN (...)` query after loading the parent objects. `joinedload` issues a single `LEFT OUTER JOIN` query. Use `joinedload` for many-to-one (or one-to-one) relationships where there is at most one related object per parent row — a join does not multiply rows. Use `selectinload` for one-to-many and many-to-many relationships where a join would produce a cartesian product, inflating result set size and wasting bandwidth. Practical rule: if loading 100 users each with 50 posts, a join returns 5,000 rows; selectinload returns 100 + 100 = 200 rows in two queries.

**Q3: Why must `expire_on_commit=False` be set for async sessions?**

After `session.commit()`, SQLAlchemy by default marks all loaded ORM attributes as "expired". In synchronous code, accessing an expired attribute transparently issues a new `SELECT` to refresh the value. In async code, this implicit reload has no `await` and raises `MissingGreenlet`. With `expire_on_commit=False`, attributes retain their in-memory values post-commit and no implicit reload is attempted. The tradeoff is that if another process modifies the same row after your commit, you will read stale data — acceptable for request-response cycles where the session closes immediately after the commit.

**Q4: How do you configure connection pool size for an async SQLAlchemy engine in a multi-worker FastAPI deployment?**

The formula is: total connections = `(pool_size + max_overflow) × num_workers`. With `pool_size=5`, `max_overflow=10`, and 4 workers, the maximum is 60 connections. PostgreSQL's default `max_connections` is 100; subtract 10 for admin and monitoring, leaving 90 usable connections for the application. For 4 workers: `(pool_size + max_overflow) × 4 ≤ 90` → `pool_size + max_overflow ≤ 22` → `pool_size=5, max_overflow=10` is safe with two workers of headroom. For high-concurrency deployments, add PgBouncer in transaction mode to multiplex more application connections onto fewer server connections.

**Q5: What does `pool_pre_ping=True` do and when should you enable it?**

`pool_pre_ping=True` causes SQLAlchemy to issue a lightweight `SELECT 1` query each time a connection is checked out from the pool. If the query fails (because the server restarted, the TCP connection was silently dropped, or a firewall timeout closed the connection), the driver discards that connection and checks out a fresh one. It prevents `OperationalError: server closed the connection unexpectedly` errors that occur when stale connections are used. Enable it for any production deployment where the database or network may close idle connections. The cost is one extra round-trip per checkout, typically under 1 ms on a local network.

**Q6: How does `pool_recycle` differ from `pool_pre_ping`?**

`pool_recycle=N` closes and recreates connections that have been open for more than N seconds, preventing issues with MySQL's `wait_timeout` (default 8 hours) or PostgreSQL idle connection limits. `pool_pre_ping=True` validates connections at checkout time regardless of age. They solve different problems: `pool_recycle` handles maximum connection lifetime; `pool_pre_ping` handles connections that died unexpectedly between uses. Use both together in production: `pool_recycle=1800` (30 minutes) with `pool_pre_ping=True`.

**Q7: How do you perform Alembic migrations with an async engine?**

Alembic's migration runner is synchronous. The bridge is `connection.run_sync(do_run_migrations)`, which executes the migration function synchronously within an async database connection. In `alembic/env.py`, replace `engine_from_config` with `async_engine_from_config`, open an async connection with `async with connectable.connect() as connection`, and call `await connection.run_sync(do_run_migrations)`. Use `asyncio.run(run_migrations_online())` at the bottom of `env.py` to drive the async function from Alembic's synchronous context.

**Q8: What is the difference between `session.flush()` and `session.commit()`?**

`flush()` sends pending SQL statements (INSERTs, UPDATEs, DELETEs) to the database within the current transaction, making changes visible to subsequent queries in the same session. The transaction is not committed; changes are invisible to other connections. `commit()` finalizes the transaction, making changes visible to all connections. Use `flush()` to obtain a database-assigned primary key (e.g., `user.id`) before committing, or to enforce constraints before a larger operation. In async code, both are awaited: `await session.flush()`, `await session.commit()`.

**Q9: How do you implement an upsert (insert-or-update) with async SQLAlchemy on PostgreSQL?**

Use the `postgresql+asyncpg` dialect's `insert().on_conflict_do_update()`:

```python
from sqlalchemy.dialects.postgresql import insert

stmt = (
    insert(User)
    .values(email="alice@example.com", name="Alice")
    .on_conflict_do_update(
        index_elements=["email"],
        set_={"name": insert.excluded.name},
    )
)
await session.execute(stmt)
```

`insert.excluded` refers to the row that was proposed for insertion. This compiles to `INSERT ... ON CONFLICT (email) DO UPDATE SET name = EXCLUDED.name`.

**Q10: How do you detect N+1 queries in a SQLAlchemy application?**

Set `echo=True` on the engine during development: all SQL statements are logged to stdout. Count how many `SELECT` statements are emitted per request — if a request that loads 50 users emits 51 queries, you have N+1. For production profiling, use `sqlalchemy-utils` or middleware that wraps the session and counts queries. The `pytest-sqlalchemy-mock` library can assert query counts in tests: `assert query_counter.count == 2`. For continuous monitoring, emit a custom metric from a SQLAlchemy event listener on `after_execute`.

**Q11: Can you use `AsyncSession` inside a Celery task?**

Celery tasks are synchronous by default and run in a thread pool, not an asyncio event loop. Running async code in a Celery task requires `asyncio.run(my_async_function())` inside the task, which creates a new event loop per task call. This works but is inefficient. Better alternatives: (1) use sync SQLAlchemy in Celery tasks, (2) use `celery[gevent]` or `celery[eventlet]` for async workers, or (3) migrate background tasks to `ARQ` or `Dramatiq` which have native async support.

**Q12: What is the transaction isolation level for `AsyncSession` by default, and how do you change it?**

The default isolation level is determined by the database driver and server configuration. For PostgreSQL via asyncpg, the default is `READ COMMITTED`. To change isolation for a specific transaction:

```python
async with engine.connect() as conn:
    await conn.execution_options(isolation_level="REPEATABLE READ")
    # or "SERIALIZABLE", "READ UNCOMMITTED"
    async with AsyncSession(bind=conn) as session:
        ...
```

For the entire engine: `create_async_engine(url, isolation_level="REPEATABLE READ")`.

**Q13: How do you test FastAPI routes that use async SQLAlchemy sessions?**

Use `dependency_overrides` to inject a test session that is rolled back after each test:

```python
import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from app.main import app
from app.dependencies import get_async_session

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

@pytest.fixture
async def async_session():
    engine = create_async_engine(TEST_DATABASE_URL)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    async with session_factory() as session:
        yield session
        await session.rollback()
    await engine.dispose()

@pytest.fixture
async def client(async_session: AsyncSession):
    app.dependency_overrides[get_async_session] = lambda: async_session
    async with AsyncClient(app=app, base_url="http://test") as c:
        yield c
    app.dependency_overrides.clear()
```

**Q14: What happens when you call `session.execute()` without an active transaction in SQLAlchemy 2.0?**

In SQLAlchemy 2.0, `AsyncSession` uses "autobegin" behavior: the first DML or `execute()` call automatically begins a transaction. There is no need to explicitly call `session.begin()` before the first query. However, you must explicitly call `await session.commit()` or `await session.rollback()` when done — there is no autocommit. If you discard the session without committing, the transaction is rolled back when the session is closed.

**Q15: How do you handle database connection errors and retry logic with async SQLAlchemy?**

SQLAlchemy raises `sqlalchemy.exc.OperationalError` (subclass of `DBAPIError`) for connection-level failures. Implement retry at the service layer using `tenacity`:

```python
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from sqlalchemy.exc import OperationalError


@retry(
    retry=retry_if_exception_type(OperationalError),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=0.1, min=0.1, max=2),
)
async def resilient_query(session: AsyncSession, user_id: int) -> User | None:
    result = await session.execute(select(User).where(User.id == user_id))
    return result.scalar_one_or_none()
```

Note: do not retry within a failed transaction — roll back first, then retry. The `yield` dependency pattern handles this by rolling back on exception before the session is closed.

**Q16: What is the `lazy="raise"` relationship strategy and when should you use it?**

`lazy="raise"` configures a relationship so that accessing it without eager loading raises `InvalidRequestError` immediately with a clear message like "Mapped attribute 'User.posts' is not available due to lazy='raise'". This is used as a defensive default in large teams: setting `lazy="raise"` on all relationships forces developers to explicitly list every relationship they need in query options, preventing accidental N+1 queries from being silently introduced. Use it in production code where strict access patterns are required; revert to `lazy="select"` in data exploration or admin tools where flexibility is more important.

---

## 13. Best Practices

1. **Set `expire_on_commit=False` unconditionally in async session factories.** There is no scenario in async FastAPI code where the default expiry behavior is desirable. Make it the default in your `async_sessionmaker` definition.

2. **Set `pool_pre_ping=True` in production.** The cost (~0.5 ms per checkout) is negligible compared to the operational pain of debugging stale-connection errors at 2 AM.

3. **Use `lazy="raise"` on all relationships in production models.** It surfaces accidental lazy loads during development and testing rather than in production under load.

4. **Always use the 2.0-style `select()` API.** The legacy `session.query(User)` style is soft-deprecated and removed in future versions. Write `select(User)` from day one.

5. **Keep `AsyncEngine` and `async_sessionmaker` at module level.** Create them once at application startup, not inside request handlers or dependency functions. This is the only way the connection pool provides value.

6. **Separate your repository layer from your route layer.** Routes call repository functions; repository functions accept a session parameter. This makes testing trivial: inject a test session into the repository function without HTTP overhead.

7. **Scope transactions to the service layer, not the repository layer.** Individual repository methods should not commit; the service function that orchestrates multiple repository calls should own the transaction boundary. The FastAPI yield dependency commits only if no exception was raised by the route handler.

8. **Use `await session.flush()` to get auto-generated IDs before committing.** If you need to reference a just-inserted row's primary key within the same request (e.g., to insert a related row), flush first to trigger the `RETURNING id` round-trip.

9. **Configure `pool_recycle` for long-running processes.** Set it to a value shorter than both PostgreSQL's `idle_in_transaction_session_timeout` and any firewall or load-balancer idle timeout (often 60–300 seconds on cloud providers). `pool_recycle=1800` is a safe default.

10. **Use `session.execute(insert(Model), list_of_dicts)` for bulk inserts.** For inserting 100+ rows, a single execute call with a list is orders of magnitude faster than looping `session.add()`. Benchmark: 1,000-row bulk insert via `asyncpg` takes ~15 ms; individual adds take ~3,000 ms.

11. **Run Alembic autogenerate in CI.** After every model change, run `alembic revision --autogenerate -m "..."` and commit the resulting migration file. Add a CI check that verifies the generated migration is not empty (which would indicate a model change without a corresponding migration).

12. **Do not share sessions across concurrent tasks.** `AsyncSession` is not safe for concurrent use. Each task that issues queries concurrently must have its own session.

---

## 14. Case Study

### Scenario: Multi-tenant SaaS API with async SQLAlchemy

A B2B SaaS platform serves 200 enterprise tenants. Each tenant has users who create documents. The API must:
- List a tenant's users with their document counts (N+1 trap)
- Create documents with attachment metadata in a single transaction (multi-table insert)
- Soft-delete users and cascade to documents (transaction with multiple operations)

Scale: 500 RPS peak, PostgreSQL RDS `db.r6g.2xlarge` (8 vCPU, 64 GB), 4 Uvicorn workers.

### Architecture

```
Client Request
     |
     v
Uvicorn Worker (4 workers)
     |
     v
FastAPI App
     |
     +---> Auth dependency (JWT validation)
     |
     +---> get_async_session [yield dep] -----> AsyncEngine
     |                                              |
     |                                         pool_size=5
     |                                         max_overflow=10
     |                                         pool_recycle=1800
     |                                         pool_pre_ping=True
     |                                              |
     v                                         PostgreSQL RDS
Route Handler
     |
     v
Service Layer (service/document_service.py)
     |
     v
Repository Layer (repositories/user_repo.py, document_repo.py)
     |
     v
SQLAlchemy 2.0 async ORM
```

### Models

```python
# models.py
from __future__ import annotations
from datetime import datetime
from sqlalchemy import String, ForeignKey, BigInteger, Boolean, DateTime, func
from sqlalchemy.orm import Mapped, mapped_column, relationship
from database import Base


class Tenant(Base):
    __tablename__ = "tenants"
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    users: Mapped[list[User]] = relationship("User", back_populates="tenant", lazy="raise")


class User(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    tenant_id: Mapped[int] = mapped_column(ForeignKey("tenants.id"), nullable=False)
    email: Mapped[str] = mapped_column(String(255), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    tenant: Mapped[Tenant] = relationship("Tenant", back_populates="users", lazy="raise")
    documents: Mapped[list[Document]] = relationship(
        "Document", back_populates="owner", lazy="raise"
    )


class Document(Base):
    __tablename__ = "documents"
    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    owner_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=False)
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    owner: Mapped[User] = relationship("User", back_populates="documents", lazy="raise")
```

### Service: list users with document counts

```python
# BROKEN: N+1 pattern — one query per user
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from models import User, Document


async def list_users_broken(session: AsyncSession, tenant_id: int) -> list[dict]:
    result = await session.execute(
        select(User).where(User.tenant_id == tenant_id, User.is_active == True)
    )
    users = result.scalars().all()
    output = []
    for user in users:
        # BROKEN: each loop iteration issues a SELECT for documents
        doc_result = await session.execute(
            select(Document).where(Document.owner_id == user.id)
        )
        docs = doc_result.scalars().all()
        output.append({"user_id": user.id, "doc_count": len(docs)})
    return output
    # For 100 users: 101 queries, ~200 ms


# FIX: selectinload + aggregate in Python
from sqlalchemy.orm import selectinload


async def list_users_with_doc_count(
    session: AsyncSession, tenant_id: int
) -> list[dict]:
    result = await session.execute(
        select(User)
        .options(selectinload(User.documents))
        .where(User.tenant_id == tenant_id, User.is_active == True)
    )
    users = result.scalars().all()
    return [
        {"user_id": u.id, "email": u.email, "doc_count": len(u.documents)}
        for u in users
    ]
    # For 100 users: 2 queries, ~8 ms
```

### Service: create document with attachment in one transaction

```python
# services/document_service.py
from sqlalchemy import insert
from sqlalchemy.ext.asyncio import AsyncSession
from models import Document


async def create_document(
    session: AsyncSession,
    owner_id: int,
    title: str,
) -> Document:
    """
    Create a document. The caller (route handler via yield dep) owns the
    commit boundary — this service function only flushes to get the ID.
    """
    doc = Document(owner_id=owner_id, title=title)
    session.add(doc)
    await session.flush()  # assigns doc.id via RETURNING
    return doc
```

### Service: soft-delete user and cascade documents

```python
# services/user_service.py
from datetime import datetime, timezone
from sqlalchemy import update
from sqlalchemy.ext.asyncio import AsyncSession
from models import User, Document


async def soft_delete_user(session: AsyncSession, user_id: int) -> None:
    """
    Soft-delete a user and all their documents in a single transaction.
    The yield dependency commits on success, rolls back on error.
    """
    now = datetime.now(tz=timezone.utc)

    # Mark user deleted
    await session.execute(
        update(User)
        .where(User.id == user_id)
        .values(is_active=False, deleted_at=now)
    )

    # Cascade soft-delete to documents
    await session.execute(
        update(Document)
        .where(Document.owner_id == user_id)
        .values(is_deleted=True)
    )
    # No commit here — the yield dependency commits after the route handler returns
```

### Route handler

```python
# routes/users.py
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from dependencies import get_async_session
from services import user_service, document_service

router = APIRouter(prefix="/tenants/{tenant_id}/users", tags=["users"])


@router.get("")
async def list_tenant_users(
    tenant_id: int,
    session: AsyncSession = Depends(get_async_session),
):
    from services.user_service import list_users_with_doc_count
    return await list_users_with_doc_count(session, tenant_id)


@router.delete("/{user_id}")
async def delete_user(
    tenant_id: int,
    user_id: int,
    session: AsyncSession = Depends(get_async_session),
):
    await user_service.soft_delete_user(session, user_id)
    return {"status": "deleted"}
    # yield dependency commits the transaction after this return
```

### Connection pool sizing for this deployment

- 4 Uvicorn workers × (5 pool_size + 10 max_overflow) = 60 max connections
- PostgreSQL `max_connections = 200`; 60 leaves 140 for read replicas, admin, migrations
- `pool_timeout=30` s — requests wait up to 30 s for a connection; at 500 RPS with ~8 ms query time, average pool utilization is 500 × 0.008 / 15 connections per worker ≈ 27% — well within capacity

### Discussion Questions

1. Why do the service functions not call `await session.commit()`? What would break if they did and the route handler threw an exception after the service returned?

2. The `list_users_with_doc_count` function loads all documents into memory to count them. For users with 100,000 documents each, this is wasteful. How would you rewrite the query to return document counts from the database without loading document objects? (Hint: `func.count()` + `group_by`.)

3. The soft-delete uses two `UPDATE` statements. Under what isolation level could another transaction read a user as active but their documents as deleted, creating an inconsistency? How would you prevent this?

4. If the `asyncpg` connection pool is exhausted under peak load and `pool_timeout=30` is reached, FastAPI raises a `TimeoutError`. How would you detect this in monitoring, and what are two operational responses to prevent cascading failure?

---
