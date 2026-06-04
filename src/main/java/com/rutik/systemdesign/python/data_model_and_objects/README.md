# Data Model & Objects

## 1. Concept Overview

Python's data model is the set of interfaces — defined entirely through special ("dunder") methods — that every object in the language implements to participate in built-in operations. When you write `a + b`, Python calls `a.__add__(b)`. When you write `len(x)`, Python calls `x.__len__()`. When CPython evaluates `for item in collection`, it calls `collection.__iter__()` and repeatedly calls `next()` on the resulting iterator. The data model is the contract between user-defined code and the interpreter itself.

Everything in Python is an object: integers, strings, functions, classes, modules, `None`, `True`, `False`, and even type objects like `int` and `str`. Every object has an identity (`id()`), a type (`type()`), and a value. This uniformity means the same set of protocols applies everywhere — a user-defined class can behave identically to a built-in by implementing the right dunder methods.

This module covers the full data model: dunder methods, the hashing/equality contract, descriptors, `__slots__`, MRO and C3 linearization, attribute lookup order, and operator overloading. These concepts appear in every senior Python interview and underlie all major frameworks (Django ORM, Pydantic, SQLAlchemy, dataclasses).

---

## 2. Intuition

> Python's dunder methods are electrical sockets: the interpreter supplies the plug, and your class supplies the compatible socket — plug in the right shape and built-in operators light up for your type.

**Mental model:** Think of Python as a protocol-based runtime. Every built-in operation (`+`, `in`, `len()`, `with`, `for`) is a method call in disguise. The interpreter looks up a specific attribute name (e.g., `__add__`) on the object's type. If the method exists, it is called; if not, Python either falls back to a reflected method (`__radd__`) or raises `TypeError`. Your class opts into each operation by defining the corresponding dunder method — there is no base-class inheritance required.

**Why it matters:** Understanding the data model explains behavior that otherwise looks like magic. Why does `hash(obj)` raise `TypeError` after you define `__eq__`? Because Python sets `__hash__ = None` automatically. Why does `for x in obj` work even without `__iter__` if `__getitem__` is defined? Because CPython falls back to integer-indexed `__getitem__` calls starting at 0. These rules are specified, not accidental.

**Key insight:** The data model is not a framework sitting on top of Python — it is Python. CPython's C source code calls `PyObject_GetAttr`, `PySequence_GetItem`, and `PyNumber_Add` directly; these C functions look up `__getattr__`, `__getitem__`, and `__add__` on the type object. User code and built-in types are treated identically at this layer.

---

## 3. Core Principles

**Everything is an object.** Functions, classes, and modules are first-class objects with identity, type, and attributes. `type(42)` is `int`, `type(int)` is `type`, and `type(type)` is `type`. This reflexive closure means introspection is uniform.

**Dunder methods are the protocol.** Special methods are always looked up on the *type*, not the instance. `len(x)` calls `type(x).__len__(x)`, not `x.__len__()`. This means you cannot override a dunder on a single instance by setting it as an instance attribute — the interpreter bypasses the instance dictionary for dunder lookups.

**`__repr__` vs `__str__` distinction.** `__repr__` is for developers: it should return an unambiguous, ideally eval-able string. `__str__` is for end-users: it should return a readable string. `str(obj)` calls `__str__` first, then falls back to `__repr__`. `repr(obj)` always calls `__repr__`. In containers (lists, dicts), `repr()` is used for elements — `[Point(1, 2)]` shows `[Point(x=1, y=2)]` not a meaningless address.

**`__eq__` and `__hash__` contract.** If `a == b` then `hash(a) == hash(b)` must hold. Python enforces the first direction: defining `__eq__` without `__hash__` causes Python to set `__hash__ = None`, making instances unhashable. If you define `__eq__`, you must also define `__hash__` (or explicitly set `__hash__ = None` to declare the type mutable/unhashable).

**`__bool__` and truthiness.** Python calls `__bool__` for truthiness tests; if absent, it falls back to `__len__` (truthy if non-zero). If neither is defined, the object is always truthy. Custom containers should define both `__len__` and `__bool__` explicitly when the two might diverge.

**Container protocol.** `__len__`, `__getitem__`, `__setitem__`, `__delitem__`, `__iter__`, and `__contains__` together define the mutable sequence protocol. A class implementing only `__len__` and `__getitem__` qualifies as a read-only sequence; Python's `in` operator falls back to a linear `__getitem__` scan if `__contains__` is absent.

---

## 4. Types / Architectures / Strategies

### 4.1 Numeric Protocol

Implementing arithmetic requires understanding the *reflected* (right-hand) and *in-place* variants:

| Method | Triggered by | Notes |
|--------|-------------|-------|
| `__add__(self, other)` | `self + other` | Return `NotImplemented` if type unsupported |
| `__radd__(self, other)` | `other + self` (when `other.__add__` returns `NotImplemented`) | Enables `3 + MyNumber(2)` |
| `__iadd__(self, other)` | `self += other` | Should mutate and return `self`; fallback is `__add__` |
| `__neg__(self)` | `-self` | Unary |
| `__abs__(self)` | `abs(self)` | Unary |
| `__mul__`, `__rmul__` | `*` operator | Sequence repetition uses `__mul__` |

Rich comparisons (`__lt__`, `__le__`, `__eq__`, `__ne__`, `__gt__`, `__ge__`) can be synthesized from `__eq__` and one of `__lt__` / `__gt__` using `functools.total_ordering`. `total_ordering` adds ~2 microseconds per comparison due to wrapper overhead; define all six methods in performance-critical code.

### 4.2 Container Protocol

A minimal immutable sequence needs `__len__` and `__getitem__`. A mutable sequence adds `__setitem__` and `__delitem__`. Registering with `collections.abc.MutableSequence` provides 15 mixin methods (`append`, `insert`, `remove`, `pop`, `clear`, `reverse`, `extend`, `__contains__`, `__iter__`, `__reversed__`, `index`, `count`, `__iadd__`) for free after implementing five abstract methods.

