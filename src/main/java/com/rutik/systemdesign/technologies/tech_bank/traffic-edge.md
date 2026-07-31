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

A service network is the unit of configuration: you register services with their listeners, associate the VPCs and accounts allowed to reach them, and a client then calls a stable DNS name without VPC peering, transit-gateway routes or overlapping-CIDR gymnastics. Authorization is IAM policy on the service or the network rather than security-group ranges, so a rule saying which role may call which path becomes expressible, and routing rules support weighted targets for canaries over health-checked target groups.

Reach for it when services are spread across many accounts and VPCs and the networking rather than the application is what makes them hard to connect. It is AWS-only and gives less than a full mesh, with no sidecar-level protocol control and no cross-cloud story, and is metered per service and per gigabyte. A mesh such as Istio, or a federated Consul, remains the answer where portability or fine-grained data-plane behaviour matters.

### API Gateway request quotas
**Short:** Per-client request quotas enforced at the gateway as admission control, shedding load before it reaches services.
**Kind:** concept
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/api-gateway @2

A quota is admission control at the boundary: each caller is identified by an API key, token subject or tenant id, counted against a limit over a window, and refused with `429 Too Many Requests` and a `Retry-After` hint, so the rejection costs microseconds at the edge rather than a connection, a thread and a timeout deep inside the system. Two settings are normally configured together: a sustained rate, and a burst depth deciding how much of a spike passes before shedding begins.

Set quotas per client, so one integration stuck in a retry loop cannot consume everyone else's capacity, and publish the limits so callers can back off deliberately. Two limits worth knowing: edge counters are often per node or per region rather than globally exact, making the effective limit a multiple of the configured one, and a quota protects capacity but not correctness, so timeouts and circuit breakers are still needed behind it.

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

It parses HTTP, so routing decisions can be made on host, path, header, query string, source IP or method, with weighted target groups behind each rule for blue/green and canary shifts. Targets can be instances, IP addresses or Lambda functions; it terminates TLS with certificates from ACM, speaks HTTP/2 and gRPC to targets, and can authenticate users against Cognito or an OIDC provider before a request reaches your code. Per-target-group health checks take failing targets out of rotation.

Reach for it as the default HTTP front door on AWS, since it is far cheaper per request than API Gateway and, with WAF attached, enough of a gateway for first-party traffic. What it is not is an API management product, because there are no usage plans, API keys, request validation or transformations. Its addresses also change, so clients needing a static IP or raw TCP throughput belong behind an NLB or Global Accelerator.

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

It works at the connection level, hashing a flow's addresses and ports to pick a target and forwarding without parsing the payload, which is why it adds very little latency, handles protocols other than HTTP, and can preserve the client's source IP to the target instead of relying on an `X-Forwarded-For` header. It holds a static address per availability zone, or your own Elastic IP, scales without pre-warming, and can terminate TLS while leaving the protocol above it opaque.

Reach for it for TCP and UDP services, for protocols an L7 balancer does not understand, when clients or firewalls need fixed IP addresses, and for extreme throughput. The tradeoff is that everything at layer seven disappears: no path routing, no header manipulation, no per-request logs, and shallower health checks. Idle connections are also silently dropped at the idle timeout, so keepalives matter. Combining it with an ALB behind is how you get both behaviours.

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

The unit of work is a policy document attached to an API, an operation or a product: an XML pipeline with inbound, backend, outbound and error sections in which you validate a JWT, rate-limit by subscription key, rewrite a URL, cache a response, transform a payload or call another service. Most gateway behaviour is therefore declarative configuration hung off an API definition imported from OpenAPI, while products bundle APIs with subscription keys and quotas and a developer portal handles self-service onboarding.

Reach for it when publishing APIs to partners or across an enterprise on Azure, and the packaging, subscriptions and documentation matter as much as the proxying. The tiers differ substantially in throughput, virtual-network integration and availability guarantees, so read that comparison before designing around it, and a self-hosted gateway is the option for keeping traffic on-premises. For simple internal routing, Application Gateway or a container-native ingress is far lighter.

### Azure Application Gateway
**Short:** Azure's L7 load balancer with URL-path routing, TLS termination and an optional web application firewall.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/api-gateway @2, platform-delivery/cloud-platform-and-cost @3

It is a regional L7 reverse proxy: listeners terminate TLS, rules map hostnames and URL paths to backend pools of virtual machines, scale sets, App Services or raw addresses, and probes decide which members are healthy. Cookie-based session affinity, connection draining during deployments, zone-redundant autoscaling and header rewriting are built in, and the WAF variant runs OWASP core rule sets in detection or prevention mode within the same hop.

Reach for it when Azure workloads need path-based routing with a web application firewall in front, or as an AKS ingress through its dedicated controller. Note the boundaries: it is regional, so global distribution needs Front Door or Traffic Manager above it, and it is a proxy rather than an API management product, so subscriptions, quotas and developer portals belong to API Management. For plain TCP or UDP the L4 Load Balancer is the right layer.

