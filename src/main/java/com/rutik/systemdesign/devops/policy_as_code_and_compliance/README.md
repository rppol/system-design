# Policy as Code and Compliance

> Phase 7 — DevSecOps & Reliability · Difficulty: Advanced

---

## 1. Concept Overview

Policy as Code (PaC) expresses governance rules — "no privileged containers," "every S3 bucket must be encrypted," "images must come from approved registries" — as version-controlled, testable code rather than wiki pages and manual review checklists. The rules execute automatically: at CI time against Terraform plans and Kubernetes manifests, and at admission time against live API-server requests. Compliance frameworks (SOC2, PCI-DSS, HIPAA, CIS benchmarks) define *what* must be true; policy as code makes those requirements *continuously enforced and auditable* instead of a once-a-year point-in-time assessment.

The dominant engine is the Open Policy Agent (OPA) with its Rego language, which evaluates JSON input against declarative rules. On Kubernetes, two enforcement layers wrap OPA-style policy: **Gatekeeper** (OPA-based, ConstraintTemplate + Constraint CRDs) and **Kyverno** (Kubernetes-native YAML policies that validate, mutate, and generate resources). For pipeline-time checks on IaC and configuration files, **Conftest** runs Rego against any structured file before it ever reaches a cluster.

This module covers writing and testing Rego, the Gatekeeper and Kyverno enforcement models, Conftest in CI, mapping policies to the CIS Kubernetes Benchmark (~120 controls) and to SOC2/PCI-DSS/HIPAA controls, the admission-time vs CI-time tradeoff, and the human side: exceptions, waivers, and policy testing. It builds on the pod-security controls in [`../kubernetes_security/README.md`](../kubernetes_security/README.md) and the IaC workflows in [`../infrastructure_as_code_terraform/README.md`](../infrastructure_as_code_terraform/README.md).

---

## 2. Intuition

> **One-line analogy**: Policy as code is a building inspector who is also a robot — every blueprint and every doorway is checked against the exact same written code, automatically, with no "the inspector was in a good mood today."

**Mental model**: A policy is a pure function: given the JSON of a resource, it returns either silence (allowed) or a deny message (violation). The same function runs in CI against a Terraform plan and at the cluster door against a live request, so the rule is enforced identically wherever the resource tries to enter.

**Why it matters**: Manual compliance review is slow, inconsistent, and only samples a point in time; an auditor checks 30 configs and signs off, while the other 3,000 drift unchecked. Policy as code evaluates 100% of resources on every change, turning compliance from a periodic event into a continuous property and producing an audit trail for free.

**Key insight**: **A policy that only runs at CI time can be bypassed; a policy that only runs at admission time gives no early feedback.** Mature platforms run the same rules in both places — Conftest in the pipeline for fast developer feedback, Gatekeeper or Kyverno at the cluster as the non-bypassable backstop.

---

## 3. Core Principles

- **Declarative over imperative.** Express *what* must be true, not *how* to check it. Rego rules describe the forbidden state; the engine handles evaluation, so policies read like specifications.
- **Same policy, multiple gates.** Run identical logic in CI (Conftest) and at admission (Gatekeeper/Kyverno). CI gives fast feedback; admission is the enforcement that cannot be skipped by editing the pipeline.
- **Default deny for high-risk, default allow with exceptions for the rest.** Privileged containers default deny; everything-else evolves from audit mode to enforcement once the violation backlog is cleared.
- **Policies are code: test them.** Every rule ships with unit tests covering an allowed input and a denied input. An untested policy is a guess about what it rejects.
- **Exceptions are explicit and expiring.** Waivers live in version control with an owner and an expiry date, not as a forgotten `--skip` flag.
- **Map every policy to a control.** Tie each rule to a CIS/SOC2/PCI line item so auditors and engineers share one vocabulary and you can prove coverage.
- **Mutate to make compliance the default.** The cheapest way to pass a validate rule is to inject the secure value automatically. A Kyverno mutate that adds `seccompProfile: RuntimeDefault` means developers never have to think about it, and the validate rule rarely fires.
- **Observe before you enforce.** Every new rule starts in audit/dryrun. The violation count is data; only after it reaches zero (fixes or waivers) do you flip to deny, so enforcement never breaks pre-existing workloads.

---

