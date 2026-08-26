# Technologies Audit — 2026-08-25

## 0. Snapshot

`technologies/` is the repo's **product** section: seven per-technology deep dives that
teach a named piece of infrastructure end to end, complementing the concept-first
sections that teach the pattern. On craft it is among the best-maintained sections in
the repo. Every module carries the full 14-section template, every module clears the
section's 16-Q&A floor by a wide margin (30–34 on five of seven), every parsed Q&A
carries an authored `**Short:**`, diagrams are 100% Mermaid with the One-Dark palette,
and the version pins are genuinely current — Vault 2.0.3 against 2.0.4 released
2026-08-05, Debezium 3.6.0.Final released 2026-07-01, Envoy 1.39.0, Airflow 3.3.0
against 3.3.1 released 2026-08-12. `tech_bank/`'s 18 shard preamble counts are all
exactly correct and `bank coverage` reports `3775 of 3775 … 0 orphans`.

The single biggest weakness is **not currency of the version string — it is currency of
the content behind it.** Three modules pin the newest release and then teach the release
before it: `apache_airflow` pins 3.3.0 and covers none of 3.2/3.3's headline features;
`hashicorp_vault` pins 2.0.3 and covers neither of Vault 2.0's two headline features;
`nvidia_triton_inference_server` never once names NVIDIA Dynamo, the framework NVIDIA
has positioned as its distributed-inference successor. A modernization pass that checks
version *strings* cannot see this class of defect, and the completed 2026-08-04 pass did
not.

Second, and the finding I would fix first because it ships to users: **the technology
bank contains six records that openly state in their own `**Short:**` line that they are
not products** — extractor debris (`AI`, `custom`, `on AWS`, `When each wins`,
`Most large tech companies`, plus two mangled names) that someone regularised by
*writing a bank record for the junk*, which is precisely what makes the `0 orphans`
coverage line read clean.

| | Count |
|---|---|
| Modules | 7 |
| Case studies | 0 (inline §14 by design) |
| Deep-dive sub-files | **0** |
| tech_bank files | 19 (1 taxonomy + 18 tier shards) |
| Total module lines | 11,333 |
| Total tech_bank lines | 37,962 |
| Q&As (parsed, in bank) | 216 |
| Avg lines/module | 1,619 |
| Range | 1,079 (airflow) → 2,017 (envoy) |
| `**Short:**` coverage | 100% (verified: global gap = 1,402 = `book/` exactly) |
| Mermaid / ASCII diagram fences | 37 / 0 |
| Tier membership | none — Full path only, deliberate |

Per-module: airflow 1,079 ln / 22 Q; temporal 1,928 / 32; triton 1,095 / 30; openvino
1,334 / 34; envoy 2,017 / 32; vault 1,923 / 32; debezium 1,957 / 34.

---

# PART A — the seven deep-dive modules

## 1. Missing — proposed ADDITIONS

### The selection principle, restated

`technologies/CLAUDE.md` calls it **domain homelessness**: a technology earns a module
here *only when no existing section already owns it*. Kafka is backend's, vLLM is llm's,
Redis is database's, PyTorch is ml's. The test is not importance — it is "which section
would this otherwise belong to, and does that section already treat it as its worked
example?" Applied honestly, that rules out most of the obvious 2026 candidates:
ClickHouse and DuckDB belong in `database/` alongside `redis_internals` and
`postgresql_internals`; Terraform/OpenTofu, Backstage and Argo CD belong in `devops/`,
which has modules named for them; Ray belongs in `ml/`; MCP belongs in `llm/`, which has
`llm/mcp_model_context_protocol`.

I verified homelessness by grepping the whole repo. The technologies that are genuinely
homeless **and** big enough in 2026 to deserve a page are, in order:

| Technology | Repo mentions | Owning module today | Verdict |
|---|---|---|---|
| **Apache Iceberg** | 6, all incidental | **none** | P0 — takes the reserved slot 8 |
| **Apache Flink** | 57, all incidental | **none** | P1 — also fits slot 8 |
| **Cilium** | 10 | none (devops/kubernetes_networking names it) | P1 — takes the reserved edge slot |
| **Trino** | **1** | **none** | P2 — pairs with Iceberg |
| **Keycloak** | 10 | none (backend/auth owns the concept) | P2 |

`technologies/CLAUDE.md` holds **two slots open by name**: a traffic/edge slot adjacent
to `envoy_proxy`, and a data-integration slot at position 8 adjacent to
`debezium_change_data_capture`, explicitly scoped to "a stream processor, a table
format, a warehouse-ingest technology". Iceberg and Cilium fill both exactly as
specified.

### Proposed additions

