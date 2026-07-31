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

### aiocache
**Short:** Async Python caching library with memory, Redis and Memcached backends, pluggable serializers and TTLs.
**Kind:** tech
**Lang:** python
**Roles:** caching/distributed-cache @1, caching/in-process-cache @2

The library gives you one interface -- `SimpleMemoryCache`, `RedisCache`, `MemcachedCache` -- with `get`, `set`, `multi_get` and `delete` as coroutines, so nothing in the request path blocks the event loop. A serializer and an optional compression plugin sit between your objects and the backend, a `@cached` decorator memoizes a coroutine with a TTL and a key builder you supply, and plugins hook every call, which is how hit-rate metrics get collected without wrapping each site.

Reach for it in an asyncio service that should move from a process-local cache in tests to Redis in production by changing configuration rather than code. Two practical points: the default serializer is JSON, so pickling arbitrary objects is an explicit choice with the security consequences that implies, and stampede protection is opt-in -- a lock helper exists but the decorator does not apply it, so a hot key expiring under load still sends every waiting request to the origin.

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

Wrapping a coroutine with `functools.lru_cache` appears to work and is a bug: what gets cached is the coroutine object, which can only be awaited once, so the second hit fails. `alru_cache` caches the future instead and awaits it, so concurrent callers on a cold key share one in-flight computation rather than each starting their own. It keeps `maxsize` and typed-key behaviour and adds an optional TTL and `cache_clear`.

Reach for it whenever an expensive await repeats with the same arguments inside one process -- a token fetch, a JWKS document, a config or metadata lookup. The limits are the ones any in-process cache has: it is per worker, so several workers mean several copies and no coordinated invalidation, and a failed call is not retained, so a broken dependency is retried by every caller rather than failing fast.

### AWS DAX
**Short:** DynamoDB Accelerator: a managed write-through cache in front of DynamoDB giving microsecond read latency.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @3

DAX is a cluster of cache nodes inside your VPC that speaks the DynamoDB API, so you swap the client and leave the calls alone. It keeps two caches: an item cache for `GetItem` and `BatchGetItem`, and a separate query cache for `Query` and `Scan` result sets. Writes go through DAX to the table and update the item cache on success, so the write path stays consistent -- but only for writes that actually go through DAX.

Reach for it for a read-mostly key-value workload where DynamoDB's single-digit millisecond latency is not enough and you would rather not hand-write cache-aside logic. Understand the failure mode: anything writing directly to the table, from another service or a stream consumer, leaves DAX serving stale items until the TTL expires, and the query cache leans on its TTL rather than on write invalidation. It is DynamoDB-only, VPC-only, and billed per node-hour whether or not it is being hit.

### AWS ElastiCache
**Short:** AWS managed Redis/Valkey and Memcached with automatic failover, multi-AZ and cluster mode.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @3, platform-delivery/cloud-platform-and-cost @3

You choose an engine and AWS runs it: node provisioning, patching, backups, parameter groups, CloudWatch metrics, and for Redis or Valkey a replication group with automatic failover to a replica behind an endpoint that follows the primary. Cluster mode shards across several node groups behind a configuration endpoint, and a serverless option removes node sizing entirely. Data stays inside your VPC, with encryption in transit and at rest and IAM or AUTH-based access control.

Reach for it when you want Redis or Memcached without owning failover, backups and version upgrades, which is most teams. The tradeoffs are the usual managed-service ones: no shell on the box, a restricted set of tunable parameters, no modules you did not get from AWS, and a premium over the same instances run yourself. It is still a cache, so plan for a cold cluster after a failover or a maintenance replacement.

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

One decorator layer covers patterns you would otherwise assemble by hand: `@cache` with a TTL, `@cache.early` which refreshes in the background before expiry so nobody waits on a cold key, `@cache.hit` which recomputes after a hit count, plus rate-limit and circuit-breaker decorators. Keys are templated from the function arguments, tags group related keys so a whole family is invalidated at once, and locking is built in so one worker recomputes while the rest wait.