## 4. Types / Architectures / Strategies

**Enforcement points:**

| Point | Tool | When | Bypassable? |
|-------|------|------|-------------|
| Pre-commit / CI | Conftest, Checkov | Before merge | Yes (edit pipeline) |
| Admission webhook | Gatekeeper, Kyverno | At API-server request | No (cluster-enforced) |
| Audit / background | Gatekeeper audit, kube-bench | Periodic scan of live state | N/A (reporting) |

**Gatekeeper model:** A `ConstraintTemplate` defines reusable Rego with parameters; a `Constraint` instantiates it with concrete values and a scope. This two-layer design lets one template (e.g., "allowed registries") be reused across many constraints.

**Kyverno model:** Policies are plain YAML with three capabilities:
- **validate** — reject resources that violate a pattern (e.g., require `runAsNonRoot: true`).
- **mutate** — inject defaults (e.g., add `seccompProfile: RuntimeDefault` if missing).
- **generate** — create dependent resources (e.g., a default NetworkPolicy per new namespace).

**Compliance mapping strategy:** Maintain a matrix linking each policy to framework controls.

| Policy | CIS K8s | SOC2 | PCI-DSS | HIPAA |
|--------|---------|------|---------|-------|
| No privileged containers | 5.2.1 | CC6.1 | 2.2 | §164.312(a) |
| Image from approved registry | 5.4.1 | CC6.8 | 6.3 | §164.312(c) |
| Encryption at rest required | — | CC6.7 | 3.4 | §164.312(a)(2)(iv) |
| Audit logging enabled | 3.2.1 | CC7.2 | 10.2 | §164.312(b) |

---

## 5. Architecture Diagrams

```
POLICY AS CODE — TWO ENFORCEMENT GATES

  Developer                          CI Pipeline                       Kubernetes API Server
  ─────────                          ───────────                       ─────────────────────
  edit manifest / *.tf ──► [Conftest: Rego vs files] ──► merge ──► kubectl apply
                                │  fast feedback                          │
                                └─ deny: "privileged not allowed"         ▼
                                                          ┌──────────────────────────────┐
                                                          │ ValidatingAdmissionWebhook     │
                                                          │  Gatekeeper / Kyverno          │
                                                          │  evaluate Rego/YAML policy     │
                                                          │     ADMIT  ──► etcd            │
                                                          │     DENY   ──► 403 to user     │
                                                          └──────────────────────────────┘
                                                                        │
                                          Gatekeeper audit (every 60s) ─┘
                                          scans existing objects, writes violations to status
```

```
GATEKEEPER CONSTRAINT MODEL

  ConstraintTemplate (Rego + param schema)
        │  reused by
        ├──► Constraint A: allowedRepos = ["myrepo/"]   scope: namespace=prod
        └──► Constraint B: allowedRepos = ["test/"]     scope: namespace=dev
```

---

## 6. How It Works — Detailed Mechanics

**Rego basics.** A Rego policy receives `input` (the resource JSON) and accumulates `violation` or `deny` messages. A non-empty result set means rejected. Here is a Gatekeeper-style rule denying privileged containers:

```rego
package k8sprivileged

violation[{"msg": msg}] {
  c := input.review.object.spec.containers[_]
  c.securityContext.privileged == true
  msg := sprintf("privileged container not allowed: %v", [c.name])
}
```

**Conftest in CI.** Conftest runs the same Rego against files. Point it at a Kubernetes manifest or a `terraform show -json` plan:

```bash
conftest test deployment.yaml --policy ./policies
# FAIL - deployment.yaml - privileged container not allowed: nginx
# exit code 1 fails the pipeline
```

**Gatekeeper deployment.** The ConstraintTemplate registers the Rego; the Constraint scopes and parameterizes it.

