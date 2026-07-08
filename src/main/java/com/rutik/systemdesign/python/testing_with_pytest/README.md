# Testing with pytest

## 1. Concept Overview

pytest is a mature, full-featured testing framework for Python that replaces the standard `unittest` module in most production codebases. It discovers tests automatically, supports parameterization, provides a rich fixture system for dependency injection, and integrates with the entire testing ecosystem — async runtimes, property-based testing, coverage reporting, mocking, and CI pipelines.

Core capabilities:

- **Fixtures** — reusable setup/teardown objects injected by name into test functions
- **Parametrize** — run one test body over many inputs without code duplication
- **Plugins** — `pytest-asyncio`, `pytest-cov`, `pytest-xdist`, `hypothesis`, `respx`, `freezegun`
- **Marks** — metadata tags (`skip`, `xfail`, `asyncio`, custom marks) applied to tests
- **Test doubles** — integration with `unittest.mock` for isolating units under test
- **conftest.py** — module-scoped fixture sharing without explicit imports

---

## 2. Intuition

> pytest is a smart test runner that acts like a dependency injection container: you declare what a test needs by naming its parameters, and pytest resolves the entire dependency graph before calling your function.

**Mental model:** Think of a `conftest.py` as a recipe book. Each fixture is a recipe. When pytest runs a test, it reads the ingredient list (parameter names), finds recipes in the recipe book, prepares them in the right order (respecting scopes and dependencies), hands them to the test, and then cleans up — in reverse order — after the test finishes.

**Why it matters:** Without a structured test framework, tests become fragile scripts that share global state, repeat setup code, and break silently when dependencies change. pytest enforces clean separation of concerns: the test body expresses *what* is being verified; fixtures express *how* the environment is prepared.

**Key insight:** Fixture scope determines the unit of reuse. Session scope pays setup cost once for the entire test run — ideal for expensive resources like database connections or compiled models. Function scope pays setup cost per test — ideal for anything that carries mutable state between tests.

---

## 3. Core Principles

**1. Convention over configuration.** pytest discovers `test_*.py` files and functions named `test_*` without any registration. No base classes required.

**2. Fixtures are dependency injection.** A test declares what it needs by naming parameters that match registered fixtures. pytest resolves the graph transitively — fixtures can depend on other fixtures.

**3. Explicit teardown via yield.** A fixture that uses `yield` treats everything before the yield as setup and everything after as teardown. Teardown runs even if the test fails.

**4. Scope controls reuse granularity.** `function` (default), `class`, `module`, `package`, `session` — narrower scopes are safer; wider scopes are faster.

**5. Marks are metadata.** `@pytest.mark.skip`, `@pytest.mark.xfail`, `@pytest.mark.asyncio`, `@pytest.mark.slow` — marks can be filtered on the CLI (`-m "not slow"`).

**6. Isolation by default.** `monkeypatch` changes are automatically reverted after each test. `tmp_path` creates per-test temporary directories. The framework pushes you toward stateless tests.

**7. Plugins extend, not replace.** The pytest plugin system uses well-defined hooks. `pytest-asyncio`, `hypothesis`, `pytest-cov` all slot in without changing the test-writing model.

---

## 4. Types / Architectures / Strategies

### Test Double Taxonomy

| Double | Behavior | Use When |
|--------|----------|----------|
| **Stub** | Returns fixed canned values | You need controlled inputs from a dependency |
| **Mock** | Verifies interactions (calls, args) | You need to assert the unit *called* something correctly |
| **Fake** | Working simplified implementation | You need realistic behavior without the real resource (e.g., in-memory DB) |
| **Spy** | Passes calls through; records calls | You want to observe a real object without fully replacing it |

### Test Scope Hierarchy

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    A("session<br/>once per run") --> B("module<br/>once per file")
    B --> C("class<br/>once per class")
    C --> D("function<br/>default · per test")

    class A frozen
    class B,C base
    class D train
```

Scope narrows top-to-bottom — `session` pays setup cost once for the whole run, while `function` (the default) recreates the fixture for every test; narrower scope buys isolation, wider scope buys speed.

### Testing Strategies

| Strategy | Tool | Goal |
|----------|------|------|
| Unit testing | pytest + `unittest.mock` | Verify a single class or function in isolation |
| Integration testing | pytest + real DB (Docker) | Verify component boundaries: service ↔ DB, service ↔ cache |
| Contract testing | pytest + `respx` | Verify HTTP client matches provider's API shape |
| Property-based | `hypothesis` | Find edge cases the developer did not anticipate |
| Async testing | `pytest-asyncio` | Test coroutines, FastAPI endpoints, background tasks |
| Mutation testing | `mutmut` | Confirm tests catch intentional code bugs |

---

## 5. Architecture Diagrams

### pytest Execution Pipeline

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    pinvoke(["pytest invoked"])

    subgraph Collect["Collect Phase"]
        direction TB
        c1["Discover<br/>conftest.py"]
        c2["Discover<br/>test_*.py files"]
        c3["Build fixture<br/>graph"]
        c4["Apply marks<br/>-k / -m filters"]
        c1 --> c2 --> c3 --> c4
    end

    subgraph Setup["Setup Phase<br/>per test"]
        direction TB
        s1["session-scoped<br/>fixtures"]
        s2["module-scoped<br/>fixtures"]
        s3["class-scoped<br/>fixtures"]
        s4["function-scoped<br/>fixtures"]
        s1 --> s2 --> s3 --> s4
    end

    subgraph Call["Call Phase"]
        direction TB
        r1["Run test<br/>function body"]
        r2["Capture stdout<br/>stderr / logs"]
        r1 --> r2
    end

    subgraph Teardown["Teardown Phase<br/>reverse order"]
        direction TB
        t1["function<br/>yield-cleanup"]
        t2["class<br/>yield-cleanup"]
        t3["module<br/>yield-cleanup"]
        t4["session<br/>yield-cleanup"]
        t1 --> t2 --> t3 --> t4
    end

    pinvoke --> Collect --> Setup --> Call --> Teardown

    class pinvoke io
    class c1,c2 req
    class c3,c4 mathOp
    class s1 frozen
    class s2,s3 base
    class s4 train
    class r1,r2 train
    class t1,t2,t3,t4 lossN
```

