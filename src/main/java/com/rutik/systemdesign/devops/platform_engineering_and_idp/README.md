# Platform Engineering and Internal Developer Platforms

> Phase 7 — DevSecOps & Reliability · Difficulty: Advanced

---

## 1. Concept Overview

Platform engineering is the discipline of building and operating an **Internal Developer Platform (IDP)** — a curated, self-service layer of tooling, automation, and golden paths that lets product engineers ship software without becoming experts in Kubernetes, Terraform, networking, or security. The platform is treated as a *product*, with its developers as the customers, a roadmap, adoption metrics, and a feedback loop — not a ticket queue.

The IDP sits between developers and the underlying infrastructure. Instead of every team writing bespoke Terraform, Helm charts, CI pipelines, and IAM policies, the platform team encodes the organization's best practices into **golden paths** (also called paved roads): opinionated, supported, low-friction routes to production. A developer creates a new service through a portal in minutes — getting a repo, CI/CD, observability, an environment, and security baked in — rather than spending two weeks wiring it by hand and getting half of it wrong.

The reference open-source backbone is **Backstage** (created at Spotify, donated to the CNCF): a software catalog that inventories every service/API/resource, a **scaffolder** that generates new components from templates, **TechDocs** for docs-as-code, and a plugin ecosystem for CI/CD, Kubernetes, cost, and security views. On the provisioning side, **Crossplane** offers a Kubernetes-native control-plane alternative to Terraform: developers submit a `Claim` for a database or bucket, and the platform's `Composition` reconciles the real cloud resources continuously.

Platform success is measured with **DORA metrics** (deployment frequency, lead time for changes, change failure rate, time to restore) and organized via **Team Topologies** (stream-aligned, platform, enabling, and complicated-subsystem teams). This module cross-references [`../gitops_argocd_flux/README.md`](../gitops_argocd_flux/README.md) (the delivery mechanism behind golden paths) and [`../infrastructure_as_code_terraform/README.md`](../infrastructure_as_code_terraform/README.md) (the alternative provisioning model to Crossplane).

---

## 2. Intuition

> **One-line analogy**: An IDP is a paved highway with on-ramps and guardrails — developers drive fast and safely without paving their own road, while still being free to go off-road when the highway does not reach their destination.

**Mental model**: Picture cognitive load as a budget. Every team has a fixed amount of attention; if they spend it on YAML, IAM, and pipeline plumbing, there is none left for the product. The platform team absorbs the undifferentiated heavy lifting into self-service abstractions, refunding that budget back to product engineers so they spend it on customer value.

**Why it matters**: Without a platform, every team reinvents deployment, secrets, monitoring, and security — inconsistently and insecurely — and the "DevOps team" becomes a ticket-driven bottleneck. Spotify built Backstage precisely because onboarding a new engineer to its sprawl of internal tools took weeks; the catalog cut it to hours.

**Key insight**: **A platform is a product, not a project.** If developers can route around it because the golden path is slower or more painful than rolling their own, the platform has failed regardless of how elegant its internals are. Adoption — not architecture — is the success metric.

---

## 3. Core Principles

1. **Platform-as-product**. The platform has named customers (product engineers), a roadmap, and adoption/satisfaction metrics. You discover requirements by talking to users, not by guessing in an architecture review.

2. **Self-service over tickets**. A developer should provision an environment, a database, or a new service themselves in minutes through a portal or API — no human in the loop for the common case.

3. **Golden paths, not golden cages**. Provide an opinionated, fully-supported path that is the *easiest* option, but keep the underlying primitives accessible for teams whose needs the path does not cover. Make the right thing the easy thing.

4. **Reduce cognitive load** (Team Topologies). The platform exists to shrink what a stream-aligned team must know to ship safely. If using the platform adds cognitive load, it is anti-platform.

5. **Thinnest viable platform (TVP)**. Start with the smallest abstraction that helps — sometimes a wiki page and a Terraform module — and grow only where demand proves it out. Do not build a giant portal nobody asked for.

6. **Measure with DORA**. Deployment frequency, lead time, change failure rate, and MTTR are the outcome metrics that tell you whether the platform is actually accelerating delivery.

