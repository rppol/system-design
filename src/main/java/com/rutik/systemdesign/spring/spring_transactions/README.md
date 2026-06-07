# Spring Transactions — Deep Dive

---

## 1. Concept Overview

Spring's transaction management provides a unified abstraction over multiple underlying transaction mechanisms: JDBC DataSource transactions, JPA/Hibernate, JTA (XA across multiple resources), and others. The programming model is the same regardless of the underlying technology: annotate a method with @Transactional and Spring wraps it in a proxy that begins, commits, or rolls back a transaction around the method invocation.

Spring transactions encompass:
- **Declarative transactions**: @Transactional annotation processed by AOP proxies
- **Programmatic transactions**: TransactionTemplate and PlatformTransactionManager for fine-grained control
- **Propagation**: how a transaction relates to an existing outer transaction
- **Isolation**: the degree of visibility a transaction has into uncommitted work by other transactions
- **Rollback rules**: which exceptions trigger rollback
- **Synchronization**: TransactionSynchronizationManager callbacks (afterCommit, beforeCompletion, etc.)
- **Distributed transactions**: JTA for XA across multiple resources; Saga pattern as a modern alternative

The central abstraction is PlatformTransactionManager. Spring Boot auto-configures the appropriate implementation based on what is on the classpath:
- JDBC only: DataSourceTransactionManager
- JPA on classpath: JpaTransactionManager (wraps EntityManager/Session lifecycle)
- JTA: JtaTransactionManager (delegates to container or standalone Atomikos/Bitronix)

---

## 2. Intuition

One-line analogy: @Transactional is the lock on a bank vault door — it ensures all operations inside either all complete together or none do, regardless of what goes wrong in the middle.

Mental model: think of a transaction as a temporary scratch pad. All reads and writes happen on the scratch pad. At commit, the scratch pad is written to the real store atomically. On rollback, the scratch pad is discarded. Other transactions cannot see your scratch pad until you commit (depending on isolation level).

Why it matters: without transaction management, a multi-step business operation (debit account A, credit account B, write audit log) can leave data in an inconsistent half-state if an exception occurs between steps. ACID guarantees prevent this.

Key insight: Spring transactions work via AOP proxies. The proxy only intercepts calls that come through the proxy — internal calls within the same class bypass the proxy entirely. This is the single most common Spring transaction gotcha and a nearly universal interview question.

---

## 3. Core Principles

1. **ACID**: Atomicity (all or nothing), Consistency (constraints preserved), Isolation (concurrent transactions do not interfere), Durability (committed data survives failures).
2. **Proxy-based interception**: @Transactional is processed by Spring AOP. The bean you inject is a proxy, not the real class. The real class is the proxy's delegate.
3. **Declarative over programmatic**: prefer @Transactional for standard cases; use TransactionTemplate for programmatic control only when needed.
4. **Explicit rollback rules**: do not rely on defaults for checked exceptions — declare rollbackFor explicitly in business-critical code.
5. **Shortest possible transaction**: hold transactions open for the minimum time necessary to reduce lock contention and connection pool pressure.
6. **Read-only optimizations**: readOnly=true is a hint to the provider and the database — Hibernate skips dirty checking; databases may route to replicas.

---

## 4. Types / Architectures / Strategies

### Propagation Levels

| Propagation | Behavior | Use Case |
|---|---|---|
| REQUIRED (default) | Join existing tx; create new if none | Standard service methods |
| REQUIRES_NEW | Suspend existing tx; always create new | Audit logging (must persist even if outer tx rolls back) |
| NESTED | Create savepoint within existing tx; rollback to savepoint on failure | Sub-operations that can fail independently |
| SUPPORTS | Join existing tx if present; run non-transactionally if none | Read-only queries that work with or without tx |
| NOT_SUPPORTED | Suspend existing tx; run non-transactionally | Operations that must not run in a tx (e.g., expensive reports) |
| MANDATORY | Must join existing tx; throw if none | DAO methods that require caller to manage tx |
| NEVER | Must NOT run in a tx; throw if one exists | Non-transactional operations that must not be wrapped |

### Isolation Levels and Anomalies

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | DB Default |
|---|---|---|---|---|
| READ_UNCOMMITTED | Possible | Possible | Possible | No major DB |
| READ_COMMITTED | Prevented | Possible | Possible | PostgreSQL, Oracle, SQL Server |
| REPEATABLE_READ | Prevented | Prevented | Possible | MySQL (InnoDB) |
| SERIALIZABLE | Prevented | Prevented | Prevented | Strict compliance |

- **Dirty read**: reading uncommitted data from another transaction (sees data that may roll back)
- **Non-repeatable read**: reading the same row twice in a transaction yields different values (another tx committed an update between reads)
- **Phantom read**: a query returns different sets of rows on two executions within the same transaction (another tx inserted/deleted rows matching the WHERE clause)

### Transaction Management Strategies

| Strategy | API | When to Use |
|---|---|---|
| Declarative (@Transactional) | Annotation | 95% of cases — clean, readable, AOP-managed |
| TransactionTemplate | Programmatic | Need fine-grained control, partial commits, custom rollback logic |
| PlatformTransactionManager direct | Low-level | Infrastructure code, batch frameworks, reactive flows |
| JTA / XA | Distributed | Two-phase commit across multiple DBs or DB + JMS |
| Saga (choreography/orchestration) | Distributed | Microservices where XA is impractical; eventual consistency |

---

## 5. Architecture Diagrams

### @Transactional Proxy Mechanism

```
Spring ApplicationContext
         |
   [UserService bean]
         |
   Actually a JDK/CGLIB proxy:
   ┌──────────────────────────────────────────┐
   │  TransactionInterceptor (AOP advice)      │
   │    |                                      │
   │    ▼                                      │
   │  PlatformTransactionManager               │
   │    .getTransaction(definition)            │
   │    |                                      │
   │    ▼                                      │
   │  [real UserService.createUser()]  ◄────── │ ← only reached through proxy
   │    |                                      │
   │    ▼                                      │
   │  PlatformTransactionManager               │
   │    .commit() or .rollback()               │
   └──────────────────────────────────────────┘
```

### Self-Invocation Problem

```
// Correct call path (through proxy):
Controller → [UserService PROXY] → TransactionInterceptor → real UserService.methodA()

// Self-invocation (bypasses proxy entirely):
real UserService.methodA()
    calls this.methodB()  ← 'this' is the real object, NOT the proxy
                          → TransactionInterceptor NEVER invoked
                          → @Transactional on methodB() is IGNORED
```

### Propagation: REQUIRED vs REQUIRES_NEW vs NESTED

