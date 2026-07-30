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

Data is partitioned across the heap or off-heap memory of every node, and you can send a compute task to the node that already holds the key instead of pulling rows to the client. That colocation of compute with data is the real difference from a plain cache. It also speaks SQL over those caches, supports ACID transactions, and can sit in front of a relational database as a read-through/write-through layer with a native persistence tier so the cluster survives a restart. Consider it when a JVM-heavy system needs an in-memory grid with queries and joins; the price is operational weight, including JVM and GC tuning and rebalancing when the topology changes, that a simpler key-value cache never charges you.

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

It supplies the cache structures the standard library leaves out — `LRUCache`, `LFUCache`, `TTLCache`, `RRCache` — as plain mutable mappings with a size bound, plus a `@cached` decorator that takes any of them and a `cachedmethod` variant for instance methods, where `functools.lru_cache` would otherwise keep `self` alive forever.

`TTLCache` is the usual reason to install it, since `lru_cache` has no expiry and anything that can go stale — a JWKS document, a feature flag, a config lookup — needs one. It is per-process and per-worker, so several workers mean several caches and several misses; once coherence across processes matters, the answer is Redis.

### Caffeine
**Short:** High-performance on-heap Java cache with W-TinyLFU eviction, size/TTL policies and weak/soft key modes.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1

Caffeine is the successor to Guava's cache: a `Cache` or `LoadingCache` you bound with `maximumSize` or `maximumWeight`, expire with `expireAfterWrite` or `expireAfterAccess`, and populate through a loader that de-duplicates concurrent misses on the same key. Its eviction is W-TinyLFU -- a frequency sketch admits a new entry only when it is likely more valuable than the victim it would displace -- which holds a substantially higher hit rate than plain LRU on the skewed access patterns real caches see, and stops a scan from wiping the hot set. `refreshAfterWrite` reloads a stale entry in the background while still serving the old value, and `weakKeys`/`softValues` hand eviction to the GC when a size bound is hard to choose. Use it as an in-process L1 in front of Redis or the database for hot, small, tolerably stale data -- it lives inside one JVM, so every node holds its own copy and its own invalidation problem.

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

You point your domain's DNS at Cloudflare and it becomes a reverse proxy in front of the origin: TLS terminates at the nearest point of presence, cacheable responses are served from there, and volumetric attacks are absorbed before any packet reaches your servers. Cache behaviour follows your `Cache-Control` headers plus cache rules you configure, and Workers run JavaScript or WASM at the edge for rewriting, auth checks, rate limiting or small APIs with no origin round trip at all.

Two consequences worth internalizing. Proxying means Cloudflare terminates your TLS and sees plaintext, which is a trust and compliance decision, not just a performance one. And the origin must be locked down — restricted to Cloudflare's address ranges, or reachable only through a tunnel — because an attacker who discovers its IP simply addresses it directly and every protection you configured is bypassed.

### CloudFront
**Short:** AWS CDN that caches HTTP responses at edge PoPs and terminates TLS, WAF and edge compute there.
**Kind:** tech
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @2, platform-delivery/cloud-platform-and-cost @3

CloudFront is AWS's CDN. You define a distribution with one or more origins — an S3 bucket, a load balancer, any HTTP server — and a cache policy that says which headers, cookies and query strings form the cache key; requests then terminate at the nearest edge location, which serves from cache or fetches once from origin. TLS terminates at the edge with an ACM certificate, and AWS WAF and Shield attach there, so filtering happens before traffic reaches your region.

CloudFront Functions run tiny JavaScript at the viewer edge for header rewrites and redirects, and Lambda@Edge handles heavier per-request logic. Reach for it to cut origin load and round-trip latency on anything cacheable; version your asset paths rather than relying on invalidations, which propagate asynchronously and are billed past a small free allowance.
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

A request is embedded, looked up in a vector store, and if the nearest cached question falls within a similarity threshold the stored response is returned without calling the model -- so paraphrases of the same question hit, which an exact-key cache never does. The pieces are pluggable (embedding model, vector index, cache storage, similarity evaluator) and it ships an adapter that stands in for the OpenAI client, so existing call sites do not change. The threshold is the entire design decision: set it too loose and two genuinely different questions share an answer, which is a correctness bug rather than a cost saving, so it suits FAQ-shaped and support traffic far better than open-ended generation. Reach for it when a large share of prompts are near-duplicates; if you already front models with a gateway, the gateway's own semantic cache is usually the shorter path.

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

Members discover each other and partition entries across the cluster, with each partition backed up on another member, so losing a node loses no data and the map keeps serving. A client can hold a near-cache for read-heavy keys, and entry processors let you run code on the member that owns the key instead of pulling the value across the network. It can be embedded directly in your JVM process or run as a separate cluster.

Reach for it when the application is already JVM, you want a JCache-compatible API, or embedding the grid in the process removes a network hop. If the cache is shared by services in several languages, Redis is the more common answer.

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

The API is deliberately tiny - get, set, add, incr, delete, compare-and-swap, each with a TTL - and memory is managed by a slab allocator with LRU eviction per slab class. There is no replication and no cluster coordination whatsoever: clients pick a node by consistent hashing, so the "cluster" exists only in the client library, and a node that restarts simply comes back empty and refills.

That is the whole proposition, and it is a good one when the workload really is a look-aside cache of opaque blobs in front of a database: fewer moving parts, nothing to fail over, nothing that can lose data because it never claimed to keep any. The moment you want sorted sets, pub/sub, streams, durability, replication or server-side scripting, the answer is Redis instead - and the slab allocator's fixed size classes mean wildly variable value sizes waste memory.

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

Commands execute on a single thread, which is why every command and every Lua script or function is atomic without any locking on your side — the property that makes it the standard distributed rate limiter, idempotency-key store, and lock — and equally why one `KEYS *` or a slow script stalls every other client on the server. The data structures are the second reason to reach for it: a sliding-window limiter is a sorted set, a leaderboard is a ZSET range, a work queue is a stream with consumer groups, and a set-membership check is a set, so behaviour you would otherwise write and test yourself is one command.

Durability is RDB snapshots, an append-only file, or both, and replication is asynchronous, so a failover can lose the most recent writes; Cluster shards by hash slot and multi-key commands must stay within one slot. Reach for it as cache, session and token store, counter, and pub/sub bus, and decide the eviction policy and memory ceiling deliberately — treating it as a system of record means accepting that a crash can lose the tail of your writes.

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

Varnish sits in front of an origin and caches HTTP responses in memory, deciding what happens to each request through VCL, a small C-like language compiled to a shared object at startup: `vcl_recv` normalizes or bypasses, `vcl_backend_response` overrides TTLs the origin got wrong, `vcl_deliver` shapes what the client sees. Grace mode serves slightly stale objects while a fetch refreshes in the background, which is what keeps an origin alive through a spike, and bans or purges invalidate on demand.

It is the tool for a read-heavy site whose pages are largely identical across users — news, catalogs, product listings — where one origin request can serve thousands of hits. It does not terminate TLS itself, so a terminator goes in front of it, and personalized responses need careful cache-key work or they simply never cache.

### weigher
**Short:** Caffeine/Guava callback giving each cache entry a cost, so eviction bounds total weight rather than entry count.
**Kind:** api
**Lang:** java
**Roles:** caching/in-process-cache @1, apis-frameworks/design-patterns-and-principles @3
