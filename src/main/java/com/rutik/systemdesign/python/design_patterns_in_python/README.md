# Design Patterns in Python

## 1. Concept Overview

Design patterns are reusable solutions to recurring software design problems. The Gang of Four (GoF) catalogue defines 23 patterns across three families: creational, structural, and behavioral. Python's first-class functions, dynamic typing, and protocol-oriented duck-typing mean that many classic Java-style pattern implementations are unnecessary ceremony in Python — the language already bakes in the mechanism.

This module covers the most important patterns with idiomatic Python implementations. The goal is not to translate Java to Python line-by-line, but to show how Python's own features — callables as strategies, modules as singletons, decorators as wrappers, ABCs as template-method contracts, `typing.Protocol` as structural interfaces — express the same intent with far less boilerplate.

Patterns covered:

- Singleton — module-level, metaclass with thread-safety, borg
- Factory / Registry — callable registry, `@register` decorator
- Strategy — first-class functions and callables, no Strategy class needed
- Observer — `weakref.WeakSet` listener bus, async variant
- Decorator Pattern — Python `@` syntax IS the GoF decorator; `functools.lru_cache` as example
- Command — `@dataclass` callable with `execute` / `undo`, undo history queue
- Template Method — ABC `@abstractmethod` hooks, composition alternative
- Repository — `typing.Protocol` generic repo, SQLAlchemy + in-memory implementations
- Anti-patterns — god object, mutable defaults, deep inheritance, premature abstraction

Cross-references:
- For GoF pattern reference implementations in Java, see `../../lld/creational/`, `../../lld/structural/`, `../../lld/behavioral/`
- For the Observer pattern at scale in a distributed system, see `../../hld/`
- For the Repository pattern with FastAPI DI, see `../fastapi/dependency_injection_in_fastapi/README.md`

---

## 2. Intuition

> A design pattern is a named conversation: when you say "that's a Strategy pattern," every senior engineer immediately understands that behavior is being swapped at runtime without touching the caller.

**Mental model**: Patterns are vocabulary, not blueprints. Java needs explicit class hierarchies to encode the vocabulary; Python can express the same idea with a callable, a dict, or a decorator — the name and the intent remain identical.

**Why it matters**: Python's dynamism tempts engineers to write one-off, ad-hoc solutions. Knowing the pattern vocabulary prevents the slow accumulation of undiscoverable ad-hoc code that becomes "legacy" six months after it ships. It also makes code reviews faster: "make the validator a Strategy" is a three-word code review that conveys a whole refactoring direction.

**Key insight**: The most important shift from Java to Python patterns is that **callables replace single-method interfaces**. A `Callable[[Item], float]` is a Strategy. A `Callable[[], None]` is a Command. Python's type system lets you be explicit about this with `typing.Protocol` without forcing inheritance.

---

## 3. Core Principles

**Single Responsibility**: Each pattern isolates one axis of variation — the creation axis (Singleton, Factory), the behavioral axis (Strategy, Command), the structural axis (Decorator, Repository).

**Open/Closed**: Registry-based factories and callable strategies let you add new variants by registering them — zero changes to existing call sites.

**Dependency Inversion**: Repository and Strategy push concrete implementations away from the caller behind a protocol or callable type hint. FastAPI's `Depends()` is the runtime expression of this principle.

**Composition over Inheritance**: Python's MRO is powerful but deep inheritance chains break refactorability fast. Protocols, callables, and dataclasses enable composition without the coupling of inheritance.

**Explicit over Implicit**: Python's `__init__.py` and module system make module-level singletons natural and transparent — no hidden `_instance` class variable, no mysterious class that behaves differently from other classes.

---

## 4. Types / Architectures / Strategies

### Creational Patterns

| Pattern | Pythonic mechanism | When |
|---|---|---|
| Singleton | Module-level object or metaclass with `Lock` | Config, logger, DB engine |
| Borg | Shared `__dict__` (`_shared_state`) | Multiple instances, one state |
| Factory | Registry `dict` + `@register` decorator | Multiple product types, runtime selection |
| Abstract Factory | Protocol + factory functions grouped by family | Cross-platform UI, multi-cloud adapters |

### Structural Patterns

| Pattern | Pythonic mechanism | When |
|---|---|---|
| Decorator | Python `@` syntax, `functools.wraps` | Cross-cutting concerns: caching, rate-limit, auth |
| Repository | `typing.Protocol` generic | Decouple domain from persistence |
| Adapter | Thin wrapper class or `__getattr__` delegation | Integrate third-party APIs into your domain interface |
| Composite | Recursive dataclass or AST node | Trees: DOM, expression trees, UI components |

### Behavioral Patterns

| Pattern | Pythonic mechanism | When |
|---|---|---|
| Strategy | Callable type hint, function as argument | Sorting key, pricing algorithm, auth backend |
| Observer | `weakref.WeakSet`, async `asyncio.Queue` | Event bus, domain events, UI reactive bindings |
| Command | `@dataclass` with `execute()` / `undo()` | Undo history, task queues, `BackgroundTasks` |
| Template Method | ABC `@abstractmethod` | ETL pipelines, report generators, test fixtures |
| Chain of Responsibility | List of callables iterated until one handles | Middleware stacks, validation chains |

---

