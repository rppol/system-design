# Kubernetes Operators & CRDs

> Phase 2 — Containers & Kubernetes · Difficulty: Advanced

Kubernetes is extensible: you can teach it new object types (**Custom Resource Definitions**) and write controllers (**operators**) that reconcile them — applying the same "declare desired state, reconcile continuously" model to anything, from databases to certificates to your own platform abstractions. Operators are how complex stateful software (Postgres, Kafka, Prometheus) gets day-2 automation (backups, failover, upgrades) encoded as code.

---

## 1. Concept Overview

Two extension mechanisms work together:

- **CRD (CustomResourceDefinition)** — registers a new API type (e.g., `PostgresCluster`, `Certificate`) with the API server. Users then `kubectl apply` instances (custom resources, CRs) just like built-in objects, with schema validation, `kubectl get`, RBAC, etc.
- **Operator** — a custom controller that **watches** CRs of a type and runs a **reconciliation loop** to make reality match the CR's `spec` (provision a database, request a cert, scale a cluster). It encodes the operational knowledge a human SRE would otherwise apply manually.

This is the **operator pattern**: domain expertise (how to safely back up/upgrade/fail over a specific system) captured as a controller, so day-2 operations become declarative. Built with the **Operator SDK** / **Kubebuilder** (controller-runtime, Go) or in other languages.

---

## 2. Intuition

> **One-line analogy**: A built-in Kubernetes controller is like a thermostat that only knows "temperature." A CRD teaches the building a new dial — say "humidity" — and an operator is the technician who knows exactly how to reach and hold a target humidity (run the humidifier, watch for mold, adjust). You declare "I want 45% humidity"; the operator does everything a human expert would.

**Mental model**: Everything in Kubernetes is already "a desired-state object + a controller that reconciles it" (see [kubernetes_architecture](../kubernetes_architecture/kubernetes_architecture.md)). Operators just let *you* add new object types and reconcilers using the exact same machinery. The operator watches its CRs, diffs spec vs the real-world state it manages, and takes one corrective step per loop — forever, level-triggered, self-healing.

**Why it matters**: Stateful and complex software needs day-2 operations (backup, restore, failover, rolling upgrade, scaling) that are error-prone by hand. Operators encode that expertise so a `kubectl apply` of a `PostgresCluster` yields a backed-up, HA, auto-failing-over database. They're also how platform teams expose simplified self-service abstractions (a `Database` CR that hides 200 lines of YAML).

**Key insight**: An operator is just a controller running the same reconcile loop the built-in controllers run — `for { observe; diff(spec, status); act }`. There's no new magic. Writing a good operator is mostly about making reconciliation **idempotent** (safe to run repeatedly) and **level-triggered** (acting on current state, not events), exactly like the core controllers.

---

## 3. Core Principles

1. **Extend the API with CRDs.** New types get schema validation, RBAC, and `kubectl` for free.
2. **Operators reconcile CRs.** Same watch → diff → act loop as built-in controllers.
3. **Encode operational expertise.** Backups, failover, upgrades become declarative day-2 automation.
4. **Reconciliation must be idempotent.** Re-running the loop converges to the same state; no duplicate side effects.
5. **Level-triggered, not edge-triggered.** Act on current observed state so missed events/restarts self-heal.
6. **Status reflects reality.** Update the CR's `status` so users and other controllers can observe progress.

---

## 4. Types / Architectures / Strategies

### Extension options

| Mechanism | What it adds | Use |
|-----------|--------------|-----|
| CRD only | New API type, no behavior | Config objects consumed by existing tools |
| CRD + operator | New type + reconciling controller | Day-2 automation (the operator pattern) |
| Aggregated API server | Custom API with its own storage | Rare; advanced extension |
| Admission webhook | Validate/mutate any object | Policy, defaulting (see [policy_as_code_and_compliance](../policy_as_code_and_compliance/policy_as_code_and_compliance.md)) |
| kro `ResourceGraphDefinition` | New API type composed of existing resources; kro generates the CRD and runs a controller for it | Platform abstractions (an `App` = Deployment + Service + bucket) with no custom logic |

### Operator capability levels (maturity model)

| Level | Capability |
|-------|-----------|
| 1 — Basic install | Provision the app |
| 2 — Upgrades | Patch/minor upgrades |
| 3 — Lifecycle | Backups, failure recovery |
| 4 — Insights | Metrics, alerts, log processing |
| 5 — Autopilot | Auto-scaling, auto-tuning, auto-remediation |

### Build tooling

| Tool | Notes |
|------|-------|
| Kubebuilder / controller-runtime (Go) | Most common; informers, workqueues, reconcilers |
| Operator SDK | Wraps Kubebuilder; also Ansible/Helm operators |
| kopf (Python) / Metacontroller | Lower-barrier operators; Metacontroller calls your logic as a webhook, so the controller can be written in any language |

---

## 5. Architecture Diagrams

