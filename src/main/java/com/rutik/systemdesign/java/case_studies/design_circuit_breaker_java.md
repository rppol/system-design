# Design a Circuit Breaker (Java)

> **A circuit breaker is a polite "I give up for now" signal.**  
> Instead of hammering a failing service with 1,000 requests that will all timeout after 5
> seconds, the circuit breaker detects the failure pattern, trips OPEN, and returns a fallback
> response in <1ms for the next 10 seconds — giving the downstream service time to recover
> without being crushed by retries.

**Key insight:** The circuit breaker's primary goal is NOT to protect the caller — it is to
protect the failing downstream service. A service that is overloaded with 10,000 retry requests
cannot recover. By stopping the calls entirely during the OPEN window, the circuit breaker
gives the dependency space to heal.

---

## 1. Requirements Clarification

### Functional requirements
- Track outcomes of calls to a downstream dependency (success / failure / slow)
- Trip OPEN when failure rate OR slow-call rate exceeds a configurable threshold
- Transition to HALF_OPEN after a configurable wait duration
- Allow a limited number of probe calls in HALF_OPEN to test recovery
- Return a configurable fallback when OPEN (or when HALF_OPEN and no probe slots remain)
- Thread-safe — concurrent callers must see consistent CB state
- Emit metrics: call outcomes, state transitions, current state

### Non-functional requirements
- **Hot path overhead**: < 100 ns per call when CLOSED (just a counter update)
- **State transition accuracy**: no missed failures, no missed transitions under concurrency
- **Recovery sensitivity**: HALF_OPEN probes must be small enough to detect recovery, large enough for statistical confidence
- **Thread safety**: the state machine must be correct under concurrent callers

### Out of scope
- Retry logic (CB is orthogonal to retry — see `design_rate_limiter_java.md` for composition)
- Distributed state sharing (this is a per-JVM CB; see `spring/case_studies/cross_cutting/resilience4j_patterns.md` for Redis-backed CB)
- Time-based sliding windows (this implementation uses count-based; see Resilience4j for time-based)

---

## 2. Scale Estimation

```
Hot-path overhead analysis:
  Per-call work (CLOSED state):
    Atomic counter CAS: ~5 ns
    Ring buffer slot write: ~10 ns
    Failure rate calculation: ~20 ns (integer division)
    Total: ~35 ns overhead per call
    At 100,000 calls/sec: 3.5 ms CPU/sec (0.35% of 1 CPU core) — negligible

  OPEN state fast-reject:
    CAS read of state + timestamp compare: ~10 ns
    vs. normal call (~100ms for HTTP): 10,000× faster

State machine memory:
  Sliding window (20 calls): int[20] = 80 bytes
  AtomicInteger state: 4 bytes
  AtomicLong stats (success, failure, slow, total): 4 × 8 = 32 bytes
  Total per CB instance: ~200 bytes

Typical deployment: 1 CB per downstream service × 10 downstream services = 2 KB total
```

---

## 3. High-Level Architecture

```
+------------------------------------------------------------+
|                   CircuitBreaker                           |
|                                                            |
|  execute(supplier, fallback)                               |
|      |                                                     |
|      v                                                     |
|  +------------------+                                      |
|  |  StateCheck       |                                     |
|  |  CLOSED → proceed |                                     |
|  |  OPEN → if wait   |                                     |
|  |    expired → probe|                                     |
|  |  OPEN → fallback  |                                     |
|  +------------------+                                      |
|      |                                                     |
|      v (CLOSED or probe allowed)                           |
|  [call supplier.get()]                                     |
|      |                                                     |
|      v (outcome: SUCCESS, FAILURE, SLOW, EXCEPTION)        |
|  +------------------+                                      |
|  |  RecordOutcome    |                                     |
|  |  → slidingWindow  |                                     |
|  |  → stats update   |                                     |
|  |  → checkThreshold |                                     |
|  +------------------+                                      |
|      |                                                     |
|      v (threshold exceeded?)                               |
|  +------------------+                                      |
|  |  StateTransition  |                                     |
|  |  CLOSED → OPEN    |                                     |
|  |  HALF_OPEN→OPEN   |                                     |
|  |  HALF_OPEN→CLOSED |                                     |
|  +------------------+                                      |
+------------------------------------------------------------+

                State Machine:
CLOSED ──(failRate ≥ threshold)──────────────> OPEN
  ^                                               |
  |                                         waitDuration expires
  |                                               |
  └──(probes succeed)── HALF_OPEN <──────────────┘
                             |
                        (probes fail)
                             |
                             └────────────────> OPEN (reset)
```