The four phases run in strict order for every test; Setup instantiates fixtures from widest scope to narrowest (session to function) and Teardown reverses that order, so a function-scoped fixture is always torn down before the session-scoped one it depends on.

### conftest.py Scope Resolution

```
project_root/
├── conftest.py          ← session-level shared fixtures
├── tests/
│   ├── conftest.py      ← test-package shared fixtures
│   ├── unit/
│   │   ├── conftest.py  ← unit-specific fixtures
│   │   └── test_foo.py
│   └── integration/
│       ├── conftest.py  ← integration-specific fixtures
│       └── test_bar.py
```

pytest walks up the directory tree to resolve fixture names. Fixtures defined closer to the test file take precedence over same-named fixtures higher up.

### Fixture Dependency Graph

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    dbc(["db_connection<br/>session scope"]) --> db(["db<br/>function scope"])
    db --> seeded(["seeded_db<br/>function scope"])
    client(["client<br/>function scope"]) --> test(["test_get_item"])
    seeded --> test

    class dbc frozen
    class db,seeded,client base
    class test train
```

pytest resolves fixture parameters transitively: `test_get_item` names `client` and `seeded_db`; `seeded_db` names `db`, which names the session-scoped `db_connection` — one dependency graph is built per test, then torn down in reverse (see §6.1 and §7).

---

## 6. How It Works — Detailed Mechanics

### 6.1 Fixtures and Scopes

```python
# conftest.py
import pytest
import psycopg2
from psycopg2.extensions import connection

# SESSION SCOPE: connect once for the entire test run.
# Use when the resource is expensive to create and tests do NOT mutate shared state.
@pytest.fixture(scope="session")
def db_connection() -> connection:
    conn = psycopg2.connect("postgresql://test:test@localhost:5432/testdb")
    yield conn
    conn.close()

# FUNCTION SCOPE: fresh transaction per test; always rolled back.
# Use when tests perform writes — guarantees isolation between tests.
@pytest.fixture(scope="function")
def db(db_connection: connection):
    db_connection.autocommit = False
    yield db_connection
    db_connection.rollback()

# MODULE SCOPE: one instance per test module (file).
@pytest.fixture(scope="module")
def expensive_model():
    from transformers import AutoTokenizer
    return AutoTokenizer.from_pretrained("bert-base-uncased")
```

```python
# test_users.py
def test_insert_user(db: connection) -> None:
    with db.cursor() as cur:
        cur.execute("INSERT INTO users (name) VALUES ('alice')")
        cur.execute("SELECT name FROM users WHERE name='alice'")
        row = cur.fetchone()
    assert row is not None
    assert row[0] == "alice"
    # db fixture rolls back after this test; the row disappears

def test_no_user_leakage(db: connection) -> None:
    with db.cursor() as cur:
        cur.execute("SELECT count(*) FROM users WHERE name='alice'")
        count = cur.fetchone()[0]
    assert count == 0  # rollback from previous test guarantees isolation
```

### 6.2 @pytest.mark.parametrize

```python
import pytest

# Basic parametrize
@pytest.mark.parametrize("x, expected", [
    (1, 1),
    (2, 4),
    (3, 9),
])
def test_square(x: int, expected: int) -> None:
    assert x ** 2 == expected

# Boundary condition parametrize — test ValueError for invalid inputs
@pytest.mark.parametrize("value, exc", [
    (0,  ValueError),
    (-1, ValueError),
    (1,  None),       # no exception expected
])
def test_sqrt(value: int, exc: type | None) -> None:
    import math
    if exc:
        with pytest.raises(exc):
            if value <= 0:
                raise ValueError(f"sqrt undefined for {value}")
    else:
        assert math.isclose(math.sqrt(value), 1.0)

# Cartesian product: two @parametrize decorators produce 2×3 = 6 test cases
@pytest.mark.parametrize("fmt", ["json", "csv"])
@pytest.mark.parametrize("rows", [0, 100, 10_000])
def test_export(fmt: str, rows: int) -> None:
    data = list(range(rows))
    # assert export(data, fmt) produces correct bytes ...
    pass

# Indirect parametrize — pass a key to a fixture, not a value
@pytest.fixture
def user(request: pytest.FixtureRequest):
    roles = {"admin": {"id": 1, "role": "admin"}, "viewer": {"id": 2, "role": "viewer"}}
    return roles[request.param]

