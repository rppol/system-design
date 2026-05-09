# API Design

## 1. Concept Overview

An API (Application Programming Interface) is a contract between a provider and a consumer that defines how software components communicate. In distributed systems, APIs are the backbone of service-to-service and client-to-server interaction.

Good API design is not just about making things work — it is about creating stable, evolvable, and developer-friendly contracts that remain consistent across years of product change. A poorly designed API is one of the most expensive technical debts you can accumulate: once published, consumers depend on it, and breaking changes cascade into multi-team coordination nightmares.

Why it matters:
- APIs outlive the code behind them. The contract is harder to change than the implementation.
- Developer experience directly affects adoption and time-to-integration.
- Well-designed APIs reduce support burden and internal coupling.
- In microservices architectures, the entire system is essentially a graph of API calls.

---

## Intuition

> **One-line analogy**: API design is like designing a restaurant menu — you decide what dishes to offer (endpoints), what information customers need to order (request format), and what they'll receive (response format). Once published, changing the menu is painful.

**Mental model**: An API is a contract between two parties (producer and consumer). REST uses URLs to represent resources and HTTP verbs to represent actions — it's stateless, cacheable, and universally understood. gRPC uses protocol buffers and HTTP/2 for high-performance service-to-service communication. GraphQL lets clients request exactly the fields they need. Each has its sweet spot.

**Why it matters**: APIs outlive the code behind them. Once external consumers depend on your API, breaking changes require coordinating across teams, codebases, and sometimes companies. Good API design with proper versioning prevents years of accumulated technical debt.

**Key insight**: Stability trumps cleverness. A simpler, more consistent API that never changes is worth far more than a sophisticated API that requires frequent breaking changes. Versioning from day one (v1, v2) is cheaper than retrofitting it later.

---

## 2. Core Principles

**Consistency** — Use the same naming conventions, error formats, and patterns across all endpoints. Developers should be able to predict how a new endpoint works from prior experience with your API.

**Statelessness** — Each request should carry all the information needed to fulfill it. The server should not rely on stored client context between requests. This enables horizontal scaling and simplifies failure recovery.

**Resource Orientation** — Design around nouns (resources), not verbs (actions). `GET /orders/123` is better than `GET /getOrder?id=123`.

**Least Surprise** — The API should behave in the way a reasonable developer would expect. Avoid surprising behaviors in edge cases.

**Versioning from Day One** — Assume your API will change. Build versioning in before you have consumers, not after.

**Fail Gracefully** — Return meaningful error codes and messages. Never expose internal stack traces. Distinguish between client errors (4xx) and server errors (5xx).

**Idempotency** — Unsafe operations (PUT, DELETE, and ideally POST with idempotency keys) should be safe to retry without side effects.

**Security by Default** — Authentication and authorization are not features to add later. Design them in from the first endpoint.

---

## 3. Types / Strategies

### REST (Representational State Transfer)
The dominant paradigm for HTTP APIs. Leverages HTTP verbs (GET, POST, PUT, PATCH, DELETE) mapped to CRUD operations on resources. Stateless, cacheable, and well-understood.

**Best for:** Public APIs, mobile/web clients, resource-centric data models.

### GraphQL
A query language for APIs developed by Facebook. The client specifies exactly the shape of the data it needs. A single endpoint handles all queries and mutations.

**Best for:** Complex UIs that need to aggregate data from multiple resources, reducing over-fetching and under-fetching, rapid frontend iteration.

### gRPC (Google Remote Procedure Call)
A high-performance RPC framework using Protocol Buffers (binary serialization) over HTTP/2. Strongly typed contracts defined in `.proto` files. Supports streaming.

**Best for:** Internal microservice communication, low-latency high-throughput systems, polyglot environments (auto-generate clients in many languages).

### WebSockets
Bidirectional, persistent connection between client and server. Enables real-time push from server to client without polling.

**Best for:** Chat, live notifications, collaborative editing, trading dashboards.

### Webhooks
Reverse API — the server pushes data to the client by calling a URL registered by the client. Event-driven.

**Best for:** Asynchronous event notification (payment processed, file uploaded, etc.).

### Comparison Table

| Criterion          | REST       | GraphQL    | gRPC       |
|--------------------|------------|------------|------------|
| Protocol           | HTTP/1.1+  | HTTP/1.1+  | HTTP/2     |
| Payload format     | JSON/XML   | JSON       | Protobuf   |
| Type safety        | Weak       | Strong     | Strong     |
| Caching            | Excellent  | Hard       | Manual     |
| Browser support    | Native     | Native     | Limited    |
| Streaming          | SSE/WS     | Subscriptions | Native  |
| Learning curve     | Low        | Medium     | Medium     |
| Best use           | Public APIs| Flexible UIs| Internal  |

