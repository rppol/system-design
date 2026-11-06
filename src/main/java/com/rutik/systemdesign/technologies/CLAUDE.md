# Technologies Section — CLAUDE.md

Section root: `src/main/java/com/rutik/systemdesign/technologies/`
Global conventions (formatting, templates, Q&A rules): see root `CLAUDE.md`.

This section holds **per-technology deep dives** — a canonical senior-engineer module
for a single major infrastructure technology (an orchestrator, an inference server, a
message broker, a workflow engine), covering its architecture, internals with real
configs, operations, and when NOT to reach for it. It complements the concept-first
sections (`backend/`, `llm/`, `ml/`, `devops/`, `cuda/`) which teach the *pattern*
(orchestration, model serving, event streaming); this section teaches the *product*.

---

## Scope & Non-Overlap Boundary

A technology earns a module here **only when no existing section already owns it**.
Before adding a module, check whether the technology is already the worked example in
another section's concept module — if so, cross-link into that module instead of
duplicating it here. Examples: Kafka is owned by
[`backend/kafka_deep_dive`](../backend/kafka_deep_dive/kafka_deep_dive.md) — do not add
`technologies/apache_kafka`; vLLM is owned by
[`llm/vllm_deep_dive`](../llm/vllm_deep_dive/vllm_deep_dive.md) — do not add
`technologies/vllm`; MLflow is owned by
[`ml/mlflow_deep_dive`](../ml/mlflow_deep_dive/mlflow_deep_dive.md) — do not add
`technologies/mlflow`; FAISS is owned by
[`llm/faiss_deep_dive`](../llm/faiss_deep_dive/faiss_deep_dive.md) — do not add
`technologies/faiss`; Redis is owned by
[`database/redis_internals`](../database/redis_internals/redis_internals.md) — do not add
`technologies/redis`; RabbitMQ is owned by
[`backend/rabbitmq_deep_dive`](../backend/rabbitmq_deep_dive/rabbitmq_deep_dive.md) — do not
add `technologies/rabbitmq`; Elasticsearch is owned by
[`database/elasticsearch_internals`](../database/elasticsearch_internals/elasticsearch_internals.md)
— do not add `technologies/elasticsearch`; PyTorch is owned by
[`ml/pytorch_deep_dive`](../ml/pytorch_deep_dive/pytorch_deep_dive.md) — do not add
`technologies/pytorch`; OpenTelemetry is owned by
[`devops/observability_tracing_and_otel/opentelemetry_deep_dive.md`](../devops/observability_tracing_and_otel/opentelemetry_deep_dive.md)
— a deep-dive SUB-FILE, not a module, because the parent module is already named for it —
so do not add `technologies/opentelemetry`. KServe, llm-d, Kueue, Volcano and KAI Scheduler are owned by
[`mlops/`](../mlops/README.md) (KServe as
[`mlops/model_serving_platforms/kserve_deep_dive.md`](../mlops/model_serving_platforms/kserve_deep_dive.md)),
so do not add `technologies/kserve` or a module for any of the others. Triton and OpenVINO
predate `mlops/` and stay here as a recorded exception; `mlops/model_serving_platforms`
links to them. Tokio is owned by
[`rust/async_runtimes_and_executors/tokio_deep_dive.md`](../rust/async_runtimes_and_executors/tokio_deep_dive.md),
a deep-dive sub-file, so do not add `technologies/tokio`. axum, tonic, sqlx and serde are
taught in `rust/` modules. This section is for technologies that don't
already have a canonical home — an orchestrator like Airflow or an inference server like Triton has
no single owning section, so it lives here instead.

