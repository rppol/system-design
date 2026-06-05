# Zero-Downtime Deployments and Configuration for Spring Services

> **A deployment that requires downtime is a deployment that destroys revenue.**  
> Zero-downtime deployment is not just a DevOps concern — it is a database schema and API
> contract discipline. The key constraint: during any rolling deploy, old and new versions of
> your service run simultaneously against the same database. Every change must be backward
> compatible across that transition window.

---

## 1. Concept Overview

Zero-downtime deployment (ZDD) requires that:
1. Old and new instances can run simultaneously against the same database
2. New instances can handle requests during startup before they are ready
3. Old instances can finish in-flight requests before shutdown
4. Database schema migrations are backward compatible across the deploy window
5. External configuration changes are propagated without restart

In Spring Boot + Kubernetes, the standard ZDD stack is:
- **Rolling deploy** — Kubernetes replaces pods one at a time; old pods coexist with new ones
- **Readiness probe** — Kubernetes only routes traffic to ready pods
- **Graceful shutdown** — Spring Boot 2.3+ finishes in-flight requests before shutting down
- **Flyway/Liquibase** — database migrations run at startup; must be backward compatible
- **Spring Cloud Config / `@RefreshScope`** — configuration changes without pod restart

---

## 2. Intuition

Think of a zero-downtime deploy as changing the tyres on a moving car. The car (your service)
cannot stop; passengers (requests) are in-flight. The constraint:

- You can change one tyre at a time (rolling deploy)
- The car must work with mixed tyres (old + new code versions in parallel)
- You cannot change the axle geometry while passengers are sitting (non-backward-compatible DB migration)

The Expand-Contract pattern is the "mixed-tyre" rule: add new capabilities alongside old ones
first, then remove the old ones only after all instances have moved to new.

**Key insight:** The most dangerous deploy is the one that works in isolation but fails when
old and new code share the same database table for 5–10 minutes during rollout. Every
`ALTER TABLE` must be tested with both old and new application code running against it.

---

## 3. Core Principles

### 3.1 Expand-Contract pattern (the only safe schema migration approach)

```
PHASE 1: EXPAND (deploy with new DB schema, old code still works)
  - Add nullable column
  - Add new table
  - Create new index (CONCURRENTLY on PostgreSQL — no table lock)

PHASE 2: MIGRATE (deploy new code; old code still compatible)
  - Deploy new app version (writes new column; reads new column with null fallback)
  - Backfill existing rows (batch UPDATE)
  - Old instances still running: they ignore the new column

PHASE 3: CONTRACT (final cleanup; old code is gone)
  - Add NOT NULL constraint (after all instances run new code)
  - Drop old column
  - Remove compatibility logic from new code
```

### 3.2 Readiness vs Liveness probes

- **Readiness probe**: "am I ready to receive traffic?" — used by Kubernetes to gate traffic.
  Return `200 OK` only when DB connections are established, Kafka consumer is ready, and cache
  is warmed. Return `503` during startup or graceful shutdown.
- **Liveness probe**: "am I alive?" — used by Kubernetes to restart a stuck pod.
  Return `200 OK` unless the application is in a deadlock or unrecoverable state. Must not
  call external dependencies (a DB outage should not cause your pod to be killed).

**Critical mistake:** using the same implementation for both. A liveness probe that checks DB
connectivity will kill your pods when the DB is slow — creating a cascade of restarts that
makes the outage worse.

### 3.3 Graceful shutdown sequence

```
SIGTERM received
    |
    v
1. Kubernetes removes pod from Service endpoint (traffic stops coming)
    |
    v (2s grace period for endpoint removal to propagate)
    |
2. Spring Boot sets readiness probe to REFUSING (HTTP 503)
    |
    v
3. Spring Boot waits for in-flight requests to complete (server.shutdown.timeout)
    |
    v
4. Spring Boot closes Kafka consumers (commits outstanding offsets)
    |
    v
5. Spring Boot closes DB connection pool (HikariCP drains)
    |
    v
6. Spring context closes (beans destroyed)
    |
    v
7. JVM exits (exit code 0 → Kubernetes marks pod as succeeded)
```

---

## 4. Configuration

### 4.1 Spring Boot graceful shutdown + readiness probe configuration

```yaml
server:
  shutdown: graceful                        # Spring Boot 2.3+; waits for in-flight requests
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s         # max time to wait per phase (Tomcat drain, etc.)

management:
  endpoint:
    health:
      probes:
        enabled: true                       # enables /actuator/health/liveness and /actuator/health/readiness
      show-details: when-authorized
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

### 4.2 Kubernetes pod spec — probes and terminationGracePeriodSeconds

```yaml
spec:
  terminationGracePeriodSeconds: 60        # must be > spring.lifecycle.timeout-per-shutdown-phase
  containers:
    - name: order-service
      image: order-service:1.2.0
      readinessProbe:
        httpGet:
          path: /actuator/health/readiness
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 5
        failureThreshold: 3                # remove from traffic after 3 consecutive failures
      livenessProbe:
        httpGet:
          path: /actuator/health/liveness
          port: 8080
        initialDelaySeconds: 30            # give the app time to fully start
        periodSeconds: 10
        failureThreshold: 5                # restart after 5 consecutive failures (high bar)
      lifecycle:
        preStop:
          exec:
            command: ["sleep", "5"]        # wait 5s for endpoint removal to propagate BEFORE SIGTERM