**CRD + operator**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    crd("CRD: PostgresCluster<br/>registers the type") --> user(["kubectl apply<br/>replicas:3, v16, backup:daily"])
    user --> etcd("apiserver stores<br/>CR in etcd")
    etcd -->|"watch"| recon("Reconcile:<br/>observe → diff → act")
    recon --> build("Create StatefulSet+PVCs,<br/>configure replication,<br/>schedule backup CronJob")
    build --> healthy{"Primary healthy?"}
    healthy -->|"no"| promote("Promote a replica")
    healthy -->|"yes"| status(["Update CR.status"])
    promote --> status
    status -.->|"repeat, level-triggered"| recon

    class crd,etcd base
    class user,status io
    class recon,healthy mathOp
    class build train
    class promote lossN
```
*A user's `kubectl apply` is stored in etcd and picked up by the operator's watch; the reconcile loop provisions the StatefulSet, promotes a replica on primary failure, publishes `status`, and loops back on a periodic requeue.*

**Reconcile loop (the heart of any operator)**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    start(["Reconcile(req)"]) --> get("cr := get(req)")
    get --> exists{"CR exists?"}
    exists -->|"no, deleted"| cleanup("Finalizer cleanup,<br/>return")
    exists -->|"yes"| observe("actual := observeRealWorld(cr)")
    observe --> diff("diff(cr.spec, actual)")
    diff --> apply("apply(step)<br/>idempotent: skip if correct")
    apply --> upd("cr.status = actual<br/>update(cr)")
    upd --> requeue(["requeue after 30s"])
    requeue -.->|"level-triggered<br/>safety net"| start

    class start,upd io
    class get,exists,observe,diff mathOp
    class cleanup frozen
    class apply train
    class requeue req
```
*The generic shape every operator's `Reconcile` function follows: fetch the CR, exit early if deleted, diff spec against observed reality, apply idempotent steps, publish status, and requeue — the dotted loop-back is the periodic resync that makes this level-triggered and self-healing.*

---

## 6. How It Works — Detailed Mechanics

### Define a CRD

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata: {name: postgresclusters.db.example.com}
spec:
  group: db.example.com
  scope: Namespaced
  names: {kind: PostgresCluster, plural: postgresclusters, shortNames: [pgc]}
  versions:
    - name: v1
      served: true
      storage: true
      schema:
        openAPIV3Schema:               # schema validation: rejects bad CRs at admission
          type: object
          properties:
            spec:
              type: object
              required: [replicas, version]
              properties:
                replicas: {type: integer, minimum: 1, maximum: 9}
                version:  {type: string}
                backup:   {type: string, enum: [none, daily, hourly]}
            status:
              type: object
              properties:
                phase: {type: string}
      subresources: {status: {}}        # enables status subresource (separate RBAC/updates)
```

### Change the storage version

Moving `storage: true` from `v1` to `v2` only affects new writes. Objects already in etcd stay encoded as `v1` until something rewrites them, and `status.storedVersions` keeps listing `v1`. The API server rejects a CRD update that drops a version still listed there. Kubernetes 1.37 (2026-08-26) made the `StorageVersionMigration` API (`storagemigration.k8s.io/v1`, KEP-4192) stable and enabled by default, so the cluster rewrites the old objects for you:

```yaml
apiVersion: storagemigration.k8s.io/v1
kind: StorageVersionMigration
metadata: {name: postgresclusters-to-v2}
spec:
  resource: {group: db.example.com, resource: postgresclusters}
```

```bash
kubectl wait --for=condition=Succeeded storageversionmigration/postgresclusters-to-v2
kubectl get crd postgresclusters.db.example.com -o jsonpath='{.status.storedVersions}'   # expect ["v2"]
# then set served: false on v1, and remove v1 from spec.versions in a later release
```

The same API rewrites Secrets after an encryption-at-rest key rotation.

### A user creates an instance

```yaml
apiVersion: db.example.com/v1
kind: PostgresCluster
metadata: {name: orders-db}
spec: {replicas: 3, version: "16", backup: daily}
# kubectl get postgresclusters  -> shows it like any built-in object
```

### The reconciler (controller-runtime, Go — the core idea)

```go
func (r *PGReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
    var cr dbv1.PostgresCluster
    if err := r.Get(ctx, req.NamespacedName, &cr); err != nil {
        return ctrl.Result{}, client.IgnoreNotFound(err)   // deleted -> nothing to do
    }
    // 1) Ensure the StatefulSet matches spec.replicas/version (idempotent create-or-update).
    if err := r.ensureStatefulSet(ctx, &cr); err != nil { return ctrl.Result{}, err }
    // 2) Ensure a backup CronJob exists matching spec.backup.
    if err := r.ensureBackup(ctx, &cr); err != nil { return ctrl.Result{}, err }
    // 3) If the primary is down, promote a healthy replica.
    if r.primaryUnhealthy(ctx, &cr) { r.failover(ctx, &cr) }
    // 4) Reflect reality in status so users/other controllers can observe.
    cr.Status.Phase = r.computePhase(ctx, &cr)
    _ = r.Status().Update(ctx, &cr)
    return ctrl.Result{RequeueAfter: 30 * time.Second}, nil  // periodic resync (level-triggered)
}
```

### Status conditions and `observedGeneration`

Every write to `spec` bumps `metadata.generation`; writes to `status` do not. A condition is only trustworthy if it says which generation it describes, so the standard `metav1.Condition` carries `type`, `status` (`True`, `False`, `Unknown`), `reason` (required, CamelCase), `message`, `lastTransitionTime`, and `observedGeneration`.

```go
import "k8s.io/apimachinery/pkg/api/meta"

