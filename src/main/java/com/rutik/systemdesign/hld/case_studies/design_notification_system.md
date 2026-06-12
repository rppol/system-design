# System Design: Notification System

## Intuition

> **Design intuition**: A notification system is a *fan-out and routing* problem dressed up as a messaging problem. Every other service in the company (Order, Chat, Marketing, Fraud, Payments) wants to tell a user "something happened" — but each user has different channels they're reachable on (push, SMS, email, in-app), different preferences (opt-in/out per category), different timezones (quiet hours), and different urgency tolerances (a fraud alert can't wait, a "30% off" promo can). The system's job is to take a flood of "something happened" events from dozens of producers and turn each one into the *right* message, on the *right* channel, at the *right* time, exactly once — without letting a slow SMS provider block a time-critical push, and without tripping a third-party rate limit when 100 million users need to hear about something at once.

**Key insight**: At 500M DAU with 5 notifications/user/day, the system handles ~29K notifications/sec on average and ~145K/sec at peak — but the *real* engineering constraint isn't your own infrastructure, it's the rate limits of FCM, APNs, Twilio, and SES, which you do not control and which will silently drop or throttle your traffic if you exceed them. Everything in this design — per-channel queues, token-bucket limiters per provider, idempotency keys, async delivery tracking — exists to convert "we have 145K events/sec to send" into "we have 145K events/sec, smoothly metered to whatever each downstream provider can actually absorb, with zero duplicates and full delivery visibility."

---

## 1. Requirements Clarification

### Functional Requirements

- **Multi-channel delivery**: Send notifications via push (FCM for Android, APNs for iOS), SMS (Twilio/SNS), email (SES/SendGrid), and in-app (websocket/polling-delivered messages shown inside the app).
- **Templated and localized messages**: Producers send a `template_id` + parameters (e.g., `order_shipped`, `{order_id, tracking_url}`); the system renders the final text in the user's preferred language and locale-specific formatting (dates, currency).
- **Per-user, per-channel preferences**: Users opt in/out of categories (e.g., "Order updates: push + email, but not SMS"; "Marketing: off entirely") and the system must honor these on every send.
- **Quiet hours by timezone**: Users can configure a "do not disturb" window (e.g., 10pm-8am in their *local* time); non-critical notifications are suppressed or deferred during this window.
- **Priority tiers**: Transactional (OTP, order shipped, payment failed, security alert) vs. marketing/broadcast (promotions, new-feature announcements, re-engagement campaigns) — these have fundamentally different latency and throughput SLAs and must never compete for the same resources.
- **Scheduled / delayed sends**: A producer can request "send this at 2026-06-15 09:00 user-local-time" or "send 24h after signup" — the system must hold the notification until the scheduled time without blocking a worker.
- **Delivery status tracking**: For every notification, track `queued -> sent -> delivered -> opened/clicked` (where the channel supports it), surfaced to producers via API/webhook and to internal dashboards.
- **Broadcast to large segments**: Marketing/Campaigns can target a segment ("all users in India who haven't opened the app in 7 days") of up to 100M+ users for a single campaign.

### Non-Functional Requirements

- **At-least-once delivery with deduplication**: A notification must never be silently dropped, but the *same logical event* (e.g., "order #123 shipped") must never result in two emails to the same user — even if the producer retries or a Kafka consumer rebalances mid-processing.
- **Low latency for transactional**: p99 < 5 seconds from "producer publishes event" to "message handed off to provider" for transactional notifications (OTP, security alerts, payment confirmations).
- **High throughput for broadcast**: A campaign targeting 100M users must complete in well under an hour (target: ~10-15 minutes), without overwhelming the system or any downstream provider.
- **Respect third-party provider rate limits**: FCM, APNs, Twilio (per sending number), and SES (per-account sending quota) all impose hard rate limits; exceeding them causes throttling, silent drops, or account suspension. Rate-limit awareness is a first-class architectural constraint, not an afterthought.
- **Durable queueing**: No notification is lost if a worker crashes mid-processing; Kafka with replication and consumer-group offset management provides this durability.
- **Horizontal scalability**: Each channel (push/SMS/email/in-app) must scale independently — a 10x spike in email volume (e.g., a billing-cycle email blast) must not affect push notification latency.

### Out of Scope

- Building a custom SMTP server, SMS gateway, or carrier-level push infrastructure — we integrate with FCM/APNs/Twilio/SES as black boxes.
- Client-side push SDK implementation (device token registration, notification rendering in the OS) — assumed to exist.
- A/B testing framework for campaign content — campaigns send pre-rendered or pre-selected variants; experimentation is a separate system that feeds `template_id` selection upstream.

---

## 2. Scale Estimation

### Traffic

- **500M DAU**, average **5 notifications/user/day** = **2.5B notifications/day**.
- Average rate: 2.5B / 86,400 sec ≈ **29,000 notifications/sec**.
- Peak (5x average, e.g., during a major sale event, breaking news push, or end-of-day digest window): **~145,000 notifications/sec**.

### Channel Mix (of the 2.5B/day)

| Channel | Share | Daily Volume | Avg/sec | Peak/sec (5x) |
|---|---|---|---|---|
| Push (FCM/APNs) | 70% | 1.75B | ~20,250 | ~101,250 |
| In-app | 20% | 500M | ~5,800 | ~29,000 |
| Email (SES/SendGrid) | 8% | 200M | ~2,300 | ~11,600 |
| SMS (Twilio/SNS) | 2% | 50M | ~580 | ~2,900 |

### Provider Rate-Limit Constraints (the real bottleneck)

