# Secrets Management

> Phase 4 — Infrastructure as Code & Config · Difficulty: Advanced

A secret is any credential that grants access — database passwords, API keys, TLS private keys, cloud access keys, signing keys. Secrets management is the discipline of **storing them encrypted, distributing them to workloads without exposing them, rotating them regularly, and auditing every access** — instead of the default failure mode of hardcoding them in source, env files, or Kubernetes manifests. The dominant tools are **HashiCorp Vault** (with its killer feature, *dynamic secrets*), the cloud-native managers (**AWS Secrets Manager**, **GCP Secret Manager**, **Azure Key Vault**), and the Kubernetes glue that delivers them — **External Secrets Operator (ESO)**, **Sealed Secrets**, and **SOPS**.

---

## 1. Concept Overview

Every secrets system answers four questions:

1. **Storage** — where does the secret live encrypted at rest? (Vault's encrypted backend, Secrets Manager, KMS-wrapped blobs.)
2. **Distribution** — how does a workload get the plaintext at runtime without it landing in Git, images, or logs? (ESO syncs to a K8s Secret, Vault Agent injects a file, the app calls the API.)
3. **Rotation** — how is the secret changed on a schedule or on compromise, ideally without downtime? (Managed rotation, dynamic short-lived credentials.)
4. **Audit** — who/what accessed which secret, when? (An audit log of every read.)

The most important conceptual split is **static vs dynamic secrets**:

- **Static**: a long-lived value (a DB password) stored and handed out. The risk is that it lives a long time, spreads to many places, and rotation requires coordination.
- **Dynamic** (Vault's signature feature): Vault *generates a credential on demand* with a short **lease TTL** (Vault's default lease TTL is 768h / 32 days, but dynamic DB creds are typically minutes-to-hours), and *revokes* it when the lease expires. The app gets a unique, short-lived credential; there's nothing long-lived to leak.

In Kubernetes specifically, native `Secret` objects are only **base64-encoded (not encrypted)** in etcd by default, so the ecosystem grew tools to bridge a real secrets backend into the cluster: **ESO** (pull from Vault/cloud into K8s Secrets), **Sealed Secrets** (encrypt secrets so they can live in Git), and **SOPS** (encrypt files with KMS/age for GitOps).

---

## 2. Intuition

> **One-line analogy**: A static secret is a house key you copy and hand to every contractor — it works forever, and you've no idea how many copies exist or who still has one. A dynamic secret is a hotel keycard the front desk mints when you check in: it's unique to you, stops working at checkout, and the desk logs every door it opened. Secrets management is running the front desk instead of cutting keys.

**Mental model**: Secrets management inserts a trusted broker between workloads and the credentials they need. The broker holds the encrypted material, authenticates the requester (by cloud IAM role, Kubernetes service account, etc.), hands out the secret (or a freshly-minted short-lived one), and logs the access. The secret never lives in Git, in the container image, or in a long-lived env var if you can help it.

**Why it matters**: Hardcoded secrets are the single most common breach vector — leaked GitHub tokens, AWS keys in public repos, passwords in Docker images. They can't be rotated without a code change, they're invisible to audit, and one leak compromises everything that shares the key. A real secrets system makes leaks rare, blast radius small (short TTLs), and rotation routine.

**Key insight**: **The best secret is one that doesn't exist long enough to leak.** Dynamic, short-lived credentials beat any amount of careful handling of a long-lived one — if a credential is valid for 15 minutes and unique per workload, a leaked copy is nearly worthless and revocation is automatic. Static secrets are a fallback; design toward dynamic and ephemeral.

---

## 3. Core Principles

1. **Never hardcode** — no secrets in source, images, manifests, env files, or logs.
2. **Encrypt at rest and in transit** — KMS-backed storage, TLS everywhere.
3. **Least privilege** — each workload reads only the secrets it needs, scoped by IAM role / service account.
4. **Short-lived over long-lived** — prefer dynamic secrets and tight lease TTLs; minimize blast radius.
5. **Rotate routinely and on compromise** — rotation must be automated and ideally zero-downtime.
6. **Audit every access** — a tamper-evident log of who read what, when.
7. **Centralize the source of truth** — one secrets backend; everything else references it (don't copy values around).

---

## 4. Types / Architectures / Strategies

### Secret managers compared

| Tool | Dynamic secrets | Rotation | K8s integration | Notable |
|------|-----------------|----------|-----------------|---------|
| HashiCorp Vault | Yes (DB, AWS, PKI, SSH) | Lease-based + rotation | Vault Agent / CSI / ESO | Most powerful; self-hosted ops cost |
| AWS Secrets Manager | No (rotation via Lambda) | Built-in (RDS rotation) | ESO / Secrets Store CSI | Native AWS, ~$0.40/secret/mo |
| GCP Secret Manager | No | Manual/scheduled | ESO / CSI | Versioned, IAM-scoped |
| Azure Key Vault | No (managed identities help) | Built-in | ESO / CSI | Keys + secrets + certs |
| AWS SSM Parameter Store | No | Manual | ESO / CSI | Cheap (free standard tier) |

### Kubernetes delivery patterns

| Pattern | How it works | Secret lives in Git? |
|---------|--------------|----------------------|
| External Secrets Operator (ESO) | A `SecretStore` + `ExternalSecret` CR; operator pulls from Vault/cloud and syncs a native K8s Secret (default `refreshInterval` e.g. 1h) | No — only a *reference* |
| Sealed Secrets | `kubeseal` encrypts a Secret to a cluster-specific public key; the controller decrypts | Yes — ciphertext only |
| SOPS | Encrypt YAML/JSON files with KMS/age; Flux/ArgoCD decrypt at apply | Yes — ciphertext only |
| Secrets Store CSI Driver | Mounts secrets from Vault/cloud as a volume | No — mounted at runtime |
| Vault Agent Injector | Sidecar/init injects secrets into a file/env from Vault | No — fetched at runtime |

### Static vs dynamic

| Aspect | Static secret | Dynamic secret (Vault) |
|--------|---------------|------------------------|
| Lifetime | Long-lived (months/years) | Short lease (minutes/hours) |
| Per-workload uniqueness | Usually shared | Unique per request |
| Leak blast radius | Large, until manual rotation | Tiny; auto-revoked at lease end |
| Revocation | Manual rotation everywhere | Automatic (lease expiry) |
| Example | Stored DB password | Vault mints a DB user on demand |

---

## 5. Architecture Diagrams

**Dynamic secrets with Vault (the front-desk model)**

```mermaid
sequenceDiagram
    participant App as App Pod
    participant Vault as Vault
    participant DB as Database

    App->>Vault: auth (k8s service account JWT)
    Vault-->>App: short-lived DB credential (lease TTL 1h)
    App->>DB: connect using that credential
    Note over App,DB: every access is audit-logged
    Vault-->>DB: lease expires, auto-revoke DB user
    Note over App,DB: nothing long-lived exists to leak
```

Vault authenticates the pod by its Kubernetes service account, mints a database user unique to that request with a 1-hour lease, and revokes it automatically when the lease ends — the "front-desk" model from Section 2's intuition.

**External Secrets Operator (ESO) in Kubernetes**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    git(["Git<br/>ExternalSecret ref only"]) -.->|configures| eso("External Secrets<br/>Operator")
    src(["Vault / Secrets Manager<br/>source of truth"]) -->|"pull every refreshInterval<br/>e.g. 1h"| eso
    eso -->|"writes / refreshes"| ks(["native K8s Secret"])
    ks --> pods(["Pods<br/>mounted / env"])

    class git frozen
    class src base
    class eso mathOp
    class ks io
    class pods req
```

Git holds only the `ExternalSecret` reference (never the value); ESO does the actual pull from Vault or a cloud secrets manager on every `refreshInterval` and writes a native K8s Secret that pods mount or read as env vars.

**Sealed Secrets vs SOPS (encrypt-then-commit-to-Git)**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph SS["Sealed Secrets"]
        direction LR
        s1(["plaintext Secret"]) --> s2("kubeseal<br/>cluster pub key") --> s3(["SealedSecret<br/>ciphertext"]) --> s4(["Git"])
        s4 --> s5("in-cluster controller<br/>decrypts with priv key") --> s6(["Secret in cluster"])
    end

    subgraph SO["SOPS"]
        direction LR
        o1(["secret.yaml"]) --> o2("sops -e<br/>KMS / age key") --> o3(["ciphertext file"]) --> o4(["Git"])
        o4 --> o5("Flux / ArgoCD<br/>decrypts at apply") --> o6(["plaintext at apply"])
    end

    class s1,s6,o1,o6 io
    class s2,o2 mathOp
    class s3,o3 frozen
    class s4,o4 base
    class s5,o5 train
```

Both patterns let ciphertext live safely in Git and differ only in who decrypts it — a cluster-local controller for Sealed Secrets, or Flux/ArgoCD via KMS/age for SOPS — unlike ESO above, which never commits the value at all.

**Rotation (zero-downtime, dual-secret window)**

```mermaid
stateDiagram-v2
    [*] --> KeyV1Only

    KeyV1Only --> Overlap: t0 - create key_v2, both valid
    Overlap --> Overlap: t1 - deploy app to key_v2
    Overlap --> KeyV2Only: t2 - confirm zero traffic, revoke key_v1

    note right of Overlap
        never a moment where the only
        valid key is one nobody has yet
    end note
```

The overlap state is the whole trick: key_v2 is provisioned and traffic migrates to it *before* key_v1 is revoked, so there is never a gap where the only valid key is one no client holds yet.

---

## 6. How It Works — Detailed Mechanics

### Vault dynamic database secrets (mint-on-demand, auto-revoke)

```bash
# 1) Configure the database secrets engine once (admin)
vault secrets enable database
vault write database/config/app-pg \
  plugin_name=postgresql-database-plugin \
  connection_url="postgresql://{{username}}:{{password}}@pg.prod:5432/app" \
  allowed_roles="app-readwrite" \
  username="vault-admin" password="$ADMIN_PW"

# 2) Define a role: what user Vault creates and its lease TTL
vault write database/roles/app-readwrite \
  db_name=app-pg \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT app_rw TO \"{{name}}\";" \
  default_ttl="1h" max_ttl="24h"           # short-lived credential

# 3) App requests a credential at runtime -> unique user, valid 1h, auto-revoked at lease end
vault read database/creds/app-readwrite
#   username  v-token-app-rw-x7f2...   (unique per request)
#   password  A1b2C3...                (Vault generated)
#   lease_duration  3600s
```

### Kubernetes auth — pod authenticates to Vault by service account

```bash
vault auth enable kubernetes
vault write auth/kubernetes/role/app \
  bound_service_account_names=app-sa \
  bound_service_account_namespaces=prod \
  policies=app-policy ttl=1h
# Pod presents its projected SA token; Vault verifies with the cluster API -> issues a Vault token.
```

### External Secrets Operator — reference in Git, value stays in Vault

```yaml
# SecretStore: how the cluster talks to Vault (auth + path)
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata: { name: vault-backend, namespace: prod }
spec:
  provider:
    vault:
      server: "https://vault.prod:8200"
      path: "secret"
      version: "v2"
      auth:
        kubernetes:
          mountPath: "kubernetes"
          role: "app"
---
# ExternalSecret: the only thing committed to Git (a reference, not the value)
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata: { name: app-db, namespace: prod }
spec:
  refreshInterval: 1h                  # re-sync from Vault every hour
  secretStoreRef: { name: vault-backend, kind: SecretStore }
  target: { name: app-db-secret }      # ESO creates/updates this native K8s Secret
  data:
    - secretKey: password
      remoteRef: { key: app/db, property: password }
```

### Sealed Secrets — encrypt so it CAN live in Git

```bash
# Encrypt a normal Secret to the cluster's public key; only the in-cluster controller decrypts.
kubectl create secret generic app-db --from-literal=password=s3cr3t --dry-run=client -o yaml \
  | kubeseal --controller-name sealed-secrets --format yaml > app-db-sealed.yaml
# app-db-sealed.yaml is ciphertext -> safe to commit to Git; controller -> real Secret in cluster.
```

### SOPS — encrypt files for GitOps

```bash
# Encrypt only the values, keep keys readable, decrypt automatically in Flux/ArgoCD via KMS.
sops --encrypt --kms "arn:aws:kms:us-east-1:111122223333:key/abcd" secret.yaml > secret.enc.yaml
# Commit secret.enc.yaml; Flux's SOPS decryption pulls plaintext at apply time.
```

### AWS Secrets Manager with built-in rotation

```bash
aws secretsmanager create-secret --name prod/app/db --secret-string '{"password":"init"}'
# Attach a rotation Lambda (RDS-managed for supported engines) to rotate every 30 days:
aws secretsmanager rotate-secret --secret-id prod/app/db \
  --rotation-lambda-arn arn:aws:lambda:us-east-1:111122223333:function:SecretsManagerRDSRotation \
  --rotation-rules AutomaticallyAfterDays=30
```

### Decoding TTL and rotation windows

Section 3 says "short-lived over long-lived — minimize blast radius," and the configs above
pick `default_ttl="1h"` and `AutomaticallyAfterDays=30`. What those settings actually buy is
an exposure window, and it is worth writing as arithmetic rather than an adjective:

```
max_exposure = validity_period                    (leak just after issue)
avg_exposure = validity_period / 2                (leak at a uniformly random moment)
```

**In plain terms.** "A stolen credential is useful to an attacker until it expires — so the
lifetime you configure *is* the size of the breach." Halving the TTL halves the window; it
does not merely make the secret feel fresher.

| Symbol | What it is |
|--------|------------|
| `validity_period` | Time the credential is accepted: a Vault lease TTL, or the rotation interval |
| `max_exposure` | Worst case — the leak happens immediately after issue or rotation |
| `avg_exposure` | Expected case over many incidents; the useful planning number |
| Vault `default_ttl` / `max_ttl` | 1h / 24h above — initial lease, and the ceiling renewals cannot pass |
| `AutomaticallyAfterDays` | Secrets Manager rotation cadence; a *static* secret's validity period |

**Walk one example.** The two mechanisms in this section, on the same leaked credential:

```
  mechanism                       validity     max exposure    avg exposure
  static + 30-day rotation         30 days       720 hours       360 hours
  Vault dynamic lease              1 hour          1 hour         30 minutes
                                                --------------------------
  ratio                                             720x
```

Same leak, same attacker, 720x difference in how long the credential works — which is the
entire argument for the "Dynamic vs Static" table in Section 4, expressed as a number. Note
that Vault's *default* lease TTL of 768h (32 days) is worse than the Secrets Manager rotation
schedule; the security win comes from the role explicitly setting `default_ttl="1h"`, not
from using Vault.

**Stated plainly.** The one place this arithmetic bites is the ESO `refreshInterval: 1h`
sitting next to a 1h lease. ESO re-syncs on its own clock, so a credential can expire up to a
full interval before the K8s Secret is refreshed, and pods reading the stale value authenticate
against an already-revoked user. Keep `refreshInterval` comfortably below the lease TTL (or let
Vault Agent renew the lease directly) so the refresh always lands ahead of expiry.

**The idea behind it.** Secrets Manager's ~$0.40/secret/month makes secret *count* a real
design input, which is why Section 12 recommends Parameter Store for bulk low-sensitivity
values:

```
  secrets      per month      per year
      50        $20.00         $240.00
     500       $200.00       $2,400.00
   5,000     $2,000.00      $24,000.00
```

A per-tenant or per-environment secret sprawl of 5,000 entries costs $24,000/year in storage
fees alone before a single API call — cheap against one breach, expensive against a config
value that was never a secret to begin with.

---

## 7. Real-World Examples

- **Vault dynamic DB creds in microservices**: each service authenticates to Vault by its Kubernetes service account and gets a unique Postgres user with a 1h lease; a compromised pod's credential auto-expires and is logged. No shared static DB password exists.
- **ESO as the standard K8s pattern**: platform teams install ESO once, point `SecretStore`s at AWS Secrets Manager, and let app teams commit only `ExternalSecret` references — Git stays free of secret values while pods get native K8s Secrets (see [gitops_argocd_flux](../gitops_argocd_flux/)).
- **SOPS + Flux for GitOps secrets**: a team encrypts secret YAML with a KMS key, commits the ciphertext, and Flux decrypts at apply — keeping the GitOps "everything in Git" model without leaking plaintext.
- **AWS Secrets Manager rotation for RDS**: a managed rotation Lambda rotates the master password every 30 days using the dual-secret window, with zero application downtime.
- **The leaked-key postmortem class**: countless incidents (Uber 2016, Codecov 2021-style supply-chain exposures) trace to a long-lived static credential committed or exfiltrated — the case *for* short-lived dynamic secrets.

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Secret type | Static (simple) | Dynamic (Vault) | Operational simplicity vs blast radius |
| Backend | Vault (powerful, self-host) | Cloud manager (managed) | Capability vs operational burden/lock-in |
| K8s delivery | ESO (reference, runtime sync) | Sealed Secrets/SOPS (ciphertext in Git) | External store vs in-Git encryption |
| Distribution | App calls API directly | Sidecar/CSI injects | Coupling vs no app changes |
| Rotation | Automated (managed/dynamic) | Manual | Effort vs staleness risk |
| Lease TTL | Short (minutes) | Long (days) | Security vs reauth churn |
| Storage | Centralized one store | Per-team stores | Consistency/audit vs autonomy |

---

## 9. When to Use / When NOT to Use

**Use a dedicated secrets manager when:** you have any production credentials at all — which is essentially always. Use **Vault** when you want dynamic secrets, PKI, transit encryption, or multi-cloud and can run it; use a **cloud-native manager** (AWS Secrets Manager/GCP/Azure) when you're single-cloud and want managed rotation with minimal ops. In Kubernetes, use **ESO** as the default (references in Git, values in the backend), **Sealed Secrets/SOPS** when you must keep everything in Git for GitOps.

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["Choose a secrets<br/>architecture"]) --> d1{"Need dynamic secrets,<br/>PKI, or multi-cloud?"}
    d1 -->|"yes, can operate it"| vault("HashiCorp Vault")
    d1 -->|"no, single-cloud"| cloudmgr("Cloud-native manager<br/>AWS / GCP / Azure")

    vault --> d2{"Kubernetes: must the<br/>secret live in Git?"}
    cloudmgr --> d2
    d2 -->|"yes (GitOps)"| gitenc("Sealed Secrets / SOPS<br/>ciphertext in Git")
    d2 -->|"no"| eso("External Secrets Operator<br/>reference only in Git")

    class start io
    class d1,d2 mathOp
    class vault,cloudmgr base
    class gitenc frozen
    class eso req
```

The backend choice (Vault vs. a cloud-native manager) and the Kubernetes delivery choice (ESO vs. Sealed Secrets/SOPS) are two independent decisions; chaining them into one path turns the "which tool" question into a lookup instead of a re-read of the prose above.

**Reconsider when:** for a hobby project, a cloud manager or even encrypted local config may suffice over running Vault (which has real operational cost — unsealing, HA, storage backend). Don't put *non-secret* config in a secrets manager — that's [configuration_management](../configuration_management/) / app config. And never use native Kubernetes Secrets *alone* for sensitive data without etcd encryption-at-rest enabled, since they're only base64-encoded by default.

---

## 10. Common Pitfalls

**Pitfall 1 — Hardcoded secrets in code or images.**

```yaml
# BROKEN: secret baked into the manifest/image and committed to Git forever.
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: app
          env:
            - name: DB_PASSWORD
              value: "Sup3rSecret!"      # plaintext in Git history; rotation needs a redeploy
```

```yaml
# FIX: reference an externally-managed secret; the value never enters Git or the image.
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: app-db-secret    # populated by External Secrets Operator from Vault
                  key: password
```

**Pitfall 2 — Treating Kubernetes Secrets as encrypted.** Native `Secret` objects are only base64-encoded in etcd, so anyone with etcd or `kubectl get secret -o yaml` access reads them in plaintext. FIX: enable etcd encryption-at-rest with a KMS provider, restrict RBAC on Secrets, and source values from a real backend via ESO/CSI rather than committing them.

**Pitfall 3 — Secrets in Terraform state.** Passing a DB password through Terraform writes it unencrypted into the state file regardless of `sensitive = true`. FIX: KMS-encrypt the state bucket, lock down access, and source secrets at runtime from Vault/Secrets Manager rather than through Terraform (see [infrastructure_as_code_terraform](../infrastructure_as_code_terraform/)).

**Pitfall 4 — Rotation that causes an outage.** A team rotates a key by replacing it atomically; in-flight clients still holding the old key get 401s. FIX: use a dual-secret window — provision the new credential while the old is still valid, migrate clients, confirm zero traffic on the old one, then revoke it.

**Pitfall 5 — Long lease TTLs that defeat the point of dynamic secrets.** Setting Vault dynamic DB creds to a 30-day TTL recreates the long-lived-secret problem. FIX: set TTLs to the smallest workable value (minutes-to-hours), let the app re-fetch on expiry, and reserve long `max_ttl` only as a ceiling.

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| HashiCorp Vault | Central secrets, dynamic secrets, PKI, transit encryption |
| AWS Secrets Manager | Managed AWS secret store with built-in rotation |
| GCP Secret Manager / Azure Key Vault | Cloud-native managers (GCP/Azure) |
| AWS SSM Parameter Store | Cheap parameter/secret store on AWS |
| External Secrets Operator (ESO) | Sync external secrets into native K8s Secrets |
| Sealed Secrets | Encrypt Secrets to commit safely in Git |
| SOPS (+ age/KMS) | Encrypt YAML/JSON files for GitOps |
| Secrets Store CSI Driver | Mount secrets as volumes from Vault/cloud |
| Vault Agent Injector | Sidecar/init injection of Vault secrets |
| git-secrets / gitleaks / trufflehog | Pre-commit/CI scanning for leaked secrets |

---

## 12. Interview Questions with Answers

**Q1: What's the difference between static and dynamic secrets, and why prefer dynamic?**
A static secret is a long-lived value (a stored DB password) handed out and reused, while a dynamic secret is generated on demand with a short lease and automatically revoked when it expires — Vault, for example, mints a unique database user per request with a 1-hour TTL. Dynamic secrets are preferable because a leaked short-lived, per-workload credential has almost no value and revocation is automatic, whereas a leaked static secret stays valid until someone manually rotates it everywhere. The principle is that the best secret is one that doesn't exist long enough to leak.

**Q2: Why aren't native Kubernetes Secrets actually secure by default?**
Kubernetes `Secret` objects are only base64-encoded in etcd, not encrypted, so anyone with etcd access or sufficient RBAC (`kubectl get secret -o yaml`) can read the plaintext. To make them safe you must enable etcd encryption-at-rest with a KMS provider, tightly scope RBAC, and ideally source the values from an external backend (Vault/cloud) via the External Secrets Operator or CSI driver rather than committing them. Treating base64 as encryption is one of the most common Kubernetes security mistakes.

**Q3: How does HashiCorp Vault's dynamic database secrets feature work end to end?**
You enable the database secrets engine and define a role with `creation_statements` that tell Vault how to create a DB user plus a `default_ttl`; when an app authenticates (e.g., via its Kubernetes service account) and reads `database/creds/<role>`, Vault connects to the DB, creates a unique user with a generated password, and returns it with a lease. When the lease expires Vault runs the revocation statement to drop the user, so there's nothing long-lived. This gives per-workload, short-lived, auto-revoked credentials with a full audit log of every issuance.

**Q4: What is the External Secrets Operator and what problem does it solve?**
ESO is a Kubernetes operator that syncs secrets from an external backend (Vault, AWS/GCP/Azure managers) into native K8s Secrets, driven by a `SecretStore` (how to connect) and `ExternalSecret` (what to fetch) custom resource. It solves the GitOps secrets problem: you commit only the `ExternalSecret` *reference* to Git while the actual value stays in the backend and is refreshed on an interval (e.g., every hour). This keeps secret values out of version control entirely while still letting pods consume ordinary K8s Secrets.

**Q5: Compare Sealed Secrets and SOPS — when would you use each?**
Both let you keep secrets in Git as ciphertext: Sealed Secrets uses `kubeseal` to encrypt a Secret to a cluster-specific key that only the in-cluster controller can decrypt, while SOPS encrypts file values with a KMS or age key and is decrypted by your GitOps tool (Flux/ArgoCD) at apply time. Use Sealed Secrets when you want a Kubernetes-native, controller-managed flow tied to one cluster; use SOPS when you want a portable, tool-agnostic file-encryption approach that also works outside Kubernetes. Both differ from ESO, which keeps values *out* of Git entirely by referencing an external store.

**Q6: How do you rotate a secret without downtime?**
Use a dual-secret (overlap) window: create the new credential while the old one is still valid, deploy/migrate clients to the new one, confirm there's no remaining traffic authenticating with the old one, and only then revoke it. Atomic replacement causes outages because in-flight clients still hold the old value and start failing the moment it's invalidated. Managed rotation (AWS Secrets Manager's RDS rotation) implements exactly this pattern, and dynamic secrets sidestep it by issuing fresh short-lived credentials continuously.

**Q7: Why are secrets in Terraform state a problem, and how do you mitigate it?**
Any sensitive value Terraform manages is written unencrypted into the state JSON, and `sensitive = true` only masks CLI output, not the stored value — so anyone who can read the state bucket can read the password. Mitigate by KMS-encrypting the state backend, locking down bucket access, never committing state to Git, and sourcing secrets at runtime from Vault/Secrets Manager instead of passing them through Terraform (see [infrastructure_as_code_terraform](../infrastructure_as_code_terraform/)). The residual risk is why state access must be treated as secret access.

**Q8: How should a workload authenticate to a secrets manager without a bootstrap secret?**
Use platform identity rather than a stored credential: on AWS, attach an IAM role to the instance/pod (IRSA) so the workload's identity is the auth; in Kubernetes, Vault's Kubernetes auth verifies the pod's projected service-account token against the cluster API and issues a Vault token. This avoids the "secret-zero" chicken-and-egg problem where you'd otherwise need a secret to fetch secrets. The platform's existing identity becomes the trust anchor, scoped by least-privilege policy.

**Q9: Vault vs cloud-native secret managers — how do you choose?**
Vault is the most powerful — dynamic secrets, PKI, transit encryption, multi-cloud — but you operate it (unsealing, HA, storage backend), which is real cost; cloud managers (AWS Secrets Manager, GCP/Azure) are fully managed with native IAM integration and built-in rotation but are single-cloud and lack Vault's dynamic-secret breadth. Choose a cloud manager when you're single-cloud and want minimal ops; choose Vault when you need dynamic secrets, multi-cloud neutrality, or advanced features and can invest in running it. Many orgs use the cloud manager for storage and Vault where dynamic credentials matter.

**Q10: What's the principle of least privilege applied to secrets, and how do you enforce it?**
Each workload should be able to read only the specific secrets it needs and nothing else, so a compromise of one service can't read another's credentials. Enforce it with scoped policies (Vault policies bound to a specific path and auth role, IAM policies limiting `secretsmanager:GetSecretValue` to specific secret ARNs) and per-service identities rather than a shared "app" role. Combined with short TTLs and audit logging, least privilege keeps the blast radius of any single compromise small.

**Q11: How do you prevent secrets from being committed to Git in the first place?**
Run secret-scanning tools (`gitleaks`, `trufflehog`, `git-secrets`) as pre-commit hooks and as a required CI check so a commit containing a key is blocked before merge, and enable provider-side push protection (e.g., GitHub secret scanning). Pair this with the architectural fix — workloads reference an external store, so there's no plaintext value to accidentally commit. If a secret does leak, treat it as compromised: rotate immediately, since deleting the commit doesn't remove it from history or anyone's clone.

**Q12: How do secrets management and GitOps coexist when "everything is in Git"?**
You never commit plaintext, so you use one of three patterns: ESO commits only a *reference* and pulls the value from Vault/cloud at runtime; Sealed Secrets commits ciphertext that only the in-cluster controller can decrypt; or SOPS commits KMS/age-encrypted files that Flux/ArgoCD decrypt at apply. All three preserve the GitOps invariant (Git is the source of truth) while keeping the actual secret value either external or encrypted — see [gitops_argocd_flux](../gitops_argocd_flux/). The choice is external-store-reference (ESO) vs encrypted-in-Git (Sealed Secrets/SOPS).

**Q13: How do AWS Secrets Manager and SSM Parameter Store compare on cost and features, and when would you pick Parameter Store?**
Secrets Manager costs about $0.40 per secret per month and includes built-in rotation, while SSM Parameter Store's standard tier is free but requires you to build rotation yourself. Both integrate with ESO/CSI and IAM for access control, but Secrets Manager adds native rotation Lambdas (e.g., RDS rotation) and versioning designed specifically for credentials, whereas Parameter Store is a general-purpose key-value store often reused for non-secret config too. Pick Parameter Store for a large number of low-sensitivity values where the per-secret fee would add up, and Secrets Manager when you want managed rotation without building it. Many teams use both: Parameter Store for app config, Secrets Manager for anything that needs automatic rotation.

**Q14: How does the Secrets Store CSI Driver differ from the Vault Agent Injector for getting secrets into a pod?**
The CSI Driver mounts secrets from Vault or a cloud manager as a volume with no application code changes, while the Agent Injector adds a sidecar/init container that writes secrets to a file or env var. CSI Driver secrets appear as files in a mounted volume the driver refreshes on a poll interval, requiring only a volume mount in the pod spec, whereas Vault Agent Injector runs alongside the app container, authenticates to Vault itself, and renders templates or raw values before or during the app's lifecycle. Neither, by default, creates a native Kubernetes Secret object the way ESO does, which reduces exposure via `kubectl get secret -o yaml`. Choose the CSI Driver for the simplest no-app-change mounting, and the Agent Injector when you need Vault-specific features like automatic renewal or templated secret files.

**Q15: What is Vault's default lease TTL, and why do dynamic secrets typically use a much shorter one?**
Vault's platform default lease TTL is 768 hours (32 days), but dynamic secrets like database credentials are typically issued with TTLs of minutes to hours. That 768h figure is a generic ceiling meant for tokens and leases broadly, not a recommendation — leaving a dynamic DB credential's `default_ttl` anywhere near it would recreate the long-lived-secret problem dynamic secrets exist to avoid. Production roles instead set `default_ttl=1h` with a `max_ttl` ceiling like 24h, so credentials auto-expire quickly and the app simply re-fetches on expiry. The gap between the platform default and the deliberately short role-level TTL is the tuning knob that makes dynamic secrets valuable.

**Q16: Why couldn't the leaked-key startup simply rotate its shared 12-service DB password to fix the underlying risk?**
Rotating a password shared by 12 services requires coordinating a redeploy of all 12 at once, so in practice nobody attempted it and the password never rotated. That's the structural flaw: a single static credential fans out to every consumer, so any change is an all-or-nothing operation with high coordination cost and no rollback safety. Replacing it with Vault dynamic secrets fixed this structurally rather than operationally — each service gets its own unique, 1-hour-lease Postgres user, so rotation happens continuously and automatically per service with zero coordination required. The lesson generalizes: sharing one static secret across many consumers doesn't just widen blast radius, it makes rotation practically impossible.

---

## 13. Best Practices

- **Never hardcode** secrets in code, images, manifests, or env files; scan with `gitleaks`/`trufflehog` in pre-commit and CI.
- Prefer **dynamic, short-lived credentials** (Vault) over static ones; set the smallest workable lease TTL.
- Authenticate workloads by **platform identity** (IAM role / K8s service account), solving the secret-zero problem.
- Enable **etcd encryption-at-rest** and tight RBAC for any native K8s Secrets; prefer ESO/CSI to keep values external.
- In GitOps, use **ESO (reference) or Sealed Secrets/SOPS (ciphertext)** — never plaintext in Git (see [gitops_argocd_flux](../gitops_argocd_flux/)).
- **Automate rotation** with a dual-secret overlap window for zero downtime; rotate immediately on suspected compromise.
- Apply **least privilege** with scoped policies per service; audit every secret access.
- Keep secrets **out of Terraform state** where possible; KMS-encrypt and lock down state if unavoidable.

---

## 14. Case Study

### Scenario: A startup leaks an AWS key in Git, and a flat shared DB password makes rotation impossible

A startup hardcodes a shared Postgres password and an AWS access key into a Kubernetes Deployment committed to a public-adjacent repo. The AWS key is scraped by a bot within hours and used to spin up crypto-mining instances, generating a $40,000 bill. The shared DB password is used by 12 services, so rotating it means a coordinated 12-service redeploy that nobody wants to attempt — so it never gets rotated.

```yaml
# BROKEN: long-lived shared secrets, in Git, no rotation, no audit.
apiVersion: apps/v1
kind: Deployment
metadata: { name: app, namespace: prod }
spec:
  template:
    spec:
      containers:
        - name: app
          env:
            - name: DB_PASSWORD
              value: "shared-prod-pw"          # used by 12 services; rotation = 12-way redeploy
            - name: AWS_ACCESS_KEY_ID
              value: "AKIA...EXAMPLE"           # scraped from Git -> $40k mining bill
            - name: AWS_SECRET_ACCESS_KEY
              value: "wJalr...EXAMPLE"
```

```yaml
# FIX part 1: no static AWS keys at all -- use IRSA (pod identity); reference DB secret externally.
apiVersion: v1
kind: ServiceAccount
metadata:
  name: app-sa
  namespace: prod
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::111122223333:role/app-role   # IRSA, no keys
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: app, namespace: prod }
spec:
  template:
    spec:
      serviceAccountName: app-sa          # AWS auth via role -> no AWS keys anywhere
      containers:
        - name: app
          env:
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef: { name: app-db-secret, key: password }   # populated by ESO
```

```yaml
# FIX part 2: ESO syncs a UNIQUE, rotated DB secret from Vault dynamic creds (per-service, 1h TTL).
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata: { name: app-db, namespace: prod }
spec:
  refreshInterval: 30m
  secretStoreRef: { name: vault-backend, kind: SecretStore }
  target: { name: app-db-secret }
  dataFrom:
    - sourceRef:
        generatorRef: { apiVersion: generators.external-secrets.io/v1alpha1, kind: VaultDynamicSecret, name: app-db-dyn }
  # Vault role app-db-dyn mints a short-lived per-service Postgres user (default_ttl=1h)
