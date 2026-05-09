# Microservices Architecture — High-Level Design

## Table of Contents
1. [Overview and Motivation](#overview-and-motivation)
2. [Monolith vs Microservices vs SOA](#monolith-vs-microservices-vs-soa)
3. [Core Principles](#core-principles)
4. [Service Discovery](#service-discovery)
5. [API Gateway](#api-gateway)
6. [Circuit Breaker](#circuit-breaker)
7. [Service Communication](#service-communication)
8. [Data Management](#data-management)
9. [Saga Pattern](#saga-pattern)
10. [Distributed Tracing](#distributed-tracing)
11. [Security](#security)
12. [Deployment](#deployment)
13. [Architecture Diagrams](#architecture-diagrams)
14. [Real-World Examples](#real-world-examples)
15. [When NOT to Use Microservices](#when-not-to-use-microservices)
16. [Tradeoffs and Considerations](#tradeoffs-and-considerations)
17. [Best Practices](#best-practices)
18. [Case Study: E-Commerce Platform Migration](#case-study-e-commerce-platform-migration)
19. [Interview Questions](#interview-questions)

---

## Intuition

> **One-line analogy**: Microservices are like specialized departments in a company — the Payments team, Inventory team, and Shipping team each do their job independently, communicate via well-defined processes, and can be scaled or updated without shutting down the whole company.

**Mental model**: A monolith is one big application — easy to build, hard to scale. Microservices split the application into small, independently deployable services, each owning its data. The Order service knows about orders; the Inventory service knows about stock. They communicate via APIs or message queues. This enables independent scaling (scale only the bottleneck service), independent deployment (update Payments without touching User service), and team autonomy.

**Why it matters**: Microservices enable large organizations to move fast. Netflix, Amazon, Uber — all operate with hundreds of microservices. The tradeoff: distributed systems are inherently complex (network failures, eventual consistency, distributed tracing). Microservices solve organizational scaling problems but add technical complexity.

**Key insight**: Don't start with microservices. The right path is monolith → well-defined internal modules → extract microservices where scaling/deployment boundaries require it. Premature microservices create distributed monolith hell.

---

## Overview and Motivation

**Microservices** is an architectural style that structures an application as a collection of small, independently deployable services, each owning a specific business capability and its data. Services communicate over well-defined APIs.

The motivation is to solve the pain points of large monolithic systems:

| Pain Point (Monolith)                   | Microservices Solution                        |
|-----------------------------------------|-----------------------------------------------|
| Entire app redeploys for any change     | Deploy only the changed service               |
| One language/framework for everything   | Polyglot: best tool for each job              |
| Scaling requires scaling everything     | Scale only the bottleneck service             |
| One team blocked by another's code      | Teams own and deploy their services           |
| Single point of failure                 | Fault isolation: one service down != all down |
| Test suite grows with the codebase      | Smaller, faster test suites per service       |

---

## Monolith vs Microservices vs SOA

```
MONOLITH                 SOA                       MICROSERVICES
+--------------+        +------------------+       +-----+  +-----+
|              |        |    ESB           |       | Svc |  | Svc |
|  UI          |        |  (Enterprise     |       |  A  |  |  B  |
|  Business    |        |   Service Bus)   |       +-----+  +-----+
|  Logic       |        |  /     |     \   |           |       |
|  Data Layer  |        |Svc   Svc    Svc  |       +-----+  +-----+
|  Database    |        | A     B      C   |       | Svc |  | Svc |
+--------------+        +------------------+       |  C  |  |  D  |
                                                   +-----+  +-----+
```

| Dimension             | Monolith                     | SOA                            | Microservices                  |
|-----------------------|------------------------------|--------------------------------|--------------------------------|
| Service size          | One large application        | Large coarse-grained services  | Small, single-purpose services |
| Communication         | In-process function calls    | ESB (SOAP/XML heavy)           | Lightweight (REST, gRPC, events)|
| Data ownership        | Shared database              | Shared database                | Database per service           |
| Deployment            | All-or-nothing               | Service-level                  | Independent per service        |
| Team size suited for  | Small (1–10 engineers)       | Enterprise (100+)              | Medium to large (10–1000+)     |
| Complexity            | Low initially, grows fast    | High (ESB is a bottleneck)     | High distributed system complexity |

### When to split from Monolith:
- **Team size > 8–10 engineers** on one codebase (Conway's Law: org structure mirrors system architecture).
- **Deployment frequency conflict**: one team's deployments constantly block others.
- **Scaling bottleneck**: one component needs 10x resources while others need none.
- **Technology mismatch**: ML inference needs Python/GPU; transaction processing needs Java/JVM.

---

## Core Principles

### Single Responsibility
Each service is responsible for one bounded context (e.g., User Service, Order Service, Payment Service). "Do one thing and do it well."

### Loose Coupling
Services should be able to change their internal implementation without requiring changes in other services. This is enabled by:
- Stable, versioned API contracts.
- Asynchronous communication where possible.
- Database per service (no shared schema).

### High Cohesion
All functionality related to a bounded context lives inside the same service. Keep related things together.

### Decentralized Data
Each service owns its data store. No service accesses another service's database directly. This enables independent scaling, technology choice, and schema evolution.

### Design for Failure
In a distributed system, partial failures are normal. Every service must handle:
- Dependency timeouts.
- Partial degradation (return cached data, default response).
- Cascading failure prevention (circuit breakers, bulkheads).

---

## Service Discovery

In a dynamic environment (containers, auto-scaling), service instances have ephemeral IP addresses. Service discovery solves: "How does Service A find Service B?"

### Client-Side Discovery (Eureka + Ribbon)

The client queries a service registry directly and performs load balancing.

```
Service A (Client)
     |
     | 1. "Where is Order Service?"
     v
[Service Registry: Eureka]
     |
     | 2. Returns list of Order Service instances
     |    [10.0.0.1:8080, 10.0.0.2:8080, 10.0.0.3:8080]
     v
Service A
     |
     | 3. Ribbon picks instance (round-robin/least-conn)
     v
Order Service (10.0.0.2:8080)
```

Pros: no intermediate hop; client has control over load balancing algorithm.
Cons: every client language needs a registry client library.

### Server-Side Discovery (AWS ALB / Nginx)

The client calls a fixed endpoint (load balancer); the LB queries the registry and routes.

```
Service A
     |
     | 1. POST /orders  (to a fixed DNS name)
     v
[Load Balancer: ALB / Nginx]
     |
     | 2. Queries registry or uses health checks to find Order Service instances
     v
Order Service (auto-selected instance)
```

Pros: client needs no registry logic; language-agnostic.
Cons: one extra network hop; LB can be a bottleneck or single point of failure.

### DNS-Based Discovery

Service names resolve to IP addresses via DNS. Kubernetes uses this natively — a Service resource creates a stable DNS name (`order-service.default.svc.cluster.local`) that resolves to one of the healthy pod IPs.

```
Service A
     |
     | 1. DNS lookup: order-service.default.svc.cluster.local
     v
[kube-dns / CoreDNS]
     |
     | 2. Returns ClusterIP (virtual stable IP)
     v
[kube-proxy / iptables] -- routes to one of the healthy pod IPs
     |
     v
Order Service Pod
```

---

## API Gateway

The API Gateway is the single entry point for all external clients. It handles cross-cutting concerns so individual services do not have to.

### Responsibilities:

```
External Clients (Web, Mobile, Third-party)
              |
              v
     +-------------------+
     |    API GATEWAY    |
     |                   |
     | - SSL Termination |
     | - Authentication  |
     | - Rate Limiting   |
     | - Routing         |
     | - Request         |
     |   Aggregation     |
     | - Protocol        |
     |   Translation     |
     | - Logging         |
     +-------------------+
       |      |      |
       v      v      v
  User Svc  Order  Payment
             Svc    Svc
```

| Feature                 | Description                                                              |
|-------------------------|--------------------------------------------------------------------------|
| Routing                 | `/api/users/*` → User Service, `/api/orders/*` → Order Service          |
| Authentication          | Validate JWT/OAuth token once; downstream services trust gateway         |
| Rate Limiting           | Prevent abuse; per-user, per-IP, per-endpoint limits                     |
| SSL Termination         | Handle TLS at the gateway; internal traffic can be HTTP                  |
| Request Aggregation     | Mobile client: single call → gateway fans out to N services, merges response |
| Protocol Translation    | External REST → internal gRPC                                            |
| Circuit Breaking        | Gateway-level circuit breaker prevents cascading failures                |

### Popular Implementations:
- **Kong**: open-source, plugin-based, runs on Nginx.
- **AWS API Gateway**: serverless, integrates with Lambda, IAM auth, usage plans.
- **Nginx**: lightweight, high-performance, manual configuration.
- **Envoy**: data plane proxy, forms the basis of Istio service mesh.

---

## Circuit Breaker

The circuit breaker pattern prevents a failing service from causing cascading failures through the entire system.

### States:

```
                    +--------+
       [Failure     |        | [Success count
        threshold   | CLOSED | exceeds threshold]
        exceeded]   |        |
                    +--------+
                    (normal ops)
                         |
                         | failures >= threshold
                         v
                    +--------+
  [After timeout,   |        |
   allow 1 probe]   |  OPEN  |
                    |        | [Returns fallback
                    +--------+  immediately, no
                         |       calls to downstream]
                         | timeout expires
                         v
                    +-----------+
                    |           |
                    | HALF-OPEN | [1 probe request sent]
                    |           |
                    +-----------+
                         |
                +--------+---------+
                |                  |
           [Probe fails]      [Probe succeeds]
                |                  |
                v                  v
            OPEN again          CLOSED
```

### Configuration knobs:
- **Failure threshold**: number/percentage of failures to trip to OPEN (e.g., 5 failures in 10 seconds).
- **Timeout**: how long to stay OPEN before probing (e.g., 30 seconds).
- **Half-Open probe count**: how many requests to allow before deciding to close.

### Fallback strategies:
- Return cached/stale data.
- Return a default/empty response.
- Return an error with a user-friendly message.
- Redirect to a degraded mode.

### Implementations: Netflix Hystrix (deprecated), Resilience4j (JVM), Polly (.NET), pybreaker (Python).

---

## Service Communication

### Synchronous (Request-Response)

**REST (HTTP/JSON)**
- Ubiquitous, easy to debug, human-readable.
- Works across all languages without code generation.
- Overhead: verbose JSON, HTTP headers, no schema enforcement by default.

**gRPC (HTTP/2 + Protobuf)**
- Binary protocol: 5–10x smaller payloads, faster serialization.
- Strongly typed via `.proto` schema; code generation for clients/servers.
- Supports streaming (unary, server-stream, client-stream, bidirectional).
- Better for internal service-to-service communication at scale.
- Harder to debug (binary); requires a proto schema registry.

```
Service A ----[HTTP/2 + Protobuf]----> Service B
              (gRPC, ~100 microsec)

Service A ----[HTTP/1.1 + JSON]------> Service B
              (REST, ~1-5ms)
```

### Asynchronous (Event-Driven)

Services communicate via events/messages through a message broker.

- **Loose temporal coupling**: sender does not wait for receiver.
- **Better resilience**: if Order Service is down, events queue up and are processed when it recovers.
- **Harder to trace**: flow is not a call stack; requires correlation IDs and distributed tracing.

```
Payment Service
     |
     | [payment.completed event]
     v
[Kafka Topic: payment-events]
     |
     +---> Order Service (update order status)
     +---> Notification Service (send email)
     +---> Analytics Service (record revenue)
```

---

## Data Management

### Database per Service

Each service owns its own data store. No other service can access it directly.

```
User Service        Order Service       Payment Service
+----------+        +----------+        +----------+
| Users DB |        | Orders DB|        |Payments  |
| (Postgres|        | (MySQL)  |        |DB (Mongo)|
+----------+        +----------+        +----------+
```

Benefits: independent scaling, independent schema evolution, polyglot persistence (best DB for each use case), fault isolation.

Cost: no cross-service JOINs; queries that span services require API calls or denormalized read models.

### Shared Database (Anti-Pattern)

Multiple services sharing a single database schema creates tight coupling at the data layer. Any schema change can break other services. Avoid in microservices architecture.

### CQRS (Command Query Responsibility Segregation)

Separate the write model (command) from the read model (query). Write to a normalized database; project events to denormalized read models optimized for queries.

```
Write Path:
Client --> [Command API] --> [Domain Model] --> [Write DB (Postgres)]
                                                       |
                                                  [Event Bus]
                                                       |
Read Path:                                     [Event Handler]
Client --> [Query API] --> [Read Model] <-- [Read DB (Elasticsearch/Redis)]
```

Benefits: read models can be optimized independently (e.g., full-text search via Elasticsearch, caching in Redis), write model stays clean.

### Event Sourcing

Instead of storing current state, store every event that led to the current state. Current state = replay of all events.

```
Events stored:
1. OrderPlaced    {orderId: 1, items: [...]}
2. PaymentReceived {orderId: 1, amount: 99.99}
3. OrderShipped   {orderId: 1, trackingId: "XYZ"}

Current state (derived by replaying):
Order #1: SHIPPED, paid $99.99, tracking: XYZ
```

Benefits: complete audit trail, temporal queries ("what was the state at time T?"), event replay for new projections.
Costs: event replay can be slow for old aggregates (mitigate with snapshots), eventual consistency.

---

## Saga Pattern

Sagas manage distributed transactions across multiple services without 2-Phase Commit (2PC), which does not scale in microservices.

A saga is a sequence of local transactions; each transaction publishes an event that triggers the next step. On failure, compensating transactions undo previous steps.

### Orchestration-based Saga

A central coordinator (saga orchestrator) tells each service what to do and tracks state.

```
             [Saga Orchestrator]
              /       |        \
             /        |         \
 1. Reserve       2. Charge    3. Create
 Inventory        Payment      Shipment
    |                |              |
 [Success]       [FAIL]
    |                |
    |         Saga triggers compensation:
    |         4. Release Inventory
    |
   (inventory released)
```

Pros: clear state machine in one place, easy to visualize.
Cons: orchestrator can become a bottleneck; introduces coupling to orchestrator.

### Choreography-based Saga

Services react to events and publish events to trigger the next step. No central coordinator.

```
Order Service
  --[OrderPlaced]-->

                     Payment Service
                       --[PaymentCharged]-->

                                             Inventory Service
                                               --[InventoryReserved]-->

                                                                        Shipping Service
                                                                          --[ShipmentCreated]-->
```

If Payment fails: Payment Service publishes `PaymentFailed` → Order Service listens and cancels order.

Pros: no central coordinator; truly decoupled.
Cons: harder to track overall saga state; debugging distributed choreography is complex.

---

## Distributed Tracing

In a microservices system, a single user request may touch 10+ services. Distributed tracing provides end-to-end visibility.

### Correlation IDs

Each request is assigned a unique trace ID at the entry point (API gateway). This ID is propagated as an HTTP header (`X-Trace-ID` or `traceparent` per W3C standard) through every service call.

```
Client
  |
  | [GET /checkout, X-Trace-ID: abc123]
  v
API Gateway --> User Svc --> Cart Svc --> Inventory Svc --> Payment Svc
                  |              |               |               |
               span-1          span-2          span-3          span-4
               (all under trace: abc123)
```

### Tools

| Tool         | Description                                                         |
|--------------|---------------------------------------------------------------------|
| **Jaeger**   | Open-source, CNCF project, supports OpenTelemetry                  |
| **Zipkin**   | Twitter-originated, simpler setup                                   |
| **OpenTelemetry** | Vendor-neutral standard for traces, metrics, logs               |
| **AWS X-Ray**| Managed, integrates with AWS services                              |
| **Datadog APM** | Commercial, rich UI, correlates with metrics and logs            |

### What you can answer with traces:
- Which service added the most latency?
- Which service call failed in this request?
- How does latency change with load?
- Which downstream dependency is the bottleneck?

---

## Security

### Service Mesh (Istio)

A service mesh manages service-to-service communication as infrastructure, not application code.

```
Service A Pod                   Service B Pod
+-------------------+          +-------------------+
| App Container     |          | App Container     |
| [Business Logic]  |          | [Business Logic]  |
+-------------------+          +-------------------+
| Sidecar Proxy     | <------> | Sidecar Proxy     |
| [Envoy]           |  mTLS    | [Envoy]           |
+-------------------+          +-------------------+
        |                               |
        +---------- Control Plane ------+
                      [Istio]
               (certificates, policies,
                traffic rules, observability)
```

Istio handles:
- **Mutual TLS (mTLS)**: all service-to-service traffic is encrypted and mutually authenticated. Services get auto-rotating certificates.
- **Authorization policies**: "Service A is allowed to call Service B, but not Service C."
- **Traffic management**: canary deployments, A/B testing, traffic mirroring.
- **Observability**: metrics, traces, logs for every service call.

### Mutual TLS (mTLS)

Every service presents a certificate to prove its identity. The other service verifies the certificate before accepting the connection. This prevents:
- Unauthorized services from calling protected services.
- Man-in-the-middle attacks on internal traffic.

Certificates are issued by a Certificate Authority (Istio's Citadel / SPIFFE/SPIRE).

---

## Deployment

### Containers and Docker

Each microservice is packaged as a Docker image containing the application and its runtime dependencies. This ensures environment consistency from development to production.

### Kubernetes

Kubernetes is the de-facto standard for orchestrating containerized microservices:

- **Pod**: smallest deployable unit; one or more containers sharing network and storage.
- **Deployment**: manages replica count, rolling updates, rollbacks.
- **Service**: stable DNS name and IP for a set of pods; load balances across them.
- **Ingress**: routes external HTTP traffic to services.
- **ConfigMap / Secret**: externalize configuration and credentials.
- **Horizontal Pod Autoscaler (HPA)**: scales pod count based on CPU/custom metrics.

### Sidecar Pattern

A sidecar container runs alongside the main application container in the same pod, extending it without modifying its code.

```
Pod
+-------------------------------+
| App Container    | Sidecar    |
| (Business Logic) | (Envoy,    |
|                  | Fluentd,   |
|                  | Vault agent|
+-------------------------------+
    Shared network namespace
```

Common sidecar uses:
- **Envoy/Istio**: transparent proxy for service mesh.
- **Fluentd/Fluent Bit**: log collection and forwarding.
- **Vault Agent**: auto-renewing secrets injection.
- **Cloud SQL Proxy**: secure DB connections without managing credentials in app.

---

## Architecture Diagrams

### Full Microservices Architecture

```
                    EXTERNAL CLIENTS
               (Web Browser, Mobile App)
                          |
                          v
              +-----------------------+
              |      API GATEWAY      |
              | Auth | Rate Limit     |
              | Routing | SSL Termination
              +-----------------------+
               |          |         |
               v          v         v
          +--------+  +--------+  +--------+
          | User   |  | Order  |  | Product|
          | Service|  | Service|  | Service|
          +--------+  +--------+  +--------+
               |          |          |
           PostgreSQL   MySQL      MongoDB
                         |
                         v
              +-------------------+
              |   Event Bus       |
              |   (Kafka)         |
              +-------------------+
               |          |         |
               v          v         v
          +--------+  +--------+  +--------+
          |Payment |  | Notif. |  |Analytics
          |Service |  | Service|  |Service |
          +--------+  +--------+  +--------+
               |
           Payment DB


     OBSERVABILITY LAYER (crosses all services)
     +------------------------------------------+
     | Jaeger (Tracing) | Prometheus (Metrics)  |
     | ELK Stack (Logs) | Grafana (Dashboards)  |
     +------------------------------------------+
```

### Service Mesh with Sidecar Proxies

```
+----------------+       +----------------+       +----------------+
| Order Service  |       | Payment Service|       | Inventory Svc  |
|  [App]         |       |  [App]         |       |  [App]         |
|  [Envoy Proxy] |<----->|  [Envoy Proxy] |<----->|  [Envoy Proxy] |
+----------------+  mTLS +----------------+  mTLS +----------------+
       |                        |                         |
       +------------------------+-------------------------+
                                |
                   +-----------------------+
                   | Istio Control Plane   |
                   | (Pilot, Citadel,      |
                   |  Galley, Telemetry)   |
                   +-----------------------+
```

---

## Real-World Examples

### Netflix — 700+ Microservices

Netflix migrated from a monolith to microservices between 2008–2012 following a major database corruption incident. Key contributions to the ecosystem:
- **Eureka**: service registry (open-sourced).
- **Hystrix**: circuit breaker (open-sourced, now in maintenance; superseded by Resilience4j).
- **Zuul**: API gateway.
- **Ribbon**: client-side load balancing.
- **Chaos Monkey**: randomly terminates services in production to validate resilience.

Netflix runs 700+ microservices handling 2+ billion API requests per day. Every service is independently deployable; the company does hundreds of deployments per day.

### Amazon — From Monolith to 2-Pizza Teams

Amazon decomposed their retail monolith in the early 2000s. Jeff Bezos mandated that every team expose its data and functionality through APIs (the "API Mandate"). Teams could not communicate with each other except through service interfaces.

This led to the creation of AWS — internally built primitives (EC2, S3, SQS) were exposed as public cloud services because they were already API-first.

### Uber — Domain-Oriented Microservice Architecture (DOMA)

Uber grew to thousands of microservices and encountered the opposite problem: too many services causing ownership ambiguity. They introduced DOMA — grouping related services into domains with clear ownership, and using a "gateway" service per domain to reduce cross-domain coupling.

Key insight: at Uber's scale (5000+ engineers), microservice granularity must be balanced against cognitive overhead. Not every function needs its own service.

---

## When NOT to Use Microservices

Microservices carry a significant **complexity tax**. They are not always the right choice.

### Avoid microservices when:
- **Small team (< 8 engineers)**: the overhead of distributed systems (service discovery, distributed tracing, separate deployments) outweighs the benefits.
- **Early-stage product**: requirements change rapidly; service boundaries chosen too early will be wrong. Start with a modular monolith.
- **Low traffic / small scale**: a single Postgres instance and a monolith handle millions of requests per day trivially.
- **No DevOps capability**: microservices require mature CI/CD, container orchestration, and observability infrastructure. Without these, microservices become a maintenance nightmare.
- **Latency is critical**: in-process function calls are nanoseconds; network calls are milliseconds. Chaining 10 services adds 10+ network round-trips.

### Consider a Modular Monolith first:
A modular monolith has clear internal module boundaries (separate packages, clear interfaces) but deploys as a single unit. When a module becomes a bottleneck or needs independent deployment, extract it into a service.

"Don't start with microservices. Start with a monolith, and when you feel the pain, extract." — Martin Fowler

---

## Tradeoffs and Considerations

| Dimension               | Benefit                                  | Cost                                         |
|-------------------------|------------------------------------------|----------------------------------------------|
| Independent deployment  | Deploy services without coordination     | Need mature CI/CD per service                |
| Fault isolation         | One service down != system down          | Partial failures are harder to reason about  |
| Technology flexibility  | Best tool for each job                   | Operational diversity increases complexity   |
| Independent scaling     | Scale only what needs scaling            | Need container orchestration (K8s)           |
| Team autonomy           | Teams move faster independently          | Need governance to prevent fragmentation     |
| Data isolation          | Independent schemas, no shared DB        | Distributed queries require API composition  |
| Distributed tracing     | End-to-end visibility                    | Must instrument every service                |
| Network latency         | Services can be co-located               | Every call is a network hop (vs in-process)  |

---

## Best Practices

### Design for Failure
Assume any dependency can fail at any time. Use timeouts on all outbound calls. Implement circuit breakers. Define fallback behavior for every external dependency.

### API Versioning
Never make breaking changes to a service API without versioning. Use URL versioning (`/v2/orders`) or header versioning. Support at least N-1 versions to allow gradual consumer migration.

### Health Checks
Every service must expose:
- `/health/live`: is the process alive? (liveness probe)
- `/health/ready`: is the service ready to accept traffic? (readiness probe)

### 12-Factor App
Follow 12-factor principles: config from environment variables, stateless processes, disposable containers, logs as event streams, etc.

### Idempotent APIs
All mutating endpoints should be idempotent (same request repeated = same result). Use a client-generated idempotency key in the request header.

### Bulkhead Pattern
Isolate resources (thread pools, connection pools) per dependency. If Dependency A becomes slow, it does not exhaust resources needed for Dependency B.

### Graceful Degradation
Identify critical vs non-critical features. If the recommendation service is down, show the checkout page without recommendations rather than failing entirely.

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement Microservices**

- **Facade** — API gateways and BFF (Backend for Frontend) services are Facades: they present a simplified, client-specific interface over the complex microservice mesh, hiding routing, aggregation, and protocol translation.
- **Proxy** — Service mesh sidecars (Envoy, Linkerd) and circuit breakers are Proxy pattern at the infrastructure level: they wrap every service-to-service call to add retries, timeouts, mTLS, load balancing, and distributed tracing.
- **Observer** — Event-driven inter-service communication (via Kafka or SNS) is Observer: services publish domain events without knowing who consumes them, enabling loose coupling and independent deployability.
- **Strategy** — Service discovery (client-side, server-side, DNS-based), circuit breaker policies (error rate threshold, half-open retry interval), and retry strategies (exponential backoff, jitter) are interchangeable Strategy implementations.

---

## Case Study: E-Commerce Platform Migration

### Context
A monolithic Java application handles an e-commerce platform. During Black Friday, the recommendation engine overwhelms the database, causing the entire site to go down. The team decides to migrate to microservices.

### Migration Strategy: Strangler Fig Pattern

Rather than a "big bang" rewrite, new functionality is built as microservices while the monolith handles the rest. Traffic is gradually redirected from the monolith to new services.

```
Phase 1: Identify bounded contexts
  User Management | Product Catalog | Order Processing
  Cart | Payments | Recommendations | Search | Notifications

Phase 2: Extract Recommendation Service (bottleneck first)
  Monolith still runs | New Recommendation Service deployed
  API Gateway routes /recommendations to new service

Phase 3: Extract Product Catalog Service
  (High read traffic, can be cached independently)

Phase 4: Extract Order + Payment Services
  (Core business logic, most complex — left for later)

Phase 5: Monolith now only handles legacy functionality
  Eventually decommissioned
```

### Resulting Architecture

```
[Browser / App]
       |
  [API Gateway: Kong]
  /       |       \        \
User   Product   Order    Search
Svc    Catalog   Svc       (Elasticsearch)
 |        |        |
Users   Products  Orders
DB(PG)  DB(Mongo) DB(MySQL)
                    |
             [Kafka: order-events]
                    |
             +------+------+
             |             |
          Notif.      Analytics
          Svc          Svc
```

### Key Outcomes
- Recommendation service scaled independently during Black Friday (10x replicas).
- Product catalog served from Redis cache; eliminated 80% of DB load.
- Order + Payment services deployed independently; 3 deploys per day (was 1 per month).
- Full distributed tracing via Jaeger; mean time to diagnose issues dropped from hours to minutes.

---

## Interview Questions

**Q1: What is the difference between microservices and SOA?**
SOA uses a heavyweight Enterprise Service Bus (ESB) for communication, large coarse-grained services, and typically shared databases. Microservices use lightweight protocols (REST, gRPC, events), small single-purpose services, and database-per-service. Microservices are often considered a refinement of SOA principles.

**Q2: How do you handle distributed transactions across multiple microservices?**
Use the Saga pattern instead of 2-Phase Commit. A saga is a sequence of local transactions with compensating transactions on failure. Use orchestration (central coordinator) for complex flows or choreography (event-driven) for simpler ones.

**Q3: What is the circuit breaker pattern and why is it needed?**
A circuit breaker prevents a failing service from receiving requests when it is unhealthy, returning a fallback immediately instead. States: Closed (normal), Open (fast-fail), Half-Open (probe). Without it, slow downstream services cause thread pool exhaustion and cascading failure.

**Q4: How would you implement service discovery in a Kubernetes environment?**
Kubernetes provides built-in DNS-based service discovery. Each Service resource gets a stable DNS name (`service-name.namespace.svc.cluster.local`). kube-proxy routes traffic to healthy pods. No external registry needed.

**Q5: What is the database-per-service pattern and what are its tradeoffs?**
Each service owns its own database; no other service accesses it directly. Benefit: independent scaling, schema evolution, technology choice. Cost: no cross-service JOINs; need API composition or read models (CQRS) for multi-service queries.

**Q6: How would you design an API gateway?**
The API gateway is the single entry point handling SSL termination, authentication (validate JWT), rate limiting (token bucket/sliding window), routing (path-based to services), and optional request aggregation. Use Kong, AWS API Gateway, or Envoy as the implementation.

**Q7: What is a service mesh and when would you use it?**
A service mesh (Istio/Envoy) manages service-to-service communication as infrastructure via sidecar proxies. Use it when you need: mutual TLS for all service traffic, fine-grained traffic policies (canary, retries, timeouts), and consistent observability without changing application code. Justified at 20+ services.

**Q8: How does distributed tracing work?**
A unique trace ID is generated at the API gateway and propagated as a header through all service calls. Each service creates a span (start time, end time, service name, tags) and reports it to a tracing backend (Jaeger, Zipkin). The backend stitches spans by trace ID into a request timeline.

**Q9: What is the Strangler Fig pattern?**
A migration pattern where new functionality is built as separate services while the monolith remains. An API gateway or proxy routes requests to either the monolith or new services based on path. Over time, more traffic moves to new services until the monolith is replaced (strangled).

**Q10: How do you handle authentication and authorization in microservices?**
Use a centralized Identity Provider (Auth0, Keycloak, AWS Cognito). The API gateway validates JWT tokens on every request. Downstream services trust the gateway and read claims from the token. For service-to-service auth, use mutual TLS (service mesh) or service account tokens.

**Q11: What is the Bulkhead pattern?**
Isolate resources (thread pools, connection pools) per external dependency. If Service A calls both Service B and Service C, use separate thread pools for each. A slowdown in Service B cannot exhaust threads needed for Service C calls.

**Q12: When should you NOT migrate to microservices?**
When the team is small (< 8 engineers), the product is in early-stage with changing requirements, there is no DevOps infrastructure (CI/CD, K8s, observability), or latency budgets are tight. The complexity tax of distributed systems is only justified when the pain of the monolith exceeds it.
