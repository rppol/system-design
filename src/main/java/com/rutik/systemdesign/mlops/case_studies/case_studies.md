# MLOps Case Studies - Learning Path

Eight case studies. Each designs the system that runs one mlops module at fleet scale: many features, many tenants, many devices, or many models at once. They stop where the neighboring sections start. The eval platform, the LLM gateway and the GPU inference platform themselves are in [llm/case_studies](../../llm/case_studies/case_studies.md). The ML platform and the experimentation platform are in [ml/case_studies](../../ml/case_studies/case_studies.md). The GPU cluster and ML platform infrastructure are in [devops/case_studies](../../devops/case_studies/case_studies.md).

Every study uses the 11-section principal template and cites at least 4 `cross_cutting/` files. The shared reference for this section is [ai_release_bundle_and_lineage](cross_cutting/ai_release_bundle_and_lineage.md): all 8 studies key their design on the bundle digest it defines.

Tiers come from the `<!-- study-paths -->` block in [the section README](../README.md). `-` means Full path only.

---

## 1. Quick Start

| Case Study | Tiers | Why read it first |
|---|---|---|
| [Design an AI Release Pipeline](design_an_ai_release_pipeline.md) | senior, principal | The release ladder (gates, shadow, canary, A/B, rollback) for 40 features and about 300 bundles a week. Every other study reuses its bundle, gate and rollback vocabulary. |
| [Design an Embedding Model Migration](design_an_embedding_model_migration.md) | senior, principal | Shows that the re-embed GPU bill is the cheap part and the dual-index window is the costly one. The same reasoning applies to any artifact that two other artifacts must stay paired with. |
| [Design a Model Weight Distribution System](design_a_model_weight_distribution_system.md) | senior | Cold start as a bandwidth budget with a fixed and a variable part. Gives the numbers behind every "scale from zero" claim in the serving modules. |

---

## 2. Full Learning Path

Grouped by engineering concern. Within a group, read in the order given.

### Release and refresh: moving a new artifact into production

| Case Study | Tiers | Concern | Summary |
|---|---|---|---|
| [Design an AI Release Pipeline](design_an_ai_release_pipeline.md) | senior, principal | Evidence and reversibility as budgets | About 190 release ladders run at once on shared traffic, a shared eval budget and a moving champion. Sizes each rung to the question it answers and reuses champion scores, so evidence costs about 5% of production inference instead of about 46%. |
| [Design an Embedding Model Migration](design_an_embedding_model_migration.md) | senior, principal | Dual-index window | Moves 2B chunks to a new embedding model with zero downtime. Covers the re-embed backfill (about 5,400 GPU-hours), dual writes, deletes, ACL changes and erasure that must reach both indexes, shadow recall gates per tenant, cutover and rollback. |
| [Design a Continuous Training and Champion-Challenger System](design_a_continuous_training_and_champion_challenger_system.md) | principal | Label delay and selection bias | A scoring model whose labels arrive 30 days late. Trains often, shadows each challenger beside the champion, buys an unbiased view of the decline region with a priced exploration slice, and promotes the whole decision policy (model, calibration, thresholds, actions) as one versioned unit. |

### Serving and delivery: getting bytes onto hardware

| Case Study | Tiers | Concern | Summary |
|---|---|---|---|
| [Design a Model Weight Distribution System](design_a_model_weight_distribution_system.md) | senior | Cold start at fleet scale | 0 to 64 nodes of a frontier MoE in under 3 minutes. Shards the checkpoint per rank, spreads bytes peer to peer so the origin serves each byte once, and pre-seeds NVMe caches when the release is cut, using OCI artifacts and a streaming loader. |
| [Design a Multi-Tenant LoRA Serving Platform](design_a_multi_tenant_lora_serving_platform.md) | senior | Base digest as the partition key | 5,000 customer adapters over 3 base models. Adapter registry keyed by base digest, node sync, affinity routing that avoids hot pods, tenant isolation, and a base-upgrade campaign that moves every adapter to a new digest. |
| [Design an On-Device Model Delivery System](design_an_on_device_model_delivery_system.md) | - | Fleet as an eventually consistent store | A 3B model plus adapters to 50M phones. Signed manifests per cohort and device class, CDN chunks and OTA deltas, cohort rollout, a device matrix, a cloud fallback sized for a kill switch, and telemetry that never reads user text. |

