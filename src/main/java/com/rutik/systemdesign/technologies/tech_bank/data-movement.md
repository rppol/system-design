# Queues & streaming — technology bank

<!-- tech-bank tier: data-movement -->

The 148 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Queues & streaming** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### ActiveMQ Artemis
**Short:** Multi-protocol JMS broker (AMQP, MQTT, STOMP, OpenWire), usable as the external STOMP relay for WebSockets.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, apis-frameworks/rpc-graphql-and-streaming @2

Artemis is the successor codebase to ActiveMQ Classic, built on a non-blocking append-only journal and an address model rather than a queue model: producers send to an address, which routes to anycast queues for point-to-point delivery or multicast queues for publish-subscribe. Protocol heads for AMQP 1.0, MQTT, STOMP, OpenWire and its own core protocol all map onto that same addressing, so a message published over one protocol can be consumed over another. High availability is either shared-store or replication between a live and a backup broker.

Reach for it when you need real JMS semantics -- transactions, message selectors, durable subscriptions, scheduled delivery -- or when several protocol families must meet on one broker, which is why it also serves as an external STOMP relay behind WebSocket messaging. The operational work is journal tuning and a paging strategy for when a consumer falls behind. For a replayable log read by many independent consumer groups, Kafka is the different shape you actually want.

### aio-pika
**Short:** Asyncio AMQP client for RabbitMQ with connection/channel pooling and per-message consumer tasks.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/message-broker @1, data-access/drivers-and-connection-pooling @2, runtime-systems/concurrency-and-async @3

It wraps the lower-level `aiormq` transport in an awaitable object model: `connect_robust` reconnects and re-declares exchanges, queues and bindings after a broker restart, `declare_queue` hands back an object you can iterate or attach a callback to, and each delivery arrives as a message whose `process()` context manager acks on success and nacks on an exception. Connection and channel pools cap how many channels a process opens, which matters because a channel is not free on the broker side.

Reach for it in asyncio services that publish or consume AMQP without blocking the event loop. The setting that decides behaviour under load is prefetch: an unbounded `qos` with slow handlers buries one worker while others idle, and unacknowledged messages pile up invisibly. For a synchronous Django or Flask codebase, the blocking `pika` client or Celery is the honest fit rather than bolting an event loop onto request handling.

### aiokafka
**Short:** asyncio-native Kafka producer and consumer client for Python event-driven services.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/event-streaming-and-processing @1, runtime-systems/concurrency-and-async @2, data-movement/message-broker @3

It is a pure-Python implementation of the Kafka protocol on asyncio rather than a wrapper over librdkafka, exposing `AIOKafkaProducer` and `AIOKafkaConsumer` with consumer-group membership, rebalance listeners, automatic or manual offset commits, and transactional produce via a `transactional_id` for read-process-write exactly-once. Because the poll loop is a coroutine, consumption shares the event loop with your HTTP handlers instead of occupying a background thread that must hand work across a queue.

Reach for it when the service is already asyncio and you want backpressure and cancellation to work naturally through the same primitives as the rest of the code. The cost is throughput: pure-Python framing and deserialization become the bottleneck well before the broker does, and it trails the Java client on newer protocol features. `confluent-kafka-python` over librdkafka is far faster if you can tolerate running a blocking client in an executor.

### aiormq
**Short:** Low-level asyncio AMQP 0.9.1 client for RabbitMQ; the transport underneath aio-pika.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/message-broker @1, runtime-systems/concurrency-and-async @2, data-access/drivers-and-connection-pooling @3

It implements AMQP 0.9.1 framing directly on an asyncio transport -- connection and channel negotiation, heartbeats, `basic.publish`, `basic.consume`, publisher confirms -- and hands back futures and frame objects rather than wrapping them in friendlier types. There is no automatic reconnection, no queue object model and no acknowledge-on-exit context manager; when the broker closes a channel after a protocol error, it is your code that has to notice and recover.

You would depend on it directly only when writing your own client abstraction, or when you need control over frames, heartbeat intervals and channel lifecycle that a higher layer hides. For application code the answer is aio-pika, which is built on it and supplies the robust connection, declared-queue objects, channel pools and the per-message process context manager you would otherwise reimplement, generally worse.

### Airbyte
**Short:** Open-source ELT platform with hundreds of connectors syncing SaaS and database sources into a warehouse or lake.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/warehouse-and-olap @3, data-movement/data-quality-and-lineage @3

Each connector is a container speaking the Airbyte protocol over stdout -- `spec`, `check`, `discover`, `read` -- so a source emits records and state messages and a destination consumes them. That contract is why connectors can be written in any language and why building a custom one is routine rather than exotic. Syncs run full-refresh or incremental, with the cursor column or CDC log position carried in the state blob the platform persists between runs, and destinations land raw data that a typing-and-deduplication step or dbt turns into modelled tables.

Reach for it when the problem is breadth -- many low-volume SaaS and database sources whose connectors nobody wants to write and maintain -- and self-hosting is acceptable. The costs are uneven quality across the long tail of community connectors and a deployment that is heavier than the volume it moves. Fivetran is the managed alternative when reliability outweighs cost, and a dedicated Debezium pipeline beats it for high-volume database replication.

### Airflow
**Short:** DAG scheduler for data and training pipelines: Python-defined tasks, dependency management, retries and backfills.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @2, data-movement/task-queue-and-jobs @3

You write a DAG in Python: tasks are operator instances, dependencies are `>>` edges, and the scheduler walks the graph, submitting ready tasks to an executor (Celery, Kubernetes, or local) while a metadata database — usually PostgreSQL — holds every run's state. Retries, SLAs, sensors that wait on an external condition, and backfill over a historical date range come with it, which is why it became the default for nightly ETL and training pipelines.

The rule people learn late is that Airflow is an orchestrator, not a compute engine: tasks should trigger Spark, dbt, or a training job and let data move through storage, because passing real data between tasks via XCom pushes it through the metadata DB. Reach for it for scheduled batch work with real dependency structure; it is a poor fit for streaming, sub-minute latency, or a workflow that branches differently for every event.

### Amazon Kinesis
**Short:** AWS-managed streaming service whose Data Streams shards carry ordered records for a fixed retention window.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

A stream is divided into shards, each an ordered sequence with its own throughput ceiling, and the partition key decides which shard a record lands on -- the same design decision Kafka's partition key makes, with the same consequence that ordering exists per shard and never per stream. Consumers track their own position through the Kinesis Client Library or enhanced fan-out, and records are retained for a configurable window rather than indefinitely, so replay is bounded by retention rather than by disk.

Reach for it when the workload is already inside AWS and you would rather size shards than operate brokers; provisioned mode makes you manage shard counts while on-demand scales for you at a higher unit cost. It is the usual sink for a Kafka-free Debezium Server deployment, and the tradeoff against Kafka is fewer moving parts against a shorter retention window and a weaker replay story.

### Amazon MQ for RabbitMQ
**Short:** AWS-managed RabbitMQ; removes cluster operations at the cost of trailing upstream versions and an allowlisted plugin set.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, platform-delivery/cloud-platform-and-cost @2

A managed broker service running RabbitMQ on AWS-operated instances, handling provisioning, patching and multi-availability-zone deployment. It offers single-instance and clustered deployment modes, integrates with CloudWatch and VPC networking, and exposes the standard AMQP and management endpoints, so existing clients connect unchanged.

The constraint to settle during design rather than during implementation is what the managed engine will not do: supported versions trail upstream, and plugins are an allowlist, so a community plugin such as the delayed-message exchange may simply be unavailable. That turns a feature choice into an architecture constraint. Read the supported-version and plugin pages before the design commits to anything version-specific.

### Amazon MSK
**Short:** AWS-managed Apache Kafka: provisioned or serverless brokers with tiered storage, IAM auth and less tuning surface.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2, platform-delivery/cloud-platform-and-cost @3

MSK runs genuine open-source Kafka brokers inside your VPC: you choose the version, the instance type and the broker count, and clients connect with unmodified Kafka libraries. AWS owns provisioning, patching, broker replacement and the metadata quorum; you still own topics, partition counts, retention and consumer-group hygiene. Authentication is IAM through a SASL mechanism that maps Kafka authorization onto IAM policy, or SASL/SCRAM from Secrets Manager, or mutual TLS. Tiered storage moves older log segments off broker volumes so retention is not bounded by attached disk.

Reach for it when you want Kafka semantics without operating brokers and the rest of the stack is already in AWS. The costs are broker-hours billed whether or not traffic flows, cross-AZ replication traffic that is charged, and tuning you still have to do. Confluent Cloud bundles Schema Registry, connectors and stream processing if you want the ecosystem rather than just the brokers.

### Amazon MWAA
**Short:** AWS-managed Apache Airflow: hosted scheduler, workers and web UI for DAG orchestration without cluster ops.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

It runs the Airflow scheduler, web server and workers as a managed environment: DAG files, plugins and a `requirements.txt` are read from an S3 bucket, workers autoscale between a minimum and maximum count, logs and metrics go to CloudWatch, and the environment sits in your VPC so tasks can reach private databases and services. You pick an Airflow version and an environment size instead of sizing instances, and the metadata database is managed and not directly reachable.

Reach for it when you want Airflow's operator ecosystem without running the scheduler and its database yourself. The costs are the ones every managed Airflow carries: upgrades happen on the provider's cadence, dependency installation is constrained to the requirements file and a bad pin can fail an environment update for half an hour, and you pay continuously even when no DAG is running. Cloud Composer and Astronomer Astro are the equivalents elsewhere.

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

You write one pipeline against the Beam model -- `PCollection`s transformed by `ParDo`, `GroupByKey`, windows and triggers -- and a runner translates it for Flink, Spark, Dataflow or a local direct runner. The model's real contribution is treating batch as the bounded special case of streaming: windowing, event-time watermarks and triggers decide when a result is emitted and how late arrivals revise it, with identical semantics in both modes. The portability framework lets Python transforms run inside a JVM runner through a language-agnostic SDK harness.

Reach for it when the same logic must run as both a backfill and a live stream, or when you want the option to change execution engines later; on Google Cloud, Dataflow is the fully managed runner and the usual reason people adopt it. The cost is a layer of indirection: runner support for features is uneven, and debugging spans your code, the harness and the runner. If you are committed to one engine, its native API is more direct.

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

### Apache Pulsar
**Short:** Messaging and streaming platform that separates stateless brokers from BookKeeper storage, so a broker can be replaced without moving data.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

A topic's data lives in BookKeeper ledgers rather than on the broker's own disk, which is the architectural difference that matters: a failed broker's topics are immediately servable by any other broker, with no re-replication step. On top of that sits a richer subscription model than a partitioned log usually offers -- exclusive, failover, shared and key-shared subscriptions let one topic behave as a queue or as a stream depending on the consumer, and tiered storage offloads older ledgers to object storage.

Reach for it when the multi-tenancy, geo-replication or queue-and-stream duality is worth a second stateful system in the deployment, or when broker replacement without data movement is an operational requirement. The cost is exactly that second system: BookKeeper and ZooKeeper are their own operational surfaces, and the ecosystem around Kafka remains considerably larger.

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
**Lang:** java
**Roles:** data-movement/batch-and-distributed-compute @1, applied-ml/recommenders-and-graph-ml @2, data-stores/graph-db @3

