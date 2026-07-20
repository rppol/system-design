# Container and Deployment Patterns

## 1. Concept Overview

Container and deployment patterns address how to package, configure, and deploy backend services reliably and repeatedly. Docker multi-stage builds produce minimal, secure container images. Kubernetes deployment strategies (rolling update, blue-green, canary) control how new versions replace old ones with varying tradeoffs of speed, risk, and resource cost. Health probes gate traffic and trigger restarts. Resource requests and limits govern scheduling and throttling behavior. These patterns collectively enable zero-downtime deployments and predictable system behavior.

---

## 2. Intuition

A deployment without a strategy is like replacing airplane engines mid-flight without a plan. Rolling update replaces one engine at a time. Blue-green lands the plane on a parallel runway, switches passengers, then takes off again. Canary sends 1% of passengers on the new plane first to check it works. Each has different risk and resource tradeoffs. In Kubernetes, you choose based on: how much downtime is acceptable, how quickly you need rollback, and how much extra compute you can afford.

---

## 3. Core Principles

- **Immutable infrastructure**: containers are never modified after build; if config changes, build a new image; this ensures reproducibility
- **12-factor configuration**: all configuration via environment variables, not baked into the image; the same image runs in dev, staging, and production
- **Fast startup, graceful shutdown**: containers should start in seconds (JVM optimizations, CRaC, GraalVM native) and drain in-flight requests before stopping
- **Minimal images**: smaller images = smaller attack surface, faster pulls; use distroless or JRE-only base images
- **Separate concerns**: readiness (traffic gate) and liveness (restart trigger) serve different purposes

---

## 4. Types / Architectures / Strategies

**Deployment strategies**:
- **Recreate**: terminate all old pods, create all new pods; downtime during transition; simple; use for stateful migrations
- **RollingUpdate**: replace pods incrementally (maxUnavailable=0 + maxSurge=1 for zero downtime); default; gradual
- **Blue-green**: maintain two identical environments; switch traffic at once; instant rollback by switching back
- **Canary**: route small % to new version; monitor error rate; gradually increase percentage; progressive delivery

**Image base choices**:
- `eclipse-temurin:21-jre`: OpenJDK JRE, ~180MB
- `eclipse-temurin:21-jre-alpine`: Alpine-based, ~120MB
- `gcr.io/distroless/java21`: no shell, no package manager, ~75MB; minimal attack surface
- GraalVM native: binary executable, ~50MB, < 100ms startup; no JVM

---

## 5. Architecture Diagrams

**Docker Multi-Stage Build**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph ST1["Stage 1: Builder (JDK)"]
        jdk(["FROM ...21-jdk"]) --> pomCache["COPY pom.xml<br/>+ go-offline<br/>cached layer"]
        pomCache --> srcBuild["COPY src<br/>mvn package<br/>rebuilds on change"]
        srcBuild --> extract["layertools<br/>extract"]
    end

    subgraph ST2["Stage 2: Runtime (JRE only)"]
        jre(["FROM ...21-jre"]) --> stableLayers["COPY deps + loader<br/>layers, rarely change"]
        stableLayers --> appLayer["COPY application<br/>layer, smallest,<br/>changes most"]
        appLayer --> entry(["ENTRYPOINT<br/>JarLauncher"])
    end

    extract -.-> jre
    entry --> result(["~180MB image<br/>vs ~800MB w/ JDK"])

    class jdk,jre base
    class pomCache,stableLayers frozen
    class srcBuild,appLayer train
    class extract mathOp
    class entry,result io
```
*The builder stage compiles with the full JDK toolchain; the runtime stage copies only compiled layers into a JRE-only image, ordered from least-changing (dependencies) to most-changing (application code) to maximize Docker layer-cache reuse — this ordering is what takes the final image from ~800MB down to ~180MB.*

**Canary Deployment (Istio)**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    client(["Client Traffic<br/>100%"]) --> svc["Kubernetes Service"]
    svc -->|"90%"| v1["Deployment v1<br/>(stable) Pod-1..3"]
    svc -->|"10%"| v2["Deployment v2<br/>(canary) Pod-4"]
    v2 --> mon{"Monitor v2:<br/>error rate, p99"}
    mon -->|"healthy"| ramp["Ramp 50% to 100%<br/>retire v1"]
    mon -.->|"errors"| rollback["Rollback to 0%<br/>no new pods needed"]

    class client io
    class svc mathOp
    class v1 train
    class v2 req
    class mon mathOp
    class ramp train
    class rollback lossN
```
*Istio's VirtualService splits traffic by weight (90/10 here); monitoring the canary's error rate and p99 latency decides whether to ramp toward 100% and retire v1, or roll back to 0% — with no new pods required either way.*

