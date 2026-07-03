# Protocols and Structural Typing

> Deep-dive sub-file for [`the_type_system_and_typing/README.md`](./README.md).
> Python 3.8+ (`typing.Protocol`, PEP 544). All code targets Python 3.11/3.12.

---

## 1. Concept Overview

`typing.Protocol` [3.8, PEP 544] brings **structural subtyping** — also called duck typing with
static verification — to Python's type system. A class satisfies a Protocol if it has the right
shape (correct method and attribute names, correct signatures) without ever inheriting from the
Protocol or even knowing it exists.

Before Protocol the only static-analysis-friendly abstraction was `ABC` (Abstract Base Class),
which requires explicit inheritance. Protocol removes that coupling, enabling library boundaries
to be expressed purely in terms of **capability** rather than **lineage**.

Key constructs in this sub-file:

- `typing.Protocol` — define structural interfaces [3.8]
- `@runtime_checkable` — allow `isinstance()` checks against a Protocol, with caveats
- Protocol inheritance and composition — combining multiple Protocols
- Generic Protocols — `Protocol[T]`
- Covariant / contravariant type variables in Protocols
- Callable Protocols — typing function-like objects
- `@classmethod` / `@staticmethod` / `__slots__` inside Protocols
- Standard-library Protocols: `SupportsInt`, `Sized`, `Iterable`, …
- Protocol vs ABC decision guide

---

## 2. Intuition

> A Protocol is a job description posted on a notice board. Anyone who can do the job gets the
> role — they do not need to apply through your agency or sign your contract.

**Mental model:** Imagine you run a city's electrical grid. You do not care whether a building was
constructed by Company A or Company B. You care only that it has a standard 240 V socket. A
`Protocol` is that socket spec. Any building (class) with the right socket shape (methods /
attributes) can plug in — no inheritance, no registration.

**Why it matters:** Library maintainers who use ABC force every downstream user to import and
inherit from their class. This creates a hard dependency and can make testing painful (you must
subclass the library ABC to write a stub). Protocol eliminates the dependency — your downstream
users just implement the right methods, and mypy verifies the fit at check time.

**Key insight:** Protocol satisfaction is checked **at the call site by the type checker**, not
at class definition time by the runtime. You can retroactively make a class satisfy a Protocol
you wrote yesterday, without modifying that class at all.

---

## 3. Core Principles

### 3.1 Nominal vs Structural Subtyping

**Nominal subtyping** (the default in Python without Protocol): a type `B` is a subtype of `A`
only if `B` explicitly inherits from `A` (or is `register()`-ed with `A`). Checked at runtime
via `isinstance(x, A)`.

**Structural subtyping**: a type `B` is a subtype of `A` if `B` has all the attributes and
methods that `A` defines, with compatible signatures. No inheritance required.

```
Nominal:  isinstance(b, A) => True only if B inherits A
Structural: mypy checks method signatures at analysis time — runtime knows nothing
```

Python's dynamic typing has always been *informally* structural (duck typing). `typing.Protocol`
makes that informal contract *formally verifiable* by static tools.

### 3.2 Implicit Satisfaction

```python
from typing import Protocol

class Drawable(Protocol):
    def draw(self) -> None: ...

class Circle:          # Does NOT inherit Drawable
    def draw(self) -> None:
        print("O")

def render(shape: Drawable) -> None:
    shape.draw()

render(Circle())       # mypy: OK — Circle structurally satisfies Drawable
```

No `@abstractmethod`, no `register()`, no `isinstance()` check needed. The type checker verifies
the contract silently.

### 3.3 Protocol Members

A Protocol can specify:
- Instance methods
- Class methods (`@classmethod`)
- Static methods (`@staticmethod`)
- Instance attributes (declared as class-level annotations)
- Properties (`@property`)
- `__call__` (making it a callable protocol)
- `__dunder__` methods

Any member **not** listed in the Protocol body is irrelevant to structural compatibility.

### 3.4 What Counts as "Satisfying" a Protocol

A concrete class `C` satisfies `Protocol P` when:
1. Every method / attribute declared in `P` is present in `C`.
2. For methods: the parameter types are **compatible** (contravariant in parameters, covariant
   in return type — standard Liskov Substitution).
3. For attributes: the declared type is compatible.

```python
from typing import Protocol

class Reader(Protocol):
    def read(self, n: int) -> bytes: ...

class FileHandle:
    def read(self, n: int) -> bytes:   # satisfies
        return b""

class PartialHandle:
    def read(self) -> bytes:           # DOES NOT satisfy — missing `n` param
        return b""
```

---

## 4. Types / Architectures / Strategies

### 4.1 Basic Protocol

```python
from typing import Protocol

class Closeable(Protocol):
    def close(self) -> None: ...

def release(resource: Closeable) -> None:
    resource.close()
```

Any object with a compatible `close()` — `socket`, `file`, `database connection`, custom class —
satisfies `Closeable` without inheriting it.

### 4.2 `@runtime_checkable` Protocol

By default, Protocols are **static-only**. To allow `isinstance()` at runtime, decorate with
`@runtime_checkable`:

```python
from typing import Protocol, runtime_checkable

@runtime_checkable
class Closeable(Protocol):
    def close(self) -> None: ...

f = open("/dev/null")
print(isinstance(f, Closeable))   # True — file objects have close()
```

**Critical limitation**: `@runtime_checkable` only checks for *attribute existence*, not *type
signatures*. This is a shallow structural check, not a full Protocol verification.