```

The `preStop: sleep 5` is the most commonly missed ZDD detail: Kubernetes sends SIGTERM and
removes the pod from Endpoints almost simultaneously, but the removal takes 1–3s to propagate
through `kube-proxy` to all nodes. Without the `preStop` sleep, some requests still arrive
at the pod after it starts shutting down, causing connection-refused errors.

---

### 4.3 Flyway backward-compatible migration examples

```java
// FLYWAY MIGRATION: V2__add_email_verified_column.sql
-- SAFE: nullable column is backward compatible with old code that doesn't know about it
ALTER TABLE users ADD COLUMN email_verified BOOLEAN;  -- nullable → old code ignores it

-- SAFE: create index concurrently (no table lock)
CREATE INDEX CONCURRENTLY idx_users_email_verified ON users(email_verified);

-- NOT SAFE (deploy later, after all instances on new code):
-- ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;
```

```java
// FLYWAY MIGRATION: V3__rename_status_column.sql
-- WRONG: renames the column; old code that reads 'status' will fail immediately
-- ALTER TABLE orders RENAME COLUMN status TO order_status;  -- BREAKING!

-- CORRECT: add new column, backfill, then drop old (3-phase Expand-Contract)
-- Phase 1 migration:
ALTER TABLE orders ADD COLUMN order_status VARCHAR(50);
UPDATE orders SET order_status = status;  -- backfill (use batch if millions of rows)

-- Phase 2: app code reads order_status with NULL fallback to status
-- Phase 3 migration (after all pods on new code):
ALTER TABLE orders DROP COLUMN status;
```

### 4.4 `@RefreshScope` for runtime configuration changes

```java
// Config bean updated without restart via Spring Cloud Config + /actuator/refresh
@RefreshScope          // bean is re-created when /actuator/refresh is called
@ConfigurationProperties("payment")
public class PaymentConfig {
    private int maxRetries = 3;
    private Duration timeout = Duration.ofSeconds(5);
    private String apiKey;
    // getters + setters
}

// Spring Cloud Bus: broadcasts refresh to all instances via Kafka/RabbitMQ
// POST /actuator/busrefresh → all instances receive message → @RefreshScope beans recreated
```

**`@RefreshScope` pitfalls:**
- Beans in `@RefreshScope` are proxied; beans that inject them get a new instance on refresh.
  Any cached state in a `@RefreshScope` bean is lost on refresh.
- `@Scheduled` methods in a `@RefreshScope` bean are not re-scheduled after refresh — the
  scheduler holds a reference to the old bean.
- Use `@RefreshScope` only for truly dynamic configuration (feature flags, rate limit values,
  API keys); use `@Value` with `@ConfigurationProperties` for static config.

---

## 5. Architecture Diagrams

### Rolling deploy with database compatibility window

```
t=0: All pods on v1.0; DB has column 'status'
+--------+   +--------+   +--------+
|  v1.0  |   |  v1.0  |   |  v1.0  |
|  pod1  |   |  pod2  |   |  pod3  |
+--------+   +--------+   +--------+
                                |
                            DB: [status col only]

t=5min: Pod3 replaced; Flyway adds nullable 'order_status'
+--------+   +--------+   +--------+
|  v1.0  |   |  v1.0  |   |  v2.0  |
|  pod1  |   |  pod2  |   |  pod3* |  ← v2.0 writes BOTH status + order_status
+--------+   +--------+   +--------+
                                |
                            DB: [status col + order_status col (nullable)]
v1.0 pods: read 'status' (ignores order_status) ← BACKWARD COMPATIBLE
v2.0 pods: read 'order_status' with null-fallback to 'status'

t=10min: All pods on v2.0
+--------+   +--------+   +--------+
|  v2.0  |   |  v2.0  |   |  v2.0  |
|  pod1  |   |  pod2  |   |  pod3  |
+--------+   +--------+   +--------+
                                |
                            DB: [status + order_status (both populated)]

t+1 day: Phase 3 migration — DROP status column (separate deployment)
```

### Graceful shutdown timeline

```
t=0s    SIGTERM received + preStop hook starts
t=0s    preStop: sleep 5s                      ← wait for endpoint propagation
t=5s    preStop completes; SIGTERM delivered to JVM
t=5s    Spring sets readiness = REFUSING (503)
t=5s    Tomcat stops accepting new connections
t=5s    In-flight requests continue processing
t=20s   All in-flight requests complete (or server.shutdown.timeout reached)
t=20s   Kafka consumers commit offsets + close
t=22s   HikariCP drains + closes all connections
t=25s   Spring context destroys all beans
t=26s   JVM exits (clean)