## 5. Architecture Diagrams

### Registry-Based Factory

```
                  @register("circle")
                  @register("rect")
                        |
                        v
              _registry: dict[str, type[Shape]]
              {
                "circle": CircleShape,
                "rect":   RectShape,
              }
                        |
          factory(kind) v
              _registry[kind]()  ---> Shape instance
```

### Observer Event Bus with Weak References

```
  EventBus
  ┌─────────────────────────────────────────┐
  │  _listeners: dict[str, WeakSet[Callable]]│
  │                                         │
  │  subscribe(event, cb) ──> WeakSet.add   │
  │  emit(event, *args)   ──> iterate set,  │
  │                           call each cb  │
  └─────────────────────────────────────────┘
       |
       | weak reference (GC can collect cb)
       v
  Listener objects (live as long as subscriber holds reference)
```

### Repository with FastAPI DI

```
  Protocol: Repository[T]
  ┌──────────────────────┐
  │ get(id) -> T | None  │
  │ save(entity: T)      │
  │ delete(id: int)      │
  └──────────┬───────────┘
             |
    ┌────────┴────────┐
    v                 v
SQLAlchemyUserRepo  InMemoryUserRepo
(production)        (test / unit)

FastAPI route:
  async def create_user(
      repo: UserRepo = Depends(get_user_repo)
  ): ...
```

### Template Method vs Composition

```
  Template Method (ABC)          Composition (callable)
  ─────────────────────          ──────────────────────
  DataProcessor (ABC)            DataProcessor
    load()                         __init__(transform_fn)
    transform() @abstract          load()
    save()                         transform_fn(data)
       |                           save()
  CsvProcessor
    transform() -> override
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Singleton

**Module-level (preferred)**

```python
# config.py  — imported once, cached in sys.modules forever
from pydantic_settings import BaseSettings

class _Settings(BaseSettings):
    db_url: str = "sqlite:///./app.db"
    secret_key: str = "change-me"
    debug: bool = False

settings = _Settings()  # module-level singleton
```

```python
# anywhere in the codebase
from config import settings  # same object every time
```

**Metaclass singleton with thread safety**

```python
import threading
from typing import Any

class SingletonMeta(type):
    _instances: dict[type, Any] = {}
    _lock: threading.Lock = threading.Lock()

    def __call__(cls, *args: Any, **kwargs: Any) -> Any:
        with cls._lock:
            if cls not in cls._instances:
                instance = super().__call__(*args, **kwargs)
                cls._instances[cls] = instance
        return cls._instances[cls]


class DatabaseEngine(metaclass=SingletonMeta):
    def __init__(self, url: str = "postgresql://localhost/app") -> None:
        self.url = url
        # expensive: connection pool created once
        self._pool: list[Any] = []
```

**Borg pattern** (shared state, multiple instance references allowed)

```python
class BorgCache:
    _shared_state: dict[str, Any] = {}

    def __init__(self) -> None:
        self.__dict__ = BorgCache._shared_state

    def set(self, key: str, value: Any) -> None:
        self._shared_state[key] = value

    def get(self, key: str) -> Any | None:
        return self._shared_state.get(key)

# Two references, one shared state
a = BorgCache()
b = BorgCache()
a.set("x", 42)
assert b.get("x") == 42  # True
assert a is not b         # True — different objects, same __dict__
```

---

### 6.2 Factory / Registry

**Registry pattern with `@register` decorator**

```python
from typing import Callable, TypeVar

T = TypeVar("T", bound="Validator")
_registry: dict[str, type["Validator"]] = {}


def register(name: str) -> Callable[[type[T]], type[T]]:
    def decorator(cls: type[T]) -> type[T]:
        _registry[name] = cls
        return cls
    return decorator


class Validator:
    def validate(self, value: str) -> bool:
        raise NotImplementedError


@register("email")
class EmailValidator(Validator):
    def validate(self, value: str) -> bool:
        return "@" in value and "." in value.split("@")[-1]


@register("phone")
class PhoneValidator(Validator):
    def validate(self, value: str) -> bool:
        return value.replace("+", "").replace("-", "").isdigit()


def make_validator(kind: str) -> Validator:
    try:
        return _registry[kind]()
    except KeyError:
        raise ValueError(f"Unknown validator: {kind!r}. "
                         f"Available: {sorted(_registry)}")
```

FastAPI's `Depends()` is itself a registry pattern — it maps a callable to a resolved dependency and caches results within the request scope.

---

### 6.3 Strategy — Callables as Strategies

```python
import operator
from collections.abc import Callable
from dataclasses import dataclass

@dataclass
class Item:
    name: str
    price: float
    rating: float


def sort_items(
    items: list[Item],
    strategy: Callable[[Item], float] = operator.attrgetter("price"),
    *,
    reverse: bool = False,
) -> list[Item]:
    return sorted(items, key=strategy, reverse=reverse)


# Three strategies — no Strategy class needed
by_price  = operator.attrgetter("price")
by_rating = operator.attrgetter("rating")
by_value  = lambda item: item.rating / max(item.price, 0.01)

catalog = [Item("pen", 1.5, 4.2), Item("notebook", 5.0, 4.8), Item("eraser", 0.5, 3.9)]

