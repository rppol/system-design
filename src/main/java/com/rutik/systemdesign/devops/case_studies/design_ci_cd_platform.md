# Design a Multi-Tenant CI/CD Platform

> A CI/CD platform is an airport: thousands of flights (jobs) land per hour, each needs a clean, isolated gate (runner) for a few minutes, and the control tower (scheduler) must never let two planes share a runway or leak a passenger's luggage into another's hold.

**Key insight**: The hard problem is *not* running one build — it is scheduling 10,000 concurrent, mutually-distrusting, short-lived jobs onto an elastic fleet where every runner must boot clean, die clean, and never leak a secret or a cache entry across a tenant boundary. The control plane is a queue-plus-scheduler problem; the data plane is a security-isolation-plus-cost problem. They are designed separately and fail differently.

---

## Intuition

**One-line analogy**: It is a giant restaurant kitchen where every order (job) gets a freshly sterilized, single-use cooking station (ephemeral runner) that is incinerated after the dish ships — and the head chef (scheduler) must keep 10,000 stations busy without ever serving one customer's allergen on another's plate.

**Mental model**: Split the system into two planes.

- **Control plane** — stateless, highly-available, durable. Receives webhooks, evaluates pipeline DAGs, enqueues jobs, tracks state, streams logs, gates secrets. It must survive a runner fleet wipe with zero data loss. Target 99.9% availability.
- **Data plane** — ephemeral, elastic, untrusted. The runner fleet. It scales from 200 to 12,000 vCPU in minutes, runs arbitrary user code (including malicious code), and is treated as a *hostile, disposable* environment. Every runner is single-use.

**Why this system exists**: Engineering velocity is gated by feedback loop latency. A 5,000-engineer org pushing code 20 times a day needs builds to start in under 30 seconds and finish in minutes, or developers context-switch and throughput collapses. Hand-rolling this on static Jenkins boxes produces snowflake runners, secret leakage, noisy-neighbor contention, and a $4M/year cloud bill from idle capacity. A purpose-built multi-tenant platform turns that into elastic, isolated, cost-attributed, sub-30-second-start infrastructure.

This case study assumes familiarity with the platform survey in [../ci_cd_platforms/README.md](../ci_cd_platforms/README.md) and artifact handling in [../artifact_and_registry_management/README.md](../artifact_and_registry_management/README.md).

---

## 1. Requirements Clarification

### Clarifying questions to scope the design

- *Single org or true multi-tenant SaaS?* — Multi-tenant: mutually-distrusting tenants share the fleet, so isolation is a hard requirement, not best-effort.
- *Do we run untrusted code (public forks, contractor PRs)?* — Yes; this forces VM-grade isolation for a subset (Firecracker), not just containers.
- *Who owns the compute — us or the tenant?* — Both models supported; bring-your-own-compute tenants need outbound-only (pull) runners that live in their VPC.
- *What is the spend ceiling?* — ~$130k/month at 17.6M jobs/month; Spot is non-negotiable as a lever.
- *Self-hosted Git or external SCM?* — We consume GitHub/GitLab webhooks; we do not host Git.

### Functional Requirements

1. **Webhook ingestion** — receive push / PR / tag / cron / manual-dispatch events from GitHub, GitLab, Bitbucket; deduplicate; expand into a pipeline DAG.
2. **Pipeline DAG execution** — parse YAML pipeline definitions, resolve job dependencies (`needs:`), run jobs in topological order, support fan-out/fan-in (1 build job → 200 parallel test shards → 1 deploy job).
3. **Ephemeral runner provisioning** — every job runs on a freshly-booted, single-use runner; runner is destroyed after the job, never reused across tenants.
4. **Artifact storage & passing** — jobs upload artifacts (binaries, test reports, coverage); downstream jobs download them; retention policy 90 days.
5. **Dependency / layer caching** — restore `node_modules`, `~/.gradle`, Docker layers, Go module cache keyed by lockfile hash; write-back on cache miss.
6. **Secrets injection** — inject per-tenant, per-environment secrets (registry creds, deploy tokens) scoped to the job, never persisted to disk, never visible to other tenants.
7. **Log streaming** — stream job logs to the UI in real time (sub-2s tail latency) and persist for 30 days.
8. **Concurrency & queueing** — enforce per-tenant concurrency limits and fair scheduling so one tenant's 5,000-job monorepo merge cannot starve a 3-job microservice team.
9. **Cost attribution** — meter runner-minutes per tenant/repo/team for chargeback.

### Non-Functional Requirements

| Requirement | Target |
|---|---|
| p50 job start latency (queue → runner executing first step) | < 8 s |
| p95 job start latency | < 30 s |
| p99 job start latency | < 90 s |
| Peak concurrent running jobs | 10,000 |
| Control-plane availability | 99.9% (43.2 min/month budget) |
| Job result durability (no lost results on infra failure) | 99.99% |
| Log tail latency | < 2 s |
| Secret leakage cross-tenant | 0 (hard requirement) |
| Cache hit ratio (warm repos) | > 85% |
| Runner boot-to-ready | < 20 s |

### Out of Scope

- The deployment target itself (k8s clusters, ArgoCD) — see [../gitops_argocd_flux/README.md](../gitops_argocd_flux/README.md).
- Source-control hosting (we consume webhooks, we do not host Git).
- Artifact *promotion* / registry internals — see [../artifact_and_registry_management/README.md](../artifact_and_registry_management/README.md).
- Test framework internals; we run whatever the user's YAML invokes.

---

## 2. Scale Estimation

### Job volume

```
Engineers:                5,000
Pushes/engineer/day:      20
Jobs per push (DAG avg):  8        (1 lint + 1 build + 4 test shards + 1 integration + 1 package)
-----------------------------------------------------------------
Jobs/day  = 5,000 × 20 × 8        = 800,000 jobs/day
Avg jobs/sec = 800,000 / 86,400   ≈ 9.26 jobs/sec   (24h average)
Working-hours avg (compress to 10h working window):
            800,000 / 36,000      ≈ 22.2 jobs/sec
Peak factor (post-merge storms, 9am pushes): 10×
Peak job start rate                          ≈ 222 jobs/sec
```

### Concurrency

```
Avg job duration:         4 min  (240 s)
Little's Law:  concurrent jobs = arrival_rate × duration
  Working-hours steady-state:  22.2 jobs/s × 240 s ≈ 5,300 concurrent jobs
  Peak burst:                  222 jobs/s × 240 s  ≈ 53,000 — but bounded by
                               per-tenant limits + fleet cap → design for 10,000 concurrent.
```

We design the fleet ceiling at **10,000 concurrent jobs**; bursts above that queue (with p95 start < 30 s preserved by fast autoscaling).

### Runner-minutes / month

```
Jobs/month = 800,000 × 22 working days   = 17.6M jobs/month
Runner-minutes/month = 17.6M × 4 min     = 70.4M runner-minutes/month
                                          = 1.17M runner-hours/month
```

### Compute sizing

