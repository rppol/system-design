# HashiCorp Vault (and OpenBao) — Secrets Management

> **Version anchor (2026-08-04).** HashiCorp Vault **2.0.3** (2026-06-17) on the **2.0** feature line, which went GA as **2.0.0** on 2026-04-14; the supported lines are **2.0.x**, **1.21.x**, **1.20.x** and **1.19.x**. Licence is **BUSL 1.1**, with the Licensor now recorded as **International Business Machines Corporation (IBM)**, a **Change Date** four years after each release and a **Change License** of MPL 2.0. **OpenBao 2.6.1** (2026-07-22), **MPL 2.0**, under the Linux Foundation. Ecosystem: **Vault Secrets Operator 1.5.0**, **vault-k8s (Agent Injector) 1.7.5**, **vault-csi-provider 1.7.3**, and **External Secrets Operator 2.8.0** serving `external-secrets.io/v1` (v1beta1 stopped being served in `[ESO 0.17]`). Version-specific behaviour is tagged inline as `[2.0]`, `[OpenBao 2.6]`, `[VSO 1.5]`, `[ESO 0.17]`; nothing here is called current without naming the release it landed in.

Vault is a **secrets platform**: a process that holds an encrypted store, authenticates a caller against an identity the caller already has, decides what that identity may reach, and — for the interesting half of its surface — **mints a brand-new credential on the spot** and takes it away again when its lease ends. This page is about the inside of that process: the barrier, the seal, the engines, the leases, the policies, the identities, and the operational failure modes that only show up at 3am.

---

## 1. Concept Overview

### What Vault is

Vault is best understood as **three products that share one process**, and most confusion about it comes from picking the wrong one of the three as your mental model.

1. **An encrypted key-value store.** The boring half. You write `secret/prod/stripe` and read it back. Every cloud has one of these and they are all roughly equivalent.
2. **A credential factory.** The half that justifies the operational cost. Vault holds a *privileged* credential for PostgreSQL, AWS, MongoDB, RabbitMQ, Consul, Snowflake or a private CA, and uses it to **create a fresh, uniquely-named, short-lived credential per request**, then destroys it when the lease expires. Nothing long-lived exists to leak, and revocation is a real operation rather than a coordination exercise.
3. **A cryptographic service.** The `transit` engine encrypts, decrypts, signs, verifies and HMACs on your behalf, so the key never leaves Vault and your application handles only ciphertext. This is what lets an application that must not hold a key still use one.

Everything else — auth methods, policies, identity, leases, audit, replication — is the machinery that makes those three safe to operate for more than one team.

### The thesis of this page: Vault is an identity broker with a crypto barrier, not a password database

The single sentence that most changes how you reason about Vault:

> **Vault's job is to turn an identity you already have into a credential you do not yet have, for a bounded time, with a record of it.**

A pod already has a Kubernetes service-account token. An EC2 instance already has an instance identity document. A CI job already has an OIDC token from its provider. None of those is a database password — and Vault's entire value is the exchange. Read from that angle, the rest of the product falls out:

- **Auth methods** exist because there are many kinds of identity you already have (§6.11–§6.14).
- **Policies and identity** exist because the exchange must be scoped, and the same human or workload may arrive through several doors (§6.15, §6.16).
- **Leases** exist because "for a bounded time" has to be enforced by something, and that something must survive Vault restarting (§6.9).
- **Audit devices** exist because "with a record of it" is the third leg, and Vault will refuse to serve rather than serve unrecorded (§6.18).
- **The barrier and the seal** exist because a broker that holds every credential in the company is the highest-value target on the network, and it must be useless when it is not running (§6.1, §6.2).

If your Vault deployment is only a KV store, you have paid the full operational price for the least interesting third of the product.

### Disambiguation — five things called "Vault"

| Name | What it is | Relationship to this page |
|---|---|---|
| **HashiCorp Vault** | The product on this page — `hashicorp/vault`, Go module `github.com/hashicorp/vault`, docs at `developer.hashicorp.com/vault` | This page |
| **IBM Vault** | The same product, rebranded after IBM closed the HashiCorp acquisition on **2026-02-27**; officially "IBM Vault (formerly HashiCorp Vault)" | Same binary, same repo, same import path — see below |
| **Azure Key Vault** | Microsoft's managed key, secret and certificate store | A *competitor*, and a Vault auth/seal backend |
| **Ansible Vault** | A file-encryption feature of Ansible (`ansible-vault encrypt`), symmetric, password-based | Unrelated. Encrypts files in a repo; does not broker identity |
| **`spring-cloud-vault-config`** | A Spring Boot starter that loads properties from HashiCorp Vault at bootstrap | A *client of* this page's product |

The slug for this module is **`hashicorp_vault`**, not `ibm_vault`, and that is a deliberate decision rather than an oversight — see the next section.

### Vendor, licence and governance

Three separate facts get tangled together here, and an adoption review needs all three stated apart.

**Ownership.** IBM closed its acquisition of HashiCorp on **2026-02-27** for **$6.4 billion**. The product's official brand is now "IBM Vault (formerly HashiCorp Vault)", and the BUSL Licensor line in the repository names **International Business Machines Corporation (IBM)**. What did *not* change: the GitHub repository is still `hashicorp/vault`, the Go module path is still `github.com/hashicorp/vault`, the documentation domain is still HashiCorp's, the Helm chart is still `hashicorp/vault`, and the Terraform provider is still `hashicorp/vault`. **The corporate owner changed without the engineering name changing** — which is precisely why this module is `hashicorp_vault`: every string an engineer types still says hashicorp.

**Licence.** Vault moved from MPL 2.0 to the **Business Source License 1.1** in August 2023, and remains there. BUSL is *source-available*, not open source: the source is public, you may read it, modify it and self-host it, and you may **not** provide a competitive hosted offering of it. Each release carries a **Change Date** four years out, at which point that release becomes **MPL 2.0**. Practical readings:

- Running Vault as internal infrastructure for your own company is unambiguously permitted, and is what essentially every user does.
- Selling "managed Vault" to third parties is what the licence exists to prevent.
- Organisations whose policy requires an **OSI-approved** licence cannot use Vault at all, regardless of how they use it. That policy — not a capability gap — is the usual reason a team lands on OpenBao.

**Governance.** Vault's roadmap is a single vendor's, now a single very large vendor's. **OpenBao** is the Linux Foundation fork taken at the BUSL change, under **MPL 2.0**, with a public technical steering committee. §4.8 and §8.4 cover how far the two have actually diverged, which is further than "drop-in replacement" suggests and much less far than "different product" suggests.

### A short history

| Year | Event |
|---|---|
| 2015 | Vault 0.1 released by HashiCorp; the barrier, Shamir unseal, and the KV/transit/PKI engine model are present from very early |
| 2016–2018 | Dynamic database credentials, AppRole, replication (Enterprise), auto-unseal by cloud KMS |
| 2019–2020 | Kubernetes auth and the Agent Injector make Vault the default answer for pod secrets; **Integrated Storage (Raft)** lands in 1.4 and starts displacing Consul as the storage backend |
| 2021–2022 | Identity secrets and OIDC provider, KV v2 as the default, quotas, Vault Agent templating maturity |
| **Aug 2023** | **Relicensed MPL 2.0 → BUSL 1.1.** **OpenBao** forked under the Linux Foundation within months |
| 2023–2025 | **Vault Secrets Operator** (a native Kubernetes controller, no sidecar); Seal HA (Enterprise); OpenBao ships its own releases and begins to diverge |
| **Feb 2026** | **IBM closes the HashiCorp acquisition**; brand becomes "IBM Vault (formerly HashiCorp Vault)" |
| **Apr 2026** | **Vault 2.0.0 GA** — the first major-version bump in a decade, carrying the security-hardening breaking changes in §6.23 |
| 2026 | Vault **2.0.3**; OpenBao **2.6.1**, now removing and deprecating things Vault still ships |

### What Vault is not

- **Not a config store.** Feature flags, log levels and connection *hostnames* are not secrets. Putting them in Vault buys you an availability dependency and an audit-log flood for nothing. Use a config system; see [`devops/configuration_management`](../../devops/configuration_management/configuration_management.md).
- **Not a Kubernetes-only tool.** Roughly half of real Vault deployments exist because the fleet is *not* uniformly Kubernetes — VMs, bare metal, CI runners, Nomad, and three clouds. That heterogeneity is the strongest argument for Vault over any single cloud's manager.
- **Not a certificate authority you can ignore.** The PKI engine is a real CA with real key-ceremony and CRL obligations (§6.7). Running it is a commitment, not a checkbox.
- **Not free of a critical path.** Once workloads fetch credentials at start-up, Vault being down means new pods cannot start. §6.23 and §9 are largely about accepting that honestly rather than pretending otherwise.
- **Not the concept module for secrets management.** See directly below.

### The boundary with `devops/secrets_management`

This page and [`devops/secrets_management`](../../devops/secrets_management/secrets_management.md) deliberately do not overlap, and the split is worth stating because it determines where to look for an answer.

| That module owns (the *discipline*) | This module owns (the *product*) |
|---|---|
| The four questions any secrets system answers; static vs dynamic as a concept | The barrier, the seal, the key hierarchy, every secrets engine's actual mechanics |
| The Kubernetes delivery patterns **as patterns**, including `kubeseal` and `sops` how-to | Vault's own four delivery mechanisms and how to choose between them |
| The **exposure-window arithmetic** (`max_exposure = validity`, the 720x table, the per-secret cost table) | The **lease-count arithmetic** — why halving a TTL does not reduce lease count and doubles issuance (§6.9) |
| Secret scanning, secrets in Terraform state, "K8s Secrets are only base64" | Leases, tokens, policies, identity, response wrapping, audit devices, replication, quotas |
| The AWS Secrets Manager rotation Lambda and the leaked-key case study | The OpenBao delta, and Vault's own operational failure modes |
| IRSA / EKS Pod Identity as the general answer to secret-zero | Vault's *specific* answers to secret-zero: Kubernetes auth, AppRole with wrapped `secret_id`, cloud IAM auth (§6.11) |

If you want to know *whether* to run a secrets manager and *which pattern* to deliver it with, read that module. If you have chosen Vault and need to operate it, read this one.

---

## 2. Intuition

> **One-line analogy:** Vault is not a safe. It is a **bonded key-cutting shop with a shredder on a timer** — it holds the master, cuts you a key stamped with your name, and destroys that key at closing time whether you brought it back or not.

**Mental model.** Picture three concentric things. The outermost is a **door** that is locked whenever the process is not running and must be deliberately opened (the seal). Inside it is a **barrier**: every single byte Vault writes to disk — secrets, policies, leases, even its own audit-device configuration — passes through an encryption layer, so the storage backend is a dumb, untrusted blob store. Inside *that* is a **broker** that spends its whole life doing one transaction: you hand it proof of an identity it recognises, it hands you a credential with an expiry attached, and it writes down that it did.

**Why it matters.** Every alternative to this shape is a variation of "the credential exists before you need it and after you stop needing it". A `.env` file, a Kubernetes Secret, a CI variable, a password manager entry — all of them are storage problems with a distribution problem attached, and all of them make revocation a coordination exercise across every consumer. Vault converts a *storage and distribution* problem into an *authentication and expiry* problem, which is a problem computers are good at.

**Key insight — the sentence the rest of this page unpacks.** *A lease is the credential's real identity; the username and password are just its current costume.*

Everything that surprises newcomers follows from it:

- A lease is **server-side state**, so it survives a restart, it can be revoked without touching the consumer, and — crucially — **it costs storage and CPU** (§6.9). Leases are the thing that scales badly, not secrets.
- Because the lease is the identity, `vault lease revoke -prefix database/creds/app` really does invalidate every credential that engine ever issued, in one command, for every consumer, whether or not they are reachable.
- Because the lease has an expiry, an application's obligation is to **renew or re-fetch**, and an application that does neither will work perfectly in staging and fail exactly once per TTL in production.
- Because expiry is enforced by a revocation *action* (a `DROP ROLE`, a `DeleteAccessKey`), the cost of a short TTL is paid at the database, not at Vault — which is why §6.9's arithmetic bites.

---

## 3. Core Principles

- **Encrypt everything before it leaves the process.** The storage backend is never trusted with plaintext. This is why a leaked Raft snapshot or a compromised S3 bucket is an inconvenience and not a breach — and why losing the seal mechanism is unrecoverable (§6.1, §6.2).
- **Sealed by default.** A freshly started Vault serves nothing. Unsealing is a deliberate act requiring either a quorum of humans or an external trust anchor. The consequence is that Vault must be *made* to start unattended; it does not by default (§6.2).
- **Identity in, credential out.** Vault authenticates what you already are, never what you were given to prove you are something else. Anything else re-creates the secret-zero problem it exists to solve (§6.11).
- **Everything dynamic has a lease; everything with a lease is revocable.** Expiry is a first-class server-side object with an id, a TTL, a renewal path and a revocation path (§6.9).
- **Deny beats allow, and the most specific path wins.** Policy evaluation is not additive in the way people assume: a single `deny` anywhere overrides every grant, and path specificity — not policy order — decides which rule applies (§6.15).
- **The API path is the security boundary, not the CLI path.** The `vault kv` command hides a path segment, and a policy written against what you typed grants nothing. This is the single most common Vault mistake (§6.4).
- **Auditability is a hard requirement, not best-effort.** Vault guarantees a request is logged to at least one enabled audit device before it is served, and **refuses the request** if it cannot manage that (§6.18).
- **Short-lived beats rotated.** A credential that expires in an hour needs no rotation process. But short is not free — see the lease arithmetic before you set five minutes (§6.9).
- **Vault is on the critical path, so design for its absence.** Cache, renew ahead of expiry, and know precisely what breaks during a Vault outage. Usually: nothing running, everything starting (§6.23, §9).
- **Root tokens are for bootstrap and emergencies only.** Generate one with a quorum when you need it, and revoke it when you are done (§6.10).

---

## 4. Types / Architectures / Strategies

### 4.1 The component model — the seven nouns

Vault's documentation names dozens of things. Seven of them are the object model, and every configuration decision is about one of the seven.

| Noun | What it is | Where it lives | Failure if you get it wrong |
|---|---|---|---|
| **Barrier** | The AES-256-GCM encryption layer every write passes through | In-process; keys only in memory | None — it is not configurable, which is the point |
| **Seal** | The mechanism that unlocks the barrier at start-up: Shamir shares, a cloud KMS, or an HSM | `seal` stanza in config | Lose it and the data is unrecoverable, backups included (§6.2) |
| **Storage backend** | Where the encrypted blobs go: Integrated Storage (Raft), Consul, or a legacy external store | `storage` stanza | Wrong choice locks in your HA story and your backup story (§6.3) |
| **Secrets engine** | A mounted plugin at a path that stores or *generates* secrets: `kv`, `database`, `pki`, `transit`, `aws`, `ssh`, … | `vault secrets enable -path=…` | A KV mount where a dynamic engine belonged is the "we bought Vault for nothing" outcome |
| **Auth method** | A mounted plugin at a path that turns an external identity into a Vault token: `kubernetes`, `jwt`, `approle`, `aws`, `cert`, … | `vault auth enable …` | The whole secret-zero problem lives here (§6.11) |
| **Policy** | Named HCL granting capabilities on API paths | `sys/policy` | Over-broad policies are the usual audit finding; the KV v2 path trap is the usual bug (§6.4, §6.15) |
| **Lease / token** | The two expiring objects: a token is your session, a lease is a credential Vault issued | Storage, with an expiration manager in memory | Lease count is the number that takes Vault down (§6.9) |

