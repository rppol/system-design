# Queues & streaming — technology bank

<!-- tech-bank tier: data-movement -->

The 135 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Queues & streaming** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### ActiveMQ Artemis
**Short:** Multi-protocol JMS broker (AMQP, MQTT, STOMP, OpenWire), usable as the external STOMP relay for WebSockets.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, apis-frameworks/rpc-graphql-and-streaming @2

### aio-pika
**Short:** Asyncio AMQP client for RabbitMQ with connection/channel pooling and per-message consumer tasks.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/message-broker @1, data-access/drivers-and-connection-pooling @2, runtime-systems/concurrency-and-async @3

### aiokafka
**Short:** asyncio-native Kafka producer and consumer client for Python event-driven services.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/event-streaming-and-processing @1, runtime-systems/concurrency-and-async @2, data-movement/message-broker @3

### aiormq
**Short:** Low-level asyncio AMQP 0.9.1 client for RabbitMQ; the transport underneath aio-pika.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/message-broker @1, runtime-systems/concurrency-and-async @2, data-access/drivers-and-connection-pooling @3

### Airbyte
**Short:** Open-source ELT platform with hundreds of connectors syncing SaaS and database sources into a warehouse or lake.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/warehouse-and-olap @3, data-movement/data-quality-and-lineage @3

### Airflow
**Short:** DAG scheduler for data and training pipelines: Python-defined tasks, dependency management, retries and backfills.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @2, data-movement/task-queue-and-jobs @3

You write a DAG in Python: tasks are operator instances, dependencies are `>>` edges, and the scheduler walks the graph, submitting ready tasks to an executor (Celery, Kubernetes, or local) while a metadata database — usually PostgreSQL — holds every run's state. Retries, SLAs, sensors that wait on an external condition, and backfill over a historical date range come with it, which is why it became the default for nightly ETL and training pipelines.

The rule people learn late is that Airflow is an orchestrator, not a compute engine: tasks should trigger Spark, dbt, or a training job and let data move through storage, because passing real data between tasks via XCom pushes it through the metadata DB. Reach for it for scheduled batch work with real dependency structure; it is a poor fit for streaming, sub-minute latency, or a workflow that branches differently for every event.

### Amazon MSK
**Short:** AWS-managed Apache Kafka: provisioned or serverless brokers with tiered storage, IAM auth and less tuning surface.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2, platform-delivery/cloud-platform-and-cost @3

### Amazon MWAA
**Short:** AWS-managed Apache Airflow: hosted scheduler, workers and web UI for DAG orchestration without cluster ops.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

### Amazon SQS
**Short:** Fully managed AWS queue with at-least-once standard and exactly-once FIFO modes, visibility timeouts and DLQs.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @2, platform-delivery/cloud-platform-and-cost @3

### Anthropic Message Batches API
**Short:** Anthropic endpoint for submitting many messages as one asynchronous batch job at reduced cost.
**Kind:** api
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, llm-apps/llm-gateway-and-routing @3, platform-delivery/cloud-platform-and-cost @3

### Apache Beam
**Short:** Unified batch and streaming pipeline model whose portable jobs run on Flink, Spark or Dataflow.
**Kind:** tech
**Lang:** java, python, go
**Roles:** data-movement/batch-and-distributed-compute @1, data-movement/event-streaming-and-processing @2

### Apache Camel
**Short:** Java integration framework implementing Enterprise Integration Patterns as a route DSL, plus saga orchestration.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/message-broker @3, apis-frameworks/design-patterns-and-principles @3

A route reads as `from(endpoint) ... to(endpoint)`, and the enterprise integration vocabulary - splitter, aggregator, content-based router, dead letter channel, idempotent consumer - is available as DSL operators instead of hand-written glue. Hundreds of components cover the transports you would otherwise integrate one at a time: JMS, Kafka, HTTP, SFTP, files, object storage, databases.

Reach for it when a service's actual job is moving and transforming messages between heterogeneous systems, especially where the mediation logic is what changes most often. For a plain HTTP service with one database it is a heavy abstraction that buys nothing, and the route DSL is a real thing to learn before a team can debug it under load.

### Apache Camel Saga EIP
**Short:** Camel route DSL element implementing the saga pattern: compensating actions for a multi-service transaction.
**Kind:** api
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2

### Apache Flink
**Short:** Stateful stream processor with exactly-once semantics, event-time windows and CEP over unbounded streams.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/batch-and-distributed-compute @2, ml-lifecycle/ml-platform-and-pipelines @3

Flink runs a dataflow graph continuously over unbounded input, keeping large keyed state in an embedded RocksDB backend and checkpointing it to durable storage, which is how it restores exactly-once semantics after a failure. Event-time processing with watermarks lets it window correctly even when records arrive late or out of order, and its CEP library matches temporal patterns across a stream.

The two jobs it is reached for most are real-time feature computation and consuming CDC streams into downstream stores. Reach for it when state is large, correctness under out-of-order data matters, and latency must stay in the sub-second range; if all your data already lives in Kafka and the job is small, Kafka Streams is far less to operate.

### Apache Kafka
**Short:** Distributed partitioned commit log used as a durable message broker and the backbone of event-streaming pipelines.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @1, data-access/transactions-and-consistency @3

A topic is split into partitions, each an append-only log on disk replicated across brokers, and consumers track their own offset — so reading is a pointer move, and a new consumer group can replay the whole retained history without the producer knowing. Ordering is guaranteed per partition, never per topic, which is why the partition key is the central design decision: anything that must stay ordered together has to hash to the same partition. Cluster metadata is managed by a quorum of KRaft controllers rather than a separate ZooKeeper ensemble.

Reach for it when many independent consumers need the same durable stream, or when replay matters — event sourcing, projections, saga event distribution. A work queue with one consumer and no replay requirement is better served by a simpler broker.