---

## 6. How It Works — Detailed Mechanics

### Dockerfile Multi-Stage Build

```dockerfile
# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Copy dependency declaration first (layer cache — only invalidated when pom.xml changes)
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -B

# Copy source (layer invalidated when source changes)
COPY src ./src

# Build the JAR and extract layers (Spring Boot Layertools)
RUN ./mvnw package -DskipTests -B && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# Stage 2: Runtime image — only what's needed to run
FROM eclipse-temurin:21-jre-jammy AS runtime

# Non-root user (security best practice)
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

WORKDIR /app

# Copy extracted layers in dependency order (least → most frequently changing)
COPY --from=builder /build/target/extracted/dependencies/ ./
COPY --from=builder /build/target/extracted/spring-boot-loader/ ./
COPY --from=builder /build/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/target/extracted/application/ ./

# JVM flags for container awareness
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 \
               -XX:+UseContainerSupport \
               -XX:+OptimizeStringConcat \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

**Read it like this.** "Take 75 percent of *the container's* memory limit for the Java heap — and the two flags together are what make the JVM read the cgroup limit instead of the machine's total RAM."

The number 75 is the interesting part, but the word "container's" is the load-bearing part. Without `UseContainerSupport` the percentage is applied to the wrong base, and the result is not a slightly-wrong heap — it is a heap several times larger than the memory the container is allowed to touch.

| Symbol | What it is |
|--------|------------|
| `-XX:+UseContainerSupport` | Makes the JVM read the cgroup memory limit as "available RAM". Default on since Java 10 |
| `-XX:MaxRAMPercentage=75.0` | Max heap as a percentage of that available RAM |
| Available RAM | The container's `resources.limits.memory` when container support is on; the *host's* total RAM when it is off |
| The other 25% | Metaspace, code cache, JIT buffers, thread stacks, direct/Netty off-heap buffers, GC structures |
| Default without the flag | `MaxRAMFraction=4`, i.e. 25% — applied to whatever the JVM believes "available" means |

**Walk one example.** The same JVM, the same 25% default, two different bases:

```
  Container limit 512 Mi, host has 8 GB RAM

  container support OFF:  heap = 25% x 8,192 Mi  =  2,048 Mi
                          container limit         =    512 Mi
                          -> JVM plans a heap 4x larger than the cgroup allows
                          -> OOMKill (exit 137) as soon as the heap actually grows

  container support ON,
  MaxRAMPercentage=75:    heap = 75% x   512 Mi  =    384 Mi
                          overhead budget          =    128 Mi
                          -> fits, with room for metaspace and off-heap

  Same flags at a 1,024 Mi limit:
                          heap = 75% x 1,024 Mi  =    768 Mi
                          overhead budget          =    256 Mi
```

The failure is silent in exactly the wrong way: the JVM starts fine, serves traffic fine, and
is killed by the kernel only once real load pushes the heap past the limit — so it looks like
a traffic-triggered application bug rather than a launch-flag mistake. Section 10 has this
exact incident. Note also that 75% is a ceiling, not a reservation: the 25% remainder is a
budget for everything the JVM allocates outside the heap, and a service with heavy Netty
direct buffers may need to drop to 60–65% rather than assume the default is safe.

### Kubernetes Deployment with Rolling Update

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: production
spec:
  replicas: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0    # no pod goes down before a replacement is ready
      maxSurge: 1          # at most 1 extra pod at a time (6 pods during rollout)
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
        version: "1.2.3"
    spec:
      terminationGracePeriodSeconds: 60   # must be > Spring's graceful timeout

      containers:
        - name: order-service
          image: company/order-service:1.2.3
          ports:
            - containerPort: 8080

          # Resource requests and limits
          resources:
            requests:
              cpu: "500m"         # 0.5 CPU guaranteed for scheduling
              memory: "512Mi"     # 512MB guaranteed
            limits:
              # CPU limit OMITTED intentionally:
              # CPU throttling (CFS cgroups) causes latency spikes even with available CPU
              # Only set CPU limits in environments where noisy neighbor is a real concern
              memory: "1Gi"       # OOM kill if exceeded; must set = prevents unbounded growth

          env:
            - name: JAVA_OPTS
              value: "-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
            - name: SPRING_PROFILES_ACTIVE
              value: "production"
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: password

          # Startup probe: allow up to 5 minutes for slow JVM startup
          startupProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            failureThreshold: 30    # 30 * 10s = 5 minutes max
            periodSeconds: 10

          # Liveness: is JVM alive? Only check trivial health
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            periodSeconds: 10
            failureThreshold: 3

          # Readiness: ready to serve traffic? Check dependencies
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            periodSeconds: 5
            failureThreshold: 3

          # Graceful shutdown: stop accepting new requests, drain in-flight
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sleep", "10"]  # wait for iptables to propagate
```