Reach for it in an asyncio service that wants those behaviours without writing them, with the backend chosen by a URL so memory in tests and Redis in production is a configuration change. The cost is a large, opinionated surface: the decorators do a great deal implicitly, and debugging a wrong key template or an unexpected background refresh means understanding what the library decided for you. For plain memoization something smaller is easier to reason about.

### CDN
**Short:** Content delivery network: geographically distributed edge caches serving static and cacheable dynamic HTTP.
**Kind:** concept
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @3

The mechanics are DNS or anycast steering a client to a nearby point of presence, which either serves the object from its local cache or fetches it once from the origin, often through a mid-tier so a thousand edges do not each become a separate origin request. What is cacheable and for how long is negotiated through `Cache-Control`, `ETag` and `Vary`, and the cache key is whichever request attributes the CDN is configured to include.

Two wins, and the second is the bigger one: latency falls because bytes travel a shorter distance, and origin load collapses because one fetch serves many hits. Reach for it for anything static, for cacheable dynamic responses, and for TLS termination and attack absorption at the edge. The hard part is invalidation, which is why versioned or content-hashed URLs beat purging; personalized or authenticated responses need a deliberate cache-key design or they simply never cache.

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

The name is the problem it solves. When a popular key expires, every concurrent request misses at once and stampedes the origin. This library puts a mutex around regeneration so exactly one caller recomputes while the others keep receiving the previous, now-expired value until the new one is ready, which keeps load flat instead of spiking. Regions bundle a backend, a default expiry and a key generator, and `@region.cache_on_arguments` decorates a function.

Reach for it in a synchronous Python service where the cached computation is expensive enough that a stampede would hurt -- an aggregate query, a rendered page, an external API call. Backends cover memory, Memcached, Redis and files, and the mutex has to be one the backend can provide if the lock must span processes. It is not asyncio-native, which is the main reason a modern async codebase reaches elsewhere.

### Ehcache
**Short:** JVM in-process cache with JCache (JSR-107) compatibility, tiered heap/offheap/disk storage and TTL eviction.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1

Its distinguishing feature is tiering: an entry lives on the heap, and as it ages or the tier fills it moves to an off-heap byte buffer outside the garbage collector's reach and then to disk, with the hottest data always in the fastest tier. That off-heap tier is the point -- a multi-gigabyte cache inside the JVM without the pauses a heap of that size would cause. Configuration is XML or a builder API, and it implements JSR-107 so code written against `javax.cache` stays portable.

Reach for it in a JVM application that needs a large local cache with disk persistence across restarts, or where a JCache-standard API is a requirement. For a purely on-heap cache Caffeine is faster and its admission policy holds a better hit rate; and clustering here needs a separate Terracotta server, which is a heavier answer than pointing several instances at Redis.

### fastapi-cache2
**Short:** Decorator-based response cache for FastAPI endpoints with pluggable Redis or in-memory backends.
**Kind:** tech
**Lang:** python
**Roles:** caching/distributed-cache @1, caching/in-process-cache @2, apis-frameworks/aop-middleware-and-scheduling @3

Initialized once at startup with a backend, it gives you a `@cache` decorator for a path operation: the response is serialized under a key built from the request, and a hit returns before your handler runs. It sets `ETag` and `Cache-Control` on the way out and answers a conditional request with a `304`, so the browser and any intermediary participate too. The key builder, the coder and the expiry are all replaceable.

Reach for it on read-heavy GET endpoints whose responses are the same for many users -- reference data, listings, aggregates. The thing to get right is the key: by default it derives from the path and query, so anything that varies the response and is not in the key, an auth header or a tenant id, will serve one user's data to another. Invalidation is a manual clear, so keep TTLs short.

