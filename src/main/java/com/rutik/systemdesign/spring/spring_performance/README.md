# Spring Performance

## 1. Concept Overview

Spring Boot performance engineering encompasses startup time, connection pool management, caching, JVM tuning, and emerging approaches like GraalVM native images and virtual threads. For senior engineers and architects, performance is not just about making things faster — it is about understanding where time is spent, what resources are being consumed, and what the trade-offs are between throughput, latency, memory, and operational complexity.

This module covers the full spectrum of Spring Boot performance concerns:

- **Connection pool tuning**: HikariCP is the default in Spring Boot. Correct sizing is the single highest-impact database performance lever.
- **Application startup time**: From class data sharing to GraalVM native compilation, reducing startup time matters for serverless, container scaling, and deployment velocity.
- **Caching**: @Cacheable with Redis eliminates redundant database queries. TTL strategy and cache invalidation determine correctness.
- **JVM configuration**: GC selection, JIT compilation, tiered compilation, and CPU cache behavior directly affect throughput and latency.
- **Virtual threads** (Spring Boot 3.2+, Java 21): Project Loom's virtual threads eliminate the thread-per-request bottleneck for I/O-bound workloads.
- **Reactive with WebFlux**: When traditional thread-per-request (Tomcat) hits its limits, WebFlux's non-blocking model provides higher concurrency with fewer threads.
- **Observability**: You cannot optimize what you cannot measure. Micrometer, HikariCP metrics, slow query logging, and APM tools are the foundation.

---

## 2. Intuition

One-line analogy: Optimizing a Spring Boot application is like tuning a restaurant — the kitchen (thread pool and connection pool) must be sized to match the dining room (request rate and concurrency), the pantry (cache) must stock frequently ordered items to avoid trips to the supplier (database), and the floor plan (startup configuration) should be simplified to reduce setup time before service opens.

Mental model: Think of a Spring Boot application as a pipeline: HTTP request arrives, consumed by a Tomcat thread, acquires a database connection, executes a query, releases the connection, returns a response. Performance problems surface when any stage in this pipeline saturates: Tomcat thread pool exhausted (all 200 threads busy waiting for DB), HikariCP pool exhausted (threads waiting for a connection), database slow (queries waiting on locks or I/O), or GC pauses freezing all threads.

Why it matters: An application that works correctly in development but degrades under production load is a production incident waiting to happen. Connection pool exhaustion, N+1 query problems, and GC pause spikes are the three most common causes of production latency degradation in Spring Boot services.

Key insight: Most Spring Boot performance problems are not JVM problems — they are architectural problems visible in metrics: high DB connection wait time means the pool is too small or queries are too slow; high GC pause time means too many short-lived objects or old-gen pressure; high P99 latency that diverges from P50 means tail latency from resource contention. Fix the architecture first, then tune the JVM.

---

## 3. Core Principles

**Measure before optimizing**: Establish a baseline with realistic load before making any change. Use JMH for microbenchmarks, Gatling or k6 for load testing, and Micrometer with Prometheus/Grafana for production metrics.

**The connection pool is the most critical resource**: In a blocking I/O model (Tomcat + JDBC), every request in flight holds a thread and a database connection simultaneously. Pool size directly determines maximum concurrency. Too small: threads queue for connections, latency spikes. Too large: database connection overhead, lock contention, and memory pressure.

**Caching trades memory for latency**: A cache hit avoids a database round-trip (~1–10ms) at the cost of holding the value in Redis or heap memory. Cache invalidation correctness (stale reads) is the primary operational risk.

**Startup time is deployment time**: In container orchestration environments (Kubernetes), slow startup means slow pod readiness, slow horizontal scaling, and long deployment windows. Reducing startup time improves deployment safety and enables faster autoscaling reactions.

**Virtual threads are not magic**: Virtual threads eliminate the platform thread as a scalability bottleneck for I/O-bound workloads. They do NOT help CPU-bound work, they do NOT eliminate synchronized blocks from being pinned, and they require libraries to be non-blocking at the syscall level to achieve full benefit.

---

## 4. Types / Architectures / Strategies

### Threading Models

| Model | Threads | Concurrency | Best For |
|-------|---------|------------|---------|
| Tomcat (default) | platform threads, default 200 | Up to 200 concurrent requests | Mixed I/O + CPU, simplicity |
| Virtual Threads (JDK 21+) | virtual threads, millions possible | Very high I/O concurrency | Highly I/O-bound workloads |
| WebFlux (Reactor) | small event loop (2 * CPU cores) | Very high I/O concurrency | Reactive streaming, event-driven |

### Connection Pool Sizing Strategies

- **OLTP workload formula**: poolSize = (CPU cores * 2) + effective_spindle_count. For a 4-core server with SSD (spindle count = 1): poolSize = (4 * 2) + 1 = 9. This is the HikariCP-recommended formula from the Postgres wiki.
- **Throughput formula**: poolSize = (requests_per_second * avg_query_duration_ms) / 1000. For 1000 RPS with 5ms average query: poolSize = (1000 * 5) / 1000 = 5 connections minimum.
- **Empirical tuning**: Start with the formula, load test, watch HikariCP's pending connection count metric. If pending > 0 regularly, increase pool size.

### Caching Strategies

- **Cache-aside (lazy loading)**: Application checks cache on read. On miss, reads from DB, writes to cache. Spring @Cacheable implements this.
- **Write-through**: On write, application updates DB and cache simultaneously. Spring @CachePut implements this.
- **Write-behind**: Application writes to cache only; cache asynchronously writes to DB. Not natively supported in Spring — requires Caffeine or custom implementation.
- **Cache warm-up**: On application startup, pre-populate cache with frequently accessed data. Implement with ApplicationListener<ApplicationReadyEvent>.

### GraalVM Native vs JVM JIT

| Dimension | JVM (JIT) | GraalVM Native |
|-----------|-----------|---------------|
| Startup time | 5–30 seconds | 10–200ms |
| Peak throughput | Very high (JIT optimizes hot paths) | Lower (AOT, no runtime profiling) |
| Memory footprint | Higher (JIT code cache, class metadata) | Much lower |
| Reflection support | Full | Requires hints declaration |
| Dynamic proxies | Full | Requires hints declaration |
| Build time | Fast | Very slow (minutes) |
| Debugging | Easy | Hard |
| Best for | Long-running services | Short-lived, serverless, CLI tools |

---

## 5. Architecture Diagrams

### HikariCP Connection Pool — Request Flow

```
  HTTP Request
       |
       v
  Tomcat Thread Pool (max: 200 threads)
  [T1][T2][T3]...[T200]
       |
       | thread acquires DB connection from pool
       v
  HikariCP Connection Pool
  +----------------------------------+
  |  maximumPoolSize: 10             |
  |  [C1][C2][C3]...[C10] (idle)     |
  |  [P1][P2]            (pending)   |  <-- threads waiting for a free connection
  +----------------------------------+
       |
       | JDBC connection
       v
  PostgreSQL Database
  (max_connections: 100)

Saturation scenario:
200 Tomcat threads * all blocked waiting on HikariCP (pool size 10)
= 190 threads in pending queue
= connection_timeout exceeded for late arrivals
= SQLTimeoutException thrown to callers
```

### @Cacheable — Cache-Aside Flow

```
  Service.getProduct(id)
       |
       | Spring AOP proxy intercepts
       v
  Cache lookup: Redis.get("products::id")
       |
       |-- HIT -----------------------------------------> return cached value (sub-ms)
       |
       |-- MISS
            |
            v
       ProductRepository.findById(id)  (5-10ms DB query)
            |
            v
       Redis.set("products::id", value, TTL=300s)
            |
            v
       return value to caller
```