```yaml
apiVersion: templates.gatekeeper.sh/v1
kind: ConstraintTemplate
metadata:
  name: k8sallowedrepos
spec:
  crd:
    spec:
      names: { kind: K8sAllowedRepos }
      validation:
        openAPIV3Schema:
          type: object
          properties:
            repos: { type: array, items: { type: string } }
  targets:
    - target: admission.k8s.gatekeeper.sh
      rego: |
        package k8sallowedrepos
        violation[{"msg": msg}] {
          image := input.review.object.spec.containers[_].image
          not startswith_any(image, input.parameters.repos)
          msg := sprintf("image %v not from an approved registry", [image])
        }
        startswith_any(s, prefixes) { startswith(s, prefixes[_]) }
---
apiVersion: constraints.gatekeeper.sh/v1beta1
kind: K8sAllowedRepos
metadata:
  name: prod-approved-repos
spec:
  enforcementAction: deny      # or 'dryrun' / 'warn' during rollout
  match:
    namespaces: ["prod"]
  parameters:
    repos: ["myrepo/", "registry.internal/"]
```

**Gatekeeper audit** runs every 60 seconds by default (`--audit-interval=60`), re-evaluating existing objects and recording violations in the Constraint's `status.violations` so you find pre-existing non-compliance without blocking it.

**Kyverno mutate example** — inject a secure default so developers do not have to:

```yaml
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata: { name: add-seccomp }
spec:
  rules:
    - name: default-seccomp
      match: { any: [{ resources: { kinds: ["Pod"] } }] }
      mutate:
        patchStrategicMerge:
          spec:
            securityContext:
              seccompProfile: { type: RuntimeDefault }
```

**Policy testing.** OPA ships a test runner; a policy without tests is unverified.

```rego
package k8sprivileged
test_denies_privileged {
  count(violation) == 1 with input as {"review": {"object": {"spec":
    {"containers": [{"name": "x", "securityContext": {"privileged": true}}]}}}}
}
test_allows_unprivileged {
  count(violation) == 0 with input as {"review": {"object": {"spec":
    {"containers": [{"name": "x", "securityContext": {"privileged": false}}]}}}}
}
```

```bash
opa test . -v   # PASS: 2/2
```

**Conftest against a Terraform plan** is the IaC-time gate. Render the plan to JSON, then assert on it before apply:

```bash
terraform plan -out=tf.plan && terraform show -json tf.plan > plan.json
conftest test plan.json --policy ./policies/terraform
# FAIL - plan.json - security group sg-web allows 0.0.0.0/0 on port 22
```

```rego
package terraform.sg
deny[msg] {
  r := input.resource_changes[_]
  r.type == "aws_security_group_rule"
  r.change.after.cidr_blocks[_] == "0.0.0.0/0"
  r.change.after.to_port == 22
  msg := sprintf("security group %v allows 0.0.0.0/0 on port 22", [r.name])
}
```

These admission policies complement the Pod Security Standards covered in [`../kubernetes_security/README.md`](../kubernetes_security/README.md); the Conftest-against-Terraform pattern above extends the IaC workflow in [`../infrastructure_as_code_terraform/README.md`](../infrastructure_as_code_terraform/README.md).

---

## 7. Real-World Examples

- **Capital One** open-sourced its Cloud Custodian usage and uses OPA-style policy to enforce encryption and tagging across thousands of AWS accounts continuously rather than via quarterly audits.
- **CNCF projects** standardized on Gatekeeper and Kyverno as graduated/incubating admission tools; many regulated enterprises run both in audit mode before enforcement.
- **Netflix** built policy gates into its CI/CD (Spinnaker) so a non-compliant deployment is stopped before it reaches a cluster, shifting compliance left.
- **PCI-DSS audited fintechs** use Conftest to assert "no security group allows 0.0.0.0/0 on port 22" against the Terraform plan, blocking the change at PR time and producing the evidence the QSA needs.
- **kube-bench** (Aqua) automates the full CIS Kubernetes Benchmark (~120 controls) as a Job, outputting pass/fail per control for SOC2 evidence collection.

---

## 8. Tradeoffs

| Dimension | CI-time policy (Conftest) | Admission-time policy (Gatekeeper/Kyverno) |
|-----------|---------------------------|--------------------------------------------|
| Feedback speed | Fast — at PR, in seconds | At deploy, after merge |
| Bypassable | Yes — edit the pipeline | No — enforced by API server |
| Covers drift / kubectl apply | No | Yes — every request is checked |
| Latency impact | None on cluster | ~5-50ms added per admission call |
| Failure blast radius | Blocks a merge | Webhook down can block ALL writes (failurePolicy) |
| Best role | Developer guardrail | Non-bypassable backstop |