```python
@runtime_checkable
class Serializable(Protocol):
    def to_json(self) -> str: ...

class Broken:
    to_json = 42          # attribute exists but is not callable

print(isinstance(Broken(), Serializable))  # True — runtime check only sees the name!
```

The type checker (mypy/pyright) would reject `Broken` because `to_json` has type `int`, not
`() -> str`. But `isinstance()` at runtime says `True`. Never rely on `runtime_checkable` for
security or correctness enforcement — use it only for ergonomics (branching on protocol support).

**Performance**: Each `isinstance(x, SomeProtocol)` call iterates the Protocol's `__protocol_attrs__`
and calls `hasattr()` for each. For hot loops this is measurably slower than `isinstance(x, SomeABC)`.
CPython 3.12 caches the result of runtime Protocol checks internally, bringing repeat checks
to near-zero cost after the first hit.

### 4.3 Generic Protocol — `Protocol[T]`

```python
from typing import Protocol, TypeVar

T_co = TypeVar("T_co", covariant=True)

class Container(Protocol[T_co]):
    def __iter__(self) -> "Iterator[T_co]": ...
    def __len__(self) -> int: ...

def first(c: Container[T_co]) -> T_co:
    return next(iter(c))
```

Generic Protocols enable parameterised structural interfaces. The covariant `T_co` means a
`Container[Dog]` is acceptable where `Container[Animal]` is expected (if `Dog` is a subtype
of `Animal`).

### 4.4 Protocol Inheritance and Composition

Protocols can inherit from other Protocols to build up capability:

```python
from typing import Protocol

class Readable(Protocol):
    def read(self, n: int) -> bytes: ...

class Writable(Protocol):
    def write(self, data: bytes) -> int: ...

class ReadWritable(Readable, Writable, Protocol):  # Note: must include Protocol here
    ...
```

The `Protocol` marker must appear in the MRO whenever you define a *new* Protocol by composing
others. Without it, `ReadWritable` would be treated as a concrete class inheriting from two
Protocol bases, which mypy would flag as ambiguous.

### 4.5 Callable Protocol

For typing function-like objects with specific signatures:

```python
from typing import Protocol

class Transformer(Protocol):
    def __call__(self, text: str, *, lowercase: bool = False) -> str: ...

def apply(t: Transformer, value: str) -> str:
    return t(value, lowercase=True)

def strip_html(text: str, *, lowercase: bool = False) -> str:
    import re
    result = re.sub(r"<[^>]+>", "", text)
    return result.lower() if lowercase else result

apply(strip_html, "<b>Hello</b>")  # mypy: OK — strip_html satisfies Transformer
```

`Callable[[str], str]` cannot express keyword-only arguments. Callable Protocol handles this.

### 4.6 Covariance and Contravariance in Protocols

```python
from typing import Protocol, TypeVar

T_co = TypeVar("T_co", covariant=True)     # read-only / producer
T_contra = TypeVar("T_contra", contravariant=True)  # write-only / consumer

class Producer(Protocol[T_co]):
    def produce(self) -> T_co: ...

class Consumer(Protocol[T_contra]):
    def consume(self, item: T_contra) -> None: ...
```

**Covariant** (`T_co`): a `Producer[Dog]` is a `Producer[Animal]` — safe to use more specific
output where less specific is expected (Liskov safe for return types).

**Contravariant** (`T_contra`): a `Consumer[Animal]` is a `Consumer[Dog]` — a consumer that
handles any animal also handles dogs specifically (Liskov safe for parameter types).

**Invariant** (default `TypeVar`): must match exactly. Suitable for mutable containers where
both read and write occur, e.g., `list[int]` is NOT a subtype of `list[float]`.

### 4.7 `@classmethod` and `@staticmethod` in Protocols

```python
from typing import Protocol, Self

class Parseable(Protocol):
    @classmethod
    def from_string(cls, s: str) -> Self: ...

class Config:
    def __init__(self, data: dict) -> None:
        self.data = data

    @classmethod
    def from_string(cls, s: str) -> "Config":
        import json
        return cls(json.loads(s))

def load(factory: type[Parseable], raw: str) -> Parseable:
    return factory.from_string(raw)

load(Config, '{"key": "value"}')  # mypy: OK
```

`Self` [3.11] is essential here. Using `Config` as a return type instead of `Self` would break
the Protocol match for any subclass of `Config`.

### 4.8 Attributes in Protocols

```python
from typing import Protocol

class HasName(Protocol):
    name: str          # instance attribute — must exist and be str-compatible

class User:
    def __init__(self, name: str) -> None:
        self.name = name

def greet(obj: HasName) -> str:
    return f"Hello, {obj.name}"

greet(User("Alice"))   # OK
```

Protocol attribute declarations are **read-write** by default. To declare a read-only attribute
use `@property`:

```python
class HasName(Protocol):
    @property
    def name(self) -> str: ...   # read-only — implementing class may use @property or plain attr
```

---

## 5. Architecture Diagrams

### Nominal vs Structural Type Hierarchy

```
NOMINAL (ABC)                          STRUCTURAL (Protocol)

     Animal (ABC)                           Drawable (Protocol)
       /    \                                    |
    Dog     Cat                     +------------+------------+
  (inherits) (inherits)             |            |            |
                                  Circle       Rect        Widget
                                (no import of Drawable — just has draw())
```

### Protocol Satisfaction Check Flow

