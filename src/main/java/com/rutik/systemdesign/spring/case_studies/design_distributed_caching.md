# Distributed Caching Layer for Microservices with Redis and Caffeine

## Problem Statement

Design a distributed caching layer for a microservices platform to reduce PostgreSQL database load by 80%. The system currently processes 20,000 read requests per second at peak, 85% of which hit the same ~50,000 frequently-accessed product and catalog records. The cache must:

- Serve 17,000 req/sec (85% of reads) from cache rather than the database
- Support per-cache TTL configuration (product catalog: 1 hour, user sessions: 30 minutes, pricing: 5 minutes)
- Prevent cache stampede when a popular key expires and 500 concurrent threads request it simultaneously
- Invalidate cache entries in real time when data is updated (not just wait for TTL expiration)
- Warm the cache on service startup to prevent cold-start latency spikes after deployments
- Implement a two-level cache: local in-process Caffeine (L1) for sub-millisecond access, backed by Redis (L2) for cross-instance consistency

Constraints: Spring Boot 3.x, Redis 7 (single instance for simplicity, cluster-ready design), Caffeine 3.x for L1, 5 microservice instances behind a load balancer.

---

## Architecture Overview

```
  [Client Request]
        |
        v
  [Spring Service Layer]
        |
        v
  [@Cacheable / @CachePut / @CacheEvict]
        |
        v
  [CompositeCacheManager]
        |
     L1 check
        |
   +----+----+
   |         |
  HIT       MISS
   |         |
   |         v
   |   [CaffeineCache L1]
   |   miss --> promote on hit
   |         |
   |         v
   |   [RedisCacheManager L2]
   |         |
   |      +--+--+
   |      |     |
   |     HIT   MISS
   |      |     |
   |      |     v
   |      |  [PostgreSQL DB]
   |      |     |
   |      |  populate L2 (Redis)
   |      |  populate L1 (Caffeine)
   |      |
   |   return + promote to L1
   |
   return to caller

  Cache Invalidation (write path):
  [@CachePut] --> update L2 (Redis) --> Redis Pub/Sub "cache-invalidation" channel
                                               |
                               +---------------+---------------+
                               |               |               |
                        [Instance 1]    [Instance 2]    [Instance 3]
                        evict L1         evict L1        evict L1
```

---

## Key Design Decisions

### 1. Two-Level Cache (Caffeine L1 + Redis L2)

A Redis-only cache adds ~0.5–2ms of network latency per cache hit. At 17,000 req/sec, that is 8.5–34 seconds of aggregated latency per second — unacceptable for sub-10ms p99 targets. Caffeine as an in-process L1 cache eliminates the network hop for hot keys, achieving sub-100 microsecond access. Redis as L2 provides cross-instance consistency: when one instance updates a record, other instances can still serve the L2 value while their L1 is being invalidated via Pub/Sub.

### 2. sync=true for Cache Stampede Prevention

When a popular cache key expires, without synchronization every concurrent request misses the cache and hits the database simultaneously. With `@Cacheable(sync=true)`, Spring delegates to the cache implementation's native locking — Caffeine's synchronous loading ensures only one thread computes the value while others wait. For Redis, a distributed lock (via Redisson) ensures only one instance fetches from the database. The tradeoff is increased latency for the waiting threads, but this is far better than 500 simultaneous database queries.

### 3. Redis Pub/Sub for L1 Cross-Instance Invalidation

When one instance updates a record and evicts its L1 entry, other instances' L1 caches still hold the stale value. Broadcasting a cache invalidation message over Redis Pub/Sub ensures all instances evict their L1 entry within milliseconds of a write. This achieves eventual consistency across the cluster without requiring a centralized L1 — each instance maintains an independent Caffeine cache that stays fresh.

### 4. Per-Cache TTL via RedisCacheManager

Different data types have different staleness tolerances. Product descriptions are stable for hours; pricing data must be fresh within minutes. Configuring per-cache TTL ensures each cache domain matches its business staleness requirement without a global one-size-fits-all TTL. The `RedisCacheManagerBuilderCustomizer` allows this without subclassing the cache manager.

### 5. ApplicationRunner for Cache Warming

After a deployment, all instances start with empty Caffeine L1 caches. The first 30–60 seconds after deployment would show high database load as the cache warms up. An `ApplicationRunner` that pre-loads the top 10,000 most-accessed keys from the database into both L1 and L2 on startup prevents this cold-start spike. The warming run completes before the instance is marked healthy in the load balancer (readiness probe integration).

---

## Implementation

### Cache Configuration

