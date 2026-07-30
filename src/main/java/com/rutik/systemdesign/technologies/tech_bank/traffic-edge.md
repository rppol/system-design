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

A proxy in Apigee is a chain of declarative policies attached to request and response flows — verify an API key or OAuth token, enforce a quota, spike-arrest a burst, transform XML to JSON, mask fields in the log — so most gateway behaviour is configuration rather than code, with JavaScript or Java callouts for the rest. On top of that sits the API-management half: products that bundle endpoints into a purchasable unit, a developer portal for self-service key issuance, and per-product analytics and monetization.

Reach for it when you publish APIs to external or partner developers and the packaging, onboarding, and reporting matter as much as the routing. If you only need auth, routing, and rate limiting for internal services, it is considerably heavier and costlier than a plain gateway or a service mesh.

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

It fronts Lambda functions, containers, or any HTTP backend and takes over the edge concerns you would otherwise hand-write: authorization through IAM, Cognito, or a custom Lambda authorizer, per-key throttling via usage plans and API keys, request/response mapping, and access logs to CloudWatch. Throttling is enforced per account per Region, and the steady rate is adjustable on request while the burst quota is not, so one noisy API can eat the budget of every other API in the same account.

Two flavours exist: REST APIs carry the full feature set (usage plans, request validation, WAF integration), while HTTP APIs are cheaper and lower-latency but drop some of it. Reach for it when you want managed auth and quotas without operating gateway instances; at very high request volume the per-request price is the reason teams move to an ALB or a self-run gateway instead.

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

A bucket has a capacity and a refill rate, and asking to consume a token returns immediately with whether one was available, so the caller decides between rejecting with a 429 and waiting. Refill is computed lazily from elapsed time rather than by a background thread, which is what keeps one bucket per user cheap enough to hold millions of them. Backing the bucket state with a distributed grid such as Redis or Hazelcast makes the limit shared, so all instances of a service enforce one budget rather than one each.

Reach for it for limits that need application context, per user, per API key or per tenant plan, where only the service knows who the caller is. Volume you simply want to shed is cheaper to drop at a gateway or CDN before it ever reaches a JVM, so the two layers complement each other rather than compete.

### Caddy
**Short:** Go web server and reverse proxy with automatic HTTPS certificates, HTTP/3 and a very small config file.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, apis-frameworks/web-framework-and-http-client @3, security/secrets-and-cryptography @3

TLS is not a configuration step here: given a domain name, Caddy obtains a certificate over ACME from Let's Encrypt or ZeroSSL, serves HTTPS, redirects plain HTTP, and renews in the background — a working reverse proxy is a two-line Caddyfile, with a JSON admin API underneath it for programmatic config.

It is a strong default for small and mid-sized edges, internal tools, and anywhere nobody wants to own a certbot cron job. One thing to set explicitly: when proxying an SSE or token stream, `flush_interval -1` on `reverse_proxy` stops the response being buffered, otherwise the client sees nothing until the stream ends.

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

You create a managed zone and record sets, and Google serves them from its anycast name servers; private zones resolve only inside chosen VPCs, which is how internal service names stay off the public internet. Routing policies do the traffic work -- weighted for splits, geolocation for regional steering, and failover backed by health checks that pull an unhealthy endpoint's address out of the answer -- and DNSSEC signing is a per-zone toggle. Reach for it when your workloads are on GCP and you want zones managed in the same IAM and Terraform world as everything else. Remember that DNS failover is bounded by client TTL caching and by resolvers that ignore short TTLs, so treat it as a coarse regional lever, never a fast one.

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

An agent runs on every node, registering local services and running their health checks, while a small cluster of servers holds the catalog and KV store behind Raft. Services find each other through DNS or the HTTP API, config lives in the KV store with blocking queries for change notification, and Consul Connect extends the same registry into a service mesh with mTLS identities and Envoy sidecars.

Reads default to being served by the leader — strongly consistent except for a brief window around leader change — with `consistent` mode available when you need a guaranteed linearizable read and `stale` when you want any server to answer for throughput. Reach for it in mixed fleets where VMs, containers, and multiple datacenters all need one discovery plane; inside a single Kubernetes cluster, native Services and etcd already cover most of what it does.

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