### Apache Spark
**Short:** Distributed compute engine for large-scale batch and streaming jobs; the usual home of offline feature computation.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, data-movement/event-streaming-and-processing @3, ml-lifecycle/ml-platform-and-pipelines @3, data-stores/warehouse-and-olap @3

A job is a DAG of stages: the driver plans it, tasks run in parallel over partitions on executors, and shuffles redistribute rows between stages. DataFrame code goes through the Catalyst optimizer and Tungsten code generation, so declarative queries usually beat hand-written RDD logic, and Structured Streaming reuses the same API for micro-batch pipelines.

In an ML system it is where offline features get computed and training sets get materialized — joins and aggregations over history far too large for one machine. The price is real cluster overhead and shuffle-heavy jobs that stall on skewed keys, so data that fits on one box is better served by pandas, Polars or DuckDB.

### Apache Spark GraphX
**Short:** Spark's distributed graph-processing API with a Pregel model for iterative algorithms like PageRank over huge graphs.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, applied-ml/recommenders-and-graph-ml @2, data-stores/graph-db @3

### Argo Workflows
**Short:** Kubernetes-native DAG workflow engine where each step is a pod; used for CI, ML and data pipelines.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/kubernetes-and-orchestration @2, platform-delivery/ci-cd-and-release @2

A workflow is a Kubernetes custom resource, so you submit a pipeline with `kubectl` and the cluster schedules it like any other workload — each step runs as its own pod with its own image, resource requests and node selector, which is what makes heterogeneous and GPU steps natural. Steps are wired either as an ordered list or as a `dag` with explicit dependencies, and data moves between them as artifacts staged through an object-store repository rather than a shared filesystem.

It fits teams already operating Kubernetes who want per-step isolation. If your steps are lightweight Python tasks that all share one runtime, a scheduler like Airflow is far less machinery for the same DAG.

### ARQ
**Short:** Asyncio job queue for Python backed only by Redis; runs background tasks on the event loop with retries and cron.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1, runtime-systems/concurrency-and-async @3

### Astronomer Astro
**Short:** Commercial managed Apache Airflow platform with hosted schedulers, deploy tooling and observability for DAGs.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @3, platform-delivery/cloud-platform-and-cost @3

### AWS DMS
**Short:** AWS Database Migration Service: managed one-off migration and ongoing CDC replication between databases.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/schema-and-migration @2, data-access/replication-ha-and-backup @3

DMS provisions a replication instance that connects a source endpoint to a target endpoint and moves rows between them. A task normally runs two phases: a full load that copies existing data, then ongoing change data capture that tails the source's write-ahead log, binlog or redo log, so the target keeps converging and cutover means pointing the application at the new database during a short window rather than taking a long outage.

Source and target need not be the same engine — Oracle to PostgreSQL is the classic case, paired with Schema Conversion Tool for the DDL — and a target can also be Kinesis, Kafka or S3, which is how it gets used as a plain CDC tap for outbox relays and analytics feeds. It moves data, it does not convert schemas or logic, and large object columns and exotic types are where migrations usually get stuck.
### AWS EMR
**Short:** Managed Hadoop/Spark clusters on EC2, commonly run on Spot instances for cheap large-scale batch processing.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, platform-delivery/cloud-platform-and-cost @2

### AWS SNS
**Short:** Managed pub/sub notification service fanning a message out to SQS queues, Lambda, HTTP endpoints, email or SMS.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, observability/alerting-and-incident-response @3, platform-delivery/cloud-platform-and-cost @3

### AWS SNS/SQS
**Short:** AWS managed pub/sub topics (SNS) fanning out to durable queues (SQS) for asynchronous service-to-service messaging.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @3, platform-delivery/cloud-platform-and-cost @3

### AWS SQS
**Short:** Managed queue service: at-least-once standard queues and FIFO queues with a 5-minute dedup window, reached over HTTPS.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @2, platform-delivery/cloud-platform-and-cost @3

Consumers pull rather than being pushed to: a receive call hides a message for a visibility timeout, and unless the consumer deletes it inside that window the message reappears for someone else. That makes idempotent handlers and a visibility timeout longer than real processing time non-negotiable. A redrive policy moves a message to a dead-letter queue after a configured receive count, and long polling avoids burning API calls on empty receives.

Reach for it to decouple a producer from a slow or bursty consumer inside AWS without operating broker infrastructure. It is a queue, not a log: a deleted message is gone, there is no replay and no independent fan-out to several consumer groups from the same stream, which is where Kafka or an SNS fan-out belongs instead.

### AWS SQS/SNS
**Short:** AWS managed queue (SQS) and pub/sub fan-out (SNS) for async messaging between services.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, platform-delivery/cloud-platform-and-cost @3

SQS is a pull queue: a consumer receives a message, gets a visibility timeout to process it, and deletes it on success, with a redrive policy sending repeat failures to a dead-letter queue. Standard queues are at-least-once and unordered; FIFO queues add per-message-group ordering and deduplication at much lower throughput. SNS is the push side, fanning one publish out to many subscribers such as SQS queues, Lambda functions or HTTPS endpoints.

The usual pattern is SNS fanning into one SQS queue per consumer, so each consumer owns its own backlog, retries and DLQ. Reach for them when you want managed async messaging with no brokers to run; do not reach for them when you need a replayable log, since a deleted message is gone and there is no offset to rewind.

### AWS Step Functions
**Short:** Managed state-machine service (ASL JSON) for saga orchestration across services, with per-step retries and visibility.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @3

You define the workflow as a JSON state machine in the Amazon States Language — task, choice, parallel, map and wait states — and the service drives it, persisting state after every transition and retrying individual steps under a per-state retry and catch policy. That makes it the orchestrated form of the saga pattern: compensation logic lives in one state machine instead of being scattered across services, and the execution history is a visible audit trail an operator can read without adding logging.