```
  Source code
      |
      v
  mypy / pyright
      |
      +-- resolves Protocol members (methods + attrs + signatures)
      |
      +-- for each call site: arg_type.has_all_members(Protocol)?
      |         YES -> OK
      |         NO  -> error: "Argument 1 ... incompatible type"
      |
      v
  Runtime (Python interpreter)
      |
      +-- @runtime_checkable? -> hasattr() loop (shallow)
      +-- No @runtime_checkable? -> no Protocol awareness at all
```

### Protocol Composition

```
  Readable           Writable
  Protocol           Protocol
     |                  |
     +------ ReadWritable ------+
                 Protocol
                    |
            (any class with
             read() AND write())
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 What `typing.Protocol` Does Internally

```python
import typing, inspect

class Drawable(typing.Protocol):
    def draw(self) -> None: ...

# Protocol stores members in __protocol_attrs__
print(Drawable.__protocol_attrs__)  # {'draw'}

# Protocol classes are marked with a special flag
print(typing.is_protocol(Drawable))  # True (Python 3.12+)
```

At static-analysis time, mypy reads `__protocol_attrs__` (or reconstructs it from the class body)
and verifies each call site. At runtime, only `@runtime_checkable` Protocols support `isinstance()`.

### 6.2 `__annotations__` vs `__protocol_attrs__`

Protocol collects *method definitions* and *annotated class attributes* into `__protocol_attrs__`.
Inherited Protocol members from parent Protocols are included. Non-Protocol parent members are
excluded.

```python
class Base(Protocol):
    def base_method(self) -> None: ...

class Extended(Base, Protocol):
    def extra_method(self) -> None: ...

print(Extended.__protocol_attrs__)  # {'base_method', 'extra_method'}
```

### 6.3 BROKEN → FIX: ABC Coupling vs Protocol Freedom

```python
# BROKEN: using ABC for a library interface — forces implementors to inherit from your ABC
from abc import ABC, abstractmethod

class Repository(ABC):
    # Every consumer of your library must import this class and subclass it.
    # Their class hierarchy is now coupled to your library's release cycle.
    @abstractmethod
    async def get(self, id: int) -> "User": ...

    @abstractmethod
    async def save(self, user: "User") -> None: ...

# Downstream code in a different package:
class PostgresRepository(Repository):    # Must inherit — no choice
    async def get(self, id: int) -> "User":
        ...

    async def save(self, user: "User") -> None:
        ...

# Test code:
class FakeRepository(Repository):       # Must inherit even for tests
    async def get(self, id: int) -> "User":
        return User(id=id, name="test")

    async def save(self, user: "User") -> None:
        pass
```

```python
# FIX: use Protocol — callers satisfy the contract without inheriting anything
from typing import Protocol

class User:
    def __init__(self, id: int, name: str) -> None:
        self.id = id
        self.name = name

class Repository(Protocol):
    # Downstream code does NOT need to import or inherit from this class.
    # mypy checks compatibility structurally at call sites.
    async def get(self, id: int) -> User: ...
    async def save(self, user: User) -> None: ...

# Downstream code — zero coupling to your library:
class PostgresRepository:                # No inheritance
    async def get(self, id: int) -> User:
        # ... real DB call
        return User(id=id, name="from-db")

    async def save(self, user: User) -> None:
        # ... real DB call
        pass

# Test double — also satisfies without inheriting:
class InMemoryRepository:
    def __init__(self) -> None:
        self._store: dict[int, User] = {}

    async def get(self, id: int) -> User:
        return self._store[id]

    async def save(self, user: User) -> None:
        self._store[user.id] = user

# Service that depends on the protocol:
class UserService:
    def __init__(self, repo: Repository) -> None:  # annotated as Repository (Protocol)
        self._repo = repo

    async def activate(self, user_id: int) -> None:
        user = await self._repo.get(user_id)       # mypy: OK — both implementations satisfy
        # ... business logic
        await self._repo.save(user)

# mypy verifies these at call time, not at class definition time:
UserService(PostgresRepository())    # OK
UserService(InMemoryRepository())    # OK — for tests, no patching of __mro__ needed
```

### 6.4 Callable Protocol — Handling Keyword-Only Arguments

```python
from typing import Callable, Protocol

# BROKEN: Callable cannot express keyword-only args
def pipeline(transform: Callable[[str], str], text: str) -> str:
    return transform(text)

def clean(text: str, *, strip: bool = True) -> str:  # keyword-only arg
    return text.strip() if strip else text

# mypy error: Argument 1 has incompatible type
# pipeline(clean, "  hello  ")

# FIX: use Callable Protocol
class TextTransform(Protocol):
    def __call__(self, text: str, *, strip: bool = True) -> str: ...

def pipeline_fixed(transform: TextTransform, text: str) -> str:
    return transform(text, strip=True)

pipeline_fixed(clean, "  hello  ")   # mypy: OK
```

### 6.5 Protocol with `__slots__`

```python
from typing import Protocol

class Efficient(Protocol):
    __slots__ = ()  # Tells type-checker: no dict-based attributes expected

    def compute(self) -> float: ...

# Implementing class does NOT need __slots__ — it only needs compute()
class FastComputer:
    __slots__ = ("_data",)

    def __init__(self, data: list[float]) -> None:
        self._data = data

    def compute(self) -> float:
        return sum(self._data) / len(self._data)