```

The AWS access keys are deleted entirely — pods authenticate to AWS via IRSA (an IAM role bound to the service account), so there's no key to scrape. The shared DB password is replaced by Vault dynamic credentials: each service gets a unique, 1-hour Postgres user, so "rotation" happens continuously and automatically, and a leaked credential auto-expires. Git holds only references, and every secret read is audit-logged in Vault.

**Outcome:** the leaked-key class of incident became impossible (no static AWS keys exist), the 12-way rotation problem evaporated (per-service dynamic creds rotate automatically every hour), and a compromised pod now leaks only a near-worthless 1-hour credential that Vault revokes. Secret scanning (`gitleaks`) was added as a required CI gate so a hardcoded credential can't merge again, and the team set up alerts on anomalous secret-access patterns in the Vault audit log.

```mermaid
xychart-beta
    title "Services exposed by one leaked credential"
    x-axis ["Static shared password (before)", "Vault dynamic credential (after)"]
    y-axis "Services exposed" 0 --> 14
    bar [12, 1]
```

The fix shrank the blast radius twelvefold: the old shared password compromised all 12 services that used it, while a leaked Vault dynamic credential exposes only the one service that requested it, and self-revokes within its 1-hour lease.

**Discussion questions:**
1. Why does replacing static AWS keys with IRSA eliminate the scraped-key attack entirely, rather than just reducing its likelihood?
2. How do Vault dynamic credentials turn the unsolvable "12-service rotation" into a non-event?
3. The leaked AWS key still existed in Git *history* even after the fix — what's the correct remediation, and why isn't deleting the commit enough?

---

**Cross-references:** [infrastructure_as_code_terraform](../infrastructure_as_code_terraform/) (keeping secrets out of state), [gitops_argocd_flux](../gitops_argocd_flux/) (ESO/Sealed Secrets/SOPS in GitOps), [configuration_management](../configuration_management/) (secrets in playbooks vs runtime fetch), [kubernetes_security](../kubernetes_security/) (RBAC, etcd encryption for Secrets), [devsecops_and_supply_chain_security](../devsecops_and_supply_chain_security/) (secret scanning in the pipeline), [cloud_fundamentals_and_aws](../cloud_fundamentals_and_aws/) (IAM roles / IRSA as the auth anchor).