**What the numbers are telling you.** "Every probe is really a stopwatch — `failureThreshold × periodSeconds` is the only number that matters, and each of the three probes is buying a different guarantee with it."

Probes are usually tuned by adjusting whichever field looks wrong. The right way to read them is as three independent time budgets whose *products* must line up with three different realities: how slow startup can legitimately be, how long a hung process may keep running, and how long a sick pod may keep receiving traffic.

| Symbol | What it is |
|--------|------------|
| `periodSeconds` | How often kubelet re-runs the check |
| `failureThreshold` | Consecutive failures tolerated before the probe is declared failed |
| `failureThreshold × periodSeconds` | The actual budget — the only derived value worth reasoning about |
| startupProbe | Suppresses liveness and readiness entirely until it first passes. Runs once per container lifetime |
| livenessProbe | Failure ⇒ kubelet restarts the container |
| readinessProbe | Failure ⇒ pod is removed from the Service endpoint list, but keeps running |

**Walk one example.** The three budgets in this manifest, and what each one buys:

```
  startup    30 x 10 s  =  300 s  =  5 min   how slow a cold JVM may legitimately be
  liveness    3 x 10 s  =   30 s            how long a hung JVM keeps running before restart
  readiness   3 x  5 s  =   15 s            how long a sick pod keeps receiving traffic

  Worst-case bad-pod traffic exposure:
    up to 5 s until the next readiness check  +  15 s of failures  =  20 s
```

The startup probe is what makes the other two safe to keep tight. Without it, liveness must be
padded with `initialDelaySeconds` large enough to cover the slowest cold start — and that
padding then applies for the container's entire life, so a JVM that deadlocks after an hour of
uptime still waits out the full startup grace before being restarted. Splitting the budget in
two lets startup be generous (5 minutes, once) while liveness stays aggressive (30 seconds,
forever). Note the readiness period is deliberately shorter than liveness: pulling a pod out of
rotation is cheap and reversible, restarting one is not, so you want to detect "unhealthy"
faster than you conclude "dead."

### Pod Disruption Budget

```yaml
# Ensure at least 3 pods are always available during voluntary disruptions
# (node drains, cluster upgrades, rolling updates)
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service-pdb
spec:
  minAvailable: 3  # or use maxUnavailable: "20%"
  selector:
    matchLabels:
      app: order-service
```

### Horizontal Pod Autoscaler with Custom Metric (KEDA)

```yaml
# Standard HPA — scale on CPU
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70  # scale when average CPU > 70%
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # wait 5 min before scaling down
      policies:
        - type: Pods
          value: 1
          periodSeconds: 60            # remove 1 pod per minute (slow scale-down)
    scaleUp:
      stabilizationWindowSeconds: 0    # scale up immediately
      policies:
        - type: Pods
          value: 4
          periodSeconds: 60            # add up to 4 pods per minute

---
# KEDA ScaledObject — scale on Kafka consumer lag
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: order-processor-scaler
spec:
  scaleTargetRef:
    name: order-processor
  minReplicaCount: 1
  maxReplicaCount: 30
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: kafka:9092
        consumerGroup: order-processor-group
        topic: order-events
        lagThreshold: "100"  # 1 replica per 100 messages of lag
```

### Blue-Green Deployment

```yaml
# Blue deployment (current production)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service-blue
spec:
  replicas: 5
  template:
    metadata:
      labels:
        app: order-service
        slot: blue

---
# Green deployment (new version — staged, not yet serving traffic)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service-green
spec:
  replicas: 5
  template:
    metadata:
      labels:
        app: order-service
        slot: green

---
# Service points to blue initially
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  selector:
    app: order-service
    slot: blue  # switch to "green" for cutover — instant, one field change

# Cutover: kubectl patch service order-service -p '{"spec":{"selector":{"slot":"green"}}}'
# Rollback: kubectl patch service order-service -p '{"spec":{"selector":{"slot":"blue"}}}'
```