### Fastly
**Short:** CDN and edge cloud: cacheing PoPs with instant purge, TLS termination, WAF/DDoS protection and edge compute.
**Kind:** tech
**Lang:** *
**Roles:** caching/http-and-cdn-cache @1, traffic-edge/proxy-and-load-balancer @2, traffic-edge/rate-limiting-and-resilience @3, platform-delivery/cloud-platform-and-cost @3

Its edge is built on Varnish, which is why the configuration language is VCL and why you get unusually direct control over the cache: you write the request and response logic that runs at the PoP rather than filling in a form. Two features follow from that lineage and define the product -- purge that propagates globally in well under a second, and surrogate keys, which tag a response with identifiers so every object touching one article or product is invalidated by a single call.

That combination is what lets you cache genuinely dynamic content: set a long TTL and purge on change rather than guessing a short one. Reach for it for news, commerce and API responses that change unpredictably but are read constantly. It is a developer-facing product with a matching learning curve, since VCL is a real language with a real deployment cycle, and its Compute platform runs WebAssembly at the edge for logic VCL cannot express.

### functools.cache
**Short:** Python's unbounded memoisation decorator: `lru_cache(maxsize=None)` under a clearer name, added in 3.9.
**Kind:** api
**Lang:** python
**Roles:** caching/in-process-cache @1

It is a thin alias for `lru_cache` with the eviction machinery switched off, which is also why it is faster than a bounded `lru_cache` - there is no recency bookkeeping on a hit, just a dict lookup keyed on the arguments. Those arguments must be hashable, and the cache is per-process and per-function, so it does nothing for a second worker.

Reach for it for pure functions over a bounded input domain - parsing a config, a recursive computation, resolving a lookup that never changes. Unbounded is the trap: on unbounded inputs it is a memory leak with a decorator on it, and anything whose result can go stale wants an explicit TTL cache instead.

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

Built through `CacheBuilder`, it is a concurrent map with eviction: bound it by `maximumSize` or a weigher, expire with `expireAfterWrite` or `expireAfterAccess`, refresh with `refreshAfterWrite`, and supply a `CacheLoader` so a miss loads once while concurrent callers on the same key wait rather than duplicating the work. Segmented locking gives it map-like concurrency, and eviction is per-segment LRU evaluated during ordinary reads and writes rather than by a background thread.

Reach for it when Guava is already on the classpath and the cache is small and uncontended. Otherwise Caffeine is the answer: it is the successor by the same author, keeps the API almost unchanged, and its W-TinyLFU admission policy holds a materially higher hit rate than segmented LRU while also behaving better under contention. Guava's own documentation points there, so new code has little reason to start here.

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

A prefix that has already been processed can be skipped on a later request, but only while its KV tensors are still in GPU memory -- which they are not once the blocks are evicted or the request lands on a different replica. LMCache moves those tensors into a hierarchy behind the GPU: CPU RAM, local disk, or a shared store other instances can read, and loads them back when a matching prefix arrives. Compression and a fast transfer path exist because moving KV bytes is the whole cost.

The win is on workloads with long, repeated prefixes -- a large system prompt, a retrieved document set, a conversation returning after a gap -- where it turns a full prefill into a load and cuts time to first token sharply. It is worth nothing when prompts are short or genuinely unique, since you pay the transfer either way, and the shared tier adds real infrastructure to operate.

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

Decorating a method with `functools.lru_cache` puts `self` in the cache key, so the cache is shared across all instances and holds a strong reference to every object it has ever seen: the instance can never be collected and a long-lived process leaks. `methodtools.lru_cache` gives each instance its own cache, stored on the instance and referenced weakly, so entries die when the object does. It works on properties and classmethods too.

Reach for it when memoizing per-instance computed state on objects that come and go -- request-scoped services, model wrappers, parsed configuration. The plain alternatives are `functools.cached_property`, which caches one value with no bound and no TTL, or an explicit dict on the instance, which is a few lines and makes the lifetime obvious. Choose the library when you want LRU bounds and decorator syntax without writing that plumbing.