@pytest.mark.parametrize("user", ["admin", "viewer"], indirect=True)
def test_role_display(user: dict) -> None:
    assert user["role"] in ("admin", "viewer")
```

### 6.3 monkeypatch

```python
# source module: app/config.py
import os

def get_api_key() -> str:
    return os.environ["API_KEY"]

def get_timeout() -> int:
    return int(os.environ.get("TIMEOUT", "30"))
```

```python
# test_config.py
import app.config as config

def test_get_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("API_KEY", "test-key-123")
    assert config.get_api_key() == "test-key-123"
    # after the test, API_KEY env var is restored to its original state

def test_missing_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("API_KEY", raising=False)  # safe even if not set
    with pytest.raises(KeyError):
        config.get_api_key()

def test_custom_function(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(config, "get_timeout", lambda: 5)
    assert config.get_timeout() == 5

def test_chdir(monkeypatch: pytest.MonkeyPatch, tmp_path) -> None:
    monkeypatch.chdir(tmp_path)
    import os
    assert os.getcwd() == str(tmp_path)
```

### 6.4 unittest.mock

```python
from unittest.mock import Mock, MagicMock, patch, AsyncMock, call
import pytest

# --- Stub: return a fixed value ---
def process_payment(gateway, amount: float) -> bool:
    return gateway.charge(amount)

def test_payment_stub() -> None:
    gateway_stub = Mock()
    gateway_stub.charge.return_value = True
    result = process_payment(gateway_stub, 99.99)
    assert result is True

# --- Mock: verify interactions ---
def test_payment_called_with_correct_amount() -> None:
    gateway_mock = Mock()
    gateway_mock.charge.return_value = True
    process_payment(gateway_mock, 49.95)
    gateway_mock.charge.assert_called_once_with(49.95)

# --- side_effect: sequence of return values or exceptions ---
def test_retry_logic() -> None:
    service = Mock()
    service.call.side_effect = [
        ConnectionError("timeout"),
        ConnectionError("timeout"),
        {"status": "ok"},
    ]
    # first two calls raise; third succeeds
    with pytest.raises(ConnectionError):
        service.call()
    with pytest.raises(ConnectionError):
        service.call()
    result = service.call()
    assert result["status"] == "ok"

# --- MagicMock: supports dunder methods ---
def test_magic_mock_context_manager() -> None:
    fake_file = MagicMock()
    fake_file.__enter__.return_value = fake_file
    fake_file.__exit__.return_value = False
    fake_file.read.return_value = "hello"

    with fake_file as f:
        content = f.read()
    assert content == "hello"

# --- patch as decorator (patches in the module under test) ---
@patch("app.services.payment.requests.post")
def test_payment_http_call(mock_post: Mock) -> None:
    mock_post.return_value.status_code = 200
    mock_post.return_value.json.return_value = {"charged": True}
    # ... call service function ...
    assert mock_post.called

# --- patch.object: patch a method on a specific instance ---
class EmailService:
    def send(self, to: str, subject: str) -> None:
        ...  # real SMTP call

def notify_user(service: EmailService, address: str) -> None:
    service.send(address, "Welcome!")

def test_notify_user() -> None:
    svc = EmailService()
    with patch.object(svc, "send") as mock_send:
        notify_user(svc, "user@example.com")
    mock_send.assert_called_once_with("user@example.com", "Welcome!")

# --- AsyncMock for async functions ---
import asyncio

async def fetch_user(client, user_id: int) -> dict:
    return await client.get(f"/users/{user_id}")

@pytest.mark.asyncio
async def test_fetch_user_async() -> None:
    async_client = AsyncMock()
    async_client.get.return_value = {"id": 1, "name": "alice"}
    result = await fetch_user(async_client, 1)
    assert result["name"] == "alice"
    async_client.get.assert_awaited_once_with("/users/1")
```

### 6.5 pytest-asyncio

```python
# pytest.ini or pyproject.toml
# [pytest]
# asyncio_mode = auto      ← auto-detects async test functions; no @mark needed

import pytest
import httpx
from httpx import AsyncClient
from app.main import app  # FastAPI application

# Async fixture
@pytest.fixture
async def client() -> AsyncClient:
    async with AsyncClient(app=app, base_url="http://test") as ac:
        yield ac

# Async test
@pytest.mark.asyncio
async def test_health_endpoint(client: AsyncClient) -> None:
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}

# Testing a FastAPI dependency override
from app.dependencies import get_current_user

@pytest.fixture
async def auth_client(client: AsyncClient) -> AsyncClient:
    app.dependency_overrides[get_current_user] = lambda: {"id": 42, "role": "admin"}
    yield client
    app.dependency_overrides.clear()

@pytest.mark.asyncio
async def test_protected_route(auth_client: AsyncClient) -> None:
    response = await auth_client.get("/admin/dashboard")
    assert response.status_code == 200
```

### 6.6 hypothesis for Property-Based Testing

```python
from hypothesis import given, settings, example
from hypothesis import strategies as st

# Property: sorting is idempotent
@given(st.lists(st.integers()))
def test_sort_idempotent(xs: list[int]) -> None:
    assert sorted(sorted(xs)) == sorted(xs)

# Property: reverse of reverse is identity
@given(st.lists(st.text()))
def test_double_reverse(xs: list[str]) -> None:
    assert list(reversed(list(reversed(xs)))) == xs

