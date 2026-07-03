# Design a Real-Time Chat System with FastAPI

---

## Problem Statement

Design a production-grade real-time chat backend (Slack-like) with the following requirements:

**Functional requirements:**
- Support chat rooms (channels) and direct messages between users.
- Deliver messages in real-time to all participants currently connected to a room.
- Persist all messages to a relational database so history is retrievable after reconnect.
- Support 10,000 concurrent WebSocket connections spread across multiple horizontally scaled pods.
- Authenticate users before they may send or receive messages.

**Non-functional requirements:**
- Message delivery latency (sender → all receivers): under 200 ms at P99 under normal load.
- The system must scale horizontally — adding pods increases capacity without changing application code.
- Messages must not be lost if a pod restarts mid-conversation (durable persistence).
- Stale/zombie connections must be detected and cleaned up within 30 seconds.

**Out of scope:**
- End-to-end encryption.
- Message editing and deletion.
- File and media uploads.
- Push notifications for offline users.

---

## Architecture Overview

```
                        Client A (Room: #general)
                             |
                        WebSocket connection
                             |
                  +----------v-----------+
                  |    FastAPI Pod 1      |
                  |                      |
                  |  ConnectionManager   |
                  |  {room_id: [ws1,ws2]}|
                  |                      |
                  | /ws/{room_id}        |
                  +----------+-----------+
                             |
              1. JWT auth on first message
              2. Subscribe to Redis channel "room:{room_id}"
              3. Publish inbound message to Redis
                             |
                  +----------v-----------+
                  |       Redis          |
                  |   Pub/Sub broker     |
                  |  channel: room:xyz   |
                  +----+----------+------+
                       |          |
            fan-out to all        fan-out to all
            subscribers           subscribers
                       |          |
          +------------v--+  +---v-----------+
          |  FastAPI Pod 1 |  | FastAPI Pod 2 |
          | (self, skip)   |  | (forward to   |
          |                |  |  local conns) |
          +----------------+  +---------------+
                       |
              +---------v--------+
              |   PostgreSQL     |
              | messages table   |
              | (async write     |
              |  via background  |
              |  task)           |
              +------------------+

WebSocket connection lifecycle:
  CONNECT → TLS upgrade → HTTP 101 Switching Protocols
  → first text frame: {"type":"auth","token":"<JWT>"}
  → server validates JWT, joins room subscription
  → bidirectional frames until CLOSE or ping timeout
```

**Component inventory:**

| Component | Role |
|-----------|------|
| FastAPI WebSocket endpoint | Manages the WS lifecycle per connection |
| `ConnectionManager` | In-process registry of live WebSocket objects per room |
| Redis pub/sub | Cross-pod message fan-out; one channel per room |
| `asyncio` background subscriber | Per-pod coroutine that reads from Redis and pushes to local connections |
| PostgreSQL + SQLAlchemy (async) | Durable message persistence |
| JWT (python-jose) | Stateless auth validated on first WS message |

---

## Key Design Decisions

### 1. WebSocket vs SSE vs Long-Polling

**Choice: WebSocket.**

Server-Sent Events (SSE) are unidirectional (server → client only), so the client would need a separate HTTP channel to send messages, doubling connection overhead. Long-polling adds 50-200 ms round-trip latency per message and high per-message HTTP overhead. WebSocket provides full-duplex, low-latency framing over a single TCP connection and is the industry standard for chat (Slack, Discord, Telegram Web). The added complexity of managing WebSocket state is acceptable given the requirements.

### 2. Fan-Out: In-Process Registry vs Redis Pub/Sub

**Choice: Redis pub/sub for cross-pod fan-out.**

The broken approach (shown in Implementation) stores connections only in a per-process Python dict. This works on a single pod but silently drops messages for users connected to a different pod. With 10k concurrent connections spread across N pods, every pod sees only 10k/N connections. Redis pub/sub adds a publish call on message receipt and a subscriber coroutine per room per pod. Any pod that receives a message publishes it to Redis; all pods (including the sender) receive it from Redis and forward to their local connections. This is the standard approach used by Socket.IO and Centrifugo.