### Azure DNS
**Short:** Azure's managed authoritative DNS service for hosting zones and records.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2

Public zones are served from anycast name servers and delegated to by pointing the registrar's NS records at them, while private zones resolve only for the virtual networks you link, with optional auto-registration writing an A record for each virtual machine as it is created, which is how internal names stay off the public internet. Records are ordinary Azure resources, so role-based access control, resource locks, tags and Terraform or Bicep apply to a record set exactly as they do to a virtual machine.

Reach for it when the estate is on Azure and you want DNS under the same identity and infrastructure-as-code control as everything else. It is authoritative hosting rather than traffic management, so weighted, priority and geographic routing with health checks belong to Traffic Manager and global HTTP load balancing to Front Door. Whatever sits above it, remember that any DNS-level change propagates at the mercy of client TTL caching.

### Azure Load Balancer
**Short:** Azure's regional L4 load balancer distributing TCP/UDP flows across backend VMs and scale sets.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

It is a software-defined L4 balancer implemented in the platform's own network stack rather than as an appliance in the path, distributing flows by a hash over source and destination address and port. Rules bind a frontend address and port to a backend pool with a health probe, and it does not proxy, so the backend sees the client's connection. Outbound rules with explicitly allocated SNAT ports govern whether those machines can reach the internet at all, and exhausting those ports is a classic and confusing failure.

Reach for it for non-HTTP protocols, for internal balancing between tiers, and when you want the cheapest possible distribution with no per-request processing. Everything at layer seven is missing by definition, with no TLS termination, path routing, header rewriting or per-request logs, so web front ends belong behind Application Gateway or Front Door. The Standard and Basic tiers differ in zone support and default security posture, and that choice is not trivially reversible.

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

Because Cilium already implements pod networking, policy and load balancing as eBPF programs in the kernel datapath, the mesh reuses that path instead of adding a sidecar to every pod: L3 and L4 policy and load balancing execute in the kernel with no extra hop, and a shared per-node Envoy handles the L7 work, such as HTTP-aware policy, only for traffic that needs it. Workload identity for mTLS is derived from the Kubernetes identity rather than from network location.

Reach for it when sidecar overhead is the objection to adopting a mesh, since there is no per-pod memory and CPU tax, no injection webhook, no init-container ordering problem and one fewer hop per call. The costs are a modern kernel requirement, eBPF as a debugging surface most teams have never touched, and a feature set that trails the sidecar meshes on elaborate traffic shaping. Istio's ambient mode attacks the same complaint from the other direction.

### circuitbreaker
**Short:** Lightweight Python decorator that opens a circuit after repeated failures, wrapping sync or async callables.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1

The decorator counts consecutive failures of the wrapped callable and, once `failure_threshold` is reached, opens: further calls raise `CircuitBreakerError` immediately instead of each one waiting out its own timeout, until `recovery_timeout` elapses and a single trial call decides whether to close again. `expected_exception` is the parameter that matters most, deciding what counts as a failure, so a validation error or a 404 does not trip a breaker meant for a dependency being down.

Reach for it when you want breaker behaviour in a small service without adopting a whole resilience framework, and use the bundled monitor so a health endpoint can report which breakers are open. State lives in the process, so every worker discovers an outage independently and the breaker means less than it appears behind a multi-process server. Always pair it with an explicit request timeout, since a breaker never trips on a call that simply hangs.

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

It is a proxy you deploy rather than a hosted edge. The Extensible Service Proxy, an Envoy-based container, runs in front of the backend and pulls its configuration from a service definition you upload, generated from an OpenAPI document or a gRPC service configuration. From that specification it validates API keys, verifies JWTs against configured issuers, enforces quotas per consumer project, and reports request metrics and logs into the project's monitoring, so the contract file is the source of truth for the gateway's behaviour.

Reach for it when the backend runs on GKE, Compute Engine or Cloud Run and you want spec-driven authentication and quotas close to the service. Note the direction of travel: Google's newer API Gateway covers similar ground as a fully managed product and Apigee covers the full-lifecycle case, so a new deployment should compare all three rather than defaulting here. The proxy is also an extra container to size, deploy and keep patched.

### Cloudflare LB
**Short:** Cloudflare's global L7 load balancer with health checks, geo-steering and failover across origins.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, caching/http-and-cdn-cache @3

You define pools of origins with health monitors and attach them to a hostname; a request arriving at any Cloudflare data centre is steered by the configured policy, whether geographic or region-based steering, latency measured from the edge itself, weighted splits, or a simple failover order, and an origin failing its monitor is removed from the pool. Because the decision is made at the edge on a live connection rather than encoded in a DNS answer, failover is not held hostage by resolver TTL caching.

Reach for it when origins sit in several regions or several providers and traffic should follow health and proximity, particularly if the zone already proxies through Cloudflare so caching, WAF and TLS share the same hop. It balances between origins rather than inside a data centre, so a local balancer is still needed behind it, and all traffic transits a third party's network, which is both a dependency and a data-path consideration.

