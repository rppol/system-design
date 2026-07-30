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

### Aurora
**Short:** AWS managed MySQL/PostgreSQL-compatible database with a shared storage layer, fast replicas and auto failover.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/relational @1, data-access/replication-ha-and-backup @2, platform-delivery/cloud-platform-and-cost @3

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

### Chroma
**Short:** Embedded, zero-setup vector database for local development and prototyping of RAG applications.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, data-stores/key-value-and-embedded @3

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

### Dragonfly
**Short:** Redis-compatible multi-threaded in-memory data store with higher throughput and better memory efficiency per node.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, caching/distributed-cache @2

### DynamoDB
**Short:** AWS fully managed key-value/document store with predictable latency, optional strong reads and serverless scaling.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-stores/document @2, data-access/transactions-and-consistency @3, platform-delivery/cloud-platform-and-cost @3

### EBS
**Short:** AWS Elastic Block Store: network-attached block volumes for EC2 and ReadWriteOnce Kubernetes persistent volumes.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/cloud-platform-and-cost @3

### EFS
**Short:** AWS Elastic File System: managed NFS shared filesystem, the usual ReadWriteMany volume for pods.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/object-and-file-storage @1, platform-delivery/kubernetes-and-orchestration @3

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

### InfluxDB
**Short:** Purpose-built time-series database for metrics and event data, with Telegraf ingestion and Flux/SQL querying.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/time-series @1, observability/metrics-and-monitoring @3

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

### Pinecone
**Short:** Fully managed vector database for embedding search, RAG corpora and agent long-term memory.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/rag-and-document-processing @2, llm-apps/prompting-context-and-structured-output @3

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

### Qdrant
**Short:** Rust-based vector database with rich payload filtering, sparse vectors and server-side hybrid fusion; OSS or cloud.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/vector-store @1, search-retrieval/lexical-and-hybrid-search @2, caching/semantic-and-llm-cache @3, search-retrieval/rag-and-document-processing @3

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

### RedisInsight
**Short:** Official Redis GUI for browsing the keyspace and running the profiler, slowlog and memory analysis.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, observability/profiling-and-performance @3

### Riak
**Short:** Dynamo-style AP key-value store: masterless replication, tunable quorums, conflict resolution via vector clocks/CRDTs.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/key-value-and-embedded @1, data-access/replication-ha-and-backup @3

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

### ScyllaDB
**Short:** C++ Cassandra-compatible wide-column store with shard-per-core threading and no JVM, tunable consistency.
**Kind:** tech
**Lang:** *
**Roles:** data-stores/wide-column @1, data-stores/key-value-and-embedded @3

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
