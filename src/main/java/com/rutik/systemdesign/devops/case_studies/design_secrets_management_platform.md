# Design a Secrets Management Platform

> A secrets platform is the bank vault and the teller window combined: it does not just lock valuables away, it hands out single-use, time-limited cashier's checks to whoever proves who they are at the counter. **Key insight:** the goal is not "store secrets safely" — it is to make long-lived static secrets *disappear* entirely, replacing them with short-TTL credentials minted on demand and auto-revoked, so a leaked credential is worthless within an hour.

---

## Intuition

Most breaches are not cryptographic failures — they are a database password sitting in a committed `.env` file, a cloud key in a Slack message, or an API token in a CI log that nobody rotated for three years. The fundamental problem is **static, long-lived, broadly-shared secrets**. One leak compromises everything, blast radius is unbounded, and you cannot tell who used the credential because everybody shares it.

A secrets management platform inverts this. Instead of an application *holding* a database password, it presents an identity (a Kubernetes ServiceAccount token, an OIDC JWT, an AWS IAM role) to a central authority. The authority verifies the identity, then **dynamically generates a brand-new database user** with a 1-hour TTL, hands it back, and remembers the lease. When the lease expires the user is dropped. No human ever sees the password; no password is ever written to disk in plaintext; every issuance is audited.

**Mental model:** think of it as three layers stacked on one identity plane.

```
  IDENTITY        →   AUTHORIZATION        →   GENERATION + LEASE
  (who are you?)      (what can you get?)      (here is a fresh, expiring secret)
  K8s SA / OIDC       policy: db-read          dynamic DB user, TTL=1h, revoke on expiry
  IRSA / JWT          path: database/creds/*    PKI cert, transit decrypt grant
```

**Why it exists:** at 5 services you can rotate by hand. At 3,000 services across 40 clusters, manual rotation is impossible, shared secrets are inevitable, and a single leaked static credential is an existential breach. The platform makes "rotate everything in 60 minutes" the *default behavior of the system*, not a heroic incident response.

This builds on [`../secrets_management/README.md`](../secrets_management/README.md) (the concept module) and [`../kubernetes_security/README.md`](../kubernetes_security/README.md) (workload identity).

---

## 1. Requirements Clarification

### Functional Requirements

- **FR1 — Dynamic database credentials.** Issue per-request, per-service database users (Postgres, MySQL, MongoDB, Redis) with bounded TTL. The platform creates the user, sets grants, returns credentials, and revokes on lease expiry.
- **FR2 — PKI / certificate issuance.** Act as an intermediate CA. Issue X.509 leaf certs for mTLS between services with short TTL (24h–72h), auto-renewed before expiry.
- **FR3 — Encryption-as-a-service (transit).** Encrypt/decrypt/sign/HMAC payloads without the app ever holding the key material. Support key rotation and rewrapping of historical ciphertext.
- **FR4 — Pluggable auth methods.** Authenticate workloads via Kubernetes ServiceAccount JWT, generic OIDC, AWS IRSA, and short-lived AppRole for legacy/CI. No static auth tokens for apps.
- **FR5 — Static-secret sync (KV).** Hold KV secrets (third-party API keys that cannot be made dynamic) and sync them into clusters via the External Secrets Operator (ESO), refreshed on an interval.
- **FR6 — Rotation.** Automatic rotation of root/static credentials (DB root, cloud keys) on a schedule, and lease-driven rotation of dynamic creds.
- **FR7 — Full audit.** Every read, lease grant, revoke, login, and policy change is logged with request hash, identity, path, and timestamp — tamper-evident, append-only.
- **FR8 — Revocation.** Revoke a single lease, all leases for an identity, or all leases under a path within seconds (incident response).

### Non-Functional Requirements

| NFR | Target |
|-----|--------|
| Scale | 3,000 services across 40 Kubernetes clusters, 8 regions |
| Throughput | 50,000 secret reads/sec peak (mostly cached token-authenticated KV + transit) |
| Dynamic-cred issuance latency | p99 < 100 ms (DB user creation is the slow path) |
| Transit encrypt/decrypt latency | p99 < 10 ms |
| Availability | 99.99% (52 min/year downtime budget) |
| Default lease TTL | 1 hour (dynamic DB); max TTL 24h |
| Cert TTL | 48 hours, renew at 50% lifetime |
| Audit completeness | 100% — a request MUST NOT succeed if it cannot be audited |
| RPO / RTO (DR) | RPO ≤ 5 min, RTO ≤ 15 min |
| Unseal | Auto-unseal via cloud KMS; no manual key-shard ceremony on restart |

### Out of Scope

- Secret *consumption* correctness inside apps (app bugs that log secrets — covered by linting/CI).
- Human password vaults / SالسO for employees (1Password / Okta domain).
- HSM-backed root-of-trust hardware procurement (we use cloud KMS as the unseal root).
- Code-signing key custody (separate sigstore/cosign pipeline, see [`cross_cutting/supply_chain_security_pipeline.md`](cross_cutting/supply_chain_security_pipeline.md)).

---

## 2. Scale Estimation

### Read traffic and lease math

- 3,000 services. Assume each service replica calls the platform on startup and renews on TTL.
- Peak read rate target: **50,000 reads/sec**. Breakdown: ~70% transit encrypt/decrypt (35k/s), ~25% cached KV reads (12.5k/s), ~5% dynamic credential issuance/renewal (2.5k/s).

**Active lease count.** A lease lives for its TTL. With dynamic-cred issuance at 2,500/sec and a 1-hour (3,600 s) TTL:

```
active_leases = issuance_rate × TTL
             = 2,500 /s × 3,600 s
             = 9,000,000 active leases
```

That is the dominant scaling pressure. Each lease is a row in storage (~300 bytes: lease ID, path, identity, expiry, renewal count). Lease storage:

```
9,000,000 leases × 300 B ≈ 2.7 GB resident in storage backend
```

Lease expiration is a background sweep. At steady state, expiry rate ≈ issuance rate = 2,500/s of revocations — each revocation issues a `DROP USER`/`REVOKE` to the target DB. **The downstream databases, not Vault, are the real bottleneck.** (See §9 lease-storm war story.)

