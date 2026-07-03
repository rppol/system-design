# REST API Design

## 1. Concept Overview

REST (Representational State Transfer) is an architectural style for distributed hypermedia systems, defined by Roy Fielding in his 2000 dissertation. A RESTful API exposes resources (nouns, not verbs) over HTTP, uses standard HTTP methods for operations, and is stateless. REST is the dominant paradigm for public and internal APIs because it leverages HTTP's universal adoption, caching infrastructure, and tooling.

Getting REST right means more than mapping CRUD to HTTP methods. It requires thoughtful resource modeling, consistent versioning, idempotency for safe retries, proper HTTP caching headers, standard error responses, and pagination strategies that scale. Each of these has subtleties that separate well-designed APIs from ones that cause client integration pain for years.

---

## 2. Intuition

> **One-line analogy**: REST is a library where books are resources — you ask for a book by its call number (URL), the library tells you if it's changed since your last visit (ETag), and you never need to know which librarian helped you last time (stateless).

**Mental model**: A REST API is a set of named resources, each with a stable URL. HTTP methods map to operations: GET reads, POST creates, PUT/PATCH updates, DELETE removes. The key is that the URL identifies the resource, and the method specifies the operation — not the reverse (avoid GET /createUser or POST /getUserById).

**Why it matters**: A poorly designed REST API becomes a maintenance burden for years. Inconsistent naming, missing idempotency, no versioning strategy, and absent caching headers cause client-side bugs, unnecessary retries, and tight coupling between clients and server implementations.

**Key insight**: REST constraints exist to enable independent evolution of clients and servers. HATEOAS (Hypermedia As The Engine Of Application State) — rarely implemented but worth understanding — takes this to its logical conclusion: clients discover available actions from response links, not from documentation.

---

## 3. Core Principles

- **Stateless**: Every request contains all information needed to process it. No session state on the server.
- **Uniform interface**: Use HTTP methods consistently, use nouns for resources, use standard status codes.
- **Client-server**: Clients and servers evolve independently.
- **Cacheable**: Responses must declare themselves cacheable or not (Cache-Control, ETag).
- **Layered system**: Client cannot tell if it's talking to a CDN, load balancer, or origin server.
- **Code on demand** (optional): Server can return executable code (JavaScript). Rarely used.

---

## 4. Types / Architectures / Strategies

### 4.1 Resource Modeling

Resources are nouns, not verbs:

```
Good:                           Bad:
GET    /users                   GET /getUsers
GET    /users/{id}              POST /getUserById
POST   /users                   GET /createUser
PUT    /users/{id}              POST /updateUser
DELETE /users/{id}              GET /deleteUser

Collections vs instances:
/users           → collection
/users/123       → instance
/users/123/orders → sub-resource collection
/users/123/orders/456 → sub-resource instance
```

Nesting limit: 2 levels max. Deeper nesting becomes unwieldy:
```
/users/123/orders/456/items/789/reviews   ← too deep
/reviews?orderId=456&itemId=789           ← use query params instead
```

### 4.2 Versioning Strategies

| Strategy | Example | Pros | Cons |
|----------|---------|------|------|
| URI versioning | /api/v2/users | Simple, visible, cacheable | URL changes break bookmarks |
| Header versioning | Accept: application/vnd.api.v2+json | Clean URL, semantic | Less visible, harder to test in browser |
| Query parameter | /api/users?version=2 | Easy to test | Non-standard, pollutes URLs |
| Content negotiation | Accept: application/json; version=2 | Standard HTTP | Complex to implement |

URI versioning is most pragmatic for public APIs. Internal APIs can use header versioning.

### 4.3 HTTP Status Code Reference

| Code | Name | Use Case |
|------|------|---------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST (include Location header) |
| 202 | Accepted | Async operation accepted (return task URL) |
| 204 | No Content | Successful DELETE; PUT with no response body |
| 301 | Moved Permanently | Permanent URL change |
| 304 | Not Modified | Conditional GET, resource unchanged |
| 400 | Bad Request | Client error in request (validation failure) |
| 401 | Unauthorized | Not authenticated (missing/invalid token) |
| 403 | Forbidden | Authenticated but not authorized |
| 404 | Not Found | Resource does not exist |
| 405 | Method Not Allowed | HTTP method not supported for this resource |
| 409 | Conflict | Conflict with current state (duplicate, optimistic lock) |
| 410 | Gone | Resource permanently deleted (stronger than 404) |
| 422 | Unprocessable Entity | Semantic validation errors (not syntax) |
| 429 | Too Many Requests | Rate limit exceeded (include Retry-After header) |
| 500 | Internal Server Error | Unhandled server error |
| 502 | Bad Gateway | Upstream service returned invalid response |
| 503 | Service Unavailable | Server temporarily unavailable (include Retry-After) |