Assume each runner = 2 vCPU / 8 GiB (most jobs); 15% are "large" 8 vCPU / 32 GiB.

```
Weighted avg vCPU/runner = 0.85×2 + 0.15×8 = 2.9 vCPU
Peak vCPU (10,000 concurrent) = 10,000 × 2.9 = 29,000 vCPU
On c6i.4xlarge (16 vCPU): 29,000 / 16 ≈ 1,813 instances at peak.
```

### Cache & artifact storage

```
Repos:                       ~3,000 active
Avg warm cache/repo:         2 GB  (node_modules + build cache + docker layers)
Cache storage hot tier:      3,000 × 2 GB           = 6 TB
Artifacts/job retained:      ~50 MB avg, 90-day retention
Artifact writes/day:         800,000 × 50 MB        = 40 TB/day
90-day artifact footprint:   40 TB × 90 × 0.4 (dedup/churn) ≈ 1.44 PB
                             (S3 Intelligent-Tiering; ~85% in IA/Glacier IR after 30d)
Log storage (30d):           800,000 × 22 × 30 × 200 KB ≈ 105 TB
```

### Cost per job (rough)

```
Runner cost:  4 min × (2.9 vCPU) on Spot c6i (~$0.024/vCPU-hr Spot) 
            = 4/60 hr × 2.9 × $0.024  ≈ $0.0046/job compute
Cache/artifact/egress amortized:               ≈ $0.0020/job
Control-plane amortized (17.6M jobs/mo):        ≈ $0.0008/job
-----------------------------------------------------------------
Fully-loaded cost ≈ $0.0074/job  → 17.6M jobs/mo ≈ $130k/month
```

Spot pricing and right-sizing the dominant lever; see §10 and [../cloud_cost_optimization_finops/README.md](../cloud_cost_optimization_finops/README.md).

---

## 3. High-Level Architecture

```
                            ┌──────────────────────────────────────────────────────────┐
   GitHub / GitLab  ─push─▶ │                    CONTROL PLANE (stateless, 99.9%)        │
   webhooks                 │                                                            │
        │                   │   ┌────────────┐   ┌──────────────┐   ┌────────────────┐  │
        └──────HTTPS───────▶│   │  Webhook   │──▶│   Pipeline   │──▶│   Scheduler /   │ │
                            │   │  Ingress   │   │   Compiler   │   │   Job Planner   │ │
                            │   │ (dedup,    │   │ (YAML → DAG, │   │ (topo sort,     │ │
                            │   │  HMAC verify)  │  needs: edges)│   │  fan-out/in)    │ │
                            │   └────────────┘   └──────────────┘   └───────┬─────────┘  │
                            │                                               │            │
                            │   ┌──────────────────────────────────────────▼─────────┐  │
                            │   │            Job Queue (per-tenant fair queues)        │ │
                            │   │     Postgres (durable state) + Redis (hot queue)     │ │
                            │   └───────┬──────────────────────────────────┬──────────┘  │
                            │           │ (pull dispatch)                  │ state/logs  │
                            └───────────┼──────────────────────────────────┼─────────────┘
                                        │                                  │
        ┌───────────────────────────────▼──────────────┐      ┌───────────▼─────────────┐
        │             DATA PLANE (ephemeral, hostile)    │      │     STATE & STORAGE     │
        │                                                │      │                         │
        │  ┌─────────────────┐   Runner Controller       │      │  Postgres (jobs, DAG,   │
        │  │ Runner Autoscaler│─ (ARC / custom operator) │      │   tenant config)        │
        │  │  (KEDA + HPA on  │   watches queue depth     │      │  Redis (queue, locks)   │
        │  │   queue depth)   │                           │      │  S3 (artifacts 1.4 PB)  │
        │  └────────┬─────────┘                           │      │  S3/EFS (cache 6 TB)    │
        │           │ provisions                          │      │  Loki/S3 (logs 105 TB)  │
        │           ▼                                     │      │  Vault (secrets)        │
        │  ┌───────────────────────────────────────────┐ │      └─────────────────────────┘
        │  │  Ephemeral Runner Pods (single-use)        │ │
        │  │  ┌──────────┐ ┌──────────┐ ┌──────────┐    │ │      gVisor / Kata / Firecracker
        │  │  │ runner-1 │ │ runner-2 │ │ runner-N │    │◀┼───── microVM isolation per job
        │  │  │ tenant A │ │ tenant B │ │ tenant A │    │ │
        │  │  └────┬─────┘ └────┬─────┘ └────┬─────┘    │ │
        │  └───────┼────────────┼────────────┼─────────┘ │
        └──────────┼────────────┼────────────┼───────────┘
                   │            │            │
                   ▼            ▼            ▼
            ┌─────────────────────────────────────────┐
            │  Cache Proxy   Artifact API   Secrets    │
            │  (keyed by     (presigned     Broker     │
            │   lockfile     S3 PUT/GET)    (short-TTL  │
            │   hash)                        tokens)    │
            └─────────────────────────────────────────┘
```

### Component inventory

| Component | Responsibility | Tech |
|---|---|---|
| Webhook Ingress | HMAC verify, dedup, rate-limit, enqueue raw event | Go service + Redis dedup set |
| Pipeline Compiler | YAML → validated DAG, resolve `needs:`, expand matrices | Go, CUE/JSON-schema validation |
| Scheduler / Job Planner | Topological scheduling, per-tenant fairness, concurrency limits | Go, Postgres + Redis |
| Job Queue | Durable per-tenant FIFO with priority; hot path in Redis Streams | Postgres (source of truth) + Redis Streams |
| Runner Autoscaler | Scale runner fleet on queue depth | KEDA → HPA, custom metric |
| Runner Controller | Create/destroy ephemeral runner pods/VMs, register/deregister | actions-runner-controller style operator |
| Ephemeral Runners | Execute one job, then self-destruct | Firecracker microVM or gVisor pod |
| Cache Proxy | Content-addressed restore/save keyed by lockfile hash | Go + S3/EFS backing |
| Artifact API | Presigned-URL upload/download, retention GC | Go + S3 |
| Secrets Broker | Mint short-TTL, job-scoped secrets | HashiCorp Vault + OIDC |
| Log Pipeline | Ingest, fan-out to UI (WebSocket) + object storage | Vector → Loki/S3 |

### Data-flow narrative

1. `git push` → GitHub fires a webhook → **Ingress** verifies HMAC, dedups on `(repo, sha, event)`, enqueues a raw event.
2. **Pipeline Compiler** fetches `.ci/pipeline.yml` at the SHA, validates schema, expands matrices, builds a DAG, writes it to Postgres.
3. **Scheduler** finds DAG nodes with satisfied dependencies, applies per-tenant concurrency limits and fair-share, and pushes runnable jobs onto per-tenant Redis Streams.
4. **Autoscaler** observes queue depth and scales the runner fleet; **Runner Controller** boots single-use runners that register and **pull** a job (pull, not push — see §5).
5. The runner mints job-scoped secrets from the **Secrets Broker** (OIDC, 15-min TTL), restores cache from the **Cache Proxy**, runs steps, streams logs, uploads artifacts via presigned URLs.
6. On completion the runner reports status, **self-destructs**, and the scheduler unblocks downstream DAG nodes (fan-in).