Standard workflows are durable and long-running; Express workflows trade that for high-volume short executions. The cost of either is that your orchestration logic now lives in vendor-specific JSON rather than in your codebase.

### Axon Framework
**Short:** Java CQRS and event-sourcing framework with a command bus, event-sourced aggregates and saga orchestration.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, apis-frameworks/design-patterns-and-principles @2, data-movement/event-streaming-and-processing @2

Axon supplies the three buses -- command, event and query -- plus an event store, so an aggregate becomes a class whose `@CommandHandler` methods validate a command and emit events via `apply()`, and whose `@EventSourcingHandler` methods fold those events back into state when the aggregate is loaded. Snapshots bound replay cost once an aggregate's event count grows, and sagas (`@SagaEventHandler` with an association property) coordinate long-running cross-service workflows with deadlines and compensating actions. The payoff is that the event log becomes the system of record, so state history is a product feature rather than an audit afterthought.

Reach for it when history and CQRS are genuine requirements, not stylistic preferences. It is a heavy commitment: evolving the schema of stored events (upcasters) and rebuilding read models are permanent obligations, and plain CRUD with a transactional outbox is usually the cheaper answer.

### AxonServer
**Short:** Purpose-built event store and message bus for Axon Framework applications doing event sourcing and CQRS.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

### BackgroundTasks
**Short:** FastAPI/Starlette helper that runs short work in-process after the response is sent; no broker, no durability.
**Kind:** api
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1, apis-frameworks/aop-middleware-and-scheduling @2

### Cadence
**Short:** Uber's durable workflow engine for long-running orchestration and sagas; Temporal is the fork of it.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1

Workflow code in Go or Java runs against a Cadence service that persists every decision and activity result as an event history; if the worker process dies, another one replays that history to rebuild the workflow's exact in-memory state and continues. Activities hold the side effects and get their own retries and timeouts, while timers, signals, and queries let a workflow sleep for days or react to an external event without holding a thread.

Temporal is a fork of it by the same original authors, and the programming model is nearly identical. Reach for Cadence when you are already on it or inside Uber's ecosystem, where it is still developed; for new durable-execution work, Temporal is where the community and tooling went.

### Camunda
**Short:** BPMN workflow and saga orchestration engine with modelled processes, human tasks, timers and durable process state.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @3, apis-frameworks/design-patterns-and-principles @3

The process is a BPMN diagram, and the engine persists each running instance's position in a database, so a process survives a restart and can sit for days waiting on a timer or a human task before continuing. Used as a saga orchestrator, each step is a service task the engine hands to a worker, and rollback is modelled explicitly as compensation handlers that the engine invokes in reverse order when a later step fails.

Reach for it when the workflow is a business process that non-engineers need to read, audit and change, and when instances are long-lived enough that durable state is the requirement. It is heavy for a three-step technical retry, where a queue with a dead-letter path or a plain state machine in code costs far less to run.

### Camunda/Zeebe
**Short:** Camunda's Zeebe engine: horizontally scalable BPMN execution used to orchestrate microservice sagas.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @3

### Celery
**Short:** Python distributed task queue running background jobs over Redis/RabbitMQ/SQS with retries and scheduling.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1, apis-frameworks/design-patterns-and-principles @3

Decorate a function as a task, call `.delay()`, and a worker process elsewhere pulls it off the broker; a result backend optionally stores the return value. Beat adds periodic scheduling, and the canvas primitives - chain, group, chord - compose tasks into workflows. The concurrency model is a real decision: prefork gives true parallelism for CPU-bound work, while gevent or eventlet pools suit fan-out over slow IO.

Delivery is at-least-once, and with `acks_late` a worker crash re-runs the task, so handlers must be idempotent. Pass identifiers rather than large payloads, and remember that a broker configured without durability can drop queued work on restart. Reach for it when jobs must outlive the request, survive a deploy, retry with backoff, or run on separate machines; for a short in-process side effect, an asyncio task or FastAPI's background tasks is far less machinery.

### checkpointers
**Short:** LangGraph persistence adapters (Sqlite, Redis, Postgres, Mongo) that snapshot agent state so a run can resume.
**Kind:** api
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, llm-apps/agent-framework @2, llm-apps/prompting-context-and-structured-output @3

### ClickHouse Sink
**Short:** Kafka Connect sink connector that streams topic records into ClickHouse tables for real-time analytics.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/warehouse-and-olap @2

### Conductor OSS
**Short:** JSON-DSL workflow engine for long-running orchestration and sagas; Netflix-created, now stewarded by Orkes.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1

A workflow is a JSON definition of tasks and their wiring; the server owns state, retries, timeouts and history, while your services run stateless workers that poll for a task type, do the work and post the result back. That inversion is the point, because orchestration logic then lives in one versioned definition you can inspect, version and replay instead of being smeared across service-to-service calls, and it makes a saga's compensating steps explicit rather than implied. It suits long-running, human-in-the-loop or multi-service business processes. It is heavy for anything a queue plus an idempotent consumer already handles, and adopting it means operating the server and its datastore.

### Confluent Cloud
**Short:** Fully managed Kafka service bundling Schema Registry, connectors and stream processing without cluster ops.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2, platform-delivery/cloud-platform-and-cost @3

### Cosmos
**Short:** Astronomer Cosmos: renders a dbt project as native Airflow task groups so each model becomes a schedulable task.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, data-stores/warehouse-and-olap @3

### Cruise Control
**Short:** LinkedIn's Kafka operator tool: goal-based partition rebalancing, broker add/remove and anomaly detection.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @2

### custom
**Short:** Not a product: a placeholder for hand-rolled implementations such as a bespoke CDC relay or A/B harness.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @3

