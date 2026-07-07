# Pydantic v2 — Deep Dive

> See `../../the_type_system_and_typing/README.md` for `TypeVar`, `Protocol`, and `Annotated` types used by Pydantic.
> See `../dependency_injection_in_fastapi/README.md` for how Pydantic models are used as request bodies in FastAPI routes.

---

## 1. Concept Overview

Pydantic is a data validation and settings management library for Python that uses type annotations as the source of truth for runtime validation, coercion, and serialization. Version 2 (released June 2023) rewrote the validation engine in Rust via `pydantic-core`, achieving 5–50x faster throughput compared to v1 while introducing a cleaner, more explicit API.

Core capabilities:

- **Validation**: coerce and validate Python values against type annotations at runtime.
- **Serialization**: convert model instances to `dict`, JSON, or custom formats with fine-grained inclusion/exclusion control.
- **Settings management**: read configuration from environment variables, `.env` files, and secret files via `pydantic-settings`.
- **FastAPI integration**: FastAPI uses Pydantic models for request body parsing, response serialization, and automatic OpenAPI schema generation.
- **Custom types**: define reusable validation logic as first-class types via `__get_pydantic_core_schema__`.

Python version target: 3.11 / 3.12 with `from __future__ import annotations` as needed.

---

## 2. Intuition

> Pydantic is a strict border-control agent for your data: every value that enters your system is checked against its declared type, coerced into shape if possible, and rejected with a clear report if not — before any business logic ever runs.

**Mental model**: Think of a `BaseModel` as a dataclass with a PhD in type enforcement. Instead of raw `__init__` assignment, every field goes through a validation pipeline that can coerce, transform, and reject values before they are stored. The model is the contract; Pydantic is the enforcement.

**Why it matters**: The majority of bugs in API servers stem from trusting input. A missing null check, an unexpected string where an int was expected, a price field that accepts `-500` — these are runtime crashes or silent data corruption. Pydantic eliminates this class of bug at the boundary where data enters your application.

**Key insight**: v2 separates the Python-level schema definition (your `BaseModel` subclass) from the compiled validation core (`pydantic-core`, written in Rust). This means the schema is built once at class-definition time and then used for millions of validations at near-native speed. The cost is paid upfront, not per-request.

---

## 3. Core Principles

1. **Type annotations as schema**: field types, constraints, and defaults are declared with standard Python type hints. No separate schema DSL.
2. **Fail fast, fail loudly**: `ValidationError` reports every failing field in one structured error object — not just the first failure.
3. **Coercion by default, strict mode available**: `"42"` can be coerced to `int` 42 unless you opt into `strict=True`.
4. **Immutability is opt-in**: models are mutable by default; set `model_config = ConfigDict(frozen=True)` for hashable, immutable instances.
5. **Schema built once**: the JSON Schema / core schema is computed at class definition time via `__pydantic_core_schema__`, not on each validation call.
6. **Separation of validation and serialization**: validators run on input; serializers control output. The same model can validate loosely and serialize strictly.
7. **Composability**: models nest freely, `Annotated` types compose constraints, and `TypeAdapter` validates arbitrary types without a `BaseModel`.

---

## 4. Types / Architectures / Strategies

### 4.1 BaseModel

The primary building block. Fields are declared as class-level annotations with optional defaults.

```python
from pydantic import BaseModel, Field
from typing import Optional

class User(BaseModel):
    id: int
    name: str
    email: str
    age: Optional[int] = None
    score: float = Field(default=0.0, ge=0.0, le=100.0, description="User ranking score")
```

### 4.2 Field Validators (`@field_validator`)

Run per-field validation logic with three modes:

| Mode | Receives | Runs | Use case |
|------|----------|------|----------|
| `"before"` | raw input (pre-coercion) | before type coercion | normalize strings, strip whitespace |
| `"after"` | coerced Python value | after type coercion | business logic, range checks |
| `"wrap"` | raw input + handler callable | wraps entire pipeline | full control, custom error messages |

### 4.3 Model Validators (`@model_validator`)

Run after all field validators pass. Access `self` (mode `"after"`) or the raw dict (mode `"before"`). Use for cross-field constraints — e.g., `end_date > start_date`.

### 4.4 ConfigDict

`model_config = ConfigDict(...)` replaces the inner `class Config:` of v1. Common keys:

| Key | Default | Description |
|-----|---------|-------------|
| `from_attributes` | `False` | Read fields from object attributes (ORM mode) |
| `str_strip_whitespace` | `False` | Strip leading/trailing whitespace from `str` fields |
| `validate_default` | `False` | Run validators on fields with defaults |
| `frozen` | `False` | Make model immutable and hashable |
| `strict` | `False` | Disable type coercion globally |
| `populate_by_name` | `False` | Allow field population by Python name when alias is set |

### 4.5 Annotated Types

`Annotated[T, metadata]` attaches validation metadata to types without subclassing:

```python
from typing import Annotated
from pydantic import Field

PositiveInt = Annotated[int, Field(gt=0)]
EmailStr50 = Annotated[str, Field(max_length=50, pattern=r"^[^@]+@[^@]+\.[^@]+$")]
```

### 4.6 Discriminated Unions

Parse `Union` types efficiently by inspecting a literal discriminator field before attempting full validation:

```python
from typing import Literal, Union, Annotated
from pydantic import Field

class Cat(BaseModel):
    type: Literal["cat"]
    meows: int

class Dog(BaseModel):
    type: Literal["dog"]
    barks: int

Animal = Annotated[Union[Cat, Dog], Field(discriminator="type")]
```