### Virtual Thread vs Platform Thread — I/O Blocking

```
Platform Thread model:
Thread [P1] ----[executing]----[BLOCKED on DB I/O, 5ms]----[executing]----
Thread [P2] ----[executing]----[BLOCKED on DB I/O, 5ms]----[executing]----
...
Thread [P200] -- at capacity -- new requests queue in accept queue

Virtual Thread model:
Virtual Thread [V1] -- [executing] -- [parking on I/O] -- [executing]
                                             |
                                  Carrier thread [P1] reassigned to [V2]
Virtual Thread [V2] -- [executing] -- [parking on I/O] ...
                                  Carrier thread [P1] reassigned to [V3]
...
Virtual Thread [V50000] -- millions can exist; carrier pool = CPU cores
```

### GraalVM Native AOT Build Pipeline

```
  Source Code
       |
       v
  spring-aot-maven-plugin (AOT processing)
  - Generates reflection configuration
  - Generates proxy hints
  - Generates resource configuration
  - Computes bean definitions at build time
       |
       v
  native-image compiler (GraalVM)
  - Points-to analysis (30-90 min build)
  - Eliminates dead code
  - AOT compiles to native binary
       |
       v
  Single native executable (~50-100MB)
  - No JVM required
  - Startup: <200ms
  - Memory: 50-70% less than JVM baseline
```

### Startup Time Contributors

```
Spring Boot JVM startup timeline (typical 10s boot):
|-- JVM init (0.5s) --|
                       |-- Spring context refresh (7s) --|
                                                          |-- Tomcat start (0.5s) --|
                                                                                    |-- Ready

Spring context refresh breakdown:
  class scanning:         2.5s  <-- narrowing @ComponentScan helps here
  bean instantiation:     3.0s  <-- lazy init defers most of this
  auto-configuration:     1.5s  <-- excluding unused auto-configs helps

With all optimizations (lazy init + spring-context-indexer + CDS):
|-- JVM (0.3s) --|-- context refresh (1.5s) --|-- Tomcat (0.3s) --| = ~2s
```

---

## 6. How It Works — Detailed Mechanics

### HikariCP Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/orders
    username: app
    password: secret
    hikari:
      # For OLTP on 4-core server with SSD: (4*2)+1 = 9 -> use 10
      maximum-pool-size: 10
      # Keep 5 connections alive even when idle
      minimum-idle: 5
      # Maximum time to wait for a connection before throwing exception (default 30s is too long)
      connection-timeout: 5000      # 5 seconds — fail fast
      # Idle connections are removed after 10 minutes
      idle-timeout: 600000          # 10 minutes
      # Connections are recycled after 30 minutes (must be < DB's wait_timeout)
      # MySQL default wait_timeout is 8 hours; PostgreSQL has no idle timeout by default
      max-lifetime: 1800000         # 30 minutes
      # Query to validate connection health before handing to application
      connection-test-query: SELECT 1
      # Pool name for Micrometer metrics (shows up as hikaricp.connections.*)
      pool-name: orders-pool
      # Register Micrometer metrics automatically
      register-mbeans: true
```

```java
// HikariCP Micrometer metrics exposure (auto-configured with micrometer-core on classpath)
// Key metrics to monitor:
// hikaricp.connections.active       -- currently in use
// hikaricp.connections.idle         -- available connections
// hikaricp.connections.pending      -- threads waiting for a connection (must stay near 0)
// hikaricp.connections.acquire      -- time to acquire a connection (P99 should be <1ms)
// hikaricp.connections.creation     -- time to create a new connection
// hikaricp.connections.usage        -- time connection was held by application code

@Component
public class HikariPoolMonitor {

    private final HikariDataSource dataSource;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 60_000)
    public void logPoolStatus() {
        HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
        log.info("HikariCP [{}]: active={}, idle={}, pending={}, total={}",
            dataSource.getPoolName(),
            pool.getActiveConnections(),
            pool.getIdleConnections(),
            pool.getThreadsAwaitingConnection(),
            pool.getTotalConnections());
    }
}
```

### Lazy Initialization

```yaml
# application.yml
spring:
  main:
    lazy-initialization: true  # all beans created on first access, not at startup
```

```java
// Selective lazy initialization for non-critical beans
@Service
@Lazy  // this bean created on first use, not at startup
public class ReportGenerationService {
    // expensive to initialize; rarely needed; make it lazy
}

// Force eager initialization for beans that must fail-fast on startup
@Service
@Lazy(false) // override global lazy-initialization=true for this bean
public class DatabaseHealthCheck {
    @PostConstruct
    public void validate() {
        // must run at startup to catch misconfigurations early
    }
}
```

Risk: with `lazy-initialization=true`, misconfigurations (missing beans, bad property bindings) are not detected at startup. They surface on the first request that triggers the bean creation — potentially causing errors in production under load, rather than failing the deployment. Mitigate with ApplicationContext validation tests in CI.

### Spring Context Indexer — Compile-Time Component Scanning

```xml
<!-- pom.xml: eliminates classpath scanning at runtime -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context-indexer</artifactId>
    <optional>true</optional>
</dependency>
<!-- Generates META-INF/spring.components at build time -->
<!-- Spring loads the index file instead of scanning JAR files at startup -->
<!-- Startup time reduction: 0.5-2s depending on classpath size -->
```

### Excluding Unused Auto-Configuration

```java
// Exclude auto-configurations that are not needed
// Reduces startup time and context size

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,       // if you don't use a DB
    KafkaAutoConfiguration.class,            // if Kafka is on classpath but not used
    SecurityAutoConfiguration.class,         // if managing security manually
    ActuatorAutoConfiguration.class          // if actuator not needed in this profile
})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

```yaml
# Or via properties (preferred — no code change needed)
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
```

### @Cacheable with Redis — TTL Strategy

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default config: 10-minute TTL, no null caching
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "products", defaults.entryTtl(Duration.ofMinutes(60)),   // stable data
            "user-sessions", defaults.entryTtl(Duration.ofMinutes(30)),
            "exchange-rates", defaults.entryTtl(Duration.ofSeconds(30)) // volatile
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaults)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}

@Service
public class ProductService {

    // Cache-aside: populate cache on read miss
    @Cacheable(cacheNames = "products", key = "#productId")
    public Product getProduct(Long productId) {
        return productRepository.findById(productId).orElseThrow();
    }

    // Write-through: update cache when product changes
    @CachePut(cacheNames = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    // Eviction: remove from cache on delete
    @CacheEvict(cacheNames = "products", key = "#productId")
    public void deleteProduct(Long productId) {
        productRepository.deleteById(productId);
    }

    // Warm up cache on startup
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        productRepository.findTop100ByOrderByViewCountDesc()
            .forEach(p -> getProduct(p.getId()));
        log.info("Product cache warmed up with top 100 products");
    }
}
```

### Tomcat Thread Pool Tuning

```yaml
server:
  tomcat:
    threads:
      max: 200          # default 200; increase for high-concurrency, CPU-bound is rare
      min-spare: 10     # default 10; threads kept alive when idle
    accept-count: 100   # queue length when all threads busy; beyond this: connection refused
    max-connections: 8192  # total connections Tomcat will accept (default 8192)
    connection-timeout: 20000  # how long to wait for HTTP request to arrive after TCP connect