### Cloudflare Rate Limiting
**Short:** Edge rate limiting with geo rules and DDoS protection; counters are per data center, not global.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/http-and-cdn-cache @3, traffic-edge/api-gateway @3

A rule matches requests with an expression over path, method, headers, country or bot score, counts them per a characteristic you choose such as client address, an API key header or a JWT claim, and then blocks, challenges or merely logs once the threshold is crossed, all at the edge before the request leaves the data centre it landed in. That placement is the entire value, because an attack is absorbed on the provider's network rather than on your bandwidth and your servers.

Reach for it for volumetric abuse, credential stuffing and scraping. The counting caveat is the architecturally important one: counters are kept per data centre, so a client distributed across many locations sees an effective limit well above the configured number, and precise per-tenant quotas therefore still belong in your application or gateway on shared state. Treat this layer as coarse protection and keep an exact limiter behind it.

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

Connect was the name given to the mesh capability layered onto the existing service catalogue, and that lineage explains its shape. The registry, health checks and KV store already existed, so the mesh added a built-in certificate authority issuing SPIFFE-style workload identities, a sidecar proxy per service instance, and authorization expressed between service identities instead of between IP addresses. The certificate authority can be Consul's own or delegated to Vault, and rotation happens underneath the application.

Reach for it when Consul is already the discovery layer and you want mTLS and policy without introducing a second control plane. The proxy is Envoy by default, with a simpler built-in option for low-throughput cases. The behaviour to internalise is that intentions are enforced by the proxy, so a workload that bypasses its sidecar bypasses the policy, which is why a mesh is not a replacement for network controls. In a Kubernetes-only estate, Linkerd or Istio is a shorter path.

### CoreDNS
**Short:** Plugin-based DNS server that is the default Kubernetes service-discovery resolver, often with a per-node cache.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2, runtime-systems/io-networking-and-syscalls @3

A CoreDNS server is a chain of plugins compiled into the binary and enabled in a `Corefile`, and the chain is the design: `kubernetes` answers cluster service names from the API server, `forward` sends everything else upstream, `cache` holds answers, and `errors`, `log`, `prometheus`, `rewrite` and `hosts` fill in the rest. Order matters, because each plugin either answers the query or passes it along to the next.

The setting that causes the most trouble is not in CoreDNS at all. With `ndots:5` in a pod's resolver configuration, any name with fewer than five dots is tried against each cluster search domain first, so one external lookup becomes several queries and DNS becomes a surprisingly large share of tail latency. The usual remedies are fully qualified names with a trailing dot, a lower `ndots` in the pod spec, and a per-node cache. Scale the deployment with the cluster, since the two-replica default is a real bottleneck at size.

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

It speaks DNS rather than wrapping the operating system's resolver, so a resolver object can be pointed at a specific nameserver, ask for any record type including `SRV`, `TXT` and `CAA`, read the TTL off the answer, and use TCP, DNS over TLS or DNS over HTTPS. Beyond lookups it parses and generates zone files, performs zone transfers, builds and signs messages and supports dynamic updates, which is what makes it the base for provisioning and auditing tools rather than only for queries.

Reach for it when code has to verify DNS rather than merely use it: confirming a delegation before a cutover, discovering `SRV` endpoints, checking that a customer added the right `TXT` record. Remember that bypassing the OS resolver also bypasses `/etc/hosts`, `nsswitch` and any local cache, so its answers can legitimately differ from what the application sees. Ordinary connection code should keep using the standard library.

### dnsutils pod
**Short:** Throwaway debug pod with dig and nslookup, used to test CoreDNS resolution from inside a Kubernetes cluster.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2, runtime-systems/io-networking-and-syscalls @2

It is a convention rather than a product: run a small image containing `dig`, `nslookup` and `host` inside the cluster, exec into it, and resolve names from the pod network with the same resolver configuration a real workload receives. That is what separates the candidate causes, which are otherwise indistinguishable from outside: a Service with no ready endpoints, a wrong namespace in the name, a search-domain surprise, a NetworkPolicy blocking port 53, or CoreDNS itself being unhealthy.

Reach for it as the first step whenever a pod cannot reach a service, before changing anything, and test both the short name and the fully qualified `service.namespace.svc.cluster.local` form, since the difference between them tells you whether the search path is the problem. Delete it afterwards, because a long-lived pod full of shell tools is an audit and security nuisance. On a restricted cluster, an ephemeral debug container attached to the affected pod is the better tool.

### ECS Service Connect
**Short:** AWS ECS built-in service discovery and sidecar mesh giving named endpoints, mTLS and traffic telemetry.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @3

You give a service a discoverable name within a namespace and ECS injects a managed proxy into each task; clients then call the logical name, and the proxy resolves it and load-balances across healthy tasks with no load balancer in the path and no registry to operate. Because the proxy sees every request it also emits per-client and per-server metrics covering request counts, errors and latency percentiles, which is the part teams notice first, since that visibility otherwise needs a mesh or instrumentation in every service.