### 4.7 BaseSettings (pydantic-settings)

Extends `BaseModel` to read values from environment variables and `.env` files. Install separately: `pip install pydantic-settings`.

### 4.8 TypeAdapter

Validates arbitrary types (not just `BaseModel`) without defining a full model class:

```python
from pydantic import TypeAdapter
adapter = TypeAdapter(list[int])
adapter.validate_python(["1", "2", "3"])  # [1, 2, 3]
```

---

## 5. Architecture Diagrams

### Validation Pipeline (single field)

```
Input value (raw)
        |
        v
  [mode="before" @field_validator]   <-- optional, pre-coercion
        |
        v
  [pydantic-core Rust validator]     <-- type coercion + constraint checks
        |                               (ge, le, max_length, pattern, ...)
        v
  [mode="after" @field_validator]    <-- optional, post-coercion
        |
        v
  Stored field value
```

### Full Model Lifecycle

```
User.__init__(raw_data)
        |
        v
  [mode="before" @model_validator]   <-- optional, sees raw dict
        |
        v
  Per-field pipeline (above) x N fields
        |
        v
  [mode="after" @model_validator]    <-- optional, sees validated model
        |
        v
  Model instance stored
        |
   model.model_dump()
        |
        v
  [Serializer pipeline]              <-- field serializers, include/exclude
        |
        v
  dict / JSON output
```

### pydantic-core Architecture

```
Python layer (pydantic 2.x)
  BaseModel subclass definition
        |
        v (class creation time, once)
  CoreSchema construction
  (Python dict describing validation rules)
        |
        v
  pydantic-core (Rust, via PyO3)
  SchemaValidator.validate_python(data)
        |
   fast path:         slow path:
   cached validators  custom Python validators
        |                   |
        +-------------------+
                |
                v
        Validated Python object
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 BaseModel Basics

```python
from __future__ import annotations
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime

class Product(BaseModel):
    id: int
    name: str
    price: float = Field(ge=0.0, description="Price in USD, must be non-negative")
    stock: int = Field(default=0, ge=0, description="Units in stock")
    created_at: datetime = Field(default_factory=datetime.utcnow)
    tags: list[str] = Field(default_factory=list)
    description: Optional[str] = None  # explicit None default

# Coercion: "42" -> 42, "19.99" -> 19.99
p = Product(id="42", name="  Widget  ", price="19.99")
print(p.id)       # 42  (int, coerced from str)
print(p.price)    # 19.99 (float, coerced from str)
print(p.stock)    # 0 (default)
```

### 6.2 @field_validator — v1 vs v2 Side by Side

**v1 syntax (legacy — do not write in new code):**

```python
# BROKEN: v1 validator syntax, not valid in v2
from pydantic import validator  # v1 import

class UserV1(BaseModel):
    email: str

    @validator("email", pre=True)    # v1: pre=True
    def normalize_email(cls, v):
        return v.strip().lower()
```

**v2 syntax (correct):**

```python
from pydantic import BaseModel, field_validator

class UserV2(BaseModel):
    email: str
    age: int

    @field_validator("email", mode="before")   # v2: mode="before"
    @classmethod
    def normalize_email(cls, v: object) -> str:
        if isinstance(v, str):
            return v.strip().lower()
        return v  # let pydantic-core raise the type error

    @field_validator("age", mode="after")
    @classmethod
    def validate_adult(cls, v: int) -> int:
        if v < 18:
            raise ValueError("Must be 18 or older")
        return v
```

**mode="wrap" — full pipeline control:**

```python
from pydantic import field_validator
from pydantic.functional_validators import FieldValidatorMode
from typing import Any, Callable

class StrictPositiveModel(BaseModel):
    value: int

    @field_validator("value", mode="wrap")
    @classmethod
    def no_negative_strings(
        cls,
        v: Any,
        handler: Callable[[Any], int],
    ) -> int:
        if isinstance(v, str) and v.startswith("-"):
            raise ValueError("Negative string not allowed")
        result = handler(v)  # call the standard pipeline
        if result < 0:
            raise ValueError("Value must be positive")
        return result
```

### 6.3 @model_validator — Cross-Field Validation

```python
from pydantic import BaseModel, model_validator
from datetime import date
from typing import Self

class DateRange(BaseModel):
    start: date
    end: date
    label: str = ""

    @model_validator(mode="after")
    def validate_range(self) -> Self:
        if self.end < self.start:
            raise ValueError(
                f"end ({self.end}) must be >= start ({self.start})"
            )
        return self

    @model_validator(mode="before")
    @classmethod
    def normalize_label(cls, data: dict) -> dict:
        # mode="before" receives the raw input dict before field validation
        if isinstance(data, dict) and not data.get("label"):
            data = {**data, "label": "default"}
        return data
```

### 6.4 model_config = ConfigDict(...)

```python
from pydantic import BaseModel, ConfigDict, Field

class OrmProduct(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,      # read from ORM object attributes
        str_strip_whitespace=True, # strip whitespace on all str fields
        validate_default=True,     # run validators even on default values
        frozen=True,               # immutable after creation
    )

    id: int
    name: str
    price: float = Field(ge=0.0)

# from_attributes=True: construct from a SQLAlchemy ORM row
# sqlalchemy_row = session.get(ProductRow, 1)
# product = OrmProduct.model_validate(sqlalchemy_row)