### custom Kafka-based state machine
**Short:** Hand-rolled saga orchestrator whose state transitions are driven by Kafka topics rather than a workflow engine.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/event-streaming-and-processing @2

### Dagster
**Short:** Asset-oriented data orchestrator: software-defined assets, scheduling, lineage and built-in data-quality checks.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/data-quality-and-lineage @2, ml-lifecycle/ml-platform-and-pipelines @3

### Dask
**Short:** Python parallel-computing library scaling NumPy/pandas-style dataframes and task graphs across cores and clusters.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/batch-and-distributed-compute @1, runtime-systems/concurrency-and-async @3, model-training/distributed-training @3

It builds a task graph and hands it to a scheduler — threads, processes, or a distributed cluster of workers. `dask.dataframe` and `dask.array` mirror the pandas and NumPy APIs over partitioned chunks, so a dataset larger than memory is processed partition by partition, while `dask.delayed` and the futures interface let you parallelize arbitrary Python. Because workers are separate processes, CPU-bound Python escapes the GIL.

Everything is lazy until you call `.compute()`, which is what lets it fuse and schedule work — and also what surprises people whose "fast" line was doing nothing. Reach for it when the data does not fit in RAM or the work is embarrassingly parallel and you want to keep the pandas API; if the data fits on one machine, pandas or Polars is usually faster than paying the scheduling and shuffle overhead.

### Databricks
**Short:** Managed Spark and lakehouse platform unifying batch/streaming ETL, SQL analytics and the ML lifecycle.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, ml-lifecycle/ml-platform-and-pipelines @2, data-stores/warehouse-and-olap @2, platform-delivery/cloud-platform-and-cost @3

### Debezium
**Short:** Change-data-capture connector that tails a database's replication log and publishes row changes to Kafka.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @3

Debezium usually runs as a source connector inside Kafka Connect, reading PostgreSQL's write-ahead log through logical decoding, MySQL's binlog, or MongoDB's change stream, and emitting one message per row change carrying the before and after images plus the operation type. It snapshots existing table contents first and then switches to streaming, so a consumer built from scratch sees complete state rather than only changes since it started.

This is the standard mechanism behind the transactional outbox — write the business row and an outbox row in one local transaction, let Debezium publish it — and behind keeping search indexes, caches and analytics warehouses in step without dual writes. The operational hazard is on the database side: a stopped connector means an unconsumed replication slot, and Postgres will retain WAL indefinitely for it until the primary's disk fills.

### Debezium MongoDB connector
**Short:** Kafka Connect source that turns MongoDB change streams into an ordered stream of change-data-capture events.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/document @3

### deequ
**Short:** Spark library that declares data-quality constraints and computes metrics over large datasets.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/data-quality-and-lineage @1

Constraints are declared against a Spark DataFrame (completeness, uniqueness, value ranges, referential checks) and Deequ compiles them into a single pass of aggregations, so validating a very large table costs roughly one scan rather than one per rule. It also has a profiling and anomaly-detection side that persists computed metrics over time and flags a column whose null rate or distribution moves against its own history, which is how it catches silent upstream breakage rather than only hard schema errors. Reach for it when the data already lives in Spark and is too big to check row-by-row in Python; PyDeequ is the wrapper for PySpark jobs, and for pandas-scale pipelines Great Expectations is the lighter fit.

### Dramatiq
**Short:** Python background task queue over Redis or RabbitMQ, with thread or prefork workers, retries and dead-lettering.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1, data-movement/message-broker @3

### Durable Functions
**Short:** Azure Functions extension for stateful serverless orchestration: fan-out/fan-in, timers and approval workflows in code.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

### DVC
**Short:** Git-native version control for datasets, models and ML pipelines using content-addressable remote storage.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/experiment-tracking-and-tuning @2, devtools/version-control-and-workbench @2

DVC keeps the data out of git and a pointer in it. Adding a dataset hashes it into a content-addressable cache and commits a small metafile, so checking out an old commit and running `dvc checkout` reconstructs exactly the data that commit was trained on, with the bytes living in S3, GCS or any configured remote. Pipeline stages declared in `dvc.yaml` record their dependencies and outputs, and `dvc repro` re-runs only the stages whose inputs actually changed.

Reach for it when reproducibility is the problem — which dataset and which code produced which model — and the team already lives in git. It is not a data catalog or a query layer, and continuously appended tables fit a lakehouse format better.

### EdgeExecutor
**Short:** Airflow 3.0 executor that runs tasks on remote edge workers pulling work over HTTP, without direct metadata-DB access.
**Kind:** api
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/task-queue-and-jobs @2

### Elasticsearch Sink
**Short:** Kafka Connect connector indexing topic records into Elasticsearch, the usual read-model path.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, search-retrieval/lexical-and-hybrid-search @2

### event publication registry
**Short:** Spring Modulith's persisted record of application events, letting an unacknowledged listener be retried after a crash.
**Kind:** api
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2, data-movement/event-streaming-and-processing @3

### EventBridge
**Short:** AWS serverless event bus: rule-based routing, schema registry and scheduled events between services.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/event-streaming-and-processing @2, platform-delivery/cloud-platform-and-cost @3

### EventStoreDB
**Short:** Database purpose-built for event sourcing: the append-only event log is the system of record, plus snapshots.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, apis-frameworks/design-patterns-and-principles @2, data-stores/key-value-and-embedded @3

### Eventuate Tram
**Short:** Java framework implementing the saga and transactional-outbox patterns over a message broker.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2, data-movement/event-streaming-and-processing @3

It implements the transactional outbox in Java: your service writes its business rows and a message row inside the same local database transaction, and a separate relay — change-data-capture on the transaction log, or polling — publishes those message rows to Kafka or ActiveMQ. That removes the dual-write problem where a service commits to its database and then fails before publishing the event, leaving the rest of the system permanently out of step.