Reach for it for east-west traffic between ECS services when a full mesh is more machinery than the problem deserves. Its scope is exactly that: ECS only, with far less traffic control than a real mesh and nothing outside AWS. Public ingress still needs a load balancer, and workloads spanning ECS, EKS and virtual machines need a service-networking layer that is not tied to one runtime.

### ELB
**Short:** AWS Elastic Load Balancing: managed L4 (NLB) and L7 (ALB) load balancers with health checks and TLS termination.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

The name covers a family rather than one product: the Application Load Balancer parses HTTP and routes on host, path and headers, the Network Load Balancer forwards TCP and UDP flows at connection level with static addresses, and the Classic balancer predates both and is best avoided in new work. All of them register targets, run health checks, integrate with ACM for TLS, scale their own capacity, and are billed hourly plus a capacity-unit charge.

Pick by protocol: if a routing decision depends on the request it is an ALB, and if the protocol is not HTTP or the client needs a fixed address or the lowest latency it is an NLB. The shared caveats matter more than the differences: targets in an unhealthy subnet or behind a misconfigured security group never receive traffic, cross-zone balancing behaviour and its charges differ between types, and a balancer is only as available as the subnets it was given.

### Envoy
**Short:** High-performance L7 proxy used as service-mesh sidecar, API gateway, load balancer and rate-limit enforcer.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, traffic-edge/service-mesh-and-discovery @1, traffic-edge/api-gateway @2, traffic-edge/rate-limiting-and-resilience @2, apis-frameworks/rpc-graphql-and-streaming @3

Its configuration is dynamic: listeners, routes, clusters and endpoints are pushed from a control plane over the xDS APIs, which is what lets a service mesh change routing, retry policy, outlier detection and connection limits fleet-wide without a restart or a single line of application code. Istio and Consul are control planes over exactly this data plane.

It terminates and originates mTLS, speaks HTTP/1.1, HTTP/2, HTTP/3 and gRPC natively, translates gRPC-Web for browsers, and emits detailed per-cluster statistics and spans - the observability is a large part of why meshes standardized on it. Rate limiting is deliberately delegated to an external service over gRPC using descriptors, so the quota is shared across every proxy. The costs are real: a sidecar per pod adds a network hop and memory, and hand-written Envoy config is dense enough that most teams only ever touch it through a control plane.

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

Policies are composed around a call and the nesting order defines the semantics: with a retry, a breaker and a timeout composed together, the outermost policy is applied last, so a timeout inside a retry bounds each attempt while one outside bounds the whole operation. Beyond retry, breaker, timeout and fallback it offers hedging, issuing a second attempt after a delay and taking whichever returns first, which is the cheap fix for tail latency on idempotent reads, plus rate limiters and bulkheads.

Reach for it in plain Java where you want composable resilience without a framework, since it is a small dependency needing no annotations or container and works with synchronous calls, `CompletableFuture` and async execution alike. Resilience4j is the more common choice in Spring applications because of its starter and Micrometer integration, so the surrounding stack usually decides rather than the feature list. As always, state is per JVM, and hedging multiplies load on the dependency.

### fastapi-limiter
**Short:** Redis-backed FastAPI rate limiter exposed as a Depends() dependency, safe across multiple app instances.
**Kind:** tech
**Lang:** python
**Roles:** traffic-edge/rate-limiting-and-resilience @1, caching/distributed-cache @3

It is initialised once against a Redis connection and then attached as a route dependency with a permitted count and a window, and the counting is done by a Lua script on Redis so the increment and the expiry are one atomic operation and every application instance shares a single counter. The identifier defaults to client host plus path and is overridable, which is how you limit by authenticated user or API key rather than by address, necessary as soon as clients sit behind a NAT or a proxy.

Reach for it when a FastAPI service needs per-route limits enforced consistently across replicas without introducing a gateway. Two consequences follow from the design: Redis becomes a hard dependency of every request on a limited route, so decide deliberately whether an outage should fail open or closed, and the whole thing is async, so it belongs in an ASGI deployment only. Fleet-wide policy across many services still belongs at the ingress.

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

Under Google Cloud it is provisioned as a project resource with organizations, environments and environment groups, running on Google-managed infrastructure, while a hybrid installation places the message processors in your own Kubernetes cluster and keeps the management plane in the cloud. Analytics land in Google's own stack, and identity, billing and Terraform provisioning follow ordinary GCP conventions rather than living in a separate console with a separate account model.

Reach for it on GCP when the requirement is genuine API management, meaning products, monetization, a developer portal and deep analytics, rather than routing. The pricing model and the minimum footprint are substantial, which is exactly why Google also offers a much lighter API Gateway for the plain authentication-and-quota case and Cloud Endpoints for spec-driven proxying beside a service. Choosing the heavyweight where a proxy would do is the common and expensive mistake.

### GCP Cloud Load Balancing
**Short:** Google Cloud's global anycast L4/L7 load balancer serving one IP worldwide with regional backends.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