# frozen=True: immutable
p = OrmProduct(id=1, name="Widget", price=9.99)
# p.name = "Other"  # raises ValidationError: Instance is frozen
```

### 6.5 Serialization

```python
from pydantic import BaseModel
from typing import Optional

class Address(BaseModel):
    street: str
    city: str
    zip_code: Optional[str] = None

class Contact(BaseModel):
    name: str
    email: str
    address: Optional[Address] = None
    internal_token: str = ""

c = Contact(
    name="Alice",
    email="alice@example.com",
    address=Address(street="1 Main St", city="Springfield"),
    internal_token="secret-abc",
)

# Full dict
c.model_dump()
# {'name': 'Alice', 'email': 'alice@example.com',
#  'address': {'street': '1 Main St', 'city': 'Springfield', 'zip_code': None},
#  'internal_token': 'secret-abc'}

# Exclude None values
c.model_dump(exclude_none=True)
# address.zip_code is omitted

# Include only specific fields
c.model_dump(include={"name", "email"})
# {'name': 'Alice', 'email': 'alice@example.com'}

# Exclude specific fields
c.model_dump(exclude={"internal_token"})

# JSON string (uses pydantic-core's Rust JSON serializer — faster than json.dumps)
c.model_dump_json(exclude={"internal_token"})
```

### 6.6 Annotated Types — Reusable Constraints

```python
from typing import Annotated
from pydantic import BaseModel, Field
import re

# Reusable constrained types — define once, use everywhere
PositiveInt   = Annotated[int,   Field(gt=0)]
NonNegFloat   = Annotated[float, Field(ge=0.0)]
ShortStr      = Annotated[str,   Field(min_length=1, max_length=100)]
EmailField    = Annotated[str,   Field(pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$")]
Percentage    = Annotated[float, Field(ge=0.0, le=100.0)]

class Item(BaseModel):
    id: PositiveInt
    name: ShortStr
    price: NonNegFloat
    discount: Percentage = 0.0
    owner_email: EmailField

# Compose with Optional
OptionalEmail = Annotated[str | None, Field(default=None, pattern=r"^[^@\s]+@[^@\s]+\.[^@\s]+$")]
```

### 6.7 Custom Types — PhoneNumber

v1 used `__get_validators__`; v2 uses `__get_pydantic_core_schema__`:

```python
from __future__ import annotations
from pydantic import GetCoreSchemaHandler
from pydantic_core import core_schema
import re

_PHONE_RE = re.compile(r"^\+?[1-9]\d{7,14}$")

class PhoneNumber(str):
    """E.164-ish phone number: +1234567890"""

    @classmethod
    def __get_pydantic_core_schema__(
        cls,
        source_type: type,
        handler: GetCoreSchemaHandler,
    ) -> core_schema.CoreSchema:
        return core_schema.no_info_plain_validator_function(
            cls._validate,
            serialization=core_schema.to_string_ser_schema(),
        )

    @classmethod
    def _validate(cls, value: object) -> PhoneNumber:
        if not isinstance(value, str):
            raise ValueError(f"PhoneNumber expects str, got {type(value)}")
        cleaned = re.sub(r"[\s\-\(\)]", "", value)
        if not _PHONE_RE.match(cleaned):
            raise ValueError(f"Invalid phone number: {value!r}")
        return cls(cleaned)


from pydantic import BaseModel

class Contact(BaseModel):
    name: str
    phone: PhoneNumber

c = Contact(name="Bob", phone="+1 (555) 867-5309")
print(c.phone)   # +15558675309 (normalized)
print(type(c.phone))  # <class '__main__.PhoneNumber'>
```

### 6.8 BaseSettings

```python
# pip install pydantic-settings
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field, SecretStr
from typing import Optional

class DatabaseSettings(BaseSettings):
    host: str = "localhost"
    port: int = 5432
    name: str = "app_db"
    password: SecretStr  # never logged, never serialized as plaintext

class AppSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        env_nested_delimiter="__",  # DB__HOST -> db.host
        case_sensitive=False,
    )

    debug: bool = False
    secret_key: SecretStr = Field(..., description="JWT signing key")
    allowed_hosts: list[str] = ["localhost"]
    db: DatabaseSettings = DatabaseSettings()

# Environment variables:
# DB__HOST=prod-db.internal
# DB__PASSWORD=s3cret
# SECRET_KEY=my-jwt-key
# DEBUG=true

settings = AppSettings()
print(settings.debug)        # True
print(settings.db.host)      # prod-db.internal
print(settings.db.password)  # SecretStr('**********')
print(settings.db.password.get_secret_value())  # s3cret
```

### 6.9 Performance — pydantic-core Numbers

Benchmark: 1 million `User(id=i, name="Alice", email="alice@example.com")` validations.

| Version | Time | Throughput |
|---------|------|------------|
| Pydantic v1.10 | 3.2 s | ~312 k/s |
| Pydantic v2.0 | 0.18 s | ~5.6 M/s |
| Speedup | ~17.8x | — |

For deeply nested models with complex validators the speedup narrows to ~5x; for flat models with only primitive types it reaches ~50x. The Rust core (`SchemaValidator`) handles coercion and constraint checks; Python callbacks are invoked only when `@field_validator` or `@model_validator` decorators are present.

Model construction cost is paid once at class definition, not per validation call.

### 6.10 Discriminated Unions

Without a discriminator, Pydantic tries every branch in order (O(n) worst case). With a discriminator on a `Literal` field, it does a single O(1) dict lookup:

```python
from typing import Literal, Union, Annotated
from pydantic import BaseModel, Field

class TextContent(BaseModel):
    type: Literal["text"]
    body: str
    max_tokens: int = 4096

class ImageContent(BaseModel):
    type: Literal["image"]
    url: str
    width: int
    height: int

class AudioContent(BaseModel):
    type: Literal["audio"]
    url: str
    duration_seconds: float

MessageContent = Annotated[
    Union[TextContent, ImageContent, AudioContent],
    Field(discriminator="type"),
]

class Message(BaseModel):
    id: int
    content: MessageContent

# Pydantic reads content["type"] == "image" and goes directly to ImageContent
m = Message(id=1, content={"type": "image", "url": "https://cdn.example.com/img.png", "width": 800, "height": 600})
print(type(m.content))  # <class '__main__.ImageContent'>
```

---

## 7. Real-World Examples

### 7.1 FastAPI Request Body with Nested Model

```python
from fastapi import FastAPI
from pydantic import BaseModel, Field, field_validator, EmailStr
from typing import Optional

# pip install pydantic[email] for EmailStr
app = FastAPI()

class AddressIn(BaseModel):
    street: str = Field(min_length=1, max_length=200)
    city: str = Field(min_length=1, max_length=100)
    country_code: str = Field(min_length=2, max_length=2, pattern=r"^[A-Z]{2}$")

class UserCreate(BaseModel):
    username: str = Field(min_length=3, max_length=50, pattern=r"^[a-z0-9_]+$")
    email: str
    age: int = Field(ge=13, le=120)
    address: Optional[AddressIn] = None

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, v: object) -> str:
        if isinstance(v, str):
            return v.strip().lower()
        return v