```
REQUIRED:
  Outer Tx ──────────────────────────────── commit/rollback
      └─ Inner (REQUIRED) ─── joins outer ─┘
         (same tx; inner rollback = outer rollback)

REQUIRES_NEW:
  Outer Tx ──── suspended ──────────────── resumed ── commit/rollback
                    └─ Inner (REQUIRES_NEW) ── commit ─┘
                       (own tx; inner commit even if outer rolls back)

NESTED:
  Outer Tx ──────────────────────────────────────────── commit/rollback
      └─ Inner (NESTED) ── savepoint ── rollback to savepoint
         (inner rollback only to savepoint; outer can still commit)
```

### Transaction Lifecycle with JpaTransactionManager

```
@Transactional method called via proxy
         │
JpaTransactionManager.getTransaction()
         │
         ├─ No existing tx → EntityManagerFactory.createEntityManager()
         │                  → bind EntityManager to thread-local
         │                  → begin JDBC transaction (conn.setAutoCommit(false))
         │
         ├─ Existing tx (REQUIRED) → reuse bound EntityManager
         │
[method executes — reads/writes go through bound EntityManager]
         │
No exception → EntityManager.flush()  (dirty checking, SQL sent to DB)
             → connection.commit()
             → EntityManager.close() (unbind from thread-local)
             │
Exception → connection.rollback()
          → EntityManager.close()
```

### Distributed Transaction: Two-Phase Commit (XA)

```
Application
    │
JtaTransactionManager (coordinator)
    │
    ├── Phase 1: PREPARE
    │      ├── DataSource A (DB): "ready to commit?" → YES
    │      └── DataSource B (JMS): "ready to commit?" → YES
    │
    └── Phase 2: COMMIT
           ├── DataSource A: COMMIT
           └── DataSource B: COMMIT

If any resource says NO in Phase 1 → ROLLBACK sent to all
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 @Transactional Attributes

```java
@Transactional(
    propagation  = Propagation.REQUIRED,        // default
    isolation    = Isolation.DEFAULT,           // DB default (READ_COMMITTED on PostgreSQL)
    readOnly     = false,                       // default
    rollbackFor  = {RuntimeException.class},    // default: RuntimeException + Error
    noRollbackFor = {},                         // override rollback for specific exceptions
    timeout      = -1                           // seconds; -1 = no timeout
)
public void processPayment(PaymentRequest request) { ... }
```

### 6.2 Rollback Rules

```java
// DEFAULT — only RuntimeException and Error trigger rollback
// Checked exceptions do NOT roll back by default

// BROKEN — checked exception silently commits the transaction
@Transactional
public void transferFunds(Long fromId, Long toId, BigDecimal amount) throws InsufficientFundsException {
    debit(fromId, amount);   // DB row updated
    credit(toId, amount);    // DB row updated
    auditService.log(fromId, toId, amount); // throws checked InsufficientFundsException
    // Transaction COMMITS even though exception was thrown — money debited but not credited audit
}

// FIX — explicitly include checked exception in rollbackFor
@Transactional(rollbackFor = {InsufficientFundsException.class, Exception.class})
public void transferFunds(Long fromId, Long toId, BigDecimal amount) throws InsufficientFundsException {
    // now any exception (checked or unchecked) rolls back
}

// Alternative: wrap checked exception in RuntimeException
@Transactional
public void transferFunds(Long fromId, Long toId, BigDecimal amount) {
    try {
        debit(fromId, amount);
        credit(toId, amount);
    } catch (InsufficientFundsException e) {
        throw new TransactionSystemException("Insufficient funds", e); // RuntimeException subclass
    }
}
```

### 6.3 REQUIRES_NEW for Independent Transactions

```java
@Service
public class AuditService {

    // REQUIRES_NEW: this transaction is independent of the caller's transaction
    // Even if the caller rolls back, the audit log entry is committed
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String event, String userId) {
        AuditLog log = new AuditLog(event, userId, Instant.now());
        auditLogRepository.save(log);
    }
}

@Service
public class PaymentService {

    @Autowired
    private AuditService auditService;

    @Transactional
    public void processPayment(PaymentRequest req) {
        paymentRepository.save(buildPayment(req));
        auditService.logEvent("PAYMENT_INITIATED", req.getUserId()); // runs in its own tx
        // Even if the outer tx rolls back later, the audit log entry is already committed
        if (fraudDetected(req)) {
            throw new FraudException("Payment rejected"); // rolls back payment, not audit log
        }
    }
}
```

### 6.4 NESTED Propagation with Savepoints

```java
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order); // step 1

    try {
        notificationService.sendEmail(order); // step 2 — NESTED
    } catch (EmailException e) {
        // rollback to savepoint before sendEmail; order save is intact
        log.warn("Email failed, continuing without notification", e);
    }

    inventoryService.reserve(order); // step 3
}

@Transactional(propagation = Propagation.NESTED)
public void sendEmail(Order order) throws EmailException {
    // if this throws, only this NESTED block rolls back to savepoint
    emailRepository.save(new EmailRecord(order));
    smtpClient.send(order.getCustomerEmail()); // may throw
}
```

### 6.5 readOnly=true Optimization

```java
// readOnly=true instructs:
// 1. Hibernate: skip dirty checking on flush (significant CPU saving for large entity graphs)
// 2. JpaTransactionManager: setFlushMode(NEVER/MANUAL) — Hibernate won't flush
// 3. Some JDBC drivers and connection pools: mark connection read-only
// 4. PostgreSQL/HAProxy/PgBouncer: routing hint to send to read replica

@Transactional(readOnly = true)
public Page<UserDTO> findActiveUsers(Pageable pageable) {
    return userRepository.findByStatus(ACTIVE, pageable)
                         .map(UserMapper::toDTO);
}

// Hibernate with readOnly skips:
// - Snapshot comparison (dirty checking) on flush
// - Entity state tracking for managed entities
// Benchmark result on 1000-entity transaction: ~35% faster flush phase
```

### 6.6 TransactionSynchronizationManager — afterCommit

```java
// Common use case: send an event/email ONLY after the transaction commits
// Sending inside the transaction risks sending the event before the DB is durable

@Transactional
public User registerUser(RegistrationRequest req) {
    User user = userRepository.save(new User(req));

    // Register a callback to run AFTER this transaction commits
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                // DB row is durable here; safe to publish event
                eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
            }
        }
    );

    return user;
}
```

### 6.7 TransactionTemplate (Programmatic)

```java
@Service
public class BatchProcessor {

    private final TransactionTemplate txTemplate;

    public BatchProcessor(PlatformTransactionManager txManager) {
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.txTemplate.setTimeout(30); // 30 seconds
    }

    public void processBatch(List<Item> items) {
        for (List<Item> chunk : partition(items, 100)) {
            txTemplate.execute(status -> {
                try {
                    itemRepository.saveAll(chunk);
                } catch (Exception e) {
                    status.setRollbackOnly(); // programmatic rollback signal
                    log.error("Chunk failed", e);
                }
                return null;
            });
            // Each chunk commits independently; failure in one chunk does not affect others
        }
    }
}
```

### 6.8 @Async + @Transactional Interaction

```java
// @Async creates a new thread; the original transaction context is NOT propagated
// Each @Async invocation runs in a fresh thread with no active transaction

