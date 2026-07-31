# Databases — technology bank

<!-- tech-bank tier: data-stores -->

The 116 tools whose PRIMARY role — the first, best-weighted one — sits in
the **Databases** tier. A tool appears in exactly one shard and carries all
of its roles here, so Redis is filed under Caching and still declares its
key-value, rate-limiting, broker and semantic-cache roles.

Record format and the full rules: [tech_bank.md](tech_bank.md).

### @mastra/libsql
**Short:** Mastra's libSQL-backed vector and memory store for TypeScript agents.
**Kind:** tech
**Lang:** js
**Roles:** data-stores/vector-store @1, search-retrieval/ann-index-library @3

The package binds Mastra's storage, vector and memory interfaces to libSQL, the SQLite fork that adds a network protocol, so the same agent code runs against a local file in development and a hosted database in production without changing anything but a URL. Conversation threads, messages, workflow state and embeddings all land in one SQLite-shaped store, with similarity search served by libSQL's own vector index rather than a separate service.

Reach for it as the default when starting a TypeScript agent: there is nothing to provision, and the entire state of a run sits in a file you can open and inspect. Move off it when concurrency or corpus size grows, since SQLite still admits one writer at a time and its vector search is not built for millions of embeddings — the PostgreSQL adapter with pgvector is the usual next step.

### @mastra/pg
**Short:** Mastra's PostgreSQL adapter, using pgvector as the vector store and memory backend for TypeScript agents.
**Kind:** tech
**Lang:** js
**Roles:** data-stores/vector-store @1, search-retrieval/ann-index-library @3

It implements the same storage, memory and vector interfaces against PostgreSQL, keeping threads, messages and workflow state in ordinary tables and embeddings in a pgvector column, so an agent's whole runtime state lives in a database the rest of the stack already backs up, monitors and connects to. Similarity search becomes SQL with a metadata filter beside it, which is what makes per-user or per-tenant memory scoping straightforward.

Reach for it once an agent leaves the laptop: connection pooling, concurrent writers and real indexes come for free, and there is no second datastore to operate for vectors. The pgvector caveats apply — pick HNSW or IVFFlat deliberately and tune the recall dial — and for a very large corpus with heavy filtering a dedicated vector database still has more headroom.

### Aerospike
**Short:** Distributed real-time key-value store with a flash-optimized storage engine and sub-millisecond reads at large scale.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2, data-stores/document @3

Its distinguishing design is a hybrid memory model: the primary index lives entirely in RAM as a small fixed-size entry per record, while the data sits on SSD and is read through a direct large-block interface that bypasses the filesystem, so a lookup costs one index probe plus one device read. Records are distributed by a hash of the key into partitions across the cluster with no manual sharding, and the cluster rebalances itself as nodes join or leave.

Reach for it when the working set is far too large for RAM but you still need sub-millisecond reads at very high throughput — ad tech, real-time bidding, fraud scoring, feature serving. Against that: it is a commercial product with a community edition, the operational model is its own to learn, and if the data fits in memory or the throughput is modest, Redis or DynamoDB needs far less justification.

### aiobotocore
**Short:** Async botocore fork; non-blocking AWS SDK calls (S3, SQS, DynamoDB) from asyncio code.
**Kind:** tech
**Lang:** python
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @2

botocore builds and signs AWS requests and parses responses, but sends them over a blocking HTTP client. aiobotocore replaces that layer with aiohttp while keeping botocore's model-driven API generation, so every service and operation is still available and the calls look like boto3 with `await` and an async context manager around the client. The aioboto3 package layers boto3's higher-level resource interfaces on top of it.

Reach for it when an asyncio service makes many AWS calls — S3 uploads, SQS polling, DynamoDB reads — and pushing them onto a thread pool has become the bottleneck. The cost is coupling: it pins to particular botocore versions and can lag new SDK releases, so upgrades are less free than with boto3. For a handful of calls, running boto3 in an executor is simpler and perfectly adequate.

### Amazon Neptune
**Short:** AWS managed property-graph and RDF database queried with Gremlin, openCypher or SPARQL.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, platform-delivery/cloud-platform-and-cost @3

Neptune is AWS's managed graph database. One cluster stores both a property graph, queried with Gremlin or openCypher, and RDF triples queried with SPARQL, on a shared storage layer replicated across availability zones with a single writer and read replicas you can add for query throughput.

Reach for it when the questions are about relationships several hops deep — fraud rings, identity resolution, entitlement chains, recommendation neighbourhoods, knowledge graphs backing retrieval — where the equivalent SQL is a stack of self-joins that degrades with each hop. Pick one model and language per graph up front, since they are not interchangeable, and note there is no self-hosted Neptune, so this is an AWS commitment.

### Apache AGE
**Short:** PostgreSQL extension adding property-graph storage and openCypher queries alongside normal relational tables.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, data-stores/relational @3

AGE stores a graph as PostgreSQL tables for vertices and edges and adds a `cypher()` function whose argument is an openCypher query returning values you then project in a normal `SELECT`. So a traversal and a relational join can appear in one statement, inside one transaction, backed by one set of backups, roles and replication.

Reach for it when a mostly-relational application has a genuinely graph-shaped corner — an org hierarchy, a permission chain, a small knowledge graph — and standing up Neo4j for it is not worth the operational cost. Do not expect parity: traversal over deep or wide graphs is far behind a native store whose relationships are pointers, Cypher coverage is partial, and the extension has to be available in your managed service, which is not a given.

### Apache Druid
**Short:** Real-time OLAP datastore that ingests event streams and answers sub-second time-sliced aggregations.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-stores/time-series @2, data-movement/event-streaming-and-processing @3

Druid stores data as time-partitioned, columnar, compressed segments with bitmap indexes on dimension columns, and splits work across process roles: ingestion tasks make freshly arrived events queryable within seconds, historical processes serve older segments from local disk backed by deep storage, and brokers scatter a query across both and merge the answers. Optional rollup aggregates rows at ingest time, trading raw detail for a far smaller dataset.

Reach for it for interactive dashboards over event streams where queries always filter by time and group by a few dimensions — clickstream, ad analytics, operational telemetry. The costs are a multi-process cluster with its own metadata database and coordination service, awkwardness with joins and updates, and heavy overlap with ClickHouse, which delivers much of the same on a much smaller operational footprint.

### Apache Parquet on S3
**Short:** Columnar Parquet files kept in S3 as the offline store for training data, backfills and analytical scans.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, data-stores/warehouse-and-olap @2, ml-lifecycle/ml-platform-and-pipelines @3

This is the plain-files pattern underneath every lakehouse: columnar Parquet objects laid out in Hive-style partition prefixes, so a query engine lists only the prefixes it needs, reads only the columns the projection names, and skips row groups using the min and max statistics in each file's footer. Nothing coordinates the writers, so a directory is only ever as consistent as the job that last wrote to it.

Reach for it for the offline half of a system — training sets, feature backfills, analytical scans, archives — where compute is transient and storage should be cheap and engine-neutral. Its limits are exactly what table formats exist to fix: no atomic multi-file commit, no row-level update or delete, no schema enforcement, and a small-files problem that quietly destroys scan performance. Add Iceberg, Delta Lake or Hudi when those start to hurt.

### APOC
**Short:** Neo4j's standard procedure and function library: graph algorithms, import/export, refactoring and utilities.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1

The name stands for Awesome Procedures On Cypher: a library of several hundred stored procedures and functions installed into the database and invoked from Cypher. It covers what the language itself does not — loading JSON, CSV or JDBC results straight into the graph, batching a large write with `apoc.periodic.iterate` so one transaction does not exhaust the heap, refactoring nodes and relationships, date and text utilities, and running dynamically built queries.

In practice, imports and bulk refactors are what you install it for, and the periodic-iterate procedure alone is what lets a multi-million-node update finish. Note the split between a core library bundled with the database and extended procedures that must be enabled deliberately, and that a managed instance permits only a subset — check before designing a pipeline around a procedure your target cannot run.

### ArangoDB
**Short:** Multi-model database serving graph, document and key-value workloads through one AQL query language.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, data-stores/document @2, data-stores/key-value-and-embedded @3

Edges are ordinary documents living in edge collections with `_from` and `_to` fields, so one AQL query can filter documents, follow a variable-depth traversal, and join the results, instead of splitting that work across a document store and a graph store and stitching it in application code. It also supports full-text and geo indexes and can run as a cluster with sharded collections.

Reach for it when the workload genuinely mixes both shapes — entity records plus their relationships, fetched together on a request path. If the work is overwhelmingly graph traversal and algorithms, a dedicated graph database has deeper support; if it is overwhelmingly documents, PostgreSQL with JSONB or MongoDB is the more common and better-staffed operational choice. Multi-model saves a system, but only when you actually need both models.

### Aurora
**Short:** AWS managed MySQL/PostgreSQL-compatible database with a shared storage layer, fast replicas and auto failover.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/replication-ha-and-backup @2, platform-delivery/cloud-platform-and-cost @3

Aurora keeps the MySQL or PostgreSQL query engine and replaces everything below it. The engine writes only redo log records, which go to a storage fleet spread over six replicas in three availability zones; storage applies them to build pages, and a write is durable once a quorum acknowledges. There are no full-page writes, no replica separately replaying a log, and no checkpoint stalls, which is where the throughput advantage over the stock engines comes from.

Because every instance reads the same storage, replicas lag by tens of milliseconds and failover is a promotion rather than a rebuild. Reach for it when you want a familiar SQL dialect with durability and failover handled. The costs are I/O-based billing that surprises write-heavy workloads, engine versions that trail upstream, and total dependence on a storage layer you no longer control.

### Azure Cognitive Search
**Short:** Azure managed search service combining keyword, semantic and vector indexes; common RAG/memory backend.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/lexical-and-hybrid-search @2, search-retrieval/rag-and-document-processing @3

The service, since renamed Azure AI Search, indexes documents you push or that an indexer pulls from Blob Storage, SQL, Cosmos DB and others, optionally running a skillset — OCR, entity extraction, chunking, embedding — as part of ingestion. One index can hold both an inverted index for keyword scoring and HNSW vector fields, and a hybrid query fuses the two by reciprocal rank fusion, with an optional semantic reranker over the top results.