@app.post("/users/")
async def create_user(body: UserCreate):
    # body is already validated and coerced
    return {"username": body.username, "email": body.email}
```

### 7.2 ORM Integration Pattern

```python
from pydantic import BaseModel, ConfigDict
from sqlalchemy import Column, Integer, String, Float
from sqlalchemy.orm import DeclarativeBase

class Base(DeclarativeBase):
    pass

class ProductRow(Base):
    __tablename__ = "products"
    id = Column(Integer, primary_key=True)
    name = Column(String)
    price = Column(Float)

class ProductSchema(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    price: float

# In a route handler:
# row = session.get(ProductRow, product_id)
# schema = ProductSchema.model_validate(row)  # reads .id, .name, .price attributes
# return schema.model_dump()
```

### 7.3 Partial Update with exclude_unset

```python
from pydantic import BaseModel
from typing import Optional

class UserUpdate(BaseModel):
    name: Optional[str] = None
    email: Optional[str] = None
    age: Optional[int] = None

# Client sends only the fields they want to change
payload = {"email": "new@example.com"}
update = UserUpdate(**payload)

# model_dump(exclude_unset=True) returns only explicitly provided fields
print(update.model_dump(exclude_unset=True))
# {'email': 'new@example.com'}
# name and age are NOT included — even though they default to None
```

---

## 8. Tradeoffs

| Dimension | Pydantic v2 | Pydantic v1 | attrs + cattrs | dataclasses (stdlib) |
|-----------|-------------|-------------|----------------|----------------------|
| Validation speed | 5.6 M/s (Rust core) | 312 k/s | ~1–2 M/s | No validation |
| Schema overhead | Paid at class def | Paid at class def | Paid at class def | None |
| JSON Schema | Built-in | Built-in | Via cattrs converters | None |
| ORM integration | `from_attributes=True` | `orm_mode=True` | Manual | Manual |
| Strictness | Coercion default, strict opt-in | Coercion default | Strict by default | No validation |
| Settings mgmt | `pydantic-settings` (separate pkg) | `BaseSettings` built-in | N/A | N/A |
| FastAPI native | Yes (v2 since FastAPI 0.100) | Yes (v1, legacy) | No | No |
| Custom types | `__get_pydantic_core_schema__` | `__get_validators__` | `structure_hook` | N/A |
| Frozen / immutable | `ConfigDict(frozen=True)` | `Config.allow_mutation=False` | `@define(frozen=True)` | `@dataclass(frozen=True)` |

**Key tradeoff**: Pydantic v2's Rust backend achieves high throughput but adds a compiled dependency (`pydantic-core`). In edge/embedded environments where a pure-Python dependency is required, `attrs` or `marshmallow` may be preferable. For standard cloud/server workloads, Pydantic v2 is the clear choice.

---

## 9. When to Use / When NOT to Use

### Use Pydantic v2 when:

- Building a FastAPI application — it is the native model layer.
- Validating external API inputs, form data, or message queue payloads.
- Reading typed configuration from environment variables (BaseSettings).
- Serializing SQLAlchemy ORM rows to JSON responses (`from_attributes=True`).
- Defining strict data contracts between services in a microservices architecture.
- Implementing discriminated unions for polymorphic payloads (event types, content blocks).

### Do NOT use Pydantic v2 when:

- You need a pure-Python validation library with no compiled extensions (edge devices, constrained build environments).
- Your data model is entirely internal and never crosses a validation boundary — plain dataclasses are lighter.
- You are building a Django project and already use DRF serializers — mixing both adds surface area for bugs.
- Performance is so critical that even the Python-to-Rust call overhead matters (sub-microsecond hot paths) — consider writing a bespoke Cython or C extension.
- The schema changes so frequently at runtime that static class-definition-time schema building becomes a liability — consider `TypeAdapter` with dynamic schema construction instead.

---

## 10. Common Pitfalls

### Pitfall 1: Using v1 `@validator` decorator in v2 code

**BROKEN (v1 syntax):**

```python
from pydantic import BaseModel, validator  # v1 import

class Order(BaseModel):
    email: str

    @validator("email", pre=True)   # pre=True is v1 API
    def normalize(cls, v):
        return v.lower()
```

This import still works in v2 for backward compatibility but emits a `PydanticDeprecatedSince20` warning and will be removed in v3.

**FIX (v2 syntax):**

```python
from pydantic import BaseModel, field_validator

class Order(BaseModel):
    email: str

    @field_validator("email", mode="before")   # mode="before" replaces pre=True
    @classmethod                                # @classmethod is required in v2
    def normalize(cls, v: object) -> str:
        if isinstance(v, str):
            return v.strip().lower()
        return v
```

Note: v2 validators must be decorated with `@classmethod`. Forgetting this raises a `PydanticUserError`.

---

### Pitfall 2: Using inner `class Config` instead of `ConfigDict`

**BROKEN (v1 pattern):**

```python
class UserProfile(BaseModel):
    class Config:           # v1 inner class
        orm_mode = True     # v1 key name
```

**FIX (v2 pattern):**

```python
from pydantic import BaseModel, ConfigDict

class UserProfile(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,   # renamed from orm_mode
    )
```

The key was renamed from `orm_mode` to `from_attributes` to make it clearer that it works with any object attributes, not just ORM rows.

---

### Pitfall 3: Calling `.dict()` or `.json()` — deprecated in v2

**BROKEN:**

```python
user = User(id=1, name="Alice")
data = user.dict()    # DeprecationWarning in v2; will be removed in v3
json_str = user.json()  # same
```

**FIX:**

```python
data = user.model_dump()
json_str = user.model_dump_json()
```

---

### Pitfall 4: Accessing `__fields__` in v2 (v1 pattern)

**BROKEN:**

```python
# v1 pattern: __fields__ returns dict[str, ModelField]
for name, field in MyModel.__fields__.items():
    print(name, field.type_)  # v1 ModelField API
```

In v2 `__fields__` still exists for compatibility but returns a compatibility shim, not the real metadata.

**FIX:**

```python
# v2: model_fields returns dict[str, FieldInfo]
for name, field_info in MyModel.model_fields.items():
    print(name, field_info.annotation, field_info.default)
```

---

### Pitfall 5: Mutable default values

**BROKEN:**

```python
class Config(BaseModel):
    allowed_hosts: list[str] = []   # shared mutable default!
```

Pydantic actually handles this safely by deep-copying the default for each instance, but it is still a bad habit and can cause surprising behavior with complex mutable objects.

**FIX:**

```python
from pydantic import Field

class Config(BaseModel):
    allowed_hosts: list[str] = Field(default_factory=list)
```

---

### Pitfall 6: `@model_validator(mode="after")` returning `None`

**BROKEN:**

```python
@model_validator(mode="after")
def check_dates(self):
    if self.end < self.start:
        raise ValueError("end must be after start")
    # forgot to return self
```

The validator must return `self` (or a modified instance). Returning `None` sets the model to `None`.

**FIX:**

```python
@model_validator(mode="after")
def check_dates(self) -> Self:
    if self.end < self.start:
        raise ValueError("end must be after start")
    return self   # always return self
```

---

## 11. Technologies & Tools

| Tool | Role | Notes |
|------|------|-------|
| `pydantic` (v2.x) | Core validation / serialization | Requires `pydantic-core` (Rust) |
| `pydantic-core` | Compiled Rust validation engine | Installed automatically as dependency |
| `pydantic-settings` | BaseSettings, env/file config | Separate package since v2 |
| `pydantic[email]` | EmailStr type | Requires `email-validator` |
| `fastapi` | Web framework, uses Pydantic natively | FastAPI 0.100+ requires Pydantic v2 |
| `mypy` + `pydantic mypy plugin` | Static type checking | Plugin in `mypy.ini`: `[mypy] plugins = pydantic.mypy` |
| `pyright` | Static type checking | Works out of the box; no plugin needed |
| `pytest` + `pydantic` | Testing validation logic | Test `ValidationError.errors()` structure |
| `hypothesis` + `hypothesis-pydantic` | Property-based testing | Auto-generate valid/invalid inputs from schema |

### mypy configuration

```ini
# mypy.ini
[mypy]
plugins = pydantic.mypy

[pydantic-mypy]
init_forbid_extra = True
init_typed = True
warn_required_dynamic_aliases = True
```

---

## 12. Interview Questions with Answers

**Q1: What is pydantic-core and why is Pydantic v2 significantly faster than v1?**
Pydantic-core is a compiled extension module written in Rust (via PyO3) that implements the entire validation and serialization pipeline. In v1, validation was pure Python: every field check was a Python function call with associated bytecode overhead, attribute lookups, and GC pressure. In v2, the schema is compiled to a `SchemaValidator` Rust struct at class-definition time, and validation calls drop into native code. The result is 5–50x faster throughput (e.g., ~312 k/s in v1 vs ~5.6 M/s in v2 for simple flat models). Speedup is smaller (~5x) for models with many Python `@field_validator` callbacks because each callback crosses the Python/Rust boundary.

**Q2: What is the difference between `@field_validator` modes `"before"`, `"after"`, and `"wrap"`?**
`mode="before"` runs before type coercion and receives the raw input; use it for normalization (strip, lowercase). `mode="after"` runs after coercion and receives the typed Python value; use it for business logic (age >= 18). `mode="wrap"` receives the raw input plus a `handler` callable that invokes the rest of the pipeline; use it when you need to decide whether to call the default pipeline, replace it, or post-process its result. `"wrap"` is the most powerful but also the most complex to reason about.

**Q3: How does `from_attributes=True` work and when do you need it?**
When `from_attributes=True` is set in `ConfigDict`, `model_validate(obj)` reads field values from `obj.field_name` attributes (using `getattr`) instead of treating the input as a dict. This is required when constructing Pydantic schemas from SQLAlchemy ORM instances, ORMs from other frameworks, or any Python object that exposes data as attributes rather than as a mapping. Without this flag, passing an ORM row to `model_validate` raises a `ValidationError` because the row object is not subscriptable.

**Q4: How do you perform cross-field validation — for example, ensuring `end_date > start_date`?**
Use `@model_validator(mode="after")`. This runs after all individual field validators have passed and gives you access to `self` (the partially constructed model). Raise `ValueError` if the constraint is violated. Do not use `@field_validator` for cross-field logic because validators for one field cannot reliably access another field's final validated value.

**Q5: How do nested Pydantic models work during validation and serialization?**
Nested models are declared as field types directly: `address: Address`. During validation, the nested dict is automatically passed to `Address.__init__` and validated recursively. If validation fails in the nested model, the error path in `ValidationError` includes the nested field name (e.g., `address.city`). During serialization, `model_dump()` recursively converts nested models to dicts; `model_dump_json()` serializes the entire tree to JSON in one Rust call.

**Q6: How do you create reusable field constraints in Pydantic v2?**
Use `Annotated[T, Field(...)]` to define a named type alias: `PositiveInt = Annotated[int, Field(gt=0)]`. This type can be used as a field annotation in any model without subclassing. Multiple constraints can be stacked: `Annotated[str, Field(min_length=1, max_length=100, pattern=r"^[a-z]+$")]`. This is the idiomatic v2 approach; do not subclass `int` or `str` just to add constraints.

**Q7: How does Pydantic v2 integrate with FastAPI, and what does FastAPI do with Pydantic models?**
FastAPI uses Pydantic v2 for three things: (1) request body parsing — a `BaseModel` parameter in a route function causes FastAPI to parse the JSON body and validate it via `model_validate`; (2) response serialization — a `response_model=SomeModel` annotation causes FastAPI to call `model_dump()` on the return value and filter/coerce fields; (3) OpenAPI schema generation — FastAPI calls `SomeModel.model_json_schema()` to generate the JSON Schema for the Swagger UI. FastAPI 0.100+ requires Pydantic v2; older versions used v1.

**Q8: What is a discriminated union and why is it more efficient than a plain Union?**
A discriminated union uses a `Literal` field (the discriminator) as a fast lookup key to select the correct union branch before attempting full validation. With a plain `Union[A, B, C]`, Pydantic tries each branch in order until one succeeds — O(n) attempts. With `Annotated[Union[A, B, C], Field(discriminator="type")]`, Pydantic reads the discriminator value and maps it directly to the correct model in O(1). This also produces clearer validation errors: instead of "none of the union variants matched", you get "type='unknown_value' is not valid".

**Q9: How do you exclude fields from serialization output?**
Three approaches: (1) `model_dump(exclude={"field_name"})` — runtime exclusion per call; (2) `model_dump(exclude_none=True)` — omit all `None` fields; (3) `model_dump(exclude_unset=True)` — omit fields not explicitly set (useful for PATCH endpoints); (4) annotate the field with `Field(exclude=True)` — permanently excluded from all `model_dump` calls; (5) use `@field_serializer` to return `None` and combine with `exclude_none=True` for conditional exclusion.

**Q10: How do you validate environment variables with BaseSettings?**
Inherit from `pydantic_settings.BaseSettings` instead of `BaseModel`. Declare fields with their expected types and defaults. Pydantic reads matching environment variable names (case-insensitive by default). Set `model_config = SettingsConfigDict(env_file=".env")` to also read from a `.env` file. Use `env_nested_delimiter="__"` to populate nested models from env vars like `DB__HOST`. Use `SecretStr` for sensitive values — the secret is never exposed in `__repr__` or `model_dump()` unless `.get_secret_value()` is called explicitly.

**Q11: How do you write a custom type in Pydantic v2?**
Implement `__get_pydantic_core_schema__` as a classmethod on your type. This method receives the source type and a handler, and must return a `core_schema.CoreSchema` instance (from `pydantic_core`). Use `core_schema.no_info_plain_validator_function(cls._validate)` for a simple validate-and-return pattern. The validator is called with the raw input and must return an instance of the custom type or raise `ValueError`. Add a `serialization` key to the schema to control output format.

**Q12: What does `model_config = ConfigDict(validate_default=True)` do?**
By default, Pydantic v2 does NOT run validators on fields that use their default value. Setting `validate_default=True` forces validators to run even when the default is used. This is important when your defaults are computed values that might fail validation (e.g., a default derived from an environment variable) or when your `@field_validator` has side effects that must always execute.

**Q13: How do you handle optional fields vs fields with a default of `None`?**
`Optional[str]` is equivalent to `str | None` — it changes the type but does NOT make the field optional in the sense of having a default. `name: Optional[str]` still requires `name` to be present in the input (as `None` or a string). `name: Optional[str] = None` makes the field optional with a `None` default — it can be omitted from input entirely. Use `model_dump(exclude_none=True)` to suppress `None` fields in output.

**Q14: How do you use `TypeAdapter` and when is it preferable to a full `BaseModel`?**
`TypeAdapter` validates arbitrary types without defining a model class: `TypeAdapter(list[int]).validate_python(["1","2"])` returns `[1, 2]`. Use it when you need to validate a single value, a generic container (`list[MyModel]`), or a union type without the overhead of a full model class definition. It also exposes `validate_json`, `dump_python`, and `json_schema` methods matching the `BaseModel` API.

**Q15: What is `model_dump(mode="json")` vs `model_dump_json()`?**
`model_dump(mode="json")` returns a Python `dict` where all values have been converted to JSON-compatible types (e.g., `datetime` → ISO string, `UUID` → string). `model_dump_json()` returns a `bytes`/`str` JSON string directly using the Rust serializer, which is faster. Use `model_dump(mode="json")` when you need a dict for further manipulation before serializing; use `model_dump_json()` for the fastest direct JSON output.

**Q16: How does `ConfigDict(frozen=True)` affect a model?**
Setting `frozen=True` makes the model immutable after creation: any attempt to set an attribute raises a `ValidationError`. It also makes the model hashable (implements `__hash__`), so instances can be used as dict keys or set members. Internally, Pydantic sets `__setattr__` and `__delattr__` to raise exceptions. Use `frozen=True` for value objects — DTOs that are passed around but never modified.

**Q17: How do you handle v1 → v2 migration for a large codebase?**
Pydantic provides a `PYDANTIC_V1_COMPAT` compatibility layer in v2 — many v1 patterns still work but emit deprecation warnings. A safe migration path: (1) install Pydantic v2, (2) run your test suite and collect all `PydanticDeprecatedSince20` warnings, (3) replace `@validator` with `@field_validator`, `class Config` with `ConfigDict`, `.dict()` with `.model_dump()`, `.json()` with `.model_dump_json()`, `parse_obj` with `model_validate`, (4) replace `__fields__` accesses with `model_fields`. The `bump-pydantic` CLI tool automates most of these rewrites.

**Q18: What is the purpose of `model_rebuild()` and when is it needed?**
`model_rebuild()` re-compiles the Pydantic core schema for a model. It is needed when a model has forward references (`from __future__ import annotations` or string annotations) that were not resolved at class definition time, or when you add fields dynamically after class creation. In practice, you call `MyModel.model_rebuild()` at the end of a module after all referenced types are defined. FastAPI calls it automatically during app startup for models registered as request/response bodies.

---

## 13. Best Practices

1. **Use `Annotated` types for constraints, not subclasses.** `PositiveInt = Annotated[int, Field(gt=0)]` is composable and readable. Subclassing `int` to add constraints creates a new type that breaks isinstance checks and adds inheritance complexity.

2. **Declare `@classmethod` on all field validators.** Forgetting `@classmethod` raises `PydanticUserError` at class definition time, not at runtime — easy to catch in tests.

3. **Return `self` from `@model_validator(mode="after")`.** Annotate the return type as `Self` to make this explicit and catch missing returns with mypy/pyright.

4. **Use `model_dump(exclude_unset=True)` for PATCH endpoints.** This ensures that only fields explicitly provided by the client are included in the update dict, preventing accidental overwrites of unrelated fields.

5. **Use `SecretStr` for passwords, API keys, and tokens in BaseSettings.** It prevents accidental logging in tracebacks, `repr()` output, and serialization.

6. **Prefer `model_validate(obj)` over `Model(**obj.__dict__)` for ORM rows.** The former respects `from_attributes=True`, handles lazy-loaded attributes, and produces proper validation errors. The latter can bypass validation.

7. **Set `validate_default=True` in `ConfigDict` when validators have side effects.** Otherwise validators are silently skipped for default values.

8. **Use discriminated unions for polymorphic event/message schemas.** Name the discriminator field consistently (`type`, `kind`, `event_type`) across all schemas in a service.

9. **Test `ValidationError.errors()` not just the exception message.** The `errors()` method returns a list of dicts with `loc`, `msg`, `type`, and `input` — assert on these fields in unit tests for precise error behavior.

10. **Run `model_rebuild()` explicitly in modules with forward references.** Do not rely on implicit resolution. Call it at module load time and add a test that instantiates every model in the module to catch unresolved references in CI.

11. **Pin `pydantic>=2.0,<3.0` in `pyproject.toml`.** Major Pydantic versions have breaking API changes. Do not use `pydantic>=2.0` open-ended.

12. **Use `model_dump(mode="json")` to prepare data for non-Pydantic serializers.** This ensures `datetime`, `UUID`, `Decimal`, and other types are converted to JSON-safe Python types before passing to `json.dumps` or a third-party serializer.

---

## 14. Case Study

### Modeling a Multi-Layer API Request/Response with Custom Validators

**Context**: An e-commerce API needs to accept order creation requests. Each order contains a list of items, a customer email, and optionally a discount code. The response maps directly to a SQLAlchemy ORM model. A PATCH endpoint supports partial updates.

---

#### BROKEN: v1 code with multiple issues

```python
# ---- BROKEN: v1 patterns that fail or warn in v2 ----
from pydantic import BaseModel, validator
from typing import List, Optional

class OrderItem(BaseModel):
    product_id: int
    quantity: int
    unit_price: float

class OrderCreate(BaseModel):
    customer_email: str
    items: List[OrderItem]
    discount_code: Optional[str]

    @validator("customer_email", pre=True)   # BROKEN: v1 validator
    def normalize_email(cls, v):
        return v.lower()                     # missing strip()

    class Config:                            # BROKEN: v1 inner Config
        orm_mode = True                      # BROKEN: renamed in v2

# Serialization
order = OrderCreate(
    customer_email="ALICE@EXAMPLE.COM",
    items=[{"product_id": 1, "quantity": 2, "unit_price": 9.99}],
    discount_code=None,
)
data = order.dict()   # BROKEN: deprecated, DeprecationWarning in v2
json_str = order.json()  # BROKEN: deprecated

# Accessing field metadata
for name, field in OrderCreate.__fields__.items():  # BROKEN: v1 __fields__ API
    print(name, field.type_)
```

---

#### FIX: v2 idiomatic code

```python
# ---- FIX: v2 idiomatic implementation ----
from __future__ import annotations
from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)
from typing import Optional, Annotated
from decimal import Decimal
from typing import Self

