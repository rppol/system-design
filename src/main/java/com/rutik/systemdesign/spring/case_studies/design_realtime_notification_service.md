# Design: Real-Time Notification Service (Spring Boot + WebSocket + Redis Pub/Sub)

> **"A digital PA system where every speaker knows which room to broadcast to."**
> A real-time notification service must solve two problems simultaneously: maintain long-lived
> connections efficiently, and route incoming events to exactly the right connections —
> even when those connections are spread across 20 pods.
>
> **Key insight:** WebSocket connections are stateful and pod-local. Events (a user's order shipped)
> arrive at a random pod. Redis Pub/Sub is the switchboard that lets any pod publish to a per-user
> channel and lets the pod holding that user's connection deliver the message instantly.

---

## 1. Requirements Clarification

### Functional Requirements
- Push real-time notifications to connected web/mobile clients over WebSocket (primary) and SSE (fallback).
- Support notification types: order updates, payment confirmations, chat messages, system alerts.
- Store the last 50 notifications per user; deliver unread notifications on reconnect.
- Clients can acknowledge notifications (read receipts); unread count shown in UI badge.
- Fan-out to all active sessions of a user (same user logged in on multiple devices).

### Non-Functional Requirements
- **Connections:** 500,000 concurrent WebSocket connections per region.
- **Latency:** P99 < 100 ms from event generation to browser display.
- **Throughput:** 50,000 notification events/sec at peak (flash sales, order updates).
- **Delivery guarantee:** At-least-once delivery; clients deduplicate by notification ID.
- **Backpressure:** Slow consumers must not cause memory OOM on the server.

### Out of Scope
- Push notifications to mobile apps (APNS/FCM — separate service).
- Email/SMS notifications (separate batch service).
- In-app notification content storage beyond 50 messages.

---

## 2. Scale Estimation

### Connections
```
500,000 WebSocket connections / region
Per pod:     25,000 connections (20 pods)
Memory/connection (Spring WebSocket + OS buffer): ~10 KB
Memory for connections: 25,000 × 10 KB = 250 MB per pod
Total threads: virtual threads (one per connection, ~few KB stack) — 25,000 virtual threads = ~50 MB
```

### Redis Pub/Sub
```
Channels:      500,000 (one per user with active connections)
Events/sec:    50,000 published
Subscribers:   average 1.2 pods per channel (a user connected to one or two pods)
Messages relayed by Redis: 50,000 × 1.2 = 60,000 msg/s
Redis throughput: 1 MB/s (60,000 × ~17 bytes message) — within r6g.large capacity (~100k msg/s)
```

### Notification Storage
```
50 notifications/user × 100,000 active users = 5,000,000 notifications in Redis
Notification size: ~500 bytes (JSON)
Total Redis storage: 5,000,000 × 500 bytes = 2.5 GB
TTL: 7 days for each notification; 30-day TTL for the user's list (ZSET)
```

---

## 3. High-Level Architecture

```
 Event Sources (Kafka Producers)
 [Order Service] [Payment Service] [Chat Service]
        |               |               |
        v               v               v
 +-----------------------------------------+
 |           Kafka: notifications-topic    |
 |           (partitioned by user_id)      |
 +-----------------------------------------+
              |
              | Kafka consumers (one consumer group per pod)
              v
 +---------------------------------------------------+
 |  NotificationPod-1          NotificationPod-N     |
 |  +--------------------+  ...+-------------------+ |
 |  | Kafka Consumer     |     | Kafka Consumer    | |
 |  | → Redis PUBLISH    |     | → Redis PUBLISH   | |
 |  +--------------------+     +-------------------+ |
 |  | Redis Subscriber   |     | Redis Subscriber  | |
 |  | → WS/SSE delivery  |     | → WS/SSE delivery | |
 |  +--------------------+     +-------------------+ |
 |  | WS Connection Map  |     | WS Connection Map | |
 |  | userId → sessions  |     | userId → sessions | |
 +---------------------------------------------------+
              |
              v
    Redis Cluster
    - Pub/Sub channels: user:<userId>
    - Notification store: ZSET notif:<userId>
    - Unread count: HASH unread:<userId>

 Clients connect to any pod via Load Balancer (sticky sessions optional but not required)
```

### Component Inventory
| Component | Role |
|-----------|------|
| `WebSocketHandler` | Spring WebSocket handler; maintains `ConcurrentHashMap<userId, Set<WebSocketSession>>` |
| `SseController` | Fallback `SseEmitter` for clients that don't support WebSocket |
| `NotificationKafkaConsumer` | Reads `notifications` topic; stores to Redis ZSET; publishes to Redis Pub/Sub |
| `RedisSubscriptionManager` | Subscribes to Redis channels for users with active connections |
| `NotificationRepository` | `RedisTemplate` ZSET operations for notification history |
| `UnreadCountService` | Manages `HINCRBY` / `HSET` for per-user unread counts |

---

## 4. Component Deep Dives

### 4.1 WebSocket Handler with Connection Registry

```java
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    // userId → set of active sessions (multiple devices)
    private final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> sessions =
        new ConcurrentHashMap<>();

    private final RedisSubscriptionManager subscriptionManager;
    private final NotificationRepository notificationRepo;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);

        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);

        // Subscribe to Redis channel for this user if this pod is now the first session holder
        subscriptionManager.subscribeIfAbsent(userId);

        // Deliver unread notifications on reconnect
        List<Notification> unread = notificationRepo.getUnread(userId, 50);
        unread.forEach(n -> sendToSession(session, n));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
                subscriptionManager.unsubscribe(userId);  // Clean up Redis subscription
            }
        }
    }

    public void deliverToUser(String userId, Notification notification) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;  // User not connected to this pod

        for (WebSocketSession session : userSessions) {
            sendToSession(session, notification);
        }
    }

    private void sendToSession(WebSocketSession session, Notification notification) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(toJson(notification)));
        } catch (IOException e) {
            log.warn("Failed to send to session userId={}", extractUserId(session), e);
        }
    }
}
```

### 4.2 Broken Pattern: Direct Redis Pub/Sub Without Connection Check

```java
// BROKEN: publishes to Redis channel without checking if any pod has the user connected
@KafkaListener(topics = "notifications")
public void handle_broken(NotificationEvent event) {
    redisTemplate.convertAndSend("user:" + event.userId(), toJson(event));
    // If no pod has this user connected, the message is silently dropped by Redis Pub/Sub.
    // Redis Pub/Sub is fire-and-forget — no persistence, no replay on reconnect.
    // User misses the notification completely.
}
```

**Failure mode:** Redis Pub/Sub is ephemeral — messages published to channels with no subscribers
are dropped permanently. A user reconnecting after 1 second misses all notifications fired during
the disconnection window.

**Fix:** Always persist the notification to `Redis ZSET notif:<userId>` FIRST, then publish to
the Pub/Sub channel. The ZSET serves as the durable store; Pub/Sub is the live delivery hint.
On reconnect, `afterConnectionEstablished` reads unread notifications from the ZSET.

### 4.3 Kafka Consumer + Redis Dual-Write

```java
@Component
public class NotificationKafkaConsumer {

    private final NotificationRepository notifRepo;
    private final UnreadCountService unreadCountService;
    private final RedisTemplate<String, String> redisTemplate;

    @KafkaListener(
        topics = "notifications",
        groupId = "notification-service",
        concurrency = "4"  // 4 consumer threads; one per Kafka partition
    )
    public void consume(ConsumerRecord<String, String> record) {
        NotificationEvent event = parseEvent(record.value());
        Notification notification = Notification.from(event);

        // Step 1: Persist to Redis ZSET (durable store) — score = timestamp
        notifRepo.save(notification);

        // Step 2: Increment unread count
        unreadCountService.increment(event.userId());

        // Step 3: Pub/Sub delivery hint (fire-and-forget; drop if no subscriber)
        redisTemplate.convertAndSend(
            "user:" + event.userId(),
            toJson(notification)
        );
    }
}
```

### 4.4 Redis Subscription Manager

```java
@Component
public class RedisSubscriptionManager {

    private final RedisConnectionFactory connectionFactory;
    private final NotificationWebSocketHandler wsHandler;

    // Channel → subscription (one subscription per active user on this pod)
    private final ConcurrentHashMap<String, RedisMessageListenerContainer> subscriptions =
        new ConcurrentHashMap<>();

    public void subscribeIfAbsent(String userId) {
        String channel = "user:" + userId;
        subscriptions.computeIfAbsent(channel, ch -> {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(
                (message, pattern) -> {
                    Notification notification = parseNotification(message.getBody());
                    wsHandler.deliverToUser(userId, notification);
                },
                new ChannelTopic(channel)
            );
            container.start();
            return container;
        });
    }

    public void unsubscribe(String userId) {
        String channel = "user:" + userId;
        RedisMessageListenerContainer container = subscriptions.remove(channel);
        if (container != null) {
            container.stop();
            container.destroy();
        }
    }
}
```

### 4.5 Notification Repository (Redis ZSET)

```java
@Repository
public class NotificationRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private static final int MAX_STORED = 50;
    private static final Duration TTL = Duration.ofDays(7);

    private String key(String userId) { return "notif:" + userId; }

    public void save(Notification notification) {
        double score = notification.getCreatedAt().toEpochMilli();
        String key = key(notification.getUserId());

        redisTemplate.opsForZSet().add(key, toJson(notification), score);
        // Keep only the latest MAX_STORED; remove oldest if overfull
        redisTemplate.opsForZSet().removeRange(key, 0, -(MAX_STORED + 1));
        redisTemplate.expire(key, TTL);
    }

    public List<Notification> getUnread(String userId, int limit) {
        Set<String> jsons = redisTemplate.opsForZSet().reverseRange(key(userId), 0, limit - 1);
        if (jsons == null) return List.of();
        return jsons.stream().map(this::parseNotification).collect(Collectors.toList());
    }
}
```

### 4.6 Backpressure: Slow Consumer Handling

Without backpressure, a slow WebSocket client blocks the thread delivering messages, eventually
causing memory overflow from a build-up of queued messages.

```java
// Add per-session send buffer with size limit
private void sendToSessionWithBackpressure(WebSocketSession session, Notification notification) {
    if (!session.isOpen()) return;
    if (!session.isWritePossible()) {
        // Session's send buffer is full — drop the message or close the session
        log.warn("Session buffer full, dropping notification for userId={}",
            extractUserId(session));
        session.close(CloseStatus.SESSION_NOT_RELIABLE);
        return;
    }
    try {
        session.sendMessage(new TextMessage(toJson(notification)));
    } catch (IOException e) {
        log.warn("Send failed", e);
        sessions.get(extractUserId(session)).remove(session);
    }
}
```

For virtual threads (Spring Boot 3.2+), set `spring.threads.virtual.enabled=true`. Each
WebSocket session is backed by a virtual thread; blocking on slow network I/O parks the virtual
thread rather than the platform thread, supporting 25,000 connections with ~25,000 virtual threads
instead of requiring 25,000 platform threads (~25 GB of stack memory).

---

## 5. Design Decisions & Tradeoffs

### Decision 1: WebSocket vs SSE vs Long Polling

| Protocol | Bidirectional | Browser support | Reconnect | Proxy compatibility |
|----------|--------------|----------------|-----------|-------------------|
| WebSocket | Yes | All modern | Manual | Variable (some proxies strip Upgrade) |
| SSE | Server→client only | All modern | Automatic | Good (plain HTTP/1.1 or HTTP/2) |
| Long polling | No | Universal | Yes | Excellent |

**Decision:** WebSocket primary + SSE fallback. WebSocket supports bidirectional messaging
(read receipts from client). SSE is simpler and reconnects automatically — used for clients
behind corporate proxies that block WebSocket upgrades.

### Decision 2: Redis Pub/Sub vs Kafka for Cross-Pod Delivery

| Approach | Latency | Persistence | Fan-out |
|----------|---------|-------------|---------|
| Redis Pub/Sub (chosen) | < 1 ms | No | Subscribe/channel |
| Kafka (second consumer group) | 5–50 ms | Yes, configurable | Consumer group |
| gRPC streaming between pods | < 2 ms | No | Direct pod mesh |

**Decision:** Redis Pub/Sub for intra-pod fan-out (fast, simple), Kafka for durability and
replay. The two work in tandem: Kafka is the durable source; Redis Pub/Sub is the delivery mechanism.

### Decision 3: Per-User Channels vs Broadcast Channels

Per-user channels (`user:<userId>`) isolate subscriptions — each pod only subscribes to channels
for users currently connected to that pod. A single broadcast channel would fan-out every message
to every pod, requiring each pod to filter locally. With 500,000 users and 50,000 events/sec,
broadcast causes 50,000 × 20 pods = 1,000,000 Redis delivers/sec. Per-user channels: 50,000 × 1.2 =
60,000 Redis delivers/sec — 17× fewer Redis operations.

### Decision 4: ZSET vs List for Notification History

| Structure | Random access | Time-ordered | Size limiting |
|-----------|--------------|-------------|--------------|
| Redis ZSET | O(log N) | Score = timestamp | `ZREMRANGEBYRANK` |
| Redis List | O(N) pop/push | Order of insertion | `LTRIM` |

ZSET (chosen): supports `ZRANGEBYSCORE` for time-range queries, range-based size trimming, and
deduplication by `notificationId` as the member key. List would require scanning to deduplicate.

### Decision 5: Sticky Sessions vs Stateless Pods

Sticky sessions (load balancer routes user to same pod) reduce Redis Pub/Sub overhead — no
cross-pod channel needed for that user. But sticky sessions require L4 load balancer support,
complicate rolling deploys (connections on the old pod must migrate), and create pod imbalance
(one user with many devices loads one pod). Stateless pods (chosen) with Redis Pub/Sub
fan-out are simpler, more resilient, and balance load naturally.

---

## 6. Real-World Implementations

**Slack:** Uses a proprietary channel abstraction (similar to Redis Pub/Sub channels) to route
messages from their Kafka event bus to WebSocket connections. Each user's connection is registered
in a distributed session store; the message router queries the store and sends to the hosting pod.
Engineering blog: "Real-Time Messaging at Slack" (2019) describes the presence service that tracks
which WebSocket pod hosts each user.

**Discord:** WebSocket gateways (stateful pods) handle connections. Events arrive via internal
message bus. Each gateway subscribes to a Redis Pub/Sub channel per user guild. With 10M concurrent
users, Discord shards connections across ~10,000 gateway pods. Engineering blog: "How Discord
Scaled Elixir to 5,000,000 Concurrent Users" (2017).

**GitHub (notifications):** Uses SSE (`text/event-stream`) for repository event notifications.
The SSE endpoint holds the connection open; events are forwarded from an internal queue.
GitHub's architecture relies on SSE rather than WebSocket because notifications are server-to-client
only (no bidirectional need).

**Uber (trip updates):** WebSocket connections from the mobile app are maintained by stateful pods.
Redis Pub/Sub channels per driver/rider ID deliver GPS updates. Each trip status update is also
persisted to Cassandra for the trip history API. Public engineering post: "Uber's Real-Time
Push Platform" (2019).

---

## 7. Technologies & Tools

| Technology | Role | Notes |
|------------|------|-------|
| Spring WebSocket (STOMP over WS) | WebSocket protocol layer | STOMP adds message routing; simpler client-side API |
| Spring Messaging + STOMP | Message broker integration | In-process STOMP broker or external ActiveMQ/RabbitMQ |
| Redis Pub/Sub (`spring-data-redis`) | Cross-pod delivery | `RedisMessageListenerContainer`; synchronous delivery |
| Redis ZSET | Notification history | O(log N) insert; range-based pagination |
| Apache Kafka | Durable event stream | `@KafkaListener`; partitioned by `user_id` for ordering |
| `SseEmitter` (Spring MVC) | SSE fallback | Auto-reconnect by browser; simpler than WebSocket |
| Micrometer | `websocket.connections.active`, `notification.delivery.latency` | Gauge on active connections per pod |

---

## 8. Operational Playbook

### Runbook 1: Notification Delivery Latency Spike

**Symptom:** `notification.delivery.latency.p99` increases from 50 ms to 2 seconds.

**Diagnosis:**
1. Check Redis Pub/Sub lag: `redis-cli --latency -h <host>` — baseline RTT elevated?
2. Check Kafka consumer lag: `kafka-consumer-groups.sh --describe --group notification-service` —
   is the consumer group falling behind the producer offset?
3. Check GC pause: `jcmd <pid> VM.info | grep GC` — if G1GC pauses are > 200 ms, connections are
   paused during GC.

**Mitigation:** Add another pod (auto-scaling based on Kafka consumer lag metric). Connections
are distributed across all pods; new pod picks up Kafka partitions automatically.

**Resolution:** If GC is the root cause, switch to ZGC (`-XX:+UseZGC`) for sub-1 ms pauses with
25,000 live WebSocket session objects.

---

### Runbook 2: Connection Leak — Active Connection Count Growing Without Bound

**Symptom:** `websocket.connections.active` grows steadily; pod memory increases 10 MB/min;
never decreases even after traffic drops.

**Diagnosis:**
1. `sessions` map size: add a Micrometer gauge `gauge("websocket.sessions.map.size", sessions, Map::size)`.
2. Check `afterConnectionClosed` is being called: add a counter for close events.
3. Check if sessions are being removed from the map: log at `TRACE` level when a session is removed.

**Resolution:** WebSocket sessions that close abnormally (network drop without clean close handshake)
trigger `afterConnectionClosed` with `CloseStatus.NO_STATUS_CODE`. Verify this path removes
the session from the `CopyOnWriteArraySet`. Add a periodic cleanup job that removes sessions where
`!session.isOpen()`.

---

### Runbook 3: Redis Pub/Sub Channel Count Explosion

**Symptom:** Redis `PUBSUB CHANNELS` returns 500,000+ channels; Redis memory grows 200 MB/day.

**Diagnosis:**
1. `redis-cli PUBSUB NUMSUB | wc -l` — count active subscribed channels.
2. Check if `unsubscribe()` is being called on connection close: look for `RedisMessageListenerContainer.stop()` in logs.
3. Verify `subscriptions` ConcurrentHashMap size matches `sessions` map size.

**Mitigation:** If channels are leaking (subscriptions not cleaned up), manually clean up:
`redis-cli PUBSUB CHANNELS "user:*" | head -1000 | while read ch; do redis-cli UNSUBSCRIBE $ch; done`
(this only removes channels with no active subscribers — safe).

**Resolution:** Ensure `afterConnectionClosed` reliably calls `subscriptionManager.unsubscribe()`.
Add a test: verify `subscriptions.size() == sessions.size()` invariant after random connection
close simulation.

---

### Runbook 4: Kafka Consumer Lag — Notification Delivery Delayed During Flash Sale

**Symptom:** During a flash sale, Kafka consumer group lag grows to 50,000 messages; notifications
are delayed 30 seconds.

**Diagnosis:**
1. `kafka-consumer-groups.sh --describe` — which partitions have highest lag?
2. `notification-service` pods: are they saturated? Check CPU and thread pool metrics.
3. Is the bottleneck Redis Pub/Sub publish rate or WebSocket delivery rate?

**Mitigation:** Scale up the notification-service pod count. With `@KafkaListener(concurrency="4")`,
each pod handles 4 partitions. 20 pods × 4 = 80 consumers for an 80-partition topic.

**Resolution:** Pre-scale during planned high-traffic events (scheduled K8s HPA override).
Add Kafka consumer lag as an HPA custom metric for event-driven auto-scaling.

---

## 9. Common Pitfalls & War Stories

**Pitfall 1: Redis Pub/Sub Drop During Redis Restart (social platform, 2022)**
During a Redis failover (primary failed, replica promoted), all active Redis Pub/Sub subscriptions
were dropped. All 25,000 active WebSocket connections on the affected pod stopped receiving Redis
events. Notifications were generated (stored in ZSET) but never delivered until the user reconnected.
Impact: 100,000 users missed real-time updates for 3 minutes. Fix: add Redis subscription health
check — if `RedisMessageListenerContainer` reports disconnected, reconnect and re-subscribe all
active users. Spring's `RedisConnectionFactory` with Lettuce handles automatic reconnection, but
subscriptions must be explicitly re-registered in the `onMessage` error handler.

---

**Pitfall 2: Virtual Thread Pinning on `synchronized` in Spring WebSocket (Spring Boot 3.2, 2024)**
After enabling `spring.threads.virtual.enabled=true`, message delivery latency spiked intermittently.
Investigation showed `TextWebSocketHandler.sendMessage()` internally synchronized on the session
object, pinning the carrier thread for the duration of the send (including slow network writes).
With 25,000 virtual threads competing for 8 carrier threads, carrier exhaustion caused 200+ ms P99.
Fix: use Spring WebFlux `WebSocketHandler` (reactive) instead of Servlet WebSocket for reactive
virtual thread compatibility. Alternatively, wrap sends in `Thread.ofVirtual().start()` to isolate
the blocking call to its own virtual thread.

---

**Pitfall 3: Thundering Herd on Reconnect (e-commerce flash sale, 2023)**
When a pod was restarted during deployment, 25,000 clients reconnected simultaneously within
2 seconds (WebSocket client default reconnect: 1 second). The reconnect handler loaded unread
notifications from Redis for each user — 25,000 ZRANGE calls hit Redis in 2 seconds, saturating
Redis for 8 seconds. Impact: all real-time features degraded across all pods. Fix: add jittered
reconnect in the client (`reconnectDelay = 1000 + Math.random() * 9000`); rate-limit the Redis
ZRANGE reads with a Semaphore(500) in the reconnect handler.

---

**Pitfall 4: CopyOnWriteArraySet Allocation Storm (high-throughput notification service, 2022)**
`CopyOnWriteArraySet.add()` copies the internal array on every mutation. At 25,000 connection
events per deploy (reconnects after rolling restart), the GC was collecting 25,000 temporary
arrays of growing sizes. G1GC paused for 800 ms every 30 seconds during reconnect storms.
Fix: replace `CopyOnWriteArraySet` with `ConcurrentHashMap.newKeySet()` (same semantics,
O(1) add/remove, no array copy).

---

**Pitfall 5: SSE Connection Leak on Client Timeout (customer portal, 2021)**
`SseEmitter` has a configurable timeout (default 30 seconds in Spring). After the timeout, the
`SseEmitter` is "completed" internally, but the `HttpServletRequest` connection is only closed
when the client disconnects or when the server sends a completion event. Corporate proxies that
buffer responses kept the TCP connection open for 300 seconds after the SSE emitter expired.
The pod accumulated 10,000 "zombie" connections holding threads. Fix: set explicit `SseEmitter`
timeout to 60 seconds; register `onTimeout()` and `onCompletion()` callbacks that clean up the
user session registry and close the response.

---

## 10. Capacity Planning

### WebSocket Connection Capacity per Pod

```
Memory per connection (virtual thread + OS socket buffer + session state): ~10 KB
Target connections per pod: 25,000
Memory for connections: 25,000 × 10 KB = 250 MB
Pod heap: 512 MB (250 MB connections + 262 MB application heap)
Instance type: 2 vCPU / 1 GB RAM (room for GC overhead)

Platform threads (without virtual threads): 25,000 connections × 1 MB stack = 25 GB — impossible
Virtual threads: 25,000 × ~2 KB stack = 50 MB — feasible
```

### Redis Pub/Sub Sizing

```
Active channels:     500,000 (all connected users)
Events published/s:  50,000
Subscribers per channel: 1.2 average (users on one or two pods)
Messages relayed/s:  50,000 × 1.2 = 60,000 msg/s
Message size:        ~300 bytes (compact JSON notification)
Redis bandwidth:     60,000 × 300 bytes = 18 MB/s
ElastiCache r6g.large: supports ~1 Gbps = 125 MB/s — 6.9× headroom at peak
```

### Kafka Throughput

```
Notification events:  50,000 msg/s × 1 KB = 50 MB/s producer throughput
Partitions needed:    20 pods × 4 consumers = 80 partitions
Retention: 50 MB/s × 86,400 s × 3 days = 12.96 TB (compressed to ~4 TB with LZ4)
Kafka broker: 3 brokers × 4 TB storage = sufficient; m5.xlarge (3 Gbps network)
```

---

## 11. Interview Discussion Points

**Q: Why is Redis Pub/Sub used instead of just having all pods consume from Kafka directly?**
All pods consuming from Kafka would require all pods to be in separate consumer groups (one per pod),
meaning every event is delivered to every pod. Each pod then checks if the user is connected locally,
discarding most messages. This creates O(events × pods) Kafka message deliveries — at 50,000 events/s
across 20 pods, that's 1,000,000 Kafka deliveries/s, with 19/20 discarded. Redis Pub/Sub delivers
only to pods that have the user connected (subscribed to that channel) — at 50,000 × 1.2 average
subscribers = 60,000 deliveries/s. Kafka remains the durable source; Redis Pub/Sub is the efficient
routing layer.

**Q: What happens to notifications for users who are not currently connected?**
Notifications are always persisted to the Redis ZSET (`notif:<userId>`) before publishing to
Pub/Sub. When no pod has the user subscribed (no active WebSocket connection), the Pub/Sub message
is silently dropped by Redis — this is expected and handled. When the user reconnects, `afterConnectionEstablished` reads the last 50 notifications from the ZSET and delivers them.
The ZSET is the reliability layer; Pub/Sub is the live delivery optimization.

**Q: How do you handle a user with active WebSocket connections on two different pods?**
Both pods subscribe to the Redis channel `user:<userId>`. When an event is published to that
channel, Redis delivers it to all subscribers — both pods receive the message. Both pods then
deliver it to all WebSocket sessions for that user on their respective pods. The client receives
the notification on all active sessions (e.g., browser + mobile web). This fan-out is intentional.
The client can deduplicate by `notificationId` if needed (though duplicate delivery across devices
is usually the desired behavior for notifications).

**Q: What is the memory cost of 500,000 WebSocket connections and how do virtual threads help?**
Each platform thread has a 1 MB stack. 500,000 platform threads would require 500 GB of stack
space — impossible. Virtual threads have a ~2 KB initial stack (expandable but parked when
blocked). 500,000 virtual threads use ~1 GB of stack, plus ~10 KB per connection for OS socket
buffers and session state = ~5 GB total — manageable on 20 pods × 256 MB. This is the core
reason virtual threads enable WebSocket servers to handle orders of magnitude more concurrent
connections than traditional thread-per-connection models.

**Q: How would you prevent a slow consumer from causing OOM on the notification server?**
Three layers of backpressure: (a) WebSocket session-level: check `session.isWritePossible()`
before sending; close sessions that are not writable (their send buffer is full). (b) Pod-level:
cap the number of queued outbound messages per user session at N; drop oldest if full (notifications
are low-value; dropping is preferable to OOM). (c) Kafka consumer-level: `max.poll.records=500`
and `max.poll.interval.ms=30000` ensure the consumer doesn't pull faster than it can deliver.
If delivery is blocked, the consumer group falls behind in Kafka (backpressure propagates upstream),
and the HPA scales up pods.

**Q: What is STOMP over WebSocket and when would you use it?**
STOMP (Simple Text Oriented Messaging Protocol) is a lightweight messaging protocol layered over
WebSocket. It adds routing primitives (SUBSCRIBE, SEND, destination addresses) that map naturally
to a message broker. Spring's `@EnableWebSocketMessageBroker` with STOMP allows clients to
subscribe to destinations like `/topic/notifications/123` (pub-sub) or `/queue/user/session` (point-to-point).
Use STOMP when you need room-based subscriptions (group chat, live dashboards) rather than
per-user push. For simple per-user push notifications, raw `TextWebSocketHandler` (as in §4.1) is
simpler and has less overhead.

**Q: How does the notification service ensure at-least-once delivery without exactly-once?**
At-least-once comes from two sources: (a) Kafka consumer with `enable.auto.commit=false` +
manual offset commit after processing — if the consumer crashes after writing to Redis but before
committing the offset, the event is re-consumed and re-processed (duplicate notification in ZSET).
The ZSET stores by `notificationId` as the member — `ZADD` is idempotent for the same member.
(b) Redis Pub/Sub may deliver the same message twice on reconnect if the subscription is re-created
during network blip. Clients deduplicate by `notificationId`. Exactly-once would require
Kafka EOS transactions for the outbox, adding complexity for marginal benefit (duplicate notifications
are visible but harmless for read-receipt use cases).

**Q: How would you add rate limiting to the notification service to protect against event storms?**
At the Kafka consumer level: limit `max.poll.records` to 500 and the polling interval to 100 ms
— effective maximum 5,000 events/s per consumer thread, 20,000/s per pod (4 threads). At the Redis
publish level: use `RateLimiter` (Resilience4j or `java.util.concurrent.Semaphore`) wrapping
`redisTemplate.convertAndSend()` to cap Redis Pub/Sub publish rate. At the WebSocket delivery level:
per-user send-rate limiting via a `ConcurrentHashMap<userId, RateLimiter>` — cap delivery to 100
notifications/s per user to prevent a single chatty user from consuming disproportionate bandwidth.

**Q: What tradeoffs come with using SSE over WebSocket for this use case?**
SSE advantages: automatic reconnection (built into `EventSource` browser API); works over plain
HTTP/1.1 and HTTP/2; no Upgrade handshake — friendlier to corporate proxies and CDNs; simpler
than WebSocket for server-push-only use cases. SSE limitations: unidirectional (server to client
only) — read receipts (client to server) require a separate HTTP POST; HTTP/1.1 limits to 6 SSE
connections per browser per domain (mitigated by HTTP/2 multiplexing); connection teardown on
some proxies drops the SSE stream. For this notification service (read receipts needed),
WebSocket is primary and SSE is a proxy-compatibility fallback.

**Q: How would you implement read receipts (marking a notification as seen) in this architecture?**

A: Read receipts are client-to-server messages, so WebSocket's bidirectionality is needed (SSE cannot handle this without a separate HTTP POST). The client sends `{"type":"ACK","notificationId":"<uuid>"}` as a WebSocket text message. The `NotificationWebSocketHandler.handleTextMessage()` parses the ACK, calls `notificationRepo.markRead(userId, notificationId)` which updates the ZSET member's metadata or a separate `read:<userId>` Redis set, and decrements the unread counter via `UnreadCountService`. On reconnect, the unread count is re-computed as `ZCARD notif:<userId>` minus `SCARD read:<userId>`. This avoids scanning all notifications to compute the badge count on every reconnect — both Redis operations are O(1).

---

## Cross-Cutting References

- [OTel Observability for Spring](cross_cutting/otel_observability_for_spring.md) — distributed trace from Kafka consumer through Redis Pub/Sub to WebSocket delivery; `@Observed` on `deliverToUser()`.
- [Resilience4j Patterns](cross_cutting/resilience4j_patterns.md) — circuit breaker protecting Redis Pub/Sub publish; bulkhead limiting concurrent Kafka consumer threads.
- [Zero-Downtime Deploys and Config](cross_cutting/zero_downtime_deploys_and_config.md) — graceful pod shutdown draining WebSocket connections before termination; `preStop: sleep 5` + client reconnect jitter.
- [Testcontainers and Test Strategy](cross_cutting/testcontainers_and_test_strategy.md) — integration tests with `RedisContainer` + `KafkaContainer` verifying end-to-end notification delivery and reconnect replay.