A graph is represented as two RDDs, vertices and edges, joined through a triplet view, and the Pregel API expresses iterative algorithms as rounds of message sending, aggregation and vertex-state update -- which is how PageRank, connected components, triangle counting and label propagation scale past one machine. Partitioning is edge-cut based, so the partition strategy you choose is what keeps shuffle volume tolerable on a skewed graph where a few vertices hold most of the degree.

It is a Scala API over RDDs and receives little new development; GraphFrames is the DataFrame-based successor with Python bindings, and a graph database is the right shape for traversals served a request at a time. Reach for GraphX only for batch analytics over a graph that already lives inside a Spark job -- computing components or centrality across billions of edges as one stage of a larger pipeline -- and not as a query layer.

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

A job is an async function; enqueuing writes it into a Redis sorted set scored by its scheduled run time, and a worker pops due jobs and runs them as tasks on the event loop, so one process handles many jobs that are all awaiting IO. Deferred and cron-style jobs come out of the same sorted-set mechanism rather than a separate scheduler process, results and job state live in Redis under a TTL, and passing an explicit job id gives you deduplication because a second enqueue with that id is a no-op.

Reach for it in an asyncio service where background work is IO-bound and Celery's configuration surface would be the largest thing in the project. The limits follow directly: Redis is the only backend, a CPU-bound job blocks the loop and everything else on it unless you push it to a process pool, and durability is exactly Redis's durability. For prefork parallelism, multiple brokers and a large ecosystem, Celery remains the answer.

### Astronomer Astro
**Short:** Commercial managed Apache Airflow platform with hosted schedulers, deploy tooling and observability for DAGs.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, ml-lifecycle/ml-platform-and-pipelines @3, platform-delivery/cloud-platform-and-cost @3

Astro packages Airflow as a hosted control plane plus deployments you push images to: the `astro` CLI builds a Docker image from your project, runs that same image locally, and deploys it onto a managed scheduler and worker pool, with worker queues letting heavy and light tasks land on differently sized workers. On top of stock Airflow it adds deployment-level access control, alerting, lineage and DAG-run analytics, and it tracks upstream releases closely because much of the maintainer effort comes from the same company.

Reach for it when Airflow is central enough that upgrade cadence, observability and vendor support are worth paying for and you would rather not operate schedulers and a metadata database. The costs are per-deployment pricing and a workflow shaped around image builds rather than syncing DAG files. MWAA and Cloud Composer are the cloud-provider equivalents, and self-hosting on Kubernetes stays free if you have platform engineers.

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

EMR provisions EC2 instances with Hadoop, Spark, Hive, Trino, HBase or Flink installed and configured: a primary node, core nodes that hold HDFS, and task nodes that hold none, which is precisely why task nodes are the ones you buy on Spot. Data normally lives in S3 through the EMRFS connector rather than in HDFS, so a cluster can be transient -- start it, run the step, write results to S3, terminate -- and EMR on EKS and EMR Serverless remove the EC2 layer entirely.

Reach for it for large scheduled Spark or Hive batch work where Spot pricing on task nodes is a real saving and you want direct control over cluster configuration. The costs are the ones open-source Hadoop always carried: version and dependency management, tuning, and debugging a resource manager. Databricks or EMR Serverless trade a higher unit price for far less of that, and are the better answer when engineering time is the scarce resource.

### AWS SNS
**Short:** Managed pub/sub notification service fanning a message out to SQS queues, Lambda, HTTP endpoints, email or SMS.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, observability/alerting-and-incident-response @3, platform-delivery/cloud-platform-and-cost @3

A publish goes to a topic and the service delivers a copy to every subscription, retrying on a per-protocol schedule and optionally capturing what never lands on a subscription-level dead-letter queue. Filter policies evaluate message attributes at the topic, so a subscriber receives only the messages matching its policy and filtering stays out of consumer code. Standard topics are at-least-once and unordered; FIFO topics preserve order within a message group and may only deliver to SQS FIFO queues.

Reach for it when one event must reach several independent consumers, and for the operational notification path where alarms, budget and deployment events go. Its structural weakness is durability at the subscriber: an HTTPS or Lambda endpoint that is down depends entirely on the retry schedule, which is why the durable pattern is always one SQS queue per consumer so the backlog and the redrive policy belong to the consumer rather than the topic.

### AWS SQS
**Short:** Managed queue service: at-least-once standard queues and FIFO queues with a 5-minute dedup window, reached over HTTPS.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @2, platform-delivery/cloud-platform-and-cost @3

Consumers pull rather than being pushed to: a receive call hides a message for a visibility timeout, and unless the consumer deletes it inside that window the message reappears for someone else. That makes idempotent handlers and a visibility timeout longer than real processing time non-negotiable. A redrive policy moves a message to a dead-letter queue after a configured receive count, and long polling avoids burning API calls on empty receives.

Reach for it to decouple a producer from a slow or bursty consumer inside AWS without operating broker infrastructure. It is a queue, not a log: a deleted message is gone, there is no replay and no independent fan-out to several consumer groups from the same stream, which is where Kafka or an SNS fan-out belongs instead.

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

It is two things in one process: an event store that appends events to per-aggregate streams with optimistic concurrency on the sequence number, and a message router that tracks which client instances handle which commands and queries. Command routing hashes on the aggregate identifier so every command for one aggregate reaches one instance, which is what lets that aggregate be cached without a distributed lock. Event processors read the global stream by token, and the clustered edition replicates through Raft.

Reach for it when you are running Axon Framework and would rather not assemble an event store on a relational database plus a broker for command and query routing, because it removes most of that wiring and the configuration that goes with it. The cost is a dedicated stateful component whose clustering and multi-context features are commercially licensed, and a choice coupled to one framework. A JDBC event store plus Kafka stays vendor-neutral if you do the routing yourself.

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

Zeebe is the engine behind Camunda 8, rewritten so that process state is not held in a relational database: workflow instances are partitioned, each partition is replicated through Raft across brokers, every state change is written to an append-only log, and an exporter streams those records to Elasticsearch or another store for the query and history side. Workers are not pushed to -- they subscribe to a job type and activate jobs, then complete or fail them -- which keeps your service topology out of the engine.

Reach for it when BPMN orchestration has to scale past what one database-backed engine handles, or when you prefer the operational shape of a replicated log to that of a transactional engine. The costs are a larger deployment surface -- brokers, gateway, exporter and a search store -- and a command-query split in which running state and the view of it are eventually consistent. Camunda 7's embeddable engine is still simpler for a single application.

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

The connector consumes the topics you name and batches records into ClickHouse inserts, mapping record fields onto table columns by name. Because ClickHouse rewards large infrequent inserts and punishes small frequent ones with runaway part counts and merge pressure, batch size and flush interval are the settings that actually decide whether the table stays healthy. Idempotence relies on the server deduplicating identical insert blocks, so a retried batch after a task failure does not duplicate rows.

Reach for it when Kafka is already the transport and ClickHouse is the analytics store, and you want ingestion configured rather than coded. The alternative shipped by the database itself is a Kafka table engine feeding a materialized view, which removes Connect from the picture but moves the consumer inside the database, where failures become a database problem. Either way, schema evolution on the topic has to be planned against the table's column set.

### CloudAMQP
**Short:** Managed RabbitMQ hosting across the major clouds, with per-plan node counts and its own monitoring layer.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, platform-delivery/cloud-platform-and-cost @2

A hosting provider that runs RabbitMQ clusters on the major cloud platforms, sizing each plan by node count and instance class and layering its own metrics, alarms and log integration on top of the standard management plugin.

It sits between self-hosting and a cloud vendor's own managed broker: closer to upstream than a vendor service tends to be, at the price of a third party in the dependency chain. Reach for it when you want RabbitMQ rather than a vendor's approximation of it, and check the per-plan node count against your queue type, because a quorum queue wants an odd number of nodes and a single-node plan cannot host a replicated one.

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

It runs Kafka as a service with the surrounding ecosystem attached rather than as bare brokers: Schema Registry for Avro, Protobuf and JSON Schema contracts with compatibility enforcement, managed source and sink connectors, stream processing, role-based access control and stream lineage. Cluster types run from a shared multi-tenant tier up to dedicated clusters with private networking, billing is by throughput, storage and connector task rather than broker hours, and retention is effectively unbounded because older segments live in object storage.

Reach for it when you want the platform, not just the log, and would rather not staff a streaming team -- schema governance and connector operations are the parts hardest to replicate in-house. The costs are price at high sustained throughput, egress charges, and coupling in the connectors and access-control model that make an exit real work. MSK is the cheaper option when you genuinely only need brokers.

### Cosmos
**Short:** Astronomer Cosmos: renders a dbt project as native Airflow task groups so each model becomes a schedulable task.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, data-stores/warehouse-and-olap @3

It parses a dbt project's `manifest.json` and turns each model, seed, snapshot and test into an Airflow task inside a task group, reusing the dependency graph dbt already knows. The consequence is per-model observability: retries, alerting, logs and the Airflow UI apply to an individual model rather than to one opaque `dbt run` step, and a single failed model can be cleared and rerun without re-executing everything upstream of it. Execution can happen in a local virtualenv, a Docker container or a Kubernetes pod per model.

Reach for it when dbt is a large part of the pipeline and you want its structure visible in an orchestrator you already operate. The cost is parse-time work in the scheduler, which is noticeable on a large project unless you render from a committed manifest. A single operator running `dbt build` stays simpler when a whole-project pass or fail is granular enough for the team.

### Cruise Control
**Short:** LinkedIn's Kafka operator tool: goal-based partition rebalancing, broker add/remove and anomaly detection.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @2

It builds a model of the cluster from broker and partition metrics, then optimizes replica placement against an ordered list of goals -- replica counts, leader distribution, disk usage, network in and out, rack awareness -- and produces a proposal you can inspect before it executes the reassignment with throttled data movement. The same model drives self-healing: broker failure, disk failure and goal-violation anomalies can trigger a rebalance automatically, and adding or decommissioning a broker becomes an API call rather than hand-written reassignment JSON.

Reach for it once a Kafka cluster is large enough that partitions drift into imbalance faster than anyone wants to fix by hand, and hot brokers start showing up as producer latency. The costs are another service to run plus a metrics reporter on every broker, and the fact that a rebalance moves real data, so throttles and timing matter. Managed Kafka services do this internally, and a small static cluster rarely needs it.

### custom
**Short:** Not a product: a placeholder for hand-rolled implementations such as a bespoke CDC relay or A/B harness.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @3

The entry stands in for the case where no product is adopted at all -- an outbox relay polling a table and publishing to a broker, a hand-written experiment assignment harness, a bespoke sync job -- because those appear in real designs alongside named tools and need somewhere to sit. What it actually names is a decision to own a mechanism rather than take one off the shelf, usually justified by the narrowness of the requirement.

Hand-rolling is right when the need is genuinely small and a mature product's operational surface would dominate it, and wrong far more often than teams expect, because the parts that get skipped are exactly the ones a real tool has already paid for: restart and resume semantics, ordering, backpressure, dead-lettering, schema change, metrics and a way to see what is stuck. Cost it over the second year, not the first week.