The global external tier is genuinely anycast: one address is announced from Google's edge worldwide, the connection terminates at the nearest point of presence, and the request travels Google's backbone to whichever regional backend has capacity, which removes a slow public-internet path and a DNS round trip from every connection. Backends are grouped into backend services with health checks and capacity scalers, URL maps handle host and path routing, and Cloud CDN and Cloud Armor attach at the same hop.

Reach for it when users are worldwide and you want one address, automatic regional failover and edge TLS termination without owning any of it. The concept count is the real cost, since forwarding rules, target proxies, URL maps, backend services and health checks are five objects for one balancer, and the global tier sits outside your VPC in ways that surprise people debugging source addresses. For a single region the regional or internal variants are simpler and cheaper.

### Global Accelerator
**Short:** AWS anycast edge service entering traffic at the nearest PoP over the AWS backbone, with fast regional failover.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/cloud-platform-and-cost @3

It gives you two static anycast addresses announced from AWS edge locations, so a client's connection is established at the nearest point of presence and then carried over the AWS backbone to the endpoint, skipping most of the public internet's congestion and route churn. Traffic dials weight each endpoint group per region, health checks pull an unhealthy region out within seconds, and because the client-facing address never changes, failover involves no DNS record change and no TTL to wait out.

Reach for it for global TCP and UDP workloads where the latency variance of the open internet is the problem, for gaming and real-time media, and wherever clients must allowlist fixed addresses. It is a network accelerator rather than a CDN, since nothing is cached, so a content-heavy site gains far more from CloudFront. It carries a fixed hourly charge plus a data premium, so measure the improvement from real client locations before adopting it.

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

It is a standalone Envoy deployment at the cluster edge with no application beside it, configured by the same control plane as the sidecars: a `Gateway` resource declares the ports, protocols and TLS certificates it exposes, and `VirtualService` resources bind hostnames and paths to in-mesh destinations. The traffic rules already used inside the mesh, such as weighted canaries, retries and mirroring, therefore apply identically to external traffic, and telemetry is continuous from edge to workload.

Reach for it when a mesh is already in place and a separate ingress controller would mean maintaining routing rules in two systems. It is a pod like any other, so its replica count, resource requests and autoscaling are yours to size, and its configuration surface is Istio's, which is powerful and easy to get subtly wrong where `Gateway` host matching and `VirtualService` binding interact. Teams standardising on Gateway API can express much of this in that vocabulary instead.

### Kiali
**Short:** Istio's observability console: live service topology, traffic flow, mTLS status and config validation.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, observability/tracing-apm-and-llm-observability @2, observability/alerting-and-incident-response @3

It reads Istio's configuration from the Kubernetes API and its telemetry from Prometheus, then joins the two into a live graph of which service calls which, with request rates, error percentages and latency on the edges and mTLS status shown per connection. The other half is validation: it inspects `VirtualService`, `DestinationRule` and `Gateway` objects for the mistakes that produce no error yet silently drop traffic, such as a route referencing a subset that no `DestinationRule` defines.

Reach for it when a mesh has enough services that nobody holds the call graph in their head, and during an incident to see which dependency is actually failing rather than which one is being blamed. It is a console over existing data rather than a data source, so it is only as good as the metrics retention behind it and it does not replace tracing for a single slow request. Its ability to edit mesh configuration should be locked down in production.

### Kong
**Short:** Nginx-based API gateway with plugins for auth, Redis-backed rate limiting and traffic policy at the ingress.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/api-gateway @1, traffic-edge/rate-limiting-and-resilience @2, traffic-edge/proxy-and-load-balancer @3

Deployment mode is the first architectural decision. The traditional mode keeps configuration in PostgreSQL, DB-less mode loads a declarative YAML file at boot so the gateway is immutable and reproducible, and hybrid mode splits a control plane from data-plane nodes that keep serving if the control plane is down. Plugins run in ordered phases around the proxied call, and beyond the bundled set they can be written in Lua or, through external plugin servers, in other languages.

Reach for it when you want a self-hosted gateway with a large plugin catalogue and no vendor runtime in the path. The recurring operational realities are that rate-limit and other counters are per node unless pointed at Redis, that a long plugin chain is latency paid on every request, and that plugin ordering has to be reasoned about explicitly. A managed gateway removes that work, and a service mesh is the better answer for east-west rather than edge policy.

### Kubernetes DNS
**Short:** In-cluster DNS (CoreDNS) resolving Service names to ClusterIPs, the default service-discovery mechanism.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2

Every Service gets an `A` record at `name.namespace.svc.cluster.local` resolving to its cluster IP, a headless Service returns the pod addresses directly instead, and `SRV` records expose named ports. Pods are configured with a search path so a bare name resolves within the namespace and `name.namespace` resolves across namespaces. Records are driven by the API server, so they appear and disappear with the objects themselves and no application needs a registry client, since discovery is simply resolution.