```

```
Thread sizing guidance:
- If workload is I/O-bound (DB, external APIs): more threads help up to DB pool limit
- If workload is CPU-bound: threads > (2 * CPU cores) causes context-switch overhead
- With virtual threads (Spring Boot 3.2+): max threads is effectively unlimited
- WebFlux: uses ~2 * CPU cores event-loop threads; far fewer than Tomcat
```

### Virtual Threads — Spring Boot 3.2+

```yaml
# application.yml (Spring Boot 3.2+, Java 21+)
spring:
  threads:
    virtual:
      enabled: true
# Effect: Tomcat uses virtual threads for request handling
# Tomcat's thread pool becomes a virtual thread pool
# Each request gets its own virtual thread (cheap: ~few KB stack vs ~1MB for platform thread)
# Blocking I/O (JDBC, RestTemplate) parks the virtual thread instead of blocking a platform thread
```

```java
// Custom @Async executor using virtual threads
@Configuration
@EnableAsync
public class VirtualThreadAsyncConfig {

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        // Java 21: Executors.newVirtualThreadPerTaskExecutor()
        // Each submitted task runs on its own virtual thread
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

// Important: synchronized blocks and methods pin the virtual thread to its carrier platform thread
// This eliminates the scalability benefit for pinned sections
// Replace synchronized with ReentrantLock where pinning is observed

// PINNED (bad for virtual threads):
public synchronized void updateSharedState() { ... }

// UNPINNED (preferred):
private final ReentrantLock lock = new ReentrantLock();
public void updateSharedState() {
    lock.lock();
    try { ... }
    finally { lock.unlock(); }
}
```

### GraalVM Native — AOT Hints

```java
// For classes that use reflection (not Spring-managed beans — those are handled automatically)
@Configuration
@ImportRuntimeHints(OrderServiceHints.class)
public class OrderServiceHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register reflection for dynamically loaded classes
        hints.reflection().registerType(OrderEvent.class,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.DECLARED_FIELDS);

        // Register dynamic proxy interfaces
        hints.proxies().registerJdkProxy(OrderRepository.class);

        // Register resource patterns
        hints.resources().registerPattern("templates/*.html");
    }
}
```

```xml
<!-- pom.xml: GraalVM native build plugin -->
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <configuration>
        <imageName>orders-service</imageName>
        <buildArgs>
            <buildArg>--initialize-at-build-time=com.rutik.systemdesign</buildArg>
        </buildArgs>
    </configuration>
</plugin>
<!-- Build: mvn -Pnative native:compile -->
<!-- Result: ./target/orders-service binary, ~80MB, starts in <200ms -->
```

### Class Data Sharing (CDS)

```bash
# Step 1: Generate class list from training run
java -XX:DumpLoadedClassList=classes.lst -jar orders-service.jar

# Step 2: Create shared archive
java -Xshare:dump -XX:SharedClassListFile=classes.lst \
     -XX:SharedArchiveFile=orders-cds.jsa -jar orders-service.jar

# Step 3: Run with shared archive (startup 30-40% faster for large applications)
java -Xshare:on -XX:SharedArchiveFile=orders-cds.jsa -jar orders-service.jar
```

```xml
<!-- Application class data sharing (AppCDS) with Spring Boot Maven plugin -->
<!-- Spring Boot 3.3+ supports CDS out of the box -->
<configuration>
    <jvmArguments>-XX:ArchiveClassesAtExit=app-cds.jsa</jvmArguments>
</configuration>
```

### N+1 Query Detection

```yaml
# application.yml
spring:
  jpa:
    show-sql: true    # logs all SQL — use only in development/staging, not production
    properties:
      hibernate:
        format_sql: true
        # Statistics: shows number of queries, cache hits, connection acquisitions
        generate_statistics: true

logging:
  level:
    org.hibernate.stat: DEBUG
    # Slow query log: queries taking longer than threshold
    org.hibernate.SQL_SLOW: INFO
```

```java
// Detecting N+1 with Hibernate statistics
@Component
public class QueryStats {

    private final Statistics stats; // inject via SessionFactory.getStatistics()

    @Scheduled(fixedDelay = 60_000)
    public void logStats() {
        log.info("Query count: {}, Entity fetches: {}, 2nd level cache hits: {}",
            stats.getQueryExecutionCount(),
            stats.getEntityFetchCount(),
            stats.getSecondLevelCacheHitCount());
        stats.clear();
    }
}

// Fix N+1 with JOIN FETCH or EntityGraph
// N+1 (broken pattern):
List<Order> orders = orderRepo.findAll(); // 1 query for orders
orders.forEach(o -> o.getItems().size()); // N queries for items

// Fixed: JOIN FETCH
@Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.status = :status")
List<Order> findWithItems(@Param("status") String status);

// Fixed: EntityGraph
@EntityGraph(attributePaths = {"items", "customer"})
List<Order> findByStatus(String status);
```

---

## 7. Real-World Examples

**Connection pool exhaustion incident**: An e-commerce service was receiving 500 errors sporadically during flash sales. Investigation showed `hikaricp.connections.pending` spiking to 80 during peak load. The pool was sized at 5 (developer default, never tuned). The fix was to increase pool size to 20 and set connection-timeout to 3s (from the default 30s). The 30s default caused requests to pile up in the queue and time out together — the 3s timeout created faster failure feedback and prevented queue buildup from compounding.

**Startup time reduction for Kubernetes autoscaling**: A microservice took 22 seconds to start, making Kubernetes readiness probe pass after 22 seconds from pod creation. During a traffic spike, newly created pods were not ready fast enough to absorb load. The team applied lazy-initialization=true, excluded 8 unused auto-configurations, added spring-context-indexer, and enabled AppCDS. Startup dropped to 4 seconds, allowing Kubernetes to scale out faster during spikes.

**N+1 query causing 4s response times**: A customer dashboard was calling getOrderHistory() which loaded 50 orders, each triggering a separate query for order items and another for customer details — 1 + 50 + 50 = 101 queries per page load. With spring.jpa.show-sql=true and slow query logging, the issue was immediately visible. A JOIN FETCH with EntityGraph reduced it to 2 queries: one for orders + items, one for customer details. Response time dropped from 4s to 80ms.

**Cache warm-up preventing cold-start latency**: A product catalog service with 50,000 products cached in Redis. After every deployment, the first wave of requests caused 80% cache miss rate as the cache was empty. Response time P99 was 2000ms for the first 10 minutes post-deployment (all DB queries). Pre-warming the top 10,000 products on ApplicationReadyEvent reduced cold-start miss rate to 15%, and P99 stayed below 200ms immediately after deployment.

---

## 8. Tradeoffs

### Tomcat (Platform Threads) vs Virtual Threads vs WebFlux

| Dimension | Tomcat + Platform Threads | Tomcat + Virtual Threads | WebFlux (Reactor) |
|-----------|--------------------------|--------------------------|-------------------|
| Programming model | Simple, imperative, blocking | Simple, imperative, blocking | Reactive, functional, non-blocking |
| Thread cost | ~1MB stack per thread | ~few KB per virtual thread | ~2 * CPU cores event-loop threads |
| Max concurrency | ~200 (default pool) | Millions (virtual thread count) | Very high (non-blocking) |
| CPU-bound | Good (up to CPU core count) | Good | No advantage over virtual threads |
| I/O-bound | Bottleneck at thread pool size | Excellent | Excellent |
| JDBC compatibility | Full | Full (parks on I/O) | Requires R2DBC for true reactive |
| Ecosystem maturity | Mature | Mature (Java 21 GA) | Mature but smaller ecosystem |
| Debugging | Easy (thread dumps, stack traces) | Easy (thread dumps work) | Harder (async stack traces) |
| Migration cost | Baseline | Low (one config change) | High (rewrite to reactive) |

### @Cacheable Strategies

| Strategy | Consistency | Complexity | Use When |
|----------|------------|-----------|---------|
| TTL-based expiry | Eventual (stale until TTL) | Low | Read-heavy, tolerate stale reads |
| @CacheEvict on write | Strong on the cached key | Medium | Write events known, single service |
| Event-driven invalidation | Strong | High | Multi-service, eventual consistency |
| No cache | Perfect consistency | None | Write-heavy, small data, fast DB |

### GraalVM Native vs JVM

| Dimension | JVM | Native Image |
|-----------|-----|-------------|
| Startup | 5-30s | <200ms |
| Throughput | Higher (JIT profile-guided optimization) | ~20-30% lower |
| Memory | 256MB-1GB typical | 50-150MB typical |
| Build time | <30s | 5-15 minutes |
| Reflection | Full | Requires hints |
| Observability | Full (JFR, JMX, async-profiler) | Limited |
| Best for | Long-running services | Serverless, CLI, short-lived functions |

---

## 9. When to Use / When NOT to Use

### Enable lazy initialization when:
- Application has a large number of beans, many of which are not used on every startup
- Startup time is critical (container scaling, serverless, CI speed)
- You have comprehensive integration tests to catch late-fail misconfigurations

### Do NOT enable lazy initialization when:
- Fast fail on misconfiguration is more important than startup speed
- The application is a long-running service that starts infrequently (the startup cost is amortized)
- Beans have side effects on initialization that must complete before accepting traffic

### Use GraalVM native when:
- The service is short-lived (serverless, AWS Lambda, CLI tools)
- Memory footprint is severely constrained
- Sub-second startup is a hard requirement

### Do NOT use GraalVM native when:
- The service has extensive use of dynamic reflection, runtime proxies, or classpath scanning
- Maximum throughput is required (JIT-compiled JVM will outperform native AOT)
- Observability via JFR / async profiler is needed
- Build times exceeding 10 minutes are unacceptable in CI

### Increase HikariCP pool size when:
- `hikaricp.connections.pending` is consistently above 0
- Response time P99 diverges significantly from P50 under load
- Connection acquisition time (hikaricp.connections.acquire) exceeds 1ms at P99

### Enable virtual threads when:
- Running Spring Boot 3.2+ on Java 21+
- Workload is I/O-bound (JDBC, HTTP calls, file I/O)
- You want to keep the simple blocking programming model without WebFlux complexity

---

## 10. Common Pitfalls

### Pitfall 1 — @Transactional readOnly=true does NOT route to read replica (broken)

```java
// BROKEN assumption: setting readOnly=true on @Transactional automatically routes
// the query to a read replica. This is FALSE.
// readOnly=true hints to the JDBC driver and Hibernate only:
//   - Hibernate disables dirty checking (small performance improvement)
//   - Some JDBC drivers set the transaction as read-only at the DB level
// It does NOT configure routing to a different DataSource.
// All queries still go to the primary database.