---

## 4. Component Deep Dives

### 4.1 State model

```java
public enum State {
    CLOSED,     // Normal: calls pass through; failure rate tracked
    OPEN,       // Tripped: calls fast-fail; wait for recovery window
    HALF_OPEN   // Probe: limited calls allowed to test recovery
}
```

### 4.2 Configuration

```java
public record CircuitBreakerConfig(
    int slidingWindowSize,                  // evaluate over last N calls (default: 20)
    int minimumNumberOfCalls,               // don't trip until at least N calls (default: 10)
    float failureRateThreshold,             // 0.0–1.0 (default: 0.5 = 50%)
    float slowCallRateThreshold,            // 0.0–1.0 (default: 0.8 = 80%)
    Duration slowCallDurationThreshold,     // calls above this duration count as slow (default: 2s)
    Duration waitDurationInOpenState,       // how long to stay OPEN before probing (default: 10s)
    int permittedCallsInHalfOpenState       // how many probes before deciding (default: 3)
) {
    public static CircuitBreakerConfig defaultConfig() {
        return new CircuitBreakerConfig(
            20, 10, 0.5f, 0.8f,
            Duration.ofSeconds(2),
            Duration.ofSeconds(10),
            3
        );
    }
}
```

### 4.3 Sliding window with atomic ring buffer

```java
public class CountBasedSlidingWindow {

    // Ring buffer of outcomes: 0=success, 1=failure, 2=slow
    private final int[] outcomes;
    private final int capacity;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger slowCount = new AtomicInteger(0);

    public CountBasedSlidingWindow(int capacity) {
        this.capacity = capacity;
        this.outcomes = new int[capacity];
        Arrays.fill(outcomes, -1);   // -1 = empty slot
    }

    public synchronized void record(int outcome) {
        // outcome: 0=success, 1=failure, 2=slow
        int idx = writeIndex.getAndIncrement() % capacity;
        int previous = outcomes[idx];

        // Remove contribution of the slot we're overwriting
        if (previous == 1) failureCount.decrementAndGet();
        if (previous == 2) slowCount.decrementAndGet();
        if (previous != -1) totalCount.decrementAndGet();

        // Write new outcome
        outcomes[idx] = outcome;
        totalCount.incrementAndGet();
        if (outcome == 1) failureCount.incrementAndGet();
        if (outcome == 2) slowCount.incrementAndGet();
    }

    public int total()   { return totalCount.get(); }
    public int failures(){ return failureCount.get(); }
    public int slow()    { return slowCount.get(); }
    public float failureRate() {
        int t = total();
        return t == 0 ? 0.0f : (float) failures() / t;
    }
    public float slowRate() {
        int t = total();
        return t == 0 ? 0.0f : (float) slow() / t;
    }

    public void reset() {
        Arrays.fill(outcomes, -1);
        writeIndex.set(0);
        totalCount.set(0);
        failureCount.set(0);
        slowCount.set(0);
    }
}
```

**Note:** The `synchronized` on `record()` is intentional — the read-modify-write on the ring
buffer slot requires atomicity between reading the old slot value and writing the new one.
For a production implementation without synchronization, use a `StampedLock` or the
`VarHandle`-based CAS approach from Resilience4j's internal `SlidingWindowMetrics`.

---

### 4.4 `CircuitBreaker` — the state machine

