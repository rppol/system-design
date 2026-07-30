# Caching — technology bank

<!-- tech-bank tier: caching -->

The 61 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Caching** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### @CacheConfig
**Short:** Spring annotation setting class-level cache defaults (cacheNames, keyGenerator, cacheManager) for @Cacheable methods.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, apis-frameworks/aop-middleware-and-scheduling @3

### @EnableRedisHttpSession
**Short:** Spring Session annotation that backs HTTP sessions with Redis and registers the session repository filter.
**Kind:** api
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2

### @EnableRedisIndexedHttpSession
**Short:** Spring Session annotation registering a Redis-backed, queryable HTTP session store and its servlet filter.
**Kind:** api
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2, apis-frameworks/dependency-injection-and-config @3

### @EnableRedisWebSession
**Short:** Spring Session annotation wiring reactive WebSession storage into Redis so WebFlux sessions survive restarts.
**Kind:** api
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2, apis-frameworks/dependency-injection-and-config @3

### @functools.lru_cache
**Short:** Python decorator memoizing a function's return values in a bounded in-process LRU; @cache is the unbounded form.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1, runtime-systems/collections-and-algorithms @2

### aiocache
**Short:** Async Python caching library with memory, Redis and Memcached backends, pluggable serializers and TTLs.
**Kind:** tech
**Lang:** python
**Roles:** caching/distributed-cache @1, caching/in-process-cache @2

### Anthropic prompt caching
**Short:** Anthropic API cache_control markers that cache a prompt prefix server-side, cutting cost and latency on reuse.
**Kind:** api
**Lang:** *
**Roles:** caching/semantic-and-llm-cache @1

### Apache Ignite
**Short:** Distributed in-memory data grid and compute platform with SQL, ACID transactions and cache-through persistence.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @2, data-movement/batch-and-distributed-compute @3, data-access/transactions-and-consistency @3

### async_lru
**Short:** asyncio-safe lru_cache: memoizes coroutine results without caching an un-awaited future.
**Kind:** tech
**Lang:** python
**Roles:** caching/in-process-cache @1, runtime-systems/concurrency-and-async @2

### AWS DAX
**Short:** DynamoDB Accelerator: a managed write-through cache in front of DynamoDB giving microsecond read latency.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @3

### AWS ElastiCache
**Short:** AWS managed Redis/Valkey and Memcached with automatic failover, multi-AZ and cluster mode.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @3, platform-delivery/cloud-platform-and-cost @3

### cache
**Short:** functools.cache: unbounded dict-backed memoization decorator with O(1) hits.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1

### cachetools
**Short:** Pure-Python in-process cache library: LRU, LFU, TTL and RR caches plus method decorators, with no infrastructure.
**Kind:** tech
**Lang:** python
**Roles:** caching/in-process-cache @1

### Caffeine
**Short:** High-performance on-heap Java cache with W-TinyLFU eviction, size/TTL policies and weak/soft key modes.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1

### Caffeine Caffeine.newBuilder().expireAfter
**Short:** Caffeine builder hook installing a pluggable per-entry expiry policy on a local JVM cache.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, apis-frameworks/design-patterns-and-principles @3

### Caffeine's LoadingCache wrapping a loader
**Short:** Self-populating in-heap cache memoizing an expensive loader call, with TTL and size eviction.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, apis-frameworks/design-patterns-and-principles @2

### cashews
**Short:** Async-first Python cache: decorators, tags, soft TTL and built-in anti-stampede locking over Redis or memory.
**Kind:** tech
**Lang:** python
**Roles:** caching/distributed-cache @1, caching/in-process-cache @2

### CDN
**Short:** Content delivery network: geographically distributed edge caches serving static and cacheable dynamic HTTP.
**Kind:** concept
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @3

### Cloudflare
**Short:** Global edge network: CDN caching, TLS termination, WAF/DDoS mitigation, rate limiting, DNS and edge compute.
**Kind:** tech
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @2, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/service-mesh-and-discovery @3, platform-delivery/cloud-platform-and-cost @3

### CloudFront
**Short:** AWS CDN that caches HTTP responses at edge PoPs and terminates TLS, WAF and edge compute there.
**Kind:** tech
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @2, platform-delivery/cloud-platform-and-cost @3