**Mitigation already baked in:** push transit + KV (cacheable, no lease) to the front; reserve dynamic creds for things that genuinely need them. If we naively put every service on 1-min TTL dynamic creds, active leases would be 60× lower per-lease but issuance rate 60× higher → 150k DROP USER/sec, which no database survives. TTL choice is a capacity decision, not a security knob alone.

### Storage backend (Raft)

```
KV secrets:        20,000 secrets × 4 KB avg          ≈ 80 MB
Leases:            9.0 M × 300 B                       ≈ 2.7 GB
Tokens:            ~200k active × 600 B                ≈ 120 MB
PKI cert metadata: 3,000 svc × 2 active certs × 1 KB  ≈ 6 MB
Policies/mounts/identity:                              ≈ 200 MB
------------------------------------------------------------
Raft DB working set                                    ≈ 3.1 GB
```

Comfortably fits in RAM on an `r6i.2xlarge` (64 GB). Raft snapshots ~3 GB every few minutes; keep on instance NVMe + ship to S3.

### Audit log volume

Every operation is audited. At 50k ops/sec, each audit entry ~1.2 KB JSON (request + response hashed):

```
50,000 /s × 1.2 KB = 60 MB/s
60 MB/s × 86,400 s  ≈ 5.0 TB/day raw audit
```

That is large. We log audit to a local file device *and* a socket device shipping to a SIEM. Compressed (~8:1) → ~640 GB/day stored, ~230 TB/year at 1-year retention. This dictates a dedicated log pipeline (see [`cross_cutting/prometheus_cardinality_and_scale.md`](cross_cutting/prometheus_cardinality_and_scale.md) for the metrics-vs-logs split).

### HA sizing and KMS unseal

- **Vault HA cluster:** 5 Raft voters (tolerates 2 failures) per region in primary; 3-node performance secondaries in read-heavy regions.
- **KMS unseal calls:** one per node start/restart. With auto-unseal, ~5 nodes × a few restarts/day = trivial (<100 KMS calls/day). KMS is *not* in the hot path — it wraps the master key only at seal/unseal, not per-request.
- **Token/lease throughput per node:** a tuned Vault node handles ~10–15k req/s. To serve 50k/s with headroom we need ~5 active-capable replicas via performance secondaries fronted by request forwarding.

---

## 3. High-Level Architecture

```
                         APPLICATIONS (3,000 svc / 40 clusters / 8 regions)
   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
   │ payments-svc │   │ orders-svc   │   │ ci-runner    │   │ batch-job    │
   │ (K8s SA)     │   │ (K8s SA)     │   │ (OIDC JWT)   │   │ (IRSA/AWS)   │
   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
          │ login + read     │                  │                  │
          ▼                  ▼                  ▼                  ▼
   ┌───────────────────────────────────────────────────────────────────────┐
   │                    AUTH METHODS  (identity verification)               │
   │  kubernetes auth   |   jwt/oidc   |   aws (IRSA)   |   approle (legacy) │
   │  TokenReview API   |  verify iss  |  STS GetCaller |  role_id+secret_id │
   └───────────────────────────────┬───────────────────────────────────────┘
                                    │ short-lived Vault token + policies
                                    ▼
   ┌───────────────────────────────────────────────────────────────────────┐
   │            VAULT HA CLUSTER  (5× Raft voters, active + standby)        │
   │   ┌─────────┐  raft  ┌─────────┐  raft  ┌─────────┐                    │
   │   │ ACTIVE  │◄──────►│ STANDBY │◄──────►│ STANDBY │  (+2 more)         │
   │   └────┬────┘        └─────────┘        └─────────┘                    │
   │        │ auto-unseal (cloud KMS wraps master key)                      │
   │        ▼                                                               │
   │   ┌─────────────────────── SECRETS ENGINES ───────────────────────┐   │
   │   │  database/   →  dynamic DB users, leases, TTL=1h               │   │
   │   │  pki/        →  intermediate CA, leaf certs TTL=48h            │   │
   │   │  transit/    →  encrypt/decrypt/sign, no key egress            │   │
   │   │  kv-v2/      →  versioned static secrets (3rd-party API keys)  │   │
   │   └───────────────────────────────────────────────────────────────┘   │
   │        │ audit (every op)                                              │
   │        ▼                                                               │
   │   ┌──────────────┐    ┌──────────────┐                                 │
   │   │ file device  │    │ socket device│──► SIEM / Splunk / Loki         │
   │   └──────────────┘    └──────────────┘                                 │
   └───────────────────────────────────────────────────────────────────────┘
        ▲ replicate (DR + perf secondaries)        ▲ generate creds on
        │                                           │ target backends
   ┌────┴─────────────┐                    ┌────────┴──────────┐
   │ DR SECONDARY     │                    │ Postgres / MySQL  │
   │ (warm standby,   │                    │ MongoDB / Redis   │
   │  another region) │                    │ (target DBs)      │
   └──────────────────┘                    └───────────────────┘

   IN-CLUSTER SYNC PATH (for static KV that must land as a native Secret):
   ┌────────────────────────────────────────────────────────────────────┐
   │ External Secrets Operator (ESO) per cluster                         │
   │   ClusterSecretStore ──auth(K8s SA)──► Vault kv-v2                   │
   │   ExternalSecret  ──poll 1h──► reconcile ──► native k8s Secret      │
   └────────────────────────────────────────────────────────────────────┘
```

### Component inventory

| Component | Responsibility |
|-----------|----------------|
| Auth methods | Verify workload identity (K8s TokenReview, OIDC iss/aud, AWS STS), map to policies |
| Vault HA cluster | Active node serves writes; Raft replicates; standbys forward |
| `database/` engine | Connect to target DBs as a privileged role; mint/drop dynamic users |
| `pki/` engine | Intermediate CA; sign leaf CSRs; maintain CRL/OCSP |
| `transit/` engine | Hold encryption keys; never export; encrypt/decrypt/sign on request |
| `kv-v2/` engine | Versioned static secret store with soft-delete + metadata |
| Lease manager | Track expiry, fire revocations, handle renewals |
| Audit devices | file + socket, fail-closed |
| Auto-unseal | Cloud KMS unwraps the master key on start |
| DR secondary | Replicated warm standby in another region |
| Performance secondaries | Read-scaling replicas in read-heavy regions |
| External Secrets Operator | Per-cluster reconciler syncing Vault KV → native k8s Secrets |