Two things people expect to find in that list and will not: there is no "user database" (identity comes from auth methods, and §6.16's identity layer only *unifies* what they produce), and there is no "secret" object type (a secret is whatever an engine returns at a path).

### 4.2 Secrets-engine taxonomy — the split that decides whether Vault was worth it

Engines divide cleanly into three kinds, and the interesting boundary is between the first and the other two.

| Kind | Engines | What a read returns | Lease? | Revocation means |
|---|---|---|---|---|
| **Static store** | `kv` v1, `kv` v2, `cubbyhole` | The value you previously wrote | No (KV) | Nothing — you must rotate at the source |
| **Dynamic generator** | `database`, `aws`, `azure`, `gcp`, `consul`, `nomad`, `rabbitmq`, `ssh` (OTP/CA), `terraform`, `kubernetes` | A credential that **did not exist a moment ago** | Yes | A real action at the target: `DROP ROLE`, `DeleteAccessKey`, delete service principal |
| **Cryptographic service** | `transit`, `pki`, `kmip`, `keymgmt`, `totp` | A ciphertext, signature, certificate or code — the key never leaves | Sometimes (PKI leaves, opt-in) | Key rotation or CRL, not deletion |

A fourth category worth naming separately because it behaves unlike the rest: **static roles**. `database/static-roles/<name>` and `aws/static-roles/<name>` do not mint a new principal per request — they take ownership of **one existing account** and rotate its password on a schedule you set. Every reader gets the *same* username and the *current* password. Use them where a real human account or a legacy service account must keep its identity (§6.6).

### 4.3 Auth-method taxonomy — and where each solves secret-zero

The right question about an auth method is never "how does it work" but **"what did the client have to already possess, and could an attacker possess it too?"**

| Method | The pre-existing identity | Secret-zero grade | Where it fits |
|---|---|---|---|
| `kubernetes` | The pod's projected service-account JWT | **Excellent** — kubelet-issued, audience-bound, short-lived, not stored anywhere | Any pod, in-cluster or out |
| `jwt` / `oidc` | An OIDC token from GitHub Actions, GitLab, an IdP | **Excellent** — no long-lived material, claims are verifiable | CI pipelines, human login |
| `aws` (IAM) | A signed `sts:GetCallerIdentity` request from the instance/task role | **Excellent** | EC2, ECS, Lambda, EKS nodes |
| `azure`, `gcp`, `alicloud`, `oci` | Cloud instance identity documents / managed identity | **Excellent** | The same idea per cloud |
| `cert` | A client TLS certificate | **Good** — as good as your certificate distribution | mTLS fleets, appliances |
| `approle` | `role_id` + `secret_id` | **Conditional** — good if `secret_id` is response-wrapped, single-use and pushed by a trusted orchestrator; a plain long-lived secret otherwise (§6.14) | The universal fallback when nothing above applies |
| `userpass`, `ldap`, `okta`, `radius` | A password | **Human only** | Operators; never a workload |
| `github` | A GitHub personal access token | **Poor** — a long-lived bearer token | Legacy; do not adopt |
| `token` | A Vault token you already hold | n/a — it is the output, not an input | Bootstrap and testing |

The taxonomy has a shape: **every method above `cert` derives from a platform that already attests identity for free, and every method below it requires you to distribute something.** Pick from the top of the table.

### 4.4 Seal taxonomy — three, and only one of them lets Vault start unattended

| Seal type | Root key protected by | Unseal is | Recovery keys | When |
|---|---|---|---|---|
| **Shamir** (default) | An unseal key split into *n* shares, threshold *t* | Humans running `vault operator unseal` *t* times | n/a — the shares **are** the unseal key | Air-gapped, highest-assurance, or where a KMS is not permitted |
| **Auto-unseal via cloud KMS** (`awskms`, `azurekeyvault`, `gcpckms`, `transit`) | A KMS key that wraps the root key directly | Automatic at start-up | Yes — a *different* thing (§6.2) | Essentially every cloud deployment |
| **Auto-unseal via HSM** (`pkcs11`) | An HSM key, via PKCS#11 | Automatic at start-up | Yes | FIPS/regulatory requirements; Enterprise |

A fourth entry belongs here for completeness: **Seal HA** (Enterprise) permits several seals at once so the loss of one KMS does not seal the cluster. And a fifth: **`transit` seal**, where a *different* Vault cluster unseals this one — elegant, and a dependency loop waiting to happen if the two clusters ever unseal each other.

The one non-obvious property, and the source of §6.2's worst outcome: **a recovery key is not an unseal key.** Under auto-unseal the KMS is the only thing that can decrypt the root key. Recovery keys authorise *privileged operations* (generating a root token, rekeying recovery shares); they cannot open the barrier. Delete the KMS key and every backup you own is ciphertext forever.

### 4.5 Storage-backend taxonomy

| Backend | HA | Where the data is | Status | Verdict |
|---|---|---|---|---|
| **Integrated Storage (Raft)** | Yes, native | On each Vault node's own disk, replicated by Raft | The default and the recommendation since 1.4 | **Use this** |
| **Consul** | Yes | A separate Consul cluster | Supported, historical | Only if you already run Consul for other reasons and have operated it for years |
| `postgresql`, `mysql`, `dynamodb`, `etcd`, `zookeeper`, … | Some | An external database | Community-supported; several deprecated | Do not start here |
| `s3`, `gcs`, `azure`, `swift` | **No** | Object storage | Community-supported | Single-node only. Not a production HA story |
| `file` | No | A local directory | Dev/test; **deprecated in `[OpenBao 2.6]`** | Never in production |
| `inmem` | No | RAM | `-dev` mode | Never |

The consolidation is deliberate and it is the right call: Integrated Storage collapses "operate Vault" and "operate Vault's database" into one problem, makes `vault operator raft snapshot save` a single complete backup, and removes an entire distributed system from the dependency graph.

### 4.6 Token taxonomy — six kinds, and the one that fixes your lease problem

| Kind | Persisted? | Renewable | Creates children | Has a lease | Prefix |
|---|---|---|---|---|---|
| **Service** (default) | Yes | Yes | Yes | Yes | `hvs.` |
| **Batch** | **No** — an encrypted, self-describing blob | **No** | **No** | **No** | `hvb.` |
| **Periodic** | Yes | Indefinitely, in `period` increments | Yes | Yes | `hvs.` |
| **Orphan** | Yes | Yes | Yes | Yes | `hvs.` |
| **Root** | Yes | n/a (no expiry unless given one) | Yes | No by default | `hvs.` |
| **Recovery** | Special-case, for a sealed/degraded cluster | No | No | No | `hvr.` |

The two that matter operationally:

- **Batch tokens** are the release valve for token-count pressure. They are not stored, so issuing a million costs Vault nothing at rest — the token itself carries its policies, encrypted with the barrier key. The price: no renewal, no children, no cubbyhole, and revocation is impossible before expiry. Ideal for a short-lived CI job or a serverless invocation; wrong for a long-running server that must be revocable.
- **Periodic tokens** solve the "this service must run for a year without re-authenticating" problem without granting an infinite TTL: the token can be renewed forever, but only ever `period` at a time, so an abandoned token dies one period after the last renewal.

### 4.7 Deployment topologies

| Topology | Shape | Reads | Writes | Licence |
|---|---|---|---|---|
| **Single node** | One Vault, one storage | Local | Local | Community |
| **HA cluster** | 3 or 5 Raft nodes, one **active**, rest **standby** | Standbys **forward or redirect** to active | Active only | Community |
| **Performance standby** | Same, but standbys serve read-only requests locally | Local on any node | Forwarded to active | **Enterprise** |
| **Performance replication** | A second cluster with its own storage, serving its own clients | Local | Forwarded to the primary, except **local mounts** | **Enterprise** |
| **DR replication** | A warm standby cluster that serves **no client traffic** | None | None | **Enterprise** |

Two facts that catch people out. First, in a community HA cluster a standby node is *not* a read replica — it is a redirector, so adding nodes buys availability and durability, never throughput. Second, **DR secondaries are deliberately useless until promoted**: they accept no client requests at all, and promotion requires a **DR operation token** generated from a quorum of unseal/recovery key holders. That ceremony is the thing to rehearse, because it is the only part of a disaster that involves humans finding each other (§6.23).

Quorum sizing is ordinary Raft: 3 nodes tolerate 1 failure, 5 tolerate 2, and even numbers buy nothing. Five is the ceiling worth paying for; beyond that, write latency rises and fault tolerance does not.

### 4.8 The OpenBao delta — what a fork three years in actually looks like

OpenBao was taken from Vault at the BUSL change and is **API-compatible at the fork point**: the same HTTP paths, the same policy HCL, the same secrets engines and auth methods, the same CLI verbs. An existing Vault client library, Terraform provider, Agent or Kubernetes integration generally points at OpenBao unchanged. That is the honest headline and it is why this page covers both rather than splitting into two.

What has actually diverged, and it is more than "a rename":

| Area | Vault `[2.0]` | OpenBao `[OpenBao 2.6]` |
|---|---|---|
| Licence | BUSL 1.1, Licensor IBM, Change Date +4y to MPL 2.0 | **MPL 2.0**, Linux Foundation, public TSC |
| Enterprise features | Namespaces, replication, HSM seal, Seal HA, lease-count quotas, performance standbys | None of the above — but namespaces and some previously-Enterprise ideas are being built in the open |
| `stored_shares` | Still accepted on `sys/init` and `sys/rekey/init` | **Removed** — a client passing it gets an error |
| Container user | Runs as root by default in the official image | Runs as the unprivileged **`openbao`** user — a genuine upgrade break for anyone with a host mount owned by root |
| Built-in KMS seals | `awskms`, `azurekeyvault`, `gcpckms`, `pkcs11` are core | All four **deprecated, for removal in 2.7.0**, moving to an external plugin model |
| `file` storage | Supported for dev | **Deprecated** |
| Release cadence | HashiCorp/IBM's | The foundation's, independent |

The practical reading: **the compatibility risk is not the API, it is the operational surface.** A migration that only exercises `vault kv get` will succeed and prove nothing; the things that break are init flags, seal configuration, container file ownership, and any Enterprise feature you had quietly started to depend on. §8.4 turns this into a decision.

### 4.9 Kubernetes delivery — Vault's own four mechanisms

[`devops/secrets_management`](../../devops/secrets_management/secrets_management.md) owns the comparison of delivery *patterns* in general. What follows is narrower: the four ways **Vault specifically** gets a secret into a pod, and the property that separates them.

| Mechanism | Component | Where the secret lands | Native K8s Secret created? | Renews a lease? |
|---|---|---|---|---|
| **Agent Injector** | `vault-k8s` **1.7.5**, a mutating webhook adding an init container and sidecar | A file under `/vault/secrets` in a shared memory volume | No | **Yes** — the sidecar is a full lease renewer |
| **CSI provider** | `vault-csi-provider` **1.7.3** under the Secrets Store CSI Driver | A file in a mounted volume | Optional (`secretObjects`) | No — it fetches, it does not renew |
| **Vault Secrets Operator** | VSO **1.5.0**, a controller with CRDs | A **native Kubernetes Secret** the operator writes and refreshes | **Yes, by design** | Yes — the controller re-reads and can restart consumers |
| **Direct API** | Your application plus a Vault client library | Your process's memory | No | Yes, if you wrote it |

The separating property is **who owns renewal and who notices a change**: the sidecar owns renewal and the app must re-read the file; the CSI driver owns neither; VSO owns renewal *and* can `rolloutRestartTargets` the Deployment so the app is restarted into the new value; direct API gives you everything and costs you a client library in every language you ship. §6.19, §6.20 and §8.5 develop this.

---

## 5. Architecture Diagrams

### 5.1 The key hierarchy — four nested layers (ASCII: the containment is the point)

Mermaid can draw boxes inside boxes but cannot make *strict physical containment* read as "the outer key is the only thing that decrypts the inner one". The nesting here is the meaning.

```
  +-------------------------------------------------------------------+
  |  UNSEAL KEY            never written to disk, never in a backup    |
  |  Shamir : split into n shares, any t reconstruct it                |
  |  Auto   : replaced by a KMS/HSM that wraps the layer below         |
  |                                                                    |
  |    +-----------------------------------------------------------+   |
  |    |  ROOT KEY  (historically "master key")                    |   |
  |    |  AES-256-GCM. Stored ENCRYPTED in the storage backend.    |   |
  |    |  Changed by:  operator rekey (Shamir shares)              |   |
  |    |                                                            |   |
  |    |    +--------------------------------------------------+   |   |
  |    |    |  KEYRING : encryption keys, terms 1..N            |   |   |
  |    |    |  Newest term encrypts new writes.                 |   |   |
  |    |    |  All terms retained, so old data stays readable.  |   |   |
  |    |    |  Changed by:  operator rotate (adds a term)       |   |   |
  |    |    |                                                    |   |   |
  |    |    |    +-----------------------------------------+    |   |   |
  |    |    |    |  EVERY BYTE IN THE STORAGE BACKEND      |    |   |   |
  |    |    |    |  secrets, policies, leases, tokens,     |    |   |   |
  |    |    |    |  identity, audit-device config          |    |   |   |
  |    |    |    +-----------------------------------------+    |   |   |
  |    |    +--------------------------------------------------+   |   |
  |    +-----------------------------------------------------------+   |
  +-------------------------------------------------------------------+

  Read it downward as "decrypts", upward as "is protected by".
  Nothing at any layer is plaintext on disk. A stolen Raft snapshot,
  a stolen S3 bucket and a stolen disk are all the same object: noise.
```

Two operations sit at two different layers, and confusing them is §6.23's most common question: **rekey** changes the *outermost* layer (who can open the door), **rotate** adds a term to the *keyring* (what encrypts tomorrow's writes). Neither re-encrypts existing data, and neither is the other's substitute.

### 5.2 The request path — from a service-account token to a database password

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    pod(["Pod<br/>projected SA token"]) --> auth("Auth method<br/>kubernetes")
    auth --> ident("Identity layer<br/>entity + alias + groups")
    ident --> tok(["Vault token<br/>policies attached"])
    tok --> acl{"Policy check<br/>path + capability"}
    acl -->|"deny or no match"| reject(["403 permission denied"])
    acl -->|"allow"| eng("Secrets engine<br/>database/creds/app")
    eng --> db[("PostgreSQL<br/>CREATE ROLE")]
    eng --> lease(["Lease<br/>id + TTL + revoke path"])
    eng --> audit(["Audit devices<br/>ALL enabled, at least one must succeed"])
    lease --> resp(["Response<br/>username, password, lease_id"])

    class pod,resp io
    class auth,eng train
    class ident,tok req
    class acl mathOp
    class reject lossN
    class db frozen
    class lease,audit base
```

Every arrow is a place something can go wrong, and the failure modes are distinct: the auth step fails on a bad audience or an expired reviewer JWT (§6.12), the policy step fails on the KV v2 path trap (§6.4), the engine step fails when the target database has no connection slots left (§6.5), and the audit step fails the **whole request** if no device can be written to (§6.18).

### 5.3 The seal lifecycle — what a restart actually does

```mermaid
stateDiagram-v2
    [*] --> Sealed: process starts
    Sealed --> Unsealing: operator unseal, share 1 of 3
    Unsealing --> Unsealing: share 2 of 3
    Unsealing --> Unsealed: share 3 of 3, threshold met
    Sealed --> Unsealed: auto-unseal, KMS decrypts root key
    Unsealed --> Active: wins the HA lock
    Unsealed --> Standby: another node holds the lock
    Standby --> Active: active node lost, lock acquired
    Active --> Sealed: operator seal, or KMS unreachable at restart
    Standby --> Sealed: operator seal
    Unsealed --> Sealed: seal-on-panic

    note right of Sealed
        A sealed Vault answers only
        sys/health, sys/seal-status
        and the unseal endpoints.
        Everything else is 503.
    end note
```

The transition worth memorising is the one that is **not** drawn as a path from `Sealed` back to data: nothing recovers a cluster whose seal mechanism is gone. Backups do not help, because backups are ciphertext under a key the seal held.

### 5.4 One lease on a time axis — why renewal can shorten it (ASCII: the axis carries the meaning)

```
  t0            t0+10m                                              t0+4h
  |               |                                                   |
  v               v                                                   v
  [=============================== lease TTL 4h =====================]
  issued
                  |
                  +-- vault lease renew -increment=1h <lease_id>
                      |
                      v
                  [=== new TTL 1h ===]
                  t0+10m          t0+1h10m          <-- new expiry
                                                        EARLIER than t0+4h

  The increment is measured from NOW, not from the old expiry.
  A "renewal" is really "set the remaining time to this value",
  clamped by max_ttl. Renewing a long lease with a small increment
  SHORTENS it, silently, and the client learns at the next request.
```

The correct client behaviour is to renew with **no increment** (Vault re-applies the role's TTL) or with an increment at least as large as the role's default, and to renew at roughly two-thirds of the TTL rather than at the last moment.

### 5.5 TTL derivation — the precedence box (ASCII: the alignment is the table)

```
  effective_ttl = min(
        requested_ttl        (what the client asked for, optional)
        role_ttl             (database/roles/<r> default_ttl)      <- most specific
        mount_default_ttl    (secrets tune -default-lease-ttl)
        sys_default_ttl      (768h)                                <- least specific
  )
  and separately hard-capped by:
  ceiling       = min(
        role_max_ttl         (database/roles/<r> max_ttl)
        mount_max_ttl        (secrets tune -max-lease-ttl)
        sys_max_ttl          (768h)
  )

  Two rules people get backwards:
    1. Specific WINS, it does not add. A role default_ttl of 1h is 1h
       even though the system default is 768h.
    2. A renewal can never push total lifetime past the ceiling. At the
       ceiling the lease simply expires -- there is no error, and a client
       that only ever renews and never re-fetches dies exactly then.
```

### 5.6 HA, performance replication and DR on one picture

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph P["Primary cluster, us-east"]
        direction LR
        a(["Active node<br/>holds the HA lock"])
        s1(["Standby<br/>forwards writes"])
        s2(["Standby<br/>forwards writes"])
        a --- s1
        a --- s2
    end

    subgraph PR["Performance secondary, eu-west"]
        direction LR
        pa(["Active<br/>serves local reads"])
        ps(["Standby"])
        pa --- ps
    end

    subgraph DR["DR secondary, us-west"]
        direction LR
        da(["Active<br/>serves NO clients"])
    end

    cl(["Clients, us-east"]) --> a
    cle(["Clients, eu-west"]) --> pa
    a -->|"replicates<br/>all shared mounts"| pa
    a -->|"replicates<br/>everything, incl. tokens"| da
    pa -->|"forwards writes"| a
    da -.->|"promote<br/>needs DR operation token"| cl

    class cl,cle io
    class a,pa,da train
    class s1,s2,ps req
    class P,PR,DR base
```

The two replication modes answer different questions and are routinely confused. **Performance replication** is about latency and load: the secondary has its own clients, its own tokens, and its own local mounts, and forwards only writes. **DR replication** is about survival: the secondary is a byte-for-byte warm copy including tokens and leases, serves nobody, and exists to be promoted. Both are Enterprise.

### 5.7 The four Kubernetes delivery mechanisms, side by side

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    v[("Vault")]

    v --> inj("Agent Injector<br/>sidecar per pod")
    v --> csi("CSI provider<br/>node driver")
    v --> vso("Vault Secrets Operator<br/>cluster controller")
    v --> sdk("Vault client library<br/>in-process")

    inj --> f1(["file in<br/>/vault/secrets"])
    csi --> f2(["file in<br/>mounted volume"])
    vso --> f3(["native<br/>Kubernetes Secret"])
    sdk --> f4(["process memory<br/>never on disk"])

    f1 --> r1{"app must<br/>re-read file"}
    f2 --> r2{"no renewal<br/>at all"}
    f3 --> r3{"rolloutRestartTargets<br/>restarts the Deployment"}
    f4 --> r4{"you own<br/>renewal logic"}

    class v frozen
    class inj,csi,vso,sdk train
    class f1,f2,f3 io
    class f4 req
    class r1,r2,r4 mathOp
    class r3 base
```

Read the bottom row as the actual decision. Nothing here is about how the secret arrives — all four work — and everything is about **what happens on the second delivery**, when the value has changed and a process is still holding the first one.

---
## 6. How It Works — Detailed Mechanics

### 6.1 The barrier — why the storage backend is untrusted by design

Vault's storage layer is a key-value interface with four operations (`Get`, `Put`, `Delete`, `List`), and **every value that crosses it is already ciphertext**. The barrier sits between Vault's core and that interface, encrypting with **AES-256-GCM** using a key from the keyring, and prefixing each blob with the keyring **term** that encrypted it so any historical term can still decrypt.

The consequences are worth stating explicitly because they drive a lot of design decisions downstream:

- **The storage backend needs no security properties beyond durability.** An S3 bucket, a Consul cluster or a Raft directory can all be read by an attacker with no benefit. This is why the Vault threat model tolerates operators who have disk access.
- **Backups are ciphertext, and that cuts both ways.** `vault operator raft snapshot save` produces a file you can store anywhere — and a file that is worthless without the seal (§6.2).
- **Vault's own configuration is inside the barrier.** Policies, audit-device configuration, mount tables and the identity store are all encrypted data, not files on disk. There is no "edit the config to get in" path, deliberately.
- **The barrier is not configurable.** There is no cipher-suite knob, no "use my key" option. The only choices are at the seal layer above it.

```bash
# What a raw storage value looks like. This is a real Vault secret in Consul:
$ consul kv get vault/logical/6f9.../foo | xxd | head -2
00000000: 0135 3161 3266 3835 622d 6534 3266 2d34  .51a2f85b-e42f-4
00000010: 3565 342d 6236 3733 2d31 6236 6136 3762  5e4-b673-1b6a67b
# byte 0 = keyring term (0x01), the rest is the GCM blob. No key material anywhere.
```

### 6.2 Seal and unseal — and the reason recovery keys are not unseal keys

A **sealed** Vault holds the root key nowhere. It has the encrypted keyring on disk and no way to read it, and it answers exactly three families of endpoint: `sys/health`, `sys/seal-status`, and the unseal endpoints. Everything else is a 503. That is the intended property: **a Vault that is not deliberately opened is a rock**.

**Shamir.** `vault operator init` generates the unseal key, splits it with Shamir's Secret Sharing into *n* shares with threshold *t*, prints the shares once, and never stores them. Unsealing means submitting *t* shares to reconstruct the unseal key, which decrypts the root key, which decrypts the keyring.

```bash
vault operator init -key-shares=5 -key-threshold=3
# Unseal Key 1: ... (5 of these, printed once, to 5 different humans, PGP-encrypted in practice)
# Initial Root Token: hvs....