### dogpile.cache
**Short:** Python cache front-end with pluggable backends and dogpile locking so one worker recomputes an expired key.
**Kind:** tech
**Lang:** python
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2

### Ehcache
**Short:** JVM in-process cache with JCache (JSR-107) compatibility, tiered heap/offheap/disk storage and TTL eviction.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1

### fastapi-cache2
**Short:** Decorator-based response cache for FastAPI endpoints with pluggable Redis or in-memory backends.
**Kind:** tech
**Lang:** python
**Roles:** caching/distributed-cache @1, caching/in-process-cache @2, apis-frameworks/aop-middleware-and-scheduling @3

### Fastly
**Short:** CDN and edge cloud: cacheing PoPs with instant purge, TLS termination, WAF/DDoS protection and edge compute.
**Kind:** tech
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @2, traffic-edge/rate-limiting-and-resilience @3, platform-delivery/cloud-platform-and-cost @3

### functools.lru_cache
**Short:** Python stdlib decorator memoizing a function's results in a bounded in-process LRU keyed by its arguments.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1

### GPTCache
**Short:** Open-source semantic cache for LLM responses: exact and embedding-similarity lookup over pluggable backends.
**Kind:** tech
**Lang:** python
**Roles:** caching/semantic-and-llm-cache @1

### Guava Cache
**Short:** Google Guava's in-heap loading cache with size/time eviction; simpler and older than Caffeine.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1

### Guava CacheBuilder
**Short:** Guava's builder for a bounded on-heap cache with size, TTL, refresh and weak/soft key or value eviction policies.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, runtime-systems/memory-processes-and-os @3

### Guava Suppliers.memoize
**Short:** Guava wrapper giving thread-safe lazy single computation of a value without hand-written double-checked locking.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, apis-frameworks/design-patterns-and-principles @2, runtime-systems/concurrency-and-async @3

### Hazelcast
**Short:** JVM in-memory data grid: partitioned distributed maps, near-cache, JCache support and in-grid compute.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @2, data-movement/batch-and-distributed-compute @3

### LMCache
**Short:** KV-cache offloading and sharing layer for vLLM, letting prefix reuse survive beyond one instance's GPU memory.
**Kind:** tech
**Lang:** python
**Roles:** caching/semantic-and-llm-cache @1, inference/inference-engine @2, caching/distributed-cache @3

### lru_cache
**Short:** Python decorator memoizing a function's results in a bounded LRU dict for O(1) hits; watch it pinning self on methods.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1, runtime-systems/collections-and-algorithms @3

### Memcached
**Short:** Multi-threaded in-memory key-value cache; pure string cache with no persistence, scales a hot key across cores.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @2

### methodtools
**Short:** Python library giving per-instance lru_cache on methods through weak references, avoiding the leak of caching self.
**Kind:** tech
**Lang:** python
**Roles:** caching/in-process-cache @1, runtime-systems/memory-processes-and-os @3

### OpenAI Prompt Caching
**Short:** OpenAI's automatic reuse of a repeated prompt prefix across requests, cutting input token cost and time to first token.
**Kind:** tech
**Lang:** *
**Roles:** caching/semantic-and-llm-cache @1, platform-delivery/cloud-platform-and-cost @3

### Prompt caching dashboard
**Short:** Console view of prompt-cache hit rate and savings, driven by the usage.cache_* fields on each response.
**Kind:** tech
**Lang:** *
**Roles:** caching/semantic-and-llm-cache @1, observability/tracing-apm-and-llm-observability @2, platform-delivery/cloud-platform-and-cost @3

### Python functools.cache
**Short:** Unbounded memoization decorator, an alias for lru_cache(maxsize=None); the one-line way to memoize a recursion.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1, runtime-systems/collections-and-algorithms @3

### Python functools.lru_cache
**Short:** Decorator memoizing a function's results in a bounded per-function LRU cache.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1, runtime-systems/collections-and-algorithms @2

### Reactive Redis
**Short:** Spring Data Redis's non-blocking template over the Netty-based Lettuce driver, for WebFlux applications.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/drivers-and-connection-pooling @2, runtime-systems/concurrency-and-async @3

### Redis
**Short:** In-memory key-value server used as distributed cache, session/token store, rate-limit counter, and pub/sub bus.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @1, traffic-edge/rate-limiting-and-resilience @2, data-movement/message-broker @2, caching/semantic-and-llm-cache @3