Reach for it in an Azure estate when you want ingestion, enrichment and retrieval as one managed thing rather than assembling a search engine, an embedding pipeline and a vector store yourself. Against that: tier limits on index size, replicas and vector dimensions shape the design early, cost scales with provisioned search units, and portability is nil next to running OpenSearch or Qdrant.

### Azure Disk CSI
**Short:** CSI driver that attaches Azure managed disks as ReadWriteOnce persistent volumes to pods.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

The driver implements the Container Storage Interface for Azure managed disks, so a `PersistentVolumeClaim` against one of its storage classes provisions a disk, attaches it to whichever node the pod is scheduled on, and formats and mounts it. Storage-class parameters map onto the disk SKU and performance tier, and resize and snapshots are supported through the standard Kubernetes objects.

The constraint that shapes everything is attachment: a managed disk attaches to one VM at a time and lives in one zone, so volumes are `ReadWriteOnce`, a pod cannot move to a node in another zone without its disk, and a Deployment backed by one is effectively single-replica. Use it for StatefulSet workloads that own their data — databases, brokers, caches — and Azure Files when several pods must share a filesystem.

### Azure SQL
**Short:** Microsoft's managed relational database service with built-in HA, automated backups and elastic scaling.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, platform-delivery/cloud-platform-and-cost @2, data-access/replication-ha-and-backup @3

It is the SQL Server engine run as a service, evergreen rather than versioned, in three shapes worth telling apart: a single database, an elastic pool where many small databases share capacity, and managed instance, which restores near-full instance-level compatibility including the agent and cross-database queries. Purchasing is by vCore or DTU, with a Business Critical tier that keeps local replicas for fast failover and a Hyperscale tier that separates compute from a page-server storage layer for very large databases and quick restores.

Reach for it when the application is already T-SQL and you want backups, patching, geo-replication and threat detection handled. The cost of the managed model is control: no OS access, some instance-level features only in managed instance, and a pricing structure where sizing the tier wrongly is expensive rather than merely slow.

### BigQuery
**Short:** Google Cloud serverless columnar data warehouse for analytics, offline feature/metric computation and cost analysis.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-movement/batch-and-distributed-compute @3, ml-lifecycle/ml-platform-and-pipelines @3, platform-delivery/cloud-platform-and-cost @3

Storage and compute are decoupled: tables live in a columnar format Google manages, and a query is handed to a worker pool with no cluster for you to size, patch or keep running. Because the on-demand model charges for bytes scanned, partitioning and clustering are not tuning niceties but the primary cost control — an unqualified scan of an unpartitioned table is a bill, not merely a slow query.

It is the natural home for the offline half of an ML system, computing features and metrics over full history, and for analytics on data already in Google Cloud. It is a poor fit for low-latency single-row lookups, which belong in an operational store.

### BIN_TO_UUID()
**Short:** MySQL function that packs a UUID string into BINARY(16), optionally byte-swapping v1 for index locality.
**Kind:** api
**Lang:** *
**Roles:** data-stores/relational @1, data-access/schema-and-migration @2

### Blob Storage
**Short:** Azure's object storage service for unstructured blobs, with hot, cool and archive access tiers.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @3

Objects are blobs inside containers inside a storage account, and the account is where the consequential choices live: the redundancy option, from locally redundant up to geo-redundant across regions, and the access tier — hot, cool, cold and archive — which trades storage price against retrieval price and, for archive, a rehydration delay measured in hours. Lifecycle rules move or delete blobs by age. Block blobs are the usual type, with append and page blobs for logs and virtual disks.

Reach for it as the Azure landing zone for backups, media, data-lake files and static content, with Data Lake Storage Gen2 adding a real hierarchical namespace for analytics. The usual object-store caveats hold: no in-place edit, paginated listing of a large container, and per-transaction costs that dominate when the objects are small.

### Cassandra
**Short:** Leaderless wide-column NoSQL store with LSM storage and tunable consistency; built for massive write throughput.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, data-access/replication-ha-and-backup @2, data-access/transactions-and-consistency @3, ml-lifecycle/ml-platform-and-pipelines @3

Cassandra has no leader. Every node is identical, rows are placed by hashing the partition key onto a token ring, and each partition's replicas live on the next nodes around that ring, so adding nodes adds both capacity and write throughput roughly linearly. Writes append to a commit log and a memtable and later flush to immutable SSTables, which is why writes are cheap and why reads may have to merge several SSTables and pay a compaction tax in the background.

Consistency is chosen per statement rather than per database: a consistency level of ONE is fast and may read stale data, QUORUM on both reads and writes gives you read-your-writes at higher latency. Reach for it for write-heavy, time-series and per-entity feed workloads whose access patterns you know in advance, because you design tables per query. Avoid it where you need ad-hoc queries, joins, or multi-partition transactions — lightweight transactions use Paxos and cost several round trips.

### Chroma
**Short:** Embedded, zero-setup vector database for local development and prototyping of RAG applications.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, data-stores/key-value-and-embedded @3

Chroma runs inside your process by default — a persistent client writes collections to a local directory, so a retrieval prototype needs no container, no cluster and no connection string. A collection stores documents, their embeddings and their metadata together and will call an embedding function for you on insert and query, which is why it is usually the first vector store anyone meets.

It also runs as a standalone server, but the moment you need sharding, high query concurrency or a genuine hybrid lexical-plus-vector score, move to a store built for that. Chroma's strength is that setup friction is near zero, not that it scales.

### Citus
**Short:** PostgreSQL extension that shards tables across worker nodes for multi-tenant and analytic scale-out.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/replication-ha-and-backup @2, data-stores/warehouse-and-olap @3

Citus turns one PostgreSQL node into the coordinator of a cluster. You declare a distribution column, and rows are hashed into shards — themselves ordinary tables — spread across worker nodes, while small lookup tables can be replicated to every worker as reference tables. The coordinator rewrites an incoming query into per-shard queries, pushes them down and merges the results, so a query filtering on the distribution column touches one shard while an analytical scan runs in parallel across all of them.

Reach for it for multi-tenant SaaS, where distributing by tenant id makes almost every query single-shard, and for parallel analytics over time-series data. The distribution column is the decision you cannot undo cheaply: queries and joins that omit it become cross-shard and slow, and unique constraints have to include it.

### ClickHouse
**Short:** Columnar OLAP database with MergeTree storage; very fast analytical scans over huge event, log and time-series tables.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-stores/time-series @2, observability/logging @3

MergeTree stores each column in its own compressed file, sorted by the table's `ORDER BY` key, with a sparse primary index that skips granules rather than pointing at rows; queries then run through a vectorized engine that processes blocks of columns at a time. The result is scans and aggregations over billions of rows in under a second on modest hardware, which is why it backs log search, product analytics, and observability stores.

The tradeoffs are all on the write side: inserts want to arrive in large batches (many small ones create parts faster than the background merge can consolidate them), and updates and deletes are asynchronous mutations that rewrite whole parts rather than row-level operations. Reach for it for append-heavy analytical workloads; keep OLTP — per-row updates, transactions, foreign keys — in PostgreSQL.

### Cloud SQL
**Short:** Google Cloud's managed MySQL/PostgreSQL/SQL Server service with automated backups, replicas and failover.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, platform-delivery/cloud-platform-and-cost @2, data-access/replication-ha-and-backup @3

Google's managed instances of MySQL, PostgreSQL and SQL Server: you choose a machine type and disk, and the service handles patching, backups, point-in-time recovery from the transaction log, read replicas and optional high availability with a synchronous standby in a second zone. Connections normally go through the Auth Proxy or a connector library, which handles IAM authentication and TLS so the instance needs no public address.

Reach for it when a Google Cloud workload wants a standard engine without operating it, and note the ceiling: it is one primary instance, so scale-out means read replicas or application-level sharding, not the storage-layer trick Aurora plays. AlloyDB is Google's higher tier for PostgreSQL, and Spanner is the differently shaped option when a single writer genuinely is not enough.

### CockroachDB
**Short:** Postgres-compatible distributed SQL database giving serializable ACID across geo-replicated Raft ranges.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

Data is split into ranges, each replicated by Raft across nodes, and transactions are serializable using MVCC and a transaction record, so readers never block writers. Because it speaks the PostgreSQL wire protocol, most drivers and ORMs work unchanged, and ranges can be pinned to regions for latency or data-residency rules while the cluster survives losing a whole region.

Reach for it when you need multi-region ACID and automatic failover without sharding the application yourself. The price is consensus latency: a transaction touching several ranges or regions pays extra round trips, so a workload of tiny hot writes to one key is slower than the single Postgres node it replaced.

### CouchDB
**Short:** AP document database with HTTP/JSON access and multi-master replication that resolves conflicts on read.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, data-access/replication-ha-and-backup @3

Documents are JSON accessed over plain HTTP, and every update creates a new revision identified by a `_rev` the client must present with its next write, which is how conflicting updates are detected. Replication is the defining feature: any database can replicate to any other, in either direction, incrementally, over that same HTTP API — so a laptop, a phone and a server hold peers rather than a primary and its copies. When two peers change the same document independently both revisions are kept, a deterministic winner is chosen so reads never fail, and the loser is retained for the application to resolve.

Reach for it for offline-first applications that sync, since PouchDB in the browser speaks the same protocol. Against it: querying is limited next to MongoDB or PostgreSQL, view builds are slow, and conflict resolution is work you must actually do.

### CSI snapshot controller
**Short:** Kubernetes controller implementing VolumeSnapshot: point-in-time snapshots and restores of CSI volumes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, data-access/replication-ha-and-backup @2, platform-delivery/kubernetes-and-orchestration @2

It is a cluster-level controller deployed alongside the CSI drivers rather than inside them. It watches `VolumeSnapshot` and `VolumeSnapshotContent` objects, drives the driver's create-snapshot call, and binds the resulting provider-side snapshot back to the Kubernetes object. A `VolumeSnapshotClass` selects the driver and its parameters, and a new `PersistentVolumeClaim` can name a snapshot as its data source to restore into a fresh volume.

Reach for it as the primitive under any Kubernetes backup tool — Velero and the vendor operators call these APIs rather than inventing their own. Two limits to design around: a snapshot is crash-consistent, not application-consistent, so a database needs a hook to quiesce or flush before it is taken, and snapshots live inside the storage provider, so on their own they are not an off-cluster backup.