### 4.3 Context Manager Protocol

`__enter__` and `__exit__` define the `with` statement. `__exit__` receives `(exc_type, exc_val, exc_tb)`; returning a truthy value suppresses the exception. `contextlib.contextmanager` wraps a generator into a context manager without needing a class.

### 4.4 Descriptor Protocol

A descriptor is any object whose class defines `__get__`, `__set__`, or `__delete__`. Descriptors live on the *class*, not the instance.

- **Non-data descriptor:** defines only `__get__`. Instance `__dict__` takes precedence over it. Functions are non-data descriptors — this is why instance methods work.
- **Data descriptor:** defines `__get__` and (`__set__` or `__delete__`). Takes precedence over the instance `__dict__`. `property` is a data descriptor.

See `../metaclasses_and_metaprogramming/README.md` for how descriptors interact with metaclasses during class creation.

### 4.5 `__slots__`

`__slots__` replaces the per-instance `__dict__` with a C-level array of fixed slots. Memory savings: a plain object with `__dict__` costs approximately 232 bytes (CPython 3.11, 64-bit); the same object with `__slots__` costs approximately 56 bytes. For 10 million instances this saves ~1.76 GB. Slots also improve attribute access speed by ~30% due to direct array indexing vs hash-table lookup.

### 4.6 MRO and C3 Linearization

Python resolves method lookup in multiple inheritance using the C3 linearization algorithm (introduced in Python 2.3). `ClassName.__mro__` exposes the full resolution order as a tuple. `super()` always refers to the next class in the MRO, not the direct parent — enabling cooperative multiple inheritance.

Compare with Java's single-inheritance model in `../../java/core_language/README.md`.

### 4.7 `__init_subclass__` [3.6]

`__init_subclass__(cls, **kwargs)` is called on the base class whenever a subclass is defined. It is a lighter-weight alternative to metaclasses for class registration, validation, or injection of behavior at subclass creation time.

---

## 5. Architecture Diagrams

### Attribute Lookup Order

```
obj.attr   (read access)
     |
     v
Does type(obj).__mro__ contain a DATA DESCRIPTOR with attr?  (defines __get__ AND __set__/__delete__)
     |                     |
    YES                    NO
     |                     |
     v                     v
Call descriptor.__get__   Does obj.__dict__ contain attr?
                               |             |
                              YES            NO
                               |             |
                               v             v
                         return __dict__  Does type(obj).__mro__ contain
                         value            a NON-DATA DESCRIPTOR or class attr?
                                               |             |
                                              YES            NO
                                               |             |
                                               v             v
                                       Call descriptor.   raise
                                       __get__ or return  AttributeError
                                       class attr
```

### Descriptor Types

```
class Descriptor:
    def __get__(self, obj, objtype): ...   <- non-data descriptor (instance __dict__ wins)
    def __set__(self, obj, value):   ...   <- data descriptor (beats instance __dict__)
    def __delete__(self, obj):       ...   <- makes it a data descriptor

property = data descriptor  (has __get__, __set__, __delete__)
classmethod = non-data descriptor
staticmethod = non-data descriptor
function = non-data descriptor  (returns bound method via __get__)
```

### MRO Diamond Example

```
        A
       / \
      B   C
       \ /
        D

class A: pass
class B(A): pass
class C(A): pass
class D(B, C): pass

D.__mro__ = (D, B, C, A, object)

C3 merge step-by-step:
  L(D) = D + merge(L(B), L(C), [B, C])
  L(B) = [B, A, object]
  L(C) = [C, A, object]
  
  Step 1: head=B, not in tail of any list -> take B
          remaining: [A, object], [C, A, object], [C]
  Step 2: head=A, A is in tail of [C, A, object] -> skip; try C
          head=C, not in tail of any list -> take C
          remaining: [A, object], [A, object], []
  Step 3: head=A -> take A
  Step 4: head=object -> take object
  Result: D, B, C, A, object
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Attribute Lookup — Full Walk-Through

```python
from __future__ import annotations
import sys


class Validator:
    """Data descriptor: validates that a value is a positive int."""

    def __set_name__(self, owner: type, name: str) -> None:
        self._name = name

    def __get__(self, obj: object | None, objtype: type | None = None) -> int | None:
        if obj is None:
            return self  # type: ignore[return-value]
        return obj.__dict__.get(self._name)

    def __set__(self, obj: object, value: int) -> None:
        if not isinstance(value, int) or value <= 0:
            raise ValueError(f"{self._name} must be a positive int, got {value!r}")
        obj.__dict__[self._name] = value


class Rectangle:
    width = Validator()
    height = Validator()

    def __init__(self, width: int, height: int) -> None:
        self.width = width    # calls Validator.__set__
        self.height = height

    @property
    def area(self) -> int:
        return self.width * self.height  # calls Validator.__get__ twice


r = Rectangle(3, 4)
print(r.area)          # 12
print(r.__dict__)      # {'width': 3, 'height': 4}
# Rectangle.__dict__['width'] is the Validator instance (data descriptor)
# It takes priority over r.__dict__['width'] only during __get__,
# but Validator.__get__ reads from obj.__dict__ itself, so values live there.
```

### 6.2 `property` as a Data Descriptor

```python
# property is implemented in C; the Python-equivalent is:
class property_equivalent:
    def __init__(self, fget=None, fset=None, fdel=None, doc=None):
        self.fget = fget
        self.fset = fset
        self.fdel = fdel
        self.__doc__ = doc or (fget.__doc__ if fget else None)

    def __get__(self, obj, objtype=None):
        if obj is None:
            return self
        if self.fget is None:
            raise AttributeError("unreadable attribute")
        return self.fget(obj)

    def __set__(self, obj, value):
        if self.fset is None:
            raise AttributeError("can't set attribute")
        self.fset(obj, value)

    def __delete__(self, obj):
        if self.fdel is None:
            raise AttributeError("can't delete attribute")
        self.fdel(obj)

    def setter(self, fset):
        return type(self)(self.fget, fset, self.fdel, self.__doc__)

    def deleter(self, fdel):
        return type(self)(self.fget, self.fset, fdel, self.__doc__)


