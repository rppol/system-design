# MLflow Deep Dive

<!-- study-paths
senior: mlflow_deep_dive.md
files this module contributes to each curated path; omit a tier to leave it out
-->
---

## 1. Concept Overview

MLflow is an open-source platform for the operational record of machine learning work: what
you ran, what came out, and which one is live. Databricks built it and open-sourced it in
2018 under Apache-2.0; it is now the default answer to "where did this model come from" in
most Python ML shops, and it positions itself as "The Open Source AI Engineering Platform"
because MLflow 3 extended the same record-keeping to GenAI traces, prompts and judges.

**This module targets MLflow 3.15.1**, released 2026-08-03 (3.15.0 landed 2026-07-31). Every
default, constant and file layout quoted below was read out of that package, not from
documentation. Where a behaviour changed at a known version the tag is inline —
`[MLflow 3.0+]`, `[2.9+]`, `[2.10+]`.

The thesis of this page, and the sentence to carry into an interview:

> **MLflow never runs your training and never runs your inference. It records what happened
> (Tracking), describes what came out in a loader-agnostic directory (Models), and says which
> one is live (Registry). Everything that executes is yours or someone else's.**

That is not a criticism. It is the design, and it is why MLflow outlived tools that tried to
own the execution too. A tracking server is a Flask/uvicorn process in front of a SQL database
and an object store; it has no scheduler, no resource manager, no GPU awareness and no opinion
about your training loop. The blast radius when it falls over is "you cannot log", not "your
training died" — and that separation is what lets one MLflow instance sit under Airflow,
Kubeflow, Ray, SageMaker, a laptop and a CI runner at the same time.

The surface it exposes is larger than most teams use:

| Component | What it is | Runs anything? |
|---|---|---|
| **Tracking** | Runs, params, metrics, tags, datasets, artifacts, and the REST/UI over them | No |
| **Models** | The `MLmodel` directory format — flavours, signature, environment | No |
| **Model Registry** | Named models, numbered versions, aliases, version tags | No |
| **Projects** | `MLproject` entry points with a declared environment | Shells out to your command |
| **Evaluation** | `mlflow.evaluate` / `mlflow.genai.evaluate` — score a model against a dataset | Calls your model |
| **Tracing** | OpenTelemetry-shaped spans for GenAI apps | No |
| **Prompt Registry** | Versioned, aliased prompt templates as first-class entities | No |
| **AI Gateway** | One endpoint over many model providers, with keys held server-side | Proxies your call |
| **`models serve`** | A single-model Python HTTP process for local checking | Yes, badly |

Only the last three touch a request path, and only one of them is something you would put in
front of production traffic — and it is not `models serve`.

**Where this module sits.** The concept-level material lives elsewhere and is not repeated
here: the four axes of reproducibility, hyperparameter-search theory, DVC's model, and the
generic idea of a model registry belong to
[Experiment Tracking and Versioning](../experiment_tracking_and_versioning/experiment_tracking_and_versioning.md);
retraining triggers, canary rollout and the CI pipeline belong to
[MLOps and CI/CD](../mlops_and_ci_cd/mlops_and_ci_cd.md); dynamic batching, tail latency and
GPU serving belong to
[Model Serving and Inference](../model_serving_and_inference/model_serving_and_inference.md)
and [NVIDIA Triton Inference Server](../../technologies/nvidia_triton_inference_server/nvidia_triton_inference_server.md).
This page is MLflow's *implementation*: the process, the schema, the file format, the failure
modes, and the version cliffs.

---

## 2. Intuition

One-line analogy: MLflow is a shipping manifest, not a shipping company. It does not move the
container; it says what is inside, who packed it, and which one is on the truck right now.

Mental model: three artefacts and one rule. A **run** is an immutable receipt for one
execution. A **logged model** is a self-describing directory that any consumer can open
without knowing your framework. A **registered model version** is a name and a number that
points at that directory. The rule is that nothing in MLflow ever calls your code except at
the moment you ask it to score something — so every MLflow failure is a *recording* failure,
never a *compute* failure, and diagnosing one starts by asking which of the two stores
(database or bucket) the client could not reach.

Why it matters: the expensive failure in ML is not a bad model, it is an unattributable one. A
model in production whose training data, code commit and hyperparameters cannot be recovered
cannot be debugged, cannot be re-trained deliberately, and cannot survive an audit. MLflow's
whole value is that the attribution is captured as a side effect of a workflow engineers were
going to run anyway.

**Key insight: the `MLmodel` file, not the tracking UI, is why MLflow won.**

Every experiment tracker in 2018 had runs, params, metrics and charts, and several had nicer
charts than MLflow. What none of them had was a *directory format* that a process which has
never heard of your training code can open, read one YAML file, and know how to load the
model, what shape to feed it, and what environment to build. That file is the interface
boundary between the team that trains and the team that serves. It is what turns "give me your
model" from a two-week integration into a URI, and it is why MLflow shows up as the *export
format* even inside platforms that have their own tracker — SageMaker, Vertex AI and Azure ML
all ingest MLflow models directly.

Put the priority in that order when you explain MLflow. The UI is the part people see; the
format is the part that made it unavoidable.

---

## 3. Core Principles

**Record, do not orchestrate.** MLflow's API is a logger. There is no MLflow scheduler, no
retry policy, no resource allocator, no DAG. Anything that wants those runs *above* MLflow.
The corollary: a run left in `RUNNING` state forever is normal — nothing in MLflow was
watching the process that died, and only the client's context manager or a `--older-than`
sweep will ever close it.

**Two stores, always.** The backend store (a SQL database) holds runs, params, metrics, tags
and registry entities. The artifact store (object storage) holds files. They are reached by
different credentials over different protocols, and half of MLflow's confusing failures are
one of the two being unreachable while the other is fine.

**The model is a directory, not a pickle.** A logged model is a folder containing the weights
in whatever format the framework prefers, plus `MLmodel` (the manifest), plus
`requirements.txt` / `python_env.yaml` / `conda.yaml` (three renderings of the same
dependency set). Consumers read the manifest, never the weights directly.

**Everything is addressable by URI.** `runs:/<run_id>/<name>`, `models:/<name>/<version>`,
`models:/<name>@<alias>`, `models:/<model_id>` `[3.0+]`. Any MLflow API that takes a model
takes any of these, which is what makes promotion a metadata edit rather than a redeploy.

**Params are immutable, tags are not, metrics are append-only.** Re-logging a param with a
different value raises. Re-setting a tag overwrites. Logging a metric appends a row keyed by
`(run, key, step, timestamp)`. Choosing the wrong one of the three is the most common schema
mistake and it is not reversible in place.

**Enforce the contract at the boundary, or it will not be enforced at all.** A model signature
is optional, and a model logged without one will happily score misaligned input forever. The
signature is the only place MLflow will refuse bad data on your behalf.

**MLflow pins what it saw, not what you meant.** Dependency capture inspects the live
interpreter. It cannot see your CUDA driver, your system libraries, your base image, or the
`LD_LIBRARY_PATH` that made the wheel work. Treat the captured environment as a strong hint
and the container image digest as the actual pin.

---

## 4. Types / Architectures / Strategies

### The four deployment topologies

**1. Local file store — now a dead end.** `mlflow.set_tracking_uri("file:./mlruns")` writes
YAML and directories under `./mlruns`. As of 3.15 this backend is in **maintenance mode and
raises on use** unless you set `MLFLOW_ALLOW_FILE_STORE=true`; MLflow ships
`mlflow migrate-filestore` to convert it losslessly to SQLite. A bare `mlflow server` with no
flags now defaults to `sqlite:///mlflow.db`, falling back to an existing `./mlruns` only if one
is already there. Treat the file store as legacy data, never as a starting point.

**2. Single-node server, local SQLite + local artifacts.** `mlflow server` with defaults. One
process, one writer. Fine for a laptop; fails the moment two trials log concurrently.

**3. Team server — the standard shape.** A tracking server (or several behind a load balancer)
against PostgreSQL and S3/GCS/Azure Blob/MinIO. This is what almost every production MLflow
looks like. The interesting decision inside it is the artifact access mode (below).

**4. Split fleet.** Tracking servers with `--artifacts-only` alongside metadata-only servers,
so large artifact transfers do not occupy the same workers that serve UI and REST metadata
calls. Worth doing when artifact upload latency starts showing up in run-creation p99;
MLflow 3.15 also added **proxy-less transfers for large files**, which lets the server hand
back a presigned URL rather than streaming the bytes itself.

### Artifact access modes — the choice that produces most support tickets

| Mode | Flag | Who talks to the bucket | Client needs bucket creds | Failure shape |
|---|---|---|---|---|
| Proxied | `--serve-artifacts` (**default: True**) with `--artifacts-destination s3://…` | The server | No | Server becomes the bandwidth bottleneck |
| Direct | `--no-serve-artifacts`, experiment artifact root is `s3://…` | The client | **Yes** | `log_param` succeeds, `log_artifact` 403s |
| Artifacts-only | `--artifacts-only` | This server does artifacts only | No | Tracking endpoints are disabled on that node |

The direct mode is the historical default in most self-hosted deployments and the source of
the single most common MLflow incident: a client that has the tracking URI and database
reachability but no `AWS_*` credentials will log parameters and metrics perfectly, then fail
on the first `log_artifact` or `log_model`. Half a run gets recorded. See §10, war story 1.

### Registry strategies

**Aliases plus version tags** `[2.9+]` is the current model: numbered versions are immutable,
`@champion`/`@challenger`/`@canary-eu` are movable pointers, and tags such as
`validation_status: passed` record *why* a version is eligible. One version may hold several
aliases; moving one is atomic.

**Stages** (`None`/`Staging`/`Production`/`Archived`) are the previous model. They were
deprecated in **2.9.0**, are still present in 3.15.1 behind a deprecation warning, and were
never supported by Unity Catalog-backed registries at all. Section 6 covers why they were the
wrong shape.

**Registry backends** split three ways: the open-source SQL registry (same database as
tracking by default, or a separate one via `--registry-store-uri`); Databricks Unity Catalog,
where a model is a three-level `catalog.schema.model` name with real governance and no stages;
and third-party registries that speak the MLflow API (SageMaker, Vertex AI, Azure ML all
ingest MLflow models, with varying fidelity on aliases).

### The serving strategies, ranked by how much production they survive

| Strategy | What it is | Verdict |
|---|---|---|
| `mlflow models serve` | One model, one Python process, FastAPI + `/invocations` | Local checking only |
| `mlflow models build-docker` | Bake the model and its env into an image | The right handoff to a real platform |
| `mlflow deployments` plugin | Push to SageMaker, Azure ML, or a custom target | Fine where the plugin is maintained |
| Registry -> KServe `InferenceService` | KServe pulls `models:/name@champion` and runs it | The standard open-source production path |
| Registry -> Triton model repository | CI exports the model, writes `config.pbtxt`, syncs the repo | The right path when you need GPU concurrency |

**The boundary sentence.** MLflow packages and hands off; Triton and KServe run the GPU.
`mlflow models serve` is a single-model Python process with no batching scheduler, no
multi-model repository and no GPU concurrency control — the production path is
MLflow-as-registry feeding a KServe `InferenceService` or a Triton model repository, not
MLflow-as-server.

### The GenAI surface (MLflow 3)

**Tracing** captures spans for LLM application code — one trace per request, spans for
retrieval, tool calls and model calls, with inputs, outputs and token counts as span
attributes. **Prompt Registry** makes a prompt template a versioned, aliased entity with the
same promotion vocabulary as a model. **Evaluation** was rewritten around scorers and judges,
including multimodal LLM judges in 3.15. **AI Gateway** puts one endpoint in front of many
providers with keys held server-side. **MCP Registry** (new in 3.15) does for MCP servers what
the model registry does for models. All of it writes to the same backend store — the
`trace_info`, `spans`, `assessments`, `scorers` and `mcp_servers` tables live beside `runs`.

---

## 5. Architecture Diagrams

**What MLflow owns, and what merely passes through it**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph yours["You own the compute"]
        trainer(["Training job<br/>Ray, Airflow, k8s Job"])
        server(["Serving runtime<br/>KServe, Triton"])
    end
    subgraph mlf["MLflow owns the record"]
        track["Tracking<br/>runs, params, metrics"]
        models["Models<br/>MLmodel directory"]
        reg["Registry<br/>versions and aliases"]
    end
    subgraph stores["Two stores, two credentials"]
        db[["Backend store<br/>PostgreSQL"]]
        blob[["Artifact store<br/>S3 or MinIO"]]
    end

    trainer -->|"log_param, log_metric"| track
    trainer -->|"log_model"| models
    models -->|"register_model"| reg
    track --> db
    reg --> db
    models --> blob
    reg -->|"models:/name@champion"| server
    blob -->|"pull artifacts"| server

    class trainer,server io
    class track,models mathOp
    class reg frozen
    class db,blob base
```

Nothing crosses from the right-hand column back into compute. The registry hands a serving
runtime a URI; the serving runtime does the work. Note that the two stores are reached
independently — which is the whole content of the next diagram.

**Proxied versus direct artifacts, and the 403 that only hits half a run**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    client(["CI runner<br/>has tracking URI"]) -->|"log_param<br/>REST"| srv["Tracking server"]
    srv --> db[["PostgreSQL"]]
    client -->|"log_artifact<br/>DIRECT mode"| bucket[["S3 bucket"]]
    client -->|"log_artifact<br/>PROXIED mode"| srv
    srv -->|"mlflow-artifacts:/"| bucket
    bucket --> deny{"has AWS creds?"}
    deny -->|"no, direct mode"| fail["403 AccessDenied<br/>params already written"]
    deny -->|"yes, or proxied"| ok(["artifact stored"])

    class client io
    class srv mathOp
    class db,bucket base
    class deny req
    class fail lossN
    class ok train
```

In direct mode the client writes metadata over HTTP to the server and bytes over S3 to the
bucket, so a credential gap splits a single logging call in half: the run exists, the params
are there, and the model is missing. In proxied mode (`--serve-artifacts`, the default) only
the server needs bucket credentials.

**The `MLmodel` handoff — one directory, many loaders**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    dir(["Model directory"]) --> manifest["MLmodel<br/>flavours, signature, env"]
    manifest --> native["Native flavour<br/>sklearn, torch, xgboost"]
    manifest --> pyf["python_function<br/>generic predict"]
    native -->|"full model object"| nb(["Notebook<br/>retrain, inspect"])
    pyf -->|"DataFrame in, array out"| batch(["Spark UDF<br/>batch scoring"])
    pyf --> srv(["mlflow models serve"])
    pyf --> docker(["build-docker<br/>image for KServe"])

    class dir,nb,batch,srv,docker io
    class manifest base
    class native frozen
    class pyf mathOp