meta.SetStatusCondition(&cr.Status.Conditions, metav1.Condition{
    Type:               "Ready",
    Status:             metav1.ConditionFalse,
    Reason:             "ReplicasNotReady",
    Message:            fmt.Sprintf("%d/%d replicas ready", ready, cr.Spec.Replicas),
    ObservedGeneration: cr.Generation,     // the spec this verdict is about
})
cr.Status.ObservedGeneration = cr.Generation
if err := r.Status().Update(ctx, &cr); err != nil { return ctrl.Result{}, err }
```

A client that waits for `Ready=True` must also check `observedGeneration == metadata.generation`; otherwise it reads the verdict on the previous spec. `kubectl wait --for=condition=Ready` makes that check only when the field exists: it reads the condition's `observedGeneration`, falls back to `status.observedGeneration`, and keeps waiting while it is older than `metadata.generation`. An operator that never sets either field gets no such protection, and `kubectl wait` returns at once on the stale `True`. `meta.SetStatusCondition` only moves `lastTransitionTime` when `status` actually flips, which keeps the field meaningful for "how long has this been failing".

### Server-side apply in the reconciler

`Update` sends the whole object and fails with a conflict whenever someone else wrote it since the read; `Create`-then-`Update` code also tends to overwrite fields other actors own (an HPA's `replicas`, an injected sidecar annotation). Server-side apply sends only the fields the operator has an opinion on, under a named **field manager**, and the API server merges it and records ownership in `metadata.managedFields`.

```go
import appsv1ac "k8s.io/client-go/applyconfigurations/apps/v1"

sts := appsv1ac.StatefulSet(cr.Name, cr.Namespace).
    WithOwnerReferences(ownerRef(&cr)).
    WithSpec(appsv1ac.StatefulSetSpec().
        WithReplicas(cr.Spec.Replicas).
        WithServiceName(cr.Name))
// Same call on every reconcile: creates if absent, no-op if already equal.
err := r.Apply(ctx, sts, client.FieldOwner("pg-operator"), client.ForceOwnership)
```

Three rules make this safe. Send the full intent every time, because a field the manager stops sending is removed if no other manager owns it. Use `ForceOwnership` only for fields the operator must own; it takes them from any other manager, which is right for a controller and wrong for fields a human or an HPA should be able to change. Keep the field manager name stable across releases, because a renamed manager leaves the old one owning fields it will never update again.

### Validation ratcheting when tightening a schema

Adding a stricter rule to a served CRD version (say `maxLength: 63` on `spec.dbName`) used to break every existing object that violated it: the next write from the operator, even a status-only label change, failed validation. With **validation ratcheting** (`CRDValidationRatcheting`, beta and on by default in 1.30, GA in 1.33) the API server accepts an update to an invalid object as long as every part that fails validation was not changed by that update. A user who edits `spec.dbName` must fix it; an operator that only touches `spec.replicas` is not blocked. Ratcheting does not cover `allOf`, `oneOf`, `anyOf`, `not`, changes to `required`, `x-kubernetes-list-type`, list map keys, or CEL rules that reference `oldSelf`; those still fail as before.

### kro: a ResourceGraphDefinition instead of a controller

When the "operator" would only create and wire existing resources, kro (Kube Resource Orchestrator, a Kubernetes SIG Cloud Provider subproject, API `kro.run/v1alpha1`) replaces the Go code with one object. A `ResourceGraphDefinition` declares a schema for the new API and the resources each instance creates; CEL expressions in `${...}` connect fields, and kro builds the dependency graph from those references.

```yaml
apiVersion: kro.run/v1alpha1
kind: ResourceGraphDefinition
metadata: {name: webapp}
spec:
  schema:
    apiVersion: v1alpha1
    kind: WebApp
    spec:
      name: string | required=true
      image: string | default=nginx
      replicas: integer | default=2
    status:
      availableReplicas: ${deployment.status.availableReplicas}
  resources:
    - id: deployment
      template:
        apiVersion: apps/v1
        kind: Deployment
        metadata: {name: "${schema.spec.name}"}
        spec:
          replicas: ${schema.spec.replicas}
          selector: {matchLabels: {app: "${schema.spec.name}"}}
          template:
            metadata: {labels: {app: "${schema.spec.name}"}}
            spec: {containers: [{name: app, image: "${schema.spec.image}"}]}
    - id: service
      template:
        apiVersion: v1
        kind: Service
        metadata: {name: "${schema.spec.name}"}
        spec:
          selector: ${deployment.spec.selector.matchLabels}
          ports: [{port: 80}]