---

## 4. Component Deep Dives

### 4.1 Scheduler — fair-share, topological, concurrency-bounded

```
        DAG (one pipeline)                Per-tenant fair queue
        ┌───────┐                         ┌──────────────────────────┐
        │ lint  │──┐                       │ tenant A: [j1 j2 j3 ...] │
        └───────┘  │   ┌──────┐            │ tenant B: [j4]           │
        ┌───────┐  ├──▶│ test │──┐         │ tenant C: [j5 j6]        │
        │ build │──┘   │ ×200 │  ├─▶ deploy└──────────────────────────┘
        └───────┘      └──────┘  │                 │ weighted round-robin
                                  fan-in            ▼
                                              dispatch to runners
```

The scheduler dequeues runnable jobs but must enforce (a) DAG dependencies, (b) per-tenant max concurrency, and (c) global fleet cap, while preventing one tenant from starving others.

```go
// scheduler.go — fair-share dispatch loop (executable-shaped)
type Job struct {
    ID         string
    TenantID   string
    DAGNode    string
    DependsOn  []string // upstream node IDs
    Priority   int
}

type Scheduler struct {
    db          *pgxpool.Pool
    redis       *redis.Client
    fleetCap    int            // global: 10,000
    tenantLimit map[string]int // per-tenant concurrency cap
}

// dispatchTick runs every 250ms.
func (s *Scheduler) dispatchTick(ctx context.Context) error {
    running, _ := s.redis.Get(ctx, "fleet:running").Int()
    budget := s.fleetCap - running
    if budget <= 0 {
        return nil // fleet saturated; jobs wait
    }

    // Weighted round-robin across tenants prevents a 5,000-job monorepo
    // merge from starving a 3-job team.
    tenants := s.activeTenants(ctx)
    for budget > 0 && len(tenants) > 0 {
        progressed := false
        for _, t := range tenants {
            tRunning, _ := s.redis.Get(ctx, "tenant:"+t+":running").Int()
            if tRunning >= s.tenantLimit[t] {
                continue // this tenant is at its cap
            }
            job, ok := s.nextRunnable(ctx, t) // deps satisfied + FIFO within priority
            if !ok {
                continue
            }
            if err := s.enqueueForPull(ctx, job); err != nil {
                return err
            }
            s.redis.Incr(ctx, "fleet:running")
            s.redis.Incr(ctx, "tenant:"+t+":running")
            budget--
            progressed = true
            if budget == 0 {
                break
            }
        }
        if !progressed {
            break // no tenant could make progress this tick
        }
    }
    return nil
}

// nextRunnable returns the highest-priority job whose DAG deps are all DONE.
func (s *Scheduler) nextRunnable(ctx context.Context, tenant string) (Job, bool) {
    // SELECT ... FOR UPDATE SKIP LOCKED keeps multiple scheduler replicas
    // from grabbing the same job — critical for HA control plane.
    row := s.db.QueryRow(ctx, `
        SELECT id, tenant_id, dag_node, priority
        FROM jobs j
        WHERE tenant_id = $1 AND state = 'pending'
          AND NOT EXISTS (
            SELECT 1 FROM job_deps d
            JOIN jobs u ON u.id = d.upstream_id
            WHERE d.job_id = j.id AND u.state != 'done')
        ORDER BY priority DESC, created_at ASC
        FOR UPDATE SKIP LOCKED
        LIMIT 1`, tenant)
    var j Job
    if err := row.Scan(&j.ID, &j.TenantID, &j.DAGNode, &j.Priority); err != nil {
        return Job{}, false
    }
    return j, true
}
```

`FOR UPDATE SKIP LOCKED` is the load-bearing detail: it lets 5 scheduler replicas run concurrently for HA without double-dispatching a job.

---

### 4.2 Ephemeral Runner — single-use, tenant-isolated (BROKEN → FIX)

```
   Job assigned ──▶ boot microVM ──▶ register ──▶ pull job ──▶ run steps ──▶ report ──▶ DESTROY
                       (clean rootfs, no prior tenant state ever)
```

The most dangerous bug in a multi-tenant CI platform is reusing a runner across tenants. Here is a runner registration script that does exactly that, then the fix.

```bash
#!/usr/bin/env bash
# runner-entrypoint.sh
set -euo pipefail

# BROKEN: reuse the runner for the next job to "save boot time".
# This is catastrophic — tenant A's checked-out source, leaked env vars,
# Docker layers, and ~/.npmrc creds survive into tenant B's job.
run_loop_broken() {
  ./config.sh --url "$CI_URL" --token "$REG_TOKEN" --name "$(hostname)"
  while true; do
    ./run.sh --once || true     # run a job, loop for the next one
    # rootfs, /home, /tmp, env all PERSIST across iterations
  done
}

# FIX: single-use ephemeral runner. Register with --ephemeral so the
# control plane deregisters it after exactly one job, then the pod/VM
# is destroyed and a fresh one is booted from an immutable image.
run_once_fixed() {
  # JIT (just-in-time) registration token, single-use, 60s TTL.
  ./config.sh --url "$CI_URL" --token "$JIT_TOKEN" \
              --name "$(hostname)" --ephemeral --disableupdate

  # Run exactly one job; --once + --ephemeral guarantees the runner
  # deregisters and exits after one job.
  ./run.sh --once

  # Belt-and-suspenders: scrub anything sensitive before the kernel
  # reclaims the microVM (defense in depth; VM teardown is the real boundary).
  shred -u "$HOME/.npmrc" 2>/dev/null || true
  exit 0   # container exits → operator destroys pod → autoscaler may boot a NEW one
}

run_once_fixed
```

The Kubernetes side that guarantees one-job-per-pod:

```yaml
# runner-pod-template.yaml — enforced by the runner controller
apiVersion: v1
kind: Pod
metadata:
  generateName: runner-tenant-a-     # unique per job; never reused
  labels: { tenant: tenant-a, ephemeral: "true" }
spec:
  restartPolicy: Never               # FIX: pod dies after the job, never restarts in place
  runtimeClassName: gvisor           # syscall-level sandbox (or kata/firecracker for VM isolation)
  automountServiceAccountToken: false
  containers:
  - name: runner
    image: registry.internal/runner:immutable-2026.06.01  # pinned digest, read-only base
    env:
    - { name: CI_URL, value: "https://ci.internal" }
    - { name: JIT_TOKEN, valueFrom: { secretKeyRef: { name: jit-tok, key: token } } }
    resources:
      requests: { cpu: "2", memory: 8Gi }
      limits:   { cpu: "2", memory: 8Gi }   # hard limit — noisy-neighbor protection
    securityContext:
      runAsNonRoot: true
      readOnlyRootFilesystem: true          # writable work dir is an emptyDir, dies with pod
      allowPrivilegeEscalation: false
      capabilities: { drop: ["ALL"] }
  nodeSelector: { workload: ci-ephemeral }  # taint isolates runner nodes from control plane
```