### OpenAI Prompt Caching
**Short:** OpenAI's automatic reuse of a repeated prompt prefix across requests, cutting input token cost and time to first token.
**Kind:** tech
**Lang:** *
**Roles:** caching/semantic-and-llm-cache @1, platform-delivery/cloud-platform-and-cost @3

There is nothing to enable. Requests are matched on their leading tokens, and a prefix seen recently and longer than a minimum length is served from cached state rather than recomputed, with the response reporting how many input tokens hit. Because the match is on an exact prefix, everything static -- system prompt, tool definitions, few-shot examples, retrieved documents that do not change -- must come before anything that varies per request, or the prefix diverges on the first differing token and nothing hits.

The gain is a discount on cached input tokens and a shorter time to first token, which matters most for long-context agents and tool loops resending a large preamble every turn. Entries expire after a period of inactivity, so a low-traffic endpoint may never hit. Order the prompt deliberately and read the usage fields rather than assuming: a timestamp or request id accidentally placed near the top silently costs the entire saving.

### Prompt caching dashboard
**Short:** Console view of prompt-cache hit rate and savings, driven by the usage.cache_* fields on each response.
**Kind:** tech
**Lang:** *
**Roles:** caching/semantic-and-llm-cache @1, observability/tracing-apm-and-llm-observability @2, platform-delivery/cloud-platform-and-cost @3

### Reactive Redis
**Short:** Spring Data Redis's non-blocking template over the Netty-based Lettuce driver, for WebFlux applications.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/drivers-and-connection-pooling @2, runtime-systems/concurrency-and-async @3

`ReactiveRedisTemplate` exposes the same operations as the blocking template but returns `Mono` and `Flux`, and it works only over Lettuce, whose Netty event loop multiplexes many in-flight commands over a single connection instead of borrowing one per call from a pool. That is the actual difference: a WebFlux handler no longer parks a thread waiting on Redis, so a small event-loop pool carries far more concurrent Redis-backed requests.

Reach for it when the application is already reactive end to end. If any part of the chain blocks -- a JDBC call, a synchronous client, a `block()` on the template -- you have taken on the complexity and kept the thread-per-request cost, which is the usual way this goes wrong. Note too that blocking commands and long Lua scripts still stall the Redis server for every client, and a reactive driver does nothing about that.

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

Every key hashes to one of 16,384 slots and each primary owns a range of them; the client caches the slot-to-node map and is redirected with `MOVED` when it is wrong, or `ASK` while a slot is migrating. Nodes gossip over a second port to detect failure and promote a replica by agreement, which is why there is no separate sentinel process. Multi-key commands must land in one slot, and hash tags in braces are how you force related keys together.

Reach for it when a dataset or a write rate genuinely exceeds one node -- not for availability alone, which replication and failover already provide. The costs are real: a cluster-aware client, no cross-slot `MGET` or Lua across shards, resharding as an operational task, and asynchronous replication that can lose recent writes on failover. Vertical scaling of a single primary with replicas stays simpler for as long as it fits.

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

It rests on the observation that the tokens a model attends to are stable: one that mattered at an earlier decoding step tends to keep mattering, and one that was ignored tends to stay ignored. A running record of attention received therefore identifies a persistent set of important tokens, and the KV entries of everything outside it can be dropped, holding the cache at a fixed budget however long generation runs instead of growing with every token.

The point is memory: the KV cache, not the weights, is what caps batch size and context length in long-form decoding, and a bounded cache raises both. The cost is that eviction is irreversible -- a discarded token can never be attended to again, so a later question about it silently gets a worse answer -- which suits long generation over a stable context better than retrieval-heavy prompts. Treat it as a reference implementation of the idea; production engines ship their own variants.

### Spring Cache
**Short:** Spring's declarative caching abstraction: @Cacheable/@CacheEvict proxies over Caffeine, Redis or any CacheManager.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2, apis-frameworks/aop-middleware-and-scheduling @2