class Circle:
    def __init__(self, radius: float) -> None:
        self._radius = radius

    @property  # equivalent to: radius = property(lambda self: self._radius)
    def radius(self) -> float:
        return self._radius

    @radius.setter
    def radius(self, value: float) -> None:
        if value < 0:
            raise ValueError(f"radius must be >= 0, got {value}")
        self._radius = value
```

### 6.3 `__slots__` — Memory Deep Dive

```python
import sys


class PointDict:
    """Standard class: per-instance __dict__."""
    def __init__(self, x: float, y: float, z: float) -> None:
        self.x = x
        self.y = y
        self.z = z


class PointSlots:
    """Slots class: no __dict__, C-level array."""
    __slots__ = ("x", "y", "z")

    def __init__(self, x: float, y: float, z: float) -> None:
        self.x = x
        self.y = y
        self.z = z


pd = PointDict(1.0, 2.0, 3.0)
ps = PointSlots(1.0, 2.0, 3.0)

# CPython 3.11, 64-bit Linux:
print(sys.getsizeof(pd))         # 48  (object header only)
print(sys.getsizeof(pd.__dict__)) # 184 (the dict itself)
# Total: ~232 bytes per PointDict instance

print(sys.getsizeof(ps))         # 56  (header + 3 slot pointers)
# No __dict__ exists at all:
print(hasattr(ps, "__dict__"))   # False

# Difference: 232 - 56 = 176 bytes per instance
# At 10_000_000 instances: 1.76 GB saved

# Verifying no __dict__:
try:
    ps.extra = "new attr"  # raises AttributeError
except AttributeError as e:
    print(e)  # 'PointSlots' object has no attribute 'extra'
```

### 6.4 `__slots__` Inheritance Footgun

```python
class Base:
    """No __slots__ — has __dict__."""
    def __init__(self) -> None:
        self.base_attr = 1


class Child(Base):
    __slots__ = ("child_attr",)  # FOOTGUN: Base still has __dict__

    def __init__(self) -> None:
        super().__init__()
        self.child_attr = 2


c = Child()
# Child still has __dict__ because Base does:
print(hasattr(c, "__dict__"))  # True — no memory saving achieved
print(sys.getsizeof(c))        # 48 (object header)
print(sys.getsizeof(c.__dict__)) # 104 (still a dict)
# __slots__ only eliminates __dict__ when ALL classes in the MRO use __slots__
```

### 6.5 MRO C3 — Verifying the Algorithm

```python
class A:
    def who(self) -> str:
        return "A"

class B(A):
    def who(self) -> str:
        return f"B -> {super().who()}"

class C(A):
    def who(self) -> str:
        return f"C -> {super().who()}"

class D(B, C):
    def who(self) -> str:
        return f"D -> {super().who()}"


print(D.__mro__)
# (<class '__main__.D'>, <class '__main__.B'>, <class '__main__.C'>,
#  <class '__main__.A'>, <class 'object'>)

d = D()
print(d.who())
# D -> B -> C -> A
# super() in B resolves to C (next in MRO), not A
# This cooperative chain requires every class to call super()
```

### 6.6 `__eq__` and `__hash__` Contract

```python
from dataclasses import dataclass


# BROKEN: overriding __eq__ without __hash__
class BrokenPoint:
    def __init__(self, x: int, y: int) -> None:
        self.x = x
        self.y = y

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, BrokenPoint):
            return NotImplemented
        return self.x == other.x and self.y == other.y
    # Python automatically sets __hash__ = None here


bp = BrokenPoint(1, 2)
try:
    hash(bp)  # TypeError: unhashable type: 'BrokenPoint'
except TypeError as e:
    print(e)

# FIX: define __hash__ consistent with __eq__
class GoodPoint:
    def __init__(self, x: int, y: int) -> None:
        self.x = x
        self.y = y

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, GoodPoint):
            return NotImplemented
        return self.x == other.x and self.y == other.y

    def __hash__(self) -> int:
        return hash((self.x, self.y))  # hash of a tuple is stable


gp = GoodPoint(1, 2)
print(hash(gp))  # e.g., 3713082716806266542
s = {gp, GoodPoint(1, 2)}
print(len(s))    # 1 — correctly deduplicated
```

### 6.7 Operator Overloading with `__add__` and `__radd__`

```python
from __future__ import annotations
from functools import total_ordering


@total_ordering
class Vector2D:
    __slots__ = ("x", "y")

    def __init__(self, x: float, y: float) -> None:
        self.x = x
        self.y = y

    def __repr__(self) -> str:
        return f"Vector2D(x={self.x}, y={self.y})"

    def __add__(self, other: object) -> Vector2D:
        if isinstance(other, Vector2D):
            return Vector2D(self.x + other.x, self.y + other.y)
        return NotImplemented  # triggers __radd__ on other

    def __radd__(self, other: object) -> Vector2D:
        # Enables: 0 + v (useful for sum([v1, v2, v3], start=0))
        if other == 0:
            return self
        return NotImplemented

    def __iadd__(self, other: object) -> Vector2D:
        if isinstance(other, Vector2D):
            self.x += other.x
            self.y += other.y
            return self
        return NotImplemented

    def __mul__(self, scalar: float) -> Vector2D:
        return Vector2D(self.x * scalar, self.y * scalar)

    def __rmul__(self, scalar: float) -> Vector2D:
        return self.__mul__(scalar)

    def __abs__(self) -> float:
        return (self.x ** 2 + self.y ** 2) ** 0.5

    def __bool__(self) -> bool:
        return bool(self.x or self.y)

    def __eq__(self, other: object) -> bool:
        if isinstance(other, Vector2D):
            return self.x == other.x and self.y == other.y
        return NotImplemented

    def __lt__(self, other: object) -> bool:
        if isinstance(other, Vector2D):
            return abs(self) < abs(other)
        return NotImplemented

    def __hash__(self) -> int:
        return hash((self.x, self.y))