### Data flow (dynamic DB credential, happy path)

1. `payments-svc` pod has a projected ServiceAccount JWT. It POSTs to `auth/kubernetes/login` with that JWT and a role name.
2. Vault calls the cluster's `TokenReview` API to verify the JWT, maps the SA to policy `payments-db`, returns a Vault token (TTL 1h).
3. App requests `database/creds/payments-ro`. Policy allows it.
4. `database/` engine opens its admin connection to Postgres, runs `CREATE ROLE v-payments-<rand> ... VALID UNTIL ...`, grants read, returns username/password + a **lease ID**.
5. App connects to Postgres with those creds. At ~2/3 of TTL the Vault Agent / SDK renews the lease.
6. On expiry (or explicit revoke), the lease manager runs `DROP ROLE v-payments-<rand>`. Every step audited.

---

## 4. Component Deep Dives

### 4.1 Dynamic Database Secrets Engine + Lease Lifecycle

```
   app ──creds/payments-ro──► vault database/ engine
                                    │  uses ADMIN conn (rotated root)
                                    ▼
                              Postgres: CREATE ROLE v-payments-ab12cd
                                        GRANT SELECT ...
                                        VALID UNTIL now()+1h
                                    │
                              lease{id, expiry, revocation_stmt} ──► Raft
                                    │
                   ┌────────────────┴─────────────────┐
                   ▼ renew (2/3 TTL)                   ▼ expire/revoke
            extend VALID UNTIL                  DROP ROLE v-payments-ab12cd
```

Configure the connection with a role Vault itself rotates, so even the *admin* password is never static:

```hcl
# database secrets engine: connection + role (HCL via terraform vault provider)
resource "vault_database_secret_backend_connection" "payments" {
  backend       = "database"
  name          = "payments-pg"
  allowed_roles = ["payments-ro", "payments-rw"]

  postgresql {
    connection_url = "postgresql://{{username}}:{{password}}@pg-payments.internal:5432/payments?sslmode=require"
    username       = "vault_admin"          # rotated by vault, see below
    password       = "BOOTSTRAP_ONLY"        # replaced on first root rotation
    max_open_connections = 10
  }
}

resource "vault_database_secret_backend_role" "payments_ro" {
  backend = "database"
  name    = "payments-ro"
  db_name = vault_database_secret_backend_connection.payments.name
  creation_statements = [
    "CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';",
    "GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";"
  ]
  revocation_statements = [
    "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM \"{{name}}\";",
    "DROP ROLE IF EXISTS \"{{name}}\";"
  ]
  default_ttl = 3600   # 1h
  max_ttl     = 86400  # 24h
}
```

Rotate the admin password itself so no human knows it after bootstrap:

```bash
# one-time: vault takes ownership of vault_admin's password
vault write -force database/rotate-root/payments-pg
# now the bootstrap password is dead; only vault holds the admin credential
```

**Reading creds from a Go service (no static password anywhere):**

```go
func fetchDBCreds(ctx context.Context, c *api.Client) (user, pass, leaseID string, err error) {
    secret, err := c.Logical().ReadWithContext(ctx, "database/creds/payments-ro")
    if err != nil {
        return "", "", "", fmt.Errorf("read db creds: %w", err)
    }
    user = secret.Data["username"].(string)
    pass = secret.Data["password"].(string)
    return user, pass, secret.LeaseID, nil
}
// A background goroutine renews secret.LeaseID at 2/3 of LeaseDuration; on
// failure it re-reads fresh creds and reconnects the pool. No DROP storms
// because each replica holds exactly one lease, renewed in place.
```

#### BROKEN → FIX

**BROKEN** — the pattern this entire platform exists to kill. A shared static DB password committed as a plain Kubernetes Secret, base64 is *not encryption*:

```yaml
# k8s-secret.yaml  -- committed to git, used by 14 services
apiVersion: v1
kind: Secret
metadata:
  name: payments-db
type: Opaque
data:
  # echo -n 'Sup3rSecret!' | base64   <-- reversible, in git history forever
  username: cGF5bWVudHM=          # payments
  password: U3VwM3JTZWNyZXQh      # Sup3rSecret!
```

Problems: one password shared by 14 services (unbounded blast radius), in git history (irrevocable leak), never rotated, no audit of who used it, base64 trivially decoded by anyone with repo read.

**FIX** — per-service dynamic creds with 1h TTL, synced by ESO, nothing secret in git:

```yaml
# externalsecret-payments-db.yaml -- references a path, holds no secret value
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: payments-db
  namespace: payments
spec:
  refreshInterval: 30m
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: payments-db        # native k8s Secret ESO will create/refresh
    creationPolicy: Owner
  dataFrom:
    - sourceRef:
        generatorRef:           # generator = dynamic creds, fresh each refresh
          apiVersion: generators.external-secrets.io/v1alpha1
          kind: VaultDynamicSecret
          name: payments-db-dynamic
---
apiVersion: generators.external-secrets.io/v1alpha1
kind: VaultDynamicSecret
metadata:
  name: payments-db-dynamic
  namespace: payments
spec:
  path: database/creds/payments-ro   # 1h-TTL, per-service, auto-revoked
  provider:
    server: https://vault.internal:8200
    auth:
      kubernetes:
        mountPath: kubernetes
        role: payments
        serviceAccountRef:
          name: payments
```

Now: each service gets its own credential, scoped read-only, 1h TTL, auto-revoked, fully audited, and there is *nothing secret in the repo* — only a path reference. A leaked manifest is harmless.

---

### 4.2 Kubernetes Auth + IRSA (workload identity, zero static tokens)