It is an abstraction, not a cache: `@Cacheable` proxies the method so a hit returns without invoking it, `@CachePut` always invokes and stores, `@CacheEvict` removes entries or clears a region, and `@Caching` combines them. Keys come from a `KeyGenerator` or a SpEL expression over the arguments, and the storage is whatever `CacheManager` bean is present -- Caffeine, Redis, Hazelcast, a plain map. Setting `sync = true` collapses concurrent misses on one key.

Reach for it to keep caching out of business logic and to change backend without touching a method. The trap is that it is proxy-based, so a call from one method of a bean to another bypasses the proxy entirely and nothing is cached, exactly the self-invocation problem `@Transactional` has. Decide null handling and TTLs per cache name deliberately too, since the defaults cache nulls and never expire.

### Spring Cache Abstraction
**Short:** Spring's @Cacheable/@CacheEvict annotations with AOP interception over a pluggable Caffeine or Redis backend.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2, apis-frameworks/aop-middleware-and-scheduling @3

The mechanism is AOP: a `CacheInterceptor` sits in the proxy chain around any bean method carrying a cache annotation, computes the key, consults the `Cache` obtained from the `CacheManager`, and either short-circuits with the stored value or calls through and stores what comes back. Because the interceptor talks only to the `Cache` interface, the same annotated code runs over an in-process Caffeine map in one profile and a Redis cluster in another.

What the abstraction cannot give you is what the backends do not share: TTL, eviction policy, serialization and cluster behaviour are all configured on the `CacheManager`, so backend-agnostic code still means backend-specific configuration. Reach for it for declarative caching of expensive reads. Once the caching logic itself turns conditional or multi-level, calling a cache API directly is clearer than stacking SpEL conditions onto annotations.

### Spring Data Redis
**Short:** Spring module wrapping Lettuce/Jedis with RedisTemplate, repositories, serializers and reactive operations.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/orm-and-data-mapping @2, data-access/drivers-and-connection-pooling @3

It sits over the Lettuce or Jedis driver and gives you `RedisTemplate` with typed operation views -- `opsForValue`, `opsForHash`, `opsForList`, `opsForSet`, `opsForZSet`, `opsForStream` -- plus connection handling and exception translation into Spring's `DataAccessException` hierarchy. Above that sit `@RedisHash` repositories mapping an object into a hash with secondary indexes, a listener container for pub/sub, and `RedisCacheManager` so the same connection backs `@Cacheable`.

Reach for it in any Spring application talking to Redis, which is close to automatic given the Boot starter. The decision that actually matters is serialization: the default is JDK serialization, which produces unreadable, Java-only, version-fragile bytes, so configure `StringRedisSerializer` for keys and a JSON serializer for values before anything reaches production. Prefer Lettuce as well, which is the default and is thread-safe over a single shared connection.

### Spring Data Redis 3.x
**Short:** Spring's Redis integration: RedisTemplate, reactive template, @RedisHash repositories and pub/sub listeners.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/orm-and-data-mapping @2, data-movement/message-broker @3

The same module named by major version, because the 3.x line is the one aligned with Spring Framework 6 and Jakarta EE and therefore what a current Spring Boot 3 application uses. The surface is `RedisTemplate` and its reactive counterpart, `@RedisHash` repositories over Redis hashes with secondary indexes the framework maintains for you, a `RedisMessageListenerContainer` for pub/sub, and stream operations with consumer groups.

Version matters here mostly for the migration: package names moved to `jakarta`, long-deprecated methods went away, and the driver defaults settled on Lettuce. Reach for it as the standard Redis access layer in a Boot 3 service, and treat the repository abstraction with some care -- `@RedisHash` maintains index keys and expirations on your behalf, and objects deleted outside the repository leave those index entries orphaned.

### Spring Session
**Short:** Moves HTTP session state into Redis or a database so any instance can serve a request without sticky sessions.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2