@Async
@Transactional
public CompletableFuture<Void> processAsync(Long orderId) {
    // This runs in a thread pool thread — no transaction from the caller
    // Spring creates a NEW transaction here (Propagation.REQUIRED → new tx since none exists)
    Order order = orderRepository.findById(orderId).orElseThrow();
    order.process();
    return CompletableFuture.completedFuture(null);
    // Transaction commits when this method returns, independently of the caller
}
```

### 6.9 JTA / XA Distributed Transactions

```java
// XA spans multiple resources — DB + JMS broker atomically
// Spring Boot supports JTA via Atomikos or Bitronix (third-party starters)

@Configuration
public class JtaConfig {
    // Spring Boot auto-configures JtaTransactionManager when JTA provider is on classpath
    // application.properties:
    // spring.jta.enabled=true
    // spring.jta.atomikos.datasource.xa-data-source-class-name=org.postgresql.xa.PGXADataSource
}

@Transactional  // uses JtaTransactionManager — spans DB + JMS atomically
public void placeOrder(Order order) {
    orderRepository.save(order);       // PostgreSQL XA resource
    jmsTemplate.convertAndSend("orders", order);  // ActiveMQ XA resource
    // Both commit atomically; if JMS fails, DB also rolls back
}
```

---

## 7. Real-World Examples

**Banking transfer**: REQUIRED propagation on the outer transfer method; debit and credit sub-operations join the same transaction. If credit fails, the debit rolls back atomically. Optimistic locking with @Version prevents double-debit under concurrent requests.

**Audit logging that must survive rollback**: AuditService.log() uses REQUIRES_NEW. When a business transaction rolls back due to fraud detection, the audit record of the rejected transaction still persists. This is a regulatory requirement in financial systems.

**Order processing with optional notification**: NESTED propagation in email notification step. Email SMTP failure should not prevent order creation. NESTED saves a savepoint, the email service rolls back to the savepoint on failure, and the outer order creation transaction still commits.

**E-commerce inventory reservation**: READ_COMMITTED isolation prevents phantom reads in most cases. For flash-sale inventory decrement, pessimistic locking (SELECT FOR UPDATE) ensures only one transaction decrements the last unit.

**Event-driven architecture with outbox pattern**: afterCommit callback publishes events to Kafka only after the DB transaction commits. Prevents the dual-write problem where the event fires but the DB rolls back (or vice versa).

**Multi-datasource batch job**: TransactionTemplate with REQUIRES_NEW creates one transaction per chunk of 100 records. A failing chunk rolls back its 100 records only; previous chunks remain committed. The job resumes from the failed chunk on retry.

---

## 8. Tradeoffs

### Propagation Trade-offs

| Propagation | Pro | Con |
|---|---|---|
| REQUIRED | Simple; minimal connection overhead | Inner failure rolls back outer |
| REQUIRES_NEW | Complete isolation; audit always persists | Two connections held simultaneously; deadlock risk if inner accesses same rows |
| NESTED | Partial rollback capability; one connection | Only supported by JDBC (not JTA); less known behavior |
| SUPPORTS | Works in and out of tx context | Inconsistent behavior (may or may not be transactional) |

### Isolation Trade-offs

| Isolation | Anomaly Protection | Concurrency Impact |
|---|---|---|
| READ_UNCOMMITTED | None | Highest throughput; dangerous |
| READ_COMMITTED | Dirty reads | Minimal locks; most databases' default |
| REPEATABLE_READ | Dirty + non-repeatable | Row-level shared locks held until commit |
| SERIALIZABLE | All anomalies | Lowest throughput; deadlock-prone; range locks |

### Declarative vs Programmatic

| | @Transactional | TransactionTemplate |
|---|---|---|
| Boilerplate | None | Moderate |
| Granularity | Method-level | Arbitrary code block |
| Testability | Harder to control boundary | Explicit control |
| Pitfalls | Self-invocation, private methods | Forgetting setRollbackOnly |

### XA vs Saga

| | XA / JTA | Saga (Choreography/Orchestration) |
|---|---|---|
| Consistency | Strong (ACID) | Eventual |
| Complexity | High (XA driver support, coordinator) | High (compensating transactions) |
| Performance | 2-phase commit overhead (~2-5x vs local) | Asynchronous; high throughput |
| Failure handling | Coordinator handles atomicity | Application must implement compensations |
| Use case | Monolith or tightly coupled services | Microservices, high scale |

---

## 9. When to Use / When NOT to Use

**Use @Transactional(readOnly=true) when:**
- Service method only reads data
- Hibernate dirty checking overhead is measurable (large entity graphs)
- Database routing to read replicas is configured

**Use REQUIRES_NEW when:**
- The inner operation must commit regardless of the outer transaction outcome
- Audit logging, event recording, metrics writes
- Never use REQUIRES_NEW when the inner operation reads the same rows as the outer — deadlock risk

**Use NESTED when:**
- You want partial rollback within a larger operation
- The sub-operation is optional (email, push notification) and should not fail the main operation
- NESTED is JDBC-only (DataSourceTransactionManager) — not available with JTA

**Use Saga over XA when:**
- Services are deployed independently (microservices)
- XA drivers are unavailable for your data stores (most NoSQL, most cloud-native DBs)
- Throughput requirements exceed what 2-phase commit can support

**Do NOT use @Transactional when:**
- The method is private (proxy cannot intercept)
- The method is called from within the same class (self-invocation bypasses proxy)
- The method is on a class not managed by Spring (no proxy created)
- You need sub-millisecond latency and the lock acquisition overhead matters

---

## 10. Common Pitfalls

### Pitfall 1: Self-Invocation Bypasses Proxy (Most Common)

```java
// BROKEN — internal call to @Transactional method bypasses proxy
@Service
public class UserService {

    @Transactional
    public void createUser(UserRequest req) {
        User user = userRepository.save(new User(req));
        sendWelcomeEmail(user);  // direct call — 'this' is the real object, NOT the proxy
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWelcomeEmail(User user) {
        // REQUIRES_NEW has NO effect — no proxy involved
        // runs in the SAME transaction as createUser()
        emailRepository.save(new EmailRecord(user));
    }
}

// FIX A — inject the service into itself (not recommended; circular dependency)
@Service
public class UserService {
    @Autowired
    @Lazy
    private UserService self;  // inject proxy reference

    @Transactional
    public void createUser(UserRequest req) {
        User user = userRepository.save(new User(req));
        self.sendWelcomeEmail(user);  // goes through proxy — REQUIRES_NEW works
    }
}

// FIX B — extract sendWelcomeEmail into a separate Spring bean
@Service
public class EmailService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendWelcomeEmail(User user) { ... }  // works — called via proxy
}

// FIX C — use ApplicationContext.getBean() (least preferred)
```

### Pitfall 2: @Transactional on Private Methods (Silently Ignored)

```java
// BROKEN — Spring AOP (CGLIB and JDK proxy) cannot override private methods
@Service
public class OrderService {