```

Applying it makes kro validate the graph, register a `WebApp` CRD, and start a controller for it; a user then applies `kind: WebApp` with three fields. Because `service` references `deployment`, kro creates the Deployment first. The limit is logic: kro composes and orders resources, but it has no place for imperative steps such as "promote a replica when the primary fails" or "take a backup before upgrading". Those still need an operator.

### Finalizers (clean up external resources on delete)

A finalizer gates a CR's deletion behind a lifecycle state, not just a boolean check:

```mermaid
stateDiagram-v2
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    [*] --> Active
    Active --> Terminating: kubectl delete (DeletionTimestamp set)
    Terminating --> Removed: finalizer cleans up, then removes itself
    Removed --> [*]
    note right of Terminating: blocked from actual deletion until the finalizer list is empty

    class Active train
    class Terminating lossN
    class Removed frozen
```
*A CR cannot leave `Terminating` until the operator's cleanup step removes the finalizer — this is exactly what protects the S3 bucket / EBS volumes below from being orphaned.*

```go
// Without a finalizer, deleting the CR could orphan cloud resources (S3 backup bucket, EBS vols).
// A finalizer blocks deletion until cleanup runs:
if cr.DeletionTimestamp != nil {
    r.deleteBackups(ctx, &cr)               // external cleanup
    controllerutil.RemoveFinalizer(&cr, myFinalizer)
    r.Update(ctx, &cr)                       // now the CR is actually deleted
    return ctrl.Result{}, nil
}
```

### Idempotency is the rule

```go
// BROKEN: creates a new backup CronJob every reconcile -> duplicates pile up.
r.Create(ctx, newBackupCronJob(&cr))

// FIX: create-or-update (server-side apply / get-then-update) -> converges, no duplicates.
cj := &batchv1.CronJob{ObjectMeta: metav1.ObjectMeta{Name: cr.Name + "-backup", Namespace: cr.Namespace}}
controllerutil.CreateOrUpdate(ctx, r.Client, cj, func() error { mutate(cj, &cr); return nil })
```

---

## 7. Real-World Examples

- **Prometheus Operator**: CRDs like `Prometheus`, `ServiceMonitor`, `AlertmanagerConfig` let you declare monitoring targets and alerting declaratively; the operator generates the underlying StatefulSets and config (see [observability_metrics_prometheus](../observability_metrics_prometheus/observability_metrics_prometheus.md)).
- **cert-manager**: a `Certificate` CR + operator requests, renews, and stores TLS certs from Let's Encrypt/ACME automatically — turning cert lifecycle into day-2 automation (see [networking_for_devops](../networking_for_devops/networking_for_devops.md)).
- **CloudNativePG / Zalando Postgres Operator**: declare a `PostgresCluster`/`postgresql` CR and get HA Postgres with streaming replication, automated failover, backups to S3, and rolling minor upgrades.
- **External Secrets Operator**: an `ExternalSecret` CR syncs secrets from Vault/cloud into Kubernetes Secrets with rotation (see [secrets_management](../secrets_management/secrets_management.md)).
- **Crossplane**: CRDs that provision *cloud* infrastructure (RDS, S3) from within Kubernetes — operators as an IaC engine (see [platform_engineering_and_idp](../platform_engineering_and_idp/platform_engineering_and_idp.md)).

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Run stateful software | Operator (automated day-2) | Manual StatefulSet | Failover/backup automation vs simplicity |
| Build vs buy operator | Write your own | Use a mature community operator | Niche need vs maintenance burden |
| Extension | CRD + operator | Helm chart (static) | Need reconciliation/day-2 vs one-time install |
| Operator language | Go (controller-runtime) | Python (kopf) / Ansible | Performance/ecosystem vs barrier to entry |
| Abstraction | Custom platform CRDs | Expose raw K8s | Self-service simplicity vs flexibility |
| Composition-only API | Hand-written controller | kro `ResourceGraphDefinition` | Imperative day-2 logic vs no Go code to maintain (API still `v1alpha1`) |

---

## 9. When to Use / When NOT to Use

**Use/build an operator when:** running complex stateful software that needs automated backups/failover/upgrades, or building a platform abstraction (a simple `Database`/`App` CR hiding complexity) for self-service.

**Prefer a mature community operator** over writing your own unless your need is genuinely novel — operators are subtle (idempotency, finalizers, status, leader election) and a buggy operator can destroy data.

**Don't build an operator when:** a Helm chart or plain manifests suffice (no ongoing reconciliation/day-2 logic needed), or a managed cloud service is a better fit than running the software at all.

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    need(["Need day-2 automation<br/>for stateful software?"]) --> chart{"Static install is enough,<br/>no ongoing reconciliation?"}
    chart -->|"yes"| helm("Use a Helm chart<br/>or plain manifests")
    chart -->|"no"| mature{"Mature community<br/>operator exists?"}
    mature -->|"yes"| adopt("Adopt it:<br/>Prometheus Operator, cert-manager,<br/>CloudNativePG")
    mature -->|"no"| novel{"Genuinely novel need,<br/>no managed service fits?"}
    novel -->|"no"| managed("Prefer a managed<br/>cloud service")
    novel -->|"yes"| build[["Build your own<br/>Kubebuilder / controller-runtime"]]

    class need io
    class chart,mature,novel mathOp
    class helm base
    class adopt train
    class managed frozen
    class build lossN
```
*Community operators are subtle (idempotency, finalizers, status, leader election) and a buggy one can destroy data, so the decision tree only reaches "build your own" after ruling out a static install, an existing operator, and a managed service.*

---

## 10. Common Pitfalls