- **FCM**: No hard documented cap on total throughput, but per-app and per-project quotas exist, and FCM applies its own server-side throttling if a single app suddenly spikes — practical safe ceiling for a well-behaved sender is in the tens of thousands of messages/sec, achieved via batching (FCM's batch send API accepts up to 500 tokens per request).
- **APNs**: HTTP/2-based, supports thousands of notifications/sec per connection, but Apple recommends connection pooling (multiple persistent HTTP/2 connections) rather than one-shot connections — connection churn itself becomes a bottleneck before raw throughput does.
- **Twilio**: SMS throughput is capped **per sending phone number** — a standard long code is limited to **~1 message/sec**; a toll-free number to ~3/sec; a short code to 30-100/sec. To send 580 SMS/sec sustained, you need a pool of **20+ short codes** (or use Twilio's Messaging Service with a number pool that load-balances automatically).
- **SES**: Sending quota is account-level (e.g., 50 emails/sec for a "production access" account by default, scalable on request to thousands/sec) — exceeding it returns `Throttling` errors.

**Conclusion**: at peak, push (101K/sec) and in-app (29K/sec) are absorbed by horizontally-scaled internal infrastructure (FCM/Kafka/workers scale linearly), but SMS (2.9K/sec) and email (11.6K/sec) are bottlenecked by *provider-side* per-account/per-number limits that don't scale just by adding more workers — this drives the per-provider token-bucket design in §4.

### Storage

- Notification record (sent log): `notification_id`, `user_id`, `channel`, `template_id`, `status`, `sent_at`, `delivered_at`, `provider_message_id`, `metadata` ≈ **500 bytes**.
- Daily volume: 2.5B records x 500 bytes = **1.25 TB/day**.
- 30-day retention (for delivery-status queries, support, debugging): 1.25 TB x 30 = **37.5 TB**.
- With replication factor 3 (Cassandra/DynamoDB): **~112.5 TB** total.
- Older data (>30 days) rolls into a cold analytics warehouse (Parquet on S3) at much lower cost.

### Dedup Cache (Redis)

- 2.5B notifications/day, each needing a dedup key with 24h TTL.
- Key (`notification_id` hash, 16 bytes) + value (small marker, ~8 bytes) + Redis overhead (~40 bytes/key) ≈ **~50 bytes/key**.
- 2.5B keys x 50 bytes ≈ **125 GB** — sized in detail in §10.

---

## 3. High-Level Architecture

```
+----------------+   +----------------+   +-------------------+
| Order Service  |   |  Chat Service  |   | Marketing/Campaign |
| (transactional)|   |  (transactional)  |   Service (broadcast) |
+--------+-------+   +--------+-------+   +---------+----------+
         |                     |                     |
         +----------+----------+----------+----------+
                    |
                    v
          +-----------------------+
          |   Notification API    |  <-- async enqueue-ack (returns 202 immediately)
          |  (validation, authn)  |
          +-----------+-----------+
                    |
                    v
          +-----------------------+
          |  Preference Service   |  <-- opt-in/out + quiet-hours-by-timezone filter
          |  (Redis cache + DB)   |
          +-----------+-----------+
                    | (passes filter)
                    v
          +-----------------------+
          | Idempotency / Dedup   |  <-- Redis SETNX on
          | Check (Redis SETNX)   |      hash(event_id+user_id+channel), 24h TTL
          +-----------+-----------+
                    | (not a duplicate)
                    v
          +-----------------------+
          |    Priority Router    |  <-- transactional vs marketing/broadcast
          +-----+----+----+----+--+
                |    |    |    |
        +-------+  +-+  +-+  +-+-------+
        |          |    |            |
        v          v    v            v
   +--------+ +--------+ +--------+ +--------+
   | Kafka  | | Kafka  | | Kafka  | | Kafka  |
   | topic: | | topic: | | topic: | | topic: |
   | push   | | sms    | | email  | | in-app |
   +---+----+ +---+----+ +---+----+ +---+----+
       |          |          |          |
       v          v          v          v
   +--------+ +--------+ +--------+ +--------+
   | Push   | | SMS    | | Email  | | In-app |
   | Worker | | Worker | | Worker | | Worker |
   | Pool   | | Pool   | | Pool   | | Pool   |
   +---+----+ +---+----+ +---+----+ +---+----+
       |          |          |          |
       | (token-bucket rate limiter + circuit breaker per provider)
       v          v          v          v
   +--------+ +--------+ +--------+ +--------+
   | FCM /  | | Twilio | | SES /  | | WS Hub /
   | APNs   | | / SNS  | | SendGrid| | Polling |
   +---+----+ +---+----+ +---+----+ +---------+
       |          |          |
       +----------+----------+
                  |
       (async delivery-receipt webhooks)
                  v
       +------------------------+
       |     Status DB          |  --> Kafka --> Data Warehouse
       | (Cassandra/DynamoDB)   |      (analytics: open rates,
       +------------------------+       delivery rates, CTR)

   Supporting services (consulted by API / Router / Workers):
   +------------------+      +----------------------+
   | Template Service |      |   Scheduler           |
   | (render +        |      |  (Redis sorted set by |
   |  localization)   |      |   send-time, or       |
   +------------------+      |   delayed Kafka topic)|
                              +----------------------+
```

**Request flow (transactional)**: Order Service publishes "order shipped" -> Notification API validates and returns `202 Accepted` with a `notification_id` (async enqueue-ack, not synchronous send) -> Preference Service checks the user opted into "order updates" on push+email and that it's not within quiet hours -> Dedup check via Redis `SETNX` -> Priority Router places it on the **transactional** partition of the `push` and `email` Kafka topics -> Channel workers pick it up, apply the per-provider token-bucket rate limiter, call FCM/SES -> provider returns a message ID -> async webhook later confirms delivery -> Status DB updated.

**Request flow (broadcast)**: Campaign Service enqueues a segment definition -> a fan-out job resolves the segment to a user list (streamed, not materialized in memory) -> each user goes through the same Preference -> Dedup -> Router pipeline, but lands on the **marketing** partition of each Kafka topic, which has its own consumer group scaled independently so a 100M-user campaign never starves transactional traffic.

---

## 4. Component Deep Dives

### 4.1 Idempotency / Dedup Check

The core invariant: **the same logical event must never produce two sends on the same channel for the same user**, even under at-least-once delivery (Kafka redelivery after a consumer-group rebalance, a producer retry after a timeout that actually succeeded, etc.).

**Key construction**: `notification_id = hash(event_id + user_id + channel)`. The `event_id` comes from the producer (e.g., Order Service's `order_id + "shipped"`), so retries of the *same business event* always hash to the same key. Combining with `user_id` and `channel` ensures a single event that fans out to push+email+SMS gets three independent dedup keys (we *want* all three channels to fire — we just don't want the *push* channel to fire twice).

**Redis SETNX with 24h TTL**: `SETNX` (SET if Not eXists) is atomic — exactly one caller wins the race even if two workers process the "same" Kafka message concurrently after a rebalance. The 24-hour TTL bounds memory usage while comfortably covering the realistic window in which a duplicate redelivery could occur (Kafka consumer-group rebalances, retry queues, and producer retries all resolve within minutes to low hours, not days).

```java
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class NotificationDedupService {

    private static final long DEDUP_TTL_SECONDS = 24 * 60 * 60; // 24h
    private final JedisPool jedisPool;

    public NotificationDedupService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Returns true if this is the FIRST time we've seen this
     * (event_id, user_id, channel) combination -> caller should proceed.
     * Returns false if it's a duplicate -> caller should skip the send.
     */
    public boolean tryClaim(String eventId, String userId, String channel) {
        String notificationId = computeNotificationId(eventId, userId, channel);
        String key = "dedup:notif:" + notificationId;

        try (var jedis = jedisPool.getResource()) {
            // SET key value NX EX 86400 -> atomic "set if not exists, with TTL"
            SetParams params = SetParams.setParams().nx().ex(DEDUP_TTL_SECONDS);
            String result = jedis.set(key, "1", params);
            // Redis returns "OK" if the key was newly set, null if it already existed
            return "OK".equals(result);
        }
    }

    public static String computeNotificationId(String eventId, String userId, String channel) {
        try {
            String raw = eventId + ":" + userId + ":" + channel;
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

**Backstop**: Redis is fast but not infinitely durable (a Redis node could fail and lose recent writes if AOF/RDB persistence lags). The Status DB (Cassandra/DynamoDB) carries a **unique constraint / conditional write** on `notification_id` as a second line of defense — `INSERT ... IF NOT EXISTS` (Cassandra LWT) or a DynamoDB `ConditionExpression: attribute_not_exists(notification_id)`. If the Redis check somehow lets a duplicate through (rare, but possible during Redis failover), the DB write fails on the second attempt and the worker treats that as "already sent, skip."

---

### 4.2 Preference Service

**Schema** (channel x category opt-in, plus quiet hours):

```sql
CREATE TABLE user_notification_preferences (
    user_id      UUID,
    category     VARCHAR(50),   -- 'order_updates', 'security', 'marketing', 'chat_messages'
    channel      VARCHAR(20),   -- 'push', 'sms', 'email', 'in_app'
    opted_in     BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (user_id, category, channel)
);

CREATE TABLE user_quiet_hours (
    user_id       UUID PRIMARY KEY,
    timezone      VARCHAR(64),   -- IANA tz name, e.g. "America/Sao_Paulo" -- NOT a raw UTC offset (see War Story 4)
    quiet_start   TIME,          -- e.g. 22:00 (10pm local)
    quiet_end     TIME,          -- e.g. 08:00 (8am local)
    enabled       BOOLEAN DEFAULT TRUE
);
```

**Filter logic**:

1. **Category opt-out check**: A `security` or `transactional_critical` category (OTP, fraud alert, payment failure) **bypasses opt-out entirely** — these are not subject to user preference, they're account-safety notifications. Everything else respects `opted_in`.
2. **Quiet hours check**: For non-critical categories, compute the user's current local time from `timezone` (an IANA name, e.g. `Asia/Kolkata`) and compare against `[quiet_start, quiet_end)`, handling the wrap-around case (e.g., 22:00-08:00 spans midnight). If inside quiet hours:
   - **Transactional but non-critical** (e.g., "your delivery is 10 minutes away"): hold and re-evaluate at `quiet_end`, OR downgrade to in-app-only (silent, no push sound/vibration).
   - **Marketing/broadcast**: defer to the next non-quiet window for that user (the Scheduler, §4.5, handles this).

```java
import java.time.*;
import java.time.temporal.ChronoUnit;

public class PreferenceFilter {

    public enum Decision { SEND_NOW, SUPPRESS, DEFER_TO_QUIET_HOURS_END }

    public Decision evaluate(UserPreferences prefs, NotificationRequest req) {
        // 1. Critical categories bypass everything
        if (req.category().equals("security") || req.category().equals("transactional_critical")) {
            return Decision.SEND_NOW;
        }

        // 2. Opt-out check
        if (!prefs.isOptedIn(req.category(), req.channel())) {
            return Decision.SUPPRESS;
        }

        // 3. Quiet hours check
        if (prefs.quietHoursEnabled()) {
            ZoneId zone = ZoneId.of(prefs.timezoneIana()); // IANA name, handles DST automatically
            LocalTime nowLocal = LocalTime.now(zone);
            LocalTime start = prefs.quietStart();
            LocalTime end = prefs.quietEnd();

            boolean inQuietWindow = isWithinWindow(nowLocal, start, end);
            if (inQuietWindow) {
                if (req.priority() == Priority.TRANSACTIONAL) {
                    // Non-critical transactional: still send, but downgrade
                    // to silent in-app only (no push alert sound).
                    return Decision.SEND_NOW; // caller downgrades channel separately
                }
                return Decision.DEFER_TO_QUIET_HOURS_END;
            }
        }
        return Decision.SEND_NOW;
    }

    /** Handles overnight windows like 22:00 -> 08:00 (wraps past midnight). */
    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        } else {
            // Wraps midnight: e.g., start=22:00, end=08:00
            return !now.isBefore(start) || now.isBefore(end);
        }
    }
}
```

Preferences are read on the hot path for every notification (145K/sec at peak), so they're cached in Redis (`prefs:{user_id}` -> serialized preference blob, TTL ~1h, invalidated on write) — a cache miss falls back to the preference DB, which is a low-write, high-read key-value store (DynamoDB or Cassandra, partitioned by `user_id`).

---

### 4.3 Priority Queueing

Transactional and marketing traffic are **physically separated** at the Kafka level — not just logically tagged:

```
Topic: notifications.push.transactional     (24 partitions, retention 24h)
Topic: notifications.push.marketing         (24 partitions, retention 6h)
Topic: notifications.sms.transactional      (8 partitions)
Topic: notifications.sms.marketing          (8 partitions)
Topic: notifications.email.transactional    (16 partitions)
Topic: notifications.email.marketing        (16 partitions)
Topic: notifications.inapp.transactional    (16 partitions)
Topic: notifications.inapp.marketing        (16 partitions)
```

Each `<channel>.<priority>` topic has its **own consumer group**, scaled independently:

- `push.transactional` consumer group: sized for low-latency p99 < 5s — over-provisioned relative to its average load so it never queues.
- `push.marketing` consumer group: sized for throughput — scales out aggressively during a campaign, scales back to near-zero between campaigns.

**Why this matters**: if a marketing campaign floods `push.marketing` with 100M messages, its consumer group's lag grows to hours — but `push.transactional`'s consumer group is on a completely separate topic with its own partitions and offsets, so a user's "your OTP is 482913" push is unaffected. Partitioning *within* a topic is by `user_id` (ensures per-user ordering — a user's notifications are processed in the order they were produced), but partitioning *across* topics by priority is what provides true isolation.

---

### 4.4 Channel Worker: Rate Limiter + Circuit Breaker per Provider

Each channel worker pool (push, SMS, email, in-app) consumes from its Kafka topics and calls the corresponding provider API. Two protections wrap every provider call:

1. **Token-bucket rate limiter** — proactively meters outbound calls to stay under the provider's documented rate limit (e.g., 1 msg/sec per Twilio long code, 50/sec for SES). This is *our* enforcement, independent of whether the provider would reject us.
2. **Circuit breaker** — reactively trips if the provider starts erroring (5xx, timeouts) so we stop hammering a degraded provider and can fail over (e.g., SES -> SendGrid). The breaker's state machine (closed -> open -> half-open) is the same one used throughout the codebase — see [`../resilience_patterns/README.md`](../resilience_patterns/README.md) for the full state-machine definition; it is not redefined here.

**Token-bucket rate limiter** (one instance per provider identity — e.g., one per Twilio sending number, one per SES account/region):

```java
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe token-bucket rate limiter.
 * One instance per "rate-limited identity" (e.g., one per Twilio
 * sending number, or one per SES account+region).
 */
