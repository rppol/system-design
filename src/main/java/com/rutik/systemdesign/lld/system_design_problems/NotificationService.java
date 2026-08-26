package com.rutik.systemdesign.lld.system_design_problems;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// =============================================================================
//  NOTIFICATION SERVICE - Low-Level Design
//  Extracted verbatim from NotificationService.md's Implementation section.
//  Patterns used:
//    - Builder                : Notification.Builder
//    - Strategy + Factory     : Channel family, ChannelFactory
//    - Chain of Responsibility: NotificationFilter chain (preference, quiet hours)
//    - Decorator              : RetryingChannel wraps any Channel
//    - Observer               : DeliveryListener / ConsoleDeliveryTracker
//  The BROKEN RetryingChannelBroken variant on the page is teaching material only
//  and is deliberately not carried here; only the idempotent fix ships.
//  The page's NotificationServiceDemo.main() lives on NotificationService itself,
//  so the file's namesake class is the entry point (house style for this folder).
// =============================================================================

enum Priority { TRANSACTIONAL, MARKETING }

enum ChannelType { EMAIL, SMS, PUSH, IN_APP, WEBHOOK }

enum DeliveryStatus {
    CREATED, FILTERED, QUEUED, SENDING, SENT, FAILED, RETRYING, DEAD_LETTER
}

final class Notification {
    private final String id = java.util.UUID.randomUUID().toString();
    private final String userId;
    private final String category;
    private final Priority priority;
    private final ChannelType channelType;
    private final String templateId;
    private final Map<String, String> params;
    private final String idempotencyKey;
    private volatile DeliveryStatus status = DeliveryStatus.CREATED;
    private final AtomicInteger attempts = new AtomicInteger(0);

    private Notification(Builder b) {
        this.userId = b.userId;
        this.category = b.category;
        this.priority = b.priority;
        this.channelType = b.channelType;
        this.templateId = b.templateId;
        this.params = Map.copyOf(b.params);
        // Derived from the LOGICAL event, not the random id above — a producer retrying
        // submit() for "the same" OTP must land on the same key both times.
        this.idempotencyKey = userId + ":" + category + ":" + templateId + ":" + params;
    }

    public static Builder builder() { return new Builder(); }

    public String id() { return id; }
    public String userId() { return userId; }
    public String category() { return category; }
    public Priority priority() { return priority; }
    public ChannelType channelType() { return channelType; }
    public String templateId() { return templateId; }
    public Map<String, String> params() { return params; }
    public String idempotencyKey() { return idempotencyKey; }
    public DeliveryStatus status() { return status; }
    void setStatus(DeliveryStatus s) { this.status = s; }
    int nextAttempt() { return attempts.incrementAndGet(); }

    public static final class Builder {
        private String userId, category, templateId;
        private Priority priority = Priority.MARKETING;
        private ChannelType channelType;
        private final Map<String, String> params = new HashMap<>();

        public Builder userId(String v) { this.userId = v; return this; }
        public Builder category(String v) { this.category = v; return this; }
        public Builder priority(Priority v) { this.priority = v; return this; }
        public Builder channel(ChannelType v) { this.channelType = v; return this; }
        public Builder template(String v) { this.templateId = v; return this; }
        public Builder param(String k, String v) { this.params.put(k, v); return this; }

        public Notification build() {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(channelType, "channelType");
            Objects.requireNonNull(templateId, "templateId");
            return new Notification(this);
        }
    }
}

interface Channel {
    ChannelType type();
    /** Throws on a transient provider failure; RetryingChannel decides what happens next. */
    void send(Notification n) throws ChannelException;
}

final class ChannelException extends Exception {
    public ChannelException(String message) { super(message); }
}