```

The manifest is the interface. A consumer that knows nothing about your training framework
reads one YAML file and gets a `predict` it can call; a consumer that does know the framework
can ask for the native object instead. Both read the same directory.

**Alias resolution at load time — why promotion is not a redeploy**

```mermaid
sequenceDiagram
    participant Ops as Release engineer
    participant Reg as Model Registry
    participant Pod as Serving pod
    participant Art as Artifact store

    Ops->>Reg: set_registered_model_alias(fraud, champion, v8)
    Note over Reg: one row updated, no bytes moved
    Pod->>Reg: resolve models:/fraud@champion
    Reg-->>Pod: version 8, source URI
    Pod->>Art: download model directory
    Art-->>Pod: MLmodel plus weights
    Pod->>Pod: load pyfunc, warm up, flip readiness
    Ops->>Reg: rollback - alias champion back to v7
    Note over Reg,Pod: v7 was never deleted, so rollback is the same call in reverse
```

The alias move is a single row update; the redeploy is the pod choosing to re-resolve. That
split is deliberate — it means a bad promotion is undone in one API call, and it also means
nothing happens until something restarts or polls, which is a gotcha teams hit the first time
they expect a live swap.

**The entity model before and after MLflow 3**

```mermaid
flowchart LR
    classDef io      fill:#61afef,stroke:#2e86c1,color:#1a1a1a,font-weight:bold
    classDef frozen  fill:#c678dd,stroke:#9b59b6,color:#fff
    classDef train   fill:#98c379,stroke:#27ae60,color:#1a1a1a
    classDef mathOp  fill:#d19a66,stroke:#e67e22,color:#1a1a1a,font-weight:bold
    classDef lossN   fill:#e06c75,stroke:#c0392b,color:#fff,font-weight:bold
    classDef req     fill:#56b6c2,stroke:#0097a7,color:#1a1a1a
    classDef base    fill:#e5c07b,stroke:#f39c12,color:#1a1a1a

    subgraph v2["MLflow 2 - model is an artifact"]
        r2(["Run"]) --> a2["artifacts/model/"]
        a2 --> mv2["Registered version"]
    end
    subgraph v3["MLflow 3 - model is an entity"]
        r3(["Run"]) -.->|"optional link"| lm["LoggedModel<br/>m-abc123"]
        lm --> art3["models/m-abc123/artifacts/"]
        lm --> met3["logged_model_metrics<br/>scored after training"]
        lm --> mv3["Registered version"]
    end

    class r2,r3 io
    class a2,art3 base
    class mv2,mv3 frozen
    class lm mathOp
    class met3 train
```

In MLflow 2 a model existed only as a path inside a run's artifacts. In MLflow 3 it is a
first-class row with its own id, its own metrics and its own artifact location — so a model
can be logged outside any run, and evaluation metrics produced days later attach to the model
rather than to the training run. The artifact path moved with it, which is exactly what breaks
deploy scripts that walked `list_artifacts()` (§10, war story 7).
---

## 6. How It Works — Detailed Mechanics

### The tracking server process, with real defaults

`mlflow server` starts a uvicorn-hosted ASGI app (gunicorn and waitress remain available via
`--gunicorn-opts` / `--waitress-opts`). The defaults matter because most teams never change
them and then are surprised by one:

| Flag | Default in 3.15.1 | Why you care |
|---|---|---|
| `--backend-store-uri` | `sqlite:///mlflow.db` (falls back to `./mlruns` if present) | Changed from the old file-store default; SQLite still cannot take concurrent writers |
| `--registry-store-uri` | same as backend store | Split it only if you want registry on a different database |
| `--serve-artifacts` | **True** | Server proxies artifact IO by default; `--no-serve-artifacts` puts the bucket on the client |
| `--artifacts-destination` | `./mlartifacts` | Set this to `s3://…` or you are proxying into a local directory |
| `--host` | `127.0.0.1` | Not a security control. You need `0.0.0.0` for a real server |
| `--port` | `5000` | Collides with macOS AirPlay Receiver |
| `--workers` | `4` | Each worker holds its own SQLAlchemy pool — size the database accordingly |
| `--allowed-hosts` | localhost plus private IP ranges | Host-header / DNS-rebinding guard; must be set when exposed |
| `--cors-allowed-origins` | localhost on any port | Set for a notebook or app on another domain |
| `--x-frame-options` | `SAMEORIGIN` | Clickjacking guard; `NONE` to embed the UI |
| `--app-name` | unset (`mlflow.server:app`) | Only accepted value is `basic-auth` |
| `--expose-prometheus` | off | Point it at a directory to get `/metrics` |
| `--read-replica-backend-store-uri` | unset | Routes reads to a replica; **no automatic failover** |

```bash
# The shape almost every team should be running.
mlflow server \
  --backend-store-uri postgresql+psycopg2://mlflow:$PG_PASSWORD@pg.internal:5432/mlflow \
  --artifacts-destination s3://acme-mlflow-artifacts/prod \
  --serve-artifacts \
  --host 0.0.0.0 --port 5000 --workers 8 \
  --allowed-hosts mlflow.acme.internal \
  --cors-allowed-origins https://notebooks.acme.internal \
  --expose-prometheus /var/lib/mlflow/prom
```

The process is stateless. Scale it horizontally behind a load balancer; all shared state is in
PostgreSQL and the bucket. What does not scale horizontally is the database, which is where
the next subsection matters.

### The backend-store schema, and why `metrics` grows without bound

The SQLAlchemy models define the tables directly. The ones that carry volume:

```
experiments        one row per experiment
runs               one row per run          (~1 KB with tags)
params             one row per (run, key)   -- immutable
tags               one row per (run, key)   -- mutable
metrics            one row per (run, key, value, timestamp, step)   <-- unbounded
latest_metrics     one row per (run, key)   -- the newest value only
datasets, inputs, input_tags        dataset lineage
logged_models, logged_model_metrics, logged_model_params, logged_model_tags   [3.0+]
trace_info, spans, trace_tags, assessments, scorers                           [3.0+]
```

`metrics` is the table that eats the database. It is append-only and keyed by step, so one run
logging 5 metrics every step for 10,000 steps writes **50,000 rows**. A hundred such runs is
5 million rows in one experiment.

**`latest_metrics` exists because of the run list.** The runs table view, `search_runs`, and
every "sort by val_auc" query need the *current* value of each metric for each run. Computing
that from `metrics` means a correlated `MAX(timestamp)` subquery per run per key — a query
whose cost grows with training length, not with the number of runs. MLflow therefore maintains
a denormalized `latest_metrics` row per `(run, key)`, updated in the same transaction as the
insert into `metrics`. The consequence you can feel: **every metric write is two writes**, and
a step-level logging loop doubles the database load you were already worried about.

```sql
-- Where the space actually goes. Run this before you plan a migration.
SELECT relname, n_live_tup, pg_size_pretty(pg_total_relation_size(relid)) AS total
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC
LIMIT 8;

-- Runs whose per-step logging is out of control.
SELECT run_uuid, COUNT(*) AS metric_rows
FROM metrics
GROUP BY run_uuid
ORDER BY metric_rows DESC
LIMIT 20;
```

### The two artifact access modes, and the exact failure they produce

A client resolves an artifact write in two steps: ask the tracking server for the run's
artifact URI, then write there. What "there" means is the whole difference.

- **`mlflow-artifacts:/…`** — proxied. The client PUTs bytes to the tracking server's
  `/api/2.0/mlflow-artifacts/artifacts/…` endpoint; the server writes to
  `--artifacts-destination`. The client needs nothing but the tracking URI.
- **`s3://bucket/path`** — direct. The client constructs a boto3 client from the ambient
  environment and writes to S3 itself. The tracking server never sees the bytes.

**The exact failure.** In direct mode, a client with database reachability (via the server)
but no bucket credentials produces this:

```python
mlflow.set_tracking_uri("https://mlflow.acme.internal")
with mlflow.start_run():
    mlflow.log_param("lr", 3e-4)          # OK  -- REST to the server
    mlflow.log_metric("val_auc", 0.91)    # OK  -- REST to the server
    mlflow.log_artifact("model.pkl")      # botocore.exceptions.ClientError:
                                          #   An error occurred (403) when calling
                                          #   the PutObject operation: Access Denied
```

The run is created, the params and metrics are committed, and the run is left in `RUNNING`
because the exception escaped before the context manager set `FINISHED`. Nothing about the
error message mentions MLflow. Diagnose it with:

```bash
# Which mode is this experiment actually in?
python -c "import mlflow; print(mlflow.get_experiment_by_name('fraud').artifact_location)"
# mlflow-artifacts:/1  -> proxied, credentials are the server's problem
# s3://acme-ml/1       -> direct,  the client needs s3:PutObject on that prefix
```

Note that the mode is baked into the **experiment** at creation time, not read from the server
flag at write time. Flipping `--serve-artifacts` on the server does not migrate existing
experiments — `--default-artifact-root` only affects experiments created afterwards.

### Runs, experiments, and nested runs

An experiment is a namespace with an artifact location and a set of tags. A run belongs to
exactly one experiment and carries `run_id` (a 32-char hex), `status`, `start_time`,
`end_time`, `artifact_uri` and `lifecycle_stage` (`active` or `deleted`).

```python
import mlflow

mlflow.set_tracking_uri("https://mlflow.acme.internal")
mlflow.set_experiment("fraud/xgboost/auc")        # creates it if absent

with mlflow.start_run(run_name="sweep") as parent:
    parent_id = parent.info.run_id
    for cfg in configs:
        # nested=True links the child via the mlflow.parentRunId tag
        with mlflow.start_run(nested=True, run_name=f"lr_{cfg['lr']}"):
            mlflow.log_params(cfg)
            mlflow.log_metric("val_auc", train(cfg))
```

Nesting is implemented as an ordinary tag (`mlflow.parentRunId`) plus a client-side stack, not
as a foreign key. Two consequences: you can build the relationship by hand across processes
(`mlflow.start_run(nested=True, parent_run_id=parent_id)`), and the stack is **thread-local**,
which is the trap covered later in this section.

Deleting a run sets `lifecycle_stage = 'deleted'`. It removes nothing. See `mlflow gc`.

### Params, metrics, tags and datasets — the cardinality table

Read from `mlflow/utils/validation.py` in 3.15.1:

| Limit | Value | Behaviour on exceed |
|---|---|---|
| `MAX_ENTITY_KEY_LENGTH` | 250 | **Raises** `MlflowException` |
| `MAX_PARAM_VAL_LENGTH` | 6000 | **Truncated** with a warning (`MLFLOW_TRUNCATE_LONG_VALUES`, default on) |
| `MAX_TAG_VAL_LENGTH` | 8000 | **Truncated** with a warning |
| `MAX_EXPERIMENT_NAME_LENGTH` | 500 | Raises |
| `MAX_EXPERIMENT_TAG_VAL_LENGTH` | 5000 | Raises |
| `MAX_MODEL_REGISTRY_TAG_VALUE_LENGTH` | 100,000 | Raises |
| `MAX_REGISTERED_MODEL_ALIAS_LENGTH` | 255 | Raises |
| `MAX_PARAMS_TAGS_PER_BATCH` | 100 | Raises |
| `MAX_METRICS_PER_BATCH` | 1000 | Raises |
| `MAX_ENTITIES_PER_BATCH` | 1000 | Raises |
| `MAX_BATCH_LOG_REQUEST_SIZE` | 1,000,000 bytes | Raises |

The truncate-versus-raise split is the part to remember: **an over-long key fails loudly, an
over-long value fails silently.** Serialising a whole config dict into one param is the usual
way to hit it, and the truncated JSON that lands in the database is unparseable.

Choosing between the four:

| Store as | When | Cost |
|---|---|---|
| **param** | An input you chose, fixed for the run | Immutable — re-logging a different value raises |
| **metric** | A number that changes or that you will sort/threshold on | Two rows per write (`metrics` + `latest_metrics`) |
| **tag** | A label you may revise later — owner, ticket, status | Overwritable; string comparison only in search |
| **dataset input** | Which data went in | Structured lineage; searchable by digest |

The search-language difference is the practical reason a number belongs in metrics:
`metrics.val_auc > 0.9` is a numeric comparison; `params.val_auc` only supports `=`, `!=`,
`LIKE`, `ILIKE`, `IN`, because params are stored as strings.

### `log_batch` and the metric-write cost model

Every fluent call is a synchronous HTTP round trip, inside your training loop, on the critical
path. The arithmetic:

```
  per-call cost   = network RTT + server handling + 2 database writes (metrics + latest_metrics)
  naive loop      = steps x metrics_per_step calls
  batched loop    = ceil(total_points / MAX_METRICS_PER_BATCH) calls

  10,000 steps x 5 metrics, 5 ms RTT
    log_metric per metric per step : 50,000 calls x 5 ms = 250 s blocked per epoch
    log_metrics(dict) per step     : 10,000 calls x 5 ms =  50 s
    every 100th step, dict         :    100 calls x 5 ms = 0.5 s
    log_batch of 1,000 points      :     50 calls x 5 ms = 0.25 s   <- and 1 request each
```

```python
from mlflow.entities import Metric
from mlflow.tracking import MlflowClient
import time

client = MlflowClient()

def flush(run_id: str, buffer: list[tuple[str, float, int]]) -> None:
    """Ship up to 1000 metric points in one request (MAX_METRICS_PER_BATCH)."""
    now = int(time.time() * 1000)
    for i in range(0, len(buffer), 1000):
        chunk = buffer[i:i + 1000]
        client.log_batch(
            run_id,
            metrics=[Metric(key=k, value=v, timestamp=now, step=s) for k, v, s in chunk],
        )

buffer: list[tuple[str, float, int]] = []
for step, batch in enumerate(loader):
    loss = train_step(batch)
    buffer.append(("train_loss", float(loss), step))
    if len(buffer) >= 1000:               # one request per 1000 points
        flush(run_id, buffer); buffer.clear()
flush(run_id, buffer)
```

Batching does not reduce the *rows* written — `metrics` still gets 50,000 rows — it reduces
round trips. Throttling is what reduces rows, and it is the one that saves your database. Do
both: throttle to a resolution a chart can render (a few hundred points per curve), and batch
whatever survives.