```

`__slots__ = ()` in a Protocol is a signal to static analysis tools, not enforced at runtime.

### 6.6 Standard-Library Structural Protocols

Python ships with pre-built Protocols in `typing` and `collections.abc`:

```python
from typing import SupportsInt, SupportsFloat, SupportsAbs, Sized, Iterable, Iterator
import collections.abc

# SupportsInt: any object with __int__
class Temperature:
    def __init__(self, celsius: float) -> None:
        self._c = celsius

    def __int__(self) -> int:
        return round(self._c)

def to_int(x: SupportsInt) -> int:
    return int(x)

to_int(Temperature(36.6))    # mypy: OK
to_int(3.14)                 # mypy: OK — float has __int__

# Sized: anything with __len__
def count(x: Sized) -> int:
    return len(x)

count([1, 2, 3])             # OK — list has __len__
count("hello")               # OK — str has __len__

# Iterable: anything with __iter__
def consume(items: Iterable[int]) -> int:
    return sum(items)
```

These are all *structural* — verified by the type checker, not by inheritance chains.

### 6.7 Using Protocol for Dependency Inversion (Real Pattern)

```python
from typing import Protocol, AsyncIterator
import asyncio

# Define the boundary as a Protocol in your domain layer
class EventStream(Protocol):
    async def subscribe(self, topic: str) -> AsyncIterator[bytes]: ...
    async def publish(self, topic: str, payload: bytes) -> None: ...

# Infrastructure layer — Kafka adapter (no import of EventStream needed)
class KafkaEventStream:
    async def subscribe(self, topic: str) -> AsyncIterator[bytes]:
        # ... aiokafka consumer
        yield b""  # simplified

    async def publish(self, topic: str, payload: bytes) -> None:
        # ... aiokafka producer
        pass

# Infrastructure layer — Redis Streams adapter
class RedisEventStream:
    async def subscribe(self, topic: str) -> AsyncIterator[bytes]:
        # ... aioredis xread
        yield b""

    async def publish(self, topic: str, payload: bytes) -> None:
        # ... aioredis xadd
        pass

# Application layer — depends only on Protocol, not on Kafka or Redis
class OrderProcessor:
    def __init__(self, stream: EventStream) -> None:
        self._stream = stream

    async def run(self) -> None:
        async for event in await self._stream.subscribe("orders"):
            print(f"Processing: {event!r}")
```

At runtime, pass `KafkaEventStream()` or `RedisEventStream()`. In tests, pass a simple in-memory
stub — no mocking frameworks needed.

---

## 7. Real-World Examples

### 7.1 FastAPI / Starlette Request Protocol

Starlette's `Request` class is consumed in middleware. Internal Starlette code uses structural
patterns: anything with `.method`, `.url`, `.headers` behaves as a request. Tests create minimal
fakes rather than constructing full `Request` objects.

### 7.2 Pydantic's `__get_validators__` / `__get_pydantic_core_schema__`

Pydantic v2 uses structural hooks: if your class defines `__get_pydantic_core_schema__`, Pydantic
picks it up automatically. The Protocol equivalent:

```python
from typing import Protocol, ClassVar, Any
from pydantic import GetCoreSchemaHandler
from pydantic_core import CoreSchema

class PydanticCompatible(Protocol):
    @classmethod
    def __get_pydantic_core_schema__(
        cls,
        source_type: Any,
        handler: GetCoreSchemaHandler,
    ) -> CoreSchema: ...
```

Third-party types (e.g., `numpy.ndarray`, `UUID`) satisfy this Protocol by implementing the
classmethod — no Pydantic inheritance required.

### 7.3 `dataclasses` + Protocol for Structural Compatibility

```python
from dataclasses import dataclass
from typing import Protocol

class Serializable(Protocol):
    def to_dict(self) -> dict[str, object]: ...

@dataclass
class Order:
    id: int
    amount: float

    def to_dict(self) -> dict[str, object]:
        return {"id": self.id, "amount": self.amount}

def send_to_api(payload: Serializable) -> None:
    import json
    data = json.dumps(payload.to_dict())
    # ... HTTP call

send_to_api(Order(1, 99.99))   # mypy: OK — Order satisfies Serializable
```

### 7.4 `io` Module Compatibility

`io.IOBase`, `io.RawIOBase`, `io.BufferedIOBase` are ABCs, but `typing.IO[str]` and
`typing.BinaryIO` in the `typing` module are essentially Protocol-shaped. Any file-like object
passed to `open()` and written via duck typing satisfies these types structurally.

### 7.5 Click / Typer CLI Parameter Converters

Click's `ParamType` is an ABC, but Typer's newer converters can be expressed as Protocols:

```python
from typing import Protocol, Any

class ClickParamType(Protocol):
    name: str
    def convert(self, value: Any, param: Any, ctx: Any) -> Any: ...
    def fail(self, message: str, param: Any, ctx: Any) -> None: ...