See [cross_cutting/kubernetes_production_hardening.md](cross_cutting/kubernetes_production_hardening.md) for the full pod-security and node-isolation profile.

---

### 4.3 Cache Proxy — content-addressed, bounded (BROKEN → FIX on unbounded growth)

```
   restore: key = sha256(package-lock.json)
        runner ──GET /cache/{key}──▶ proxy ──▶ S3 (hit 85%) ──▶ tar -xzf
   save:   miss ──PUT /cache/{key}──▶ proxy ──▶ S3 + LRU index update
```

```go
// cache.go — content-addressed cache with eviction (the FIX is the eviction).
func cacheKey(lockfilePath string) string {
    data, _ := os.ReadFile(lockfilePath)
    sum := sha256.Sum256(data)
    return hex.EncodeToString(sum[:])
}

// BROKEN: save without bounds. 3,000 repos × dozens of branch keys × no TTL
// ballooned an S3 bucket to 340 TB and a $7,800/month bill in 6 weeks.
func saveCacheBroken(ctx context.Context, key string, body io.Reader) error {
    return s3PutObject(ctx, "ci-cache", key, body) // never evicted → unbounded
}

// FIX: scope keys, cap per-tenant footprint, and run LRU eviction on a TTL.
func saveCache(ctx context.Context, tenant, key string, body io.Reader, size int64) error {
    used, _ := tenantCacheBytes(ctx, tenant)
    const perTenantCap = 50 << 30 // 50 GiB per tenant
    if used+size > perTenantCap {
        if err := evictLRU(ctx, tenant, (used+size)-perTenantCap); err != nil {
            return err
        }
    }
    if err := s3PutObject(ctx, "ci-cache", tenant+"/"+key, body); err != nil {
        return err
    }
    // Track last-access for LRU; lifecycle rule also expires keys idle > 14 days.
    return redisClient.ZAdd(ctx, "cache:lru:"+tenant,
        redis.Z{Score: float64(time.Now().Unix()), Member: key}).Err()
}

func evictLRU(ctx context.Context, tenant string, need int64) error {
    var freed int64
    for freed < need {
        // oldest accessed first
        keys, _ := redisClient.ZRange(ctx, "cache:lru:"+tenant, 0, 9).Result()
        if len(keys) == 0 {
            break
        }
        for _, k := range keys {
            sz, _ := s3ObjectSize(ctx, "ci-cache", tenant+"/"+k)
            _ = s3DeleteObject(ctx, "ci-cache", tenant+"/"+k)
            redisClient.ZRem(ctx, "cache:lru:"+tenant, k)
            freed += sz
            if freed >= need {
                break
            }
        }
    }
    return nil
}
```

S3 lifecycle backstop (defense in depth against a leaking LRU index):

```json
{ "Rules": [{
  "ID": "expire-idle-cache",
  "Filter": { "Prefix": "" },
  "Status": "Enabled",
  "Expiration": { "Days": 14 }
}]}
```

---

### 4.4 Secrets Broker — short-TTL, job-scoped, OIDC

```
   runner ──OIDC JWT (signed by control plane, claims: tenant,repo,job,env)──▶
            Vault (verifies JWT, maps claims→policy) ──▶ 15-min lease token
            ──▶ runner reads only the secrets its policy allows ──▶ lease auto-revokes
```

```go
// secrets.go — job-scoped secret minting via Vault JWT auth.
// The control plane signs a JWT asserting the job's identity; Vault maps
// claims to a policy. No long-lived static creds ever live on a runner.
func (b *SecretsBroker) mintJobToken(ctx context.Context, job Job) (string, error) {
    claims := jwt.MapClaims{
        "tenant": job.TenantID,
        "repo":   job.Repo,
        "job":    job.ID,
        "env":    job.Environment,        // e.g. "staging" — gates which secrets
        "exp":    time.Now().Add(15 * time.Minute).Unix(),
        "aud":    "vault-ci",
    }
    signed, err := jwt.NewWithClaims(jwt.SigningMethodRS256, claims).
        SignedString(b.privateKey)
    if err != nil {
        return "", err
    }
    // Vault role "ci-job" binds: bound_claims.tenant + bound_claims.env → policy
    // path "secret/data/{tenant}/{env}/*". Tenant A can NEVER read tenant B's path.
    resp, err := b.vault.Logical().WriteWithContext(ctx, "auth/jwt/login", map[string]any{
        "role": "ci-job", "jwt": signed,
    })
    if err != nil {
        return "", err
    }
    return resp.Auth.ClientToken, nil // TTL 15m, max_ttl 30m, then auto-revoked
}
```

The Vault policy that enforces the tenant boundary:

```hcl
# ci-job.hcl — path is templated by the JWT's verified claims.
path "secret/data/{{identity.entity.aliases.auth_jwt.metadata.tenant}}/{{identity.entity.aliases.auth_jwt.metadata.env}}/*" {
  capabilities = ["read"]
}
# No wildcard across tenants. A leaked token for tenant A is useless against tenant B.
```

See [../secrets_management/README.md](../secrets_management/README.md) for rotation and the broader secrets model.

---

### 4.5 Webhook Ingress — verify, dedup, enqueue (BROKEN → FIX on duplicate runs)

```
   GitHub ──POST /hooks──▶ Ingress ──HMAC verify──▶ dedup(repo,sha,event) ──▶ enqueue
                              │                          │
                          reject 401                  Redis SETNX
                          on bad sig               (idempotency key, 10-min TTL)
```

GitHub retries webhook deliveries that do not get a 2xx within 10 seconds, and load balancers can replay a request on timeout. Without idempotency, a single push can trigger the same 8-job pipeline two or three times — tripling fleet load and producing duplicate deploys.