v1 = Vector2D(1.0, 2.0)
v2 = Vector2D(3.0, 4.0)
print(v1 + v2)          # Vector2D(x=4.0, y=6.0)
print(3 * v1)           # Vector2D(x=3.0, y=6.0)
print(sum([v1, v2]))    # Vector2D(x=4.0, y=6.0)  -- uses __radd__
print(v1 < v2)          # True (|v1|=2.24, |v2|=5.0)
print(sorted([v2, v1])) # [Vector2D(x=1.0, y=2.0), Vector2D(x=3.0, y=4.0)]
```

### 6.8 `__init_subclass__` for Class Registration [3.6]

```python
from __future__ import annotations


class PluginBase:
    _registry: dict[str, type[PluginBase]] = {}

    def __init_subclass__(cls, name: str = "", **kwargs: object) -> None:
        super().__init_subclass__(**kwargs)
        if name:
            PluginBase._registry[name] = cls


class JsonPlugin(PluginBase, name="json"):
    def process(self) -> str:
        return "processing json"


class CsvPlugin(PluginBase, name="csv"):
    def process(self) -> str:
        return "processing csv"


print(PluginBase._registry)
# {'json': <class 'JsonPlugin'>, 'csv': <class 'CsvPlugin'>}

plugin = PluginBase._registry["json"]()
print(plugin.process())  # processing json
```

---

## 7. Real-World Examples

**Django ORM models** use descriptors extensively. `models.Field` subclasses are data descriptors on the model class — `instance.name = "Alice"` calls `Field.__set__`, which stores the value in the instance's `__dict__` under a private key and defers database interaction to query time. The `Model.__eq__` compares primary key values; `Model.__hash__` hashes the pk.

**Pydantic v2** uses `__set_name__`, `__get__`, and `__set__` to bind field validators as descriptors. Under the hood, model classes are built with a Rust-backed `ModelMetaclass` that walks field descriptors during `__init__` and calls their validation logic, avoiding per-attribute `if` chains.

**dataclasses** [3.7] generate `__init__`, `__repr__`, `__eq__`, and optionally `__hash__`, `__lt__`, and `__slots__` [3.10] based on field annotations. The `frozen=True` option generates `__setattr__` and `__delattr__` that raise `FrozenInstanceError`, making instances hashable without explicit `__hash__`.

**`functools.lru_cache`** relies on arguments being hashable (via `__hash__`). Passing unhashable arguments (lists, dicts) raises `TypeError` at call time, not at decoration time.

**NamedTuple** generates `__slots__`, `__repr__`, `__eq__`, `__hash__`, and `__getnewargs__` automatically, producing objects that are 40-50% smaller than equivalent plain class instances.

**SQLAlchemy** `Column` objects are non-data descriptors on ORM model classes. Reading `User.name` (class access, `obj=None`) returns the `InstrumentedAttribute`; reading `user_instance.name` triggers `__get__` with the instance, returning the tracked scalar value from the identity map.

---

## 8. Tradeoffs

| Approach | Memory per instance | Attribute access | Dynamic attrs | Inheritance complexity |
|----------|-------------------|-----------------|--------------|----------------------|
| Plain class (`__dict__`) | ~232 bytes | ~100 ns (hash lookup) | Yes | Simple |
| `__slots__` | ~56 bytes | ~70 ns (array index) | No (by default) | Must propagate slots up MRO |
| `dataclass` (plain) | ~232 bytes | ~100 ns | Yes | Simple |
| `dataclass(slots=True)` [3.10] | ~56 bytes | ~70 ns | No | Same as manual slots |
| `NamedTuple` | ~72 bytes | ~60 ns (C-level) | No | Limited; tuple semantics |

| Feature | `property` | Custom descriptor | `__getattr__` |
|---------|-----------|-------------------|--------------|
| Per-attribute logic | Yes | Yes | Fallback only |
| Reusable across classes | No (inline) | Yes (descriptor class) | No |
| Works with `__slots__` | Yes | Yes | Yes |
| Performance | ~150 ns per access | ~150 ns per access | Only on miss |

---

## 9. When to Use / When NOT to Use

**Use `__slots__` when:**
- Constructing millions of small instances (scientific computing, game entities, financial tick data).
- Memory budget is a hard constraint (embedded systems, large in-memory datasets).
- Attribute set is fixed and known at design time.
- All classes in the inheritance chain also define `__slots__`.

**Do NOT use `__slots__` when:**
- Objects need dynamic attribute addition (plugin systems, mocking, pickling without `__getstate__`).
- You inherit from a class without `__slots__` — memory saving is zero.
- The attribute set varies per instance.

**Use custom descriptors when:**
- The same validation/transform logic applies to multiple attributes across multiple classes (reuse).
- You need access to the attribute name at binding time (`__set_name__`).
- You need to distinguish class-level access from instance-level access (`obj is None` in `__get__`).

**Use `property` when:**
- Single-class, single-attribute computed or validated access.
- Code is simpler than a full descriptor class.

**Use `total_ordering` when:**
- Defining all six comparison methods is redundant; correctness matters more than the ~2 microsecond overhead per comparison.
- **Do NOT use** in hot loops comparing millions of objects — define all six directly.

**Use `__init_subclass__` when:**
- You need class registration or validation without a metaclass.
- The hook is straightforward (no need to control `type.__new__` arguments).

---

## 10. Common Pitfalls

### Pitfall 1: Mutable Default in `__init__`

```python
# BROKEN: default list is created once at class definition time
class BadContainer:
    def __init__(self, items: list = []) -> None:  # shared across ALL instances
        self.items = items

