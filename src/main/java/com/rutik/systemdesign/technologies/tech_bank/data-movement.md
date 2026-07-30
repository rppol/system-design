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

### Apache Kafka
**Short:** Distributed partitioned commit log used as a durable message broker and the backbone of event-streaming pipelines.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @1, data-access/transactions-and-consistency @3

### Apache Spark
**Short:** Distributed compute engine for large-scale batch and streaming jobs; the usual home of offline feature computation.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, data-movement/event-streaming-and-processing @3, ml-lifecycle/ml-platform-and-pipelines @3, data-stores/warehouse-and-olap @3

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

### AWS SQS/SNS
**Short:** AWS managed queue (SQS) and pub/sub fan-out (SNS) for async messaging between services.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, platform-delivery/cloud-platform-and-cost @3

### AWS Step Functions
**Short:** Managed state-machine service (ASL JSON) for saga orchestration across services, with per-step retries and visibility.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @3

### Axon Framework
**Short:** Java CQRS and event-sourcing framework with a command bus, event-sourced aggregates and saga orchestration.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, apis-frameworks/design-patterns-and-principles @2, data-movement/event-streaming-and-processing @2

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

### Camunda
**Short:** BPMN workflow and saga orchestration engine with modelled processes, human tasks, timers and durable process state.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @3, apis-frameworks/design-patterns-and-principles @3

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

### Great Expectations
**Short:** Python data-validation framework: declarative expectation suites plus generated data docs for pipeline quality gates.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/drift-and-production-monitoring @3

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

### Kafka Streams
**Short:** Java library for stateful stream processing directly on Kafka topics: joins, aggregations, exactly-once state stores.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1

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

### Prefect
**Short:** Python-native workflow orchestrator; flows and tasks built dynamically at runtime, with retries and scheduling.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @2, data-movement/task-queue-and-jobs @3

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