```

Third-party Click plugins satisfy this structurally without being registered under the ABC
hierarchy.

---

## 8. Tradeoffs

| Dimension | `typing.Protocol` | `abc.ABC` |
|---|---|---|
| **Coupling** | Zero — implementor needs no import | Hard — implementor must inherit |
| **Runtime check** | Shallow (`@runtime_checkable` + `hasattr`) | Full (`isinstance` via MRO) |
| **Retroactive conformance** | Yes — existing classes satisfy automatically | Only via `register()` |
| **IDE autocomplete** | Full support in VS Code / PyCharm / vim-lsp | Full support |
| **Error messages** | mypy shows first missing member | `TypeError` at instantiation |
| **Abstract enforcement** | None at runtime (static only) | Enforced — `TypeError` on instantiation |
| **Mixin methods** | Not possible (Protocol has no implementation) | Possible via default method bodies |
| **Variance support** | Native via `TypeVar(covariant=True)` | Manual with `Generic[T_co]` |
| **Best for** | Library boundaries, DI, pluggable backends, test doubles | Internal hierarchies needing shared implementation |

### When ABC wins over Protocol

Use `ABC` when:
1. You need **shared implementation** (mixin methods) across subclasses.
2. You need **runtime enforcement** — instantiating an incomplete subclass raises `TypeError`.
3. Your codebase is purely internal and you want the IDE to auto-suggest `@abstractmethod` stubs.
4. You need `register()` to extend compatibility to third-party classes you cannot modify.

### When Protocol wins over ABC

Use `Protocol` when:
1. You are defining a **library or package boundary** where you cannot require downstream inheritance.
2. You want **test doubles** (fakes, stubs) that do not inherit from production code.
3. You want to express contracts on **built-in or third-party types** retroactively.
4. You are using **dependency injection** — service takes an interface, implementations are swappable.
5. The interface is **purely behavioral** (no shared state or implementation).

---

## 9. When to Use / When NOT to Use

### Use Protocol When

- Designing public library APIs where users should not be forced to inherit your classes.
- Defining function parameter types that accept any duck-typed object (files, buffers, streams).
- Writing test doubles — a fake that does not inherit from the real class avoids accidentally
  inheriting production behavior that you wanted to stub out.
- Expressing callable interfaces with keyword-only arguments (where `Callable` falls short).
- Applying the Dependency Inversion Principle across module or package boundaries.

### Do NOT Use Protocol When

- You need **runtime isinstance()** as a security or correctness gate — `@runtime_checkable`
  only checks attribute existence, not signatures. An object with `close = 42` satisfies a
  `Closeable` protocol at runtime.
- You need **shared mixin implementation** — Protocols cannot carry method bodies that would be
  inherited (any method body in a Protocol becomes implementation detail of the Protocol class,
  not inherited by conforming classes).
- The team is not using a type checker — without mypy/pyright, Protocol annotations provide
  zero enforcement and only add cognitive overhead.
- You need **abstract enforcement at instantiation time** — use ABC if you want `TypeError`
  when someone forgets to implement an abstract method.

---

## 10. Common Pitfalls

### Pitfall 1: Forgetting `Protocol` in Composed Protocols

```python
from typing import Protocol

class Readable(Protocol):
    def read(self) -> bytes: ...

class Writable(Protocol):
    def write(self, data: bytes) -> int: ...

# BROKEN: omitting Protocol in the combined class
class ReadWritable(Readable, Writable):  # mypy: This is now a concrete class, not a Protocol
    ...

# FIX: always include Protocol in MRO when defining composed protocols
class ReadWritable(Readable, Writable, Protocol):
    ...
```

Without the `Protocol` marker in `ReadWritable`, mypy treats it as a normal concrete class that
inherits two Protocol bases. Any class you pass where `ReadWritable` is expected must inherit
from it — defeating the structural typing goal entirely.

### Pitfall 2: `@runtime_checkable` False Positive

```python
from typing import Protocol, runtime_checkable

@runtime_checkable
class JSONSerializable(Protocol):
    def to_json(self) -> str: ...

class Broken:
    to_json = "I am not a method"   # attribute with wrong type

obj = Broken()
print(isinstance(obj, JSONSerializable))   # True — runtime only checks name existence!

# mypy would catch this:
# error: Argument 1 to "returns_json" has incompatible type "Broken";
#        expected "JSONSerializable"
```

Never use `@runtime_checkable isinstance()` as a correctness gate in data validation pipelines.
Use it only for optional feature detection (checking whether a backend supports an optional
capability).

### Pitfall 3: Mutable Protocol Attribute — Read-Write vs Read-Only Mismatch

```python
from typing import Protocol

class HasItems(Protocol):
    items: list[int]   # read-write — implementing class must have a settable list[int]

class ReadOnlyContainer:
    @property
    def items(self) -> list[int]:   # only a getter — mypy error: property is read-only
        return self._items

# FIX: declare as property in Protocol to accept both property and plain attr
class HasItems(Protocol):
    @property
    def items(self) -> list[int]: ...   # satisfiable by property or regular attribute
```

### Pitfall 4: Protocol with Default Method Body

```python
from typing import Protocol

class Processor(Protocol):
    def process(self, data: str) -> str:
        return data.upper()   # This is NOT inherited by conforming classes!

class MyProcessor:
    pass   # Does NOT get process() from Protocol

p: Processor = MyProcessor()   # mypy error: MyProcessor missing process()
```

Protocol method bodies exist only on the Protocol class itself. They are not inherited by
conforming classes. If you want shared implementation, use an ABC mixin or a concrete base class
alongside the Protocol.

### Pitfall 5: Missing `covariant=True` Causing Unexpected Type Errors

```python
from typing import Protocol, TypeVar, Iterator

# BROKEN: invariant TypeVar in a read-only producer protocol
T = TypeVar("T")  # invariant

class Iterable(Protocol[T]):
    def __iter__(self) -> Iterator[T]: ...

def process_animals(items: Iterable["Animal"]) -> None: ...

dogs: list["Dog"] = []
process_animals(dogs)  # mypy error: list[Dog] is not Iterable[Animal] (invariant!)