```java
package com.rutik.systemdesign.spring.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class CacheConfig {

    // -------------------------------------------------------------------------
    // Redis L2 Cache Manager
    // -------------------------------------------------------------------------

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration for all Redis caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()))
            // Store null values to prevent cache penetration attacks on missing keys
            .disableCachingNullValues();

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("products",
            defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("pricing",
            defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put("userSessions",
            defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigs.put("categoryTree",
            defaultConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigs.put("inventory",
            defaultConfig.entryTtl(Duration.ofMinutes(2)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }

    // -------------------------------------------------------------------------
    // Caffeine L1 Cache Manager
    // -------------------------------------------------------------------------

    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Global Caffeine spec: max 50,000 entries, expire 5 minutes after write
        // (shorter than Redis TTL — L1 is a hot-data buffer, not a full replica)
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats());  // Enable hit/miss stats for Micrometer
        return manager;
    }

    // -------------------------------------------------------------------------
    // Two-Level Composite Cache Manager
    // -------------------------------------------------------------------------

    @Bean
    @Primary  // This is the default CacheManager injected by @Cacheable
    public CacheManager compositeCacheManager(
            CaffeineCacheManager caffeineCacheManager,
            RedisCacheManager redisCacheManager) {
        return new TwoLevelCacheManager(caffeineCacheManager, redisCacheManager);
    }
}
```

### Two-Level Cache Manager

```java
package com.rutik.systemdesign.spring.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A CacheManager that wraps each cache name as a TwoLevelCache:
 * reads check L1 (Caffeine) first, then L2 (Redis), then the database loader.
 * Writes go to both L1 and L2.
 */
public class TwoLevelCacheManager implements CacheManager {

    private final CacheManager l1Manager;   // Caffeine
    private final CacheManager l2Manager;   // Redis

    public TwoLevelCacheManager(CacheManager l1Manager, CacheManager l2Manager) {
        this.l1Manager = l1Manager;
        this.l2Manager = l2Manager;
    }

    @Override
    public Cache getCache(String name) {
        Cache l1Cache = l1Manager.getCache(name);
        Cache l2Cache = l2Manager.getCache(name);
        if (l1Cache == null || l2Cache == null) {
            return null;
        }
        return new TwoLevelCache(name, l1Cache, l2Cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return l2Manager.getCacheNames();
    }
}
```

```java
package com.rutik.systemdesign.spring.cache;

import org.springframework.cache.Cache;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;

/**
 * A Cache that checks L1 first, falls through to L2, and promotes L2 hits to L1.
 */
public class TwoLevelCache implements Cache {

    private final String name;
    private final Cache l1;   // Caffeine
    private final Cache l2;   // Redis

    public TwoLevelCache(String name, Cache l1, Cache l2) {
        this.name = name;
        this.l1 = l1;
        this.l2 = l2;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return l2.getNativeCache();
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        // Check L1 first
        ValueWrapper l1Value = l1.get(key);
        if (l1Value != null) {
            return l1Value;
        }

        // Fall through to L2
        ValueWrapper l2Value = l2.get(key);
        if (l2Value != null) {
            // Promote L2 hit to L1 for faster future access
            l1.put(key, l2Value.get());
        }
        return l2Value;
    }

    @Override
    @Nullable
    public <T> T get(Object key, Class<T> type) {
        T l1Value = l1.get(key, type);
        if (l1Value != null) {
            return l1Value;
        }
        T l2Value = l2.get(key, type);
        if (l2Value != null) {
            l1.put(key, l2Value);
        }
        return l2Value;
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        // This method is used by @Cacheable(sync=true) — delegate to L2's locking mechanism
        // L2 (Redis via Redisson or sync=true) handles the distributed lock
        T value = l2.get(key, valueLoader);
        if (value != null) {
            l1.put(key, value);
        }
        return value;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        l1.put(key, value);
        l2.put(key, value);
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        l1.putIfAbsent(key, value);
        return l2.putIfAbsent(key, value);
    }

    @Override
    public void evict(Object key) {
        l1.evict(key);
        l2.evict(key);
    }

    @Override
    public void clear() {
        l1.clear();
        l2.clear();
    }
}
```

### Service Layer — @Cacheable, @CachePut, @CacheEvict

```java
package com.rutik.systemdesign.spring.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CacheInvalidationPublisher cacheInvalidationPublisher;

    public ProductService(ProductRepository productRepository,
                          CacheInvalidationPublisher cacheInvalidationPublisher) {
        this.productRepository = productRepository;
        this.cacheInvalidationPublisher = cacheInvalidationPublisher;
    }

    /**
     * Cache-aside: check cache first, load from DB on miss.
     * sync=true prevents cache stampede: only one thread loads from DB,
     * others wait for the result rather than all hitting the DB simultaneously.
     */
    @Cacheable(value = "products", key = "#productId", sync = true)
    public Product getProduct(String productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productId));
    }

    /**
     * Write-through: update DB and update cache in one operation.
     * @CachePut always executes the method body and stores the result in the cache.
     * Unlike @Cacheable, it never skips the method call.
     */
    @CachePut(value = "products", key = "#product.id")
    public Product updateProduct(Product product) {
        Product saved = productRepository.save(product);
        // Publish invalidation to other instances' L1 caches via Redis Pub/Sub
        cacheInvalidationPublisher.publishInvalidation("products", product.getId());
        return saved;
    }

    /**
     * Evict cache on delete.
     */
    @CacheEvict(value = "products", key = "#productId")
    public void deleteProduct(String productId) {
        productRepository.deleteById(productId);
        cacheInvalidationPublisher.publishInvalidation("products", productId);
    }

    /**
     * Evict all entries in the products cache (e.g., after a bulk import).
     * allEntries=true is expensive — use only for bulk operations.
     */
    @CacheEvict(value = "products", allEntries = true)
    public void invalidateProductCache() {
        cacheInvalidationPublisher.publishInvalidation("products", "*");
    }

    /**
     * Pricing data — shorter TTL (5 min), no sync=true because price misses
     * are cheap (pricing service is fast), and strict ordering matters less.
     */
    @Cacheable(value = "pricing", key = "#productId + ':' + #customerId")
    public Price getPrice(String productId, String customerId) {
        return productRepository.calculatePrice(productId, customerId);
    }
}
```