final class EmailChannel implements Channel {
    private final Random random = new Random();
    public ChannelType type() { return ChannelType.EMAIL; }
    public void send(Notification n) throws ChannelException {
        String body = TemplateEngine.render(n.templateId(), n.params());
        sleep(50); // simulated SES/SendGrid round trip
        if (random.nextInt(10) == 0) throw new ChannelException("provider 5xx");
        System.out.println("[EMAIL] -> " + n.userId() + " : " + body);
    }
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

final class SmsChannel implements Channel {
    private final Random random = new Random();
    public ChannelType type() { return ChannelType.SMS; }
    public void send(Notification n) throws ChannelException {
        String body = TemplateEngine.render(n.templateId(), n.params());
        sleep(300); // deliberately the slow provider in this demo — proves the bulkhead
        if (random.nextInt(5) == 0) throw new ChannelException("provider timeout");
        System.out.println("[SMS]   -> " + n.userId() + " : " + body);
    }
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

final class PushChannel implements Channel {
    public ChannelType type() { return ChannelType.PUSH; }
    public void send(Notification n) {
        // FCM HTTP v1 (OAuth2 service-account token) for Android, APNs (ES256 JWT) for iOS —
        // see Cross-Perspective; both are token-based, neither uses a long-lived server key.
        System.out.println("[PUSH]  -> " + n.userId() + " : " + TemplateEngine.render(n.templateId(), n.params()));
    }
}

final class InAppChannel implements Channel {
    public ChannelType type() { return ChannelType.IN_APP; }
    public void send(Notification n) {
        System.out.println("[INAPP] -> " + n.userId() + " : " + TemplateEngine.render(n.templateId(), n.params()));
    }
}

final class ChannelFactory {
    private static final Map<ChannelType, Channel> REGISTRY = Map.of(
        ChannelType.EMAIL, new EmailChannel(),
        ChannelType.SMS, new SmsChannel(),
        ChannelType.PUSH, new PushChannel(),
        ChannelType.IN_APP, new InAppChannel()
    );
    public static Channel create(ChannelType type) {
        Channel c = REGISTRY.get(type);
        if (c == null) throw new IllegalArgumentException("no channel wired for " + type);
        return c;
    }
}

final class TemplateEngine {
    private static final Map<String, String> TEMPLATES = Map.of(
        "order_shipped", "Your order {orderId} shipped, track at {trackingUrl}",
        "otp", "Your OTP is {code}, expires in 5 minutes",
        "promo", "{discount}% off today only!"
    );
    public static String render(String templateId, Map<String, String> params) {
        String result = TEMPLATES.getOrDefault(templateId, templateId);
        for (Map.Entry<String, String> e : params.entrySet()) {
            result = result.replace("{" + e.getKey() + "}", e.getValue());
        }
        return result;
    }
}

final class UserPreferences {
    // "<userId>:<category>:<channel>" -> opted out. Composite key, O(1), lock-free reads.
    private final Set<String> optOuts = ConcurrentHashMap.newKeySet();
    // userId -> [quietStartHourLocal, quietEndHourLocal]
    private final Map<String, int[]> quietHours = new ConcurrentHashMap<>();

    public void optOut(String userId, String category, ChannelType channel) {
        optOuts.add(key(userId, category, channel));
    }
    public boolean isOptedIn(String userId, String category, ChannelType channel) {
        return !optOuts.contains(key(userId, category, channel));
    }
    public void setQuietHours(String userId, int startHourLocal, int endHourLocal) {
        quietHours.put(userId, new int[]{startHourLocal, endHourLocal});
    }
    public boolean isQuietHour(String userId, int currentHourLocal) {
        int[] window = quietHours.get(userId);
        if (window == null) return false;
        int start = window[0], end = window[1];
        return start <= end
            ? (currentHourLocal >= start && currentHourLocal < end)
            : (currentHourLocal >= start || currentHourLocal < end); // wraps past midnight
    }
    private String key(String userId, String category, ChannelType channel) {
        return userId + ":" + category + ":" + channel;
    }
}

interface NotificationFilter {
    /** True lets the notification continue; false stops the chain and drops it. */
    boolean apply(Notification n);
}

final class PreferenceFilter implements NotificationFilter {
    private final UserPreferences prefs;
    public PreferenceFilter(UserPreferences prefs) { this.prefs = prefs; }
    public boolean apply(Notification n) {
        return prefs.isOptedIn(n.userId(), n.category(), n.channelType());
    }
}

final class QuietHoursFilter implements NotificationFilter {
    private final UserPreferences prefs;
    public QuietHoursFilter(UserPreferences prefs) { this.prefs = prefs; }
    public boolean apply(Notification n) {
        if (n.priority() == Priority.TRANSACTIONAL) return true; // never suppressed
        int hourLocal = java.time.LocalTime.now().getHour(); // per-user zone in a real system
        return !prefs.isQuietHour(n.userId(), hourLocal);
    }
}

final class PerUserThrottle {
    private static final class Bucket { double tokens; long lastRefillMs; }
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final double capacity;
    private final double refillPerSecond;