public class TokenBucketRateLimiter {

    private final long capacity;          // max burst size (tokens)
    private final long refillTokensPerNs; // refill rate, expressed per nanosecond (scaled)
    private final long refillIntervalNs;  // how often `refillTokensPerNs` tokens are added

    private final AtomicLong availableTokens;
    private volatile long lastRefillTimestampNs;

    /**
     * @param capacity        max tokens the bucket can hold (burst capacity)
     * @param refillTokens    number of tokens added every refillIntervalNs
     * @param refillIntervalNs interval, in nanoseconds, between refills
     */
    public TokenBucketRateLimiter(long capacity, long refillTokens, long refillIntervalNs) {
        this.capacity = capacity;
        this.refillTokensPerNs = refillTokens;
        this.refillIntervalNs = refillIntervalNs;
        this.availableTokens = new AtomicLong(capacity);
        this.lastRefillTimestampNs = System.nanoTime();
    }

    /** Convenience factory: N permits per second, with burst = N. */
    public static TokenBucketRateLimiter perSecond(long permitsPerSecond) {
        return new TokenBucketRateLimiter(permitsPerSecond, permitsPerSecond, 1_000_000_000L);
    }

    /**
     * Attempts to acquire a single token (one provider call).
     * Returns true if allowed immediately, false if the caller
     * should back off / re-queue the message for later.
     */
    public boolean tryAcquire() {
        refillIfNeeded();
        long current;
        do {
            current = availableTokens.get();
            if (current <= 0) {
                return false;
            }
        } while (!availableTokens.compareAndSet(current, current - 1));
        return true;
    }

    private void refillIfNeeded() {
        long now = System.nanoTime();
        long last = lastRefillTimestampNs;
        long elapsed = now - last;
        if (elapsed < refillIntervalNs) {
            return;
        }
        long intervalsElapsed = elapsed / refillIntervalNs;
        long tokensToAdd = intervalsElapsed * refillTokensPerNs;
        if (tokensToAdd > 0) {
            // Best-effort CAS update of the timestamp; if another thread
            // wins, we just skip this refill cycle (next call retries).
            if (compareAndSetTimestamp(last, last + intervalsElapsed * refillIntervalNs)) {
                long updated;
                long cur;
                do {
                    cur = availableTokens.get();
                    updated = Math.min(capacity, cur + tokensToAdd);
                } while (!availableTokens.compareAndSet(cur, updated));
            }
        }
    }

    private boolean compareAndSetTimestamp(long expect, long update) {
        synchronized (this) {
            if (lastRefillTimestampNs == expect) {
                lastRefillTimestampNs = update;
                return true;
            }
            return false;
        }
    }
}
```

**Usage in the SMS worker**:

```java
public class SmsChannelWorker {

    // One bucket per Twilio sending number, ~1 msg/sec (long code limit)
    private final Map<String, TokenBucketRateLimiter> perNumberLimiters;
    private final CircuitBreaker twilioBreaker; // see resilience_patterns/README.md

    public void processMessage(NotificationMessage msg) {
        String fromNumber = numberPool.assign(msg);
        TokenBucketRateLimiter limiter = perNumberLimiters.get(fromNumber);

        if (!limiter.tryAcquire()) {
            // Provider would reject this right now -- re-queue with a
            // short delay rather than blocking the consumer thread.
            redeliveryQueue.scheduleRetry(msg, Duration.ofMillis(200));
            return;
        }

        if (twilioBreaker.isOpen()) {
            // Twilio degraded -- fail over to SNS, or hold for SMS specifically
            snsChannelWorker.send(msg);
            return;
        }

        try {
            twilioClient.sendSms(fromNumber, msg.recipientPhone(), msg.renderedBody());
            statusDb.markSent(msg.notificationId());
        } catch (TwilioApiException e) {
            twilioBreaker.recordFailure();
            redeliveryQueue.scheduleRetry(msg, backoffWithJitter(msg.attempt()));
        }
    }
}
```

The rate limiter and the breaker answer different questions: the limiter asks "*am I about to exceed my contractual quota?*" (proactive, always-on shaping), while the breaker asks "*is the provider currently healthy?*" (reactive, only matters during incidents). Both are needed — a limiter alone won't help if Twilio itself is down, and a breaker alone won't prevent gradually creeping past the per-number 1/sec cap during normal operation.

---

### 4.5 Broadcast Fan-Out Worked Example

**Scenario**: A campaign targets 100M users for a push notification ("Flash sale starts now").

1. **Segment resolution**: The Campaign Service streams the 100M `user_id`s from the segment query (e.g., a precomputed segment table or a query against the user warehouse) — never materializes the full list in memory. It writes them in batches to the `notifications.push.marketing` Kafka topic via a fan-out producer job.

2. **Throttled producer math**: The fan-out producer doesn't dump 100M messages into Kafka instantaneously — it paces itself to match what the *downstream* (FCM + workers) can sustain without tripping FCM's throttling.

   - Target throughput: **145,000 msgs/sec** (the system's overall peak design point — this campaign should not exceed the capacity already provisioned for peak load, since transactional traffic shares the same downstream FCM connection pool, just on different partitions).
   - 100,000,000 users / 145,000 msgs/sec = **~690 seconds ≈ 11.5 minutes**.

3. **Producer-side batching**: FCM's batch-send API accepts up to 500 registration tokens per HTTP request. At 145,000 msgs/sec / 500 per batch = **290 batch requests/sec** to FCM — well within FCM's tolerance for a properly batched, steady-rate sender (versus 145,000 individual HTTP/2 streams/sec, which is far more connection overhead for the same payload).

4. **Backpressure-aware pacing**: The fan-out producer monitors the `push.marketing` consumer group's lag. If lag grows beyond a threshold (e.g., 2 minutes of backlog), the producer slows its enqueue rate — there's no point producing faster than the workers (and FCM) can drain, since Kafka's disk is not infinite and a runaway producer just shifts the bottleneck from "FCM throttling us" to "Kafka disk filling up."

5. **Result**: 100M-user broadcast completes in ~11.5 minutes, FCM never sees more than its sustainable batch rate, and the `push.transactional` topic — on separate partitions with its own consumer group — continues serving OTPs and trip alerts at p99 < 5s throughout.

```
Campaign: 100,000,000 users, push channel
Target rate: 145,000 msgs/sec  (shared peak budget with transactional)
Batch size: 500 tokens/FCM request
=> 290 FCM batch requests/sec
=> 100,000,000 / 145,000 = 689.7 sec ≈ 11.5 minutes to fully drain
```

---

### 4.6 Delivery Tracking via Async Provider Webhooks

Providers don't deliver synchronously — `sendSms()` / FCM's send call returns a `provider_message_id` meaning "accepted for delivery," not "delivered to the device." Final delivery status arrives later via webhooks:

- **FCM**: delivery receipts are limited (FCM doesn't universally guarantee delivery callbacks), but client SDKs can report "notification received" / "notification opened" back to an analytics endpoint.
- **APNs**: feedback service / delivery receipts via `apns-push-type` and response status.
- **Twilio**: status callback webhook (`queued -> sent -> delivered -> failed/undelivered`) posted to a configured URL per message.
- **SES**: SNS-based notifications for `Delivery`, `Bounce`, `Complaint` events.

```
Provider Webhook Receiver (stateless HTTP endpoint, behind LB)
    |
    | 1. Validate webhook signature (per-provider HMAC/cert check)
    | 2. Extract provider_message_id + new status
    | 3. Look up notification_id via provider_message_id -> notification_id index
    | 4. Publish status-change event to Kafka (notifications.delivery_status)
    v