### 3. Message Persistence: Synchronous vs Async Write

**Choice: Async background task (broadcast-first, persist-via-task).**

Writing to PostgreSQL synchronously before broadcasting adds 5-20 ms database latency to every message delivery, violating the P99 < 200 ms target under load. Instead, the handler broadcasts via Redis immediately, then enqueues a background task (via FastAPI `BackgroundTasks`) to persist to PostgreSQL. The tradeoff is a small durability window: if the pod crashes between publish and persist, that message is lost. For most chat applications this is acceptable. For financial or compliance-critical chat, a write-ahead log or outbox pattern would be required.

### 4. Authentication: JWT in First Message vs Query Parameter

**Choice: JWT in first WebSocket message.**

Passing a JWT as a query parameter (`/ws/room?token=xxx`) is a common shortcut but embeds the token in server access logs, proxy logs, and browser history — a significant security risk. The WebSocket HTTP upgrade request does not support the `Authorization` header in most browsers. The correct approach is to complete the WebSocket handshake unauthenticated, then require the client to send a structured `{"type":"auth","token":"<JWT>"}` frame as its very first message. The server sends an error frame and closes the connection if auth fails or the timeout elapses.

### 5. Backpressure and Stale Connection Detection

**Choice: Per-connection `asyncio.Queue` with bounded capacity and periodic ping.**

A slow consumer (e.g., a mobile client on a poor connection) can stall `await websocket.send_text()` indefinitely, blocking the subscriber coroutine for all connections in that room. Each connection gets an `asyncio.Queue(maxsize=64)` as a buffer. The sender puts messages into the queue without blocking; a per-connection writer coroutine drains the queue. If the queue is full (slow consumer), the message is dropped and the connection is flagged for eviction. Additionally, the server sends a WebSocket ping frame every 20 seconds; if no pong is received within 10 seconds, the connection is closed and removed from the registry.

---

## Implementation

### Broken Approach: In-Process Manager Does Not Scale

```python
# BAD: in-process only — messages not delivered to users on other pods
class ConnectionManager:
    def __init__(self) -> None:
        self.active: dict[str, list[WebSocket]] = {}  # room_id -> connections

    async def connect(self, room_id: str, ws: WebSocket) -> None:
        await ws.accept()
        self.active.setdefault(room_id, []).append(ws)

    async def broadcast(self, room_id: str, message: str) -> None:
        for ws in self.active.get(room_id, []):
            await ws.send_text(message)  # blocks if slow consumer
            # ALSO: only reaches connections on THIS pod
```

This drops messages for all users on pods 2..N. With a 10-pod deployment, ~90% of users see nothing.

### Fixed Approach: Redis Pub/Sub Fan-Out