### custom Kafka-based state machine
**Short:** Hand-rolled saga orchestrator whose state transitions are driven by Kafka topics rather than a workflow engine.
**Kind:** concept
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/event-streaming-and-processing @2

The pattern is a saga implemented with topics instead of an engine: the saga's state is keyed by a saga id in a compacted topic or a local state store, each participant publishes a completion or failure event, and a processor folds those events into the next state and emits the next command. Ordering per saga follows from keying every message on the saga id, and progress survives a restart because the state store is rebuilt from its changelog rather than held only in memory.

Reach for it when Kafka is already the backbone, the workflow is short and stable, and adding infrastructure is not on the table. What you take on is everything an engine would have given free: timeouts and retries must be modelled as timers you schedule and store yourself, compensation is code paths nobody diagrams, and there is no execution history a support engineer can read. Once the state chart grows branches, Temporal or a BPMN engine costs less than maintaining it.

### Dagster
**Short:** Asset-oriented data orchestrator: software-defined assets, scheduling, lineage and built-in data-quality checks.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/data-quality-and-lineage @2, ml-lifecycle/ml-platform-and-pipelines @3

The unit is a software-defined asset -- a table, a file, a model -- declared as a function whose parameters are its upstream assets, so the graph being scheduled is the graph of data that should exist rather than a list of tasks to run. That inversion is what gives it lineage, per-asset freshness policies and the ability to materialize a subset. Resources are typed and injected, so the same asset runs against a local DuckDB in tests and a warehouse in production, and asset checks attach data-quality assertions to the asset itself.

Reach for it when the pipeline's product is data and you want lineage, partitions and testability as first-class concerns instead of conventions. The costs are a genuine mental shift for a team that thinks in tasks and an integration catalogue smaller than Airflow's. Airflow remains the safer pick for task-shaped orchestration of arbitrary external systems, where the thing being scheduled is not a dataset at all.

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

It is Spark plus a managed control plane: clusters and SQL warehouses run in your cloud account, notebooks and jobs are the interface, and Delta Lake -- Parquet files plus a transaction log -- supplies ACID commits, time travel and schema enforcement over object storage. Unity Catalog centralizes table, volume and model governance with lineage, Photon is a vectorized native engine under SQL and DataFrame queries, and MLflow, declarative pipelines and model serving cover the ML lifecycle in the same workspace.

Reach for it when a team wants batch, streaming, SQL analytics and machine learning over one governed set of tables rather than three separate stacks with three copies of the data. The costs are compute spend that grows quietly, platform units billed on top of cloud instance cost, and enough platform-specific surface that leaving is a project. EMR or self-managed Spark on Kubernetes is cheaper when you have the operations capacity to run it.

### DBOS
**Short:** Durable execution library that checkpoints workflow and step state into your own Postgres instead of a separate orchestrator service.
**Kind:** tech
**Lang:** python, js, go, java
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @3

You annotate a function as a workflow and its calls as steps, and the library records each step's completion in a Postgres schema alongside your own tables, so a crashed process resumes at the first unfinished step when it restarts. Because the durable state lives in the database you already run, there is no separate cluster to operate and a workflow's progress is queryable with ordinary SQL and committed in the same transactional world as your business rows.

Reach for it when you want durable execution without adopting a stateful service, and your Postgres has headroom for the extra write volume. The tradeoffs against Temporal are ecosystem breadth, cross-language reach, and the fact that your database now carries orchestration load as well as application load.

### Debezium
**Short:** Change-data-capture connector that tails a database's replication log and publishes row changes to Kafka.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @3

Debezium usually runs as a source connector inside Kafka Connect, reading PostgreSQL's write-ahead log through logical decoding, MySQL's binlog, or MongoDB's change stream, and emitting one message per row change carrying the before and after images plus the operation type. It snapshots existing table contents first and then switches to streaming, so a consumer built from scratch sees complete state rather than only changes since it started.

This is the standard mechanism behind the transactional outbox — write the business row and an outbox row in one local transaction, let Debezium publish it — and behind keeping search indexes, caches and analytics warehouses in step without dual writes. The operational hazard is on the database side: a stopped connector means an unconsumed replication slot, and Postgres will retain WAL indefinitely for it until the primary's disk fills.

### Debezium Engine
**Short:** Embeddable library that runs a Debezium connector inside your own JVM and hands each change event to a callback.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1

The `debezium-embedded` artifact hosts the same connector code Kafka Connect would run, minus Connect itself: you configure a connector, supply a change consumer, and the engine drives snapshot and streaming in your process. `AsyncEmbeddedEngine` has been the only implementation since 3.2, replacing the older single-threaded engine. Offsets and, where the connector needs one, schema history are stored wherever you point them -- a file, a database table, Redis or Kafka.

Reach for it when the consumer is the application and a broker in the path would only add latency, or when you are building a product that embeds change capture. What you take on is everything Connect was doing for you: supervision, restart, offset durability and back-pressure are now your code's responsibility, and the default file-based offset store is not durable on ephemeral infrastructure.

### Debezium JDBC sink connector
**Short:** Kafka Connect sink that consumes Debezium change events and applies them to a relational target as upserts and deletes.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1

It reads either the full change envelope or an already-flattened record, derives the primary key from the message key, and writes an upsert for creates and updates and a delete for tombstones, so a source-to-relational-sink pipeline needs no third-party connector and no custom consumer. Table and column names can be mapped, schema evolution can create or alter the target table, and batching is configurable so a high-volume stream does not become one statement per row.

Reach for it when the destination is an ordinary SQL database and the transformation is nothing more than shape. Its insert mode and primary-key mode are the two settings that decide correctness under redelivery, and getting them wrong turns at-least-once delivery into duplicate rows rather than idempotent writes.

### Debezium MongoDB connector
**Short:** Kafka Connect source that turns MongoDB change streams into an ordered stream of change-data-capture events.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/document @3

MongoDB exposes no write-ahead log to outside readers, so this connector opens a change stream -- the server-side resumable feed built on the oplog -- and converts each change into a Kafka record keyed by the document id. It snapshots the collections first and then switches to streaming, and it stores the change stream's resume token as its offset so a restarted connector continues rather than re-snapshotting. Full before-images require pre- and post-image capture enabled on the collection; without it an update event carries only the changed fields.

Reach for it to keep a search index, cache or warehouse in step with a collection, or to run a transactional outbox in a document store. The trap is oplog retention: if the connector is down longer than the oplog window the resume token expires, and recovery means a fresh snapshot of the entire collection while the topic backfills. Size the oplog for your worst realistic outage, not for normal operation.

### Debezium Operator
**Short:** Kubernetes operator that reconciles a DebeziumServer custom resource into a running Debezium Server deployment.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, platform-delivery/kubernetes-and-orchestration @2

The custom resource declares the source connector's configuration, the sink, the offset and schema-history storage, and the runtime image, and the operator materialises the deployment, config secret and volumes for it. That turns a Debezium Server pipeline into a GitOps artifact instead of a properties file somebody copied onto a node, and it is the piece Debezium Platform drives underneath its UI.

Reach for it when you run Debezium Server on Kubernetes and want the pipeline declared alongside the rest of your manifests. It does not manage Kafka Connect -- a Connect-hosted Debezium is Strimzi's territory -- and the operator does not absolve you of choosing a durable offset store, which is still the most common way a Debezium Server pipeline loses its position.

### Debezium Platform
**Short:** Web UI and control layer for Debezium Server pipelines, built as a Conductor backend plus a Stage front end.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, platform-delivery/kubernetes-and-orchestration @3

Conductor holds pipeline definitions -- source, transforms, sink -- and drives the Debezium Operator to run them, while Stage is the browser interface for authoring and watching them. It replaced the `debezium-ui` project, whose repository was archived in September 2025, and it is the project's answer to managing capture pipelines without hand-editing properties files.

The expectation to correct before deploying it: it manages Debezium Server, not Kafka Connect. Teams running Debezium as a Connect connector reach for it as "the Debezium UI" and find it drives a different runtime entirely; for Connect the management surface remains the Connect REST API and whatever console the Kafka distribution ships.

### Debezium Quarkus Outbox Extension
**Short:** Quarkus extension that writes and immediately deletes a transactional outbox row when your code fires a CDI event.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, apis-frameworks/web-framework-and-http-client @3

You fire a CDI event implementing `ExportedEvent` and the extension inserts the corresponding row into the outbox table inside the current transaction, then deletes it in the same transaction. The delete is the clever part: the row exists only long enough for the write-ahead log to record it, so change capture sees the insert while the table itself stays empty and never needs a janitor.

Reach for it in Quarkus services already using the outbox pattern with Debezium's EventRouter transform on the read side, since the two are designed against the same default column names. It requires Java 21, it assumes a JTA transaction is in scope, and it is Quarkus-specific -- Spring services implement the same shape by hand or with an outbox library of their own.

### Debezium Server
**Short:** Standalone Quarkus application that runs one Debezium connector and writes change events to a non-Kafka sink.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1

It wraps the embedded engine in a supervised process configured by a properties file, with sink adapters for Kinesis, Google Pub/Sub, Pulsar, Redis Streams, NATS JetStream, RabbitMQ, HTTP and others. That removes Kafka and Kafka Connect from the deployment entirely, which is the right trade when Kafka would exist for no reason other than to carry change data, and the wrong one when several independent consumers need to replay the same stream.

The failure to design against is the offset store: it defaults to a local file, so a container without a persistent volume loses its position on every reschedule and the connector re-snapshots. Configure a Redis or Kafka offset store, or mount a real volume. It requires Java 21, and supervision, restart and back-pressure are the operator's responsibility rather than a cluster's.

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

Tasks are actors -- decorated functions each with their own queue, retry policy and time limit -- and the broker is RabbitMQ or Redis. Delivery is at-least-once with the worker acknowledging only after the actor returns, so a crash redelivers; retries use exponential backoff and a message whose retries are exhausted goes to a dead-letter queue automatically instead of vanishing. Middleware is the extension point, and age limits, time limits, retries, callbacks and metrics are all implemented as middleware you can replace or extend.

Reach for it when you want Celery's job -- durable background work with retries -- with a much smaller configuration surface and defaults that already do the safe thing rather than needing to be discovered. The costs are a smaller ecosystem, fewer prebuilt integrations, and no equivalent of Celery's canvas for composing task graphs. Celery stays the answer where that ecosystem matters, and ARQ or taskiq where the codebase is asyncio throughout.

### Durable Functions
**Short:** Azure Functions extension for stateful serverless orchestration: fan-out/fan-in, timers and approval workflows in code.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

An orchestrator function calls activity functions and awaits them, and the extension replays that function from the top on every resumption, consulting an append-only history in Azure Storage to short-circuit the calls that already completed -- which is exactly why an orchestrator must be deterministic and must not read the clock, generate randomness or perform IO directly. Fan-out and fan-in, durable timers, waiting on an external event for human approval, and entity functions holding small addressable state all fall out of that one mechanism.