# Always test a known edge case with @example
@given(st.integers(min_value=1, max_value=10_000))
@example(1)           # min boundary
@example(10_000)      # max boundary
@settings(max_examples=500)
def test_factorial_positive(n: int) -> None:
    import math
    result = math.factorial(n)
    assert result >= 1

# Property for a stateful system: deposit then withdraw returns original balance
@given(
    balance=st.floats(min_value=0.01, max_value=1_000_000, allow_nan=False),
    amount=st.floats(min_value=0.01, max_value=1_000_000, allow_nan=False),
)
def test_balance_roundtrip(balance: float, amount: float) -> None:
    from app.wallet import Wallet
    w = Wallet(balance=balance)
    if amount <= balance:
        w.deposit(amount)
        w.withdraw(amount)
        assert abs(w.balance - balance) < 1e-9
```

### 6.7 capsys and caplog

```python
import logging
import pytest

def greet(name: str) -> None:
    print(f"Hello, {name}")

def compute(x: int) -> int:
    logging.getLogger("app").info("computing for %d", x)
    return x * 2

def test_stdout_capture(capsys: pytest.CaptureFixture) -> None:
    greet("world")
    captured = capsys.readouterr()
    assert captured.out == "Hello, world\n"
    assert captured.err == ""

def test_log_capture(caplog: pytest.LogCaptureFixture) -> None:
    with caplog.at_level(logging.INFO, logger="app"):
        compute(5)
    assert "computing for 5" in caplog.text
    assert caplog.records[0].levelname == "INFO"
```

---

## 7. Real-World Examples

### FastAPI endpoint integration test

```python
# tests/integration/test_items_api.py
import pytest
from httpx import AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

@pytest.fixture
async def seeded_db(db: AsyncSession) -> None:
    from app.models import Item
    db.add(Item(id=1, name="widget", price=9.99))
    await db.commit()

@pytest.mark.asyncio
async def test_get_item(client: AsyncClient, seeded_db: None) -> None:
    response = await client.get("/items/1")
    assert response.status_code == 200
    payload = response.json()
    assert payload["name"] == "widget"
    assert payload["price"] == pytest.approx(9.99, rel=1e-3)
```

### Background task testing

```python
from unittest.mock import patch, AsyncMock

@pytest.mark.asyncio
async def test_background_email_sent(client: AsyncClient) -> None:
    with patch("app.tasks.send_welcome_email", new_callable=AsyncMock) as mock_email:
        response = await client.post("/register", json={"email": "new@example.com"})
    assert response.status_code == 201
    mock_email.assert_awaited_once_with("new@example.com")