cheapest  = sort_items(catalog, by_price)
top_rated = sort_items(catalog, by_rating, reverse=True)
best_val  = sort_items(catalog, by_value, reverse=True)
```

---

### 6.4 Observer — WeakRef Listener Bus

```python
import weakref
from collections import defaultdict
from collections.abc import Callable
from typing import Any

class EventBus:
    def __init__(self) -> None:
        self._listeners: dict[str, weakref.WeakSet[Callable[..., None]]] = \
            defaultdict(weakref.WeakSet)

    def subscribe(self, event: str, callback: Callable[..., None]) -> None:
        self._listeners[event].add(callback)

    def unsubscribe(self, event: str, callback: Callable[..., None]) -> None:
        self._listeners[event].discard(callback)

    def emit(self, event: str, *args: Any, **kwargs: Any) -> None:
        for cb in list(self._listeners[event]):
            cb(*args, **kwargs)


# Async variant — each subscriber gets its own queue
import asyncio

class AsyncEventBus:
    def __init__(self) -> None:
        self._queues: dict[str, list[asyncio.Queue[Any]]] = defaultdict(list)

    def subscribe(self, event: str) -> asyncio.Queue[Any]:
        q: asyncio.Queue[Any] = asyncio.Queue()
        self._queues[event].append(q)
        return q

    async def emit(self, event: str, payload: Any) -> None:
        for q in self._queues[event]:
            await q.put(payload)
```

---

### 6.5 Decorator Pattern — `@` Syntax IS GoF Decorator

The GoF Decorator pattern attaches new behavior to an object by wrapping it. Python's `@decorator` syntax does exactly this to callables.

```python
import time
import functools
from collections import defaultdict
from collections.abc import Callable
from typing import TypeVar, ParamSpec

P = ParamSpec("P")
R = TypeVar("R")


def rate_limit(max_calls: int, period: float = 1.0) -> Callable[[Callable[P, R]], Callable[P, R]]:
    """Wrap any callable so it raises if called more than max_calls times per period."""
    def decorator(fn: Callable[P, R]) -> Callable[P, R]:
        call_times: list[float] = []

        @functools.wraps(fn)
        def wrapper(*args: P.args, **kwargs: P.kwargs) -> R:
            now = time.monotonic()
            # remove timestamps older than the rolling window
            while call_times and call_times[0] < now - period:
                call_times.pop(0)
            if len(call_times) >= max_calls:
                raise RuntimeError(
                    f"{fn.__name__} rate limit exceeded: "
                    f"{max_calls} calls per {period}s"
                )
            call_times.append(now)
            return fn(*args, **kwargs)

        return wrapper
    return decorator


@rate_limit(max_calls=5, period=1.0)
def send_email(address: str, subject: str) -> None:
    print(f"Sending '{subject}' to {address}")
```

`functools.lru_cache` is the canonical stdlib example: it wraps a function, intercepts calls, checks a cache, and either returns the cached result or forwards the call — pure GoF Decorator.

---

### 6.6 Command Pattern

```python
from __future__ import annotations
import dataclasses
from collections import deque
from typing import Protocol


class Command(Protocol):
    def execute(self) -> None: ...
    def undo(self) -> None: ...


@dataclasses.dataclass
class MoveCommand:
    entity_id: int
    dx: float
    dy: float
    _positions: dict[int, tuple[float, float]] = dataclasses.field(
        default_factory=dict, repr=False
    )

    def execute(self) -> None:
        x, y = self._positions.get(self.entity_id, (0.0, 0.0))
        self._positions[self.entity_id] = (x + self.dx, y + self.dy)

    def undo(self) -> None:
        x, y = self._positions.get(self.entity_id, (0.0, 0.0))
        self._positions[self.entity_id] = (x - self.dx, y - self.dy)


class CommandHistory:
    def __init__(self) -> None:
        self._done: deque[Command] = deque()
        self._undone: deque[Command] = deque()

    def execute(self, cmd: Command) -> None:
        cmd.execute()
        self._done.append(cmd)
        self._undone.clear()

    def undo(self) -> None:
        if not self._done:
            return
        cmd = self._done.pop()
        cmd.undo()
        self._undone.append(cmd)

    def redo(self) -> None:
        if not self._undone:
            return
        cmd = self._undone.pop()
        cmd.execute()
        self._done.append(cmd)
```

FastAPI's `BackgroundTasks.add_task(fn, *args)` is a command queue: tasks are enqueued as callables and fired after the response is sent.

---

### 6.7 Template Method via ABC

```python
from abc import ABC, abstractmethod
from pathlib import Path


class DataProcessor(ABC):
    """Template Method: load → transform → save sequence is fixed."""

    def run(self, source: Path, dest: Path) -> None:
        raw = self.load(source)
        processed = self.transform(raw)
        self.save(processed, dest)

    def load(self, path: Path) -> list[dict]:
        import json
        return json.loads(path.read_text())

    @abstractmethod
    def transform(self, data: list[dict]) -> list[dict]:
        ...

    def save(self, data: list[dict], path: Path) -> None:
        import json
        path.write_text(json.dumps(data, indent=2))


class DeduplicateProcessor(DataProcessor):
    def transform(self, data: list[dict]) -> list[dict]:
        seen: set[str] = set()
        result: list[dict] = []
        for row in data:
            key = str(row.get("id", id(row)))
            if key not in seen:
                seen.add(key)
                result.append(row)
        return result