Reach for it when the code already lives in Azure Functions and you want durable orchestration without standing up an engine, since the state store is a storage account rather than a service to operate. The costs are the replay model's sharp edges, throughput bounded by the storage backend unless you move to an alternative provider, and a strong tie to one cloud. Temporal is the portable equivalent when that matters.

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

The connector consumes topic records and issues bulk index requests, deriving each document id from the record key so a repeated record overwrites rather than duplicates -- that idempotence is what turns Kafka's at-least-once delivery into an effectively exactly-once index. A null-valued tombstone becomes a delete, batch size and flush timeout govern bulk sizing, and records that fail can be dropped, logged or routed to a dead-letter topic instead of stalling the task forever.

Reach for it to maintain a search read model from an event stream: writes go to the system of record, changes flow through Kafka via change data capture, and the index is rebuildable by replaying the topic from the beginning. The costs are mapping and schema drift, which the connector cannot resolve for you, and bulk rejections when the cluster's write queue saturates, which surface as connector retries rather than as an obvious cluster alarm.

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

Events are JSON envelopes on a bus, and rules match them with content-based patterns over any field rather than just a topic name, then deliver to targets such as Lambda, Step Functions, SQS, Kinesis or an API destination, optionally transforming the payload first. AWS services publish their own events to the default bus, partner buses carry SaaS events, a schema registry can discover shapes and generate bindings, and the scheduler fires rules on cron or rate expressions. Delivery is at-least-once with retries and an optional dead-letter queue; ordering is not guaranteed.

Reach for it when routing decisions belong in configuration rather than consumer code, or when you want one place to react to events emitted by AWS itself. The limits are throughput and latency next to a real broker, per-event pricing at volume, and replay only through the archive feature. SNS is cheaper for plain fan-out, and Kafka or Kinesis is the answer when consumers need offsets and long retention.

### EventStoreDB
**Short:** Database purpose-built for event sourcing: the append-only event log is the system of record, plus snapshots.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, apis-frameworks/design-patterns-and-principles @2, data-stores/key-value-and-embedded @3

Writes append to a named stream, normally one per aggregate, and the expected-version check on append supplies optimistic concurrency without a lock. Every event also lands in a global ordered stream, and catch-up subscriptions read from a stored position then follow live, which is how read models are built and rebuilt after a projection change. Server-side projections can fold or re-partition streams, persistent subscriptions add competing consumers with per-event acknowledgement, and stream metadata controls truncation and retention.

Reach for it when the event log genuinely is the system of record and you want append semantics, subscriptions and per-stream concurrency as database features rather than assembled from a relational table plus a broker. The costs are another stateful system to operate and queries that must go through projected read models. Note that the product was renamed KurrentDB, so current documentation and releases appear under that name.

### Eventuate Tram
**Short:** Java framework implementing the saga and transactional-outbox patterns over a message broker.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2, data-movement/event-streaming-and-processing @3

It implements the transactional outbox in Java: your service writes its business rows and a message row inside the same local database transaction, and a separate relay — change-data-capture on the transaction log, or polling — publishes those message rows to Kafka or ActiveMQ. That removes the dual-write problem where a service commits to its database and then fails before publishing the event, leaving the rest of the system permanently out of step.

Eventuate Tram Sagas layers orchestrated sagas on top, with a saga definition that names each participant's command and its compensating command. Reach for it in Spring/JPA microservices that want correct event publishing without hand-rolling an outbox; if you already run Debezium for CDC or a durable workflow engine for orchestration, those cover the same ground.

### Fivetran
**Short:** Managed ELT service with prebuilt connectors that land SaaS and database sources into a warehouse on a schedule.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/data-quality-and-lineage @3

Connectors are fully managed: schema detection, incremental syncs, log-based change capture for the database sources, automatic handling of upstream schema changes, and dbt transformation runs on the loaded tables. The appeal is that nobody on your team maintains a connector, a replication slot or an offset store, which is most of the operational surface of a self-hosted capture pipeline.

The cost model is the thing to check before adopting it: pricing is per monthly active row, so a nightly batch that rewrites fifty million rows costs the same whether or not any value changed. Reach for it when the destination is one warehouse and the sources are long-tail SaaS APIs; a self-hosted Debezium pipeline stays cheaper and far more controllable for high-volume database replication with several consumers.

### FlatFileItemReader
**Short:** Spring Batch reader that streams CSV or fixed-width lines into domain objects with restartable read state.
**Kind:** api
**Lang:** java
**Roles:** data-movement/batch-and-distributed-compute @1, apis-frameworks/aop-middleware-and-scheduling @2

### Flink CDC
**Short:** Flink connector suite that embeds Debezium's capture code in a job, streaming database changes without Kafka Connect.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/batch-and-distributed-compute @3

The connectors run inside the Flink job rather than in a separate runtime, so a pipeline is one system instead of three: capture, transformation with full stream SQL, and the sink all live in the same checkpointed dataflow, and exactly-once to the sink comes from Flink's checkpoints plus a two-phase-commit sink rather than from Kafka transactions. Its pipeline connectors also handle whole-database synchronisation and schema evolution as a first-class feature.

Reach for it when Flink already exists and the shape is one source, some transformation and one sink. The moment a second consumer appears the calculus inverts: each job re-reads the source, which on PostgreSQL means another replication slot on the primary with its own write-ahead-log retention, and that is precisely what a durable log in the middle exists to avoid.

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

Composer runs Airflow on GKE inside your project: DAG files sync from a Cloud Storage bucket, the scheduler, web server and workers run as cluster workloads, logs and metrics land in Cloud Logging and Monitoring, and workers autoscale between bounds you set. Later generations hide most node management, and the environment's service account is what governs the pipeline's access to BigQuery, Dataflow, Pub/Sub and everything else in the project.

Reach for it when the pipeline mostly orchestrates Google Cloud services and you want Airflow's operators without owning the scheduler and its database. The costs are the familiar managed-Airflow ones: upgrades on the provider's cadence, PyPI dependency installs that can fail an environment update, and a bill that accrues whether or not any DAG runs. MWAA and Astronomer Astro are the equivalents, and self-hosting on Kubernetes is cheaper with a platform team.

### Google Cloud Datastream
**Short:** Google Cloud's serverless change-data-capture service, streaming from PostgreSQL, MySQL, Oracle and SQL Server into BigQuery or storage.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @3

It reads the source's own replication log, performs a backfill and then streams the delta, and writes into BigQuery or Cloud Storage with no infrastructure to size. Because it is managed, the replication slot, the offset store and the schema history -- the three objects that make self-hosted capture operationally demanding -- are the provider's problem rather than yours.

Reach for it when the destination is BigQuery, there is one consumer, and nobody on the team wants to be paged about a replication slot. It is point-to-point, so it gives you no durable fan-out to several independent consumers and little control over the event shape; that is the boundary at which a self-hosted pipeline over a log starts to pay for itself.

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

A task is a decorated function; enqueuing pushes it onto a Redis list -- or SQLite, or an in-memory queue for tests -- and a consumer process executes it with a worker type you choose between threads, greenlets and processes. Periodic tasks use a crontab decorator evaluated by the consumer itself, so there is no separate beat process to keep alive, and results, delayed execution, retries, task revocation and locking all fall out of the same small storage interface.

Reach for it when the requirement is genuinely modest -- a handful of background jobs in a Django or Flask app -- and Celery's configuration surface would be the largest thing in the repository; the SQLite backend even removes the broker. The limits are the flip side of that: a small ecosystem, no workflow composition, and monitoring you assemble yourself. Once jobs fan across machines with real routing, Celery or Dramatiq is the better fit.

### Inngest
**Short:** SaaS-first durable functions platform for TypeScript and Python: steps that survive restarts, retries and sleeps.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/task-queue-and-jobs @2

You write a function as a sequence of `step.run` calls, and each step's output is memoized by the platform, so a crash or a redeploy resumes at the first incomplete step instead of repeating side effects. Functions are triggered by events sent to the platform or by a cron expression, while sleeping, waiting for another event, concurrency limits, throttling and debouncing are declared rather than coded. Execution is HTTP-driven -- the service invokes your endpoint once per step -- which is what makes it work on serverless platforms with short request timeouts.

Reach for it when you want durable execution on a serverless deployment without operating a workflow cluster, and the language is TypeScript or Python. The costs are that your code runs behind a vendor's invocation loop, local development needs the dev server, and per-step pricing adds up at volume. Temporal is the self-hostable alternative when the workflow state must stay inside your own infrastructure.

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

Two pieces do most of the work: `Parallel` with `delayed` spreads a loop across threads or processes through a pluggable backend -- loky by default, which reuses a process pool and survives a crashed worker -- and `Memory` caches a function's return value on disk keyed by a hash of its arguments, so an expensive step is skipped on re-run. Its serialization is tuned for NumPy: large arrays are written outside the pickle stream and memory-mapped into workers instead of copied per task.

This is what `n_jobs` means inside scikit-learn, and the usual way a fitted estimator is written to a file. Reach for it for single-machine parallelism over independent work and for caching in a research loop. The costs are process startup and data transfer dominating small tasks, and a persisted model tied to the library versions that wrote it. Ray or Dask is the step up once the work has to span machines.

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

### ksqlDB
**Short:** Streaming SQL engine over Kafka that turns continuous queries into Kafka Streams topologies with no application code.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1

You declare streams and tables over topics and write persistent queries in SQL; the engine compiles each into a Kafka Streams topology, runs it in the ksqlDB server, and writes results back to a topic. Joins, windowed aggregations and stream-table enrichment are all expressible without a JVM project, and pull queries can read the materialised state directly for simple lookups.

It fits naturally over a change stream, where the usual job is joining a change-data-capture topic against a reference table and writing an enriched result. The limits arrive with complexity: it is Kafka-only, the SQL dialect covers less than Flink's, and a topology that outgrows the dialect has to be rewritten as a Kafka Streams application anyway, so choose it when the query really is the whole job.

### KurrentDB
**Short:** Purpose-built event store: append-only streams, optimistic concurrency and native subscriptions; formerly EventStoreDB.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/transactions-and-consistency @3, data-stores/key-value-and-embedded @3

Data is organized as append-only streams, usually one per aggregate, and an append carries an expected version so two concurrent writers cannot both succeed -- that check is the concurrency control an event-sourced aggregate needs. Everything also lands in a global ordered stream, and catch-up subscriptions read from a stored position and then follow live, which is how projections are built and rebuilt after a change. Persistent subscriptions add competing consumers with per-event acknowledgement and a parked-message queue.

Reach for it when the event log is the source of truth and you want stream-level concurrency, subscriptions and retention policy as database features rather than as code over a relational table plus a broker. The costs are another stateful system to operate and queries that must go through projected read models. Postgres with an events table and an outbox is the pragmatic alternative when event sourcing is only one part of the system.

### Marquez
**Short:** Open-source metadata and lineage server, the reference implementation of the vendor-neutral OpenLineage standard.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/ml-platform-and-pipelines @3

It receives OpenLineage run events over HTTP -- each naming a job, a run, and the input and output datasets -- and stores them in PostgreSQL as a versioned model of jobs, datasets and runs, so the lineage graph reflects what actually executed rather than what a static declaration claimed. Because job and dataset versions are tracked over time, you can ask which run produced a table's current contents, what the job's code and schema looked like then, and what downstream consumers a column change would break.