### DataStax Astra DB
**Short:** DataStax's managed Cassandra-as-a-service with serverless scaling, a REST/GraphQL data API and vector search.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, data-stores/vector-store @3, platform-delivery/cloud-platform-and-cost @3

It is Cassandra run as a service with the operational model inverted: no nodes to size, no repairs to schedule, capacity that scales with use and, on the serverless tier, scales to zero. Alongside CQL it exposes REST, GraphQL and document APIs plus a data API used by AI frameworks, and it supports vector columns with approximate nearest-neighbour search, which is how it is often used as a retrieval store rather than only a wide-column database.

Reach for it when the data model genuinely wants Cassandra's partition-key design and you would rather not run repairs, compaction tuning and node replacements. The tradeoffs are the usual managed ones — consumption billing that rewards efficient partition design and punishes scatter-gather queries, and a control plane you cannot self-host — while Cassandra's modelling discipline still applies in full.

### DataStax Studio
**Short:** Notebook-style GUI for developing and profiling CQL against Cassandra/DataStax clusters.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, devtools/version-control-and-workbench @3

### dbt
**Short:** Templated-SQL transformation tool that builds versioned warehouse models with tests, docs and lineage.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-movement/data-quality-and-lineage @2, ml-lifecycle/ml-platform-and-pipelines @3

A model is a `SELECT` statement in a file; dbt compiles the Jinja templating and `ref()` calls into real table names, derives the dependency graph from those references, and executes the models in order as create-table-as or create-view statements, or incrementally with a merge predicate. Because the graph comes from the code itself, lineage, documentation and a per-model test suite — uniqueness, not-null, accepted values, referential checks — all fall out of the same source.

It transforms inside the warehouse and does nothing else: extraction and loading are someone else's job and all compute belongs to the engine underneath. Reach for it to give analytics code what application code has had for years — version control, environments, tests, CI. The recurring failure is sprawl: hundreds of thin models nobody can prune.

### Delta Lake
**Short:** Lakehouse table format adding ACID transactions, schema enforcement and time travel on top of Parquet in object storage.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-movement/data-quality-and-lineage @2, data-stores/object-and-file-storage @3, ml-lifecycle/ml-platform-and-pipelines @3

A table is Parquet files plus a `_delta_log` of ordered commits; a reader replays the log to get a consistent snapshot and a writer commits atomically by appending a new log entry, which is how you get ACID semantics on object storage that offers no multi-file atomicity of its own. That same log is what enables time travel to an earlier version, `MERGE` upserts, schema enforcement and evolution, and the targeted deletes compliance work needs, none of which a bare directory of Parquet can do. Reach for it when concurrent writers, late-arriving corrections or reproducible training snapshots matter. Iceberg and Hudi solve the same problem, so the decision is usually which format your query engines and catalog support best.

### Dragonfly
**Short:** Redis-compatible multi-threaded in-memory data store with higher throughput and better memory efficiency per node.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2

Dragonfly reimplements the Redis and Memcached protocols on a multi-threaded, shared-nothing core: the keyspace is partitioned across CPU cores, each partition owned by one thread, so a single instance uses the whole machine instead of one core. That is the difference from Redis, whose single-threaded command loop means the answer to a CPU-bound instance is Redis Cluster and the operational cost of a sharded deployment.

Its internal hash table and snapshotting design also cut memory per key and avoid the fork-time memory spike a background save can cause. Reach for it when one cache node is CPU-bound and you would rather scale up than shard; before you do, check that the commands and modules you rely on are covered, and check that its BSL licence is acceptable for how you intend to run it.

### DynamoDB
**Short:** AWS fully managed key-value/document store with predictable latency, optional strong reads and serverless scaling.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-stores/document @2, data-access/transactions-and-consistency @3, platform-delivery/cloud-platform-and-cost @3

Access patterns are designed before the table, not after: the partition key decides which physical partition an item lives on, the sort key gives range and prefix queries within it, and any other lookup needs a secondary index or a full table scan. Throttling is per partition, so one hot key throttles while the table as a whole has headroom - which is why high-cardinality keys and write sharding matter more here than raw capacity numbers.

Reads are eventually consistent unless you ask for a strongly consistent one, and that option does not exist on a global secondary index. Conditional writes give idempotency and optimistic locking, TTL expires items without a delete job, and Streams emit an ordered change feed for CDC and materialized views. Reach for it for high-volume key-based lookups with predictable shapes and no servers to run; ad-hoc queries, joins and analytics belong somewhere else.

### EBS
**Short:** AWS Elastic Block Store: network-attached block volumes for EC2 and ReadWriteOnce Kubernetes persistent volumes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @3

A volume is network-attached but presented as a raw block device, so you put a filesystem on it and the instance treats it like a local disk, durable across stop and start and snapshottable for backup. The constraint that shapes designs is attachment: a volume lives in one Availability Zone and, outside Multi-Attach on the provisioned-IOPS types, is mounted by a single instance at a time. That is precisely why the Kubernetes CSI driver exposes it as `ReadWriteOnce` and why a Deployment backed by one EBS volume cannot scale past a single pod. Use it for databases and other single-writer stateful workloads, reach for EFS when several nodes must share a filesystem, and use S3 when the access pattern is really object storage.

### EFS
**Short:** AWS Elastic File System: managed NFS shared filesystem, the usual ReadWriteMany volume for pods.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @3

EFS is an NFS filesystem AWS operates for you: it grows and shrinks automatically, and many instances across several availability zones can mount it at once. That last property is why it is the usual answer for a Kubernetes ReadWriteMany volume, which EBS cannot provide since a block device attaches to one node.

The tradeoff is that every operation is a network round trip, so latency sits well above a local disk and metadata-heavy work — many small files, deep directory walks — feels it most. Use it for shared configuration, model files or uploaded assets several pods must see; never as a database's data directory, which wants block storage.

### Filestore
**Short:** Google Cloud managed NFS, used when workloads need a shared read-write-many filesystem instead of block volumes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @3

It is managed NFS: you provision an instance of a fixed capacity in a service tier, and it exports a share that many Compute Engine VMs or GKE pods mount at once. Performance scales with the provisioned capacity rather than being bought separately, which is why the standard advice is to size for throughput first and space second.

Reach for it when workloads genuinely need POSIX semantics on a shared mount: a `ReadWriteMany` volume for several pods, a shared model or asset directory, or lifting an application that expects a filesystem. It costs far more per gigabyte than object storage and is provisioned rather than elastic, so anything that is really object access — datasets, artifacts, backups — belongs in Cloud Storage, and a single writer is better served by a persistent disk.

### Firestore
**Short:** Google Cloud's serverless document database with real-time listeners and offline sync for mobile and web clients.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, platform-delivery/cloud-platform-and-cost @3, apis-frameworks/rpc-graphql-and-streaming @3

Documents live in collections and may nest subcollections, and every query is served by an index, which is the defining constraint: composite queries need a composite index declared in advance and there are no ad-hoc scans, so query cost tracks results returned rather than data stored. Client SDKs talk to it directly, with security rules — not your backend — deciding what each authenticated user may read or write, and a listener holds an open channel so a changed document is pushed to every subscriber.

The offline cache is the other half: a mobile client reads and writes locally and reconciles when the network returns. Reach for it for mobile and web applications where realtime and offline matter more than query flexibility. Against it: per-document read and write billing punishes fan-out designs, and reporting or aggregation belongs in a warehouse.

### FoundationDB
**Short:** Distributed ordered key-value store with strictly serializable ACID transactions, used as a substrate for higher layers.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

It is deliberately a small piece — an ordered key-value store with strictly serializable multi-key transactions and nothing else: no query language, no secondary indexes, no data model. Data is range-partitioned across storage servers, transactions are resolved optimistically by a dedicated conflict-detection layer, and a five-second transaction limit is a design constraint you build around rather than a bug to work past. Higher layers implement the models people actually want on top of that one guarantee.

The famous part is the testing: a deterministic simulation runs an entire cluster inside one process with injected disk, network and process failures, which is why its correctness reputation is what it is. Reach for it when you are building a database rather than using one; an application is better served by something that already has a query layer above it.

### GCE PD
**Short:** Google Compute Engine Persistent Disk: network block storage attached to one VM or pod (ReadWriteOnce).
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @3

A persistent disk is network-attached block storage with a lifetime independent of any VM, presented as a raw device, where the disk type decides the performance profile and IOPS scale with size on the standard types. Snapshots are incremental and can be restored into a new disk in another zone or region.

The attachment rule shapes designs the same way it does on other clouds: a zonal disk is mounted read-write by one instance at a time, so the Kubernetes CSI driver exposes it as `ReadWriteOnce` and a pod using one is pinned to that zone. Regional persistent disks replicate synchronously across two zones for failover at extra cost. Use it for databases and StatefulSet workloads, Filestore when several pods need one filesystem, and Cloud Storage when the access pattern is really objects.

### GCS
**Short:** Google Cloud Storage: managed object storage with buckets, storage classes and lifecycle policies.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @2

Objects sit in a flat namespace inside a bucket, with strong read-after-write consistency, optional versioning, and storage classes that trade a lower storage price for retrieval fees and minimum storage durations. A bucket is regional, dual-region or multi-region, which is a durability and latency decision made at creation time, and lifecycle rules handle tiering and expiry. Uniform bucket-level access with IAM is the modern permission model rather than per-object ACLs.

Reach for it as the Google Cloud landing zone for data lakes, backups, model artifacts and static assets fronted by a CDN. The usual object-store caveats hold: no in-place update, no cheap rename, per-operation cost that dominates when objects are small, and class-transition rules that can cost more than they save if the data is read after being tiered down.

### Gephi
**Short:** Desktop graph visualization and exploration tool for laying out, filtering and debugging network structure.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, applied-ml/recommenders-and-graph-ml @3

It is a desktop workbench rather than a database: you import an edge list, GraphML or a database extract, run a force-directed layout such as ForceAtlas2 to make the structure visible, compute metrics like degree, betweenness and modularity-based communities, and map those metrics onto node size and colour. Filters hide parts of the graph interactively, so you can isolate a subgraph without recomputing anything.

Reach for it when a human needs to understand a graph — checking whether a knowledge graph built from documents really has the communities you assumed, spotting a hairball caused by one hub node, or producing a figure. It is memory-bound and single-machine, so hundreds of thousands of nodes is a realistic ceiling; beyond that, compute the metrics in the database or a graph library and visualise a sample.