Status DB Consumer
    |
    | Update Status DB: notification_id -> status, delivered_at
    v
Analytics Pipeline (Kafka -> Warehouse)
    |
    | Aggregate: delivery rate, bounce rate, open rate per template/campaign
```

The **Status DB** (Cassandra/DynamoDB, partitioned by `notification_id`) is the source of truth for "what happened to notification X" — used for support tickets ("did my order-shipped email go out?"), producer-facing delivery APIs, and feeding the analytics warehouse for dashboards (campaign open rates, channel-level delivery SLAs).

---

## 5. Design Decisions & Tradeoffs

### Push (Kafka -> Worker) vs. Pull/Poll

- **Choice**: Push model — Kafka delivers messages to worker consumer groups, which actively process and call providers.
- **Reason**: Pull/poll (workers periodically querying a "pending notifications" table) adds polling-interval latency to every notification (even with a 1-second poll interval, that's 1s of latency added to every single send) and wastes resources polling empty queues during quiet periods.
- **Trade-off**: Push requires careful consumer-group sizing to avoid over- or under-provisioning, whereas a poll-based system "naturally" self-throttles (at the cost of latency). For a system with a <5s p99 transactional SLA, push wins decisively.

### Per-Channel Queues vs. a Single Unified Queue

- **Choice**: Separate Kafka topics per channel (push/sms/email/in-app), further split by priority (§4.3).
- **Reason**: A single unified queue means a slow or degraded channel's messages sit interleaved with healthy channels' messages on the same partitions — if SMS workers fall behind because Twilio is degraded, push messages stuck behind SMS messages in the same partition would also be delayed (Kafka guarantees order *within* a partition, so a stuck consumer blocks everything behind it on that partition).
- **Trade-off**: More topics/partitions to operate and monitor (8 topics x up to 24 partitions in §4.3), but this is purely an operational cost — it buys complete failure isolation between channels, which is non-negotiable given push (101K/sec peak) and SMS (2.9K/sec peak, provider-throttled to ~1/sec/number) have wildly different sustainable rates.

### Async Enqueue-Ack API vs. Synchronous Send-and-Wait

- **Choice**: The Notification API returns `202 Accepted` with a `notification_id` immediately after the message passes validation and is durably written to Kafka — it does not wait for the provider call to complete.
- **Reason**: Provider call latency (FCM/Twilio/SES round-trips) is variable and occasionally slow (100ms-2s+ during provider-side issues). A synchronous API would tie up a producer-side request thread for that entire duration, multiplied by 145K/sec at peak — completely impractical.
- **Trade-off**: Producers don't get an immediate "delivered" confirmation; they must poll the Status API or subscribe to delivery-status webhooks/events for final outcome. This is the correct trade for a system whose own NFR (§1) is "at-least-once delivery," not "synchronous delivery."

### Redis SETNX Dedup + DB Unique-Constraint Backstop vs. DB-Only Dedup

- **Choice**: Redis `SETNX` as the primary, fast dedup check, with a DB-level conditional write (`IF NOT EXISTS` / `ConditionExpression`) as a backstop.
- **Reason**: A DB-only approach means every single notification (145K/sec peak) performs a conditional write to Cassandra/DynamoDB on the hot path *before* any other processing — adding DB round-trip latency (typically 5-15ms) to every send, and putting 145K/sec of write load on the DB just for dedup checks (most of which are non-duplicates and "wasted" in some sense). Redis SETNX is sub-millisecond and handles this volume trivially.
- **Trade-off**: Redis is in-memory and (despite persistence options) has a small window where a node failure could lose a very recent dedup key, theoretically allowing a duplicate through. The DB unique-constraint backstop catches this rare case at write time — the worker's write to the Status DB fails with a conflict, and the worker treats that as "already sent."

### At-Least-Once + Idempotent Consumer vs. Kafka Exactly-Once Transactions

- **Choice**: At-least-once delivery semantics throughout (standard Kafka consumer with manual offset commits after processing), with idempotency enforced at the application layer (§4.1) rather than relying on Kafka's exactly-once transactional API (`read_committed` isolation + transactional producers).
- **Reason**: Kafka's exactly-once semantics (EOS) add meaningful overhead — transactional producers have higher latency per write, transaction coordinators add a failure mode of their own, and EOS only guarantees exactly-once *within Kafka* (e.g., consume-transform-produce loops); it does **not** extend to the side effect of "we called the FCM/Twilio/SES API" — that's an external system EOS cannot make idempotent. Since we need application-level idempotency for the *external* call regardless, the marginal benefit of also paying for Kafka EOS internally is small.
- **Trade-off**: The application-level idempotency check (Redis SETNX + DB backstop) is mandatory, not optional — if it were ever removed "for simplicity," at-least-once delivery would immediately start producing user-visible duplicate notifications (War Story 1, §9). This coupling must be documented and tested, not assumed.

---

## 6. Real-World Implementations

- **Uber's notification platform**: Uber sends notifications for trip-status changes (driver assigned, driver arriving, trip started, trip completed, fare receipt) at a scale of **millions of push notifications per day** across rider and driver apps. Uber's architecture historically separates "trip-critical" notifications (driver arrived — must reach the rider within seconds) from "informational" ones (promo codes, ratings reminders) using distinct pipelines with different latency SLAs — directly mirroring the transactional-vs-marketing partitioning in §4.3. Uber also has to handle the geographic reality that a single trip event fans out to notifications across multiple channels (rider push, driver push, SMS fallback if push fails because the app is backgrounded and the OS has killed the push connection).

- **Amazon SNS / Pinpoint**: AWS offers two complementary managed services that map almost directly onto this design's components. **SNS** (Simple Notification Service) is the pub/sub fan-out layer — a single publish to an SNS topic can fan out to SQS queues, Lambda functions, HTTP endpoints, email, and SMS subscribers, structurally similar to the Priority Router fanning out to per-channel Kafka topics in §3. **Pinpoint** (now part of AWS End User Messaging) is the higher-level "notification system" product — it adds the Preference Service layer (per-user channel preferences, quiet hours via "quiet time" settings), templating, segmentation for campaigns, and delivery analytics, which is the exact feature set of §4.2, §4.5, and §4.6. Pinpoint's documented SES integration enforces the same per-account sending-rate quotas described in §2.

- **Slack's per-channel fan-out**: When a message is posted to a Slack channel with N members, Slack must determine, for each member, whether to send a push notification (based on that member's notification preferences — "all messages," "mentions only," "nothing," each possibly overridden per-channel) and whether they're in a "do not disturb" window (Slack's quiet-hours feature, configurable per user with timezone awareness). For a channel with thousands of members, this is a fan-out of one event into thousands of independent preference evaluations — structurally identical to §4.2's per-user Preference Service filter, just triggered by a chat event instead of an order event. Slack also has to dedupe: a user active on both desktop and mobile shouldn't get a push notification for a message they're already actively viewing in the desktop client — a presence-aware variant of the dedup problem in §4.1.

- **Netflix's new-content push notifications**: Netflix sends "New episode available" or "New season of [show] is here" push notifications to subscribers who've watched related content — a recommendation-driven broadcast that can target tens of millions of users for a popular show's season premiere. Netflix's architecture throttles these sends to avoid overwhelming APNs/FCM during a single release window, and — notably — uses delivery-time optimization, sending each user's notification near *their* historically-observed app-open time rather than blasting all 50M+ users simultaneously, which is a more sophisticated variant of the quiet-hours-aware Scheduler in §3/§4.2 (instead of just avoiding a "do not send" window, it actively targets a "best send" time per user).

---

## 7. Technologies & Tools

| Component | Technology | Why |
|---|---|---|
| Event backbone | Kafka | Durable, partitioned, per-channel/per-priority topic isolation (§4.3); absorbs broadcast bursts without overwhelming workers |
| Dedup cache | Redis (Cluster) | Sub-millisecond `SETNX` for the idempotency check (§4.1); also caches user preferences |
| Preferences cache | Redis | `prefs:{user_id}` cached blob, ~1h TTL, avoids a DB round-trip on every send |
| Notification log / status DB | Cassandra or DynamoDB | High write throughput (145K/sec peak), partitioned by `notification_id`, conditional writes for dedup backstop (§4.1) |
| Push providers | FCM (Android), APNs (iOS) | Industry-standard mobile push gateways; batch-send APIs (§4.5) |
| SMS providers | Twilio, AWS SNS (SMS) | Twilio for primary with number pools; SNS as a secondary/failover path |
| Email providers | AWS SES, SendGrid | SES primary (cost-efficient at scale), SendGrid as failover (War Story 2) |
| Searchable history | Elasticsearch | Support and ops teams search "did user X receive notification about Y" across the notification log |
| Scheduler | Redis sorted set (ZADD by send-timestamp) or delayed Kafka topic | Holds scheduled/delayed sends until their send-time, then re-injects into the priority router |
| Analytics pipeline | Kafka -> Spark/Flink -> Data Warehouse (Snowflake/BigQuery) | Aggregates delivery rates, open rates, bounce rates per template/campaign |

---

## 8. Operational Playbook

### Monitoring (RED method per channel)

Following the RED method (Rate, Errors, Duration) — see [`../observability/README.md`](../observability/README.md) for the full framework — applied per channel and per priority tier:

| Signal | What it measures | Why it matters |
|---|---|---|
| **Rate**: enqueue rate vs. send rate per `<channel>.<priority>` topic | Is the producer outpacing the consumer? | A growing gap means queue lag is building (consumer-group lag metric) |
| **Errors**: provider error rate (4xx/5xx from FCM/Twilio/SES) | Is a provider degraded or are we sending malformed requests? | Sudden spike usually means a provider incident or a bad deploy (e.g., expired API credentials) |
| **Duration**: end-to-end latency, event-published -> provider-accepted | Are transactional notifications meeting the <5s p99 SLA? | Latency creep often precedes a queue-lag explosion |

### Alert Thresholds

| Condition | Threshold | Severity |
|---|---|---|
| `push.transactional` / `email.transactional` / `sms.transactional` consumer-group lag | > 5 minutes of backlog | **Page** (transactional SLA at risk) |
| Any provider error rate (5xx + timeouts) | > 5% over 5 minutes | **Ticket** (investigate; may need failover) |
| Any provider error rate | > 25% over 2 minutes | **Page** (likely provider outage — trigger failover runbook) |
| Dedup Redis cluster memory usage | > 80% of provisioned capacity | **Ticket** (plan shard addition before TTL-driven eviction starts dropping legitimate dedup keys early) |
| `*.marketing` consumer-group lag | > 2 hours | **Ticket** (campaign SLA degraded, but not user-facing-critical) |
| Webhook receiver error rate (delivery-status updates failing) | > 10% over 10 minutes | **Ticket** (Status DB going stale, support queries will show wrong status) |

### Runbook: Provider Outage (e.g., SES Down)

1. **Detect**: SES error rate alert fires (>25% 5xx/timeouts over 2 minutes) on the `email.transactional` and `email.marketing` workers.
2. **Confirm**: Check AWS Service Health Dashboard / SES status page to confirm it's a provider-side incident, not a credentials/config issue on our side.
3. **Failover**: Flip the email channel worker's provider-selection config from SES to SendGrid (pre-configured secondary provider, with its own token-bucket limiter sized to SendGrid's quotas — see War Story 2 for why this must be pre-provisioned, not improvised). This is a config/feature-flag change, not a deploy — target: <5 minutes to execute.
4. **Verify**: Confirm `email.transactional` consumer-group lag stabilizes and provider error rate on SendGrid stays low.
5. **Backfill**: Messages that failed during the SES outage were re-queued by the retry mechanism (§4.4) — confirm they drain through SendGrid without hitting SendGrid's own rate limits (the token-bucket for SendGrid must be sized for "normal load + SES backlog" temporarily).
6. **Recovery**: Once SES recovers, decide whether to fail back immediately (lower cost) or stay on SendGrid until end of business day (avoid mid-day provider switches causing additional noise).

### Runbook: Queue Lag Spike on a Transactional Topic

1. **Detect**: `push.transactional` consumer-group lag > 5 minutes — page fires.
2. **Triage — is it a worker problem or a downstream problem?**
   - Check worker pool health (CPU, memory, pod restarts). If workers are healthy but lag is growing, the bottleneck is downstream (FCM/APNs).
   - Check the token-bucket limiter's reject rate (§4.4) — if it's near 100%, the limiter is correctly throttling but the *configured* limit may be too low relative to actual provider capacity, or the provider itself is degraded.
3. **Mitigate**:
   - If workers are under-provisioned: scale out the consumer group (Kafka partitions already provisioned for headroom — §10 — so this is a quick horizontal scale, not a repartitioning operation).
   - If the provider is degraded: check circuit-breaker state; if open, confirm failover path (e.g., FCM degraded has no clean failover for Android push — mitigation here is queueing and waiting, with user-facing impact communicated).
4. **Verify**: Lag trending back toward zero; p99 latency back under 5s.

---

## 9. Common Pitfalls & War Stories

### War Story 1: Triplicate "Your Order Has Shipped" Emails During a Kafka Rebalance

**What happened**: The email worker consumer group had 16 instances. During a routine deploy, a rolling restart triggered a consumer-group rebalance. The system was running **at-least-once Kafka consumption with offsets committed only after successful processing**, but with **no application-level idempotency check** — the assumption was "Kafka redelivery is rare, and resending an email isn't a big deal."

During the rebalance, three different consumer instances ended up processing the same message for the same "order #48213 shipped" event before offsets were committed and partition ownership stabilized — each one called SES successfully and each call succeeded (SES doesn't dedupe on our behalf). The result: **the customer received the same "Your order has shipped!" email three times within about 90 seconds**, each with a working tracking link. Customer support tickets ("is something wrong with my order? I got 3 emails") spiked for about 20 minutes — the duration of the rebalance plus the in-flight message backlog that had built up during it.

**Broken approach** (what was running):
```java
// BROKEN: no dedup -- at-least-once delivery means this can run
// multiple times for the "same" logical event during a rebalance.
public void processOrderShippedEvent(OrderShippedEvent event) {
    String renderedBody = templateService.render("order_shipped", event.toTemplateParams());
    sesClient.sendEmail(event.userEmail(), "Your order has shipped!", renderedBody);
    // offset committed after this returns -- but if THIS instance
    // crashes or loses partition ownership AFTER sendEmail() succeeds
    // but BEFORE the offset commit, another instance redelivers and
    // resends.
}
```

**Fix**: Added the Redis `SETNX` dedup check from §4.1 as the very first step, keyed on `hash(event_id="order_48213_shipped" + user_id + channel="email")`:

```java
// FIXED: claim the dedup key BEFORE calling the provider.
public void processOrderShippedEvent(OrderShippedEvent event) {
    boolean isFirstAttempt = dedupService.tryClaim(
        event.eventId(), event.userId(), "email");

    if (!isFirstAttempt) {
        // Already sent (or currently being sent by another instance).
        // Safe to skip -- commit the offset and move on.
        return;
    }

    String renderedBody = templateService.render("order_shipped", event.toTemplateParams());
    sesClient.sendEmail(event.userEmail(), "Your order has shipped!", renderedBody);
}
```

With the 24-hour-TTL `SETNX` claim taken *before* the SES call, the second and third redelivered copies of the same message find the key already claimed and skip the send entirely. The DB-level conditional-write backstop (§4.1) was added in the same change for defense-in-depth against a Redis-layer failure.

**Lesson**: "At-least-once + no idempotency = duplicates will happen, not might happen" — the only question is when (a rebalance, a retry, a timeout-that-actually-succeeded). Idempotency is not an optimization; it's a correctness requirement that must ship with the very first version of any at-least-once consumer.

---

### War Story 2: 50M-User Broadcast Got Silently Throttled by FCM

**What happened**: A marketing campaign targeting 50M Android users for a "Big Sale" push notification was launched. The fan-out producer (a batch job) had no rate limiting of its own — it read all 50M user IDs from the segment table and published to the `push.marketing` Kafka topic as fast as the producer client could serialize and send, which on the provisioned hardware was on the order of **300,000+ messages/sec** for short bursts.

The `push.marketing` consumer group drained this backlog quickly and called FCM's batch-send API at a correspondingly high rate. **FCM began silently dropping a portion of the batch-send requests** — not with a hard error that would trip the circuit breaker, but with partial-failure responses buried inside individual entries of the batch response (some tokens in a 500-token batch reported success, others reported a transient `UNAVAILABLE` with no automatic retry on our side, since the worker's error-handling only checked the top-level HTTP status, which was `200 OK`).

The campaign's actual delivery rate, measured via client-side "notification received" telemetry over the following hours, came in at roughly **68% of the targeted 50M** — about 16M users never received the notification, and there was no alert, because from the worker's point of view, every batch request "succeeded" (`200 OK` at the HTTP level).

**Broken approach** (what was running):
```java
// BROKEN: no producer-side throttling, and only checks the
// top-level HTTP status -- ignores per-message results inside
// a batch response.
public void sendBatch(List<PushMessage> batch) {
    FcmBatchResponse response = fcmClient.sendBatch(batch); // up to 500 messages
    if (response.httpStatus() == 200) {
        statusDb.markSentBatch(batch); // marks ALL 500 as sent, regardless
                                        // of per-message results inside response
    } else {
        retryQueue.enqueue(batch);
    }
}
```

**Fix**: Two changes, addressing producer-side and worker-side respectively:

1. **Producer-side token-bucket throttling**: The fan-out producer for broadcasts now uses the same `TokenBucketRateLimiter` (§4.4) — capped at the system's overall peak design budget (145,000 msgs/sec, shared with transactional traffic via Kafka's partition-level isolation, §4.3) — and additionally watches `push.marketing` consumer-group lag, backing off further if lag exceeds 2 minutes (the backpressure-aware pacing from §4.5).

2. **Worker-side per-message result handling**:
```java
// FIXED: inspect each per-message result inside the batch response;
// only mark individually-successful messages as sent, and re-queue
// the failed ones with backoff.
public void sendBatch(List<PushMessage> batch) {
    FcmBatchResponse response = fcmClient.sendBatch(batch);

    for (int i = 0; i < batch.size(); i++) {
        FcmMessageResult result = response.results().get(i);
        PushMessage msg = batch.get(i);

        if (result.isSuccess()) {
            statusDb.markSent(msg.notificationId(), result.providerMessageId());
        } else if (result.isRetryable()) { // e.g., UNAVAILABLE, INTERNAL
            retryQueue.scheduleRetry(msg, backoffWithJitter(msg.attempt()));
        } else { // e.g., UNREGISTERED -- invalid/expired token
            statusDb.markFailed(msg.notificationId(), result.errorCode());
            deviceTokenService.markTokenInvalid(msg.deviceToken());
        }
    }
}
```

**Lesson**: "200 OK at the HTTP level" and "all messages in this batch were delivered" are **not the same claim** for any provider with a batch API — FCM, SES (`SendBulkTemplatedEmail`), and Twilio's bulk APIs all return per-item results that must be inspected individually. Combined with producer-side throttling that respects the *system's* sustainable rate (not just "how fast can our Kafka producer serialize messages"), this closed the gap from 68% to >99.5% measured delivery on subsequent campaigns of similar size.

---

### War Story 3: Notification Storm During an Incident (Producer-Side Mirror of Alert Fatigue)

**What happened**: During a 20-minute partial outage of the Order Service's database, **every microservice that depended on order data** (Inventory, Shipping, Recommendations, Fraud Detection, and three others) independently detected the failure and — each per its own error-handling logic — fired a "something is wrong" notification: some to on-call engineers via the same notification system (treating PagerDuty-style alerts as just another "channel"), and some, more alarmingly, **directly to end users** ("We're having trouble processing your order, please try again"), because a few services interpreted "I can't reach the Order Service" as "the user's specific order has a problem" and notified the affected user.

Within about 90 seconds of the database issue starting, the `push.transactional` and `email.transactional` topics received a burst of **over 2 million notifications** — far exceeding the actual number of users with in-flight orders at that moment (most recipients had no active order at all; the notifications were false positives generated by services misinterpreting a shared dependency's failure as a per-user problem).

**This is the producer-side mirror of the alert-fatigue problem**: where [`../observability/README.md`](../observability/README.md)'s War Story 2 describes *internal* alert fatigue (340 alert rules, 22 pages/shift, the real page buried in noise), this incident is the same root cause — **causes treated as if they were independently-actionable signals, multiplied across many producers, with no shared circuit-breaker or coordination** — except the "page" being sent is a *user-facing notification*, and the "on-call engineer" who misses the signal in the noise is the *customer*, who now has 2M people receiving an alarming, mostly-false "your order has a problem" message.

**Mitigation** (cross-referencing rather than duplicating the observability fix): the underlying fix is the same pattern as the observability War Story 2 fix — alert (or in this case, notify) on the **symptom that directly affects the user** (their specific order is delayed/failed), not on the **shared-dependency cause** (Order Service DB is unreachable). Concretely: each producer service's error-handling was changed so that a *dependency* failure (Order Service unreachable) triggers an internal on-call page (via the existing alerting pipeline, not the user-notification system) and a circuit breaker that **suppresses user-facing notifications from that producer** until the dependency recovers — rather than each producer independently deciding "I should tell the user about this." A shared "is this a systemic incident?" flag (set by the on-call/incident-management tooling) is checked by the Notification API itself as an additional backstop: during a declared incident, non-critical user-facing notifications from affected producers are held (not dropped) and replayed once the incident is resolved and the root cause is confirmed not to be user-specific.

**Lesson**: A notification system's dedup and rate-limiting protects against *duplicate* sends of the *same* event, but does nothing to protect against *many different (false-positive) events* generated by *many different producers* during a shared-dependency outage — that requires the producers themselves to distinguish "my dependency is down" (an internal/ops concern) from "this specific user's request failed" (a user-facing concern), exactly as observability tooling must distinguish cause-based alerts from symptom-based alerts.

---

### War Story 4: DST Transition Caused 3am Notifications Across an Entire Region

**What happened**: The `user_quiet_hours` table (§4.2) was originally designed with a `utc_offset_minutes` integer column (e.g., `-180` for UTC-3) instead of an IANA timezone name — a seemingly simpler representation that avoided pulling in a timezone database dependency. For users in Brazil (which historically observed DST, shifting between UTC-3 and UTC-2 depending on time of year — and at the time this bug occurred, Brazil's DST rules had also been changed/abolished and reinstated more than once by government decree, a notoriously unstable case for timezone handling), the stored `utc_offset_minutes` value was set once at account-creation time and never updated.

When Brazil's DST rules shifted (the local UTC offset changed from what was stored), the quiet-hours calculation — `current_utc_time + stored_offset_minutes`, compared against `[quiet_start, quiet_end)` — was now off by one hour. For users whose quiet hours were configured as `22:00-08:00`, the off-by-one-hour calculation meant the system believed it was, say, 7am (just past `quiet_end`) when it was actually 6am local time — **marketing notifications that were supposed to be held until 8am started going out at 6-7am** for an entire region's worth of users, immediately following the DST transition, until each user happened to update their preferences (which recalculated and stored a fresh offset) or an engineer noticed the support-ticket spike ("why am I getting promotional notifications at 6am?!").

**Broken approach** (what was stored and computed):
```sql
-- BROKEN: raw UTC offset, captured once, goes stale across DST transitions
CREATE TABLE user_quiet_hours (
    user_id            UUID PRIMARY KEY,
    utc_offset_minutes INT,     -- e.g., -180 for UTC-3 -- frozen at creation time
    quiet_start        TIME,
    quiet_end          TIME
);
```
```java
// BROKEN: applies a possibly-stale fixed offset
Instant nowUtc = Instant.now();
LocalTime userLocalTime = LocalTime.ofInstant(
    nowUtc.plus(Duration.ofMinutes(prefs.utcOffsetMinutes())), ZoneOffset.UTC);