**Pitfall 1 — Non-idempotent reconciliation creating duplicates.**

```go
// BROKEN: unconditionally creating child resources each reconcile.
func (r *Reconciler) Reconcile(...) {
    r.Create(ctx, newCronJob(&cr))     // every loop -> another CronJob -> N duplicate backups
}
```

```go
// FIX: create-or-update; the loop converges and re-running is safe.
controllerutil.CreateOrUpdate(ctx, r.Client, cj, func() error { mutate(cj); return nil })
```

**Pitfall 2 — No finalizer; deleting a CR orphans external resources.** Deleting a `PostgresCluster` leaves its S3 backup bucket, cloud volumes, or DNS records behind, leaking cost and creating drift. FIX: add a finalizer that performs external cleanup before allowing deletion.

**Pitfall 3 — Edge-triggered logic that breaks on missed events.** Reacting only to "create" events means a controller restart (which replays nothing) leaves resources unmanaged. FIX: reconcile from current observed state with a periodic resync (`RequeueAfter`), so the controller self-heals after restarts and missed events — the level-triggered model.

**Pitfall 4 - Dropping an old CRD version while etcd still holds objects in it.** Flipping `storage: true` to `v2` does not rewrite stored `v1` objects.

```yaml
# BROKEN: v1 removed right after the storage flip. The API server rejects the CRD update
# because status.storedVersions still lists v1; force it and v1 objects become unreadable.
versions:
  - {name: v2, served: true, storage: true}

# FIX: keep v1 served, run a StorageVersionMigration for postgresclusters, wait for
# Succeeded and storedVersions == ["v2"], then set v1 served: false, then remove it.
versions:
  - {name: v1, served: false, storage: false}
  - {name: v2, served: true,  storage: true}
```

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| CRD (apiextensions) | Register custom API types |
| Kubebuilder / controller-runtime | Build operators in Go |
| Operator SDK | Scaffolding (Go/Ansible/Helm operators) |
| kopf (Python) / Metacontroller | Lower-barrier operators |
| OperatorHub.io | Discover community operators |
| Prometheus Operator / cert-manager / CloudNativePG | Reference production operators |
| Crossplane | Provision cloud infra via CRDs |
| kro | Compose existing resources into a new API from a `ResourceGraphDefinition`, no controller code |
| OLM v1 (operator-controller) | Install/upgrade operators declaratively via the `ClusterExtension` API; upstream project, also shipped in OpenShift |

---

## 12. Interview Questions with Answers

**Q: What is a CRD and what does it give you?**
**Short:** A CRD registers a new API type with the API server, giving schema validation, `kubectl`, RBAC, watches, and etcd storage for free — but no behavior without a controller.
A CustomResourceDefinition registers a new API type with the API server, so you can `kubectl apply` instances of it (custom resources) just like built-in objects — with OpenAPI schema validation, `kubectl get`, RBAC, watches, and storage in etcd, all for free. A CRD alone adds a *type*, not behavior; pairing it with a controller (operator) adds the reconciliation logic.

**Q: What is the operator pattern?**
**Short:** It encodes operational expertise as a controller that watches custom resources and reconciles them, automating day-2 operations an SRE would otherwise do by hand.
It's encoding operational/domain expertise as a custom controller that watches custom resources and reconciles them to desired state — applying Kubernetes' "declare + reconcile" model to complex software. Instead of an SRE manually backing up, failing over, and upgrading a database, an operator does it automatically in response to a declarative CR. It captures day-2 operations as code.

**Q: How does an operator's reconcile loop work?**
**Short:** It watches CRs, reads spec, observes real-world state, applies one corrective step, updates status, and requeues — level-triggered and idempotent.
The same loop as built-in controllers: it watches CRs (via informers), and on each event (or periodic resync) it reads the CR's `spec`, observes the real-world state it manages, computes the diff, and applies one corrective step — then updates the CR's `status` and requeues. It's level-triggered (acts on current state) and idempotent (safe to re-run), so it self-heals after missed events and restarts.

**Q: Why must reconciliation be idempotent?**
**Short:** The loop runs repeatedly and may re-run after a restart, so non-idempotent side effects like unconditional creation produce duplicates and drift.
Because the loop runs repeatedly — on every relevant event and on periodic resyncs — and may re-run after a controller restart. If reconciliation has non-idempotent side effects (e.g., unconditionally creating a child object), you get duplicates and drift. Idempotent reconciliation (create-or-update, skip-if-already-correct) means running it once or a hundred times converges to the same correct state.

**Q: After `kubectl apply` changed a custom resource's spec, `kubectl wait --for=condition=Ready` returned immediately but the change had not rolled out. Why?**
**Short:** The operator never set `observedGeneration`, so the stale `Ready=True` from the previous spec looked current; with it set, `kubectl wait` keeps waiting until the condition describes the new generation.
Every spec write increments `metadata.generation`, but the operator's status still holds the verdict it computed for the old generation until the next reconcile finishes. `kubectl wait` compares the condition's `observedGeneration` (or `status.observedGeneration`) with `metadata.generation` and keeps waiting while it is older, but it can only do that if the field exists; this operator wrote conditions without it, so `kubectl wait` saw `True` and returned. The fix is in the operator: set `observedGeneration: cr.Generation` on each condition and on `status.observedGeneration` whenever it writes status, and have any custom client treat a condition as current only when that value equals `metadata.generation`. `kstatus`, the library Flux uses for health checks, reports a resource as `InProgress` while `status.observedGeneration` lags, so publishing the field makes the operator work correctly with those tools as well.