| Proposed file path | Type | Why now (cited) | Priority | Est. lines |
|---|---|---|---|---|
| `technologies/apache_iceberg/apache_iceberg.md` | module | v3 spec ratified mid-2025; the 1.10/1.11 line through May 2026 delivered deletion vectors, the variant type with shredding, row lineage and geospatial types; REST catalog is the interop standard; Snowflake, Databricks, AWS, Google and Microsoft all read/write it; Iceberg Summit 2026 drew 600+. Zero owning module in this repo. ([lakehouse table formats 2026](https://amdatalakehouse.substack.com/p/lakehouse-table-formats-in-2026-iceberg), [catalog state June 2026](https://amdatalakehouse.substack.com/p/the-state-of-apache-iceberg-catalogs)) | **P0** | 1,600–1,900 |
| `technologies/nvidia_triton_inference_server/dynamo_and_the_nvidia_serving_stack.md` | sub-file | Triton module names Dynamo **0 times**, `trtllm-serve` **0 times**, GenAI-Perf **once** against `perf_analyzer`'s 14. A reader cannot tell whether Triton is still NVIDIA's recommended LLM path. ([NVIDIA Dynamo](https://developer.nvidia.com/dynamo)) | **P0** | 500–650 |
| `technologies/envoy_proxy/gateway_api_and_inference_extension.md` | sub-file | Gateway API Inference Extension turns any ext_proc-capable Gateway into an Inference Gateway; InferencePool routes on live KV-cache usage, queue depth and LoRA-adapter presence. Envoy module has ext_proc ×12 and AI Gateway ×9 but Inference Extension **×0**. ([k8s blog](https://kubernetes.io/blog/2025/06/05/introducing-gateway-api-inference-extension/), [Envoy AI Gateway InferencePool](https://aigateway.envoyproxy.io/docs/capabilities/inference/httproute-inferencepool/)) | **P0** | 500–650 |
| `technologies/temporal_durable_execution/versioning_and_safe_deploys.md` | sub-file | Named in `technologies/CLAUDE.md` as the obvious first sub-file. Worker Versioning went **GA 2026-03-30** with Upgrade-on-Continue-as-New and the Worker Controller K8s operator in public preview; module names Worker Controller **0 times**. ([Temporal changelog](https://temporal.io/change-log/worker-versioning-continue-as-new-worker-controller)) | **P0** | 500–650 |
| `technologies/debezium_change_data_capture/cdc_across_failover_and_into_the_lakehouse.md` | sub-file | PG17 failover slots (`pg_create_logical_replication_slot(..., failover => true)`) are the fix for the module's own four-quadrant position-loss table, and Debezium's founder blogged them; module says **0**. Iceberg/Delta sinks get 2 mentions each. ([Morling on failover slots](https://www.morling.dev/blog/failover-replication-slots-with-postgres-17/)) | **P0** | 500–650 |
| `technologies/apache_flink/apache_flink.md` | module | 57 incidental mentions, no owner; still the reference stateful stream processor in the 2026 landscape even as RisingWave/Materialize take the low-ops end. ([Flink alternatives 2026](https://risingwave.com/blog/best-apache-flink-alternatives-2026/)) | P1 | 1,600–1,900 |
| `technologies/cilium/cilium.md` | module | Fills the reserved edge slot. ingress-nginx retired March 2026 and Cilium is one of three named migration targets alongside Envoy Gateway and GKE Gateway; eBPF has no home anywhere in the repo. ([Google OSS blog](https://opensource.googleblog.com/2026/02/the-end-of-an-era-transitioning-away-from-ingress-nginx.html)) | P1 | 1,600–1,900 |
| `technologies/hashicorp_vault/workload_identity_and_the_end_of_stored_secrets.md` | sub-file | Vault 2.0 shipped **Workload Identity Federation** and **SCIM 2.0**; module covers neither. SPIFFE/SPIRE get 1 mention each. The 2026 principal question is whether you need a secret store at all. ([InfoQ on Vault 2.0](https://www.infoq.com/news/2026/04/vault-2-0-ibm-identity/)) | P1 | 500–650 |
| `technologies/apache_airflow/assets_partitions_and_the_state_store.md` | sub-file | Closes the 3.2/3.3 gap in §2 without reflowing the 1,079-line parent. | P1 | 500–650 |
| `technologies/trino/trino.md` | module | 1 mention repo-wide. The query-engine half of the Iceberg story; pairs with the P0 module. | P2 | 1,500–1,800 |
| `technologies/keycloak/keycloak.md` | module | 10 mentions, no owner; `backend/auth_and_authorization_systems` owns OIDC as concept. | P2 | 1,500–1,800 |

### What the P0s contain

**`apache_iceberg`** — §1 the table format vs the catalog vs the engine, v2/v3 and what
v3 added; §2 "a table is a pointer to a list of files, and the pointer swap is the
commit"; §3 snapshot isolation via optimistic concurrency on a single atomic pointer
swap, metadata-as-data, engine neutrality; §4 metadata.json → manifest list → manifest →
data file, plus catalog types (REST, Hive, Glue, Nessie, Polaris, Unity, S3 Tables); §5
the metadata tree, a commit-conflict sequence diagram, a compaction before/after; §6 an
annotated `metadata.json` and manifest with real field values, positional vs equality
deletes and the v3 **deletion vectors** that replace them, hidden partitioning and
partition evolution, schema evolution by field-id, sort orders, the variant type with
shredding, row lineage; §7 Netflix's origin, Apple, Snowflake/Databricks/BigQuery
interop, S3 Tables; §8 Iceberg vs Delta vs Hudi vs Paimon vs DuckLake; §9 when a
warehouse or plain Parquet is the right answer instead; §10 the small-files problem,
orphan-file and expired-snapshot cleanup, commit-retry storms under concurrent writers,
catalog lock-in via a non-REST catalog; §11 pyiceberg, Spark/Flink/Trino/DuckDB,
Polaris/Nessie/lakeFS, compaction services; §12 20 Q&As; §13 partition-spec discipline,
compaction scheduling, catalog choice; §14 CDC-to-Iceberg via Debezium — deliberately
the pair-partner scenario.

**`dynamo_and_the_nvidia_serving_stack`** — where Triton, Dynamo, NIM, `trtllm-serve`
and TensorRT-LLM each sit; disaggregated prefill/decode and the KV-cache-aware router as
the thing Triton's dynamic batcher structurally cannot do; the migration question ("we
run 40 CV models and 3 LLMs on Triton — what moves?"); GenAI-Perf vs `perf_analyzer` for
token-level metrics; an honest §9 on when staying on Triton is correct.

**`gateway_api_and_inference_extension`** — Gateway API 1.6.1's resource model, GAMMA
for mesh, then InferencePool/InferenceModel and the endpoint-picker as an ext_proc
callout; the routing signals (KV-cache utilisation, queued requests, LoRA adapters) and
why they beat least-request for LLM traffic; Envoy AI Gateway's token-based rate
limiting and model-name routing; the ingress-nginx migration path.

**`versioning_and_safe_deploys`** — patching/`GetVersion` vs Worker Deployments; Pinned
vs AutoUpgrade and how to choose per workflow type; Upgrade-on-Continue-as-New; the
Temporal Worker Controller operator and versioned-Deployment ramping; draining a version
with month-long executions still open; the replay test in CI as the gate.

**`cdc_across_failover_and_into_the_lakehouse`** — PG16 logical decoding on standbys,
PG17 failover slots and slot synchronisation, what Debezium must set for a slot to be a
failover slot, and how that collapses the module's four-quadrant recovery table; then
the Debezium Iceberg sink, Paimon/Hudi paths, and CDC-merge semantics (upsert vs
append-with-deletion-vectors) into a lakehouse.

---

## 2. Stale — verified CORRECTIONS

Version *strings* are current across the board — I verified the Vault, Debezium,
Airflow, Envoy and Temporal pins against upstream and found no stale pin. Every entry
below is a **content** gap behind a correct pin.

| File | Line/heading | What it says | What is true in Aug 2026 | Source URL |
|---|---|---|---|---|
| `apache_airflow/apache_airflow.md` | L3 version pin | "targets the **Airflow 3 line, current stable 3.3.0 (July 2026)**" | 3.3.1 shipped 2026-08-12; and 3.3's headline features are absent: **task/asset state store (AIP-103)** — 0 mentions; **pluggable retry policies** — 0; **asset partitioning** (3.2, expanded 3.3) — 0 (the word "partition" appears 0 times in the file) | https://airflow.apache.org/blog/airflow-3.3.0/ |
| `apache_airflow/apache_airflow.md` | §4/§6 deployment | DAG parsing and the `dag-processor` are covered (7 mentions) | **DAG bundles** — Airflow 3.0's replacement for the flat DAG folder, and the mechanism DAG versioning is built on — appear **0 times** ("bundle" count = 0) | https://airflow.apache.org/docs/apache-airflow/stable/release_notes.html |
| `hashicorp_vault/hashicorp_vault.md` | §1 version pin | pins Vault 2.0.3 (2026-06-17) on the 2.0 line | 2.0.4 shipped 2026-08-05; and Vault 2.0's two headline capabilities are absent: **Workload Identity Federation** — 0 mentions; **SCIM 2.0 provisioning** — 0 mentions. Both are the reason 2.0 exists | https://www.infoq.com/news/2026/04/vault-2-0-ibm-identity/ |
| `nvidia_triton_inference_server/…md` | throughout | Triton presented as NVIDIA's general-purpose serving surface, with TensorRT-LLM (19 mentions) as the LLM backend | **NVIDIA Dynamo — 0 mentions**; **`trtllm-serve` — 0**; **Open Inference Protocol — 0** (only "KServe v2", ×6). GenAI-Perf ×1 vs `perf_analyzer` ×14, though GenAI-Perf is the LLM-workload tool. `llm/inference_and_decoding` and `llm/vllm_deep_dive` already discuss Dynamo — the repo contradicts itself across sections | https://developer.nvidia.com/dynamo |
| `debezium_change_data_capture/…md` | §6 slots, §10 recovery | Four-quadrant position-loss table; "a lost slot is data loss" | Correct for PG≤15. **PG16 logical decoding on standbys — 0 mentions; PG17 failover slots — 0.** With `failover => true` the slot syncs to the standby and survives promotion, which is the direct answer to the module's own worst quadrant | https://www.morling.dev/blog/failover-replication-slots-with-postgres-17/ |
| `debezium_change_data_capture/…md` | §4 deployment modes | Debezium Server ×24, Operator ×8, Embedded Engine ×8 | **Debezium Management Platform — 0 mentions.** It is the lead item in the 3.6 release the module pins: operator-based full-lifecycle management with no Kafka components, a monitoring REST API + multi-panel dashboard, JDBC-sink pipelines, MongoDB source config in the UI | https://debezium.io/blog/2026/07/10/debezium-platform-status-update/ |
| `envoy_proxy/envoy_proxy.md` | §4/§11 ecosystem | Excellent ambient/ztunnel/waypoint/Linkerd/Cilium/ingress-nginx coverage (27/16/18/9/6/14 mentions); Envoy AI Gateway ×9 | **Gateway API Inference Extension / InferencePool — 0 mentions; GAMMA — 0.** The Inference Extension is built on Envoy's own ext_proc (covered ×12), so this is the one place the module teaches its own mechanism without its most important 2026 application | https://gateway-api-inference-extension.sigs.k8s.io/ |
| `intel_openvino/intel_openvino.md` | §8/§9 positioning | Competitive frame is **only** Triton and vLLM — i.e. "why not a CUDA GPU stack" | **llama.cpp/GGUF, ONNX Runtime as a standalone runtime, ExecuTorch, Core ML/MLX, Qualcomm QNN — all 0 mentions.** ONNX Runtime appears solely as an integration partner (the OpenVINO EP). The module wins an easy argument and never engages the real 2026 CPU/edge decision. Separately, Intel's accelerator trajectory (Falcon Shores cancelled to an internal test chip; Gaudi the only near-term AI part) is absent, and it is material to an "invest in this ecosystem" judgment | https://www.tomshardware.com/tech-industry/artificial-intelligence/intel-cancels-falcon-shores-gpu-for-ai-workloads-jaguar-shores-to-be-successor |
| `README.md` | §Version Notes, Temporal row | "`[1.31]` brought … **Task Queue Priority and Fairness to GA**" | Correct upstream, but the module teaches it in **3 mentions of "fairness" and 2 of "priority"** — the README advertises depth the module does not have. Either deepen §6 or drop the claim | https://temporal.io/changelog/product-area/server |

**Not findings** (checked and cleared, recorded so nobody re-reports them):
- Vault 2.0 is **not** a semver-architecture jump — it is the 1.21→2.0 renumber onto
  IBM's Support Cycle-2 lifecycle, which the README states correctly.
- The `hashicorp_vault` slug is correct under the section's own precedent 4 (follow the
  engineering name, not the IBM brand); the ownership fact is where it belongs, in §1.
- Two bold prose lines starting with "Q" (`triton:797`, `temporal:687`) look like Q&As
  to a naive `grep '^\*\*Q'` but sit in §6, outside the `interview q` span. Not defects;
  the real per-module Q&A counts are 30 and 32.

---

## 3. Reordering — SEQUENCE and TIER changes

The current order is deliberate and well-argued (two contrast pairs, then three appended
domains, two slots explicitly held open). **I propose no change to the relative order of
the existing seven.** The only sequencing decisions are where new modules go, and both
are already specified by `CLAUDE.md`:

| Module | Current position/tier | Proposed | Why |
|---|---|---|---|
| `cilium` | — | **insert at 6**, pushing vault→7, debezium→8 | `CLAUDE.md`: "a future traffic/edge page belongs adjacent to `envoy_proxy`, which now means inserting at position 6 and pushing Vault to 7, not appending at 7. Do not append blindly." |
| `apache_iceberg` | — | **append at 9** (after debezium at 8) | `CLAUDE.md`: "a **fourth pair slot at 8** for a future data-integration page … belongs adjacent to Debezium." |
| `apache_flink` | — | append at 10, adjacent to iceberg | Completes a data-platform triple; Flink's §8 is largely "why this is not Airflow and not Debezium". |
| `trino` | — | append at 11, adjacent to iceberg | Query engine to Iceberg's storage. |
| `keycloak` | — | append at 12 | Third control-plane page after Vault; no natural pair. |

Proposed `STUDY_ORDER["technologies"]` after all P0/P1 work:

```js
technologies: [
  "technologies/apache_airflow","technologies/temporal_durable_execution",
  "technologies/nvidia_triton_inference_server","technologies/intel_openvino",
  "technologies/envoy_proxy","technologies/cilium",
  "technologies/hashicorp_vault",
  "technologies/debezium_change_data_capture","technologies/apache_iceberg",
],
```

(P2 modules append after; sub-files need no `STUDY_ORDER` entry — they group under their
parent by directory.)

### The tier decision — the single most important structural move

**Land the five P0 sub-files, then end the tiering deferral in one commit.** These are
the same act, and that is the point.

`technologies/CLAUDE.md` defers tiering with a specific, correct reason: at module-page
granularity with zero sub-files, "the two tiers can only differ by *membership*, so the
repo-wide 'roughly half of each principal list is material senior never sees' property
cannot yet be expressed here. It becomes expressible the moment the first sub-file
lands." The deferral is not an oversight — it is a blocked dependency, and the blocker
is that this section has **0 deep-dive sub-files against llm's 85 and devops's 1**.

The five P0 sub-files unblock it, and in the right shape, because every one of them is a
judgment page rather than a craft page:

| File | Tier | Why that tier |
|---|---|---|
| all 7 module pages | **senior** | operate it, debug it at 3am — exactly the craft cut |
| `temporal_…/versioning_and_safe_deploys.md` | **principal only** | safe-deploy strategy across a fleet of month-long executions is a judgment call, as `CLAUDE.md` itself argues |
| `triton/dynamo_and_the_nvidia_serving_stack.md` | **principal only** | "do we migrate off Triton, and what moves" is an adoption decision |
| `envoy/gateway_api_and_inference_extension.md` | **principal only** | picking the inference data plane is an architecture bet |
| `vault/workload_identity_and_the_end_of_stored_secrets.md` | **principal only** | "do we need a secret store at all" is the principal question |
| `debezium/cdc_across_failover_and_into_the_lakehouse.md` | **principal only** | HA topology and destination format are both design choices |
| `airflow/assets_partitions_and_the_state_store.md` | **senior** | mechanics of a feature you will operate |
| `temporal_durable_execution`, `envoy_proxy`, `debezium…` module pages | **also principal** | these three carry genuine adopt/don't-adopt material in §8/§9 |

That yields senior = 8 files, principal = 8 files, with 5 of principal's 8 being
material senior never sees — the repo-wide property, satisfied by construction.

Mechanics (from `CLAUDE.md`, verified): add every module and file to the `README.md`
`<!-- study-paths -->` block with tier tags; paste empty
`<!-- study-path-table senior -->` / `<!-- /study-path-table -->` and principal marker
pairs into `README.md`; run `python3 game/extract.py --write-paths`; **update
`game/CLAUDE.md`'s toggle-exception language in the same commit** (it lists
`technologies` among the untiered sections). There is **no array in `app.js`** to add,
and `game/README.md` carries no such language. Never hand-edit the generated tables.

---

## 4. Depth — THIN modules and BLOAT

No module is bloated and none should be merged or deleted. Two are thin relative to
their siblings, and both are thin in the same direction — recent-release coverage.

| Module | Lines | Q&As | What is missing | Fix |
|---|---|---|---|---|
| `apache_airflow` | **1,079** (shortest; siblings avg 1,762) | **22** (lowest; siblings 30–34) | Asset partitioning, task/asset state store (AIP-103), pluggable retry policies, DAG bundles — all 0 mentions. Competitive framing thin: Dagster ×5, Prefect ×2, Kestra ×0, Argo Workflows ×0, Databricks Workflows ×0 | Add the `assets_partitions_and_the_state_store.md` sub-file (P1); expand §8 with the 2026 orchestrator field; raise §12 from 22 to 28 |
| `nvidia_triton_inference_server` | **1,095** (2nd shortest) | 30 | Dynamo, `trtllm-serve`, Open Inference Protocol all 0; GenAI-Perf ×1; BentoML ×0, SGLang ×1, TorchServe ×1 (and TorchServe is archived — verify what the one mention claims) | Add the `dynamo_and_the_nvidia_serving_stack.md` sub-file (P0); it also carries the competitive §8 the parent lacks |

**`intel_openvino` — the one module whose selection I questioned. Verdict:
KEEP-AND-RESCOPE, not delete.** The case against: Intel cancelled Falcon Shores to an
internal test chip and Gaudi 3 has seen limited uptake, so betting on the ecosystem is a
live question the module never raises; and its own §8/§9 never engage llama.cpp/GGUF,
ONNX Runtime standalone, ExecuTorch, Core ML/MLX or QNN — it argues only against CUDA
GPU stacks, which is the easy case. A reader finishes it knowing OpenVINO deeply and
still unable to answer "OpenVINO or llama.cpp for an on-prem CPU LLM deployment", which
is the most common real decision the page should settle.

The case for, which wins: it is genuinely domain-homeless (no section owns CPU/NPU
inference); it is one of the best-executed pages in the repo (34/34 Q&As with Shorts, 6
Mermaid diagrams, a textbook broken→fix async-loop pair at §6.13, 26 sub-sections in §6,
and unusually careful version discipline — the `mo` removal, the MULTI deprecation and
the InferenceEngine 1.0 removal are all handled correctly rather than left stale); and
CPU/edge inference on hardware already paid for is a real 2026 deployment shape.
Deleting 1,334 lines of correct, well-made content to fix a positioning section is the
wrong trade. **Rewrite §8 and §9 against the real competitive set, and add a
`### Vendor trajectory` note to §1 in the style `hashicorp_vault` uses for the IBM
ownership fact.** ~120 lines of edit, no restructure.

Also thin, minor: `temporal_durable_execution` teaches Task Queue Priority and Fairness
in 5 mentions total while `README.md` advertises both as GA-in-1.31 headline features;
Worker Controller (the K8s operator, public preview since 2026-03-30) is 0 mentions.

---

# PART B — the `tech_bank/`

## B1. Build output

```
Wrote technology bank -> game/tech_index.json  (3775 tools, 18 tiers, 95 roles, 6 kinds)
authored descriptions: 2688 of 3775 bank records
Wrote technologies index -> game/questions/tech.json  (3775 technologies, 624 module files, 618 tradeoff tables)
bank coverage: 3775 of 3775 indexed tools have a record; 0 bank records no module teaches
wiring check: OK
```

Structurally the bank is in excellent order:

- **All 18 shard preamble counts are exactly correct.** `CLAUDE.md` warns these drift
  silently (all 18 were wrong on 2026-08-04); they have been maintained since.
- **No record name exceeds the 42-char `clean_tech_name` ceiling.** Two sit *at* it —
  `ONNX Runtime — OpenVINO Execution Provider` (exactly 42) and
  `spring.batch.jdbc.initialize-schema=always` (42) — plus 18 more at 41–42. One added
  character to any of these silently yields zero tools. Worth a note in `CLAUDE.md`, not
  a change.
- No record name contains more than 5 spaces. Two contain ` & ` (`Weights & Biases`,
  `Weights & Biases Weave`) — the documented splitter hazard — but coverage is clean, so
  the citation side is being written safely today. Fragile, not broken.
- 6 kinds: tech 2238, api 1116, concept 157, model 110, spec 89, dataset 65.

## B2. The finding: six records that are not products

`authored descriptions: 2688 of 3775` leaves 1,087 records with no description. 1,064 of
those are `**Kind:** api`, which `CLAUDE.md` states is the permanent terminal state.
**23 are not**, and six of those are extractor debris laundered into the bank:

| Record | Shard | Its own `**Short:**` | Source citation |
|---|---|---|---|
| `AI` | applied-ml.md:43 | "**Umbrella index entry, not a product**; here it stands for EHR-integrated AI clinical documentation." | not yet traced |
| `When each wins` | gpu.md:1601 | "**Table-row fragment, not a product**: MIG versus MPS…" | `technologies/nvidia_triton_inference_server/…md:933` — a §11 **labelled bullet** `- **When each wins** — pick MIG when…` |
| `custom` | data-movement.md:415 | "**Not a product**: a placeholder for hand-rolled implementations such as a bespoke CDC relay or A/B harness." | not yet traced |
| `Most large tech companies` | ml-lifecycle.md:1589 | "**Not a technology**: a table cell referring to the in-house ML platforms…" | `ml/ml_system_design/ab_testing_for_ml.md:843` — table row `\| In-House Platform \| Most large tech companies \| … \|` |
| `on AWS` | traffic-edge.md:643 | "**Placeholder entry** for the AWS-hosted service-mesh option…" | `database/redis_internals/redis_internals.md:66` — a bold span that **wraps onto a second physical line**: `the **default engine on AWS⏎ ElastiCache and MemoryDB…` |
| `IDE "expand inferred type` | devtools.md | (real description; the *name* is truncated with an unbalanced `"`) | `java/generics_and_type_system/type_inference_and_bounds.md:592` — column 0 is `IDE "expand inferred type"` |
| `@modelcontextprotocol/server` | llm-apps.md | (real description; the *name* is debris) | `llm/mcp_model_context_protocol/…md:650` — column 0 is ``` `@modelcontextprotocol/sdk` (v1) / `@modelcontextprotocol/server` + `/client` (v2 beta) ``` — hits the ` / ` split, the ` + ` split **and** a parenthetical in one cell. The legitimate `@modelcontextprotocol/server-everything` record exists alongside it |

**Why this matters more than the count suggests.** Every one is the *exact* failure shape
`technologies/CLAUDE.md` documents — the labelled-bullet bold trap, the wrapped-bullet
trap, the ` / ` and ` + ` splitters. The `CLAUDE.md` prescribes the fix: "**Prefer fixing
the CITATION over adding an alias**." What happened instead is worse than an alias: a
**bank record was authored for the debris**, which (a) makes the `0 bank records no
module teaches` coverage line report clean, permanently hiding the bug from the one
check that could catch it, and (b) ships the junk to users — `on AWS` is filed
`traffic-edge/service-mesh-and-discovery @1`, so it surfaces as a top result under
"Service mesh & discovery" on the Technologies screen. `custom` and `AI` are bare common
words that will now also swallow any future citation of those strings.

The first one is in **this section's own module** (`nvidia_triton_inference_server` §11),
which is the strongest possible argument for adding a §11 lint.

Three further records are borderline — real concepts indexed as `**Kind:** tech`:
`Prompt caching dashboard` (a console feature, from
`llm/agentic_frameworks/claude_agent_sdk.md:563`), `Asymptotic Complexity Calculator`
(whose own tool column reads "Pen-and-paper / whiteboard"), and `QUIC Tracker`. These
are defensible; fix the six above and leave these.

The remaining 14 non-api records with no description are genuine tools missing a
description, not debris: `DataStax Studio`, `Humanloop`, `MCP Hub`, `SomAgent`, `Arthur`,
`SkyRL`, `torchtune`, `PromptArmor`, `Skyfire`, `Trusted Agent Protocol`,
`Visa Intelligent Commerce`, `OpenAI GPT-5.6`, `OpenAI GPT-5.6 series`,
`Gemini Embedding API`. Descriptions are per-record opt-in, so this is backlog, not a
defect — except that `OpenAI GPT-5.6` and `OpenAI GPT-5.6 series` are a **near-duplicate
pair** and should be one record plus a `TECH_ALIASES` entry.

## B3. Homonym / bare-word hazards not yet in `TECH_HOMONYMS`

`tech_key()` normalises on the string alone, so any record whose `###` name is a bare
word another section could plausibly write meaning something else is a live trap — the
`Maxwell` case in `CLAUDE.md` is exactly this. Scanning the 3,807 records for
single-token names against what this repo actually teaches, the highest-risk are:

| Record | Collides with | Risk |
|---|---|---|
| `Sentinel` | **Redis Sentinel**, taught at length in `database/redis_internals` | **highest** — a future `\| Sentinel \|` §11 cell in a Redis context silently becomes Alibaba Sentinel |
| `Vector` | `java.util.Vector`, `std::vector`, and "vector" throughout ml/llm | **highest** |
| `Arena` | arena allocation in `cs_fundamentals`/`runtime-systems`; Chatbot Arena in llm | high |
| `Apex` | **NVIDIA Apex** (mixed precision), in a repo with a 24-module CUDA section | high |
| `Ray` | ray tracing in `cuda/` | high |
| `Node` | a graph/tree node, everywhere | high |
| `Cortex` | Grafana Cortex (archived into Mimir), Cortex the IDP, Cortex XSOAR | medium — also a currency question |
| `Connect` | Kafka Connect (Debezium's own runtime), Consul Connect | medium |
| `Mamba` | Mamba SSM vs `mamba` the package manager | medium |
| `Falcon` | Falcon LLM / Falcon web framework / Intel Falcon Shores | medium |
| `Linker`, `Step`, `Spot`, `Reader`, `Epic`, `Atlas` | ordinary nouns or second products | low–medium |

None is broken today (coverage is clean). The action is **not** to alias them — an alias
is global and permanent, and `CLAUDE.md` is explicit that the test is "could a future
author write this bare word meaning something else". For every row above the answer is
yes, so the correct action is a **documented watchlist in `technologies/CLAUDE.md`**
telling future authors to write `Redis Sentinel`, `NVIDIA Apex`, `Ray (Anyscale)` etc.
in column 0, plus `_HOMONYM_LABELS` entries only if a collision actually lands.

## B4. Taxonomy coverage — whole categories missing in 2026

The taxonomy (6 kinds / 8 languages / 18 tiers / 95 roles) is coherent, and the `Merged:`
note on each merged role is exemplary documentation. Shard balance reflects repo content
weight rather than an authoring choice — apis-frameworks 491 and runtime-systems 454 at
one end, caching 61, inference 93 and traffic-edge 98 at the other. `inference` at 93 is
the one imbalance worth naming: it is the hottest area of 2026 infrastructure and holds
a quarter of what `llm-apps` (249) holds.

Genuine category gaps, in priority order:

| Gap | Evidence | Proposed home |
|---|---|---|
| **eBPF** — bpftrace, Tetragon, Hubble, Pixie, Parca, bcc, libbpf, Katran | Cilium and Falco are seeded, but only under `service-mesh-and-discovery` and `supply-chain-and-runtime-security`; there is no role where an eBPF *tool* belongs. eBPF is the substrate for 2026 networking, security and continuous profiling | new role `observability/ebpf-and-kernel-instrumentation`, or extend `profiling-and-performance` |
| **WebAssembly** — Wasmtime, WasmEdge, wazero, Spin, WASI 0.2, Proxy-Wasm, the component model | **no role anywhere.** Proxy-Wasm is the extension ABI the `envoy_proxy` module teaches, so the bank cannot classify a tool this section already covers | new role under `platform-delivery` or `runtime-systems` |
| **SQL query engines / federation** — Trino, Presto, StarRocks, Doris, Dremio, Databend | `batch-and-distributed-compute` seeds Spark/Ray/Dask/Beam; `warehouse-and-olap` seeds ClickHouse/Snowflake/BigQuery/Druid. A federating query engine is neither | new role `data-stores/query-engine-and-federation` |
| **Lakehouse catalogs** — Polaris, Unity Catalog, Nessie, lakeFS, Gravitino, S3 Tables | `warehouse-and-olap` seeds Iceberg/Delta/Parquet (the formats) but not the catalog layer, which is where 2026 governance consolidated | extend `warehouse-and-olap`'s def, or split a `lakehouse-table-and-catalog` role |
| **Data transformation** — dbt, SQLMesh, Dataform, Malloy | no role. dbt has 13 repo mentions and would land awkwardly in `data-quality-and-lineage` | extend `data-quality-and-lineage`, or new `data-movement/transformation-and-modelling` |
| **Internal developer platforms** — Backstage, Port, Humanitec, Score, Radius, Kratix | `platform-delivery` has 5 roles, none for the IDP layer; Backstage has nowhere natural today | new role `platform-delivery/developer-portal-and-idp` |
| **Post-quantum crypto** — ML-KEM/ML-DSA, liboqs, X25519MLKEM768 | `secrets-and-cryptography` covers it definitionally; browsers and TLS libraries shipped hybrid PQ by default through 2025–26 | extend the `secrets-and-cryptography` seeds; no new role |
| **Language tokens** | 8 tokens; `sql` is absent though the repo has a 33-module database section, and `kotlin`/`scala` are absent though Spring and Flink content exists | add `sql` at minimum |

AI/agent infrastructure is **well covered**, contrary to my prior — `llm-apps` has five
roles (agent-framework, tool-use-and-mcp, prompting-context-and-structured-output,
llm-gateway-and-routing, agentic-environments) with A2A, MCP, LangGraph and E2B all
seeded, plus `security/ai-safety-and-guardrails`. The only AI-infra gap is on the
serving side, which is the `inference` tier's thinness noted above (Dynamo, NIM, llm-d,
Gateway API Inference Extension, vLLM production-stack).

## B5. Dead / renamed / acquired tools in the bank

Full verification of ~3,800 records against 2026 reality was not possible in this pass —
see §7. What I can state: the `0 orphans / 3775 of 3775` line proves **join integrity**,
not **factual currency**, and `CLAUDE.md` says so directly ("A factual audit is the
highest-risk time for this bank … to the index a rename is indistinguishable from
deleting one tool and inventing another"). Records flagged for verification on name
alone: `Cortex` (Grafana Cortex was archived into Mimir; three unrelated products share
the name), `Hystrix`/`Zuul`/`Eureka` (Netflix OSS, all maintenance-mode), `Skaffold`,
`Weave`, `TorchServe` (archived), `ksqlDB`, `Riak`, `Solr`, `Opsgenie` (folded into Jira
Service Management Operations), `Jeli` (acquired by PagerDuty), `POT`/`Open Model
Zoo`/`Myriad` (removed from OpenVINO — and the `intel_openvino` module correctly says
so). A retired tool having a record is legitimate; the defect would be a `**Short:**`
that asserts current status. That check is a discrete work item (Wave 6 below).

---

## 5. Cross-section notes

- **`llm/` and `technologies/` contradict each other on Dynamo.**
  `llm/inference_and_decoding` and `llm/vllm_deep_dive` both discuss NVIDIA Dynamo;
  `nvidia_triton_inference_server` names it zero times. Whoever writes the Dynamo
  sub-file must cross-link both and state the boundary: llm/ owns *distributed LLM
  inference as a pattern*, `technologies/` owns *the NVIDIA product family*.
- **`database/` should own ClickHouse and DuckDB, not this section.** `database/` has
  `redis_internals`, `postgresql_internals`, `mysql_innodb_internals` and
  `elasticsearch_internals`; a `clickhouse_internals` is the consistent home. ClickHouse
  has 40 repo mentions and no owner. Flag to the `database/` auditor.
- **Apache Iceberg is homeless across the entire repo**, not just here — 6 incidental
  mentions in `devops/case_studies`, `ml/case_studies`, `ml/data_pipelines_and_processing`
  and `ml/mlflow_deep_dive`. If `database/` would rather own it as
  `database/lakehouse_table_formats`, that is a defensible alternative to the P0 above;
  what is not defensible is leaving it unowned. **Coordinate with the `database/` auditor
  before executing Wave 4.**
- **`devops/` owns eBPF today only incidentally** (`kubernetes_networking`,
  `kubernetes_architecture`; 11 files). If `technologies/cilium` lands, the boundary
  needs the same treatment `hashicorp_vault` got against `devops/secrets_management`:
  devops owns the discipline, technologies owns the product.
- **`backend/kafka_deep_dive` is the precedent that keeps Flink here, not there.**
  backend/ owns the log; a stream processor over the log is a separate product with no
  owner. Confirm with the `backend/` auditor that they do not intend a `backend/flink…`.
- **Root `CLAUDE.md` lists 16 sections but `STUDY_ORDER` contains a `soft_skills`
  section** (26+ modules) absent from that table. Not mine to fix — flagging for whoever
  owns the root docs.

---

## 6. Execution plan

Effort in agent-sessions. Everything within a wave is parallel-safe unless noted.

### Wave 0 — the bug, before anything else (0.5 session)
1. **Fix the six §11 citations, not the bank:**
   `technologies/nvidia_triton_inference_server/…md:933` (relabel the bullet or move it
   out of §11); `ml/ml_system_design/ab_testing_for_ml.md:843`;
   `database/redis_internals/redis_internals.md:66` (unwrap the bold span onto one
   physical line); `java/generics_and_type_system/type_inference_and_bounds.md:592`
   (drop the quotes from column 0); `llm/mcp_model_context_protocol/…md:650` (split the
   ` / ` + ` + ` cell into separate rows); and trace the `AI` and `custom` citations.
2. **Then delete the six laundered records** from `applied-ml.md`, `gpu.md`,
   `data-movement.md`, `ml-lifecycle.md`, `traffic-edge.md`, `devtools.md` and
   `llm-apps.md`, and **decrement each shard's preamble count in the same commit**.
3. Re-run `cd game && python3 extract.py 2>&1 | grep "bank coverage"`. Expect
   `3769 of 3769 … 0 orphans`. Then run `bash scripts/build_banks.sh` in full —
   `--strict` passing is not the same as CI passing.
4. Add a `## The §11 lint` note to `technologies/CLAUDE.md`: no labelled bullet whose
   bold label is not a product; no bold span wrapping a physical line; no ` / `, ` + `
   or ` & ` in column 0; and **never author a bank record to silence an orphan warning —
   that is the bug reporting itself.**
   *Touches 5 content files outside this section — coordinate.*

### Wave 1 — the verified corrections (2 sessions, fully parallel, one agent per module)
5. `apache_airflow` — asset partitioning, task/asset state store (AIP-103), pluggable
   retry policies, DAG bundles; bump the pin to 3.3.1; expand §8's orchestrator field;
   §12 22→28 Q&As.
6. `hashicorp_vault` — Workload Identity Federation and SCIM 2.0 into §4/§6; pin →2.0.4.
7. `nvidia_triton_inference_server` — a §1 positioning paragraph naming
   Dynamo/NIM/`trtllm-serve`; GenAI-Perf into §11 and §6; verify the TorchServe mention
   says "archived".
8. `debezium_change_data_capture` — Debezium Management Platform into §4; PG16 standby
   decoding and PG17 failover slots into §6 and the §10 recovery table.
9. `envoy_proxy` — Gateway API Inference Extension + InferencePool into §4/§11; GAMMA,
   one line.
10. `intel_openvino` — rewrite §8/§9 against llama.cpp/GGUF, ONNX Runtime standalone,
    ExecuTorch, Core ML/MLX, QNN; add `### Vendor trajectory` to §1.
11. `temporal_durable_execution` — Worker Controller; deepen priority/fairness to match
    what `README.md` claims.

    **Every edited answer must have its `**Short:**` line re-read and updated in the same
    edit** — the audit owns the Short line. After each module:
    `python3 game/extract.py --strict`, then check `bank coverage` — a rename inside a
    §11 table orphans a record silently.

### Wave 2 — the five P0 sub-files (3 sessions, parallel)
12–16. Write the five sub-files from §1. **Each needs a line added to `README.md`'s
`<!-- study-paths -->` block in the same commit** — a file on disk absent from the block
is fatal under `--strict`. **No `STUDY_ORDER` change** (sub-files group by directory).

### Wave 3 — end the tiering deferral (0.5 session, SEQUENTIAL, after Wave 2)
17. Assign tiers per the table in §3; fill the `<!-- study-paths -->` block; paste both
    empty `<!-- study-path-table … -->` marker pairs into `README.md`; run
    `python3 game/extract.py --write-paths`; update `game/CLAUDE.md`'s toggle-exception
    language in the same commit.

### Wave 4 — `apache_iceberg` (2 sessions) — coordinate with the `database/` auditor first
18. Write the module. **Insert into `STUDY_ORDER` at position 9**, update
    `technologies/CLAUDE.md`'s Module List and slot-reservation prose (slot 8 is now
    taken; state whether a new slot opens), `README.md`'s Module Table / Learning Path /
    Knowledge-Question Map / Study Plan / Version Notes / `<!-- study-paths -->` block,
    **root `README.md`**, and **root `CLAUDE.md`'s section-table row (module count
    7→8)**.

### Wave 5 — `cilium`, `apache_flink` (4 sessions)
19. `cilium` — **insert at STUDY_ORDER position 6**, pushing vault→7, debezium→8,
    iceberg→9. Same six-file update set. Boundary statement against
    `devops/kubernetes_networking` in §1.
20. `apache_flink` — append at 10. Confirm with the `backend/` auditor first.

### Wave 6 — bank taxonomy and currency (2 sessions)
21. Taxonomy: add the eBPF, WebAssembly, query-engine/federation, transformation and IDP
    roles from §B4; add `sql` to the language tokens. **Every role id added must be used
    by at least one record or the role is dead weight** — seed each with 8+ records (the
    taxonomy's own floor).
22. Currency sweep of the ~2,238 `kind: tech` records. Follow `CLAUDE.md`'s rename recipe
    exactly — **rename the `### ` heading and alias the old written form** to preserve
    the authored description; delete-and-reauthor only for a genuine replacement.
    Re-check `bank coverage` both directions after every batch.
23. Add the bare-word watchlist from §B3 to `technologies/CLAUDE.md`. No aliases.

### Wave 7 — P2 (4 sessions)
24. `trino`, `keycloak`.

**Total: ~18 agent-sessions.** Waves 0 and 1 (2.5 sessions) capture most of the value.

**Files needing coordination outside this section:**
`ml/ml_system_design/ab_testing_for_ml.md`, `database/redis_internals/redis_internals.md`,
`java/generics_and_type_system/type_inference_and_bounds.md`,
`llm/mcp_model_context_protocol/mcp_model_context_protocol.md` (Wave 0);
`game/CLAUDE.md` (Wave 3); root `README.md` and root `CLAUDE.md` (Waves 4–5, 7).

---

## 7. Confidence and gaps

**High confidence — mechanically verified in this session:** all line, Q&A, `**Short:**`
and diagram counts; the six laundered bank records and five of their source citations,
quoted verbatim; all 18 shard preamble counts correct; no record over the 42-char
ceiling; the `extract.py` output; every presence/absence count in §2 (literal `grep -ic`
over the exact files); the two "Q"-prefixed bold lines being §6 prose rather than Q&As.

**High confidence — web-verified with the cited URL:** Airflow 3.3.1 and 3.3's feature
set; Vault 2.0's GA date, WIF/SCIM, the 1.21→2.0 renumber rationale, and 2.0.4; Debezium
3.6.0.Final and the Management Platform; Temporal Worker Versioning GA and Nexus GA;
ingress-nginx retirement; PG17 failover slots; Gateway API Inference Extension /
InferencePool; Iceberg v3 and REST-catalog adoption; Intel's Falcon Shores cancellation.

**Could not verify — the real gaps:**

1. **~3,800 bank records for factual currency, one by one.** §B5 is a flagged list, not
   a verdict. This is the single largest unverified surface in the section and needs its
   own pass (Wave 6). `bank coverage` cannot see it — the check is join integrity, and
   `CLAUDE.md` says so.
2. **Four of seven modules were profiled by targeted grep, not by full read.** Three
   sonnet profilers (airflow, envoy, vault, temporal) could not launch — the subagent
   pool was saturated by sibling section auditors and the model was intermittently
   rate-limited throughout. Only `intel_openvino` received a full-file profile. So my §2
   and §4 findings for the other six rest on greps plus the CLAUDE.md/README
   descriptions: **absence claims are solid** (a 0-count grep for "asset partition" or
   "Dynamo" is conclusive) while **quality claims are weaker** — I did not read every §6,
   so a thin-but-present treatment could read to me as adequate. **Re-run those four
   profilers before executing Wave 1.**
3. **Whether the two ` & ` record names are actually cited safely.** Coverage is clean,
   which implies yes, but I did not read `tech_key()`/`clean_tech_name` to confirm the
   splitter's exception. Ten minutes, before touching either record.
4. **The 3,807-records-in-shards vs 3,775-indexed-tools delta (32).** Presumably alias
   collapse, but I did not confirm the mechanism. If it is not alias collapse, it is a
   second class of hidden record.
5. **Whether `database/` intends to own Iceberg.** My P0 assumes not. Confirm before
   Wave 4 — building it in the wrong section is the most expensive mistake available in
   this plan.
6. **The 14 non-api records with no description** — confirmed absent, but I did not
   assess whether each is worth one, beyond the `OpenAI GPT-5.6` /
   `OpenAI GPT-5.6 series` duplicate pair.