Reach for it when pipelines already emit OpenLineage -- integrations exist for Airflow, Spark, dbt and Flink -- and you want a self-hosted lineage service without adopting a full catalog product. Its scope is deliberately narrow: no business glossary, no quality checks, no access governance. DataHub, OpenMetadata or a commercial catalog is the answer when discovery and governance matter as much as lineage does.

### Materialize
**Short:** Streaming database that keeps SQL views incrementally up to date over Kafka and CDC feeds.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-stores/warehouse-and-olap @2, data-stores/relational @3

You define views in ordinary SQL over sources such as Kafka topics or Postgres change data capture, and rather than re-running the query the engine maintains the result incrementally: a change to one input row propagates through joins and aggregations to revise only the affected output rows. That comes from differential dataflow underneath, which is also why multi-way joins and recursion stay correct instead of being restricted the way most streaming SQL is. It speaks the PostgreSQL wire protocol, so results are read with a normal query or streamed with `SUBSCRIBE`.

Reach for it when a dashboard, an alert or a serving path needs a continuously fresh join across streams and you would rather write SQL than maintain a stream-processing job. The costs are memory, because maintained state lives in the cluster, and pricing that follows it. A warehouse refreshed every few minutes is far cheaper whenever seconds of staleness are acceptable, which is most of the time.

### Maxwell's Daemon
**Short:** MySQL binlog change-data-capture daemon emitting row changes as JSON to Kafka; simpler than Debezium, fewer connectors.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @3

It runs as a standalone Java process rather than inside a connector framework, which is the practical difference from Debezium: one artifact, a config file, and producers for Kafka, Kinesis, Pub/Sub, RabbitMQ, Redis or stdout. Row-based binary logging is a hard prerequisite on the source, and the account it connects with needs replication privileges plus a schema of its own, because it must track both its binlog position and each table's column layout in order to name fields in the JSON it emits.

Reach for it when you want change data capture out of MySQL with the smallest possible deployment and no cluster to operate. What you give up is the breadth around it: no schema registry, no single message transforms, no framework managing restarts, offsets and scaling for you, and exactly one source engine. Debezium on Kafka Connect is heavier but far more general once more than one database is in scope.

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

You create a cluster and topics and there are no brokers or partitions to size: capacity is allocated for you within per-partition and per-cluster throughput limits, storage grows without provisioning, and billing is by cluster-hour plus data written, read and stored. Authentication is IAM only, so SASL/SCRAM and mutual TLS are not options, and clients otherwise speak the ordinary Kafka protocol with unchanged libraries apart from the IAM callback handler.

Reach for it for spiky or unpredictable workloads where provisioned brokers would sit mostly idle, and for teams that want Kafka semantics without capacity planning at all. The costs are the constraints themselves: throughput ceilings you cannot raise by adding brokers, IAM-only authentication, and a price that overtakes provisioned MSK at steady high volume. Provisioned MSK is the answer for sustained throughput or broker-level configuration.

### Mule ESB
**Short:** Enterprise service bus and integration platform routing, transforming and mediating messages between systems.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, traffic-edge/api-gateway @2

An application is a set of flows: a source receives a message, and processors route, transform, enrich and dispatch it, with connectors covering HTTP, JMS, files, databases and SaaS APIs. The bus form buys a canonical message -- payload, attributes and variables travelling together -- while DataWeave expresses the mapping between formats and error handling is declared per flow with retry and until-successful scopes. Flows are authored visually or as XML and deployed to a runtime on premises or on the vendor's hosted plane.

Reach for it in integration-heavy enterprises where connecting many packaged and legacy systems is the actual job, and where governance, API management and vendor support are requirements rather than luxuries. The costs are licensing, DataWeave as a language a team must genuinely learn, and the general shift away from a central bus toward per-service integration. For a new microservice estate, Camel, Spring Integration or plain HTTP clients are much lighter.

### NATS JetStream
**Short:** NATS's persistence layer adding durable streams, replay and at-least-once consumers to its very fast pub/sub core.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/event-streaming-and-processing @2, data-stores/key-value-and-embedded @3

A stream captures messages published to a set of subjects and stores them in memory or on disk under a retention policy -- limits, interest, or work-queue -- and consumers are separate server-side objects with their own position, acknowledgement policy and redelivery timer, durable or ephemeral, pull or push. Because the consumer is state on the server rather than an offset in the client, a work-queue stream gives competing consumers while a limits stream gives every consumer its own replayable view. Streams replicate across servers via Raft.

Reach for it when you already run NATS for low-latency request-reply and now need durability, or when you want one small binary rather than a broker cluster plus its coordination layer -- the same subsystem also provides a key-value and object store. It is far less to operate than Kafka. Kafka is still the answer for very high sustained throughput, a mature connector ecosystem and long-term log retention.

### OpenLineage
**Short:** Vendor-neutral open standard for emitting data lineage events from pipelines and schedulers.
**Kind:** spec
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1

The standard defines a run event -- a JSON document naming a job, a run id, a state transition, and the input and output datasets -- plus facets, versioned extension objects that carry schema, column-level lineage, data-quality metrics or anything else a producer wants to attach without changing the core model. Integrations for Airflow, Spark, dbt, Flink and Dagster emit these events as jobs execute, so lineage is captured from actual runs rather than reverse-engineered from SQL after the fact.

Reach for it to avoid coupling pipelines to one catalog vendor: the same emitters work whether events land in Marquez, DataHub, OpenMetadata or a commercial platform, which makes the backend a replaceable decision. What a specification cannot give you is the product -- it collects events, it does not store, visualize or govern them -- and coverage depends on each integration's quality, with column-level lineage still uneven across producers.

### OpenLogReplicator
**Short:** Open-source C++ reader of Oracle redo logs that emits change events without LogMiner or a GoldenGate licence.
**Kind:** tech
**Lang:** cpp
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @3

It parses Oracle's redo log files directly, in a separate process, rather than mining them through the database's own LogMiner package -- which is what removes the CPU and I/O cost that LogMiner imposes on the source instance. Output can go to a network endpoint or a file, and Debezium's Oracle connector can drive it as the `OLR` adapter instead of LogMiner or XStream.

Reach for it when Oracle change capture is stalling on either of the two usual blockers: LogMiner is too expensive on a busy production instance, and XStream requires a GoldenGate licence nobody will buy. What you take on is a less-travelled path -- another process to deploy and monitor, and a much smaller community than the LogMiner adapter has.

### Oracle GoldenGate
**Short:** Oracle's commercial replication product, whose XStream API is one of the ways a change-capture connector reads Oracle.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-access/replication-ha-and-backup @2

It captures committed changes from the redo stream and applies them to heterogeneous targets, with its own trail files, extract and replicat processes and conflict handling, and it is the product Oracle shops reach for when replication has to be supported rather than assembled. Its XStream API exposes that capture to outside readers, which is how Debezium's Oracle connector can push changes rather than mine them.

The decisive fact is commercial rather than technical: XStream requires a GoldenGate licence, so an Oracle capture project frequently chooses LogMiner or OpenLogReplicator on cost grounds despite XStream being the lighter option for the source instance. Confirm the licence position before designing around it.

### Pachyderm
**Short:** Kubernetes-native data versioning and pipeline system giving every run content-addressed, reproducible lineage.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/ml-platform-and-pipelines @2, platform-delivery/kubernetes-and-orchestration @3

Data lives in versioned repositories with commits and branches, and a pipeline specification watches an input repository, runs a container over each new commit, and writes an output commit -- so every output is content-addressed and traceable to the exact input data and image that produced it. The glob pattern on the input decides the unit of parallelism: matching each file separately fans the job across workers, and incrementality follows because only changed datums are reprocessed. It runs on Kubernetes with object storage beneath the repositories.

Reach for it when provenance is the actual requirement -- regulated pipelines, or model training where which data produced which artifact must be answerable -- and the work is already containerized. The costs are a Kubernetes deployment, an unfamiliar data model, and a smaller community than the mainstream orchestrators. DVC covers versioning for a git-centric team, and Airflow or Dagster covers scheduling without the data layer underneath.

### pandas
**Short:** Python DataFrame library for tabular loading, joining, grouping and feature transformation in memory.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/batch-and-distributed-compute @1, runtime-systems/collections-and-algorithms @2, ml-lifecycle/ml-platform-and-pipelines @3

A DataFrame is a set of typed columns held as contiguous arrays with an index, so `groupby`, `merge`, `pivot` and window operations execute in compiled code over whole columns instead of a Python loop. That is the entire performance story: vectorized column operations are fast, and `iterrows` or a row-wise `apply` drops back into the interpreter and is orders of magnitude slower. Copy-versus-view semantics are the other recurring surprise, which is what the chained-assignment warnings are about, and Arrow-backed dtypes fix the old costs of object-dtype strings and nullable integers.

Reach for it whenever tabular data fits comfortably in memory, budgeting several times the data's size in RAM for the intermediates that joins and sorts create. Past that point Polars is faster on the same machine, DuckDB handles larger-than-memory queries in SQL without leaving the process, and Dask or Spark spread the same shaped work across a cluster at the cost of real scheduling overhead.

### pandas-profiling
**Short:** Automated EDA report over a DataFrame (now ydata-profiling): distributions, correlations, missingness.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/evaluation-and-benchmarks @3

Given a DataFrame it runs a full pass and renders an HTML report: per-column type inference, distributions and quantiles, missing-value patterns, cardinality and most frequent values, a correlation matrix across several coefficients, duplicate detection, and alerts for constant, highly correlated or heavily skewed columns. The point is that it replaces the twenty exploratory cells someone would otherwise write, and produces an artifact you can send to people who do not open notebooks.

Reach for it at the start of work on an unfamiliar dataset or as a snapshot attached to a data handoff. The cost is compute, because it is multi-pass and the correlation work grows quadratically with column count, so a large table should be sampled rather than profiled whole. The project was renamed ydata-profiling, so install and import under that name; for repeatable pipeline gates an expectation suite or a schema is the right tool instead.

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

Airflow's core ships the scheduler, the DAG model and the executor machinery, while everything that talks to an outside system -- the Amazon, Google, Postgres, Snowflake, dbt, Kubernetes and HTTP integrations among hundreds -- lives in separately versioned `apache-airflow-providers-*` distributions. Each contributes operators, hooks, sensors, transfer operators, connection types and sometimes UI components, discovered through an entry point at startup. Because they release on their own cadence, a fix for one integration does not wait for a core release and can be pinned independently.

The practical consequence is dependency management: providers pull real client libraries, so an environment installing many of them accumulates conflicting version constraints, which is the usual reason a managed Airflow environment refuses to update. Install only the providers actually used, pin them, and prefer running work in a container or pod operator when an integration's dependency set would otherwise poison the scheduler's own environment.

### Ra
**Short:** The Erlang Raft implementation underneath RabbitMQ's quorum queues, streams and Khepri metadata store.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-access/transactions-and-consistency @2

A Raft library for the Erlang VM, written by the RabbitMQ team, providing leader election, log replication and membership changes for many state machines inside one node. Quorum queues, streams and the Khepri metadata store are each a Ra state machine, which is why they share a durability story and a majority requirement.