### 4.4 Pagination Strategies

| Strategy | Example | Use Case | Limitations |
|----------|---------|---------|-------------|
| Offset | ?page=3&size=20 | Simple, known total count needed | Performance degrades at high offsets; inconsistent on inserts |
| Cursor-based | ?cursor=eyJpZCI6MTAwfQ | Consistent, efficient | Cannot jump to arbitrary page |
| Keyset | ?after_id=100&size=20 | Efficient, consistent, stable | Cannot go backward easily |
| Seek/Keyset | WHERE id > 100 ORDER BY id LIMIT 20 | Most efficient for DB | Same as keyset |

Offset pagination at page 1000 with page size 20 executes `OFFSET 20000` in SQL — the database scans and discards 20,000 rows. This causes query time proportional to page number, not page size.

---

## 5. Architecture Diagrams

### Request Lifecycle with Caching

```
Client         CDN/Proxy        API Server       Database
  |               |                 |                |
  |--GET /items/5->|                 |                |
  |               | cache hit?       |                |
  |               | ETag: "v3"       |                |
  |<--200 OK ------|                 |                |
  |  [cache hit, no origin request]  |                |
  |               |                 |                |
  |--GET /items/5->|                 |                |
  |  If-None-Match: "v3"             |                |
  |               | cache miss/stale |                |
  |               |--GET /items/5-->|                |
  |               |  If-None-Match: "v3"             |
  |               |                 |--SELECT------->|
  |               |                 |<--item v3------|
  |               |<--304 Not Mod.--|                |
  |<--304 Not Mod--|                 |                |
  [304 saves body bandwidth]
```

### RFC 7807 Problem Details Error Response

```json
{
  "type": "https://api.example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Request body failed validation",
  "instance": "/api/v1/users",
  "traceId": "abc-123-def",
  "violations": [
    {
      "field": "email",
      "message": "must be a valid email address",
      "rejectedValue": "not-an-email"
    },
    {
      "field": "age",
      "message": "must be at least 18",
      "rejectedValue": 15
    }
  ]
}
```

---

## 6. How It Works — Detailed Mechanics

### 6.1 Idempotency

An operation is idempotent if applying it N times has the same effect as applying it once.

```
Idempotent:
  PUT /orders/123 with the same body → order 123 always ends in same state
  DELETE /orders/123 → first call deletes, subsequent calls return 404 (no side effect)

Not idempotent:
  POST /orders → creates a new order every time

Making POST idempotent with Idempotency-Key:
  Client generates a UUID for the request
  Sends: POST /orders
         Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

  Server:
  1. Check idempotency key in Redis/DB
  2. If exists: return cached response
  3. If new: process, store key+response, return response

  Server stores: key -> (response_body, status_code, expires_at)
  Expires after 24h (client must retry within this window)
```

### 6.2 HATEOAS

```json
// Without HATEOAS: client must know API structure
GET /orders/123
{
  "id": 123,
  "status": "pending",
  "total": 99.99
}

// With HATEOAS: response tells client what actions are available
GET /orders/123
{
  "id": 123,
  "status": "pending",
  "total": 99.99,
  "_links": {
    "self": { "href": "/orders/123" },
    "cancel": { "href": "/orders/123/cancel", "method": "POST" },
    "payment": { "href": "/payments?orderId=123", "method": "POST" },
    "customer": { "href": "/customers/456" }
  }
}
```

HATEOAS decouples clients from API structure — clients navigate via links, not hardcoded URLs. In practice, it is rarely implemented because API documentation serves the same purpose with less complexity.

### 6.3 OpenAPI 3 Specification

```yaml
openapi: 3.0.3
info:
  title: Order API
  version: 1.0.0

paths:
  /orders:
    post:
      summary: Create an order
      operationId: createOrder
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateOrderRequest'
      responses:
        '201':
          description: Order created
          headers:
            Location:
              description: URL of created order
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Order'
        '400':
          $ref: '#/components/responses/ValidationError'
        '429':
          $ref: '#/components/responses/RateLimited'
```