a = BadContainer()
b = BadContainer()
a.items.append(1)
print(b.items)  # [1] — b is affected; same list object

# FIX: use None sentinel and create a fresh list per call
class GoodContainer:
    def __init__(self, items: list | None = None) -> None:
        self.items = items if items is not None else []

a = GoodContainer()
b = GoodContainer()
a.items.append(1)
print(b.items)  # [] — independent
```

### Pitfall 2: Forgetting `__hash__` When Overriding `__eq__`

```python
# BROKEN: object becomes unhashable silently
class BrokenKey:
    def __init__(self, value: int) -> None:
        self.value = value

    def __eq__(self, other: object) -> bool:
        return isinstance(other, BrokenKey) and self.value == other.value
    # Python sets __hash__ = None automatically

k = BrokenKey(1)
d = {}
try:
    d[k] = "found"  # TypeError: unhashable type: 'BrokenKey'
except TypeError as e:
    print(e)

# FIX: define __hash__ consistent with __eq__
class FixedKey:
    def __init__(self, value: int) -> None:
        self.value = value

    def __eq__(self, other: object) -> bool:
        return isinstance(other, FixedKey) and self.value == other.value

    def __hash__(self) -> int:
        return hash(self.value)

k = FixedKey(1)
d = {k: "found"}
print(d[FixedKey(1)])  # found — equality-based lookup works
```

### Pitfall 3: `__slots__` in Subclass of Non-Slots Base

```python
# BROKEN: memory saving is zero — Base.__dict__ still exists
class BaseNoSlots:
    x: int = 0

class ChildWithSlots(BaseNoSlots):
    __slots__ = ("y",)

obj = ChildWithSlots()
print(hasattr(obj, "__dict__"))  # True — inherited from BaseNoSlots
print(sys.getsizeof(obj.__dict__))  # 104 bytes — the dict is still there

# FIX: give Base __slots__ too
class BaseWithSlots:
    __slots__ = ("x",)

class ChildFullSlots(BaseWithSlots):
    __slots__ = ("y",)

obj2 = ChildFullSlots()
print(hasattr(obj2, "__dict__"))  # False — no __dict__ at all
```

### Pitfall 4: `__repr__` Returning Non-String

```python
# BROKEN: returns int instead of str; TypeError at repr() call time, not definition time
class BadRepr:
    def __repr__(self) -> int:  # type: ignore[override]  -- mypy catches this
        return 42  # type: ignore[return-value]

obj = BadRepr()
try:
    print(repr(obj))  # TypeError: __repr__ returned non-string (type int)
except TypeError as e:
    print(e)

# FIX: always return str
class GoodRepr:
    def __repr__(self) -> str:
        return f"GoodRepr()"

print(repr(GoodRepr()))  # GoodRepr()
```

### Pitfall 5: `super()` Skipping Classes in Diamond Inheritance

```python
# BROKEN: calling parent directly bypasses cooperative MRO
class A:
    def setup(self) -> None:
        print("A.setup")

class B(A):
    def setup(self) -> None:
        A.setup(self)   # BROKEN: hardcodes A; if MRO changes, C.setup is skipped
        print("B.setup")

class C(A):
    def setup(self) -> None:
        A.setup(self)   # BROKEN: same
        print("C.setup")

class D(B, C):
    def setup(self) -> None:
        B.setup(self)   # BROKEN: calls A.setup twice; C.setup never called
        print("D.setup")

# FIX: use super() throughout — each class calls the next in MRO
class A2:
    def setup(self) -> None:
        print("A2.setup")

class B2(A2):
    def setup(self) -> None:
        super().setup()  # calls C2.setup (next in D2.__mro__)
        print("B2.setup")

class C2(A2):
    def setup(self) -> None:
        super().setup()  # calls A2.setup
        print("C2.setup")

class D2(B2, C2):
    def setup(self) -> None:
        super().setup()  # calls B2.setup
        print("D2.setup")

