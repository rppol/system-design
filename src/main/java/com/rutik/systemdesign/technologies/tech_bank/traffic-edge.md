# Traffic & resilience — technology bank

<!-- tech-bank tier: traffic-edge -->

The 91 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Traffic & resilience** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### Amazon VPC Lattice
**Short:** AWS managed application networking that connects and authorizes services across VPCs and accounts without sidecars.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2, security/authentication-and-identity @3

### API Gateway
**Short:** AWS API Gateway: managed HTTP front door doing routing, authorization, throttling and Lambda integration.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/rate-limiting-and-resilience @2, platform-delivery/cloud-platform-and-cost @3

### API Gateway request quotas
**Short:** Per-client request quotas enforced at the gateway as admission control, shedding load before it reaches services.
**Kind:** concept
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/api-gateway @2

### Apigee
**Short:** Google Cloud's full-lifecycle API gateway and management platform: policies, quotas, keys and developer portal.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, platform-delivery/cloud-platform-and-cost @3

### AWS ALB
**Short:** AWS Application Load Balancer: L7 content-based routing to targets including Lambda, with WAF and TLS integration.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @3

### AWS API Gateway
**Short:** Managed AWS ingress that fronts services with auth, throttling quotas, request mapping and usage plans.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/rate-limiting-and-resilience @2, platform-delivery/cloud-platform-and-cost @3

### AWS NLB
**Short:** AWS Network Load Balancer: L4 TCP/UDP distribution with static IPs, ultra-low latency and millions of RPS.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

### AWS Route53 health checks
**Short:** Route 53 endpoint probes that drive DNS failover, withdrawing records for an unhealthy region or origin.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/rate-limiting-and-resilience @2, data-access/replication-ha-and-backup @3

### Azure API Management
**Short:** Microsoft's managed API gateway: routing, policy, authentication, quotas, versioning and a developer portal.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, platform-delivery/cloud-platform-and-cost @3

### Azure Application Gateway
**Short:** Azure's L7 load balancer with URL-path routing, TLS termination and an optional web application firewall.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2, platform-delivery/cloud-platform-and-cost @3

### Azure DNS
**Short:** Azure's managed authoritative DNS service for hosting zones and records.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2

### Azure Load Balancer
**Short:** Azure's regional L4 load balancer distributing TCP/UDP flows across backend VMs and scale sets.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

### Bucket4j
**Short:** Java token-bucket rate limiter, in-memory or distributed over Redis/Hazelcast/JCache.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/distributed-cache @3

### Caddy
**Short:** Go web server and reverse proxy with automatic HTTPS certificates, HTTP/3 and a very small config file.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, apis-frameworks/web-framework-and-http-client @3, security/secrets-and-cryptography @3

### Cilium Service Mesh
**Short:** eBPF-based, sidecar-free service mesh providing L3-L7 policy, mTLS and traffic control in the kernel.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2, runtime-systems/io-networking-and-syscalls @3

### circuitbreaker
**Short:** Lightweight Python decorator that opens a circuit after repeated failures, wrapping sync or async callables.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1

### Cloud DNS
**Short:** Google Cloud managed authoritative DNS with routing policies and health checks for discovery and failover.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2, runtime-systems/io-networking-and-syscalls @3

### Cloud Endpoints
**Short:** Google Cloud's API front door: authentication, API keys, quotas and throttling in front of backend services.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1

### Cloudflare LB
**Short:** Cloudflare's global L7 load balancer with health checks, geo-steering and failover across origins.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, caching/http-and-cdn-cache @3

### Cloudflare Rate Limiting
**Short:** Edge rate limiting with geo rules and DDoS protection; counters are per data center, not global.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/http-and-cdn-cache @3, traffic-edge/api-gateway @3

### Consul
**Short:** Raft-backed service discovery, health checking, KV config store and service mesh for polyglot services.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, data-access/transactions-and-consistency @2, data-stores/key-value-and-embedded @3

### Consul Connect
**Short:** Consul's service mesh: sidecar proxies with automatic mTLS, intentions policy and health-aware balancing.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2, security/authentication-and-identity @3

### Consul service mesh
**Short:** Consul's mesh layer: sidecar proxies, mTLS between services, intention-based policy and service discovery.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @3, security/secrets-and-cryptography @3

### CoreDNS
**Short:** Plugin-based DNS server that is the default Kubernetes service-discovery resolver, often with a per-node cache.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2, runtime-systems/io-networking-and-syscalls @3