# FIX: use covariant TypeVar for producer/output position
T_co = TypeVar("T_co", covariant=True)

class ReadableCollection(Protocol[T_co]):
    def __iter__(self) -> Iterator[T_co]: ...

def process_animals_fixed(items: ReadableCollection["Animal"]) -> None: ...

process_animals_fixed(dogs)  # mypy: OK — Dog is a subtype of Animal (covariant)
```

### Pitfall 6: `isinstance()` Performance in Hot Paths

```python
from typing import Protocol, runtime_checkable

@runtime_checkable
class Configurable(Protocol):
    def configure(self, **kwargs: object) -> None: ...
    def reset(self) -> None: ...
    def validate(self) -> bool: ...

# BROKEN: hot-path isinstance check — 3 hasattr() calls per object per iteration
for item in items:   # 1 000 000 items
    if isinstance(item, Configurable):
        item.configure(debug=True)

# FIX option 1: cache the check outside the loop
configurables = [x for x in items if isinstance(x, Configurable)]
for item in configurables:
    item.configure(debug=True)

# FIX option 2: use try/except (EAFP — idiomatic Python)
for item in items:
    try:
        item.configure(debug=True)
    except AttributeError:
        pass
```

The EAFP approach is fastest — zero overhead for the common case where all items are configurable.
`@runtime_checkable isinstance()` incurs ~3-5 µs per call for a 3-member Protocol. Across 1 million
items that is 3-5 seconds of pure overhead.

---

## 11. Technologies & Tools

| Tool | Protocol Support | Notes |
|---|---|---|
| **mypy** | Full PEP 544 — structural checking, variance, generic Protocols | `--strict` recommended for Protocol-heavy code |
| **pyright / pylance** | Full PEP 544 — often stricter than mypy on edge cases | Default in VS Code Python extension |
| **ruff** | Linting only — does not check Protocol structural conformance | Catches some `typing` anti-patterns via rules |
| **beartype** | Runtime enforcement of Protocol (deep check, not just `hasattr`) | ~10-50 µs per call; use on hot paths only if needed |
| **typing_extensions** | Backports `Protocol` to Python 3.7; `runtime_checkable`, `TypeAlias` | Use for libraries targeting Python < 3.8 |
| **attrs** | `attrs` classes structurally satisfy Protocols — no special integration needed | Ideal pair: attrs for data classes, Protocol for interfaces |
| **Pydantic v2** | `__get_pydantic_core_schema__` on any class is a de-facto Protocol hook | Structural, not nominal |

### beartype for Deep Runtime Protocol Checking

```python
from beartype import beartype
from beartype.typing import Protocol, runtime_checkable

@runtime_checkable
class Closeable(Protocol):
    def close(self) -> None: ...

@beartype                      # beartype validates signatures, not just attr presence
def release(res: Closeable) -> None:
    res.close()

class Fake:
    close = "not callable"

release(Fake())   # beartype raises BeartypeCallHintParamViolation — unlike plain isinstance()
```

`beartype` performs O(1) runtime type checking with full signature verification, not just
`hasattr()`. Use it when you genuinely need runtime Protocol enforcement (security-sensitive
input validation, plugin loading).

---

## 12. Interview Questions with Answers

**Q: What is structural subtyping and how does `typing.Protocol` implement it?**
Structural subtyping means a type is compatible based on its shape — the methods and attributes
it has — rather than its class hierarchy. `Protocol` [PEP 544, 3.8] defines a set of required
members; any class that provides those members (with compatible signatures) satisfies the Protocol,
verified by mypy/pyright at analysis time without runtime enforcement unless `@runtime_checkable`
is added.

**Q: How does Protocol differ from ABC?**
ABC requires explicit inheritance (nominal subtyping) and can provide default method bodies; it
raises `TypeError` at instantiation if abstract methods are missing. Protocol uses structural
matching — no inheritance needed — and is verified statically, not at instantiation. Prefer
Protocol for library boundaries and dependency injection; prefer ABC when you need shared
implementation or runtime instantiation enforcement.

**Q: What does `@runtime_checkable` do and what are its limitations?**
It allows `isinstance(obj, SomeProtocol)` at runtime. The check is *shallow*: it only verifies
that each Protocol member name exists on the object via `hasattr()`. It does NOT verify that
the attribute is callable, has the right signature, or returns the right type. A class attribute
`close = 42` satisfies `@runtime_checkable Closeable(Protocol)` at runtime even though `close`
is not a method. For security-sensitive checks, use `beartype` or explicit attribute + callable
guards.

**Q: When would you use a callable Protocol instead of `Callable[[X], Y]`?**
When the callable has keyword-only arguments, `*args`, or `**kwargs` that `Callable` cannot
express. `Callable[[str], str]` matches any one-argument str-to-str function. A callable
Protocol can require `def __call__(self, text: str, *, lowercase: bool = False) -> str`,
restricting to functions that accept that exact keyword argument.

**Q: What is the difference between a covariant and contravariant TypeVar in a Protocol?**
Covariant (`T_co = TypeVar("T_co", covariant=True)`): safe for *producer / output* positions.
A `Container[Dog]` satisfies `Container[Animal]` — you can use the more specific output where
the less specific is expected. Contravariant (`T_contra`, `contravariant=True`): safe for
*consumer / input* positions. A `Handler[Animal]` satisfies `Handler[Dog]` — a handler that
accepts any animal also handles dogs. Invariant (default): must match exactly; suitable when
the type is both read and written (mutable containers).

**Q: Why must `Protocol` appear in the MRO when composing multiple Protocols?**
Without `Protocol` in the MRO, Python (and mypy) treats the composed class as a concrete class
inheriting from Protocol bases. The structural-typing semantics are lost — mypy requires
explicit inheritance to satisfy the combined type, defeating the purpose. Adding `Protocol`
explicitly (e.g., `class ReadWritable(Readable, Writable, Protocol)`) preserves structural
typing for the composed interface.

**Q: Can Protocol method bodies be inherited by conforming classes?**
No. Method bodies written inside a Protocol class exist only on the Protocol class itself. They
do not propagate to conforming classes. If you want shared implementation, use an ABC mixin,
a concrete base class, or a separate utility function alongside the Protocol.

**Q: How does mypy verify that a class satisfies a Protocol?**
At each call site where a Protocol is expected, mypy checks that the actual argument's type
has all Protocol members with compatible signatures. It does not check this at class definition
time. This means you can define `PostgresRepository` before writing the `Repository` Protocol
and mypy will verify compatibility when you first pass a `PostgresRepository` where `Repository`
is expected.

**Q: What happens with `isinstance(x, P)` if `P` is a non-`@runtime_checkable` Protocol?**
Python raises `TypeError: Protocols with non-method members don't support issubclass()` (or a
similar message). You must decorate the Protocol with `@runtime_checkable` to use it with
`isinstance()`. Without the decorator, the Protocol is a purely static construct.