```python
# src/chat/connection_manager.py
from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import AsyncIterator

import redis.asyncio as aioredis
from fastapi import WebSocket

logger = logging.getLogger(__name__)

QUEUE_MAX = 64
PING_INTERVAL = 20  # seconds
PONG_TIMEOUT = 10   # seconds


class ConnectionManager:
    """
    In-process registry for WebSocket connections on this pod.
    Cross-pod delivery is handled via Redis pub/sub.
    One ConnectionManager instance per application (singleton via lifespan).
    """

    def __init__(self, redis_client: aioredis.Redis) -> None:
        self._redis = redis_client
        # room_id -> {ws_id -> (WebSocket, asyncio.Queue)}
        self._rooms: dict[str, dict[int, tuple[WebSocket, asyncio.Queue[str]]]] = {}
        self._subscriber_tasks: dict[str, asyncio.Task[None]] = {}

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def connect(self, room_id: str, ws: WebSocket) -> asyncio.Queue[str]:
        """Accept WS and register it. Returns the per-connection send queue."""
        await ws.accept()
        q: asyncio.Queue[str] = asyncio.Queue(maxsize=QUEUE_MAX)
        ws_id = id(ws)
        self._rooms.setdefault(room_id, {})[ws_id] = (ws, q)

        if room_id not in self._subscriber_tasks:
            task = asyncio.create_task(
                self._redis_subscriber(room_id),
                name=f"redis-sub-{room_id}",
            )
            self._subscriber_tasks[room_id] = task

        return q

    def disconnect(self, room_id: str, ws: WebSocket) -> None:
        room = self._rooms.get(room_id, {})
        room.pop(id(ws), None)
        if not room:
            self._rooms.pop(room_id, None)
            task = self._subscriber_tasks.pop(room_id, None)
            if task:
                task.cancel()

    async def publish(self, room_id: str, payload: dict) -> None:
        """Publish a message to Redis so ALL pods receive it."""
        await self._redis.publish(f"room:{room_id}", json.dumps(payload))

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    async def _redis_subscriber(self, room_id: str) -> None:
        """
        Long-running coroutine: subscribes to Redis channel for this room
        and enqueues received messages into all local WebSocket queues.
        """
        channel_name = f"room:{room_id}"
        pubsub = self._redis.pubsub()
        await pubsub.subscribe(channel_name)
        logger.info("Subscribed to Redis channel %s", channel_name)
        try:
            async for raw in pubsub.listen():
                if raw["type"] != "message":
                    continue
                text: str = raw["data"]
                room = self._rooms.get(room_id, {})
                dead: list[int] = []
                for ws_id, (_, q) in room.items():
                    try:
                        q.put_nowait(text)
                    except asyncio.QueueFull:
                        logger.warning(
                            "Queue full for ws_id=%s in room=%s, dropping message",
                            ws_id,
                            room_id,
                        )
                        dead.append(ws_id)
                for ws_id in dead:
                    room.pop(ws_id, None)
        except asyncio.CancelledError:
            pass
        finally:
            await pubsub.unsubscribe(channel_name)
            await pubsub.close()
```

### WebSocket Endpoint with JWT Auth and Heartbeat

```python
# src/chat/router.py
from __future__ import annotations

import asyncio
import json
import logging
from datetime import datetime, timezone

from fastapi import APIRouter, BackgroundTasks, WebSocket, WebSocketDisconnect
from pydantic import BaseModel

from .auth import verify_jwt
from .connection_manager import ConnectionManager, PING_INTERVAL, PONG_TIMEOUT
from .persistence import persist_message
from .dependencies import get_manager  # returns singleton from app.state

logger = logging.getLogger(__name__)
router = APIRouter()


class ChatMessage(BaseModel):
    type: str          # "auth" | "message" | "pong"
    token: str | None = None
    content: str | None = None


AUTH_TIMEOUT = 10.0  # seconds to send auth frame after connect


@router.websocket("/ws/{room_id}")
async def websocket_endpoint(
    room_id: str,
    websocket: WebSocket,
    background_tasks: BackgroundTasks,
    manager: ConnectionManager = ...,  # injected via Depends in actual app
) -> None:
    send_queue = await manager.connect(room_id, websocket)
    user_id: str | None = None

    # --- Phase 1: authenticate ---
    try:
        raw = await asyncio.wait_for(websocket.receive_text(), timeout=AUTH_TIMEOUT)
    except asyncio.TimeoutError:
        await websocket.close(code=4001, reason="auth timeout")
        manager.disconnect(room_id, websocket)
        return

    try:
        first = ChatMessage.model_validate_json(raw)
        if first.type != "auth" or not first.token:
            raise ValueError("expected auth frame")
        claims = verify_jwt(first.token)
        user_id = claims["sub"]
    except Exception as exc:
        await websocket.close(code=4003, reason=f"auth failed: {exc}")
        manager.disconnect(room_id, websocket)
        return

    await websocket.send_text(json.dumps({"type": "auth_ok", "user_id": user_id}))
    logger.info("User %s joined room %s", user_id, room_id)

    # --- Phase 2: main loop ---
    writer_task = asyncio.create_task(
        _queue_writer(websocket, send_queue),
        name=f"writer-{id(websocket)}",
    )
    ping_task = asyncio.create_task(
        _heartbeat(websocket, manager, room_id),
        name=f"ping-{id(websocket)}",
    )

    try:
        while True:
            raw = await websocket.receive_text()
            msg = ChatMessage.model_validate_json(raw)

            if msg.type == "pong":
                # heartbeat response — handled inside _heartbeat via event
                continue

            if msg.type == "message" and msg.content:
                payload = {
                    "type": "message",
                    "room_id": room_id,
                    "user_id": user_id,
                    "content": msg.content,
                    "ts": datetime.now(timezone.utc).isoformat(),
                }
                # broadcast-first via Redis, persist asynchronously
                await manager.publish(room_id, payload)
                background_tasks.add_task(persist_message, payload)

    except WebSocketDisconnect:
        logger.info("User %s disconnected from room %s", user_id, room_id)
    finally:
        writer_task.cancel()
        ping_task.cancel()
        manager.disconnect(room_id, websocket)


async def _queue_writer(ws: WebSocket, q: asyncio.Queue[str]) -> None:
    """Drains the per-connection send queue, decoupling slow consumers."""
    try:
        while True:
            text = await q.get()
            await ws.send_text(text)
    except (asyncio.CancelledError, Exception):
        pass


async def _heartbeat(
    ws: WebSocket,
    manager: ConnectionManager,
    room_id: str,
) -> None:
    """Sends periodic pings; closes connection if no pong within PONG_TIMEOUT."""
    try:
        while True:
            await asyncio.sleep(PING_INTERVAL)
            await ws.send_text(json.dumps({"type": "ping"}))
            try:
                await asyncio.wait_for(_wait_pong(ws), timeout=PONG_TIMEOUT)
            except asyncio.TimeoutError:
                logger.warning("Ping timeout for ws=%s in room=%s", id(ws), room_id)
                await ws.close(code=1001, reason="ping timeout")
                return
    except (asyncio.CancelledError, Exception):
        pass


async def _wait_pong(ws: WebSocket) -> None:
    """Reads frames until a pong is seen (non-pong frames are re-dispatched)."""
    # In production, integrate with the main receive loop via asyncio.Event.
    # Simplified here for clarity.
    while True:
        raw = await ws.receive_text()
        msg = ChatMessage.model_validate_json(raw)
        if msg.type == "pong":
            return
```