**Naming convention:** `<vendor>_<product>`, lowercase snake_case. Disambiguate
collisions explicitly in the module's intro — e.g. `nvidia_triton_inference_server`
(NVIDIA's model-serving server) vs [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/triton_and_kernel_dsls.md)
(OpenAI Triton, the GPU kernel DSL). Same word, unrelated products; both files must
say so on first mention.

**Five disambiguation precedents, all by extending the slug rather than adding a marker:**

1. *Vendor differs from a same-named product* — spell out the full product descriptor.
   `nvidia_triton_inference_server` (2026-07) took this route rather than
   `triton_server` or `triton_nvidia`.
2. *Vendor and product are the SAME word, and that word is a common English adjective* —
   extend with the product's own **category term**. `temporal_durable_execution`
   (2026-08-04) took this route because the convention degenerates here:
   "Temporal Technologies" makes `temporal_temporal` absurd, and a bare `temporal`
   would collide with five unrelated senses already in this repo (temporal locality in
   `cs_fundamentals`, temporal queries in `backend/event_sourcing_and_cqrs`, temporal
   coupling in `lld/behavioral/command`, temporal decoupling in
   `backend/event_driven_fundamentals`, and `java.time.temporal.Temporal` in
   `java/java_time_datetime`) plus Temporal Fusion Transformer in `ml/`. The category
   term also does real work here: it names the half of the tier role
   `data-movement/workflow-and-durable-execution` that means Temporal, keeping it
   distinct from `apache_airflow`, which owns the "workflow" half.
3. *Vendor is a foundation, not a company, and the product's own name already carries a
   descriptor* — use the product's full self-name. `envoy_proxy` (2026-08-04) took this
   route. Envoy has no vendor in the `<vendor>_` sense: Lyft originated it in 2016,
   donated it to the CNCF in September 2017, and it graduated in November 2018 with
   maintainers across Google, Lyft, Tetrate, Bloomberg and IBM. `cncf_envoy` names a
   foundation, not a vendor, and would generalize to `cncf_istio`/`cncf_prometheus`;
   `lyft_envoy` is factually wrong today. A bare `envoy` has **no collision problem in
   this repo** — verified 2026-08-04, the only other `envoy` string is French prose in an
   LLM translation example — but a one-token slug would be the section's first break of
   the two-token shape. The product's own name, used by its domain (`envoyproxy.io`), its
   GitHub org and its docs title, resolves all three: **`envoy_proxy`**.
4. *Vendor and product are distinct words and the convention resolves cleanly* — just apply
   it. `hashicorp_vault` (2026-08-04) is recorded as a precedent **because the corporate
   owner changed without the engineering name changing**, which is the only reason it was
   ever in doubt. IBM closed its $6.4B acquisition of HashiCorp on **2026-02-27** and the
   product's brand is now "IBM Vault (formerly HashiCorp Vault)", with IBM named as the BUSL
   Licensor — so `ibm_vault` is arguably the *current* name. It is still the wrong slug.
   The repository is `hashicorp/vault`, the Go module is `github.com/hashicorp/vault`, the
   docs domain, the Helm chart and the Terraform provider all say hashicorp, all ~20 existing
   citations across this repo write "HashiCorp Vault", and — decisively — **the tech-bank
   join key is the DISPLAY name** (`### HashiCorp Vault`), so renaming the slug would either
   orphan that record or require a homonym entry for no gain. The vendor prefix also does
   real disambiguation work here, against Azure Key Vault, Ansible Vault and
   `spring-cloud-vault-config`, all three of which are indexed. **The rule to carry forward:
   follow the ENGINEERING name — repo, module path, package, docs domain — not the
   marketing or corporate-ownership name. Put the ownership fact in §1 instead**, which
   `hashicorp_vault.md` does under `### Vendor, licence and governance`.
5. *Vendor is a corporate SPONSOR whose own same-named product is a DIFFERENT artifact, and
   the product name is a single coined token with no built-in descriptor* — extend with the
   product's own **category term**. `debezium_change_data_capture` (2026-08-04) took this
   route, and it is precedent 2's mechanic reached for a different reason: Temporal extended
   because the vendor half *degenerated*, Debezium extends because the vendor half is
   *wrong*. `redhat_debezium` names the **Red Hat build of Debezium**, a separately
   versioned downstream product inside Streams for Apache Kafka with its own lifecycle —
   so the slug would name the distribution while the page teaches upstream. `cncf_debezium`
   is unavailable: Debezium is **not** a CNCF project (Apache 2.0, community-governed, no
   foundation). Precedent 3's mechanic yields nothing either — Envoy resolved to
   `envoy_proxy` because its self-name carries a descriptor (`envoyproxy.io`), while
   Debezium's self-name is one coined token (`debezium.io`, org `debezium`, docs titled
   "Debezium Documentation") with no second token to borrow. The category term also states
   the boundary against [`backend/kafka_deep_dive`](../backend/kafka_deep_dive/kafka_deep_dive.md)
   in the module id itself, which is exactly the work precedent 2's term does against
   `apache_airflow`. **Spelled out, not `debezium_cdc`, for a checkable reason:**
   `titleize()` in `game/app.js` title-cases each token against the `ACRONYMS` map and
   **`cdc` is not in it** (the map has `cdn`, `cqrs`, `cd`, `ci`), so `debezium_cdc` would
   render "Debezium **Cdc**" in the reader nav, the command palette, `fileLabel()` and the
   Study tree. Do NOT fix that by adding `cdc` to `ACRONYMS` — that edits game code to
   rescue a naming choice and title-cases `cdc` in every unrelated file label.

---

## Module List

Listed in `STUDY_ORDER.technologies` order, which groups modules by domain:

1. **Orchestration pair** - `apache_airflow`, `temporal_durable_execution`.
2. **Serving pair** - `nvidia_triton_inference_server`, `intel_openvino`.
3. **Traffic and data-plane pair** - `envoy_proxy`, `cilium`. Both earlier pairs *run your
   workload*; these two *move traffic to* it. Cilium sits next to Envoy because its L7
   policy runs through an embedded Envoy (`cilium` §6.4).
4. **Credential layer** - `hashicorp_vault`, a control-plane dependency of everything, not
   a traffic component.
5. **Data run** - `debezium_change_data_capture`, `apache_iceberg`, `apache_flink`,
   `trino`: capture changes, land them in a table format, process the stream, query the
   result. Debezium was deliberately not inserted beside Temporal, which would split the
   orchestration pair for a surface similarity (both are fed by a durable log).

A new module goes into the group it belongs to, not at the end. Do not append blindly.