Eventuate Tram Sagas layers orchestrated sagas on top, with a saga definition that names each participant's command and its compensating command. Reach for it in Spring/JPA microservices that want correct event publishing without hand-rolling an outbox; if you already run Debezium for CDC or a durable workflow engine for orchestration, those cover the same ground.

### FlatFileItemReader
**Short:** Spring Batch reader that streams CSV or fixed-width lines into domain objects with restartable read state.
**Kind:** api
**Lang:** java
**Roles:** data-movement/batch-and-distributed-compute @1, apis-frameworks/aop-middleware-and-scheduling @2

### Flowable
**Short:** BPMN workflow engine with modelled processes, human tasks and timers; also RxJava's Flowable stream type.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, runtime-systems/concurrency-and-async @2, apis-frameworks/design-patterns-and-principles @3

The workflow engine executes BPMN 2.0 process definitions - and CMMN cases and DMN decision tables - persisting every running instance in a database, so a process survives a restart, waits days on a human task or a timer, and resumes exactly where it stopped. The value is that the state machine lives in a model an analyst can read and change, with history and audit for free, rather than being scattered across service code and cron jobs.

Reach for it for long-running, human-in-the-loop or approval-shaped processes; it is the wrong tool for high-throughput stateless message processing, where every step costs database writes. Note the name collision: in RxJava, `Flowable` is the backpressure-aware stream type, the counterpart of `Observable` that lets a slow consumer signal demand upstream.

### Google Cloud Composer
**Short:** Google Cloud's managed Apache Airflow: hosted scheduler, workers and web UI for DAG orchestration.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

### Google Pub/Sub
**Short:** GCP's managed pub/sub messaging service: auto-scaled topics and subscriptions over HTTP or gRPC.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/event-streaming-and-processing @2, platform-delivery/cloud-platform-and-cost @3

Publishers write to a topic and each subscription receives its own copy of the stream, so adding a consumer never disturbs the existing ones. The service holds a message until the subscriber acknowledges it within the ack deadline and redelivers otherwise, which makes delivery at-least-once and consumers' idempotency your responsibility; ordering holds only within an ordering key, and messages that keep failing are routed to a dead-letter topic after a configured number of attempts.

It is the low-operations choice on Google Cloud — no partitions or brokers to size, throughput scales on its own. Replay is done through snapshots and seek rather than by rewinding an offset in a log, which is a different mental model from Kafka.

### Great Expectations
**Short:** Python data-validation framework: declarative expectation suites plus generated data docs for pipeline quality gates.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/drift-and-production-monitoring @3

You assert properties (this column is never null, this value is one of five, this row count is in range) as an expectation suite, run it against a batch, and get a structured pass/fail result plus rendered documentation of what the data is supposed to look like. Wiring a checkpoint into an Airflow task or a training pipeline is what turns a silent schema change upstream into a failed run, and bad data that reaches a model is far more expensive to find later. The cost is ceremony: contexts, datasources, suites and checkpoints are a lot of configuration for a handful of checks, so for two or three assertions a plain query or a Pandera schema often does the job with less to maintain.

### Huey
**Short:** Lightweight Python task queue backed by Redis, SQLite or files; periodic tasks, retries, sync and async workers.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1

### Inngest
**Short:** SaaS-first durable functions platform for TypeScript and Python: steps that survive restarts, retries and sleeps.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/task-queue-and-jobs @2

### JdbcMessageStore
**Short:** Spring Integration message store persisting in-flight messages and groups to a database so flows survive restarts.
**Kind:** api
**Lang:** java
**Roles:** data-movement/message-broker @1, data-access/orm-and-data-mapping @3

### JmsTemplate
**Short:** Spring template collapsing JMS connect/send/translate/close into one call with unchecked exceptions.
**Kind:** api
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/design-patterns-and-principles @2

### JobDataMap
**Short:** Quartz's serializable parameter map carried with a scheduled job, so the command survives restarts and misfires.
**Kind:** api
**Lang:** java
**Roles:** data-movement/task-queue-and-jobs @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

### JobDetail
**Short:** Quartz's serializable job definition, stored durably with cron triggers and misfire policy so it survives restarts.
**Kind:** api
**Lang:** java
**Roles:** data-movement/task-queue-and-jobs @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

### joblib
**Short:** Python parallelism and caching helper; the process/thread backend behind scikit-learn's n_jobs and model pickling.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/batch-and-distributed-compute @1, runtime-systems/concurrency-and-async @2, model-training/classical-ml-and-boosting @3

### Kafka Connect
**Short:** Kafka's source/sink connector framework for moving data in and out of the log, including Debezium CDC and S3 sinks.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @3

Connect is a worker process -- standalone, or a distributed cluster that rebalances tasks and keeps its config, offsets and status in internal Kafka topics -- that runs connector plugins: a source connector pulls from an external system into topics, a sink connector writes topics out. You configure a connector with JSON over its REST API instead of writing code; Single Message Transforms handle field renames, masking and routing in flight, and converters (Avro, Protobuf or JSON Schema, usually with a schema registry) decide the wire format. Debezium source connectors reading the database transaction log are the standard change-data-capture path, and the S3, JDBC and Elasticsearch sinks cover most egress. Reach for it when the job genuinely is move-and-lightly-reshape; anything needing joins, windows or real business logic belongs in a stream processor such as Kafka Streams or Flink.

### Kafka Streams
**Short:** Java library for stateful stream processing directly on Kafka topics: joins, aggregations, exactly-once state stores.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1

It is a library, not a cluster: you embed it in an ordinary application and scale by starting more instances, which the consumer group protocol rebalances across partitions. State for joins and aggregations lives in a local RocksDB store backed by a compacted changelog topic, so when an instance dies its state is replayed onto whichever instance takes over its partitions. Exactly-once processing comes from Kafka transactions that commit the output records and the input offsets together.