---

## 4. Architecture Diagrams

### REST API Request Lifecycle

```
Client
  |
  |  HTTP Request (GET /api/v1/users/42)
  v
API Gateway
  |-- Authentication (validate JWT)
  |-- Rate Limiting (check token bucket)
  |-- Routing (match path to service)
  v
User Service
  |-- Controller (parse request, validate)
  |-- Service Layer (business logic)
  |-- Repository (DB query)
  v
Database
  |
  | Response
  v
User Service --> API Gateway --> Client
                (JSON payload)
```

### GraphQL vs REST Data Fetching

```
REST (N+1 requests):
Client --> GET /users/1         --> { id, name, avatarId }
Client --> GET /avatars/99      --> { url, size }
Client --> GET /posts?userId=1  --> [{ id, title }, ...]
(3 round trips)

GraphQL (1 request):
Client --> POST /graphql
  query {
    user(id: 1) {
      name
      avatar { url }
      posts { title }
    }
  }
--> single response with all data
(1 round trip)
```

### gRPC Internal Service Communication

```
Service A (Go)                   Service B (Python)
    |                                   |
    | proto-generated client stub       |
    |                                   |
    |------- HTTP/2 + Protobuf -------->|
    |       (binary, multiplexed)       |
    |<------ response stream -----------|
    |                                   |
```

---

## 5. How It Works — Detailed Mechanics

### REST Mechanics

HTTP verbs map to operations:
- `GET` — retrieve resource, safe and idempotent
- `POST` — create resource, neither safe nor idempotent
- `PUT` — replace resource entirely, idempotent
- `PATCH` — partial update, not necessarily idempotent
- `DELETE` — remove resource, idempotent

Status codes carry semantic meaning:
- `200 OK`, `201 Created`, `204 No Content`
- `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`
- `429 Too Many Requests`
- `500 Internal Server Error`, `503 Service Unavailable`

### Pagination Strategies

**Offset-based:** `GET /items?offset=40&limit=20`
- Simple to implement
- Breaks under concurrent inserts (item skipped or duplicated as page shifts)
- Poor performance at high offsets (DB scans all rows up to offset)

**Cursor-based:** `GET /items?after=eyJpZCI6MTAwfQ&limit=20`
- Cursor encodes position (e.g., base64 of last seen ID + timestamp)
- Stable under inserts/deletes
- Cannot jump to arbitrary pages
- Used by Twitter, Facebook, GitHub

**Keyset pagination:** `GET /items?last_id=100&limit=20`
- Similar to cursor, uses an indexed column
- Very fast with proper index (`WHERE id > 100 LIMIT 20`)

**Page-based:** `GET /items?page=3&per_page=20`
- Human-friendly
- Suffers same offset problems

### Authentication

**API Keys** — static tokens in headers (`X-API-Key: abc123`). Simple but not user-specific, hard to rotate per-user.

**OAuth 2.0** — authorization framework. Four grant types:
1. Authorization Code (web apps) — most secure, uses server-side token exchange
2. Client Credentials (service-to-service) — no user involved
3. Implicit (deprecated) — was for SPAs, now replaced by Authorization Code + PKCE
4. Resource Owner Password (legacy) — avoid

**JWT (JSON Web Token)** — self-contained token with header, payload, signature. Stateless verification (server checks signature without DB lookup). Structure: `base64(header).base64(payload).signature`.

Payload contains claims: `sub` (subject), `iat` (issued at), `exp` (expiry), `iss` (issuer), custom claims.

Risk: stolen JWTs are valid until expiry. Mitigate with short expiry + refresh tokens.

### Idempotency

For non-idempotent operations (POST), use an idempotency key:

```
POST /payments
Idempotency-Key: client-generated-uuid-here
{ "amount": 100, "currency": "USD" }
```

Server stores (key → result) with a TTL. On retry with same key, return cached result without re-executing the operation. Stripe and PayPal use this pattern.

### HATEOAS (Hypermedia as the Engine of Application State)

Responses include links to related actions, allowing clients to discover capabilities dynamically:

```json
{
  "id": 42,
  "status": "pending",
  "_links": {
    "self": { "href": "/orders/42" },
    "cancel": { "href": "/orders/42/cancel", "method": "POST" },
    "payment": { "href": "/orders/42/payment" }
  }
}
```