# Reusable constrained types
PositiveInt   = Annotated[int,     Field(gt=0, description="Must be positive")]
NonNegDecimal = Annotated[Decimal, Field(ge=Decimal("0.00"))]
EmailStr      = Annotated[str,     Field(min_length=3, max_length=254)]

class OrderItem(BaseModel):
    product_id: PositiveInt
    quantity:   PositiveInt
    unit_price: NonNegDecimal

    @property
    def line_total(self) -> Decimal:
        return self.quantity * self.unit_price


class OrderCreate(BaseModel):
    model_config = ConfigDict(
        from_attributes=True,      # ORM mapping
        str_strip_whitespace=True, # auto-strip all str fields
        validate_default=True,
    )

    customer_email: EmailStr
    items: list[OrderItem] = Field(min_length=1, description="At least one item required")
    discount_code: Optional[str] = None
    notes: Optional[str] = Field(default=None, max_length=500)

    @field_validator("customer_email", mode="before")
    @classmethod
    def normalize_email(cls, v: object) -> str:
        if isinstance(v, str):
            return v.strip().lower()
        return v

    @field_validator("discount_code", mode="before")
    @classmethod
    def normalize_discount(cls, v: object) -> object:
        if isinstance(v, str):
            return v.strip().upper() or None  # empty string -> None
        return v

    @model_validator(mode="after")
    def validate_total(self) -> Self:
        total = sum(item.line_total for item in self.items)
        if total < Decimal("0.01"):
            raise ValueError(
                f"Order total must be at least $0.01, got ${total:.2f}"
            )
        return self

    @property
    def total_price(self) -> Decimal:
        return sum(item.line_total for item in self.items)


class OrderResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    customer_email: str
    total_price: Decimal
    discount_code: Optional[str] = None
    status: str


class OrderUpdate(BaseModel):
    """Partial update — only provided fields are applied."""
    discount_code: Optional[str] = None
    notes: Optional[str] = None
    status: Optional[str] = None


# --- Usage ---

# Validation (request body parsing — FastAPI does this automatically)
payload = {
    "customer_email": "  ALICE@EXAMPLE.COM  ",
    "items": [
        {"product_id": 1, "quantity": 2, "unit_price": "9.99"},
        {"product_id": 2, "quantity": 1, "unit_price": "4.50"},
    ],
    "discount_code": "  summer24  ",
}
order = OrderCreate.model_validate(payload)
print(order.customer_email)    # alice@example.com
print(order.discount_code)     # SUMMER24
print(order.total_price)       # 24.48

# Serialization for API response (exclude None fields)
response_data = order.model_dump(exclude_none=True)
# Notes field omitted because it is None

# Partial update (PATCH endpoint)
patch_payload = {"status": "confirmed"}
update = OrderUpdate(**patch_payload)
patch_fields = update.model_dump(exclude_unset=True)
print(patch_fields)   # {'status': 'confirmed'}  — discount_code and notes NOT included

# ORM round-trip
# orm_row = session.get(OrderRow, order_id)
# response = OrderResponse.model_validate(orm_row)  # reads attributes via from_attributes=True