```go
// ingress.go — HMAC verification + idempotent enqueue.
func (i *Ingress) handle(w http.ResponseWriter, r *http.Request) {
    body, _ := io.ReadAll(io.LimitReader(r.Body, 1<<20)) // 1 MiB cap

    // 1. Verify HMAC-SHA256 signature — reject spoofed/forged events.
    sig := r.Header.Get("X-Hub-Signature-256")
    mac := hmac.New(sha256.New, i.webhookSecret)
    mac.Write(body)
    want := "sha256=" + hex.EncodeToString(mac.Sum(nil))
    if !hmac.Equal([]byte(sig), []byte(want)) {
        http.Error(w, "bad signature", http.StatusUnauthorized)
        return
    }

    evt := parseEvent(body)
    deliveryID := r.Header.Get("X-GitHub-Delivery") // unique per delivery attempt

    // BROKEN: enqueue every delivery. GitHub redelivers on timeout; an LB replay
    // re-fires the same push → the 8-job pipeline runs 2-3× → fleet load triples
    // and a deploy job ran TWICE, double-publishing a release artifact.
    // _ = i.enqueue(r.Context(), evt)  // no dedup

    // FIX: idempotency key on (repo, sha, event) via Redis SETNX with TTL.
    // Redelivery of the same logical event is a no-op; distinct pushes still pass.
    key := fmt.Sprintf("dedup:%s:%s:%s", evt.Repo, evt.SHA, evt.Type)
    ok, err := i.redis.SetNX(r.Context(), key, deliveryID, 10*time.Minute).Result()
    if err != nil {
        http.Error(w, "dedup store error", http.StatusServiceUnavailable)
        return
    }
    if !ok {
        w.WriteHeader(http.StatusOK) // already processed; ack so GitHub stops retrying
        return
    }
    if err := i.enqueue(r.Context(), evt); err != nil {
        i.redis.Del(r.Context(), key) // allow retry on enqueue failure
        http.Error(w, "enqueue failed", http.StatusServiceUnavailable)
        return
    }
    w.WriteHeader(http.StatusAccepted)
}
```

The ingress must ack within GitHub's 10 s window, so it does *only* verify + dedup + enqueue — all heavy work (clone, compile) is deferred to the async pipeline compiler.

---

### 4.6 Log Pipeline — real-time tail + durable archive

```
   runner ──stdout/stderr──▶ Vector sidecar ──┬──▶ WebSocket fan-out (UI tail, <2s)
                                              └──▶ S3 batched objects (30-day retention)
```

Logs have two consumers with opposite needs: the UI wants a sub-2-second live tail; storage wants large batched objects, not millions of tiny writes. The pipeline forks.

```yaml
# vector.toml — runner log sidecar config (executable-shaped).
[sources.runner_logs]
type = "file"
include = ["/var/log/ci/job-*.log"]

# Live tail: low-latency push to the log gateway WebSocket fan-out.
[sinks.live_tail]
type = "http"
inputs = ["runner_logs"]
uri = "http://log-gateway.ci.svc/ingest"
batch.max_events = 50          # flush every 50 lines OR...
batch.timeout_secs = 1         # ...every 1s, whichever first → <2s tail latency

# Durable archive: large batched objects to S3, gzip, 30-day lifecycle.
[sinks.archive]
type = "aws_s3"
inputs = ["runner_logs"]
bucket = "ci-logs"
key_prefix = "{{ tenant }}/{{ job_id }}/"
compression = "gzip"
batch.max_bytes = 10485760     # 10 MiB objects, not per-line PUTs (cost control)
batch.timeout_secs = 60
```

At 800k jobs/day × ~200 KB logs, per-line S3 PUTs would be ~hundreds of millions of requests/day at $0.005/1k PUTs — batching into 10 MiB objects cuts request cost by ~99%.

---

## 5. Design Decisions & Tradeoffs

### Decision 1 — Ephemeral vs persistent runners

- **Decision**: Ephemeral, single-use runners (one job per runner, then destroy).
- **Alternatives**: Persistent runner pool reused across jobs; warm-pool hybrid.
- **Rationale**: Persistent runners leak state (secrets, source, caches) across tenants and accumulate snowflake drift. Single-use is the only clean tenant-isolation boundary.
- **Consequences**: ~15 s boot cost per job; mitigated with a warm pool of pre-booted, *unassigned* runners (no tenant identity yet) so p95 start stays < 30 s.

### Decision 2 — Container (gVisor) vs microVM (Firecracker/Kata) isolation

- **Decision**: gVisor for the 85% of trusted internal jobs; Firecracker microVMs for untrusted forks / public PRs / anything running third-party code.
- **Alternatives**: Plain containers (cheap, weak); microVM for everything (strong, +200ms boot, more overhead).
- **Rationale**: Plain container escape (single shared kernel) is a cross-tenant breach. gVisor intercepts syscalls cheaply; Firecracker gives true VM isolation where the threat model demands it.
- **Consequences**: Two runtime classes to operate; routing logic in the scheduler keys off `trust_level`.

### Decision 3 — Pull vs push job dispatch

- **Decision**: Runners **pull** jobs (long-poll / register-and-claim).
- **Alternatives**: Control plane pushes a job to a chosen runner.
- **Rationale**: Pull decouples the control plane from runner lifecycle/health — a dead runner simply never pulls, no orphaned push to reconcile. It also lets runners live in customer VPCs behind firewalls (outbound-only).
- **Consequences**: Slightly higher dispatch latency (poll interval); solved with long-poll + Redis Streams `XREADGROUP` blocking reads.

### Decision 4 — Cache strategy: content-addressed S3 vs sticky local disk

- **Decision**: Content-addressed cache in S3 fronted by a regional proxy, keyed by lockfile hash.
- **Alternatives**: Sticky runners with local SSD caches; per-node NVMe cache.
- **Rationale**: Ephemeral runners have no local cache to inherit. S3 + lockfile-hash keys give cross-runner, cross-time hits at 85%+ without sacrificing the single-use model.
- **Consequences**: Network restore cost (~2–5 s for 2 GB over 10 Gbps); mitigated with regional S3 + gzip + parallel range GETs.

### Decision 5 — Queue: Postgres source-of-truth + Redis hot path

- **Decision**: Job state durable in Postgres; hot dispatch queue in Redis Streams.
- **Alternatives**: Pure Kafka; pure Redis; pure Postgres `SKIP LOCKED`.
- **Rationale**: Postgres gives transactional DAG state + `SKIP LOCKED` for HA scheduling; Redis Streams gives the low-latency fan-out runners poll. Kafka adds ops weight without the transactional DAG semantics.
- **Consequences**: Dual-write consistency to manage (outbox pattern: write Postgres in txn, project to Redis).

### Decision 6 — Spot vs on-demand fleet

- **Decision**: 80% Spot, 20% on-demand baseline, with interruption-aware draining.
- **Alternatives**: 100% on-demand (predictable, 3× cost); 100% Spot (cheap, interruption risk).
- **Rationale**: Jobs are short (4 min) and retryable; Spot interruption (2-min notice) is tolerable with checkpoint-and-requeue. Saves ~$1.8M/year vs on-demand at this scale.
- **Consequences**: ~1.5% of jobs hit interruption → automatic requeue; on-demand floor guarantees the control-plane-adjacent jobs.

### Decision 7 — Per-tenant fair-share vs strict FIFO

- **Decision**: Weighted round-robin fair-share across tenants.
- **Alternatives**: Global FIFO; strict priority.
- **Rationale**: Global FIFO lets one tenant's 5,000-job monorepo merge starve everyone for 20 minutes. Fair-share bounds head-of-line blocking.
- **Consequences**: A huge fan-out completes slightly slower under contention, by design.

### Comparison table

