# HTTP Protocols

## 1. Concept Overview

HTTP (Hypertext Transfer Protocol) is the application-layer protocol underpinning the web and virtually all backend API communication. Understanding how HTTP has evolved from HTTP/1.0 through HTTP/2 and HTTP/3 — and how TLS secures it — is essential for backend engineers designing APIs, configuring load balancers, debugging latency issues, and making informed infrastructure decisions.

HTTP/1.1 added persistent connections and chunked transfer but remained fundamentally serial. HTTP/2 multiplexed requests over a single TCP connection, compressed headers, and introduced server push. HTTP/3 moved to QUIC, eliminating TCP's head-of-line blocking. TLS evolved from SSL 3.0 through TLS 1.3, with the 1.3 handshake reducing latency by one full round trip.

---

## 2. Intuition

> **One-line analogy**: HTTP/1.1 is one checkout lane per customer — efficient, but sequential. HTTP/2 is like a modern bank with one customer representative and multiple service windows simultaneously active. HTTP/3 is like that same bank, but now operating by radio communication so the representative can handle customers even while moving between offices.

**Mental model**: Each HTTP version solves a specific bottleneck of the previous version. HTTP/1.1 solved the overhead of new TCP connections per request. HTTP/2 solved the need for multiple parallel TCP connections (which browsers opened up to 6 per domain). HTTP/3 solved TCP's head-of-line blocking when packets are lost. Each improvement reflects a real performance bottleneck observed at web scale.

**Why it matters**: Most backend performance issues involve HTTP semantics: missing cache headers causing redundant requests, HTTP/2 header compression reducing bandwidth, TLS 1.2 vs 1.3 adding an extra RTT, or missing keep-alive causing thousands of new TCP connections under load. Getting HTTP right is table stakes for senior backend engineers.

**Key insight**: HTTP/2 over a lossy connection can perform worse than HTTP/1.1 with multiple connections. By multiplexing all traffic into one TCP stream, a single lost TCP segment stalls all HTTP/2 requests. HTTP/3's move to QUIC solves this fundamental architectural problem.

---

## 3. Core Principles

- **Request-response**: HTTP is fundamentally request-response, though HTTP/2 enables concurrent requests on one connection and HTTP/3 stream independence eliminates HoL blocking.
- **Stateless**: Each request contains all information needed to process it. Sessions are implemented via cookies or tokens — not TCP connection state.
- **Header-driven semantics**: Content-Type, Accept, Cache-Control, Authorization — HTTP behavior is controlled by headers, not protocol version (mostly).
- **Caching**: HTTP caching (ETag, Cache-Control, Vary) can eliminate server load for identical requests. A well-cached API can serve 90% of requests from cache.
- **TLS layering**: HTTPS is HTTP over TLS. TLS provides authentication (certificate), confidentiality (encryption), and integrity (MAC). ALPN negotiates the HTTP version during TLS handshake.

---

## 4. Types / Architectures / Strategies

### 4.1 HTTP Version Comparison

| Feature | HTTP/1.0 | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---------|---------|---------|--------|--------|
| Persistent connections | No | Yes (default) | Yes (required) | Yes (QUIC) |
| Multiplexing | No | No (pipelining, rarely used) | Yes (streams) | Yes (QUIC streams) |
| Header compression | No | No | HPACK | QPACK |
| Server push | No | No | Yes | Yes (rarely used) |
| Transport | TCP | TCP | TCP | QUIC (UDP) |
| HoL blocking | Per connection | Per connection | At TCP level | None |
| TLS | Optional | Optional | Practical requirement | Mandatory |
| Binary framing | No | No | Yes | Yes |

### 4.2 TLS Version Comparison

| TLS Version | Status | Handshake RTTs | Notes |
|-------------|--------|---------------|-------|
| SSL 3.0 | Deprecated (POODLE) | 2 RTTs | Broken, never use |
| TLS 1.0 | Deprecated (PCI DSS) | 2 RTTs | RC4, BEAST vulnerable |
| TLS 1.1 | Deprecated (RFC 8996) | 2 RTTs | Removed 2021 |
| TLS 1.2 | Still widely used | 2 RTTs (or 1 with session resumption) | AES-GCM, ChaCha20 |
| TLS 1.3 | Current standard | 1 RTT (0-RTT on resumption) | Forward secrecy required |

---

## 5. Architecture Diagrams