### dig
**Short:** Command-line DNS lookup tool that shows the full resolution chain, record types and TTLs.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, runtime-systems/io-networking-and-syscalls @2

### dnspython
**Short:** Python DNS toolkit for programmatic A/AAAA/CNAME/MX/SRV lookups, zone parsing and custom resolvers.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/service-mesh-and-discovery @1, runtime-systems/io-networking-and-syscalls @2

### dnsutils pod
**Short:** Throwaway debug pod with dig and nslookup, used to test CoreDNS resolution from inside a Kubernetes cluster.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2, runtime-systems/io-networking-and-syscalls @2

### ECS Service Connect
**Short:** AWS ECS built-in service discovery and sidecar mesh giving named endpoints, mTLS and traffic telemetry.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @3

### ELB
**Short:** AWS Elastic Load Balancing: managed L4 (NLB) and L7 (ALB) load balancers with health checks and TLS termination.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

### Envoy
**Short:** High-performance L7 proxy used as service-mesh sidecar, API gateway, load balancer and rate-limit enforcer.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @1, traffic-edge/api-gateway @2, traffic-edge/rate-limiting-and-resilience @2

### Envoy as edge proxy
**Short:** Running Envoy at the edge so the same proxy that powers the mesh sidecars also terminates and routes external traffic.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2, traffic-edge/service-mesh-and-discovery @2

### Envoy Proxy
**Short:** L7 proxy used as sidecar or edge: load balancing, retries, rate limits, gRPC-Web transcoding and HTTP/3 termination.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @2, traffic-edge/api-gateway @2, apis-frameworks/rpc-graphql-and-streaming @3, traffic-edge/rate-limiting-and-resilience @3

### Envoy/Istio outlier detection
**Short:** Mesh-level circuit breaking that ejects an upstream host from the pool after consecutive errors.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/service-mesh-and-discovery @2

### Eureka
**Short:** Netflix service registry where JVM services register and clients look each other up to load-balance.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/service-mesh-and-discovery @1

### EXPIRE
**Short:** Redis command setting a TTL on a key; the basis of fixed-window rate-limit counters and cache expiry.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/distributed-cache @2

### Failsafe
**Short:** Lightweight Java resilience library: retry, circuit breaker, timeout, hedge and fallback policies.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1

### fastapi-limiter
**Short:** Redis-backed FastAPI rate limiter exposed as a Depends() dependency, safe across multiple app instances.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/distributed-cache @3

### GatewayFilter
**Short:** Spring Cloud Gateway filter with ordered pre/post hooks around the proxied call: rate limits, rewrites, breakers.
**Kind:** api
**Lang:** java
**Roles:** traffic-edge/api-gateway @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

### GCP Apigee
**Short:** Google Cloud's managed API management gateway: proxies, quotas, key/OAuth security, monetization and developer portal.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/rate-limiting-and-resilience @3, platform-delivery/cloud-platform-and-cost @3

### GCP Cloud Load Balancing
**Short:** Google Cloud's global anycast L4/L7 load balancer serving one IP worldwide with regional backends.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

### Global Accelerator
**Short:** AWS anycast edge service entering traffic at the nearest PoP over the AWS backbone, with fast regional failover.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

### Guava RateLimiter
**Short:** Guava's in-process token-bucket limiter that smooths or blocks calls, with an optional warm-up ramp.
**Kind:** api
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1

### HAProxy
**Short:** High-performance L4/L7 TCP and HTTP load balancer with health checks, widely used in front of apps and databases.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/rate-limiting-and-resilience @3, data-access/replication-ha-and-backup @3

### HAProxy stick-table
**Short:** HAProxy in-memory counter table keyed by IP or header, used for connection and request rate limiting.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/proxy-and-load-balancer @2

### ingress-nginx
**Short:** Kubernetes Ingress controller built on NGINX for L7 routing, TLS termination and host/path rules.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2, platform-delivery/kubernetes-and-orchestration @3

### Istio
**Short:** Kubernetes service mesh: sidecar traffic routing, canary weighting, retries/circuit breaking, mTLS, all config-driven.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/proxy-and-load-balancer @2, security/authentication-and-identity @3, platform-delivery/kubernetes-and-orchestration @3