@Service
public class ReportService {

    @Transactional(readOnly = true) // does NOT route to replica
    public List<Order> getOrderReport() {
        return orderRepo.findAll(); // still hits primary DB
    }
}
```

```java
// FIXED: implement a routing DataSource that inspects the transaction context
// and routes read-only transactions to the replica

public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            ? "replica"
            : "primary";
    }
}

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource routingDataSource(
            @Qualifier("primaryDataSource") DataSource primary,
            @Qualifier("replicaDataSource") DataSource replica) {
        RoutingDataSource routing = new RoutingDataSource();
        routing.setTargetDataSources(Map.of("primary", primary, "replica", replica));
        routing.setDefaultTargetDataSource(primary);
        return routing;
    }
}

// Now @Transactional(readOnly = true) routes to replica via RoutingDataSource
@Transactional(readOnly = true)
public List<Order> getOrderReport() {
    return orderRepo.findAll(); // now routes to replica
}
```

### Pitfall 2 — N+1 queries degrading response time (broken)

```java
// BROKEN: lazy loading triggers N additional queries for each element in the list
// @OneToMany(fetch = FetchType.LAZY) is the default — necessary for performance
// but dangerous when iterated without JOIN FETCH

@Entity
public class Order {
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY)
    private List<OrderItem> items; // LAZY
}

@Service
public class OrderService {

    @Transactional
    public List<OrderSummary> getOrderSummaries() {
        List<Order> orders = orderRepo.findAll(); // 1 query: SELECT * FROM orders

        return orders.stream()
            .map(o -> {
                // Each call to o.getItems() triggers a query: SELECT * FROM order_items WHERE order_id = ?
                // For 100 orders: 1 + 100 = 101 queries
                int total = o.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
                return new OrderSummary(o.getId(), total);
            })
            .toList();
    }
}
```

```java
// FIXED option A: JOIN FETCH in JPQL
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT DISTINCT o FROM Order o JOIN FETCH o.items")
    List<Order> findAllWithItems();
    // Result: 1 query with JOIN — no N+1
}

// FIXED option B: EntityGraph
@EntityGraph(attributePaths = {"items"})
List<Order> findAll();
// EntityGraph adds LEFT JOIN to the generated query

// FIXED option C: DTO projection (most efficient — no entity graph needed)
@Query("""
    SELECT new com.rutik.OrderSummary(o.id, SUM(i.quantity))
    FROM Order o JOIN o.items i
    GROUP BY o.id
    """)
List<OrderSummary> findOrderSummaries();
// Aggregation in DB — no object materialization, no N+1
```

### Pitfall 3 — HikariCP pool exhaustion from long-running transactions (broken)

```java
// BROKEN: @Transactional wraps a method that calls an external HTTP service
// The DB connection is held for the ENTIRE duration of the transaction,
// including the HTTP call latency (which can be seconds)
// Under load: all 10 connections held by threads waiting on HTTP -> pool exhaustion

@Service
public class OrderService {

    @Transactional // holds DB connection for duration of entire method
    public void processOrder(Order order) {
        orderRepo.save(order);

        // HTTP call to payment service — can take 1-5 seconds
        PaymentResult result = paymentClient.charge(order.getTotal()); // slow external call

        order.setPaymentStatus(result.getStatus());
        orderRepo.save(order);
    }
}
```

```java
// FIXED: do not hold DB transaction open during external calls
// Split the method: DB work before and after the external call in separate transactions

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final PaymentClient paymentClient;
    private final OrderService self; // self-injection for @Transactional

    // Transaction 1: save order as PENDING (fast, returns connection to pool)
    @Transactional
    public Order createPendingOrder(Order order) {
        order.setPaymentStatus("PENDING");
        return orderRepo.save(order); // commits immediately after return
    }

    // No @Transactional: no connection held during HTTP call
    public void processPayment(Long orderId) {
        Order order = orderRepo.findById(orderId).orElseThrow();

        // External HTTP call — no DB connection held
        PaymentResult result = paymentClient.charge(order.getTotal());

        // Transaction 2: update order status (fast, new connection acquired)
        self.updateOrderStatus(orderId, result.getStatus());
    }

    // Transaction 2: update status (short DB transaction)
    @Transactional
    public void updateOrderStatus(Long orderId, String status) {
        Order order = orderRepo.findById(orderId).orElseThrow();
        order.setPaymentStatus(status);
        orderRepo.save(order);
    }
}
```

### Pitfall 4 — @Cacheable on a self-invoked method (broken)

```java
// BROKEN: @Cacheable is implemented via Spring AOP proxy
// Calling a cached method from within the same bean (self-invocation) bypasses the proxy
// The cache lookup never happens — every call goes to the database