Reach for it when both the input and the output are Kafka and you would rather not operate a separate processing cluster. Its limits follow from the same design: co-partitioning is required for joins, rebalances and state restore are the things that hurt in production, and a job reading from something other than Kafka belongs in Flink instead.

### Kafka, Redis Pub/Sub, AWS SNS
**Short:** Cross-process observer options: Kafka for durable replayable logs, Redis Pub/Sub and SNS for fire-and-forget fan-out.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/event-streaming-and-processing @2

### KurrentDB
**Short:** Purpose-built event store: append-only streams, optimistic concurrency and native subscriptions; formerly EventStoreDB.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/transactions-and-consistency @3, data-stores/key-value-and-embedded @3

### Managed Kafka
**Short:** Cloud-operated Kafka such as Amazon MSK or MSK Serverless: the same log API without running brokers yourself.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, platform-delivery/cloud-platform-and-cost @2, data-movement/message-broker @3

### Marquez
**Short:** Open-source metadata and lineage server, the reference implementation of the vendor-neutral OpenLineage standard.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/ml-platform-and-pipelines @3

### Materialize
**Short:** Streaming database that keeps SQL views incrementally up to date over Kafka and CDC feeds.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/warehouse-and-olap @2, data-stores/relational @3

### Maxwell
**Short:** MySQL binlog reader that publishes row changes as JSON to Kafka, used for outbox and CDC pipelines.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1

### Maxwell's Daemon
**Short:** MySQL binlog change-data-capture daemon emitting row changes as JSON to Kafka; simpler than Debezium, fewer connectors.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @3

### MongoDbMessageStore
**Short:** Spring Integration message store on MongoDB, giving aggregators and queue channels durable persistence.
**Kind:** api
**Lang:** java
**Roles:** data-movement/message-broker @1, data-stores/document @3

### MSK Serverless
**Short:** AWS-run Kafka with no broker sizing: capacity scales per-topic, with tiered storage and IAM auth.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2, platform-delivery/cloud-platform-and-cost @3

### Mule ESB
**Short:** Enterprise service bus and integration platform routing, transforming and mediating messages between systems.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, traffic-edge/api-gateway @2

### Native streaming
**Short:** Index entry for a cloud provider's own streaming primitive, here Kinesis Data Streams as the AWS-native pub/sub log.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1

### NATS JetStream
**Short:** NATS's persistence layer adding durable streams, replay and at-least-once consumers to its very fast pub/sub core.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/event-streaming-and-processing @2, data-stores/key-value-and-embedded @3

### OpenLineage
**Short:** Vendor-neutral open standard for emitting data lineage events from pipelines and schedulers.
**Kind:** spec
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1

### Orchestration
**Short:** Scheduling and sequencing pipeline steps with a DAG engine such as Airflow or Kubeflow Pipelines.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @2

### Pachyderm
**Short:** Kubernetes-native data versioning and pipeline system giving every run content-addressed, reproducible lineage.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/ml-platform-and-pipelines @2, platform-delivery/kubernetes-and-orchestration @3

### pandas
**Short:** Python DataFrame library for tabular loading, joining, grouping and feature transformation in memory.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/batch-and-distributed-compute @1, runtime-systems/collections-and-algorithms @2, ml-lifecycle/ml-platform-and-pipelines @3

### pandas-profiling
**Short:** Automated EDA report over a DataFrame (now ydata-profiling): distributions, correlations, missingness.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/evaluation-and-benchmarks @3

### pandas.DataFrame
**Short:** pandas' column-oriented in-memory table built on NumPy; the default Python structure for tabular analytics.
**Kind:** api
**Lang:** python
**Roles:** data-movement/batch-and-distributed-compute @1, runtime-systems/collections-and-algorithms @2

### Pandera
**Short:** Declarative schema and distribution validation for pandas/Spark DataFrames, catching bad or drifted columns.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/drift-and-production-monitoring @3, apis-frameworks/data-formats-and-api-contracts @3

You declare the expected frame as a schema or a typed model class, giving each column its dtype, nullability, allowed range, uniqueness and any custom check, then validate at pipeline boundaries. Failure reporting is the useful part: instead of an exception deep inside a transform, you get a structured report naming the columns and the offending rows.

Reach for it at the seams, between ingestion and training and again between training and serving, so a column that silently changed type, gained nulls or shifted distribution is caught before it becomes a drift or leakage bug that only shows up in offline-online metric disagreement. It applies the same schema idea to pandas, Polars and PySpark frames, so one contract can guard batch and streaming paths.

### Prefect
**Short:** Python-native workflow orchestrator; flows and tasks built dynamically at runtime, with retries and scheduling.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @2, data-movement/task-queue-and-jobs @3

You write plain Python and decorate functions as flows and tasks; the orchestrator records every run, retries failed tasks, caches results and handles scheduling and concurrency limits. Because the graph is discovered by executing the code rather than declared up front, branching on a value computed at runtime and fanning out over a list whose length is unknown until the flow runs are ordinary, not workarounds.

Reach for it when your pipeline logic is Python and genuinely dynamic, and when you want local development to be the same code that runs in production. Airflow remains the counterweight where you need its scheduler maturity, its operator ecosystem or a team that already runs it.

### provider packages
**Short:** Airflow's separately versioned integration packages supplying the operators, hooks and sensors for each system.
**Kind:** concept
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, devtools/build-and-dependency-management @3

### Pub/Sub
**Short:** Google Cloud Pub/Sub: managed at-least-once topic and subscription messaging with push or pull delivery.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, platform-delivery/cloud-platform-and-cost @3

### RabbitMQ
**Short:** AMQP broker with exchange-based routing and durable queues; common Celery/Dramatiq backend and STOMP relay.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @2, apis-frameworks/rpc-graphql-and-streaming @3