# Composition alternative — avoids subclassing
from collections.abc import Callable

class FunctionalProcessor:
    def __init__(
        self,
        transform_fn: Callable[[list[dict]], list[dict]],
    ) -> None:
        self._transform = transform_fn

    def run(self, source: Path, dest: Path) -> None:
        import json
        raw = json.loads(source.read_text())
        processed = self._transform(raw)
        dest.write_text(json.dumps(processed, indent=2))
```

---

### 6.8 Repository Pattern with Protocol

```python
from typing import Generic, Protocol, TypeVar, runtime_checkable

T = TypeVar("T")


@runtime_checkable
class Repository(Protocol[T]):
    def get(self, id: int) -> T | None: ...
    def save(self, entity: T) -> None: ...
    def delete(self, id: int) -> None: ...
    def list_all(self) -> list[T]: ...


# --- Domain entity ---
@dataclasses.dataclass
class User:
    id: int
    email: str
    hashed_password: str


# --- In-memory implementation (tests) ---
class InMemoryUserRepo:
    def __init__(self) -> None:
        self._store: dict[int, User] = {}

    def get(self, id: int) -> User | None:
        return self._store.get(id)

    def save(self, entity: User) -> None:
        self._store[entity.id] = entity

    def delete(self, id: int) -> None:
        self._store.pop(id, None)

    def list_all(self) -> list[User]:
        return list(self._store.values())


# --- FastAPI DI wiring ---
from fastapi import Depends, FastAPI

app = FastAPI()

def get_user_repo() -> InMemoryUserRepo:
    # In production, replace with SQLAlchemyUserRepo(session)
    return InMemoryUserRepo()

UserRepo = Repository[User]

@app.get("/users/{user_id}")
async def read_user(
    user_id: int,
    repo: UserRepo = Depends(get_user_repo),
) -> dict:
    user = repo.get(user_id)
    if user is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="User not found")
    return {"id": user.id, "email": user.email}
```

---

## 7. Real-World Examples

**FastAPI itself** uses the Registry pattern for route registration: `@app.get("/path")` adds the route handler to an internal `APIRouter` registry keyed by path and method.

**Celery** implements the Command pattern: tasks are serialized `Command` objects placed on a queue and consumed by workers. The `@app.task` decorator is the Command wrapping mechanism.

**SQLAlchemy's `Session`** is a Unit of Work pattern (an extension of Command): it tracks dirty objects and issues SQL as a single `commit()` command.

**Pydantic validators** (`@field_validator`) are the Chain of Responsibility pattern: each validator is called in sequence until one raises or all pass.

**`functools.lru_cache`, `functools.cached_property`, `dataclasses.dataclass`** are all Decorator pattern at stdlib level — they wrap classes or functions with additional behavior without the user subclassing anything.

**Starlette middleware** (`app.add_middleware(SomeMiddleware)`) is a Decorator stack: each middleware wraps the next in an `ASGI` chain, adding cross-cutting behavior (timing, auth, CORS) transparently.

**Django's authentication backends** are the Strategy pattern: `AUTHENTICATION_BACKENDS = ["myapp.backends.LDAPBackend"]` swaps the authentication callable at runtime via settings.

---

## 8. Tradeoffs

| Pattern | Benefit | Cost | Python-specific note |
|---|---|---|---|
| Module singleton | Zero boilerplate, thread-safe by GIL | Hard to replace in tests | Use `importlib.reload` or monkeypatch in tests |
| Metaclass singleton | Explicit, subclassable | Complex metaclass machinery | Prefer module-level unless subclassing needed |
| Borg | Multiple instance references OK | Shared mutable state — tricky | Rarely needed; document the intent clearly |
| Registry factory | Open/closed, extensible | Lookup overhead; typo in name fails at runtime | Add `ValueError` with available keys |
| Strategy (callable) | Zero classes, composable | No shared interface enforced unless Protocol used | Add `Protocol` type alias for IDE support |
| Observer (WeakSet) | No memory leaks from listeners | Listeners must be held by caller | Document that lambdas won't survive `WeakSet` |
| Decorator (`@`) | Transparent cross-cutting | Wrapping cost; `__wrapped__` inspection needed | Always use `functools.wraps` |
| Template Method (ABC) | Enforces hook contract | Locks subclass into a specific load/save flow | Prefer composition when flow varies |
| Repository (Protocol) | Swappable persistence, test-friendly | Extra indirection layer | Use `@runtime_checkable` for `isinstance` checks |

---

## 9. When to Use / When NOT to Use

**Use Singleton** when you have a single shared resource with global lifetime: a settings object, a logging handler, a DB connection pool. Do NOT use it for service classes that might need multiple instances in tests — prefer dependency injection instead.

**Use Factory / Registry** when you have a closed set of product types that grows over time and you want to add new types without touching the dispatching logic. Do NOT use it for two-type switches — a plain `if/elif` is clearer.

**Use Strategy** when the algorithm varies independently from the client, and you want to swap it at runtime or inject it as a parameter. Do NOT wrap in a class if a simple callable does the job — over-engineering a callable into a class hierarchy is the most common Python pattern abuse.

**Use Observer** when you want loose coupling between producers and consumers of events, especially when the set of consumers is not known at design time. Do NOT use it for synchronous, mandatory side effects — call the function directly.

**Use Decorator** for cross-cutting concerns: caching, rate-limiting, auth checks, retry logic, tracing. Do NOT use it when the behavior depends on per-call context that cannot be expressed in a wrapper — use middleware or explicit parameter passing instead.

**Use Command** when you need undo/redo semantics, or a queue of deferred operations, or an audit log. Do NOT use it for simple fire-and-forget calls — `BackgroundTasks.add_task(fn, arg)` is already the Command pattern; no further wrapping needed.

**Use Template Method** when a processing pipeline has a fixed structure but variable steps that subclasses must fill in. Do NOT use it when the sequence itself can vary — use a pipeline of callables (list of `Callable`) instead.

**Use Repository** when domain logic must not depend on a specific storage technology and you need to swap implementations (prod SQL vs. test in-memory). Do NOT add a Repository layer for simple scripts or CLIs where there is no meaningful persistence boundary.

---

## 10. Common Pitfalls

### Pitfall 1: Classic Singleton — Race Condition Under Threads

```python
# BROKEN: not thread-safe — two threads can both see _instance is None
class NotSafeSingleton:
    _instance: "NotSafeSingleton | None" = None

    def __new__(cls) -> "NotSafeSingleton":
        if cls._instance is None:          # <-- thread A reads None
            cls._instance = super().__new__(cls)  # thread B also reads None
        return cls._instance               # two distinct instances possible