The trap lives in the resolver configuration rather than the server. With `ndots:5`, any name containing fewer than five dots is tried against each search domain first, so resolving an external hostname costs several failed queries before the real one, and all of them land on the same few DNS replicas. Use fully qualified names with a trailing dot for external hosts, consider a per-node cache, and scale the DNS deployment with the cluster instead of leaving the default replica count.

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

Its relevance here is embedding: the interpreter is tiny and has no threads of its own, so a host that is already single-threaded runs a script to completion without interleaving. In Redis that makes an entire script atomic, which is how a rate limiter reads a counter, compares it against a limit and increments it with an expiry as one indivisible operation rather than three racing commands. In nginx, OpenResty embeds LuaJIT into the request phases so authentication, routing and limiting logic run inside the proxy.

Reach for it when several operations must be atomic against shared state, or when a proxy needs logic that configuration cannot express. Keep scripts short, because in Redis a long script blocks every other client on that instance, and scripts must be deterministic to replicate correctly. Past a few dozen lines, moving the logic into a service is far easier to test than a string of Lua embedded in configuration.

### Lyft ratelimit
**Short:** Envoy's descriptor-based global rate-limit service, backed by Redis and configured per route or header.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/rate-limiting-and-resilience @1, traffic-edge/service-mesh-and-discovery @2, traffic-edge/api-gateway @3

Envoy does not count requests itself. It computes descriptors, key-value pairs derived from the route, headers or remote address, and asks this service over gRPC whether the request is within limits. The service matches them against a YAML tree of domains and limits and increments counters in Redis, so every proxy in the fleet consults one shared budget instead of each enforcing its own. Nested descriptors are what allow a per-route limit and a per-key limit to be evaluated in the same call.

Reach for it when a mesh or gateway needs a globally accurate quota rather than a per-node approximation. It is another service in the request path, so decide the failure mode deliberately: failing open keeps traffic flowing when the limiter is down and removes the protection exactly when overload is most likely. Local per-proxy limits cost nothing extra and suffice when the limit is a coarse safety valve rather than a contractual quota.

### MetalLB
**Short:** Provides Service type=LoadBalancer on bare-metal Kubernetes by advertising external IPs over ARP/NDP or BGP.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, platform-delivery/kubernetes-and-orchestration @2

On a cloud, a Service of type LoadBalancer is fulfilled by the provider's API; on bare metal nothing answers and the Service stays pending forever. MetalLB fills that gap by allocating an address from pools you configure and then advertising it, either in layer-2 mode, where one elected node answers ARP or NDP so all traffic enters through that node, or in BGP mode, where nodes peer with your routers and the address is announced as a route that ECMP hashing spreads flows across.

Reach for it in any self-hosted cluster where you want the standard Service type to work instead of node ports plus an external proxy. Understand the mode: layer-2 is trivial to configure but gives failover rather than balancing, with one node's bandwidth as the ceiling and a brief outage while the address moves, while BGP balances properly but needs the network team and rehashes flows when the topology changes.

### mitmproxy
**Short:** Interactive TLS-intercepting proxy for inspecting, replaying and rewriting HTTP traffic while debugging a client.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/proxy-and-load-balancer @1, runtime-systems/io-networking-and-syscalls @2, devtools/testing-and-mocking @3

It sits between client and server and generates a certificate per host on the fly from its own certificate authority, so once that authority is trusted by the client the session is decrypted, inspected and re-encrypted onwards, making every request and response visible. It runs as an interactive terminal application, a web interface or a headless runner, and its addon API lets Python hooks rewrite headers, inject faults, delay or replay traffic, which turns it from a viewer into a test harness.

Reach for it when the client is not yours to instrument, such as a mobile app or a vendor SDK, and you need to see exactly what it sends, or to fake a backend response. Two constraints decide whether it works at all: certificate pinning defeats interception by design, and it is a debugging tool rather than a production proxy. Treat any capture as containing credentials, because it usually does.

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

The commercial build adds precisely the parts the open-source server leaves out: active health checks that probe upstreams rather than only marking a server bad after a real request fails, an API for adding and removing upstream servers with no configuration reload, session persistence beyond simple hashing, re-resolution of upstream DNS names as they change, a live activity monitoring dashboard, and JWT validation at the edge.

Reach for it when upstreams change frequently and reloading configuration for every change is unacceptable, or when a support contract is a procurement requirement. Weigh it honestly against the alternatives, because that same gap is what most teams close for free with a controller that regenerates and reloads configuration, an Envoy-based proxy fed endpoints by a control plane, or Traefik discovering backends from labels. Licensing is per instance, which makes a large horizontally scaled edge expensive.

### NodeLocal DNSCache
**Short:** Per-node DNS cache DaemonSet that removes conntrack pressure and tail latency from Kubernetes service lookups.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/kubernetes-and-orchestration @2