vault operator unseal   # x3, ideally by 3 different people from 3 different machines
vault status
# Sealed          false
# Total Shares    5
# Threshold       3
# HA Mode         active
```

**Auto-unseal.** The `seal` stanza names an external key manager which wraps the root key directly, so a restarting Vault decrypts its own root key with no human present.

```hcl
seal "awskms" {
  region     = "us-east-1"
  kms_key_id = "arn:aws:kms:us-east-1:111122223333:key/abcd-...."
}
```

**And here is the part that ends companies.** With auto-unseal, `vault operator init` still prints keys — but they are **recovery keys**, and they are a different object with a different job:

| | Unseal key (Shamir) | Recovery key (auto-unseal) |
|---|---|---|
| Can decrypt the root key | **Yes** | **No** |
| Can unseal a sealed Vault | **Yes** | **No** — only the KMS/HSM can |
| Authorises `operator generate-root` | Yes | Yes |
| Authorises `operator rekey -target=recovery` | n/a | Yes |
| Authorises DR promotion | Yes | Yes |

So under auto-unseal the KMS key is the **only** thing in the universe that can open your barrier. Delete it — or lose the account, or let a "clean up unused KMS keys" policy schedule it for deletion, or restore into a region where it does not exist — and every Vault node, every Raft snapshot and every offsite backup you own becomes permanently unreadable. Recovery keys will not help, because they were never able to do that job.

The controls follow directly: put a deletion-protection and a key policy on the KMS key that no automation can override, replicate it as a multi-region key, record its ARN in the disaster-recovery runbook next to the snapshot location, and — if the regulatory posture allows it — consider `[Enterprise]` Seal HA so a single KMS outage does not seal the cluster.

**Seal migration** between mechanisms is supported (`seal` plus `disabled = true` on the old stanza, then a rolling restart with a quorum of the *old* keys). It is a real, rehearsable operation, and the only reason it exists is that people do change their minds about this layer.

### 6.3 Integrated Storage (Raft) — the operational reality

Integrated Storage puts a Raft log and a BoltDB-backed FSM on each Vault node's own disk. There is no external cluster. Practical mechanics:

```hcl
storage "raft" {
  path    = "/opt/vault/data"
  node_id = "vault-1"

  retry_join { leader_api_addr = "https://vault-1.internal:8200" }
  retry_join { leader_api_addr = "https://vault-2.internal:8200" }
  retry_join { leader_api_addr = "https://vault-3.internal:8200" }
}

listener "tcp" {
  address       = "0.0.0.0:8200"
  cluster_address = "0.0.0.0:8201"      # node-to-node: Raft + request forwarding
  tls_cert_file = "/etc/vault/tls/tls.crt"
  tls_key_file  = "/etc/vault/tls/tls.key"
}

api_addr     = "https://vault-1.internal:8200"   # how CLIENTS reach this node
cluster_addr = "https://vault-1.internal:8201"   # how PEERS reach this node
```

Four things to know:

1. **`api_addr` and `cluster_addr` must be individually correct per node.** They are how a standby tells a client where the active node is, and how peers find each other. Setting them to a load-balancer address is a classic bootstrap failure — the standby redirects the client to the load balancer, which sends it back to the standby.
2. **Autopilot** manages the server set automatically: dead-server cleanup, `min_quorum` protection, and stabilisation before a new node counts toward quorum. Check it with `vault operator raft autopilot state` after every node replacement; a cluster that looks healthy in `vault status` can be one node from losing quorum in autopilot's view.
3. **Snapshots are the backup.** `vault operator raft snapshot save prod.snap` captures everything inside the barrier, still encrypted. Take them on a schedule, store them where the KMS key is not, and **restore one into a scratch cluster every quarter** — a snapshot you have never restored is a hypothesis.
4. **Raft is not a throughput layer.** Every write is a quorum write. Vault's write path is deliberately not fast, and this is the reason lease churn (§6.9) is expensive: each issuance and each revocation is at least one quorum write.

**Sizing, as arithmetic rather than a shrug.** Vault's storage footprint is driven almost entirely by *countable objects*, not by secret size, so it is predictable in advance:

```
  what grows the barrier          rough cost each     a 2,500-pod fleet
  --------------------------      ----------------    -----------------
  a service token                 ~ 1 KB              2,500 tokens    ~   2.5 MB
  a lease                         ~ 1 KB              2,500 leases    ~   2.5 MB
  an identity entity + alias      ~ 2 KB              2,500 entities  ~   5.0 MB
  a KV v2 secret, 10 versions     ~ size x 10         3,000 secrets   ~  30-60 MB
  a STORED pki certificate        ~ 2 KB              1,700 / day     ~ 3.4 MB / day  <-- unbounded

  Rules of thumb that follow:
    - a healthy cluster of this shape sits comfortably in single-digit GB
    - the only unbounded line is stored PKI, which is why no_store=true matters
    - Raft wants low-latency disk far more than it wants capacity: put the data
      directory on local NVMe or a provisioned-IOPS volume, never on NFS
    - size the disk for 5-10x the working set, because Raft keeps log entries
      and snapshots alongside the FSM
```

### 6.4 KV v1 and KV v2 — and the policy trap that catches everyone

KV v2 adds versioning, soft delete, metadata and check-and-set. It also **changes the API path**, and that change is invisible from the CLI.

```
  What you type:      vault kv get       secret/app/db
  What the API sees:  GET                secret/data/app/db
  Metadata lives at:  GET                secret/metadata/app/db
  Deleting a version: DELETE             secret/data/app/db
  Destroying data:    PUT                secret/destroy/app/db
  Listing keys:       LIST               secret/metadata/app/db     <- not secret/data
```

So a policy written the obvious way grants **nothing**:

```hcl
# BROKEN on a KV v2 mount. Grants read on a path no client ever requests.
path "secret/app/db" {
  capabilities = ["read"]
}
```

```hcl
# FIX: write policies against API paths, not CLI paths.
path "secret/data/app/*" {
  capabilities = ["read"]
}
path "secret/metadata/app/*" {
  capabilities = ["list", "read"]        # needed for `vault kv list` and version history
}
# Deliberately NOT granted: secret/destroy/* and secret/delete/* -- destroying
# a version is irreversible, and almost no application needs it.
```

The symptom is always the same and always misread: the operator tests with a root token, everything works, the application gets `permission denied`, and the team concludes the auth method is broken. `vault token capabilities <token> secret/data/app/db` answers it in one command — it reports the *effective* capabilities on the real path.

Other KV v2 mechanics worth knowing:

- **`max_versions`** defaults to **10**; the eleventh write silently discards version 1. If something reads by explicit version, that is a time bomb.
- **`cas_required`** forces every write to name the version it is replacing, turning concurrent writes from last-writer-wins into a detectable conflict. Turn it on for anything a pipeline writes.
- **`delete_version_after`** gives versions their own expiry, independent of any lease.
- **Delete is not destroy.** `vault kv delete` marks a version deleted and `vault kv undelete` brings it back; `vault kv destroy` removes the data; `vault kv metadata delete` removes everything including history. Only the last two are irreversible, and the CLI verbs read almost identically at 3am.
- **KV reads produce no lease.** A KV secret has no expiry and no revocation — which is exactly why §4.2 calls it the least interesting third of the product.

### 6.5 The database secrets engine — the credential factory in detail

```bash
vault secrets enable -path=database database

vault write database/config/app-pg \
  plugin_name="postgresql-database-plugin" \
  connection_url="postgresql://{{username}}:{{password}}@pg.prod:5432/app?sslmode=require" \
  allowed_roles="app-ro,app-rw" \
  username="vault_root" \
  password="$BOOTSTRAP_PW" \
  password_authentication="scram-sha-256" \
  max_open_connections=4 \
  max_idle_connections=0 \
  max_connection_lifetime=0

# Immediately destroy the bootstrap password -- Vault rotates it to something nobody knows:
vault write -f database/rotate-root/app-pg

vault write database/roles/app-rw \
  db_name="app-pg" \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT app_rw TO \"{{name}}\";" \
  revocation_statements="REASSIGN OWNED BY \"{{name}}\" TO app_owner; DROP OWNED BY \"{{name}}\"; DROP ROLE IF EXISTS \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"
```

Five details that are the difference between a demo and production:

1. **`rotate-root` is mandatory, not optional.** The bootstrap password is in your shell history, your CI logs and your Terraform state. Rotating it means the privileged credential is known only to Vault. The cost is real and must be accepted deliberately: **you can never log in as that user again**, and restoring an old Vault snapshot will restore an old, now-wrong password for it.
2. **`max_open_connections` defaults to 4.** That is Vault's own pool *to* the database, and it throttles issuance and revocation together. A revocation storm — say, `lease revoke -prefix` over 4,000 leases — queues four at a time behind it.
3. **The revocation statement is where zombies come from.** The naive `DROP ROLE "{{name}}"` fails in PostgreSQL if the role owns any object or holds any grant, and Vault records the revocation as failed and retries. Do the `REASSIGN OWNED` / `DROP OWNED` dance above, or accumulate thousands of undead roles and a permanently-growing lease table.
4. **`VALID UNTIL '{{expiration}}'` is belt and braces.** It makes the *database* expire the account even if Vault never gets to run the revocation — the single most valuable line in the statement when Vault has an outage.
5. **`allowed_roles` is a real boundary.** Without it, any role on the mount can use any connection, so a low-privilege role can be pointed at your most privileged database config.

**The connection-exhaustion failure mode.** This is the one that turns a successful Vault rollout into a database incident, and it is arithmetic, not bad luck. Each pod fetches a credential, opens a connection **pool** with it, and holds those connections. When the lease expires the pod fetches a *new* credential and opens a *new* pool — but the old pool's sockets may still be open, and in PostgreSQL **dropping a role does not terminate its existing sessions**. So for a window you have two generations of connections per pod:

```
  200 pods x pool_size 10                      = 2,000 connections steady state
  during a TTL boundary, two generations       = up to 4,000
  PostgreSQL max_connections default           =   100
                                                 -------
  outcome: "FATAL: sorry, too many clients already", for everyone
```

The fixes are all boring and all necessary: a connection **proxy** (PgBouncer) in front of the database so pooling is centralised, an application pool that closes cleanly on credential rotation, a `default_ttl` matched to the application's real connection lifetime rather than to a security aspiration, and `SELECT pg_terminate_backend(...)` in the revocation statement if you genuinely need old sessions gone.

### 6.6 Static roles and root rotation — when the account must keep its name

A dynamic role mints a new principal per request. A **static role** adopts an existing one and rotates only its password:

```bash
vault write database/static-roles/legacy-reporting \
  db_name="app-pg" \
  username="reporting_svc" \
  rotation_period="24h" \
  rotation_statements="ALTER USER \"{{name}}\" WITH PASSWORD '{{password}}';"

vault read database/static-creds/legacy-reporting
# username           reporting_svc
# password           A1b2C3...
# last_vault_rotation  2026-08-04T09:00:00Z
# ttl                  18h23m            <- time until the NEXT rotation, not a lease
```

The differences that matter:

- **Every reader gets the same username and the current password.** There is no per-consumer isolation and no per-consumer revocation.
- **There is no lease.** The `ttl` field is the countdown to the next rotation. Nothing is revoked when it hits zero; the password simply changes, and any consumer still holding the old one breaks.
- **It fixes the lease-count problem completely.** One account, one rotation per period, regardless of how many consumers read it.
- **`vault write -f database/rotate-role/<name>` forces an immediate rotation** — the correct first action on suspected compromise.

Use static roles for: an account that appears in grants, audit rules or third-party allowlists by name; a legacy system that cannot tolerate a changing username; and any case where the lease arithmetic in §6.9 says dynamic is too expensive. Use dynamic everywhere else.

### 6.7 The PKI engine — a real CA, with real obligations

```bash
vault secrets enable -path=pki_int pki
vault secrets tune -max-lease-ttl=43800h pki_int      # 5 years, for the intermediate itself

vault write pki_int/roles/internal-service \
  allowed_domains="svc.cluster.local,internal.example.com" \
  allow_subdomains=true \
  allow_bare_domains=false \
  allow_wildcard_certificates=false \
  key_type="ec" key_bits=256 \
  ttl="72h" max_ttl="720h" \
  no_store=true \
  generate_lease=false

vault write pki_int/issue/internal-service common_name="orders.svc.cluster.local" ttl="72h"
# certificate, issuing_ca, ca_chain, private_key, serial_number
```

The two flags in that role are the ones that decide whether your PKI mount survives contact with a real fleet:

- **`no_store=true`** stops Vault writing every issued certificate into storage. At 5,000 pods with a 72h certificate you issue roughly **1,700 certificates a day**; storing them is unbounded growth in the barrier and a CRL that eventually cannot be fetched. The trade is that a `no_store` certificate **cannot be revoked** — which is fine, and is the modern answer: a 72-hour certificate does not need revocation, because expiry beats CRL propagation.
- **`generate_lease=false`** (the default) means issuance creates no lease. Setting it `true` makes every certificate a lease object, which is the fastest way to discover the §6.9 arithmetic the hard way.

Other essentials: run a **root CA offline** and only an intermediate inside Vault; schedule `vault write pki_int/tidy` with `tidy_cert_store` and `tidy_revoked_certs` or the store grows forever; set `leaf_not_after_behavior` deliberately so a leaf whose TTL exceeds the issuer's is either truncated or rejected rather than silently wrong; and if you serve certificates to Kubernetes, prefer `cert-manager` with the Vault issuer over hand-rolled renewal.

### 6.8 Transit — encryption as a service, so the app never holds a key

```bash
vault secrets enable transit
vault write -f transit/keys/pii type=aes256-gcm96

vault write transit/encrypt/pii plaintext=$(base64 <<< "4111111111111111")
# ciphertext   vault:v1:8SDd3WHDOjf7mq69CyFYSFgVvSw...
#                    ^^ key VERSION, carried in the ciphertext

vault write transit/keys/pii/rotate                    # now at v2; v1 still decrypts
vault write transit/keys/pii/config min_decryption_version=2   # retire v1 for reads
vault write transit/rewrap/pii ciphertext="vault:v1:..."       # re-encrypt WITHOUT plaintext
```

What makes it worth the round trip: the key material never leaves Vault, so an application compromise yields ciphertext and an audit trail rather than a key; rotation is a single call because the version travels **inside the ciphertext**; and `rewrap` upgrades stored ciphertext to the current key **without Vault ever returning the plaintext to the caller**, which means a batch re-encryption job needs only `update` on the rewrap path.

The costs are equally concrete. Every encrypt and decrypt is a network round trip, so it belongs on field-level PII and not on a hot path — use `transit/encrypt/<key>` in **batch mode** (`batch_input`) to amortise, or `transit/datakey/plaintext/<key>` for envelope encryption where Vault mints a data key, you use it locally, and you store only the wrapped copy. And **convergent encryption** (same plaintext to same ciphertext, needed for equality search) leaks equality by construction; enable it only when you have decided that trade explicitly.

### 6.9 Leases — the centrepiece, and the arithmetic nobody runs first

Every dynamic secret and every service token is accompanied by a **lease**: a server-side object with an id, a TTL, an issue time, a renewability flag and a revocation path. The expiration manager holds them and acts when they expire. This is Vault's most important abstraction and its most common source of production trouble.

**TTL precedence** is the box in §5.5: the most specific value wins, and the system default of **768h (32 days)** is a backstop, not a suggestion. `default_lease_ttl` and `max_lease_ttl` both default to 768h at the system level; a mount tune overrides them for the mount; a role's `default_ttl` / `max_ttl` overrides them again.

**Renewal is "set the remaining time", not "add time".** The `increment` is measured from *now*:

```bash
vault lease renew -increment=1h database/creds/app-rw/h4d9...
# If the lease had 4h left, it now has 1h. This SHORTENS it.
vault lease renew database/creds/app-rw/h4d9...
# No increment: Vault re-applies the role's default TTL. Usually what you meant.
```

and no renewal can push total lifetime past `max_ttl`. At the ceiling the lease **just expires** — no error, no warning to the client. A client that renews forever and never re-fetches works flawlessly until exactly `max_ttl` after start-up, which for a role with `max_ttl=24h` is a daily 3am outage nobody can reproduce during the day.

**Revocation** works by id or by prefix, and prefix revocation is the reason leases exist:

```bash
vault lease revoke database/creds/app-rw/h4d9...        # one credential
vault lease revoke -prefix database/creds/app-rw        # every credential this role ever issued
vault lease revoke -prefix -force database/creds/app-rw # forget them WITHOUT calling the database
```

`-force` is the emergency valve when the target database is unreachable and Vault's retry queue is growing without bound. It leaves real accounts alive on the target — you now own that cleanup manually. Know that it exists; reach for it only when the alternative is Vault falling over.

#### The lease-count trap, as arithmetic

Here is the reasoning that surprises nearly everyone. In steady state:

```
  active_leases  ~=  issuance_rate  x  TTL

  A well-behaved client re-fetches when its credential expires, so:

  issuance_rate  =  N_clients / TTL

  Substituting:

  active_leases  ~=  (N_clients / TTL) x TTL  =  N_clients
                                                 ^^^^^^^^^
                                        INDEPENDENT OF TTL
```

**Halving the TTL does not halve the lease count. It leaves the count unchanged and doubles both the issuance rate and the revocation rate.** Worked, at 4,000 pods:

```
  TTL      active leases     issuance/s      revocations/s    each revocation is
  ------   --------------    -----------     --------------   ------------------
  1h        ~4,000             1.11             1.11          one DROP ROLE
  30m       ~4,000             2.22             2.22          one DROP ROLE
  5m        ~4,000            13.33            13.33          one DROP ROLE
```

Each issuance is a `CREATE ROLE` plus a quorum write to Raft. Each revocation is a `DROP ROLE` plus another quorum write. At 5 minutes you have asked Vault and PostgreSQL to do roughly **27 privileged operations per second, forever**, and bought exactly zero reduction in lease count — while Vault's own `max_open_connections` of 4 (§6.5) throttles the whole thing into a growing backlog.

```mermaid
xychart-beta
    title "Privileged database operations per second at 4,000 clients"
    x-axis ["TTL 8h", "TTL 1h", "TTL 30m", "TTL 5m"]
    y-axis "issuances plus revocations per second" 0 --> 30
    bar [0.28, 2.22, 4.44, 26.67]