You never configure Ra as a component, but its write-ahead-log settings are the knobs behind quorum-queue disk behaviour, and its majority rule is why an even-node cluster buys nothing over the odd number below it and why stretching a cluster across regions puts inter-region latency inside every publisher confirm. When quorum-queue memory or disk use surprises you, Ra's log is the thing you are actually looking at.

### RabbitMQ
**Short:** AMQP broker with exchange-based routing and durable queues; common Celery/Dramatiq backend and STOMP relay.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @2, apis-frameworks/rpc-graphql-and-streaming @3

RabbitMQ is an AMQP 0-9-1 broker. Producers never name a queue; they publish to an exchange, and bindings decide which queues receive a copy — direct on an exact routing key, topic on a pattern, fanout to everything bound. Routing therefore lives in broker configuration and can change without redeploying producers. Queues are durable, each message is delivered to one consumer, and an unacknowledged message is requeued when that consumer dies.

Classic and quorum queues are a work-queue model, not a log: once acknowledged a message is gone and there are no consumer-owned offsets to rewind. Its third queue type, streams, IS a log — append-only, Raft-replicated, non-destructive, addressable by offset or timestamp — so the line between it and Kafka is ecosystem and per-message cost rather than replay. Reach for it for task distribution and complex routing — it is the common Celery and Dramatiq backend, and its STOMP plugin makes it the external relay behind Spring's WebSocket messaging — and note that quorum queues, which replicate through Raft, are now the only replicated queue type rather than an upgrade, because classic queue mirroring was removed in 4.0.

### RabbitMQ STOMP plugin
**Short:** RabbitMQ plugin exposing STOMP, letting browsers and app servers use the broker as a production relay.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, apis-frameworks/rpc-graphql-and-streaming @2

Enabling the plugin adds a STOMP listener alongside AMQP and maps STOMP destinations onto the broker's own objects: a queue destination is a durable queue, an exchange destination publishes with a routing key, a topic destination goes through the topic exchange, and temporary destinations support reply-to. Subscriptions carry an acknowledgement mode, so a client can acknowledge per message rather than relying on auto-ack, and a companion Web STOMP plugin exposes the same protocol over WebSocket so a browser can connect directly.

This matters because an application's built-in simple STOMP broker keeps subscriptions in the memory of one process, so a second instance never sees the first instance's subscribers and fan-out silently breaks the moment you scale out. Pointing the application at RabbitMQ as an external relay moves that state into the broker and makes horizontal scaling work. The cost is a broker in the path of every message and its own capacity to plan.

### rabbitmq-perf-test
**Short:** Official RabbitMQ load generator for measuring throughput and latency against your own hardware, payload and queue type.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, devtools/testing-and-mocking @2

A command-line tool that drives configurable producer and consumer load at a broker and reports send and receive rates with latency percentiles. Every variable that moves a RabbitMQ throughput number by an order of magnitude is a flag: message size, queue type, persistence, publisher confirms and the confirm window, consumer acknowledgement mode, prefetch, and the number of queues and clients.

It matters because published RabbitMQ throughput figures are close to meaningless without their conditions, and the most-quoted vendor comparison was run against classic mirrored queues, a queue type removed in 4.0. Reach for it before sizing a cluster, or before believing any number in a blog post. Run it from a machine other than the broker, or you are measuring your load generator.

### rabbitmqadmin v2
**Short:** Standalone Rust CLI over RabbitMQ's HTTP API, for scripting topology and exporting or importing definitions.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1, devtools/version-control-and-workbench @2

A single binary driving the management plugin's HTTP API: declaring exchanges, queues, bindings, users, vhosts and policies, and exporting or importing a whole definitions document. That makes broker topology something a pipeline can apply and diff rather than something a person assembles in the management UI.

It replaces the v1 Python script, whose download endpoint was removed in 4.3, so an automation job that fetched `rabbitmqadmin` from a running broker breaks on upgrade -- a small and easily missed piece of upgrade planning. It still needs the management plugin enabled and reachable, so it is not a substitute for `rabbitmqctl` on a node that is not fully up.

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

Airflow 3 replaced the Flask-and-Jinja rendered pages with a single-page React application served over the same REST API that external clients use, which is what removed the old plugin model's ability to inject server-rendered views. The gains are grid, graph and asset views that update without full page reloads, DAG-version awareness so a historical run is displayed against the code that actually ran it, and consistency between what the interface shows and what the API returns, because there is now one source rather than two.

For a team the change shows up in customization and upgrades: server-side view plugins written against the old application do not carry over, and anything that scraped or automated the old pages should target the REST API instead. For everyday use the difference is mostly layout, but it is worth knowing that rendering is now a client-side concern, so a stale view usually comes from browser caching rather than the server.

### Reactor Kafka
**Short:** Reactive Streams adapter over the Kafka client, giving backpressure-aware Flux producers and consumers.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, runtime-systems/concurrency-and-async @2

It wraps the Java clients in Project Reactor types: `KafkaReceiver.receive()` returns a `Flux` of records where each element carries a receiver offset you acknowledge explicitly, and `KafkaSender.send()` takes a `Flux` of producer records and emits results. Backpressure is the whole point -- demand from downstream operators throttles the underlying poll loop by pausing partitions, so a slow consumer stops pulling instead of accumulating records in memory. Transactions spanning consume and produce are exposed as sender operators.

Reach for it when a service is reactive end to end and blocking a listener thread would waste the point of that stack. The costs are real: reactive operators make error handling and offset ordering subtle, a mistake with concurrent processing commits offsets for work that has not finished, and stack traces through operator chains are hard to read. Spring Kafka's listener containers are simpler wherever blocking is acceptable.

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

`XADD` appends an entry with a monotonically increasing id, and readers either scan ranges with `XRANGE` or block on `XREAD`. Consumer groups add the queue semantics: `XREADGROUP` assigns each entry to exactly one member and records it in that group's pending entries list until `XACK`, so a crashed consumer's unacknowledged work stays visible in `XPENDING` and can be reassigned with `XCLAIM` or `XAUTOCLAIM`. Trimming with `MAXLEN` or `MINID` is what bounds memory, since the stream otherwise grows without limit.

Reach for it when Redis is already deployed and you need at-least-once delivery with replay and consumer groups, which is a substantial step up from Pub/Sub's fire-and-forget for no new infrastructure. The limits are Redis's limits: the stream lives in memory, durability depends on the persistence configuration, and there are no partitions, so one stream is one shard's throughput. Kafka is the answer at real streaming volume or long retention.

### Redpanda
**Short:** Kafka API-compatible streaming broker written in C++: no JVM, no ZooKeeper, thread-per-core, drop-in for Kafka clients.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

It reimplements the Kafka protocol in C++ on a thread-per-core architecture: each core owns its partitions, its memory and its share of the network and disk queues, so there is no shared state to lock and no garbage collector to pause. Raft is used directly for both partition replication and cluster metadata, which removes the separate coordination service, and writes go to disk rather than relying on the page cache, which makes tail latency more predictable. Existing Kafka clients, connectors and tooling work unchanged.

Reach for it when you want Kafka's API with fewer moving parts -- one binary per node, no JVM tuning -- and where p99 latency is a stated requirement rather than a preference. The costs are a smaller operational community and a licence that keeps some enterprise features closed, so the experience you can hire for is thinner. Kafka itself, especially managed, stays the safer default where ecosystem depth matters more than latency.

### Restate
**Short:** Durable execution runtime with durable promises and strong consistency; SDKs for TS, Python, Java, Go and Rust.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-movement/task-queue-and-jobs @3

Every handler invocation is journaled to a replicated log before it does anything, and each subsequent action -- a call to another handler, a sleep, an external promise, a state read or write -- is appended as an entry, so a crashed handler is re-executed against that journal and replays past completed steps instead of repeating their effects. Virtual objects give a keyed handler exclusive concurrency plus a small piece of consistent state, which removes the usual reason to reach for a distributed lock, and durable promises let one handler await a signal from another.

Reach for it when you want durable execution from a single self-hostable binary rather than a service plus a separate database cluster, and the SDKs' inverted control fits container and function deployments alike. It is younger than the alternatives, so tooling, migration guides and hard-won operational knowledge are thinner. Temporal is the mature choice where a large ecosystem and a long production track record outweigh simplicity of deployment.

### RQ
**Short:** Redis Queue: a minimal Python background-job library with prefork sync workers and simple retries.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1

Enqueuing pushes a job onto a Redis list; a worker pops it, forks a child process to execute it, and writes the result and status back to Redis under a TTL. The fork per job is deliberate -- a crashed or memory-hungry job cannot damage the worker -- and it is also why the worker is Unix-only and why throughput is bounded by process creation. Failed jobs land in a failed-job registry with the traceback preserved, and retries, scheduling and job dependencies exist but are deliberately minimal.

Reach for it when you want background jobs in a Python web application with almost no concepts to learn and Redis is already running. The limits are the design: one backend, no complex routing or workflow composition, modest throughput, no Windows support. Celery is where you go for multiple brokers, canvas workflows and a large ecosystem, Dramatiq for safer defaults, and ARQ or taskiq when the application is asyncio.

### Soda
**Short:** Data quality platform: declarative checks in SodaCL run against warehouse tables and fail the pipeline on breach.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/data-quality-and-lineage @1

Checks are written in SodaCL, a YAML dialect where each check names a dataset and an assertion -- row count within a range, missing or invalid values under a threshold, freshness inside a window, schema unchanged, or an arbitrary SQL metric compared against a bound. The work is pushed down to the warehouse as SQL rather than pulling data out, so validating a large table costs a query, and results come back as pass, warn or fail with failing rows sampled for triage.

Reach for it when the data already lives in a warehouse and you want quality gates a data team can write and review without touching pipeline code, since the same check file runs in CI, in an orchestrator step or on a schedule. The costs are warehouse compute for every scan and a hosted control plane if you want the collaboration features. Great Expectations goes deeper in Python, and dbt tests are simpler when dbt already owns the models.

### Spark SQL
**Short:** Spark's SQL and DataFrame engine with the Catalyst optimizer, for distributed queries over lake and warehouse data.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/batch-and-distributed-compute @1, data-stores/warehouse-and-olap @2

SQL text and DataFrame calls converge on the same logical plan, which Catalyst rewrites -- predicate pushdown, column pruning, constant folding, join reordering against catalog statistics -- before the planner picks physical operators and whole-stage code generation emits JVM bytecode for them. The choice that dominates performance is the join strategy: a broadcast hash join when one side fits in memory, otherwise a sort-merge join behind a shuffle, with adaptive query execution able to switch strategy and coalesce partitions once real shuffle sizes are known.

Reach for it whenever the work is expressible as a query over lake files or catalog tables, because the optimizer usually beats hand-written RDD code and the code is far shorter. The costs live in the shuffle: skewed join keys stall on a single task while the cluster idles, and a small-file layout destroys scan performance. For single-machine analytics, DuckDB gives the same SQL without a cluster at all.

### Spark Structured Streaming
**Short:** Spark's streaming API treating a stream as an unbounded table, with watermarks, windows and exactly-once sinks.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/batch-and-distributed-compute @2