The mesh registers each service and runs a sidecar proxy (Envoy by default) beside it; Consul's built-in CA issues every service a certificate, so service-to-service traffic becomes mTLS with a verified identity on both ends rather than trust in a network range. Authorization is expressed as intentions -- allow or deny between two service identities -- enforced by the proxy, which replaces IP-based firewall rules with rules that survive rescheduling. Service defaults, resolvers and splitters then carry traffic shaping: subsets, failover to another datacenter, and weighted splits for canaries. Reach for it when workloads span VMs and Kubernetes or several datacenters, where Consul's federation is stronger than a Kubernetes-only mesh; a single-cluster Kubernetes shop usually finds Istio or Linkerd a shorter path.

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

`dig name type` sends a real DNS query and prints the whole response — question, answer, authority and additional sections, each record's remaining TTL, which server answered and how long it took. `+trace` walks the delegation from the root down so you can see exactly which nameserver returns the wrong answer, and `@8.8.8.8` or `@ns1.example.com` asks a specific resolver, which is how you tell a stale cache apart from a bad zone.

It is the first command for any problem that turns out to be resolution: propagation after a record change, a split-horizon internal zone, a CNAME chain, an MX or TXT record a provider insists is present. Because it queries DNS directly it bypasses the OS resolver and `/etc/hosts`, so when `dig` is right and the application is wrong, the fault is on the host rather than in DNS.

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

Its configuration is dynamic: listeners, routes, clusters and endpoints are pushed from a control plane over the xDS APIs, which is what lets a service mesh change routing, retry policy, outlier detection and connection limits fleet-wide without a restart or a single line of application code. Istio and Consul are control planes over exactly this data plane.

It terminates and originates mTLS, speaks HTTP/1.1, HTTP/2, HTTP/3 and gRPC natively, translates gRPC-Web for browsers, and emits detailed per-cluster statistics and spans - the observability is a large part of why meshes standardized on it. Rate limiting is deliberately delegated to an external service over gRPC using descriptors, so the quota is shared across every proxy. The costs are real: a sidecar per pod adds a network hop and memory, and hand-written Envoy config is dense enough that most teams only ever touch it through a control plane.

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

Envoy is configured dynamically over the xDS APIs instead of a config file plus reload, which is why nearly every service mesh and modern gateway uses it as the data plane. Per-route it gives retries with budgets, timeouts, outlier detection that ejects a failing host, load-balancing policies, gRPC-Web and gRPC-JSON transcoding, and it terminates HTTP/2 and HTTP/3.

Reach for it as a sidecar or edge proxy when you want those behaviours out of application code and identical across languages. Hand-written Envoy config is verbose and easy to get wrong, so in practice you drive it from a control plane such as Istio or use a gateway built on top of it.

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

Instances register on startup and renew with a heartbeat; clients pull the whole registry, cache it locally, refresh periodically, and choose an instance themselves, so a lookup costs nothing at request time and a registry outage does not immediately break traffic. That cache is also the catch — registration and eviction are eventually consistent, so a dead instance can linger in a client's view for tens of seconds, and self-preservation mode deliberately stops evicting when heartbeats drop en masse rather than emptying the registry during a network partition.

It suits a JVM fleet where a client library can do the discovery and the load balancing. On Kubernetes the platform already provides this through Services and endpoints, so running Eureka there duplicates the mechanism.

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

HAProxy is an event-driven proxy whose config splits into `frontend`, `backend` and `listen` sections, and it runs either at L4 (`mode tcp`, moving bytes) or L7 (`mode http`, parsing requests so it can route on host, path or header). It carries the machinery production actually needs: active and passive health checks, algorithms including `leastconn` and consistent hashing, connection limits with queueing, retries, TLS termination, and a runtime admin socket that drains a server without a config reload. Its stats page and per-backend counters make it a diagnostic as well as a router -- queue depth and check failures are visible per server. Reach for it in front of services and databases where TCP-mode balancing with real health checks matters (MySQL and PostgreSQL failover routing is the classic case); it is a proxy and balancer only, not a web server, so static content and applications live elsewhere.

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

The controller watches Ingress resources and renders them into an NGINX configuration it reloads in place, so a host or path rule becomes a real proxy rule and a Service's endpoints become upstreams -- it proxies to pod IPs directly rather than through the Service's cluster IP. It terminates TLS from certificates held in secrets, usually issued by cert-manager, and exposes NGINX behaviour through annotations: rewrites, timeouts, body-size limits, rate limits, session affinity, and canary routing by header or percentage. Two things bite in practice: annotations are per-Ingress and there are a great many of them, and a large number of Ingress objects means frequent configuration reloads. It is the community controller maintained under the Kubernetes project; new routing capability is increasingly landing in Gateway API implementations rather than in Ingress, so a new cluster should at least evaluate those.