@Service
public class ProductService {

    @Cacheable("products")
    public Product getProduct(Long id) {
        return productRepo.findById(id).orElseThrow();
    }

    public List<Product> getProductsForOrder(List<Long> ids) {
        return ids.stream()
            .map(id -> getProduct(id)) // calls this.getProduct() -- bypasses proxy!
            .toList(); // N database queries, no cache benefit
    }
}
```

```java
// FIXED option A: inject self-reference to go through proxy

@Service
public class ProductService {

    @Autowired
    private ProductService self;

    @Cacheable("products")
    public Product getProduct(Long id) {
        return productRepo.findById(id).orElseThrow();
    }

    public List<Product> getProductsForOrder(List<Long> ids) {
        return ids.stream()
            .map(id -> self.getProduct(id)) // goes through proxy -> cache works
            .toList();
    }
}

// FIXED option B: extract cached method to a separate CacheService bean
@Service
public class ProductCacheService {
    @Cacheable("products")
    public Product getProduct(Long id) { ... }
}
```

### Pitfall 5 — maxLifetime exceeding database connection timeout (broken)

```java
// BROKEN: HikariCP default maxLifetime is 1800000ms (30 minutes)
// If the database server (or firewall) has a connection idle timeout less than 30 minutes,
// HikariCP will hand out a stale connection that is immediately broken
// Result: SQLExceptions on the first query after the connection was silently killed

spring:
  datasource:
    hikari:
      max-lifetime: 1800000  # 30 minutes
      # MySQL default wait_timeout: 28800 seconds (8 hours) - usually OK
      # BUT: AWS RDS proxy idle timeout: 1800 seconds (30 minutes) - ties exactly to max-lifetime!
      # A connection approaching max-lifetime may arrive at the proxy AFTER it times out
```

```java
// FIXED: set max-lifetime to 25 minutes when using RDS Proxy (1800s timeout)
// Rule: max-lifetime must be SEVERAL MINUTES LESS than the DB/proxy idle timeout
// This gives HikariCP time to retire the connection before the server kills it

spring:
  datasource:
    hikari:
      max-lifetime: 1500000        # 25 minutes (RDS proxy is 30 minutes -> 5 min buffer)
      keepalive-time: 30000        # ping idle connections every 30s to detect server-side kills
      connection-test-query: SELECT 1