    public PerUserThrottle(double capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    public boolean tryAcquire(String userId, Priority priority) {
        if (priority == Priority.TRANSACTIONAL) return true; // the requirement, not an optimization
        Bucket b = buckets.computeIfAbsent(userId, k -> {
            Bucket nb = new Bucket();
            nb.tokens = capacity;
            nb.lastRefillMs = System.currentTimeMillis();
            return nb;
        });
        synchronized (b) { // per-bucket lock, not a global one — see Q&A
            long now = System.currentTimeMillis();
            double elapsedSec = (now - b.lastRefillMs) / 1000.0;
            b.tokens = Math.min(capacity, b.tokens + elapsedSec * refillPerSecond);
            b.lastRefillMs = now;
            if (b.tokens >= 1.0) { b.tokens -= 1.0; return true; }
            return false;
        }
    }
}

final class RetryPolicy {
    private final int maxAttempts;
    private final long baseBackoffMs;
    public RetryPolicy(int maxAttempts, long baseBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
    }
    public int maxAttempts() { return maxAttempts; }
    public long backoffMs(int attempt) { return baseBackoffMs * (1L << (attempt - 1)); }
}

interface DeliveryListener {
    void onStatusChange(Notification n, DeliveryStatus status);
}

final class ConsoleDeliveryTracker implements DeliveryListener {
    public void onStatusChange(Notification n, DeliveryStatus status) {
        System.out.println("[STATUS] " + n.id().substring(0, 8) + " " + n.userId() + " -> " + status);
    }
}

// FIX: claim the idempotency key BEFORE dispatching. Set.add() is atomic, so two concurrent
// attempts for the same logical event race safely — exactly one of them actually sends.
final class RetryingChannel implements Channel {
    private final Channel delegate;
    private final RetryPolicy policy;
    private final Set<String> sentKeys = ConcurrentHashMap.newKeySet();
    private final Queue<Notification> deadLetterQueue = new ConcurrentLinkedQueue<>();

    public RetryingChannel(Channel delegate, RetryPolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }
    public ChannelType type() { return delegate.type(); }

    public void send(Notification n) {
        if (!sentKeys.add(n.idempotencyKey())) {
            System.out.println("[DEDUP] skipped duplicate for " + n.idempotencyKey());
            return;
        }
        n.setStatus(DeliveryStatus.SENDING);
        for (int attempt = n.nextAttempt(); attempt <= policy.maxAttempts(); attempt = n.nextAttempt()) {
            try {
                delegate.send(n);
                n.setStatus(DeliveryStatus.SENT);
                return;
            } catch (ChannelException e) {
                n.setStatus(DeliveryStatus.FAILED);
                if (attempt == policy.maxAttempts()) {
                    n.setStatus(DeliveryStatus.DEAD_LETTER);
                    deadLetterQueue.add(n);
                    return;
                }
                n.setStatus(DeliveryStatus.RETRYING);
                sleep(policy.backoffMs(attempt));
            }
        }
    }
    public Queue<Notification> deadLetterQueue() { return deadLetterQueue; }
    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

public final class NotificationService {
    private final List<NotificationFilter> filters;
    private final PerUserThrottle throttle;
    private final Map<ChannelType, Channel> channels = new ConcurrentHashMap<>();
    private final Map<ChannelType, ExecutorService> pools = new ConcurrentHashMap<>();
    private final List<DeliveryListener> listeners = new CopyOnWriteArrayList<>();