```

```python
# FIX 1: metaclass with double-checked locking
import threading

class SafeSingletonMeta(type):
    _instances: dict[type, object] = {}
    _lock: threading.Lock = threading.Lock()

    def __call__(cls, *args: object, **kwargs: object) -> object:
        with cls._lock:
            if cls not in cls._instances:
                cls._instances[cls] = super().__call__(*args, **kwargs)
        return cls._instances[cls]

# FIX 2 (simpler): use the module as the singleton — Python's import system
# guarantees module-level code executes exactly once, protected by the GIL.
# config.py
settings = _Settings()   # one object, created once, thread-safe by import machinery
```

---

### Pitfall 2: Deep Inheritance Chain — Fragile and Untestable

```python
# BROKEN: 5-level chain to add logging to a notifier
class BaseNotifier:
    def send(self, msg: str) -> None: ...

class EmailNotifier(BaseNotifier):
    def send(self, msg: str) -> None:
        print(f"Email: {msg}")

class FormattedEmailNotifier(EmailNotifier):
    def send(self, msg: str) -> None:
        super().send(msg.upper())

class LoggedFormattedEmailNotifier(FormattedEmailNotifier):
    def send(self, msg: str) -> None:
        print("LOG:", msg)
        super().send(msg)

class RateLimitedLoggedFormattedEmailNotifier(LoggedFormattedEmailNotifier):
    # Nobody can reason about MRO at 4 levels deep
    ...
```

```python
# FIX: composition + Protocol
from typing import Protocol

class Notifier(Protocol):
    def send(self, msg: str) -> None: ...

class EmailNotifier:
    def send(self, msg: str) -> None:
        print(f"Email: {msg}")

def with_logging(notifier: Notifier) -> Notifier:
    class _Logged:
        def send(self, msg: str) -> None:
            print(f"LOG: {msg}")
            notifier.send(msg)
    return _Logged()

def with_uppercase(notifier: Notifier) -> Notifier:
    class _Upper:
        def send(self, msg: str) -> None:
            notifier.send(msg.upper())
    return _Upper()

# Compose freely, no MRO headaches
notifier = with_logging(with_uppercase(EmailNotifier()))
notifier.send("hello")
```

---

### Pitfall 3: Mutable Default Argument in Command / Factory

```python
# BROKEN
class CommandQueue:
    def __init__(self, commands: list = []):  # shared across all instances!
        self.commands = commands
```

```python
# FIX
import dataclasses
from collections import deque

@dataclasses.dataclass
class CommandQueue:
    commands: deque = dataclasses.field(default_factory=deque)
```

### Pitfall 4: Lambda in WeakSet — Immediately Garbage Collected

```python
bus = EventBus()
# BROKEN: the lambda has no other reference — WeakSet drops it immediately
bus.subscribe("order.created", lambda order: print(order))
bus.emit("order.created", {"id": 1})  # nothing printed — lambda is gone
```

```python
# FIX: hold a strong reference to the callback
def on_order_created(order: dict) -> None:
    print(order)

bus.subscribe("order.created", on_order_created)
# on_order_created is a module-level name — strong reference kept
```

### Pitfall 5: Forgetting `functools.wraps` in Decorator

```python
# BROKEN: introspection breaks
def my_decorator(fn):
    def wrapper(*args, **kwargs):
        return fn(*args, **kwargs)
    return wrapper  # wrapper.__name__ == "wrapper", not fn.__name__

# FIX
import functools

def my_decorator(fn):
    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        return fn(*args, **kwargs)
    return wrapper