### Google Spanner
**Short:** Google's globally distributed SQL database giving external consistency via TrueTime and 2PC over Paxos.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3, platform-delivery/cloud-platform-and-cost @3

Data is range-partitioned into splits, each split a Paxos group replicated across zones or regions, so losing a replica costs no failover step. A transaction spanning splits is two-phase commit layered over those groups, which defuses the usual objection to 2PC — that a coordinator crash blocks participants indefinitely — because the coordinator's own state is replicated.

External consistency comes from TrueTime: the clock API returns an interval rather than an instant, and a transaction waits out that uncertainty before releasing its commit timestamp, so any transaction starting after a commit is guaranteed to observe it. Reach for it when you genuinely need global ACID with SQL and cannot shard by tenant; the cost is commit latency that grows with participant count and geographic spread.

### H2 Database
**Short:** Embeddable Java SQL database that runs in-memory or on disk, used mostly for fast integration tests.
**Kind:** tech
**Lang:** java
**Roles:** data-stores/relational @1, devtools/testing-and-mocking @2, data-stores/key-value-and-embedded @3

The same engine under its other common name. Beyond in-memory mode there is a file mode backed by its MVStore format, a server mode accepting TCP connections, and a built-in web console that is handy in development and a genuine risk if it is ever exposed. Compatibility modes tell it to imitate PostgreSQL, MySQL, Oracle and others, which improves the odds your SQL parses but does not make the semantics identical.

Choose it when you want a real SQL database with no process to run: a desktop application's store, an embedded appliance, or a fast test fixture. Do not choose it as a stand-in for production, because the compatibility modes cover syntax rather than behaviour under concurrency, and running the real engine in a container costs seconds and removes the whole class of bugs that only appear after the switch.

### HBase
**Short:** Hadoop-based wide-column store with strongly consistent row-level reads and writes over HDFS.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, data-access/transactions-and-consistency @3

It follows the Bigtable design: rows are sorted lexicographically by row key and split into regions, each served by exactly one RegionServer at a time, which is why a single row's reads and writes are strongly consistent while there are no multi-row transactions. Writes append to a write-ahead log and an in-memory store, flush to immutable HFiles on HDFS, and are merged by background compaction; reads may have to consult several files, which is what makes read latency variable.

Reach for it for very large, sparse tables that you query by key or by key range, with an existing Hadoop estate to run it on. The failure everyone hits at least once is a monotonically increasing row key such as a timestamp, which sends all writes to the last region and turns a distributed cluster into one hot server; salting or hashing the key prefix is the standard fix.

### InfluxDB
**Short:** Purpose-built time-series database for metrics and event data, with Telegraf ingestion and Flux/SQL querying.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/time-series @1, observability/metrics-and-monitoring @3

Data is written as a measurement with tags (indexed strings you filter and group by), fields (the numeric values), and a timestamp; retention policies drop old data automatically and downsampling tasks roll fine-grained points up into coarser summaries so long-range queries stay cheap. Telegraf is the companion agent that collects from hundreds of input plugins and writes into it, and depending on which major version you run, you query with InfluxQL, Flux, or SQL.

Cardinality is the failure mode to plan for: because tags are indexed, putting an unbounded value like a user ID, request ID, or full URL in a tag explodes the series count and the memory that goes with it — those belong in fields. Reach for it for metrics, sensor and IoT telemetry with a bounded tag space; high-cardinality event analytics is a job for a columnar store.

### JanusGraph
**Short:** Open-source distributed property-graph database speaking Gremlin over a Cassandra, HBase or Bigtable backend.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, data-stores/wide-column @3

JanusGraph is a graph layer rather than a storage engine: it implements the TinkerPop stack and Gremlin traversals over a pluggable backend such as Cassandra, ScyllaDB, HBase or Bigtable, encoding vertices, edges and properties as rows in that store, with an optional external index in Elasticsearch or Solr for full-text and range predicates. Its scaling is therefore the backend's scaling, and so is the operational burden.

Reach for it when a graph must be distributed across a cluster you already run and Gremlin is an acceptable query language. Understand the shape of the cost: each hop becomes round trips into the backing store, so it is far slower per hop than a native store with pointer-based adjacency, supernodes with millions of edges need explicit vertex-centric indexes, and you are now operating three systems rather than one.

### KeyDB
**Short:** Multi-threaded Redis fork with active replication, drop-in compatible with the Redis protocol.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2

KeyDB is a fork of Redis that made the command loop multi-threaded: several I/O and worker threads share the keyspace behind fine-grained locking, so one instance can use several cores instead of one. It also added active-active replication, where two nodes both accept writes and replicate to each other with last-writer-wins resolution, which stock Redis does not offer, and it stays wire-compatible so existing clients work unchanged.

Reach for it if a single cache node is CPU-bound and sharding is unattractive. Two cautions: active-active on a key-value store means silently discarded writes whenever the same key is touched in both places, so it suits partitioned or idempotent workloads only, and the fork's pace and its divergence from upstream are worth checking before adopting — Valkey and Dragonfly occupy the same niche.

### LangChain VectorStores
**Short:** LangChain's uniform interface over many vector databases so retrievers can swap backends.
**Kind:** api
**Lang:** python
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2, search-retrieval/ann-index-library @3

### LevelDB
**Short:** Google's embedded LSM-tree key-value storage engine, the ancestor of RocksDB.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1

LevelDB is an embedded library storing sorted string keys in an LSM tree: writes go to a write-ahead log and an in-memory memtable, which is flushed as an immutable sorted table file, and background compaction merges those files down through levels so a read consults a bounded number of them. You get ordered iteration and atomic batches, a single-process single-writer model, and no server, no query language and no secondary indexes.

Its main significance now is ancestry — RocksDB forked it and added the concurrency, tuning, column families and compaction strategies that serious embedded use needs, and it is RocksDB you find inside modern databases and stream processors. Reach for LevelDB only for something small and self-contained; anything with real write volume or a need to tune compaction should start from RocksDB.

### llama-index-vector-stores-pinecone
**Short:** LlamaIndex integration package binding its VectorStore interface to a Pinecone index.
**Kind:** tech
**Lang:** python
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2

LlamaIndex ships each integration as its own package, and this one implements the framework's vector-store interface against a Pinecone index. It maps a node's embedding, text and metadata onto a Pinecone vector with its id and metadata fields, translates LlamaIndex metadata filters into Pinecone's filter syntax, and handles batched upserts and top-k queries, so an index built over it behaves like any other to the retriever and query engine above.

Install it when the corpus already lives in Pinecone or you want retrieval without operating a database. What to watch is the boundary: filtering is limited to what the index stores as metadata, sparse or hybrid behaviour depends on the index type you created, and the framework version and the integration package have to move together, which is the usual source of breakage.

### LMDB
**Short:** Embedded memory-mapped key-value store using a copy-on-write B+tree; single-writer, lock-free readers.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1

LMDB memory-maps the whole database file and reads straight out of the page cache, so a read is a pointer dereference with no copy, no parsing and no lock: readers never block and never wait on the writer, because writes are copy-on-write on a B+tree and become visible only when a new root page is committed. That gives ACID transactions with one writer at a time, crash safety with no recovery step, and essentially no configuration or background threads.

Reach for it for read-dominated embedded data — a local index, a structured cache, a model or feature lookup table — which is why it turns up inside search and machine-learning tooling. Its constraints are firm: the map size must be set in advance and grown deliberately, a long-running read transaction pins old pages and bloats the file, and one writer means it is not a write-heavy store.

### Longhorn
**Short:** Software-defined block storage for Kubernetes providing replicated in-cluster persistent volumes and snapshots.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

Longhorn runs the storage layer inside the cluster: for each volume it starts an engine process on the node using it and places replicas as separate processes on other nodes, writing synchronously to all of them, so a volume survives losing a node and can be reattached elsewhere. Snapshots, scheduled backups to object storage or NFS, and volume expansion are exposed as Kubernetes objects and through a UI, and it is a CNCF project commonly paired with lightweight distributions.

Reach for it on bare metal or edge clusters where there is no cloud block-storage service to lean on and you want replicated persistent volumes with backups. The costs are honest: synchronous replication over the cluster network makes it slower than a local NVMe disk, it consumes CPU and memory on every node, and rebuilds after a node failure are heavy. In a public cloud, the provider's CSI driver is simpler.

### Memgraph
**Short:** In-memory property-graph database speaking Cypher, aimed at low-latency and streaming graph workloads.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, data-stores/key-value-and-embedded @3

Memgraph keeps the graph in memory with an on-disk write-ahead log and periodic snapshots for durability, so a traversal never touches storage, and it uses MVCC so reads do not block writers. It speaks Cypher and the Bolt protocol, which means Neo4j drivers and much of the tooling work against it, and it ships a query-module system where custom procedures can be written in C++ or Python — including a dynamic algorithms library that updates results such as PageRank or community labels as the graph changes rather than recomputing from scratch.

Reach for it when the graph fits in RAM and latency is the point: fraud checks inside a live transaction, network or dependency analysis over streaming updates. The limits follow from the design — memory sizes the graph — so for very large or archival graphs a disk-based or distributed store is the safer choice.

### Milvus
**Short:** Distributed, Kubernetes-native vector database built for billion-scale similarity search.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/ann-index-library @3

It separates storage from compute: data is persisted as segments in object storage, query nodes load and search them, and coordinator services handle sharding, index building and rebalancing, so capacity is added by scaling one layer rather than resharding by hand. It supports several index families with different memory and recall profiles, including graph indexes such as HNSW, quantized IVF variants and disk-resident indexes, plus filtering on scalar fields alongside the vector search.

Reach for it when the corpus no longer fits a single-node index or when you need multi-tenancy, replication and online index rebuilds. Below that scale the operational surface is real and a library index or a vector extension inside the database you already run is far less to look after.

### Milvus stores
**Short:** Spring AI's Milvus VectorStore binding, one of the interchangeable backends behind the VectorStore interface.
**Kind:** api
**Lang:** java
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @3

### MongoDB
**Short:** Document database storing BSON with flexible schema, rich queries, replica sets and multi-document ACID transactions.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, data-access/replication-ha-and-backup @3, data-access/transactions-and-consistency @3