### Operating and governing: keeping it correct after launch

| Case Study | Tiers | Concern | Summary |
|---|---|---|---|
| [Design an AI Quality Incident Response Program](design_an_ai_quality_incident_response_program.md) | principal | Quality incidents with no error | A provider moves a model alias and task success on one feature falls from 92.0% to 84.6% with every health check green. Designs task-success SLOs, change-point detection on the response model id, attribution, a model-id kill switch, pinned snapshots, re-qualification and communications. |
| [Design an AI Governance and Model Inventory Platform](design_an_ai_governance_and_model_inventory_platform.md) | principal | Validator hours as the scarce resource | A bank with 1,200 models and 300 GenAI use cases. Builds the inventory from discovery and the release pipeline, tiers risk, routes changes so validation demand falls from about 61,000 to about 6,500 hours a month, and answers EU AI Act and model-risk audit questions with joins on the bundle digest. |

---

## 3. Cross-Cutting / Shared Primitives

The mlops file lives in `cross_cutting/`, which is in `CS_EXCLUDE_DIRS`: it is not listed in the README `<!-- study-paths -->` block, and its Q&As never enter the question bank. The other rows are shared references owned by other sections.

| File | Becomes relevant when | Cited by |
|---|---|---|
| [ai_release_bundle_and_lineage](cross_cutting/ai_release_bundle_and_lineage.md) | Release and refresh: the first time two artifacts must ship as one unit and an audit must find which bytes ran | All 8 studies |
| [llm_eval_harness_in_production](../../llm/case_studies/cross_cutting/llm_eval_harness_in_production.md) | Release and refresh: a gate needs a repeatable eval run | Release pipeline, embedding migration, LoRA serving, quality incident |
| [gpu_pool_economics](../../llm/case_studies/cross_cutting/gpu_pool_economics.md) | Serving and delivery: sizing a backfill or a warm pool in GPU-hours | Embedding migration, LoRA serving, weight distribution |
| [tenant_isolation_patterns](../../llm/case_studies/cross_cutting/tenant_isolation_patterns.md) | Serving and delivery: one pool serves many customers | Embedding migration, LoRA serving |
| [opentelemetry_for_llm_apps](../../llm/case_studies/cross_cutting/opentelemetry_for_llm_apps.md) | Operating and governing: traces must carry the bundle digest and model id | Release pipeline, quality incident, governance, on-device |
| [red_team_eval_harness](../../llm/case_studies/cross_cutting/red_team_eval_harness.md) | Operating and governing: safety evidence for an approval or a re-qualification | Quality incident, governance |
| [slo_error_budget_math](../../devops/case_studies/cross_cutting/slo_error_budget_math.md) | Release and refresh: a rollout rung or a quality SLO needs a burn-rate rule | Release pipeline, embedding migration, quality incident, on-device |
| [supply_chain_security_pipeline](../../devops/case_studies/cross_cutting/supply_chain_security_pipeline.md) | Serving and delivery: weights must be signed and verified before load | Weight distribution, governance, on-device |
| [gpu_node_lifecycle](../../devops/case_studies/cross_cutting/gpu_node_lifecycle.md) | Serving and delivery: node bring-up time is part of the cold-start budget | Weight distribution |
| [prometheus_cardinality_and_scale](../../devops/case_studies/cross_cutting/prometheus_cardinality_and_scale.md) | Release and refresh: per-release or per-adapter labels on metrics | Release pipeline, LoRA serving |
| [experimentation_and_online_evaluation](../../ml/case_studies/cross_cutting/experimentation_and_online_evaluation.md) | Release and refresh: an A/B rung or a cohort rollout needs a decision rule | Release pipeline, continuous training, on-device |
| [drift_monitoring_and_retraining](../../ml/case_studies/cross_cutting/drift_monitoring_and_retraining.md) | Release and refresh: retraining triggers | Continuous training |
| [model_calibration_and_thresholding](../../ml/case_studies/cross_cutting/model_calibration_and_thresholding.md) | Release and refresh: thresholds ship with the model | Continuous training |
| [feature_store_and_point_in_time_correctness](../../ml/case_studies/cross_cutting/feature_store_and_point_in_time_correctness.md) | Release and refresh: training rows must match what the model saw at decision time | Continuous training |
| [responsible_ai_fairness_and_explainability](../../ml/case_studies/cross_cutting/responsible_ai_fairness_and_explainability.md) | Operating and governing: fairness evidence for a risk tier | Governance |
| [numerical_precision_and_determinism](../../cuda/case_studies/cross_cutting/numerical_precision_and_determinism.md) | Serving and delivery: quantized outputs differ across device classes | On-device |