```

The bars are the cost. The thing they are supposed to be buying — the lease count — is **4,000 at every one of those four TTLs**, which is why it does not appear on the chart: it is a flat line the shape of the x-axis. Each unit on the y-axis is one `CREATE ROLE` or `DROP ROLE` against the database plus one quorum write to Raft, and Vault's default pool of four connections per database config is the ceiling all of them queue behind.

**The levers that actually reduce lease count**, in the order to try them:

1. **Share the lease inside the process.** One credential per *process*, not per goroutine, per request or per worker. This is the single biggest win and it is an application change, not a Vault one.
2. **Use batch tokens** for anything short-lived that does not need revocation (§4.6). No lease at all, no storage write, no revocation work.
3. **Use static roles** where per-consumer isolation genuinely does not matter (§6.6). One account, one rotation per period, N consumers.
4. **Set a lease-count quota** so a runaway client is throttled instead of taking the cluster down (`sys/quotas/lease-count`, **Enterprise**) — see §6.22.
5. **Only then** tune the TTL, and tune it *upward* toward the real risk tolerance rather than downward toward an aspiration.

Watch `vault.expire.num_leases` as a first-class SLI. A cluster whose lease count is climbing monotonically has a client that fetches and never re-uses, and it will find the storage ceiling before anyone notices.

### 6.10 Tokens — the session object, and the two operations to memorise

Every authenticated request carries a token. Its lifecycle is the same lease machinery as §6.9 applied to the session:

```bash
vault token create -policy=app -ttl=1h -explicit-max-ttl=24h -period=""
vault token lookup -accessor <accessor>       # inspect WITHOUT holding the token
vault token revoke -accessor <accessor>       # revoke WITHOUT holding the token
vault token revoke <token>                    # revokes it AND every child token
```

The **accessor** is the operationally important idea: it is a handle that permits `lookup`, `renew` and `revoke` but never authentication. It is what appears in audit logs, and it is how you revoke a token you can see in an audit trail but do not possess.

**Token hierarchy.** A token created by another token is its **child**, and revoking a parent revokes the whole subtree. That is powerful and it is a footgun: the token your Agent used to create a wrapped token is a parent, and revoking it cascades. `-orphan` breaks the link deliberately, which is what auth methods do for their issued tokens.

**Root tokens.** `vault operator generate-root` produces one from a quorum of unseal or recovery keys via a one-time-password protocol, so no single operator ever sees it in the clear on the wire. Use it for the bootstrap, for enabling the first audit device, and for breaking glass — then `vault token revoke` it. A long-lived root token sitting in a CI variable is the finding every Vault audit opens with. `[2.0]` `sys/generate-root` is now an **authenticated** endpoint; see §6.23.

### 6.11 Auth methods and the secret-zero problem

Secret-zero is the question "what credential does the workload need in order to obtain credentials?", and Vault's answer is always the same shape: **do not distribute one — verify something the platform already gave the workload.**

```
  BAD, and it is a whole class:      GOOD, and every entry is the same idea:
  ----------------------------      -------------------------------------
  a Vault token in a K8s Secret     the pod's projected SA token   (kubernetes)
  a long-lived AppRole secret_id    the instance's signed identity (aws/azure/gcp)
  a shared password in CI           the pipeline's OIDC token      (jwt)
  a client cert with a 2-year TTL   a short-lived client cert      (cert)
```

Common tuning across every method, set with `vault write auth/<mount>/tune` or on the role:

- `token_ttl`, `token_max_ttl` — the session length, distinct from any secret's lease
- `token_policies` — what the resulting token can do
- `token_bound_cidrs` — pin the token to source networks; cheap defence-in-depth
- `token_num_uses` — a one-shot token for a job that fetches once and exits
- `token_type` — `service` or `batch` (§4.6); `default-batch` on a high-volume mount is a real lever

The generic advice that survives every method: **the auth mount's TTLs are not the secret's TTLs**, and confusing them produces an application that holds a valid database credential and a dead token, or the reverse.

### 6.12 Kubernetes auth in depth

```bash
vault auth enable kubernetes

vault write auth/kubernetes/config \
  kubernetes_host="https://kubernetes.default.svc:443" \
  kubernetes_ca_cert=@/var/run/secrets/kubernetes.io/serviceaccount/ca.crt \
  disable_local_ca_jwt=false

vault write auth/kubernetes/role/orders \
  bound_service_account_names="orders-sa" \
  bound_service_account_namespaces="prod" \
  audience="vault" \
  token_policies="orders" \
  token_ttl="1h" token_max_ttl="4h"
```

```mermaid
sequenceDiagram
    autonumber
    participant P as Pod (orders-sa)
    participant V as Vault
    participant K as Kubernetes API

    P->>P: read projected SA token from /var/run/secrets/...
    P->>V: POST auth/kubernetes/login  role=orders, jwt=<SA token>
    V->>K: TokenReview with the presented JWT
    K-->>V: authenticated, ns=prod, sa=orders-sa, aud=vault
    V->>V: match bound_service_account_names and namespaces
    V->>V: resolve or create identity entity and alias
    V-->>P: Vault token, policies=orders, ttl=1h
    Note over P,V: no shared secret was distributed at any point
```

Three failure modes account for nearly every Kubernetes-auth ticket:

1. **The audience mismatch.** Modern projected service-account tokens are audience-bound. If the pod's `serviceAccountToken` projection sets `audience: vault` and the Vault role does not (or the reverse), login fails with a message about the token being invalid, and nothing in it says "audience".
2. **The reviewer JWT expired.** Vault must call `TokenReview`, which needs its own credential. `disable_local_ca_jwt=false` lets Vault use *its own* pod's service-account token, which the kubelet keeps fresh. Configuring a static `token_reviewer_jwt` instead — the pattern in a lot of older documentation — works beautifully for ninety days and then stops, because bound service-account tokens expire. **Prefer the local token, or use a long-lived Secret-backed token deliberately and monitor its age.**
3. **Vault outside the cluster.** Then Vault cannot use a local token and genuinely needs a `token_reviewer_jwt` plus network reachability to the API server, and the entire cluster's identity now depends on one long-lived credential in Vault. That is a real architectural cost of running Vault outside the cluster it serves.

Note also that `bound_service_account_names="*"` scoped to a namespace is a legitimate pattern for a namespace-per-team model, and a serious over-grant everywhere else.

### 6.13 JWT/OIDC auth — `bound_claims` authorises, `claim_mappings` only labels

This is the highest-severity misconfiguration on this page, because it fails **open** and looks correct in review.

```bash
vault write auth/jwt/config \
  oidc_discovery_url="https://token.actions.githubusercontent.com" \
  bound_issuer="https://token.actions.githubusercontent.com"

vault write auth/jwt/role/deploy-prod \
  role_type="jwt" \
  user_claim="sub" \
  bound_audiences="https://github.com/acme" \
  bound_claims_type="glob" \
  bound_claims='{"repository":"acme/payments","ref":"refs/heads/main"}' \
  claim_mappings='{"workflow":"workflow","actor":"actor"}' \
  token_policies="deploy-prod" \
  token_ttl="15m"