### HTTP/1.1 vs HTTP/2 Multiplexing

```
HTTP/1.1 (6 parallel connections per browser):
  Conn 1: [Req1]---[Resp1]---[Req4]---[Resp4]
  Conn 2: [Req2]---[Resp2]---[Req5]---[Resp5]
  Conn 3: [Req3]---[Resp3]---[Req6]---[Resp6]
  Wasted: head-of-line per connection; 6 TCP handshakes; 6 TLS handshakes

HTTP/2 (1 connection, many streams):
  Connection: -------------------------------------------->
  Stream 1:   [Req1 H]----[Req1 D]     [Resp1 H][Resp1 D]
  Stream 2:       [Req2 H][Req2 D] [Resp2 H][Resp2 D]
  Stream 3:           [Req3 H][Req3 D]     [Resp3 H][Resp3 D]
  (H=HEADERS frame, D=DATA frame; all interleaved on one TCP)
  1 TCP handshake, 1 TLS handshake, better congestion window ramp-up
```

### TLS 1.2 vs TLS 1.3 Handshake

```
TLS 1.2 (2 RTTs):
  Client                    Server
    |-- ClientHello -------->|  RTT 1
    |<-- ServerHello --------|
    |<-- Certificate --------|
    |<-- ServerKeyExchange --|
    |<-- ServerHelloDone ----|
    |-- ClientKeyExchange -->|  RTT 2
    |-- ChangeCipherSpec --->|
    |-- Finished ----------->|
    |<-- ChangeCipherSpec ---|
    |<-- Finished -----------|
    |==== Application Data ==|  Data after 2 RTTs

TLS 1.3 (1 RTT):
  Client                    Server
    |-- ClientHello -------->|  RTT 1
    |   (+ key_share,        |
    |     supported_versions)|
    |<-- ServerHello --------|
    |<-- EncryptedExtensions-|
    |<-- Certificate --------|
    |<-- CertificateVerify --|
    |<-- Finished -----------|
    |-- Finished ----------->|
    |==== Application Data ==|  Data after 1 RTT

TLS 1.3 Session Resumption (0-RTT):
    |-- ClientHello -------->|
    |   (+ early_data,       |
    |     session ticket)    |
    |==== 0-RTT AppData ====>|  Data in first packet (replay risk)
    |<-- ServerHello --------|
    |<-- Finished -----------|
    |-- Finished ----------->|
```

### HPACK Header Compression (HTTP/2)

```
HTTP/1.1 headers (sent every request, uncompressed):
  GET /api/users HTTP/1.1
  Host: api.example.com
  Accept: application/json
  Authorization: Bearer eyJhbGciOiJSUzI1...
  Content-Type: application/json
  User-Agent: MyApp/1.0
  (headers: 400-800 bytes per request)

HTTP/2 HPACK:
  Static table: 61 predefined header name/value pairs
    Index 2: :method: GET
    Index 7: :scheme: https
    Index 1: :authority (name only)
    ...

  Dynamic table: per-connection growing table of recently sent headers
    First request: send all headers, add to dynamic table
    Subsequent requests: send only changed headers as index references
    Authorization header: send once, then reference by index (2 bytes)

Result: requests 2-N send 30-50 bytes instead of 400-800 bytes
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 HTTP/2 Frame Types

HTTP/2 is a binary framing protocol. All HTTP/2 frames have a 9-byte header:
- Length: 24 bits (max 16 MB per frame; default max 16 KB enforced by SETTINGS)
- Type: 8 bits (DATA, HEADERS, PRIORITY, RST_STREAM, SETTINGS, PUSH_PROMISE, PING, GOAWAY, WINDOW_UPDATE, CONTINUATION)
- Flags: 8 bits
- Stream Identifier: 31 bits (0 = connection-level)

```
HTTP/2 Frame:
  +-----------------------------------------------+
  |                Length (24)                    |
  +---------------+---------------+---------------+
  |   Type (8)    |   Flags (8)   |
  +-+-------------+---------------+-------------------------------+
  |R|                 Stream Identifier (31)                      |
  +=+=============================================================+
  |                   Frame Payload (0...)                      ...
  +---------------------------------------------------------------+