The abstraction is that a stream is a table which keeps growing, and the query is re-evaluated over new rows while the engine keeps whatever state aggregations and joins require in a state store checkpointed to durable storage. That checkpoint, together with source offsets, is what gives end-to-end exactly-once against a replayable source and an idempotent sink. Execution is micro-batch by default with the trigger interval setting latency, and event-time watermarks bound how late data may arrive so state can eventually be evicted.

Reach for it when Spark is already the platform and the latency requirement is seconds rather than milliseconds; the strongest case is one codebase serving both the backfill and the live path over the same lakehouse tables. The costs are micro-batch latency and state-store behaviour under large keyed state, where Flink's continuous model does better. Never delete a checkpoint directory to unstick a job -- it holds the offsets and the state.

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

It is a control plane rather than a runtime: you register applications -- Boot jars or container images -- as sources, processors, sinks or tasks, compose them with a pipe-based DSL, and it deploys each one as a separate process on Kubernetes or Cloud Foundry, wiring the pipes to Kafka or RabbitMQ destinations underneath. Streams are long-running Spring Cloud Stream applications, tasks are finite Spring Batch or Spring Cloud Task jobs whose execution history it records, and a scheduler plus a composed-task runner cover DAGs of tasks.

Reach for it in a Spring shop that already builds Boot applications and wants a catalogue, a UI and a deployment story covering both streaming pipelines and batch jobs. The costs are the server, its database and the messaging middleware to operate, plus an abstraction that only fits Spring-shaped applications. Argo Workflows or Airflow is the general-purpose alternative once the steps are arbitrary containers.

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

A task is a Boot application that runs to completion, and the module's contribution is bookkeeping: at startup it writes a row recording the task name, arguments and start time into a task repository, and on exit it records the end time and exit code, so a finite job launched by a scheduler or a platform has a durable execution record. Task listeners hook that lifecycle, and the batch integration links a task execution to the Spring Batch job execution it ran, so the two histories join.

Reach for it when short-lived jobs run as their own processes -- a Kubernetes job, a platform task, a container per run -- and you need to answer whether a run happened and how it ended without inventing a status table. It is deliberately thin: no scheduling, no distribution, no retry logic of its own. Spring Batch supplies chunk processing and restartability, and a scheduler or Data Flow supplies the trigger.

### Spring Events module
**Short:** Spring Modulith event support: transactional application events with a publication registry for reliable handoff.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, apis-frameworks/design-patterns-and-principles @2, data-movement/message-broker @3

The building block is Spring's ordinary application event, published inside a transaction and consumed by a listener that runs after commit, which lets one module notify another without calling its beans directly. What Modulith adds is durability: before the transaction commits, the event and the identity of each interested listener are written to a publication table, and a row is deleted only when that listener completes, so a crash between commit and delivery leaves an incomplete publication that can be republished at startup or on demand.

Reach for it when modules inside one deployable must stay decoupled and an event must not be lost if the process dies mid-handling -- it is the transactional outbox applied to in-process listeners, and the same events can be externalized to Kafka or AMQP later without changing the publishers. The costs are a table in your operational schema and at-least-once semantics, so listeners must be idempotent. A real broker is still needed once consumers move out of process.

### Spring Integration
**Short:** Spring's enterprise integration framework: channels, adapters and transformers implementing EIP patterns.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/design-patterns-and-principles @2, data-movement/event-streaming-and-processing @3

The model is pipes and filters: messages carrying a payload and headers travel along channels between endpoints, and the endpoint types are the enterprise integration patterns -- transformer, filter, router, splitter, aggregator, service activator -- configured through a Java DSL, annotations or XML. Channel adapters connect the flow to files, FTP, JDBC, JMS, AMQP, HTTP and MQTT; a channel may be direct and synchronous or queue-backed and asynchronous; and a message store makes an aggregator's partial groups survive a restart.

Reach for it when a Spring application's real work is integration -- polling a directory, correlating related messages, mediating between two protocols -- and you want that expressed as a flow rather than nested service code. The costs are a large vocabulary to learn and error handling that lives on error channels instead of in a call stack, which is genuinely harder to follow. For plain broker consumption, Spring Kafka or Spring AMQP directly is far less machinery.

### Spring Kafka
**Short:** Spring's Kafka integration: KafkaTemplate producers, @KafkaListener containers, retries and dead-letter topics.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-movement/message-broker @2

`KafkaTemplate` wraps the producer with serialization, transactions and a result callback per send, while `@KafkaListener` runs on a listener container that owns the consumer, the poll loop and the threading model -- concurrency maps to consumer threads within the group, and the acknowledgement mode decides whether offsets commit automatically, per record, or under your control. Error handling is a defaulted chain: a failing record is retried with backoff by the container's error handler and then published to a dead-letter topic with the exception recorded in headers.

Reach for it in any Spring Boot service that produces or consumes Kafka, because the container solves what people get wrong by hand -- rebalance-safe offset commits, retries that do not stall the whole group, and non-blocking retry topics when they would. The trap is assuming an exception in a listener is handled: with no configured error handler and dead-letter topic, a poison record retries forever and its partition stops advancing.

### spring-amqp
**Short:** Spring's RabbitMQ integration: RabbitTemplate for publishing, @RabbitListener for consuming, retries and DLX.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, data-movement/task-queue-and-jobs @3

`RabbitTemplate` handles connections, channels and serialization for publishing and for synchronous request-reply over a reply queue, while `@RabbitListener` runs on a container that manages consumers, prefetch and acknowledgement mode. Topology is declarative: a `Queue`, `Exchange` and `Binding` defined as beans are created on the broker at startup, so it lives with the code. Retry is interceptor-based, a republishing recoverer or the broker's dead-letter exchange catches what still fails, and publisher confirms and returns are available when a lost publish is unacceptable.

Reach for it in any Spring service speaking AMQP, since the container's handling of prefetch, manual acknowledgement and container-level errors is what a hand-rolled client usually gets wrong. The thing to configure deliberately is failure: the default is to requeue on exception, which puts a poison message straight back at the head of the queue and spins a consumer at full speed until somebody notices the CPU graph.

### spring-cloud-starter-bus-amqp
**Short:** Spring Cloud Bus over RabbitMQ, broadcasting config-refresh and management events to every instance of a service.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/dependency-injection-and-config @2

Bus links every instance of every participating application to a shared AMQP topic exchange and turns a management endpoint call into a broadcast: posting to the bus refresh endpoint on one instance publishes a refresh event that all subscribers receive, causing each to re-bind its configuration properties and recreate refresh-scoped beans against the current configuration source. Events carry an originating service id and an optional destination pattern, so a refresh can be aimed at one application or one instance, and custom remote events ride the same channel.

Reach for it when a Config Server backs many instances and restarting them to pick up a property change is unacceptable. The costs are a broker sitting in the configuration path and a refresh that is not atomic, so the fleet is briefly split between old and new values. A git webhook into the Config Server's monitor endpoint automates the trigger; Kubernetes ConfigMap reloading or a feature-flag service covers the same need without a bus.

### spring-integration-core
**Short:** Spring's enterprise-integration engine: message channels, endpoints, routers and aggregators in-process.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/message-broker @1, apis-frameworks/aop-middleware-and-scheduling @2, apis-frameworks/design-patterns-and-principles @3

This is the engine without the transport adapters: the channel implementations -- direct, queue-backed, publish-subscribe, executor -- the endpoint types that consume from them, the message handler and message source contracts, and the Java DSL that assembles them into a flow. A direct channel invokes the handler on the caller's thread inside the caller's transaction; swapping in a queue or executor channel changes the concurrency and the transaction boundary of the whole flow, which is the most consequential decision and the easiest one to make by accident.

Reach for the core alone when the endpoints are your own code -- routing, splitting, aggregating and enriching in process -- and no external transport is involved, so you avoid pulling in adapter dependencies you never use. The costs are a real learning curve and stack traces that thread through channel and handler infrastructure. Plain method calls or an in-process event publisher are clearer when there is no correlation, buffering or routing to do.

### spring-modulith-events-jpa
**Short:** Persists published application events in a JPA event registry so they survive a crash and are republished.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/event-streaming-and-processing @1, data-access/transactions-and-consistency @2, data-movement/workflow-and-durable-execution @3

The starter supplies the JPA implementation of the event publication registry: a table holding the serialized event, the target listener's identifier, and the publication and completion timestamps. A row is inserted in the same transaction that publishes the event and marked complete only when that listener returns, so any row with no completion timestamp after a restart is a delivery that never finished. Republication can run at startup or on a schedule, and completed rows are purged under a configurable retention so the table does not grow unbounded.

Reach for it when application events must survive a crash and the application already uses JPA -- it is the transactional outbox applied to in-process listeners, with no broker required. The costs are a table in your operational schema, serialized payloads that must stay deserializable across deploys, and at-least-once redelivery that makes listener idempotency mandatory. JDBC and MongoDB variants exist for stacks that are not JPA.

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

Airflow 3 separates task execution from the metadata database: task code runs inside a process built on the task SDK that talks to the Task Execution API over HTTP, so a worker no longer holds database credentials and no longer imports the whole scheduler codebase. Variables, connections and cross-task values become API calls, heartbeats and state transitions travel the same way, and the DAG author's import surface narrows to a stable, separately versioned package rather than Airflow internals.

What this buys is isolation and reach: tasks can run on remote or less-trusted workers, in a different network, or eventually in another language once a client exists, and a task's dependency set stops colliding with the scheduler's. It also closes the oldest Airflow security hole, which was that any task could write directly to the metadata database. The cost is a migration for code that reached into internals or the ORM session.

### taskiq
**Short:** Async-native distributed task queue for Python - a Celery alternative on asyncio with pluggable brokers/backends.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/task-queue-and-jobs @1, runtime-systems/concurrency-and-async @2

The design mirrors ASGI's separation of concerns: a broker sends and receives task messages, a result backend stores return values, and both are pluggable across NATS, RabbitMQ, Redis and Kafka, while the worker awaits tasks on the event loop rather than forking a process per job. Tasks are declared with a decorator and invoked with a kick method that returns a handle you can await, middleware wraps execution for retries, metrics and logging, and dependency injection borrows the `Depends` style familiar from FastAPI.

Reach for it when a service is asyncio end to end and you want Celery's shape -- durable distributed tasks with a broker and a result store -- without a synchronous worker model bolted onto async code. The costs are a young ecosystem and fewer battle-tested integrations. Celery remains the answer for CPU-bound prefork parallelism and breadth of tooling, and ARQ is smaller still when Redis alone is enough.

### Temporal
**Short:** Durable execution engine: workflows written as code survive restarts, with built-in retries, timeouts and sagas.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, data-access/transactions-and-consistency @2, data-movement/task-queue-and-jobs @3

Workflow code is ordinary Go, Java, Python or TypeScript, but every step's result is persisted to an event history, so when a worker dies the workflow is replayed against that history on another worker and continues exactly where it stopped — which is why sleeping for thirty days is a legitimate line of code. Side effects live in activities, retried independently under a policy you set, and a saga's compensation is simply the code you write after a failure rather than an orchestration DSL.