**Q: How do standard-library `SupportsInt`, `Sized`, and `Iterable` relate to Protocol?**
They are pre-defined Protocols in the `typing` module that formalize Python's existing dunder-
method contracts. `SupportsInt` requires `__int__`; `Sized` requires `__len__`; `Iterable[T]`
requires `__iter__`. They allow existing built-in types (`int`, `list`, `dict`, `str`) to satisfy
structural type annotations without inheriting from anything.

**Q: How would you type-hint a function that accepts any file-like object supporting read and write?**
Define a composed Protocol:
```python
from typing import Protocol
class ReadWritable(Protocol):
    def read(self, n: int = -1) -> bytes: ...
    def write(self, data: bytes) -> int: ...
```
Any `io.BytesIO`, `io.FileIO`, or custom buffer satisfies this. Alternatively, use
`typing.BinaryIO` from the standard library, which provides a broader file-like Protocol.

**Q: What is the performance cost of `@runtime_checkable isinstance()` checks?**
Each call performs a `hasattr()` lookup for every Protocol member. For a 3-member Protocol this
is roughly 3-5 µs per call. CPython 3.12 added internal caching so repeat checks on the same
type are near-zero after the first. In hot loops (millions of iterations), prefer EAFP
(`try/except AttributeError`) or cache the boolean result outside the loop.

**Q: How do you express a Protocol for an object that must support `async with` (async context manager)?**
```python
from typing import Protocol, Self
class AsyncContextManager(Protocol):
    async def __aenter__(self) -> Self: ...
    async def __aexit__(self, exc_type, exc_val, exc_tb) -> bool | None: ...
```
Any class that implements both dunder methods satisfies the Protocol structurally.

**Q: Can a `dataclass` satisfy a Protocol?**
Yes. A `@dataclass`-decorated class is an ordinary class with auto-generated `__init__`,
`__repr__`, and `__eq__`. If it has all the methods and attributes required by the Protocol
(with compatible types), mypy considers it a structural match. No special handling is needed.

**Q: How does Protocol support the Dependency Inversion Principle in Python?**
High-level modules define the interface as a Protocol. Low-level modules implement it without
importing the Protocol. The type checker verifies the match at the injection site. This inverts
the dependency: the interface is owned by the consumer (high-level module), not the implementor.
Test doubles implement the same Protocol without inheriting any production code, giving clean
isolation with zero mocking overhead.

---

## 13. Best Practices

1. **Default to Protocol for library or package boundaries.** Never force downstream users to
   inherit from your classes. Define all public interfaces as Protocols; let users satisfy them
   structurally.

2. **Use `@runtime_checkable` sparingly.** Add it only when you need `isinstance()` for optional
   feature detection (e.g., checking whether a plugin supports an optional method). Never rely
   on it for data validation or security gates — it only checks name presence.

3. **Always include `Protocol` in the MRO of composed Protocols.** `class Combined(A, B, Protocol)`
   not `class Combined(A, B)`. Forgetting this turns the class into a concrete type with nominal
   semantics.

4. **Use covariant TypeVars for producer-only Protocols.** Anything that only returns `T` (a
   container you only read from, a factory, an iterator) should use `T_co = TypeVar("T_co",
   covariant=True)` to allow subtype relationships.

5. **Prefer Protocol over `Callable` for complex function signatures.** As soon as you have
   keyword-only arguments, default values, or `*args`/`**kwargs`, replace `Callable` with a
   callable Protocol.

6. **Keep Protocols small and focused.** The Interface Segregation Principle applies doubly to
   Protocols. A Protocol with 8 methods is hard to satisfy and hard to test. Split into smaller
   Protocols and compose them where needed.

7. **Name Protocols as adjectives or nouns describing capability.** `Drawable`, `Closeable`,
   `Serializable`, `Repository`, `EventStream` — not `DrawableInterface` or `IDrawable`.

8. **Write test doubles directly, not via mocking libraries.** A test double that structurally
   satisfies a Protocol is more readable and more type-safe than `MagicMock()`. Use `unittest.mock`
   only when you genuinely need call recording.

