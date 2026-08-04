# DevOps Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/devops/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.
Build manifest / status tracker: `README.md` §8 — check NEXT UP pointer and per-file status before starting a new module.

## Scope — unparked 2026-08-04; the factual audit is COMPLETE

Parked 2026-07-29, re-opened 2026-08-04, and audited the same day.

| Work | State |
|------|-------|
| Factual audit | **DONE — all 41 modules**, ~1,560 claims verified against upstream docs, ~215 corrections. Commits `7f98bf4`, `9462731`, `ecd9414` |
| `**Short:**` MCQ summaries | **650 outstanding (of 650)** — runs next, and deliberately AFTER the audit |
| Case studies | **not audited.** 13 case studies + `cross_cutting/`; only one defect was fixed there opportunistically (4 invalid HCL blocks in `design_multi_region_dr_architecture.md`) |

**Why the Short migration comes second here.** A `**Short:**` line written against an
answer the audit later corrects becomes a wrong answer shipping as the CORRECT MCQ option,
and no check catches it. See root `CLAUDE.md` -> "ORDER: audit a version-sensitive section
BEFORE migrating its Shorts". This section is the most version-sensitive in the repo, so
the ordering mattered most here.

### What the audit found, for whoever writes the next module

Three classes, and only the first is what people expect an audit to be:

1. **Currency** — IPVS deprecated (v1.35, nftables is the recommended mode), ingress-nginx
   retired March 2026, Helm 4, CDKTF archived, Crossplane v2 removed Claims, Strimzi
   requires KafkaNodePool, SLSA has no Build-track L4, OPA v1 Rego, DORA is five metrics,
   and a long list of dead products (Opsgenie, Grafana OnCall OSS, Kaniko, Drone, Jeli,
   Lightstep, Deployment Manager, tfsec, Promtail, Bitnami charts).
2. **Wrong numbers in modules nobody suspected** — an etcd quota given as 8 GB default in
   six places (it is 2 GiB), a Prometheus `scrape_interval` default of 15s in five places
   (it is 1m), NAT exhaustion modelled per 5-tuple instead of per destination, a GPU
   module whose monthly cost was 4x wrong on top of a per-GPU decimal slip that fed five
   downstream tables, three wrong CIS control IDs.
3. **Code examples that cannot run** — 15 invalid one-line HCL blocks across two syntaxes
   (`;` and `,`), a Tekton YAML collapsing into a single string, a removed OTel exporter,
   `set -o pipefail` documented as returning the FIRST non-zero exit, `--force-with-lease`
   advice that was exactly inverted, a Rego rule matching a field the resource no longer
   has, and a "build once, promote the same artifact" fix passing a digest through
   `$GITHUB_ENV`, which is job-scoped.

**The lesson for authors: class 2 and 3 outnumbered class 1 and are invisible to a
"is this still current" reading.** Recompute the arithmetic and parse the code.

### Deliberately left unverified

Do not "fix" these by substituting a plausible number — each was checked and could not be
sourced: DORA's benchmark bands (dora.dev no longer publishes them; the stale 15% was
removed, not replaced), Salesforce/Hyperforce's claimed 4-hour RTO, Karpenter's 30-50%
savings, Trivy's DB size, Loki/ELK compression ratios, and Kafka's ~4,000
partitions-per-broker ceiling.

Two NEGATIVE results worth keeping, because both look stale and are not: **Docker Hub's
100 pulls / 6h is still correct** (the announced 10/hour change was reverted), and **Argo
CD 3.x has not removed ApplicationSet fasttemplate** (`goTemplate: true` remains opt-in).

---

## Module List — 41 Modules (8 Phases)

AWS is the default cloud in worked examples; GCP/Azure appear in comparison tables. This section deliberately **cross-references** `backend/`, `database/`, and `hld/` instead of duplicating them — see the non-overlap boundary in `README.md` §2.

| Phase | Modules |
|-------|---------|
| 1 — Foundations | linux_and_os_fundamentals, shell_scripting_and_automation, networking_for_devops, version_control_and_git_workflows |
| 2 — Containers & Kubernetes | containers_and_docker, container_runtimes_and_oci, kubernetes_architecture, kubernetes_workloads_and_objects, kubernetes_networking, kubernetes_storage_and_state, kubernetes_scheduling_and_autoscaling, kubernetes_security, helm_and_package_management, kubernetes_operators_and_crds |
| 3 — CI/CD & GitOps | ci_cd_fundamentals, ci_cd_platforms, deployment_strategies, gitops_argocd_flux, artifact_and_registry_management |
| 4 — IaC & Config | infrastructure_as_code_terraform, terraform_advanced_and_alternatives, configuration_management, secrets_management |
| 5 — Cloud (AWS-primary) | cloud_fundamentals_and_aws, gcp_and_azure_essentials, serverless_and_faas, cloud_networking_and_cdn, cloud_cost_optimization_finops |
| 6 — Observability & SRE | observability_metrics_prometheus, observability_logging, observability_tracing_and_otel, visualization_and_alerting, sre_principles_and_slos, incident_management_and_oncall |
| 7 — DevSecOps & Reliability | devsecops_and_supply_chain_security, policy_as_code_and_compliance, disaster_recovery_and_resilience, platform_engineering_and_idp |
| 8 — Specialized Platforms & Performance | ml_platform_and_gpu_infrastructure, event_streaming_operations, performance_and_load_testing |

**Q&A floor:** 15 per module (root CLAUDE.md hard floor). Deep modules aim higher (18+): kubernetes_architecture, kubernetes_networking, kubernetes_security, infrastructure_as_code_terraform, observability_metrics_prometheus, sre_principles_and_slos, ml_platform_and_gpu_infrastructure