terminationGracePeriodSeconds = 60 → Kubernetes force-kills at t=60s if JVM hasn't exited
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Broken migration — additive AND restrictive change in one deployment

**Broken — two breaking changes in one migration:**
```sql
-- V3__order_processing_refactor.sql  (BROKEN — multiple breaking changes at once)
ALTER TABLE orders RENAME COLUMN status TO order_status;   -- breaks v1 code reading 'status'
ALTER TABLE orders ALTER COLUMN total SET NOT NULL;         -- breaks v1 code inserting null total
DROP TABLE order_temp;                                      -- breaks v1 code if it still writes temp

-- If v1 and v2 coexist for 5 minutes: v1 pods fail to read 'status' → 500 errors
```

**Fixed — three separate deployments:**
```
Deployment A (v2.0 migration):
  ALTER TABLE orders ADD COLUMN order_status VARCHAR(50);   -- safe: nullable, additive
  UPDATE orders SET order_status = status WHERE order_status IS NULL;  -- backfill

Deployment B (v2.0 code):
  App reads 'order_status' with null coalesce to 'status'
  App writes both 'status' AND 'order_status' during transition

Deployment C (v2.1 cleanup, after v2.0 fully deployed):
  ALTER TABLE orders DROP COLUMN status;                    -- safe: no code reads it
  ALTER TABLE orders ALTER COLUMN order_status SET NOT NULL; -- safe: all rows backfilled
  ALTER TABLE orders ALTER COLUMN total SET NOT NULL;        -- safe: all rows have total
```

---

### 6.2 Custom readiness indicator that checks business dependencies

```java
@Component
public class OrderServiceReadinessIndicator implements HealthIndicator {

    private final DataSource dataSource;
    private final KafkaAdmin kafkaAdmin;

    public OrderServiceReadinessIndicator(DataSource dataSource, KafkaAdmin kafkaAdmin) {
        this.dataSource = dataSource;
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(2);   // 2-second timeout
        } catch (SQLException e) {
            // Return DOWN: pod will be removed from traffic
            return Health.down()
                .withDetail("database", "connection failed: " + e.getMessage())
                .build();
        }

        // Check that required Kafka topics exist
        Map<String, TopicDescription> topics;
        try {
            topics = kafkaAdmin.describeTopics("order.events", "order.dlq");
        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "topic check failed: " + e.getMessage())
                .build();
        }
        if (!topics.containsKey("order.events")) {
            return Health.down().withDetail("kafka", "topic order.events missing").build();
        }

        return Health.up().build();
    }
}
```

**Separate liveness indicator (must not check external dependencies):**
```java
@Component
public class OrderServiceLivenessIndicator implements HealthIndicator {

    private final AtomicBoolean isAlive = new AtomicBoolean(true);

    // Called by watchdog thread if a deadlock or unrecoverable error is detected
    public void markUnhealthy() { isAlive.set(false); }

    @Override
    public Health health() {
        if (!isAlive.get()) {
            return Health.down().withDetail("reason", "unrecoverable error detected").build();
        }
        return Health.up().build();
    }
}
```

---

### 6.3 Canary deployment with Spring Cloud Gateway routing

```yaml
# Spring Cloud Gateway: route 5% of traffic to canary (v2) pods
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-canary
          uri: lb://order-service-canary    # canary deployment (v2, separate K8s Deployment)
          predicates:
            - Weight=group1, 5             # 5% of traffic to canary
        - id: order-service-stable
          uri: lb://order-service           # stable deployment (v1)
          predicates:
            - Weight=group1, 95            # 95% to stable
```

Monitor canary error rate and P99 latency via Micrometer gauges (see
[otel_observability_for_spring.md](./otel_observability_for_spring.md)); promote to 100%
only when canary metrics are within 5% of stable.

---

### 6.4 Blue-green deployment with feature flags

```java
// Feature flag: controlled via Spring Cloud Config without restart
@RefreshScope
@ConfigurationProperties("features")
public class FeatureFlags {
    private boolean newCheckoutFlow = false;  // default off

    public boolean isNewCheckoutFlowEnabled() { return newCheckoutFlow; }
    // setter required for @ConfigurationProperties
    public void setNewCheckoutFlow(boolean v) { this.newCheckoutFlow = v; }
}

@Service
public class CheckoutService {

    @Autowired
    private FeatureFlags flags;

    public CheckoutResult checkout(Cart cart) {
        if (flags.isNewCheckoutFlowEnabled()) {
            return newCheckoutFlow(cart);
        }
        return legacyCheckoutFlow(cart);
    }
}
```

**Enable new flow without deployment:**
```bash
# Update config server (e.g., Git-backed Spring Cloud Config)
# Set features.new-checkout-flow=true in config repo
# Trigger Spring Cloud Bus refresh:
curl -X POST http://config-server/actuator/busrefresh
# All instances with @RefreshScope re-read the config; new checkout flow enabled
```

