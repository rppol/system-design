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

### @mastra/pg
**Short:** Mastra's PostgreSQL adapter, using pgvector as the vector store and memory backend for TypeScript agents.
**Kind:** tech
**Lang:** js
**Roles:** data-stores/vector-store @1, search-retrieval/ann-index-library @3

### Aerospike
**Short:** Distributed real-time key-value store with a flash-optimized storage engine and sub-millisecond reads at large scale.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2, data-stores/document @3

### aiobotocore
**Short:** Async botocore fork; non-blocking AWS SDK calls (S3, SQS, DynamoDB) from asyncio code.
**Kind:** tech
**Lang:** python
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @2

### Amazon Aurora
**Short:** AWS MySQL/PostgreSQL-compatible relational engine with a shared distributed storage layer and fast replica failover.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/replication-ha-and-backup @2, platform-delivery/cloud-platform-and-cost @3

### Amazon DynamoDB
**Short:** AWS serverless key-value and document store with partition-key sharding, auto-scaling and global tables.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-stores/document @2, platform-delivery/cloud-platform-and-cost @3, data-access/replication-ha-and-backup @3

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

### Apache Druid
**Short:** Real-time OLAP datastore that ingests event streams and answers sub-second time-sliced aggregations.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, data-stores/time-series @2, data-movement/event-streaming-and-processing @3

### Apache Parquet on S3
**Short:** Columnar Parquet files kept in S3 as the offline store for training data, backfills and analytical scans.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, data-stores/warehouse-and-olap @2, ml-lifecycle/ml-platform-and-pipelines @3

### APOC
**Short:** Neo4j's standard procedure and function library: graph algorithms, import/export, refactoring and utilities.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1

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

Aurora keeps the MySQL or PostgreSQL engine but replaces the storage layer with a distributed service that replicates every write across three availability zones. A read replica therefore does not replay a log into its own copy of the data — it reads the same storage the writer does, which is why replicas are cheap to add, lag far less than they would under streaming replication, and can be promoted in seconds on failover.

Choose it when you want a familiar SQL engine with durability and failover handled for you. You give up control of the storage layer, and you are pinned to whichever engine versions AWS has certified.

### Azure Cognitive Search
**Short:** Azure managed search service combining keyword, semantic and vector indexes; common RAG/memory backend.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/lexical-and-hybrid-search @2, search-retrieval/rag-and-document-processing @3

### Azure Disk CSI
**Short:** CSI driver that attaches Azure managed disks as ReadWriteOnce persistent volumes to pods.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

### Azure SQL
**Short:** Microsoft's managed relational database service with built-in HA, automated backups and elastic scaling.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, platform-delivery/cloud-platform-and-cost @2, data-access/replication-ha-and-backup @3

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

### CSI snapshot controller
**Short:** Kubernetes controller implementing VolumeSnapshot: point-in-time snapshots and restores of CSI volumes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, data-access/replication-ha-and-backup @2, platform-delivery/kubernetes-and-orchestration @2

### DataStax Astra DB
**Short:** DataStax's managed Cassandra-as-a-service with serverless scaling, a REST/GraphQL data API and vector search.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, data-stores/vector-store @3, platform-delivery/cloud-platform-and-cost @3

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

### Firestore
**Short:** Google Cloud's serverless document database with real-time listeners and offline sync for mobile and web clients.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, platform-delivery/cloud-platform-and-cost @3, apis-frameworks/rpc-graphql-and-streaming @3

### FoundationDB
**Short:** Distributed ordered key-value store with strictly serializable ACID transactions, used as a substrate for higher layers.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

### GCE PD
**Short:** Google Compute Engine Persistent Disk: network block storage attached to one VM or pod (ReadWriteOnce).
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @3

### GCS
**Short:** Google Cloud Storage: managed object storage with buckets, storage classes and lifecycle policies.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @2

### Gephi
**Short:** Desktop graph visualization and exploration tool for laying out, filtering and debugging network structure.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, applied-ml/recommenders-and-graph-ml @3

### Google Spanner
**Short:** Google's globally distributed SQL database giving external consistency via TrueTime and 2PC over Paxos.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3, platform-delivery/cloud-platform-and-cost @3

Data is range-partitioned into splits, each split a Paxos group replicated across zones or regions, so losing a replica costs no failover step. A transaction spanning splits is two-phase commit layered over those groups, which defuses the usual objection to 2PC — that a coordinator crash blocks participants indefinitely — because the coordinator's own state is replicated.

External consistency comes from TrueTime: the clock API returns an interval rather than an instant, and a transaction waits out that uncertainty before releasing its commit timestamp, so any transaction starting after a commit is guaranteed to observe it. Reach for it when you genuinely need global ACID with SQL and cannot shard by tenant; the cost is commit latency that grows with participant count and geographic spread.

### H2
**Short:** Embedded Java SQL database, usually run in-memory as the default @DataJpaTest backing store.
**Kind:** tech
**Lang:** java
**Roles:** data-stores/relational @1, data-stores/key-value-and-embedded @2, devtools/testing-and-mocking @2

### H2 Database
**Short:** Embeddable Java SQL database that runs in-memory or on disk, used mostly for fast integration tests.
**Kind:** tech
**Lang:** java
**Roles:** data-stores/relational @1, devtools/testing-and-mocking @2, data-stores/key-value-and-embedded @3

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

### KeyDB
**Short:** Multi-threaded Redis fork with active replication, drop-in compatible with the Redis protocol.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2

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

### llama-index-vector-stores-pinecone
**Short:** LlamaIndex integration package binding its VectorStore interface to a Pinecone index.
**Kind:** tech
**Lang:** python
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2

### LMDB
**Short:** Embedded memory-mapped key-value store using a copy-on-write B+tree; single-writer, lock-free readers.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1