### 6.4 Cursor-Based Pagination Implementation

```java
// Request: GET /api/v1/orders?cursor=eyJpZCI6MTAwfQ&size=20
// cursor is base64({"id":100})

@GetMapping("/orders")
public Page<OrderDto> listOrders(
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int size) {

    Long afterId = cursor != null ? decodeCursor(cursor) : 0L;

    List<Order> orders = orderRepository
        .findByIdGreaterThanOrderByIdAsc(afterId, PageRequest.of(0, size + 1));

    boolean hasNext = orders.size() > size;
    if (hasNext) {
        orders = orders.subList(0, size);
    }

    String nextCursor = hasNext
        ? encodeCursor(orders.get(orders.size() - 1).getId())
        : null;

    return new Page<>(
        orders.stream().map(orderMapper::toDto).toList(),
        nextCursor,
        hasNext
    );
}

private String encodeCursor(Long id) {
    return Base64.getEncoder().encodeToString(
        ("{\"id\":" + id + "}").getBytes()
    );
}
```

---

## 7. Real-World Examples

**GitHub REST API**: Uses URI versioning implicitly (v3 in the path), ETag for all responses, Link header for pagination (cursor-based with rel=next/prev/first/last), standard 422 for validation errors with a `message` + `errors` array structure. Their idempotency key pattern is used for payment APIs.

**Stripe API**: The gold standard for API design. Uses idempotency keys for all POST requests, cursor-based pagination with starting_after, RFC 7807-compatible error format, and webhook signatures with HMAC-SHA256. Their versioning uses dates (2023-08-16) rather than integers.

---

## 8. Tradeoffs

| Versioning Strategy | Discoverability | Caching | Client Effort |
|--------------------|----------------|---------|---------------|
| URI (/v2/) | High | Easy (different URL) | Low |
| Header | Low | Varies | Medium |
| Query param | High | Easy | Low |

| Pagination | Consistency | Performance at scale | Can seek |
|------------|------------|---------------------|---------|
| Offset | Poor (concurrent writes) | Poor (high OFFSET) | Yes |
| Cursor | Good | Good | No |
| Keyset | Good | Excellent | No (forward only) |

---

## 9. When to Use / When NOT to Use

**REST over gRPC**: Use REST for public APIs, browser-consumable APIs, and when you need human-readable debugging. Use gRPC for internal service-to-service communication where schema enforcement and generated clients save development time.

**Cursor pagination**: Use for any feed, list, or log that can be modified between pages. Use offset only for admin interfaces where user jumps to specific pages and the data does not change.

**Idempotency-Key header**: Implement for all payment, order creation, and other state-changing POST endpoints where network failures may cause retries.

---

## 10. Common Pitfalls

**Returning 200 for errors**: Some APIs return HTTP 200 with an error body (`{"success": false, "error": "not found"}`). This breaks HTTP clients that make decisions based on status codes, breaks monitoring (all requests look successful), and breaks CDN error caching. Always use the appropriate HTTP status code.

**Using GET for state-changing operations**: `GET /users/123/delete` violates REST semantics and is dangerous — bots, prefetch, and crawlers can trigger GET requests. DELETE /users/123 ensures the operation only runs when explicitly executed by a client.

**Missing idempotency for creates**: A network timeout after a POST /orders reaches the server but before the response returns causes clients to retry, creating duplicate orders. Without idempotency keys, you need deduplication logic. With them, the server returns the original response for retries.

**Offset pagination in production**: An application that works fine at launch fails when the database grows to millions of rows. `SELECT * FROM orders ORDER BY created_at OFFSET 500000 LIMIT 20` scans and discards 500,000 rows. Migrate to keyset pagination before this becomes a problem.

**Inconsistent error formats**: Different endpoints returning different error structures (some with "message", others with "error", others with "detail") make client error handling a mess of special cases. Adopt RFC 7807 Problem Details across all endpoints from day one.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Swagger UI / ReDoc | OpenAPI documentation rendering |
| springdoc-openapi | Spring Boot OpenAPI 3 auto-generation |
| Postman | API testing, collection management |
| `httpie` | CLI HTTP client with JSON formatting |
| RestAssured | Java REST API testing framework |
| `karate` | BDD-style API testing |
| Bruno | Open-source Postman alternative |
| Stoplight Studio | OpenAPI visual editor |

---

## 12. Interview Questions with Answers