```

### Pitfall 6: Premature Abstraction

Adding a Repository, Factory, and Observer for a script that reads a CSV and writes a report is over-engineering. Patterns add indirection — indirection is justified only when variation exists or is concretely expected within the next release cycle.

---

## 11. Technologies & Tools

| Tool / Library | Pattern support | Notes |
|---|---|---|
| `abc` (stdlib) | Template Method, abstract interfaces | `@abstractmethod` enforces hook contracts at instantiation |
| `typing.Protocol` | Repository, Strategy interfaces | Structural subtyping — no inheritance required |
| `functools` (stdlib) | Decorator, Singleton-via-cache | `lru_cache`, `cached_property`, `wraps`, `partial` |
| `weakref` (stdlib) | Observer | `WeakSet`, `WeakValueDictionary` prevent listener leaks |
| `dataclasses` (stdlib) | Command, Value Objects | `field(default_factory=...)` for mutable defaults |
| `attrs` | Command, Value Objects | Faster than `dataclasses` for large models; validators built-in |
| FastAPI `Depends` | Registry, Factory, Repository DI | Maps a callable to a resolved dependency per-request |
| SQLAlchemy | Repository, Unit of Work | `Session` is Unit of Work; `Mapped[]` ORM is Repository ready |
| Pydantic v2 | Strategy (validators), Factory | `model_validator`, `field_validator` as strategy hooks |
| Celery | Command, Observer | Tasks = Commands; signals = Observer |

---

## 12. Interview Questions with Answers

**Q1: Why is a Python module the preferred Singleton implementation over the classic `_instance` class variable pattern?**
A module is initialized exactly once by the import machinery and cached in `sys.modules`; subsequent imports return the same module object. The classic `_instance` pattern requires explicit locking to be thread-safe, adding boilerplate that the import system already handles for free.

**Q2: When would you choose the Borg pattern over the module singleton?**
Borg is useful when external code passes your class to APIs that call the constructor directly (e.g., plugin systems, ORM metaclasses). All constructed instances share `__dict__`, so they behave as one, but type checks like `isinstance` still pass for each. If you control all call sites, prefer the module singleton.

**Q3: How does Python's callable protocol eliminate the need for a Strategy interface class?**
Any Python callable — a function, a lambda, a class with `__call__`, a `functools.partial` — satisfies `Callable[[InputType], OutputType]`. The Strategy pattern's only requirement is that behavior is swappable; Python's type system expresses this directly without a marker interface or abstract base class.

**Q4: Why should `WeakSet` be used for Observer listener registries?**
If the event bus holds a strong reference to each listener, listener objects will not be garbage-collected even after the subscriber (the object owning the listener method) goes out of scope. This leaks memory proportional to the number of transient subscribers. `WeakSet` holds a weak reference so the GC can reclaim the listener when the subscriber is no longer referenced by anything else.

**Q5: What is the difference between the Python `@decorator` syntax and the GoF Decorator pattern?**
They are the same pattern at different abstraction levels. GoF Decorator wraps an object to add behavior while preserving the interface. Python's `@` syntax wraps a callable (function or class) to add behavior while preserving the callable contract. `functools.lru_cache` is the clearest example: it wraps a function, intercepts calls, and adds caching — pure GoF Decorator, zero classes required.

**Q6: How does the Factory Registry pattern keep code open for extension and closed for modification?**
New product types register themselves via the `@register("name")` decorator at module load time. The `factory(name)` function does a dict lookup — it never needs to change when a new type is added. The `if kind == "A"` dispatch block that requires editing for each new type is replaced by a one-line registration at the point of definition.

**Q7: What is the Template Method pattern and when should you prefer composition over it?**
Template Method defines a fixed algorithm skeleton in a base class, with abstract hook methods that subclasses override. Prefer composition when: (a) the algorithm sequence itself can vary (not just the steps), (b) the subclass would only override one method (a callable parameter is simpler), or (c) you want to compose multiple behaviors (a single-inheritance chain cannot compose two independent transforms).

**Q8: How does the Repository pattern integrate with FastAPI's dependency injection?**
Define the repository as a `typing.Protocol`. Write concrete implementations (`SQLAlchemyRepo`, `InMemoryRepo`). Write a provider function (`get_user_repo`) that returns the appropriate implementation — swappable by environment. Declare the repo as a route parameter with `Depends(get_user_repo)`. FastAPI resolves and caches it per request. Tests override the provider with `app.dependency_overrides[get_user_repo] = lambda: InMemoryRepo()`.

**Q9: What makes the Command pattern useful for undo/redo, and how does FastAPI BackgroundTasks relate to it?**
Command encapsulates a call as a first-class object with `execute()` and `undo()`. Storing a history deque of executed commands makes undo/redo a matter of popping and calling `undo()` or re-executing. FastAPI's `BackgroundTasks` is a simplified Command queue: tasks are callables queued during request handling and fired after the response — the pattern without explicit undo.

**Q10: How do you prevent the "lambda lost in WeakSet" bug?**
Lambdas defined inline have no name binding holding a strong reference — the `WeakSet` is the only reference, so the lambda is collected immediately. Always assign lambdas or use named functions when subscribing to a `WeakSet`-based bus. If you need a quick inline handler, assign it to a local variable with `handler = lambda ...: ...; bus.subscribe("event", handler)` and hold `handler` for as long as the subscription should live.

**Q11: What Python anti-pattern does deep inheritance chains represent, and how do you refactor it?**
Deep inheritance chains encode behavior as a taxonomy of classes rather than as composable behaviors. The symptom is `ChildClass(Parent3(Parent2(Parent1(Base))))` where each level adds one small behavior. Refactor by extracting each behavior as a standalone callable or wrapper (`with_logging(fn)`, `with_retry(fn)`) and composing them explicitly. `typing.Protocol` defines the interface without requiring any inheritance.

**Q12: How does the `@register` decorator pattern differ from a plugin-based entry_points approach?**
The `@register` decorator requires the registering module to be imported before the factory is called — order of imports matters. Python packaging `entry_points` (via `importlib.metadata`) discovers plugins from installed packages without requiring explicit imports, making the registry extensible across package boundaries. Use `@register` for in-repo extensibility; `entry_points` for third-party plugin ecosystems.

**Q13: When is a `typing.Protocol` preferable to an ABC for defining a pattern interface?**
`Protocol` enables structural (duck-type) subtyping: a class satisfies the Protocol if it has the required methods, with no inheritance. Use `Protocol` when you cannot or should not modify the implementing class (third-party code, stdlib types). Use ABC when you want to enforce the contract at instantiation time (`@abstractmethod` raises `TypeError` if a subclass forgets to implement a method) or when you want to share default implementations across subclasses.

---

## 13. Best Practices

**Prefer module-level objects over class-based singletons.** `settings = Settings()` at module scope is the idiomatic Python singleton — no metaclass, no `_instance`, no threading boilerplate beyond what the import system already provides.

**Express Strategy as a `Callable` type alias.** Define `SortKey = Callable[[Item], float]` in a types module and annotate parameters with it. This documents the contract without inventing a class hierarchy.

**Always `functools.wraps` your wrapper functions.** Without it, `help()`, introspection tools, and FastAPI's OpenAPI schema generation see the wrapper's signature, not the original function's. One line of `@functools.wraps(fn)` prevents hours of debugging.

**Hold strong references to Observer callbacks when using `WeakSet`.** Document this in the `subscribe()` docstring. For method callbacks, note that `weakref.WeakMethod` is available for bound methods.

**Use `dataclasses.field(default_factory=...)` for any mutable default in Command or Value Object classes.** Never use `list`, `dict`, or `set` as default argument values or class-level mutable defaults.

**Use `typing.Protocol` with `@runtime_checkable` when you want `isinstance` checks in addition to type-checking.** Without `@runtime_checkable`, `isinstance(obj, MyProtocol)` raises `TypeError`.

**Register factories at import time, validate keys at lookup time.** The `@register` decorator runs when the module is imported. The `make_validator(kind)` call raises a `ValueError` with the list of valid keys if the key is missing — fail fast with a helpful message, not a `KeyError`.

**Prefer composition chains over inheritance chains for structural patterns.** `with_logging(with_retry(with_timeout(service)))` is readable, individually testable, and independently reorderable. The equivalent 3-level inheritance chain is none of those things.

**Document which patterns you are intentionally applying.** A comment `# Registry pattern: add new validators by decorating with @register("name")` at the top of the factory module saves the next reader ten minutes of archaeology.