7. **Encode policy, do not gatekeep**. Security and compliance are baked into the golden path (signed images, scanned dependencies, least-privilege IAM) so doing the right thing requires no extra developer effort.

---

## 4. Types / Architectures / Strategies

**Developer portal (Backstage)** — A single pane of glass. Core building blocks:
- **Software Catalog**: a graph of `Component`, `API`, `System`, `Resource`, and `Group` entities described by `catalog-info.yaml` files living next to the code, giving ownership, dependencies, and discoverability.
- **Scaffolder (Software Templates)**: parameterized generators that create a new repo with CI/CD, Dockerfile, Helm chart, and `catalog-info.yaml` pre-wired — the golden path made executable.
- **TechDocs**: docs-as-code rendered from Markdown in each repo, so documentation lives with the service.
- **Plugins**: Kubernetes, CI/CD, cost, PagerDuty, security scorecards surfaced per-service.

**Control-plane provisioning (Crossplane)** — Instead of imperative `terraform apply` in a pipeline, Crossplane runs *inside* Kubernetes and continuously reconciles cloud resources:
- **Providers**: install CRDs for AWS/GCP/Azure resources (e.g., `RDSInstance`, `Bucket`).
- **Compositions**: platform-authored bundles mapping a high-level abstraction to many concrete resources.
- **Composite Resource Definitions (XRDs)** define the developer-facing API; a developer submits a **Claim** (`PostgreSQLInstance`) and Crossplane provisions the RDS instance, subnet group, parameter group, and secret.

**Golden paths vs paved roads** — Same idea, slightly different emphasis: a *golden path* is the recommended end-to-end journey ("create a Java microservice"); a *paved road* is the supported, hardened toolchain that path runs on. Off-road is allowed but unsupported.

**Team Topologies** — Four team types: **stream-aligned** (own a product slice end-to-end), **platform** (provide the IDP as a service), **enabling** (coach teams to adopt new skills), and **complicated-subsystem** (own a deep specialized component). Interaction modes: collaboration, X-as-a-service, facilitating.

---

## 5. Architecture Diagrams

IDP layered architecture with developer self-service flow:

```
   Developer
      |  "create payments-service"  /  "I need a Postgres DB"
      v
+-------------------------------------------------------------+
|              Developer Portal  (Backstage)                  |
|  +-----------+  +-------------+  +----------+  +----------+  |
|  |  Catalog  |  | Scaffolder  |  | TechDocs |  | Plugins  |  |
|  | (graph)   |  | (templates) |  | (docs)   |  | (k8s/CI) |  |
|  +-----------+  +------+------+  +----------+  +----------+  |
+--------------------------|----------------------------------+
                           | generates repo + manifests
                           v
+-------------------------------------------------------------+
|        Golden Path Automation (GitOps + Control Plane)      |
|   Git repo --> Argo CD / Flux --> Kubernetes                |
|   Claim    --> Crossplane Composition --> Cloud resources   |
+--------------------------|----------------------------------+
                           v
+-------------------------------------------------------------+
|     Infrastructure:  EKS  |  RDS  |  S3  |  IAM  |  VPC      |
+-------------------------------------------------------------+
```

Team Topologies interaction:

```
  +------------------+   X-as-a-Service   +------------------+
  | Stream-aligned   |<------------------>|  Platform team   |
  | team (product)   |  (self-service)    |  (the IDP)       |
  +------------------+                    +------------------+
           ^                                      ^
           | facilitating                         | facilitating
           v                                      v
        +------------------------------------------------+
        |              Enabling team (coaching)          |
        +------------------------------------------------+
```

---

## 6. How It Works — Detailed Mechanics

**Backstage catalog** registers a service via a `catalog-info.yaml` committed in the repo:

```yaml
apiVersion: backstage.io/v1alpha1
kind: Component
metadata:
  name: payments-service
  description: Handles card authorization and capture
  annotations:
    backstage.io/techdocs-ref: dir:.
    github.com/project-slug: acme/payments-service
    argocd/app-name: payments-service
spec:
  type: service
  lifecycle: production
  owner: team-payments
  system: checkout
  providesApis:
    - payments-api
  dependsOn:
    - resource:payments-db
```