    @Transactional  // SILENTLY IGNORED — Spring cannot proxy private methods
    private void createOrderInternal(Order order) {
        orderRepository.save(order);
        inventoryRepository.reserve(order);
    }

    public void createOrder(OrderRequest req) {
        createOrderInternal(buildOrder(req));  // no transaction
    }
}

// FIX — make the method public (or package-private with CGLIB)
@Service
public class OrderService {

    @Transactional
    public void createOrderInternal(Order order) {  // public — CGLIB can proxy
        orderRepository.save(order);
        inventoryRepository.reserve(order);
    }
}
```

### Pitfall 3: Swallowing Exception Prevents Rollback

```java
// BROKEN — exception is caught and swallowed; Spring sees clean return → commits
@Transactional
public void processPayment(PaymentRequest req) {
    try {
        paymentRepository.save(buildPayment(req));
        externalPaymentGateway.charge(req);  // throws RuntimeException on failure
    } catch (Exception e) {
        log.error("Payment failed", e);  // exception swallowed — tx commits!
        // Payment record saved to DB even though external charge failed
    }
}

// FIX A — rethrow the exception
@Transactional
public void processPayment(PaymentRequest req) {
    try {
        paymentRepository.save(buildPayment(req));
        externalPaymentGateway.charge(req);
    } catch (Exception e) {
        log.error("Payment failed", e);
        throw e;  // rethrow — tx rolls back
    }
}

// FIX B — programmatic rollback signal (when you need to handle the exception)
@Transactional
public void processPayment(PaymentRequest req) {
    try {
        paymentRepository.save(buildPayment(req));
        externalPaymentGateway.charge(req);
    } catch (Exception e) {
        log.error("Payment failed", e);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        // returns normally but transaction is marked for rollback only
    }
}
```

### Pitfall 4: @Transactional on @Async Methods — Misleading Behavior

```java
// BROKEN (misleading) — developer expects caller's transaction to extend into async method
@Service
public class ReportService {

    @Transactional
    public void generateReport(Long reportId) {
        Report report = reportRepository.findById(reportId).orElseThrow();
        report.setStatus(IN_PROGRESS);
        // reportRepository.save(report) — dirty check will flush this

        processAsync(reportId);  // launches in new thread — caller's tx is NOT inherited
    }

    @Async
    @Transactional  // new transaction on the async thread — does NOT see IN_PROGRESS status
    public void processAsync(Long reportId) {
        // This runs after generateReport's transaction may or may not have committed
        // Race condition: processAsync may run before generateReport commits → sees OLD_STATUS
        Report report = reportRepository.findById(reportId).orElseThrow();
        // report.getStatus() might be the OLD value if tx hasn't committed yet
    }
}

// FIX — commit the outer transaction before launching async work
// OR ensure async method does not depend on data written in the caller's uncommitted transaction
@Transactional
public void generateReport(Long reportId) {
    Report report = reportRepository.findById(reportId).orElseThrow();
    report.setStatus(IN_PROGRESS);
    reportRepository.saveAndFlush(report); // flush to DB before tx commits

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                processAsync(reportId); // launched only after outer tx commits
            }
        }
    );
}
```

### Pitfall 5: Checked Exception Does Not Roll Back

```java
// BROKEN — OrderCreationException is checked; does NOT roll back by default
@Transactional
public void createOrder(OrderRequest req) throws OrderCreationException {
    Order order = orderRepository.save(buildOrder(req));
    inventoryService.reserve(order);  // throws checked OrderCreationException
    // Transaction COMMITS — order saved, inventory not reserved — inconsistent state
}

// FIX — add rollbackFor
@Transactional(rollbackFor = OrderCreationException.class)
public void createOrder(OrderRequest req) throws OrderCreationException {
    Order order = orderRepository.save(buildOrder(req));
    inventoryService.reserve(order);
    // Now throws → transaction rolls back correctly
}
```

### Pitfall 6: Long Transactions Holding DB Connections

```java
// BROKEN — long-running HTTP call inside a transaction holds a DB connection for its duration
// With HikariCP default pool size 10 and 10 concurrent slow requests → pool exhaustion
@Transactional
public OrderResult placeOrder(OrderRequest req) {
    Order order = orderRepository.save(buildOrder(req));
    PaymentResult payment = externalPaymentService.charge(req);  // 2-3 second HTTP call
    // DB connection held during the entire HTTP call — connection pool starvation
    order.setPaymentId(payment.getId());
    return buildResult(order, payment);
}

// FIX — do external calls outside the transaction boundary
@Service
public class OrderService {

    public OrderResult placeOrder(OrderRequest req) {
        // External call BEFORE transaction starts
        PaymentResult payment = externalPaymentService.charge(req);  // 2-3 seconds, no DB conn held

        return saveOrderTransactionally(req, payment);  // short transaction
    }

    @Transactional
    public OrderResult saveOrderTransactionally(OrderRequest req, PaymentResult payment) {
        Order order = orderRepository.save(buildOrder(req));
        order.setPaymentId(payment.getId());
        return buildResult(order, payment);
    }
}
```

---

## 11. Technologies & Tools

| Tool | Role |
|---|---|
| Spring TX 6.x | Core transaction abstraction: PlatformTransactionManager, TransactionTemplate, @Transactional |
| Spring AOP | Proxy creation for @Transactional interception |
| HikariCP | Default connection pool; default max-pool-size=10 (tune for workload) |
| Atomikos / Bitronix | JTA (XA) transaction coordinator for multi-resource transactions |
| Narayana | Red Hat JTA implementation; used in Quarkus and WildFly |
| P6Spy / datasource-proxy | Logs SQL with parameters and transaction boundaries |
| Spring Retry | @Retryable for handling ObjectOptimisticLockingFailureException |
| Testcontainers | Integration tests with real DB; tests actual transaction isolation behavior |
| Micrometer | Metrics on transaction commit/rollback rates |
| Zipkin / Jaeger | Distributed tracing that spans transaction boundaries |

### Key Spring Boot Properties

```properties
# JPA transaction manager is auto-configured when JPA is on classpath
# DataSourceTransactionManager is used for JDBC-only setups

# Connection pool sizing (tune based on DB max_connections and thread count)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# JTA (Atomikos) — enable only when multi-resource XA is required
spring.jta.enabled=true
spring.jta.atomikos.datasource.xa-data-source-class-name=org.postgresql.xa.PGXADataSource