Backfill status (2026-07-16): the floor backlog is fully cleared — every module repo-wide now meets the 15-question floor (all former 11-14 modules raised to 16 across backend, database, devops, fastapi, hld, and python).

---

## Learning Paths (Full + Senior)

`README.md` documents the **Full Path** (all 41 modules = "8-Phase Learning Path") plus
one curated tier: **Senior** (22 modules). **This section has no Principal tier and needs
none** — no module declares one, `check_wiring()` skips the tier when the markers declare
zero modules for it, and adding a Principal heading with no members is a false alarm, not
a gap to close. Membership is declared ONCE per module, in a `<!-- study-paths -->` block
in that module's own page (`<module>.md`) naming the files each tier takes; listing a tier joins it,
omitting the tier opts out, and the module page (`<module>.md`) must always be listed. Order is never
declared — it comes from `STUDY_ORDER.devops` in `game/app.js`, so a tier is an ordered
subset by construction. **There is no path array in `app.js` to edit**: `extract.py` walks
the markers and emits the gitignored `questions/paths.json`, which the game fetches at
boot. The Senior table in `README.md` sits between `<!-- study-path-table senior -->` /
`<!-- /study-path-table -->` and is **generated** — regenerate with
`python3 game/extract.py --write-paths`; a hand-edited or stale block fails
`extract.py --strict` and the Pages deploy. The 13 case studies carry no tier markers, so
the Case Studies tab shows all of them with no Level filter. The README also carries a
Knowledge-Question Map and a 6-week Study Plan (prose; no path impact).

---

## Case Studies — 13 Total

`case_studies/` — all use the 11-section principal template.
Reference: `../llm/case_studies/design_gpu_inference_platform.md`
Learning-path index: `case_studies/case_studies.md` (mandatory; update with every new case study).

design_ci_cd_platform, design_kubernetes_platform, design_observability_platform, design_gitops_delivery_pipeline, design_secrets_management_platform, design_multi_region_dr_architecture, design_autoscaling_platform, design_log_aggregation_pipeline, design_internal_developer_platform, design_incident_response_system, design_container_registry, design_zero_downtime_infra_migration, design_ml_platform_infrastructure

---

## Cross-Cutting Shared Primitives — 7 Files

`case_studies/cross_cutting/` — all use the 14-section template:

| File | When Relevant |
|------|--------------|
| `kubernetes_production_hardening/` | Any case study deploying to Kubernetes |
| `terraform_state_at_scale/` | Any case study using infrastructure as code |
| `prometheus_cardinality_and_scale/` | Any observability case study |
| `slo_error_budget_math/` | Any case study with SRE/reliability requirements |
| `supply_chain_security_pipeline/` | Any CI/CD case study |
| `multi_cluster_networking/` | Any multi-region or multi-cluster case study |
| `gpu_node_lifecycle/` | Any ML platform or GPU infrastructure case study |

---

## Cross-Reference Map

| DevOps Module | See Also (other sections) |
|--------------|--------------------------|
| `kubernetes_workloads_and_objects/` | `../../backend/container_and_deployment_patterns/` — 12-factor, K8s strategies |
| `observability_tracing_and_otel/` | `../../backend/observability_and_monitoring/` — Micrometer, MDC; `../../spring/observability_and_tracing/` |
| `event_streaming_operations/` | `../../backend/kafka_deep_dive/` — Kafka internals; `../../backend/event_driven_fundamentals/` |
| `ml_platform_and_gpu_infrastructure/` | `../../ml/gpu_and_hardware_optimization/` — CUDA profiling; `../../ml/mlops_and_ci_cd/` |

---

## Content Rules (DevOps-specific)

- Code in realistic YAML / HCL / Dockerfile / Bash / PromQL / Rego / Go
- AWS-default with GCP/Azure in comparison tables
- Concrete numbers everywhere; at least 1 BROKEN→FIX block in §10 and §14
- No emojis; ASCII diagrams only

## Build Manifest

See `README.md` §8 for the authoritative per-file `pending`/`done` status table and NEXT UP pointer.

On finishing a module/chunk:
1. Flip status to `done` in `README.md` §8
2. Advance NEXT UP pointer
3. Update `case_studies/case_studies.md` + root `README.md` + this CLAUDE.md if applicable

## Adding a New DevOps Module

1. Create `<module_name>/<module_name>.md` — 14-section template; 15 Q&As minimum (root CLAUDE.md hard floor; 18+ for deep modules listed above)
2. Follow DevOps-specific content rules above
3. Update `README.md` module table AND flip the file's status in the §8 build manifest
4. Add the module dir to `STUDY_ORDER.devops` in `game/app.js` at its phase position — a
   module missing from it falls to the 9999 sort (dead-last in Study) and fails `--strict`
5. Write a `<!-- study-paths -->` block at the top of the new module's page (`<module_name>.md`) with a `senior:` line listing `<module_name>.md` itself plus any sub-files if
   it belongs in that tier (omit the block entirely for Full-path-only; do not invent a
   `principal:` line — this section has no Principal tier), then run
   `python3 game/extract.py --write-paths` to regenerate `README.md`'s Senior table
6. Update root `README.md` DevOps phase table
7. Update root `CLAUDE.md` DevOps module table

---

## Visual Intuition Diagrams

Section 5 (Architecture Diagrams) and any hard-to-picture concept should use an
**ASCII visual intuition diagram** that makes an abstract relationship visible
(constraint grid, before/after-with-delta, stacked flow, routing fan-out, bar
chart, or curve/sketch). Generate and validate them with the
`/visual-intuition-diagrams` skill. The full archetype catalog, conventions
(ASCII only, no tabs, no emojis, widest line <= 100 cols, caption every diagram),
and the `diagram_tools.py` validator live in root `CLAUDE.md` -> "Visual Intuition
Diagrams".