### Longhorn
**Short:** Software-defined block storage for Kubernetes providing replicated in-cluster persistent volumes and snapshots.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

### Memgraph
**Short:** In-memory property-graph database speaking Cypher, aimed at low-latency and streaming graph workloads.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/graph-db @1, data-stores/key-value-and-embedded @3

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

### mongostat
**Short:** MongoDB CLI printing a live per-second view of server ops, connections, queues and memory.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/document @1, observability/metrics-and-monitoring @2, observability/profiling-and-performance @2

### MySQL
**Short:** The mainstream open-source ACID relational database, with InnoDB storage and binlog-based replication.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/replication-ha-and-backup @3

InnoDB stores each table as a B+tree clustered on the primary key, so a secondary index holds the primary key rather than a row pointer and every secondary lookup costs a second descent — which is why a wide or randomly ordered primary key hurts read and write paths at once. Durability comes from the redo log and crash recovery, while replication ships the binlog, row-based by default and asynchronous unless you configure otherwise, so a replica can lag and a read served there may be stale.

Its default isolation is repeatable read with gap locking, stricter than most engines and a recurring source of surprise deadlocks on range conditions in otherwise ordinary transactions.

### MySQL 8+
**Short:** Mainstream open-source relational database: InnoDB storage, window functions, CTEs, JSON columns, binlog replication.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @3, data-access/replication-ha-and-backup @3

### MySQL InnoDB
**Short:** MySQL's default storage engine: clustered B+tree indexes, MVCC, row locks and Repeatable Read by default.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-stores/key-value-and-embedded @2, data-access/transactions-and-consistency @2

### MySQL/InnoDB
**Short:** MySQL with its default InnoDB engine: row-level locking and a clustered B+tree keyed on the primary key.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-stores/key-value-and-embedded @2

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

### OpenEBS
**Short:** Software-defined container-attached storage providing dynamically provisioned persistent volumes in-cluster.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

### OpenTSDB
**Short:** Distributed time-series database storing metrics as rows in HBase, built for very long retention.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/time-series @1, data-stores/wide-column @3

### Oracle
**Short:** Oracle Database: the long-standing commercial ACID relational engine with PL/SQL and RAC clustering.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @3

### Oracle DB
**Short:** Oracle's commercial relational database: MVCC with Read Committed default, PL/SQL, RAC clustering and partitioning.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2

### Parquet
**Short:** Columnar on-disk file format with per-column compression and predicate pushdown; the lakehouse default.
**Kind:** spec
**Lang:** *
**Roles:** data-stores/warehouse-and-olap @1, apis-frameworks/data-formats-and-api-contracts @2, data-stores/object-and-file-storage @3

### pg_hint_plan
**Short:** PostgreSQL extension adding per-query planner hints; core Postgres has no native hint syntax.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, observability/profiling-and-performance @2

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

### PlanetScale
**Short:** Managed MySQL platform built on Vitess offering horizontal sharding and branch-and-merge non-blocking schema changes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/schema-and-migration @2, data-access/replication-ha-and-backup @3, platform-delivery/cloud-platform-and-cost @3

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

### Rook-Ceph
**Short:** Kubernetes operator running Ceph in-cluster to provide block, shared-file and S3-compatible object storage.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @2

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

### Spanner
**Short:** Google's globally distributed SQL database giving strict serializability via Paxos replication and TrueTime clocks.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3, platform-delivery/cloud-platform-and-cost @3

Every write goes through the Paxos group owning its key range, and commit timestamps come from TrueTime's bounded-uncertainty clock, which together give strict serializability across regions; snapshot reads at a past timestamp take no locks at all, so long analytical scans never block writers.

In CAP terms it chooses consistency — a partitioned minority of replicas stops serving writes rather than diverging — and leans on Google's redundant network to keep availability high anyway. Schema design decides whether it actually performs: a monotonically increasing primary key funnels every insert into a single split, so hashed or reversed keys and interleaved child tables are what keep the write path spread across the cluster.

### spring-ai-pgvector-store
**Short:** Spring AI starter backing a VectorStore with Postgres pgvector for similarity search from Java.
**Kind:** tech
**Lang:** java
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2

### SQL Server
**Short:** Microsoft's ACID relational database engine with T-SQL, Always On availability groups and columnstore indexes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1

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

### TiKV
**Short:** Distributed transactional key-value store using Raft-replicated regions; the storage layer beneath TiDB.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

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

### valkey-cli
**Short:** Valkey's command-line client and benchmark tool, command-compatible with redis-cli and redis-benchmark.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2, devtools/version-control-and-workbench @3

### Voldemort
**Short:** LinkedIn's Dynamo-style distributed key-value store: masterless, eventually consistent, tunable quorum reads and writes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/replication-ha-and-backup @3

### VoltDB
**Short:** In-memory partitioned NewSQL database running stored procedures single-threaded per partition for serializability.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-stores/key-value-and-embedded @3

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

### YugabyteDB
**Short:** Distributed SQL database with a PostgreSQL-compatible wire protocol, Raft replication and built-in 2PC.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/transactions-and-consistency @2, data-access/replication-ha-and-backup @3

It is two layers: DocDB, a sharded transactional store built on RocksDB with Raft consensus per tablet, and a query layer above it. The SQL layer reuses the actual PostgreSQL query engine source, which is why extension and feature compatibility runs deep; a separate Cassandra-flavoured API is offered over the same storage.

Reach for it, as with CockroachDB, when you need geo-distributed ACID with automatic failover and a PostgreSQL-shaped application. For a single-region OLTP workload, plain PostgreSQL with a replica is cheaper, simpler and lower-latency -- distributed consensus is not free per transaction.