### 12-Factor App Configuration

```yaml
# All configuration via environment variables — no config in image
# The same image deploys to dev/staging/production with different env vars

apiVersion: v1
kind: ConfigMap  # non-sensitive configuration
metadata:
  name: order-service-config
data:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres:5432/orderdb"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  APP_FEATURE_NEW_CHECKOUT: "false"
  LOGGING_LEVEL_COM_COMPANY: "INFO"

---
apiVersion: v1
kind: Secret  # sensitive configuration (encrypted at rest in etcd)
metadata:
  name: order-service-secrets
type: Opaque
data:
  DB_PASSWORD: <base64-encoded>
  JWT_SECRET: <base64-encoded>
  REDIS_PASSWORD: <base64-encoded>
```

---

## 7. Real-World Examples

- **Google**: runs billions of containers; pioneered Kubernetes from internal Borg/Omega systems; canary deployments are standard for all Google services
- **Netflix**: Spinnaker for deployment pipelines; automated canary analysis (ACA) uses Kayenta to compare canary vs baseline metrics; automatic rollback on regression
- **Amazon**: blue-green deployments for all critical services; CodeDeploy supports traffic shifting with Lambda hooks for pre/post deployment validation
- **Cloudflare**: distroless containers with Go binaries; no shell access to running containers; all debugging via observability tooling, not exec

---

## 8. Tradeoffs

| Strategy | Downtime | Rollback Speed | Resource Cost | Complexity |
|----------|----------|----------------|---------------|------------|
| Recreate | Yes (brief) | Instant | 1x | Low |
| Rolling Update | Zero | Minutes (roll forward) | ~1.2x | Low |
| Blue-Green | Zero | Instant (switch selector) | 2x | Medium |
| Canary | Zero | Instant (set weight to 0%) | ~1.2x | High |

**Visualizing the tradeoff space** (resource cost vs. rollback speed):

```mermaid
quadrantChart
    title Deployment Strategy - Cost vs Rollback Speed
    x-axis Low Resource Cost --> High Resource Cost
    y-axis Slow Rollback --> Instant Rollback
    quadrant-1 Costly but instant
    quadrant-2 Cheap and instant
    quadrant-3 Cheap but slow
    quadrant-4 Costly and slow
    Recreate: [0.1, 0.85]
    Rolling Update: [0.3, 0.15]
    Blue-Green: [0.9, 0.85]
    Canary: [0.32, 0.8]
```
*Rolling Update and Canary carry the same ~1.2x resource cost, but only Canary gets instant rollback (set weight to 0%) — Rolling Update must roll forward again, costing minutes. Blue-Green's instant rollback costs a full 2x in standing resources, the premium for keeping two complete environments live.*

---

## 9. When to Use / When NOT to Use

Use **RollingUpdate** (default) for most deployments — low risk, zero downtime, minimal extra resources.

Use **Blue-Green** when: you need instant rollback capability, the deployment includes a database schema change that must be atomic with the application change, or you cannot tolerate the partial-state period during rolling updates (some pods running old version, some new).

Use **Canary** when: deploying high-risk changes, validating new features on a small percentage of users before full exposure, or running A/B tests. Requires Istio or Argo Rollouts for percentage-based traffic splitting.

Use **Recreate** only when: old and new versions cannot run simultaneously (incompatible DB schema, singleton-requiring stateful process).

**Decision path** (the guidance above, as a flow):

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["New deployment"]) --> q1{"Old + new versions<br/>cannot coexist?"}
    q1 -->|"yes"| recreate["Recreate<br/>brief downtime"]
    q1 -->|"no"| q2{"Need instant rollback or<br/>atomic DB cutover?"}
    q2 -->|"yes"| bluegreen["Blue-Green<br/>2x resources"]
    q2 -->|"no"| q3{"High-risk change or<br/>gradual validation?"}
    q3 -->|"yes"| canary["Canary<br/>progressive traffic shift"]
    q3 -->|"no"| rolling["Rolling Update<br/>default, ~1.2x cost"]

    class start io
    class q1,q2,q3 mathOp
    class recreate lossN
    class bluegreen frozen
    class canary req
    class rolling train