It runs a caching resolver as a DaemonSet on every node and points pods at that local listener instead of at the cluster DNS service address. Two things follow. Cache hits are answered on the node with no network hop at all, and the misses that do leave the node travel to the cluster resolver over a long-lived TCP connection rather than as fresh UDP exchanges through the service's NAT rules, which is the actual fix, because it removes the conntrack churn and UDP entry races behind sporadic five-second lookups.

Reach for it in any busy cluster, and especially where those intermittent multi-second DNS timeouts appear under load. The tradeoffs are ordinary DaemonSet ones: a small resident footprint on every node and one more component to upgrade, plus a cache that can serve a briefly stale answer after an endpoint change. It complements rather than replaces tuning the resolver's `ndots` setting and scaling the cluster DNS deployment itself.

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

### PrivateLink
**Short:** AWS PrivateLink: expose or consume a single service privately over VPC endpoints, never traversing the internet.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2

An interface VPC endpoint is an elastic network interface in your own subnet with a private address, and it fronts either an AWS service or a service another account published behind a Network Load Balancer. Traffic to it never touches an internet gateway or a NAT gateway, security groups on the interface control who may use it, and an endpoint policy can restrict which API actions and resources are permitted through it.

Reach for it for private access to AWS APIs from isolated subnets and for consuming partner services without peering. Two practical points dominate: private DNS must be enabled or configured so the service's normal hostname resolves to the endpoint, otherwise clients silently keep using the public path, and interface endpoints carry an hourly charge per availability zone plus data processing, which becomes significant across many services and accounts. Gateway endpoints for S3 and DynamoDB are free where they apply.

### PSC
**Short:** Google Private Service Connect: exposes one service privately across VPCs or projects without public IPs or peering.
**Kind:** tech
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, platform-delivery/cloud-platform-and-cost @2

A producer publishes a service attachment in front of an internal load balancer, and a consumer creates an endpoint reserving an internal address in their own VPC that points at it. Connections travel Google's network and reach the producer from a dedicated NAT subnet, which is what allows two organisations with identical private ranges to connect without renumbering anything. The producer explicitly accepts or rejects each consumer project, so exposure is a per-consumer decision rather than a network-wide one.

Reach for it for consuming Google APIs privately, for reaching managed services such as Cloud SQL, and for publishing your own service to other projects or customers without peering. The same caveats apply as elsewhere: an endpoint per service to create and pay for, DNS entries so the intended name resolves privately, and a deliberately one-way connection. Where two networks genuinely need full mutual reachability, VPC peering or a connectivity hub is the right tool.

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

The mechanism is interception. A proxy beside each workload, or a per-node component in the ambient variants, takes over the connection, so retries, timeouts, outlier ejection, load balancing, mTLS with rotating workload identity and uniform request metrics all happen without a library in the application. A control plane distributes that configuration, which is what makes behaviour consistent across languages and changeable at runtime: the fleet's retry policy becomes a configuration push rather than a coordinated release.

Reach for it when many services in several languages need the same traffic and security policy, and the alternative is asking every team to adopt a resilience library correctly. The costs are routinely underestimated: an extra hop on every call, per-pod resources, a configuration surface whose failure modes look exactly like application bugs, and a control plane to operate. Under a dozen services, or in a single language, a library and a good ingress usually win.

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

It is an abstraction rather than an implementation: you inject a factory, wrap a call with an id, a supplier and a fallback, and whichever starter is on the classpath provides the actual behaviour. Configuration is applied through a customizer bean rather than at the call site, which keeps per-circuit thresholds and timeouts in one place, and a reactive variant covers `Mono` and `Flux` pipelines with the same model.

Reach for it when writing a library or shared code that should not force a resilience implementation on its consumers, or when the surrounding project already leans on the Spring Cloud ecosystem. If the application is going to use Resilience4j anyway, and it usually is, programming against that library's own annotations and configuration properties gives access to settings the abstraction does not expose, such as the full sliding-window and bulkhead options. Abstracting over exactly one implementation is cost without benefit.

### Spring Cloud Eureka
**Short:** Spring Cloud's Eureka integration: client-side registration and lookup of service instances from a Spring Boot app.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/service-mesh-and-discovery @1

Adding the client starter and enabling discovery makes a Boot application register itself on startup, renew its lease by heartbeat, and pull the instance list into a local cache; discovery is then exposed through the `DiscoveryClient` interface, and combined with the client-side load balancer a call to a logical service name is resolved straight from that cache. Configuration is ordinary properties, so the registry address and the heartbeat and refresh intervals are environment concerns rather than code.

Reach for it in a Spring estate on virtual machines or in a datacentre where discovery has to live in the application layer. Two behaviours must be understood before relying on it: the client's cached view lags reality by the refresh interval, so a stopped instance is still called for a while and calls must be retried against another one, and the registry deliberately favours availability over consistency. On Kubernetes the platform already provides discovery, so running this on top duplicates it.

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

The core type is a load balancer that chooses one instance from a supplier of the current list, and the supplier is a chain you compose: the discovery-backed list, then optional filters and caches such as health-check filtering, zone preference, or the same-instance preference used in local development. Because the choice happens inside the caller there is no proxy hop, and the strategy is a bean you can replace outright without touching any call site.