### Cache Invalidation via Redis Pub/Sub

```java
package com.rutik.systemdesign.spring.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes cache invalidation messages to a Redis Pub/Sub channel.
 * All service instances subscribe to this channel and evict their L1 entries.
 */
@Component
public class CacheInvalidationPublisher {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationPublisher.class);
    private static final String CHANNEL = "cache-invalidation";

    private final StringRedisTemplate redisTemplate;

    public CacheInvalidationPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishInvalidation(String cacheName, String key) {
        String message = cacheName + ":" + key;
        redisTemplate.convertAndSend(CHANNEL, message);
        log.debug("Published cache invalidation: channel={} message={}", CHANNEL, message);
    }
}
```

```java
package com.rutik.systemdesign.spring.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Listens for cache invalidation messages and evicts entries from the local L1 cache.
 * This ensures that when any instance updates a value, all other instances' L1 caches
 * become consistent within milliseconds.
 */
@Component
public class CacheInvalidationListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationListener.class);

    private final CacheManager caffeineCacheManager;  // L1 only — L2 was already updated by @CachePut

    public CacheInvalidationListener(CacheManager caffeineCacheManager) {
        this.caffeineCacheManager = caffeineCacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.debug("Received cache invalidation: {}", body);

        String[] parts = body.split(":", 2);
        if (parts.length != 2) {
            log.warn("Malformed invalidation message: {}", body);
            return;
        }

        String cacheName = parts[0];
        String key = parts[1];

        var cache = caffeineCacheManager.getCache(cacheName);
        if (cache == null) {
            log.debug("No local cache for name={}, ignoring invalidation", cacheName);
            return;
        }

        if ("*".equals(key)) {
            cache.clear();
            log.info("Cleared L1 cache: name={}", cacheName);
        } else {
            cache.evict(key);
            log.debug("Evicted L1 cache entry: name={} key={}", cacheName, key);
        }
    }
}
```

```java
package com.rutik.systemdesign.spring.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationListener cacheInvalidationListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
            cacheInvalidationListener,
            new ChannelTopic("cache-invalidation"));
        return container;
    }
}
```

### Cache Warming on Startup

```java
package com.rutik.systemdesign.spring.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Warms the cache with the top N most-accessed records on startup.
 * Runs after all beans are initialized and before the readiness probe passes.
 * Prevents cold-start latency spikes after deployments.
 */
@Component
public class CacheWarmingRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmingRunner.class);
    private static final int WARM_PRODUCT_COUNT = 10_000;

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;

    public CacheWarmingRunner(ProductRepository productRepository,
                               CacheManager cacheManager) {
        this.productRepository = productRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting cache warming for top {} products", WARM_PRODUCT_COUNT);
        long startMs = System.currentTimeMillis();

        // Load top products by access frequency from database
        List<Product> hotProducts = productRepository.findTopByAccessFrequency(WARM_PRODUCT_COUNT);

        var productCache = cacheManager.getCache("products");
        if (productCache == null) {
            log.warn("Cache 'products' not available for warming");
            return;
        }

        int count = 0;
        for (Product product : hotProducts) {
            productCache.put(product.getId(), product);
            count++;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        log.info("Cache warming complete: warmedEntries={} durationMs={}", count, durationMs);
    }
}
```

### Micrometer Cache Metrics