9. **Check mypy with `--strict` or at minimum `--warn-return-any`.** Structural Protocol
   checking is only as good as your type annotations. Unannotated functions are treated as
   returning `Any`, which satisfies any Protocol — masking real errors.

10. **Document Protocol invariants in docstrings, not just signatures.** The type checker verifies
    structure, not semantics. Document preconditions and postconditions in the Protocol's docstring
    so implementors understand the behavioral contract, not just the structural one.

---

## 14. Case Study: Plugin System for a Data Pipeline Framework

### Problem

A data pipeline framework needs to support pluggable **source connectors** (Kafka, S3, database)
and **sink connectors** (ClickHouse, BigQuery, Parquet). Third-party developers must be able to
add connectors without importing the framework's internal ABCs or registering with a central
registry.

### Design

```
     Framework (defines Protocols)          External Plugins (no framework import)
     ─────────────────────────────          ──────────────────────────────────────
     SourceConnector (Protocol)  ─── satisfied by ──>  KafkaSource
     SinkConnector (Protocol)    ─── satisfied by ──>  BigQuerySink
     PipelineRunner                                    S3Source
           |                                           ClickHouseSink
           +──── injects via constructor ────>         (any future connector)
```

### Implementation

```python
# framework/connectors.py — NO external dependencies
from typing import Protocol, AsyncIterator

class Record:
    def __init__(self, key: bytes, value: bytes, metadata: dict[str, str]) -> None:
        self.key = key
        self.value = value
        self.metadata = metadata

class SourceConnector(Protocol):
    """Yields records from an external system."""

    async def connect(self) -> None: ...
    async def records(self, batch_size: int = 100) -> AsyncIterator[list[Record]]: ...
    async def commit(self, offset: int) -> None: ...
    async def close(self) -> None: ...

class SinkConnector(Protocol):
    """Writes records to an external system."""

    async def connect(self) -> None: ...
    async def write(self, records: list[Record]) -> int: ...   # returns count written
    async def flush(self) -> None: ...
    async def close(self) -> None: ...

class PipelineRunner:
    def __init__(self, source: SourceConnector, sink: SinkConnector) -> None:
        self._source = source
        self._sink = sink

    async def run(self, batch_size: int = 100) -> None:
        await self._source.connect()
        await self._sink.connect()
        offset = 0
        try:
            async for batch in self._source.records(batch_size):
                written = await self._sink.write(batch)
                offset += written
                await self._source.commit(offset)
        finally:
            await self._sink.flush()
            await self._source.close()
            await self._sink.close()
```

```python
# plugins/kafka_source.py — third-party plugin; does NOT import SourceConnector
from typing import AsyncIterator
# import aiokafka  # real dependency

class KafkaSource:
    def __init__(self, bootstrap_servers: str, topic: str, group_id: str) -> None:
        self._servers = bootstrap_servers
        self._topic = topic
        self._group = group_id

    async def connect(self) -> None:
        # self._consumer = AIOKafkaConsumer(...)
        pass

    async def records(self, batch_size: int = 100) -> AsyncIterator[list["Record"]]:
        # async for msg in self._consumer:
        yield []   # simplified

    async def commit(self, offset: int) -> None:
        pass

    async def close(self) -> None:
        pass
```

```python
# tests/test_pipeline.py — in-memory stubs, zero mocking libraries
from framework.connectors import PipelineRunner, Record

class FakeSource:
    def __init__(self, records: list[list[Record]]) -> None:
        self._records = records
        self.committed: list[int] = []

    async def connect(self) -> None: pass

    async def records(self, batch_size: int = 100):
        for batch in self._records:
            yield batch

    async def commit(self, offset: int) -> None:
        self.committed.append(offset)

    async def close(self) -> None: pass

class FakeSink:
    def __init__(self) -> None:
        self.written: list[Record] = []
        self.flushed = False

    async def connect(self) -> None: pass

    async def write(self, records: list[Record]) -> int:
        self.written.extend(records)
        return len(records)

    async def flush(self) -> None:
        self.flushed = True

    async def close(self) -> None: pass

import asyncio

def test_pipeline_commits_on_each_batch() -> None:
    records = [
        [Record(b"k1", b"v1", {}), Record(b"k2", b"v2", {})],
        [Record(b"k3", b"v3", {})],
    ]
    source = FakeSource(records)
    sink = FakeSink()
    runner = PipelineRunner(source, sink)   # mypy: OK — both satisfy Protocols
    asyncio.run(runner.run())
    assert len(sink.written) == 3
    assert sink.flushed is True
    assert source.committed == [2, 3]        # committed after each batch
```

### Key Takeaways

1. `KafkaSource` never imports `SourceConnector`. The framework owns the interface; plugins
   satisfy it implicitly. Third-party developers read the Protocol as documentation, not as a
   base class.
2. `FakeSource` and `FakeSink` are plain classes — 10-15 lines each. No mocking library needed.
   They satisfy the Protocols structurally; mypy verifies at the `PipelineRunner(source, sink)`
   call.
3. Adding a new connector (e.g., `PulsarSource`) requires zero framework changes — implement the
   4 methods and pass it to `PipelineRunner`.
4. The `PipelineRunner` constructor is typed against Protocols, not concrete classes. Passing a
   non-conforming object raises a mypy error at the call site with a clear message naming the
   missing member — not a `TypeError` buried in a runtime stack trace.