### The autolog truth table, and what each flavour silently misses

`mlflow.autolog()` enables every available integration by monkey-patching the framework's
training entry point. What it patches is the whole story:

| Flavour | Patches | Silently misses |
|---|---|---|
| `sklearn` | `Estimator.fit`, `Pipeline.fit`, and the `*SearchCV` classes | Anything you compute after `fit` — your own test-set metrics |
| `pytorch` | **PyTorch Lightning `Trainer.fit` only** | **A hand-written training loop: zero params, zero metrics, no error** |
| `tensorflow` / `keras` | `Model.fit` via a callback, TF **>= 2.3** | Custom `train_step`, `GradientTape` loops |
| `xgboost` / `lightgbm` | `train()` via a callback | `best_iteration` — the run records the `num_boost_round` ceiling you passed, not where early stopping actually landed |
| `spark` | Datasource reads, **asynchronously** | Everything unless `PYSPARK_PIN_THREAD=false` on PySpark >= 3.2 |
| `transformers` | `Trainer.train` | A raw `accelerate` loop |
| `langchain`, `openai`, `llama_index`, `dspy`, `crewai`, `autogen`, `smolagents`, `pydantic_ai` | Traces, not runs | These log to the tracing surface — do not expect params and metrics |

**`mlflow.pytorch.autolog()` is the one that costs teams weeks.** The docstring is explicit
that full autologging is only supported for PyTorch Lightning models, and there is no warning
when you call it beside a hand-written loop: the run is created, the run is empty, and nobody
notices until someone tries to compare two experiments. If your loop is `for batch in loader:`
you must log manually, and the second-order damage is that a run with no parsed metrics also
looks like a run with nothing to migrate when someone later audits the experiment.

```python
# BROKEN: looks instrumented, logs nothing.
mlflow.pytorch.autolog()
with mlflow.start_run():
    for epoch in range(50):
        for batch in loader:
            loss = step(batch)          # never observed by MLflow

# FIX: autolog is for Lightning. A raw loop logs explicitly.
with mlflow.start_run():
    mlflow.log_params(cfg)
    for epoch in range(50):
        val = validate(model)
        mlflow.log_metrics({"val_loss": val.loss, "val_auc": val.auc}, step=epoch)
```

Autolog is also **best-effort by design**: exceptions inside the patched logging path are
swallowed so they cannot break training. That is the right trade, and it is another reason an
empty autologged run is not an error you will see.

### The `MLmodel` file, field by field

This is the real file produced by `mlflow.sklearn.log_model(..., signature=..., input_example=...)`
on 3.15.1, annotated:

```yaml
artifact_path: /…/mlruns/0/models/m-0a24c301…/artifacts   # where this directory lives
flavors:                                                  # >= 1; consumers pick one
  python_function:                                        # the universal flavour
    env:
      conda: conda.yaml                                   # three renderings of one env
      virtualenv: python_env.yaml
    loader_module: mlflow.sklearn                         # who reconstructs the object
    model_path: model.skops                               # the weights file, relative
    predict_fn: predict                                   # which method pyfunc calls
    python_version: 3.13.11                               # the interpreter that logged it
  sklearn:                                                # the native flavour
    pickled_model: model.skops
    serialization_format: skops                           # DEFAULT in 3.15 -- not pickle
    sklearn_version: 1.9.0
    skops_trusted_types: null
mlflow_version: 3.15.1
model_id: m-0a24c301ab8840bc9af390a84b80d1ca              # [3.0+] the LoggedModel id
model_size_bytes: 117149
model_uuid: m-0a24c301ab8840bc9af390a84b80d1ca
run_id: 627648c8e53549f0acf8ebeb6b27aba8                  # optional in 3.x
saved_input_example_info:
  artifact_path: input_example.json
  pandas_orient: split
  serving_input_path: serving_input_example.json          # a ready-made /invocations body
  type: dataframe
signature:
  inputs: '[{"type": "double", "name": "amount", "required": true},
            {"type": "long", "name": "age", "required": true}]'
  outputs: '[{"type": "tensor", "tensor-spec": {"dtype": "int64", "shape": [-1]}}]'
  params: null
utc_time_created: '2026-08-04 11:00:46.002863'
```

And the directory beside it:

```
models/m-0a24c301…/artifacts/
  MLmodel                      the manifest -- the only file a consumer must understand
  model.skops                  the weights
  requirements.txt             pip-installable pin set
  python_env.yaml              python version + build deps + -r requirements.txt
  conda.yaml                   the same set as a conda env
  input_example.json           what a valid input looks like
  serving_input_example.json   the same, shaped as an /invocations request body
```

Two details worth noticing. **`serialization_format: skops`** is the 3.15 default for sklearn,
replacing cloudpickle — skops refuses to load arbitrary code, which closes the "loading a model
executes a pickle" hole, at the cost of failing on estimators it does not trust. And
`artifact_path` at the top is a *path*, unrelated to the deprecated `artifact_path=` keyword of
`log_model`.

### Flavours and the `python_function` contract

A flavour is a named recipe for reconstructing a model. The `python_function` (pyfunc) flavour
is the one every consumer can rely on: given the directory, it produces an object with

```python
def predict(self, data, params: dict | None = None): ...
```

where `data` is a `pandas.DataFrame`, a numpy array, a dict of arrays, a list, or a scalar
depending on the signature, and the return is a DataFrame/Series/array. That is the whole
contract, and it is what `mlflow models serve`, `mlflow.pyfunc.spark_udf`,
`mlflow models build-docker`, KServe's MLflow runtime and every deployment plugin call.

Flavours present in 3.15.1 include `sklearn`, `pytorch`, `tensorflow`, `keras`, `xgboost`,
`lightgbm`, `catboost`, `spark`, `onnx`, `transformers`, `sentence_transformers`, `openai`,
`langchain`, `llama_index`, `dspy`, `crewai`, `autogen`, `smolagents`, `pydantic_ai`,
`statsmodels`, `prophet`, `spacy`, `h2o`, `paddle` and `johnsnowlabs`.

**Removed in MLflow 3: `fastai`, `mleap`, `diviner`, `gluon`.** A model logged under any of
those in a 2.x run cannot be loaded by a 3.x client. If you have them, load and re-log under a
supported flavour *before* upgrading, or wrap them in a custom `pyfunc`.

```python
import mlflow

class Fraud(mlflow.pyfunc.PythonModel):
    """Custom pyfunc: the escape hatch for anything without a flavour."""

    def load_context(self, context) -> None:
        import joblib
        self.model = joblib.load(context.artifacts["weights"])
        self.threshold = 0.62

    def predict(self, context, model_input, params=None):
        proba = self.model.predict_proba(model_input)[:, 1]
        cutoff = (params or {}).get("threshold", self.threshold)
        return (proba >= cutoff).astype(int)

with mlflow.start_run():
    mlflow.pyfunc.log_model(
        name="fraud",
        python_model=Fraud(),
        artifacts={"weights": "local/model.joblib"},
        pip_requirements=["scikit-learn==1.9.0", "joblib==1.5.2"],
    )
```

A custom pyfunc is also how you make pre- and post-processing part of the model rather than
part of the caller — the single highest-value refactor available in MLflow, because it removes
training/serving skew by construction.

### Signatures — inference, enforcement, and the unsigned-model landmine

`infer_signature(X, y_pred)` walks the input and output and produces a schema. What
enforcement then does, verified on 3.15.1:

| Input problem | Behaviour |
|---|---|
| Columns in a different order | **Silently reordered to the schema** — this is the win |
| Extra column | Warning logged, column ignored |
| Missing required column | `MlflowException: Model is missing inputs ['age']` |
| Uncastable type (string into a double) | `MlflowException: Failed to convert column …` |
| Safe widening (int into a double) | Converted silently |
| **No signature at all** | **Nothing is checked. Ever.** |

That last row is the landmine, and it is worth showing rather than asserting:

```python
# BROKEN -- no signature. The model was fit on a 2-column array [amount, age].
with mlflow.start_run():
    info = mlflow.sklearn.log_model(model, name="unsigned")

p = mlflow.pyfunc.load_model(info.model_uri)
p.predict(pd.DataFrame([[90.0, 30]]))   # amount, age -> [1]  correct
p.predict(pd.DataFrame([[30, 90.0]]))   # age, amount -> [0]  WRONG, and no error

# FIX -- log the signature. The same two calls now agree, because named columns
# are matched to the schema instead of consumed positionally.
sig = infer_signature(X_train, model.predict(X_train))
with mlflow.start_run():
    mlflow.sklearn.log_model(model, name="signed", signature=sig, input_example=X_train.head(3))
```

Both predictions above were produced by running the code; the unsigned model returns a
confidently wrong class and logs nothing. There is no metric that goes red for this.

**What `input_example` triggers at log time.** Passing one is not decoration. MLflow will
(1) infer a signature from it if you did not supply one, (2) write `input_example.json` and
`serving_input_example.json`, and (3) run a **prediction against the freshly-saved model** to
validate that it loads and scores — which catches a broken serialisation at log time rather
than at deploy time. It also emits the integer-column warning you will see in any real log:
an inferred `long` column cannot represent nulls, so a production request with a missing value
arrives as a float and fails enforcement. Infer the signature from data that contains the
missing values you expect, or declare those columns as doubles.

### Dependency capture — what MLflow pins, and what it cannot see

At log time MLflow walks the imported modules, maps them to distributions, and writes the
installed versions. For the sklearn model above that produced exactly:

```
mlflow==3.15.1
numpy==2.5.1
pandas==2.3.3
scikit-learn==1.9.0
scipy==1.18.0
skops==0.14.0
```

**MLflow pins what it saw, not what you meant.** The honest list of what is outside the model:

- the CUDA driver and toolkit, cuDNN, NCCL — a torch model's `requirements.txt` names
  `torch==2.10.0` and says nothing about the driver that wheel needs
- system shared libraries: glibc, OpenSSL, `libgomp`, MKL, the BLAS your numpy linked against
- the base image, the OS, and the CPU instruction set the wheels were built for
- environment variables that changed behaviour (`OMP_NUM_THREADS`, `TOKENIZERS_PARALLELISM`)
- anything installed but not imported during the run — a plugin loaded by entry point is
  invisible to the walker
- the *Python patch version*: `python_env.yaml` records `3.13.11`, and if that patch has been
  yanked from the index the env build fails outright

Override the inference when you know better:

```python
mlflow.sklearn.log_model(
    model, name="model",
    pip_requirements=["scikit-learn==1.9.0", "numpy==2.5.1"],   # replace inference entirely
    # or: extra_pip_requirements=["boto3>=1.40"]                # add to what was inferred
)
```

`--env-manager` at load/serve time chooses what to do with the captured environment:
`virtualenv` (default for `models serve`) or `conda` build it, `uv` builds it fast, and
`local` **ignores it and uses whatever is already installed**. `local` is right in a container
you built from the model's own `requirements.txt`; it is a silent-wrong-answer generator
anywhere else. `mlflow models predict --env-manager virtualenv` and
`mlflow.models.predict()` `[2.10+]` exist precisely so you can validate scoring inside the
model's declared environment from your own process.

### The registry API, and the three URI forms

```python
from mlflow.tracking import MlflowClient

client = MlflowClient()

# 1. Register a logged model as a new version of a named registered model.
mv = mlflow.register_model(model_uri=info.model_uri, name="fraud")
#    or, from the run: mlflow.register_model(f"runs:/{run_id}/model", "fraud")

# 2. Record WHY it is eligible (a tag) and WHERE it is live (an alias).
client.set_model_version_tag("fraud", mv.version, "validation_status", "passed")
client.set_model_version_tag("fraud", mv.version, "eval_dataset", "holdout_2026_07")
client.set_registered_model_alias("fraud", "challenger", mv.version)

# 3. Promotion is one atomic call. So is rollback.
client.set_registered_model_alias("fraud", "champion", mv.version)
client.set_registered_model_alias("fraud", "champion", previous_version)   # rollback

# 4. Resolve. Serving code should never hardcode a version number.
model = mlflow.pyfunc.load_model("models:/fraud@champion")
```

The three (now four) URI forms and when each is correct:

| URI | Resolves to | Use it |
|---|---|---|
| `runs:/<run_id>/<name>` | An artifact inside one run | Registering; debugging one specific run |
| `models:/<name>/<version>` | An exact, immutable version | Reproducing an incident; pinned batch jobs |
| `models:/<name>@<alias>` | Whatever the alias points at now | **Serving.** The only one that makes rollback free |
| `models:/<model_id>` `[3.0+]` | A LoggedModel by id, registered or not | Evaluating a candidate before it earns a name |

`load_model` also accepts a raw `s3://` path, which works and throws away every piece of
lineage — the URI *is* the audit trail.

### Stages, and why they are gone

Until 2.9.0 a model version had a `current_stage` field: `None`, `Staging`, `Production`,
`Archived`, moved with `transition_model_version_stage(..., archive_existing_versions=True)`.
The method **still exists in 3.15.1**, decorated `@deprecated(since="2.9.0")`, and the docs
keep a "Migrating from Stages" page. Unity Catalog registries never supported it at all.

The deprecation is worth understanding as design, not trivia, because the failure is one every
status-field design hits:

- **A stage is a single enum, so a version can be in exactly one.** Real rollouts need a
  version that is champion in `eu-west` and challenger in `us-east` simultaneously. A stage
  cannot express that; two aliases can.
- **The vocabulary is fixed.** `Staging` and `Production` are the only two words you get. Teams
  wanted `canary`, `shadow`, `baseline`, `champion-2026q3` — and encoded them in tags anyway,
  so the stage stopped being the source of truth.
- **`archive_existing_versions=True` mutates other rows.** One promotion silently rewrote the
  stage of every other Production version, which is a broadcast write dressed as a local one.
- **It conflated two different questions.** "Is this version approved?" is a property of the
  version and never changes. "Is this version live?" is a property of the deployment and
  changes constantly. Tags answer the first, aliases the second.

```python
# OLD -- deprecated since 2.9.0, unsupported on Unity Catalog.
client.transition_model_version_stage("fraud", version=8, stage="Production",
                                      archive_existing_versions=True)

# NEW -- one immutable fact, one movable pointer.
client.set_model_version_tag("fraud", 8, "validation_status", "passed")
client.set_registered_model_alias("fraud", "champion", 8)
```

Migrating is mechanical: for each version, write a tag recording its old stage, then point
`@champion` at whatever was `Production`. Do it before you need Unity Catalog, not during.