### Message Persistence (SQLAlchemy Async)

```python
# src/chat/persistence.py
from __future__ import annotations

import logging
from datetime import datetime, timezone

from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

logger = logging.getLogger(__name__)


async def persist_message(
    payload: dict,
    session_factory: async_sessionmaker[AsyncSession] | None = None,
) -> None:
    """
    Write a chat message to PostgreSQL.
    Called as a BackgroundTask — failures are logged but do not affect delivery.
    """
    if session_factory is None:
        # In production, inject via app.state; simplified here
        logger.error("No session_factory — message not persisted: %s", payload)
        return

    try:
        async with session_factory() as session:
            await session.execute(
                text(
                    """
                    INSERT INTO messages (room_id, user_id, content, created_at)
                    VALUES (:room_id, :user_id, :content, :created_at)
                    """
                ),
                {
                    "room_id": payload["room_id"],
                    "user_id": payload["user_id"],
                    "content": payload["content"],
                    "created_at": datetime.fromisoformat(payload["ts"]).replace(
                        tzinfo=timezone.utc
                    ),
                },
            )
            await session.commit()
    except Exception:
        logger.exception("Failed to persist message for room=%s", payload.get("room_id"))
```

### Application Lifespan (Wiring)

```python
# src/chat/main.py
from __future__ import annotations

from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

import redis.asyncio as aioredis
from fastapi import FastAPI
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker

from .connection_manager import ConnectionManager
from .router import router


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    redis_client = aioredis.from_url("redis://redis:6379", decode_responses=True)
    engine = create_async_engine("postgresql+asyncpg://user:pass@db/chat", pool_size=20)
    session_factory = async_sessionmaker(engine, expire_on_commit=False)

    app.state.manager = ConnectionManager(redis_client)
    app.state.session_factory = session_factory

    yield

    await redis_client.aclose()
    await engine.dispose()


app = FastAPI(lifespan=lifespan)
app.include_router(router)
```

### JWT Verification

