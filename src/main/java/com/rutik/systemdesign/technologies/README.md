# Technologies — Per-Technology Deep Dives

A canonical, senior-engineer-level module per major infrastructure technology — the
architecture, the internals (with real configs, not placeholders), the operational
playbook, and when NOT to reach for it. This section complements the concept-first
sections (`backend/`, `llm/`, `ml/`, `devops/`, `cuda/`), which teach the *pattern*
(orchestration, model serving); each module here teaches the *product* itself, and is
cross-linked back into the concept sections that use it as a worked example.

> **No runtime application** — all content is Markdown with annotated real-world
> config blocks and command output.

---

## 1. Section Overview

This section covers:

- **Workflow Orchestration** — Apache Airflow: the scheduler loop, executor models
  (Local/Celery/Kubernetes), DAG authoring, deferrable operators, backfills, and
  scheduler high availability.
- **Durable Execution** — Temporal: the two-plane split where the Service never runs
  your code, the event history and replay determinism, activities and their four
  timeouts, signals/queries/updates, Continue-As-New against the 51,200-event limit,
  versioning by patching or Pinned Worker Deployments, and the immutable shard count.
- **GPU Model Serving** — NVIDIA Triton Inference Server: the model repository layout,
  `config.pbtxt`, multi-framework backends, dynamic batching, ensembles/BLS, and
  `perf_analyzer`-driven capacity planning.
- **CPU/Edge Inference & Model Optimization** — Intel OpenVINO: the Runtime and device
  plugins (CPU/GPU/NPU), the IR format and `ovc`/`convert_model`, AUTO/HETERO virtual
  devices and performance hints, async infer requests, NNCF INT8/INT4 quantization,
  model caching, `PrePostProcessor`, and OpenVINO Model Server.
- **L7 Proxy & Service-Mesh Data Plane** — Envoy: the
  listener/filter-chain/route/cluster/endpoint model, the xDS control-plane split,
  load-balancing and locality-aware routing, outlier detection and circuit breaking as
  resource ceilings, the stats and access-log subsystems, Wasm/Lua/ext_authz/ext_proc
  extensibility, and the ecosystem of control planes built on it.
- **Secrets Management** — HashiCorp Vault (and OpenBao): the barrier and the four-layer
  key hierarchy, seal and unseal, Integrated Storage, every secrets-engine family, leases
  and the arithmetic that makes a shorter TTL cost more and change nothing, auth methods
  and the secret-zero problem, policies and identity, response wrapping, audit devices
  that refuse rather than drop, the four Kubernetes delivery mechanisms, and the OpenBao
  fork.
- **Change Data Capture** — Debezium: the two questions every CDC deployment reduces to
  (where do I start, can I keep reading), logical decoding and output plugins, what a
  replication slot actually pins, heartbeats and the idle-captured-table trap,
  `REPLICA IDENTITY` and the `before: null` consequence, TOAST placeholders, snapshot
  modes and the incremental-snapshot watermark algorithm, offsets and schema history,
  the four-quadrant position-loss recovery table, ordering guarantees, the
  `ExtractNewRecordState` tombstone modes and the outbox `EventRouter` defaults.
- **eBPF Networking** - Cilium: the pod-to-pod, pod-to-Service and egress packet paths,
  `CiliumNetworkPolicy` and the default-deny that kills DNS, Hubble flow observability,
  Cluster Mesh, the BGP Control Plane, Gateway API, XDP/Maglev/DSR, netkit, and
  WireGuard vs IPsec encryption.
- **Lakehouse Table Format** - Apache Iceberg: spec v2 vs v3, the metadata tree and the
  atomic pointer swap, hidden partitioning, schema evolution by column ID, copy-on-write
  vs merge-on-read, snapshot expiry, compaction, and the catalog tier.
- **Stream Processing** - Apache Flink: event time and watermarks, barrier
  checkpointing, checkpoint vs savepoint, the HashMap/RocksDB/ForSt state backends,
  two-phase-commit sinks, backpressure, Flink SQL, `maxParallelism` and key groups, and
  elastic scaling.
- **Distributed SQL Query Engine** - Trino: the query lifecycle, memory pools and the
  low-memory killer, fault-tolerant execution, the cost-based optimizer, dynamic
  filtering, the Iceberg connector, federation, and access control.

**Primary stack:** Apache Airflow 3.3.2; Temporal Server 1.32.1 (with Go 1.50.0,
Java 1.40.0, Python 1.34.0, TypeScript 1.24.0 and .NET 1.20.0 SDKs, and `temporal` CLI
1.9.1); NVIDIA Triton Inference Server (version tagged per NGC container release inside
the module); Intel OpenVINO 2026.4.1 (API 2.0 `ov::` era); Envoy 1.40.0 (with Envoy
Gateway 1.9.2, Istio 1.31.2 and Gateway API 1.6.3); Cilium 1.20.3; HashiCorp Vault 2.2.0
and OpenBao 2.7.1 (with Vault Secrets Operator 1.5.0, vault-k8s 1.7.5, vault-csi-provider
1.7.3 and External Secrets Operator 2.8.0); Debezium 3.7.0.Final (built against Apache
Kafka 4.3.1, with PostgreSQL as the worked connector); Apache Iceberg (Java) 1.12.0;
Apache Flink 2.3.0; Trino 484. See each module's §1 for the exact version studied and
inline tags for version-specific features.

See [`CLAUDE.md`](CLAUDE.md) for the scope & non-overlap boundary (why Kafka and vLLM
are *not* here), the module template, and how to add a new technology.

---

## 2. Module Table