    public NotificationService(List<NotificationFilter> filters, PerUserThrottle throttle, RetryPolicy retryPolicy) {
        this.filters = filters;
        this.throttle = throttle;
        for (ChannelType type : ChannelType.values()) {
            if (type == ChannelType.WEBHOOK) continue; // cut for this demo — see Follow-Up
            channels.put(type, new RetryingChannel(ChannelFactory.create(type), retryPolicy));
            // One fixed pool PER channel type: the bulkhead. A backlog on SMS never
            // steals a thread that EMAIL needs.
            pools.put(type, Executors.newFixedThreadPool(2));
        }
    }

    public void addListener(DeliveryListener l) { listeners.add(l); }

    public void submit(Notification n) {
        for (NotificationFilter f : filters) {
            if (!f.apply(n)) {
                n.setStatus(DeliveryStatus.FILTERED);
                notifyListeners(n);
                return;
            }
        }
        n.setStatus(DeliveryStatus.FILTERED);
        if (!throttle.tryAcquire(n.userId(), n.priority())) {
            notifyListeners(n); // stays FILTERED: suppressed by throttle, not a provider failure
            return;
        }
        n.setStatus(DeliveryStatus.QUEUED);
        pools.get(n.channelType()).submit(() -> dispatch(n));
    }

    private void dispatch(Notification n) {
        try {
            channels.get(n.channelType()).send(n); // drives SENDING/SENT/FAILED/RETRYING/DEAD_LETTER
        } catch (ChannelException e) {
            // send() is declared `throws ChannelException`, and dispatch() runs inside a
            // bare Runnable -- so this MUST be handled here; `throws` would not compile.
            n.setStatus(DeliveryStatus.DEAD_LETTER);
        }
        notifyListeners(n);
    }

    private void notifyListeners(Notification n) {
        for (DeliveryListener l : listeners) l.onStatusChange(n, n.status());
    }

    public void shutdown() {
        pools.values().forEach(ExecutorService::shutdown);
    }

    public static void main(String[] args) throws InterruptedException {
        UserPreferences prefs = new UserPreferences();
        prefs.optOut("user-42", "marketing", ChannelType.SMS);
        prefs.setQuietHours("user-42", 22, 8);

        List<NotificationFilter> filters = List.of(new PreferenceFilter(prefs), new QuietHoursFilter(prefs));
        PerUserThrottle throttle = new PerUserThrottle(3, 1.0); // burst 3, refill 1/sec, MARKETING only
        RetryPolicy retryPolicy = new RetryPolicy(3, 200);

        NotificationService service = new NotificationService(filters, throttle, retryPolicy);
        service.addListener(new ConsoleDeliveryTracker());

        // Transactional: never throttled, never suppressed by quiet hours.
        service.submit(Notification.builder()
            .userId("user-7").category("security").priority(Priority.TRANSACTIONAL)
            .channel(ChannelType.SMS).template("otp").param("code", "482913").build());

        // Marketing burst: capacity 3, so requests 4 and 5 are throttled.
        // NOTE the varying param. The idempotency key is
        // userId:category:templateId:params -- five IDENTICAL sends would collapse onto one
        // key and be dropped by RetryingChannel's dedupe BEFORE the throttle ever saw them.
        // Five distinct offers is what actually exercises the rate limiter.
        for (int i = 1; i <= 5; i++) {
            service.submit(Notification.builder()
                .userId("user-88").category("marketing").priority(Priority.MARKETING)
                .channel(ChannelType.EMAIL).template("promo")
                .param("discount", String.valueOf(10 * i)).build());
        }

        // Opted-out channel: filtered before it ever reaches the throttle or a worker pool.
        service.submit(Notification.builder()
            .userId("user-42").category("marketing").priority(Priority.MARKETING)
            .channel(ChannelType.SMS).template("promo").param("discount", "50").build());

        Thread.sleep(3000); // let the worker pools drain
        service.shutdown();
    }
}