# Transaction logging (debug only)
logging.level.org.springframework.transaction=DEBUG
logging.level.org.springframework.orm.jpa=DEBUG
```

---

## 12. Interview Questions with Answers

**Q1: What does @Transactional actually do at runtime?**
Spring creates a CGLIB (or JDK) proxy around the bean class at ApplicationContext startup. When a method annotated with @Transactional is invoked through the proxy, the TransactionInterceptor AOP advice runs before and after the method. It calls PlatformTransactionManager.getTransaction() to begin or join a transaction, invokes the real method, then calls commit() or rollback() based on whether an exception was thrown and whether it matches the rollbackFor rules. The proxy approach means any call not going through the proxy — such as a direct internal call using this — will not be intercepted.

**Q2: Explain the self-invocation problem and all the ways to fix it.**
Self-invocation occurs when a method in a Spring bean calls another method in the same object using this. The call goes directly to the real object, bypassing the AOP proxy entirely, so any @Transactional annotation on the called method has no effect. Fix options: (A) Extract the called method into a separate Spring bean — cleaner, preferred. (B) Inject the bean into itself using @Autowired @Lazy on the same field, then call via the injected reference (the proxy). (C) Use ApplicationContext.getBean() to retrieve the proxy reference at runtime. (D) Use AspectJ compile-time or load-time weaving instead of Spring AOP proxies — weaving modifies the bytecode directly, so internal calls are intercepted. For most production codebases, option A (extraction) is the right answer.

**Q3: What is the difference between REQUIRES_NEW and NESTED propagation?**
REQUIRES_NEW suspends the current transaction entirely and starts a completely independent new transaction with its own connection. The inner transaction commits or rolls back independently of the outer. The outer transaction resumes after the inner completes. NESTED creates a savepoint within the current transaction rather than starting a new one. If the nested block rolls back, it rolls back only to the savepoint — the outer transaction remains intact. NESTED uses the same connection and shares the outer transaction, so the inner rollback does not prevent an eventual outer commit. A critical limitation: NESTED requires JDBC savepoint support (works with DataSourceTransactionManager) but is NOT supported by JTA. REQUIRES_NEW works with JTA.

**Q4: Why does readOnly=true improve performance and what exactly does it affect?**
readOnly=true affects three layers. First, Hibernate: it sets FlushMode.NEVER on the session, skipping dirty checking entirely on flush — this avoids the per-entity snapshot comparison that normally runs before every commit. For a transaction loading 500 entities, dirty checking can be a significant CPU cost. Second, the JDBC driver: many drivers propagate Connection.setReadOnly(true) to the DB, which may disable certain lock acquisitions. Third, at the infrastructure level: PostgreSQL and some connection pool proxies (like PgBouncer in replica mode or AWS RDS Proxy) use the read-only hint to route the connection to a read replica. The hint is not a guarantee — a developer calling save() inside a readOnly transaction will likely get an exception from Hibernate, which is the intended behavior.

**Q5: By default, which exceptions trigger a rollback in @Transactional?**
By default, Spring rolls back for RuntimeException (and its subclasses) and java.lang.Error. Checked exceptions (those that extend Exception but not RuntimeException) do NOT trigger rollback by default. This default was inherited from EJB semantics and reflects the philosophy that checked exceptions represent expected business conditions, not unexpected failures. In practice, this default is dangerous for business operations. The safer pattern is to declare rollbackFor = Exception.class on any service method that performs multiple DB writes, ensuring that ANY exception — including checked ones from third-party libraries — triggers a rollback.

**Q6: How does Spring determine which PlatformTransactionManager to use when multiple are configured?**
If only one PlatformTransactionManager bean exists in the context, Spring uses it automatically. If multiple exist (e.g., DataSourceTransactionManager for one DB and JpaTransactionManager for another), you must either: (A) declare one as @Primary, which Spring picks by default; or (B) reference a specific manager by name in the annotation: @Transactional("secondaryTransactionManager"). TransactionManagementConfigurer interface can also programmatically designate the default. In tests, @Transactional picks up the single transaction manager in the test context unless overridden.

**Q7: What happens if a @Transactional method throws an exception that is listed in noRollbackFor?**
The exception propagates normally to the caller, but Spring does NOT roll back the transaction — it commits instead. This is the reverse of rollbackFor. Use noRollbackFor for specific RuntimeException subclasses that represent non-fatal conditions: for example, an inventory warning that is a RuntimeException but should not roll back the entire order creation. Example: @Transactional(noRollbackFor = InventoryWarningException.class). This is used rarely; most engineers should default to committing on success and rolling back on any exception.

**Q8: What is the difference between TransactionTemplate and using @Transactional?**
@Transactional is declarative — Spring wraps the entire method in a transaction via AOP proxy. It is the right choice for service layer methods where the entire method should be transactional. TransactionTemplate is programmatic — you define a code block and pass it as a lambda/callback. The advantage is fine-grained control: you can have multiple transaction boundaries within a single method, set rollback-only programmatically via TransactionStatus, vary timeouts or propagation per block, and use it in contexts where AOP proxies are unavailable (e.g., abstract base classes with overriding edge cases). TransactionTemplate is also useful in batch processing where you want to commit per chunk within a loop.

**Q9: How does Spring transaction management integrate with JPA's EntityManager?**
JpaTransactionManager bridges Spring transactions and JPA. When a @Transactional method is entered, JpaTransactionManager calls EntityManagerFactory.createEntityManager(), binds it to the current thread via ThreadLocal in EntityManagerFactoryUtils, and begins a JDBC transaction on the underlying connection. Repository code that calls EntityManagerHolder.getEntityManager() receives this bound EntityManager. On commit, JpaTransactionManager calls EntityManager.flush() (runs dirty checking, sends SQL), then the JDBC commit, then closes the EntityManager. This means the EntityManager lifecycle is tied to the transaction — entity managed state is valid only within the transaction boundary.

**Q10: What is TransactionSynchronizationManager and when would you use afterCommit?**
TransactionSynchronizationManager is a Spring infrastructure class that maintains thread-local state about the current transaction — bound resources (EntityManagers, connections), transaction name, read-only status, and registered synchronizations. Synchronizations are callbacks that fire at transaction lifecycle points: beforeCommit, afterCommit, beforeCompletion, afterCompletion. The most useful is afterCommit, which fires only after the DB transaction successfully commits. Use it to publish events, send messages to Kafka/RabbitMQ, or trigger external API calls only after you are certain the DB write is durable. This prevents the dual-write problem where you send an event but the DB later rolls back, leaving downstream consumers with data that does not exist in the source of truth.

**Q11: How do you handle the case where a @Transactional service method calls another @Transactional method in a different service bean?**
When the second service bean is a separate Spring-managed bean, the call goes through the proxy. The propagation behavior is determined by the @Transactional annotation on the called method. With default REQUIRED, the called method joins the existing transaction — both methods share the same transaction, commit, and rollback. With REQUIRES_NEW, the called method gets its own transaction (current one suspended). With NESTED, a savepoint is created. The key is that cross-bean calls respect propagation correctly because both beans are proxied, unlike same-bean self-invocation. However, both methods sharing a REQUIRED transaction means any unhandled exception in the inner call rolls back the entire transaction including the outer method's work.

**Q12: Describe the Outbox Pattern and how it relates to Spring transactions.**
The Outbox Pattern solves the dual-write problem: you need to write to the DB and publish a message to a broker atomically. XA transactions can do this but are operationally expensive. The Outbox Pattern writes the "message to be sent" as a row in an outbox table in the SAME DB transaction as the business data. A separate background process (Debezium CDC, a scheduled Spring task) reads unprocessed outbox rows and publishes them to Kafka/RabbitMQ, then marks them as processed. Spring implementation: in the @Transactional service method, save the business entity and save an OutboxEvent row in the same transaction. Use TransactionSynchronizationManager.afterCommit to trigger the publisher immediately after commit (for low latency), or rely on a @Scheduled poller for reliability.

**Q13: What happens to @Transactional when used with @Async?**
@Async causes the method to run in a separate thread pool thread, completely disconnecting from the caller's thread and its transaction context. Spring's transaction infrastructure uses ThreadLocal to bind transaction state to the current thread. When @Async spawns a new thread, the new thread has no transaction bound to it. If the async method also has @Transactional, Spring starts a NEW transaction on the async thread (Propagation.REQUIRED → no existing tx → create new). This new transaction is completely independent of the caller's. The implication: data written in the caller's uncommitted transaction is NOT visible to the async thread until it commits. This creates a race condition if the async method reads data the caller just wrote.

**Q14: How do isolation levels map to database locking behavior in PostgreSQL?**
PostgreSQL uses MVCC (Multi-Version Concurrency Control) rather than read locks. READ_UNCOMMITTED behaves the same as READ_COMMITTED in PostgreSQL — the engine never returns dirty reads. READ_COMMITTED (default) takes a new snapshot at the start of each statement; non-repeatable reads are possible. REPEATABLE_READ takes a snapshot at transaction start; the same query returns identical results within the transaction; phantom reads on aggregate queries are still theoretically possible but rare in MVCC. SERIALIZABLE uses Serializable Snapshot Isolation (SSI) — no locks for reads, but tracks read/write dependencies and aborts transactions with serialization conflicts. PostgreSQL's implementation means SERIALIZABLE has less throughput impact than traditional range-locking databases, but serialization failures (40001 error code) must be handled with retry logic.

**Q15: How do you test transaction boundaries and rollback behavior in Spring Boot tests?**
Use @DataJpaTest or @SpringBootTest with a real database (Testcontainers). @Transactional on the test class wraps each test in a transaction that rolls back at the end, keeping tests isolated. To test rollback behavior: remove @Transactional from the test, call the service method that should roll back, then assert the DB state using a separate non-transactional read. For testing REQUIRES_NEW (inner tx must persist even if outer rolls back), use TestTransaction.flagForRollback() programmatically. For testing afterCommit callbacks, use a TransactionSynchronizationManager test spy or verify side effects (events fired, emails sent). Testing with embedded H2 misses PostgreSQL-specific isolation behaviors — always run isolation tests against a real Postgres instance via Testcontainers.

**Q16: Explain a scenario where a deadlock can occur in Spring transactions and how to prevent it.**
Classic deadlock scenario: Thread A holds a lock on Row 1 and waits for Row 2. Thread B holds a lock on Row 2 and waits for Row 1. Both wait forever. In Spring: if two concurrent REQUIRES_NEW transactions update the same rows in different order, a deadlock is possible. For example, TransferService.transfer(A→B) locks account A then B; simultaneously, TransferService.transfer(B→A) locks account B then A — deadlock. Prevention strategies: (A) consistent lock ordering — always acquire locks in the same order (e.g., by ascending account ID). (B) timeout: spring.jpa.properties.javax.persistence.lock.timeout=5000 (ms) — Hibernate throws LockTimeoutException instead of waiting indefinitely. (C) reduce lock scope: use optimistic locking with @Version to avoid DB locks entirely at low contention. (D) narrow the transaction: hold the pessimistic lock for the shortest time possible.

**Q17: What is the difference between JDBC transaction management and JPA transaction management in Spring?**
DataSourceTransactionManager manages raw JDBC transactions: it sets connection.setAutoCommit(false), binds the connection to the thread via TransactionSynchronizationManager, and calls connection.commit() or connection.rollback(). It knows nothing about entities or persistence contexts. JpaTransactionManager extends DataSourceTransactionManager and adds JPA lifecycle management: it creates and binds an EntityManager to the thread, integrates Hibernate session flushing into the commit phase, and manages the persistence context lifecycle. If you use Spring Data JPA repositories, JpaTransactionManager is required — repositories need a bound EntityManager. If you mix JDBC templates with JPA in the same transaction, JpaTransactionManager handles both by exposing the underlying connection to JdbcTemplate via DataSourceUtils.

**Q18: How does the Saga pattern differ from XA transactions and when should each be used?**
XA (two-phase commit) provides ACID consistency across multiple resources within a single distributed transaction. The transaction coordinator (JtaTransactionManager) ensures all resources commit or all roll back. It is synchronous, strongly consistent, but has high latency (two round trips), requires XA-capable drivers (not all NoSQL/cloud DBs support XA), and the coordinator is a single point of failure. The Saga pattern decomposes a distributed business transaction into a sequence of local transactions, each publishing an event or message to trigger the next step. On failure, compensating transactions undo previous steps (e.g., release inventory reservation if payment fails). Sagas provide eventual consistency, not ACID. They have no coordinator SPoF and work with any data store. Use XA when ACID is non-negotiable and the resource set is small and XA-capable (e.g., DB + JMS in a monolith). Use Saga for microservices, high-scale systems, or when using non-XA data stores (Kafka, DynamoDB, MongoDB). Spring Modulith and Eventuate Tram provide Saga orchestration support on top of Spring.

---

## 13. Best Practices

1. **Annotate @Transactional at the service layer, not the repository layer.** Repository methods already have Spring Data's own transaction management. Service-layer transactions group multiple repository calls into one atomic operation.

2. **Always specify rollbackFor = Exception.class for business-critical multi-step operations.** Never rely on the RuntimeException-only default for financial, inventory, or user data operations.

3. **Keep transactions short.** Do all input validation, object construction, and external API calls BEFORE entering a transaction. The transaction scope should be only the DB writes.

4. **Never call external services (HTTP, gRPC, SMTP) inside a transaction.** External calls hold the DB connection open for the duration of the call, starving the connection pool under load.

5. **Use afterCommit callbacks for event publishing.** Publish Kafka/RabbitMQ messages only after the transaction commits to avoid publishing events whose corresponding DB data may not yet exist.

6. **Use REQUIRES_NEW for audit logging and idempotency records.** These must persist even when the business transaction rolls back.

7. **Avoid NESTED unless you specifically need savepoints and are certain JTA is not in use.** The semantics are subtle and the production behavior is harder to reason about.

8. **Use @Transactional(readOnly=true) consistently on all read-only service methods.** It is a free performance optimization with Hibernate's dirty check skip.

9. **Extract self-invoked @Transactional methods into separate Spring beans.** Self-invocation is the most common Spring transaction bug and is invisible at compile time.

10. **Monitor transaction metrics.** Export commit rate, rollback rate, and transaction duration via Micrometer. A rising rollback rate often signals a production bug before user complaints arrive.

11. **Set a sensible timeout.** Long-running transactions under lock contention cause cascading failures. @Transactional(timeout=30) prevents runaway transactions from blocking the system.

12. **Use TransactionTemplate in batch jobs, not @Transactional on the loop method.** A single transaction around a 10,000-row loop holds a connection for the entire duration and risks OOM from the first-level cache accumulating all loaded entities. Commit per-chunk with TransactionTemplate.

---

## 14. Case Study

### Scenario: Payment Processing Service with Transaction Boundary Bugs

**Context**: A fintech startup reports intermittent data inconsistencies: payments appear in the DB without corresponding audit records; occasionally, audit records exist for payments that were rejected. User balances show stale values after concurrent updates. The engineering team has been adding @Transactional annotations liberally but the bugs persist.

**Bug 1: Self-invocation hiding REQUIRES_NEW**

```java
// ORIGINAL BROKEN CODE
@Service
public class PaymentService {