### Istio
**Short:** Kubernetes service mesh: sidecar traffic routing, canary weighting, retries/circuit breaking, mTLS, all config-driven.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/proxy-and-load-balancer @2, security/authentication-and-identity @3, platform-delivery/kubernetes-and-orchestration @3

The `istiod` control plane compiles your intent into Envoy configuration and pushes it to the sidecar proxies, or to the sidecar-free ambient data plane. You then express behaviour as Kubernetes objects: a VirtualService shifting a percentage of traffic to a canary, a DestinationRule setting outlier detection and connection limits, a PeerAuthentication requiring mTLS, and you get uniform golden-signal telemetry for free.

Reach for it when many services in several languages need the same traffic, security and observability policy and you do not want a resilience library in each one. The cost is real: an extra network hop, per-pod resource overhead, and a large configuration surface that is easy to misconfigure, so a handful of services is usually better served by a library.

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

Requests hit an nginx and OpenResty data plane that matches a route to an upstream service and runs a chain of plugins around the proxied call, covering key, JWT and OIDC authentication, rate limiting, request and response transformation, and logging, so cross-cutting policy is configured once at the edge instead of reimplemented in every service. Distributed rate limiting needs shared state: the local counter policy is fast but per-node, so a cluster enforcing one global limit points the plugin at Redis and pays the extra hop. Configuration comes from a database or from a declarative file, and the Kubernetes ingress controller drives the same engine from CRDs. Reach for it when several services need consistent edge policy; a single service is usually better served by middleware in its own framework.

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

Each pod gets a Rust micro-proxy sidecar that intercepts its traffic and handles mutual TLS, retries, timeouts, per-request L7 load balancing and golden-signal metrics, with a control plane issuing and rotating the workload certificates. Identity is derived from the service account, so mTLS between pods is on by default without the application knowing anything about it.

Its load balancing is the practical win: an exponentially weighted moving average of observed latency, so requests steer away from a slow replica instead of round-robining into it. It deliberately offers less than Istio — fewer knobs, no Envoy configuration surface — which is exactly the point when you want mTLS and traffic metrics rather than a programmable data plane.

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

Each remote call was wrapped in a `HystrixCommand` that ran on its own bounded thread pool, so a slow dependency exhausted only its own pool instead of every request thread — the bulkhead. A circuit breaker tracked error rate over a rolling window, opened when it crossed a threshold, ran the command's fallback while open, and let a single trial request through in half-open state to test recovery; a metrics stream fed a real-time dashboard.

Netflix stopped active development and the project is in maintenance mode; new JVM work uses Resilience4j, usually behind Spring Cloud Circuit Breaker. It is still worth knowing because the vocabulary every later library uses — bulkhead, half-open, fallback, rolling window — was popularized here, and legacy services still run it.

### Nginx
**Short:** Reverse proxy, L4/L7 load balancer and web server; also TLS termination, HTTP caching and rate limiting.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, caching/http-and-cdn-cache @2, traffic-edge/api-gateway @2, traffic-edge/rate-limiting-and-resilience @3, apis-frameworks/web-framework-and-http-client @3

Nginx runs a master process and a small fixed number of worker processes, each handling thousands of connections in a non-blocking event loop, which is why holding many idle keep-alive connections costs almost nothing and why worker count tracks CPU cores rather than traffic. Configuration is a tree of `server` and `location` blocks, and from that one file it terminates TLS, serves static content, caches upstream responses with `proxy_cache`, balances across an `upstream` group, and rate-limits with `limit_req`'s leaky bucket. Reloads are graceful: new workers take new connections while old ones drain.

Two defaults cause most of the mystery incidents. `proxy_buffering` is on, so a streaming response — Server-Sent Events, a chunked LLM token stream — is held until a buffer fills and the client sees nothing for seconds; you turn it off for those locations. And a plain `proxy_pass` speaks HTTP/1.0 upstream unless you set `proxy_http_version 1.1` and clear the `Connection` header, which silently disables keep-alive to your backend and adds a TCP handshake to every request.

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