| # | Module | Category | Difficulty | Key Topics |
|---|--------|----------|-----------|-----------|
| 1 | [apache_airflow](apache_airflow/apache_airflow.md) | Workflow Orchestration | Advanced | Scheduler loop, executors, deferrables, backfills, HA scheduler, Airflow 2→3 migration |
| 2 | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) | Durable Execution | Advanced | Event history + replay determinism, the four activity timeouts, signals/queries/updates, Continue-As-New, patching vs Pinned Worker Deployments, immutable `numHistoryShards` |
| 3 | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) | GPU Model Serving | Advanced | Model repository + `config.pbtxt`, backends, dynamic batching, ensembles/BLS, `perf_analyzer` |
| 4 | [intel_openvino](intel_openvino/intel_openvino.md) | CPU/Edge Inference & Optimization | Advanced | IR + `ovc`/`convert_model`, device plugins (CPU/GPU/NPU), AUTO/HETERO + performance hints, async infer requests, NNCF INT8/INT4, model caching, OVMS, `openvino-genai` |
| 5 | [envoy_proxy](envoy_proxy/envoy_proxy.md) | L7 Proxy & Service-Mesh Data Plane | Advanced | Listener/filter-chain/route/cluster/endpoint model, xDS + ADS ordering, LB policies and locality/priority/panic mode, the `enforcing_*` outlier trap, circuit breaking as five ceilings, retry budgets, the seven-layer timeout stack, `%RESPONSE_FLAGS%`, Wasm/Lua/ext_authz/ext_proc |
| 6 | [cilium](cilium/cilium.md) | eBPF Networking (CNI) | Advanced | Pod-to-pod, pod-to-Service and egress packet paths, kube-proxy replacement, `CiliumNetworkPolicy` and the default-deny DNS trap, Hubble, Cluster Mesh, BGP Control Plane, Gateway API and Cilium Ingress, Cilium Service Mesh vs Istio Ambient, XDP/Maglev/DSR, netkit, WireGuard vs IPsec, Bandwidth Manager, the conntrack table ceiling |
| 7 | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) | Secrets Management | Advanced | Barrier + four-layer key hierarchy, seal/unseal and why recovery keys cannot unseal, Integrated Storage, every secrets engine, the KV v2 `data/` policy trap, leases and the lease-count arithmetic, secret-zero and auth methods, `bound_claims` vs `claim_mappings`, policies + identity, response wrapping, audit refusal, Agent/VSO/CSI, quotas, rekey vs rotate, the OpenBao delta |
| 8 | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) | Change Data Capture | Advanced | Logical decoding and output plugins, what a replication slot pins (`restart_lsn`, `confirmed_flush_lsn`, `catalog_xmin`), `flush.lsn.source` and the idle-captured-table heartbeat trap, `REPLICA IDENTITY` and `before: null`, TOAST and `__debezium_unavailable_value`, snapshot modes and their removals, the incremental-snapshot watermark algorithm, the four-quadrant position-loss recovery table, the never-compact schema-history topic, ordering per key / per table / never across tables, the five tombstone modes, the `EventRouter` defaults, why `tasks.max` is ignored |
| 9 | [apache_iceberg](apache_iceberg/apache_iceberg.md) | Lakehouse Table Format | Advanced | Spec v2 vs v3, the metadata tree (metadata.json / manifest list / manifests) and the atomic pointer swap, hidden partitioning and partition evolution, column-ID schema evolution, copy-on-write vs merge-on-read, deletion vectors, snapshot expiry and orphan-file cleanup, compaction economics, the catalog tier (REST, Polaris, Glue, Nessie), and engine support. The vendor-neutral concept layer lives in [`database/lakehouse_and_table_formats`](../database/lakehouse_and_table_formats/lakehouse_and_table_formats.md). |
| 10 | [apache_flink](apache_flink/apache_flink.md) | Stream Processing | Advanced | Event time, watermarks and allowed lateness, asynchronous barrier checkpointing, checkpoint vs savepoint, HashMap/RocksDB/ForSt state backends, two-phase-commit sinks, backpressure diagnosis, Flink SQL and dynamic tables, restart strategies and failover regions, FlinkCEP, state schema evolution, Reactive Mode and the Operator autoscaler, `maxParallelism` and key groups, Materialized Tables |
| 11 | [trino](trino/trino.md) | Distributed SQL Query Engine | Advanced | Query lifecycle, memory pools and the low-memory killer, task concurrency and scheduling, fault-tolerant execution, cost-based optimizer and table statistics, dynamic filtering, the Iceberg connector, federation and cross-catalog joins, `EXPLAIN ANALYZE`, session vs configuration properties, access control, the file system cache |

---

## 3. Learning Path

The modules are **independent**: none depends on the others, so study order is a
matter of which domain you need first, not a hard prerequisite chain.
`STUDY_ORDER.technologies` groups them by domain: three contrast pairs (orchestration,
serving, and traffic and data plane), Vault on its own, and a four-module data run from
Debezium to Trino:

- **Orchestration pair — apache_airflow ↔ temporal_durable_execution.** Both are called
  "orchestrators" and are constantly confused in interviews. Airflow schedules
  *pipelines over calendar time* with data intervals and backfills; Temporal runs *one
  durable execution per business entity* for minutes to months. Temporal's §8 and §9 are
  largely "why this is not Airflow", so reading them back to back is what makes the
  distinction stick.
- **Serving pair — nvidia_triton_inference_server ↔ intel_openvino.** GPU model serving
  against CPU/edge inference and model optimization, sharing a vocabulary (batching,
  instances, throughput/latency knees) with opposite hardware assumptions.