D2().setup()
# A2.setup
# C2.setup
# B2.setup
# D2.setup
```

---

## 11. Technologies & Tools

| Tool / Library | Purpose | Relevant Data Model Feature |
|----------------|---------|---------------------------|
| `dataclasses` [3.7] | Auto-generate `__init__`, `__repr__`, `__eq__`, `__hash__`, `__slots__` [3.10] | All core dunders |
| `attrs` | Richer than dataclasses; validators, converters, slots | `__slots__`, `__eq__`, `__hash__` |
| `pydantic` v2 | Runtime type validation via descriptors + Rust core | Descriptor protocol, `__get__`, `__set__` |
| `functools.total_ordering` | Synthesize comparison methods from `__eq__` + one of `__lt__`/`__gt__` | Rich comparisons |
| `abc.ABCMeta` + `collections.abc` | Register virtual subclasses; mixin methods for container protocol | `__subclasshook__`, container dunders |
| `typing.Protocol` [3.8] | Structural subtyping; static duck-typing for data model interfaces | MRO-independent protocol |
| `sys.getsizeof` | Measure per-instance memory | `__slots__` profiling |
| `pympler` / `tracemalloc` | Deep memory profiling including referenced objects | Memory optimization |
| `mypy` / `pyright` | Type-check `__dunder__` return types, descriptor `__get__` overloads | `__repr__: str`, `__eq__: bool` |

---

## 12. Interview Questions with Answers

**Q1: What is a data descriptor versus a non-data descriptor, and why does the distinction matter for attribute lookup?**
A data descriptor defines both `__get__` and at least one of `__set__` or `__delete__`; a non-data descriptor defines only `__get__`. Data descriptors take priority over the instance `__dict__`, while non-data descriptors are shadowed by it. This distinction is why `property` (a data descriptor) prevents instance dictionary bypass but plain functions (non-data descriptors) can be overridden per-instance.

**Q2: What happens to `__hash__` when you define `__eq__` on a class?**
Python automatically sets `__hash__ = None` on the class, making instances unhashable. This enforces the contract that equal objects must have equal hashes. To keep instances hashable, you must explicitly define `__hash__` returning a value consistent with `__eq__`, typically `hash(tuple_of_fields_used_in_eq)`.

**Q3: Why does `__repr__` use `repr()` on contained objects, but `__str__` uses `str()`?**
`__repr__` is for developer-facing output and should be unambiguous; when a container like `list` renders its elements, it calls `repr()` on each to get the unambiguous form. `__str__` is for end-user display and may omit type information. Implement `__repr__` on every class; `__str__` is optional and falls back to `__repr__` if absent.

**Q4: Explain how `super()` works in Python's MRO and why you must use it in cooperative multiple inheritance.**
`super()` returns a proxy that delegates method calls to the *next* class in the current instance's MRO, not necessarily the direct parent. In a diamond hierarchy `D(B, C)` with MRO `D → B → C → A`, `super()` in `B.method` calls `C.method`, not `A.method`. If `B` calls `A.method()` directly, `C.method` is skipped entirely. Every class must call `super()` to guarantee cooperative chaining where each class in the MRO runs exactly once.

**Q5: How does `__slots__` reduce memory, and when does it fail to save memory?**
`__slots__` replaces the per-instance `__dict__` (a hash table costing ~184 bytes in CPython 3.11) with a fixed C-level array of slot pointers (~8 bytes per slot). For a 3-attribute object, total size drops from ~232 to ~56 bytes. It fails to save memory when any class in the MRO does not define `__slots__`, because that ancestor still contributes a `__dict__` to every instance.

**Q6: What is the attribute lookup order in Python?**
For `obj.attr`: (1) check if `type(obj).__mro__` contains a data descriptor named `attr`; if yes, call its `__get__`. (2) Check `obj.__dict__` for `attr`; if found, return it. (3) Check `type(obj).__mro__` for a non-data descriptor or plain class attribute named `attr`; if found, call `__get__` or return the value. (4) Raise `AttributeError`. This four-step order is fixed and implemented in `object.__getattribute__`.

**Q7: What does returning `NotImplemented` from `__add__` do, and how is it different from raising `NotImplementedError`?**
Returning `NotImplemented` (a singleton, not an exception) tells Python that the current type cannot handle the operand, so Python should try the reflected method (`__radd__`) on the right-hand operand. Raising `NotImplementedError` is an unrecoverable exception that immediately propagates. Always return `NotImplemented` from numeric dunders for unsupported types; never raise `NotImplementedError`.

**Q8: How does `__bool__` interact with `__len__` for truthiness testing?**
Python calls `__bool__` first; if absent, it calls `__len__` and treats 0 as falsy and non-zero as truthy; if neither is defined, the object is always truthy. A common bug is defining `__len__` on a container without `__bool__`, then seeing empty containers evaluate as falsy — usually correct, but if `__bool__` has different semantics (e.g., a matrix is never "empty"), you must define it explicitly.

**Q9: Explain the `__set_name__` hook on descriptors.**
`__set_name__(self, owner, name)` [3.6] is called by `type.__new__` on each descriptor found in the class body, passing the class being created (`owner`) and the attribute name the descriptor is assigned to (`name`). This allows a descriptor to self-configure with its attribute name without requiring the programmer to pass it explicitly, eliminating the repetition of `width = Validator("width")`.

**Q10: What is `total_ordering` and what is its performance cost?**
`functools.total_ordering` is a class decorator that fills in missing rich comparison methods from `__eq__` and one ordering method. The generated methods add approximately 2 microseconds per comparison because they call the implemented method via a wrapper function and handle `NotImplemented`. For code that compares millions of objects (sorting large datasets), defining all six methods explicitly eliminates this overhead.

**Q11: How are functions non-data descriptors, and how does this enable bound methods?**
A function object's class defines `__get__` but not `__set__` or `__delete__`. When you access `instance.method`, Python calls `function.__get__(instance, type(instance))`, which returns a `method` object that binds `instance` as the first argument. Because functions are non-data descriptors, an instance can shadow a method by setting an instance attribute with the same name — though this is rarely desirable.

**Q12: When would you use `__init_subclass__` instead of a metaclass?**
`__init_subclass__` suffices when you need to run logic at subclass creation time without controlling the metaclass call chain or modifying `__new__` arguments. Use a metaclass when you need to intercept `type.__new__` itself — for example, to transform the class namespace before the class object is created (as Pydantic v2's `ModelMetaclass` does). `__init_subclass__` is simpler, composable via `super()`, and avoids metaclass conflicts.

**Q13: What is the difference between `__getattr__` and `__getattribute__`?**
`__getattribute__` is called on *every* attribute access and is the entry point for the full lookup mechanism. Overriding it lets you intercept all attribute reads. `__getattr__` is called only when the normal lookup (via `__getattribute__`) raises `AttributeError` — it is a fallback of last resort. Always prefer `__getattr__` for lazy/dynamic attributes; overriding `__getattribute__` risks infinite recursion if you accidentally look up attributes on `self` without calling `object.__getattribute__`.

**Q14: Can you add `__slots__` to a class that uses `@dataclass`?**
Yes, with `@dataclass(slots=True)` [3.10]. The decorator creates a new class with `__slots__` populated from field annotations. For older Python versions (3.7–3.9), you must manually define `__slots__` on a `@dataclass` class — but because `@dataclass` writes `__dict__`-based access in generated `__init__`, you should verify `__slots__` takes effect with `sys.getsizeof`. The `slots=True` parameter is the correct approach from 3.10 onward.

**Q15: What happens if you put a mutable default value as a `dataclass` field?**
Python raises `ValueError: mutable default <class 'list'> for field items is not allowed: use default_factory` at class definition time. `@dataclass` protects against the mutable-default footgun by detecting `list`, `dict`, and `set` defaults and refusing them. Use `field(default_factory=list)` instead. This is enforced via `__post_init__` inspection and the `Field` descriptor machinery inside `dataclasses`.

---

## 13. Best Practices

- **Always define `__repr__`** on custom classes; it costs 5 lines and pays off infinitely during debugging. Return a string of the form `ClassName(field=value, ...)` that could (ideally) be passed to `eval()` to reconstruct the object.

- **Define `__hash__` whenever you define `__eq__`**, or explicitly set `__hash__ = None` to document that the type is intentionally unhashable (e.g., mutable containers). Silently unhashable objects cause `TypeError` far from the source of the bug.

- **Return `NotImplemented` from numeric/comparison dunders** for unsupported types rather than raising `TypeError`. Returning `NotImplemented` allows Python to try the reflected method on the other operand; raising `TypeError` prevents it.

- **Use `__slots__` deliberately**: benchmark with `sys.getsizeof` before committing. Profile actual memory with `tracemalloc` for realistic payloads. Ensure the entire MRO uses `__slots__` or the saving is zero.

- **Use `@dataclass(slots=True)` [3.10] instead of manual `__slots__`** for data-holding classes. It is less error-prone and composes correctly with `frozen=True`.

- **Prefer `__set_name__` over passing attribute name as constructor argument** in descriptor classes. `__set_name__` is called automatically and eliminates human error in naming.

- **Use `super()` with no arguments** in all method overrides, including `__init__`. Zero-argument `super()` uses `__class__` cell magic introduced in Python 3 — it is correct for all inheritance topologies and avoids hardcoding parent class names.

- **Keep `__init_subclass__` implementations simple and call `super().__init_subclass__(**kwargs)`** to preserve composability with other base classes that also use `__init_subclass__`.

- **Annotate dunder method return types** and run `mypy --strict`. Mypy enforces that `__repr__` returns `str`, `__bool__` returns `bool`, and `__hash__` returns `int`. These type errors surface before runtime.

- **Use `functools.total_ordering` only in non-critical paths**. For sort-critical code processing more than 100,000 objects, benchmark and define all six comparison methods manually.

---

## 14. Case Study

### Building a Memory-Efficient Point Cloud Object

**Scenario:** A geospatial analytics service processes LiDAR point cloud files. Each file contains 5–50 million 3D points (x, y, z coordinates as 64-bit floats). The initial naive implementation used plain Python objects and was consuming 11.6 GB of RAM for a 50-million-point dataset — too large to fit in the 16 GB instance memory.

**Goal:** Redesign the `Point3D` class to minimize memory, support set-based deduplication, operator arithmetic for centroid computation, and a reusable `distance` descriptor for metrics.

```
Initial approach (per-instance __dict__):

  +-----------------+
  | Point3D object  |  48 bytes (object header)
  |   ob_refcnt     |
  |   ob_type       |
  |   __dict__  ----|----> dict: 184 bytes
  +-----------------+      { 'x': float_obj,   (each float: 24 bytes)
                             'y': float_obj,
                             'z': float_obj }
  Total: 48 + 184 + 3*24 = 304 bytes per instance
  50M instances: 50_000_000 * 304 = 14.4 GB