A key admission-specific risk: a `failurePolicy: Fail` webhook that becomes unavailable can block all matching API writes cluster-wide, so scope `namespaceSelector` tightly and monitor webhook health. Gatekeeper vs Kyverno: Gatekeeper offers full Rego expressiveness for complex logic, while Kyverno's YAML is easier for teams without Rego skills and uniquely supports mutate and generate.

---

## 9. When to Use / When NOT to Use

**Use policy as code when:**
- You operate multi-tenant or regulated clusters where consistent enforcement matters more than per-team flexibility.
- You must produce continuous compliance evidence for SOC2, PCI-DSS, or HIPAA rather than point-in-time snapshots.
- Misconfigurations (public buckets, privileged pods, open security groups) are a recurring incident source.
- You have enough resource volume that manual review cannot scale.

**Use lighter controls when:**
- A single small team owns one cluster and informal review is genuinely sufficient.
- The overhead of maintaining and testing policies exceeds the risk they mitigate for a non-production sandbox.

**Do NOT** deploy admission webhooks in `enforce`/`Fail` mode before running them in audit/dryrun long enough to clear the existing violation backlog — flipping straight to enforce will block legitimate, pre-existing workloads and can freeze the cluster.

---

## 10. Common Pitfalls

- **Enforce before audit.** Turning on `deny` while hundreds of non-compliant objects exist breaks deploys and erodes trust in the platform.
- **Untested policies.** A Rego typo can silently allow everything; without `opa test` you ship a policy that enforces nothing.
- **`failurePolicy: Fail` with broad scope.** If the webhook pod crashes, every matching create/update is rejected — including the pods that would heal the webhook.
- **Waivers as permanent exceptions.** A skip annotation with no expiry becomes a forever hole; track exceptions in version control with owners and dates.

**BROKEN → FIX: a webhook that blocks the entire cluster when it fails.**

```yaml
# BROKEN: fail-closed webhook matching ALL namespaces, no health exclusion
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata: { name: policy-webhook }
webhooks:
  - name: validate.policy.io
    failurePolicy: Fail          # any webhook outage = cluster-wide write block
    namespaceSelector: {}        # matches kube-system, gatekeeper itself, everything
    rules:
      - apiGroups: ["*"]
        apiVersions: ["*"]
        operations: ["CREATE", "UPDATE"]
        resources: ["*"]
```

```yaml
# FIX: exclude control-plane namespaces and narrow the scope
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata: { name: policy-webhook }
webhooks:
  - name: validate.policy.io
    failurePolicy: Fail
    namespaceSelector:
      matchExpressions:
        - key: kubernetes.io/metadata.name
          operator: NotIn
          values: ["kube-system", "gatekeeper-system", "kyverno"]
    rules:
      - apiGroups: [""]
        apiVersions: ["v1"]
        operations: ["CREATE", "UPDATE"]
        resources: ["pods"]      # scope to what the policy actually evaluates
```

The fix prevents a webhook outage from blocking the very system namespaces that run the webhook, and limits evaluation to the resources the policy targets, shrinking the blast radius.

---

## 11. Technologies & Tools

| Tool | Layer | Language | Capabilities | Best for |
|------|-------|----------|--------------|----------|
| OPA / Rego | Engine | Rego | Generic decision API | Cross-domain policy, complex logic |
| Gatekeeper | K8s admission | Rego (templates) | validate, audit | Rego-fluent teams, OPA reuse |
| Kyverno | K8s admission | YAML | validate, mutate, generate | Teams avoiding Rego, defaults injection |
| Conftest | CI / files | Rego | validate any structured file | Terraform/K8s/Dockerfile in pipeline |
| Checkov | CI / IaC | Python rules | IaC misconfig scan, ~1000 built-ins | Fast out-of-box Terraform coverage |
| kube-bench | Audit | Go | CIS benchmark (~120 controls) | SOC2 evidence, node hardening checks |

---

## 12. Interview Questions with Answers

**Q: What is policy as code and how does it differ from a compliance checklist?**
Policy as code expresses governance rules as version-controlled, testable code that executes automatically against every resource, whereas a checklist is a manual document sampled by a human at a point in time. The code runs identically in CI and at admission, covering 100% of changes and producing an audit trail for free, while a checklist covers only what the reviewer happened to inspect. Adopt policy as code when you need continuous, consistent enforcement and auditable evidence rather than periodic manual review.