// If Brazil's actual current offset has changed since utc_offset_minutes
// was last written, userLocalTime is now wrong by exactly the DST delta.
```

**Fix**: Store the **IANA timezone name** (e.g., `America/Sao_Paulo`), not a raw offset — and compute the offset **at send-time**, using the platform's timezone database, which is updated independently of application deploys when DST rules change (the `tzdata` package is updated by the OS/JVM vendor):

```sql
-- FIXED: IANA timezone name -- the offset is derived at query time,
-- always correct even if DST rules change after this row was written.
CREATE TABLE user_quiet_hours (
    user_id     UUID PRIMARY KEY,
    timezone    VARCHAR(64),  -- e.g., "America/Sao_Paulo"
    quiet_start TIME,
    quiet_end   TIME
);
```
```java
// FIXED: ZoneId.of() + LocalTime.now(zone) resolves the CURRENT
// offset for this instant, including any DST rules in effect today --
// even if those rules changed after the user's row was written.
ZoneId zone = ZoneId.of(prefs.timezoneIana()); // "America/Sao_Paulo"
LocalTime userLocalTime = LocalTime.now(zone);
```

The fix also included a one-time backfill: for existing users with only a stored `utc_offset_minutes`, the migration mapped each user's *country/region* (already known from account data) to its primary IANA timezone (acknowledging this is imprecise for countries spanning multiple timezones, but vastly more correct than a frozen offset, and users could subsequently correct it via settings).

**Lesson**: A UTC offset is a *derived value*, valid only for a specific instant — it is never safe to store as if it were a stable property of a user. The IANA timezone *name* is the stable property; always store the name and compute the offset (including DST) at the moment it's needed, using the standard library's timezone database (`java.time.ZoneId`), which tracks real-world legislative timezone changes via routine `tzdata` updates independent of application releases.

---

## 10. Capacity Planning

### Kafka Partition Count

- Peak system throughput: **145,000 msgs/sec** (across all channels combined).
- A single Kafka partition, with reasonably-sized messages (notification payloads are small — a few hundred bytes to ~1KB for templated content with parameters) and a consumer doing modest processing (preference lookup from cache, dedup check, provider call dispatch), sustains roughly **~10,000 msgs/sec**.
- 145,000 / 10,000 ≈ **14.5**, round up to **15** as the bare minimum.
- **Provisioned: 24 partitions** per high-volume topic (push being the largest single channel at ~101K/sec peak alone already needs ~10-11 partitions on its own) — the extra headroom above the bare minimum absorbs:
  - Uneven partition assignment (consumer-group rebalances don't always distribute load perfectly).
  - Future growth (DAU growth, increased notifications/user/day) without a disruptive repartitioning operation.
  - Hot-key skew if `user_id`-based partitioning ever produces a "celebrity user" hotspot (rare for notifications, since each user generates roughly proportional notification volume, but provisioned for defensively).

### Worker Pool Sizing (Little's Law)

Per Little's Law, `L = λ * W` (number of in-flight items = arrival rate x average time in system). For the `push.transactional` worker pool targeting p99 < 5s:

- Arrival rate λ ≈ 20,250/sec average (push transactional share of 29K/sec average — see §2's channel mix applied to transactional volume), peaking at ~101,250/sec for push overall (transactional portion scales similarly).
- Average processing time W per message (preference cache lookup + dedup check + FCM call) ≈ 50-150ms under normal conditions.
- At λ = 100,000/sec and W = 0.1s: L = 100,000 x 0.1 = **10,000 messages in flight** at any instant during peak.
- If each worker thread handles one in-flight message at a time (synchronous provider call per thread) and a worker pod runs ~200 threads, that's `10,000 / 200 = 50` worker pods needed at peak — sized with headroom (e.g., 65-70 pods) to keep W from creeping upward as utilization approaches 100% (queueing theory: latency degrades sharply as utilization -> 1; see [`../resilience_patterns/README.md`](../resilience_patterns/README.md) for the broader treatment of load-shedding and backpressure when a pool approaches saturation).

### Redis Dedup Cache Footprint

- 2.5B notifications/day, each holding a dedup key for its 24h TTL — at steady state, the cache holds roughly one full day's worth of keys (~2.5B keys) at any given moment (older keys expiring as new ones are added).
- Per-key footprint: a 64-character SHA-256 hex digest as the key (~64 bytes) + Redis's per-key overhead (object header, hash table entry — typically 50-70 bytes for small string values in Redis) + a tiny value (~8 bytes) ≈ **~50 bytes/key effective** when accounting for Redis's internal encoding optimizations for small keys (using `OBJ_ENCODING_EMBSTR` and shared integer values where possible) — consistent with the back-of-envelope figure used in §2.
- Total: 2.5B keys x 50 bytes ≈ **125 GB**.
- **A single Redis instance (even a large one, 256GB+ RAM) could theoretically hold this**, but a single instance is unacceptable: it's a single point of failure for *every* dedup check across *all* channels and priorities, and 145K/sec of `SETNX` calls at peak against one instance risks CPU-bound throughput limits (Redis is single-threaded for command execution).
- **Provisioned: a sharded Redis Cluster** — e.g., 8-16 shards, each holding ~8-16GB of dedup keys, distributing both the memory footprint and the 145K/sec command rate (roughly 9K-18K SETNX/sec per shard) comfortably within a single Redis instance's single-threaded command-processing capacity (Redis comfortably handles 100K+ simple ops/sec per instance, so even the lower shard count leaves significant headroom).

---

## 11. Interview Discussion Points

**Q: How do you prevent duplicate notifications when your consumers use at-least-once delivery?**
A: Compute a deterministic `notification_id = hash(event_id + user_id + channel)` so that redeliveries of the "same" logical event always produce the same key, then use Redis `SETNX` with a 24-hour TTL as an atomic "claim" — only the caller that successfully sets the key proceeds to call the provider; everyone else (redelivered copies after a rebalance, retried producer calls) sees the key already exists and skips the send. Back this with a DB-level conditional write (`IF NOT EXISTS` / `ConditionExpression`) on the same `notification_id` as a second line of defense in case a Redis failover loses a very recent key. War Story 1 (§9) shows what happens without this: triplicate "order shipped" emails during a routine consumer-group rebalance.

**Q: How do you implement quiet hours correctly across timezones, and what's the most common bug?**
A: Store each user's timezone as an **IANA timezone name** (e.g., `America/Sao_Paulo`), never as a raw UTC offset — then compute `LocalTime.now(ZoneId.of(timezone))` at send-time and compare against the user's `[quiet_start, quiet_end)` window (handling the midnight-wraparound case for windows like `22:00-08:00`). The most common bug is storing a frozen UTC offset captured at account-creation time: when DST rules change (or a country changes its DST policy, as Brazil has done multiple times), the stored offset goes stale and quiet-hours calculations become off by exactly the DST delta — War Story 4 (§9) describes this causing 6am marketing notifications across an entire region. The IANA name is the stable property; the offset is always a derived, point-in-time value.

**Q: A major push provider (FCM or APNs) goes down. What happens to your system, and how do you respond?**
A: The circuit breaker for that provider (per §4.4, sharing the state machine defined in `resilience_patterns`) trips to open after the error-rate threshold is crossed, and the worker pool stops sending requests to the dead provider — preventing the thread/connection pool from being consumed by calls that will only time out. Unlike email (SES -> SendGrid failover) or SMS (Twilio -> SNS failover), there is typically **no clean failover for a specific platform's push provider** — if FCM is down, you cannot deliver Android push notifications through APNs. The mitigation is queueing: messages accumulate in `push.transactional`/`push.marketing` (Kafka retains them durably), the breaker periodically half-opens to test recovery, and once FCM recovers, the backlog drains — with the broadcast fan-out math (§4.5) and backpressure-aware pacing ensuring the backlog drain itself doesn't create a secondary throttling event.

**Q: Why push (Kafka -> worker) instead of a poll-based delivery model?**
A: A poll-based model — workers periodically querying a "pending notifications" table — adds the polling interval directly to delivery latency for every single notification (even a 1-second poll interval means every transactional notification incurs up to 1s of pure waiting before processing even starts), and wastes resources continuously polling during quiet periods. A push model (Kafka delivering to consumer groups) has near-zero added latency from the queue itself — workers are notified the instant a message is available — which is essential for the <5s p99 transactional SLA (§1). The trade-off is that push requires more careful consumer-group capacity planning (§10), since an under-provisioned consumer group accumulates lag rather than "naturally" self-throttling the way a slow poller would.

**Q: How do you prioritize transactional notifications over marketing/broadcast traffic?**
A: Physical separation at the Kafka topic level — `<channel>.transactional` and `<channel>.marketing` are different topics with different partition counts, different retention settings, and crucially **different consumer groups that scale independently** (§4.3). This isn't just a priority field on a shared queue (which would still let a transactional message get stuck behind millions of marketing messages on the same partition, since Kafka guarantees in-partition ordering) — it's complete infrastructure isolation, so a 100M-user marketing campaign growing `push.marketing` lag to hours has zero effect on `push.transactional`'s <5s p99.

**Q: How would you scale a notification to 100M users without tripping FCM's or Twilio's rate limits?**
A: Throttle at the producer (fan-out) side using the same token-bucket rate limiter that guards individual provider calls (§4.4), capped at the system's overall sustainable peak rate (145,000 msgs/sec in this design) — and additionally make the fan-out producer backpressure-aware, watching the target consumer group's lag and slowing down if a backlog starts building (§4.5). For FCM specifically, batch sends (up to 500 tokens/request) reduce the request rate by 500x relative to individual sends. The math: 100,000,000 users / 145,000 msgs/sec ≈ 11.5 minutes to fully drain — comfortably "well under an hour" per the NFR (§1), without exceeding any provider's sustainable rate. War Story 2 (§9) shows the failure mode when this throttling is absent: ~32% of a 50M-user campaign was silently dropped by FCM with no top-level error.

**Q: What's the trade-off between at-least-once delivery with idempotent consumers vs. Kafka's exactly-once semantics (EOS)?**
A: EOS adds real overhead (transactional producers have higher per-write latency, transaction coordinators are an additional failure mode) and only guarantees exactly-once *within Kafka itself* — it does not, and cannot, make the external side effect (calling FCM/Twilio/SES) idempotent, since those are systems Kafka has no visibility into. Since application-level idempotency (§4.1) is therefore mandatory regardless of whether you use EOS, the marginal value of also paying EOS's overhead is small — at-least-once + a Redis `SETNX`-based idempotency check achieves the same user-visible guarantee (no duplicate sends) at lower cost and complexity.

**Q: How do you implement scheduled or delayed sends (e.g., "send 24 hours after signup" or "send at 9am user-local-time")?**
A: Two common mechanisms: (1) a Redis sorted set keyed by send-timestamp (`ZADD scheduled_notifications <unix_ts> <notification_payload>`), with a background poller that periodically `ZRANGEBYSCORE`s for due items and re-injects them into the priority router; or (2) a "delayed Kafka topic" pattern, where a message is written with a target-send-time header and a consumer either holds it (if the delay is short) or re-publishes it to a series of topics with increasing TTLs (tiered delay queues) until it's due. For "9am user-local-time," the scheduled timestamp is computed once at enqueue time using the user's IANA timezone (§4.2) — converting "9am tomorrow in `Asia/Kolkata`" to a UTC instant — so the Scheduler itself only ever deals in absolute UTC timestamps.

**Q: How do you track delivery status when providers report it asynchronously, sometimes minutes later?**
A: A stateless webhook receiver endpoint accepts provider callbacks (Twilio status callbacks, SES/SNS delivery/bounce/complaint notifications, APNs feedback), validates the provider's signature, looks up the internal `notification_id` via a `provider_message_id -> notification_id` index, and publishes a status-change event to a Kafka topic (§4.6). A consumer updates the Status DB (Cassandra/DynamoDB, partitioned by `notification_id`) with the new status and timestamp. This decouples "the provider told us something" from "our system of record reflects it," so a slow or bursty webhook stream (e.g., a campaign's worth of delivery receipts arriving over several minutes) doesn't block anything on the send path.

**Q: A user opts out of marketing notifications while a 100M-user campaign is mid-flight. What happens to messages already in the pipeline for that user?**
A: The Preference Service check (§4.2) happens once, early in the pipeline, before the message is placed on a channel-specific Kafka topic — so if the opt-out is recorded *before* that user's message reaches the Preference Service step, it's correctly suppressed. However, if the user's message has *already passed* the Preference check and is sitting in `push.marketing` waiting for a worker, the in-flight message will still be sent — the system does not retroactively scan queues for opted-out users (that would require an expensive scan of potentially millions of in-flight messages for a single user's opt-out). This is an accepted trade-off: the opt-out takes effect for all *future* notifications immediately (preference cache invalidation, §4.2), but a small window of "in-flight at the moment of opt-out" messages may still be delivered — generally acceptable for marketing content, and explicitly *not* acceptable to leave ambiguous for transactional/security categories (which bypass opt-out entirely, §4.2, so this scenario doesn't arise for them).

**Q: How do SLAs differ between transactional and marketing notifications, and how does that show up in the architecture?**
A: Transactional notifications (OTP, order updates, security alerts) have a **latency SLA** (<5s p99, §1) because they're time-sensitive and directly tied to a user action the user is actively waiting on. Marketing/broadcast notifications have a **throughput SLA** (100M users in well under an hour, §1) but no meaningful per-message latency requirement — nobody notices if their promotional notification arrives 10 minutes after a campaign "starts." This difference drives the entire topic/consumer-group separation in §4.3: transactional consumer groups are over-provisioned relative to their average load specifically to protect the latency SLA (never queue), while marketing consumer groups are sized for sustained throughput and are allowed to build temporary backlogs measured in minutes-to-hours without violating any SLA.

**Q: How would you test a system like this — what's hard to test, and how do you approach it?**
A: Three layers: (1) **Unit/component tests** for the dedup logic, preference filter (especially DST/timezone edge cases — War Story 4 is exactly the kind of bug a property-based test sweeping across DST transition dates would catch), and rate limiter (verify token refill math under simulated clock advancement). (2) **Integration tests against provider sandboxes** — FCM, Twilio, and SES all offer test/sandbox modes that accept requests without actually delivering to devices/phones/inboxes, letting you verify the full pipeline (API -> Kafka -> worker -> provider call -> webhook -> Status DB) end-to-end. (3) **Load/chaos testing for the failure modes in §9** — specifically, inject a Kafka consumer-group rebalance mid-load-test and verify zero duplicate sends (catches War Story 1 regressions), and run a broadcast-scale load test against a provider sandbox with artificially low rate limits to verify the token-bucket + backpressure logic correctly throttles rather than silently dropping (catches War Story 2 regressions). The hardest part to test realistically is provider-side partial failures (a batch response where some items succeed and others fail) — this requires either a sophisticated sandbox/mock that can simulate partial-batch failures, or recorded real provider responses replayed in tests.

**Q: Walk through what happens, end-to-end, for a single "order shipped" transactional notification.**
A: (1) Order Service publishes an `OrderShippedEvent` with a stable `event_id` (e.g., `order_48213_shipped`) to the Notification API, which validates and returns `202 Accepted` after durably writing to an intake topic. (2) The Preference Service checks whether this user has opted into "order updates" on push and email, and whether it's currently within their quiet hours (it's a non-critical transactional category, so quiet hours can defer or downgrade it, §4.2). (3) Assuming it passes, the Idempotency check computes `hash(event_id + user_id + "push")` and `hash(event_id + user_id + "email")` and claims both via Redis `SETNX` (§4.1). (4) The Priority Router places both messages on the `*.transactional` partitions of `push` and `email` topics respectively (§4.3). (5) The Template Service renders the localized "Your order has shipped! Track it here: ..." text for both channels. (6) Each channel worker checks its token-bucket limiter and circuit breaker (§4.4) before calling FCM (push) and SES (email). (7) Both calls return provider message IDs; the Status DB records `sent`. (8) Minutes later, async webhooks from FCM (notification-received telemetry) and SES (delivery notification via SNS) update the Status DB to `delivered`. Total time from step 1 to step 7: well under the 5-second p99 target.

### Numbers to Remember

- 500M DAU x 5 notifications/user/day = 2.5B/day ≈ 29K/sec average, **145K/sec peak** (5x).
- Channel mix: ~70% push, ~20% in-app, ~8% email, ~2% SMS.
- Twilio long code: ~1 SMS/sec/number — need 20+ numbers (or a number pool) for 580 SMS/sec sustained.
- FCM batch API: 500 tokens/request -> 100M-user broadcast at 145K/sec ≈ **11.5 minutes**.
- Dedup cache: 2.5B keys x ~50 bytes x 24h TTL ≈ **125 GB** -> sharded Redis Cluster (8-16 shards).
- Notification log: 2.5B/day x 500 bytes x 30-day retention ≈ 37.5 TB (x3 replication ≈ 112.5 TB).
- Kafka partitions: 145K/sec / ~10K msgs/sec/partition ≈ 15 minimum -> **24 provisioned** for headroom.
- Transactional SLA: <5s p99 end-to-end. Broadcast SLA: 100M users in well under an hour.

### Cost Estimate (rough order of magnitude)

- **Provider fees dominate**: at $0.0075/SMS and 50M SMS/day (2% of 2.5B), SMS alone is ~$375K/day -> this is why SMS is reserved for high-priority/transactional only, with push as the default channel.
- **Push (FCM/APNs) is effectively free** at this volume, so the infrastructure cost is dominated by Kafka, Redis, and the worker fleet -> low-to-mid six figures per year, an order of magnitude below the SMS provider bill.
- **Takeaway for interviews**: channel selection is a cost decision as much as a UX one — defaulting marketing/broadcast traffic to push and reserving SMS/email for transactional or opt-in cases is the single biggest lever on the provider bill.

---

## Cross-References

- **Per-channel Kafka topic isolation and consumer-group scaling (§4.3)** -> [`../message_queues/README.md`](../message_queues/README.md)
- **Token-bucket rate limiting and provider-quota enforcement (§4.4)** -> [`../rate_limiting/README.md`](../rate_limiting/README.md)
- **Circuit breaker state machine, retries with backoff, load shedding (§4.4, §8, §10)** -> [`../resilience_patterns/README.md`](../resilience_patterns/README.md)
- **RED-method monitoring, SLI/SLO framing, and the alert-fatigue war story referenced in §9** -> [`../observability/README.md`](../observability/README.md)
- **Async messaging patterns (pub/sub, DLQ, outbox) underlying the producer/consumer pipeline** -> [`../../backend/messaging_patterns/README.md`](../../backend/messaging_patterns/README.md)
- **Kafka internals (partitions, consumer groups, offset management, rebalancing)** -> [`../../backend/kafka_deep_dive/README.md`](../../backend/kafka_deep_dive/README.md)