```
  pod (projected SA JWT)──login──► auth/kubernetes
                                       │ TokenReview(JWT) ─► cluster API
                                       │ verify: bound SA, namespace, aud
                                       ▼
                                 issue Vault token (TTL 1h, policy=payments)

  EKS pod (IRSA)──────login──► auth/aws
                                       │ sts:GetCallerIdentity (signed)
                                       │ verify ARN matches bound role
                                       ▼
                                 issue Vault token (policy=batch)
```

Kubernetes auth uses *short-lived projected tokens*, not the legacy long-lived SA secret:

```yaml
# pod spec: projected SA token, audience-bound, 1h, auto-rotated by kubelet
volumes:
  - name: vault-token
    projected:
      sources:
        - serviceAccountToken:
            path: vault-token
            expirationSeconds: 3600
            audience: vault            # MUST match vault role's bound audience
```

```bash
# configure vault kubernetes auth to use the cluster's OIDC issuer (no static
# reviewer token stored in vault -> uses vault's own SA TokenReview)
vault write auth/kubernetes/config \
    kubernetes_host="https://$KUBE_API:443" \
    disable_iss_validation=false

vault write auth/kubernetes/role/payments \
    bound_service_account_names=payments \
    bound_service_account_namespaces=payments \
    audience=vault \
    policies=payments-db,payments-transit \
    ttl=1h
```

IRSA path for EKS workloads that should authenticate as an AWS identity:

```bash
vault write auth/aws/role/batch \
    auth_type=iam \
    bound_iam_principal_arn="arn:aws:iam::123456789012:role/batch-job" \
    policies=batch-kv \
    ttl=1h
```

#### BROKEN → FIX (the root-token anti-pattern)

**BROKEN** — apps authenticate with the Vault *root token* pasted into a Deployment env var:

```yaml
env:
  - name: VAULT_TOKEN
    value: "hvs.rootXXXXXXXXXXXXXXXXXXXX"   # root token: unlimited, no TTL, no audit identity
```

The root token has unlimited privileges, never expires, and every action shows up in audit as "root" — you lose all attribution, and one leaked manifest = total platform compromise.

**FIX** — root token is revoked after bootstrap; apps use K8s auth, get a 1h policy-scoped token tied to their identity:

```bash
# after initial setup, revoke root entirely (regenerate only via unseal ceremony)
vault token revoke -self
# apps now authenticate as themselves; every audit line shows the real SA
```

---

### 4.3 External Secrets Operator (ESO) Sync

```
 ClusterSecretStore (per cluster, 1 K8s-auth identity)
        │
        ▼
 ExternalSecret (per app) ── reconcile loop (refreshInterval) ──► Vault kv-v2
        │                                                            │
        ▼                                                            ▼
 native k8s Secret  ◄──────────── decoded values ─────────── kv read (audited)
        │
        ▼
 mounted/env into pod  (app sees an ordinary Secret; rotation is transparent)
```

```yaml
apiVersion: external-secrets.io/v1beta1
kind: ClusterSecretStore
metadata:
  name: vault-backend
spec:
  provider:
    vault:
      server: "https://vault.internal:8200"
      path: "kv"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "eso-reader"
          serviceAccountRef:
            name: external-secrets
            namespace: external-secrets
---
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: stripe-api-key
  namespace: payments
spec:
  refreshInterval: 1h
  secretStoreRef: { name: vault-backend, kind: ClusterSecretStore }
  target:
    name: stripe-api-key
    creationPolicy: Owner
  data:
    - secretKey: STRIPE_SECRET_KEY
      remoteRef:
        key: kv/data/payments/stripe
        property: secret_key
```

**Operational note:** ESO polls. With 3,000 ExternalSecrets at `refreshInterval: 1h`, that is ~0.83 reads/sec average — negligible. But a naive `refreshInterval: 10s` across 3,000 secrets = 300 reads/sec of pure polling overhead, and if all reconcile loops align you get thundering-herd spikes. Jitter the intervals and prefer event-driven (`PushSecret` / webhook) for high-churn paths.

---

### 4.4 Transit (Encryption-as-a-Service) + PKI

```
 transit: app sends plaintext ──► vault holds key, returns ciphertext
          key never leaves vault; app cannot exfiltrate key material
          rotate key → new version; old ciphertext still decryptable
          rewrap → re-encrypt old ciphertext to new key version (no plaintext)

 pki: app CSR ──► intermediate CA signs ──► leaf cert TTL=48h
      auto-renew at 50% lifetime; CRL/OCSP for revocation
```

```bash
# transit: create a rotatable key, encrypt without app ever seeing key bytes
vault secrets enable transit
vault write -f transit/keys/payments-pii

# encrypt (app passes base64 plaintext; gets versioned ciphertext)
vault write transit/encrypt/payments-pii \
    plaintext=$(echo -n "4111-1111-1111-1111" | base64)
# -> ciphertext: vault:v3:abc123...   (v3 = key version, enables rewrap)

# rotate the key; new writes use v4, old ciphertext still decrypts
vault write -f transit/keys/payments-pii/rotate
```

```bash
# pki: intermediate CA issues short-lived leaf certs for mTLS
vault secrets enable -path=pki_int pki
vault write pki_int/roles/svc-mtls \
    allowed_domains="svc.internal" \
    allow_subdomains=true \
    max_ttl=72h
vault write pki_int/issue/svc-mtls common_name="payments.svc.internal" ttl=48h
# returns cert + key + CA chain; agent re-issues at 24h (50% of 48h)
```

The transit pattern means a database breach yields only ciphertext: the attacker has `vault:v3:abc123...` and no way to decrypt without authenticating to Vault, which is audited and revocable.

---

## 5. Design Decisions & Tradeoffs

### D1 — Vault (self-hosted) vs AWS Secrets Manager vs cloud KMS

**Decision:** Self-hosted HashiCorp Vault as the org-wide control plane; cloud KMS only as the unseal root.
**Alternatives:** AWS Secrets Manager (managed), pure cloud KMS + per-cloud secret stores.
**Rationale:** We are multi-cloud / multi-cluster and need *dynamic* DB creds + transit + PKI under one policy model. AWS Secrets Manager has no dynamic DB engine of Vault's breadth, no transit, and locks us to AWS. Cloud KMS is key management, not secret lifecycle.
**Consequences:** We own Vault HA, upgrades, and unseal — real operational cost. We accept it for capability and portability.