```

Key frames:
- **HEADERS**: carries HTTP headers (request line + headers, HPACK compressed)
- **DATA**: carries request/response body
- **SETTINGS**: negotiates connection parameters (max concurrent streams, initial window, max header list size)
- **WINDOW_UPDATE**: flow control — increases available window
- **RST_STREAM**: aborts a stream without closing connection
- **GOAWAY**: graceful shutdown — in-flight streams can complete; no new streams accepted

### 6.2 HTTP Caching Headers

```http
# Server response with caching directives:
HTTP/1.1 200 OK
Cache-Control: max-age=3600, public
ETag: "abc123xyz"
Last-Modified: Thu, 01 Jan 2026 00:00:00 GMT
Vary: Accept-Encoding, Accept-Language

# Cache-Control directives:
# max-age=N      : cache for N seconds
# public         : cacheable by CDNs/proxies
# private        : cacheable only by browser (not CDN)
# no-cache       : must revalidate with origin before using
# no-store       : must not cache at all
# immutable      : resource will never change (for versioned assets)
# s-maxage=N     : CDN cache duration (overrides max-age for shared caches)
# stale-while-revalidate=N : serve stale while revalidating in background

# Conditional request (ETag-based revalidation):
GET /api/users/123 HTTP/1.1
If-None-Match: "abc123xyz"

# Response if unchanged:
HTTP/1.1 304 Not Modified
ETag: "abc123xyz"
# No body — saves bandwidth

# Vary header:
# Tells caches to store separate responses for different header values
Vary: Accept-Encoding
# Browser requesting gzip gets a different cache entry than one requesting br
```

### 6.3 ALPN and SNI

ALPN (Application-Layer Protocol Negotiation) is a TLS extension that allows the client to advertise supported application protocols during the TLS ClientHello. The server selects the best match. This enables HTTPS to negotiate HTTP/1.1 vs HTTP/2 vs HTTP/3 in a single TLS handshake.

```
ClientHello extensions:
  server_name: api.example.com   (SNI — Server Name Indication)
  application_layer_protocol_negotiation: ["h2", "http/1.1"]

ServerHello extension:
  application_layer_protocol_negotiation: "h2"
  -> Connection will use HTTP/2

SNI allows a single server to host multiple TLS certificates:
  api.example.com  -> certificate A
  app.example.com  -> certificate B
  Same IP address, different certificates selected by SNI