| Dir | Category | Key Concepts | Version Studied |
|-----|----------|-------------|-----------------|
| [`apache_airflow/`](apache_airflow/apache_airflow.md) | Workflow orchestration | Scheduler loop, executors (Local/Celery/Kubernetes), DAGs, deferrable operators, backfills, HA scheduler | Airflow 3.3.2 |
| [`temporal_durable_execution/`](temporal_durable_execution/temporal_durable_execution.md) | Durable execution | Two-plane split (Service never runs your code), event history + replay determinism, activities and the four timeouts, signals/queries/updates, Continue-As-New and the 51,200-event limit, versioning via patching vs Pinned Worker Deployments, shard immutability | Temporal Server 1.32.1 |
| [`nvidia_triton_inference_server/`](nvidia_triton_inference_server/nvidia_triton_inference_server.md) | GPU model serving | Model repository, `config.pbtxt`, backends (TensorRT/ONNX/PyTorch/Python), dynamic batching, ensembles/BLS, `perf_analyzer` | NGC release studied inline per module |
| [`intel_openvino/`](intel_openvino/intel_openvino.md) | CPU/edge inference & model optimization | `ov::Core` + device plugins (CPU/GPU/NPU), IR (`.xml`/`.bin`), `ovc`/`convert_model`, AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, `PrePostProcessor`, OVMS, `openvino-genai` | OpenVINO 2026.4.1 |
| [`envoy_proxy/`](envoy_proxy/envoy_proxy.md) | L7 proxy / service-mesh data plane | Listener/filter-chain/route/cluster/endpoint model, xDS (LDS/RDS/CDS/EDS/SDS, ADS, Delta), LB policies plus locality/priority/panic mode, outlier detection and the `enforcing_*` trap, circuit breaking as five resource ceilings, retry budgets, the seven-layer timeout stack, stats cardinality, `%RESPONSE_FLAGS%`, Wasm/Lua/ext_authz/ext_proc/dynamic modules, admin interface and draining | Envoy 1.40.0 |
| [`cilium/`](cilium/cilium.md) | eBPF networking / CNI | Pod-to-pod, pod-to-Service and egress packet paths; kube-proxy replacement; `CiliumNetworkPolicy` and the default-deny DNS trap; the embedded Envoy for L7 rules; Hubble; Cluster Mesh; BGP Control Plane; Gateway API and Cilium Ingress; Cilium Service Mesh vs Istio Ambient; XDP, Maglev and DSR; netkit; WireGuard vs IPsec; Bandwidth Manager | Cilium 1.20.3 |
| [`hashicorp_vault/`](hashicorp_vault/hashicorp_vault.md) | Secrets management / identity broker | Barrier and the four-layer key hierarchy, seal/unseal and why recovery keys are not unseal keys, Integrated Storage, every secrets-engine family, the KV v2 policy trap, **leases and the lease-count arithmetic** (halving the TTL changes nothing and doubles issuance), auth methods and secret-zero, `bound_claims` vs `claim_mappings`, policies and identity, response wrapping, audit refusal, Agent/VSO/CSI, quotas, rekey vs rotate, and the OpenBao delta | Vault 2.2.0, OpenBao 2.7.1 |
| [`debezium_change_data_capture/`](debezium_change_data_capture/debezium_change_data_capture.md) | Change data capture | The two questions — where do I start, can I keep reading; logical decoding and output plugins; what a replication slot pins (`restart_lsn`, `confirmed_flush_lsn`, `catalog_xmin`); `flush.lsn.source`, heartbeats and the idle-captured-table trap; `REPLICA IDENTITY` and the `before: null` consequence; TOAST and `__debezium_unavailable_value`; snapshot modes and their removals; the incremental-snapshot watermark algorithm; the four-quadrant position-loss recovery table and why a lost slot is data loss; the never-compact schema-history topic; ordering per key / per table / never across tables; `ExtractNewRecordState` and the five tombstone modes; the `EventRouter` defaults; why `tasks.max` is ignored | Debezium 3.7.0.Final |
| [`apache_iceberg/`](apache_iceberg/apache_iceberg.md) | Lakehouse table format | Spec v2 vs v3; the metadata tree (metadata.json / manifest list / manifests) and the atomic pointer swap; hidden partitioning and partition evolution; column-ID schema evolution; copy-on-write vs merge-on-read; deletion vectors; snapshot expiry and orphan-file cleanup; compaction economics; the catalog tier (REST, Polaris, Glue, Nessie); engine support. The vendor-neutral concept layer lives in `../database/lakehouse_and_table_formats/` | Iceberg Java 1.12.0 |
| [`apache_flink/`](apache_flink/apache_flink.md) | Stream processing | Event time and watermarks; asynchronous barrier checkpointing; checkpoint vs savepoint; HashMap, RocksDB and ForSt state backends; two-phase-commit sinks; backpressure diagnosis; Flink SQL and dynamic tables; restart strategies and failover regions; FlinkCEP; state schema evolution; Reactive Mode and the Operator autoscaler; `maxParallelism` and key groups; Materialized Tables | Flink 2.3.0 |
| [`trino/`](trino/trino.md) | Distributed SQL query engine | Query lifecycle; memory pools and the low-memory killer; task concurrency and scheduling; fault-tolerant execution; cost-based optimizer and table statistics; dynamic filtering; the Iceberg connector; federation and cross-catalog joins; `EXPLAIN ANALYZE`; session vs configuration properties; access control; file system cache | Trino 484 |

---

## Module Template

Every module page (`<module>/<module>.md`) follows the standard 14-section template (see root `CLAUDE.md`):

```
## 1. Concept Overview
## 2. Intuition
## 3. Core Principles
## 4. Types / Architectures / Strategies
## 5. Architecture Diagrams
## 6. How It Works — Detailed Mechanics
## 7. Real-World Examples
## 8. Tradeoffs
## 9. When to Use / When NOT to Use
## 10. Common Pitfalls
## 11. Technologies & Tools
## 12. Interview Questions with Answers
## 13. Best Practices
## 14. Case Study
```