Polly wraps a call in a pipeline of resilience strategies — retry with backoff and jitter, circuit breaker, timeout, rate limiter, hedging, fallback — each configured declaratively and applied in the order you compose them. Ordering is what people get wrong: a timeout inside a retry bounds each attempt while a timeout outside bounds the whole operation, and a circuit breaker placed beneath a retry will be tripped by your own retries rather than by the dependency failing.

In .NET it plugs into the HTTP client factory so outbound calls inherit a policy without call sites changing. It is the ecosystem's counterpart to Resilience4j on the JVM, and the patterns transfer directly between them.

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

Wrap a call in a `CircuitBreaker` and after `fail_max` consecutive failures the breaker opens: subsequent calls raise `CircuitBreakerError` immediately instead of each one waiting out a timeout. After `reset_timeout` it goes half-open and allows a single trial call, which either closes it again or reopens it. That converts a sick dependency from a sink for threads and latency into a fast, cheap failure your code can fall back from — a cached value, a degraded response, a queued retry.

Practical details that decide whether it helps: an exclude list so expected exceptions (a 404, a validation error) do not count as failures and trip the breaker on healthy traffic; listeners to log and emit metrics on state change, because a silently open breaker looks exactly like a working system serving wrong answers; and a Redis-backed state store when you want a fleet of workers to share one breaker rather than each discovering the outage independently. Always pair it with a request timeout — a breaker never trips on calls that simply hang forever.

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

Each concern is a standalone decorator composed functionally around a call - `CircuitBreaker`, `Retry`, `Bulkhead` and `ThreadPoolBulkhead`, `TimeLimiter`, `RateLimiter` - with a Spring Boot starter that applies them by annotation and binds their configuration to properties. The circuit breaker works over a sliding window of the last N calls or last N seconds, opening on a failure-rate or slow-call-rate threshold, then admitting a few probe calls in half-open before it closes; every transition and outcome is a Micrometer metric, which is what makes production behaviour explainable.

Composition order matters and is easy to get backwards: a retry outside the breaker retries calls the breaker is rejecting, while a retry inside it feeds every attempt into the failure window. Always pair a retry with a timeout and jittered exponential backoff, cap the attempts, and never retry a non-idempotent write.

### Resilience4j RateLimiter
**Short:** In-process rate limiter for Java services, composable with Resilience4j retry, bulkhead and circuit breaker.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1

It hands out a fixed number of permits per refresh period; a thread either takes one, waits up to a configured timeout, or fails immediately with `RequestNotPermitted`, and the decorator composes with retry, bulkhead, time limiter and circuit breaker in a defined order. Because it is a library, the limit protects a dependency from your service without a network hop.

The trap is that state lives in one JVM: with N replicas the effective limit is N times what you configured, and it moves as you autoscale. Reach for it for self-imposed limits on outbound calls to a fragile downstream; a real per-client quota belongs in a gateway or a Redis-backed shared counter.

### Route 53
**Short:** AWS managed DNS with health checks and latency/weighted/failover routing policies.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2, traffic-edge/proxy-and-load-balancer @3

Route 53 is authoritative DNS with routing policies attached to record sets: weighted for canaries and gradual shifts, latency-based to send a client to the nearest healthy region, failover backed by health checks, geolocation, and multivalue answers. Health checks probe endpoints independently of the resolver path and pull failing records out of rotation.

That makes it the coarse layer of global traffic management — the level above a load balancer, deciding which region a client resolves to at all. DNS is cached, though: TTLs plus resolvers that ignore them mean failover takes minutes rather than seconds, so anything needing a fast cutover belongs to anycast or a load balancer instead of a record change.

### Sentinel
**Short:** Name shared by two products: Alibaba's Java flow-control/circuit-breaking library and HashiCorp's policy DSL.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, security/authorization-and-policy @2

Alibaba's Sentinel guards a JVM service at the granularity of a named resource: rules cap QPS or concurrent threads, break the circuit on a slow-call or error ratio, and hotspot rules rate-limit per parameter value so one abusive tenant id is throttled without touching anyone else. It also carries system-adaptive rules that shed load from overall CPU and queue signals, and a dashboard for changing rules at runtime.

HashiCorp's Sentinel is an entirely different product — an embedded policy language in the enterprise editions of Terraform, Vault, Consul and Nomad, whose policies run against a plan or a request at advisory, soft-mandatory or hard-mandatory enforcement levels. Open-source stacks generally use Rego with OPA or Conftest for that job.

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