---

### 6.5 Database migration dry-run in CI (Flyway validate)

```java
@SpringBootTest
@Testcontainers
class FlywayMigrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Test
    void allMigrations_shouldApplyCleanly() {
        // Flyway auto-runs on Spring Boot startup; if it fails, this test fails
        MigrationInfoService info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(info.failed()).isEmpty();
    }

    @Test
    void migration_shouldBeBackwardCompatible_withOldCode() {
        // Simulate v1.0 schema + v2.0 migration: verify v1.0 queries still work
        // after the migration runs
        jdbcTemplate.query("SELECT id, status FROM orders LIMIT 1", rs -> null);
        // If the migration dropped 'status', this throws SQLException → test fails
    }
}
```

---

## 7. Real-World Examples

### GitHub — Expand-Contract for GitHub Issues rename migration

GitHub migrated the `issues` table's `state` column from a VARCHAR to an ENUM in 2017 — a
potentially dangerous migration on a multi-billion-row table. They used a 3-phase Expand-Contract:
Phase 1: add `state_new` ENUM column, nullable. Phase 2: dual-write to both columns; backfill
`state_new` from `state` in a background job over 2 weeks. Phase 3: drop `state`, rename
`state_new` to `state`. Zero downtime; the backfill used `WHERE state_new IS NULL LIMIT 1000`
to process 1,000 rows per transaction, avoiding table locks. Reference: GitHub Engineering
blog, "Online migrations at scale" (2017).

### Shopify — preStop hook eliminates deployment-time 502s

Shopify discovered in 2019 that rolling deployments caused ~0.1% of requests to fail with
connection-refused errors. Root cause: Kubernetes removed pods from the Endpoints list and
sent SIGTERM simultaneously, but `kube-proxy` took 2–3s to update iptables rules on all nodes.
Requests routing to dying pods received connection-refused during this 2–3s window. Fix:
`preStop: exec: sleep 5` delays the pod shutdown for 5 seconds, giving `kube-proxy` time to
remove the pod from all routing tables before the pod stops accepting connections. This
eliminated deployment-time 502s completely. Reference: Shopify Engineering blog (2020).

### LinkedIn — Zero-downtime schema migration on 1.5B row table

LinkedIn migrated a 1.5-billion-row `member_profile` table to add a `verified_skills` JSON
column in 2021. Direct `ALTER TABLE ADD COLUMN` would lock the table for 40+ minutes (unacceptable
for a global production system). Solution: (1) `ALTER TABLE ADD COLUMN verified_skills JSONB` —
PostgreSQL adds nullable column with no table lock (<1s, metadata-only change on modern PostgreSQL).
(2) Background job backfills 10,000 rows at a time using `FOR UPDATE SKIP LOCKED` to avoid
contention. (3) New code reads `verified_skills` with `COALESCE(verified_skills, '[]'::jsonb)`.
(4) After 100% backfill: `ALTER TABLE ALTER COLUMN verified_skills SET DEFAULT '[]'`. Backfill
job ran for 6 hours with no production impact. Reference: LinkedIn Engineering blog (2022).

### Netflix — Spring Cloud Config + Bus for A/B testing

Netflix uses Spring Cloud Config backed by a Git repository to manage per-service feature flags.
When a new recommendation algorithm is deployed, it is initially behind a `features.recommendation.v2=false`
flag. After initial validation (circuit breaker, error rate), the flag is set to `true` in the
Config Server Git repo and `POST /actuator/busrefresh` is called via a release tool — updating
all 1,000+ instances via Kafka-backed Spring Cloud Bus in under 10 seconds without any pod
restart. Reference: Netflix Engineering blog (2021).

### Amazon — Deployment automation with health checks gating progression

Amazon's deployment pipeline (Spinnaker + CodeDeploy) gates each rolling step on health checks.
Deployment rule: advance to the next pod batch only if: (a) readiness probe returns 200 for 60
consecutive seconds, (b) error rate (4xx + 5xx) is within 2% of baseline, (c) P99 latency is
within 20% of baseline. If any gate fails, the deployment pauses and pages on-call. This
automation means that a bad migration or config change is caught after ~10% rollout (1–2 pods),
not after 100%. Reference: Amazon Builder's Library, "Automating safe, hands-off deployments" (2019).

---

## 8. Tradeoffs

| Strategy | Downtime | Rollback speed | DB migration constraint | Cost |
|----------|---------|---------------|------------------------|------|
| Rolling deploy | Zero | Slow (roll forward then back) | Must be backward compatible | Low (existing infra) |
| Blue-green | Zero | Instant (flip LB) | Both versions need to share DB | 2× infra cost temporarily |
| Canary | Zero | Instant (route 0% to canary) | Same as rolling (canary + stable coexist) | Minimal extra cost |
| Recreate | Full downtime | N/A (always forward) | Can make breaking changes | Simplest |
| Shadow deploy | Zero | N/A (no traffic) | No constraint | 2× infra + complex |