RabbitMQ is an AMQP 0-9-1 broker. Producers never name a queue; they publish to an exchange, and bindings decide which queues receive a copy — direct on an exact routing key, topic on a pattern, fanout to everything bound. Routing therefore lives in broker configuration and can change without redeploying producers. Queues are durable, each message is delivered to one consumer, and an unacknowledged message is requeued when that consumer dies.

That is a work-queue model, not a log: once acknowledged a message is gone, and there are no consumer-owned offsets to rewind, which is the line between it and Kafka. Reach for it for task distribution and complex routing — it is the common Celery and Dramatiq backend, and its STOMP plugin makes it the external relay behind Spring's WebSocket messaging — and use quorum queues, which replicate through Raft, when a queue must survive losing a node.
### RabbitMQ STOMP plugin
**Short:** RabbitMQ plugin exposing STOMP, letting browsers and app servers use the broker as a production relay.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, apis-frameworks/rpc-graphql-and-streaming @2

### rabbitmq_stomp plugin
**Short:** RabbitMQ plugin exposing a STOMP listener on port 61613 so an app can relay WebSocket STOMP traffic to a real broker.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, apis-frameworks/rpc-graphql-and-streaming @2

### Ray
**Short:** Distributed Python compute framework; Train/Tune/Serve/Data on top, and the placement layer for multi-node vLLM.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/batch-and-distributed-compute @1, model-training/distributed-training @2, ml-lifecycle/ml-platform-and-pipelines @2, inference/model-server @3, gpu/multi-gpu-and-collectives @3

Two primitives carry most of the weight: `@ray.remote` on a function gives a stateless task scheduled anywhere in the cluster, and on a class gives a long-lived stateful actor with its own process. A distributed object store moves large objects by reference with zero-copy reads inside a node, so passing a big array between tasks does not mean serializing it repeatedly. The libraries - Train, Tune, Serve, Data - are all built on those primitives.

Reach for it when Python work must span machines, or when the unit of work is a stateful worker that `multiprocessing` cannot express - which is also why it ends up as the placement and coordination layer under multi-node vLLM and Ray-based RLHF trainers. The costs are a cluster to operate and an object store whose memory pressure and disk spilling are the usual first production surprise.

### React UI
**Short:** Airflow 3.0's rewritten React front end: the DAG, task and asset browsing UI replacing the legacy Flask pages.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, devtools/version-control-and-workbench @3

### Reactor Kafka
**Short:** Reactive Streams adapter over the Kafka client, giving backpressure-aware Flux producers and consumers.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, runtime-systems/concurrency-and-async @2

### Redis Pub/Sub
**Short:** Redis fire-and-forget publish/subscribe channels; used to fan messages out across WebSocket server instances.
**Kind:** api
**Lang:** *
**Roles:** data-movement/message-broker @1, caching/distributed-cache @2, apis-frameworks/rpc-graphql-and-streaming @3

### Redis Streams
**Short:** Redis append-only log type with consumer groups, acknowledgements and pending-entry recovery.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2, data-stores/key-value-and-embedded @3

### Redpanda
**Short:** Kafka API-compatible streaming broker written in C++: no JVM, no ZooKeeper, thread-per-core, drop-in for Kafka clients.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

### Restate
**Short:** Durable execution runtime with durable promises and strong consistency; SDKs for TS, Python, Java, Go and Rust.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/task-queue-and-jobs @3

### RQ
**Short:** Redis Queue: a minimal Python background-job library with prefork sync workers and simple retries.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1

### SNS
**Short:** AWS Simple Notification Service: managed pub/sub topics fanning out to queues, functions and endpoints.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, observability/alerting-and-incident-response @3

### Soda
**Short:** Data quality platform: declarative checks in SodaCL run against warehouse tables and fail the pipeline on breach.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1

### Spark SQL
**Short:** Spark's SQL and DataFrame engine with the Catalyst optimizer, for distributed queries over lake and warehouse data.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, data-stores/warehouse-and-olap @2

### Spark Structured Streaming
**Short:** Spark's streaming API treating a stream as an unbounded table, with watermarks, windows and exactly-once sinks.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/batch-and-distributed-compute @2

### Spring Batch Tasklet
**Short:** Spring Batch single-step unit of work with transaction boundaries and repository metadata; restartable on failure.
**Kind:** api
**Lang:** java
**Roles:** data-movement/batch-and-distributed-compute @1, apis-frameworks/aop-middleware-and-scheduling @2, data-movement/workflow-and-durable-execution @3

### Spring Cloud Data Flow
**Short:** Orchestration UI and runtime composing Spring Batch tasks and Spring Cloud Stream pipelines, with scheduling.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/event-streaming-and-processing @2, data-movement/task-queue-and-jobs @2

### Spring Cloud Stream
**Short:** Spring binder abstraction that writes messaging code once and binds it to Kafka, RabbitMQ, Kinesis or Service Bus.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2, apis-frameworks/dependency-injection-and-config @3

You write plain `java.util.function` beans -- a Supplier that produces, a Function that transforms, a Consumer that sinks -- and a binder maps them to real destinations from configuration, so moving from RabbitMQ to Kafka is a dependency and a property change rather than a rewrite. It also standardises consumer groups, partitioning, retries, dead-letter routing and error channels across brokers.

Reach for it when you want messaging code that is portable and free of broker boilerplate. The abstraction leaks as soon as you need broker-specific behaviour -- a Kafka Streams topology, transactional producers, RabbitMQ's exchange types -- at which point the native client is the honest choice.

### Spring Cloud Task
**Short:** Spring Cloud module for short-lived finite tasks that record start, end and exit status in a task repository.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/task-queue-and-jobs @1, data-movement/workflow-and-durable-execution @3