Rarely implemented fully in practice, but link inclusion is common.

---

## 6. Real-World Examples

**Stripe** — widely considered the gold standard for REST API design. Consistent error objects, idempotency keys on all write operations, webhook signatures for security, excellent versioning (`Stripe-Version` header + date-based versions), comprehensive SDK generation.

**GitHub** — offers both REST v3 and GraphQL v4. GraphQL v4 was introduced because the REST API required many requests to fetch PR data with associated reviews, checks, and comments. Power users migrated to GraphQL for efficiency.

**Twitter/X** — moved from REST to GraphQL-like patterns internally. Public API uses REST with cursor-based pagination (`next_token` in responses).

**Google** — gRPC is used extensively for internal service communication. Public-facing APIs follow the Google API Design Guide (AIP — API Improvement Proposals), which defines a resource-oriented REST style called "Google API Style."

**Netflix** — uses GraphQL Federation at the API Gateway layer (Federated Graph). Each domain team owns their schema slice. The gateway composes them into a unified graph for clients.

**Amazon AWS** — uses a consistent REST-like style across thousands of APIs with Signature Version 4 authentication (HMAC-SHA256 request signing). API Gateway product lets teams expose their services externally with built-in throttling, caching, and auth.

---

## 7. Tradeoffs

### REST
| Gain | Lose |
|------|------|
| Simple, universal, HTTP-native caching | Over-fetching (extra fields) and under-fetching (N+1 requests) |
| Works in any browser natively | No strong schema enforcement without OpenAPI |
| Easy to debug with curl/Postman | Multiple round trips for related data |

### GraphQL
| Gain | Lose |
|------|------|
| Fetch exactly what you need | Complex server-side query analysis and execution |
| Strongly typed schema | HTTP caching is hard (POST requests) |
| Excellent for rapid UI iteration | N+1 problem moves to server (use DataLoader) |

### gRPC
| Gain | Lose |
|------|------|
| Extremely fast binary serialization | No native browser support (requires grpc-web proxy) |
| Bidirectional streaming | Harder to debug (binary, not human-readable) |
| Auto-generated type-safe clients | Proto schema management overhead |

---

## 8. When to Use

- **REST** — public APIs, mobile/web clients, when HTTP caching matters, when the team knows HTTP well
- **GraphQL** — complex UIs aggregating multiple data sources, when clients have varying data needs, BFF (Backend for Frontend) pattern
- **gRPC** — internal microservices, high-throughput low-latency pipelines, streaming data (IoT, telemetry), polyglot environments
- **WebSockets** — real-time features: live scores, chat, collaborative editing
- **Webhooks** — event-driven integrations (CI/CD triggers, payment notifications, CRM syncs)

---

## 9. When NOT to Use

- Do not use **REST** for real-time bidirectional communication (use WebSockets or gRPC streaming)
- Do not use **GraphQL** for simple CRUD APIs — the complexity overhead is not worth it
- Do not use **gRPC** for public browser-facing APIs unless you add a translation layer
- Do not add **HATEOAS** in full purity unless your clients are truly hypermedia-driven — most clients ignore the links
- Do not use **webhooks** when the consumer needs synchronous confirmation of the result

---

## 10. Common Pitfalls

**Verb-based URLs** — `POST /createUser` violates REST. Use `POST /users`.

**Inconsistent error formats** — different endpoints returning different error shapes forces consumers to handle each case specially. Standardize on one error envelope.

**Ignoring HTTP status codes** — returning `200 OK` with `{ "success": false }` in the body is an anti-pattern. Use appropriate 4xx/5xx codes.

**Not versioning from the start** — adding `/v2/` later forces all consumers to migrate. Start with `/v1/` on day one.

**Synchronous calls for async work** — making `POST /reports` block for 30 seconds while generating the report. Instead return `202 Accepted` with a job ID and a polling or webhook mechanism.

**Exposing database IDs** — sequential integer IDs leak information (how many users you have). Prefer UUIDs or opaque IDs.

**Missing rate limiting on public endpoints** — any unauthenticated endpoint will be abused without rate limiting.

**Over-nesting resources** — `/users/1/orders/2/items/3/reviews` is too deep. Flatten where possible: `/reviews?item_id=3`.

**Not documenting breaking vs non-breaking changes** — adding a required field is breaking. Adding an optional field is not. Communicate this clearly in changelogs.

**Missing idempotency on payment/order creation** — network retries without idempotency keys cause duplicate charges.

---

## 11. Technologies & Tools