The determinism requirement is the thing to internalize: workflow code must not read a clock, generate a random number or call a service directly, because replay has to reproduce the same decisions. First-class SDKs cover Go, Java, Python, TypeScript and .NET, and the MIT-licensed server can be self-hosted on Cassandra or PostgreSQL or consumed as the vendor-hosted Temporal Cloud.

Reach for it when state must outlive a process — multi-day approvals, payment sagas, provisioning flows, long-running agents — and not for work that finishes inside one HTTP request. The costs are real: a stateful tier to operate or a per-Action bill, and versioning workflow code carefully so a deploy does not break replay of histories still in flight.

### Temporal child workflows
**Short:** Temporal feature spawning independently-scheduled durable sub-workflows for long-running parallel work.
**Kind:** api
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, llm-apps/agent-framework @3

### temporal CLI
**Short:** Single-binary Temporal command line that runs the dev server and drives namespaces, executions, schedules and worker deployments.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, devtools/version-control-and-workbench @3

One binary covers the whole operational surface: `temporal server start-dev` boots a full local Service with a Web UI and a SQLite or in-memory store, and the same command starts workflows, sends signals, runs queries and updates, describes and shows event histories, and cancels, terminates or resets executions. Administrative subcommands manage namespaces, retention, custom search attributes, schedules with backfill, and worker deployment versions with ramping.

Batch operations take a visibility List Filter rather than a list of ids, which is how a bad rollout is terminated or reset in one command. It supersedes the older `tctl`, so new tooling and runbooks should be written against this binary.

### Temporal Cloud
**Short:** Vendor-hosted Temporal Service: no persistence fleet or Elasticsearch to run, with managed multi-region HA namespaces, billed per Action.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

It removes exactly the parts of Temporal that are unpleasant to operate: the Cassandra or PostgreSQL fleet, the Elasticsearch cluster behind advanced visibility, server upgrades and schema migrations, plus managed RBAC, SSO, audit logging, per-namespace export of closed histories, and high-availability namespaces with cross-region failover. What it does not take is your workers, because the Service never runs user code in any deployment shape.

Billing is primarily per Action, roughly one state transition, plus storage measured in GB-hours and a support tier, so chatty polling loops and long retention windows are the two things that dominate a bill. Because the server is MIT licensed, moving between Cloud and self-hosted is a client configuration change plus a history migration rather than a rewrite.

### Temporal Go SDK
**Short:** Temporal's reference SDK for Go, with the richest worker tuning surface and determinism enforced only by the replayer and a static analyzer.
**Kind:** tech
**Lang:** go
**Roles:** data-movement/workflow-and-durable-execution @1

Workflows are ordinary functions taking a `workflow.Context`, concurrency uses `workflow.Go` and `Selector` rather than raw goroutines and `select`, and activities are registered on a worker bound to a task queue. Because the server itself is written in Go, this SDK usually receives new capabilities first, and it exposes the deepest worker options including slot suppliers, resource-based tuners and automatic poller autoscaling.

The catch is enforcement: nothing at runtime stops workflow code calling `time.Now`, iterating a map, or opening a socket, so the `workflowcheck` static analyzer and replay tests against exported production histories are the safety net rather than a nicety.

### Temporal Java SDK
**Short:** Temporal's Java SDK, with a workflow deadlock detector, a Saga compensation helper and stable virtual threads on JVM 21 and above.
**Kind:** tech
**Lang:** java
**Roles:** data-movement/workflow-and-durable-execution @1

Workflows are interfaces annotated with `@WorkflowMethod`, `@SignalMethod`, `@QueryMethod` and `@UpdateMethod`, implemented by a class the worker instantiates per execution, with activities reached through typed stubs carrying their own `ActivityOptions`. `Async.function` and `Promise` supply structured concurrency inside a workflow, and a deadlock detector kills any workflow task that blocks the SDK's scheduler thread.

The built-in `Saga` helper is the closest thing in any SDK to a first-class compensation primitive, accumulating undo steps as forward steps succeed and unwinding them newest-first on failure. Sticky cache sizing is a Java-specific concern because the default cached workflow count is far smaller than Go's.

### Temporal Nexus
**Short:** Temporal's cross-namespace call boundary: Endpoints expose named Operations another team's workflows invoke like activities.
**Kind:** api
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1

### Temporal Python SDK
**Short:** Temporal's Python SDK, whose workflow sandbox gives the strongest determinism enforcement of any Temporal SDK.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/workflow-and-durable-execution @1

Workflows are classes decorated with `@workflow.defn` whose `@workflow.run` coroutine executes on a deterministic asyncio event loop, so `await` on an activity, a timer or a condition is the whole concurrency model. Signals, queries and updates are decorated methods, and an update may declare a validator that rejects a call without writing anything to history.

The distinguishing feature is the sandbox: workflow modules are re-imported into a restricted environment where non-deterministic standard library calls are blocked at runtime, with `workflow.unsafe.imports_passed_through()` as the escape hatch for known-safe third-party imports. That makes an accidental clock read a loud failure in development rather than a silent replay bug months later.

### Temporal Schedules
**Short:** Temporal's first-class scheduling resource, supporting pause, trigger, backfill and overlap policies unlike a plain cron workflow.
**Kind:** api
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1

### Temporal TypeScript SDK
**Short:** Temporal's TypeScript SDK, running workflow code in an isolated bundle where Node I/O is unreachable rather than merely discouraged.
**Kind:** tech
**Lang:** js
**Roles:** data-movement/workflow-and-durable-execution @1

Workflow code is bundled separately from activity code and executed inside an isolated context with a deterministic clock, a deterministic `Math.random` and no access to Node built-ins, so the usual determinism violations are structurally impossible instead of caught after the fact. Activities are reached through `proxyActivities`, which returns a typed proxy whose calls become durable activity invocations.

The bundling step is the thing to plan for: workflow modules cannot import anything that touches the network or the filesystem, so shared code between workflows and activities has to be factored with that boundary in mind.

### Temporal Web UI
**Short:** Temporal's execution browser: event histories as a timeline, pending activities with attempt counts, visibility search, and cancel or reset controls.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, devtools/version-control-and-workbench @3

It is where a stuck execution is actually diagnosed: the event history renders as an ordered timeline, pending activities show their attempt number, last failure and next retry time, and the input and result payloads of every step are readable inline. Visibility search over predefined and custom search attributes drives the list view, and the same List Filter powers batch operations.

Two operational notes. With a payload codec configured the UI shows opaque ciphertext unless you also run a codec server that decrypts under your own authorization rules, and the cancel, terminate and reset buttons carry very different consequences, since only cancellation gives workflow code the chance to run its compensation.

### Temporal Worker Deployments
**Short:** Temporal's GA versioning mechanism pairing a deployment name with a build id, so executions pin to the version they started on.
**Kind:** api
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1

### TensorFlow Data Validation
**Short:** TFX library that infers a data schema and detects skew, drift and anomalies in training and serving data.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1

It computes statistics over a dataset with Apache Beam, so the same code runs on a laptop or on a distributed runner, then infers a schema -- expected features, types, domains, value counts, presence -- that you review once and thereafter treat as a contract. Validating a new batch against that schema yields typed anomalies: an unexpected feature, a missing one, an out-of-domain categorical value, a changed type. Comparing two statistics sets detects training-serving skew and drift between spans using distance measures rather than eyeballed charts.

Reach for it inside a TFX pipeline, where the statistics and schema components make this the standard gate before training, though it also stands alone over a DataFrame or a set of TFRecords. The costs are the Beam dependency, a schema that has to be curated as features evolve, and a protobuf-shaped API that feels heavy outside TensorFlow. Great Expectations, Pandera or a drift-monitoring tool fit a non-TFX stack better.

### TFDV
**Short:** TensorFlow Data Validation: infers a data schema, then detects anomalies and training/serving skew in new batches.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/drift-and-production-monitoring @2

The workflow is three calls: generate statistics for a batch, infer or load a schema, and validate the batch against it. Skew and drift thresholds are configured on the schema itself rather than at call time -- an infinity-norm bound on a categorical feature, a divergence bound on a numeric one -- so the tolerance is versioned alongside the feature definition. The failure modes are worth separating: schema skew is a structural difference between training and serving data, feature skew is a value computed differently in the two paths, and distribution skew is the same pipeline seeing different data.

Reach for it when a training pipeline must fail loudly on a bad batch rather than quietly training on it, since a silently changed upstream column is the classic cause of a model that degrades with no alert firing. Its notebook visualizations are also the fastest way to see what actually changed. Budget for schema maintenance, and expect to relax inferred domains that were too tight on the first sample.

### VMware Tanzu RabbitMQ
**Short:** The commercial RabbitMQ distribution, and the route to support past a community series' end-of-life.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/message-broker @1

A commercially supported packaging of RabbitMQ with a longer support window than the open-source series, aimed at organisations that cannot upgrade on the community cadence.

The reason it enters a design conversation is rarely a feature: it is the answer to "our compliance window outlasts this release's community support". Check the specific version's support dates before treating a commercial distribution as a way to defer upgrading indefinitely, because a distribution several majors behind still misses the architectural changes -- the removal of classic queue mirroring, the move from Mnesia to Khepri -- that a later migration then has to absorb all at once.

### Workflows
**Short:** Serverless state-machine orchestrators (Google Cloud Workflows and peers) that survive restarts between steps.
**Kind:** tech
**Lang:** *
**Roles:** data-movement/workflow-and-durable-execution @1, platform-delivery/cloud-platform-and-cost @2

The pattern is a state machine written as a declarative document in YAML or JSON, where each step calls an HTTP endpoint or a cloud API, assigns the result to a variable, branches on it, or waits, and the service persists the position after every step so an execution survives infrastructure failure and can sleep without holding a process. Billing follows steps executed rather than time running, retry and error handling are declared per step, and the execution history doubles as the audit trail.

Reach for it to glue managed services together -- call an API, poll until something is ready, then trigger the next job -- where writing that as a long-running function would mean paying for idle time and handling restarts yourself. The costs are a definition language that is neither unit-testable nor portable, awkward expression syntax for anything computational, and no local execution. Real logic belongs in a function the workflow invokes, or in a code-first engine such as Temporal.

### ydata-profiling
**Short:** Generates an automated EDA report over a dataframe: distributions, correlations, missingness and leakage hints.
**Kind:** tech
**Lang:** python
**Roles:** data-movement/data-quality-and-lineage @1, ml-lifecycle/ml-platform-and-pipelines @3

One call over a dataframe produces an HTML or JSON report: inferred types, per-column distributions and quantiles, missing-value patterns, cardinality and duplicate detection, a correlation matrix across several coefficients, and alerts for constant, imbalanced, highly correlated or heavily skewed columns. A minimal mode drops the expensive pairwise computations for wide frames, and comparing two reports puts a training set beside a production sample, which is a quick drift check before anything more formal exists.

Reach for it at the start of work on an unfamiliar dataset and as an artifact attached to a data handoff, since it answers most first-hour questions without a notebook full of exploratory cells. The cost is compute: it is multi-pass and quadratic in column count for correlations, so a large table should be sampled. For repeatable gates inside a pipeline an expectation suite or a Pandera schema is the right tool -- profiling is exploration, not enforcement.
