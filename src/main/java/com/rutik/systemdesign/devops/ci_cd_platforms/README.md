# CI/CD Platforms

> Phase 3 — CI/CD & GitOps · Difficulty: Intermediate

Knowing CI/CD *principles* (see [ci_cd_fundamentals](../ci_cd_fundamentals/)) is necessary but not sufficient — you must also know the *platforms* that implement them and how to choose between them. GitHub Actions, GitLab CI, Jenkins, and Kubernetes-native engines (Tekton, Argo Workflows) make very different tradeoffs around hosting, configuration model, ecosystem, and where they run. This module compares them and covers the cross-cutting concerns (runners, secrets, reusability) that apply to all.

---

## 1. Concept Overview

A CI/CD platform provides: a way to **define pipelines** (YAML/Groovy/DSL), **runners/agents** that execute jobs, **triggers** (push, PR, schedule, manual, webhook), **secret management**, an **artifact/cache store**, and an **extension ecosystem** (actions/plugins/tasks).

The major families:
- **SCM-integrated SaaS** — GitHub Actions, GitLab CI. Pipeline lives next to code; tight VCS integration; managed runners (plus self-hosted option).
- **Standalone server** — Jenkins. Self-hosted, infinitely extensible via plugins, language-agnostic; you operate it.
- **Kubernetes-native** — Tekton, Argo Workflows. Pipelines are Kubernetes CRDs; each step is a Pod; ideal when your platform *is* Kubernetes.
- **Other SaaS** — CircleCI, Buildkite, Drone, etc.

The choice hinges on: where your code lives, whether you want to operate the system, how much extensibility/customization you need, and whether you want pipelines running as first-class Kubernetes workloads.

---

## 2. Intuition

> **One-line analogy**: CI/CD platforms are like kitchens. GitHub Actions/GitLab CI are a fully-equipped rental kitchen attached to your apartment (code) — convenient, stocked, you just cook. Jenkins is a warehouse where you build and wire every appliance yourself — ultimate flexibility, but you maintain the plumbing. Tekton/Argo is a kitchen *inside your existing restaurant* (Kubernetes) — same staff, tools, and supply chain as everything else you run.

**Mental model**: Every platform reduces to "events trigger jobs that run on runners and produce artifacts," but they differ in *who operates the runners and control plane*, *how pipelines are expressed*, and *how much you assemble yourself*. SaaS platforms trade flexibility/control for zero-ops convenience; Jenkins trades convenience for unlimited extensibility; Kubernetes-native trades simplicity for unifying CI/CD with your existing cluster ops.