    @Transactional
    public void processPayment(PaymentRequest req) {
        Payment payment = paymentRepository.save(new Payment(req));
        recordAudit(payment, "INITIATED");  // self-invocation — REQUIRES_NEW ignored

        if (fraudService.isFraudulent(req)) {
            throw new FraudException("Rejected");  // outer tx rolls back → audit lost
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void recordAudit(Payment payment, String status) {  // private + self-invocation
        auditRepository.save(new AuditEntry(payment, status));
    }
}
```

Fix: extract AuditService into its own bean with public @Transactional(REQUIRES_NEW) method.

**Bug 2: Checked exception swallowed, balance debit committed without credit**

```java
// ORIGINAL BROKEN CODE
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    try {
        accountService.debit(fromId, amount);
        accountService.credit(toId, amount);  // throws InsufficientFundsException (checked)
    } catch (InsufficientFundsException e) {
        log.warn("Transfer failed: {}", e.getMessage());
        // exception swallowed — transaction commits — debit persisted, credit not
    }
}

// FIX
@Transactional(rollbackFor = InsufficientFundsException.class)
public void transfer(Long fromId, Long toId, BigDecimal amount) throws InsufficientFundsException {
    accountService.debit(fromId, amount);
    accountService.credit(toId, amount);  // exception now propagates → rollback
}
```

**Bug 3: Optimistic locking failures on concurrent balance reads**

Concurrent debit operations read the same balance (1000), both deduct 200, both write 800 — net balance should be 600. Fix: add @Version to Account entity and @Retryable on the service method.

```java
@Entity
public class Account {
    @Version
    private Long version;
    private BigDecimal balance;
}

@Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3,
           backoff = @Backoff(delay = 50, multiplier = 2))