**Technology-flavor interpretation** of four sections that differ from a pure-concept
module:

| Section | Technology-flavor interpretation |
|---------|-----------------------------------|
| §4 Types / Architectures / Strategies | Process/component taxonomy and deployment topologies — e.g. Airflow's webserver/scheduler/triton/workers/metadata-DB split under each executor; Triton's server process + backend model instances + model repository layout |
| §6 How It Works | Internals **with concrete configs** — an annotated `airflow.cfg` (real default values, not placeholders) or an annotated `config.pbtxt` (real default `max_batch_size`, instance groups, dynamic-batching timeouts) |
| §11 Technologies & Tools | Ecosystem & integrations — what plugs into this technology (Airflow: Kubernetes executor, Great Expectations, dbt; Triton: TensorRT, ONNX Runtime, Kubernetes + KServe/Triton Operator) |
| §14 Case Study | An **inline mini case study** (no `case_studies/` sub-directory at section launch — see below) |

**No `case_studies/` directory yet.** Unlike concept sections, this section launches
without a `case_studies/` sub-directory; §14 of each module carries a self-contained
mini case study instead. Add a `case_studies/` directory (11-section principal
template, `case_studies/case_studies.md` learning-path index) only once a module family
needs a case study too large for §14 — follow the root `CLAUDE.md` case-study rules
when that happens.

---

## Q&A Minimums

**16 Q&As minimum per module** — the repo floor is 15 (root `CLAUDE.md`); this section
standardizes one above the floor, and flagship modules go well past it when the
technology's surface demands it (the flagship modules carry 22, 30, 32 and 34). Format from the start: `**Q: <question>?**` bolded
question prefixed `Q: `, plain-text answer, first sentence a self-contained direct
answer 15–220 characters (see root `CLAUDE.md` Interview Q&A Rules and the Game/Reader
Q&A extraction contract — the length window and bold-question rule are load-bearing
for `extract.py`, not cosmetic).

---

## Version Pinning

Every module states the **version studied** up front (in its intro, §1 Concept
Overview) and tags version-specific features inline as they come up — e.g.
`[Airflow 3.0+]` for the React-based UI and DAG versioning, `[Airflow 2.7+]` for
deferrable operators. Triton modules tag by NGC container release (e.g.
`[24.05-py3]`) since Triton doesn't use simple semver for feature gating. Never
describe a feature as current without naming the version it landed in — this section
covers fast-moving infrastructure products where defaults and flags change release to
release.

---

## Learning Paths (Full-only — tiers deliberately deferred past the threshold)

The `README.md` `<!-- study-paths -->` block lists every file with `-` (Full path only), so
`questions/paths.json` has no `technologies` key and the section is Full-path only. The
Study view's tier tabs **auto-hide** for any section absent from the derived paths (`book`
is the other such section), so this is a deliberate omission, not a gap.

**Status (owner-set 2026-08-04):** the section crossed the 4-module threshold with
`temporal_durable_execution`, and the tier decision was **deliberately deferred** to a
separate change once the planned technology pages land. The deferral **still stands** for
every module in `STUDY_ORDER.technologies`. Do not read the un-tiered state as an
oversight, and do not tag one module's files with a tier on its own: tiering is a
section-wide decision plus a one-time `README.md` marker-pair setup, and doing it
piecemeal produces a Senior path that silently advertises a partial section.