```python
# src/chat/auth.py
from __future__ import annotations

from jose import JWTError, jwt

SECRET_KEY = "change-me-in-production"
ALGORITHM = "HS256"


def verify_jwt(token: str) -> dict:
    """
    Decode and verify a JWT. Raises ValueError on failure.
    In production, use RS256 with a public key fetched from JWKS endpoint.
    """
    try:
        claims: dict = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        if "sub" not in claims:
            raise ValueError("missing 'sub' claim")
        return claims
    except JWTError as exc:
        raise ValueError(f"invalid token: {exc}") from exc
```

---

## Python/FastAPI Components Used

| Component | Usage |
|-----------|-------|
| `fastapi.WebSocket` | Manages individual WS connection lifecycle (accept, receive, send, close) |
| `fastapi.WebSocketDisconnect` | Exception raised when client closes the connection |
| `fastapi.BackgroundTasks` | Defers PostgreSQL write to after the broadcast returns |
| `asyncio.Queue` | Per-connection bounded buffer that decouples slow consumers from the Redis subscriber |
| `asyncio.create_task` | Spawns per-connection writer and heartbeat coroutines concurrently with the main receive loop |
| `asyncio.wait_for` | Enforces auth timeout (10 s) and pong timeout (10 s) |
| `redis.asyncio` (aioredis) | Async Redis client; `pubsub()` for subscription, `publish()` for fan-out |
| `sqlalchemy.ext.asyncio` | Async PostgreSQL session for non-blocking persistence |
| `pydantic.BaseModel` | Validates inbound WebSocket JSON frames (ChatMessage) |
| `contextlib.asynccontextmanager` | Lifespan handler for startup/shutdown of Redis and DB connections |
| `python-jose` | JWT decode and validation |

---

## Tradeoffs and Alternatives

### WebSocket vs SSE vs Long-Polling

| Dimension | WebSocket | SSE | Long-Polling |
|-----------|-----------|-----|--------------|
| Directionality | Full-duplex | Server-to-client only | Server-to-client (client re-connects for each poll) |
| Latency | ~1 ms (single TCP frame) | ~10 ms (HTTP headers per chunk) | 50-200 ms (new TCP + HTTP per poll) |
| Browser support | Excellent | Excellent (except older IE) | Universal |
| Proxy compatibility | Can be blocked by older proxies | HTTP/1.1 compatible | HTTP/1.1 compatible |
| Server connection cost | 1 fd + asyncio task | 1 fd + asyncio task | 1 fd per poll cycle |
| Best for | Chat, collaboration, games | Feeds, dashboards, notifications | Fallback or firewall-constrained environments |

### Redis Pub/Sub vs Kafka

| Dimension | Redis Pub/Sub | Kafka |
|-----------|--------------|-------|
| Delivery guarantee | At-most-once (no persistence) | At-least-once with offsets |
| Message retention | None (fire-and-forget) | Configurable (hours to weeks) |
| Throughput | ~1M msg/s single node | Millions/s with partitioning |
| Latency | Sub-millisecond | 5-15 ms typical |
| Fan-out model | All subscribers in channel | Consumer group per subscriber |
| Operational complexity | Low | High |
| Best fit | Chat fan-out (at-most-once acceptable) | Audit log, replay, multi-consumer pipelines |

For chat where Redis pub/sub drops messages if no subscriber is listening, that is acceptable — clients reconnect and fetch history from PostgreSQL. For compliance use cases (financial chat, healthcare), Kafka with consumer-group offsets per pod is required.

### Synchronous vs Asynchronous Persistence

| Approach | Latency impact | Durability | Complexity |
|----------|---------------|------------|------------|
| Write-through (DB before broadcast) | +5-20 ms per message | Strong (no loss on crash) | Low |
| Background task (broadcast first) | Negligible | Soft (crash window ~50 ms) | Low-medium |
| Outbox pattern (DB + event log atomically) | +5-20 ms | Strong + replay capability | High |

The background-task approach is the right default for general chat. The outbox pattern is required if strict ordering or exactly-once delivery to downstream consumers (e.g., search indexers) is needed.

---

## Interview Discussion Points