**Q: What is level-triggered vs edge-triggered, and why does it matter for operators?**
**Short:** Level-triggered acts on current observed state regardless of which event led there, which operators need because restarts and dropped events make edge-triggering unreliable.
Edge-triggered reacts to specific events (the "create" happened); level-triggered acts on the current observed state regardless of which event led there. Operators must be level-triggered: a controller restart replays no events, and watch streams can drop events, so reconciling from current state (with periodic resync) is the only way to reliably converge and self-heal. Edge-triggered operators silently leave resources unmanaged after restarts.

**Q: What are finalizers and why do operators need them?**
**Short:** A finalizer blocks an object's deletion until removed, letting an operator clean up external resources like cloud volumes before the CR disappears.
A finalizer is a key on an object that blocks its deletion until removed. Operators use finalizers to perform external cleanup before a CR is deleted — e.g., delete the S3 backup bucket, cloud volumes, or DNS records a `PostgresCluster` created. Without a finalizer, deleting the CR orphans those external resources, leaking cost and creating drift between Kubernetes and the cloud.

**Q: When should you write your own operator vs use an existing one?**
**Short:** Use a mature community operator whenever one fits, since getting idempotency, finalizers, and status right is subtle, and write your own only for genuinely novel needs.
Use a mature community operator (Prometheus Operator, cert-manager, CloudNativePG) whenever one fits — operators are subtle (idempotency, finalizers, status, leader election, edge cases) and a buggy one can destroy data. Write your own only for genuinely novel needs: a custom platform abstraction, proprietary software with no operator, or organization-specific automation. Building an operator is a real engineering investment to maintain.

**Q: How do you build an operator?**
**Short:** Typically with Kubebuilder/controller-runtime, which scaffolds CRD types and a Reconcile method you fill in with idempotency, finalizers, status, and leader election.
Typically with Kubebuilder/controller-runtime (Go), which scaffolds CRD types, a manager, informers, workqueues, and a `Reconcile` method you implement. The Operator SDK wraps this and also supports Ansible/Helm-based operators; kopf (Python) lowers the barrier. You define the CRD schema, implement idempotent reconciliation, manage finalizers, update status, and use leader election so only one replica reconciles at a time.

**Q: What's the difference between a CRD-with-operator and a Helm chart?**
**Short:** A Helm chart is a one-time template render with no ongoing logic, while a CRD-plus-operator continuously reconciles and performs day-2 automation like failover.
A Helm chart is a one-time (or upgrade-time) template render — it installs objects but has no ongoing logic. A CRD+operator provides *continuous* reconciliation and day-2 automation: it keeps watching and acting (failover, backup, scaling) long after install. Use a chart when you just need to deploy static manifests; use an operator when the software needs ongoing operational logic.

**Q: How does an operator report progress, and why does status matter?**
**Short:** It updates the CR's `status` subresource, letting users and other controllers observe real state without inspecting internals.
By updating the CR's `status` subresource (e.g., `status.phase: Ready`, conditions, observed replicas). This lets users (`kubectl get pgc`) and other controllers observe the real state without inspecting internals, and enables tools/automation to wait on readiness. The status subresource also allows separate RBAC and avoids spec/status update conflicts. Reflecting reality in status is part of being a good API citizen.

**Q: What is leader election in an operator and why is it needed?**
**Short:** Leader election, via a Lease, ensures only one operator replica actively reconciles at a time so multiple replicas for availability don't fight over the same resources.
When you run multiple operator replicas for availability, only one should actively reconcile at a time — otherwise two controllers fight over the same resources. Leader election (via a Lease object) ensures exactly one replica is the active leader; the others stand by and take over if the leader fails. controller-runtime provides this out of the box; forgetting it causes duplicate, conflicting actions.

**Q: How do CRDs get schema validation, and why use it?**
**Short:** The API server validates every custom resource against the CRD's mandatory structural OpenAPI schema at admission, with CEL rules for what OpenAPI can't express and ratcheting for objects already invalid.
A CRD includes an OpenAPI v3 schema; the API server validates every custom resource against it at admission (types, required fields, enums, min/max), rejecting malformed CRs before they're stored or reconciled. In `apiextensions.k8s.io/v1` that schema is mandatory and must be *structural*: every object and array item carries a non-empty `type`, and unknown fields are pruned rather than persisted. Constraints OpenAPI can't express, such as cross-field rules or "this field is immutable once set", go in `x-kubernetes-validations` as CEL expressions, which the API server type-checks when the CRD is created and evaluates on every write. This catches user errors early (e.g., `replicas: 0` where minimum is 1) with clear messages, rather than the operator having to defensively handle garbage input at runtime. Tightening a rule later is safe because of validation ratcheting (GA in 1.33): an update to an object that violates the new rule still succeeds if it does not change the invalid part, so existing objects keep working until someone edits that field.