**A note for whoever does the tiering.** The section has deep-dive sub-files (see
[Deep-Dive Sub-Files](#deep-dive-sub-files)), so the tiers can differ per file and not only
by module membership. `temporal_durable_execution/versioning_and_safe_deploys.md` is the
obvious first principal-only file, since safe-deploy strategy across a fleet of
long-lived executions is a judgment call.

**The mechanics, when the deferral ends:** decide the tiers — Senior (the craft: operate it, debug it) and Principal (the
judgment: adopt it or not, at what cost) are different cuts, not nested depths — then
replace the `-` on each participating file in this section's `README.md` `<!-- study-paths -->`
block with its tiers (the module page must carry every tier the module is in), paste an empty `<!-- study-path-table senior -->` /
`<!-- /study-path-table -->` marker pair into `README.md` where the table should sit
(placement is editorial; `--write-paths` only fills blocks that already exist), and run
`python3 extract.py --write-paths` to generate it. Also update the toggle-exception
language in `game/CLAUDE.md` in the same commit — it lists `technologies` among the
sections with no tiers. **`game/README.md` carries no such language** (verified
2026-08-04 by grep); the older instruction to update it too was stale and is corrected
here. There is **no array in `app.js`** to add.

---

## `tech_bank/` — the technology knowledge bank (DATA, not a module)

`technologies/tech_bank/` is the **source of truth for `game/tech_index.json`**: what each
of the ~3,800 indexed tools *is* and what problems it solves, independent of which module
teaches it. It is authored markdown, committed; the JSON is **generated and gitignored**,
regenerated by CI on every push exactly like `questions/*.json`, `paths.json`, the
relatedness graphs and the pre-rendered Mermaid `.mmz` assets.

**It is not a module and must never become one.** `extract.py` excludes it from BOTH walks
by exact relative path (`TECH_BANK_DIR` / `in_tech_bank()`, in `game/build_tech.py`), so nothing here is in
`STUDY_ORDER`, contributes a question, appears in the reader's module tree, or carries a
`<!-- study-paths -->` block. Three measured hazards make the exclusion load-bearing:

| If it were… | What happens | Loud? |
|---|---|---|
| a plain module dir under `technologies/` | lands in `index.files` with `moduleCounts: 0` → a **phantom module** in the reader tree, prev/next chain, command palette and Study skill tree | no — `--strict` exits 0 |
| a shard carrying a `## 11. Technologies & Tools` table | `build_tech_index()` indexes the bank **into itself** (3809/613 → 3810/614) | no |
| a file at the SECTION ROOT with a `## 12. Interview Questions` heading | `module` collapses to the 1-segment `technologies` → `WIRING ERROR: STUDY_ORDER gap` | yes — deploy dies |

Because the exclusion is silent by design, `build_tech_bank()` **fails the build under
`--strict` on any `## NN.` section-template heading** anywhere under `tech_bank/` — that
heading is the signature of someone mistaking this data directory for study content.

### A `## 11` cell can drop its tool SILENTLY — the shapes that do it (measured 2026-08-04)

The index is keyed on **column 0** of a `## 11. Technologies & Tools` row. Three cell shapes
make that column yield nothing or the wrong thing, with no warning anywhere:

| Cell shape | What happens | How it surfaced |
|---|---|---|
| A parenthetical containing its own parens, pushing the cell past `clean_tech_name`'s **42-char ceiling** — ``PyTorch AMP (`torch.autocast` + `torch.amp.GradScaler("cuda")`)`` (59 chars) | the trailing-parenthetical strip is defeated by the nested parens, the cell blows the length cap, and the row yields **ZERO tools** | only as an orphaned bank record — the tool vanished from the index and nothing complained |
| More than **5 spaces** in the cell | the ` / `, ` + `, ` & ` splitter bails and the row is dropped entirely — `oneAPI plugins for NVIDIA and AMD GPUs` (6 spaces) would go | caught before it landed; the fix was dropping the trailing `GPUs` |
| A ` / ` between two real products — `Confluence / runbooks-as-code in Git`, `oneAPI plugins for NVIDIA / AMD GPUs` | splits into two "tools", one of which is debris (`AMD GPUs`, `runbooks-as-code in Git`) | phantom rows with no bank record |

**Keep column 0 to a bare product name.** Flags, APIs, launch syntax and paths belong in the
description column: `cuobjdump` not `cuobjdump --dump-resource-usage <binary>`,
`cudaStreamTailLaunch` not `kernel<<<g, b, 0, cudaStreamTailLaunch>>>()`, ``EEVDF
`base_slice_ns` `` not `/sys/kernel/debug/sched/base_slice_ns`.

### Two more shapes, both in BULLETS rather than tables (found 2026-08-04)

`tech_from_bullets()` has its own pair, and they fail in opposite directions — one loses
tools, one invents them. Both were hit by agents writing new modules the same day.

| Bullet shape | What happens | How it surfaced |
|---|---|---|
| A bullet that **wraps onto a second physical line** | the parser matches only the FIRST physical line, so every tool past the wrap is dropped. A Redis §11 Measurement bullet lost `LATENCY DOCTOR` and `RedisInsight` this way | orphaned bank records; the bullet reads perfectly as source |
| Ordinary bold **emphasis** inside a *labelled* bullet — `- **Sinks:** exactly **one** per connector` | in a labelled bullet EVERY later bold span is a tool, so `one` was indexed as a product (`3687 of 3688`) | the coverage count, and nothing else |

Note the asymmetry, because it decides where emphasis is safe: in a **dashed** bullet the
blurb is `split("**", 2)[-1]`, so later bolds are prose and harmless; in a **labelled**
bullet they are all tools. Wrapping is unsafe in both. So: keep every §11 bullet on one
physical line, and never bold anything in a labelled bullet that is not a product name.

Neither shape fails the build. The wrapped bullet shows up only as a bank record nothing
teaches; the stray emphasis only as an indexed tool with no record. **Read the coverage
line — the exit code cannot see either.**

### The §11 lint, and the one thing you must never do about an orphan (2026-08-25)

Every shape above is a **citation** defect. The fix is always to repair the cell or the
bullet, never to make the warning go away at the other end.

**NEVER author a bank record for extractor debris. That is the bug reporting itself.**
An orphan warning means "a record exists that no module teaches"; writing a record for a
phantom the extractor manufactured inverts it into "coverage is clean", which permanently
blinds the only check that can see this class of defect. Eight such records were found and
removed on 2026-08-25 — `AI`, `custom`, `on AWS`, `When each wins`,
`Most large tech companies`, `IDE "expand inferred type`, `/client` and (introduced and
caught in the same pass) `per-slice under MIG`. Five of them **stated in their own
`**Short:**` line that they were not products**, which is what a record written to silence
a warning looks like. Their citations were repaired instead, in
`technologies/nvidia_triton_inference_server`, `hld/microservices`,
`ml/ml_system_design/ab_testing_for_ml`, `llm/ai_applications`,
`llm/data_flywheels_and_continuous_learning`,
`backend/distributed_transactions_and_consistency`,
`java/generics_and_type_system/type_inference_and_bounds` and
`llm/mcp_model_context_protocol`.

**Repairing a citation can RECOVER tools, not only remove phantoms.** The `hld/microservices`
Service Mesh cell read `... Consul service mesh; on AWS, Amazon ECS Service Connect (ECS)
and VPC Lattice (EKS)`. That invented `on AWS` **and** silently dropped both AWS products,
because after the parenthetical strip the tail `Amazon ECS Service Connect  and VPC Lattice`
exceeds the 5-space limit and the whole fragment is discarded. One malformed cell, one
phantom and two real tools lost. Expect a repair to change the tool count in both
directions.

**The lint, before you commit a `## 11` section:**

1. No labelled bullet (`- **Label:** ...`) whose **label** is not a product — the label
   becomes the blurb, but every *later* bold span becomes a tool, so a labelled bullet is
   only safe when everything bolded after the colon is a real product name.
2. No **dashed** bullet (`- **X** — ...`) whose first bold is not a product. `**When each
   wins** —` and `**DCGM exporter pairing** —` both indexed as tools. Convert a summary or
   caveat bullet to the labelled form (`- **When each wins:** ...`), which indexes nothing.
3. No ordinary **emphasis** inside a labelled bullet. Use `*italics*`.
4. No bullet wrapping onto a second physical line.
5. No ` / `, ` + ` or ` & ` inside a single tool name in column 0 or a bold span — those
   are splitters. Between two real products they are correct and useful; the defect is
   using them to join a product to prose (`Postgres logical replication + custom`,
   `Optimizely / custom`, `Epic + AI`).
6. No quotes inside column 0 — `IDE "expand inferred type"` loses its trailing quote to the
   strip and lands as an unbalanced name.
7. Column 0 stays a **bare product name**. Prose, flags, versions and "who uses it" belong
   in the description column. A grouped table (`| Category | Tools |`) takes its names from
   **column 1** instead, so the same rule applies there — `| In-House Platform | Most large
   tech companies |` indexed the prose.
8. When you rename a tool, use the exact `### ` name already in the bank. `Seldon` and
   `AWS VPC Lattice` are uncovered; `Seldon Core` and `Amazon VPC Lattice` are the records.

**Check it in-process, which does not race other agents.** `extract.py` writes
`game/questions/`, so concurrent runs collide; this reads the same functions and writes
nothing:

```bash
cd src/main/java/com/rutik/systemdesign/game && python3 -c "
import sys; sys.path.insert(0,'.')
import build_tech as bt
idx=bt.build_tech_index(); r=bt.build_tech_bank(); bank=r[0] if isinstance(r,tuple) else r
d={t['n'] for t in idx['tech']}
orph=sorted(set(bank['tools'])-d); unc=sorted(d-set(bank['tools']))
print('coverage: %d of %d; %d orphans'%(len(d)-len(unc),len(d),len(orph)))
print('orphans:',orph)"
```

To trace a phantom back to the cell that made it, print `t['u']` for that record and map
each `modIdx` through `idx['modules']`. Do that **before** deciding a record is debris —
two of the seven citations recorded in the 2026-08-25 audit were traced to the wrong file
by grep, and the extractor's own index was what corrected them.

**A factual audit is the highest-risk time for this bank.** Renaming a tool is exactly what
an audit should do (`tfsec` -> `Trivy`, `Kaniko` -> `rootless BuildKit`, `OpsGenie` -> `Jira
Service Management Operations`), but to the index a rename is indistinguishable from
deleting one tool and inventing another: the old record orphans and the new name has none.
The 2026-08-04 devops/cuda/cs_fundamentals audit moved coverage from `3579 of 3579, 0
orphans` to `3544 of 3607, 35 orphans` — a silent regression, because **the coverage check
is deliberately non-fatal** (a degraded Technologies screen beats a broken quiz, so it warns
and continues). Always re-run it after any pass that touches `## 11` tables:

```bash
cd src/main/java/com/rutik/systemdesign/game && python3 extract.py 2>&1 | grep "bank coverage"
```

For a pure rename, **rename the `### <name>` heading and alias the old written form** — that
preserves the authored description. Delete-and-reauthor only for a genuine replacement
(Jeli is not PagerDuty Post-Incident Reviews). And check whether the rename changed what the
record is ABOUT: `OLM` -> `OLM v1` kept the heading but needed wholly new prose, because v1
replaced Subscription/CatalogSource/CSV resolution with `ClusterExtension`.

### Layout

```
tech_bank/
├── tech_bank.md      ← the TAXONOMY: 6 kinds, 8 language tokens, 18 tiers, 95 roles
├── caching.md        ← one shard per tier, named for the tier id
├── data-stores.md
└── …                 ← 18 shards
```

Shard by **primary tier** — the tier of the tool's FIRST role. Roles are weight-ascending
in every record, so "first" is "best"; ties at weight 1 are rare (26 when last counted). A tool
lives in exactly one shard and declares all of its roles there, so Redis is filed under
Caching and still carries its key-value, rate-limiting, broker and semantic-cache roles.
A record whose primary tier disagrees with its shard is a **warning**, not an error.

**Each shard's preamble opens with a tool count, and it is a SNAPSHOT that nothing
validates.** It counts exactly the `### ` records in that file — verified 2026-08-04
against the index, where "records in the shard" and "indexed tools whose primary role is
this tier" are the same set by construction, so there is no second meaning to hunt for.
All 18 had drifted (17 wrong, one accidentally correct because a module addition happened
to close the gap) and were recomputed. Nothing in the build checks them, so update the
number in the same commit that adds or removes a record:

```bash
cd technologies/tech_bank
for f in *.md; do [ "$f" = tech_bank.md ] && continue
  printf '%-22s preamble=%-5s actual=%s\n' "$f" \
    "$(grep -ohE '^The [0-9]+ tools whose PRIMARY' $f | grep -oE '[0-9]+')" \
    "$(grep -c '^### ' $f)"
done
```

### The record contract — the `**Short:**` Q&A contract, applied to a tool

```markdown
### Redis
**Short:** In-memory key-value server used as distributed cache, session store, rate-limit counter and pub/sub bus.
**Kind:** tech
**Lang:** *
**Roles:** caching/distributed-cache @1, data-stores/key-value-and-embedded @1, traffic-edge/rate-limiting-and-resilience @2

Redis holds its entire dataset in RAM and executes commands on a single thread, so every
command — including a multi-key Lua script — is atomic without you taking a lock.

Reach for it when the working set fits in memory and you want sub-millisecond reads.
```

- **`**Short:**` is REQUIRED and bounded 15–220 chars** — the same `SHORT_MIN`/`SHORT_MAX`
  constants the Q&A contract uses, reused rather than re-declared, for the same reason: it
  is the always-visible row line, so a bad value ships to the user either way.
- `**Kind:**` one kind id; `**Lang:**` comma-separated language tokens (`*` = polyglot, the
  literal JSON token, never a friendly alias); `**Roles:**` comma-separated
  `tier/role @weight`, weight `1|2|3`, best first. Every id must exist in `tech_bank.md`.
- The **field block is contiguous and starts on the line after the `###`**, so a bolded
  sentence inside a description can never be mistaken for a field.
- The paragraphs after the blank line are the **description**, revealed when the row is
  expanded. **Per-record opt-in**, exactly like `**Short:**` in a Q&A: a record without one
  emits no `d` key and renders as it does today, so the description pass is resumable
  shard by shard forever. Hard-wrapped lines inside a paragraph are joined with a space.
- **A description with no short line is FATAL. A short line with no description is the
  normal state** (and the permanent terminal state for the 1,063 `api` symbols).
- Descriptions are **escaped, not rendered** — no fences, lists, tables or links; that is
  a `--strict` failure. Backtick code spans are the one inline form to use.
- **Nothing but records after a shard's preamble.** A footer would be read as the last
  record's description.

Also fatal: an unknown `**Kind:**`, language token or role id, a malformed `@weight`, a
record with zero roles, a duplicate `###` name across shards, an unknown field name, and
— since 2026-07-31 — a **markdown heading or an emoji** inside a description. Both survive
the parser and ship as literal escaped text in the reader; `###` opens a new record so it
can never reach the check, but `##` and below can. The emoji range deliberately carves out
`U+2713..U+2718`, because the repo style guide asks for `✓`/`✗` and banning the whole
dingbats block would fail the build on content it requests.

Warn-only: an **orphan** record no module teaches (it can never render) and the shard/tier
mismatch above. `extract.py` prints coverage both ways every build.

**Deliberately NOT checked: whether a description merely restates its `**Short:**` line.**
Word-overlap between the two was tried at several thresholds and does not separate — the
house style opens by recapping the tool and then expands, so well-written records score the
same as the one genuine offender (five of seven hits were false positives on inspection).
It shipped as a warning nobody could act on, which is worse than no check. Catch it in
review.

### The name is the join key, so two exception tables exist

`tech_key()` in `extract.py` decides which written forms are the same tool. The record's
`###` name must match the DISPLAY name the index settles on, or the record orphans and its
description never renders. Two hand-kept tables handle what no rule can derive:

| Table | Problem | Example |
|---|---|---|
| `TECH_ALIASES` | many names, ONE product | `envoy proxy` -> `envoy`, `grafana loki` -> `loki`, `huggingface transformers` -> `transformers` |
| `TECH_HOMONYMS` | one name, TWO unrelated products | `medusa` in `llm/` -> `Medusa (speculative decoding)`, leaving `Medusa` to the Cassandra backup tool |

They cannot be one mechanism: the alias key is derivable from the string alone, the homonym
key needs an outside signal (the section that teaches it).

**A homonym needs BOTH halves of the fix.** Splitting the key yields two rows, but the
display name is voted on from the surface forms modules actually wrote — and both halves of
a homonym were written the same way. `_HOMONYM_LABELS` overrides that vote. Without it you
get two identically-named rows AND, because the bank lookup is keyed on display name, both
receive the same record: a wrong answer wearing the costume of a duplicate.

**After touching either table, check the coverage line both ways.** `bank coverage: N of N
indexed tools have a record; 0 bank records no module teaches` is the only thing that
notices a merge which left an old record stranded.

**Prefer fixing the CITATION over adding an alias, and always ask the homonym question
first.** An alias is permanent and global: it rewrites that key for every section, forever.
Two duplicates were resolved on 2026-08-04 and they went opposite ways for exactly this
reason.

- `Vault` / `HashiCorp Vault` — the bare word was manufactured by two §11 cells, not
  written deliberately (`External Secrets Operator / Vault` split on the ` / `, and
  `Vault (HashiCorp)` had its parenthetical stripped). Both cells were rewritten,
  the duplicate record deleted, **and** an alias added as a safety net, because nothing
  else in the repo is called plain `Vault` — `Ansible Vault` and `Azure Key Vault` are
  their own keys.
- `Maxwell` / `Maxwell's Daemon` — same shape, and an alias would have been a **trap**.
  `cuda/` discusses NVIDIA's Maxwell GPU architecture at length, so aliasing the bare
  word would silently turn a future cuda §11 `Maxwell` into a MySQL binlog reader. The
  single citation was changed and the record deleted; no alias.

The test is not "are these the same product today" but "could a future author write this
bare word meaning something else". If yes, fix the citation and leave the key alone.

### Adding or editing a tool

1. Edit the shard for its primary tier (add the `###` record in alphabetical position).
2. Run `python3 extract.py --strict` from `game/`. Confirm the tool count, the module-file
   count and every per-section question count are unchanged — a change to any of those
   means the bank leaked into a walk.
3. `bash scripts/build_banks.sh` re-runs the round-trip guard: every record must still
   carry `k`/`r`/`l`/`s` and no key beyond those plus `d`.

Never hand-edit `game/tech_index.json` — it is overwritten on every build.

---

## Deep-Dive Sub-Files

| Sub-file | Parent module |
|----------|---------------|
| [`assets_partitions_and_the_state_store.md`](apache_airflow/assets_partitions_and_the_state_store.md) | `apache_airflow` |
| [`versioning_and_safe_deploys.md`](temporal_durable_execution/versioning_and_safe_deploys.md) | `temporal_durable_execution` |
| [`dynamo_and_the_nvidia_serving_stack.md`](nvidia_triton_inference_server/dynamo_and_the_nvidia_serving_stack.md) | `nvidia_triton_inference_server` |
| [`gateway_api_and_inference_extension.md`](envoy_proxy/gateway_api_and_inference_extension.md) | `envoy_proxy` |
| [`workload_identity_and_the_end_of_stored_secrets.md`](hashicorp_vault/workload_identity_and_the_end_of_stored_secrets.md) | `hashicorp_vault` |
| [`cdc_across_failover_and_into_the_lakehouse.md`](debezium_change_data_capture/cdc_across_failover_and_into_the_lakehouse.md) | `debezium_change_data_capture` |

Sub-files follow the repo-wide convention: `<module>/<topic>.md`, full 14-section
template, 15+ Q&As. `extract.py` auto-groups a sub-file under its parent module's
existing `STUDY_ORDER` position by directory - **no `STUDY_ORDER` change is needed**
when adding a sub-file, only when adding a new top-level module directory. Add each new
sub-file to this table and to the `README.md` `<!-- study-paths -->` block in the same
commit.

---

## How to Add a Technology

1. Create `<vendor>_<product>/<vendor>_<product>.md` — 14 canonical sections, 16 Q&As, version
   studied stated up front.
2. **Add the slug to `STUDY_ORDER.technologies` in `game/app.js` in the same commit, at
   its learning-path position — not necessarily at the end.** The array drives reading
   order, so related modules belong adjacent: Temporal was inserted at position 2, next
   to Airflow, because its §8 and §9 are largely "why this is not Airflow", and Triton
   and OpenVINO stay adjacent as the serving contrast pair. `extract.py --strict` runs
   in Pages CI and **fails the deploy** if a module that produced Q&As is missing from
   `STUDY_ORDER` — this is not optional housekeeping.
3. Update this file's Module List table and `technologies/README.md`'s Module Table /
   Learning Path / Knowledge-Question Map / Study Plan.
4. Update root `README.md`'s Technologies section and root `CLAUDE.md`'s section
   table row (module count).
5. Run `python3 extract.py --strict` locally from `game/` before committing, to catch
   wiring gaps before CI does.
6. Author diagrams via the `/mermaid-diagrams` skill (Mermaid is the default form for
   flows/topologies/lifecycles) and `/visual-intuition-diagrams` for anything Mermaid
   can't draw (constraint grids, alignment-critical layout, vector geometry) — e.g. an
   annotated `config.pbtxt` field-by-field breakdown.

---

## HARD RULE — structure lives in `README.md`, content files hold only content

**The section `README.md` is the single source of truth for this section's file inventory
and study-tier membership.** Its `<!-- study-paths -->` block lists EVERY module, EVERY
file that module owns (the module page AND every deep-dive sub-file), and EVERY case
study, each tagged with the tiers it belongs to — `-` means Full path only. Reading that
one block tells you every file in the section and which paths it is on.

**A content file carries NO structural metadata.** A module page or a deep-dive sub-file
holds the content of its topic and nothing else — no `<!-- study-paths -->` block, no tier
declaration, no path membership. That metadata used to live in each module page; it was
moved here so there is one place to look and one place to change.

**Adding a file? Add its line to the section README's block in the same commit.** A file on
disk that is missing from the block — or listed there and absent from disk — FAILS
`python3 game/extract.py --strict` and takes the Pages deploy red. That check exists
because the old failure was silent: a new sub-file was invisible to the curated paths, or
silently dragged into every tier its parent was in, with a green build either way.

Order is never declared in the block: it comes from `STUDY_ORDER` in `game/app.js`. The
tier TABLES further down the README are GENERATED from the block by
`python3 game/extract.py --write-paths` — never hand-edit them.