### Istio Ingress Gateway
**Short:** Envoy-based mesh edge proxy terminating external traffic and applying Istio routing, mTLS and policy.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2

### Kiali
**Short:** Istio's observability console: live service topology, traffic flow, mTLS status and config validation.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, observability/tracing-apm-and-llm-observability @2, observability/alerting-and-incident-response @3

### Kong
**Short:** Nginx-based API gateway with plugins for auth, Redis-backed rate limiting and traffic policy at the ingress.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/proxy-and-load-balancer @3

### Kong API Gateway
**Short:** Plugin-driven API gateway on Nginx/OpenResty: fixed and sliding-window rate limits, auth, transforms.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/proxy-and-load-balancer @2, security/authentication-and-identity @3

### Kubernetes DNS
**Short:** In-cluster DNS (CoreDNS) resolving Service names to ClusterIPs, the default service-discovery mechanism.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2

### Kubernetes Services
**Short:** The Kubernetes object giving a stable virtual IP and DNS name that load-balances across a changing set of Pods.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2, platform-delivery/kubernetes-and-orchestration @2

### Linkerd
**Short:** Lightweight Kubernetes service mesh: Rust micro-proxy sidecars doing L7 load balancing, retries and mTLS.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2, traffic-edge/rate-limiting-and-resilience @3, security/secrets-and-cryptography @3

### LoadbalanceRSocketClient
**Short:** RSocket client that spreads requests across a live list of targets with client-side load balancing.
**Kind:** api
**Lang:** java
**Roles:** traffic-edge/proxy-and-load-balancer @1, apis-frameworks/rpc-graphql-and-streaming @2

### Lua
**Short:** Small embeddable scripting language; in Redis and nginx its single-threaded execution makes a multi-step script atomic.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/distributed-cache @2

### Lyft ratelimit
**Short:** Envoy's descriptor-based global rate-limit service, backed by Redis and configured per route or header.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/service-mesh-and-discovery @2, traffic-edge/api-gateway @3

### MetalLB
**Short:** Provides Service type=LoadBalancer on bare-metal Kubernetes by advertising external IPs over ARP/NDP or BGP.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/kubernetes-and-orchestration @2

### mitmproxy
**Short:** Interactive TLS-intercepting proxy for inspecting, replaying and rewriting HTTP traffic while debugging a client.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, runtime-systems/io-networking-and-syscalls @2, devtools/testing-and-mocking @3

### Netflix Hystrix
**Short:** Legacy JVM resilience library providing circuit breakers, bulkheads and fallbacks; superseded by Resilience4j.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1

### Nginx
**Short:** Reverse proxy, L4/L7 load balancer and web server; also TLS termination, HTTP caching and rate limiting.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, caching/http-and-cdn-cache @2, traffic-edge/api-gateway @2, traffic-edge/rate-limiting-and-resilience @3, apis-frameworks/web-framework-and-http-client @3

### Nginx limit_req
**Short:** NGINX leaky-bucket rate limiter (limit_req_zone, burst, nodelay) that sheds load at the edge before the app.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/proxy-and-load-balancer @2

### Nginx Plus
**Short:** F5's commercial Nginx build adding active health checks, dynamic upstreams, session persistence and dashboards.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2, caching/http-and-cdn-cache @3

### NodeLocal DNSCache
**Short:** Per-node DNS cache DaemonSet that removes conntrack pressure and tail latency from Kubernetes service lookups.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2

### on AWS
**Short:** Placeholder entry for the AWS-hosted service-mesh option in a microservices technology table.
**Kind:** concept
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1

### Polly
**Short:** .NET resilience library for retries, circuit breakers, timeouts, bulkheads and fallbacks around remote calls.
**Kind:** tech
**Lang:** csharp
**Roles:** traffic-edge/rate-limiting-and-resilience @1, apis-frameworks/design-patterns-and-principles @3

### Private Link
**Short:** Cloud feature exposing a single service into your VPC through a private endpoint, so traffic never crosses the internet.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, runtime-systems/io-networking-and-syscalls @2, security/authorization-and-policy @3

### PrivateLink
**Short:** AWS PrivateLink: expose or consume a single service privately over VPC endpoints, never traversing the internet.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2

### PSC
**Short:** Google Private Service Connect: exposes one service privately across VPCs or projects without public IPs or peering.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2

### pybreaker
**Short:** Python circuit-breaker library with configurable failure thresholds, listeners and Redis-backed shared state.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1