| Category | Tools |
|----------|-------|
| API Frameworks (REST) | Express.js, FastAPI, Django REST Framework, Spring Boot, Rails |
| API Frameworks (GraphQL) | Apollo Server, Strawberry, Hasura, Pothos |
| API Frameworks (gRPC) | grpc-go, grpc-java, grpcio, grpc-node |
| API Gateway | Kong, AWS API Gateway, Apigee, Traefik, Envoy |
| Documentation | OpenAPI/Swagger, Redoc, Stoplight, GraphQL Playground |
| Testing | Postman, Insomnia, k6, Hurl, Karate |
| Mocking | WireMock, Mockoon, Microcks |
| SDK Generation | OpenAPI Generator, Buf (for protobuf) |
| Auth | Auth0, Keycloak, AWS Cognito, Okta |

---

## 12. Interview Questions

**Q1: What is the difference between PUT and PATCH?**
PUT replaces the entire resource. PATCH applies a partial update. Example: PUT /users/1 requires sending all user fields; PATCH /users/1 can send only `{ "email": "new@example.com" }`.

**Q2: How do you handle API versioning?**
Common strategies: URL path versioning (`/v1/`, `/v2/`), header versioning (`Accept: application/vnd.myapi.v2+json`), and query parameter versioning (`?version=2`). URL path versioning is most visible and commonly used. Always deprecate old versions with sunset headers before removing.

**Q3: What is the difference between authentication and authorization?**
Authentication verifies identity ("who are you?" — JWT, session cookie). Authorization verifies permission ("are you allowed?" — RBAC, ABAC, scope checks). Both are needed; confusing them is a common security mistake.

**Q4: How would you design a pagination strategy for a high-traffic API?**
Use cursor-based pagination instead of offset. Store the cursor as a base64-encoded pointer to the last seen row (ID or timestamp). This ensures stable pages under concurrent writes and is O(1) with a proper index, unlike OFFSET which scans all preceding rows.

**Q5: What is idempotency and why does it matter in API design?**
An idempotent operation produces the same result whether called once or many times. GET and DELETE are naturally idempotent. POST is not; you add idempotency by accepting a client-provided key that the server uses to deduplicate retries. Critical for payment and order APIs where network failures cause retries.

**Q6: Explain REST constraints.**
Stateless, client-server separation, uniform interface (resource identification, manipulation through representations, self-descriptive messages, HATEOAS), layered system, cacheable, optional code-on-demand.

**Q7: How does GraphQL solve the N+1 problem on the server side?**
Using DataLoader: batch individual database lookups that occur during field resolution into a single batched query per tick of the event loop. Instead of 100 separate DB calls to fetch 100 users' avatars, DataLoader fires one `SELECT WHERE id IN (...)` query.

**Q8: What are the tradeoffs of JWT vs opaque session tokens?**
JWTs are stateless (no DB lookup to verify) enabling horizontal scaling, but cannot be invalidated before expiry. Opaque tokens require a DB/cache lookup per request but can be revoked instantly. Hybrid: short-lived JWTs (15 min) + long-lived refresh tokens stored in a revocation store.

**Q9: How would you design rate limiting at the API gateway level?**
Use a token bucket or sliding window algorithm. Store state in Redis (for distributed rate limiting). Key by IP for unauthenticated endpoints and by user/API key for authenticated ones. Return `429 Too Many Requests` with `Retry-After` and `X-RateLimit-*` headers.

**Q10: What is gRPC and when would you prefer it over REST?**
gRPC uses Protocol Buffers over HTTP/2, providing binary serialization (smaller payloads), multiplexed streams, bidirectional streaming, and auto-generated type-safe clients. Prefer it for internal service-to-service communication where latency and throughput matter, or when you need streaming.

**Q11: How do you handle breaking changes in a public API?**
Version the API (introduce `/v2/`). Run both versions in parallel. Set a deprecation date, communicate via docs and `Deprecation` / `Sunset` headers. Give consumers at least 6-12 months to migrate. Use feature flags for gradual migration. Never silently change behavior of an existing version.

**Q12: What is CORS and how does it work?**
Cross-Origin Resource Sharing. Browsers block requests from `a.com` to `b.com` unless `b.com` responds with `Access-Control-Allow-Origin: a.com`. Preflight requests (`OPTIONS`) are sent for non-simple requests. Server-side concern, not an API design concern per se, but APIs must handle it for browser clients.

---

## 13. Best Practices