---

## 4. Dependency Map

Every study stands alone. The arrows show which study teaches a prerequisite idea best.

```
                    ai_release_bundle_and_lineage
                    (bundle digest, lineage joins)
                                 |
          +----------------------+----------------------+
          |                                             |
          v                                             v
design_an_ai_release_pipeline                design_a_model_weight_distribution_system
(gates, rollout ladder, rollback)            (cold start, OCI artifacts, P2P)
          |                                             |
   +------+---------------+-----------+          +------+----------------+
   |                      |           |          |                       |
   v                      v           v          v                       v
design_an_embedding   design_a_     design_an_  design_a_multi_     design_an_on_device_
_model_migration      continuous_   ai_quality  tenant_lora_        model_delivery_system
(dual index,          training_...  _incident_  serving_platform    (OTA, cohorts,
 paired artifacts)    (label delay, response_   (adapter per base   device matrix)
                       exploration) program     digest)
                          |           |
                          +-----+-----+
                                |
                                v
            design_an_ai_governance_and_model_inventory_platform
            (inventory, risk tiers, audit evidence from lineage)
```

The release pipeline is the root for the release and operating studies. The weight distribution study is the root for the serving studies. The governance study reads evidence that the continuous training and incident studies produce.

---

## 5. Interview Prep Shortcuts

| "Design X" question | Best case study | What it gives you |
|---|---|---|
| "Design the release process for prompts, models and RAG indexes" | [AI Release Pipeline](design_an_ai_release_pipeline.md) | Bundle promotion, rung sizing, eval and shadow budget, rollback under 5 minutes |
| "We are changing our embedding model. How do we migrate?" | [Embedding Model Migration](design_an_embedding_model_migration.md) | Dual index, re-embed cost, erasure and ACL sync across indexes, per-tenant cutover |
| "Serve thousands of fine-tuned adapters per customer" | [Multi-Tenant LoRA Serving Platform](design_a_multi_tenant_lora_serving_platform.md) | Registry keyed by base digest, affinity routing, isolation, base-upgrade campaign |
| "Why does our 600 GB model take 20 minutes to scale out?" | [Model Weight Distribution System](design_a_model_weight_distribution_system.md) | Fixed vs variable cold-start budget, per-rank shards, P2P, NVMe pre-seeding |
| "Ship an LLM to millions of phones" | [On-Device Model Delivery System](design_an_on_device_model_delivery_system.md) | Signed cohort manifests, OTA deltas, device matrix, offline kill switch |
| "Retrain a fraud or risk model when labels arrive a month late" | [Continuous Training and Champion-Challenger System](design_a_continuous_training_and_champion_challenger_system.md) | Challenger shadowing, exploration slice, inverse-propensity gates, policy versioning |
| "Our LLM feature got worse and nothing alerted" | [AI Quality Incident Response Program](design_an_ai_quality_incident_response_program.md) | Task-success SLOs, model-id change detection, kill switch, re-qualification |
| "Build a model inventory that passes a bank audit" | [AI Governance and Model Inventory Platform](design_an_ai_governance_and_model_inventory_platform.md) | Discovery-fed inventory, risk tiers, validation routing, evidence by digest |
| "What exactly is in an AI release, and how do you prove which bytes ran?" | [ai_release_bundle_and_lineage](cross_cutting/ai_release_bundle_and_lineage.md) | Manifest fields, digest computation, lineage queries |

---

## Maintenance

To add a case study, update all five parts above in the same commit: Quick Start if it replaces an entry, the Full Learning Path group, the Cross-Cutting table if it cites a new shared file, the dependency map, and an Interview Prep row. Add it to the `<!-- study-paths -->` block in [the section README](../README.md) with its tiers, unless it lives under `cross_cutting/`, and to the case-study list in [mlops/CLAUDE.md](../CLAUDE.md).