**Scaffolder template** turns the golden path into a one-click action. The `parameters` render a form in the portal; the `steps` fetch a skeleton, push a repo, and register it in the catalog:

```yaml
apiVersion: scaffolder.backstage.io/v1beta3
kind: Template
metadata:
  name: java-microservice
  title: Java Microservice (Golden Path)
spec:
  parameters:
    - title: Service details
      required: [name, owner]
      properties:
        name: { type: string, pattern: '^[a-z0-9-]+$' }
        owner: { type: string, ui:field: OwnerPicker }
  steps:
    - id: fetch
      name: Fetch skeleton
      action: fetch:template
      input:
        url: ./skeleton          # Dockerfile, Helm chart, CI, catalog-info.yaml
        values: { name: '${{ parameters.name }}', owner: '${{ parameters.owner }}' }
    - id: publish
      name: Publish to GitHub
      action: publish:github
      input:
        repoUrl: github.com?owner=acme&repo=${{ parameters.name }}
    - id: register
      name: Register in catalog
      action: catalog:register
      input:
        repoContentsUrl: ${{ steps.publish.output.repoContentsUrl }}
        catalogInfoPath: /catalog-info.yaml
```

**Crossplane self-service database** — the platform defines a `Composition`; the developer submits only a tiny `Claim`:

```yaml
# Developer-facing Claim (everything else is hidden by the Composition)
apiVersion: platform.acme.io/v1alpha1
kind: PostgreSQLInstance
metadata:
  name: payments-db
  namespace: team-payments
spec:
  parameters:
    storageGB: 100
    size: medium          # platform maps medium -> db.r6g.large
  compositionSelector:
    matchLabels: { provider: aws, tier: prod }
```

Crossplane's controller continuously reconciles this Claim into an RDS instance, subnet group, and a Kubernetes `Secret` with the connection string — and *re-reconciles* if anyone drifts the resource manually, unlike a one-shot `terraform apply`.

A typical golden path end-to-end: developer fills the scaffolder form → repo created with CI/CD + Helm + observability wired → Argo CD syncs it to the cluster ([`../gitops_argocd_flux/README.md`](../gitops_argocd_flux/README.md)) → service appears in the catalog with docs, ownership, and dashboards. Elapsed time: minutes, versus days of manual setup.

---

## 7. Real-World Examples

- **Spotify** built Backstage to tame ~2,000 microservices and hundreds of teams. New-engineer onboarding to internal tooling fell from weeks to hours, and the catalog became the single source of truth for ownership during incidents. They open-sourced it in 2020; it is now a CNCF incubating project.

- **Netflix** runs a mature internal platform (Spinnaker for delivery, a paved-road JVM stack) so product teams deploy thousands of times per day without touching the underlying AWS plumbing — the canonical "platform-as-product" at scale.

- **Adobe** adopted Backstage to unify dozens of teams; their public talks report cutting service-creation time and standardizing observability/security defaults across the org by moving them into golden-path templates.

- **Mercedes-Benz** built an internal platform on Backstage and GitOps to give ~thousands of engineers self-service environments, citing reduced cognitive load and faster onboarding as the primary wins — a direct application of Team Topologies thinking.

---

## 8. Tradeoffs

| Dimension | Buy (Humanitec, Port, Cortex) | Build on Backstage (OSS) | DIY scripts/wiki |
|---|---|---|---|
| Time to value | weeks | 2–6 months | days, but low ceiling |
| Customization | moderate | very high (plugins) | unbounded but unmaintained |
| Maintenance cost | vendor-managed | dedicated platform team needed | accrues as tech debt |
| Lock-in | high | low (CNCF, open) | none |
| Best for | small platform team, fast start | mid-large orgs, custom needs | early-stage / TVP |

| Provisioning model | Terraform (pipeline) | Crossplane (control plane) |
|---|---|---|
| Reconciliation | one-shot `apply` per run | continuous, self-healing |
| Drift correction | only on next run | automatic |
| Interface | HCL + CI | Kubernetes CRDs / Claims |
| Learning curve | familiar to most | requires K8s fluency |
| Best for | broad existing adoption | K8s-native self-service |