### Lineage — datasets, inputs, and `LoggedModel`

`mlflow.data` turns "which data trained this" from a tag convention into a first-class record
with a **digest**:

```python
import mlflow.data
import pandas as pd

df = pd.read_parquet("s3://acme-data/fraud/2026-07-31/train.parquet")
ds = mlflow.data.from_pandas(
    df,
    source="s3://acme-data/fraud/2026-07-31/train.parquet",
    name="fraud_train",
    targets="is_fraud",
)
with mlflow.start_run():
    mlflow.log_input(ds, context="training")     # rows into `datasets` + `inputs`
    ...
```

The digest is content-derived, so two runs claiming the same S3 path with different bytes are
distinguishable — which the bare `mlflow.log_param("data_path", …)` convention cannot do.
`mlflow.data.from_spark`, `from_numpy`, `from_huggingface_dataset` and `from_delta` cover the
usual sources; a Delta source records the table version.

**`LoggedModel`** `[3.0+]` is the other half of lineage. In MLflow 2 a model was a path inside
a run's artifacts, so it inherited the run's identity and could carry no metrics of its own. In
MLflow 3 it is a row in `logged_models` with its own id (`m-…`), its own
`logged_model_metrics`, `logged_model_params` and `logged_model_tags`, and an optional link to
a run. That buys three things:

1. **`log_model` works outside a run.** Registering an externally-produced artifact no longer
   requires inventing a fake run to hang it on.
2. **Evaluation metrics attach to the model.** A model scored on a new dataset three weeks
   after training gets those numbers on the model, not smeared onto the training run.
3. **A run may produce several models** — a preprocessing pyfunc and a classifier, or one
   checkpoint per fold — each independently addressable.

The cost is the artifact-path change described in the next paragraph.

**The path change that breaks scripts.** Model artifacts moved from
`experiments/<exp_id>/<run_id>/artifacts/<name>/` to
`experiments/<exp_id>/models/<model_id>/artifacts/`. Verified on 3.15.1: a model logged into
experiment `0` landed at `mlruns/0/models/m-0a24c301…/artifacts/`. Anything that enumerated
`client.list_artifacts(run_id)` looking for a `model/` entry finds nothing after the upgrade —
the run has no model artifact any more. Use `mlflow.search_logged_models()` or the
`model_uri` returned by `log_model()`.

### `mlflow models serve` and the `/invocations` protocol

```bash
mlflow models serve -m "models:/fraud@champion" -h 0.0.0.0 -p 5001 --env-manager virtualenv
```

That starts one FastAPI process wrapping one pyfunc. The endpoints are `/ping` and `/health`
(liveness), `/version`, and `/invocations` (scoring). The request body must carry exactly one
of these keys:

```jsonc
{"dataframe_split": {"columns": ["amount", "age"], "data": [[90.0, 30], [12.5, 44]]}}
{"dataframe_records": [{"amount": 90.0, "age": 30}]}
{"instances": [[90.0, 30]]}          // TF-Serving-compatible
{"inputs": {"amount": [90.0], "age": [30]}}
{"dataframe_split": {...}, "params": {"threshold": 0.7}}   // params -> pyfunc predict(params=)
```

The response is `{"predictions": [...]}`. `serving_input_example.json` in the model directory
is a ready-made body — `curl -d @serving_input_example.json` is the fastest possible smoke
test.

**Why this is not a production server**, stated plainly because interviewers ask it:

- one model per process; no model repository, no multi-model memory management
- no request batching scheduler — concurrent requests are handled by worker processes, and the
  model's own thread usage is whatever the framework does by default
- no GPU concurrency control, no instance groups, no CUDA stream management
- no model warmup contract, no graceful version switching
- metrics are what you bolt on; there is no first-class latency histogram

It is the correct tool for "does this artifact load and score", and the wrong tool for traffic.

### `build-docker`, `mlflow deployments`, and the handoff

```bash
# Bake model + environment into an image. This is the real handoff artefact.
mlflow models build-docker \
  -m "models:/fraud@champion" \
  -n registry.acme.io/ml/fraud:$(git rev-parse --short HEAD) \
  --enable-mlserver          # MLServer instead of the built-in FastAPI scorer

# Or emit the Dockerfile and own the build.
mlflow models generate-dockerfile -m "models:/fraud@champion" -d build/fraud
```

`--enable-mlserver` matters: MLServer is the runtime KServe and Seldon speak, so an image built
that way drops into either without a shim.

`mlflow deployments` (the plugin CLI: `create`, `update`, `delete`, `predict`,
`create-endpoint`, …) pushes a model to a registered target — SageMaker and Azure ML being the
maintained ones. Note what is **not** there any more: `mlflow deployments start-server`, the
MLflow 2 deployment-server app, was **removed in MLflow 3**, along with the gateway
configuration keys `routes` and `route_type` (now `endpoints` and `endpoint_type`).

The production shapes, concretely:

```yaml
# KServe pulls straight from the registry. MLflow's job ends at the URI.
apiVersion: serving.kserve.io/v1beta1
kind: InferenceService
metadata:
  name: fraud
spec:
  predictor:
    model:
      modelFormat: {name: mlflow}
      storageUri: "models:/fraud@champion"     # requires MLFLOW_TRACKING_URI in the pod env
      resources:
        limits: {cpu: "2", memory: 4Gi}
```

For Triton the handoff is an export step, not a URI: CI loads `models:/fraud@champion`, exports
to ONNX or TorchScript, writes the `config.pbtxt`, and syncs the directory into the model
repository. MLflow's contribution is the *provenance* — the repo directory carries the model
version and run id as labels, so an incident traces back. The GPU concurrency, batching and
ensemble mechanics belong to
[NVIDIA Triton Inference Server](../../technologies/nvidia_triton_inference_server/nvidia_triton_inference_server.md)
and are not MLflow's business.

### MLflow Projects — and the honest verdict

An `MLproject` file declares entry points, parameters and an environment:

```yaml
name: fraud
python_env: python_env.yaml          # or conda_env: conda.yaml, or docker_env:
entry_points:
  train:
    parameters:
      lr: {type: float, default: 3e-4}
      data: path
    command: "python train.py --lr {lr} --data {data}"
```

```bash
mlflow run . -e train -P lr=1e-4 -P data=s3://acme-data/train.parquet
mlflow run https://github.com/acme/fraud.git#pipelines -v 4f2c1a9 -P lr=1e-4
```

**The honest verdict: adoption is near zero and you should not start here.** Projects solved
"run this repo reproducibly" in 2018, when containerising a Python ML job was genuinely hard.
Today the environment problem is solved by an image and the execution problem is solved by an
orchestrator — Airflow, Argo Workflows, Kubeflow Pipelines, Ray Jobs, or a plain Kubernetes
Job — every one of which has retries, scheduling, resource requests, secrets and observability
that Projects has never had. `mlflow run` shells out to your command with environment
variables set; that is the entire feature.

The supporting evidence is that MLflow itself moved on: **Recipes** (the successor abstraction,
previously MLflow Pipelines) was **removed in MLflow 3**. What survives from the idea is the
useful half — `MLFLOW_RUN_ID` and `MLFLOW_EXPERIMENT_ID` in the job's environment so the job
logs into a run its orchestrator created. Use that, skip the rest.

### Fluent versus client API, and the thread-locality trap

Two APIs sit over the same REST surface:

- **Fluent** (`mlflow.log_param`, `mlflow.start_run`) — keeps an *active run* on a
  **thread-local stack**, so calls need no run id.
- **Client** (`MlflowClient().log_param(run_id, …)`) — explicit run id on every call, no
  hidden state.

The trap follows directly:

```python
# BROKEN: Optuna's n_jobs is THREAD-based. Worker threads have an empty run stack, so
# `nested=True` silently produces flat top-level runs and the parent groups nothing.
with mlflow.start_run(run_name="hpo"):
    study.optimize(objective, n_trials=100, n_jobs=8)     # objective calls start_run(nested=True)

# FIX A: keep the fluent API and pass the parent explicitly.
parent_id = mlflow.active_run().info.run_id
def objective(trial):
    with mlflow.start_run(nested=True, parent_run_id=parent_id):
        ...

# FIX B: use the client API in anything concurrent. No ambient state to lose.
client = MlflowClient()
run = client.create_run(experiment_id, tags={"mlflow.parentRunId": parent_id})
client.log_batch(run.info.run_id, metrics=[...], params=[...])
client.set_terminated(run.info.run_id, "FINISHED")
```

The same applies to `ThreadPoolExecutor`, to any async framework that hops event-loop threads,
and to `mlflow.spark.autolog()` — which is why that one requires `PYSPARK_PIN_THREAD=false` on
PySpark >= 3.2. Rule of thumb: **fluent in a notebook and a single-threaded script, client in
a library, a service, or anything parallel.**

### GenAI tracing

```python
import mlflow

mlflow.set_experiment("support-agent")
mlflow.openai.autolog()            # every OpenAI call becomes a span
mlflow.langchain.autolog()

@mlflow.trace(span_type="RETRIEVER")
def retrieve(query: str) -> list[str]:
    return vector_store.search(query, k=8)

@mlflow.trace                       # the root span for one request
def answer(question: str) -> str:
    docs = retrieve(question)
    return llm.invoke(prompt.format(docs=docs, q=question))
```

A trace is one row in `trace_info` plus its `spans`; spans carry inputs, outputs, latency,
token counts and errors. The model is OpenTelemetry-shaped — spans, attributes, parent links —
and MLflow can export to an OTLP collector, so it slots beside your existing tracing rather
than replacing it. What MLflow adds over a generic tracer is that traces live in the same
experiment as your runs and can be turned into an **evaluation dataset**: capture production
traces, label them, and score a candidate prompt against them.

Two operational notes. Traces are written asynchronously with a background exporter, so a
process that exits immediately can drop them (`mlflow.flush_trace_async_logging()`). And trace
volume is production request volume, not experiment volume — the `spans` table will dwarf
`metrics` if you enable tracing on a live service without sampling.

### Prompt Registry and the evaluation revamp

A prompt template becomes a registered entity with versions and aliases, exactly like a model:

```python
prompt = mlflow.genai.register_prompt(
    name="support-answer",
    template="Answer using only the context.\n\nContext: {{context}}\n\nQ: {{question}}",
    commit_message="tighten grounding instruction",
)
mlflow.genai.set_prompt_alias("support-answer", alias="production", version=prompt.version)
p = mlflow.genai.load_prompt("prompts:/support-answer@production")
```

The point is not storage — it is that a prompt change becomes a reviewable, revertible,
attributable version, and that a trace records which prompt version produced it.

**Evaluation changed shape in MLflow 3**, and the breaking bits are specific:

| MLflow 2 | MLflow 3 |
|---|---|
| `baseline_model=` on `mlflow.evaluate` | **Removed** — use `mlflow.validate_evaluation_results()` |
| `higher_is_better=` on a metric | Renamed **`greater_is_better=`** |
| `custom_metrics=` | **Removed** — use `extra_metrics=` |
| SHAP explainer logged by default | **Not logged by default** — opt in via `evaluator_config` |
| `mlflow.evaluate` for everything | `mlflow.genai.evaluate` with `scorers=` for LLM work |

```python
results = mlflow.evaluate(
    model="models:/fraud@challenger",
    data=eval_df, targets="is_fraud", model_type="classifier",
    extra_metrics=[my_cost_weighted_metric],          # was custom_metrics
)
mlflow.validate_evaluation_results(                    # was baseline_model
    candidate_result=results,
    baseline_result=mlflow.evaluate(model="models:/fraud@champion", data=eval_df,
                                    targets="is_fraud", model_type="classifier"),
    validation_thresholds={"roc_auc": mlflow.models.MetricThreshold(
        threshold=0.90, min_absolute_change=0.005, greater_is_better=True)},
)
```

MLflow 3.15 adds **multimodal LLM judges**, so a scorer can grade an answer that includes an
image. The judges are model calls: they cost money per evaluation row and they drift when the
judge model is upgraded, so pin the judge and version your rubric.

### The AI Gateway

One endpoint over many providers, with API keys held server-side so application code never
sees them:

```yaml
# gateway.yaml  -- note: `endpoints`/`endpoint_type`. The MLflow 2 keys `routes`
# and `route_type` were REMOVED in MLflow 3.
endpoints:
  - name: chat
    endpoint_type: llm/v1/chat
    model:
      provider: anthropic
      name: claude-sonnet-4-5
      config:
        anthropic_api_key: $ANTHROPIC_API_KEY
  - name: embeddings
    endpoint_type: llm/v1/embeddings
    model:
      provider: openai
      name: text-embedding-3-large
      config:
        openai_api_key: $OPENAI_API_KEY
```

The gateway gives you provider-agnostic request shapes, central key rotation, and one place to
attach rate limits and cost accounting. What it does not give you is the routing intelligence
of a dedicated LLM gateway — no semantic caching, no automatic fallback chains, no per-tenant
budgets — so it is the right choice when you already run MLflow and want to stop scattering
keys, and the wrong choice when gateway behaviour is the product. Note again that the standalone
`mlflow deployments start-server` app is gone in MLflow 3; the gateway is served by the
tracking server.

### Operating it — sizing, growth, cleanup, tenancy and auth

**Tracking database sizing.** The arithmetic that actually predicts your database:

```
  rows per run  = 1 (runs)
                + P (params)
                + T (tags, ~8 with the mlflow.* defaults)
                + M x S (metrics)          <- the term that matters
                + M     (latest_metrics)

  a "normal" run  : 30 params, 12 tags, 6 metrics x 200 epochs = 1,200 + 6 + 43 ~= 1,250 rows
  a step-logged run: 6 metrics x 100,000 steps                 = 600,000 rows, from ONE run

  500 normal runs/week x 52 weeks x 1,250 rows ~= 32.5 M rows/year   -- fine on PostgreSQL
  50 step-logged runs                          ~= 30 M rows          -- from fifty jobs
```

The lesson is not "MLflow does not scale" — it is that **logging resolution, not run count, is
the scaling variable**. Throttle to a few hundred points per curve and a single PostgreSQL
instance carries years of a large team's work.

**Artifact growth.** Artifacts are the bigger bill and nothing deletes them automatically. A
7 GB checkpoint per run at 500 runs a week is 3.5 TB a week. Two controls: `log_models=False`
in autolog for sweep trials (register only the winner), and a bucket **lifecycle policy** that
expires objects under experiment prefixes you have declared disposable. Do not rely on `gc`
for volume — see below.