It replaces the container's session at the filter level: a `SessionRepositoryFilter` wraps the request so `getSession()` returns a session backed by a `SessionRepository` rather than by Tomcat's in-memory map. Your code is unchanged, the session id travels in a cookie or a header, and expiry is enforced by the repository. Because the state now lives outside the JVM, any instance can serve any request, and a deploy or a crash no longer logs everybody out.

Reach for it the moment you run more than one instance, since the alternative is sticky sessions, which unbalance traffic and still lose state when a node dies. The costs are a network round trip on session access and serialization of everything you put in there -- which is the discipline it forces: keep sessions small, store identifiers rather than object graphs, and remember a class change can break sessions written by the previous version.

### spring-boot-starter-cache
**Short:** Spring Boot starter auto-configuring a CacheManager, with the backend chosen by classpath and spring.cache.type.
**Kind:** tech
**Lang:** java
**Roles:** caching/in-process-cache @1, caching/distributed-cache @2, apis-frameworks/dependency-injection-and-config @2

Adding the starter pulls in the caching abstraction and switches on auto-configuration, which picks a `CacheManager` by inspecting the classpath in a fixed order -- a provider you defined yourself first, then JCache, Hazelcast, Couchbase, Redis, Caffeine, and finally a simple `ConcurrentHashMap` manager if nothing else is present. `spring.cache.type` overrides that choice, `spring.cache.cache-names` pre-creates regions, and `@EnableCaching` is still required on a configuration class.

Reach for it so that adding Caffeine or Redis to the build is the whole configuration change. The failure it produces is quiet: with no cache library on the classpath you get the simple in-memory manager, which works in tests and then gives every production instance its own unsynchronized copy. Pin `spring.cache.type` explicitly and configure TTLs per cache name, because the simple manager has neither expiry nor a size bound.

### spring-boot-starter-data-redis
**Short:** Spring Boot starter auto-configuring a Lettuce Redis connection and a RedisCacheManager behind @Cacheable.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-access/drivers-and-connection-pooling @2, apis-frameworks/dependency-injection-and-config @3

The starter brings in Spring Data Redis and Lettuce and auto-configures a `LettuceConnectionFactory` from `spring.data.redis.*` properties -- host, port, credentials, database, SSL, timeouts, and standalone, sentinel or cluster topology -- along with a `RedisTemplate` and a `StringRedisTemplate`. When the cache starter is present as well, it configures a `RedisCacheManager` so `@Cacheable` writes to Redis with no further code.

Reach for it as the default way to talk to Redis from Boot. Two things to configure rather than inherit: the connection pool, since `commons-pool2` must be on the classpath and pooling enabled before the pool properties do anything, and serialization, because the default JDK serializer for cache values produces opaque bytes nothing outside the JVM can read and that break when a class changes. Set a per-cache TTL too; the default is no expiry at all.

### spring-session-core
**Short:** Store-agnostic Session and SessionRepository abstractions plus the filter that replaces HttpSession.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2, apis-frameworks/aop-middleware-and-scheduling @3

This is the store-agnostic half of Spring Session: the `Session` and `SessionRepository` interfaces, the `SessionRepositoryFilter` that substitutes the container's session, the `HttpSessionIdResolver` implementations choosing between a cookie and a header for the id, and the events published when a session is created, expires or is deleted. It carries no storage of its own beyond a map implementation used in tests.

You depend on it directly when writing a custom `SessionRepository` -- over a store none of the shipped modules cover, or with a schema you control -- or when only the filter and id-resolution behaviour are wanted. For header-based ids in a single-page or mobile client, `HeaderHttpSessionIdResolver` is the piece to know. For an ordinary application take the Redis or JDBC module, which depends on this and configures it for you.

### spring-session-data-mongodb
**Short:** Spring Session backend storing HTTP sessions in MongoDB with a TTL index for expiry, shared across instances.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, data-stores/document @3, security/authentication-and-identity @3