**Q: Why must the ConnectionManager use Redis pub/sub instead of an in-process dict in a horizontally scaled deployment?**
Each pod maintains its own in-memory dict of WebSocket connections. A message received by pod 1 is only delivered to connections on pod 1. Users connected to pods 2 through N never see it. Redis pub/sub acts as a shared message bus — any pod publishes to a channel, and all pods receive the broadcast and forward it to their local connections.

**Q: What happens if Redis goes down?**
New messages cannot be published or delivered. Existing WebSocket connections remain open but messages are silently dropped. On reconnect after Redis recovery, clients should request message history via a REST endpoint backed by PostgreSQL. To reduce the blast radius, use Redis Sentinel (automatic failover in ~5-10 seconds) or Redis Cluster. The app should catch `aioredis.ConnectionError` and either enqueue messages locally for retry or immediately return an error frame to the sender.

**Q: How does the per-connection asyncio.Queue solve the slow consumer problem?**
Without a queue, the Redis subscriber coroutine calls `await ws.send_text()` directly on each connection. A slow client causes this await to block, stalling delivery for all other connections in the room. The queue decouples publishing from delivery: the subscriber does a non-blocking `put_nowait()` into each connection's queue. A separate writer coroutine per connection drains the queue. If the queue is full (`maxsize=64`), the message is dropped and the connection is eventually evicted — this is the correct backpressure response.

**Q: Why is JWT passed in the first WebSocket message rather than as a query parameter?**
Query parameters appear in URL form and are logged by every proxy, load balancer, and web server between client and backend — a token in a URL is a credential in a log file. They also appear in browser history. The WebSocket HTTP upgrade request does not support the `Authorization` header in browser clients. Sending the token as the first frame after the handshake keeps it inside the encrypted WebSocket channel and out of logs.

**Q: How do you handle the case where a client reconnects after being disconnected?**
On reconnect, the client re-authenticates and re-joins the room. It should include a `last_message_id` in the auth frame. The server queries PostgreSQL for messages newer than that ID and sends them as a replay burst before resuming live delivery. This pattern is called "catch-up on reconnect" and prevents message gaps during brief disconnections.

**Q: What is the durability gap in the broadcast-first persistence design, and how do you close it?**
Between `await manager.publish()` and the background task writing to PostgreSQL, a pod crash loses that message permanently — typically a window of 10-50 ms. For general chat this is acceptable. To close the gap, use an outbox pattern: write the message to a `message_outbox` table in PostgreSQL atomically with the chat record (single transaction), then have a separate process tail the outbox and publish to Redis. On crash, the outbox tails from the last committed row.

**Q: How would you scale to 100k concurrent connections?**
Scale horizontally to 10 pods (10k connections per pod). Each pod runs a single asyncio event loop — there is no GIL contention for I/O-bound WebSocket operations. Ensure Redis pub/sub capacity: a single Redis node handles ~1M pub/sub messages per second, well above 100k connected users sending messages at typical chat rates (1-5 msg/min average). For connection memory, each asyncio WebSocket plus a 64-item queue costs roughly 50-100 KB — 10k connections per pod is approximately 500 MB-1 GB of RAM, within a standard 2-4 GB pod allocation.

**Q: How do you detect and clean up zombie connections (client crash without sending a CLOSE frame)?**
The TCP FIN/RST may not arrive if the client's network disappears (mobile, NAT timeout). The server-side ping-pong loop sends a JSON `{"type":"ping"}` every 20 seconds. If no `{"type":"pong"}` arrives within 10 seconds, the server closes the WebSocket with code 1001 and calls `manager.disconnect()`. This bounds the zombie window to at most 30 seconds (20 s interval + 10 s timeout), which satisfies the 30-second cleanup requirement.

**Q: What changes are needed to support direct messages (DMs) in addition to rooms?**
Model a DM as a room with exactly two participants. Generate a canonical DM room ID from the two user IDs (e.g., `dm:{min(uid_a,uid_b)}:{max(uid_a,uid_b)}`). The WebSocket endpoint, ConnectionManager, and Redis pub/sub channel all remain identical — only the room ID format changes. Access control (verifying the requesting user is one of the two participants) is enforced during the auth phase by checking the JWT `sub` against the room membership in PostgreSQL.