```java
package com.rutik.systemdesign.spring.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registers Micrometer gauges for Caffeine L1 cache statistics.
 * Redis metrics are exposed automatically via spring-boot-actuator + Redis health indicator.
 */
@Component
public class CacheMetricsRegistrar implements ApplicationListener<ApplicationReadyEvent> {

    private final MeterRegistry meterRegistry;
    private final CaffeineCacheManager caffeineCacheManager;

    private static final List<String> CACHE_NAMES = List.of(
        "products", "pricing", "userSessions", "categoryTree", "inventory"
    );

    public CacheMetricsRegistrar(MeterRegistry meterRegistry,
                                  CaffeineCacheManager caffeineCacheManager) {
        this.meterRegistry = meterRegistry;
        this.caffeineCacheManager = caffeineCacheManager;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        for (String cacheName : CACHE_NAMES) {
            var springCache = caffeineCacheManager.getCache(cacheName);
            if (springCache == null) continue;

            // Caffeine's native cache is exposed via CaffeineCache.getNativeCache()
            Cache<Object, Object> nativeCache =
                (Cache<Object, Object>) springCache.getNativeCache();

            Gauge.builder("cache.l1.hit.rate", nativeCache,
                          c -> c.stats().hitRate())
                 .tag("cache", cacheName)
                 .description("L1 Caffeine cache hit rate")
                 .register(meterRegistry);

            Gauge.builder("cache.l1.size", nativeCache,
                          c -> c.estimatedSize())
                 .tag("cache", cacheName)
                 .description("Estimated number of entries in L1 cache")
                 .register(meterRegistry);

            Gauge.builder("cache.l1.eviction.count", nativeCache,
                          c -> c.stats().evictionCount())
                 .tag("cache", cacheName)
                 .description("Total number of L1 cache evictions")
                 .register(meterRegistry);
        }
    }
}
```

### Redis Configuration

```java
package com.rutik.systemdesign.spring.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        LettucePoolingClientConfiguration clientConfig =
            LettucePoolingClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(500))  // Fail fast on Redis unavailability
                .poolConfig(poolConfig())
                .build();

        // In production: use RedisClusterConfiguration for Redis Cluster
        org.springframework.data.redis.connection.RedisStandaloneConfiguration serverConfig =
            new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                "redis.internal", 6379);

        return new LettuceConnectionFactory(serverConfig, clientConfig);
    }

    private org.apache.commons.pool2.impl.GenericObjectPoolConfig<?>
            poolConfig() {
        org.apache.commons.pool2.impl.GenericObjectPoolConfig<?> config =
            new org.apache.commons.pool2.impl.GenericObjectPoolConfig<>();
        config.setMaxTotal(50);
        config.setMaxIdle(20);
        config.setMinIdle(10);
        config.setMaxWait(Duration.ofMillis(500));
        return config;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
```

### Application Properties

```yaml
# application.yml
spring:
  cache:
    type: none  # Disable Spring Boot auto-configured cache manager — we configure our own

  data:
    redis:
      host: redis.internal
      port: 6379
      timeout: 500ms
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 10

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,caches
  metrics:
    tags:
      application: product-service
```

---

## Spring Components Used

| Spring Component | Purpose |
|---|---|
| `RedisCacheManager` | Manages named Redis caches with per-cache TTL configuration |
| `RedisCacheConfiguration` | Configures serialization, TTL, and null-value handling per cache |
| `CaffeineCacheManager` | Manages in-process Caffeine caches with size bounds and write expiry |
| `@Cacheable(sync=true)` | Cache-aside with distributed locking to prevent cache stampede |
| `@CachePut` | Write-through: always executes method and updates both L1 and L2 |
| `@CacheEvict` | Evicts a specific key or all entries from the named cache |
| `RedisMessageListenerContainer` | Subscribes to Redis Pub/Sub channels; routes messages to `MessageListener` |
| `MessageListener` | Receives invalidation messages and evicts local L1 Caffeine entries |
| `StringRedisTemplate` | Publishes string invalidation messages to the Redis channel |
| `ApplicationRunner` | Warms caches on startup before readiness probe passes |
| `ApplicationListener<ApplicationReadyEvent>` | Registers Micrometer gauges after all caches are fully initialized |
| `LettuceConnectionFactory` | Reactive Redis connection factory with connection pooling |

---

## Tradeoffs and Alternatives

### Two-Level Cache vs Redis-Only

| Approach | Latency | Cross-Instance Consistency | Complexity |
|---|---|---|---|
| Redis only | 0.5–2ms per cache hit | Strong | Low |
| Caffeine L1 + Redis L2 (chosen) | 0.05ms L1 hit, 1ms L2 miss | Eventual (Pub/Sub propagation) | Medium |
| Caffeine L1 only | 0.05ms hit | None (stale across instances) | Low |

Two-level cache was chosen because the target is sub-10ms p99 for API responses — a 2ms Redis round-trip on every cache hit adds up across multiple service calls in a request. The Pub/Sub invalidation ensures L1 entries are evicted within ~5ms of a write, which is acceptable eventual consistency for product catalog data.

### Cache-Aside vs Read-Through

Cache-aside (`@Cacheable`) — the service layer explicitly manages the cache. The application code is aware of caching. Read-through — a separate cache loader fetches from the database, transparent to the service. Spring's cache abstraction implements cache-aside. Read-through would require a custom `CacheLoader` per cache, which was not necessary here since the service layer already owns the loading logic.

### Pub/Sub vs Redis Keyspace Notifications for Invalidation

Redis keyspace notifications (firing events when Redis keys expire or are deleted) were considered but rejected because: (1) they require `notify-keyspace-events` configuration on the Redis server, adding operational overhead; (2) they only fire on Redis-side events, not on application-level logical invalidations; (3) they cannot carry custom invalidation metadata. Application-level Pub/Sub gives full control over the invalidation message format and timing.