| Dimension | Ephemeral + gVisor | Ephemeral + Firecracker | Persistent pool |
|---|---|---|---|
| Tenant isolation | Strong (syscall) | Strongest (VM) | Weak (shared state) |
| Boot latency | ~5 s | ~7 s (+microVM) | ~0 s |
| Cross-tenant leak risk | Very low | Near zero | High |
| Cost/job | Low | Medium | Lowest (but unsafe) |
| Snowflake drift | None | None | High |
| Best for | Internal jobs | Untrusted/fork PRs | Never (multi-tenant) |

---

## 6. Real-World Implementations

**GitHub Actions — actions-runner-controller (ARC)**. GitHub's self-hosted runner story is the Kubernetes operator `actions-runner-controller`. It uses **just-in-time (JIT) ephemeral registration tokens** so each runner registers, runs exactly one job (`--ephemeral`), and deregisters — no reuse. The newer `gha-runner-scale-set` mode listens to GitHub's Actions service via a long-poll **listener** and scales runner pods on assigned-job count rather than CPU, giving sub-30 s starts. GitHub itself runs hosted runners as fresh VMs per job on Azure, booting from a pre-baked image with ~7,000 pre-installed tools to avoid per-job install latency.

**GitLab — autoscaling executors (Docker Machine → custom)**. GitLab CI's runner historically used the `docker+machine` executor to provision ephemeral cloud VMs per job and tear them down after, with an `IdleCount` warm pool to absorb bursts. GitLab.com runs the majority of CI on GCP, and published that they process well over **2 million CI/CD jobs per day**, leaning on autoscaling and aggressive caching keyed by `cache:key:files` (lockfile-hashed) to keep hit rates high. They moved off Docker Machine to a custom autoscaler (`gitlab-runner` Next Runner Auto-scaling / Taskscaler + Fleeting) for finer Spot-aware control.

**Buildkite — agent + your-own-compute**. Buildkite's control plane schedules; the **agent** runs in *your* cloud and pulls jobs (outbound-only, fits locked-down VPCs). Their `elastic-ci-stack-for-aws` provisions an Auto Scaling Group of ephemeral EC2 instances driven by queue depth via a Lambda that polls the Buildkite Agent Metrics API and sets ASG desired capacity. This pull + bring-your-own-compute model is why security-sensitive orgs adopt it — secrets never leave the customer account.

**Netflix — internal CI on Titus**. Netflix runs CI workloads on **Titus**, their container management platform on top of EC2, using ephemeral containers and heavy Spot usage. They published extensively on bin-packing and Spot interruption handling, draining jobs on the 2-minute interruption notice and requeuing — the pattern this design adopts.

**Shopify — build farm scale**. Shopify reported running a massive CI fleet for their Rails monolith where a single merge can fan out to thousands of test shards; they invested in **test selection / partitioning** and distributed caching to keep monorepo CI under control, and built tooling to bisect flaky tests at fleet scale. Their experience is the canonical example of why fair-share scheduling (Decision 7) exists.

---

## 7. Technologies & Tools

| Tool | Model | Isolation | Autoscaling | Best fit | Weakness |
|---|---|---|---|---|---|
| **GitHub Actions (ARC)** | Hosted control plane + self-hosted runners (k8s) | Ephemeral pod / VM | gha-scale-set on job count | GitHub-centric orgs | YAML expressivity limits; opaque hosted runner internals |
| **GitLab CI** | Integrated with GitLab; autoscaling runners | Ephemeral VM/container | Taskscaler/Fleeting, Spot-aware | GitLab users, monorepos | Runner config sprawl; tied to GitLab |
| **Jenkins** | Self-hosted controller + agents | Static or k8s ephemeral pods | Kubernetes plugin | Legacy, max plugin flexibility | Snowflake controllers; scaling pain; security burden |
| **Argo Workflows** | k8s-native DAG engine | Pod per step | k8s HPA / native | k8s-native ML/data pipelines | Not a CI-first UX; no native SCM webhooks/UI |
| **Tekton** | k8s-native CRDs (Task/Pipeline) | Pod per task | k8s native | Building a *platform* on k8s | Low-level; you assemble the UX yourself |
| **Buildkite** | Hosted control plane + your-compute agents | Whatever you provision (ASG/k8s) | Agent-metrics-driven ASG | Security-sensitive, bring-your-own-cloud | You operate the data plane |

For an org building its own multi-tenant platform, **Tekton or a custom controller + ARC-style ephemeral runners** gives the most control; **Buildkite** wins when data-plane sovereignty (secrets never leave your account) is the priority.

---

## 8. Operational Playbook

### (a) Quality gate / eval before rollout

Before shipping a runner image or scheduler change to the fleet, it passes a canary gate:

- **Golden pipeline suite**: 40 representative pipelines (Node, Go, Rust, Python, Docker-in-Docker, matrix fan-out) must pass on the new runner image with identical exit codes and within +10% wall-clock of baseline.
- **Isolation assertion**: an adversarial pipeline attempts to read `/proc`, mount host paths, reach the metadata endpoint (`169.254.169.254`), and read another tenant's Vault path — all must be denied. A single success blocks the rollout.
- **Canary fleet**: 2% of traffic to the new image for 30 minutes; auto-rollback if job failure rate rises > 0.5% absolute or p95 start latency > 30 s.

### (b) Observability — OTel span hierarchy for a CI job

```
Trace: pipeline-run (trace_id = pipeline_run_id)
└─ span: webhook.ingest            (attrs: repo, sha, event, dedup_hit)
└─ span: pipeline.compile          (attrs: dag_nodes=8, matrix_expanded=12)
   └─ span: job.schedule           (attrs: tenant, queue_wait_ms, fair_share_rank)
      └─ span: runner.provision    (attrs: runtime_class=gvisor, boot_ms, warm_pool_hit)
         └─ span: job.execute       (attrs: job_id, runner_id)
            ├─ span: secrets.mint    (attrs: vault_lease_ttl=900s)
            ├─ span: cache.restore   (attrs: cache_key, hit=true, bytes=2.1e9, restore_ms=3400)
            ├─ span: step.run        (attrs: step="npm test", exit_code=0, duration_ms)
            ├─ span: artifact.upload  (attrs: bytes=5.2e7, presigned)
            └─ span: runner.destroy   (attrs: reason=ephemeral_complete)
```

Key metrics and their cardinality discipline: emit `job_start_latency_seconds` histogram labeled by `tenant` and `runtime_class` only — **never** by `repo` or `job_id` (3,000 repos × buckets would explode series). See [cross_cutting/prometheus_cardinality_and_scale.md](cross_cutting/prometheus_cardinality_and_scale.md). SLO math (43.2 min/month control-plane budget, burn-rate alerts) follows [cross_cutting/slo_error_budget_math.md](cross_cutting/slo_error_budget_math.md).

### (c) Incident runbooks

**Runbook 1 — Queue backlog / start latency SLO burn**
- *Symptom*: p95 job start latency > 30 s for 5 min; queue depth climbing.
- *Diagnosis*: Check `fleet:running` vs `fleetCap`; check autoscaler — is it scaling? Spot capacity errors? Quota hit?
- *Mitigation*: Temporarily raise `fleetCap` and on-demand floor; if Spot is exhausted, flip the autoscaler's instance-type allocation to a broader pool (3+ instance families).
- *Resolution*: Add the exhausted AZ/instance family to the Spot allocation strategy `capacity-optimized`; raise account vCPU quota.