```

### Rate limiter under test with parametrize

```python
@pytest.mark.parametrize("requests_count, expected_status", [
    (5,  200),   # under limit
    (10, 200),   # at limit
    (11, 429),   # over limit
])
@pytest.mark.asyncio
async def test_rate_limit(
    client: AsyncClient,
    requests_count: int,
    expected_status: int,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr("app.limiter.LIMIT", 10)
    for i in range(requests_count - 1):
        await client.get("/resource")
    response = await client.get("/resource")
    assert response.status_code == expected_status
```

---

## 8. Tradeoffs

| Concern | pytest | unittest | Recommendation |
|---------|--------|----------|----------------|
| Boilerplate | Minimal — plain functions | High — classes mandatory | pytest for new code |
| Fixture DI | First-class, composable | Manual setUp/tearDown | pytest fixtures scale better |
| Parametrize | Built-in, expressive | `subTest` — verbose | pytest parametrize wins |
| Async support | `pytest-asyncio` plugin | `IsolatedAsyncioTestCase` | pytest-asyncio for FastAPI |
| IDE integration | PyCharm, VS Code: excellent | Excellent | Tie |
| Discovery | Automatic | Automatic | Tie |
| Migration cost | Low (can run unittest tests) | N/A | pytest is backward-compatible |

| Scope | Setup Cost | Isolation | Use For |
|-------|-----------|-----------|---------|
| `function` | Per-test | Perfect | Mutable state, DB writes |
| `class` | Per-class | Good | Grouped related tests |
| `module` | Per-file | Moderate | Read-only resources |
| `session` | Once | Low | Expensive read-only: model load, DB schema |

---

## 9. When to Use / When NOT to Use

**Use pytest when:**

- Starting a new Python project — it is the de-facto standard
- Testing FastAPI or async code — `pytest-asyncio` integrates cleanly
- You need parametrize over many boundary conditions
- You want property-based testing via `hypothesis`
- You require coverage gating in CI (`pytest-cov --cov-fail-under=80`)
- You want parallel test execution (`pytest-xdist -n auto`)

**Do NOT use session-scoped fixtures when:**

- Tests write data to a shared resource. Writes from test A leak into test B.
- The fixture carries connection state that becomes stale between tests.

**Do NOT mock what you do not own.** Avoid mocking third-party library internals (e.g., SQLAlchemy's `Session`). Use fakes (in-memory SQLite, `fakeredis`) instead. Mocking internals couples tests to implementation details.

**Do NOT use `unittest.mock.patch` as a default reflex.** If a function is hard to patch in a test, the real problem is a design issue — the dependency should be injected, not monkey-patched at the module level.

---

## 10. Common Pitfalls

### Pitfall 1: Session-Scoped Fixture with Mutable State (BROKEN → FIX)

```python
# BROKEN: session-scoped fixture that modifies shared DB state.
# test_a inserts a row; test_b sees that row unexpectedly.
@pytest.fixture(scope="session")
def db(engine):
    with engine.connect() as conn:
        yield conn
        # no rollback — writes persist across ALL tests in the session
```

```python
# FIX: use function scope with transactional rollback.
@pytest.fixture(scope="session")
def db_engine():
    engine = create_engine("postgresql://test:test@localhost/testdb")
    yield engine
    engine.dispose()

@pytest.fixture(scope="function")
def db(db_engine):
    with db_engine.begin() as conn:   # begin() starts a transaction
        yield conn
        conn.rollback()               # all writes are undone after the test
        # if the test raised an exception, rollback still fires (yield fixture guarantee)
```

**Why it matters:** With session scope, insert in `test_a` is visible to `test_b`. Debugging this class of failure takes hours because the tests pass when run individually but fail in suite order.

---

### Pitfall 2: assert_called_with Checks Only the Last Call (BROKEN → FIX)

```python
# BROKEN: the assertion does NOT raise, even though the first argument was wrong.
def test_email_sent_broken() -> None:
    mock_sender = Mock()
    mock_sender("first@example.com")
    mock_sender("second@example.com")

    # assert_called_with checks the MOST RECENT call only.
    # This passes silently even if first@example.com was wrong.
    mock_sender.assert_called_with("second@example.com")  # True, but misleading
```

```python
# FIX: check call_args_list when multiple calls matter.
def test_email_sent_all_calls() -> None:
    mock_sender = Mock()
    mock_sender("first@example.com")
    mock_sender("second@example.com")

    assert mock_sender.call_count == 2
    assert mock_sender.call_args_list == [
        call("first@example.com"),
        call("second@example.com"),
    ]

# ALTERNATIVE FIX: assert_called_once_with catches duplicate unexpected calls.
def test_email_sent_once() -> None:
    mock_sender = Mock()
    mock_sender("only@example.com")
    mock_sender.assert_called_once_with("only@example.com")
    # Raises AssertionError if called zero times OR more than once.
```

**Why it matters:** `assert_called_with` is the most common mock assertion pitfall. It silently passes when a mock is called multiple times with wrong early arguments. `assert_called_once_with` is safer for single-call contracts; `call_args_list` is authoritative for multi-call contracts.

---

### Pitfall 3: Forgetting asyncio_mode or Mixing Sync and Async Fixtures

Async fixtures passed to sync test functions raise `TypeError: object AsyncGenerator can't be used in 'await' expression` at collection time. Always mark async tests with `@pytest.mark.asyncio` or set `asyncio_mode = auto` globally in `pyproject.toml`.

### Pitfall 4: Patching the Wrong Namespace

```python
# BROKEN: patches the original location, not where the test subject imported it
@patch("email_lib.send")     # wrong — test subject already imported send
def test_send(mock_send): ...

# FIX: patch where the name is looked up
@patch("app.notifications.send")  # correct — patch in the module that uses it
def test_send(mock_send): ...
```

### Pitfall 5: hypothesis and Database Side Effects

`hypothesis` runs each `@given` test up to `max_examples` times. If a test writes to a database inside a `@given` block without rollback, it accumulates rows across iterations. Always use function-scoped transactional fixtures, or use an in-memory fake.

---

## 11. Technologies & Tools

| Tool | Purpose | Install |
|------|---------|---------|
| `pytest` | Core test runner and fixture engine | `pip install pytest` |
| `pytest-asyncio` | Async test and fixture support | `pip install pytest-asyncio` |
| `pytest-cov` | Coverage reporting (integrates with `coverage.py`) | `pip install pytest-cov` |
| `pytest-xdist` | Parallel test execution (`-n auto`) | `pip install pytest-xdist` |
| `hypothesis` | Property-based / fuzz testing | `pip install hypothesis` |
| `respx` | Mock `httpx` clients at the transport level | `pip install respx` |
| `fakeredis` | In-memory Redis fake for tests | `pip install fakeredis` |
| `freezegun` | Freeze / travel time in tests | `pip install freezegun` |
| `factory_boy` | Test object factories (replaces hand-rolled fixtures) | `pip install factory-boy` |
| `pytest-mock` | Thin wrapper around `unittest.mock` — adds `mocker` fixture | `pip install pytest-mock` |

### pytest.ini / pyproject.toml Configuration

```toml
# pyproject.toml
[tool.pytest.ini_options]
asyncio_mode   = "auto"
addopts        = "-ra -q --tb=short --cov=app --cov-report=term-missing"
testpaths      = ["tests"]
filterwarnings = ["error"]           # treat warnings as errors in CI

[tool.coverage.run]
source  = ["app"]
omit    = ["app/migrations/*", "app/tests/*"]

[tool.coverage.report]
fail_under = 80
```

---

## 12. Interview Questions with Answers

**Q1: What is the difference between `scope="function"` and `scope="session"` for a fixture, and when would you choose each?**

`scope="function"` creates a new fixture instance before each test and tears it down after; `scope="session"` creates the instance once for the entire test run and tears it down after the last test. Choose `function` scope for any fixture that holds mutable state — database connections that receive writes, mock objects, temporary files with content. Choose `session` scope for expensive read-only resources: loading a large ML model, establishing a DB schema, compiling a regex corpus. A practical rule: start with function scope; only widen to session when profiling shows setup cost dominates test runtime.

**Q2: How does `conftest.py` work, and why do you not need to import fixtures from it?**

pytest automatically discovers all `conftest.py` files in the directory tree from the rootdir down to the test file. When a test function declares a parameter name, pytest searches for a matching fixture starting from the closest `conftest.py` and walking upward to the rootdir `conftest.py`. Because discovery is automatic, fixtures in `conftest.py` are available to all tests at or below that directory level without any import. This mechanism enables shared fixtures (e.g., a DB connection, an HTTP client) to be defined once and used everywhere without coupling test modules to each other.

**Q3: What is the difference between `Mock`, `MagicMock`, and `AsyncMock`?**

`Mock` is the base object that records attribute access and method calls. `MagicMock` extends `Mock` by pre-configuring magic (dunder) methods — `__enter__`, `__exit__`, `__len__`, `__iter__`, etc. — making it usable as a context manager or iterable without additional setup. `AsyncMock` makes the object awaitable; calling it returns a coroutine, and `assert_awaited_once_with` checks awaited calls. Use `MagicMock` as the default for synchronous code; use `AsyncMock` for async code. Use `Mock` only when you explicitly do not want dunder support (to catch accidental iteration or context-manager usage).

**Q4: Explain `monkeypatch` and how it differs from `unittest.mock.patch`.**

`monkeypatch` is a pytest fixture that provides methods to temporarily modify attributes, environment variables, dictionary entries, and the working directory. Changes are automatically reverted after the test without a context manager or decorator. `unittest.mock.patch` is a context manager or decorator that replaces a named attribute with a `Mock` for the duration of a `with` block or function call. The key difference: `monkeypatch` is more Pythonic for simple attribute replacements and environment variables; `patch` integrates with the `Mock` ecosystem and is better for full mock objects where you need to verify call counts and arguments.

**Q5: How does `@pytest.mark.parametrize` with `indirect=True` work?**

When `indirect=True` is passed, pytest treats the parametrize value not as a direct argument to the test but as the `param` attribute of a `FixtureRequest` object passed to the named fixture. The fixture can use `request.param` to return different objects based on the parameter. This enables parametrizing fixture behavior — for example, a `user` fixture that returns admin, editor, or viewer user objects based on the parameter string — keeping the test body clean and the parametrize list readable.

**Q6: What is the difference between a stub and a spy?**

A stub replaces a dependency and returns a fixed canned value without executing any real logic. It answers "what should this dependency return?" A spy wraps the real implementation, lets it execute, but also records calls so you can assert on them afterward. Use a stub when you do not trust the real dependency in a test (network, DB, time). Use a spy when you want to verify interaction without changing behavior — for example, checking that a cache is read before the DB without replacing the cache implementation.

**Q7: How would you test a FastAPI endpoint that has a dependency injection override?**

Use `app.dependency_overrides` — a dictionary on the FastAPI application instance. Map the real dependency callable to a lambda or function that returns a test double. Pass the patched `app` to `httpx.AsyncClient` via `AsyncClient(app=app, base_url="http://test")`. After the test (or in the fixture teardown), call `app.dependency_overrides.clear()` to restore the original wiring. This approach tests the full request/response cycle including serialization and middleware without hitting real databases or external services.

**Q8: How does `hypothesis` shrinking work, and why does it matter?**

When `hypothesis` finds a falsifying input, it does not report that raw input immediately. It runs a shrinking phase that systematically reduces the counterexample — removing list elements, decreasing integers, shortening strings — until it finds the minimal input that still fails the property. The result is a small, readable counterexample rather than a 10,000-element list. Shrinking matters because developers cannot debug a 10,000-integer list; they can debug a 2-integer list.

**Q9: What does `pytest-cov --cov-fail-under=80` do in CI, and what does 80% coverage miss?**

`--cov-fail-under=80` causes pytest to exit with a non-zero status code if the measured line coverage falls below 80%, which fails the CI build. This enforces a coverage floor. What 80% coverage misses: branch coverage (whether both sides of every `if` are exercised), integration paths (a line can be executed without testing its correctness), and concurrency bugs. Coverage is a necessary but not sufficient signal; use it as a floor, not a target.

**Q10: How do you test a function that calls `time.time()` or `datetime.now()`?**

Three approaches in order of preference: (1) `monkeypatch.setattr("app.module.time", lambda: 1_700_000_000.0)` — patch the function reference in the module under test. (2) `freezegun` — `@freeze_time("2024-01-01 12:00:00")` decorator freezes `datetime.now()`, `time.time()`, and `date.today()` globally for the test. (3) Inject a clock callable as a dependency — the production code calls `clock()` instead of `time.time()`, and tests pass a lambda. Approach 3 is most design-correct; approach 2 is most convenient for legacy code.

**Q11: What is `capsys` and when would you use it over `caplog`?**

`capsys` captures text written to `sys.stdout` and `sys.stderr`. `caplog` captures Python `logging` records. Use `capsys` when testing a function that uses `print()` or writes directly to stderr — for example, a CLI entry point. Use `caplog` when testing a function that uses the `logging` module — FastAPI, Celery workers, database adapters all log via `logging`. `caplog` is preferable because it gives access to structured record attributes (`levelname`, `name`, `message`) not available in raw text.

**Q12: How do you avoid test pollution when using `@pytest.fixture(scope="module")`?**

Module-scoped fixtures are shared across all tests in a file. Pollution occurs when one test mutates the fixture's state. Mitigation strategies: (1) design module-scoped fixtures to return read-only objects or frozen dataclasses; (2) if the resource must be mutable, use `copy.deepcopy` inside the test to get a private copy; (3) document the fixture's contract — "this fixture is read-only" — and enforce it with a `@property`-based wrapper that raises `AttributeError` on assignment. The safest long-term choice is to prefer function scope and widen to module scope only after measuring a real performance cost.

---

## 13. Best Practices

**1. One assertion per test concept, not one assertion per test file.** A test can contain multiple `assert` statements as long as they all verify the same logical invariant. Do not split a single concept across three test functions; it generates noise in the test report.

**2. Name tests as sentences.** `test_rate_limiter_returns_429_when_limit_exceeded` is readable in a CI failure report. `test_limit` is not.

**3. Keep fixture graphs shallow.** Fixtures that depend on five other fixtures are hard to reason about. If a test needs more than three fixtures, consider whether the production code has a design problem (too many dependencies) rather than adding more fixtures.

**4. Use `pytest.approx` for floating-point comparisons.** `assert result == pytest.approx(3.14159, rel=1e-4)` avoids float comparison bugs.

**5. Mark slow tests and skip them in fast feedback loops.** `@pytest.mark.slow` combined with `-m "not slow"` in local pre-commit hooks keeps the inner development loop fast. Run `slow` tests only in CI.

**6. Use `tmp_path` instead of `tempfile`.** `tmp_path` is a pytest fixture that creates a per-test temporary directory and cleans it up automatically. No context managers needed.

**7. Prefer fakes over mocks for persistence layers.** `fakeredis.FakeRedis()` behaves like real Redis; a `Mock()` for Redis requires you to correctly predict every method call in advance. Fakes catch more bugs with less maintenance.

**8. Set `filterwarnings = ["error"]` in CI.** This catches deprecated API usage early, before it becomes a breaking change at the next library upgrade.

**9. Run `pytest --tb=short` locally and `--tb=long` in CI.** Short tracebacks are faster to read during development; long tracebacks in CI give enough context to debug without re-running.

**10. Gate merges on coverage delta, not only absolute coverage.** A PR that adds 200 lines of code with 0 tests should fail even if overall coverage stays above the floor. Use tools like `diff-cover` to enforce per-PR coverage.

---

## 14. Case Study

### Full Test Suite for a FastAPI Rate-Limited Endpoint

#### Problem

A FastAPI service exposes `GET /resource` protected by a Redis-backed sliding-window rate limiter: 10 requests per user per minute. The test suite must cover the happy path, the 429 boundary, error handling when Redis is unavailable, and correct time-window rollover — all without hitting a real Redis instance and with full test isolation.

```mermaid
sequenceDiagram
    participant C as Client
    participant A as FastAPI /resource
    participant R as Redis Cache

    C->>A: GET /resource (requests 1-10)
    A->>R: INCR rl:user-2:window1
    R-->>A: count under 10
    A-->>C: 200 OK

    C->>A: GET /resource (request 11)
    A->>R: INCR rl:user-2:window1
    R-->>A: count = 11
    A-->>C: 429 Rate limit exceeded

    Note over C,R: +61s — window rolls over<br/>new bucket key

    C->>A: GET /resource (window2)
    A->>R: INCR rl:user-2:window2
    R-->>A: count = 1
    A-->>C: 200 OK
```

This dramatizes `test_rate_limit_boundary` and `test_time_window_rollover_allows_new_requests` from the suite below: the 11th request in the 60-second window gets `429`, and advancing the frozen clock by 61 seconds — past the window boundary — rolls the counter into a fresh Redis key so the next request succeeds again.

#### Production Code

```python
# app/limiter.py
import time
from typing import Protocol

LIMIT: int = 10          # module-level constant — important for the BROKEN/FIX below
WINDOW_SECONDS: int = 60

class CacheClient(Protocol):
    def incr(self, key: str) -> int: ...
    def expire(self, key: str, seconds: int) -> None: ...

def check_rate_limit(client: CacheClient, user_id: str) -> bool:
    """Return True if the request is allowed; False if over limit."""
    key = f"rl:{user_id}:{int(time.time()) // WINDOW_SECONDS}"
    count = client.incr(key)
    if count == 1:
        client.expire(key, WINDOW_SECONDS)
    return count <= LIMIT
```

```python
# app/main.py
from fastapi import FastAPI, Depends, HTTPException, Request
from app.limiter import check_rate_limit, CacheClient
import fakeredis

app = FastAPI()
_redis = fakeredis.FakeRedis()   # replaced in production by real Redis

def get_cache() -> CacheClient:
    return _redis

@app.get("/resource")
def get_resource(request: Request, cache: CacheClient = Depends(get_cache)) -> dict:
    user_id = request.headers.get("X-User-Id", "anonymous")
    if not check_rate_limit(cache, user_id):
        raise HTTPException(status_code=429, detail="Rate limit exceeded")
    return {"data": "ok"}
```

#### Test Suite

```python
# tests/test_rate_limiter.py
import pytest
import time
from unittest.mock import AsyncMock, MagicMock, patch, call
from httpx import AsyncClient
from app.main import app, get_cache
from app.limiter import check_rate_limit
import fakeredis

# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def fake_cache() -> fakeredis.FakeRedis:
    """Fresh in-memory Redis per test — zero state leakage."""
    return fakeredis.FakeRedis()

@pytest.fixture
async def client(fake_cache: fakeredis.FakeRedis) -> AsyncClient:
    """Async HTTP client with the cache dependency overridden to a fake."""
    app.dependency_overrides[get_cache] = lambda: fake_cache
    async with AsyncClient(app=app, base_url="http://test") as ac:
        yield ac
    app.dependency_overrides.clear()

# ---------------------------------------------------------------------------
# Parametrize: rate limit boundary conditions
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("requests_count, expected_status", [
    (1,  200),   # first request always allowed
    (10, 200),   # exactly at limit — still allowed
    (11, 429),   # one over limit — rejected
    (20, 429),   # well over limit — still rejected (not double-counted)
])
@pytest.mark.asyncio
async def test_rate_limit_boundary(
    client: AsyncClient,
    requests_count: int,
    expected_status: int,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # Freeze the time window so all requests fall in the same bucket.
    monkeypatch.setattr("app.limiter.time.time", lambda: 1_700_000_000.0)

    for _ in range(requests_count - 1):
        await client.get("/resource", headers={"X-User-Id": "user-1"})

    response = await client.get("/resource", headers={"X-User-Id": "user-1"})
    assert response.status_code == expected_status

# ---------------------------------------------------------------------------
# monkeypatch: time window rollover resets the counter
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_time_window_rollover_allows_new_requests(
    client: AsyncClient,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    frozen_time = 1_700_000_000.0
    monkeypatch.setattr("app.limiter.time.time", lambda: frozen_time)

    # Exhaust the limit in window 1.
    for _ in range(10):
        await client.get("/resource", headers={"X-User-Id": "user-2"})

    eleventh = await client.get("/resource", headers={"X-User-Id": "user-2"})
    assert eleventh.status_code == 429

    # Advance time past the window boundary.
    frozen_time += 61
    monkeypatch.setattr("app.limiter.time.time", lambda: frozen_time)

    first_in_new_window = await client.get("/resource", headers={"X-User-Id": "user-2"})
    assert first_in_new_window.status_code == 200

# ---------------------------------------------------------------------------
# AsyncMock: Redis unavailable raises 500, not 429
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_cache_unavailable_returns_500(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    broken_cache = MagicMock()
    broken_cache.incr.side_effect = ConnectionError("Redis unreachable")

    app.dependency_overrides[get_cache] = lambda: broken_cache
    try:
        async with AsyncClient(app=app, base_url="http://test") as ac:
            response = await ac.get("/resource", headers={"X-User-Id": "user-3"})
        assert response.status_code == 500
    finally:
        app.dependency_overrides.clear()

# ---------------------------------------------------------------------------
# BROKEN → FIX: module-level LIMIT mutation without cleanup
# ---------------------------------------------------------------------------

# BROKEN: modifying the module-level LIMIT constant directly.
# test_limit_five runs fine, but it permanently changes LIMIT for
# every subsequent test in the session — breaking test_rate_limit_boundary.
def test_limit_five_broken() -> None:
    import app.limiter as limiter
    limiter.LIMIT = 5                    # BROKEN: no cleanup

    cache = fakeredis.FakeRedis()
    for _ in range(5):
        assert check_rate_limit(cache, "u") is True
    assert check_rate_limit(cache, "u") is False

    # LIMIT is now 5 for ALL subsequent tests — silent pollution.

# FIX: use monkeypatch to set and automatically restore the constant.
def test_limit_five_fixed(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.limiter.LIMIT", 5)

    cache = fakeredis.FakeRedis()
    for _ in range(5):
        assert check_rate_limit(cache, "u") is True
    assert check_rate_limit(cache, "u") is False
    # After this test, LIMIT is automatically restored to its original value (10).
    # All subsequent tests see the correct constant.

# ---------------------------------------------------------------------------
# Transactional DB rollback pattern (mirrors the §6.1 explanation)
# ---------------------------------------------------------------------------
# If this service also persists request logs to Postgres, the db fixture from
# conftest.py uses begin()/rollback() so every test starts with a clean table.
# See conftest.py fixture `db` defined with scope="function".
```

#### Key design decisions in this test suite

- `fakeredis.FakeRedis()` is a **fake** (not a mock): it implements the real Redis protocol in memory. This means `incr` and `expire` behave identically to production, catching off-by-one bugs in the sliding-window logic that a `Mock` would miss.
- `monkeypatch.setattr("app.limiter.time.time", ...)` patches the `time` reference *inside* the limiter module, not the global `time` module. This is the correct namespace.
- `app.dependency_overrides.clear()` in the `client` fixture teardown (after `yield`) ensures the override does not leak to tests that run after the fixture is torn down.
- The BROKEN→FIX block demonstrates the single most common source of test-suite flakiness in FastAPI projects: direct mutation of module-level configuration without cleanup.