It wraps the `limits` library and gives you a `Limiter` attached to the app plus a `@limiter.limit("100/minute")` decorator per route, with a key function deciding what is being limited. Remote address is the default, but the authenticated user or API key is usually the right key, since IP alone punishes everyone behind one NAT. Point `storage_uri` at Redis or Memcached so counters are shared, which is the only way the limit means anything once more than one worker runs; the in-memory default silently multiplies the effective limit by the number of processes. It fits per-route limits inside one FastAPI application, while a fleet-wide policy across services belongs at the gateway or ingress. The decorated endpoint must accept a `Request` parameter, which is the usual first stumble.

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

A route is a set of predicates - path, host, header, method, weight - plus an ordered filter chain, declared in YAML or a Java DSL. Filters rewrite paths, add or strip headers, retry, apply a circuit breaker, and `RequestRateLimiter` runs a token-bucket Lua script in Redis so the quota is shared across every gateway instance rather than being per-process. Service discovery integration means routes can target logical service names instead of hosts.

Its reactive server is built on Project Reactor and Netty, so it holds many idle and streaming connections cheaply - and correspondingly, any blocking call inside a filter stalls an event loop thread and degrades the whole gateway. Reach for it when the gateway is owned by a JVM team and should share the application ecosystem's security, discovery and observability; when the gateway should be infrastructure rather than an application, Envoy or a managed API gateway fits better.

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

This is Spring Cloud Gateway running on the Servlet stack instead of WebFlux: the same route predicates and filters, expressed as functional `RouterFunction` beans or the familiar YAML route definitions, but with each request handled by a thread rather than a reactive pipeline. That used to cost throughput; with virtual threads enabled a blocking gateway parks a cheap thread while waiting on the upstream, which removes most of the original reason to accept reactive code's debugging difficulty.

Reach for it when the team and the surrounding estate are Servlet-based and nobody wants to reason about `Mono`, `Flux` and reactive context propagation just to operate an edge. Choose the reactive flavour instead when the gateway must stream very large responses or hold enormous numbers of idle long-lived connections.

### spring-cloud-starter-loadbalancer
**Short:** Spring Cloud client-side load balancer that resolves lb:// URIs against any DiscoveryClient instance list.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @2

It resolves `lb://service-name` URIs used by `RestTemplate`, `RestClient`, `WebClient`, and Spring Cloud Gateway routes by asking whatever `DiscoveryClient` is on the classpath — Eureka, Consul, Kubernetes, or a static configured list — for the current instance list and choosing one, round-robin by default. Swapping in a `ReactiveLoadBalancer` bean gives you random, weighted, zone-preference, or health-check-filtered strategies, and a caching layer keeps it from hitting the registry per request.

It replaced Ribbon and is strictly client-side: no extra network hop and no proxy to run, but every client needs registry access and forms its own view of instance health, which is exactly the responsibility a service mesh moves out of the application. The behaviour worth memorizing is that when discovery returns no instance it fails with a 503 — that reads like the downstream service erroring, but it means the registry is empty, so check registration before you debug the callee.

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

The retry decorator is assembled from independent pieces: a wait strategy such as exponential backoff combined with random jitter, a stop condition on attempts or total elapsed time, a predicate deciding which exceptions or which returned values are worth retrying, and callbacks for logging each attempt. Coroutines are supported directly, and an explicit async retrying object covers cases where a decorator does not fit.

Retry only what is genuinely transient, a timeout, a connection reset, a 503 or a rate-limit response, and never a validation error or a 400, where every attempt fails identically while burning the caller's latency budget. Always combine a bounded stop condition with jitter: synchronized retries without jitter turn a brief degradation into a thundering herd that keeps the dependency down, and unbounded retries hide an outage from the caller instead of surfacing it.

### Traefik
**Short:** L7 reverse proxy and load balancer that discovers routes dynamically from Docker and Kubernetes labels.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2

It watches providers -- Docker labels, Kubernetes Ingress or its own CRDs, Consul, file -- and continuously rebuilds its routers, services and middleware chain, so a container that starts with the right labels is routable immediately with no config edit and no reload. Middlewares cover authentication, rate limiting, retries, header rewriting and path stripping, and it obtains and renews Let's Encrypt certificates over ACME by itself.

Reach for it at the edge of a container platform where the topology changes constantly and manual config would always be stale. For a static topology at very high throughput, nginx or HAProxy remain the more predictable and better-understood choice.

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