- **Traffic and data-plane pair - envoy_proxy ↔ cilium.** Both pairs above *run your
  workload*; these two *move traffic to* it. Envoy is the L7 proxy, Cilium the eBPF
  L3/L4 data plane and CNI underneath it, and Cilium hands L7 policy to an embedded
  Envoy (`cilium` §6.4), so reading them back to back shows where one layer stops and
  the other starts.
- **The credential layer - hashicorp_vault.** Vault neither runs your workload nor
  moves traffic to it: it is the control-plane dependency that lets both start at all.
  Its most useful pairing is outside this section: read it against
  [devops/secrets_management](../devops/secrets_management/secrets_management.md), which
  owns the discipline while this module owns the product.
- **Data run - debezium_change_data_capture → apache_iceberg → apache_flink → trino.**
  Debezium captures row changes from the database log, Iceberg is the table format they
  land in, Flink is the stream processor that moves and transforms them, and Trino
  queries the result. Debezium was deliberately not placed next to Temporal: both are
  fed by a durable log, but that would split the orchestration pair for a surface
  similarity. Debezium's contrast partners outside this section are
  [backend/kafka_deep_dive](../backend/kafka_deep_dive/kafka_deep_dive.md) for the log it
  writes to and
  [database/polyglot_persistence_patterns](../database/polyglot_persistence_patterns/polyglot_persistence_patterns.md)
  for the pattern it implements.

```
apache_airflow  (batch orchestration: DAG runs per data interval)
       |
       v  (contrast pair — same word "orchestrator", opposite unit of work)
temporal_durable_execution  (durable execution: one execution per entity)
       |
       v  (independent — different domain entirely)
nvidia_triton_inference_server  (GPU model serving)
       |
       v  (contrast pair — CPU/edge counterpoint to GPU serving)
intel_openvino  (CPU/edge inference & model optimization)
       |
       v  (independent — the traffic layer, not a workload runner)
envoy_proxy  (L7 proxy / service-mesh data plane)
       |
       v  (contrast pair - the eBPF L3/L4 data plane under the L7 proxy)
cilium  (eBPF networking / CNI)
       |
       v  (independent - the credential layer everything else depends on)
hashicorp_vault  (secrets management / identity broker)
       |
       v  (independent - data movement between stores, not traffic to workloads)
debezium_change_data_capture  (change data capture from the database log)
       |
       v  (data run - where the change stream lands)
apache_iceberg  (lakehouse table format)
       |
       v  (data run - the stream processor that writes those tables)
apache_flink  (stream processing)
       |
       v  (data run - the engine that queries them)
trino  (distributed SQL query engine)
```

---

<!-- study-paths
     THE source of truth for this section's file inventory and study-tier membership.
     Every module, every file it owns (module page AND deep-dive sub-files), and every
     case study is listed here with the tiers it belongs to; `-` means Full path only.
     Content files carry NO structural metadata -- a deep dive holds its topic, nothing
     else. Order is never declared here: it comes from STUDY_ORDER in game/app.js.
     The tier TABLES lower in this file are generated from this block by
     `python3 game/extract.py --write-paths`. A file on disk that is missing from this
     block, or listed here and absent from disk, FAILS `extract.py --strict`.
apache_airflow
  apache_airflow.md  -
  assets_partitions_and_the_state_store.md  -
temporal_durable_execution
  temporal_durable_execution.md  -
  versioning_and_safe_deploys.md  -
nvidia_triton_inference_server
  nvidia_triton_inference_server.md  -
  dynamo_and_the_nvidia_serving_stack.md  -
intel_openvino
  intel_openvino.md  -
envoy_proxy
  envoy_proxy.md  -
  gateway_api_and_inference_extension.md  -
hashicorp_vault
  hashicorp_vault.md  -
  workload_identity_and_the_end_of_stored_secrets.md  -
debezium_change_data_capture
  debezium_change_data_capture.md  -
  cdc_across_failover_and_into_the_lakehouse.md  -
apache_iceberg
  apache_iceberg.md  -
cilium
  cilium.md  -
apache_flink
  apache_flink.md  -
trino
  trino.md  -
-->
## Learning Paths

This section is small by design, with each module already scoped to what a senior
engineer needs to operate the technology in production. There is **no Senior/Principal
tier toggle yet**: the browser learning game's Study view only shows tier tabs for
sections present in the derived `paths.json`, and while this section has now crossed
the documented 4-module threshold, the tier decision is **deliberately deferred** until
the remaining planned technology pages land. See
[`CLAUDE.md`](CLAUDE.md) "Learning Paths (Full-only — tiers deliberately deferred past
the threshold)" for the reasoning and the mechanics.

### Full Path