Sessions are stored as documents in a collection with the attributes serialized into the document, by default as a binary field written with JDK serialization, or as readable subdocuments if you configure the JSON converter. Expiry is delegated to MongoDB itself: a TTL index on the expiry field lets the server's background task remove expired sessions, so there is no cleanup job in the application.

Reach for it when MongoDB is already the datastore and adding Redis purely for sessions is not worth the operational surface. Two consequences follow. The TTL monitor runs periodically rather than instantly, so an expired session can linger briefly and must be judged invalid by its expiry timestamp rather than by its presence. And every session read and write is a round trip against your primary datastore, load that Redis would have absorbed.

### spring-session-data-redis
**Short:** Backs HttpSession with Redis so any instance can serve any request; removes sticky sessions from horizontal scaling.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2

Each session becomes a Redis hash plus supporting keys, and enabling it is one annotation or a Boot property. Expiry is deliberately belt-and-braces: the hash carries a native TTL so it disappears on its own, and a separate sorted set of expiry times is polled by a background task so the session-destroyed event actually fires -- Redis key expiration is lazy and would not otherwise notify anything reliably. The indexed variant adds a secondary index so sessions can be found by principal.

Reach for it as the default clustered session store: reads are sub-millisecond, expiry is native, and it removes sticky sessions from the load balancer. Get the serializer right, since the JDK default makes session data unreadable and brittle across deployments, and configure `notify-keyspace-events` if you depend on session events. Remember the sessions are only as durable as the Redis instance holding them.

### spring-session-hazelcast
**Short:** Spring Session backend storing sessions in a distributed Hazelcast IMap.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1

The repository stores each session as an entry in a distributed `IMap`, so sessions are partitioned across the Hazelcast cluster with a backup copy on another member and survive the loss of a node. Expiry uses a map entry TTL together with a listener that turns eviction into the session-destroyed event, and an entry processor updates a single attribute in place on the member owning the partition rather than reading and rewriting the whole session across the network.

Reach for it when Hazelcast is already the clustering layer, particularly embedded in the same JVMs, where a session read may be served from the local partition or a near-cache with no network hop at all. Against that, a session store is a modest job for an in-memory data grid: if Hazelcast is not already present Redis is far less to run, and embedding the grid couples your application's memory and cluster membership to it.

### spring-session-jdbc
**Short:** Spring Session store keeping HTTP sessions in SPRING_SESSION tables, with scheduled expiry cleanup.
**Kind:** tech
**Lang:** java
**Roles:** caching/distributed-cache @1, security/authentication-and-identity @2, data-stores/relational @3

Two tables do the work: `SPRING_SESSION` holds one row per session with its id, creation and last-access times and expiry, and `SPRING_SESSION_ATTRIBUTES` holds one row per attribute with the value as a serialized blob. Boot can create both from the bundled DDL for your dialect. Nothing expires by itself, so a scheduled task deletes rows past their expiry time on a fixed interval.

Reach for it when a relational database is already there and standing up another service for sessions is not justified -- it is the least-infrastructure clustered option, and it is transactional and backed up alongside everything else. The cost is that every request touches the database, and session writes are frequent because the last-access time changes on each one, so this competes with your real queries for connections. Past modest traffic, Redis is the right move.

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

An expiry attached to a stored value, after which it is treated as absent -- implemented either by deleting it on a timer or, more commonly, by stamping an expiry time and discarding the value lazily when something next reads it. That lazy form is why an expired key can still occupy memory, and why a store that must notify on expiry needs a separate sweep. It is the mechanism behind cache freshness, session timeout, idempotency windows and dedup markers.

It is the pragmatic answer to cache invalidation: rather than proving when data changed, you bound how stale it may be, and the number you pick trades freshness directly against load on the origin. Two things bite. Many keys written together expire together and stampede the origin at the same moment, which is what jitter prevents. And an expiry alone cannot express correctness -- where a read must not be stale, explicit invalidation on write is the requirement.

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