Documents in a collection need no declared schema, so nested objects and arrays are stored and read as one unit instead of being spread across joined tables - which is the real win when an aggregate is fetched and written whole. Secondary indexes, the aggregation pipeline and `$lookup` cover most query needs, and schema validators are available where you do want the constraint.

A replica set gives automatic failover, with write concern and read preference as the knobs deciding how much recently acknowledged work a failover can lose: `w:"majority"` is the setting that makes durability claims meaningful. Sharding partitions a collection by shard key, and that key is the costliest decision in the design - a monotonically increasing key sends every insert to one shard. Reach for it when documents match the domain's natural units; a workload of many-entity joins and cross-row transactions is still happier in a relational database.

### MongoDB Atlas
**Short:** MongoDB's managed cloud service: sharded document clusters with backups, full-text search and vector search.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, data-access/replication-ha-and-backup @2, data-stores/vector-store @3, platform-delivery/cloud-platform-and-cost @3

Atlas provisions replica sets and sharded clusters on AWS, Azure or Google Cloud and owns everything around them: patching, continuous backup with point-in-time restore, monitoring with index and query advisors, private networking, encryption and IP access lists. Two features push it beyond hosting — Atlas Search embeds Lucene indexes next to the data for real full-text queries, and Atlas Vector Search adds approximate nearest-neighbour indexes so a document and its embedding live in the same collection under the same filter.

Reach for it when MongoDB is the right data model and running it yourself is not worth the staff; the free and serverless tiers make it the default for prototypes. Against it: cost climbs quickly with cluster tier and storage, tuning options are narrower than a self-managed deployment, and the search and vector features exist only here, so building on them is a commitment.

### MongoDB Compass
**Short:** Official MongoDB GUI for browsing collections, building queries, analyzing schemas and reading explain plans.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, observability/profiling-and-performance @2, devtools/version-control-and-workbench @3

Connect with a URI and you get collection browsing, an aggregation-pipeline builder that previews each stage's output, index listing and creation, and a schema view that samples documents to show which fields exist, their types and how often. The explain plan view is the payoff: it shows whether a query used an index or fell back to a collection scan, and how many documents it examined versus returned.

Reach for it when diagnosing a slow query, meeting an undocumented collection, or checking an index actually gets used before shipping. Routine and scripted work still belongs in `mongosh` or the driver.

### mongosh
**Short:** MongoDB's official shell: query, explain() plan inspection, index and schema analysis.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, observability/profiling-and-performance @2, devtools/version-control-and-workbench @3

The modern shell is a Node.js REPL with the full JavaScript language available, so a query, a loop over its cursor and a bulk write can be one script, and the same file runs non-interactively from CI. It replaced the legacy shell and adds syntax highlighting, completion and better error messages while keeping the same collection API the drivers mirror.

The reason to know it well is diagnosis. Running a query with execution statistics shows whether it used an index and how many documents were examined against how many were returned, the current-operation view finds what is blocking everything else, and index statistics reveal indexes that exist but are never used. Keep destructive one-liners out of production sessions: there is no undo, and an update without a filter reaches every document.

### mongostat
**Short:** MongoDB CLI printing a live per-second view of server ops, connections, queues and memory.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, observability/metrics-and-monitoring @2, observability/profiling-and-performance @2

It samples the server's status counters once a second and prints a fixed-width row per interval: inserts, queries, updates, deletes and cursor fetches, queued readers and writers, connection count, resident and virtual memory, replication state and network throughput. It is MongoDB's answer to `vmstat` — no history, no storage, just what the server is doing right now.

Reach for it in the first minute of an incident to tell the common shapes apart: a rising write queue points at lock or disk contention, a high cursor-fetch rate means something is churning through large scans, and a climbing connection count against flat operation counts means clients are piling up somewhere else. It answers what, not why, so follow it with the current-operation view and an execution-statistics run, and use a real metrics pipeline for anything you need to look at afterwards.

### MySQL
**Short:** The mainstream open-source ACID relational database, with InnoDB storage and binlog-based replication.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/replication-ha-and-backup @3

The 8 line is where MySQL closed most of the gaps people used to leave it over: window functions and common table expressions including recursive ones, a JSON type with a full function set and generated columns you can index, descending and functional indexes, an atomic transactional data dictionary that removed the old per-table metadata files, and four-byte UTF-8 as the default character set.

Operationally the notable changes are roles, invisible indexes that let you test dropping an index without dropping it, and the removal of the query cache, which was a scalability bottleneck rather than a help. The default isolation is still repeatable read with gap locking, which remains the most common source of surprise deadlocks on range conditions, and replication is still asynchronous unless you configure semi-synchronous replication or Group Replication.

### MySQL InnoDB
**Short:** MySQL's default storage engine: clustered B+tree indexes, MVCC, row locks and Repeatable Read by default.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-stores/key-value-and-embedded @2, data-access/transactions-and-consistency @2

Every table is a B+tree clustered on the primary key, so the row data sits in the leaf of that tree and a secondary index stores the primary-key value rather than a physical pointer — which means every secondary lookup costs a second descent, and a long primary key inflates every index in the table at once. MVCC comes from undo logs: a reader reconstructs the version visible to its snapshot instead of blocking, and old versions are purged later.

Writes land in the buffer pool and the redo log, with the change buffer smoothing random secondary-index writes and the doublewrite buffer guarding against torn pages. The default isolation is repeatable read implemented with next-key locks, which lock the gaps between index entries as well as the rows, so two transactions inserting into the same range can deadlock without ever touching the same row.

### Neo4j
**Short:** Native property-graph database queried with Cypher; used for knowledge graphs and edge storage for GNNs.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, applied-ml/recommenders-and-graph-ml @3

Relationships are stored as direct pointers between records rather than being reconstructed by joining tables at query time, so a multi-hop traversal costs roughly what the subgraph you touch costs instead of degrading as the whole dataset grows. That property is what makes recommendation, fraud-ring and lineage queries practical. Cypher expresses those traversals declaratively as ASCII-art patterns, and the ecosystem adds APOC for procedures and the Graph Data Science library for algorithms such as PageRank and community detection. It is the usual store behind GraphRAG-style knowledge graphs and for serving edges to a GNN at inference time, and a poor fit for high-volume aggregate scans, where a columnar store wins easily.

### Neo4j Browser
**Short:** Neo4j's web workbench for running Cypher and visualizing the returned subgraph.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, devtools/version-control-and-workbench @3

The browser is a web application served by the database itself: you connect over Bolt, type Cypher into a command bar, and results come back as a rendered subgraph you can expand node by node, or as a table, or as raw JSON. Labels get colours and captions you set in the UI, and built-in commands report indexes, constraints and store statistics, which makes it the usual medium for teaching a graph model to someone new.

Reach for it to explore a graph, sanity-check a model, or profile a query — `PROFILE` returns the operator tree with database hits, which is how you find a traversal that scanned every node because a label index was missing. It renders exactly what you return, so a query without a limit on a large graph buries the canvas, and non-technical exploration is better served by a purpose-built visualisation tool.

### nodetool compactionstats
**Short:** Cassandra nodetool command showing running compactions and pending compaction backlog per table.
**Kind:** api
**Lang:** *
**Roles:** data-stores/wide-column @1, observability/metrics-and-monitoring @2, observability/profiling-and-performance @2

### nodetool tablestats
**Short:** Cassandra nodetool subcommand reporting per-table partition sizes, tombstone counts and bloom-filter efficiency.
**Kind:** api
**Lang:** *
**Roles:** data-stores/wide-column @1, observability/profiling-and-performance @2

### nodetool tpstats
**Short:** Cassandra nodetool subcommand showing per-thread-pool active, pending and dropped task counts on a node.
**Kind:** api
**Lang:** *
**Roles:** data-stores/wide-column @1, observability/metrics-and-monitoring @2, observability/profiling-and-performance @3

### NVMe SSD
**Short:** Flash storage on the NVMe interface: roughly 100 microsecond random reads and multi-GB/s sequential throughput.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, runtime-systems/io-networking-and-syscalls @2

NVMe is a protocol over PCIe designed for flash rather than inherited from spinning disks: instead of one shallow queue it allows thousands of deep queues, one per CPU core, so a device stays busy without the lock contention and interrupt overhead older interfaces impose. The result is random reads in the low hundreds of microseconds and multiple gigabytes per second sequentially — roughly two orders of magnitude better on random I/O than a rotating disk.

The consequences reach into database design: cheap fsync latency is what makes a consensus commit or a WAL flush affordable, and read amplification from an LSM tree costs far less than it did on rotating media. What has not changed is that flash wears, rated in drive writes per day, and that an instance's local NVMe is usually ephemeral, disappearing when the instance stops — so it holds caches and replicas, never the only copy.

### OpenEBS
**Short:** Software-defined container-attached storage providing dynamically provisioned persistent volumes in-cluster.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

OpenEBS runs storage as containers in the cluster and exposes local disks as persistent volumes through several engines with different tradeoffs: a local-volume engine binds a volume to one node's disk with no replication and no network hop, while its replicated engine uses a userspace storage stack and NVMe over fabrics to mirror synchronously across nodes with far less overhead than earlier designs. Everything is driven through storage classes and CSI, so the engine choice is a class parameter.

Reach for the local engine when the workload replicates its own data — Cassandra, Kafka, Elasticsearch — and you want raw local-disk speed with a Kubernetes-managed lifecycle. Reach for the replicated engine when a single-node database must survive node loss. The general caution applies to all in-cluster storage: you are operating a storage system now, and its failure modes become yours.

### OpenTSDB
**Short:** Distributed time-series database storing metrics as rows in HBase, built for very long retention.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/time-series @1, data-stores/wide-column @3

OpenTSDB stores every metric as rows in HBase with a carefully packed row key — a metric id, an hour-aligned timestamp, then tag key and value ids drawn from a lookup table — so one row holds an hour of points for one series and a range scan over that key answers a time query. The id indirection is what keeps the keys small, and the layer is otherwise thin, inheriting HBase's durability, replication and near-unlimited retention.

It was influential and is largely superseded. A design that scans and aggregates in the query process is slow next to a columnar store, tag dimensionality is limited by the key layout, and running HBase purely for metrics is a great deal of cluster. New systems reach for Prometheus with a long-term store, VictoriaMetrics, InfluxDB or ClickHouse instead.