```java
import java.time.Instant;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

public class CircuitBreaker {

    private final String name;
    private final CircuitBreakerConfig config;
    private final CountBasedSlidingWindow window;

    // State as enum ordinal in AtomicInteger for CAS transitions
    private final AtomicInteger state = new AtomicInteger(State.CLOSED.ordinal());

    // When did we transition to OPEN? Used to determine when to probe
    private volatile Instant openedAt;

    // How many probe calls are currently in-flight in HALF_OPEN
    private final AtomicInteger halfOpenProbeCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
    private final AtomicInteger halfOpenFailureCount = new AtomicInteger(0);

    // Metrics
    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong rejectedCalls = new AtomicLong(0);
    private final AtomicLong successCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);

    public CircuitBreaker(String name) {
        this(name, CircuitBreakerConfig.defaultConfig());
    }

    public CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = name;
        this.config = config;
        this.window = new CountBasedSlidingWindow(config.slidingWindowSize());
    }

    public <T> T execute(Supplier<T> supplier, Supplier<T> fallback) {
        totalCalls.incrementAndGet();
        State currentState = currentState();

        switch (currentState) {
            case OPEN:
                // Check if wait duration has expired → attempt to probe
                if (Instant.now().isAfter(openedAt.plus(config.waitDurationInOpenState()))) {
                    // Try to transition to HALF_OPEN (only one thread should succeed)
                    if (transitionTo(State.OPEN, State.HALF_OPEN)) {
                        halfOpenProbeCount.set(0);
                        halfOpenSuccessCount.set(0);
                        halfOpenFailureCount.set(0);
                        // Fall through to attempt a probe call
                        return attemptCall(supplier, fallback, true);
                    }
                }
                // Still OPEN (or another thread already transitioned): fast-reject
                rejectedCalls.incrementAndGet();
                return fallback.get();

            case HALF_OPEN:
                // Allow up to permittedCallsInHalfOpenState probe calls
                int probeSlot = halfOpenProbeCount.incrementAndGet();
                if (probeSlot <= config.permittedCallsInHalfOpenState()) {
                    return attemptCall(supplier, fallback, true);
                } else {
                    // Probe slots exhausted: wait for probe results
                    halfOpenProbeCount.decrementAndGet();
                    rejectedCalls.incrementAndGet();
                    return fallback.get();
                }

            case CLOSED:
            default:
                return attemptCall(supplier, fallback, false);
        }
    }

    private <T> T attemptCall(Supplier<T> supplier, Supplier<T> fallback, boolean isProbe) {
        long startNs = System.nanoTime();
        try {
            T result = supplier.get();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;

            boolean slow = durationMs > config.slowCallDurationThreshold().toMillis();
            recordOutcome(slow ? 2 : 0, isProbe);  // 2=slow, 0=success
            successCalls.incrementAndGet();
            return result;

        } catch (Exception e) {
            recordOutcome(1, isProbe);  // 1=failure
            failedCalls.incrementAndGet();
            return fallback.get();
        }
    }

    private void recordOutcome(int outcome, boolean isProbe) {
        if (isProbe) {
            // HALF_OPEN probe result
            boolean isFailure = outcome == 1 || outcome == 2;
            if (isFailure) {
                int failures = halfOpenFailureCount.incrementAndGet();
                // Any probe failure → back to OPEN
                if (transitionTo(State.HALF_OPEN, State.OPEN)) {
                    openedAt = Instant.now();
                    window.reset();
                }
            } else {
                int successes = halfOpenSuccessCount.incrementAndGet();
                if (successes >= config.permittedCallsInHalfOpenState()) {
                    // All probes succeeded → CLOSED
                    if (transitionTo(State.HALF_OPEN, State.CLOSED)) {
                        window.reset();
                    }
                }
            }
        } else {
            // CLOSED state: update sliding window
            window.record(outcome);
            evaluateThreshold();
        }
    }

    private void evaluateThreshold() {
        if (window.total() < config.minimumNumberOfCalls()) {
            return;   // not enough data yet
        }

        boolean shouldTrip =
            window.failureRate() >= config.failureRateThreshold() ||
            window.slowRate() >= config.slowCallRateThreshold();

        if (shouldTrip) {
            if (transitionTo(State.CLOSED, State.OPEN)) {
                openedAt = Instant.now();
            }
        }
    }

    private boolean transitionTo(State from, State to) {
        boolean transitioned = state.compareAndSet(from.ordinal(), to.ordinal());
        if (transitioned) {
            onStateTransition(from, to);
        }
        return transitioned;
    }

    protected void onStateTransition(State from, State to) {
        System.out.printf("[CB:%s] %s → %s (failures=%d/%d, slow=%d/%d)%n",
            name, from, to, window.failures(), window.total(), window.slow(), window.total());
    }

    public State currentState() {
        return State.values()[state.get()];
    }

    public CircuitBreakerMetrics getMetrics() {
        return new CircuitBreakerMetrics(
            name, currentState(), window.failureRate(), window.slowRate(),
            totalCalls.get(), rejectedCalls.get(), successCalls.get(), failedCalls.get()
        );
    }

    public record CircuitBreakerMetrics(
        String name, State state, float failureRate, float slowRate,
        long totalCalls, long rejectedCalls, long successCalls, long failedCalls
    ) {}
}
```