```

### 6.4 HSTS (HTTP Strict Transport Security)

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

HSTS tells browsers to only use HTTPS for this domain for the next `max-age` seconds. If preload is specified and the domain is submitted to browsers' preload lists, the browser will use HTTPS on the very first visit (before any HTTP response). This prevents SSL stripping attacks.

### 6.5 HTTP Methods and Idempotency

| Method | Safe | Idempotent | Body | Use Case |
|--------|------|-----------|------|----------|
| GET | Yes | Yes | No | Retrieve resource |
| HEAD | Yes | Yes | No | GET without body (check headers) |
| OPTIONS | Yes | Yes | No | CORS preflight, capabilities |
| PUT | No | Yes | Yes | Replace resource completely |
| DELETE | No | Yes | No | Delete resource |
| POST | No | No | Yes | Create resource, submit data |
| PATCH | No | No | Yes | Partial update |

Idempotent: sending the same request N times has the same effect as sending it once. This property is critical for retry logic in distributed systems.

---

## 7. Real-World Examples

**Nginx HTTP/2 configuration**:
```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    # Enable HSTS
    add_header Strict-Transport-Security "max-age=31536000" always;

    # HTTP/2 push (limited use case)
    # http2_push /css/main.css;
}
```

**Spring Boot configuring caching headers**:
```java
@GetMapping("/api/products/{id}")
public ResponseEntity<Product> getProduct(
        @PathVariable Long id,
        WebRequest request) {

    Product product = productService.findById(id);
    String etag = '"' + product.getVersion() + '"';

    if (request.checkNotModified(etag)) {
        return ResponseEntity.status(304).build();  // 304 Not Modified
    }

    return ResponseEntity.ok()
        .eTag(etag)
        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
        .lastModified(product.getUpdatedAt())
        .body(product);
}
```

---

## 8. Tradeoffs

| HTTP Version | Connection overhead | HoL blocking | CPU (parsing) | Support |
|-------------|---------------------|-------------|----------------|---------|
| HTTP/1.1 | High (multiple connections) | Per connection | Low | Universal |
| HTTP/2 | Low (1 connection) | At TCP level | Medium (HPACK) | Wide (95%+) |
| HTTP/3 | Low (QUIC) | None | Higher (QUIC) | Growing (85%+) |

| TLS Version | RTTs | Security | Performance |
|-------------|------|---------|-------------|
| TLS 1.2 | 2 | Acceptable | Standard |
| TLS 1.3 | 1 (0 on resume) | Strong (mandatory PFS) | Better |

---

## 9. When to Use / When NOT to Use

**HTTP/2**: Use for all modern APIs. The single-connection model with HPACK compression reduces bandwidth and improves latency for mobile clients. Do not rely on HTTP/2 server push — it was rarely effective and Chrome removed it in Chrome 106.

**HTTP/3**: Use for public-facing endpoints serving mobile or high-latency users. Requires infrastructure support (UDP on port 443). Fall back gracefully to HTTP/2. Not needed for internal service-to-service communication on reliable networks.

**TLS 1.3**: Use for all new deployments. Disable TLS 1.0 and 1.1 (required by PCI DSS 4.0). TLS 1.2 is acceptable but should be upgraded.

**no-store vs no-cache**: Use `no-store` only when responses must never be stored (sensitive data like bank statements). Use `no-cache` when responses can be stored but must be revalidated — this enables conditional GET (304) optimization.

---

## 10. Common Pitfalls

**HTTP/2 and load balancers**: Some legacy load balancers only support HTTP/1.1 between themselves and backends. They terminate HTTP/2 from clients but speak HTTP/1.1 to backends — losing multiplexing benefits at the LB-backend hop. Verify that your LB supports HTTP/2 for upstream connections.

**Vary header causing cache fragmentation**: An overly broad `Vary: *` or `Vary: User-Agent` causes CDNs to cache a separate response for every User-Agent string — potentially thousands of cache entries for the same resource. Use `Vary: Accept-Encoding` for compressed responses and nothing else for most APIs.

**HPACK dynamic table size and header size limits**: HTTP/2 has a `SETTINGS_HEADER_TABLE_SIZE` (default 4096 bytes) and servers enforce `SETTINGS_MAX_HEADER_LIST_SIZE`. Spring Boot defaults to max header size of 8 KB for HTTP/1.1 but the HTTP/2 default in Tomcat/Netty may be lower. Applications with large cookies or JWT tokens in headers can hit this limit and receive 431 (Request Header Fields Too Large).

**Missing Content-Type on REST responses**: HTTP/1.1 clients that receive JSON without `Content-Type: application/json` may treat the response as text. Proxies may not compress it. Always set Content-Type explicitly.

**Certificate pinning and rotation**: Mobile apps that pin the server certificate will break when the certificate is rotated. Use public key pinning (pin the SubjectPublicKeyInfo hash) rather than certificate pinning, and always ship a backup pin. Certificate pinning is generally not recommended for most APIs — use HSTS and certificate transparency instead.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| `curl -v --http2` | Test HTTP/2 connection |
| `curl --http3` | Test HTTP/3 (requires curl with QUIC support) |
| `nghttp2` | HTTP/2 client/server debugging |
| `h2c` (Go) | Simple HTTP/2 test server |
| Chrome DevTools Network tab | Inspect HTTP versions, timing, headers |
| `openssl s_client` | TLS handshake inspection |
| `ssllabs.com` | TLS configuration grading |
| Mozilla Observatory | Security headers checker |
| `mkcert` | Local development TLS certificates |
| `caddy` | HTTP/3-ready web server with auto-TLS |
| Wireshark | HTTP/2 frame dissector |

---

## 12. Interview Questions with Answers

**What are the main improvements HTTP/2 provides over HTTP/1.1?**
HTTP/2 provides multiplexing (multiple streams on one TCP connection, eliminating the need for 6 parallel connections per domain), HPACK header compression (repeated headers like Authorization are sent as 2-byte references after first request, reducing bandwidth 70-90%), binary framing (replaces text parsing with structured frames), and built-in flow control per stream. HTTP/1.1 with pipelining was supposed to solve serialization but was so broken in practice it was almost never enabled.

**Explain the head-of-line blocking problem in HTTP/2.**
HTTP/2 multiplexes all streams over one TCP connection. If a TCP segment is lost, TCP's in-order delivery guarantee means no data from any stream can be delivered to the application until the lost segment is retransmitted and received. All HTTP/2 streams stall, even those whose data arrived successfully. This is TCP-level HoL blocking. HTTP/3 solves this by running over QUIC, where each stream is independently sequenced.

**How does TLS 1.3 reduce latency compared to TLS 1.2?**
TLS 1.2 requires 2 RTTs for a full handshake (1 RTT for TCP, 2 for TLS = 3 RTTs before data). TLS 1.3 reduced this to 1 RTT for TLS (2 RTTs total). TLS 1.3 also supports 0-RTT session resumption (sending application data in the first packet). TLS 1.3 mandatory forward secrecy eliminated export-grade ciphers and simplified cipher suite negotiation, improving security alongside performance.

**What is ALPN and why is it needed?**
ALPN (Application-Layer Protocol Negotiation) is a TLS extension allowing the client to advertise supported application protocols (h2, http/1.1, h3) in the ClientHello. The server picks the best supported protocol and includes it in the ServerHello. Without ALPN, a client would need a separate round trip to negotiate the application protocol, or use a different port per protocol. ALPN enables HTTP/2 to be selected during the TLS handshake on port 443.

**What does the HTTP Cache-Control: max-age directive do, and how does it differ from Expires?**
Cache-Control: max-age=N specifies that the response is fresh for N seconds from when it was served. Expires provides an absolute date-time. max-age takes precedence over Expires when both are present. Prefer Cache-Control because it is relative to serving time (robust to clock skew), and because Expires is a legacy header from HTTP/1.0.

**What is an ETag and how does it enable conditional requests?**
An ETag is a server-generated identifier representing the version of a resource (hash, version number, or timestamp). The server includes it in the response: `ETag: "abc123"`. On subsequent requests, the client sends `If-None-Match: "abc123"`. If the resource hasn't changed, the server responds 304 Not Modified with no body — saving bandwidth. ETags must change whenever the resource changes.

**What is SNI and why is it necessary for modern HTTPS?**
SNI (Server Name Indication) is a TLS extension where the client includes the target hostname in the ClientHello (before TLS is established). This allows a server to present different certificates for different hostnames on the same IP address. Without SNI, a server could only host one certificate per IP — impractical when IPv4 addresses are scarce. CDNs, hosting providers, and cloud load balancers all depend on SNI for multi-tenant certificate management.

**What does the HSTS header do and what is the preload list?**
HSTS (Strict-Transport-Security) tells browsers to only connect via HTTPS for the duration specified by max-age. If a user types http://example.com, the browser upgrades to HTTPS locally before making any network request — preventing SSL stripping. The preload list is a browser-shipped list of domains that must always use HTTPS, protecting even first-time visitors before any HSTS header is received.

**What is the difference between HTTP 301 and 302 redirects, and how do they affect caching?**
301 (Moved Permanently) is cacheable and instructs browsers to update bookmarks. Subsequent requests go directly to the new URL. 302 (Found, temporary redirect) is not permanently cacheable — the browser asks the original URL each time (though some browsers cache 302 with a short duration). Use 301 for permanent moves (old API versions, www to non-www). Use 302 for temporary moves or feature flags. Incorrect use of 301 makes rollbacks painful (cached redirect).

**How does HTTP/2 server push work, and why was it deprecated in Chrome?**
HTTP/2 server push allowed a server to proactively send resources (CSS, JS) to the client before it requests them, using PUSH_PROMISE frames. In theory, this eliminated round trips for critical resources. In practice, servers couldn't know what was already in the browser cache — they would push resources the browser already had, wasting bandwidth. Chrome removed server push in Chrome 106. The preload link header with `<link rel="preload">` is more effective.

**What are the HTTP methods and which are idempotent?**
GET, HEAD, OPTIONS, PUT, DELETE are idempotent (same request N times has same effect as once). POST and PATCH are not idempotent (submitting the same POST twice creates two resources). Safe methods (GET, HEAD, OPTIONS) do not modify server state. Idempotency is critical for retry logic in distributed systems — safely retrying a PUT or DELETE after a network failure cannot create inconsistency.

**What is the Vary header and when does it cause problems?**
The Vary header tells caches to store separate responses for different values of the listed headers. `Vary: Accept-Encoding` causes caches to store different responses for gzip, br, and uncompressed clients. `Vary: User-Agent` causes caches to store thousands of responses per URL (one per User-Agent), destroying cache hit rates. `Vary: *` means nothing can be cached. Only include headers in Vary that genuinely produce different responses.

**What is the difference between HTTP long polling and WebSocket?**
Long polling: the client sends an HTTP request; the server holds it open until data is available (or timeout), then responds; the client immediately sends another request. It uses standard HTTP semantics but creates connection churn and overhead. WebSocket: the client upgrades the connection (101 Switching Protocols), and then both sides can send frames at any time over the persistent connection. WebSocket has lower overhead per message, better performance, but requires explicit infrastructure support (load balancers, proxies).

**How does HTTP/2 flow control work?**
HTTP/2 has flow control at two levels: per-connection and per-stream. Each stream has an initial window size (default 65,535 bytes). When the receiver processes DATA frames, it sends WINDOW_UPDATE frames to increase the window. The sender cannot send more data than the window allows. Connection-level flow control aggregates all streams. This prevents a fast sender from overwhelming a slow receiver's buffers, analogous to TCP's receive window but at the application layer.

**What is the HTTP/2 SETTINGS frame and what can it configure?**
SETTINGS frames are exchanged at connection setup and can be sent anytime to update settings. Key parameters: HEADER_TABLE_SIZE (HPACK dynamic table size, default 4096), ENABLE_PUSH (server push, 0 to disable), MAX_CONCURRENT_STREAMS (default unlimited; typically 100-1000 in practice), INITIAL_WINDOW_SIZE (flow control window, default 65535), MAX_FRAME_SIZE (max DATA frame, default 16384 bytes), MAX_HEADER_LIST_SIZE (max header set size). Misconfiguring these causes 429/431 errors or poor performance.

---

## 13. Best Practices

- Disable TLS 1.0 and 1.1 everywhere. Require TLS 1.2 minimum; prefer TLS 1.3.
- Enable HSTS with max-age of at least 1 year (31,536,000 seconds) on all production domains.
- Use HPACK compression benefits by keeping headers consistent between requests (same header order, same values where possible).
- Set `Cache-Control: no-store` only for genuinely sensitive responses (bank account data). Use `no-cache` for resources that should be revalidated but can be stored.
- Always include a strong ETag for resources that support conditional GET — eliminates bandwidth for unchanged resources.
- Configure HTTP/2 on both client and server sides of your infrastructure, including LB-to-backend connections.
- Set `Content-Type` on all responses; never rely on content sniffing.
- Monitor HTTP/2 stream errors (RST_STREAM frames) in server metrics — they indicate client or server behavior issues.

---

## 14. Case Study

**Problem**: A mobile API had slow load times on first launch despite fast database queries and minimal processing. Cold start on mobile was 3.5–5 seconds. Warm launch (subsequent) was 200ms.

**Investigation**:
1. Charles Proxy capture showed the initial TLS handshake was taking 600ms on mobile (high latency links).
2. After TLS, 8 parallel HTTP/1.1 requests to 2 domains (6 to api.example.com, 2 to cdn.example.com).
3. The API was returning large headers (800-byte JWT in Authorization response, 400 bytes of debugging headers in dev mode accidentally shipped to prod).

**Root Cause**: Three compounding issues:
1. TLS 1.2 (2 RTTs) on a 150ms mobile link = 300ms just for TLS.
2. HTTP/1.1 with 6 connections to api.example.com — 6 TLS handshakes = 6 * 300ms = 1.8 seconds of TLS overhead.
3. 8 KB headers per request with uncompressed headers.

**Fixes applied**:
1. Migrated to TLS 1.3 with session resumption: TLS handshake reduced to 1 RTT (150ms), 0-RTT on resume (0ms).
2. Enabled HTTP/2 on the API server: reduced 6 TLS handshakes to 1. All 8 requests multiplexed.
3. Removed debug headers from production responses: headers reduced from 800 bytes to 180 bytes.
4. Enabled HPACK compression: after first request, repeated headers sent as 2-byte references.

**Results**:
- Cold start: 3.5s → 0.9s (74% reduction)
- Warm start: 200ms → 95ms (53% reduction, primarily from 0-RTT TLS + HPACK)
- Bandwidth per request: reduced 60% due to HPACK and header cleanup

**Lesson**: HTTP protocol version and TLS version have multiplicative effects on mobile performance. Every RTT on a 150ms mobile link costs 150ms. Eliminating unnecessary RTTs (TLS 1.3, HTTP/2 connection reuse) and unnecessary bytes (HPACK, clean headers) directly translates to perceived app speed.