```
*Rolling Update sits at the end of the chain — you land there only after ruling out an incompatible-version migration (Recreate), a need for instant atomic rollback (Blue-Green), and a high-risk change that needs gradual exposure (Canary).*

Do NOT set CPU limits on JVM applications unless absolutely necessary — CFS (Completely Fair Scheduler) CPU throttling causes latency spikes and p99 degradation even when physical CPU is available. Set CPU requests (for scheduling) but omit CPU limits.

---

## 10. Common Pitfalls

**CPU limits causing latency spikes**: A team set `cpu: limit: 500m` on a Spring Boot service. Under moderate load, the service experienced p99 latency spikes of 500ms every 100ms (exactly matching CFS quota period of 100ms). The JVM's GC, JIT compilation, and request handling would occasionally need more than 500m CPU for a brief burst, causing throttling. Fix: remove CPU limits entirely; use CPU requests only. CPU requests ensure scheduling fairness; limits add throttling that hurts latency-sensitive services.

**Memory limit set too low causing OOM**: A team set `memory: limit: 512Mi` but did not set `MaxRAMPercentage`. The JVM defaulted to allocating 25% of total host RAM (e.g., 8GB) for heap = 2GB heap in a 512Mi container. The container was OOM-killed repeatedly. Fix: always set `-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport` on containerized JVMs. This tells the JVM to use 75% of the container's memory limit for heap, not the host's total RAM.

**No preStop sleep with rolling update**: During a rolling update, old pods received connection refused errors for 2-3 seconds after shutdown. iptables rules from kube-proxy take time to propagate to load balancers. The pod stopped accepting connections before all load balancers were updated. Fix: add `lifecycle.preStop.exec: ["/bin/sleep", "10"]` — this sleeps 10 seconds before SIGTERM, giving iptables propagation time.

**Blue-green with database migrations**: A team deployed a new service version with additive DB migrations (new columns) alongside the blue-green deployment. After switching to green, they ran `ALTER TABLE` to remove the columns that blue required. In the next incident, they rolled back to blue — but blue was reading columns that no longer existed. Fix: use expand-contract pattern for DB migrations; never run destructive migrations until the old version is fully decommissioned.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| Docker | Container build and runtime |
| Kubernetes | Container orchestration |
| Helm | Kubernetes package manager, templated deployments |
| Argo Rollouts | Advanced canary and blue-green deployments |
| KEDA | Kubernetes event-driven autoscaler (Kafka, RabbitMQ, etc.) |
| Skaffold | Local Kubernetes development workflow |
| Trivy | Container image vulnerability scanning |
| GraalVM | Native image compilation for fast startup |
| CRaC (Coordinated Restore at Checkpoint) | JVM checkpoint/restore for fast warm startup |

---

## 12. Interview Questions with Answers

**Q: What is a Docker multi-stage build and why do you use it?**
A multi-stage build uses multiple `FROM` instructions in a Dockerfile, each creating an intermediate image. You build the application in a JDK image (full development tools), then copy only the compiled artifacts to a JRE-only runtime image. The final image does not contain the JDK, Maven, source code, or test dependencies — only the JRE and the compiled JAR. This reduces image size from ~800MB (JDK) to ~180MB (JRE-only), reduces attack surface (no compiler/package manager in production), and speeds up image pulls. Layer caching: copy `pom.xml` and download dependencies before copying source code; the dependency layer is cached and only re-built when `pom.xml` changes.

**Q: What is the difference between maxUnavailable and maxSurge in rolling updates?**
`maxUnavailable` sets the maximum number of pods that can be unavailable during the rolling update. Setting it to 0 means no pod goes down until a replacement is ready and passing readiness checks — guarantees zero downtime but requires capacity for surge pods. `maxSurge` sets the maximum number of extra pods beyond the desired replica count. Setting `maxUnavailable=0, maxSurge=1` means: start a new pod (6 total), wait for it to be ready, then terminate an old pod (back to 5) — one pod at a time, always at least 5 healthy. `maxUnavailable=1, maxSurge=0` means: terminate one old pod first (4 total), then start a replacement — faster but with temporarily reduced capacity.

**Q: What is the difference between CPU requests and CPU limits in Kubernetes?**
CPU requests are used by the scheduler to find a node with sufficient available CPU — the pod is guaranteed this amount. CPU limits are enforced by CFS (Completely Fair Scheduler) cgroups at runtime: if a container exceeds its limit within a CFS period (100ms), it is throttled until the next period. For JVM applications, GC and JIT compilation cause brief CPU spikes. With a CPU limit set too low, these spikes trigger throttling, causing latency spikes visible as high p99 latency. Best practice for latency-sensitive JVM services: set CPU requests (for scheduling fairness) but omit CPU limits. Set memory limits always (prevent unbounded growth; OOM kill is predictable and recoverable via restart).

**Q: How should you size JVM heap in a Kubernetes container?**
Always use `-XX:+UseContainerSupport` (default in Java 11+) to make the JVM container-aware — it reads cgroup memory limits instead of host total RAM. Set `-XX:MaxRAMPercentage=75.0` to allocate 75% of the container's memory limit to the heap. Reserve 25% for: JVM overhead (metaspace, code cache, JIT buffers), off-heap memory (direct buffers, Netty allocations), and OS/container overhead. Example: 1GB container limit → 768MB heap, 256MB for JVM overhead. Without these flags on Java < 11 or with UseContainerSupport disabled, the JVM sees the host's 64GB RAM and tries to use 16GB for heap, causing immediate OOM kill.

**Q: What is a Pod Disruption Budget (PDB) and why is it important?**
A PDB specifies the minimum number (or percentage) of pods of a replicated application that must be available at any time. During voluntary disruptions (node drains for maintenance, cluster upgrades, rolling updates), Kubernetes respects PDBs: it will not drain a node if doing so would violate the PDB. Without a PDB, a node drain can terminate all pods of a deployment simultaneously if they all happen to be on the same node — causing a complete outage. Set `minAvailable` to at least the majority of replicas: for 5 replicas, set `minAvailable=3`. This ensures at least 3 pods are always serving traffic during any rolling operation.

**Q: When would you choose blue-green over rolling update deployment?**
Blue-green is preferred when: you need instant rollback capability (switch selector back in < 1 second vs rolling forward with rolling update taking minutes), you have database schema changes that are tightly coupled to the application version (both must cut over at the same time), your service cannot tolerate having two versions running simultaneously (conflicting session formats, incompatible API versions with shared state), or you are running acceptance tests against the new version in production before switching traffic. The cost is maintaining 2x the compute resources during the deployment. For most stateless services with backward-compatible changes, rolling update is simpler and equally safe.

**Q: How does HPA work and what are common pitfalls?**
HPA (Horizontal Pod Autoscaler) queries the metrics API (CPU from metrics-server, custom metrics from Prometheus Adapter or KEDA) every 15 seconds, calculates the desired replica count, and adjusts. Formula: `desiredReplicas = ceil(currentReplicas * (currentMetric / targetMetric))`. Common pitfalls: (1) flapping — CPU oscillates around the target, causing constant scale up/down; fix with `stabilizationWindowSeconds=300` for scale-down. (2) Slow reaction — by default HPA reacts in 15s intervals; for bursty traffic, set `scaleUp.stabilizationWindowSeconds=0` and high `maxSurge`. (3) Scale-down thrashing during rolling updates — temporarily inflated pod count confuses HPA; HPA should not overlap with deployment rollouts. (4) Not working with Karpenter/cluster autoscaler — HPA must add replicas before cluster autoscaler can add nodes; configure adequate pending pod time.

**Q: What is the 12-factor app methodology and which factors are most important for containerized Java services?**
The 12-factor app is a methodology for building cloud-native applications. The most critical factors for containerized Java services: Factor III (Config via environment variables — no config in image; inject via Kubernetes ConfigMap/Secret), Factor VI (Processes — stateless; store session in Redis, not in-memory), Factor IX (Disposability — fast startup for rapid scaling; graceful shutdown for zero-downtime deploys), Factor X (Dev/Prod parity — use Testcontainers to have dev/test match production infrastructure), Factor XI (Logs as streams — write to stdout/stderr; let the container platform capture and ship logs). These directly affect deployment reliability, scalability, and operability.

**Q: What can KEDA scale on that a standard Kubernetes HPA cannot?**
KEDA scales on external event-source metrics like Kafka consumer lag or SQS queue depth, while standard HPA is limited to metrics-server CPU/memory or a manually wired custom metrics adapter. The `ScaledObject` in §6 scales `order-processor` directly on Kafka consumer lag with `lagThreshold: "100"`, adding roughly one replica per 100 messages of lag up to `maxReplicaCount: 30`, a signal standard HPA cannot see without bolting on a Prometheus Adapter and hand-writing a custom metrics query. KEDA also supports `minReplicaCount: 0`, true scale-to-zero, which vanilla HPA cannot do at all since its floor is 1 replica. Under the hood, KEDA actually generates and manages a standard HPA object once replicas are above zero — it is a metrics adapter and scale-to-zero controller layered on top of HPA, not a replacement for it.

**Q: How does KEDA achieve scale-to-zero for a Kafka consumer, and why does that matter for cost?**
KEDA keeps a lightweight external poller running even at zero replicas, watching Kafka lag directly, then creates the first pod once lag crosses the trigger threshold. Below `minReplicaCount`, no HPA object drives scaling at all because HPA needs at least one running pod to sample metrics from; KEDA's operator instead polls the broker's consumer group offsets independently of any running pod, so a `ScaledObject` with `minReplicaCount: 0` can run zero pods, zero CPU and memory cost, during idle periods like overnight batch windows. This matters most for bursty, non-latency-critical consumers: a nightly reconciliation job that runs 20 minutes and sits idle the other 23.6 hours pays for compute only during that window instead of 1-3 replicas running around the clock. The tradeoff is cold-start latency — the first message in a new burst waits for a pod to schedule and the JVM to boot before it is consumed, so scale-to-zero is wrong for anything with a tight end-to-end SLA.

**Q: What happens when a PodDisruptionBudget makes a voluntary node drain impossible to complete?**
The drain command blocks indefinitely on the protected pods, because the eviction API refuses any eviction that would violate the PDB. If `order-service-pdb` requires `minAvailable: 3` out of 5 replicas but a node happens to host 3 of those 5 pods, draining it would drop availability to 2, so the eviction API returns 429 for those pods and `kubectl drain` retries in a loop until more replicas become available elsewhere. This is a frequent on-call surprise during cluster upgrades: a node stuck draining for 20-plus minutes is not a Kubernetes bug, it is the PDB doing exactly what it was configured to do, and the fix is temporarily scaling replicas up to give the drain slack, not lowering `minAvailable` under pressure. A PDB with `minAvailable` set equal to the total replica count is a common misconfiguration that makes every voluntary disruption impossible.

**Q: What are the three Kubernetes QoS classes and how do they determine which pods get OOM-killed first?**
Kubernetes assigns every pod to Guaranteed, Burstable, or BestEffort QoS based on its resource requests and limits, and BestEffort pods are OOM-killed first under node memory pressure. Guaranteed requires every container's memory and CPU requests to equal their limits; Burstable sets at least one request lower than its limit, which is the `order-service` deployment in §6 with `requests.memory: 512Mi` and `limits.memory: 1Gi`; BestEffort sets no requests or limits at all. When a node hits memory pressure, the kubelet's OOM killer scores primarily by QoS class and how far a pod is over its request — BestEffort dies first, then Burstable in order of most-over-request, and Guaranteed only as a last resort. A pod exceeding its own `limits.memory` gets OOM-killed regardless of QoS class or node pressure, since that is separate per-container enforcement, and leaving BestEffort pods with no memory limit at all is the riskiest configuration on a shared node.

**Q: When would you choose canary over blue-green, or the reverse, for a risky release?**
Choose canary when you want gradual, metrics-gated exposure at roughly 1.2x resource cost, and blue-green when every user must be on one version at a time with instant, atomic rollback. Canary, from the quadrant chart in §8, exposes a small percentage of real traffic to the new version while both versions run side by side during the ramp, ideal for validating a risky change against real user behavior, but it requires Istio or Argo Rollouts and a team disciplined enough to watch metrics at each step. Blue-green commits every user to one version at a time and is preferred when the change includes a database migration that must be atomic with the code, or when two versions running simultaneously would corrupt shared state — the 2x resource cost buys a rollback that is a single Service selector patch rather than a gradual ramp-down. A team facing slow-burn feature risk should reach for canary; a team facing an all-or-nothing schema cutover should reach for blue-green.

**Q: What do you give up operationally by switching to a distroless base image, and why is it usually still worth it?**
A distroless image has no shell and no package manager, so you lose the ability to exec in and debug interactively inside a running container. The image base table in §4 lists `gcr.io/distroless/java21` at roughly 75MB versus 180MB for `eclipse-temurin:21-jre` — the size difference comes almost entirely from stripping bash, apt, curl, and every other userland binary an attacker could otherwise use once inside, which also shrinks the CVE surface Trivy has to scan. Cloudflare's approach in §7 is the model for operating without shell access: all debugging happens through structured logs, metrics, and traces, or an ephemeral debug container attached to the pod's process namespace, rather than exec-ing directly into the running container. The tradeoff is a steeper learning curve for teams used to interactive debugging, but it removes an entire class of container-escape and credential-theft techniques that depend on a shell being present.

**Q: What is the startup ordering guarantee for Kubernetes init containers versus native sidecar containers?**
Init containers run one at a time to full completion strictly before any regular or sidecar container starts, and native sidecars then start and reach Ready before the main container's own startup begins. Classic init containers, used for one-shot setup like a DB-migration Job, execute sequentially in listed order, and a failing one blocks the pod indefinitely since the main containers never start at all. Kubernetes 1.29+ native sidecars are declared as `initContainers` with `restartPolicy: Always`, so a native sidecar starts, and Kubernetes waits for its readiness probe if it has one, before regular containers start, but then keeps running alongside them rather than exiting — solving the pre-1.29 problem where a legacy sidecar like the Fluentd container in the operational-patterns module had no ordering guarantee and could still be starting when the app began emitting logs it needed to ship. Before 1.29, teams worked around this with a startup script polling the sidecar's health endpoint; native sidecars make that workaround unnecessary.

**Q: Should secrets be injected as environment variables or mounted as files in a Kubernetes pod, and why?**
Mounted secret volumes are generally safer than environment variables, because env vars are visible in process listings, crash dumps, and child-process environments by default. The Secret YAML in §6 shows `DB_PASSWORD` and `JWT_SECRET` as `valueFrom.secretKeyRef` environment variables, which is convenient and matches the 12-factor config-via-environment principle, but any code path that logs the full environment can leak the secret into logs or APM traces. A mounted secret volume writes the value to a tmpfs file, the same pattern as the Vault-agent sidecar in the operational-patterns module, that the application reads explicitly at startup — it does not appear in `/proc/pid/environ`, is not inherited by child processes, and can be rotated in place without a pod restart if the app re-reads it periodically. Many teams land on a compromise: environment variables for low-sensitivity config and mounted volumes specifically for credentials, paired with etcd encryption-at-rest so the base64-encoded Secret object is not effectively plaintext in the cluster's datastore.

---

## 13. Best Practices

- Use Spring Boot's layered JAR feature for optimal Docker layer caching (`jarmode=layertools`)
- Set `JAVA_OPTS` via environment variable (not hardcoded in Dockerfile) for runtime tuning
- Scan images with Trivy in CI pipeline: `trivy image --exit-code 1 --severity HIGH,CRITICAL company/order-service:1.0.0`
- Run containers as non-root user (UID 1000) to limit container escape blast radius
- Use `readOnlyRootFilesystem: true` in SecurityContext where possible
- Pin base image versions to digest: `FROM eclipse-temurin:21-jre-jammy@sha256:...` for reproducibility
- Set `terminationGracePeriodSeconds` 2x the expected maximum request processing time
- Use `topologySpreadConstraints` to spread pods across AZs — prevents all pods on one failing AZ
- Label all deployments with `version` for canary traffic splitting and Kiali visualization

---

## 14. Case Study

**Problem**: A checkout service had 99.5% availability but needed 99.95% for SLA compliance. Root causes: rolling updates caused 5-second latency spikes during pod replacement (no preStop hook), CPU limits caused 10x p99 latency spikes during Black Friday (GC throttled by CFS), and a single-AZ deployment caused 30-minute outage during an AZ failure.

**Fixes applied**:
1. Added `preStop: sleep 10` — latency spikes during deploys dropped to zero
2. Removed CPU limits, set only CPU requests — p99 latency improved from 850ms to 80ms under full load
3. Added `topologySpreadConstraints` with `maxSkew=1` across AZs — pods automatically distributed across 3 AZs
4. Added `PodDisruptionBudget` with `minAvailable=3` out of 5 replicas — cluster upgrades no longer cause concurrent pod terminations
5. Switched from `maxUnavailable=1` to `maxUnavailable=0, maxSurge=1` — zero pods down during rolling updates

**Result**: Availability improved to 99.98% over the next quarter. Black Friday handled 3x normal load with p99 latency < 100ms throughout.