**Why it matters**: The platform shapes your entire delivery workflow — config model, secret handling, runner scaling, and cost. Picking the wrong one (e.g., heavyweight Jenkins for a small GitHub-hosted team, or self-managed runners you can't keep patched) creates ongoing toil. The platform is also a security boundary: it holds deploy credentials and runs arbitrary code.

**Key insight**: For most teams the dominant factor is **where your code already lives and whether you want to run infrastructure**. GitHub repo + don't-want-ops → GitHub Actions. GitLab → GitLab CI. Heavy customization/legacy/on-prem → Jenkins. Platform *is* Kubernetes and you want pipelines as cluster workloads → Tekton/Argo. Don't over-think it past those axes.

---

## 3. Core Principles

1. **Pipeline as code, in the repo.** Versioned, reviewed config — true of all modern platforms.
2. **Match the platform to your SCM and ops appetite.** SaaS for low-ops; Jenkins for control; K8s-native for cluster-centric.
3. **Ephemeral runners by default.** Clean isolation per job (see [ci_cd_fundamentals](../ci_cd_fundamentals/)).
4. **Reuse, don't repeat.** Reusable workflows / shared libraries / templates avoid copy-paste pipelines.
5. **Secrets via the platform store + OIDC.** No long-lived cloud keys; least-privilege deploy creds.
6. **The platform is a high-value attack target.** It holds deploy credentials and runs your code — secure it.

---

## 4. Types / Architectures / Strategies

### Platform comparison

| Platform | Hosting | Config | Runners | Best for |
|----------|---------|--------|---------|----------|
| GitHub Actions | SaaS (+ self-hosted runners) | YAML in `.github/workflows` | GitHub-hosted or self | GitHub repos, marketplace ecosystem |
| GitLab CI | SaaS or self-hosted | `.gitlab-ci.yml` | Shared or self-managed | GitLab repos, all-in-one DevOps |
| Jenkins | Self-hosted | Groovy `Jenkinsfile` | Static/dynamic agents | Max extensibility, legacy/on-prem, any language |
| Tekton | In Kubernetes | CRDs (Task/Pipeline) | Pods | K8s-native, reusable Tasks |
| Argo Workflows | In Kubernetes | CRD (Workflow DAG) | Pods | Complex DAGs, ML/data pipelines on K8s |
| CircleCI | SaaS | `config.yml` | Cloud/self | Fast SaaS, orbs ecosystem |

### Reusability mechanisms

| Platform | Reuse unit |
|----------|-----------|
| GitHub Actions | Reusable workflows, composite actions, marketplace actions |
| GitLab CI | `include:`, `extends:`, templates |
| Jenkins | Shared libraries, plugins |
| Tekton | Reusable `Task`s (Artifact Hub) |

---

## 5. Architecture Diagrams

```
SaaS-integrated (GitHub Actions / GitLab CI)

  push/PR -> SCM webhook -> platform control plane -> dispatch job
                                                        |
                            +---------------------------+
                            v
                     ephemeral runner (cloud-hosted or self-hosted)
                            | runs steps, uses cached deps, OIDC to cloud
                            v
                     artifacts -> registry;  status -> back to PR

Kubernetes-native (Tekton/Argo)

  trigger (webhook/EventListener) -> create PipelineRun CR
        |
        v
  Tekton controller schedules each Task as a Pod in the cluster
        |  (same RBAC, secrets, scaling, observability as your apps)
        v
  Pods run steps -> push artifact -> update PipelineRun status
```

---

## 6. How It Works — Detailed Mechanics

### GitHub Actions: reusable workflow (DRY across repos)

```yaml
# .github/workflows/reusable-build.yml  (called by many repos)
on: {workflow_call: {inputs: {image: {required: true, type: string}}}}
jobs:
  build:
    runs-on: ubuntu-latest
    permissions: {id-token: write, contents: read}   # OIDC for keyless cloud auth
    steps:
      - uses: actions/checkout@v4
      - uses: docker/build-push-action@v6
        with: {push: true, tags: "${{ inputs.image }}:${{ github.sha }}", cache-from: type=gha, cache-to: "type=gha,mode=max"}
---
# caller repo workflow:
jobs:
  ci: {uses: org/.github/.github/workflows/reusable-build.yml@v1, with: {image: registry/app}}
```

### GitLab CI: templates + DAG

```yaml
# .gitlab-ci.yml
include: {project: org/ci-templates, file: build.yml}    # shared template
stages: [test, build, deploy]
test:  {stage: test, script: ["npm ci", "npm test"], cache: {paths: [node_modules/]}}
build: {stage: build, needs: [test], script: ["docker build -t $IMG:$CI_COMMIT_SHA ."]}   # needs = DAG, not just stage order
deploy:{stage: deploy, needs: [build], environment: production, when: manual, script: ["deploy.sh $IMG:$CI_COMMIT_SHA"]}
```

### Jenkins: declarative pipeline + shared library

```groovy
// Jenkinsfile
@Library('org-shared') _      // reusable steps from a shared library
pipeline {
  agent { kubernetes { yaml podTemplate() } }   // dynamic K8s agent per build
  stages {
    stage('Test')  { steps { sh 'npm ci && npm test' } }
    stage('Build') { steps { buildAndPush(image: 'registry/app', tag: env.GIT_COMMIT) } } // shared-lib step
    stage('Deploy'){ when { branch 'main' }; steps { input 'Deploy to prod?'; sh 'deploy.sh' } }
  }
  post { failure { notifySlack() } }
}
```

### Tekton: pipelines as Kubernetes CRDs

```yaml
apiVersion: tekton.dev/v1
kind: Pipeline
metadata: {name: build-deploy}
spec:
  tasks:
    - name: test  ; taskRef: {name: npm-test}        # reusable Task
    - name: build ; runAfter: [test] ; taskRef: {name: kaniko-build}   # in-cluster image build
    - name: deploy; runAfter: [build]; taskRef: {name: kubectl-apply}
# A PipelineRun executes this; each Task runs as a Pod with cluster RBAC/secrets.
```

### Self-hosted runner autoscaling (the ops concern)

```
Demand spikes -> need more runners. Options:
  - GitHub Actions Runner Controller (ARC) on K8s: scale ephemeral runner Pods on queue depth
  - GitLab Kubernetes executor: each job in a fresh Pod
  - Jenkins Kubernetes plugin: dynamic agent Pods
Keep runners ephemeral + autoscaled so you don't pay for idle and don't accumulate state.
```

---

## 7. Real-World Examples

- **GitHub Actions + ARC (Actions Runner Controller)**: orgs run autoscaling ephemeral self-hosted runners as Kubernetes Pods — combining Actions' ecosystem with their own compute and network access to private resources.
- **GitLab all-in-one**: teams use GitLab for SCM + CI + registry + security scanning in one platform, with the Kubernetes executor spawning a fresh Pod per job.
- **Jenkins in the enterprise**: long-standing on-prem/regulated environments rely on Jenkins' plugin ecosystem and language-agnostic agents — at the cost of operating (and securing/patching) the server.
- **Tekton/Argo for platform teams**: organizations building internal delivery platforms express pipelines as CRDs so CI/CD shares the cluster's RBAC, secrets, autoscaling, and observability with everything else they run (see [platform_engineering_and_idp](../platform_engineering_and_idp/)).

---

## 8. Tradeoffs

| Decision | Option A | Option B | Key factor |
|----------|----------|----------|-----------|
| Hosting | SaaS (no ops) | Self-hosted (control, private access) | Ops appetite, network/compliance |
| Config model | YAML (simple, declarative) | Groovy/code (Jenkins, flexible) | Simplicity vs programmability |
| Where it runs | External runners | Kubernetes-native (Tekton/Argo) | Cluster integration |
| Extensibility | Marketplace/orbs (curated) | Jenkins plugins (vast, variable quality) | Need vs maintenance/security |
| Lock-in | Tied to SCM (Actions/GitLab) | Portable (Jenkins/Tekton) | Flexibility vs convenience |
| Runner scaling | Managed autoscale | Self-managed (ARC/K8s) | Cost vs control |

---

## 9. When to Use / When NOT to Use

**GitHub Actions / GitLab CI**: your code is on GitHub/GitLab and you want low-ops, fast setup, strong VCS integration. **Jenkins**: you need maximum extensibility, run on-prem/regulated, support many languages/legacy, or already have deep Jenkins investment. **Tekton/Argo Workflows**: your platform is Kubernetes and you want pipelines as cluster-native workloads with shared RBAC/observability.

**Avoid:** running Jenkins for a small GitHub team that just needs build+test+deploy (operational overhead with no payoff); adopting Kubernetes-native CI before you have a Kubernetes platform; or piling on unvetted Jenkins plugins (security and maintenance debt).

---

## 10. Common Pitfalls

**Pitfall 1 — Self-hosted runners that persist state (and bleed secrets).**

```yaml
# BROKEN: a long-lived self-hosted runner reused across jobs accumulates files, caches,
# and credentials; one repo's job can read artifacts/secrets left by another -> breach + flaky builds.
runs-on: self-hosted        # static, persistent VM shared across many repos/jobs
```

```yaml
# FIX: ephemeral runners (one job per runner, then destroyed), e.g., ARC on Kubernetes.
runs-on: [self-hosted, ephemeral]    # ARC provisions a fresh Pod per job, deletes after
# Each job gets a clean filesystem; no cross-job secret/state leakage.
```

**Pitfall 2 — Copy-pasted pipelines across dozens of repos.** A change to the build process means editing N repos, and they drift. FIX: reusable workflows (Actions), `include`/`extends` (GitLab), or shared libraries (Jenkins) — define once, reference everywhere (see the DRY mechanisms in §4).

**Pitfall 3 — Over-broad CI credentials / unpatched Jenkins.** CI holds deploy credentials and runs arbitrary code; a compromised pipeline or vulnerable Jenkins plugin is a direct path to production. FIX: OIDC + least-privilege deploy roles (no static admin keys), pin and review third-party actions/plugins, patch the control plane, and isolate runners (see [devsecops_and_supply_chain_security](../devsecops_and_supply_chain_security/)).

---

## 11. Technologies & Tools

| Tool | Purpose |
|------|---------|
| GitHub Actions + ARC | SaaS CI/CD; autoscaling self-hosted runners |
| GitLab CI | All-in-one SCM + CI/CD + registry |
| Jenkins + Kubernetes plugin | Extensible self-hosted CI; dynamic agents |
| Tekton | Kubernetes-native pipelines (CRDs, reusable Tasks) |
| Argo Workflows | K8s-native DAG workflows (CI, ML, data) |
| CircleCI / Buildkite | SaaS alternatives |
| act | Run GitHub Actions locally |
| Renovate / Dependabot | Keep pipeline action/plugin versions current |

---

## 12. Interview Questions with Answers

**Q1: How do you choose a CI/CD platform?**
Primarily by where your code lives and your ops appetite: GitHub → GitHub Actions, GitLab → GitLab CI (low-ops, tight integration); need maximum extensibility / on-prem / many languages → Jenkins (you operate it); platform is Kubernetes and you want pipelines as cluster workloads → Tekton/Argo. Secondary factors: ecosystem, secret handling, runner scaling, cost, and lock-in tolerance.

**Q2: SaaS (GitHub Actions/GitLab CI) vs Jenkins — core tradeoff?**
SaaS platforms are low-ops (no control plane to run/patch), tightly integrated with the SCM, and YAML-configured, at the cost of flexibility and some lock-in. Jenkins is self-hosted and infinitely extensible via plugins and Groovy pipelines (any language, on-prem, custom workflows), at the cost of operating, securing, and patching the server and its plugin sprawl. Choose SaaS for convenience, Jenkins for control.

**Q3: What's distinctive about Kubernetes-native CI (Tekton/Argo)?**
Pipelines are Kubernetes CRDs and each step runs as a Pod, so CI/CD shares the cluster's RBAC, secrets, autoscaling, networking, and observability with your applications — no separate runner fleet to manage. It's ideal when your platform is already Kubernetes. The tradeoff is added conceptual complexity and that you need a cluster to run pipelines at all.

**Q4: Why insist on ephemeral runners regardless of platform?**
Ephemeral runners (fresh container/VM per job, destroyed after) guarantee reproducibility and isolation: no leftover state causes "passes only because of a previous build," and no cross-job leakage of files, caches, or credentials. Persistent self-hosted runners shared across repos are a security and flakiness liability. Autoscaling ephemeral runners (e.g., ARC on Kubernetes) also avoids paying for idle capacity.

**Q5: How do you avoid copy-pasted pipelines across many repos?**
Use the platform's reuse mechanism: reusable workflows and composite actions (GitHub Actions), `include`/`extends`/templates (GitLab), or shared libraries (Jenkins), and reusable Tasks (Tekton). Define the build/test/deploy logic once in a central, versioned location and reference it from each repo, so a single change updates everyone and pipelines can't drift.

**Q6: Why is the CI/CD platform a high-value security target?**
It holds the credentials to deploy to production and executes arbitrary code (your build scripts, third-party actions/plugins). A compromised pipeline, a malicious dependency in a build, or a vulnerable Jenkins plugin is a direct route to prod. Defenses: OIDC + least-privilege deploy roles (no static admin keys), pinned/reviewed third-party actions, patched control plane, isolated ephemeral runners, and secret masking.

**Q7: How does OIDC improve CI security over stored cloud keys?**
With OIDC federation, the runner presents a short-lived, signed identity token to the cloud, which exchanges it for temporary scoped credentials for a specific IAM role — so no long-lived cloud access keys are stored in the CI platform at all. This eliminates the most commonly leaked secret class and limits blast radius (the role is scoped and the credentials expire).

**Q8: What does "pipeline as code" buy you, and do all platforms support it?**
It means the pipeline definition lives in the repo (YAML/Groovy/CRD), versioned and code-reviewed alongside the application. Benefits: history/auditability, review of pipeline changes, reproducibility, and the ability to branch pipeline changes. All modern platforms support it — GitHub `.github/workflows`, GitLab `.gitlab-ci.yml`, Jenkins `Jenkinsfile`, Tekton CRDs — though older Jenkins setups with UI-clicked freestyle jobs are an anti-pattern to migrate away from.

**Q9: How do you scale self-hosted runners with demand?**
Autoscale ephemeral runners on a Kubernetes cluster: GitHub's Actions Runner Controller (ARC) provisions runner Pods based on queue depth and tears them down after each job; GitLab's Kubernetes executor and Jenkins' Kubernetes plugin do the equivalent. This gives clean isolation, elastic capacity for spikes, and no payment for idle runners — versus a fixed pool that's either over- or under-provisioned.

**Q10: GitLab CI `needs:` vs stages — what's the difference?**
Stages enforce sequential ordering (all `test` jobs finish before any `build` job starts). `needs:` creates a directed acyclic graph: a job starts as soon as its specific dependencies complete, regardless of stage, enabling more parallelism and faster pipelines. For example, a `deploy-docs` job needing only `build-docs` can run while unrelated test jobs are still going, rather than waiting for the whole `test` stage.

---

## 13. Best Practices

- Choose by **SCM + ops appetite**; don't run Jenkins for a small SaaS-hosted team.
- Keep pipelines **as code** in the repo; **reuse** via reusable workflows/templates/shared libraries.
- Use **ephemeral, autoscaled runners** (ARC/K8s executors); never share persistent runners across repos.
- Secrets via the platform store + **OIDC**; least-privilege deploy roles; mask all secret output.
- **Pin and review** third-party actions/plugins; **patch** self-hosted control planes.
- Prefer **Kubernetes-native CI** only when you already operate Kubernetes as your platform.
- Keep the **artifact build once** and consistent across platforms (see [ci_cd_fundamentals](../ci_cd_fundamentals/)).

---

## 14. Case Study

### Scenario: A shared self-hosted Jenkins runner leaks one team's secrets to another

A company runs a few persistent self-hosted Jenkins agents shared across 40 repos to save cost. A build for team A's repo writes a cloud credential to the agent's workspace and a temp file. Later, team B's job (a forked PR build) runs on the same agent, reads the leftover credential, and exfiltrates it. Builds are also intermittently flaky from stale `node_modules` left by prior jobs.

```
BROKEN: persistent shared agents
  agent-1 (long-lived VM, shared by 40 repos)
     job A: writes ~/.aws/credentials, /tmp/build-cache, node_modules
     job B (fork PR, untrusted): same FS -> reads A's creds + stale deps
  -> cross-team secret leak + flaky builds from state bleed
```

```yaml
# FIX: ephemeral, isolated agents per job via the Jenkins Kubernetes plugin,
# OIDC/short-lived creds instead of files, and no fork-PR builds on trusted runners.
pipeline {
  agent {
    kubernetes {                 // a FRESH Pod per build, destroyed after
      yaml '''
        apiVersion: v1
        kind: Pod
        spec:
          serviceAccountName: ci-build      # IRSA -> short-lived scoped cloud creds, no files
          containers:
            - name: build
              image: registry/ci-toolbox@sha256:...   # pinned toolchain
      '''
    }
  }
  stages { stage('Build') { steps { sh 'npm ci && npm test && build.sh' } } }
}
// Org policy: fork/untrusted PRs run only on disposable runners with NO deploy credentials.
```

After the change, each build runs in a fresh Pod with a clean filesystem and obtains cloud access via IRSA (short-lived, scoped) rather than a credentials file that can be left behind — so there's nothing for a later job to scavenge, and stale-state flakiness disappears. Untrusted fork PRs are isolated to credential-free runners.

**Outcome:** the cross-team credential-leak class was eliminated (no persistent filesystem, no static credential files), build flakiness from leftover state dropped to near zero, and the move to per-job Pods also gave elastic capacity. The lesson: shared persistent runners are a false economy — ephemeral isolation is both safer and more reliable.

**Discussion questions:**
1. Why do persistent shared runners create *both* a security and a reliability problem?
2. How does IRSA/OIDC remove the specific thing that was leaked (a credentials file)?
3. Why should untrusted fork PRs run on credential-free, isolated runners, and how do platforms gate this?

---

**Cross-references:** [ci_cd_fundamentals](../ci_cd_fundamentals/) (principles these platforms implement), [deployment_strategies](../deployment_strategies/) (what the deploy stage does), [gitops_argocd_flux](../gitops_argocd_flux/) (pull-based CD as an alternative deploy model), [kubernetes_workloads_and_objects](../kubernetes_workloads_and_objects/) (runners as Pods), [devsecops_and_supply_chain_security](../devsecops_and_supply_chain_security/) (securing the pipeline + third-party actions).