@Transactional
public void debit(Long accountId, BigDecimal amount) {
    Account account = accountRepository.findById(accountId).orElseThrow();
    if (account.getBalance().compareTo(amount) < 0) throw new InsufficientFundsException();
    account.setBalance(account.getBalance().subtract(amount));
}
```

**Bug 4: External HTTP call inside transaction depletes connection pool**

The fraud service HTTP call averages 800ms. With 10 concurrent transactions each holding a DB connection during the fraud check, the HikariCP pool (default 10) is exhausted. New requests queue and timeout.

```java
// FIX — move fraud check outside transaction
public void processPayment(PaymentRequest req) {
    // External call BEFORE transaction — no DB connection held
    boolean isFraud = fraudService.isFraudulent(req);  // 800ms HTTP call
    if (isFraud) throw new FraudException("Rejected");  // fail fast, no DB interaction

    savePaymentTransactionally(req);  // short transaction, <5ms
}

@Transactional(rollbackFor = Exception.class)
public void savePaymentTransactionally(PaymentRequest req) {
    Payment payment = paymentRepository.save(new Payment(req));
    auditService.recordAudit(payment, "COMPLETED");  // REQUIRES_NEW via separate bean
}
```

**Outcome after fixes**: Zero data inconsistencies in a 30-day post-fix monitoring window. P99 transaction duration dropped from 950ms to 12ms. Connection pool exhaustion incidents eliminated. Audit record integrity at 100% (REQUIRES_NEW correctly isolates audit writes from payment rollbacks).

---

**Expanded Case Study: Transactional Integrity in a Multi-Step Payment Settlement Pipeline**

**Scenario:** A fintech platform settles 50,000 payment transactions per hour across 4 sequential steps: (1) validate payment, (2) debit customer account, (3) credit merchant account, (4) publish settlement event to Kafka. Each step touches a different aggregate. A partial failure (debit succeeds, credit fails) causes real money imbalance. The existing code used a single `@Transactional` method wrapping all four steps — but step 4 (Kafka publish) is non-transactional and fires before the DB commits, causing duplicate events on retry.

**Scale:** 50k tx/hr = ~14 TPS sustained, peak 80 TPS during business hours. Settlement SLA: < 2s end-to-end. Postgres with `READ_COMMITTED` isolation.

```
Settlement pipeline — transactions and boundaries:

  [SettlementService.settle()]
        │
        │── TX_1 (REQUIRED) ─────────────────────────────────────────────┐
        │   validate(payment)         -- read-only check                  │
        │   accountService.debit()    -- write to accounts table          │
        │   accountService.credit()   -- write to accounts table          │
        │   outbox.record()           -- write to outbox table (same tx!) │
        └─────────────────────────────────────────────────────────────────┘
                                              │
                                              │  TX commits
                                              │
        [OutboxPoller @Scheduled]             │
              │── TX_2 (REQUIRES_NEW) ────────┘
              │   publish to Kafka
              │   mark outbox row SENT
              └──────────────────────────────────

Invariants:
  - If TX_1 rolls back: outbox row also rolls back → no Kafka event
  - If Kafka publish fails: TX_2 rolls back → outbox row stays PENDING → retried
  - Debit + credit + outbox: atomic — all succeed or all roll back
```

**Settlement service — correct transactional boundaries:**

```java
@Service
@Transactional                        // default: REQUIRED, READ_COMMITTED
public class SettlementService {

    private final AccountRepository accounts;
    private final OutboxRepository outbox;
    private final ValidationService validation;

    public SettlementResult settle(Payment payment) {
        // Step 1: validate (read-only — same tx, no separate tx overhead)
        validation.validate(payment);

        // Step 2+3: debit + credit in one atomic write
        accounts.debit(payment.customerId(), payment.amount());
        accounts.credit(payment.merchantId(), payment.amount());

        // Step 4: write to outbox table (same tx — committed atomically with above)
        outbox.record(new OutboxEvent("payment.settled", payment.id(), payment));

        return SettlementResult.success(payment.id());
    }
}
```

**Outbox poller — REQUIRES_NEW for independent retry:**

```java
@Component
public class OutboxPoller {

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final OutboxPublisher publisher;