---

## 14. Case Study

### Refactoring a Monolithic Payment Processor to Composable Patterns

**Context**: An e-commerce backend has a single `PaymentService` class that validates payment data, calls a payment provider, persists the result, and sends confirmation emails and Slack alerts — 300 lines in one class. Adding a new payment provider (Stripe, PayPal, bank transfer) requires editing the class. Adding a new notification channel (SMS, push) requires editing the class. Tests require mocking four different things simultaneously.

---

#### Before: Monolithic God Object

```python
# BROKEN: PaymentService does everything — validation, provider dispatch,
# persistence, and notification. Every new feature touches this class.

class PaymentService:
    def process(self, amount: float, method: str, user_id: int) -> dict:
        # Validation inline
        if amount <= 0:
            raise ValueError("Amount must be positive")
        if method not in ("stripe", "paypal"):
            raise ValueError(f"Unknown method: {method}")

        # Provider dispatch — if/elif chain that grows with every new provider
        if method == "stripe":
            result = self._call_stripe(amount)
        elif method == "paypal":
            result = self._call_paypal(amount)

        # Persistence inline — directly constructs DB session
        from sqlalchemy.orm import Session
        from db import engine
        with Session(engine) as session:
            record = PaymentRecord(user_id=user_id, amount=amount, result=result)
            session.add(record)
            session.commit()

        # Notification inline — adds more if/elif for channels
        import smtplib
        # ... 40 lines of SMTP setup ...
        import requests
        requests.post("https://hooks.slack.com/...", json={"text": f"Payment {result}"})

        return result

    def _call_stripe(self, amount: float) -> dict: ...
    def _call_paypal(self, amount: float) -> dict: ...
```

---

#### After: Composable Patterns