**Q: Why should a reconciler use server-side apply with a field manager instead of Get-modify-Update?**
**Short:** Server-side apply sends only the fields the operator owns, merges with other writers instead of failing on resourceVersion conflicts, and records ownership, so the operator stops overwriting fields an HPA or a user owns.
Get-modify-Update sends the whole object with its `resourceVersion`, so any concurrent write returns a 409 conflict and a retry, and the operator easily clobbers fields it never meant to manage, such as `spec.replicas` that an HPA scales. With server-side apply the reconciler sends a partial object holding its full intent under a stable field manager (`client.FieldOwner("pg-operator")`); the API server merges it, records per-field ownership in `metadata.managedFields`, and the same call creates the object or does nothing if it already matches, which makes the reconcile idempotent by construction. Controllers pass `ForceOwnership` to take fields another manager holds, so leave out of the apply configuration any field another actor should control. A field the operator stops sending is deleted unless another manager owns it, so always send the complete intent.

**Q: When is kro a better choice than writing an operator?**
**Short:** When the new API only creates and wires existing resources with no imperative logic; a kro ResourceGraphDefinition generates the CRD and controller, while failover, backups, or upgrade steps still need an operator.
kro's `ResourceGraphDefinition` declares a schema (`name: string | required=true`) and a list of resource templates connected by CEL expressions such as `${deployment.status.availableReplicas}`. kro derives the dependency order from those references, registers the CRD, and runs a controller for it, so a platform team ships an `App` or `Database` API without writing, testing, and upgrading Go code. It fits composition: Deployment plus Service plus a Crossplane or ACK bucket, with status fields copied up. It does not fit imperative day-2 work such as promoting a replica, running a backup before an upgrade, or calling an external API, which is what a level 3+ operator encodes. The API is `kro.run/v1alpha1`, so budget for breaking changes.

**Q: What do OwnerReferences give you that finalizers don't?**
**Short:** OwnerReferences let the garbage collector auto-delete in-cluster children when the owner is deleted, while finalizers gate the owner's own deletion for external cleanup.
OwnerReferences let Kubernetes' built-in garbage collector automatically delete child objects when their owner is deleted. A finalizer instead blocks and gates deletion of the *owner itself* until custom cleanup logic runs, so setting `controllerutil.SetControllerReference(&cr, cj, r.Scheme)` on a created CronJob means deleting the parent `Database` CR cascades to delete that CronJob for free with no reconcile code required, while a finalizer handles cleanup the garbage collector cannot do, like deprovisioning an external S3 bucket. In practice you use both: OwnerReferences for in-cluster children, finalizers for anything that lives outside the cluster's own garbage collection.

**Q: How do you evolve a CRD's schema without breaking existing custom resources?**
**Short:** Add a new API version with a conversion webhook, move `storage: true` to it, run a StorageVersionMigration to rewrite stored objects, then retire the old version.
Add a new API version to the CRD rather than mutating the existing version's schema in place, and mark exactly one version `storage: true` so etcd has a single source of truth while all served versions remain readable. When the new version's shape genuinely differs from the old one, you provide a **conversion webhook** that the API server calls to translate CRs between versions on the fly, so old and new clients both get a valid object. Skipping this and just editing the schema in place risks the API server rejecting every existing CR the next time it's read or reconciled. Moving `storage: true` only changes how new writes are encoded: objects already in etcd stay in the old version and `status.storedVersions` keeps listing it, and the API server refuses to drop a version still listed there. Create a `StorageVersionMigration` (`storagemigration.k8s.io/v1`, stable and on by default since Kubernetes 1.37) for the resource, wait for its `Succeeded` condition and for `storedVersions` to show only the new version, then set the old version `served: false` and remove it in a later release.

**Q: Why must an operator's RBAC be scoped to least privilege?**
**Short:** An operator runs continuously with real privileges, so scoping RBAC to exactly what the reconciler touches limits damage from a bug or compromised image.
An operator is a powerful, always-running controller, so a compromised or buggy one with cluster-wide access can modify or destroy far more than its intended scope. Scoping RBAC to exactly the API groups, verbs, and namespaces the reconciler actually touches means a bug or a supply-chain-compromised operator image can only damage what it legitimately needed to manage, not the whole cluster. This is the same least-privilege principle applied to CI tokens, just for a controller that runs continuously rather than a pipeline that runs once.

**Q: What do the operator capability levels (1-5) actually distinguish?**
**Short:** The capability levels measure how much day-2 operational knowledge is encoded, from Level 1 install-only through Level 5 autonomous scaling and remediation.
The maturity model measures how much day-2 operational knowledge is encoded in the controller, not how well it's built. Level 1 just installs the application, Level 2 adds patch/minor upgrades, Level 3 adds lifecycle operations like backup and failure recovery, Level 4 adds insights such as metrics and alerts, and Level 5 ("autopilot") adds autonomous scaling, tuning, and remediation without human intervention. Most production operators (CloudNativePG, Prometheus Operator) sit at Level 3-4, since reaching Level 5 is rare — autonomous remediation for stateful systems is genuinely hard to get safely right, and a Level 1 operator from OperatorHub still requires you to build your own backup/failover automation around it.