### Oracle Database
**Short:** Oracle Database: the long-standing commercial ACID relational engine with PL/SQL and RAC clustering.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @3

Its distinguishing implementation is undo-based MVCC: a reader reconstructs the block as it stood at statement start from the undo tablespace rather than taking a lock, so readers never block writers and writers never block readers, with read committed the practical default and serializable available. PL/SQL puts a full procedural language inside the database, RAC lets several instances share one storage layer for availability, and partitioning, materialized views and a mature cost-based optimizer with honoured hints cover the large-scale end.

Reach for it where it already is — the migration cost of a large PL/SQL estate is what keeps it in place, rather than a feature nobody else has. The reasons to leave are per-core licensing with audits, and the fact that PostgreSQL now covers most of the technical ground. Treat any migration as a rewrite of the procedural layer, not a data copy.

### Parquet
**Short:** Columnar on-disk file format with per-column compression and predicate pushdown; the lakehouse default.
**Kind:** spec
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, apis-frameworks/data-formats-and-api-contracts @2, data-stores/object-and-file-storage @3

A file is a sequence of row groups; inside each, every column is stored contiguously as a chunk of pages, dictionary- and run-length-encoded where that helps and then compressed. Each chunk's metadata carries min and max values and null counts, and the footer holds the schema and the index to all of it — so a reader opens the footer, decides which row groups can possibly match a predicate, and reads only the columns the query projects. Nested structures are flattened using definition and repetition levels rather than stored as blobs.

Reach for it as the default format for anything analytical at rest: columnar layout plus compression typically cuts both storage and scan time by an order of magnitude against CSV or JSON. It is immutable and write-once, so an update means rewriting files — exactly the gap Iceberg, Delta Lake and Hudi were built to fill.

### pg_hint_plan
**Short:** PostgreSQL extension adding per-query planner hints; core Postgres has no native hint syntax.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, observability/profiling-and-performance @2

PostgreSQL deliberately ships no hint syntax, on the argument that a hint freezes a decision the planner should keep making as the data changes. This extension adds them anyway, as a specially formatted block comment before the query naming scan methods, join methods, join order, parallelism or corrected row counts. It can also apply hints from a table keyed by query id, so a statement you cannot edit — one generated by an ORM — can still be steered.

Reach for it as a tourniquet: a critical query has picked a catastrophic plan and you need it fixed now. Then find the real cause, which is usually stale or insufficient statistics, a bad estimate on correlated predicates that extended statistics would fix, or a missing index — and remove the hint, because a hint outlives the data distribution that justified it.

### pg_toast_
**Short:** PostgreSQL's out-of-line storage: oversized column values are compressed and chunked into a hidden TOAST table.
**Kind:** api
**Lang:** *
**Roles:** data-stores/relational @1, data-stores/key-value-and-embedded @3

### pgTune
**Short:** Calculator that suggests postgresql.conf memory, WAL and parallelism settings from hardware and workload.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, observability/profiling-and-performance @2, platform-delivery/infrastructure-as-code-and-config @3

It is a calculator, not a daemon: you supply the PostgreSQL version, total RAM, CPU count, storage type and an application profile, and it returns the handful of configuration values whose defaults are wrong for real hardware — `shared_buffers`, `effective_cache_size`, `work_mem`, `maintenance_work_mem`, `max_wal_size`, `random_page_cost` and the parallelism settings. Those defaults exist so the server starts on tiny machines, which is why an untuned instance leaves most of a large box idle.

Treat the output as a starting point rather than an answer. `work_mem` is per sort or hash node per connection, not per query, so multiplying it by an aggressive connection limit is how a server runs out of memory; `random_page_cost` should reflect SSD rather than the spinning-disk assumption behind the default; and none of it helps if autovacuum cannot keep up.

### pgvector
**Short:** PostgreSQL extension adding a vector type with exact and HNSW/IVFFlat ANN search, so RAG needs no new datastore.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, caching/semantic-and-llm-cache @2, data-stores/relational @3, search-retrieval/ann-index-library @3

It adds a `vector` column type (plus half-precision and sparse variants) with distance operators - `<->` for L2, `<=>` for cosine, `<#>` for inner product - so a similarity search is an `ORDER BY ... LIMIT` in ordinary SQL. That is the whole argument for it: the embedding sits in the same row as the metadata you filter on, joins work, and it inherits transactions, backups, replication and access control you already operate.

Two index types with different tradeoffs: IVFFlat is quick to build but must be created after representative data exists, since it clusters what it sees; HNSW builds slower and uses more memory but gives better recall at the same latency. Both are approximate, so `hnsw.ef_search` or `ivfflat.probes` is a recall-versus-latency dial you have to tune and measure, and an unindexed table falls back to exact search - correct, and linear in table size.

### Pinecone
**Short:** Fully managed vector database for embedding search, RAG corpora and agent long-term memory.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2, llm-apps/prompting-context-and-structured-output @3

You create an index, upsert vectors with an id, metadata and optionally a namespace, then query for the nearest `top_k` with a metadata filter applied; sharding, replication and index maintenance are the service's problem, not yours. The serverless form separates storage from compute and bills by reads and writes rather than by a pod that runs whether you use it or not.

Reach for it when you want vector search working today and do not want to operate a database. Against that: your embeddings leave your network, cost grows with query volume, and you get less control than a self-hosted Qdrant, Milvus or a `pgvector` column beside data you already have.

### pinecone-haystack
**Short:** Haystack integration package that uses a Pinecone index as the document store for retrieval pipelines.
**Kind:** tech
**Lang:** python
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2

Haystack builds pipelines from typed components, and this package supplies a Pinecone-backed document store plus the retriever that pairs with it, so an indexing pipeline writes embedded documents into a Pinecone index and a query pipeline retrieves from it. Documents map onto vectors carrying their content and metadata, Haystack filter expressions are translated into Pinecone's filter syntax, and namespaces are exposed for separating tenants or corpora.

Install it when Haystack is the orchestration layer and you would rather not run a vector database. The considerations belong to the integration rather than the framework: filterable fields must exist in the index metadata, store operations that are trivial locally — listing every document, deleting in bulk by filter — map awkwardly onto a remote index, and the package version tracks both Haystack and the Pinecone client, so pin them together.

### PlanetScale
**Short:** Managed MySQL platform built on Vitess offering horizontal sharding and branch-and-merge non-blocking schema changes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/schema-and-migration @2, data-access/replication-ha-and-backup @3, platform-delivery/cloud-platform-and-cost @3

It runs MySQL under Vitess, which is where both distinctive features come from. Sharding is Vitess's: a keyspace is split by a vindex and the routing tier scatters or targets queries, so growing past one write primary does not mean rewriting the application. Branching is a schema workflow built on top — a development branch copies the production schema, changes are made there, and a deploy request applies the diff as an online, non-blocking schema change that can be reverted.

Reach for it when a MySQL application needs horizontal scale and safe schema change without operating Vitess yourself. The constraints follow from Vitess: foreign keys and cross-shard joins are limited, cross-shard transactions are expensive, and portability is reduced, since the branching workflow that makes it pleasant is the part you cannot take with you.

### PostgreSQL
**Short:** Open-source ACID relational database with MVCC, WAL, JSONB and a huge extension ecosystem.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-movement/event-streaming-and-processing @3, data-stores/warehouse-and-olap @3

MVCC lets readers see a consistent snapshot without blocking writers, at the cost of dead row versions that autovacuum must reclaim — which is why a write-heavy table bloats and why vacuum tuning is the recurring operational chore. The write-ahead log underpins crash recovery, streaming replication, logical replication, and point-in-time recovery, and `SERIALIZABLE` is genuine serializable snapshot isolation that aborts conflicting transactions rather than locking them out.

Beyond relational basics it carries JSONB, arrays, full-text search, GIN/GiST/BRIN indexes, and extensions — PostGIS, pgvector, TimescaleDB — which is why it keeps absorbing workloads people were about to buy a specialised store for. Reach for it as the default OLTP database; watch connection count, since every connection is an OS process and a pooler like PgBouncer becomes mandatory well before you expect, and do not mistake it for a columnar analytics engine.

### Qdrant
**Short:** Rust-based vector database with rich payload filtering, sparse vectors and server-side hybrid fusion; OSS or cloud.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/lexical-and-hybrid-search @2, caching/semantic-and-llm-cache @3, search-retrieval/rag-and-document-processing @3

A point is a vector plus a JSON payload, and the payload is the design centre: filters are pushed into the HNSW traversal rather than applied after the search, so a query restricted to one tenant, date range, or permission set still reaches its recall target instead of returning too few results. Collections support named vectors for multiple embeddings per item, sparse vectors, and server-side hybrid fusion through the Query API, with scalar, product, or binary quantization to trade a little recall for a large cut in memory.

It is written in Rust, single-binary, and runs embedded in memory for tests or clustered with sharding and replication in production. Reach for it when retrieval genuinely needs metadata filtering alongside similarity; if you only need vectors and already run PostgreSQL, pgvector is one fewer system to operate.

### RDS
**Short:** Amazon's managed relational database service: provisioned Postgres, MySQL and more with backups and failover.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, platform-delivery/cloud-platform-and-cost @2, data-access/replication-ha-and-backup @2

RDS provisions an instance running the actual engine — PostgreSQL, MySQL, MariaDB, SQL Server, Oracle — on block storage, and manages everything around it: automated backups with point-in-time recovery, minor-version patching in a maintenance window, parameter and option groups instead of editing config files, read replicas built on the engine's native replication, and Multi-AZ, which keeps a synchronous standby in another availability zone and fails over by moving the endpoint.

Understand what Multi-AZ is and is not: it buys availability, not read capacity, because the classic standby serves no traffic. Reach for RDS when you want a stock engine without operating it. The ceiling is the single writer — vertical scaling and read replicas only — which is where Aurora, application sharding or a distributed SQL engine take over, and there is no superuser or OS access.

### Redis Sorted Sets
**Short:** Redis type combining a skip list and hash map: O(log n) score and rank ops; leaderboards, priority queues, windows.
**Kind:** api
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, runtime-systems/collections-and-algorithms @2, caching/distributed-cache @2