```

---

## 11. Technologies & Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| HikariCP | JDBC connection pool | Default in Spring Boot; fastest pool available |
| Micrometer | Application metrics | Integrates with Prometheus, Datadog, CloudWatch |
| Caffeine | In-process L1 cache | For hot data that cannot tolerate Redis latency |
| Redis (Spring Data Redis) | Distributed L2 cache | @Cacheable with TTL, session store |
| async-profiler | CPU/memory/lock profiler | Wall-clock profiling, flame graphs, zero overhead |
| JFR (Java Flight Recorder) | JVM event profiling | Built-in since JDK 11; production-safe |
| JMH (Java Microbenchmark Harness) | Microbenchmarking | Prevents JIT warmup issues in benchmarks |
| GraalVM native-image | AOT native compilation | Build-time analysis, native binary |
| spring-context-indexer | Compile-time component index | Eliminates classpath scanning at startup |
| Gatling / k6 | Load testing | Measure throughput, latency, saturation |
| Hibernate Statistics | Query count, cache hits | Detect N+1, measure 2nd-level cache effectiveness |
| Testcontainers | Real DB in tests | PostgreSQLContainer, RedisContainer, KafkaContainer |
| P6Spy / datasource-proxy | SQL query logging/timing | Statement-level SQL audit with parameters |

---

## 12. Interview Questions with Answers

**Q: What is the correct formula for sizing a HikariCP connection pool, and what happens if it is too small or too large?**
The HikariCP-recommended formula for OLTP workloads is: poolSize = (numCores * 2) + effectiveSpindleCount. For a 4-core application server using an SSD-backed database, this yields 9 connections. The throughput formula is: poolSize = (requestsPerSecond * avgQueryDurationMs) / 1000. If the pool is too small, threads queue for connections — `hikaricp.connections.pending` rises, P99 latency spikes, and eventually connection-timeout exceptions occur. If the pool is too large, the database server handles too many concurrent connections — lock contention increases, context switches multiply, and the DB's own connection overhead (each PostgreSQL connection spawns a backend process) becomes expensive. The right size is validated empirically by load testing and monitoring the pending metric: it should stay near zero under target load.

**Q: What does spring.main.lazy-initialization=true do, and what is the risk of enabling it?**
It configures all Spring beans to be created on first access rather than at ApplicationContext startup. This reduces startup time because the majority of beans are never needed during JVM initialization — they are created on the first request that exercises their code path. Typical startup time reduction is 40–70% for large applications. The risk is late-fail behavior: misconfigurations (missing required properties, unsatisfied dependencies, failed @PostConstruct methods) that would normally surface as startup errors are deferred until the first request triggers the problematic bean's creation. This means a misconfigured deployment may appear healthy (readiness probe passes) but fail on the first real request. Mitigate by running integration tests in CI that exercise all beans before production deployment, or by selectively annotating critical beans with @Lazy(false) to force eager initialization.

**Q: Explain how @Cacheable works internally and what the limitations are for self-invocation.**
@Cacheable is implemented using Spring AOP. Spring creates a proxy wrapping the bean. When an external caller invokes a @Cacheable method, the call goes through the proxy, which checks the cache. On a hit, the proxy returns the cached value without invoking the real method. On a miss, the proxy invokes the real method, stores the result in the cache, and returns it. Self-invocation bypasses the proxy: when a method in the same bean calls another @Cacheable method using this.method(), it directly invokes the target class, not the proxy. The cache is never consulted. To fix this, inject a self-reference to the proxied bean (via @Autowired private ServiceClass self), or extract the cached method to a separate Spring bean so all calls go through a proxy.

**Q: What is the difference between virtual threads and WebFlux for achieving high concurrency in Spring Boot?**
Both solve the same problem — too many platform threads blocking on I/O — but with different approaches. Virtual threads (Project Loom) are lightweight JVM threads. A platform thread blocked on I/O parks its virtual thread and frees the carrier platform thread to run other virtual threads. The programming model remains synchronous and imperative — existing JDBC, RestTemplate, and service code works without modification. WebFlux uses Reactor's non-blocking, event-driven model: a small pool of event-loop threads (typically 2 * CPU cores) handles many concurrent requests by never blocking — all I/O is callback-driven or reactive (Flux/Mono). WebFlux requires rewriting application code in a reactive functional style and requires R2DBC instead of JDBC. Virtual threads are easier to adopt (one config flag in Spring Boot 3.2); WebFlux provides more control for streaming use cases and pure reactive architectures.

**Q: Why does @Transactional(readOnly=true) not route queries to a read replica, and how do you achieve read-replica routing?**
@Transactional(readOnly=true) communicates two things: to Hibernate (disable dirty checking, skip flush on commit) and to the JDBC driver (set the connection's read-only flag). Neither of these changes which DataSource the connection comes from. Routing to a read replica requires a RoutingDataSource — a DataSource implementation that extends AbstractRoutingDataSource and overrides determineCurrentLookupKey() to return "replica" when TransactionSynchronizationManager.isCurrentTransactionReadOnly() returns true. The RoutingDataSource holds references to both the primary and replica DataSource instances. Spring Data and JDBC use the RoutingDataSource without knowing about replicas. This pattern requires that the application is disciplined about @Transactional(readOnly=true) annotations — a read-only transaction accidentally calling a method that writes will succeed (replica typically allows reads) but the write will fail, not be silently ignored.

**Q: What is the HikariCP maxLifetime property, and what can go wrong if it is set incorrectly?**
maxLifetime is the maximum amount of time a connection is allowed to exist in the pool, whether idle or in use. When a connection reaches maxLifetime, HikariCP retires it from the pool and creates a new one to replace it. This is necessary because long-lived database connections can become stale: firewalls may silently drop idle TCP connections, database servers may kill old connections, or connection state (e.g., transaction isolation level, character set) may drift. The critical rule is that maxLifetime must be several minutes less than the database or network proxy's connection idle timeout. AWS RDS Proxy has a default idle timeout of 1800 seconds (30 minutes). HikariCP's default maxLifetime is also 1800 seconds. If a connection approaches its maxLifetime exactly when the RDS Proxy times it out, HikariCP may hand out the connection to a thread after the proxy has already closed it, resulting in a broken pipe exception on the first query. Set maxLifetime to 1500s (25 minutes) with a 5-minute buffer.

**Q: How does spring-context-indexer improve startup time and what are its constraints?**
spring-context-indexer is an annotation processor that runs at compile time and generates a META-INF/spring.components file listing all @Component-annotated classes and their stereotype annotations. At runtime, Spring reads this index file instead of scanning the entire classpath JAR by JAR. This eliminates the classpath scanning phase, which for large applications with hundreds of JARs can take 1–2 seconds. The constraint is that all components must be compiled with the annotation processor on the classpath — dynamically added components (loaded via URLClassLoader at runtime, OSGi bundles, etc.) are not indexed and will not be found. Also, the index covers only the current module's compilation; components in dependencies are still found normally.

**Q: What is GraalVM AOT compilation and what Spring Boot features require special configuration (hints) to work natively?**
GraalVM's native-image compiler performs ahead-of-time compilation by running a closed-world analysis starting from the main method, tracing all reachable code paths, and compiling them to native machine code. It eliminates all unreachable code and bakes in the call graph statically. Anything that adds to the call graph dynamically at runtime — reflection, dynamic class loading, JDK proxies, serialization — must be declared explicitly via hints (JSON configuration files or RuntimeHintsRegistrar). Spring Boot's spring-aot-maven-plugin generates most hints automatically for Spring beans, @Autowired, @Value, and common patterns. Areas requiring manual hints: custom Jackson modules, third-party libraries using reflection internally, JNI calls, and runtime-generated proxy classes not recognized by Spring's AOT analysis. Test native images with mvn -Pnative test using GraalVM's native test runner.

**Q: How does HikariCP pool saturation manifest in production metrics, and what is the remediation sequence?**
Pool saturation manifests as: (1) hikaricp.connections.pending > 0 regularly under load — threads are waiting for a free connection; (2) hikaricp.connections.acquire P99 rising from sub-millisecond to tens or hundreds of milliseconds; (3) application P99 latency spiking proportionally. The remediation sequence: first, check if the pool size is below the formula recommendation — increase it if so. Second, check if the average query duration is high (slow queries holding connections longer than expected) — use slow query log or Hibernate statistics to identify expensive queries and add indexes or optimize them. Third, check if transactions are holding connections longer than necessary (N+1 patterns, external API calls within @Transactional) — refactor to minimize connection hold time. Finally, if the database itself is the bottleneck (CPU or I/O saturation), adding connections will not help — scale the database.

**Q: What are the implications of synchronized blocks and methods on virtual thread performance?**
Java's synchronized keyword acquires a monitor associated with a specific object. In JDK 21, a virtual thread that enters a synchronized block is pinned to its carrier platform thread — it cannot be unmounted even if it blocks on I/O inside the synchronized block. This means the carrier platform thread is blocked for the duration, eliminating the scalability benefit of virtual threads for that code path. Synchronized blocks that call blocking I/O (database queries, network calls) are particularly harmful. The fix is to replace synchronized with ReentrantLock, which supports virtual thread unmounting: the virtual thread can park while waiting for the lock or for I/O, freeing the carrier thread. Spring Boot 3.2 with virtual threads enabled logs warnings when pinning is detected. Libraries using synchronized internally (some legacy JDBC drivers, older connection pools) can degrade virtual thread scalability — this is one reason Loom-friendly versions of common libraries were updated for Java 21.

**Q: How does the @Cacheable TTL strategy affect consistency and what patterns reduce stale read risk?**
TTL-based cache expiry means cached values are served for up to TTL seconds after they are written — during this window, readers may see stale data if the source record changed. The risk is proportional to TTL length and update frequency. Patterns to reduce stale reads: (1) @CacheEvict on write operations — immediately removes the cache entry when data changes, forcing the next read to go to the database. This provides strong consistency for single-service scenarios. (2) Short TTL for volatile data — exchange rates at 30 seconds, product prices at 5 minutes, user profiles at 30 minutes. (3) Event-driven cache invalidation — another service publishing a change event triggers a cache eviction listener. (4) Cache-aside with version checking — include a version number in the cache key; when data changes, the version increments and old keys are naturally abandoned. Never cache data that must be strongly consistent (bank balances, inventory counts during checkout) — the complexity of maintaining cache consistency under concurrent writes exceeds the performance benefit.

**Q: What does server.tomcat.threads.max control and when should it be changed?**
server.tomcat.threads.max controls the maximum number of platform threads in Tomcat's executor thread pool. Each concurrent HTTP request in flight occupies one thread. The default is 200. Threads waiting on blocking I/O (JDBC, external REST calls) are blocked but still occupy their slot — so 200 threads means at most 200 concurrent requests regardless of actual CPU usage. Reasons to increase it: high I/O-bound concurrency requirements where you want more concurrent in-flight requests without switching to WebFlux or virtual threads. Reasons to decrease it: if the application is CPU-bound, more threads than 2 * CPU cores adds context-switch overhead with no throughput benefit. With virtual threads enabled (Spring Boot 3.2, Java 21), this property becomes largely irrelevant — virtual threads are created per request and the platform thread pool (number of CPU cores) handles I/O parking automatically.

**Q: How do you detect and diagnose a GC pause contributing to high latency P99 in a Spring Boot application?**
Enable JFR (Java Flight Recorder) with -XX:+FlightRecorder and -XX:StartFlightRecording=filename=app.jfr. JFR records GC events with pause durations at microsecond resolution and zero production overhead. In JFR output, look for GC pause events that correlate with latency spikes. Also useful: -XX:+PrintGCDetails -Xlog:gc*:file=gc.log for traditional GC logging. For G1GC (default since Java 9), a target pause time of 200ms is the default — tune with -XX:MaxGCPauseMillis=50 for lower latency at the cost of some throughput. ZGC (Java 15+ production ready) provides sub-millisecond pauses at the cost of slightly higher CPU overhead — add -XX:+UseZGC. In Spring Boot applications, common GC pressure sources are: large response objects materialized in memory, Jackson serialization of deep object graphs, and Hibernate first-level cache accumulating entities in long-running transactions. Use async-profiler in allocation mode (-e alloc) to find the top allocation sites.

---

## 13. Best Practices

**Monitor connection pool metrics before tuning**: Install Micrometer with the Prometheus endpoint and create dashboards for `hikaricp.connections.pending`, `hikaricp.connections.acquire`, and `hikaricp.connections.active`. Make data-driven decisions — do not guess pool size.

**Set connection-timeout to 3–5 seconds in production**: The default 30 seconds allows too many requests to pile up before failing. A 3-second timeout causes fast failures and prevents request queue buildup from compounding under load.

**Always set max-lifetime below database/proxy connection timeout**: For AWS RDS Proxy (1800s timeout), set max-lifetime to 1500s. For MySQL with 8-hour wait_timeout, the default 1800s is fine. Check your database configuration explicitly.

**Use JOIN FETCH or EntityGraph for known collection associations**: Audit all service methods that iterate over JPA entity collections. Every o.getCollection() in a loop where the collection is LAZY is a potential N+1. Enable Hibernate statistics in staging to quantify the impact.

**Do not hold @Transactional across external service calls**: Refactor @Transactional methods that call HTTP APIs or message brokers. Split into: (1) pre-external-call DB transaction, (2) external call (no transaction), (3) post-external-call DB transaction.

**Prefer Redis TTL + @CacheEvict over very long TTLs**: Long TTLs increase cache hit rate but risk stale data. Combine a moderate TTL (minutes to hours) with explicit @CacheEvict on write paths for data correctness without sacrificing hit rate.

**Use spring-context-indexer in production builds**: It is a pure compile-time change with zero runtime downside. It removes classpath scanning overhead and is especially impactful for large multi-module applications.

**Enable virtual threads via one property for Spring Boot 3.2+ on Java 21**: spring.threads.virtual.enabled=true is a one-line change that eliminates thread pool as a bottleneck for I/O-bound workloads. Test for pinning issues with -Djdk.tracePinnedThreads=full.

**Exclude unused auto-configurations explicitly**: Review the autoconfiguration report (--debug flag on startup) to see which auto-configurations are active. Exclude ones that configure unused infrastructure (Kafka, ActiveMQ, Liquibase) to reduce startup time and context complexity.

**Test with production-realistic data volumes**: Connection pool sizing and query performance tests on 100-row tables will not reveal production behavior on million-row tables. Use Testcontainers with realistic data volumes in CI for query performance regression testing.

---

## 14. Case Study

### Problem: Order Service Degrading Under Load with 4-Second P99 Latency

A Spring Boot order service handling 300 requests per second was experiencing 4-second P99 latency under normal load and complete unavailability (timeouts) during peak traffic of 500 RPS. The service was using HikariCP with default settings, had no caching, and was running on a 4-core EC2 instance connecting to an RDS PostgreSQL instance.

### Investigation

**Step 1 — HikariCP metrics**: `hikaricp.connections.pending` was consistently 15–30 during load tests. The pool size was 10 (default). Connection acquire time P99 was 3.2 seconds — threads were waiting nearly 3 seconds just to get a connection.

**Step 2 — SQL analysis**: Enabled `spring.jpa.properties.hibernate.generate_statistics=true`. The query execution count per request was 47 on average. The application loaded an order (1 query), then loaded its 20 items (20 queries), then loaded the product for each item (20 queries), then loaded the customer (1 query), then loaded the customer's address (1 query) — classic N+1 in three layers.

**Step 3 — Transaction scope**: Found that `OrderService.processOrder()` was annotated @Transactional and internally called a PaymentGatewayClient HTTP call. The DB connection was held open for the duration of the HTTP call, averaging 800ms. With 10 connections held for 800ms each, the maximum throughput was 10/0.8 = 12.5 transactions per second — far below the target 300 RPS.

**Step 4 — No caching**: Product catalog data (SKU, description, price) was fetched from the DB on every order. 90% of orders involved the same 200 products.

### Fixes Applied

**Fix 1 — Connection pool sizing**
```yaml
spring.datasource.hikari.maximum-pool-size: 20  # (4 cores * 2) + 1 = 9, rounded up to 20 for headroom
spring.datasource.hikari.connection-timeout: 5000
spring.datasource.hikari.max-lifetime: 1500000  # RDS Proxy timeout is 1800s
```

**Fix 2 — N+1 elimination**
```java
// Replaced N+1 with a DTO projection query
@Query("""
    SELECT new com.rutik.OrderDTO(
        o.id, o.status, c.name, c.email,
        SUM(i.quantity * p.price)
    )
    FROM Order o
    JOIN o.customer c
    JOIN o.items i
    JOIN i.product p
    WHERE o.id = :orderId
    GROUP BY o.id, o.status, c.name, c.email
    """)