**`mlflow gc` and the deletion trap.**

```bash
mlflow db upgrade postgresql://…                # run migrations before a version bump
mlflow gc --older-than 30d --backend-store-uri postgresql://…
```

`mlflow gc` **only touches runs already in the `deleted` lifecycle stage.** Deleting a run in
the UI is a soft delete: it flips a flag, leaves every row in the database and every byte in
the bucket. A team that "cleaned up six months of runs" and saw no change in the S3 bill has
done exactly this. Two further gotchas: with artifact proxying enabled you **must** set
`MLFLOW_TRACKING_URI` in the environment or gc cannot resolve artifact URIs and will skip the
files silently; and gc does not check whether a run backs a registered model version — it will
happily delete the artifacts behind a version you are serving.

**Multi-tenancy.** Open-source MLflow has no projects, no namespaces and no org model. The
options are: one server per team (simplest, and what most large orgs actually do); one server
with an experiment-naming convention plus bucket-prefix IAM; or `--enable-workspaces` `[3.x]`,
a backwards-compatible logical isolation of experiments, registered models and prompts, which
is off by default and is not a security boundary on its own.

**Authentication — say the thinness out loud.** The built-in plugin is enabled with
`mlflow server --app-name basic-auth`. It is **HTTP Basic authentication**, credentials in a
`basic_auth.ini`-configured store, with per-experiment and per-registered-model permissions
(`READ`, `EDIT`, `MANAGE`, `NO_PERMISSIONS`), and it is still marked **experimental**. There is
no SSO, no OIDC, no groups, no token expiry and no audit log. Nobody should treat it as the
access control layer for a shared production registry.

What to do instead: terminate auth in front of the server — an OAuth2-proxy, an ingress with
OIDC, or a service mesh — and give MLflow the identity as a header; keep the server off the
public internet; set `--allowed-hosts` and `--cors-allowed-origins` explicitly; and back it
all with bucket-level IAM so a leaked tracking token cannot read another team's artifacts. On
Databricks, Unity Catalog supplies the real thing and this whole paragraph becomes someone
else's problem.
---

## 7. Real-World Examples

**Databricks** created MLflow in 2018 and open-sourced it under Apache-2.0. The interesting
thing about the relationship is what Databricks did *not* do: it kept the format open, so
MLflow's managed variant and its self-hosted variant read the same `MLmodel` directory. That
is why an MLflow-shaped model is portable off Databricks — and why "we use MLflow" says nothing
about whether you are a Databricks customer. On Databricks the registry is backed by **Unity
Catalog**, where a model is `catalog.schema.model` with real governance and no stages.

**The three hyperscalers all ingest MLflow models directly**, which is the clearest evidence
that the format, not the server, is the durable part. Azure Machine Learning treats MLflow as
its native tracking and packaging format — an MLflow model deploys to a managed endpoint with
no scoring script. AWS ships a managed MLflow tracking server as a SageMaker feature, so the
tracking API you already call is the one the platform hosts. Google's Vertex AI Model Registry
imports MLflow-format artifacts. In each case what crosses the boundary is a directory with an
`MLmodel` file.

**The self-hosted-plus-Kubernetes shape** is what most non-Databricks teams run: MLflow on a
Deployment behind an OIDC ingress, PostgreSQL (usually RDS or Cloud SQL), an S3 or MinIO
bucket, and KServe pulling `models:/<name>@champion`. Nothing in that stack is MLflow-specific
except the tracking server itself, which is the point — the pieces are individually replaceable.

**MLflow inside a larger platform** is the pattern at organisations that build their own ML
platform. The platform team keeps MLflow as the metadata and packaging layer and builds their
own orchestration, feature store and serving on top, because MLflow's refusal to own execution
means it does not fight the platform. The tell that a team is in this mode: they talk about
"the registry" and "the tracking URI" as infrastructure primitives, not as a product.

**Where MLflow gets replaced.** Research teams that need rich visualisation, media logging and
managed distributed sweeps generally use Weights & Biases and keep MLflow only as an export
format at the handoff to production. That is a reasonable division and worth saying in an
interview: MLflow's charting is deliberately basic, and pretending otherwise is a tell.

---

## 8. Tradeoffs

**MLflow against the alternatives**

| Axis | MLflow | Weights & Biases | Neptune | ClearML | Databricks-managed MLflow |
|---|---|---|---|---|---|
| Licence / hosting | Apache-2.0, self-host | SaaS, self-host tier | SaaS, self-host tier | Apache-2.0, self-host or SaaS | Managed |
| Visualisation | Basic | Excellent | Very good | Good | Basic plus platform UI |
| Model packaging format | **`MLmodel`, portable, an ecosystem standard** | Artifacts, W&B-shaped | Artifacts | Artifacts | Same as MLflow |
| Registry | Versions, aliases, tags | Model registry with lineage | Model registry | Model registry | Unity Catalog governance |
| Built-in HPO | No — pair with Optuna | Sweeps | No | HPO service | No |
| Orchestration | **None, by design** | None | None | Agents and queues | Databricks Jobs |
| Auth / RBAC | Experimental basic-auth | Full SaaS RBAC | Full SaaS RBAC | Full RBAC | Enterprise SSO plus UC |
| Air-gapped | Yes | Self-host tier only | Self-host tier only | Yes | No |

**Decisions inside MLflow**

| Decision | Option A | Option B | Pick A when | Pick B when |
|---|---|---|---|---|
| Backend store | SQLite | PostgreSQL | One person, one machine | Literally any other case |
| Artifact access | Proxied (`--serve-artifacts`) | Direct | Clients must not hold bucket creds; simpler IAM | Artifact volume would saturate the server |
| Logging API | Fluent | `MlflowClient` | Notebook, single-threaded script | Library, service, anything parallel |
| Instrumentation | Autolog | Manual | sklearn / XGBoost / Lightning; prototyping | Raw loops; you need exactly-defined metrics |
| Env at load time | `virtualenv` or `uv` | `local` | You need the model's declared env rebuilt | You built the container from that env already |
| Model lifecycle | Aliases plus tags | Stages | Always | Never — deprecated since 2.9.0 |
| Serving | `mlflow models serve` | Registry into KServe or Triton | Smoke test on a laptop | Production traffic |
| Registry backend | Open-source SQL | Unity Catalog | Self-hosted, no governance mandate | Databricks; you need lineage and grants |

**What each design choice costs**

| Choice MLflow made | What it buys | What it costs |
|---|---|---|
| No orchestration | Runs under anything; tiny blast radius | You must bring a scheduler and a retry policy |
| Model as a directory | Framework-agnostic handoff; ecosystem adoption | The directory can be internally inconsistent with reality (see dependency capture) |
| Optional signature | Zero friction to log a first model | A model with no contract scores anything you give it |
| `latest_metrics` denormalisation | Fast run lists and sorting | Two database writes per metric point |
| Alias indirection | Free rollback, no redeploy | Nothing swaps until a consumer re-resolves |
| Params immutable | Runs are trustworthy receipts | A typo in a param name is permanent for that run |

---

## 9. When to Use / When NOT to Use

**Use MLflow when:**

- You need provenance — the ability to answer "what produced this model" months later — and
  you want it as a side effect of the workflow rather than a discipline people must remember.
- Models cross a team boundary. The `MLmodel` directory is the cheapest handoff contract
  available, and the receiving team needs to know nothing about your framework.
- You are self-hosted, on-premise, or air-gapped. Apache-2.0, no phone-home, PostgreSQL plus a
  bucket is the whole dependency list.
- You are on Databricks, Azure ML, or SageMaker, where MLflow is the native or managed format
  and using anything else means fighting the platform.
- You want a registry whose promotion semantics are metadata edits, so rollback is one API
  call rather than a deploy.
- You already run an orchestrator and want a metadata layer that will not fight it.

**Do NOT use MLflow when:**

- **You need it to run your training.** It will not. Bring Airflow, Argo, Ray or Kubernetes.
- **You need it to serve production traffic.** `mlflow models serve` is one model in one Python
  process with no batching scheduler and no GPU concurrency control. Hand off to KServe or
  Triton.
- **Rich interactive visualisation is the requirement.** Media logging, parallel-coordinates
  sweep views and collaborative report-building are Weights & Biases' territory and MLflow is
  not close.
- **You need built-in hyperparameter search.** MLflow has no sampler and no pruner. Pair it
  with Optuna or Ray Tune, both of which log into MLflow happily.
- **You need real RBAC and SSO on open-source MLflow.** The built-in plugin is experimental
  HTTP Basic auth. Either put a real identity proxy in front of it or use a managed offering.
- **Data versioning is the primary problem.** MLflow records a dataset digest; it does not
  store, diff, or restore data. Use DVC, Delta Lake or an Iceberg snapshot and log the
  reference.
- **You are logging every training step at full resolution and cannot throttle.** The `metrics`
  table is the wrong shape for a high-frequency time series. Send those to Prometheus or a
  time-series store and log epoch summaries to MLflow.

---

## 10. Common Pitfalls

**Pitfall 1 — The artifact 403 that only appears in CI.** A team's nightly training worked on
laptops and failed in GitHub Actions. Every run appeared in the UI with complete params and
metrics, and no model. The runner had `MLFLOW_TRACKING_URI` but no AWS credentials, and the
experiment's artifact root was a direct `s3://` path, so `log_param` went over HTTPS to the
server and `log_artifact` went straight to S3 as an anonymous caller. Six weeks of nightly runs
had no models. **Fix:** move the experiment to proxied artifacts (`--serve-artifacts` plus
`--artifacts-destination`) so only the server holds bucket credentials, or grant the CI role
`s3:PutObject` on the prefix. **Detect it** by asserting on the artifact list at the end of
every training job — `assert client.list_artifacts(run_id)` is one line and would have caught
it on night one.

**Pitfall 2 — An unsigned model scored silently wrong for 11 days.** A fraud model was logged
without a signature. A downstream service was refactored and started building its request
DataFrame from a dict whose key order had changed, so `amount` and `age` swapped columns.
Nothing errored: the pyfunc consumed the frame positionally and returned confident, wrong
classes. Approval rates moved 3 percentage points, which sat inside normal weekly variance,
and the bug was found 11 days later by someone reading a SHAP plot. **Fix:** always pass
`signature=infer_signature(X_train, y_pred)`; MLflow then reorders columns to the schema and
raises on a missing one. **Add a CI gate** that fails the build if
`Model.load(uri).signature is None`.

**Pitfall 3 — `mlflow.pytorch.autolog()` on a hand-written loop produced zero metrics.** A team
migrating from Lightning to a custom loop kept the autolog call at the top of the script. 40
training runs completed over two weeks with correct artifacts and completely empty metric
histories; the model comparison meeting had nothing to compare. `mlflow.pytorch.autolog()`
patches PyTorch Lightning's `Trainer.fit` and nothing else, and autolog swallows its own
exceptions by design, so there is no warning. **Fix:** log manually in a raw loop. **Detect
it** with a post-run assertion that the run has the metric keys you expect.

**Pitfall 4 — SQLite under a parallel sweep.** An Optuna study with 16 parallel worker
processes pointed at `sqlite:///mlflow.db`. Roughly one trial in five died with
`sqlite3.OperationalError: database is locked`, and because the exception surfaced from inside
`log_metric` the trial was recorded as failed rather than retried — the study's best value was
drawn from a biased subset of configurations. SQLite serialises writers behind a single file
lock; sixteen writers at epoch boundaries is a guaranteed collision. **Fix:** PostgreSQL. It is
a one-line `--backend-store-uri` change and removes the entire class.

**Pitfall 5 — Per-step metrics produced a 40 GB `metrics` table.** A team logged 8 metrics on
every step for runs averaging 120,000 steps. That is 960,000 metric rows per run, doubled by
`latest_metrics` maintenance on the write path. After roughly 40 such runs the table passed
40 GB, the runs list took 30 seconds to load, and a `search_runs` ordered by a metric timed
out. The charts were unreadable anyway — no browser renders a million points. **Fix:** throttle
to a resolution a chart can use (every 100th step plus an epoch summary is ~200 points per
curve), batch what survives with `log_batch`, and send genuine high-frequency telemetry to a
time-series store. **Remediation** is a partition-and-delete on `metrics` by `run_uuid`, which
needs a maintenance window; prevention is one `if step % 100 == 0`.

**Pitfall 6 — Six months of deleted runs and a bucket that never shrank.** Someone tidied the
UI, deleting about 4,000 old runs, and the finance team asked why storage spend was flat.
Deleting a run in MLflow sets `lifecycle_stage = 'deleted'`; it removes no rows and no bytes.
`mlflow gc` is the only thing that reclaims, it only considers runs already in the deleted
stage, and with artifact proxying enabled it silently skips artifact deletion unless
`MLFLOW_TRACKING_URI` is set in its environment. The team had run `gc` once, without that
variable, and concluded it did not work. **Fix:** run `mlflow gc --older-than 30d` with the
tracking URI exported, and put a bucket lifecycle policy on experiment prefixes you have
declared disposable. **Check before you gc** that no registered model version points at the
runs you are about to erase — gc does not.

**Pitfall 7 — An MLflow 3 upgrade broke the deploy script.** The deploy job found the model by
walking `client.list_artifacts(run_id)` for an entry named `model`, then constructed
`runs:/<run_id>/model`. After the upgrade the call returned an empty list for every new run and
the job failed closed — better than the alternative, but every deploy stopped. In MLflow 3 a
model is a `LoggedModel` entity stored under `experiments/<exp_id>/models/<model_id>/artifacts/`,
not under the run's artifact tree. **Fix:** use the `model_uri` that `log_model()` returns, or
`mlflow.search_logged_models()`, and stop reconstructing URIs by string concatenation. The same
upgrade renames `log_model(artifact_path=)` to `name=` — `artifact_path` still works with a
deprecation warning in 3.15.1, and passing **both** raises.

**Pitfall 8 — `conda.yaml` pinned a Python patch that no longer existed.** A model logged
eighteen months earlier declared `python=3.11.4`. That patch had been removed from the
channel, so `mlflow models serve --env-manager conda` failed to solve the environment on every
attempt. Under deadline someone switched to `--env-manager local`, the server started, and the
model scored — inside the *caller's* environment, which had NumPy 2.x against a model
serialised under NumPy 1.x. Predictions came back numerically shifted rather than erroring,
and a downstream threshold that had been tuned at 0.62 was now cutting a different population.
**Fix:** treat `--env-manager local` as valid only inside a container built from that model's
own `requirements.txt`. Pin the image digest alongside the model version, validate scoring with
`mlflow.models.predict()` `[2.10+]` in the declared environment before promoting, and prefer
`python_env.yaml` with `uv` over `conda.yaml` for reproducibility on a patch-version boundary.