| Config change strategy | Restart required | Risk | Use case |
|----------------------|-----------------|------|---------|
| `@RefreshScope` + Bus | No | Bean state lost; potential startup side effects | Feature flags, rate limits |
| Pod restart (new ConfigMap) | Yes | Brief downtime if no rolling | Sensitive config (DB URL, secrets) |
| Spring Cloud Config pull | No (with @RefreshScope) | Same as @RefreshScope | Any dynamic config |
| Kubernetes Secrets rotation | No (with volume-mounted secrets) | Application must re-read the file | Credential rotation |

---

## 9. When to Use / When NOT to Use

### Use rolling deploy when:
- Your migration is backward compatible (additive changes: add nullable column, new index)
- You can accept 1–3 minute transition windows where old and new code coexist
- Infrastructure is already Kubernetes-based

### Use blue-green when:
- Instant rollback is required (financial systems, payment processing)
- The migration is too complex for backward-compatible rolling deploy
- You can afford 2× infrastructure cost for the deployment window

### Use canary when:
- You want to validate new code against real production traffic before full rollout
- The service has high enough traffic that a 5% canary sample is statistically significant

### Avoid rolling deploy when:
- The schema migration is NOT backward compatible (column rename, NOT NULL addition, type change)
  — use blue-green or plan a 3-phase Expand-Contract before attempting rolling

---

## 10. Common Pitfalls

### Pitfall 1 — Non-backward-compatible migration in same deploy as code change

**Broken:**
```
Deploy v2.0 (contains Flyway migration + new code in one artifact):
Flyway: ALTER TABLE orders RENAME COLUMN status TO order_status;  -- breaking!
Code: reads 'order_status'

During rolling deploy: v1.0 pods still running → read 'status' → column not found → 500 errors
```

**Fixed:** Deploy the Flyway migration (additive only) first, then deploy the code change.
The migration must run forward-compatible with the OLD code still running.

---

### Pitfall 2 — Liveness probe checking external dependencies

```yaml
# BROKEN: liveness probe calls /actuator/health which checks DB
# When DB is slow, health returns DOWN → Kubernetes kills pod → cascade restart
livenessProbe:
  httpGet:
    path: /actuator/health   # checks DB, Kafka, Redis — ALL external deps
```

**Fixed:**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness   # only checks LivenessState (internal JVM state)
readinessProbe:
  httpGet:
    path: /actuator/health/readiness  # checks DB, Kafka, etc.