**Q: Explain the Gatekeeper ConstraintTemplate vs Constraint distinction.**
A ConstraintTemplate defines reusable Rego logic plus a parameter schema (the "what kind of check"), and a Constraint instantiates that template with concrete values and a scope (the "applied here with these settings"). This two-layer design lets one template like "allowed repositories" be reused by many constraints with different repo lists per namespace. It mirrors a class-versus-instance relationship and avoids duplicating Rego for every variation.

**Q: When would you choose Kyverno over Gatekeeper?**
Choose Kyverno when your team prefers Kubernetes-native YAML over learning Rego and when you need mutation or generation, not just validation. Kyverno can inject secure defaults (mutate) and create dependent resources like default NetworkPolicies (generate), which Gatekeeper does not natively do as cleanly. Choose Gatekeeper when you need the full expressiveness of Rego for complex cross-field logic or want to reuse existing OPA policies.

**Q: Why run the same policy at both CI and admission time?**
CI-time checks (Conftest) give developers fast feedback before merge but are bypassable by editing the pipeline, while admission-time checks (Gatekeeper/Kyverno) are enforced by the API server and cannot be skipped, but only fire after merge at deploy. Running both gives early feedback and a non-bypassable backstop, so a manifest applied directly with kubectl is still caught. The practical guidance is to keep the policy logic identical in both gates so behavior is consistent.

**Q: What is the danger of `failurePolicy: Fail` on an admission webhook?**
With `failurePolicy: Fail`, if the webhook pod is unavailable, every matching create/update request is rejected, which can block cluster-wide writes — including the pods needed to recover the webhook itself. The fix is to exclude control-plane namespaces via a namespaceSelector and scope the webhook rules narrowly to only the resources the policy evaluates. Always monitor webhook health and have a break-glass path to remove the configuration during an incident.

**Q: How do you roll out a new enforcement policy without breaking existing workloads?**
Deploy it first in audit/dryrun mode so it records violations in status without denying anything, then work through the existing violation backlog and fix or waive each. Once violations are near zero, flip `enforcementAction` to deny, ideally per-namespace starting with low-risk environments. This staged approach surfaces pre-existing non-compliance safely instead of breaking deploys the moment enforcement is enabled.

**Q: How does policy as code map to frameworks like SOC2 or PCI-DSS?**
Each policy is tagged to specific control IDs — for example "no privileged containers" maps to CIS K8s 5.2.1, SOC2 CC6.1, and PCI-DSS 2.2 — in a maintained traceability matrix. This lets auditors see exactly which automated control satisfies which requirement and lets you prove continuous enforcement rather than a snapshot. The practical payoff is that audit evidence becomes a query against policy results instead of a manual document hunt.

**Q: How do you test Rego policies?**
Write unit tests using OPA's built-in test runner, providing mock `input` for both an allowed case and a denied case and asserting the violation count. For example, a privileged-container test feeds a privileged pod and asserts exactly one violation, plus a clean pod asserting zero. Run `opa test` in CI so an untested or broken policy — which can silently allow everything — fails the build before it ships.

**Q: What is Conftest and where does it fit?**
Conftest runs Rego policies against any structured file — Kubernetes YAML, a `terraform show -json` plan, Dockerfiles, JSON config — at CI time before the resource reaches a cluster. It shifts policy left so a developer sees "security group opens 0.0.0.0/0 on port 22" at the PR instead of after apply. It is a fast guardrail; pair it with admission enforcement since CI checks can be bypassed.

**Q: How should exceptions and waivers be handled?**
Exceptions must be explicit, version-controlled, owned, and time-boxed with an expiry date, never an undocumented skip flag. In Gatekeeper you scope constraints with match excludedNamespaces or labels; in Kyverno you use policy exceptions resources — both reviewable in git. The goal is that every waiver is auditable and automatically expires, so temporary holes do not become permanent.

**Q: What does the CIS Kubernetes Benchmark cover and how do you automate it?**
The CIS Kubernetes Benchmark is a set of roughly 120 hardening controls spanning the API server, etcd, kubelet, RBAC, and pod security configuration. You automate it with kube-bench, which runs as a Job on nodes and outputs pass/fail per control, feeding SOC2 and compliance evidence. Combine it with admission policies so the benchmark covers node/control-plane config while Gatekeeper/Kyverno cover workload config.