---

## 13. Best Practices

- Make reconciliation **idempotent** (create-or-update) and **level-triggered** (reconcile from current state + periodic resync).
- Use **finalizers** to clean up external resources on CR deletion.
- Maintain an accurate **status** subresource; define meaningful conditions/phases.
- Use **leader election** when running multiple operator replicas.
- Validate CRs with a thorough **OpenAPI schema**; reject bad input at admission.
- Prefer **mature community operators**; only build your own for novel needs.
- Scope the operator's **RBAC to least privilege** (it's a powerful controller).
- Version your CRDs and provide **conversion webhooks** when evolving the schema.

---

## 14. Case Study

### Scenario: A homegrown "backup operator" silently stops protecting databases after a restart

A team wrote a simple operator: when a `Database` CR is *created*, it sets up a backup CronJob. It worked in testing. Months later they discover several production databases have no recent backups — and a restore is needed after a corruption incident.

**BROKEN operator behavior — the failure cascade**

```mermaid
flowchart TD
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    design("Edge-triggered,<br/>non-idempotent design") --> onCreate("Reconciles only on<br/>the CR create event")
    design --> noLeader("Multiple replicas,<br/>no leader election")
    onCreate --> reschedule("Operator pod rescheduled<br/>6 weeks later")
    reschedule --> noReplay("Restart replays<br/>no create events")
    noReplay --> stale("Existing Databases<br/>never re-reconciled")
    stale --> drift("Backup CronJobs drift<br/>or get deleted")
    drift --> silent("No status field:<br/>gap goes unnoticed")
    noLeader --> dup("Duplicate CronJobs<br/>appear earlier")
    silent --> incident(("Corruption incident<br/>forces a restore"))
    dup --> incident

    class design,noReplay mathOp
    class onCreate,noLeader req
    class reschedule frozen
    class stale,drift,silent,dup lossN
    class incident lossN
```
*Two independent design flaws — edge-triggered reconciliation and missing leader election — trace back to the same root cause and converge on the same outcome: weeks of silent backup drift discovered only during a real restore.*

```go
// BROKEN: edge-triggered, non-idempotent, no status, no leader election.
func (r *Reconciler) onCreate(cr *Database) {           // only fires on create events
    r.Create(ctx, newBackupCronJob(cr))                 // duplicates if it ever re-fires
}
```

```go
// FIX: level-triggered idempotent reconcile + status + periodic resync + leader election.
func (r *Reconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
    var db dbv1.Database
    if err := r.Get(ctx, req.NamespacedName, &db); err != nil {
        return ctrl.Result{}, client.IgnoreNotFound(err)
    }
    // idempotent: ensure the backup CronJob exists and matches spec (create OR update).
    cj := &batchv1.CronJob{ObjectMeta: metav1.ObjectMeta{Name: db.Name + "-backup", Namespace: db.Namespace}}
    if _, err := controllerutil.CreateOrUpdate(ctx, r.Client, cj, func() error {
        applyBackupSchedule(cj, &db); return controllerutil.SetControllerReference(&db, cj, r.Scheme)
    }); err != nil { return ctrl.Result{}, err }

    // reflect reality: did the last backup succeed and when?
    db.Status.LastBackup, db.Status.Phase = r.lastBackupTime(ctx, &db), r.phase(ctx, &db)
    _ = r.Status().Update(ctx, &db)
    return ctrl.Result{RequeueAfter: 10 * time.Minute}, nil  // periodic resync -> self-heals after restarts
}
// main(): mgr started with LeaderElection: true so only one replica reconciles.
```

After the fix, the operator re-reconciles every `Database` on startup and every 10 minutes, recreating any missing/drifted backup CronJob and publishing `status.lastBackup`. The team added an alert on `status.lastBackup` age, so a missing backup pages within an hour instead of being discovered during a restore.

**Outcome:** backup coverage returned to 100% and became self-healing; the `status.lastBackup` field plus an alert turned a silent multi-week gap into a sub-hour detection. The root cause was the two cardinal operator sins — edge-triggered logic and non-idempotent reconciliation — which the level-triggered, idempotent rewrite eliminated.

**Discussion questions:**
1. Why did the edge-triggered design fail specifically after a pod restart, and how does periodic resync fix it?
2. How would `OwnerReferences` (SetControllerReference) have helped the duplicate-CronJob problem and cleanup?
3. What status field + alert would you add to make "this resource isn't being managed" observable, and why is status a first-class part of operator design?

---

**Cross-references:** [kubernetes_architecture](../kubernetes_architecture/kubernetes_architecture.md) (the reconcile model operators reuse), [kubernetes_workloads_and_objects](../kubernetes_workloads_and_objects/kubernetes_workloads_and_objects.md) (the child objects operators manage), [policy_as_code_and_compliance](../policy_as_code_and_compliance/policy_as_code_and_compliance.md) (admission webhooks as another extension), [platform_engineering_and_idp](../platform_engineering_and_idp/platform_engineering_and_idp.md) (Crossplane, custom platform CRDs), [observability_metrics_prometheus](../observability_metrics_prometheus/observability_metrics_prometheus.md) (Prometheus Operator).