---

## 9. When to Use / When NOT to Use

**Build an IDP when**: you have enough teams and services (roughly 5+ teams or dozens of services) that inconsistency and the DevOps-as-ticket-queue bottleneck are measurably slowing delivery, and you can fund a dedicated platform team. The signal is product engineers spending days on undifferentiated setup or duplicating each other's pipelines.

**Use Backstage when** you want a customizable, open, catalog-centric portal and have the engineering capacity to run and extend it. **Use a commercial IDP (Humanitec, Port, Cortex)** when you want faster time-to-value and have a small platform team. **Use Crossplane** when your org is Kubernetes-native and you want self-healing, claim-based provisioning rather than pipeline-driven Terraform.

**Do NOT** build a heavyweight portal for a 2-team startup — a Terraform module library and a README *is* your thinnest viable platform; building Backstage there is gold-plating. **Do NOT** build a platform no one asked for: if you cannot name the developer pain it removes, you are building a project, not a product. **Do NOT** make the golden path a golden cage — if teams cannot escape it for legitimate edge cases, they will route around the whole platform and adoption collapses.

---

## 10. Common Pitfalls

1. **Building the platform in a vacuum**. No user research, no pilot team, no adoption metric — resulting in an elegant portal nobody uses. Treat it as a product with customers.

2. **Golden cage instead of golden path**. Over-rigid templates with no escape hatch push teams to fork or bypass the platform entirely.

3. **No ownership in the catalog**. A catalog where half the services have `owner: unknown` is useless during an incident.

4. **Vanity metrics**. Counting "templates created" instead of measuring DORA outcomes (deployment frequency, lead time, change failure rate, MTTR).

Broken scaffolder template — no input validation, so a developer can inject an invalid repo name and an unowned service, polluting the catalog and breaking downstream automation:

```yaml
# BROKEN: free-text name, no owner requirement, no validation.
# A developer types "My Service!" -> invalid K8s/repo name downstream,
# and owner is optional -> orphaned, unownable service in the catalog.
spec:
  parameters:
    - title: Service details
      properties:
        name:  { type: string }          # no pattern -> spaces, caps, symbols allowed
        owner: { type: string }          # not required -> can be empty
```

```yaml
# FIX: enforce a DNS-safe name pattern and make owner a required,
# picked-from-catalog field so every generated service is valid and owned.
spec:
  parameters:
    - title: Service details
      required: [name, owner]
      properties:
        name:
          type: string
          pattern: '^[a-z]([-a-z0-9]{1,38}[a-z0-9])?$'   # DNS-1123 safe, 2-40 chars
          ui:autofocus: true
        owner:
          type: string
          ui:field: OwnerPicker            # only real catalog groups selectable
          ui:options: { allowedKinds: [Group] }
```

5. **Treating the platform as done**. Platforms rot without continuous investment; abandon the roadmap and adoption decays as the toolchain ages.

---

## 11. Technologies & Tools

| Tool | Category | Model | Strength | Note |
|---|---|---|---|---|
| Backstage | Developer portal | OSS, plugin-based | Catalog + scaffolder + TechDocs | CNCF incubating; needs a team to run |
| Port | Developer portal | Commercial SaaS | Fast setup, low-code | No-code catalog/blueprints |
| Humanitec | Platform orchestrator | Commercial | Dynamic env config, score | Pairs with Score spec |
| Crossplane | Control-plane IaC | OSS, K8s-native | Continuous reconcile, Claims | Alternative to Terraform |
| Argo CD | GitOps delivery | OSS | Declarative sync, drift detect | Golden-path deploy engine |
| Backstage + Kubernetes plugin | Observability surface | OSS | Per-service pod/deploy view | Ties catalog to runtime |

GCP/Azure context: the portal layer is cloud-agnostic (Backstage runs anywhere); Crossplane has first-class providers for AWS, GCP, and Azure, so the same Claim abstraction can target any cloud by swapping the Composition.