### D2 — Dynamic vs static secrets

**Decision:** Dynamic by default; static KV only for third-party secrets that cannot be minted (Stripe key, SendGrid key).
**Alternatives:** All-static with scheduled rotation.
**Rationale:** Dynamic creds make a leak self-healing — TTL expiry *is* the rotation. Static rotation is a fragile cron with downtime windows.
**Consequences:** Load shifts onto target databases (DROP/CREATE USER). TTL becomes a capacity parameter (see §2 lease math).

### D3 — ESO vs Vault Agent sidecar vs CSI Secrets Store driver

**Decision:** ESO for KV→native-Secret sync; Vault Agent for dynamic creds that need in-place renewal; CSI driver avoided as default.
**Alternatives:** Vault Agent sidecar everywhere; CSI Secrets Store Provider.
**Rationale:** ESO gives a native `Secret` the whole ecosystem understands, central reconcile, GitOps-friendly. Agent is better when you need lease renewal without a k8s Secret (e.g., short-TTL DB creds in-memory). CSI couples secret lifetime to pod lifetime and complicates rotation.
**Consequences:** ESO materializes secrets as k8s Secrets (etcd) — must enable etcd encryption-at-rest and tight RBAC; see [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md).

### D4 — Raft (integrated storage) vs Consul storage backend

**Decision:** Raft integrated storage.
**Alternatives:** Consul as external storage backend.
**Rationale:** Raft removes a whole separate Consul cluster to operate; snapshots are first-class; HA is built in.
**Consequences:** Vault nodes are now stateful — careful with disk, snapshots, and node replacement. No more "stateless Vault, state in Consul" decoupling.

### D5 — Auto-unseal (cloud KMS) vs Shamir key shares

**Decision:** Auto-unseal via cloud KMS.
**Alternatives:** Shamir 5-of-3 manual unseal.
**Rationale:** At 99.99% and frequent pod restarts/autoscaling, a human key-shard ceremony on every restart is incompatible with availability. KMS auto-unseal restarts unattended.
**Consequences:** KMS becomes a hard dependency for unseal; if the KMS key is deleted, Vault is permanently sealed. Protect the KMS key with deletion protection + cross-region replica.

### D6 — Lease TTL = 1h (not 5m, not 24h)

**Decision:** 1h default dynamic TTL.
**Alternatives:** 5m (tighter blast radius), 24h (less DB churn).
**Rationale:** 1h balances leak-window vs DB DROP/CREATE load. 5m → 12× issuance = DB overload; 24h → leaked cred valid a full day.
**Consequences:** A leaked dynamic cred is valid up to 1h. Acceptable given full audit + instant revoke capability.

### D7 — Performance secondaries vs single big cluster

**Decision:** One primary + regional performance secondaries (Enterprise) / per-region clusters with replication.
**Alternatives:** Single global cluster.
**Rationale:** 50k reads/sec globally with cross-region latency demands local read serving.
**Consequences:** Replication lag means writes go to primary; reads local. Acceptable for read-heavy transit/KV.

### Comparison table

| Dimension | Vault (self-host) | AWS Secrets Mgr | GCP Secret Mgr | Azure Key Vault | KMS only |
|-----------|-------------------|-----------------|----------------|-----------------|----------|
| Dynamic DB creds | Yes (broad) | Limited (rotation lambda) | No | No | No |
| Transit (EaaS) | Yes | No | No | Partial (keys) | Encrypt only |
| PKI / CA | Yes | ACM PCA (separate) | CAS (separate) | Partial | No |
| Multi-cloud | Yes | No | No | No | No |
| Ops burden | High | None | None | None | Low |
| Audit granularity | Per-request | CloudTrail | Cloud Audit | Azure Monitor | CloudTrail |
| Cost model | Compute + license | Per-secret/mo | Per-version | Per-op | Per-op |

---

## 6. Real-World Implementations

- **Adobe** runs one of the largest known Vault deployments — publicly described handling tens of thousands of requests/sec across hundreds of namespaces, with a heavy emphasis on **transit encryption-as-a-service** so application teams never hold key material. Adobe engineers have written about scaling Vault's storage and the operational discipline of auto-unseal + Raft snapshots, and about per-team namespace isolation to keep blast radius bounded.

- **Cloudflare** built secret distribution around short-lived, frequently-rotated credentials and mTLS everywhere; their internal PKI issues short-TTL certs for service-to-service auth, mirroring the `pki/` short-cert + auto-renew pattern here. Cloudflare's post-incident writeups repeatedly stress that *no long-lived shared secrets* is the design goal, not an aspiration.

- **Shopify** uses Vault extensively for dynamic cloud and database credentials in a very high-throughput commerce environment; they have discussed Kubernetes-auth-based workload identity so that pods authenticate as themselves (ServiceAccount) rather than carrying static tokens — exactly the §4.2 pattern.

- **GitHub** publicly documented a 2022 incident where OAuth tokens issued to third-party integrators (Travis CI, Heroku) were abused; the remediation hardened **token scoping, rotation, and revocation** — a direct argument for short-TTL, narrowly-scoped, instantly-revocable credentials over broad static tokens. GitHub also runs secret-scanning to catch leaked static secrets, complementing (not replacing) dynamic issuance.

- **Roblox** has discussed using Vault for service credentials at scale; their well-known 2021 multi-day outage centered on Consul (used as a backend for HashiCorp tooling) under load, which is part of why integrated **Raft** storage (D4) is now preferred over Consul-backed Vault — fewer moving stateful systems to fail under stress.

Common thread: every one of these organizations treats "static long-lived secret" as a bug class, scopes credentials to identity, keeps TTLs short, and invests heavily in audit + revocation speed.

---

## 7. Technologies & Tools