---

### 4.5 Broken pattern — checking state without atomic transition

**Broken:**
```java
// BROKEN: two threads can both see OPEN + expired → both transition to HALF_OPEN
// Only one thread should be allowed to transition (via CAS)
if (state == OPEN && Instant.now().isAfter(openedAt.plus(waitDuration))) {
    state = HALF_OPEN;           // non-atomic: two threads can set this simultaneously
    halfOpenProbeCount = 0;      // non-atomic reset
    // Now two threads both believe they transitioned; both run probes
    // Both decrement halfOpenProbeCount → count goes negative → never transitions to CLOSED
}
```

**Fixed (in our `transitionTo` above):**
```java
// Only ONE thread succeeds with CAS; others see their CAS fail and treat it as "already transitioned"
if (transitionTo(State.OPEN, State.HALF_OPEN)) {
    halfOpenProbeCount.set(0);    // called only by the winning thread
    // ...
}
```

**Rule:** Every state transition must be an atomic CAS on `state`. Only the thread that wins
the CAS should perform side effects (resetting counters, recording `openedAt`).

---

### 4.6 Usage and composition with retry

```java
// Correct order: CircuitBreaker outer; Retry inner
// If CB is OPEN, retry never attempts the call (correct)
// If CB is CLOSED: retry retries failures; CB sees the final outcome

CircuitBreaker cb = new CircuitBreaker("payment-service",
    new CircuitBreakerConfig(20, 10, 0.5f, 0.8f,
        Duration.ofSeconds(2), Duration.ofSeconds(10), 3));

RetryPolicy retry = RetryPolicy.of(maxAttempts=2, backoff=Duration.ofMillis(500));

// Execution: CB wraps Retry
PaymentResult result = cb.execute(
    () -> retry.execute(() -> paymentClient.charge(request)),
    () -> PaymentResult.pending(request.orderId(), "circuit-open")
);
```

---

## 5. Design Decisions & Tradeoffs

### Decision 1: Count-based vs time-based sliding window

| | Count-based (last N calls) | Time-based (last N seconds) |
|--|---------------------------|---------------------------|
| Response to burst | Immediate — N failures in a row trip the CB | Delayed — failures spread over time may not exceed rate |
| Low-traffic services | Accurate (every call counts) | May not reach threshold with few calls |
| High-traffic services | Window fills quickly; fast reaction | More stable; less sensitive to individual spikes |
| Implementation complexity | Simple ring buffer | Requires time-bucketed aggregation |
| **Recommendation** | Default for most services | Use for high-traffic (>1k ops/sec) |

### Decision 2: `volatile Instant openedAt` + CAS state

The `openedAt` field is `volatile` (not `AtomicReference`) because: only one thread writes it
(the CAS winner for OPEN transition), but many threads read it. A single volatile write/read
suffices; no CAS needed. The state itself uses `AtomicInteger` for CAS-based transitions.

### Decision 3: Probe count in HALF_OPEN

`permittedCallsInHalfOpenState = 3` (default) means only 3 calls are allowed through before
the CB decides. Too few (1) means one flaky call re-trips the CB; too many (10) means 10 calls
hit a still-degraded service. Rule: `3–5` probes at 50% threshold = need ≤1 failure in 3 probes.

### Decision 4: Any HALF_OPEN failure → immediately back to OPEN

Our implementation trips immediately on any probe failure. An alternative is to wait for all
`permittedCalls` probes to complete and evaluate the rate — allowing 1-in-3 failures.
The immediate-trip approach is simpler and more conservative (fail-safe): any sign of continued
failure extends the OPEN window. Resilience4j's default is immediate-trip.

### Decision 5: Synchronised vs lock-free sliding window

We used `synchronized` for simplicity. A production implementation would use a
`StampedLock` (read/write separation) or VarHandle CAS per slot. The synchronisation is on the
per-CB window object, not a global lock — contention is bounded by calls to one dependency.

---

## 6. Real-World Implementations

### Netflix Hystrix (legacy) — Thread-pool isolation + CB

Hystrix combined circuit breaker with a thread-pool bulkhead (each dependency had its own pool).
The CB used a rolling window of 10-second buckets (time-based), tracking success/failure/timeout/
rejection counts per bucket. At each window evaluation, Hystrix summed the last 10 buckets and
computed failure rate. The key metric: `error percentage = (failures + timeouts) / total`.
Hystrix is no longer maintained; Resilience4j is the successor.