---

## 12. Interview Questions with Answers

**Q: What is an Internal Developer Platform and what problem does it solve?**
An IDP is a self-service layer of tooling and golden paths that lets product engineers ship software without mastering the underlying infrastructure (Kubernetes, Terraform, IAM, networking). It solves the twin problems of inconsistency (every team reinventing deployment differently and insecurely) and the DevOps-as-ticket-queue bottleneck. The goal is to reduce cognitive load on stream-aligned teams so their attention goes to product value, not plumbing.

**Q: Explain "platform-as-product" and why it matters.**
It means treating the platform as a product with real customers (your developers), a roadmap, adoption metrics, and a feedback loop — not as a one-off internal project. It matters because if the golden path is slower or more painful than rolling their own, developers route around it and the platform fails regardless of its technical elegance. Success is measured by adoption and DORA outcomes, not by how clever the internals are.

**Q: What is the difference between a golden path and a golden cage?**
A golden path is the opinionated, fully-supported, easiest route to production that you make so frictionless developers want to use it, while still leaving the underlying primitives accessible for edge cases. A golden cage is an over-rigid path with no escape hatch, forcing teams with legitimate non-standard needs to either suffer or bypass the platform entirely. Always provide an off-ramp; make the right thing easy, not mandatory.

**Q: Describe Backstage's core components.**
The Software Catalog is a graph of components, APIs, systems, and resources described by `catalog-info.yaml` files for ownership and discoverability; the Scaffolder generates new services from parameterized templates (the golden path made executable); TechDocs renders docs-as-code from each repo; and Plugins surface CI/CD, Kubernetes, cost, and security views per service. Together they form a single pane of glass for the developer. Spotify built it to cut onboarding from weeks to hours.

**Q: How does Crossplane differ from Terraform for provisioning?**
Crossplane runs as a control plane inside Kubernetes and continuously reconciles cloud resources from declarative Claims/CRDs, self-healing drift automatically, whereas Terraform is a one-shot `apply` per pipeline run that only corrects drift on the next execution. Crossplane exposes a Kubernetes-native API so developers submit a small Claim and the platform's Composition hides the complexity. Choose Crossplane when you are K8s-native and want continuous reconciliation; choose Terraform for broad existing adoption and a familiar workflow.

**Q: What are the four DORA metrics and what do they measure?**
Deployment frequency (how often you ship), lead time for changes (commit to production), change failure rate (percentage of deploys causing a failure), and time to restore service (MTTR). The first two measure velocity, the last two measure stability — elite performers achieve both simultaneously, deploying multiple times per day with lead time under an hour and change failure rate under 15%. They are the outcome metrics that tell you whether a platform is actually accelerating safe delivery.

**Q: Explain Team Topologies and how it relates to platform engineering.**
Team Topologies defines four team types — stream-aligned (own a product slice end-to-end), platform (provide the IDP as a service), enabling (coach teams on new skills), and complicated-subsystem (own a deep specialty) — plus three interaction modes (collaboration, X-as-a-service, facilitating). The platform team's job is to offer the IDP "as a service" to reduce stream-aligned teams' cognitive load. This framing keeps the platform focused on reducing load rather than becoming a gatekeeper.

**Q: How do you measure whether your platform is succeeding?**
Primarily adoption (what fraction of teams/services use the golden path) and DORA outcomes (rising deployment frequency and shrinking lead time/MTTR/change-failure-rate) for teams on the platform versus off it, plus developer satisfaction surveys. Vanity metrics like "templates created" are misleading because they do not prove the platform reduced anyone's pain. If adoption is low, treat it as a product failure and go talk to your users.

**Q: A team complains the golden path does not fit their use case. How do you respond?**
First treat it as product feedback: understand the gap and decide whether to extend the golden path (if the need is common) or provide a documented, supported escape hatch to the underlying primitives (if it is genuinely an edge case). Forcing them into an ill-fitting path creates a golden cage and erodes trust; blocking them outright pushes them to bypass the platform. The right answer is almost always to keep the primitives accessible while improving the path for the next team with the same need.