Reach for it in a Spring estate where discovery already exists and you want balancing without deploying infrastructure. Two consequences follow from being client-side: every client holds its own slightly stale view of instance health, and the policy is upgraded by redeploying applications rather than by changing configuration centrally, which is precisely the responsibility a service mesh moves out of the application. It also only balances traffic that goes through Spring's own clients.

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

The starter pulls in the Eureka client and its auto-configuration, so registration happens on startup from the application name and the configured instance metadata, and a background thread renews the lease on an interval. What matters in practice is the metadata it publishes and the timing around it, because the lease renewal and expiry intervals, together with the client's own registry-fetch interval, decide how long a dead instance keeps being handed to callers.

Reach for it when a Boot service must appear in a Eureka registry. Two configuration mistakes are common enough to name: an instance registering a container hostname or otherwise unreachable address, which needs the prefer-IP-address setting or an explicit hostname, and leaving the default intervals in place while expecting fast failover, which those defaults were never designed for. Behind a Kubernetes Service none of this is needed at all.

### spring-cloud-starter-netflix-eureka-server
**Short:** Eureka service registry server: an AP registry with peer-to-peer replication and client-side lease renewal.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/service-mesh-and-discovery @1

The server is an ordinary Boot application with an annotation, which is why it is so often run as a small in-house service. Peers replicate registrations to each other instead of electing a leader, so the registry stays available under partition and is eventually consistent, and a client may read from any node. Self-preservation is the behaviour to know: when renewals fall below an expected threshold the server stops expiring leases, on the theory that mass heartbeat loss is more likely a network fault than a mass outage.

Reach for it when a JVM estate outside Kubernetes needs a registry that is trivial to run. That same self-preservation lets stale entries linger, so clients must retry elsewhere rather than trusting the list, and the registry needs several peers across zones or it becomes the single point of failure it was meant to remove. Consul offers a stronger consistency model, and on Kubernetes the platform makes it unnecessary.

### spring-retry
**Short:** AOP-based retry for Spring: @Retryable with backoff policies and an @Recover fallback when attempts run out.
**Kind:** tech
**Lang:** java
**Roles:** traffic-edge/rate-limiting-and-resilience @1, apis-frameworks/aop-middleware-and-scheduling @2

It is AOP. `@Retryable` on a method has a proxy intercept the call and re-invoke it according to the configured exception types, maximum attempts and backoff policy, with `@Recover` naming the method that runs once attempts are exhausted. The proxy is also the first thing that bites, because an internal call from one method of a bean to another bypasses it entirely and the retry silently never happens. A `RetryTemplate` provides the same policies programmatically where annotations do not fit.

Reach for it for genuinely transient failures such as a deadlock or optimistic-locking conflict on a write, a connection reset, or a 503. Always set a multiplier and random jitter on the backoff so retries from many instances do not synchronise into a thundering herd, retry only idempotent operations, and be careful retrying inside a transaction that is already doomed. Resilience4j is the fuller library when breakers, bulkheads and metrics are wanted alongside retry.

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

It works purely in DNS. A client resolving your name receives an answer chosen by the profile's routing method, whether priority for failover, weighted for splits, performance for the lowest-latency region, geographic, subnet or multivalue, and the client then connects directly to that endpoint, so no traffic passes through the service itself. Endpoints can be Azure resources, external hostnames or nested profiles, and health probes remove a failing endpoint from the answers it hands out.

Reach for it for global distribution across regions, clouds or on-premises endpoints, and for protocols other than HTTP, since nothing about it is HTTP-specific. The DNS mechanism is also the limit: answers are cached for the record's TTL and by resolvers that ignore it, so failover takes minutes rather than seconds, and there is no TLS termination, caching, firewall or path-based routing. Front Door is the anycast proxy to reach for when those matter.

### xDS
**Short:** Envoy's discovery-service API family: a control plane streams endpoint, cluster, route and listener config to proxies.
**Kind:** spec
**Lang:** *
**Roles:** traffic-edge/service-mesh-and-discovery @1, traffic-edge/proxy-and-load-balancer @2, apis-frameworks/rpc-graphql-and-streaming @3

It is a family of gRPC and REST APIs, with LDS for listeners, RDS for routes, CDS for clusters, EDS for endpoints and SDS for secrets, that a proxy subscribes to and a control plane streams, so configuration is pushed rather than polled and applies without a restart. Consistency across resource types is the hard part: the aggregated variant carries every type on one stream so ordering is well defined, and the delta protocol sends only what changed, which matters when endpoints churn in a large fleet.

You meet it as a protocol to implement rather than to configure, whether writing your own control plane or using the existing libraries. Its significance is that it decoupled the data plane from the control plane and became a de facto standard beyond Envoy, with gRPC clients able to consume it directly for proxyless load balancing. Writing one is a serious undertaking, which is why Istio, Consul and off-the-shelf gateways exist.