### Spring Events module
**Short:** Spring Modulith event support: transactional application events with a publication registry for reliable handoff.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, apis-frameworks/design-patterns-and-principles @2, data-movement/message-broker @3

### Spring Integration
**Short:** Spring's enterprise integration framework: channels, adapters and transformers implementing EIP patterns.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/design-patterns-and-principles @2, data-movement/event-streaming-and-processing @3

### Spring Kafka
**Short:** Spring's Kafka integration: KafkaTemplate producers, @KafkaListener containers, retries and dead-letter topics.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

### Spring State Machine
**Short:** Spring framework for explicit state machines - states, transitions, guards, actions - fits a saga orchestrator core.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, apis-frameworks/design-patterns-and-principles @2, data-access/transactions-and-consistency @3

### spring-amqp
**Short:** Spring's RabbitMQ integration: RabbitTemplate for publishing, @RabbitListener for consuming, retries and DLX.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @3

### spring-cloud-starter-bus-amqp
**Short:** Spring Cloud Bus over RabbitMQ, broadcasting config-refresh and management events to every instance of a service.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/dependency-injection-and-config @2

### spring-integration-core
**Short:** Spring's enterprise-integration engine: message channels, endpoints, routers and aggregators in-process.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

### spring-kafka
**Short:** Spring's Kafka integration: KafkaTemplate, @KafkaListener containers and Kafka transaction management.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

### spring-modulith-events-jpa
**Short:** Persists published application events in a JPA event registry so they survive a crash and are republished.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-access/transactions-and-consistency @2, data-movement/workflow-and-durable-execution @3

### SQS
**Short:** AWS managed queue with at-least-once delivery, visibility timeouts, DLQs and optional FIFO ordering.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @2, platform-delivery/cloud-platform-and-cost @3

A producer sends a message; a consumer receives it and gets a visibility timeout during which the message is hidden from everyone else. Deleting it inside that window completes the work, and failing to — a crash, or a timeout shorter than the job — makes it visible again for redelivery. Standard queues are unordered and at-least-once with effectively unlimited throughput; FIFO queues add per-message-group ordering and deduplication at a lower throughput ceiling.

Because redelivery is normal rather than exceptional, consumers must be idempotent, and a redrive policy should send repeatedly failing messages to a dead-letter queue instead of letting them cycle forever. Reach for it as the default AWS work queue, and for Kafka or Kinesis instead when several consumers must independently read the same stream or replay history, which a queue deletes.

### Step Functions
**Short:** AWS managed state-machine orchestrator: durable multi-step workflows with retries, parallel branches and integrations.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

### Stream processing
**Short:** The stage that computes over a live event log rather than a batch, here Kinesis Data Analytics or Flink over the stream.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1

### Task Execution API
**Short:** Airflow 3.0's Task Execution API, the boundary that lets workers run tasks without direct metadata-DB access.
**Kind:** api
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1

### Task SDK
**Short:** Airflow 3.0 SDK that runs task code in an isolated process talking to the scheduler over an API, not the database.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1

### taskiq
**Short:** Async-native distributed task queue for Python - a Celery alternative on asyncio with pluggable brokers/backends.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1, runtime-systems/concurrency-and-async @2

### Temporal
**Short:** Durable execution engine: workflows written as code survive restarts, with built-in retries, timeouts and sagas.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2, data-movement/task-queue-and-jobs @3

Workflow code is ordinary Go, Java, Python or TypeScript, but every step's result is persisted to an event history, so when a worker dies the workflow is replayed against that history on another worker and continues exactly where it stopped — which is why sleeping for thirty days is a legitimate line of code. Side effects live in activities, retried independently under a policy you set, and a saga's compensation is simply the code you write after a failure rather than an orchestration DSL.

The determinism requirement is the thing to internalize: workflow code must not read a clock, generate a random number or call a service directly, because replay has to reproduce the same decisions. Reach for it when state must outlive a process — multi-day approvals, payment sagas, long-running agents — and not for work that finishes inside one HTTP request.

### Temporal child workflows
**Short:** Temporal feature spawning independently-scheduled durable sub-workflows for long-running parallel work.
**Kind:** api
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, llm-apps/agent-framework @3

### Temporal.io
**Short:** Durable execution platform: workflow code checkpointed to a history so long-running sagas survive process restarts.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2, data-movement/task-queue-and-jobs @3

You write ordinary code in Go, Java, TypeScript, Python, or .NET, but the Temporal service persists every step's result to an event history; when a worker dies mid-flight, another worker replays that history to rebuild the exact in-memory state and carries on from the point of failure. Side effects go into Activities, which carry their own retry policies, timeouts, and heartbeats, while the workflow itself must be deterministic — no direct clock reads, no randomness, no I/O — because replay must reproduce the same decisions.

Reach for it when a business process runs for minutes to months across several services and "the process died halfway" must not lose state: sagas with compensation, payment and provisioning flows, human-approval steps, long agent runs. The costs are real — operating the service or paying for Temporal Cloud, and versioning workflow code carefully so a deploy does not break replay of histories still in flight.

### TensorFlow Data Validation
**Short:** TFX library that infers a data schema and detects skew, drift and anomalies in training and serving data.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1

### TFDV
**Short:** TensorFlow Data Validation: infers a data schema, then detects anomalies and training/serving skew in new batches.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/drift-and-production-monitoring @2

### Tiered storage
**Short:** Offloading a broker's older log segments to object storage (Kafka KIP-405) so retention is not bound by disk.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/object-and-file-storage @2

### Workflows
**Short:** Serverless state-machine orchestrators (Google Cloud Workflows and peers) that survive restarts between steps.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

### ydata-profiling
**Short:** Generates an automated EDA report over a dataframe: distributions, correlations, missingness and leakage hints.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/ml-platform-and-pipelines @3
