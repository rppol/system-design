# Content Delivery Networks (CDN)

## Table of Contents
1. [What is a CDN?](#what-is-a-cdn)
2. [How CDN Works](#how-cdn-works)
3. [CDN Architecture](#cdn-architecture)
4. [Content Delivery Models](#content-delivery-models)
5. [Caching at CDN](#caching-at-cdn)
6. [Geographic Routing](#geographic-routing)
7. [CDN for Dynamic Content](#cdn-for-dynamic-content)
8. [Security](#security)
9. [Real-World Examples](#real-world-examples)
10. [CDN Metrics](#cdn-metrics)
11. [Interview Questions](#interview-questions)
12. [Best Practices](#best-practices)

---

## Intuition

> **One-line analogy**: A CDN is like opening local branch offices instead of making everyone drive to headquarters — users get served from the nearest office, reducing both travel time and HQ load.

**Mental model**: Without a CDN, a user in Tokyo requesting images from a US-hosted website waits for a round-trip across the Pacific (~150ms one-way). With a CDN, the same images are cached at a Tokyo edge server — served in <10ms. The CDN's global network of "Points of Presence" (PoPs) caches static content close to users. When the edge cache misses, it fetches from origin once and caches for all subsequent users in that region.

**Why it matters**: CDNs are essential for any globally used service with static assets (images, CSS, JS, videos). They reduce origin server load (99% of static requests never reach origin), improve latency (serve from 10-50ms instead of 150-300ms), and provide DDoS protection (attacks absorbed at edge, far from origin). Netflix serves 100M+ users with ~800 CDN PoPs.

**Key insight**: Cache invalidation (how quickly edge caches pick up content updates) is the main CDN tradeoff. Short TTLs mean fresh content but more origin traffic; long TTLs mean fewer origin hits but stale content risk. Cache busting (include content hash in URL) solves this for static assets.

---

## What is a CDN?

A Content Delivery Network (CDN) is a globally distributed network of servers (called edge servers or Points of Presence — PoPs) strategically placed in data centers around the world. Its primary purpose is to serve content to users from the closest geographic location, dramatically reducing latency and improving performance.

### Core Problems CDN Solves

**1. Latency from Geographic Distance**
The speed of light is a hard physical limit. A user in Tokyo requesting content from a server in New York experiences ~150ms of round-trip latency just from propagation delay, before any processing time. A CDN places copies of that content in Tokyo, reducing latency to <10ms.

**2. Origin Server Overload**
Without a CDN, every user request hits the origin server. For a popular website with millions of users, this creates massive load. A CDN absorbs 80-99% of requests by serving cached content, acting as a shield for the origin.

**3. Network Congestion**
The public internet has congested backbone routes, especially for transoceanic traffic. CDN providers own or peer with major internet exchange points (IXPs) and have optimized private networks between PoPs, bypassing public internet congestion.

**4. Availability**
CDNs provide redundancy. If one PoP fails, traffic is routed to the next closest. Origin failures can be masked by serving stale content ("stale-while-revalidate").

### What CDNs Serve
- Static assets: images, CSS, JavaScript, fonts, videos
- Software downloads and updates
- Streaming media (VOD and live)
- API responses (with appropriate cache headers)
- Dynamic HTML (with edge computing)

---

## How CDN Works

### Step-by-Step Request Flow

```
1. User types example.com in browser
2. DNS resolver queries for cdn.example.com (or example.com is CNAMEd to CDN)
3. CDN's DNS (Anycast/GeoDNS) returns IP of nearest PoP
4. Browser connects to nearest PoP (edge server)
5a. CACHE HIT: Edge has valid cached response -> serve immediately
5b. CACHE MISS: Edge fetches from origin, caches response, serves to user
6. Subsequent requests from the same region hit the cache (step 5a)
```

### DNS Resolution Detail

```
Browser                    CDN DNS               Origin DNS
  |                            |                      |
  |--DNS: example.com--------->|                      |
  |                            | (GeoDNS looks up      |
  |                            |  user's IP location)  |
  |<--IP: 203.x.x.x (Tokyo PoP)|                      |
  |                            |                      |
  |--HTTP GET /image.jpg (to Tokyo PoP)               |
  |       |                                           |
  |    [Cache Miss?]                                  |
  |       |--GET /image.jpg--------------------------->|
  |       |<--200 OK + image--------------------------|
  |       | (cache the image)                         |
  |<--200 OK + image                                  |
  |    (from Tokyo PoP, 8ms away)                     |
```

---

## CDN Architecture

### Components

**Origin Server**
The authoritative source of content. This is your web server or object storage (S3, GCS). CDN fetches content from origin on cache misses. The origin should only receive a small fraction of total traffic in a well-configured CDN setup.

**Edge Servers / PoPs (Points of Presence)**
Physical servers in data centers distributed globally. Each PoP typically has:
- Multiple servers for redundancy
- Local SSD/NVMe cache storage (terabytes)
- High-bandwidth network connectivity
- BGP peering with local ISPs

**CDN Control Plane**
- Configuration management (cache rules, routing policies, SSL certificates)
- Cache purge/invalidation API
- Analytics and reporting pipeline
- Health monitoring of PoPs

### ASCII Architecture Diagram

```
                          CDN Architecture

    User (New York)                    User (Tokyo)
         |                                  |
         | DNS: nearest PoP?                | DNS: nearest PoP?
         v                                  v
   [NYC Edge PoP]                    [Tokyo Edge PoP]
   +------------+                    +------------+
   | Cache:     |                    | Cache:     |
   | logo.png   |                    | logo.png   |
   | style.css  |                    | style.css  |
   | video.mp4  |                    | video.mp4  |
   +------------+                    +------------+
         |                                  |
         | Cache Miss: fetch from origin    |
         +----------------------------------+
                          |
                          v
                  [CDN Backbone Network]
                  (Private fiber / optimized routes)
                          |
                          v
               +--------------------+
               |   Origin Server    |
               |   (AWS us-east-1)  |
               |                    |
               |   example.com      |
               +--------------------+

   PoPs Worldwide:
   Americas: New York, Los Angeles, São Paulo, Toronto
   Europe:   London, Frankfurt, Amsterdam, Paris
   Asia:     Tokyo, Singapore, Mumbai, Sydney
   (~200-300 PoPs for major CDNs like Cloudflare/Akamai)
```

---

## Content Delivery Models

### Push CDN

In the Push model, the content publisher (you) proactively pushes content to CDN edge servers before any user requests it. Content lives on CDN storage until you delete or update it.

#### How It Works
```
Publisher --> [CDN API / Upload] --> Edge Server 1 (NY)
                                --> Edge Server 2 (Tokyo)
                                --> Edge Server 3 (London)
                                ...all PoPs pre-populated

User Request --> Edge Server (nearest) --> Serve from local storage
                (no origin fetch ever needed)
```

#### When to Use
- Large static files: software installers, game patches, video files
- Content that is known in advance (pre-generated reports, batch-uploaded media)
- Cases where cache-miss latency is unacceptable (first user in a region)
- Low-traffic sites where pull CDN cache might be cold

#### Tradeoffs
| Pros | Cons |
|------|------|
| Zero cache-miss latency — content always ready | Storage cost on CDN (you pay for space) |
| Predictable performance | Must manage content lifecycle (push updates, delete old) |
| Works for low-popularity content | Complexity: need to push to all PoPs |
| Good for time-sensitive releases | Over-provisioning if content is rarely accessed |

---

### Pull CDN

In the Pull model, the CDN fetches content from the origin on the first request (cache miss) and caches it at the edge. Subsequent requests in that region are served from cache. Content expires based on TTL and is re-fetched from origin on the next request after expiry.

#### How It Works
```
First user in Tokyo requests /logo.png:
  User --> [Tokyo PoP] -- MISS --> Origin --> cache response
  User receives content (slightly higher latency)

Second user in Tokyo requests /logo.png:
  User --> [Tokyo PoP] -- HIT --> serve from cache
  (origin not contacted)

After TTL expires:
  Next user --> [Tokyo PoP] -- MISS (stale) --> Origin --> re-cache
```

#### When to Use
- Websites with large catalogs (millions of URLs) where pre-push is impractical
- Content with unpredictable popularity (you don't know what will be requested)
- Dynamic-ish content that changes but not too frequently

#### Tradeoffs
| Pros | Cons |
|------|------|
| No upfront storage cost on CDN | First request to each PoP has cache-miss latency |
| Automatic: CDN handles caching | Cold cache after TTL expiry |
| Works for large content catalogs | Popular content for first users in a region is slow |
| Simple to set up | Origin must handle cache-miss traffic |

---

## Caching at CDN

### TTL and Cache-Control Headers

CDN caching behavior is controlled by HTTP headers sent from the origin server.

```http
# Cache for 1 year (immutable static assets with content-hashed filenames)
Cache-Control: public, max-age=31536000, immutable

# Cache for 10 minutes, serve stale for 1 day while revalidating
Cache-Control: public, max-age=600, stale-while-revalidate=86400

# CDN caches for 1 hour, browser caches for 5 minutes
Cache-Control: public, s-maxage=3600, max-age=300

# Do not cache (private user data)
Cache-Control: private, no-store

# Surrogate-Control (CDN-specific, stripped before sending to browser)
Surrogate-Control: max-age=86400
```

### ETag and Conditional Requests

ETags allow efficient cache revalidation without re-downloading unchanged content:

```
Client/CDN -> Origin: GET /style.css
Origin -> Client: 200 OK, ETag: "abc123", body: [CSS content]

[Later, TTL expires]
CDN -> Origin: GET /style.css, If-None-Match: "abc123"
Origin -> CDN: 304 Not Modified (if unchanged, no body transferred)
           OR: 200 OK, ETag: "def456", body: [new CSS] (if changed)
```

### Cache Invalidation / Purging

When content changes before TTL expires, you need to purge the CDN cache:

**Purge by URL**
```bash
# Cloudflare API example
curl -X POST "https://api.cloudflare.com/client/v4/zones/{zone_id}/purge_cache" \
  -H "Authorization: Bearer {token}" \
  -d '{"files": ["https://example.com/style.css"]}'
```

**Purge by Tag (Cache Tags / Surrogate Keys)**
```http
# Origin response includes:
Cache-Tag: product-123, category-electronics, homepage

# Later, when product 123 updates:
# Purge everything tagged "product-123" across all PoPs
```

**Versioned URLs (Best Practice)**
Instead of purging, use content-hashed filenames:
```
style.abc123.css  (version 1)
style.def456.css  (version 2, after changes)
```
The old file stays cached forever, new deployments use new filenames. No purge needed.

### Cache Key Design

CDN cache keys default to the full URL. You can customize:

```
Default key: https://example.com/api/products?sort=asc&page=2

Normalize query params: sort parameters before hashing
Vary by header: Cache-Control: Vary: Accept-Encoding, Accept-Language
Strip tracking params: utm_source, fbclid shouldn't create separate cache entries
```

---

## Geographic Routing

### Anycast Routing

Anycast assigns the same IP address to multiple servers in different locations. The internet's BGP routing protocol automatically routes traffic to the "nearest" (fewest BGP hops) server with that IP.

```
CDN IP: 104.16.0.1 (announced from every PoP)

User in Tokyo   --> BGP routes to Tokyo PoP   (104.16.0.1 in Tokyo)
User in New York --> BGP routes to NY PoP     (104.16.0.1 in NY)
User in London   --> BGP routes to London PoP (104.16.0.1 in London)

Same IP, different physical servers based on network proximity.
```

Cloudflare uses Anycast for all traffic. Benefits: automatic failover (if a PoP goes down, BGP re-routes), DDoS absorption (attack traffic distributed across all PoPs).

### GeoDNS

DNS-based routing returns different IP addresses (or CNAME targets) based on the resolver's geographic location.

```
DNS Query from Tokyo resolver --> returns: 203.0.113.1 (Tokyo PoP)
DNS Query from NY resolver    --> returns: 198.51.100.1 (NY PoP)
DNS Query from EU resolver    --> returns: 192.0.2.1 (Frankfurt PoP)
```

Limitation: DNS TTL means routing changes take time to propagate. Also, DNS resolver location may not match user location (e.g., 1.1.1.1 resolves from Cloudflare's location, not the user's ISP).

### Latency-Based Routing

More sophisticated than GeoDNS — CDN continuously measures round-trip latency from each PoP to major internet prefixes and routes each user to the PoP with the lowest measured latency, not just geographic proximity.

---

## CDN for Dynamic Content

Static content (images, CSS, JS) is the classic CDN use case. But modern CDNs also accelerate dynamic content.

### Edge Computing

Run code at CDN edge nodes, enabling dynamic content generation without a round trip to origin.

**Cloudflare Workers**
```javascript
// Runs at every Cloudflare PoP worldwide
addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request))
})

async function handleRequest(request) {
  // Personalize at the edge without hitting origin
  const userCountry = request.headers.get('CF-IPCountry')
  const cachedResponse = await caches.default.match(request)

  if (cachedResponse) return cachedResponse

  const response = await fetch(request)
  // Cache for 60 seconds at the edge
  const newResponse = new Response(response.body, response)
  newResponse.headers.set('Cache-Control', 'public, max-age=60')

  event.waitUntil(caches.default.put(request, newResponse.clone()))
  return newResponse
}
```

**AWS Lambda@Edge**
- Functions run at CloudFront edge nodes
- Can modify request/response headers, redirect, A/B test, authenticate
- Latency: ~1ms overhead vs. origin round trip

### Dynamic Caching Strategies

**Micro-caching**: Cache even dynamic content for 1-5 seconds. A page generating 10,000 req/s with 1s micro-caching only hits origin once per second — 9,999x reduction in origin load, with at most 1 second of staleness.

**Fragment Caching (ESI — Edge Side Includes)**
```html
<!-- Serve cached page with dynamic fragment -->
<html>
  <body>
    <esi:include src="/user/header" /> <!-- Dynamic, not cached -->
    <esi:include src="/content/article/123" ttl="300" /> <!-- Cached 5min -->
    <esi:include src="/footer" ttl="3600" /> <!-- Cached 1hr -->
  </body>
</html>
```

### Personalization Challenges

Personalized content (user-specific pages) is the hardest to cache:

- **User-specific content**: Cannot be cached in shared edge cache. Use client-side rendering or inject personalization via JavaScript after serving a cached skeleton.
- **Cookie-based variants**: CDN can create separate cache entries per cookie value, but this fragments the cache badly.
- **Cache by user segment**: Instead of per-user, cache per "segment" (logged-in vs. anonymous, country, language).

---

## Security

### DDoS Protection at Edge

CDN edge servers absorb volumetric DDoS attacks by:
- Having far more bandwidth capacity than any single origin (Cloudflare: 197 Tbps aggregate)
- Distributing attack traffic across all PoPs (Anycast)
- Applying rate limiting at edge before traffic reaches origin
- IP reputation filtering

### SSL/TLS Termination

CDN terminates SSL at the edge server, establishing a separate connection to origin:

```
Browser <--TLS--> Edge PoP <--TLS (or HTTP)--> Origin

Benefits:
- TLS handshake with nearby edge (low latency)
- CDN handles certificate renewal (via Let's Encrypt or custom)
- OCSP stapling, TLS 1.3, HTTP/2 negotiated at edge
- Origin can use simpler HTTP internally (if private network)
```

### WAF (Web Application Firewall)

CDN-level WAF inspects HTTP requests at edge, blocking:
- SQL injection attempts
- XSS payloads
- OWASP Top 10 attacks
- Bad bots (using bot fingerprinting)
- Geographic restrictions (block requests from specific countries)

WAF rules can be: Managed (maintained by CDN vendor), Custom (your own rules), or Rate-based (block IPs with anomalous patterns).

### Signed URLs and Tokens

For private content (paid videos, user documents), restrict access using:

**Signed URLs**
```
# AWS CloudFront signed URL
https://cdn.example.com/video.mp4
  ?Expires=1700010000
  &Signature=AbCdEfGh...
  &Key-Pair-Id=APKAEXAMPLE

# URL is valid only until Expires timestamp
# Signature verifies it was generated by your private key
# Only share URL with the authorized user
```

**Signed Cookies**
For multiple files (e.g., entire video course), set a signed cookie once and all subsequent CDN requests are automatically authorized.

**Token Auth / JWT at Edge**
```javascript
// Cloudflare Worker validates JWT before serving content
async function handleRequest(request) {
  const token = request.headers.get('Authorization')?.split(' ')[1]
  if (!token || !await verifyJWT(token)) {
    return new Response('Unauthorized', { status: 401 })
  }
  return fetch(request)  // forward to origin/cache
}
```

---

## Real-World Examples

### Netflix: Open Connect

Netflix built its own CDN called Open Connect rather than using commercial CDN providers.

- **ISP Partnerships**: Netflix places Open Connect Appliances (OCAs) — custom servers with large NVMe storage — directly inside ISP data centers and internet exchange points
- **Pre-positioning**: Netflix pre-populates popular content during off-peak hours (2-5 AM) so it's ready before users request it
- **Scale**: Open Connect delivers 99%+ of Netflix traffic; at peak, ~700 Gbps per major ISP
- **Why custom?**: Cost at Netflix's scale makes commercial CDN prohibitively expensive; also enables unique optimizations

### Cloudflare vs. AWS CloudFront vs. Akamai

| Feature | Cloudflare | AWS CloudFront | Akamai |
|---------|-----------|----------------|--------|
| PoPs | ~300 cities | ~450 PoPs | ~4,000 PoPs |
| Model | Anycast | GeoDNS-based | GeoDNS-based |
| Edge compute | Workers (V8 isolates) | Lambda@Edge | EdgeWorkers |
| DDoS protection | Best-in-class, free | Separate Shield service | Enterprise tier |
| Pricing | Usage-based + plans | Per-GB + request fees | Enterprise contracts |
| Origin shield | Yes | Origin Shield (extra cost) | Yes |
| Best for | All-in-one, SMB to enterprise | AWS ecosystem integration | Large enterprises, media |

### YouTube: Adaptive Bitrate + CDN

YouTube combines CDN with Adaptive Bitrate Streaming (ABR):
- Videos encoded at multiple quality levels (144p to 4K)
- Player monitors download speed and switches quality in real-time
- CDN caches each quality version separately
- Google's own CDN infrastructure (Google Global Cache) — placed in ISPs
- QUIC protocol (HTTP/3) reduces latency for video streaming

### GitHub: Asset CDN

- Static assets (images, JS bundles) served via Fastly CDN
- Pull CDN model with long TTLs for versioned assets
- Edge caching for GitHub Pages sites
- GitHubs's git protocol traffic goes directly to origin (not cached)

---

## CDN Metrics

### Key Metrics to Track

**Cache Hit Ratio (CHR)**
```
CHR = (Cache Hits) / (Total Requests) * 100%

Good: > 90%
Great: > 95%
Poor: < 70% (investigate cache-busting or low-TTL configs)
```

**Bandwidth Savings**
```
Bandwidth Savings = (CDN bandwidth served) / (Total bandwidth) * 100%

CDN serves: 950 GB
Origin serves: 50 GB
Total: 1000 GB
Bandwidth Savings = 95%
```

**Origin Offload Percentage**
```
Origin Offload = 1 - (Origin requests / Total CDN requests)

If CDN receives 1,000,000 requests and only 50,000 go to origin:
Origin Offload = 1 - (50,000 / 1,000,000) = 95%
```

**Latency by Region**
- Time To First Byte (TTFB) for cached vs. uncached requests
- P50, P95, P99 latency per geographic region
- Cache miss latency (includes origin round-trip)

**Error Rate**
- 5xx errors originating from CDN vs. origin
- Cache poisoning attempts

**Edge Hit vs. Origin Hit Ratio**
- Segment by content type, URL pattern, and geography

---

## Interview Questions

**Q1: What is the difference between a CDN and a regular cache?**

A: A regular cache (like Redis or Varnish) is typically a single centralized server. A CDN is a geographically distributed network of caches. CDN reduces latency by serving content from the edge nearest to the user, whereas a single cache still requires crossing the network to the data center. CDNs also provide redundancy, DDoS protection, and edge computing capabilities.

**Q2: What is the difference between Push CDN and Pull CDN? When would you use each?**

A: Push CDN requires you to proactively upload content to CDN storage; Pull CDN fetches from origin on first request and caches it. Use Push for large static files you know will be requested (game updates, video releases), where first-request performance matters. Use Pull for large content catalogs with unpredictable access patterns, where pre-uploading everything is impractical.

**Q3: How does a CDN handle cache invalidation?**

A: Three main approaches: (1) Wait for TTL expiry — simplest but content can be stale until TTL runs out. (2) API-based purge — call the CDN's purge API to immediately evict specific URLs or cache tags from all PoPs. (3) URL versioning — use content-hashed filenames so new versions have new URLs; old versions expire naturally. URL versioning is the most reliable approach for static assets.

**Q4: How does Anycast routing work in CDN?**

A: Anycast assigns the same IP address to servers in multiple locations. Each CDN PoP announces this IP via BGP. The internet's routing protocol automatically routes packets to the "nearest" BGP node (fewest hops). Users in different regions naturally route to their nearest PoP without DNS lookup. This also provides automatic failover — if a PoP goes down, BGP reconverges and traffic flows to the next nearest PoP.

**Q5: How would you design a CDN architecture for a live streaming platform?**

A: For live streaming: (1) Origin ingest server receives the stream and transcodes it into multiple bitrates. (2) Stream is segmented into small chunks (HLS: .m3u8 manifest + .ts segments, typically 2-6 second chunks). (3) CDN pulls and caches segments with very short TTL (equal to segment duration). (4) Edge caching is shallow — only cache the last N segments since historical segments still get hit. (5) Use CDN with support for chunked streaming to minimize manifest cache lag. (6) Pre-warm edge caches before scheduled events.

**Q6: What is "cache stampede" and how do CDN handle it?**

A: Cache stampede (thundering herd) occurs when a popular cached item expires and thousands of requests simultaneously miss the cache and all race to fetch from origin. CDN solutions: (1) Probabilistic early expiration — each request has a small chance of refreshing before TTL expires, spreading revalidation. (2) Request coalescing — when multiple simultaneous misses occur for the same URL, CDN makes only one request to origin and serves the response to all waiting clients. (3) Stale-while-revalidate — serve stale content immediately while one background request refreshes the cache.

**Q7: How does CDN improve TTFB (Time To First Byte)?**

A: CDN improves TTFB by: (1) Serving from a geographically close PoP — reducing propagation delay from ~150ms transoceanic to <10ms local. (2) TLS termination at the edge — eliminates TLS handshake latency over the WAN. (3) HTTP/2 and HTTP/3 (QUIC) support at edge — multiplexing, 0-RTT resumption. (4) Pre-positioned content — no origin processing delay on cache hits. (5) Persistent connections from edge to origin — avoids TCP handshake overhead for cache misses.

**Q8: What are the tradeoffs of using a CDN for API responses?**

A: Pros: drastically reduces origin load, improves response time for cacheable API responses (search results, product catalogs), natural DDoS protection. Cons: stale data risk if TTL is too long, cache invalidation is complex for mutable resources, user-specific responses cannot be cached in shared edge cache, debugging is harder (need to distinguish CDN vs. origin responses), adds cost per request/GB. Best practice: use CDN for read-heavy, public, cacheable endpoints; bypass CDN for user-specific or write APIs.

**Q9: How would you handle cache poisoning attacks in a CDN?**

A: Cache poisoning occurs when an attacker causes the CDN to cache a malicious response that gets served to all users. Mitigations: (1) Normalize cache keys — strip or normalize query parameters, headers that shouldn't affect cache. (2) Validate responses before caching — don't cache 5xx responses. (3) Use Vary headers correctly — ensure `Vary: Accept-Encoding` doesn't allow different-encoding responses to poison each other. (4) Disable caching for sensitive endpoints. (5) WAF rules to detect and block injection attempts. (6) CDN-level origin verification — only allow known origin IPs.

**Q10: Explain the concept of "Origin Shield" in CDN.**

A: Origin Shield adds an additional caching layer between CDN edge nodes and the origin. Without it, each of 300+ PoPs might independently request a cache miss from origin, creating a large fan-out. With Origin Shield, all PoP cache misses are routed through a single designated shield node, which is the only one that contacts origin. This dramatically reduces origin traffic (especially for low-traffic content) at the cost of slightly higher latency for shield-miss requests. AWS CloudFront calls this "Origin Shield"; Cloudflare calls it "Argo Tiered Caching."

**Q11: How do CDNs support HTTPS and certificate management?**

A: CDN terminates TLS at the edge. Modern CDNs handle: (1) Automatic certificate provisioning via Let's Encrypt (ACME protocol). (2) Certificate renewal before expiry. (3) SNI (Server Name Indication) for hosting multiple domains on shared IP. (4) HSTS preloading, OCSP stapling. (5) Custom certificate upload for Enterprise customers. The connection from CDN edge to origin can be HTTP (if on private network) or HTTPS (end-to-end encryption).

**Q12: What is "stale-while-revalidate" and why is it valuable?**

A: `stale-while-revalidate` is a Cache-Control directive: `Cache-Control: max-age=60, stale-while-revalidate=300`. It means: serve the cached version immediately (even if up to 5 minutes stale) while asynchronously fetching a fresh copy in the background. This eliminates cache-miss latency from the user's perspective — they always get an instant response. The background revalidation updates the cache for the next request. Ideal for content that changes infrequently and where slight staleness is acceptable.

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement CDN**

- **Proxy** — A CDN edge node is a caching Proxy: clients request content from the edge; the edge serves from cache on a hit, or delegates to the origin on a miss and stores the response. Transparent to the client — same URL, different responder.
- **Decorator** — CDN capabilities (Brotli/gzip compression, image optimization, SSL/TLS termination, bot detection) layer as Decorators on top of the base content delivery without modifying origin servers.
- **Strategy** — Cache invalidation strategies (TTL-based expiry, event-driven purge, stale-while-revalidate, surrogate keys) and geographic routing strategies (anycast, latency-based, geolocation-based) are interchangeable Strategy implementations per content type.

---

## Best Practices

### 1. Cache Warm-Up
Before a major launch, traffic spike, or new PoP activation, pre-warm the cache:
- Crawl your most popular URLs to seed the cache
- Use CDN APIs to pre-populate content (Push CDN)
- For video: pre-position files at PoPs serving your target markets before release

### 2. Use Content-Hashed URLs for Static Assets
```
style.a1b2c3.css  (hash of file content)
app.x7y8z9.js
```
Cache these with `max-age=31536000, immutable`. When content changes, the URL changes, so no cache invalidation is ever needed. Old files naturally expire.

### 3. Monitoring and Alerting
Track these dashboards:
- Cache hit ratio by URL pattern and region (alert if CHR drops below threshold)
- Origin error rate (CDN should mask origin errors with stale content)
- Edge latency P99 by region (detect PoP performance degradation)
- Bandwidth cost trends (unusual spikes may indicate abuse or misconfiguration)

### 4. Multi-CDN Strategy
Using multiple CDN providers provides:
- Resilience against CDN outages (Fastly, Cloudflare, and Akamai have all had major outages)
- Ability to route to the best-performing CDN per region
- Negotiating leverage on pricing

Implementation: use DNS-based traffic routing (Route 53, NS1) to distribute between CDNs. Keep the same content on all CDNs (synchronized invalidation is the hard part).

### 5. Set Appropriate Cache-Control Headers
- Immutable versioned assets: `max-age=31536000, immutable`
- Frequently updated pages: `max-age=60, stale-while-revalidate=3600`
- User-specific content: `Cache-Control: private, no-store`
- API responses: `max-age=10, stale-while-revalidate=60` (short TTL, graceful fallback)

### 6. Bypass CDN for Non-Cacheable Requests
Configure CDN to pass through:
- Requests with authentication headers (Authorization, Cookie with session tokens)
- POST/PUT/DELETE requests
- Real-time data endpoints
- WebSocket connections (or configure CDN to support WebSockets)

### 7. Test Cache Behavior
Always verify:
- Cache-Control headers are set correctly on origin responses
- CDN respects your headers (some CDNs override TTLs)
- Cache keys are correct (ensure no over/under-caching)
- Purge operations work as expected
- Edge compute functions behave correctly in all PoP environments

### 8. Use Cache Tags for Targeted Invalidation
Tag related content (all pages using a specific product image, all pages in a category) with surrogate keys. When content changes, purge by tag rather than URL-by-URL.