**Q: What are the REST architectural constraints?**
Stateless (no server-side session), uniform interface (standard HTTP methods and status codes, resource-based URLs), client-server separation (independent evolution), cacheable (responses declare cacheability), layered system (client does not know about intermediaries), and optionally code-on-demand (server-provided executable code). The most important for API design are stateless, uniform interface, and cacheable.

**Q: What is the difference between PUT and PATCH?**
PUT replaces the entire resource with the provided representation — fields not included in the request are set to null/default. PATCH applies a partial update — only the fields provided are changed. PUT is idempotent; PATCH can be idempotent or not depending on the patch format (JSON Patch per RFC 6902 is idempotent; a "increment counter" PATCH is not). For most REST APIs, PATCH is more practical for updates.

**Q: How do you version a REST API and what are the tradeoffs?**
URI versioning (/v1/, /v2/) is most common: visible, easy to route at the CDN/load balancer, easy to test in a browser. Header versioning (Accept: application/vnd.api.v2+json) is more RESTful but harder to test and less visible. Query parameter (?version=2) is easy to add but not standard. For public APIs, URI versioning is the pragmatic choice. Never remove a version without at least 6-12 months deprecation notice with Sunset headers.

**Q: What is idempotency and why does it matter for API design?**
Idempotency means sending the same request N times has the same side effect as sending it once. GET, PUT, DELETE are idempotent. POST is not. Idempotency matters because network timeouts can cause clients to retry — a non-idempotent POST retry creates duplicate resources. Adding an Idempotency-Key header to POST endpoints allows clients to safely retry: the server deduplicates based on the key and returns the original response for subsequent calls.

**Q: What is the difference between 401 and 403?**
401 Unauthorized means the client is not authenticated — no valid credentials were provided (missing or invalid token). Despite the name, it really means "unauthenticated." The client should authenticate and retry. 403 Forbidden means the client is authenticated but not authorized — the identity is known but lacks permission for the requested resource. The client should not retry with the same credentials.

**Q: How would you design pagination for a high-volume feed?**
Use cursor-based (keyset) pagination: instead of OFFSET N, use WHERE id > last_seen_id LIMIT 20. This is O(1) regardless of page depth. The cursor is an opaque base64-encoded value (e.g., JSON with id and timestamp) so the implementation can change. Provide a next_cursor field in responses. This is what Twitter/X, Instagram, and GitHub use for their feed APIs.

**Q: What is RFC 7807 Problem Details?**
RFC 7807 defines a standard error response format for HTTP APIs. It includes: type (URI identifying the error class), title (human-readable summary), status (HTTP status code), detail (specific explanation for this occurrence), and instance (URI of the specific request). Additional fields can be added. Using Problem Details ensures all errors have a consistent, machine-parseable format across endpoints.

**Q: How do you design a REST API for a file upload?**
For small files (<10 MB): multipart/form-data POST with the file as a form field. For large files: use a signed URL pattern — client requests a signed upload URL from the API, uploads directly to object storage (S3, GCS), then notifies the API of completion. This offloads bandwidth from the API server and enables resumable uploads. Never buffer large files in the API server's memory.

**Q: What HTTP caching headers should a REST API set?**
For mutable resources: `ETag` (version identifier) + `Cache-Control: no-cache` (must revalidate, but can store) or `Cache-Control: max-age=60, private`. For immutable versioned resources: `Cache-Control: max-age=31536000, immutable`. For sensitive data: `Cache-Control: no-store`. Always set `Vary: Accept-Encoding` if responses are compressed. The `Vary` header tells caches which request headers affect the response.

**Q: What is HATEOAS and is it practical?**
HATEOAS (Hypermedia As The Engine Of Application State) means responses include links to related resources and available actions, enabling clients to navigate the API without out-of-band documentation. In theory it allows client-server evolution. In practice it is rarely implemented because: (1) generating links is complex, especially for conditional actions; (2) most clients use generated SDKs from OpenAPI specs rather than following links; (3) it does not solve versioning problems. Understand it for interviews; use OpenAPI in production.

**Q: How do you design an async long-running operation endpoint?**
POST /jobs returns 202 Accepted with a Location header pointing to a job status URL (/jobs/123). The client polls GET /jobs/123 until status is "completed" or "failed". The response includes the result URL when done. Alternative: use webhooks — client registers a callback URL, server POSTs the result when done. Webhooks require the client to have a publicly accessible endpoint.