### TTL-Only Invalidation vs Event-Driven Invalidation

TTL-only invalidation (no Pub/Sub, just wait for entries to expire) was rejected because pricing data with a 5-minute TTL would serve stale prices for up to 5 minutes after an update. For product catalog data (1-hour TTL), this would mean stale product descriptions visible to customers for up to an hour. Event-driven invalidation reduces staleness to ~5ms (Pub/Sub propagation time) at the cost of the invalidation infrastructure.

---

## Interview Discussion Points

**Q: What happens to the L1 cache if the Redis Pub/Sub connection drops?**

A: The `RedisMessageListenerContainer` will not receive invalidation messages while the connection is down. L1 caches on all instances will serve stale data until their Caffeine TTL expires (5 minutes). Mitigations: (1) the Caffeine TTL ensures staleness is bounded at 5 minutes even without invalidation; (2) Lettuce (the Redis client library used) automatically reconnects on connection loss and resubscribes to channels; (3) monitor `cache.l1.eviction.count` in Prometheus — a drop in evictions during an expected update period indicates an invalidation channel issue.

**Q: How does sync=true prevent cache stampede for Redis-backed caches?**

A: For Caffeine caches, `sync=true` uses Caffeine's `get(key, loader)` method which is internally synchronized — only one thread calls the loader, others wait. For Redis-backed `RedisCacheManager`, Spring's `sync=true` is not natively supported — it falls back to unsynchronized access. To properly prevent Redis cache stampede, the service must acquire a distributed lock (via Redisson's `RLock` or a Redis `SET NX` with expiry) before loading from the database. The two-level cache design mitigates this: the Caffeine L1 handles stampede prevention for the common case, and a Redis miss triggers L1 loading with Caffeine's built-in synchronization.

**Q: How do you handle cache penetration — requests for keys that will never exist in the database?**

A: Cache penetration occurs when attackers flood requests for non-existent keys (e.g., random product IDs), bypassing the cache on every request and hitting the database. Mitigations: (1) cache null values — store a sentinel `null` entry with a short TTL (30 seconds) for keys that return no result from the database. The `RedisCacheConfiguration` has `.disableCachingNullValues()` removed if null caching is desired; (2) Bloom filter — a Redis Bloom filter (`RedisBloom` module) can check if a key definitely does not exist before even attempting the cache lookup, rejecting invalid keys in O(1) without database access.

**Q: How do you handle the case where a cached object's class changes between deployments (Java serialization compatibility)?**

A: With `GenericJackson2JsonRedisSerializer`, objects are stored as JSON. If a field is added, existing cached entries simply lack that field — Jackson uses the field's default value. If a field is removed, Jackson ignores the extra JSON field. If a field is renamed, you have a compatibility break — the old JSON field name will not map to the new Java field. To handle this: (1) use `@JsonProperty` to maintain backward-compatible JSON names; (2) add a cache version suffix to the cache name (e.g., `products:v2`) and roll over; (3) call `cacheEvict allEntries=true` as part of the deployment procedure before the new code starts serving traffic.

**Q: How do you implement cache-aside with a fallback when Redis is completely down?**

A: Override the `RedisCacheManager` to catch `RedisConnectionFailureException` and return a cache miss instead of throwing an exception. Spring's `@Cacheable` will then call the actual service method on every request. This degrades to database-direct mode rather than causing a full outage. Implement a circuit breaker around Redis operations (Resilience4j `@CircuitBreaker` on the `CacheManager.getCache()` call) that opens after 10 consecutive failures and automatically tries to reconnect after 30 seconds. Instrument this with a Micrometer counter for `cache.redis.fallback.count` to alert when Redis is degraded.

**Q: How do you measure whether the cache is actually reducing database load by 80%?**

A: Compare two Micrometer metrics: (1) `cache.l1.hit.rate` + Redis cache hit rate (from Redis INFO keyspace stats or Spring Boot Actuator `/actuator/caches`) gives the combined hit rate; (2) database query count per second (from HikariCP `hikaricp.connections.usage` and database slow query logs). A Grafana dashboard showing the ratio of `(cache hits)/(cache hits + DB queries)` gives the real-time reduction rate. Set an alert if the cache hit rate drops below 70% — this indicates either a cache eviction storm, a TTL misconfiguration, or an access pattern shift requiring cache sizing adjustment.

---

## Failure Scenarios and Recovery

A distributed cache is a performance optimization, but at scale the database often cannot survive without it — losing the cache becomes an availability problem because the database is sized for cache-hit traffic, not cache-miss traffic. The defining failure is Redis primary loss.

### Failure: Redis Primary Fails

In a Redis Sentinel setup, when the primary dies, Sentinel detects the failure (after `down-after-milliseconds`, typically ~5s) and promotes a replica to primary — the full failover including client reconnection typically completes in ~30 seconds. During those 30 seconds, every cache lookup misses and falls through to the database. If the cache normally absorbs 80% of read traffic, the database suddenly sees 5x its provisioned load — a thundering herd that can take the database down, turning a cache failover into a full outage.

```
Redis primary dies (before fix):

  Clients --miss--> [ Redis: unavailable for ~30s ] 
                          |  fall-through
                          v
                    [ Database ]  <-- 5x normal load, may collapse
```

Fix: a two-level cache (local L1 in-process + Redis L2) plus a circuit breaker around Redis so the database is shielded during failover.

```java
// FIX: L1 Caffeine (in-process) absorbs reads when L2 Redis is unavailable
@Bean
public CacheManager cacheManager(RedisConnectionFactory redis) {
    // L1: Caffeine, per-instance, survives Redis outage
    CaffeineCacheManager l1 = new CaffeineCacheManager();
    l1.setCaffeine(Caffeine.newBuilder()
        .maximumSize(50_000)
        .expireAfterWrite(Duration.ofSeconds(60)));

    // L2: Redis, shared across instances
    RedisCacheManager l2 = RedisCacheManager.builder(redis)
        .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10)))
        .build();

    return new TwoLevelCacheManager(l1, l2); // checks L1, then L2, then loader
}
```

```java
// FIX: circuit breaker around Redis so a Redis blip degrades gracefully to L1 + DB
@CircuitBreaker(name = "redisCache", fallbackMethod = "fromL1OrDb")
public Product getFromRedis(String key) {
    return redisTemplate.opsForValue().get(key); // throws if Redis down
}

public Product fromL1OrDb(String key, Throwable ex) {
    Product cached = l1Cache.getIfPresent(key);   // serve stale-ish L1 if present
    return cached != null ? cached : productRepository.findByKey(key);
}
```

Recovery and time-to-recovery:
- L1 Caffeine continues serving hot keys throughout the ~30s Redis failover, so the database sees only the long-tail misses, not the full 5x spike.
- The circuit breaker opens after consecutive Redis failures, stops hammering the dead primary, and probes for recovery (`waitDurationInOpenState` ~30s), automatically closing once the promoted primary accepts traffic.
- After failover the L2 cache is cold (the new primary's dataset depends on replication; with `replica-read-only` replicas it is usually warm). Refill happens lazily on demand or via a warmup job.
- A `cache.redis.fallback.count` Micrometer counter and a Redis-down alert page operators if the degraded state persists beyond the expected failover window.

### Failure: Cache Stampede on a Hot Key Expiry

When a single very hot key expires, thousands of concurrent requests all miss and stampede the database simultaneously. Fix: request coalescing (Caffeine `get(key, loader)` lets only one thread load, others wait) for L1, and a distributed lock (Redisson `RLock` or `SET NX`) for L2 so only one node recomputes the hot value. Add jitter to TTLs so many keys do not expire in the same instant.

---

## Capacity Planning

### Memory and Shard Math

```
Cached objects:    500,000
Object size:       2 KB average (serialized JSON)
Working set:       500,000 x 2 KB = 1 GB

Redis cluster:     3 shards x 2 GB = 6 GB total capacity
Headroom:          1 GB data / 6 GB capacity = ~17% utilization
                   (leaves room for key overhead, replication buffers,
                    fragmentation ~1.5x, and growth)
Eviction policy:   allkeys-lru  -> evicts least-recently-used across all keys
                   when memory pressure hits maxmemory
```

Redis memory is more than the raw value size: each key has ~50–100 bytes of overhead (key string, expiry, internal structures), and the allocator (jemalloc) introduces fragmentation, so plan for ~1.5x the raw data size. Set `maxmemory` to ~75% of the instance RAM and `maxmemory-policy allkeys-lru` so the cache evicts cold entries gracefully under pressure rather than OOM-ing or rejecting writes.

### Throughput and Connection Budget

```
Read QPS:          100,000 reads/sec
Cache hit rate:    80%  -> 80,000 served from cache, 20,000 hit DB
With L1 (Caffeine) absorbing the hottest 50%:
                   L1 serves 50,000, L2 Redis serves 30,000, DB sees 20,000

Lettuce connections: Lettuce multiplexes on a single connection by default;
                   for 100k QPS use a small pool (e.g., 8-16) or shared
                   connection with pipelining. Avoid one-connection-per-request.

DB protection:     database sized for ~20-25k QPS; if cache hit rate drops
                   to 0 (total cache loss) DB would face 100k QPS -> 5x.
                   This is why L1 + circuit breaker are mandatory, not optional.
```

### TTL and Eviction Sizing

```
Hot tier (TTL 10 min):   product catalog, ~100k keys
Warm tier (TTL 1 hour):  user profiles, ~300k keys
Cold tier (TTL 24 hours):reference data, ~100k keys
TTL jitter:              +/- 10% random offset per key to avoid synchronized
                         mass expiry (stampede prevention).
```

---

## Additional Production War Stories

### War Story 1: Cache Key Collision Across Services

Two services, the user service and the order service, both ran against the same shared Redis cluster and both used the key `user:{id}` — one storing a `UserProfile`, the other storing a `UserOrderSummary`. They overwrote each other's entries, so reads returned the wrong type and deserialization either threw or, worse, silently returned corrupted data.

```java
// BROKEN: two different services use the same key namespace
// In user-service:
redisTemplate.opsForValue().set("user:" + id, userProfile);   // UserProfile

// In order-service:
redisTemplate.opsForValue().set("user:" + id, orderSummary);  // OrderSummary — collision!
// A read of "user:42" returns whichever service wrote last -> corrupted reads
```

```java
// FIX: namespace every key with service + entity to guarantee uniqueness
// In user-service:
redisTemplate.opsForValue().set("user-service:profile:" + id, userProfile);

// In order-service:
redisTemplate.opsForValue().set("order-service:user-summary:" + id, orderSummary);
```

```java
// FIX via Spring cache name -> the cache name becomes the key prefix
@Cacheable(cacheNames = "user-service:profile", key = "#id")
public UserProfile getProfile(String id) { ... }
// RedisCacheManager prefixes keys with the cache name by default:
// "user-service:profile::42"
```

The rule: every key in a shared cache must be namespaced `service:entity:{id}`. Rely on `RedisCacheManager`'s default cache-name key prefix, and never let two services share an unqualified key space.

### War Story 2: Caching Mutable Objects Caused Cross-Request Corruption

A service cached a mutable `Cart` object in an in-process Caffeine cache and returned the same instance to every caller. One request mutated the cart (added an item) and the change appeared in every other request reading the same cached cart, because they all shared one object reference.

```java
// BROKEN: returns the shared cached instance; callers mutate shared state
@Cacheable(cacheNames = "carts", key = "#id")
public Cart getCart(String id) { return cartRepository.findById(id); }

// caller mutates the cached instance, corrupting it for everyone
Cart c = cartService.getCart("42");
c.addItem(item); // mutates the cached object in place!
```

```java
// FIX 1: cache immutable snapshots (records) so callers cannot mutate shared state
public record CartView(String id, List<ItemView> items, BigDecimal total) {}

@Cacheable(cacheNames = "carts", key = "#id")
public CartView getCart(String id) {
    return CartView.from(cartRepository.findById(id)); // immutable copy
}
```

```java
// FIX 2: if mutable, return a defensive copy from the cache layer
@Cacheable(cacheNames = "carts", key = "#id")
public Cart getCart(String id) { return cartRepository.findById(id); }
// callers must copy: Cart working = new Cart(cartService.getCart(id));
```

Cache immutable value objects (Java records are ideal). For Redis-backed caches this is less of a risk because each read deserializes a fresh object, but for in-process L1 caches that return references, mutability is a correctness landmine.

---

## Write-Through vs Write-Behind Patterns

How writes propagate to the cache and database defines the consistency and durability tradeoffs.

| Pattern        | Write path                                                       | Consistency                          | Durability risk                                  | Use when                                  |
|----------------|------------------------------------------------------------------|--------------------------------------|--------------------------------------------------|-------------------------------------------|
| Cache-aside    | App writes DB, then evicts/updates cache                         | eventual; brief stale window         | none (DB is source of truth)                     | default; most read-heavy workloads        |
| Write-through  | App writes cache, cache synchronously writes DB                  | strong (cache and DB always agree)   | none, but write latency = cache + DB             | reads must never see stale data           |
| Write-behind   | App writes cache, cache asynchronously flushes DB later          | eventual; cache ahead of DB          | HIGH — buffered writes lost if cache crashes     | write-heavy, can tolerate some loss       |

```java
// Cache-aside write (default, safest): DB is source of truth, then invalidate
@CacheEvict(cacheNames = "user-service:profile", key = "#profile.id")
public UserProfile update(UserProfile profile) {
    return userRepository.save(profile); // DB committed; cache invalidated -> next read refills
}
```

```java
// Write-through: keep cache and DB in lockstep on every write
@CachePut(cacheNames = "user-service:profile", key = "#profile.id")
public UserProfile update(UserProfile profile) {
    return userRepository.save(profile); // cache updated with the saved value
}
```

Write-behind is the highest-performance option (it batches and coalesces writes) but trades durability: if the cache node crashes before flushing, buffered writes are lost. It is appropriate only for data where some loss is acceptable (e.g., view counters, last-seen timestamps), never for financial or order data. For most systems, cache-aside with `@CacheEvict` on write is the right default because the database remains the unambiguous source of truth and stale windows are bounded by TTL.

---

## Multi-Region Considerations

A globally cached service must decide whether the cache is replicated across regions or kept region-local. Cross-region cache replication adds latency and consistency complexity, so the common pattern is region-local caches backed by region-local or globally replicated databases.

```
        Region US (us-east-1)                    Region EU (eu-west-1)
   +----------------------------+           +----------------------------+
   | App US                     |           | App EU                     |
   |   L1 Caffeine (in-proc)    |           |   L1 Caffeine (in-proc)    |
   |   L2 Redis cluster US      |           |   L2 Redis cluster EU      |
   +-------------+--------------+           +-------------+--------------+
                 |                                        |
                 v                                        v
         [ DB primary (US) ] --async replication--> [ DB replica (EU) ]
                  ^------- invalidation events (pub/sub) -------^
```

Design changes for multi-region:
- Each region runs its own L1 + L2 cache. Caches are NOT replicated synchronously across regions — cross-region Redis replication would add 80–150ms to writes and create split-brain risk.
- Cache invalidation must cross regions: when the US region writes and invalidates a key, publish an invalidation event (Redis pub/sub, or a Kafka topic) that the EU region consumes to evict the stale entry from its local cache. This bounds cross-region staleness to the replication/propagation lag (typically seconds).
- The source-of-truth database is region-aware: a single global primary with regional read replicas (writes routed to the primary, reads served locally) or an active-active database with conflict resolution. The cache mirrors whichever model the database uses.
- Accept that a freshly written value may briefly be stale in remote regions until invalidation propagates; design read flows to tolerate bounded staleness, and use write-through with cross-region invalidation only for data that must be near-consistent globally.
- During a regional Redis outage, that region's L1 + circuit breaker + local DB replica keep it serving; other regions are unaffected because caches are independent.

---

## Additional Interview Questions

**Q: Why can losing the Redis cache cause a full outage, not just slower responses?**

A: Because the database is provisioned for cache-hit traffic, not cache-miss traffic. If the cache normally absorbs 80% of reads, the database is sized for ~20% of total QPS. When Redis fails, every read falls through and the database suddenly faces 5x its provisioned load — a thundering herd that can collapse it, turning a cache-layer failure into a full outage. The mitigations are a two-level cache where an in-process L1 (Caffeine) keeps absorbing hot keys during the ~30s Redis failover, plus a circuit breaker that stops hammering the dead primary and degrades gracefully to L1 and the database for the long tail.

**Q: How does a two-level (L1 + L2) cache help during Redis failover?**

A: L1 is an in-process Caffeine cache local to each application instance; L2 is the shared Redis cluster. During a Redis primary failure and the ~30-second Sentinel failover, L2 is unavailable, but L1 continues serving the hottest keys directly from JVM memory, so the database sees only long-tail misses instead of the full read volume. A circuit breaker around the Redis calls opens after consecutive failures, routing requests to L1-or-database and probing for Redis recovery, then closing automatically once the promoted primary is reachable. This caps the database blast radius and gives zero-downtime read availability for hot data.

**Q: How do you size a Redis cluster for 500,000 objects of 2 KB each?**

A: The raw working set is 500,000 x 2 KB = 1 GB, but Redis needs ~1.5x for key overhead (~50–100 bytes per key), expiry metadata, and allocator fragmentation, so plan for ~1.5 GB effective. A 3-shard cluster at 2 GB per shard gives 6 GB total — comfortable ~17% utilization with room for replication buffers and growth. Set `maxmemory` to ~75% of instance RAM and `maxmemory-policy allkeys-lru` so the cache evicts cold entries under pressure rather than OOM-ing or rejecting writes, and add per-key TTL jitter to avoid synchronized mass expiry.

**Q: What is the difference between write-through and write-behind, and when is each safe?**

A: Write-through writes to the cache and synchronously to the database on every write, keeping them in lockstep — strong consistency at the cost of write latency being the sum of both. Write-behind writes to the cache and asynchronously flushes to the database later, which is the fastest option because it batches and coalesces writes, but it risks data loss: if the cache node crashes before flushing, buffered writes are gone. Write-through suits read paths that must never see stale data; write-behind is acceptable only for loss-tolerant data like counters or last-seen timestamps, never for orders or payments, where cache-aside (DB as source of truth, invalidate on write) is the safe default.

**Q: How do you prevent cache key collisions in a cache shared across multiple services?**

A: Namespace every key with `service:entity:{id}` so two services can never write the same unqualified key. The classic incident is two services both using `user:{id}` for different payloads, overwriting each other and returning corrupted reads. With Spring's `@Cacheable`, the cache name becomes the Redis key prefix by default (`RedisCacheManager` prefixes `cacheName::key`), so naming caches `user-service:profile` and `order-service:user-summary` enforces isolation automatically. Never let independent services share an unqualified key space on a common cluster.

**Q: How do cache invalidations work across regions, and what staleness do you accept?**

A: Caches are kept region-local (synchronous cross-region Redis replication would add 80–150ms per write and risk split-brain), so invalidation must be propagated explicitly: when one region writes and evicts a key, it publishes an invalidation event over Redis pub/sub or a Kafka topic that other regions consume to evict the stale entry from their local caches. This bounds cross-region staleness to the propagation lag, typically seconds. Read flows must tolerate that bounded staleness; only data requiring near-global consistency uses write-through plus cross-region invalidation, while most data accepts eventual consistency with TTL as a backstop.