### Redis INCR
**Short:** Atomic Redis counter increment - the simplest distributed fixed-window rate limiter, at the cost of boundary bursts.
**Kind:** api
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/distributed-cache @2

### Resilience4j
**Short:** JVM fault-tolerance library: circuit breaker, retry, bulkhead, time limiter and rate limiter as composable decorators.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1, runtime-systems/concurrency-and-async @3, apis-frameworks/aop-middleware-and-scheduling @3

### Resilience4j RateLimiter
**Short:** In-process rate limiter for Java services, composable with Resilience4j retry, bulkhead and circuit breaker.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1

### Route 53
**Short:** AWS managed DNS with health checks and latency/weighted/failover routing policies.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2, traffic-edge/proxy-and-load-balancer @3

### Sentinel
**Short:** Name shared by two products: Alibaba's Java flow-control/circuit-breaking library and HashiCorp's policy DSL.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, security/authorization-and-policy @2

### service mesh
**Short:** Sidecar or ambient layer that takes over service-to-service traffic: discovery, mTLS, retries and load balancing.
**Kind:** concept
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1

### slowapi
**Short:** Rate-limiting decorator and middleware for FastAPI/Starlette, backed by the limits library with Redis storage.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1, apis-frameworks/aop-middleware-and-scheduling @2

### Spring Cloud CircuitBreaker
**Short:** Spring abstraction over pluggable circuit breakers: Resilience4j, Spring Retry, Framework 7 native retry.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1, apis-frameworks/dependency-injection-and-config @3

### Spring Cloud Eureka
**Short:** Spring Cloud's Eureka integration: client-side registration and lookup of service instances from a Spring Boot app.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/service-mesh-and-discovery @1

### Spring Cloud Gateway
**Short:** Reactive Spring-native API gateway: routing predicates, filters, Redis-backed rate limiting for JVM microservices.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/api-gateway @1, traffic-edge/proxy-and-load-balancer @2, traffic-edge/rate-limiting-and-resilience @2

### Spring Cloud Gateway GlobalFilter
**Short:** Spring Cloud Gateway's global filter interface: an ordered reactive pre/post chain around every proxied call.
**Kind:** api
**Lang:** java
**Roles:** traffic-edge/api-gateway @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

### Spring Cloud LoadBalancer
**Short:** Spring's client-side load balancer: caches the discovered instance list and picks a target per request.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @2

### Spring Framework resilience
**Short:** Spring's built-in @Retryable/@ConcurrencyLimit support, including retry of optimistic-locking failures.
**Kind:** api
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1, data-access/transactions-and-consistency @2

### spring-cloud-starter-gateway-server-webmvc
**Short:** Spring Cloud Gateway's blocking Servlet flavour: same filter model on Spring MVC, pairing well with virtual threads.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/api-gateway @1, apis-frameworks/web-framework-and-http-client @3

### spring-cloud-starter-loadbalancer
**Short:** Spring Cloud client-side load balancer that resolves lb:// URIs against any DiscoveryClient instance list.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @2

### spring-cloud-starter-netflix-eureka-client
**Short:** Starter registering a Spring Boot app with a Eureka registry and keeping it alive by heartbeat for discovery.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/service-mesh-and-discovery @1, apis-frameworks/dependency-injection-and-config @3

### spring-cloud-starter-netflix-eureka-server
**Short:** Eureka service registry server: an AP registry with peer-to-peer replication and client-side lease renewal.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/service-mesh-and-discovery @1

### spring-retry
**Short:** AOP-based retry for Spring: @Retryable with backoff policies and an @Recover fallback when attempts run out.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1, apis-frameworks/aop-middleware-and-scheduling @2

### tenacity
**Short:** Python retry library: decorator-driven backoff, jitter, stop conditions and exception predicates; async-native.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1, runtime-systems/concurrency-and-async @3

### Traefik
**Short:** L7 reverse proxy and load balancer that discovers routes dynamically from Docker and Kubernetes labels.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2

### Traffic Manager
**Short:** Azure's DNS-based global traffic router: weighted, geographic and priority routing with endpoint health checks.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @2, platform-delivery/cloud-platform-and-cost @3

### xDS
**Short:** Envoy's discovery-service API family: a control plane streams endpoint, cluster, route and listener config to proxies.
**Kind:** spec
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2, apis-frameworks/rpc-graphql-and-streaming @3