Optional<OrderDTO> findOrderSummary(@Param("orderId") Long orderId);
// 47 queries per request -> 1 query per request
```

**Fix 3 — Transaction scope refactoring**
```java
// Separated DB work from HTTP call
@Transactional
public Order createOrder(CreateOrderRequest req) { ... } // fast DB transaction

public void chargeAndConfirm(Long orderId) {
    PaymentResult result = paymentClient.charge(orderId); // no TX held
    updateOrderStatus(orderId, result.getStatus()); // new fast TX
}
```

**Fix 4 — Product catalog caching**
```java
@Cacheable(cacheNames = "products", key = "#productId")
public Product getProduct(Long productId) { return productRepo.findById(productId).orElseThrow(); }

// Warm up on startup
@EventListener(ApplicationReadyEvent.class)
public void warmUp() {
    productRepo.findTop200ByOrderByOrderFrequencyDesc().forEach(p -> getProduct(p.getId()));
}
```

### Results

| Metric | Before | After |
|--------|--------|-------|
| P50 latency | 280ms | 18ms |
| P99 latency | 4,100ms | 95ms |
| Max throughput | ~120 RPS | >600 RPS |
| HikariCP pending (at 300 RPS) | 20–30 | 0 |
| Queries per request | 47 | 1 |
| Cache hit rate (products) | 0% | 94% |
| Pool exhaustion events | Multiple per minute | Zero |

### Key Lessons

- The HikariCP default pool size of 10 is almost always wrong for production. The first action on any new service should be to tune pool size based on the formula and load test.
- N+1 queries are the most common source of unexpected latency in Spring Boot services. Hibernate statistics exposure should be on by default in staging environments.
- @Transactional scope determines how long connections are held. Every millisecond a connection is held under a blocking external call is a connection that cannot serve another request.
- Caching product catalog data — stable, frequently read, large savings on DB load — provided a 6x throughput increase at zero infrastructure cost (used existing Redis cluster).
- The combined effect of four changes (pool sizing, N+1 fix, transaction scope, caching) reduced P99 from 4.1 seconds to 95ms — a 43x improvement — without any infrastructure changes beyond configuration.

---

**Expanded Case Study: Migrating a 200 TPS REST API to Virtual Threads and GraalVM Native**

**Scenario:** A fleet management API (Java 17, Spring Boot 3.1, 200 TPS peak) runs on 8-core Kubernetes pods with 4GB heap. Platform-thread pool (200 Tomcat threads) is saturated at 180 TPS because DB queries average 40ms blocking. P99 latency is 1,200ms. The team evaluates three performance levers: (1) virtual threads (Java 21 LTS), (2) HikariCP pool tuning, (3) GraalVM native compilation. Each lever is benchmarked independently.

**Scale:** 200 TPS sustained, peak 400 TPS on Monday morning GPS batch sync. 8 cores, 4GB heap, 40ms DB p50, PostgreSQL with 20 existing HikariCP connections.

```
Bottleneck analysis (JFR profiling, 30-minute recording):

  200 Tomcat threads:
    40% blocked on JDBC (40ms avg)
    25% blocked on HTTP client calls (external GPS API, 80ms avg)
    15% blocked on Redis (5ms avg)
    20% active CPU (parsing, business logic)

  CPU utilization: 22% (8 cores × 22% = 1.76 cores in use)
  Thread utilization: 98% (196/200 threads blocked at any given time)
  → This is a classic I/O-bound profile: threads are the bottleneck, not CPU