**Runbook 2 — Cross-tenant secret exposure suspected**
- *Symptom*: Job logs of tenant A contain a value resembling tenant B's secret, or alert from secret-scanner on egress.
- *Diagnosis*: Pull the Vault audit log for the runner's token; confirm which paths it accessed; verify the JWT claims matched the job. Check whether a runner was reused (it must not be).
- *Mitigation*: Immediately revoke the implicated Vault role's leases; quarantine the runner image; freeze the fleet for the affected tenant.
- *Resolution*: Root-cause the policy templating; rotate all tenant B secrets; add an isolation regression test to the §8(a) gate.

**Runbook 3 — Runner boot storm / thundering herd**
- *Symptom*: A monorepo merge enqueues 5,000 jobs; autoscaler tries to boot 1,000 nodes; cloud API throttles; nothing starts.
- *Diagnosis*: Cloud control-plane API 429s; node provisioning stalled; warm pool drained.
- *Mitigation*: Cap autoscaler scale-up rate (e.g. +200 nodes/min); rely on the warm pool to absorb the first burst; queue the rest within fair-share.
- *Resolution*: Pre-warm the pool before known merge windows; spread provisioning across regions/zones.

**Runbook 4 — Cache poisoning / corrupt cache wedging builds**
- *Symptom*: A whole tenant's builds fail at `npm ci` with checksum errors after a cache save.
- *Diagnosis*: A partial/corrupt object was saved under a cache key. Confirm via S3 ETag mismatch / truncated tar.
- *Mitigation*: Purge the offending cache key; builds repopulate on miss.
- *Resolution*: Make cache saves atomic (upload to `key.tmp`, verify checksum, then rename); add integrity check on restore. See supply-chain hardening in [cross_cutting/supply_chain_security_pipeline.md](cross_cutting/supply_chain_security_pipeline.md).

---

## 9. Common Pitfalls & War Stories

**1. Reused runner leaked a deploy token — $0 stolen but full rebuild of trust.** An anonymized fintech ran persistent runners to "save the 15 s boot". A PR from a contractor's repo landed on a runner that still had a production AWS deploy token in `~/.aws/credentials` from a prior job. The contractor's malicious `Makefile` exfiltrated it. Impact: emergency rotation of **1,200 secrets**, a 9-hour CI freeze, and an external security audit costing roughly **$85,000**. Fix: mandatory single-use ephemeral runners + short-TTL OIDC tokens (§4.4). See [cross_cutting/supply_chain_security_pipeline.md](cross_cutting/supply_chain_security_pipeline.md).

**2. Unbounded cache ballooned to 340 TB.** As shown in §4.3, no eviction + per-branch cache keys grew an S3 bucket from 6 TB to **340 TB in 6 weeks**, an unbudgeted **$7,800/month** surprise on the cloud bill. The team only noticed via a FinOps cost-anomaly alert. Fix: per-tenant 50 GiB caps + LRU + 14-day lifecycle expiry.

**3. Global FIFO let one merge starve everyone.** A 5,000-job monorepo merge entered a strict-FIFO queue and held the entire fleet for **23 minutes**. During that window ~600 other engineers' builds queued, and the company estimated **~150 engineer-hours of idle wait** that morning (at a loaded $100/hr ≈ **$15,000** of lost productivity in one incident). Fix: weighted fair-share (Decision 7).

**4. Spot interruptions silently failed jobs.** Before interruption handling, a region-wide Spot reclamation killed **~1,400 in-flight jobs** with no requeue; engineers saw red builds and re-pushed manually, doubling load and extending the incident to **40 minutes**. Fix: catch the 2-minute interruption notice, checkpoint state, mark the job `requeue`, and re-dispatch on a fresh runner; on-demand floor for the control-plane-adjacent path.

**5. Metric cardinality melted Prometheus.** Someone added `job_id` and `repo` as labels on the start-latency histogram. With 3,000 repos × 800k daily job_ids × 12 buckets, Prometheus ingested **>40M active series** and the TSDB OOM-killed, blinding on-call during a real outage. Fix: drop high-cardinality labels; aggregate per tenant/runtime_class only. See [cross_cutting/prometheus_cardinality_and_scale.md](cross_cutting/prometheus_cardinality_and_scale.md).

**6. Docker-in-Docker privileged escape.** A team enabled `--privileged` DinD for convenience on shared-kernel container runners; a crafted image broke out to the host and read other pods' tmpfs. No data was confirmed exfiltrated, but the **incident review cost ~120 engineer-hours** and forced a migration of all untrusted jobs to Firecracker microVMs (Decision 2). Fix: rootless buildkit / gVisor for trusted, Firecracker for untrusted; never `--privileged` on multi-tenant nodes.

---

## 10. Capacity Planning

### Bottleneck: runner fleet sizing