    @Scheduled(fixedDelay = 500)       // poll every 500ms
    @Transactional(readOnly = true)
    public void poll() {
        outbox.findByStatus("PENDING").forEach(publisher::publish);
    }
}

@Component
public class OutboxPublisher {

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // independent tx per event
    public void publish(OutboxEvent event) {
        kafka.send(event.topic(), event.payload()).get(2, SECONDS);
        outbox.markSent(event.id());
        // If kafka.send() throws, REQUIRES_NEW tx rolls back → row stays PENDING → retried
    }
}
```

**BROKEN→FIX: Kafka publish inside the settlement transaction**

```java
// BROKEN: KafkaTemplate.send() fires before DB commits
// If DB commit fails after Kafka, consumers see the event but DB has no record
@Service
@Transactional
public class SettlementService {
    public void settle(Payment p) {
        accounts.debit(p.customerId(), p.amount());
        accounts.credit(p.merchantId(), p.amount());
        // DANGER: Kafka publish here fires BEFORE the enclosing @Transactional commits!
        // On DB commit failure → Kafka already has the event → duplicate/phantom event
        kafka.send("payment.settled", p);
    }
}

// FIX: Transactional Outbox pattern (shown above)
// Publish to outbox table inside tx, then poll and publish to Kafka separately
```

**BROKEN→FIX: REQUIRES_NEW audit creates phantom reads under SERIALIZABLE**

```java
// BROKEN: AuditService uses REQUIRES_NEW → suspends outer tx
// Under SERIALIZABLE isolation, the newly started tx sees committed data
// that the outer (suspended) tx hasn't committed — phantom read risk
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.SERIALIZABLE)
public void auditSettlement(Payment p) {
    // reads accounts that the suspended tx hasn't committed yet
    BigDecimal balance = accounts.getBalance(p.customerId());  // stale!
}

// FIX: audit after the outer tx commits using @TransactionalEventListener
// Fired AFTER_COMMIT — guaranteed to see committed state
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSettlementCommitted(SettlementCommittedEvent event) {
    auditRepository.save(new AuditEntry(event.paymentId(), Instant.now()));
}

// In SettlementService: publish event (handled AFTER_COMMIT, not immediately)
applicationEventPublisher.publishEvent(new SettlementCommittedEvent(payment.id()));
```

**BROKEN→FIX: Optimistic locking for concurrent settlement attempts**

```java
// BROKEN: two threads settle the same payment concurrently → double-debit
@Entity
public class Account {
    private BigDecimal balance;
    // No version field → last-write-wins → balance incorrect
}

// FIX: @Version for optimistic locking — second committer gets ObjectOptimisticLockingFailureException
@Entity
public class Account {
    @Version
    private Long version;

    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) throw new InsufficientFundsException();
        this.balance = balance.subtract(amount);
    }
}

// Caller retries on OptimisticLockException (Spring Retry or @Retryable)
@Retryable(value = ObjectOptimisticLockingFailureException.class, maxAttempts = 3)
public SettlementResult settle(Payment p) { ... }
```

**Isolation levels chosen per operation:**

| Operation | Isolation | Reason |
|---|---|---|
| Debit/credit | READ_COMMITTED (default) | Sees committed balances; performance |
| Balance check for approval | REPEATABLE_READ | Re-read must see same balance within single tx |
| Settlement audit query | READ_UNCOMMITTED | Read-only report; dirty reads acceptable |
| Idempotency check | SERIALIZABLE | Must not allow two concurrent new records for same payment_id |

**Metrics and results:**
- Pre-fix: 12 duplicate settlement events per day (Kafka publish before DB commit)
- Post-fix: 0 duplicate events in 90 days (transactional outbox pattern)
- Settlement latency p99: 180ms (vs. 450ms with distributed 2PC attempt)
- Optimistic lock conflicts: 0.3% of settlements (retried successfully, max 2 attempts)
- Outbox poll lag: < 600ms from commit to Kafka delivery (500ms poll interval + 100ms Kafka)

**Interview discussion points:**

**Why is the Transactional Outbox pattern better than publishing to Kafka inside the settlement transaction?** Kafka's `KafkaTemplate.send()` is not part of the database transaction. Firing it inside `@Transactional` means the Kafka message can be sent before the DB commits (false positive) or after a partial failure (orphaned event). The outbox pattern writes a record to a DB table inside the same ACID transaction as the business data, then a separate poller reads and publishes committed records, guaranteeing at-least-once Kafka delivery only after DB commit.

**What is the difference between @TransactionalEventListener(AFTER_COMMIT) and a regular @EventListener?** A regular `@EventListener` fires synchronously within the current transaction — if the tx rolls back, the listener has already executed (side effects are permanent). `@TransactionalEventListener(AFTER_COMMIT)` fires only if the enclosing transaction successfully commits, making it ideal for sending notifications, publishing events, or triggering async workflows that must reflect committed state.

**How do you handle a Kafka publish failure in the outbox poller?** The `publish()` method runs in `REQUIRES_NEW`. If `kafka.send().get()` throws, the transaction rolls back — the outbox row stays `PENDING`. The next poll cycle retries it. After N retries (configurable), move the row to a `DEAD_LETTER` table and alert ops. This gives you exactly-once delivery semantics on the DB side with at-least-once on Kafka (consumers must be idempotent).

**When should you use REQUIRES_NEW vs NESTED propagation?** `REQUIRES_NEW` suspends the current transaction and starts a completely independent one — fully isolated, commits and rolls back independently. Use for audit writes that must persist even if the outer tx rolls back. `NESTED` uses a savepoint within the current transaction — rolls back to the savepoint on failure but the outer tx can still commit. Use when you want partial rollback within one transaction (e.g., retry a sub-step without losing all prior work). Postgres supports `NESTED` (via savepoints); not all databases do.

**What is the risk of REQUIRES_NEW with high transaction volume?** Each `REQUIRES_NEW` opens a new connection from the pool. If 80 settlement threads each fire a `REQUIRES_NEW` audit, that's 160 total connections — doubling HikariCP pool pressure. Size the pool accounting for nested transactions, or use `@TransactionalEventListener(AFTER_COMMIT)` which runs after the outer connection is released.

---

## Related / See Also

- [Spring Data JPA](../spring_data_jpa/README.md) — @Transactional on repositories
- [Spring Proxies](../spring_proxies/README.md) — self-invocation issue
- [Case Study: Idempotent Payment API](../case_studies/design_idempotent_payment_api.md) — transactional outbox
- [Concurrency Control & Locking (Database)](../../database/concurrency_control_and_locking/README.md) — MVCC, gap locks, SELECT FOR UPDATE at the DB engine level
- [Consistency Models & Consensus (Database)](../../database/consistency_models_and_consensus/README.md) — linearizability, Raft, distributed locks, fencing tokens