| Tool | Type | Dynamic creds | Transit/EaaS | Sync to k8s | Multi-cloud | Best for |
|------|------|---------------|--------------|-------------|-------------|----------|
| HashiCorp Vault | Self-hosted control plane | Yes (DB/cloud/PKI) | Yes | via ESO/Agent/CSI | Yes | Org-wide dynamic secrets, EaaS, PKI |
| AWS Secrets Manager | Managed | Rotation-lambda only | No | via ESO | No (AWS) | AWS-only static secrets + rotation |
| GCP Secret Manager | Managed | No | No | via ESO | No (GCP) | GCP-only versioned static secrets |
| Azure Key Vault | Managed | No | Keys/HSM | via ESO/CSI | No (Azure) | Azure static secrets + HSM keys |
| External Secrets Operator | Sync controller | Passthrough (Vault gen) | No | Yes (native) | Yes (any backend) | Backend → native k8s Secret |
| SOPS / sealed-secrets | Encrypt-in-git | No | No | Yes (decrypt in cluster) | Yes | GitOps secrets without a live backend |

Guidance: use **Vault** as the source of truth for dynamic creds + transit + PKI; **ESO** to surface KV into clusters; **cloud secret managers** only where a managed single-cloud store is simpler and dynamic creds aren't needed; **SOPS/sealed-secrets** for small GitOps setups with no central server. CSI driver where you need pod-lifecycle-bound mounting; otherwise ESO.

---

## 8. Operational Playbook

### (a) Rotation & eval gate

- **Root credential rotation:** `vault write -force database/rotate-root/<conn>` on a 90-day schedule; the admin password Vault holds is replaced and never logged.
- **Transit key rotation:** rotate monthly (`transit/keys/<k>/rotate`); run a **rewrap job** to migrate old ciphertext forward so you can eventually retire old key versions.
- **PKI:** intermediate CA rotated yearly; leaf certs auto-renew at 50% TTL.
- **Eval/promotion gate:** any policy or auth-role change runs through a CI pipeline that (1) `vault policy fmt` + lints, (2) applies to a **staging Vault** and runs an integration test verifying a sample pod can read exactly the intended paths and *cannot* read others (negative tests), (3) only then promotes via PR. No direct prod policy edits. Tie this into the supply-chain controls in [`cross_cutting/supply_chain_security_pipeline.md`](cross_cutting/supply_chain_security_pipeline.md).

### (b) Observability

Scrape Vault's Prometheus telemetry. Critical signals:

```
vault_core_unsealed                  # 0 = SEALED, page immediately
vault_expire_num_leases              # active lease count -> capacity + lease-storm early warning
vault_token_count                    # active tokens
vault_core_handle_request_count      # request rate
vault_runtime_alloc_bytes            # memory pressure (leases live in RAM)
vault_audit_log_request_failure      # audit device failing -> fail-closed risk
vault_secret_lease_creation          # issuance rate -> downstream DB load
```

```promql
# alert: Vault sealed
max(vault_core_unsealed) < 1
# alert: lease growth abnormal (storm)
rate(vault_expire_num_leases[5m]) > 1000
# alert: audit device failing (requests will start failing closed)
increase(vault_audit_log_request_failure[1m]) > 0
```

Watch **lease-count cardinality** in your metrics pipeline — per-mount lease labels can explode; see [`cross_cutting/prometheus_cardinality_and_scale.md`](cross_cutting/prometheus_cardinality_and_scale.md). Trace login→read→lease as an OTel span chain so you can attribute p99 issuance latency to TokenReview vs DB CREATE ROLE. Define SLOs (issuance p99 < 100ms, availability 99.99%) and budget against them per [`cross_cutting/slo_error_budget_math.md`](cross_cutting/slo_error_budget_math.md).

### (c) Runbooks

**Runbook 1 — Vault sealed.**
Symptom: `vault_core_unsealed=0`, all auth/read failing, apps cannot start.
Diagnosis: check whether nodes lost quorum (Raft) or auto-unseal KMS call failed (`vault status`, KMS access logs, IAM on the unseal role).
Mitigation: if KMS-permission regression — restore the IAM policy; nodes auto-unseal on next start. If quorum loss — bring back a voter or restore from snapshot.
Resolution: confirm `vault_core_unsealed=1` on all nodes; verify a test login + read; postmortem the KMS/IAM change.

**Runbook 2 — Lease storm.**
Symptom: `vault_expire_num_leases` climbing fast; target DB CPU spiking from CREATE/DROP ROLE churn; issuance p99 breaching 100ms.
Diagnosis: identify the offending mount/role (`vault list sys/leases/lookup/...`); usually a crash-looping deployment re-authenticating every few seconds instead of renewing.
Mitigation: raise that role's `default_ttl`, fix the app to *renew* not re-read, or temporarily cap with `max_lease_ttl`. If a DB is melting, throttle issuance on that mount.
Resolution: confirm lease growth flat; the crash-loop fixed; DB CPU normal. Add an alert at the lease-growth threshold.

**Runbook 3 — Audit device blocking writes.**
Symptom: requests timing out / 500s; `vault_audit_log_request_failure` > 0. Vault is **fail-closed**: if it cannot write audit, it refuses the request.
Diagnosis: audit file device disk full, or socket device (SIEM) backpressured/unreachable.
Mitigation: free disk / fix the SIEM sink. Ensure ≥2 audit devices so one failing does not block (Vault succeeds if *any* enabled device writes — but if all fail, it blocks by design).
Resolution: disk/SIEM healthy, failures at zero, requests flowing. Add disk-usage and SIEM-reachability alerts. Never disable auditing to "fix" this.

**Runbook 4 — Leaked secret revocation (incident response).**
Symptom: a credential or token appears in a log/repo/paste.
Diagnosis: identify the lease/token/path from the leaked value; check audit logs for usage since exposure.
Mitigation: `vault lease revoke -prefix database/creds/<role>` (revoke all under a path) or `vault token revoke <id>`; for transit, rotate the key and rewrap; for KV, write a new version and let ESO propagate.
Resolution: confirm revocation in audit; if it was a static KV third-party key, rotate it upstream too. Document exposure window and what the audit log shows was accessed.

---

## 9. Common Pitfalls & War Stories