**Q: What is the N+1 problem in REST APIs?**
Clients need a user and their 50 orders. Naive approach: GET /users/123, then GET /orders/123, GET /orders/124... = 51 requests. Solutions: (1) include sub-resources in the response via an `include` or `embed` query parameter; (2) provide a batch endpoint (POST /orders/batch with list of IDs); (3) switch to GraphQL which resolves this structurally. The N+1 problem in REST APIs is the main argument for GraphQL in complex data-access scenarios.

**Q: How do you handle breaking changes in a REST API?**
(1) Add fields without removing (clients ignore unknown fields if using lenient deserialization). (2) Never change field types. (3) Never remove required request fields. (4) Deprecate via Sunset header: `Sunset: Sat, 01 Jan 2027 00:00:00 GMT` + `Deprecation: true`. (5) When breaking changes are unavoidable, increment the major version (/v2/). (6) Run both versions in parallel for at least 6 months. (7) Monitor usage of deprecated endpoints via metrics.

**Q: What is content negotiation in REST?**
The client uses the Accept header to specify acceptable response formats: `Accept: application/json, application/xml;q=0.9`. The server responds with the best match and includes `Content-Type` in the response. This allows one endpoint to serve multiple formats without separate URLs. Java Spring MVC's content negotiation via `produces` on @RequestMapping handles this automatically.

**Q: How do you implement rate limiting in a REST API?**
Implement at the API gateway or a filter/middleware layer. Return 429 Too Many Requests when the limit is exceeded, with headers: `X-RateLimit-Limit: 100`, `X-RateLimit-Remaining: 0`, `X-RateLimit-Reset: 1735689600` (epoch when window resets), `Retry-After: 60` (seconds). Use sliding window or token bucket algorithms. Limit by API key, user ID, or IP depending on the use case.

---

## 13. Best Practices

- Use nouns for resource URLs; HTTP methods express the operation.
- Return the created resource in the POST response body (not just a 201 with Location).
- Include a trace/correlation ID in every error response for debugging.
- Adopt RFC 7807 Problem Details for all error responses from day one.
- Implement Idempotency-Key for all state-changing POST endpoints that create resources.
- Use cursor-based pagination from the start; migrating later is painful.
- Set ETag + Cache-Control on all GET responses — even short-lived cache headers (max-age=5) dramatically reduce origin load.
- Provide a machine-readable OpenAPI spec; generate client SDKs from it.
- Use the Sunset header to communicate deprecation timelines.

---

## 14. Case Study

**Problem**: An e-commerce API had a checkout endpoint `POST /checkout` that was creating duplicate orders during Black Friday traffic spikes. Network timeouts at the CDN layer (30s) were causing mobile clients to retry the checkout POST, resulting in doubled orders and doubled charges.

**Investigation**: Load balancer logs showed the original POST reached the backend and completed in 25s (slow due to payment processor latency). The CDN timed out at 30s and the client retried. The retry arrived 5s after the first request completed, and since there was no deduplication, a second order was created.

**Fix**:
```java
// 1. Client generates UUID once per checkout attempt
// 2. Sends: POST /checkout
//           Idempotency-Key: 7f3a9b2e-1234-5678-abcd-9f8e7d6c5b4a

@PostMapping("/checkout")
public ResponseEntity<Order> checkout(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody CheckoutRequest request) {

    // Check if this key was already processed
    Optional<CachedResponse> cached =
        idempotencyStore.get(idempotencyKey);

    if (cached.isPresent()) {
        // Return identical response to original request
        return ResponseEntity
            .status(cached.get().getStatus())
            .body(cached.get().getBody());
    }

    // Acquire distributed lock on idempotency key
    try (Lock lock = lockService.lock(idempotencyKey, 60, SECONDS)) {
        // Double-check after acquiring lock
        cached = idempotencyStore.get(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(cached.get().getStatus())
                .body(cached.get().getBody());
        }

        Order order = checkoutService.process(request);

        // Store result with 24h TTL
        idempotencyStore.store(idempotencyKey, 201, order, Duration.ofHours(24));

        return ResponseEntity.created(orderUri(order)).body(order);
    }
}
```

**Result**: Zero duplicate orders on the next Black Friday. The idempotency key stored in Redis with a 24h TTL meant all retries within 24 hours returned the original response. The distributed lock prevented concurrent duplicate processing for the rare case of near-simultaneous retries.