### Redis Vector
**Short:** Redis Stack's vector index: HNSW or flat similarity search in memory, beside the cache and KV data.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, caching/distributed-cache @2, data-stores/key-value-and-embedded @3, search-retrieval/ann-index-library @3

Redis Stack's search module lets a hash or JSON field hold a vector and indexes it either flat, which is exact brute force, or with HNSW for approximate search, alongside the numeric, tag and text fields of the same index. A search or aggregate query combines a nearest-neighbour clause with ordinary filters, so similarity and metadata predicates are evaluated together, and all of it lives in the same instance as your cache and key-value data.

Reach for it when Redis is already in the stack and the corpus is modest: it saves a whole system and latency is excellent because everything is in memory. That is also the limit — vectors are large, so memory cost scales unpleasantly with corpus size and dimensionality, and index rebuilds and persistence are memory-bound too. Past a few million vectors, a purpose-built store or pgvector beside your data is cheaper.

### redis-cli
**Short:** Redis command-line client for key inspection, MONITOR, latency checks and memory analysis.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2, observability/profiling-and-performance @3

Beyond running commands interactively, redis-cli carries the diagnostics you reach for when a cache misbehaves: sampling modes that find the one key holding a gigabyte or the type consuming the memory, latency measurement from where you are standing, a scan mode that iterates keys without the blocking `KEYS` command, and `INFO` for memory, eviction, persistence and replication state. `MONITOR` streams every command the server executes, which is the fastest way to see what an application is genuinely sending — and a real throughput cost, so use it briefly.

The rule tying all of that together is that Redis executes commands on a single thread: anything you run by hand that touches many keys stalls every other client, which is exactly why the sampling variants exist.

### RedisInsight
**Short:** Official Redis GUI for browsing the keyspace and running the profiler, slowlog and memory analysis.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, observability/profiling-and-performance @3

RedisInsight connects to a standalone, Cluster or Sentinel deployment and gives you a keyspace browser that walks keys with `SCAN` rather than `KEYS`, plus type-aware editors for hashes, sorted sets, streams and JSON, so inspecting a value does not require composing the right command first. Its Workbench runs commands with inline documentation, the profiler is a UI over `MONITOR`, the slowlog view surfaces commands that exceeded the configured threshold, and memory analysis breaks the keyspace down by prefix and type -- the fastest way to find which key pattern is eating the instance. Reach for it when debugging or exploring an instance by hand rather than for anything automated. Note that `MONITOR` streams every command the server executes and costs real throughput, so profile briefly and never leave it running against a busy production node.

### Riak
**Short:** Dynamo-style AP key-value store: masterless replication, tunable quorums, conflict resolution via vector clocks/CRDTs.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/replication-ha-and-backup @3

Every node is equal and there is no primary, so a write goes to whichever node the client reaches and is replicated to N others, with per-request R and W quorums letting you trade latency against consistency on the individual operation. Choosing availability means concurrent writes can diverge, and Riak surfaces that honestly rather than hiding it: it keeps siblings with causal-context metadata and asks the application to merge them, or you model the value as a CRDT (counter, set, map, register) so convergence happens automatically. In practice you will meet it as the textbook illustration of the AP corner of CAP and of the Dynamo paper's design; for a new system the mainstream Dynamo-style choices are Cassandra, ScyllaDB or DynamoDB.

### RocksDB
**Short:** Embedded LSM-tree key-value storage engine tuned for SSDs; the local store inside many databases and stream processors.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1

RocksDB is an embedded LSM-tree engine forked from LevelDB and rebuilt for modern hardware: writes go to a write-ahead log and a memtable, flush to immutable sorted files, and background compaction merges them down through levels, with bloom filters and a block cache keeping most reads from touching more files than necessary. Column families give separate keyspaces sharing one log, and almost every dimension — compaction strategy, compression per level, write stalls, cache sizes — is tunable.

It is a library, not a server, which is why it turns up as the local state store inside so many other systems: Kafka Streams and Flink for operator state, TiKV and CockroachDB and MyRocks for storage, plus countless caches and queues. Reach for it when you need ordered local storage with high write throughput; the price is write and space amplification from compaction, and a tuning surface deep enough to be a specialism.

### Rook-Ceph
**Short:** Kubernetes operator running Ceph in-cluster to provide block, shared-file and S3-compatible object storage.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

Rook is an operator that installs and runs Ceph inside Kubernetes: custom resources describe the cluster, the daemons that own each disk, the monitors and the pools, and the operator handles placement, upgrades and recovery. Ceph itself distributes objects with the CRUSH algorithm and replicates or erasure-codes them, and exposes three interfaces from the same cluster — block volumes, a shared filesystem, and an S3-compatible object gateway — so one system covers read-write-once, read-write-many and object needs.

Reach for it on bare metal or in a private cloud where you need all three and have disks to give it. Be honest about the commitment: Ceph is a substantial distributed system with its own failure modes, placement-group tuning and recovery storms, and it wants dedicated nodes and fast networking. In a public cloud, the provider's CSI drivers are far less work.

### S3
**Short:** AWS object storage with versioning and lifecycle tiers; also the usual Terraform remote-state backend.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @2, platform-delivery/infrastructure-as-code-and-config @3

Objects live in a flat namespace under a bucket, addressed by key, with strong read-after-write consistency, optional versioning, lifecycle rules that tier or expire data into cheaper classes, server-side encryption, and access governed by IAM plus bucket policies. Throughput scales by parallelizing across keys, and durability is high enough that it is the default place to put anything you cannot afford to lose.

It is not a filesystem: there is no in-place update or append, renaming means copy-and-delete, listing a huge prefix is slow and paginated, and per-request cost dominates when objects are tiny. Reach for it as the landing zone for data lakes, backups, logs, model artifacts, and Terraform remote state; when you need POSIX semantics, low-latency random writes, or a shared mount, that is EBS or EFS.

### ScyllaDB
**Short:** C++ Cassandra-compatible wide-column store with shard-per-core threading and no JVM, tunable consistency.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, data-stores/key-value-and-embedded @3

ScyllaDB reimplements Cassandra in C++ on the Seastar framework, with one thread pinned per core owning a shard of the data, its own memory and its own scheduler, and no garbage collector anywhere. That architecture targets exactly what hurts most on Cassandra in production — JVM GC pauses showing up as tail-latency spikes — and it removes the heap-tuning exercise that comes with them.

Because it speaks CQL and works with the same drivers, the migration story is largely operational rather than a rewrite. Reach for it when a wide-column store is already the right shape and either p99 latency or node count and hardware cost is the problem. Do not assume API compatibility means behavioural identity: the internals, tuning knobs and failure characteristics differ, so capacity planning has to be redone rather than carried over.

### Snowflake
**Short:** Cloud data warehouse with storage and compute separated into independently scaled virtual warehouses.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1

Storage is columnar micro-partitions in the cloud provider's object store, immutable and automatically clustered by ingestion order with per-partition metadata used to prune scans. Compute is a virtual warehouse — an independently sized, independently billed cluster that reads that shared storage — so two teams can run heavy queries without contending, and a warehouse suspends when idle. Because storage is immutable and versioned, time travel to an earlier state and zero-copy cloning of a whole database are cheap metadata operations, which is what makes per-developer environments practical.

Reach for it when analytics should be a service with no infrastructure and elastic, isolated compute. Watch cost rather than performance: oversized warehouses, generous idle timeouts and large unclustered tables are where the money goes, and auto-scaling makes it easy to spend without noticing.

### spring-ai-pgvector-store
**Short:** Spring AI starter backing a VectorStore with Postgres pgvector for similarity search from Java.
**Kind:** tech
**Lang:** java
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2

The starter wires a pgvector-backed bean behind Spring AI's `VectorStore` interface, creating the table and vector column, choosing the index type and distance function from properties, and calling your configured embedding model on add, so documents go in as text and come back from a similarity search as documents with scores. Because the interface is shared, moving later to Redis, Qdrant or Milvus is a dependency change and a few properties.

Reach for it when a Java service already runs PostgreSQL: the embeddings sit beside the business rows, inside the same transaction, backup and access control, and there is no second datastore to operate. The caveats are pgvector's — choose HNSW or IVFFlat deliberately and tune the recall dial — plus the reminder that automatic schema initialisation is convenient in development and should be a migration in production.

### SQL Server
**Short:** Microsoft's ACID relational database engine with T-SQL, Always On availability groups and columnstore indexes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1

The engine's distinctive parts are the ones that shape application behaviour. Its default isolation is read committed implemented with locks, so a reader blocks on an uncommitted writer until you enable read-committed snapshot isolation, which switches it to row versioning in `tempdb` and removes most of the blocking people blame on the database. Clustered indexes decide physical row order, columnstore indexes give a compressed columnar copy of the same table for analytics, and in-memory OLTP offers lock-free tables for extreme write rates.

For availability, Always On availability groups replicate a set of databases to synchronous or asynchronous secondaries, some readable. Reach for it in a Microsoft estate or where T-SQL and the reporting and integration tooling already exist; the reasons to move are per-core licensing, and the fact that it now runs on Linux and in containers, so being on Windows is no longer the tie it once was.

### SQLite
**Short:** Embedded serializable SQL database in a single file, with a B+tree row store and WAL mode; no server process.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-stores/key-value-and-embedded @1

SQLite is a C library linked into your process, so a query is a function call against a single file on disk: no server, no port, no connection pool, nothing to operate or monitor. It is fully ACID, and in WAL mode readers no longer block the writer — but there is still exactly one writer at a time for the whole database, and that single constraint decides most adoption questions.

It is the right answer for an embedded or on-device store, an application file format, a test fixture, or a local cache. It is the wrong answer for concurrent write traffic from many clients, which needs a server-based engine.

### Synapse
**Short:** Azure Synapse Analytics: managed data warehouse with dedicated/serverless SQL pools and integrated Spark.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-movement/batch-and-distributed-compute @3, platform-delivery/cloud-platform-and-cost @3

Synapse packages several engines behind one workspace: dedicated SQL pools, which distribute data across compute nodes by hash, round robin or replication; serverless SQL, which queries Parquet and CSV in a data lake with nothing provisioned and bills by data scanned; Spark pools; and pipelines that are the Data Factory engine under another name.

