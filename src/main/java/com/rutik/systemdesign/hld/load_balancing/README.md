# Load Balancing

## Table of Contents
1. [Concept Overview](#concept-overview)
2. [Core Principles](#core-principles)
3. [Types and Strategies](#types-and-strategies)
4. [Architecture Diagrams](#architecture-diagrams)
5. [How It Works](#how-it-works)
6. [Real-World Examples](#real-world-examples)
7. [Tradeoffs](#tradeoffs)
8. [When to Use](#when-to-use)
9. [When NOT to Use](#when-not-to-use)
10. [Common Pitfalls](#common-pitfalls)
11. [Technologies and Tools](#technologies-and-tools)
12. [Interview Questions](#interview-questions)
13. [Best Practices](#best-practices)
14. [Metrics and Monitoring](#metrics-and-monitoring)
15. [Case Study](#case-study)

---

## Intuition

> **One-line analogy**: A load balancer is like a restaurant host who directs arriving customers to tables — ensuring no single waiter is overwhelmed while others stand idle.

**Mental model**: Without load balancing, all traffic hits one server — it becomes the bottleneck and single point of failure. A load balancer sits in front of a pool of servers, distributing incoming requests so each server handles a fair share. Health checks ensure unhealthy servers are removed from the pool. Round-robin is the simplest strategy; consistent hashing is best for stateful workloads.

**Why it matters**: Load balancers are the entry point of every large-scale web system. They enable horizontal scaling (add more servers without changing clients), provide redundancy (one server dies, traffic reroutes), and enable zero-downtime deployments (remove one server, update it, re-add).

**Key insight**: L4 (transport layer) load balancers are fast but dumb — they see only IP/port. L7 (application layer) load balancers are smart but slower — they see HTTP headers, URL paths, and cookies, enabling sticky sessions, path-based routing, and SSL termination.

---

## Concept Overview

A load balancer is a component that distributes incoming network traffic across multiple backend servers. Its core purpose is to ensure no single server bears too much demand, increasing responsiveness and availability of applications.

**Why it matters:**
- A single server has finite CPU, memory, and network capacity
- Without distribution, one server becomes the bottleneck and eventually fails under load
- Load balancers enable horizontal scaling by making a pool of servers look like one endpoint
- They provide automatic failover — if a server dies, traffic routes around it

Load balancing operates at multiple layers of the network stack. Choosing the right layer and algorithm directly affects performance, cost, and the capabilities you can build on top.

**Key responsibilities of a load balancer:**
- Traffic distribution across healthy backend instances
- Health checking — continuously detecting and removing failed servers
- SSL termination — handling TLS encryption/decryption so backends don't have to
- Connection persistence — ensuring stateful sessions hit the same backend (sticky sessions)
- Request routing — routing based on URL path, headers, cookies, or content type

---

## Core Principles

### 1. Health Awareness
The load balancer must know which backends are healthy. It runs periodic health checks (TCP ping, HTTP probe) and removes unhealthy instances from rotation immediately.

### 2. Algorithm-Driven Distribution
Traffic is distributed according to a configurable algorithm. The choice of algorithm determines whether load is distributed evenly, whether connection cost is considered, or whether client identity drives routing.

### 3. Transparency
From the client's perspective, the load balancer is the server. The existence of the backend pool is invisible. This abstraction enables backend changes (additions, removals, upgrades) without client impact.

### 4. Session Persistence
When applications have server-side state, the load balancer must route a client's subsequent requests to the same server. This is done via sticky sessions (cookie-based or IP-hash-based).

### 5. Failure Isolation
The load balancer is the first line of defense against backend failures. It detects failures through health checks and stops sending traffic to failed instances, improving overall availability.

---

## Types and Strategies

### Load Balancing Algorithms

#### Round Robin
Requests are distributed sequentially across servers in a loop: S1 → S2 → S3 → S1 → ...

- **Best for**: Servers with identical hardware and similar request costs
- **Problem**: Ignores server load. A slow, CPU-bound request on S1 means S1 is overloaded while S2 and S3 are idle
- **Variant**: Weighted Round Robin — assign more traffic to more powerful servers

#### Least Connections
Route each new request to the server with the fewest active connections.

- **Best for**: Workloads with variable request duration (e.g., video streaming, file uploads)
- **Problem**: Slight overhead to track connection counts; not ideal for very short-lived connections
- **Variant**: Weighted Least Connections — factor in server capacity along with connection count

#### Least Response Time
Route to the server with the fewest connections AND the lowest average response time.

- **Best for**: Latency-sensitive applications where backend performance varies
- **Problem**: More complex to implement; requires active response time measurement

#### IP Hash (Source IP Affinity)
Hash the client IP address to determine which server handles the request. The same client IP always maps to the same server (as long as the pool doesn't change).

- **Best for**: Session persistence without cookies; consistent routing for a given client
- **Problem**: Poor distribution if many clients share a NAT IP (e.g., corporate networks); adding/removing servers changes the entire hash mapping

#### Consistent Hashing
A more sophisticated version of IP hash. Servers are placed on a "hash ring." Adding or removing a server only remaps a small fraction of clients.

- **Best for**: Cache servers, where you want the same client to always hit the same cache node
- **Problem**: More complex to implement than simple IP hash

#### Random
Route requests to a randomly selected backend.

- **Best for**: Simple, roughly even distribution without any state
- **Problem**: Can lead to uneven distribution in small pools by chance

### Layer 4 vs Layer 7 Load Balancing

#### Layer 4 (Transport Layer)
Operates on IP and TCP/UDP. Routes based on source/destination IP and port. Does not inspect packet contents.

- **Pros**: Extremely fast, low latency, no decryption needed
- **Cons**: Cannot route based on application content (URL, headers, cookies)
- **Examples**: AWS NLB, HAProxy TCP mode

```
Client -> [L4 LB] -> Backend
           |
           Sees: src_ip, dst_ip, port
           Does NOT see: HTTP headers, URL path, cookies
```

#### Layer 7 (Application Layer)
Operates on the full HTTP/HTTPS request. Can inspect headers, URL paths, cookies, and body content.

- **Pros**: Content-based routing (route /api/* differently from /static/*), SSL termination, cookie-based sticky sessions, HTTP rewrites
- **Cons**: Slightly higher latency due to full request parsing; must decrypt HTTPS
- **Examples**: AWS ALB, Nginx, HAProxy HTTP mode

```
Client -> [L7 LB] -> Backend
           |
           Sees: HTTP method, URL path, headers, cookies, body
           Can do: path-based routing, header injection, auth offload
```

### Sticky Sessions (Session Persistence)

#### Cookie-Based Sticky Sessions
Load balancer inserts a cookie (e.g., `SERVERID=s1`) into the first response. Subsequent requests from that client include the cookie, and the load balancer routes to the server specified in the cookie.

- Survives IP changes (mobile clients switching from WiFi to cellular)
- Works with any load balancing algorithm underneath

#### IP-Hash Sticky Sessions
Client IP is hashed to determine the backend. Consistent for a given IP.

- Does not require cookie support
- Breaks if client changes IP; poor distribution behind NAT

---

## Architecture Diagrams

### Basic Load Balancer Architecture

```
                      [ Internet ]
                           |
                    +------v------+
                    |   Load      |
                    |  Balancer   |
                    | (HAProxy /  |
                    |  Nginx /    |
                    |  AWS ALB)   |
                    +--+---+---+--+
                       |   |   |
              +--------+   |   +--------+
              |             |            |
        +-----v--+    +-----v--+   +-----v--+
        |  Web   |    |  Web   |   |  Web   |
        | Server |    | Server |   | Server |
        |   S1   |    |   S2   |   |   S3   |
        +--------+    +--------+   +--------+
```

### L7 Content-Based Routing

```
                    +------------------+
                    |   L7 Load        |
                    |   Balancer       |
                    | (AWS ALB / Nginx) |
                    +--+--------+------+
                       |        |
           +-----------+        +-----------+
           |  /api/*                        |  /static/*
           v                                v
    +------+------+                  +------+------+
    | API Servers |                  | Static File |
    |  (Node.js)  |                  |  Servers    |
    | S1  S2  S3  |                  |  S4  S5     |
    +-------------+                  +-------------+
```

### Global Load Balancing (Multi-Region)

```
        Users (US)      Users (EU)      Users (APAC)
             |               |                |
             +-------+-------+--------+-------+
                     |                |
              +------v------+  +------v------+
              | Global DNS  |  | Anycast IP  |
              | (Route 53)  |  | (Cloudflare)|
              +------+------+  +------+------+
                     |                |
          +----------+                +----------+
          |                                      |
    +-----v------+                        +-----v------+
    | US-EAST-1  |                        | EU-WEST-1  |
    | Regional   |                        | Regional   |
    | Load Bal.  |                        | Load Bal.  |
    +-----+------+                        +-----+------+
          |                                      |
    +-----+-----+                         +------+-----+
    | App Pool  |                         | App Pool   |
    | US servers|                         | EU servers |
    +-----------+                         +------------+
```

### Health Check Flow

```
Load Balancer
    |
    |-- every 10s --> GET /health -> S1  (200 OK) -> HEALTHY, in rotation
    |-- every 10s --> GET /health -> S2  (timeout) -> mark 1/3 failures
    |-- every 10s --> GET /health -> S2  (timeout) -> mark 2/3 failures
    |-- every 10s --> GET /health -> S2  (timeout) -> mark 3/3 failures -> UNHEALTHY, removed
    |
    | ... S2 is restarted ...
    |
    |-- every 10s --> GET /health -> S2  (200 OK) -> mark 1/2 healthy
    |-- every 10s --> GET /health -> S2  (200 OK) -> mark 2/2 healthy -> HEALTHY, back in rotation
```

---

## How It Works

### Request Lifecycle Through a Load Balancer

1. **DNS Resolution**: Client resolves `api.example.com` — DNS returns the load balancer's IP
2. **TCP Connection**: Client opens a TCP connection to the load balancer
3. **Algorithm Selection**: Load balancer applies its routing algorithm to select a backend server
4. **Request Forwarding**: L7 LB parses the full HTTP request, optionally modifies headers (adds `X-Forwarded-For`), and forwards to the selected backend
5. **Backend Response**: Backend processes the request and returns the response to the load balancer
6. **Response Forwarding**: Load balancer forwards the response to the client
7. **Connection Management**: Connection may be kept alive (persistent connections) or closed

### SSL Termination at the Load Balancer

The load balancer decrypts HTTPS traffic from clients, then communicates with backends over plain HTTP (on the internal network). Benefits:
- Backend servers don't need TLS certificates or the CPU overhead of encryption
- Centralized certificate management at the load balancer
- The load balancer can inspect decrypted request content for routing decisions

Note: If end-to-end encryption is required (e.g., PCI compliance), the load balancer can re-encrypt before forwarding to backends (SSL passthrough or re-encryption).

### Health Check Mechanics

The load balancer sends periodic health checks to each backend:
- **TCP health check**: Opens a TCP connection to the backend port. Success = server is up
- **HTTP health check**: Sends `GET /health` and expects an HTTP 2xx response
- **Custom health check**: Application-specific logic (check DB connection, verify cache is warm)

A backend is marked unhealthy after N consecutive failures (configurable). It is marked healthy again after M consecutive successes. This hysteresis prevents flapping.

---

## Real-World Examples

### Google
- Google Front End (GFE) handles all external traffic before it reaches any Google service
- GFE is a globally distributed L7 load balancer / reverse proxy running on thousands of machines
- Uses Maglev (Google's software-based load balancer) for consistent hashing across backend pools
- Maglev handles 1M+ packets per second per server using ECMP (Equal Cost Multi-Path) routing

### AWS (Amazon Elastic Load Balancing)
- ALB (Application Load Balancer): L7, content-based routing, WebSocket support, targets ECS/Lambda
- NLB (Network Load Balancer): L4, ultra-low latency, millions of RPS, static IP support
- CLB (Classic, legacy): Simple L4/L7, being phased out
- AWS uses its own Hyperplane network service as the backend for NLB, capable of handling millions of flows

### Netflix
- Netflix uses Eureka (service discovery) + Ribbon (client-side load balancing) in its microservices
- Client-side load balancing means each service knows about all instances of its dependencies and makes routing decisions locally — no central load balancer hop
- Zuul is Netflix's API gateway that acts as an L7 load balancer for external traffic into the microservices cluster

### Cloudflare
- Cloudflare's global anycast network means the load balancer is geographically distributed
- A DNS request resolves to the nearest Cloudflare PoP (Point of Presence), not a single server
- Within each PoP, traffic is distributed to backend servers using least-connections
- Cloudflare Load Balancer supports active health checks, failover, and geo-steering

---

## Tradeoffs

| Factor | L4 Load Balancing | L7 Load Balancing |
|--------|-------------------|-------------------|
| Performance | Higher (no content parsing) | Slightly lower |
| Routing granularity | IP/port only | URL, headers, cookies |
| SSL termination | Optional (pass-through) | Natural fit |
| Cost | Lower | Slightly higher |
| Observability | Limited (IP/port metrics) | Rich (request-level metrics) |

### What You Gain
- Higher availability — no single point of failure in the server tier
- Horizontal scalability — add backend servers without changing the client-facing endpoint
- Operational flexibility — replace, upgrade, or scale backends without downtime (rolling deploys)
- Security — backends are not directly exposed to the internet

### What You Lose
- Added network hop — small latency cost (typically 1-2ms for L4, 2-5ms for L7)
- The load balancer itself can become a SPOF if not made redundant
- Complexity — SSL certificates, health check configuration, algorithm tuning
- Cost — managed load balancers (AWS ALB) have hourly charges plus data processing charges

---

## When to Use

- **Serving more traffic than one server can handle** — the primary use case
- **High availability is required** — zero-tolerance for a single server failure taking down the service
- **Rolling deployments** — replace backend instances one at a time while keeping the service up
- **Geographic load distribution** — route users to the nearest regional server pool
- **A/B testing or canary deployments** — route a percentage of traffic to a new version
- **SSL offloading** — centralize TLS management at the load balancer

---

## When NOT to Use

- **Single-server deployments during early development** — adds unnecessary complexity
- **Internal microservice-to-microservice calls at low volume** — direct service discovery may be simpler
- **When client-side load balancing suffices** — e.g., gRPC clients with built-in load balancing

---

## Common Pitfalls

### 1. The Load Balancer as a SPOF
A single load balancer handling all traffic is itself a single point of failure. Always run load balancers in an active-active or active-passive HA pair. Managed solutions (AWS ALB) handle this automatically.

### 2. Sticky Sessions Defeating the Purpose of Scaling
If sticky sessions route all users of a popular account to one server, that server is overloaded while others are idle. The real fix is to make the application stateless.

### 3. Health Checks That Don't Reflect True Health
A health check endpoint that returns 200 even when the database is down provides false confidence. Health checks should verify all critical dependencies.

### 4. Slow Health Check Intervals
If the health check runs every 60 seconds, a failed server serves bad traffic for up to 60 seconds. Use short intervals (5-10s) with a failure threshold of 2-3 for fast failover.

### 5. Not Accounting for Draining
When deregistering a server (e.g., for deployment), abruptly stopping it kills in-flight requests. Configure connection draining — give in-flight requests time to complete before removing the instance.

### 6. Backend Servers Seeing the Load Balancer's IP
Without `X-Forwarded-For` headers, backend servers see the load balancer's internal IP as the client IP. This breaks rate limiting, geo-blocking, and logging. Always configure the LB to pass the real client IP.

### 7. Ignoring the Long-Tail
Round Robin routes evenly by request count but not by cost. One request that takes 30 seconds blocks a connection slot. Least Connections handles this better for workloads with variable request duration.

---

## Technologies and Tools

### Software Load Balancers
| Tool | Layer | Key Feature |
|------|-------|-------------|
| Nginx | L7 | Most popular; doubles as web server and reverse proxy |
| HAProxy | L4 + L7 | Extremely fast; excellent for TCP and HTTP; battle-tested |
| Envoy | L7 | Modern; used in service meshes (Istio); gRPC support |
| Traefik | L7 | Dynamic configuration; native Docker/Kubernetes integration |
| Caddy | L7 | Automatic HTTPS; simple config |

### Cloud Managed Load Balancers
| Service | Layer | Key Feature |
|---------|-------|-------------|
| AWS ALB | L7 | Content-based routing, WAF integration, Lambda targets |
| AWS NLB | L4 | Ultra-low latency, static IPs, millions of RPS |
| GCP Cloud Load Balancing | L4 + L7 | Global, anycast-based, single IP worldwide |
| Azure Load Balancer | L4 | Regional, fast |
| Azure Application Gateway | L7 | WAF, URL-based routing |
| Cloudflare LB | L7 | Global with health checks and geo-steering |

### Service Mesh Load Balancing
| Tool | Description |
|------|-------------|
| Istio + Envoy | Service mesh with per-service load balancing, circuit breaking, retries |
| Linkerd | Lightweight service mesh with automatic L7 load balancing |
| Consul Connect | HashiCorp's service mesh with built-in health-aware load balancing |

---

## Interview Questions

**Q1: What is the difference between L4 and L7 load balancing?**
L4 operates at the TCP/IP layer — it routes based on IP address and port without inspecting request content. L7 operates at the HTTP layer — it can route based on URL path, headers, cookies, and body. L7 is more flexible; L4 is faster.

**Q2: What algorithms do load balancers use to distribute traffic?**
Round Robin (sequential), Weighted Round Robin, Least Connections, Weighted Least Connections, IP Hash (source affinity), Least Response Time, Random, and Consistent Hashing. The choice depends on whether servers are homogeneous, whether sessions matter, and whether request duration varies.

**Q3: What is a sticky session and when would you use it?**
A sticky session (session persistence) routes all requests from a given client to the same backend server. It's needed when the application stores session state server-side (e.g., in memory). The better long-term solution is to make the application stateless, but sticky sessions work as a bridge.

**Q4: How does a load balancer detect that a backend is unhealthy?**
Through health checks — periodic probes (TCP or HTTP) sent to each backend. If a backend fails N consecutive checks, it's marked unhealthy and removed from the rotation. After M consecutive successes, it's marked healthy and traffic resumes.

**Q5: What is SSL termination and why is it done at the load balancer?**
SSL termination means the load balancer decrypts HTTPS traffic and forwards plain HTTP to backends. This offloads CPU-intensive cryptographic operations from backend servers, centralizes certificate management, and allows the LB to inspect decrypted content for routing.

**Q6: How do you prevent the load balancer itself from being a single point of failure?**
Run multiple load balancer instances in active-active (all handle traffic) or active-passive (one standby, promoted on failure) configuration. Use a virtual IP (VIP) with VRRP/HSRP, or use a cloud-managed load balancer (AWS ALB is inherently highly available across AZs).

**Q7: What is connection draining?**
Connection draining (deregistration delay) is a grace period during which the load balancer stops sending new requests to a server being removed, but waits for in-flight requests to complete before fully removing it. This enables zero-downtime deployments.

**Q8: Explain the difference between client-side and server-side load balancing.**
Server-side: a central load balancer intercepts all traffic and routes it. Client-side: the client (or a sidecar) knows all server instances and makes routing decisions locally. Client-side (used by Netflix Ribbon, gRPC) eliminates the central LB hop but requires clients to maintain server lists.

**Q9: How would you design a load balancer for WebSocket connections?**
WebSockets are long-lived connections — once established, traffic flows bidirectionally on the same connection. The load balancer must support WebSocket upgrade (L7 feature) and not close idle connections. Sticky sessions are needed to ensure WebSocket traffic stays on the established backend connection.

**Q10: What is the role of a load balancer in a blue-green deployment?**
In a blue-green deployment, the new version (green) is deployed alongside the old (blue). The load balancer is switched to route traffic to green. If green has issues, the LB is switched back to blue instantly. The load balancer is the routing control plane for zero-downtime deployments.

**Q11: What is consistent hashing and why is it better than simple IP hash for caching?**
Consistent hashing places servers on a virtual ring. Each key maps to the nearest server on the ring. When a server is added or removed, only K/N keys need remapping (K = keys, N = servers), compared to simple hash where all keys remap. This minimizes cache misses when the pool changes.

---

## Best Practices

1. **Always run load balancers in HA pairs.** A single load balancer is a SPOF. Use active-active or deploy behind a managed service.
2. **Use meaningful health check endpoints.** `/health` should verify DB connectivity, cache availability, and any critical dependencies — not just return 200.
3. **Set aggressive health check intervals.** 5-10 second intervals with a threshold of 2-3 failures for fast failover (30-second detection max).
4. **Always configure connection draining.** 30-60 second draining period prevents request drops during deployments.
5. **Pass real client IPs.** Configure `X-Forwarded-For` / `X-Real-IP` headers so backends can log, rate-limit, and geo-filter correctly.
6. **Prefer Least Connections for variable workloads.** If request duration varies significantly, Round Robin creates hot spots.
7. **Terminate SSL at the load balancer.** Simplifies certificate management and reduces backend CPU load.
8. **Monitor backend response time distribution.** P99 latency per backend reveals slow instances that should be scaled or debugged.
9. **Avoid sticky sessions where possible.** Make the application stateless; use sticky sessions only as a last resort.
10. **Log everything at the load balancer.** Access logs with upstream response time, backend IP, and client IP are invaluable for debugging.

---

## Metrics and Monitoring

### Load Balancer Metrics
| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| Request Rate | RPS through the LB | Sudden drop > 20% |
| Active Connections | Open connections | > 80% of max |
| Healthy Host Count | Backends in rotation | Any drop |
| 4xx Rate | Client error rate | > 5% |
| 5xx Rate | Backend error rate | > 0.5% |
| Target Response Time | P50/P95/P99 backend latency | P99 > SLA target |
| Processed Bytes | Throughput in/out | > 80% of bandwidth |

### Per-Backend Metrics
| Metric | Description |
|--------|-------------|
| Request Count | Per-backend RPS (check for uneven distribution) |
| Error Rate | Per-backend 5xx rate |
| Response Time | Per-backend P99 latency |
| Connection Count | Active connections per backend |
| Health Check Status | Pass/fail per backend |

### Monitoring Tools
- **AWS ALB Access Logs** -> S3 -> Athena for query analysis
- **Nginx access logs** -> Filebeat -> Elasticsearch -> Kibana
- **HAProxy stats socket** -> Prometheus exporter -> Grafana
- **Datadog Load Balancer dashboards** — pre-built for AWS ALB/NLB

---

## Cross-Perspective: LLD Connections

**LLD View — Design Patterns That Implement Load Balancing**

- **Strategy** — Load balancing algorithms (round-robin, weighted round-robin, least-connections, random, consistent-hash) are the textbook Strategy pattern: interchangeable algorithms behind a `LoadBalancingStrategy` interface, selected per-deployment.
- **Proxy** — The load balancer itself is a Proxy: clients connect to the LB address; the LB forwards requests to backends transparently. Layer 4 (TCP) and Layer 7 (HTTP) LBs are both Proxy variants.
- **Observer** — Health check results are broadcast to observer subscribers. When a backend fails its health check, the LB removes it from the pool and notifies alert systems — reactive, not polling.
- **Iterator** — Round-robin selection is a circular Iterator over the server pool: it cycles through servers, advancing the pointer on each request and wrapping around at the end.

---

## Case Study: Load Balancing a High-Traffic E-Commerce Platform

### Scenario
An e-commerce site with 500 app servers needs to handle 50,000 requests per second at peak (Black Friday). They also need to route `/api/*` differently from `/static/*`, support zero-downtime deployments, and handle WebSocket connections for real-time inventory updates.

### Architecture Decision

**Layer**: L7 (AWS ALB) — needed for content-based routing and WebSocket support.

**Algorithms**:
- `/api/*` — Least Connections (API request duration varies; checkout takes 500ms, product page takes 50ms)
- `/static/*` — Round Robin (all requests are identical cost; fast, no need for connection tracking)
- WebSocket endpoint — Sticky Sessions with cookie affinity (WebSocket connections are long-lived)

### Load Balancer Configuration

```
ALB Listener: HTTPS :443
  |
  +-- Rule 1: Path is /api/*       -> Target Group: api-servers   (Least Connections)
  |
  +-- Rule 2: Path is /ws/*        -> Target Group: ws-servers    (Sticky Sessions)
  |
  +-- Rule 3: Path is /static/*    -> Target Group: static-servers (Round Robin)
  |
  +-- Default Rule                 -> Target Group: web-servers   (Round Robin)

Health Check:
  - Protocol: HTTP
  - Path: /health (checks: DB connection, Redis ping)
  - Interval: 10s
  - Healthy threshold: 2
  - Unhealthy threshold: 3

Connection Draining: 60 seconds (allows in-flight requests to complete during deployments)
```

### Deployment Process (Zero Downtime)
1. Deploy new version to a new target group
2. ALB: Shift 5% of traffic to new target group (canary)
3. Monitor error rate and latency for 10 minutes
4. If healthy, shift 50% then 100%
5. Deregister old target group (connection draining ensures no dropped requests)

### Black Friday Scaling
- Auto Scaling Group behind the ALB: min 10, max 500 instances
- Scale-out trigger: ALB `TargetResponseTime` P99 > 200ms for 3 consecutive minutes
- Pre-warm: scheduled scaling adds 200 instances at 11:45pm on Thanksgiving
- Result: handled 52,000 RPS peak with P99 < 180ms and zero downtime