- Use nouns for resource URLs: `/users`, `/orders`, not `/getUsers`, `/createOrder`
- Use plural nouns consistently: `/users/42`, not `/user/42`
- Return consistent JSON envelope: `{ "data": {}, "meta": {}, "errors": [] }`
- Always include pagination metadata: `{ "data": [...], "meta": { "next_cursor": "...", "has_more": true } }`
- Document every endpoint with OpenAPI/Swagger before implementing
- Use semantic versioning in the path: `/v1/`, `/v2/`
- Provide `Retry-After` headers on `429` and `503` responses
- Validate and sanitize all inputs server-side — never trust the client
- Log request IDs (`X-Request-Id`) for distributed tracing
- Use HTTPS everywhere; reject HTTP
- Set `Content-Type: application/json` explicitly
- Return `405 Method Not Allowed` for unsupported methods, not `404`
- Use `ETag` and `Last-Modified` headers for cacheable resources
- Design for mobile: minimize payload size, support partial responses (`?fields=id,name`)

---

## 14. Metrics & Monitoring

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| Request Latency (p50/p95/p99) | Response time distribution | p99 > 500ms |
| Error Rate (4xx/5xx) | % of requests resulting in errors | >1% 5xx |
| Throughput (RPS) | Requests per second | Baseline deviation |
| Availability | Uptime percentage | <99.9% |
| Rate Limit Hit Rate | % of requests hitting rate limits | >5% sustained |
| Cache Hit Rate | For cacheable endpoints | <80% |
| Auth Failure Rate | % of requests failing auth | Spike detection |
| Payload Size | Average response size | >100KB (investigate) |

Key tools: Datadog APM, AWS X-Ray, Prometheus + Grafana, New Relic, Jaeger (distributed tracing).

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement API Design**

- **Facade** — An API gateway is a Facade over a microservice mesh: it presents a unified, simplified interface to clients while hiding backend routing, protocol translation, and service topology.
- **Chain of Responsibility** — The middleware pipeline (auth → rate limit → validation → handler → response transform) is Chain of Responsibility. Each middleware decides whether to handle the request or pass it to the next link.
- **Builder** — Request/response objects with many optional fields (headers, query params, body, auth, timeout) are natural Builder candidates. HTTP client builders (`OkHttpClient.Builder`, gRPC stub builders) follow this pattern directly.
- **Strategy** — Authentication mechanisms (JWT, OAuth 2.0, API key, mTLS) and versioning strategies (URL path, header, content-type negotiation) are interchangeable Strategy implementations configured per-route.

---

## 15. Case Study: Designing the Stripe Payments API

**Problem:** Build a payments API that handles card charges from web and mobile clients, supports retries safely, and integrates with partner platforms.

**Step 1 — Resource Modeling**
Core resources: `PaymentIntent`, `Customer`, `PaymentMethod`, `Refund`, `Webhook`. Separate the intent (what we want to do) from the attempt (what we tried) — this allows multi-step payment flows.

**Step 2 — Idempotency**
Every `POST /payment_intents` must accept an `Idempotency-Key` header. Store `(key, user_id) -> response` in Redis with 24h TTL. Network retries return the same `PaymentIntent` object without double-charging.

**Step 3 — Versioning**
Release as `/v1/`. All API keys are pinned to the API version at time of key creation. When `/v2/` launches, existing keys continue to use v1 behavior. Customers opt in to new versions explicitly.

**Step 4 — Authentication**
API keys are `sk_live_...` (secret, server-side only) or `pk_live_...` (publishable, client-side, limited scope). OAuth for platform accounts (Stripe Connect). JWTs issued by Stripe for frontend-to-gateway calls.

**Step 5 — Webhooks for Async Events**
Payment processing is async. `POST /payment_intents` returns immediately with `status: processing`. The client registers a webhook URL. Stripe sends `payment_intent.succeeded` or `payment_intent.payment_failed` events. Webhooks include a `Stripe-Signature` header (HMAC-SHA256) for verification.

**Step 6 — Rate Limiting**
Per API key: 100 read requests/second, 100 write requests/second. Return `429` with `Retry-After: 1`. Stripe also uses exponential backoff with jitter in their SDKs.

**Step 7 — Error Design**
All errors return a consistent shape:
```json
{
  "error": {
    "type": "card_error",
    "code": "insufficient_funds",
    "message": "Your card has insufficient funds.",
    "param": "amount",
    "request_id": "req_abc123"
  }
}
```

**Outcome:** An API that handles billions of dollars in transactions daily, with near-zero double-charge incidents, high developer adoption, and a support burden dramatically lower than the industry average.