1. **The committed-Secret leak (industry-wide).** Static DB passwords in base64 `Secret` manifests in git are the single most common breach root cause. GitHub's own secret-scanning blocks **millions** of leaked credentials per year across public repos. Quantified pattern: one company found a 4-year-old Postgres password in git history shared by 14 services — a single repo-read leak would have exposed every payments table; remediation forced a platform-wide migration to dynamic creds (the §4.1 BROKEN→FIX). Pre-platform exposure window: effectively unbounded.

2. **Lease storm melts the primary database.** A crash-looping deployment re-authenticated every 3 seconds instead of renewing leases. At 200 replicas that is ~67 CREATE ROLE/sec *per service*; the DB spent 80%+ CPU on role DDL, latency for real queries blew past 2s, and the on-call paged for a "database outage" that was actually a secrets-issuance bug. Impact: ~40 min of degraded payments, est. low-six-figure revenue dip. Fix: renew-not-reauth + per-role TTL bump.

3. **Audit fail-closed mistaken for an outage.** A SIEM socket-device sink went unreachable; Vault correctly refused requests it could not audit, and ~600 services failed to fetch creds on rollout. The team's first instinct — "disable auditing to restore service" — would have created a *blind* window during an incident. Correct fix was a second file audit device as fallback. Impact: ~12 min platform-wide secret-read failures before the SIEM was restored.

4. **Auto-unseal KMS key over-locked.** An overzealous "least privilege" cleanup removed `kms:Decrypt` from the Vault unseal role. The running cluster was fine — until a routine node replacement restarted a pod that could not unseal, then a deploy rolled the rest. Result: a fully sealed cluster, RTO ~22 min while IAM was restored. Lesson: the unseal KMS grant is load-bearing; protect it with change review and deletion protection.

5. **CSI-mounted secret never rotates.** A team used the CSI Secrets Store driver and assumed rotation was automatic; secrets are mounted at pod start and, without rotation polling enabled, stayed stale for the pod's whole 30-day lifetime. A rotated upstream key meant half the fleet was using a dead credential after rotation. Impact: intermittent auth failures across ~120 pods until pods were force-recycled. Lesson: rotation semantics differ per delivery mechanism (ESO polls; CSI needs explicit rotation config; Agent renews leases).

6. **Over-broad policy = lateral movement.** A wildcard policy `path "secret/*" { capabilities = ["read"] }` handed to one service let a compromised pod read *every* team's KV. Tie this to supply-chain hardening — a compromised base image with that policy is catastrophic; see [`cross_cutting/supply_chain_security_pipeline.md`](cross_cutting/supply_chain_security_pipeline.md). Fix: namespace/path-scoped policies, negative integration tests in the §8 eval gate. Estimated exposure: ~2,000 secrets readable by a single service for the months the wildcard existed.

---

## 10. Capacity Planning

### Scaling formulas

```
active_leases        = issuance_rate (/s) × dynamic_TTL (s)
db_revocation_rate   ≈ issuance_rate (steady state)        # each expiry = 1 DROP
audit_volume (B/s)   = total_ops (/s) × avg_entry_bytes
raft_working_set (B) = active_leases×300 + active_tokens×600 + kv_bytes + meta
vault_active_nodes   = ceil(peak_read_rate / per_node_capacity)   # + Raft quorum
perf_secondaries     = ceil(regional_read_rate / per_node_capacity) per region
```

Per-node tuned throughput ≈ 10–15k req/s. Quorum: 5 voters tolerate 2 losses.

### Worked example

Targets: 50k reads/sec peak, 2,500 dynamic issuances/sec, 1h TTL, 8 regions.

```
active_leases     = 2,500 × 3,600 = 9,000,000
db_drop_rate      ≈ 2,500 /s  -> shard across 10 DB clusters = 250 DROP/s each (sustainable)
audit_volume      = 50,000 × 1.2 KB = 60 MB/s = 5.0 TB/day raw (~640 GB/day @8:1)
raft_working_set  ≈ 9.0M×300 + 200k×600 + 80MB + 200MB ≈ 3.1 GB  (fits r6i.2xlarge RAM)
vault_active_caps = 50,000 / 12,000 ≈ 4.2 -> 5 active-capable replicas
```

**Topology & cost (AWS, us-east-1 primary + 3 read regions):**

| Item | Spec | Qty | $/mo est. |
|------|------|-----|-----------|
| Primary Vault voters | `r6i.2xlarge` (8 vCPU/64 GB) | 5 | ~$1,520 |
| Perf-secondary nodes (3 regions ×3) | `r6i.xlarge` | 9 | ~$1,370 |
| DR secondary cluster | `r6i.xlarge` | 3 | ~$455 |
| EBS gp3 (snapshots + raft) | 200 GB ×17 | — | ~$340 |
| KMS unseal key + requests | 1 key, low volume | — | ~$5 |
| Audit storage (S3, 1yr, compressed) | ~230 TB | — | ~$5,300 |
| Cross-region replication egress | ~est. | — | ~$900 |
| **Total** | | | **~$10,400/mo** |

Compare to AWS Secrets Manager naive cost: 20,000 secrets × $0.40/secret/mo = $8,000/mo *plus* $0.05 per 10k API calls → 50k/s = 4.32B calls/day → wildly more on API charges alone. Vault's compute model wins decisively at this read volume; the value is dynamic creds + transit, not just price.

For node hardening, etcd-encryption (ESO materializes k8s Secrets), PodSecurity, and seccomp on Vault pods, follow [`cross_cutting/kubernetes_production_hardening.md`](cross_cutting/kubernetes_production_hardening.md).

---

## 11. Interview Discussion Points

**Q1. Why are dynamic secrets fundamentally safer than rotating static secrets?**
Dynamic secrets make the credential's *existence* time-bounded: TTL expiry is the rotation, executed automatically with no downtime window and no shared blast radius. Static rotation is a scheduled job that must coordinate every consumer simultaneously, fails open if any consumer misses the cutover, and still leaves a long-lived secret valid between rotations. With dynamic creds a leaked credential self-destructs within the TTL (1h here) and every issuance is uniquely attributable. Practically: reserve static for third-party keys you cannot mint, make everything you control dynamic.