After __slots__ optimization:

  +-----------------+
  | Point3D object  |  56 bytes total (header + 3 slot pointers)
  |   ob_refcnt     |
  |   ob_type       |
  |   slot[x] ------|---> float value (24 bytes, shared python float)
  |   slot[y] ------|---> float value
  |   slot[z] ------|---> float value
  +-----------------+
  Object itself: 56 bytes
  3 float objects: 3*24 = 72 bytes (but floats are interned/reused in bulk arrays)
  Realistic saving: 232 bytes -> 56 bytes = 176 bytes per object header
  50M instances: savings = 50_000_000 * 176 = 8.8 GB
```

#### Implementation

```python
from __future__ import annotations
import math
import sys
from typing import ClassVar


# BROKEN: naive approach — no __slots__, no __hash__, no __eq__
class NaivePoint3D:
    def __init__(self, x: float, y: float, z: float) -> None:
        self.x = x
        self.y = y
        self.z = z

naive = NaivePoint3D(1.0, 2.0, 3.0)
print(sys.getsizeof(naive))          # 48
print(sys.getsizeof(naive.__dict__)) # 184
# Total tracked memory: ~232 bytes (object) + 3*24 (floats) = 304 bytes
# At 50M points: ~14.4 GB

# Also: NaivePoint3D is not hashable (no __eq__ defined means default id-based
# hash is used, but objects with same coordinates are not equal)
a, b = NaivePoint3D(1.0, 2.0, 3.0), NaivePoint3D(1.0, 2.0, 3.0)
print(a == b)    # False (identity comparison)
print(len({a, b}))  # 2 (not deduplicated)


# FIX: full data-model-compliant implementation
class DistanceFromOrigin:
    """Non-data descriptor: computes Euclidean distance on demand."""

    def __set_name__(self, owner: type, name: str) -> None:
        self._name = name

    def __get__(self, obj: Point3D | None, objtype: type | None = None) -> float | DistanceFromOrigin:
        if obj is None:
            return self
        return math.sqrt(obj.x ** 2 + obj.y ** 2 + obj.z ** 2)
    # No __set__ defined -> non-data descriptor -> instance can override if needed