# Accessing field metadata (v2 API)
for name, field_info in OrderCreate.model_fields.items():
    print(f"{name}: annotation={field_info.annotation}, default={field_info.default}")

# JSON output (Rust serializer, fastest path)
json_bytes = order.model_dump_json(exclude_none=True)
```

**Key design decisions in the fix:**

1. `model_config = ConfigDict(from_attributes=True, str_strip_whitespace=True)` replaces the inner `class Config` and handles basic normalization globally, reducing per-field validator boilerplate.
2. `@field_validator("customer_email", mode="before")` normalizes before coercion; `@model_validator(mode="after")` validates the cross-field total constraint after all fields are set.
3. `Annotated` reusable types (`PositiveInt`, `NonNegDecimal`) enforce constraints at the type level, not the model level, making them shareable across schemas.
4. `OrderUpdate` uses `model_dump(exclude_unset=True)` for safe PATCH semantics — only explicitly provided fields are written to the database.
5. `OrderResponse` uses `from_attributes=True` for zero-boilerplate ORM-to-schema mapping.

---

*Cross-references:*
- `../../the_type_system_and_typing/README.md` — `TypeVar`, `Protocol`, `Annotated`, `Self` used throughout this module.
- `../dependency_injection_in_fastapi/README.md` — how Pydantic models are declared as request body parameters and how FastAPI calls `model_validate` automatically.