```

---

### Pitfall 3 — Missing preStop sleep causes deployment-time errors

Without `preStop: sleep 5`, pods receive SIGTERM while still receiving traffic (Kubernetes
endpoint removal takes 2–3s to propagate). New connections to the pod get ECONNREFUSED.
Always include `preStop: exec: sleep 5` in production pod specs.

---

### Pitfall 4 — `terminationGracePeriodSeconds` shorter than `timeout-per-shutdown-phase`

```yaml
# BROKEN: SIGKILL arrives before Spring Boot finishes shutdown
terminationGracePeriodSeconds: 20
spring.lifecycle.timeout-per-shutdown-phase: 30s
# After 20s, Kubernetes SIGKILL kills the JVM mid-shutdown → in-flight requests aborted
```

**Fixed:** `terminationGracePeriodSeconds = preStop + spring.lifecycle.timeout-per-shutdown-phase + 10s buffer`
= 5 + 30 + 10 = 45s minimum.

---

### Pitfall 5 — `@RefreshScope` on a `@KafkaListener` bean

`@RefreshScope` proxies the bean and re-creates it on `/actuator/refresh`. If a `@KafkaListener`
bean is in `@RefreshScope`, its Kafka container (the listener thread) is stopped and a new one
is started on each refresh. During the 100–200ms restart, messages are not consumed — causing
consumer lag spikes. Fix: keep `@KafkaListener` beans in non-`@RefreshScope` beans and inject
the `@RefreshScope` config via a non-proxied accessor method.

---

### Pitfall 6 — Flyway migration locks table during peak traffic

`ALTER TABLE` operations like `ADD CONSTRAINT NOT NULL` on large tables acquire `ACCESS EXCLUSIVE`
lock — blocking all reads and writes for the migration duration. On a 10M-row table, this can
take 30–60 seconds, causing a 500-error spike. Fix: (1) Add column as nullable first (no lock);
(2) backfill in small batches at off-peak hours; (3) add the NOT NULL constraint with PostgreSQL
15's `NOT VALID` + `VALIDATE CONSTRAINT` pattern (NOT VALID takes no lock; VALIDATE acquires
a weaker share lock).

---

## 11. Technologies & Tools

| Tool | Role | Notes |
|------|------|-------|
| Flyway / Liquibase | Schema migrations | Flyway: SQL-first; Liquibase: XML/YAML/JSON; both support Spring Boot auto-run |
| Spring Boot Actuator probes | `/health/liveness` + `/health/readiness` | Separate endpoints since Spring Boot 2.3 |
| Spring Cloud Config | Centralised config + `@RefreshScope` | Git-backed or Vault-backed; Spring Boot auto-configures |
| Spring Cloud Bus | Broadcast refresh to all instances | Kafka or RabbitMQ backed |
| Kubernetes `rollingUpdate` strategy | Rolling deploy | `maxUnavailable: 0, maxSurge: 1` for zero-downtime |
| Spinnaker | Advanced deploy pipelines (canary, blue-green) | Netflix OSS; integrates with GKE, EKS |
| ArgoRollouts | Progressive delivery (canary, blue-green) | Kubernetes-native; analysis + promotion |
| Spring Cloud Gateway `WeightRoutePredicateFactory` | Canary routing | Weighted traffic split to canary pods |
| Testcontainers Flyway test | Verify migration backward compatibility | See [testcontainers_and_test_strategy.md](./testcontainers_and_test_strategy.md) |
| `pg_repack` (PostgreSQL) | Table rebuild without lock | Allows `ADD CONSTRAINT` on large tables without `ACCESS EXCLUSIVE` |

---

## 12. Interview Questions with Answers

**Q1. What is a zero-downtime deployment and what are the key requirements to achieve it in Spring Boot?**
A zero-downtime deployment is a rolling replacement of running instances where no requests
are dropped or return errors during the transition. In Spring Boot + Kubernetes, four requirements
must be met: (1) Graceful shutdown — `server.shutdown=graceful` ensures in-flight requests
complete before the pod exits. (2) Readiness probe — Kubernetes only routes traffic to pods
reporting `/actuator/health/readiness` as UP, preventing new traffic from reaching pods during
startup. (3) `preStop` sleep — a `preStop: sleep 5` hook delays shutdown by 5 seconds, giving
Kubernetes time to remove the pod from load balancer routing before SIGTERM is delivered.
(4) Backward-compatible DB migrations — since old and new pods coexist for 1–5 minutes, all
schema changes must be readable by both versions simultaneously.

**Q2. Explain the Expand-Contract pattern for database schema migrations.**
Expand-Contract (also called Parallel Change) is a three-phase approach to making
non-backward-compatible schema changes without downtime. Phase 1 (Expand): add the new
structure alongside the old one — add nullable columns, new tables, new indexes. Deploy code
that writes to both old and new structures. Phase 2 (Migrate): backfill existing data from
the old to the new structure; both old and new code versions continue working. Phase 3 (Contract):
once all instances run the new code and data is fully migrated, remove the old structure —
drop the old column, add NOT NULL constraints, remove the compatibility code. The key constraint
is that each phase is a separate deployment, separated by time: Phase 1 must complete on all
instances before Phase 2 is deployed, and Phase 2 must run everywhere before Phase 3.

**Q3. Why must liveness and readiness probes use separate implementations?**
A readiness probe answers "am I ready to receive traffic?" and should check that all
dependencies (DB, Kafka, cache) are healthy. If Redis is down, returning `503` from readiness
is correct — the pod should stop receiving traffic. A liveness probe answers "am I alive?"
and should check only internal JVM state (no external calls). If a liveness probe checks DB
health and the DB is slow, Kubernetes kills and restarts the pod — creating a cascade of
restarts that makes the outage worse: all pods restart simultaneously, none are ready, 100%
downtime. Spring Boot 2.3+ provides `/actuator/health/liveness` (internal state only) and
`/actuator/health/readiness` (external deps) as separate endpoints. Always use the appropriate
endpoint for each probe type.

**Q4. What does `server.shutdown=graceful` do and what are the related configuration values to set?**
`server.shutdown=graceful` (Spring Boot 2.3+) configures Tomcat/Netty to stop accepting new
requests immediately on shutdown but allow in-flight requests to complete before the server
shuts down. This is controlled by `spring.lifecycle.timeout-per-shutdown-phase` (default 30s):
Spring waits up to this duration for each shutdown phase (Tomcat drain, Kafka consumer close,
HikariCP drain). If requests don't complete within the timeout, they are forcibly terminated.
Related settings: `terminationGracePeriodSeconds` in the Kubernetes pod spec must be set to
at least `preStop sleep time + spring.lifecycle.timeout-per-shutdown-phase + 10s buffer`.
With `preStop sleep 5s` and `timeout 30s`, set `terminationGracePeriodSeconds: 60` minimum.
Without this coordination, Kubernetes sends SIGKILL before Spring finishes graceful shutdown.

**Q5. How do you handle configuration changes that must propagate to all instances without a restart?**
Use Spring Cloud Config (Git-backed) + `@RefreshScope` + Spring Cloud Bus. Configuration values
stored in a Config Server Git repository are fetched by all instances on startup. When a value
changes, `POST /actuator/busrefresh` (or the Bus trigger from a Git webhook) broadcasts a
`RefreshRemoteApplicationEvent` via Kafka or RabbitMQ to all instances. Each instance
re-fetches its config from the Config Server and re-creates all `@RefreshScope` beans. The
refresh propagates in <10 seconds to 1,000+ instances. Key limitations: (1) `@RefreshScope`
beans lose their internal state on refresh (caches, counters); (2) `@Scheduled` methods on
`@RefreshScope` beans stop after refresh; (3) database URLs and connection pool settings should
NOT be `@RefreshScope` — changing them at runtime requires careful connection pool lifecycle
management.

**Q6. How do you perform a `preStop` hook in Kubernetes to achieve zero-downtime rolling deploys?**
The `preStop` hook runs before Kubernetes sends SIGTERM to the container. The standard ZDD
pattern is `preStop: exec: command: ["sleep", "5"]` — this delays SIGTERM by 5 seconds.
Why: Kubernetes removes the pod from the `Service` endpoints list at the same time it delivers
SIGTERM, but the endpoint removal must propagate through `kube-proxy` and iptables on all
nodes (typically 1–3 seconds). During this propagation window, some nodes still route new
connections to the terminating pod. The `preStop` sleep ensures the pod continues accepting
connections for 5 seconds while the endpoint removal propagates, then gracefully shuts down
after SIGTERM. Without this, 0.1–1% of requests during rolling deploys receive ECONNREFUSED.

**Q7. What are the risks of using `@RefreshScope` on beans that maintain state?**
`@RefreshScope` proxies the annotated bean; on `/actuator/refresh`, the proxy is cleared and
the bean is re-instantiated. Risks: (1) State loss — any in-memory state (rate limiter counters,
warm caches, circuit breaker state) is reset to initial values. A rate limiter that was tracking
50 in-flight requests resets to 0 — temporarily allowing more than the configured limit.
(2) `@Scheduled` loss — the `TaskScheduler` holds a reference to the old bean instance; after
refresh, the scheduled method is no longer called until the next application restart. Fix: use
`@Scheduled` on a non-`@RefreshScope` bean that reads config from a `@RefreshScope` config
object. (3) Circular proxy issues — if bean A (non-scope) injects bean B (`@RefreshScope`),
bean A gets a proxy reference; calling B's methods always delegates to the current bean
version, which is correct. But if B injects A, the cycle can cause issues with Spring's proxy
creation. Mitigate by keeping `@RefreshScope` on config-only beans, not service beans.

**Q8. What is the difference between a canary deploy and a blue-green deploy?**
Blue-green maintains two complete identical production environments (blue = current, green = new);
traffic is switched all-at-once from blue to green at deploy time via a load balancer update.
Rollback is instant: switch back to blue. Cost: 2× infrastructure during the deploy window.
Canary deploys route a small percentage of traffic (1–5%) to the new version alongside the
full production environment. Canary success is measured by error rate, latency, and business
metrics. Only after validation is the canary promoted to 100%. Canary is slower to complete
(hours vs minutes for blue-green) but provides real-traffic validation with a limited blast
radius. Use blue-green when: instant rollback is required, or the deploy involves stateful
data plane changes. Use canary when: validating algorithmic changes (recommendation, pricing)
with real user behaviour, or when 2× infrastructure cost is not justified.

**Q9. How do you add a NOT NULL constraint to a large existing table without downtime?**
On PostgreSQL 15+, use the `NOT VALID` + `VALIDATE CONSTRAINT` pattern: (1) `ALTER TABLE orders
ADD CONSTRAINT orders_status_not_null CHECK (status IS NOT NULL) NOT VALID` — this adds the
constraint immediately without checking existing rows (metadata-only, no lock). (2) Run a
background backfill: `UPDATE orders SET status = 'PENDING' WHERE status IS NULL` in small batches.
(3) `ALTER TABLE orders VALIDATE CONSTRAINT orders_status_not_null` — this acquires a ShareLock
(weaker than AccessExclusive) and validates all rows. Reads are not blocked; only DDL operations
are blocked. (4) After validation, use `ALTER TABLE orders ALTER COLUMN status SET NOT NULL` —
on PostgreSQL 12+, this detects the CHECK constraint and completes without a table rewrite.
For very large tables (>100M rows), Flyway can execute steps 2–3 in a separate migration with
a loop, committing every 10,000 rows.

**Q10. How do you test that your Flyway migrations are backward compatible in CI?**
Run three test scenarios using Testcontainers in CI: (1) Forward compatibility: apply all
migrations up to version N-1, then run the new migration V_N, then execute SQL queries from
the OLD application code against the migrated schema — verify no errors. (2) Application
startup test: `@SpringBootTest` with Testcontainers PostgreSQL applies all Flyway migrations at
startup; test failure = broken migration SQL. (3) Dual-version test: apply V_N migration; then
instantiate both the old and new JPA entity classes and verify both can `SELECT` from the
migrated table without errors. This is the key test: it catches the case where the migration
adds a NOT NULL column that the old entity doesn't know about — the old `INSERT` will fail.
Automate these tests in a CI stage that runs between the "build new version" and "deploy to
staging" steps. Reference: [testcontainers_and_test_strategy.md](./testcontainers_and_test_strategy.md).

**Q11. Describe how Spring Cloud Config integrates with Vault for secret rotation without restart.**
Spring Cloud Config supports a Vault backend (`spring.cloud.config.server.vault.host`). Application
instances fetch secrets (DB passwords, API keys) from Vault via the Config Server at startup.
When a secret is rotated: (1) The new secret is written to Vault under a new version. (2) The
Config Server's `/actuator/refresh` (or Bus broadcast) causes all instances to re-fetch from
Vault. (3) Each instance with a `@RefreshScope` `DataSourceProperties` bean re-instantiates the
bean with the new password. (4) HikariCP detects that the DataSource config changed and validates
new connections with the new password; old connections are drained as they complete. For DB
password rotation specifically, use HikariCP's `allowPoolSuspension=true` and suspend the pool
during the brief window when old and new passwords coexist (Vault dynamic secrets transition period).
This achieves zero-downtime credential rotation without pod restart.

**Q12. What is ArgoRollouts and how does it implement canary deployments differently from a rolling update?**
ArgoRollouts is a Kubernetes-native progressive delivery controller that replaces `Deployment`
with a `Rollout` resource supporting canary and blue-green strategies. Unlike a Kubernetes
rolling update (which gradually replaces all pods), ArgoRollouts gives fine-grained control:
route exactly 5% of traffic to canary, wait 30 minutes, check Prometheus `AnalysisTemplate`
metrics (error rate, P99 latency), automatically abort and rollback if metrics fail the gates.
Integration with Spring Boot: annotate the `Rollout` with the readiness probe path (`/actuator/health/readiness`);
ArgoRollouts only advances the canary when the required number of canary pods pass the readiness
probe. The `AnalysisTemplate` queries Prometheus for Spring Boot's
`http_server_requests_seconds_bucket` (P99) and `http_server_requests_errors_total`; if either
exceeds the configured threshold during the canary window, the rollout is automatically aborted
and old pods restored.

---

## 13. Best Practices

- **Schema migrations must precede code changes** — deploy Flyway migration (additive only)
  before deploying the code that uses the new schema.
- **Use separate liveness and readiness probe endpoints** — never mix external dependency checks
  into the liveness probe.
- **Always include `preStop: sleep 5`** in production pod specs — eliminates deployment-time
  ECONNREFUSED errors.
- **Set `terminationGracePeriodSeconds ≥ preStop + shutdown-timeout + 10s buffer`**.
- **Test migrations with old code before deploying new code** — the migration must not break
  the currently running version.
- **Use `@RefreshScope` only for config beans, not service beans** — avoid state loss on refresh.
- **Use canary deployments for algorithmic or pricing changes** — validate with real traffic
  before 100% rollout; a 5% canary with Prometheus gate is production-safe.
- **Instrument deploy events in Grafana** — annotate deployments on metric dashboards so P99
  changes after deploy are instantly visible.
- **Document the Expand-Contract phases** — track which phase each schema change is in via
  Flyway migration comments; Phase 3 cleanup often gets forgotten.

---

## 14. Case Study

### Zero-downtime migration in design_batch_pipeline.md

Reference case study: [../design_batch_pipeline.md](../design_batch_pipeline.md)

The batch pipeline ETL job reads from a `customers` table and writes to a processed `customers`
table. A schema migration adds a `tier_version` column to support the new tiering algorithm:

**Phase 1 migration (V4__add_tier_version.sql):**
```sql
-- SAFE: nullable column, additive change, backward compatible
ALTER TABLE customers ADD COLUMN tier_version INT;
```
- Old batch job: ignores `tier_version` in SELECT/INSERT (nullable, no default required)
- New batch job: reads `tier_version`, writes it on each processed row

**Phase 2 code change (deployed next):**
```java
// ItemProcessor reads tier_version; null-safe (old rows have null)
@Override
public CustomerOutput process(Customer input) {
    int version = input.getTierVersion() != null ? input.getTierVersion() : 1;
    // ... process with version
    return new CustomerOutput(input, computedTier, version + 1);
}
```

**Phase 3 migration (deployed after 100% rollout of Phase 2):**
```sql
-- SAFE: all rows now have tier_version set by Phase 2 batch runs
UPDATE customers SET tier_version = 1 WHERE tier_version IS NULL;  -- backfill
ALTER TABLE customers ALTER COLUMN tier_version SET DEFAULT 1 NOT NULL;
```

**Key result:** The batch job continued processing during the entire 3-phase migration with no
job failures, no data corruption, and no pause in customer tier updates. Total migration window:
48 hours (Phase 1 to Phase 3); actual downtime: 0 seconds.

See also: [testcontainers_and_test_strategy.md](./testcontainers_and_test_strategy.md) for
testing Flyway migrations for backward compatibility with Testcontainers.