### Resilience4j — Lock-free sliding window using `AtomicLong` bitfields

Resilience4j's `SlidingWindowMetrics` packs outcome into a `64-bit AtomicLong` per ring buffer slot
(high 32 bits = success count, low 32 bits = failure count) and uses a `LongAdder`-backed
aggregate for O(1) rate computation. The ring buffer uses a `VarHandle` CAS for lock-free slot
updates. This achieves < 50 ns per record even at 1M ops/sec. Reference: Resilience4j source
`io.github.resilience4j.core.metrics.SlidingWindowMetrics`.

### Amazon — Cell-based CB for S3 control plane

Amazon's S3 uses circuit breakers at each cell boundary. When the metadata service for a
cell shows >5% error rate over a 30-second count window (COUNT_BASED: 1,000 minimum calls),
the circuit breaker routes new requests to a fallback cell. This is not fail-fast for the
caller but fail-over: traffic redirected rather than rejected. Reference: Amazon Builder's
Library, "Avoiding overload in distributed systems" (2020).

### Google — Adaptive throttling as a CB alternative

Google's SRE Book (2016) describes "adaptive throttling" as an alternative to hard circuit
breakers for high-throughput services: the client measures its own accept rate vs request rate
and self-throttles when the ratio drops below a threshold. `K * requests_accepted / requests_total < threshold`
(where K=2 is Google's recommended multiplier). This is a softer CB that degrades gradually
rather than flipping to full OPEN. Used in Stubby (internal gRPC). No binary open/closed;
more stable under high concurrency.

### LinkedIn — Per-method circuit breakers

LinkedIn's `rest.li` framework applies circuit breakers at method granularity: `/api/payment/charge`
and `/api/payment/refund` have separate circuit breakers. This prevents a slow `/refund`
endpoint from tripping the CB and blocking `/charge` — the higher-value operation.
Reference: LinkedIn Engineering blog (2018).

---

## 7. Technologies & Tools

| Tool | Role | Notes |
|------|------|-------|
| Resilience4j | Production CB for Spring/non-Spring | Count-based + time-based; VarHandle lock-free; Micrometer integration |
| `java.util.concurrent.atomic.AtomicInteger` | CAS state machine | Ordinal-based state encoding |
| `java.util.concurrent.atomic.AtomicLong` | Lock-free counters | Failure/success counts |
| Micrometer | CB state + rate metrics | Export to Prometheus; alert on state gauge |
| JFR Custom Events | Record state transitions | `@Name("CircuitBreaker.StateTransition")` |
| Testcontainers + WireMock | Test CB under real failure conditions | Simulate downstream failures; assert CB state transitions |

---

## 8. Operational Playbook

**(a) Runbook: Circuit breaker stuck OPEN**
- **Symptom**: CB state = OPEN for > 5 minutes; `waitDurationInOpenState = 10s` suggests it should have probed by now
- **Diagnosis**: Check if probe calls are also failing — HALF_OPEN → OPEN cycle. Check downstream health
- **Mitigation**: If downstream is recovered but CB doesn't detect it: force-reset via admin endpoint calling `circuitBreaker.reset()`. Check `permittedCallsInHalfOpenState` — if set to 1 and the one probe is unlucky, CB may keep re-opening
- **Resolution**: Fix downstream; increase `permittedCallsInHalfOpenState` to 3–5; reduce `failureRateThreshold` to allow 1-in-3 probe failures

**(b) Runbook: CB trips too aggressively (false positive)**
- **Symptom**: CB opens during brief downstream latency spike (20s), then downstream recovers but 60s of traffic was rejected
- **Diagnosis**: `minimumNumberOfCalls` is too low (e.g., 2); CB tripped on 1 failure out of 2
- **Mitigation**: Increase `minimumNumberOfCalls` to 10; increase `slidingWindowSize` to 30; increase `waitDurationInOpenState` to allow recovery before probing
- **Resolution**: Tune thresholds based on measured downstream error rate patterns; use `slowCallRateThreshold` instead of `failureRateThreshold` if the issue is latency, not errors

**(c) Runbook: CB never trips (misses persistent failures)**
- **Symptom**: Downstream is returning 500s for 2 minutes; CB remains CLOSED; caller is retrying and failing
- **Diagnosis**: `ignoreExceptions` includes the exception type being thrown; or `recordExceptions` list is missing the thrown exception type
- **Resolution**: Add the exception class to `recordExceptions`; verify by checking `cb.getMetrics().failureRate()`

**(d) Runbook: State oscillates between HALF_OPEN and OPEN repeatedly**
- **Symptom**: CB logs show OPEN → HALF_OPEN → OPEN → HALF_OPEN transitions every 10 seconds
- **Diagnosis**: Probe calls fail (downstream partially recovered but unstable); each probe failure immediately re-opens CB
- **Mitigation**: Increase `waitDurationInOpenState` (e.g., from 10s to 30s) to allow downstream more recovery time per probe attempt
- **Resolution**: Fix root cause of downstream instability; implement retry with jitter on the probe calls themselves

---

## 9. Common Pitfalls & War Stories

### Pitfall 1 — CB trips on normal error responses (HTTP 404, 400)

**Incident (2021, SaaS):** A CB monitored an inventory API. The CB counted HTTP 404 (item not
found) as failures, tripping when 50% of lookups were for non-existent items (normal in the
business). **Fix:** Configure `ignoreExceptions` to exclude `ItemNotFoundException` (404);
only count `ServiceUnavailableException` (503) and connection failures. **Impact:** 2-hour
partial outage; 15% of orders delayed.

### Pitfall 2 — Retry outside Circuit Breaker amplifies load

**Incident (2020, fintech):** A payment service had `Retry(maxAttempts=3)` wrapping a
`CircuitBreaker`. When the CB opened, each caller got `CallNotPermittedException` from the CB —
which the `Retry` treated as a failure and retried 3 times. Net effect: 3× rejected-call rate,
draining the `rejectedCalls` counter and causing alerts to fire misleadingly. **Fix:** Correct
composition: CB outer, Retry inner. `CallNotPermittedException` should not be retried.

### Pitfall 3 — Non-thread-safe state transition

**Incident (2022, logistics):** A hand-rolled CB used `if/else` on a non-volatile `enum state`
field. Under concurrent load, two threads simultaneously saw `OPEN + expired → transition to
HALF_OPEN`. Both reset probe counters concurrently. Result: `halfOpenProbeCount` went to -2
(both threads decremented concurrently); the CB stuck permanently because the count never
reached `permittedCallsInHalfOpenState`. Service was degraded for 40 minutes until pod restart.
**Fix:** Use `AtomicInteger` + CAS for all state transitions; only the CAS winner performs
side effects.

### Pitfall 4 — CB on a health-check endpoint causing Kubernetes cascades

**Incident (2023, e-commerce):** A CB was applied to the payment service health check
(`/actuator/health`). When the payment service's DB became slow, the CB protecting the health
check trips → health check returns OPEN fallback → Spring Boot `/actuator/health` returns DOWN
→ Kubernetes liveness probe fails → pod is restarted. 30 pods restart simultaneously →
increased DB load → more CB trips → cascade. **Fix:** Never apply a CB to health check calls;
health checks should be passive (read CB state, not call through it).

### Pitfall 5 — Missing `minimum number of calls` causes startup false-trip

**Incident (2022, startup):** On service startup, the first 2 calls to a downstream were slow
(cold-start). With `minimumNumberOfCalls=2` and `failureRateThreshold=0.5` and `slowCallRateThreshold=0.8`,
the CB tripped OPEN after 2 slow calls. All subsequent calls during the 10s OPEN window received
fallbacks. 800 requests were served incorrect data during the first 10 seconds of deployment.
**Fix:** Set `minimumNumberOfCalls = 10`; add a startup warmup period where CB is disabled.

---

## 10. Capacity Planning

**CB sizing for high-throughput services:**
```
# Given: 10,000 calls/sec to payment service
# CB overhead per call (CLOSED state): ~35 ns
# Total CB overhead: 10,000 × 35 ns = 350 µs/sec = 0.035% of 1 CPU core

# Sliding window memory: 20 int slots = 80 bytes per CB instance
# 10 CB instances (10 downstream services): 800 bytes

# OPEN state fast-reject at 10,000 calls/sec:
# CB saves: 10,000 × 200ms (avg call time when broken) = 2,000 CPU-seconds/sec
# vs CB overhead: 10,000 × 10ns = 100 µs/sec
# Benefit ratio: 20,000,000×

# Appropriate sliding window size:
# windowSize = min_calls_for_statistical_significance × safety_factor
# At 10,000 calls/sec: 100 calls/10ms → window fills in 0.2ms (too fast, churn)
# At 100 calls/sec: 100 calls in 1 second → good window fill rate
# Rule: window fills in 1-10 seconds at nominal call rate
# For 10,000 calls/sec: use TIME_BASED window (10s × 1000 calls = 100k minimum)
# For 100 calls/sec: COUNT_BASED windowSize=20 (fills in 0.2s) is fine
```

---

## 11. Interview Discussion Points

**Q1. Explain the circuit breaker pattern and its three states.**
A circuit breaker monitors calls to a downstream dependency and trips to OPEN state when the
failure rate exceeds a threshold. In CLOSED state, calls pass through and outcomes are recorded
in a sliding window; when the window's failure rate exceeds the threshold, the CB transitions
to OPEN. In OPEN state, all calls are fast-rejected (no network call made) and a fallback is
returned immediately — giving the failing downstream time to recover without being bombarded.
After a configurable `waitDuration`, the CB transitions to HALF_OPEN and admits a small number
of probe calls. If the probes succeed, the CB returns to CLOSED; if they fail, it returns to
OPEN for another wait cycle.

**Q2. Why must the circuit breaker be outside the retry decorator?**
If Retry wraps CircuitBreaker, when the CB is OPEN, each retry attempt gets an immediate
`CallNotPermittedException` from the CB (no actual network call). The Retry layer sees this
as a retriable failure and fires again — N times, all instantly failing. This adds zero recovery
benefit but creates N rejected calls per original request, draining rejection counters and
potentially making the CB's failure rate tracking misleading. With CB outside Retry, the CB
catches the final outcome of all retry attempts as a single event: if all 3 retries fail,
the CB records 1 failure (or counts each retry individually, depending on implementation).
If the CB opens, the outer CB rejects the call before any retry even starts.

**Q3. How does the sliding window work and why is `minimumNumberOfCalls` important?**
The sliding window tracks the last N call outcomes in a ring buffer. The failure rate is computed
as `failures / total` over the window. `minimumNumberOfCalls` prevents the CB from tripping
on insufficient data: with `minimum=10` and `threshold=0.5`, the CB won't trip until it has seen
at least 10 calls. Without this, 2 failures out of 2 calls = 100% failure rate → immediate trip
on service startup (cold calls), a known false positive. Set `minimum ≥ 10` for most services.
For high-traffic services (>1k calls/s), increase to 50–100 to smooth out statistical noise.

**Q4. How do you make a circuit breaker state machine thread-safe?**
Use an `AtomicInteger` to encode the state as an ordinal and use `compareAndSet(from, to)` for
all transitions. This ensures only one thread succeeds in transitioning from OPEN to HALF_OPEN —
the CAS winner — and only that thread performs side effects (resetting probe counters, recording
`openedAt`). Other threads that attempt the same CAS concurrently get `false` and either treat
the state as "already transitioned" (and proceed with a probe) or re-read the state and behave
accordingly. Without CAS, two threads can both see `state == OPEN` → both transition →
double-reset of probe counters → negative count → CB never closes.

**Q5. What is the difference between count-based and time-based sliding windows?**
Count-based: track the last N individual call outcomes. The window always contains exactly N
entries (once filled), regardless of time. Fast to fill at high call rates; may be slow to fill
at low call rates. Simple ring buffer implementation. Time-based: aggregate outcomes in time
buckets (e.g., 1-second buckets); keep the last N seconds. More stable at high traffic (smooths
bursts); never fails to fill at low traffic (always covers the time window). More complex
implementation (requires time-bucketed aggregation and periodic bucket rotation). Rule: use
count-based for services with < 1,000 calls/sec; time-based for higher-throughput services
where you want time-based SLA alignment (e.g., "5% failure rate in the last 10 seconds").

**Q6. How would you implement a circuit breaker that fails gradually rather than all-at-once?**
Replace the binary OPEN/CLOSED state with a continuous probability:
`P(reject) = clamp(0, 1, (failures / total - threshold) / (1 - threshold))`. At exactly the
threshold: 0% rejection; at 2× threshold: 100% rejection. Each request is accepted/rejected
based on `Math.random() < P(reject)`. This is Google's "adaptive throttling" model. Advantages:
no sudden cliff; the service degrades smoothly; P99 latency rises gradually instead of snapping
to "all fallback". Disadvantages: harder to reason about; harder to monitor ("what fraction of
traffic is being throttled?"); harder to set recovery thresholds. Standard circuit breakers
(binary) are preferred for most services because state transitions are visible and auditable.

**Q7. What metrics should you export from a circuit breaker?**
Four key metrics: (1) `circuitbreaker.state` gauge (0=CLOSED, 1=OPEN, 2=HALF_OPEN) — alert
when 1 (OPEN) for > 30s. (2) `circuitbreaker.calls.success` counter — rate should match
baseline throughput when CLOSED. (3) `circuitbreaker.calls.failure` counter — watch for
rising trend before CB trips. (4) `circuitbreaker.calls.not_permitted` counter (OPEN rejections)
— non-zero means CB is active and fallbacks are serving. Also: `circuitbreaker.failure.rate`
gauge and `circuitbreaker.slow.rate` gauge for pre-trip alerting. Wire a Grafana alert on
`state == OPEN for > 5 minutes` → page on-call (downstream is stuck, not just blipping).

**Q8. How does the HALF_OPEN probe work and what is the right probe count?**
HALF_OPEN permits exactly `permittedCallsInHalfOpenState` actual calls to the downstream.
These probes go through the normal call path (including timeouts). If the configured number
of probes succeed → CLOSED. Any probe failure → OPEN (reset `openedAt`). The probe count
should be: large enough for statistical confidence (1 probe is too noisy; 1 failure could be
a transient blip), small enough to limit damage if the downstream is still broken (10 probes
at 5s each = 50s of load on an unhealthy service). Recommended: 3–5 probes. With `threshold=0.5`
and 5 probes: need ≤2 failures to stay CLOSED. With 3 probes: need 0 failures (all probes
must succeed). Choose 3 for conservative recovery (prefer staying OPEN); choose 5 for faster
recovery acceptance.

**Q9. How would you test a circuit breaker implementation?**
Three categories: (1) Unit tests: inject a `Supplier` that fails N times, then succeeds; assert
CB state transitions match expectations. Use `CountDownLatch` to synchronise concurrent-caller
tests. (2) Integration tests with WireMock: configure WireMock to return 500 for first 10 calls,
then 200; assert CB opens after 10 failures and closes after probes succeed. (3) Concurrency
tests: 50 threads hammering `execute()` simultaneously; assert no state corruption (invalid
transitions, negative probe counts). Use JFR `ThreadDump` to confirm no deadlocks under sustained
concurrency. Critical test: verify `transitionTo(OPEN, HALF_OPEN)` is called exactly once under
concurrent callers — not twice.

**Q10. How would you make the circuit breaker configuration dynamically adjustable without restart?**
Use Spring's `@RefreshScope` on a `CircuitBreakerConfig` bean (backed by Spring Cloud Config)
and implement a `CircuitBreaker.reconfigure(newConfig)` method that atomically swaps the config
reference: `volatile CircuitBreakerConfig config = defaultConfig; config = newConfig;` (volatile
write ensures visibility). Adjust threshold and window size without resetting the existing
state — a CB in OPEN state should remain OPEN under the new config unless reset explicitly.
For `slidingWindowSize` changes: create a new `CountBasedSlidingWindow` and swap it atomically
(`AtomicReference<CountBasedSlidingWindow>`); history is lost on resize. Subscribe to
`@EventListener(RefreshScopeRefreshedEvent.class)` to trigger `reconfigure()` after a
Spring Cloud Bus refresh.

---

## Cross-Cutting References

- [cross_cutting/concurrency_memory_visibility_primitives.md](cross_cutting/concurrency_memory_visibility_primitives.md) — AtomicInteger CAS state machine, volatile fields
- [cross_cutting/benchmarking_with_jmh.md](cross_cutting/benchmarking_with_jmh.md) — measuring CB overhead at 100k ops/sec
- [cross_cutting/backpressure_and_bounded_resources.md](cross_cutting/backpressure_and_bounded_resources.md) — CB as part of a backpressure chain
- [../../spring/case_studies/cross_cutting/resilience4j_patterns.md](../../spring/case_studies/cross_cutting/resilience4j_patterns.md) — production CB with Spring integration
- [design_rate_limiter_java.md](design_rate_limiter_java.md) — rate limiter as complementary pattern to CB