**Q2. How do you choose the dynamic-credential TTL?**
It is a capacity decision as much as a security one. Active leases = issuance_rate × TTL, and each expiry triggers a DROP/REVOKE on the target DB at roughly the issuance rate. Shorter TTL shrinks the leak window but multiplies DB DDL load (5m TTL = 12× the CREATE/DROP churn of 1h). 1h is a common balance: short enough that a leak is contained, long enough that the database isn't drowning in role DDL. If you need a tighter window, shard the target DBs to absorb the revocation rate rather than blindly cutting TTL.

**Q3. Walk through Kubernetes auth — why is it better than a static token?**
The pod presents a projected ServiceAccount JWT (audience-bound, ~1h, kubelet-rotated). Vault verifies it via the cluster's TokenReview API, confirms the bound SA/namespace/audience match a role, and issues a short-lived Vault token scoped to that identity's policies. There is no static credential anywhere: the pod authenticates *as itself*, every action in audit shows the real ServiceAccount, and revoking the SA or rotating the projected token invalidates access. A static token, by contrast, is a long-lived bearer secret with no identity binding and no attribution.

**Q4. What is auto-unseal and what's the failure mode?**
Vault encrypts its master key with a cloud KMS key; on start it calls KMS to unwrap and unseal automatically, removing the manual Shamir key-shard ceremony. This is essential for availability when pods restart or autoscale. The failure mode is that KMS becomes a hard unseal dependency: lose `kms:Decrypt` permission and new/restarted nodes seal; *delete* the KMS key and Vault is permanently, unrecoverably sealed. Mitigate with deletion protection, cross-region KMS replicas, and treating the unseal grant as load-bearing in change review (see §9 war story 4).

**Q5. Why is Vault fail-closed on audit, and how do you operate around it?**
If Vault cannot write an audit record, it refuses the request — because a successful operation with no audit trail is worse than a failed one in a security system. Operationally you run at least two audit devices (e.g., a local file device plus a socket device to your SIEM); Vault succeeds if any enabled device writes, so a SIEM outage degrades to file-only rather than blocking. The anti-pattern is disabling auditing to "restore service" during an incident — that creates a blind window exactly when you most need attribution.

**Q6. ESO vs Vault Agent sidecar vs CSI driver — when each?**
ESO when you want a native Kubernetes Secret the whole ecosystem consumes, with central reconcile and GitOps workflow — ideal for KV. Vault Agent when you need lease *renewal* in place (short-TTL dynamic DB creds held in memory, renewed at 2/3 TTL) without materializing a k8s Secret. CSI Secrets Store when you want secrets mounted as files tied to pod lifecycle — but beware rotation must be explicitly configured, or mounted secrets go stale for the pod's lifetime (§9 war story 5). Most orgs use ESO for static + Agent for dynamic, and avoid CSI as a default.

**Q7. How does transit (encryption-as-a-service) change the breach calculus?**
The application sends plaintext and receives versioned ciphertext (`vault:v3:...`); the key never leaves Vault and the app physically cannot exfiltrate key material. A full database breach then yields only ciphertext — useless without authenticating to Vault, which is audited and revocable. Key rotation produces a new version while old ciphertext still decrypts, and a rewrap operation migrates old ciphertext to the new key without ever exposing plaintext. It moves the trust boundary from "every app that touches data" to "Vault plus its audit log."

**Q8. Raft vs Consul storage — why did the industry shift to Raft?**
Raft integrated storage puts Vault's state inside Vault itself with built-in HA, first-class snapshots, and no separate stateful system to operate. Consul-backed Vault meant running and scaling a *second* distributed system whose failure took Vault down — Roblox's multi-day 2021 outage centered on Consul under load is the canonical cautionary tale. Raft trades "stateless Vault nodes" for "stateful Vault nodes you must snapshot and replace carefully," which is the better trade for most operators: fewer moving parts that can fail under stress.

**Q9. How do you revoke a leaked credential fast, and what are the limits?**
For dynamic creds: `vault lease revoke -prefix database/creds/<role>` revokes every lease under a path in seconds, dropping the DB users. For tokens: `vault token revoke`. For transit: rotate the key and rewrap so the leaked key version can be retired. For static KV: write a new version (ESO propagates) and rotate the secret upstream too, since you don't control its lifecycle. The limit: a dynamic cred can still be used within its TTL until you revoke, so detection speed matters — pair revocation with audit-log analysis to determine what was accessed during the exposure window.

**Q10. How do you prevent over-broad policies enabling lateral movement?**
Scope policies to the narrowest path and capability the workload needs — never `secret/*` read for a single service (§9 war story 6). Enforce this with a CI eval gate: policy changes apply to a staging Vault and run *negative* integration tests proving a sample pod can read exactly its intended paths and is denied everything else, gated by PR review before prod. Combine with namespace isolation (Enterprise) or per-team mount paths so a compromised workload's blast radius is bounded to its own secrets.

**Q11. How do you scale reads to 50k/sec across regions?**
Most traffic is cacheable transit + KV, not dynamic issuance. Front the system with performance secondaries (or per-region clusters with replication) so reads serve locally without cross-region latency; the primary handles writes and replicates out. Per tuned node ~10–15k req/s, so ~5 active-capable replicas meet 50k/s with quorum headroom. Push transit and KV to the front (no lease, cacheable) and keep dynamic issuance — the expensive DB-touching path — to the ~5% of traffic that truly needs it.

**Q12. What's your DR strategy and how do you hit RPO ≤ 5 min / RTO ≤ 15 min?**
Run a warm DR secondary in another region receiving continuous replication (RPO bounded by replication lag, kept under 5 min). Snapshot Raft regularly to S3 as a second recovery path. On primary loss, promote the DR secondary (RTO target 15 min: DNS/endpoint cutover + verify unseal + smoke-test a login/read). The KMS unseal key must exist (replicated) in the DR region or the promoted cluster cannot unseal — a frequently-missed dependency. Rehearse promotion regularly; an unrehearsed DR plan is a guess.