After virtual threads:
  CPU utilization: 45% (same I/O time, but threads never block OS threads)
  Threads: unlimited (JVM creates carriers on demand, 8 OS threads total)
  TPS capacity: 400+ (I/O-bound ceiling removed)
```

**Virtual threads enablement (Spring Boot 3.2+):**

```yaml
# application.yml — single property, zero code changes
spring:
  threads:
    virtual:
      enabled: true   # Replaces Tomcat's platform-thread pool with virtual threads
```

**HikariCP pool sizing formula (with virtual threads):**

```java
// Platform threads: pool = (cores × 2) + spindle_count = (8×2) + 0 = 16
// HikariCP default: 10  ← WRONG for this workload

// With virtual threads: pool can be larger because VT suspension is cheap
// Formula: pool_size = (TPS × avg_hold_time_ms) / 1000
// = (400 × 40ms) / 1000 = 16 connections for sustaining 400 TPS
// Add 25% headroom: 20 connections

@Bean
@ConfigurationProperties("spring.datasource.hikari")
public HikariConfig hikariConfig() {
    HikariConfig cfg = new HikariConfig();
    cfg.setMaximumPoolSize(20);          // matches DB server capacity
    cfg.setMinimumIdle(5);               // don't hold idle connections
    cfg.setConnectionTimeout(2000);      // fail fast — 2s max wait
    cfg.setIdleTimeout(600_000);         // 10 min idle before eviction
    cfg.setKeepaliveTime(60_000);        // keep connections alive to PostgreSQL
    return cfg;
}
```

**BROKEN→FIX: ThreadLocal state leaks with virtual threads**

```java
// BROKEN: MDC (which uses ThreadLocal) on virtual threads is safe per-request,
// but a shared ThreadLocal holding a non-request-scoped object leaks between requests
// if the virtual thread carrier is reused (it isn't, but reused platform threads are)

// Common mistake: using ThreadLocal for connection-per-thread pattern
private static final ThreadLocal<Connection> CONN = new ThreadLocal<>();

public void doWork() {
    CONN.set(dataSource.getConnection());
    // ... if work throws and CONN is never cleaned, connection leaks
    // With virtual threads, the thread may be GC'd but connection not returned
}

// FIX: always use try-with-resources; never store Connection in ThreadLocal
public void doWork() {
    try (Connection conn = dataSource.getConnection()) {
        // conn closed on exit, always — no ThreadLocal needed
        execute(conn);
    }
}
```

**BROKEN→FIX: synchronized block pins virtual threads to OS thread**

```java
// BROKEN: synchronized method blocks the OS carrier thread while holding DB lock
// Negates the benefit of virtual threads — all other VTs on this carrier queue up
public synchronized void updateFleetCache(String key, FleetData data) {
    // 40ms Redis write inside synchronized — pins OS thread!
    redis.set(key, data);
}

// FIX: use ReentrantLock (JDK 21 makes VT-aware) or restructure to avoid locking
private final ReentrantLock lock = new ReentrantLock();

public void updateFleetCache(String key, FleetData data) {
    lock.lock();    // VT suspends instead of pinning OS thread
    try {
        redis.set(key, data);
    } finally {
        lock.unlock();
    }
}

// BETTER FIX: use ConcurrentHashMap for cache — no lock needed
private final ConcurrentHashMap<String, FleetData> cache = new ConcurrentHashMap<>();
public void updateFleetCache(String key, FleetData data) {
    cache.put(key, data);  // CAS internally, no pinning
}
```

**GraalVM Native Image — startup time reduction:**

```xml
<!-- pom.xml — Spring Boot Native support -->
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
</plugin>
```

```bash
# Build native image (takes 3-5 minutes, AOT compilation)
./mvnw -Pnative native:compile

# Before: JVM startup
# Started FleetApplication in 4.2 seconds

# After: Native image startup  
# Started FleetApplication in 0.08 seconds (52x faster)
```

**GraalVM trade-offs:**

```
JVM (platform threads) → JVM (virtual threads) → GraalVM Native

Startup time:   4.2s           4.2s               0.08s
Throughput:     200 TPS        400 TPS            380 TPS (JIT not available)
Memory:         512MB RSS      480MB RSS          180MB RSS
Build time:     15s            15s                4min
Reflection:     free           free               requires hints
JFR profiling:  full           full               limited
Lambda friendly: yes           yes                caution (reflection hints)
```

**Lazy initialization for faster startup (non-native):**

```yaml
# application.yml
spring:
  main:
    lazy-initialization: true    # beans created on first use, not at startup
    # Trade-off: first request to a cold bean is slower
    # Use for serverless / scale-to-zero where startup dominates
```

**Benchmark results (JMH, 8-core EC2, 400 TPS sustained load):**

| Configuration | p50 latency | p99 latency | TPS capacity | Memory |
|---|---|---|---|---|
| Platform threads (200) | 42ms | 1,200ms | 180 TPS | 512MB |
| Virtual threads + tuned pool | 41ms | 95ms | 420 TPS | 490MB |
| GraalVM Native + VT | 40ms | 88ms | 400 TPS | 180MB |
| GraalVM Native, no VT | 42ms | 140ms | 310 TPS | 165MB |

**Interview discussion points:**

**Why do virtual threads not help CPU-bound workloads?** Virtual threads reduce blocking overhead: when a virtual thread blocks on I/O, the OS carrier thread is released to run other virtual threads. For CPU-bound work (parsing, cryptography, matrix multiplication), threads are never blocked — they use the CPU continuously. Adding more virtual threads beyond `Runtime.availableProcessors()` for CPU-bound code just adds scheduling overhead.

**What is "pinning" in virtual thread context and how do you detect it?** A virtual thread is "pinned" to its OS carrier thread when it blocks inside a `synchronized` block or method. While pinned, the carrier cannot execute other virtual threads, defeating the purpose. Detect pinning via `-Djdk.tracePinnedThreads=full` JVM flag (logs stack traces when pinning occurs) or JFR with the `jdk.VirtualThreadPinned` event. Fix by replacing `synchronized` with `ReentrantLock` or restructuring to avoid locks on I/O paths.

**How do you size the HikariCP pool when using virtual threads?** With platform threads, pool size = thread count (threads block, holding connections). With virtual threads, threads are cheap but DB connections are still expensive OS resources. Size based on actual connection hold time: `pool = TPS × avg_hold_ms / 1000` + headroom. Avoid a pool larger than the DB server's `max_connections / service_count` — PostgreSQL default is 100; with 5 services each getting 20 connections, you exactly hit the limit.

**What are the trade-offs of GraalVM Native Image for a Spring Boot service?** Native images compile ahead-of-time: startup drops from seconds to milliseconds, memory footprint shrinks 60-70%. The cost: JIT compilation is unavailable (peak throughput ~10-15% lower than warmed JVM), reflection requires explicit hints (`@RegisterReflectionForBinding`, `reflect-config.json`), build time is 3-5 minutes vs. 15 seconds, and runtime profiling (JFR, async-profiler) has limited support. Best fit: serverless functions, batch jobs that start/stop frequently, or microservices where memory cost dominates.

**How does lazy initialization interact with health checks on startup?** With `spring.main.lazy-initialization=true`, beans initialize on first use. A readiness probe that calls `/actuator/health` before the first real request will get a "healthy" response — but the first real request hits cold beans and can be slow. For production, warm up critical paths in an `ApplicationReadyEvent` listener or use a dedicated readiness probe that exercises the critical bean path.