```

The two fields look like siblings. They are not:

| | `bound_claims` | `claim_mappings` |
|---|---|---|
| What it does | **Authorisation filter.** Login **fails** unless the presented token's claims match | **Copies** the named claims into the resulting token's metadata |
| Effect on who may log in | Restricts it | **None whatsoever** |
| Typical use | "only `acme/payments` on `refs/heads/main`" | "record which workflow and which actor, for the audit log and for policy templating" |

```hcl
# BROKEN: the security condition is in the wrong field.
# Every repository in the GitHub organisation can now assume deploy-prod --
# the repository claim is merely copied into metadata and checked by nobody.
claim_mappings='{"repository":"repository","ref":"ref"}'
```

```hcl
# FIX: the condition goes in bound_claims. Keep claim_mappings for labelling only.
bound_claims='{"repository":"acme/payments","ref":"refs/heads/main"}'
claim_mappings='{"workflow":"workflow","actor":"actor"}'
```

Three companions matter. `bound_audiences` must be set or any token from that issuer is acceptable. `bound_claims_type` defaults to exact string matching; `glob` enables `acme/*` patterns and is where an over-broad `*` hides. And metadata written by `claim_mappings` is available to policy templating as `{{identity.entity.aliases.<accessor>.metadata.workflow}}` — which is genuinely useful, and is the reason the field exists at all.

### 6.14 AppRole — the fallback, and the only safe way to deliver a `secret_id`

AppRole is what you use when no platform identity exists: bare metal, an appliance, a legacy VM, a vendor's agent. It splits credentials in two.

```bash
vault write auth/approle/role/legacy-batch \
  token_policies="legacy-batch" \
  token_ttl="1h" token_max_ttl="4h" \
  secret_id_ttl="10m" \
  secret_id_num_uses=1 \
  secret_id_bound_cidrs="10.4.0.0/16" \
  token_bound_cidrs="10.4.0.0/16"

vault read auth/approle/role/legacy-batch/role-id          # role_id: semi-public, bake into config
vault write -f -wrap-ttl=120s auth/approle/role/legacy-batch/secret-id
# wrapping_token: hvs....   <- THIS is what you hand to the workload
```

- **`role_id`** is a stable identifier, roughly a username. It can live in a config file or an image.
- **`secret_id`** is the actual credential. A long-lived, reusable `secret_id` in a config file is precisely the secret-zero problem AppRole was meant to avoid, and it is how most AppRole deployments are actually run.

The safe pattern — the **trusted-orchestrator** or **response-wrapped delivery** pattern — is:

1. A trusted component (a configuration-management run, a CI job, a Nomad/Kubernetes controller) authenticates to Vault with an identity it *does* have.
2. It requests a `secret_id` with **`-wrap-ttl`**, receiving a single-use **wrapping token** rather than the `secret_id` itself (§6.17).
3. It delivers the wrapping token to the workload over a channel it already has.
4. The workload unwraps it, obtaining the `secret_id`, and logs in.

The property that makes this work: a wrapping token can be unwrapped **exactly once**. If an attacker intercepts and unwraps it, the legitimate workload's unwrap fails loudly, and you have detection rather than a silent compromise. Add `secret_id_num_uses=1`, a `secret_id_ttl` of minutes, and CIDR binding on both the `secret_id` and the resulting token, and the residual exposure is a short-lived, single-use, network-pinned credential.

**Never** use AppRole where a platform identity exists. On Kubernetes that is always; on EC2 that is always; in GitHub Actions that is always.

### 6.15 Policies — path matching, capabilities, deny, and templating

Policies are HCL granting **capabilities** on **API paths**. The eight capabilities map to HTTP verbs plus two specials:

| Capability | HTTP | Note |
|---|---|---|
| `create` | POST/PUT where nothing exists | Most engines only check `update` |
| `read` | GET | |
| `update` | POST/PUT | The one most people mean by "write" |
| `patch` | PATCH | KV v2 partial update |
| `delete` | DELETE | |
| `list` | LIST | **Separate from `read`** — you can list without reading and vice versa |
| `sudo` | any | Required *in addition* on root-protected paths such as `sys/seal`, `auth/token/create-orphan`, `pki/root/sign-self-issued` |
| `deny` | any | **Overrides everything, everywhere, unconditionally** |

**Path matching** has exactly two wildcards and one precedence rule:

```hcl
path "secret/data/app/db"      { capabilities = ["read"] }             # exact
path "secret/data/app/+/creds" { capabilities = ["read"] }             # + = ONE segment
path "secret/data/app/*"       { capabilities = ["read","list"] }      # * = trailing glob only
path "secret/data/app/prod/*"  { capabilities = ["deny"] }             # deny wins
```

The rule is **most-specific-wins, and it is not additive.** An exact path beats a `+` match, which beats a `*` glob. So a policy granting `read` on `secret/data/app/*` and `deny` on `secret/data/app/prod/*` denies prod, and the order of the stanzas in the file is irrelevant. Equally: if a token has two policies, one granting and one denying the same path, **deny wins** — there is no "the more permissive policy applies".

**Templating** lets one policy serve every entity:

```hcl
# Each entity reaches only its own subtree. One policy, N tenants.
path "secret/data/teams/{{identity.groups.names}}/*" {
  capabilities = ["read", "list"]
}
path "secret/data/users/{{identity.entity.id}}/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "secret/data/apps/{{identity.entity.aliases.auth_kubernetes_a1b2c3d4.metadata.service_account_name}}/*" {
  capabilities = ["read"]
}
```

Two constraints. The alias accessor in that last path is the **mount accessor** (`vault auth list -detailed`), so a policy is bound to a specific auth mount and breaks if the mount is recreated. And `[2.0]` **globs are now prohibited in rendered identity-template output** — a metadata value containing `*` used to expand into a wildcard at evaluation time, which meant an attacker who controlled a claim could widen their own policy. That is now rejected outright, and it is a breaking change for anyone whose metadata legitimately contained a `*`.

**A complete application policy**, for reference — this is roughly what a real service's policy looks like once every trap above is accounted for:

```hcl
# Policy: orders-service.  Attached by the kubernetes auth role "orders".

# --- Static configuration values (KV v2: note the data/ and metadata/ segments) ---
path "secret/data/orders/*" {
  capabilities = ["read"]
}
path "secret/metadata/orders/*" {
  capabilities = ["read", "list"]
}
# Deliberately absent: secret/destroy/*, secret/delete/*, and any write capability.
# An application reads its configuration. It does not author it.

# --- Dynamic database credentials ---
path "database/creds/orders-rw" {
  capabilities = ["read"]
}

# --- Encryption as a service: encrypt and decrypt only, never key management ---
path "transit/encrypt/orders-pii" { capabilities = ["update"] }
path "transit/decrypt/orders-pii" { capabilities = ["update"] }
path "transit/keys/orders-pii"    { capabilities = ["deny"]  }   # cannot read, rotate or delete the key

# --- Certificates for mTLS ---
path "pki_int/issue/internal-service" {
  capabilities = ["update"]
}

# --- Self-management: renew and revoke its OWN token, look up its own capabilities ---
path "auth/token/renew-self"  { capabilities = ["update"] }
path "auth/token/revoke-self" { capabilities = ["update"] }
path "auth/token/lookup-self" { capabilities = ["read"]   }
path "sys/leases/renew"       { capabilities = ["update"] }

# --- Explicitly denied, even though nothing above would grant them ---
# Written down so a future broadening of a glob cannot silently pick them up.
path "sys/*"                  { capabilities = ["deny"] }
path "auth/token/create*"     { capabilities = ["deny"] }
```

Three habits are visible in it. Capabilities are the narrowest that work (`update` on `transit/encrypt`, not `create` and `update` and `read`). The **self-management block** is present, because a token that cannot renew itself will die mid-request and an application that cannot revoke itself leaves leases behind on shutdown. And the **defensive denies at the bottom** cost nothing today and are the reason a later well-intentioned `path "sys/leases/*"` grant cannot quietly widen into `sys/seal`.

Finally, `[2.0]` **`LIST` with a trailing slash now respects a more-specific deny.** Previously `LIST secret/metadata/app/` could bypass a `deny` written on `secret/metadata/app/prod`, because the trailing-slash form matched a different, less specific rule. Re-read your deny rules on upgrade; the change makes previously-working listings start failing, which is the correct outcome and will still look like a regression.

### 6.16 Identity — entities, aliases and groups

The identity store is the layer that says "the human who logged in via Okta and the CI job that logged in via OIDC are the same principal", and most teams discover it only when they need policy templating.

- An **entity** is the principal. It has an id, a name, and arbitrary **metadata**.
- An **alias** is one login identity on one auth mount, attached to an entity. `alice@okta` and `alice@ldap` become two aliases of one entity.
- A **group** contains entities and other groups. **Internal** groups are managed in Vault; **external** groups are mapped from an identity provider's group claim, so IdP membership drives Vault policy with no Vault-side change.

```bash
vault write identity/entity name="orders-service" \
  metadata=team="payments" metadata=tier="critical" policies="base"

vault write identity/entity-alias \
  name="orders-sa" \
  canonical_id="<entity_id>" \
  mount_accessor="$(vault auth list -format=json | jq -r '."kubernetes/".accessor')"

vault write identity/group name="payments-oncall" type="external" policies="break-glass"
vault write identity/group-alias name="payments-oncall" \
  mount_accessor="$(vault auth list -format=json | jq -r '."oidc/".accessor')" \
  canonical_id="<group_id>"
```

Policies attach at three levels and they **do** accumulate here: the token's own policies, plus the entity's, plus every group the entity belongs to. That is the one place in Vault where the answer is additive — and `deny` still beats all of it.

The identity layer also powers **identity tokens** (`identity/oidc/token/<role>`), which turn Vault into an OIDC provider issuing signed JWTs about an entity. That is how a workload authenticated to Vault proves *to a third system* who it is, without that system needing to talk to Vault at all.

### 6.17 Response wrapping and cubbyhole

**Cubbyhole** is a per-token private KV store at `cubbyhole/`. Nothing else — not even a root token — can read another token's cubbyhole, and it is destroyed when the token is revoked or expires. It is not a general-purpose store; it exists to make response wrapping possible.

**Response wrapping** takes any Vault response and, instead of returning it, stores it in the cubbyhole of a brand-new single-use token and returns that token:

```bash
vault kv get -wrap-ttl=5m secret/app/db
# wrapping_token:            hvs.CAESIJ...
# wrapping_token_ttl:        5m
# wrapping_token_creation_path: secret/data/app/db

vault unwrap hvs.CAESIJ...        # returns the original response, exactly once
vault unwrap hvs.CAESIJ...        # error: wrapping token is not valid or does not exist
```

Four properties do real work:

1. **Single use.** The second unwrap fails. That converts interception from an undetectable compromise into a loud failure at the legitimate consumer.
2. **Short TTL.** The secret is in flight for seconds, not for the life of a config file.
3. **`creation_path` is verifiable.** `vault write sys/wrapping/lookup token=…` returns the path the wrapped response came from, *without unwrapping it*. A consumer should check it before unwrapping — otherwise an attacker who can hand you a wrapping token can hand you one wrapping a different, attacker-chosen response.
4. **The intermediary never sees the value.** A CI system, a config-management run or an operator can carry a wrapping token without being trusted with its contents.

This is the mechanism behind safe AppRole delivery (§6.14), and it is the correct way to hand any secret to a human over any channel.

### 6.18 Audit devices — and why "all devices down" means "Vault down"

Vault's audit guarantee is stronger than a log line, and the strength is the surprise:

> **A request is written to at least one enabled audit device before it is served. If Vault cannot write to at least one device, it refuses the request.**

Not "logs a warning". Not "drops the audit entry". **Refuses.** So an audit device that blocks — a full disk on a `file` device, an unreachable endpoint on a `socket` device, a wedged syslog daemon — takes the entire cluster down, returning 500s to every client while the process itself is healthy, unsealed and answering `sys/health`.

```bash
vault audit enable file file_path=/vault/logs/audit.log
vault audit enable -path=file-2 file file_path=/vault/logs/audit-2.log   # the second one
vault audit list -detailed
```

The rule that follows is short and non-negotiable: **enable at least two devices, on independent failure domains.** Two files on the same volume are one device. A file plus a socket to a log collector is two. Vault sends to *all* enabled devices and needs only one to succeed, so a second device converts a total outage into a monitoring alert.

Other mechanics:

An entry looks like this — request and response are logged separately and correlated by `request.id`:

```json
{
  "time": "2026-08-04T09:14:22.417Z",
  "type": "response",
  "auth": {
    "client_token": "hmac-sha256:9f2c...",
    "accessor": "1kL9xQ...",
    "display_name": "kubernetes-prod-orders-sa",
    "policies": ["default", "orders-service"],
    "metadata": { "service_account_name": "orders-sa", "service_account_namespace": "prod" },
    "entity_id": "a3f1e8b2-..."
  },
  "request": {
    "id": "6c1e...",
    "operation": "read",
    "path": "database/creds/orders-rw",
    "remote_address": "10.4.12.87",
    "mount_type": "database"
  },
  "response": {
    "data": { "username": "hmac-sha256:be41...", "password": "hmac-sha256:07da..." }
  }
}
```

Read the four fields that make it useful in an incident: **`accessor`** is what you feed to `vault token revoke -accessor` to cut this client off without knowing its token; **`entity_id`** ties every login this principal ever made across every auth method into one identity; **`path`** and **`mount_type`** tell you exactly which credential was taken; and the **HMACs** are stable, so you can correlate "this same password was read here, here and here" without ever recovering it.

- **Sensitive strings are HMAC-SHA256'd** with a per-Vault audit salt, so the log records *that* a value was accessed without recording the value. The HMAC is stable, so you can correlate across entries without ever recovering plaintext.
- **`log_raw=true`** disables that. It exists for debugging and it turns your log pipeline into a secondary secrets store. Do not enable it in production; if you must, treat the log store as Vault-equivalent in classification.
- **`hmac_accessor`** controls whether token accessors are hashed. Leave them **unhashed** — the accessor is not a credential, and it is what lets you revoke a token you saw misbehaving (§6.10).
- The audit log carries the request and the response separately, correlated by `request.id`, with the client token's accessor, the remote address, the mount point and the exact path. It is the single most useful artifact in any incident involving credentials.
- **Do not send audit logs to a system that authenticates using a Vault-issued credential.** That loop turns a Vault hiccup into a permanent outage.

### 6.19 Vault Agent and Vault Proxy

**Vault Agent** is a client-side daemon that does three things, each independently useful:

```hcl
# /etc/vault-agent.hcl
pid_file = "/var/run/vault-agent.pid"

auto_auth {
  method "kubernetes" {
    mount_path = "auth/kubernetes"
    config = { role = "orders" }
  }
  sink "file" {
    config = { path = "/run/secrets/vault-token", mode = 0640 }
  }
}

cache {
  use_auto_auth_token = true          # the agent attaches the token for you
}

template {
  destination = "/run/secrets/db.env"
  contents    = <<-EOT
    {{- with secret "database/creds/app-rw" -}}
    DB_USER={{ .Data.username }}
    DB_PASS={{ .Data.password }}
    {{- end }}
  EOT
  command     = "/usr/bin/systemctl reload orders"     # <-- the line everyone forgets
}
```

1. **Auto-auth.** It authenticates, keeps the token renewed, and writes it to a sink. Your application never implements a login flow.
2. **Caching.** It caches tokens and leases and can proxy the whole Vault API from `localhost`, so a fleet of processes on one host produces one Vault connection.
3. **Templating.** It renders secrets into files with the `consul-template` language and **renews the underlying leases**, re-rendering when a value changes.

**The failure everyone hits: re-rendering a file does not restart your process.** The agent updates `/run/secrets/db.env` at 03:00 with a fresh credential, and the application — which read the file once at start-up and built a connection pool — carries on with a revoked one until it happens to reconnect. Then it fails, at a time unrelated to any deploy. The `command` field is the fix: run a reload, send a signal, or touch a file the application watches. If the application genuinely cannot reload, the honest answer is to restart it, which is exactly what §6.20's `rolloutRestartTargets` does on Kubernetes.

Two more agent facts worth carrying: `exit_after_auth = true` turns the agent into an **init container** that fetches once and exits, which is how the Injector's init container works; and the **persistent cache** lets an agent restart without re-authenticating, which matters on a host where the auth method has a rate limit.

**Vault Proxy** is the caching-and-auto-auth half, split out as its own binary and configuration so you can run the API proxy without adopting the templating engine. If all you want is "one authenticated connection to Vault per host, and my apps talk to `127.0.0.1:8200`", Proxy is the smaller thing to run.

### 6.20 The Vault Secrets Operator and the CSI provider

**VSO `[VSO 1.5]`** is the Kubernetes-native option: a controller, a set of CRDs, no sidecar in your pods.

```yaml
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultAuth
metadata: { name: prod-auth, namespace: prod }
spec:
  method: kubernetes
  mount: kubernetes
  kubernetes: { role: orders, serviceAccount: orders-sa, audiences: ["vault"] }
---
apiVersion: secrets.hashicorp.com/v1beta1
kind: VaultDynamicSecret
metadata: { name: orders-db, namespace: prod }
spec:
  vaultAuthRef: prod-auth
  mount: database
  path: creds/app-rw
  destination:
    create: true
    name: orders-db          # a NATIVE Kubernetes Secret, written by the operator
  renewalPercent: 67         # renew at 67% of the lease, not at the last moment
  rolloutRestartTargets:
    - kind: Deployment
      name: orders           # <-- restarts the app when the credential changes
```

What VSO buys over the Injector: no sidecar in every pod (the resource saving is real at scale), a declarative Kubernetes-shaped API instead of pod annotations, `VaultAuthGlobal` to avoid repeating auth configuration per namespace, and — the differentiator — **`rolloutRestartTargets`**, which solves §6.19's re-render problem by restarting the workload rather than hoping it re-reads.

What it costs: the value lands in a **native Kubernetes Secret**, so it is in etcd, subject to `kubectl get secret -o yaml`, and only as protected as your RBAC and encryption-at-rest are. The Injector and the CSI provider deliberately avoid creating one. That is the trade, and it is a genuine one — see [`devops/secrets_management`](../../devops/secrets_management/secrets_management.md) for why "K8s Secrets are only base64" matters here.

**The CSI provider `[1.7.3]`** plugs Vault into the Secrets Store CSI Driver, mounting secrets as files in a volume. It is the lightest option and the least capable: it fetches at mount time and **does not renew a lease**. Rotation requires the driver's own polling (off by default), which makes it a poor fit for dynamic secrets and a fine fit for static KV values that change rarely.

**The Agent Injector `[vault-k8s 1.7.5]`** remains the annotation-driven option, mutating the pod spec to add an init container and a sidecar:

```yaml
annotations:
  vault.hashicorp.com/agent-inject: "true"
  vault.hashicorp.com/role: "orders"
  vault.hashicorp.com/agent-inject-secret-db: "database/creds/app-rw"
  vault.hashicorp.com/agent-inject-template-db: |
    {{- with secret "database/creds/app-rw" -}}
    DB_USER={{ .Data.username }}
    DB_PASS={{ .Data.password }}
    {{- end }}
  vault.hashicorp.com/agent-inject-command-db: "sh -c 'kill -HUP 1'"
```

### 6.21 The OpenBao delta in mechanics — what actually breaks on migration

§4.8 gave the shape; here is what a migration script has to handle.

**`stored_shares` is removed `[OpenBao 2.6]`.** Any automation that calls `sys/init` or `sys/rekey/init` with `stored_shares` — and a lot of older Terraform and shell bootstrap does, because it was required with auto-unseal in old Vault versions — now receives an error rather than being ignored. Strip it.

**The container runs as `openbao`, not root `[OpenBao 2.6]`.** A `docker run -v /srv/vault:/openbao/data` that worked because the process was root now fails to write. Fix the ownership; do not fix it by running as root.

**The four built-in KMS seals are deprecated for removal in 2.7.0 `[OpenBao 2.6]`.** `awskms`, `azurekeyvault`, `gcpckms` and `pkcs11` move to an external plugin model. Anything auto-unsealing today needs a plan before 2.7.0, and "we will deal with it at upgrade time" is how a cluster ends up unable to start.

**`file` storage is deprecated `[OpenBao 2.6]`.** Only relevant for dev and test rigs — but those rigs are in CI, and CI is where a deprecation warning becomes a failed pipeline.

**No Enterprise features, ever.** Namespaces, replication, HSM seals, Seal HA, performance standbys and lease-count quotas do not exist. If your Vault design leans on any of them, OpenBao is not a drop-in and the migration is a redesign. Note that OpenBao is building some of these ideas in the open, so this list shortens over time — check it against the current release rather than trusting this table in a year.

The migration itself, when it applies, is mechanically simple: OpenBao reads a Vault storage backend from the fork point. The work is entirely in the surrounding automation.

### 6.22 Quotas — the only thing standing between one bad client and the cluster

```bash
# Requests per second, per path, per client. Community edition.
vault write sys/quotas/rate-limit/global rate=2000 interval=1s
vault write sys/quotas/rate-limit/db-creds \
  path="database/creds/" rate=50 interval=1s block_interval=60s

# Lease-count ceiling. ENTERPRISE only.
vault write sys/quotas/lease-count/db-creds path="database/creds/" max_leases=10000
```

**Rate-limit quotas** are available in the community edition and should be configured on day one, not after the first incident. A quota applies to a namespace and optionally a path prefix and a role, counts per client IP (or per entity with `inheritable`), and returns **429** when exceeded. `block_interval` additionally blocks an offending client for a period after it trips, which is what stops a crash-looping pod from hammering the login endpoint.

**Lease-count quotas** are the direct control for §6.9's arithmetic — and they are **Enterprise**. On the community edition your equivalents are a rate-limit quota on the issuing path (which bounds the issuance rate and therefore, at a given TTL, the steady-state lease count) plus an alert on `vault.expire.num_leases`.

A starting alert set, which takes about twenty minutes to write and is the difference between finding §6.18's audit failure in nine minutes and finding it in ninety:

```yaml
# Vault exposes these at sys/metrics?format=prometheus (needs a token or an unauthenticated
# telemetry listener). Thresholds are illustrative -- derive yours from a week of baseline.
- alert: VaultSealed
  expr: vault_core_unsealed == 0
  for: 1m
  # The one that means "nothing works". Fires on every node, including standbys.

- alert: VaultLeaseCountClimbing
  expr: deriv(vault_expire_num_leases[6h]) > 0 and vault_expire_num_leases > 20000
  for: 30m
  # A monotonic climb is a client that fetches and never re-uses. See 6.9.

- alert: VaultRevocationBacklog
  expr: rate(vault_expire_revoke_error[5m]) > 0
  for: 10m
  # Revocations failing against an unreachable target. Grows without bound.

- alert: VaultRequestLatency
  expr: histogram_quantile(0.99, rate(vault_core_handle_request_bucket[5m])) > 0.5
  for: 10m
  # Usually storage pressure, and usually downstream of the lease count above.

- alert: VaultAuditDeviceCount
  expr: vault_audit_log_request_failure > 0
  for: 1m
  # Any audit write failure at all. With two devices this is a warning; with
  # one it is already an outage, which is exactly why you run two.

- alert: VaultRaftCommitLatency
  expr: histogram_quantile(0.99, rate(vault_raft_storage_bolt_write_bucket[5m])) > 0.1
  for: 10m
  # Slow disk. Raft cares about latency, not capacity.
```

The metrics to alert on, in priority order: `vault.expire.num_leases` (the ceiling that kills you), `vault.core.handle_request` p99 (the symptom of storage pressure), `vault.token.count` and `vault.token.count.by_auth`, `vault.expire.revoke` errors (a revocation backlog against an unreachable target), and `vault.core.unseal` / `vault.core.seal` as event markers. Add `vault.raft-storage.*` commit latencies if you are on Integrated Storage.

### 6.23 Operating Vault — rekey, rotate, upgrades, snapshots, DR drills, and the `[2.0]` breaking changes

**Rekey versus rotate.** They operate at different layers of §5.1 and neither substitutes for the other:

```bash
vault operator rekey -init -key-shares=5 -key-threshold=3
# Changes WHO CAN UNSEAL. Requires a quorum of the CURRENT keys. Issues new shares.
# Does not touch the encryption keys and does not re-encrypt anything.

vault operator rotate
# Adds a NEW TERM to the keyring. New writes use it, old terms still decrypt old data.
# Requires no quorum, takes milliseconds, and should be on a schedule.

vault operator rekey -init -target=recovery -key-shares=5 -key-threshold=3
# The auto-unseal equivalent: changes who holds RECOVERY keys.
```

The mnemonic: **rekey changes the lock on the front door; rotate changes the ink you write tomorrow's secrets in.** A compromised key-holder means rekey. A compliance requirement to rotate encryption keys means rotate. Neither re-encrypts existing data — that only happens as data is rewritten, which is why long-lived KV values sit under old terms indefinitely.

**Upgrades.** Vault upgrades in place, standbys first, active last, with a deliberate step-down:

```bash
# 1. Snapshot. Always.
vault operator raft snapshot save pre-upgrade-$(date +%F).snap
# 2. Upgrade each STANDBY, one at a time, waiting for it to rejoin:
vault operator raft list-peers
# 3. Then force the active node to hand over, and upgrade it last:
vault operator step-down
```

Never skip a major version, always read the release's upgrade guide, and remember that the storage schema is upgraded by the **active** node on first start — so a rollback after that point is a snapshot restore, not a binary swap.

**The `[2.0]` breaking changes** are all security hardening, and all four will break something:

| Change | Was | Is `[2.0]` | What breaks |
|---|---|---|---|
| `sys/rekey` endpoints | **Unauthenticated** (guarded only by the key quorum) | **Authenticated** | Bootstrap and DR automation that rekeys without a token |
| `sys/generate-root` | Unauthenticated | **Authenticated** | Break-glass scripts that assumed no token was needed — which is exactly when you have no token |
| The DR operation-token endpoint | Unauthenticated | **Authenticated** | DR promotion runbooks |
| `LIST` with a trailing slash | Could match a less-specific rule and bypass a deny | Respects the more-specific deny | Listings that "worked"; see §6.15 |
| Globs in rendered identity-template output | Expanded as wildcards | **Prohibited** | Policies whose metadata legitimately contains `*` |
| Non-canonical paths (`//`, `.`, `..`) | Normalised and served | **Rejected** | Clients that build paths by string concatenation |

The first three deserve a specific warning. They make Vault meaningfully safer — an unauthenticated rekey endpoint is a denial-of-service surface — and they mean **your break-glass procedure now requires a token at the moment you have none**. Re-write and re-rehearse that runbook *before* upgrading, not during the incident that needs it.

**The DR drill.** Rehearse quarterly, and rehearse the human part: reaching the recovery-key holders, generating the DR operation token from a quorum, promoting the secondary, and repointing clients. The technical steps are minutes; finding three people with keys at 2am is the part that fails.

---

## 7. Real-World Examples

- **Dynamic database credentials across a microservice fleet.** The canonical deployment: every service authenticates by its Kubernetes service account, receives a unique PostgreSQL user with a 1-hour lease, and the platform team's answer to "rotate the database password" becomes "there is no database password". The thing teams learn second is §6.5's connection arithmetic, usually from an incident.
- **Vault as the internal CA.** A PKI intermediate issuing 72-hour service certificates to a mesh, with `no_store=true` and `cert-manager` driving renewal. Replaces a wiki page describing how to run `openssl` and a spreadsheet of expiry dates. The characteristic incident is the intermediate itself expiring, because nobody set an alert on the one certificate that has no automation behind it.
- **Transit for application-layer field encryption.** A payments service encrypts card metadata through `transit/encrypt/pii` so the key never enters the application's memory. Compliance auditors get a per-access audit trail; the application team gets a latency budget item and a hard dependency on Vault availability for that code path.
- **CI/CD without long-lived cloud keys.** GitHub Actions and GitLab present their OIDC token to Vault's `jwt` auth with `bound_claims` on repository and ref, and receive a 15-minute AWS credential from the `aws` secrets engine. Removes the entire class of "long-lived deploy key in a CI variable" incidents — and depends entirely on §6.13's `bound_claims` being in the right field.
- **The multi-cloud argument.** An organisation with workloads in AWS, Azure and on-premises VMware uses one Vault as the common credential broker, because the alternative is three secret managers, three policy languages and three audit trails. This is the strongest case for Vault over a cloud-native manager, and it is why the cloud-native option so often loses in large enterprises and wins in small single-cloud teams.
- **The licence-driven fork.** Public-sector and some regulated organisations whose procurement rules require an OSI-approved licence moved to **OpenBao** with no functional requirement changing at all. The migration succeeded on the API and took its time on §6.21's operational surface.

---

## 8. Tradeoffs

### 8.1 The headline decision table

| Decision | Option A | Option B | What actually decides it |
|---|---|---|---|
| Run Vault at all | Self-hosted Vault/OpenBao | A cloud-native manager | Whether you need **dynamic credentials, PKI, transit, or multi-cloud**. If the answer is a KV store, you are paying for the wrong product |
| Licence | Vault (BUSL 1.1, IBM) | OpenBao (MPL 2.0, LF) | Whether policy requires OSI-approved, and whether you depend on an Enterprise feature |
| Seal | Shamir | Auto-unseal by KMS | Whether Vault may restart unattended. Almost always yes, so almost always auto-unseal |
| Storage | Integrated Storage (Raft) | Consul or external | Integrated Storage, unless you have run Consul in production for years |
| Secret shape | Dynamic per-consumer | Static role, rotated | §6.9's lease arithmetic against the value of per-consumer isolation |
| K8s delivery | VSO | Agent Injector | Whether a native K8s Secret is acceptable, and whether your app can reload without a restart |
| Token type | Service | Batch | Whether you need revocation before expiry. If not, batch is free |
| Edition | Community | Enterprise | Namespaces, replication, HSM, **lease-count quotas**. The last one is the sleeper |

### 8.2 The cost shape — and why per-call pricing is the real difference

The honest comparison is not licence cost, it is **cost shape**. A self-hosted Vault costs roughly a fixed amount: three or five nodes, some storage, and the engineer-time to operate it. A cloud-native manager costs per secret **and per API call**, which means its bill is a function of your client behaviour.

Take AWS Secrets Manager's published pricing — **$0.40 per secret per month** and **$0.05 per 10,000 API calls** — and a large but not absurd deployment: 5,000 secrets, and an application fleet making 50,000 calls per second.

```
  storage:   5,000 secrets  x  $0.40 / month                   =       $2,000 / month

  API calls: 50,000 calls/s x 86,400 s/day x 30 days
                                                = 129,600,000,000 calls / month
             129.6e9 / 10,000  x  $0.05                        =     $648,000 / month
                                                                 --------------------
  total                                                              $650,000 / month
```

**$648,000 a month in API charges alone**, and the storage line — the one everybody models — is 0.3% of it.

Now the honest counter, in the same paragraph, because quoting the number without it is dishonest: **nobody should be calling a secrets API fifty thousand times a second.** That is not a workload, it is a missing client-side cache — an application fetching a credential per request instead of per process, or a pod restarting in a crash loop. The real lesson is not "Secrets Manager is expensive"; it is that **per-call pricing converts a caching bug into a six-figure invoice, while self-hosted Vault converts the same bug into a latency graph and a rate-limit quota**. One of those you find in the postmortem; the other you find on the bill, a month later.

Which cuts both ways, and should. Vault's fixed cost is not small — three nodes, an on-call rotation, an unseal ceremony, a DR drill, and a person who understands §6.9 — and for a team with 200 secrets, one cloud and no dynamic-credential requirement, the managed service is straightforwardly correct and the arithmetic above never happens.

### 8.3 Vault versus the cloud-native managers

| | Vault / OpenBao | AWS Secrets Manager | GCP Secret Manager | Azure Key Vault |
|---|---|---|---|---|
| Dynamic credentials | **Yes** — DB, cloud, PKI, SSH, messaging | No (rotation Lambda) | No | No |
| Encryption as a service | **Yes** (transit) | No | No | Partial (keys) |
| Private CA | **Yes** (pki) | Separate product | Separate product | Yes (certs) |
| Multi-cloud / on-prem | **Yes**, one policy language | No | No | No |
| Cost shape | Fixed: nodes + ops | Per secret **and per call** | Per secret + per access | Per operation |
| You operate | Seal, HA, storage, upgrades, DR | Nothing | Nothing | Nothing |
| Licence | BUSL 1.1 / MPL 2.0 | Proprietary service | Proprietary service | Proprietary service |
| Availability blast radius | Yours to design | The cloud's | The cloud's | The cloud's |

The line that decides it in practice: **if you cannot name a dynamic secrets engine you will actually use, the managed service wins.** The rest of the table is a rounding error next to the operational cost of running a stateful, sealed, quorum-based system that everything else depends on.

### 8.4 Vault versus OpenBao

| Axis | Vault `[2.0]` | OpenBao `[OpenBao 2.6]` | Weight |
|---|---|---|---|
| Licence | BUSL 1.1, source-available, IBM Licensor | MPL 2.0, OSI-approved | **Decisive** where policy requires OSI |
| Governance | Single vendor, now IBM | Linux Foundation, public TSC | Matters over a 5-year horizon |
| API compatibility | — | Compatible at the fork point, diverging since | Low risk for core paths |
| Enterprise features | Namespaces, replication, HSM, Seal HA, lease-count quotas, performance standbys | **None** | **Decisive** if you use any |
| Ecosystem | Every integration targets it first | Growing; most Vault clients work | Moderate |
| Operational surface | Stable | `stored_shares` removed, non-root container, KMS seals deprecated for 2.7.0, `file` storage deprecated | **The actual migration cost** |
| Commercial support | HashiCorp/IBM | Vendors around the foundation | Depends on procurement |

**Choose Vault** when you use or will use Enterprise features, when commercial support from a single vendor is a procurement requirement, or when you simply want the path every integration is tested against. **Choose OpenBao** when an OSI-approved licence is a hard requirement, when single-vendor governance is a risk you are explicitly managing, or when you are starting fresh and the community edition covers you. **Do not choose OpenBao to save money** — Vault's community edition is free too, and the migration cost is in §6.21's operational surface, not in the API.

### 8.5 Delivery-mechanism tradeoffs

```mermaid
quadrantChart
    title Kubernetes delivery mechanisms
    x-axis "Low operational weight" --> "High operational weight"
    y-axis "Static values only" --> "Full dynamic lease handling"
    quadrant-1 "Powerful and heavy"
    quadrant-2 "Powerful and light"
    quadrant-3 "Simple and limited"
    quadrant-4 "Limited and heavy"
    "CSI provider": [0.22, 0.16]
    "Agent Injector": [0.72, 0.86]
    "Vault Secrets Operator": [0.36, 0.82]
    "Direct API client": [0.62, 0.94]
```

Read the plot as the recommendation it is: **VSO occupies the useful corner** — full lease handling at moderate operational weight — which is why it is the default choice for new Kubernetes deployments. The Agent Injector sits high on both axes because a sidecar in every pod is a real, recurring cost. The CSI provider is genuinely light and genuinely limited. Direct API is the most capable and the most expensive, because you write and maintain it in every language you ship.

| | Injector | CSI | VSO | Direct API |
|---|---|---|---|---|
| Sidecar per pod | **Yes** | No | No | No |
| Creates a K8s Secret | No | Optional | **Yes** | No |
| Renews leases | Yes | **No** | Yes | If you wrote it |
| Restarts app on change | Via `command` | No | **Yes** (`rolloutRestartTargets`) | You decide |
| App code change | None | None | None | **Required** |
| Non-Kubernetes workloads | Agent works standalone | No | No | Yes |

### 8.6 Storage and seal tradeoffs

| Choice | Buys | Costs |
|---|---|---|
| Integrated Storage | One system to operate, one snapshot command, native HA | Vault nodes become stateful; disk sizing and Raft latency are now your problem |
| Consul storage | Reuses an existing Consul investment | Two distributed systems, two failure modes, two upgrade schedules |
| Shamir seal | No external dependency; highest assurance | **No unattended restart.** A node reboot at 3am is a human page |
| Auto-unseal (KMS) | Unattended restart, autoscaling, immutable nodes | The KMS key is now the single most critical object you own (§6.2) |
| HSM seal | Regulatory compliance, hardware key custody | Enterprise licence, hardware, and an appliance in the availability path |

---

## 9. When to Use / When NOT to Use

### Use Vault (or OpenBao) when

- You need **dynamic credentials** — database, cloud, SSH, messaging. This is the reason the product exists and no managed alternative offers it.
- You need an **internal PKI** issuing short-lived certificates at machine speed.
- You need **encryption as a service** so applications never hold key material.
- You are **multi-cloud or hybrid**, and a single policy language, identity model and audit trail across all of it is worth real money.
- You need **fine-grained, path-scoped policy with templating** across many teams in one system.
- Your organisation's licence policy permits BUSL — or requires OSI, in which case OpenBao.
- You have, or will hire, someone who owns it. Vault is not a service you install and forget.

### Do NOT use Vault when

- **All you need is a KV store.** A single-cloud team with 200 static secrets should use their cloud's manager and spend the saved effort elsewhere. This is the most common wrong adoption.
- **You cannot staff its operation.** Seal ceremony, HA, upgrades, snapshot restores and DR drills are a standing commitment. A Vault nobody has upgraded in two years is worse than no Vault.
- **You are putting non-secret configuration in it.** Log levels and feature flags in Vault buy an availability dependency and an audit flood.
- **You cannot tolerate it on the critical path**, and are not willing to design around that with caching, generous TTLs and renewal-ahead-of-expiry (§6.19).
- **You want it to be a password manager for humans.** It can do that, badly. A password manager does it well.

### The decision table

| Situation | Answer |
|---|---|
| Single cloud, static secrets, small team | That cloud's secrets manager |
| Need per-consumer database credentials | **Vault**, database engine |
| Need an internal CA at fleet scale | **Vault**, PKI engine, `no_store=true` |
| Application must not hold a key | **Vault**, transit engine |
| Multi-cloud plus on-premises | **Vault**, one broker |
| Licence policy requires OSI-approved | **OpenBao** |
| Need namespaces or replication | **Vault Enterprise** — OpenBao has neither |
| GitOps, want ciphertext in Git | Sealed Secrets or SOPS — see [`devops/secrets_management`](../../devops/secrets_management/secrets_management.md) |
| Want K8s Secrets synced from a backend | External Secrets Operator, or VSO if the backend is Vault |
| 5,000 secrets and a very chatty client | Fix the client first. Then read §8.2 |

---

## 10. Common Pitfalls (Production War Stories)

1. **The policy that grants nothing.** A team writes `path "secret/app/db"`, tests with a root token, ships, and every pod gets `permission denied`. The KV v2 API path is `secret/data/app/db` and the CLI hides the segment. **Fix:** write policies against API paths and verify with `vault token capabilities <token> secret/data/app/db` before shipping. See §6.4.
2. **The five-minute TTL that doubled the load and changed nothing.** Security asks for shorter credential lifetimes, an engineer changes `default_ttl` from 1h to 5m, and lease count stays at 4,000 while issuance and revocation go from 1.1/s to 13.3/s each — 27 privileged database operations per second, throttled through a pool of 4. **Fix:** run §6.9's arithmetic first; share leases in-process, use batch tokens or static roles.
3. **Vault down because a log collector was down.** A `socket` audit device pointed at a collector that was rolled. It was the only enabled device. Vault refused every request, returning 500s while `sys/health` reported healthy and unsealed — so every dashboard said Vault was fine. **Fix:** always enable two audit devices on independent failure domains. See §6.18.
4. **The KMS key that a cleanup policy deleted.** An automated "remove unused KMS keys" rule scheduled the auto-unseal key for deletion. Nobody objected, because the key had no CloudTrail reads outside Vault's own start-ups. Three weeks later a node restarted and could not unseal — and neither could the DR cluster, nor any snapshot. **Fix:** deletion protection, an explicit key policy, multi-region replication, and the key ARN in the DR runbook. See §6.2.
5. **Everyone in the GitHub org could deploy to production.** The repository condition was written in `claim_mappings` instead of `bound_claims`, so it was copied into token metadata and enforced by nothing. Discovered by an engineer who ran the deploy workflow from a fork. **Fix:** authorisation goes in `bound_claims`; set `bound_audiences`; audit every JWT role for the same mistake. See §6.13.
6. **"FATAL: sorry, too many clients already."** Dynamic credentials rolled out to 200 pods with a 1-hour TTL. At each hour boundary two generations of connection pools overlapped, PostgreSQL's `max_connections` of 100 was overwhelmed, and the outage was blamed on Vault. **Fix:** PgBouncer, a pool that closes on rotation, and a TTL matched to real connection lifetime. See §6.5.
7. **The nightly failure with no deploy behind it.** Vault Agent re-rendered `/run/secrets/db.env` correctly every hour. The application read it once at start-up. It failed at whatever hour it next reconnected, never during business hours, never reproducibly. **Fix:** `command` in the template stanza, or VSO's `rolloutRestartTargets`. See §6.19.
8. **Rekey when they meant rotate.** A compliance control said "rotate encryption keys quarterly". An operator ran `vault operator rekey`, which regenerated the unseal shares — invalidating five key-holders' shares mid-quarter — and did not touch an encryption key. **Fix:** know which layer each command operates on (§5.1, §6.23); `rotate` needs no quorum and takes milliseconds.
9. **The AppRole `secret_id` in the AMI.** A long-lived, reusable `secret_id` baked into a machine image, valid indefinitely, present on every instance ever launched from it, and recoverable from any snapshot. AppRole had reproduced the exact problem it exists to solve. **Fix:** response-wrapped, single-use, short-TTL, CIDR-bound `secret_id` delivered by a trusted orchestrator. See §6.14.
10. **Break-glass required a token they did not have.** After upgrading to `[2.0]`, the documented emergency procedure — generate a root token from a quorum of recovery keys — failed, because `sys/generate-root` is now **authenticated**. It was discovered during an incident. **Fix:** re-write and rehearse break-glass runbooks *before* the 2.0 upgrade. See §6.23.
11. **The zombie roles.** A naive `DROP ROLE "{{name}}"` revocation statement failed for every dynamic user that had created a temporary table. Vault retried forever, the lease table grew without bound, and the database accumulated tens of thousands of undead roles. **Fix:** `REASSIGN OWNED` / `DROP OWNED` before `DROP ROLE`, and alert on revocation errors. See §6.5.
12. **The root token in the CI variable.** The initial root token from `vault operator init`, three years old, in a pipeline variable, used by everything, revocable by nobody because nobody knew what would break. **Fix:** revoke the initial root token as the last step of bootstrap; generate one with a quorum when genuinely needed and revoke it after.
13. **The snapshot that had never been restored.** Nightly snapshots for two years, monitored, alerted, green. During a real recovery they discovered the snapshots were of the wrong cluster — a copy-paste in the cron job — and nobody had ever restored one. **Fix:** restore into a scratch cluster on a schedule; a snapshot you have never restored is a hypothesis.
14. **Certificates for everything, stored forever.** A PKI mount issuing 24-hour certificates to 5,000 pods with default settings wrote every certificate into storage. Barrier growth of several gigabytes a month and a CRL too large to fetch. **Fix:** `no_store=true` for short-lived leaves, `generate_lease=false`, and a scheduled `tidy`. See §6.7.

---

## 11. Technologies & Tools

### 11.1 Vault itself and its fork

- **HashiCorp Vault** — the identity-brokered secrets platform this page covers, at 2.0.3 under BUSL 1.1 with IBM recorded as Licensor since the February 2026 acquisition close.
- **OpenBao** — the Linux Foundation, MPL 2.0 fork taken at the BUSL change, at 2.6.1, API-compatible at the fork point and diverging on operational surface rather than on paths.

The Vault CLI, the HTTP API and the client libraries are the three surfaces you actually touch; Integrated Storage is the storage backend to choose, and Vault Enterprise is where namespaces, replication, HSM seals and lease-count quotas live.

### 11.2 Delivering secrets into Kubernetes

- **Vault Secrets Operator** — a cluster controller with `VaultAuth`, `VaultStaticSecret`, `VaultDynamicSecret` and `VaultPKISecret` CRDs that writes native Kubernetes Secrets and can restart the consuming Deployment on change.
- **Vault Agent Injector** — the mutating admission webhook that adds an init container and a renewing sidecar to any annotated pod, rendering secrets to files under `/vault/secrets`.
- **Vault Agent** — the client-side daemon behind that injector, also useful standalone on VMs for auto-auth, caching and templating.
- **Secrets Store CSI Driver** — the vendor-neutral volume mount, driven for Vault by the Vault CSI Provider; the lightest option and the one that does not renew a lease.
- **External Secrets Operator** — the multi-backend alternative that syncs from Vault or a cloud manager into native Secrets, at 2.8.0 serving `external-secrets.io/v1`.

### 11.3 Seals, storage and the trust anchors underneath

- **Seal backends:** **AWS KMS**, **GCP Cloud KMS**, **Azure Key Vault** — the three cloud key managers that wrap the root key for auto-unseal, plus a PKCS#11 HSM or AWS CloudHSM where hardware custody is required.
- **Consul** — the original storage backend and still supported, now second choice to Integrated Storage for every new deployment.
- **etcd** — a legacy storage option, and the place a Kubernetes Secret written by VSO or ESO actually lands, which is why encryption at rest matters there.

### 11.4 What Vault issues credentials into

- **Databases:** **PostgreSQL**, **MySQL**, **MongoDB**, **Cassandra**, **Elasticsearch**, **Snowflake**, **Redis** — each with a database-engine plugin that runs your creation and revocation statements.
- **Message brokers:** **RabbitMQ** — dynamic users and permissions with the same lease model.
- **Schedulers and platforms:** **Kubernetes**, **Nomad** — Vault mints service-account tokens and Nomad tokens the same way it mints database users.
- **Certificate consumers:** **cert-manager** — the Kubernetes issuer that drives the PKI engine on a renewal schedule so nothing hand-rolls certificate lifecycle.

### 11.5 Alternatives you will be asked to compare against

- **Cloud-native managers:** **AWS Secrets Manager**, **GCP Secret Manager**, **Azure Key Vault**, **AWS SSM Parameter Store** — managed, single-cloud, no dynamic credentials, priced per secret and per call.
- **Encrypt-then-commit:** **Sealed Secrets**, **SOPS** — a different answer entirely, keeping ciphertext in Git rather than brokering identity; owned by [`devops/secrets_management`](../../devops/secrets_management/secrets_management.md).

### 11.6 Operating it

- **Provisioning:** **Terraform**, **Ansible**, **Helm** — the Vault Terraform provider is how policies, roles and mounts should be managed, and the Helm chart is how the cluster gets onto Kubernetes.
- **Observability:** **Prometheus**, **Grafana**, **OpenTelemetry**, **Splunk** — scrape `sys/metrics?format=prometheus` and put `vault.expire.num_leases`, `vault.core.handle_request` p99 and Raft commit latency on the dashboard before the first incident.
- **Delivery:** **ArgoCD** — the GitOps controller that applies VSO or ESO custom resources; it holds references, never values.
- **Adjacent security tooling:** **gitleaks**, **Trivy**, **OpenSSL**, **Keycloak**, **Okta** — leak detection, image scanning, certificate inspection, and the identity providers behind OIDC auth. Workload identity frameworks such as SPIFFE and its SPIRE implementation are the other common source of the short-lived identity Vault consumes.

### 11.7 What this module deliberately does not own

The *discipline* of secrets management — the four questions, the delivery patterns as patterns, `kubeseal` and `sops` usage, secret scanning, secrets in Terraform state, the exposure-window arithmetic, and IRSA/EKS Pod Identity as the general secret-zero answer — belongs to [`devops/secrets_management`](../../devops/secrets_management/secrets_management.md). This module owns the inside of Vault.

Related reading: [secrets management](../../devops/secrets_management/secrets_management.md), [Kubernetes security](../../devops/kubernetes_security/kubernetes_security.md), [GitOps with Argo CD and Flux](../../devops/gitops_argocd_flux/gitops_argocd_flux.md), [infrastructure as code with Terraform](../../devops/infrastructure_as_code_terraform/infrastructure_as_code_terraform.md), [configuration management](../../devops/configuration_management/configuration_management.md), [DevSecOps and supply chain security](../../devops/devsecops_and_supply_chain_security/devsecops_and_supply_chain_security.md), [auth and authorization systems](../../backend/auth_and_authorization_systems/auth_and_authorization_systems.md), [backend security and OWASP](../../backend/backend_security_owasp/backend_security_owasp.md), [Envoy](../envoy_proxy/envoy_proxy.md) for the SDS side of certificate delivery.

---

## 12. Interview Questions with Answers

**Q: Your Vault policy grants read on `secret/app/db` and the application still gets permission denied — why?**
**Short:** On a KV v2 mount the real API path is `secret/data/app/db`, so a policy written against the CLI path grants nothing at all.
KV version 2 inserts a `data/` segment into every read and write, and a separate `metadata/` segment for listing and version history — but the `vault kv` CLI hides both, so the path you type and the path the API receives are different strings. A policy on `secret/app/db` therefore matches no request any client ever makes. The fix is to write policies against API paths: `secret/data/app/*` for values and `secret/metadata/app/*` for `list` and version history, deliberately withholding `secret/destroy/*` and `secret/delete/*` from applications. Verify before shipping with `vault token capabilities <token> secret/data/app/db`, which reports the effective capabilities on the real path — and never test with a root token, because a root token makes every path work.

**Q: Security asks you to cut the dynamic database credential TTL from one hour to five minutes. What actually happens to Vault's load?**
**Short:** Lease count stays the same because steady-state leases equal the number of clients regardless of TTL, while issuance and revocation rates each go up twelvefold.
Steady-state active leases are `issuance_rate x TTL`, and a well-behaved client re-fetches on expiry, so `issuance_rate = N_clients / TTL`. Substituting, active leases equal `N_clients` — **independent of the TTL**. At 4,000 pods with a one-hour TTL you have roughly 4,000 leases and 1.1 issuances per second; at five minutes you still have roughly 4,000 leases and 13.3 issuances *and* 13.3 revocations per second, each revocation being a real `DROP ROLE` against the database plus a quorum write to Raft. So the change buys a shorter exposure window at twelvefold the operational cost and no reduction in the number that actually limits Vault. The levers that do reduce lease count are sharing one credential per process, using batch tokens where revocation is not needed, using static roles where per-consumer isolation does not matter, and a lease-count quota to cap a runaway client.

**Q: Vault is unsealed, healthy and returning 500 to every request. What is the first thing you check?**
**Short:** Whether every audit device is failing — Vault refuses any request it cannot write to at least one enabled audit device.
Vault guarantees that a request is recorded before it is served, so it sends to *all* enabled audit devices and requires at least one success. If every device is unavailable — a full disk on a `file` device, an unreachable collector on a `socket` device, a wedged syslog — Vault deliberately refuses the request rather than serving it unrecorded. The confusing part is that `sys/health` still reports healthy and unsealed, so dashboards say the cluster is fine while every client sees 500s. The permanent fix is to enable at least two audit devices on independent failure domains, which turns a total outage into an alert, and to make sure the log destination never authenticates using a Vault-issued credential, because that loop makes a hiccup permanent.

**Q: You are using auto-unseal with AWS KMS. Someone deletes the KMS key. What is your recovery path?**
**Short:** There is none — recovery keys cannot decrypt the root key, so every node, snapshot and DR copy is permanently unreadable ciphertext.
Under auto-unseal the KMS key wraps the root key directly, and the recovery keys that `vault operator init` printed are a different object entirely: they authorise privileged operations such as generating a root token, rekeying recovery shares and promoting a DR secondary, but they cannot open the barrier. Since the barrier encrypts everything in the storage backend, losing the KMS key makes every Raft snapshot and every offsite backup noise. The controls are preventive only: deletion protection and an explicit key policy that no cleanup automation can override, multi-region key replication, the key ARN recorded in the DR runbook next to the snapshot location, and on Enterprise, Seal HA so one KMS is not the single point of failure. Treat the seal mechanism as more critical than the data, because it is.

**Q: A JWT auth role sets `claim_mappings` to pin the repository and ref. What is wrong with that?**
**Short:** `claim_mappings` only copies claims into token metadata and enforces nothing, so the security condition belongs in `bound_claims` instead.
`bound_claims` is an authorisation filter: login **fails** unless the presented token's claims match it. `claim_mappings` merely copies named claims into the resulting token's metadata so they appear in audit logs and can be referenced in policy templating — it restricts nobody. Putting your repository and branch condition in `claim_mappings` therefore authorises every repository in the organisation to assume the role, and the configuration reads as correct in review because the two field names look like siblings. Alongside the fix, always set `bound_audiences` (or any token from that issuer is acceptable) and check `bound_claims_type`, since `glob` mode is where an over-broad `*` hides.

**Q: What licence is Vault under, who owns it now, and what does that actually prevent you from doing?**
**Short:** BUSL 1.1 with IBM as Licensor since the February 2026 acquisition — source-available, so self-hosting is fine and selling managed Vault is not.
Vault moved from MPL 2.0 to the Business Source License 1.1 in August 2023 and remains there; IBM closed its $6.4 billion acquisition of HashiCorp on 27 February 2026, so the Licensor line now names IBM and the brand is "IBM Vault (formerly HashiCorp Vault)". BUSL is source-available rather than open source: you may read, modify and self-host it, you may not offer a competing hosted service, and each release converts to MPL 2.0 four years after its Change Date. In practice the licence blocks essentially nobody's internal use — the real constraint is organisational policy requiring an OSI-approved licence, which is the usual reason a team lands on OpenBao, the Linux Foundation's MPL 2.0 fork. Note what did not change with the acquisition: the repository is still `hashicorp/vault`, the Go module path, docs domain, Helm chart and Terraform provider all still say hashicorp.

**Q: You roll out dynamic database credentials to 200 pods and PostgreSQL starts refusing connections. What happened?**
**Short:** At each TTL boundary two generations of connection pools overlap, and dropping a PostgreSQL role does not terminate its existing sessions.
Each pod fetches a credential and opens a connection pool with it. When the lease expires it fetches a new credential and opens a *new* pool, but the old pool's sockets may still be open — and in PostgreSQL a `DROP ROLE` does not kill that role's active sessions. So 200 pods with a pool of 10 sit at 2,000 connections in steady state and spike toward 4,000 at every TTL boundary, against a `max_connections` default of 100. The fixes are all unglamorous: put PgBouncer or an equivalent proxy in front so pooling is centralised, make the application close its pool cleanly on credential rotation, match `default_ttl` to the real connection lifetime rather than to a security aspiration, and remember that Vault's own `max_open_connections` to the database defaults to 4, so a revocation storm queues four at a time.

**Q: Vault Agent renders a fresh credential into a file every hour, and the application still fails at 3am. Why?**
**Short:** Re-rendering a file does not restart the process — an app that read the file once at startup keeps using the revoked credential until it reconnects.
The agent's templating engine renews the lease and rewrites the destination file correctly, but nothing signals the running process. An application that read `/run/secrets/db.env` at start-up and built a connection pool from it carries on with a credential Vault has already revoked, and fails whenever it next reconnects — which is why the failure has no deploy behind it and never reproduces during business hours. The fix inside the agent is the `command` field on the template stanza: reload the service, send a signal, or touch a file the app watches. On Kubernetes, the Vault Secrets Operator's `rolloutRestartTargets` solves it more bluntly by restarting the Deployment when the value changes, which is the right answer for any application that cannot reload configuration in place.

**Q: A compliance control says "rotate encryption keys quarterly". Do you run `vault operator rekey` or `vault operator rotate`?**
**Short:** `rotate` — it adds a new keyring term for future writes, while `rekey` changes who can unseal and touches no encryption key.
They operate at different layers of the key hierarchy. `vault operator rotate` adds a term to the keyring, so new writes use the new encryption key while every previous term is retained to decrypt existing data; it needs no quorum, takes milliseconds and belongs on a schedule. `vault operator rekey` regenerates the unseal key and re-splits it into new Shamir shares, which requires a quorum of the *current* shares, invalidates every existing share, and does not touch an encryption key at all — it is what you run when a key-holder leaves or a share is compromised. The mnemonic is that rekey changes the lock on the front door and rotate changes the ink you write tomorrow's secrets in. Neither re-encrypts existing data; old values stay under old terms until they are rewritten.

**Q: How do you deliver an AppRole `secret_id` to a workload without recreating the secret-zero problem?**
**Short:** Have a trusted orchestrator request it response-wrapped, so the workload receives a single-use wrapping token instead of the credential itself.
AppRole splits credentials into a semi-public `role_id` that can live in config or an image, and a `secret_id` that is the real credential — and baking a long-lived reusable `secret_id` into a machine image is exactly the problem AppRole exists to solve. The safe pattern is the trusted orchestrator: a component that already has a platform identity requests the `secret_id` with `-wrap-ttl`, receives a wrapping token, and delivers that token to the workload, which unwraps it once. Because a wrapping token can be unwrapped exactly once, interception makes the legitimate unwrap fail loudly rather than succeeding silently, so you get detection rather than a quiet compromise. Combine it with `secret_id_num_uses=1`, a `secret_id_ttl` measured in minutes, and CIDR binding on both the `secret_id` and the resulting token — and use AppRole only where no platform identity exists at all.

**Q: What changed about `sys/rekey` and `sys/generate-root` in Vault 2.0, and why does it matter operationally?**
**Short:** Both are now authenticated endpoints, so break-glass procedures that assumed no token was needed fail at exactly the moment you have no token.
In `[2.0]` the `sys/rekey` endpoints, `sys/generate-root` and the DR operation-token endpoint all became **authenticated**, where previously they were guarded only by the unseal-key quorum. The security reasoning is sound — an unauthenticated rekey endpoint is a denial-of-service surface — but it inverts the emergency assumption: the documented recovery from "we have lost every token" was to generate a root token from a quorum of recovery keys, and that path now requires a token. Teams discover this during the incident rather than before it. The response is to re-write and rehearse break-glass and DR-promotion runbooks *before* upgrading to 2.0, and to make sure at least one durable, tightly-scoped operational credential exists that the procedure can use.

**Q: A token has one policy granting read on `secret/data/app/*` and another denying `secret/data/app/prod/*`. What can it read?**
**Short:** Everything under `secret/data/app/` except the prod subtree — `deny` overrides every grant, and the most specific path wins regardless of policy order.
Vault's policy evaluation is not the additive union people expect. Path matching picks the **most specific** rule first: an exact path beats a `+` single-segment wildcard, which beats a `*` trailing glob, and the order of stanzas or of policies on the token is irrelevant. On top of that, a `deny` capability anywhere overrides every grant everywhere, unconditionally, so there is no "the more permissive policy wins". The one place Vault genuinely does accumulate is identity: a token's own policies plus its entity's policies plus every group the entity belongs to are combined — and `deny` still beats all of it. When in doubt, ask Vault rather than reasoning about it, with `vault token capabilities`.

**Q: Walk the key hierarchy. What does unsealing actually do?**
**Short:** It reconstructs the unseal key, which decrypts the root key, which decrypts the keyring, whose encryption keys decrypt every byte in storage.
There are four nested layers. The **unseal key** is never written to disk; under Shamir it is split into *n* shares with threshold *t*, and under auto-unseal it is replaced by a KMS or HSM that wraps the layer below. It decrypts the **root key**, which is stored encrypted in the storage backend and is what `vault operator rekey` protects differently. The root key decrypts the **keyring**, a set of encryption keys identified by term, where the newest term encrypts new writes and every older term is retained so old data stays readable — this is what `vault operator rotate` extends. Those encryption keys, AES-256-GCM, encrypt **every byte** Vault writes: secrets, policies, leases, tokens, identity, even the audit-device configuration. That is why a stolen snapshot or a compromised storage bucket is noise, and why losing the seal mechanism is unrecoverable.

**Q: What changes when you move from Shamir unseal to auto-unseal?**
**Short:** Vault can restart unattended because a KMS or HSM holds the wrapping key, and the printed keys become recovery keys that cannot unseal anything.
With Shamir, a restarting node is sealed until *t* humans each run `vault operator unseal`, which is unacceptable for autoscaling, immutable infrastructure, or a node reboot at 3am. Auto-unseal names a `seal` stanza — `awskms`, `gcpckms`, `azurekeyvault`, `pkcs11` or a `transit` seal backed by another Vault — and the external key manager decrypts the root key at start-up with no human present. The trade is that the key manager becomes the single most critical object you own, and the keys `vault operator init` prints are now **recovery keys**: they authorise generating a root token, rekeying recovery shares and promoting a DR secondary, and they cannot open the barrier. Seal migration between mechanisms is supported and rehearsable, and Enterprise Seal HA allows several seals so one KMS outage does not seal the cluster.

**Q: When would you use a batch token instead of a service token?**
**Short:** For short-lived work that never needs revoking before expiry — batch tokens are not persisted, so issuing millions costs Vault nothing at rest.
A service token is written to storage with a lease, can be renewed, can create child tokens and can be revoked at any time; that persistence is exactly what makes token count an operational limit. A batch token is an encrypted, self-describing blob carrying its own policies, prefixed `hvb.` and never stored, so it costs no storage write on issue and no revocation work on expiry. The price is that it cannot be renewed, cannot create children, has no cubbyhole, and **cannot be revoked before it expires**. That makes it ideal for a CI job, a serverless invocation or any burst of short-lived clients, and wrong for a long-running server you may need to cut off. Setting `token_type=default-batch` on a high-volume auth mount is one of the few single-line changes that meaningfully reduces Vault's storage pressure.

**Q: How does Kubernetes auth prove that a request really came from a particular pod?**
**Short:** The pod presents its projected service-account JWT and Vault validates it with a TokenReview against the Kubernetes API, then matches the bound name and namespace.
The pod reads its kubelet-issued service-account token from `/var/run/secrets/kubernetes.io/serviceaccount/token` and posts it to `auth/kubernetes/login` with a role name. Vault calls `TokenReview` on the Kubernetes API, which returns the authenticated namespace, service-account name and audience, and Vault then checks them against the role's `bound_service_account_names`, `bound_service_account_namespaces` and `audience` before issuing a token. No shared secret is distributed at any point, which is the whole appeal. Three things break it in practice: an audience mismatch between the pod's token projection and the role, a static `token_reviewer_jwt` that silently expires after ninety days because bound service-account tokens are short-lived — prefer `disable_local_ca_jwt=false` so Vault uses its own kubelet-refreshed token — and running Vault outside the cluster, where a local token is unavailable and one long-lived reviewer credential becomes a real architectural cost.

**Q: What is response wrapping, and what attack does it actually detect?**
**Short:** Vault stores the response in a new single-use token's cubbyhole and returns that token, so an intercepted secret makes the legitimate unwrap fail loudly.
Passing `-wrap-ttl` to any Vault command returns a wrapping token instead of the response; the real payload sits in that token's private cubbyhole and is retrieved with `vault unwrap`. The security property is single use: if an attacker intercepts the wrapping token and unwraps it, the intended consumer's unwrap fails with "wrapping token is not valid", converting an otherwise undetectable interception into a loud, immediate alert. Three companion properties matter — a short TTL so the secret is in flight for seconds, `sys/wrapping/lookup` so a consumer can verify the `creation_path` *before* unwrapping (otherwise an attacker who can hand you a token can hand you one wrapping a different response), and the fact that the intermediary carrying the token never sees the value. This is the mechanism behind safe AppRole `secret_id` delivery and the correct way to hand any secret to a human.

**Q: What is the cubbyhole engine for, given that KV already exists?**
**Short:** It is a private per-token store nothing else can read, not even a root token, and it is destroyed when the token is revoked or expires.
Every token gets its own namespace under `cubbyhole/`, isolated at the token level rather than by policy — there is no path a different token can use to read it, and a root token cannot read another token's cubbyhole either. It is also ephemeral by construction: when the token goes away, so does the data, with no lease and no cleanup job. That combination makes it useless as a general store and perfect as the substrate for response wrapping, where Vault creates a throwaway token, drops the wrapped response into its cubbyhole, and hands you the token. The occasional legitimate direct use is a bootstrap handoff where a process needs somewhere to stash a value that will vanish with it.

**Q: What problem does the transit engine solve that a KV secret does not?**
**Short:** Vault performs the encryption itself, so the application handles only ciphertext and the key never enters application memory.
Storing an encryption key in KV means the application reads it and holds it — so a heap dump, a log line or a memory-scraping exploit yields the key and every record it ever protected. The transit engine instead exposes `encrypt`, `decrypt`, `sign`, `verify`, `hmac` and `rewrap` as API calls: the key is created in Vault, never leaves it, and every operation is audited against a named identity. Key rotation becomes a single call because the version travels inside the ciphertext as a `vault:v1:` prefix, and `rewrap` upgrades stored ciphertext to the current key without ever returning the plaintext to the caller. The costs are a network round trip per operation, which makes it right for field-level PII and wrong for a hot path — use batch mode or `transit/datakey` for envelope encryption there — and convergent encryption leaks equality by construction, so enable it only as a deliberate trade.

**Q: You are issuing 72-hour certificates to 5,000 pods from Vault's PKI engine. Which two settings decide whether that survives?**
**Short:** `no_store=true` so certificates are not written into the barrier, and `generate_lease=false` so issuance does not create a lease object per certificate.
At 5,000 pods with a 72-hour lifetime you issue roughly 1,700 certificates a day. With default settings each one is written into the storage backend, which grows the barrier without bound and produces a CRL that eventually cannot be fetched, and turning on `generate_lease` would additionally create a lease per certificate and rediscover the lease-count arithmetic the hard way. `no_store=true` means the certificate cannot be revoked, which sounds alarming and is the correct modern answer: a 72-hour certificate expires faster than a CRL propagates, so short lifetimes replace revocation rather than needing it. Round it out by keeping the root CA offline with only an intermediate in Vault, scheduling `pki/tidy`, setting `leaf_not_after_behavior` explicitly, and letting cert-manager drive renewal rather than hand-rolling it.

**Q: Why is Integrated Storage the default recommendation over Consul, and what does it cost you?**
**Short:** It collapses operating Vault and operating Vault's database into one system with one snapshot command, at the price of making Vault nodes stateful.
Integrated Storage puts a Raft log and a BoltDB file on each Vault node's own disk, so there is no external cluster to run, upgrade or debug, and `vault operator raft snapshot save` produces one complete encrypted backup of everything inside the barrier. Consul storage was the historical default and remains supported, but it means two distributed systems, two failure modes and two upgrade schedules for one capability. The costs of Integrated Storage are real: Vault nodes become stateful, so disk sizing, IOPS and Raft commit latency are now yours, and every write is a quorum write — which is why lease churn is expensive. Size the cluster at 3 nodes to tolerate one failure or 5 to tolerate two, never an even number, and check `vault operator raft autopilot state` after every node replacement rather than trusting `vault status`.

**Q: What is the difference between a dynamic role and a static role in the database engine?**
**Short:** A dynamic role creates a brand-new uniquely-named user per request with a lease, while a static role adopts one existing account and rotates its password on a schedule.
Reading `database/creds/<role>` runs your `creation_statements` and returns a user that did not exist a moment ago, unique to that request, attached to a lease whose expiry triggers a real `DROP ROLE`. Reading `database/static-creds/<role>` returns the *same* username every time with whatever password Vault last set on it, and the `ttl` field is a countdown to the next rotation rather than a lease — nothing is revoked when it reaches zero, the password simply changes, and any consumer still holding the old one breaks. Static roles give up per-consumer isolation and per-consumer revocation, and in exchange they completely eliminate the lease-count problem: one account, one rotation per period, regardless of how many consumers read it. Use them where the account name appears in grants, audit rules or third-party allowlists, and `vault write -f database/rotate-role/<name>` for an immediate rotation on suspected compromise.

**Q: Compare performance replication, DR replication and performance standby nodes.**
**Short:** Performance replication serves local clients in another region, DR replication serves nobody until promoted, and a performance standby serves local reads inside one cluster.
A **performance standby** is a node within an HA cluster that, on Enterprise, serves read requests locally instead of forwarding them, which turns extra nodes into read throughput — on the community edition standbys only forward or redirect, so adding nodes buys availability and durability but never throughput. **Performance replication** is a whole second cluster with its own storage, its own clients, its own tokens and its own local mounts, forwarding only writes to the primary; it exists to cut latency and spread load across regions. **DR replication** is a byte-for-byte warm copy including tokens and leases that accepts no client traffic at all and exists purely to be promoted, which requires a DR operation token generated from a quorum of unseal or recovery key holders. All three are Enterprise, and the DR promotion ceremony is the one to rehearse, because finding three key-holders at 2am is the part that fails rather than the commands.

**Q: What is a token accessor and why does it matter?**
**Short:** It is a handle that can look up, renew and revoke a token without being able to authenticate as it, and it is what appears in audit logs.
Every service token has an accessor. Holding the accessor lets you run `vault token lookup -accessor` and `vault token revoke -accessor`, but never lets you make a request as that token — so it is safe to log, safe to store in a management system, and exactly the right thing to put in an audit trail. That is why `hmac_accessor` should be left off in audit device configuration: the accessor is not a credential, and leaving it unhashed is what lets you revoke a token you spotted misbehaving in the logs. It also pairs with the token hierarchy: a token created by another token is its child, and revoking a parent cascades to the whole subtree, which is powerful in a bootstrap chain and a footgun everywhere else — `-orphan` breaks the link deliberately, which is what auth methods do for the tokens they issue.

**Q: A client renews its lease with `-increment=1h` and the credential dies sooner than before. Explain.**
**Short:** The increment is measured from now, not added to the existing expiry, so renewing a lease with four hours left for one hour shortens it to one hour.
`vault lease renew -increment=X` means "set the remaining time to X", clamped by the role's `max_ttl` — it does not extend the existing expiry. A lease with four hours remaining, renewed with a one-hour increment, now expires in one hour, and the client learns about it at the next failed request. Renewing with no increment is almost always what was meant, since Vault then re-applies the role's default TTL. The companion trap is the ceiling: no amount of renewal pushes total lifetime past `max_ttl`, and at the ceiling the lease simply expires with no error and no warning — so an application that renews forever and never re-fetches works flawlessly until exactly `max_ttl` after start-up. Renew at roughly two-thirds of the TTL, and always implement re-fetch as well as renew.

**Q: How do you revoke every credential a role has ever issued, and what is the escape hatch when the target is unreachable?**
**Short:** `vault lease revoke -prefix <path>` revokes them all in one command, and adding `-force` makes Vault forget them without calling the target system.
Prefix revocation is the payoff for leases existing at all: `vault lease revoke -prefix database/creds/app-rw` walks every lease under that path and runs each one's revocation action, whether or not the consumers are reachable or even running. That is what makes "rotate the compromised credential" a single command rather than a coordination exercise. When the target system is down, though, revocations fail and Vault retries, and the backlog can grow until it becomes the incident; `-force` removes the leases from Vault without contacting the target, which leaves real accounts alive that you now own cleaning up manually. Know the flag exists, reach for it only when the alternative is Vault falling over, and note that `VALID UNTIL '{{expiration}}'` in the creation statement is the cheap insurance that makes the database expire the account even when Vault never gets to.

**Q: What quotas can you set on Vault, and which edition do you need for each?**
**Short:** Rate-limit quotas are in the community edition and should be set on day one; lease-count quotas — the direct control for lease growth — are Enterprise only.
`sys/quotas/rate-limit` applies to a namespace and optionally a path prefix and role, counts requests per client, returns 429 when exceeded, and supports a `block_interval` that keeps an offender blocked for a period after it trips — which is what stops a crash-looping pod hammering the login endpoint. It is available in the community edition and there is no good reason not to configure a global one plus one on any high-volume issuing path. `sys/quotas/lease-count` caps active leases under a path and is the direct answer to a runaway client, but it is **Enterprise**; on the community edition the closest equivalent is a rate-limit quota on the issuing path, which bounds the issuance rate and therefore the steady-state lease count at a given TTL. Either way, alert on `vault.expire.num_leases`, because a monotonically climbing lease count means a client that fetches and never re-uses.

**Q: What does the identity layer add on top of auth methods?**
**Short:** It unifies the several logins one principal has into a single entity with metadata and group membership, which is what makes templated policies possible.
An auth method turns an external identity into a token, but a human who logs in via Okta and again via LDAP, or a service that authenticates from two clusters, produces unrelated tokens with no shared notion of who they are. The identity store adds an **entity** (the principal, with arbitrary metadata), **aliases** (one per auth mount, attached to the entity), and **groups** that can be internal or mapped from an IdP's group claim so provider membership drives Vault policy with no Vault-side change. The practical payoff is policy templating: one policy containing `{{identity.entity.id}}` or `{{identity.groups.names}}` serves every tenant instead of one policy per team. It is also the one place Vault's policies genuinely accumulate — token plus entity plus every group — and `deny` still beats the union.

**Q: How does OpenBao differ from Vault today, and what actually breaks on migration?**
**Short:** It is API-compatible at the fork point under MPL 2.0, has no Enterprise features at all, and the migration cost is in init flags, container users and seal plugins rather than in paths.
The API, policy HCL, secrets engines and auth methods are the ones already in use, so clients, Terraform providers and agents generally point at it unchanged — which is why a migration test that only runs `vault kv get` succeeds and proves nothing. What actually breaks is operational surface: `[OpenBao 2.6]` removed `stored_shares` from `sys/init` and `sys/rekey/init` so older bootstrap automation now errors, the container runs as the unprivileged `openbao` user rather than root so root-owned host mounts fail, the built-in `awskms`, `azurekeyvault`, `gcpckms` and `pkcs11` seals are deprecated for removal in 2.7.0, and `file` storage is deprecated. Above all, there are **no Enterprise features** — no namespaces, replication, HSM seals, Seal HA, performance standbys or lease-count quotas — so if your design leans on any of those, this is a redesign rather than a drop-in.

**Q: When should a team choose a cloud-native secrets manager over Vault?**
**Short:** Whenever they cannot name a dynamic secrets engine, a PKI need or a transit need they will actually use — a KV store does not justify Vault's operational cost.
Vault's fixed cost is three or five stateful nodes, a seal ceremony, an HA and upgrade story, snapshot restores, DR drills, and an engineer who understands the lease arithmetic. That cost is repaid by dynamic credentials, an internal CA, encryption as a service, and one policy language across multiple clouds — and it is repaid by nothing if the deployment is a key-value store with 200 static secrets in one cloud. The honest test is to name the engine: if the answer is "we would use `kv`", the managed service is correct and the saved effort is worth more elsewhere. The strongest genuine case for Vault is heterogeneity — VMs, bare metal, CI runners and three clouds under one broker — which is why the cloud-native option so often wins in small single-cloud teams and loses in large enterprises.

**Q: Model the cost difference between self-hosted Vault and a per-call managed secrets service at high volume.**
**Short:** At AWS Secrets Manager's $0.05 per 10,000 calls, 50,000 calls per second is about $648,000 a month in API charges, dwarfing the $2,000 storage line.
Take 5,000 secrets at $0.40 per secret per month — that is $2,000 — and a fleet making 50,000 calls per second: 50,000 x 86,400 x 30 is 129.6 billion calls a month, which at $0.05 per 10,000 is **$648,000 a month**, so the storage line everyone models is 0.3% of the bill. The honest counter belongs in the same breath: nobody should call a secrets API fifty thousand times a second, and that number is a missing client-side cache — a credential fetched per request instead of per process, or a pod in a crash loop. The real lesson is about cost *shape*, not price: per-call pricing turns a caching bug into a six-figure invoice discovered a month later, while self-hosted Vault turns the same bug into a latency graph and a rate-limit quota you find in the postmortem. For 200 secrets and one cloud, the managed service is simply correct.

**Q: Vault is now on the critical path for every deployment. What breaks when it goes down, and how do you design for it?**
**Short:** Running workloads keep working because credentials are already in memory, but nothing new can start and nothing can renew a lease past its expiry.
The failure is asymmetric and that asymmetry is the design lever. A process holding a valid credential keeps using it, so an outage shorter than your TTLs is largely invisible; what stops is start-up — new pods cannot fetch, autoscaling stalls, deploys fail, and anything whose lease expires during the outage cannot renew. So the mitigations are all about widening that window: TTLs generous enough to cover a realistic outage rather than aspirationally short, renewal at two-thirds of the lease rather than at the last moment, Vault Agent or Proxy caching on each host so a Vault blip is absorbed locally, and `VALID UNTIL` in creation statements so the database enforces expiry even when Vault cannot. Then make the outage rarer: 3 or 5 Raft nodes across failure domains, auto-unseal so a restart needs no human, two audit devices, and a rehearsed DR promotion. Finally, be honest in the design review that this dependency exists, rather than discovering it during the first incident.

---

## 13. Best Practices

### Seal, storage and bootstrap

- **Auto-unseal in every environment that is not air-gapped**, and treat the KMS or HSM key as more critical than the data: deletion protection, an explicit key policy no automation can override, multi-region replication, and its ARN written into the DR runbook next to the snapshot location.
- **Know that recovery keys cannot unseal.** Write it on the runbook, because the printed output of `vault operator init` looks identical either way.
- **Integrated Storage, 3 or 5 nodes, never an even number.** Set `api_addr` and `cluster_addr` per node to that node's own address, never to a load balancer.
- **Revoke the initial root token as the final bootstrap step.** Generate one from a quorum when you genuinely need it, and revoke it when you are done.
- **Restore a snapshot into a scratch cluster quarterly.** A snapshot you have never restored is a hypothesis, not a backup.

### Policies and access

- **Write policies against API paths, not CLI paths**, and verify with `vault token capabilities` before shipping. The KV v2 `data/` segment is the single most common Vault bug.
- **Never test a policy with a root token.** It makes every path work and proves nothing.
- **Put authorisation conditions in `bound_claims`, never in `claim_mappings`**, and always set `bound_audiences` on a JWT role.
- **Prefer platform identity over distributed credentials**, in this order: `kubernetes`, `jwt`/`oidc`, cloud IAM, `cert`, and only then AppRole.
- **If you must use AppRole, deliver the `secret_id` response-wrapped**, single-use, short-TTL and CIDR-bound, from a trusted orchestrator.
- **Use identity templating rather than one policy per team.** One templated policy plus external groups scales; N hand-written policies do not.

### Leases and secrets engines

- **Run the lease arithmetic before you shorten a TTL.** Halving the TTL does not halve the lease count; it doubles issuance and revocation.
- **One credential per process, not per request.** This is the biggest single reduction in Vault load available to you, and it is an application change.
- **Use batch tokens where revocation before expiry is not required**, and static roles where per-consumer isolation genuinely does not matter.
- **Always `rotate-root` after configuring a database connection**, and accept the consequence: nobody can log in as that user again.
- **Write revocation statements that actually succeed** — `REASSIGN OWNED`, `DROP OWNED`, then `DROP ROLE` — and put `VALID UNTIL '{{expiration}}'` in every creation statement as insurance.
- **PKI at scale means `no_store=true`, `generate_lease=false` and a scheduled `tidy`**, with the root CA offline and only an intermediate in Vault.
- **Alert on `vault.expire.num_leases`.** A monotonic climb is a client that fetches and never re-uses, and it will find the ceiling before anyone notices.

### Audit and observability

- **Two audit devices, on independent failure domains, from day one.** Vault refuses requests it cannot log, so one device is a single point of total failure.
- **Never enable `log_raw`, and leave `hmac_accessor` off** so accessors stay usable for revocation.
- **Do not send audit logs anywhere that authenticates with a Vault-issued credential.** That loop turns a hiccup into a permanent outage.
- **Dashboard `vault.expire.num_leases`, `vault.core.handle_request` p99, `vault.token.count`, revocation errors, and Raft commit latency** before the first incident, not after.
- **Set a rate-limit quota globally and on every high-volume issuing path**, with a `block_interval` so a crash-looping client is stopped rather than merely slowed.

### Operations

- **Snapshot before every upgrade**, upgrade standbys first, then `vault operator step-down` and upgrade the active node last. Never skip a major version.
- **Rehearse break-glass and DR promotion quarterly**, including the human part — reaching key-holders is what fails, not the commands.
- **Re-write break-glass runbooks before the `[2.0]` upgrade**, since `sys/rekey`, `sys/generate-root` and the DR operation-token endpoint are now authenticated.
- **Manage mounts, roles and policies in Terraform**, so the security posture is reviewable, diffable and reproducible rather than a sequence of `vault write` commands somebody ran.
- **Design for Vault being down**: generous TTLs, renewal at two-thirds, agent-side caching, and an honest statement in the design review that new workloads cannot start without it.

---

## 14. Case Study — Replacing 3,400 static database passwords in a regulated bank

### The situation

A bank's platform team runs **410 services** across Kubernetes and a long tail of VMs, against **62 PostgreSQL and MySQL databases**. Credentials live in a mixture of Kubernetes Secrets committed years ago, Ansible variable files, and — for the VM estate — a shared password manager vault that eleven people can open. An internal audit counts **3,400 distinct static credentials** and finds that **none has been rotated in over two years**, because rotating any one of them requires coordinating a redeploy of every consumer and nobody can enumerate the consumers.

The regulator's finding gives them nine months and three requirements: every privileged credential must be **individually attributable**, **revocable within fifteen minutes**, and **rotated at least every 24 hours**. The team is four engineers.

### Why Vault, and why not a cloud manager

The estate is genuinely hybrid — EKS, on-premises VMware, and a mainframe-adjacent tier that will not move — so a single cloud's secrets manager would have covered roughly 60% of it and left the audit finding open on the rest. More decisively, **the requirement is dynamic**: "individually attributable and revocable in fifteen minutes" is not a rotation schedule, it is a per-consumer credential, and no managed service in their clouds offers one.

They also had to answer the licence question in writing. The bank's policy permits source-available software with legal review; BUSL 1.1 passed, with the note that IBM is now the Licensor. **OpenBao was evaluated and rejected** — not on capability but because the design used **namespaces** to separate the retail and markets divisions and **lease-count quotas** to bound blast radius, and neither exists outside Enterprise.

### The architecture

```
  3 Vault Enterprise clusters:
    prod-primary     (us-east, 5 Raft nodes)     <- writes
    prod-perf-sec    (eu-west, 5 Raft nodes)     <- performance replication, local reads
    prod-dr          (us-west, 5 Raft nodes)     <- DR replication, serves nobody

  Seal:      AWS KMS multi-region key, deletion protection on, replicated to all three regions
  Storage:   Integrated Storage, snapshots hourly to a bucket in a DIFFERENT account
  Audit:     file device on local disk  +  socket device to the SIEM   (two, deliberately)
  Namespaces: /retail  and  /markets   -- separate policy trees, separate operators
  Quotas:    global rate-limit 2000/s; lease-count 10,000 per database mount
```

### The migration, and the number that reshaped it

Phase 1 was an inventory: every credential, every consumer, every database. Phase 2 was the pilot — twelve services onto dynamic credentials with a **five-minute TTL**, because the regulator's language had been read as "shorter is better".

The pilot fell over in week two, and the arithmetic explains why:

```
  Planned rollout:  410 services, average 6 pods each  =  2,460 pods

  At TTL = 5m:
      steady-state leases   ~= N_clients                =  2,460
      issuance rate          = 2,460 / 300s             =  8.2 / s
      revocation rate                                   =  8.2 / s
      privileged DB ops                                 = 16.4 / s, forever
      Vault max_open_connections per DB config          =  4        <- the bottleneck

  At TTL = 8h (the value they shipped):
      steady-state leases   ~= N_clients                =  2,460    <- UNCHANGED
      issuance rate          = 2,460 / 28,800s          =  0.085 / s
      privileged DB ops                                 =  0.17 / s
```

**The lease count was identical at both TTLs.** Shortening the TTL had bought nothing on the number that constrains Vault, and had cost a hundredfold increase in privileged database operations — throttled through a pool of four, producing a revocation backlog that grew until the mount stopped issuing.

Worse, the five-minute TTL had triggered the connection-pool overlap: at every boundary, two generations of pools coexisted, and PostgreSQL's `max_connections` was hit on three databases.

The rewrite was three changes, and none of them was "shorten the TTL further":

1. **TTL raised to 8h, `max_ttl` 24h** — which still satisfies the regulator's 24-hour rotation requirement, because a credential that expires in eight hours is rotated three times a day.
2. **PgBouncer in front of every database**, so pooling is centralised and a credential change does not multiply sockets.
3. **One credential per process**, enforced by a shared internal client library rather than by review — the single biggest reduction in issuance, and the only change that required touching application code.

Revocation within fifteen minutes was met not by a short TTL at all but by `vault lease revoke -prefix`, which revokes every credential a role ever issued in one command, in seconds, whether or not the consumers are reachable. **That is the correct answer to a revocation SLA, and it is independent of the TTL** — a point the team had to make twice to the audit committee.

### The four incidents

1. **Every pod got `permission denied` on cutover day.** The policies were written as `secret/app/db` against a KV v2 mount. Root-token testing had passed. Diagnosed in eleven minutes once someone ran `vault token capabilities`; the class was then swept out of all 62 policies with a script.
2. **Vault returned 500 to everything for nine minutes.** The SIEM collector was rolled during a maintenance window, and at that point the socket device was the *only* enabled audit device — the file device had been disabled during a disk-space incident and never re-enabled. `sys/health` reported healthy throughout. The second device became a monitored invariant.
3. **The markets namespace could deploy to retail.** A `jwt` role for the CI pipeline had its repository condition in `claim_mappings` rather than `bound_claims`. Found by a routine policy review three weeks before the audit, not by a control.
4. **A break-glass rehearsal failed after the 2.0 upgrade.** The runbook's step 1 was "generate a root token from a quorum of recovery keys", and `sys/generate-root` is now authenticated `[2.0]`. The rehearsal was the reason they discovered it in a conference room rather than at 2am.

### Outcome

| Measure | Before | After |
|---|---|---|
| Static database credentials | 3,400 | **0** for services; 14 static roles for named legacy accounts |
| Time to rotate one credential | A coordinated multi-team redeploy; never attempted | Automatic, every 8 hours |
| Time to revoke a compromised credential | Unbounded — consumers unknown | **Seconds**, by `lease revoke -prefix` |
| Attribution | A shared password, 11 key-holders | One credential per process, audited to a named identity |
| Active leases | n/a | ~2,460, flat, quota-capped at 10,000 |
| Privileged DB operations from Vault | n/a | 0.17/s (was 16.4/s in the failed pilot) |
| Audit devices | n/a | 2, on independent failure domains |

The sentence the team put at the top of their own runbook is the one this module exists for: **"Shortening the TTL is almost never the answer. Work out what the lease count actually is first, and use prefix revocation for the revocation requirement."** They had spent six weeks proving it the expensive way.

### Discussion questions

1. The regulator asked for rotation every 24 hours and revocation within fifteen minutes. Why does an 8-hour TTL satisfy both, and why would a 5-minute TTL satisfy neither *better*?
2. The pilot's lease count was identical at 5 minutes and 8 hours. Derive that result, and name the three levers that would actually have reduced it.
3. Incident 2 was invisible to every dashboard because `sys/health` reported healthy. What is the correct alert, and what does it monitor?
4. The team rejected OpenBao on namespaces and lease-count quotas. If a later release adds namespaces, what else would have to be true before the migration became reasonable?