**Q: What is the difference between validate, mutate, and generate in Kyverno?**
Validate rejects resources that violate a pattern (e.g., require runAsNonRoot), mutate modifies incoming resources to inject defaults (e.g., add seccompProfile RuntimeDefault if missing), and generate creates dependent resources automatically (e.g., a default NetworkPolicy for each new namespace). Validate enforces, mutate fixes silently, and generate provisions. Use mutate to make the secure path the default so developers comply without extra effort, reducing validate-stage friction.

---

## 13. Best Practices

- Store all policies in version control alongside the code they govern; review policy changes like any other PR.
- Ship every policy with `opa test` unit tests covering at least one allow and one deny case; gate CI on them.
- Run the same logic in CI (Conftest) for fast feedback and at admission (Gatekeeper/Kyverno) as the non-bypassable backstop.
- Always roll out new enforcement in audit/dryrun mode first; only flip to deny after the violation backlog is cleared.
- Scope admission webhooks tightly with namespaceSelector, exclude control-plane namespaces, and monitor webhook health.
- Maintain a policy-to-control traceability matrix mapping each rule to CIS/SOC2/PCI/HIPAA IDs for audit evidence.
- Make exceptions explicit, owned, and expiring in version control; never use silent skip flags.
- Prefer Kyverno mutate to inject secure defaults so the compliant path is the default and validate failures are rare.

---

## 14. Case Study

**Scenario.** A healthcare SaaS subject to HIPAA runs three EKS clusters shared by eight teams. A pen test finds privileged containers, images pulled from public Docker Hub, and one pod with `hostNetwork: true`. The compliance team needs continuous, provable enforcement mapped to HIPAA §164.312 controls — not a quarterly manual sweep that misses drift.

**Design.** Conftest runs in every PR against rendered manifests and Terraform plans for fast feedback. Gatekeeper enforces at admission with three constraints — no privileged containers, approved registries only, no hostNetwork — each mapped to a HIPAA control. Policies are rolled out in dryrun, the backlog cleared, then flipped to deny per namespace. kube-bench produces CIS evidence on a weekly Job.

**BROKEN → FIX: the constraint that was enabled in deny mode before auditing.**

```yaml
# BROKEN: deny from day one across all namespaces; existing privileged pods now un-redeployable
apiVersion: constraints.gatekeeper.sh/v1beta1
kind: K8sPSPPrivilegedContainer
metadata: { name: no-privileged }
spec:
  enforcementAction: deny        # flipped straight to deny
  match:
    kinds: [{ apiGroups: [""], kinds: ["Pod"] }]
    namespaces: ["team-a", "team-b", "kube-system"]  # includes system ns -> breakage
```

```yaml
# FIX: start in dryrun, exclude system namespaces, scope to app namespaces, then promote to deny
apiVersion: constraints.gatekeeper.sh/v1beta1
kind: K8sPSPPrivilegedContainer
metadata:
  name: no-privileged
  annotations:
    compliance.io/control: "HIPAA-164.312(a); CIS-5.2.1"
spec:
  enforcementAction: dryrun      # observe violations first; flip to deny after backlog cleared
  match:
    kinds: [{ apiGroups: [""], kinds: ["Pod"] }]
    excludedNamespaces: ["kube-system", "gatekeeper-system"]
    namespaces: ["team-a", "team-b"]
```

**Outcome.** Over a 2-week dryrun, Gatekeeper's audit (every 60s) surfaces 23 privileged pods and 11 public-registry images across teams; each is fixed or given an expiring, version-controlled waiver. After flipping to deny, the next attempt to deploy a privileged pod is rejected at the API server in ~12ms with a HIPAA-mapped message, and a direct `kubectl apply` of a Docker Hub image is blocked identically. Quarterly audit prep drops from a 3-day manual sweep to a single query over policy results plus the weekly kube-bench report. See [`../kubernetes_security/README.md`](../kubernetes_security/README.md) for the pod-runtime baseline these policies enforce and [`../infrastructure_as_code_terraform/README.md`](../infrastructure_as_code_terraform/README.md) for the Conftest-against-Terraform integration.