```python
# FIX: Strategy for provider, Observer for notifications,
# Repository for persistence, Factory for validator selection.

from __future__ import annotations
import dataclasses
import weakref
from collections import defaultdict
from collections.abc import Callable
from typing import Any, Protocol

# --- Domain types ---
@dataclasses.dataclass
class PaymentRequest:
    amount: float
    method: str
    user_id: int

@dataclasses.dataclass
class PaymentResult:
    success: bool
    transaction_id: str
    amount: float


# --- Strategy: payment providers are callables ---
PaymentProvider = Callable[[PaymentRequest], PaymentResult]

def stripe_provider(req: PaymentRequest) -> PaymentResult:
    # real Stripe SDK call here
    return PaymentResult(success=True, transaction_id="stripe_txn_001", amount=req.amount)

def paypal_provider(req: PaymentRequest) -> PaymentResult:
    return PaymentResult(success=True, transaction_id="paypal_txn_002", amount=req.amount)

# Registry: add providers without touching PaymentService
_provider_registry: dict[str, PaymentProvider] = {
    "stripe": stripe_provider,
    "paypal": paypal_provider,
}

def get_provider(method: str) -> PaymentProvider:
    try:
        return _provider_registry[method]
    except KeyError:
        raise ValueError(
            f"Unknown payment method: {method!r}. "
            f"Available: {sorted(_provider_registry)}"
        )


# --- Repository: swappable persistence ---
class PaymentRepository(Protocol):
    def save(self, result: PaymentResult, user_id: int) -> None: ...
    def get(self, transaction_id: str) -> PaymentResult | None: ...

class InMemoryPaymentRepo:
    def __init__(self) -> None:
        self._store: dict[str, PaymentResult] = {}

    def save(self, result: PaymentResult, user_id: int) -> None:
        self._store[result.transaction_id] = result

    def get(self, transaction_id: str) -> PaymentResult | None:
        return self._store.get(transaction_id)


# --- Observer: notification bus with weak references ---
class PaymentEventBus:
    def __init__(self) -> None:
        self._listeners: dict[str, weakref.WeakSet[Callable[..., None]]] = \
            defaultdict(weakref.WeakSet)

    def subscribe(self, event: str, cb: Callable[..., None]) -> None:
        self._listeners[event].add(cb)

    def emit(self, event: str, **payload: Any) -> None:
        for cb in list(self._listeners[event]):
            cb(**payload)

payment_bus = PaymentEventBus()

# Notification handlers — registered at module load, held by module scope (strong refs)
def send_confirmation_email(result: PaymentResult, user_id: int) -> None:
    print(f"Email to user {user_id}: payment {result.transaction_id} OK")

def send_slack_alert(result: PaymentResult, user_id: int) -> None:
    print(f"Slack: payment {result.transaction_id} amount={result.amount}")

payment_bus.subscribe("payment.completed", send_confirmation_email)
payment_bus.subscribe("payment.completed", send_slack_alert)
# Adding SMS: one line, zero changes to PaymentService
# payment_bus.subscribe("payment.completed", send_sms_notification)


# --- Validator: Factory pattern ---
_validator_registry: dict[str, Callable[[PaymentRequest], None]] = {}

def register_validator(name: str) -> Callable:
    def decorator(fn: Callable[[PaymentRequest], None]) -> Callable[[PaymentRequest], None]:
        _validator_registry[name] = fn
        return fn
    return decorator

@register_validator("amount")
def validate_amount(req: PaymentRequest) -> None:
    if req.amount <= 0:
        raise ValueError(f"Amount must be positive, got {req.amount}")

@register_validator("method")
def validate_method(req: PaymentRequest) -> None:
    if req.method not in _provider_registry:
        raise ValueError(f"Unknown method: {req.method!r}")


# --- Refactored PaymentService: thin orchestrator ---
class PaymentService:
    def __init__(
        self,
        repo: PaymentRepository,
        bus: PaymentEventBus,
    ) -> None:
        self._repo = repo
        self._bus = bus

    def process(self, req: PaymentRequest) -> PaymentResult:
        # Validate via factory-registered validators
        for validate in _validator_registry.values():
            validate(req)

        # Dispatch via Strategy (callable from registry)
        provider = get_provider(req.method)
        result = provider(req)

        # Persist via Repository
        self._repo.save(result, req.user_id)

        # Notify via Observer
        self._bus.emit("payment.completed", result=result, user_id=req.user_id)

        return result


# --- FastAPI wiring ---
from fastapi import Depends, FastAPI

app = FastAPI()

def get_payment_repo() -> InMemoryPaymentRepo:
    return InMemoryPaymentRepo()

def get_payment_service(
    repo: InMemoryPaymentRepo = Depends(get_payment_repo),
) -> PaymentService:
    return PaymentService(repo=repo, bus=payment_bus)

@app.post("/payments")
async def create_payment(
    body: dict,
    service: PaymentService = Depends(get_payment_service),
) -> dict:
    req = PaymentRequest(
        amount=body["amount"],
        method=body["method"],
        user_id=body["user_id"],
    )
    result = service.process(req)
    return dataclasses.asdict(result)
```

**What changed**:

- Adding a new payment provider: add one function + one dict entry in `_provider_registry`. Zero changes to `PaymentService`.
- Adding a new notification channel: one `payment_bus.subscribe("payment.completed", new_handler)` line. Zero changes to `PaymentService`.
- Testing `PaymentService`: inject `InMemoryPaymentRepo` and a test `PaymentEventBus`. No mocking of SMTP or Slack.
- Validating a new rule: `@register_validator("idempotency")` on a new function.

The refactored service has 12 lines of logic. The original had 60+ in one method. Each pattern removed one hard dependency from the core class.