---

## 11. Technologies & Tools

- **MLflow** — the platform itself. Version 3.15.1, released 2026-08-03, Apache-2.0, created by Databricks in 2018. Records, packages and labels; never executes.
- **MLflow Tracking** — runs, params, metrics, tags, datasets and artifacts, over a SQL backend store plus an object artifact store.
- **MLflow Models** — the `MLmodel` directory format: flavours, signature, and three renderings of the dependency set. The reason the ecosystem interoperates.
- **MLflow Model Registry** — named models with numbered versions, movable aliases and version tags. Stages deprecated in 2.9.0.
- **MLflow Projects** — `MLproject` entry points with a declared environment. Near-zero adoption; superseded by containers plus a real orchestrator.
- **MLflow Tracing** — OpenTelemetry-shaped spans for GenAI applications, written into the same experiment as runs.
- **MLflow Prompt Registry** — prompt templates as versioned, aliased entities with the same promotion vocabulary as models.
- **MLflow AI Gateway** — one endpoint over many model providers with keys held server-side. Config keys are `endpoints` and `endpoint_type`.
- **Backend stores:** **PostgreSQL**, **MySQL**, **SQLite**
- **Artifact stores:** **S3**, **MinIO**, **GCS**, **Blob Storage**
- **Environment managers:** **virtualenv**, **conda**, **uv**
- **Serving handoff:** **KServe**, **NVIDIA Triton**, **Seldon Core**, **BentoML**, **Ray Serve**, **TorchServe**
- **Packaging and CI:** **Docker**, **GitHub Actions**, **pytest**
- **Orchestrators that run above it:** **Airflow**, **Argo Workflows**, **Kubeflow Pipelines**, **Ray**, **Metaflow**
- **Autologged training frameworks:** **scikit-learn**, **XGBoost**, **LightGBM**, **PyTorch Lightning**, **Apache Spark**, **transformers**
- **Search and tuning alongside it:** **Optuna**
- **Managed and hosted variants:** **Databricks**, **Unity Catalog**, **AWS SageMaker**, **Azure Machine Learning**, **Vertex AI**
- **Competing registries:** **SageMaker Model Registry**, **Vertex AI Model Registry**
- **Competing trackers:** **Weights & Biases**, **Neptune**, **Comet ML**, **ClearML**
- **Adjacent, not overlapping:** **DVC**, **Delta Lake**, **Feast**, **Evidently AI**, **Langfuse**, **OpenTelemetry**, **Prometheus**, **Grafana**
- **Model interchange:** **ONNX**
---

## 12. Interview Questions with Answers

**Q: Does MLflow run your training or your inference?**
**Short:** No. MLflow records runs, packages models and labels which version is live; every piece of compute belongs to you or another system.
Neither. MLflow is a recording and packaging layer: Tracking writes what happened to a database and a bucket, Models describes what came out in a loader-agnostic directory, and the Registry says which version is live. There is no scheduler, no resource manager and no GPU awareness anywhere in it. The one exception people cite is `mlflow models serve`, which does run a model — in a single Python process with no batching scheduler and no concurrency control, which is why it is a smoke test rather than a serving tier. The practical consequence is that an MLflow outage stops you logging, not training, and that MLflow sits happily under Airflow, Ray, Kubeflow or a plain Kubernetes Job without competing with any of them.

**Q: A CI job logs params and metrics successfully but fails on `log_artifact` with a 403. What is wrong?**
**Short:** The experiment uses direct artifact access, so metadata goes to the server over HTTPS while artifacts go straight to the bucket, and the client has no bucket credentials.
MLflow uses two stores reached by different credentials. Params and metrics travel over REST to the tracking server, which writes them to the database using the server's credentials. Artifacts, in direct mode, are written by the *client* straight to S3 or GCS using ambient cloud credentials the client must hold. A CI runner with `MLFLOW_TRACKING_URI` and no `AWS_*` role therefore succeeds at half the run and 403s on the first artifact, leaving the run in `RUNNING` because the exception escaped the context manager. Check the mode with `mlflow.get_experiment_by_name(...).artifact_location`: an `mlflow-artifacts:/` root is proxied and needs nothing from the client, an `s3://` root is direct. Fix by switching the experiment to proxied artifacts or granting the runner `s3:PutObject` on the prefix.

**Q: What does `mlflow.pytorch.autolog()` log from a hand-written PyTorch training loop?**
**Short:** Nothing at all. That autologger patches PyTorch Lightning's Trainer.fit only, and it fails silently, producing an empty run with no warning.
Absolutely nothing, and there is no error. `mlflow.pytorch.autolog()` monkey-patches PyTorch Lightning's `Trainer.fit`; a `for batch in loader:` loop is never intercepted. Autologging is also best-effort by design — exceptions inside the patched path are swallowed so they cannot break training — so an empty autologged run looks exactly like a successful one. Teams discover it weeks later when they try to compare experiments and find no metric history. If your loop is hand-written, log explicitly. Guard against it in CI with a post-run assertion that the run carries the metric keys you expect, and remember the second-order damage: a run with no metrics also looks like a run with nothing worth auditing.

**Q: What breaks if you log a model without a signature?**
**Short:** Nothing errors, which is the problem: an unsigned model consumes input positionally, so swapped or renamed columns produce confident wrong predictions forever.
Nothing errors — that is exactly why it is dangerous. With a signature, MLflow reorders columns to the schema, ignores extras with a warning, and raises `MlflowException` on a missing column or an uncastable type. Without one, no check happens at any point: a request whose columns arrive in a different order is consumed positionally and returns a confident wrong class. Running this on MLflow 3.15.1, the same two-column model returns `[1]` for `[[90.0, 30]]` and `[0]` for `[[30, 90.0]]` with no warning of any kind. No metric goes red for it. Always pass `signature=infer_signature(X_train, model.predict(X_train))`, and add a CI gate that fails when `Model.load(uri).signature is None`.

**Q: Why are Model Registry stages deprecated, and what replaced them?**
**Short:** Stages were a single enum per version, so one model could not be champion in one region and challenger in another; aliases plus version tags replaced them in 2.9.0.
Stages (`None`/`Staging`/`Production`/`Archived`) were deprecated in **2.9.0** in favour of mutable **aliases** plus version **tags**, and Unity Catalog registries never supported them. Four reasons. A stage is one enum, so a version cannot be champion in `eu-west` and challenger in `us-east` at once, which real rollouts need. The vocabulary is fixed at two useful words, so teams encoded `canary` and `shadow` in tags anyway and the stage stopped being the source of truth. `transition_model_version_stage(archive_existing_versions=True)` silently rewrote other versions' rows — a broadcast write dressed as a local one. And it conflated "is this approved" (a permanent property of the version, now a tag) with "is this live" (a property of the deployment, now an alias). `transition_model_version_stage` still exists in 3.15.1 behind an `@deprecated(since="2.9.0")` decorator; migrating is one tag write plus one alias move per version.

**Q: Why is SQLite a broken tracking backend for a parallel hyperparameter sweep?**
**Short:** SQLite serialises all writers behind one file lock, so concurrent trials collide with "database is locked" and the failures bias which configurations survive.
SQLite serialises writers behind a single file lock. Sixteen parallel workers all logging metrics at epoch boundaries collide constantly, and the failures surface as `sqlite3.OperationalError: database is locked` raised from inside `log_metric`. The subtle damage is worse than the noise: because the exception kills the trial rather than retrying, the study's surviving trials are a biased subset, and the "best" configuration is selected from whichever runs happened to win the lock race. The fix is a one-line `--backend-store-uri` change to PostgreSQL or MySQL. Note also that `mlflow server` with no flags now defaults to `sqlite:///mlflow.db`, so the bad default is the easy path.

**Q: Why does the `metrics` table grow without bound, and what is `latest_metrics` for?**
**Short:** `metrics` is append-only, one row per key per step, while `latest_metrics` denormalises the newest value per key so run lists and sorting do not need a subquery.
`metrics` stores one row per `(run, key, value, timestamp, step)` and is never compacted, so a run logging 5 metrics for 10,000 steps writes 50,000 rows. `latest_metrics` exists because the runs list, `search_runs` and every "sort by val_auc" query need the current value per run per key; computing that from `metrics` requires a correlated `MAX(timestamp)` subquery whose cost grows with training length rather than run count. MLflow therefore maintains a denormalised row per `(run, key)` in the same transaction. The consequence to remember is that **every metric write is two writes**, so step-level logging doubles a database load that was already the problem. Throttle to a resolution a chart can render and the table stays small; a single PostgreSQL instance then carries years of a large team's work.

**Q: What are MLflow's cardinality limits, and which ones fail loudly?**
**Short:** Keys are capped at 250 characters and raise; param values at 6000 and tag values at 8000 are silently truncated, and batches cap at 100 params or 1000 metrics.
Read from `mlflow/utils/validation.py` in 3.15.1: `MAX_ENTITY_KEY_LENGTH` 250, `MAX_PARAM_VAL_LENGTH` 6000, `MAX_TAG_VAL_LENGTH` 8000, `MAX_PARAMS_TAGS_PER_BATCH` 100, `MAX_METRICS_PER_BATCH` 1000, `MAX_ENTITIES_PER_BATCH` 1000, `MAX_BATCH_LOG_REQUEST_SIZE` 1,000,000 bytes, `MAX_REGISTERED_MODEL_ALIAS_LENGTH` 255. The split that matters: an over-long **key raises**, while an over-long param or tag **value is truncated with a warning** (controlled by `MLFLOW_TRUNCATE_LONG_VALUES`, on by default). Serialising a whole config dict into one param is the usual way to hit the second, and what lands in the database is unparseable JSON. Log a config as an artifact with `mlflow.log_dict`, not as a param.

**Q: Why can you not re-log a param with a different value, and what should you use instead?**
**Short:** Params are immutable so a run stays a trustworthy receipt of its inputs; use a tag for anything you may revise or a metric for anything numeric.
Params are immutable by design: a run is a receipt for one execution, and a mutable input would make it untrustworthy as a record. Re-logging the same key with a different value raises `MlflowException` naming the existing value; re-logging the identical value is a no-op. Use a **tag** for anything you may revise later — owner, ticket, review status — since tags overwrite freely. Use a **metric** for anything numeric you will sort or threshold on, because `metrics.val_auc > 0.9` is a numeric comparison in the search language while params are strings and only support `=`, `!=`, `LIKE`, `ILIKE` and `IN`. The trap that follows: a typo in a param name is permanent for that run, so a shared logging helper with a fixed vocabulary is worth writing.

**Q: You deleted six months of runs and storage did not shrink. Why?**
**Short:** Deleting a run only sets its lifecycle stage to deleted; nothing is reclaimed until `mlflow gc` runs, and with artifact proxying it needs MLFLOW_TRACKING_URI set.
Deleting a run in the UI or API is a soft delete — it sets `lifecycle_stage = 'deleted'` and removes no rows and no bytes. `mlflow gc` is the only thing that reclaims, and it considers **only** runs already in the deleted stage. Two further traps: with artifact proxying enabled you must export `MLFLOW_TRACKING_URI` before running gc or it cannot resolve artifact URIs and skips file deletion silently, which is why teams conclude gc "does not work"; and gc does not check whether a registered model version points at the run, so it will erase the artifacts behind something you are serving. Run `mlflow gc --older-than 30d` with the tracking URI exported, verify no live version depends on those runs, and put a bucket lifecycle policy on experiment prefixes you have declared disposable.

**Q: What is the difference between proxied and direct artifact access?**
**Short:** Proxied routes artifact bytes through the tracking server so only it needs bucket credentials; direct has each client write to object storage itself.
With `--serve-artifacts` (the **default** in 3.15.1) the experiment's artifact root is `mlflow-artifacts:/…`, clients PUT bytes to the tracking server, and the server writes to `--artifacts-destination`. Only the server holds bucket credentials, which makes IAM dramatically simpler and is the right default for CI and untrusted clients. With `--no-serve-artifacts` the root is a real `s3://` URI and each client writes directly, which removes the server as a bandwidth bottleneck at the cost of every client needing bucket permissions. A third mode, `--artifacts-only`, dedicates a server to artifact traffic so large transfers do not occupy the workers serving UI and metadata calls; 3.15 also added proxy-less transfers for large files. The critical detail: the mode is baked into the **experiment** at creation, so flipping the server flag does not migrate existing experiments.

**Q: What is the `MLmodel` file and why is it the reason MLflow succeeded?**
**Short:** It is the YAML manifest naming a model's flavours, signature and environment, so any consumer can load and score it without knowing the training framework.
`MLmodel` is a YAML manifest at the root of a logged-model directory. It names one or more **flavours** (a native one such as `sklearn` plus the universal `python_function`), the loader module and weights file, the Python version, pointers to `conda.yaml` / `python_env.yaml`, the **signature**, and in MLflow 3 the `model_id`. Its importance is that it is an interface boundary: a process that has never seen your training code reads one YAML file and knows how to load the model, what shape to feed it, and what environment to build. Every tracker in 2018 had runs and charts, several with nicer charts than MLflow; none had a portable model directory. That is why SageMaker, Vertex AI and Azure ML all ingest MLflow models directly, and why "give me your model" became a URI instead of a two-week integration.

**Q: What is the `python_function` flavour and what does it guarantee?**
**Short:** It is the universal flavour giving every logged model a generic predict(data, params) callable, which is what serving, Spark UDFs and Docker builds all use.
`python_function` (pyfunc) is the framework-agnostic flavour every logged model carries. It guarantees an object exposing `predict(data, params=None)`, where `data` is a DataFrame, numpy array, dict of arrays, list or scalar shaped by the signature. That single contract is what `mlflow models serve`, `mlflow.pyfunc.spark_udf`, `mlflow models build-docker`, KServe's MLflow runtime and every deployment plugin call — none of them know or care that the underlying model is XGBoost or a transformer. Native flavours coexist with it: ask for `mlflow.sklearn.load_model` when you want the real estimator back to inspect or retrain. Custom pyfunc models (`mlflow.pyfunc.PythonModel`) are the escape hatch, and the highest-value refactor available in MLflow, because folding preprocessing into the model removes training/serving skew by construction.