The dominant resource is runner vCPU. The steady-state fleet must satisfy job arrival × duration (Little's Law), with headroom for the autoscaler's reaction lag and Spot interruption replacement.

```
Required concurrent jobs  C = λ × T̄
  λ  = job arrival rate (jobs/sec)
  T̄  = mean job duration (sec)

Required vCPU             V = C × v̄ / U
  v̄  = mean vCPU per job
  U  = target node utilization (bin-packing efficiency), e.g. 0.75

Instances                N = ceil(V / vCPU_per_instance)

Headroom for autoscale lag + Spot churn: multiply N by 1.15.
```

### Worked example (peak)

```
λ_peak = 222 jobs/sec
T̄      = 240 s
C      = 222 × 240 = 53,280  → bounded by design cap to 10,000 concurrent

v̄      = 2.9 vCPU
U      = 0.75
V      = 10,000 × 2.9 / 0.75 = 38,667 vCPU

Instance: c6i.4xlarge = 16 vCPU, 32 GiB, on-demand $0.68/hr (us-east-1).
N        = ceil(38,667 / 16) = 2,417 instances
With 1.15 headroom: 2,780 instances at peak.
```

### Cost model (blended Spot)

```
Peak instances:        2,780  (only during ~2h/day peaks)
Steady-state avg:      ~600 instances (working-hours average, C≈5,300)
Spot price c6i.4xl:    ~$0.22/hr (≈ 32% of on-demand)
Blend: 80% Spot @ $0.22 + 20% on-demand @ $0.68 = $0.312/hr effective

Monthly runner-hours = 1.17M runner-hours (from §2)
  → on c6i.4xlarge, runner-hours ≈ instance-hours × 16/2.9 packing... 
    simpler: 1.17M runner-hours × (instance-vCPU basis):
    Effective instance-hours ≈ 1.17M × 2.9 / (16 × 0.75) = 282,750 instance-hours/mo
Monthly compute = 282,750 × $0.312 ≈ $88,200/month
vs 100% on-demand: 282,750 × $0.68 ≈ $192,270/month
Spot savings ≈ $104,000/month ≈ $1.25M/year.
```

Add ~$20k/month for cache/artifact/log storage + egress + control plane → **~$108k/month all-in**, matching the §2 per-job estimate. Node-level hardening (taints, PodDisruptionBudgets, topology spread for the runner ASG) follows [cross_cutting/kubernetes_production_hardening.md](cross_cutting/kubernetes_production_hardening.md). FinOps levers (right-sizing, Savings Plans on the on-demand floor) are in [../cloud_cost_optimization_finops/README.md](../cloud_cost_optimization_finops/README.md).

### Autoscaler tuning

- Scale-up signal: `pending_jobs / available_runner_slots > 1.2` for 30 s → add nodes (capped +200/min to avoid the boot storm in Runbook 3).
- Scale-down: node idle > 5 min and not draining a job → terminate (respect a 90 s grace for in-flight log flush).
- Warm pool: keep `max(200, 5% of current fleet)` pre-booted, tenant-unassigned runners to hold p95 start < 30 s.

---

## 11. Interview Discussion Points

**Q: Why ephemeral single-use runners instead of a reused pool, given the boot-cost penalty?**
Because reuse is the single largest cross-tenant breach vector — leftover secrets, source, caches, and Docker layers from a prior tenant survive into the next job. Single-use is the only clean isolation boundary; you pay ~15 s boot per job, which you hide behind a warm pool of tenant-*unassigned* pre-booted runners. The security guarantee is worth far more than the latency you reclaim by reusing.

**Q: How do you keep p95 job start latency under 30 s when the fleet must scale from 200 to 2,780 instances?**
You decouple "boot a runner" from "assign a job" with a warm pool: keep ~5% of the fleet pre-booted and tenant-unassigned so a burst of jobs binds to already-running runners in single-digit seconds. The autoscaler reacts to queue depth to refill the pool, capped at +200 nodes/min so you do not trigger cloud-API throttling. p95 start is dominated by warm-pool hits, not cold boots.

**Q: Pull vs push dispatch — why pull?**
Pull decouples the control plane from runner health and lets runners live behind firewalls (outbound-only), which is essential for bring-your-own-compute tenants. A dead runner simply stops pulling — there is no orphaned push to reconcile, no zombie assignment. The cost is a small poll latency, eliminated with long-poll / Redis Streams blocking reads.

**Q: How do you stop one tenant's 5,000-job monorepo merge from starving everyone?**
Weighted round-robin fair-share across tenants instead of global FIFO. Each dispatch tick distributes the available fleet budget across active tenants up to their per-tenant concurrency cap, so a giant fan-out gets a bounded share and small teams keep flowing. Strict FIFO caused a real 23-minute, ~$15k-productivity-loss incident; fair-share bounds head-of-line blocking by construction.

**Q: How do you guarantee zero cross-tenant secret leakage?**
Three layers: (1) single-use runners so no state survives a job; (2) short-TTL (15-min) job-scoped tokens minted via OIDC, where Vault policy paths are templated by *verified* JWT claims (`tenant`, `env`) so a token for tenant A literally cannot address tenant B's path; (3) runtime isolation (gVisor/Firecracker) blocking the metadata endpoint and host paths. The isolation is also continuously asserted by an adversarial pipeline in the pre-rollout gate.

**Q: gVisor vs Firecracker — when do you use each?**
gVisor (syscall interception, single shared kernel sandbox) for the ~85% of trusted internal jobs because it is cheap and boots fast. Firecracker microVMs (true VM isolation, separate kernels) for untrusted work — fork PRs, public contributions, anything running third-party code — because syscall sandboxing is a weaker boundary than a hypervisor. The scheduler routes on a `trust_level` attribute.

**Q: How is the control plane made HA while multiple scheduler replicas dispatch the same queue?**
Job state lives transactionally in Postgres, and replicas claim jobs with `SELECT ... FOR UPDATE SKIP LOCKED`, so two replicas never grab the same row. Redis Streams carries the hot dispatch path with consumer groups (`XREADGROUP`) for at-least-once delivery, and the durable Postgres state is the reconciliation source of truth. This gives 99.9% control-plane availability without double-dispatch.

**Q: A Spot interruption fires mid-job — what happens?**
The runner traps the 2-minute interruption notice, marks the job `requeue` in Postgres (idempotently), flushes logs, and exits. The scheduler re-dispatches the job onto a fresh runner; because jobs are short (4 min) and steps are designed idempotent, the user sees at most a small delay, not a failure. An on-demand floor protects the small set of non-retryable, control-plane-adjacent jobs.

**Q: How do you bound cache cost without killing the 85% hit rate?**
Content-address the cache by lockfile hash so identical dependency sets share one object across runners and time, then enforce per-tenant footprint caps (50 GiB) with LRU eviction plus a 14-day S3 lifecycle backstop. The hit rate stays high because the keys are stable across branches that share lockfiles, while eviction keeps any one tenant from growing unbounded — the fix for the 340 TB / $7,800-month incident.

**Q: What is the right cardinality discipline for CI metrics?**
Label histograms only by low-cardinality dimensions like `tenant` (thousands) and `runtime_class` (two), never `repo` or `job_id`. Putting `job_id` on a histogram produced 40M+ active series and OOM-killed Prometheus during a live outage. High-cardinality identifiers belong in traces/exemplars (linked from the span hierarchy), not metric labels. See the cardinality deep-dive cross-reference.

**Q: How do you handle a fan-out of 200 parallel test shards that then fan back in to one deploy job?**
The pipeline compiler expands the matrix into 200 DAG nodes with the deploy node depending on all 200 via `needs:`. The scheduler dispatches all 200 subject to the tenant's concurrency cap, and the deploy node only becomes runnable when `NOT EXISTS (upstream WHERE state != 'done')` — the same dependency predicate from §4.1. Fan-in is just the absence of any unfinished upstream node.

**Q: How would you detect and contain a poisoned cache that wedges an entire tenant's builds?**
Make cache saves atomic — upload to `key.tmp`, verify the checksum/tar integrity, then rename — so a truncated upload never becomes a live key, and verify integrity on restore. If a bad key slips through, the runbook purges that single key and builds repopulate on the next miss; you do not need to flush the whole tenant cache. This pairs with the supply-chain integrity controls in the cross-referenced hardening guide.

**Q: What is the dominant cost driver and the biggest lever to reduce it?**
Runner compute dominates (~$0.0046 of the ~$0.0074 per-job cost). The biggest lever is Spot — an 80/20 Spot/on-demand blend cuts compute roughly to 46% of all-on-demand, about $1.25M/year saved at this scale, with interruption-aware requeue making it safe. Secondary levers are bin-packing utilization (U from 0.6 → 0.75 is a ~20% fleet reduction) and cache hit rate (fewer rebuilds = shorter jobs = fewer runner-minutes).