**Q: What is a "thinnest viable platform" and when is building Backstage premature?**
The thinnest viable platform is the smallest abstraction that measurably helps — sometimes a wiki page of golden-path docs plus a shared Terraform module library, not a full portal. Building Backstage is premature for a 2–3 team startup where the cognitive-load problem does not yet exist and you lack a team to run the portal; that is gold-plating. Start thin, measure demand, and invest in heavier abstractions only where adoption and pain justify them.

---

## 13. Best Practices

1. **Talk to your developers first** — run interviews and a pilot team before building anything; the platform's requirements come from its users.
2. **Start with the thinnest viable platform** and grow only where demand is proven; resist building a giant portal up front.
3. **Make the golden path the easiest path** so adoption is pull, not push, and always keep an escape hatch to the primitives.
4. **Require ownership in the catalog** — enforce a non-empty `owner` on every component so incident response and discoverability work.
5. **Validate scaffolder inputs** (DNS-safe names, required owner from the catalog) to keep generated services valid and downstream automation unbroken.
6. **Bake security and compliance into the path** (signed images, scanned deps, least-privilege IAM) so the right thing requires no extra effort.
7. **Measure DORA outcomes**, not vanity counts, and compare on-platform versus off-platform teams.
8. **Run the platform as a product** with a roadmap, versioned APIs, deprecation policy, and a public changelog.
9. **Pair golden paths with GitOps and continuous reconciliation** so deployment and provisioning are declarative and self-healing.
10. **Keep investing** — a platform without ongoing investment rots and loses adoption as its toolchain ages.

---

## 14. Case Study

**Scenario**: A 12-team SaaS company has every team writing its own CI pipeline, Helm chart, and Terraform. New-service setup takes 6–9 days, observability is inconsistent, and 4 of 30 services have no clear owner — during a recent incident, responders spent 40 minutes just finding who owned a failing service. The platform team's mandate: cut service creation to under an hour and guarantee every service is owned, observable, and deployed via GitOps.

The team builds a Backstage portal with one golden-path scaffolder template ("standard microservice") that generates a repo pre-wired with CI/CD, a Helm chart, OpenTelemetry, and a `catalog-info.yaml`, then registers it and lets Argo CD ([`../gitops_argocd_flux/README.md`](../gitops_argocd_flux/README.md)) sync it. Provisioning of databases is exposed as a Crossplane Claim.

The first rollout shipped a template that let developers skip the owner field, and within two weeks the catalog had 7 new but unowned services — recreating the exact problem they set out to solve.

```yaml
# BROKEN: owner optional in the golden-path template ->
# orphaned services in the catalog, defeating the incident-ownership goal.
spec:
  parameters:
    - title: New service
      required: [name]                 # owner NOT required
      properties:
        name:  { type: string, pattern: '^[a-z][a-z0-9-]{1,38}[a-z0-9]$' }
        owner: { type: string }        # free-text, optional -> often left blank
```

```yaml
# FIX: owner is required and must be a real catalog Group, and the generated
# catalog-info.yaml fails CI if owner resolves to "unknown".
spec:
  parameters:
    - title: New service
      required: [name, owner]
      properties:
        name:  { type: string, pattern: '^[a-z][a-z0-9-]{1,38}[a-z0-9]$' }
        owner:
          type: string
          ui:field: OwnerPicker
          ui:options: { allowedKinds: [Group] }   # must pick a real team
  steps:
    - id: validate-owner
      name: Enforce ownership
      action: roadiehq:utils:jsonata
      input:
        expression: '$exists(parameters.owner) ? true : $error("owner required")'
```

**Outcome**: After making `owner` a required, catalog-validated field and gating the generated `catalog-info.yaml` in CI, every new service was owned by construction. Within one quarter, service creation dropped from 6–9 days to about 25 minutes, 11 of 12 teams adopted the golden path (the twelfth had a legitimate edge case served by the documented escape hatch to raw Terraform), deployment frequency for on-platform teams rose from weekly to daily, and incident responder time-to-find-owner fell to under 1 minute via the catalog. The platform team now publishes a monthly changelog and tracks adoption and DORA metrics as its primary success indicators.