**Q: Exactly how does MLflow signature enforcement behave on bad input?**
**Short:** Reordered columns are silently corrected, extras are dropped with a warning, and a missing column or an uncastable type raises MlflowException at scoring time.
Four behaviours, verified on 3.15.1. Columns in the wrong **order** are silently reordered to match the schema — this is the main win, and the thing an unsigned model cannot do. An **extra** column logs a warning and is ignored. A **missing** required column raises `MlflowException: Model is missing inputs ['age']`. An **uncastable** type raises `MlflowException: Failed to convert column …`, while a safe widening such as int into double converts silently. Note it is a *type* contract, not a value-range contract: a negative age passes enforcement, so range checks remain a separate data test. And a common production surprise — an inferred integer column cannot hold nulls, so a request with a missing value arrives as a float and fails. Infer the signature from data that contains the nulls you expect, or declare those columns as doubles.

**Q: What does passing `input_example` to `log_model` actually trigger?**
**Short:** It infers a signature if you did not supply one, writes the example and a ready-made /invocations body, and runs a validation prediction against the saved model.
Three things, none of them cosmetic. MLflow infers a signature from it if you did not pass one. It writes `input_example.json` plus `serving_input_example.json` — the latter is a ready-made `/invocations` request body, which makes `curl -d @serving_input_example.json` the fastest possible smoke test. And it runs a **prediction against the freshly saved model**, so a broken serialisation or a missing dependency fails at log time rather than at deploy time. It also surfaces the integer-column warning about nulls, which is genuinely useful. The only cost is a few seconds and a couple of small files, so pass it always.

**Q: What does MLflow's dependency capture pin, and what does it miss?**
**Short:** It pins the Python distributions it saw imported at log time; it cannot see CUDA, system libraries, the base image, or anything installed but never imported.
At log time MLflow walks the imported modules, maps them to installed distributions, and writes `requirements.txt`, `python_env.yaml` and `conda.yaml`. What it cannot see is everything outside the interpreter: the CUDA driver and toolkit, cuDNN and NCCL, glibc and OpenSSL, the BLAS your numpy linked against, the base image and CPU instruction set the wheels were built for, environment variables that changed behaviour, and any package installed but never imported during the run. It also records the exact Python **patch** version, which fails to solve later if that patch is yanked. The honest framing is that MLflow pins what it *saw*, not what you *meant* — treat the captured environment as a strong hint and the container image digest, pinned alongside the model version, as the actual reproducibility guarantee. Override inference with `pip_requirements=` or `extra_pip_requirements=` when you know better.

**Q: When is `--env-manager local` correct and when is it a landmine?**
**Short:** It is correct only inside a container you built from that model's own requirements; anywhere else it silently scores in the caller's environment.
`--env-manager local` skips environment reconstruction and loads the model into whatever is already installed. That is exactly right inside a container you built from the model's own `requirements.txt`, where rebuilding would be wasted work. It is a landmine everywhere else, and the failure is silent: a model serialised under NumPy 1.x loaded under NumPy 2.x will often score, returning numerically shifted predictions rather than raising, which then invalidates any threshold tuned on the old behaviour. It becomes tempting precisely when it is most dangerous — someone reaches for it under deadline because `conda.yaml` will not solve. The alternatives are `virtualenv` (the default for `models serve`), `uv` (much faster), and validating with `mlflow.models.predict()` `[2.10+]`, which scores inside the declared environment from your own process.

**Q: What are the model URI forms and which one belongs in serving code?**
**Short:** Serving code should use `models:/<name>@<alias>`, because resolving through a movable alias is what makes promotion and rollback a single metadata call.
Four forms. `runs:/<run_id>/<name>` addresses an artifact inside one run — right for registering and for debugging one run. `models:/<name>/<version>` is an exact immutable version — right for reproducing an incident or pinning a batch job. `models:/<name>@<alias>` resolves through a movable pointer — this is the one serving code should use, because promotion becomes `set_registered_model_alias` and rollback is the same call aimed at the previous version, with no redeploy and no bytes moved. `models:/<model_id>` `[3.0+]` addresses a LoggedModel directly, which is how you evaluate a candidate before it earns a registered name. `load_model` also accepts a raw `s3://` path; it works and throws away the entire audit trail, because the URI *is* the lineage.

**Q: What is the difference between an alias and a tag on a model version?**
**Short:** A tag is an immutable-in-spirit fact about the version such as validation status; an alias is a movable pointer saying which version is currently live.
They answer different questions and that is the whole design. A **tag** records a property of the version — `validation_status: passed`, `eval_dataset: holdout_2026_07`, `approved_by: risk-team` — which was true when it was written and stays attached to that version forever. An **alias** records a property of the *deployment* — `@champion`, `@challenger`, `@canary-eu` — and moves. One version can hold several aliases at once, which is what lets a model be champion in one region and challenger in another; a single stage enum could not. Operationally: CI writes tags when gates pass, a human or a deploy job moves aliases, and serving resolves only aliases. That split is what makes rollback one atomic API call.

**Q: Why is the fluent API wrong in concurrent code?**
**Short:** The fluent API keeps the active run on a thread-local stack, so worker threads see no parent and `nested=True` silently produces flat top-level runs.
`mlflow.start_run` maintains an active-run stack that is **thread-local**. A worker thread sees an empty stack, so `mlflow.start_run(nested=True)` inside it creates a flat top-level run instead of a child, the parent groups nothing, and no error is raised. This bites Optuna's thread-based `n_jobs`, any `ThreadPoolExecutor`, and async frameworks that hop event-loop threads; it is also why `mlflow.spark.autolog()` requires `PYSPARK_PIN_THREAD=false` on PySpark 3.2 and later. Two fixes: pass the parent explicitly with `mlflow.start_run(nested=True, parent_run_id=parent_id)`, or use `MlflowClient`, which takes an explicit `run_id` on every call and holds no ambient state. Rule of thumb — fluent in a notebook and a single-threaded script, client in a library, a service, or anything parallel.

**Q: What is a `LoggedModel` in MLflow 3 and what problem does it solve?**
**Short:** It makes a model a first-class entity with its own id, metrics and artifact location, so it can exist outside a run and accumulate metrics after training.
In MLflow 2 a model was a path inside a run's artifacts, so it had no identity of its own and could carry no metrics. `[3.0+]` it is a row in `logged_models` with an id (`m-…`), its own `logged_model_metrics`, `logged_model_params` and `logged_model_tags`, and an *optional* link to a run. Three things follow: `log_model` works outside a run, so registering an externally produced artifact no longer needs a fake run; evaluation metrics produced weeks later attach to the model rather than smearing onto the training run; and one run can produce several independently addressable models. The cost is that model artifacts moved from `experiments/<exp>/<run_id>/artifacts/<name>/` to `experiments/<exp>/models/<model_id>/artifacts/`, which is what breaks deploy scripts built on `list_artifacts()`.

**Q: How do you log metrics efficiently, and what are the batch limits?**
**Short:** Buffer points and send them with `log_batch`, capped at 1000 metrics or 100 params and tags per request and 1 MB per request; throttle first, then batch.
Every fluent call is a synchronous HTTP round trip inside your training loop. `MlflowClient.log_batch(run_id, metrics=[Metric(...)], params=[...], tags=[...])` ships many points in one request, capped by `MAX_METRICS_PER_BATCH` 1000, `MAX_PARAMS_TAGS_PER_BATCH` 100, `MAX_ENTITIES_PER_BATCH` 1000 and `MAX_BATCH_LOG_REQUEST_SIZE` 1 MB. Concretely, 10,000 steps of 5 metrics at 5 ms RTT is 250 s of blocked training with per-metric calls, 50 s with a per-step dict, and 0.25 s with 1000-point batches. But note what batching does *not* fix: the database still receives 50,000 rows, doubled by `latest_metrics`. **Throttling is what saves the database; batching is what saves the wall clock.** Do both — log every Nth step plus an epoch summary, and batch what survives.

**Q: What does `mlflow models serve` give you, and why is it not a production server?**
**Short:** It is one pyfunc in one Python process exposing /invocations, with no batching scheduler, no model repository and no GPU concurrency control.
It starts a FastAPI process wrapping a single pyfunc, exposing `/ping`, `/health`, `/version` and `/invocations`. The body carries one of `dataframe_split`, `dataframe_records`, `instances` or `inputs`, optionally with `params`, and the response is `{"predictions": [...]}`. What it lacks is everything a serving tier needs: one model per process with no repository or multi-model memory management, no request-batching scheduler, no GPU concurrency control or instance groups, no warmup contract, no graceful version switching, and no first-class latency metrics. Use it to answer "does this artifact load and score" — `curl -d @serving_input_example.json` against it is a complete smoke test — and hand production traffic to KServe or Triton.

**Q: How do you hand an MLflow model to KServe or to Triton?**
**Short:** KServe consumes a `models:/name@alias` storageUri directly; Triton needs a CI export step to ONNX or TorchScript plus a config.pbtxt in its model repository.
For **KServe** the handoff is a URI: an `InferenceService` with `modelFormat: {name: mlflow}` and `storageUri: "models:/fraud@champion"`, with `MLFLOW_TRACKING_URI` in the pod environment. KServe pulls the directory and runs the pyfunc, usually under MLServer — which is why `mlflow models build-docker --enable-mlserver` produces an image that drops straight into KServe or Seldon. For **Triton** the handoff is an export step, because Triton wants a model repository of native backends, not a pyfunc: CI loads `models:/fraud@champion`, exports to ONNX or TorchScript, writes `config.pbtxt`, and syncs the directory. MLflow's contribution there is provenance — stamp the model version and run id as labels on the repository directory so an incident traces back. The batching, instance groups and ensemble mechanics are Triton's concern, not MLflow's.

**Q: What changed in `mlflow.evaluate` between MLflow 2 and 3?**
**Short:** `baseline_model` was removed in favour of validate_evaluation_results, `higher_is_better` became `greater_is_better`, `custom_metrics` became `extra_metrics`, and the SHAP explainer is no longer logged by default.
Four breaking changes plus one addition. `baseline_model=` was **removed**; comparing a candidate against a champion is now two `mlflow.evaluate` calls fed to `mlflow.validate_evaluation_results(candidate_result=…, baseline_result=…, validation_thresholds=…)`. The metric-threshold keyword `higher_is_better` was renamed **`greater_is_better`**. `custom_metrics=` was removed in favour of `extra_metrics=`. The SHAP explainer is **no longer logged by default** — opt in through `evaluator_config`, which matters because it was quietly the most expensive part of an evaluation. And GenAI work moved to `mlflow.genai.evaluate` with a `scorers=` list; 3.15 adds multimodal LLM judges. Judges are model calls, so pin the judge model and version the rubric or your evaluation drifts under you.

**Q: What is MLflow Tracing and how does it relate to OpenTelemetry?**
**Short:** It captures OpenTelemetry-shaped spans for GenAI apps into the same experiment as your runs, and can export to an OTLP collector rather than replacing your tracer.
Tracing records one trace per request with spans for retrieval, tool calls and model calls, carrying inputs, outputs, latency, token counts and errors as attributes. You get it from `@mlflow.trace` decorators and from flavour autologgers (`mlflow.openai.autolog()`, `mlflow.langchain.autolog()`). The data model is OpenTelemetry-shaped and MLflow can export to an OTLP collector, so it complements rather than replaces existing tracing. What it adds over a generic tracer is that traces live in the same experiment as your runs and can be turned into an evaluation dataset — capture production traces, label them, score a candidate prompt against them. Two operational notes: export is asynchronous, so a short-lived process should call `mlflow.flush_trace_async_logging()`, and trace volume is *production request* volume, so sample before enabling it on a live service or `spans` will dwarf `metrics`.

**Q: What is the MLflow Prompt Registry for?**
**Short:** It makes a prompt template a versioned, aliased entity with the same promotion vocabulary as a model, so a prompt change is reviewable and revertible.
`mlflow.genai.register_prompt(name=…, template=…, commit_message=…)` creates a versioned prompt; `set_prompt_alias` and `load_prompt("prompts:/name@production")` give it the same alias-based promotion and instant rollback as a model version. The value is not storage — it is that a prompt edit stops being an untracked string change in a Python file and becomes an attributable, reviewable, revertible version, and that a trace records which prompt version produced it. That closes the most common GenAI incident loop: quality dropped, nobody can say which prompt was live at the time. Treat prompts with the same discipline as models — gate promotion on an evaluation run, not on someone's judgement in a pull request.

**Q: What is the MLflow AI Gateway, and what happened to the deployment server?**
**Short:** The gateway is one endpoint over many model providers with keys held server-side; the standalone MLflow 2 deployment server app and its start-server CLI were removed in MLflow 3.
The AI Gateway is configured with an `endpoints` list, each naming a provider, a model and an `endpoint_type` such as `llm/v1/chat` or `llm/v1/embeddings`, with API keys resolved server-side so application code never holds them. It buys provider-agnostic request shapes, central key rotation and one place to attach rate limits and cost accounting. It is served by the tracking server: the standalone MLflow 2 deployment-server app and `mlflow deployments start-server` were **removed in MLflow 3**, and the config keys `routes` and `route_type` became `endpoints` and `endpoint_type`. What it does not give you is the routing intelligence of a dedicated LLM gateway — no semantic cache, no fallback chains, no per-tenant budgets — so choose it when you already run MLflow and want to stop scattering keys, not when gateway behaviour is the product.

**Q: Are MLflow Projects worth adopting in 2026?**
**Short:** No. Projects solved reproducible execution before containers were easy; today an image plus a real orchestrator beats it on every axis, and its successor Recipes was removed in MLflow 3.
No, and the evidence is that MLflow moved on too. An `MLproject` declares entry points, parameters and an environment, and `mlflow run` shells out to your command with environment variables set. That is the entire feature. It solved a real 2018 problem — containerising a Python ML job was hard — but today the environment problem is an image and the execution problem is Airflow, Argo, Kubeflow Pipelines, Ray Jobs or a Kubernetes Job, each of which has retries, scheduling, resource requests, secrets and observability that Projects never had. **Recipes**, the successor abstraction, was **removed in MLflow 3**. What survives is the useful half: set `MLFLOW_RUN_ID` and `MLFLOW_EXPERIMENT_ID` in your job's environment so the job logs into a run the orchestrator created.