The complete curriculum in the order above - see [Learning Path](#3-learning-path).
Every module and deep-dive sub-file in full: every layer of Airflow's scheduler and executor internals,
every layer of Temporal's event history, determinism constraint, timeout and failure
taxonomy, versioning strategies and shard sizing, every layer of Triton's model
repository, backend, and batching internals, every layer of OpenVINO's Runtime,
device plugins, hints/streams model, and NNCF quantization, every layer of Envoy's
object model, xDS delivery, load-balancing and health machinery, timeout stack,
observability subsystems and extension surfaces, every layer of Cilium's eBPF packet
paths, policy engine, Hubble, Cluster Mesh and encryption, every layer of Vault's barrier
and key hierarchy, seal mechanics, secrets engines, lease economics, auth and policy
model, delivery mechanisms and operational playbook, every layer of Debezium's snapshot
and streaming phases, replication-slot mechanics, offset and schema-history stores,
transformation surface and failure modes, every layer of Iceberg's metadata tree,
partitioning, delete files, maintenance and catalogs, every layer of Flink's event-time,
checkpoint, state-backend and exactly-once machinery, and every layer of Trino's query
lifecycle, memory management, optimizer and connectors.

---

## Knowledge-Question Map

The highest-frequency *technology-specific* interview questions mapped to the module
that answers them.

| Interview question | Where the answer lives |
|--------------------|------------------------|
| Why did un-pausing a DAG trigger hundreds of runs at once? | [apache_airflow](apache_airflow/apache_airflow.md) |
| What's the difference between the Local, Celery, and Kubernetes executors, and when do you pick each? | [apache_airflow](apache_airflow/apache_airflow.md) |
| How do deferrable operators free up a worker slot while waiting on an external event? | [apache_airflow](apache_airflow/apache_airflow.md) |
| How does Airflow's scheduler achieve high availability, and what changed between Airflow 2 and 3? | [apache_airflow](apache_airflow/apache_airflow.md) |
| You deployed a change and every in-flight workflow stopped progressing, but nothing is marked failed — what happened? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Why can't workflow code call `time.Now()` or iterate a Go map, and what replaces each? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Which of the four activity timeouts do engineers get wrong most often, and which one must you always set? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Why can't you change `numHistoryShards` after a Temporal cluster is built, and what do you do at the ceiling? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| When would you choose Airflow over Temporal, and when the reverse? | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) |
| Why autoscale GPU inference on queue time, not GPU utilization? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| How does dynamic batching in Triton trade latency for throughput, and what does `max_queue_delay_microseconds` control? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| What's the difference between an ensemble and a Business Logic Scripting (BLS) pipeline in Triton? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| How do you use `perf_analyzer` to find the throughput/latency knee for a deployed model? | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) |
| Why is OpenVINO's THROUGHPUT hint slower for a single request than LATENCY mode? | [intel_openvino](intel_openvino/intel_openvino.md) |
| What changed between Model Optimizer (`mo`) and `ovc`/`convert_model`, and where did `--mean_values` go? | [intel_openvino](intel_openvino/intel_openvino.md) |
| Why did INT8 quantization tank accuracy, and how does accuracy-aware quantization fix it? | [intel_openvino](intel_openvino/intel_openvino.md) |
| When do you serve with OpenVINO Model Server versus embedding the runtime versus Triton? | [intel_openvino](intel_openvino/intel_openvino.md) |
| Does OpenVINO need a GPU, and can it run on AMD CPUs or NVIDIA GPUs? | [intel_openvino](intel_openvino/intel_openvino.md) |
| A request fails with a 503 and the access log shows `UO` — what happened, and what do you change? | [envoy_proxy](envoy_proxy/envoy_proxy.md) |
| You configured outlier detection and nothing has ever been ejected — why? | [envoy_proxy](envoy_proxy/envoy_proxy.md) |
| Your upstream takes 30 seconds and Envoy kills it at 15 — which timeout did that? | [envoy_proxy](envoy_proxy/envoy_proxy.md) |
| You set a local rate limit of 100 rps and it is admitting roughly 800 — why? | [envoy_proxy](envoy_proxy/envoy_proxy.md) |
| Is Envoy a service mesh, and which of Istio, Envoy Gateway, Contour and Consul are control planes over the same data plane? | [envoy_proxy](envoy_proxy/envoy_proxy.md) |
| A `CiliumNetworkPolicy` meant to allow one external API takes down DNS for the whole namespace - what happened? | [cilium](cilium/cilium.md) |
| `cilium-agent` on a node crashes and restarts - do the pods on that node lose connectivity while it is down? | [cilium](cilium/cilium.md) |
| Connections start failing silently after months of steady growth with `bpf-ct-global-tcp-max` at its default - what is the mechanism? | [cilium](cilium/cilium.md) |
| Your Vault policy grants read on `secret/app/db` and every pod still gets permission denied — why? | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) |
| You cut the dynamic credential TTL from 1h to 5m. What happens to Vault's lease count and its load? | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) |
| Vault is unsealed and healthy but returning 500 to everything — what do you check first? | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) |
| Someone deleted the KMS key your auto-unseal depends on. What is your recovery path? | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) |
| What is the difference between `vault operator rekey` and `vault operator rotate`? | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) |
| When would you choose OpenBao over Vault, and what actually breaks on migration? | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) |
| Your CDC connector stopped and now the PostgreSQL primary is out of disk — what happened? | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) |
| Retained WAL is climbing but the connector is RUNNING with zero lag and zero errors — why? | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) |
| You already bulk-copied the table — how do you tell Debezium to start streaming from that exact point? | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) |
| Why can't you just publish to Kafka from the application after the commit and skip CDC entirely? | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) |
| What ordering does CDC actually guarantee — per key, per table, or across tables? | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) |
| Why is renaming a column safe in Iceberg when it would break a Hive table's downstream readers? | [apache_iceberg](apache_iceberg/apache_iceberg.md) |
| Storage cost tripled over eight months even though compaction ran the whole time - what did the team forget? | [apache_iceberg](apache_iceberg/apache_iceberg.md) |
| Two Spark jobs commit to the same Iceberg table at nearly the same time - what actually happens? | [apache_iceberg](apache_iceberg/apache_iceberg.md) |
| A rescale from 8 to 16 parallelism restores cleanly, but a downstream operator's counts reset to zero - what happened? | [apache_flink](apache_flink/apache_flink.md) |
| A `KafkaSink` with `EXACTLY_ONCE` starts failing after someone widened the checkpoint interval - why? | [apache_flink](apache_flink/apache_flink.md) |
| All windowed output stops across every key while the job still checkpoints - what is the likely cause? | [apache_flink](apache_flink/apache_flink.md) |
| A Trino cluster sits under 30% CPU but latency climbs as more analysts connect - what do you check first? | [trino](trino/trino.md) |
| The low-memory killer removed a well-behaved query instead of the one causing the pressure - how? | [trino](trino/trino.md) |
| A wide join keeps hitting "exceeded distributed user memory limit" - why does adding workers not fix it? | [trino](trino/trino.md) |