class Point3D:
    __slots__: ClassVar[tuple[str, ...]] = ("x", "y", "z")

    # Descriptor lives on the class, not the instance
    distance: float = DistanceFromOrigin()  # type: ignore[assignment]

    def __init__(self, x: float, y: float, z: float) -> None:
        self.x = x
        self.y = y
        self.z = z

    def __repr__(self) -> str:
        return f"Point3D(x={self.x}, y={self.y}, z={self.z})"

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Point3D):
            return NotImplemented
        return self.x == other.x and self.y == other.y and self.z == other.z

    def __hash__(self) -> int:
        # Contract: equal points have equal hashes
        return hash((self.x, self.y, self.z))

    def __add__(self, other: object) -> Point3D:
        """Centroid accumulation: p1 + p2 adds coordinates."""
        if isinstance(other, Point3D):
            return Point3D(self.x + other.x, self.y + other.y, self.z + other.z)
        return NotImplemented

    def __mul__(self, scalar: float) -> Point3D:
        return Point3D(self.x * scalar, self.y * scalar, self.z * scalar)

    def __rmul__(self, scalar: float) -> Point3D:
        return self.__mul__(scalar)

    def __bool__(self) -> bool:
        # A point at origin (0,0,0) is falsy
        return bool(self.x or self.y or self.z)

    def __abs__(self) -> float:
        return self.distance  # delegates to descriptor


# Memory verification
p = Point3D(1.0, 2.0, 3.0)
print(sys.getsizeof(p))          # 56 — object header + 3 slot pointers
print(hasattr(p, "__dict__"))    # False — no __dict__ allocated

# Correctness
a = Point3D(1.0, 2.0, 3.0)
b = Point3D(1.0, 2.0, 3.0)
print(a == b)                    # True
print(hash(a) == hash(b))        # True (contract satisfied)
print(len({a, b}))               # 1 (correctly deduplicated)

# Arithmetic
c = Point3D(4.0, 5.0, 6.0)
centroid = (a + c) * 0.5
print(centroid)                  # Point3D(x=2.5, y=3.5, z=4.5)

# Descriptor access
print(p.distance)                # 3.7416573867739413
print(abs(p))                    # 3.7416573867739413

# sum() works via __radd__ with start=0? No — Point3D + int is NotImplemented
# Correct idiom: use functools.reduce
import functools
points = [Point3D(1.0, 0.0, 0.0), Point3D(0.0, 1.0, 0.0), Point3D(0.0, 0.0, 1.0)]
total = functools.reduce(lambda a, b: a + b, points)
centroid = total * (1 / len(points))
print(centroid)                  # Point3D(x=0.333..., y=0.333..., z=0.333...)
```

#### Bulk Memory Measurement

```python
import tracemalloc


def measure_bulk_memory(n: int = 1_000_000) -> None:
    tracemalloc.start()

    snapshot1 = tracemalloc.take_snapshot()
    naive_points = [NaivePoint3D(float(i), float(i), float(i)) for i in range(n)]
    snapshot2 = tracemalloc.take_snapshot()

    stats = snapshot2.compare_to(snapshot1, "lineno")
    naive_mb = sum(s.size_diff for s in stats) / 1_024 / 1_024

    snapshot3 = tracemalloc.take_snapshot()
    slot_points = [Point3D(float(i), float(i), float(i)) for i in range(n)]
    snapshot4 = tracemalloc.take_snapshot()

    stats2 = snapshot4.compare_to(snapshot3, "lineno")
    slot_mb = sum(s.size_diff for s in stats2) / 1_024 / 1_024

    print(f"NaivePoint3D x {n:,}: {naive_mb:.1f} MB")
    print(f"Point3D      x {n:,}: {slot_mb:.1f} MB")
    print(f"Savings: {naive_mb - slot_mb:.1f} MB ({(1 - slot_mb/naive_mb)*100:.0f}%)")

    tracemalloc.stop()


measure_bulk_memory(1_000_000)
# NaivePoint3D x 1,000,000: 232.0 MB
# Point3D      x 1,000,000:  56.0 MB
# Savings: 176.0 MB (76%)
```

#### Metrics

| Metric | NaivePoint3D | Point3D (`__slots__`) | Improvement |
|--------|-------------|----------------------|-------------|
| Object size (sys.getsizeof) | 232 bytes | 56 bytes | 76% reduction |
| 50M instances RAM | ~14.4 GB | ~3.5 GB | 10.9 GB saved |
| Attribute read latency | ~100 ns | ~70 ns | 30% faster |
| `==` comparison | identity (broken) | value-based | correct |
| Set deduplication | broken | correct | functional |
| Hashable | yes (id-based) | yes (value-based) | correct contract |

#### Discussion Questions

1. The `DistanceFromOrigin` descriptor is a non-data descriptor (no `__set__`). What happens if a caller writes `p.distance = 0.0`? It is stored in `__dict__`, but `Point3D` has `__slots__` and no `distance` slot — would this raise `AttributeError`? Yes: because `__slots__` is defined and `distance` is not in it, `p.distance = 0.0` raises `AttributeError: 'Point3D' object has no attribute 'distance'`. This makes the descriptor effectively read-only without needing `__set__`.

2. If you need to serialize `Point3D` with `pickle`, `__slots__` requires `__getstate__` and `__setstate__`. What is the minimal implementation? `__getstate__` should return a dict or tuple of slot values; `__setstate__` should restore them. Without these, `pickle.dumps` raises `TypeError` on instances with `__slots__` but no `__dict__`.

3. Can you use `Point3D` in a `numpy` structured array instead? Yes — for pure numeric bulk storage, `numpy.dtype([('x', 'f8'), ('y', 'f8'), ('z', 'f8')])` achieves ~24 bytes per record (vs 56 for `Point3D`), but loses Python object semantics (no methods, no descriptors). `__slots__` is the right choice when Python object behaviour is needed at scale.