**Q: How do you do authentication and multi-tenancy on open-source MLflow?**
**Short:** You largely do not — the built-in `--app-name basic-auth` plugin is experimental HTTP Basic auth with no SSO, so terminate identity in a proxy in front of the server.
Say the thinness out loud in an interview. The built-in plugin (`mlflow server --app-name basic-auth`) is HTTP Basic authentication with per-experiment and per-registered-model grants of `READ`, `EDIT`, `MANAGE` or `NO_PERMISSIONS`, and it is still marked **experimental**: no SSO, no OIDC, no groups, no token expiry, no audit log. Nobody should make it the access control for a shared production registry. The workable pattern is to terminate identity in front — an OAuth2 proxy, an OIDC ingress, or a service mesh — keep the server off the public internet, set `--allowed-hosts` and `--cors-allowed-origins` explicitly, and back everything with bucket-level IAM so a leaked tracking token cannot read another team's artifacts. For tenancy the honest options are one server per team, an experiment-naming convention plus bucket prefixes, or `--enable-workspaces` for logical isolation — which is not a security boundary. On Databricks, Unity Catalog supplies the real thing.

**Q: How does dataset lineage work in MLflow, and why is it better than logging a path as a param?**
**Short:** `mlflow.data` records a content-derived digest alongside the source, so two runs claiming the same S3 path with different bytes are distinguishable.
`mlflow.data.from_pandas(df, source="s3://…", name="fraud_train", targets="is_fraud")` followed by `mlflow.log_input(ds, context="training")` writes structured rows into `datasets` and `inputs`, including a **content-derived digest**. That is the difference from `mlflow.log_param("data_path", …)`: an S3 path is mutable, so two runs naming the same path may have trained on different bytes and the param cannot tell you. The digest can. `from_spark`, `from_numpy`, `from_delta` and `from_huggingface_dataset` cover the usual sources, and a Delta source records the table version, which is a genuinely immutable reference. Combine it with the git SHA on the run and you have the two axes people most often forget.

**Q: What was removed in MLflow 3 that will break an existing codebase?**
**Short:** Recipes, the deployment server and its start-server CLI, the fastai, mleap, diviner and gluon flavours, `run_uuid`, and several log_model keyword arguments.
The list worth memorising: **Recipes** (and the earlier MLflow Pipelines) is gone; the **deployment server app** and `mlflow deployments start-server` are gone, with gateway `routes`/`route_type` renamed to `endpoints`/`endpoint_type`; the **fastai, mleap, diviner and gluon flavours** are gone, so a model logged under any of them cannot be loaded by a 3.x client — re-log it before upgrading. Keyword removals: `example_no_conversion`, `code_path`, `requirements_file`, `inference_config`, `run_uuid`, and TF autolog's `every_n_iter`. `mlflow.evaluate` lost `baseline_model` and `custom_metrics` and renamed `higher_is_better`. And `log_model(artifact_path=)` became `name=`; `artifact_path` still works in 3.15.1 with a deprecation warning, but passing both raises.

**Q: Why does `mlflow server` with no arguments now use SQLite, and why does a `file:./mlruns` tracking URI raise?**
**Short:** The filesystem backend is in maintenance mode as of 3.15 and raises unless MLFLOW_ALLOW_FILE_STORE is set; the new default backend is `sqlite:///mlflow.db`.
The local file store — YAML and directories under `./mlruns` — cannot express the entity model MLflow 3 needs, has no concurrency story, and cannot be shared. As of 3.15 it is in **maintenance mode and raises on use**, with a message pointing at `mlflow migrate-filestore`, which converts existing data losslessly to SQLite. `MLFLOW_ALLOW_FILE_STORE=true` opts out if you genuinely need it. Correspondingly a bare `mlflow server` now defaults to `--backend-store-uri sqlite:///mlflow.db`, falling back to `./mlruns` only when one already exists. Treat file-store data as legacy to migrate, not as a starting point — and note that SQLite is still single-writer, so it remains a laptop backend, not a team one.

**Q: How would you make an MLflow deployment auditable enough for a regulated model?**
**Short:** Pin all four axes on the run, force signatures and dataset digests, keep aliases as the only promotion mechanism, and put the audit trail outside MLflow's own auth.
Pin the four axes on every run: git SHA as a tag, dataset digest via `mlflow.log_input`, hyperparameters as params, and the container image digest as a param — MLflow's captured `requirements.txt` is a hint, not the environment. Make the model self-describing: enforce a non-null signature and an `input_example` in CI, and fold preprocessing into a custom pyfunc so what was validated is what runs. Make promotion the only path: CI writes `validation_status` tags when gates pass, a separate approval job moves `@champion`, and serving resolves aliases only. Then acknowledge the gap — open-source MLflow's basic-auth plugin is experimental and has no audit log, so who moved which alias when must be captured by the deploy system in front of it, or by Unity Catalog if you are on Databricks. Finally, never `mlflow gc` a run that backs a live version; gc does not check.

---

## 13. Best Practices

- **Run PostgreSQL and object storage from day two.** SQLite is a laptop backend and the new default; a second concurrent writer is all it takes to break it, and migrating a year of runs later is a maintenance window you did not need.
- **Default to proxied artifacts** (`--serve-artifacts` with `--artifacts-destination s3://…`). It keeps bucket credentials on the server, which removes the single most common MLflow incident. Switch to direct only when artifact bandwidth actually saturates the server, and remember the mode is fixed per experiment at creation.
- **Always log a signature and an `input_example`.** Gate it in CI: fail the build if `Model.load(uri).signature is None`. This is the only enforcement MLflow will ever do on your behalf, and an unsigned model fails silently and permanently.
- **Throttle metric writes to a few hundred points per curve, then batch them.** Throttling protects the database, batching protects wall-clock training time. Send genuine high-frequency telemetry to a time-series store instead.
- **Use the client API in anything concurrent.** The fluent active-run stack is thread-local; a library, a service, or a thread-parallel sweep should pass `run_id` explicitly rather than rely on ambient state.
- **Never trust autolog without verifying it reached the bank.** Assert after the first run that the metric keys you expect exist. `mlflow.pytorch.autolog()` on a hand-written loop logs nothing and raises nothing.
- **Fold preprocessing into a custom pyfunc.** It is the cheapest structural cure for training/serving skew, and it makes the signature describe the real input rather than an internal feature vector.
- **Serving resolves aliases, never version numbers.** `models:/name@champion` is what makes rollback one API call. Reserve pinned `models:/name/7` URIs for incident reproduction and batch jobs that must not move.
- **Record why, not just what.** A `validation_status` tag with the eval dataset name turns the registry from a list of versions into an audit trail. Aliases say what is live; tags say what earned it.
- **Pin the container image digest as a run param.** MLflow's captured environment cannot see CUDA, system libraries or the base image, so the digest is the actual reproducibility guarantee and the captured `requirements.txt` is a strong hint.
- **Put a real identity proxy in front of the server.** The built-in basic-auth plugin is experimental and has no SSO or audit log. Terminate OIDC at the ingress, set `--allowed-hosts` and `--cors-allowed-origins`, and back it with bucket-level IAM.
- **Delete deliberately, with a bucket lifecycle policy behind it.** Deleting a run reclaims nothing; `mlflow gc --older-than 30d` with `MLFLOW_TRACKING_URI` exported is the only thing that does, and it will not check whether a live model version depends on the run.
- **Run `mlflow db upgrade` as a deliberate step before every version bump**, and read the release notes for removals — MLflow 3 deleted four flavours, Recipes, the deployment server and several keyword arguments.
- **Let MLflow hand off.** Package with `build-docker --enable-mlserver` or export for Triton; do not put `mlflow models serve` in front of traffic because it happened to work in staging.

---

## 14. Case Study

**Scenario.** A fraud team at a payments company runs about 400 training runs a week across six
models, on a mix of Airflow-scheduled retrains and ad-hoc notebook work. The current state:
an MLflow instance someone started 18 months ago on SQLite with a local artifact directory,
mounted on one EC2 host. Symptoms, in the order they were reported: the runs list takes 25
seconds to load, nightly Airflow retrains lose about one run in six to `database is locked`, a
model was rolled back last month by editing a Kubernetes manifest and redeploying (23 minutes
of degraded approvals), and nobody can say which dataset trained the version currently in
production. The team is also blocked on an MLflow 3 upgrade because their deploy script broke
in staging.

**Target.** Concurrent-safe tracking; rollback in under a minute without a redeploy; every
production version traceable to code, data and environment; the MLflow 3 upgrade unblocked.

### Step 1 — Move the state off the box

```bash
# The file store is in maintenance mode as of 3.15 and raises without an opt-out.
export MLFLOW_ALLOW_FILE_STORE=true
mlflow migrate-filestore --backend-store-uri sqlite:///staging.db   # then dump/restore into PG

mlflow server \
  --backend-store-uri postgresql+psycopg2://mlflow@fraud-mlflow.rds:5432/mlflow \
  --artifacts-destination s3://fraud-mlflow-artifacts/prod \
  --serve-artifacts \
  --host 0.0.0.0 --port 5000 --workers 8 \
  --allowed-hosts mlflow.payments.internal \
  --expose-prometheus /var/lib/mlflow/prom
```

Two decisions here carry the incident list. PostgreSQL removes the lock contention outright.
`--serve-artifacts` moves bucket credentials onto the server, so the Airflow workers and the CI
runner stop needing S3 permissions — and, more importantly, stop being able to half-write a run.

### Step 2 — Fix the logging shape

The runs list was slow because of the `metrics` table, not the run count. Measured first:

```sql
SELECT run_uuid, COUNT(*) FROM metrics GROUP BY run_uuid ORDER BY 2 DESC LIMIT 5;
--  9,600,000 rows across 14 runs. Two experiments were logging 8 metrics per step.
```

```python
# BEFORE: 8 metrics x ~120,000 steps -> 960,000 rows per run, doubled by latest_metrics.
for step, batch in enumerate(loader):
    for k, v in step_metrics(batch).items():
        mlflow.log_metric(k, v, step=step)

# AFTER: ~200 points per curve, batched. Same charts, 0.2% of the rows.
buffer: list[tuple[str, float, int]] = []
for step, batch in enumerate(loader):
    m = step_metrics(batch)
    if step % 500 == 0:
        buffer += [(k, float(v), step) for k, v in m.items()]
    if len(buffer) >= 1000:
        client.log_batch(run_id, metrics=[Metric(k, v, now_ms(), s) for k, v, s in buffer])
        buffer.clear()
```

Runs list dropped from 25 s to under 1 s once the old rows were purged in a maintenance window.

### Step 3 — Make every model self-describing

```python
sig = infer_signature(X_train, pipeline.predict(X_train))
ds = mlflow.data.from_pandas(train_df, source=TRAIN_URI, name="fraud_train", targets="is_fraud")

with mlflow.start_run(run_name=f"xgb_{cfg['max_depth']}") as run:
    mlflow.log_input(ds, context="training")          # dataset digest, not just a path
    mlflow.log_params(cfg)
    mlflow.set_tag("git_sha", git_sha())
    mlflow.log_param("image_digest", os.environ["IMAGE_DIGEST"])   # the real env pin
    mlflow.log_metrics(eval_metrics, step=cfg["epochs"])

    info = mlflow.xgboost.log_model(
        pipeline, name="model",                        # `name=`, not `artifact_path=`
        signature=sig, input_example=X_train.head(3),
    )

# CI gate: refuse to register a model with no contract.
assert mlflow.models.Model.load(info.model_uri).signature is not None, "unsigned model"
mv = mlflow.register_model(info.model_uri, "fraud")
client.set_model_version_tag("fraud", mv.version, "validation_status", "passed")
client.set_model_version_tag("fraud", mv.version, "eval_dataset", ds.name)
client.set_registered_model_alias("fraud", "challenger", mv.version)
```

Note the four pins on one run — git SHA, dataset digest, hyperparameters, image digest — which
is the whole answer to "which data trained production".

### Step 4 — Make rollback a metadata call

The 23-minute rollback happened because the serving manifest named a version. Replacing the
manifest's `storageUri` with an alias moves the decision into the registry:

```yaml
apiVersion: serving.kserve.io/v1beta1
kind: InferenceService
metadata:
  name: fraud
spec:
  predictor:
    model:
      modelFormat: {name: mlflow}
      storageUri: "models:/fraud@champion"
```

```python
client.set_registered_model_alias("fraud", "champion", 41)   # promote
client.set_registered_model_alias("fraud", "champion", 40)   # roll back, same call
```

Promotion is one row update; a rolling restart of the predictor re-resolves. Measured on their
cluster: 38 seconds from the alias move to full traffic on the previous version, against 23
minutes for a manifest edit through the deploy pipeline. Version 40 was never deleted, so
nothing had to be rebuilt.

### Step 5 — Unblock the MLflow 3 upgrade

The staging break was the deploy script:

```python
# BROKEN under MLflow 3: models are LoggedModel entities, not run artifacts, and live
# under experiments/<exp>/models/<model_id>/artifacts/ -- this returns nothing.
entry = next(a for a in client.list_artifacts(run_id) if a.path == "model")
uri = f"runs:/{run_id}/{entry.path}"

# FIX: use the URI log_model handed back, or search the entities directly.
uri = info.model_uri                                   # "models:/m-0a24c301…"
# or, recovering it later:
lm = mlflow.search_logged_models(experiment_ids=[exp_id],
                                 filter_string=f"source_run_id='{run_id}'")[0]
uri = f"models:/{lm.model_id}"
```

Alongside it, `log_model(artifact_path="model")` was changed to `name="model"` (the old keyword
still works with a deprecation warning, but not beside `name=`), and a sweep confirmed no model
used a removed flavour — the team had one legacy `fastai` model, which was re-logged as a
custom pyfunc before the upgrade.

### Outcome

| Metric | Before | After |
|---|---|---|
| Runs-list load time | 25 s | under 1 s |
| Nightly retrains lost to lock contention | ~1 in 6 | 0 |
| Rollback time | 23 min (manifest edit and redeploy) | 38 s (alias move) |
| Production versions with a dataset digest | 0 | all |
| Production versions with a signature | 3 of 11 | all, enforced in CI |
| `metrics` table size | 41 GB | 0.4 GB after purge |
| MLflow version | 2.x, upgrade blocked | 3.15.1 |

**What the team got wrong first.** They initially tried to fix the slow runs list by adding
database indexes, which helped marginally and hid the real problem for a month. The table was
not slow because it was unindexed; it was slow because a logging loop was writing a million
rows per run and every one of those writes also touched `latest_metrics`. The lesson generalises:
in MLflow, almost every performance problem is a *logging resolution* problem wearing a database
costume.