For a dedicated pool the distribution key is the whole performance story — joining two tables distributed on different columns forces a data-movement step, which is where slow queries come from — and pausing the pool is how you avoid paying for it overnight. Weigh adoption carefully: Microsoft's investment has moved to Fabric, which absorbs the same capabilities into a newer platform, so a green-field analytics project in Azure should compare the two before committing.

### TiDB
**Short:** MySQL-compatible distributed SQL database with Percolator-style 2PC, horizontal scale-out and an HTAP columnar replica.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-stores/warehouse-and-olap @3, data-access/replication-ha-and-backup @3

TiDB is a distributed SQL database assembled from three parts: a stateless SQL layer that speaks the MySQL wire protocol, TiKV, a Raft-replicated key-value store that splits data into Regions and moves them between nodes, and PD, which places those Regions and hands out globally ordered timestamps. Transactions use Percolator-style two-phase commit against those timestamps, so a statement spanning many Regions is still ACID without any application-level sharding logic.

TiFlash adds columnar replicas kept in sync through Raft, so analytical scans run on the same cluster without touching the row store — the HTAP claim. Reach for it when a sharded MySQL fleet has outgrown manual resharding and you want to keep the MySQL dialect and drivers. The cost is a multi-component cluster to operate and higher latency on a single point lookup than one MySQL would give you.

### TigerGraph
**Short:** Distributed native property-graph database with GSQL, aimed at deep multi-hop queries on huge graphs.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1

It stores the graph natively and partitions it across a cluster, and its query language is procedural rather than purely declarative: a query declares vertex sets and accumulators, then repeatedly selects the neighbours of the current frontier, accumulating values as it goes. That accumulator model is a bulk-synchronous traversal, which is why deep multi-hop queries and in-database algorithms parallelise across machines instead of degrading hop by hop.

Reach for it when the workload is genuinely deep traversal at scale — many-hop fraud rings, entity resolution, supply-chain or network impact analysis — on a graph too large for one machine. Against it: the query language is one your team must learn and cannot transfer, it is a commercial product, and for graphs that fit on a single node Neo4j has a far larger ecosystem and Cypher is much more widely known.

### TiKV
**Short:** Distributed transactional key-value store using Raft-replicated regions; the storage layer beneath TiDB.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

TiKV presents a single ordered keyspace split into Regions of roughly a hundred megabytes, each replicated by its own Raft group across nodes, while a placement driver decides where Regions live, splits and merges them, and moves them to balance load. Each replica stores its data in RocksDB. Transactions are Percolator-style: an optimistic two-phase commit anchored on a primary lock key and using timestamps from the placement driver, giving snapshot isolation across arbitrary keys with no central lock manager.

It is a CNCF project and the storage layer beneath TiDB, but usable on its own through a raw key-value API or the transactional one. Reach for it when you need a horizontally scalable, strongly consistent key-value store and are prepared to run the placement driver plus a fleet of nodes; for a key-value need without distribution, this is a great deal of machinery to take on.

### TimescaleDB
**Short:** PostgreSQL extension for time-series data: hypertable partitioning, native compression and continuous aggregates.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/time-series @1, data-stores/relational @2

A hypertable looks like an ordinary table but is transparently partitioned into chunks by time (and optionally by a space dimension such as device id), so inserts concentrate in the newest chunk where the indexes are still small, and a query with a time predicate prunes whole chunks instead of scanning history. Older chunks can be converted to a compressed columnar form, which is where the large storage reductions come from, and continuous aggregates maintain hourly or daily rollups incrementally rather than recomputing them.

The reason to pick it over a purpose-built time-series database is that it is still PostgreSQL: your metrics can be joined to customer and product tables, you keep SQL, transactions, and every driver, extension and backup tool you already run. The reason not to is that a dedicated system like Prometheus is a better fit when the data is pure operational metrics with its own query language and retention model.

### uuid_extract_timestamp
**Short:** PostgreSQL function recovering the embedded creation time from a v1 or v7 UUID, handy for triage.
**Kind:** api
**Lang:** *
**Roles:** data-stores/relational @1, data-access/schema-and-migration @2

### UUID_TO_BIN
**Short:** MySQL function packing a UUID string into BINARY(16); swap_flag=1 reorders v1 fields for index locality.
**Kind:** api
**Lang:** *
**Roles:** data-stores/relational @1, data-access/schema-and-migration @2

### uuidv4()
**Short:** PostgreSQL built-in generating an RFC 9562 version-4 UUID with no extension required.
**Kind:** api
**Lang:** *
**Roles:** data-stores/relational @1, data-access/schema-and-migration @2

### Valkey
**Short:** BSD-licensed Linux Foundation fork of Redis 7.2.4; wire-compatible, the default on AWS ElastiCache/MemoryDB.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @1, data-movement/message-broker @3

Valkey is the community continuation of Redis, forked from the last BSD-licensed release and now developed under the Linux Foundation by contributors from several large cloud vendors. It is command- and protocol-compatible, so existing clients, libraries and tooling work unchanged, and it has kept developing rather than freezing — most visibly with a multi-threaded I/O path that lifts per-node throughput above the strictly single-threaded model.

It is what the major managed caching services now offer by default, and most distributions have followed. Reach for it wherever you would have reached for Redis and licence terms matter, such as vendoring it into a product or offering it as a service. The practical caution is divergence over time: features and modules added on either side will not necessarily exist on the other, so check anything beyond the core commands.

### valkey-cli
**Short:** Valkey's command-line client and benchmark tool, command-compatible with redis-cli and redis-benchmark.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2, devtools/version-control-and-workbench @3

It is the fork's copy of the familiar command-line client, so the interface is the one you already know: interactive command entry, a scan mode that iterates keys without the blocking `KEYS` command, sampling modes that find the single key or the type eating memory, a latency mode that measures round-trip time from where you are standing, and an option to pull a snapshot. A benchmark tool ships alongside it.

Because the protocol is unchanged, the Redis client works against a Valkey server and vice versa, so the choice is mostly about which package your image already has. The same discipline applies as always: `MONITOR` streams every command the server executes and costs real throughput, and any command that touches many keys stalls every other client, because the command loop still runs them one at a time.

### Voldemort
**Short:** LinkedIn's Dynamo-style distributed key-value store: masterless, eventually consistent, tunable quorum reads and writes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/replication-ha-and-backup @3

Voldemort was LinkedIn's implementation of the Dynamo paper: a consistent-hash ring with no primary, per-request quorum settings so each operation chose its own consistency, vector clocks to detect concurrent writes and hand siblings back to the application, hinted handoff and read repair to converge after a failure, and pluggable storage engines — including a read-only store whose files were built by Hadoop jobs.

That last part was its distinctive use: batch-computed data such as recommendations, built offline and swapped into the serving cluster atomically. As something to adopt it is history, since the project is no longer active and the mainstream Dynamo-style options are Cassandra, ScyllaDB and DynamoDB. It stays worth knowing as a clean worked example of quorum tuning and of what it costs to push conflict resolution onto the client.

### VoltDB
**Short:** In-memory partitioned NewSQL database running stored procedures single-threaded per partition for serializability.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-stores/key-value-and-embedded @3

Its design removes concurrency control rather than optimising it: the dataset is partitioned across cores, each partition is owned by a single thread executing transactions serially against in-memory data, and a transaction arrives as one stored procedure. With no locks, no latches and no buffer manager, a single-partition transaction is a few microseconds of pure computation, and serializability is a consequence of the execution model instead of a protocol. Durability comes from command logging plus periodic snapshots, and availability from replicating each partition.

Reach for it when the workload is a high rate of short, known-in-advance transactions that partition cleanly — telecom charging, fraud checks, ad decisioning. The constraints are severe by design: the working set must fit in memory across the cluster, cross-partition and ad-hoc queries are expensive because they serialise against every partition, and application logic has to be written as stored procedures.

### Weaviate
**Short:** Open-source vector database with built-in hybrid BM25+dense search, filtering and a GraphQL API.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/lexical-and-hybrid-search @2, search-retrieval/rag-and-document-processing @3, llm-apps/prompting-context-and-structured-output @3

Objects are stored against a schema together with their vectors, and modules can embed at write time by calling an external model, so you may send text rather than vectors if you prefer. Its hybrid query fuses a BM25 keyword score with dense similarity through an `alpha` weighting, and filters are applied against the HNSW graph rather than after it, so a filtered search still returns k results instead of whatever happened to survive post-filtering.

Reach for it when queries mix exact terms — a product code, a person's name, an error string — with semantic similarity, since pure dense retrieval is notoriously weak on exact tokens. Compare it with the search engine you already run: if Elasticsearch or OpenSearch is in the stack, its vector support may beat adding a second datastore.

### Weaviate multi2vec
**Short:** Weaviate module vectorizing text and images into one joint index so a text query retrieves images directly.
**Kind:** api
**Lang:** *
**Roles:** data-stores/vector-store @1, applied-ml/vision-speech-and-multimodal @2, search-retrieval/rag-and-document-processing @3

### WiredTiger
**Short:** MongoDB's default storage engine: B+tree (optionally LSM) pages with document-level concurrency and compression.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-stores/document @3

WiredTiger stores each collection and each index as its own B+tree file, building pages in memory and writing them out compressed — never updating in place, since a checkpoint writes new pages and switches the root, so an interrupted checkpoint leaves the previous one intact. Concurrency is MVCC at document level, so two writers touching different documents in the same collection do not block each other, and the journal makes writes durable between checkpoints, flushed on an interval unless the write concern demands otherwise.

Its cache is the tuning surface that matters: it claims roughly half of available memory by default, and a working set larger than that cache turns reads into disk I/O, which is where most MongoDB latency complaints eventually lead. Index and working-set size, not document count, decide the machine you need.

### YugabyteDB
**Short:** Distributed SQL database with a PostgreSQL-compatible wire protocol, Raft replication and built-in 2PC.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

It is two layers: DocDB, a sharded transactional store built on RocksDB with Raft consensus per tablet, and a query layer above it. The SQL layer reuses the actual PostgreSQL query engine source, which is why extension and feature compatibility runs deep; a separate Cassandra-flavoured API is offered over the same storage.

Reach for it, as with CockroachDB, when you need geo-distributed ACID with automatic failover and a PostgreSQL-shaped application. For a single-region OLTP workload, plain PostgreSQL with a replica is cheaper, simpler and lower-latency -- distributed consensus is not free per transaction.