### Redis Cluster
**Short:** Redis's sharded mode: 16,384 hash slots spread over primaries with replicas, for horizontal scale-out.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @2, data-access/replication-ha-and-backup @3

### Redis SETNX
**Short:** Atomic set-if-absent, the primitive behind Redis idempotency keys, dedup markers and distributed locks.
**Kind:** api
**Lang:** *
**Roles:** caching/distributed-cache @1, data-access/transactions-and-consistency @2, traffic-edge/rate-limiting-and-resilience @3

### RedisMessageStore
**Short:** Spring Integration MessageStore backed by Redis, persisting in-flight messages and aggregator state.
**Kind:** api
**Lang:** java
**Roles:** caching/distributed-cache @1, data-movement/message-broker @2

### RedisTemplate
**Short:** Spring Data Redis template collapsing connect, execute, translate-exception and release into a single call.
**Kind:** api
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/orm-and-data-mapping @2, apis-frameworks/design-patterns-and-principles @3

### Scissorhands
**Short:** Reference implementation of KV-cache token eviction, keeping only persistently important tokens.
**Kind:** tech
**Lang:** python
**Roles:** caching/semantic-and-llm-cache @1, inference/inference-engine @2

### Spring Cache
**Short:** Spring's declarative caching abstraction: @Cacheable/@CacheEvict proxies over Caffeine, Redis or any CacheManager.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2, apis-frameworks/aop-middleware-and-scheduling @2

### Spring Cache Abstraction
**Short:** Spring's @Cacheable/@CacheEvict annotations with AOP interception over a pluggable Caffeine or Redis backend.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2, apis-frameworks/aop-middleware-and-scheduling @3

### Spring Data Redis
**Short:** Spring module wrapping Lettuce/Jedis with RedisTemplate, repositories, serializers and reactive operations.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/orm-and-data-mapping @2, data-access/drivers-and-connection-pooling @3

### Spring Data Redis 3.x
**Short:** Spring's Redis integration: RedisTemplate, reactive template, @RedisHash repositories and pub/sub listeners.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/orm-and-data-mapping @2, data-movement/message-broker @3

### Spring Session
**Short:** Moves HTTP session state into Redis or a database so any instance can serve a request without sticky sessions.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2

### spring-boot-starter-cache
**Short:** Spring Boot starter auto-configuring a CacheManager, with the backend chosen by classpath and spring.cache.type.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2, apis-frameworks/dependency-injection-and-config @2

### spring-boot-starter-data-redis
**Short:** Spring Boot starter auto-configuring a Lettuce Redis connection and a RedisCacheManager behind @Cacheable.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/drivers-and-connection-pooling @2, apis-frameworks/dependency-injection-and-config @3

### spring-session-core
**Short:** Store-agnostic Session and SessionRepository abstractions plus the filter that replaces HttpSession.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2, apis-frameworks/aop-middleware-and-scheduling @3

### spring-session-data-mongodb
**Short:** Spring Session backend storing HTTP sessions in MongoDB with a TTL index for expiry, shared across instances.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-stores/document @3, security/authentication-and-identity @3

### spring-session-data-redis
**Short:** Backs HttpSession with Redis so any instance can serve any request; removes sticky sessions from horizontal scaling.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2

### spring-session-hazelcast
**Short:** Spring Session backend storing sessions in a distributed Hazelcast IMap.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1

### spring-session-jdbc
**Short:** Spring Session store keeping HTTP sessions in SPRING_SESSION tables, with scheduled expiry cleanup.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2, data-stores/relational @3

### SpringSessionBackedSessionRegistry
**Short:** Cluster-wide session registry on Spring Session, replacing the per-JVM one so concurrent-session limits hold.
**Kind:** api
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2

### TTL
**Short:** Time-to-live: the expiry stamped on a cache entry, session or idempotency/dedup key so stale state clears itself.
**Kind:** concept
**Lang:** *
**Roles:** caching/distributed-cache @1, data-access/transactions-and-consistency @2

### Varnish
**Short:** HTTP accelerator: a reverse-proxy cache for HTTP responses, programmed with the VCL configuration language.
**Kind:** tech
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @2

### weigher
**Short:** Caffeine/Guava callback giving each cache entry a cost, so eviction bounds total weight rather than entry count.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, apis-frameworks/design-patterns-and-principles @3