---

## Study Plan

A one-week-per-module plan, in study order. Each week pairs the module with related concept-section material to
rehearse how the technology fits into a broader system design answer.

| Week | Focus | Module | Rehearse with |
|------|-------|--------|---------------|
| 1 | Orchestration | [apache_airflow](apache_airflow/apache_airflow.md) | [ml/data_pipelines_and_processing](../ml/data_pipelines_and_processing/data_pipelines_and_processing.md) (what Airflow is scheduling), [ml/mlops_and_ci_cd](../ml/mlops_and_ci_cd/mlops_and_ci_cd.md) (where Airflow sits in a training/retraining pipeline) |
| 2 | Durable Execution | [temporal_durable_execution](temporal_durable_execution/temporal_durable_execution.md) | [backend/distributed_transactions_and_consistency](../backend/distributed_transactions_and_consistency/distributed_transactions_and_consistency.md) and [hld/distributed_transactions](../hld/distributed_transactions/distributed_transactions.md) (the saga and compensation theory Temporal's §6.15 applies rather than re-teaches), [backend/event_sourcing_and_cqrs](../backend/event_sourcing_and_cqrs/event_sourcing_and_cqrs.md) (the log-as-source-of-truth idea behind the event history), [llm/agents_and_tool_use/durable_long_running_agents.md](../llm/agents_and_tool_use/durable_long_running_agents.md) (the durable-agent pattern Temporal now hosts) |
| 3 | GPU Serving | [nvidia_triton_inference_server](nvidia_triton_inference_server/nvidia_triton_inference_server.md) | [llm/inference_engines](../llm/inference_engines/inference_engines.md) (Triton vs vLLM/TGI-style serving stacks), [devops/ml_platform_and_gpu_infrastructure](../devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) (the GPU platform Triton runs on) |
| 4 | CPU/Edge Serving & Optimization | [intel_openvino](intel_openvino/intel_openvino.md) | [llm/optimization_and_quantization](../llm/optimization_and_quantization/optimization_and_quantization.md) (the quantization concepts NNCF implements), [ml/gpu_and_hardware_optimization](../ml/gpu_and_hardware_optimization/gpu_and_hardware_optimization.md) (VNNI/AMX/XMX vs Tensor Cores), [llm/vllm_deep_dive](../llm/vllm_deep_dive/vllm_deep_dive.md) (the GPU LLM-serving contrast to OpenVINO's CPU/edge LLM path) |
| 5 | Traffic Layer & Data Plane | [envoy_proxy](envoy_proxy/envoy_proxy.md) | [backend/service_mesh_and_service_discovery](../backend/service_mesh_and_service_discovery/service_mesh_and_service_discovery.md) (the mesh pattern and Istio's CRD surface over this data plane), [backend/api_gateway_patterns](../backend/api_gateway_patterns/api_gateway_patterns.md) (the gateway pattern Envoy implements), [devops/kubernetes_networking](../devops/kubernetes_networking/kubernetes_networking.md) (Gateway API, whose implementations are mostly Envoy), [hld/load_balancing](../hld/load_balancing/load_balancing.md) (the algorithms behind Envoy's LB policies) |
| 6 | eBPF Networking | [cilium](cilium/cilium.md) | [devops/kubernetes_networking](../devops/kubernetes_networking/kubernetes_networking.md) (CNI and kube-proxy, which Cilium replaces), [backend/service_mesh_and_service_discovery](../backend/service_mesh_and_service_discovery/service_mesh_and_service_discovery.md) (the sidecar mesh Cilium Service Mesh competes with), [devops/kubernetes_security](../devops/kubernetes_security/kubernetes_security.md) (the NetworkPolicy model `CiliumNetworkPolicy` extends), [backend/off_cpu_and_ebpf_analysis](../backend/off_cpu_and_ebpf_analysis/off_cpu_and_ebpf_analysis.md) (eBPF as a general kernel mechanism) |
| 7 | Secrets & Credential Brokering | [hashicorp_vault](hashicorp_vault/hashicorp_vault.md) | [devops/secrets_management](../devops/secrets_management/secrets_management.md) (the discipline this module's product implements — read it first, and note the deliberate non-overlap table in §1), [devops/kubernetes_security](../devops/kubernetes_security/kubernetes_security.md) (RBAC and etcd encryption, which is what VSO's native Secrets depend on), [backend/auth_and_authorization_systems](../backend/auth_and_authorization_systems/auth_and_authorization_systems.md) (the OIDC and JWT mechanics behind Vault's `jwt` auth), [devops/infrastructure_as_code_terraform](../devops/infrastructure_as_code_terraform/infrastructure_as_code_terraform.md) (how mounts, roles and policies should actually be managed) |
| 8 | Change Data Capture | [debezium_change_data_capture](debezium_change_data_capture/debezium_change_data_capture.md) | [backend/kafka_deep_dive](../backend/kafka_deep_dive/kafka_deep_dive.md) (the log Debezium writes to — broker internals, partitions and the Netflix DBLog story all live there, not here), [database/polyglot_persistence_patterns](../database/polyglot_persistence_patterns/polyglot_persistence_patterns.md) (the outbox and derived-store *pattern* this module's `EventRouter` implements), [database/replication_and_high_availability](../database/replication_and_high_availability/replication_and_high_availability.md) and [database/postgresql_internals](../database/postgresql_internals/postgresql_internals.md) (logical replication, slots as a general primitive and the xmin horizon), [devops/case_studies/design_zero_downtime_infra_migration](../devops/case_studies/design_zero_downtime_infra_migration.md) (bulk copy plus CDC catch-up at 200 TB) |
| 9 | Lakehouse Table Format | [apache_iceberg](apache_iceberg/apache_iceberg.md) | [database/lakehouse_and_table_formats](../database/lakehouse_and_table_formats/lakehouse_and_table_formats.md) (the vendor-neutral concept layer this module is the product half of), [ml/data_pipelines_and_processing](../ml/data_pipelines_and_processing/data_pipelines_and_processing.md) (the pipelines that write these tables) |
| 10 | Stream Processing | [apache_flink](apache_flink/apache_flink.md) | [hld/stream_processing](../hld/stream_processing/stream_processing.md) (the windowing and exactly-once concepts Flink implements), [backend/kafka_deep_dive](../backend/kafka_deep_dive/kafka_deep_dive.md) (the source and sink most Flink jobs read and write) |
| 11 | Distributed SQL Query Engine | [trino](trino/trino.md) | [database/analytical_databases_and_olap](../database/analytical_databases_and_olap/analytical_databases_and_olap.md) (where a federated query engine sits against a warehouse), [database/sql_query_optimization](../database/sql_query_optimization/sql_query_optimization.md) (the plan-reading skills `EXPLAIN ANALYZE` needs), [database/lakehouse_and_table_formats](../database/lakehouse_and_table_formats/lakehouse_and_table_formats.md) (the tables Trino reads through the Iceberg connector) |

---

## Version Notes

| Technology | Version Studied | Key Notes |
|-----------|-----------------|-----------|
| Apache Airflow | 3.3.2 (2026-09-17) | React-based UI, DAG versioning, and the task-execution API server arrived in `[3.0]`; 3.3.2 also tightened backfill-endpoint authorization and made an explicit API credential take precedence over a session cookie. Where 2.x behaves differently the delta is tagged inline as `[2.x]` |
| Temporal | Server 1.32.1 (2026-10-08); supported lines 1.30, 1.31, 1.32 (SDKs: Go 1.50.0, Java 1.40.0, Python 1.34.0, TypeScript 1.24.0, .NET 1.20.0; CLI 1.9.1) | MIT throughout. `[1.31]` brought Worker Deployment APIs to GA, Task Queue Priority and Fairness to GA, and Nexus on by default; the legacy Worker Versioning v1/v2 APIs are deprecated in 1.31 with removal in 1.33. Update-with-Start went GA in `[1.28]` |
| NVIDIA Triton Inference Server | Tagged per NGC container release inline (e.g. `26.09-py3`, server 2.73.0) | Triton does not gate features by simple semver - the module tags each version-specific flag/default against the NGC release it shipped in |
| Intel OpenVINO | 2026.4.1 (2026-10-01) | API 2.0 (`ov::` / top-level `openvino` package) - the legacy `InferenceEngine::` API was removed in 2024.0 and the Model Optimizer (`mo`) CLI in 2025.0; from `[2026.0]` the CPU plugin requires AVX2. Features are tagged inline against the release they landed in |
| Envoy | 1.40.0 (2026-10-13) | Apache 2.0, CNCF graduated. Quarterly releases around the 15th with roughly 12 months of support per line, so 1.40, 1.39, 1.38 and 1.37 are supported. Dynamic Modules landed `[1.34]` and expanded in `[1.39]`. Ecosystem versions studied: Gateway API 1.6.3, Gateway API Inference Extension 1.6.2, Istio 1.31.2 (ambient GA in `[Istio 1.24]`), Envoy Gateway 1.9.2, Contour 1.33.7, and Agent Router 1.2.0 (the release that renamed Envoy AI Gateway, which reached 1.0 in June 2026) |
| Cilium | 1.20.3 (2026-10-15) | Three most recent minor lines supported (1.20, 1.19, 1.18) on roughly a six-month cadence; 1.17 left support when 1.20 shipped. Version-gated behaviour is tagged inline as `[1.20]`, `[BGP v2]` and `[Gateway API]` |
| HashiCorp Vault | 2.2.0 (2026-10-15), on the 2.x line GA'd as 2.0.0 on 2026-04-14 | **BUSL 1.1**, with the Licensor now **International Business Machines Corporation (IBM)**; the repo, Go module, docs domain, Helm chart and Terraform provider all still say hashicorp. Change Date +4 years, Change License MPL 2.0. From 2.x, Enterprise follows IBM's Support Cycle-2 model (fixes only in the latest fix release of a major line); 1.19.x is the remaining LTS line, in extended maintenance until April 2027. `[2.0]` made `sys/rekey`, `sys/generate-root` and the DR operation-token endpoint **authenticated** |
| OpenBao | 2.7.1 (2026-10-01) | **MPL 2.0**, Linux Foundation. API-compatible at the fork point and diverging on operational surface: `[OpenBao 2.6]` removed `stored_shares` from `sys/init` and `sys/rekey/init` and runs containers as the unprivileged `openbao` user; `[OpenBao 2.7]` moved the KMS seals and the LDAP, Kerberos and RADIUS auth methods out to plugins, removed `file` storage, and added Control Groups |
| Vault Kubernetes ecosystem | VSO 1.5.0, vault-k8s 1.7.5, vault-csi-provider 1.7.3, ESO 2.8.0 | The four delivery mechanisms are compared in `hashicorp_vault` §4.9, §6.19–§6.20 and §8.5. ESO 2.8.0 serves `external-secrets.io/v1`; `[ESO 0.17]` stopped serving `v1beta1` |
| Debezium | 3.7.0.Final (2026-09-29) | **Apache 2.0**, community-governed with Red Hat as corporate sponsor - **not a CNCF project**. Built against Apache Kafka 4.3.1; preceding lines end at 3.6.3.Final and 3.5.2.Final. Build requires Java 21; connector runtime baseline is Java 17, while Debezium Server, the Operator and the Quarkus Outbox extension require 21. `[3.7]` removed the Cassandra 3 connector and continuous mining for Oracle 12, and renamed `ByLogicalTableRouter` to `ToLogicalTopicRouter`. Earlier removals that break copied configs: `schema_only`/`schema_only_recovery` `[3.3.0.Alpha1]`, MySQL `never` `[3.6.0.Alpha2]`, `drop.tombstones`/`delete.handling.mode` `[3.2.0.Beta1]`, `wal2json` `[2.0.0.Alpha1]`. Support floors: PostgreSQL 14+ `[3.4]`, MySQL 8.0+ `[2.5]` |
| Apache Iceberg | Java 1.12.0 (2026-09-29); PyIceberg 0.12.0 | Apache 2.0, ASF top-level project. Table spec v3 is ratified, but `format-version` still defaults to 2 for new tables; v3 is opt-in and one-way (v3 readers read v2 tables, not the reverse). `[1.12]` removed Spark 3.4 support; `[1.11]` made Spark 4.1 and Flink 2.1 the default build targets |
| Apache Flink | 2.3.0 (2026-06-25); 1.20 is the 1.x LTS line | `[2.0]` (2025-03-24) is the boundary the module keeps returning to: the first major version in nine years. Ecosystem versions studied: Flink CDC 3.7.0 and the Flink Kubernetes Operator 1.16.1 (supports Flink 1.19.x to 2.3.x) |
| Trino | 484 (2026-10-15) | Single-number releases. Amazon Athena engine version 3 is built on Trino. Version-gated behaviour is tagged inline as `[484]`, `[483]`, `[Athena v3]` and `[SEP]` |

---

## Technology Knowledge Bank (`tech_bank/`)

`tech_bank/` is **data, not a module** — the authored source of the game's Technologies
screen (`game/tech_index.json`, generated and gitignored). One record per tool: a one-line
summary, its kind, its language binding, and every problem-role it plays with a weight.
It is excluded from the question bank and from the reader's module tree by exact path;
see [`CLAUDE.md`](CLAUDE.md) for the record contract.

| File | What it holds |
|------|---------------|
| [`tech_bank/tech_bank.md`](tech_bank/tech_bank.md) | The taxonomy — 6 kinds, 8 language tokens, 18 tiers, 95 roles |
| [`tech_bank/caching.md`](tech_bank/caching.md) · [`data-stores.md`](tech_bank/data-stores.md) · [`data-access.md`](tech_bank/data-access.md) · [`data-movement.md`](tech_bank/data-movement.md) | Storage and data movement |
| [`tech_bank/runtime-systems.md`](tech_bank/runtime-systems.md) · [`apis-frameworks.md`](tech_bank/apis-frameworks.md) · [`devtools.md`](tech_bank/devtools.md) · [`platform-delivery.md`](tech_bank/platform-delivery.md) | Language runtimes, frameworks, build and delivery |
| [`tech_bank/traffic-edge.md`](tech_bank/traffic-edge.md) · [`observability.md`](tech_bank/observability.md) · [`security.md`](tech_bank/security.md) · [`search-retrieval.md`](tech_bank/search-retrieval.md) | Edge, operations, security, retrieval |
| [`tech_bank/gpu.md`](tech_bank/gpu.md) · [`model-training.md`](tech_bank/model-training.md) · [`inference.md`](tech_bank/inference.md) · [`ml-lifecycle.md`](tech_bank/ml-lifecycle.md) · [`applied-ml.md`](tech_bank/applied-ml.md) · [`llm-apps.md`](tech_bank/llm-apps.md) | GPU, ML and LLM stack |

---

## See also

- [`ml/data_pipelines_and_processing`](../ml/data_pipelines_and_processing/data_pipelines_and_processing.md) — the data-pipeline concepts Airflow orchestrates.
- [`backend/distributed_transactions_and_consistency`](../backend/distributed_transactions_and_consistency/distributed_transactions_and_consistency.md) and [`hld/distributed_transactions`](../hld/distributed_transactions/distributed_transactions.md) — the saga and compensation theory Temporal implements as ordinary `try`/`catch` plus a compensation stack.
- [`backend/event_sourcing_and_cqrs`](../backend/event_sourcing_and_cqrs/event_sourcing_and_cqrs.md) — the append-only-log-as-truth idea behind Temporal's event history, and the source of the *temporal queries* name collision.
- [`llm/agents_and_tool_use/durable_long_running_agents.md`](../llm/agents_and_tool_use/durable_long_running_agents.md) — the durable-agent pattern; that sub-file owns the agent shape, `temporal_durable_execution` owns the product.
- [`ml/mlops_and_ci_cd`](../ml/mlops_and_ci_cd/mlops_and_ci_cd.md) — where a workflow orchestrator fits in the training/deployment lifecycle.
- [`llm/inference_engines`](../llm/inference_engines/inference_engines.md) and [`llm/vllm_deep_dive`](../llm/vllm_deep_dive/vllm_deep_dive.md) — LLM-specific serving engines, contrasted with Triton's general-purpose multi-framework serving and OpenVINO's CPU/edge LLM path.
- [`llm/optimization_and_quantization`](../llm/optimization_and_quantization/optimization_and_quantization.md) — the quantization concepts OpenVINO's NNCF implements (INT8 PTQ, INT4 weight compression).
- [`ml/gpu_and_hardware_optimization`](../ml/gpu_and_hardware_optimization/gpu_and_hardware_optimization.md) — the hardware-acceleration contrast (Intel VNNI/AMX/XMX vs NVIDIA Tensor Cores) behind OpenVINO's precision choices.
- [`devops/ml_platform_and_gpu_infrastructure`](../devops/ml_platform_and_gpu_infrastructure/ml_platform_and_gpu_infrastructure.md) — the GPU cluster and platform layer both Airflow (GPU-backed tasks) and Triton (GPU inference) run on.
- [`cuda/triton_and_kernel_dsls`](../cuda/triton_and_kernel_dsls/triton_and_kernel_dsls.md) — OpenAI Triton, the GPU kernel DSL — an unrelated product that shares a name with NVIDIA Triton Inference Server; see the disambiguation note in [`CLAUDE.md`](CLAUDE.md).
- [`backend/service_mesh_and_service_discovery`](../backend/service_mesh_and_service_discovery/service_mesh_and_service_discovery.md) and [`backend/api_gateway_patterns`](../backend/api_gateway_patterns/api_gateway_patterns.md) — the mesh and gateway *patterns*, plus Istio's CRD authoring surface. `envoy_proxy` owns the data plane underneath both: the object model, the xDS protocol, the configuration defaults and the failure modes. If a `VirtualService` block ever appears in `envoy_proxy.md`, the boundary has been crossed.
- [`hld/load_balancing`](../hld/load_balancing/load_balancing.md), [`hld/consistent_hashing`](../hld/consistent_hashing/consistent_hashing.md) and [`backend/fault_tolerance_patterns`](../backend/fault_tolerance_patterns/fault_tolerance_patterns.md) — load-balancing algorithms and circuit-breaker theory; `envoy_proxy` owns only Envoy's knobs on them (`choice_count`, the 65537-entry Maglev table, the five circuit-breaker ceilings).
- [`devops/kubernetes_networking`](../devops/kubernetes_networking/kubernetes_networking.md) — Ingress, Gateway API, CNI and kube-proxy; `envoy_proxy` explains why nearly every Gateway API implementation is the same binary underneath.
- [`devops/secrets_management`](../devops/secrets_management/secrets_management.md) — the secrets-management *discipline*: the four questions, static vs dynamic as a concept, the Kubernetes delivery patterns as patterns (including `kubeseal` and `sops`), the exposure-window arithmetic, secret scanning, secrets in Terraform state, and IRSA as the general secret-zero answer. `hashicorp_vault` owns the inside of the product and re-derives none of it; the non-overlap table is in that module's §1. If an exposure-window formula or a `kubeseal` command ever appears in `hashicorp_vault.md`, the boundary has been crossed.
- [`devops/kubernetes_security`](../devops/kubernetes_security/kubernetes_security.md) and [`backend/auth_and_authorization_systems`](../backend/auth_and_authorization_systems/auth_and_authorization_systems.md) — RBAC and etcd encryption-at-rest (what a VSO-written native Secret actually rests on), and the OIDC/JWT mechanics underneath Vault's `jwt` auth method.
- [`backend/kafka_deep_dive`](../backend/kafka_deep_dive/kafka_deep_dive.md) — an example of a technology already owned by a concept section (why it isn't duplicated here). It is also `debezium_change_data_capture`'s hard boundary: broker internals, partitions, ISR, `acks`, consumer groups, the rebalance protocol, log compaction, retention, KRaft **and the Netflix DBLog story** all live there. If a partition-assignment algorithm or an ISR diagram ever appears in `debezium_change_data_capture.md`, the boundary has been crossed.
- [`database/polyglot_persistence_patterns`](../database/polyglot_persistence_patterns/polyglot_persistence_patterns.md) — the transactional-outbox and derived-store *patterns*, the dual-write failure narrative and the PostgreSQL-to-Elasticsearch worked example. `debezium_change_data_capture` owns the `EventRouter` SMT's defaults and mechanics instead, including why that module's example had to override all three of `route.by.field`, `table.field.event.key` and `route.topic.replacement`.
- [`database/replication_and_high_availability`](../database/replication_and_high_availability/replication_and_high_availability.md) and [`database/postgresql_internals`](../database/postgresql_internals/postgresql_internals.md) — physical vs logical replication, replication slots as a general primitive, `max_slot_wal_keep_size`, the xmin horizon and `pg_createsubscriber`. `debezium_change_data_capture` owns Debezium's own contribution: `flush.lsn.source`, `heartbeat.interval.ms` defaulting to 0, the idle-captured-table case where the slot cannot advance, `lsn.flush.timeout.action` and the `[3.4]` pgjdbc keepalive-flush fix.
- [`devops/case_studies/design_zero_downtime_infra_migration`](../devops/case_studies/design_zero_downtime_infra_migration.md) — bulk copy plus CDC catch-up at 200 TB, with the slot created before the copy. `debezium_change_data_capture` owns the general mechanic that case study had to reconstruct, and its §14 is deliberately a different scenario.
